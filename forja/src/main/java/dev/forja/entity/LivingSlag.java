package dev.forja.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
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
import net.minecraft.world.level.Level;

/**
 * Escoria Viviente: what gets skimmed off the top of a melt, still hot enough to move.
 *
 * <p>It splits when you kill it, like a magma cube — but the point of a splitter here is not the
 * arithmetic, it is the floor. Every one of them **leaves burning ground where it dies**, so a fight
 * against a big one in a corridor ends with you standing in the ashes of three smaller ones, and the
 * ground you cleared is the ground you can no longer stand on.
 *
 * <p>It is also the only source of <b>escoria</b>: the halves that get small enough stop splitting and
 * cool into something you can pick up. A monster that is worth killing for the material and awkward to
 * kill in a small room is a better reason to fight it than more health would be.
 */
public class LivingSlag extends Monster implements GeoEntity {
	/** The size it turns up at, and the size below which it stops splitting. */
	public static final int BIG = 3;
	public static final int SMALLEST = 1;

	/** Health and damage both scale off the size. */
	public static final double HEALTH_PER_SIZE = 9.0;
	public static final double DAMAGE_PER_SIZE = 2.0;

	/** How long the ground it dies on stays lit, and what standing in it costs. */
	public static final int POOL_TICKS = 80;
	public static final int POOL_EVERY = 10;
	public static final float POOL_DAMAGE = 2.0F;

	private static final EntityDataAccessor<Integer> DATA_SIZE =
		SynchedEntityData.defineId(LivingSlag.class, EntityDataSerializers.INT);

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation SPLIT = RawAnimation.begin().thenPlay("split");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public LivingSlag(EntityType<? extends LivingSlag> type, Level level) {
		super(type, level);
		this.xpReward = 6;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH_PER_SIZE * BIG)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE_PER_SIZE * BIG)
			// Slow. It has no legs and is not pretending to.
			.add(Attributes.MOVEMENT_SPEED, 0.187)
			.add(Attributes.FOLLOW_RANGE, 20.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SIZE, BIG);
	}

	public int size() {
		return this.entityData.get(DATA_SIZE);
	}

	/**
	 * Sets how big this one is, which is health, damage, hitbox and how loud it looks all at once.
	 *
	 * <p>Called before the thing joins the world, so the health can be set to full without fighting
	 * whatever it already had.
	 */
	public void setSize(int size) {
		int clamped = Math.max(SMALLEST, Math.min(BIG, size));
		this.entityData.set(DATA_SIZE, clamped);
		this.refreshDimensions();
		var health = this.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(HEALTH_PER_SIZE * clamped);
		}
		var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.setBaseValue(DAMAGE_PER_SIZE * clamped);
		}
		this.setHealth(this.getMaxHealth());
		this.xpReward = 2 * clamped;
	}

	@Override
	public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
		float share = this.size() / (float) BIG;
		return super.getDefaultDimensions(pose).scale(0.45F + share * 0.55F);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		boolean hurt = super.doHurtTarget(level, target);
		if (hurt) {
			// Touching it burns, which is most of what a lump of molten waste can do to anybody.
			target.igniteForSeconds(2 + this.size());
		}
		return hurt;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.tickCount % 5 == 0) {
			level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY(0.4), this.getZ(), 1, 0.25, 0.15, 0.25, 0.0);
		}
		if (this.tickCount % 14 == 0) {
			level.sendParticles(dev.forja.registry.ModParticles.CENIZA,
				this.getX(), this.getY(0.7), this.getZ(), 2, 0.3, 0.25, 0.3, 0.01);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.triggerAnim("escoria", "split");
		int size = this.size();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 1.4F, 0.6F);
		level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY(0.5), this.getZ(), 20 + size * 8, 0.4, 0.3, 0.4, 0.05);
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA, this.getX(), this.getY(0.6), this.getZ(), 16, 0.4, 0.3, 0.4, 0.02);

		if (size > SMALLEST) {
			LivingEntity killer = source.getEntity() instanceof LivingEntity hunter ? hunter : null;
			for (int half = 0; half < 2; half++) {
				LivingSlag piece = dev.forja.registry.ModEntities.ESCORIA.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (piece == null) {
					break;
				}
				double angle = half * Math.PI + this.random.nextDouble();
				piece.setSize(size - 1);
				piece.snapTo(this.getX() + Math.cos(angle) * 0.8, this.getY(), this.getZ() + Math.sin(angle) * 0.8,
					(float) Math.toDegrees(angle), 0.0F);
				piece.setDeltaMovement(Math.cos(angle) * 0.22, 0.24, Math.sin(angle) * 0.22);
				if (killer != null) {
					piece.setTarget(killer);
				}
				level.addFreshEntity(piece);
			}
		} else {
			// The last pieces cool where they lie, and that is the only place escoria comes from.
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.3), this.getZ(),
				new ItemStack(dev.forja.registry.ModItems.ESCORIA, 1 + this.random.nextInt(2))));
		}

		// And whatever it was, the ground it died on stays hot.
		dev.forja.world.SlagPools.pour(level, this.position(), 1.2 + size * 0.5, POOL_TICKS);
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.MAGMA_CUBE_SQUISH;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.MAGMA_CUBE_HURT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return SoundEvents.MAGMA_CUBE_DEATH;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<LivingSlag>("escoria", test ->
			test.setAndContinue(test.isMoving() ? WALK : IDLE)
		).triggerableAnim("split", SPLIT));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
