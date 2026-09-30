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

	/**
	 * A blow struck by hand throws further behind a counterweighted handle and less far behind a light one
	 * (combat/Grip): the base push every hit gives and the sprint's extra push alike.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double forja$gripKnockback(double strength,
		@com.llamalad7.mixinextras.sugar.Local(argsOnly = true) net.minecraft.world.damagesource.DamageSource source) {
		if (source != null && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
			&& attacker != (Object) this) {
			return strength * dev.forja.combat.Grip.knockback(attacker.getMainHandItem());
		}
		return strength;
	}

	/** Jumping costs stamina (combat/Stamina.onJump); the server sees a player's jump here. */
	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void forja$jumpCostsStamina(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if ((Object) this instanceof net.minecraft.server.level.ServerPlayer player) {
			dev.forja.combat.Stamina.onJump(player);
		}
	}

	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void forja$resonantSenses(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if ((effect.is(MobEffects.DARKNESS) || effect.is(MobEffects.BLINDNESS)) && TraitEffects.resonantArmor((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
