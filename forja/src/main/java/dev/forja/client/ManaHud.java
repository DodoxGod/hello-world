package dev.forja.client;

import dev.forja.item.ForgedItems;
import dev.forja.magic.Mana;
import dev.forja.magic.Spellcasting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * The mana bar (magic/Mana): over the hearts side of the hotbar, the stamina bar's twin across the screen,
 * the same brass-capped well filled from deep blue to violet.
 *
 * <p>It sits above the armour row, or above the hearts when there is no armour, and climbs with them when
 * absorption or a big health pool stack the hearts up — the vanilla bars are never covered. It is not there
 * at all for a player who has never had a use for mana, nor in creative, nor with the interface hidden (F1).
 * Otherwise it shows while a staff, a tome or anything that spends mana is in the hand, and while the bar is
 * short of full, and lingers a moment after it fills.
 *
 * <p>What it draws: the mana, eased towards the synced value so it slides rather than steps; a dim trail of
 * what was just spent, that catches up after a beat; the kill pool as a pale stretch after the end of the
 * mana, flowing into it; a small notch where the held weapon's cheapest spell would leave the bar; and a red
 * flash when a cast is refused, beating for as long as there is not a tap's worth in the bar.
 */
public final class ManaHud implements HudElement {
	private static final int WIDTH = 81;
	private static final int HEIGHT = 3;
	/** How long a full bar lingers before it hides, in milliseconds. */
	private static final long LINGER = 1500L;
	/** How long a refused cast flashes the bar, in milliseconds. */
	private static final long FLASH = 650L;
	private static final int DEEP = 0x3D5BFF;
	private static final int VIOLET = 0xA66BFF;
	private static final int PENDING = 0xC9D6FF;
	private static final int DENIED = 0xFF5A6E;

