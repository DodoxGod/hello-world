package com.dodoxgod.filo.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;

/** Jugador simulado que, a diferencia del FakePlayer de Fabric, sí puede recibir daño. */
class TestPlayer extends FakePlayer {
	TestPlayer(ServerWorld world, GameProfile profile) {
		super(world, profile);
	}

	@Override
	public boolean isInvulnerableTo(DamageSource damageSource) {
		return false;
	}
}
