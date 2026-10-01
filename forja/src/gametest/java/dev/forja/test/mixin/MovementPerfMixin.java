package dev.forja.test.mixin;

import dev.forja.test.Perf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The other movement goals of phase 7, each asked whether it wants to run. */
@Mixin(targets = {"dev.forja.ai.MovementGoals$Track", "dev.forja.ai.MovementGoals$Home", "dev.forja.ai.MovementGoals$Curious",
	"dev.forja.ai.MovementGoals$HighGround"}, remap = false)
abstract class MovementPerfMixin {
	@Inject(method = "canUse", at = @At("HEAD"))
	private void bench$in(CallbackInfoReturnable<Boolean> cir) {
		Perf.start(Perf.T.MOVEMENT);
	}

	@Inject(method = "canUse", at = @At("RETURN"))
	private void bench$out(CallbackInfoReturnable<Boolean> cir) {
		Perf.stop(Perf.T.MOVEMENT);
	}
}
