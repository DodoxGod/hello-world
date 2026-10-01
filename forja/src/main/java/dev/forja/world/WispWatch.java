package dev.forja.world;

import dev.forja.entity.EmberWisp;
import dev.forja.registry.ModBlocks;
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
import net.minecraft.world.phys.AABB;

/**
 * A forge that has been running all night draws them.
 *
 * <p>Where the empty suits come at the smith who owns a workshop, wisps come at the fire itself: they
 * only ever turn up where something is actually burning, and they turn up in twos and threes because
 * one alone is barely a fight. The forge that fed them is also what makes them dangerous, so the choice
 * they put to you is whether to back away from your own hearth to deal with them.
 */
public final class WispWatch {
	/** Checked once a minute per player, with something lit nearby. */
	public static final float CHANCE = 0.08F;

	/** How far it looks for the fire that drew them. */
	private static final int HEAT_RANGE = 10;

	/** How far out they turn up, how high, and how many may be around at once. */
	private static final int DISTANCE = 10;
	private static final int HEIGHT = 5;
	private static final int LIMIT = 3;

	private WispWatch() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 1200 != 600) {
				return;
			}
			boolean raining = WorldEvents.active(level) == WorldEvents.LLUVIA_DE_PAVESAS;
			float chance = dev.forja.ForjaConfig.get().pavesas * (raining ? 3.0F : 1.0F);
			for (ServerPlayer player : level.players()) {
				// An ember rain brings them whether or not there is a fire to come for.
				if (level.getRandom().nextFloat() < chance && (raining || nearHeat(level, player))) {
					drawIn(level, player);
				}
			}
		});
	}

	/** Whether there is a fire close enough to be worth coming for. */
	private static boolean nearHeat(ServerLevel level, ServerPlayer player) {
		BlockPos at = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-HEAT_RANGE, -4, -HEAT_RANGE), at.offset(HEAT_RANGE, 4, HEAT_RANGE))) {
			var state = level.getBlockState(pos);
			// The caught one calls the rest in. It is the price of not having to haul lava: a workshop
			// with a wisp lantern in it is a workshop wisps keep visiting.
			if (state.is(ModBlocks.MESA_DE_FORJA) || state.is(ModBlocks.FAROL_DE_PAVESA)
				|| state.is(net.minecraft.world.level.block.Blocks.LAVA)
				|| state.is(net.minecraft.world.level.block.Blocks.FIRE)
				|| state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)) {
				return true;
			}
		}
		return false;
	}

	private static void drawIn(ServerLevel level, ServerPlayer player) {
		if (!level.getEntitiesOfClass(EmberWisp.class, new AABB(player.position(), player.position()).inflate(48.0)).isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		// Now and then what comes instead is one big one. A greater ember splits into five wisps when
		// it dies, so this is the same swarm arriving in the wrong order: all at once if you are slow
		// about it, and never at all if you deal with it at range.
		if (random.nextFloat() < GREATER_CHANCE && greater(level, player, random)) {
			return;
		}
		int came = 0;
		for (int i = 0; i < LIMIT; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			BlockPos around = player.blockPosition().offset(
				(int) Math.round(Math.cos(angle) * DISTANCE), HEIGHT, (int) Math.round(Math.sin(angle) * DISTANCE)
			);
			if (!level.isLoaded(around) || !level.getBlockState(around).isAir()) {
				continue;
			}
			EmberWisp wisp = ModEntities.PAVESA.create(level, EntitySpawnReason.EVENT);
			if (wisp == null) {
				continue;
			}
			wisp.snapTo(around.getX() + 0.5, around.getY(), around.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			wisp.setTarget(player);
			// Ones that fall out of an ember rain arrive already burning.
			if (WorldEvents.active(level) == WorldEvents.LLUVIA_DE_PAVESAS) {
				wisp.stoke();
			}
			level.addFreshEntity(wisp);
			came++;
		}
		if (came == 0) {
			return;
		}
		player.sendSystemMessage(Component.translatable("gui.forja.pavesas_llegan").withColor(0xFFA83E));
		level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2F, 1.4F);
	}

	/** How often the thing that turns up is one greater ember rather than a handful of wisps. */
	public static final float GREATER_CHANCE = 0.12F;

	/** Brings one greater ember instead of the swarm. True if it managed to. */
	private static boolean greater(ServerLevel level, ServerPlayer player, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		BlockPos around = player.blockPosition().offset(
			(int) Math.round(Math.cos(angle) * DISTANCE), HEIGHT, (int) Math.round(Math.sin(angle) * DISTANCE)
		);
		if (!level.isLoaded(around) || !level.getBlockState(around).isAir()) {
			return false;
		}
		var ember = ModEntities.ASCUA_MAYOR.create(level, EntitySpawnReason.EVENT);
		if (ember == null) {
			return false;
		}
		ember.snapTo(around.getX() + 0.5, around.getY(), around.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		ember.setTarget(player);
		ember.setPersistenceRequired();
		level.addFreshEntity(ember);
		player.sendSystemMessage(Component.translatable("gui.forja.ascua_mayor").withColor(0xFF7A1E));
		level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.6F);
		return true;
	}

	/** How many can be drawn in at once, for the book and the tests. */
	public static int limit() {
		return LIMIT;
	}
}
