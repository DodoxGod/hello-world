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
 * recortado a [1, base + 2]
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
		return Math.max(1, Math.min(base + 2, turns));
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
		return Math.min(0.6, habit * 0.8);
	}

	/** Chance a vanilla melee mob fakes its blow, against a player who parries a lot. */
	public static double feintChance(LivingEntity target) {
		return target instanceof Player player ? Math.min(0.5, PlayerHabits.get(player, PlayerHabits.PARRY) * 0.6) : 0.0;
	}

	/** The same, for a given mob: the cunning ones fake more (idea 61). */
	public static double feintChance(net.minecraft.world.entity.Mob mob, LivingEntity target) {
		double base = feintChance(target);
		return Personality.trait(mob) == Personality.Trait.ASTUTO ? Math.min(0.65, base + 0.15) : base;
	}
}
