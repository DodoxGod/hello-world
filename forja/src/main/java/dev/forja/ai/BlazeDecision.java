package dev.forja.ai;

/**
 * One decision of a blaze network (red_blaze_v1, docs/red_blaze_contrato.json "cabezas"; Andy, 2026-09-29): its five
 * heads, each sampled on its own. {@link BlazePilot} carries it out.
 *
 * @param move     mover, as v3's: 0 still, 1 towards the player, 5 away, the others (k − 1) · 45° from "towards"
 * @param vertical 0 hold its height, 1 rise, 2 descend
 * @param fire     0 wait (a charge is kept), 1 charge, 2 fire the burst
 * @param range    the distance band RODEAR_ALTO and ESPERAR keep: 0 middle (8–12), 1 near (5–8), 2 far (12–15)
 * @param tactic   what it is doing
 */
public record BlazeDecision(int move, int vertical, int fire, int range, Plan tactic) {
	public static final int HOLD = 0;
	public static final int RISE = 1;
	public static final int DESCEND = 2;
	public static final int WAIT = 0;
	public static final int CHARGE = 1;
	public static final int FIRE = 2;
	public static final int MIDDLE = 0;
	public static final int NEAR = 1;
	public static final int FAR = 2;

	/** The tactic head, in the contract's order (tacticas). ACOSAR, the first, is never masked. */
	public enum Plan {
		/** Presses in: the move and vertical heads drive it directly. */
		ACOSAR(Tactic.ACERCARSE),
		/** Circles the player at the chosen distance, a few blocks above them; the vertical head does not count. */
		RODEAR_ALTO(Tactic.RODEAR),
		/** Backs straight away from the player. */
		RETIRARSE(Tactic.RETIRARSE),
		/** Holds within the chosen distance band. */
		ESPERAR(Tactic.ESPERAR);

		/** The ground mobs' tactic it is counted and shown as (AiStats, AiDebug). */
		public final Tactic shown;

		Plan(Tactic shown) {
			this.shown = shown;
		}
	}

	/** The same decision as the ground mobs' kind, for what counts and shows decisions (AiStats, AiDebug, TacticGoal.canUse). */
	public Decision asDecision() {
		return new Decision(this.move, false, this.fire == FIRE, this.tactic.shown, 0, 0, false);
	}
}
