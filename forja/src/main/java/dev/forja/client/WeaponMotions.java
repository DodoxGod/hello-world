package dev.forja.client;

import java.util.EnumMap;
import java.util.List;
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

		/** Moved further, but turned the same: a turn made larger would point the weapon somewhere else. */
		Hand stronger(float by) {
			return new Hand(this.x * by, this.y * by, this.z * by, this.rotY, this.rotZ, this.rotX);
		}
	}

	/**
	 * Which way a flail's ball goes out to its target (see {@link HeldFlail}): up and over onto it, round
	 * from the side, or up from below. Only the flail's blows have one.
	 */
	enum Swirl {
		OVER,
		SIDE,
		UNDER
	}

	/**
	 * One blow: the wind-up, the keys the strike passes through on its way when its arc is too wide to go
	 * the short way or it strikes twice (empty otherwise), the strike, and the same in first person (where
	 * the keys on the way are left out unless first person has its own).
	 *
	 * @param whirl whole turns the weapon spins about the arm while it winds up (the flail), 0 for none
	 * @param swirl the way a flail's ball goes out, null for anything else
	 */
	record Blow(Key wind, List<Key> via, Key strike, Hand handWind, List<Hand> handVia, Hand handStrike, float whirl,
		@Nullable Swirl swirl) {
		Blow stronger(float by) {
			return new Blow(this.wind.stronger(by), this.via.stream().map(key -> key.stronger(by)).toList(), this.strike.stronger(by),
				this.handWind.stronger(by), this.handVia.stream().map(hand -> hand.stronger(by)).toList(), this.handStrike.stronger(by),
				this.whirl, this.swirl);
		}

		/** The same blow, whirled round this many whole turns while it winds up, its ball going out this way. */
		Blow whirled(float turns, Swirl way) {
			return new Blow(this.wind, this.via, this.strike, this.handWind, this.handVia, this.handStrike, turns, way);
		}

		/** The same blow with keys of its own on the way in first person too. */
		Blow handsVia(Hand... hands) {
			return new Blow(this.wind, this.via, this.strike, this.handWind, List.of(hands), this.handStrike, this.whirl, this.swirl);
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
	 * <p>Andy: "las armas con animaciones tienen como mucho 1, maximo 2, quiero mas variedad". So each weapon
	 * has three or four blows of its own and a finisher, and a flurry goes through them in turn, never the
	 * same one twice running (see {@link #variant}).
	 *
	 * @param windEnd   where the wind-up ends, as a part of the whole swing
	 * @param strikeEnd where the strike lands
	 * @param holdEnd   until when the finished blow is held before the weapon comes back
	 * @param blows     its blows; the first is the everyday one, also the pose a player's charge holds
	 * @param finisher  the third blow of a combo
	 */
	record Motion(String name, Hands hands, float windEnd, float strikeEnd, float holdEnd, List<Blow> blows, Blow finisher) {
		Blow plain() {
			return this.blows.get(0);
		}

		Blow blow(boolean finisher, int variant) {
			return finisher ? this.finisher : this.blows.get(Math.floorMod(variant, this.blows.size()));
		}

		/**
		 * Which of the blows a swing throws. Everyone who sees the swing has to work out the same one, the
		 * attacker and whoever watches, from what they all know: how many swings in a row this is (they all
		 * see every swing start) and a seed that is the same for all of them. For a mob's blow that follows
		 * its warning the seed is the one the server sent with the warning, and nothing else counts, so the
		 * blow is the one the warning wound up; otherwise it is the seed the flurry started with, and each
		 * swing in a row takes the next blow, so no two in a row are the same.
		 *
		 * @param swings how many swings in a row, counting this one (see {@link CombatAnims#swings})
		 * @param warned the blow was announced by a warning, whose seed alone picks it
		 */
		int variant(int swings, int seed, boolean warned) {
			return Math.floorMod(warned ? seed : seed + Math.max(1, swings) - 1, this.blows.size());
		}

		/** Whether the swing with this count in a row is thrown by the other fist. */
		boolean alternates(int swings) {
			return this.hands == Hands.FISTS && swings >= 2 && swings % 2 == 0;
		}
	}

	/** Where the other fist waits while one is thrown: up in front of the chin. */
	static final Aim GUARD = new Aim(-28.0F, -22.0F);
	static final Aim GUARD_TIP = new Aim(0.0F, 90.0F);
	/** In first person, the gauntlet's own hand while the other fist punches: pulled back a little, on guard. */
	static final Hand GUARD_WIND = new Hand(0.0F, 0.0F, 0.08F, 5.0F, 0.0F, 5.0F);
	static final Hand GUARD_STRIKE = new Hand(0.02F, -0.02F, 0.12F, 10.0F, 0.0F, 8.0F);

	private static Key k(float armYaw, float armPitch, float tipYaw, float tipPitch, float reach, float bodyYaw, float bodyPitch) {
		return new Key(new Aim(armYaw, armPitch), new Aim(tipYaw, tipPitch), reach, bodyYaw, bodyPitch);
	}

	private static Hand h(float x, float y, float z, float rotY, float rotZ, float rotX) {
		return new Hand(x, y, z, rotY, rotZ, rotX);
	}

	private static Blow blow(Key wind, Key strike, Hand handWind, Hand handStrike) {
		return new Blow(wind, List.of(), strike, handWind, List.of(), handStrike, 0.0F, null);
	}

	private static Blow blow(Key wind, Key through, Key strike, Hand handWind, Hand handStrike) {
		return new Blow(wind, List.of(through), strike, handWind, List.of(), handStrike, 0.0F, null);
	}

	private static Blow blow(Key wind, List<Key> via, Key strike, Hand handWind, Hand handStrike) {
		return new Blow(wind, via, strike, handWind, List.of(), handStrike, 0.0F, null);
	}

	private static Motion motion(String name, Hands hands, float windEnd, float strikeEnd, float holdEnd, Blow finisher, Blow... blows) {
		return new Motion(name, hands, windEnd, strikeEnd, holdEnd, List.of(blows), finisher);
	}

	// The sword, one hand and quick: a diagonal cut from over the right shoulder down to the left hip, a flat
	// cut from the right across to the left, the same flat cut back the other way (a backhand), and a thrust
	// from the hip. The finisher cuts an X: down across the body to the left hip, then back up and out to the
	// right, above the shoulder.
	static final Motion ESPADA = motion("espada", Hands.ONE, 0.22F, 0.46F, 0.54F,
		blow(k(45, 55, 170, 40, -1, 26, -6), List.of(k(-40, -35, -120, -25, 2, -30, 10)), k(55, 45, 60, 80, 1.5F, 30, -4),
			h(-0.1F, 0.24F, 0.05F, 0, -25, 5), h(0.02F, 0.22F, -0.05F, 0, -40, 10))
			.handsVia(h(-0.45F, -0.1F, -0.1F, 0, 75, -30)),
		blow(k(45, 50, 170, 35, -1, 22, -4), k(5, 5, -50, 70, 1, 0, 4), k(-40, -35, -120, -25, 2, -28, 8),
			h(-0.1F, 0.22F, 0.05F, 0, -25, 5), h(-0.45F, -0.1F, -0.1F, 0, 75, -30)),
		blow(k(75, 5, 165, 5, -1, 32, 0), k(10, 0, 80, 0, 1.5F, 0, 3), k(-65, -5, -160, -5, 2, -32, 5),
			h(-0.08F, 0.15F, 0.05F, 0, -70, 0), h(-0.42F, 0.1F, -0.05F, 175, -85, 0)),
		blow(k(-55, 5, -145, 5, -1, -30, 0), k(75, -10, -15, 0, 2, 32, 4),
			h(-0.4F, 0.12F, 0.0F, 175, -80, 0), h(-0.05F, 0.12F, -0.05F, -10, -65, 0)),
		blow(k(15, -88, 0, 2, -3, 28, 0), k(-5, -55, 0, 35, 6, -22, 10),
			h(-0.08F, -0.02F, 0.06F, -20, 0, -40), h(-0.12F, 0.1F, -0.32F, -30, 0, -80)));

	// Two hands. A wide flat sweep from the right to the left with the whole body turning through it, a
	// cleave from straight overhead, a rising cut from low behind the right hip up across to the left, and
	// the wide sweep back the other way. The finisher is the first sweep, wider and with more body in it.
	private static final Blow ESPADON_SWEEP = blow(k(65, -15, 155, -5, -1, 35, 0), k(10, -5, 100, 0, 2, 5, 3), k(-70, -10, 20, 0, 2, -40, 5),
		h(-0.2F, 0.2F, 0.0F, 0, -70, 0), h(-0.35F, 0.12F, -0.05F, 175, -85, 0));
	static final Motion ESPADON = motion("espadon", Hands.TWO, 0.32F, 0.6F, 0.68F,
		ESPADON_SWEEP.stronger(1.35F),
		ESPADON_SWEEP,
		blow(k(5, 125, 180, -30, -2, 10, -12), k(-5, -40, 0, 50, 3, -6, 22),
			h(-0.25F, 0.22F, -0.06F, 0, 12, -10), h(-0.34F, -0.1F, -0.08F, 40, 12, -50)),
		blow(k(45, -75, 175, -35, -1.5F, 30, 6), k(10, -20, 100, 30, 1, 5, 0), k(-35, 55, -40, 70, 2, -30, -6),
			h(-0.05F, -0.06F, 0.05F, 0, -35, 25), h(-0.35F, 0.3F, -0.1F, 0, 45, -15)),
		blow(k(-65, -5, -155, 0, -1, -35, 0), k(-5, -5, -95, 0, 2, 0, 3), k(70, -10, -20, 0, 2, 40, 5),
			h(-0.35F, 0.15F, 0.0F, 175, -85, 0), h(-0.1F, 0.08F, -0.05F, -10, -65, 0)));

	// Two hands, low and wide: the blade reaps from the right across to the left and is pulled back in
	// towards the body; the same reap the other way, from the left; and a cut from high over the shoulder
	// down into the ground in front. The finisher reaps from high on the right, with more body in it.
	static final Motion GUADANA = motion("guadana", Hands.TWO, 0.3F, 0.6F, 0.7F,
		blow(k(65, 30, 155, 30, -1, 45, 0), k(15, -25, 90, 0, 1, 0, 12), k(-50, -60, 20, -20, -3, -45, 20),
			h(-0.1F, 0.25F, 0.05F, 0, -35, 10), h(-0.38F, 0.15F, 0.05F, 175, -95, 5)),
		blow(k(60, -20, 150, -10, -1, 38, 8), k(0, -45, 90, -10, 1, 0, 14), k(-45, -60, 20, -20, -2.5F, -35, 16),
			h(-0.1F, 0.02F, 0.05F, 0, -55, 10), h(-0.35F, 0.15F, 0.05F, 175, -92, 5)),
		blow(k(-60, -20, -150, -10, -1, -38, 8), k(0, -45, -90, -10, 1, 0, 14), k(45, -60, -20, -20, -2.5F, 35, 16),
			h(-0.35F, 0.12F, 0.05F, 175, -92, 5), h(-0.05F, 0.02F, 0.05F, 0, -55, 10)),
		blow(k(10, 115, 180, -15, -1.5F, 15, -10), k(0, -40, 0, 50, 2, -5, 22),
			h(-0.2F, 0.3F, 0.05F, 0, -10, 10), h(-0.3F, -0.05F, -0.12F, 40, 10, -55)));

	// One hand: raised high over the right shoulder and chopped down diagonally to the left; a flat swing
	// from the right; a backhand from across the body out to the right; and a chop straight down from
	// overhead. The finisher is the diagonal chop from higher still.
	static final Motion HACHA = motion("hacha", Hands.ONE, 0.3F, 0.55F, 0.62F,
		blow(k(20, 100, 180, -10, -1, 28, -10), k(-15, -55, -10, 35, 3, -30, 18),
			h(-0.15F, 0.3F, 0.08F, 0, -24, 8), h(-0.32F, -0.08F, -0.12F, 40, 24, -55)),
		blow(k(30, 75, 175, 15, 0, 20, -5), k(-20, -40, -20, 50, 2, -25, 12),
			h(-0.15F, 0.25F, 0.05F, 0, -20, 5), h(-0.3F, -0.05F, -0.1F, 40, 20, -45)),
		blow(k(80, 10, 170, 15, -1, 35, 0), k(10, -5, 80, 5, 1.5F, 0, 4), k(-55, -15, -150, -10, 2, -30, 6),
			h(-0.08F, 0.15F, 0.05F, 0, -70, 0), h(-0.42F, 0.06F, -0.05F, 175, -88, 0)),
		blow(k(-60, 15, -150, 15, -1, -30, 0), k(75, -15, -10, 10, 2, 32, 5),
			h(-0.42F, 0.14F, 0.0F, 175, -80, 0), h(-0.05F, 0.1F, -0.05F, -10, -65, 0)),
		blow(k(5, 125, 180, -25, -1.5F, 10, -10), k(-5, -45, 0, 50, 3, -6, 20),
			h(-0.22F, 0.3F, 0.02F, 0, 5, 8), h(-0.28F, -0.1F, -0.1F, 40, 5, -55)));

	// One hand, a hook from below: from low behind the hip up to head height, the pick's point turning
	// over at the top to bite down; the other end of the tool, a straight peck from overhead; and a flat
	// swing from the right that brings the axe blade across. The finisher is the peck from higher still.
	static final Motion PICAHACHA = motion("picahacha", Hands.ONE, 0.3F, 0.5F, 0.6F,
		blow(k(0, 130, 180, -35, -2.5F, 14, -14), k(0, -35, 0, 55, 3.5F, -10, 28),
			h(-0.2F, 0.32F, 0.06F, 0, 5, 8), h(-0.3F, -0.08F, -0.1F, 40, 5, -55)),
		blow(k(15, -110, 180, -20, -1, 25, 10), k(-10, 40, -10, -50, 2.5F, -20, -8),
			h(-0.15F, -0.05F, -0.02F, 0, -10, 10), h(-0.25F, 0.28F, -0.12F, 35, 5, -55)),
		blow(k(0, 120, 180, -30, -2, 12, -12), k(0, -30, 0, 60, 3, -8, 24),
			h(-0.2F, 0.3F, 0.06F, 0, 5, 8), h(-0.28F, -0.05F, -0.1F, 40, 5, -50)),
		blow(k(80, 10, 170, 15, -1, 35, 0), k(10, -5, 80, 5, 1.5F, 0, 4), k(-55, -15, -150, -10, 2, -30, 6),
			h(-0.08F, 0.15F, 0.05F, 0, -70, 0), h(-0.42F, 0.06F, -0.05F, 175, -88, 0)));

	// A two-handed swing from the right, the head out flat, that the hammer and the mace share, and an
	// uppercut: from low behind the hip, up past the face to high in front.
	private static final Blow HEAVY_SIDE = blow(k(70, 0, 160, 0, -1.5F, 42, 0), k(15, -10, 95, 0, 1.5F, 8, 4), k(-60, -15, -20, 0, 2, -42, 8),
		h(-0.1F, 0.16F, 0.02F, 0, -65, 0), h(-0.38F, 0.08F, -0.05F, 175, -88, 0));
	private static final Blow HEAVY_UPPERCUT = blow(k(30, -85, 175, -55, -1.5F, 25, 10), k(-15, 60, -10, 85, 3, -20, -10),
		h(-0.05F, -0.08F, 0.08F, 0, -10, 30), h(-0.28F, 0.32F, -0.18F, 20, 10, -25));

	// Both hands: lifted straight overhead and smashed down in front, swung flat from the side, or driven
	// up from below. The finisher is the overhead smash, bigger.
	static final Motion MAZO = motion("mazo", Hands.TWO, 0.34F, 0.56F, 0.66F,
		blow(k(5, 125, 180, -35, -2, 10, -14), k(-5, -50, 0, 40, 3, -6, 26),
			h(-0.25F, 0.22F, -0.06F, 0, 12, -10), h(-0.34F, -0.12F, -0.08F, 40, 12, -52)),
		blow(k(5, 110, 180, -20, -1.5F, 8, -10), k(-5, -35, 0, 55, 2, -5, 20),
			h(-0.25F, 0.18F, -0.08F, 0, 12, -15), h(-0.32F, -0.08F, -0.08F, 40, 12, -45)),
		HEAVY_SIDE,
		HEAVY_UPPERCUT);

	// Both hands, the biggest blow there is: from far behind the head, over, and down to the ground, bent
	// over it and held there a moment before the hammer comes back up; then the same flat swing from the
	// side and the uppercut as the mace, only slower. The finisher goes further back and further down.
	static final Motion MARTILLO = motion("martillo", Hands.TWO, 0.38F, 0.6F, 0.82F,
		blow(k(5, 145, 180, -55, -3, 12, -20), k(0, 30, 180, 60, 0, 0, 5), k(-5, -80, 0, 10, 3, -10, 38),
			h(-0.25F, 0.28F, -0.04F, 0, 12, -6), h(-0.37F, -0.2F, -0.05F, 40, 12, -62)),
		blow(k(5, 135, 180, -45, -2, 10, -16), k(0, 30, 180, 60, 0, 0, 5), k(-5, -70, 0, 20, 2, -8, 32),
			h(-0.25F, 0.25F, -0.05F, 0, 12, -10), h(-0.35F, -0.15F, -0.05F, 40, 12, -55)),
		HEAVY_SIDE.stronger(1.15F),
		HEAVY_UPPERCUT.stronger(1.15F));

	// One hand, the ball on its chain (see HeldFlail): whirled round over the head through the whole wind-up
	// and brought up and over onto the target; whirled like a windmill out at the side and swung round flat;
	// or swung back low behind the hip and brought up from below. The finisher whirls twice overhead.
	static final Motion MANGUAL = motion("mangual", Hands.ONE, 0.42F, 0.62F, 0.7F,
		blow(k(30, 95, 110, 0, -1, 18, -8), k(-30, -45, -20, 45, 3, -35, 18),
			h(-0.22F, 0.32F, 0.06F, 0, -5, 8), h(-0.38F, -0.08F, -0.12F, 35, 34, -55)).whirled(2.0F, Swirl.OVER),
		blow(k(25, 85, 110, 0, 0, 12, -4), k(-25, -30, -20, 60, 2, -30, 12),
			h(-0.22F, 0.28F, 0.05F, 0, -5, 5), h(-0.35F, -0.05F, -0.1F, 35, 30, -45)).whirled(1.0F, Swirl.OVER),
		blow(k(85, 5, 0, 0, -1, 35, 0), k(-35, -10, -100, 0, 2.5F, -35, 6),
			h(0.12F, 0.1F, 0.1F, 0, -60, 0), h(-0.38F, 0.05F, -0.1F, 20, 50, -20)).whirled(1.0F, Swirl.SIDE),
		blow(k(20, -110, 180, -20, -1.5F, 25, 10), k(-10, 35, -10, -55, 2.5F, -20, -8),
			h(0.02F, -0.22F, 0.12F, 0, -10, 35), h(-0.25F, 0.22F, -0.15F, 0, 10, -35)).whirled(0.0F, Swirl.UNDER));

	// Fast and short: a stab up from the hip, a backhand slash, an overhand stab with the blade pointing
	// down (the grip turned over), and two quick stabs in the time of one. The finisher lunges.
	static final Motion DAGA = motion("daga", Hands.ONE, 0.18F, 0.38F, 0.46F,
		blow(k(15, -88, 0, 2, -3.5F, 32, 0), k(0, -40, 0, 50, 6, -30, 14),
			h(-0.08F, -0.02F, 0.04F, -20, 0, -40), h(-0.12F, 0.12F, -0.34F, -30, 0, -82)),
		blow(k(10, -88, 0, 2, -2, 25, 0), k(-5, -50, 0, 40, 4, -20, 8),
			h(-0.08F, 0.0F, 0.02F, -20, 0, -40), h(-0.12F, 0.1F, -0.26F, -30, 0, -80)),
		blow(k(-55, -15, -140, 10, 0, -25, 2), k(60, -10, 140, 5, 1, 22, 4),
			h(-0.45F, 0.12F, 0.0F, -20, 70, -10), h(-0.05F, 0.12F, -0.08F, 20, -55, -10)),
		blow(k(10, 95, 0, -5, -1, 18, -8), k(-5, -5, 0, -85, 3.5F, -15, 14),
			h(-0.12F, 0.25F, 0.05F, 0, 0, 60), h(-0.18F, 0.02F, -0.22F, 0, 0, 125)),
		blow(k(12, -88, 0, 2, -2, 22, 0), List.of(k(-5, -50, 0, 40, 4, -18, 8), k(8, -80, 0, 8, -0.5F, 12, 2)),
			k(-5, -45, 0, 45, 4.5F, -22, 10),
			h(-0.08F, 0.0F, 0.04F, -20, 0, -40), h(-0.12F, 0.1F, -0.28F, -30, 0, -80))
			.handsVia(h(-0.1F, 0.09F, -0.24F, -30, 0, -78), h(-0.08F, 0.02F, 0.0F, -20, 0, -45)));

	// Both hands on the shaft: drawn back past the hip and driven straight forward, drawn back by the shoulder
	// and the turn of the body, not by the arm (an arm swung back behind the hip tips whatever it holds down
	// at the ground, since the weapon stands square to the arm); a flat sweep of the shaft from the right;
	// and a jab down from overhead, the points driven down into the target. The finisher is the thrust with
	// more of the body in it.
	static final Motion TRIDENTE = motion("tridente", Hands.TWO, 0.3F, 0.5F, 0.62F,
		blow(k(20, -88, 0, 2, -4, 36, 0), k(-5, -55, 0, 35, 7, -25, 14),
			h(-0.2F, -0.02F, 0.04F, -20, 0, -45), h(-0.16F, 0.12F, -0.34F, -30, 0, -82)),
		blow(k(15, -88, 0, 2, -3, 30, 0), k(-5, -62, 0, 28, 5.5F, -20, 10),
			h(-0.2F, 0.0F, 0.02F, -20, 0, -40), h(-0.16F, 0.1F, -0.26F, -30, 0, -80)),
		blow(k(70, -5, 160, 0, -1.5F, 40, 0), k(10, -10, 95, 0, 1.5F, 5, 3), k(-60, -15, -20, 0, 2, -40, 6),
			h(-0.1F, 0.15F, 0.02F, 0, -65, 0), h(-0.38F, 0.08F, -0.05F, 175, -88, 0)),
		blow(k(10, 110, 0, 20, -1.5F, 12, -10), k(-5, 25, 0, -60, 4, -12, 12),
			h(-0.15F, 0.3F, 0.08F, 0, 0, 55), h(-0.2F, 0.12F, -0.25F, 0, 0, 100)));

	// The spear keeps vanilla's stab and its kinetic charge: this is only the pose a mob holds while it
	// warns (the spear drawn back) and a player's charged blow, which vanilla has no pose for. It has the one
	// blow: whatever its warning wound up, vanilla's stab is what comes after it.
	static final Motion LANZA = motion("lanza", Hands.ONE, 0.3F, 0.5F, 0.6F,
		blow(k(20, -88, 0, 2, -4, 36, 0), k(-5, -55, 0, 35, 7, -25, 12),
			h(-0.2F, -0.02F, 0.04F, 0, 0, -35), h(-0.22F, 0.12F, -0.28F, 35, 0, -75)),
		blow(k(15, -88, 0, 2, -3, 30, 0), k(-5, -62, 0, 28, 5.5F, -20, 8),
			h(-0.2F, 0.0F, 0.02F, 0, 0, -30), h(-0.2F, 0.1F, -0.2F, 35, 0, -70)));

	// Fists, left and right in turn with the other one up at the chin: a straight punch, a hook swung round
	// from the side, and an uppercut from below. The finisher is the uppercut with the whole body behind it.
	private static final Blow UPPERCUT = blow(k(20, -85, 0, 5, -1, 30, 8), k(-10, 50, 180, 40, 3, -30, -8),
		h(-0.05F, -0.1F, 0.06F, 0, 0, 10), h(-0.25F, 0.3F, -0.18F, 0, 0, 25));
	static final Motion GUANTELETES = motion("guanteletes", Hands.FISTS, 0.2F, 0.42F, 0.5F,
		UPPERCUT.stronger(1.3F),
		blow(k(-5, -50, 0, 40, -2, 20, 0), k(-8, 0, 0, 90, 4, -25, 4),
			h(-0.05F, 0.02F, 0.08F, 0, 0, 0), h(-0.25F, 0.15F, -0.25F, 0, 0, 5)),
		blow(k(70, -20, 0, 70, -1.5F, 35, 0), k(10, -5, 0, 85, 1.5F, 0, 3), k(-35, 0, 0, 90, 2.5F, -35, 5),
			h(0.12F, 0.02F, 0.1F, 0, 0, 0), h(-0.38F, 0.14F, -0.16F, 0, 0, 5)),
		UPPERCUT);

	// A staff: a two-handed jab with the crescent at head height, a swing round like a quarterstaff's, the
	// same swing back the other way, and a blow straight down from overhead. The finisher is the swing round,
	// with more body in it.
	private static final Blow BACULO_SWING = blow(k(70, 10, 160, 30, -1, 35, 0), k(-60, -20, 30, 20, 2, -40, 6),
		h(-0.15F, 0.15F, 0.05F, 0, -45, 5), h(-0.35F, 0.05F, -0.05F, 175, -85, 0));
	static final Motion BACULO = motion("baculo", Hands.TWO, 0.3F, 0.52F, 0.6F,
		BACULO_SWING.stronger(1.3F),
		blow(k(15, -88, 0, 2, -2.5F, 22, 0), k(-5, -35, 0, 55, 4, -15, 6),
			h(-0.15F, 0.0F, 0.08F, 0, 0, -10), h(-0.25F, 0.15F, -0.2F, 35, 0, -55)),
		BACULO_SWING,
		blow(k(-60, 10, -160, 30, -1, -35, 0), k(65, -20, -30, 20, 2, 40, 6),
			h(-0.38F, 0.12F, 0.0F, 175, -85, 0), h(-0.08F, 0.12F, -0.05F, 0, -45, 5)),
		blow(k(5, 120, 180, -20, -1.5F, 10, -10), k(-5, -35, 0, 55, 3, -6, 20),
			h(-0.25F, 0.28F, 0.04F, 0, 5, 10), h(-0.3F, -0.05F, -0.1F, 40, 5, -50)));

	// The tome, slapped forward one-handed from up by the shoulder, brought down from overhead, or swung
	// back across as a backhand. The finisher is the blow from overhead, harder.
	private static final Blow GRIMORIO_OVERHEAD = blow(k(10, 100, 180, -10, -1, 10, -8), k(-5, -40, 0, 50, 3, -8, 16),
		h(-0.25F, 0.3F, 0.05F, 0, 0, 10), h(-0.32F, 0.0F, -0.12F, 0, 0, -60));
	static final Motion GRIMORIO = motion("grimorio", Hands.ONE, 0.25F, 0.45F, 0.55F,
		GRIMORIO_OVERHEAD.stronger(1.3F),
		blow(k(45, 40, 160, 45, -1, 22, -3), k(-10, -5, 0, 85, 3, -18, 6),
			h(-0.15F, 0.2F, -0.05F, 30, -15, 5), h(-0.35F, 0.12F, -0.15F, 70, 10, -20)),
		GRIMORIO_OVERHEAD,
		blow(k(-55, 15, -150, 30, -1, -28, 0), k(65, -5, 10, 60, 2.5F, 28, 4),
			h(-0.42F, 0.16F, 0.0F, -40, 20, 0), h(-0.02F, 0.12F, -0.1F, 40, -20, -10)));

	// The tools get a light version and one blow each: a small peck for the pick, a scoop for the shovel,
	// a short chop pulled back for the hoe. They are for work; the finisher is only the same, harder.
	private static final Blow PICO_BLOW = blow(k(10, 50, 175, 40, 0, 10, -3), k(0, -35, 0, 55, 1, -8, 8),
		h(-0.15F, 0.15F, -0.05F, 0, -5, -5), h(-0.22F, -0.05F, -0.08F, 35, 5, -40));
	static final Motion PICO = motion("pico", Hands.ONE, 0.3F, 0.55F, 0.6F, PICO_BLOW.stronger(1.3F), PICO_BLOW);

	private static final Blow PALA_BLOW = blow(k(10, -88, 0, 2, -1.5F, 12, 0), k(0, -45, 0, 45, 2, -8, 8),
		h(-0.15F, 0.0F, 0.0F, 0, 0, -15), h(-0.25F, 0.12F, -0.15F, 0, 0, 15));
	static final Motion PALA = motion("pala", Hands.ONE, 0.3F, 0.55F, 0.6F, PALA_BLOW.stronger(1.3F), PALA_BLOW);

	private static final Blow AZADA_BLOW = blow(k(10, 55, 175, 35, 0, 10, -3), k(0, -50, 0, 40, -1.5F, -6, 10),
		h(-0.12F, 0.22F, 0.03F, 0, -5, 8), h(-0.18F, -0.02F, 0.05F, 0, 5, -55));
	static final Motion AZADA = motion("azada", Hands.ONE, 0.3F, 0.55F, 0.62F, AZADA_BLOW.stronger(1.3F), AZADA_BLOW);

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
