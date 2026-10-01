package dev.forja.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.client.HeldFlail;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells a flail in hand when it is being drawn in the world, where its chain hangs and swings, and not
 * on the inventory's doll, which draws the same entity again in a screen with nothing to hang it in.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	@Inject(method = "submitEntities", at = @At("HEAD"))
	private void forja$entitiesStart(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output, CallbackInfo ci) {
		HeldFlail.drawingWorld(true);
	}

	@Inject(method = "submitEntities", at = @At("RETURN"))
	private void forja$entitiesEnd(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output, CallbackInfo ci) {
		HeldFlail.drawingWorld(false);
	}
}
