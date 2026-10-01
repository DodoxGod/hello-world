package dev.forja.item;

import java.util.List;

import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.UpgradeOrb;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.Nullable;

/** Holds one upgrade taken from salvaged gear, tinted with the upgrade's color. */
public class UpgradeOrbItem extends Item {
	public UpgradeOrbItem(Item.Properties properties) {
		super(properties);
	}

	public static ItemStack create(Upgrade upgrade, int percent) {
		ItemStack stack = new ItemStack(ModItems.ORBE_DE_MEJORA);
		stack.set(ModComponents.ORBE, new UpgradeOrb(upgrade, percent));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.forja.orbe_de_mejora.de", upgrade.displayName()));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(upgrade.color)));
		stack.set(DataComponents.RARITY, Rarity.UNCOMMON);
		return stack;
	}

	public static @Nullable UpgradeOrb orb(ItemStack stack) {
		return stack.getItem() instanceof UpgradeOrbItem ? stack.get(ModComponents.ORBE) : null;
	}
}
