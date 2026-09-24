package dev.forja.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Pavesa: a scrap of a forge that got out, and the only thing in the mod that flies.
 *
 * <p>Everything else Forja puts in the world is slow, heavy and answered by footwork. This is the
 * opposite of all three: twelve health, quick, and it comes at you from above. What makes it worth
 * respecting is that it feeds — park one next to a fire and it swells, hits harder and burns what it
 * touches, so the answer is to fight it away from the forge rather than on top of one.
 */
public class EmberWisp extends Monster implements GeoEntity {
	public static final double HEALTH = 12.0;

	/** The dive: how often, how far, and what it costs to be under it. */
	/**
	 * How long it hangs and burns before it comes down.
	 *
	 * <p>A wisp is small and fast and there is usually more than one, so being hit by a dive you never
	 * saw start reads as the room being on fire rather than as anything having attacked you. It stops
	 * dead in the air and flares first now, which is short — it is a small thing, not a boss — but it
	 * is a moment, and a moment is enough to move.
	 */
	public static final int DIVE_WINDUP = 8;

	public static final int DIVE_COOLDOWN = 70;
	public static final double DIVE_MIN = 3.0;
	public static final double DIVE_MAX = 12.0;
	public static final int DIVE_TICKS = 12;
	public static final float DIVE_DAMAGE = 4.0F;
	public static final int DIVE_BURN = 3;

	/** Feeding on heat: how far it looks, how long a mouthful lasts, and what it is worth. */
	public static final double HEAT_REACH = 4.0;
	public static final int FLARE_TICKS = 200;
	public static final float FLARE_DAMAGE = 3.0F;
	public static final int FLARE_CHECK = 20;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("fly");
	private static final RawAnimation FLARE = RawAnimation.begin().thenLoop("flare");
	private static final RawAnimation DIVE = RawAnimation.begin().thenPlay("dive");

