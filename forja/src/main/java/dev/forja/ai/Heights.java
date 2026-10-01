package dev.forja.ai;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The player up high (docs/red_mob_v4_diseno.md §2.6, block A, and §4.6): on a pillar, in a tower, near an edge, and
 * for how long. The part that is the same for every mob is worked out once per player at most every
 * {@link #PERIOD} ticks, when a mob first asks, and kept; the rest (the fall a push towards the player would give, whether
 * this mob's blow gets there, whether it sees them) is per mob and cheap.
 *
 * <p>Heights are feet heights, in blocks. The "ground" of a column is the top of its highest collision box at or below
 * the height asked from, looking down at most {@link #DEPTH} blocks (deeper is as good as a bottomless drop).
 */
public final class Heights {
	public static final int PERIOD = 10;
	/** How far down a column is looked at. */
	public static final int DEPTH = 16;
	/** The ring round the player the ground is the median of (jug_sobre_suelo). */
	public static final double RING = 2.5;
	/** The ring round the player a pillar's drops are looked for at (jug_en_pilar), and how many of 8 must drop. */
	public static final double PILLAR_RING = 1.5;
	public static final int PILLAR_SIDES = 6;
	/** A drop this deep counts (pillar, edge). */
	public static final double DROP = 2.5;
	/** A tower: this high, and a block at chest height in this many of the 8 directions, 1 to 3 blocks off. */
	public static final double TOWER_HEIGHT = 2.5;
	public static final int TOWER_SIDES = 5;
	/** How far an edge is looked for, and in what steps (jug_borde/2 reads at most this, over 2). */
	public static final double EDGE_RANGE = 4.0;
	public static final double EDGE_STEP = 0.5;
	/** High enough to count as "up" (jug_arriba, jug_tira_desde_arriba, veo_jug_arriba, the siege). */
	public static final double UP = 2.0;
	/** A shot from above counts for this long. */
	public static final int SHOT_MEMORY = 40;
	/** How far a push towards the player is taken to carry them (jug_caida_empuje). */
	public static final double PUSH = 1.5;

	/** What is known about one player's height, as of {@link #scannedAt}. */
	public static final class State {
		public long scannedAt = Long.MIN_VALUE / 2;
		/** Feet over the median ground of the ring round them (jug_sobre_suelo, in blocks). */
		public double overGround;
		public double groundY;
		public boolean pillar;
		public boolean tower;
		/** The nearest edge they would fall off (at most EDGE_RANGE). */
		public double edge = EDGE_RANGE;
		/** A continuous wall up to their height within 3 blocks: something a spider climbs. */
		public boolean climbable;
		/** Since when they have been up (overGround ≥ UP), or MIN for not up. */
		public long upSince = Long.MIN_VALUE / 2;
		/** When they last shot while up. */
		public long shotUpAt = Long.MIN_VALUE / 2;
	}

	private static final Map<Player, State> STATES = new WeakHashMap<>();
	private static final double[][] DIRS = new double[8][];

	static {
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4.0;
			DIRS[k] = new double[] {Math.cos(a), Math.sin(a)};
		}
	}

	private Heights() {
	}

	/** The player's state, scanned again if it is older than {@link #PERIOD} ticks. */
	public static State of(Player player) {
		long now = player.level().getGameTime();
		State state = STATES.computeIfAbsent(player, p -> new State());
		if (now - state.scannedAt >= PERIOD || now < state.scannedAt) {
			scan(player, state, now);
		}
		return state;
	}

	/** Forgets what was known (tests). */
	public static void forget(Player player) {
		STATES.remove(player);
	}

	/** Whether the player is up (overGround ≥ UP) or in a tower: ASEDIAR's mask. */
	public static boolean besieged(Player player) {
		State s = of(player);
		return s.overGround >= UP || s.tower;
	}

	private static void scan(Player player, State s, long now) {
		Level level = player.level();
		double feet = player.getY();
		double x = player.getX();
		double z = player.getZ();
		int feetBlock = (int) Math.floor(feet + 1.0E-3);
		// The ring's ground, leaving out the points in a wall at the height of their feet (a tower's battlements, a
		// trench's sides): those are not ground they stand over. All walls: they stand on what is under them.
		double[] ring = new double[8];
		int n = 0;
		for (int k = 0; k < 8; k++) {
			double px = x + DIRS[k][0] * RING;
			double pz = z + DIRS[k][1] * RING;
			if (!solid(level, px, feetBlock, pz)) {
				ring[n++] = top(level, px, pz, feetBlock + 1);
			}
		}
		if (n == 0) {
			s.groundY = feet;
		} else {
			java.util.Arrays.sort(ring, 0, n);
			s.groundY = n % 2 == 1 ? ring[n / 2] : (ring[n / 2 - 1] + ring[n / 2]) / 2.0;
		}
		s.overGround = feet - s.groundY;
		int drops = 0;
		int walls = 0;
		for (int k = 0; k < 8; k++) {
			double px = x + DIRS[k][0] * PILLAR_RING;
			double pz = z + DIRS[k][1] * PILLAR_RING;
			drops += feet - top(level, px, pz, feetBlock) >= DROP ? 1 : 0;
			boolean wall = false;
			for (int r = 1; r <= 3 && !wall; r++) {
				wall = solid(level, x + DIRS[k][0] * r, feetBlock + 1, z + DIRS[k][1] * r);
			}
			walls += wall ? 1 : 0;
		}
		s.pillar = drops >= PILLAR_SIDES;
		s.tower = s.overGround >= TOWER_HEIGHT && walls >= TOWER_SIDES;
		double edge = EDGE_RANGE;
		for (int k = 0; k < 8; k++) {
			for (double d = EDGE_STEP; d <= EDGE_RANGE && d < edge; d += EDGE_STEP) {
				double px = x + DIRS[k][0] * d;
				double pz = z + DIRS[k][1] * d;
				if (solid(level, px, feetBlock, pz)) {
					break;
				}
				if (feet - top(level, px, pz, feetBlock) >= DROP) {
					edge = d;
					break;
				}
			}
		}
		s.edge = edge;
		s.climbable = s.overGround >= 1.0 && climbable(level, x, z, feetBlock, s.groundY);
		if (s.overGround >= UP) {
			if (s.upSince <= Long.MIN_VALUE / 4) {
				s.upSince = now;
			}
			// A shot from up there since the last look: the player's own projectiles, a few ticks old.
			for (Projectile shot : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(6.0),
				p -> p.getOwner() == player && p.tickCount <= PERIOD + 1)) {
				s.shotUpAt = now;
				break;
			}
		} else {
			s.upSince = Long.MIN_VALUE / 2;
		}
		s.scannedAt = now;
	}

	/** Ticks the player has been up, 0 when not. */
	public static long upTicks(State s, long now) {
		return s.upSince <= Long.MIN_VALUE / 4 ? 0 : Math.max(0, now - s.upSince);
	}

	/**
	 * A wall a spider could climb to the player: their own supporting column, or one within 3 blocks of them in the 8
	 * directions, solid from their feet down to the ground with no gap of 2 or more.
	 */
	private static boolean climbable(Level level, double x, double z, int feetBlock, double groundY) {
		if (column(level, x, z, feetBlock - 1, groundY)) {
			return true;
		}
		for (double[] dir : DIRS) {
			for (int r = 1; r <= 3; r++) {
				if (column(level, x + dir[0] * r, z + dir[1] * r, feetBlock, groundY)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean column(Level level, double x, double z, int from, double groundY) {
		if (!solid(level, x, from, z)) {
			return false;
		}
		int gap = 0;
		for (int y = from; y >= Math.floor(groundY); y--) {
			if (solid(level, x, y, z)) {
				gap = 0;
			} else if (++gap >= 2) {
				return false;
			}
		}
		return true;
	}

	/** Whether the block at (x, y, z) has a collision box. */
	static boolean solid(Level level, double x, int y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * The ground of a column: the top of the highest collision box from block {@code fromY} down, looking at most
	 * {@link #DEPTH} blocks; one below the last block looked at for nothing found.
	 */
	public static double top(Level level, double x, double z, int fromY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		for (int y = fromY; y >= fromY - DEPTH; y--) {
			pos.set(bx, y, bz);
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				return y + shape.max(net.minecraft.core.Direction.Axis.Y);
			}
		}
		return fromY - DEPTH - 1;
	}

	// ---------------------------------------------------------------- per mob

	/** How far the player would fall if pushed {@link #PUSH} blocks away from the mob (jug_caida_empuje, blocks). */
	public static double pushFall(Mob mob, Player player) {
		double dx = player.getX() - mob.getX();
		double dz = player.getZ() - mob.getZ();
		double d = Math.hypot(dx, dz);
		if (d < 1.0E-4) {
			return 0.0;
		}
		double px = player.getX() + dx / d * PUSH;
		double pz = player.getZ() + dz / d * PUSH;
		int feetBlock = (int) Math.floor(player.getY() + 1.0E-3);
		if (solid(player.level(), px, feetBlock, pz)) {
			return 0.0;
		}
		return Math.max(0.0, player.getY() - top(player.level(), px, pz, feetBlock));
	}

	/**
	 * Whether this mob's own attack gets to the player from where it stands (jug_alcanzable): a body's blow needs their
	 * feet under the top of its head and over its own feet less the player's height (vanilla's melee box is only widened
	 * sideways); an archer needs to see them; a creeper's blast reaches 3 up.
	 */
	public static boolean reachable(Mob mob, Player player) {
		double dy = player.getY() - mob.getY();
		return switch (MobFamily.executor(mob)) {
			case ARQUERO, BLAZE -> ObsM1.sees(mob, player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ());
			case CREEPER -> dy <= 3.0;
			default -> dy < mob.getBbHeight() && dy > -player.getBbHeight();
		};
	}

	/** The ten of block A, written from {@code at}. */
	public static void observe(Mob mob, Player player, long now, float[] out, int at) {
		State s = of(player);
		out[at] = (float) ObsM1.clip(s.overGround / 8.0, -2.0, 2.0);
		out[at + 1] = s.pillar ? 1.0F : 0.0F;
		out[at + 2] = s.tower ? 1.0F : 0.0F;
		out[at + 3] = (float) (s.edge / 2.0);
		out[at + 4] = (float) ObsM1.clip(pushFall(mob, player) / 8.0, 0.0, 2.0);
		out[at + 5] = reachable(mob, player) ? 1.0F : 0.0F;
		out[at + 6] = s.climbable ? 1.0F : 0.0F;
		out[at + 7] = (float) ObsM1.clip(upTicks(s, now) / 200.0, 0.0, 2.0);
		out[at + 8] = s.overGround >= UP && now - s.shotUpAt <= SHOT_MEMORY ? 1.0F : 0.0F;
		out[at + 9] = s.overGround >= UP && ObsM1.sees(mob, player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ()) ? 1.0F : 0.0F;
	}
}
