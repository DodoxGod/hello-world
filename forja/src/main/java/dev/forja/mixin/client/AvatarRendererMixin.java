package dev.forja.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.client.WornGauntlets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Players hold any loaded crossbow up to aim, not only Items.CROSSBOW. And a first-person hand wears the
 * gauntlets when the player has a pair in either hand.
 */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@WrapOperation(
		method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private static boolean forja$loadedCrossbow(ItemStack stack, Object item, Operation<Boolean> original) {
		return original.call(stack, item) || item == Items.CROSSBOW && stack.getItem() instanceof CrossbowItem;
	}

	/** The hand first person draws (empty, or holding a map, or the gauntlets' own fist) gets its gauntlet. */
	@Inject(method = "renderHand", at = @At("TAIL"))
	private void forja$gauntletOnHand(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		ItemStack gauntlets = WornGauntlets.on(player.getMainHandItem(), player.getOffhandItem());
		if (!gauntlets.isEmpty()) {
			PlayerModel model = ((AvatarRenderer<?>) (Object) this).getModel();
			WornGauntlets.submit(poseStack, collector, light, 0, arm, arm == model.leftArm, gauntlets);
		}
	}
}
