package dev.forja.client;

import dev.forja.item.EssenceJarItem;
import dev.forja.world.WorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The card that comes down when the sky changes.
 *
 * <p>An event used to announce itself with one line of chat, which is where the game puts things it
 * does not mind you missing — and these are the rarest things in the mod. The card says what has
 * started, shows the jar that catches it in its own colour, and names the one upgrade it carries, for
 * the seven seconds that is worth interrupting for. A smaller one says when it is over, because
 * "is it still going?" was only answerable by looking up.
 */
public final class EventBannerHud implements HudElement {
	private static final int STARTS = 150;
	private static final int ENDS = 70;
	private static final int SLIDE = 12;
	private static final int FADE = 24;

	private static @Nullable WorldEvents event;
	private static boolean starting;
	private static int age;
	private static int life;
	private static ItemStack jar = ItemStack.EMPTY;

	/** Called when the server says an event has begun, or that the one that was running is over. */
	public static void show(WorldEvents shown, boolean begins) {
		event = shown;
		starting = begins;
		age = 0;
		life = begins ? STARTS : ENDS;
		jar = EssenceJarItem.filled(shown);
	}

	/** Puts the card away at once: for the tests, which photograph the sky it would be in front of. */
	public static void dismiss() {
		event = null;
	}

	public static void tick() {
		if (event != null && ++age >= life) {
			event = null;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		WorldEvents shown = event;
		Minecraft minecraft = Minecraft.getInstance();
		if (shown == null) {
			return;
		}
		float time = age + delta.getGameTimeDeltaPartialTick(false);
		// Down from above the screen, fast and then settling; out by fading where it is.
		float in = Mth.clamp(time / SLIDE, 0.0F, 1.0F);
		float slide = 1.0F - (1.0F - in) * (1.0F - in) * (1.0F - in);
		float fade = Mth.clamp((life - time) / FADE, 0.0F, 1.0F);
		int alpha = Math.round(255 * fade);
		if (alpha < 8) {
			return;
		}
		Font font = minecraft.font;
		Component title = starting ? shown.displayName() : Component.translatable("gui.forja.cartel.termina", shown.displayName());
		Component carries = Component.translatable("gui.forja.cartel.mejora", shown.upgrade.displayName());
		Component hint = Component.translatable("gui.forja.cartel.pista");
		float scale = starting ? 1.5F : 1.0F;
		int titleWidth = Math.round(font.width(title) * scale);
		int textWidth = starting ? Math.max(titleWidth, Math.max(font.width(carries), font.width(hint))) : titleWidth;
		int width = textWidth + (starting ? 38 : 16);
		int height = starting ? 44 : 18;
		int x = (graphics.guiWidth() - width) / 2;
		int y = Math.round(Mth.lerp(slide, -height - 4, 34));
		int colour = shown.color & 0xFFFFFF;

		// The plate: dark, with the event's colour coming up from the bottom edge, and a line of it
		// along the top that runs down with the time the card has left.
		graphics.fill(x, y, x + width, y + height, alpha * 3 / 4 << 24 | 0x0B0A10);
		graphics.fillGradient(x, y + height / 2, x + width, y + height, colour, Math.min(90, alpha / 3) << 24 | colour);
		graphics.outline(x - 1, y - 1, width + 2, height + 2, alpha << 24 | darker(colour));
		int left = Math.round(width * Mth.clamp((life - time) / life, 0.0F, 1.0F));
		graphics.fill(x, y, x + left, y + 1, alpha << 24 | colour);

		if (starting) {
			graphics.item(jar, x + 9, y + 14);
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + 32, y + 6);
			graphics.pose().scale(scale, scale);
			graphics.text(font, title, 0, 0, alpha << 24 | colour, true);
			graphics.pose().popMatrix();
			graphics.text(font, carries, x + 32, y + 21, alpha << 24 | 0xF0F0F0, false);
			graphics.text(font, hint, x + 32, y + 31, alpha << 24 | 0x9A9AA6, false);
		} else {
			graphics.text(font, title, x + 8, y + 5, alpha << 24 | colour, true);
		}
	}

	private static int darker(int colour) {
		return ((colour >> 16 & 255) * 5 / 8) << 16 | ((colour >> 8 & 255) * 5 / 8) << 8 | (colour & 255) * 5 / 8;
	}
}
