package dev.forja.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.forja.mixin.CreeperAiAccess;
import dev.forja.mixin.LivingEntityAiAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The 102 numbers a mob sees, exactly as the simulator's {@code obs_mob} (motor_rust/src/combate/control.rs)
 * works them out, so a network trained there sees the same thing here. The frame is the mob's: "forward"
 * is the flat direction from the mob to its target, "right" is (−forward_z, forward_x).
 *
 * <p>Where the simulator's world is simpler than Minecraft's (it is a height map with no ceilings), the
 * translation is written next to the number: see {@link #ground}.
 */
public final class ObsM1 {
	public static final int SIZE = 102;
	/** Reach of a mob's blow in the simulator: sqrt(2.04) − 0.6, added to the gap between the boxes. */
	public static final double REACH = Math.sqrt(2.04) - 0.6;
	/** Ticks a shield must be up before it blocks, as in the simulator. */
	public static final int SHIELD_TICKS = 5;
	private static final int ALLIES = 3;
	private static final double ALLY_RANGE = 32.0;

	private ObsM1() {
	}

	/**
	 * @param cooldown the mob's attack cooldown in ticks (0 when ready), from whoever drives it
	 * @param draw     ticks the mob has spent drawing its bow (0 when not)
	 */
	public static float[] of(Mob mob, Player target, int cooldown, int draw) {
		double[] o = new double[SIZE];
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double ux = dx / d;
		double uz = dz / d;
		double rx = -uz;
		double rz = ux;
		Vec3 tv = target.getDeltaMovement();
		Vec3 mv = mob.getDeltaMovement();

		o[0] = clip(d / 16.0, 0.0, 2.0);
		o[1] = clip((target.getY() - mob.getY()) / 4.0, -2.0, 2.0);
		o[2] = reaches(mob, target) ? 1.0 : 0.0;
		o[3] = clip(-(tv.x * ux + tv.z * uz) * 5.0, -3.0, 3.0);
		o[4] = clip((tv.x * rx + tv.z * rz) * 5.0, -3.0, 3.0);
		o[5] = clip(tv.y * 5.0, -3.0, 3.0);
		o[6] = target.onGround() ? 1.0 : 0.0;
		o[7] = target.isShiftKeyDown() ? 1.0 : 0.0;
		o[8] = target.isSprinting() ? 1.0 : 0.0;
		int shield = shieldTicks(target);
		o[9] = shield > 0 ? 1.0 : 0.0;
		o[10] = shield >= SHIELD_TICKS ? 1.0 : 0.0;
		o[11 + hand(target)] = 1.0;
		o[17] = clip(target.getHealth() / 20.0, 0.0, 1.5);
		Vec3 look = target.getViewVector(1.0F);
		o[18] = -(look.x * ux + look.z * uz);
		o[19] = Math.min(40, Math.max(0, ((LivingEntityAiAccess) target).forja$attackStrengthTicker())) / 40.0;
		o[20] = eating(target) ? 1.0 : 0.0;
		o[21] = Math.min(20, bowTicks(target)) / 20.0;

		o[22] = mob.getHealth() / mob.getMaxHealth();
		o[23] = mob.onGround() ? 1.0 : 0.0;
		o[24] = clip((mv.x * ux + mv.z * uz) * 5.0, -3.0, 3.0);
		o[25] = clip((mv.x * rx + mv.z * rz) * 5.0, -3.0, 3.0);
		o[26] = clip(mv.y * 5.0, -3.0, 3.0);
		o[27] = clip(Math.max(0, cooldown) / 40.0, 0.0, 1.0);
		o[28] = clip(draw / 20.0, 0.0, 1.0);
		o[29] = mob instanceof Creeper creeper ? clip(((CreeperAiAccess) creeper).forja$swell() / 30.0, 0.0, 1.0) : 0.0;
		o[30] = mob.isOnFire() ? 1.0 : 0.0;
		o[31] = mob.isInWater() ? 1.0 : 0.0;
		o[32] = mob.invulnerableTime > 10 ? 1.0 : 0.0;
		int type = MobFamily.typeIndex(mob.getType());
		if (type >= 0) {
			o[33 + type] = 1.0;
		}
		o[40] = mob.isBaby() ? 1.0 : 0.0;
		double sightHeight = MobFamily.of(mob) == MobFamily.CREEPER ? 1.5 : target.getBbHeight() * 0.6;
		o[41] = sees(mob, target.getX(), target.getY() + sightHeight, target.getZ()) ? 1.0 : 0.0;
		o[42] = mob.getMainHandItem().is(Items.TRIDENT) ? 1.0 : 0.0;

		// The ground around, in the mob's frame: 8 directions at 1.5 and 3 blocks.
		boolean water = mob.isInWater();
		Level level = mob.level();
		double halfWidth = Math.min(mob.getBbWidth() / 2.0, 0.3);
		for (int ring = 0; ring < 2; ring++) {
			double radius = ring == 0 ? 1.5 : 3.0;
			for (int k = 0; k < 8; k++) {
				double[] dir = direction(k + 1, ux, uz);
				double px = mob.getX() + dir[0] * radius;
				double pz = mob.getZ() + dir[1] * radius;
				double ground = ground(level, px, pz, mob.getY(), halfWidth);
				o[43 + ring * 8 + k] = clip((ground - mob.getY()) / 4.0, -1.0, 1.0);
				FluidState fluid = level.getFluidState(BlockPos.containing(px, ground + 0.05, pz));
				if (fluid.is(FluidTags.WATER)) {
					water = true;
				}
				if (ring == 0) {
					o[59 + k] = fluid.is(FluidTags.LAVA) || mob.getY() - ground >= 3.0 ? 1.0 : 0.0;
				}
			}
		}
		o[67] = water ? 1.0 : 0.0;

		// The nearest other hostiles still alive.
		List<Mob> allies = allies(mob);
		for (int k = 0; k < Math.min(ALLIES, allies.size()); k++) {
			Mob ally = allies.get(k);
			int base = 68 + k * 11;
			double ax = ally.getX() - mob.getX();
			double az = ally.getZ() - mob.getZ();
			o[base] = 1.0;
			o[base + 1] = clip((ax * ux + az * uz) / 16.0, -2.0, 2.0);
			o[base + 2] = clip((ax * rx + az * rz) / 16.0, -2.0, 2.0);
			o[base + 3] = clip((ally.getY() - mob.getY()) / 4.0, -2.0, 2.0);
			o[base + 4] = clip(Math.hypot(target.getX() - ally.getX(), target.getZ() - ally.getZ()) / 16.0, 0.0, 2.0);
			o[base + 5 + MobFamily.of(ally).ordinal()] = 1.0;
			o[base + 10] = ally.getHealth() / ally.getMaxHealth();
		}
		o[101] = Math.min(allies.size() / 5.0, 2.0);

		float[] out = new float[SIZE];
		for (int i = 0; i < SIZE; i++) {
			out[i] = (float) o[i];
		}
		return out;
	}

	/** Direction k (1 to 8): (k − 1) · 45° from forward, turning right. 1 forward, 3 right, 5 back, 7 left. */
	public static double[] direction(int k, double ux, double uz) {
		double th = (k - 1) * Math.PI / 4.0;
		double c = Math.cos(th);
		double s = Math.sin(th);
		double rx = -uz;
		double rz = ux;
		return new double[] {ux * c + rx * s, uz * c + rz * s};
	}

	/** Whether the mob's blow reaches: the gap between the two boxes, flat, within {@link #REACH}. */
	public static boolean reaches(LivingEntity mob, LivingEntity target) {
		AABB a = mob.getBoundingBox();
		AABB b = target.getBoundingBox();
		double gx = Math.max(0.0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
		double gz = Math.max(0.0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
		double gy = Math.max(0.0, Math.max(a.minY - b.maxY, b.minY - a.maxY));
		return Math.hypot(gx, gz) <= REACH && gy <= REACH;
	}

	/**
	 * The ground under (x, z): the top of the highest block with collision among the columns the mob's
	 * footprint would cover, searched from 4 above the mob's feet down to 8 below (the simulator has no
	 * ceilings, so anything higher than that is not "ground" here). Nothing found: 8 below the feet.
	 */
	public static double ground(Level level, double x, double z, double y, double halfWidth) {
		double best = y - 8.0;
		// A half width of 0 is a single column: the one the point is in.
		int i0 = (int) Math.floor(x - halfWidth);
		int i1 = halfWidth <= 0.0 ? i0 : (int) Math.floor(x + halfWidth - 1.0E-6);
		int j0 = (int) Math.floor(z - halfWidth);
		int j1 = halfWidth <= 0.0 ? j0 : (int) Math.floor(z + halfWidth - 1.0E-6);
		for (int i = i0; i <= i1; i++) {
			for (int j = j0; j <= j1; j++) {
				for (int k = (int) Math.floor(y) + 4; k >= (int) Math.floor(y) - 8; k--) {
					BlockPos pos = new BlockPos(i, k, j);
					var shape = level.getBlockState(pos).getCollisionShape(level, pos);
					if (!shape.isEmpty()) {
						double top = k + shape.max(net.minecraft.core.Direction.Axis.Y);
						if (top > best) {
							best = top;
						}
						break;
					}
				}
			}
		}
		return best;
	}

	/** Line of sight from the mob's eyes to a point, through blocks only. */
	public static boolean sees(Mob mob, double x, double y, double z) {
		Vec3 eye = mob.getEyePosition();
		HitResult hit = mob.level().clip(new ClipContext(eye, new Vec3(x, y, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
		return hit.getType() == HitResult.Type.MISS;
	}

	/** Ticks the player has had a blocking item up (0 when not). */
	public static int shieldTicks(Player player) {
		return player.isUsingItem() && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS) ? player.getTicksUsingItem() : 0;
	}

	private static int bowTicks(Player player) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem ? player.getTicksUsingItem() : 0;
	}

	private static boolean eating(Player player) {
		return player.isUsingItem() && player.getUseItem().has(DataComponents.FOOD);
	}

	/** What is in the player's hand, as the simulator's one-hot: sword, axe, drawn bow, food, blocks, nothing. */
	private static int hand(Player player) {
		ItemStack held = player.getMainHandItem();
		if (bowTicks(player) > 0) return 2;
		if (held.has(DataComponents.FOOD)) return 3;
		if (held.getItem() instanceof BlockItem) return 4;
		if (held.is(ItemTags.AXES)) return 1;
		if (held.is(ItemTags.SWORDS) || dev.forja.combat.SwingStyle.of(held) != dev.forja.combat.SwingStyle.VANILLA) return 0;
		return 5;
	}

	/** Other hostile mobs alive near the mob, nearest first. */
	public static List<Mob> allies(Mob mob) {
		List<Mob> found = new ArrayList<>(mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(ALLY_RANGE),
			other -> other != mob && other.isAlive() && other instanceof Enemy));
		found.sort(Comparator.comparingDouble(other -> {
			double ox = other.getX() - mob.getX();
			double oz = other.getZ() - mob.getZ();
			return ox * ox + oz * oz;
		}));
		return found;
	}

	static double clip(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}
}
