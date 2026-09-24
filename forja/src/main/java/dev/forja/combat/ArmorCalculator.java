package dev.forja.combat;

import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** The armor half of a hit: stands in for CombatRules.getDamageAfterAbsorb. */
public final class ArmorCalculator {
	/** Helmet, chestplate, leggings, boots. */
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Scales one piece's armor to a whole set's: a diamond piece counts as 20 in its own zone. */
	private static final double[] SLOT_NORMALIZER = {20.0 / 3.0, 20.0 / 8.0, 20.0 / 6.0, 20.0 / 3.0};
	/** How much each piece counts towards the weight of the whole set. */
	private static final double[] SLOT_SHARE = {0.15, 0.40, 0.30, 0.15};

	private ArmorCalculator() {
	}

	/** One worn piece as the formula sees it. */
	record Piece(double armor, MaterialCombat.Profile profile, double durability) {
	}

	public static float apply(LivingEntity target, float amount, AttackProfile attack, double extraPenetration) {
		CombatConfig cfg = CombatConfig.get();
		double armor = effectiveArmor(target, attack, cfg);
		double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		double pen = 1.0 - (1.0 - attack.penetration()) * (1.0 - ArmorMath.clamp(extraPenetration, 0.0, 1.0));
		return (float) ArmorMath.damageAfterArmor(amount, armor, pen, toughness, cfg.curveK, cfg.maxReduction, cfg.toughnessScale);
	}

	static double effectiveArmor(LivingEntity target, AttackProfile attack, CombatConfig cfg) {
		double armor = 0.0;
		double itemArmor = 0.0;
		for (int i = 0; i < SLOTS.length; i++) {
			Piece piece = piece(target.getItemBySlot(SLOTS[i]), SLOTS[i], cfg);
			if (piece == null) continue;
			itemArmor += piece.armor();
			double weight = attack.zone().weight(i);
			if (weight > 0) {
				armor += piece.armor() * SLOT_NORMALIZER[i] * weight * piece.profile().resistance(attack.kind()) * piece.durability();
			}
		}
		// What is not on the pieces (a zombie's hide, set bonuses, an elite's plating, talismans) guards
		// the whole body. The base value covers the tick before freshly equipped gear updates the total.
		armor += Math.max(target.getAttributeBaseValue(Attributes.ARMOR), target.getArmorValue() - itemArmor);
		return armor;
	}

	/** How heavy the worn set is, 0 (cloth) to 1 (a full set of the heaviest plate). */
	public static double armorWeight(LivingEntity entity) {
		CombatConfig cfg = CombatConfig.get();
		double weight = 0.0;
		for (int i = 0; i < SLOTS.length; i++) {
			Piece piece = piece(entity.getItemBySlot(SLOTS[i]), SLOTS[i], cfg);
			if (piece != null) weight += SLOT_SHARE[i] * piece.profile().weight();
		}
		return weight;
	}

	static Piece piece(ItemStack stack, EquipmentSlot slot, CombatConfig cfg) {
		if (stack.isEmpty()) return null;
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double armor = modifiers.compute(Attributes.ARMOR, 0.0, slot);
		if (armor <= 0) return null;
		double durability = stack.isDamageableItem()
			? ArmorMath.durabilityFactor(stack.getDamageValue(), stack.getMaxDamage(), cfg.minDurabilityFactor)
			: 1.0;
		return new Piece(armor, profile(stack, slot, armor, modifiers), durability);
	}

	private static MaterialCombat.Profile profile(ItemStack stack, EquipmentSlot slot, double armor, ItemAttributeModifiers modifiers) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts != null && parts.type().kind == ForgeType.Kind.ARMOR) {
			ForgeMaterial plate = parts.material(parts.type().slotOf(PartType.Role.PLATE));
			ForgeMaterial lining = parts.material(parts.type().slotOf(PartType.Role.LINING));
			return MaterialCombat.forged(plate, lining);
		}
		int index = switch (slot) {
			case HEAD -> 0;
			case CHEST -> 1;
			case LEGS -> 2;
			default -> 3;
		};
		double fullSet = armor * SLOT_NORMALIZER[index];
		double toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot) * 4.0;
		double knockback = modifiers.compute(Attributes.KNOCKBACK_RESISTANCE, 0.0, slot);
		return MaterialCombat.generic(fullSet, toughness, knockback);
	}
}
