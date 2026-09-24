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
		ServerPlayNetworking.registerGlobalReceiver(DodgePayload.TYPE, (payload, context) -> Stamina.onDodge(context.player()));

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && !player.isSpectator()) {
				Stamina.onAttack(player);
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CombatHooks::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(Stamina::tick);
		ParryRhythm.register();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Stamina.forget(handler.getPlayer()));
	}
}
