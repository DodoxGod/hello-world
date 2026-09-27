package dev.forja.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.client.WeaponMotions.Blow;
import dev.forja.client.WeaponMotions.Hand;
import dev.forja.client.WeaponMotions.Key;
import dev.forja.client.WeaponMotions.Motion;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The poses a fight puts a body in, worked out from {@link CombatAnims} and the entity's own state:
 *
 * <ul>
 *   <li>a <b>flinch</b> back from every blow taken;</li>
 *   <li>a mob <b>rearing back</b> while it winds up, and <b>lunging</b> into the blow when it comes;</li>
 *   <li>a zombie <b>crouching</b> before it leaps;</li>
 *   <li>a <b>stagger</b>: swaying on its feet while its balance is gone;</li>
 *   <li>a <b>lean into a dodge</b>, a <b>jolt back</b> on a parry, a <b>stumble</b> when a guard breaks;</li>
 *   <li>and a blow of each weapon's own (see {@link WeaponMotions}): the greatsword swept flat with the
 *       body turning, the hammer from far behind the head down to the ground, the fists in turn...,
 *       which a mob also holds wound up while it warns, so the pose says what is coming.</li>
 * </ul>
 *
 * Everything is a lean of the whole body about its feet plus, for humanoids, the arm; no model is
 * replaced, so it works on any mob, vanilla or not.
 */
public final class CombatPoses {
	/**
	 * What the renderer needs from this frame's pose.
	 *
	 * @param windup   how far a mob's weapon is drawn into its wind-up while it warns (0 to 1), -1 when not
	 * @param released the swing under way is the blow a warning wound up, so it goes straight into the strike
	 * @param swings   how many swings in a row, counting the one under way (see {@link CombatAnims#swings})
	 * @param mining   the local player breaking a block: the swing is left to vanilla
	 */
	public record Pose(float leanX, float leanZ, float squash, float shieldKick, float charge, boolean comboFinish,
		float windup, boolean released, int swings, boolean mining) {
		public static final Pose NONE = new Pose(0.0F, 0.0F, 1.0F, 0.0F, -1.0F, false, -1.0F, false, 0, false);

		boolean still() {
			return this == NONE;
		}
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "forja:combat_pose");

	private static final float FLINCH_DEGREES = 9.0F;
	private static final float REAR_DEGREES = 11.0F;
	private static final float STRIKE_DEGREES = 13.0F;
	private static final float LUNGE_CROUCH = 0.16F;
	private static final float STAGGER_DEGREES = 8.0F;
	private static final float DODGE_DEGREES = 17.0F;
	private static final float PARRY_DEGREES = 6.0F;
	private static final float GUARD_BREAK_DEGREES = 12.0F;
	/** How long the lunge into a blow lasts once the windup is over, in ticks. */
	private static final float STRIKE_TICKS = 4.0F;
	/** The part of a warning it takes a mob to draw its weapon all the way back; it holds it there after. */
	private static final float WINDUP_DRAWN = 0.55F;
	/** How long a mob takes to lower a weapon it wound up and never swung (a feint), in ticks. */
	private static final int SETTLE_TICKS = 6;
	/** How long after the end of its warning a mob's swing still counts as the blow it warned of, in ticks. */
	private static final double RELEASE_SLACK = 6.0;

	private CombatPoses() {
	}

