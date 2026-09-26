package dev.forja.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Over the crosshair: how far a charged blow has got (grey until it would strike at all, gold once it is
 * worth it, glowing when full), and, after a perfect dodge, a cyan bar running down while the counter is
 * open. Both are moments where the timing is the whole point, so they sit where the eyes already are.
 */
public final class ChargeHud implements HudElement {
	private static final int WIDTH = 25;
	private static final int HEIGHT = 2;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float charge = CombatClient.localCharge(partial);
		boolean counter = CombatAnims.counterOpen(minecraft.player.getId(), partial);
		if (charge < 0.0F && !counter) {
			return;
		}
		int x = graphics.guiWidth() / 2 - WIDTH / 2;
		int y = graphics.guiHeight() / 2 - 11;
		float time = (Util.getMillis() % 60000L) / 1000.0F;
		HudBars.well(graphics, x, y, WIDTH, HEIGHT);
		if (charge >= 0.0F) {
			float min = (float) dev.forja.combat.CombatConfig.get().chargeMinShare;
			int colour = charge >= 1.0F ? 0xFFD75E : charge >= min ? 0xE8B04A : 0x9A9A9A;
			HudBars.fill(graphics, x, x + Math.round(WIDTH * Mth.clamp(charge, 0.0F, 1.0F)), y, HEIGHT, colour, time, x, WIDTH);
			if (charge >= 1.0F) {
				HudBars.halo(graphics, x, y, WIDTH, HEIGHT, colour, 0.5F + 0.5F * Mth.sin(time * 14.0F));
			}
			return;
		}
		HudBars.fill(graphics, x, x + WIDTH, y, HEIGHT, 0x5FD3E8, time, x, WIDTH);
		HudBars.halo(graphics, x, y, WIDTH, HEIGHT, 0x5FD3E8, 0.6F);
	}
}
