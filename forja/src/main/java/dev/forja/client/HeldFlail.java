package dev.forja.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.forja.Forja;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The mangual in hand is a real flail, not the flat item. Andy: "el mangual podria estar en 3D y que la
 * bola este encadenada al palo, la bola recorre la distancia de golpe". A haft held in the fist, a chain
 * of links hanging from its top and the spiked ball on the end of it.
 *
 * <p>The chain is a string of points simulated in the world, one string per flail in view: the top one
 * is pinned to the top of the haft wherever the hand has taken it this frame, and the rest hang from it
 * under gravity, each held its link's length from the next (position-based: step, then pull every pair
 * back to its length a few times over), with the ball at the end heavier than a link. Steps are a fixed
 * slice of game time, as many as the frame took, so it swings the same at any frame rate and stops when
 * the game is paused. So the ball hangs straight down at rest and swings behind the hand when it moves.
 *
 * <p>A blow takes the ball over (see {@link CombatPoses#flailCue}): while the flail is whirled it is pulled
 * round with the haft; on the strike it flies out along an arc (up and over, round from the side, or up
 * from below, as the blow has it) and the chain pays out behind it, so it reaches whatever the blow is
 * aimed at the moment the blow lands, however far away: the target it hits, or with nothing to hit, the
 * full length of the chain (three blocks, the reach the mangual adds). It is held there a moment, then
 * reeled back in and let go to swing.
 *
 * <p>Tinted part by part as the item is: the ball and its spikes by the BOLA material, the links and the
 * haft's iron by the CADENA, the wood of the haft by the MANGO.
 */
public final class HeldFlail {
	/** Where each entity with a flail is looking and what its swing is aimed at, worked out while the entity is at hand. */
	public static final RenderStateDataKey<Aim> KEY = RenderStateDataKey.create(() -> "forja:mangual");

	/**
	 * What a flail's holder aims its swing at.
	 *
	 * @param entity the holder
	 * @param footY  the ground the holder stands on, which the ball does not go through
	 * @param look   the way the holder looks
	 * @param side   the holder's right, level
	 * @param target the point its swing is going to hit, or null for none
	 * @param free   how far the holder could reach along its look before a wall, from its eyes
	 */
	public record Aim(int entity, double footY, Vec3 look, Vec3 side, @Nullable Vec3 target, float free) {
	}

	private static final Identifier TEXTURE = Forja.id("textures/entity/mangual.png");
	private static final float TEXTURE_SIZE = 64.0F;
	/** Where each material's skin starts on the texture: wood, iron, the ball, the spikes. */
	private static final int[][] SKIN_AT = {{0, 0}, {32, 0}, {0, 32}, {32, 32}};
	private static final int WOOD = 0;
	private static final int IRON = 1;
	private static final int BALL = 2;
	private static final int SPIKE = 3;
	/** The item slot whose material tints each skin: MANGO, CADENA, BOLA, BOLA. */
	private static final int[] SLOT = {2, 1, 0, 0};
	/** The colours the item model falls back on for those slots: BOLA (HEAD), CADENA (EXTRA), MANGO (HANDLE). */
	private static final int[] DEFAULT_COLOURS = {0xE4E4E4, 0xB8894F, 0xB8894F};

	// The flail's own measures, in model pixels. The grip's frame has the haft running along -z out of the
	// fist (the way a held weapon points: the blows are written for that) and y going on down the arm.
	/** How far the haft sticks out behind the fist, and how far it runs out in front of it. */
	private static final float BUTT = 2.5F;
	private static final float HAFT = 9.0F;
	/** The chain hangs from a ring this far past the end of the haft. */
	private static final float RING = 1.4F;
	/** The chain's length at rest, from the ring to the ball's own ring. */
	private static final float CHAIN = 8.0F;
	/** One link to the next along the chain; each is a little longer, so they overlap as linked rings do. */
	private static final float LINK_PITCH = 2.3F;
	/** From the middle of the ball to the ring on top of it, where the chain ends. */
	private static final float BALL_TOP = 3.4F;
	/** Third person: the middle of the fist, down the arm and a pixel in from its outer face. */
	private static final float FIST_DOWN = 8.5F;
	/** First person: how big the flail is against third person's, and how much higher than a sword's grip it is held (blocks). */
	private static final float FIRST_PERSON_SCALE = 0.7F;
	private static final float FIRST_PERSON_LIFT = 0.08F;

	// The chain: its points, and how they move.
	private static final int POINTS = 9;
	/** One step of the chain, in seconds of game time. */
	private static final float STEP = 1.0F / 240.0F;
	/** No more steps than this in one frame; a longer gap starts the chain afresh, hanging. */
	private static final int MAX_STEPS = 60;
	private static final float GRAVITY = 16.0F;
	/** How much of its speed a point keeps each second: the air slows a chain a little. */
	private static final float KEEP_PER_SECOND = 0.35F;
	private static final int ITERATIONS = 6;
	/** The ball is heavier than a link: it moves this much of what a link moves when the two are pulled together. */
	private static final float BALL_GIVE = 0.2F;
	/** How fast the whirl pulls the ball round with the haft, per second: slow enough that it trails a little. */
	private static final float WHIRL_PULL = 28.0F;
	/** The furthest the ball goes with nothing to hit: the chain's full length, which is the reach the mangual adds. */
	private static final float CHAIN_REACH = Assembler.MANGUAL_REACH;
	/** The holder's own reach plus the chain's, from the eyes, for what a swing can be aimed at. */
	private static final double REACH_FROM_EYE = 3.0 + Assembler.MANGUAL_REACH;

	private static final int VIEW_WORLD = 0;
	private static final int VIEW_FIRST_PERSON = 1;

	/** Every flail in view, by holder, view and hand. */
	private static final Long2ObjectMap<Chain> CHAINS = new Long2ObjectOpenHashMap<>();
	/** What each holder's current swing was aimed at when it started. */
	private static final Int2ObjectMap<Swing> SWINGS = new Int2ObjectOpenHashMap<>();
	private static double lastSweep;

	/** Whether the world's entities are being drawn right now, rather than one in a screen (the inventory's doll). */
	private static boolean inWorld;

	private static @Nullable Parts parts;

	/** Time every chain adds to its next frame, as if the pose had been held that long (see {@link #settle}). */
	private static float settleSeconds;
	private static int settleCount;

	private HeldFlail() {
	}

	/** Whether this is a forged flail. */
	public static boolean is(ItemStack stack) {
		ForgedParts forged = stack.isEmpty() ? null : stack.get(ModComponents.PARTS);
		return forged != null && forged.type() == ForgeType.MANGUAL;
	}

	/**
	 * For the client test, which holds a swing still at one moment for a shot with no time going by: every
	 * chain's next frame runs this much longer, as if the pose had been held that long, so the ball gets to
	 * where that moment of the blow puts it.
	 */
	public static void settle(float seconds) {
		settleSeconds = seconds;
		settleCount++;
	}

	/**
	 * Where the ball of the flail an entity holds (in the world, its right hand's first) last landed a blow,
	 * for the client test, or null if it has not.
	 */
	public static @Nullable Vec3 lastLanding(int entity) {
		for (long hand = 0L; hand <= 1L; hand++) {
			Chain chain = CHAINS.get((long) entity << 2 | (long) VIEW_WORLD << 1 | hand);
			if (chain != null && chain.landed != null) {
				return chain.landed;
			}
		}
		return null;
	}

	/** Set while the level draws its entities, so a flail knows it is in the world and not in a screen. */
	public static void drawingWorld(boolean drawing) {
		inWorld = drawing;
	}

	// --- What the swing is aimed at ------------------------------------------------------------------

	private static final class Swing {
		long started = Long.MIN_VALUE / 2;
		int target = -1;
		float free = (float) REACH_FROM_EYE;
		double seen;
	}

	/**
	 * Where an entity with a flail in hand is looking and what its swing is aimed at, or null when it holds
	 * none. The target is picked once, when the swing starts, the way the game would: for the player, what
	 * is under the crosshair; for anyone else, the first thing along its look within the flail's reach. The
	 * ball then follows it wherever it moves until the blow lands.
	 */
	public static @Nullable Aim aim(LivingEntity entity, float partialTick) {
		if (!is(entity.getMainHandItem()) && !is(entity.getOffhandItem())) {
			return null;
		}
		Vec3 look = entity.getViewVector(partialTick);
		Swing swing = SWINGS.computeIfAbsent(entity.getId(), id -> new Swing());
		swing.seen = CombatAnims.now(partialTick);
		if (entity.swinging) {
			long started = entity.tickCount - entity.swingTime;
			if (Math.abs(started - swing.started) > 1L) {
				swing.started = started;
				pickTarget(entity, swing, look, partialTick);
			}
		} else if (entity.attackAnim <= 0.0F) {
			swing.target = -1;
		}
		Vec3 target = null;
		Entity aimed = swing.target >= 0 ? entity.level().getEntity(swing.target) : null;
		if (aimed != null && aimed.isAlive()) {
			// Against the near side of it at chest height, not in its middle, or the ball would sink into it.
			Vec3 middle = aimed.getPosition(partialTick).add(0.0, aimed.getBbHeight() * 0.6, 0.0);
			Vec3 toward = middle.subtract(entity.getEyePosition(partialTick)).multiply(1.0, 0.0, 1.0);
			double length = toward.length();
			double off = aimed.getBbWidth() * 0.5 + BALL_TOP / 16.0 * 0.7;
			target = length < 1.0E-3 ? middle : middle.subtract(toward.scale(Math.min(off, length * 0.5) / length));
		}
		double yaw = Math.toRadians(entity.getYRot(partialTick));
		Vec3 side = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
		return new Aim(entity.getId(), entity.getPosition(partialTick).y, look, side, target, swing.free);
	}

	private static void pickTarget(LivingEntity entity, Swing swing, Vec3 look, float partialTick) {
		Vec3 eye = entity.getEyePosition(partialTick);
		Vec3 end = eye.add(look.scale(REACH_FROM_EYE));
		HitResult wall = entity.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
		double free = wall.getType() == HitResult.Type.MISS ? REACH_FROM_EYE : wall.getLocation().distanceTo(eye);
		swing.free = (float) free;
		swing.target = -1;
		Minecraft minecraft = Minecraft.getInstance();
		if (entity == minecraft.player && minecraft.crosshairPickEntity instanceof LivingEntity under) {
			swing.target = under.getId();
			return;
		}
		Vec3 reach = eye.add(look.scale(free));
		AABB around = entity.getBoundingBox().expandTowards(look.scale(free)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(entity, eye, reach, around,
			other -> other != entity && other instanceof LivingEntity && other.isPickable() && !other.isSpectator(), free * free);
		if (hit != null) {
			swing.target = hit.getEntity().getId();
		}
	}

	// --- The chain -----------------------------------------------------------------------------------

	/** The stages of a flail's swing that move its ball (see {@link CombatPoses.FlailCue}). */
	static final int STILL = 0;
	static final int WHIRL = 1;
	static final int WIND = 2;
	static final int STRIKE = 3;
	static final int HOLD = 4;
	static final int BACK = 5;

	/**
	 * One flail's chain: its points in the world (as floats from an origin, which follows it about), where
	 * they were a step ago, and what its swing has done so far.
	 */
	private static final class Chain {
		final float[] x = new float[POINTS];
		final float[] y = new float[POINTS];
		final float[] z = new float[POINTS];
		final float[] ox = new float[POINTS];
		final float[] oy = new float[POINTS];
		final float[] oz = new float[POINTS];
		double originX;
		double originY;
		double originZ;
		double time = Double.NaN;
		float carried;
		int settled;
		/** The anchor at the end of the last frame, and where the ball was being pulled to. */
		final Vector3f anchor = new Vector3f();
		final Vector3f pulled = new Vector3f();
		boolean pulling;
		/** The chain's whole length right now: longer than at rest while the ball is out. */
		float length;
		int stage = STILL;
		/** Where the ball was (in the world) when the strike began, or when it began coming back. */
		final Vector3f from = new Vector3f();
		/** Where the ball last landed a blow, in the world, or null before it has, and when. */
		@Nullable Vec3 landed;
		double landedAt = Double.NaN;
		/** Where the whirl last had the ball, in the world, and when. */
		final Vector3f whirled = new Vector3f();
		double whirledAt = Double.NaN;
		float reached;
	}

	/** What a frame asks of the ball: nothing, to be pulled towards a point, or to be exactly at one. */
	private record Drive(int mode, Vector3f at, float strength, float length) {
		static final int FREE = 0;
		static final int PULL = 1;
		static final int PIN = 2;
	}

	private static Chain chain(int entity, int view, boolean left, double time) {
		long key = (long) entity << 2 | (long) view << 1 | (left ? 1L : 0L);
		Chain chain = CHAINS.get(key);
		if (chain == null) {
			chain = new Chain();
			CHAINS.put(key, chain);
		}
		if (time - lastSweep > 100.0 || time < lastSweep) {
			lastSweep = time;
			CHAINS.values().removeIf(other -> !(time - other.time < 100.0) || other.time > time);
			SWINGS.values().removeIf(other -> !(time - other.seen < 100.0) || other.seen > time);
			CHAINS.put(key, chain);
		}
		return chain;
	}

	/** Hangs the chain straight down from the anchor, still, as it would be after a long rest. */
	private static void hang(Chain chain, double ax, double ay, double az, float length) {
		chain.originX = ax;
		chain.originY = ay;
		chain.originZ = az;
		for (int i = 0; i < POINTS; i++) {
			chain.x[i] = chain.ox[i] = 0.0F;
			chain.y[i] = chain.oy[i] = -length * i / (POINTS - 1);
			chain.z[i] = chain.oz[i] = 0.0F;
		}
		chain.anchor.set(0.0F);
		chain.length = length;
		chain.carried = 0.0F;
		chain.pulling = false;
		chain.stage = STILL;
	}

	/** Moves the origin the floats count from to the anchor, when it has wandered far from it. */
	private static void recentre(Chain chain, double ax, double ay, double az) {
		float dx = (float) (ax - chain.originX);
		float dy = (float) (ay - chain.originY);
		float dz = (float) (az - chain.originZ);
		for (int i = 0; i < POINTS; i++) {
			chain.x[i] -= dx;
			chain.ox[i] -= dx;
			chain.y[i] -= dy;
			chain.oy[i] -= dy;
			chain.z[i] -= dz;
			chain.oz[i] -= dz;
		}
		chain.anchor.sub(dx, dy, dz);
		chain.pulled.sub(dx, dy, dz);
		chain.originX = ax;
		chain.originY = ay;
		chain.originZ = az;
	}

	/**
	 * Runs one flail's chain up to this frame. The anchor goes from where it was at the last frame to where
	 * it is now over the steps, as the hand did; so does the point the ball is pulled towards.
	 */
	private static void simulate(Chain chain, double ax, double ay, double az, double time, float restLength, Drive drive, double floorY,
		float ballRadius) {
		if (Double.isNaN(chain.time) || time - chain.time > 10.0 || time < chain.time - 2.0
			|| Math.abs(ax - chain.originX - chain.anchor.x) + Math.abs(ay - chain.originY - chain.anchor.y)
				+ Math.abs(az - chain.originZ - chain.anchor.z) > 4.0) {
			// A first look, a long gap or a jump (a teleport, the camera changing): start again, hanging.
			hang(chain, ax, ay, az, restLength);
			chain.time = time;
		}
		if (Math.abs(ax - chain.originX) + Math.abs(ay - chain.originY) + Math.abs(az - chain.originZ) > 64.0) {
			recentre(chain, ax, ay, az);
		}
		// A frame a little behind the last one (the partial tick is not always kept in step with the tick)
		// is taken as no time at all.
		float seconds = (float) Math.max(0.0, time - chain.time) / 20.0F + chain.carried;
		chain.time = Math.max(time, chain.time);
		int most = MAX_STEPS;
		if (chain.settled != settleCount) {
			chain.settled = settleCount;
			seconds += settleSeconds;
			most += (int) (settleSeconds / STEP);
		}
		int steps = Math.min(most, (int) (seconds / STEP));
		chain.carried = steps == most ? 0.0F : seconds - steps * STEP;

		Vector3f anchorTo = new Vector3f((float) (ax - chain.originX), (float) (ay - chain.originY), (float) (az - chain.originZ));
		Vector3f driveTo = new Vector3f(drive.at()).sub((float) chain.originX, (float) chain.originY, (float) chain.originZ);
		if (drive.mode() == Drive.FREE || !chain.pulling) {
			chain.pulled.set(driveTo);
		}
		float floor = (float) (floorY - chain.originY);
		float keep = (float) Math.pow(KEEP_PER_SECOND, STEP);
		float pull = drive.mode() == Drive.PULL ? 1.0F - (float) Math.exp(-drive.strength() * STEP) : 0.0F;
		Vector3f anchorFrom = new Vector3f(chain.anchor);
		Vector3f pulledFrom = new Vector3f(chain.pulled);
		int last = POINTS - 1;
		for (int step = 1; step <= steps; step++) {
			float along = (float) step / steps;
			float px = Mth.lerp(along, anchorFrom.x, anchorTo.x);
			float py = Mth.lerp(along, anchorFrom.y, anchorTo.y);
			float pz = Mth.lerp(along, anchorFrom.z, anchorTo.z);
			for (int i = 1; i < POINTS; i++) {
				float vx = (chain.x[i] - chain.ox[i]) * keep;
				float vy = (chain.y[i] - chain.oy[i]) * keep;
				float vz = (chain.z[i] - chain.oz[i]) * keep;
				chain.ox[i] = chain.x[i];
				chain.oy[i] = chain.y[i];
				chain.oz[i] = chain.z[i];
				chain.x[i] += vx;
				chain.y[i] += vy - GRAVITY * STEP * STEP;
				chain.z[i] += vz;
			}
			chain.x[0] = chain.ox[0] = px;
			chain.y[0] = chain.oy[0] = py;
			chain.z[0] = chain.oz[0] = pz;
			float ballGive = BALL_GIVE;
			if (drive.mode() != Drive.FREE) {
				float tx = Mth.lerp(along, pulledFrom.x, driveTo.x);
				float ty = Mth.lerp(along, pulledFrom.y, driveTo.y);
				float tz = Mth.lerp(along, pulledFrom.z, driveTo.z);
				float by = drive.mode() == Drive.PIN ? 1.0F : pull;
				chain.x[last] += (tx - chain.x[last]) * by;
				chain.y[last] += (ty - chain.y[last]) * by;
				chain.z[last] += (tz - chain.z[last]) * by;
				if (drive.mode() == Drive.PIN) {
					ballGive = 0.0F;
				}
			}
			float segment = drive.length() / last;
			for (int pass = 0; pass < ITERATIONS; pass++) {
				for (int i = 0; i < last; i++) {
					float dx = chain.x[i + 1] - chain.x[i];
					float dy = chain.y[i + 1] - chain.y[i];
					float dz = chain.z[i + 1] - chain.z[i];
					float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (distance < 1.0E-6F) {
						continue;
					}
					float give0 = i == 0 ? 0.0F : 1.0F;
					float give1 = i + 1 == last ? ballGive : 1.0F;
					float total = give0 + give1;
					if (total <= 0.0F) {
						continue;
					}
					float off = (distance - segment) / distance / total;
					chain.x[i] += dx * off * give0;
					chain.y[i] += dy * off * give0;
					chain.z[i] += dz * off * give0;
					chain.x[i + 1] -= dx * off * give1;
					chain.y[i + 1] -= dy * off * give1;
					chain.z[i + 1] -= dz * off * give1;
				}
			}
			// The ground under the holder's feet holds the chain up.
			for (int i = 1; i < POINTS; i++) {
				float lowest = floor + (i == last ? ballRadius : 0.03F);
				if (chain.y[i] < lowest) {
					chain.y[i] = lowest;
				}
			}
		}
		if (steps == 0) {
			// No step this frame (a fast one): the chain keeps its shape and goes where the hand went.
			float dx = anchorTo.x - chain.anchor.x;
			float dy = anchorTo.y - chain.anchor.y;
			float dz = anchorTo.z - chain.anchor.z;
			chain.x[0] += dx;
			chain.y[0] += dy;
			chain.z[0] += dz;
			chain.ox[0] = chain.x[0];
			chain.oy[0] = chain.y[0];
			chain.oz[0] = chain.z[0];
		}
		chain.anchor.set(anchorTo);
		chain.pulled.set(driveTo);
		chain.pulling = drive.mode() != Drive.FREE;
		chain.length = drive.length();
	}

	/**
	 * What the swing asks of the ball this frame, from where the haft's top is and which way it points.
	 *
	 * @param anchor the top of the haft, in the world
	 * @param radial which way the haft points out from what it is whirled about (the arm), in the world
	 * @param rest   the chain's length at rest, in the world (it goes by how big the flail is drawn)
	 * @param left   the flail is in a left hand, so a blow from the side comes round from the left
	 */
	private static Drive drive(Chain chain, CombatPoses.FlailCue cue, @Nullable Aim aim, Vector3f anchor, Vector3f radial, float rest,
		boolean left, double time) {
		int stage = aim == null ? STILL : cue.stage();
		Vector3f ball = new Vector3f(chain.x[POINTS - 1], chain.y[POINTS - 1], chain.z[POINTS - 1])
			.add((float) chain.originX, (float) chain.originY, (float) chain.originZ);
		boolean whirls = cue.blow() != null && cue.blow().whirl() > 0.0F;
		// The strike starts from wherever the wind-up left the ball, and the ball comes back from wherever the
		// blow landed: from where it is, unless that stage ended a moment ago (a frame or two between them
		// that was neither, as a held pose filmed by the client test has).
		if (stage == STRIKE && chain.stage != STRIKE) {
			chain.from.set(time - chain.whirledAt <= 2.0 ? chain.whirled : ball);
		}
		if (stage == BACK && chain.stage != BACK) {
			Vec3 landed = chain.landed;
			chain.from.set(landed != null && time - chain.landedAt <= 2.0 ? new Vector3f((float) landed.x, (float) landed.y, (float) landed.z) : ball);
			chain.reached = Math.max(rest, chain.from.distance(anchor));
		}
		if (stage == WHIRL || stage == WIND) {
			chain.whirled.set(ball);
			chain.whirledAt = time;
		}
		chain.stage = stage;
		switch (stage) {
			case WHIRL, WIND -> {
				if (!whirls) {
					return new Drive(Drive.FREE, ball, 0.0F, rest);
				}
				// Whirled: round with the haft, the chain straight out from it.
				float strength = stage == WHIRL ? cue.t() : Mth.clamp(cue.t() / 0.25F, 0.0F, 1.0F);
				Vector3f at = new Vector3f(radial).mul(rest).add(anchor);
				return new Drive(Drive.PULL, at, WHIRL_PULL * strength, rest);
			}
			case STRIKE -> {
				Vector3f target = target(aim, anchor);
				Vector3f at = arc(chain.from, target, cue.blow() == null ? null : cue.blow().swirl(), aim, left, cue.t());
				return new Drive(Drive.PIN, at, 0.0F, Math.max(rest, at.distance(anchor) * 1.02F));
			}
			case HOLD -> {
				Vector3f target = target(aim, anchor);
				chain.landed = new Vec3(target.x, target.y, target.z);
				chain.landedAt = time;
				return new Drive(Drive.PIN, target, 0.0F, Math.max(rest, target.distance(anchor) * 1.02F));
			}
			case BACK -> {
				// Reeled back in, falling as it comes, and let go to swing once it is most of the way.
				float t = cue.t();
				float s = Ease.inOutSine(Math.min(1.0F, t / 0.75F));
				Vector3f hanging = new Vector3f(anchor).add(0.0F, -rest, 0.0F);
				Vector3f middle = new Vector3f(chain.from).add(hanging).mul(0.5F);
				middle.y -= 0.3F * chain.from.distance(hanging);
				Vector3f at = bezier(chain.from, middle, hanging, s);
				float length = Mth.lerp(s, chain.reached, rest);
				if (t < 0.45F) {
					return new Drive(Drive.PIN, at, 0.0F, Math.max(rest, Math.max(length, at.distance(anchor))));
				}
				return new Drive(Drive.PULL, at, 60.0F * (1.0F - (t - 0.45F) / 0.55F), Math.max(rest, length));
			}
			default -> {
				return new Drive(Drive.FREE, ball, 0.0F, rest);
			}
		}
	}

	/** Where the blow lands: on what it was aimed at, or with nothing there, the chain's full length along the look. */
	private static Vector3f target(Aim aim, Vector3f anchor) {
		if (aim.target() != null) {
			return new Vector3f((float) aim.target().x, (float) aim.target().y, (float) aim.target().z);
		}
		float out = Mth.clamp(aim.free() - 0.6F, 1.0F, CHAIN_REACH);
		return new Vector3f((float) aim.look().x, (float) aim.look().y, (float) aim.look().z).mul(out).add(anchor);
	}

	/**
	 * The ball's way out from where the strike found it to where the blow lands, a share {@code t} of the way:
	 * a curve that rises over and comes down on it, swings round from the side of the arm that throws it, or
	 * comes up from below.
	 */
	private static Vector3f arc(Vector3f from, Vector3f to, WeaponMotions.@Nullable Swirl swirl, Aim aim, boolean left, float t) {
		float span = from.distance(to);
		Vector3f middle = new Vector3f(from).add(to).mul(0.5F);
		switch (swirl == null ? WeaponMotions.Swirl.OVER : swirl) {
			case OVER -> middle.y += 0.45F * span + 0.4F;
			case SIDE -> {
				float out = (left ? -0.55F : 0.55F) * span;
				middle.add((float) aim.side().x * out, 0.25F * span, (float) aim.side().z * out);
			}
			case UNDER -> middle.y = Math.max((float) aim.footY() + 0.2F, middle.y - 0.35F * span);
		}
		return bezier(from, middle, to, t);
	}

	private static Vector3f bezier(Vector3f a, Vector3f b, Vector3f c, float t) {
		float u = 1.0F - t;
		return new Vector3f(a).mul(u * u).add(new Vector3f(b).mul(2.0F * u * t)).add(new Vector3f(c).mul(t * t));
	}

	// --- Drawing ---------------------------------------------------------------------------------------

	/**
	 * Draws the flail in a hand in third person: the pose stack is at the hand as ArmedModel.translateToHand
	 * leaves it (the shoulder, turned with the arm). The haft runs out of the fist the way any held weapon
	 * points, and the chain hangs from its top.
	 */
	public static void submitInHand(PoseStack poseStack, SubmitNodeCollector collector, int light, ItemStack flail, HumanoidArm arm,
		ArmedEntityRenderState state) {
		boolean left = arm == HumanoidArm.LEFT;
		Aim aim = state.getData(KEY);
		CombatPoses.FlailCue cue = CombatPoses.flailCue(state, arm);
		poseStack.pushPose();
		poseStack.translate((left ? 1.0F : -1.0F) / 16.0F, FIST_DOWN / 16.0F, 0.0F);
		// The arm's own length is what a whirl turns the haft about.
		Vector3f axis = poseStack.last().pose().transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
		submit(poseStack, collector, light, flail, left, aim, cue, inWorld ? VIEW_WORLD : -1, axis);
		poseStack.popPose();
	}

	/**
	 * Draws the flail in first person, where the held item would be: the pose stack is the hand's frame after
	 * the arm and the blow have been applied (x right, y up, z towards the eye). The haft stands up out of
	 * the fist, leaning forward and in, as a sword's grip does, and the ball hangs from its top in view.
	 */
	public static void submitFirstPerson(PoseStack poseStack, SubmitNodeCollector collector, int light, ItemStack flail, boolean left,
		LocalPlayer holder, float partialTick) {
		int invert = left ? -1 : 1;
		boolean mainHand = (left ? HumanoidArm.LEFT : HumanoidArm.RIGHT) == holder.getMainArm();
		CombatPoses.FlailCue cue = CombatPoses.firstPersonFlailCue(holder, mainHand, partialTick);
		poseStack.pushPose();
		Vector3f axis = poseStack.last().pose().transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
		// Where a sword's grip is, and a little higher, so the ball hanging from the top shows above the hotbar.
		poseStack.translate(invert * 1.13F / 16.0F, -1.57F / 16.0F + FIRST_PERSON_LIFT, -0.25F / 16.0F);
		// The grip's frame: the haft (-z) up, a little forward and in towards the middle of the screen; x across
		// the screen; y, which goes on down the arm in third person, back towards the eye and down.
		Vector3f haft = new Vector3f(-invert * 0.28F, 0.9F, -0.34F).normalize();
		Vector3f back = haft.negate(new Vector3f());
		Vector3f across = new Vector3f(1.0F, 0.0F, 0.0F).fma(-haft.x, haft).normalize();
		Vector3f down = back.cross(across, new Vector3f()).normalize();
		poseStack.mulPose(new Quaternionf().setFromNormalized(new org.joml.Matrix3f(across, down, back)));
		poseStack.scale(FIRST_PERSON_SCALE, FIRST_PERSON_SCALE, FIRST_PERSON_SCALE);
		submit(poseStack, collector, light, flail, left, aim(holder, partialTick), cue, VIEW_FIRST_PERSON, axis);
		poseStack.popPose();
	}

	/**
	 * Works the chain out in the world (or, with no world to hang it in, straight down the arm) and draws the
	 * haft, the links and the ball in the grip's frame.
	 *
	 * @param view which chain this is, or -1 for one in a screen, which hangs still
	 * @param axis what a whirl turns the haft about, in the pose stack's space
	 */
	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, ItemStack flail, boolean left, @Nullable Aim aim,
		CombatPoses.FlailCue cue, int view, Vector3f axis) {
		Matrix4f grip = new Matrix4f(poseStack.last().pose());
		Matrix4f toGrip = new Matrix4f(grip).invert();
		float scale = grip.getScale(new Vector3f()).x;
		float rest = CHAIN / 16.0F * scale;
		Vector3f topLocal = new Vector3f(0.0F, 0.0F, -(HAFT + RING) / 16.0F);
		Vector3f[] points = new Vector3f[POINTS];
		if (view < 0 || aim == null) {
			// In a screen (the inventory's doll) there is no world to hang it in: straight down the arm.
			for (int i = 0; i < POINTS; i++) {
				points[i] = new Vector3f(topLocal).add(0.0F, CHAIN / 16.0F * i / (POINTS - 1), 0.0F);
			}
		} else {
			Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
			Vector3f top = grip.transformPosition(new Vector3f(topLocal));
			double ax = camera.x + top.x;
			double ay = camera.y + top.y;
			double az = camera.z + top.z;
			double time = CombatAnims.now(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
			Chain chain = chain(aim.entity(), view, left, time);
			// Which way the haft points, square to what it is whirled about.
			Vector3f haft = grip.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F));
			Vector3f radial = haft.fma(-haft.dot(axis), axis, new Vector3f());
			if (radial.lengthSquared() < 1.0E-6F) {
				radial.set(0.0F, -1.0F, 0.0F);
			}
			radial.normalize();
			Vector3f anchor = new Vector3f((float) ax, (float) ay, (float) az);
			// The drive works in world floats, near enough for the few blocks a swing spans; the chain
			// itself counts from its own origin.
			Drive drive = drive(chain, cue, aim, anchor, radial, rest, left, time);
			simulate(chain, ax, ay, az, time, rest, drive, aim.footY(), BALL_TOP / 16.0F * scale * 0.8F);
			for (int i = 0; i < POINTS; i++) {
				Vector3f relative = new Vector3f((float) (chain.originX - camera.x + chain.x[i]),
					(float) (chain.originY - camera.y + chain.y[i]), (float) (chain.originZ - camera.z + chain.z[i]));
				points[i] = toGrip.transformPosition(relative);
			}
			points[0].set(topLocal);
		}
		draw(poseStack, collector, light, flail, points);
	}

	/** One box of the flail and the pose it is drawn at, relative to the grip. */
	private record Piece(ModelPart.Cube cube, Matrix4f at, int skin) {
	}

	/** The boxes it is built of, once. Model pixels; each piece's boxes are laid out along its own y. */
	private static final class Parts {
		final List<ModelPart.Cube> haft = new ArrayList<>();
		final List<ModelPart.Cube> iron = new ArrayList<>();
		final List<ModelPart.Cube> link = new ArrayList<>();
		final List<ModelPart.Cube> ball = new ArrayList<>();
		final List<ModelPart.Cube> spikes = new ArrayList<>();
		final ModelPart.Cube spike;

		Parts() {
			// The haft along -z: the wood from the butt to the top...
			this.haft.add(cube(WOOD, -0.8F, -0.8F, -HAFT, 0.8F, 0.8F, BUTT));
			// ...an iron cap on the butt, a band where it meets the fist and a socket at the top with its ring.
			this.iron.add(cube(IRON, -1.1F, -1.1F, BUTT - 0.4F, 1.1F, 1.1F, BUTT + 0.9F));
			this.iron.add(cube(IRON, -1.0F, -1.0F, -2.2F, 1.0F, 1.0F, -1.6F));
			this.iron.add(cube(IRON, -1.15F, -1.15F, -HAFT - 0.3F, 1.15F, 1.15F, -HAFT + 1.6F));
			this.iron.add(cube(IRON, -0.3F, -0.9F, -HAFT - RING - 0.6F, 0.3F, 0.9F, -HAFT - 0.3F));
			// A link: a flat ring, two long sides and two ends, three pixels long and two wide.
			float half = 1.5F;
			float bar = 0.55F;
			this.link.add(cube(IRON, -1.0F, -half, -bar / 2.0F, -1.0F + bar, half, bar / 2.0F));
			this.link.add(cube(IRON, 1.0F - bar, -half, -bar / 2.0F, 1.0F, half, bar / 2.0F));
			this.link.add(cube(IRON, -1.0F + bar, -half, -bar / 2.0F, 1.0F - bar, -half + bar, bar / 2.0F));
			this.link.add(cube(IRON, -1.0F + bar, half - bar, -bar / 2.0F, 1.0F - bar, half, bar / 2.0F));
			// The ball: a core rounded off by three slabs through it, and a ring on top for the chain.
			this.ball.add(cube(BALL, -2.4F, -2.4F, -2.4F, 2.4F, 2.4F, 2.4F));
			this.ball.add(cube(BALL, -3.0F, -1.8F, -1.8F, 3.0F, 1.8F, 1.8F));
			this.ball.add(cube(BALL, -1.8F, -3.0F, -1.8F, 1.8F, 3.0F, 1.8F));
			this.ball.add(cube(BALL, -1.8F, -1.8F, -3.0F, 1.8F, 1.8F, 3.0F));
			this.ball.add(cube(IRON, -0.3F, 2.9F, -0.9F, 0.3F, BALL_TOP + 0.3F, 0.9F));
			// Spikes out of the five faces the ring leaves free...
			this.spikes.add(cube(SPIKE, -0.6F, -4.6F, -0.6F, 0.6F, -2.9F, 0.6F));
			this.spikes.add(cube(SPIKE, 2.9F, -0.6F, -0.6F, 4.6F, 0.6F, 0.6F));
			this.spikes.add(cube(SPIKE, -4.6F, -0.6F, -0.6F, -2.9F, 0.6F, 0.6F));
			this.spikes.add(cube(SPIKE, -0.6F, -0.6F, 2.9F, 0.6F, 0.6F, 4.6F));
			this.spikes.add(cube(SPIKE, -0.6F, -0.6F, -4.6F, 0.6F, 0.6F, -2.9F));
			// ...and one to be set on each corner, pointing out along its diagonal.
			this.spike = cube(SPIKE, -0.55F, 0.0F, -0.55F, 0.55F, 1.7F, 0.55F);
		}
	}

	private static ModelPart.Cube cube(int skin, float x0, float y0, float z0, float x1, float y1, float z1) {
		return new ModelPart.Cube(SKIN_AT[skin][0], SKIN_AT[skin][1], Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1),
			Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0), 0.0F, 0.0F, 0.0F, false, TEXTURE_SIZE, TEXTURE_SIZE,
			EnumSet.allOf(Direction.class));
	}

	/** The eight corner spikes' turns, from +y to each diagonal. */
	private static final Quaternionf[] CORNERS = new Quaternionf[8];

	static {
		int i = 0;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					Vector3f diagonal = new Vector3f(sx, sy, sz).normalize();
					CORNERS[i++] = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), diagonal);
				}
			}
		}
	}

	/**
	 * Lays the haft, the links along the chain's points and the ball at the end of them, all in the grip's
	 * frame (blocks), and draws them in one go.
	 */
	private static void draw(PoseStack poseStack, SubmitNodeCollector collector, int light, ItemStack flail, Vector3f[] points) {
		if (parts == null) {
			parts = new Parts();
		}
		Parts made = parts;
		List<Piece> pieces = new ArrayList<>();
		Matrix4f none = new Matrix4f();
		for (ModelPart.Cube cube : made.haft) {
			pieces.add(new Piece(cube, none, WOOD));
		}
		for (ModelPart.Cube cube : made.iron) {
			pieces.add(new Piece(cube, none, IRON));
		}

		// The ball hangs off the end of the chain, its ring towards the last link.
		Vector3f ballAt = points[POINTS - 1];
		Vector3f up = new Vector3f(points[POINTS - 2]).sub(ballAt);
		if (up.lengthSquared() < 1.0E-8F) {
			up.set(0.0F, -1.0F, 0.0F);
		}
		up.normalize();
		Quaternionf ballTurn = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), up);
		Matrix4f ball = new Matrix4f().translation(ballAt).rotate(ballTurn);
		for (ModelPart.Cube cube : made.ball) {
			pieces.add(new Piece(cube, ball, cube == made.ball.get(made.ball.size() - 1) ? IRON : BALL));
		}
		for (ModelPart.Cube cube : made.spikes) {
			pieces.add(new Piece(cube, ball, SPIKE));
		}
		for (Quaternionf corner : CORNERS) {
			Matrix4f at = new Matrix4f(ball).rotate(corner).translate(0.0F, 2.5F / 16.0F, 0.0F);
			pieces.add(new Piece(made.spike, at, SPIKE));
		}

		// The links, one every pitch along the chain from the haft's ring to the ball's, each turned a quarter
		// from the one before about the chain.
		List<Vector3f> line = new ArrayList<>(POINTS);
		for (int i = 0; i < POINTS - 1; i++) {
			line.add(points[i]);
		}
		line.add(new Vector3f(up).mul(BALL_TOP / 16.0F).add(ballAt));
		float[] along = new float[line.size()];
		for (int i = 1; i < line.size(); i++) {
			along[i] = along[i - 1] + line.get(i).distance(line.get(i - 1));
		}
		float total = along[along.length - 1];
		float pitch = LINK_PITCH / 16.0F;
		int links = Math.max(1, Math.min(160, Math.round(total / pitch)));
		Vector3f normal = new Vector3f(1.0F, 0.0F, 0.0F);
		int segment = 0;
		for (int n = 0; n < links; n++) {
			float at = (n + 0.5F) * total / links;
			while (segment < along.length - 2 && along[segment + 1] < at) {
				segment++;
			}
			float length = along[segment + 1] - along[segment];
			float t = length < 1.0E-6F ? 0.0F : (at - along[segment]) / length;
			Vector3f a = line.get(segment);
			Vector3f b = line.get(segment + 1);
			Vector3f centre = new Vector3f(a).lerp(b, t);
			Vector3f tangent = new Vector3f(b).sub(a);
			if (tangent.lengthSquared() < 1.0E-10F) {
				tangent.set(0.0F, -1.0F, 0.0F);
			}
			tangent.normalize();
			// Carried along from link to link, so the quarter turns stay put however the chain bends.
			normal.fma(-normal.dot(tangent), tangent);
			if (normal.lengthSquared() < 1.0E-6F) {
				normal.set(Math.abs(tangent.x) < 0.9F ? 1.0F : 0.0F, Math.abs(tangent.x) < 0.9F ? 0.0F : 1.0F, 0.0F);
				normal.fma(-normal.dot(tangent), tangent);
			}
			normal.normalize();
			Vector3f side = n % 2 == 0 ? new Vector3f(normal) : new Vector3f(tangent).cross(normal).normalize();
			Vector3f face = new Vector3f(side).cross(tangent).normalize();
			Matrix4f turn = new Matrix4f().translation(centre).mul(new Matrix4f(
				side.x, side.y, side.z, 0.0F,
				tangent.x, tangent.y, tangent.z, 0.0F,
				face.x, face.y, face.z, 0.0F,
				0.0F, 0.0F, 0.0F, 1.0F));
			// Spread out a little when the chain is let out long, so it does not look thin.
			float stretch = Math.max(1.0F, total / links / pitch);
			if (stretch > 1.0F) {
				turn.scale(1.0F, stretch, 1.0F);
			}
			for (ModelPart.Cube cube : made.link) {
				pieces.add(new Piece(cube, turn, IRON));
			}
		}

		CustomModelData colours = flail.get(DataComponents.CUSTOM_MODEL_DATA);
		int[] tints = new int[SLOT.length];
		for (int skin = 0; skin < SLOT.length; skin++) {
			Integer colour = colours == null ? null : colours.getColor(SLOT[skin]);
			tints[skin] = 0xFF000000 | (colour == null ? DEFAULT_COLOURS[SLOT[skin]] : colour);
		}
		RenderType type = RenderTypes.entityCutout(TEXTURE);
		collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> drawPieces(pose, buffer, pieces, tints, light));
	}

	private static void drawPieces(PoseStack.Pose pose, VertexConsumer buffer, List<Piece> pieces, int[] tints, int light) {
		PoseStack.Pose at = pose.copy();
		for (Piece piece : pieces) {
			at.set(pose);
			at.mulPose(piece.at());
			piece.cube().compile(at, buffer, light, OverlayTexture.NO_OVERLAY, tints[piece.skin()]);
		}
	}
}
