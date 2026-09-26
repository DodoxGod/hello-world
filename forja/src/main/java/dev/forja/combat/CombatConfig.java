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

	// --- Charged strike, finisher, combos, counter, weapon guard ----------------------------------
	/** Keep the attack button held after a swing to charge a heavy blow; let go to strike. */
	public boolean chargedAttack = true;
	/** Ticks the button has to stay down after the swing before the charge begins. */
	public int chargeDelayTicks = 6;
	/** Ticks from the start of the charge to a full one. */
	public int chargeFullTicks = 14;
	/** A release below this share of a full charge strikes nothing and costs nothing. */
	public double chargeMinShare = 0.35;
	/** Extra damage at full charge, as a share of the blow (1.0 = double). */
	public double chargeDamageBonus = 1.0;
	/** Extra posture damage at full charge (1.5 = two and a half times). */
	public double chargePostureBonus = 1.5;
	/** Stamina a charged strike costs: this, plus chargeStaminaPerShare times the charge. */
	public float chargeStaminaCost = 20F;
	public float chargeStaminaPerShare = 15F;
	/** Blow on a staggered foe that is charged or comes from behind: a finisher, this many times as hard. */
	public double finisherMultiplier = 2.0;
	/** Hits at nearly full strength this close together chain into a combo; the third is heavier. */
	public int comboWindowTicks = 30;
	public double comboFinisherDamage = 1.3;
	public double comboFinisherPosture = 1.5;
	/** After dodging a blow that would have landed: the next hit inside this window is a counter. */
	public int counterWindowTicks = 30;
	public double counterDamage = 1.5;
	public double counterPosture = 2.0;
	public float counterStaminaRefund = 10F;
	/** Swords, the greatsword and the dagger block on right click; a raise caught in time parries. */
	public boolean weaponGuard = true;
	/** Share of a blow a weapon guard stops (a shield stops all of it). */
	public float weaponGuardBlock = 0.5F;
	/** Ticks from raising the weapon in which a blow is parried. */
	public int weaponParryTicks = 3;

	// --- Mob brains (dev.forja.ai) ----------------------------------------------------------------
	/** auto: networks where there are any (config/forja/redes), rules elsewhere; reglas: rules only; red: networks only. */
	public String iaModo = "auto";
	/** Sampling temperature of the networks: 1 as trained, lower is sharper. Also scaled by difficulty and threat. */
	public double iaTemperatura = 1.0;
	/** With several players about, monsters share themselves out between them (idea 20). */
	public boolean iaRepartirObjetivos = true;
	/** Where the network files are read from; empty: config/forja/redes. */
	public String iaCarpetaRedes = "";
	/** Mobs think only while their player target is this close. */
	public double iaAlcance = 32.0;

	// --- Difficulty (dev.forja.difficulty) -------------------------------------------------------
	/** APRENDIZ, HERRERO, MAESTRO or LEYENDA; also /forja dificultad. */
	public String dificultad = "HERRERO";
	/** Most of a mob's max health one ordinary blow can take (finishers and blows on the staggered go past). */
	public double hitCapNormal = 0.45;
	public double hitCapVeteran = 0.35;
	public double hitCapElite = 0.20;
	public double hitCapChampion = 0.12;
	public double hitCapBoss = 0.08;
	/** Elites, champions and bosses: share of a blow that reaches their health while their guard holds. */
	public double guardHealthShare = 0.5;
	/** Pressure: armor penetration each blow a player takes adds to the next ones, up to a total cap. */
	public double pressurePerHit = 0.07;
	public double pressureMax = 0.70;
	public int pressureDelayTicks = 40;
	public double pressureDrainPerTick = 0.02;
	/** Stagger resistance: each stagger in a row is shorter and raises max posture; it fades with time. */
	public double staggerRepeatDuration = 0.7;
	public int staggerMinTicks = 12;
	public double staggerRepeatPosture = 0.3;
	public int staggerResistanceFadeTicks = 200;
	/** Ticks before the same foe can take another finisher. */
	public int finisherCooldownTicks = 100;
	/** A boss can only be finished at or below this share of its health. */
	public double bossFinisherHealth = 0.5;
	/** Chance a hostile mob spawns a veteran or an elite, before the multipliers (difficulty, distance, depth, nights, gear, adaptive). */
	public double veteranChance = 0.12;
	public double eliteChance = 0.03;
	public double veteranChanceMax = 0.5;
	public double eliteChanceMax = 0.25;
	/** Gear score tiers (0 to 3): each adds this share of health and this much armor to mobs spawning near. */
	public double gearHealthPerTier = 0.2;
	public double gearArmorPerTier = 1.5;
	/** Adaptive difficulty: nudges mob damage and threat chances by how the player is doing. */
	public boolean adaptive = true;
	/** Nights: each survived night makes natural night spawns a little more likely to come in twos, up to a cap. */
	public double nightCompanionPerDoubling = 0.04;
	public double nightCompanionMax = 0.35;
	public double nightThreatPerNight = 0.03;
	public double nightThreatMax = 1.5;
	/** Rewards for beating a stronger foe: an extra loot roll, and mastery. */
	public double rewardVeteranLoot = 0.35;
	public double rewardEliteLoot = 1.0;
	/** Diminishing returns: each extra upgrade proc on the same foe in the same tick counts for less. */
	public double upgradeProcSoftness = 4.0;
	/** Chance a zombie (or kin) spawns with a shield, before the difficulty's threat multiplier. */
	public double shieldChance = 0.08;
	/** Only Forja's upgrades: vanilla enchantments on non-forged gear stop doing anything. */
	public boolean soloMejorasForja = false;
	/** Damage each mob type takes from each kind of blow (1 = normal), by entity id. */
	public Map<String, MobResistance> resistenciasMobs = defaultMobResistances();

	public static final class MobResistance {
		public double slash = 1.0;
		public double blunt = 1.0;
		public double pierce = 1.0;

		public MobResistance() {
		}

		public MobResistance(double slash, double blunt, double pierce) {
			this.slash = slash;
			this.blunt = blunt;
			this.pierce = pierce;
		}

		public double factor(DamageKind kind) {
			return switch (kind) {
				case SLASH -> slash;
				case BLUNT -> blunt;
				case PIERCE -> pierce;
				case OTHER -> 1.0;
			};
		}
	}

	private static Map<String, MobResistance> defaultMobResistances() {
		Map<String, MobResistance> m = new LinkedHashMap<>();
		m.put("forja:yunque_andante", new MobResistance(0.5, 1.15, 0.7));
		m.put("forja:automata_de_forja", new MobResistance(0.8, 1.1, 0.7));
		m.put("forja:escoria_viviente", new MobResistance(1.2, 0.5, 1.0));
		m.put("forja:herrumbre", new MobResistance(0.8, 1.25, 0.4));
		m.put("forja:coraza_vacia", new MobResistance(0.7, 1.3, 0.9));
		m.put("forja:percutor", new MobResistance(0.9, 0.8, 1.1));
		m.put("forja:guardian_de_cuno", new MobResistance(0.75, 1.1, 0.8));
		m.put("minecraft:skeleton", new MobResistance(1.0, 1.3, 0.7));
		m.put("minecraft:stray", new MobResistance(1.0, 1.3, 0.7));
		m.put("minecraft:wither_skeleton", new MobResistance(1.0, 1.25, 0.75));
		m.put("minecraft:spider", new MobResistance(1.15, 1.0, 0.9));
		m.put("minecraft:slime", new MobResistance(1.2, 0.6, 1.0));
		m.put("minecraft:magma_cube", new MobResistance(1.1, 0.6, 1.0));
		return m;
	}

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
