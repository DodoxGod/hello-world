package dev.forja.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Ticks since the entity last swung, for the mob brains' view of a player (see dev.forja.ai.ObsM1). */
@Mixin(LivingEntity.class)
public interface LivingEntityAiAccess {
	@Accessor("attackStrengthTicker")
	int forja$attackStrengthTicker();
}
