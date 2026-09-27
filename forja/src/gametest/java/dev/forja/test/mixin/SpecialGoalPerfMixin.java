package dev.forja.test.mixin;

import dev.forja.ai.SpecialGoal;
import dev.forja.test.Perf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The rules' specials: whether one starts (every time the selector asks), and the one under way. */
@Mixin(value = SpecialGoal.class, remap = false)
abstract class SpecialGoalPerfMixin {
	@Inject(method = "canUse", at = @At("HEAD"))
	private void bench$useIn(CallbackInfoReturnable<Boolean> cir) {
		Perf.start(Perf.T.SPECIAL_USE);
	}

	@Inject(method = "canUse", at = @At("RETURN"))
	private void bench$useOut(CallbackInfoReturnable<Boolean> cir) {
		Perf.stop(Perf.T.SPECIAL_USE);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void bench$tickIn(CallbackInfo ci) {
		Perf.start(Perf.T.SPECIAL_TICK);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void bench$tickOut(CallbackInfo ci) {
		Perf.stop(Perf.T.SPECIAL_TICK);
	}
}
