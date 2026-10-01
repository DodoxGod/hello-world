package dev.forja.client;

import dev.forja.Forja;
import dev.forja.block.entity.PartsCabinetBlockEntity;
import dev.forja.menu.CabinetMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The parts cabinet: three drawers in the same wood and stone as every other panel of the mod.
 *
 * <p>The one thing the cabinet does that a chest does not is refuse, so the screen says so where it
 * happens: pick up something that is not a smith's and the drawers go dull and the line under them
 * tells you why, before you have tried a slot and wondered whether the game is broken.
 */
public class CabinetScreen extends AbstractContainerScreen<CabinetMenu> {
	private static final Identifier TEXTURE = Forja.id("textures/gui/armario.png");

	private static final int TEXT = 0xFF3B2A1A;
	private static final int MUTED = 0xFF6B6455;
	private static final int BAD = 0xFFA02020;

	/** The three drawer fronts, as the texture draws them. */
	private static final int DRAWERS_X = 10;
	private static final int DRAWERS_Y = 19;
	private static final int DRAWERS_W = 186;
	private static final int HINT_Y = 91;

	public CabinetScreen(CabinetMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 206, 196);
		this.inventoryLabelX = CabinetMenu.INVENTORY_X;
		this.inventoryLabelY = CabinetMenu.INVENTORY_Y - 11;
	}

	/** Whether what is on the cursor would be turned away. */
	private boolean refused() {
		ItemStack carried = this.menu.getCarried();
		return !carried.isEmpty() && !PartsCabinetBlockEntity.accepts(carried);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		if (this.refused()) {
			g.fill(this.leftPos + DRAWERS_X, this.topPos + DRAWERS_Y, this.leftPos + DRAWERS_X + DRAWERS_W,
				this.topPos + DRAWERS_Y + CabinetMenu.ROWS * CabinetMenu.DRAWER_PITCH, 0x70302018);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
		g.text(this.font, this.title, 8, 5, CrucibleScreen.ON_WOOD, true);
		boolean refused = this.refused();
		CrucibleScreen.centred(g, this.font, Component.translatable(refused ? "gui.forja.armario.no" : "gui.forja.armario.pista"),
			this.imageWidth / 2, HINT_Y, 180, refused ? BAD : MUTED);
	}
}
