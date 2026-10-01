package dev.forja.world;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ground the Templador has thrown his quenching oil over, and for how much longer.
 *
 * <p>The mirror of {@link SlagPools}, and deliberately so. That one makes a patch of floor you cannot
 * stand on; this one makes a patch of floor where <b>nothing you have built up counts</b>. Standing
 * in it costs no health at all — it puts your fire out, empties a voltaic weapon and wipes the combo,
 * over and over, for as long as you are in it.
 *
 * <p>Which is why it is a pool and not a hit. A single throw that wiped a combo would just be an
 * annoyance you eat and rebuild through; a patch of quenched ground is a piece of the room you have
 * to fight around, and moving out of it is a decision with a cost, because it is normally thrown over
 * the thing you were trying to reach.
 */
public final class OilPools {
	/** How often a pool washes down whoever is standing in it. */
	private static final int EVERY = 8;

	/** The dull green of cold quenching oil. */
	private static final net.minecraft.core.particles.DustParticleOptions OIL =
		new net.minecraft.core.particles.DustParticleOptions(0x3E4C40, 1.1F);

	private record Pool(ServerLevel level, Vec3 at, double radius, int left) {
	}

	private static final List<Pool> POOLS = new ArrayList<>();

	private OilPools() {
	}

	/** Leaves a patch of quenched ground. */
	public static void pour(ServerLevel level, Vec3 at, double radius, int ticks) {
		POOLS.add(new Pool(level, at, radius, ticks));
		// The slick itself, drawn by the clients: no flame on it, because it is not a fire (yet).
		dev.forja.entity.Shockwave.pool(level, at, radius, ticks, dev.forja.entity.Shockwave.SLICK, 0.0F);
		// The throw itself puts out whatever was already alight where it lands, which is what makes
		// the Templador worth having on the other side's payroll.
		douseGround(level, at, radius);
	}

	/** How many are lying about right now, which is what the tests ask. */
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
				draw(level, pool, left);
				if (left % EVERY == 0) {
					wash(level, pool);
				}
			}
		});
	}

	/** The patch: a slick that thins as it dries, so you can see how long you have to avoid it. */
	private static void draw(ServerLevel level, Pool pool, int left) {
		int points = (int) (pool.radius() * 5);
		for (int step = 0; step < points; step++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double reach = Math.sqrt(level.getRandom().nextDouble()) * pool.radius();
			double x = pool.at().x + Math.cos(angle) * reach;
			double z = pool.at().z + Math.sin(angle) * reach;
			if (left % 4 == 0) {
				level.sendParticles(OIL, x, pool.at().y + 0.08, z, 1, 0.05, 0.01, 0.05, 0.0);
			}
			if (left % 11 == 0) {
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
					x, pool.at().y + 0.15, z, 1, 0.04, 0.02, 0.04, 0.004);
			}
		}
	}

	/** What the oil takes off whatever is standing in it. */
	private static void wash(ServerLevel level, Pool pool) {
		AABB box = new AABB(pool.at(), pool.at()).inflate(pool.radius(), 1.5, pool.radius());
		for (LivingEntity soaked : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			double flat = Math.sqrt((soaked.getX() - pool.at().x) * (soaked.getX() - pool.at().x)
				+ (soaked.getZ() - pool.at().z) * (soaked.getZ() - pool.at().z));
			if (flat > pool.radius()) {
				continue;
			}
			quench(soaked);
		}
	}

	/**
	 * One washing down: fire out, charge out, combo out. No damage anywhere in here on purpose — the
	 * Templador is the one mob you can stand next to all day and still lose to.
	 */
	public static int quench(LivingEntity soaked) {
		soaked.clearFire();
		int lost = dev.forja.upgrade.Frenzy.douse(soaked);
		net.minecraft.world.item.ItemStack weapon = soaked.getMainHandItem();
		if (weapon.getOrDefault(dev.forja.registry.ModComponents.CARGA, 0) > 0) {
			weapon.set(dev.forja.registry.ModComponents.CARGA, 0);
			lost++;
		}
		return lost;
	}

	/** Fire blocks inside the splash, put out. */
	private static void douseGround(ServerLevel level, Vec3 at, double radius) {
		int span = (int) Math.ceil(radius);
		BlockPos middle = BlockPos.containing(at);
		for (int dx = -span; dx <= span; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -span; dz <= span; dz++) {
					if (dx * dx + dz * dz > radius * radius) {
						continue;
					}
					BlockPos spot = middle.offset(dx, dy, dz);
					if (level.getBlockState(spot).is(Blocks.FIRE)) {
						level.removeBlock(spot, false);
						level.levelEvent(null, 1009, spot, 0);
					}
				}
			}
		}
	}
}
