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

import com.llamalad7.mixinextras.sugar.Local;

/**
 * And the same colour into the fog, weaker.
 *
 * <p>Without this the horizon stays the ordinary night blue while everything above it has gone red,
 * and the join between the two is a hard line across the world. The fog takes a smaller share than
 * the sky on purpose: it sits right in front of the eye, so the same amount of colour there is
 * exhausting to look at for the hundred seconds an event lasts.
 *
 * <p>It bends the fog just <i>before</i> the method returns, once the last field is set, and not at the
 * return itself. Sodium copies the fog out for its own terrain shader at the return, and whichever of
 * the two ran first there decided whether Sodium's terrain got the event's fog or the plain one: under
 * Sodium the blizzard could close in on the sky and the mobs while the ground stayed clear to the
 * horizon.
 */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F",
		opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void forja$eventFog(Camera camera, int renderDistance, DeltaTracker delta, float rain,
		ClientLevel level, CallbackInfoReturnable<FogData> info, @Local FogData data) {
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
		// The graveyard's ash haze and the glow of the void's floor (client/StarYardSky).
		if (level.dimension() == dev.forja.world.StarYard.LEVEL) {
			dev.forja.client.StarYardSky.fog(data, camera);
		}
		// How far anything can be seen before the fog starts on it: the sky is hung inside that.
		SkyMood.clearTo = data.renderDistanceStart;
	}
}
