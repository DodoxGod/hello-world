package dev.forja.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The shape of one world's Cementerio entre Estrellas, drawn from its seed (Andy, 2026-09-29: every
 * world's graveyard should be its own).
 *
 * <p>The arena in the middle is the same everywhere: it is where the fight is, and a fight should not
 * change with the seed. Everything round it is dealt from the seed once, when the layout is made — the
 * outline of the plateau, how many rivers and where they wander, the bridges, the islets, how the graves
 * are laid out, how thick the pits of them are, how many cold forges there are and where, and the roll
 * of the ash. Nothing here is noise: a few dozen numbers and some sines, then a hash salted with the
 * seed for every per-column choice. A column still costs the same handful of arithmetic it did, and
 * still depends on nothing but itself.
 */
public final class StarYardLayout {
	private static final Map<Long, StarYardLayout> CACHE = new ConcurrentHashMap<>();

	public final long seed;
	/** The outline: a base radius and three harmonics, each with its own count of lobes, height and turn. */
	private final double baseRadius;
	private final int[] lobes = new int[3];
	private final double[] lobeHeight = new double[3];
	private final double[] lobeTurn = new double[3];
	/** The roll of the ash: three waves, each stretched and shifted by the seed. */
	private final double[] rollScale = new double[6];
	private final double[] rollShift = new double[3];
	/** The patches of trodden ash and the basalt outcrops: stretch and shift of two warped waves each. */
	private final double[] ground = new double[8];
	/** The rivers: bearing, sideways sway (two waves: height, length, turn). */
	public final double[] rivers;
	private final double[][] sway;
	/** The two rings where bridges cross the rivers. */
	public final double[] bridges;
	/** The islets: x, z, top y, radius. */
	public final int[][] islets;
	/**
	 * The dead vents: the old cones the smiths once drew their heat from (Andy, 2026-09-29). Each one: x, z,
	 * radius, height, what it has (bit 0 smoke, bit 1 a cold chimney, bit 2 a copper chute), and the
	 * bearing of its chimney and chute in thousandths of a turn.
	 */
	public final int[][] vents;
	/** The graves: spacing along a row, how many columns in a thousand hold one loose, and in a pit. */
	public final double rowSpacing;
	public final int looseGraves;
	public final int pitGraves;
	/** One pit cell in this many. */
	public final int pitEvery;
	/** The forges: cell size, and one cell in this many has one. */
	public final int forgeCell;
	public final int forgeEvery;

