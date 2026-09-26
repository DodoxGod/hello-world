package dev.forja.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * How the mod's own particles behave.
 *
 * <p>Each is a quad with a sprite and a handful of numbers, which is all a particle ever is. What
 * makes one read as a spark and another as ash is entirely in those numbers: how fast it falls, how
 * much the air holds it back, how long it lasts and what it does on the way out. They are written
 * here rather than borrowed because borrowing is what made a forge look like a nether portal.
 */
public final class ForjaParticles {
	private ForjaParticles() {
	}

	/**
	 * Chispa: a bit of metal that was just hit, still hot.
	 *
	 * <p>Heavy and brief. A spark is not smoke — it goes where it was thrown, drops, and dies before
	 * it gets anywhere. The fade is to red rather than to nothing, because that is what cooling looks
	 * like and a spark that simply vanished looked like a rendering fault.
	 */
	public static class Spark extends SingleQuadParticle {
		protected Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
			SpriteSet sprites, RandomSource random) {
			super(level, x, y, z, 0.0, 0.0, 0.0, sprites.get(random));
			this.friction = 0.82F;
			this.gravity = 1.1F;
			this.xd = dx * 0.4 + (random.nextDouble() - 0.5) * 0.06;
			this.yd = dy * 0.4 + random.nextDouble() * 0.08;
			this.zd = dz * 0.4 + (random.nextDouble() - 0.5) * 0.06;
			this.quadSize = 0.06F + random.nextFloat() * 0.04F;
			this.lifetime = 8 + random.nextInt(10);
			this.rCol = 1.0F;
			this.gCol = 0.82F;
			this.bCol = 0.45F;
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		public void tick() {
			super.tick();
			// Cooling on the way down: the green and blue go first, so it slides orange then red.
			float left = 1.0F - (float) this.age / this.lifetime;
			this.gCol = 0.20F + 0.62F * left;
			this.bCol = 0.05F + 0.40F * left * left;
			this.alpha = Math.min(1.0F, left * 2.0F);
		}
	}

	/**
	 * Ceniza: what is left over a forge that has gone out.
	 *
	 * <p>Light enough that the air matters more than the ground does, so it drifts far more than it
	 * falls and takes its time about both. Each one picks a direction at birth and keeps it, which is
	 * what stops a cloud of them looking like one thing pulsing.
	 */
	public static class Ash extends SingleQuadParticle {
		private final double drift;
		private final double sway;

		protected Ash(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
			SpriteSet sprites, RandomSource random) {
			super(level, x, y, z, 0.0, 0.0, 0.0, sprites.get(random));
			this.friction = 0.96F;
			this.gravity = 0.06F;
			this.xd = dx * 0.1;
			this.yd = dy * 0.1 + 0.01;
			this.zd = dz * 0.1;
			this.quadSize = 0.05F + random.nextFloat() * 0.05F;
			this.lifetime = 90 + random.nextInt(70);
			this.drift = (random.nextDouble() - 0.5) * 0.012;
			this.sway = random.nextDouble() * Math.PI * 2.0;
			float grey = 0.28F + random.nextFloat() * 0.22F;
			this.rCol = grey * 1.12F;
			this.gCol = grey;
			this.bCol = grey * 0.94F;
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		public void tick() {
			super.tick();
			this.xd += this.drift * Math.cos(this.sway + this.age * 0.08);
			this.zd += this.drift * Math.sin(this.sway + this.age * 0.08);
			this.alpha = Math.min(0.85F, (1.0F - (float) this.age / this.lifetime) * 1.6F);
		}
	}

	/**
	 * Alma: whatever was wearing the armour, leaving it.
	 *
	 * <p>Rises, and slows as it goes, and swells a little right at the end before it goes out — a
	 * thing letting go rather than a thing being thrown. The pale blue is the soul fire the suits are
	 * lit with, so the two read as the same substance.
	 */
	public static class Soul extends SingleQuadParticle {
		private final float peak;

		protected Soul(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
			SpriteSet sprites, RandomSource random) {
			super(level, x, y, z, 0.0, 0.0, 0.0, sprites.get(random));
			this.friction = 0.90F;
			this.gravity = -0.02F;
			this.xd = dx * 0.25 + (random.nextDouble() - 0.5) * 0.02;
			this.yd = dy * 0.25 + 0.03;
			this.zd = dz * 0.25 + (random.nextDouble() - 0.5) * 0.02;
			this.peak = 0.10F + random.nextFloat() * 0.05F;
			this.quadSize = this.peak * 0.6F;
			this.lifetime = 24 + random.nextInt(18);
			this.rCol = 0.42F;
			this.gCol = 0.88F;
			this.bCol = 0.95F;
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		public void tick() {
			super.tick();
			float through = (float) this.age / this.lifetime;
			this.quadSize = this.peak * (0.6F + through * 0.7F);
			this.alpha = through < 0.75F ? 1.0F : (1.0F - through) * 4.0F;
		}
	}

