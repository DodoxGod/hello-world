package dev.forja.mixin;

import dev.forja.world.BastionHome;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The great castle's grid hands out its world's home chunk for the home's own cell (see {@link BastionHome}), so
 * that generation, {@code /locate} and the other sets' exclusion zones all see the home castle as one of the set.
 * Every other placement carries no home and pays one field read.
 */
@Mixin(RandomSpreadStructurePlacement.class)
abstract class RandomSpreadStructurePlacementMixin implements BastionHome.Carrier {
	@Unique
	private volatile @Nullable BastionHome forja$home;

	@Override
	public @Nullable BastionHome forja$home() {
		return this.forja$home;
	}

	@Override
	public void forja$setHome(BastionHome home) {
		this.forja$home = home;
	}

	@Inject(method = "getPotentialStructureChunk", at = @At("HEAD"), cancellable = true)
	private void forja$homeCastle(long seed, int sourceX, int sourceZ, CallbackInfoReturnable<ChunkPos> cir) {
		BastionHome home = this.forja$home;
		if (home != null) {
			ChunkPos chunk = home.replace(seed, sourceX, sourceZ);
			if (chunk != null) {
				cir.setReturnValue(chunk);
			}
		}
	}
}
