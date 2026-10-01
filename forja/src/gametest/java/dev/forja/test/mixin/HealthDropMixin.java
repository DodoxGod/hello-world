package dev.forja.test.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where a watched player's health goes down (CapitanMedidaGameTests): every drop, with the call that made it, so that
 * damage that no hit accounts for can be traced to its source.
 */
@Mixin(LivingEntity.class)
abstract class HealthDropMixin {
	@Inject(method = "setHealth", at = @At("HEAD"))
	private void test$watchDrop(float health, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (dev.forja.test.CapitanMedidaGameTests.watching(self) && health < self.getHealth()) {
			dev.forja.test.CapitanMedidaGameTests.healthDrop(self, self.getHealth() - health);
		}
	}
}
