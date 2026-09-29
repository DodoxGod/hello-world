package dev.forja.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.forja.Forja;
import dev.forja.registry.ModParticles;
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

	/** For the footage: the ash haze put out, to show the plateau as it is without it. */
	public static boolean fogOff = false;

	/** One slot of the frame's submit order per layer, before anything translucent: see EventSkyRenderer#layer. */
	private static final int FIRST_LAYER = -96;

	/** A turn of the whole sky about the north-south line every forty minutes. */
	private static final float TURN_SECONDS = 2400.0F;
	/** How often a constellation is poured, and how the pour is timed inside that. */
	private static final float POUR_EVERY = 40.0F;
	private static final float POUR_RUN = 6.0F;
	private static final float POUR_HOLD = 4.0F;
	private static final float POUR_COOL = 3.0F;

	private static final int VIOLET = 0x1B1624;
	private static final int GARNET = 0x5A1A12;
	private static final int EMBER = 0xFF6A1E;

	private static final List<Star> STARS = new ArrayList<>();
	private static final List<Constellation> CONSTELLATIONS = new ArrayList<>();

	private record Star(Vec3 at, float size, int colour, float speed, float phase) {
	}

	/** A mould drawn in stars: its stars, and the lines between them as index pairs, in pouring order. */
	private record Constellation(String name, List<Vec3> stars, int[][] lines, float length) {
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
		buildConstellations();
		LevelRenderEvents.COLLECT_SUBMITS.register(StarYardSky::collect);
		ClientTickEvents.END_CLIENT_TICK.register(StarYardSky::tick);
	}

	/** Whether the client is in the Cementerio entre Estrellas. */
	public static boolean here() {
		ClientLevel level = Minecraft.getInstance().level;
		return level != null && level.dimension() == StarYard.LEVEL;
	}

	// ------------------------------------------------------------------ the constellations

	/**
	 * The eight moulds, each an outline in degrees across the sky round its centre, then hung at a
	 * bearing and a height. Polylines, closed where the mould is closed.
	 */
	private static void buildConstellations() {
		float s = 1.4F;
		add("espada", 10, 40, s, new float[][] {
			{0, 12}, {1.2F, 8}, {1.2F, -2}, {4, -2}, {4, -3}, {1, -3}, {1.8F, -8}, {0, -9.5F}, {-1.8F, -8}, {-1, -3}, {-4, -3}, {-4, -2}, {-1.2F, -2}, {-1.2F, 8}
		}, true);
		add("hacha", 55, 62, s, new float[][] {
			{0.6F, -9}, {0.6F, 4}, {5, 1.5F}, {6, 6}, {5, 10.5F}, {0.6F, 8}, {0.6F, 9.5F}, {-0.6F, 9.5F}, {-0.6F, -9}
		}, true);
		add("martillo", 100, 35, s, new float[][] {
			{0.5F, -9}, {0.5F, 5}, {5, 5}, {5, 9}, {-5, 9}, {-5, 5}, {-0.5F, 5}, {-0.5F, -9}
		}, true);
		add("lanza", 150, 55, s, new float[][] {
			{0, -12}, {0, 6}, {1.5F, 8}, {0, 13}, {-1.5F, 8}, {0, 6}
		}, false);
		add("escudo", 195, 38, s, new float[][] {
			{-5, 6}, {5, 6}, {5, 1}, {3, -4}, {0, -7}, {-3, -4}, {-5, 1}
		}, true);
		add("yunque", 240, 65, s, new float[][] {
			{-8, 4}, {-3, 5}, {5, 5}, {5, 3}, {2, 1.5F}, {2, -2}, {4, -4}, {-4, -4}, {-2, -2}, {-2, 1.5F}, {-4, 2.5F}
		}, true);
		add("tenazas", 285, 42, s, new float[][] {
			{-3, -9}, {1, 3}, {0.5F, 7}, {-1, 9}, {-1, 3}, {3, -9}
		}, false);
		add("guadana", 330, 58, s, new float[][] {
			{0, -10}, {0, 9}, {-4, 10}, {-9, 8}, {-11, 5}, {-7, 7.2F}, {-3, 7.5F}, {0, 7}
		}, false);
	}

	private static void add(String name, float bearing, float height, float scale, float[][] outline, boolean closed) {
		double yaw = Math.toRadians(bearing);
		double pitch = Math.toRadians(height);
		Vec3 centre = new Vec3(Math.cos(pitch) * Math.sin(yaw), Math.sin(pitch), -Math.cos(pitch) * Math.cos(yaw));
		Vec3 east = new Vec3(0.0, 1.0, 0.0).cross(centre).normalize();
		Vec3 north = centre.cross(east).normalize();
		List<Vec3> stars = new ArrayList<>();
		for (float[] point : outline) {
			double u = Math.tan(Math.toRadians(point[0] * scale));
			double v = Math.tan(Math.toRadians(point[1] * scale));
			stars.add(centre.add(east.scale(u)).add(north.scale(v)).normalize());
		}
		int count = closed ? stars.size() : stars.size() - 1;
		int[][] lines = new int[count][];
		float length = 0.0F;
		for (int i = 0; i < count; i++) {
			lines[i] = new int[] {i, (i + 1) % stars.size()};
			length += (float) stars.get(i).subtract(stars.get((i + 1) % stars.size())).length();
		}
		CONSTELLATIONS.add(new Constellation(name, stars, lines, length));
	}

	/** Which constellation is being poured at this game time, and how many seconds into its pour. */
	public static int pouredAt(long gameTime) {
		return (int) ((gameTime % 1_728_000L) / 20.0F / POUR_EVERY) % CONSTELLATIONS.size();
	}

	public static float pourSecondsAt(long gameTime) {
		return ((gameTime % 1_728_000L) / 20.0F) % POUR_EVERY;
	}

	/** Where in the sky constellation {@code i} is at this game time, as a direction from the viewer. */
	public static Vec3 constellationAt(int i, long gameTime) {
		List<Vec3> stars = CONSTELLATIONS.get(i).stars();
		Vec3 sum = Vec3.ZERO;
		for (Vec3 star : stars) {
			sum = sum.add(star);
		}
		return turn(sum.normalize(), (gameTime % 1_728_000L) / 20.0F / TURN_SECONDS * Mth.TWO_PI);
	}

	/** The names of the moulds in the sky, in the order they are poured: for the tests and the guide. */
	public static List<String> constellations() {
		return CONSTELLATIONS.stream().map(Constellation::name).toList();
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
		float time = (client.level.getGameTime() % 1_728_000L + partial) / 20.0F;
		float radius = Mth.clamp(SkyMood.clearTo * 0.72F, 48.0F, 120.0F);
		float spin = time / TURN_SECONDS * Mth.TWO_PI;
		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();

		// The burning floor of the void: a bowl of colour under the horizon, and a slow glow at the bottom.
		layer(collector, 0).submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> abyss(p, buffer, radius));
		float breath = 1.0F + 0.12F * Mth.sin(time * Mth.TWO_PI / 7.0F);
		layer(collector, 1).submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			sprite(p, buffer, new Vec3(0.0, -1.0, 0.0), radius * 0.98F, radius * 1.1F * breath, 0xFF7A2A, 0.55F);
			sprite(p, buffer, new Vec3(0.0, -1.0, 0.0), radius * 0.97F, radius * 0.45F * breath, 0xFFC070, 0.4F);
		});
		// The nebula, then the stars, then the moulds' lines and their stars over them.
		layer(collector, 2).submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> nebula(p, buffer, radius, spin));
		layer(collector, 3).submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			for (Star star : STARS) {
				Vec3 at = turn(star.at(), spin);
				float alpha = starlight(at) * (0.7F + 0.3F * Mth.sin(time * star.speed() + star.phase()));
				sprite(p, buffer, at, radius, star.size() * radius / 100.0F, star.colour(), alpha);
			}
		});
		int poured = (int) (time / POUR_EVERY) % CONSTELLATIONS.size();
		float into = time % POUR_EVERY;
		layer(collector, 4).submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> {
			for (int i = 0; i < CONSTELLATIONS.size(); i++) {
				Constellation c = CONSTELLATIONS.get(i);
				lines(p, buffer, c, radius, spin, 1.0F, 0x9FB4FF, 0.28F, 0.0045F);
				if (i == poured) {
					lines(p, buffer, c, radius * 0.995F, spin, pourShare(into), 0xFFC24A, pourAlpha(into), 0.0075F);
				}
			}
		});
		layer(collector, 5).submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			for (int i = 0; i < CONSTELLATIONS.size(); i++) {
				Constellation c = CONSTELLATIONS.get(i);
				boolean hot = i == poured && into < POUR_RUN + POUR_HOLD + POUR_COOL;
				for (Vec3 star : c.stars()) {
					Vec3 at = turn(star, spin);
					float alpha = starlight(at);
					sprite(p, buffer, at, radius * 0.99F, radius * 0.012F, hot ? 0xFFE2A0 : 0xEAF0FF, alpha);
					sprite(p, buffer, at, radius * 0.99F, radius * 0.03F, hot ? 0xFFA030 : 0x8FA8FF, 0.35F * alpha);
				}
			}
		});
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

	/** A direction in the sky turned about the north-south line by the sky's slow turn. */
	private static Vec3 turn(Vec3 at, float spin) {
		float cos = Mth.cos(spin);
		float sin = Mth.sin(spin);
		return new Vec3(at.x * cos - at.y * sin, at.x * sin + at.y * cos, at.z);
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
	private static void abyss(PoseStack.Pose p, VertexConsumer buffer, float radius) {
		int rings = 12;
		int segments = 36;
		for (int ring = 0; ring < rings; ring++) {
			float d0 = (float) ring / rings;
			float d1 = (float) (ring + 1) / rings;
			float e0 = -d0 * Mth.HALF_PI;
			float e1 = -d1 * Mth.HALF_PI;
			int c0 = depthColour(d0);
			int c1 = depthColour(d1);
			float a0 = depthAlpha(d0);
			float a1 = depthAlpha(d1);
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
	private static void nebula(PoseStack.Pose p, VertexConsumer buffer, float radius, float spin) {
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
			float alpha0 = 0.2F * (0.7F + 0.3F * Mth.sin(t0 * 5.0F));
			float alpha1 = 0.2F * (0.7F + 0.3F * Mth.sin(t1 * 5.0F));
			for (int side = -1; side <= 1; side += 2) {
				Vec3 e0 = turn(m0.add(axis.scale(side * width0)).normalize(), spin);
				Vec3 e1 = turn(m1.add(axis.scale(side * width1)).normalize(), spin);
				Vec3 c0v = turn(m0, spin);
				Vec3 c1v = turn(m1, spin);
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

	/** The lines of a constellation, drawn as far round as {@code share} of its whole length. */
	private static void lines(PoseStack.Pose p, VertexConsumer buffer, Constellation c, float radius, float spin,
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
			segment(p, buffer, turn(from, spin), turn(end, spin), radius, colour, alpha, width);
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
		float down = Math.max(0.0F, -camera.forwardVector().y());
		float low = (float) Mth.clamp((StarYard.SURFACE - camera.position().y) / 120.0, 0.0, 1.0);
		// Looking down onto the plateau, what fills the view is ash and stone, not the void: the glow
		// only takes the fog over where there is void under the camera to look into.
		Vec3 eye = camera.position();
		float overVoid = StarYard.onPlateau(Mth.floor(eye.x), Mth.floor(eye.z)) ? 0.3F : 1.0F;
		float share = Math.max(0.55F * overVoid * down * down * (3.0F - 2.0F * down), low);
		share = Math.min(share, 0.75F);
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

	// ------------------------------------------------------------------ ash and embers

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
		// Embers out of the burning floor, anywhere under the player that is not under the plateau.
		for (int i = 0; i < 3; i++) {
			double x = player.getX() + (random.nextDouble() - 0.5) * 80.0;
			double z = player.getZ() + (random.nextDouble() - 0.5) * 80.0;
			double y = Math.max(2.0, player.getY() - 10.0 - random.nextDouble() * 50.0);
			int bx = Mth.floor(x);
			int bz = Mth.floor(z);
			if (StarYard.onPlateau(bx, bz) && y > StarYard.bottom(bx, bz) - 2) {
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
