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
import net.minecraft.world.phys.Vec3;

/**
 * Tenaza: a pair of blacksmith's tongs that stood up, and the only thing in the mod that holds you
 * still.
 *
 * <p>Everything else in Forja pushes you away — the shockwave, the gale, the ram. That is comfortable:
 * being thrown is dangerous and it is also an escape. This does the opposite. It closes on you and
 * <b>roots you where you stand</b> for a few seconds, which costs almost no health and is far worse,
 * because whatever else is in the room now knows exactly where you will be.
 *
 * <p>So it is never the dangerous thing in a fight and it is often the reason the fight went badly. It
 * folds its arms away when it is not using them and its reach is hidden until it uses it, which means
 * the mistake it punishes is standing at what looks like a safe distance.
 */
public class Tongs extends Monster implements GeoEntity {
	public static final double HEALTH = 34.0;

	/** The grab: how often, how long the arms take, and how far they actually go. */
	public static final int GRAB_COOLDOWN = 190;
	/** Eighteen ticks, where the animation closes the jaws. */
	public static final int GRAB_WINDUP = 18;
	public static final double GRAB_MIN = 3.0;
	public static final double GRAB_MAX = 9.0;
	public static final float GRAB_DAMAGE = 3.0F;

	/** And how long it holds whatever it caught. */
	public static final int HELD_TICKS = 70;

	/** How hard it drags something that tries to walk out of its grip. */
	private static final double PULL = 0.34;
	/** And how far it lets you get before it starts pulling. */
	private static final double SLACK = 0.4;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation GRAB = RawAnimation.begin().thenPlay("grab");

	/** The pale soul colour it is lit with, used for the jaws closing. */
	private static final net.minecraft.core.particles.DustParticleOptions SOUL =
		new net.minecraft.core.particles.DustParticleOptions(0x96E8EE, 0.9F);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();

	private int grabCooldown = 50;

	/** What it is holding, where it caught it, and for how much longer. */
	private @org.jspecify.annotations.Nullable LivingEntity held;
	private Vec3 anchor = Vec3.ZERO;
	private int heldTicks;

