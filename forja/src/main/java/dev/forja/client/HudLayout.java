package dev.forja.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Where the mod's bars go over the hotbar, worked out in one place so they never land on each other or on
 * vanilla's (the visual pass of 2026-10-01).
 *
 * <p>Each bar used to pick its own height. The flight bar sat a fixed 49 pixels up, which is the armour row: with
 * a chestplate on it ran through the armour icons and the integrity shield, and with absorption or a second row
 * of hearts it ran through the hearts. The frenzy bar sat a fixed 56 up and did the same one row higher, and the
 * stamina bar stayed 64 up whatever vanilla was doing, under the hearts of a big mount and the air bubbles over
 * them. They are lanes now, counted from the top of vanilla's own stack:
 * <ol>
 * <li>the armour row, which is kept free even with no armour on, because the integrity shield sits in its
 *     middle the moment a blow lands;</li>
 * <li>mana over the hearts side and stamina over the hunger side, the pair, right above that;</li>
 * <li>the wings' bar, across the middle;</li>
 * <li>the frenzy bar above it.</li>
 * </ol>
 * The lanes do not close up when a bar is not showing: a combo starting must not make the mana bar jump.
 * Vanilla's stack is measured as vanilla draws it — rows of hearts and absorption, a mount's hearts in place of
 * the food, and the air bubbles over those — so the lanes climb with it and never sit on it.
 */
public final class HudLayout {
	/** A bar's well and caps reach two pixels above its top and two below its foot. */
	private static final int CAPS = 2;

	private HudLayout() {
	}

	/** The armour row's top, where vanilla puts it: ten pixels over the highest row of hearts. */
	public static int armourRow(int guiHeight, LocalPlayer player) {
		int base = guiHeight - 39;
		float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), player.getHealth());
		int absorption = Mth.ceil(player.getAbsorptionAmount());
		int rows = Mth.ceil((maxHealth + absorption) / 2.0F / 10.0F);
		int rowHeight = Math.max(10 - (rows - 2), 3);
		return base - (rows - 1) * rowHeight - 10;
	}

	/** The top of the hunger side: the food row, or a mount's hearts in its place, and the air bubbles over them. */
	public static int rightTop(int guiHeight, LocalPlayer player) {
		int top = guiHeight - 39;
		if (player.getVehicle() instanceof LivingEntity mount) {
			int hearts = Math.min(30, (int) (mount.getMaxHealth() + 0.5F) / 2);
			top -= (Math.max(1, Mth.ceil(hearts / 10.0F)) - 1) * 10;
		}
		int maxAir = player.getMaxAirSupply();
		int air = Math.min(player.getAirSupply(), maxAir);
		if (player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) || air < maxAir) {
			top -= 10;
		}
		return top;
	}

	/** The top of everything vanilla draws over the hotbar, with the armour row always counted in. */
	public static int stackTop(int guiHeight, LocalPlayer player) {
		return Math.min(armourRow(guiHeight, player), rightTop(guiHeight, player));
	}

	/** Lane 2: the mana and stamina bars' top, for a bar {@code height} pixels tall. */
	public static int sideY(int guiHeight, LocalPlayer player, int height) {
		return stackTop(guiHeight, player) - 1 - CAPS - height;
	}

	/** Lane 3: the wings' bar, over the pair. */
	public static int flightY(int guiHeight, LocalPlayer player, int height) {
		return sideY(guiHeight, player, 3) - CAPS - 1 - CAPS - height;
	}

	/** Lane 4: the frenzy bar, over the wings' bar's lane (kept whether or not wings are worn). */
	public static int frenzyY(int guiHeight, LocalPlayer player, int height) {
		return flightY(guiHeight, player, 5) - CAPS - 1 - CAPS - height;
	}
}
