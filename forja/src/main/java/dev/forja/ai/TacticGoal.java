package dev.forja.ai;

import java.util.EnumSet;

import dev.forja.combat.AttackTokens;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.CombatFeedback;
import dev.forja.combat.Posture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The executor: does what the mob's brain decided, the same way whether the decision came from a
 * network or from the rules. It runs above vanilla's attack goals and holds movement, looking and
 * jumping while it does, so a network-driven mob is never pulled two ways; with the rule brain it only
 * steps in for the tactics vanilla has no goal for (circling, flanking, waiting, retreating...).
 *
 * <p>Every number here is in docs/COMBATE_ESPECIFICACION.md ("Tácticas" and "Controles de la red"),
 * because the simulator implements the same executor.
 */
public final class TacticGoal extends Goal {
	public static final double RING_RADIUS = 3.5;
	public static final double FLANK_RADIUS = 2.5;
	public static final double WAIT_MIN = 4.0;
	public static final double WAIT_MAX = 6.0;
	public static final double RETREAT_DISTANCE = 6.0;
	public static final int MELEE_COOLDOWN = 20;
	public static final int BOW_DRAW = 20;
	public static final int BOW_COOLDOWN = 20;

	private final Mob mob;
	private final MobMind mind;
	private int repath;

	public TacticGoal(Mob mob, MobMind mind) {
		this.mob = mob;
		this.mind = mind;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return this.mind.target != null && this.mind.target.isAlive()
			&& (this.mind.networked || this.mind.decision.tactic() != Tactic.ACERCARSE);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		if (this.mob.isUsingItem()) {
			this.mob.stopUsingItem();
		}
		this.mind.draw = 0;
		if (this.mob instanceof Creeper creeper && this.mind.networked) {
			creeper.setSwellDir(-1);
		}
	}

	@Override
	public void tick() {
		Player target = this.mind.target;
		if (target == null) {
			return;
		}
		if (this.mind.cooldown > 0) {
			this.mind.cooldown--;
		}
		this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		long now = this.mob.level().getGameTime();
		if (Posture.isStaggered(this.mob, now)) {
			this.mind.windup = 0;
			this.mob.getNavigation().stop();
			return;
		}
		// A special under way, or one the network asks for, comes first (the runner warns before it strikes).
		if (this.mind.specials != null && this.mind.networked) {
			if (this.mind.specials.active()) {
				this.mind.specials.tick();
				return;
			}
			int asked = this.mind.decision.special();
			if (asked > 0 && this.mind.windup == 0 && this.mind.specials.start(asked - 1, target)) {
				return;
			}
		}
		// A blow being wound up is finished before anything else: that is what makes it fair.
		if (this.mind.windup > 0) {
			this.tickWindup(target);
			return;
		}
		Decision decision = this.mind.decision;
		// Defense: shield up (held while asked), or a dodge to the side.
		if (decision.defense() == 1 || decision.tactic() == Tactic.CUBRIRSE) {
			MobDefense.raise(this.mob);
		} else if (this.mind.draw == 0 && this.mob.isUsingItem() && this.mob.getUsedItemHand() == InteractionHand.OFF_HAND) {
			this.mob.stopUsingItem();
		}
		if (decision.defense() == 2) {
			MobDefense.dodge(this.mob, target);
		}
		switch (decision.tactic()) {
			case LIBRE, ACERCARSE -> this.free(decision, target);
			case RODEAR -> this.toRing(target, Double.isNaN(this.mind.ringAngle) ? this.currentAngle(target) : this.mind.ringAngle, RING_RADIUS, 1.0);
			case FLANQUEAR -> this.toRing(target, this.behindAngle(target), FLANK_RADIUS, 1.15);
			case ESPERAR -> this.hold(target);
			case RETIRARSE -> this.retreat(target);
			case REAGRUPARSE -> this.regroup(target);
			case CUBRIRSE -> this.cover(target);
			case PARAPETARSE -> this.parapet(target);
		}
		if (decision.tactic() != Tactic.LIBRE && decision.tactic() != Tactic.ACERCARSE && decision.use()) {
			this.use(target, true);
		}
	}

	// --- The simulator's low-level controls ---------------------------------------------------------

	private void free(Decision decision, Player target) {
		double speed = this.mind.draw > 0 ? 0.5 : 1.0;
		this.move(decision.move(), target, speed);
		if (decision.jump()) {
			this.jump(target);
		}
		this.use(target, decision.use());
	}

