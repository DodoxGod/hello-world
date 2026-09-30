package dev.forja.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.forja.Forja;
import dev.forja.registry.ModParticles;
import dev.forja.world.StarChart;
import dev.forja.world.StarYard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The sky, the fog and the ash of the Cementerio entre Estrellas (docs/HERRERO_DIMENSION.md, 2.7 and 2.8).
 *
 * <p>All of it client side and cheap: a few hundred quads a frame handed to the level's submit pass the
 * way {@link EventSkyRenderer} hangs an event's sky, a colour and two distances bent in the fog, and a
 * dozen particles a tick.
 *
 * <ul>
 *   <li><b>Above:</b> the void's own stars on top of vanilla's, a band of nebula, and eight
 *       constellations that are the outlines of weapon moulds. Every forty seconds one of them is
 *       "poured": a thread of gold runs round its lines, holds, and cools.</li>
 *   <li><b>Below:</b> the bottom of the void is burning. Under the horizon the sky runs from dark violet
 *       to ember orange straight down, the stars there fade into it, and embers rise out of it.</li>
 *   <li><b>Around:</b> the fog is the ash in the air: violet, tinted towards the ember glow the further
 *       down you look and the lower you are, and the ash itself thickens as you go down.</li>
 * </ul>
 */
public final class StarYardSky {
	private static final Identifier GLOW = Forja.id("textures/environment/resplandor.png");
	private static final Identifier FLAT = Forja.id("textures/environment/plano.png");
	private static final Identifier CORONA = Forja.id("textures/environment/corona.png");

	/** For the footage: seconds added to the sky's clock, to show it minutes apart without waiting minutes. */
	public static float skewSeconds = 0.0F;

	/** For the footage: the ash haze put out, to show the plateau as it is without it. */
	public static boolean fogOff = false;

	/** One slot of the frame's submit order per layer, before anything translucent: see EventSkyRenderer#layer. */
	private static final int FIRST_LAYER = -96;

	/** Shooting stars: about four a minute, each a fifth to a third of a second of light with a short tail. */
	private static final float SHOOTING_PER_TICK = 4.0F / 1200.0F;
	private static final List<Shooting> SHOOTING = new ArrayList<>();
	/** How often a constellation is poured, and how the pour is timed inside that. */
	private static final float POUR_EVERY = 40.0F;
	private static final float POUR_RUN = 6.0F;
	private static final float POUR_HOLD = 4.0F;
	private static final float POUR_COOL = 3.0F;

	private static final int VIOLET = 0x1B1624;
	private static final int GARNET = 0x5A1A12;
	private static final int EMBER = 0xFF6A1E;

	private static final List<Star> STARS = new ArrayList<>();

	private record Star(Vec3 at, float size, int colour, float speed, float phase) {
	}

	/** A shooting star: where it starts, the way it goes (a unit tangent), how far, and its life in ticks. */
	private static final class Shooting {
		final Vec3 from;
		final Vec3 way;
		final float sweep;
		final int life;
		int age;

		Shooting(Vec3 from, Vec3 way, float sweep, int life) {
			this.from = from;
			this.way = way;
			this.sweep = sweep;
			this.life = life;
		}

		/** Where the head is after {@code share} of its run: along a great circle from where it started. */
		Vec3 at(float share) {
			float angle = this.sweep * share;
			return this.from.scale(Mth.cos(angle)).add(this.way.scale(Mth.sin(angle)));
		}
	}

	private StarYardSky() {
	}

	public static void register() {
		RandomSource random = RandomSource.create(0x5EEDL);
		for (int i = 0; i < 900; i++) {
			Vec3 at = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
			float pick = random.nextFloat();
			int colour = pick < 0.55F ? 0xF4F2FF : pick < 0.78F ? 0xAFC6FF : pick < 0.95F ? 0xFFD88A : 0xFF8A70;
			float size = 0.18F + random.nextFloat() * random.nextFloat() * 0.55F;
			STARS.add(new Star(at, size, colour, 0.4F + random.nextFloat() * 1.6F, random.nextFloat() * Mth.TWO_PI));
		}
		LevelRenderEvents.COLLECT_SUBMITS.register(StarYardSky::collect);
		ClientTickEvents.END_CLIENT_TICK.register(StarYardSky::tick);
	}

	/** Whether the client is in the Cementerio entre Estrellas. */
	public static boolean here() {
		ClientLevel level = Minecraft.getInstance().level;
		return level != null && level.dimension() == StarYard.LEVEL;
	}

