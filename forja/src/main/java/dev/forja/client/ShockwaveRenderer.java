package dev.forja.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.forja.Forja;
import dev.forja.entity.Shockwave;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Draws a {@link Shockwave}: first the warning on the floor, then the ring that leaves it.
 *
 * <p>All of it is a handful of strips built fresh every frame from one number, the radius, which is why
 * it is smooth at any frame rate and never breaks up into dots however wide it gets. What makes it
 * read as fire rather than as a coloured hoop is that it is several things stacked: a band on the
 * floor that is white-hot at the front and trails off behind, a curtain of flame standing on that
 * front, a fainter ring following, and a flash where the hammer landed. Any one of them alone looks
 * like a placeholder.
 *
 * <p>Everything goes through the {@code eyes} render type. It is the vanilla one that is lit by itself,
 * blends by alpha, writes no depth and — the reason it was picked over the emissive entity type the
 * melt tank uses — <b>has no alpha cut-off</b>, so a tail can fade all the way to nothing instead of
 * snapping off at ten percent. It does cull back faces, so every quad here is wound both ways.
 *
 * <p>It all lies at <b>one level</b>, the one the blow landed on. It used to be laid over the terrain
 * piece by piece, and over anything that stuck up out of the floor that was a patch of the ring lifted
 * onto the top of a wall, with its edges hanging in the air: Andy's word for it was "raro", and he was
 * right. The floor is still measured ({@link #measure}), but only to find what stands above that
 * level, and those blocks are <b>tinted</b> — the same colours, fainter, on their own faces — so a step
 * inside the ring is visibly inside the ring without the ring having to climb it. See {@link #tint}.
 */
public class ShockwaveRenderer extends EntityRenderer<Shockwave, ShockwaveRenderer.State> {
	private static final Identifier BAND = Forja.id("textures/entity/onda_anillo.png");
	private static final Identifier CURTAIN = Forja.id("textures/entity/onda_cortina.png");
	private static final Identifier FLAT = Forja.id("textures/entity/onda_plano.png");
	private static final Identifier GLOW = Forja.id("textures/environment/resplandor.png");
	private static final Identifier RUNE = Forja.id("textures/entity/onda_runa.png");
	private static final Identifier RUNE_STAR = Forja.id("textures/entity/onda_runa_centro.png");

	/** Ticks between the bites of a rune, which is how often it flares: {@code Spellcasting.RUNE_EVERY}. */
	private static final float RUNE_BEAT = 10.0F;

	private static final int MAX_SEGMENTS = 128;

	/** Blocks of circumference per strip segment: short enough that the ring never shows its corners. */
	private static final float SEGMENT = 0.5F;

	/** Blocks of circumference one copy of a texture covers, which keeps the fire the same size at any radius. */
	private static final float TILE = 2.0F;

	/** How far the fire trails behind the front, and how tall it stands on it. */
	private static final float TAIL = 2.6F;
	private static final float CURTAIN_HEIGHT = 2.6F;

	/** The share of the run the flash at the centre lasts for. */
	private static final float FLASH = 0.2F;

	/** Ticks the ring takes to go out once it has reached the end of its run. */
	public static final float AFTERGLOW = 6.0F;

	/** How much of the ring's own strength a block standing up inside it is tinted with. */
	private static final float TINT = 0.45F;

	/** Anything this little above the floor — a carpet, a layer of snow — is the floor: it takes the ring whole. */
	private static final float SKIN = 0.2F;

	/** How far off a block's face its tint is drawn, so the two never fight over the same depth. */
	private static final float OFF_FACE = 0.004F;

	private static final int[] NONE = new int[0];

	/** Ticks between looks at the floor. Blocks do change under a fight, just not sixty times a second. */
	private static final int REMEASURE = 10;

	public ShockwaveRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	/** Everything a frame needs, read off the entity once and never again. */
	public static class State extends EntityRenderState {
		public boolean fired;
		/** A patch lying on the ground for its whole life, rather than a ring crossing it. */
		public boolean pool;
		/** A patch with a rune written on it. */
		public boolean glyph;
		/** How far up whatever this warns of begins its fall, or zero. */
		public float fall;
		/** Ticks since the blow, with the part of a tick. */
		public float age;
		public int duration;
		/** 0 to 1 over the wind-up. */
		public float charge;
		/** 0 to 1 over the run. */
		public float progress;
		/** 1 while it runs, falling to 0 over the afterglow. */
		public float fade;
		public float reach;
		public int colour;
		/** How tall the fire stands, against the smith's hammer at 1: steam and oil are not bonfires. */
		public float flame;
		/** Seconds since it appeared, for everything that scrolls or flickers. */
		public float time;
		public int segments;
		public final float[] cos = new float[MAX_SEGMENTS + 1];
		public final float[] sin = new float[MAX_SEGMENTS + 1];
		public final float[] angle = new float[MAX_SEGMENTS + 1];
		/** How much of a full turn the strips cover: all of it for a circle, a quarter for the automaton's wedge. */
		public float turn = 1.0F;
		public boolean whole = true;
		/** The measured floor: a height per block column, {@code span} by {@code span}, from ({@code minX}, {@code minZ}). Replaced whole, never written to. */
		public float[] columns;
		public int span;
		public int minX;
		public int minZ;
		/** Indices into {@link #columns} of what stands above the wave's own level. Usually nothing. */
		public int[] raised = NONE;
		/** The one height everything flat is drawn at, relative to the wave. */
		public float level;
		/** Where the strips start and how far round they go, for asking whether a spot is inside a wedge. */
		public float from;
		public float sweep = Mth.TWO_PI;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	/** Its box is half a block and what it draws is eighteen across: culling it by the box hides the ring. */
	@Override
	protected boolean affectedByCulling(Shockwave wave) {
		return false;
	}

	@Override
	public void extractRenderState(Shockwave wave, State state, float partialTicks) {
		super.extractRenderState(wave, state, partialTicks);
		Level level = wave.level();
		long now = level.getGameTime();
		state.reach = wave.reach();
		state.colour = wave.colour();
		state.flame = wave.flame();
		state.fired = wave.fired();
		state.pool = wave.pool();
		state.glyph = wave.glyph();
		state.fall = wave.fall();
		state.duration = wave.duration();
		// Ages are taken as differences first: the world clock itself is far too large for a float to
		// keep the part of a tick in.
		state.time = ((now - wave.startedAt()) + partialTicks) / 20.0F;
		float widest;
		if (state.fired) {
			float age = (now - wave.firedAt()) + partialTicks;
			state.age = age;
			state.progress = Mth.clamp(age / wave.duration(), 0.0F, 1.0F);
			state.fade = age <= wave.duration() ? 1.0F : Mth.clamp(1.0F - (age - wave.duration()) / AFTERGLOW, 0.0F, 1.0F);
			state.charge = 1.0F;
			widest = state.pool ? state.reach : (float) Shockwave.radiusAt(state.reach, age, wave.duration());
		} else {
			float age = (now - wave.startedAt()) + partialTicks;
			state.charge = Mth.clamp(age / Math.max(1, wave.windup()), 0.0F, 1.0F);
			state.progress = 0.0F;
			state.fade = 1.0F;
			widest = state.reach;
		}
		float arc = wave.arc();
		state.whole = arc >= Shockwave.WHOLE - 0.001F;
		float from = state.whole ? 0.0F : wave.facing() - arc;
		float span = state.whole ? Mth.TWO_PI : arc * 2.0F;
		state.turn = span / Mth.TWO_PI;
		state.from = from;
		state.sweep = span;
		state.segments = Mth.clamp(Mth.ceil(widest * span / SEGMENT), state.whole ? 24 : 6, MAX_SEGMENTS);
		for (int i = 0; i <= state.segments; i++) {
			float angle = from + i * span / state.segments;
			state.angle[i] = angle;
			state.cos[i] = Mth.cos(angle);
			state.sin[i] = Mth.sin(angle);
		}
		measure(wave, level, now);
		state.columns = wave.floorColumns;
		state.raised = wave.floorRaised == null ? NONE : wave.floorRaised;
		state.level = wave.floorLevel;
		state.span = wave.floorSpan;
		state.minX = wave.floorMinX;
		state.minZ = wave.floorMinZ;
	}

	/**
	 * Looks at the floor under the whole reach, one block column at a time, and remembers it.
	 *
	 * <p>Third go at this. Measuring only under the moving edge sank the warning — a filled circle —
	 * under the floor on its way to a ditch. Measuring on a grid of spokes and blending between the
	 * samples fixed that and tore the edges instead: a blend across a one-block step is a ramp, the
	 * ramp cuts through the corner of the step, and what is left showing is a jagged flap. The world
	 * is made of block columns, so that is what is measured: the height of every column the ring can
	 * reach, exactly, and nothing in between.
	 *
	 * <p>What it is for changed when the ring stopped climbing things: the heights are no longer where
	 * the ring lies, they are how it knows which blocks stand up inside it and how tall, so it can tint
	 * them. The list of those is made here too, because on open ground it is empty and the ring then
	 * costs nothing extra at all.
	 *
	 * <p>A few hundred columns is nothing once and too much per frame, so it is kept on the entity and
	 * redone every half second, or when the warning is carried somewhere else by whoever is winding
	 * up. A fresh array each time, never the old one written over: a frame already handed the old one
	 * goes on drawing from something that is not changing under it.
	 */
	private static void measure(Shockwave wave, Level level, long now) {
		int reach = Mth.ceil(wave.reach()) + 2;
		int span = reach * 2 + 1;
		double dx = wave.getX() - wave.floorX;
		double dy = wave.getY() - wave.floorY;
		double dz = wave.getZ() - wave.floorZ;
		boolean stale = wave.floorColumns == null || wave.floorSpan != span || dx * dx + dy * dy + dz * dz > 0.04
			|| now - wave.floorStamp >= REMEASURE || now < wave.floorStamp;
		if (!stale) {
			return;
		}
		int minX = Mth.floor(wave.getX()) - reach;
		int minZ = Mth.floor(wave.getZ()) - reach;
		float[] columns = new float[span * span];
		for (int z = 0; z < span; z++) {
			for (int x = 0; x < span; x++) {
				columns[z * span + x] = Shockwave.floorAt(level, minX + x + 0.5, wave.getY(), minZ + z + 0.5);
			}
		}
		float floor = settle(columns, span, wave.reach());
		int[] raised = new int[span * span];
		int count = 0;
		for (int i = 0; i < columns.length; i++) {
			if (columns[i] > floor + 0.01F) {
				raised[count++] = i;
			}
		}
		wave.floorColumns = columns;
		wave.floorLevel = floor;
		wave.floorRaised = count == 0 ? NONE : java.util.Arrays.copyOf(raised, count);
		wave.floorSpan = span;
		wave.floorMinX = minX;
		wave.floorMinZ = minZ;
		wave.floorStamp = now;
		wave.floorX = wave.getX();
		wave.floorY = wave.getY();
		wave.floorZ = wave.getZ();
	}

	/**
	 * The one level a wave lies at: the lowest floor that a fair share of its reach stands on.
	 *
	 * <p>Not simply the floor under the middle of it. The smith on a dais one block up would put his whole
	 * ring a block up with him, a sheet of fire at chest height over everyone it is meant to warn. And not
	 * simply the commonest height either: across a wide step the upper half winning would hang the ring
	 * over the lower half, where the lower half winning only leaves the upper half tinted. So the heights
	 * inside the reach are counted, and the lowest one that at least a sixth of them share is the floor.
	 * A ditch or a few holes never reach a sixth, so they do not drag the ring down into them.
	 */
	private static float settle(float[] columns, int span, float reach) {
		java.util.Map<Integer, Integer> shares = new java.util.TreeMap<>();
		int inside = 0;
		int centre = span / 2;
		float limit = (reach + 0.5F) * (reach + 0.5F);
		for (int z = 0; z < span; z++) {
			for (int x = 0; x < span; x++) {
				float dx = x - centre;
				float dz = z - centre;
				if (dx * dx + dz * dz > limit) {
					continue;
				}
				inside++;
				// In sixteenths, which is as fine as a block's height ever is.
				shares.merge(Math.round(columns[z * span + x] * 16.0F), 1, Integer::sum);
			}
		}
		int commonest = 0;
		int most = -1;
		for (java.util.Map.Entry<Integer, Integer> share : shares.entrySet()) {
			if (share.getValue() * 6 >= inside) {
				return share.getKey() / 16.0F;
			}
			if (share.getValue() > most) {
				most = share.getValue();
				commonest = share.getKey();
			}
		}
		return commonest / 16.0F;
	}

	/** The height of the column at a place in the measured square, or level ground off the edge of it. */
	private static float columnAt(State state, int bx, int bz) {
		if (bx < 0 || bz < 0 || bx >= state.span || bz >= state.span) {
			return 0.0F;
		}
		return state.columns[bz * state.span + bx];
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.fired && state.pool) {
			this.pool(state, pose, collector);
		} else if (state.fired) {
			this.wave(state, pose, collector);
		} else {
			this.warning(state, pose, collector);
			if (state.fall > 0.0F) {
				this.falling(state, pose, collector, camera);
			}
		}
		super.submit(state, pose, collector, camera);
	}

	/**
	 * The circle on the floor while the blow is on its way.
	 *
	 * <p>Two things, and the gap between them is the message: a line where the ring will stop, there
	 * from the first tick so the reach is never something you learn by being hit, and a glow that fills
	 * out towards it. When the glow touches the line the hammer lands.
	 */
	private void warning(State state, PoseStack pose, SubmitNodeCollector collector) {
		float g = state.charge;
		float filled = state.reach * g;
		// The last stretch burns brighter: the difference between "soon" and "now".
		float urgency = 1.0F + Mth.clamp((g - 0.8F) / 0.2F, 0.0F, 1.0F) * 0.7F;
		float pulse = 0.85F + 0.15F * Mth.sin(state.time * 9.0F);
		int hot = mix(state.colour, 0xFFFFFF, 0.72F);
		int mid = state.colour;
		float centre = 0.06F * urgency;
		float edge = Math.min(0.55F, (0.14F + 0.16F * g) * urgency);
		collector.submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> {
			band(p, buffer, state, 0.0F, filled, 0.02F, 0.0F, 1.0F, mid, centre, mid, edge, 1.0F, 0.0F);
			band(p, buffer, state, Math.max(0.0F, filled - 0.6F), filled, 0.025F, 0.0F, 1.0F,
				mid, 0.0F, hot, Math.min(0.9F, 0.5F * urgency), 1.0F, 0.0F);
			band(p, buffer, state, state.reach - 0.14F, state.reach, 0.03F, 0.0F, 1.0F,
				hot, Math.min(1.0F, (0.5F + 0.4F * g) * pulse * urgency), hot, Math.min(1.0F, (0.6F + 0.4F * g) * pulse * urgency), 1.0F, 0.0F);
			band(p, buffer, state, state.reach, state.reach + 0.4F, 0.03F, 0.0F, 1.0F,
				mid, 0.3F * pulse * urgency, mid, 0.0F, 1.0F, 0.0F);
			if (!state.whole) {
				float side = Math.min(1.0F, (0.6F + 0.4F * g) * pulse * urgency);
				spoke(p, buffer, state, 0, state.reach, 0.032F, hot, side);
				spoke(p, buffer, state, state.segments, state.reach, 0.032F, hot, side);
			}
		});
	}

	/**
	 * A patch that lies where it fell: slag still hot, oil still wet.
	 *
	 * <p>The body of it is dull and the edge is bright, because the edge is the information — this far
	 * and no further — and the whole thing thins as its time runs out, so "how long do I have to stay
	 * off it" is answered by looking. Two rings of the floor texture turn slowly inside it so that it
	 * is a surface with something going on in it and not a coloured disc.
	 */
	private void pool(State state, PoseStack pose, SubmitNodeCollector collector) {
		// In over a quarter of a second, spreading as it lands; out over its whole life, slowly at first.
		float in = Mth.clamp(state.age / 5.0F, 0.0F, 1.0F);
		float left = 1.0F - state.progress;
		float alpha = in * Mth.clamp((float) Math.pow(left, 0.6) * state.fade, 0.0F, 1.0F);
		if (alpha <= 0.003F) {
			return;
		}
		float r = state.reach * (0.6F + 0.4F * in);
		float t = state.time;
		int hot = mix(state.colour, 0xFFFFFF, 0.55F);
		int mid = state.colour;
		int deep = deepen(state.colour);
		int dark = scaled(deep, 0.45F);
		float beat = 0.88F + 0.12F * Mth.sin(t * 3.1F);
		// Under a rune the patch is only the ground the writing lies on: thin enough to read it against.
		float body = state.glyph ? 0.5F : 1.0F;
		collector.submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (pz, buffer) -> {
			band(pz, buffer, state, 0.0F, r * 0.7F, 0.02F, 0.0F, 1.0F, dark, 0.5F * alpha * body, deep, 0.62F * alpha * body, 1.0F, 0.0F);
			band(pz, buffer, state, r * 0.7F, r, 0.02F, 0.0F, 1.0F, deep, 0.62F * alpha * body, mid, 0.8F * alpha * beat * body, 1.0F, 0.0F);
			// The rim, and a short fall-off outside it. The rim alone is an eighth of a block wide, which
			// from across a room is less than a pixel tall and draws as a dashed line; the fall-off gives
			// the line something to fade into instead of something to break up against.
			band(pz, buffer, state, r - 0.16F, r, 0.03F, 0.0F, 1.0F, hot, 0.85F * alpha * beat, hot, alpha * beat, 1.0F, 0.0F);
			band(pz, buffer, state, r, r + 0.14F, 0.03F, 0.0F, 1.0F, hot, 0.55F * alpha * beat, mid, 0.0F, 1.0F, 0.0F);
			if (!state.whole) {
				spoke(pz, buffer, state, 0, r, 0.03F, hot, alpha * beat);
				spoke(pz, buffer, state, state.segments, r, 0.03F, hot, alpha * beat);
			}
		});
		float turn = Math.max(1, Math.round(r * Mth.TWO_PI * state.turn / TILE));
		if (state.glyph) {
			this.rune(state, pose, collector, r, alpha, hot, mid);
			return;
		}
		collector.submitCustomGeometry(pose, RenderTypes.eyes(BAND), (pz, buffer) -> {
			band(pz, buffer, state, r * 0.45F, r * 0.96F, 0.026F, 1.0F, 0.0F, deep, 0.0F, hot, 0.5F * alpha, turn, t * 0.05F);
			band(pz, buffer, state, 0.0F, r * 0.5F, 0.026F, 1.0F, 0.0F, deep, 0.0F, mid, 0.4F * alpha, Math.max(1.0F, turn * 0.5F), -t * 0.08F);
		});
		if (state.flame > 0.05F) {
			collector.submitCustomGeometry(pose, RenderTypes.eyes(CURTAIN), (pz, buffer) -> {
				curtain(pz, buffer, state, r - 0.05F, CURTAIN_HEIGHT * state.flame * (0.5F + 0.5F * left), t, 0.0F, turn, t * 0.2F, hot, mid, deep, 0.75F * alpha);
				curtain(pz, buffer, state, r * 0.55F, CURTAIN_HEIGHT * state.flame * 0.7F * (0.5F + 0.5F * left), t, 2.3F, Math.max(1.0F, turn * 0.5F), -t * 0.16F, hot, mid, deep, 0.5F * alpha);
			});
		}
	}

	/**
	 * The writing on a rune: a band of letters turning one way and the forge's star turning the other.
	 *
	 * <p>Two squares of texture laid flat, and nothing else, because what makes a rune read as one is that
	 * it is <b>drawn</b> — sharp lines at the grain of the floor — where everything else a wave does is fire
	 * and has no edges. It flares each time it bites, from the same clock the server bites by, so the flare
	 * is not decoration: it is the moment to not be standing there.
	 */
	private void rune(State state, PoseStack pose, SubmitNodeCollector collector, float r, float alpha, int hot, int mid) {
		float since = state.age % RUNE_BEAT;
		float flare = state.age >= RUNE_BEAT ? Mth.clamp(1.0F - since / 4.0F, 0.0F, 1.0F) : 0.0F;
		float strength = Mth.clamp(alpha * (0.72F + 0.28F * flare), 0.0F, 1.0F);
		int lit = mix(hot, 0xFFFFFF, 0.6F * flare);
		float level = state.level + 0.034F;
		float t = state.time;
		collector.submitCustomGeometry(pose, RenderTypes.eyes(RUNE), (pz, buffer) -> turned(pz, buffer, r, level, t * 0.22F, lit, strength));
		collector.submitCustomGeometry(pose, RenderTypes.eyes(RUNE_STAR), (pz, buffer) -> turned(pz, buffer, r, level + 0.004F, -t * 0.35F, mix(mid, lit, 0.5F), strength * 0.9F));
	}

	/** A square of texture lying flat, {@code half} blocks from its middle to its side, turned by {@code angle}. */
	private static void turned(PoseStack.Pose pose, VertexConsumer buffer, float half, float y, float angle, int colour, float alpha) {
		if (alpha <= 0.003F) {
			return;
		}
		float cos = Mth.cos(angle) * half;
		float sin = Mth.sin(angle) * half;
		// The corners (-1,-1), (1,-1), (1,1), (-1,1) of the square, turned.
		quad(pose, buffer,
			-cos + sin, y, -sin - cos, 0.0F, 0.0F, colour, alpha,
			cos + sin, y, sin - cos, 1.0F, 0.0F, colour, alpha,
			cos - sin, y, sin + cos, 1.0F, 1.0F, colour, alpha,
			-cos - sin, y, -sin + cos, 0.0F, 1.0F, colour, alpha,
			0.0F, 1.0F, 0.0F);
	}

	/**
	 * The thing on its way down onto the warning: a white-hot head in a halo, and the tail it drags.
	 *
	 * <p>The server throws particles along the same path, which is smoke and sparks and reads, at
	 * night, as a column of soot. What was missing was the light: the one bright thing in the sky that
	 * a meteorite is. The head is two sprites turned to face whoever is looking, and the tail is a
	 * ribbon straight up behind it — it falls straight down — turned the same way about its length,
	 * which lengthens as the fall quickens.
	 */
	private void falling(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		float left = 1.0F - state.charge;
		if (left <= 0.0F) {
			return;
		}
		float height = (float) Shockwave.fallHeight(state.fall, left);
		float tail = 7.0F + 16.0F * state.charge;
		int hot = mix(state.colour, 0xFFFFFF, 0.8F);
		int mid = state.colour;
		int deep = deepen(state.colour);
		float flicker = 0.9F + 0.1F * Mth.sin(state.time * 31.0F);
		// Which way is "across" for someone looking at the tail from where the camera is.
		double toX = camera.pos.x - state.x;
		double toZ = camera.pos.z - state.z;
		double flat = Math.max(1.0E-4, Math.sqrt(toX * toX + toZ * toZ));
		float acrossX = (float) (-toZ / flat);
		float acrossZ = (float) (toX / flat);
		collector.submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (p, buffer) -> {
			float wide = 0.55F;
			float narrow = 0.06F;
			float waist = height + tail * 0.3F;
			quad(p, buffer,
				-acrossX * wide, height, -acrossZ * wide, 0.0F, 1.0F, hot, 0.95F * flicker,
				acrossX * wide, height, acrossZ * wide, 1.0F, 1.0F, hot, 0.95F * flicker,
				acrossX * wide * 0.6F, waist, acrossZ * wide * 0.6F, 1.0F, 0.5F, mid, 0.6F,
				-acrossX * wide * 0.6F, waist, -acrossZ * wide * 0.6F, 0.0F, 0.5F, mid, 0.6F,
				0.0F, 0.0F, 1.0F);
			quad(p, buffer,
				-acrossX * wide * 0.6F, waist, -acrossZ * wide * 0.6F, 0.0F, 0.5F, mid, 0.6F,
				acrossX * wide * 0.6F, waist, acrossZ * wide * 0.6F, 1.0F, 0.5F, mid, 0.6F,
				acrossX * narrow, height + tail, acrossZ * narrow, 1.0F, 0.0F, deep, 0.0F,
				-acrossX * narrow, height + tail, -acrossZ * narrow, 0.0F, 0.0F, deep, 0.0F,
				0.0F, 0.0F, 1.0F);
		});
		pose.pushPose();
		pose.translate(0.0F, height, 0.0F);
		pose.mulPose(camera.orientation);
		collector.submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, buffer) -> {
			sprite(p, buffer, 4.6F, mid, 0.55F * flicker);
			sprite(p, buffer, 2.0F, hot, 1.0F);
		});
		pose.popPose();
	}

	/** A square in the plane the pose has been turned to: for something that always faces the viewer. */
	private static void sprite(PoseStack.Pose pose, VertexConsumer buffer, float size, int colour, float alpha) {
		quad(pose, buffer,
			-size, -size, 0.0F, 0.0F, 1.0F, colour, alpha,
			size, -size, 0.0F, 1.0F, 1.0F, colour, alpha,
			size, size, 0.0F, 1.0F, 0.0F, colour, alpha,
			-size, size, 0.0F, 0.0F, 0.0F, colour, alpha,
			0.0F, 0.0F, 1.0F);
	}

	/** The ring itself, from the flash under the hammer to the last of the afterglow. */
	private void wave(State state, PoseStack pose, SubmitNodeCollector collector) {
		float fade = state.fade;
		if (fade <= 0.003F) {
			return;
		}
		float p = state.progress;
		float r = state.reach * p;
		float t = state.time;
		int hot = mix(state.colour, 0xFFFFFF, 0.72F);
		int mid = state.colour;
		int deep = deepen(state.colour);
		float repeats = Math.max(1, Math.round(r * Mth.TWO_PI * state.turn / TILE));
		// It thins a little as it spreads, the way anything does that is sharing itself over more floor.
		float strength = fade * (1.0F - 0.25F * p);

		if (p < FLASH) {
			float k = p / FLASH;
			float flash = 1.4F + 5.0F * k;
			collector.submitCustomGeometry(pose, RenderTypes.eyes(FLAT), (pz, buffer) -> {
				band(pz, buffer, state, 0.0F, flash * 0.5F, 0.02F, 0.0F, 1.0F, hot, 0.9F * (1.0F - k), hot, 0.55F * (1.0F - k), 1.0F, 0.0F);
				band(pz, buffer, state, flash * 0.5F, flash, 0.02F, 0.0F, 1.0F, hot, 0.55F * (1.0F - k), hot, 0.0F, 1.0F, 0.0F);
			});
		}

		collector.submitCustomGeometry(pose, RenderTypes.eyes(BAND), (pz, buffer) -> {
			// The echo: a second, fainter ring a third of the way behind, which is most of what makes
			// the first one look like it is travelling rather than growing.
			float echo = r * 0.68F;
			float echoRepeats = Math.max(1, Math.round(echo * Mth.TWO_PI * state.turn / TILE));
			band(pz, buffer, state, Math.max(0.0F, echo - 1.1F), echo, 0.022F, 1.0F, 0.0F,
				deep, 0.0F, mid, 0.5F * strength, echoRepeats, -t * 0.15F);
			// The band, in three lengths so it bends over whatever it crosses: a long tail coming up
			// out of nothing, the body of the fire, and the white-hot lip at the very front.
			// A ring with little flame on it trails less behind, too: it is a line more than a fire.
			float length = TAIL * (0.55F + 0.45F * Math.min(1.0F, state.flame));
			float tail = Math.max(0.0F, r - length);
			float body = Math.max(0.0F, r - length * 0.6F);
			float core = Math.max(0.0F, r - length * 0.3F);
			band(pz, buffer, state, tail, body, 0.03F, 1.0F, 0.6F, deep, 0.0F, deep, 0.55F * strength, repeats, t * 0.22F);
			band(pz, buffer, state, body, core, 0.03F, 0.6F, 0.3F, deep, 0.55F * strength, mid, 0.95F * strength, repeats, t * 0.22F);
			band(pz, buffer, state, core, r, 0.03F, 0.3F, 0.0F, mid, 0.95F * strength, hot, strength, repeats, t * 0.22F);
		});

		// The curtain comes up out of the floor over the first tenth rather than appearing full height.
		float rise = Mth.clamp(p / 0.1F, 0.0F, 1.0F) * (1.0F - 0.35F * p) * state.flame;
		collector.submitCustomGeometry(pose, RenderTypes.eyes(CURTAIN), (pz, buffer) -> {
			// Twice, sliding opposite ways: where the two sets of tongues cross is what flickers.
			curtain(pz, buffer, state, r - 0.03F, CURTAIN_HEIGHT * rise, t, 0.0F, repeats, t * 0.35F, hot, mid, deep, strength);
			curtain(pz, buffer, state, r + 0.06F, CURTAIN_HEIGHT * rise * 0.78F, t, 1.7F, repeats, 0.37F - t * 0.24F, hot, mid, deep, 0.7F * strength);
		});
	}

	/**
	 * A wall of flame standing on the front, its top edge moving by itself.
	 *
	 * <p>The height of each post comes off two waves that are whole numbers of turns round the circle,
	 * so the wall closes on itself without a seam, running at different speeds so it never repeats.
	 */
	private static void curtain(PoseStack.Pose pose, VertexConsumer buffer, State state, float radius, float height,
		float time, float phase, float repeats, float scroll, int hot, int mid, int deep, float alpha) {
		if (radius <= 0.05F || height <= 0.02F || alpha <= 0.003F) {
			return;
		}
		int n = state.segments;
		float[] tall = new float[n + 1];
		for (int i = 0; i <= n; i++) {
			float angle = state.angle[i];
			tall[i] = height * (0.64F + 0.24F * Mth.sin(angle * 23.0F + time * 11.0F + phase) + 0.12F * Mth.sin(angle * 57.0F - time * 17.0F + phase));
		}
		for (int i = 0; i < n; i++) {
			float x0 = state.cos[i] * radius;
			float z0 = state.sin[i] * radius;
			float x1 = state.cos[i + 1] * radius;
			float z1 = state.sin[i + 1] * radius;
			// On the wave's own level, like the strip it stands on: over a block in its way the foot of
			// it is inside the block and the tongues come up out of the top.
			float y0 = state.level + 0.02F;
			float y1 = y0;
			float u0 = i * repeats / n + scroll;
			float u1 = (i + 1) * repeats / n + scroll;
			float m0 = y0 + tall[i] * 0.42F;
			float m1 = y1 + tall[i + 1] * 0.42F;
			// Foot to waist: white-hot into the colour. Waist to tip: the colour going out.
			quad(pose, buffer,
				x0, y0, z0, u0, 1.0F, hot, alpha,
				x1, y1, z1, u1, 1.0F, hot, alpha,
				x1, m1, z1, u1, 0.58F, mid, alpha * 0.85F,
				x0, m0, z0, u0, 0.58F, mid, alpha * 0.85F,
				state.cos[i], 0.0F, state.sin[i]);
			quad(pose, buffer,
				x0, m0, z0, u0, 0.58F, mid, alpha * 0.85F,
				x1, m1, z1, u1, 0.58F, mid, alpha * 0.85F,
				x1, y1 + tall[i + 1], z1, u1, 0.0F, deep, 0.0F,
				x0, y0 + tall[i], z0, u0, 0.0F, deep, 0.0F,
				state.cos[i], 0.0F, state.sin[i]);
		}
	}

	/**
	 * A flat strip between two radii, lying at the wave's own level.
	 *
	 * <p>Colour and alpha are given for the inner and the outer edge and run between them, so one
	 * greyscale texture serves every colour a wave can be and the fade never has a step in it. Whatever
	 * stands up through the strip hides the part of it that is inside, and is tinted instead.
	 */
	private static void band(PoseStack.Pose pose, VertexConsumer buffer, State state,
		float inner, float outer, float lift, float vInner, float vOuter,
		int innerColour, float innerAlpha, int outerColour, float outerAlpha, float repeats, float scroll) {
		if (outer <= inner || outer <= 0.0F || (innerAlpha <= 0.003F && outerAlpha <= 0.003F)) {
			return;
		}
		int n = state.segments;
		float start = Math.max(0.0F, inner);
		float flat = state.level + lift;
		for (int i = 0; i < n; i++) {
			float ax = state.cos[i] * start;
			float az = state.sin[i] * start;
			float bx = state.cos[i] * outer;
			float bz = state.sin[i] * outer;
			float cx = state.cos[i + 1] * outer;
			float cz = state.sin[i + 1] * outer;
			float dx = state.cos[i + 1] * start;
			float dz = state.sin[i + 1] * start;
			float u0 = i * repeats / n + scroll;
			float u1 = (i + 1) * repeats / n + scroll;
			quad(pose, buffer,
				ax, flat, az, u0, vInner, innerColour, innerAlpha,
				bx, flat, bz, u0, vOuter, outerColour, outerAlpha,
				cx, flat, cz, u1, vOuter, outerColour, outerAlpha,
				dx, flat, dz, u1, vInner, innerColour, innerAlpha,
				0.0F, 1.0F, 0.0F);
		}
		tint(pose, buffer, state, start, outer, lift, vInner, vOuter, innerColour, innerAlpha, outerColour, outerAlpha, repeats, scroll);
	}

	/**
	 * The same strip, fainter, on the faces of whatever stands up inside it.
	 *
	 * <p>Block by block rather than by bending the strip: a tint that stops exactly at the edge of the
	 * block it is on reads as the block being lit, where a piece of ring draped over it read as a piece
	 * of ring in the wrong place. Each corner of a face asks what the strip is doing at its own distance
	 * from the middle, so the tint fades across a block the way the strip fades across the floor beside
	 * it, and a block only half inside the ring is only half tinted. The top is done, and every side that
	 * shows above its neighbour — or above the wave's level, where the neighbour is a hole.
	 */
	private static void tint(PoseStack.Pose pose, VertexConsumer buffer, State state, float inner, float outer, float lift,
		float vInner, float vOuter, int innerColour, float innerAlpha, int outerColour, float outerAlpha, float repeats, float scroll) {
		if (state.raised.length == 0 || state.columns == null) {
			return;
		}
		float width = Math.max(1.0E-4F, outer - inner);
		float[] xs = new float[4];
		float[] zs = new float[4];
		float[] us = new float[4];
		float[] vs = new float[4];
		float[] alphas = new float[4];
		int[] colours = new int[4];
		for (int index : state.raised) {
			int bx = index % state.span;
			int bz = index / state.span;
			float height = state.columns[index];
			// The block's footprint, relative to the wave: corner 0 at its low x and z, then round.
			float x0 = (float) (state.minX + bx - state.x);
			float z0 = (float) (state.minZ + bz - state.z);
			xs[0] = x0;
			zs[0] = z0;
			xs[1] = x0 + 1.0F;
			zs[1] = z0;
			xs[2] = x0 + 1.0F;
			zs[2] = z0 + 1.0F;
			xs[3] = x0;
			zs[3] = z0 + 1.0F;
			boolean skin = height - state.level <= SKIN;
			float strength = skin ? 1.0F : TINT;
			boolean any = false;
			for (int corner = 0; corner < 4; corner++) {
				float distance = Mth.sqrt(xs[corner] * xs[corner] + zs[corner] * zs[corner]);
				float share = (distance - inner) / width;
				boolean inside = share >= 0.0F && share <= 1.0F;
				float round = 0.0F;
				if (inside) {
					// Which way this corner lies, as a share of the way round the strips: for the texture,
					// and for a wedge to say the corner is not in it at all.
					float angle = (float) Mth.atan2(zs[corner], xs[corner]) - state.from;
					angle -= Mth.TWO_PI * Mth.floor(angle / Mth.TWO_PI);
					inside = state.whole || angle <= state.sweep;
					round = angle / state.sweep;
				}
				float clamped = Mth.clamp(share, 0.0F, 1.0F);
				alphas[corner] = inside ? Mth.lerp(clamped, innerAlpha, outerAlpha) * strength : 0.0F;
				colours[corner] = mix(innerColour, outerColour, clamped);
				us[corner] = round * repeats + scroll;
				vs[corner] = Mth.lerp(clamped, vInner, vOuter);
				any |= alphas[corner] > 0.003F;
			}
			if (!any) {
				continue;
			}
			float top = height + lift + OFF_FACE;
			quad(pose, buffer,
				xs[0], top, zs[0], us[0], vs[0], colours[0], alphas[0],
				xs[1], top, zs[1], us[1], vs[1], colours[1], alphas[1],
				xs[2], top, zs[2], us[2], vs[2], colours[2], alphas[2],
				xs[3], top, zs[3], us[3], vs[3], colours[3], alphas[3],
				0.0F, 1.0F, 0.0F);
			if (skin) {
				continue;
			}
			// The four sides, corner to corner round the footprint: -z, +x, +z, -x.
			for (int side = 0; side < 4; side++) {
				int nx = side == 1 ? 1 : side == 3 ? -1 : 0;
				int nz = side == 0 ? -1 : side == 2 ? 1 : 0;
				float foot = Math.max(state.level, columnAt(state, bx + nx, bz + nz));
				if (foot >= height - 0.01F) {
					continue;
				}
				int a = side;
				int b = (side + 1) % 4;
				if (alphas[a] <= 0.003F && alphas[b] <= 0.003F) {
					continue;
				}
				float ox = nx * OFF_FACE;
				float oz = nz * OFF_FACE;
				quad(pose, buffer,
					xs[a] + ox, foot + lift, zs[a] + oz, us[a], vs[a], colours[a], alphas[a],
					xs[b] + ox, foot + lift, zs[b] + oz, us[b], vs[b], colours[b], alphas[b],
					xs[b] + ox, height + lift, zs[b] + oz, us[b], vs[b], colours[b], alphas[b],
					xs[a] + ox, height + lift, zs[a] + oz, us[a], vs[a], colours[a], alphas[a],
					nx, 0.0F, nz);
			}
		}
	}

	/** A thin line from the middle out along one of the strip's own angles: the straight side of a wedge. */
	private static void spoke(PoseStack.Pose pose, VertexConsumer buffer, State state, int index, float length, float lift, int colour, float alpha) {
		if (alpha <= 0.003F || length <= 0.05F) {
			return;
		}
		float cos = state.cos[index];
		float sin = state.sin[index];
		float half = 0.07F;
		float ax = sin * half, az = -cos * half;
		float bx = cos * length + sin * half, bz = sin * length - cos * half;
		float cx = cos * length - sin * half, cz = sin * length + cos * half;
		float dx = -sin * half, dz = cos * half;
		float flat = state.level + lift;
		quad(pose, buffer,
			ax, flat, az, 0.0F, 0.0F, colour, alpha,
			bx, flat, bz, 1.0F, 0.0F, colour, alpha,
			cx, flat, cz, 1.0F, 1.0F, colour, alpha,
			dx, flat, dz, 0.0F, 1.0F, colour, alpha,
			0.0F, 1.0F, 0.0F);
	}

	/** One quad, wound both ways: every piece of this is looked at from either side. */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
		float ax, float ay, float az, float au, float av, int aColour, float aAlpha,
		float bx, float by, float bz, float bu, float bv, int bColour, float bAlpha,
		float cx, float cy, float cz, float cu, float cv, int cColour, float cAlpha,
		float dx, float dy, float dz, float du, float dv, int dColour, float dAlpha,
		float nx, float ny, float nz) {
		put(pose, buffer, ax, ay, az, au, av, aColour, aAlpha, nx, ny, nz);
		put(pose, buffer, bx, by, bz, bu, bv, bColour, bAlpha, nx, ny, nz);
		put(pose, buffer, cx, cy, cz, cu, cv, cColour, cAlpha, nx, ny, nz);
		put(pose, buffer, dx, dy, dz, du, dv, dColour, dAlpha, nx, ny, nz);
		put(pose, buffer, dx, dy, dz, du, dv, dColour, dAlpha, -nx, -ny, -nz);
		put(pose, buffer, cx, cy, cz, cu, cv, cColour, cAlpha, -nx, -ny, -nz);
		put(pose, buffer, bx, by, bz, bu, bv, bColour, bAlpha, -nx, -ny, -nz);
		put(pose, buffer, ax, ay, az, au, av, aColour, aAlpha, -nx, -ny, -nz);
	}

	private static void put(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v,
		int colour, float alpha, float nx, float ny, float nz) {
		buffer.addVertex(pose, x, y, z)
			.setColor((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF, Mth.clamp(Math.round(alpha * 255.0F), 0, 255))
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0)
			.setNormal(pose, nx, ny, nz);
	}

	private static int mix(int from, int to, float share) {
		int r = Math.round(Mth.lerp(share, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(share, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(share, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}

	/**
	 * The same colour, further from the heat: a little darker and a good deal more saturated.
	 *
	 * <p>Simply darkening it was the first try, and orange darkened is brown — in daylight the tail of
	 * the ring looked like dirt. Pushed away from grey instead, orange goes to a deep red-orange and
	 * violet to a deep violet, which is what the cool end of a flame does.
	 */
	private static int deepen(int colour) {
		float r = ((colour >> 16) & 0xFF) / 255.0F;
		float g = ((colour >> 8) & 0xFF) / 255.0F;
		float b = (colour & 0xFF) / 255.0F;
		float high = Math.max(r, Math.max(g, b));
		float low = Math.min(r, Math.min(g, b));
		// Every channel is pulled away from the brightest one by half again, then the lot is dimmed.
		float widen = 1.5F;
		float dim = 0.85F;
		r = Mth.clamp((high - (high - r) * widen) * dim, 0.0F, 1.0F);
		g = Mth.clamp((high - (high - g) * widen) * dim, 0.0F, 1.0F);
		b = Mth.clamp((high - (high - b) * widen) * dim, 0.0F, 1.0F);
		if (high - low < 0.01F) {
			return scaled(colour, dim);
		}
		return Math.round(r * 255.0F) << 16 | Math.round(g * 255.0F) << 8 | Math.round(b * 255.0F);
	}

	private static int scaled(int colour, float by) {
		int r = Math.min(255, Math.round(((colour >> 16) & 0xFF) * by));
		int g = Math.min(255, Math.round(((colour >> 8) & 0xFF) * by));
		int b = Math.min(255, Math.round((colour & 0xFF) * by));
		return r << 16 | g << 8 | b;
	}
}
