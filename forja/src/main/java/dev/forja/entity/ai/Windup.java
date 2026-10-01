package dev.forja.entity.ai;

import net.minecraft.server.level.ServerLevel;

/**
 * The gap between a blow starting and a blow landing.
 *
 * <p>Every special attack in the mod used to hit on the same tick it began. The animation would start,
 * and the damage was already done: the hammer was still over the smith's head while you were being
 * thrown across the room by it. Nothing you could do about any of it, so a fight came down to how much
 * health you had rather than to anything you did.
 *
 * <p>The odd part is that the timing was never missing. It was drawn into the animations and nobody
 * read it back out. The smith's {@code slam} puts his arm up at seven ticks, <b>holds it there for
 * eight more</b>, and brings it down at eighteen — an anticipation, a hold and a contact, which is
 * exactly what a telegraph is. This class is the thing that waits for it: the mob commits, the cue
 * shows where it is going to land, and the blow arrives when the animation says it arrives.
 *
 * <p>A mob that is charging is not steering, which is the other half of the deal. Committing is what
 * makes the move dodgeable, and it is also what makes landing one feel earned.
 */
public final class Windup {
	/** Drawn every tick of the charge, so the attack can be read and stepped out of. */
	public interface Cue {
		/**
		 * @param left how many ticks are still to run
		 * @param total the whole charge, so a cue can grow with it
		 */
		void show(ServerLevel level, int left, int total);
	}

	/** What actually happens when the charge runs out. */
	public interface Blow {
		void land(ServerLevel level);
	}

	private int left;
	private int total;
	private Cue cue;
	private Blow blow;
	private dev.forja.entity.Shockwave warning;

	/**
	 * Commit to a blow that lands {@code ticks} from now.
	 *
	 * <p>Callers time this off their own animation rather than off a number that felt about right,
	 * because the two drifting apart is what the class exists to stop.
	 */
	public void start(int ticks, Cue cue, Blow blow) {
		this.dropWarning();
		this.left = Math.max(1, ticks);
		this.total = this.left;
		this.cue = cue;
		this.blow = blow;
	}

	/**
	 * Puts the circle on the floor that goes with this charge.
	 *
	 * <p>Kept here rather than by each mob because its life is the charge's life: it has to go when the
	 * charge is dropped, when another one starts over it, and when the blow lands without using it.
	 * Eight mobs each remembering to do that in three places is twenty-four chances to leave a circle
	 * glowing on the floor for a blow that is never coming.
	 */
	public void warn(dev.forja.entity.Shockwave warning) {
		this.dropWarning();
		this.warning = warning;
	}

	/** The circle, while the charge runs: for a mark that has to be moved along with whoever is charging. */
	public dev.forja.entity.Shockwave warning() {
		return this.warning;
	}

	/**
	 * Hands the circle over to the blow, which is the one thing that may turn it into a ring.
	 *
	 * @return the warning if it is still in the world, or null — and then the blow draws its own ring
	 */
	public dev.forja.entity.Shockwave takeWarning() {
		dev.forja.entity.Shockwave taken = this.warning;
		this.warning = null;
		return taken != null && taken.isAlive() ? taken : null;
	}

	private void dropWarning() {
		if (this.warning != null) {
			this.warning.discard();
			this.warning = null;
		}
	}

	/** Whether a blow is on its way. */
	public boolean charging() {
		return this.left > 0;
	}

	/** How far through the charge we are, from 0 at the start to 1 as it lands. */
	public float progress() {
		return this.total == 0 ? 0.0F : (float) (this.total - this.left) / this.total;
	}

	/** Drops a charge on the floor without firing it — for a mob that dies or loses its target. */
	public void cancel() {
		this.dropWarning();
		this.left = 0;
		this.cue = null;
		this.blow = null;
	}

	/**
	 * Runs one tick of the charge.
	 *
	 * @return true while the mob is still winding up and should be doing nothing else. The tick the
	 *     blow lands returns false, so the mob goes straight back to its ordinary business.
	 */
	public boolean tick(ServerLevel level) {
		if (this.left <= 0) {
			return false;
		}
		this.left--;
		if (this.left > 0) {
			if (this.cue != null) {
				this.cue.show(level, this.left, this.total);
			}
			return true;
		}
		Blow landing = this.blow;
		dev.forja.entity.Shockwave untaken = this.warning;
		this.cue = null;
		this.blow = null;
		if (landing != null) {
			landing.land(level);
		}
		// A blow that did not take its warning leaves it to be cleared up here — unless the blow went
		// straight into another charge with a warning of its own, which is not ours to clear.
		if (this.warning == untaken) {
			this.dropWarning();
		}
		return false;
	}
}
