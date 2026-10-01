package dev.forja.mixin.client;

import dev.forja.client.BossBarArt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the Fallen Smith's bar for his own, and leaves every other boss's alone.
 *
 * <p>At the head of the method that draws one bar, so the name over it, the spacing between bars and
 * everything else about the overlay stay vanilla's: only the stripe itself is replaced.
 */
@Mixin(BossHealthOverlay.class)
abstract class BossHealthOverlayMixin {
	@Inject(method = "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V", at = @At("HEAD"), cancellable = true)
	private void forja$smithBar(GuiGraphicsExtractor graphics, int x, int y, BossEvent event, CallbackInfo info) {
		if (BossBarArt.isSmith(event)) {
			BossBarArt.draw(graphics, x, y, event);
			info.cancel();
		}
	}
}
