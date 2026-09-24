package dev.forja.item;

import java.util.List;

import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.Nullable;

/**
 * A mould: the shape of one part, cut into refractory steel by pouring it over the real thing.
 *
 * <p>It is not a template. A template tells the forge table what to cut out of a bar; a mould tells the
 * casting box what shape to pour molten metal into, and the metal comes out of the tanks rather than out
 * of your pockets. Making one costs you the part it was taken from, which is the whole trade: one pick
 * head spent to never cut another by hand.
 */
public class CastingMouldItem extends Item {
	public CastingMouldItem(Properties properties) {
		super(properties);
	}

	/** A mould of this part. */
	public static ItemStack of(PartType part) {
		ItemStack mould = new ItemStack(dev.forja.registry.ModItems.MOLDE_DE_FUNDICION);
		mould.set(ModComponents.MOULD, part);
		// Which part is cut into it, so the item shows the shape rather than a blank plate. Same trick
		// the engraved templates use: the id goes in the string list and the model selects on it.
		mould.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
			new net.minecraft.world.item.component.CustomModelData(
				List.of(), List.of(), List.of(part.id()), List.of()));
		return mould;
	}

	public static @Nullable PartType partOf(ItemStack stack) {
		return stack.get(ModComponents.MOULD);
	}

	@Override
	public Component getName(ItemStack stack) {
		PartType part = partOf(stack);
		return part == null
			? Component.translatable("item.forja.molde_de_fundicion.vacio")
			: Component.translatable("item.forja.molde_de_fundicion", part.displayName());
	}

	/** Every mould there could be, for the creative tab and the guide. */
	public static List<ItemStack> all() {
		return java.util.Arrays.stream(PartType.values()).map(CastingMouldItem::of).toList();
	}
}
