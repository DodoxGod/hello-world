package dev.forja.combat;

import dev.forja.Forja;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player dodged. The client already moved; the server grants the i-frames. */
public record DodgePayload() implements CustomPacketPayload {
	public static final DodgePayload INSTANCE = new DodgePayload();
	public static final CustomPacketPayload.Type<DodgePayload> TYPE = new CustomPacketPayload.Type<>(Forja.id("esquiva"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DodgePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
