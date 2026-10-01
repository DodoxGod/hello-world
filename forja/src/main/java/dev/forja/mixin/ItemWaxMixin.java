package dev.forja.mixin;

import dev.forja.item.ForgedItems;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Honeycomb on a forged copper piece seals its patina, the same gesture as waxing a block: hold the
 * honeycomb on the cursor and right-click the piece in the inventory.
 */
@Mixin(Item.class)
public abstract class ItemWaxMixin {
	@Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
	private void forja$waxCopper(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access, CallbackInfoReturnable<Boolean> cir) {
		if (action == ClickAction.SECONDARY && ForgedItems.waxWith(self, other, player)) {
			cir.setReturnValue(true);
		}
	}
}
