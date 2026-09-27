package dev.forja.test.mixin;

import dev.forja.test.Perf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every particle burst the server sends. The short overload hands over to this one, so each burst is
 * counted once; each is one packet to every player close enough to see it.
 */
@Mixin(ServerLevel.class)
abstract class ParticlesCountMixin {
	@Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDIDDDD)I", at = @At("HEAD"))
	private <T extends ParticleOptions> void bench$sent(T options, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z,
		int count, double dx, double dy, double dz, double speed, CallbackInfoReturnable<Integer> cir) {
		Perf.particles(options, count);
	}
}
