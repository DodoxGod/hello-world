package dev.forja.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ambushes and hiding (docs/red_mob_v4_diseno.md §4.4 and §2.5, block E): where a mob can stand out of the player's
 * sight, and the player's surroundings that make a good place for it (a corridor, a doorway, a roof).
 *
 * <p><b>Hiding spot</b> (worked out at most every {@link #PERIOD} ticks per mob, and kept): 16 points round the mob
 * (the 8 directions, 3 and 6 blocks off) with a floor and room to stand; a point is a hiding spot if there is no line
 * from the player's eyes to 1.5 above it. The best by: light under 4 there +2; 2 to 5 blocks off the player's likely
 * way (where they are now and where their motion takes them in 40 ticks) +1; a corner, a doorway's side or a roof
 * over it +1; minus its distance from the mob over 8.
 *
 * <p><b>EMBOSCAR</b>: to the spot at 0.8 and still there, looking out at the player (the estimate when it has lost
 * them); a mob standing still makes no steps. Coming out is choosing another tactic.
 */
public final class Ambush {
	public static final int PERIOD = 10;
	public static final double[] RINGS = {3.0, 6.0};
	/** How far a hiding spot may be (escondite_presente: "a ≤ 8"). */
	public static final double RANGE = 8.0;
	public static final int ROUTE_TICKS = 40;

	/** A hiding spot: where, its light, and how far from the player. */
	public record Spot(Vec3 pos, int light, double fromPlayer) {
	}

	private Ambush() {
	}

	private static boolean clear(Level level, Vec3 from, Vec3 to, Mob mob) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
	}

	/** Whether a spot's head is out of the player's sight: no line from their eyes to 1.5 above it. */
	public static boolean hidden(Mob mob, Player player, Vec3 spot) {
		return !clear(mob.level(), player.getEyePosition(), spot.add(0.0, 1.5, 0.0), mob);
	}

	/** The mob's hiding spot from this player (kept for {@link #PERIOD} ticks), or null for none. */
	public static Spot spot(Mob mob, MobMind mind, Player player, long now) {
		if (now - mind.hideAt < PERIOD && now >= mind.hideAt && mind.hideFor == Perception.real(player)) {
			return mind.hideSpot;
		}
		// A spot it has is kept while it still hides it and is not too far: picked afresh each time from the points round
		// the mob, it moved as the mob walked to it, and the mob never got there.
		Spot kept = mind.hideSpot;
		boolean keep = kept != null && mind.hideFor == Perception.real(player) && kept.pos().distanceTo(mob.position()) <= RANGE
			&& hidden(mob, player, kept.pos());
		mind.hideAt = now;
		mind.hideFor = Perception.real(player);
		mind.hidePlayerAt = player.position();
		mind.hideSpot = keep ? new Spot(kept.pos(), kept.light(), kept.pos().distanceTo(player.position())) : find(mob, player);
		return mind.hideSpot;
	}

	private static Spot find(Mob mob, Player player) {
		Level level = mob.level();
		Vec3 here = player.position();
		Vec3 there = here.add(player.getDeltaMovement().multiply(ROUTE_TICKS, 0.0, ROUTE_TICKS));
		Spot best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (double r : RINGS) {
			for (int k = 0; k < 8; k++) {
				double a = k * Math.PI / 4.0;
				Vec3 near = MobItems.floorNear(mob, mob.getX() + Math.cos(a) * r, mob.getY(), mob.getZ() + Math.sin(a) * r);
				// the middle of its block: a spot a mob can stand on exactly, not against a wall's face
				Vec3 at = near == null ? null : new Vec3(Math.floor(near.x) + 0.5, near.y, Math.floor(near.z) + 0.5);
				if (at == null || at.distanceTo(mob.position()) > RANGE || !hidden(mob, player, at)) {
					continue;
				}
				BlockPos pos = BlockPos.containing(at);
				int light = level.getMaxLocalRawBrightness(pos);
				double route = distanceToSegment(at, here, there);
				double score = (light < Perception.DARK ? 2.0 : 0.0) + (route >= 2.0 && route <= 5.0 ? 1.0 : 0.0) + (sheltered(level, pos) ? 1.0 : 0.0)
					- at.distanceTo(mob.position()) / 8.0;
				if (score > bestScore) {
					bestScore = score;
					best = new Spot(at, light, at.distanceTo(here));
				}
			}
		}
		return best;
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		double dx = b.x - a.x;
		double dz = b.z - a.z;
		double len = dx * dx + dz * dz;
		double t = len < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, ((p.x - a.x) * dx + (p.z - a.z) * dz) / len));
		return Math.hypot(p.x - (a.x + dx * t), p.z - (a.z + dz * t));
	}

	/** A corner or a doorway's side (walls on 2 or more sides at head height) or a roof within 3 over it. */
	private static boolean sheltered(Level level, BlockPos pos) {
		int walls = 0;
		BlockPos head = pos.above();
		for (BlockPos side : new BlockPos[] {head.north(), head.south(), head.east(), head.west()}) {
			walls += solid(level, side) ? 1 : 0;
		}
		if (walls >= 2) {
			return true;
		}
		for (int dy = 2; dy <= 3; dy++) {
			if (solid(level, pos.above(dy))) {
				return true;
			}
		}
		return false;
	}

	private static boolean solid(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** The 8 of oculto_r3_*: from the point 3 blocks off in each of the frame's directions, no line to the player's eyes. */
	public static boolean[] occluded(Mob mob, MobMind mind, Player player, double ux, double uz, long now) {
		if (now - mind.occludedAt < PERIOD && now >= mind.occludedAt && mind.occluded != null && mind.occludedFor == Perception.real(player)) {
			return mind.occluded;
		}
		boolean[] out = new boolean[8];
		Vec3 eyes = player.getEyePosition();
		for (int k = 0; k < 8; k++) {
			double[] dir = ObsM1.direction(k + 1, ux, uz);
			Vec3 at = new Vec3(mob.getX() + dir[0] * 3.0, mob.getEyeY(), mob.getZ() + dir[1] * 3.0);
			out[k] = !clear(mob.level(), eyes, at, mob);
		}
		mind.occluded = out;
		mind.occludedAt = now;
		mind.occludedFor = Perception.real(player);
		mind.occludedPlayerAt = player.position();
		return out;
	}

	// ---------------------------------------------------------------- the player's surroundings

	/**
	 * jug_en_pasillo: walls at least 2 high within 1.5 blocks on both sides of the player, across the way they look.
	 */
	public static boolean corridor(Player player) {
		Level level = player.level();
		double yaw = Math.toRadians(player.getYRot());
		double lx = -Math.sin(yaw);
		double lz = Math.cos(yaw);
		double sx = -lz;
		double sz = lx;
		return wallSide(level, player, sx, sz) && wallSide(level, player, -sx, -sz);
	}

	private static boolean wallSide(Level level, Player player, double sx, double sz) {
		for (double d = 0.5; d <= 1.5; d += 0.5) {
			BlockPos at = BlockPos.containing(player.getX() + sx * d, player.getY() + 0.1, player.getZ() + sz * d);
			if (solid(level, at) && solid(level, at.above())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * jug_en_puerta: in a door, a fence gate or a trapdoor's block, or in a gap 1 or 2 wide between walls along one of
	 * the two axes (walls at the feet and head on both sides within 2 blocks, and open ahead and behind).
	 */
	public static boolean doorway(Player player) {
		Level level = player.level();
		BlockPos feet = player.blockPosition();
		var state = level.getBlockState(feet);
		if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof TrapDoorBlock) {
			return true;
		}
		return gap(level, feet, 1, 0) || gap(level, feet, 0, 1);
	}

	/** Walls on both sides along (dx, dz) within 2 (a gap of 1 or 2), and open along the other axis both ways. */
	private static boolean gap(Level level, BlockPos feet, int dx, int dz) {
		boolean left = false;
		boolean right = false;
		for (int d = 1; d <= 2; d++) {
			left |= solid(level, feet.offset(dx * d, 0, dz * d)) && solid(level, feet.offset(dx * d, 1, dz * d));
			right |= solid(level, feet.offset(-dx * d, 0, -dz * d)) && solid(level, feet.offset(-dx * d, 1, -dz * d));
		}
		if (!left || !right) {
			return false;
		}
		// open ahead and behind, across the gap
		return !solid(level, feet.offset(dz, 0, dx)) && !solid(level, feet.offset(-dz, 0, -dx));
	}

	/** jug_bajo_techo: a block with a collision box within 4 over the player's head. */
	public static boolean roofed(Player player) {
		Level level = player.level();
		BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
		for (int dy = 1; dy <= 4; dy++) {
			if (solid(level, head.above(dy))) {
				return true;
			}
		}
		return false;
	}
}
