package dev.forja.mixin.client;

import dev.forja.client.SkyMood;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * And the same colour into the fog, weaker.
 *
 * <p>Without this the horizon stays the ordinary night blue while everything above it has gone red,
 * and the join between the two is a hard line across the world. The fog takes a smaller share than
 * the sky on purpose: it sits right in front of the eye, so the same amount of colour there is
 * exhausting to look at for the hundred seconds an event lasts.
 */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void forja$eventFog(Camera camera, int renderDistance, DeltaTracker delta, float rain,
		ClientLevel level, CallbackInfoReturnable<FogData> info) {
		FogData data = info.getReturnValue();
		if (data == null || data.color == null) {
			return;
		}
		if (SkyMood.showing() != null) {
			long time = level.getOverworldClockTime();
			SkyMood.tintFog(data.color, time, delta.getGameTimeDeltaPartialTick(false));
			// And closer, for the ones that are weather: a blizzard is not being able to see.
			float reach = SkyMood.fogReach(SkyMood.showing());
			float weight = SkyMood.weight(time);
			weight *= SkyMood.exposure();
			if (reach > 0.0F && weight > 0.0F) {
				data.environmentalStart = net.minecraft.util.Mth.lerp(weight, data.environmentalStart, Math.min(data.environmentalStart, reach * 0.08F));
				data.environmentalEnd = net.minecraft.util.Mth.lerp(weight, data.environmentalEnd, Math.min(data.environmentalEnd, reach));
			}
		}
		// How far anything can be seen before the fog starts on it: the sky is hung inside that.
		SkyMood.clearTo = data.renderDistanceStart;
	}
}
