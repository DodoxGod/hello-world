package dev.forja.item;

import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Colador: the gate the melt is poured through on its way into a mould.
 *
 * <p>A plain one is fired clay and will take bronze and nothing hotter. You do not craft a better one:
 * you <b>infuse</b> the one you have — set it in the casting box and let molten metal run over it, and
 * it comes back out made of that metal, standing whatever that metal stands.
 *
 * <p>And it is the thing that can go wrong. If the melt is harder than the strainer will take, the
 * strainer <b>breaks</b> in the pour, and what comes out of the mould is a rough casting: it still
 * works, but the finished piece is worth less than one poured clean. That is the whole of the risk the
 * foundry has, and it is deliberately the smith's own fault when it happens.
 */
public class StrainerItem extends Item {
	/** What a plain fired-clay strainer will take, on the same hardness scale as everything else. */
	public static final int CLAY_HOLDS = 320;

	public StrainerItem(Properties properties) {
		super(properties);
	}

	/** A strainer of this metal, or the plain clay one when given nothing. */
	public static ItemStack of(@Nullable ForgeMaterial material) {
		ItemStack strainer = new ItemStack(ModItems.COLADOR);
		if (material != null) {
			strainer.set(ModComponents.STRAINER, material);
			// The grate is drawn grey and tinted, so an infused one comes back out looking like the
			// metal that went through it rather than like clay forever.
			strainer.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
				new net.minecraft.world.item.component.CustomModelData(
					java.util.List.of(), java.util.List.of(), java.util.List.of(),
					java.util.List.of(material.color)));
		}
		return strainer;
	}

	public static @Nullable ForgeMaterial materialOf(ItemStack stack) {
		return stack.get(ModComponents.STRAINER);
	}

	/** The hardest metal this strainer will let through without going with it. */
	public static int holds(ItemStack stack) {
		ForgeMaterial material = materialOf(stack);
		return material == null ? CLAY_HOLDS : material.durability;
	}

	/** Whether this strainer survives pouring that metal. */
	public static boolean survives(ItemStack stack, ForgeMaterial melt) {
		return melt.durability <= holds(stack);
	}

	@Override
	public Component getName(ItemStack stack) {
		ForgeMaterial material = materialOf(stack);
		return material == null
			? Component.translatable("item.forja.colador")
			: Component.translatable("item.forja.colador.de", material.displayName());
	}
}
