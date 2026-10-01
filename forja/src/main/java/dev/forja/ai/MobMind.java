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
	/** The player those two belong to: the one it was after when it last thought (boredom counts for them). */
	public Player hunted;
	/** The cover spot it last found from the player's arrows, and when it looked. */
	public Vec3 cover;
	public long coverAt = Long.MIN_VALUE / 2;
	/** When it last swung at its target (for the relay: strike, then make room). */
	public long lastStrike = Long.MIN_VALUE / 2;
	/**
	 * A warned blow of vanilla's melee goal is under way (MeleeAttackGoalMixin): the rules keep it going in until it
	 * lands or misses (Andy: a warned blow is a commitment the player reads and answers).
	 */
	public boolean warning;
	/**
	 * When the wait after its last warned melee blow (landed, missed or feinted) is over, whichever path struck it:
	 * vanilla's melee goal (MeleeAttackGoalMixin) or TacticGoal. One wait for both, counted in game time: before
	 * (2026-09-30), each path had its own countdown, and a blow by one was followed at once by a warning from the other.
	 */
	public long nextBlowAt = Long.MIN_VALUE / 2;
	/** Its breath for running (ai/MobSprint), whether it wants to run and whether it is running. */
	public float stamina = MobSprint.MAX;
	public boolean wantsRun;
	public boolean running;
	/** Running in surround mode (MobSprint.rodeo): going round to its slot while the player backs away. */
	public boolean rodeo;
	/** Ticks it went to its ring slot with no path, steering straight at it instead (TacticGoal.toRing). */
	public int pathless;
	/** Ran itself out: it runs again only once its breath is partly back. */
	public boolean winded;
	public long lastRun = Long.MIN_VALUE / 2;
	/** The slot on the ring around the target this mob was given, as an angle; NaN for none. */
	public double ringAngle = Double.NaN;
	/** How far out that slot is: the ring widens when a crowd would stand too close together on it. */
	public double ringRadius = TacticGoal.RING_RADIUS;
	/**
	 * The slot as the Squad last handed it out, before any tactic moves ringAngle (closing round a staggered ally):
	 * its angle, the size of the ring it was cut for, and whose ring. A ring of the same size keeps its slots.
	 */
	/** The yo_arma_alcance its network reads (NetBrain.reachVersion); 1 without a network. */
	public int reachVersion = 1;
	public double slotAngle = Double.NaN;
	public int slotN;
	public net.minecraft.world.entity.player.Player slotOf;
	/** When the Squad gave it the slot it holds (a new angle or a new player), for v4's hueco_estable/100. */
	public long slotSince = Long.MIN_VALUE / 2;
	/**
	 * Since when it has gone without a turn while others wait (v4's turno_espera/40): kept up to date by each v4
	 * observation (ObsV4), which is the only reader.
	 */
	public long waitingSince = Long.MIN_VALUE / 2;
	/**
	 * A blaze driven by its own network (red_blaze_v1, docs/red_blaze_contrato.json): its last decision, null whenever
	 * anything else drives it, and the state of its burst and flight (BlazePilot), made the first time it is needed.
	 */
	public BlazeDecision blaze;
	public BlazePilot.State blazeState;

	// --- v4 (docs/red_mob_v4_diseno.md): the state of the new mechanics, the same for a network and the rules

	/** The family file whose network it last ran, so a change of family (a skeleton that took a blade) starts its memory afresh. */
	public String family;
	/** Its shield (ShieldPlay): when it last blocked or parried, when the bash is ready, the bash's warning and target. */
	public long lastBlockAt = Long.MIN_VALUE / 2;
	public long bashReadyAt;
	public int bashWindup;
	public Player bashTarget;
	public long lastBash = Long.MIN_VALUE / 2;
	/** The object under way (MobItems): which, ticks left, at whom, what it shows, and when the next may start. */
	public int itemAction;
	public int itemTicks;
	public long itemReadyAt;
	public Player itemTarget;
	public net.minecraft.world.item.ItemStack itemShown;
	public boolean showing;
	public float shownChance;
	/** A pearl of its own in flight, to hurt it when it lands. */
	public net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl pearl;
	/** When it last changed weapon (a pick-up or a swap). */
	public long swapAt = Long.MIN_VALUE / 2;
	/** RECOGER (GroundItems): what it is going for, and the ticks spent picking it up. */
	public net.minecraft.world.entity.item.ItemEntity pickupItem;
	public int pickupTicks;
	/** APAGAR_LUZ (TorchGoal): the torch it is going for, the ticks spent striking it, and when it set off. */
	public net.minecraft.core.BlockPos lightTarget;
	public int lightTicks;
	public long lightSince = Long.MIN_VALUE / 2;
	/** ASEDIAR (Siege): its spot in the siege ring, and when it was chosen. */
	public Vec3 siegeSpot;
	public long siegeAt = Long.MIN_VALUE / 2;
	/** Perception (M4): when it last perceived its player, and what it had of them then (Perception). */
	public long perceivedAt = Long.MIN_VALUE / 2;
	public Perception.Snapshot snapshot;
	/** Hearing (M4): where and when it last heard its player, its two latest sounds, and up to when it has listened. */
	public Vec3 lastHeard;
	public long lastHeardAt = Long.MIN_VALUE / 2;
	public Hearing.Sound sound0;
	public Hearing.Sound sound1;
	public long heardUpTo = Long.MIN_VALUE / 2;
	/** BUSCAR (M4): since when, which stage, the fan of points ahead and the one it is going to, for which estimate. */
	public long searchSince = Long.MIN_VALUE / 2;
	public int searchStage;
	public Vec3[] searchPoints;
	public int searchIndex;
	public long searchFor = Long.MIN_VALUE / 2;
	/** EMBOSCAR (M4): its hiding spot and when it was worked out, and for whom; since when it lies in wait. */
	public Ambush.Spot hideSpot;
	public long hideAt = Long.MIN_VALUE / 2;
	public Player hideFor;
	public Vec3 hidePlayerAt;
	public long ambushSince = Long.MIN_VALUE / 2;
	/** oculto_r3_* (M4), kept for Ambush.PERIOD ticks. */
	public boolean[] occluded;
	public long occludedAt = Long.MIN_VALUE / 2;
	public Player occludedFor;
	public Vec3 occludedPlayerAt;
	/** The captain's orders (M5): its post (Captain.FRENTE..RESERVA, -1 for none) and the point of it. */
	public int post = -1;
	public Vec3 postPoint;
	/** Captain 2: one of its captain's escorts this pass (Captain.protect): the rules take it to its point without a turn. */
	public boolean escort;
	/** When a player's blows landed on it, the last {@link #HITS_KEPT} ticks (capitan_golpeado/3 of a v2 captain). */
	public final java.util.ArrayDeque<Long> playerHits = new java.util.ArrayDeque<>();
	public static final int HITS_KEPT = 100;
	/** Since when it has been in RETIRARSE without a break (retirada_ticks, and leaving the fight at 100). */
	public long retreatSince = Long.MIN_VALUE / 2;
	/** The world's memory (M6): the damage it has done to its player in this life, and how it fought (by WorldMemory's styles). */
	public float lifeDamage;
	public final int[] styleTicks = new int[5];
	/** Whether it has used its fury in this fight (one each). */
	public boolean furyUsed;
	/** Its fury (M5): until when it lasts, and until when it is spent after. */
	public long furyUntil = Long.MIN_VALUE / 2;
	public long exhaustedUntil = Long.MIN_VALUE / 2;

	/** Per-tick caches of v4's dearer questions, asked by the observation and again by the mask. */
	public long usefulAt = Long.MIN_VALUE / 2;
	public java.util.List<GroundItems.Seen> useful;
	public long throwAt = Long.MIN_VALUE / 2;
	public Player throwFor;
	public boolean throwOk;
	public Vec3 pearlSpot;

	/** When one of v4's executors (RECOGER, ASEDIAR, APAGAR_LUZ) last asked for a path. */
	public long pathAt = Long.MIN_VALUE / 2;

	/**
	 * Whether a new path may be asked for now, and if so notes it: at most every 10 ticks. The pathfinder is the dear
	 * part, and asked "whenever the last path is done", a mob whose path ended short asked every tick.
	 */
	public boolean pathDue(long now) {
		if (now - this.pathAt >= 10 || now < this.pathAt) {
			this.pathAt = now;
			return true;
		}
		return false;
	}

	/** Whether it is in a fury now. */
	public boolean furyActive(long now) {
		return now < this.furyUntil;
	}

	MobMind(Mob mob) {
		this.mob = mob;
		this.random = RandomSource.create(mob.getUUID().getLeastSignificantBits() ^ mob.level().getGameTime());
	}

	public void resetMemory(int size) {
		this.memory = new float[size];
	}
}
