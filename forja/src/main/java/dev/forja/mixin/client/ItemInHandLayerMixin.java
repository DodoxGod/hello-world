package dev.forja.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.client.HeldFlail;
import dev.forja.client.HeldTome;
import dev.forja.client.WornGauntlets;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What a hand holds in third person, for the forged things that are not drawn as the flat item: the
 * gauntlets are worn on both hands instead of held in one, the tome is a book held by its spine, and the
 * flail is a haft with its ball hanging on a chain (see {@link WornGauntlets}, {@link HeldTome} and
 * {@link HeldFlail}). Every armed body goes through here: players, mannequins, armour stands, zombies,
 * skeletons, piglins.
 */
@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerMixin {
	@Inject(
		method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
		at = @At("HEAD")
	)
	private void forja$wornGauntlets(PoseStack poseStack, SubmitNodeCollector collector, int light, ArmedEntityRenderState state, float yRot,
		float xRot, CallbackInfo ci) {
		if (!(((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model)) {
			return;
		}
		// Both hands, whichever of them holds the pair: the empty one too, which never reaches submitArmWithItem.
		for (HumanoidArm arm : HumanoidArm.values()) {
			ItemStack own = state.getUseItemStackForArm(arm);
			ItemStack gauntlets = WornGauntlets.on(own, state.getUseItemStackForArm(arm.getOpposite()));
			if (!gauntlets.isEmpty()) {
				poseStack.pushPose();
				model.root().translateAndRotate(poseStack);
				WornGauntlets.submit(poseStack, collector, light, state.outlineColor, model.getArm(arm), arm == HumanoidArm.LEFT, gauntlets);
				poseStack.popPose();
			}
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void forja$heldGear(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack itemStack, HumanoidArm arm, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		Object model = ((RenderLayer<?, ?>) (Object) this).getParentModel();
		if (model instanceof HumanoidModel<?> && WornGauntlets.is(itemStack)) {
			// Worn on the hand already (above); nothing is held.
			ci.cancel();
		} else if (HeldTome.is(itemStack)) {
			poseStack.pushPose();
			forja$translateToHand(model, state, arm, poseStack);
			HeldTome.submitInHand(poseStack, collector, light, state.outlineColor, itemStack, arm == HumanoidArm.LEFT, state.getData(HeldTome.KEY));
			poseStack.popPose();
			ci.cancel();
		} else if (HeldFlail.is(itemStack)) {
			poseStack.pushPose();
			forja$translateToHand(model, state, arm, poseStack);
			HeldFlail.submitInHand(poseStack, collector, light, itemStack, arm, state);
			poseStack.popPose();
			ci.cancel();
		}
	}

	@Unique
	@SuppressWarnings("unchecked")
	private static void forja$translateToHand(Object model, ArmedEntityRenderState state, HumanoidArm arm, PoseStack poseStack) {
		((ArmedModel<ArmedEntityRenderState>) model).translateToHand(state, arm, poseStack);
	}
}
