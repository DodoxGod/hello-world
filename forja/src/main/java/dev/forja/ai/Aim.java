package dev.forja.ai;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "The player aims at me" (contract revision 4.1, block J, docs/mod_spec_mira.md): what the player's crosshair ray
 * hits first, worked out the way vanilla picks its crosshair target, ONE ray per player per tick and shared by every
 * mob that asks (each one only compares ids afterwards).
 *
 * <p>The ray starts at the player's eyes and goes along getViewVector (yaw and pitch) for R = max(6, the player's
 * reach); a block's collider cuts it short, and the nearest pickable entity before that (any mob, animal or other
 * player, never a spectator or the player) is the one aimed at.
 */
public final class Aim {
	/** The ray's length when the player's reach is shorter: the network sees the threat coming before it is in reach. */
	public static final double MIN_RAY = 6.0;
	/** A hit this ready (getAttackStrengthScale) counts as a threat. */
	public static final double READY = 0.9;

	/** What a player's eyes hit: the entity's id (-1 for none), the distance to it, and what the answer was computed for. */
	private record Pick(long tick, Vec3 eye, Vec3 look, int id, double distance) {
	}

	private static final Map<Player, Pick> PICKS = new WeakHashMap<>();
	/** How many rays were really cast (tests: one per player per tick, however many mobs ask). */
	private static long casts;

	private Aim() {
	}

	/** The player's ray, cast the first time it is asked in a tick and read from the cache after that. */
	private static Pick pick(Player player) {
		long now = player.level().getGameTime();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Pick cached = PICKS.get(player);
		if (cached != null && cached.tick == now && cached.eye.equals(eye) && cached.look.equals(look)) {
			return cached;
		}
		casts++;
		double reach = Math.max(MIN_RAY, Reach.player(player));
		Vec3 end = eye.add(look.scale(reach));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double blockDistance = block.getType() == HitResult.Type.MISS ? reach : eye.distanceTo(block.getLocation());
		Vec3 to = eye.add(look.scale(blockDistance));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, to,
			player.getBoundingBox().expandTowards(look.scale(blockDistance)).inflate(1.0),
			entity -> !entity.isSpectator() && entity.isPickable() && entity != player, blockDistance * blockDistance);
		Pick pick = hit == null ? new Pick(now, eye, look, -1, 0.0)
			: new Pick(now, eye, look, hit.getEntity().getId(), eye.distanceTo(hit.getLocation()));
		PICKS.put(player, pick);
		return pick;
	}

	/** Whether the player's crosshair ray hits this entity's box before any other entity or block. */
	public static boolean aimsAt(Player player, Entity entity) {
		return pick(player).id == entity.getId();
	}

	/**
	 * The four inputs of block J for {@code mob}, into {@code out} from {@code at}: jug_apunta_mi_caja,
	 * jug_apunta_dist/6 (0 when not aimed at), jug_golpe_listo and jug_amenaza. The player is the real one.
	 */
	public static void observe(Entity mob, Player player, float[] out, int at) {
		float ready = player.getAttackStrengthScale(0.5F);
		out[at + 2] = ready;
		Pick pick = pick(player);
		if (pick.id != mob.getId()) {
			return;
		}
		out[at] = 1.0F;
		out[at + 1] = (float) ObsM1.clip(pick.distance / 6.0, 0.0, 2.0);
		out[at + 3] = pick.distance <= Reach.player(player) && ready >= READY ? 1.0F : 0.0F;
	}

	/** Rays cast so far (for the tests). */
	public static long casts() {
		return casts;
	}
}
