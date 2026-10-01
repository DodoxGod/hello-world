package dev.forja.test.mixin;

import dev.forja.ai.MobMind;
import dev.forja.ai.ObsForja;
import dev.forja.test.Perf;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Forja block of a network's inputs, clocked. */
@Mixin(value = ObsForja.class, remap = false)
abstract class ObsForjaPerfMixin {
	@Inject(method = "full", at = @At("HEAD"))
	private static void bench$in(Mob mob, Player target, MobMind mind, float[] m1, int wanted, CallbackInfoReturnable<float[]> cir) {
		Perf.start(Perf.T.OBS_FORJA);
	}

	@Inject(method = "full", at = @At("RETURN"))
	private static void bench$out(Mob mob, Player target, MobMind mind, float[] m1, int wanted, CallbackInfoReturnable<float[]> cir) {
		Perf.stop(Perf.T.OBS_FORJA);
	}
}
