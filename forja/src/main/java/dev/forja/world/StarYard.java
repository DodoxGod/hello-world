package dev.forja.world;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.forja.Forja;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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
	/** The middle of the lit portal back, behind the arrival platform (the frame reaches 2 round it). */
	public static final BlockPos RETURN_WELL = new BlockPos(0, SURFACE, -37);
	/** How close to the arena's edge molten metal is allowed: none nearer than this (the doc's 24 blocks). */
	public static final double MOLTEN_CLEARANCE = 24.0;

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

	// ------------------------------------------------------------------ going and coming back

	/**
	 * Where a player came into the graveyard from: a dimension, a place and a heading. Kept on the player
	 * (and through death), so the way back survives a log-out, a restart and a death in the fight.
	 */
	public record Return(String level, double x, double y, double z, float yRot) {
		public static final Codec<Return> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("dimension").forGetter(Return::level),
			Codec.DOUBLE.fieldOf("x").forGetter(Return::x),
			Codec.DOUBLE.fieldOf("y").forGetter(Return::y),
			Codec.DOUBLE.fieldOf("z").forGetter(Return::z),
			Codec.FLOAT.fieldOf("yaw").forGetter(Return::yRot)
		).apply(instance, Return::new));
	}

	@SuppressWarnings("deprecation")
	public static final AttachmentType<Return> RETURN = AttachmentRegistry.<Return>builder()
		.persistent(Return.CODEC)
		.copyOnDeath()
		.buildAndRegister(Forja.id("vuelta_estelar"));

	/** {@code /forja dimension} and {@code /forja dimension volver}, for testing. */
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

	/** Remembers where this player is coming from, unless they are already in the graveyard. */
	public static void remember(ServerPlayer player, Vec3 at, float yRot) {
		if (player.level().dimension() != LEVEL) {
			player.setAttached(RETURN, new Return(player.level().dimension().identifier().toString(), at.x, at.y, at.z, yRot));
		}
	}

	/** Where a player arrives in the graveyard: the arrival platform, looking into the arena. */
	public static @org.jspecify.annotations.Nullable TeleportTransition arrival(MinecraftServer server, TeleportTransition.PostTeleportTransition after) {
		ServerLevel yard = server.getLevel(LEVEL);
		if (yard == null) {
			return null;
		}
		return new TeleportTransition(yard, Vec3.atBottomCenterOf(ARRIVAL), Vec3.ZERO, 0.0F, 0.0F, after);
	}

	/** Where a player leaving the graveyard goes: back where they came in, or to their own respawn point. */
	public static TeleportTransition departure(ServerPlayer player, TeleportTransition.PostTeleportTransition after) {
		Return back = player.getAttached(RETURN);
		if (back != null) {
			ServerLevel level = player.level().getServer().getLevel(
				ResourceKey.create(Registries.DIMENSION, net.minecraft.resources.Identifier.parse(back.level())));
			if (level != null) {
				return new TeleportTransition(level, new Vec3(back.x(), back.y(), back.z()), Vec3.ZERO, back.yRot(), 0.0F, after);
			}
		}
		return TeleportTransition.createDefault(player, after);
	}

	/** Sends a player to the arrival platform, and remembers where they were. */
	public static boolean enter(ServerPlayer player) {
		TeleportTransition there = arrival(player.level().getServer(), TeleportTransition.DO_NOTHING);
		if (there == null) {
			player.sendSystemMessage(Component.translatable("commands.forja.dimension.falta"));
			return false;
		}
		remember(player, player.position(), player.getYRot());
		player.teleport(there);
		player.sendSystemMessage(Component.translatable("commands.forja.dimension.llegas"));
		return true;
	}

	/** Back to where the player came into the graveyard from, or to their own respawn point. */
	public static void leave(ServerPlayer player) {
		player.teleport(departure(player, TeleportTransition.DO_NOTHING));
	}
}