	/** mover: 0 still, 1 towards, 5 away, the others fixed directions around "towards". */
	private void move(int move, Player target, double speed) {
		double dx = target.getX() - this.mob.getX();
		double dz = target.getZ() - this.mob.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double ux = dx / d;
		double uz = dz / d;
		if (move == 0 || this.mob instanceof Creeper creeper && creeper.getSwellDir() > 0) {
			this.mob.getNavigation().stop();
			return;
		}
		if (move == 1) {
			if (d > 0.9) {
				this.pathTo(target.getX(), target.getY(), target.getZ(), speed);
			} else {
				this.mob.getNavigation().stop();
			}
			return;
		}
		if (move == 5) {
			this.pathTo(this.mob.getX() - ux * 4.0, this.mob.getY(), this.mob.getZ() - uz * 4.0, speed);
			return;
		}
		double[] dir = ObsM1.direction(move, ux, uz);
		this.mob.getNavigation().stop();
		// Never a step into lava or off a drop (idea 52).
		if (Terrain.danger(this.mob.level(), this.mob.getX() + dir[0] * 1.5, this.mob.getZ() + dir[1] * 1.5, this.mob.getY())) {
			return;
		}
		this.mob.getMoveControl().setWantedPosition(this.mob.getX() + dir[0] * 2.0, this.mob.getY(), this.mob.getZ() + dir[1] * 2.0, speed);
	}

	private void jump(Player target) {
		boolean inWater = this.mob.isInWater();
		if (!this.mob.onGround() && !inWater) {
			return;
		}
		double d = this.mob.distanceTo(target);
		if (MobFamily.of(this.mob) == MobFamily.ARANA && d >= 2.0 && d <= 4.0 && this.mob.onGround()) {
			// The spider's leap, as vanilla's LeapAtTargetGoal throws it.
			Vec3 motion = this.mob.getDeltaMovement();
			Vec3 flat = new Vec3(target.getX() - this.mob.getX(), 0.0, target.getZ() - this.mob.getZ());
			if (flat.lengthSqr() > 1.0E-7) {
				flat = flat.normalize().scale(0.4).add(motion.scale(0.2));
			}
			this.mob.setDeltaMovement(flat.x, 0.4, flat.z);
			return;
		}
		this.mob.getJumpControl().jump();
	}

	/** usar: the family's basic attack. */
	private void use(Player target, boolean use) {
		switch (MobFamily.of(this.mob)) {
			case ARQUERO -> this.bow(target, use);
			case CREEPER -> this.fuse(target, use);
			default -> {
				if (use) {
					this.strike(target);
				}
			}
		}
	}

	/** A melee blow, always with its warning: the same telegraph as vanilla's mobs get from Forja. */
	private void strike(Player target) {
		if (this.mind.cooldown > 0 || !ObsM1.reaches(this.mob, target)) {
			return;
		}
		if (!AttackTokens.tryAcquire(target, this.mob, Aggression.maxAttackers(target))) {
			return;
		}
		this.mind.windupTotal = MobDefense.windup(this.mob);
		MobDefense.spendCounter(this.mob);
		this.mind.windup = this.mind.windupTotal;
		this.mob.getNavigation().stop();
		CombatFeedback.telegraph(this.mob);
	}

	private void tickWindup(Player target) {
		this.mob.getNavigation().stop();
		// A feint drops the blow, but only in the first half of the warning.
		if (this.mind.decision.feint() && this.mind.windup > this.mind.windupTotal / 2) {
			this.mind.windup = 0;
			this.mind.cooldown = MELEE_COOLDOWN / 2;
			AttackTokens.release(target, this.mob);
			return;
		}
		if (--this.mind.windup > 0) {
			return;
		}
		this.mob.swing(InteractionHand.MAIN_HAND);
		double allowed = this.mob.getBbWidth() * 2.0 + target.getBbWidth() * 0.5 + CombatConfig.get().strikeReachBonus;
		if (this.mob.distanceTo(target) <= allowed && ObsM1.sees(this.mob, target.getX(), target.getEyeY(), target.getZ())
			&& this.mob.level() instanceof ServerLevel level) {
			this.mob.doHurtTarget(level, target);
		}
		this.mind.cooldown = MELEE_COOLDOWN;
		this.mind.lastStrike = this.mob.level().getGameTime();
		AttackTokens.release(target, this.mob);
	}

	/** Draw while asked, seeing the target within 16 blocks; at 20 ticks, loose. Letting go early unstrings it. */
	private void bow(Player target, boolean use) {
		boolean able = use && this.mind.cooldown <= 0 && this.mob.distanceTo(target) < 16.0
			&& ObsM1.sees(this.mob, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ())
			&& this.mob instanceof RangedAttackMob;
		if (!able) {
			if (this.mind.draw > 0) {
				this.mob.stopUsingItem();
				this.mind.draw = 0;
			}
			return;
		}
		InteractionHand hand = this.mob.getMainHandItem().getItem() instanceof BowItem ? InteractionHand.MAIN_HAND
			: this.mob.getOffhandItem().getItem() instanceof BowItem ? InteractionHand.OFF_HAND : null;
		if (hand == null) {
			return;
		}
		if (!this.mob.isUsingItem()) {
			this.mob.startUsingItem(hand);
		}
		if (++this.mind.draw >= BOW_DRAW) {
			this.mob.stopUsingItem();
			((RangedAttackMob) this.mob).performRangedAttack(target, BowItem.getPowerForTime(this.mind.draw));
			this.mind.draw = 0;
			this.mind.cooldown = BOW_COOLDOWN;
		}
	}

