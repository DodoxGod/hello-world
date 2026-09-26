package dev.forja.entity;

import java.util.List;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Mastery;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModEntities;
import dev.forja.registry.ModItems;
import dev.forja.world.Legends;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * El Herrero Caído: what is left of the smith who built the fallen forge, still working. He is woken
 * by an offering on his own dead forge and he fights in three stages, each one asking for something
 * different from the player.
 *
 * <ul>
 *   <li>Under three quarters he calls up the apprentices that died with him.
 *   <li>At half he goes back to the forge: nothing touches him while he reforges, and the only way
 *       through it is to put out the embers he lights around himself.
 *   <li>Under a quarter he brings the sky down, the same shower that buried the forge.
 * </ul>
 *
 * <p>What he leaves behind is his heart, and one piece of what he was carrying.
 */
public class FallenSmith extends Monster implements GeoEntity {
	/** How much health he has: this is not a mob you meet by accident. */
	public static final double HEALTH = 320.0;

	/** Ticks the reforge stage keeps him out of reach. */
	public static final int REFORGE_TICKS = 160;

	/** How many embers have to be put out to break the reforge. */
	public static final int EMBERS = 3;

	/**
	 * The anvils he stands up when his last quarter begins.
	 *
	 * <p>Six of them, and they are not there to fight you — a walking anvil has no target goal at all.
	 * They are there to <b>mend him</b>, which turns his last quarter from "hit him faster" into a
	 * question about what you break first. The anvils are soft, slow and scattered; he is hard, quick
	 * and in your face. Every second spent on one of them is a second he is not being hurt in, and
	 * leaving them up means his health bar stops going down.
	 */
	public static final int FINAL_ANVILS = 6;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("slam");
	private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("roar");
	private static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("strike");
	private static final RawAnimation HOOK = RawAnimation.begin().thenPlay("hook");
	// The fire runs on its own controller, so it can burn however it likes while he does something else.
	private static final RawAnimation FIRE_CALM = RawAnimation.begin().thenLoop("fire_calm");
	private static final RawAnimation FIRE_RAGE = RawAnimation.begin().thenLoop("fire_rage");
	private static final RawAnimation FIRE_FLASH = RawAnimation.begin().thenPlay("fire_flash");
	private static final RawAnimation FIRE_FLASH_HOT = RawAnimation.begin().thenPlay("fire_flash_hot");

