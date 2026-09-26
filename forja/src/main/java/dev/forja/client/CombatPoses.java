package dev.forja.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.combat.SwingStyle;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.SwingAnimationType;
import org.joml.Quaternionf;

/**
 * The poses a fight puts a body in, worked out from {@link CombatAnims} and the entity's own state:
 *
 * <ul>
 *   <li>a <b>flinch</b> back from every blow taken;</li>
 *   <li>a mob <b>rearing back</b> while it winds up, and <b>lunging</b> into the blow when it comes;</li>
 *   <li>a zombie <b>crouching</b> before it leaps;</li>
 *   <li>a <b>stagger</b>: swaying on its feet while its balance is gone;</li>
 *   <li>a <b>lean into a dodge</b>, a <b>jolt back</b> on a parry, a <b>stumble</b> when a guard breaks;</li>
 *   <li>and a swing that fits the weapon: a sweep, a chop from overhead, a thrust.</li>
 * </ul>
 *
 * Everything is a lean of the whole body about its feet plus, for humanoids, the arm; no model is
 * replaced, so it works on any mob, vanilla or not.
 */
public final class CombatPoses {
	/** What the renderer needs from this frame's pose. */
	public record Pose(float leanX, float leanZ, float squash, float shieldKick, float charge, boolean comboFinish) {
		public static final Pose NONE = new Pose(0.0F, 0.0F, 1.0F, 0.0F, -1.0F, false);

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

