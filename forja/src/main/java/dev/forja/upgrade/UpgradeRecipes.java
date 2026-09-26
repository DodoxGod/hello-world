package dev.forja.upgrade;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Potential;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * Matches what is in the forge table's upgrade slots against the upgrades a piece accepts.
 *
 * <p>Either the slots hold exactly one set of a combo (e.g. piston + slime ball + ender pearl), or
 * every slot holds a single-item option of the same upgrade (e.g. redstone dust and redstone
 * blocks together). As many items as are needed to reach the ceiling are used, never more.
 *
 * <p>The ceiling is no longer always 100: see {@link Potential}. Whoever asks says how high each
 * upgrade may go on this piece at this table, and the ingredients are worth less the further along the
 * upgrade already is. A piece of master flux may lie among the ingredients; it is not one of them, it
 * is what lets an upgrade past ninety, and one is used up the time an upgrade crosses that line.
 */
public final class UpgradeRecipes {
	private UpgradeRecipes() {
	}

	/**
	 * @param result   the upgraded item, or empty when the upgrade can go no higher or conflicts
	 * @param consumed how many items to take from each ingredient slot, the flux's among them
	 * @param conflict an upgrade already on the item that this one cannot share it with
	 * @param ceiling  how high this upgrade could be taken here, and {@code limit} what stops it there
	 */
	public record Application(ItemStack result, int[] consumed, Upgrade upgrade, int before, int after, @Nullable Upgrade conflict,
		int ceiling, Potential.Limit limit) {
		/** Whether the upgrade stopped short of 100 because something said so, rather than because it is done. */
		public boolean held() {
			return this.limit != Potential.Limit.NONE && this.after >= this.ceiling && this.ceiling < Potential.MOST;
		}
	}

	public static @Nullable Application apply(ItemStack target, List<ItemStack> ingredients, HolderLookup.Provider registries) {
		return apply(target, ingredients, registries, 0);
	}

	/** With no ceiling but 100 and no flux asked for: what loot, commands and the older tests mean. */
	public static @Nullable Application apply(ItemStack target, List<ItemStack> ingredients, HolderLookup.Provider registries, int bonus) {
		return apply(target, ingredients, registries, bonus, Potential.Limits.NONE);
	}

	/**
	 * The same, with the extra share a practised smith wrings out of every ingredient
	 * (see SmithLevel.upgradeBonus), under the ceilings {@code limits} gives.
	 */
	public static @Nullable Application apply(ItemStack target, List<ItemStack> ingredients, HolderLookup.Provider registries, int bonus,
		Potential.Limits limits) {
		ForgedParts parts = target.get(ModComponents.PARTS);
		if (parts == null) {
			return null;
		}
		List<Integer> filled = new ArrayList<>();
		int fluxSlot = -1;
		for (int i = 0; i < ingredients.size(); i++) {
			if (ingredients.get(i).isEmpty()) {
				continue;
			}
			if (Potential.isFlux(ingredients.get(i))) {
				// One pile of flux is plenty; a second is somebody's mistake and matches nothing.
				if (fluxSlot >= 0) {
					return null;
				}
				fluxSlot = i;
			} else {
				filled.add(i);
			}
		}
		if (filled.isEmpty()) {
			return null;
		}

		for (Upgrade upgrade : Upgrade.values()) {
			if (!upgrade.appliesTo(parts.type())) {
				continue;
			}
			Upgrade.Option combo = null;
			for (Upgrade.Option option : upgrade.options) {
				if (combo == null && !option.isSingle() && matchesCombo(option, ingredients, filled)) {
					combo = option;
				}
			}
			Upgrade.Option[] singles = combo == null ? singleOptionsFor(upgrade, ingredients, filled) : null;
			if (combo == null && singles == null) {
				continue;
			}
			// Without the flux first. Only if that is what stops it, and there is some, again with it: the
			// flux is spent when it is what made the difference and never otherwise.
			Application application = null;
			for (boolean flux : fluxSlot >= 0 ? new boolean[] {false, true} : new boolean[] {false}) {
				Potential.Ceiling ceiling = limits.ceiling(upgrade, flux);
				application = combo != null
					? applyCombo(target, parts.type(), upgrade, combo, ingredients, filled, registries, bonus, ceiling)
					: applySingles(target, parts.type(), upgrade, singles, ingredients, filled, registries, bonus, ceiling);
				if (flux && application.after() > Potential.WITHOUT_FLUX && application.before() <= Potential.WITHOUT_FLUX) {
					application.consumed()[fluxSlot] = 1;
				}
				if (ceiling.limit() != Potential.Limit.FLUX || application.after() < ceiling.percent()) {
					break;
				}
			}
			Upgrade conflict = conflictWith(target, upgrade);
			return conflict == null ? application
				: new Application(ItemStack.EMPTY, new int[ingredients.size()], upgrade, application.before(), application.before(), conflict,
					application.ceiling(), application.limit());
		}
		return null;
	}