	private StarYardLayout(long seed) {
		this.seed = seed;
		RandomSource random = RandomSource.create(seed ^ 0x5CE3E7E7A1L);
		this.baseRadius = 138.0 + random.nextDouble() * 24.0;
		int[][] lobeRange = {{2, 4}, {5, 8}, {9, 14}};
		double[][] heightRange = {{10.0, 16.0}, {5.0, 9.0}, {2.0, 5.0}};
		for (int i = 0; i < 3; i++) {
			this.lobes[i] = lobeRange[i][0] + random.nextInt(lobeRange[i][1] - lobeRange[i][0] + 1);
			this.lobeHeight[i] = Mth.lerp(random.nextDouble(), heightRange[i][0], heightRange[i][1]);
			this.lobeTurn[i] = random.nextDouble() * Math.PI * 2.0;
		}
		for (int i = 0; i < 6; i++) {
			this.rollScale[i] = 0.8 + random.nextDouble() * 0.45;
		}
		for (int i = 0; i < 3; i++) {
			this.rollShift[i] = random.nextDouble() * Math.PI * 2.0;
		}
		for (int i = 0; i < 8; i++) {
			this.ground[i] = i % 2 == 0 ? 0.75 + random.nextDouble() * 0.5 : random.nextDouble() * Math.PI * 2.0;
		}
		// Two to four rivers, spread round the plateau with a little play so no two meet.
		int count = 2 + random.nextInt(3);
		double start = random.nextDouble() * Math.PI * 2.0;
		double step = Math.PI * 2.0 / count;
		this.rivers = new double[count];
		this.sway = new double[count][];
		for (int k = 0; k < count; k++) {
			this.rivers[k] = start + k * step + (random.nextDouble() - 0.5) * Math.min(step - Math.toRadians(70.0), Math.toRadians(40.0));
			this.sway[k] = new double[] {
				6.0 + random.nextDouble() * 5.0, 15.0 + random.nextDouble() * 8.0, random.nextDouble() * Math.PI * 2.0,
				3.0 + random.nextDouble() * 2.0, 7.0 + random.nextDouble() * 3.0, random.nextDouble() * Math.PI * 2.0
			};
		}
		this.bridges = new double[] {70.0 + random.nextDouble() * 12.0, 100.0 + random.nextDouble() * 20.0};
		// Five to nine islets out past the edge, none on top of another.
		int wanted = 5 + random.nextInt(5);
		List<int[]> placed = new ArrayList<>();
		for (int tries = 0; placed.size() < wanted && tries < 200; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int radius = 7 + random.nextInt(12);
			double out = edge(angle) + radius + 25.0 + random.nextDouble() * 70.0;
			int x = (int) Math.round(Math.cos(angle) * out);
			int z = (int) Math.round(Math.sin(angle) * out);
			boolean clear = true;
			for (int[] other : placed) {
				double apart = Math.hypot(x - other[0], z - other[1]);
				clear &= apart > radius + other[3] + 12;
			}
			if (clear) {
				placed.add(new int[] {x, z, 55 + random.nextInt(66), radius});
			}
		}
		this.islets = placed.toArray(new int[0][]);
		// Five to nine dead vents on the ash, away from the arena, the rivers, the bridges and each other.
		int vented = 5 + random.nextInt(5);
		List<int[]> cones = new ArrayList<>();
		for (int tries = 0; cones.size() < vented && tries < 300; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int radius = 7 + random.nextInt(7);
			double far = edge(angle) - radius - 8.0;
			if (far < 88.0) {
				continue;
			}
			double out = 88.0 + random.nextDouble() * (far - 88.0);
			int x = (int) Math.round(Math.cos(angle) * out);
			int z = (int) Math.round(Math.sin(angle) * out);
			if (riverDistance(x + 0.5, z + 0.5) < radius + 8.0 || bridgeOffset(x + 0.5, z + 0.5) < radius + 4.0) {
				continue;
			}
			boolean clear = true;
			for (int[] other : cones) {
				clear &= Math.hypot(x - other[0], z - other[1]) > radius + other[2] + 10;
			}
			if (clear) {
				cones.add(new int[] {x, z, radius, 6 + random.nextInt(9), random.nextInt(8), random.nextInt(1000)});
			}
		}
		this.vents = cones.toArray(new int[0][]);
		this.rowSpacing = 3.0 + random.nextDouble() * 0.6;
		this.looseGraves = 40 + random.nextInt(21);
		this.pitGraves = 120 + random.nextInt(81);
		this.pitEvery = 4 + random.nextInt(3);
		this.forgeCell = 22 + random.nextInt(5);
		this.forgeEvery = 2;
	}

	/** The layout of the world with this seed; made once and kept. */
	public static StarYardLayout of(long seed) {
		return CACHE.computeIfAbsent(seed, StarYardLayout::new);
	}

	// ------------------------------------------------------------------ the shape of the ground

	/** How far the plateau reaches along this bearing: roughly 108 to 192, with capes and bays. */
	public double edge(double theta) {
		double reach = this.baseRadius;
		for (int i = 0; i < 3; i++) {
			reach += this.lobeHeight[i] * Math.sin(this.lobes[i] * theta + this.lobeTurn[i]);
		}
		return reach;
	}

	/** Whether this column is part of the plateau at all. */
	public boolean onPlateau(int x, int z) {
		double cx = x + 0.5;
		double cz = z + 0.5;
		return StarYard.radius(cx, cz) < edge(StarYard.bearing(cx, cz));
	}

	/** The top block of the plateau here: flat over the arena and the apron, then a slow roll of about ±3. */
	public int surface(int x, int z) {
		double cx = x + 0.5;
		double cz = z + 0.5;
		double r = StarYard.radius(cx, cz);
		if (r <= StarYard.APRON) {
			return StarYard.SURFACE;
		}
		double roll = 1.6 * Math.sin(cx * 0.052 * this.rollScale[0] + this.rollShift[0]) * Math.cos(cz * 0.047 * this.rollScale[1])
			+ 1.1 * Math.sin((cx * this.rollScale[2] + cz * this.rollScale[3]) * 0.09 + this.rollShift[1])
			+ 0.8 * Math.sin((cx * this.rollScale[4] - cz * this.rollScale[5]) * 0.13 + this.rollShift[2]);
		double share = Mth.clamp((r - StarYard.APRON) / (StarYard.FLAT_TO - StarYard.APRON), 0.0, 1.0);
		return StarYard.SURFACE + (int) Math.round(roll * share);
	}

	/** The lowest block of the plateau under this column: thick in the middle, a crust at the edge. */
	public int bottom(int x, int z) {
		double cx = x + 0.5;
		double cz = z + 0.5;
		double r = StarYard.radius(cx, cz);
		double reach = edge(StarYard.bearing(cx, cz));
		double inner = Math.max(0.0, 1.0 - (r / reach) * (r / reach));
		int depth = 4 + (int) Math.round(64.0 * Math.pow(inner, 0.8)) + (int) (hash(x, z, 11) & 3L);
		// Basalt teeth hanging off the underside, one column in fifty.
		if (hash(x, z, 12) % 50L == 0L) {
			depth += 4 + (int) (hash(x, z, 13) % 9L);
		}
		return StarYard.SURFACE - depth;
	}

