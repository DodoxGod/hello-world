package dev.forja.world;

import dev.forja.entity.StarCore;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/**
 * Where the Nucleo Estelar turns up: over open ground, at night, under the sky it fell out of.
 *
 * <p>It is put outdoors and alone for one reason — it is the mob that punishes you for attacking it,
 * and that lesson only lands somewhere you have room to back off. In a corridor with something else
 * already on you, "stop swinging and walk away" is not an option, so the core would just be damage
 * you could not avoid. In a field at night it is exactly the puzzle it is meant to be.
 */
public final class CoreWatch {
	private static final int EVERY = 1400;

	/** How far out one hangs itself, how high above the ground, and how many may be about. */
	private static final int DISTANCE = 14;
	private static final int HEIGHT = 2;
	private static final int LIMIT = 1;

	private CoreWatch() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % EVERY != 0 || !level.dimensionType().hasSkyLight() || level.isBrightOutside()) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || player.isCreative()) {
					continue;
				}
				if (!level.canSeeSky(player.blockPosition())) {
					continue;
				}
				if (level.getRandom().nextFloat() < dev.forja.ForjaConfig.get().nucleos) {
					hang(level, player);
				}
			}
		});
	}

	private static void hang(ServerLevel level, ServerPlayer player) {
		AABB near = new AABB(player.position(), player.position()).inflate(64.0);
		if (!level.getEntitiesOfClass(StarCore.class, near).isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 12; attempt++) {
			BlockPos spot = player.blockPosition().offset(
				random.nextInt(DISTANCE * 2 + 1) - DISTANCE,
				HEIGHT + random.nextInt(2),
				random.nextInt(DISTANCE * 2 + 1) - DISTANCE
			);
			if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir()
				|| !level.getBlockState(spot.above()).isAir() || !level.canSeeSky(spot)) {
				continue;
			}
			StarCore core = ModEntities.NUCLEO_ESTELAR.create(level, EntitySpawnReason.EVENT);
			if (core == null) {
				return;
			}
			core.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			core.setPersistenceRequired();
			// No target on purpose. It does nothing at all until somebody hits it, and whoever that is
			// is who it owes.
			level.addFreshEntity(core);
			level.playSound(null, spot, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.9F, 1.6F);
			level.sendParticles(dev.forja.registry.ModParticles.ALMA,
				spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 24, 0.5, 0.5, 0.5, 0.02);
			return;
		}
	}
}
