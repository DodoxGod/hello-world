package dev.forja.ai;

/**
 * One decision of a blaze network (red_blaze_v1, docs/red_blaze_contrato.json "cabezas"): its five heads, each sampled
 * on its own. {@link BlazePilot} carries it out every tick until the next one.
 *
 * @param move     mover, as v3's: 0 no push, k = 1..8 the direction (k − 1) · 45° from "towards the player", turning right
 * @param vertical 0 hold its height, 1 rise, 2 descend
 * @param use      start the burst (charge, then three fireballs) if it can start now
 * @param lead     aim lead: 0, 0.5, 1 or 1.5 × the player's flat speed × the fireball's flight ticks ({@link #LEADS})
 * @param retreat  retirarse: overrides move and vertical with "away from the player" and "rise"
 */
public record BlazeDecision(int move, int vertical, boolean use, int lead, boolean retreat) {
	public static final int HOLD = 0;
	public static final int RISE = 1;
	public static final int DESCEND = 2;
	/** The aim lead factors of the adelanto head, in its order. */
	public static final double[] LEADS = {0.0, 0.5, 1.0, 1.5};
	/** mover's "away from the player", what retirarse flies. */
	public static final int AWAY = 5;

	/** The lead factor this decision fires with. */
	public double leadFactor() {
		return LEADS[Math.max(0, Math.min(LEADS.length - 1, this.lead))];
	}

	/** The same decision in the ground mobs' terms, for what counts and shows decisions (AiStats, AiDebug, TacticGoal.canUse). */
	public Decision asDecision() {
		Tactic shown = this.retreat ? Tactic.RETIRARSE : Tactic.LIBRE;
		return new Decision(this.retreat ? AWAY : this.move, false, this.use, shown, 0, 0, false);
	}
}
