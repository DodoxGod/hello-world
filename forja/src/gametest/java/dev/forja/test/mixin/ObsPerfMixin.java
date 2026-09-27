package dev.forja.test.mixin;

import java.util.List;

import dev.forja.ai.ObsM1;
import dev.forja.test.Perf;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The simulator's 102 inputs and the search for allies they (and the rules) lean on, clocked. */
@Mixin(value = ObsM1.class, remap = false)
abstract class ObsPerfMixin {
	@Inject(method = "of", at = @At("HEAD"))
	private static void bench$ofIn(Mob mob, Player target, int cooldown, int draw, CallbackInfoReturnable<float[]> cir) {
		Perf.start(Perf.T.OBS_M1);
	}

	@Inject(method = "of", at = @At("RETURN"))
	private static void bench$ofOut(Mob mob, Player target, int cooldown, int draw, CallbackInfoReturnable<float[]> cir) {
		Perf.stop(Perf.T.OBS_M1);
	}

	@Inject(method = "allies", at = @At("HEAD"))
	private static void bench$alliesIn(Mob mob, CallbackInfoReturnable<List<Mob>> cir) {
		Perf.start(Perf.T.ALLIES);
	}

	@Inject(method = "allies", at = @At("RETURN"))
	private static void bench$alliesOut(Mob mob, CallbackInfoReturnable<List<Mob>> cir) {
		Perf.stop(Perf.T.ALLIES);
	}
}
