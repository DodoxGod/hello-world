package dev.forja.world;

import java.util.Locale;

import dev.forja.upgrade.Upgrade;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Eventos: nine things that happen to the world on their own, each carrying one upgrade that cannot be
 * had any other way. While an event is running, an empty jar held up under the open sky catches it and
 * keeps it, and pouring the full jar out gives the orb, which goes on the star like any other.
 *
 * <p>Only one event runs at a time and they are rare, so a jar on a shelf is a story about the night
 * you were outside when it happened — which is why the jar is glass and takes the colour of what is
 * in it rather than turning straight into an orb you cannot tell apart from any other.
 */
public enum WorldEvents implements net.minecraft.util.StringRepresentable {
	/** Meteor shower: star iron comes down with it, and Lluvia estelar with that. */
	METEORITOS(Upgrade.LLUVIA_ESTELAR, 0xBFE8FF),
	/** Arcane storm: the sky goes hard and quick, and leaves Conductor behind. */
	TORMENTA_ARCANA(Upgrade.CONDUCTOR, 0xFFE45C),
	/** Soul fog: the dead drift through, and what they leave is Siega de almas. */
	NIEBLA_DE_ALMAS(Upgrade.SIEGA_DE_ALMAS, 0x6BC7C7),
	/** Aurora: lights over the world, and the calm of Aurora in the flask. */
	AURORA(Upgrade.AURORA, 0x9FE2BF),
	/** Blood moon: everything out there is braver tonight, and the blade learns from it. */
	LUNA_DE_SANGRE(Upgrade.CARNICERO, 0xB3241F),
	/** Eclipse: the light goes wrong, and what you kill covers you for a moment. */
	ECLIPSE(Upgrade.SOMBRA_LARGA, 0x4A3B5A),
	/** Blizzard: the cold gets into the plate and stays there as Tempano. */
	VENTISCA(Upgrade.TEMPANO, 0xD8F0FF),
	/** Spring tide: the water pulls at everything, and the blade learns to pull with it. */
	MAREA_VIVA(Upgrade.RESACA, 0x3FBFD0),
	/** Ember rain: the sky itself is on fire, everything with a hearth draws wisps, and Rescoldo falls. */
	LLUVIA_DE_PAVESAS(Upgrade.RESCOLDO, 0xFFA83E);

	/** Written into the jar that caught it, and sent to the client so the jar can show its colour. */
	public static final com.mojang.serialization.Codec<WorldEvents> CODEC =
		net.minecraft.util.StringRepresentable.fromEnum(WorldEvents::values);
	public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, WorldEvents> STREAM_CODEC =
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

	/** How long an event lasts, in ticks. */
	/** The white-hot head of a falling meteorite. */
	private static final net.minecraft.core.particles.DustParticleOptions HEAD =
		new net.minecraft.core.particles.DustParticleOptions(0xFFF4E0, 2.4F);

	public static final int DURATION = 6000;

	/** The chance per minute that an event starts when none is running. */
	public static final float CHANCE = 0.02F;

	public final Upgrade upgrade;
	public final int color;

	WorldEvents(Upgrade upgrade, int color) {
		this.upgrade = upgrade;
		this.color = color;
	}

	private static @Nullable WorldEvents current;
	private static long endsAt;

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public Component displayName() {
		return Component.translatable("evento.forja." + this.id());
	}

	@Override
	public String getSerializedName() {
		return this.id();
	}

	/** The event running right now, if any. */
	public static @Nullable WorldEvents active(ServerLevel level) {
		return current != null && level.getGameTime() < endsAt ? current : null;
	}

	/** How much longer the running event has, in ticks, or zero if none is. */
	public static int ticksLeft(ServerLevel level) {
		return current == null ? 0 : (int) Math.max(0L, endsAt - level.getGameTime());
	}

	/** Ends whatever is running, which only the command and the tests need. */
	public static void stop() {
		current = null;
		endsAt = 0L;
	}

	/** Ends it and tells everyone, so the sky goes back to normal instead of staying lit. */
	public static void stop(ServerLevel level) {
		stop();
		EventSky.sendAll(level, null, 0);
	}

