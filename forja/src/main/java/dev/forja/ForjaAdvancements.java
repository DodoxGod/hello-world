package dev.forja;

import java.util.Set;

import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Upgrades;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Forja's advancement tab. Most advancements use the impossible trigger and are granted from code
 * when the player does the thing at a table; see data/forja/advancement/forja.
 */
public final class ForjaAdvancements {
	private static final Set<ForgeType> ARSENAL = Set.of(ForgeType.ARCO, ForgeType.BALLESTA, ForgeType.ESCUDO, ForgeType.LANZA, ForgeType.MAZO, ForgeType.TRIDENTE);

	private ForjaAdvancements() {
	}

	public static void award(Player player, String id) {
		award(player, id, "done");
	}

	public static void award(Player player, String id, String criterion) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		AdvancementHolder holder = serverPlayer.level().getServer().getAdvancements().get(Forja.id("forja/" + id));
		if (holder != null) {
			serverPlayer.getAdvancements().award(holder, criterion);
		}
	}

	/** New gear left the star: first forge, special materials and the arsenal. */
	public static void forged(Player player, ItemStack gear) {
		ForgedParts parts = gear.get(ModComponents.PARTS);
		if (parts == null) {
			return;
		}
		award(player, "forja");
		if (parts.primary() == ForgeMaterial.NETHERITA) {
			award(player, "netherita");
		}
		for (ForgeMaterial.Trait trait : ForgeMaterial.Trait.values()) {
			if (trait != ForgeMaterial.Trait.NONE && parts.hasTrait(trait)) {
				award(player, "rasgo");
				break;
			}
		}
		if (ARSENAL.contains(parts.type())) {
			award(player, "arsenal", parts.type().id());
		}
	}

	/** A named legend came out of a chest. */
	public static void foundLegend(Player player) {
		award(player, "leyenda");
	}

	/** An upgrade was applied on the star. */
	public static void upgraded(Player player, ItemStack gear, int after) {
		award(player, "mejora");
		if (after >= 100) {
			award(player, "mejora_completa");
		}
		long maxed = gear.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percents().values().stream().filter(percent -> percent >= 100).count();
		if (maxed >= 5) {
			award(player, "maestro");
		}
	}
}
