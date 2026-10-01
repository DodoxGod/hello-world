package dev.forja.forge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.forja.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * El cincel: a smith works stone too. Right-click a block of a family it knows and it steps to the next
 * face of that family; crouch and it steps back. Nothing is consumed and nothing is gained, so this is
 * for building, not for making material.
 */
public final class Chisel {
	/** Each family, in the order the chisel walks it. */
	private static final List<List<Block>> FAMILIES = List.of(
		List.of(Blocks.STONE, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
			Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS),
		List.of(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE),
		List.of(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS,
			Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE),
		List.of(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.SMOOTH_SANDSTONE),
		List.of(Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE),
		List.of(Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BRICKS, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ),
		List.of(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
			Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE),
		List.of(Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE),
		List.of(Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR),
		List.of(Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS, Blocks.CHISELED_NETHER_BRICKS, Blocks.RED_NETHER_BRICKS),
		List.of(Blocks.END_STONE, Blocks.END_STONE_BRICKS),
		List.of(Blocks.TUFF, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF, Blocks.CHISELED_TUFF_BRICKS),
		List.of(Blocks.BASALT, Blocks.POLISHED_BASALT, Blocks.SMOOTH_BASALT)
	);

	/** block -> (family, index), built once. */
	private static final Map<Block, int[]> WHERE = new HashMap<>();

	static {
		for (int family = 0; family < FAMILIES.size(); family++) {
			List<Block> blocks = FAMILIES.get(family);
			for (int i = 0; i < blocks.size(); i++) {
				WHERE.put(blocks.get(i), new int[] {family, i});
			}
		}
	}

	private Chisel() {
	}

	/** The next face of this block's family, forwards or back, or nothing if the chisel does not know it. */
	public static @Nullable Block next(Block block, boolean back) {
		int[] where = WHERE.get(block);
		if (where == null) {
			return null;
		}
		List<Block> family = FAMILIES.get(where[0]);
		int step = (where[1] + (back ? -1 : 1) + family.size()) % family.size();
		return family.get(step);
	}

	/** How many faces the chisel knows, for the book. */
	public static int knownBlocks() {
		return WHERE.size();
	}

	public static List<List<Block>> families() {
		return FAMILIES;
	}

	/** Right-click with a chisel: the block steps to the next face of its family. */
	public static InteractionResult tryUse(UseOnContext context, ForgeType type) {
		ItemStack chisel = context.getItemInHand();
		Player player = context.getPlayer();
		if (type != ForgeType.CINCEL || player == null || !chisel.has(ModComponents.PARTS) || chisel.isBroken()) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		Block carved = next(state.getBlock(), player.isShiftKeyDown());
		if (carved == null) {
			return InteractionResult.PASS;
		}
		if (level instanceof net.minecraft.server.level.ServerLevel) {
			level.setBlockAndUpdate(pos, carry(state, carved.defaultBlockState()));
			chisel.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
			level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7F, 1.4F);
			level.levelEvent(2001, pos, Block.getId(state));
		}
		player.swing(context.getHand());
		return InteractionResult.SUCCESS;
	}

	/**
	 * Keeps whatever the old block and the new one both understand: the axis of a pillar, the water in a
	 * waterlogged block. Without this, carving a basalt pillar would lay it flat.
	 */
	private static BlockState carry(BlockState from, BlockState to) {
		BlockState result = to;
		for (net.minecraft.world.level.block.state.properties.Property<?> property : from.getProperties()) {
			if (result.hasProperty(property)) {
				result = copy(from, result, property);
			}
		}
		return result;
	}

	private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, net.minecraft.world.level.block.state.properties.Property<T> property) {
		return to.setValue(property, from.getValue(property));
	}

	/** The item a chisel is worth showing next to in the book. */
	public static ItemStack sample() {
		return new ItemStack(Items.STONE_BRICKS);
	}
}
