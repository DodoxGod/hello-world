package dev.forja.test.mixin;

import dev.forja.ai.Decision;
import dev.forja.ai.MobMind;
import dev.forja.ai.RuleBrain;
import dev.forja.test.Perf;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The rule brain's decision, clocked. */
@Mixin(value = RuleBrain.class, remap = false)
abstract class BrainPerfMixin {
	@Inject(method = "decide", at = @At("HEAD"))
	private static void bench$in(MobMind mind, Player target, CallbackInfoReturnable<Decision> cir) {
		Perf.start(Perf.T.RULES);
	}

	@Inject(method = "decide", at = @At("RETURN"))
	private static void bench$out(MobMind mind, Player target, CallbackInfoReturnable<Decision> cir) {
		Perf.stop(Perf.T.RULES);
	}
}
