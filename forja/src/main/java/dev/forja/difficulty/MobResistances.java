package dev.forja.difficulty;

import dev.forja.combat.CombatConfig;
import dev.forja.combat.DamageKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Each monster takes each kind of blow its own way (config: {@code resistenciasMobs}): the walking anvil
 * shrugs off edges, the slag swallows hammers, the rust swarm lets points pass through. No one weapon is
 * right for everything, so what you carry into a fight starts to matter.
 */
public final class MobResistances {
	private MobResistances() {
	}

	/** The damage multiplier for this entity against this kind of blow; 1 for players and anything unlisted. */
	public static double factor(LivingEntity target, DamageKind kind) {
		if (target instanceof Player) {
			return 1.0;
		}
		CombatConfig.MobResistance resistance = CombatConfig.get().resistenciasMobs
			.get(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
		return resistance == null ? 1.0 : resistance.factor(kind);
	}
}
