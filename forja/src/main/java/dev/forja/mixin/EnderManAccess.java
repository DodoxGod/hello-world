package dev.forja.mixin;

import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The enderman's own teleport to a spot: solid, dry ground under it, room for its body, and vanilla's sound at
 * both ends, portal particles and game event. The dodge (dev.forja.ai.EnderDodge) blinks with it.
 */
@Mixin(EnderMan.class)
public interface EnderManAccess {
	@Invoker("teleport")
	boolean forja$teleport(double x, double y, double z);
}
