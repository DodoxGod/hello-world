package dev.forja.test.mixin;

import dev.forja.entity.Shockwave;
import dev.forja.test.Perf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One ring's tick on the server, clocked. */
@Mixin(value = Shockwave.class, remap = false)
abstract class ShockwavePerfMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void bench$in(CallbackInfo ci) {
		Perf.start(Perf.T.SHOCKWAVE);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void bench$out(CallbackInfo ci) {
		Perf.stop(Perf.T.SHOCKWAVE);
	}
}
