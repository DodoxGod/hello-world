package dev.forja.item;

import java.util.Locale;

import dev.forja.registry.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Talismanes: a gem in a setting, carried in the bag rather than worn. Only one works at a time, the
 * first one your pack finds, so choosing which gem rides in your inventory is the whole mechanic.
 */
public enum Talisman {
	/** Emerald: doors open and prices drop, the way they do for the hero of a village. */
	ESMERALDA(0x3FD46A, Items.EMERALD),
	/** Diamond: one more point of armor, wherever you were going to take the hit. */
	DIAMANTE(0x5FF0E2, Items.DIAMOND),
	/** Quartz: the edge finds the gap more often. */
	CUARZO(0xEEE6DA, Items.QUARTZ),
	/** Amethyst: arrows have a way of missing you. */
	AMATISTA(0xB57BEA, Items.AMETHYST_SHARD),
	/** Echo: what you carry learns faster than it should. */
	ECO(0x2A8C94, Items.ECHO_SHARD),
	/** Prismarine: the sea stops arguing with you. */
	PRISMARINA(0x6CC7B2, Items.PRISMARINE_SHARD),
	/** Netherite: nothing shifts you as far as it means to. */
	NETERITA(0x6B5A5A, Items.NETHERITE_INGOT);

	/** What a diamond talisman is worth in armor. */
	public static final float ARMOR = 1.0F;

	/** The chance a quartz talisman turns a blow into a crit. */
	public static final float CRIT_CHANCE = 0.15F;

	/** The chance an amethyst talisman makes an arrow miss entirely. */
	public static final float DODGE_CHANCE = 0.20F;

	/** How much faster gear learns while an echo talisman rides along. */
	public static final float MASTERY_BONUS = 0.25F;

	/** What a netherite talisman is worth against a shove. */
	public static final float KNOCKBACK = 0.2F;

	public final int color;
	public final Item gem;

	Talisman(int color, Item gem) {
		this.color = color;
		this.gem = gem;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public Component displayName() {
		return Component.translatable("talisman.forja." + this.id());
	}

	public Component description() {
		return Component.translatable("talisman.forja." + this.id() + ".desc");
	}

	/** The talisman a stack is, if it is one. */
	public static @Nullable Talisman of(ItemStack stack) {
		String id = stack.get(ModComponents.TALISMAN);
		if (id == null) {
			return null;
		}
		for (Talisman talisman : values()) {
			if (talisman.id().equals(id)) {
				return talisman;
			}
		}
		return null;
	}

	/**
	 * The one talisman that counts: the first the pack finds, hotbar before the rest. Carrying five of
	 * them is carrying four dead stones.
	 */
	public static @Nullable Talisman active(Player player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			Talisman talisman = of(inventory.getItem(slot));
			if (talisman != null) {
				return talisman;
			}
		}
		return null;
	}

	public static boolean carried(Player player, Talisman talisman) {
		return active(player) == talisman;
	}

	/** A finished talisman of this gem. */
	public ItemStack create() {
		ItemStack stack = new ItemStack(dev.forja.registry.ModItems.TALISMAN);
		stack.set(ModComponents.TALISMAN, this.id());
		stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.translatable("item.forja.talisman.de", this.displayName()));
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(this.color)));
		stack.set(net.minecraft.core.component.DataComponents.RARITY, net.minecraft.world.item.Rarity.UNCOMMON);
		return stack;
	}
}
