package com.dodoxgod.filo.combat;

import com.dodoxgod.filo.config.FiloConfig;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;

/** Sustituye el cálculo de armadura vanilla (DamageUtil.getDamageLeft). */
public final class ArmorCalculator {
	/** Casco, peto, pantalones, botas. */
	public static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	/**
	 * Convierte la armadura de una pieza en "armadura de conjunto completo": una pieza de
	 * diamante vale 20 en su zona, como el conjunto entero en vanilla.
	 */
	private static final double[] SLOT_NORMALIZER = {20.0 / 3.0, 20.0 / 8.0, 20.0 / 6.0, 20.0 / 3.0};
	/** Peso de cada pieza en la movilidad del conjunto. */
	private static final double[] SLOT_SHARE = {0.15, 0.40, 0.30, 0.15};

	private ArmorCalculator() {
	}

	public static float apply(LivingEntity target, float amount, AttackProfile profile) {
		FiloConfig cfg = FiloConfig.get();
		double armor = effectiveArmor(target, profile, cfg);
		double toughness = target.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
		return (float) ArmorMath.damageAfterArmor(amount, armor, profile.penetration(), toughness,
				cfg.armor.curveK, cfg.armor.maxReduction, cfg.armor.toughnessScale);
	}

	static double effectiveArmor(LivingEntity target, AttackProfile profile, FiloConfig cfg) {
		double armor = 0.0;
		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack stack = target.getEquippedStack(SLOTS[i]);
			if (!(stack.getItem() instanceof ArmorItem armorItem)) continue;
			double protection = armorItem.getProtection();
			double weight = profile.zone().weight(i);
			if (weight <= 0) continue;
			FiloConfig.MaterialStats stats = cfg.material(materialId(armorItem));
			double durability = stack.isDamageable()
					? ArmorMath.durabilityFactor(stack.getDamage(), stack.getMaxDamage(), cfg.armor.minDurabilityFactor)
					: 1.0;
			armor += protection * SLOT_NORMALIZER[i] * weight * stats.resistance(profile.kind()) * durability;
		}
		// Armadura natural (zombis, etc.): el valor base del atributo, que no incluye la de las piezas.
		armor += target.getAttributeBaseValue(EntityAttributes.GENERIC_ARMOR);
		return armor;
	}

	/** Penalización de movilidad del equipo actual: 0 = ninguna, 0.1 = 10 % más lento. */
	public static double armorWeight(LivingEntity entity) {
		FiloConfig cfg = FiloConfig.get();
		double weight = 0.0;
		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack stack = entity.getEquippedStack(SLOTS[i]);
			if (stack.getItem() instanceof ArmorItem armorItem) {
				weight += SLOT_SHARE[i] * (1.0 - cfg.material(materialId(armorItem)).mobility);
			}
		}
		return Math.max(0.0, weight);
	}

	private static String materialId(ArmorItem item) {
		return item.getMaterial().getKey().map(key -> key.getValue().toString()).orElse("");
	}
}
