package dev.forja.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How far a creeper's fuse has burned, for the mob brains (see dev.forja.ai.ObsM1). */
@Mixin(Creeper.class)
public interface CreeperAiAccess {
	@Accessor("swell")
	int forja$swell();
}
