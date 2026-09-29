package dev.forja.ai;

import java.util.ArrayList;
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
		// A player's own movement is its client's: the server's delta barely moves except when it is knocked
		// about, so a network trained on real speeds would see a statue. getKnownMovement is what the client
		// last said it moved (and the delta, for anything that is not a player on a server).
		Vec3 tv = target.getKnownMovement();
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
		o[42] = trident(mob.getMainHandItem()) ? 1.0 : 0.0;

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

	/**
	 * Whether the mob's blow reaches: the gap between the two boxes, flat, within {@link #REACH}. This is
	 * obj_en_alcance, the simulator's body reach, whatever the mob holds: what its weapon adds is its own
	 * input (yo_arma_alcance), and what the executor strikes by is {@link Reach#reaches}.
	 */
	public static boolean reaches(LivingEntity mob, LivingEntity target) {
		return reaches(mob, target, REACH);
	}

	/**
	 * The same with a longer reach, a weapon's: the flat gap within {@code reach}. The gap up or down stays
	 * within the body's {@link #REACH}: a weapon lengthens the blow, it does not lift it onto a pillar.
	 */
	public static boolean reaches(LivingEntity mob, LivingEntity target, double reach) {
		AABB a = mob.getBoundingBox();
		AABB b = target.getBoundingBox();
		double gx = Math.max(0.0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
		double gz = Math.max(0.0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
		double gy = Math.max(0.0, Math.max(a.minY - b.maxY, b.minY - a.maxY));
		return Math.hypot(gx, gz) <= reach && gy <= REACH;
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
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = i0; i <= i1; i++) {
			for (int j = j0; j <= j1; j++) {
				// The column's chunk is looked up once, not once per block: this is what Level.getBlockState
				// does, out-of-bounds air included, minus the chunk lookup for every block.
				net.minecraft.world.level.chunk.LevelChunk chunk = null;
				for (int k = (int) Math.floor(y) + 4; k >= (int) Math.floor(y) - 8; k--) {
					pos.set(i, k, j);
					net.minecraft.world.level.block.state.BlockState state;
					if (!level.isInValidBounds(pos)) {
						state = net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
					} else {
						if (chunk == null) {
							chunk = level.getChunk(i >> 4, j >> 4);
						}
						state = chunk.getBlockState(pos);
					}
					var shape = state.getCollisionShape(level, pos);
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
	/** A trident, vanilla's or one forged: the simulator's yo_tridente does not ask who made it. */
	public static boolean trident(ItemStack stack) {
		if (stack.is(Items.TRIDENT)) {
			return true;
		}
		dev.forja.part.ForgedParts parts = stack.get(dev.forja.registry.ModComponents.PARTS);
		return parts != null && parts.type() == dev.forja.forge.ForgeType.TRIDENTE;
	}

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
		List<Mob> near = mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(ALLY_RANGE),
			other -> other != mob && other.isAlive() && other instanceof Enemy);
		// Sorting the list with a comparator was most of the cost of this search in a crowd: every
		// comparison went through two interface calls the JIT cannot inline, as every sort on the server
		// shares them. Each distance is worked out once and sorted as a plain number, stably, in the order
		// Double.compare gives: the same order as before, ties included.
		int n = near.size();
		Mob[] mobs = near.toArray(new Mob[0]);
		double[] keys = new double[n];
		for (int i = 0; i < n; i++) {
			double ox = mobs[i].getX() - mob.getX();
			double oz = mobs[i].getZ() - mob.getZ();
			keys[i] = ox * ox + oz * oz;
		}
		sortStable(keys, mobs, new double[n], new Mob[n], 0, n);
		return new ArrayList<>(java.util.Arrays.asList(mobs));
	}

	/** A stable merge sort of {@code mobs} by {@code keys} over [from, to), ordered as Double.compare orders. */
	private static void sortStable(double[] keys, Mob[] mobs, double[] spareKeys, Mob[] spareMobs, int from, int to) {
		if (to - from <= 16) {
			for (int i = from + 1; i < to; i++) {
				double key = keys[i];
				Mob held = mobs[i];
				int j = i - 1;
				while (j >= from && Double.compare(keys[j], key) > 0) {
					keys[j + 1] = keys[j];
					mobs[j + 1] = mobs[j];
					j--;
				}
				keys[j + 1] = key;
				mobs[j + 1] = held;
			}
			return;
		}
		int mid = (from + to) >>> 1;
		sortStable(keys, mobs, spareKeys, spareMobs, from, mid);
		sortStable(keys, mobs, spareKeys, spareMobs, mid, to);
		if (Double.compare(keys[mid - 1], keys[mid]) <= 0) {
			return;
		}
		System.arraycopy(keys, from, spareKeys, from, to - from);
		System.arraycopy(mobs, from, spareMobs, from, to - from);
		int left = from;
		int right = mid;
		int out = from;
		while (left < mid && right < to) {
			// On a tie the left one goes first: that is what keeps the sort stable.
			if (Double.compare(spareKeys[right], spareKeys[left]) < 0) {
				keys[out] = spareKeys[right];
				mobs[out++] = spareMobs[right++];
			} else {
				keys[out] = spareKeys[left];
				mobs[out++] = spareMobs[left++];
			}
		}
		while (left < mid) {
			keys[out] = spareKeys[left];
			mobs[out++] = spareMobs[left++];
		}
		while (right < to) {
			keys[out] = spareKeys[right];
			mobs[out++] = spareMobs[right++];
		}
	}

	static double clip(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}
}
