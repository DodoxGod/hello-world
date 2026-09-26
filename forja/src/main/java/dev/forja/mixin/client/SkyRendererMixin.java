package dev.forja.mixin.client;

import dev.forja.client.SkyMood;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the running event's colour into the sky, and its mood into the stars.
 *
 * <p>The sky's colour and star brightness are both settled in one place, once a frame, just before
 * anything is drawn — so this waits for the game to work out what the sky was going to be and then
 * bends it, rather than trying to draw a second sky over the top of the first.
 */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void forja$eventSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo info) {
		// Where the sun and moon are, for whatever the event wants to hang on them.
		SkyMood.sunAngle = state.sunAngle;
		SkyMood.moonAngle = state.moonAngle;
		if (SkyMood.showing() == null) {
			return;
		}
		long time = level.getOverworldClockTime();
		state.skyColor = SkyMood.tintSky(state.skyColor, time, partialTick);
		// A meteor shower with no more stars in it than usual is just a purple night.
		state.starBrightness = Math.min(1.0F, Math.max(SkyMood.starFloor(time), state.starBrightness * SkyMood.starFactor(time)));
	}
}