	private static @Nullable Upgrade conflictWith(ItemStack target, Upgrade upgrade) {
		for (Upgrade existing : target.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percents().keySet()) {
			if (!upgrade.isCompatibleWith(existing)) {
				return existing;
			}
		}
		return null;
	}

	private static boolean matchesCombo(Upgrade.Option option, List<ItemStack> ingredients, List<Integer> filled) {
		if (option.requirements().size() != filled.size()) {
			return false;
		}
		boolean[] used = new boolean[option.requirements().size()];
		for (int slot : filled) {
			boolean found = false;
			for (int r = 0; r < used.length; r++) {
				if (!used[r] && option.requirements().get(r).test(ingredients.get(slot))) {
					used[r] = true;
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	/** The single-item option matched by each filled slot, or null if any slot matches none. */
	private static Upgrade.Option @Nullable [] singleOptionsFor(Upgrade upgrade, List<ItemStack> ingredients, List<Integer> filled) {
		Upgrade.Option[] result = new Upgrade.Option[ingredients.size()];
		for (int slot : filled) {
			for (Upgrade.Option option : upgrade.options) {
				if (option.isSingle() && option.requirements().getFirst().test(ingredients.get(slot))) {
					result[slot] = option;
					break;
				}
			}
			if (result[slot] == null) {
				return null;
			}
		}
		return result;
	}

	private static Application applyCombo(
		ItemStack target, ForgeType type, Upgrade upgrade, Upgrade.Option option, List<ItemStack> ingredients, List<Integer> filled,
		HolderLookup.Provider registries, int bonus, Potential.Ceiling ceiling
	) {
		int before = target.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(upgrade);
		int[] consumed = new int[ingredients.size()];
		if (before >= ceiling.percent()) {
			return new Application(ItemStack.EMPTY, consumed, upgrade, before, before, null, ceiling.percent(), ceiling.limit());
		}
		int sets = Integer.MAX_VALUE;
		for (int slot : filled) {
			sets = Math.min(sets, ingredients.get(slot).getCount());
		}
		// A set at a time, because a set is worth less the further along the upgrade is.
		int progress = before * 4;
		int used = 0;
		while (used < sets && progress / 4 < ceiling.percent()) {
			progress = Potential.spend(progress, option.percent() + bonus);
			used++;
		}
		for (int slot : filled) {
			consumed[slot] = used;
		}
		int after = Math.min(ceiling.percent(), progress / 4);
		return finished(target, type, upgrade, consumed, before, after, registries, ceiling);
	}

	private static Application applySingles(
		ItemStack target, ForgeType type, Upgrade upgrade, Upgrade.Option[] options, List<ItemStack> ingredients, List<Integer> filled,
		HolderLookup.Provider registries, int bonus, Potential.Ceiling ceiling
	) {
		int before = target.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(upgrade);
		int[] consumed = new int[ingredients.size()];
		if (before >= ceiling.percent()) {
			return new Application(ItemStack.EMPTY, consumed, upgrade, before, before, null, ceiling.percent(), ceiling.limit());
		}
		// Spend the big items (blocks) first, then top up with the small ones.
		List<Integer> order = new ArrayList<>(filled);
		order.sort((a, b) -> options[b].percent() - options[a].percent());
		int progress = before * 4;
		for (int slot : order) {
			int value = options[slot].percent() + bonus;
			while (consumed[slot] < ingredients.get(slot).getCount() && progress / 4 < ceiling.percent()) {
				progress = Potential.spend(progress, value);
				consumed[slot]++;
			}
		}
		int after = Math.min(ceiling.percent(), progress / 4);
		return finished(target, type, upgrade, consumed, before, after, registries, ceiling);
	}

	/**
	 * What came of it. An ingredient that bought less than a whole percent bought nothing: the items are
	 * left where they are rather than taken for no change anybody could see.
	 */
	private static Application finished(ItemStack target, ForgeType type, Upgrade upgrade, int[] consumed, int before, int after,
		HolderLookup.Provider registries, Potential.Ceiling ceiling) {
		if (after <= before) {
			return new Application(ItemStack.EMPTY, new int[consumed.length], upgrade, before, before, null, ceiling.percent(), ceiling.limit());
		}
		return new Application(upgraded(target, type, upgrade, after, registries), consumed, upgrade, before, after, null, ceiling.percent(), ceiling.limit());
	}

	/** A copy of the gear with one upgrade set to a percentage and every component rebuilt. */
	public static ItemStack upgraded(ItemStack target, ForgeType type, Upgrade upgrade, int percent, HolderLookup.Provider registries) {
		ItemStack result = target.copyWithCount(1);
		Upgrades upgrades = result.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).with(upgrade, percent);
		result.set(ModComponents.UPGRADES, upgrades);
		// Attribute upgrades live in the attribute modifiers, so rebuild them.
		Assembler.rewrite(result, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		HiddenEnchantments.write(result, registries);
		return result;
	}

	private static int ceilDiv(int value, int divisor) {
		return (value + divisor - 1) / divisor;
	}
}
