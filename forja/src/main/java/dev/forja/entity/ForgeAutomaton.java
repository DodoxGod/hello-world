package dev.forja.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import dev.forja.forge.Assembler;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Autómata de forja: what the old smiths left minding the shop. Stone and iron, slow, heavy and very
 * hard to shift, and when it finally comes apart what falls out is the parts it was made of. There is
 * one in every abandoned forge and a pair of them in the fallen one.
 */
public class ForgeAutomaton extends Monster implements GeoEntity {
	public static final double HEALTH = 70.0;

	/** The ember it spits: how often, how far, and how long the wind-up lasts. */
	public static final int EMBER_COOLDOWN = 90;
	public static final int EMBER_WINDUP = 14;
	public static final double EMBER_MIN = 4.0;
	public static final double EMBER_MAX = 16.0;

	/**
	 * Coz de escoria: it puts both fists through the floor and the floor stays lit.
	 *
	 * <p>It had three moves and two of them only happen if you do something first — the ember needs you
	 * at range and the steam needs you to hit it — so most of a fight against it was a stone thing
	 * walking at you. This is the one it opens with, and it is the only move in the mod that takes
	 * ground away and keeps it: the smith's ring passes over you and is gone, this stays lit for three
	 * seconds and standing in it is your problem rather than its cooldown.
	 */
	public static final int SLAG_COOLDOWN = 200;
	public static final int SLAG_WINDUP = 36;
	public static final double SLAG_MIN = 2.0;
	public static final double SLAG_MAX = 8.0;
	public static final double SLAG_REACH = 6.0;
	/** Half the cone, in radians: a right angle in front of it and nothing behind. */
	public static final double SLAG_ARC = Math.PI / 4.0;
	public static final float SLAG_DAMAGE = 6.0F;
	/** How long the slag burns, and how often it bites whoever is standing in it. */
	public static final int SLAG_POOL_TICKS = 60;
	public static final int SLAG_POOL_EVERY = 10;
	public static final float SLAG_POOL_DAMAGE = 2.0F;

	/** The steam it vents when something is standing on top of it. */
	public static final int STEAM_COOLDOWN = 200;
	public static final double STEAM_REACH = 3.5;
	public static final float STEAM_DAMAGE = 4.0F;
	public static final int STEAM_SLOW = 80;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation SMASH = RawAnimation.begin().thenPlay("smash");
	private static final RawAnimation VENT = RawAnimation.begin().thenPlay("vent");
	private static final RawAnimation STEAM = RawAnimation.begin().thenPlay("steam");
	private static final RawAnimation SLAG = RawAnimation.begin().thenPlay("coz");

	/** The parts it is made of, and so the parts it leaves behind. */
	private static final PartType[] SCRAP = {
		PartType.CABEZA_MARTILLO, PartType.MANGO, PartType.ATADURA, PartType.PLACA_ESCUDO, PartType.GARFIO, PartType.BOLA,
	};

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private int emberCooldown = 40;
	private int steamCooldown;
	private int winding;
	private int slagCooldown = 80;
	private int slagLeft;
	private net.minecraft.world.phys.Vec3 slagAt = net.minecraft.world.phys.Vec3.ZERO;
	private net.minecraft.world.phys.Vec3 slagAim = net.minecraft.world.phys.Vec3.ZERO;
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();

