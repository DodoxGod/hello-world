package dev.forja.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.forja.mixin.LivingEntityAiAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The blaze's own observation, red_blaze_v1 (docs/red_blaze_contrato.json; Andy, 2026-09-29): 65 inputs in five blocks
 * (YO, OBJ, BOLAS, ALIADOS, ENTORNO). A flying mob with a burst of fireballs did not fit red_mob_v3 without breaking it
 * (PROPUESTAS_IA_SIMULADOR.md §7.1): it needs its height over the ground and the player's, the state of its burst and
 * where its own fireballs are going, and none of the melee, squad or special inputs.
 *
 * <p>The frame is v3's: "forward" is the flat direction from the blaze to the player, "right" is (−forward_z,
 * forward_x). Every input is 0 when there is nothing to measure, the contracts' neutral value.
 */
public final class ObsBlaze {
	/** The "formato" of a blaze network (docs/red_blaze_contrato.json). */
	public static final String FORMAT = "red_blaze_v1";
	public static final int SIZE = 65;
	/** Where each block starts. */
	public static final int YO_AT = 0;
	public static final int OBJ_AT = 15;
	public static final int BOLAS_AT = 39;
	public static final int ALIADOS_AT = 43;
	public static final int ENTORNO_AT = 54;
	/** How far down the ground is looked for (yo_sobre_suelo/8 reads 2 when there is none), and up for a ceiling. */
	public static final int GROUND_DEPTH = 16;
	public static final int CEILING_RANGE = 8;
	/** How far off its own fireballs, other blazes and allies are counted, and fireballs sent back at it. */
	public static final double RANGE = 32.0;
	public static final double BATTED_RANGE = 16.0;
	/** A ground ally this close to the player (flat) is round them: tierra_junto_obj/5. */
	public static final double CLOSE_TO_PLAYER = 3.0;

	private static final String[] DIRECTIONS = {"adelante", "adelante_derecha", "derecha", "atras_derecha", "atras",
		"atras_izquierda", "izquierda", "adelante_izquierda"};

	private static final List<String> NAMES;

	static {
		List<String> names = new ArrayList<>();
		// YO: the blaze itself
		Collections.addAll(names, "yo_vida_frac", "yo_sobre_suelo/8", "yo_vy*5", "yo_vel_hacia_obj*5", "yo_vel_lateral*5", "yo_en_suelo",
			"yo_carga", "yo_cargado", "yo_en_rafaga", "yo_rafaga_quedan/3", "yo_enfriamiento/60", "yo_fuego", "yo_en_agua", "yo_lluvia",
			"yo_herido");
		// OBJ: the player
		Collections.addAll(names, "obj_distancia/16", "obj_distancia3d/16", "obj_dy/4", "obj_vel_hacia_mi*5", "obj_vel_lateral*5",
			"obj_vy*5", "obj_en_suelo", "obj_sobre_suelo/8", "obj_vida/20", "obj_escudo_arriba", "obj_escudo_activo", "obj_mano_arco",
			"obj_arco_tensado/20", "obj_mano_cuerpo", "obj_me_mira(cos)", "obj_desde_ataque/40", "obj_esprintando", "obj_en_agua",
			"obj_ardiendo", "obj_resiste_fuego", "obj_techo_cerca", "obj_a_espada", "yo_ve_obj", "linea_aliado");
		// BOLAS: its fireballs in flight
		Collections.addAll(names, "bolas_en_vuelo/3", "bola0_fallo/4", "bola0_t/20", "bolas_desviadas/3");
		// ALIADOS: other blazes and allies on the ground
		Collections.addAll(names, "blazes_n/4", "blaze0_presente", "blaze0_delante/16", "blaze0_derecha/16", "blaze0_dy/4", "tierra_n/5",
			"tierra0_presente", "tierra0_delante/16", "tierra0_derecha/16", "tierra0_dist_obj/16", "tierra_junto_obj/5");
		// ENTORNO: what is round it
		Collections.addAll(names, "techo_cerca", "agua_debajo", "agua_cerca");
		for (String d : DIRECTIONS) {
			names.add("pared_r2_" + d);
		}
		NAMES = Collections.unmodifiableList(names);
	}

	private ObsBlaze() {
	}

	/** The 65 names, in the contract's order. */
	public static List<String> names() {
		return NAMES;
	}

