package dev.forja.world;

import dev.forja.Forja;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A constellation lighting up in the Fallen Smith's fight (docs/HERRERO_DIMENSION.md, 3.6), told to every
 * player in the graveyard so their sky can draw it: which mould ({@link StarChart#CONSTELLATIONS}), in which
 * of the five colours, for whose side, and how long an ash storm it brings (0 for none).
 */
public record StarCast(int constellation, int tier, boolean forBoss, int stormTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StarCast> TYPE = new CustomPacketPayload.Type<>(Forja.id("constelacion"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StarCast> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, StarCast::constellation,
		ByteBufCodecs.VAR_INT, StarCast::tier,
		ByteBufCodecs.BOOL, StarCast::forBoss,
		ByteBufCodecs.VAR_INT, StarCast::stormTicks,
		StarCast::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void register() {
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(TYPE, STREAM_CODEC);
	}

	public static void sendAll(ServerLevel level, StarCast cast) {
		for (ServerPlayer player : level.players()) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, cast);
		}
	}
}
