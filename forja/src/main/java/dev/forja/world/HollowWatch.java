package dev.forja.world;

import dev.forja.entity.HollowArmor;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * A workshop full of plate does not go unnoticed. At night, near a forge table, an empty suit now and
 * then stands up in the dark outside and comes looking. Only one at a time near any smith, so it stays
 * a visit rather than a siege.
 */
public final class HollowWatch {
	/** Checked once a minute per player, at night, with a table nearby. */
	public static final float CHANCE = 0.05F;

	/** How far a forge table counts as "your workshop". */
	private static final int TABLE_RANGE = 12;

	/** How far out it stands up, and how many may be around at once. */
	private static final int DISTANCE = 18;
	private static final int LIMIT = 1;

	private HollowWatch() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 1200 != 0 || !level.dimensionType().hasSkyLight() || level.isBrightOutside()) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (level.getRandom().nextFloat() < dev.forja.ForjaConfig.get().corazas && nearTable(level, player)) {
					standUp(level, player);
				}
			}
		});
	}

	/** Whether the smith is standing in their own workshop. */
	private static boolean nearTable(ServerLevel level, ServerPlayer player) {
		BlockPos at = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-TABLE_RANGE, -4, -TABLE_RANGE), at.offset(TABLE_RANGE, 4, TABLE_RANGE))) {
			if (level.getBlockState(pos).is(ModBlocks.MESA_DE_FORJA)) {
				return true;
			}
		}
		return false;
	}

	private static void standUp(ServerLevel level, ServerPlayer player) {
		if (!level.getEntitiesOfClass(HollowArmor.class, new AABB(player.position(), player.position()).inflate(48.0)).isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		BlockPos around = player.blockPosition().offset(
			(int) Math.round(Math.cos(angle) * DISTANCE), 0, (int) Math.round(Math.sin(angle) * DISTANCE)
		);
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around);
		if (!level.isLoaded(ground) || level.getBlockState(ground.below()).isAir()) {
			return;
		}
		HollowArmor armor = ModEntities.CORAZA.create(level, EntitySpawnReason.EVENT);
		if (armor == null) {
			return;
		}
		armor.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		armor.setPersistenceRequired();
		armor.setTarget(player);
		level.addFreshEntity(armor);
		player.sendSystemMessage(Component.translatable("gui.forja.coraza_despierta").withColor(0x8FD8DE));
		level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.8F, 0.6F);
	}

	/** The limit, for the book and the tests. */
	public static int limit() {
		return LIMIT;
	}
}
