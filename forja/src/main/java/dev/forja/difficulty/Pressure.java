package dev.forja.difficulty;

import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.combat.CombatAnim;
import dev.forja.combat.CombatConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Pressure: a player being hit again and again has no time to set their armor. It starts at 0, every blow
 * they take adds a step (about 9 blows from none to the cap, and a blow caught on a shield half a step), and
 * it is armor penetration for the next blows, chained with the weapon's and the attacker's rank; 3 s without
 * being hit and it drains away, from the cap to nothing in about 4.5 s. Being surrounded or caught in a chain
 * of blows gets through any armor, which is what makes a crowd dangerous to someone in the best plate, but
 * one blow alone never takes much of it (Andy, 2026-09-30: "tiene que existir un balance").
 *
 * <pre>
 * al recibir un golpe: p = min(máx, p(ahora) + porGolpe)            (0,065 por golpe, máx 0,60)
 * p(t) = max(0, p − max(0, t − último − espera) · vaciado)          (espera 60 ticks, vaciado 0,0067/tick)
 * base  = min(0,30, 1 − (1 − arma)(1 − rango))
 * total = min(0,60, 1 − (1 − base)(1 − p))
 * </pre>
 */
public final class Pressure {
	private static final class State {
		double value;
		long lastHit;
	}

	private static final Map<Player, State> STATES = new WeakHashMap<>();

	private Pressure() {
	}

	/** Pressure on this player right now, 0 to the cap. */
	public static double of(Player player) {
		State state = STATES.get(player);
		if (state == null) {
			return 0.0;
		}
		CombatConfig cfg = CombatConfig.get();
		long idle = player.level().getGameTime() - state.lastHit - cfg.pressureDelayTicks;
		return idle <= 0 ? state.value : Math.max(0.0, state.value - idle * cfg.pressureDrainPerTick);
	}

	/** Takes all pressure off a player at once: for the balance probe, which hits the same player fresh again and again. */
	public static void forget(Player player) {
		STATES.remove(player);
	}

	/** When a blow last reached this player, in game time; far in the past if never. */
	public static long lastHit(Player player) {
		State state = STATES.get(player);
		return state == null ? Long.MIN_VALUE / 2 : state.lastHit;
	}

	/** A blow reached the player. */
	public static void onHit(Player player) {
		add(player, CombatConfig.get().pressurePerHit);
	}

	/** A blow caught on a shield or parried: no time lost to it, but still some (Andy, 2026-09-29). */
	public static void onBlocked(Player player) {
		CombatConfig cfg = CombatConfig.get();
		add(player, cfg.pressurePerHit * cfg.pressureBlockedShare);
	}

	private static void add(Player player, double amount) {
		// Pressure is part of armor penetration, which a level below Normal leaves out (Ladder.penetration).
		if (!Ladder.current().penetration) {
			return;
		}
		CombatConfig cfg = CombatConfig.get();
		double now = of(player);
		State state = STATES.computeIfAbsent(player, p -> new State());
		state.value = Math.min(cfg.pressureMax, now + amount);
		state.lastHit = player.level().getGameTime();
		if (player instanceof ServerPlayer serverPlayer) {
			CombatAnim.sendTo(serverPlayer, serverPlayer, CombatAnim.Kind.PRESSURE, cfg.pressureDelayTicks,
				(float) state.value, (float) cfg.pressureDrainPerTick);
		}
	}

	/** How much of the armor still holds, 1 with no pressure and 0 at the cap: what the HUD shows. */
	public static double integrity(double pressure, double max) {
		return max <= 0.0 ? 1.0 : Math.max(0.0, Math.min(1.0, 1.0 - pressure / max));
	}

	/** The penetration a blow on this player gets, from the weapon's own, the attacker's rank and the pressure on them. */
	public static double penetration(Player player, double weaponPenetration, double rankPenetration) {
		return total(weaponPenetration, rankPenetration, of(player), CombatConfig.get());
	}

	/**
	 * The three chained: the weapon's and the rank's are capped together first (one blow alone never takes
	 * more than {@code penetrationBaseMax} of the armor), then the pressure on top, and the whole is capped
	 * at {@code penetrationTotalMax}.
	 */
	public static double total(double weapon, double rank, double pressure, CombatConfig cfg) {
		double base = Math.min(cfg.penetrationBaseMax, chain(weapon, rank));
		return Math.min(cfg.penetrationTotalMax, chain(base, pressure));
	}

	/** {@code 1 − (1 − a)(1 − b)}, each clamped to 0..1. */
	public static double chain(double a, double b) {
		return 1.0 - (1.0 - Math.max(0.0, Math.min(1.0, a))) * (1.0 - Math.max(0.0, Math.min(1.0, b)));
	}
}
