package dev.forja.combat;

import dev.forja.Forja;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player dodged, and which way (world x and z). The client already moved; the
 * server grants the i-frames and shows the lean to everyone watching.
 */
public record DodgePayload(float x, float z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DodgePayload> TYPE = new CustomPacketPayload.Type<>(Forja.id("esquiva"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DodgePayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.FLOAT, DodgePayload::x,
		ByteBufCodecs.FLOAT, DodgePayload::z,
		DodgePayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
