package dev.forja.client;

import java.util.EnumMap;
import java.util.Map;

import dev.forja.forge.ForgeType;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Every weapon's own blow, written down as keys: where the arm points and where the weapon points at the
 * top of the wind-up and at the end of the strike, how far the shoulder goes, how the body turns, and
 * what the other hand does. {@link CombatPoses} walks a body through them by how far the swing has got
 * (0 to 1), so a heavy weapon with a longer swing plays the same blow, only slower.
 *
 * <p>Directions are degrees seen from the attacking arm's side of the body: a yaw of 0 is straight ahead,
 * a positive yaw goes out to that arm's own side and a negative one across the body, 180 is behind; a
 * pitch of 90 is straight up and -90 straight down. The same keys serve either hand.
 *
 * <p>A held item sticks out of the fist square to the arm, and Minecraft's arm is one stiff block, so a
 * weapon can only point across the arm: pointing the arm is half the pose and turning it about its own
 * length (where the weapon points) is the other half. That is why a key names both.
 */
final class WeaponMotions {
	/** A direction from the shoulder or from the fist, as yaw and pitch in degrees. */
	record Aim(float yaw, float pitch) {
		/** In model space (y down, -z forward), for the right arm, or mirrored for the left. */
		Vector3f dir(boolean left) {
			float yaw = this.yaw * Mth.DEG_TO_RAD;
			float pitch = this.pitch * Mth.DEG_TO_RAD;
			float side = -Mth.sin(yaw) * Mth.cos(pitch);
			return new Vector3f(left ? -side : side, -Mth.sin(pitch), -Mth.cos(yaw) * Mth.cos(pitch));
		}
	}

	/**
	 * One moment of a blow in third person.
	 *
	 * @param arm       where the attacking arm points
	 * @param tip       where the weapon points out of the fist; it can only stand across the arm, so the
	 *                  nearest such direction is taken
	 * @param reach     the attacking shoulder pushed forward (negative: drawn back), in model pixels
	 * @param bodyYaw   the torso turned, in degrees; positive draws the attacking shoulder back
	 * @param bodyPitch the torso bent forward, in degrees
	 */
	record Key(Aim arm, Aim tip, float reach, float bodyYaw, float bodyPitch) {
		Quaternionf orientation(boolean left) {
			return orient(this.arm.dir(left), this.tip.dir(left));
		}

		Key stronger(float by) {
			return new Key(this.arm, this.tip, this.reach * by, this.bodyYaw * by, this.bodyPitch * by);
		}
	}

	/** One moment of a blow in first person: the held item moved (blocks) and turned (degrees) from where it rests. */
	record Hand(float x, float y, float z, float rotY, float rotZ, float rotX) {
		static final Hand REST = new Hand(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

		Hand stronger(float by) {
			return new Hand(this.x * by, this.y * by, this.z * by, this.rotY * by, this.rotZ * by, this.rotX * by);
		}
	}

	/**
	 * One blow: the wind-up, a key the strike passes through when its arc is too wide to go the short way
	 * (null otherwise), the strike, and the same wind-up and strike in first person.
	 */
	record Blow(Key wind, @Nullable Key through, Key strike, Hand handWind, Hand handStrike) {
		Blow stronger(float by) {
			return new Blow(this.wind.stronger(by), this.through == null ? null : this.through.stronger(by), this.strike.stronger(by),
				this.handWind.stronger(by), this.handStrike.stronger(by));
		}
	}

	enum Hands {
		/** One hand on the weapon; the other stays as it was, turning with the body. */
		ONE,
		/** Both hands on it: the other fist follows the first along the grip. */
		TWO,
		/** Fists: the other one guards the chin, and every other blow is thrown with it. */
		FISTS
	}