	// ------------------------------------------------------------------ the constellations (world/StarChart)

	/** Which constellation is being poured at this game time, and how many seconds into its pour. */
	public static int pouredAt(long gameTime) {
		return (int) ((gameTime % 1_728_000L) / 20.0F / POUR_EVERY) % StarChart.CONSTELLATIONS.size();
	}

	public static float pourSecondsAt(long gameTime) {
		return ((gameTime % 1_728_000L) / 20.0F) % POUR_EVERY;
	}

	/** Where in the sky constellation {@code i} is at this game time, as a direction from the viewer. */
	public static Vec3 constellationAt(int i, long gameTime) {
		List<Vec3> stars = StarChart.CONSTELLATIONS.get(i).stars();
		Vec3 sum = Vec3.ZERO;
		for (Vec3 star : stars) {
			sum = sum.add(star);
		}
		return StarChart.turn(sum.normalize(), StarChart.spin((gameTime % 1_728_000L) / 20.0));
	}

	/** The names of the moulds in the sky, in the order they are poured: for the tests and the guide. */
	public static List<String> constellations() {
		return StarChart.CONSTELLATIONS.stream().map(StarChart.Constellation::name).toList();
	}

	// ------------------------------------------------------------------ drawing

	private static OrderedSubmitNodeCollector layer(SubmitNodeCollector collector, int layer) {
		return collector.order(FIRST_LAYER + layer);
	}

