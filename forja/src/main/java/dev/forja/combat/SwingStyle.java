package dev.forja.combat;

import dev.forja.forge.ForgeType;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * How a weapon is swung, for the animation only: a blade sweeps across, a head-heavy weapon comes down
 * from over the shoulder, a point is driven forward. Anything else keeps the vanilla swing.
 */
public enum SwingStyle {
	VANILLA,
	/** A flat cut from the outside in: swords, the greatsword, the scythe. */
	SLASH,
	/** Raised over the shoulder and brought down: axes, hammers, maces, the flail. */
	CHOP,
	/** Driven straight forward: spears, tridents, the dagger. */
	THRUST;

	public static SwingStyle of(ItemStack weapon) {
		if (weapon.isEmpty()) {
			return VANILLA;
		}
		ForgedParts parts = weapon.get(ModComponents.PARTS);
		if (parts != null) {
			ForgeType type = parts.type();
			return switch (type) {
				case ESPADA, ESPADON, GUADANA -> SLASH;
				case HACHA, PICAHACHA, MAZO, MARTILLO, MANGUAL -> CHOP;
				case LANZA, TRIDENTE, DAGA -> THRUST;
				default -> VANILLA;
			};
		}
		if (weapon.is(ItemTags.SWORDS)) {
			return SLASH;
		}
		if (weapon.is(ItemTags.AXES) || weapon.is(Items.MACE)) {
			return CHOP;
		}
		if (weapon.is(Items.TRIDENT)) {
			return THRUST;
		}
		return VANILLA;
	}
}