	/**
	 * A weapon's whole way of swinging.
	 *
	 * @param windEnd   where the wind-up ends, as a part of the whole swing
	 * @param strikeEnd where the strike lands
	 * @param holdEnd   until when the finished blow is held before the weapon comes back
	 * @param whirl     whole turns the weapon spins about the arm while it winds up (the flail), 0 for none
	 * @param plain     the everyday blow; also the pose a mob holds while it warns and a player's charge
	 * @param finisher  the third blow of a combo
	 * @param alternate the blow of every second swing in a row (a backhand, the other fist), or null
	 */
	record Motion(String name, Hands hands, float windEnd, float strikeEnd, float holdEnd, float whirl,
		Blow plain, Blow finisher, @Nullable Blow alternate) {
		Blow blow(boolean finisher, boolean second) {
			if (finisher) {
				return this.finisher;
			}
			return second && this.alternate != null ? this.alternate : this.plain;
		}

		/** Whether a swing with this count in a row is the second blow of a pair. */
		boolean alternates(int swings) {
			return this.alternate != null && swings >= 2 && swings % 2 == 0;
		}
	}

	/** Where the other fist waits while one is thrown: up in front of the chin. */
	static final Aim GUARD = new Aim(-28.0F, -22.0F);
	static final Aim GUARD_TIP = new Aim(0.0F, 90.0F);

	private static Key k(float armYaw, float armPitch, float tipYaw, float tipPitch, float reach, float bodyYaw, float bodyPitch) {
		return new Key(new Aim(armYaw, armPitch), new Aim(tipYaw, tipPitch), reach, bodyYaw, bodyPitch);
	}

	private static Hand h(float x, float y, float z, float rotY, float rotZ, float rotX) {
		return new Hand(x, y, z, rotY, rotZ, rotX);
	}

	private static Blow blow(Key wind, Key strike, Hand handWind, Hand handStrike) {
		return new Blow(wind, null, strike, handWind, handStrike);
	}

	private static Blow blow(Key wind, Key through, Key strike, Hand handWind, Hand handStrike) {
		return new Blow(wind, through, strike, handWind, handStrike);
	}

	// A quick one-handed cut from over the right shoulder down to the left hip. The finisher comes back the
	// other way, a flat backhand sweep from the left that ends with the blade out in front.
	static final Motion ESPADA = new Motion("espada", Hands.ONE, 0.22F, 0.46F, 0.54F, 0.0F,
		blow(k(45, 50, 170, 35, -1, 22, -4), k(5, 5, -50, 70, 1, 0, 4), k(-40, -35, -120, -25, 2, -28, 8),
			h(-0.1F, 0.22F, 0.05F, 0, -25, 5), h(-0.45F, -0.1F, -0.1F, 0, 75, -30)),
		blow(k(-55, 5, -145, 5, -1, -30, 0), k(75, -10, -15, 0, 2, 32, 4),
			h(-0.4F, 0.12F, 0.0F, 175, -80, 0), h(-0.05F, 0.12F, -0.05F, -10, -65, 0)),
		null);

	// Two hands, a wide flat sweep from the right to the left with the whole body turning through it; the
	// finisher sweeps back the other way.
	static final Motion ESPADON = new Motion("espadon", Hands.TWO, 0.32F, 0.6F, 0.68F, 0.0F,
		blow(k(65, -15, 155, -5, -1, 35, 0), k(10, -5, 100, 0, 2, 5, 3), k(-70, -10, 20, 0, 2, -40, 5),
			h(-0.2F, 0.2F, 0.0F, 0, -70, 0), h(-0.35F, 0.12F, -0.05F, 175, -85, 0)),
		blow(k(-65, -5, -155, 0, -1, -35, 0), k(-5, -5, -95, 0, 2, 0, 3), k(70, -10, -20, 0, 2, 40, 5),
			h(-0.35F, 0.15F, 0.0F, 175, -85, 0), h(-0.1F, 0.08F, -0.05F, -10, -65, 0)),
		null);

