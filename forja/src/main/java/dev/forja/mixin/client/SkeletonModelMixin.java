package dev.forja.mixin.client;

import dev.forja.client.CombatPoses;
import dev.forja.client.MobGaits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An angry skeleton with a blade (a wither skeleton's sword, or a forged weapon given to one) poses both
 * arms out in front after the swing is set up; see {@link CombatPoses#reapplyAfterSkeletonArms}. At a run
 * it carries the blade low instead, and leaps like any other humanoid ({@link MobGaits#reposeArms}).
 */
@Mixin(SkeletonModel.class)
abstract class SkeletonModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/SkeletonRenderState;)V", at = @At("TAIL"))
	private void forja$weaponSwing(SkeletonRenderState state, CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		if (state.isAggressive && !state.isHoldingBow) {
			MobGaits.reposeArms(model, state, true);
		}
		CombatPoses.reapplyAfterSkeletonArms(model.leftArm, model.rightArm, state);
	}
}