	/** Whether it is running hot. The client draws the fire, so the client has to be told. */
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_FED =
		net.minecraft.network.syncher.SynchedEntityData.defineId(EmberWisp.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private int diveCooldown = 30;
	private int diving;
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();
	private int fed;
	private final java.util.Set<java.util.UUID> diveHit = new java.util.HashSet<>();

	public EmberWisp(EntityType<? extends EmberWisp> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl(this, 20, true);
		this.xpReward = 6;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.ARMOR, 2.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, 0.62)
			.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(false);
		return navigation;
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FED, false);
	}

	/** Whether it has eaten recently, which is what the extra size and the extra bite come from. */
	public boolean isFed() {
		return this.entityData.get(DATA_FED);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
		// There is no body in there for poison or hunger to find.
		return !effect.is(net.minecraft.world.effect.MobEffects.POISON)
			&& !effect.is(net.minecraft.world.effect.MobEffects.HUNGER)
			&& super.canBeAffected(effect);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		boolean hurt = super.doHurtTarget(level, target);
		if (hurt && this.isFed() && target instanceof net.minecraft.world.entity.Entity victim) {
			victim.igniteForSeconds(DIVE_BURN);
		}
		return hurt;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.trail(level);
		if (this.tickCount % FLARE_CHECK == 0) {
			this.feed(level);
		}
		if (this.fed > 0) {
			this.fed--;
			if (this.fed == 0) {
				this.entityData.set(DATA_FED, false);
			}
		}
		this.dive(level);
	}

	/** What it leaves behind it, which is how you see one coming in a dark forge. */
	private void trail(ServerLevel level) {
		boolean hot = this.isFed();
		int every = hot ? 2 : 5;
		if (this.tickCount % every == 0) {
			level.sendParticles(hot ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
				this.getX(), this.getY(0.5), this.getZ(), 1, 0.08, 0.08, 0.08, 0.01);
		}
		if (this.tickCount % 12 == 0) {
			level.sendParticles(ParticleTypes.SMALL_FLAME, this.getX(), this.getY(0.2), this.getZ(), 1, 0.1, 0.05, 0.1, 0.0);
		}
	}

	/**
	 * Looks for heat close by and eats it. The check is a small box walked twice a second rather than a
	 * radius walked every tick, because there can be a room of these and the fallen forge is all lava.
	 */
	private void feed(ServerLevel level) {
		BlockPos at = this.blockPosition();
		int reach = (int) Math.ceil(HEAT_REACH);
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-reach, -reach, -reach), at.offset(reach, reach, reach))) {
			var state = level.getBlockState(pos);
			boolean heat = state.is(net.minecraft.world.level.block.Blocks.FIRE)
				|| state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
				|| state.is(net.minecraft.world.level.block.Blocks.LAVA)
				|| state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
				|| state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
				|| state.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
				|| state.is(dev.forja.registry.ModBlocks.MESA_DE_FORJA)
				|| (state.is(net.minecraft.world.level.block.Blocks.FURNACE)
					&& state.getOptionalValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT).orElse(false));
			if (!heat) {
				continue;
			}
			boolean wasFed = this.fed > 0;
			this.fed = FLARE_TICKS;
			this.entityData.set(DATA_FED, true);
			if (!wasFed) {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.8F, 1.5F);
				level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY(0.4), this.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
			}
			return;
		}
	}

	/** Lights it without a fire to sit next to: what an ember rain does to one as it arrives. */
	public void stoke() {
		this.fed = FLARE_TICKS;
		this.entityData.set(DATA_FED, true);
	}

	/** Picado: it climbs, folds up and drops on you, and it does not steer once it has let go. */
	public void dive(ServerLevel level) {
		if (this.diveCooldown > 0) {
			this.diveCooldown--;
		}
		if (this.diving > 0) {
			this.diving--;
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.4), this.getZ(), 2, 0.1, 0.1, 0.1, 0.02);
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.6),
				other -> other != this && other.isAlive() && !(other instanceof EmberWisp))) {
				if (!this.diveHit.add(victim.getUUID())) {
					continue;
				}
				victim.invulnerableTime = 0;
				float bite = DIVE_DAMAGE + (this.isFed() ? FLARE_DAMAGE : 0.0F);
				victim.hurtServer(level, this.damageSources().mobAttack(this), bite);
				victim.igniteForSeconds(this.isFed() ? DIVE_BURN * 2 : DIVE_BURN);
				this.diving = 0;
			}
			return;
		}
		// A dive already on its way finishes before it thinks about another. It hangs still while it
		// charges, which is what makes a stopped wisp worth looking at.
		if (this.windup.charging()) {
			this.setDeltaMovement(this.getDeltaMovement().scale(0.4));
			this.windup.tick(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || this.diveCooldown > 0) {
			return;
		}
		double distance = this.distanceTo(target);
		if (distance < DIVE_MIN || distance > DIVE_MAX || !this.hasLineOfSight(target)) {
			return;
		}
		this.diveCooldown = this.isFed() ? DIVE_COOLDOWN / 2 : DIVE_COOLDOWN;
		this.triggerAnim("pavesa", "dive");
		Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(this.position()).normalize();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CANDLE_EXTINGUISH, SoundSource.HOSTILE, 1.2F, 0.6F);
		this.windup.start(DIVE_WINDUP, (world, left, total) -> {
			float grown = 1.0F - (float) left / total;
			world.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.5), this.getZ(),
				(int) (1 + grown * 5), 0.25, 0.25, 0.25, 0.01);
			// And a short lead along the line it will take, so the one it has picked is obvious.
			Vec3 along = this.position().add(aim.scale(1.0 + grown * 2.0));
			world.sendParticles(ParticleTypes.SMALL_FLAME, along.x, along.y + 0.4, along.z, 1, 0.05, 0.05, 0.05, 0.0);
		}, world -> {
			this.diving = DIVE_TICKS;
			this.diveHit.clear();
			double speed = this.isFed() ? 1.05 : 0.8;
			this.setDeltaMovement(aim.scale(speed));
			this.hurtMarked = true;
			world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.9F, 1.6F);
		});
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// A light going out has to get brighter first, or it just stops being there.
		level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.5), this.getZ(),
			this.isFed() ? 30 : 16, 0.15, 0.15, 0.15, 0.22);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
			this.getX(), this.getY(0.5), this.getZ(), this.isFed() ? 26 : 14, 0.2, 0.2, 0.2, 0.3);
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA,
			this.getX(), this.getY(0.5), this.getZ(), 10, 0.3, 0.3, 0.3, 0.01);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.CANDLE_EXTINGUISH, SoundSource.HOSTILE, 1.6F, 0.7F);

		// What is left of it once the fire is out.
		// Ascuas: the only thing a crucible burns, and the only place they come from besides a crucible.
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.3), this.getZ(),
			new ItemStack(dev.forja.registry.ModItems.ASCUA, (this.isFed() ? 2 : 1) + this.random.nextInt(2))));
		if (this.random.nextFloat() < 0.35F) {
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.3), this.getZ(),
				new ItemStack(Items.BLAZE_POWDER)));
		}
		if (this.random.nextFloat() < 0.12F) {
			dev.forja.upgrade.Upgrade upgrade = dev.forja.upgrade.Upgrade.values()[this.random.nextInt(dev.forja.upgrade.Upgrade.values().length)];
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.3), this.getZ(),
				dev.forja.item.UpgradeOrbItem.create(upgrade, 25)));
		}
		// One that died fed goes out loudly, and leaves the floor warm for a moment.
		if (this.isFed()) {
			if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
				dev.forja.ForjaAdvancements.award(player, "pavesa");
			}
			level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY(0.4), this.getZ(), 24, 0.4, 0.4, 0.4, 0.05);
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(2.0),
				other -> other != this && other.isAlive())) {
				victim.igniteForSeconds(DIVE_BURN);
			}
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.9F, 1.4F);
		level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.4), this.getZ(), 16, 0.3, 0.3, 0.3, 0.02);
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.BLAZE_BURN;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLAZE_HURT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return SoundEvents.FIRE_EXTINGUISH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.7F;
	}

	@Override
	public float getVoicePitch() {
		return 1.5F;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<EmberWisp>("pavesa", test -> {
			if (test.animatable().isFed()) {
				return test.setAndContinue(FLARE);
			}
			return test.setAndContinue(test.isMoving() ? FLY : IDLE);
		}).triggerableAnim("dive", DIVE));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