	/**
	 * Whether the forge in his chest is running violet. The client has to know, because the fire is drawn
	 * there, and the only thing it can read off him by itself is his health.
	 */
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_RAGING =
		net.minecraft.network.syncher.SynchedEntityData.defineId(FallenSmith.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

	/** How long a heavy blow keeps the forge violet after it lands. */
	public static final int RAGE_TICKS = 70;

	/** The shockwave: how often he can drop the hammer, how far the ring runs and what it costs you. */
	public static final int WAVE_COOLDOWN = 160;
	public static final int WAVE_TICKS = 20;
	public static final double WAVE_REACH = 9.0;
	public static final float WAVE_DAMAGE = 8.0F;
	/** How far off the floor your feet have to be for the ring to pass under them: most of a jump, not a hop. */
	public static final double WAVE_CLEARANCE = 0.5;

	/** The ring that leaves him as his last quarter opens: wider than the shockwave, slower, and harmless. */
	public static final double PHASE_RING_REACH = 12.0;
	public static final int PHASE_RING_TICKS = 26;

	/**
	 * How long each blow hangs in the air before it lands, read off the animation that draws it.
	 *
	 * <p>These are not numbers that felt about right. {@code slam} has the hammer up by twenty-four
	 * ticks, holds it over his head until fifty and brings it down at fifty-four; {@code hook} cocks
	 * the dead arm and throws at eight. Matching them is what makes the move readable — the hammer is
	 * up, so move.
	 */
	// Three times what it used to be. The hammer is enormous and it was coming down like a stick: at
	// eighteen ticks the weight of it was in the model and nowhere else. The animation has to land on
	// the same tick — the "slam" keyframes in tools/generate_assets.py say 2.7 s for exactly this reason.
	// For a day they did not: this was tripled, the animation was left at 0.9 s, and the ring left the
	// floor nearly two seconds after the hammer had hit it. The gametest checks the two agree now.
	public static final int WAVE_WINDUP = 54;
	public static final int HOOK_WINDUP = 8;
	public static final int STRIKE_WINDUP = 10;

	/** The backhand: close range, fast, and the one he punishes you with for standing next to him. */
	public static final int STRIKE_COOLDOWN = 90;
	public static final double STRIKE_REACH = 4.5;
	public static final float STRIKE_DAMAGE = 7.0F;

	/** The hook: how often he can throw the claw, how far it reaches and what the pull costs you. */
	public static final int HOOK_COOLDOWN = 220;
	public static final double HOOK_MIN = 5.0;
	public static final double HOOK_MAX = 16.0;
	public static final float HOOK_DAMAGE = 4.0F;

	/** The violet the forge burns with once he stops holding back, matching the model's hot plate. */
	private static final net.minecraft.core.particles.DustParticleOptions VIOLET =
		new net.minecraft.core.particles.DustParticleOptions(0xC460FF, 1.6F);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final ServerBossEvent bar = new ServerBossEvent(
		java.util.UUID.randomUUID(), Component.translatable("entity.forja.herrero_caido"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6
	);

	private int reforging;
	private int embersLeft;
	private int raging;
	private int waveCooldown = 60;
	private int hookCooldown = 120;
	private int strikeCooldown = 40;
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();
	private int wave;
	private final java.util.Set<java.util.UUID> waveHit = new java.util.HashSet<>();
	/** Where the hammer came down: the ring runs out from there, not from wherever he has walked to since. */
	private Vec3 waveOrigin = Vec3.ZERO;
	private boolean calledHelp;
	private boolean broughtSky;

	public FallenSmith(EntityType<? extends FallenSmith> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 250;
		this.bar.setDarkenScreen(true);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_RAGING, false);
	}

	/** The forge goes violet in his last quarter, while he is reforging, and just after a heavy blow. */
	public boolean isRaging() {
		return this.entityData.get(DATA_RAGING);
	}

	/** Lights the forge violet for a while: what the heavy moves call when they go off. */
	public void lightForge(int ticks) {
		this.raging = Math.max(this.raging, ticks);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 12.0)
			.add(Attributes.ARMOR, 14.0)
			.add(Attributes.ARMOR_TOUGHNESS, 8.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.STEP_HEIGHT, 1.2);
	}

