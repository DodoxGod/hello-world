package dev.forja.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
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
			if (this.mind.networked || !(this.mob.getTarget() instanceof Player player) || now < this.nextLook
				|| this.mob.isUsingItem() || this.mob.distanceTo(player) > 16.0 || this.mob.distanceTo(player) < 8.0) {
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

	/**
	 * Ideas 54 and 55: a zombie that cannot reach its player gets through soft blocks in its way (after a
	 * moment of digging), and if the player stands high above, builds itself a pillar of dirt to them.
	 * Only where mobs may change blocks; whatever it places is taken away again after 10 seconds.
	 */
	public static final class Builder extends Goal {
		public static final int DIG_TICKS = 20;
		public static final int MAX_PILLAR = 4;
		public static final int BLOCK_LIFE = 200;
		private final Mob mob;
		private int digging;
		private BlockPos dig;
		private int placed;
		private long lastPlace;

		public Builder(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (!(this.mob.getTarget() instanceof Player player) || !(this.mob.level() instanceof ServerLevel level) || !Terrain.griefing(level)) {
				return false;
			}
			return this.mob.getNavigation().isStuck() || this.needsPillar(player) || this.softAhead(player) != null;
		}

		private boolean needsPillar(Player player) {
			double flat = Math.hypot(player.getX() - this.mob.getX(), player.getZ() - this.mob.getZ());
			return player.getY() - this.mob.getY() >= 2.5 && flat <= 3.0 && this.placed < MAX_PILLAR;
		}

		private BlockPos softAhead(Player player) {
			Vec3 dir = new Vec3(player.getX() - this.mob.getX(), 0.0, player.getZ() - this.mob.getZ());
			if (dir.lengthSqr() < 1.0E-6) {
				return null;
			}
			dir = dir.normalize();
			for (double up : new double[] {0.5, 1.5}) {
				BlockPos pos = BlockPos.containing(this.mob.getX() + dir.x * 0.9, this.mob.getY() + up, this.mob.getZ() + dir.z * 0.9);
				if (Terrain.soft(this.mob.level().getBlockState(pos))) {
					return pos;
				}
			}
			return null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.mob.getTarget() instanceof Player && (this.digging > 0 || this.canUse());
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (!(this.mob.getTarget() instanceof Player player) || !(this.mob.level() instanceof ServerLevel level)) {
				return;
			}
			this.mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
			if (this.needsPillar(player)) {
				// Jump, and once clear of the ground put a block where the feet were.
				if (this.mob.onGround()) {
					this.mob.getJumpControl().jump();
				} else if (this.mob.getDeltaMovement().y < 0.1 && level.getGameTime() - this.lastPlace > 8) {
					BlockPos below = BlockPos.containing(this.mob.getX(), this.mob.getY() - 0.5, this.mob.getZ());
					if (level.getBlockState(below).isAir() && this.mob.getY() - below.getY() >= 0.9) {
						level.setBlockAndUpdate(below, Blocks.DIRT.defaultBlockState());
						TemporaryBlocks.add(level, below, Blocks.DIRT, BLOCK_LIFE);
						this.placed++;
						this.lastPlace = level.getGameTime();
					}
				}
				return;
			}
			BlockPos soft = this.dig != null ? this.dig : this.softAhead(player);
			if (soft == null) {
				this.digging = 0;
				return;
			}
			if (this.dig == null) {
				this.dig = soft;
				this.digging = DIG_TICKS;
			}
			this.mob.getNavigation().stop();
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(this.dig)),
				this.dig.getX() + 0.5, this.dig.getY() + 0.5, this.dig.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
			if (--this.digging <= 0) {
				level.destroyBlock(this.dig, true, this.mob);
				this.dig = null;
			}
		}

		@Override
		public void stop() {
			this.dig = null;
			this.digging = 0;
			if (this.mob.level().getGameTime() - this.lastPlace > BLOCK_LIFE) {
				this.placed = 0;
			}
		}

		public static boolean builds(Mob mob) {
			return mob instanceof Zombie;
		}
	}
}
