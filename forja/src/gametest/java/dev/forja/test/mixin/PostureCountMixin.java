package dev.forja.test.mixin;

import dev.forja.combat.Posture;
import dev.forja.test.Perf;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** How often posture is read: counted, not timed. */
@Mixin(value = Posture.class, remap = false)
abstract class PostureCountMixin {
	@Inject(method = "isStaggered", at = @At("HEAD"))
	private static void bench$staggered(LivingEntity entity, long now, CallbackInfoReturnable<Boolean> cir) {
		Perf.count(Perf.C.POSTURE_STAGGERED);
	}

	@Inject(method = "fill", at = @At("HEAD"))
	private static void bench$fill(LivingEntity entity, CallbackInfoReturnable<Double> cir) {
		Perf.count(Perf.C.POSTURE_FILL);
	}
}
