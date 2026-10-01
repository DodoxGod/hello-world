package dev.forja.mixin;

import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's "charged" flag of a blaze (the flames round it, Blaze.isOnFire), for the warning of its network's burst (dev.forja.ai.BlazePilot). */
@Mixin(Blaze.class)
public interface BlazeAccess {
	@Invoker("setCharged")
	void forja$setCharged(boolean charged);
}
