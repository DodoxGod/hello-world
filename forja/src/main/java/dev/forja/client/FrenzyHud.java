package dev.forja.client;

import dev.forja.upgrade.Frenzy;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The frenzy bar: one notch per blow of the combo, over the hotbar. It used to be a boss bar, which
 * meant it fought the Fallen Smith for the top of the screen; this sits where it belongs.
 */
public final class FrenzyHud implements HudElement {
	private static final int WIDTH = 92;
	private static final int HEIGHT = 5;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return;
		}
		int hits = Frenzy.shownHits(player);
		if (hits <= 0) {
			return;
		}
		int x = (graphics.guiWidth() - WIDTH) / 2;
		int y = graphics.guiHeight() - 56;
		float time = (net.minecraft.util.Util.getMillis() % 60000L) / 1000.0F;
		HudBars.well(graphics, x, y, WIDTH, HEIGHT);
		int notch = WIDTH / Frenzy.MAX_HITS;
		boolean full = hits >= Frenzy.MAX_HITS;
		for (int i = 0; i < hits; i++) {
			int left = x + i * notch;
			// The bar warms up as the combo climbs: deep orange at the first blow, through yellow,
			// white hot at the top — and at the top the whole of it goes white hot together.
			float heat = full ? 1.0F : i / (float) (Frenzy.MAX_HITS - 1);
			int colour = heat < 0.5F ? mix(0xE8501E, 0xFFB43C, heat * 2.0F) : mix(0xFFB43C, 0xFFF0C0, heat * 2.0F - 1.0F);
			HudBars.fill(graphics, left, left + notch - 1, y, HEIGHT, colour, time, x, WIDTH);
		}
		if (full) {
			HudBars.halo(graphics, x, y, WIDTH, HEIGHT, 0xFFD070, 0.6F + 0.4F * net.minecraft.util.Mth.sin(time * 9.0F));
		}
		graphics.text(
			minecraft.font, Component.translatable("gui.forja.frenesi", hits, Frenzy.MAX_HITS),
			x + WIDTH + 7, y - 1, full ? 0xFFFFF0C0 : 0xFFFFC857, true
		);
	}

	private static int mix(int from, int to, float share) {
		int r = Math.round(net.minecraft.util.Mth.lerp(share, from >> 16 & 255, to >> 16 & 255));
		int g = Math.round(net.minecraft.util.Mth.lerp(share, from >> 8 & 255, to >> 8 & 255));
		int b = Math.round(net.minecraft.util.Mth.lerp(share, from & 255, to & 255));
		return r << 16 | g << 8 | b;
	}
}
