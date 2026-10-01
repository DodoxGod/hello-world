package dev.forja.test.mixin;

import dev.forja.ai.Personality;
import dev.forja.test.Perf;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** How often a mob's trait is looked up in its tags: counted, not timed. */
@Mixin(value = Personality.class, remap = false)
abstract class TraitCountMixin {
	@Inject(method = "trait", at = @At("HEAD"))
	private static void bench$trait(LivingEntity mob, CallbackInfoReturnable<Personality.Trait> cir) {
		Perf.count(Perf.C.TRAIT);
	}
}
