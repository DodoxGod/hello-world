package dev.forja.test.mixin;

import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.test.Perf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clocks round the brains' tick, each mob's thinking and the network's mask (see {@link Perf}). */
@Mixin(value = MobAi.class, remap = false)
abstract class MobAiPerfMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private static void bench$tickIn(ServerLevel level, CallbackInfo ci) {
		Perf.start(Perf.T.MOBAI_TICK);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private static void bench$tickOut(ServerLevel level, CallbackInfo ci) {
		Perf.stop(Perf.T.MOBAI_TICK);
	}

	@Inject(method = "think", at = @At("HEAD"))
	private static void bench$thinkIn(MobMind mind, long now, CallbackInfo ci) {
		Perf.start(Perf.T.THINK);
	}

	@Inject(method = "think", at = @At("RETURN"))
	private static void bench$thinkOut(MobMind mind, long now, CallbackInfo ci) {
		Perf.stop(Perf.T.THINK);
	}

	@Inject(method = "mask(Lnet/minecraft/world/entity/Mob;Ldev/forja/ai/MobMind;Lnet/minecraft/world/entity/player/Player;I)[Z", at = @At("HEAD"))
	private static void bench$maskIn(Mob mob, MobMind mind, Player target, int outputs, CallbackInfoReturnable<boolean[]> cir) {
		Perf.start(Perf.T.MASK);
	}

	@Inject(method = "mask(Lnet/minecraft/world/entity/Mob;Ldev/forja/ai/MobMind;Lnet/minecraft/world/entity/player/Player;I)[Z", at = @At("RETURN"))
	private static void bench$maskOut(Mob mob, MobMind mind, Player target, int outputs, CallbackInfoReturnable<boolean[]> cir) {
		Perf.stop(Perf.T.MASK);
	}
}
