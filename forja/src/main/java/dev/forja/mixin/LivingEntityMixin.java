package dev.forja.mixin;

import dev.forja.upgrade.TraitEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two things that have to happen before anything else looks at a blow.
 *
 * <p>Resonante armor keeps Darkness and Blindness from taking hold, and Forja's own monsters do not
 * hurt each other — see {@link dev.forja.world.Truce}, which is where the reasoning lives.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void forja$truce(net.minecraft.server.level.ServerLevel level,
		net.minecraft.world.damagesource.DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (dev.forja.world.Truce.blocks((LivingEntity) (Object) this, source)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void forja$resonantSenses(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if ((effect.is(MobEffects.DARKNESS) || effect.is(MobEffects.BLINDNESS)) && TraitEffects.resonantArmor((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
