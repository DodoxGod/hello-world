package dev.forja.ai;

/**
 * One decision of a mob's brain: the simulator's three heads (mover 0 to 8, saltar, usar) and Forja's
 * (tactica, especial 0 to 4, defensa 0 to 2, fintar).
 *
 * @param move    0 still; 1 towards the target; 5 away; 2, 3, 4, 6, 7, 8 fixed directions (k − 1) · 45°
 *                from "towards", turning right
 * @param jump    jump (a spider at 2 to 4 blocks leaps at the target)
 * @param use     the basic attack: strike, draw the bow, light the fuse
 * @param tactic  what the executor does (LIBRE: the three heads above drive it)
 * @param special 0 none, k: start special k of the moveset
 * @param defense 0 nothing, 1 shield up, 2 dodge sideways
 * @param feint   drop the blow being wound up (first half of the warning only)
 */
public record Decision(int move, boolean jump, boolean use, Tactic tactic, int special, int defense, boolean feint) {
	public static final Decision APPROACH = new Decision(1, false, true, Tactic.ACERCARSE, 0, 0, false);

	public static Decision tactic(Tactic tactic) {
		return new Decision(0, false, false, tactic, 0, 0, false);
	}
}
