package dev.forja.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.forja.combat.CombatAnim;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * How a monster moves when it is not just walking: the <b>run</b> and the <b>leap</b>. Andy, 2026-09-27:
 * "¿correr tiene animación o harás una?", and then "dale animación al salto".
 *
 * <ul>
 *   <li>The run is read off vanilla's sprint flag, which the server sets on a running monster
 *       ({@link dev.forja.ai.MobSprint}) and every client already has. It eases in and out over a few ticks,
 *       and is as strong as the monster is actually going: a runner held up against a wall barely leans.
 *       Humanoids lean into it with the head still up at their target, stride long and pump their arms,
 *       and a weapon is carried low and forward rather than swung about; a zombie keeps its arms out and
 *       lunges along. A spider scuttles lower and faster, a creeper waddles and bobs.</li>
 *   <li>A leap is told by the server as it leaves the ground (CombatAnim.Kind.LEAP): at a target the body
 *       stretches out forward with the legs trailing, a zombie's arms out to grab, a spider's legs spread, a
 *       weapon drawn back ready; hopping back it gathers up with its guard in front; charging it goes
 *       shoulder first. It comes down in a short crouch and a puff of dust.</li>
 * </ul>
 *
 * The fight's own poses win over all of this: while a blow is swung or warned of, a charge held, a stagger
 * or a dodge under way ({@link CombatPoses}), the arms and the lean go back to the fight within two ticks.
 * Only monsters: a player never runs this way, and a monster doing none of it is drawn exactly as vanilla
 * draws it.
 */
public final class MobGaits {
	/** The kinds of body there is a way of running for. */
	public enum Body { HUMANOID, ILLAGER, SPIDER, CREEPER, GEO, OTHER }

	/**
	 * One frame of it, for the renderer and the models.
	 *
	 * @param run       how far into its run the body is, 0 to 1, eased in and out
	 * @param stride    how fast it is actually going, 0 (standing) to 1
	 * @param free      the share of the run and the leap the arms and the lean show: 1, down to 0 while the
	 *                  fight has them
	 * @param walkPos   where vanilla's walk cycle is, the same number its legs go by
	 * @param air       how far into the pose of a leap, 0 to 1
	 * @param style     which leap (CombatAnim.Kind.LEAP_*), 0 for none
	 * @param land      the crouch of a landing, 0 to 1
	 * @param landStyle the leap it came down from
	 * @param yaw       which way the body faces, in degrees
	 */
	public record Gait(Body body, float run, float stride, float free, float walkPos, float air, float style, float land,
		float landStyle, float yaw) {
		boolean still() {
			return this.run <= 0.0F && this.air <= 0.0F && this.land <= 0.0F;
		}

		boolean leaping(float which) {
			return this.air > 0.0F && this.style == which;
		}

		/** A stride's worth of vanilla's walk cycle is 2π of this. */
		float phase() {
			return this.walkPos * 0.6662F;
		}
	}

	public static final RenderStateDataKey<Gait> KEY = RenderStateDataKey.create(() -> "forja:gait");

	/** The run eases in and out over this many ticks. */
	private static final float RUN_TICKS = 4.0F;
	/** The fight takes the arms over in this many ticks, and gives them back over the other. */
	private static final float BUSY_TICKS = 2.0F;
	private static final float FREE_TICKS = 5.0F;
	/** A leap's pose comes in over this many ticks. */
	private static final float AIR_TICKS = 2.5F;
	/** The crouch of a landing, all of it. */
	private static final float LAND_TICKS = 7.0F;
	/** How far up a leap has to be seen to go, and down from the top of it, in blocks. */
	private static final double LIFT_SEEN = 0.08;
	/** A charge is over once the body is going slower than this, in blocks a tick. */
	private static final double CHARGE_DONE = 0.12;

	private static final class Track {
		float run;
		float runO;
		float free = 1.0F;
		float freeO = 1.0F;
		int tick = Integer.MIN_VALUE;
	}

	private static final Map<LivingEntity, Track> TRACKS = new WeakHashMap<>();

	private MobGaits() {
	}

