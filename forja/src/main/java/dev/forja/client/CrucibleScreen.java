package dev.forja.client;

import java.util.List;

import dev.forja.Forja;
import dev.forja.forge.Alloys;
import dev.forja.material.ForgeMaterial;
import dev.forja.menu.CrucibleMenu;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The crucible's screen.
 *
 * <p>The point of it is the basin. What is melting is drawn as a pool that rises with how full the pot
 * is, takes the colour of the metal that is in it, and moves — a surface that ripples, a shine that
 * crosses it, and sparks coming off it while the fire is lit. Everything a player needs to know about
 * the pot is in that pool: how full it is, what is in it, whether it is running, and how close the pour
 * is. The numbers underneath it are for when the answer matters exactly.
 */
public class CrucibleScreen extends AbstractContainerScreen<CrucibleMenu> {
	private static final Identifier TEXTURE = Forja.id("textures/gui/crisol.png");

	private static final int TEXT = 0xFF3B2A1A;
	static final int ON_WOOD = 0xFFF2E3C6;
	private static final int MUTED = 0xFF6B6455;
	private static final int GOOD = 0xFF2E7D32;
	private static final int BAD = 0xFFA02020;

	/**
	 * The pot. The melt is drawn as a rectangle this size and the texture's own cut-out of the panel —
	 * the same sheet, from {@link #MASK_V} down, with a hole the shape of the pot — goes back over it,
	 * which is what gives a rectangle of orange a belly and a round bottom.
	 */
	private static final int POOL_X = 58;
	private static final int POOL_Y = 24;
	private static final int POOL_W = 64;
	private static final int POOL_H = 54;
	private static final int MASK_V = 198;

	/** The gauge beside the fuel slot. */
	private static final int GAUGE_X = 22;
	private static final int GAUGE_Y = 62;
	private static final int GAUGE_W = 5;
	private static final int GAUGE_H = 17;

	/** The channel the pour runs down, from the lip of the basin to the output slot. */
	private static final int ARROW_X = 122;
	private static final int ARROW_Y = 54;
	private static final int ARROW_W = 19;

	/** Under the output slot: what the fire under this pot reaches, and how much the pot holds. */
	private static final int SIDE_X = 156;
	private static final int SIDE_Y = 71;
	/** Under the pot: what it is doing, on two lines if it takes two. */
	private static final int STATUS_X = 52;
	private static final int STATUS_Y = 86;
	private static final int STATUS_W = 146;

	private static final int IMAGE_WIDTH = 206;
	private static final int IMAGE_HEIGHT = 196;

	public CrucibleScreen(CrucibleMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.inventoryLabelX = CrucibleMenu.INVENTORY_X;
		this.inventoryLabelY = CrucibleMenu.INVENTORY_Y - 11;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		this.drawPool(g, x, y);
		this.drawGauge(g, x, y);
		this.drawChannel(g, x, y);
	}

	/** The melt itself: a level that rises, a surface that moves, and sparks while the fire is in it. */
	private void drawPool(GuiGraphicsExtractor g, int x, int y) {
		float fill = this.menu.fill();
		int colour = this.meltColour();
		int bottom = y + POOL_Y + POOL_H;
		int height = Math.round(POOL_H * fill);
		if (height <= 0) {
			return;
		}
		int top = bottom - height;
		long time = System.currentTimeMillis();
		boolean lit = this.menu.isLit();
		if (lit) {
			// The light the melt throws up the inside of the pot.
			g.fillGradient(x + POOL_X, Math.max(y + POOL_Y, top - 14), x + POOL_X + POOL_W, top, 0x00FFB050, 0x58FFB050);
		}
		// The body of the melt, darker the deeper it goes, because a pot of metal is not a flat colour.
		for (int row = 0; row < height; row++) {
			float depth = row / (float) Math.max(1, height);
			int shade = shade(colour, 0.68F + 0.32F * depth);
			g.fill(x + POOL_X, bottom - row - 1, x + POOL_X + POOL_W, bottom - row, shade);
		}
		// The surface, which is where the movement reads: a wave running across the width of the pot.
		for (int column = 0; column < POOL_W; column++) {
			double wave = Math.sin((time / 260.0) + column * 0.26) + Math.sin((time / 410.0) + column * 0.13);
			int lift = (int) Math.round(wave * 1.2);
			int surface = Math.max(y + POOL_Y, top + lift);
			g.fill(x + POOL_X + column, surface, x + POOL_X + column + 1, surface + 2, shade(colour, 1.35F));
			g.fill(x + POOL_X + column, surface + 2, x + POOL_X + column + 1, surface + 3, shade(colour, 1.1F));
		}
		if (!lit) {
			this.drawPot(g, x, y);
			return;
		}
		// Bubbles working their way up through it: each one is born at the bottom, rises, and spends the
		// last tenth of its life as a flat ring on the surface.
		for (int bubble = 0; bubble < 4; bubble++) {
			long period = 1500L + bubble * 370L;
			long turn = (time + bubble * 911L) / period;
			float life = (time + bubble * 911L) % period / (float) period;
			int column = 8 + (int) ((bubble * 5347L + turn * 7919L) % (POOL_W - 16));
			if (life > 0.9F) {
				g.fill(x + POOL_X + column - 1, top + 1, x + POOL_X + column + 3, top + 2, shade(colour, 1.55F));
				continue;
			}
			int at = bottom - 3 - Math.round((height - 5) * life / 0.9F);
			if (height > 8 && at > top + 2) {
				g.fill(x + POOL_X + column, at, x + POOL_X + column + 2, at + 2, shade(colour, 1.25F));
				g.fill(x + POOL_X + column, at, x + POOL_X + column + 1, at + 1, shade(colour, 1.6F));
			}
		}
		// A shine crossing the surface, and a few sparks lifting off it.
		int glint = (int) ((time / 22) % (POOL_W + 40)) - 20;
		for (int column = Math.max(0, glint - 6); column < Math.min(POOL_W, glint + 6); column++) {
			double wave = Math.sin((time / 260.0) + column * 0.26) + Math.sin((time / 410.0) + column * 0.13);
			int surface = Math.max(y + POOL_Y, top + (int) Math.round(wave * 1.2));
			g.fill(x + POOL_X + column, surface, x + POOL_X + column + 1, surface + 2, 0xFFFFF3D0);
		}
		for (int spark = 0; spark < 5; spark++) {
			long phase = (time / 7 + spark * 320L) % 420L;
			if (phase > 260) {
				continue;
			}
			int column = (int) ((spark * 4013L + (time / 420 + spark) * 977L) % POOL_W);
			int rise = (int) (phase / 12);
			int at = top - rise;
			if (at > y + POOL_Y) {
				g.fill(x + POOL_X + column, at, x + POOL_X + column + 1, at + 1, shade(colour, 1.5F));
			}
		}
		this.drawPot(g, x, y);
	}

