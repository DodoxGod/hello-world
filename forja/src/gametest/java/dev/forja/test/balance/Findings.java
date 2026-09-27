package dev.forja.test.balance;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.forja.difficulty.ForjaDifficulty;
import dev.forja.forge.ForgeStats;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.upgrade.Upgrade;

/**
 * The suspicions and the new findings, each measured by turning one rule off, or one thing on, and
 * fighting again. Everything here is a number the report prints; the verdicts are worked out from them.
 */
public final class Findings {
	private final Analysis analysis;

	/** Per weapon type, on its best build at 100 %. */
	public final Map<ForgeType, Double> staminaRatio = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> tiredShare = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Fight.Policy> sustainedPolicy = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> cappedShare = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> zombieSwings = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> zombieSeconds = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, double[]> pactGain = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> frenesiGain = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, double[]> frenesiSpeed = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Map<Upgrade, Double>> perWeight = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> frenzyRatio = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> eventGain = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> headshotRatio = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Boolean> charges = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> swallowedShare = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> iframesRatio = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> afiladoRatio = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> vidriaceroRatio = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, double[]> mestizaje = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, double[]> difficulty = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, Double> allMobsGeo = new EnumMap<>(ForgeType.class);
	/**
	 * The cross-check of every fight of two seconds or more at 100 %: the simulated time to kill over the
	 * plain estimate, max health over the sustained damage a second against that mob. [median, lowest,
	 * highest, fights], and the fight furthest from 1.
	 */
	public final Map<ForgeType, double[]> crossCheck = new EnumMap<>(ForgeType.class);
	public final Map<ForgeType, String> crossWorst = new EnumMap<>(ForgeType.class);
	/** Types dominated by another in every scenario and against every mob: dominated type to what dominates it. */
	public final Map<ForgeType, ForgeType> dominated = new EnumMap<>(ForgeType.class);
	/** Clear faults (plain materials, parts with two lines or more) and the whole list for weapon parts. */
	public List<Materials.Inversion> inversions = new ArrayList<>();
	public List<Materials.Inversion> weaponInversions = new ArrayList<>();
	/** Materials beaten in every weapon slot they fit, by another of the same trait. */
	public final List<ForgeMaterial> uselessForWeapons = new ArrayList<>();
	/** Share of the damage in the best 100 % fights that came from the upgrades' extras, per type. */
	public final Map<ForgeType, Double> extraShare = new EnumMap<>(ForgeType.class);
	/** A plain mace (default materials): swings a second without Frenesí and with it at 100 %. */
	public double[] plainMace;
	/** Runs for every comparison below, on both sides of it. */
	static final int COMPARE_RUNS = 24;
	public final Map<PartType, Materials.Front> fronts = new LinkedHashMap<>();
	public final List<ForgeMaterial> neverTop = new ArrayList<>();

	public Findings(Analysis analysis) {
		this.analysis = analysis;
	}

	/** Mean time to kill the sample mobs, measured the same way on both sides of every comparison. */
	private double geo(Build build, Fight.Options options) {
		return this.analysis.geo(build, this.analysis.search, COMPARE_RUNS, options);
	}

	private Analysis.Evaluated best(ForgeType type) {
		return this.analysis.reports.get(type).best.get(Analysis.Scenario.MAXIMO);
	}

