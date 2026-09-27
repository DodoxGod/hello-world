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

	/** A champion gives its legend and nothing else: no rotten flesh beside it (world/Elites drops the legend). */
	@Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
		at = @At("HEAD"), cancellable = true)
	private void forja$championGivesOnlyItsLegend(net.minecraft.server.level.ServerLevel level,
		net.minecraft.world.damagesource.DamageSource source, boolean playerKilled, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (dev.forja.world.Elites.isElite((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void forja$resonantSenses(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if ((effect.is(MobEffects.DARKNESS) || effect.is(MobEffects.BLINDNESS)) && TraitEffects.resonantArmor((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
