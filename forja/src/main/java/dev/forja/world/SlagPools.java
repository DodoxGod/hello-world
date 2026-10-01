package dev.forja.world;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ground that is still hot, and for how much longer.
 *
 * <p>The Escoria Viviente leaves a patch behind wherever one of it dies, and since it splits, killing a
 * big one in a corridor ends with you standing in the ashes of three smaller ones. That is the whole
 * fight: the ground you cleared is the ground you can no longer stand on, so where you take it on
 * matters more than what you take it on with.
 *
 * <p>Kept as a list on the server rather than as blocks in the world. A pool is a few seconds long and
 * turning the floor into fire blocks for that would set light to the room, melt whatever was on the
 * floor and leave scorch marks that outlive the fight by a very long way — which is a different and
 * much worse mob.
 */
public final class SlagPools {
	/** How often a pool bites whoever is standing in it. */
	private static final int EVERY = 10;

	private record Pool(ServerLevel level, Vec3 at, double radius, int left) {
	}

	private static final List<Pool> POOLS = new ArrayList<>();

	private SlagPools() {
	}

	/** Leaves a patch of hot ground. */
	public static void pour(ServerLevel level, Vec3 at, double radius, int ticks) {
		POOLS.add(new Pool(level, at, radius, ticks));
		// What it looks like is one entity the clients draw: the patch, its edge, low fire on it.
		dev.forja.entity.Shockwave.pool(level, at, radius, ticks, dev.forja.entity.Shockwave.EMBER, 0.32F);
	}

	/** How many are burning right now, which is what the tests ask. */
	public static int count() {
		return POOLS.size();
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (POOLS.isEmpty()) {
				return;
			}
			for (int index = POOLS.size() - 1; index >= 0; index--) {
				Pool pool = POOLS.get(index);
				if (pool.level() != level) {
					continue;
				}
				int left = pool.left() - 1;
				if (left <= 0) {
					POOLS.remove(index);
					continue;
				}
				POOLS.set(index, new Pool(level, pool.at(), pool.radius(), left));
				// Drawn by the client now; the odd puff of smoke from here is all that is still sent.
				if (left % 12 == 0) {
					draw(level, pool, left);
				}
				if (left % EVERY == 0) {
					burn(level, pool);
				}
			}
		});
	}

	/** The patch, drawn thinning as it cools so you can see how long you have. */
	private static void draw(ServerLevel level, Pool pool, int left) {
		int points = (int) (pool.radius() * 6);
		for (int step = 0; step < points; step++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double reach = Math.sqrt(level.getRandom().nextDouble()) * pool.radius();
			double x = pool.at().x + Math.cos(angle) * reach;
			double z = pool.at().z + Math.sin(angle) * reach;
			if (left % 3 == 0) {
				level.sendParticles(ParticleTypes.FLAME, x, pool.at().y + 0.1, z, 1, 0.05, 0.02, 0.05, 0.005);
			}
			if (left % 7 == 0) {
				level.sendParticles(dev.forja.registry.ModParticles.CENIZA,
					x, pool.at().y + 0.2, z, 1, 0.05, 0.05, 0.05, 0.01);
			}
		}
	}

	private static void burn(ServerLevel level, Pool pool) {
		AABB box = new AABB(pool.at(), pool.at()).inflate(pool.radius(), 1.5, pool.radius());
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
			other -> other.isAlive() && !other.fireImmune())) {
			double flat = Math.sqrt((victim.getX() - pool.at().x) * (victim.getX() - pool.at().x)
				+ (victim.getZ() - pool.at().z) * (victim.getZ() - pool.at().z));
			if (flat > pool.radius()) {
				continue;
			}
			victim.invulnerableTime = 0;
			victim.hurtServer(level, level.damageSources().onFire(), dev.forja.entity.LivingSlag.POOL_DAMAGE);
			victim.igniteForSeconds(2.0F);
		}
	}
}