	public void measure() {
		Fight.Options plain = this.analysis.options;
		for (ForgeType type : Analysis.TYPES) {
			Analysis.TypeReport report = this.analysis.reports.get(type);
			Analysis.Evaluated best = this.best(type);
			Build build = best.build;
			boolean magic = dev.forja.magic.Spellcasting.casts(type);
			this.charges.put(type, build.charges);

			// 1. Stamina: the long fight with it and without it.
			Fight.Options noStamina = plain.copy();
			noStamina.stamina = false;
			Fight.Result free = this.analysis.dps(build, 1200, 4, null, noStamina);
			this.staminaRatio.put(type, best.sustained.damage / Math.max(1.0E-9, free.damage));
			this.tiredShare.put(type, best.sustained.swings <= 0 ? 0.0 : best.sustained.tired / best.sustained.swings);
			this.sustainedPolicy.put(type, best.sustainedPolicy);

			// 2. The cap: how many blows against the vanilla mobs were cut down to it, and the zombie.
			double capped = 0.0;
			double swings = 0.0;
			for (Target target : this.analysis.targets) {
				Fight.Result result = best.ttk.get(target.id);
				if (!target.forja && result != null) {
					capped += result.capped;
					swings += result.swings;
				}
			}
			this.cappedShare.put(type, swings <= 0 ? 0.0 : capped / swings);
			Fight.Result zombie = best.ttk.get("minecraft:zombie");
			if (zombie != null) {
				this.zombieSwings.put(type, zombie.swings);
				this.zombieSeconds.put(type, zombie.seconds());
			}

			// 3. Pacts: the best with them over the best without, at 50 % and at 100 %.
			double[] pacts = new double[2];
			int index = 0;
			for (Analysis.Scenario scenario : List.of(Analysis.Scenario.MEDIO, Analysis.Scenario.MAXIMO)) {
				pacts[index++] = report.bestWithPacts.get(scenario).geo / report.best.get(scenario).geo;
			}
			this.pactGain.put(type, pacts);

			// 4 and 5. What each damage upgrade is worth alone at 100 %, and per point of load.
			Analysis.Knapsack knapsack = report.knapsack.get(Analysis.Scenario.MAXIMO);
			Map<Upgrade, Double> worth = new LinkedHashMap<>();
			for (Map.Entry<Upgrade, Double> entry : knapsack.single.entrySet()) {
				worth.put(entry.getKey(), (1.0 - Math.exp(-entry.getValue())) / dev.forja.forge.Potential.weight(entry.getKey()));
			}
			this.perWeight.put(type, worth);
			if (knapsack.single.containsKey(Upgrade.FRENESI)) {
				this.frenesiGain.put(type, 1.0 - Math.exp(-knapsack.single.get(Upgrade.FRENESI)));
				Build bare = this.analysis.build(type, report.ranking.getFirst().build.materials, Map.of());
				Build fast = this.analysis.build(type, report.ranking.getFirst().build.materials, Map.of(Upgrade.FRENESI, 100));
				this.frenesiSpeed.put(type, new double[] {bare.attackSpeed, fast.attackSpeed});
			}
			double reference = this.geo(build, plain);
			Fight.Options noFrenzy = plain.copy();
			noFrenzy.frenzy = false;
			this.frenzyRatio.put(type, this.geo(build, noFrenzy) / reference);

			// 6. What the sky leaves behind: Lluvia estelar at a hundred, weighing nothing.
			Map<Upgrade, Integer> withEvent = new EnumMap<>(Upgrade.class);
			withEvent.putAll(build.upgrades);
			withEvent.put(Upgrade.LLUVIA_ESTELAR, 100);
			Build event = this.analysis.build(type, build.materials, withEvent);
			this.eventGain.put(type, this.geo(event, plain) / reference);

			// 7. Every blow to the head.
			Fight.Options heads = plain.copy();
			heads.headshots = true;
			this.headshotRatio.put(type, this.geo(build, heads) / reference);

			// New: vanilla's ten ticks after a hit.
			this.swallowedShare.put(type, best.sustained.swings <= 0 ? 0.0 : this.swallowed(best) / best.sustained.swings);
			Fight.Options noIframes = plain.copy();
			noIframes.iframes = false;
			if (!magic) {
				Fight.Result open = this.analysis.dps(build, 1200, 4, null, noIframes);
				this.iframesRatio.put(type, best.sustained.damage / Math.max(1.0E-9, open.damage));
			}

			// New: an Afilado part, and the Diafano handle, swapped for the nearest plain material.
			this.afiladoRatio.put(type, this.swapped(best, ForgeMaterial.Trait.AFILADO, ForgeMaterial.NETHERITA, reference));
			this.vidriaceroRatio.put(type, this.swapped(best, ForgeMaterial.Trait.DIAFANO, ForgeMaterial.ACERO_ESTELAR, reference));
			double main = 0.0;
			double extra = 0.0;
			for (Target target : this.analysis.search) {
				Fight.Result result = best.ttk.get(target.id);
				if (result != null) {
					main += result.mainDamage;
					extra += result.extraDamage;
				}
			}
			this.extraShare.put(type, main + extra <= 0 ? 0.0 : extra / (main + extra));

			// New: the Mestizaje bonus, as the tooltip shows it and as the weapon swings it.
			if (!magic) {
				List<ForgeMaterial> mixed = new ArrayList<>();
				for (PartType part : type.slots) {
					mixed.add(switch (part.role) {
						case HEAD -> ForgeMaterial.DAMASCO;
						case HANDLE -> ForgeMaterial.VIDRIACERO;
						default -> ForgeMaterial.ECO;
					});
				}
				Build mix = this.analysis.build(type, mixed, Map.of());
				ForgeStats.Sheet shown = ForgeStats.sheet(mix.stack, mix.parts());
				this.mestizaje.put(type, new double[] {ForgeStats.mixBonus(mix.parts()), 1.0 + shown.attackDamage, mix.attackDamage});
			}

			// The cross-check: simulated kill against max health over sustained damage on the same mob.
			List<Double> ratios = new ArrayList<>();
			double furthest = 1.0;
			String worst = "";
			for (Target target : this.analysis.targets) {
				Fight.Result result = best.ttk.get(target.id);
				Fight.Policy policy = best.policy.get(target.id);
				if (result == null || policy == null || result.killedShare < 0.999 || result.seconds() < 2.0) {
					continue;
				}
				Fight.Result endless = new Fight(build, target, plain, this.analysis.level, this.analysis.source)
					.run(policy, 8, 1200, false);
				double estimate = target.maxHealth / Math.max(1.0E-9, endless.dps());
				double ratio = result.seconds() / estimate;
				ratios.add(ratio);
				if (Math.abs(Math.log(ratio)) > Math.abs(Math.log(furthest))) {
					furthest = ratio;
					worst = target.id.substring(target.id.indexOf(':') + 1) + String.format(java.util.Locale.ROOT, " (%.1f s simulado, %.1f s estimado)",
						result.seconds(), estimate).replace('.', ',');
				}
			}
			if (!ratios.isEmpty()) {
				ratios.sort(java.util.Comparator.naturalOrder());
				this.crossCheck.put(type, new double[] {ratios.get(ratios.size() / 2), ratios.getFirst(), ratios.getLast(), ratios.size()});
				this.crossWorst.put(type, worst);
			}

			// Difficulty: every mob, under each preset, against the HERRERO numbers.
			double[] presets = new double[ForjaDifficulty.values().length];
			ForjaDifficulty current = ForjaDifficulty.current();
			double base = 0.0;
			for (ForjaDifficulty preset : ForjaDifficulty.values()) {
				List<Target> scaled = new ArrayList<>();
				for (Target target : this.analysis.targets) {
					scaled.add(target.under(current, preset));
				}
				double geo = this.analysis.geo(build, scaled, Analysis.SEARCH_RUNS, plain);
				presets[preset.ordinal()] = geo;
				if (preset == current) {
					base = geo;
				}
			}
			for (int i = 0; i < presets.length; i++) {
				presets[i] /= base;
			}
			this.difficulty.put(type, presets);
			this.allMobsGeo.put(type, base);
		}

		// Guard: a type that some other type beats against every mob in every scenario.
		for (ForgeType type : Analysis.TYPES) {
			for (ForgeType other : Analysis.TYPES) {
				if (other != type && this.beatsEverywhere(other, type)) {
					this.dominated.put(type, other);
					break;
				}
			}
		}
		this.inversions = Materials.tierInversions(true);
		java.util.Set<PartType> weaponParts = java.util.EnumSet.noneOf(PartType.class);
		for (ForgeType type : Analysis.TYPES) {
			for (PartType part : type.slots) {
				this.fronts.computeIfAbsent(part, Materials::front);
				weaponParts.add(part);
			}
		}
		for (Materials.Inversion inversion : Materials.tierInversions(false)) {
			if (weaponParts.contains(inversion.part())) {
				this.weaponInversions.add(inversion);
			}
		}
		for (ForgeMaterial material : ForgeMaterial.values()) {
			boolean fits = false;
			boolean survives = false;
			for (Map.Entry<PartType, Materials.Front> entry : this.fronts.entrySet()) {
				if (entry.getKey().accepts(material)) {
					fits = true;
					survives |= entry.getValue().kept().contains(material);
				}
			}
			if (fits && !survives) {
				this.uselessForWeapons.add(material);
			}
		}
		Build bareMace = this.analysis.build(ForgeType.MAZO, dev.forja.forge.Assembler.defaultMaterials(ForgeType.MAZO), Map.of());
		Build fastMace = this.analysis.build(ForgeType.MAZO, dev.forja.forge.Assembler.defaultMaterials(ForgeType.MAZO), Map.of(Upgrade.FRENESI, 100));
		this.plainMace = new double[] {bareMace.attackSpeed, fastMace.attackSpeed};
		java.util.Set<ForgeMaterial> seen = java.util.EnumSet.noneOf(ForgeMaterial.class);
		for (Analysis.TypeReport report : this.analysis.reports.values()) {
			for (int i = 0; i < Math.min(5, report.ranking.size()); i++) {
				seen.addAll(report.ranking.get(i).build.materials);
			}
			for (Analysis.Evaluated evaluated : report.best.values()) {
				seen.addAll(evaluated.build.materials);
			}
		}
		for (ForgeMaterial material : ForgeMaterial.values()) {
			if (!seen.contains(material)) {
				this.neverTop.add(material);
			}
		}
	}

