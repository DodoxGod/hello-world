package dev.forja.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * How the mod's own bars over the hotbar are drawn, so that the two of them are the same object.
 *
 * <p>They were two flat rectangles each — a grey one and a coloured one on top of it — which is what a
 * placeholder looks like, and they sat an inch above vanilla's hearts and armour, which are not. A bar
 * here is a dark well with a lit lip and a brass cap at each end, and what fills it is lit from above,
 * darker underneath, with a glint that crosses it now and then: enough to read as a thing made of
 * something, at five pixels tall.
 */
final class HudBars {
	private HudBars() {
	}

	/** The empty bar: the well, its lip, and the caps. */
	static void well(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xD0000000);
		graphics.fill(x, y, x + width, y + height, 0xFF191614);
		graphics.fill(x, y + height - 1, x + width, y + height, 0xFF2A2522);
		// Brass caps, outside the ends, a pixel taller than the bar.
		for (int side = 0; side < 2; side++) {
			int cap = side == 0 ? x - 3 : x + width + 1;
			graphics.fill(cap, y - 2, cap + 2, y + height + 2, 0xFF7A5A22);
			graphics.fill(cap, y - 2, cap + 2, y - 1, 0xFFE2BE6A);
			graphics.fill(cap + (side == 0 ? 0 : 1), y - 1, cap + (side == 0 ? 1 : 2), y + height + 1, 0xFFC89A48);
		}
	}

	/**
	 * One run of filling, from {@code from} to {@code to}, in a colour.
	 *
	 * @param time anything that counts up in seconds: it moves the glint
	 */
	static void fill(GuiGraphicsExtractor graphics, int from, int to, int y, int height, int colour, float time, int x, int width) {
		if (to <= from) {
			return;
		}
		graphics.fillGradient(from, y, to, y + height, 0xFF000000 | lighter(colour, 1.18F), 0xFF000000 | lighter(colour, 0.62F));
		graphics.fill(from, y, to, y + 1, 0xFF000000 | lighter(colour, 1.45F));
		// The glint: a soft band that crosses the whole bar every three seconds and rests in between.
		float phase = (time % 3.0F) / 0.9F;
		if (phase < 1.0F) {
			int centre = x + Math.round((width + 12) * phase) - 6;
			int left = Math.max(from, centre - 4);
			int right = Math.min(to, centre + 4);
			if (right > left) {
				graphics.fill(left, y, right, y + height, 0x38FFFFFF);
			}
		}
	}

	/** A thin light all the way round, for a bar that is full of something worth noticing. */
	static void halo(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int colour, float strength) {
		int alpha = Mth.clamp(Math.round(strength * 150.0F), 0, 255);
		if (alpha < 6) {
			return;
		}
		graphics.outline(x - 2, y - 2, width + 4, height + 4, alpha << 24 | (colour & 0xFFFFFF));
		graphics.outline(x - 3, y - 3, width + 6, height + 6, alpha / 3 << 24 | (colour & 0xFFFFFF));
	}

	static int lighter(int colour, float by) {
		int r = Mth.clamp(Math.round((colour >> 16 & 255) * by), 0, 255);
		int g = Mth.clamp(Math.round((colour >> 8 & 255) * by), 0, 255);
		int b = Mth.clamp(Math.round((colour & 255) * by), 0, 255);
		return r << 16 | g << 8 | b;
	}
}
