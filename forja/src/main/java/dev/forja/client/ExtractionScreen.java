package dev.forja.client;

import java.util.ArrayList;
import java.util.List;

import dev.forja.Forja;
import dev.forja.menu.ExtractionMenu;
import dev.forja.upgrade.Upgrade;
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

/**
 * The extraction table's screen, which is its own kind of inventory: a wheel.
 *
 * <p>Andy asked for a new type of inventory for this table. Every other bench of the mod is a grid or a
 * list on wood and sandstone, because every other bench puts things together; this one takes a thing
 * off, so it looks like what it does. The piece sits in the hub of a brass wheel and every upgrade it
 * carries is a <b>gem set in the rim</b>, in the upgrade's own colour and bigger the more of it there
 * is. You do not read a list and pick a line: you look at the piece and pull a stone out of it.
 *
 * <p>Pick a gem and its card comes up on the right — what it is, how much, what it does — and the tray
 * under the card shows, as ghosts, exactly what taking it off costs. With an empty orb in the tray the
 * button is "Extraer"; without one it is a red "Borrar". When it is done the gem leaves its socket and
 * flies to the cradle, which is where the orb then is. A pact is a gem too, dark and crossed out: it is
 * on the piece, so it is on the wheel, and it will not come out.
 */
public class ExtractionScreen extends AbstractContainerScreen<ExtractionMenu> {
	private static final Identifier TEXTURE = Forja.id("textures/gui/mesa_de_extraccion.png");

	private static final int WIDTH = 236;
	private static final int HEIGHT = 204;
	private static final int ON_SLATE = 0xFFC9C9D6;
	private static final int BRASS = 0xFFF0D286;
	private static final int MUTED = 0xFF8C8C9C;

	/** The wheel: its hub, how far out the sockets are, and how many of them there are. */
	private static final int HUB_X = 56;
	private static final int HUB_Y = 64;
	private static final int SOCKET_RADIUS = 30;
	public static final int SOCKETS = 8;

	private static final int CARD_X = 110;
	private static final int CARD_Y = 20;
	private static final int CARD_W = 117;

	private static final int BUTTON_X = 108;
	private static final int BUTTON_Y = 92;
	private static final int BUTTON_W = 120;
	private static final int BUTTON_H = 15;

	private static final int LOAD_CELL = 4;

	/** How long a gem takes to cross from its socket to the cradle. */
	private static final int FLIGHT_MILLIS = 520;

	/** Which eight of the piece's upgrades are on the wheel, for the rare piece that carries more. */
	private int page;
	private long flightStart;
	private int flightFromX;
	private int flightFromY;
	private int flightColour;

	public ExtractionScreen(ExtractionMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.inventoryLabelX = ExtractionMenu.INVENTORY_X;
		this.inventoryLabelY = ExtractionMenu.INVENTORY_Y - 11;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		this.drawWheel(g, x, y, mouseX, mouseY);
		this.drawLoad(g, x, y);
		this.drawCard(g, x, y);
		this.drawPrice(g, x, y);
		this.drawButton(g, x, y, mouseX, mouseY);
		this.drawFlight(g, x, y);
	}

	/** Where socket {@code index} of the wheel is, relative to the panel. */
	private static int[] socket(int index) {
		double angle = Math.toRadians(-90 + 45 * index);
		return new int[] {(int) Math.round(HUB_X + Math.cos(angle) * SOCKET_RADIUS), (int) Math.round(HUB_Y + Math.sin(angle) * SOCKET_RADIUS)};
	}

	/** The upgrades on the wheel right now. */
	private List<Upgrade> shown() {
		List<Upgrade> all = this.menu.upgrades();
		int pages = Math.max(1, (all.size() + SOCKETS - 1) / SOCKETS);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		return all.subList(this.page * SOCKETS, Math.min(all.size(), (this.page + 1) * SOCKETS));
	}

	/** A gem's size says how much of the upgrade there is: three pixels across the radius for a trace, six for all of it. */
	private static int gemRadius(int percent) {
		return 3 + Math.round(3.0F * percent / 100.0F);
	}