	/** Read everything off the entity while it is still at hand; the renderer only gets the state. */
	public static Pose compute(LivingEntity entity, LivingEntityRenderState state, float partialTick) {
		double now = CombatAnims.now(partialTick);
		double yaw = Math.toRadians(state.bodyRot);
		float forwardX = (float) -Math.sin(yaw);
		float forwardZ = (float) Math.cos(yaw);
		float leanX = 0.0F;
		float leanZ = 0.0F;
		float squash = 1.0F;
		float shieldKick = 0.0F;

		// The flinch: back from the blow, fast, then settling over the rest of the hurt time.
		if (entity.hurtTime > 0 && entity.hurtDuration > 0 && entity.deathTime <= 0) {
			float left = Mth.clamp((entity.hurtTime - partialTick) / entity.hurtDuration, 0.0F, 1.0F);
			float amount = Mth.sin((float) Math.PI * (float) Math.sqrt(1.0F - left)) * FLINCH_DEGREES;
			leanX -= forwardX * amount;
			leanZ -= forwardZ * amount;
		}

		float drawn = -1.0F;
		boolean released = false;
		CombatAnims.State anim = CombatAnims.get(entity.getId());
		if (anim != null) {
			// Winding up: rearing back, the head of the blow drawn in...
			float windup = CombatAnims.progress(anim.telegraphAt, anim.telegraphTicks, now);
			if (windup >= 0.0F) {
				float rear = Ease.outCubic(Math.min(1.0F, windup * 1.4F)) * REAR_DEGREES;
				leanX -= forwardX * rear;
				leanZ -= forwardZ * rear;
				// ...and the weapon with it, drawn back to where its own blow starts: a hammer overhead, a
				// spear back past the hip. Always a share of the warning the server announced, however long.
				drawn = Ease.outCubic(Math.min(1.0F, windup / WINDUP_DRAWN));
			}
			if (anim.telegraphAt != CombatAnims.State.NEVER) {
				double end = anim.telegraphAt + anim.telegraphTicks;
				if (entity.swinging) {
					// The blow it wound up (and not some later one): it starts from the top of the wind-up. Both
					// packets are late by about the same, so the swing starts about when the warning ends; the
					// slack covers a tick or two between them, and a counter's shorter windup.
					double started = now - partialTick - entity.swingTime;
					released = started >= anim.telegraphAt - 1.0 && started <= end + RELEASE_SLACK;
				} else if (windup < 0.0F) {
					// Wound up and nothing came (a feint, or the target slipped away): the weapon goes back down.
					float settle = CombatAnims.progress(end, SETTLE_TICKS, now);
					if (settle >= 0.0F) {
						drawn = 1.0F - Ease.inOutSine(settle);
					}
				}
			}
			// ...then thrown forward into it.
			float strike = CombatAnims.progress(anim.telegraphAt + anim.telegraphTicks, (int) STRIKE_TICKS, now);
			if (strike >= 0.0F) {
				float into = Mth.sin(strike * (float) Math.PI) * STRIKE_DEGREES
					- (1.0F - Ease.outCubic(Math.min(1.0F, strike * 3.0F))) * REAR_DEGREES;
				leanX += forwardX * into;
				leanZ += forwardZ * into;
			}

			// Crouching to leap: down on its haunches and leaning in.
			float lunge = CombatAnims.progress(anim.lungeAt, anim.lungeTicks, now);
			if (lunge >= 0.0F) {
				float crouch = Ease.outCubic(Math.min(1.0F, lunge * 1.6F));
				squash -= crouch * LUNGE_CROUCH;
				leanX += forwardX * crouch * 14.0F;
				leanZ += forwardZ * crouch * 14.0F;
			}

			// Staggered: swaying side to side and back, less and less as it gets its feet under it.
			float stagger = CombatAnims.progress(anim.staggerAt, anim.staggerTicks, now);
			if (stagger >= 0.0F) {
				float left = 1.0F - stagger;
				float strength = (float) Math.sqrt(left) * Ease.outCubic(Math.min(1.0F, stagger * 8.0F));
				float sway = Mth.sin((float) (now * 0.55)) * STAGGER_DEGREES * strength;
				float back = STAGGER_DEGREES * 0.7F * strength;
				leanX += forwardZ * sway - forwardX * back;
				leanZ += -forwardX * sway - forwardZ * back;
				squash -= 0.04F * strength;
			}

			// A dodge: the body leans the way it is going.
			float dodge = CombatAnims.progress(anim.dodgeAt, anim.dodgeTicks, now);
			if (dodge >= 0.0F) {
				float lean = Mth.sin(dodge * (float) Math.PI) * DODGE_DEGREES;
				leanX += anim.dodgeX * lean;
				leanZ += anim.dodgeZ * lean;
				squash -= Mth.sin(dodge * (float) Math.PI) * 0.06F;
			}

			// A parry jolts the defender back a little and throws the shield out.
			float parry = CombatAnims.progress(anim.parryAt, 8, now);
			if (parry >= 0.0F) {
				float jolt = Mth.sin(parry * (float) Math.PI) * PARRY_DEGREES * (anim.parryPerfect ? 1.4F : 1.0F);
				leanX -= forwardX * jolt;
				leanZ -= forwardZ * jolt;
				shieldKick = Mth.sin(Math.min(1.0F, parry * 1.6F) * (float) Math.PI) * (anim.parryPerfect ? 1.0F : 0.7F);
			}

			// A broken guard: knocked back onto the heels.
			float broken = CombatAnims.progress(anim.guardBreakAt, 12, now);
			if (broken >= 0.0F) {
				float stumble = Mth.sin(broken * (float) Math.PI) * GUARD_BREAK_DEGREES;
				leanX -= forwardX * stumble;
				leanZ -= forwardZ * stumble;
			}
		}

		float charge = CombatAnims.charge(entity.getId(), partialTick);
		boolean comboFinish = CombatAnims.comboFinishing(entity.getId(), partialTick);
		int swings = entity.swinging || entity.attackAnim > 0.0F ? CombatAnims.swings(entity) : 0;
		boolean mining = mining(entity);
		if (leanX == 0.0F && leanZ == 0.0F && squash == 1.0F && shieldKick == 0.0F && charge < 0.0F && !comboFinish
			&& drawn < 0.0F && !released && swings == 0 && !mining) {
			return Pose.NONE;
		}
		return new Pose(leanX, leanZ, squash, shieldKick, charge, comboFinish, drawn, released, swings, mining);
	}

