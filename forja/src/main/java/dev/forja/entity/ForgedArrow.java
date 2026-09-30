package dev.forja.entity;

import dev.forja.combat.ArrowTips;
import dev.forja.forge.ForgeStats;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModEntities;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An arrow forged from a tip and a fletching. The fletching decides how fast it leaves the string; the tip
 * (combat/ArrowTips) how heavy it flies, how hard it bites, what armour it goes through and what it does on
 * impact. The tip's material is synced to the client, which draws the tip in its colour (client/ForgedArrowRenderer)
 * and flies the arrow with the same gravity the server does.
 */
public class ForgedArrow extends Arrow {
	/** The tip's material, by ordinal; -1 for an arrow that has none (a bare /summon). */
	private static final EntityDataAccessor<Integer> TIP = SynchedEntityData.defineId(ForgedArrow.class, EntityDataSerializers.INT);

	private float speed = 1.0F;
	private byte pierce;
	/** What AbstractArrow keeps to itself: the base damage, before the arrow's speed multiplies it. */
	private double base = 2.0;

	public ForgedArrow(EntityType<? extends ForgedArrow> type, Level level) {
		super(type, level);
	}

	public ForgedArrow(Level level, LivingEntity shooter, ItemStack arrow, ItemStack weapon) {
		super(ModEntities.FLECHA_FORJADA, level);
		this.setOwner(shooter);
		this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
		this.setPickupItemStack(arrow.copyWithCount(1));
		this.apply(arrow);
		// Tirador: a bow with the gift throws everything it looses harder.
		if (dev.forja.forge.Perk.has(weapon, dev.forja.forge.Perk.TIRADOR)) {
			this.speed *= dev.forja.forge.Perk.TIRADOR_SPEED;
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(TIP, -1);
	}

	/** Reads the parts and upgrades of the stack it came from, once, when it is created. */
	private void apply(ItemStack arrow) {
		ForgedParts parts = arrow.get(ModComponents.PARTS);
		if (parts == null) {
			return;
		}
		Upgrades upgrades = arrow.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		ForgeStats.Sheet sheet = ForgeStats.sheet(arrow, parts);
		ForgeMaterial material = ArrowTips.tipOf(arrow);
		ArrowTips.Tip tip = material == null ? null : ArrowTips.of(material);
		if (material != null) {
			this.entityData.set(TIP, material.ordinal());
		}
		// The tip's weight: a heavy one bites harder and leaves slower, a light one the other way round.
		this.setBaseDamage(sheet.arrowDamage * (tip == null ? 1.0F : tip.damage()));
		this.speed = sheet.drawSpeed * (tip == null ? 1.0F : tip.speed());
		this.pierce = (byte) (Math.round(Upgrade.arrowPierce(upgrades.percent(Upgrade.PUNTA_PERFORANTE) / 100.0F)) + (tip == null ? 0 : tip.pierce()));
		int poison = Math.round(Upgrade.arrowPoisonSeconds(upgrades.percent(Upgrade.PUNTA_ENVENENADA) / 100.0F) * 20.0F);
		if (poison > 0) {
			this.addEffect(new MobEffectInstance(MobEffects.POISON, poison, 0));
		}
		int fire = Math.round(Upgrade.arrowFireSeconds(upgrades.percent(Upgrade.PUNTA_IGNEA) / 100.0F));
		if (fire > 0) {
			this.igniteForSeconds(fire);
		}
		// Punta maldita: poison and fire in the same tip end in wither.
		if (dev.forja.upgrade.Synergy.PUNTA_MALDITA.active(arrow)) {
			this.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
		}
		// Asta perfecta: quick and sharp at once, so it leaves the string critical.
		if (dev.forja.upgrade.Synergy.ASTA_PERFECTA.active(arrow)) {
			this.setCritArrow(true);
		}
		// Fortuna (an emerald or electrum tip): now and then it leaves critical all the same.
		if (tip != null && tip.special() == ArrowTips.Special.FORTUNA && this.random.nextFloat() < ArrowTips.LUCK_CRIT) {
			this.setCritArrow(true);
		}
		// Cargador: a quarter of these arrows never really left the quiver.
		if (dev.forja.forge.Perk.has(arrow, dev.forja.forge.Perk.CARGADOR)
			&& this.getOwner() instanceof net.minecraft.world.entity.player.Player archer
			&& archer.getRandom().nextFloat() < dev.forja.forge.Perk.CARGADOR_SHARE) {
			ItemStack spare = arrow.copyWithCount(1);
			if (!archer.getInventory().add(spare)) {
				archer.drop(spare, false);
			}
		}
	}

	@Override
	public void setBaseDamage(double damage) {
		super.setBaseDamage(damage);
		this.base = damage;
	}

	/** The base damage, before the arrow's speed multiplies it. */
	public double baseDamage() {
		return this.base;
	}

	/** The tip's material, on either side; null for an arrow without one. */
	public @Nullable ForgeMaterial tipMaterial() {
		int index = this.entityData.get(TIP);
		ForgeMaterial[] all = ForgeMaterial.values();
		return index >= 0 && index < all.length ? all[index] : null;
	}

	public ArrowTips.@Nullable Tip tip() {
		ForgeMaterial material = this.tipMaterial();
		return material == null ? null : ArrowTips.of(material);
	}

	public ArrowTips.Special special() {
		ArrowTips.Tip tip = this.tip();
		return tip == null ? ArrowTips.Special.NONE : tip.special();
	}

	/** The share of armour the tip goes through on top of an arrow's own (combat/AttackClassifier). */
	public float tipPenetration() {
		ArrowTips.Tip tip = this.tip();
		return tip == null ? 0.0F : tip.penetration();
	}

	/** How fast its own tip and fletching make it leave the string, against a plain arrow. */
	public float speedFactor() {
		return this.speed;
	}

	@Override
	public byte getPierceLevel() {
		// Punta perforante and an echo tip live on the arrow, not on the bow, so this is where their piercing comes from.
		return (byte) Math.max(super.getPierceLevel(), this.pierce);
	}

	@Override
	protected double getDefaultGravity() {
		ArrowTips.Tip tip = this.tip();
		return tip == null ? super.getDefaultGravity() : tip.gravity();
	}

	@Override
	protected float getWaterInertia() {
		// Marea (prismarine): it cuts water the way a trident does.
		return this.special() == ArrowTips.Special.MAREA ? 0.99F : super.getWaterInertia();
	}

	@Override
	public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
		// The fletching and the tip are the only things in the mod that change how fast an arrow leaves the string.
		super.shoot(x, y, z, velocity * this.speed, inaccuracy);
	}

