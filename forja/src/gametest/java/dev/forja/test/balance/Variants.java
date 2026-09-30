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
 * Heavy and light handles and bindings (combat/Grip) fought against the plain ones: the same head, bare, on a
 * few weapon kinds. Andy asked for a choice, not an upgrade, so the guard (BalanceGameTests) fails if either
 * variant beats the plain one in burst, sustained damage and time to kill all at once.
 */
public final class Variants {
	/** The kinds compared: a sword (handle only), and three with a handle and a binding. */
	public static final List<ForgeType> TYPES = List.of(ForgeType.ESPADA, ForgeType.HACHA, ForgeType.MAZO, ForgeType.LANZA);

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
	public record Row(ForgeType type, Setup setup, Build build, Analysis.Evaluated evaluated) {
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
	}

	public final List<Row> rows = new ArrayList<>();
	/** "type: setup" for every variant that beats the plain one of its kind in everything at once. */
	public final List<String> dominant = new ArrayList<>();

	/** The parts of a setup: an iron head, and the handle and binding in what that variant is made of. */
	public static ForgedParts parts(ForgeType type, Setup setup) {
		List<ForgeMaterial> materials = new ArrayList<>(Assembler.defaultMaterials(type));
		List<PartVariant> variants = new ArrayList<>();
		for (int slot = 0; slot < type.slots.size(); slot++) {
			PartType part = type.slots.get(slot);
			PartVariant variant = part == PartType.MANGO ? setup.handle() : part == PartType.ATADURA ? setup.binding() : PartVariant.NORMAL;
			if (part == PartType.MANGO) {
				materials.set(slot, variant == PartVariant.PESADO ? ForgeMaterial.HIERRO : ForgeMaterial.MADERA);
			} else if (part == PartType.ATADURA) {
				materials.set(slot, variant == PartVariant.PESADO ? ForgeMaterial.HIERRO : variant == PartVariant.LIGERO ? ForgeMaterial.CUERO : ForgeMaterial.MADERA);
			}
			variants.add(variant);
		}
		return new ForgedParts(type, materials, variants);
	}

	public void measure(Analysis analysis) {
		for (ForgeType type : TYPES) {
			for (Setup setup : SETUPS) {
				if (setup.binding() != PartVariant.NORMAL && !type.slots.contains(PartType.ATADURA)) {
					continue;
				}
				Build build = new Build(parts(type, setup), Map.of(), analysis.level.registryAccess());
				this.rows.add(new Row(type, setup, build, analysis.evaluate(build, analysis.targets, Analysis.FINAL_RUNS)));
			}
		}
		for (Row row : this.rows) {
			if (row.setup.handle() == PartVariant.NORMAL && row.setup.binding() == PartVariant.NORMAL) {
				continue;
			}
			Row plain = this.rows.stream().filter(r -> r.type == row.type && r.setup == SETUPS.getFirst()).findFirst().orElseThrow();
			// Better in all three by more than noise, and paying nothing in breath, balance or wear: an upgrade, not a choice.
			if (row.burst() > plain.burst() * 1.01 && row.sustained() > plain.sustained() * 1.01 && row.ttk() < plain.ttk() * 0.99
				&& row.staminaPerSwing() <= plain.staminaPerSwing() && row.staggersPerMinute() >= plain.staggersPerMinute()
				&& row.build.durability >= plain.build.durability) {
				this.dominant.add(row.type.id() + ": " + row.setup.name());
			}
		}
	}
}
