package dev.forja.client;

import dev.forja.forge.Flight;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The flight bar: what is left of a pair of wings. It only shows while the wings matter, and never at
 * all once Aerodinamica has traded the whole reserve away for speed.
 */
public final class FlightHud implements HudElement {
	private static final int WIDTH = 92;
	private static final int HEIGHT = 5;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return;
		}
		ItemStack wings = player.getItemBySlot(EquipmentSlot.CHEST);
		// The bar is always there now. Aerodinamica used to take it off the screen at the full hundred,
		// because at the full hundred there was nothing left to show.
		if (!Flight.isWings(wings)) {
			return;
		}
		int max = Flight.maxTicks(wings);
		int left = Flight.remaining(wings);
		// Out of the way while the wings are whole and you are walking; it appears the moment it matters.
		if (left >= max && !player.isFallFlying()) {
			return;
		}
		float fill = max <= 0 ? 0.0F : Math.clamp(left / (float) max, 0.0F, 1.0F);
		int x = (graphics.guiWidth() - WIDTH) / 2;
		int y = graphics.guiHeight() - 49;
		float time = (net.minecraft.util.Util.getMillis() % 60000L) / 1000.0F;
		HudBars.well(graphics, x, y, WIDTH, HEIGHT);
		boolean low = fill <= 0.25F;
		// Sky blue while there is air under you to spare; red, and beating, once there is not.
		int colour = low ? 0xFF5A40 : 0x7FD8FF;
		HudBars.fill(graphics, x, x + Math.round(WIDTH * fill), y, HEIGHT, colour, time, x, WIDTH);
		if (low) {
			HudBars.halo(graphics, x, y, WIDTH, HEIGHT, 0xFF5A40, 0.5F + 0.5F * net.minecraft.util.Mth.sin(time * 11.0F));
		}
		// A seam down the middle so the bar reads as wings and not as another health bar.
		graphics.fill(x + WIDTH / 2, y, x + WIDTH / 2 + 1, y + HEIGHT, 0x70000000);
		// And the wings themselves, small, at the end of it: which bar this is without having to learn it.
		graphics.pose().pushMatrix();
		graphics.pose().translate(x - 15, y - 3);
		graphics.pose().scale(0.65F, 0.65F);
		graphics.item(wings, 0, 0);
		graphics.pose().popMatrix();
	}
}
