package dev.forja.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The monsters' ears (ai/Hearing, red_mob_v4 block P): every game event a player sets off goes past here, and the ones
 * monsters listen for (steps, blocks, eating, shots, blows) are kept for the mobs hunting that player.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelGameEventMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
		at = @At("HEAD"))
	private void forja$heard(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
		if (context.sourceEntity() instanceof net.minecraft.world.entity.player.Player) {
			dev.forja.ai.Hearing.onEvent(event, pos, context.sourceEntity());
		}
	}
}
