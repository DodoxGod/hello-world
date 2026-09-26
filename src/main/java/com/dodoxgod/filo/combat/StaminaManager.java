package com.dodoxgod.filo.combat;

import com.dodoxgod.filo.Filo;
import com.dodoxgod.filo.config.FiloConfig;
import com.dodoxgod.filo.network.StaminaPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Estamina de los jugadores (solo servidor). Atacar, esquivar y bloquear la gastan; se recupera
 * tras un momento sin gastarla, más despacio cuanto más pesada sea la armadura.
 */
public final class StaminaManager {
	private static final Identifier WEIGHT_MODIFIER = Filo.id("armor_weight");
	private static final Map<UUID, Data> DATA = new HashMap<>();

	public static final class Data {
		float stamina = FiloConfig.get().stamina.max;
		float lastSynced = -1f;
		long lastSpend;
		double weight;
		/** El último ataque se hizo sin estamina suficiente. */
		boolean tiredAttack;
		long dodgeUntil = -1;
		long dodgeCooldownUntil;
	}

	private StaminaManager() {
	}

	public static Data get(PlayerEntity player) {
		return DATA.computeIfAbsent(player.getUuid(), id -> new Data());
	}

	public static void remove(PlayerEntity player) {
		DATA.remove(player.getUuid());
	}

	private static boolean exempt(PlayerEntity player) {
		return !FiloConfig.get().stamina.enabled || player.isCreative() || player.isSpectator();
	}

	/** Gasta estamina si hay suficiente. Si no, la deja a 0 y devuelve false. */
	public static boolean trySpend(PlayerEntity player, float amount) {
		if (exempt(player)) return true;
		Data data = get(player);
		data.lastSpend = player.getWorld().getTime();
		if (data.stamina >= amount) {
			data.stamina -= amount;
			return true;
		}
		data.stamina = 0f;
		return false;
	}

	public static void restore(PlayerEntity player, float amount) {
		Data data = get(player);
		data.stamina = Math.min(FiloConfig.get().stamina.max, data.stamina + amount);
	}

	public static boolean isDodging(PlayerEntity player, long now) {
		return get(player).dodgeUntil >= now;
	}

	public static boolean consumeTiredAttack(PlayerEntity player) {
		Data data = get(player);
		boolean tired = data.tiredAttack;
		data.tiredAttack = false;
		return tired;
	}

	public static void onAttack(PlayerEntity player) {
		get(player).tiredAttack = !trySpend(player, FiloConfig.get().stamina.attackCost);
	}

	/** El cliente avisa de que ha esquivado. */
	public static void onDodge(ServerPlayerEntity player) {
		FiloConfig cfg = FiloConfig.get();
		if (!cfg.dodge.enabled || player.isSpectator()) return;
		Data data = get(player);
		long now = player.getWorld().getTime();
		if (now < data.dodgeCooldownUntil) return;
		if (!trySpend(player, cfg.stamina.dodgeCost)) return;
		data.dodgeUntil = now + cfg.dodge.iframeTicks;
		data.dodgeCooldownUntil = now + cfg.dodge.cooldownTicks;
		CombatFeedback.dodge(player);
	}

	public static void tick(MinecraftServer server) {
		FiloConfig cfg = FiloConfig.get();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			Data data = get(player);
			long now = player.getWorld().getTime();

			if (now % 10 == 0) {
				updateWeight(player, data, cfg);
			}

			float max = cfg.stamina.max;
			if (exempt(player)) {
				data.stamina = max;
			} else if (now - data.lastSpend >= cfg.stamina.regenDelayTicks && data.stamina < max) {
				double penalty = Math.min(0.9, data.weight * cfg.stamina.regenPenaltyPerWeight);
				data.stamina = (float) Math.min(max, data.stamina + cfg.stamina.regenPerTick * (1.0 - penalty));
			}

			boolean changed = Math.abs(data.stamina - data.lastSynced) >= 0.5f
					|| (data.stamina == max && data.lastSynced != max);
			if (changed) {
				data.lastSynced = data.stamina;
				ServerPlayNetworking.send(player, new StaminaPayload(data.stamina, max));
			}
		}
	}

	private static void updateWeight(ServerPlayerEntity player, Data data, FiloConfig cfg) {
		double weight = ArmorCalculator.armorWeight(player);
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		boolean slows = cfg.stamina.armorWeightSlows && weight > 0.0001;
		if (speed != null && (weight != data.weight || slows != speed.hasModifier(WEIGHT_MODIFIER))) {
			speed.removeModifier(WEIGHT_MODIFIER);
			if (slows) {
				speed.addTemporaryModifier(new EntityAttributeModifier(WEIGHT_MODIFIER, -weight,
						EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		data.weight = weight;
	}
}
