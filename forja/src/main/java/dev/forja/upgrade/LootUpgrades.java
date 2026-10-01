package dev.forja.upgrade;

import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Fundicion smelts block drops as they drop; Telequinesis puts them straight in the inventory.
 * Each drop gets its own roll against the upgrade's percentage. Both hook block loot tables, so
 * they also apply to blocks broken by Veta, Excavacion and a thrown head.
 */
public final class LootUpgrades {
	private LootUpgrades() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (!context.hasParameter(LootContextParams.BLOCK_STATE)) {
				return;
			}
			ItemInstance tool = context.getOptionalParameter(LootContextParams.TOOL);
			if (tool == null) {
				return;
			}
			ServerLevel level = context.getLevel();
			RandomSource random = level.getRandom();

			float smelting = Upgrades.fraction(tool, Upgrade.FUNDICION);
			if (smelting > 0.0F) {
				smelt(level, drops, random, smelting);
			}

			Entity breaker = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
			float telekinesis = Upgrades.fraction(tool, Upgrade.TELEQUINESIS);
			ForgedParts parts = tool.get(ModComponents.PARTS);
			if (parts != null && parts.hasTrait(ForgeMaterial.Trait.DEL_END)) {
				telekinesis = Math.max(telekinesis, 0.3F);
			}
			float sendChance = telekinesis;
			if (breaker instanceof ServerPlayer player && sendChance > 0.0F) {
				drops.removeIf(drop -> {
					if (random.nextFloat() >= sendChance) {
						return false;
					}
					player.getInventory().add(drop);
					return drop.isEmpty();
				});
			}
		});
	}

	private static void smelt(ServerLevel level, List<ItemStack> drops, RandomSource random, float chance) {
		ListIterator<ItemStack> iterator = drops.listIterator();
		while (iterator.hasNext()) {
			ItemStack drop = iterator.next();
			if (random.nextFloat() >= chance) {
				continue;
			}
			SingleRecipeInput input = new SingleRecipeInput(drop);
			Optional<RecipeHolder<SmeltingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level);
			if (recipe.isPresent()) {
				ItemStack result = recipe.get().value().assemble(input);
				if (!result.isEmpty()) {
					iterator.set(result.copyWithCount(result.getCount() * drop.getCount()));
				}
			}
		}
	}
}
