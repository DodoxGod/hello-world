package dev.forja.ai;

import dev.forja.combat.AttackTokens;
import dev.forja.combat.SwingStyle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;

/**
 * The brain every mob has when there is no network for it, and the fallback when one fails: vanilla's own
 * approach-and-strike, plus the tactics that make a group readable and that read the player
 * (see {@link PlayerHabits} and {@link Aggression}). Later phases add squads and personality to it.
 *
 * <p>Only vanilla's melee monsters take these tactics; archers and creepers keep their own goals, and the
 * mod's own monsters their own AI, until their phases give them one.
 */
public final class RuleBrain {
	/** Mobs closer than this that cannot attack circle instead of pressing in. */
	static final double CIRCLE_RANGE = 6.0;
	/** A mob with a turn further than this round the ring from its slot goes round to it before it strikes. */
	static final double GO_ROUND_ANGLE = Math.toRadians(60.0);
	/** ...unless it is already this close: then it just strikes. */
	static final double GO_ROUND_MIN = 2.5;
	/** A mob below this share of its health backs off, if it has company. */
	static final double RETREAT_HEALTH = 0.2;
	/** Within this distance of a player charging a blow, a mob steps back out of it. */
	static final double CHARGE_RANGE = 4.0;
	/** After a blow, a mob gives up its place in front for this long if others are waiting. */
	static final long RELAY_TICKS = 20;
	/** The flanker is round the back once it is this far (radians) off where the player faces. */
	static final double FLANK_DONE = Math.toRadians(120.0);

	private static boolean hasShield(Mob mob) {
		return mob.getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
	}

	private RuleBrain() {
	}

