package dev.forja.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.Pressure;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * How much of your armour still holds (Andy, 2026-09-30): a small shield in the gap between the armour row and
 * the hunger side, 9 by 10 pixels, on the armour row itself so no bar of the mod or of vanilla is covered.
 *
 * <p>Its level is the integrity, {@code 1 - pressure / pressureMax} (difficulty/Pressure): full when nothing
 * has been hitting you, draining as the blows land, filling again, gradually, once the pressure drains. The
 * colour goes from steel through orange to red as it empties, a pale trail shows what the last blow took, and
 * each blow flashes the outline.
 *
 * <p>At 0 (the pressure at its cap) it breaks: the shield splits along a crack, a short break sound plays and a
 * few shards fall away from it, and it stays cracked for as long as the pressure stays at the cap. When the
 * pressure starts to drain the halves slide back together.
 *
 * <p>It is hidden with no pressure and no armour worn, in creative and spectator, and with the interface hidden
 * (F1). The pressure is the one the server already tells the client (combat/CombatAnim PRESSURE).
 */
public final class PressureHud implements HudElement {
	private static final int WIDTH = 9;
	private static final int HEIGHT = 10;
	/** Which pixels of the 9 by 10 shield are there. */
	private static final String[] MASK = {
		".XXXXXXX.",
		"XXXXXXXXX",
		"XXXXXXXXX",
		"XXXXXXXXX",
		"XXXXXXXXX",
		"XXXXXXXXX",
		".XXXXXXX.",
		"..XXXXX..",
		"...XXX...",
		"....X....",
	};
	/** Where the crack runs, as the column each row splits at (the left piece takes up to and including it). */
	private static final int[] CRACK = {4, 3, 4, 5, 4, 3, 4, 4, 4, 4};
	private static final int STEEL = 0x9FB4C8;
	private static final int ORANGE = 0xF0A030;
	private static final int RED = 0xE0402E;
	private static final int EMPTY = 0xFF1E2126;
	private static final long FLASH = 180L;
	private static final long SHARD_LIFE = 700L;
	private static final long REASSEMBLE = 450L;

	private static boolean broken;
	private static float shownIntegrity = 1.0F;

	private final List<float[]> shards = new ArrayList<>();
	private final Random random = new Random();
	private float shown = 1.0F;
	private float trail = 1.0F;
	private long trailSince;
	private long lastFrame;
	private long flashAt = -FLASH;
	private long reassembleAt = -REASSEMBLE;
	private boolean seen;

	/** Whether the indicator is showing as broken right now (for the tests). */
	public static boolean isBroken() {
		return broken;
	}

	/** The level the indicator was last drawn at, 0 to 1 (for the tests). */
	public static float shownIntegrity() {
		return shownIntegrity;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		CombatConfig cfg = CombatConfig.get();
		if (player == null || minecraft.gui.hud.isHidden() || player.isSpectator() || player.isCreative() || !cfg.enabled) {
			this.hidden();
			return;
		}
		float pressure = CombatAnims.pressure(delta.getGameTimeDeltaPartialTick(false));
		if (pressure <= 0.002F && player.getArmorValue() <= 0) {
			this.hidden();
			return;
		}
		float integrity = (float) Pressure.integrity(pressure, cfg.pressureMax);
		boolean nowBroken = integrity <= 0.002F;
		long now = Util.getMillis();
		float seconds = this.lastFrame == 0L ? 0.0F : Math.min(0.25F, (now - this.lastFrame) / 1000.0F);
		this.lastFrame = now;
		int x = graphics.guiWidth() / 2 - WIDTH / 2;
		int y = armourRowY(graphics, player);

		if (!this.seen) {
			this.shown = integrity;
			this.trail = integrity;
			this.seen = true;
			broken = nowBroken;
		}
		if (nowBroken && !broken) {
			this.shatter(minecraft, x, y, now);
		} else if (!nowBroken && broken) {
			this.reassembleAt = now;
		}
		broken = nowBroken;
		if (integrity < this.shown - 0.03F) {
			this.flashAt = now;
		}
		// Quick when it drops, so a blow is seen to cost at once; it rises as the real value does, which is slow.
		this.shown = integrity < this.shown ? this.shown + (integrity - this.shown) * Math.min(1.0F, 24.0F * seconds) : integrity;
		if (Math.abs(this.shown - integrity) < 0.004F) {
			this.shown = integrity;
		}
		if (this.trail < this.shown) {
			this.trail = this.shown;
			this.trailSince = now;
		} else if (now - this.trailSince > 300L) {
			this.trail += (this.shown - this.trail) * Math.min(1.0F, 6.0F * seconds);
		}
		shownIntegrity = this.shown;

		float together = Mth.clamp((now - this.reassembleAt) / (float) REASSEMBLE, 0.0F, 1.0F);
		if (broken || together < 1.0F) {
			this.drawBroken(graphics, x, y, broken ? 1.0F : 1.0F - together, now);
		} else {
			this.drawWhole(graphics, x, y, now);
		}
		this.drawShards(graphics, seconds, now);
	}

