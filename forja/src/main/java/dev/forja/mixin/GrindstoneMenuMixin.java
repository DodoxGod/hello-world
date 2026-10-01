package dev.forja.mixin;

import dev.forja.registry.ModComponents;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The grindstone would strip the hidden enchantments behind a forged item's upgrades while the
 * upgrades stayed listed, and pay experience for them. Forged gear sheds upgrades by salvage instead.
 */
@Mixin(GrindstoneMenu.class)
abstract class GrindstoneMenuMixin {
	@Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
	private void forja$refuseForged(ItemStack input, ItemStack additional, CallbackInfoReturnable<ItemStack> cir) {
		if (input.has(ModComponents.PARTS) || additional.has(ModComponents.PARTS)) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}
}
