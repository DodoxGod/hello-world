package dev.forja.mixin.client;

import dev.forja.client.SkyMood;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the running event's colour into the sky, and its mood into the stars.
 *
 * <p>The sky's colour and star brightness are both settled in one place, once a frame, just before
 * anything is drawn — so this waits for the game to work out what the sky was going to be and then
 * bends it, rather than trying to draw a second sky over the top of the first.
 *
 * <p>The one thing it does not bend but puts out is the sun or the moon an event draws its own of:
 * see {@link SkyMood#hidesMoon}. Their brightness is the alpha the game draws them with, so they fade
 * out as the event's own fades in.
 */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
	/** How much of each body to put out this frame, settled with the rest of the sky's state. */
	@Unique
	private static float forja$moonOut;
	@Unique
	private static float forja$sunOut;

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void forja$eventSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo info) {
		// Where the sun and moon are, for whatever the event wants to hang on them.
		SkyMood.sunAngle = state.sunAngle;
		SkyMood.moonAngle = state.moonAngle;
		forja$moonOut = 0.0F;
		forja$sunOut = 0.0F;
		// The Cementerio entre Estrellas has no sun and no moon: only stars (client/StarYardSky).
		if (level.dimension() == dev.forja.world.StarYard.LEVEL) {
			forja$moonOut = 1.0F;
			forja$sunOut = 1.0F;
			return;
		}
		if (SkyMood.showing() == null) {
			return;
		}
		long time = level.getOverworldClockTime();
		state.skyColor = SkyMood.tintSky(state.skyColor, time, partialTick);
		// A meteor shower with no more stars in it than usual is just a purple night.
		state.starBrightness = Math.min(1.0F, Math.max(SkyMood.starFloor(time), state.starBrightness * SkyMood.starFactor(time)));
		if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
			forja$moonOut = SkyMood.hidesMoon(time);
			forja$sunOut = SkyMood.hidesSun(time);
		}
	}

	@ModifyArg(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SkyRenderer;renderMoon(Lnet/minecraft/world/level/MoonPhase;FLcom/mojang/blaze3d/vertex/PoseStack;)V"),
		index = 1)
	private float forja$putOutMoon(float brightness) {
		return brightness * (1.0F - forja$moonOut);
	}

	@ModifyArg(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSun(FLcom/mojang/blaze3d/vertex/PoseStack;)V"),
		index = 0)
	private float forja$putOutSun(float brightness) {
		return brightness * (1.0F - forja$sunOut);
	}
}