		CombatAnims.State anim = CombatAnims.get(entity.getId());
		if (anim != null) {
			// Winding up: rearing back, the head of the blow drawn in...
			float windup = CombatAnims.progress(anim.telegraphAt, anim.telegraphTicks, now);
			if (windup >= 0.0F) {
				float rear = Ease.outCubic(Math.min(1.0F, windup * 1.4F)) * REAR_DEGREES;
				leanX -= forwardX * rear;
				leanZ -= forwardZ * rear;
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
		if (leanX == 0.0F && leanZ == 0.0F && squash == 1.0F && shieldKick == 0.0F && charge < 0.0F && !comboFinish) {
			return Pose.NONE;
		}
		return new Pose(leanX, leanZ, squash, shieldKick, charge, comboFinish);
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

	/** One moment of a swing: the attacking arm and the body, as offsets from where they rest. */
	private record Key(float armX, float armY, float armZ, float armForward, float bodyY, float bodyX) {
		static final Key REST = new Key(Float.NaN, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
	}

	/**
	 * Raised to the side, then swept across. Arm rotations are for the right arm; the left mirrors them.
	 * {@code armX} is absolute (the arm pointing forward at -PI/2), the rest are added to where the arm was.
	 */
	private static final Key SLASH_WIND = new Key(-1.45F, 0.95F, -0.35F, 0.0F, 0.35F, 0.0F);
	private static final Key SLASH_STRIKE = new Key(-1.35F, -0.85F, 0.25F, -1.0F, -0.45F, 0.08F);
	private static final Key CHOP_WIND = new Key(-2.95F, 0.25F, 0.0F, 1.0F, 0.2F, -0.08F);
	private static final Key CHOP_STRIKE = new Key(-0.55F, -0.2F, 0.0F, -1.5F, -0.2F, 0.22F);
	private static final Key THRUST_WIND = new Key(-1.2F, 0.3F, 0.0F, 2.5F, 0.3F, -0.04F);
	private static final Key THRUST_STRIKE = new Key(-1.62F, -0.15F, 0.0F, -4.0F, -0.3F, 0.12F);

	/** The third blow of a combo: the sweep comes back the other way, the chop and the thrust go further. */
	private static final Key SLASH_BACK_WIND = mirror(SLASH_WIND);
	private static final Key SLASH_BACK_STRIKE = mirror(SLASH_STRIKE);
	private static final Key CHOP_HEAVY_WIND = amplify(CHOP_WIND, 1.25F);
	private static final Key CHOP_HEAVY_STRIKE = amplify(CHOP_STRIKE, 1.25F);
	private static final Key THRUST_HEAVY_WIND = amplify(THRUST_WIND, 1.3F);
	private static final Key THRUST_HEAVY_STRIKE = amplify(THRUST_STRIKE, 1.3F);

	private static Key mirror(Key key) {
		return new Key(key.armX(), -key.armY() * 1.1F, -key.armZ(), key.armForward(), -key.bodyY() * 1.15F, key.bodyX());
	}

	private static Key amplify(Key key, float by) {
		return new Key(key.armX(), key.armY() * by, key.armZ() * by, key.armForward() * by, key.bodyY() * by, key.bodyX() * by);
	}

	/** The wind-up and strike keys of a style: the plain ones, or the combo finisher's. Null for vanilla. */
	private static Key[] keys(SwingStyle style, boolean finisher) {
		return switch (style) {
			case SLASH -> finisher ? new Key[] {SLASH_BACK_WIND, SLASH_BACK_STRIKE} : new Key[] {SLASH_WIND, SLASH_STRIKE};
			case CHOP -> finisher ? new Key[] {CHOP_HEAVY_WIND, CHOP_HEAVY_STRIKE} : new Key[] {CHOP_WIND, CHOP_STRIKE};
			case THRUST -> finisher ? new Key[] {THRUST_HEAVY_WIND, THRUST_HEAVY_STRIKE} : new Key[] {THRUST_WIND, THRUST_STRIKE};
			default -> null;
		};
	}

	/** The end of the wind-up and the end of the strike, as parts of the whole swing. */
	private static final float WIND_END = 0.28F;
	private static final float STRIKE_END = 0.58F;

	/** Where a swing is at one moment: the finished offsets for the arm and the body. */
	private record Frame(float armX, float armY, float armZ, float forward, float bodyY, float bodyX) {
	}

	/**
	 * The moment of a weapon's swing the state is at, or null when the swing is vanilla's (no weapon of
	 * note, a spear's stab, or no swing at all).
	 *
	 * @param restX where the attacking arm points when it is not swinging, to leave from and come back to
	 */
	private static Frame frame(HumanoidRenderState state, float restX) {
		float attack = state.attackTime;
		if (state.swingAnimationType != SwingAnimationType.WHACK) {
			return null;
		}
		Pose pose = state.getData(KEY);
		Key[] keys = keys(SwingStyle.of(state.getUseItemStackForArm(state.attackArm)), pose != null && pose.comboFinish());
		if (keys == null) {
			return null;
		}
		Key wind = keys[0];
		Key strike = keys[1];
		if (attack <= 0.0F) {
			// Charging: drawn back and held, trembling once it is full.
			if (pose == null || pose.charge() < 0.0F) {
				return null;
			}
			float t = Ease.outCubic(pose.charge());
			float tremble = pose.charge() >= 1.0F ? Mth.sin(state.ageInTicks * 2.3F) * 0.05F : 0.0F;
			return new Frame(Mth.lerp(t, restX, wind.armX() * 1.08F) + tremble, wind.armY() * t, wind.armZ() * t,
				wind.armForward() * t * 1.2F, wind.bodyY() * t * 1.2F, wind.bodyX() * t);
		}
		if (attack < WIND_END) {
			float t = Ease.outCubic(attack / WIND_END);
			return new Frame(Mth.lerp(t, restX, wind.armX()), wind.armY() * t, wind.armZ() * t, wind.armForward() * t,
				wind.bodyY() * t, wind.bodyX() * t);
		}
		if (attack < STRIKE_END) {
			float t = Ease.outQuart((attack - WIND_END) / (STRIKE_END - WIND_END));
			return new Frame(Mth.lerp(t, wind.armX(), strike.armX()), Mth.lerp(t, wind.armY(), strike.armY()),
				Mth.lerp(t, wind.armZ(), strike.armZ()), Mth.lerp(t, wind.armForward(), strike.armForward()),
				Mth.lerp(t, wind.bodyY(), strike.bodyY()), Mth.lerp(t, wind.bodyX(), strike.bodyX()));
		}
		float t = Ease.inOutSine((attack - STRIKE_END) / (1.0F - STRIKE_END));
		return new Frame(Mth.lerp(t, strike.armX(), restX), strike.armY() * (1.0F - t), strike.armZ() * (1.0F - t),
			strike.armForward() * (1.0F - t), strike.bodyY() * (1.0F - t), strike.bodyX() * (1.0F - t));
	}

	/**
	 * Poses a humanoid swinging a weapon with a style of its own.
	 *
	 * @return false to leave the swing to vanilla
	 */
	public static boolean thirdPersonSwing(HumanoidModel<?> model, HumanoidRenderState state) {
		boolean right = state.attackArm == HumanoidArm.RIGHT;
		ModelPart arm = right ? model.rightArm : model.leftArm;
		Frame frame = frame(state, arm.xRot);
		if (frame == null) {
			return false;
		}
		float mirror = right ? 1.0F : -1.0F;
		// The body turns and the shoulders go with it, as vanilla does for its own swing.
		model.body.yRot = frame.bodyY() * mirror;
		model.body.xRot += frame.bodyX();
		float ageScale = state.ageScale;
		model.rightArm.z = Mth.sin(model.body.yRot) * 5.0F * ageScale;
		model.rightArm.x = -Mth.cos(model.body.yRot) * 5.0F * ageScale;
		model.leftArm.z = -Mth.sin(model.body.yRot) * 5.0F * ageScale;
		model.leftArm.x = Mth.cos(model.body.yRot) * 5.0F * ageScale;
		applyArms(model.rightArm, model.leftArm, state, frame);
		arm.z += frame.forward() * ageScale;
		return true;
	}

	/**
	 * Zombies, zombie villagers, zombified piglins and unarmed illagers pose both arms afresh after the
	 * swing has been set up, which throws the weapon's swing away. This puts the arms back; the body and
	 * the shoulders were set by {@link #thirdPersonSwing} and are left alone.
	 */
	public static void reapplyAfterZombieArms(ModelPart leftArm, ModelPart rightArm, HumanoidRenderState state) {
		ModelPart arm = state.attackArm == HumanoidArm.RIGHT ? rightArm : leftArm;
		Frame frame = frame(state, arm.xRot);
		if (frame != null) {
			applyArms(rightArm, leftArm, state, frame);
		}
	}

	private static void applyArms(ModelPart rightArm, ModelPart leftArm, HumanoidRenderState state, Frame frame) {
		boolean right = state.attackArm == HumanoidArm.RIGHT;
		float mirror = right ? 1.0F : -1.0F;
		ModelPart arm = right ? rightArm : leftArm;
		ModelPart other = right ? leftArm : rightArm;
		float bodyYaw = frame.bodyY() * mirror;
		other.yRot += bodyYaw;
		arm.xRot = frame.armX();
		arm.yRot += bodyYaw + frame.armY() * mirror;
		arm.zRot += frame.armZ() * mirror;
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

	/** One moment of a first-person swing: where the hand is and how it is turned, relative to rest. */
	private record HandKey(float x, float y, float z, float rotY, float rotZ, float rotX) {
	}

	private static final HandKey HAND_REST = new HandKey(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
	private static final HandKey HAND_SLASH_WIND = new HandKey(0.14F, 0.1F, 0.06F, 28.0F, -18.0F, -8.0F);
	private static final HandKey HAND_SLASH_STRIKE = new HandKey(-0.48F, -0.1F, -0.16F, -58.0F, 28.0F, -30.0F);
	private static final HandKey HAND_CHOP_WIND = new HandKey(0.04F, 0.26F, 0.1F, 6.0F, -6.0F, 38.0F);
	private static final HandKey HAND_CHOP_STRIKE = new HandKey(-0.12F, -0.3F, -0.2F, -10.0F, 12.0F, -72.0F);
	private static final HandKey HAND_THRUST_WIND = new HandKey(0.02F, 0.03F, 0.16F, 4.0F, 0.0F, 6.0F);
	private static final HandKey HAND_THRUST_STRIKE = new HandKey(-0.12F, 0.02F, -0.48F, -10.0F, 0.0F, -8.0F);

	private static final HandKey HAND_SLASH_BACK_WIND = mirror(HAND_SLASH_WIND);
	private static final HandKey HAND_SLASH_BACK_STRIKE = mirror(HAND_SLASH_STRIKE);
	private static final HandKey HAND_CHOP_HEAVY_WIND = amplify(HAND_CHOP_WIND, 1.25F);
	private static final HandKey HAND_CHOP_HEAVY_STRIKE = amplify(HAND_CHOP_STRIKE, 1.25F);
	private static final HandKey HAND_THRUST_HEAVY_WIND = amplify(HAND_THRUST_WIND, 1.3F);
	private static final HandKey HAND_THRUST_HEAVY_STRIKE = amplify(HAND_THRUST_STRIKE, 1.3F);

	private static HandKey mirror(HandKey key) {
		return new HandKey(-key.x() * 1.1F, key.y(), key.z(), -key.rotY() * 1.1F, -key.rotZ(), key.rotX());
	}

	private static HandKey amplify(HandKey key, float by) {
		return new HandKey(key.x() * by, key.y() * by, key.z() * by, key.rotY() * by, key.rotZ() * by, key.rotX() * by);
	}

	private static HandKey[] handKeys(SwingStyle style, boolean finisher) {
		return switch (style) {
			case SLASH -> finisher ? new HandKey[] {HAND_SLASH_BACK_WIND, HAND_SLASH_BACK_STRIKE} : new HandKey[] {HAND_SLASH_WIND, HAND_SLASH_STRIKE};
			case CHOP -> finisher ? new HandKey[] {HAND_CHOP_HEAVY_WIND, HAND_CHOP_HEAVY_STRIKE} : new HandKey[] {HAND_CHOP_WIND, HAND_CHOP_STRIKE};
			case THRUST -> finisher ? new HandKey[] {HAND_THRUST_HEAVY_WIND, HAND_THRUST_HEAVY_STRIKE} : new HandKey[] {HAND_THRUST_WIND, HAND_THRUST_STRIKE};
			default -> null;
		};
	}

	/**
	 * Moves the held weapon through a first-person swing that fits it.
	 *
	 * @param invert 1 for the right hand, -1 for the left
	 * @return false to leave the swing to vanilla
	 */
	public static boolean firstPersonSwing(PoseStack poseStack, net.minecraft.world.item.ItemStack weapon, float attack, int invert) {
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean finisher = minecraft.player != null && CombatAnims.comboFinishing(minecraft.player.getId(), partial);
		HandKey[] keys = handKeys(SwingStyle.of(weapon), finisher);
		if (keys == null) {
			return false;
		}
		HandKey wind = keys[0];
		HandKey strike = keys[1];
		HandKey from;
		HandKey to;
		float t;
		float charge = CombatClient.localCharge(partial);
		if (attack <= 0.0F && charge >= 0.0F) {
			// Charging: the weapon drawn back and held, shaking once the charge is full.
			from = HAND_REST;
			to = wind;
			t = Ease.outCubic(charge) * 1.15F;
			if (charge >= 1.0F && minecraft.player != null) {
				float shake = Mth.sin((minecraft.player.tickCount + partial) * 2.6F) * 0.012F;
				poseStack.translate(shake, shake * 0.5F, 0.0F);
			}
		} else if (attack < WIND_END) {
			from = HAND_REST;
			to = wind;
			t = Ease.outCubic(attack / WIND_END);
		} else if (attack < STRIKE_END) {
			from = wind;
			to = strike;
			t = Ease.outQuart((attack - WIND_END) / (STRIKE_END - WIND_END));
		} else {
			from = strike;
			to = HAND_REST;
			t = Ease.inOutSine((attack - STRIKE_END) / (1.0F - STRIKE_END));
		}
		poseStack.translate(
			invert * Mth.lerp(t, from.x(), to.x()),
			Mth.lerp(t, from.y(), to.y()),
			Mth.lerp(t, from.z(), to.z())
		);
		poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(invert * Mth.lerp(t, from.rotY(), to.rotY())));
		poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(invert * Mth.lerp(t, from.rotZ(), to.rotZ())));
		poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(Mth.lerp(t, from.rotX(), to.rotX())));
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
