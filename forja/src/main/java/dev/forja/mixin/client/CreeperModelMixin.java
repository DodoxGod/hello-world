package dev.forja.mixin.client;

import dev.forja.client.MobGaits;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A creeper's quick steps at a run and its feet up on a hop back; see {@link MobGaits#creeperLegs}. */
@Mixin(CreeperModel.class)
abstract class CreeperModelMixin {
	@Shadow @Final private ModelPart head;
	@Shadow @Final private ModelPart rightHindLeg;
	@Shadow @Final private ModelPart leftHindLeg;
	@Shadow @Final private ModelPart rightFrontLeg;
	@Shadow @Final private ModelPart leftFrontLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;)V", at = @At("TAIL"))
	private void forja$gait(CreeperRenderState state, CallbackInfo ci) {
		MobGaits.Gait gait = MobGaits.of(state);
		if (gait != null) {
			MobGaits.creeperLegs(this.head, this.rightHindLeg, this.leftHindLeg, this.rightFrontLeg, this.leftFrontLeg,
				state.walkAnimationPos, gait);
		}
	}
}
