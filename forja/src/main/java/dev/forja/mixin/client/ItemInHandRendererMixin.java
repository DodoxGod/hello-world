package dev.forja.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * First person only poses bows and crossbows that are exactly Items.BOW or Items.CROSSBOW: aiming a
 * loaded crossbow, winding it up, hiding the other hand while drawing. Forged ones count too.
 */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
	@WrapOperation(
		method = {"evaluateWhichHandsToRender", "selectionUsingItemWhileHoldingBowLike", "isChargedCrossbow"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private static boolean forja$bowLikeSelection(ItemStack stack, Object item, Operation<Boolean> original) {
		return original.call(stack, item) || forja$bowLike(stack, item);
	}

	@WrapOperation(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private boolean forja$bowLikeArm(ItemStack stack, Object item, Operation<Boolean> original) {
		return original.call(stack, item) || forja$bowLike(stack, item);
	}

	private static boolean forja$bowLike(ItemStack stack, Object item) {
		return item == Items.CROSSBOW ? stack.getItem() instanceof CrossbowItem : item == Items.BOW && stack.getItem() instanceof BowItem;
	}
}