	private void drawWheel(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		List<Upgrade> shown = this.shown();
		Upgrade selected = this.menu.selected();
		long now = System.currentTimeMillis();
		for (int i = 0; i < shown.size(); i++) {
			Upgrade upgrade = shown.get(i);
			int[] at = socket(i);
			int cx = x + at[0];
			int cy = y + at[1];
			boolean locked = !ExtractionMenu.removable(upgrade);
			boolean hovered = Math.hypot(mouseX - cx - 0.5, mouseY - cy - 0.5) <= 7.5;
			int radius = gemRadius(this.menu.percent(upgrade));
			if (upgrade == selected) {
				// The chosen one breathes, and a thread of its colour runs from it to the piece it is coming out of.
				float pulse = (float) ((Math.sin(now / 240.0) + 1.0) * 0.5);
				disc(g, cx, cy, radius + 3, (int) (70 + 90 * pulse) << 24 | upgrade.color);
				for (int step = 2; step < 9; step++) {
					float along = step / 10.0F + (now % 600L) / 6000.0F;
					int tx = Math.round(cx + (x + HUB_X - cx) * along);
					int ty = Math.round(cy + (y + HUB_Y - cy) * along);
					if (Math.hypot(tx - x - HUB_X, ty - y - HUB_Y) > 14 && Math.hypot(tx - cx, ty - cy) > radius + 2) {
						g.fill(tx, ty, tx + 1, ty + 1, 0xC0000000 | upgrade.color);
					}
				}
			} else if (hovered && !locked) {
				disc(g, cx, cy, radius + 2, 0x50FFFFFF);
			}
			gem(g, cx, cy, radius, locked ? scaled(upgrade.color, 0.35F) : upgrade.color);
			if (locked) {
				// Crossed out: it is on the piece, so it is on the wheel, and it is not coming off.
				for (int d = -2; d <= 2; d++) {
					g.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, 0xFF14141A);
					g.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, 0xFF14141A);
				}
			}
		}
		int all = this.menu.upgrades().size();
		if (all > SOCKETS) {
			int pages = (all + SOCKETS - 1) / SOCKETS;
			small(g, Component.literal((this.page + 1) + "/" + pages), x + HUB_X - 5, y + HUB_Y + 44, MUTED);
		}
	}

	/**
	 * The piece's load, in the header: what it carries against what it holds, with the chosen gem's share
	 * picked out — which is how much room taking it off would give back, and half the reason to be here.
	 */
	private void drawLoad(GuiGraphicsExtractor g, int x, int y) {
		ItemStack gear = this.menu.getSlot(ExtractionMenu.GEAR_SLOT).getItem();
		if (gear.isEmpty()) {
			return;
		}
		int barX = x + WIDTH - 9 - LoadBar.width(LOAD_CELL);
		LoadBar.draw(g, barX, y + 7, LOAD_CELL, gear, null, this.menu.selected());
		int load = dev.forja.forge.Potential.load(gear);
		int capacity = dev.forja.forge.Potential.capacity(gear);
		// Numbers alone: the table's own name takes the rest of the header, and the tooltip on the bar has the words.
		Component text = Component.translatable("gui.forja.carga.numeros", load, capacity);
		small(g, text, barX - 4 - Math.round(this.font.width(text) * 0.75F), y + 7, load >= capacity ? 0xFFFFB347 : ON_SLATE);
	}

	/** The card of the chosen gem; or, with none chosen, what the table is waiting for. */
	private void drawCard(GuiGraphicsExtractor g, int x, int y) {
		Upgrade upgrade = this.menu.selected();
		if (upgrade == null) {
			String blocked = this.menu.blocked();
			if (blocked != null) {
				wrapped(g, Component.translatable(blocked), x + CARD_X, y + CARD_Y + 14, CARD_W - 2, 3, MUTED);
			}
			return;
		}
		int percent = this.menu.percent(upgrade);
		int colour = 0xFF000000 | upgrade.color;
		g.text(this.font, upgrade.displayName(), x + CARD_X, y + CARD_Y, colour, false);
		String amount = percent + "%";
		g.text(this.font, amount, x + CARD_X + CARD_W - this.font.width(amount) - 1, y + CARD_Y, 0xFFE8E8F0, false);
		int weight = dev.forja.forge.Potential.weight(upgrade);
		if (weight > 0) {
			// What it weighs, beside how much of it there is: the room it would give back.
			Component heavy = Component.translatable("gui.forja.carga.peso", weight);
			small(g, heavy, x + CARD_X + CARD_W - this.font.width(amount) - 5 - Math.round(this.font.width(heavy) * 0.75F), y + CARD_Y + 2, MUTED);
		}
		// How much of it there is, as a bar in its own colour with the quarters the price is counted in marked.
		int barY = y + CARD_Y + 11;
		g.fill(x + CARD_X, barY, x + CARD_X + CARD_W - 2, barY + 4, 0xFF0C0C10);
		g.fill(x + CARD_X, barY, x + CARD_X + (CARD_W - 2) * percent / 100, barY + 4, colour);
		g.fill(x + CARD_X, barY, x + CARD_X + (CARD_W - 2) * percent / 100, barY + 1, 0x60FFFFFF);
		for (int quarter = 1; quarter < 4; quarter++) {
			int at = x + CARD_X + (CARD_W - 2) * quarter / 4;
			g.fill(at, barY, at + 1, barY + 4, 0xA0000000);
		}
		Component effect = ExtractionMenu.removable(upgrade) ? upgrade.effect(percent) : Component.translatable("gui.forja.extraccion.pacto");
		wrapped(g, effect, x + CARD_X, y + CARD_Y + 19, CARD_W - 2, 3, ExtractionMenu.removable(upgrade) ? ON_SLATE : 0xFFFF7A7A);
	}

	/** What the chosen upgrade costs to take off, as ghosts in whichever payment slots are still empty. */
	private void drawPrice(GuiGraphicsExtractor g, int x, int y) {
		Upgrade upgrade = this.menu.selected();
		if (upgrade == null || !ExtractionMenu.removable(upgrade)) {
			return;
		}
		List<ItemStack> price = ExtractionMenu.price(upgrade, this.menu.percent(upgrade));
		for (int i = 0; i < price.size() && i < ExtractionMenu.PAYMENT_COUNT; i++) {
			if (!this.menu.getSlot(ExtractionMenu.PAYMENT_FIRST + i).hasItem()) {
				int sx = x + ExtractionMenu.PAYMENT_X + i * 20;
				int sy = y + ExtractionMenu.PAYMENT_Y;
				g.item(price.get(i), sx, sy);
				g.itemDecorations(this.font, price.get(i), sx, sy);
			}
		}
		g.nextStratum();
		for (int i = 0; i < price.size() && i < ExtractionMenu.PAYMENT_COUNT; i++) {
			if (!this.menu.getSlot(ExtractionMenu.PAYMENT_FIRST + i).hasItem()) {
				int sx = x + ExtractionMenu.PAYMENT_X + i * 20;
				int sy = y + ExtractionMenu.PAYMENT_Y;
				g.fill(sx, sy, sx + 16, sy + 16, 0xA022222A);
			}
		}
	}

	private void drawButton(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		boolean ready = this.menu.blocked() == null;
		boolean keeps = this.menu.hasOrb();
		boolean hovered = ready && inside(mouseX, mouseY, x + BUTTON_X, y + BUTTON_Y, BUTTON_W, BUTTON_H);
		int fill = !ready ? 0xFF3A3A46 : keeps ? (hovered ? 0xFFD2A24A : 0xFFB0822E) : (hovered ? 0xFFC04A3A : 0xFF9A3428);
		int light = !ready ? 0xFF5A5A6C : keeps ? 0xFFF4DC9A : 0xFFE8907E;
		int dark = !ready ? 0xFF1C1C24 : keeps ? 0xFF5E4210 : 0xFF4A1810;
		g.fill(x + BUTTON_X, y + BUTTON_Y, x + BUTTON_X + BUTTON_W, y + BUTTON_Y + BUTTON_H, fill);
		g.fill(x + BUTTON_X, y + BUTTON_Y, x + BUTTON_X + BUTTON_W - 1, y + BUTTON_Y + 1, light);
		g.fill(x + BUTTON_X, y + BUTTON_Y, x + BUTTON_X + 1, y + BUTTON_Y + BUTTON_H - 1, light);
		g.fill(x + BUTTON_X + 1, y + BUTTON_Y + BUTTON_H - 1, x + BUTTON_X + BUTTON_W, y + BUTTON_Y + BUTTON_H, dark);
		g.fill(x + BUTTON_X + BUTTON_W - 1, y + BUTTON_Y + 1, x + BUTTON_X + BUTTON_W, y + BUTTON_Y + BUTTON_H, dark);
		g.centeredText(this.font, Component.translatable(keeps ? "gui.forja.extraccion.extraer" : "gui.forja.extraccion.borrar"),
			x + BUTTON_X + BUTTON_W / 2, y + BUTTON_Y + 4, ready ? 0xFFFFFFFF : 0xFF8C8C9C);
	}

	/** The gem on its way from the socket it came out of to the cradle, along an arc, with a tail. */
	private void drawFlight(GuiGraphicsExtractor g, int x, int y) {
		if (this.flightStart == 0L) {
			return;
		}
		float age = (System.currentTimeMillis() - this.flightStart) / (float) FLIGHT_MILLIS;
		if (age >= 1.0F) {
			this.flightStart = 0L;
			return;
		}
		g.nextStratum();
		int toX = ExtractionMenu.OUTPUT_X + 8;
		int toY = ExtractionMenu.OUTPUT_Y + 8;
		for (int link = 5; link >= 0; link--) {
			float t = Math.max(0.0F, age - link * 0.045F);
			float eased = t * t * (3.0F - 2.0F * t);
			int px = x + Math.round(this.flightFromX + (toX - this.flightFromX) * eased);
			int py = y + Math.round(this.flightFromY + (toY - this.flightFromY) * eased - 26.0F * (float) Math.sin(Math.PI * eased));
			if (link == 0) {
				gem(g, px, py, 4, this.flightColour);
			} else {
				disc(g, px, py, Math.max(1, 3 - link / 2), (200 - link * 30) << 24 | this.flightColour);
			}
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, ON_SLATE, false);
		g.text(this.font, this.title, 8, 5, BRASS, true);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		int x = this.leftPos;
		int y = this.topPos;
		Upgrade selected = this.menu.selected();
		if (inside(mouseX, mouseY, x + BUTTON_X, y + BUTTON_Y, BUTTON_W, BUTTON_H)) {
			List<Component> lines = new ArrayList<>();
			String blocked = this.menu.blocked();
			if (blocked != null) {
				lines.add(Component.translatable(blocked).withColor(0xFFB347));
			} else if (selected != null) {
				int percent = this.menu.percent(selected);
				lines.add(Component.translatable(this.menu.hasOrb() ? "gui.forja.extraccion.se_guarda" : "gui.forja.extraccion.se_pierde",
					selected.displayName(), percent).withColor(this.menu.hasOrb() ? 0x7CFC7C : 0xFF6B6B));
				if (selected == Upgrade.RECOCIDO) {
					lines.add(Component.translatable("gui.forja.extraccion.potencial_baja", this.menu.potentialAfter(selected)).withColor(0xFFB347));
				}
				lines.add(Component.translatable("gui.forja.extraccion.resto_intacto").withColor(0xAAAAAA));
			}
			g.setTooltipForNextFrame(GuideText.wrap(this.font, lines), mouseX, mouseY);
			return;
		}
		ItemStack gear = this.menu.getSlot(ExtractionMenu.GEAR_SLOT).getItem();
		if (!gear.isEmpty() && inside(mouseX, mouseY, x + WIDTH - 9 - LoadBar.width(LOAD_CELL), y + 5, LoadBar.width(LOAD_CELL), LoadBar.HEIGHT + 4)) {
			g.setTooltipForNextFrame(GuideText.wrap(this.font, LoadBar.legend(gear)), mouseX, mouseY);
			return;
		}
		List<Upgrade> shown = this.shown();
		for (int i = 0; i < shown.size(); i++) {
			int[] at = socket(i);
			if (Math.hypot(mouseX - x - at[0] - 0.5, mouseY - y - at[1] - 0.5) > 7.5) {
				continue;
			}
			Upgrade hovered = shown.get(i);
			int percent = this.menu.percent(hovered);
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable("gui.forja.mejora.linea", hovered.displayName(), percent).withColor(hovered.color));
			lines.add(hovered.effect(percent).copy().withColor(0xAAAAAA));
			if (dev.forja.forge.Potential.weight(hovered) > 0) {
				lines.add(Component.translatable("gui.forja.carga.libera", dev.forja.forge.Potential.weight(hovered)).withColor(0x9FD3FF));
			}
			if (!ExtractionMenu.removable(hovered)) {
				lines.add(Component.translatable("gui.forja.extraccion.pacto").withColor(0xFF6B6B));
			} else {
				lines.add(Component.translatable("gui.forja.extraccion.cuesta").withColor(0xFFD37F));
				for (ItemStack stack : ExtractionMenu.price(hovered, percent)) {
					lines.add(Component.literal(" " + stack.getCount() + " × ").append(stack.getHoverName()).withColor(0xE0E0E0));
				}
			}
			g.setTooltipForNextFrame(GuideText.wrap(this.font, lines), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int x = this.leftPos;
		int y = this.topPos;
		List<Upgrade> shown = this.shown();
		for (int i = 0; i < shown.size(); i++) {
			int[] at = socket(i);
			if (Math.hypot(event.x() - x - at[0] - 0.5, event.y() - y - at[1] - 0.5) <= 7.5) {
				this.press(this.page * SOCKETS + i);
				return true;
			}
		}
		if (inside(event.x(), event.y(), x + BUTTON_X, y + BUTTON_Y, BUTTON_W, BUTTON_H) && this.menu.blocked() == null) {
			Upgrade leaving = this.menu.selected();
			int index = leaving == null ? -1 : shown.indexOf(leaving);
			boolean keeps = this.menu.hasOrb();
			if (this.press(ExtractionMenu.BUTTON_EXTRACT) && keeps && index >= 0) {
				int[] from = socket(index);
				this.flightFromX = from[0];
				this.flightFromY = from[1];
				this.flightColour = leaving.color;
				this.flightStart = System.currentTimeMillis();
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (Math.hypot(mouseX - this.leftPos - HUB_X, mouseY - this.topPos - HUB_Y) <= 42.0) {
			this.page = Math.max(0, this.page - (int) Math.signum(scrollY));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private boolean press(int id) {
		if (!this.menu.clickMenuButton(this.minecraft.player, id)) {
			return false;
		}
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		return true;
	}

	// ------------------------------------------------------------------ drawing in pixels

	/** A filled circle, a row at a time. */
	private static void disc(GuiGraphicsExtractor g, int cx, int cy, int radius, int colour) {
		for (int dy = -radius; dy <= radius; dy++) {
			int half = (int) Math.floor(Math.sqrt(radius * radius - dy * dy + 0.6));
			g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
		}
	}

	/** A cut stone: a dark setting, the colour lit from the upper left, and the glint where the light lands. */
	private static void gem(GuiGraphicsExtractor g, int cx, int cy, int radius, int colour) {
		disc(g, cx, cy, radius + 1, 0xFF0E0E14);
		for (int dy = -radius; dy <= radius; dy++) {
			int half = (int) Math.floor(Math.sqrt(radius * radius - dy * dy + 0.6));
			for (int dx = -half; dx <= half; dx++) {
				float lit = 1.0F - (dx + dy + 2.0F * radius) / (4.0F * radius);
				g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, 0xFF000000 | scaled(colour, 0.55F + 0.75F * lit));
			}
		}
		g.fill(cx - radius / 2, cy - radius / 2, cx - radius / 2 + 1, cy - radius / 2 + 1, 0xFFFFFFFF);
	}

	private static int scaled(int colour, float by) {
		int r = Math.min(255, Math.round(((colour >> 16) & 0xFF) * by));
		int green = Math.min(255, Math.round(((colour >> 8) & 0xFF) * by));
		int b = Math.min(255, Math.round((colour & 0xFF) * by));
		return r << 16 | green << 8 | b;
	}

	private void small(GuiGraphicsExtractor g, Component text, int x, int y, int colour) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75F, 0.75F);
		g.text(this.font, text, 0, 0, colour, false);
		g.pose().popMatrix();
	}

	/** Small text wrapped to a width, for the card. */
	private void wrapped(GuiGraphicsExtractor g, Component text, int x, int y, int width, int most, int colour) {
		List<FormattedCharSequence> parts = this.font.split(text, Math.round(width / 0.75F));
		for (int i = 0; i < Math.min(most, parts.size()); i++) {
			g.pose().pushMatrix();
			g.pose().translate(x, y + i * 8);
			g.pose().scale(0.75F, 0.75F);
			g.text(this.font, parts.get(i), 0, 0, colour, false);
			g.pose().popMatrix();
		}
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	// ------------------------------------------------------------------ for the client test

	/** A left click at a point, through the same door a real one comes in by. */
	public boolean clickAt(double[] point) {
		return this.mouseClicked(new MouseButtonEvent(point[0], point[1], new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
	}

	/** Where the gem of upgrade number {@code index} is, in GUI coordinates; turns the wheel to it first if it has to. */
	public double[] gemPoint(int index) {
		this.page = index / SOCKETS;
		int[] at = socket(index % SOCKETS);
		return new double[] {this.leftPos + at[0] + 0.5, this.topPos + at[1] + 0.5};
	}

	public double[] buttonPoint() {
		return new double[] {this.leftPos + BUTTON_X + BUTTON_W / 2.0, this.topPos + BUTTON_Y + BUTTON_H / 2.0};
	}

	/** Whether a gem is in the air right now. */
	public boolean flying() {
		return this.flightStart != 0L && System.currentTimeMillis() - this.flightStart < FLIGHT_MILLIS;
	}
}
