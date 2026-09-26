package dev.forja.combat;

import dev.forja.Forja;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server to client: something happened in a fight that the client should show, not only hear. The
 * server already knows every one of these moments (it is where the telegraph, the stagger and the parry
 * are decided), so it says so, and the client turns it into a pose.
 *
 * @param entity the entity it happened to
 * @param kind   what happened, an ordinal of {@link Kind}
 * @param ticks  how long it lasts, where that matters
 * @param a      a number that depends on the kind (see {@link Kind})
 * @param b      a second one
 */
public record CombatAnim(int entity, byte kind, short ticks, float a, float b) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CombatAnim> TYPE = new CustomPacketPayload.Type<>(Forja.id("animacion_combate"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CombatAnim> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, CombatAnim::entity,
		ByteBufCodecs.BYTE, CombatAnim::kind,
		ByteBufCodecs.SHORT, CombatAnim::ticks,
		ByteBufCodecs.FLOAT, CombatAnim::a,
		ByteBufCodecs.FLOAT, CombatAnim::b,
		CombatAnim::new
	);

	public enum Kind {
		/** A melee mob is winding up a blow. */
		TELEGRAPH,
		/** A zombie crouches to leap. */
		LUNGE,
		/** Posture broke. */
		STAGGER,
		/** A dodge; a and b are the world direction (x, z). */
		DODGE,
		/** A parry landed; a is 1 when it was perfect. */
		PARRY,
		/** A guard ran out of stamina. */
		GUARD_BREAK,
		/** To the attacker only: a blow of theirs landed; a is the damage done. */
		HIT,
		/** To the victim only: a blow reached them; a is the damage taken. */
		HURT,
		/** The posture bar changed; a is how full (0 to 1), b how much of it drains per tick, ticks the delay before it does. */
		POSTURE,
		/** A player started charging a heavy blow (a = 1, ticks = to full charge) or let it go (a = 0). */
		CHARGE,
		/** A player's combo moved on: ticks is the step (2 = the next hit finishes it, 3 = it did). */
		COMBO,
		/** A staggered foe took a finishing blow. */
		FINISHER,
		/** A player dodged a blow that would have landed; ticks is how long the counter stays open. */
		PERFECT_DODGE,
		/** To the player only: the pressure on them changed; a is its value, b how much drains per tick, ticks the wait before it does. */
		PRESSURE;

		private static final Kind[] VALUES = values();

		public static Kind of(byte ordinal) {
			return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
		}
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static CombatAnim of(Entity entity, Kind kind, int ticks, float a, float b) {
		return new CombatAnim(entity.getId(), (byte) kind.ordinal(), (short) Math.clamp(ticks, 0, Short.MAX_VALUE), a, b);
	}

	/** To everyone who can see the entity, and to the entity itself when it is a player. */
	public static void broadcast(Entity entity, Kind kind, int ticks, float a, float b) {
		if (entity.level().isClientSide()) {
			return;
		}
		CombatAnim payload = of(entity, kind, ticks, a, b);
		for (ServerPlayer player : PlayerLookup.tracking(entity)) {
			if (ServerPlayNetworking.canSend(player, TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
		if (entity instanceof ServerPlayer self && ServerPlayNetworking.canSend(self, TYPE)) {
			ServerPlayNetworking.send(self, payload);
		}
	}

	public static void broadcast(Entity entity, Kind kind, int ticks) {
		broadcast(entity, kind, ticks, 0.0F, 0.0F);
	}

	/** To one player, about an entity (their target, or themselves). */
	public static void sendTo(ServerPlayer player, Entity about, Kind kind, int ticks, float a, float b) {
		if (ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, of(about, kind, ticks, a, b));
		}
	}
}
