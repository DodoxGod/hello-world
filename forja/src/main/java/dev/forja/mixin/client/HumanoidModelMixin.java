package dev.forja.mixin.client;

import dev.forja.client.CombatPoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swings that fit the weapon in third person, and the shield thrown out on a parry. */
@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
	@Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
	private void forja$weaponSwing(HumanoidRenderState state, CallbackInfo ci) {
		if (CombatPoses.thirdPersonSwing((HumanoidModel<?>) (Object) this, state)) {
			ci.cancel();
		}
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void forja$shieldKick(HumanoidRenderState state, CallbackInfo ci) {
		CombatPoses.Pose pose = state.getData(CombatPoses.KEY);
		if (pose != null) {
			CombatPoses.thirdPersonShieldKick((HumanoidModel<?>) (Object) this, state, pose.shieldKick());
		}
	}
}
