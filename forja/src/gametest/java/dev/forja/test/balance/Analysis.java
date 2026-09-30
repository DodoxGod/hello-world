package dev.forja.test.balance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.forja.combat.CombatConfig;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Potential;
import dev.forja.material.ForgeMaterial;
import dev.forja.upgrade.Synergy;
import dev.forja.upgrade.Upgrade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;

/**
 * The whole search: materials pruned and folded ({@link Materials}), each survivor fought against a
 * spread of mobs, upgrades chosen for the best of them under the load budget by an exact dynamic
 * program over the load, and that choice checked against every legal set of upgrades fought for real.
 *
 * <p>The yardstick of a build is the geometric mean, over a spread of mobs, of the seconds it takes to
 * kill each one: a mean of ratios, so a slow fight against a tank does not drown a quick one against a
 * zombie.
 */
public final class Analysis {
	public static final List<ForgeType> TYPES = List.of(
		ForgeType.ESPADA, ForgeType.DAGA, ForgeType.ESPADON, ForgeType.HACHA, ForgeType.LANZA, ForgeType.MAZO,
		ForgeType.TRIDENTE, ForgeType.MANGUAL, ForgeType.GUANTELETES, ForgeType.GUADANA, ForgeType.BACULO, ForgeType.GRIMORIO);

	/** Upgrades that change what a blow or a spell does to a single foe. Everything else is area, utility or defense. */
	public static final List<Upgrade> DAMAGE_UPGRADES = List.of(
		Upgrade.FILO, Upgrade.CASTIGO, Upgrade.PERDICION_DE_ARTROPODOS, Upgrade.BRECHA, Upgrade.DENSIDAD,
		Upgrade.ASPECTO_IGNEO, Upgrade.CRITICO, Upgrade.FRENESI, Upgrade.TORMENTA, Upgrade.EJECUCION,
		Upgrade.MATAGIGANTES, Upgrade.VENENO, Upgrade.DESGARRO, Upgrade.NUDILLOS_DE_HIERRO, Upgrade.RAFAGA,
		Upgrade.CONJURO_VELOZ, Upgrade.SOBRECARGA, Upgrade.RESONANCIA);

	/**
	 * The spread every build is judged on while searching: two undead, an arthropod, two big ones and three
	 * armored ones of Forja's. The hollow armor is left out of the search on purpose: its one-off feint
	 * (three seconds of playing dead) turns a tenth of a second either side of a threshold into a three
	 * second difference and would drown every other mob; it is in every table all the same.
	 */
	public static final List<String> SEARCH_TARGETS = List.of(
		"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:enderman", "minecraft:piglin_brute",
		"forja:yunque_andante", "forja:percutor", "forja:automata_de_forja");

	/** How far the upgrades go: none, a piece of potential 50 and a perfect one with flux. */
	public enum Scenario {
		BASE("0 %", 0),
		MEDIO("50 %", 50),
		MAXIMO("100 %", 100);

		public final String label;
		public final int potential;

		Scenario(String label, int potential) {
			this.label = label;
			this.potential = potential;
		}
	}

	/** Two runs to choose a rhythm, more to measure it; a two minute limit to any fight. */
	public static final int SEARCH_RUNS = 4;
	public static final int FINAL_RUNS = 24;
	public static final int MAX_TICKS = 2400;

