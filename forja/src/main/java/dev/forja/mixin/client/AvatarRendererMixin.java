package dev.forja.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Players hold any loaded crossbow up to aim, not only Items.CROSSBOW. */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@WrapOperation(
		method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private static boolean forja$loadedCrossbow(ItemStack stack, Object item, Operation<Boolean> original) {
		return original.call(stack, item) || item == Items.CROSSBOW && stack.getItem() instanceof CrossbowItem;
	}
}
