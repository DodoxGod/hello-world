package dev.forja.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.client.CombatPoses;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First person only poses bows and crossbows that are exactly Items.BOW or Items.CROSSBOW: aiming a
 * loaded crossbow, winding it up, hiding the other hand while drawing. Forged ones count too.
 *
 * <p>It also swings each weapon its own way (see {@link CombatPoses#firstPersonSwing}) and moves the
 * hands for the player's parries, broken guards and dodges.
 */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
	/** The stack in the hand being drawn right now; swingArm is not told. Render thread only. */
	@Unique
	private ItemStack forja$drawing = ItemStack.EMPTY;

	@WrapOperation(
		method = {"evaluateWhichHandsToRender", "selectionUsingItemWhileHoldingBowLike", "isChargedCrossbow"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private static boolean forja$bowLikeSelection(ItemStack stack, Object item, Operation<Boolean> original) {
		return original.call(stack, item) || forja$bowLike(stack, item);
	}

	@WrapOperation(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private boolean forja$bowLikeArm(ItemStack stack, Object item, Operation<Boolean> original) {
		return original.call(stack, item) || forja$bowLike(stack, item);
	}

	private static boolean forja$bowLike(ItemStack stack, Object item) {
		return item == Items.CROSSBOW ? stack.getItem() instanceof CrossbowItem : item == Items.BOW && stack.getItem() instanceof BowItem;
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void forja$remember(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
		ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
		CallbackInfo ci) {
		this.forja$drawing = itemStack;
	}

	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void forja$weaponSwing(float attack, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
		if (CombatPoses.firstPersonSwing(poseStack, this.forja$drawing, attack, invert)) {
			ci.cancel();
		}
	}

	@Inject(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")
	)
	private void forja$combatExtras(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
		ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
		CallbackInfo ci) {
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		CombatPoses.firstPersonExtras(poseStack, player, itemStack, arm == HumanoidArm.RIGHT ? 1 : -1, frameInterp);
	}
}
