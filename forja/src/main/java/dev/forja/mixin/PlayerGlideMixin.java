package dev.forja.mixin;

import dev.forja.forge.Flight;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wings with an empty reserve stop gliding, and cannot start again until they fill up. */
@Mixin(Player.class)
public abstract class PlayerGlideMixin {
	@Inject(method = "canGlide", at = @At("RETURN"), cancellable = true)
	private void forjaFlightLeft(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && Flight.exhausted(((Player) (Object) this).getItemBySlot(EquipmentSlot.CHEST))) {
			cir.setReturnValue(false);
		}
	}
}
