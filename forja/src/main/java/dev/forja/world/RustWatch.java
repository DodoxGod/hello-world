package dev.forja.world;

import dev.forja.entity.RustSwarm;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Where the Herrumbre comes from.
 *
 * <p>It eats metal, so it lives where metal is: down in the dark, and only to somebody who has some.
 * A player in leather walks past a nest and never knows it was there; a player in a full set of forged
 * plate is carrying a meal through it.
 *
 * <p>That gating is the whole design. A swarm that turns up regardless would be a tax on going
 * underground, which everybody has to do. One that only turns up for the well-equipped is a cost
 * attached to the thing the mod is actually about, and it can be dodged by taking the good plate off
 * — which is a decision, and a slightly ridiculous one, and therefore a good one.
 */
public final class RustWatch {
	/** Checked once a minute per player. */
	private static final int EVERY = 1200;

	/** Only down where the ore is. */
	private static final int CEILING = 42;

	/** And only in the dark, which is where rust is left alone long enough to get up. */
	private static final int DARK = 7;

	/** How far out the nest opens, and how many swarms may be near one player at once. */
	private static final int DISTANCE = 9;
	private static final int LIMIT = 6;

	/** How much forged metal a player has to be carrying to be worth the trip. */
	private static final int WORTH_IT = 2;

	private RustWatch() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % EVERY != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || player.isCreative() || player.getY() > CEILING) {
					continue;
				}
				if (level.getMaxLocalRawBrightness(player.blockPosition()) > DARK) {
					continue;
				}
				if (wearing(player) < WORTH_IT) {
					continue;
				}
				if (level.getRandom().nextFloat() < dev.forja.ForjaConfig.get().herrumbres) {
					nest(level, player);
				}
			}
		});
	}

	/** How many pieces of forged metal the player has on. */
	private static int wearing(ServerPlayer player) {
		int count = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
		}) {
			ItemStack worn = player.getItemBySlot(slot);
			if (!worn.isEmpty() && worn.has(dev.forja.registry.ModComponents.PARTS) && !worn.isBroken()) {
				count++;
			}
		}
		return count;
	}

	private static void nest(ServerLevel level, ServerPlayer player) {
		AABB near = new AABB(player.position(), player.position()).inflate(32.0);
		if (level.getEntitiesOfClass(RustSwarm.class, near).size() >= LIMIT) {
			return;
		}
		RandomSource random = level.getRandom();
		int pack = RustSwarm.PACK_MIN + random.nextInt(RustSwarm.PACK_MAX - RustSwarm.PACK_MIN + 1);
		int placed = 0;
		for (int attempt = 0; attempt < pack * 4 && placed < pack; attempt++) {
			BlockPos spot = player.blockPosition().offset(
				random.nextInt(DISTANCE * 2 + 1) - DISTANCE,
				random.nextInt(5) - 2,
				random.nextInt(DISTANCE * 2 + 1) - DISTANCE
			);
			// It comes out of a floor, not out of the air: solid under it, room above it.
			if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir()
				|| !level.getBlockState(spot.above()).isAir() || level.getBlockState(spot.below()).isAir()) {
				continue;
			}
			RustSwarm swarm = ModEntities.HERRUMBRE.create(level, EntitySpawnReason.EVENT);
			if (swarm == null) {
				return;
			}
			swarm.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			swarm.setTarget(player);
			level.addFreshEntity(swarm);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
				spot.getX() + 0.5, spot.getY() + 0.2, spot.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.02);
			placed++;
		}
		if (placed > 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SILVERFISH_AMBIENT, SoundSource.HOSTILE, 0.8F, 0.6F);
		}
	}
}
