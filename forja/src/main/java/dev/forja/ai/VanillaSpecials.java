package dev.forja.ai;

import java.util.List;

import dev.forja.combat.CombatAnim;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.CombatFeedback;
import dev.forja.combat.CombatStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The warned special attacks of vanilla's monsters (ideas 21 to 30 of the plan), and the movesets that
 * hand them out. Every one of them announces itself: a crouch, a glow, a sound, a mark where it will land.
 */
public final class VanillaSpecials {
	private VanillaSpecials() {
	}

	/** The moveset of a mob type: its specials in slot order (at most 4). */
	public static List<Special> of(EntityType<?> type, Mob mob) {
		// v4's specials go in the third slot (especial 3, docs/red_mob_v4_diseno.md §3.1 and §4.6): the knockback
		// arrow for the archers, the swipe for the spiders, the hook (with a fishing rod it picked up) for the bodies.
		if (type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK || type == EntityTypes.ZOMBIE_VILLAGER) {
			return List.of(LUNGE, Special.NONE, HOOK);
		}
		if (type == EntityTypes.DROWNED) {
			return List.of(LUNGE, TRIDENT_THRUST, HOOK);
		}
		if (type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER) {
			return List.of(POUNCE, WEB, SWIPE);
		}
		if (type == EntityTypes.SKELETON || type == EntityTypes.STRAY || type == EntityTypes.BOGGED) {
			return List.of(BACKSTEP, VOLLEY, KNOCKBACK_ARROW);
		}
		if (type == EntityTypes.PILLAGER) {
			return List.of(Special.NONE, Special.NONE, KNOCKBACK_ARROW);
		}
		if (type == EntityTypes.ENDERMAN) {
			return List.of(BLINK);
		}
		if (type == EntityTypes.VINDICATOR) {
			return List.of(CLEAVE, Special.NONE, HOOK);
		}
		if (type == EntityTypes.WITCH) {
			return List.of(MARKED_POTION);
		}
		if (type == EntityTypes.PIGLIN_BRUTE) {
			return List.of(CHARGE, Special.NONE, HOOK);
		}
		if (type == EntityTypes.PIGLIN || type == EntityTypes.ZOMBIFIED_PIGLIN || type == EntityTypes.WITHER_SKELETON) {
			return List.of(Special.NONE, Special.NONE, HOOK);
		}
		if (type == EntityTypes.CREEPER) {
			return List.of(STALK);
		}
		return List.of();
	}

	// --- Helpers --------------------------------------------------------------------------------------

	static void particles(Mob mob, ParticleOptions type, double x, double y, double z, int count, double spread) {
		if (mob.level() instanceof ServerLevel level) {
			level.sendParticles(type, x, y, z, count, spread, spread * 0.5, spread, 0.05);
		}
	}

	static void sound(Mob mob, SoundEvent sound, float volume, float pitch) {
		mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
	}

	static Vec3 flatTo(Mob mob, Vec3 to) {
		Vec3 flat = new Vec3(to.x - mob.getX(), 0.0, to.z - mob.getZ());
		return flat.lengthSqr() < 1.0E-7 ? Vec3.ZERO : flat.normalize();
	}

	/** Shooting needs a bow in hand (a skeleton that lost it cannot loose anything). */
	static boolean holdsBow(Mob mob) {
		return mob.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem
			|| mob.getOffhandItem().getItem() instanceof net.minecraft.world.item.BowItem;
	}

	static boolean sees(Mob mob, Player target) {
		return mob.getSensing().hasLineOfSight(target);
	}

	/** Hits the target if the mob touches it now: once per run. */
	static boolean contact(Mob mob, Player target, SpecialRunner.Run run, float extra) {
		if (!run.hit && mob.getBoundingBox().inflate(0.4).intersects(target.getBoundingBox()) && mob.level() instanceof ServerLevel level) {
			run.hit = true;
			mob.swing(InteractionHand.MAIN_HAND);
			boolean landed = mob.doHurtTarget(level, target);
			if (landed && extra > 0.0F) {
				target.invulnerableTime = 0;
				target.hurtServer(level, level.damageSources().mobAttack(mob), extra);
			}
			return landed;
		}
		return false;
	}

