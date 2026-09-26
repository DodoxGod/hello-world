package dev.forja.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In the rain the drowned come out of the water after you, day or night (idea 99). */
@Mixin(Drowned.class)
abstract class DrownedRainMixin {
	@Inject(method = "okTarget", at = @At("RETURN"), cancellable = true)
	private void forja$rain(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		Drowned self = (Drowned) (Object) this;
		if (!cir.getReturnValue() && target != null && dev.forja.combat.CombatConfig.get().enabled && self.level().isRaining()) {
			cir.setReturnValue(true);
		}
	}
}