	// Two hands, low and wide: the blade reaps from the right across to the left and is pulled back in
	// towards the body. The finisher starts from high over the shoulder.
	static final Motion GUADANA = new Motion("guadana", Hands.TWO, 0.3F, 0.6F, 0.7F, 0.0F,
		blow(k(60, -20, 150, -10, -1, 38, 8), k(0, -45, 90, -10, 1, 0, 14), k(-45, -60, 20, -20, -2.5F, -35, 16),
			h(-0.1F, 0.02F, 0.05F, 0, -55, 10), h(-0.35F, 0.15F, 0.05F, 175, -92, 5)),
		blow(k(60, 30, 150, 30, -1, 40, 0), k(10, -20, 90, 0, 1, 0, 10), k(-45, -60, 10, -30, -2.5F, -40, 20),
			h(-0.1F, 0.25F, 0.05F, 0, -35, 10), h(-0.35F, 0.15F, 0.05F, 175, -92, 5)),
		null);

	// One hand, raised high over the right shoulder and chopped down diagonally to the left.
	static final Motion HACHA = new Motion("hacha", Hands.ONE, 0.3F, 0.55F, 0.62F, 0.0F,
		blow(k(30, 75, 175, 15, 0, 20, -5), k(-20, -40, -20, 50, 2, -25, 12),
			h(-0.15F, 0.25F, 0.05F, 0, -20, 5), h(-0.3F, -0.05F, -0.1F, 40, 20, -45)),
		blow(k(20, 100, 180, -10, -1, 28, -10), k(-15, -55, -10, 35, 3, -30, 18),
			h(-0.15F, 0.3F, 0.08F, 0, -24, 8), h(-0.32F, -0.08F, -0.12F, 40, 24, -55)),
		null);

	// One hand, a hook from below: from low behind the hip up to head height, the pick's point turning
	// over at the top to bite down. The finisher is the other end of the tool, a straight peck from overhead.
	static final Motion PICAHACHA = new Motion("picahacha", Hands.ONE, 0.3F, 0.5F, 0.6F, 0.0F,
		blow(k(15, -110, 180, -20, -1, 25, 10), k(-10, 40, -10, -50, 2.5F, -20, -8),
			h(-0.15F, -0.05F, -0.02F, 0, -10, 10), h(-0.25F, 0.28F, -0.12F, 35, 5, -55)),
		blow(k(0, 120, 180, -30, -2, 12, -12), k(0, -30, 0, 60, 3, -8, 24),
			h(-0.2F, 0.3F, 0.06F, 0, 5, 8), h(-0.28F, -0.05F, -0.1F, 40, 5, -50)),
		null);

	// Both hands, lifted straight overhead and smashed down in front.
	static final Motion MAZO = new Motion("mazo", Hands.TWO, 0.34F, 0.56F, 0.66F, 0.0F,
		blow(k(5, 110, 180, -20, -1.5F, 8, -10), k(-5, -35, 0, 55, 2, -5, 20),
			h(-0.25F, 0.18F, -0.08F, 0, 12, -15), h(-0.32F, -0.08F, -0.08F, 40, 12, -45)),
		blow(k(5, 125, 180, -35, -2, 10, -14), k(-5, -50, 0, 40, 3, -6, 26),
			h(-0.25F, 0.22F, -0.06F, 0, 12, -10), h(-0.34F, -0.12F, -0.08F, 40, 12, -52)),
		null);

	// Both hands, the biggest blow there is: from far behind the head, over, and down to the ground, bent
	// over it and held there a moment before the hammer comes back up.
	static final Motion MARTILLO = new Motion("martillo", Hands.TWO, 0.38F, 0.6F, 0.82F, 0.0F,
		blow(k(5, 135, 180, -45, -2, 10, -16), k(0, 30, 180, 60, 0, 0, 5), k(-5, -70, 0, 20, 2, -8, 32),
			h(-0.25F, 0.25F, -0.05F, 0, 12, -10), h(-0.35F, -0.15F, -0.05F, 40, 12, -55)),
		blow(k(5, 145, 180, -55, -3, 12, -20), k(0, 30, 180, 60, 0, 0, 5), k(-5, -80, 0, 10, 3, -10, 38),
			h(-0.25F, 0.28F, -0.04F, 0, 12, -6), h(-0.37F, -0.2F, -0.05F, 40, 12, -62)),
		null);

