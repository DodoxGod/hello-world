package dev.forja.client;

import dev.forja.Forja;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.BossEvent;

/**
 * The Fallen Smith's own boss bar.
 *
 * <p>He is the one fight in the mod with stages, and the vanilla bar could not say so: it was a red
 * stripe with six notches that meant nothing. This one is a length of forged iron with a hammer head at
 * each end and a notch at three quarters, a half and a quarter — the three moments the fight changes —
 * and what is in it is metal: molten orange while he is only fighting, the violet of his forge once he
 * is down to his last quarter, and pale gold with a hatch running through it while he is back at the
 * forge and nothing can touch him (which the server already says, by turning the bar yellow).
 */
public final class BossBarArt {
	private static final Identifier FRAME = Forja.id("textures/gui/barra_herrero.png");
	private static final String SMITH = "entity.forja.herrero_caido";
	private static final int WIDTH = 182;
	private static final int HEIGHT = 5;

	private BossBarArt() {
	}

	/** Whether this bar is his, which is read off its name: the one thing about an event the client is told. */
	public static boolean isSmith(BossEvent event) {
		return event.getName().getContents() instanceof TranslatableContents name && SMITH.equals(name.getKey());
	}

	/** Draws it where vanilla would have drawn its own: {@code x}, {@code y} are the corner of the five-pixel stripe. */
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, BossEvent event) {
		float share = Mth.clamp(event.getProgress(), 0.0F, 1.0F);
		float time = (Util.getMillis() % 60000L) / 1000.0F;
		boolean reforging = event.getColor() == BossEvent.BossBarColor.YELLOW;
		boolean last = share <= 0.25F;
		graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF120E0C);
		int full = Math.round(WIDTH * share);
		if (full > 0) {
			int top = reforging ? 0xFFFFF2C0 : last ? 0xFFE6B0FF : 0xFFFFC24A;
			int bottom = reforging ? 0xFFC8A040 : last ? 0xFF7A2AD0 : 0xFFD2460E;
			graphics.fillGradient(x, y, x + full, y + HEIGHT, top, bottom);
			graphics.fill(x, y, x + full, y + 1, reforging ? 0xFFFFFFFF : last ? 0xFFF6DCFF : 0xFFFFE9A0);
			if (reforging) {
				// A hatch running along it: this health is not yours to take right now.
				int offset = (int) (time * 12.0F) % 8;
				for (int stripe = -8 + offset; stripe < full; stripe += 8) {
					for (int row = 0; row < HEIGHT; row++) {
						int sx = x + stripe + row;
						if (sx >= x && sx + 2 <= x + full) {
							graphics.fill(sx, y + row, sx + 2, y + row + 1, 0x50402808);
						}
					}
				}
			} else {
				// The metal moves: a slow brightness travelling along it, and a hot lip at the end of it.
				int centre = x + (int) ((time * 22.0F) % (WIDTH + 30)) - 15;
				int from = Math.max(x, centre - 9);
				int to = Math.min(x + full, centre + 9);
				if (to > from) {
					graphics.fill(from, y, to, y + HEIGHT, 0x30FFFFFF);
				}
				float beat = 0.6F + 0.4F * Mth.sin(time * (last ? 10.0F : 5.0F));
				graphics.fill(x + Math.max(0, full - 2), y, x + full, y + HEIGHT, Math.round(beat * 255.0F) << 24 | 0xFFFFFF);
			}
		}
		// Four rows up: three of hammer head standing clear of the name, and the one-pixel lip over the window.
		graphics.blit(RenderPipelines.GUI_TEXTURED, FRAME, x - 8, y - 4, 0.0F, 0.0F, 198, 16, 256, 32);
	}
}
