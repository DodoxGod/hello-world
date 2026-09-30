package dev.forja.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.Vec3;

/**
 * Where the Fallen Smith's apprentices come up round him (docs/HERRERO_DIMENSION.md, 3.5).
 *
 * <p>Concentric regular polygons: the inner ring a square three blocks out, each ring after it one vertex
 * more (pentagon, hexagon...) and two blocks further, filled from the inside. What is left for the last
 * ring makes a smaller regular polygon of its own on that ring, except that one or two left over are no
 * polygon at all: they join the ring inside, which grows by that many (Andy: ten is 4 + 6, a hexagon).
 * The first vertex of each ring points south (+z); every other ring is turned half a side, so the rings
 * do not line up.
 */
public final class Formation {
	private Formation() {
	}

	/** How many come for this many players in the graveyard: four, and three more for each player after the first. */
	public static int count(int players) {
		return 4 + 3 * Math.max(0, players - 1);
	}

	/** How many stand on each ring, inside out. */
	public static List<Integer> rings(int count) {
		List<Integer> rings = new ArrayList<>();
		int left = count;
		int ring = 0;
		while (left > 0) {
			int take = Math.min(4 + ring, left);
			if (take < 3 && !rings.isEmpty()) {
				rings.set(rings.size() - 1, rings.get(rings.size() - 1) + take);
				break;
			}
			rings.add(take);
			left -= take;
			ring++;
		}
		return rings;
	}

	/** The offset of each one from the smith, on the ground, inside ring first. */
	public static List<Vec3> offsets(int count) {
		List<Vec3> offsets = new ArrayList<>();
		List<Integer> rings = rings(count);
		for (int ring = 0; ring < rings.size(); ring++) {
			int take = rings.get(ring);
			double radius = radius(ring);
			double turn = ring % 2 == 1 ? Math.PI / take : 0.0;
			for (int k = 0; k < take; k++) {
				double angle = turn + 2.0 * Math.PI * k / take;
				offsets.add(new Vec3(radius * Math.sin(angle), 0.0, radius * Math.cos(angle)));
			}
		}
		return offsets;
	}

	/** Which ring each one is on, in the same order as {@link #offsets}. */
	public static List<Integer> ringOf(int count) {
		List<Integer> of = new ArrayList<>();
		List<Integer> rings = rings(count);
		for (int ring = 0; ring < rings.size(); ring++) {
			for (int k = 0; k < rings.get(ring); k++) {
				of.add(ring);
			}
		}
		return of;
	}

	public static double radius(int ring) {
		return 3.0 + 2.0 * ring;
	}
}