	/**
	 * The ones that fight from afar by nature - a pillager's crossbow, a witch's bottles, an evoker's fangs,
	 * a blaze's fire. The tactics below are a body's (wait on the ring, circle, press in), and the goal that
	 * carries them out outranks their shooting: given them, a pillager walked round you and never fired.
	 * A drowned is the exception: it throws its trident, but it is a body that fights up close.
	 */
	/** Whether the mob charges with vanilla's spear goal (zombies and the like in 26.2). */
	static boolean hasSpearGoal(Mob mob) {
		for (var wrapped : ((dev.forja.mixin.MobGoalsAccess) mob).forjaGoals().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof net.minecraft.world.entity.ai.goal.SpearUseGoal) {
				return true;
			}
		}
		return false;
	}

	static boolean shootsOrCasts(Mob mob, MobFamily family) {
		return mob instanceof net.minecraft.world.entity.monster.RangedAttackMob && family != MobFamily.CUERPO
			|| mob instanceof net.minecraft.world.entity.monster.illager.SpellcasterIllager
			|| mob instanceof net.minecraft.world.entity.monster.Blaze;
	}

	public static Decision decide(MobMind mind, Player target) {
		Mob mob = mind.mob;
		// A staff or a tome in the hand: its own goal fights with it (entity/ai/CasterGoal), as an archer's
		// bow goal does, and none of the tactics below is a caster's.
		if (dev.forja.entity.ai.CasterGoal.casts(mob)) {
			return Decision.APPROACH;
		}
		MobFamily family = MobFamily.of(mob);
		// The ember wisp never stands and fights: close in and it drifts off, leaving fire behind (idea 45).
		if (mob instanceof dev.forja.entity.EmberWisp && mob.distanceTo(target) < 4.0) {
			return Decision.tactic(Tactic.RETIRARSE);
		}
		if (!(mob instanceof PathfinderMob) || family == MobFamily.ARQUERO || family == MobFamily.CREEPER || shootsOrCasts(mob, family)
			|| !"minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace())) {
			return Decision.APPROACH;
		}
		double distance = mob.distanceTo(target);
		long now = mob.level().getGameTime();
		// A thief runs with what it took (idea 92).
		if (WorldFights.thief(mob)) {
			return Decision.tactic(Tactic.RETIRARSE);
		}
		// A duel: the watchers stand back round the ring; the challenger fights on its own (idea 96).
		if (Duels.watching(mob)) {
			return Decision.tactic(Tactic.ESPERAR);
		}
		if (Duels.challenger(mob) || Duels.mayChallenge(mind, target)) {
			return Decision.APPROACH;
		}
		boolean fearless = Personality.fearless(mob) || Personality.atHome(mob);
		// Fear (65) or a broken group: everyone falls back for a moment; the elites, and those defending
		// their home, never do (66, 68).
		if ((mind.routed || Personality.afraid(mob)) && mind.windup == 0 && !fearless) {
			return Decision.tactic(Tactic.RETIRARSE);
		}
		if (!fearless && mob.getHealth() < mob.getMaxHealth() * Personality.retreatHealth(mob) && ObsM1.allies(mob).size() >= 2 && distance < 8.0) {
			return Decision.tactic(Tactic.RETIRARSE);
		}
		boolean hasTurn = AttackTokens.holds(target, mob) || AttackTokens.free(target, Aggression.maxAttackers(mob, target));
		// A charged blow is coming: shield up if it has one, a dodge if it is right on top, else out of reach
		// (a flail's or a lance's reach, when that is what the player holds).
		if (Aggression.charging(target) && distance < Reach.outside(target, CHARGE_RANGE) && mind.windup == 0 && !AttackTokens.holds(target, mob)) {
			if (MobDefense.hasShield(mob) && !MobDefense.guardBroken(mob)) {
				return Decision.tactic(Tactic.CUBRIRSE);
			}
			if (distance < 3.0 && MobDefense.dodgeReady(mob)) {
				return new Decision(0, false, false, Tactic.RETIRARSE, 0, 2, false);
			}
			return Decision.tactic(Tactic.RETIRARSE);
		}
		// Its balance nearly gone: back off to get it back (idea 35), if it is not alone.
		if (dev.forja.combat.Posture.fill(mob) > 0.7 && ObsM1.allies(mob).size() >= 1 && distance < 6.0 && mind.windup == 0) {
			return Decision.tactic(Tactic.RETIRARSE);
		}
		// Arrows coming (the player drawing a bow): shield bearers cover as they come on (idea 40); the rest
		// take cover behind a block when far (idea 59), or zigzag in when near (idea 58).
		boolean drawing = target.isUsingItem() && target.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem;
		if (drawing && MobDefense.hasShield(mob) && !MobDefense.guardBroken(mob) && distance > 3.0) {
			return Decision.tactic(Tactic.CUBRIRSE);
		}
		if (drawing && distance > 8.0 && mind.cover != null && now - mind.coverAt < 40) {
			return Decision.tactic(Tactic.PARAPETARSE);
		}
		if (drawing && distance > 8.0 && now - mind.coverAt >= 40) {
			mind.cover = Terrain.cover(mob, target);
			mind.coverAt = now;
			if (mind.cover != null) {
				return Decision.tactic(Tactic.PARAPETARSE);
			}
		}
		if (drawing && distance > 3.0) {
			return new Decision((now / 10) % 2 == 0 ? 2 : 8, false, false, Tactic.LIBRE, 0, 0, false);
		}
		// A spear keeps its distance: too close for its point, it steps back (idea 57). A spear, not everything
		// that thrusts: a dagger is at its best right up against you, and backing off with one made no sense.
		// Not for a mob with vanilla's spear goal: that one falls back and charges again by itself, and this rule
		// pulled it back out of every charge it started. Too close is inside the point's own shortest reach
		// (attack_range min_reach, scaled for a monster as vanilla does: 1 block between the boxes) and a little.
		if (SwingStyle.of(mob.getMainHandItem()) == SwingStyle.THRUST && mob.getMainHandItem().has(net.minecraft.core.component.DataComponents.KINETIC_WEAPON)
			&& Reach.tooClose(mob, target) && mind.windup == 0 && !hasSpearGoal(mob)) {
			return new Decision(5, false, false, Tactic.LIBRE, 0, 0, false);
		}
		// An ally lies staggered by the player: close round it (its slot is set to the ally's side).
		if (mind.guarding && !AttackTokens.holds(target, mob)) {
			return Decision.tactic(Tactic.RODEAR);
		}
		// The bait: a hurt one without a turn draws the player back into the others.
		if (!hasTurn && mob.getHealth() < mob.getMaxHealth() * 0.4F && ObsM1.allies(mob).size() >= 2 && distance < 6.0) {
			return Decision.tactic(Tactic.REAGRUPARSE);
		}
		// The relay: having just struck, make room for the next one if anyone is waiting.
		if (now - mind.lastStrike < RELAY_TICKS && mind.othersWaiting && distance < 3.0 + Reach.extra(mob)) {
			return Decision.tactic(Tactic.ESPERAR);
		}
		// The pincer: the flanker goes round behind before it swings.
		if (mind.role == SquadRole.FLANCO && Math.abs(Squad.wrap(Squad.angle(mob, target) - Squad.facing(target))) < FLANK_DONE) {
			return Decision.tactic(Tactic.FLANQUEAR);
		}
		// The shield wall, and a hurt shield bearer's guard (ideas 15 and 37): without a turn, covered.
		if (!hasTurn && distance < 8.0 && hasShield(mob) && !MobDefense.guardBroken(mob) && mind.role != SquadRole.FLANCO) {
			return Decision.tactic(Tactic.CUBRIRSE);
		}
		// Waiting for a turn: from further off against a player whose weapon reaches further (flail, lance...).
		if (!hasTurn && distance < Reach.outside(target, CIRCLE_RANGE)) {
			// Waiting for a turn: against heavy plate, round the side; against a head-heavy weapon, out of its
			// reach; otherwise on the ring.
			if (Aggression.heavy(target)) {
				return Decision.tactic(Tactic.FLANQUEAR);
			}
			if (SwingStyle.of(target.getMainHandItem()) == SwingStyle.CHOP) {
				return Decision.tactic(Tactic.ESPERAR);
			}
			// The careful ones wait out of reach, the cunning ones go round behind (61).
			return switch (Personality.trait(mob)) {
				case PRUDENTE, COBARDE -> Decision.tactic(Tactic.ESPERAR);
				case ASTUTO -> Decision.tactic(Tactic.FLANQUEAR);
				default -> Decision.tactic(Tactic.RODEAR);
			};
		}
		// Going in from its own side: with others at the same player, one with a turn that is still well round
		// the ring from its slot goes round to it first, so the blows come from all sides and not one.
		// A long weapon strikes from further off, so "already this close" starts further off too.
		if (distance > GO_ROUND_MIN + Reach.extra(mob) && distance < Reach.outside(target, CIRCLE_RANGE + 2.0) && !Double.isNaN(mind.ringAngle)
			&& ObsM1.allies(mob).size() >= 1
			&& Math.abs(Squad.wrap(Squad.angle(mob, target) - mind.ringAngle)) > GO_ROUND_ANGLE) {
			return Decision.tactic(Tactic.RODEAR);
		}
		return Decision.APPROACH;
	}
}