	// --- The specials -----------------------------------------------------------------------------------

	/** Zombies: crouch, then leap at the player from middle range; a hit also holds them back. */
	public static final Special LUNGE = new Special(CombatStats.LUNGE, 12, 100, 200, 0.3) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			CombatConfig cfg = CombatConfig.get();
			double d = mob.distanceTo(target);
			// Not from where its weapon already reaches: a flail's or a lance's reach pushes the nearest leap out
			// as far (a flail, 3.5 -> 6.5), so it strikes from its length instead of leaping into the player's face.
			return cfg.zombieLunge && mob.onGround() && d >= cfg.lungeMinDistance + Reach.actionExtra(mob) && d <= cfg.lungeMaxDistance && sees(mob, target)
				&& !dev.forja.world.Elites.isElite(mob)
				&& !Duels.watching(mob)
				// A spear has a charge of its own (SpearUseGoal, warned in SpearUseGoalMixin): a lunge on top cut it
				// short every time and the spear hardly ever went in.
				&& !mob.getMainHandItem().has(net.minecraft.core.component.DataComponents.KINETIC_WEAPON)
				&& (dev.forja.combat.AttackTokens.holds(target, mob) || dev.forja.combat.AttackTokens.free(target, Aggression.maxAttackers(mob, target)));
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			dev.forja.combat.AttackTokens.tryAcquire(target, mob, Aggression.maxAttackers(mob, target));
			CombatFeedback.lungeTelegraph(mob);
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			CombatConfig cfg = CombatConfig.get();
			Vec3 dir = flatTo(mob, target.position());
			mob.setDeltaMovement(dir.x * cfg.lungeSpeed, cfg.lungeLift, dir.z * cfg.lungeSpeed);
			mob.hurtMarked = true;
			CombatAnim.broadcast(mob, CombatAnim.Kind.LEAP, 25, CombatAnim.Kind.LEAP_FORWARD, 0.0F);
		}

		@Override
		public boolean follow(Mob mob, Player target, SpecialRunner.Run run, int tick) {
			if (contact(mob, target, run, 0.0F) && run.hit) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CombatConfig.get().lungeGrabTicks, 1));
			}
			boolean over = tick > 3 && mob.onGround() || tick > 25;
			if (over) {
				dev.forja.combat.AttackTokens.release(target, mob);
			}
			return !over;
		}
	};

	/** Spiders: a short crouch, then a long pounce. */
	public static final Special POUNCE = new Special("salto_arana", 10, 60, 120, 0.06) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			double d = mob.distanceTo(target);
			return mob.onGround() && d >= 2.5 && d <= 6.0 && sees(mob, target);
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			CombatAnim.broadcast(mob, CombatAnim.Kind.LUNGE, this.windup);
			sound(mob, SoundEvents.SPIDER_AMBIENT, 1.0F, 1.6F);
			particles(mob, ParticleTypes.POOF, mob.getX(), mob.getY() + 0.2, mob.getZ(), 8, 0.4);
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			Vec3 dir = flatTo(mob, target.position());
			mob.setDeltaMovement(dir.x * 0.7, 0.45, dir.z * 0.7);
			mob.hurtMarked = true;
			CombatAnim.broadcast(mob, CombatAnim.Kind.LEAP, 25, CombatAnim.Kind.LEAP_FORWARD, 0.0F);
		}

		@Override
		public boolean follow(Mob mob, Player target, SpecialRunner.Run run, int tick) {
			contact(mob, target, run, 0.0F);
			return !(tick > 3 && mob.onGround() || tick > 25);
		}
	};

	/** Spiders: a shadow under the player's feet, then a web there (gone again after 5 seconds). */
	public static final Special WEB = new Special("telarana", 15, 160, 260, 0.02) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			double d = mob.distanceTo(target);
			return d >= 4.0 && d <= 12.0 && sees(mob, target) && target.onGround();
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			run.mark = target.position();
			sound(mob, SoundEvents.SPIDER_STEP, 1.0F, 0.6F);
		}

		@Override
		public void warning(Mob mob, Player target, SpecialRunner.Run run, int left) {
			if (left % 3 == 0) {
				particles(mob, ParticleTypes.SQUID_INK, run.mark.x, run.mark.y + 0.05, run.mark.z, 6, 0.35);
			}
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			BlockPos at = BlockPos.containing(run.mark);
			if (mob.level() instanceof ServerLevel level && level.getBlockState(at).isAir()) {
				level.setBlockAndUpdate(at, Blocks.COBWEB.defaultBlockState());
				TemporaryBlocks.add(level, at, Blocks.COBWEB, 100);
			}
		}
	};

	/** Skeletons: when the player is on top of them, a hop back and a quick shot. */
	public static final Special BACKSTEP = new Special("paso_atras", 4, 80, 140, 0.15) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			return mob.onGround() && mob.distanceTo(target) < 3.0 && mob instanceof RangedAttackMob && holdsBow(mob);
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			sound(mob, SoundEvents.SKELETON_STEP, 1.0F, 1.5F);
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			Vec3 away = flatTo(mob, target.position()).scale(-1.0);
			mob.setDeltaMovement(away.x * 0.6, 0.35, away.z * 0.6);
			mob.hurtMarked = true;
			CombatAnim.broadcast(mob, CombatAnim.Kind.LEAP, 20, CombatAnim.Kind.LEAP_BACK, 0.0F);
		}

		@Override
		public boolean follow(Mob mob, Player target, SpecialRunner.Run run, int tick) {
			if (tick == 5 && mob instanceof RangedAttackMob archer && sees(mob, target)) {
				archer.performRangedAttack(target, 0.8F);
			}
			return tick < 5;
		}
	};

	/** Skeletons: a long, glowing draw, then three arrows in a fan. */
	public static final Special VOLLEY = new Special("andanada", 30, 200, 320, 0.02) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			double d = mob.distanceTo(target);
			return d >= 6.0 && d <= 16.0 && sees(mob, target) && mob instanceof RangedAttackMob && holdsBow(mob)
				&& !Squad.allyInLine(mob, target);
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			CombatAnim.broadcast(mob, CombatAnim.Kind.TELEGRAPH, this.windup);
			sound(mob, SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 1.4F);
			if (mob.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem) {
				mob.startUsingItem(InteractionHand.MAIN_HAND);
			}
		}

		@Override
		public void warning(Mob mob, Player target, SpecialRunner.Run run, int left) {
			if (left % 4 == 0) {
				particles(mob, ParticleTypes.ENCHANTED_HIT, mob.getX(), mob.getEyeY(), mob.getZ(), 6, 0.3);
			}
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			mob.stopUsingItem();
			if (!(mob instanceof RangedAttackMob archer)) {
				return;
			}
			for (int i = 0; i < 3; i++) {
				archer.performRangedAttack(target, 1.0F);
			}
			var arrows = mob.level().getEntitiesOfClass(AbstractArrow.class, mob.getBoundingBox().inflate(3.0),
				arrow -> arrow.getOwner() == mob && arrow.tickCount == 0);
			double[] turn = {-10.0, 0.0, 10.0};
			for (int i = 0; i < arrows.size() && i < 3; i++) {
				arrows.get(i).setDeltaMovement(arrows.get(i).getDeltaMovement().yRot((float) Math.toRadians(turn[i])));
			}
		}
	};

	/** Endermen: a sound and a swirl behind the player, then it is there, and swings. */
	public static final Special BLINK = new Special("teletransporte", 12, 160, 240, 0.04) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			double d = mob.distanceTo(target);
			return d >= 3.0 && d <= 16.0 && target.onGround();
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			double yaw = Math.toRadians(target.getYRot());
			run.mark = target.position().add(Math.sin(yaw) * 2.0, 0.0, -Math.cos(yaw) * 2.0);
			mob.level().playSound(null, run.mark.x, run.mark.y, run.mark.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F);
		}

		@Override
		public void warning(Mob mob, Player target, SpecialRunner.Run run, int left) {
			particles(mob, ParticleTypes.PORTAL, run.mark.x, run.mark.y + 1.0, run.mark.z, 10, 0.4);
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			if (mob.randomTeleport(run.mark.x, run.mark.y, run.mark.z, true)) {
				CombatFeedback.telegraph(mob);
			}
		}

		@Override
		public boolean follow(Mob mob, Player target, SpecialRunner.Run run, int tick) {
			// After the jump, the usual warning before the blow.
			if (tick >= CombatConfig.get().windupTicks) {
				double reach = Reach.landing(mob, target);
				if (mob.distanceTo(target) <= reach && mob.level() instanceof ServerLevel level) {
					mob.swing(InteractionHand.MAIN_HAND);
					mob.doHurtTarget(level, target);
				}
				return false;
			}
			mob.getNavigation().stop();
			return true;
		}
	};

	/** Vindicators: a red glow, then an overhead chop that breaks a raised guard. */
	public static final Special CLEAVE = new Special("hachazo", 16, 100, 160, 0.06) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			return mob.distanceTo(target) <= 3.0 && sees(mob, target);
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			CombatAnim.broadcast(mob, CombatAnim.Kind.TELEGRAPH, this.windup);
			sound(mob, SoundEvents.VINDICATOR_AMBIENT, 1.0F, 0.7F);
		}

		@Override
		public void warning(Mob mob, Player target, SpecialRunner.Run run, int left) {
			if (left % 3 == 0) {
				particles(mob, new DustParticleOptions(0xD02020, 1.2F), mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 6, 0.3);
			}
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			mob.swing(InteractionHand.MAIN_HAND);
			if (mob.distanceTo(target) > 3.5 || !(mob.level() instanceof ServerLevel level)) {
				return;
			}
			// Any raised guard, even one not yet set: the chop comes down through it.
			ItemStack guard = target.isUsingItem() && target.getUseItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)
				? target.getUseItem() : null;
			if (guard != null) {
				target.getCooldowns().addCooldown(guard, dev.forja.combat.Grip.guardBreakTicks(target.getMainHandItem(), 100));
				target.stopUsingItem();
				CombatFeedback.guardBreak(target);
			}
			mob.doHurtTarget(level, target);
			target.invulnerableTime = 0;
			target.hurtServer(level, level.damageSources().mobAttack(mob), 3.0F);
		}
	};

	/** Witches: a ring on the floor where the potion will burst, then the throw at that spot. */
	public static final Special MARKED_POTION = new Special("pocion_marcada", 20, 80, 140, 0.05) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			double d = mob.distanceTo(target);
			return d >= 4.0 && d <= 12.0 && sees(mob, target);
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			run.mark = target.position();
			sound(mob, SoundEvents.WITCH_CELEBRATE, 1.0F, 1.2F);
		}

		@Override
		public void warning(Mob mob, Player target, SpecialRunner.Run run, int left) {
			if (left % 2 == 0) {
				for (int i = 0; i < 12; i++) {
					double a = i * Math.PI * 2.0 / 12.0;
					particles(mob, ParticleTypes.WITCH, run.mark.x + Math.cos(a) * 1.5, run.mark.y + 0.1, run.mark.z + Math.sin(a) * 1.5, 1, 0.0);
				}
			}
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			var potion = switch (mob.getRandom().nextInt(3)) {
				case 0 -> Potions.HARMING;
				case 1 -> Potions.SLOWNESS;
				default -> Potions.POISON;
			};
			ThrownSplashPotion thrown = new ThrownSplashPotion(mob.level(), mob, PotionContents.createItemStack(Items.SPLASH_POTION, potion));
			double dx = run.mark.x - mob.getX();
			double dz = run.mark.z - mob.getZ();
			double dy = run.mark.y - mob.getEyeY() + Math.sqrt(dx * dx + dz * dz) * 0.2;
			thrown.setXRot(thrown.getXRot() + 20.0F);
			thrown.shoot(dx, dy, dz, 0.75F, 2.0F);
			mob.level().addFreshEntity(thrown);
			sound(mob, SoundEvents.WITCH_THROW, 1.0F, 0.8F);
		}
	};

	/** Piglin brutes: a roar and a crouch, then a charge in a straight line that knocks down whoever it meets. */
	public static final Special CHARGE = new Special("carga_bruto", 15, 140, 220, 0.05) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			double d = mob.distanceTo(target);
			return mob.onGround() && d >= 4.0 && d <= 10.0 && sees(mob, target);
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			CombatAnim.broadcast(mob, CombatAnim.Kind.LUNGE, this.windup);
			sound(mob, SoundEvents.RAVAGER_ROAR, 1.0F, 1.3F);
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			run.mark = flatTo(mob, target.position());
			CombatAnim.broadcast(mob, CombatAnim.Kind.LEAP, 15, CombatAnim.Kind.LEAP_CHARGE, 0.0F);
		}

		@Override
		public boolean follow(Mob mob, Player target, SpecialRunner.Run run, int tick) {
			mob.setDeltaMovement(run.mark.x * 0.9, mob.getDeltaMovement().y, run.mark.z * 0.9);
			mob.hurtMarked = true;
			if (contact(mob, target, run, 2.0F)) {
				Vec3 push = flatTo(mob, target.position());
				target.setDeltaMovement(push.x * 1.2, 0.45, push.z * 1.2);
				target.hurtMarked = true;
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
				return false;
			}
			return tick < 15 && !mob.horizontalCollision;
		}
	};

	/** Drowned with a trident, in the water: bubbles, then a thrust across the gap. */
	public static final Special TRIDENT_THRUST = new Special("estocada_tridente", 12, 100, 180, 0.06) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			double d = mob.distanceTo(target);
			return mob.isInWater() && mob.getMainHandItem().is(Items.TRIDENT) && d >= 3.0 && d <= 8.0 && sees(mob, target);
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			CombatAnim.broadcast(mob, CombatAnim.Kind.TELEGRAPH, this.windup);
			sound(mob, SoundEvents.DROWNED_AMBIENT_WATER, 1.0F, 1.4F);
		}

		@Override
		public void warning(Mob mob, Player target, SpecialRunner.Run run, int left) {
			particles(mob, ParticleTypes.BUBBLE_COLUMN_UP, mob.getX(), mob.getY() + 0.5, mob.getZ(), 6, 0.3);
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			Vec3 dir = target.getEyePosition().subtract(mob.getEyePosition()).normalize();
			mob.setDeltaMovement(dir.scale(1.0));
			mob.hurtMarked = true;
		}

		@Override
		public boolean follow(Mob mob, Player target, SpecialRunner.Run run, int tick) {
			contact(mob, target, run, 2.0F);
			return !run.hit && tick < 12;
		}
	};

	/** Creepers: while the player looks away, it creeps up crouched and quiet; a faint smoke gives it away. */
	public static final Special STALK = new Special("acecho", 0, 200, 300, 0.05) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			double d = mob.distanceTo(target);
			Vec3 look = target.getViewVector(1.0F);
			Vec3 toMob = mob.position().subtract(target.position()).normalize();
			return d >= 5.0 && d <= 12.0 && look.dot(toMob) < 0.3;
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			mob.setShiftKeyDown(true);
			mob.setSilent(true);
		}

		@Override
		public boolean follow(Mob mob, Player target, SpecialRunner.Run run, int tick) {
			Vec3 look = target.getViewVector(1.0F);
			Vec3 toMob = mob.position().subtract(target.position()).normalize();
			boolean over = tick > 80 || mob.distanceTo(target) < 3.0 || look.dot(toMob) > 0.6;
			if (over) {
				mob.setShiftKeyDown(false);
				mob.setSilent(false);
				return false;
			}
			if (tick % 10 == 0) {
				particles(mob, ParticleTypes.SMOKE, mob.getX(), mob.getY() + 0.8, mob.getZ(), 2, 0.2);
			}
			mob.getNavigation().moveTo(target, 0.8);
			return true;
		}
	};

	// --- red_mob_v4: the tools against a player up high without building or breaking (docs/red_mob_v4_diseno.md §4.6)

	/** The tag of a knockback arrow in flight: MobAi pushes whoever it hits (see {@link #knockbackHit}). */
	public static final String KNOCKBACK_TAG = "forja_flecha_empuje";
	/** A knockback arrow's shove: sideways along shooter → player, and up. */
	public static final double KNOCKBACK_PUSH = 0.9;
	public static final double KNOCKBACK_LIFT = 0.15;

	/** Whether the player is somewhere a shove would bring them down: up high, by an edge, or by lava or a drop. */
	static boolean shoveWorth(Player target) {
		Heights.State h = Heights.of(target);
		return h.overGround >= Heights.UP || h.edge < 0.6 || Terrain.dangerNear(target) != null;
	}

	/** Moves a player directly, less what Anclaje takes out of it. */
	static void shove(Player target, double x, double y, double z) {
		double keep = 1.0 - dev.forja.upgrade.Upgrades.anchor(target);
		target.push(x * keep, y * keep, z * keep);
		target.hurtMarked = true;
	}

	/**
	 * Archers (skeleton, stray, bogged, pillager): a glowing, humming draw of 20 ticks, then an arrow that hits for half
	 * and shoves 0.9 sideways (shooter → player) and 0.15 up, as Punch II would. Only against a player it would bring
	 * down: up high, by an edge or by lava or a drop; from 4 to 24 blocks, in sight. 160 to 240 ticks between.
	 */
	public static final Special KNOCKBACK_ARROW = new Special("flecha_empuje", 20, 160, 240, 0.05, true) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			double d = mob.distanceTo(target);
			return d >= 4.0 && d <= 24.0 && mob instanceof RangedAttackMob && (holdsBow(mob) || MobFamily.holdsCrossbow(mob))
				&& sees(mob, target) && shoveWorth(target);
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			CombatAnim.broadcast(mob, CombatAnim.Kind.TELEGRAPH, this.windup);
			sound(mob, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.8F);
			if (holdsBow(mob)) {
				mob.startUsingItem(mob.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
			}
		}

		@Override
		public void warning(Mob mob, Player target, SpecialRunner.Run run, int left) {
			if (left % 3 == 0) {
				particles(mob, ParticleTypes.GLOW, mob.getX(), mob.getEyeY(), mob.getZ(), 4, 0.25);
			}
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			mob.stopUsingItem();
			if (!(mob.level() instanceof ServerLevel level) || !sees(mob, target)) {
				return;
			}
			ItemStack weapon = mob.getMainHandItem().isEmpty() ? new ItemStack(Items.BOW) : mob.getMainHandItem();
			net.minecraft.world.entity.projectile.arrow.Arrow arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(level, mob,
				new ItemStack(Items.ARROW), weapon.copy());
			double dx = target.getX() - mob.getX();
			double dy = target.getY(0.5) - arrow.getY();
			double dz = target.getZ() - mob.getZ();
			double flat = Math.sqrt(dx * dx + dz * dz);
			arrow.shoot(dx, dy + flat * 0.2, dz, 1.6F, 2.0F);
			arrow.setBaseDamage(1.0); // half of an arrow's 2.0
			arrow.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED;
			arrow.setCritArrow(true);
			arrow.addTag(KNOCKBACK_TAG);
			level.addFreshEntity(arrow);
			sound(mob, SoundEvents.SKELETON_SHOOT, 1.0F, 0.6F);
		}
	};

	/** A knockback arrow reached someone: the shove, if it was not blocked (MobAi's AFTER_DAMAGE). */
	public static void knockbackHit(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, boolean blocked) {
		if (blocked || !(victim instanceof Player player) || !(source.getDirectEntity() instanceof AbstractArrow arrow)
			|| !arrow.entityTags().contains(KNOCKBACK_TAG) || !(source.getEntity() instanceof Mob shooter)) {
			return;
		}
		Vec3 dir = flatTo(shooter, player.position());
		if (dir == Vec3.ZERO) {
			return;
		}
		shove(player, dir.x * KNOCKBACK_PUSH, KNOCKBACK_LIFT, dir.z * KNOCKBACK_PUSH);
	}

	/**
	 * Spiders: the swipe. A 6-tick crouch, then a blow with its claws that shoves 1.0 sideways (spider → player).
	 * Within 2 blocks, and only against a player up high or by an edge: what it climbed up the pillar for. 80 to 120
	 * ticks between.
	 */
	public static final Special SWIPE = new Special("zarpazo", 6, 80, 120, 0.2, true) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			if (mob.distanceTo(target) >= 2.0) {
				return false;
			}
			Heights.State h = Heights.of(target);
			return h.overGround >= Heights.UP || h.edge < 0.6;
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			CombatAnim.broadcast(mob, CombatAnim.Kind.TELEGRAPH, this.windup);
			sound(mob, SoundEvents.SPIDER_AMBIENT, 1.0F, 0.6F);
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			mob.swing(InteractionHand.MAIN_HAND);
			if (mob.distanceTo(target) > 2.5 || !(mob.level() instanceof ServerLevel level)) {
				return;
			}
			mob.doHurtTarget(level, target);
			Vec3 dir = flatTo(mob, target.position());
			shove(target, dir.x, 0.1, dir.z);
			particles(mob, ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(1.0), target.getZ(), 1, 0.0);
		}
	};

	/** How far the hook reaches, and how hard it pulls. */
	public static final double HOOK_RANGE = 12.0;
	public static final double HOOK_PULL = 1.0;

	/**
	 * A body with a fishing rod it picked up (MobKit.hasRod): 10 ticks with the line out (the cast's sound and a trail of
	 * bubbles to the player), then a pull of 1.0 towards it and a little up: off the top of a pillar, down it comes.
	 * In sight, 3 to 12 blocks off. 120 to 200 ticks between.
	 */
	public static final Special HOOK = new Special("garfio", 10, 120, 200, 0.05, true) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			double d = mob.distanceTo(target);
			return MobKit.hasRod(mob) && d >= 3.0 && d <= HOOK_RANGE && sees(mob, target);
		}

		@Override
		public void warn(Mob mob, Player target, SpecialRunner.Run run) {
			CombatAnim.broadcast(mob, CombatAnim.Kind.TELEGRAPH, this.windup);
			sound(mob, SoundEvents.FISHING_BOBBER_THROW, 1.0F, 0.6F);
			mob.swing(InteractionHand.MAIN_HAND);
		}

		@Override
		public void warning(Mob mob, Player target, SpecialRunner.Run run, int left) {
			if (!(mob.level() instanceof ServerLevel level)) {
				return;
			}
			Vec3 from = mob.getEyePosition();
			Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
			double share = 1.0 - left / (double) this.windup;
			for (int i = 0; i <= 4; i++) {
				Vec3 at = from.lerp(to, share * i / 4.0);
				level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
			if (mob.distanceTo(target) > HOOK_RANGE || !sees(mob, target)) {
				return;
			}
			Vec3 dir = flatDir(target, mob.position());
			shove(target, dir.x * HOOK_PULL, 0.3, dir.z * HOOK_PULL);
			sound(mob, SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0F, 0.7F);
		}
	};

	private static Vec3 flatDir(net.minecraft.world.entity.Entity from, Vec3 to) {
		if (from == null) {
			return Vec3.ZERO;
		}
		Vec3 flat = new Vec3(to.x - from.getX(), 0.0, to.z - from.getZ());
		return flat.lengthSqr() < 1.0E-7 ? Vec3.ZERO : flat.normalize();
	}
}
