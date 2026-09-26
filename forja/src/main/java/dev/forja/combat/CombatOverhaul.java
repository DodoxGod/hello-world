package dev.forja.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.InteractionResult;

/**
 * The combat overhaul: armor that cares what hits it and where, stamina, an active dodge, mob
 * posture, and vanilla monsters that warn before they strike. Everything reads {@link CombatConfig}.
 */
public final class CombatOverhaul {
	private CombatOverhaul() {
	}

	public static void register() {
		// Registers the synced stamina attachment now, on both sides, before any player joins.
		java.util.Objects.requireNonNull(Stamina.VALUE);
		PayloadTypeRegistry.serverboundPlay().register(DodgePayload.TYPE, DodgePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ChargePayload.TYPE, ChargePayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ChargePayload.TYPE,
			(payload, context) -> ChargedStrike.onPayload(context.player(), payload.action()));
		WeaponGuard.register();
		PayloadTypeRegistry.clientboundPlay().register(CombatAnim.TYPE, CombatAnim.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CombatRules.TYPE, CombatRules.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DodgePayload.TYPE,
			(payload, context) -> Stamina.onDodge(context.player(), payload.x(), payload.z()));
		// The client judges its own dodge before the server answers, so it has to judge it by the server's rules.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CombatRules.sendTo(handler.player));

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && !player.isSpectator()) {
				dev.forja.ai.PlayerHabits.onAttack(player, player.distanceTo(entity), ChargedStrike.striking(player) != null);
			}
			// A charged blow goes through Player#attack too, and has already paid for itself.
			if (!level.isClientSide() && !player.isSpectator() && ChargedStrike.striking(player) == null) {
				Stamina.onAttack(player);
				Combos.onAttack(player, player.getAttackStrengthScale(0.5F));
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CombatHooks::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CombatHooks::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(Stamina::tick);
		ParryRhythm.register();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Stamina.forget(handler.getPlayer()));
	}
}
