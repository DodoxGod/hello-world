package dev.forja.item;

import java.util.List;

import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.Nullable;

/**
 * A part template. Blank templates are engraved with a part shape at the parts table; an engraved
 * template decides which part the table cuts from the material and is never used up. The engraving
 * is permanent.
 */
public class TemplateItem extends Item {
	public TemplateItem(Item.Properties properties) {
		super(properties);
	}

	public static @Nullable PartType pattern(ItemStack stack) {
		return stack.getItem() instanceof TemplateItem ? stack.get(ModComponents.PATTERN) : null;
	}

	/** Engraves a shape on the template in place: name, model and pattern follow the part. */
	public static void engrave(ItemStack stack, PartType part) {
		stack.set(ModComponents.PATTERN, part);
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.forja.plantilla.de", part.displayName()));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(part.id()), List.of()));
	}
}