	/** What kind of body a renderer draws, or null for one that is none of this module's business. */
	public static @Nullable Body bodyOf(EntityRenderer<?, ?> renderer) {
		if (renderer instanceof LivingEntityRenderer<?, ?, ?> living) {
			Object model = living.getModel();
			if (model instanceof HumanoidModel<?>) {
				return Body.HUMANOID;
			}
			if (model instanceof IllagerModel<?>) {
				return Body.ILLAGER;
			}
			if (model instanceof SpiderModel) {
				return Body.SPIDER;
			}
			if (model instanceof CreeperModel) {
				return Body.CREEPER;
			}
			return Body.OTHER;
		}
		return renderer instanceof com.geckolib.renderer.GeoEntityRenderer<?, ?> ? Body.GEO : null;
	}

	/**
	 * Worked out while the entity is at hand; null for anything that is not a monster (players above all, and
	 * a cat sprinting after its own business), and for a monster doing none of this.
	 */
	public static @Nullable Gait compute(LivingEntity entity, @Nullable Body body, float partialTick) {
		if (body == null || !(entity instanceof Mob) || !(entity instanceof Enemy) || entity.isPassenger() || entity.deathTime > 0) {
			return null;
		}
		double now = CombatAnims.now(partialTick);
		CombatAnims.State anim = CombatAnims.get(entity.getId());
		boolean running = entity.isSprinting() && !entity.isInWater();
		boolean busy = busy(entity, anim, now);

		Track track = TRACKS.computeIfAbsent(entity, e -> new Track());
		float runTarget = running ? 1.0F : 0.0F;
		float freeTarget = busy ? 0.0F : 1.0F;
		if (track.tick == Integer.MIN_VALUE || entity.tickCount < track.tick) {
			track.run = track.runO = runTarget;
			track.free = track.freeO = freeTarget;
		} else {
			for (int i = Math.min(8, entity.tickCount - track.tick); i > 0; i--) {
				track.runO = track.run;
				track.run = approach(track.run, runTarget, 1.0F / RUN_TICKS);
				track.freeO = track.free;
				track.free = approach(track.free, freeTarget, 1.0F / (freeTarget < track.free ? BUSY_TICKS : FREE_TICKS));
			}
		}
		track.tick = entity.tickCount;
		float run = Ease.inOutSine(Mth.lerp(partialTick, track.runO, track.run));
		float free = Ease.inOutSine(Mth.lerp(partialTick, track.freeO, track.free));
		float stride = Mth.clamp(entity.walkAnimation.speed(partialTick) * 2.5F, 0.0F, 1.0F);

		float air = 0.0F;
		float style = 0.0F;
		float land = 0.0F;
		float landStyle = 0.0F;
		if (anim != null) {
			if (anim.leapAt != CombatAnims.State.NEVER) {
				double t = now - anim.leapAt;
				boolean charge = anim.leapStyle == CombatAnim.Kind.LEAP_CHARGE;
				double y = entity.getY();
				if (Double.isNaN(anim.leapY)) {
					anim.leapY = y;
					anim.leapPeak = y;
				}
				anim.leapPeak = Math.max(anim.leapPeak, y);
				// Gone by what is drawn, not by the ground flag, which comes a few ticks ahead of the body it
				// belongs to: the server says so as it leaves, and the body is seen to leave a tick or three
				// later; only what has been seen to go up can be seen to come down.
				if (y > anim.leapY + LIFT_SEEN) {
					anim.leapAirborne = true;
				}
				boolean down;
				if (charge) {
					down = t > 3.0 && Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo) < CHARGE_DONE || t > anim.leapTicks + 3.0;
				} else if (anim.leapAirborne) {
					// On its way down and no longer falling: it has hit something.
					down = y < anim.leapPeak - LIFT_SEEN && y - entity.yo > -0.01 || t > anim.leapTicks + 4.0;
				} else {
					down = t > 10.0;
				}
				if (t > anim.leapTicks + 10.0) {
					// Long over while nobody was looking: nothing to show for it now.
					anim.leapAt = CombatAnims.State.NEVER;
				} else if (down) {
					anim.landAt = now;
					anim.landStyle = anim.leapStyle;
					anim.leapAt = CombatAnims.State.NEVER;
					dust(entity, charge);
				} else {
					air = Ease.outCubic((float) Math.min(1.0, t / AIR_TICKS));
					style = anim.leapStyle;
				}
			}
			if (anim.landAt != CombatAnims.State.NEVER) {
				float t = (float) (now - anim.landAt);
				if (t >= 0.0F && t < LAND_TICKS) {
					// Down fast on impact, up again slowly; the leap's own pose lets go over the first two ticks.
					land = t < 1.0F ? Ease.outCubic(t) : 1.0F - Ease.inOutSine((t - 1.0F) / (LAND_TICKS - 1.0F));
					landStyle = anim.landStyle;
					if (style == 0.0F && t < 2.0F) {
						air = 1.0F - Ease.inOutSine(t / 2.0F);
						style = anim.landStyle;
					}
				}
			}
		}
		float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
		Gait gait = new Gait(body, run, stride, free, entity.walkAnimation.position(partialTick), air, style, land, landStyle, yaw);
		return gait.still() ? null : gait;
	}

	private static float approach(float from, float to, float step) {
		return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
	}

	/** Whether the fight has the body: a blow swung or warned of, a charge, a crouch to leap, a stagger, a dodge, a parry. */
	private static boolean busy(LivingEntity entity, CombatAnims.@Nullable State anim, double now) {
		if (entity.swinging || entity.attackAnim > 0.0F) {
			return true;
		}
		if (anim == null) {
			return false;
		}
		return CombatAnims.progress(anim.telegraphAt, anim.telegraphTicks + 6, now) >= 0.0F
			|| CombatAnims.progress(anim.lungeAt, anim.lungeTicks, now) >= 0.0F
			|| CombatAnims.progress(anim.staggerAt, anim.staggerTicks, now) >= 0.0F
			|| CombatAnims.progress(anim.dodgeAt, anim.dodgeTicks, now) >= 0.0F
			|| CombatAnims.progress(anim.parryAt, 8, now) >= 0.0F
			|| CombatAnims.progress(anim.guardBreakAt, 12, now) >= 0.0F
			|| anim.chargeAt != CombatAnims.State.NEVER;
	}

	/**
	 * A puff of the ground it came down on, thrown out round its feet, and a little cloud. The body is drawn
	 * a moment behind where the server has it, so the ground is looked for a little way under it.
	 */
	private static void dust(LivingEntity entity, boolean skid) {
		var level = entity.level();
		BlockPos.MutableBlockPos at = BlockPos.containing(entity.getX(), entity.getY() + 0.1, entity.getZ()).mutable();
		BlockState ground = null;
		for (int down = 0; down < 3; down++, at.move(0, -1, 0)) {
			BlockState state = level.getBlockState(at);
			if (!state.isAir() && state.getRenderShape() != RenderShape.INVISIBLE && !state.getCollisionShape(level, at).isEmpty()) {
				ground = state;
				break;
			}
		}
		if (ground == null) {
			return;
		}
		double floor = at.getY() + level.getBlockState(at).getCollisionShape(level, at).max(net.minecraft.core.Direction.Axis.Y) + 0.05;
		BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		int count = skid ? 18 : 14;
		float radius = entity.getBbWidth() * 0.6F;
		for (int i = 0; i < count; i++) {
			double angle = i * Mth.TWO_PI / count + entity.getRandom().nextFloat() * 0.4;
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			level.addParticle(particle, entity.getX() + dx * radius, floor, entity.getZ() + dz * radius,
				dx * 0.2, 0.1 + entity.getRandom().nextFloat() * 0.1, dz * 0.2);
		}
		for (int i = 0; i < (skid ? 4 : 3); i++) {
			double angle = entity.getRandom().nextFloat() * Mth.TWO_PI;
			level.addParticle(ParticleTypes.POOF, entity.getX() + Math.cos(angle) * radius, floor + 0.05, entity.getZ() + Math.sin(angle) * radius,
				Math.cos(angle) * 0.04, 0.01, Math.sin(angle) * 0.04);
		}
	}

	// --- The whole body ---------------------------------------------------------------------------------

	/** How far a run leans each kind of body forward, in degrees, at full tilt. */
	private static float runLean(Body body) {
		return switch (body) {
			case HUMANOID, ILLAGER -> 17.0F;
			case CREEPER -> 12.0F;
			case SPIDER -> 7.0F;
			case GEO -> 9.0F;
			case OTHER -> 7.0F;
		};
	}

	/** How high a running body bounces, in blocks, twice a stride. */
	private static float runBob(Body body) {
		return switch (body) {
			case HUMANOID, ILLAGER -> 0.06F;
			case CREEPER -> 0.08F;
			case GEO -> 0.03F;
			default -> 0.0F;
		};
	}

	/** How far forward the leap at a target tips each body, in degrees; the spider rears up instead. */
	private static float airLean(Body body) {
		return switch (body) {
			case HUMANOID, ILLAGER -> 30.0F;
			case SPIDER -> -12.0F;
			case CREEPER -> 8.0F;
			default -> 15.0F;
		};
	}

	/** The forward lean of the whole body this frame, in degrees (negative is back): run, leap and landing. */
	static float lean(Gait g) {
		float lean = runLean(g.body()) * g.run() * (0.35F + 0.65F * g.stride()) * (0.3F + 0.7F * g.free());
		if (g.air() > 0.0F) {
			if (g.style() == CombatAnim.Kind.LEAP_FORWARD) {
				lean += airLean(g.body()) * g.air();
			} else if (g.style() == CombatAnim.Kind.LEAP_BACK) {
				lean -= 16.0F * g.air();
			} else if (g.style() == CombatAnim.Kind.LEAP_CHARGE) {
				lean += 25.0F * g.air();
			}
		}
		if (g.land() > 0.0F) {
			// Coming down off a leap forward it digs its heels in; off a hop back it rocks forward onto its feet.
			float skid = g.landStyle() == CombatAnim.Kind.LEAP_BACK ? 6.0F : g.landStyle() == CombatAnim.Kind.LEAP_CHARGE ? -14.0F : -10.0F;
			lean += (g.body() == Body.SPIDER ? skid * 0.5F : skid) * g.land();
		}
		return lean;
	}

	/** How far a charge turns a humanoid shoulder first, in degrees. */
	static float twist(Gait g) {
		return g.leaping(CombatAnim.Kind.LEAP_CHARGE) && (g.body() == Body.HUMANOID || g.body() == Body.ILLAGER) ? 35.0F * g.air() : 0.0F;
	}

	/**
	 * Leans, bounces, turns and squashes the whole body about its feet. Called before the renderer turns it
	 * to face its way, like {@link CombatPoses#applyLean}, so the lean is along the way it faces.
	 */
	public static void applyBody(@Nullable Gait g, PoseStack poseStack) {
		if (g == null) {
			return;
		}
		Body body = g.body();
		float phase = g.phase();
		float lift = 0.0F;
		float roll = 0.0F;
		float squash = 1.0F;
		if (g.run() > 0.0F) {
			float going = g.run() * g.stride();
			if (body == Body.CREEPER) {
				// A quick waddle: side to side once a step, up and down twice.
				float quick = phase * CREEPER_QUICK;
				lift += runBob(body) * going * Math.abs(Mth.sin(quick));
				roll += 7.0F * going * Mth.sin(quick);
			} else {
				lift += runBob(body) * going * Math.abs(Mth.sin(phase));
			}
			if (body == Body.SPIDER) {
				// Down on its legs as it goes.
				lift -= 0.1F * g.run();
			}
		}
		if (g.leaping(CombatAnim.Kind.LEAP_BACK)) {
			squash -= 0.08F * g.air();
		}
		if (g.land() > 0.0F) {
			squash -= (body == Body.SPIDER ? 0.14F : body == Body.GEO || body == Body.OTHER ? 0.15F : 0.2F) * g.land();
		}
		float lean = lean(g);
		float twist = twist(g);

		if (lift != 0.0F) {
			poseStack.translate(0.0F, lift, 0.0F);
		}
		double yaw = Math.toRadians(g.yaw());
		float forwardX = (float) -Math.sin(yaw);
		float forwardZ = (float) Math.cos(yaw);
		if (lean != 0.0F) {
			// Turning about (z, 0, -x) carries straight up towards (x, 0, z): see CombatPoses#applyLean.
			poseStack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(lean), forwardZ, 0.0F, -forwardX));
		}
		if (roll != 0.0F) {
			// About the way it faces: the top of it rocks from one side to the other.
			poseStack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(roll), forwardX, 0.0F, forwardZ));
		}
		if (twist != 0.0F) {
			// Turned further round to its right, which puts the left shoulder in front (the head is turned back).
			poseStack.mulPose(Axis.YP.rotationDegrees(twist));
		}
		if (squash != 1.0F) {
			float widen = 1.0F + (1.0F - squash) * 0.5F;
			poseStack.scale(widen, squash, widen);
		}
	}

	// --- Humanoids -------------------------------------------------------------------------------------

	/** A leg's swing at a run, from the cosine of its stride: further forward (the knee up) than back. */
	private static float stride(float c) {
		return (c < 0.0F ? 1.15F * c : 0.95F * c) - 0.15F;
	}

	/**
	 * A humanoid monster's legs, head and arms for its run and its leap. Called just before the model sets up
	 * the swing, so that a blow thrown on the run starts from the run's arms (see CombatPoses#applyArms);
	 * a zombie's, a skeleton's and a piglin's own arm poses come after, and are seen to in
	 * {@link #zombieArms}, {@link #reposeArms} and {@link #leapArms}.
	 */
	public static void poseHumanoid(HumanoidModel<?> model, HumanoidRenderState state) {
		Gait g = state.getData(KEY);
		if (g == null || state.isPassenger || state.swimAmount > 0.0F) {
			return;
		}
		float c = Mth.cos(g.phase());
		float going = g.run() * g.stride();
		if (going > 0.0F) {
			model.rightLeg.xRot = Mth.lerp(going, model.rightLeg.xRot, stride(c));
			model.leftLeg.xRot = Mth.lerp(going, model.leftLeg.xRot, stride(-c));
		}
		legsInTheAir(model.rightLeg, model.leftLeg, g, c);
		// The head stays on its target while the body leans under it, and looks ahead through a charge.
		model.head.xRot -= (float) Math.toRadians(lean(g)) * 0.75F;
		model.head.yRot += (float) Math.toRadians(twist(g));
		reposeArms(model, state, true);
	}

	/** The legs through a leap and its landing, for humanoids and illagers alike. */
	private static void legsInTheAir(ModelPart rightLeg, ModelPart leftLeg, Gait g, float c) {
		if (g.air() > 0.0F) {
			float a = g.air();
			if (g.style() == CombatAnim.Kind.LEAP_FORWARD) {
				// Stretched out behind it, one further than the other.
				rightLeg.xRot = Mth.lerp(a, rightLeg.xRot, 0.35F);
				leftLeg.xRot = Mth.lerp(a, leftLeg.xRot, 0.85F);
			} else if (g.style() == CombatAnim.Kind.LEAP_BACK) {
				// Tucked up in front.
				rightLeg.xRot = Mth.lerp(a, rightLeg.xRot, -0.95F);
				leftLeg.xRot = Mth.lerp(a, leftLeg.xRot, -0.6F);
			} else if (g.style() == CombatAnim.Kind.LEAP_CHARGE) {
				// Driving on at a full run whatever the flag says.
				rightLeg.xRot = Mth.lerp(a, rightLeg.xRot, stride(c) * Math.max(0.6F, g.stride()));
				leftLeg.xRot = Mth.lerp(a, leftLeg.xRot, stride(-c) * Math.max(0.6F, g.stride()));
			}
		}
		if (g.land() > 0.0F) {
			float l = g.land();
			// Feet apart to take it: one braced in front, the other behind.
			float front = g.landStyle() == CombatAnim.Kind.LEAP_BACK ? -0.35F : -0.7F;
			rightLeg.xRot = Mth.lerp(l, rightLeg.xRot, front);
			leftLeg.xRot = Mth.lerp(l, leftLeg.xRot, 0.45F);
		}
	}

	/**
	 * The arms for the run (when {@code run}) and for a leap. Each arm that is posed for what it holds
	 * (a bow drawn, a crossbow, a raised shield) is left as it is. What is swung like a weapon is carried
	 * low and forward at a run, both hands on it when it takes two; an empty hand pumps against the legs.
	 */
	public static void reposeArms(HumanoidModel<?> model, HumanoidRenderState state, boolean run) {
		Gait g = state.getData(KEY);
		if (g == null || state.isPassenger || state.swimAmount > 0.0F) {
			return;
		}
		boolean rightMain = state.mainArm == HumanoidArm.RIGHT;
		ModelPart main = rightMain ? model.rightArm : model.leftArm;
		ModelPart off = rightMain ? model.leftArm : model.rightArm;
		boolean mainFree = armFree(state, state.mainArm);
		boolean offFree = armFree(state, state.mainArm.getOpposite());
		boolean armed = !state.getMainHandItemStack().isEmpty();
		boolean offHolds = !state.getUseItemStackForArm(state.mainArm.getOpposite()).isEmpty();
		WeaponMotions.Motion motion = WeaponMotions.of(state.getMainHandItemStack());
		boolean twoHands = motion != null && motion.hands() == WeaponMotions.Hands.TWO && !offHolds;
		// +1 turns an arm in across the body, whichever side it is on.
		float mainIn = rightMain ? -1.0F : 1.0F;
		float offIn = -mainIn;
		float c = Mth.cos(g.phase());
		// The main arm is the right one's swing when it is on the right: against the right leg.
		float mainPump = rightMain ? -c : c;

		float w = run ? g.run() * g.free() : 0.0F;
		if (w > 0.0F) {
			float pump = g.stride();
			if (armed && twoHands) {
				// Both hands on it, held across in front at port arms, bobbing a little with the stride.
				float bob = 0.1F * pump * Mth.sin(g.phase() * 2.0F);
				if (mainFree) {
					blend(main, w, -0.8F + bob, mainIn * 0.4F, main.zRot);
				}
				if (offFree) {
					blend(off, w, -0.75F + bob, offIn * 0.55F, off.zRot);
				}
			} else {
				if (mainFree) {
					if (armed) {
						// Carried low and forward, the point ahead: not swung about like a jogger's fist.
						blend(main, w, -0.55F - 0.2F * pump * mainPump, mainIn * 0.12F, main.zRot);
					} else {
						blend(main, w, -0.3F + 1.05F * pump * mainPump, main.yRot, main.zRot);
					}
				}
				if (offFree) {
					float swing = offHolds ? 0.4F : 1.05F;
					blend(off, w, -0.3F - swing * pump * mainPump, off.yRot, off.zRot);
				}
			}
		}
		leapArms(model, state, main, off, mainFree, offFree, armed, mainIn, offIn);
	}

	/** For a piglin, whose blade is held up high at a run of its own accord: only the leap's arms. */
	public static void leapArms(HumanoidModel<?> model, HumanoidRenderState state) {
		Gait g = state.getData(KEY);
		if (g == null || g.air() <= 0.0F && g.land() <= 0.0F) {
			return;
		}
		boolean rightMain = state.mainArm == HumanoidArm.RIGHT;
		float mainIn = rightMain ? -1.0F : 1.0F;
		leapArms(model, state, rightMain ? model.rightArm : model.leftArm, rightMain ? model.leftArm : model.rightArm,
			armFree(state, state.mainArm), armFree(state, state.mainArm.getOpposite()), !state.getMainHandItemStack().isEmpty(),
			mainIn, -mainIn);
	}

	private static void leapArms(HumanoidModel<?> model, HumanoidRenderState state, ModelPart main, ModelPart off,
		boolean mainFree, boolean offFree, boolean armed, float mainIn, float offIn) {
		Gait g = state.getData(KEY);
		float a = g.air() * g.free();
		if (a > 0.0F) {
			if (g.style() == CombatAnim.Kind.LEAP_FORWARD) {
				// Reaching out at it. A weapon that has blows of its own is drawn back ready instead (CombatPoses#frame);
				// anything else in the hand goes up high to come down on it.
				if (mainFree) {
					if (armed) {
						blend(main, a, -2.5F, mainIn * 0.2F, main.zRot);
					} else {
						blend(main, a, -1.4F, -mainIn * 0.25F, main.zRot);
					}
				}
				if (offFree) {
					blend(off, a, -1.4F, -offIn * 0.25F, off.zRot);
				}
			} else if (g.style() == CombatAnim.Kind.LEAP_BACK) {
				// On guard: the weapon across in front, the other fist up beside it.
				if (mainFree) {
					blend(main, a, armed ? -1.15F : -1.05F, mainIn * 0.35F, main.zRot);
				}
				if (offFree) {
					blend(off, a, -1.0F, offIn * 0.45F, off.zRot);
				}
			} else if (g.style() == CombatAnim.Kind.LEAP_CHARGE) {
				// The leading arm folded in across the chest behind its shoulder, the weapon trailing low behind
				// to swing through with.
				if (offFree) {
					blend(off, a, -0.95F, offIn * 0.65F, offIn * 0.15F);
				}
				if (mainFree) {
					blend(main, a, 0.55F, main.yRot, -mainIn * 0.15F);
				}
			}
		}
		float l = g.land() * g.free();
		if (l > 0.0F && g.landStyle() != CombatAnim.Kind.LEAP_BACK) {
			// Arms out a little for balance as it skids to a stop.
			if (offFree) {
				blend(off, l * 0.8F, -0.5F, off.yRot, -offIn * 0.55F);
			}
			if (mainFree && !armed) {
				blend(main, l * 0.8F, -0.5F, main.yRot, -mainIn * 0.55F);
			}
		}
	}

	/** Whether an arm is posed only by what hangs in it: nothing drawn, raised or aimed. */
	private static boolean armFree(HumanoidRenderState state, HumanoidArm arm) {
		HumanoidModel.ArmPose pose = arm == HumanoidArm.RIGHT ? state.rightArmPose : state.leftArmPose;
		if (pose != HumanoidModel.ArmPose.EMPTY && pose != HumanoidModel.ArmPose.ITEM) {
			return false;
		}
		if (state.isUsingItem) {
			HumanoidArm using = state.useItemHand == net.minecraft.world.InteractionHand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
			return using != arm;
		}
		return true;
	}

	/** Takes a part {@code w} of the way to the given turn. The turn about z is left alone when passed its own value. */
	private static void blend(ModelPart part, float w, float xRot, float yRot, float zRot) {
		part.xRot = Mth.lerp(w, part.xRot, xRot);
		part.yRot = Mth.lerp(w, part.yRot, yRot);
		part.zRot = Mth.lerp(w, part.zRot, zRot);
	}

	/** A weapon worth holding ready through a leap at a target: how far drawn back, 0 when not leaping at one. */
	static float readyToStrike(HumanoidRenderState state) {
		Gait g = state.getData(KEY);
		return g != null && g.leaping(CombatAnim.Kind.LEAP_FORWARD) ? g.air() * g.free() : 0.0F;
	}

	/**
	 * Zombies (and drowned, husks, zombie villagers, zombified piglins, a vindicator attacking with bare
	 * hands) hold their arms out, and keep them out at a run: a lunging, grasping run, the arms bobbing
	 * against the stride on top of vanilla's pose, so a swing thrown on the run still reads. Leaping, they
	 * reach out wide to grab; hopping back, they bring them in lower to fend off.
	 */
	public static void zombieArms(ModelPart leftArm, ModelPart rightArm, HumanoidRenderState state) {
		Gait g = state.getData(KEY);
		if (g == null || state.isPassenger || state.swimAmount > 0.0F) {
			return;
		}
		float w = g.run() * g.free();
		if (w > 0.0F) {
			float c = Mth.cos(g.phase());
			float pump = 0.22F * g.stride();
			rightArm.xRot += w * (-0.05F - pump * c);
			leftArm.xRot += w * (-0.05F + pump * c);
		}
		float a = g.air() * g.free();
		if (a > 0.0F) {
			if (g.style() == CombatAnim.Kind.LEAP_FORWARD) {
				blend(rightArm, a, -1.35F, 0.3F, 0.0F);
				blend(leftArm, a, -1.35F, -0.3F, 0.0F);
			} else if (g.style() == CombatAnim.Kind.LEAP_BACK) {
				blend(rightArm, a, -1.3F, -0.2F, 0.0F);
				blend(leftArm, a, -1.3F, 0.2F, 0.0F);
			}
		}
	}

	// --- Illagers, spiders, creepers -------------------------------------------------------------------

	/** Illagers: the long stride and the leap's legs; their arms are posed by what they are doing, and left to it. */
	public static void illagerLegs(ModelPart head, ModelPart rightLeg, ModelPart leftLeg, @Nullable Gait g) {
		if (g == null) {
			return;
		}
		float c = Mth.cos(g.phase());
		float going = g.run() * g.stride();
		if (going > 0.0F) {
			rightLeg.xRot = Mth.lerp(going, rightLeg.xRot, stride(c));
			leftLeg.xRot = Mth.lerp(going, leftLeg.xRot, stride(-c));
		}
		legsInTheAir(rightLeg, leftLeg, g, c);
		head.xRot -= (float) Math.toRadians(lean(g)) * 0.75F;
		head.yRot += (float) Math.toRadians(twist(g));
	}

	/** How much faster a spider's legs go at a run. */
	private static final float SPIDER_QUICK = 1.6F;
	/** And a creeper's. */
	private static final float CREEPER_QUICK = 1.5F;
	/** The four pairs of a spider's legs, hind to front, a quarter of a cycle apart as vanilla has them. */
	private static final float[] SPIDER_OFFSETS = {0.0F, Mth.PI, Mth.HALF_PI, Mth.PI * 1.5F};

	/**
	 * A spider at a run: the same scuttle as its walk, but quicker, wider and flatter, the body down between
	 * its legs. Leaping, the legs spread wide; hopping back, the front ones come up in front of it; landing,
	 * they splay under the weight. Vanilla's swing is taken back out of each leg and the run's put in.
	 *
	 * @param rights the right legs hind to front, {@code lefts} the left
	 */
	public static void spiderLegs(ModelPart head, ModelPart[] rights, ModelPart[] lefts, float walkPos, float walkSpeed, @Nullable Gait g) {
		if (g == null) {
			return;
		}
		float going = g.run() * g.stride();
		float pos = walkPos * 0.6662F;
		float quick = pos * SPIDER_QUICK;
		for (int i = 0; i < 4; i++) {
			float offset = SPIDER_OFFSETS[i];
			float swing = 0.0F;
			float step = 0.0F;
			if (going > 0.0F) {
				float vanillaSwing = -(Mth.cos(pos * 2.0F + offset) * 0.4F) * walkSpeed;
				float vanillaStep = Math.abs(Mth.sin(pos + offset) * 0.4F) * walkSpeed;
				float runSwing = -(Mth.cos(quick * 2.0F + offset) * 0.55F);
				float runStep = Math.abs(Mth.sin(quick + offset) * 0.5F);
				swing += going * (runSwing - vanillaSwing);
				step += going * (runStep - vanillaStep);
			}
			// Flatter at a run, so the body rides lower.
			step += 0.15F * g.run();
			boolean front = i >= 2;
			if (g.air() > 0.0F) {
				if (g.style() == CombatAnim.Kind.LEAP_FORWARD) {
					// Spread wide: the front ones thrown up and ahead to seize, the hind ones trailing behind.
					step += (front ? 1.0F : 0.2F) * g.air();
					swing += (front ? -0.2F : 0.35F) * g.air();
				} else if (g.style() == CombatAnim.Kind.LEAP_BACK) {
					step += (front ? 0.75F : -0.15F) * g.air();
				}
			}
			step += 0.35F * g.land();
			rights[i].yRot += swing;
			lefts[i].yRot -= swing;
			rights[i].zRot += step;
			lefts[i].zRot -= step;
		}
		head.xRot -= (float) Math.toRadians(lean(g)) * 0.5F;
	}

	/** A creeper at a run: short quick steps under the waddle and bob of {@link #applyBody}. */
	public static void creeperLegs(ModelPart head, ModelPart rightHind, ModelPart leftHind, ModelPart rightFront, ModelPart leftFront,
		float walkPos, @Nullable Gait g) {
		if (g == null) {
			return;
		}
		float going = g.run() * g.stride();
		if (going > 0.0F) {
			float quick = walkPos * 0.6662F * CREEPER_QUICK;
			float c = Mth.cos(quick) * 1.1F;
			rightHind.xRot = Mth.lerp(going, rightHind.xRot, c);
			leftHind.xRot = Mth.lerp(going, leftHind.xRot, -c);
			rightFront.xRot = Mth.lerp(going, rightFront.xRot, -c);
			leftFront.xRot = Mth.lerp(going, leftFront.xRot, c);
		}
		if (g.leaping(CombatAnim.Kind.LEAP_BACK)) {
			// Its feet up in front of it as it springs back.
			float a = g.air();
			rightFront.xRot = Mth.lerp(a, rightFront.xRot, -0.7F);
			leftFront.xRot = Mth.lerp(a, leftFront.xRot, -0.7F);
			rightHind.xRot = Mth.lerp(a, rightHind.xRot, -0.35F);
			leftHind.xRot = Mth.lerp(a, leftHind.xRot, -0.35F);
		}
		head.xRot -= (float) Math.toRadians(lean(g)) * 0.5F;
	}

	/** What a GeckoLib monster's walk is sped up by (see dev.forja.entity.GeoGait): how far into a run it is. */
	public static float geoRun(@Nullable Gait g) {
		return g == null ? 0.0F : g.run() * g.stride();
	}

	/** The render state's gait, for the mixins. */
	public static @Nullable Gait of(EntityRenderState state) {
		return state.getData(KEY);
	}
}
