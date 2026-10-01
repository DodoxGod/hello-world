package com.dodoxgod.filo;

import com.dodoxgod.filo.combat.StaminaManager;
import com.dodoxgod.filo.config.FiloConfig;
import com.dodoxgod.filo.network.DodgePayload;
import com.dodoxgod.filo.network.StaminaPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Filo implements ModInitializer {
	public static final String MOD_ID = "filo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		FiloConfig.load();

		PayloadTypeRegistry.playC2S().register(DodgePayload.ID, DodgePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StaminaPayload.ID, StaminaPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(DodgePayload.ID,
				(payload, context) -> StaminaManager.onDodge(context.player()));

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient && !player.isSpectator()) {
				StaminaManager.onAttack(player);
			}
			return ActionResult.PASS;
		});

		ServerTickEvents.END_SERVER_TICK.register(StaminaManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> StaminaManager.remove(handler.getPlayer()));

		LOGGER.info("Filo cargado: combate más difícil pero justo");
	}
}
