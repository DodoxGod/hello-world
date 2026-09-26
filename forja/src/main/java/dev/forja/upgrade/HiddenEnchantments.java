package dev.forja.upgrade;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Forged gear cannot be enchanted, but vanilla enchantments still do the work behind the scenes:
 * enchantment-backed upgrades and some material traits are written here as real enchantment levels,
 * which the item's tooltip hides. Always rebuilt from scratch from the upgrades and parts.
 */
public final class HiddenEnchantments {
	private HiddenEnchantments() {
	}

	public static void write(ItemStack stack, HolderLookup.Provider registries) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null) {
			return;
		}
		Upgrades upgrades = stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		Map<ResourceKey<Enchantment>, Integer> levels = new LinkedHashMap<>();
		upgrades.percents().forEach((upgrade, percent) -> {
			int level = upgrade.enchantmentLevel(percent);
			if (upgrade.enchantment != null && level > 0) {
				levels.merge(upgrade.enchantment, level, Math::max);
			}
		});

		ForgeType.Kind kind = parts.type().kind;
		if (parts.hasTrait(ForgeMaterial.Trait.AFORTUNADO)) {
			if (kind == ForgeType.Kind.WEAPON) {
				levels.merge(Enchantments.LOOTING, 1, Integer::sum);
			} else if (kind == ForgeType.Kind.TOOL && upgrades.percent(Upgrade.TOQUE_DE_SEDA) == 0) {
				levels.merge(Enchantments.FORTUNE, 1, Integer::sum);
			}
		}
		if (parts.hasTrait(ForgeMaterial.Trait.ACUATICO) && kind == ForgeType.Kind.WEAPON) {
			levels.merge(Enchantments.IMPALING, 2, Integer::sum);
		}
		if (parts.hasTrait(ForgeMaterial.Trait.IGNEO)) {
			if (kind == ForgeType.Kind.WEAPON || kind == ForgeType.Kind.TOOL) {
				levels.merge(Enchantments.FIRE_ASPECT, 1, Integer::sum);
			} else if (kind == ForgeType.Kind.RANGED) {
				levels.merge(Enchantments.FLAME, 1, Math::max);
			}
		}
		if (parts.hasTrait(ForgeMaterial.Trait.AFILADO) && kind == ForgeType.Kind.ARMOR) {
			levels.merge(Enchantments.THORNS, 1, Integer::sum);
		}
		if (parts.hasTrait(ForgeMaterial.Trait.RESONANTE)) {
			if (kind == ForgeType.Kind.WEAPON) {
				levels.merge(Enchantments.BREACH, 1, Integer::sum);
			} else if (kind == ForgeType.Kind.RANGED) {
				levels.merge(Enchantments.PIERCING, 1, Integer::sum);
			}
		}
		if (parts.hasTrait(ForgeMaterial.Trait.PEGAJOSO)) {
			levels.merge(Enchantments.UNBREAKING, 1, Integer::sum);
		}
		if (parts.hasTrait(ForgeMaterial.Trait.ACORAZADO) && kind == ForgeType.Kind.ARMOR) {
			levels.merge(Enchantments.PROJECTILE_PROTECTION, 1, Integer::sum);
			levels.merge(Enchantments.BLAST_PROTECTION, 1, Integer::sum);
		}

		if (dev.forja.forge.Perk.has(stack, dev.forja.forge.Perk.PESCADOR)) {
			levels.merge(Enchantments.LURE, 1, Integer::sum);
			levels.merge(Enchantments.LUCK_OF_THE_SEA, 1, Integer::sum);
		}

		HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		levels.forEach((key, level) -> lookup.get(key).ifPresent(holder -> enchantments.set(holder, level)));
		stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
	}
}