	/**
	 * Where the ash lies trodden hard, above 0.55: two waves, each bent by a slower one across it so the
	 * patches wander instead of lining up in rows.
	 */
	public double patch(double x, double z) {
		double wx = x + 9.0 * Math.sin(z * 0.023 * this.ground[2] + this.ground[3]);
		double wz = z + 9.0 * Math.sin(x * 0.019 * this.ground[4] + this.ground[5]);
		return Math.sin(wx * 0.071 * this.ground[0] + Math.sin(wz * 0.05) + this.ground[1]) * Math.cos(wz * 0.064 * this.ground[6] + this.ground[7]);
	}

	/** Where basalt breaks through the ash, above 0.86: the same bending, on a shorter wave. */
	public double outcrop(double x, double z) {
		double wx = x + 7.0 * Math.sin(z * 0.031 * this.ground[4] + this.ground[1]);
		double wz = z + 7.0 * Math.sin(x * 0.029 * this.ground[2] + this.ground[7]);
		return Math.sin(wx * 0.11 * this.ground[6] + this.ground[3]) * Math.sin(wz * 0.097 * this.ground[0] + this.ground[5]);
	}

	// ------------------------------------------------------------------ rivers

	private double riverSway(int k, double r) {
		double[] s = this.sway[k];
		return s[0] * Math.sin(r / s[1] + s[2]) + s[3] * Math.sin(r / s[4] + s[5]);
	}

	private double riverSlope(int k, double r) {
		double[] s = this.sway[k];
		return s[0] / s[1] * Math.cos(r / s[1] + s[2]) + s[3] / s[4] * Math.cos(r / s[4] + s[5]);
	}

	/** How far this point is from the middle of the nearest river, across it; MAX_VALUE where there is none. */
	public double riverDistance(double x, double z) {
		double r = StarYard.radius(x, z);
		if (r < StarYard.RIVER_START) {
			return Double.MAX_VALUE;
		}
		double theta = StarYard.bearing(x, z);
		double best = Double.MAX_VALUE;
		for (int k = 0; k < this.rivers.length; k++) {
			double off = Mth.wrapDegrees(Math.toDegrees(theta - this.rivers[k])) * Mth.DEG_TO_RAD * r - riverSway(k, r);
			double slope = riverSlope(k, r);
			best = Math.min(best, Math.abs(off) / Math.sqrt(1.0 + slope * slope));
		}
		return best;
	}

	/** The middle of river {@code k} at distance {@code r} from the arena's centre, at the plateau's level. */
	public Vec3 riverPoint(int k, double r) {
		double theta = this.rivers[k] + riverSway(k, r) / r;
		return new Vec3(Math.cos(theta) * r, StarYard.SURFACE, Math.sin(theta) * r);
	}

	/** Where river {@code k} rises: a small basin on its line, RIVER_START out. */
	public Vec3 spring(int k) {
		return riverPoint(k, StarYard.RIVER_START);
	}

	/** Whether this point is on one of the bridges' decks (a ring 5 wide where it crosses a river). */
	public boolean onBridge(double x, double z) {
		return bridgeOffset(x, z) < 2.5;
	}

	/** How far across a bridge's deck this point is from its middle line. */
	public double bridgeOffset(double x, double z) {
		double r = StarYard.radius(x, z);
		double best = Double.MAX_VALUE;
		for (double ring : this.bridges) {
			best = Math.min(best, Math.abs(r - ring));
		}
		return best;
	}

	/** The dead vent this column is on, or -1: a handful of distances, one per vent. */
	public int vent(double x, double z) {
		for (int i = 0; i < this.vents.length; i++) {
			int[] v = this.vents[i];
			double dx = x - v[0] - 0.5;
			double dz = z - v[1] - 0.5;
			if (dx * dx + dz * dz < (double) v[2] * v[2]) {
				return i;
			}
		}
		return -1;
	}

	/** Whether this point is within {@code margin} of any vent's foot. */
	public boolean nearVent(double x, double z, double margin) {
		for (int[] v : this.vents) {
			if (Math.hypot(x - v[0] - 0.5, z - v[1] - 0.5) < v[2] + margin) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ randomness

	/** Every per-column choice: a hash of the column, the salt and this world's seed. */
	public long hash(int x, int z, int salt) {
		return StarYard.hash(x, z, salt, this.seed);
	}
}
