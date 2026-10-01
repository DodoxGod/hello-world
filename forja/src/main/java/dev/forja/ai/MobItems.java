package dev.forja.ai;

import dev.forja.Forja;
import dev.forja.combat.Posture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v4's object head (docs/red_mob_v4_diseno.md §4.8, outputs 42–50, block C): what a monster does with what it carries
 * (MobKit). The same executor for a v4 network and for the rules ({@link #ruleItem}), started from the decision and run
 * every tick from MobAi.
 *
 * <table>
 *   <caption>The nine objects</caption>
 *   <tr><th>k</th><th>what</th><th>ticks</th><th>then</th><th>cooldown after</th></tr>
 *   <tr><td>1</td><td>drink a healing potion, at half speed</td><td>32</td><td>+4 health per level (regeneration: its vanilla effect)</td><td>20</td></tr>
 *   <tr><td>2</td><td>drink a potion that betters it, at half speed</td><td>32</td><td>its vanilla effect, 90 s</td><td>20</td></tr>
 *   <tr><td>3</td><td>eat, at half speed</td><td>32</td><td>+4 health</td><td>20</td></tr>
 *   <tr><td>4</td><td>throw a splash potion (arm up)</td><td>10</td><td>a lob at where the player will be (lead 1.0)</td><td>40</td></tr>
 *   <tr><td>5</td><td>ender pearl in (pearl in hand)</td><td>8</td><td>to 2 blocks from the player on its side, or at them on a pillar</td><td>100</td></tr>
 *   <tr><td>6</td><td>ender pearl away</td><td>8</td><td>to a spot 12–16 from the player, hidden from them if it can</td><td>100</td></tr>
 *   <tr><td>7</td><td>wind charge (arm up)</td><td>8</td><td>vanilla's wind charge at the player's feet</td><td>60</td></tr>
 *   <tr><td>8</td><td>change weapon</td><td>20</td><td>main hand and spare slot swap</td><td>40</td></tr>
 * </table>
 *
 * <p>Staggered in the middle of it, it stops and loses nothing. The pearl hurts the mob 2 when it lands (a player's
 * would hurt them 5). What it drinks, eats or throws is gone from the kit only when it happens. Nothing here ever
 * breaks or triggers a block: the wind charges monsters throw leave doors, trapdoors and buttons alone
 * (WindChargeMixin), so a closed base stays closed.
 */
public final class MobItems {
	public static final int NONE = 0;
	public static final int HEAL = 1;
	public static final int BUFF = 2;
	public static final int EAT = 3;
	public static final int SPLASH = 4;
	public static final int PEARL_IN = 5;
	public static final int PEARL_AWAY = 6;
	public static final int WIND = 7;
	public static final int SWAP = 8;

	public static final int DRINK_TICKS = 32;
	public static final int THROW_WARNING = 10;
	public static final int PEARL_WARNING = 8;
	public static final int WIND_WARNING = 8;
	public static final int SWAP_TICKS = 20;
	public static final int[] COOLDOWN = {0, 20, 20, 20, 40, 100, 100, 60, 40};
	/** Health a healing potion gives per level, and food. */
	public static final float HEAL_PER_LEVEL = 4.0F;
	public static final float FOOD_HEAL = 4.0F;
	/** A potion that betters it lasts this long (90 s). */
	public static final int BUFF_TICKS = 1800;
	/** The damage an ender pearl does to the mob it carries. */
	public static final float PEARL_DAMAGE = 2.0F;
	/** A throw needs the player between these (lanzamiento_ok). */
	public static final double THROW_MIN = 3.0;
	public static final double THROW_MAX = 10.0;
	/** A pearl in lands this far from the player, on the mob's side; one away lands this far from the player. */
	public static final double PEARL_NEAR = 2.0;
	public static final double PEARL_FAR_MIN = 12.0;
	public static final double PEARL_FAR_MAX = 16.0;
	public static final double PEARL_RANGE = 16.0;
	private static final Identifier SLOW = Forja.id("consumo");

	private MobItems() {
	}

	private static boolean on() {
		return dev.forja.combat.CombatConfig.get().mobActionsV4;
	}

	// ---------------------------------------------------------------- what may be done

	/** Whether it is in the middle of one (drinking, eating, warning, changing weapon): "consumiendo". */
	public static boolean busy(MobMind mind) {
		return mind.itemTicks > 0;
	}

	/** Ticks of its cooldown still to go (consumo_enfriamiento). */
	public static long cooldown(MobMind mind, long now) {
		return Math.max(0L, mind.itemReadyAt - now);
	}

	/** The object head's mask for object k (1..8): the contract's, plus the master switch. */
	public static boolean allowed(Mob mob, MobMind mind, Player target, int k, long now) {
		if (!on() || k <= 0 || k > 8 || busy(mind) || cooldown(mind, now) > 0 || Posture.isStaggered(mob, now) || target == null) {
			return false;
		}
		int[] counts = MobKit.counts(mob);
		return switch (k) {
			case HEAL -> counts[MobKit.Kind.HEAL.ordinal()] > 0;
			case BUFF -> counts[MobKit.Kind.BUFF.ordinal()] > 0;
			case EAT -> counts[MobKit.Kind.FOOD.ordinal()] > 0;
			// nothing is thrown at a player it does not perceive: that would be aiming at where they really are
			case SPLASH -> counts[MobKit.Kind.SPLASH.ordinal()] > 0 && Perception.perceived(mind, now) && throwable(mob, target);
			case PEARL_IN -> counts[MobKit.Kind.PEARL.ordinal()] > 0 && Perception.perceived(mind, now) && pearlIn(mob, target) != null;
			case PEARL_AWAY -> counts[MobKit.Kind.PEARL.ordinal()] > 0 && !mind.furyActive(now);
			case WIND -> counts[MobKit.Kind.WIND.ordinal()] > 0 && Perception.perceived(mind, now) && throwable(mob, target);
			case SWAP -> !MobKit.spare(mob).isEmpty() && !MobKit.hasRod(mob) && now - mind.swapAt >= GroundItems.SWAP_GAP;
			default -> false;
		};
	}

	/**
	 * lanzamiento_ok: the player 3 to 10 blocks off and the lob's way clear: from its eyes to a point 1.5 above the
	 * middle of the way, and from there to the player's chest.
	 */
	public static boolean throwable(Mob mob, Player target) {
		MobMind mind = MobAi.mind(mob);
		long now = mob.level().getGameTime();
		if (mind != null && mind.throwAt == now && mind.throwFor == target) {
			return mind.throwOk;
		}
		// Only for a mob that has something to throw (a splash potion, a wind charge) or a pearl: for the rest both read 0,
		// and the rays are not cast (the simulator does the same, docs/red_mob_v4_mod_estado.md).
		int[] counts = MobKit.counts(mob);
		boolean throwing = counts[MobKit.Kind.SPLASH.ordinal()] > 0 || counts[MobKit.Kind.WIND.ordinal()] > 0;
		boolean ok = throwing && throwableNow(mob, target);
		Vec3 spot = counts[MobKit.Kind.PEARL.ordinal()] > 0 ? pearlInNow(mob, target) : null;
		if (mind != null) {
			mind.throwAt = now;
			mind.throwFor = target;
			mind.throwOk = ok;
			mind.pearlSpot = spot;
		}
		return ok;
	}

	private static boolean throwableNow(Mob mob, Player target) {
		double d = mob.distanceTo(target);
		if (d < THROW_MIN || d > THROW_MAX) {
			return false;
		}
		Vec3 eye = mob.getEyePosition();
		Vec3 chest = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
		Vec3 apex = eye.add(chest).scale(0.5).add(0.0, 1.5, 0.0);
		return clear(mob, eye, apex) && clear(mob, apex, chest);
	}

	private static boolean clear(Mob mob, Vec3 from, Vec3 to) {
		return mob.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
	}

	/**
	 * perla_destino_ok's spot: at the player on a pillar or up high, if it sees them within 16; else a spot 2 blocks
	 * from them on its own side with a floor (at most 3 down), no lava or drop, within 16 of it and with a clear line
	 * from its eyes. Null for none.
	 */
	public static Vec3 pearlIn(Mob mob, Player target) {
		MobMind mind = MobAi.mind(mob);
		if (mind != null) {
			// worked out with lanzamiento_ok, once a tick
			throwable(mob, target);
			return mind.pearlSpot;
		}
		return pearlInNow(mob, target);
	}

	private static Vec3 pearlInNow(Mob mob, Player target) {
		if (mob.distanceTo(target) > PEARL_RANGE) {
			return null;
		}
		Heights.State h = Heights.of(target);
		if ((h.pillar || h.overGround >= Heights.UP) && ObsM1.sees(mob, target.getX(), target.getY() + 0.5, target.getZ())) {
			return target.position().add(0.0, 0.1, 0.0);
		}
		double dx = mob.getX() - target.getX();
		double dz = mob.getZ() - target.getZ();
		double d = Math.hypot(dx, dz);
		if (d < PEARL_NEAR + 1.0) {
			return null;
		}
		Vec3 spot = floorNear(mob, target.getX() + dx / d * PEARL_NEAR, target.getY(), target.getZ() + dz / d * PEARL_NEAR);
		if (spot == null || mob.position().distanceTo(spot) > PEARL_RANGE || !clear(mob, mob.getEyePosition(), spot.add(0.0, 1.0, 0.0))) {
			return null;
		}
		return spot;
	}

	/** A pearl away: a spot 12–16 from the player, on the side away from them, hidden from their eyes if one is; null for none. */
	public static Vec3 pearlAway(Mob mob, Player target) {
		double dx = mob.getX() - target.getX();
		double dz = mob.getZ() - target.getZ();
		double base = Math.atan2(dz, dx);
		Vec3 fallback = null;
		for (double r : new double[] {14.0, PEARL_FAR_MIN, PEARL_FAR_MAX}) {
			for (double turn : new double[] {0.0, 0.5, -0.5, 1.0, -1.0, 1.5, -1.5}) {
				double a = base + turn;
				Vec3 spot = floorNear(mob, target.getX() + Math.cos(a) * r, mob.getY(), target.getZ() + Math.sin(a) * r);
				if (spot == null || mob.position().distanceTo(spot) > PEARL_RANGE + 4.0 || !clear(mob, mob.getEyePosition(), spot.add(0.0, 1.0, 0.0))) {
					continue;
				}
				if (!clear(mob, target.getEyePosition(), spot.add(0.0, 1.2, 0.0))) {
					return spot;
				}
				if (fallback == null) {
					fallback = spot;
				}
			}
		}
		return fallback;
	}

	/** A spot with a floor under it near (x, y, z): room for a mob, no lava or drop; looked for from 2 up to 3 down. */
	static Vec3 floorNear(Mob mob, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int by = (int) Math.floor(y);
		for (int dy = 2; dy >= -3; dy--) {
			pos.set(Math.floor(x), by + dy, Math.floor(z));
			var level = mob.level();
			if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty()
				&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
				&& !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
				if (Terrain.danger(level, x, z, pos.getY())) {
					return null;
				}
				return new Vec3(x, pos.getY(), z);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- the C block

	/** The eighteen of block C, written from {@code at}. */
	public static void observe(Mob mob, MobMind mind, Player target, long now, float[] out, int at) {
		int[] counts = MobKit.counts(mob);
		out[at] = counts[MobKit.Kind.HEAL.ordinal()] / 2.0F;
		out[at + 1] = counts[MobKit.Kind.BUFF.ordinal()] / 2.0F;
		out[at + 2] = counts[MobKit.Kind.SPLASH.ordinal()] / 3.0F;
		out[at + 3] = counts[MobKit.Kind.FOOD.ordinal()] / 3.0F;
		out[at + 4] = counts[MobKit.Kind.PEARL.ordinal()] / 2.0F;
		out[at + 5] = counts[MobKit.Kind.WIND.ordinal()] / 3.0F;
		out[at + 6] = (float) ObsM1.clip(cooldown(mind, now) / 40.0, 0.0, 2.0);
		out[at + 7] = busy(mind) ? 1.0F : 0.0F;
		out[at + 8] = mob.hasEffect(MobEffects.STRENGTH) ? 1.0F : 0.0F;
		out[at + 9] = mob.hasEffect(MobEffects.SPEED) ? 1.0F : 0.0F;
		out[at + 10] = mob.hasEffect(MobEffects.REGENERATION) ? 1.0F : 0.0F;
		out[at + 11] = harmful(mob) ? 1.0F : 0.0F;
		out[at + 12] = target.hasEffect(MobEffects.SLOWNESS) ? 1.0F : 0.0F;
		out[at + 13] = target.hasEffect(MobEffects.POISON) ? 1.0F : 0.0F;
		out[at + 14] = target.hasEffect(MobEffects.WEAKNESS) ? 1.0F : 0.0F;
		out[at + 15] = target.hasEffect(MobEffects.STRENGTH) || target.hasEffect(MobEffects.SPEED) || target.hasEffect(MobEffects.RESISTANCE)
			|| target.hasEffect(MobEffects.REGENERATION) ? 1.0F : 0.0F;
		out[at + 16] = pearlIn(mob, target) != null ? 1.0F : 0.0F;
		out[at + 17] = throwable(mob, target) ? 1.0F : 0.0F;
	}

	private static boolean harmful(LivingEntity entity) {
		for (MobEffectInstance effect : entity.getActiveEffects()) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- running it

	/** Each tick (MobAi): an object asked for by the decision just made is started; one under way goes on. */
	public static void tick(MobMind mind, long now) {
		Mob mob = mind.mob;
		if (mind.pearl != null && (mind.pearl.isRemoved() || !mind.pearl.isAlive())) {
			// the pearl landed and carried it: 2 damage, as vanilla's pearl hurts a player 5
			if (mob.isAlive() && mob.level() instanceof ServerLevel level) {
				mob.hurtServer(level, level.damageSources().enderPearl(), PEARL_DAMAGE);
			}
			mind.pearl = null;
		}
		if (mind.itemTicks == 0) {
			int asked = mind.decision.item();
			if (asked > 0 && mind.decidedAt == now && allowed(mob, mind, mind.target, asked, now)) {
				start(mob, mind, mind.target, asked, now);
			}
			return;
		}
		if (Posture.isStaggered(mob, now) || !mob.isAlive() || mind.itemTarget != null && !mind.itemTarget.isAlive()) {
			stop(mob, mind);
			return;
		}
		int k = mind.itemAction;
		if (k == SPLASH || k == WIND || k == PEARL_IN || k == PEARL_AWAY) {
			if (mind.itemTarget != null) {
				mob.getLookControl().setLookAt(mind.itemTarget, 30.0F, 30.0F);
			}
			if (mind.itemTicks % 4 == 0) {
				mob.swing(InteractionHand.OFF_HAND);
			}
		} else if ((k == HEAL || k == BUFF || k == EAT) && mind.itemTicks % 4 == 0 && mob.level() instanceof ServerLevel level) {
			ItemStack shown = mind.itemShown;
			if (shown != null && !shown.isEmpty()) {
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shown.getItem()), mob.getX(), mob.getEyeY() - 0.2, mob.getZ(), 3, 0.15, 0.1, 0.15, 0.05);
			}
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), k == EAT ? SoundEvents.GENERIC_EAT.value() : SoundEvents.GENERIC_DRINK.value(),
				SoundSource.HOSTILE, 0.6F, 0.9F + mob.getRandom().nextFloat() * 0.2F);
		}
		if (--mind.itemTicks > 0) {
			return;
		}
		finish(mob, mind, now);
	}

	/** Starts object k: the warning, what it shows in its off hand, the slowing down. */
	public static boolean start(Mob mob, MobMind mind, Player target, int k, long now) {
		mind.itemAction = k;
		mind.itemTarget = target;
		mind.itemTicks = switch (k) {
			case HEAL, BUFF, EAT -> DRINK_TICKS;
			case SPLASH -> THROW_WARNING;
			case PEARL_IN, PEARL_AWAY -> PEARL_WARNING;
			case WIND -> WIND_WARNING;
			case SWAP -> SWAP_TICKS;
			default -> 0;
		};
		if (mind.itemTicks == 0) {
			return false;
		}
		MobKit.Kind kind = kindOf(k);
		MobKit.Entry entry = kind == null ? null : MobKit.peek(mob, kind);
		mind.itemShown = entry == null ? null : MobKit.stack(entry);
		// Shown in the off hand while it lasts, when that hand is free: never dropped (chance 0), put back after.
		if (mind.itemShown != null && mob.getOffhandItem().isEmpty()) {
			mind.shownChance = mob.getDropChances().byEquipment(EquipmentSlot.OFFHAND);
			mob.setItemSlot(EquipmentSlot.OFFHAND, mind.itemShown.copy());
			mob.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
			mind.showing = true;
		}
		if (k == HEAL || k == BUFF || k == EAT || k == SWAP) {
			AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null && speed.getModifier(SLOW) == null) {
				speed.addTransientModifier(new AttributeModifier(SLOW, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		if (k == SPLASH || k == WIND || k == PEARL_IN || k == PEARL_AWAY) {
			dev.forja.combat.CombatFeedback.telegraph(mob, mind.itemTicks);
		}
		return true;
	}

	private static MobKit.Kind kindOf(int k) {
		return switch (k) {
			case HEAL -> MobKit.Kind.HEAL;
			case BUFF -> MobKit.Kind.BUFF;
			case EAT -> MobKit.Kind.FOOD;
			case SPLASH -> MobKit.Kind.SPLASH;
			case PEARL_IN, PEARL_AWAY -> MobKit.Kind.PEARL;
			case WIND -> MobKit.Kind.WIND;
			default -> null;
		};
	}

	/** Stops what is under way, losing nothing: the off hand and the speed come back. */
	public static void stop(Mob mob, MobMind mind) {
		mind.itemTicks = 0;
		mind.itemAction = NONE;
		mind.itemTarget = null;
		restore(mob, mind);
	}

	private static void restore(Mob mob, MobMind mind) {
		if (mind.showing) {
			mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			mob.setDropChance(EquipmentSlot.OFFHAND, mind.shownChance);
			mind.showing = false;
		}
		mind.itemShown = null;
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SLOW);
		}
	}

	private static void finish(Mob mob, MobMind mind, long now) {
		int k = mind.itemAction;
		Player target = mind.itemTarget;
		restore(mob, mind);
		mind.itemAction = NONE;
		mind.itemTarget = null;
		mind.itemReadyAt = now + COOLDOWN[k];
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		switch (k) {
			case HEAL -> {
				MobKit.Entry entry = MobKit.take(mob, MobKit.Kind.HEAL);
				if (entry != null) {
					drink(mob, entry, true);
				}
			}
			case BUFF -> {
				MobKit.Entry entry = MobKit.take(mob, MobKit.Kind.BUFF);
				if (entry != null) {
					drink(mob, entry, false);
				}
			}
			case EAT -> {
				if (MobKit.take(mob, MobKit.Kind.FOOD) != null) {
					mob.heal(FOOD_HEAL);
					level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_BURP, SoundSource.HOSTILE, 0.6F, 0.8F);
				}
			}
			case SPLASH -> {
				if (target != null && throwable(mob, target)) {
					MobKit.Entry entry = MobKit.take(mob, MobKit.Kind.SPLASH);
					if (entry != null) {
						throwPotion(mob, target, MobKit.stack(entry));
					}
				}
			}
			case PEARL_IN, PEARL_AWAY -> {
				if (target != null) {
					Vec3 to = k == PEARL_IN ? pearlIn(mob, target) : pearlAway(mob, target);
					if (to == null && k == PEARL_AWAY) {
						Vec3 away = mob.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
						to = away.lengthSqr() < 1.0E-6 ? null : mob.position().add(away.normalize().scale(PEARL_FAR_MIN));
					}
					if (to != null && MobKit.take(mob, MobKit.Kind.PEARL) != null) {
						mind.pearl = throwPearl(mob, to);
					}
				}
			}
			case WIND -> {
				if (target != null && throwable(mob, target) && MobKit.take(mob, MobKit.Kind.WIND) != null) {
					throwWind(mob, target);
				}
			}
			case SWAP -> swap(mob, mind, now);
			default -> {
			}
		}
	}

	/** Drinks one potion of the kit: a healing one heals 4 per level straight (§4.8); anything else is vanilla's effect. */
	private static void drink(Mob mob, MobKit.Entry entry, boolean heal) {
		Holder<Potion> potion = MobKit.potion(entry.variant());
		ItemStack stack = MobKit.stack(entry);
		PotionContents contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
		if (contents != null) {
			for (MobEffectInstance effect : contents.getAllEffects()) {
				Holder<MobEffect> e = effect.getEffect();
				if (e.value() == MobEffects.INSTANT_HEALTH.value()) {
					mob.heal(HEAL_PER_LEVEL * (effect.getAmplifier() + 1));
				} else {
					int duration = heal || effect.getDuration() <= 1 ? effect.getDuration() : BUFF_TICKS;
					mob.addEffect(new MobEffectInstance(e, duration, effect.getAmplifier()));
				}
			}
		} else if (heal && potion == null) {
			mob.heal(HEAL_PER_LEVEL);
		}
	}

	/** A splash potion lobbed at where the player will be by the time it lands (lead 1.0), as a witch throws. */
	public static ThrownSplashPotion throwPotion(Mob mob, LivingEntity target, ItemStack stack) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return null;
		}
		ThrownSplashPotion thrown = new ThrownSplashPotion(level, mob, stack.copyWithCount(1));
		Vec3 aim = lead(mob, target, 0.7);
		double dx = aim.x - mob.getX();
		double dy = target.getEyeY() - 1.1 - mob.getY();
		double dz = aim.z - mob.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		thrown.setXRot(thrown.getXRot() + 20.0F);
		thrown.shoot(dx, dy + flat * 0.2, dz, 0.75F, 4.0F);
		level.addFreshEntity(thrown);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.0F, 0.8F + mob.getRandom().nextFloat() * 0.4F);
		return thrown;
	}

	/** Where the target will be when something flying at {@code speed} blocks a tick gets there: its motion times the flight. */
	private static Vec3 lead(Mob mob, LivingEntity target, double speed) {
		double ticks = mob.distanceTo(target) / Math.max(0.1, speed);
		Vec3 v = target.getDeltaMovement();
		return target.position().add(v.x * ticks, 0.0, v.z * ticks);
	}

	/** An ender pearl from the kit, lobbed at a spot (MobActions.throwPearl's lob, owned by the mob). */
	public static ThrownEnderpearl throwPearl(Mob mob, Vec3 to) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return null;
		}
		ThrownEnderpearl pearl = new ThrownEnderpearl(level, mob, new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL));
		double dx = to.x - pearl.getX();
		double dz = to.z - pearl.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		double dy = to.y - pearl.getY();
		// The lob that lands there: 45 degrees up, or 60 when the spot is almost as high as it is far, at the speed that
		// brings it down on the spot under a thrown item's flight (lobSpeed).
		double x = Math.max(1.0, flat);
		double tan = x - dy > 0.5 ? 1.0 : Math.sqrt(3.0);
		float speed = (float) lobSpeed(x, dy, tan, PEARL_GRAVITY, 0.99);
		pearl.shoot(dx, x * tan, dz, speed, 0.0F);
		level.addFreshEntity(pearl);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.HOSTILE, 0.5F, 0.4F);
		return pearl;
	}

	/** A thrown ender pearl's gravity per tick. */
	public static final double PEARL_GRAVITY = 0.03;

	/**
	 * The speed a thrown item needs, launched at the slope {@code tan} (rise over run), to come down {@code dy} above its
	 * start after {@code x} blocks, flying as vanilla's thrown items fly: each tick it moves, then slows by {@code drag}
	 * and falls by {@code gravity}. Found by halving (the simulator copies it: at 45 degrees, 8 blocks off and 2.5 up it
	 * is about 0.615). Between 0.3 and 3.
	 */
	public static double lobSpeed(double x, double dy, double tan, double gravity, double drag) {
		double cos = 1.0 / Math.sqrt(1.0 + tan * tan);
		double sin = tan * cos;
		double lo = 0.3;
		double hi = 3.0;
		for (int i = 0; i < 24; i++) {
			double v = (lo + hi) / 2.0;
			double vx = v * cos;
			double vy = v * sin;
			double px = 0.0;
			double py = 0.0;
			double yAtX = Double.NEGATIVE_INFINITY;
			for (int t = 0; t < 200; t++) {
				double nx = px + vx;
				double ny = py + vy;
				if (nx >= x) {
					yAtX = py + (ny - py) * (x - px) / Math.max(1.0E-9, nx - px);
					break;
				}
				px = nx;
				py = ny;
				vx *= drag;
				vy = vy * drag - gravity;
				if (py < dy - 64.0) {
					break;
				}
			}
			if (yAtX < dy) {
				lo = v;
			} else {
				hi = v;
			}
		}
		return (lo + hi) / 2.0;
	}

	/** Vanilla's wind charge, thrown at the player's feet the way a player throws one (1.5 blocks a tick). */
	public static WindCharge throwWind(Mob mob, LivingEntity target) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return null;
		}
		Vec3 eye = mob.getEyePosition();
		WindCharge charge = new WindCharge(level, eye.x, eye.y - 0.1, eye.z, Vec3.ZERO);
		charge.setOwner(mob);
		Vec3 aim = lead(mob, target, 1.5);
		charge.shoot(aim.x - eye.x, target.getY() + 0.2 - (eye.y - 0.1), aim.z - eye.z, 1.5F, 1.0F);
		level.addFreshEntity(charge);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.HOSTILE, 0.8F, 0.9F);
		return charge;
	}

	/** Main hand and spare slot change places, each with its drop chance. */
	private static void swap(Mob mob, MobMind mind, long now) {
		ItemStack spare = MobKit.spare(mob).copy();
		if (spare.isEmpty() || MobKit.hasRod(mob)) {
			return;
		}
		ItemStack main = mob.getMainHandItem().copy();
		boolean mainPicked = mob.getDropChances().isPreserved(EquipmentSlot.MAINHAND);
		float mainChance = mob.getDropChances().byEquipment(EquipmentSlot.MAINHAND);
		boolean sparePicked = mob.getDropChances().isPreserved(EquipmentSlot.BODY);
		float spareChance = mob.getDropChances().byEquipment(EquipmentSlot.BODY);
		mob.setItemSlot(EquipmentSlot.MAINHAND, spare);
		if (sparePicked) {
			mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		} else {
			mob.setDropChance(EquipmentSlot.MAINHAND, spareChance);
		}
		MobKit.setSpare(mob, main, mainPicked, mainChance);
		mind.swapAt = now;
		mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.HOSTILE, 0.8F, 1.0F);
	}

	// ---------------------------------------------------------------- the rules' choice

	/**
	 * What the rules do with the kit, when nothing is under way: drink a healing potion under 40 % health out of the
	 * player's reach; eat under 50 % more than 4 blocks off; drink a buff once, fighting; a pearl in at a player on a
	 * pillar for 2 seconds, or more than 10 blocks off; a pearl away under 25 % if it is running; a wind charge at a
	 * player up high or by an edge; a splash potion at 4 to 8 blocks, one the player does not already suffer; a shooter
	 * takes its bow from the spare slot beyond 8 blocks, and its blade within 3.
	 */
	public static int ruleItem(MobMind mind, Player target, long now) {
		Mob mob = mind.mob;
		if (!on() || target == null || busy(mind) || cooldown(mind, now) > 0) {
			return NONE;
		}
		double d = mob.distanceTo(target);
		float health = mob.getHealth() / Math.max(1.0F, mob.getMaxHealth());
		boolean inReach = d <= Reach.player(target) + 1.5;
		if (health < 0.4F && !inReach && allowed(mob, mind, target, HEAL, now)) {
			return HEAL;
		}
		if (health < 0.5F && d > 4.0 && allowed(mob, mind, target, EAT, now)) {
			return EAT;
		}
		if (d < 12.0 && !mob.hasEffect(MobEffects.STRENGTH) && !mob.hasEffect(MobEffects.SPEED) && allowed(mob, mind, target, BUFF, now)) {
			return BUFF;
		}
		Heights.State h = Heights.of(target);
		if ((Heights.upTicks(h, now) >= 40 || d > 10.0) && allowed(mob, mind, target, PEARL_IN, now)) {
			return PEARL_IN;
		}
		if (health < 0.25F && (mind.decision.tactic() == Tactic.RETIRARSE || mind.routed) && allowed(mob, mind, target, PEARL_AWAY, now)) {
			return PEARL_AWAY;
		}
		if ((h.overGround >= Heights.UP || h.edge < 1.0 || Terrain.dangerNear(target) != null) && allowed(mob, mind, target, WIND, now)) {
			return WIND;
		}
		if (d >= 4.0 && d <= 8.0 && mind.random.nextFloat() < 0.3F && allowed(mob, mind, target, SPLASH, now)) {
			MobKit.Entry entry = MobKit.peek(mob, MobKit.Kind.SPLASH);
			Holder<Potion> potion = entry == null ? null : MobKit.potion(entry.variant());
			boolean already = false;
			if (potion != null) {
				for (MobEffectInstance effect : potion.value().getEffects()) {
					already |= target.hasEffect(effect.getEffect());
				}
			}
			if (!already) {
				return SPLASH;
			}
		}
		if (mob instanceof RangedAttackMob && allowed(mob, mind, target, SWAP, now)) {
			ItemStack spare = MobKit.spare(mob);
			boolean spareRanged = spare.getItem() instanceof BowItem || spare.getItem() instanceof CrossbowItem;
			boolean mainRanged = mob.getMainHandItem().getItem() instanceof BowItem || mob.getMainHandItem().getItem() instanceof CrossbowItem;
			if (spareRanged && !mainRanged && d > 8.0 || mainRanged && !spareRanged && d < 3.0) {
				return SWAP;
			}
		}
		return NONE;
	}
}