	/** Light the fuse only within 3 blocks and in sight; lit, it burns on while within 7 and in sight. */
	private void fuse(Player target, boolean use) {
		if (!(this.mob instanceof Creeper creeper)) {
			return;
		}
		double d = this.mob.distanceTo(target);
		boolean sees = ObsM1.sees(this.mob, target.getX(), target.getY() + 1.5, target.getZ());
		if (creeper.getSwellDir() > 0) {
			if (d >= 7.0 || !sees) {
				creeper.setSwellDir(-1);
			}
		} else if (use && d < 3.0 && sees) {
			creeper.setSwellDir(1);
		} else {
			creeper.setSwellDir(-1);
		}
	}

	// --- Tactics --------------------------------------------------------------------------------------

	private double currentAngle(Player target) {
		return Math.atan2(this.mob.getZ() - target.getZ(), this.mob.getX() - target.getX());
	}

	/**
	 * Behind the target: where it faces plus 180°, nudged 30° to one side. The side the player tends to
	 * dodge towards (their habit) if they have one; otherwise the side this mob is already on.
	 */
	private double behindAngle(Player target) {
		double yaw = Math.toRadians(target.getYRot());
		double facing = Math.atan2(Math.cos(yaw), -Math.sin(yaw));
		double behind = facing + Math.PI;
		float habit = PlayerHabits.get(target, PlayerHabits.SIDE);
		double side = Math.abs(habit) > 0.3F ? Math.signum(habit) : Math.sin(this.currentAngle(target) - behind) >= 0.0 ? 1.0 : -1.0;
		return behind + side * Math.PI / 6.0;
	}

	private void toRing(Player target, double angle, double radius, double speed) {
		double x = target.getX() + Math.cos(angle) * radius;
		double z = target.getZ() + Math.sin(angle) * radius;
		if (this.mob.distanceToSqr(x, this.mob.getY(), z) < 0.5) {
			this.mob.getNavigation().stop();
			return;
		}
		this.pathTo(x, target.getY(), z, speed);
	}

	private void hold(Player target) {
		double d = this.mob.distanceTo(target);
		if (d < WAIT_MIN) {
			this.move(5, target, 0.8);
		} else if (d > WAIT_MAX) {
			this.move(1, target, 0.8);
		} else {
			this.mob.getNavigation().stop();
		}
	}

	private void retreat(Player target) {
		double dx = this.mob.getX() - target.getX();
		double dz = this.mob.getZ() - target.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double x = this.mob.getX() + dx / d * RETREAT_DISTANCE;
		double z = this.mob.getZ() + dz / d * RETREAT_DISTANCE;
		if (!this.pathTo(x, this.mob.getY(), z, 1.2) && this.mob instanceof PathfinderMob pathfinder) {
			Vec3 away = DefaultRandomPos.getPosAway(pathfinder, 8, 4, target.position());
			if (away != null) {
				this.pathTo(away.x, away.y, away.z, 1.2);
			}
		}
	}

	private void regroup(Player target) {
		var allies = ObsM1.allies(this.mob);
		double x = 0.0;
		double z = 0.0;
		int n = 0;
		for (Mob ally : allies) {
			if (ally.getTarget() == target && n < 6) {
				x += ally.getX();
				z += ally.getZ();
				n++;
			}
		}
		if (n == 0) {
			this.mob.getNavigation().stop();
			return;
		}
		this.pathTo(x / n, this.mob.getY(), z / n, 1.0);
	}

	private void cover(Player target) {
		if (this.mob.distanceTo(target) > 1.5) {
			this.pathTo(target.getX(), target.getY(), target.getZ(), 0.6);
		} else {
			this.mob.getNavigation().stop();
		}
	}

	/** Behind a block from the player's arrows, if there is one near; back off otherwise. */
	private void parapet(Player target) {
		long now = this.mob.level().getGameTime();
		if (now - this.mind.coverAt > 20) {
			this.mind.cover = Terrain.cover(this.mob, target);
			this.mind.coverAt = now;
		}
		if (this.mind.cover == null) {
			this.retreat(target);
			return;
		}
		if (this.mob.distanceToSqr(this.mind.cover) < 0.5) {
			this.mob.getNavigation().stop();
		} else {
			this.pathTo(this.mind.cover.x, this.mind.cover.y, this.mind.cover.z, 1.2);
		}
	}

	/** A path, refreshed at most every 10 ticks so the pathfinder is not asked every tick. */
	private boolean pathTo(double x, double y, double z, double speed) {
		if (--this.repath > 0 && !this.mob.getNavigation().isDone()) {
			this.mob.getNavigation().setSpeedModifier(speed);
			return true;
		}
		this.repath = 10;
		return this.mob.getNavigation().moveTo(x, y, z, speed);
	}
}
