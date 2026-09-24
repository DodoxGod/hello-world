package dev.forja.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import dev.forja.forge.Assembler;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * Coraza vacía: a suit of plate that stands up on its own in the old forges, with nothing inside it but
 * a cold light. Plain steel slides off it — only work that came off a star bites properly — so it is the
 * one thing in the world that asks you to be carrying your own gear rather than a shop-bought sword.
 */
public class HollowArmor extends Monster implements GeoEntity {
	public static final double HEALTH = 45.0;

	/** The share of a blow that lands when it comes from something that was not forged. */
	public static final float UNFORGED_SHARE = 0.35F;

	/** How far the thing inside will jump to find another empty suit to wear. */
	public static final double SOUL_REACH = 12.0;

	/** What the suit it jumps into gets back, and for how long it burns brighter. */
	public static final float SOUL_HEAL = 8.0F;
	public static final int SOUL_RAGE = 200;

	/** The lunge: how often, how far it will throw itself and what it costs to be in the way. */
	/**
	 * How long it braces before it goes.
	 *
	 * <p>The {@code dash} animation crouches back at three ticks, which is honest but far too quick to
	 * read, so the animation was given a longer set and this matches it. Three ticks of warning is not
	 * warning, it is a coin toss.
	 */
	// Tripled with the smith's hammer: a suit of plate throwing its whole weight at you is the other
	// move in the mod that ought to look like it costs something to start.
	public static final int DASH_WINDUP = 33;

	public static final int DASH_COOLDOWN = 130;
	public static final int DASH_TICKS = 10;
	public static final double DASH_MIN = 4.0;
	public static final double DASH_MAX = 11.0;
	public static final float DASH_DAMAGE = 7.0F;

	/** The wail: how often, how far it carries and how long it hangs on whoever hears it. */
	public static final int WAIL_COOLDOWN = 320;
	public static final double WAIL_REACH = 7.0;
	public static final double WAIL_CALL = 14.0;
	public static final int WAIL_TICKS = 120;
	public static final float WAIL_MEND = 4.0F;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation CUT = RawAnimation.begin().thenPlay("cut");
	private static final RawAnimation DASH = RawAnimation.begin().thenPlay("dash");
	private static final RawAnimation WAIL = RawAnimation.begin().thenPlay("wail");

	/** What is left of it: the plates it was made of. */
	private static final PartType[] PLATES = {
		PartType.PLACA_PECHERA, PartType.PLACA_CASCO, PartType.PLACA_GREBAS, PartType.PLACA_BOTAS, PartType.FORRO,
	};

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private int dashCooldown = 50;
	private int wailCooldown = 120;
	private int dashing;
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();
	private final java.util.Set<java.util.UUID> dashHit = new java.util.HashSet<>();

