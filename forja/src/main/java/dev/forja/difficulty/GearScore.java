package dev.forja.difficulty;

import dev.forja.forge.Mastery;
import dev.forja.forge.SmithLevel;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Upgrades;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * How well equipped a player is, from 0 (bare hands) to 1 (a legend's kit): what the weapon hits for,
 * how much upgrade is on the gear, the weapon's mastery, the smith's own level and the armor worn.
 * Monsters that spawn near a player come tougher by tiers of it, so improving your gear still feels like
 * getting stronger, but never turns every fight into one blow.
 *
 * <pre>
 * score = 0,35 · clamp((ataque − 1) / 15)
 *       + 0,20 · clamp(suma de % de mejoras en mano y armadura / 400)
 *       + 0,15 · maestría del arma / 10
 *       + 0,10 · nivel de herrero / 10
 *       + 0,20 · clamp(armadura / 30)
 * tramo = min(3, floor(score · 4))
 * </pre>
 */
public final class GearScore {
	private static final EquipmentSlot[] WORN = {
		EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private GearScore() {
	}

	public static double of(Player player) {
		if (player == null) {
			return 0.0;
		}
		double weapon = Mth.clamp((player.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0) / 15.0, 0.0, 1.0);
		int percents = 0;
		for (EquipmentSlot slot : WORN) {
			ItemStack stack = player.getItemBySlot(slot);
			Upgrades upgrades = stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
			for (int percent : upgrades.percents().values()) {
				percents += percent;
			}
		}
		double upgrades = Mth.clamp(percents / 400.0, 0.0, 1.0);
		double mastery = Mastery.level(player.getMainHandItem()) / (double) Mastery.MAX_LEVEL;
		double smith = SmithLevel.level(player) / (double) SmithLevel.MAX_LEVEL;
		double armor = Mth.clamp(player.getArmorValue() / 30.0, 0.0, 1.0);
		return Mth.clamp(0.35 * weapon + 0.20 * upgrades + 0.15 * mastery + 0.10 * smith + 0.20 * armor, 0.0, 1.0);
	}

	public static int tier(double score) {
		return Math.min(3, (int) Math.floor(score * 4.0));
	}
}
