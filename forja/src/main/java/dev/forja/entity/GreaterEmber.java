package dev.forja.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Ascua Mayor: the Pavesa, grown, and the reason a lantern full of them is worth setting down.
 *
 * <p>It is the mod's first proper elite of one of its own monsters, and the point of it is arithmetic
 * rather than menace: on its own it is a slower, harder wisp, but when it dies it **comes apart into
 * five ordinary ones**. Killing it at range costs you nothing; killing it while you are surrounded
 * turns one problem into six, all of them already angry and all of them already next to you.
 *
 * <p>So the interesting decision is not whether you can kill it — you can — but <b>where</b>.
 */
public class GreaterEmber extends Monster implements GeoEntity {
	public static final double HEALTH = 40.0;

	/** How many wisps it leaves behind, which is what Andy asked for and what makes it worth fighting. */
	public static final int SHARDS = 5;

	/** Its dive: how often, how far, and what it costs. */
	public static final int DIVE_COOLDOWN = 90;
	public static final int DIVE_WINDUP = 12;
	public static final int DIVE_TICKS = 14;
	public static final double DIVE_MIN = 4.0;
	public static final double DIVE_MAX = 16.0;
	public static final float DIVE_DAMAGE = 7.0F;
	public static final int DIVE_BURN = 6;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("fly");
	private static final RawAnimation DIVE = RawAnimation.begin().thenPlay("dive");
	private static final RawAnimation SPLIT = RawAnimation.begin().thenPlay("split");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();
	private final java.util.Set<java.util.UUID> diveHit = new java.util.HashSet<>();

	private int diveCooldown = 40;
	private int diving;

	public GreaterEmber(EntityType<? extends GreaterEmber> type, Level level) {
		super(type, level);
		this.xpReward = 25;
		this.setNoGravity(true);
		this.moveControl = new net.minecraft.world.entity.ai.control.FlyingMoveControl(this, 20, true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 5.0)
			.add(Attributes.MOVEMENT_SPEED, 0.22)
			.add(Attributes.FLYING_SPEED, 0.5)
			.add(Attributes.FOLLOW_RANGE, 28.0)
			.add(Attributes.ARMOR, 4.0);
	}

	@Override
	protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
		var navigation = new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
		// There is nothing in there for poison to work on.
		return !effect.is(net.minecraft.world.effect.MobEffects.POISON) && super.canBeAffected(effect);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// It burns in the air it is standing in, which is how you find one in a dark room.
		if (this.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.5), this.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
		}
		if (this.tickCount % 10 == 0) {
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
				this.getX(), this.getY(0.4), this.getZ(), 1, 0.35, 0.3, 0.35, 0.08);
		}
		this.dive(level);
	}

	/** The dive, which is the wisp's, slower and heavier and with a real wind-up on it. */
	private void dive(ServerLevel level) {
		if (this.diveCooldown > 0) {
			this.diveCooldown--;
		}
		if (this.diving > 0) {
			this.rollDive(level);
			return;
		}
		if (this.windup.charging()) {
			this.setDeltaMovement(this.getDeltaMovement().scale(0.35));
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
		this.diveCooldown = DIVE_COOLDOWN;
		this.triggerAnim("ascua", "dive");
		Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(this.position()).normalize();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 1.4F, 0.6F);
		this.windup.start(DIVE_WINDUP, (world, left, total) -> {
			float grown = 1.0F - (float) left / total;
			world.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.5), this.getZ(),
				(int) (2 + grown * 8), 0.4, 0.4, 0.4, 0.02);
			Vec3 along = this.position().add(aim.scale(1.5 + grown * 3.0));
			world.sendParticles(ParticleTypes.SMALL_FLAME, along.x, along.y + 0.4, along.z, 2, 0.08, 0.08, 0.08, 0.0);
		}, world -> {
			this.diving = DIVE_TICKS;
			this.diveHit.clear();
			this.setDeltaMovement(aim.scale(1.15));
			this.hurtMarked = true;
			world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.4F, 0.8F);
		});
	}

	private void rollDive(ServerLevel level) {
		this.diving--;
		level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.5), this.getZ(), 4, 0.2, 0.2, 0.2, 0.03);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.8),
			other -> other != this && other.isAlive() && !(other instanceof GreaterEmber) && !(other instanceof EmberWisp))) {
			if (!this.diveHit.add(victim.getUUID())) {
				continue;
			}
			victim.invulnerableTime = 0;
			victim.hurtServer(level, this.damageSources().mobAttack(this), DIVE_DAMAGE);
			victim.igniteForSeconds(DIVE_BURN);
			this.diving = 0;
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.triggerAnim("ascua", "split");
		// It comes apart into five. They arrive already awake and already looking at whoever did it,
		// which is the whole point: the question is not whether you can kill it, it is where.
		LivingEntity killer = source.getEntity() instanceof LivingEntity hunter ? hunter : null;
		for (int i = 0; i < SHARDS; i++) {
			EmberWisp shard = dev.forja.registry.ModEntities.PAVESA.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (shard == null) {
				break;
			}
			double angle = i * Math.PI * 2.0 / SHARDS;
			shard.snapTo(this.getX() + Math.cos(angle) * 1.2, this.getY() + 0.4, this.getZ() + Math.sin(angle) * 1.2,
				(float) Math.toDegrees(angle), 0.0F);
			shard.setDeltaMovement(Math.cos(angle) * 0.25, 0.18, Math.sin(angle) * 0.25);
			if (killer != null) {
				shard.setTarget(killer);
			}
			level.addFreshEntity(shard);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6F, 1.4F);
		level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.6), this.getZ(), 60, 0.6, 0.6, 0.6, 0.3);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY(0.6), this.getZ(), 40, 0.5, 0.5, 0.5, 0.4);
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA, this.getX(), this.getY(0.6), this.getZ(), 20, 0.5, 0.5, 0.5, 0.02);
		// And what it was burning, which is worth more than a wisp's.
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.4), this.getZ(),
			new ItemStack(dev.forja.registry.ModItems.ASCUA, 3 + this.random.nextInt(3))));
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.BLAZE_AMBIENT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLAZE_HURT;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<GreaterEmber>("ascua", test ->
			test.setAndContinue(test.isMoving() ? FLY : IDLE)
		).triggerableAnim("dive", DIVE).triggerableAnim("split", SPLIT));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