	public HollowArmor(EntityType<? extends HollowArmor> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 5.0)
			.add(Attributes.ARMOR, 10.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
			.add(Attributes.MOVEMENT_SPEED, 0.25)
			.add(Attributes.FOLLOW_RANGE, 20.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Whether the blow came from something a smith made, which is the only thing it really feels. */
	public static boolean forgedBlow(DamageSource source) {
		if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) {
			// An arrow counts if the arrow itself was cut at a table.
			return source.getDirectEntity() instanceof ForgedArrow;
		}
		return source.getEntity() instanceof LivingEntity attacker && attacker.getMainHandItem().has(ModComponents.PARTS);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		float taken = forgedBlow(source) ? damage : damage * UNFORGED_SHARE;
		if (taken < damage && this.tickCount % 4 == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 0.6F, 1.4F);
			level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(1.2), this.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
		}
		return super.hurtServer(level, source, taken);
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
		// There is nobody in there to poison.
		return !effect.is(net.minecraft.world.effect.MobEffects.POISON)
			&& !effect.is(net.minecraft.world.effect.MobEffects.WITHER)
			&& !effect.is(net.minecraft.world.effect.MobEffects.HUNGER)
			&& super.canBeAffected(effect);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		this.triggerAnim("coraza", "cut");
		return super.doHurtTarget(level, target);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// Whatever is in there shows through the gaps: a wisp at the neck and a breath at the waist.
		if (this.tickCount % 8 == 0) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(1.72), this.getZ(), 1, 0.1, 0.04, 0.1, 0.0);
		}
		if (this.tickCount % 24 == 0) {
			level.sendParticles(dev.forja.registry.ModParticles.ALMA, this.getX(), this.getY(0.95), this.getZ(), 1, 0.16, 0.1, 0.16, 0.01);
		}
		// And when it moves, it drags a little of itself along.
		if (this.getDeltaMovement().horizontalDistanceSqr() > 0.002 && this.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(0.4), this.getZ(), 1, 0.12, 0.05, 0.12, 0.0);
		}
		this.soulMoves(level);
	}

	/**
	 * What it does when the sword alone is not working: it throws itself across the gap, or it opens up
	 * and calls. The call is the dangerous one, because every other suit in the room hears it.
	 */
	private void soulMoves(ServerLevel level) {
		if (this.dashCooldown > 0) {
			this.dashCooldown--;
		}
		if (this.wailCooldown > 0) {
			this.wailCooldown--;
		}
		if (this.dashing > 0) {
			this.rollDash(level);
			return;
		}
		// Planting its feet is the tell. It cannot turn once it has done that, which is the point.
		if (this.windup.charging()) {
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 1.0, 0.2));
			this.windup.tick(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		double distance = this.distanceTo(target);
		if (this.wailCooldown == 0 && distance <= WAIL_REACH) {
			this.wail(level);
		} else if (this.dashCooldown == 0 && distance >= DASH_MIN && distance <= DASH_MAX && this.hasLineOfSight(target)) {
			this.lunge(level, target);
		}
	}

	/**
	 * Embestida: the thing inside goes first and the plate follows, which is why it does not steer.
	 *
	 * <p>That last part was in the comment before it was in the code. It launched on the same tick it
	 * decided to, aimed at wherever you were standing at that instant — unsteerable and unreadable at
	 * once, which is just a tax. Now it sets itself first and the line it is going to travel is drawn
	 * on the floor while it does. The aim is taken <b>here</b> and not at launch, so stepping off that
	 * line is the whole answer to it.
	 */
	public void lunge(ServerLevel level, LivingEntity target) {
		this.dashCooldown = DASH_COOLDOWN;
		this.triggerAnim("coraza", "dash");
		Vec3 aim = target.position().subtract(this.position()).normalize();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
		this.windup.start(DASH_WINDUP, (world, left, total) -> {
			for (int step = 1; step <= 10; step++) {
				Vec3 along = this.position().add(aim.scale(step * DASH_MAX / 10.0));
				world.sendParticles(ParticleTypes.SOUL, along.x, along.y + 0.15, along.z, 1, 0.06, 0.02, 0.06, 0.0);
			}
		}, world -> {
			this.dashing = DASH_TICKS;
			this.dashHit.clear();
			this.setDeltaMovement(aim.x * 0.95, 0.32, aim.z * 0.95);
			this.hurtMarked = true;
			world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.4F, 1.3F);
		});
	}

	/** One tick of the lunge: it keeps its feet moving and cuts whatever it goes through, once each. */
	private void rollDash(ServerLevel level) {
		this.dashing--;
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(0.9), this.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.7),
			other -> other != this && other.isAlive() && !(other instanceof HollowArmor))) {
			if (!this.dashHit.add(victim.getUUID())) {
				continue;
			}
			victim.invulnerableTime = 0;
			victim.hurtServer(level, this.damageSources().mobAttack(this), DASH_DAMAGE);
			Vec3 away = victim.position().subtract(this.position()).normalize();
			double hold = 1.0 - dev.forja.upgrade.Upgrades.anchor(victim);
			victim.push(away.x * 0.5 * hold, 0.36 * hold, away.z * 0.5 * hold);
			victim.hurtMarked = true;
			this.dashing = 0;
		}
	}

	/**
	 * Lamento: the suit opens around what is wearing it and it calls. What hears it gets slow and finds
	 * its arms heavy; what answers it is every other empty suit in the room, which gets back on its feet.
	 */
	public void wail(ServerLevel level) {
		this.wailCooldown = WAIL_COOLDOWN;
		this.triggerAnim("coraza", "wail");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.6F, 1.5F);
		// The call going out, as far as it is heard: a ring the colour of what is inside the plate.
		// The souls stay, close in, because the ring is the sound and they are what is making it.
		Shockwave.burst(level, this.position(), WAIL_REACH, 12, Shockwave.SOUL, 0.6F);
		level.sendParticles(dev.forja.registry.ModParticles.ALMA, this.getX(), this.getY(1.0), this.getZ(), 16, 0.8, 0.4, 0.8, 0.08);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(WAIL_REACH),
			other -> other != this && other.isAlive() && !(other instanceof HollowArmor))) {
			// Firme: it still hears the call, it just does not answer it.
			if (dev.forja.upgrade.Upgrades.steadfast(victim)) {
				continue;
			}
			victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, WAIL_TICKS, 0));
			victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MINING_FATIGUE, WAIL_TICKS, 0));
		}
		for (HollowArmor sibling : level.getEntitiesOfClass(HollowArmor.class, this.getBoundingBox().inflate(WAIL_CALL),
			other -> other != this && other.isAlive())) {
			sibling.heal(WAIL_MEND);
			if (sibling.getTarget() == null) {
				sibling.setTarget(this.getTarget());
			}
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, sibling.getX(), sibling.getY(1.2), sibling.getZ(), 8, 0.2, 0.3, 0.2, 0.02);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// Whatever was in there, leaving. A column of it, straight up and gone — the one good thing
		// that happens to a suit of this armour, and until now the only sign of it was the loot.
		for (int step = 0; step < 26; step++) {
			double height = step * 0.09;
			level.sendParticles(dev.forja.registry.ModParticles.ALMA,
				this.getX(), this.getY(0.4) + height, this.getZ(), 1,
				0.18 + height * 0.1, 0.05, 0.18 + height * 0.1, 0.02);
		}
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(0.9), this.getZ(),
			14, 0.3, 0.4, 0.3, 0.05);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.4F, 0.6F);
		// And the empty plate hitting the ground after it.
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.HOSTILE, 0.9F, 0.7F);

		// The suit falls apart into the plates it was made of.
		int pieces = 1 + this.random.nextInt(2);
		for (int i = 0; i < pieces; i++) {
			PartType plate = PLATES[this.random.nextInt(PLATES.length)];
			ForgeMaterial material = plate == PartType.FORRO ? ForgeMaterial.CUERO : ForgeMaterial.HIERRO;
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.5), this.getZ(), Assembler.createPart(plate, material)));
		}
		// What the suit itself was made of, which is a material nothing else gives.
		if (this.random.nextFloat() < 0.4F) {
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.5), this.getZ(),
				new ItemStack(dev.forja.registry.ModItems.PLACA_HUECA, 1 + this.random.nextInt(2))));
		}
		if (this.random.nextFloat() < 0.25F) {
			dev.forja.upgrade.Upgrade upgrade = dev.forja.upgrade.Upgrade.values()[this.random.nextInt(dev.forja.upgrade.Upgrade.values().length)];
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.5), this.getZ(),
				dev.forja.item.UpgradeOrbItem.create(upgrade, 25 + this.random.nextInt(2) * 25)));
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.9F, 1.3F);
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(1.0), this.getZ(), 18, 0.3, 0.5, 0.3, 0.02);
		this.soulEscapes(level);
	}

	/**
	 * Breaking the suit does not kill what was wearing it. If there is another empty suit within reach it
	 * moves into that one, which comes back up healed and angry, so a room of them has to be cleared fast
	 * rather than one at a time.
	 */
	private void soulEscapes(ServerLevel level) {
		HollowArmor next = null;
		double best = SOUL_REACH * SOUL_REACH;
		for (HollowArmor other : level.getEntitiesOfClass(HollowArmor.class,
			this.getBoundingBox().inflate(SOUL_REACH), armor -> armor != this && armor.isAlive())) {
			double distance = other.distanceToSqr(this);
			if (distance < best) {
				best = distance;
				next = other;
			}
		}
		if (next == null) {
			// Nothing to move into: it goes out where it stood.
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.7F, 0.8F);
			return;
		}
		next.heal(SOUL_HEAL);
		next.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, SOUL_RAGE, 0, false, true));
		next.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, SOUL_RAGE, 0, false, true));
		if (this.getLastHurtByMob() != null) {
			next.setTarget(this.getLastHurtByMob());
		}
		// Draw the jump, so it is obvious which suit it went into.
		int steps = 12;
		for (int step = 1; step <= steps; step++) {
			double fraction = (double) step / steps;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
				net.minecraft.util.Mth.lerp(fraction, this.getX(), next.getX()),
				net.minecraft.util.Mth.lerp(fraction, this.getY(1.0), next.getY(1.4)),
				net.minecraft.util.Mth.lerp(fraction, this.getZ(), next.getZ()),
				1, 0.05, 0.05, 0.05, 0.0);
		}
		level.sendParticles(ParticleTypes.SOUL, next.getX(), next.getY(1.2), next.getZ(), 20, 0.3, 0.4, 0.3, 0.03);
		level.playSound(null, next.getX(), next.getY(), next.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 0.7F);
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.SOUL_ESCAPE.value();
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
	protected float getSoundVolume() {
		return 1.0F;
	}

	@Override
	public float getVoicePitch() {
		return 0.8F;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<HollowArmor>("coraza", test ->
			test.setAndContinue(test.isMoving() ? WALK : IDLE)
		).triggerableAnim("cut", CUT).triggerableAnim("dash", DASH).triggerableAnim("wail", WAIL));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
