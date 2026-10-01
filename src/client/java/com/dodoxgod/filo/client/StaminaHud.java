package com.dodoxgod.filo.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Barra fina de estamina sobre la comida. Desaparece cuando lleva un rato llena. */
public final class StaminaHud {
	private static final int WIDTH = 81;
	private static final int HEIGHT = 3;

	private StaminaHud() {
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden || !ClientStamina.known()) return;
		if (client.player.isCreative() || client.player.isSpectator()) return;

		float ratio = ClientStamina.max() <= 0 ? 1f : ClientStamina.value() / ClientStamina.max();
		if (ratio >= 1f && ClientStamina.millisSinceChange() > 1500) return;

		int x = context.getScaledWindowWidth() / 2 + 10;
		int y = context.getScaledWindowHeight() - 52;
		int filled = Math.round(WIDTH * Math.max(0f, Math.min(1f, ratio)));

		context.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xAA000000);
		context.fill(x + WIDTH - filled, y, x + WIDTH, y + HEIGHT, color(ratio));
	}

	private static int color(float ratio) {
		if (ratio > 0.6f) return 0xFF7FD34E;
		if (ratio > 0.3f) return 0xFFE8C547;
		return 0xFFE0533D;
	}
}
