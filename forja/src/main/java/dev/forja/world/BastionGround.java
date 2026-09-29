package dev.forja.world;

import java.util.Arrays;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Where the great castle may stand. It is a single 250-block plan laid flat at the height of the one column under
 * its start piece, and it used to come down wherever the dice said: over the sea with its gate in the water,
 * half inside a mesa, across a valley (Andy, 2026-09-29). Before anything is assembled, the ground under the whole
 * plan is sampled and the site is turned down unless it is dry, fairly level, and the start column sits at the
 * ground's usual height.
 */
public final class BastionGround {
	/** Half the width of the ground looked at, around the start piece's corner (the plan is about 250 across). */
	static final int REACH = 128;
	/** Samples along each side of the grid. */
	static final int GRID = 9;
	/** The highest and the lowest sampled ground may differ by this much, and no more. */
	static final int MAX_RISE = 14;
	/** The start column (which sets the courtyard's height) may be this far from the ground's median. */
	static final int MAX_OFF_MEDIAN = 3;
	/** Samples with water over the ground allowed: a puddle, not a lake. */
	static final int MAX_WET = 1;

	private BastionGround() {
	}

	/** Whether a jigsaw structure starting from this pool is the great castle (and not its clipped test twin). */
	public static boolean isBastion(Holder<StructureTemplatePool> startPool) {
		return startPool.unwrapKey()
			.map(key -> key.identifier().getNamespace().equals("forja") && key.identifier().getPath().startsWith("bastion/"))
			.orElse(false);
	}

	public static boolean suitable(Structure.GenerationContext context) {
		int x0 = context.chunkPos().getMinBlockX();
		int z0 = context.chunkPos().getMinBlockZ();
		int[] heights = new int[GRID * GRID];
		int wet = 0;
		int n = 0;
		for (int i = 0; i < GRID; i++) {
			for (int k = 0; k < GRID; k++) {
				int x = x0 - REACH + i * 2 * REACH / (GRID - 1);
				int z = z0 - REACH + k * 2 * REACH / (GRID - 1);
				int surface = height(context, x, z, Heightmap.Types.WORLD_SURFACE_WG);
				int floor = height(context, x, z, Heightmap.Types.OCEAN_FLOOR_WG);
				heights[n++] = floor;
				if (surface > floor) {
					wet++;
				}
			}
		}
		if (wet > MAX_WET) {
			return false;
		}
		int[] sorted = heights.clone();
		Arrays.sort(sorted);
		if (sorted[sorted.length - 1] - sorted[0] > MAX_RISE) {
			return false;
		}
		int median = sorted[sorted.length / 2];
		int start = height(context, x0, z0, Heightmap.Types.WORLD_SURFACE_WG);
		return Math.abs(start - median) <= MAX_OFF_MEDIAN;
	}

	private static int height(Structure.GenerationContext context, int x, int z, Heightmap.Types type) {
		return context.chunkGenerator().getBaseHeight(x, z, type, context.heightAccessor(), context.randomState());
	}
}
