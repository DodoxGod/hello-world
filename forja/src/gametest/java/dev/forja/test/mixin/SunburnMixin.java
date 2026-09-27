package dev.forja.test.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The undead stop burning in the test world's noon while {@link dev.forja.test.Sunburn#off} is set: a
 * picture of a zombie's badge is no use with the zombie in flames up to the badge.
 */
@Mixin(Mob.class)
abstract class SunburnMixin {
	@Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
	private void test$noSunburn(CallbackInfoReturnable<Boolean> cir) {
		if (dev.forja.test.Sunburn.off) {
			cir.setReturnValue(false);
		}
	}
}
