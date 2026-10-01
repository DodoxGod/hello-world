package dev.forja.world;

import dev.forja.Forja;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Tells the client which event is running, so that the sky can say so.
 *
 * <p>Until now nothing left the server about an event except a line of chat and a handful of particles
 * thrown into the air above each player. The event itself lived in two static fields that the client
 * never saw, so a blood moon looked exactly like an ordinary night with some red dust in it — which is
 * a shame, because these are the rarest things in the mod and the only way to get nine of the upgrades.
 *
 * <p>An event is one small enum and a tick count, so this is one small packet: sent when an event
 * starts, when it stops, and to anybody who joins while one is running. Everything the sky does with
 * it is worked out on the client from those two numbers.
 */
public record EventSky(int ordinal, int ticksLeft) implements CustomPacketPayload {
	/** What goes in `ordinal` when nothing is running. */
	public static final int NONE = -1;

	public static final CustomPacketPayload.Type<EventSky> TYPE =
		new CustomPacketPayload.Type<>(Forja.id("evento_cielo"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EventSky> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, EventSky::ordinal,
		ByteBufCodecs.VAR_INT, EventSky::ticksLeft,
		EventSky::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** The event this packet is about, or null for "nothing is running". */
	public @Nullable WorldEvents event() {
		return this.ordinal < 0 || this.ordinal >= WorldEvents.values().length
			? null : WorldEvents.values()[this.ordinal];
	}

	public static EventSky of(@Nullable WorldEvents event, int ticksLeft) {
		return new EventSky(event == null ? NONE : event.ordinal(), Math.max(0, ticksLeft));
	}

	/** Tell one player what the sky is doing. */
	public static void sendTo(ServerPlayer player, @Nullable WorldEvents event, int ticksLeft) {
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, of(event, ticksLeft));
	}

	/** Tell everybody. */
	public static void sendAll(ServerLevel level, @Nullable WorldEvents event, int ticksLeft) {
		for (ServerPlayer player : level.players()) {
			sendTo(player, event, ticksLeft);
		}
	}

	public static void register() {
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(TYPE, STREAM_CODEC);
		// And anybody arriving mid-event is told on the way in, otherwise they get an ordinary sky
		// until it ends and then wonder what everyone was talking about.
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerLevel level = handler.player.level() instanceof ServerLevel world ? world : null;
			if (level == null) {
				return;
			}
			WorldEvents running = WorldEvents.active(level);
			sendTo(handler.player, running, running == null ? 0 : WorldEvents.ticksLeft(level));
		});
	}
}
