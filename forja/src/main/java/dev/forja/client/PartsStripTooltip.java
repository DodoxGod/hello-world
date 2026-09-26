package dev.forja.client;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.Assembler;
import dev.forja.item.PartsStrip;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The row of parts under a forged item's name: each part as its own icon, in its own material, with
 * the material's name beside it in the material's colour.
 *
 * <p>The icon says which part it is, so the words only have to say what it is made of. Three parts
 * take one row where they took three lines; five wrap onto a second row and still take less room than
 * they did. Width is capped so a greatsword does not push its own tooltip off the screen.
 */
public class PartsStripTooltip implements ClientTooltipComponent {
	/** A cell is a 16 px icon, a gap, the name, and the space before the next one. */
	private static final int ICON = 16;
	private static final int GAP = 3;
	private static final int BETWEEN = 8;
	private static final int ROW = 18;
	/** A row is broken before it would be wider than this. */
	private static final int WIDEST = 210;

	private record Cell(ItemStack icon, Component name, int colour, int x, int row) {
	}

	/** Under the parts, the load: a bar (client/LoadBar) and what it comes to in numbers. */
	private static final int LOAD_CELL = 4;
	private static final int LOAD_ROW = 9;

	private final ForgedParts parts;
	private final ItemStack stack;
	private List<Cell> cells;
	private int width;
	private int rows;
	private Component load;

	public PartsStripTooltip(PartsStrip strip) {
		this.parts = strip.parts();
		this.stack = strip.stack();
	}

	/** Laid out once, the first time anything asks: the font is only known then. */
	private void layout(Font font) {
		if (this.cells != null) {
			return;
		}
		this.cells = new ArrayList<>();
		int x = 0;
		int row = 0;
		for (int slot = 0; slot < this.parts.type().slots.size(); slot++) {
			ForgeMaterial material = this.parts.material(slot);
			Component name = material.displayName();
			int cell = ICON + GAP + font.width(name);
			if (x > 0 && x + cell > WIDEST) {
				x = 0;
				row++;
			}
			this.cells.add(new Cell(Assembler.createPart(this.parts.type().slots.get(slot), material), name, 0xFF000000 | material.color, x, row));
			this.width = Math.max(this.width, x + cell);
			x += cell + BETWEEN;
		}
		this.rows = row + 1;
		int held = dev.forja.forge.Potential.load(this.stack);
		int capacity = dev.forja.forge.Potential.capacity(this.stack);
		this.load = Component.translatable("gui.forja.carga.corta", held, capacity).withColor(held >= capacity ? 0xFFB347 : 0xA8A8B8);
		this.width = Math.max(this.width, LoadBar.width(LOAD_CELL) + 4 + font.width(this.load));
	}

	@Override
	public int getHeight(Font font) {
		this.layout(font);
		return this.rows * ROW + LOAD_ROW + 2;
	}

	@Override
	public int getWidth(Font font) {
		this.layout(font);
		return this.width;
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
		this.layout(font);
		for (Cell cell : this.cells) {
			int cx = x + cell.x();
			int cy = y + cell.row() * ROW;
			// A faint plate behind each icon, so a dark part does not vanish into the tooltip's own dark.
			g.fill(cx, cy, cx + ICON, cy + ICON, 0x28FFFFFF);
			g.item(cell.icon(), cx, cy);
		}
		LoadBar.draw(g, x, y + this.rows * ROW + 2, LOAD_CELL, this.stack, null, null);
	}

	@Override
	public void extractText(GuiGraphicsExtractor g, Font font, int x, int y) {
		this.layout(font);
		for (Cell cell : this.cells) {
			g.text(font, cell.name(), x + cell.x() + ICON + GAP, y + cell.row() * ROW + 4, cell.colour(), true);
		}
		g.text(font, this.load, x + LoadBar.width(LOAD_CELL) + 4, y + this.rows * ROW, 0xFFFFFFFF, true);
	}

	/** For the client test: how many cells, and over how many rows. */
	public int[] shape(Font font) {
		this.layout(font);
		return new int[] {this.cells.size(), this.rows, this.width};
	}
}
