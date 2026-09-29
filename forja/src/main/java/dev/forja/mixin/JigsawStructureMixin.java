package dev.forja.mixin;

import java.util.Optional;

import dev.forja.world.BastionGround;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The great castle asks about the ground before it is assembled; see {@link BastionGround}. */
@Mixin(JigsawStructure.class)
abstract class JigsawStructureMixin {
	@Shadow
	@Final
	private Holder<StructureTemplatePool> startPool;

	@Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
	private void forja$bastionGround(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		if (BastionGround.isBastion(this.startPool) && !BastionGround.suitable(context)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
