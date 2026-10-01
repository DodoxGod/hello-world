package dev.forja.ai;

import dev.forja.combat.ArmorCalculator;
import dev.forja.combat.ChargedStrike;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.Stamina;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * How hard the monsters press a player, read off the player (ideas 1 to 10 of the plan): more of them
 * may swing at once when the player is out of breath, has just dodged, is eating or is nearly dead, or
 * plays well; fewer against a beginner. Used by every way a mob can take a turn.
 *
 * <pre>
 * turnos = base (2) − 1 si nivel &lt; 0,2 + 1 si nivel &gt; 0,7
 *        + 1 si estamina &lt; 25 + 1 si su esquiva está en enfriamiento + 1 si come o bebe + 1 si vida &lt; 30 %
 *        + extra (1 por tramo de equipo 0-3, +1 en MAESTRO, +2 en LEYENDA)
 * recortado a [1, base + 2 + extra]
 * </pre>
 */
public final class Aggression {
	private Aggression() {
	}

	/** How many monsters may swing at this target at once right now. */
	public static int maxAttackers(LivingEntity target) {
		int base = CombatConfig.get().maxSimultaneousAttackers;
		if (!(target instanceof Player player)) {
			return base;
		}
		int turns = base;
		float skill = PlayerHabits.skill(player);
		if (skill < 0.2F) turns--;
		if (skill > 0.7F) turns++;
		if (CombatConfig.get().stamina && Stamina.value(player) < 25.0F && !player.isCreative()) turns++;
		if (Stamina.dodgeCooldown(player) > 0) turns++;
		if (player.isUsingItem() && (player.getUseItem().has(DataComponents.FOOD) || player.getUseItem().has(DataComponents.CONSUMABLE))) turns++;
		if (player.getHealth() < player.getMaxHealth() * 0.3F) turns++;
		int extra = gearTurns(player);
		// The captain's synchronized charge (v4, M5): one more turn for 2 s after its shout.
		int charge = Captain.chargeTurn(player, player.level().getGameTime()) ? 1 : 0;
		// A big group (2026-09-30, iaTurnoGrupoGrande): one more turn when iaGrupoGrandeMin or more fight this player.
		CombatConfig cfg = CombatConfig.get();
		int big = cfg.iaTurnoGrupoGrande && Captain.groupSize(player, player.level().getGameTime()) >= cfg.iaGrupoGrandeMin ? 1 : 0;
		return Math.max(1, Math.min(base + 2 + extra, turns + extra) + charge + big);
	}

	/**
	 * Turns added for a well-equipped player and on the harder difficulties (Andy, 2026-09-29): one per gear
	 * tier (0 to 3), one more on MAESTRO and two on LEYENDA.
	 */
	public static int gearTurns(Player player) {
		CombatConfig cfg = CombatConfig.get();
		int extra = dev.forja.difficulty.GearScore.tier(player) * cfg.attackersPerGearTier;
		return extra + switch (dev.forja.difficulty.ForjaDifficulty.current()) {
			case MAESTRO -> cfg.attackersMaestro;
			case LEYENDA -> cfg.attackersLeyenda;
			default -> 0;
		};
	}

	/** For a given mob: an enraged one (a duel refused or cheated) counts one more turn (idea 96). */
	public static int maxAttackers(net.minecraft.world.entity.Mob mob, LivingEntity target) {
		MobMind mind = MobAi.mind(mob);
		return maxAttackers(target) + (mind != null && mind.enraged ? 1 : 0);
	}

	/** Whether the player is winding up a charged blow (mobs back out of its reach). */
	public static boolean charging(Player player) {
		return ChargedStrike.isCharging(player);
	}

	/** Whether the player wears heavy plate (mobs go round the side rather than face it). */
	public static boolean heavy(Player player) {
		return ArmorCalculator.armorWeight(player) > 0.5;
	}

	/**
	 * The automaton learns: it fakes its blow against whoever parries or dodges a lot, whichever of the two
	 * the player leans on (idea 42).
	 */
	public static double adaptiveFeintChance(LivingEntity target) {
		if (!(target instanceof Player player)) {
			return 0.0;
		}
		float habit = Math.max(PlayerHabits.get(player, PlayerHabits.PARRY), PlayerHabits.get(player, PlayerHabits.DODGE));
		return Math.min(0.6, dev.forja.difficulty.ForjaDifficulty.current().feint + habit * 0.8);
	}

	/**
	 * Chance a melee mob fakes its blow: more against a player who parries a lot, and never nothing ("que
	 * todos los mobs puedan fintar"): the difficulty's floor (0.05 to 0.20) comes first, whatever the player does.
	 */
	public static double feintChance(LivingEntity target) {
		return target instanceof Player player
			? Math.min(0.5, dev.forja.difficulty.ForjaDifficulty.current().feint + PlayerHabits.get(player, PlayerHabits.PARRY) * 0.6) : 0.0;
	}

	/** The same, for a given mob: the cunning ones fake more (idea 61). */
	public static double feintChance(net.minecraft.world.entity.Mob mob, LivingEntity target) {
		double base = feintChance(target);
		return Personality.trait(mob) == Personality.Trait.ASTUTO ? Math.min(0.65, base + 0.15) : base;
	}
}