	// One hand: the ball is whirled round over the head through the whole wind-up and whipped down across.
	static final Motion MANGUAL = new Motion("mangual", Hands.ONE, 0.42F, 0.62F, 0.7F, 1.0F,
		blow(k(25, 85, 110, 0, 0, 12, -4), k(-25, -30, -20, 60, 2, -30, 12),
			h(-0.22F, 0.28F, 0.05F, 0, -5, 5), h(-0.35F, -0.05F, -0.1F, 35, 30, -45)),
		blow(k(30, 95, 110, 0, -1, 18, -8), k(-30, -45, -20, 45, 3, -35, 18),
			h(-0.22F, 0.32F, 0.06F, 0, -5, 8), h(-0.38F, -0.08F, -0.12F, 35, 34, -55)),
		null);

	// Fast and short: a stab up from the hip, and every second blow in a row a backhand slash; the
	// finisher lunges.
	static final Motion DAGA = new Motion("daga", Hands.ONE, 0.18F, 0.38F, 0.46F, 0.0F,
		blow(k(10, -88, 0, 2, -2, 25, 0), k(-5, -50, 0, 40, 4, -20, 8),
			h(-0.08F, 0.0F, 0.02F, 0, 0, -20), h(-0.18F, 0.1F, -0.2F, 35, 0, -65)),
		blow(k(15, -88, 0, 2, -3.5F, 32, 0), k(0, -40, 0, 50, 6, -30, 14),
			h(-0.08F, -0.02F, 0.04F, 0, 0, -25), h(-0.2F, 0.12F, -0.28F, 35, 0, -70)),
		blow(k(-55, -15, -140, 10, 0, -25, 2), k(60, -10, 140, 5, 1, 22, 4),
			h(-0.45F, 0.12F, 0.0F, -20, 70, -10), h(-0.05F, 0.12F, -0.08F, 20, -55, -10)));

	// Both hands on the shaft, drawn back past the hip and driven straight forward. Drawn back by the
	// shoulder and the turn of the body, not by the arm: an arm swung back behind the hip tips whatever
	// it holds down at the ground, since the weapon stands square to the arm.
	static final Motion TRIDENTE = new Motion("tridente", Hands.TWO, 0.3F, 0.5F, 0.62F, 0.0F,
		blow(k(15, -88, 0, 2, -3, 30, 0), k(-5, -62, 0, 28, 5.5F, -20, 10),
			h(-0.2F, 0.0F, 0.02F, 0, 0, -30), h(-0.2F, 0.1F, -0.2F, 35, 0, -70)),
		blow(k(20, -88, 0, 2, -4, 36, 0), k(-5, -55, 0, 35, 7, -25, 14),
			h(-0.2F, -0.02F, 0.04F, 0, 0, -35), h(-0.22F, 0.12F, -0.28F, 35, 0, -75)),
		null);

	// The spear keeps vanilla's stab and its kinetic charge: this is only the pose a mob holds while it
	// warns (the spear drawn back) and a player's charged blow, which vanilla has no pose for.
	static final Motion LANZA = new Motion("lanza", Hands.ONE, 0.3F, 0.5F, 0.6F, 0.0F,
		blow(k(15, -88, 0, 2, -3, 30, 0), k(-5, -62, 0, 28, 5.5F, -20, 8),
			h(-0.2F, 0.0F, 0.02F, 0, 0, -30), h(-0.2F, 0.1F, -0.2F, 35, 0, -70)),
		blow(k(20, -88, 0, 2, -4, 36, 0), k(-5, -55, 0, 35, 7, -25, 12),
			h(-0.2F, -0.02F, 0.04F, 0, 0, -35), h(-0.22F, 0.12F, -0.28F, 35, 0, -75)),
		null);

