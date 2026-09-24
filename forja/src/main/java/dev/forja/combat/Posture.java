package dev.forja.combat;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A hidden balance bar on every mob. Blows fill it (blunt ones most); full, the mob is staggered:
 * it cannot swing, barely moves and takes more damage. It drains on its own once the blows stop.
 * Players have no posture: stamina plays that part for them.
 */
public final class Posture {
	private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

	private static final class State {
		double value;
		long lastHit;
		long staggerUntil;
	}

	private Posture() {
	}

	public static double max(LivingEntity entity) {
		CombatConfig cfg = CombatConfig.get();
		return entity.getMaxHealth() * cfg.postureHealthFactor + cfg.postureBase;
	}

	public static void onHit(LivingEntity entity, DamageKind kind, float damage, long now) {
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.posture || entity instanceof Player) return;
		add(entity, damage * cfg.postureFactor(kind), now);
	}

	/** A perfect parry: the attacker's balance goes at once. */
	public static void breakPosture(LivingEntity entity, long now) {
		if (entity instanceof Player || !CombatConfig.get().posture) return;
		add(entity, max(entity), now);
	}

	/** A plain parry: part of the attacker's balance, as a share of the whole bar. */
	public static void shake(LivingEntity entity, double share, long now) {
		if (entity instanceof Player || !CombatConfig.get().posture) return;
		add(entity, max(entity) * share, now);
	}

	private static void add(LivingEntity entity, double amount, long now) {
		State state = STATES.computeIfAbsent(entity, e -> new State());
		CombatConfig cfg = CombatConfig.get();
		long idle = now - state.lastHit - cfg.postureRegenDelayTicks;
		if (idle > 0) {
			state.value = Math.max(0.0, state.value - idle * cfg.postureRegenPerTick);
		}
		state.lastHit = now;
		if (now < state.staggerUntil) return;
		state.value += amount;
		if (state.value >= max(entity)) {
			state.value = 0;
			state.staggerUntil = now + cfg.staggerTicks;
			entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.staggerTicks, 4, false, false));
			entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, cfg.staggerTicks, 1, false, false));
			CombatFeedback.stagger(entity);
		}
	}

	public static boolean isStaggered(LivingEntity entity, long now) {
		State state = STATES.get(entity);
		return state != null && now < state.staggerUntil;
	}
}