	public final ServerLevel level;
	public final DamageSource source;
	public final List<Target> targets;
	public final Target dummy;
	public final List<Target> search = new ArrayList<>();
	public final Map<ForgeType, TypeReport> reports = new EnumMap<>(ForgeType.class);
	public final Fight.Options options = new Fight.Options();
	private final java.util.concurrent.atomic.LongAdder fights = new java.util.concurrent.atomic.LongAdder();
	/**
	 * Fights run on a pool of their own once every enchantment bonus they need has been asked on the server
	 * thread (Build.prepare): a fight is plain arithmetic on numbers already measured.
	 */
	private static final java.util.concurrent.ForkJoinPool POOL =
		new java.util.concurrent.ForkJoinPool(Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 1)));

	/** How many fights were played, all told. */
	public long evaluations() {
		return this.fights.sum();
	}

	/** Maps a list in parallel on the pool, keeping its order. */
	private <T, R> List<R> parallel(List<T> items, java.util.function.Function<T, R> work) {
		return POOL.submit(() -> items.parallelStream().map(work).toList()).join();
	}

	/** Every build asks its enchantments about every mob here, on the server thread; false if one must stay here. */
	private boolean prepare(List<Build> builds) {
		boolean all = true;
		for (Build build : builds) {
			all &= build.prepare(this.targets, this.level, this.source);
		}
		return all;
	}

	/** The mean time to kill of every build, in parallel when they allow it. */
	private List<Double> geos(List<Build> builds, List<Target> list, int runs, Fight.Options options) {
		if (this.prepare(builds)) {
			return this.parallel(builds, build -> this.geo(build, list, runs, options));
		}
		List<Double> result = new ArrayList<>();
		for (Build build : builds) {
			result.add(this.geo(build, list, runs, options));
		}
		return result;
	}

	/** One build, fought: its time to kill each mob with the rhythm that suits it best, and its DPS. */
	public static final class Evaluated {
		public Build build;
		public double geo;
		public final Map<String, Fight.Result> ttk = new LinkedHashMap<>();
		public final Map<String, Fight.Policy> policy = new LinkedHashMap<>();
		public Fight.Result burst;
		public Fight.Result sustained;
		public Fight.Policy burstPolicy;
		public Fight.Policy sustainedPolicy;
	}

	/** What the dynamic program chose, what fighting every legal set found, and how far apart they are. */
	public static final class Knapsack {
		public int capacity;
		public int potential;
		public List<Upgrade> candidates = new ArrayList<>();
		public Map<Upgrade, Double> single = new LinkedHashMap<>();
		public Map<Synergy, Double> pairBonus = new LinkedHashMap<>();
		public List<Upgrade> dpPick = new ArrayList<>();
		public List<Upgrade> enumPick = new ArrayList<>();
		public double baseGeo;
		public double dpGeo;
		public double enumGeo;
		public int subsets;
	}

	public static final class TypeReport {
		public ForgeType type;
		public List<Evaluated> ranking = new ArrayList<>();
		public final Map<Scenario, Evaluated> best = new EnumMap<>(Scenario.class);
		public final Map<Scenario, Evaluated> bestWithPacts = new EnumMap<>(Scenario.class);
		public final Map<Scenario, Knapsack> knapsack = new EnumMap<>(Scenario.class);
		public final Map<Scenario, Knapsack> knapsackPacts = new EnumMap<>(Scenario.class);
		public int combinations;
		/** Distinct in a fight, before the whole-weapon front. */
		public int distinct;
	}

	public Analysis(ServerLevel level, DamageSource source, List<Target> targets) {
		this.level = level;
		this.source = source;
		this.targets = targets;
		Map<ForgeType, Double> posture = new EnumMap<>(ForgeType.class);
		for (ForgeType type : TYPES) {
			var kind = type == ForgeType.MAZO || type == ForgeType.MANGUAL || type == ForgeType.GUANTELETES || type == ForgeType.BACULO
				? dev.forja.combat.DamageKind.BLUNT
				: type == ForgeType.LANZA || type == ForgeType.TRIDENTE ? dev.forja.combat.DamageKind.PIERCE
				: type == ForgeType.GRIMORIO ? dev.forja.combat.DamageKind.BLUNT : dev.forja.combat.DamageKind.SLASH;
			posture.put(type, CombatConfig.get().postureFactor(kind));
		}
		CombatConfig cfg = CombatConfig.get();
		this.dummy = Target.neutral(20.0 * cfg.postureHealthFactor + cfg.postureBase, posture);
		for (String id : SEARCH_TARGETS) {
			for (Target target : targets) {
				if (target.id.equals(id)) {
					this.search.add(target);
				}
			}
		}
	}

	public Target target(String id) {
		for (Target target : this.targets) {
			if (target.id.equals(id)) {
				return target;
			}
		}
		return null;
	}

	/**
	 * The fight as a Mago fights it (clase/PlayerClass.MAGO): the class's base numbers on the spells and the bar,
	 * and with {@code talents} every talent of its tree that moves them (Núcleo afinado, Catalizador, Mente clara,
	 * Canalización, Economía arcana), added up the way ClassEffects.stat adds them.
	 */
	public static Fight.Options mago(Fight.Options base, boolean talents) {
		java.util.Map<dev.forja.clase.ClassStat, Double> sum = new java.util.EnumMap<>(dev.forja.clase.ClassStat.class);
		for (dev.forja.clase.ClassStat.Mod mod : dev.forja.clase.PlayerClass.MAGO.base) {
			sum.merge(mod.stat(), (double) mod.value(), Double::sum);
		}
		if (talents) {
			for (dev.forja.clase.Talent talent : dev.forja.clase.PlayerClass.MAGO.talents()) {
				for (dev.forja.clase.ClassStat.Mod mod : talent.mods) {
					sum.merge(mod.stat(), (double) mod.value(), Double::sum);
				}
			}
		}
		Fight.Options options = base.copy();
		options.spellDamage = Math.max(0.1, 1.0 + sum.getOrDefault(dev.forja.clase.ClassStat.SPELL_DAMAGE, 0.0));
		options.spellCooldown = Math.max(0.1, 1.0 + sum.getOrDefault(dev.forja.clase.ClassStat.SPELL_COOLDOWN, 0.0));
		options.spellCost = Math.max(0.0, 1.0 + sum.getOrDefault(dev.forja.clase.ClassStat.SPELL_COST, 0.0));
		options.manaRegen = 1.0 + sum.getOrDefault(dev.forja.clase.ClassStat.MANA_REGEN, 0.0);
		options.manaMax = 1.0 + sum.getOrDefault(dev.forja.clase.ClassStat.MANA_MAX, 0.0);
		return options;
	}

	public static int percent(Upgrade upgrade, int potential) {
		if (Potential.exempt(upgrade) || Potential.allOrNothing(upgrade)) {
			return 100;
		}
		return Math.max(1, Math.min(Potential.MOST, potential));
	}

	public static List<Upgrade> candidates(ForgeType type) {
		List<Upgrade> list = new ArrayList<>();
		for (Upgrade upgrade : DAMAGE_UPGRADES) {
			if (upgrade.appliesTo(type) && Potential.weight(upgrade) > 0) {
				list.add(upgrade);
			}
		}
		return list;
	}

	public Build build(ForgeType type, List<ForgeMaterial> materials, Map<Upgrade, Integer> upgrades) {
		return new Build(type, materials, upgrades, this.level.registryAccess());
	}

	// ---------------------------------------------------------------- fighting

	/** The best rhythm against one mob and what it comes to, chosen on few runs and measured on more. */
	public Fight.Result ttk(Build build, Target target, int chooseRuns, int measureRuns, Fight.Policy[] chosen, Fight.Options options) {
		Fight fight = new Fight(build, target, options, this.level, this.source);
		Fight.Policy bestPolicy = null;
		Fight.Result best = null;
		for (Fight.Policy policy : Fight.policies(build)) {
			Fight.Result result = fight.run(policy, chooseRuns, MAX_TICKS, true);
			this.fights.increment();
			if (best == null || result.ticks < best.ticks - 1.0E-9) {
				best = result;
				bestPolicy = policy;
			}
		}
		if (measureRuns > chooseRuns) {
			best = fight.run(bestPolicy, measureRuns, MAX_TICKS, true);
		}
		if (chosen != null) {
			chosen[0] = bestPolicy;
		}
		return best;
	}

	/** Damage a second against the neutral dummy over {@code ticks}, with the rhythm that does most. */
	public Fight.Result dps(Build build, int ticks, int runs, Fight.Policy[] chosen, Fight.Options options) {
		Fight fight = new Fight(build, this.dummy, options, this.level, this.source);
		Fight.Policy bestPolicy = null;
		Fight.Result best = null;
		for (Fight.Policy policy : Fight.policies(build)) {
			Fight.Result result = fight.run(policy, runs, ticks, false);
			this.fights.increment();
			if (best == null || result.damage > best.damage + 1.0E-9) {
				best = result;
				bestPolicy = policy;
			}
		}
		if (chosen != null) {
			chosen[0] = bestPolicy;
		}
		return best;
	}

	/** Geometric mean of the seconds to kill every mob in the list that can be hurt at all. */
	public double geo(Build build, List<Target> list, int runs, Fight.Options options) {
		double logs = 0.0;
		int count = 0;
		for (Target target : list) {
			if (!target.hurtable()) {
				continue;
			}
			Fight.Result result = this.ttk(build, target, Math.min(runs, SEARCH_RUNS), runs, null, options);
			logs += Math.log(Math.max(0.05, result.seconds()));
			count++;
		}
		return count == 0 ? Double.NaN : Math.exp(logs / count);
	}

	/** A build fought to the full: every mob, the burst, the long fight. */
	public Evaluated evaluate(Build build, List<Target> list, int runs) {
		Evaluated evaluated = new Evaluated();
		evaluated.build = build;
		double logs = 0.0;
		int count = 0;
		boolean threads = build.prepare(this.targets, this.level, this.source);
		java.util.function.Function<Target, Object[]> fight = target -> {
			Fight.Policy[] chosen = new Fight.Policy[1];
			Fight.Result result = target.hurtable() ? this.ttk(build, target, SEARCH_RUNS, runs, chosen, this.options) : null;
			return new Object[] {result, chosen[0]};
		};
		List<Object[]> fought = threads ? this.parallel(list, fight) : list.stream().map(fight).toList();
		for (int i = 0; i < list.size(); i++) {
			Target target = list.get(i);
			Fight.Result result = (Fight.Result) fought.get(i)[0];
			if (result != null) {
				evaluated.ttk.put(target.id, result);
				evaluated.policy.put(target.id, (Fight.Policy) fought.get(i)[1]);
				if (this.search.contains(target)) {
					logs += Math.log(Math.max(0.05, result.seconds()));
					count++;
				}
			}
		}
		evaluated.geo = count == 0 ? Double.NaN : Math.exp(logs / count);
		Fight.Policy[] chosen = new Fight.Policy[1];
		evaluated.burst = this.dps(build, 60, runs, chosen, this.options);
		evaluated.burstPolicy = chosen[0];
		evaluated.sustained = this.dps(build, 1200, Math.max(2, runs / 2), chosen, this.options);
		evaluated.sustainedPolicy = chosen[0];
		return evaluated;
	}

	// ---------------------------------------------------------------- the search

	/** Seconds spent in each part of the search, for the log. */
	public final Map<String, Double> timings = new LinkedHashMap<>();

	private void time(String phase, long started) {
		this.timings.merge(phase, (System.nanoTime() - started) / 1.0E9, Double::sum);
	}

	public void runAll() {
		for (ForgeType type : TYPES) {
			this.reports.put(type, this.searchType(type));
		}
	}

	private TypeReport searchType(ForgeType type) {
		TypeReport report = new TypeReport();
		report.type = type;
		List<List<ForgeMaterial>> combinations = Materials.combinations(type);
		report.combinations = combinations.size();
		// Folded again on what the real stacks carry: a Diafano part adds speed the sheet does not show.
		Map<String, Build> unique = new LinkedHashMap<>();
		for (List<ForgeMaterial> materials : combinations) {
			Build build = this.build(type, materials, Map.of());
			String key = String.format(java.util.Locale.ROOT, "%.3f|%.3f|%.3f|%d|%d|%b|%b",
				build.attackDamage, build.attackSpeed, build.spellDamage, build.fireAspect, build.breach,
				build.hasTrait(ForgeMaterial.Trait.AFILADO), build.hasTrait(ForgeMaterial.Trait.ACUATICO));
			Build known = unique.get(key);
			if (known == null || build.durability > known.durability) {
				unique.put(key, build);
			}
		}
		// And pruned again, now whole: of two builds with the same combat traits, one no faster and no harder
		// hitting than the other can never kill sooner.
		List<Build> front = new ArrayList<>();
		for (Build build : unique.values()) {
			boolean beaten = false;
			for (Build other : unique.values()) {
				if (other != build && sameTraits(other, build) && other.attackDamage >= build.attackDamage - 1.0E-9
					&& other.attackSpeed >= build.attackSpeed - 1.0E-9 && other.spellDamage >= build.spellDamage - 1.0E-9
					&& other.spellCooldown <= build.spellCooldown
					&& (other.attackDamage > build.attackDamage + 1.0E-9 || other.attackSpeed > build.attackSpeed + 1.0E-9
					|| other.spellDamage > build.spellDamage + 1.0E-9 || other.spellCooldown < build.spellCooldown)) {
					beaten = true;
					break;
				}
			}
			if (!beaten) {
				front.add(build);
			}
		}
		report.distinct = unique.size();
		long started = System.nanoTime();
		List<Double> geos = this.geos(front, this.search, SEARCH_RUNS, this.options);
		List<Evaluated> ranking = new ArrayList<>();
		for (int i = 0; i < front.size(); i++) {
			Evaluated evaluated = new Evaluated();
			evaluated.build = front.get(i);
			evaluated.geo = geos.get(i);
			ranking.add(evaluated);
		}
		ranking.sort(Comparator.comparingDouble(e -> e.geo));
		report.ranking = ranking;
		List<ForgeMaterial> bestMaterials = ranking.getFirst().build.materials;
		this.time("materiales", started);

		started = System.nanoTime();
		report.best.put(Scenario.BASE, this.evaluate(ranking.getFirst().build, this.targets, FINAL_RUNS));
		this.time("evaluación final", started);
		for (Scenario scenario : List.of(Scenario.MEDIO, Scenario.MAXIMO)) {
			for (boolean pacts : new boolean[] {false, true}) {
				started = System.nanoTime();
				Knapsack knapsack = this.knapsack(type, bestMaterials, scenario, pacts);
				this.time("mejoras", started);
				started = System.nanoTime();
				// The materials were ranked bare; the five best are fought again with the chosen upgrades.
				Map<Upgrade, Integer> chosen = this.upgradeMap(knapsack.enumPick, knapsack.potential, pacts);
				Build winner = null;
				double winnerGeo = Double.MAX_VALUE;
				for (int i = 0; i < Math.min(5, ranking.size()); i++) {
					Build candidate = this.build(type, ranking.get(i).build.materials, chosen);
					double geo = this.geo(candidate, this.search, SEARCH_RUNS, this.options);
					if (geo < winnerGeo) {
						winnerGeo = geo;
						winner = candidate;
					}
				}
				Evaluated evaluated = this.evaluate(winner, this.targets, FINAL_RUNS);
				(pacts ? report.bestWithPacts : report.best).put(scenario, evaluated);
				this.time("evaluación final", started);
				(pacts ? report.knapsackPacts : report.knapsack).put(scenario, knapsack);
			}
		}
		return report;
	}

	/** Same fire, breach, edge and water trait: the only things besides damage and speed that change a fight here. */
	private static boolean sameTraits(Build a, Build b) {
		return a.fireAspect == b.fireAspect && a.breach == b.breach
			&& a.hasTrait(ForgeMaterial.Trait.AFILADO) == b.hasTrait(ForgeMaterial.Trait.AFILADO)
			&& a.hasTrait(ForgeMaterial.Trait.ACUATICO) == b.hasTrait(ForgeMaterial.Trait.ACUATICO);
	}

	/** The upgrades at the percentage a piece of this potential takes them to, with both pacts on top if asked. */
	public Map<Upgrade, Integer> upgradeMap(List<Upgrade> upgrades, int potential, boolean pacts) {
		Map<Upgrade, Integer> map = new EnumMap<>(Upgrade.class);
		for (Upgrade upgrade : upgrades) {
			map.put(upgrade, percent(upgrade, potential));
		}
		if (pacts) {
			map.put(Upgrade.PACTO_DE_SED, 100);
			map.put(Upgrade.PACTO_DE_VIDRIO, 100);
		}
		return map;
	}

	/** The potential a piece of this scenario really has: each pact on it is ten more (Potential.PER_PACT). */
	public static int potential(Scenario scenario, boolean pacts) {
		return Math.min(Potential.MOST, scenario.potential + (pacts ? 2 * Potential.PER_PACT : 0));
	}

	/**
	 * Upgrades under the load budget. Each upgrade alone, and each synergy pair, is fought; the log of how
	 * much it cuts the mean time to kill is its value. The dynamic program is exact for those values over
	 * the load (a multiple-choice knapsack: one of an exclusive group, and a synergy as a pair that may
	 * be taken whole). The values do not add up exactly in a real fight, so every legal set is then fought
	 * as well and the best of those is the answer; the report says how far the program's pick was from it.
	 */
	public Knapsack knapsack(ForgeType type, List<ForgeMaterial> materials, Scenario scenario, boolean pacts) {
		Knapsack knapsack = new Knapsack();
		knapsack.potential = potential(scenario, pacts);
		knapsack.capacity = Potential.capacity(knapsack.potential);
		knapsack.candidates = candidates(type);
		Build base = this.build(type, materials, this.upgradeMap(List.of(), knapsack.potential, pacts));
		knapsack.baseGeo = this.geo(base, this.search, SEARCH_RUNS, this.options);
		Map<Upgrade, Double> value = new LinkedHashMap<>();
		List<Build> singles = new ArrayList<>();
		for (Upgrade upgrade : knapsack.candidates) {
			singles.add(this.build(type, materials, this.upgradeMap(List.of(upgrade), knapsack.potential, pacts)));
		}
		List<Double> singleGeos = this.geos(singles, this.search, SEARCH_RUNS, this.options);
		for (int i = 0; i < knapsack.candidates.size(); i++) {
			value.put(knapsack.candidates.get(i), Math.log(knapsack.baseGeo / singleGeos.get(i)));
		}
		knapsack.single = value;
		List<List<List<Upgrade>>> clusters = new ArrayList<>();
		List<Upgrade> placed = new ArrayList<>();
		for (Synergy synergy : Synergy.values()) {
			if (knapsack.candidates.contains(synergy.first) && knapsack.candidates.contains(synergy.second)
				&& !placed.contains(synergy.first) && !placed.contains(synergy.second)
				&& synergy.first.group == Upgrade.Group.NONE && synergy.second.group == Upgrade.Group.NONE) {
				List<Upgrade> pair = List.of(synergy.first, synergy.second);
				double geo = this.geo(this.build(type, materials, this.upgradeMap(pair, knapsack.potential, pacts)), this.search, SEARCH_RUNS, this.options);
				knapsack.pairBonus.put(synergy, Math.log(knapsack.baseGeo / geo) - value.get(synergy.first) - value.get(synergy.second));
				clusters.add(List.of(List.of(synergy.first), List.of(synergy.second), pair));
				placed.addAll(pair);
			}
		}
		Map<Upgrade.Group, List<List<Upgrade>>> groups = new EnumMap<>(Upgrade.Group.class);
		for (Upgrade upgrade : knapsack.candidates) {
			if (placed.contains(upgrade)) {
				continue;
			}
			if (upgrade.group != Upgrade.Group.NONE) {
				groups.computeIfAbsent(upgrade.group, g -> new ArrayList<>()).add(List.of(upgrade));
			} else {
				clusters.add(List.of(List.of(upgrade)));
			}
		}
		clusters.addAll(groups.values());
		knapsack.dpPick = dynamicProgram(clusters, value, knapsack.pairBonus, knapsack.capacity);

		// Every legal set, fought.
		List<List<Upgrade>> sets = new ArrayList<>();
		enumerate(knapsack.candidates, 0, new ArrayList<>(), 0, knapsack.capacity, sets);
		knapsack.subsets = sets.size();
		Map<String, Double> seen = new HashMap<>();
		double bestGeo = Double.MAX_VALUE;
		List<Upgrade> bestSet = List.of();
		List<Build> setBuilds = new ArrayList<>();
		for (List<Upgrade> set : sets) {
			setBuilds.add(this.build(type, materials, this.upgradeMap(set, knapsack.potential, pacts)));
		}
		List<Double> setGeos = this.geos(setBuilds, this.search, SEARCH_RUNS, this.options);
		for (int i = 0; i < sets.size(); i++) {
			List<Upgrade> set = sets.get(i);
			double geo = setGeos.get(i);
			seen.put(set.toString(), geo);
			if (geo < bestGeo - 1.0E-9 || (Math.abs(geo - bestGeo) <= 1.0E-9 && load(set) < load(bestSet))) {
				bestGeo = geo;
				bestSet = set;
			}
		}
		knapsack.enumPick = new ArrayList<>(bestSet);
		knapsack.enumGeo = bestGeo;
		List<Upgrade> dpSorted = new ArrayList<>(knapsack.dpPick);
		dpSorted.sort(Comparator.naturalOrder());
		Double dpGeo = seen.get(dpSorted.toString());
		knapsack.dpGeo = dpGeo != null ? dpGeo
			: this.geo(this.build(type, materials, this.upgradeMap(dpSorted, knapsack.potential, pacts)), this.search, SEARCH_RUNS, this.options);
		return knapsack;
	}

	static int load(List<Upgrade> set) {
		int total = 0;
		for (Upgrade upgrade : set) {
			total += Potential.weight(upgrade);
		}
		return total;
	}

	private static void enumerate(List<Upgrade> candidates, int index, List<Upgrade> current, int load, int capacity, List<List<Upgrade>> out) {
		if (index == candidates.size()) {
			List<Upgrade> set = new ArrayList<>(current);
			set.sort(Comparator.naturalOrder());
			out.add(set);
			return;
		}
		enumerate(candidates, index + 1, current, load, capacity, out);
		Upgrade next = candidates.get(index);
		int weight = Potential.weight(next);
		if (load + weight > capacity) {
			return;
		}
		for (Upgrade taken : current) {
			if (!taken.isCompatibleWith(next)) {
				return;
			}
		}
		current.add(next);
		enumerate(candidates, index + 1, current, load + weight, capacity, out);
		current.removeLast();
	}

	/** Multiple-choice knapsack over the load: from each cluster at most one option. Exact for additive values. */
	static List<Upgrade> dynamicProgram(List<List<List<Upgrade>>> clusters, Map<Upgrade, Double> value, Map<Synergy, Double> pairs, int capacity) {
		int n = clusters.size();
		double[][] best = new double[n + 1][capacity + 1];
		int[][] choice = new int[n + 1][capacity + 1];
		for (int i = 1; i <= n; i++) {
			List<List<Upgrade>> options = clusters.get(i - 1);
			for (int c = 0; c <= capacity; c++) {
				best[i][c] = best[i - 1][c];
				choice[i][c] = -1;
				for (int o = 0; o < options.size(); o++) {
					List<Upgrade> option = options.get(o);
					int weight = load(option);
					if (weight > c) {
						continue;
					}
					double worth = 0.0;
					for (Upgrade upgrade : option) {
						worth += value.getOrDefault(upgrade, 0.0);
					}
					if (option.size() == 2) {
						for (Map.Entry<Synergy, Double> pair : pairs.entrySet()) {
							if (option.contains(pair.getKey().first) && option.contains(pair.getKey().second)) {
								worth += pair.getValue();
							}
						}
					}
					if (best[i - 1][c - weight] + worth > best[i][c] + 1.0E-12) {
						best[i][c] = best[i - 1][c - weight] + worth;
						choice[i][c] = o;
					}
				}
			}
		}
		List<Upgrade> picked = new ArrayList<>();
		int c = capacity;
		for (int i = n; i >= 1; i--) {
			int o = choice[i][c];
			if (o >= 0) {
				List<Upgrade> option = clusters.get(i - 1).get(o);
				picked.addAll(option);
				c -= load(option);
			}
		}
		picked.sort(Comparator.naturalOrder());
		return picked;
	}
}
