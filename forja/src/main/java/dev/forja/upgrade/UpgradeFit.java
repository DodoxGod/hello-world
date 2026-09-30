package dev.forja.upgrade;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.Potential;
import dev.forja.menu.Station;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Which upgrades a piece can take, and which of them still fit (Andy, 2026-09-30: a slot in the book where you put
 * a tool, weapon or armour piece and it lists what goes on it). Read-only: it asks the same rules the forge star
 * asks when it upgrades (UpgradeRecipes.apply and ForgeMenu) and changes nothing.
 *
 * <ul>
 *   <li><b>Compatible</b>: the upgrade applies to the piece's type (Upgrade.appliesTo, by ForgeType, which is also
 *   what decides the armour slot).</li>
 *   <li><b>Fits now</b>: it can still go up, at the greater table with flux to hand, which is as far as any table
 *   takes it: it is not in the same exclusive group as an upgrade already on (Upgrade.isCompatibleWith), a pact
 *   finds a place among the {@link Pacts#MOST} and is open to this player, and the piece has the load and the
 *   potential for it (Potential.ceiling).</li>
 *   <li>Otherwise it is compatible but does not fit, and {@link Reason} says why.</li>
 * </ul>
 */
public final class UpgradeFit {
	public enum Reason {
		/** It fits: it can go up from where it is. */
		NONE,
		/** Already as high as it goes (a hundred, or its all-or-nothing whole). */
		FULL,
		/** The piece has no load left for something this heavy. */
		LOAD,
		/** The piece's potential stops it where it is. */
		POTENTIAL,
		/** An upgrade of the same exclusive group is on the piece. */
		CONFLICT,
		/** A pact, and the piece already carries as many as a piece may. */
		PACTS,
		/** A pact this player has not opened with its offering yet. */
		SEALED
	}

	/**
	 * One upgrade on this piece: where it is, how high the greater table and the plain bench could take it, why it
	 * stops, what it clashes with, and the synergies it would wake with what is on the piece.
	 */
	public record Fit(Upgrade upgrade, int current, int greater, int bench, Reason reason, @Nullable Upgrade conflict, List<Synergy> pairs) {
		public boolean fits() {
			return this.reason == Reason.NONE;
		}
	}

	/** The piece's own state, for the head of the list: potential, load, pacts and synergies awake. */
	public record Summary(int potential, int load, int capacity, int pacts, int synergies) {
	}

	private UpgradeFit() {
	}

	/** Whether this is something upgrades go on at all: a forged piece. */
	public static boolean upgradable(ItemStack stack) {
		return stack.has(ModComponents.PARTS);
	}

	public static Summary summary(ItemStack gear) {
		Upgrades upgrades = gear.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		return new Summary(Potential.of(gear), Potential.load(gear), Potential.capacity(gear), Pacts.count(upgrades), Synergy.on(gear).size());
	}

	/**
	 * Every upgrade compatible with the piece, in the game's order, each with whether it fits. A player may be null
	 * (a pact is then taken as open); empty for anything that is not a forged piece.
	 */
	public static List<Fit> of(ItemStack gear, @Nullable Player player) {
		List<Fit> fits = new ArrayList<>();
		ForgedParts parts = gear.get(ModComponents.PARTS);
		if (parts == null) {
			return fits;
		}
		Upgrades upgrades = gear.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		for (Upgrade upgrade : Upgrade.values()) {
			if (!upgrade.appliesTo(parts.type())) {
				continue;
			}
			fits.add(fit(gear, upgrades, upgrade, player));
		}
		return fits;
	}

	private static Fit fit(ItemStack gear, Upgrades upgrades, Upgrade upgrade, @Nullable Player player) {
		int current = upgrades.percent(upgrade);
		int greater = Potential.ceiling(gear, upgrade, Station.FORJA_MAYOR, true).percent();
		int bench = Potential.ceiling(gear, upgrade, Station.FORJA, true).percent();
		Upgrade conflict = null;
		for (Upgrade existing : upgrades.percents().keySet()) {
			if (!upgrade.isCompatibleWith(existing)) {
				conflict = existing;
				break;
			}
		}
		List<Synergy> pairs = new ArrayList<>();
		for (Synergy synergy : Synergy.values()) {
			Upgrade other = synergy.first == upgrade ? synergy.second : synergy.second == upgrade ? synergy.first : null;
			if (other != null && upgrades.percent(other) > 0) {
				pairs.add(synergy);
			}
		}
		Reason reason;
		if (conflict != null) {
			reason = Reason.CONFLICT;
		} else if (!Pacts.fits(upgrades, upgrade)) {
			reason = Reason.PACTS;
		} else if (upgrade.isPact() && player != null && !Pacts.unlocked(player, upgrade)) {
			reason = Reason.SEALED;
		} else if (current >= Potential.MOST) {
			reason = Reason.FULL;
		} else if (!Potential.fits(gear, upgrade)) {
			reason = Reason.LOAD;
		} else if (greater <= current) {
			reason = Potential.allOrNothing(upgrade) ? Reason.FULL : Reason.POTENTIAL;
		} else {
			reason = Reason.NONE;
		}
		return new Fit(upgrade, current, greater, bench, reason, conflict, pairs);
	}
}
