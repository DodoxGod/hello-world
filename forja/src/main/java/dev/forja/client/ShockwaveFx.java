package dev.forja.client;

import dev.forja.entity.Shockwave;
import dev.forja.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a {@link Shockwave} does on the client besides being drawn: the floor it tears up, and the
 * moment it reaches you.
 *
 * <p>All of it is made here rather than sent. The server used to post every flame of the ring to every
 * player; now the debris is each client's own business, thrown up only along the front, from whatever
 * the floor there is actually made of, at angles picked fresh each tick so it never settles into
 * spokes. And the blow is felt twice, the way a real one is: once when the hammer lands, and again —
 * louder, and with the camera — when the ring gets to where you are standing.
 */
public final class ShockwaveFx {
	/** Bits of floor per block of front per tick, before the cap. */
	private static final float DEBRIS_DENSITY = 0.45F;
	private static final int DEBRIS_CAP = 26;

	/** How far off a landing blow still moves the camera. */
	private static final double FELT_FROM = 28.0;

	private ShockwaveFx() {
	}

	public static void register() {
		Shockwave.clientTicker = ShockwaveFx::tick;
	}

	private static void tick(Shockwave wave) {
		if (!wave.fired()) {
			return;
		}
		Level level = wave.level();
		float age = level.getGameTime() - wave.firedAt();
		if (age < 0.0F || age > wave.duration()) {
			return;
		}
		if (wave.pool()) {
			simmer(wave, level, 1.0F - age / wave.duration());
			return;
		}
		double before = Shockwave.radiusAt(wave.reach(), age - 1.0F, wave.duration());
		double radius = Shockwave.radiusAt(wave.reach(), age, wave.duration());
		debris(wave, level, radius);
		feel(wave, level, before, radius);
	}

	/**
	 * What comes off a patch while it lies there: a little fire and smoke off one that burns, a slow
	 * mote of its own colour off one that does not. Less of it as the patch runs out.
	 */
	private static void simmer(Shockwave wave, Level level, float left) {
		RandomSource random = level.getRandom();
		int colour = wave.colour();
		int red = (colour >> 16) & 0xFF;
		int green = (colour >> 8) & 0xFF;
		// A rune is not slag: an amethyst one is as red as it is anything, and flames off it would be a lie.
		boolean burns = green <= red && !wave.glyph();
		float chance = wave.reach() * 0.22F * (0.3F + 0.7F * left);
		while (chance > 0.0F) {
			if (chance < 1.0F && random.nextFloat() > chance) {
				break;
			}
			chance -= 1.0F;
			double angle = within(wave, random);
			double reach = Math.sqrt(random.nextDouble()) * wave.reach();
			double x = wave.getX() + Math.cos(angle) * reach;
			double z = wave.getZ() + Math.sin(angle) * reach;
			double y = wave.getY() + Shockwave.floorAt(level, x, wave.getY(), z) + 0.08;
			if (burns) {
				level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME, x, y, z, 0.0, 0.015 + random.nextDouble() * 0.02, 0.0);
			} else {
				level.addParticle(new DustParticleOptions(colour, 0.7F), x, y, z, 0.0, 0.01, 0.0);
			}
		}
	}

	/** An angle somewhere inside the wave's shape: anywhere at all for a circle, inside the wedge for a wedge. */
	private static double within(Shockwave wave, RandomSource random) {
		float arc = wave.arc();
		if (arc >= Shockwave.WHOLE - 0.001F) {
			return random.nextDouble() * Math.PI * 2.0;
		}
		return wave.facing() + (random.nextDouble() * 2.0 - 1.0) * arc;
	}

	/** Floor thrown up along the front, and sparks with it. */
	private static void debris(Shockwave wave, Level level, double radius) {
		if (radius < 0.4) {
			return;
		}
		RandomSource random = level.getRandom();
		// Less of everything off a ring with little force in it: a horn call does not tear the floor up.
		float force = Mth.clamp(wave.flame(), 0.0F, 1.0F);
		int count = Math.min(DEBRIS_CAP, Mth.ceil(radius * Math.min(wave.arc(), Shockwave.WHOLE) * 2.0F * DEBRIS_DENSITY * force));
		DustParticleOptions glow = new DustParticleOptions(wave.colour(), 1.1F);
		// Sparks come off the rings that burn. Steam, a soul's call and oil are told apart by colour —
		// more green than red, or barely any colour at all — and throw motes of their own colour instead.
		int red = (wave.colour() >> 16) & 0xFF;
		int green = (wave.colour() >> 8) & 0xFF;
		int blue = wave.colour() & 0xFF;
		int high = Math.max(red, Math.max(green, blue));
		int low = Math.min(red, Math.min(green, blue));
		boolean burns = green <= red && high > 0 && (high - low) / (float) high >= 0.4F;
		for (int i = 0; i < count; i++) {
			double angle = within(wave, random);
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			double x = wave.getX() + cos * radius;
			double z = wave.getZ() + sin * radius;
			double y = wave.getY() + Shockwave.floorAt(level, x, wave.getY(), z);
			// Thrown the way the ring is going, and up: it is being pushed, not dropped.
			double push = 0.12 + random.nextDouble() * 0.2;
			int kind = random.nextInt(10);
			if (kind < 5) {
				BlockState floor = level.getBlockState(BlockPos.containing(x, y - 0.1, z));
				if (!floor.isAir()) {
					level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, floor), x, y + 0.1, z,
						cos * push * 4.0, 0.6 + random.nextDouble() * 0.8, sin * push * 4.0);
				}
			} else if (kind < 8 && burns) {
				level.addParticle(ModParticles.CHISPA, x, y + 0.15, z, cos * push * 2.2, 0.35 + random.nextDouble() * 0.5, sin * push * 2.2);
			} else {
				level.addParticle(glow, x, y + 0.2 + random.nextDouble() * 0.8, z, cos * push, 0.02, sin * push);
			}
		}
	}

	/** The blow landing somewhere nearby, and then the ring arriving where the player is. */
	private static void feel(Shockwave wave, Level level, double before, double radius) {
		Player player = Minecraft.getInstance().player;
		if (player == null || Math.abs(player.getY() - wave.getY()) > 5.0) {
			return;
		}
		double dx = player.getX() - wave.getX();
		double dz = player.getZ() - wave.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		float force = Mth.clamp(wave.flame(), 0.0F, 1.0F);
		if (!wave.landed) {
			wave.landed = true;
			if (distance < FELT_FROM) {
				ScreenShake.add((float) (0.55 * force * (1.0 - distance / FELT_FROM)));
			}
		}
		if (!wave.arrived && distance <= radius && distance <= wave.reach() + 0.5 && wave.covers(dx, dz)) {
			wave.arrived = true;
			// Only if it actually swept over them this tick or the last few: someone who walks into
			// the afterglow has not been hit by anything.
			// Steam, oil and a horn call go past without the roar: that belongs to the ones that burn.
			if (distance >= before - 1.5 && force >= 0.5F) {
				ScreenShake.add(0.6F * force);
				level.playLocalSound(player.getX(), player.getY(), player.getZ(),
					SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, force, 0.55F, false);
			}
		}
	}
}
