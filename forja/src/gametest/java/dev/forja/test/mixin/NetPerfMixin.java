package dev.forja.test.mixin;

import dev.forja.ai.Decision;
import dev.forja.ai.NetBrain;
import dev.forja.test.Perf;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The network's inference and its sampling, clocked. */
@Mixin(value = NetBrain.class, remap = false)
abstract class NetPerfMixin {
	@Inject(method = "forward", at = @At("HEAD"))
	private void bench$forwardIn(float[] input, float[] memory, CallbackInfoReturnable<float[]> cir) {
		Perf.start(Perf.T.FORWARD);
	}

	@Inject(method = "forward", at = @At("RETURN"))
	private void bench$forwardOut(float[] input, float[] memory, CallbackInfoReturnable<float[]> cir) {
		Perf.stop(Perf.T.FORWARD);
	}

	@Inject(method = "sample", at = @At("HEAD"))
	private static void bench$sampleIn(float[] logits, double temperature, RandomSource random, boolean[] mask, CallbackInfoReturnable<Decision> cir) {
		Perf.start(Perf.T.SAMPLE);
	}

	@Inject(method = "sample", at = @At("RETURN"))
	private static void bench$sampleOut(float[] logits, double temperature, RandomSource random, boolean[] mask, CallbackInfoReturnable<Decision> cir) {
		Perf.stop(Perf.T.SAMPLE);
	}
}
