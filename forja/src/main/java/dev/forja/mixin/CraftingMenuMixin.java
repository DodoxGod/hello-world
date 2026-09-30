package dev.forja.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.forja.GuideBooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The guide's books are crafted from recipes the player has to learn first (GuideBooks): the grid gives
 * nothing for a book whose recipe is not in the player's recipe book yet. It is vanilla's own check for the
 * doLimitedCrafting rule, asked of these recipes whatever the rule says. The 2x2 grid of the inventory goes
 * through the same method.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
	@WrapOperation(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/inventory/ResultContainer;setRecipeUsed(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/RecipeHolder;)Z"))
	private static boolean forja$learnedBooksOnly(ResultContainer result, ServerPlayer player, RecipeHolder<?> recipe, Operation<Boolean> original) {
		if (!GuideBooks.mayCraft(player, recipe)) {
			return false;
		}
		return original.call(result, player, recipe);
	}
}
