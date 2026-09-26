package dev.forja.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.forja.Forja;
import dev.forja.world.WorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * What hangs in the sky while an event runs.
 *
 * <p>An event used to be a colour: the whole sky tinted, the fog tinted a little less, and some
 * particles thrown about at head height. Nine events were nine tints, and a tint has nothing in it to
 * look at — a blood moon with no moon, an eclipse with the sun shining through it. This draws the
 * thing each night is named after, and {@link SkyMood} turns the tint down into a backdrop for it.
 *
 * <p>Everything here is geometry handed to the level's own submit pass, at the point Fabric opens for
 * that, through the vanilla {@code eyes} render type: lit by itself, blended by alpha, <b>depth-tested
 * but writing none</b>. That last part is what makes it sit in the world rather than on the screen: a
 * mountain stands in front of the aurora, a roof hides the moon. It is all hung on a sphere round the
 * camera, inside the distance the fog leaves clear, because that pipeline fogs what it draws and
 * anything further out would simply be taken by it.
 *
 * <p>The one exception is the soul fog's lights, which belong to the ground and are anchored to it.
 */
public final class EventSkyRenderer {
	private static final Identifier AURORA = Forja.id("textures/environment/aurora.png");
	private static final Identifier GLOW = Forja.id("textures/environment/resplandor.png");
	private static final Identifier MOON = Forja.id("textures/environment/luna.png");
	private static final Identifier DISC = Forja.id("textures/environment/disco.png");
	private static final Identifier CORONA = Forja.id("textures/environment/corona.png");
	private static final Identifier RUNES = Forja.id("textures/environment/runas.png");
	private static final Identifier FLAT = Forja.id("textures/environment/plano.png");

