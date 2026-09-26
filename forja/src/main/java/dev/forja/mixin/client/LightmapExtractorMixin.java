package dev.forja.mixin.client;

import dev.forja.client.SkyMood;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the running event colour and dim the light the sky throws on the ground.
 *
 * <p>At the end of {@code extract}, once the game has settled what the light was going to be: the
 * same bargain the sky and the fog mixins make, and for the same reason — bend what is there rather
 * than compute a second one beside it.
 */
@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapExtractorMixin {
	@Inject(method = "extract", at = @At("RETURN"))
	private void forja$eventLight(LightmapRenderState state, float partialTick, CallbackInfo info) {
		SkyMood.light(state, partialTick);
	}
}
