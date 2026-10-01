package dev.forja.mixin.client;

import dev.forja.client.LadderClient;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world creation screen's difficulty button (its Game tab) goes on to Extremo, and in hardcore toggles Difícil and
 * Extremo (LadderClient.attachCreation). The button is found among the tab's widgets by its value, a Difficulty.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
abstract class CreateWorldGameTabMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	@SuppressWarnings("unchecked")
	private void forja$ladder(CreateWorldScreen screen, CallbackInfo ci) {
		((GridLayoutTab) (Object) this).visitChildren(widget -> {
			if (widget instanceof CycleButton<?> button && button.getValue() instanceof Difficulty) {
				LadderClient.attachCreation((CycleButton<Difficulty>) button, screen.getUiState());
			}
		});
	}
}
