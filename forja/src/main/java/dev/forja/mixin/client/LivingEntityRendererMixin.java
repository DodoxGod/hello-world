package dev.forja.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.client.CombatPoses;
import dev.forja.client.HeldFlail;
import dev.forja.client.HeldTome;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the fight on every living body: {@link CombatPoses} works the pose out while the entity is at
 * hand, and it is applied before the body is turned to face its way, so a lean is a lean in the world.
 * How far open a tome in its hand is gets worked out here too ({@link HeldTome}), and what a flail in
 * its hand is aimed at ({@link HeldFlail}).
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL")
	)
	private void forja$combatPose(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		state.setData(CombatPoses.KEY, CombatPoses.compute(entity, state, partialTicks));
		state.setData(HeldTome.KEY, HeldTome.reading(entity, partialTicks));
		state.setData(HeldFlail.KEY, HeldFlail.aim(entity, partialTicks));
	}

	@Inject(method = "setupRotations", at = @At("HEAD"))
	private void forja$lean(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		CombatPoses.applyLean(state.getData(CombatPoses.KEY), poseStack);
	}
}
