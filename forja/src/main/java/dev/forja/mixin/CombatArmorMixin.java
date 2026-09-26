package dev.forja.mixin;

import dev.forja.combat.CombatConfig;
import dev.forja.combat.CombatHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The armor formula of the combat overhaul replaces vanilla's; see {@link CombatHooks#afterArmor}. */
@Mixin(LivingEntity.class)
abstract class CombatArmorMixin {
	@Shadow
	protected abstract void hurtArmor(DamageSource source, float damage);

	@Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
	private void forja$combatArmor(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide() || !CombatConfig.get().enabled) {
			return;
		}
		cir.setReturnValue(CombatHooks.afterArmor(self, source, damage, scaled -> this.hurtArmor(source, scaled)));
	}
}