	/** Wakes him up with his own gear on, wherever the offering was made. */
	public static FallenSmith summon(ServerLevel level, net.minecraft.core.BlockPos pos) {
		FallenSmith smith = ModEntities.HERRERO_CAIDO.create(level, EntitySpawnReason.EVENT);
		if (smith == null) {
			throw new IllegalStateException("the fallen smith could not be created");
		}
		smith.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		smith.dress(level);
		level.addFreshEntity(smith);
		level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0F, 0.5F);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 80, 1.0, 1.5, 1.0, 0.05);
		return smith;
	}

	/** His own work: a damascus flail and obsidian steel plate, all of it at full Maestria. */
	private void dress(ServerLevel level) {
		ItemStack flail = Assembler.create(ForgeType.MANGUAL, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.DAMASCO, ForgeMaterial.OBSIDIACERO), level.registryAccess());
		Mastery.setLevel(flail, Mastery.MAX_LEVEL, level.registryAccess());
		this.setItemSlot(EquipmentSlot.MAINHAND, flail);
		this.setItemSlot(EquipmentSlot.HEAD, armour(level, ForgeType.CASCO));
		this.setItemSlot(EquipmentSlot.CHEST, armour(level, ForgeType.PECHERA));
		this.setItemSlot(EquipmentSlot.LEGS, armour(level, ForgeType.GREBAS));
		this.setItemSlot(EquipmentSlot.FEET, armour(level, ForgeType.BOTAS));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 0.0F);
		}
	}

	private ItemStack armour(ServerLevel level, ForgeType type) {
		return Assembler.create(type, List.of(ForgeMaterial.OBSIDIACERO, ForgeMaterial.ESCAMA), level.registryAccess());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return effect.is(MobEffects.WITHER) ? false : super.canBeAffected(effect);
	}

	/** While he is reforging, nothing gets through: the embers are the way in. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (this.reforging > 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 1.8F);
			return false;
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		if (this.raging > 0) {
			this.raging--;
		}
		boolean hot = this.reforging > 0 || this.raging > 0 || this.getHealth() / this.getMaxHealth() <= 0.25F;
		if (hot != this.entityData.get(DATA_RAGING)) {
			this.entityData.set(DATA_RAGING, hot);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, hot ? 0.5F : 1.4F);
		}
		this.forgeBreathes(level, hot);
		if (this.tickCount % 20 == 0) {
			this.watchers(level);
		}
		// He has been burning for a long time and it settles on everything around him.
		if (this.tickCount % 6 == 0) {
			level.sendParticles(dev.forja.registry.ModParticles.CENIZA,
				this.getX(), this.getY(1.4), this.getZ(), 2, 0.6, 0.5, 0.6, 0.01);
		}
		if (this.reforging > 0) {
			this.wave = 0;
			// Whatever he was winding up when he turned back to the forge is not coming. Left alone it
			// used to sit paused for the whole reforge and then land, out of nowhere, as he came out.
			this.windup.cancel();
			this.reforge(level);
			return;
		}
		float share = this.getHealth() / this.getMaxHealth();
		if (!this.calledHelp && share <= 0.75F) {
			this.calledHelp = true;
			this.callApprentices(level);
		}
		if (this.embersLeft == 0 && share <= 0.5F && this.calledHelp && !this.broughtSky) {
			this.startReforge(level);
		}
		if (!this.broughtSky && share <= 0.25F) {
			this.broughtSky = true;
			this.triggerAnim("boss", "roar");
			this.phaseBreak(level);
		}
		if (this.broughtSky && this.tickCount % 60 == 0 && this.getTarget() != null) {
			this.starfall(level, this.getTarget());
		}
		this.heavyMoves(level);
	}

	/**
	 * The two things he does with his hands when you are too far to hit and too close to ignore: he
	 * drops the hammer and sends the floor at you, or he throws the claw on the dead arm and hauls you
	 * back in. One is answered by getting off the ground, the other by staying out of the middle range.
	 */
	private void heavyMoves(ServerLevel level) {
		if (this.wave > 0) {
			this.rollWave(level);
		}
		if (this.waveCooldown > 0) {
			this.waveCooldown--;
		}
		if (this.hookCooldown > 0) {
			this.hookCooldown--;
		}
		if (this.strikeCooldown > 0) {
			this.strikeCooldown--;
		}
		// A blow already on its way finishes first, and while it is on its way he is committed: he
		// plants his feet and stops following you. That is the whole bargain — the move can be dodged
		// because he cannot correct it, and it hurts because he cannot be talked out of it either.
		if (this.windup.charging()) {
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.35, 1.0, 0.35));
			this.windup.tick(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || this.wave > 0) {
			return;
		}
		double distance = this.distanceTo(target);
		// He remembers: against someone who keeps getting out of his wave, he goes in close first (idea 100).
		if (dev.forja.ai.WorldFights.smithPrefersClose(target) && this.strikeCooldown == 0 && distance <= STRIKE_REACH) {
			this.backhand(level);
		} else if (this.waveCooldown == 0 && distance >= 3.0 && distance <= WAVE_REACH + 4.0) {
			this.anvilWave(level);
		} else if (this.strikeCooldown == 0 && distance <= STRIKE_REACH) {
			this.backhand(level);
		} else if (this.hookCooldown == 0 && this.phase() >= 2 && distance >= HOOK_MIN && distance <= HOOK_MAX
			&& this.hasLineOfSight(target)) {
			this.hookIn(level, target);
		}
	}

	/**
	 * Revés: the one move he had an animation for and never used.
	 *
	 * <p>{@code strike} has been sitting in his animation file since the model was drawn — arm back at
	 * four ticks, through at ten, recovered at eighteen — and nothing ever played it. It fills the hole
	 * in his range: the shockwave needs you three blocks out, the hook needs you five, and between those
	 * you could stand in his face and take nothing but ordinary melee.
	 */
	public void backhand(ServerLevel level) {
		this.strikeCooldown = STRIKE_COOLDOWN;
		this.triggerAnim("boss", "strike");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.6F);
		this.windup.start(STRIKE_WINDUP, (world, left, total) -> {
			Vec3 arc = this.getEyePosition().add(this.getLookAngle().scale(1.6)).subtract(0.0, 0.6, 0.0);
			world.sendParticles(ParticleTypes.CRIT, arc.x, arc.y, arc.z, 2, 0.35, 0.2, 0.35, 0.02);
		}, world -> {
			dev.forja.ai.ForjaTraits.smithStruck(this);
			dev.forja.ai.WorldFights.smithBlowLanded(this, false);
			Vec3 reach = this.position().add(this.getLookAngle().scale(STRIKE_REACH * 0.5));
			world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.7F);
			for (LivingEntity victim : world.getEntitiesOfClass(LivingEntity.class,
				this.getBoundingBox().inflate(STRIKE_REACH * 0.5 + 1.0),
				other -> other != this && other.isAlive() && !(other instanceof FallenSmith))) {
				// A backhand only catches what is in front of him, which is the way out of it.
				if (victim.distanceToSqr(reach) > STRIKE_REACH * STRIKE_REACH * 0.36) {
					continue;
				}
				victim.hurtServer(world, this.damageSources().mobAttack(this), STRIKE_DAMAGE);
				Vec3 away = victim.position().subtract(this.position()).normalize();
				double hold = 1.0 - dev.forja.upgrade.Upgrades.anchor(victim);
				victim.push(away.x * 0.8 * hold, 0.32 * hold, away.z * 0.8 * hold);
				victim.hurtMarked = true;
			}
			world.sendParticles(ParticleTypes.SWEEP_ATTACK, reach.x, reach.y + 1.0, reach.z, 3, 0.4, 0.3, 0.4, 0.0);
			world.sendParticles(dev.forja.registry.ModParticles.CHISPA, reach.x, reach.y + 0.9, reach.z, 14, 0.4, 0.3, 0.4, 0.25);
		});
	}

	/**
	 * The moment the last quarter of him starts, drawn.
	 *
	 * <p>He already had the roar. What he did not have was anything to look at: a boss going into his
	 * final phase played an animation and then carried on, and the only way to know it had happened was
	 * to be watching the health bar rather than the fight. This is the one moment in the whole encounter
	 * that deserves to stop you, so it gets a ring that leaves him, a column of ash off him, and the
	 * forge in his chest going violet for good.
	 */
	private void phaseBreak(ServerLevel level) {
		this.lightForge(RAGE_TICKS * 3);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.4F, 0.7F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0F, 0.4F);
		// A ring on the floor that leaves him, to say the room has changed and not just his health. It
		// was six still circles of dust standing in for one moving one; now it is the moving one. It
		// hurts nobody — this is the room being told, not an attack.
		Shockwave.burst(level, this.position(), PHASE_RING_REACH, PHASE_RING_TICKS, Shockwave.VIOLET);
		// And everything that has been burning in him for a hundred years, at once.
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA,
			this.getX(), this.getY(1.6), this.getZ(), 60, 0.8, 1.6, 0.8, 0.05);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
			this.getX(), this.getY(1.2), this.getZ(), 50, 0.6, 0.8, 0.6, 0.6);
		this.standUpAnvils(level);
	}

	/** Six walking anvils, in a ring round him, as the last quarter opens. */
	private void standUpAnvils(ServerLevel level) {
		for (int index = 0; index < FINAL_ANVILS; index++) {
			double angle = index * 2.0 * Math.PI / FINAL_ANVILS + this.random.nextDouble() * 0.3;
			double radius = 6.0 + this.random.nextDouble() * 2.0;
			dev.forja.entity.WalkingAnvil anvil = dev.forja.registry.ModEntities.YUNQUE_ANDANTE
				.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			if (anvil == null) {
				return;
			}
			double x = this.getX() + Math.cos(angle) * radius;
			double z = this.getZ() + Math.sin(angle) * radius;
			anvil.snapTo(x, this.getY(), z, (float) Math.toDegrees(angle) + 90.0F, 0.0F);
			anvil.setPersistenceRequired();
			level.addFreshEntity(anvil);
			level.playSound(null, x, this.getY(), z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.8F, 0.5F);
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA, x, this.getY() + 0.6, z, 20, 0.35, 0.5, 0.35, 0.3);
		}
	}

	/**
	 * Onda de yunque: the hammer comes down and a ring of fire runs out of him along the floor.
	 *
	 * <p>The ring used to leave him on the same tick the animation started, while the hammer was still
	 * on its way up. Now it waits for the hammer. Eighteen ticks is a long time to watch something
	 * coming, and it is meant to be: this is the move you are supposed to get off the ground for.
	 */
	public void anvilWave(ServerLevel level) {
		this.waveCooldown = WAVE_COOLDOWN;
		this.triggerAnim("boss", "slam");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 2.5F, 0.7F);
		// The floor lights up under the whole ring before any of it moves, so the reach is not
		// something you have to learn by being hit by it. It used to be twenty particles a tick; now
		// it is one entity, told once, that every client draws for itself. The charge owns it: it goes
		// if the charge is dropped, and the blow is the only thing that can turn it into the ring.
		int colour = this.isRaging() ? Shockwave.VIOLET : Shockwave.EMBER;
		this.windup.start(WAVE_WINDUP, (world, left, total) -> {
			float grown = 1.0F - (float) left / total;
			if (left % 4 == 0) {
				world.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(1.9), this.getZ(),
					(int) (2 + grown * 4), 0.3, 0.2, 0.3, 0.01);
			}
		}, world -> {
			dev.forja.ai.ForjaTraits.smithStruck(this);
			dev.forja.ai.WorldFights.smithBlowLanded(this, true);
			this.wave = WAVE_TICKS;
			this.waveHit.clear();
			this.waveOrigin = this.position();
			// The ring leaves from where the hammer came down and stays there: he is free to walk off
			// again, and a ring that followed him would be hitting people it never went past.
			Shockwave ring = this.windup.takeWarning();
			if (ring != null) {
				ring.fire(this.waveOrigin);
			} else {
				Shockwave.burst(world, this.waveOrigin, WAVE_REACH, WAVE_TICKS, colour);
			}
			this.lightForge(RAGE_TICKS);
			// The hammer meeting the floor throws metal, not smoke.
			world.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY() + 0.2, this.getZ(),
				40, 0.6, 0.15, 0.6, 0.45);
			world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4.0F, 0.5F);
		});
		this.windup.warn(Shockwave.telegraph(level, this, WAVE_REACH, WAVE_WINDUP, WAVE_TICKS, colour));
	}

	/**
	 * One tick of the ring: it grows, and it only ever hits you once.
	 *
	 * <p>It no longer draws itself — the {@link Shockwave} it fired does that, on the client, off the
	 * same {@link Shockwave#radiusAt}. So the circle tested here and the circle on the screen are one
	 * circle, which matters now that the one on the screen has an edge you can see.
	 */
	private void rollWave(ServerLevel level) {
		int step = WAVE_TICKS - this.wave;
		this.wave--;
		double before = Shockwave.radiusAt(WAVE_REACH, step, WAVE_TICKS);
		double radius = Shockwave.radiusAt(WAVE_REACH, step + 1, WAVE_TICKS);
		Vec3 origin = this.waveOrigin;
		net.minecraft.world.phys.AABB sweep = new net.minecraft.world.phys.AABB(
			origin.x - radius - 1.5, origin.y - 3.0, origin.z - radius - 1.5,
			origin.x + radius + 1.5, origin.y + this.getBbHeight() + 3.0, origin.z + radius + 1.5);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, sweep,
			other -> other != this && other.isAlive() && !(other instanceof FallenSmith))) {
			double reach = Math.sqrt(victim.distanceToSqr(origin.x, victim.getY(), origin.z));
			double half = victim.getBbWidth() * 0.5;
			// Swept, not sampled: whoever the front crossed between last tick and this one, however
			// fast it is going. The old test was a band 1.2 either side of the ring, which also caught
			// people the ring had not reached yet — invisible when the ring was dots, plain to see now.
			if (reach - half > radius || reach + half < before) {
				continue;
			}
			// Off the floor as it gets to you, and it goes under you. The guide has said "se salta" since
			// the move was written and nothing here ever checked; with a wall of flame to clear, it does.
			// Tested before the name goes in the set on purpose: whoever comes down while the front is
			// still passing through them has not got away with it.
			double floor = origin.y + Shockwave.floorAt(level, victim.getX(), origin.y, victim.getZ());
			if (victim.getY() - floor > WAVE_CLEARANCE || !this.waveHit.add(victim.getUUID())) {
				continue;
			}
			victim.invulnerableTime = 0;
			victim.hurtServer(level, this.damageSources().mobAttack(this), WAVE_DAMAGE);
			// Away from where the hammer landed, which is the way the ring is travelling when it gets there.
			Vec3 away = victim.position().subtract(origin).multiply(1.0, 0.0, 1.0).normalize();
			double hold = 1.0 - dev.forja.upgrade.Upgrades.anchor(victim);
			victim.push(away.x * 0.6 * hold, 0.55 * hold, away.z * 0.6 * hold);
			victim.hurtMarked = true;
			victim.igniteForSeconds(3.0F);
		}
	}

	/** Garfio: the claw on the ruined arm goes out on its chain and drags what it catches back to him. */
	public void hookIn(ServerLevel level, LivingEntity target) {
		this.hookCooldown = HOOK_COOLDOWN;
		this.triggerAnim("boss", "hook");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2.2F, 0.5F);
		// Eight ticks with the chain gathered on the dead arm: short, because the answer to this one is
		// to break his line to you rather than to outrun it.
		this.windup.start(HOOK_WINDUP, (world, left, total) -> {
			Vec3 arm = this.getEyePosition().add(this.getLookAngle().scale(0.9)).subtract(0.0, 0.4, 0.0);
			world.sendParticles(ParticleTypes.CRIT, arm.x, arm.y, arm.z, 2, 0.15, 0.15, 0.15, 0.01);
		}, world -> this.throwClaw(world, target));
	}

	/** The claw actually leaving his hand, once the arm has finished cocking. */
	private void throwClaw(ServerLevel level, LivingEntity target) {
		if (!target.isAlive()) {
			return;
		}
		this.lightForge(RAGE_TICKS);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 3.0F, 0.6F);
		Vec3 from = this.getEyePosition().add(this.getLookAngle().scale(0.8));
		Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		for (int step = 0; step <= 24; step++) {
			double fraction = step / 24.0;
			level.sendParticles(ParticleTypes.CRIT,
				net.minecraft.util.Mth.lerp(fraction, from.x, to.x),
				net.minecraft.util.Mth.lerp(fraction, from.y, to.y),
				net.minecraft.util.Mth.lerp(fraction, from.z, to.z),
				1, 0.02, 0.02, 0.02, 0.0);
		}
		target.invulnerableTime = 0;
		target.hurtServer(level, this.damageSources().mobAttack(this), HOOK_DAMAGE);
		// Hauled in to just short of arm's reach, which is where the hammer is waiting.
		Vec3 pull = this.position().subtract(target.position());
		double reach = Math.max(1.0, pull.horizontalDistance());
		double hold = 1.0 - dev.forja.upgrade.Upgrades.anchor(target);
		target.push(pull.x / reach * 1.5 * hold, 0.42 * hold, pull.z / reach * 1.5 * hold);
		target.hurtMarked = true;
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.HOSTILE, 1.4F, 0.5F);
	}

	/**
	 * The forge is open at the front, so it has to be seen working: embers drifting out of the grate
	 * while he is banked down, and a column of violet fire out of the opening and the flue when he is not.
	 */
	private void forgeBreathes(ServerLevel level, boolean hot) {
		Vec3 facing = this.getLookAngle();
		double mouthX = this.getX() - facing.x * 0.55;
		double mouthY = this.getY(0.78);
		double mouthZ = this.getZ() - facing.z * 0.55;
		if (!hot) {
			if (this.tickCount % 6 == 0) {
				level.sendParticles(ParticleTypes.FLAME, mouthX, mouthY, mouthZ, 1, 0.22, 0.2, 0.22, 0.01);
			}
			if (this.tickCount % 18 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, this.getX() + facing.z * 0.5, this.getY(1.16), this.getZ() - facing.x * 0.5, 2, 0.1, 0.1, 0.1, 0.02);
			}
			return;
		}
		if (this.tickCount % 2 == 0) {
			level.sendParticles(VIOLET, mouthX, mouthY, mouthZ, 2, 0.25, 0.3, 0.25, 0.01);
			level.sendParticles(ParticleTypes.PORTAL, mouthX, mouthY + 0.4, mouthZ, 3, 0.22, 0.35, 0.22, 0.06);
		}
		if (this.tickCount % 4 == 0) {
			// Out of the flue, which is the only bit of him that still vents the way it was built to.
			level.sendParticles(VIOLET, this.getX() + facing.z * 0.5, this.getY(1.9), this.getZ() - facing.x * 0.5, 2, 0.1, 0.1, 0.1, 0.03);
		}
	}

	/** Everyone close enough sees the bar; everyone who walks away loses it. */
	private void watchers(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(this) <= 60.0 * 60.0) {
				this.bar.addPlayer(player);
			} else {
				this.bar.removePlayer(player);
			}
		}
	}

	/** The apprentices: four of them, in his own gear, all at once. */
	private void callApprentices(ServerLevel level) {
		this.triggerAnim("boss", "roar");
		this.lightForge(RAGE_TICKS * 2);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 0.7F);
		for (int i = 0; i < 4; i++) {
			Mob apprentice = (Mob) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
				.getValue(net.minecraft.world.entity.EntityTypeIds.WITHER_SKELETON).create(level, EntitySpawnReason.EVENT);
			if (apprentice == null) {
				continue;
			}
			double angle = i * Math.PI / 2.0;
			apprentice.snapTo(this.getX() + Math.cos(angle) * 3.0, this.getY(), this.getZ() + Math.sin(angle) * 3.0, 0.0F, 0.0F);
			dev.forja.world.Elites.makeElite(apprentice, level.getRandom());
			apprentice.setCustomName(Component.translatable("entity.forja.aprendiz"));
			level.addFreshEntity(apprentice);
		}
	}

	/** He goes back to the forge, lights three embers and waits. Put them out or wait him out. */
	private void startReforge(ServerLevel level) {
		this.reforging = REFORGE_TICKS;
		this.embersLeft = EMBERS;
		this.triggerAnim("boss", "slam");
		this.lightForge(REFORGE_TICKS);
		this.bar.setColor(BossEvent.BossBarColor.YELLOW);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_USE, SoundSource.HOSTILE, 4.0F, 0.6F);
		for (int i = 0; i < EMBERS; i++) {
			double angle = i * 2.0 * Math.PI / EMBERS;
			net.minecraft.core.BlockPos ember = this.blockPosition().offset((int) Math.round(Math.cos(angle) * 5.0), 0, (int) Math.round(Math.sin(angle) * 5.0));
			for (int dy = 0; dy < 3; dy++) {
				net.minecraft.core.BlockPos at = ember.above(dy);
				if (level.getBlockState(at).canBeReplaced()) {
					level.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
					break;
				}
			}
		}
	}

	/** Every second of the reforge heals him, unless the embers are out. */
	private void reforge(ServerLevel level) {
		this.reforging--;
		level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY(1.0), this.getZ(), 4, 0.6, 0.8, 0.6, 0.0);
		// Counting the embers walks a box of blocks, so it happens twice a second and not sixty times.
		if (this.tickCount % 10 == 0) {
			int burning = 0;
			for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
				this.blockPosition().offset(-7, -2, -7), this.blockPosition().offset(7, 3, 7)
			)) {
				if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FIRE)) {
					burning++;
				}
			}
			this.embersLeft = burning;
		}
		int burning = this.embersLeft;
		if (burning > 0 && this.tickCount % 20 == 0) {
			this.heal(this.getMaxHealth() * 0.01F * burning);
		}
		if (burning == 0 || this.reforging <= 0) {
			this.reforging = 0;
			this.bar.setColor(BossEvent.BossBarColor.RED);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 3.0F, 0.7F);
		}
	}

	/** The last stage: the shower that buried the forge, aimed at whoever is fighting him. */
	private void starfall(ServerLevel level, LivingEntity target) {
		this.lightForge(RAGE_TICKS);
		Vec3 at = target.position();
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 6.0, at.z, 60, 1.0, 1.0, 1.0, 0.1);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0F, 0.6F);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.0), other -> other != this && other.isAlive())) {
			victim.invulnerableTime = 0;
			victim.hurtServer(level, level.damageSources().magic(), 9.0F);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.bar.removeAllPlayers();
		// His heart, and one piece of what he was carrying.
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(1.0), this.getZ(), new ItemStack(ModItems.CORAZON_DE_FORJA)));
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(1.0), this.getZ(), Legends.create(level.getRandom(), level.registryAccess())));
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(1.0), this.getZ(), new ItemStack(dev.forja.registry.ModItems.YUNQUE_DEL_HERRERO)));
		// The hammer he was working with: the only thing that takes back a technique.
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(1.0), this.getZ(),
			new ItemStack(dev.forja.registry.ModItems.MARTILLO_DEL_MAESTRO)));
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 4.0F, 0.6F);
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(1.0), this.getZ(), 120, 1.0, 1.5, 1.0, 0.1);
		if (source.getEntity() instanceof ServerPlayer player) {
			dev.forja.ForjaAdvancements.award(player, "herrero_caido");
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bar.removeAllPlayers();
		this.windup.cancel();
		super.remove(reason);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		this.triggerAnim("boss", "strike");
		this.triggerAnim("fire", this.isRaging() ? "flash_hot" : "flash");
		Vec3 facing = this.getLookAngle();
		double mouthX = this.getX() - facing.x * 0.55;
		double mouthZ = this.getZ() - facing.z * 0.55;
		if (this.isRaging()) {
			level.sendParticles(VIOLET, mouthX, this.getY(0.78), mouthZ, 12, 0.3, 0.3, 0.3, 0.06);
		} else {
			level.sendParticles(ParticleTypes.FLAME, mouthX, this.getY(0.78), mouthZ, 10, 0.3, 0.3, 0.3, 0.06);
		}
		return super.doHurtTarget(level, target);
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ANVIL_LAND;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return SoundEvents.WITHER_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 4.0F;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<FallenSmith>("boss", test -> {
			FallenSmith smith = test.animatable();
			if (smith.reforging > 0) {
				return test.setAndContinue(SLAM);
			}
			return test.setAndContinue(test.isMoving() ? WALK : IDLE);
		}).triggerableAnim("slam", SLAM).triggerableAnim("roar", ROAR).triggerableAnim("strike", STRIKE).triggerableAnim("hook", HOOK));
		// The fire is its own controller: it only touches the two bones the flames hang off, so it can
		// keep burning through a swing, a roar or a reforge without any of them fighting over a bone.
		controllers.add(new AnimationController<FallenSmith>("fire", test ->
			test.setAndContinue(test.animatable().isRaging() ? FIRE_RAGE : FIRE_CALM)
		).triggerableAnim("flash", FIRE_FLASH).triggerableAnim("flash_hot", FIRE_FLASH_HOT));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	/** How many stages he fights in, for whatever wants to say which one this is. */
	public static final int PHASES = 3;

	/**
	 * Which stage he is in, read off his health: the apprentices come at three quarters and the sky at
	 * one quarter, so the thirds line up with what he actually does.
	 */
	public int phase() {
		float share = this.getHealth() / this.getMaxHealth();
		if (share <= 0.25F) {
			return 3;
		}
		return share <= 0.5F ? 2 : 1;
	}

	/** Whether he is in the stage where nothing can touch him, for the tests and the tooltip. */
	public boolean isReforging() {
		return this.reforging > 0;
	}

	public int embers() {
		return this.embersLeft;
	}
}
