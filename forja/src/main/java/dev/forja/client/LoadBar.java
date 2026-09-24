package dev.forja.client;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.Potential;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The load of a piece, drawn: a cell for every point a perfect piece could hold, so that every bar in
 * the game is the same length and a poor piece is visibly a short one.
 *
 * <p>The cells the piece's potential has opened are lighter; each upgrade fills as many as it weighs,
 * in its own colour and run together so a heavy one reads as one block; the rest of the open ones are
 * the room that is left; and the closed ones past them are what a better piece would have had. One
 * picture for the three places that show it — the forge table, the extraction wheel and the tooltip —
 * because a player who has learned to read it once should not have to learn it again.
 */
public final class LoadBar {
	public static final int HEIGHT = 5;
	private static final int FRAME = 0xFF101014;
	private static final int OPEN = 0xFF5A5A68;
	private static final int CLOSED = 0xFF26262E;
	private static final int OVER = 0xFFFF5A4A;

	private LoadBar() {
	}

	public static int width(int cell) {
		return Potential.MOST_CAPACITY * cell + 1;
	}

	/**
	 * @param cell      how many pixels a point is wide, its separator included
	 * @param incoming  an upgrade on its way onto the piece, drawn breathing after the rest — in red if
	 *                  it does not fit; null for none
	 * @param marked    an upgrade already on it to pick out (the wheel's chosen gem); null for none
	 */
	public static void draw(GuiGraphicsExtractor g, int x, int y, int cell, ItemStack gear, @Nullable Upgrade incoming, @Nullable Upgrade marked) {
		int capacity = Potential.capacity(gear);
		g.fill(x, y, x + width(cell), y + HEIGHT, FRAME);
		for (int i = 0; i < Potential.MOST_CAPACITY; i++) {
			g.fill(x + i * cell + 1, y + 1, x + (i + 1) * cell, y + HEIGHT - 1, i < capacity ? OPEN : CLOSED);
		}
		float breath = (float) ((Math.sin(System.currentTimeMillis() / 220.0) + 1.0) * 0.5);
		int at = 0;
		Upgrades upgrades = gear.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		for (Upgrade upgrade : upgrades.percents().keySet()) {
			int weight = Potential.weight(upgrade);
			int colour = upgrade == marked ? mix(upgrade.color, 0xFFFFFF, 0.25F + 0.45F * breath) : upgrade.color;
			at = run(g, x, y, cell, at, weight, colour, capacity);
		}
		if (incoming != null && upgrades.percent(incoming) <= 0) {
			boolean fits = Potential.fits(gear, incoming);
			int colour = mix(fits ? incoming.color : 0xE03A2A, 0x101014, 0.55F * (1.0F - breath));
			run(g, x, y, cell, at, Potential.weight(incoming), colour, fits ? capacity : Integer.MAX_VALUE);
		}
	}

	/** One upgrade's cells, run together; returns where the next one starts. */
	private static int run(GuiGraphicsExtractor g, int x, int y, int cell, int from, int weight, int colour, int capacity) {
		for (int i = from; i < from + weight && i < Potential.MOST_CAPACITY; i++) {
			int left = x + i * cell + (i == from ? 1 : 0);
			g.fill(left, y + 1, x + (i + 1) * cell, y + HEIGHT - 1, 0xFF000000 | colour);
			g.fill(left, y + 1, x + (i + 1) * cell, y + 2, 0xFF000000 | mix(colour, 0xFFFFFF, 0.45F));
			if (i >= capacity) {
				// More than the piece holds: something from before the load existed. It stays, and says so.
				g.fill(left, y + HEIGHT - 2, x + (i + 1) * cell, y + HEIGHT - 1, OVER);
			}
		}
		return from + weight;
	}

	/** What the bar says, in words, for whoever hovers it. */
	public static List<Component> legend(ItemStack gear) {
		List<Component> lines = new ArrayList<>();
		int potential = Potential.of(gear);
		int capacity = Potential.capacity(potential);
		int load = Potential.load(gear);
		lines.add(Component.translatable("gui.forja.carga.titulo", load, capacity).withColor(load > capacity ? 0xFF6B5A : 0xFFD37F));
		gear.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percents().forEach((upgrade, percent) -> {
			int weight = Potential.weight(upgrade);
			lines.add(Component.translatable(weight == 0 ? "gui.forja.carga.linea.libre" : "gui.forja.carga.linea", upgrade.displayName(), weight)
				.withColor(upgrade.color));
		});
		if (capacity < Potential.MOST_CAPACITY) {
			lines.add(Component.translatable("gui.forja.carga.mas", potential, Potential.MOST_CAPACITY).withColor(0xAAAAAA));
		}
		return lines;
	}

	private static int mix(int from, int to, float share) {
		int r = Math.round(((from >> 16) & 0xFF) * (1 - share) + ((to >> 16) & 0xFF) * share);
		int green = Math.round(((from >> 8) & 0xFF) * (1 - share) + ((to >> 8) & 0xFF) * share);
		int b = Math.round((from & 0xFF) * (1 - share) + (to & 0xFF) * share);
		return r << 16 | green << 8 | b;
	}
}
