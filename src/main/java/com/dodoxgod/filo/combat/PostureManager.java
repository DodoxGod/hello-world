package com.dodoxgod.filo.combat;

import com.dodoxgod.filo.config.FiloConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Barra de equilibrio de los mobs. Cada golpe la llena; al llenarse, el mob queda aturdido:
 * no ataca, apenas se mueve y recibe más daño. Se vacía sola si pasa un rato sin recibir golpes.
 * Los jugadores no tienen postura: para ellos cumple ese papel la estamina.
 */
public final class PostureManager {
	private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

	private static final class State {
		double value;
		long lastHit;
		long staggerUntil;
	}

	private PostureManager() {
	}

	public static double maxPosture(LivingEntity entity) {
		FiloConfig.Posture cfg = FiloConfig.get().posture;
		return entity.getMaxHealth() * cfg.healthFactor + cfg.base;
	}

	/** Suma postura por un golpe de {@code damage} puntos del tipo indicado. */
	public static void onHit(LivingEntity entity, DamageKind kind, float damage, long now) {
		FiloConfig.Posture cfg = FiloConfig.get().posture;
		if (!cfg.enabled || entity instanceof PlayerEntity) return;
		add(entity, damage * cfg.factor(kind), now);
	}

	/** Aturde al instante (parry perfecto). */
	public static void breakPosture(LivingEntity entity, long now) {
		if (entity instanceof PlayerEntity player) {
			// En PvP un parry no inmoviliza, pero sí frena.
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 1, false, false));
			CombatFeedback.stagger(player);
			return;
		}
		if (!FiloConfig.get().posture.enabled) return;
		add(entity, maxPosture(entity), now);
	}

	private static void add(LivingEntity entity, double amount, long now) {
		State state = STATES.computeIfAbsent(entity, e -> new State());
		decay(state, now);
		state.lastHit = now;
		if (now < state.staggerUntil) return;
		state.value += amount;
		if (state.value >= maxPosture(entity)) {
			state.value = 0;
			stagger(entity, state, now);
		}
	}

	private static void stagger(LivingEntity entity, State state, long now) {
		int ticks = FiloConfig.get().posture.staggerTicks;
		state.staggerUntil = now + ticks;
		entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 4, false, false));
		entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 1, false, false));
		CombatFeedback.stagger(entity);
	}

	private static void decay(State state, long now) {
		FiloConfig.Posture cfg = FiloConfig.get().posture;
		long idle = now - state.lastHit - cfg.regenDelayTicks;
		if (idle > 0) {
			state.value = Math.max(0.0, state.value - idle * cfg.regenPerTick);
		}
	}

	public static boolean isStaggered(LivingEntity entity, long now) {
		State state = STATES.get(entity);
		return state != null && now < state.staggerUntil;
	}
}
