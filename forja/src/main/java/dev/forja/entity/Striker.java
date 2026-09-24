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
import net.minecraft.world.entity.EquipmentSlot;
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
 * Percutor: a drop hammer that walks, and the only thing in the mod with an answer to a raised shield.
 *
 * <p>The parry is the tightest, most rewarding thing Forja asks of a player — and nothing ever
 * punished holding the shield up and waiting. This does: the ram comes down, and if it lands on a
 * block it <b>breaks the guard</b>, putting the shield on a long cooldown and leaving whoever was
 * behind it standing in the open.
 *
 * <p>Which makes the fight a question about timing rather than about a button. The wind-up is long and
 * the animation says exactly what is coming: the ram climbs to the top of its frame and hangs there.
 * Blocking through it is the wrong answer; stepping out of it is the right one.
 */
public class Striker extends Monster implements GeoEntity {
	public static final double HEALTH = 80.0;

	/** The drop: how often, how long it hangs, and how far it reaches. */
	public static final int DROP_COOLDOWN = 160;
	/** Thirty-eight ticks, which is where the animation brings the ram down. */
	public static final int DROP_WINDUP = 38;
	public static final double DROP_MIN = 1.5;
	public static final double DROP_MAX = 6.0;
	public static final double DROP_REACH = 5.5;
	/** Ticks the ring takes to cross the circle once the ram is down: a blow, not a wave you outrun. */
	public static final int IMPACT_TICKS = 6;
	public static final float DROP_DAMAGE = 11.0F;

	/** And what it does to a shield that was in the way. */
	public static final int GUARD_BREAK = 120;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation DROP = RawAnimation.begin().thenPlay("drop");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();

	private int dropCooldown = 60;

	public Striker(EntityType<? extends Striker> type, Level level) {
		super(type, level);
		this.xpReward = 30;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 7.0)
			.add(Attributes.MOVEMENT_SPEED, 0.22)
			.add(Attributes.FOLLOW_RANGE, 26.0)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
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
		// The boiler that drives the ram, ticking over.
		if (this.tickCount % 14 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(2.1), this.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
		}
		this.hammer(level);
	}

	private void hammer(ServerLevel level) {
		if (this.dropCooldown > 0) {
			this.dropCooldown--;
		}
		if (this.windup.charging()) {
			// It plants itself. A drop hammer that could walk while it wound up would be unanswerable.
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.25, 1.0, 0.25));
			this.windup.tick(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || this.dropCooldown > 0) {
			return;
		}
		double distance = this.distanceTo(target);
		if (distance < DROP_MIN || distance > DROP_MAX || !this.hasLineOfSight(target)) {
			return;
		}
		this.drop(level);
	}

	/** Winds the ram to the top of its frame and lets it go. */
	public void drop(ServerLevel level) {
		this.dropCooldown = DROP_COOLDOWN;
		this.triggerAnim("percutor", "drop");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 2.0F, 0.5F);
		Vec3 aim = this.getLookAngle().normalize();
		this.windup.start(DROP_WINDUP, (world, left, total) -> {
			float grown = 1.0F - (float) left / total;
			// Where it is going to land, marked from the first tick — and carried along if he is shoved,
			// because the mark is worked out from where he stands.
			if (this.windup.warning() != null) {
				this.windup.warning().recentre(this.position().add(aim.scale(DROP_REACH * 0.55)));
			}
			if (left % 5 == 0) {
				world.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(2.4), this.getZ(),
					(int) (2 + grown * 4), 0.25, 0.15, 0.25, 0.02);
			}
		}, world -> this.land(world, aim));
		// The whole of what the ram hits. The ten flames it used to be drawn with stood at half that:
		// nobody could see it while the circle was dots, and standing just outside them still got you hit.
		this.windup.warn(Shockwave.telegraph(level, this, this.position().add(aim.scale(DROP_REACH * 0.55)),
			DROP_REACH, DROP_WINDUP, IMPACT_TICKS, Shockwave.EMBER, 0.8F));
	}

	/** The ram arriving. */
	private void land(ServerLevel level, Vec3 aim) {
		Vec3 mark = this.position().add(aim.scale(DROP_REACH * 0.55));
		Shockwave ring = this.windup.takeWarning();
		if (ring != null) {
			ring.fire(mark);
		} else {
			Shockwave.burst(level, mark, DROP_REACH, IMPACT_TICKS, Shockwave.EMBER, 0.8F);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0F, 0.45F);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA, mark.x, this.getY() + 0.2, mark.z, 40, 0.6, 0.15, 0.6, 0.5);
		level.sendParticles(ParticleTypes.EXPLOSION, mark.x, this.getY() + 0.3, mark.z, 2, 0.4, 0.1, 0.4, 0.0);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
			this.getBoundingBox().inflate(DROP_REACH + 1.0),
			other -> other != this && other.isAlive() && !(other instanceof Striker))) {
			if (victim.distanceToSqr(mark) > DROP_REACH * DROP_REACH) {
				continue;
			}
			// The guard break. A shield that was up is the thing this mob exists to answer, so it
			// takes the blow, keeps its owner alive, and is gone for six seconds.
			if (victim.isBlocking()) {
				this.breakGuard(level, victim);
			}
			victim.invulnerableTime = 0;
			victim.hurtServer(level, this.damageSources().mobAttack(this), DROP_DAMAGE);
			Vec3 away = victim.position().subtract(mark).normalize();
			double hold = 1.0 - dev.forja.upgrade.Upgrades.anchor(victim);
			victim.push(away.x * 0.5 * hold, 0.42 * hold, away.z * 0.5 * hold);
			victim.hurtMarked = true;
		}
	}

	/** Puts the shield down and keeps it down. */
	private void breakGuard(ServerLevel level, LivingEntity victim) {
		ItemStack shield = victim.getUseItem();
		victim.stopUsingItem();
		if (victim instanceof Player player && !shield.isEmpty()) {
			player.getCooldowns().addCooldown(shield, GUARD_BREAK);
		}
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
			SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.4F, 0.7F);
		level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(victim.getBbHeight() * 0.6), victim.getZ(),
			22, 0.35, 0.35, 0.35, 0.3);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 1.4F, 0.6F);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY(1.2), this.getZ(), 50, 0.5, 0.7, 0.5, 0.6);
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA, this.getX(), this.getY(1.4), this.getZ(), 24, 0.5, 0.6, 0.5, 0.02);
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.6), this.getZ(),
			dev.forja.forge.Assembler.createPart(dev.forja.part.PartType.CABEZA_MARTILLO, dev.forja.material.ForgeMaterial.HIERRO)));
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.6), this.getZ(),
			new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2 + this.random.nextInt(3))));
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
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Striker>("percutor", test ->
			test.setAndContinue(test.isMoving() ? WALK : IDLE)
		).triggerableAnim("drop", DROP));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
