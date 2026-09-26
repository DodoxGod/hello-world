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
		/** Staggers in a row: each one is shorter and raises the bar; one fades every so often. */
		int staggers;
		long lastStagger;
	}

	private Posture() {
	}

	/**
	 * The size of the bar: health and a base, times the mob's threat and the difficulty, and raised
	 * further by every recent stagger (see {@link #resistance}).
	 */
	public static double max(LivingEntity entity) {
		CombatConfig cfg = CombatConfig.get();
		double base = entity.getMaxHealth() * cfg.postureHealthFactor + cfg.postureBase;
		double scale = dev.forja.difficulty.Threat.of(entity).posture * dev.forja.difficulty.ForjaDifficulty.current().posture
			* dev.forja.ai.ForjaTraits.postureMax(entity);
		State state = STATES.get(entity);
		int staggers = state == null ? 0 : recentStaggers(state, entity.level().getGameTime());
		return base * scale * (1.0 + cfg.staggerRepeatPosture * staggers);
	}

	private static int recentStaggers(State state, long now) {
		int fade = Math.max(1, CombatConfig.get().staggerResistanceFadeTicks);
		return (int) Math.max(0, state.staggers - Math.max(0L, now - state.lastStagger) / fade);
	}

	/** How resistant to staggering this mob has become, 0 (fresh) to near 1: 1 − 0,7^staggers. */
	public static double resistance(LivingEntity entity) {
		State state = STATES.get(entity);
		if (state == null) {
			return 0.0;
		}
		return 1.0 - Math.pow(CombatConfig.get().staggerRepeatDuration, recentStaggers(state, entity.level().getGameTime()));
	}

	/** How full the bar is right now, 0 to 1, draining as it does between blows. */
	public static double fill(LivingEntity entity) {
		State state = STATES.get(entity);
		if (state == null) {
			return 0.0;
		}
		CombatConfig cfg = CombatConfig.get();
		long idle = entity.level().getGameTime() - state.lastHit - cfg.postureRegenDelayTicks;
		double value = idle > 0 ? Math.max(0.0, state.value - idle * cfg.postureRegenPerTick) : state.value;
		return Math.min(1.0, value / max(entity));
	}

	/** Ticks of stagger left, 0 when not staggered. */
	public static int staggerLeft(LivingEntity entity) {
		State state = STATES.get(entity);
		return state == null ? 0 : (int) Math.max(0L, state.staggerUntil - entity.level().getGameTime());
	}

	public static void onHit(LivingEntity entity, DamageKind kind, float damage, long now) {
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.posture || entity instanceof Player) return;
		add(entity, damage * cfg.postureFactor(kind) * dev.forja.ai.ForjaTraits.postureFactor(entity, kind, now), now);
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
			// Each stagger in a row is shorter than the last, and the bar grows for the next one.
			int recent = recentStaggers(state, now);
			int ticks = Math.max(cfg.staggerMinTicks, (int) Math.round(cfg.staggerTicks * Math.pow(cfg.staggerRepeatDuration, recent)));
			state.value = 0;
			state.staggerUntil = now + ticks;
			state.staggers = recent + 1;
			state.lastStagger = now;
			entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 4, false, false));
			entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false));
			CombatFeedback.stagger(entity, ticks);
		}
		// The bar is drawn on the client, which drains it on its own between blows at the same pace.
		double max = max(entity);
		CombatAnim.broadcast(entity, CombatAnim.Kind.POSTURE, cfg.postureRegenDelayTicks,
			(float) (state.value / max), (float) (cfg.postureRegenPerTick / max));
	}

	/** A finisher ends the stagger it punished: the next one has to be earned again. */
	public static void endStagger(LivingEntity entity) {
		State state = STATES.get(entity);
		if (state != null) {
			state.staggerUntil = 0;
			state.value = 0;
		}
		entity.removeEffect(MobEffects.SLOWNESS);
		entity.removeEffect(MobEffects.WEAKNESS);
	}

	public static boolean isStaggered(LivingEntity entity, long now) {
		State state = STATES.get(entity);
		return state != null && now < state.staggerUntil;
	}
}