	/**
	 * Whether this is the local player breaking a block. Digging is a swing over and over, and it keeps
	 * vanilla's: a hammer slammed down six times a second at a wall of stone is not a fight.
	 */
	private static boolean mining(LivingEntity entity) {
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		return entity == minecraft.player && minecraft.gameMode != null && minecraft.gameMode.isDestroying();
	}

	/**
	 * Tips the whole body about its feet. Called before the renderer turns it to face its way, so the
	 * lean is in world directions: a lean of (x, z) moves the top of the head towards +x, +z.
	 */
	public static void applyLean(Pose pose, PoseStack poseStack) {
		if (pose == null || pose.still()) {
			return;
		}
		float degrees = (float) Math.sqrt(pose.leanX() * pose.leanX() + pose.leanZ() * pose.leanZ());
		if (degrees > 0.01F) {
			// Turning about the axis (z, 0, -x) by a positive angle carries straight up towards (x, 0, z).
			float axisX = pose.leanZ() / degrees;
			float axisZ = -pose.leanX() / degrees;
			poseStack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(degrees), axisX, 0.0F, axisZ));
		}
		if (pose.squash() != 1.0F) {
			float widen = 1.0F + (1.0F - pose.squash()) * 0.5F;
			poseStack.scale(widen, pose.squash(), widen);
		}
	}

	// --- Third person swings ------------------------------------------------------------------------

	/** A flail whirled while its bearer warns or charges: a turn every seven ticks. */
	private static final float WHIRL_PER_TICK = Mth.TWO_PI / 7.0F;
	/** A charged blow draws the body back further than the keys of a plain one. */
	private static final float CHARGE_BOOST = 1.25F;
	/** A blow released from a warning spends this share of its swing striking, and the rest coming back. */
	private static final float RELEASE_STRIKE = 0.3F;
	/** Where the other fist takes a two-handed grip, in the leading arm's own space (pixels): just behind the first. */
	private static final Vector3f SECOND_GRIP = new Vector3f(0.0F, 9.5F, 2.5F);

	/** Which two moments of a blow a swing is between (0 rest, 1 wind-up, 2 through, 3 strike), how far, and how much the other hand is in it. */
	private record Span(int from, int to, float t, float hold) {
	}

	/**
	 * Where a swing is at one moment.
	 *
	 * @param from       the key it comes from, null for the arm's own rest
	 * @param to         the key it goes to, null for the rest
	 * @param hold       how far the other hand has joined in (on the grip, up to guard), 0 to 1
	 * @param otherLeads the blow is thrown by the other arm (the fists taking turns)
	 * @param otherFree  the other hand is empty, so it can take the grip or throw a punch; with a shield or a
	 *                   torch in it, it keeps to itself
	 * @param whirl      how far the weapon has spun about the arm, in radians
	 * @param boost      how much further than the keys the body goes (a charge)
	 * @param tremble    the shake of a full charge, in radians
	 */
	private record Frame(Motion motion, @Nullable Key from, @Nullable Key to, float t, float hold, boolean otherLeads,
		boolean otherFree, float whirl, float boost, float tremble) {
		float reach() {
			return Mth.lerp(this.t, this.from == null ? 0.0F : this.from.reach(), this.to == null ? 0.0F : this.to.reach()) * this.boost;
		}

		float bodyYaw() {
			return Mth.lerp(this.t, this.from == null ? 0.0F : this.from.bodyYaw(), this.to == null ? 0.0F : this.to.bodyYaw())
				* this.boost * Mth.DEG_TO_RAD;
		}

		float bodyPitch() {
			return Mth.lerp(this.t, this.from == null ? 0.0F : this.from.bodyPitch(), this.to == null ? 0.0F : this.to.bodyPitch())
				* this.boost * Mth.DEG_TO_RAD;
		}
	}

	/**
	 * The part of a blow a swing is in. Everything is a share of the swing (0 to 1), never ticks, so a
	 * heavy weapon's longer swing plays the same blow more slowly.
	 *
	 * @param through  the blow passes through a middle key on its way to the strike
	 * @param released the blow was wound up during a warning: it starts at the top of the wind-up
	 */
	private static Span span(Motion motion, boolean through, float attack, boolean released) {
		float windEnd = motion.windEnd();
		float strikeEnd = motion.strikeEnd();
		if (released) {
			attack = attack < RELEASE_STRIKE
				? Mth.lerp(attack / RELEASE_STRIKE, windEnd, strikeEnd)
				: Mth.lerp((attack - RELEASE_STRIKE) / (1.0F - RELEASE_STRIKE), strikeEnd, 1.0F);
		}
		if (attack < windEnd) {
			float t = Ease.outCubic(attack / windEnd);
			return new Span(0, 1, t, t);
		}
		if (attack < strikeEnd) {
			float t = Ease.outQuart((attack - windEnd) / (strikeEnd - windEnd));
			if (through) {
				return t < 0.5F ? new Span(1, 2, t * 2.0F, 1.0F) : new Span(2, 3, t * 2.0F - 1.0F, 1.0F);
			}
			return new Span(1, 3, t, 1.0F);
		}
		if (attack < motion.holdEnd()) {
			return new Span(3, 3, 1.0F, 1.0F);
		}
		float t = Ease.inOutSine((attack - motion.holdEnd()) / (1.0F - motion.holdEnd()));
		return new Span(3, 0, t, 1.0F - t);
	}

	private static @Nullable Key key(Blow blow, int which) {
		return switch (which) {
			case 1 -> blow.wind();
			case 2 -> blow.through();
			case 3 -> blow.strike();
			default -> null;
		};
	}

	/**
	 * The moment of a weapon's blow the state is at, or null to leave the arms to vanilla (no weapon of
	 * note, a spear's stab, digging, or nothing happening).
	 */
	private static @Nullable Frame frame(HumanoidRenderState state) {
		Pose pose = state.getData(KEY);
		if (pose != null && pose.mining()) {
			return null;
		}
		Motion motion = WeaponMotions.of(state.getUseItemStackForArm(state.attackArm));
		if (motion == null) {
			return null;
		}
		float attack = state.attackTime;
		boolean otherFree = state.getUseItemStackForArm(state.attackArm.getOpposite()).isEmpty();
		if (attack > 0.0F) {
			if (state.swingAnimationType != SwingAnimationType.WHACK) {
				// A spear's stab stays vanilla's, which goes with its kinetic charge.
				return null;
			}
			boolean finisher = pose != null && pose.comboFinish();
			boolean fists = motion.hands() == WeaponMotions.Hands.FISTS;
			boolean second = !finisher && pose != null && motion.alternates(pose.swings()) && (otherFree || !fists);
			Blow blow = motion.blow(finisher, second);
			Span span = span(motion, blow.through() != null, attack, pose != null && pose.released());
			float whirl = span.from() == 0 && span.to() == 1 ? motion.whirl() * Mth.TWO_PI * span.t() : 0.0F;
			// When the other fist throws the blow, the gauntlet's hand is the one left over, and it is never empty.
			return new Frame(motion, key(blow, span.from()), key(blow, span.to()), span.t(), span.hold(),
				second && fists, otherFree || second && fists, whirl, 1.0F, 0.0F);
		}
		if (pose == null) {
			return null;
		}
		Blow blow = motion.plain();
		float spin = motion.whirl() > 0.0F ? state.ageInTicks * WHIRL_PER_TICK : 0.0F;
		if (pose.windup() >= 0.0F) {
			// A mob warning of a blow holds its weapon where that blow starts from, so the pose says what is coming.
			return new Frame(motion, null, blow.wind(), pose.windup(), pose.windup(), false, otherFree, spin, 1.0F, 0.0F);
		}
		if (pose.charge() >= 0.0F) {
			// Charging: drawn back further than a plain blow and held, trembling once it is full.
			float t = Ease.outCubic(pose.charge());
			float tremble = pose.charge() >= 1.0F ? Mth.sin(state.ageInTicks * 2.3F) * 0.05F : 0.0F;
			return new Frame(motion, null, blow.wind(), t, t, false, otherFree, spin, CHARGE_BOOST, tremble);
		}
		return null;
	}

	/**
	 * Poses a humanoid swinging a weapon with a blow of its own, warning with it, or charging it.
	 *
	 * @return false to leave the swing to vanilla
	 */
	public static boolean thirdPersonSwing(HumanoidModel<?> model, HumanoidRenderState state) {
		Frame frame = frame(state);
		if (frame == null) {
			return false;
		}
		boolean leftLeads = (state.attackArm == HumanoidArm.LEFT) != frame.otherLeads();
		float bodyYaw = frame.bodyYaw() * (leftLeads ? -1.0F : 1.0F);
		// The body turns and the shoulders go with it, as vanilla does for its own swing.
		model.body.yRot = bodyYaw;
		model.body.xRot += frame.bodyPitch();
		float ageScale = state.ageScale;
		model.rightArm.z = Mth.sin(bodyYaw) * 5.0F * ageScale;
		model.rightArm.x = -Mth.cos(bodyYaw) * 5.0F * ageScale;
		model.leftArm.z = -Mth.sin(bodyYaw) * 5.0F * ageScale;
		model.leftArm.x = Mth.cos(bodyYaw) * 5.0F * ageScale;
		// The attacking shoulder goes forward into the blow (or back for the wind-up), the way the chest faces.
		ModelPart lead = leftLeads ? model.leftArm : model.rightArm;
		lead.x -= Mth.sin(bodyYaw) * frame.reach() * ageScale;
		lead.z -= Mth.cos(bodyYaw) * frame.reach() * ageScale;
		applyArms(model.rightArm, model.leftArm, frame, leftLeads, bodyYaw);
		return true;
	}

	/**
	 * Zombies, zombie villagers, zombified piglins and drowned pose both arms afresh after the swing has
	 * been set up, which throws the weapon's blow away. This puts it back, from their own arms-out rest
	 * (without vanilla's swing folded into it); the body and the shoulders were set by
	 * {@link #thirdPersonSwing} and are left alone.
	 */
	public static void reapplyAfterZombieArms(ModelPart leftArm, ModelPart rightArm, boolean aggressive, UndeadRenderState state) {
		Frame frame = frame(state);
		if (frame == null) {
			return;
		}
		boolean raised = !state.isBaby || state.getMainHandItemStack().isEmpty();
		float drop = raised ? -Mth.PI / (aggressive ? 1.5F : 2.25F) : 0.0F;
		rightArm.setRotation(drop, raised ? -0.1F : 0.1F, 0.0F);
		leftArm.setRotation(drop, raised ? 0.1F : -0.1F, 0.0F);
		reapply(leftArm, rightArm, state, frame);
	}

	/** Skeletons with a blade in hand do the same as zombies once they are angry: both arms out in front. */
	public static void reapplyAfterSkeletonArms(ModelPart leftArm, ModelPart rightArm, SkeletonRenderState state) {
		if (!state.isAggressive || state.isHoldingBow) {
			return;
		}
		Frame frame = frame(state);
		if (frame == null) {
			return;
		}
		rightArm.setRotation(-Mth.HALF_PI, -0.1F, 0.0F);
		leftArm.setRotation(-Mth.HALF_PI, 0.1F, 0.0F);
		reapply(leftArm, rightArm, state, frame);
	}

	/** Piglins hold a blade up high between blows, over whatever the warning had drawn back. */
	public static void reapplyAfterPiglinArms(ModelPart leftArm, ModelPart rightArm, HumanoidRenderState state) {
		if (state.attackTime > 0.0F) {
			return;
		}
		Frame frame = frame(state);
		if (frame != null) {
			reapply(leftArm, rightArm, state, frame);
		}
	}

	private static void reapply(ModelPart leftArm, ModelPart rightArm, HumanoidRenderState state, Frame frame) {
		boolean leftLeads = (state.attackArm == HumanoidArm.LEFT) != frame.otherLeads();
		applyArms(rightArm, leftArm, frame, leftLeads, frame.bodyYaw() * (leftLeads ? -1.0F : 1.0F));
	}

	/**
	 * Turns both arms for the frame. The leading arm goes from its rest through the keys; the other one
	 * turns with the body, takes the grip behind the first fist on a two-handed weapon, or comes up to
	 * guard when the weapon is the fists (unless it is holding something of its own: a zombie's shield
	 * stays on its arm).
	 */
	private static void applyArms(ModelPart rightArm, ModelPart leftArm, Frame frame, boolean leftLeads, float bodyYaw) {
		ModelPart lead = leftLeads ? leftArm : rightArm;
		ModelPart other = leftLeads ? rightArm : leftArm;
		Quaternionf body = new Quaternionf().rotationY(bodyYaw);
		Quaternionf leadRest = rotationOf(lead);
		Quaternionf otherRest = rotationOf(other);

		Quaternionf from = frame.from() == null ? leadRest : frame.from().orientation(leftLeads);
		Quaternionf to = frame.to() == null ? leadRest : frame.to().orientation(leftLeads);
		Quaternionf leadTurn = new Quaternionf(from).slerp(to, frame.t());
		if (frame.whirl() != 0.0F) {
			// About the arm's own length: the weapon goes round over the head.
			leadTurn.rotateY(leftLeads ? -frame.whirl() : frame.whirl());
		}
		leadTurn.premul(body);
		setRotation(lead, leadTurn);
		lead.xRot += frame.tremble();

		Quaternionf otherTurn = new Quaternionf(body).mul(otherRest);
		switch (frame.otherFree() ? frame.motion().hands() : WeaponMotions.Hands.ONE) {
			case TWO -> {
				// Wherever the blow has carried the first fist, the second takes the grip just behind it.
				Vector3f grip = leadTurn.transform(new Vector3f(SECOND_GRIP)).add(lead.x, lead.y, lead.z);
				Vector3f toGrip = grip.sub(other.x, other.y, other.z).normalize();
				otherTurn.slerp(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, toGrip.x, toGrip.y, toGrip.z), frame.hold());
			}
			case FISTS -> {
				Quaternionf guard = WeaponMotions.orient(WeaponMotions.GUARD.dir(!leftLeads), WeaponMotions.GUARD_TIP.dir(!leftLeads));
				otherTurn.slerp(guard.premul(body), frame.hold());
			}
			default -> {
			}
		}
		setRotation(other, otherTurn);
	}

	private static Quaternionf rotationOf(ModelPart part) {
		return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
	}

	private static void setRotation(ModelPart part, Quaternionf turn) {
		Vector3f angles = turn.getEulerAnglesZYX(new Vector3f());
		part.setRotation(angles.x, angles.y, angles.z);
	}

	/**
	 * Where a weapon's swing is fully wound up and where its blow lands, as shares of the swing, then 1 if
	 * every second blow in a row is a different one and 0 if not; null for anything that swings like
	 * vanilla. The client test films the poses at these moments.
	 */
	public static float @Nullable [] keyMoments(ItemStack weapon) {
		Motion motion = WeaponMotions.of(weapon);
		return motion == null ? null : new float[] {motion.windEnd(), motion.strikeEnd(), motion.alternate() != null ? 1.0F : 0.0F};
	}

	/** A shield thrown out to meet a blow: the shield arm snaps forward and up for a moment. */
	public static void thirdPersonShieldKick(HumanoidModel<?> model, HumanoidRenderState state, float kick) {
		if (kick <= 0.0F || !state.isUsingItem) {
			return;
		}
		HumanoidArm shieldArm = state.useItemHand == net.minecraft.world.InteractionHand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
		ModelPart arm = shieldArm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		arm.xRot -= 0.45F * kick;
		arm.z -= 2.5F * kick;
	}

	// --- First person swings ------------------------------------------------------------------------

	private static Hand hand(Blow blow, int which) {
		return switch (which) {
			case 1 -> blow.handWind();
			case 2, 3 -> blow.handStrike();
			default -> Hand.REST;
		};
	}

	/**
	 * Moves the held weapon through its own blow in first person: the same blow as in third person, from
	 * the same table, by the same share of the swing.
	 *
	 * @param invert 1 for the right hand, -1 for the left
	 * @return false to leave the swing to vanilla
	 */
	public static boolean firstPersonSwing(PoseStack poseStack, ItemStack weapon, float attack, int invert) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Motion motion = WeaponMotions.of(weapon);
		if (player == null || motion == null || mining(player)) {
			return false;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean finisher = CombatAnims.comboFinishing(player.getId(), partial);
		boolean second = !finisher && attack > 0.0F && motion.alternates(CombatAnims.swings(player))
			&& (motion.hands() != WeaponMotions.Hands.FISTS || player.getOffhandItem().isEmpty());
		Blow blow = motion.blow(finisher, second);
		Hand from;
		Hand to;
		float t;
		float whirl = 0.0F;
		if (attack <= 0.0F) {
			float charge = CombatClient.localCharge(partial);
			if (charge < 0.0F) {
				return false;
			}
			// Charging: the weapon drawn back and held, shaking once the charge is full.
			from = Hand.REST;
			to = blow.handWind();
			t = Ease.outCubic(charge) * 1.15F;
			if (charge >= 1.0F) {
				float shake = Mth.sin((player.tickCount + partial) * 2.6F) * 0.012F;
				poseStack.translate(shake, shake * 0.5F, 0.0F);
			}
			if (motion.whirl() > 0.0F) {
				whirl = (player.tickCount + partial) * WHIRL_PER_TICK;
			}
		} else {
			Span span = span(motion, false, attack, false);
			from = hand(blow, span.from());
			to = hand(blow, span.to());
			t = span.t();
			if (span.from() == 0 && span.to() == 1) {
				whirl = motion.whirl() * Mth.TWO_PI * t;
			}
		}
		poseStack.translate(
			invert * Mth.lerp(t, from.x(), to.x()),
			Mth.lerp(t, from.y(), to.y()),
			Mth.lerp(t, from.z(), to.z())
		);
		poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(invert * Mth.lerp(t, from.rotY(), to.rotY())));
		poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(invert * Mth.lerp(t, from.rotZ(), to.rotZ())));
		poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(Mth.lerp(t, from.rotX(), to.rotX())));
		if (whirl != 0.0F) {
			poseStack.mulPose(com.mojang.math.Axis.YP.rotation(invert * whirl));
		}
		return true;
	}

	/**
	 * The fists' second blow in a row is thrown with the other fist, which first person never draws while
	 * that hand is empty. When it is being thrown this moves the pose to where that fist is (the same
	 * straight punch as the gauntlet's, mirrored, coming up from below the screen and going back down)
	 * and returns true, and the caller draws the bare arm there, at its rest.
	 *
	 * @param invert 1 for an arm on the right, -1 on the left
	 */
	public static boolean firstPersonOtherFist(AbstractClientPlayer player, float partialTick, PoseStack poseStack, int invert) {
		if (player.swingingArm == InteractionHand.OFF_HAND || mining(player)) {
			return false;
		}
		Motion motion = WeaponMotions.of(player.getMainHandItem());
		if (motion == null || motion.hands() != WeaponMotions.Hands.FISTS) {
			return false;
		}
		float attack = player.getAttackAnim(partialTick);
		if (attack <= 0.0F || CombatAnims.comboFinishing(player.getId(), partialTick) || !motion.alternates(CombatAnims.swings(player))) {
			return false;
		}
		Span span = span(motion, false, attack, false);
		Hand from = hand(motion.plain(), span.from());
		Hand to = hand(motion.plain(), span.to());
		float t = span.t();
		poseStack.translate(
			invert * Mth.lerp(t, from.x(), to.x()),
			Mth.lerp(t, from.y(), to.y()) - 0.6F * (1.0F - span.hold()),
			Mth.lerp(t, from.z(), to.z())
		);
		return true;
	}

	/**
	 * The first-person side of the local player's own moments: the shield punched forward on a parry,
	 * dropped when the guard breaks, and both hands trailing behind a dodge.
	 */
	public static void firstPersonExtras(PoseStack poseStack, net.minecraft.world.entity.player.Player player,
		net.minecraft.world.item.ItemStack stack, int invert, float partialTick) {
		CombatAnims.State anim = CombatAnims.get(player.getId());
		if (anim == null) {
			return;
		}
		double now = CombatAnims.now(partialTick);
		boolean shieldUp = player.isUsingItem() && player.getUseItem() == stack
			&& stack.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
		if (shieldUp) {
			float parry = CombatAnims.progress(anim.parryAt, 7, now);
			if (parry >= 0.0F) {
				float kick = Mth.sin(parry * (float) Math.PI) * (anim.parryPerfect ? 1.0F : 0.7F);
				poseStack.translate(invert * -0.06F * kick, 0.06F * kick, -0.22F * kick);
				poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-10.0F * kick));
			}
		}
		float broken = CombatAnims.progress(anim.guardBreakAt, 12, now);
		if (broken >= 0.0F && stack.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) {
			float drop = Mth.sin(broken * (float) Math.PI);
			poseStack.translate(0.0F, -0.35F * drop, 0.1F * drop);
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(invert * 25.0F * drop));
		}
		float dodge = CombatAnims.progress(anim.dodgeAt, anim.dodgeTicks, now);
		if (dodge >= 0.0F) {
			// The hands lag behind the body: to the left when dodging right, up when stepping back.
			double yaw = Math.toRadians(player.getYRot(partialTick));
			float rightX = (float) -Math.cos(yaw);
			float rightZ = (float) -Math.sin(yaw);
			float forwardX = (float) -Math.sin(yaw);
			float forwardZ = (float) Math.cos(yaw);
			float side = anim.dodgeX * rightX + anim.dodgeZ * rightZ;
			float ahead = anim.dodgeX * forwardX + anim.dodgeZ * forwardZ;
			float lag = Mth.sin(dodge * (float) Math.PI);
			poseStack.translate(-side * 0.18F * lag, -0.05F * lag, ahead * 0.12F * lag);
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * 10.0F * lag));
		}
	}
}
