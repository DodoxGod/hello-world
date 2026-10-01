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
 *
 * <p>Its max is the player's own (Aguante raises it), and what kills have left waiting to flow in
 * (combat/KillFlow) is drawn as a pale stretch past the end of the green, which the green eats as it comes.
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
		if (player == null || minecraft.gui.hud.isHidden() || player.isSpectator() || player.isCreative() || !cfg.enabled || !cfg.stamina) {
			return;
		}
		float max = Math.max(1F, Stamina.max(player));
		float value = player.getAttachedOrElse(Stamina.VALUE, max);
		float pending = Stamina.pending(player);
		long now = net.minecraft.util.Util.getMillis();
		if (value != last || pending > 0.0F) {
			last = value;
			changedAt = now;
		}
		float ratio = Math.max(0F, Math.min(1F, value / max));
		int x = graphics.guiWidth() / 2 + 10;
		int y = graphics.guiHeight() - 64;
		if (ratio >= 1F && now - changedAt > LINGER) {
			return;
		}
		float time = (now % 60000L) / 1000.0F;
		HudBars.well(graphics, x, y, WIDTH, HEIGHT);
		int filled = Math.round(WIDTH * ratio);
		int colour = ratio > 0.6F ? 0x7FD34E : ratio > 0.3F ? 0xE8C547 : 0xE0533D;
		HudBars.fill(graphics, x + WIDTH - filled, x + WIDTH, y, HEIGHT, colour, time, x, WIDTH);
		// The bar fills from the right, so what is waiting sits to the left of the green.
		int waiting = Math.round(WIDTH * Math.max(0F, Math.min(1F, pending / max)));
		if (waiting > 0) {
			int to = x + WIDTH - filled;
			int from = Math.max(x, to - Math.max(1, waiting));
			int alpha = 150 + Math.round(70 * net.minecraft.util.Mth.sin(time * 7.0F));
			graphics.fill(from, y, to, y + HEIGHT, alpha << 24 | 0xD8F5C0);
			graphics.fill(from, y, to, y + 1, 0xE0FFFFFF);
		}
	}
}
