package dev.forja.mixin.client;

import dev.forja.client.HudLayout;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla's two lines of text over the hotbar — the held item's name and the action-bar message — go up over the
 * mod's bars while any of them is showing (client/HudLayout.textLift), instead of being written across them. With
 * no bar of the mod on screen they stay exactly where vanilla puts them.
 */
@Mixin(Hud.class)
abstract class HudTextMixin {
	@ModifyArg(method = "extractSelectedItemName", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"), index = 3)
	private int forja$liftItemName(int y) {
		return y - HudLayout.textLift();
	}

	@ModifyArg(method = "extractOverlayMessage", at = @At(value = "INVOKE",
		target = "Lorg/joml/Matrix3x2fStack;translate(FF)Lorg/joml/Matrix3x2f;"), index = 1)
	private float forja$liftOverlayMessage(float y) {
		return y - HudLayout.textLift();
	}
}