	/** Starts an event by hand, which is what the command and the tests use. */
	public static void start(ServerLevel level, WorldEvents event) {
		current = event;
		endsAt = level.getGameTime() + DURATION;
		EventSky.sendAll(level, event, DURATION);
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(Component.translatable("gui.forja.evento", event.displayName()).withColor(event.color));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 0.4F, 1.6F);
		}
		if (event == METEORITOS) {
			for (ServerPlayer player : level.players()) {
				meteor(level, player);
			}
		}
	}

	/**
	 * A meteorite on its way down, and how many ticks it has left.
	 *
	 * <p>The crater used to appear out of nothing: a bang, a hole in the ground and some star iron in
	 * it, with the sky never mentioned. Something falling out of the sky should be **seen falling** —
	 * it is the rarest thing the mod does and it lasted one tick.
	 */
	private record Falling(ServerLevel level, BlockPos ground, int left, dev.forja.entity.Shockwave mark) {
	}

	private static final java.util.List<Falling> INBOUND = new java.util.ArrayList<>();

	/** How long a meteorite is visible on its way in. */
	private static final int FALL_TICKS = 34;

	/** How high up it comes into view. */
	private static final int FALL_HEIGHT = 48;

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			long time = level.getGameTime();
			descend(level);
			if (time % 1200 != 0 || level.players().isEmpty()) {
				return;
			}
			if (active(level) == null) {
				// It may have just run out. `active` goes null on its own when the clock passes, so
				// this is the one place that notices, and the sky has to be told or it stays lit.
				if (current != null) {
					stop(level);
				}
				if (level.getRandom().nextFloat() < dev.forja.ForjaConfig.get().eventos) {
					start(level, values()[level.getRandom().nextInt(values().length)]);
				}
				return;
			}
			// While it runs, the sky says so wherever anyone is standing.
			WorldEvents event = active(level);
			for (ServerPlayer player : level.players()) {
				level.sendParticles(
					switch (event) {
						case NIEBLA_DE_ALMAS -> ParticleTypes.SOUL;
						case AURORA -> ParticleTypes.END_ROD;
						case TORMENTA_ARCANA -> ParticleTypes.ELECTRIC_SPARK;
						case LUNA_DE_SANGRE -> ParticleTypes.DUST_PLUME;
						case ECLIPSE -> ParticleTypes.SQUID_INK;
						case VENTISCA -> ParticleTypes.SNOWFLAKE;
						case MAREA_VIVA -> ParticleTypes.BUBBLE_POP;
						case LLUVIA_DE_PAVESAS -> ParticleTypes.FLAME;
						default -> ParticleTypes.FIREWORK;
					},
					player.getX(), player.getY() + 6.0, player.getZ(), 30, 8.0, 2.0, 8.0, 0.02
				);
			}
		});
	}

	/** Whether this spot can see the sky, which is what catching an event in a flask needs. */
	public static boolean underOpenSky(ServerLevel level, BlockPos pos) {
		return level.canSeeSky(pos);
	}

	/**
	 * Sends one down on a named spot, which is what the tests and the footage need.
	 *
	 * <p>The real one picks somewhere within twenty blocks of a player at random, which is right for
	 * the game and useless for a camera: a shot aimed at a spot it might not land on is a shot of an
	 * empty sky most of the time.
	 */
	public static void meteorForTest(ServerLevel level, BlockPos ground) {
		INBOUND.add(new Falling(level, ground, FALL_TICKS, landingMark(level, ground)));
	}

	/**
	 * Moves every meteorite that is still in the air one tick closer, and lands the ones that arrive.
	 *
	 * <p>Called on every level tick rather than on the event's own minute clock, because a thing
	 * falling out of the sky is only worth anything if it is drawn while it falls.
	 */
	private static void descend(ServerLevel level) {
		if (INBOUND.isEmpty()) {
			return;
		}
		for (int index = INBOUND.size() - 1; index >= 0; index--) {
			Falling falling = INBOUND.get(index);
			if (falling.level() != level) {
				continue;
			}
			int left = falling.left() - 1;
			if (left <= 0) {
				INBOUND.remove(index);
				strike(level, falling.ground());
				net.minecraft.world.phys.Vec3 centre = net.minecraft.world.phys.Vec3.atBottomCenterOf(falling.ground());
				if (falling.mark() != null && falling.mark().isAlive()) {
					falling.mark().fire(centre);
				}
				// And the blast running out over the ground from it, which is also what shakes whoever
				// is standing near enough to have seen it come down.
				dev.forja.entity.Shockwave.burst(level, centre, 11.0, 16, 0xFFC27A, 1.0F);
				continue;
			}
			INBOUND.set(index, new Falling(level, falling.ground(), left, falling.mark()));
			// Where it is now: straight down the line, easing in the way a falling thing does.
			double share = left / (double) FALL_TICKS;
			double height = falling.ground().getY() + dev.forja.entity.Shockwave.fallHeight(FALL_HEIGHT, share);
			double x = falling.ground().getX() + 0.5;
			double z = falling.ground().getZ() + 0.5;
			// The head has to be the brightest thing in the sky or the tail reads as a line of soot
			// falling by itself, which is what the first cut of this looked like.
			level.sendParticles(HEAD, x, height, z, 10, 0.3, 0.3, 0.3, 0.0);
			level.sendParticles(ParticleTypes.FLAME, x, height, z, 12, 0.3, 0.45, 0.3, 0.03);
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA, x, height, z, 10, 0.25, 0.35, 0.25, 0.2);
			// The tail it leaves behind it, which is what makes it read as falling rather than hanging.
			level.sendParticles(ParticleTypes.LARGE_SMOKE, x, height + 1.5, z, 3, 0.3, 0.6, 0.3, 0.01);
			if (left % 6 == 0) {
				level.playSound(null, x, height, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
					SoundSource.AMBIENT, 2.0F, 0.5F);
			}
		}
	}

	/**
	 * A meteorite falls near the player: it comes down out of the sky, and where it lands there is a
	 * small crater of scorched stone with the star iron that came down inside it.
	 */
	private static void meteor(ServerLevel level, ServerPlayer player) {
		BlockPos where = player.blockPosition().offset(level.getRandom().nextInt(41) - 20, 0, level.getRandom().nextInt(41) - 20);
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, where);
		INBOUND.add(new Falling(level, ground, FALL_TICKS, landingMark(level, ground)));
		for (ServerPlayer nearby : level.players()) {
			nearby.sendSystemMessage(Component.translatable("gui.forja.evento.meteorito", ground.getX(), ground.getZ()).withColor(METEORITOS.color));
		}
	}

	/**
	 * Where it is going to come down, on the ground, for the second and a half it takes to get there.
	 *
	 * <p>It lands within twenty blocks of a player, wherever it likes, and it digs a crater full of
	 * magma. Looking up was the only way to know where, and only if you happened to be facing it.
	 */
	private static dev.forja.entity.Shockwave landingMark(ServerLevel level, BlockPos ground) {
		return dev.forja.entity.Shockwave.markFalling(level, net.minecraft.world.phys.Vec3.atBottomCenterOf(ground), 4.0, FALL_TICKS, 8, 0xFFC27A, 0.9F, FALL_HEIGHT);
	}

	/** The moment it arrives. */
	private static void strike(ServerLevel level, BlockPos ground) {
		int radius = 3;
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				for (int y = -2; y <= 1; y++) {
					BlockPos pos = ground.offset(x, y, z);
					double distance = Math.sqrt(x * x + y * y * 2.0 + z * z);
					if (distance > radius) {
						continue;
					}
					BlockState state = y >= 0 ? Blocks.AIR.defaultBlockState()
						: distance > radius - 1.2 ? Blocks.BASALT.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
					level.setBlockAndUpdate(pos, state);
				}
			}
		}
		// What is left of the meteorite, lying in the crater for whoever walks up to it.
		int pieces = 3 + level.getRandom().nextInt(4);
		ItemStack iron = new ItemStack(dev.forja.registry.ModItems.HIERRO_ESTELAR, pieces);
		level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, ground.getX() + 0.5, ground.getY() + 0.5, ground.getZ() + 0.5, iron));
		level.playSound(null, ground, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 4.0F, 0.6F);
		level.sendParticles(ParticleTypes.EXPLOSION, ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5, 8, 2.0, 1.0, 2.0, 0.0);
		// And the dust it throws up, which hangs around after the bang.
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA,
			ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5, 50, 2.2, 1.0, 2.2, 0.04);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
			ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5, 40, 1.2, 0.6, 1.2, 0.6);
	}
}
