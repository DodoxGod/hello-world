package dev.forja.world;

import dev.forja.Forja;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * What it feels like to be standing inside one of the mod's ruins.
 *
 * <p>The five structures are built out of ordinary blocks and a few of ours, and once you are inside
 * one it looks like any other pile of stone: the abandoned forge, the fallen smithy and the smith's
 * barrow are three different stories told with the same silence. This gives each of them an ambience
 * of its own — what drifts through the air, and what you can hear that is not there.
 *
 * <p>It runs on the server because only the server knows what structure a place belongs to, and it
 * runs at a crawl on purpose: once every two seconds per player, a handful of particles. A ruin that
 * is constantly doing something is a set, not a ruin. The point is that every so often, while you are
 * picking through it, the building reminds you what happened here.
 */
public final class RuinMood {
	/** How often each player is checked, in ticks. */
	private static final int EVERY = 40;

	private static final ResourceKey<Structure> FORJA_ABANDONADA = key("forja_abandonada");
	private static final ResourceKey<Structure> FRAGUA_CAIDA = key("fragua_caida");
	private static final ResourceKey<Structure> TUMULO = key("tumulo_del_herrero");
	private static final ResourceKey<Structure> TALLER = key("taller_de_montana");
	private static final ResourceKey<Structure> CAMPAMENTO = key("campamento_saqueadores");

	/** All five, so one lookup can answer "am I in any of ours, and which". */
	@SuppressWarnings("unchecked")
	private static final ResourceKey<Structure>[] OURS = new ResourceKey[] {
		FORJA_ABANDONADA, FRAGUA_CAIDA, TUMULO, TALLER, CAMPAMENTO,
	};

	private RuinMood() {
	}

	private static ResourceKey<Structure> key(String name) {
		return ResourceKey.create(Registries.STRUCTURE, Forja.id(name));
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % EVERY != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator()) {
					inside(level, player);
				}
			}
		});
	}

	/**
	 * Which of ours this spot belongs to, or null.
	 *
	 * <p>Public because more than the ambience wants to know: the constructs only wake inside the
	 * ruins, and asking the same question twice with two copies of the lookup would be two chunk reads
	 * for one answer.
	 */
	public static @Nullable ResourceKey<Structure> ruinAt(ServerLevel level, net.minecraft.core.BlockPos pos) {
		ResourceKey<Structure>[] found = new ResourceKey[1];
		level.structureManager().getStructureWithPieceAt(pos, holder -> {
			for (ResourceKey<Structure> key : OURS) {
				if (holder.is(key)) {
					found[0] = key;
					return true;
				}
			}
			return false;
		});
		return found[0];
	}

	/** The three that were built in a workshop, and so the three a construct might still be guarding. */
	public static boolean isWorkshop(@Nullable ResourceKey<Structure> ruin) {
		return ruin == FORJA_ABANDONADA || ruin == FRAGUA_CAIDA || ruin == TALLER;
	}

	private static void inside(ServerLevel level, ServerPlayer player) {
		ResourceKey<Structure> here = ruinAt(level, player.blockPosition());
		if (here == null) {
			return;
		}

		var random = level.getRandom();
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();

		if (here == FORJA_ABANDONADA) {
			// An abandoned forge: the fire has been out for years and the ash has not settled.
			level.sendParticles(dev.forja.registry.ModParticles.CENIZA, x, y + 2.0, z, 5, 4.0, 2.0, 4.0, 0.01);
			if (random.nextInt(6) == 0) {
				// A hammer, a long way off, that stops when you listen for it.
				level.playSound(null, x, y, z, SoundEvents.ANVIL_USE, SoundSource.AMBIENT, 0.18F, 0.55F);
			}
		} else if (here == FRAGUA_CAIDA) {
			// A fallen smithy: it burned, and something of it is still warm.
			level.sendParticles(dev.forja.registry.ModParticles.CENIZA, x, y + 1.5, z, 4, 3.5, 1.5, 3.5, 0.01);
			if (random.nextInt(4) == 0) {
				level.sendParticles(dev.forja.registry.ModParticles.CHISPA, x, y + 0.4, z, 2, 3.0, 0.4, 3.0, 0.05);
			}
			if (random.nextInt(8) == 0) {
				level.playSound(null, x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.AMBIENT, 0.25F, 0.5F);
			}
		} else if (here == TUMULO) {
			// The smith's barrow: nothing burns down here. What is left is what is left of him.
			level.sendParticles(dev.forja.registry.ModParticles.ALMA, x, y + 1.0, z, 3, 3.5, 1.2, 3.5, 0.01);
			level.sendParticles(ParticleTypes.ASH, x, y + 2.0, z, 6, 4.0, 2.0, 4.0, 0.0);
			if (random.nextInt(5) == 0) {
				level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT, 0.22F, 0.5F);
			}
		} else if (here == TALLER) {
			// A mountain workshop: high, cold, and still being used by somebody.
			if (random.nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 2.5, z, 6, 4.0, 2.0, 4.0, 0.0);
			}
			if (random.nextInt(10) == 0) {
				level.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.AMBIENT, 0.15F, 0.7F);
			}
		} else if (here == CAMPAMENTO) {
			// A raider camp: smoke off the cooking fires and the smell of a forge nobody owns.
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 3.0, z, 3, 5.0, 1.5, 5.0, 0.005);
			if (random.nextInt(7) == 0) {
				level.playSound(null, x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.AMBIENT, 0.2F, 0.9F);
			}
		}
	}
}
