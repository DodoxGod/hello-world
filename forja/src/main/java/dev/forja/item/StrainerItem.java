package dev.forja.item;

import dev.forja.block.CastingTableBlock;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Colador: the gate the melt is poured through on its way into a mould.
 *
 * <p>It is set down in the world, on top of a casting table (block/StrainerBlock), and the metal falling
 * out of the spout overhead goes through it before it reaches the mould or the frame. A plain one is fired
 * clay and will take bronze and nothing hotter. You do not craft a better one: you <b>infuse</b> the one
 * you have — set it in the casting box and let molten metal run over it, and it comes back out made of
 * that metal, standing whatever that metal stands.
 *
 * <p>And it is the thing that can go wrong. If the melt is harder than the strainer will take, the
 * strainer <b>breaks</b> in the pour, and what comes out of the mould is a rough casting: it still
 * works, but the finished piece is worth less than one poured clean. That is the whole of the risk the
 * foundry has, and it is deliberately the smith's own fault when it happens.
 */
public class StrainerItem extends BlockItem {
	/** What a plain fired-clay strainer will take, on the same hardness scale as everything else. */
	public static final int CLAY_HOLDS = 320;

	/** The colour a clay grate is drawn in, in the hand and on the table alike. */
	public static final int CLAY_COLOUR = 0xB6825C;

	public StrainerItem(Block block, Properties properties) {
		super(block, properties);
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

	/**
	 * It goes on top of things, and on top of a casting table however the table was clicked.
	 *
	 * <p>A click on the side of a table means "put it on this table", not "stand it on the floor beside
	 * it", so the placement is moved up onto the table's top. Anywhere else only a top face will do: a
	 * strainer stuck to a wall strains nothing.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		BlockPos clicked = context.getClickedPos();
		if (context.getLevel().getBlockState(clicked).getBlock() instanceof CastingTableBlock) {
			if (context.getClickedFace() == Direction.UP) {
				return super.useOn(context);
			}
			BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(clicked).add(0.0, 0.5, 0.0), Direction.UP, clicked, false);
			return this.place(new BlockPlaceContext(context.getPlayer(), context.getHand(), context.getItemInHand(), top));
		}
		return context.getClickedFace() == Direction.UP ? super.useOn(context) : InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context,
		net.minecraft.world.item.component.TooltipDisplay display,
		java.util.function.Consumer<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
		// What it will stand, and where it goes: nothing about the item says either.
		lines.accept(Component.translatable("tooltip.forja.colador", holds(stack))
			.withStyle(net.minecraft.ChatFormatting.GRAY));
	}

	@Override
	public Component getName(ItemStack stack) {
		ForgeMaterial material = materialOf(stack);
		return material == null
			? Component.translatable("item.forja.colador")
			: Component.translatable("item.forja.colador.de", material.displayName());
	}
}
