package dev.forja.mixin.client;

import dev.forja.client.LadderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.DifficultyButtons;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The world options' difficulty button (Options in the pause menu) goes on to Extremo (LadderClient). */
@Mixin(DifficultyButtons.class)
abstract class DifficultyButtonsMixin {
	@Inject(method = "create", at = @At("RETURN"))
	private static void forja$ladder(Minecraft minecraft, Level level, Screen screen, CallbackInfoReturnable<DifficultyButtons> cir) {
		LadderClient.attachInGame(cir.getReturnValue().difficultyButton());
	}
}