	// Straight punches, left and right in turn with the other fist up at the chin; the finisher is an uppercut.
	static final Motion GUANTELETES = new Motion("guanteletes", Hands.FISTS, 0.2F, 0.42F, 0.5F, 0.0F,
		blow(k(-5, -50, 0, 40, -2, 20, 0), k(-8, 0, 0, 90, 4, -25, 4),
			h(-0.05F, 0.02F, 0.08F, 0, 0, 0), h(-0.25F, 0.15F, -0.25F, 0, 0, 5)),
		blow(k(20, -85, 0, 5, -1, 30, 8), k(-10, 50, 180, 40, 3, -30, -8),
			h(-0.05F, -0.1F, 0.06F, 0, 0, 10), h(-0.25F, 0.3F, -0.18F, 0, 0, 25)),
		// Thrown with the other fist; in first person the gauntlet hand only pulls back to guard.
		blow(k(-5, -50, 0, 40, -2, 20, 0), k(-8, 0, 0, 90, 4, -25, 4),
			h(0.0F, 0.0F, 0.08F, 5, 0, 5), h(0.02F, -0.02F, 0.12F, 10, 0, 8)));

	// A staff: a two-handed jab with the crescent at head height; the finisher swings it round like a
	// quarterstaff.
	static final Motion BACULO = new Motion("baculo", Hands.TWO, 0.3F, 0.52F, 0.6F, 0.0F,
		blow(k(15, -88, 0, 2, -2.5F, 22, 0), k(-5, -35, 0, 55, 4, -15, 6),
			h(-0.15F, 0.0F, 0.08F, 0, 0, -10), h(-0.25F, 0.15F, -0.2F, 35, 0, -55)),
		blow(k(70, 10, 160, 30, -1, 35, 0), k(-60, -20, 30, 20, 2, -40, 6),
			h(-0.15F, 0.15F, 0.05F, 0, -45, 5), h(-0.35F, 0.05F, -0.05F, 175, -85, 0)),
		null);

	// The tome, slapped forward one-handed from up by the shoulder; the finisher brings it down from overhead.
	static final Motion GRIMORIO = new Motion("grimorio", Hands.ONE, 0.25F, 0.45F, 0.55F, 0.0F,
		blow(k(45, 40, 160, 45, -1, 22, -3), k(-10, -5, 0, 85, 3, -18, 6),
			h(-0.15F, 0.2F, -0.05F, 30, -15, 5), h(-0.35F, 0.12F, -0.15F, 70, 10, -20)),
		blow(k(10, 100, 180, -10, -1, 10, -8), k(-5, -40, 0, 50, 3, -8, 16),
			h(-0.25F, 0.3F, 0.05F, 0, 0, 10), h(-0.32F, 0.0F, -0.12F, 0, 0, -60)),
		null);

	// The tools get a light version: a small peck for the pick, a scoop for the shovel, a short chop
	// pulled back for the hoe.
	private static final Blow PICO_BLOW = blow(k(10, 50, 175, 40, 0, 10, -3), k(0, -35, 0, 55, 1, -8, 8),
		h(-0.15F, 0.15F, -0.05F, 0, -5, -5), h(-0.22F, -0.05F, -0.08F, 35, 5, -40));
	static final Motion PICO = new Motion("pico", Hands.ONE, 0.3F, 0.55F, 0.6F, 0.0F, PICO_BLOW, PICO_BLOW.stronger(1.3F), null);

