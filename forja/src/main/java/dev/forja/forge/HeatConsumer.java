package dev.forja.forge;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that takes heat: a heat pipe beside it reaches out to it (the arm is drawn) and hands it its
 * fluid. The crucibles, the casting tables and the forge tables are consumers; anything new that wants heat
 * (the assembler) implements this and reads {@link Alloys#heatAt}, or {@link HeatSources#at} for the fluid's qualities.
 */
public interface HeatConsumer {
	/** Whether this particular state wants heat at all (a parts table is a forge table that does not). */
	default boolean takesHeat(BlockState state) {
		return true;
	}
}
