package dev.forja.client;

import dev.forja.Forja;
import dev.forja.item.CastingMouldItem;
import dev.forja.menu.CastingBoxMenu;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The casting box's screen.
 *
 * <p>It has one job the crucible's does not: telling you, before you waste a part, whether this box can
 * hold the metal you are about to ask it for. So the line under the slots is the limit it will take and
 * what it is doing about what is in it right now.
 */
public class CastingBoxScreen extends AbstractContainerScreen<CastingBoxMenu> {
	private static final Identifier TEXTURE = Forja.id("textures/gui/caja_de_moldeo.png");

	private static final int TEXT = 0xFF3B2A1A;
	private static final int MUTED = 0xFF6B6455;
	private static final int GOOD = 0xFF2E7D32;

	/** The channel the pour runs down, matching textures/gui/caja_de_moldeo.png. */
	private static final int ARROW_X = 95;
	private static final int ARROW_Y = 54;
	private static final int ARROW_W = 38;

	/** The sand inside the flask, which the part in the box is pressed into. */
	private static final int SAND_X = 60;
	private static final int SAND_Y = 28;
	private static final int SAND = 30;
	/** The colour of that sand, for the veil that lies over the print. */
	private static final int SAND_COLOUR = 0xCCBA8E;
	private static final int MOLTEN_COLOUR = 0xF08A34;

	public CastingBoxScreen(CastingBoxMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 206, 196);
		this.inventoryLabelX = CastingBoxMenu.INVENTORY_X;
		this.inventoryLabelY = CastingBoxMenu.INVENTORY_Y - 11;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

		float progress = this.menu.progress();
		if (progress > 0.0F) {
			int width = Math.round(ARROW_W * progress);
			g.fill(x + ARROW_X, y + ARROW_Y, x + ARROW_X + width, y + ARROW_Y + 5, 0xFFE0762A);
			g.fill(x + ARROW_X, y + ARROW_Y, x + ARROW_X + width, y + ARROW_Y + 1, 0xFFFFC46A);
			g.fill(x + ARROW_X + Math.max(0, width - 2), y + ARROW_Y, x + ARROW_X + width, y + ARROW_Y + 5, 0xFFFFE2A0);
		}
		this.drawPrint(g, x, y, progress);
	}

	/**
	 * The print in the sand: the part that is actually in the box, drawn big and seen through sand.
	 *
	 * <p>It was a sword painted into the texture, whatever was in the box — a pickaxe head went in and a
	 * sword showed in the sand. Now it is the part itself at nearly twice its size with a veil of sand
	 * colour over it, which is what a print is: the shape, in the colour of what it was pressed into.
	 * Taking a mould, the veil thins as the work goes and the print comes up out of the sand. Casting,
	 * the veil turns from sand to the colour of hot metal as the print fills.
	 */
	private void drawPrint(GuiGraphicsExtractor g, int x, int y, float progress) {
		ItemStack pattern = this.menu.pattern();
		PartType mould = CastingMouldItem.partOf(pattern);
		ItemStack shape = mould != null ? dev.forja.forge.Assembler.createPart(mould, dev.forja.material.ForgeMaterial.HIERRO)
			: pattern.getItem() instanceof dev.forja.item.PartItem ? pattern : ItemStack.EMPTY;
		if (shape.isEmpty()) {
			return;
		}
		float size = SAND / 16.0F;
		g.pose().pushMatrix();
		g.pose().translate(x + SAND_X, y + SAND_Y);
		g.pose().scale(size, size);
		g.item(shape, 0, 0);
		g.pose().popMatrix();
		g.nextStratum();
		int veil;
		if (mould != null) {
			// Sand going over to metal: the more of the pour is in, the hotter and the more solid it reads.
			int colour = blend(SAND_COLOUR, MOLTEN_COLOUR, progress);
			veil = (int) (150 + 60 * progress) << 24 | colour;
			if (progress > 0.0F) {
				float flicker = (float) ((Math.sin(System.currentTimeMillis() / 120.0) + 1.0) * 0.5);
				veil = (int) (150 + 60 * progress + 20 * flicker * progress) << 24 | colour;
			}
		} else {
			veil = (int) (215 - 110 * progress) << 24 | SAND_COLOUR;
		}
		g.fill(x + SAND_X, y + SAND_Y, x + SAND_X + SAND, y + SAND_Y + SAND, veil);
	}

	private static int blend(int from, int to, float share) {
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * share);
		int green = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * share);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * share);
		return r << 16 | green << 8 | b;
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
		g.text(this.font, this.title, 8, 5, CrucibleScreen.ON_WOOD, true);

		// What it is about to do runs the width of the panel under the slots, on two lines when it needs
		// them; what the box can stand goes under the slot the work comes out of.
		CrucibleScreen.lines(g, this.font, this.doing(), 22, 86, 176, 2, this.menu.progress() > 0.0F ? GOOD : MUTED);
		int holds = this.menu.holds();
		CrucibleScreen.centred(g, this.font, holds == Integer.MAX_VALUE
			? Component.translatable("gui.forja.caja.aguanta_todo")
			: Component.translatable("gui.forja.caja.aguanta", holds), 148, 70, 92, MUTED);
	}

	/** What the box is about to do with what is in it, said before it costs you the part. */
	private Component doing() {
		ItemStack pattern = this.menu.pattern();
		if (pattern.isEmpty()) {
			return Component.translatable("gui.forja.caja.pon_pieza");
		}
		PartType mould = CastingMouldItem.partOf(pattern);
		if (mould != null) {
			return Component.translatable("gui.forja.caja.lista", mould.displayName(), mould.cost);
		}
		if (pattern.getItem() instanceof dev.forja.item.PartItem) {
			return Component.translatable("gui.forja.caja.gastara");
		}
		return Component.translatable("gui.forja.caja.pon_pieza");
	}

}
