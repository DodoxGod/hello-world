package dev.forja.ai;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Everything one mob's brain carries between decisions: the network's memory (zeroed when it appears),
 * its own random numbers, what it decided last and when, and the state of the moves it is in the middle
 * of (a blow being wound up, a bow being drawn), which the executor needs and the network sees.
 */
public final class MobMind {
	public final Mob mob;
	public final RandomSource random;
	public float[] memory;
	public Decision decision = Decision.APPROACH;
	public long decidedAt = Long.MIN_VALUE / 2;
	public Player target;
	/** Whether a network is driving this mob right now (vanilla's attack goals stand down). */
	public boolean networked;
	/** Ticks until the basic attack can be used again. */
	public int cooldown;
	/** A blow being wound up: ticks left, and the whole windup. */
	public int windup;
	public int windupTotal;
	/** Ticks spent drawing the bow. */
	public int draw;
	/** The last observation and logits, for recording. */
	public float[] lastObs;
	public float[] lastLogits;
	/** Its special attacks and their state; null for a mob with none. */
	public SpecialRunner specials;
	/** A network given to this one mob, over whatever its family uses (tests, experiments); null for none. */
	public NetBrain override;
	/** Its part in the squad, and the squad's state as it concerns this mob (see {@link Squad}). */
	public SquadRole role = SquadRole.RESERVA;
	public boolean routed;
	public boolean guarding;
	/** Enraged: a duel refused or cheated (it presses harder: one more turn). */
	public boolean enraged;
	/** Whether others in its squad are waiting for a turn. */
	public boolean othersWaiting;
	/** Where it last saw its player, and when (to follow the trail when it loses them). */
	public Vec3 lastSeen;
	public long lastSeenAt = Long.MIN_VALUE / 2;
	/** The cover spot it last found from the player's arrows, and when it looked. */
	public Vec3 cover;
	public long coverAt = Long.MIN_VALUE / 2;
	/** When it last swung at its target (for the relay: strike, then make room). */
	public long lastStrike = Long.MIN_VALUE / 2;
	/** The slot on the ring around the target this mob was given, as an angle; NaN for none. */
	public double ringAngle = Double.NaN;

	MobMind(Mob mob) {
		this.mob = mob;
		this.random = RandomSource.create(mob.getUUID().getLeastSignificantBits() ^ mob.level().getGameTime());
	}

	public void resetMemory(int size) {
		this.memory = new float[size];
	}
}