	private float shown = -1.0F;
	private float trail = -1.0F;
	private long trailSince;
	private long lastFrame;
	private long changedAt;
	private float lastValue = -1.0F;
	private int seenDenied = -1;
	private int seenClientDenied = -1;
	private long flashAt = -FLASH;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.hud.isHidden() || player.isSpectator() || player.isCreative() || !Mana.awake(player)) {
			return;
		}
		Mana.State state = player.getAttached(Mana.STATE);
		if (state == null) {
			return;
		}
		long now = Util.getMillis();
		float max = Math.max(1.0F, state.max());
		float value = Mth.clamp(state.mana(), 0.0F, max);
		this.ease(value, now);
		if (state.denied() != this.seenDenied || Mana.clientDenied != this.seenClientDenied) {
			if (this.seenDenied >= 0) {
				this.flashAt = now;
			}
			this.seenDenied = state.denied();
			this.seenClientDenied = Mana.clientDenied;
		}
		if (Math.abs(value - this.lastValue) > 0.01F || state.pending() > 0.0F) {
			this.lastValue = value;
			this.changedAt = now;
		}
		ItemStack held = player.getMainHandItem();
		float tap = tapCost(held);
		boolean holding = Mana.usesMana(held) || Mana.usesMana(player.getOffhandItem());
		boolean flashing = now - this.flashAt < FLASH;
		if (!holding && !flashing && value >= max && now - this.changedAt > LINGER) {
			return;
		}

		int x = graphics.guiWidth() / 2 - 91;
		int y = top(graphics, player);
		float time = (now % 60000L) / 1000.0F;
		HudBars.well(graphics, x, y, WIDTH, HEIGHT);
		int filled = Math.round(WIDTH * Mth.clamp(this.shown / max, 0.0F, 1.0F));
		int trailed = Math.round(WIDTH * Mth.clamp(this.trail / max, 0.0F, 1.0F));
		// What was just spent, dim, from the end of the mana to where the bar was a moment ago.
		if (trailed > filled) {
			graphics.fill(x + filled, y, x + trailed, y + HEIGHT, 0xA0584A8C);
		}
		// The mana: deep blue at the empty end, violet at the full one, in short runs so the gradient reads along it.
		for (int from = 0; from < filled; from += 3) {
			int to = Math.min(filled, from + 3);
			int colour = mix(DEEP, VIOLET, (from + 1.5F) / WIDTH);
			HudBars.fill(graphics, x + from, x + to, y, HEIGHT, colour, time, x, WIDTH);
		}
		// What kills have left waiting: a pale stretch after the mana, breathing, that the mana eats as it flows in.
		int pending = Math.round(WIDTH * Mth.clamp(state.pending() / max, 0.0F, 1.0F));
		if (pending > 0) {
			int from = x + filled;
			int to = Math.min(x + WIDTH, from + Math.max(1, pending));
			int alpha = 150 + Math.round(70 * Mth.sin(time * 7.0F));
			graphics.fill(from, y, to, y + HEIGHT, alpha << 24 | PENDING);
			graphics.fill(from, y, to, y + 1, 0xE0FFFFFF);
		}
		// The price of the cheapest spell of what is in the hand: a notch on the lip, where a tap would bring the bar.
		if (tap > 0.0F && tap <= max) {
			int notch = x + Math.round(WIDTH * Mth.clamp(tap / max, 0.0F, 1.0F));
			boolean low = value < tap;
			graphics.fill(notch, y - 2, notch + 1, y, low ? 0xFFFF8A96 : 0xFFE6E0FF);
		}
		// With a staff or a tome in the hand, the number, small, left of the bar: mana over max, so a Reserva
		// piece going on shows as the second number growing.
		if (holding || flashing) {
			String label = Math.round(value) + "/" + Math.round(max);
			float scale = 0.75F;
			int width = Math.round(minecraft.font.width(label) * scale);
			graphics.pose().pushMatrix();
			graphics.pose().translate(x - 5 - width, y - 2);
			graphics.pose().scale(scale, scale);
			graphics.text(minecraft.font, net.minecraft.network.chat.Component.literal(label), 0, 0, flashing ? 0xFFFF8A96 : 0xFFC9C2FF, true);
			graphics.pose().popMatrix();
		}
		if (value >= max - 0.01F && holding) {
			HudBars.halo(graphics, x, y, WIDTH, HEIGHT, VIOLET, 0.25F + 0.15F * Mth.sin(time * 3.0F));
		}
		// Refused: a red flash round the bar, and while there is not a tap's worth in it, a slow red beat.
		if (flashing) {
			float left = 1.0F - (now - this.flashAt) / (float) FLASH;
			HudBars.halo(graphics, x, y, WIDTH, HEIGHT, DENIED, left * (0.7F + 0.3F * Mth.sin(time * 40.0F)));
			graphics.fill(x, y, x + WIDTH, y + HEIGHT, Math.round(left * 110) << 24 | DENIED);
		} else if (holding && tap > 0.0F && value < tap) {
			HudBars.halo(graphics, x, y, WIDTH, HEIGHT, DENIED, 0.3F + 0.25F * Mth.sin(time * 6.0F));
		}
	}

	/** Eases the drawn value towards the real one, and lets the trail of what was spent follow after a beat. */
	private void ease(float value, long now) {
		float seconds = this.lastFrame == 0L ? 1.0F : Math.min(0.25F, (now - this.lastFrame) / 1000.0F);
		this.lastFrame = now;
		if (this.shown < 0.0F) {
			this.shown = value;
			this.trail = value;
		}
		// Quick when it drops, so a spell is seen to cost at once; gentler when it fills.
		float rate = value < this.shown ? 22.0F : 9.0F;
		this.shown += (value - this.shown) * Math.min(1.0F, rate * seconds);
		if (Math.abs(value - this.shown) < 0.05F) {
			this.shown = value;
		}
		if (this.trail < this.shown) {
			this.trail = this.shown;
			this.trailSince = now;
		} else if (now - this.trailSince > 350L) {
			this.trail += (this.shown - this.trail) * Math.min(1.0F, 6.0F * seconds);
		}
	}

	/** The cheapest spell of whatever is in the hand, or 0 for anything that is not a staff or a tome. */
	private static float tapCost(ItemStack held) {
		if (held.getItem() instanceof ForgedItems.Forged forged && Spellcasting.casts(forged.forgeType()) && !held.isBroken()) {
			return Spellcasting.tapCost(held, forged.forgeType());
		}
		return 0.0F;
	}

	/**
	 * Where the bar goes: seven pixels above the armour row, or above the top row of hearts when no armour is
	 * worn, worked out the way vanilla lays those out, and never lower than the stamina bar across from it.
	 */
	static int top(GuiGraphicsExtractor graphics, LocalPlayer player) {
		int base = graphics.guiHeight() - 39;
		float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), player.getHealth());
		int absorption = Mth.ceil(player.getAbsorptionAmount());
		int rows = Mth.ceil((maxHealth + absorption) / 2.0F / 10.0F);
		int rowHeight = Math.max(10 - (rows - 2), 3);
		int hearts = base - (rows - 1) * rowHeight;
		int highest = player.getArmorValue() > 0 ? hearts - 10 : hearts;
		return Math.min(graphics.guiHeight() - 64, highest - 7);
	}

	private static int mix(int from, int to, float share) {
		float s = Mth.clamp(share, 0.0F, 1.0F);
		int r = Math.round(Mth.lerp(s, from >> 16 & 255, to >> 16 & 255));
		int g = Math.round(Mth.lerp(s, from >> 8 & 255, to >> 8 & 255));
		int b = Math.round(Mth.lerp(s, from & 255, to & 255));
		return r << 16 | g << 8 | b;
	}
}
