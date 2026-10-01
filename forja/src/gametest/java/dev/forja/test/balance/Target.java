package dev.forja.test.balance;

import java.util.EnumMap;
import java.util.Map;

import dev.forja.forge.ForgeType;
import net.minecraft.world.entity.LivingEntity;

/**
 * One mob as the balance report sees it. Every number here is read off the real entity, and every
 * "per point" factor is what one point of raw damage from that source actually took off its health
 * through the real damage pipeline (armor, the weapon's penetration and breach, the mob's resistance to
 * the kind of blow, the guard of the guarded and the bosses, the stagger bonus): see {@link Probe}.
 *
 * <p>Index 0 of a factor pair is the mob standing, index 1 the mob staggered.
 */
public final class Target {
	/** How many breach levels the melee factors are measured for: none, Brecha I..IV, Brecha IV plus Resonante. */
	public static final int BREACH_LEVELS = 6;

	public final String id;
	public final String name;
	public final boolean forja;
	public double maxHealth;
	/** The most one ordinary blow from a player takes (CombatHooks.capped), in health. */
	public double cap;
	/** The size of the posture bar with no stagger behind it (Posture.max). */
	public double postureMax;
	public boolean undead;
	public boolean poisonable = true;
	public boolean witherable = true;
	public boolean fireImmune;
	public boolean boss;
	/** Share of max health a boss must be under before a finisher lands (Bosses.finishable). */
	public double finishableBelow = 1.0;
	/** Hollow armor: plays dead once under this share of its health, for this many ticks. */
	public double feignShare;
	public int feignTicks;
	/** Why this mob's time to kill is only indicative: a damage rule of its own. Empty when it has none. */
	public String special = "";
	/** Left out of every table: a mob whose damage rules make single blows meaningless (the star core soaks them). */
	public boolean excluded;

	/** Weapon type to [standing/staggered][breach level] health lost per raw point of a blow. */
	public final Map<ForgeType, double[][]> melee = new EnumMap<>(ForgeType.class);
	/** Weapon type to posture added per raw point of a blow (resistance x kind factor x the mob's own factor). */
	public final Map<ForgeType, Double> posture = new EnumMap<>(ForgeType.class);
	/** Staff bolt and tome area, [standing, staggered], and the posture per point they add. */
	public double[] bolt = {0, 0};
	public double[] area = {0, 0};
	public double boltPosture;
	public double areaPosture;
	/** Sources with nobody behind them: lightning (Tormenta), magic (Lluvia estelar, bleeding, poison), fire, wither. */
	public double[] lightning = {0, 0};
	public double[] magic = {0, 0};
	public double[] fire = {0, 0};
	public double[] wither = {0, 0};

	/** The live entity, kept while the report is being worked out: enchantment bonuses are asked of it. */
	transient LivingEntity entity;

	public Target(String id, String name, boolean forja) {
		this.id = id;
		this.name = name;
		this.forja = forja;
	}

	public LivingEntity entity() {
		return this.entity;
	}

	/**
	 * The same mob under another difficulty: health times the preset's health (Scaling), the per-blow cap
	 * times its cap and that health (CombatHooks.capped reads max health), the bar times its posture
	 * (Posture.max). {@code from} is the difficulty it was measured under.
	 */
	public Target under(dev.forja.difficulty.ForjaDifficulty from, dev.forja.difficulty.ForjaDifficulty to) {
		Target copy = new Target(this.id, this.name, this.forja);
		double health = to.health / from.health;
		copy.maxHealth = this.maxHealth * health;
		copy.cap = this.cap * health * to.cap / from.cap;
		copy.postureMax = this.postureMax * to.posture / from.posture;
		copy.undead = this.undead;
		copy.poisonable = this.poisonable;
		copy.witherable = this.witherable;
		copy.fireImmune = this.fireImmune;
		copy.boss = this.boss;
		copy.finishableBelow = this.finishableBelow;
		copy.feignShare = this.feignShare;
		copy.feignTicks = this.feignTicks;
		copy.special = this.special;
		copy.excluded = this.excluded;
		copy.melee.putAll(this.melee);
		copy.posture.putAll(this.posture);
		copy.bolt = this.bolt;
		copy.area = this.area;
		copy.boltPosture = this.boltPosture;
		copy.areaPosture = this.areaPosture;
		copy.lightning = this.lightning;
		copy.magic = this.magic;
		copy.fire = this.fire;
		copy.wither = this.wither;
		copy.entity = this.entity;
		return copy;
	}

	/** Whether any blow can hurt it at all as it stands (a sealed guardian or a core soaking hits cannot). */
	public boolean hurtable() {
		if (this.excluded) {
			return false;
		}
		for (double[][] factors : this.melee.values()) {
			if (factors[0][0] > 1.0E-6) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A target with no armor, no resistance, no cap and endless health, with the posture bar of a 20-health
	 * mob: what the weapon itself puts out, for the DPS columns.
	 */
	public static Target neutral(double postureMax, Map<ForgeType, Double> postureByType) {
		Target dummy = new Target("maniqui", "Maniquí neutro", false);
		dummy.maxHealth = 1.0E9;
		dummy.cap = Double.MAX_VALUE;
		dummy.postureMax = postureMax;
		dummy.poisonable = false;
		dummy.witherable = false;
		dummy.fireImmune = true;
		for (ForgeType type : postureByType.keySet()) {
			double[][] factors = new double[2][BREACH_LEVELS];
			for (int breach = 0; breach < BREACH_LEVELS; breach++) {
				factors[0][breach] = 1.0;
				factors[1][breach] = dev.forja.combat.CombatConfig.get().staggerDamageMultiplier;
			}
			dummy.melee.put(type, factors);
			dummy.posture.put(type, postureByType.get(type));
		}
		double stagger = dev.forja.combat.CombatConfig.get().staggerDamageMultiplier;
		dummy.bolt = new double[] {1.0, stagger};
		dummy.area = new double[] {1.0, stagger};
		dummy.boltPosture = dev.forja.combat.CombatConfig.get().postureBlunt;
		dummy.areaPosture = dev.forja.combat.CombatConfig.get().postureOther;
		dummy.lightning = new double[] {1.0, stagger};
		dummy.magic = new double[] {1.0, stagger};
		dummy.fire = new double[] {0.0, 0.0};
		dummy.wither = new double[] {0.0, 0.0};
		return dummy;
	}
}