	@Override
	public void tick() {
		ArrowTips.Special special = this.special();
		if (special == ArrowTips.Special.BUSCADORA && !this.isInGround() && this.tickCount > 1 && this.level() instanceof ServerLevel level) {
			this.seek(level);
		}
		super.tick();
		if (this.level().isClientSide() && !this.isInGround() && special != ArrowTips.Special.NONE && this.tickCount % 2 == 0) {
			ForgeMaterial material = this.tipMaterial();
			if (material != null) {
				this.level().addParticle(new DustParticleOptions(material.color, 0.7F), this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
			}
		}
	}

	/** Buscadora: bends a few degrees a tick towards the nearest foe of its archer ahead of it. */
	private void seek(ServerLevel level) {
		Vec3 motion = this.getDeltaMovement();
		double speed = motion.length();
		if (speed < 0.2) {
			return;
		}
		Vec3 heading = motion.scale(1.0 / speed);
		Entity owner = this.getOwner();
		LivingEntity best = null;
		double bestScore = -1.0;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(ArrowTips.SEEK_RANGE),
			e -> e.isAlive() && e != owner && (e instanceof Enemy || e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == owner))) {
			Vec3 to = other.getBoundingBox().getCenter().subtract(this.position());
			double distance = to.length();
			if (distance < 0.5) {
				continue;
			}
			double facing = heading.dot(to.scale(1.0 / distance));
			if (facing < 0.5) {
				continue;
			}
			double score = facing / distance;
			if (score > bestScore) {
				bestScore = score;
				best = other;
			}
		}
		if (best == null) {
			return;
		}
		Vec3 want = best.getBoundingBox().getCenter().subtract(this.position()).normalize();
		double most = Math.toRadians(ArrowTips.SEEK_DEGREES);
		double angle = Math.acos(Mth.clamp(heading.dot(want), -1.0, 1.0));
		Vec3 turned = angle <= most ? want : heading.scale(Math.sin(angle - most)).add(want.scale(Math.sin(most))).normalize();
		this.setDeltaMovement(turned.scale(speed));
		this.hurtMarked = true;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		// Brasa, Sol and Luna bite harder when their fire, light or dark is there; only for this one blow.
		double before = this.base;
		if (hit.getEntity() instanceof LivingEntity target && this.level() instanceof ServerLevel level) {
			this.setBaseDamage(before + ArrowTips.conditionalBonus(this.special(), target.isOnFire(),
				dev.forja.upgrade.TraitEffects.inSun(level, target), dev.forja.upgrade.TraitEffects.inDark(level, target), target.isInWaterOrRain()));
		}
		super.onHitEntity(hit);
		this.setBaseDamage(before);
		// Vidrio: it shatters on what it hits, whether or not that was the last thing it went through.
		if (this.special() == ArrowTips.Special.VIDRIO && !this.level().isClientSide() && this.isAlive()) {
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.6F, 1.6F);
			this.discard();
		}
	}

	@Override
	protected void doKnockback(LivingEntity target, DamageSource source) {
		super.doKnockback(target, source);
		ArrowTips.Tip tip = this.tip();
		if (tip != null && tip.heavy()) {
			Vec3 push = this.getDeltaMovement().multiply(1.0, 0.0, 1.0);
			if (push.lengthSqr() > 1.0E-6) {
				push = push.normalize().scale(ArrowTips.HEAVY_SHOVE);
				target.push(push.x, 0.08, push.z);
			}
		}
	}

	@Override
	protected void doPostHurtEffects(LivingEntity target) {
		super.doPostHurtEffects(target);
		if (this.level() instanceof ServerLevel level) {
			ArrowTips.onHit(level, this, this.getOwner() instanceof LivingEntity shooter ? shooter : null, target, this.special());
		}
	}
}
