package dev.forja.entity;

import java.util.ArrayList;
import java.util.List;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.server.level.ServerBossEvent;

/**
 * Guardian de Cuno: the die that guards the templates, and a fight you cannot start by attacking.
 *
 * <p>It is <b>sealed</b>. While a single ember lantern still burns in its hall every blow that lands
 * on it does nothing at all, and there is no health bar to whittle down — so the first minute of the
 * fight is not a fight, it is housekeeping under fire. Three lanterns, on three pillars, in the open,
 * with a guardian and its two constructs between you and them.
 *
 * <p>Which is the whole design. Every other boss in the mod asks "can you out-damage this"; this one
 * asks "can you do three errands while it is happening". The seals are deliberately easy to break and
 * deliberately far apart: the difficulty is never the lantern, it is what is chasing you while you go
 * to it.
 *
 * <p>Its one attack matches: it lifts the die and stamps the floor with it, <b>forty ticks</b> from
 * start to contact, which is the longest wind-up in the mod and the only one that leaves a mark on
 * the ground where it landed.
 */
public class CuneGuardian extends Monster implements GeoEntity {
	public static final double HEALTH = 140.0;

	/**
	 * How far it looks for its seals, measured from where it was stood up rather than from where it
	 * happens to be.
	 *
	 * <p>Seven, which covers its own hall and stops at the wall of it. The castle also lights the four
	 * towers, and the first version of this counted those: the guardian read seven seals and could
	 * have been unsealed from the wall walk without anybody going inside. The seals belong to the
	 * room, so the search is anchored to the room — the guardian walking about during the fight must
	 * not change what is holding it together.
	 */
	public static final int SEAL_RANGE = 7;

	/** The stamp: how often, how long the die takes to come down, and what it does when it lands. */
	public static final int STAMP_COOLDOWN = 130;
	/** Forty ticks, which is where the animation puts the die on the floor. */
	public static final int STAMP_WINDUP = 40;
	public static final double STAMP_MIN = 1.0;
	public static final double STAMP_MAX = 9.0;
	public static final double STAMP_RADIUS = 4.5;
	public static final float STAMP_DAMAGE = 13.0F;
	/**
	 * The share of the stamp that ignores armour, and what it does to the armour on the way past.
	 *
	 * <p>A die is a thing for putting a shape into metal, so the one attack it has should be felt
	 * by the metal. A third of it lands whatever you are wearing — plate is no answer to being
	 * stamped — and the rest marks the plate itself, which makes the fight cost something you
	 * cannot get back by standing still afterwards.
	 */
	public static final float STAMP_PIERCE = 0.33F;
	public static final int STAMP_ARMOUR_WEAR = 12;

	private static final EntityDataAccessor<Integer> DATA_SEALS =
		SynchedEntityData.defineId(CuneGuardian.class, EntityDataSerializers.INT);

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation STAMP = RawAnimation.begin().thenPlay("stamp");
	private static final RawAnimation UNSEALED = RawAnimation.begin().thenPlay("unsealed");

	/** The pale gold the seal on its face is cut in, used for the tethers. */
	private static final net.minecraft.core.particles.DustParticleOptions SEAL =
		new net.minecraft.core.particles.DustParticleOptions(0xD8B454, 1.0F);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();

