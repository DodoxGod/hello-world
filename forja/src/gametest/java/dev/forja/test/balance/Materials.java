package dev.forja.test.balance;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.forja.forge.ForgeStats;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;

/**
 * Material pruning by Pareto dominance, one slot at a time, before anything is combined.
 *
 * <p>For a weapon only three things of a material count, and each only in its slot: a head brings its
 * damage bonus and durability, a handle its attack speed and durability factor, a binding or a guard its
 * durability. The trait counts wherever the material sits, so a material only dominates another that
 * carries the same trait. What survives is the front; the rest is listed with what beats it.
 */
public final class Materials {
	/** Traits that change what a blow does to one foe; the rest are ties as far as killing goes. */
	public static final Set<ForgeMaterial.Trait> COMBAT_TRAITS = EnumSet.of(
		ForgeMaterial.Trait.AFILADO, ForgeMaterial.Trait.IGNEO, ForgeMaterial.Trait.RESONANTE,
		ForgeMaterial.Trait.ACUATICO, ForgeMaterial.Trait.DIAFANO, ForgeMaterial.Trait.ESPECTRAL);

	private Materials() {
	}

	/** What a material is worth in a slot of a weapon: bigger is better in every entry. */
	public static double[] worth(PartType.Role role, ForgeMaterial material) {
		return switch (role) {
			case HEAD -> new double[] {material.attackDamageBonus, material.durability};
			case HANDLE -> new double[] {material.handleAttackSpeed, material.handleDurability};
			default -> new double[] {material.durability};
		};
	}

	/** One slot's survivors, and every loser with the material that beats it. */
	public record Front(List<ForgeMaterial> kept, Map<ForgeMaterial, ForgeMaterial> dominatedBy) {
	}

	public static Front front(PartType part) {
		List<ForgeMaterial> accepted = new ArrayList<>();
		for (ForgeMaterial material : ForgeMaterial.values()) {
			if (part.accepts(material)) {
				accepted.add(material);
			}
		}
		List<ForgeMaterial> kept = new ArrayList<>();
		Map<ForgeMaterial, ForgeMaterial> dominated = new LinkedHashMap<>();
		for (ForgeMaterial candidate : accepted) {
			ForgeMaterial beaten = null;
			for (ForgeMaterial other : accepted) {
				if (other != candidate && other.trait == candidate.trait && dominates(part.role, other, candidate, accepted)) {
					beaten = other;
					break;
				}
			}
			if (beaten == null) {
				kept.add(candidate);
			} else {
				dominated.put(candidate, beaten);
			}
		}
		return new Front(kept, dominated);
	}

	/**
	 * Whether {@code a} is at least as good as {@code b} everywhere and better somewhere. Two materials that
	 * tie in everything: the one listed first stays, so exactly one of a tie survives.
	 */
	static boolean dominates(PartType.Role role, ForgeMaterial a, ForgeMaterial b, List<ForgeMaterial> order) {
		double[] x = worth(role, a);
		double[] y = worth(role, b);
		boolean better = false;
		for (int i = 0; i < x.length; i++) {
			if (x[i] < y[i] - 1.0E-9) {
				return false;
			}
			if (x[i] > y[i] + 1.0E-9) {
				better = true;
			}
		}
		return better || order.indexOf(a) < order.indexOf(b);
	}

	/**
	 * Every material combination of a weapon built from the slot fronts, folded down to what differs in a
	 * fight: the stat sheet's damage and speed and the combat traits. Of each such group the most durable
	 * combination is kept.
	 */
	public static List<List<ForgeMaterial>> combinations(ForgeType type) {
		List<List<ForgeMaterial>> fronts = new ArrayList<>();
		for (PartType part : type.slots) {
			fronts.add(front(part).kept());
		}
		Map<String, List<ForgeMaterial>> best = new LinkedHashMap<>();
		Map<String, Integer> durability = new LinkedHashMap<>();
		List<ForgeMaterial> combo = new ArrayList<>();
		for (int i = 0; i < type.slots.size(); i++) {
			combo.add(null);
		}
		walk(type, fronts, 0, combo, best, durability);
		return new ArrayList<>(best.values());
	}

