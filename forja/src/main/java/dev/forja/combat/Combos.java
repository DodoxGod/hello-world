package dev.forja.combat;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.player.Player;

/**
 * Rhythm over mashing: blows at (nearly) full strength that follow each other quickly chain, and the
 * third of them lands heavier and hits balance harder. A weak, rushed swing breaks the chain, so
 * spamming the button is the one way never to get there.
 */
public final class Combos {
	/** Hits at least this strong count towards a combo. */
	public static final float MIN_STRENGTH = 0.9F;

	private static final class State {
		int step;
		long lastHit = Long.MIN_VALUE / 2;
		boolean finishing;
	}

	private static final Map<Player, State> STATES = new WeakHashMap<>();

	private Combos() {
	}

	/** A swing that is about to land; called before the damage is dealt. */
	public static void onAttack(Player player, float strength) {
		CombatConfig cfg = CombatConfig.get();
		State state = STATES.computeIfAbsent(player, p -> new State());
		long now = player.level().getGameTime();
		if (strength < MIN_STRENGTH) {
			state.step = 0;
			return;
		}
		state.step = now - state.lastHit <= cfg.comboWindowTicks ? state.step + 1 : 1;
		state.lastHit = now;
		if (state.step == 2) {
			CombatAnim.broadcast(player, CombatAnim.Kind.COMBO, 2);
		} else if (state.step >= 3) {
			state.finishing = true;
			state.step = 0;
			CombatAnim.broadcast(player, CombatAnim.Kind.COMBO, 3);
		}
	}

	/** Whether the blow landing now is the third of a combo. Reading it clears it. */
	public static boolean consumeFinisher(Player player) {
		State state = STATES.get(player);
		if (state == null || !state.finishing) {
			return false;
		}
		state.finishing = false;
		return true;
	}

	/** The step the player's combo is on (0 to 2). */
	public static int step(Player player) {
		State state = STATES.get(player);
		if (state == null || player.level().getGameTime() - state.lastHit > CombatConfig.get().comboWindowTicks) {
			return 0;
		}
		return state.step;
	}
}
