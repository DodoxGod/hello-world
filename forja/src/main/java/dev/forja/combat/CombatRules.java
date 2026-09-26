package dev.forja.combat;

import dev.forja.Forja;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server to client, on joining: the numbers the client needs to act on its own before the server
 * answers. The dodge is moved on the client so it feels instant, so the client has to judge it by the
 * server's config and not by whatever its own config/forja.json happens to say.
 */
public record CombatRules(boolean enabled, boolean dodge, boolean stamina, float staminaMax, float dodgeCost,
	int dodgeCooldownTicks, float dodgeStrength, float dodgeLift, boolean chargedAttack, int chargeDelayTicks,
	int chargeFullTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CombatRules> TYPE = new CustomPacketPayload.Type<>(Forja.id("reglas_combate"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CombatRules> STREAM_CODEC = StreamCodec.of(
		(buf, rules) -> {
			buf.writeBoolean(rules.enabled);
			buf.writeBoolean(rules.dodge);
			buf.writeBoolean(rules.stamina);
			buf.writeFloat(rules.staminaMax);
			buf.writeFloat(rules.dodgeCost);
			buf.writeVarInt(rules.dodgeCooldownTicks);
			buf.writeFloat(rules.dodgeStrength);
			buf.writeFloat(rules.dodgeLift);
			buf.writeBoolean(rules.chargedAttack);
			buf.writeVarInt(rules.chargeDelayTicks);
			buf.writeVarInt(rules.chargeFullTicks);
		},
		buf -> new CombatRules(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat(),
			buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt())
	);

	/** The rules as this side's own config has them. */
	public static CombatRules local() {
		CombatConfig cfg = CombatConfig.get();
		return new CombatRules(cfg.enabled, cfg.dodge, cfg.stamina, cfg.staminaMax, cfg.dodgeCost, cfg.dodgeCooldownTicks,
			(float) cfg.dodgeStrength, (float) cfg.dodgeLift, cfg.chargedAttack, cfg.chargeDelayTicks, cfg.chargeFullTicks);
	}

	public static void sendTo(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, local());
		}
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
