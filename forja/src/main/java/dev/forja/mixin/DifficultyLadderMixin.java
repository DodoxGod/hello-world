package dev.forja.mixin;

import dev.forja.difficulty.Ladder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's own changes of difficulty and the Extremo flag (difficulty/Ladder): a world taken off HARD by anything
 * (a vanilla client's button, server.properties) loses the flag, which only means something on HARD. The ladder's own
 * changes (the button, /forja dificultad) go through Ladder.apply, which these leave alone.
 */
@Mixin(MinecraftServer.class)
public abstract class DifficultyLadderMixin {
	@Inject(method = "setDifficulty", at = @At("TAIL"))
	private void forja$ladderFollows(Difficulty difficulty, boolean ignoreLock, CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		Ladder.onVanillaChange(server, server.getWorldData().getDifficulty(), false);
	}
}
