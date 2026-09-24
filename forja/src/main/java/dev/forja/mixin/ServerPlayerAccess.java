package dev.forja.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** For the game tests: a simulated player should not start with a spawn's grace period. */
@Mixin(ServerPlayer.class)
public interface ServerPlayerAccess {
	@Accessor("spawnInvulnerableTime")
	void forja$setSpawnInvulnerableTime(int ticks);
}
