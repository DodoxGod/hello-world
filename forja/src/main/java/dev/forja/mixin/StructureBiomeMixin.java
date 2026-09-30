package dev.forja.mixin;

import java.util.Optional;

import dev.forja.world.BastionGround;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla checks a structure's biome at its start piece, and the great castle's start piece sits 25 blocks under the
 * courtyard: there the biome can be a cave (lush or dripstone caves under a plain), and a site that passed every
 * test of {@link BastionGround} was thrown away for it. The castle's biome is checked by {@link BastionGround} at
 * the start column and the four corners, at sea level, before anything else, so here it is not asked again.
 */
@Mixin(Structure.class)
abstract class StructureBiomeMixin {
	@Shadow
	protected abstract Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context);

	@Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
	private void forja$bastionBiome(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		if ((Object) this instanceof JigsawStructure jigsaw && BastionGround.isBastion(jigsaw.getStartPool())) {
			cir.setReturnValue(this.findGenerationPoint(context));
		}
	}
}
