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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cargador de Carbon: a siege beast that is mostly fuel, and the only thing in the mod that is worth
 * more dead than alive to whoever is standing next to it.
 *
 * <p>It carries its load <b>inside</b> — coal down the spine under iron plates, with the fire showing
 * through the seams — so what looks like armour is the bomb. Nothing about the fight is about its
 * health: it walks at you, plants its feet, swells for <b>thirty-six ticks</b> and goes off, and the
 * whole question is whether you were still there.
 *
 * <p>Killing it is not an escape either. A hauler that dies with its load still in it blows anyway,
 * smaller and without the warning, which means finishing one at melee range is its own mistake. The
 * clean answer is to make it commit and walk out of the circle, and the reward for doing that is the
 * coal, which is on the floor afterwards either way.
 */
public class CoalHauler extends Monster implements GeoEntity {
	public static final double HEALTH = 46.0;

	/** The fuse: thirty-six ticks, where the animation blows the stacks. */
	public static final int PRIME_WINDUP = 36;
	/** How close it has to get before it commits, and how far the blast actually reaches. */
	public static final double PRIME_RANGE = 4.5;
	public static final double BLAST_RADIUS = 5.0;
	public static final float BLAST_DAMAGE = 14.0F;
	/** A death with the load still in it: no warning, and about half of everything. */
	public static final float DEATH_DAMAGE = 7.0F;
	public static final double DEATH_RADIUS = 3.5;

	/** How long the ground it went off on keeps burning. */
	public static final int FIRE_TICKS = 90;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation PRIME = RawAnimation.begin().thenPlay("prime");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();

	/** Set once it commits: a primed hauler blows even if you kill it first. */
	private boolean primed;

	public CoalHauler(EntityType<? extends CoalHauler> type, Level level) {
		super(type, level);
		this.xpReward = 22;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 5.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 28.0)
			.add(Attributes.ARMOR, 7.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.7);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
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
		// The stacks are always going, harder the faster it is moving. It announces itself from a long
		// way off, which is the only fair way to run something that ends a fight in one move.
		if (this.tickCount % 4 == 0) {
			double speed = this.getDeltaMovement().horizontalDistance();
			int puffs = this.primed ? 6 : (speed > 0.06 ? 3 : 1);
			for (int stack = -1; stack <= 1; stack += 2) {
				Vec3 vent = this.position()
					.add(new Vec3(stack * 0.25, 1.9, 0.25).yRot(-this.getYRot() * ((float) Math.PI / 180.0F)));
				level.sendParticles(dev.forja.registry.ModParticles.CENIZA, vent.x, vent.y, vent.z,
					puffs, 0.08, 0.05, 0.08, 0.02);
			}
		}
		this.close(level);
	}

	private void close(ServerLevel level) {
		if (this.windup.charging()) {
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1.0, 0.2));
			this.windup.tick(level);
			return;
		}
		if (this.primed) {
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || this.distanceTo(target) > PRIME_RANGE
			|| !this.hasLineOfSight(target)) {
			return;
		}
		this.prime(level);
	}

	/** Plants its feet and lights the load. From here it goes off whatever happens to it. */
	public void prime(ServerLevel level) {
		this.primed = true;
		this.triggerAnim("cargador", "prime");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2.0F, 0.5F);
		this.windup.start(PRIME_WINDUP, (world, left, total) -> {
			float progress = 1.0F - left / (float) total;
			if (left % 6 == 0) {
				world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(),
					SoundSource.HOSTILE, 1.4F, 0.6F + progress * 0.8F);
			}
		}, world -> {
			this.blow(world, BLAST_DAMAGE, BLAST_RADIUS);
			this.kill(world);
		});
		// The circle it is going to clear. It goes where he goes — he is still walking at you with the
		// fuse lit — and it fills as the fuse runs down.
		this.windup.warn(Shockwave.telegraph(level, this, null, BLAST_RADIUS, PRIME_WINDUP, 7, Shockwave.BLAST, 1.2F));
	}

	/** The load going up: everything inside the circle, then the coal and the fire it leaves. */
	private void blow(ServerLevel level, float damage, double radius) {
		// The fuse's own circle becomes the blast. A hauler killed before it lit anything has no circle,
		// and what it lets go of is smaller, so that one is drawn fresh at its own size.
		Shockwave ring = this.windup.takeWarning();
		if (ring != null && radius >= BLAST_RADIUS) {
			ring.fire(this.position());
		} else {
			if (ring != null) {
				ring.discard();
			}
			Shockwave.burst(level, this.position(), radius, 7, Shockwave.BLAST, 1.2F);
		}
		for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class,
			new AABB(this.position(), this.position()).inflate(radius), victim -> victim != this && victim.isAlive())) {
			double share = 1.0 - Math.min(1.0, this.distanceTo(caught) / radius);
			if (share <= 0.0) {
				continue;
			}
			caught.invulnerableTime = 0;
			caught.hurtServer(level, this.damageSources().explosion(this, this), (float) (damage * share));
			caught.igniteForTicks((int) (FIRE_TICKS * share));
			Vec3 away = caught.position().subtract(this.position()).normalize().scale(0.55 * share);
			caught.push(away.x, 0.35 * share, away.z);
			caught.hurtMarked = true;
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(1.0), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY(1.0), this.getZ(),
			(int) (radius * 24), radius * 0.4, 0.6, radius * 0.4, 0.5);
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA, this.getX(), this.getY(1.4), this.getZ(),
			(int) (radius * 16), radius * 0.5, 0.8, radius * 0.5, 0.06);
		this.scatter(level, radius);
	}

	/** What is left on the floor: the coal it was carrying, and the ground it stood on alight. */
	private void scatter(ServerLevel level, double radius) {
		int lumps = 4 + this.random.nextInt(5);
		for (int lump = 0; lump < lumps; lump++) {
			ItemEntity coal = new ItemEntity(level, this.getX(), this.getY(0.8), this.getZ(), new ItemStack(Items.COAL));
			coal.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.4, 0.3 + this.random.nextDouble() * 0.2,
				(this.random.nextDouble() - 0.5) * 0.4);
			level.addFreshEntity(coal);
		}
		// Fire on whatever it can be put on, which is what makes walking back in afterwards a choice.
		int span = (int) Math.ceil(radius * 0.6);
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				if (dx * dx + dz * dz > span * span || this.random.nextFloat() > 0.35F) {
					continue;
				}
				BlockPos spot = this.blockPosition().offset(dx, 0, dz);
				if (level.getBlockState(spot).isAir() && level.getBlockState(spot.below()).isSolidRender()) {
					level.setBlockAndUpdate(spot, Blocks.FIRE.defaultBlockState());
				}
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// Only a hauler that had not already committed blows on death. One that went off on its own
		// fuse has nothing left inside it, and a second blast on the same tick would be a free hit.
		if (!this.primed) {
			this.blow(level, DEATH_DAMAGE, DEATH_RADIUS);
		}
	}

	/** Whether it has lit its load, for the test and for the spawner's head count. */
	public boolean primed() {
		return this.primed;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.BLAZE_BURN;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HOGLIN_HURT;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<CoalHauler>("cargador", test ->
			test.setAndContinue(test.isMoving() ? WALK : IDLE)
		).triggerableAnim("prime", PRIME));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
