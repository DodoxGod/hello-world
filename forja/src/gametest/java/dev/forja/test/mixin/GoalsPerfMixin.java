package dev.forja.test.mixin;

import dev.forja.ai.TacticGoal;
import dev.forja.test.Perf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The tactic executor, each tick and each time the goal selector asks whether it wants to run. */
@Mixin(value = TacticGoal.class, remap = false)
abstract class GoalsPerfMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void bench$tickIn(CallbackInfo ci) {
		Perf.start(Perf.T.TACTIC_TICK);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void bench$tickOut(CallbackInfo ci) {
		Perf.stop(Perf.T.TACTIC_TICK);
	}

	@Inject(method = "canUse", at = @At("HEAD"))
	private void bench$useIn(CallbackInfoReturnable<Boolean> cir) {
		Perf.start(Perf.T.TACTIC_USE);
	}

	@Inject(method = "canUse", at = @At("RETURN"))
	private void bench$useOut(CallbackInfoReturnable<Boolean> cir) {
		Perf.stop(Perf.T.TACTIC_USE);
	}
}
