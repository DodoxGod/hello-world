package dev.forja.test.balance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.forja.combat.CombatConfig;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.part.PartVariant;

/**
 * Heavy and light handles and bindings (combat/Grip) fought against the plain ones: the same iron head, bare,
 * on a few weapon kinds, with the handle and binding made of a sample of materials. Every variant comes in
 * every material (Andy, 2026-09-30), so the sample runs from wood to netherite and through the glass steel
 * that weighs next to nothing.
 *
 * <p>Andy asked for a choice, not an upgrade, so the guard (BalanceGameTests) fails if a variant beats the
 * plain part of the same material in burst, sustained damage and time to kill all at once without paying for
 * it, or if any one combination of material and variant beats every other one of its kind at everything.
 */
public final class Variants {
	/** The kinds compared: a sword (handle only), and three with a handle and a binding. */
	public static final List<ForgeType> TYPES = List.of(ForgeType.ESPADA, ForgeType.HACHA, ForgeType.MAZO, ForgeType.LANZA);
	/** The kinds the compact table in EQUILIBRIO.md shows. */
	public static final List<ForgeType> TABLE_TYPES = List.of(ForgeType.ESPADA, ForgeType.HACHA, ForgeType.MAZO);

	/**
	 * What the handle and the binding are made of: light (wood, bone), the middle (iron), the heaviest
	 * (netherite) and a metal lighter than bone (glass steel).
	 */
	public static final List<ForgeMaterial> MATERIALS = List.of(ForgeMaterial.MADERA, ForgeMaterial.HUESO, ForgeMaterial.HIERRO,
		ForgeMaterial.NETHERITA, ForgeMaterial.VIDRIACERO);

	/** One way of building a kind: its name in the table and its handle and binding. */
	public record Setup(String name, PartVariant handle, PartVariant binding) {
	}

	public static final List<Setup> SETUPS = List.of(
		new Setup("normal", PartVariant.NORMAL, PartVariant.NORMAL),
		new Setup("mango pesado", PartVariant.PESADO, PartVariant.NORMAL),
		new Setup("mango ligero", PartVariant.LIGERO, PartVariant.NORMAL),
		new Setup("atadura pesada", PartVariant.NORMAL, PartVariant.PESADO),
		new Setup("atadura ligera", PartVariant.NORMAL, PartVariant.LIGERO),
		new Setup("todo pesado", PartVariant.PESADO, PartVariant.PESADO),
		new Setup("todo ligero", PartVariant.LIGERO, PartVariant.LIGERO));

	/** A setup fought to the full. */
	public record Row(ForgeType type, ForgeMaterial material, Setup setup, Build build, Analysis.Evaluated evaluated) {
		public double burst() {
			return this.evaluated.burst.damage / 3.0;
		}

		public double sustained() {
			return this.evaluated.sustained.dps();
		}

		public double ttk() {
			return this.evaluated.geo;
		}

		/** Stamina one plain swing costs. */
		public double staminaPerSwing() {
			return CombatConfig.get().attackCost * this.build.swingCost;
		}

		public double tiredShare() {
			return this.evaluated.sustained.tired / Math.max(1.0E-9, this.evaluated.sustained.swings);
		}

		/** Staggers a minute on the dummy (a mob of 20 health's bar), with the rhythm that does most damage. */
		public double staggersPerMinute() {
			double seconds = Math.max(1.0E-9, this.evaluated.sustained.seconds());
			return this.evaluated.sustained.staggers * 60.0 / seconds;
		}

		public double kg() {
			return dev.forja.combat.Weight.kg(this.build.parts());
		}

		/**
		 * Balance taken off the dummy a second in the long fight: the damage, times what each point of it does to
		 * the posture bar with this handle (combat/Grip). The staggers a minute say the same in whole numbers, too
		 * coarse to tell a light handle's softer blows from the plain one's on a weapon that staggers rarely.
		 */
		public double balancePerSecond() {
			return this.sustained() * this.build.posture;
		}

		/** Whether this row is better than that one in every number at once (by more than noise where it is noisy). */
		boolean beats(Row other) {
			return this.burst() > other.burst() * 1.01 && this.sustained() > other.sustained() * 1.01 && this.ttk() < other.ttk() * 0.99
				&& this.staminaPerSwing() <= other.staminaPerSwing() && this.staggersPerMinute() >= other.staggersPerMinute()
				&& this.balancePerSecond() >= other.balancePerSecond() && this.build.durability >= other.build.durability;
		}
	}

	public final List<Row> rows = new ArrayList<>();
	/** "type material: setup" for every variant that beats the plain one of its kind and material in everything at once. */
	public final List<String> dominant = new ArrayList<>();
	/** "type material: setup" for a combination that beats every other combination of its kind in the sample. */
	public final List<String> overall = new ArrayList<>();

	/** The parts of a setup: an iron head, and the handle and binding of that material in those variants. */
	public static ForgedParts parts(ForgeType type, ForgeMaterial material, Setup setup) {
		List<ForgeMaterial> materials = new ArrayList<>(Assembler.defaultMaterials(type));
		List<PartVariant> variants = new ArrayList<>();
		for (int slot = 0; slot < type.slots.size(); slot++) {
			PartType part = type.slots.get(slot);
			PartVariant variant = part == PartType.MANGO ? setup.handle() : part == PartType.ATADURA ? setup.binding() : PartVariant.NORMAL;
			if (part == PartType.MANGO || part == PartType.ATADURA) {
				materials.set(slot, material);
			}
			variants.add(variant);
		}
		return new ForgedParts(type, materials, variants);
	}

	public void measure(Analysis analysis) {
		for (ForgeType type : TYPES) {
			for (ForgeMaterial material : MATERIALS) {
				for (Setup setup : SETUPS) {
					if (setup.binding() != PartVariant.NORMAL && !type.slots.contains(PartType.ATADURA)) {
						continue;
					}
					Build build = new Build(parts(type, material, setup), Map.of(), analysis.level.registryAccess());
					this.rows.add(new Row(type, material, setup, build, analysis.evaluate(build, analysis.search, Analysis.FINAL_RUNS)));
				}
			}
		}
		for (Row row : this.rows) {
			String name = row.type.id() + " " + row.material.getSerializedName() + ": " + row.setup.name();
			if (row.setup != SETUPS.getFirst() && row.beats(this.row(row.type, row.material, SETUPS.getFirst()))) {
				// Better in all three by more than noise, and paying nothing in breath, balance or wear: an upgrade, not a choice.
				this.dominant.add(name);
			}
			boolean beatsAll = true;
			for (Row other : this.rows) {
				if (other != row && other.type == row.type && !row.beats(other)) {
					beatsAll = false;
					break;
				}
			}
			if (beatsAll) {
				this.overall.add(name);
			}
		}
	}

	/** The row of that kind, material and setup, or null when the kind has no binding for it. */
	public Row row(ForgeType type, ForgeMaterial material, Setup setup) {
		for (Row row : this.rows) {
			if (row.type == type && row.material == material && row.setup == setup) {
				return row;
			}
		}
		return null;
	}

	/** The plain setup, and the all-heavy and all-light ones (for a sword, just its handle). */
	public static List<Setup> threeShapes(ForgeType type) {
		boolean binding = type.slots.contains(PartType.ATADURA);
		return List.of(SETUPS.get(0), binding ? SETUPS.get(5) : SETUPS.get(1), binding ? SETUPS.get(6) : SETUPS.get(2));
	}
}
