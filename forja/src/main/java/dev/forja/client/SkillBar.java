package dev.forja.client;

import dev.forja.clase.ActiveSkill;
import dev.forja.clase.ClassSkills;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The three class skills left of the off-hand slot (the V, B and N skills), as drawn since the visual pass of
 * 2026-10-01.
 *
 * <p>They were an icon in a frame with a grey cover and a number while waiting, and nothing said which key fired
 * which. Now each carries its key on a keycap (whatever it is bound to), the wait runs down as a shutter with a
 * lit edge, the last second counts in a warmer colour, and the moment a skill comes back it flashes and throws a
 * ring, so a skill that is ready again is noticed without looking for it.
 */
final class SkillBar {
	/** How long the flash of a skill coming back lasts, in milliseconds. */
	private static final long FLASH = 450L;

	private static final int[] lastWaiting = {0, 0, 0, 0};
	private static final long[] readyAt = {-FLASH, -FLASH, -FLASH, -FLASH};

	private SkillBar() {
	}

	static void draw(GuiGraphicsExtractor g, Minecraft client, LocalPlayer player) {
		int size = ClassGui.HUD_SIZE;
		int x = g.guiWidth() / 2 - 91 - 29 - 3 * (size + 2) - 4;
		int y = g.guiHeight() - size - 1;
		long now = Util.getMillis();
		for (int slot = 1; slot <= 3; slot++) {
			ActiveSkill skill = ClassSkills.skill(player, slot);
			int at = x + (slot - 1) * (size + 2);
			if (skill == null) {
				lastWaiting[slot] = 0;
				continue;
			}
			int waiting = ClassSkills.waiting(player, slot);
			if (waiting == 0 && lastWaiting[slot] > 0) {
				readyAt[slot] = now;
			}
			lastWaiting[slot] = waiting;
			ClassGui.hudSlot(g, at, y, waiting == 0);
			g.item(skill.icon(), at + 3, y + 3);
			if (waiting > 0) {
				float share = Math.min(1.0F, waiting / (float) Math.max(1, skill.cooldownTicks(player)));
				int cover = Math.round(16 * share);
				// The shutter: dark over what is still to wait, with a pale edge where it is moving.
				g.fill(at + 3, y + 3, at + 19, y + 3 + cover, 0x98101014);
				if (cover > 0 && cover < 16) {
					g.fill(at + 3, y + 3 + cover, at + 19, y + 4 + cover, 0xC0E8DCC0);
				}
				int seconds = (waiting + 19) / 20;
				String text = Integer.toString(seconds);
				int colour = seconds <= 1 ? 0xFFFFD27A : 0xFFFFFFFF;
				g.text(client.font, text, at + 11 - client.font.width(text) / 2, y + 7, colour, true);
			}
			// Back: a white flash over the icon and a ring that opens out of the frame.
			long since = now - readyAt[slot];
			if (since < FLASH) {
				float left = 1.0F - since / (float) FLASH;
				g.fill(at + 3, y + 3, at + 19, y + 19, Math.round(150 * left * left) << 24 | 0xFFF4D8);
				int grow = Math.round(4 * (1.0F - left));
				g.outline(at - grow, y - grow, size + grow * 2, size + grow * 2, Math.round(220 * left) << 24 | 0xF0C070);
			}
			key(g, client, slot, at, y, size, waiting == 0);
		}
	}

	/** The key the skill is on, small, on a dark keycap over the frame's top edge. */
	private static void key(GuiGraphicsExtractor g, Minecraft client, int slot, int at, int y, int size, boolean ready) {
		KeyMapping mapping = slot == 1 ? ClassClient.SKILL_1 : slot == 2 ? ClassClient.SKILL_2 : ClassClient.SKILL_3;
		if (mapping == null || mapping.isUnbound()) {
			return;
		}
		String name = mapping.getTranslatedKeyMessage().getString();
		if (name.length() > 3) {
			name = name.substring(0, 3);
		}
		float scale = 0.75F;
		int w = Mth.ceil(client.font.width(name) * scale);
		// A keycap on the frame's top edge, half over it: clear of the countdown in the middle and of the icon.
		int tabX = at + (size - w) / 2;
		int tabY = y - 4;
		g.fill(tabX - 2, tabY - 2, tabX + w + 2, tabY + 7, 0xFF0C0A0E);
		g.fill(tabX - 1, tabY - 1, tabX + w + 1, tabY + 6, ready ? 0xFF3A2A12 : 0xFF24202A);
		g.pose().pushMatrix();
		g.pose().translate(tabX, tabY);
		g.pose().scale(scale, scale);
		g.text(client.font, name, 0, 0, ready ? ClassGui.GOLD : 0xFFA8A0B0, false);
		g.pose().popMatrix();
	}
}
