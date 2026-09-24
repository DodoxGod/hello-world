package dev.forja.combat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The combat section of config/forja.json. Armor never gets a line per piece: a piece's resistances
 * and weight come out of its materials (see {@link MaterialCombat}), so the only per-material entry
 * here is an optional override for the odd material whose derived numbers you disagree with.
 */
public final class CombatConfig {
	public boolean enabled = true;

	// --- Armor formula ---------------------------------------------------------------------------
	/** K in armor / (armor + K): higher means every point of armor blocks less. */
	public double curveK = 20.0;
	/** The most armor can ever block, 0 to 1. */
	public double maxReduction = 0.8;
	/** Toughness at which an attacker's penetration is halved. */
	public double toughnessScale = 10.0;
	/** Protection a piece keeps at zero durability left (1 = durability does not matter). */
	public double minDurabilityFactor = 0.6;
	/** When false, every piece guards every hit equally. */
	public boolean hitZones = true;
	/** Extra damage for a precise hit to the head (your blade, anyone's arrow). */
	public double headMultiplier = 1.3;
	/** Slowdown with a full set of the heaviest plate (weight 1.0). */
	public double maxArmorSlow = 0.10;
	/** Optional per-material tweaks, e.g. "hierro": {"slash": 1.1}. Missing fields keep the derived value. */
	public Map<String, MaterialOverride> materiales = new LinkedHashMap<>();

	public static final class MaterialOverride {
		public Double slash;
		public Double blunt;
		public Double pierce;
		public Double weight;
	}

	// --- Penetration (share of armor an attack ignores, 0 to 1) -----------------------------------
	public double penFist = 0.0;
	public double penBlade = 0.10;
	public double penAxe = 0.35;
	public double penBlunt = 0.25;
	public double penSpear = 0.30;
	public double penPick = 0.25;
	public double penOtherTool = 0.05;
	public double penThrownTrident = 0.40;
	/** Arrows: base + speed * perSpeed, capped. A fully drawn bow shoots at about 3 blocks per tick. */
	public double penArrowBase = 0.05;
	public double penArrowPerSpeed = 0.12;
	public double penArrowMax = 0.50;

	// --- Stamina, dodge, parry --------------------------------------------------------------------
	public boolean stamina = true;
	public float staminaMax = 100F;
	public float staminaRegenPerTick = 1.0F;
	public int staminaRegenDelayTicks = 20;
	public float attackCost = 12F;
	public float dodgeCost = 25F;
	/** Stamina a raised shield pays per point of damage it stops. Out of stamina, the guard breaks. */
	public float blockCostPerDamage = 3F;
	public double tiredDamageMultiplier = 0.6;
	/** How much armor weight slows stamina recovery (weight 1.0 * 0.5 = half as fast). */
	public double regenPenaltyPerWeight = 0.5;
	public int guardBreakTicks = 60;
	public boolean dodge = true;
	public int dodgeIframeTicks = 6;
	public int dodgeCooldownTicks = 15;
	public double dodgeStrength = 0.75;
	public double dodgeLift = 0.2;
	public float parryStaminaRefund = 15F;
	/** Share of a shield's parry window, from the start, that counts as a perfect parry. */
	public double parryPerfectShare = 0.34;
	/** Raising a shield again within this many ticks of the last raise gives no parry window. */
	public int parrySpamTicks = 10;
	/** Extra damage of a riposte against a staggered foe, as a share of the blow (1.0 = double). */
	public double riposteStaggeredExtra = 2.0;

	// --- Posture (mobs) ---------------------------------------------------------------------------
	public boolean posture = true;
	public double postureHealthFactor = 0.6;
	public double postureBase = 5.0;
	public int postureRegenDelayTicks = 60;
	public double postureRegenPerTick = 0.3;
	public int staggerTicks = 40;
	public double staggerDamageMultiplier = 1.25;
	public double postureSlash = 1.0;
	public double postureBlunt = 1.5;
	public double posturePierce = 0.6;
	public double postureOther = 0.5;

	// --- Vanilla mob AI ---------------------------------------------------------------------------
	/** Melee mobs stop, flash and wait before striking a player. */
	public boolean telegraph = true;
	public int windupTicks = 8;
	/** How many mobs may swing at the same player at once; the rest wait their turn. */
	public int maxSimultaneousAttackers = 2;
	public double strikeReachBonus = 0.5;
	public boolean zombieLunge = true;
	public double lungeMinDistance = 3.5;
	public double lungeMaxDistance = 7.0;
	public int lungeWindupTicks = 12;
	public int lungeCooldownMinTicks = 100;
	public int lungeCooldownMaxTicks = 200;
	public double lungeSpeed = 0.85;
	public double lungeLift = 0.35;
	public int lungeGrabTicks = 30;
	/** Every Nth skeleton shot is a charged one (0 = never). */
	public int skeletonChargedEvery = 3;
	public int chargedExtraDrawTicks = 20;
	public double chargedArrowDamageMultiplier = 1.5;
	public double chargedArrowExtraPenetration = 0.25;
	public double creeperFeintChance = 0.35;
	public int creeperFeintAtTicks = 12;
	public int creeperFeintPauseTicks = 20;

	/** Natural attacks of unarmed mobs by entity id; anything missing hits BLUNT. */
	public Map<String, DamageKind> ataquesNaturales = defaultNaturalAttacks();

	public double postureFactor(DamageKind kind) {
		return switch (kind) {
			case SLASH -> postureSlash;
			case BLUNT -> postureBlunt;
			case PIERCE -> posturePierce;
			case OTHER -> postureOther;
		};
	}

	private static Map<String, DamageKind> defaultNaturalAttacks() {
		Map<String, DamageKind> m = new LinkedHashMap<>();
		for (String id : new String[] {"spider", "wolf", "polar_bear", "cat", "ocelot", "fox", "phantom", "vex"}) {
			m.put("minecraft:" + id, DamageKind.SLASH);
		}
		for (String id : new String[] {"cave_spider", "bee", "silverfish", "endermite", "hoglin", "zoglin"}) {
			m.put("minecraft:" + id, DamageKind.PIERCE);
		}
		return m;
	}

	public static CombatConfig get() {
		return dev.forja.ForjaConfig.get().combate;
	}
}
