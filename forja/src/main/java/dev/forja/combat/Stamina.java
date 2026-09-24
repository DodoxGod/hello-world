package dev.forja.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.forja.Forja;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Stamina, server side. Swinging, dodging and holding a shield up against a blow spend it; it comes
 * back after a moment of rest, slower the heavier the armor. The value is synced to its own player
 * for the bar on screen.
 */
public final class Stamina {
	public static final AttachmentType<Float> VALUE = AttachmentRegistry.<Float>builder()
		.initializer(() -> CombatConfig.get().staminaMax)
		.syncWith(ByteBufCodecs.FLOAT.cast(), AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Forja.id("estamina"));

	private static final Identifier WEIGHT_MODIFIER = Forja.id("peso_armadura");
	private static final Map<UUID, Data> DATA = new HashMap<>();

	private static final class Data {
		float stamina = CombatConfig.get().staminaMax;
		float synced = -1F;
		long lastSpend;
		double weight = -1.0;
		boolean tiredAttack;
		long dodgeUntil = -1;
		long dodgeCooldownUntil;
	}

	private Stamina() {
	}

	private static Data data(Player player) {
		return DATA.computeIfAbsent(player.getUUID(), id -> new Data());
	}

	public static void forget(Player player) {
		DATA.remove(player.getUUID());
	}

	private static boolean exempt(Player player) {
		return !CombatConfig.get().enabled || !CombatConfig.get().stamina || player.isCreative() || player.isSpectator();
	}

	public static float value(Player player) {
		return data(player).stamina;
	}

	/** Spends stamina if there is enough; otherwise empties it and says no. */
	public static boolean trySpend(Player player, float amount) {
		if (exempt(player)) return true;
		Data data = data(player);
		data.lastSpend = player.level().getGameTime();
		if (data.stamina >= amount) {
			data.stamina -= amount;
			return true;
		}
		data.stamina = 0F;
		return false;
	}

	public static void restore(Player player, float amount) {
		Data data = data(player);
		data.stamina = Math.min(CombatConfig.get().staminaMax, data.stamina + amount);
	}

	public static boolean isDodging(Player player, long now) {
		return data(player).dodgeUntil >= now;
	}

	public static void onAttack(Player player) {
		data(player).tiredAttack = !trySpend(player, CombatConfig.get().attackCost);
	}

	/** Whether the swing that is landing now was made out of breath. Reading it clears it. */
	public static boolean consumeTiredAttack(Player player) {
		Data data = data(player);
		boolean tired = data.tiredAttack;
		data.tiredAttack = false;
		return tired;
	}

	public static void onDodge(ServerPlayer player) {
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.enabled || !cfg.dodge || player.isSpectator()) return;
		Data data = data(player);
		long now = player.level().getGameTime();
		if (now < data.dodgeCooldownUntil || !trySpend(player, cfg.dodgeCost)) return;
		data.dodgeUntil = now + cfg.dodgeIframeTicks;
		data.dodgeCooldownUntil = now + cfg.dodgeCooldownTicks;
		CombatFeedback.dodge(player);
	}

	public static void tick(MinecraftServer server) {
		CombatConfig cfg = CombatConfig.get();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Data data = data(player);
			long now = player.level().getGameTime();
			if (now % 10 == 0) {
				updateWeight(player, data, cfg);
			}
			float max = cfg.staminaMax;
			if (exempt(player)) {
				data.stamina = max;
			} else if (now - data.lastSpend >= cfg.staminaRegenDelayTicks && data.stamina < max) {
				double penalty = Math.min(0.9, Math.max(0.0, data.weight) * cfg.regenPenaltyPerWeight);
				data.stamina = (float) Math.min(max, data.stamina + cfg.staminaRegenPerTick * (1.0 - penalty));
			}
			if (Math.abs(data.stamina - data.synced) >= 0.5F || (data.stamina == max && data.synced != max)) {
				data.synced = data.stamina;
				player.setAttached(VALUE, data.stamina);
			}
		}
	}

	/** The heavier the worn set, the slower the walk: up to maxArmorSlow with the heaviest plate. */
	private static void updateWeight(ServerPlayer player, Data data, CombatConfig cfg) {
		double weight = cfg.enabled ? ArmorCalculator.armorWeight(player) : 0.0;
		double slow = weight * cfg.maxArmorSlow;
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && (weight != data.weight || (slow > 0.0001) != speed.hasModifier(WEIGHT_MODIFIER))) {
			speed.removeModifier(WEIGHT_MODIFIER);
			if (slow > 0.0001) {
				speed.addTransientModifier(new AttributeModifier(WEIGHT_MODIFIER, -slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		data.weight = weight;
	}
}
