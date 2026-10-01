package dev.forja.world;

import java.util.List;

import dev.forja.entity.BrokenMould;
import dev.forja.entity.Striker;
import dev.forja.entity.Tongs;
import dev.forja.entity.WalkingAnvil;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/**
 * The things a ruined workshop still has working in it.
 *
 * <p>The tongs, the striker and the walking anvil were all built in a forge, so they wake up in one:
 * only inside the mod's own workshop ruins, and only to somebody who has gone in. Wandering past a
 * ruin costs nothing; going through it for what is inside is what wakes the place up.
 *
 * <p>The anvil is the exception and the reason this is one class rather than three. It mends the other
 * two and does almost nothing on its own, so it <b>never wakes alone</b> — it turns up to a workshop
 * that already has something standing in it, which is the only arrangement where a healer is
 * interesting rather than a nuisance.
 */
public final class ConstructWatch {
	private static final int EVERY = 600;

	/** How far out one wakes, and how many may be about at once. */
	private static final int DISTANCE = 8;
	private static final int LIMIT = 4;

	private ConstructWatch() {
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
				if (!RuinMood.isWorkshop(RuinMood.ruinAt(level, player.blockPosition()))) {
					continue;
				}
				if (level.getRandom().nextFloat() < dev.forja.ForjaConfig.get().constructos) {
					wake(level, player);
				}
			}
		});
	}

	private static void wake(ServerLevel level, ServerPlayer player) {
		AABB near = new AABB(player.position(), player.position()).inflate(36.0);
		List<Mob> already = level.getEntitiesOfClass(Mob.class, near,
			mob -> mob instanceof Tongs || mob instanceof Striker || mob instanceof WalkingAnvil
				|| mob instanceof BrokenMould);
		if (already.size() >= LIMIT) {
			return;
		}
		RandomSource random = level.getRandom();
		// The anvil only turns up to a workshop that already has something in it to mend.
		boolean anvilAllowed = already.stream().anyMatch(mob -> mob instanceof Tongs || mob instanceof Striker)
			&& already.stream().noneMatch(mob -> mob instanceof WalkingAnvil);
		// And never two moulds. One is a fight about what you brought with you; two is the same fight
		// twice with the answer already known.
		boolean mouldAllowed = already.stream().noneMatch(mob -> mob instanceof BrokenMould);
		float roll = random.nextFloat();
		Mob construct;
		if (anvilAllowed && roll < 0.2F) {
			construct = ModEntities.YUNQUE_ANDANTE.create(level, EntitySpawnReason.EVENT);
		} else if (mouldAllowed && roll < 0.4F) {
			construct = ModEntities.MOLDE_ROTO.create(level, EntitySpawnReason.EVENT);
		} else if (roll < 0.72F) {
			construct = ModEntities.TENAZA.create(level, EntitySpawnReason.EVENT);
		} else {
			construct = ModEntities.PERCUTOR.create(level, EntitySpawnReason.EVENT);
		}
		if (construct == null) {
			return;
		}
		for (int attempt = 0; attempt < 14; attempt++) {
			BlockPos spot = player.blockPosition().offset(
				random.nextInt(DISTANCE * 2 + 1) - DISTANCE,
				random.nextInt(5) - 2,
				random.nextInt(DISTANCE * 2 + 1) - DISTANCE
			);
			if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir()
				|| !level.getBlockState(spot.above()).isAir()
				|| !level.getBlockState(spot.above(2)).isAir()
				|| level.getBlockState(spot.below()).isAir()) {
				continue;
			}
			construct.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			construct.setPersistenceRequired();
			construct.setTarget(player);
			level.addFreshEntity(construct);
			level.playSound(null, spot, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6F, 0.5F);
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
				spot.getX() + 0.5, spot.getY() + 0.6, spot.getZ() + 0.5, 20, 0.35, 0.5, 0.35, 0.3);
			player.sendSystemMessage(Component.translatable("gui.forja.constructo_despierta").withColor(0xFF7A1E));
			return;
		}
		construct.discard();
	}
}
