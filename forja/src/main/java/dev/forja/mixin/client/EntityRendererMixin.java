package dev.forja.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.client.ThreatBadge;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The rank badge over veterans, elites and champions ({@link ThreatBadge}). Hooked on the base renderer
 * rather than LivingEntityRenderer because Forja's own monsters are GeckoLib models, which skip that
 * one but still come through here.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void forja$threat(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (entity instanceof LivingEntity living) {
			state.setData(ThreatBadge.KEY, ThreatBadge.of(living));
		}
	}

	@Inject(method = "submit", at = @At("HEAD"))
	private void forja$badge(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		ThreatBadge.submit(state, poseStack, collector, camera);
	}

	/** The name goes up over the badge rather than through it. */
	@Inject(
		method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER)
	)
	private void forja$liftName(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, int offset, CallbackInfo ci) {
		float lift = ThreatBadge.nameLift(state);
		if (lift > 0.0F) {
			poseStack.translate(0.0F, lift, 0.0F);
		}
	}
}
