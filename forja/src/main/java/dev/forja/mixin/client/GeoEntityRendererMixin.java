package dev.forja.mixin.client;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.client.CombatPoses;
import dev.forja.client.MobGaits;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forja's own monsters are GeckoLib models, which never go through LivingEntityRenderer: their run and leap
 * ({@link MobGaits#applyBody}) lean them here, before they are turned to face their way, as vanilla's are, and
 * so does the fight ({@link CombatPoses}: the flinch from a blow, the lean into one of their own, the sway of a
 * stagger), less the bigger they are.
 */
@Mixin(GeoEntityRenderer.class)
abstract class GeoEntityRendererMixin {
	/** Up to this tall a body leans the whole way; a taller one leans as much less as it is taller. */
	private static final float FULL_LEAN_HEIGHT = 2.4F;

	@Inject(method = "applyRotations", at = @At("HEAD"))
	private void forja$gait(RenderPassInfo<?> info, PoseStack poseStack, float nativeScale, CallbackInfo ci) {
		if (info.renderState() instanceof net.minecraft.client.renderer.entity.state.EntityRenderState state) {
			float height = Math.max(FULL_LEAN_HEIGHT, state.boundingBoxHeight);
			CombatPoses.applyLean(state.getData(CombatPoses.KEY), poseStack, FULL_LEAN_HEIGHT / height);
			MobGaits.applyBody(MobGaits.of(state), poseStack);
		}
	}
}