	/**
	 * Vapor: steam, which is in a hurry and then is not.
	 *
	 * <p>It leaves whatever made it fast, the air stops it almost at once, and from there it only climbs
	 * and spreads. The sprite is four frames of one puff coming apart and the particle walks through
	 * them as it ages, so it thins out instead of shrinking — a cloud that shrinks reads as something
	 * going away from you, and steam does not go anywhere, it stops being there.
	 */
	public static class Steam extends SingleQuadParticle {
		private final SpriteSet sprites;
		private final float start;

		protected Steam(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
			SpriteSet sprites, RandomSource random) {
			super(level, x, y, z, 0.0, 0.0, 0.0, sprites.get(0, 1));
			this.sprites = sprites;
			this.friction = 0.86F;
			this.gravity = -0.035F;
			this.xd = dx + (random.nextDouble() - 0.5) * 0.03;
			this.yd = dy + 0.02 + random.nextDouble() * 0.02;
			this.zd = dz + (random.nextDouble() - 0.5) * 0.03;
			this.start = 0.20F + random.nextFloat() * 0.14F;
			this.quadSize = this.start;
			this.lifetime = 22 + random.nextInt(18);
			this.roll = random.nextFloat() * ((float) Math.PI * 2.0F);
			this.oRoll = this.roll;
			float white = 0.90F + random.nextFloat() * 0.10F;
			this.rCol = white;
			this.gCol = white;
			this.bCol = Math.min(1.0F, white + 0.02F);
			this.alpha = 0.0F;
			this.setSpriteFromAge(sprites);
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		public void tick() {
			super.tick();
			if (this.removed) {
				return;
			}
			this.setSpriteFromAge(this.sprites);
			float through = (float) this.age / this.lifetime;
			this.quadSize = this.start * (1.0F + through * 2.4F);
			// In over the first tenth, so a burst of them does not pop into being; out over the rest.
			this.alpha = through < 0.1F ? through * 8.0F : 0.8F * (1.0F - through) * (1.0F - through) + 0.06F * (1.0F - through);
		}

		/**
		 * Never darker than dusk. Steam is white because it throws back whatever light there is, and at
		 * midnight the honest answer to that is a grey nobody can see: the purge of an automaton in a dark
		 * cellar was twenty-six particles of nothing. The floor is low enough that it still sits in shadow.
		 */
		@Override
		protected int getLightCoords(float partialTick) {
			int packed = super.getLightCoords(partialTick);
			int block = Math.max(packed & 0xFFFF, 9 << 4);
			return packed & 0xFFFF0000 | block;
		}
	}

	/**
	 * Gota: molten metal, falling, and lit by nothing but itself.
	 *
	 * <p>Thrown with a colour instead of a velocity — see {@code ModParticles.GOTA}. It falls like the
	 * heavy thing it is, stretches as it picks up speed, and where it lands it throws a spark or two and
	 * is gone.
	 */
	public static class Drip extends SingleQuadParticle {
		private final SpriteSet sprites;

		protected Drip(ClientLevel level, double x, double y, double z, double red, double green, double blue,
			SpriteSet sprites, RandomSource random) {
			super(level, x, y, z, 0.0, 0.0, 0.0, sprites.get(0, 1));
			this.sprites = sprites;
			this.friction = 0.98F;
			this.gravity = 0.9F;
			this.xd = 0.0;
			this.yd = -0.02;
			this.zd = 0.0;
			this.quadSize = 0.07F + random.nextFloat() * 0.03F;
			this.lifetime = 50;
			this.hasPhysics = true;
			// A colour of all zeroes is somebody who did not know about the convention: plain molten orange.
			boolean given = red + green + blue > 0.01;
			this.rCol = given ? (float) Math.min(1.0, red) : 1.0F;
			this.gCol = given ? (float) Math.min(1.0, green) : 0.55F;
			this.bCol = given ? (float) Math.min(1.0, blue) : 0.15F;
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		protected int getLightCoords(float partialTick) {
			return 0xF000F0;
		}

		@Override
		public void tick() {
			super.tick();
			if (this.removed) {
				return;
			}
			if (this.yd < -0.25) {
				this.setSprite(this.sprites.get(1, 1));
			}
			if (this.onGround) {
				for (int i = 0; i < 2; i++) {
					this.level.addParticle(dev.forja.registry.ModParticles.CHISPA, this.x, this.y + 0.02, this.z,
						(this.random.nextDouble() - 0.5) * 0.3, 0.2 + this.random.nextDouble() * 0.15, (this.random.nextDouble() - 0.5) * 0.3);
				}
				this.remove();
			}
		}
	}

	/** One provider shape for all of them: pick the sprite, hand over the velocity, let the class decide. */
	public record Maker(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
		public enum Kind {
			SPARK, ASH, SOUL, STEAM, DRIP
		}

		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level,
			double x, double y, double z, double dx, double dy, double dz, RandomSource random) {
			return switch (this.kind) {
				case SPARK -> new Spark(level, x, y, z, dx, dy, dz, this.sprites, random);
				case ASH -> new Ash(level, x, y, z, dx, dy, dz, this.sprites, random);
				case SOUL -> new Soul(level, x, y, z, dx, dy, dz, this.sprites, random);
				case STEAM -> new Steam(level, x, y, z, dx, dy, dz, this.sprites, random);
				case DRIP -> new Drip(level, x, y, z, dx, dy, dz, this.sprites, random);
			};
		}
	}
}
