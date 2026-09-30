package dev.forja.mixin;

import java.util.OptionalInt;
import java.util.function.Predicate;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * One walk down a column of noise, with Forja's own test on every block: the castle's site test learns the ground
 * and whether water stands over it in one walk instead of two (see world/BastionGround).
 */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseColumnAccess {
	@Invoker("iterateNoiseColumn")
	OptionalInt forja$iterateNoiseColumn(LevelHeightAccessor heightAccessor, RandomState randomState, int blockX, int blockZ,
		@Nullable MutableObject<NoiseColumn> columnReference, @Nullable Predicate<BlockState> tester);
}
