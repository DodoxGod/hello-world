package dev.forja.mixin.client;

import dev.forja.client.MobGaits;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A spider's legs at a run and through a pounce; see {@link MobGaits#spiderLegs}. */
@Mixin(SpiderModel.class)
abstract class SpiderModelMixin {
	@Shadow @Final private ModelPart head;
	@Shadow @Final private ModelPart rightHindLeg;
	@Shadow @Final private ModelPart leftHindLeg;
	@Shadow @Final private ModelPart rightMiddleHindLeg;
	@Shadow @Final private ModelPart leftMiddleHindLeg;
	@Shadow @Final private ModelPart rightMiddleFrontLeg;
	@Shadow @Final private ModelPart leftMiddleFrontLeg;
	@Shadow @Final private ModelPart rightFrontLeg;
	@Shadow @Final private ModelPart leftFrontLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)V", at = @At("TAIL"))
	private void forja$gait(LivingEntityRenderState state, CallbackInfo ci) {
		MobGaits.Gait gait = MobGaits.of(state);
		if (gait != null) {
			MobGaits.spiderLegs(this.head,
				new ModelPart[] {this.rightHindLeg, this.rightMiddleHindLeg, this.rightMiddleFrontLeg, this.rightFrontLeg},
				new ModelPart[] {this.leftHindLeg, this.leftMiddleHindLeg, this.leftMiddleFrontLeg, this.leftFrontLeg},
				state.walkAnimationPos, state.walkAnimationSpeed, gait);
		}
	}
}
