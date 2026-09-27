package dev.forja.test.mixin;

import dev.forja.test.Perf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The zombies' digging and climbing goal: asked every other tick whether it is needed. */
@Mixin(targets = "dev.forja.ai.MovementGoals$Builder", remap = false)
abstract class BuilderPerfMixin {
	@Inject(method = "canUse", at = @At("HEAD"))
	private void bench$useIn(CallbackInfoReturnable<Boolean> cir) {
		Perf.start(Perf.T.BUILDER);
	}

	@Inject(method = "canUse", at = @At("RETURN"))
	private void bench$useOut(CallbackInfoReturnable<Boolean> cir) {
		Perf.stop(Perf.T.BUILDER);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void bench$tickIn(CallbackInfo ci) {
		Perf.start(Perf.T.BUILDER);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void bench$tickOut(CallbackInfo ci) {
		Perf.stop(Perf.T.BUILDER);
	}
}