	private static final Blow PALA_BLOW = blow(k(10, -88, 0, 2, -1.5F, 12, 0), k(0, -45, 0, 45, 2, -8, 8),
		h(-0.15F, 0.0F, 0.0F, 0, 0, -15), h(-0.25F, 0.12F, -0.15F, 0, 0, 15));
	static final Motion PALA = new Motion("pala", Hands.ONE, 0.3F, 0.55F, 0.6F, 0.0F, PALA_BLOW, PALA_BLOW.stronger(1.3F), null);

	private static final Blow AZADA_BLOW = blow(k(10, 55, 175, 35, 0, 10, -3), k(0, -50, 0, 40, -1.5F, -6, 10),
		h(-0.12F, 0.22F, 0.03F, 0, -5, 8), h(-0.18F, -0.02F, 0.05F, 0, 5, -55));
	static final Motion AZADA = new Motion("azada", Hands.ONE, 0.3F, 0.55F, 0.62F, 0.0F, AZADA_BLOW, AZADA_BLOW.stronger(1.3F), null);

	private static final Map<ForgeType, Motion> BY_TYPE = new EnumMap<>(ForgeType.class);

	static {
		BY_TYPE.put(ForgeType.ESPADA, ESPADA);
		BY_TYPE.put(ForgeType.ESPADON, ESPADON);
		BY_TYPE.put(ForgeType.GUADANA, GUADANA);
		BY_TYPE.put(ForgeType.HACHA, HACHA);
		BY_TYPE.put(ForgeType.PICAHACHA, PICAHACHA);
		BY_TYPE.put(ForgeType.MAZO, MAZO);
		BY_TYPE.put(ForgeType.MARTILLO, MARTILLO);
		BY_TYPE.put(ForgeType.MANGUAL, MANGUAL);
		BY_TYPE.put(ForgeType.DAGA, DAGA);
		BY_TYPE.put(ForgeType.TRIDENTE, TRIDENTE);
		BY_TYPE.put(ForgeType.LANZA, LANZA);
		BY_TYPE.put(ForgeType.GUANTELETES, GUANTELETES);
		BY_TYPE.put(ForgeType.BACULO, BACULO);
		BY_TYPE.put(ForgeType.GRIMORIO, GRIMORIO);
		BY_TYPE.put(ForgeType.PICO, PICO);
		BY_TYPE.put(ForgeType.PALA, PALA);
		BY_TYPE.put(ForgeType.AZADA, AZADA);
	}

	private WeaponMotions() {
	}

	/**
	 * The way a weapon swings, or null to leave it to vanilla: forged weapons by their type, and vanilla's
	 * swords, axes, mace and trident as their forged cousins.
	 */
	static @Nullable Motion of(ItemStack weapon) {
		if (weapon.isEmpty()) {
			return null;
		}
		ForgedParts parts = weapon.get(ModComponents.PARTS);
		if (parts != null) {
			return BY_TYPE.get(parts.type());
		}
		if (weapon.is(ItemTags.SWORDS)) {
			return ESPADA;
		}
		if (weapon.is(ItemTags.AXES)) {
			return HACHA;
		}
		if (weapon.is(Items.MACE)) {
			return MAZO;
		}
		if (weapon.is(Items.TRIDENT)) {
			return TRIDENTE;
		}
		return null;
	}

	/**
	 * The turn that takes an arm hanging at rest to point along {@code arm} with its weapon along {@code tip}
	 * (both in model space). An arm hangs along +y and what it holds sticks out along -z.
	 */
	static Quaternionf orient(Vector3f arm, Vector3f tip) {
		Vector3f down = new Vector3f(arm).normalize();
		Vector3f out = new Vector3f(tip).fma(-tip.dot(down), down);
		if (out.lengthSquared() < 1.0E-4F) {
			// The weapon asked to lie along the arm, which it cannot: point the arm and let it fall where it may.
			return new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, down.x, down.y, down.z);
		}
		out.normalize();
		Vector3f back = out.negate(new Vector3f());
		Vector3f side = down.cross(back, new Vector3f());
		return new Quaternionf().setFromNormalized(new Matrix3f(side, down, back));
	}
}
