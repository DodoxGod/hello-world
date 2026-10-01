package dev.forja.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.Vec3;

/**
 * The constellations of the Cementerio entre Estrellas (docs/HERRERO_DIMENSION.md, 2.7): eight weapon and
 * tool moulds drawn in stars, and the slow turn of the whole sky. Plain geometry, shared by the client's
 * sky and the server's tests (and, in delivery 3, by the fight, whose events each constellation calls).
 *
 * <p>The sky turns about the north-south line (the z axis), so a direction's height goes up and down with
 * the turn while its z stays. The moulds used to sit bunched in one part of the sky, and there were moments
 * of the turn with none of them up at all (Andy, 2026-09-29). Now they are spread evenly round the turn,
 * 45 degrees apart, a little north or south of it by turns: whatever the moment, three or four of them are
 * well above the horizon ({@link #fewestUp}).
 */
public final class StarChart {
	/** A turn of the whole sky every twelve minutes: half a degree a second. */
	public static final float TURN_SECONDS = 720.0F;

	/** A mould drawn in stars: its stars, and the lines between them as index pairs, in pouring order. */
	public record Constellation(String name, List<Vec3> stars, int[][] lines, float length, Vec3 centre) {
	}

	public static final List<Constellation> CONSTELLATIONS = new ArrayList<>();

	static {
		float s = 1.4F;
		// Each one: its place round the turn (degrees), how far north (+) or south (-) of the turn's circle.
		add("espada", 22.5F, 0.30F, s, new float[][] {
			{0, 12}, {1.2F, 8}, {1.2F, -2}, {4, -2}, {4, -3}, {1, -3}, {1.8F, -8}, {0, -9.5F}, {-1.8F, -8}, {-1, -3}, {-4, -3}, {-4, -2}, {-1.2F, -2}, {-1.2F, 8}
		}, true);
		add("hacha", 67.5F, -0.35F, s, new float[][] {
			{0.6F, -9}, {0.6F, 4}, {5, 1.5F}, {6, 6}, {5, 10.5F}, {0.6F, 8}, {0.6F, 9.5F}, {-0.6F, 9.5F}, {-0.6F, -9}
		}, true);
		add("martillo", 112.5F, 0.20F, s, new float[][] {
			{0.5F, -9}, {0.5F, 5}, {5, 5}, {5, 9}, {-5, 9}, {-5, 5}, {-0.5F, 5}, {-0.5F, -9}
		}, true);
		add("lanza", 157.5F, -0.25F, s, new float[][] {
			{0, -12}, {0, 6}, {1.5F, 8}, {0, 13}, {-1.5F, 8}, {0, 6}
		}, false);
		add("escudo", 202.5F, 0.35F, s, new float[][] {
			{-5, 6}, {5, 6}, {5, 1}, {3, -4}, {0, -7}, {-3, -4}, {-5, 1}
		}, true);
		add("yunque", 247.5F, -0.20F, s, new float[][] {
			{-8, 4}, {-3, 5}, {5, 5}, {5, 3}, {2, 1.5F}, {2, -2}, {4, -4}, {-4, -4}, {-2, -2}, {-2, 1.5F}, {-4, 2.5F}
		}, true);
		add("tenazas", 292.5F, 0.25F, s, new float[][] {
			{-3, -9}, {1, 3}, {0.5F, 7}, {-1, 9}, {-1, 3}, {3, -9}
		}, false);
		add("guadana", 337.5F, -0.30F, s, new float[][] {
			{0, -10}, {0, 9}, {-4, 10}, {-9, 8}, {-11, 5}, {-7, 7.2F}, {-3, 7.5F}, {0, 7}
		}, false);
	}

	private StarChart() {
	}

	/**
	 * One mould, its outline in degrees across the sky round its centre, hung at {@code around} degrees
	 * round the turn's circle (0 is due east, 90 overhead at the start) and {@code across} towards the
	 * north (+z) or the south of it.
	 */
	private static void add(String name, float around, float across, float scale, float[][] outline, boolean closed) {
		double a = Math.toRadians(around);
		double flat = Math.sqrt(1.0 - across * across);
		Vec3 centre = new Vec3(Math.cos(a) * flat, Math.sin(a) * flat, across).normalize();
		Vec3 up = Math.abs(centre.y) > 0.95 ? new Vec3(0.0, 0.0, 1.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 east = up.cross(centre).normalize();
		Vec3 north = centre.cross(east).normalize();
		List<Vec3> stars = new ArrayList<>();
		for (float[] point : outline) {
			double u = Math.tan(Math.toRadians(point[0] * scale));
			double v = Math.tan(Math.toRadians(point[1] * scale));
			stars.add(centre.add(east.scale(u)).add(north.scale(v)).normalize());
		}
		int count = closed ? stars.size() : stars.size() - 1;
		int[][] lines = new int[count][];
		float length = 0.0F;
		for (int i = 0; i < count; i++) {
			lines[i] = new int[] {i, (i + 1) % stars.size()};
			length += (float) stars.get(i).subtract(stars.get((i + 1) % stars.size())).length();
		}
		CONSTELLATIONS.add(new Constellation(name, stars, lines, length, centre));
	}

	/** How far the sky has turned at this game time, in radians. */
	public static float spin(double seconds) {
		return (float) (seconds / TURN_SECONDS * Math.PI * 2.0);
	}

	/** A direction in the sky turned about the north-south line by the sky's turn. */
	public static Vec3 turn(Vec3 at, float spin) {
		double cos = Math.cos(spin);
		double sin = Math.sin(spin);
		return new Vec3(at.x * cos - at.y * sin, at.x * sin + at.y * cos, at.z);
	}

	/**
	 * How strong the sun under the void burns for a camera at this height: all of it up to 30 above the
	 * arena, gone by 90 above it (from high up the plateau is further away than the sun hangs).
	 */
	public static float sunStrength(double y) {
		return (float) net.minecraft.util.Mth.clamp(1.0 - (y - StarYard.SURFACE - 30.0) / 60.0, 0.0, 1.0);
	}

	/**
	 * How far the fog leans to the void's ember glow: from how far down the camera looks (-1 to 1) and how
	 * high it is, and nothing else. Smooth in both: looking down takes it up to 0.45 (fading as the sun
	 * does with height), being low takes it up to 0.75 (half at y 20).
	 */
	public static float emberShare(float down, double y) {
		float look = net.minecraft.util.Mth.clamp(down, 0.0F, 1.0F);
		float fromLook = 0.45F * look * look * (3.0F - 2.0F * look) * sunStrength(y);
		float fromLow = (float) net.minecraft.util.Mth.clamp((StarYard.SURFACE - y) / 120.0, 0.0, 1.0);
		return Math.min(0.75F, Math.max(fromLook, fromLow));
	}

	/** How many moulds have their middle at least {@code degrees} above the horizon at this turn of the sky. */
	public static int up(float spin, double degrees) {
		double floor = Math.sin(Math.toRadians(degrees));
		int count = 0;
		for (Constellation c : CONSTELLATIONS) {
			if (turn(c.centre(), spin).y >= floor) {
				count++;
			}
		}
		return count;
	}

	/** The fewest moulds at least {@code degrees} up at any moment of a whole turn, sampled every degree. */
	public static int fewestUp(double degrees) {
		int fewest = Integer.MAX_VALUE;
		for (int step = 0; step < 360; step++) {
			fewest = Math.min(fewest, up((float) Math.toRadians(step), degrees));
		}
		return fewest;
	}
}
