package dev.forja.test.mixin;

import dev.forja.entity.FallenSmith;
import dev.forja.test.Perf;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The boss's whole tick (his vanilla body included) and his heavy moves, clocked. */
@Mixin(value = FallenSmith.class, remap = false)
abstract class SmithPerfMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void bench$tickIn(CallbackInfo ci) {
		Perf.start(Perf.T.SMITH_TICK);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void bench$tickOut(CallbackInfo ci) {
		Perf.stop(Perf.T.SMITH_TICK);
	}

	@Inject(method = "heavyMoves", at = @At("HEAD"))
	private void bench$movesIn(ServerLevel level, CallbackInfo ci) {
		Perf.start(Perf.T.SMITH_MOVES);
	}

	@Inject(method = "heavyMoves", at = @At("RETURN"))
	private void bench$movesOut(ServerLevel level, CallbackInfo ci) {
		Perf.stop(Perf.T.SMITH_MOVES);
	}
}
