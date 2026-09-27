package dev.forja.test.mixin;

import dev.forja.combat.AttackTokens;
import dev.forja.test.Perf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** How often the turns are asked about: counted, not timed (each call is a map lookup). */
@Mixin(value = AttackTokens.class, remap = false)
abstract class HelpersCountMixin {
	@Inject(method = "tryAcquire", at = @At("HEAD"))
	private static void bench$acquire(LivingEntity target, Mob mob, int max, CallbackInfoReturnable<Boolean> cir) {
		Perf.count(Perf.C.TOKENS_ACQUIRE);
	}

	@Inject(method = "free", at = @At("HEAD"))
	private static void bench$free(LivingEntity target, int max, CallbackInfoReturnable<Boolean> cir) {
		Perf.count(Perf.C.TOKENS_FREE);
	}

	@Inject(method = "holds", at = @At("HEAD"))
	private static void bench$holds(LivingEntity target, Mob mob, CallbackInfoReturnable<Boolean> cir) {
		Perf.count(Perf.C.TOKENS_HOLDS);
	}
}
