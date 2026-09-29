package dev.forja.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** The movement goals of phase 7: following a trail, taking the high ground, and getting through or up. */
public final class MovementGoals {
	private MovementGoals() {
	}

	/**
	 * Idea 60: a mob that lost its player goes to where it last saw them, for up to 10 seconds after,
	 * instead of forgetting at once.
	 */
	public static final class Track extends Goal {
		public static final int MEMORY = 200;
		private final Mob mob;
		private final MobMind mind;

		public Track(Mob mob, MobMind mind) {
			this.mob = mob;
			this.mind = mind;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return this.mob.getTarget() == null && this.mind.lastSeen != null
				&& this.mob.level().getGameTime() - this.mind.lastSeenAt < MEMORY
				&& this.mob.distanceToSqr(this.mind.lastSeen) > 2.0;
		}

		@Override
		public void start() {
			this.mob.getNavigation().moveTo(this.mind.lastSeen.x, this.mind.lastSeen.y, this.mind.lastSeen.z, 1.0);
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse() && !this.mob.getNavigation().isDone();
		}

		@Override
		public void stop() {
			this.mind.lastSeen = null;
		}
	}

	/** Idea 68: with its fight over, a mob with a home goes back to it. */
	public static final class Home extends Goal {
		private final Mob mob;

		public Home(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			net.minecraft.core.BlockPos home = Personality.home(this.mob);
			return this.mob.getTarget() == null && home != null && this.mob.getRandom().nextInt(
				this.mob.blockPosition().distSqr(home) > 16.0 * 16.0 ? 40 : PATROL_EVERY) == 0;
		}

		/** Far from home: back to it. Near it: a round of its ground, to a point 8 to 12 blocks out (idea 97). */
		@Override
		public void start() {
			net.minecraft.core.BlockPos home = Personality.home(this.mob);
			if (home == null) {
				return;
			}
			if (this.mob.blockPosition().distSqr(home) > 16.0 * 16.0) {
				this.mob.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.9);
			} else {
				double a = this.mob.getRandom().nextDouble() * Math.PI * 2.0;
				double r = 8.0 + this.mob.getRandom().nextDouble() * 4.0;
				this.mob.getNavigation().moveTo(home.getX() + 0.5 + Math.cos(a) * r, home.getY(), home.getZ() + 0.5 + Math.sin(a) * r, 0.7);
			}
		}

		public static final int PATROL_EVERY = 200;

		@Override
		public boolean canContinueToUse() {
			return this.mob.getTarget() == null && !this.mob.getNavigation().isDone();
		}
	}

	/** Idea 70: a forge worked at night draws the idle ones near to come and look. */
	public static final class Curious extends Goal {
		private final Mob mob;
		private Vec3 noise;

		public Curious(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (this.mob.getTarget() != null) {
				return false;
			}
			this.noise = Personality.heard(this.mob);
			return this.noise != null && this.mob.distanceToSqr(this.noise) > 9.0;
		}

		@Override
		public void start() {
			this.mob.getNavigation().moveTo(this.noise.x, this.noise.y, this.noise.z, 0.9);
		}

		@Override
		public boolean canContinueToUse() {
			return this.mob.getTarget() == null && !this.mob.getNavigation().isDone();
		}
	}

	/** Idea 51: an archer that can climb 2 or more blocks higher nearby does, and shoots from there. */
	public static final class HighGround extends Goal {
		public static final int EVERY = 100;

		/** Whether a player has a bow or a crossbow up. */
		static boolean aiming(Player player) {
			return player.isUsingItem() && (player.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem
				|| player.getUseItem().getItem() instanceof net.minecraft.world.item.CrossbowItem);
		}
		private final Mob mob;
		private final MobMind mind;
		private long nextLook = Long.MIN_VALUE;
		private Vec3 spot;
		private int left;

		public HighGround(Mob mob, MobMind mind) {
			this.mob = mob;
			this.mind = mind;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			long now = this.mob.level().getGameTime();
			if (this.nextLook == Long.MIN_VALUE) {
				// Not straight away: a fresh archer shoots first and looks for a better spot later.
				this.nextLook = now + 2 * EVERY;
			}
			// Not while it is drawing itself (it would drop the shot), and not while the player is drawing on it
			// (the spec: high ground is sought when the player is not aiming - a climbing archer is an easy mark).
			if (this.mind.networked || !(this.mob.getTarget() instanceof Player player) || now < this.nextLook
				|| this.mob.isUsingItem() || aiming(player) || this.mob.distanceTo(player) > 16.0 || this.mob.distanceTo(player) < 8.0) {
				return false;
			}
			this.nextLook = now + EVERY;
			this.spot = Terrain.highGround(this.mob);
			return this.spot != null;
		}

		@Override
		public void start() {
			this.left = 60;
			this.mob.getNavigation().moveTo(this.spot.x, this.spot.y, this.spot.z, 1.1);
		}

		@Override
		public boolean canContinueToUse() {
			return --this.left > 0 && !this.mob.getNavigation().isDone();
		}
	}
}