	public ForgeAutomaton(EntityType<? extends ForgeAutomaton> type, Level level) {
		super(type, level);
		this.xpReward = 20;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 7.0)
			.add(Attributes.ARMOR, 12.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.MOVEMENT_SPEED, 0.16)
			.add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 0.9, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			this.slagMoves(level);
			this.emberSpit(level);
		}
		// The furnace in its belly, ticking over.
		if (this.level() instanceof ServerLevel level && this.tickCount % 24 == 0) {
			net.minecraft.world.phys.Vec3 front = this.getLookAngle().normalize().scale(0.45);
			level.sendParticles(ParticleTypes.SMOKE, this.getX() + front.x, this.getY(0.55), this.getZ() + front.z, 2, 0.05, 0.05, 0.05, 0.01);
			if (this.random.nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.FLAME, this.getX() + front.x, this.getY(0.5), this.getZ() + front.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
		// A thing of stone does not care about poison or hunger.
		return !effect.is(net.minecraft.world.effect.MobEffects.POISON)
			&& !effect.is(net.minecraft.world.effect.MobEffects.HUNGER)
			&& super.canBeAffected(effect);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		this.triggerAnim("automata", "smash");
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
			target.getX(), target.getY(0.6), target.getZ(), 10, 0.25, 0.25, 0.25, 0.2);
		return super.doHurtTarget(level, target);
	}

	/**
	 * Brasa: it leans back, the belly opens and it spits what is burning in there. Slow enough to walk
	 * out of, but it is the one answer it has to somebody who stands off and shoots it, which until now
	 * was the whole of the fight against it.
	 */
	private void emberSpit(ServerLevel level) {
		if (this.emberCooldown > 0) {
			this.emberCooldown--;
		}
		LivingEntity target = this.getTarget();
		if (this.winding > 0) {
			this.winding--;
			net.minecraft.world.phys.Vec3 mouth = this.mouth();
			level.sendParticles(ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z, 2, 0.12, 0.12, 0.12, 0.01);
			if (this.winding == 0 && target != null && target.isAlive()) {
				this.spit(level, target);
			}
			return;
		}
		if (target == null || this.emberCooldown > 0) {
			return;
		}
		double distance = this.distanceTo(target);
		if (distance < EMBER_MIN || distance > EMBER_MAX || !this.hasLineOfSight(target)) {
			return;
		}
		this.emberCooldown = EMBER_COOLDOWN;
		this.winding = EMBER_WINDUP;
		this.triggerAnim("automata", "vent");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.6F, 0.6F);
	}

	/** Choosing the stomp, running its wind-up, and keeping the slag burning once it is down. */
	private void slagMoves(ServerLevel level) {
		if (this.slagCooldown > 0) {
			this.slagCooldown--;
		}
		if (this.slagLeft > 0) {
			this.burnPool(level);
		}
		if (this.windup.charging()) {
			// Both fists over its head: it is not going anywhere until they come down.
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1.0, 0.2));
			this.windup.tick(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || this.slagCooldown > 0 || this.winding > 0) {
			return;
		}
		double distance = this.distanceTo(target);
		if (distance < SLAG_MIN || distance > SLAG_MAX || !this.hasLineOfSight(target)) {
			return;
		}
		this.slagStomp(level, target);
	}

	/** Coz de escoria: arms up, and everything in front of it is alight for the next three seconds. */
	public void slagStomp(ServerLevel level, LivingEntity target) {
		this.slagCooldown = SLAG_COOLDOWN;
		this.triggerAnim("automata", "coz");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 2.0F, 0.5F);
		// The cone is taken now, not when the fists land. Thirty-six ticks is a long time to be aimed
		// at, and a cone that followed you through all of it would not be an attack, it would be a tax.
		net.minecraft.world.phys.Vec3 aim = target.position().subtract(this.position()).normalize();
		this.windup.start(SLAG_WINDUP, (world, left, total) -> {
			if (left % 6 == 0) {
				world.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(2.0), this.getZ(), 3, 0.4, 0.2, 0.4, 0.01);
			}
		}, world -> this.dropFists(world, aim));
		// The wedge on the floor, in the same words as every circle in the mod: the line is how far,
		// the glow fills towards it, and when they meet the fists come down.
		this.windup.warn(Shockwave.wedge(level, this, this.position(), aim, SLAG_ARC, SLAG_REACH, SLAG_WINDUP, 6, Shockwave.EMBER, 0.9F));
	}

	/** The fists landing: the cone bites once, hard, and then stays lit. */
	private void dropFists(ServerLevel level, net.minecraft.world.phys.Vec3 aim) {
		this.slagAt = this.position();
		this.slagAim = aim;
		this.slagLeft = SLAG_POOL_TICKS;
		Shockwave ring = this.windup.takeWarning();
		if (ring != null) {
			ring.fire(this.slagAt);
		}
		// And what stays burning afterwards, for exactly as long as it bites.
		Shockwave.poolWedge(level, this.slagAt, aim, SLAG_ARC, SLAG_REACH, SLAG_POOL_TICKS, Shockwave.EMBER, 0.45F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6F, 0.5F);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY() + 0.2, this.getZ(), 30, 0.5, 0.1, 0.5, 0.4);
		for (LivingEntity victim : this.inCone(level, this.position(), aim, SLAG_REACH)) {
			victim.hurtServer(level, this.damageSources().mobAttack(this), SLAG_DAMAGE);
			victim.igniteForSeconds(4.0F);
			net.minecraft.world.phys.Vec3 away = victim.position().subtract(this.position()).normalize();
			double hold = 1.0 - dev.forja.upgrade.Upgrades.anchor(victim);
			victim.push(away.x * 0.5 * hold, 0.38 * hold, away.z * 0.5 * hold);
			victim.hurtMarked = true;
		}
	}

	/** One tick of the slag: it draws itself, and every half second it bites whoever is still in it. */
	private void burnPool(ServerLevel level) {
		this.slagLeft--;
		if (this.slagLeft % SLAG_POOL_EVERY != 0) {
			return;
		}
		for (LivingEntity victim : this.inCone(level, this.slagAt, this.slagAim, SLAG_REACH)) {
			victim.invulnerableTime = 0;
			victim.hurtServer(level, this.damageSources().onFire(), SLAG_POOL_DAMAGE);
			victim.igniteForSeconds(2.0F);
		}
	}

	/** Draws the wedge on the floor, so where it is going to land is never a guess. */
	private void markCone(ServerLevel level, net.minecraft.world.phys.Vec3 aim, double reach,
		net.minecraft.core.particles.ParticleOptions dust, int count) {
		if (count <= 0 || aim.lengthSqr() < 1.0E-4) {
			return;
		}
		double facing = Math.atan2(aim.z, aim.x);
		for (int ray = -3; ray <= 3; ray++) {
			double angle = facing + ray * SLAG_ARC / 3.0;
			for (double step = 1.0; step <= reach; step += 1.0) {
				level.sendParticles(dust,
					this.getX() + Math.cos(angle) * step, this.getY() + 0.12, this.getZ() + Math.sin(angle) * step,
					count, 0.12, 0.02, 0.12, 0.0);
			}
		}
	}

	/** Everything standing in the wedge, which is the only thing any of this touches. */
	private java.util.List<LivingEntity> inCone(ServerLevel level, net.minecraft.world.phys.Vec3 from,
		net.minecraft.world.phys.Vec3 aim, double reach) {
		java.util.List<LivingEntity> caught = new java.util.ArrayList<>();
		if (aim.lengthSqr() < 1.0E-4) {
			return caught;
		}
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
			this.getBoundingBox().inflate(reach + 1.0, 3.0, reach + 1.0),
			other -> other != this && other.isAlive() && !(other instanceof ForgeAutomaton))) {
			net.minecraft.world.phys.Vec3 toward = victim.position().subtract(from);
			double flat = toward.horizontalDistance();
			if (flat > reach || flat < 0.1) {
				continue;
			}
			double dot = (toward.x * aim.x + toward.z * aim.z) / flat;
			if (dot >= Math.cos(SLAG_ARC)) {
				caught.add(victim);
			}
		}
		return caught;
	}

	/** Where the belly opens, which is where the ember comes from and where the steam goes. */
	private net.minecraft.world.phys.Vec3 mouth() {
		net.minecraft.world.phys.Vec3 front = this.getLookAngle().normalize().scale(0.5);
		return new net.minecraft.world.phys.Vec3(this.getX() + front.x, this.getY(0.55), this.getZ() + front.z);
	}

	private void spit(ServerLevel level, LivingEntity target) {
		net.minecraft.world.phys.Vec3 mouth = this.mouth();
		net.minecraft.world.phys.Vec3 aim = target.position()
			.add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(mouth).normalize();
		var ember = new net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball(level, this, aim);
		ember.snapTo(mouth.x, mouth.y, mouth.z, this.getYRot(), this.getXRot());
		level.addFreshEntity(ember);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.4F, 0.7F);
		level.sendParticles(ParticleTypes.LAVA, mouth.x, mouth.y, mouth.z, 6, 0.1, 0.1, 0.1, 0.02);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		// Vapor: get in its face and the boiler lets go. It is how it buys back the room its own weight
		// will not let it take, and it is the reason hugging it is not free.
		if (hurt && this.steamCooldown == 0 && source.getEntity() instanceof LivingEntity attacker
			&& attacker.distanceToSqr(this) <= STEAM_REACH * STEAM_REACH) {
			this.steamPurge(level);
		}
		if (this.steamCooldown > 0) {
			this.steamCooldown--;
		}
		return hurt;
	}

	public void steamPurge(ServerLevel level) {
		this.steamCooldown = STEAM_COOLDOWN;
		this.triggerAnim("automata", "steam");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 2.4F, 0.6F);
		// The purge as a ring of steam out to exactly as far as it scalds, where forty puffs of cloud
		// used to stand at sixty percent of that. A little loose cloud is kept: steam has a body.
		Shockwave.burst(level, this.position(), STEAM_REACH, 8, Shockwave.STEAM, 0.9F);
		level.sendParticles(dev.forja.registry.ModParticles.VAPOR, this.getX(), this.getY(0.4), this.getZ(), 26, STEAM_REACH * 0.3, 0.25, STEAM_REACH * 0.3, 0.14);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
			this.getBoundingBox().inflate(STEAM_REACH),
			other -> other != this && other.isAlive() && !(other instanceof ForgeAutomaton))) {
			victim.invulnerableTime = 0;
			victim.hurtServer(level, level.damageSources().hotFloor(), STEAM_DAMAGE);
			victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, STEAM_SLOW, 1));
		}
		// It buys itself a moment while the boiler is empty.
		this.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 60, 0, false, false));
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// It comes apart into the parts it was built out of.
		int pieces = 1 + this.random.nextInt(3);
		for (int i = 0; i < pieces; i++) {
			PartType part = SCRAP[this.random.nextInt(SCRAP.length)];
			ForgeMaterial material = this.random.nextBoolean() ? ForgeMaterial.HIERRO : ForgeMaterial.PIEDRA;
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.5), this.getZ(), Assembler.createPart(part, material)));
		}
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.5), this.getZ(), new ItemStack(Items.IRON_NUGGET, 2 + this.random.nextInt(4))));
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 1.2F, 0.7F);
		level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY(0.8), this.getZ(), 20, 0.4, 0.5, 0.4, 0.0);
		// The boiler lets go: everything it was holding in, outward at once.
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 2.2F, 0.5F);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
			this.getX(), this.getY(0.7), this.getZ(), 45, 0.4, 0.4, 0.4, 0.5);
		Shockwave.burst(level, this.position(), 2.6, 8, Shockwave.STEAM, 0.6F);
		level.sendParticles(dev.forja.registry.ModParticles.VAPOR, this.getX(), this.getY(0.3), this.getZ(), 16, 0.6, 0.1, 0.6, 0.1);
		// And the fire in its belly going out, which is the bit that says it is not getting up.
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA,
			this.getX(), this.getY(0.9), this.getZ(), 18, 0.5, 0.4, 0.5, 0.02);
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.IRON_GOLEM_STEP;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 1.2F;
	}

	@Override
	public float getVoicePitch() {
		return 0.7F;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<ForgeAutomaton>("automata", test ->
			test.setAndContinue(test.isMoving() ? WALK : IDLE)
		).triggerableAnim("smash", SMASH).triggerableAnim("vent", VENT).triggerableAnim("steam", STEAM)
			.triggerableAnim("coz", SLAG));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
