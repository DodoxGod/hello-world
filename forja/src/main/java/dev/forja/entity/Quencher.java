package dev.forja.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Templador: the man who cools things down, with a tank of quenching oil on his back.
 *
 * <p>He takes no health off you at all. What he purges is a tankful of cold oil over the ground you are
 * standing on, and what it costs you is everything you had <b>built up</b>: the fire you were on
 * fire with, the charge in a voltaic weapon and — the one that matters — the Frenesi combo, which is
 * several seconds of connected blows and the only thing in the mod that pushes an upgrade past its
 * own tooltip.
 *
 * <p>So he is a mob you are allowed to ignore, and ignoring him is how the fight goes long. He keeps
 * his distance, purges over the top of whatever you are fighting, and the pool he leaves sits on the
 * ground re-quenching anything that stands in it. The right answer is to go and deal with him, which
 * means turning your back on the thing he came in with.
 */
public class Quencher extends Monster implements GeoEntity {
	public static final double HEALTH = 26.0;

	/** The purge: how often, how long the tank takes to build to it, and how far it carries. */
	public static final int DOUSE_COOLDOWN = 140;
	/** Twenty-two ticks, where the animation lets go of the oil. */
	public static final int DOUSE_WINDUP = 22;
	public static final double DOUSE_MIN = 3.0;
	public static final double DOUSE_MAX = 14.0;

	/** The pool it leaves: how wide, and for how long. */
	/** Twice the area of the first pass, which is the radius times the root of two. */
	public static final double POOL_RADIUS = 4.5;
	public static final int POOL_TICKS = 110;

	/** How close he will let anything get before backing off. */
	public static final float KEEP_AWAY = 6.0F;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation DOUSE = RawAnimation.begin().thenPlay("douse");

	/** The dull green of the oil, used for the arc and the splash. */
	private static final net.minecraft.core.particles.DustParticleOptions OIL =
		new net.minecraft.core.particles.DustParticleOptions(0x3E4C40, 1.1F);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();

	private int douseCooldown = 40;

	public Quencher(EntityType<? extends Quencher> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 2.0)
			.add(Attributes.MOVEMENT_SPEED, 0.23)
			.add(Attributes.FOLLOW_RANGE, 30.0)
			.add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		// He never closes. The cart is slow and he is not the dangerous one, so being reached is
		// simply the end of him — which is what makes going after him a real trade.
		this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, KEEP_AWAY, 1.1, 1.35));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 18.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// The stack, always going. It is the whole warning the mob gives: a templador that is venting
		// has pressure in it, and pressure is the thing that ends up on the floor.
		if (this.tickCount % 5 == 0) {
			Vec3 vent = this.vent();
			boolean building = this.windup.charging();
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
				vent.x, vent.y, vent.z, 1, 0.05, 0.02, 0.05, 0.012);
			if (building) {
				// Pressure, which is white and fast where the idle stack is grey and slow: the difference
				// between the two is the whole of the warning.
				level.sendParticles(dev.forja.registry.ModParticles.VAPOR, vent.x, vent.y, vent.z, 4, 0.04, 0.02, 0.04, 0.09);
			}
			if (building) {
				level.sendParticles(OIL, vent.x, vent.y, vent.z, 2, 0.06, 0.03, 0.06, 0.05);
			}
		}
		this.throwOil(level);
	}

	private void throwOil(ServerLevel level) {
		if (this.douseCooldown > 0) {
			this.douseCooldown--;
		}
		if (this.windup.charging()) {
			this.getNavigation().stop();
			this.windup.tick(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || this.douseCooldown > 0) {
			return;
		}
		double distance = this.distanceTo(target);
		if (distance < DOUSE_MIN || distance > DOUSE_MAX || !this.hasLineOfSight(target)) {
			return;
		}
		this.douse(level, target.position());
	}

	/** Opens the valve. The oil leaves twenty-two ticks later, at wherever it was aimed. */
	public void douse(ServerLevel level, Vec3 at) {
		this.douseCooldown = DOUSE_COOLDOWN;
		this.triggerAnim("templador", "douse");
		// Not a bucket being filled any more: a valve opened on something that was already full.
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 0.9F, 1.6F);
		this.windup.start(DOUSE_WINDUP, (world, left, total) -> {
		}, world -> this.splash(world, at));
		// The ring the oil is going to land in: the same circle every area attack in the mod is warned
		// with now, in the oil's own colour and with next to no flame on it, because it is not a fire.
		this.windup.warn(Shockwave.telegraph(level, this, at, POOL_RADIUS, DOUSE_WINDUP, 8, Shockwave.OIL, 0.2F));
	}

	/** The mouth of the stack, in world space: where the smoke leaves and where the oil comes from. */
	public Vec3 vent() {
		return this.position().add(new Vec3(0.0, 2.16, 0.41).yRot(-this.getYRot() * ((float) Math.PI / 180.0F)));
	}

	/** The oil landing: an arc through the air, a splash, and a pool that stays. */
	private void splash(ServerLevel level, Vec3 at) {
		Shockwave ring = this.windup.takeWarning();
		if (ring != null) {
			ring.fire(at);
		} else {
			Shockwave.burst(level, at, POOL_RADIUS, 8, Shockwave.OIL, 0.2F);
		}
		Vec3 from = this.vent();
		for (int step = 1; step <= 12; step++) {
			double share = step / 12.0;
			Vec3 along = from.add(at.subtract(from).scale(share)).add(0.0, Math.sin(share * Math.PI) * 1.6, 0.0);
			level.sendParticles(OIL, along.x, along.y, along.z, 1, 0.06, 0.06, 0.06, 0.0);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 1.8F, 0.8F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 30, POOL_RADIUS * 0.4, 0.3, POOL_RADIUS * 0.4, 0.02);
		level.sendParticles(OIL, at.x, at.y + 0.2, at.z, 40, POOL_RADIUS * 0.5, 0.2, POOL_RADIUS * 0.5, 0.05);

		// Whoever was standing there when it landed gets washed down once immediately, and then the
		// pool keeps doing it to anyone who stays.
		for (LivingEntity soaked : level.getEntitiesOfClass(LivingEntity.class,
			new net.minecraft.world.phys.AABB(at, at).inflate(POOL_RADIUS, 1.5, POOL_RADIUS),
			other -> other != this && other.isAlive())) {
			int lost = dev.forja.world.OilPools.quench(soaked);
			if (lost > 0 && soaked instanceof ServerPlayer player) {
				player.sendSystemMessage(Component.translatable("gui.forja.templado_apagado").withColor(0x7FA08A));
			}
		}
		dev.forja.world.OilPools.pour(level, at, POOL_RADIUS, POOL_TICKS);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// The tank splits when he does, which is a last pool where he fell.
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.HOSTILE, 1.6F, 0.6F);
		dev.forja.world.OilPools.pour(level, this.position(), POOL_RADIUS * 0.8, POOL_TICKS / 2);
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.5), this.getZ(),
			new ItemStack(net.minecraft.world.item.Items.BUCKET)));
		if (this.random.nextFloat() < 0.5F) {
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.5), this.getZ(),
				new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 1 + this.random.nextInt(2))));
		}
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.BUCKET_EMPTY;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VILLAGER_HURT;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Quencher>("templador", test ->
			test.setAndContinue(test.isMoving() ? WALK : IDLE)
		).triggerableAnim("douse", DOUSE));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
