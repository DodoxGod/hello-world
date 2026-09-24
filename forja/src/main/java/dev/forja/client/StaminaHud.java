package dev.forja.client;

import dev.forja.combat.CombatConfig;
import dev.forja.combat.Stamina;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * The stamina bar, over the hunger side of the hotbar and above the frenzy bar. It only shows while
 * stamina is being spent or coming back, and goes from green through amber to red as it runs out.
 */
public final class StaminaHud implements HudElement {
	private static final int WIDTH = 81;
	private static final int HEIGHT = 3;
	/** How long a full bar lingers before it hides, in milliseconds. */
	private static final long LINGER = 1500L;

	private float last = -1F;
	private long changedAt;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		CombatConfig cfg = CombatConfig.get();
		if (player == null || player.isSpectator() || player.isCreative() || !cfg.enabled || !cfg.stamina) {
			return;
		}
		float max = Math.max(1F, cfg.staminaMax);
		float value = player.getAttachedOrElse(Stamina.VALUE, max);
		long now = net.minecraft.util.Util.getMillis();
		if (value != last) {
			last = value;
			changedAt = now;
		}
		float ratio = Math.max(0F, Math.min(1F, value / max));
		if (ratio >= 1F && now - changedAt > LINGER) {
			return;
		}
		int x = graphics.guiWidth() / 2 + 10;
		int y = graphics.guiHeight() - 64;
		HudBars.well(graphics, x, y, WIDTH, HEIGHT);
		int filled = Math.round(WIDTH * ratio);
		int colour = ratio > 0.6F ? 0x7FD34E : ratio > 0.3F ? 0xE8C547 : 0xE0533D;
		HudBars.fill(graphics, x + WIDTH - filled, x + WIDTH, y, HEIGHT, colour, (now % 60000L) / 1000.0F, x, WIDTH);
	}
}
