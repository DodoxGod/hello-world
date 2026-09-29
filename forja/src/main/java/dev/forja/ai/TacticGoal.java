package dev.forja.ai;

import java.util.EnumSet;

import dev.forja.combat.AttackTokens;
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
	/** Closer than this a creeper lights its fuse whether or not its brain asked. */
	static final double FUSE_ANYWAY = 2.0;
	/** Once the fuse has burnt this long, it only goes out beyond 9 blocks, not 7. */
	static final int FUSE_COMMIT = 15;
	public static final double WAIT_MIN = 4.0;
	/** How much of the ring a mob going round to its slot covers at a time, in radians (50 degrees). */
	public static final double ARC_STEP = Math.toRadians(50.0);
	/** How far ahead the waypoint to a distant ring slot is set (toRing). */
	public static final double WAYPOINT = 12.0;
	public static final double WAIT_MAX = 6.0;
	public static final double RETREAT_DISTANCE = 6.0;
	public static final double DUEL_WATCH_MIN = 7.0;
	public static final double DUEL_WATCH_MAX = 9.0;
	public static final int MELEE_COOLDOWN = 20;
	public static final int BOW_DRAW = 20;
	public static final int BOW_COOLDOWN = 20;
	/** A crossbow's full charge (vanilla's is 25 ticks without Quick Charge), with a little to spare. */
	public static final int CROSSBOW_DRAW = 27;
	/** The blaze's burst: three fireballs, six ticks apart, then 60 ticks before the next (vanilla's rhythm). */
	public static final int BLAZE_BURST = 3;
	public static final int BLAZE_BURST_GAP = 6;
	public static final int BLAZE_COOLDOWN = 60;
	/** red_mob_v3. CEBO: how far past the ally it runs. RELEVO: how far off, how far round (radians) it steps. */
	public static final double BAIT_PAST = 2.5;
	public static final double RELAY_RADIUS = 5.0;
	public static final double RELAY_SWING = 0.5;
	/** OCULTARSE: how far behind an ally it stands. EMPUJAR: how far out on the player's far side from the danger. */
	public static final double SHADOW_BEHIND = 1.5;
	public static final double PUSH_SIDE = 1.5;

	private final Mob mob;
	private final MobMind mind;
	private int repath;
	/** Who the blow being wound up is for: the player whose turn it holds. */
	private Player struck;
	/** Whether it was following the trail to the last known position last tick (HonestPerception). */
	private boolean trailing;

	public TacticGoal(Mob mob, MobMind mind) {
		this.mob = mob;
		this.mind = mind;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return this.mind.target != null && this.mind.target.isAlive()
			&& (this.mind.networked || this.mind.decision.tactic() != Tactic.ACERCARSE
				|| HonestPerception.lost(this.mind, this.mob.level().getGameTime()));
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
		this.trailing = false;
		if (this.mob.isUsingItem()) {
			this.mob.stopUsingItem();
		}
		// A blow still being wound up is dropped with its turn. Left alone it stayed paused in the mind
		// and landed, with no warning, on whoever the mob took on next.
		if (this.mind.windup > 0) {
			this.mind.windup = 0;
			AttackTokens.release(this.struck, this.mob);
		}
		this.struck = null;
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
		long now = this.mob.level().getGameTime();
		boolean lost = HonestPerception.lost(this.mind, now);
		if (lost != this.trailing) {
			// A fresh path either way: the one it had led to the real player, or to where it last saw them.
			this.trailing = lost;
			this.repath = 0;
			this.mob.getNavigation().stop();
		}
		if (!lost) {
			this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}
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
			if (asked > 0 && !lost && this.mind.windup == 0 && this.mind.specials.start(asked - 1, target)) {
				return;
			}
		}
		// A blow being wound up is finished before anything else: that is what makes it fair.
		if (this.mind.windup > 0 && this.struck != target) {
			this.retarget(target);
		}
		if (this.mind.windup > 0) {
			this.tickWindup(target);
			return;
		}
		if (lost) {
			this.toLastKnown();
			return;
		}
		// A blaze on its own network (red_blaze_v1) flies and fires by its own executor, in 3D.
		if (this.mind.networked && this.mind.blaze != null) {
			BlazePilot.tick(this.mob, this.mind, target);
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
			case RODEAR -> this.toRing(target, Double.isNaN(this.mind.ringAngle) ? this.currentAngle(target) : this.mind.ringAngle,
				Reach.outside(target, this.mind.ringRadius), 1.0);
			case FLANQUEAR -> this.toRing(target, this.behindAngle(target), Math.max(FLANK_RADIUS, Reach.standOff(this.mob, target)), 1.15);
			case ESPERAR -> this.waitOnRing(target);
			case RETIRARSE -> this.retreat(target);
			case REAGRUPARSE -> this.regroup(target);
			case CUBRIRSE -> this.cover(target);
			case PARAPETARSE -> this.parapet(target);
			case CEBO -> this.bait(target);
			case RELEVO -> this.toRing(target, this.currentAngle(target) + this.side() * RELAY_SWING,
				Math.max(Reach.outside(target, RELAY_RADIUS), Reach.standOff(this.mob, target)), 1.0);
			case OCULTARSE -> this.hide(target);
			case EMPUJAR -> this.pushTowardsDanger(decision, target);
		}
		if (decision.tactic() != Tactic.LIBRE && decision.tactic() != Tactic.ACERCARSE && decision.use()) {
			this.use(target, true);
		}
	}

	// --- The simulator's low-level controls ---------------------------------------------------------

	private void free(Decision decision, Player target) {
		double speed = this.mind.draw > 0 ? 0.5 : 1.0;
		this.move(this.reachMove(decision, target), target, speed);
		if (decision.jump()) {
			this.jump(target);
		}
		this.use(target, decision.use());
	}

	/**
	 * Where the weapon puts a mob that means to strike (usar): one whose weapon reaches further than its
	 * body stops walking in once the player is within that reach, and holds there to strike, instead of
	 * walking up into their face; a spear or a lance inside the shortest reach of its point backs off
	 * to get it. Anything else goes where it was told: with a vanilla weapon, always.
	 */
	private int reachMove(Decision decision, Player target) {
		int move = decision.move();
		MobFamily family = MobFamily.executor(this.mob);
		if (!decision.use() || family == MobFamily.ARQUERO || family == MobFamily.CREEPER) {
			return move;
		}
		if (Reach.min(this.mob) > 0.0 && Reach.tooClose(this.mob, target)) {
			return 5;
		}
		return move == 1 && Reach.closeEnough(this.mob, target) ? 0 : move;
	}

	/** mover: 0 still, 1 towards, 5 away, the others fixed directions around "towards". */
	private void move(int move, Player target, double speed) {
		double dx = target.getX() - this.mob.getX();
		double dz = target.getZ() - this.mob.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double ux = dx / d;
		double uz = dz / d;
		if (this.mob instanceof Creeper creeper && creeper.getSwellDir() > 0) {
			// lit: on at the player at half speed, whatever the network asked (it is masked from moving anyway)
			if (d > 1.5) {
				this.pathTo(target.getX(), target.getY(), target.getZ(), 0.5);
			} else {
				this.mob.getNavigation().stop();
			}
			return;
		}
		if (move == 0) {
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
		if (MobFamily.executor(this.mob) == MobFamily.ARANA && d >= 2.0 && d <= 4.0 && this.mob.onGround()) {
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
		switch (MobFamily.executor(this.mob)) {
			case ARQUERO -> this.bow(target, use);
			case CREEPER -> this.fuse(target, use);
			case BLAZE -> this.fireballs(target, use);
			default -> {
				if (use) {
					this.strike(target);
				}
			}
		}
	}

	/** A melee blow, always with its warning: the same telegraph as vanilla's mobs get from Forja. */
	private void strike(Player target) {
		// Within the weapon's reach, not the body's: a flail or a lance strikes from further off, a fist or a
		// sword from where it always did.
		if (this.mind.cooldown > 0 || !Reach.reaches(this.mob, target)) {
			return;
		}
		if (!AttackTokens.tryAcquire(target, this.mob, Aggression.maxAttackers(this.mob, target))) {
			return;
		}
		this.mind.windupTotal = MobDefense.windup(this.mob);
		MobDefense.spendCounter(this.mob);
		this.mind.windup = this.mind.windupTotal;
		this.struck = target;
		this.mob.getNavigation().stop();
		CombatFeedback.telegraph(this.mob, this.mind.windupTotal);
	}

	/**
	 * The target changed while a blow was being wound up (another player struck the mob, the squad was
	 * shared out...). The turn the blow was wound up under goes back at once, and the blow carries on
	 * against the new target only if a turn is free there too: nobody takes a blow outside their own
	 * turns, and the one it was meant for is not left waiting on a turn nobody is using.
	 */
	private void retarget(Player target) {
		AttackTokens.release(this.struck, this.mob);
		this.struck = null;
		if (AttackTokens.tryAcquire(target, this.mob, Aggression.maxAttackers(this.mob, target))) {
			this.struck = target;
		} else {
			this.mind.windup = 0;
		}
	}

	private void tickWindup(Player target) {
		this.mob.getNavigation().stop();
		// A feint drops the blow, but only in the first half of the warning.
		if (this.mind.decision.feint() && this.mind.windup > this.mind.windupTotal / 2) {
			this.mind.windup = 0;
			this.mind.cooldown = MELEE_COOLDOWN / 2;
			AttackTokens.release(target, this.mob);
			this.struck = null;
			return;
		}
		if (--this.mind.windup > 0) {
			return;
		}
		this.mob.swing(InteractionHand.MAIN_HAND);
		if (this.mob.distanceTo(target) <= Reach.landing(this.mob, target) && ObsM1.sees(this.mob, target.getX(), target.getEyeY(), target.getZ())
			&& this.mob.level() instanceof ServerLevel level && this.mob.doHurtTarget(level, target)) {
			HopBack.afterHit(this.mob, target);
		}
		this.mind.cooldown = dev.forja.combat.Weight.interval(this.mob, MELEE_COOLDOWN);
		this.mind.lastStrike = this.mob.level().getGameTime();
		AttackTokens.release(target, this.mob);
		this.struck = null;
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
			// On the archer's network with a crossbow (a pillager, a piglin): loaded as vanilla loads it, then fired.
			this.crossbow(target);
			return;
		}
		if (!this.mob.isUsingItem()) {
			this.mob.startUsingItem(hand);
		}
		if (++this.mind.draw >= BOW_DRAW) {
			// Covering fire, as the rules' bow goal does it: drawn and one of its own in the way, it holds
			// the arrow until the line is clear instead of putting it in its friend's back.
			if (Squad.allyInLine(this.mob, target)) {
				this.mind.draw = BOW_DRAW;
				Squad.stepToClearLine(this.mob, target);
				return;
			}
			this.mob.stopUsingItem();
			((RangedAttackMob) this.mob).performRangedAttack(target, BowItem.getPowerForTime(this.mind.draw));
			this.mind.draw = 0;
			this.mind.cooldown = BOW_COOLDOWN;
		}
	}

	/**
	 * The archer's "usar" with a crossbow in the hand instead of a bow: draw it until it is loaded (the item loads
	 * itself when let go at full charge, as in vanilla's RangedCrossbowAttackGoal), then fire it the way the mob
	 * fires it by the rules. A friend in the line holds the loaded bolt, as the bow holds the arrow.
	 */
	private void crossbow(Player target) {
		InteractionHand hand = this.mob.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem ? InteractionHand.MAIN_HAND
			: this.mob.getOffhandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem ? InteractionHand.OFF_HAND : null;
		if (hand == null) {
			return;
		}
		net.minecraft.world.item.ItemStack held = this.mob.getItemInHand(hand);
		if (!net.minecraft.world.item.CrossbowItem.isCharged(held)) {
			if (!this.mob.isUsingItem()) {
				this.mob.startUsingItem(hand);
			}
			if (++this.mind.draw >= CROSSBOW_DRAW) {
				this.mob.releaseUsingItem();
				this.mind.draw = 0;
			}
			return;
		}
		if (Squad.allyInLine(this.mob, target)) {
			Squad.stepToClearLine(this.mob, target);
			return;
		}
		((RangedAttackMob) this.mob).performRangedAttack(target, 1.0F);
		this.mind.cooldown = BOW_COOLDOWN;
	}

	/**
	 * The blaze's "usar" when the rules send it into a tactic (docs/red_mob_blaze.md): a burst of three small
	 * fireballs a few ticks apart, as vanilla's blaze throws them, then a long wait. Within 16 and in sight to start.
	 * The burst itself is BlazePilot.burst, shared with the blaze's own network (red_blaze_v1).
	 */
	private void fireballs(Player target, boolean use) {
		boolean inBurst = this.mind.draw > 0;
		if (!inBurst && (!use || this.mind.cooldown > 0 || this.mob.distanceTo(target) >= 16.0
			|| !ObsM1.sees(this.mob, target.getX(), target.getEyeY(), target.getZ()))) {
			return;
		}
		BlazePilot.burst(this.mob, this.mind, target, false, false);
	}

	/**
	 * Light the fuse within 3 blocks and in sight when asked; lit, it burns on while within 7 and in sight.
	 * Right up against the player it lights whatever was asked: a creeper that hugs you and waits is no threat
	 * (Andy, 2026-09-29).
	 */
	private void fuse(Player target, boolean use) {
		if (!(this.mob instanceof Creeper creeper)) {
			return;
		}
		double d = this.mob.distanceTo(target);
		boolean sees = ObsM1.sees(this.mob, target.getX(), target.getY() + 1.5, target.getZ());
		if (creeper.getSwellDir() > 0) {
			// a fuse well under way is not given up for a step back (as the rules' SwellGoalMixin)
			double reach = ((dev.forja.mixin.CreeperAiAccess) creeper).forja$swell() >= FUSE_COMMIT ? 9.0 : 7.0;
			if (d >= reach || !sees) {
				creeper.setSwellDir(-1);
			}
		} else if ((use && d < 3.0 || d < FUSE_ANYWAY) && sees) {
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
		// Round, not through: a slot on the far side is reached along the ring, a step of the arc at a time,
		// instead of by walking straight at the player and into the blade.
		double current = this.currentAngle(target);
		double turn = Squad.wrap(angle - current);
		if (Math.abs(turn) > ARC_STEP) {
			angle = current + Math.signum(turn) * ARC_STEP;
			radius = Math.max(radius, Math.min(this.mob.distanceTo(target), radius + 1.5));
		}
		double x = target.getX() + Math.cos(angle) * radius;
		double z = target.getZ() + Math.sin(angle) * radius;
		if (this.mob.distanceToSqr(x, this.mob.getY(), z) < 0.5) {
			this.mob.getNavigation().stop();
			return;
		}
		// A point far off is gone to by a waypoint 12 blocks along the way: a long path to a spot beside a player
		// on the move comes back empty or too late.
		double dx = x - this.mob.getX();
		double dz = z - this.mob.getZ();
		double far = Math.sqrt(dx * dx + dz * dz);
		if (far > WAYPOINT) {
			x = this.mob.getX() + dx / far * WAYPOINT;
			z = this.mob.getZ() + dz / far * WAYPOINT;
		}
		if (!this.pathTo(x, target.getY(), z, speed) || this.mob.getNavigation().isDone()) {
			// No path to a point beside a player on the move (it is gone before the path is): straight at it,
			// as a mob walks to the last step of any path. It stood still here, and never went round a player who
			// was backing away (Andy, 2026-09-29).
			this.mind.pathless++;
			this.mob.getMoveControl().setWantedPosition(x, target.getY(), z, speed);
		}
	}

	/**
	 * Waiting for a turn: out of reach as before, but at its own slot of the ring rather than straight in
	 * front, so the ones waiting stand round the player's sides and back and not in a queue before them.
	 * Out of this player's reach: a flail or a lance in their hand pushes the ring out by what it adds.
	 */
	private void waitOnRing(Player target) {
		if (Duels.watching(this.mob) || Double.isNaN(this.mind.ringAngle)) {
			this.hold(target);
			return;
		}
		this.toRing(target, this.mind.ringAngle, Reach.outside(target, Math.max(this.mind.ringRadius, (WAIT_MIN + WAIT_MAX) / 2.0)), 0.8);
	}

	private void hold(Player target) {
		double d = this.mob.distanceTo(target);
		// A duel's watchers stand round the ring, outside it.
		boolean watching = Duels.watching(this.mob);
		double min = Reach.outside(target, watching ? DUEL_WATCH_MIN : WAIT_MIN);
		double max = Reach.outside(target, watching ? DUEL_WATCH_MAX : WAIT_MAX);
		if (d < min) {
			this.move(5, target, 0.8);
		} else if (d > max) {
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

	// --- red_mob_v3's four ---------------------------------------------------------------------------

	/** The ally fighting the same player nearest to this mob, or null. */
	private Mob nearestAlly(Player target) {
		for (Mob other : ObsM1.allies(this.mob)) {
			if (other.getTarget() == target) {
				return other;
			}
		}
		return null;
	}

	/** Which way this one steps aside: fixed per mob, so a relay does not dither from side to side. */
	private double side() {
		return (this.mob.getId() & 1) == 0 ? 1.0 : -1.0;
	}

	/** CEBO: to the nearest ally and {@link #BAIT_PAST} beyond it, away from the player, drawing them in. */
	private void bait(Player target) {
		Mob ally = this.nearestAlly(target);
		if (ally == null) {
			this.retreat(target);
			return;
		}
		double dx = ally.getX() - target.getX();
		double dz = ally.getZ() - target.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		this.pathTo(ally.getX() + dx / d * BAIT_PAST, ally.getY(), ally.getZ() + dz / d * BAIT_PAST, 1.2);
	}

	/** OCULTARSE: behind a block if one is near, else in the shadow of an ally (the ally between it and the player). */
	private void hide(Player target) {
		long now = this.mob.level().getGameTime();
		if (now - this.mind.coverAt > 20) {
			this.mind.cover = Terrain.cover(this.mob, target);
			this.mind.coverAt = now;
		}
		if (this.mind.cover != null) {
			if (this.mob.distanceToSqr(this.mind.cover) < 0.5) {
				this.mob.getNavigation().stop();
			} else {
				this.pathTo(this.mind.cover.x, this.mind.cover.y, this.mind.cover.z, 1.2);
			}
			return;
		}
		Mob ally = this.nearestAlly(target);
		if (ally == null) {
			this.retreat(target);
			return;
		}
		double dx = ally.getX() - target.getX();
		double dz = ally.getZ() - target.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		this.pathTo(ally.getX() + dx / d * SHADOW_BEHIND, ally.getY(), ally.getZ() + dz / d * SHADOW_BEHIND, 1.1);
	}

	/**
	 * EMPUJAR: round to the player's far side from the danger, {@link #PUSH_SIDE} out, so that its blows (which
	 * push a player towards a danger within 3 blocks: MobAi.pushToDanger) send them in; once there, straight in.
	 */
	private void pushTowardsDanger(Decision decision, Player target) {
		Vec3 danger = Terrain.dangerNear(target);
		if (danger == null) {
			this.free(Decision.APPROACH, target);
			return;
		}
		double x = target.getX() - danger.x * PUSH_SIDE;
		double z = target.getZ() - danger.z * PUSH_SIDE;
		if (this.mob.distanceToSqr(x, this.mob.getY(), z) > 1.0) {
			this.pathTo(x, target.getY(), z, 1.1);
		} else {
			this.pathTo(target.getX(), target.getY(), target.getZ(), 1.0);
		}
	}

	/**
	 * Honest perception (HonestPerception, Andy 2026-09-29): with its player unseen for a second, it walks to where it
	 * last saw them, whatever the brain decided (every tactic is worked out from the real position), and waits there.
	 */
	private void toLastKnown() {
		if (this.mob.isUsingItem() && this.mind.draw > 0) {
			this.mob.stopUsingItem();
			this.mind.draw = 0;
		}
		if (HonestPerception.arrived(this.mind)) {
			this.mob.getNavigation().stop();
			return;
		}
		this.pathTo(this.mind.lastSeen.x, this.mind.lastSeen.y, this.mind.lastSeen.z, 1.0);
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
