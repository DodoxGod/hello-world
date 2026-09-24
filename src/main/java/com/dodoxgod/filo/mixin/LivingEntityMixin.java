package com.dodoxgod.filo.mixin;

import com.dodoxgod.filo.combat.CombatHooks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Shadow
	protected abstract void damageArmor(DamageSource source, float amount);

	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void filo$interceptDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient) return;
		if (self instanceof PlayerEntity player && CombatHooks.interceptPlayerDamage(player, source, amount)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "applyArmorToDamage", at = @At("HEAD"), cancellable = true)
	private void filo$applyArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient) return;
		cir.setReturnValue(CombatHooks.applyArmor(self, source, amount, scaled -> this.damageArmor(source, scaled)));
	}
}