	private void hidden() {
		this.seen = false;
		this.lastFrame = 0L;
		this.shards.clear();
		broken = false;
		shownIntegrity = 1.0F;
	}

	/** Where the shield goes: on the armour row, in the gap between its end and the hunger side, worked out as vanilla does. */
	static int armourRowY(GuiGraphicsExtractor graphics, LocalPlayer player) {
		int base = graphics.guiHeight() - 39;
		float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), player.getHealth());
		int absorption = Mth.ceil(player.getAbsorptionAmount());
		int rows = Mth.ceil((maxHealth + absorption) / 2.0F / 10.0F);
		int rowHeight = Math.max(10 - (rows - 2), 3);
		return base - (rows - 1) * rowHeight - 10;
	}

	/** Steel above 70 %, orange at 40 %, red below 12 %, blended in between. */
	static int colourFor(float level) {
		if (level >= 0.7F) {
			return STEEL;
		}
		if (level >= 0.4F) {
			return mix(ORANGE, STEEL, (level - 0.4F) / 0.3F);
		}
		return mix(RED, ORANGE, Mth.clamp((level - 0.12F) / 0.28F, 0.0F, 1.0F));
	}

	private static boolean solid(int column, int row) {
		return column >= 0 && column < WIDTH && row >= 0 && row < HEIGHT && MASK[row].charAt(column) == 'X';
	}

	private static boolean edge(int column, int row) {
		return !solid(column - 1, row) || !solid(column + 1, row) || !solid(column, row - 1) || !solid(column, row + 1);
	}

	private void drawWhole(GuiGraphicsExtractor graphics, int x, int y, long now) {
		int colour = colourFor(this.shown);
		boolean flashing = now - this.flashAt < FLASH;
		boolean surface = true;
		for (int row = 0; row < HEIGHT; row++) {
			boolean filled = level(row) <= this.shown + 0.001F;
			boolean trailed = !filled && level(row) <= this.trail + 0.001F;
			for (int column = 0; column < WIDTH; column++) {
				if (!solid(column, row)) {
					continue;
				}
				int pixel;
				if (edge(column, row)) {
					pixel = filled ? 0xFF000000 | HudBars.lighter(colour, 0.5F) : 0xFF5A616C;
					if (flashing) {
						pixel = 0xFFFFFFFF;
					}
				} else if (filled) {
					float shade = column <= 3 && row <= 4 ? 1.25F : row >= 7 ? 0.75F : 1.0F;
					if (column == WIDTH / 2 && row >= 2 && row <= 6) {
						shade = 1.4F;
					}
					pixel = 0xFF000000 | HudBars.lighter(colour, surface ? 1.5F : shade);
				} else if (trailed) {
					pixel = 0xFF8A5A44;
				} else {
					pixel = EMPTY;
				}
				graphics.fill(x + column, y + row, x + column + 1, y + row + 1, pixel);
			}
			if (filled) {
				surface = false;
			}
		}
	}

	/**
	 * The cracked shield: two pieces split along the crack, apart by up to a pixel (apart = 1 when broken, and
	 * back to 0 as it comes together again), dull red, with a bright edge on the crack while it is still new.
	 */
	private void drawBroken(GuiGraphicsExtractor graphics, int x, int y, float apart, long now) {
		float pulse = 0.5F + 0.5F * Mth.sin(now / 260.0F);
		int dark = mix(0x4A2220, colourFor(this.shown), 1.0F - apart);
		int rim = mix(0xE0402E, colourFor(this.shown), 1.0F - apart);
		int gap = Math.round(apart);
		for (int row = 0; row < HEIGHT; row++) {
			for (int column = 0; column < WIDTH; column++) {
				if (!solid(column, row)) {
					continue;
				}
				boolean left = column <= CRACK[row];
				int dx = left ? -gap : gap;
				int dy = left ? 0 : Math.round(apart);
				int pixel;
				if (edge(column, row) || column == CRACK[row] || column == CRACK[row] + 1) {
					pixel = 0xFF000000 | HudBars.lighter(rim, 0.65F + 0.25F * pulse * apart);
				} else {
					pixel = 0xFF000000 | HudBars.lighter(dark, row >= 7 ? 0.7F : 1.0F);
				}
				graphics.fill(x + column + dx, y + row + dy, x + column + dx + 1, y + row + dy + 1, pixel);
			}
		}
		if (apart < 1.0F) {
			// Coming together: a pale flash along the seam.
			int alpha = Math.round((1.0F - apart) * 90.0F);
			graphics.fill(x + 3, y + 1, x + 6, y + 9, alpha << 24 | 0xFFFFFF);
		}
	}

	/** The broken moment: the sound, and shards thrown off the shield. */
	private void shatter(Minecraft minecraft, int x, int y, long now) {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK.value(), 0.8F, 0.7F));
		for (int i = 0; i < 14; i++) {
			float angle = (float) (this.random.nextDouble() * Math.PI * 2.0);
			float speed = 25.0F + this.random.nextFloat() * 60.0F;
			float size = this.random.nextBoolean() ? 2.0F : 1.0F;
			int colour = switch (this.random.nextInt(3)) {
				case 0 -> 0xFFC9D6E3;
				case 1 -> 0xFFF0A030;
				default -> 0xFFE0402E;
			};
			// x, y, vx, vy (pixels and pixels a second), birth, size, colour
			this.shards.add(new float[] {x + 2 + this.random.nextInt(5), y + 2 + this.random.nextInt(5),
				Mth.cos(angle) * speed, Mth.sin(angle) * speed - 40.0F, now, size, Float.intBitsToFloat(colour)});
		}
	}

	private void drawShards(GuiGraphicsExtractor graphics, float seconds, long now) {
		this.shards.removeIf(shard -> now - shard[4] > SHARD_LIFE);
		for (float[] shard : this.shards) {
			shard[0] += shard[2] * seconds;
			shard[1] += shard[3] * seconds;
			shard[3] += 260.0F * seconds;
			float left = 1.0F - (now - shard[4]) / (float) SHARD_LIFE;
			int alpha = Mth.clamp(Math.round(left * 300.0F), 0, 255);
			int colour = Float.floatToRawIntBits(shard[6]) & 0xFFFFFF;
			int size = Math.round(shard[5]);
			int px = Math.round(shard[0]);
			int py = Math.round(shard[1]);
			graphics.fill(px, py, px + size, py + size, alpha << 24 | colour);
		}
	}

	/** How full the shield is when the level is at the middle of a row: 0.95 at the top row, 0.05 at the bottom. */
	private static float level(int row) {
		return 1.0F - (row + 0.5F) / HEIGHT;
	}

	private static int mix(int from, int to, float share) {
		float s = Mth.clamp(share, 0.0F, 1.0F);
		int r = Math.round(Mth.lerp(s, from >> 16 & 255, to >> 16 & 255));
		int g = Math.round(Mth.lerp(s, from >> 8 & 255, to >> 8 & 255));
		int b = Math.round(Mth.lerp(s, from & 255, to & 255));
		return r << 16 | g << 8 | b;
	}
}
