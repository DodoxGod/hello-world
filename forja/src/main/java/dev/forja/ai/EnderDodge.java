package dev.forja.ai;

import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.combat.CombatConfig;
import dev.forja.combat.CombatFeedback;
import dev.forja.combat.CombatStats;
import dev.forja.combat.Posture;
import dev.forja.mixin.EnderManAccess;
import dev.forja.mixin.LivingEntityAiAccess;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.phys.Vec3;

/**
 * The enderman's dodge. Andy, 2026-09-28: "quiero que el enderman tenga un 34 % de probabilidad de esquivar por
 * teletransportación, pero que tenga cooldown de 7 segundos si hay una teletransportación exitosa".
 *
 * <p>When a blow with an attacker behind it is about to reach an enderman, it rolls
 * {@link CombatConfig#endermanDodgeChance}. On a success it blinks 4 to 8 blocks away, to the side of or away from
 * whoever struck, with vanilla's own teleport (sound at both ends, portal particles, game event), and the blow is
 * gone before armour, posture or anything else sees it. It still knows who swung at it, and turns on them as if hit.
 * Only a teleport that went through starts the cooldown ({@link CombatConfig#endermanDodgeCooldownTicks}); boxed in
 * with nowhere to land, it takes the blow and the next one rolls again.
 *
 * <p>What it dodges: any blow whose source has a living attacker other than itself, so melee from players and mobs,
 * thorns, explosions someone set off, a staff's bolt or a tome's area. Not what vanilla already dodges: projectiles
 * (arrows, tridents, fireballs, anything tagged as one) and thrown potions never reach this, the enderman blinks from
 * them on its own every time. Not damage with nobody behind it (a fall, lava, fire, cactus, drowning), nor what
 * bypasses invulnerability (the void, /kill, even a /damage generic_kill attributed to a player). Not a blow the hurt
 * cooldown is going to swallow anyway, and not while it is staggered: a broken balance is the opening a finisher
 * needs, and it would be a cheat to blink out of it.
 *
 * <p>It lives in the damage path (MobDefense.allowDamage, from CombatHooks), so it works the same with the mod's mob
 * brain driving the enderman, with vanilla's goals, or with no AI at all. The cooldown is transient: a weak map by
 * entity, not saved, so an enderman that is unloaded and loaded again may dodge again sooner.
 */
public final class EnderDodge {
	/** How far the blink goes, in blocks, along the ground. */
	static final double MIN_DISTANCE = 4.0;
	static final double MAX_DISTANCE = 8.0;
	/** Spots it tries before it gives up and takes the blow. */
	static final int ATTEMPTS = 16;

	/** When each enderman can dodge again (game time). */
	private static final Map<LivingEntity, Long> READY_AT = new WeakHashMap<>();

	private EnderDodge() {
	}

	/** Ticks until this enderman can dodge again; 0 when it can. */
	public static int cooldownLeft(LivingEntity mob) {
		Long ready = READY_AT.get(mob);
		return ready == null ? 0 : (int) Math.max(0L, ready - mob.level().getGameTime());
	}

	/** Starts the cooldown as if it had just dodged: the balance probes use it so a measured blow lands. */
	public static void hold(LivingEntity mob) {
		READY_AT.put(mob, mob.level().getGameTime() + CombatConfig.get().endermanDodgeCooldownTicks);
	}

	/** Clears the cooldown. */
	public static void forget(LivingEntity mob) {
		READY_AT.remove(mob);
	}

	/**
	 * Before a blow reaches an enderman: whether it blinks away from it. Called only for blows that do not bypass
	 * invulnerability, with the combat overhaul on.
	 *
	 * @return true when it teleported and the blow is to be cancelled
	 */
	public static boolean dodges(EnderMan ender, DamageSource source, float amount) {
		double chance = CombatConfig.get().endermanDodgeChance;
		// Forja's rules (Ladder): below Normal an enderman blinks only as vanilla's does.
		if (chance <= 0.0 || !ender.isAlive() || ender.level().isClientSide() || !dev.forja.difficulty.Ladder.thinks(ender)
			|| !(source.getEntity() instanceof LivingEntity attacker) || attacker == ender
			|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			// Vanilla's: it already blinks from every projectile and thrown potion.
			|| source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof AbstractThrownPotion
			|| cooldownLeft(ender) > 0 || Posture.isStaggered(ender, ender.level().getGameTime())
			|| swallowed(ender, source, amount)) {
			return false;
		}
		if (ender.getRandom().nextFloat() >= chance) {
			return false;
		}
		Vec3 from = ender.position();
		if (!blink(ender, attacker)) {
			CombatStats.record(ender, CombatStats.TELEPORT_DODGE_FAILED);
			return false;
		}
		hold(ender);
		CombatStats.record(ender, CombatStats.TELEPORT_DODGE);
		CombatFeedback.dodgedHit(ender, from);
		// It dodged the blow, not the fight: the one who struck is still who it turns on.
		ender.setLastHurtByMob(attacker);
		return true;
	}

	/** Whether the hurt cooldown will swallow this blow anyway (vanilla's check, which comes after this one). */
	private static boolean swallowed(EnderMan ender, DamageSource source, float amount) {
		return ender.invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)
			&& amount <= ((LivingEntityAiAccess) ender).forja$lastHurt();
	}

	/** Teleports it 4 to 8 blocks away, within 90 degrees either side of straight away from the attacker. */
	private static boolean blink(EnderMan ender, LivingEntity attacker) {
		RandomSource random = ender.getRandom();
		double away = Math.atan2(ender.getZ() - attacker.getZ(), ender.getX() - attacker.getX());
		if (ender.position().subtract(attacker.position()).horizontalDistanceSqr() < 1.0E-6) {
			away = random.nextDouble() * Math.PI * 2.0;
		}
		for (int i = 0; i < ATTEMPTS; i++) {
			double angle = away + (random.nextDouble() - 0.5) * Math.PI;
			double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
			double x = ender.getX() + Math.cos(angle) * distance;
			double y = ender.getY() + random.nextInt(7) - 2;
			double z = ender.getZ() + Math.sin(angle) * distance;
			if (((EnderManAccess) ender).forja$teleport(x, y, z)) {
				return true;
			}
		}
		return false;
	}
}
