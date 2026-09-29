package dev.forja.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Ticks since the entity last swung, for the mob brains' view of a player (see dev.forja.ai.ObsM1); and the box
 * a blow has to reach, for a mob's weapon reach (MobMixin).
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAiAccess {
	@Accessor("attackStrengthTicker")
	int forja$attackStrengthTicker();

	@Invoker("getHitbox")
	net.minecraft.world.phys.AABB forja$hitbox();
}