	/**
	 * The bar. It says {@code sellado} while the lanterns are up, which is the point of having one:
	 * a boss bar that does not move is the clearest possible way to say that hitting it is not the
	 * answer.
	 */
	private final ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(),
		Component.translatable("entity.forja.guardian_de_cuno"), BossBarColor.YELLOW, BossBarOverlay.NOTCHED_6);

	private int stampCooldown = 60;
	private boolean announced;

	/** Where it was stood up: the middle of its hall, and the centre of the seal search. */
	private @org.jspecify.annotations.Nullable BlockPos home;

	public CuneGuardian(EntityType<? extends CuneGuardian> type, Level level) {
		super(type, level);
		this.xpReward = 90;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 8.0)
			.add(Attributes.MOVEMENT_SPEED, 0.2)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.ARMOR, 12.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SEALS, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 20.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** How many seals are still burning, as the client last heard it. */
	public int seals() {
		return this.entityData.get(DATA_SEALS);
	}

	public boolean sealed() {
		return this.seals() > 0;
	}

	/** The middle of its hall. */
	public BlockPos home() {
		if (this.home == null) {
			this.home = this.blockPosition();
		}
		return this.home;
	}

	/** The lanterns still standing in its hall, counted fresh. */
	public List<BlockPos> findSeals(ServerLevel level) {
		List<BlockPos> found = new ArrayList<>();
		BlockPos at = this.home();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-SEAL_RANGE, -3, -SEAL_RANGE), at.offset(SEAL_RANGE, 4, SEAL_RANGE))) {
			if (level.getBlockState(pos).is(dev.forja.registry.ModBlocks.FAROL_DE_PAVESA)) {
				found.add(pos.immutable());
			}
		}
		return found;
	}

	/** Nothing gets through while a seal stands. Not fire, not a beam, not a player with a hammer. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.sealed()) {
			this.turnAway(level, source);
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	/** A blow bouncing off, which is the only way a player learns that it should not be swinging. */
	private void turnAway(ServerLevel level, DamageSource source) {
		if (this.tickCount % 4 == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.4F, 0.6F);
		}
		level.sendParticles(SEAL, this.getX(), this.getY(1.9), this.getZ(), 10, 0.5, 0.5, 0.5, 0.02);
		if (source.getEntity() instanceof ServerPlayer player && !this.announced) {
			this.announced = true;
			player.sendSystemMessage(Component.translatable("gui.forja.cuno_sellado", this.seals()).withColor(0xD8B454));
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.tickCount % 10 == 0) {
			this.checkSeals(level);
		}
		this.bar.setProgress(this.sealed() ? 1.0F : this.getHealth() / this.getMaxHealth());
		this.bar.setName(this.sealed()
			? Component.translatable("gui.forja.cuno_barra_sellado", this.seals())
			: Component.translatable("entity.forja.guardian_de_cuno"));
		if (this.tickCount % 20 == 0) {
			this.watchers(level);
		}
		this.strike(level);
	}

	/**
	 * Counts the lanterns and draws a tether to each.
	 *
	 * <p>The tether is the whole tutorial. Nothing tells the player that the lanterns matter; a line of
	 * gold running from the seal on its face to each pillar tells them, and it tells them from across
	 * the room without a word of text.
	 */
	private void checkSeals(ServerLevel level) {
		List<BlockPos> seals = this.findSeals(level);
		int before = this.entityData.get(DATA_SEALS);
		this.entityData.set(DATA_SEALS, seals.size());
		Vec3 face = this.position().add(0.0, 2.1, 0.0);
		for (BlockPos seal : seals) {
			Vec3 toward = Vec3.atCenterOf(seal).subtract(face);
			int steps = (int) Math.max(4, toward.length() * 2);
			for (int step = 1; step <= steps; step++) {
				Vec3 along = face.add(toward.scale(step / (double) steps));
				level.sendParticles(SEAL, along.x, along.y, along.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
		if (before > seals.size()) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0F, 1.2F);
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY(2.1), this.getZ(), 30, 0.6, 0.6, 0.6, 0.2);
		}
		if (before > 0 && seals.isEmpty()) {
			this.unseal(level);
		}
	}

	/** The last lantern going out. */
	private void unseal(ServerLevel level) {
		this.triggerAnim("cuno", "unsealed");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.4F, 1.6F);
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA, this.getX(), this.getY(2.1), this.getZ(), 50, 0.9, 1.0, 0.9, 0.06);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(this) < 48.0 * 48.0) {
				player.sendSystemMessage(Component.translatable("gui.forja.cuno_abierto").withColor(0xFF7A1E));
			}
		}
	}

	private void strike(ServerLevel level) {
		if (this.stampCooldown > 0) {
			this.stampCooldown--;
		}
		if (this.windup.charging()) {
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1.0, 0.2));
			this.windup.tick(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || this.stampCooldown > 0) {
			return;
		}
		double distance = this.distanceTo(target);
		if (distance < STAMP_MIN || distance > STAMP_MAX || !this.hasLineOfSight(target)) {
			return;
		}
		this.stamp(level, target.position());
	}

	/** Lifts the die. Forty ticks later it comes down where it was aimed. */
	public void stamp(ServerLevel level, Vec3 at) {
		this.stampCooldown = STAMP_COOLDOWN;
		this.triggerAnim("cuno", "stamp");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 2.0F, 0.4F);
		this.windup.start(STAMP_WINDUP, (world, left, total) -> {
		}, world -> this.land(world, at));
		// The print it is going to leave, in the gold of the seal: the line is the edge of the die and
		// the glow fills in under it as the die comes over.
		this.windup.warn(Shockwave.telegraph(level, this, at, STAMP_RADIUS, STAMP_WINDUP, 6, Shockwave.SEAL, 0.5F));
	}

	/** The die hitting the floor. */
	private void land(ServerLevel level, Vec3 at) {
		Shockwave ring = this.windup.takeWarning();
		if (ring != null) {
			ring.fire(at);
		} else {
			Shockwave.burst(level, at, STAMP_RADIUS, 6, Shockwave.SEAL, 0.5F);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.4F, 0.4F);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 4, 1.2, 0.1, 1.2, 0.0);
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA, at.x, at.y + 0.2, at.z, 40, STAMP_RADIUS * 0.4, 0.3, STAMP_RADIUS * 0.4, 0.05);
		level.sendParticles(SEAL, at.x, at.y + 0.15, at.z, 30, STAMP_RADIUS * 0.5, 0.1, STAMP_RADIUS * 0.5, 0.02);
		for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class,
			new AABB(at, at).inflate(STAMP_RADIUS, 2.0, STAMP_RADIUS), victim -> victim != this && victim.isAlive())) {
			double flat = Math.sqrt((caught.getX() - at.x) * (caught.getX() - at.x) + (caught.getZ() - at.z) * (caught.getZ() - at.z));
			if (flat > STAMP_RADIUS) {
				continue;
			}
			double share = 1.0 - flat / STAMP_RADIUS;
			float blow = (float) (STAMP_DAMAGE * share);
			caught.invulnerableTime = 0;
			// The part armour stops, and then the part it does not. Two hits rather than one so the
			// pierce is a real bypass and not a bigger number that plate still eats.
			caught.hurtServer(level, this.damageSources().mobAttack(this), blow * (1.0F - STAMP_PIERCE));
			caught.invulnerableTime = 0;
			caught.hurtServer(level, level.damageSources().magic(), blow * STAMP_PIERCE);
			caught.hurtArmor(this.damageSources().mobAttack(this), (float) (STAMP_ARMOUR_WEAR * share));
			caught.push(0.0, 0.45 * share, 0.0);
			caught.hurtMarked = true;
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.bar.removeAllPlayers();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.HOSTILE, 2.2F, 0.4F);
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA, this.getX(), this.getY(2.0), this.getZ(), 60, 0.9, 1.0, 0.9, 0.08);
		// The die comes apart into the shapes it was cut for: three templates and its own seal.
		for (int drop = 0; drop < 3; drop++) {
			ItemStack template = new ItemStack(dev.forja.registry.ModItems.PLANTILLA);
			dev.forja.item.TemplateItem.engrave(template, TEMPLATES[this.random.nextInt(TEMPLATES.length)]);
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(1.0), this.getZ(), template));
		}
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(1.0), this.getZ(),
			new ItemStack(dev.forja.registry.ModItems.SELLO)));
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(1.0), this.getZ(),
			new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 4 + this.random.nextInt(5))));
	}

	/** The shapes cut into it: the ones worth carrying a template of. */
	private static final dev.forja.part.PartType[] TEMPLATES = {
		dev.forja.part.PartType.HOJA,
		dev.forja.part.PartType.CABEZA_MARTILLO,
		dev.forja.part.PartType.CABEZA_PICO,
		dev.forja.part.PartType.PLACA_PECHERA,
		dev.forja.part.PartType.PUNTA_LANZA,
		dev.forja.part.PartType.BRAZOS_ARCO,
	};

	/** Everyone close enough sees the bar; everyone who walks away loses it. */
	private void watchers(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(this) <= 48.0 * 48.0) {
				this.bar.addPlayer(player);
			} else {
				this.bar.removePlayer(player);
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
		super.addAdditionalSaveData(output);
		BlockPos at = this.home();
		output.putIntArray("forja_hall", new int[] {at.getX(), at.getY(), at.getZ()});
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
		super.readAdditionalSaveData(input);
		input.getIntArray("forja_hall")
			.filter(hall -> hall.length == 3)
			.ifPresent(hall -> this.home = new BlockPos(hall[0], hall[1], hall[2]));
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
		return !this.sealed() && super.canBeAffected(effect);
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.DEEPSLATE_BRICKS_STEP;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.DEEPSLATE_BRICKS_HIT;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<CuneGuardian>("cuno", test ->
			test.setAndContinue(test.isMoving() ? WALK : IDLE)
		).triggerableAnim("stamp", STAMP).triggerableAnim("unsealed", UNSEALED));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
