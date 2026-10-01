package dev.forja.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.forja.item.RepairKitItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The repair kits (Andy, 2026-10-01): one per metal, the guild-sealed bar of tools/lingotes.py's variant B.
 *
 * <p>A kit and a forged piece in a crafting grid, in any two slots, give the same piece back with
 * {@link #AMOUNT} more uses, never past its maximum, with everything else it carries untouched (parts,
 * upgrades, potential, Maestria, name, enchantments). It only takes a piece whose <b>main part</b> is of the
 * kit's metal, and the main part is {@link ForgedParts#primary()}: the first head (a tool's head, a weapon's
 * blade or tip, a bow's limbs), else the first plate (armour, barding, shield, wings), else the first slot.
 * It is the same part that names the piece ("Pico de acero") and decides what the anvil and the forge star
 * mend it with, so a kit always agrees with the name on the item.
 *
 * <p>The kits are an extra way to mend, for the road; the forge star, the anvil, the tempering bar, the
 * forge heart and the self-mending traits all still work as before.
 *
 * <p>Which metals get one is read from the material list, not written out: every material that has an alloy
 * ingot of its own ({@link ModItems#alloy}), plus the vanilla metals a head can be cast from. An alloy added
 * to Alloys.ALL and ForgeMaterial gets its kit, its recipe and its texture without touching this file
 * (tools/generate_assets.py writes the data from ALLOY_COLORS the same way).
 */
public final class RepairKits {
	/** How many uses one kit gives back. */
	public static final int AMOUNT = 300;

	/** The vanilla metals that can be a main part: copper, iron, gold and netherite. */
	public static final Set<ForgeMaterial> VANILLA_METALS = Collections.unmodifiableSet(
		EnumSet.of(ForgeMaterial.COBRE, ForgeMaterial.HIERRO, ForgeMaterial.ORO, ForgeMaterial.NETHERITA));

	private static final Map<ForgeMaterial, Item> KITS = new EnumMap<>(ForgeMaterial.class);

	private RepairKits() {
	}

	/** Whether this material gets a kit. Called while items register, after the alloy ingots. */
	public static boolean hasKit(ForgeMaterial material) {
		return material.canBeHead && (VANILLA_METALS.contains(material) || ModItems.alloy(material.getSerializedName()) != null);
	}

	/** The item id of one material's kit. */
	public static String id(ForgeMaterial material) {
		return "kit_de_reparacion_" + material.getSerializedName();
	}

	/** Records a registered kit (ModItems). */
	public static void put(ForgeMaterial material, Item kit) {
		KITS.put(material, kit);
	}

	/** Every material with a kit, in the material list's order. */
	public static List<ForgeMaterial> materials() {
		return new ArrayList<>(KITS.keySet());
	}

	public static @Nullable Item kit(ForgeMaterial material) {
		return KITS.get(material);
	}

	/** The metal a stack is a kit of, or null if it is not a kit. */
	public static @Nullable ForgeMaterial materialOf(ItemStack stack) {
		return stack.getItem() instanceof RepairKitItem kit ? kit.material : null;
	}

	/**
	 * What a kit does to a piece: the piece with {@link #AMOUNT} more uses, or empty when the kit does not
	 * fit it (not forged gear, nothing to mend, or a main part of another metal).
	 */
	public static ItemStack repair(ItemStack gear, ForgeMaterial kit) {
		ForgedParts parts = gear.get(ModComponents.PARTS);
		if (parts == null || gear.getCount() != 1 || !gear.isDamageableItem() || !gear.isDamaged() || parts.primary() != kit) {
			return ItemStack.EMPTY;
		}
		ItemStack repaired = gear.copy();
		repaired.setDamageValue(Math.max(0, gear.getDamageValue() - AMOUNT));
		return repaired;
	}
}
