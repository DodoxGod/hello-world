package dev.forja.magic;

import dev.forja.forge.ForgeType;
import net.minecraft.world.entity.player.Player;

/**
 * Where the healing lantern (and anything else of the class branch) pays for its magic. The mana system is
 * being built in another branch; until it is merged there is no mana, so {@link #spend} always says yes and
 * the only limit on a spell is its cooldown.
 *
 * <p>TODO(mana merge): make {@link #spend} take the mana from the player's bar and say no when it is short,
 * and have the staff and the tome's costs go through {@link dev.forja.clase.ClassEffects#spellCostMultiplier}
 * the way {@link #cost} already does for the lantern.
 */
public final class ManaHooks {
	/** Lantern: what a tap costs, and what a full charge adds on top. */
	public static final float LANTERN_TAP_COST = 6.0F;
	public static final float LANTERN_CHARGE_COST = 8.0F;

	private ManaHooks() {
	}

	/** Takes {@code amount} mana from the player if there is that much. Stub: always true. */
	public static boolean spend(Player player, float amount) {
		return true;
	}

	/** What a spell of this weapon costs this player, charge and class included. */
	public static float cost(Player player, ForgeType type, float charge) {
		float base = type == ForgeType.FAROL ? LANTERN_TAP_COST + LANTERN_CHARGE_COST * Math.max(0.0F, Math.min(1.0F, charge)) : 0.0F;
		return base * dev.forja.clase.ClassEffects.spellCostMultiplier(player);
	}
}
