package dev.forja.combat;

import java.util.EnumMap;
import java.util.Map;

import dev.forja.material.ForgeMaterial;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * What a material is like to be hit through, worked out from the numbers every material already has,
 * so a new material or an odd plate-and-lining pairing needs no table of its own.
 *
 * <ul>
 *   <li><b>Rigidity</b> comes from the full-set defense and the toughness of a material: leather is 0,
 *   iron about a third, diamond and netherite at the top.</li>
 *   <li>The <b>plate</b> is what an edge or a point has to get through, so a rigid plate turns slashes
 *   and pierces. Knockback resistance means mass behind it, which helps against blunt blows.</li>
 *   <li>The <b>lining</b> is what a hammer has to get through: a soft lining (leather, wool-like
 *   materials) cushions blunt blows, a rigid one passes them on.</li>
 *   <li><b>Weight</b> follows the plate's defense and mass; Diafano plate weighs next to nothing.</li>
 * </ul>
 *
 * Every number can still be nudged per material in config/forja.json, but nothing has to be.
 */
public final class MaterialCombat {
	/** Resistance multipliers against each kind of blow, and how heavy a full set would be (0 to 1). */
	public record Profile(double slash, double blunt, double pierce, double weight) {
		public static final Profile NEUTRAL = new Profile(1.0, 1.0, 1.0, 0.0);

		public double resistance(DamageKind kind) {
			return switch (kind) {
				case SLASH -> slash;
				case BLUNT -> blunt;
				case PIERCE -> pierce;
				case OTHER -> 1.0;
			};
		}
	}

	/** Leather's full-set defense: the floor of the rigidity scale. */
	private static final double SOFTEST_SET = 7.0;
	/** Defense past leather that, with no toughness at all, would make a material fully rigid. */
	private static final double DEFENSE_SPAN = 26.0;
	private static final double TOUGHNESS_SPAN = 3.0;

	private static final Map<ForgeMaterial, Double> RIGIDITY = new EnumMap<>(ForgeMaterial.class);

	private MaterialCombat() {
	}

	/** 0 for the softest material, 1 for the hardest; derived, not listed. */
	public static double rigidity(ForgeMaterial material) {
		return RIGIDITY.computeIfAbsent(material, m -> rigidity(fullSetDefense(m), m.toughness));
	}

	public static double rigidity(double fullSetDefense, double toughness) {
		return ArmorMath.clamp(toughness / TOUGHNESS_SPAN + (fullSetDefense - SOFTEST_SET) / DEFENSE_SPAN, 0.0, 1.0);
	}

	/** Weight of a full set of this plate, 0 to 1. */
	public static double weight(double fullSetDefense, double knockbackResistance) {
		return ArmorMath.clamp((fullSetDefense - SOFTEST_SET) / 13.0, 0.0, 1.0) * 0.7
			+ ArmorMath.clamp(knockbackResistance * 3.0, 0.0, 0.3);
	}

	/** A forged piece: the plate against edges and points, the lining against blunt blows. */
	public static Profile forged(ForgeMaterial plate, ForgeMaterial lining) {
		double rigid = rigidity(plate);
		double soft = 1.0 - rigidity(lining);
		double weight = weight(fullSetDefense(plate), plate.knockbackResistance);
		if (plate.trait == ForgeMaterial.Trait.DIAFANO) {
			weight *= 0.2;
		}
		Profile derived = new Profile(
			0.8 + 0.5 * rigid,
			(0.9 + 2.0 * plate.knockbackResistance) * (0.8 + 0.4 * soft),
			0.7 + 0.5 * rigid,
			weight
		);
		return override(plate, derived);
	}

	/**
	 * Any other piece (vanilla, another mod): the same scale, read from the piece's own armor and
	 * toughness. There is no lining to speak of, so blunt blows meet the plain plate.
	 *
	 * @param fullSetDefense the piece's armor scaled to what a full set of it would give
	 */
	public static Profile generic(double fullSetDefense, double toughness, double knockbackResistance) {
		double rigid = rigidity(fullSetDefense, toughness);
		return new Profile(
			0.8 + 0.5 * rigid,
			0.9 + 0.3 * (1.0 - rigid) + 2.0 * knockbackResistance,
			0.7 + 0.5 * rigid,
			weight(fullSetDefense, knockbackResistance)
		);
	}

	/**
	 * Mail is the one vanilla armor the formula reads wrong: it is light, so the scale takes it for a soft
	 * material, but rings are exactly what stops an edge. They do little against a hammer, and a point can
	 * slip through them. Nudged per piece like any material, under the name "cota_de_malla".
	 */
	public static Profile chainmail(Profile generic) {
		Profile mail = new Profile(1.35, 0.8, 0.95, generic.weight());
		CombatConfig.MaterialOverride tweak = CombatConfig.get().materiales.get("cota_de_malla");
		if (tweak == null) {
			return mail;
		}
		return new Profile(
			tweak.slash != null ? tweak.slash : mail.slash(),
			tweak.blunt != null ? tweak.blunt : mail.blunt(),
			tweak.pierce != null ? tweak.pierce : mail.pierce(),
			tweak.weight != null ? tweak.weight : mail.weight()
		);
	}

	private static Profile override(ForgeMaterial plate, Profile derived) {
		CombatConfig.MaterialOverride tweak = CombatConfig.get().materiales.get(plate.getSerializedName());
		if (tweak == null) {
			return derived;
		}
		return new Profile(
			tweak.slash != null ? tweak.slash : derived.slash(),
			tweak.blunt != null ? tweak.blunt : derived.blunt(),
			tweak.pierce != null ? tweak.pierce : derived.pierce(),
			tweak.weight != null ? tweak.weight : derived.weight()
		);
	}

	private static double fullSetDefense(ForgeMaterial m) {
		return m.defense(ArmorType.HELMET) + m.defense(ArmorType.CHESTPLATE)
			+ m.defense(ArmorType.LEGGINGS) + m.defense(ArmorType.BOOTS);
	}
}
