package dev.forja.item;

import java.util.List;

import dev.forja.forge.Perk;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.Nullable;

/** The stamped seal that engraves one gift on a Maestria 10 item, coloured after the gift it carries. */
public class SealItem extends Item {
	public SealItem(Item.Properties properties) {
		super(properties);
	}

	public static ItemStack create(Perk perk) {
		ItemStack stack = new ItemStack(ModItems.SELLO);
		stack.set(ModComponents.SELLO, perk.id());
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.forja.sello.de", perk.displayName()));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(perk.color)));
		stack.set(DataComponents.RARITY, Rarity.UNCOMMON);
		return stack;
	}

	/** The gift a seal carries, or null for anything else. */
	public static @Nullable Perk perk(ItemStack stack) {
		if (!(stack.getItem() instanceof SealItem)) {
			return null;
		}
		String id = stack.get(ModComponents.SELLO);
		for (Perk perk : Perk.values()) {
			if (perk.id().equals(id)) {
				return perk;
			}
		}
		return null;
	}
}