	/** The panel, put back over everything that is not inside the pot. */
	private void drawPot(GuiGraphicsExtractor g, int x, int y) {
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + POOL_X, y + POOL_Y, 0.0F, MASK_V, POOL_W, POOL_H, 256, 256);
	}

	/** What is left of the ember, drawn as a column that burns down. */
	private void drawGauge(GuiGraphicsExtractor g, int x, int y) {
		float left = this.menu.emberLeft();
		if (left <= 0.0F) {
			return;
		}
		int height = Math.max(1, Math.round(GAUGE_H * left));
		int bottom = y + GAUGE_Y + GAUGE_H;
		// A flicker, so a crucible that is running never looks like a still picture.
		int flicker = (int) ((System.currentTimeMillis() / 90) % 2);
		g.fill(x + GAUGE_X, bottom - height, x + GAUGE_X + GAUGE_W, bottom, 0xFFB5480F);
		g.fill(x + GAUGE_X + 1, bottom - height + flicker, x + GAUGE_X + GAUGE_W - 1, bottom - 1, 0xFFFF9A3C);
		g.fill(x + GAUGE_X + 2, bottom - height + 1 + flicker, x + GAUGE_X + GAUGE_W - 2, bottom - height + 4, 0xFFFFE6A8);
	}

	/** The pour running down the channel toward the slot it lands in. */
	private void drawChannel(GuiGraphicsExtractor g, int x, int y) {
		float progress = this.menu.pourProgress();
		if (progress <= 0.0F) {
			return;
		}
		int width = Math.round(ARROW_W * progress);
		int colour = this.meltColour();
		g.fill(x + ARROW_X, y + ARROW_Y, x + ARROW_X + width, y + ARROW_Y + 4, shade(colour, 1.0F));
		g.fill(x + ARROW_X, y + ARROW_Y, x + ARROW_X + width, y + ARROW_Y + 1, shade(colour, 1.3F));
		// The head of the pour, brighter than what follows it.
		g.fill(x + ARROW_X + Math.max(0, width - 2), y + ARROW_Y, x + ARROW_X + width, y + ARROW_Y + 4, shade(colour, 1.5F));
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
		// On the wood of the header, so in the colour everything else written on wood is in: the title
		// used to be the panel's dark brown on a dark brown plank, and could not be read.
		g.text(this.font, this.title, 8, 5, ON_WOOD, true);

		// What it is doing goes under the pot, where there is the width for it and room for a second line;
		// the two facts about the pot itself go under the slot it pours into.
		int colour = this.menu.pourProgress() > 0.0F || this.menu.isLit() ? GOOD : MUTED;
		lines(g, this.font, this.doing(), STATUS_X, STATUS_Y, STATUS_W, 2, colour);
		Alloys.Heat heat = Alloys.Heat.values()[Math.min(this.menu.heat(), Alloys.Heat.values().length - 1)];
		centred(g, this.font, Component.translatable("gui.forja.crisol.calor", heat.displayName()), SIDE_X, SIDE_Y, MUTED);
		int inside = this.menu.first().getCount() + this.menu.second().getCount();
		centred(g, this.font, Component.translatable("gui.forja.crisol.cabida", inside, this.menu.capacity()),
			SIDE_X, SIDE_Y + 8, inside >= this.menu.capacity() ? BAD : MUTED);
	}

	/** What the crucible would make out of what is in it, in words. */
	private Component doing() {
		List<ItemStack> inputs = List.of(this.menu.first(), this.menu.second());
		Alloys.Heat heat = Alloys.Heat.values()[Math.min(this.menu.heat(), Alloys.Heat.values().length - 1)];
		Alloys.Recipe recipe = Alloys.match(inputs, heat);
		if (recipe != null) {
			return Component.translatable("gui.forja.crisol.cuela", recipe.displayName());
		}
		// Nothing to alloy. If it holds something a smith made, it is going back to its material.
		for (ItemStack stack : inputs) {
			ForgedParts parts = stack.get(ModComponents.PARTS);
			if (parts != null && !parts.materials().isEmpty()) {
				return Component.translatable("gui.forja.crisol.funde", parts.materials().getFirst().displayName());
			}
		}
		// It may simply be too cold for what is in it, which is worth saying out loud.
		Alloys.Recipe hotter = Alloys.match(inputs, Alloys.Heat.FORJA_BLANCA);
		if (hotter != null) {
			return Component.translatable("gui.forja.crisol.frio", hotter.displayName(), hotter.heat().displayName());
		}
		return Component.translatable("gui.forja.crisol.nada");
	}

	/**
	 * The colour of what is in the pot: what it is turning into, when it is turning into something.
	 *
	 * <p>Iron and coal are on their way to being steel, so the pot is the colour of steel; a lone part
	 * being melted down is the colour of what it was made of; anything else is the first thing in it that
	 * is a metal at all.
	 */
	private int meltColour() {
		List<ItemStack> inputs = List.of(this.menu.first(), this.menu.second());
		Alloys.Heat heat = Alloys.Heat.values()[Math.min(this.menu.heat(), Alloys.Heat.values().length - 1)];
		Alloys.Recipe recipe = Alloys.match(inputs, heat);
		if (recipe != null) {
			ForgeMaterial made = ForgeMaterial.fromInput(recipe.result());
			if (made != null) {
				return molten(made.color);
			}
		}
		for (ItemStack stack : inputs) {
			if (stack.isEmpty()) {
				continue;
			}
			ForgedParts parts = stack.get(ModComponents.PARTS);
			ForgeMaterial material = parts != null && !parts.materials().isEmpty() ? parts.materials().getFirst() : ForgeMaterial.fromInput(stack);
			if (material != null) {
				return molten(material.color);
			}
		}
		return 0xFFCF7A2A;
	}

	/** The mod's one molten colour, opaque: the same the tanks and the channels draw this metal in. */
	private static int molten(int colour) {
		return 0xFF000000 | ForgeMaterial.molten(colour);
	}

	private static int shade(int colour, float factor) {
		int r = Math.min(255, Math.round(((colour >> 16) & 0xFF) * factor));
		int green = Math.min(255, Math.round(((colour >> 8) & 0xFF) * factor));
		int b = Math.min(255, Math.round((colour & 0xFF) * factor));
		return 0xFF000000 | r << 16 | green << 8 | b;
	}

	/** The small print of these panels. */
	private static final float SMALL = 0.75F;

	/**
	 * Small text that knows how wide its strip is: it wraps instead of running off the panel, and stops
	 * after {@code most} lines. "Refractory steel needs white forge heat" is forty characters, and the old
	 * single line carried it straight out through the frame.
	 */
	static void lines(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, Component text, int x, int y, int width, int most, int colour) {
		List<net.minecraft.util.FormattedCharSequence> parts = font.split(text, Math.round(width / SMALL));
		for (int i = 0; i < Math.min(most, parts.size()); i++) {
			g.pose().pushMatrix();
			g.pose().translate(x, y + i * 8);
			g.pose().scale(SMALL, SMALL);
			g.text(font, parts.get(i), 0, 0, colour, false);
			g.pose().popMatrix();
		}
	}

	static void centred(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, Component text, int centreX, int y, int colour) {
		centred(g, font, text, centreX, y, Integer.MAX_VALUE / 2, colour);
	}

	/** Centred small text that wraps at {@code width} instead of leaving the panel. */
	static void centred(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, Component text, int centreX, int y, int width, int colour) {
		List<net.minecraft.util.FormattedCharSequence> parts = font.split(text, Math.round(width / SMALL));
		for (int i = 0; i < parts.size(); i++) {
			g.pose().pushMatrix();
			g.pose().translate(centreX - font.width(parts.get(i)) * SMALL / 2.0F, y + i * 8);
			g.pose().scale(SMALL, SMALL);
			g.text(font, parts.get(i), 0, 0, colour, false);
			g.pose().popMatrix();
		}
	}
}
