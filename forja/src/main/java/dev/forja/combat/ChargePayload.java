package dev.forja.combat;

import dev.forja.Forja;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the attack button, held past a swing, started charging a heavy blow ({@link #START}),
 * was let go to strike ({@link #RELEASE}), or the charge was dropped without striking ({@link #CANCEL}:
 * the player looked at a block, opened a screen, raised a shield). The server times the charge itself.
 */
public record ChargePayload(byte action) implements CustomPacketPayload {
	public static final byte CANCEL = 0;
	public static final byte START = 1;
	public static final byte RELEASE = 2;

	public static final CustomPacketPayload.Type<ChargePayload> TYPE = new CustomPacketPayload.Type<>(Forja.id("carga"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ChargePayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BYTE, ChargePayload::action,
		ChargePayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
