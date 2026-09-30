package dev.forja.mixin;

import dev.forja.ai.BlazePilot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While its own network drives a blaze (red_blaze_v1), its height is the executor's (dev.forja.ai.BlazePilot): vanilla's
 * lift towards a player above its eyes (Blaze.customServerAiStep) is left out, as the simulator has none. Without the
 * network, or on the rules, the blaze is vanilla's.
 */
@Mixin(Blaze.class)
public abstract class BlazeMixin {
	@Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
	private void forja$networkFlies(ServerLevel level, CallbackInfo ci) {
		if (BlazePilot.drives((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
