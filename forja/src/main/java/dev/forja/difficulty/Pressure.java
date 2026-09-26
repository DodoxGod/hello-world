package dev.forja.difficulty;

import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.combat.CombatAnim;
import dev.forja.combat.CombatConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Pressure: a player being hit again and again has no time to set their armor. Every blow they take adds
 * armor penetration to the next ones, on top of the weapon's own, up to a total cap; a few seconds without
 * being hit and it drains away. Being surrounded or caught in a chain of blows gets through any armor,
 * which is what makes a crowd dangerous to someone in the best plate.
 *
 * <pre>
 * al recibir un golpe: p = min(máx, p(ahora) + porGolpe)
 * p(t) = max(0, p − max(0, t − último − espera) · vaciado)
 * penetración final = max(pen_arma, min(máx, pen_arma + p))
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

	/** When a blow last reached this player, in game time; far in the past if never. */
	public static long lastHit(Player player) {
		State state = STATES.get(player);
		return state == null ? Long.MIN_VALUE / 2 : state.lastHit;
	}

	/** A blow reached the player. */
	public static void onHit(Player player) {
		CombatConfig cfg = CombatConfig.get();
		double now = of(player);
		State state = STATES.computeIfAbsent(player, p -> new State());
		state.value = Math.min(cfg.pressureMax, now + cfg.pressurePerHit);
		state.lastHit = player.level().getGameTime();
		if (player instanceof ServerPlayer serverPlayer) {
			CombatAnim.sendTo(serverPlayer, serverPlayer, CombatAnim.Kind.PRESSURE, cfg.pressureDelayTicks,
				(float) state.value, (float) cfg.pressureDrainPerTick);
		}
	}

	/** The penetration a blow on this player gets, from the weapon's own and the pressure on them. */
	public static double penetration(Player player, double weaponPenetration) {
		double pressure = of(player);
		if (pressure <= 0.0) {
			return weaponPenetration;
		}
		return Math.max(weaponPenetration, Math.min(CombatConfig.get().pressureMax, weaponPenetration + pressure));
	}
}
