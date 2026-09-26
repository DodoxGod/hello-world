package dev.forja.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.forja.Forja;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeStats;
import dev.forja.forge.Mastery;
import dev.forja.material.ForgeMaterial;
import dev.forja.menu.ForgeMenu;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.UpgradeRecipes;
import dev.forja.upgrade.Upgrades;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The screen of both work tables, drawn over Forja's own panel textures. The parts table has tabs
 * for cutting parts from a template and for salvaging gear; the forge table is a single star of five
 * slots around the gear in the center, with an info panel and a button that forges, swaps parts or
 * upgrades depending on what is on the star.
 */
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {
	private static final Identifier PARTS_TEXTURE = Forja.id("textures/gui/mesa_de_piezas.png");
	private static final Identifier SALVAGE_TEXTURE = Forja.id("textures/gui/mesa_de_piezas_desarmar.png");
	private static final Identifier FORGE_TEXTURE = Forja.id("textures/gui/mesa_de_forja.png");
	private static final Identifier TECHNIQUES_TEXTURE = Forja.id("textures/gui/mesa_de_forja_tecnicas.png");
	/**
	 * The greater table's panels, in its own colour.
	 *
	 * <p>Andy asked for this on top of the fix, and he is right to: the two tables share a menu, share
	 * a layout and differ only in what they will agree to build, so the one thing that tells you which
	 * one you are standing at should not be a line of text you have to go and read. Deepslate and
	 * violet, which is what the greater table is built out of.
	 */
	private static final Identifier FORGE_MAYOR_TEXTURE = Forja.id("textures/gui/mesa_de_forja_mayor.png");
	private static final Identifier TECHNIQUES_MAYOR_TEXTURE = Forja.id("textures/gui/mesa_de_forja_mayor_tecnicas.png");
	/**
	 * The engraved star again, in light: white, laid over the panel pixel for pixel, with nothing under
	 * the slots. White so that each table pours its own colour into it — the forge's orange, the greater
	 * table's violet.
	 */
	private static final Identifier STAR_LIT = Forja.id("textures/gui/estrella_viva.png");

	private static final int TEXT = 0xFF3B2A1A;
	private static final int ON_WOOD = 0xFFF2E3C6;
	private static final int GOOD = 0xFF7CFC7C;
	private static final int BAD = 0xFFFF6B6B;
	private static final int BAD_ON_STONE = 0xFFA02020;
	private static final int MUTED = 0xFFB0B0B0;

	private static final int TAB_Y = 4;

	/** Where the line under the pattern grid is written, just above the row of slots. */
	private static final int PARTS_LABEL_Y = 77;

	/** Where the first tier's row starts, and how far apart the three of them are. */
	private static final int TECH_ROW_Y = 16;
	private static final int TECH_ROW_H = 25;
	/** The smith's own level, as a track under the three tiers: where it starts, how long, how tall. */
	private static final int TRACK_X = 84;
	private static final int TRACK_Y = 99;
	private static final int TRACK_W = 114;
	private static final int TRACK_H = 4;
	private static final int TAB_W = 58;
	private static final int TAB_H = 16;
	private static final int GRID_X = 7;
	private static final int GRID_Y = 24;
	private static final int GRID_COLUMNS = 12;
	/** Cell size of the pattern grid: the icon is 16, the rest is its border. */
	private static final int GRID_CELL = 16;
	private static final int INFO_X = 111;
	private static final int INFO_Y = 19;
	private static final int INFO_W = 89;
	private static final int INFO_H = 65;
	/** How long the hammer takes to cross the rail and come back, in milliseconds. */
	private static final int SWEEP_MILLIS = 1400;

	/** Half the width of the perfect window, as a share of the rail, before Maestria widens it. */
	private static final float WINDOW = 0.05F;

	private static final int FORGE_BUTTON_X = 110;
	private static final int FORGE_BUTTON_Y = 88;
	private static final int FORGE_BUTTON_W = 91;
	private static final int FORGE_BUTTON_H = 15;

	/** When the current swing started, or zero while no hammer is in the air. */
	private long swingStart;

	/** How long the sparks of a finished piece of work stay in the air, and how many there are. */
	private static final int BURST_MILLIS = 650;
	private static final int BURST_SPARKS = 26;
	/** When the last piece of work was struck, and how well: 2 perfect, 1 decent, 0 a miss. */
	private long burstStart;
	private int burstQuality;
	/** Sparks running round the star while it is ready: how many, and how long one takes to go all the way. */
	private static final int RUNNERS = 5;
	private static final int LAP_MILLIS = 5200;
	private static final int BUTTON_X = 64;
	/** Orbs shown on the salvage tab before the rest collapse into "+N". */
	private static final int ORB_ROW = 6;
	private static final int BUTTON_Y = 68;
	private static final int BUTTON_W = 88;
	private static final int BUTTON_H = 16;

	public ForgeScreen(ForgeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, ForgeMenu.IMAGE_WIDTH, ForgeMenu.IMAGE_HEIGHT);
		this.inventoryLabelX = ForgeMenu.INVENTORY_X;
		this.inventoryLabelY = ForgeMenu.INVENTORY_Y - 11;
	}

	private static Component tabName(int mode) {
		String key = switch (mode) {
			case ForgeMenu.MODE_PARTS -> "piezas";
			case ForgeMenu.MODE_FORGE -> "forja";
			case ForgeMenu.MODE_TECHNIQUES -> "tecnicas";
			default -> "desarmar";
		};
		return Component.translatable("gui.forja.pestana." + key);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		boolean greater = this.menu.station() == dev.forja.menu.Station.FORJA_MAYOR;
		Identifier texture = switch (this.menu.getMode()) {
			case ForgeMenu.MODE_PARTS -> PARTS_TEXTURE;
			case ForgeMenu.MODE_FORGE -> greater ? FORGE_MAYOR_TEXTURE : FORGE_TEXTURE;
			case ForgeMenu.MODE_TECHNIQUES -> greater ? TECHNIQUES_MAYOR_TEXTURE : TECHNIQUES_TEXTURE;
			default -> SALVAGE_TEXTURE;
		};
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

		switch (this.menu.getMode()) {
			case ForgeMenu.MODE_PARTS -> {
				this.drawTabs(g, x, y, mouseX, mouseY);
				this.drawPartsTab(g, x, y, mouseX, mouseY);
			}
			case ForgeMenu.MODE_FORGE -> {
				this.drawTabs(g, x, y, mouseX, mouseY);
				this.drawForge(g, x, y, mouseX, mouseY);
			}
			case ForgeMenu.MODE_TECHNIQUES -> {
				this.drawTabs(g, x, y, mouseX, mouseY);
				this.drawTechniques(g, x, y, mouseX, mouseY);
			}
			default -> {
				this.drawTabs(g, x, y, mouseX, mouseY);
				this.drawDisassembleTab(g, x, y, mouseX, mouseY);
			}
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
		int titleWidth = Math.round(this.font.width(this.title) * 0.8F);
		float titleX;
		float titleY;
		if (this.menu.station().modes.size() > 1) {
			int tabsEnd = 5 + this.menu.station().modes.size() * (this.tabWidth() + 2) + 2;
			titleX = tabsEnd + (this.imageWidth - 5 - tabsEnd - titleWidth) / 2.0F;
			titleY = this.tabTop() + (this.compactTabs() ? 3 : 6);
		} else {
			titleX = (this.imageWidth - titleWidth) / 2.0F;
			titleY = 6;
		}
		g.pose().pushMatrix();
		g.pose().translate(titleX, titleY);
		g.pose().scale(0.8F, 0.8F);
		g.text(this.font, this.title, 0, 0, ON_WOOD, true);
		g.pose().popMatrix();
	}

	/**
	 * Embers around the filled points of the star, and a warm ring when what is on it would actually make
	 * something. The star is engraved in the panel texture; this is the fire in it.
	 */
	private void drawStarEmbers(GuiGraphicsExtractor g, int x, int y, ForgeMenu.Action action) {
		boolean ready = action != ForgeMenu.Action.NONE;
		long now = System.currentTimeMillis();
		// A slow breath rather than a blink: one cycle every two seconds.
		float pulse = (float) ((Math.sin(now / 320.0) + 1.0) * 0.5);
		int alpha = (int) (ready ? 70 + 70 * pulse : 34 + 26 * pulse);
		// The greater table burns violet, and paler than the forge burns orange: its panel is violet
		// already, and a violet line on violet stone was a line nobody could see.
		boolean greater = this.menu.station() == dev.forja.menu.Station.FORJA_MAYOR;
		int warm = greater ? 0xD9B8FF : 0xFF9A3C;
		int bright = greater ? 0xF1E4FF : 0xFFC468;
		if (ready) {
			// The engraving itself takes fire: the whole star and its ring, breathing with the slots.
			int fire = greater ? 0xE9D6FF : 0xFF9A3C;
			int glow = (int) ((greater ? 185 : 150) + (greater ? 70 : 95) * pulse) << 24 | fire;
			g.blit(RenderPipelines.GUI_TEXTURED, STAR_LIT, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256, glow);
			this.drawRunners(g, x, y, now, greater ? 0xF0E0FF : 0xFFF0C8, greater ? 0xC9A0FF : 0xFFB45A);
		}
		for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
			if (!this.menu.getSlot(ForgeMenu.STAR_FIRST + i).hasItem()) {
				continue;
			}
			int px = x + ForgeMenu.STAR_POINTS[i][0];
			int py = y + ForgeMenu.STAR_POINTS[i][1];
			int colour = alpha << 24 | (ready ? warm : 0xB0A890);
			// A plus-shaped halo rather than a square: the corners would look like a box around the slot.
			g.fill(px - 3, py + 1, px + 19, py + 15, colour);
			g.fill(px + 1, py - 3, px + 15, py + 19, colour);
			g.fill(px - 1, py - 1, px + 17, py + 17, (Math.min(255, alpha + 40)) << 24 | (ready ? bright : 0xC8C0AC));
		}
		if (ready) {
			int centre = (int) (40 + 40 * pulse) << 24 | warm;
			int cx = x + ForgeMenu.CENTER_X;
			int cy = y + ForgeMenu.CENTER_Y;
			g.fill(cx - 4, cy, cx + 20, cy + 16, centre);
			g.fill(cx, cy - 4, cx + 16, cy + 20, centre);
		}
	}

	/**
	 * Sparks that run the star's own lines, point to point in the order the star is drawn in, each with
	 * a short tail. They pass under the slots rather than over what lies in them.
	 */
	private void drawRunners(GuiGraphicsExtractor g, int x, int y, long now, int head, int tail) {
		for (int runner = 0; runner < RUNNERS; runner++) {
			float lap = (now % LAP_MILLIS) / (float) LAP_MILLIS * ForgeMenu.STAR_COUNT + runner * (ForgeMenu.STAR_COUNT / (float) RUNNERS);
			for (int link = 0; link < 4; link++) {
				float at = (lap - link * 0.035F + ForgeMenu.STAR_COUNT) % ForgeMenu.STAR_COUNT;
				int leg = (int) at;
				float along = at - leg;
				// The star is one line drawn without lifting the pen: every leg skips a point.
				int[] from = ForgeMenu.STAR_POINTS[leg * 2 % ForgeMenu.STAR_COUNT];
				int[] to = ForgeMenu.STAR_POINTS[(leg * 2 + 2) % ForgeMenu.STAR_COUNT];
				int px = Math.round(from[0] + 8 + (to[0] - from[0]) * along);
				int py = Math.round(from[1] + 8 + (to[1] - from[1]) * along);
				if (this.underSlot(px, py)) {
					continue;
				}
				int alpha = 255 - link * 60;
				int size = link == 0 ? 2 : 1;
				g.fill(x + px, y + py, x + px + size, y + py + size, alpha << 24 | (link == 0 ? head : tail));
			}
		}
	}

	/** Whether a point of the panel lies under one of the star's slots or the one in the middle. */
	private boolean underSlot(int px, int py) {
		for (int[] point : ForgeMenu.STAR_POINTS) {
			if (px >= point[0] - 2 && px < point[0] + 18 && py >= point[1] - 2 && py < point[1] + 18) {
				return true;
			}
		}
		return px >= ForgeMenu.CENTER_X - 6 && px < ForgeMenu.CENTER_X + 22 && py >= ForgeMenu.CENTER_Y - 6 && py < ForgeMenu.CENTER_Y + 22;
	}

	/**
	 * The strike: sparks thrown out of the middle of the star when the button does its work. A perfect
	 * press throws more of them, further and whiter; a miss throws a few dull ones. They fall as they go.
	 */
	private void drawBurst(GuiGraphicsExtractor g, int x, int y) {
		if (this.burstStart == 0L) {
			return;
		}
		float age = (System.currentTimeMillis() - this.burstStart) / (float) BURST_MILLIS;
		if (age >= 1.0F) {
			this.burstStart = 0L;
			return;
		}
		int cx = x + ForgeMenu.CENTER_X + 8;
		int cy = y + ForgeMenu.CENTER_Y + 8;
		int sparks = this.burstQuality == 2 ? BURST_SPARKS : this.burstQuality == 1 ? BURST_SPARKS * 2 / 3 : BURST_SPARKS / 3;
		// Saturated, not bright: the panel is pale stone, and a white spark on it is a spark nobody sees.
		int hot = this.burstQuality == 2 ? 0xFFA010 : this.burstQuality == 1 ? 0xFF7A1E : 0x6A6052;
		int cool = this.burstQuality == 0 ? 0x4A4238 : 0xC23A0A;
		// The flash on the slot first, gone in the first third.
		if (age < 0.33F) {
			int flash = (int) (200 * (1.0F - age / 0.33F)) << 24 | (this.burstQuality == 0 ? 0xB8AE9C : 0xFFE9B0);
			g.fill(cx - 12, cy - 12, cx + 12, cy + 12, flash);
		}
		for (int spark = 0; spark < sparks; spark++) {
			// No random: the same spark has to be in the same place from one frame to the next.
			double angle = spark * 2.399963 + this.burstStart % 7;
			float reach = (18.0F + (spark * 37 % 17)) * (this.burstQuality == 2 ? 1.35F : 1.0F);
			float out = 1.0F - (1.0F - age) * (1.0F - age);
			int px = cx + Math.round((float) Math.cos(angle) * reach * out);
			int py = cy + Math.round((float) Math.sin(angle) * reach * out + 14.0F * age * age);
			int alpha = (int) (255 * (1.0F - age * age));
			int size = age < 0.55F || spark % 3 == 0 ? 2 : 1;
			// What it was a moment ago, behind it, so it reads as something flying and not as a dot.
			int tx = cx + Math.round((float) Math.cos(angle) * reach * out * 0.86F);
			int ty = cy + Math.round((float) Math.sin(angle) * reach * out * 0.86F + 14.0F * age * age);
			g.fill(tx, ty, tx + 1, ty + 1, alpha / 2 << 24 | cool);
			g.fill(px, py, px + size, py + size, alpha << 24 | (age < 0.5F ? hot : cool));
		}
	}

	/**
	 * A colour of the panel, as the greater table has it.
	 *
	 * <p>The greater table's panel is the ordinary one recoloured — every pixel to its own grey, then
	 * toward slate and violet (see {@code generate_greater_table_gui}) — but only the texture went through
	 * that. Everything the screen paints itself, which is the tabs, the button and the three rows of
	 * techniques, stayed wood and sandstone: a warm brown "Forjar" on a violet bench. This is the same
	 * arithmetic, for one colour, so what the screen paints lands on what the texture was painted with.
	 */
	private int tone(int colour) {
		if (this.menu.station() != dev.forja.menu.Station.FORJA_MAYOR) {
			return colour;
		}
		int grey = (((colour >> 16) & 0xFF) * 299 + ((colour >> 8) & 0xFF) * 587 + (colour & 0xFF) * 114) / 1000;
		return colour & 0xFF000000
			| Math.min(255, (int) (grey * 0.55F) + 18) << 16
			| Math.min(255, (int) (grey * 0.50F) + 14) << 8
			| Math.min(255, (int) (grey * 0.68F) + 34);
	}

	/**
	 * The same for the one thing on the panel that is meant to stand out. Put through {@link #tone} the
	 * button comes out the grey of the bench it is on; this keeps it a colour — the violet the table's
	 * star burns in — where the forge's is the orange of its own.
	 */
	private int accent(int colour) {
		if (this.menu.station() != dev.forja.menu.Station.FORJA_MAYOR) {
			return colour;
		}
		int grey = (((colour >> 16) & 0xFF) * 299 + ((colour >> 8) & 0xFF) * 587 + (colour & 0xFF) * 114) / 1000;
		return colour & 0xFF000000
			| Math.min(255, (int) (grey * 0.62F) + 22) << 16
			| Math.min(255, (int) (grey * 0.42F) + 12) << 8
			| Math.min(255, (int) (grey * 0.98F) + 44);
	}

	/** The parts table has room for full tabs; the forge table wears them in its header band. */
	private boolean compactTabs() {
		return this.menu.station() != dev.forja.menu.Station.PIEZAS;
	}

	private int tabWidth() {
		return this.compactTabs() ? 46 : TAB_W;
	}

	private int tabHeight() {
		return this.compactTabs() ? 11 : TAB_H;
	}

	private int tabTop() {
		return this.compactTabs() ? 2 : TAB_Y;
	}

	private void drawTabs(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		List<Integer> modes = this.menu.station().modes;
		int tabW = this.tabWidth();
		int tabH = this.tabHeight();
		int tabTop = this.tabTop();
		for (int i = 0; i < modes.size(); i++) {
			int tabX = x + 5 + i * (tabW + 2);
			boolean active = this.menu.getMode() == modes.get(i);
			boolean hovered = inside(mouseX, mouseY, tabX, y + tabTop, tabW, tabH);
			int fill = this.tone(active ? 0xFFC6BBA7 : hovered ? 0xFF9A6E44 : 0xFF7E5632);
			bevel(g, tabX, y + tabTop, tabW, tabH + (active ? 2 : 0), fill,
				this.tone(active ? 0xFFE8DCC4 : 0xFFA87B4E), this.tone(active ? 0xFFC6BBA7 : 0xFF4A2F18));
			Component name = tabName(modes.get(i));
			int width = Math.round(this.font.width(name) * 0.8F);
			g.pose().pushMatrix();
			g.pose().translate(tabX + (tabW - width) / 2.0F, y + tabTop + (tabH - 6) / 2.0F);
			g.pose().scale(0.8F, 0.8F);
			g.text(this.font, name, 0, 0, active ? this.tone(TEXT) : ON_WOOD, false);
			g.pose().popMatrix();
			// A dot on the tab while a choice is waiting to be made.
			if (modes.get(i) == ForgeMenu.MODE_TECHNIQUES && !active
				&& dev.forja.forge.Techniques.pending(this.minecraft.player) > 0) {
				g.fill(tabX + tabW - 6, y + tabTop + 3, tabX + tabW - 2, y + tabTop + 7, 0xFFF0C070);
			}
		}
	}

	/** The three tiers, the three choices in each, and what each one is worth. */
	private void drawTechniques(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		var player = this.minecraft.player;
		int level = dev.forja.forge.SmithLevel.level(player);
		for (int tier = 1; tier <= dev.forja.forge.Technique.TIERS; tier++) {
			int rowY = y + TECH_ROW_Y + (tier - 1) * TECH_ROW_H;
			boolean unlocked = level >= dev.forja.forge.Technique.levelFor(tier);
			dev.forja.forge.Technique chosen = dev.forja.forge.Techniques.chosen(player, tier);
			bevel(g, x + 6, rowY, 194, 24, this.tone(unlocked ? 0xFFDCD2BC : 0xFFC2B9A6), this.tone(0xFFF2EAD6), this.tone(0xFF8C8271));
			if (!unlocked) {
				padlock(g, x + 189, rowY + 8);
			}
			List<dev.forja.forge.Technique> options = dev.forja.forge.Technique.ofTier(tier);
			for (int i = 0; i < options.size(); i++) {
				dev.forja.forge.Technique option = options.get(i);
				int bx = x + 10 + i * 20;
				int by = rowY + 3;
				boolean taken = option == chosen;
				boolean open = unlocked && chosen == null;
				// The one that was taken stays gold on either table: it is the one warm thing in the row.
				int fill = taken ? 0xFFF0D07A : this.tone(open ? 0xFFE8DCC4 : 0xFFBFB6A3);
				if (open) {
					// A choice is waiting: the three of them breathe, a third of a beat apart, so the eye is
					// led along the row instead of at one button.
					float beat = (float) ((Math.sin(System.currentTimeMillis() / 300.0 - i * 2.1) + 1.0) * 0.5);
					g.fill(bx - 1, by - 1, bx + 19, by + 19, (int) (70 + 150 * beat) << 24 | 0xF0C070);
				}
				bevel(g, bx, by, 18, 18, fill, taken ? 0xFFFFF0C0 : this.tone(0xFFF4ECDA), this.tone(0xFF8C8271));
				g.item(option.icon(), bx + 1, by + 1);
				if (!taken && chosen != null) {
					// The two not taken are shaded over: that choice is done with.
					g.fill(bx + 1, by + 1, bx + 17, by + 17, 0xA0403828);
				}
			}
			Component head = chosen != null
				? chosen.displayName()
				: unlocked
					? Component.translatable("gui.forja.tecnica.elige")
					: Component.translatable("gui.forja.tecnica.bloqueada", dev.forja.forge.Technique.levelFor(tier));
			this.small(g, head, x + 76, rowY + 4, this.tone(chosen != null ? 0xFF4A3A22 : unlocked ? 0xFF7A5A2A : 0xFF8C8271));
			Component note = chosen != null
				? chosen.description()
				: unlocked
					? Component.translatable("gui.forja.tecnica.aviso")
					: Component.translatable("gui.forja.tecnica.maestria", dev.forja.forge.Technique.levelFor(tier));
			this.small(g, note, x + 76, rowY + 13, this.tone(0xFF6B6455));
		}
		Component standing = level >= dev.forja.forge.SmithLevel.MAX_LEVEL
			? dev.forja.forge.SmithLevel.describe(player)
			: Component.translatable("gui.forja.herrero.corto", level, dev.forja.forge.SmithLevel.toNextLevel(player));
		this.smallCentered(g, standing, x + this.imageWidth / 2, y + 92, this.tone(0xFF4A4034));
		this.drawSmithTrack(g, x, y, player, level);
	}

	/**
	 * The smith's level as a track from nought to ten, with a pin at each level a tier of techniques opens
	 * on. The line of text above it says how much is left in numbers; this says where you are, and where
	 * the next choice is, at a glance.
	 */
	private void drawSmithTrack(GuiGraphicsExtractor g, int x, int y, net.minecraft.world.entity.player.Player player, int level) {
		int left = x + TRACK_X;
		int top = y + TRACK_Y;
		int max = dev.forja.forge.SmithLevel.MAX_LEVEL;
		float reached = level;
		if (level < max) {
			int floor = level * level * dev.forja.forge.SmithLevel.STEP;
			int ceiling = (level + 1) * (level + 1) * dev.forja.forge.SmithLevel.STEP;
			reached += Math.max(0.0F, Math.min(1.0F, (dev.forja.forge.SmithLevel.experience(player) - floor) / (float) (ceiling - floor)));
		}
		g.fill(left - 1, top - 1, left + TRACK_W + 1, top + TRACK_H + 1, this.tone(0xFF5A4A36));
		g.fill(left, top, left + TRACK_W, top + TRACK_H, 0xFF2B2216);
		int filled = Math.round(TRACK_W * reached / max);
		g.fill(left, top, left + filled, top + TRACK_H, 0xFFD9A441);
		g.fill(left, top, left + filled, top + 1, 0xFFFFE2A0);
		for (int mark = 1; mark < max; mark++) {
			int at = left + TRACK_W * mark / max;
			g.fill(at, top + TRACK_H - 1, at + 1, top + TRACK_H, 0x80000000);
		}
		for (int tier = 1; tier <= dev.forja.forge.Technique.TIERS; tier++) {
			int needed = dev.forja.forge.Technique.levelFor(tier);
			int at = left + TRACK_W * needed / max;
			boolean open = level >= needed;
			// The pin: a head above the track and a shank through it.
			g.fill(at - 1, top - 3, at + 2, top - 1, open ? 0xFFFFE2A0 : 0xFF8C8271);
			g.fill(at, top - 1, at + 1, top + TRACK_H, open ? 0xFFFFF4D6 : 0xFF6B6455);
		}
	}

	/** A small iron padlock, for a tier the smith has not reached. */
	private static void padlock(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x + 1, y, x + 5, y + 1, 0xFF6B6455);
		g.fill(x + 1, y + 1, x + 2, y + 4, 0xFF6B6455);
		g.fill(x + 4, y + 1, x + 5, y + 4, 0xFF6B6455);
		g.fill(x, y + 4, x + 6, y + 9, 0xFF5A5246);
		g.fill(x, y + 4, x + 6, y + 5, 0xFF8C8271);
		g.fill(x + 2, y + 6, x + 4, y + 8, 0xFF2B2216);
	}

	private void drawPartsTab(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		PartType[] parts = PartType.values();
		ForgeMaterial preview = ForgeMaterial.fromInput(this.menu.getSlot(ForgeMenu.MATERIAL_SLOT).getItem());
		PartType selected = this.menu.selectedPartType();
		boolean template = this.menu.hasTemplate();
		boolean canEngrave = this.menu.canEngrave();
		for (int i = 0; i < parts.length; i++) {
			int bx = x + GRID_X + i % GRID_COLUMNS * GRID_CELL;
			int by = y + GRID_Y + i / GRID_COLUMNS * GRID_CELL;
			boolean chosen = selected == parts[i];
			boolean hovered = canEngrave && inside(mouseX, mouseY, bx, by, GRID_CELL, GRID_CELL);
			int fill = chosen ? 0xFFD9A15A : hovered ? 0xFFB9AC94 : 0xFF9C8E78;
			bevel(g, bx, by, GRID_CELL, GRID_CELL, fill, chosen ? 0xFF7A4A18 : 0xFF5E5244, 0xFFEFE4CF);
			ForgeMaterial shown = preview != null && parts[i].accepts(preview) ? preview : ForgeMaterial.HIERRO;
			// Sixteen to a cell since the grid went to twelve across: the part fills it, and what says
			// "chosen" is the colour showing round the part rather than a frame the part would cover.
			g.item(Assembler.createPart(parts[i], shown), bx, by);
		}
		// Without a blank template the patterns cannot be picked; an engraved one keeps only its own lit.
		if (!canEngrave) {
			g.nextStratum();
			for (int i = 0; i < parts.length; i++) {
				if (selected != parts[i]) {
					int bx = x + GRID_X + i % GRID_COLUMNS * GRID_CELL;
					int by = y + GRID_Y + i / GRID_COLUMNS * GRID_CELL;
					g.fill(bx, by, bx + GRID_CELL, by + GRID_CELL, 0xA0C6BBA7);
				}
			}
		}

		Component status;
		int color = TEXT;
		ItemStack material = this.menu.getSlot(ForgeMenu.MATERIAL_SLOT).getItem();
		ForgeMaterial chosenMaterial = ForgeMaterial.fromInput(material);
		if (!template) {
			status = Component.translatable("gui.forja.plantilla.falta");
		} else if (selected == null) {
			status = Component.translatable("gui.forja.plantilla.grabar");
		} else if (!material.isEmpty() && chosenMaterial == null) {
			status = Component.translatable("gui.forja.material_invalido");
			color = BAD_ON_STONE;
		} else if (chosenMaterial != null && !selected.accepts(chosenMaterial)) {
			status = Component.translatable("gui.forja.material_blando", chosenMaterial.displayName());
			color = BAD_ON_STONE;
		} else if (chosenMaterial != null && material.getCount() < selected.cost) {
			status = Component.translatable("gui.forja.faltan", selected.cost - material.getCount(), chosenMaterial.displayName());
			color = BAD_ON_STONE;
		} else {
			status = Component.translatable("gui.forja.coste", selected.displayName(), selected.cost);
		}
		this.smallCentered(g, status, x + this.imageWidth / 2, y + PARTS_LABEL_Y, color);
	}

	private void drawForge(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		ForgeMenu.Action action = this.menu.action();
		ItemStack gear = this.menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
		ItemStack preview = this.menu.forgePreview();

		this.drawStarEmbers(g, x, y, action);

		// Ghost of the gear the star would forge, in the empty center.
		if ((action == ForgeMenu.Action.FORGE || action == ForgeMenu.Action.MERGE) && gear.isEmpty()) {
			g.item(preview, x + ForgeMenu.CENTER_X, y + ForgeMenu.CENTER_Y);
			g.nextStratum();
			g.fill(x + ForgeMenu.CENTER_X, y + ForgeMenu.CENTER_Y, x + ForgeMenu.CENTER_X + 16, y + ForgeMenu.CENTER_Y + 16, this.tone(0x907E705E));
		}

		Lines lines = new Lines();
		boolean pointsUsed = false;
		for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
			pointsUsed |= this.menu.getSlot(ForgeMenu.STAR_FIRST + i).hasItem();
		}
		UpgradeRecipes.Application application = this.menu.application();
		switch (action) {
			case FORGE -> this.statLines(preview, lines);
			case SWAP -> {
				lines.add(Component.translatable("gui.forja.estrella.cambio"), 0xFFFFB347);
				this.statLines(preview, lines, gear);
			}
			case UPGRADE -> this.upgradeLines(application, gear, lines);
			case DON -> {
				lines.add(Component.translatable("gui.forja.don.titulo"), 0xFFFFB347);
				dev.forja.forge.Perk perk = this.menu.perk();
				if (perk != null) {
					lines.add(perk.displayName(), 0xFF000000 | perk.color);
					lines.wrap(this.font, perk.description(), 0xFFE0E0E0);
					lines.wrap(this.font, Component.translatable("gui.forja.don.unico"), MUTED);
				}
			}
			case MERGE -> {
				dev.forja.upgrade.UpgradeOrb merged = dev.forja.item.UpgradeOrbItem.orb(preview);
				lines.add(Component.translatable("gui.forja.orbes.fusion"), 0xFFFFB347);
				if (merged != null) {
					lines.add(merged.upgrade().displayName(), 0xFF000000 | merged.upgrade().color);
					lines.add(Component.translatable("gui.forja.orbes.total", merged.percent()), GOOD);
					lines.wrap(this.font, merged.upgrade().effect(merged.percent()), 0xFFE0E0E0);
				}
			}
			case BOOK -> {
				lines.add(Component.translatable("gui.forja.libros.titulo"), 0xFFFFB347);
				for (ForgeMenu.BookTransfer transfer : this.menu.bookTransfers()) {
					lines.add(transfer.upgrade().displayName(), 0xFF000000 | transfer.upgrade().color);
					lines.add(Component.translatable("gui.forja.mejora.progreso", transfer.before(), transfer.after()), GOOD);
				}
				this.refusalLines(gear, lines);
			}
			case FUNDIR -> {
				lines.add(Component.translatable("gui.forja.fundir.titulo"), 0xFFFFB347);
				lines.add(preview.getHoverName(), 0xFFE0E0E0);
				lines.add(Component.translatable("gui.forja.fundir.devuelve", preview.getCount()), GOOD);
				lines.wrap(this.font, Component.translatable("gui.forja.fundir.aviso"), MUTED);
			}
			case REPAIR -> {
				int max = gear.getMaxDamage();
				int used = 0;
				for (int count : this.menu.repairUse()) {
					used += count;
				}
				lines.add(Component.translatable("gui.forja.reparar.titulo"), 0xFFFFB347);
				lines.add(gear.getHoverName(), 0xFFE0E0E0);
				lines.add(Component.translatable("gui.forja.reparar.durabilidad", max - gear.getDamageValue(), max - preview.getDamageValue(), max), GOOD);
				lines.bar(100 * (max - gear.getDamageValue()) / Math.max(1, max), 100 * (max - preview.getDamageValue()) / Math.max(1, max), 0x55FF55);
				lines.add(Component.translatable("gui.forja.reparar.usa", used), MUTED);
			}
			default -> {
				if (application != null) {
					this.upgradeLines(application, gear, lines);
				} else if (this.menu.beyondBench() != null) {
					lines.add(Component.translatable("gui.forja.mesa_mayor.1", this.menu.beyondBench().displayName()), 0xFFFFB347);
					lines.add(Component.translatable("gui.forja.mesa_mayor.2"), MUTED);
				} else if (gear.isEmpty() && this.menu.missingParts() != null) {
					lines.add(Component.translatable("gui.forja.faltan_piezas"), 0xFFFFB347);
					for (PartType part : this.menu.missingParts()) {
						lines.add(Component.literal("- ").append(part.displayName()), 0xFFE0E0E0);
					}
				} else if (pointsUsed && this.menu.refusal() != null) {
					this.refusalLines(gear, lines);
				} else if (pointsUsed) {
					lines.add(Component.translatable("gui.forja.mejora.no_sirve.1"), BAD);
					lines.add(Component.translatable("gui.forja.mejora.no_sirve.2"), BAD);
					lines.add(Component.translatable("gui.forja.mejora.no_sirve.3"), MUTED);
				} else if (!gear.isEmpty()) {
					this.statLines(gear, lines);
					// How far its upgrades go on the left, how much of them it carries on the right, and the
					// load itself drawn under both. (This line used to name the table's ceiling as well and ran
					// clean off the panel; the table says its own ceiling when it is what stops an upgrade.)
					int load = dev.forja.forge.Potential.load(gear);
					int capacity = dev.forja.forge.Potential.capacity(gear);
					Component held = Component.translatable("gui.forja.carga.numeros", load, capacity);
					Component potential = Component.translatable("gui.forja.potencial.corto", dev.forja.forge.Potential.of(gear));
					if (this.font.width(potential) + this.font.width(held) + 6 > Math.round((INFO_W - 8) / 0.75F)) {
						potential = Component.translatable("gui.forja.potencial.muy_corto", dev.forja.forge.Potential.of(gear));
					}
					lines.pair(potential, 0xFFC79BFF, held, load >= capacity ? 0xFFFFB347 : 0xFFE0E0E0);
					lines.load(gear, null);
					Upgrades upgrades = gear.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
					upgrades.percents().forEach((upgrade, percent) ->
						lines.add(Component.translatable("gui.forja.mejora.linea", upgrade.displayName(), percent), 0xFF000000 | upgrade.color));
				} else {
					for (int i = 1; i <= 4; i++) {
						lines.add(Component.translatable("gui.forja.estrella.ayuda." + i), MUTED);
					}
				}
			}
		}
		this.drawLines(g, lines, x + INFO_X + 3, y + INFO_Y + 3);

		boolean enabled = action != ForgeMenu.Action.NONE;
		boolean hovered = enabled && inside(mouseX, mouseY, x + FORGE_BUTTON_X, y + FORGE_BUTTON_Y, FORGE_BUTTON_W, FORGE_BUTTON_H);
		int fill = !enabled ? this.tone(0xFF8E8472) : this.accent(hovered ? 0xFFC98B45 : 0xFFA86F34);
		bevel(g, x + FORGE_BUTTON_X, y + FORGE_BUTTON_Y, FORGE_BUTTON_W, FORGE_BUTTON_H, fill,
			enabled ? this.accent(0xFFE6B57A) : this.tone(0xFFB0A690), enabled ? this.accent(0xFF5A3A18) : this.tone(0xFF5E5648));
		if (enabled && this.swingStart == 0L) {
			// A light that crosses the button now and then, the way it crosses a blade: the button is the
			// one thing on the panel to press, and when it can be pressed it should say so.
			long cycle = System.currentTimeMillis() % 2600L;
			if (cycle < 700L) {
				int sweep = (int) (cycle * (FORGE_BUTTON_W + 24) / 700L) - 12;
				for (int row = 1; row < FORGE_BUTTON_H - 1; row++) {
					int left = Math.max(1, sweep - row / 2);
					int right = Math.min(FORGE_BUTTON_W - 1, sweep - row / 2 + 7);
					if (right > left) {
						g.fill(x + FORGE_BUTTON_X + left, y + FORGE_BUTTON_Y + row, x + FORGE_BUTTON_X + right, y + FORGE_BUTTON_Y + row + 1, 0x48FFF2D0);
					}
				}
			}
		}
		String key = switch (action) {
			case SWAP -> "cambiar";
			case UPGRADE, BOOK -> "mejorar";
			case REPAIR -> "reparar";
			case MERGE -> "fusionar";
			case DON -> "grabar";
			case ALEACION -> "fundir";
			case FUNDIR -> "derretir";
			case HERENCIA -> "heredar";
			case RECALENTAR -> "recalentar";
			default -> "forjar";
		};
		g.centeredText(this.font, Component.translatable("gui.forja.boton." + key), x + FORGE_BUTTON_X + FORGE_BUTTON_W / 2, y + FORGE_BUTTON_Y + 4, enabled ? 0xFFFFFFFF : 0xFFCFC7B6);
		this.drawSwing(g, x, y);
		this.drawHeat(g, x, y, mouseX, mouseY);
		this.drawBurst(g, x, y);
	}

	/**
	 * A little forge fire under the button with the heat of the table, because which alloys a table can
	 * melt depends on what it is standing on and nothing else on screen would say so.
	 */
	private void drawHeat(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		dev.forja.forge.Alloys.Heat heat = this.menu.heat();
		int barX = x + FORGE_BUTTON_X;
		int barY = y + FORGE_BUTTON_Y - 7;
		int steps = dev.forja.forge.Alloys.Heat.values().length - 1;
		int cell = FORGE_BUTTON_W / steps;
		int[] colors = {0xFF6E6E7E, 0xFFE8A33C, 0xFFE2622B, 0xFFFFE45C};
		for (int i = 1; i <= steps; i++) {
			boolean reached = heat.ordinal() >= i;
			int left = barX + (i - 1) * cell;
			g.fill(left, barY, left + cell - 2, barY + 5, reached ? colors[i] : 0xFF4A4034);
			if (reached) {
				// A lick of light along the top of each cell that is lit, never two cells in step.
				int lick = (int) ((System.currentTimeMillis() / 140 + i * 3) % 4);
				g.fill(left + 1 + lick * (cell - 6) / 3, barY, left + 4 + lick * (cell - 6) / 3, barY + 1, 0xA0FFF4D6);
				g.fill(left, barY + 4, left + cell - 2, barY + 5, 0x40000000);
			}
		}
		if (inside(mouseX, mouseY, barX, barY, FORGE_BUTTON_W, 5)) {
			List<Component> lines = new java.util.ArrayList<>();
			lines.add(Component.translatable("gui.forja.calor", heat.displayName()));
			lines.add(Component.translatable(this.menu.wholeWorkshop() ? "gui.forja.taller.completo" : "gui.forja.taller.suelto"));
			g.setTooltipForNextFrame(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
		}
	}

	/**
	 * The press: while a swing is in the air the hammer runs along a rail under the button. Stopping it
	 * on the lit middle is a perfect forge, and a practised smith gets a wider middle to aim at.
	 */
	private void drawSwing(GuiGraphicsExtractor g, int x, int y) {
		if (this.swingStart == 0L) {
			return;
		}
		int railX = x + FORGE_BUTTON_X;
		int railY = y + FORGE_BUTTON_Y + FORGE_BUTTON_H + 2;
		float window = this.window();
		int center = railX + FORGE_BUTTON_W / 2;
		int half = Math.max(2, Math.round(FORGE_BUTTON_W * window));
		g.fill(railX - 1, railY - 1, railX + FORGE_BUTTON_W + 1, railY + 6, this.tone(0xFF5A3A18));
		g.fill(railX, railY, railX + FORGE_BUTTON_W, railY + 5, 0xFF2B2216);
		// The decent hit around the perfect one: a press that lands here still forges, and until now the
		// rail showed only the middle, so a near miss and a wild one looked the same until it was too late.
		int decent = Math.min(FORGE_BUTTON_W / 2, Math.round(FORGE_BUTTON_W * window * 2.2F));
		g.fill(center - decent, railY, center + decent, railY + 5, 0xFF7A4A1C);
		float beat = (float) ((Math.sin(System.currentTimeMillis() / 90.0) + 1.0) * 0.5);
		g.fill(center - half, railY, center + half, railY + 5, 0xFFE8A33C);
		g.fill(center - half, railY, center + half, railY + 1, (int) (120 + 135 * beat) << 24 | 0xFFF4D6);
		// The hammer: a head on a short haft, so which way is "down on the rail" is never in doubt.
		int marker = railX + Math.round((FORGE_BUTTON_W - 5) * this.swingPosition());
		boolean over = this.swingQuality() == 2;
		g.fill(marker + 2, railY - 4, marker + 3, railY + 1, 0xFF8A5A2C);
		g.fill(marker, railY - 1, marker + 5, railY + 3, over ? 0xFFFFFFFF : 0xFFE8E0D0);
		g.fill(marker, railY + 2, marker + 5, railY + 3, 0xFF8C8478);
	}

	/** Where the hammer is along the rail right now, from 0 to 1. */
	private float swingPosition() {
		float phase = (System.currentTimeMillis() - this.swingStart) % SWEEP_MILLIS / (float) SWEEP_MILLIS;
		return phase < 0.5F ? phase * 2.0F : 2.0F - phase * 2.0F;
	}

	/** Half the width of the perfect window, wider the more the smith has forged. */
	private float window() {
		boolean steady = dev.forja.forge.Techniques.has(this.minecraft.player, dev.forja.forge.Technique.PULSO_FIRME);
		return WINDOW + dev.forja.forge.SmithLevel.level(this.minecraft.player) * 0.01F
			+ (this.menu.wholeWorkshop() ? 0.02F : 0.0F)
			+ (steady ? dev.forja.forge.Technique.PULSE_WINDOW * 0.01F : 0.0F);
	}

	/** The quality of a press stopped now: 2 for perfect, 1 for a decent hit, 0 for a miss. */
	private int swingQuality() {
		float off = Math.abs(this.swingPosition() - 0.5F);
		float window = this.window();
		if (off <= window) {
			return 2;
		}
		return off <= window * 2.2F ? 1 : 0;
	}

	/** What an orb, a book or an inheritance brought and had turned away whole, and why. */
	private void refusalLines(ItemStack gear, Lines lines) {
		ForgeMenu.Refusal refusal = this.menu.refusal();
		if (refusal == null) {
			return;
		}
		Upgrade left = refusal.upgrade();
		lines.add(left.displayName(), 0xFF000000 | left.color);
		if (refusal.limit() == dev.forja.forge.Potential.Limit.LOAD) {
			lines.wrap(this.font, Component.translatable("gui.forja.carga.no_cabe", dev.forja.forge.Potential.weight(left),
				Math.max(0, dev.forja.forge.Potential.capacity(gear) - dev.forja.forge.Potential.load(gear))), 0xFFFFB347);
			lines.load(gear, left);
		} else {
			lines.wrap(this.font, refusal.limit().message(0), 0xFFFFB347);
		}
	}

	private void upgradeLines(UpgradeRecipes.Application application, ItemStack gear, Lines lines) {
		Upgrade upgrade = application.upgrade();
		int weight = dev.forja.forge.Potential.weight(upgrade);
		// A new one says what it weighs, because that is the price the ingredients do not show. Beside the
		// progress and not beside the name: a name can be as long as the panel is wide.
		boolean weighed = application.before() <= 0 && weight > 0;
		lines.add(upgrade.displayName(), 0xFF000000 | upgrade.color);
		if (application.limit() == dev.forja.forge.Potential.Limit.LOAD) {
			lines.wrap(this.font, Component.translatable("gui.forja.carga.no_cabe", weight,
				Math.max(0, dev.forja.forge.Potential.capacity(gear) - dev.forja.forge.Potential.load(gear))), 0xFFFFB347);
			lines.load(gear, upgrade);
			lines.wrap(this.font, Component.translatable("gui.forja.carga.consejo"), MUTED);
			return;
		}
		if (application.conflict() != null) {
			lines.add(Component.translatable("gui.forja.mejora.incompatible"), BAD);
			lines.add(application.conflict().displayName(), BAD);
		} else if (application.result().isEmpty() && application.before() >= dev.forja.forge.Potential.MOST) {
			lines.add(Component.translatable("gui.forja.mejora.maxima"), GOOD);
			lines.wrap(this.font, upgrade.effect(application.before()), MUTED);
		} else if (application.result().isEmpty()) {
			// Not finished: stopped. The line says by what, because "it will not go in" with no reason
			// given is a bug report waiting to be written.
			this.progressLine(lines, application.before(), application.before(), MUTED, weighed ? weight : 0);
			lines.wrap(this.font, application.limit().message(application.ceiling()), 0xFFFFB347);
		} else {
			this.progressLine(lines, application.before(), application.after(), GOOD, weighed ? weight : 0);
			lines.bar(application.before(), application.after(), upgrade.color);
			if (application.held()) {
				lines.wrap(this.font, application.limit().message(application.ceiling()), 0xFFFFB347);
			} else if (application.before() <= 0 && weight > 0) {
				lines.load(gear, upgrade);
			}
			lines.wrap(this.font, upgrade.effect(application.after()), 0xFFE0E0E0);
		}
	}

	/** "12% -> 40%", and at the far end of the same line what the upgrade weighs, when it is a new one. */
	private void progressLine(Lines lines, int before, int after, int color, int weight) {
		Component progress = Component.translatable("gui.forja.mejora.progreso", before, after);
		if (weight > 0) {
			lines.pair(progress, color, Component.translatable("gui.forja.carga.peso", weight), 0xFFD8C8A8);
		} else {
			lines.add(progress, color);
		}
	}

	private void drawDisassembleTab(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		ItemStack target = this.menu.getSlot(ForgeMenu.DISASSEMBLE_SLOT).getItem();
		if (target.isEmpty()) {
			this.small(g, Component.translatable("gui.forja.desarmar.ayuda.1"), x + BUTTON_X, y + 32, TEXT);
			this.small(g, Component.translatable("gui.forja.desarmar.ayuda.2"), x + BUTTON_X, y + 41, TEXT);
			this.small(g, Component.translatable("gui.forja.desarmar.ayuda.3"), x + BUTTON_X, y + 50, TEXT);
			return;
		}

		Assembler.Disassembly disassembly = Assembler.disassemble(target);
		int slotX = x + BUTTON_X;
		for (ItemStack part : disassembly.returned()) {
			bevel(g, slotX, y + 44, 18, 18, 0xFF8C806E, 0xFF3C3228, 0xFFF6EEDE);
			g.item(part, slotX + 1, y + 45);
			slotX += 22;
		}
		for (ItemStack part : disassembly.lost()) {
			bevel(g, slotX, y + 44, 18, 18, 0xFF8B5050, 0xFF3C3228, 0xFFF6EEDE);
			g.item(part, slotX + 1, y + 45);
			g.nextStratum();
			g.fill(slotX + 1, y + 45, slotX + 17, y + 61, 0x90A00000);
			g.text(this.font, "X", slotX + 6, y + 49, 0xFFFFFFFF, true);
			slotX += 22;
		}
		// Upgrade orbs go in a row above the parts.
		int orbX = x + BUTTON_X;
		for (int i = 0; i < Math.min(ORB_ROW, disassembly.orbs().size()); i++) {
			bevel(g, orbX, y + 22, 18, 18, 0xFF6E5A8C, 0xFF3C3228, 0xFFF6EEDE);
			g.item(disassembly.orbs().get(i), orbX + 1, y + 23);
			orbX += 20;
		}
		if (!disassembly.orbs().isEmpty()) {
			this.small(g, Component.translatable("gui.forja.desarmar.orbes"), x + 20, y + 28, TEXT);
		}
		if (disassembly.orbs().size() > ORB_ROW) {
			this.small(g, Component.literal("+" + (disassembly.orbs().size() - ORB_ROW)), orbX + 1, y + 28, TEXT);
		}

		boolean hovered = inside(mouseX, mouseY, x + BUTTON_X, y + BUTTON_Y, BUTTON_W, BUTTON_H);
		bevel(g, x + BUTTON_X, y + BUTTON_Y, BUTTON_W, BUTTON_H, hovered ? 0xFFC98B45 : 0xFFA86F34, 0xFFE6B57A, 0xFF5A3A18);
		g.centeredText(this.font, Component.translatable("gui.forja.desarmar.boton"), x + BUTTON_X + BUTTON_W / 2, y + BUTTON_Y + 4, 0xFFFFFFFF);

		int lineY = y + 88;
		if (!disassembly.lost().isEmpty()) {
			this.small(g, Component.translatable("gui.forja.desarmar.desgastada"), x + 20, lineY, BAD_ON_STONE);
			lineY += 8;
		}
	}

	private void statLines(ItemStack result, Lines lines) {
		this.statLines(result, lines, ItemStack.EMPTY);
	}

	/** The stats of an item; against another item of the same kind each line also shows how much it changes. */
	private void statLines(ItemStack result, Lines lines, ItemStack compare) {
		ForgedParts parts = result.get(ModComponents.PARTS);
		lines.add(result.getHoverName(), parts != null ? 0xFF000000 | parts.primary().color : 0xFFFFFFFF);
		if (parts == null) {
			return;
		}
		Upgrades upgrades = result.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		java.util.Map<ForgeStats.Stat, Double> before = ForgeStats.values(compare);
		for (ForgeStats.Line line : ForgeStats.sheet(result, parts).lines()) {
			if (ForgeStats.shown(line)) {
				lines.add(ForgeStats.withDelta(line.text(), line, before.get(line.stat())), 0xFF000000 | ForgeStats.color(parts.type(), line));
			}
		}
		dev.forja.forge.Perk engraved = dev.forja.forge.Perk.of(result);
		if (engraved != null) {
			lines.add(Component.translatable("tooltip.forja.don", engraved.displayName()), 0xFF000000 | engraved.color);
		}
		if (Mastery.experience(result) > 0) {
			lines.add(Mastery.describe(result), 0xFFFFC857);
			int level = Mastery.level(result);
			if (level < Mastery.MAX_LEVEL) {
				// Progress from this level's threshold to the next, as a percentage for the bar.
				int floor = Mastery.experienceFor(level);
				int ceiling = Mastery.experienceFor(level + 1);
				int progress = Math.round(100.0F * (Mastery.experience(result) - floor) / (ceiling - floor));
				lines.bar(progress, progress, 0xFFC857);
			}
		}
		for (ForgeMaterial.Trait trait : ForgeMaterial.Trait.values()) {
			if (trait != ForgeMaterial.Trait.NONE && parts.hasTrait(trait)) {
				lines.add(Component.translatable("gui.forja.stat.rasgo", trait.displayName()), 0xFFFFD37F);
			}
		}
	}

	/** Small text lines for the info panel; a null text entry is a progress bar. */
	/** Where the info panel drew a load bar this frame, and whose, for the tooltip; null when it drew none. */
	private int @Nullable [] loadBarAt;
	private ItemStack loadBarOf = ItemStack.EMPTY;

	private static final class Lines {
		/** What a line carries besides its text: something said at its right-hand end, or a load to draw. */
		private record Extra(@Nullable FormattedCharSequence right, int rightColor, @Nullable ItemStack gear, @Nullable Upgrade incoming) {
		}

		final List<FormattedCharSequence> text = new ArrayList<>();
		final List<Integer> colors = new ArrayList<>();
		final List<int[]> bars = new ArrayList<>();
		final java.util.Map<Integer, Extra> extras = new java.util.HashMap<>();

		void add(Component line, int color) {
			this.text.add(line.getVisualOrderText());
			this.colors.add(color);
			this.bars.add(null);
		}

		/** One line said from both ends: a thing on the left and its number on the right. */
		void pair(Component left, int leftColor, Component right, int rightColor) {
			this.extras.put(this.text.size(), new Extra(right.getVisualOrderText(), rightColor, null, null));
			this.add(left, leftColor);
		}

		/** The piece's load, drawn (see LoadBar), with whatever is on its way onto it. */
		void load(ItemStack gear, @Nullable Upgrade incoming) {
			this.extras.put(this.text.size(), new Extra(null, 0, gear, incoming));
			this.text.add(null);
			this.colors.add(0);
			this.bars.add(null);
		}

		void wrap(Font font, Component line, int color) {
			for (FormattedCharSequence part : font.split(line, Math.round((INFO_W - 6) / 0.75F))) {
				this.text.add(part);
				this.colors.add(color);
				this.bars.add(null);
			}
		}

		void bar(int before, int after, int color) {
			this.text.add(null);
			this.colors.add(0);
			this.bars.add(new int[] {before, after, color});
		}
	}

	private void drawLines(GuiGraphicsExtractor g, Lines lines, int x, int y) {
		this.loadBarAt = null;
		int lineY = y;
		for (int i = 0; i < lines.text.size() && lineY <= y + INFO_H - 10; i++) {
			int[] bar = lines.bars.get(i);
			Lines.Extra extra = lines.extras.get(i);
			if (extra != null && extra.gear() != null) {
				LoadBar.draw(g, x, lineY + 1, 4, extra.gear(), extra.incoming(), null);
				this.loadBarAt = new int[] {x, lineY};
				this.loadBarOf = extra.gear();
			} else if (bar != null) {
				int width = INFO_W - 8;
				g.fill(x, lineY + 1, x + width, lineY + 5, 0xFF505050);
				g.fill(x, lineY + 1, x + width * bar[1] / 100, lineY + 5, 0xFF000000 | bar[2]);
				g.fill(x, lineY + 1, x + width * bar[0] / 100, lineY + 5, 0xFF000000 | GuideText.darken(bar[2]));
			} else {
				g.pose().pushMatrix();
				g.pose().translate(x, lineY);
				g.pose().scale(0.75F, 0.75F);
				g.text(this.font, lines.text.get(i), 0, 0, lines.colors.get(i), false);
				if (extra != null && extra.right() != null) {
					g.text(this.font, extra.right(), Math.round((INFO_W - 8) / 0.75F) - this.font.width(extra.right()), 0, extra.rightColor(), false);
				}
				g.pose().popMatrix();
			}
			lineY += 8;
		}
	}

	private void small(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75F, 0.75F);
		g.text(this.font, text, 0, 0, color, false);
		g.pose().popMatrix();
	}

	private void smallCentered(GuiGraphicsExtractor g, Component text, int centerX, int y, int color) {
		int width = Math.round(this.font.width(text) * 0.75F);
		this.small(g, text, centerX - width / 2, y, color);
	}

	private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int topLeft, int bottomRight) {
		g.fill(x, y, x + w, y + h, fill);
		g.fill(x, y, x + w - 1, y + 1, topLeft);
		g.fill(x, y, x + 1, y + h - 1, topLeft);
		g.fill(x + 1, y + h - 1, x + w, y + h, bottomRight);
		g.fill(x + w - 1, y + 1, x + w, y + h, bottomRight);
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	// ------------------------------------------------------------------ tooltips and input

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		int x = this.leftPos;
		int y = this.topPos;
		if (this.menu.getMode() == ForgeMenu.MODE_FORGE && this.loadBarAt != null
			&& inside(mouseX, mouseY, this.loadBarAt[0], this.loadBarAt[1] - 1, LoadBar.width(4), LoadBar.HEIGHT + 3)) {
			// The bar is new to everyone who meets it: pointing at it says what each run of colour is.
			g.setTooltipForNextFrame(GuideText.wrap(this.font, LoadBar.legend(this.loadBarOf)), mouseX, mouseY);
			return;
		}
		switch (this.menu.getMode()) {
			case ForgeMenu.MODE_PARTS -> {
				PartType[] parts = PartType.values();
				ForgeMaterial preview = ForgeMaterial.fromInput(this.menu.getSlot(ForgeMenu.MATERIAL_SLOT).getItem());
				for (int i = 0; i < parts.length; i++) {
					if (inside(mouseX, mouseY, x + GRID_X + i % GRID_COLUMNS * GRID_CELL,
						y + GRID_Y + i / GRID_COLUMNS * GRID_CELL, GRID_CELL, GRID_CELL)) {
						List<Component> tooltip = new ArrayList<>();
						tooltip.add(parts[i].displayName());
						tooltip.add(Component.translatable("gui.forja.coste_tooltip", parts[i].cost).withColor(0xAAAAAA));
						tooltip.add(Component.translatable("gui.forja.rol." + parts[i].role.name().toLowerCase(Locale.ROOT)).withColor(0xAAAAAA));
						ForgeMaterial shown = preview != null && parts[i].accepts(preview) ? preview : ForgeMaterial.HIERRO;
						tooltip.add(Component.translatable("gui.forja.con_material", shown.displayName()).withColor(shown.color));
						for (ForgeStats.Line line : ForgeStats.partLines(parts[i], shown)) {
							if (ForgeStats.shown(line)) {
								tooltip.add(ForgeStats.colored(parts[i], line));
							}
						}
						String hint = this.menu.canEngrave() ? "gui.forja.plantilla.clic" : this.menu.hasTemplate() ? "gui.forja.plantilla.fija" : "gui.forja.plantilla.falta";
						tooltip.add(Component.translatable(hint).withColor(0xFFD37F));
						g.setTooltipForNextFrame(GuideText.wrap(this.font, tooltip), mouseX, mouseY);
					}
				}
			}
			case ForgeMenu.MODE_TECHNIQUES -> {
				for (dev.forja.forge.Technique technique : dev.forja.forge.Technique.values()) {
					List<dev.forja.forge.Technique> options = dev.forja.forge.Technique.ofTier(technique.tier);
					int bx = x + 10 + options.indexOf(technique) * 20;
					int by = y + TECH_ROW_Y + (technique.tier - 1) * TECH_ROW_H + 3;
					if (!inside(mouseX, mouseY, bx, by, 18, 18)) {
						continue;
					}
					List<Component> tooltip = new ArrayList<>();
					tooltip.add(technique.displayName());
					tooltip.add(technique.description().copy().withColor(0xAAAAAA));
					var player = this.minecraft.player;
					boolean taken = dev.forja.forge.Techniques.has(player, technique);
					dev.forja.forge.Technique chosen = dev.forja.forge.Techniques.chosen(player, technique.tier);
					if (taken) {
						tooltip.add(Component.translatable("gui.forja.tecnica.tuya").withColor(0x7FD97F));
					} else if (chosen != null) {
						tooltip.add(Component.translatable("gui.forja.tecnica.perdida", chosen.displayName()).withColor(0xC08070));
					} else if (dev.forja.forge.SmithLevel.level(player) < technique.levelNeeded()) {
						tooltip.add(Component.translatable("gui.forja.tecnica.bloqueada", technique.levelNeeded()).withColor(0xC08070));
					} else {
						tooltip.add(Component.translatable("gui.forja.tecnica.clic").withColor(0xFFD37F));
					}
					g.setTooltipForNextFrame(GuideText.wrap(this.font, tooltip), mouseX, mouseY);
				}
			}
			case ForgeMenu.MODE_FORGE -> {
				if ((this.menu.action() == ForgeMenu.Action.FORGE || this.menu.action() == ForgeMenu.Action.MERGE) && !this.menu.getSlot(ForgeMenu.CENTER_SLOT).hasItem()
					&& inside(mouseX, mouseY, x + ForgeMenu.CENTER_X, y + ForgeMenu.CENTER_Y, 16, 16)) {
					g.setTooltipForNextFrame(this.font, this.menu.forgePreview(), mouseX, mouseY);
				}
			}
			default -> {
				ItemStack target = this.menu.getSlot(ForgeMenu.DISASSEMBLE_SLOT).getItem();
				if (!target.isEmpty()) {
					Assembler.Disassembly disassembly = Assembler.disassemble(target);
					List<ItemStack> shown = new ArrayList<>(disassembly.returned());
					shown.addAll(disassembly.lost());
					for (int i = 0; i < shown.size(); i++) {
						if (inside(mouseX, mouseY, x + BUTTON_X + i * 22, y + 44, 18, 18)) {
							g.setTooltipForNextFrame(this.font, shown.get(i), mouseX, mouseY);
						}
					}
					for (int i = 0; i < Math.min(ORB_ROW, disassembly.orbs().size()); i++) {
						if (inside(mouseX, mouseY, x + BUTTON_X + i * 20, y + 22, 18, 18)) {
							g.setTooltipForNextFrame(this.font, disassembly.orbs().get(i), mouseX, mouseY);
						}
					}
				}
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int x = this.leftPos;
		int y = this.topPos;
		List<Integer> modes = this.menu.station().modes;
		if (modes.size() > 1) {
			int tabW = this.tabWidth();
			for (int i = 0; i < modes.size(); i++) {
				if (inside(event.x(), event.y(), x + 5 + i * (tabW + 2), y + this.tabTop(), tabW, this.tabHeight())
					&& this.menu.getMode() != modes.get(i)) {
					this.pressButton(ForgeMenu.BUTTON_TAB + modes.get(i));
					return true;
				}
			}
		}
		if (this.menu.getMode() == ForgeMenu.MODE_TECHNIQUES) {
			for (dev.forja.forge.Technique technique : dev.forja.forge.Technique.values()) {
				List<dev.forja.forge.Technique> options = dev.forja.forge.Technique.ofTier(technique.tier);
				int bx = x + 10 + options.indexOf(technique) * 20;
				int by = y + TECH_ROW_Y + (technique.tier - 1) * TECH_ROW_H + 3;
				if (inside(event.x(), event.y(), bx, by, 18, 18)
					&& dev.forja.forge.Techniques.canLearn(this.minecraft.player, technique)) {
					this.pressButton(ForgeMenu.BUTTON_TECHNIQUE + technique.ordinal());
					return true;
				}
			}
			return super.mouseClicked(event, doubleClick);
		}
		if (this.menu.getMode() == ForgeMenu.MODE_PARTS && this.menu.canEngrave()) {
			for (int i = 0; i < PartType.values().length; i++) {
				if (inside(event.x(), event.y(), x + GRID_X + i % GRID_COLUMNS * GRID_CELL,
					y + GRID_Y + i / GRID_COLUMNS * GRID_CELL, GRID_CELL, GRID_CELL)) {
					this.pressButton(i);
					return true;
				}
			}
		}
		if (this.menu.getMode() == ForgeMenu.MODE_FORGE && this.menu.action() != ForgeMenu.Action.NONE
			&& inside(event.x(), event.y(), x + FORGE_BUTTON_X, y + FORGE_BUTTON_Y, FORGE_BUTTON_W, FORGE_BUTTON_H)) {
			// Only a piece being born takes a timed press; swaps, upgrades and repairs go straight through.
			if (this.menu.action() != ForgeMenu.Action.FORGE) {
				this.swingStart = 0L;
				this.pressButton(ForgeMenu.BUTTON_FORGE);
				return true;
			}
			if (this.swingStart == 0L) {
				this.swingStart = System.currentTimeMillis();
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				return true;
			}
			int quality = this.swingQuality();
			this.swingStart = 0L;
			this.pressButton(ForgeMenu.BUTTON_PRESS + quality);
			return true;
		}
		if (this.menu.getMode() == ForgeMenu.MODE_DISASSEMBLE && this.menu.getSlot(ForgeMenu.DISASSEMBLE_SLOT).hasItem()
			&& inside(event.x(), event.y(), x + BUTTON_X, y + BUTTON_Y, BUTTON_W, BUTTON_H)) {
			this.pressButton(ForgeMenu.BUTTON_DISASSEMBLE);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void pressButton(int id) {
		boolean work = id == ForgeMenu.BUTTON_FORGE || id >= ForgeMenu.BUTTON_PRESS && id <= ForgeMenu.BUTTON_PRESS + 2;
		if (this.menu.clickMenuButton(this.minecraft.player, id)) {
			if (work) {
				// Anything the button does that is not a timed press is simply done well.
				this.burstStart = System.currentTimeMillis();
				this.burstQuality = id >= ForgeMenu.BUTTON_PRESS ? id - ForgeMenu.BUTTON_PRESS : 1;
			}
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	/** For the client test: start the strike's sparks as a press of that quality would. */
	public void showBurst(int quality) {
		this.burstStart = System.currentTimeMillis();
		this.burstQuality = quality;
	}

	/** GUI coordinates of a point given relative to the panel's corner, for the client test's cursor. */
	public double[] guiPoint(int x, int y) {
		return new double[] {this.leftPos + x, this.topPos + y};
	}
}
