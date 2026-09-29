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
 * <p>Everything about the ground is a <b>pure function of the column</b> ({@link #surface},
 * {@link #riverDistance}…): the generator, the tests and anything that wants to know where the arena is
 * all read the same numbers, and no chunk ever needs to look at its neighbour to know what goes in it.
 * The layout is the same in every world on purpose: it is one arena, not a landscape to explore.
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
	/** Where the rivers rise, and the rings where bridges cross them. */
	public static final double RIVER_START = 52.0;
	public static final double[] BRIDGES = {76.0, 112.0};
	/** Half the width of a river's bed (3 across) and how far out its bank wall stands. */
	public static final double RIVER_HALF = 1.6;
	public static final double BANK = 2.6;
	/** The three rivers' bearings, in radians from +X towards +Z. */
	public static final double[] RIVERS = {Math.toRadians(30.0), Math.toRadians(150.0), Math.toRadians(270.0)};
	/** The four braziers on pillars round the arena. */
	public static final double PILLAR_RING = 28.0;
	/** Where anyone arriving stands, north of the arena and looking into it. */
	public static final BlockPos ARRIVAL = new BlockPos(0, SURFACE + 1, -30);
	/** How close to the arena's edge molten metal is allowed: none nearer than this (the doc's 24 blocks). */
	public static final double MOLTEN_CLEARANCE = 24.0;

	/** The islets that hang round the plateau: x, z, top y, radius. What the far view is made of. */
	public static final int[][] ISLETS = {
		{205, 40, 70, 14}, {-150, 140, 96, 10}, {35, 215, 58, 18}, {-210, -50, 110, 12},
		{125, -175, 84, 16}, {-50, -225, 64, 9}, {215, 150, 118, 8}
	};

	private static final Map<UUID, Return> RETURNS = new HashMap<>();

	private record Return(ResourceKey<Level> level, Vec3 pos, float yRot, float xRot) {
	}

	private StarYard() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Forja.id("cementerio_estelar"), StarYardGenerator.CODEC);
	}

	// ------------------------------------------------------------------ the shape of the ground

	/** How far the plateau reaches along this bearing: 124 to 176, with capes and bays. */
	public static double edge(double theta) {
		return 150.0 + 14.0 * Math.sin(3.0 * theta + 1.1) + 8.0 * Math.sin(7.0 * theta + 2.3) + 4.0 * Math.sin(13.0 * theta + 0.4);
	}

	public static double radius(double x, double z) {
		return Math.sqrt(x * x + z * z);
	}

	public static double bearing(double x, double z) {
		return Math.atan2(z, x);
	}

	/** Whether this column is part of the plateau at all. */
	public static boolean onPlateau(int x, int z) {
		double cx = x + 0.5;
		double cz = z + 0.5;
		return radius(cx, cz) < edge(bearing(cx, cz));
	}

	/**
	 * The top block of the plateau in this column: flat at {@link #SURFACE} over the arena and the
	 * apron, then a slow roll of ±3 in the ash that has fully arrived by {@link #FLAT_TO}.
	 */
	public static int surface(int x, int z) {
		double cx = x + 0.5;
		double cz = z + 0.5;
		double r = radius(cx, cz);
		if (r <= APRON) {
			return SURFACE;
		}
		double roll = 1.6 * Math.sin(cx * 0.052 + 0.7) * Math.cos(cz * 0.047)
			+ 1.1 * Math.sin((cx + cz) * 0.09) + 0.8 * Math.sin((cx - cz) * 0.13 + 1.3);
		double share = Mth.clamp((r - APRON) / (FLAT_TO - APRON), 0.0, 1.0);
		return SURFACE + (int) Math.round(roll * share);
	}

	/** The lowest block of the plateau under this column: thick in the middle, a crust at the edge. */
	public static int bottom(int x, int z) {
		double cx = x + 0.5;
		double cz = z + 0.5;
		double r = radius(cx, cz);
		double reach = edge(bearing(cx, cz));
		double inner = Math.max(0.0, 1.0 - (r / reach) * (r / reach));
		int depth = 4 + (int) Math.round(64.0 * Math.pow(inner, 0.8)) + (int) (hash(x, z, 11) & 3L);
		// Basalt teeth hanging off the underside, one column in fifty.
		if ((hash(x, z, 12) % 50L) == 0L) {
			depth += 4 + (int) (hash(x, z, 13) % 9L);
		}
		return SURFACE - depth;
	}

	// ------------------------------------------------------------------ rivers

	/** How far river {@code k} has wandered sideways from its bearing at distance {@code r}, in blocks. */
	private static double riverSway(int k, double r) {
		return 9.0 * Math.sin(r / 19.0 + 1.3 * k) + 4.0 * Math.sin(r / 8.5 + 2.0 * k);
	}

	private static double riverSlope(int k, double r) {
		return 9.0 / 19.0 * Math.cos(r / 19.0 + 1.3 * k) + 4.0 / 8.5 * Math.cos(r / 8.5 + 2.0 * k);
	}

	/**
	 * How far this point is from the middle of the nearest river, across the river, in blocks. Rivers
	 * run from {@link #RIVER_START} out past the edge; anywhere else is {@code Double.MAX_VALUE}.
	 */
	public static double riverDistance(double x, double z) {
		double r = radius(x, z);
		if (r < RIVER_START) {
			return Double.MAX_VALUE;
		}
		double theta = bearing(x, z);
		double best = Double.MAX_VALUE;
		for (int k = 0; k < RIVERS.length; k++) {
			double off = Mth.wrapDegrees(Math.toDegrees(theta - RIVERS[k])) * Mth.DEG_TO_RAD * r - riverSway(k, r);
			double slope = riverSlope(k, r);
			best = Math.min(best, Math.abs(off) / Math.sqrt(1.0 + slope * slope));
		}
		return best;
	}

	/** Where river {@code k} rises: a small basin on its line, {@link #RIVER_START} out. */
	public static Vec3 spring(int k) {
		double theta = RIVERS[k] + riverSway(k, RIVER_START) / RIVER_START;
		return new Vec3(Math.cos(theta) * RIVER_START, SURFACE, Math.sin(theta) * RIVER_START);
	}

	/** The middle of river {@code k} at distance {@code r} from the arena's centre, at the plateau's level. */
	public static Vec3 riverPoint(int k, double r) {
		double theta = RIVERS[k] + riverSway(k, r) / r;
		return new Vec3(Math.cos(theta) * r, SURFACE, Math.sin(theta) * r);
	}

	/** Whether this point is on one of the bridges' decks (a ring 5 wide where it crosses a river). */
	public static boolean onBridge(double x, double z) {
		double r = radius(x, z);
		for (double ring : BRIDGES) {
			if (Math.abs(r - ring) < 2.5) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ noise-free randomness

	/** A well-mixed 64-bit hash of a column and a salt: every "random" choice on the plateau. */
	public static long hash(int x, int z, int salt) {
		long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
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