	private static void collect(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (!here()) {
			return;
		}
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float time = (client.level.getGameTime() % 1_728_000L + partial) / 20.0F + skewSeconds;
		float radius = Mth.clamp(SkyMood.clearTo * 0.72F, 48.0F, 120.0F);
		float spin = StarChart.spin(time);
		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();

		// The burning floor of the void: a bowl of colour under the horizon, and the sun at the bottom of it.
		float heat = sunStrength(client.gameRenderer.mainCamera().position().y);
		if (heat > 0.0F) {
			layer(collector, 0).submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> abyss(p, buffer, radius, heat));
		}
		if (heat > 0.0F) {
			sun(collector, pose, radius, time, heat);
		}
		// The nebula, then the stars, then the moulds' lines and their stars over them.
		// The nebula drifts a little faster than the stars and breathes, so the band is seen to live.
		float drift = spin * 1.35F + 0.04F * Mth.sin(time * 0.013F);
		float breathe = 0.8F + 0.2F * Mth.sin(time * Mth.TWO_PI / 23.0F);
		layer(collector, 12).submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> nebula(p, buffer, radius, drift, breathe, time));
		layer(collector, 16).submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> shooting(p, buffer, radius, partial));
		layer(collector, 13).submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			for (Star star : STARS) {
				Vec3 at = StarChart.turn(star.at(), spin);
				float alpha = starlight(at) * (0.7F + 0.3F * Mth.sin(time * star.speed() + star.phase()));
				sprite(p, buffer, at, radius, star.size() * radius / 100.0F, star.colour(), alpha);
			}
		});
		int poured = (int) (time / POUR_EVERY) % StarChart.CONSTELLATIONS.size();
		float into = time % POUR_EVERY;
		layer(collector, 14).submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> {
			for (int i = 0; i < StarChart.CONSTELLATIONS.size(); i++) {
				StarChart.Constellation c = StarChart.CONSTELLATIONS.get(i);
				lines(p, buffer, c, radius, spin, 1.0F, 0x9FB4FF, 0.28F, 0.0045F);
				if (i == poured) {
					lines(p, buffer, c, radius * 0.995F, spin, pourShare(into), 0xFFC24A, pourAlpha(into), 0.0075F);
				}
			}
		});
		layer(collector, 15).submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			for (int i = 0; i < StarChart.CONSTELLATIONS.size(); i++) {
				StarChart.Constellation c = StarChart.CONSTELLATIONS.get(i);
				boolean hot = i == poured && into < POUR_RUN + POUR_HOLD + POUR_COOL;
				for (Vec3 star : c.stars()) {
					Vec3 at = StarChart.turn(star, spin);
					float alpha = starlight(at);
					sprite(p, buffer, at, radius * 0.99F, radius * 0.012F, hot ? 0xFFE2A0 : 0xEAF0FF, alpha);
					sprite(p, buffer, at, radius * 0.99F, radius * 0.03F, hot ? 0xFFA030 : 0x8FA8FF, 0.35F * alpha);
				}
			}
		});
	}

	// ------------------------------------------------------------------ the sun under the void

	private static final Vec3 DOWN = new Vec3(0.0, -1.0, 0.0);
	private static final int RAYS = 28;

	/**
	 * How strong the sun below burns for a camera at this height: all of it up to 30 above the arena,
	 * gone by 90 above it. It has to go: the sun hangs inside the distance the fog leaves clear, so from
	 * high up the plateau is further away than the sun is, and the sun was drawn in front of it.
	 */
	static float sunStrength(double y) {
		return StarChart.sunStrength(y);
	}

	/**
	 * A sun far below the plateau (Andy, 2026-09-29: "como si estuvieras encima de un sol"): a wide red
	 * haze, long rays turning slowly round it, two coronas turning against each other, a white-hot core
	 * that is too bright to look at, and a shimmer of heat round the core. Each in its own layer, the
	 * furthest first, for the reason EventSkyRenderer#layer gives.
	 */
	private static void sun(SubmitNodeCollector collector, PoseStack pose, float radius, float time, float heat) {
		float breath = 1.0F + 0.08F * Mth.sin(time * Mth.TWO_PI / 7.0F);
		layer(collector, 1).submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			sprite(p, buffer, DOWN, radius * 0.99F, radius * 1.9F * breath, 0xFF3A0C, 0.6F * heat);
			sprite(p, buffer, DOWN, radius * 0.985F, radius * 1.25F * breath, 0xFF6A1A, 0.55F * heat);
		});
		layer(collector, 2).submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> rays(p, buffer, radius * 0.98F, time, heat));
		layer(collector, 3).submitCustomGeometry(pose, RenderTypes.eyes(CORONA), (p, buffer) ->
			rolled(p, buffer, DOWN, radius * 0.975F, radius * 1.05F * breath, time * 0.03F, 0xFF9A30, 0.8F * heat));
		layer(collector, 4).submitCustomGeometry(pose, RenderTypes.eyes(CORONA), (p, buffer) ->
			rolled(p, buffer, DOWN, radius * 0.97F, radius * 0.75F, -time * 0.05F, 0xFFD27A, 0.9F * heat));
		layer(collector, 5).submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			// The heat coming off it: blobs of light wandering round the core, never quite still.
			for (int i = 0; i < 12; i++) {
				float phase = i * 2.39996F;
				float out = radius * (0.2F + 0.16F * (0.5F + 0.5F * Mth.sin(time * 0.7F + phase)));
				float angle = phase + time * (0.05F + 0.02F * (i % 3));
				Vec3 at = DOWN.add(new Vec3(Mth.cos(angle) * out / radius, 0.0, Mth.sin(angle) * out / radius)).normalize();
				float flicker = 0.5F + 0.5F * Mth.sin(time * 3.1F + phase * 1.7F);
				sprite(p, buffer, at, radius * 0.965F, radius * (0.1F + 0.05F * flicker), 0xFFB050, 0.35F * flicker * heat);
			}
		});
		layer(collector, 6).submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			sprite(p, buffer, DOWN, radius * 0.96F, radius * 0.5F * breath, 0xFFE9A0, 1.0F * heat);
			sprite(p, buffer, DOWN, radius * 0.955F, radius * 0.3F, 0xFFFBE8, 1.0F * heat);
			sprite(p, buffer, DOWN, radius * 0.95F, radius * 0.18F, 0xFFFFFF, heat);
		});
	}

	/** Long thin rays out from the sun, each its own length and flicker, the whole crown turning slowly. */
	private static void rays(PoseStack.Pose p, VertexConsumer buffer, float radius, float time, float heat) {
		for (int i = 0; i < RAYS; i++) {
			float angle = i * Mth.TWO_PI / RAYS + time * 0.015F + 0.3F * Mth.sin(i * 1.7F);
			float length = 0.8F + 0.6F * (0.5F + 0.5F * Mth.sin(i * 2.3F + time * 0.21F));
			float width = 0.018F + 0.02F * (0.5F + 0.5F * Mth.sin(i * 3.7F));
			float alpha = heat * (0.35F + 0.25F * Mth.sin(time * 1.3F + i));
			Vec3 along = new Vec3(Mth.cos(angle), 0.0, Mth.sin(angle));
			Vec3 across = new Vec3(-Mth.sin(angle), 0.0, Mth.cos(angle));
			float inner = 0.18F;
			Vec3 a = DOWN.add(along.scale(inner)).add(across.scale(width)).normalize();
			Vec3 b = DOWN.add(along.scale(inner)).subtract(across.scale(width)).normalize();
			Vec3 tip = DOWN.add(along.scale(inner + length)).normalize();
			quad(p, buffer,
				scaled(a, radius), 0.0F, 0.0F, 0xFFE0A0, alpha,
				scaled(b, radius), 1.0F, 0.0F, 0xFFE0A0, alpha,
				scaled(tip, radius), 1.0F, 1.0F, 0xFF6A1E, 0.0F,
				scaled(tip, radius), 0.0F, 1.0F, 0xFF6A1E, 0.0F);
		}
	}

	/** A square sprite out along a direction, turned by {@code roll} about that line. */
	private static void rolled(PoseStack.Pose p, VertexConsumer buffer, Vec3 towards, float distance, float size, float roll, int colour, float alpha) {
		Vec3 up = Math.abs(towards.y) > 0.98 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 right = towards.cross(up).normalize();
		Vec3 above = right.cross(towards).normalize();
		float cos = Mth.cos(roll);
		float sin = Mth.sin(roll);
		Vec3 r = right.scale(cos).add(above.scale(sin)).scale(size);
		Vec3 a = above.scale(cos).subtract(right.scale(sin)).scale(size);
		Vec3 centre = towards.scale(distance);
		quad(p, buffer,
			scaled(centre.subtract(r).subtract(a), 1.0F), 0.0F, 1.0F, colour, alpha,
			scaled(centre.add(r).subtract(a), 1.0F), 1.0F, 1.0F, colour, alpha,
			scaled(centre.add(r).add(a), 1.0F), 1.0F, 0.0F, colour, alpha,
			scaled(centre.subtract(r).add(a), 1.0F), 0.0F, 0.0F, colour, alpha);
	}

	/** How much of the poured constellation's outline the gold has run round, 0 to 1. */
	static float pourShare(float into) {
		return Mth.clamp(into / POUR_RUN, 0.0F, 1.0F);
	}

	static float pourAlpha(float into) {
		if (into < POUR_RUN + POUR_HOLD) {
			return 0.95F;
		}
		return 0.95F * Mth.clamp(1.0F - (into - POUR_RUN - POUR_HOLD) / POUR_COOL, 0.0F, 1.0F);
	}

	/**
	 * How much a star shows where it is: full above 4 degrees, faded in over the horizon's first four, not
	 * at all hard on the horizon (where it would be drawn over far ground), and fading into the glow below.
	 */
	private static float starlight(Vec3 at) {
		float elevation = (float) Math.toDegrees(Math.asin(Mth.clamp(at.y, -1.0, 1.0)));
		if (elevation >= 4.0F) {
			return 1.0F;
		}
		if (elevation >= 0.5F) {
			return (elevation - 0.5F) / 3.5F;
		}
		if (elevation > -4.0F) {
			return 0.0F;
		}
		// Below: in from nothing by ten degrees down, then out into the glow by thirty-five.
		float in = Mth.clamp((-4.0F - elevation) / 6.0F, 0.0F, 1.0F);
		float out = Mth.clamp((elevation + 35.0F) / 25.0F, 0.0F, 1.0F);
		return 0.6F * in * out;
	}

	/** The bowl under the horizon: violet at its rim, garnet at thirty degrees down, ember at the bottom. */
	private static void abyss(PoseStack.Pose p, VertexConsumer buffer, float radius, float heat) {
		int rings = 12;
		int segments = 36;
		for (int ring = 0; ring < rings; ring++) {
			float d0 = (float) ring / rings;
			float d1 = (float) (ring + 1) / rings;
			float e0 = -d0 * Mth.HALF_PI;
			float e1 = -d1 * Mth.HALF_PI;
			int c0 = depthColour(d0);
			int c1 = depthColour(d1);
			float a0 = depthAlpha(d0) * heat;
			float a1 = depthAlpha(d1) * heat;
			for (int seg = 0; seg < segments; seg++) {
				float t0 = seg * Mth.TWO_PI / segments;
				float t1 = (seg + 1) * Mth.TWO_PI / segments;
				quad(p, buffer,
					point(t0, e0, radius), 0.0F, 0.0F, c0, a0,
					point(t1, e0, radius), 1.0F, 0.0F, c0, a0,
					point(t1, e1, radius), 1.0F, 1.0F, c1, a1,
					point(t0, e1, radius), 0.0F, 1.0F, c1, a1);
			}
		}
	}

	private static int depthColour(float depth) {
		return depth < 0.33F ? mix(VIOLET, GARNET, depth / 0.33F) : mix(GARNET, EMBER, (depth - 0.33F) / 0.67F);
	}

	private static float depthAlpha(float depth) {
		return depth < 0.33F ? 0.45F * depth / 0.33F : 0.45F + 0.45F * (depth - 0.33F) / 0.67F;
	}

	private static float[] point(float bearing, float elevation, float radius) {
		float flat = Mth.cos(elevation) * radius;
		return new float[] {Mth.cos(bearing) * flat, Mth.sin(elevation) * radius, Mth.sin(bearing) * flat};
	}

	/** A band of nebula along a tilted great circle: faint, violet going amber, soft at both edges. */
	private static void nebula(PoseStack.Pose p, VertexConsumer buffer, float radius, float spin, float breathe, float time) {
		int steps = 72;
		Vec3 a = new Vec3(1.0, 0.25, 0.3).normalize();
		Vec3 b = a.cross(new Vec3(0.2, 0.9, -0.4)).normalize();
		Vec3 axis = a.cross(b).normalize();
		for (int i = 0; i < steps; i++) {
			float t0 = i * Mth.TWO_PI / steps;
			float t1 = (i + 1) * Mth.TWO_PI / steps;
			float width0 = 0.10F + 0.05F * Mth.sin(t0 * 3.0F + 1.0F);
			float width1 = 0.10F + 0.05F * Mth.sin(t1 * 3.0F + 1.0F);
			Vec3 m0 = a.scale(Mth.cos(t0)).add(b.scale(Mth.sin(t0)));
			Vec3 m1 = a.scale(Mth.cos(t1)).add(b.scale(Mth.sin(t1)));
			int c0 = mix(0x6A3FA0, 0xC07830, 0.5F + 0.5F * Mth.sin(t0 * 2.0F));
			int c1 = mix(0x6A3FA0, 0xC07830, 0.5F + 0.5F * Mth.sin(t1 * 2.0F));
			// Its brighter knots slide slowly along the band.
			float alpha0 = 0.2F * breathe * (0.7F + 0.3F * Mth.sin(t0 * 5.0F - time * 0.02F));
			float alpha1 = 0.2F * breathe * (0.7F + 0.3F * Mth.sin(t1 * 5.0F - time * 0.02F));
			for (int side = -1; side <= 1; side += 2) {
				Vec3 e0 = StarChart.turn(m0.add(axis.scale(side * width0)).normalize(), spin);
				Vec3 e1 = StarChart.turn(m1.add(axis.scale(side * width1)).normalize(), spin);
				Vec3 c0v = StarChart.turn(m0, spin);
				Vec3 c1v = StarChart.turn(m1, spin);
				float s0 = starlight(c0v) * alpha0;
				float s1 = starlight(c1v) * alpha1;
				quad(p, buffer,
					scaled(c0v, radius), 0.0F, 0.0F, c0, s0,
					scaled(c1v, radius), 1.0F, 0.0F, c1, s1,
					scaled(e1, radius), 1.0F, 1.0F, c1, 0.0F,
					scaled(e0, radius), 0.0F, 1.0F, c0, 0.0F);
			}
		}
	}

	/** The shooting stars now in the sky: a bright head and a tail that thins to nothing behind it. */
	private static void shooting(PoseStack.Pose p, VertexConsumer buffer, float radius, float partial) {
		for (Shooting star : List.copyOf(SHOOTING)) {
			float through = Mth.clamp((star.age + partial) / star.life, 0.0F, 1.0F);
			float alpha = Math.min(1.0F, through * 5.0F) * (1.0F - through * through);
			Vec3 head = star.at(through);
			Vec3 tail = star.at(Math.max(0.0F, through - 0.35F));
			Vec3 across = head.subtract(tail).cross(head).normalize().scale(0.0035);
			quad(p, buffer,
				scaled(tail, radius), 0.0F, 0.0F, 0x9FB4FF, 0.0F,
				scaled(head.subtract(across).normalize(), radius), 1.0F, 0.0F, 0xFFFFFF, alpha,
				scaled(head.add(across).normalize(), radius), 1.0F, 1.0F, 0xFFFFFF, alpha,
				scaled(tail, radius), 0.0F, 1.0F, 0x9FB4FF, 0.0F);
		}
	}

	/** Sends a shooting star across the sky, somewhere well above the horizon. For the footage too. */
	public static void shoot(RandomSource random) {
		Vec3 from = new Vec3(random.nextGaussian(), 0.6 + random.nextDouble() * 1.2, random.nextGaussian()).normalize();
		Vec3 side = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.3, random.nextGaussian());
		Vec3 way = side.subtract(from.scale(side.dot(from))).normalize();
		if (way.y > 0.0) {
			way = way.scale(-1.0);
		}
		SHOOTING.add(new Shooting(from, way, 0.25F + random.nextFloat() * 0.3F, 5 + random.nextInt(4)));
	}

	/** The lines of a constellation, drawn as far round as {@code share} of its whole length. */
	private static void lines(PoseStack.Pose p, VertexConsumer buffer, StarChart.Constellation c, float radius, float spin,
		float share, int colour, float alpha, float width) {
		if (alpha <= 0.003F || share <= 0.0F) {
			return;
		}
		float left = share * c.length();
		for (int[] line : c.lines()) {
			Vec3 from = c.stars().get(line[0]);
			Vec3 to = c.stars().get(line[1]);
			float length = (float) from.subtract(to).length();
			if (left <= 0.0F) {
				return;
			}
			float part = Math.min(1.0F, left / length);
			left -= length;
			Vec3 end = from.add(to.subtract(from).scale(part));
			segment(p, buffer, StarChart.turn(from, spin), StarChart.turn(end, spin), radius, colour, alpha, width);
		}
	}

	private static void segment(PoseStack.Pose p, VertexConsumer buffer, Vec3 from, Vec3 to, float radius, int colour, float alpha, float width) {
		int pieces = 4;
		for (int i = 0; i < pieces; i++) {
			Vec3 a = from.add(to.subtract(from).scale((double) i / pieces)).normalize();
			Vec3 b = from.add(to.subtract(from).scale((double) (i + 1) / pieces)).normalize();
			float la = starlight(a) * alpha;
			float lb = starlight(b) * alpha;
			if (la <= 0.003F && lb <= 0.003F) {
				continue;
			}
			Vec3 across = b.subtract(a).cross(a).normalize().scale(width);
			quad(p, buffer,
				scaled(a.subtract(across).normalize(), radius), 0.0F, 0.0F, colour, la,
				scaled(b.subtract(across).normalize(), radius), 1.0F, 0.0F, colour, lb,
				scaled(b.add(across).normalize(), radius), 1.0F, 1.0F, colour, lb,
				scaled(a.add(across).normalize(), radius), 0.0F, 1.0F, colour, la);
		}
	}

	private static float[] scaled(Vec3 at, float radius) {
		return new float[] {(float) at.x * radius, (float) at.y * radius, (float) at.z * radius};
	}

	/** A small square sprite out along a direction, turned to face the viewer. */
	private static void sprite(PoseStack.Pose p, VertexConsumer buffer, Vec3 towards, float distance, float size, int colour, float alpha) {
		if (alpha <= 0.003F) {
			return;
		}
		Vec3 up = Math.abs(towards.y) > 0.98 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 right = towards.cross(up).normalize().scale(size);
		Vec3 above = right.cross(towards).normalize().scale(size);
		Vec3 centre = towards.scale(distance);
		quad(p, buffer,
			scaled(centre.subtract(right).subtract(above), 1.0F), 0.0F, 1.0F, colour, alpha,
			scaled(centre.add(right).subtract(above), 1.0F), 1.0F, 1.0F, colour, alpha,
			scaled(centre.add(right).add(above), 1.0F), 1.0F, 0.0F, colour, alpha,
			scaled(centre.subtract(right).add(above), 1.0F), 0.0F, 0.0F, colour, alpha);
	}

	/** One quad, wound both ways (the eyes pipeline culls). */
	private static void quad(PoseStack.Pose p, VertexConsumer buffer,
		float[] a, float au, float av, int ac, float aa,
		float[] b, float bu, float bv, int bc, float ba,
		float[] c, float cu, float cv, int cc, float ca,
		float[] d, float du, float dv, int dc, float da) {
		put(p, buffer, a, au, av, ac, aa);
		put(p, buffer, b, bu, bv, bc, ba);
		put(p, buffer, c, cu, cv, cc, ca);
		put(p, buffer, d, du, dv, dc, da);
		put(p, buffer, d, du, dv, dc, da);
		put(p, buffer, c, cu, cv, cc, ca);
		put(p, buffer, b, bu, bv, bc, ba);
		put(p, buffer, a, au, av, ac, aa);
	}

	private static void put(PoseStack.Pose p, VertexConsumer buffer, float[] at, float u, float v, int colour, float alpha) {
		buffer.addVertex(p, at[0], at[1], at[2])
			.setColor((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF, Mth.clamp(Math.round(alpha * 255.0F), 0, 255))
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0)
			.setNormal(p, 0.0F, 1.0F, 0.0F);
	}

	private static int mix(int from, int to, float share) {
		share = Mth.clamp(share, 0.0F, 1.0F);
		int r = Math.round(Mth.lerp(share, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(share, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(share, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}

	// ------------------------------------------------------------------ the fog

	/**
	 * Bends the fog in the graveyard: its colour towards the ember glow the further down the camera
	 * looks and the lower it is, and its reach opened up high in the air (and put out for the footage).
	 */
	public static void fog(FogData data, Camera camera) {
		// Only the camera's pitch and height decide it, never what is in view: it used to ask whether the
		// camera stood over the void, and one step off the edge snapped the whole sky from maroon to orange
		// (Andy, 2026-09-29). Eased towards its target so no turn of the head can jump it either.
		float target = emberShare(-camera.forwardVector().y(), camera.position().y);
		long now = net.minecraft.util.Util.getMillis();
		float dt = emberAt == 0L ? 1.0F : Mth.clamp((now - emberAt) / 1000.0F, 0.0F, 1.0F);
		emberAt = now;
		emberShown += (target - emberShown) * (1.0F - (float) Math.exp(-dt * 4.0));
		float share = emberShown;
		if (share > 0.0F) {
			data.color.set(
				Mth.lerp(share, data.color.x(), 1.0F),
				Mth.lerp(share, data.color.y(), 0.42F),
				Mth.lerp(share, data.color.z(), 0.12F),
				data.color.w());
		}
		float open = fogOff ? 1.0F : (float) Mth.clamp((camera.position().y - 110.0) / 40.0, 0.0, 1.0);
		if (open > 0.0F) {
			data.environmentalStart = Mth.lerp(open, data.environmentalStart, data.renderDistanceEnd * 4.0F);
			data.environmentalEnd = Mth.lerp(open, data.environmentalEnd, data.renderDistanceEnd * 4.0F + 64.0F);
		}
	}

	/** The fog's ember tint as last shown, and when: eased so it never jumps. */
	private static float emberShown = 0.0F;
	private static long emberAt = 0L;

	/**
	 * How far the fog leans to the void's ember glow, from how far down the camera looks (-1 to 1) and
	 * how high it is. Smooth in both: looking down takes it up to 0.45, and being low takes it up to 0.75
	 * (half at y 20). Fading, like the sun, as the camera climbs over the plateau.
	 */
	public static float emberShare(float down, double y) {
		return StarChart.emberShare(down, y);
	}

	// ------------------------------------------------------------------ ash and embers

	/**
	 * Whether nothing at all stands in this column: off the plateau and the islets, over the open void.
	 * Read off the client's own heightmap, because the shape of the plateau is the world's seed's and the
	 * client is never told the seed.
	 */
	private static boolean open(ClientLevel level, int x, int z) {
		return level == null || level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) <= level.getMinY();
	}

	// ------------------------------------------------------------------ distant echoes

	/**
	 * A far-off hammer, a bell, a chime or falling rubble, heard as an echo of a time long gone (Andy,
	 * 2026-09-29): somewhere 40 to 80 blocks away, low (pitch 0.5 to 0.75) and soft, and then again two
	 * or three times, later and fainter and a shade lower each time, like the tail of a great hall's
	 * reverb. Vanilla has no low-pass to muffle with, so the sounds are the ones that already ring or
	 * rumble, and distance does the rest. Only here, and sparse: one every 8 to 25 seconds.
	 */
	private static final net.minecraft.sounds.SoundEvent[] ECHOED = {
		net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundEvents.ANVIL_USE,
		net.minecraft.sounds.SoundEvents.BELL_RESONATE, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
		net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE, net.minecraft.sounds.SoundEvents.GRINDSTONE_USE,
		net.minecraft.sounds.SoundEvents.CHAIN_PLACE, net.minecraft.sounds.SoundEvents.BASALT_BREAK
	};
	/** The delays of the echo, in ticks after the first sound, and how much quieter each one is. */
	private static final int[] ECHO_DELAYS = {8, 18, 30};
	private static final float ECHO_FADE = 0.55F;

	private record Echo(net.minecraft.sounds.SoundEvent sound, double x, double y, double z, float volume, float pitch, long at) {
	}

	private static final List<Echo> ECHOES = new ArrayList<>();
	private static int nextEcho = 200;

	private static void echoes(ClientLevel level, Player player, RandomSource random) {
		long now = level.getGameTime();
		ECHOES.removeIf(echo -> {
			if (echo.at() > now) {
				return false;
			}
			level.playLocalSound(echo.x(), echo.y(), echo.z(), echo.sound(), net.minecraft.sounds.SoundSource.AMBIENT,
				echo.volume(), echo.pitch(), false);
			return true;
		});
		if (--nextEcho > 0) {
			return;
		}
		nextEcho = 160 + random.nextInt(340);
		net.minecraft.sounds.SoundEvent sound = ECHOED[random.nextInt(ECHOED.length)];
		double angle = random.nextDouble() * Math.PI * 2.0;
		double far = 40.0 + random.nextDouble() * 40.0;
		double x = player.getX() + Math.cos(angle) * far;
		double z = player.getZ() + Math.sin(angle) * far;
		double y = player.getY() + (random.nextDouble() - 0.5) * 20.0;
		// Heard from this far only if it is loud: the game fades a sound out over sixteen blocks per
		// point of volume, so five points carry it to eighty and it arrives here a quarter as loud.
		float volume = 5.0F;
		float pitch = 0.5F + random.nextFloat() * 0.25F;
		ECHOES.add(new Echo(sound, x, y, z, volume, pitch, now));
		int repeats = 2 + random.nextInt(2);
		float fade = 1.0F;
		for (int i = 0; i < repeats; i++) {
			fade *= ECHO_FADE;
			// Each echo comes back off something a little further away and to the side.
			double off = far + 8.0 * (i + 1);
			double turn = angle + (random.nextDouble() - 0.5) * 0.6;
			ECHOES.add(new Echo(sound, player.getX() + Math.cos(turn) * off, y, player.getZ() + Math.sin(turn) * off,
				volume * fade, Math.max(0.4F, pitch - 0.04F * (i + 1)), now + ECHO_DELAYS[i]));
		}
	}

	/** How much ash is in the air at this height: light at the arena, eight times as thick at y 20. */
	static float ashAt(double y) {
		if (y >= StarYard.SURFACE) {
			return 0.4F;
		}
		if (y >= 60.0) {
			return Mth.lerp((float) ((StarYard.SURFACE - y) / 20.0), 0.4F, 1.0F);
		}
		if (y >= 40.0) {
			return Mth.lerp((float) ((60.0 - y) / 20.0), 1.0F, 4.0F);
		}
		return Mth.lerp((float) Mth.clamp((40.0 - y) / 20.0, 0.0, 1.0), 4.0F, 7.0F);
	}

	private static void tick(Minecraft client) {
		if (!here() || client.isPaused() || client.player == null) {
			return;
		}
		ClientLevel level = client.level;
		Player player = client.player;
		RandomSource random = level.getRandom();
		echoes(level, player, random);
		// The shooting stars: age the ones in flight, now and then send another.
		SHOOTING.removeIf(star -> ++star.age > star.life);
		if (random.nextFloat() < SHOOTING_PER_TICK) {
			shoot(random);
		}
		// Embers out of the burning floor, anywhere under the player that is not under the plateau.
		for (int i = 0; i < 3; i++) {
			double x = player.getX() + (random.nextDouble() - 0.5) * 80.0;
			double z = player.getZ() + (random.nextDouble() - 0.5) * 80.0;
			double y = Math.max(2.0, player.getY() - 10.0 - random.nextDouble() * 50.0);
			int bx = Mth.floor(x);
			int bz = Mth.floor(z);
			if (!open(level, bx, bz) && y > 8.0) {
				continue;
			}
			level.addParticle(ModParticles.BRASA, true, false, x, y, z, 0.0, 0.03, 0.0);
		}
		// Ash, thicker the lower it is: tried all round the player, kept by how much ash its height holds.
		for (int i = 0; i < 12; i++) {
			double x = player.getX() + (random.nextDouble() - 0.5) * 32.0;
			double y = player.getY() - 24.0 + random.nextDouble() * 34.0;
			double z = player.getZ() + (random.nextDouble() - 0.5) * 32.0;
			if (random.nextFloat() * 7.0F > ashAt(y) - 0.4F) {
				continue;
			}
			if (!level.getBlockState(net.minecraft.core.BlockPos.containing(x, y, z)).isAir()) {
				continue;
			}
			level.addParticle(ModParticles.CENIZA, true, false, x, y, z,
				(random.nextDouble() - 0.5) * 0.2, -0.05, (random.nextDouble() - 0.5) * 0.2);
		}
	}
}
