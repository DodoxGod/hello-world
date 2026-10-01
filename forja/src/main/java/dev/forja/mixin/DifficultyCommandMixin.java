package dev.forja.mixin;

import dev.forja.difficulty.Ladder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.DifficultyCommand;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * /difficulty keeps working on the ladder (difficulty/Ladder): it names one of vanilla's four, so /difficulty hard
 * means Difícil. On an Extremo world, already HARD, vanilla would answer "already hard" and change nothing; here it
 * takes the Extremo flag off and says so as vanilla does. Extremo itself is /forja dificultad extremo, or the button.
 */
@Mixin(DifficultyCommand.class)
public abstract class DifficultyCommandMixin {
	@Inject(method = "setDifficulty", at = @At("HEAD"), cancellable = true)
	private static void forja$plainVanillaLevel(CommandSourceStack source, Difficulty difficulty, CallbackInfoReturnable<Integer> cir) {
		MinecraftServer server = source.getServer();
		if (difficulty == Difficulty.HARD && server.getWorldData().getDifficulty() == Difficulty.HARD && Ladder.extremo(server)) {
			Ladder.onVanillaChange(server, Difficulty.HARD, true);
			source.sendSuccess(() -> Component.translatable("commands.difficulty.success", difficulty.getDisplayName()), true);
			cir.setReturnValue(0);
		}
	}
}
