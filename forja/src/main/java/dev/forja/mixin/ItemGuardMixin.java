package dev.forja.mixin;

import dev.forja.combat.WeaponGuard;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A guarding weapon steps aside for a shield in the other hand; see {@link WeaponGuard#yieldsToShield}. */
@Mixin(Item.class)
abstract class ItemGuardMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void forja$shieldFirst(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (WeaponGuard.yieldsToShield(player, hand, player.getItemInHand(hand))) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
}
