package dev.forja.mixin.client;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.client.MobGaits;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forja's own monsters are GeckoLib models, which never go through LivingEntityRenderer: their run and leap
 * ({@link MobGaits#applyBody}) lean them here, before they are turned to face their way, as vanilla's are.
 */
@Mixin(GeoEntityRenderer.class)
abstract class GeoEntityRendererMixin {
	@Inject(method = "applyRotations", at = @At("HEAD"))
	private void forja$gait(RenderPassInfo<?> info, PoseStack poseStack, float nativeScale, CallbackInfo ci) {
		if (info.renderState() instanceof net.minecraft.client.renderer.entity.state.EntityRenderState state) {
			MobGaits.applyBody(MobGaits.of(state), poseStack);
		}
	}
}
