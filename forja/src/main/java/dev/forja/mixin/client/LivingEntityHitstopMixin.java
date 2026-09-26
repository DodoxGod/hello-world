package dev.forja.mixin.client;

import dev.forja.client.CombatAnims;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hitstop: when a blow lands, the player's own swing holds still for a few frames, first person and
 * third alike, which is most of what makes a hit feel like it met something.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityHitstopMixin {
	@Inject(method = "getAttackAnim", at = @At("HEAD"), cancellable = true)
	private void forja$hitstop(float partialTick, CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			float frozen = CombatAnims.frozenAttack(self);
			if (!Float.isNaN(frozen)) {
				cir.setReturnValue(frozen);
			}
		}
	}
}