	private double swallowed(Analysis.Evaluated best) {
		return best.sustained.swallowed;
	}

	/** The best build with every part of a trait swapped for a plain material; its mean time to kill over the original's. */
	private double swapped(Analysis.Evaluated best, ForgeMaterial.Trait trait, ForgeMaterial replacement, double reference) {
		if (!best.build.hasTrait(trait)) {
			return Double.NaN;
		}
		List<ForgeMaterial> materials = new ArrayList<>(best.build.materials);
		for (int i = 0; i < materials.size(); i++) {
			if (materials.get(i).trait == trait && best.build.type.slots.get(i).accepts(replacement)) {
				materials.set(i, replacement);
			}
		}
		Build swapped = this.analysis.build(best.build.type, materials, best.build.upgrades);
		return this.geo(swapped, this.analysis.options) / reference;
	}

	/** Whether {@code a} kills every mob at least 2 % sooner than {@code b}, at 0, 50 and 100 %. */
	public boolean beatsEverywhere(ForgeType a, ForgeType b) {
		for (Analysis.Scenario scenario : Analysis.Scenario.values()) {
			Analysis.Evaluated x = this.analysis.reports.get(a).best.get(scenario);
			Analysis.Evaluated y = this.analysis.reports.get(b).best.get(scenario);
			for (Target target : this.analysis.targets) {
				Fight.Result rx = x.ttk.get(target.id);
				Fight.Result ry = y.ttk.get(target.id);
				if (rx == null || ry == null) {
					continue;
				}
				if (!(rx.ticks < ry.ticks * 0.98)) {
					return false;
				}
			}
		}
		return true;
	}
}
