package com.dodoxgod.filo.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Solo lo usan las pruebas: quitar la invulnerabilidad de entrada de un jugador simulado. */
@Mixin(ServerPlayerEntity.class)
public interface ServerPlayerEntityAccessor {
	@Accessor("joinInvulnerabilityTicks")
	void filo$setJoinInvulnerabilityTicks(int ticks);
}
