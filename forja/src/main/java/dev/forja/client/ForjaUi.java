package dev.forja.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The pieces every Forja screen paints for itself, drawn one way (the visual pass of 2026-10-01).
 *
 * <p>The panels came out of one generator and matched; what the screens drew on top of them did not. The
 * forge table's button was a one-pixel bevel, the extraction table's another, and the channels the metal runs
 * down were a flat orange bar in the casting box and the assembler beside the crucible's live, coloured pour.
 * These are the shared versions:
 * <ul>
 * <li>{@link #button}: a framed plate with a rim, a lit top edge and a shaded foot, its label with a shadow;
 *     lighter and ringed under the mouse, flat and grey when it cannot be pressed.</li>
 * <li>{@link #channel}: molten metal in a trough, lit on top, dark underneath, flowing, with a white-hot head.</li>
 * <li>{@link #slotGlow}: light round a slot that holds something that matters, outside its frame, so the
 *     frame and what lies in it stay as they are.</li>
 * <li>{@link #trough}: the sunken strip a gauge sits in.</li>
 * </ul>
 */
public final class ForjaUi {
	private ForjaUi() {
	}

	/** What a button looks like right now. */
	public enum Look {
		OFF, READY, HOVERED
	}

	/**
	 * A button: rim, body graded from a lit top to a shaded foot, a highlight along the top and the left, a
	 * shadow along the bottom and the right, and the label centred with a shadow.
	 *
	 * @param base the button's own colour (the forge's orange, the extraction's brass or red); OFF ignores it
	 */
	public static void button(GuiGraphicsExtractor g, Font font, Component label, int x, int y, int w, int h, int base, Look look) {
		int body = look == Look.OFF ? dull(base) : look == Look.HOVERED ? lighter(base, 1.16F) : base;
		int rim = darker(body, 0.36F);
		g.fill(x, y, x + w, y + h, rim);
		g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, lighter(body, 1.10F), darker(body, 0.86F));
		g.fill(x + 1, y + 1, x + w - 1, y + 2, lighter(body, look == Look.OFF ? 1.18F : 1.42F));
		g.fill(x + 1, y + 2, x + 2, y + h - 2, lighter(body, 1.22F));
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, darker(body, 0.62F));
		g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, darker(body, 0.72F));
		if (look == Look.HOVERED) {
			// Ringed in a pale light under the mouse, outside the rim, so the button grows a little rather than shifts.
			g.outline(x - 1, y - 1, w + 2, h + 2, 0x90FFF4DC);
		}
		int textY = y + (h - 8) / 2 + 1;
		g.centeredText(font, label, x + w / 2, textY, look == Look.OFF ? 0xFFD8D0C0 : 0xFFFFFFFF);
	}

	/**
	 * Molten metal running along a trough, from {@code x} towards {@code x + w}, as far as {@code progress} goes.
	 * The trough itself is the panel's (painted in the texture); this is what runs in it.
	 *
	 * @param colour the metal's colour, 0xRRGGBB
	 */
	public static void channel(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int colour) {
		int width = Math.round(w * Mth.clamp(progress, 0.0F, 1.0F));
		if (width <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		// Body: lit on top, deep underneath.
		for (int row = 0; row < h; row++) {
			float along = h <= 1 ? 0.0F : row / (float) (h - 1);
			float light = row == 0 ? 1.45F : 1.12F - 0.42F * along;
			g.fill(x, y + row, x + width, y + row + 1, 0xFF000000 | lighter(colour, light));
		}
		// The flow: short bright dashes on the surface running towards the head, so a still frame still reads
		// as something moving.
		int phase = (int) (now / 70L % 7L);
		for (int dash = x + phase - 7; dash < x + width - 3; dash += 7) {
			int left = Math.max(x, dash);
			int right = Math.min(x + width - 3, dash + 3);
			if (right > left) {
				g.fill(left, y + 1, right, y + 2, 0xFF000000 | lighter(colour, 1.6F));
			}
		}
		// The head: white-hot, and a breath of glow ahead of it.
		int head = x + width;
		g.fill(head - 2, y, head, y + h, 0xFFFFF4D8);
		g.fill(head - 3, y, head - 2, y + h, 0xFF000000 | lighter(colour, 1.7F));
		if (width < w) {
			float pulse = 0.5F + 0.5F * Mth.sin(now / 120.0F);
			g.fill(head, y, head + 1, y + h, (int) (70 + 60 * pulse) << 24 | lighter(colour, 1.5F));
		}
	}

	/**
	 * Light round an 18-pixel slot whose item sits at ({@code itemX}, {@code itemY}): two soft rings outside its
	 * frame with the corners cut, a tinted edge on the frame itself and a faint wash inside. The frame stays
	 * a frame, and what is in the slot is not washed out.
	 *
	 * @param strength 0 to 1
	 */
	public static void slotGlow(GuiGraphicsExtractor g, int itemX, int itemY, int colour, float strength) {
		frameGlow(g, itemX - 1, itemY - 1, itemX + 17, itemY + 17, colour, strength);
		int a = Mth.clamp(Math.round(strength * 255.0F), 0, 255);
		if (a >= 6) {
			// A wash inside, faint enough that the item stays its own colour.
			g.fill(itemX, itemY, itemX + 16, itemY + 16, (a * 22 / 100) << 24 | (colour & 0xFFFFFF));
		}
	}

	/**
	 * The same light round any frame from (x0, y0) to (x1, y1), exclusive: two rings outside it with their corners
	 * cut, and its own edge lit.
	 */
	public static void frameGlow(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int colour, float strength) {
		int a = Mth.clamp(Math.round(strength * 255.0F), 0, 255);
		if (a < 6) {
			return;
		}
		int rgb = colour & 0xFFFFFF;
		ring(g, x0 - 2, y0 - 2, x1 + 2, y1 + 2, (a * 35 / 100) << 24 | rgb, 2);
		ring(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, (a * 75 / 100) << 24 | rgb, 1);
		g.outline(x0, y0, x1 - x0, y1 - y0, (a * 85 / 100) << 24 | (lighter(rgb, 1.2F) & 0xFFFFFF));
	}

	/** A one-pixel rectangle ring from (x0, y0) to (x1, y1) exclusive, its corners cut by {@code cut} pixels. */
	private static void ring(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int argb, int cut) {
		g.fill(x0 + cut, y0, x1 - cut, y0 + 1, argb);
		g.fill(x0 + cut, y1 - 1, x1 - cut, y1, argb);
		g.fill(x0, y0 + cut, x0 + 1, y1 - cut, argb);
		g.fill(x1 - 1, y0 + cut, x1, y1 - cut, argb);
	}

	/** A sunken strip for a gauge: dark inside, shadowed along the top and the left, lit along the bottom. */
	public static void trough(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int shadow, int light) {
		g.fill(x, y, x + w, y + h, fill);
		g.fill(x, y, x + w, y + 1, shadow);
		g.fill(x, y, x + 1, y + h, shadow);
		g.fill(x, y + h - 1, x + w, y + h, light);
		g.fill(x + w - 1, y + 1, x + w, y + h, light);
	}

	/** A button that cannot be pressed: its own colour drained most of the way to grey, and dimmed. */
	private static int dull(int base) {
		int r = base >> 16 & 255;
		int gr = base >> 8 & 255;
		int b = base & 255;
		int grey = (r * 299 + gr * 587 + b * 114) / 1000;
		r = Math.round((grey + (r - grey) * 0.25F) * 0.8F);
		gr = Math.round((grey + (gr - grey) * 0.25F) * 0.8F);
		b = Math.round((grey + (b - grey) * 0.25F) * 0.8F);
		return 0xFF000000 | r << 16 | gr << 8 | b;
	}

	/** The colour times {@code by}, each channel clamped, keeping its alpha (or full alpha if it had none). */
	public static int lighter(int colour, float by) {
		int alpha = colour >>> 24 == 0 ? 0xFF : colour >>> 24;
		int r = Mth.clamp(Math.round((colour >> 16 & 255) * by), 0, 255);
		int gr = Mth.clamp(Math.round((colour >> 8 & 255) * by), 0, 255);
		int b = Mth.clamp(Math.round((colour & 255) * by), 0, 255);
		return alpha << 24 | r << 16 | gr << 8 | b;
	}

	public static int darker(int colour, float by) {
		return lighter(colour, by);
	}
}