	/** The observation a red_blaze_v1 network is fed. {@code mind} may be null (tests): its burst then reads as idle. */
	public static float[] build(Mob mob, Player target, MobMind mind) {
		double[] o = new double[SIZE];
		Level level = mob.level();
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double flat = Math.hypot(dx, dz);
		double ux = flat > 1.0E-6 ? dx / flat : 1.0;
		double uz = flat > 1.0E-6 ? dz / flat : 0.0;
		double rx = -uz;
		double rz = ux;
		Vec3 mv = mob.getDeltaMovement();
		// what the player's client says it moved (the server's delta barely moves: see ObsM1)
		Vec3 tv = target.getKnownMovement();

		// YO
		int at = YO_AT;
		o[at] = mob.getHealth() / Math.max(1.0E-6, mob.getMaxHealth());
		o[at + 1] = heightOverGround(mob) / 8.0;
		o[at + 2] = ObsM1.clip(mv.y * 5.0, -3.0, 3.0);
		o[at + 3] = ObsM1.clip((mv.x * ux + mv.z * uz) * 5.0, -3.0, 3.0);
		o[at + 4] = ObsM1.clip((mv.x * rx + mv.z * rz) * 5.0, -3.0, 3.0);
		o[at + 5] = mob.onGround() ? 1.0 : 0.0;
		int charge = mind == null ? 0 : mind.blazeCharge;
		int draw = mind == null ? 0 : mind.draw;
		int cooldown = mind == null ? 0 : mind.cooldown;
		o[at + 6] = ObsM1.clip(charge / (double) BlazePilot.CHARGE_TICKS, 0.0, 1.0);
		o[at + 7] = charge >= BlazePilot.CHARGE_TICKS ? 1.0 : 0.0;
		o[at + 8] = draw > 0 ? 1.0 : 0.0;
		o[at + 9] = draw > 0 ? BlazePilot.shotsLeft(draw) / (double) TacticGoal.BLAZE_BURST : 0.0;
		o[at + 10] = ObsM1.clip(Math.max(0, cooldown) / (double) TacticGoal.BLAZE_COOLDOWN, 0.0, 1.0);
		o[at + 11] = mob.isOnFire() ? 1.0 : 0.0;
		o[at + 12] = mob.isInWater() ? 1.0 : 0.0;
		o[at + 13] = level.isRainingAt(mob.blockPosition()) ? 1.0 : 0.0;
		o[at + 14] = mob.invulnerableTime > 10 ? 1.0 : 0.0;

		// OBJ
		at = OBJ_AT;
		Vec3 mobCenter = new Vec3(mob.getX(), mob.getY(0.5), mob.getZ());
		Vec3 targetCenter = new Vec3(target.getX(), target.getY(0.5), target.getZ());
		o[at] = ObsM1.clip(flat / 16.0, 0.0, 2.0);
		o[at + 1] = ObsM1.clip(mobCenter.distanceTo(targetCenter) / 16.0, 0.0, 2.0);
		o[at + 2] = ObsM1.clip((target.getY() - mob.getY()) / 4.0, -2.0, 2.0);
		o[at + 3] = ObsM1.clip(-(tv.x * ux + tv.z * uz) * 5.0, -3.0, 3.0);
		o[at + 4] = ObsM1.clip((tv.x * rx + tv.z * rz) * 5.0, -3.0, 3.0);
		o[at + 5] = ObsM1.clip(tv.y * 5.0, -3.0, 3.0);
		o[at + 6] = target.onGround() ? 1.0 : 0.0;
		o[at + 7] = heightOverGround(target) / 8.0;
		o[at + 8] = ObsM1.clip(target.getHealth() / 20.0, 0.0, 1.5);
		int shield = ObsM1.shieldTicks(target);
		o[at + 9] = shield > 0 ? 1.0 : 0.0;
		o[at + 10] = shield >= ObsM1.SHIELD_TICKS ? 1.0 : 0.0;
		o[at + 11] = ranged(target.getMainHandItem()) || ranged(target.getOffhandItem()) ? 1.0 : 0.0;
		boolean drawing = target.isUsingItem() && ranged(target.getUseItem());
		o[at + 12] = drawing ? ObsM1.clip(target.getTicksUsingItem() / 20.0, 0.0, 1.0) : 0.0;
		ItemStack held = target.getMainHandItem();
		o[at + 13] = held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES)
			|| dev.forja.combat.SwingStyle.of(held) != dev.forja.combat.SwingStyle.VANILLA ? 1.0 : 0.0;
		Vec3 toMob = mobCenter.subtract(target.getEyePosition());
		o[at + 14] = toMob.lengthSqr() > 1.0E-8 ? ObsM1.clip(target.getViewVector(1.0F).dot(toMob.normalize()), -1.0, 1.0) : 0.0;
		o[at + 15] = Math.min(40, Math.max(0, ((LivingEntityAiAccess) target).forja$attackStrengthTicker())) / 40.0;
		o[at + 16] = target.isSprinting() ? 1.0 : 0.0;
		o[at + 17] = target.isInWater() ? 1.0 : 0.0;
		o[at + 18] = target.isOnFire() ? 1.0 : 0.0;
		o[at + 19] = target.hasEffect(MobEffects.FIRE_RESISTANCE) ? 1.0 : 0.0;
		o[at + 20] = ceilingNear(level, target.getX(), target.getY() + target.getBbHeight(), target.getZ());
		o[at + 21] = playerReaches(mob, target) ? 1.0 : 0.0;
		o[at + 22] = sees(mob, target) ? 1.0 : 0.0;
		o[at + 23] = Squad.allyInLine(mob, target) ? 1.0 : 0.0;

