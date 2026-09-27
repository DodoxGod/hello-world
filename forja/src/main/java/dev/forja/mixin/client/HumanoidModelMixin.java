package dev.forja.mixin.client;

import dev.forja.client.CombatPoses;
import dev.forja.client.MobGaits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swings that fit the weapon in third person, the shield thrown out on a parry, and a monster's run and
 * leap ({@link MobGaits}), posed just before the swing so a blow thrown on the run starts from the run.
 */
@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
	@Inject(
		method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V")
	)
	private void forja$gait(HumanoidRenderState state, CallbackInfo ci) {
		MobGaits.poseHumanoid((HumanoidModel<?>) (Object) this, state);
	}

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
