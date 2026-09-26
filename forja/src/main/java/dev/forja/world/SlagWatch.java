package dev.forja.world;

import dev.forja.entity.LivingSlag;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Where the Escoria Viviente gets up.
 *
 * <p>Next to lava, underground, and only where there is enough of it: one block of it is a puddle, a
 * pool of it is a place where something could have been skimmed off and left. So this counts the lava
 * around a player and does nothing until there is a real pool of it nearby.
 *
 * <p>That makes it a hazard of a place rather than a hazard of a depth. Lava is already the thing
 * everybody is careful around down there; this gives the care a second reason, and it puts the only
 * source of escoria somewhere you were going to have to be careful anyway.
 */
public final class SlagWatch {
	private static final int EVERY = 1200;

	/** How far it looks for lava, and how much it has to find. */
	private static final int LAVA_RANGE = 8;
	private static final int LAVA_NEEDED = 12;

	/** How far out one gets up, and how many may be about at once. */
	private static final int DISTANCE = 7;
	private static final int LIMIT = 3;

	private SlagWatch() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % EVERY != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || player.isCreative()) {
					continue;
				}
				if (level.getRandom().nextFloat() < dev.forja.ForjaConfig.get().escorias && lava(level, player)) {
					rise(level, player);
				}
			}
		});
	}

	/** Whether there is a real pool of lava about, rather than a block of it. */
	private static boolean lava(ServerLevel level, ServerPlayer player) {
		BlockPos at = player.blockPosition();
		int found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-LAVA_RANGE, -5, -LAVA_RANGE), at.offset(LAVA_RANGE, 3, LAVA_RANGE))) {
			if (level.getBlockState(pos).is(Blocks.LAVA) && ++found >= LAVA_NEEDED) {
				return true;
			}
		}
		return false;
	}

	private static void rise(ServerLevel level, ServerPlayer player) {
		AABB near = new AABB(player.position(), player.position()).inflate(40.0);
		if (level.getEntitiesOfClass(LivingSlag.class, near).size() >= LIMIT) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 12; attempt++) {
			BlockPos spot = player.blockPosition().offset(
				random.nextInt(DISTANCE * 2 + 1) - DISTANCE,
				random.nextInt(4) - 1,
				random.nextInt(DISTANCE * 2 + 1) - DISTANCE
			);
			if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir()
				|| !level.getBlockState(spot.above()).isAir() || level.getBlockState(spot.below()).isAir()) {
				continue;
			}
			LivingSlag slag = ModEntities.ESCORIA.create(level, EntitySpawnReason.EVENT);
			if (slag == null) {
				return;
			}
			// Always a whole one. The halves are something you make by killing it, not something the
			// world hands you — otherwise the split stops meaning anything.
			slag.setSize(LivingSlag.BIG);
			slag.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			slag.setTarget(player);
			level.addFreshEntity(slag);
			level.playSound(null, spot, SoundEvents.LAVA_POP, SoundSource.HOSTILE, 1.6F, 0.5F);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
				spot.getX() + 0.5, spot.getY() + 0.3, spot.getZ() + 0.5, 14, 0.3, 0.2, 0.3, 0.05);
			return;
		}
	}
}
