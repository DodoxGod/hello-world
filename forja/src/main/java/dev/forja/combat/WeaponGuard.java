package dev.forja.combat;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.forja.forge.ForgeType;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;

/**
 * A guard with the weapon itself, for whoever has no shield: swords, the greatsword and the dagger block
 * on right click. It turns only part of a blow aside (a shield stops it all), but a raise caught in the
 * first few ticks is a parry like a shield's, and that stops everything.
 *
 * <p>With a shield in the other hand the shield wins: the weapon steps aside so the right click reaches it.
 */
public final class WeaponGuard {
	private static final Set<ForgeType> FORGED = Set.of(ForgeType.ESPADA, ForgeType.ESPADON, ForgeType.DAGA);

	private WeaponGuard() {
	}

	public static void register() {
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(WeaponGuard::guards, (builder, registries, item) -> {
			CombatConfig cfg = CombatConfig.get();
			if (!cfg.enabled || !cfg.weaponGuard) {
				return;
			}
			var bypass = registries.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeTags.BYPASSES_SHIELD);
			builder.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(
				0.0F,
				1.0F,
				List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, cfg.weaponGuardBlock)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 0.5F),
				Optional.of(bypass),
				Optional.of(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_ATTACK_KNOCKBACK)),
				Optional.of(SoundEvents.SHIELD_BREAK)
			));
		}));
	}

	/** The items that guard: vanilla swords and the forged blades. */
	private static boolean guards(Item item) {
		if (item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.COPPER_SWORD || item == Items.GOLDEN_SWORD
			|| item == Items.IRON_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD) {
			return true;
		}
		for (ForgeType type : FORGED) {
			if (item == ModItems.forged(type)) {
				return true;
			}
		}
		return false;
	}

	/** Whether this stack is a weapon raised to guard (not a shield). */
	public static boolean is(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.has(DataComponents.BLOCKS_ATTACKS) && guards(stack.getItem());
	}

	/**
	 * Whether a right click with this stack should go to the shield in the other hand instead: the
	 * weapon is in the main hand and a real shield is in the off hand.
	 */
	public static boolean yieldsToShield(Player player, InteractionHand hand, ItemStack stack) {
		if (hand != InteractionHand.MAIN_HAND || !is(stack)) {
			return false;
		}
		ItemStack off = player.getOffhandItem();
		return off.has(DataComponents.BLOCKS_ATTACKS) && !is(off);
	}
}
