package dev.forja.mixin;

import dev.forja.world.BastionHome;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A world's structure state is made here, next to the generator that knows its ground: the home castle needs both. */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorStateMixin {
	@Inject(method = "createState", at = @At("RETURN"))
	private void forja$homeCastle(HolderLookup<StructureSet> structureSets, RandomState randomState, long legacyLevelSeed,
		CallbackInfoReturnable<ChunkGeneratorStructureState> cir) {
		BastionHome.attach((ChunkGenerator) (Object) this, cir.getReturnValue(), randomState, legacyLevelSeed);
	}
}
