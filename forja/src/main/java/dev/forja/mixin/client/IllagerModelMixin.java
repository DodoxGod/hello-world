package dev.forja.mixin.client;

import dev.forja.client.MobGaits;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A vindicator's or a pillager's stride at a run and its legs through a leap; see {@link MobGaits#illagerLegs}. */
@Mixin(IllagerModel.class)
abstract class IllagerModelMixin {
	@Shadow @Final private ModelPart head;
	@Shadow @Final private ModelPart rightLeg;
	@Shadow @Final private ModelPart leftLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/IllagerRenderState;)V", at = @At("TAIL"))
	private void forja$gait(IllagerRenderState state, CallbackInfo ci) {
		if (!state.isRiding) {
			MobGaits.illagerLegs(this.head, this.rightLeg, this.leftLeg, MobGaits.of(state));
		}
	}
}
