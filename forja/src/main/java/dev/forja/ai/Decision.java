package dev.forja.ai;

/**
 * One decision of a mob's brain: the simulator's three heads (mover 0 to 8, saltar, usar) and Forja's
 * (tactica, especial 0 to 4, defensa 0 to 2, fintar), and v4's (objeto 0 to 8, furia, golpe_escudo).
 *
 * @param move    0 still; 1 towards the target; 5 away; 2, 3, 4, 6, 7, 8 fixed directions (k − 1) · 45°
 *                from "towards", turning right
 * @param jump    jump (a spider at 2 to 4 blocks leaps at the target)
 * @param use     the basic attack: strike, draw the bow, light the fuse
 * @param tactic  what the executor does (LIBRE: the three heads above drive it)
 * @param special 0 none, k: start special k of the moveset
 * @param defense 0 nothing, 1 shield up, 2 dodge sideways
 * @param feint   drop the blow being wound up (first half of the warning only)
 * @param item    v4's object head (MobItems): 0 nothing, 1 heal, 2 buff, 3 eat, 4 throw a potion, 5 pearl in,
 *                6 pearl away, 7 wind charge, 8 change weapon
 * @param fury    v4: go into a fury (M5)
 * @param bash    v4: the shield bash, lowering a shield that has just blocked (ShieldPlay)
 */
public record Decision(int move, boolean jump, boolean use, Tactic tactic, int special, int defense, boolean feint,
	int item, boolean fury, boolean bash) {
	public static final Decision APPROACH = new Decision(1, false, true, Tactic.ACERCARSE, 0, 0, false);

	/** A decision without v4's heads: no object, no fury, no bash (every brain before v4, and most rule decisions). */
	public Decision(int move, boolean jump, boolean use, Tactic tactic, int special, int defense, boolean feint) {
		this(move, jump, use, tactic, special, defense, feint, 0, false, false);
	}

	public static Decision tactic(Tactic tactic) {
		return new Decision(0, false, false, tactic, 0, 0, false);
	}

	/** The same decision with v4's heads set. */
	public Decision withV4(int item, boolean fury, boolean bash) {
		return new Decision(this.move, this.jump, this.use, this.tactic, this.special, this.defense, this.feint, item, fury, bash);
	}
}
