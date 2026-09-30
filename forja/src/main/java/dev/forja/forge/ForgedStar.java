package dev.forja.forge;

import dev.forja.registry.ModComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * La Estrella forjada (docs/HERRERO_DIMENSION.md, 4): what the Fallen Smith gives each smith who fought him.
 * Set on a finished piece at the greater forge, once, for good.
 *
 * <p>Andy: "ya acabaste el mod, deberías tener derecho a ser más poderoso". So it is a clear step above the
 * best there is, and not a multiplier on everything: the piece can hold more (potential up to 125, so 26
 * points of upgrades against 20), its material hits and digs harder (×1.12), it lasts half as long again,
 * and a starred piece of armour is a point of armour and half a point of toughness better (Andy approved
 * that it may pass the netherite P4 + 5 ceiling the unstarred sets are held to).
 */
public final class ForgedStar {
	/** The ceiling of a starred piece's potential, and what the star adds to it at once. */
	public static final int MOST = 125;
	public static final int POTENTIAL_BONUS = 25;
	public static final float DAMAGE = 1.12F;
	public static final float MINING = 1.12F;
	public static final float DURABILITY = 1.5F;
	public static final int ARMOR = 1;
	public static final float TOUGHNESS = 0.5F;

	private ForgedStar() {
	}

	public static boolean starred(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ESTRELLADA, false);
	}

	/** Whether the star can go on this: a finished forged piece (no arrows) that has none yet. */
	public static boolean takes(ItemStack gear) {
		return gear.has(ModComponents.PARTS) && gear.getMaxStackSize() == 1 && !starred(gear);
	}

	/** The piece with the star set in it, its numbers written again. */
	public static ItemStack star(ItemStack gear) {
		ItemStack starred = gear.copy();
		starred.set(ModComponents.ESTRELLADA, true);
		Assembler.rewrite(starred, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		return starred;
	}

	/** What the star does to a piece's numbers. */
	public static void apply(ForgeStats.Sheet sheet) {
		sheet.attackDamage *= DAMAGE;
		for (int i = 0; i < sheet.miningSpeeds.length; i++) {
			sheet.miningSpeeds[i] *= MINING;
		}
		sheet.durability = Math.round(sheet.durability * DURABILITY);
		if (sheet.armor > 0) {
			sheet.armor += ARMOR;
			sheet.toughness += TOUGHNESS;
		}
	}
}
