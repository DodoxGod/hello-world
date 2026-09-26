package dev.forja.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** What the ground around a fight is like, for moving well on it (ideas 51 to 60 of the plan). */
public final class Terrain {
	private Terrain() {
	}

	/** Lava, or a drop of 3 or more, at a spot (standing at height {@code y}). */
	public static boolean danger(Level level, double x, double z, double y) {
		// A point, not a footprint: the solid column beside a pool of lava must not hide it.
		double ground = ObsM1.ground(level, x, z, y, 0.0);
		return level.getFluidState(BlockPos.containing(x, ground + 0.05, z)).is(FluidTags.LAVA) || y - ground >= 3.0;
	}

	/**
	 * The direction (flat, unit) from the player towards the nearest danger within 3 blocks, looking in 8
	 * directions at 1,5 and 3; null when there is none.
	 */
	public static Vec3 dangerNear(Player player) {
		for (double r : new double[] {1.5, 3.0}) {
			for (int k = 0; k < 8; k++) {
				double a = k * Math.PI / 4.0;
				double x = player.getX() + Math.cos(a) * r;
				double z = player.getZ() + Math.sin(a) * r;
				if (danger(player.level(), x, z, player.getY())) {
					return new Vec3(Math.cos(a), 0.0, Math.sin(a));
				}
			}
		}
		return null;
	}

	/** Soft blocks a monster may push through when it is stuck: leaves, wool, hay, snow, sand, gravel, dirt. */
	public static boolean soft(BlockState state) {
		return state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL) || state.is(BlockTags.SAND) || state.is(BlockTags.DIRT)
			|| state.is(net.minecraft.world.level.block.Blocks.HAY_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
			|| state.is(net.minecraft.world.level.block.Blocks.GRAVEL);
	}

	/** Whether mobs may change blocks here (the mobGriefing rule). */
	public static boolean griefing(ServerLevel level) {
		return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
	}

	/**
	 * A spot within 4 blocks where a block stands between the mob and the player's eyes: cover from their
	 * arrows. Looks in 8 directions at 2 and 4 blocks; null when there is none.
	 */
	public static Vec3 cover(Mob mob, Player player) {
		Vec3 eye = player.getEyePosition();
		for (double r : new double[] {2.0, 4.0}) {
			for (int k = 0; k < 8; k++) {
				double a = k * Math.PI / 4.0;
				double x = mob.getX() + Math.cos(a) * r;
				double z = mob.getZ() + Math.sin(a) * r;
				double y = ObsM1.ground(mob.level(), x, z, mob.getY(), 0.3);
				if (Math.abs(y - mob.getY()) > 1.0 || danger(mob.level(), x, z, y)) {
					continue;
				}
				Vec3 spot = new Vec3(x, y + mob.getEyeHeight(), z);
				var hit = mob.level().clip(new net.minecraft.world.level.ClipContext(spot, eye,
					net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mob));
				if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
					return new Vec3(x, y, z);
				}
			}
		}
		return null;
	}

	/** A spot within 6 blocks at least 2 higher than the mob's, standable and safe; null when there is none. */
	public static Vec3 highGround(Mob mob) {
		Vec3 best = null;
		double bestHeight = mob.getY() + 1.9;
		for (double r : new double[] {3.0, 6.0}) {
			for (int k = 0; k < 8; k++) {
				double a = k * Math.PI / 4.0;
				double x = mob.getX() + Math.cos(a) * r;
				double z = mob.getZ() + Math.sin(a) * r;
				double y = ObsM1.ground(mob.level(), x, z, mob.getY() + 4.0, 0.3);
				if (y > bestHeight && !danger(mob.level(), x, z, y)) {
					bestHeight = y;
					best = new Vec3(x, y, z);
				}
			}
		}
		return best;
	}
}
