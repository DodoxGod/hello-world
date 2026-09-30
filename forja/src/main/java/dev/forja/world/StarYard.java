package dev.forja.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.forja.Forja;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * El Cementerio entre Estrellas: the Fallen Smith's own dimension (docs/HERRERO_DIMENSION.md).
 *
 * <p>One plateau alone in the void. An obsidian arena in the middle (the smith's workshop), a plain of
 * ash round it with thousands of weapons driven into it as graves, cold forges, three rivers of molten
 * metal that pour off the edge, and a sky of stars whose constellations draw weapon moulds.
 *
 * <p>Everything about the ground is a <b>pure function of the column and the world's seed</b>
 * ({@link StarYardLayout}): the generator, the tests and anything that wants to know where things are all
 * read the same numbers, and no chunk ever needs to look at its neighbour to know what goes in it. The
 * arena in the middle is the same in every world; the plateau round it is the seed's.
 */
public final class StarYard {
	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Forja.id("cementerio_estelar"));
	public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, Forja.id("cementerio_estelar"));

	/** The top block of the arena floor and the level the ash plain rolls round. */
	public static final int SURFACE = 80;
	/** Radii, from the middle of the arena out. */
	public static final double ARENA = 22.0;
	public static final double RIM = 24.0;
	public static final double APRON = 34.0;
	public static final double ROWS_END = 72.0;
	/** The ground stops rolling this far in; inside it the plateau is flat. */
	public static final double FLAT_TO = 54.0;
	/** Where the rivers rise. How many there are, where they run and where the bridges cross them is the seed's (StarYardLayout). */
	public static final double RIVER_START = 52.0;
	/** Half the width of a river's bed (3 across) and how far out its bank wall stands. */
	public static final double RIVER_HALF = 1.6;
	public static final double BANK = 2.6;
	/** The four braziers on pillars round the arena. */
	public static final double PILLAR_RING = 28.0;
	/** Where anyone arriving stands, north of the arena and looking into it. */
	public static final BlockPos ARRIVAL = new BlockPos(0, SURFACE + 1, -30);
	/** How close to the arena's edge molten metal is allowed: none nearer than this (the doc's 24 blocks). */
	public static final double MOLTEN_CLEARANCE = 24.0;

	private static final Map<UUID, Return> RETURNS = new HashMap<>();

	private record Return(ResourceKey<Level> level, Vec3 pos, float yRot, float xRot) {
	}

	private StarYard() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Forja.id("cementerio_estelar"), StarYardGenerator.CODEC);
	}

	// ------------------------------------------------------------------ the shape of the ground

	public static double radius(double x, double z) {
		return Math.sqrt(x * x + z * z);
	}

	public static double bearing(double x, double z) {
		return Math.atan2(z, x);
	}

	/** The layout of the graveyard this level holds (its generator's, drawn from the world's seed). */
	public static StarYardLayout layout(ServerLevel level) {
		if (level.getChunkSource().getGenerator() instanceof StarYardGenerator generator) {
			return generator.layout();
		}
		return StarYardLayout.of(level.getSeed());
	}

	// ------------------------------------------------------------------ noise-free randomness

	/** A well-mixed 64-bit hash of a column, a salt and the world's seed: every "random" choice on the plateau. */
	public static long hash(int x, int z, int salt, long seed) {
		long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L + seed * 0xD6E8FEB86659FD93L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		h *= 0x94D049BB133111EBL;
		h ^= h >>> 29;
		return h & Long.MAX_VALUE;
	}

	// ------------------------------------------------------------------ going there, for testing

	/** {@code /forja dimension} and {@code /forja dimension volver}. */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("dimension")
			.executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				return enter(player) ? 1 : 0;
			})
			.then(Commands.literal("volver").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				leave(player);
				return 1;
			}));
	}

	/** Sends a player to the arrival platform, looking into the arena, and remembers where they were. */
	public static boolean enter(ServerPlayer player) {
		ServerLevel yard = player.level().getServer().getLevel(LEVEL);
		if (yard == null) {
			player.sendSystemMessage(Component.translatable("commands.forja.dimension.falta"));
			return false;
		}
		if (player.level().dimension() != LEVEL) {
			RETURNS.put(player.getUUID(), new Return(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
		}
		Vec3 at = Vec3.atBottomCenterOf(ARRIVAL);
		player.teleport(new TeleportTransition(yard, at, Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
		player.sendSystemMessage(Component.translatable("commands.forja.dimension.llegas"));
		return true;
	}

	/** Back to where {@link #enter} was used from, or to the player's own respawn point. */
	public static void leave(ServerPlayer player) {
		Return back = RETURNS.remove(player.getUUID());
		ServerLevel level = back == null ? null : player.level().getServer().getLevel(back.level());
		if (level != null) {
			player.teleport(new TeleportTransition(level, back.pos(), Vec3.ZERO, back.yRot(), back.xRot(), TeleportTransition.DO_NOTHING));
		} else {
			player.teleport(TeleportTransition.createDefault(player, TeleportTransition.DO_NOTHING));
		}
	}
}
