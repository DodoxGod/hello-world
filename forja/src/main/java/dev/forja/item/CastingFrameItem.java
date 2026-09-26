package dev.forja.item;

import java.util.List;

import dev.forja.forge.ForgeType;
import dev.forja.registry.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Un marco: the shape of a whole finished tool, cut into refractory steel by pouring it over the real
 * thing.
 *
 * <p>A {@link CastingMouldItem mould} is one part — a pick head, a handle — and the casting box fills
 * it. A frame is the other size of the same idea: every hollow of a finished pickaxe at once, head and
 * handle and binding, so that what comes out of it is not a part but a tool you can swing. Nothing but
 * a casting table will fill one, and filling it takes exactly the metal the tool is worth.
 *
 * <p>It costs the tool it was taken from, and it costs more steel than a mould does, because a frame
 * ends the line: from then on that tool is something you pour rather than something you assemble.
 */
public class CastingFrameItem extends Item {
	public CastingFrameItem(Properties properties) {
		super(properties);
	}

	/** A frame of this tool. */
	public static ItemStack of(ForgeType type) {
		ItemStack frame = new ItemStack(dev.forja.registry.ModItems.MARCO);
		frame.set(ModComponents.MARCO, type);
		// Which tool it casts, so a drawer of frames is readable at a glance instead of being a row of
		// identical plates.
		frame.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
			new net.minecraft.world.item.component.CustomModelData(
				List.of(), List.of(), List.of(type.id()), List.of()));
		return frame;
	}

	public static @Nullable ForgeType typeOf(ItemStack stack) {
		return stack.get(ModComponents.MARCO);
	}

	/**
	 * What one casting costs: the sum of every part the tool is made of.
	 *
	 * <p>Exactly that much metal falls into the frame and not a drop more, which is the difference
	 * between casting a tool and assembling one — the table cannot give you a cheaper pickaxe by being
	 * clever, it can only give you a better one by being hot.
	 */
	public static int cost(ForgeType type) {
		int total = 0;
		for (dev.forja.part.PartType part : type.slots) {
			total += part.cost;
		}
		return total;
	}

	/** Whether every part of this tool can be made of that one metal. */
	public static boolean castable(ForgeType type, dev.forja.material.ForgeMaterial material) {
		for (dev.forja.part.PartType part : type.slots) {
			if (!part.accepts(material)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context,
		net.minecraft.world.item.component.TooltipDisplay display,
		java.util.function.Consumer<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
		ForgeType type = typeOf(stack);
		if (type != null) {
			// The one number that matters: how much of a tank one pour off this frame costs.
			lines.accept(Component.translatable("tooltip.forja.marco", cost(type))
				.withStyle(net.minecraft.ChatFormatting.GRAY));
		}
	}

	@Override
	public Component getName(ItemStack stack) {
		ForgeType type = typeOf(stack);
		return type == null
			? Component.translatable("item.forja.marco.vacio")
			: Component.translatable("item.forja.marco", type.displayName());
	}

	/** Every frame there could be, for the creative tab and the guide. */
	public static List<ItemStack> all() {
		return java.util.Arrays.stream(ForgeType.values()).map(CastingFrameItem::of).toList();
	}
}
