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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Nucleo Estelar: a cut stone hanging in the air that does not fight back until you make it.
 *
 * <p>It has almost no health and it does no damage of its own. What it does is <b>keep</b> whatever
 * you hit it with: every blow is absorbed instead of taken, up to a ceiling, and when it is full it
 * lets the lot go back down the line at whoever put it there. Hitting it harder is strictly worse
 * than hitting it softly, which is the exact inversion of every other fight in the mod, and the whole
 * trick is being willing to stop swinging.
 *
 * <p>Andy asked for two tells and this has two, on purpose, because one is not enough when the answer
 * is "stop": it <b>changes colour</b> as it fills, from the cold blue it hangs at to a white that is
 * hard to look at, and its <b>shards swing out and spin faster</b> the fuller it gets. Colour alone
 * fails in a dark room and against a colour-blind player; a silhouette that visibly opens does not.
 */
public class StarCore extends Monster implements GeoEntity {
	public static final double HEALTH = 24.0;

	/** How much it can hold before it has to let go, and how much of it comes back. */
	public static final float CAPACITY = 24.0F;
	public static final float RETURN_SHARE = 1.25F;

	/** The release: how far it reaches, and how long the beam takes to arrive. */
	public static final int RELEASE_WINDUP = 18;
	public static final double RELEASE_RANGE = 16.0;

	/** What leaks away each second when nothing is hitting it, so a stored charge is not forever. */
	public static final float BLEED_PER_SECOND = 1.0F;

	/**
	 * How long it is <b>spent</b> for after it fires, and how much harder it is to hurt then.
	 *
	 * <p>Andy asked whether it could be killed at all, which is the whole problem with the first
	 * version: it could, but only by filling it and then feeding it the overflow, and nothing in the
	 * world told anybody that. The rule now is the one this shape was always asking for — it holds
	 * everything until it lets go, and for four seconds after letting go it holds <b>nothing</b> and
	 * takes double. Fight it in its own rhythm instead of guessing at an arithmetic rule.
	 */
	public static final int SPENT_TICKS = 80;
	public static final float SPENT_MULTIPLIER = 2.0F;

	private static final EntityDataAccessor<Float> DATA_CHARGE =
		SynchedEntityData.defineId(StarCore.class, EntityDataSerializers.FLOAT);

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation CHARGED = RawAnimation.begin().thenLoop("charged");
	private static final RawAnimation RELEASE = RawAnimation.begin().thenPlay("release");

	/** The two ends of the colour it is drawn in: cold blue when empty, white-hot when full. */
	public static final int COLD = 0x6E8CC8;
	public static final int FULL = 0xFFFFFF;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();

	/** Who put the charge in, which is who gets it back. */
	private LivingEntity owedTo;

	/** Ticks left of the window after a release, where it is an ordinary mob with twelve health. */
	private int spent;

