package dev.forja.mixin.client;

import dev.forja.client.CombatPoses;
import dev.forja.client.MobGaits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.piglin.PiglinModel;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A piglin raises its blade high between blows, over its warning; see {@link CombatPoses#reapplyAfterPiglinArms}.
 * It keeps it up at a run; a hop back or a charge poses its arms over it ({@link MobGaits#leapArms}).
 */
@Mixin(PiglinModel.class)
abstract class PiglinModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PiglinRenderState;)V", at = @At("TAIL"))
	private void forja$weaponSwing(PiglinRenderState state, CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		MobGaits.leapArms(model, state);
		CombatPoses.reapplyAfterPiglinArms(model.leftArm, model.rightArm, state);
	}
}