		// BOLAS
		at = BOLAS_AT;
		List<SmallFireball> mine = level.getEntitiesOfClass(SmallFireball.class, mob.getBoundingBox().inflate(RANGE),
			ball -> ball.isAlive() && ball.getOwner() == mob);
		o[at] = Math.min(mine.size() / 3.0, 2.0);
		double bestMiss = Double.POSITIVE_INFINITY;
		double bestTime = 0.0;
		for (SmallFireball ball : mine) {
			Vec3 p = ball.position();
			Vec3 v = ball.getDeltaMovement();
			Vec3 rel = targetCenter.subtract(p);
			double speed2 = v.lengthSqr();
			double t = speed2 > 1.0E-8 ? Math.max(0.0, rel.dot(v) / speed2) : 0.0;
			double miss = rel.subtract(v.scale(t)).length();
			if (miss < bestMiss) {
				bestMiss = miss;
				bestTime = t;
			}
		}
		if (!mine.isEmpty()) {
			o[at + 1] = ObsM1.clip(bestMiss / 4.0, 0.0, 2.0);
			o[at + 2] = ObsM1.clip(bestTime / 20.0, 0.0, 2.0);
		}
		int batted = level.getEntitiesOfClass(SmallFireball.class, mob.getBoundingBox().inflate(BATTED_RANGE),
			ball -> ball.isAlive() && ball.getOwner() == target).size();
		o[at + 3] = Math.min(batted / 3.0, 2.0);

		// ALIADOS
		at = ALIADOS_AT;
		int blazes = 0;
		int ground = 0;
		int round = 0;
		Mob blaze0 = null;
		Mob ground0 = null;
		for (Mob other : ObsM1.allies(mob)) {
			if (other.distanceToSqr(mob) > RANGE * RANGE) {
				continue;
			}
			if (other.getType() == EntityTypes.BLAZE) {
				blazes++;
				if (blaze0 == null) {
					blaze0 = other;
				}
			} else if (other.getTarget() == target) {
				ground++;
				if (ground0 == null) {
					ground0 = other;
				}
				if (Math.hypot(other.getX() - target.getX(), other.getZ() - target.getZ()) <= CLOSE_TO_PLAYER) {
					round++;
				}
			}
		}
		o[at] = Math.min(blazes / 4.0, 2.0);
		if (blaze0 != null) {
			double ax = blaze0.getX() - mob.getX();
			double az = blaze0.getZ() - mob.getZ();
			o[at + 1] = 1.0;
			o[at + 2] = ObsM1.clip((ax * ux + az * uz) / 16.0, -2.0, 2.0);
			o[at + 3] = ObsM1.clip((ax * rx + az * rz) / 16.0, -2.0, 2.0);
			o[at + 4] = ObsM1.clip((blaze0.getY() - mob.getY()) / 4.0, -2.0, 2.0);
		}
		o[at + 5] = Math.min(ground / 5.0, 2.0);
		if (ground0 != null) {
			double ax = ground0.getX() - mob.getX();
			double az = ground0.getZ() - mob.getZ();
			o[at + 6] = 1.0;
			o[at + 7] = ObsM1.clip((ax * ux + az * uz) / 16.0, -2.0, 2.0);
			o[at + 8] = ObsM1.clip((ax * rx + az * rz) / 16.0, -2.0, 2.0);
			o[at + 9] = ObsM1.clip(Math.hypot(target.getX() - ground0.getX(), target.getZ() - ground0.getZ()) / 16.0, 0.0, 2.0);
		}
		o[at + 10] = Math.min(round / 5.0, 2.0);

		// ENTORNO
		at = ENTORNO_AT;
		o[at] = ceilingNear(level, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ());
		o[at + 1] = waterBelow(mob) ? 1.0 : 0.0;
		o[at + 2] = waterNear(mob, ux, uz) ? 1.0 : 0.0;
		for (int k = 0; k < 8; k++) {
			double[] dir = ObsM1.direction(k + 1, ux, uz);
			double px = mob.getX() + dir[0] * 2.0;
			double pz = mob.getZ() + dir[1] * 2.0;
			o[at + 3 + k] = solid(level, px, mob.getY() + 0.5, pz) || solid(level, px, mob.getY() + 1.5, pz) ? 1.0 : 0.0;
		}