	public Tongs(EntityType<? extends Tongs> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.MOVEMENT_SPEED, 0.27)
			.add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.ARMOR, 4.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 14.0F));
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
		if (this.tickCount % 16 == 0) {
			level.sendParticles(dev.forja.registry.ModParticles.ALMA,
				this.getX(), this.getY(1.55), this.getZ(), 1, 0.12, 0.06, 0.12, 0.01);
		}
		this.hold(level);
		this.close(level);
	}

	private void close(ServerLevel level) {
		if (this.grabCooldown > 0) {
			this.grabCooldown--;
		}
		if (this.windup.charging()) {
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 1.0, 0.3));
			this.windup.tick(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || this.grabCooldown > 0) {
			return;
		}
		double distance = this.distanceTo(target);
		if (distance < GRAB_MIN || distance > GRAB_MAX || !this.hasLineOfSight(target)) {
			return;
		}
		this.grab(level, target);
	}

	/** Unfolds, and closes on whatever it was looking at. */
	public void grab(ServerLevel level, LivingEntity target) {
		this.grabCooldown = GRAB_COOLDOWN;
		this.triggerAnim("tenaza", "grab");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.HOSTILE, 1.4F, 0.6F);
		this.windup.start(GRAB_WINDUP, (world, left, total) -> {
			// The line the jaws will close along, so the reach it had been hiding is finally on show.
			Vec3 toward = target.position().subtract(this.position()).normalize();
			for (int step = 1; step <= 8; step++) {
				Vec3 along = this.position().add(0.0, 1.0, 0.0).add(toward.scale(step * GRAB_MAX / 8.0));
				world.sendParticles(SOUL, along.x, along.y, along.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}, world -> this.snap(world, target));
	}

	/**
	 * Keeping hold of whatever it caught.
	 *
	 * <p>This used to be two mob effects and both of them were wrong. Slowness at amplifier six is a
	 * <b>field-of-view change</b> first and a movement change second, which is a horrible thing to do
	 * to somebody for three and a half seconds. And the jump lock was the old {@code JUMP_BOOST 128}
	 * trick, which worked back when the amplifier was a signed byte and wrapped to -128; it is an int
	 * now, so what it actually did was hand you a hundred and twenty-nine levels of jump boost and
	 * fire you into the sky.
	 *
	 * <p>So it holds you properly instead: it remembers where it caught you and drags you back to that
	 * spot, and takes the rise out of a jump without granting anything. No effects, no FOV, and a
	 * chain drawn between the two of you so it is obvious what is happening and what to kill.
	 */
	private void hold(ServerLevel level) {
		if (this.heldTicks <= 0 || this.held == null) {
			return;
		}
		LivingEntity caught = this.held;
		this.heldTicks--;
		if (!caught.isAlive() || !this.isAlive() || caught.distanceToSqr(this.anchor) > 400.0) {
			this.heldTicks = 0;
			this.held = null;
			return;
		}
		Vec3 back = this.anchor.subtract(caught.position());
		double flat = Math.sqrt(back.x * back.x + back.z * back.z);
		Vec3 motion = caught.getDeltaMovement();
		if (flat > SLACK) {
			Vec3 pull = new Vec3(back.x / flat, 0.0, back.z / flat).scale(Math.min(PULL, flat * 0.5));
			caught.setDeltaMovement(pull.x, Math.min(motion.y, 0.0), pull.z);
		} else {
			// Close enough: stand still, and a jump goes nowhere.
			caught.setDeltaMovement(0.0, Math.min(motion.y, 0.0), 0.0);
		}
		caught.hurtMarked = true;
		caught.fallDistance = 0.0;
		// The chain, so from outside it reads as one thing holding another.
		Vec3 from = this.position().add(0.0, 1.1, 0.0);
		Vec3 toward = caught.position().add(0.0, caught.getBbHeight() * 0.5, 0.0).subtract(from);
		int steps = (int) Math.max(3, toward.length() * 2.5);
		for (int step = 1; step <= steps; step++) {
			Vec3 along = from.add(toward.scale(step / (double) steps));
			level.sendParticles(SOUL, along.x, along.y, along.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** Lets go of whatever it holds (a heavy blunt blow knocks the jaws open; see dev.forja.ai.ForjaTraits). */
	public void letGo() {
		if (this.heldTicks > 0 && this.level() instanceof ServerLevel level) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.HOSTILE, 1.2F, 1.3F);
		}
		this.heldTicks = 0;
		this.held = null;
	}

	/** Whether it is holding something, and what. */
	public @org.jspecify.annotations.Nullable LivingEntity holding() {
		return this.heldTicks > 0 ? this.held : null;
	}

	/** The jaws meeting. */
	private void snap(ServerLevel level, LivingEntity target) {
		if (!target.isAlive() || this.distanceTo(target) > GRAB_MAX + 2.0) {
			// It closed on nothing, which is what stepping out of it looks like from the other side.
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 1.2F, 1.4F);
			return;
		}
		target.invulnerableTime = 0;
		target.hurtServer(level, this.damageSources().mobAttack(this), GRAB_DAMAGE);
		// Held. Almost no damage and no knockback at all: the cost is that you are still here.
		this.held = target;
		this.anchor = target.position();
		this.heldTicks = HELD_TICKS;
		target.setDeltaMovement(0.0, target.getDeltaMovement().y * 0.2, 0.0);
		target.hurtMarked = true;
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.6F, 1.2F);
		level.sendParticles(SOUL, target.getX(), target.getY(target.getBbHeight() * 0.5), target.getZ(),
			26, 0.35, 0.5, 0.35, 0.02);
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(target.getBbHeight() * 0.5), target.getZ(),
			12, 0.3, 0.4, 0.3, 0.1);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		// Killing it lets go. With the old mob effects it did not: you broke the tongs and stayed
		// rooted for the rest of the three and a half seconds anyway.
		this.heldTicks = 0;
		this.held = null;
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 1.4F, 0.5F);
		level.sendParticles(dev.forja.registry.ModParticles.ALMA, this.getX(), this.getY(1.0), this.getZ(), 24, 0.35, 0.6, 0.35, 0.03);
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.5), this.getZ(),
			dev.forja.forge.Assembler.createPart(dev.forja.part.PartType.ATADURA, dev.forja.material.ForgeMaterial.HIERRO)));
		if (this.random.nextFloat() < 0.4F) {
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.5), this.getZ(),
				new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, 1 + this.random.nextInt(3))));
		}
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.IRON_TRAPDOOR_OPEN;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_TRAPDOOR_CLOSE;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Tongs>("tenaza", test ->
			test.setAndContinue(test.isMoving() ? WALK : IDLE)
		).triggerableAnim("grab", GRAB));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
