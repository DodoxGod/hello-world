package dev.forja.test.mixin;

import java.util.List;

import dev.forja.ai.MobMind;
import dev.forja.ai.Squad;
import dev.forja.test.Perf;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The squads' round (slots, roles, rout), clocked. */
@Mixin(value = Squad.class, remap = false)
abstract class SquadPerfMixin {
	@Inject(method = "update", at = @At("HEAD"))
	private static void bench$in(ServerLevel level, List<MobMind> minds, long now, CallbackInfo ci) {
		Perf.start(Perf.T.SQUAD);
	}

	@Inject(method = "update", at = @At("RETURN"))
	private static void bench$out(ServerLevel level, List<MobMind> minds, long now, CallbackInfo ci) {
		Perf.stop(Perf.T.SQUAD);
	}
}