		float[] out = new float[SIZE];
		for (int i = 0; i < SIZE; i++) {
			out[i] = (float) o[i];
		}
		return out;
	}

	private static boolean ranged(ItemStack stack) {
		return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
	}

	/** Line of sight from the blaze's eyes to the player's middle: what yo_ve_obj reads and what a shot needs. */
	public static boolean sees(Mob mob, Player target) {
		return ObsM1.sees(mob, target.getX(), target.getY(0.5), target.getZ());
	}

	/** Whether the player's blow reaches the blaze now: from their eyes to the nearest point of its box, within their real reach. */
	static boolean playerReaches(Mob mob, Player target) {
		AABB box = mob.getBoundingBox();
		Vec3 eye = target.getEyePosition();
		double x = Math.max(box.minX, Math.min(box.maxX, eye.x));
		double y = Math.max(box.minY, Math.min(box.maxY, eye.y));
		double z = Math.max(box.minZ, Math.min(box.maxZ, eye.z));
		return eye.distanceTo(new Vec3(x, y, z)) <= Reach.player(target);
	}

	/**
	 * How high an entity's feet are over the first block with collision in the column under its centre, searched
	 * {@link #GROUND_DEPTH} blocks down: 0 standing, {@link #GROUND_DEPTH} when there is none that deep.
	 */
	public static double heightOverGround(net.minecraft.world.entity.Entity entity) {
		Level level = entity.level();
		double y = entity.getY();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int x = (int) Math.floor(entity.getX());
		int z = (int) Math.floor(entity.getZ());
		int top = (int) Math.floor(y);
		for (int k = top; k >= top - GROUND_DEPTH; k--) {
			pos.set(x, k, z);
			var shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				double surface = k + shape.max(Direction.Axis.Y);
				return ObsM1.clip(y - Math.min(surface, y), 0.0, GROUND_DEPTH);
			}
		}
		return GROUND_DEPTH;
	}

	/**
	 * How far above {@code headY} the first block with collision is, in the column at (x, z), searched
	 * {@link #CEILING_RANGE} blocks up; {@link Double#POSITIVE_INFINITY} for none.
	 */
	public static double ceiling(Level level, double x, double headY, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int from = (int) Math.floor(headY);
		for (int k = from; k <= from + CEILING_RANGE; k++) {
			pos.set(bx, k, bz);
			var shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				double bottom = k + shape.min(Direction.Axis.Y);
				if (bottom >= headY - 1.0E-3) {
					return bottom - headY;
				}
				if (k + shape.max(Direction.Axis.Y) > headY) {
					return 0.0;
				}
			}
		}
		return Double.POSITIVE_INFINITY;
	}

	/** techo_cerca and obj_techo_cerca: (8 − distance to the ceiling) / 8, 0 for none within 8. */
	private static double ceilingNear(Level level, double x, double headY, double z) {
		double d = ceiling(level, x, headY, z);
		return Double.isInfinite(d) ? 0.0 : ObsM1.clip((CEILING_RANGE - d) / CEILING_RANGE, 0.0, 1.0);
	}

	/** Water in the column under it, before the ground and within {@link #GROUND_DEPTH}. */
	private static boolean waterBelow(Mob mob) {
		Level level = mob.level();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int x = (int) Math.floor(mob.getX());
		int z = (int) Math.floor(mob.getZ());
		int top = (int) Math.floor(mob.getY());
		for (int k = top; k >= top - GROUND_DEPTH; k--) {
			pos.set(x, k, z);
			if (level.getFluidState(pos).is(FluidTags.WATER)) {
				return true;
			}
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return false;
			}
		}
		return false;
	}

	/** Water in its own block or in the eight round it at 1.5, at its feet or one lower. */
	private static boolean waterNear(Mob mob, double ux, double uz) {
		if (mob.isInWater()) {
			return true;
		}
		Level level = mob.level();
		for (int k = 0; k <= 8; k++) {
			double px = mob.getX();
			double pz = mob.getZ();
			if (k > 0) {
				double[] dir = ObsM1.direction(k, ux, uz);
				px += dir[0] * 1.5;
				pz += dir[1] * 1.5;
			}
			for (int dy = 0; dy >= -1; dy--) {
				if (level.getFluidState(BlockPos.containing(px, mob.getY() + dy, pz)).is(FluidTags.WATER)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean solid(Level level, double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
}
