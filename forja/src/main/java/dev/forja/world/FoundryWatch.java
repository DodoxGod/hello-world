package dev.forja.world;

import java.util.List;

import dev.forja.entity.CoalHauler;
import dev.forja.entity.Quencher;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/**
 * The two that came in with the fire: the Cargador de Carbon and the Templador.
 *
 * <p>They turn up together on purpose. Separately neither is much of a fight — the hauler is a fuse
 * with legs that you beat by walking out of a circle, and the quencher does no damage at all. Put
 * together they are the mod's only pincer: the quencher stands back and washes the combo off you while
 * the hauler closes, so the punishment for dealing with the loud one first is that the quiet one has
 * been taking your build-up apart the whole time.
 *
 * <p>They come to <b>a lit forge</b> rather than to a depth or a darkness. Somewhere in the world a
 * player has a melting tank running or an anvil hot, and that is what they walk toward, which makes
 * them the only mob in the mod that is a consequence of what you built rather than of where you went.
 */
public final class FoundryWatch {
	private static final int EVERY = 1000;

	/** How far it looks for a working forge, and how much of one it has to find. */
	private static final int FORGE_RANGE = 10;
	private static final int FORGE_NEEDED = 2;

	/** How far out they arrive, and how many may be about at once. */
	private static final int DISTANCE = 12;
	private static final int LIMIT = 3;

	private FoundryWatch() {
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
				if (level.getRandom().nextFloat() < dev.forja.ForjaConfig.get().fundicion && forge(level, player)) {
					arrive(level, player);
				}
			}
		});
	}

	/** Whether there is a forge running here rather than a furnace somebody left in a wall. */
	private static boolean forge(ServerLevel level, ServerPlayer player) {
		BlockPos at = player.blockPosition();
		int found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-FORGE_RANGE, -4, -FORGE_RANGE), at.offset(FORGE_RANGE, 4, FORGE_RANGE))) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (state.is(dev.forja.registry.ModBlocks.MESA_DE_FORJA)
				|| state.is(dev.forja.registry.ModBlocks.MESA_DE_FORJA_MAYOR)
				|| state.is(dev.forja.registry.ModBlocks.CUBA_DE_COLADA)
				|| state.is(dev.forja.registry.ModBlocks.YUNQUE_DEL_HERRERO)) {
				if (++found >= FORGE_NEEDED) {
					return true;
				}
			}
		}
		return false;
	}

	private static void arrive(ServerLevel level, ServerPlayer player) {
		AABB near = new AABB(player.position(), player.position()).inflate(48.0);
		List<Mob> already = level.getEntitiesOfClass(Mob.class, near,
			mob -> mob instanceof CoalHauler || mob instanceof Quencher);
		if (already.size() >= LIMIT) {
			return;
		}
		RandomSource random = level.getRandom();
		// The quencher never arrives on his own. He is support, and support with nothing to support is
		// just a man with a cart standing in a field.
		boolean haulerHere = already.stream().anyMatch(mob -> mob instanceof CoalHauler);
		boolean quencher = haulerHere && already.stream().noneMatch(mob -> mob instanceof Quencher)
			&& random.nextFloat() < 0.5F;
		Mob arrival = quencher
			? ModEntities.TEMPLADOR.create(level, EntitySpawnReason.EVENT)
			: ModEntities.CARGADOR_DE_CARBON.create(level, EntitySpawnReason.EVENT);
		if (arrival == null) {
			return;
		}
		for (int attempt = 0; attempt < 16; attempt++) {
			BlockPos spot = player.blockPosition().offset(
				random.nextInt(DISTANCE * 2 + 1) - DISTANCE,
				random.nextInt(5) - 2,
				random.nextInt(DISTANCE * 2 + 1) - DISTANCE
			);
			if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir()
				|| !level.getBlockState(spot.above()).isAir()
				|| level.getBlockState(spot.below()).isAir()) {
				continue;
			}
			arrival.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			arrival.setPersistenceRequired();
			arrival.setTarget(player);
			level.addFreshEntity(arrival);
			level.playSound(null, spot, SoundEvents.HOGLIN_ANGRY, SoundSource.HOSTILE, 1.4F, 0.6F);
			level.sendParticles(dev.forja.registry.ModParticles.CENIZA,
				spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 16, 0.4, 0.5, 0.4, 0.04);
			return;
		}
		arrival.discard();
	}
}
