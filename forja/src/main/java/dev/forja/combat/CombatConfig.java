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
	/**
	 * Andy, 2026-09-27: jumping costs a little, and the weapons' special moves cost stamina as well as wear.
	 * A jump always goes (out of stamina it still jumps, and the regeneration waits); a special does not
	 * go without the stamina for it, like the dodge.
	 */
	public float jumpCost = 4F;
	public float sprintJumpCost = 8F;
	public float whirlStamina = 30F;
	public float quakeStamina = 35F;
	public float reapStamina = 30F;
	public float chargeMoveStamina = 20F;
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

	// --- Kills feed the bars (combat/KillFlow) ------------------------------------------------------
	/**
	 * Andy, 2026-09-28: "al matar recuperas un máximo de 3 % de maná por cada 5 ticks", and then "matar también
	 * debe regenerar stamina, con el mismo límite". A kill does not refill a bar at once: what it is worth waits
	 * in a pending pool (the lighter stretch on the bar) and flows in at most this share of the bar's max every
	 * {@link #killFlowEveryTicks} ticks, so a whole room cleared in a second still takes its time to arrive.
	 */
	public float killFlowShare = 0.03F;
	public int killFlowEveryTicks = 5;
	/** What one kill is worth to the stamina bar: this, plus this much per point of the victim's max health, up to a share of the bar. */
	public float killStaminaBase = 10F;
	public float killStaminaPerHealth = 0.5F;
	public float killStaminaCapShare = 0.4F;

	// --- Mana (magic/Mana) --------------------------------------------------------------------------
	/**
	 * Andy, 2026-09-28: "¿podrías hacer una barra de maná?", and for the staff and the tome, "maná y un
	 * enfriamiento corto": every spell costs mana and the wait between two is cut a lot, so you can burst until
	 * the bar is empty and then wait for it to fill again. Mana comes back "solo, con el tiempo" — slowly while
	 * you keep casting, much faster once you have left it alone for {@link #manaIdleDelayTicks} — and with kills.
	 */
	public boolean mana = true;
	public float manaMax = 100F;
	/** Per tick while spells keep coming (6 a second). */
	public float manaRegenPerTick = 0.3F;
	/** After this long without a spell, the quick regeneration takes over. */
	public int manaIdleDelayTicks = 40;
	/** Per tick once you have stopped casting (20 a second: an empty bar is whole again in about seven seconds). */
	public float manaIdleRegenPerTick = 1.0F;
	/** What a tap of the staff costs: a bolt is cheap, twelve of them in a full bar. */
	public float manaBoltCost = 8F;
	/** What a tap of the tome costs: an area and a rune, three of them in a full bar. */
	public float manaTomeCost = 30F;
	/** A full charge costs this share more than a tap, for half again the damage: holding the spell is the thrifty way. */
	public float manaChargeExtra = 0.25F;
	/** What one kill is worth to the mana bar: this, plus this much per point of the victim's max health, up to a share of the bar. */
	public float killManaBase = 8F;
	public float killManaPerHealth = 0.4F;
	public float killManaCapShare = 0.3F;

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
	/** Chance, once a minute per player at night near their forge, that a siege comes (× the difficulty's threat). */
	public double siegeChance = 0.03;
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
	/**
	 * Pressure: armor penetration each blow a player takes adds to the next ones, up to a total cap. Andy,
	 * 2026-09-29: it builds faster (0.07 -> 0.10 a blow), waits longer before draining (40 -> 60 ticks), and a
	 * blow caught on a shield or parried adds {@link #pressureBlockedShare} of it too.
	 */
	public double pressurePerHit = 0.10;
	public double pressureMax = 0.70;
	public int pressureDelayTicks = 60;
	public double pressureDrainPerTick = 0.02;
	public double pressureBlockedShare = 0.5;
	/**
	 * Gear against crowds (Andy, 2026-09-29: diamond with Protection IV made crowds harmless). By the player's
	 * gear tier (difficulty/GearScore, 0 to 3): monsters hit this much harder per tier (+45 % at tier 3, on top
	 * of the difficulty and adaptive multipliers), and this many more of them may swing at once per tier.
	 */
	public double mobDamagePerGearTier = 0.15;
	public int attackersPerGearTier = 1;
	/** More simultaneous attackers on the harder difficulties. */
	public int attackersMaestro = 1;
	public int attackersLeyenda = 2;
	/** Armor penetration of a monster's blows by its threat, combined with Pressure's (the larger counts). */
	public double penetrationVeteran = 0.10;
	public double penetrationElite = 0.20;
	public double penetrationChampion = 0.35;
	/**
	 * Surround mode, "modo rodeo" (Andy, 2026-09-29: backing away, the mobs never got the time to surround him).
	 * A monster going round to its ring slot while the player backs off runs at this multiple of its walking
	 * speed, and pays {@link #rodeoCostPerTick} of its run stamina (100) a tick for it: about 3.5 s from full.
	 */
	public double rodeoSpeed = 2.3;
	public float rodeoCostPerTick = 1.4F;
	/** Stagger resistance: each stagger in a row is shorter and raises max posture; it fades with time. */
	public double staggerRepeatDuration = 0.7;
	public int staggerMinTicks = 12;
	public double staggerRepeatPosture = 0.3;
	public int staggerResistanceFadeTicks = 200;
	/** Ticks before the same foe can take another finisher. */
	public int finisherCooldownTicks = 100;
	/** A boss can only be finished at or below this share of its health. */
	public double bossFinisherHealth = 0.5;
	/**
	 * Damage a boss takes from anything that is not a player (Andy, 2026-09-28: "si pones un warden al lado
	 * del jefe éste lo mata y ni siquiera tienes que pelear"). A warden's boom, an iron golem, another
	 * monster: this share of the blow. A player's pet or arrow counts as the player and is not touched, and
	 * neither is damage with no attacker behind it (lava, a fall, an upgrade's lightning). 1 turns it off.
	 * A third since 2026-09-29 (Andy: "que sea 1/3"); it was a half.
	 */
	public double jefeDanoAjeno = 0.333;
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
	/**
	 * Packs (Andy, 2026-09-27): "que sea difícil que aparezca un zombi solo; si hay un veterano, que aparezcan
	 * en grupos de 3 a 6". A natural hostile spawn brings companions of its kind with this chance, for a group
	 * of packMin..packMax; a veteran or an elite always does, for packVeteranMin..packVeteranMax. None past
	 * packCrowd hostiles within 32 blocks (the monster cap is the spawner's, asked once, as vanilla's packs do).
	 */
	public double packChance = 0.75;
	public int packMin = 2;
	public int packMax = 3;
	public int packVeteranMin = 3;
	public int packVeteranMax = 6;
	public int packCrowd = 12;
	/**
	 * Mixed packs (Andy, 2026-09-29: "siempre salen del mismo tipo"): each companion is of another common
	 * kind with this chance (zombie, skeleton, spider, creeper; the desert and snow ones where the leader is),
	 * never more than one creeper to a pack. 0 keeps every pack one kind.
	 */
	public double packMixChance = 0.5;
	/** A natural spawn only brings a pack if no other pack leader is this close: vanilla's own groups no longer multiply. */
	public double packLeaderSpacing = 24.0;
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
	/**
	 * A monster's reach by what it swings (Andy, 2026-09-29: "un zombie con espada debería poder atacar de más lejos
	 * que uno con puños"), on top of its body's: for starting and landing a blow and for where it stands. Weapons
	 * that already reach further by their own attributes (scythe, trident, lance, flail) keep theirs and get none
	 * of this. The networks' input yo_arma_alcance keeps the contract's number (ai.Reach.extra).
	 */
	public double mobReachFist = 0.0;
	public double mobReachDagger = 0.2;
	public double mobReachSword = 0.6;
	public double mobReachAxe = 0.5;
	public double mobReachGreatsword = 0.9;
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
	public double creeperFeintChance = 0.15;
	public int creeperFeintAtTicks = 12;
	public int creeperFeintPauseTicks = 20;
	/**
	 * Andy, 2026-09-28: "quiero que el enderman tenga un 34 % de probabilidad de esquivar por teletransportación,
	 * pero que tenga cooldown de 7 segundos si hay una teletransportación exitosa". The chance an enderman blinks
	 * away from a blow that has an attacker behind it instead of taking it (dev.forja.ai.EnderDodge); 0 turns it off.
	 */
	public double endermanDodgeChance = 0.34;
	/** Ticks an enderman cannot dodge that way after a teleport that went through (7 s). A failed one costs nothing. */
	public int endermanDodgeCooldownTicks = 140;

	// --- Mob actions for the v4 network (dev.forja.ai.MobActions) ---------------------------------
	/**
	 * Andy, 2026-09-29: monsters will pick up better weapons, drink and throw potions, eat, throw ender pearls,
	 * break lights and raise their shield when aimed at, and the network (contract v4) will decide when. The
	 * actions are built and tested but nothing calls them yet: this master switch keeps every one of them off
	 * until the v4 wires them in. The rules do not read it.
	 */
	public boolean mobActionsV4 = false;
	/** How far (blocks) a monster looks for a better weapon lying on the floor. */
	public double mobPickupRange = 6.0;
	/**
	 * Monsters never break or place blocks (Andy, 2026-09-29). The one exception planned is putting out torches and
	 * lanterns, and it comes with the v4: off until then, even with {@link #mobActionsV4} on.
	 */
	public boolean mobsBreakLights = false;

	/** Natural attacks of unarmed mobs by entity id; anything missing hits BLUNT. */
	public Map<String, DamageKind> ataquesNaturales = defaultNaturalAttacks();

	// --- Network contract v4 and honest perception (docs/red_mob_v4_diseno.md §1.6, §4.5) -------------
	/**
	 * Which network contract the mobs use (Andy, 2026-09-29): "v3" only the v1..v3.1 files of config/forja/redes;
	 * "v4" the red_mob_v4 files of config/forja/redes_v4; "auto" a v4 file where there is one for the family and the
	 * mod can feed it, v3 elsewhere. The mod cannot build the v4 observation yet (no ObsV4), so today every v4 file is
	 * found, logged once and left unused, whatever this says, and v3 keeps running.
	 */
	public String iaContrato = "auto";
	/**
	 * Honest perception (Andy, 2026-09-29): a monster that has not perceived its player for a second walks to where it
	 * last did, not to where the player really is, and waits there. Vanilla's navigation went to the real position
	 * through walls, which is cheating. It only redirects movement: it never breaks or builds anything.
	 */
	public boolean iaPercepcionHonesta = true;

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
