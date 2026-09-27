package dev.forja.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.client.CombatPoses;
import dev.forja.client.HeldFlail;
import dev.forja.client.HeldTome;
import dev.forja.client.WornGauntlets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First person only poses bows and crossbows that are exactly Items.BOW or Items.CROSSBOW: aiming a
 * loaded crossbow, winding it up, hiding the other hand while drawing. Forged ones count too.
 *
 * <p>It also swings each weapon its own way (see {@link CombatPoses#firstPersonSwing}), throws the other
 * fist when the gauntlets punch with it, and moves the hands for the player's parries, broken guards and
 * dodges. The gauntlets show as the gauntleted hand rather than as a held item, the tome as a book, and
 * the flail as a haft with its ball on a chain.
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

	@Shadow
	private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight,
		float attackValue, HumanoidArm arm) {
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void forja$remember(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
		ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
		CallbackInfo ci) {
		this.forja$drawing = itemStack;
		// With the gauntlets on, the other hand shows too while it is empty (first person never draws an
		// empty off hand): at its rest, on guard, and thrown forward for the gauntlets' second punch in a row.
		// An off hand holding something (a shield, a torch) keeps showing that instead.
		if (hand == InteractionHand.OFF_HAND && itemStack.isEmpty() && !player.isInvisible() && !player.isScoping()) {
			HumanoidArm arm = player.getMainArm().getOpposite();
			poseStack.pushPose();
			if (CombatPoses.firstPersonOtherFist(player, frameInterp, poseStack, arm == HumanoidArm.RIGHT ? 1 : -1)) {
				this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, 0.0F, arm);
			}
			poseStack.popPose();
		}
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

	/**
	 * The gauntlets, the tome and the flail in first person. The pose here is the held item's, already moved
	 * by the arm and the blow; the gauntlets draw the hand itself there instead (with the gauntlet over it,
	 * see AvatarRendererMixin), the tome draws the book and the flail its haft, chain and ball.
	 */
	@WrapOperation(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")
	)
	private void forja$gearInHand(ItemInHandRenderer self, LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector, int lightCoords, Operation<Void> original) {
		boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
		if (WornGauntlets.is(stack) && context.firstPerson()) {
			// From where the item is held back to where the bare hand starts, so the fist is where the
			// item would be and moves as it would: vanilla puts the two 0.08 apart.
			int invert = left ? -1 : 1;
			poseStack.pushPose();
			poseStack.translate(invert * -0.56F, 0.52F, 0.72F);
			this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, 0.0F, 0.0F, left ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
			poseStack.popPose();
		} else if (HeldTome.is(stack) && context.firstPerson()) {
			float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			HeldTome.submitFirstPerson(poseStack, submitNodeCollector, lightCoords, stack, left, HeldTome.reading(entity, partial));
		} else if (HeldFlail.is(stack) && context.firstPerson() && entity instanceof LocalPlayer player) {
			float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			HeldFlail.submitFirstPerson(poseStack, submitNodeCollector, lightCoords, stack, left, player, partial);
		} else {
			original.call(self, entity, stack, context, poseStack, submitNodeCollector, lightCoords);
		}
	}
}