	private EventSkyRenderer() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(EventSkyRenderer::collect);
	}

	private static void collect(LevelRenderContext context) {
		WorldEvents showing = SkyMood.showing();
		Minecraft client = Minecraft.getInstance();
		if (showing == null || client.level == null || client.level.dimension() != Level.OVERWORLD) {
			return;
		}
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float weight = SkyMood.weight(client.level.getOverworldClockTime());
		if (weight <= 0.01F) {
			return;
		}
		// The bodies themselves — a moon, the disc across the sun — are as solid as the event is, not
		// as the hour is. At dusk the night is only half arrived, and a half-drawn moon lets the real
		// one show through the middle of it.
		float solid = SkyMood.blend();
		// Seconds, wrapped well before a float starts dropping the part of a tick.
		float time = (client.level.getGameTime() % 72000L + partial) / 20.0F;
		float radius = SkyMood.skyRadius();
		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		switch (showing) {
			case AURORA -> aurora(pose, collector, radius * 0.62F, time, weight);
			case LUNA_DE_SANGRE -> bloodMoon(pose, collector, radius, time, weight, solid);
			case MAREA_VIVA -> springTide(pose, collector, radius, time, weight, solid);
			case ECLIPSE -> eclipse(pose, collector, radius, time, weight, solid);
			case TORMENTA_ARCANA -> sigil(pose, collector, radius, time, weight, SkyMood.flash(partial));
			case METEORITOS -> streaks(pose, collector, radius, partial, weight);
			case LLUVIA_DE_PAVESAS -> {
				burningHorizon(pose, collector, radius, time, weight);
				streaks(pose, collector, radius, partial, weight);
			}
			case NIEBLA_DE_ALMAS -> wisps(pose, collector, client, time, weight);
			case VENTISCA -> {
			}
		}
	}

	/**
	 * Four curtains across the north, each a strip that wanders, hangs and shimmers.
	 *
	 * <p>What makes an aurora is that the light is organised: long sheets that hold their shape while
	 * they move. The particles this replaces were dots at altitude, which read as snow however they
	 * were arranged. A curtain is a foot that snakes across the sky, a height that breathes, and rays
	 * in it that slide along — green at the foot going up into violet, the way the real ones do because
	 * of which gas is glowing at which height.
	 */
	private static void aurora(PoseStack pose, SubmitNodeCollector collector, float radius, float time, float weight) {
		int steps = 44;
		float half = radius * 1.55F;
		// Its light on the horizon underneath it, which is what ties the curtains to the ground.
		collector.submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) ->
			facing(p, buffer, new Vec3(0.0, 0.16, -1.0).normalize(), radius, radius * 1.5F, radius * 0.5F, 0.0F, 0x2FE0A0,
				0.2F * weight * (0.85F + 0.15F * Mth.sin(time * 0.4F))));
		collector.submitCustomGeometry(pose, RenderTypes.eyes(AURORA), (p, buffer) -> {
			for (int band = 0; band < 4; band++) {
				int foot = band == 2 ? 0x7CFFD6 : 0x4DFFA6;
				int waist = band == 1 ? 0x58D8FF : 0x46E6C0;
				int crown = band == 2 ? 0xFF7AD0 : 0x9C6BFF;
				float depth = -radius * (0.97F - band * 0.17F);
				float strength = weight * (band == 3 ? 0.5F : 0.82F);
				float slide = time * 0.012F * (band % 2 == 0 ? 1.0F : -1.0F);
				float px = 0.0F, pz = 0.0F, pFoot = 0.0F, pTall = 0.0F, pAlpha = 0.0F;
				for (int i = 0; i <= steps; i++) {
					float x = -half + 2.0F * half * i / steps;
					float z = depth + Mth.sin(x * 0.011F + time * 0.10F + band * 1.3F) * radius * 0.10F
						+ Mth.sin(x * 0.027F - time * 0.06F + band) * radius * 0.04F;
					float y = radius * (0.30F + band * 0.05F) + Mth.sin(x * 0.017F + time * 0.08F + band * 2.1F) * radius * 0.035F;
					float tall = radius * (0.52F - band * 0.04F) * (0.75F + 0.25F * Mth.sin(x * 0.021F + time * 0.15F + band * 0.7F));
					// It thins to nothing at both ends, and brightens and dims along its length.
					float ends = Mth.sqrt(Mth.clamp(1.0F - Math.abs(x) / half, 0.0F, 1.0F));
					float alpha = strength * ends * (0.7F + 0.3F * Mth.sin(x * 0.05F + time * 0.9F + band));
					if (i > 0) {
						float u0 = (i - 1) * 6.0F / steps + slide;
						float u1 = i * 6.0F / steps + slide;
						quad(p, buffer,
							px, pFoot, pz, u0, 1.0F, foot, pAlpha,
							x, y, z, u1, 1.0F, foot, alpha,
							x, y + tall * 0.35F, z, u1, 0.62F, waist, alpha * 0.8F,
							px, pFoot + pTall * 0.35F, pz, u0, 0.62F, waist, pAlpha * 0.8F);
						quad(p, buffer,
							px, pFoot + pTall * 0.35F, pz, u0, 0.62F, waist, pAlpha * 0.8F,
							x, y + tall * 0.35F, z, u1, 0.62F, waist, alpha * 0.8F,
							x, y + tall, z, u1, 0.0F, crown, 0.0F,
							px, pFoot + pTall, pz, u0, 0.0F, crown, 0.0F);
					}
					px = x;
					pz = z;
					pFoot = y;
					pTall = tall;
					pAlpha = alpha;
				}
			}
		});
	}

	/** A full red moon, twice the size the real one looks so that it covers it, in a wide dull halo. */
	private static void bloodMoon(PoseStack pose, SubmitNodeCollector collector, float radius, float time, float weight, float solid) {
		Vec3 moon = heavenly(SkyMood.moonAngle);
		if (moon.y < -0.2) {
			return;
		}
		float breath = 0.9F + 0.1F * Mth.sin(time * 0.7F);
		collector.submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) ->
			facing(p, buffer, moon, radius, radius * 0.62F, 0.0F, 0xC8261E, 0.6F * weight * breath));
		collector.submitCustomGeometry(pose, RenderTypes.eyes(MOON), (p, buffer) ->
			facing(p, buffer, moon, radius * 0.98F, radius * 0.2F, 0.0F, 0xF04A32, solid));
	}

	/** The moon come close: huge, pale and blue-white, with its light spilling down towards the water. */
	private static void springTide(PoseStack pose, SubmitNodeCollector collector, float radius, float time, float weight, float solid) {
		Vec3 moon = heavenly(SkyMood.moonAngle);
		if (moon.y < -0.2) {
			return;
		}
		float swell = 1.0F + 0.04F * Mth.sin(time * 0.5F);
		collector.submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			facing(p, buffer, moon, radius, radius * 0.8F * swell, 0.0F, 0x3FA8E0, 0.45F * weight);
			// The light coming down off it: a second, taller glow hung below.
			Vec3 below = new Vec3(moon.x, moon.y - 0.32, moon.z).normalize();
			facing(p, buffer, below, radius, radius * 0.34F, radius * 0.8F, 0.0F, 0x7FD4FF, 0.24F * weight);
		});
		collector.submitCustomGeometry(pose, RenderTypes.eyes(MOON), (p, buffer) ->
			facing(p, buffer, moon, radius * 0.98F, radius * 0.3F * swell, 0.0F, 0xD6F2FF, solid));
	}

	/**
	 * A disc across the sun, and the corona standing out round it. By night, across the moon instead.
	 *
	 * <p>The disc is drawn a shade larger than the sun so that no sliver of it shows, which is also how
	 * the real thing manages totality.
	 */
	private static void eclipse(PoseStack pose, SubmitNodeCollector collector, float radius, float time, float weight, float solid) {
		Vec3 sun = heavenly(SkyMood.sunAngle);
		boolean day = sun.y > -0.12;
		Vec3 body = day ? sun : heavenly(SkyMood.moonAngle);
		// The sun's sprite is thirty across at a hundred out, and the sun in it is about half of that.
		float size = radius * (day ? 0.17F : 0.115F);
		float turn = time * 0.02F;
		collector.submitCustomGeometry(pose, RenderTypes.eyes(CORONA), (p, buffer) -> {
			// 3.75: the hard ring in the texture is 0.27 of the way out, and it has to sit on the disc's edge.
			facing(p, buffer, body, radius, size * 3.75F, turn, day ? 0xFFF4FF : 0xFF5A40, weight);
			facing(p, buffer, body, radius, size * 5.2F, -turn * 1.7F, day ? 0xC9B8FF : 0xB02A20, 0.55F * weight);
		});
		collector.submitCustomGeometry(pose, RenderTypes.eyes(DISC), (p, buffer) ->
			facing(p, buffer, body, radius * 0.97F, size, 0.0F, 0x05030A, solid));
	}

	/**
	 * The arcane storm's sigil: a circle of marks the size of the sky, turning slowly overhead, with a
	 * smaller one turning against it inside. Lightning lights it from behind for a frame.
	 */
	private static void sigil(PoseStack pose, SubmitNodeCollector collector, float radius, float time, float weight, float flash) {
		float height = radius * 0.5F;
		float pulse = 0.85F + 0.15F * Mth.sin(time * 1.3F);
		collector.submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) ->
			level(p, buffer, height * 1.02F, radius * 0.72F, 0.0F, 0x6A4BD0, (0.22F + 0.5F * flash) * weight));
		collector.submitCustomGeometry(pose, RenderTypes.eyes(RUNES), (p, buffer) -> {
			level(p, buffer, height, radius * 0.58F, time * 0.035F, 0xFFE45C, Math.min(1.0F, (0.5F * pulse + 0.5F * flash) * weight));
			level(p, buffer, height * 0.96F, radius * 0.26F, -time * 0.09F, 0xB48CFF, Math.min(1.0F, (0.42F + 0.5F * flash) * weight));
		});
	}

	/** Shooting stars and falling fire: a thin bright line with its tail fading out behind it. */
	private static void streaks(PoseStack pose, SubmitNodeCollector collector, float radius, float partial, float weight) {
		if (SkyMood.STREAKS.isEmpty()) {
			return;
		}
		java.util.List<SkyMood.Streak> all = java.util.List.copyOf(SkyMood.STREAKS);
		collector.submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> {
			for (SkyMood.Streak streak : all) {
				float through = Mth.clamp((streak.age + partial) / streak.life, 0.0F, 1.0F);
				// In quickly, out slowly: it is brightest just after it appears.
				float alpha = weight * Math.min(1.0F, through * 6.0F) * (1.0F - through * through);
				float headAt = streak.sweep * through;
				float tailAt = Math.max(0.0F, headAt - streak.length);
				Vec3 head = along(streak, headAt).scale(radius);
				Vec3 tail = along(streak, tailAt).scale(radius);
				Vec3 across = head.subtract(tail).cross(head).normalize().scale(radius * streak.width);
				int pale = mix(streak.colour, 0xFFFFFF, 0.7F);
				quad(p, buffer,
					(float) (tail.x - across.x * 0.2), (float) (tail.y - across.y * 0.2), (float) (tail.z - across.z * 0.2), 0.0F, 0.0F, streak.colour, 0.0F,
					(float) (head.x - across.x), (float) (head.y - across.y), (float) (head.z - across.z), 1.0F, 0.0F, pale, alpha,
					(float) (head.x + across.x), (float) (head.y + across.y), (float) (head.z + across.z), 1.0F, 1.0F, pale, alpha,
					(float) (tail.x + across.x * 0.2), (float) (tail.y + across.y * 0.2), (float) (tail.z + across.z * 0.2), 0.0F, 1.0F, streak.colour, 0.0F);
			}
		});
		collector.submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			for (SkyMood.Streak streak : all) {
				if (!streak.fireball) {
					continue;
				}
				float through = Mth.clamp((streak.age + partial) / streak.life, 0.0F, 1.0F);
				float alpha = weight * Math.min(1.0F, through * 6.0F) * (1.0F - through * through);
				facing(p, buffer, along(streak, streak.sweep * through), radius * 0.99F, radius * streak.width * 7.0F, 0.0F, streak.colour, alpha);
			}
		});
	}

	/** Where a streak is after it has gone {@code angle} radians round the sky from where it started. */
	private static Vec3 along(SkyMood.Streak streak, float angle) {
		float cos = Mth.cos(angle);
		float sin = Mth.sin(angle);
		return new Vec3(streak.x * cos + streak.tx * sin, streak.y * cos + streak.ty * sin, streak.z * cos + streak.tz * sin);
	}

	/**
	 * The ember rain's horizon: a band of firelight all the way round, brightest at the ground, that
	 * never quite holds still. It is what says the fire is <i>everywhere</i> rather than overhead.
	 */
	private static void burningHorizon(PoseStack pose, SubmitNodeCollector collector, float radius, float time, float weight) {
		int steps = 48;
		collector.submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> {
			for (int i = 0; i < steps; i++) {
				float a0 = i * Mth.TWO_PI / steps;
				float a1 = (i + 1) * Mth.TWO_PI / steps;
				float f0 = flicker(a0, time);
				float f1 = flicker(a1, time);
				float x0 = Mth.cos(a0) * radius, z0 = Mth.sin(a0) * radius;
				float x1 = Mth.cos(a1) * radius, z1 = Mth.sin(a1) * radius;
				float low = -radius * 0.12F;
				quad(p, buffer,
					x0, low, z0, 0.0F, 1.0F, 0xFF7A1E, 0.62F * weight * f0,
					x1, low, z1, 1.0F, 1.0F, 0xFF7A1E, 0.62F * weight * f1,
					x1, low + radius * (0.2F + 0.16F * f1), z1, 1.0F, 0.0F, 0xB02808, 0.0F,
					x0, low + radius * (0.2F + 0.16F * f0), z0, 0.0F, 0.0F, 0xB02808, 0.0F);
			}
		});
	}

	private static float flicker(float angle, float time) {
		return 0.72F + 0.18F * Mth.sin(angle * 5.0F + time * 0.9F) + 0.10F * Mth.sin(angle * 11.0F - time * 1.7F);
	}

	/**
	 * The soul fog's lights: pale things near the ground, one to a patch of land, each wandering round
	 * its own spot. They belong to the place and not to the viewer, so they are found from the world —
	 * a light that kept pace with you as you walked would not be a light in the fog, it would be a
	 * smudge on the lens.
	 */
	private static void wisps(PoseStack pose, SubmitNodeCollector collector, Minecraft client, float time, float weight) {
		Vec3 camera = client.gameRenderer.mainCamera().position();
		Level level = client.level;
		int cell = 9;
		int reach = 4;
		int baseX = Mth.floor(camera.x / cell);
		int baseZ = Mth.floor(camera.z / cell);
		collector.submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			for (int cx = baseX - reach; cx <= baseX + reach; cx++) {
				for (int cz = baseZ - reach; cz <= baseZ + reach; cz++) {
					long seed = Mth.getSeed(cx, 17, cz);
					// Two patches in three have one.
					if ((seed >> 8 & 3L) == 0L) {
						continue;
					}
					float phase = (seed >> 12 & 1023L) / 1023.0F * Mth.TWO_PI;
					double x = (cx + 0.5) * cell + Mth.sin(time * 0.23F + phase) * 2.6;
					double z = (cz + 0.5) * cell + Mth.cos(time * 0.19F + phase * 1.7F) * 2.6;
					double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
					double y = ground + 1.6 + Mth.sin(time * 0.6F + phase) * 0.6;
					Vec3 from = new Vec3(x - camera.x, y - camera.y, z - camera.z);
					double distance = from.length();
					if (distance < 2.5 || distance > cell * reach) {
						continue;
					}
					// Out of the fog as you come near, and gone again before you can walk into one.
					float near = (float) Mth.clamp((distance - 2.5) / 5.0, 0.0, 1.0);
					float far = (float) Mth.clamp((cell * reach - distance) / 10.0, 0.0, 1.0);
					float beat = 0.7F + 0.3F * Mth.sin(time * 1.9F + phase * 3.0F);
					float alpha = weight * near * far * beat;
					Vec3 towards = from.normalize();
					facing(p, buffer, towards, (float) distance, 2.6F, 0.0F, 0x8FF0E8, 0.42F * alpha);
					facing(p, buffer, towards, (float) distance * 0.995F, 0.7F, 0.0F, 0xEFFFFC, 0.95F * alpha);
				}
			}
		});
	}

	/** Where the game has the sun or the moon, as a direction from the viewer: the same turn the sky renderer makes. */
	private static Vec3 heavenly(float angle) {
		return new Vec3(-Mth.sin(angle), Mth.cos(angle), 0.0);
	}

	private static void facing(PoseStack.Pose pose, VertexConsumer buffer, Vec3 towards, float distance, float size,
		float roll, int colour, float alpha) {
		facing(pose, buffer, towards, distance, size, size, roll, colour, alpha);
	}

	/**
	 * A sprite out along a direction, square to whoever is looking along it, turned by {@code roll}
	 * about that line — and <b>bent onto the sphere</b> it hangs on, in a four by four grid.
	 *
	 * <p>It was a flat card at first, and the big ones came out nearly black. The pipeline fogs by the
	 * distance of each <i>vertex</i>, and the corners of a flat card a hundred blocks across are a good
	 * deal further away than its middle: the corona's corners were past the end of the fog, so the fog
	 * was blended across the whole of it. Every vertex of a patch of sphere is the same distance away.
	 */
	private static void facing(PoseStack.Pose pose, VertexConsumer buffer, Vec3 towards, float distance, float width, float height,
		float roll, int colour, float alpha) {
		if (alpha <= 0.003F) {
			return;
		}
		Vec3 up = Math.abs(towards.y) > 0.98 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 right = towards.cross(up).normalize();
		Vec3 above = right.cross(towards).normalize();
		float cos = Mth.cos(roll);
		float sin = Mth.sin(roll);
		Vec3 r = right.scale(cos).add(above.scale(sin)).scale(width / distance);
		Vec3 a = above.scale(cos).subtract(right.scale(sin)).scale(height / distance);
		int cells = width / distance > 0.12F ? 4 : 1;
		float[][][] at = new float[cells + 1][cells + 1][];
		for (int i = 0; i <= cells; i++) {
			for (int j = 0; j <= cells; j++) {
				float u = i * 2.0F / cells - 1.0F;
				float v = j * 2.0F / cells - 1.0F;
				Vec3 spot = towards.add(r.scale(u)).add(a.scale(v)).normalize().scale(distance);
				at[i][j] = new float[] {(float) spot.x, (float) spot.y, (float) spot.z};
			}
		}
		for (int i = 0; i < cells; i++) {
			for (int j = 0; j < cells; j++) {
				float u0 = (float) i / cells;
				float u1 = (float) (i + 1) / cells;
				float v0 = 1.0F - (float) j / cells;
				float v1 = 1.0F - (float) (j + 1) / cells;
				quad(pose, buffer,
					at[i][j][0], at[i][j][1], at[i][j][2], u0, v0, colour, alpha,
					at[i + 1][j][0], at[i + 1][j][1], at[i + 1][j][2], u1, v0, colour, alpha,
					at[i + 1][j + 1][0], at[i + 1][j + 1][1], at[i + 1][j + 1][2], u1, v1, colour, alpha,
					at[i][j + 1][0], at[i][j + 1][1], at[i][j + 1][2], u0, v1, colour, alpha);
			}
		}
	}

	/** A sprite lying flat overhead, turned by {@code turn} about the vertical. */
	private static void level(PoseStack.Pose pose, VertexConsumer buffer, float height, float size, float turn, int colour, float alpha) {
		if (alpha <= 0.003F) {
			return;
		}
		float cos = Mth.cos(turn) * size;
		float sin = Mth.sin(turn) * size;
		quad(pose, buffer,
			-cos + sin, height, -sin - cos, 0.0F, 0.0F, colour, alpha,
			cos + sin, height, sin - cos, 1.0F, 0.0F, colour, alpha,
			cos - sin, height, sin + cos, 1.0F, 1.0F, colour, alpha,
			-cos - sin, height, -sin + cos, 0.0F, 1.0F, colour, alpha);
	}

	/** One quad, wound both ways: the {@code eyes} pipeline culls, and all of this is seen from either side. */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
		float ax, float ay, float az, float au, float av, int aColour, float aAlpha,
		float bx, float by, float bz, float bu, float bv, int bColour, float bAlpha,
		float cx, float cy, float cz, float cu, float cv, int cColour, float cAlpha,
		float dx, float dy, float dz, float du, float dv, int dColour, float dAlpha) {
		put(pose, buffer, ax, ay, az, au, av, aColour, aAlpha);
		put(pose, buffer, bx, by, bz, bu, bv, bColour, bAlpha);
		put(pose, buffer, cx, cy, cz, cu, cv, cColour, cAlpha);
		put(pose, buffer, dx, dy, dz, du, dv, dColour, dAlpha);
		put(pose, buffer, dx, dy, dz, du, dv, dColour, dAlpha);
		put(pose, buffer, cx, cy, cz, cu, cv, cColour, cAlpha);
		put(pose, buffer, bx, by, bz, bu, bv, bColour, bAlpha);
		put(pose, buffer, ax, ay, az, au, av, aColour, aAlpha);
	}

	private static void put(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int colour, float alpha) {
		buffer.addVertex(pose, x, y, z)
			.setColor((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF, Mth.clamp(Math.round(alpha * 255.0F), 0, 255))
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	private static int mix(int from, int to, float share) {
		int r = Math.round(Mth.lerp(share, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(share, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(share, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}
}
