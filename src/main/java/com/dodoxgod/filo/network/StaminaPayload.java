package com.dodoxgod.filo.network;

import com.dodoxgod.filo.Filo;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor → cliente: estamina actual para dibujar la barra. */
public record StaminaPayload(float stamina, float max) implements CustomPayload {
	public static final Id<StaminaPayload> ID = new Id<>(Filo.id("stamina"));
	public static final PacketCodec<RegistryByteBuf, StaminaPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, StaminaPayload::stamina,
			PacketCodecs.FLOAT, StaminaPayload::max,
			StaminaPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
