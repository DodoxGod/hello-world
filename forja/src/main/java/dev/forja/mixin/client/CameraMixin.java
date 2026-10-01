package dev.forja.mixin.client;

import dev.forja.client.ScreenShake;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets {@link ScreenShake} nudge the camera.
 *
 * <p>After {@code alignWithEntity} and nowhere else: that is where the camera has just been pointed
 * the way the player is looking, and everything {@code update} does next — the view matrix, the
 * culling frustum — is worked out from where it points. Turning it any later would draw a shaken view
 * through an unshaken frustum, and the edges of the screen would lose chunks on every knock.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private float xRot;

	@Shadow
	private float yRot;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void forja$shake(float partialTicks, CallbackInfo info) {
		if (ScreenShake.active()) {
			this.setRotation(this.yRot + ScreenShake.yaw(partialTicks), this.xRot + ScreenShake.pitch(partialTicks));
		}
	}
}
