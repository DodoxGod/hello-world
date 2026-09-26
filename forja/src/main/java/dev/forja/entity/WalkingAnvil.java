package dev.forja.entity;

import java.util.List;

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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Yunque Andante: an anvil that got up, and the first thing in the mod that mends rather than breaks.
 *
 * <p>Nothing in Forja healed anything before this, which meant every fight was a subtraction and the
 * only question was whether your damage per second beat theirs. This one welds: it plants itself at
 * the back of a group and puts health back into the mod's own constructs — a hollow suit, an
 * automaton, a striker — as fast as you can take it off them.
 *
 * <p>It <b>does not chase you</b>, which is the whole point. It is slower than anything it is helping
 * and it would rather stand still and work, so the fight it creates is a decision: go through the
 * things hitting you to reach the thing keeping them up, or out-damage a welder. It is not a hard
 * decision the first time and it is a genuinely hard one when you are already cornered.
 */
public class WalkingAnvil extends Monster implements GeoEntity {
	public static final double HEALTH = 60.0;

	/** How far it can weld, how often, and how much it puts back. */
	public static final double WELD_RANGE = 9.0;
	public static final int WELD_COOLDOWN = 70;
	public static final float WELD_AMOUNT = 6.0F;

	/** And how many it can mend with one weld. */
	public static final int WELD_TARGETS = 3;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation WELD = RawAnimation.begin().thenPlay("weld");

	/** The orange of a weld, which is what the thread between it and its patient is drawn in. */
	private static final net.minecraft.core.particles.DustParticleOptions WELD_ARC =
		new net.minecraft.core.particles.DustParticleOptions(0xFFC24A, 1.0F);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private int weldCooldown = 30;

	public WalkingAnvil(EntityType<? extends WalkingAnvil> type, Level level) {
		super(type, level);
		this.xpReward = 20;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 5.0)
			// Slower than everything it is helping, on purpose: it is furniture, and furniture that
			// could keep up with you would make walking past it the wrong answer every time.
			.add(Attributes.MOVEMENT_SPEED, 0.14)
			.add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.ARMOR, 10.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		// It will hit you if you are standing on it, but it never goes looking. There is no target
		// goal here at all, which is what makes it a thing you choose to deal with.
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 0.8, true));
		// It goes where the work is. A healer that wanders off is a healer that heals nothing, and
		// this one has no target goal to keep it anywhere near a fight.
		this.goalSelector.addGoal(3, new dev.forja.entity.ai.FollowOursGoal(
			this, WalkingAnvil::ours, 0.9, WELD_RANGE * 2.0, 3.0));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.4));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
		// There is nobody in there for poison, hunger or wither to work on.
		return !effect.is(net.minecraft.world.effect.MobEffects.POISON)
			&& !effect.is(net.minecraft.world.effect.MobEffects.HUNGER)
			&& !effect.is(net.minecraft.world.effect.MobEffects.WITHER)
			&& super.canBeAffected(effect);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.tickCount % 10 == 0) {
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
				this.getX(), this.getY(0.55), this.getZ(), 1, 0.3, 0.1, 0.3, 0.06);
		}
		if (this.weldCooldown > 0) {
			this.weldCooldown--;
			return;
		}
		this.weld(level);
	}

	/**
	 * Mends whatever of ours is nearby and hurt.
	 *
	 * <p>Worst first, and never more than three at a time. Healing everything in range would make a
	 * group with one of these in it simply not die; three at a time means a big enough fight still
	 * goes your way, and the anvil is a reason to fight it somewhere smaller.
	 */
	public void weld(ServerLevel level) {
		List<Mob> hurt = level.getEntitiesOfClass(Mob.class,
			new AABB(this.position(), this.position()).inflate(WELD_RANGE),
			other -> other != this && other.isAlive() && ours(other) && other.getHealth() < other.getMaxHealth());
		if (hurt.isEmpty()) {
			return;
		}
		hurt.sort(java.util.Comparator.comparingDouble(mob -> mob.getHealth() / mob.getMaxHealth()));
		this.weldCooldown = WELD_COOLDOWN;
		this.triggerAnim("yunque", "weld");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_USE, SoundSource.HOSTILE, 1.6F, 0.7F);
		int mended = 0;
		for (Mob patient : hurt) {
			if (mended++ >= WELD_TARGETS) {
				break;
			}
			patient.heal(WELD_AMOUNT);
			this.arc(level, patient);
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
				patient.getX(), patient.getY(patient.getBbHeight() * 0.6), patient.getZ(),
				12, 0.3, 0.4, 0.3, 0.25);
		}
		level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.9), this.getZ(), 10, 0.3, 0.2, 0.3, 0.02);
	}

	/** Whether this is one of ours, and so something an anvil would bother mending. */
	/** Who it mends, and who it follows. */
	public static boolean ours(Mob mob) {
		return mob instanceof HollowArmor || mob instanceof ForgeAutomaton || mob instanceof FallenSmith
			|| mob instanceof WalkingAnvil || mob instanceof Striker || mob instanceof Tongs;
	}

	/** The weld itself: a line of sparks from the anvil to whatever it is mending. */
	private void arc(ServerLevel level, Mob patient) {
		Vec3 from = this.position().add(0.0, 0.8, 0.0);
		Vec3 to = patient.position().add(0.0, patient.getBbHeight() * 0.5, 0.0);
		int steps = Math.max(4, (int) (from.distanceTo(to) * 1.6));
		for (int step = 1; step < steps; step++) {
			double fraction = step / (double) steps;
			level.sendParticles(WELD_ARC,
				net.minecraft.util.Mth.lerp(fraction, from.x, to.x),
				net.minecraft.util.Mth.lerp(fraction, from.y, to.y),
				net.minecraft.util.Mth.lerp(fraction, from.z, to.z),
				1, 0.04, 0.04, 0.04, 0.0);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_DESTROY, SoundSource.HOSTILE, 1.4F, 0.8F);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY(0.5), this.getZ(), 40, 0.5, 0.4, 0.5, 0.5);
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA, this.getX(), this.getY(0.6), this.getZ(), 20, 0.5, 0.4, 0.5, 0.02);
		// It was an anvil. Somebody can still use it as one.
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.4), this.getZ(),
			new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 3 + this.random.nextInt(4))));
		if (this.random.nextFloat() < 0.5F) {
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.4), this.getZ(),
				new ItemStack(dev.forja.registry.ModItems.YUNQUE_PORTATIL)));
		}
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.ANVIL_LAND;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ANVIL_PLACE;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return SoundEvents.ANVIL_DESTROY;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<WalkingAnvil>("yunque", test ->
			test.setAndContinue(test.isMoving() ? WALK : IDLE)
		).triggerableAnim("weld", WELD));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
