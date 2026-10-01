package dev.forja.client;

import java.util.List;

import dev.forja.Forja;
import dev.forja.clase.TreeIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * What the two class screens, the toast and the HUD share: the one texture they are drawn from
 * ({@code textures/gui/clases.png}, made by tools/generate_assets.py) and where each piece of it is.
 */
final class ClassGui {
	static final Identifier TEXTURE = Forja.id("textures/gui/clases.png");
	static final int TEX = 512;
	static final int PANEL_W = 380;
	static final int PANEL_H = 210;
	/** The tree's panel: three sunken columns and the row for the skill node. */
	static final int TREE_V = 0;
	/** The choice's panel: the list on the left, the page on the right. */
	static final int CHOICE_V = 256;
	static final int CARD_U = 390;
	static final int CARD_W = 104;
	static final int CARD_H = 22;
	static final int BUTTON_U = 390;
	static final int BUTTON_V = 74;
	static final int BUTTON_W = 80;
	static final int BUTTON_H = 20;
	static final int HUD_U = 390;
	static final int HUD_V = 140;
	static final int HUD_SIZE = 22;
	static final int NODE_U = 0;
	static final int NODE_V = 472;
	static final int NODE = 26;

	static final int INK = 0xFFE8DCC0;
	static final int INK_SOFT = 0xFFB8A888;
	static final int INK_DIM = 0xFF7A6E5E;
	static final int GOLD = 0xFFF0C070;
	static final int RED = 0xFFE0533D;
	static final int GREEN = 0xFF7FD34E;

	enum NodeState {
		LOCKED,
		AVAILABLE,
		LEARNED
	}

	private ClassGui() {
	}

	static void panel(GuiGraphicsExtractor g, int x, int y, int v) {
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, v, PANEL_W, PANEL_H, TEX, TEX);
	}

	static void node(GuiGraphicsExtractor g, int x, int y, NodeState state, boolean skill) {
		int index = state.ordinal() + (skill ? 3 : 0);
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, NODE_U + index * NODE, NODE_V, NODE, NODE, TEX, TEX);
	}

	/** 0 normal, 1 pointed at, 2 chosen. */
	static void card(GuiGraphicsExtractor g, int x, int y, int look) {
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, CARD_U, look * 24, CARD_W, CARD_H, TEX, TEX);
	}

	/** 0 normal, 1 pointed at, 2 off. */
	static void button(GuiGraphicsExtractor g, int x, int y, int look) {
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, BUTTON_U, BUTTON_V + look * 22, BUTTON_W, BUTTON_H, TEX, TEX);
	}

	static void hudSlot(GuiGraphicsExtractor g, int x, int y, boolean ready) {
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, HUD_U + (ready ? 24 : 0), HUD_V, HUD_SIZE, HUD_SIZE, TEX, TEX);
	}

	/** Text at three quarters of its size, as the guide sets its pages. */
	static void small(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int colour) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75F, 0.75F);
		g.text(font, text, 0, 0, colour, false);
		g.pose().popMatrix();
	}

	static void small(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int colour) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75F, 0.75F);
		g.text(font, text, 0, 0, colour, false);
		g.pose().popMatrix();
	}

	/** Wraps and writes at three quarters, returning how far down it went. */
	static int smallWrapped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int colour) {
		List<FormattedCharSequence> lines = font.split(text, Math.round(width / 0.75F));
		for (FormattedCharSequence line : lines) {
			small(g, font, line, x, y, colour);
			y += 7;
		}
		return y;
	}

	static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	/**
	 * A tree node's icon (clase/TreeIcon), centred on a point and {@code size} pixels wide: an item, a sprite from
	 * textures/gui/arbol, or a class's emblem. A skill's upgrade carries a small gold "II" in its corner.
	 */
	static void treeIcon(GuiGraphicsExtractor g, Font font, String icon, float cx, float cy, float size, boolean upgrade) {
		ItemStack stack = TreeIcon.stack(icon);
		Identifier sprite = TreeIcon.sprite(icon);
		if (stack != null) {
			item(g, stack, Math.round(cx - size / 2.0F), Math.round(cy - size / 2.0F), size / 16.0F);
		} else if (sprite != null) {
			g.pose().pushMatrix();
			g.pose().translate(Math.round(cx - size / 2.0F), Math.round(cy - size / 2.0F));
			g.pose().scale(size / 16.0F, size / 16.0F);
			g.blit(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, 0.0F, 0.0F, 16, 16, 16, 16);
			g.pose().popMatrix();
		}
		if (upgrade && size >= 9.0F) {
			float k = Math.max(0.5F, size / 22.0F);
			g.pose().pushMatrix();
			g.pose().translate(cx + size * 0.5F - 9.0F * k, cy + size * 0.5F - 8.0F * k);
			g.pose().scale(k, k);
			g.text(font, "II", 1, 1, 0xFF201808, false);
			g.text(font, "II", 0, 0, GOLD, false);
			g.pose().popMatrix();
		}
	}

	static boolean over(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	/** A straight line of pixels, one wide, between two points on the same row or column. */
	static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int colour) {
		g.fill(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1) + 1, Math.max(y0, y1) + 1, colour);
	}
}
