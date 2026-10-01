package com.dodoxgod.filo.network;

import com.dodoxgod.filo.Filo;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Cliente → servidor: el jugador ha esquivado (el movimiento ya lo aplicó el cliente). */
public record DodgePayload() implements CustomPayload {
	public static final DodgePayload INSTANCE = new DodgePayload();
	public static final Id<DodgePayload> ID = new Id<>(Filo.id("dodge"));
	public static final PacketCodec<RegistryByteBuf, DodgePayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
