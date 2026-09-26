package dev.forja.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Blocks a monster puts in the world for a moment (a spider's web): taken away again when their time is up. */
public final class TemporaryBlocks {
	private record Placed(ServerLevel level, BlockPos pos, Block block, long until) {
	}

	private static final List<Placed> PLACED = new ArrayList<>();

	private TemporaryBlocks() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PLACED.isEmpty()) {
				return;
			}
			for (Iterator<Placed> it = PLACED.iterator(); it.hasNext();) {
				Placed placed = it.next();
				if (placed.level().getGameTime() >= placed.until()) {
					if (placed.level().getBlockState(placed.pos()).is(placed.block())) {
						placed.level().setBlockAndUpdate(placed.pos(), Blocks.AIR.defaultBlockState());
					}
					it.remove();
				}
			}
		});
	}

	public static void add(ServerLevel level, BlockPos pos, Block block, int ticks) {
		PLACED.add(new Placed(level, pos.immutable(), block, level.getGameTime() + ticks));
	}
}