	private static void walk(ForgeType type, List<List<ForgeMaterial>> fronts, int slot, List<ForgeMaterial> combo,
		Map<String, List<ForgeMaterial>> best, Map<String, Integer> durability) {
		if (slot == fronts.size()) {
			ForgeStats.Sheet sheet = ForgeStats.sheet(type, combo, dev.forja.upgrade.Upgrades.EMPTY);
			Set<ForgeMaterial.Trait> traits = EnumSet.noneOf(ForgeMaterial.Trait.class);
			for (ForgeMaterial material : combo) {
				if (COMBAT_TRAITS.contains(material.trait)) {
					traits.add(material.trait);
				}
			}
			double spell = 0.0;
			if (dev.forja.magic.Spellcasting.casts(type)) {
				spell = combo.get(type.slots.indexOf(PartType.NUCLEO)).attackDamageBonus;
			}
			String key = String.format(java.util.Locale.ROOT, "%.3f|%.3f|%.3f|%s", sheet.attackDamage, sheet.attackSpeed, spell, traits);
			Integer known = durability.get(key);
			if (known == null || sheet.durability > known) {
				durability.put(key, sheet.durability);
				best.put(key, List.copyOf(combo));
			}
			return;
		}
		for (ForgeMaterial material : fronts.get(slot)) {
			combo.set(slot, material);
			walk(type, fronts, slot + 1, combo, best, durability);
		}
	}

	/**
	 * The tier of a material from what it can mine (its incorrect-for tag): wood and gold 0, stone 1, copper
	 * 1.5, iron 2, diamond 3, netherite 4. The mod has no other notion of tier.
	 */
	public static double tier(ForgeMaterial material) {
		String tag = material.incorrectBlocksForDrops.location().getPath();
		if (tag.contains("netherite")) {
			return 4.0;
		}
		if (tag.contains("diamond")) {
			return 3.0;
		}
		if (tag.contains("iron")) {
			return 2.0;
		}
		if (tag.contains("copper")) {
			return 1.5;
		}
		if (tag.contains("stone")) {
			return 1.0;
		}
		return 0.0;
	}

	/** A higher tier material worse than a lower one in every stat a part shows for it. */
	public record Inversion(PartType part, ForgeMaterial higher, ForgeMaterial lower) {
	}

	/**
	 * Every part and pair where the higher tier is strictly worse in every stat line of that part. Only
	 * materials that can be a head are compared (the mining tier means nothing for leather, scute or a blaze
	 * rod), and only parts with two stat lines or more (being worse at the only thing a part does is just a
	 * lower number, which a trait or a cost may buy).
	 *
	 * <p>{@code strict}: only what is a clear fault and not a design choice. Both materials plain (a trait
	 * is a reason to trade numbers away: quartz gives up durability for its edge), and a part where the tier
	 * is the point of it, a head or a plate.
	 */
	public static List<Inversion> tierInversions(boolean strict) {
		List<Inversion> found = new ArrayList<>();
		for (PartType part : PartType.values()) {
			for (ForgeMaterial higher : ForgeMaterial.values()) {
				for (ForgeMaterial lower : ForgeMaterial.values()) {
					if (!part.accepts(higher) || !part.accepts(lower) || tier(higher) <= tier(lower)) {
						continue;
					}
					// Strict: only where the tier is the point of the part, a head or a plate. A handle, a lining or a
					// membrane is about lightness, and a heavier metal being worse at it is the design, not a fault.
					if (!higher.canBeHead || !lower.canBeHead) {
						continue;
					}
					if (strict && (higher.trait != ForgeMaterial.Trait.NONE || lower.trait != ForgeMaterial.Trait.NONE
						|| part.role != PartType.Role.HEAD && part.role != PartType.Role.PLATE || part == PartType.MEMBRANA)) {
						continue;
					}
					List<ForgeStats.Line> high = ForgeStats.partLines(part, higher);
					List<ForgeStats.Line> low = ForgeStats.partLines(part, lower);
					if (high.size() < 2 || high.size() != low.size()) {
						continue;
					}
					boolean worseEverywhere = true;
					for (int i = 0; i < high.size(); i++) {
						ForgeStats.Line h = high.get(i);
						ForgeStats.Line l = low.get(i);
						boolean worse = h.stat().higherIsBetter ? h.value() < l.value() - 1.0E-9 : h.value() > l.value() + 1.0E-9;
						if (!worse) {
							worseEverywhere = false;
							break;
						}
					}
					if (worseEverywhere) {
						found.add(new Inversion(part, higher, lower));
					}
				}
			}
		}
		return found;
	}
}