	public StarCore(EntityType<? extends StarCore> type, Level level) {
		super(type, level);
		this.xpReward = 18;
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 0.0)
			.add(Attributes.MOVEMENT_SPEED, 0.16)
			.add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.ARMOR, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CHARGE, 0.0F);
	}

	@Override
	protected void registerGoals() {
		// No melee and no chasing. It hangs where it is and waits to be hit, which is the only
		// arrangement where "do not hit it" is a choice the player actually gets to make.
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 20.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** How full it is, from 0 to 1. Read on both sides: the client draws its colour from this. */
	public float charge() {
		return this.entityData.get(DATA_CHARGE) / CAPACITY;
	}

	/** What it is holding, in hit points. */
	public float stored() {
		return this.entityData.get(DATA_CHARGE);
	}

	/** Fills it to the brim without anybody hitting it, for the portrait that shows a full one. */
	public void fillForTest() {
		this.entityData.set(DATA_CHARGE, CAPACITY);
	}

	/**
	 * The colour it is drawn in right now, blended between cold and full.
	 *
	 * <p>Lives here rather than in the renderer so the number the client draws and the number the
	 * server is keeping can never be two different ideas of how full it is.
	 */
	public int tint() {
		float fill = Math.min(1.0F, this.charge());
		int r = (int) (((COLD >> 16) & 0xFF) + (((FULL >> 16) & 0xFF) - ((COLD >> 16) & 0xFF)) * fill);
		int g = (int) (((COLD >> 8) & 0xFF) + (((FULL >> 8) & 0xFF) - ((COLD >> 8) & 0xFF)) * fill);
		int b = (int) ((COLD & 0xFF) + ((FULL & 0xFF) - (COLD & 0xFF)) * fill);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	/**
	 * Every blow it takes goes into the stone instead of into its health.
	 *
	 * <p>Except the ones that should not: fall damage, its own beam and anything with no attacker
	 * behind it are taken normally, or a core dropped down a shaft would arrive full.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// One of ours cannot feed it either. The absorb reached this override without ever touching
		// the truce, so a striker could fill the core and then be immune to what came back, which is
		// the worst of both rules.
		if (dev.forja.world.Truce.blocks(this, source)) {
			return false;
		}
		if (source.getEntity() == null || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}
		// Spent: it has just let go of everything and there is nowhere for a blow to go. This is the
		// window the fight is actually about.
		if (this.spent > 0) {
			this.cracked(level);
			return super.hurtServer(level, source, amount * SPENT_MULTIPLIER);
		}
		if (source.getEntity() instanceof LivingEntity attacker) {
			this.owedTo = attacker;
			this.setTarget(attacker);
		}
		float held = this.entityData.get(DATA_CHARGE);
		float room = CAPACITY - held;
		float taken = Math.min(room, amount);
		this.entityData.set(DATA_CHARGE, held + taken);
		this.absorbed(level, taken);
		// Only the part it had no room for actually hurts it, which is how you eventually kill one:
		// fill it, survive the return, and the next hits land.
		float overflow = amount - taken;
		if (overflow > 0.0F) {
			return super.hurtServer(level, source, overflow);
		}
		this.invulnerableTime = 10;
		return true;
	}

	/** A blow landing on it while it is empty, which is the only kind that does anything. */
	private void cracked(ServerLevel level) {
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK,
			SoundSource.HOSTILE, 1.2F, 1.4F);
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(0.9), this.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
	}

	/** Whether it is in the window where it can be hurt. Read by the client for the cracked look. */
	public boolean spent() {
		return this.spent > 0;
	}

	/** The blow disappearing into it. */
	private void absorbed(ServerLevel level, float amount) {
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
			SoundSource.HOSTILE, 1.0F, 0.6F + this.charge() * 1.2F);
		int motes = 4;
		for (int step = 0; step < motes; step++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double reach = 1.4 + this.random.nextDouble();
			// Count zero means "one particle, travelling along this delta": inwards, so the blow is
			// seen going into the stone rather than spraying off it.
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
				this.getX() + Math.cos(angle) * reach, this.getY(0.9) + this.random.nextDouble() * 0.6,
				this.getZ() + Math.sin(angle) * reach,
				0, -Math.cos(angle) * 0.4, 0.05, -Math.sin(angle) * 0.4, 1.0);
		}
	}

	@Override
	public void tick() {
		super.tick();
		// It hangs. Bobbing is done in the animation; this only stops it falling out of the air.
		this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 0.0, 0.6));
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.windup.charging()) {
			this.windup.tick(level);
			return;
		}
		if (this.spent > 0) {
			this.spent--;
			// Hanging open, and obviously so: the shards fall in against the stone and the ring stops.
			if (this.tickCount % 3 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(1.0), this.getZ(), 2, 0.25, 0.3, 0.25, 0.01);
			}
			return;
		}
		float held = this.entityData.get(DATA_CHARGE);
		if (held <= 0.0F) {
			return;
		}
		// The ring round it, drawn thicker the fuller it is: the second tell, and the one that still
		// reads with the colour taken away.
		if (this.tickCount % 4 == 0) {
			int points = 2 + (int) (this.charge() * 6);
			for (int step = 0; step < points; step++) {
				double angle = (this.tickCount * 0.12) + step * Math.PI * 2.0 / points;
				double reach = 0.9 + this.charge() * 0.9;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					this.getX() + Math.cos(angle) * reach, this.getY(1.0), this.getZ() + Math.sin(angle) * reach,
					1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (held >= CAPACITY && this.owedTo != null && this.owedTo.isAlive()
			&& this.distanceTo(this.owedTo) <= RELEASE_RANGE) {
			this.release(level);
			return;
		}
		// Leaks when nothing is feeding it, so walking away from a full one is an answer too.
		if (this.tickCount % 20 == 0) {
			this.entityData.set(DATA_CHARGE, Math.max(0.0F, held - BLEED_PER_SECOND));
		}
	}

	/** Lets the whole lot go at whoever put it in. */
	public void release(ServerLevel level) {
		LivingEntity owed = this.owedTo;
		if (owed == null) {
			return;
		}
		float held = this.entityData.get(DATA_CHARGE);
		this.triggerAnim("nucleo", "release");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.8F, 0.7F);
		this.windup.start(RELEASE_WINDUP, (world, left, total) -> {
			// The line it is about to fire down, so there is something to step out of.
			Vec3 from = this.position().add(0.0, 1.0, 0.0);
			Vec3 toward = owed.position().add(0.0, owed.getBbHeight() * 0.5, 0.0).subtract(from);
			for (int step = 1; step <= 14; step++) {
				Vec3 along = from.add(toward.scale(step / 14.0));
				world.sendParticles(ParticleTypes.END_ROD, along.x, along.y, along.z, 1, 0.03, 0.03, 0.03, 0.0);
			}
		}, world -> this.beam(world, owed, held));
	}

	/** The beam arriving. */
	private void beam(ServerLevel level, LivingEntity owed, float held) {
		this.entityData.set(DATA_CHARGE, 0.0F);
		// Everything it had is gone, and so is the thing that was keeping it safe.
		this.spent = SPENT_TICKS;
		if (!owed.isAlive() || this.distanceTo(owed) > RELEASE_RANGE + 4.0) {
			// It fired at where you were. Walking out of the line is the whole answer.
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.4F, 1.4F);
			return;
		}
		Vec3 from = this.position().add(0.0, 1.0, 0.0);
		Vec3 toward = owed.position().add(0.0, owed.getBbHeight() * 0.5, 0.0).subtract(from);
		for (int step = 1; step <= 24; step++) {
			Vec3 along = from.add(toward.scale(step / 24.0));
			level.sendParticles(ParticleTypes.END_ROD, along.x, along.y, along.z, 2, 0.06, 0.06, 0.06, 0.0);
		}
		owed.invulnerableTime = 0;
		owed.hurtServer(level, this.damageSources().indirectMagic(this, this), held * RETURN_SHARE);
		level.playSound(null, owed.getX(), owed.getY(), owed.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 1.6F, 1.3F);
		level.sendParticles(ParticleTypes.END_ROD, owed.getX(), owed.getY(owed.getBbHeight() * 0.5), owed.getZ(),
			18, 0.4, 0.5, 0.4, 0.05);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.8F, 0.5F);
		level.sendParticles(dev.forja.registry.ModParticles.ALMA, this.getX(), this.getY(1.0), this.getZ(), 40, 0.5, 0.5, 0.5, 0.1);
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.8), this.getZ(),
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 2 + this.random.nextInt(3))));
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<StarCore>("nucleo", test ->
			// The second tell: past a third full the shards swing out and the whole thing speeds up.
			test.setAndContinue(!test.animatable().spent() && test.animatable().charge() > 0.33F ? CHARGED : IDLE)
		).triggerableAnim("release", RELEASE));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
