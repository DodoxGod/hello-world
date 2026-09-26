package dev.forja.command;

import dev.forja.block.entity.CastingTableBlockEntity;
import dev.forja.block.entity.CrucibleBlockEntity;
import dev.forja.block.entity.MeltTankBlockEntity;
import dev.forja.forge.ForgeType;
import dev.forja.item.CastingFrameItem;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The whole foundry line, stood up and running, for looking at: Andy asked to be shown "un sistema entero de
 * fundicion" in a world before deciding what to change about it. Pot, channels, a bank of tanks, the working row
 * with a moulding box and two casting tables kept hot by pavesa lanterns, and a gantry with a spout pouring down.
 * {@code /forja fundicion} puts one down in front of whoever asks.
 */
public final class FoundryDemo {
	/** The box {@link #build} clears around its origin, floor to ceiling: everything it puts down is inside. */
	public static final int MIN_X = -3;
	public static final int MAX_X = 8;
	public static final int MIN_Z = -3;
	public static final int MAX_Z = 7;
	/** How far in front of whoever asked the box starts: their own block and the two after it are left alone. */
	private static final int CLEAR = 3;

	private FoundryDemo() {
	}

	/**
	 * Where the origin goes for someone standing at {@code feet} and looking {@code facing}: the whole box in
	 * front of them, {@link #CLEAR} blocks off, with the line itself across the middle of their view.
	 */
	public static BlockPos originFor(BlockPos feet, Direction facing) {
		return switch (facing) {
			case NORTH -> feet.offset(-2, 0, -CLEAR - MAX_Z);
			case SOUTH -> feet.offset(-2, 0, CLEAR - MIN_Z);
			case WEST -> feet.offset(-CLEAR - MAX_X, 0, -1);
			default -> feet.offset(CLEAR - MIN_X, 0, -1);
		};
	}

	public static void build(ServerLevel level, BlockPos origin) {
		int px = origin.getX();
		int y = origin.getY();
		int pz = origin.getZ();
		for (BlockPos pos : BlockPos.betweenClosed(px + MIN_X, y - 1, pz + MIN_Z, px + MAX_X, y + 5, pz + MAX_Z)) {
			level.setBlockAndUpdate(pos, pos.getY() == y - 1 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		// Back row: the pot, two lengths of channel and the bank of tanks it fills.
		BlockPos potAt = new BlockPos(px, y, pz);
		level.setBlockAndUpdate(potAt, ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
		if (level.getBlockEntity(potAt) instanceof CrucibleBlockEntity pot) {
			pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 32));
			pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.IRON_INGOT, 16));
			pot.setItem(CrucibleBlockEntity.SLOT_SECOND, new ItemStack(Items.COAL, 16));
		}
		for (int dx = 1; dx <= 2; dx++) {
			level.setBlockAndUpdate(new BlockPos(px + dx, y, pz), ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
		}
		for (int dx = 3; dx <= 4; dx++) {
			for (int dy = 0; dy <= 1; dy++) {
				BlockPos at = new BlockPos(px + dx, y + dy, pz);
				level.setBlockAndUpdate(at, ModBlocks.CUBA_DE_COLADA.defaultBlockState());
				if (level.getBlockEntity(at) instanceof MeltTankBlockEntity tank) {
					tank.fill(Items.IRON_INGOT, dy == 0 ? MeltTankBlockEntity.CAPACITY : 90);
				}
			}
		}
		// Down each end to the working row, and the line of channel along it.
		level.setBlockAndUpdate(new BlockPos(px, y, pz + 1), ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(px + 4, y, pz + 1), ModBlocks.CONDUCTO_DE_ACERO.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(px, y, pz + 2), ModBlocks.CAJA_DE_MOLDEO.defaultBlockState());
		for (int dx = 1; dx <= 4; dx++) {
			level.setBlockAndUpdate(new BlockPos(px + dx, y, pz + 2), (dx == 4 ? ModBlocks.CONDUCTO_DE_ACERO : ModBlocks.CONDUCTO_DE_COLADA).defaultBlockState());
		}
		// The gantry: a spur of channel three blocks up, ending in a spout over the left-hand table.
		for (int dz = 0; dz <= 2; dz++) {
			level.setBlockAndUpdate(new BlockPos(px + 1, y + 3, pz + dz), ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
		}
		level.setBlockAndUpdate(new BlockPos(px + 1, y + 3, pz + 3), ModBlocks.CANO_DE_COLADA.defaultBlockState());
		// The two tables hanging off the line, a lantern either side of each to keep them hot.
		level.setBlockAndUpdate(new BlockPos(px + 1, y, pz + 3), ModBlocks.MESA_DE_LOSA.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(px + 3, y, pz + 3), ModBlocks.MESA_DE_BRASA.defaultBlockState());
		for (int dx : new int[] {0, 2, 4}) {
			level.setBlockAndUpdate(new BlockPos(px + dx, y, pz + 3), ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		}
		ForgeType[] made = {ForgeType.PICO, ForgeType.ESPADA};
		int[] xs = {1, 3};
		for (int i = 0; i < xs.length; i++) {
			BlockPos at = new BlockPos(px + xs[i], y, pz + 3);
			if (level.getBlockEntity(at) instanceof CastingTableBlockEntity table) {
				// warm first, or the first pour comes out rough
				for (int tick = 0; tick < CastingTableBlockEntity.EVERY * 8; tick++) {
					CastingTableBlockEntity.serverTick(level, at, level.getBlockState(at), table);
				}
				table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingFrameItem.of(made[i]));
			}
		}
	}
}
