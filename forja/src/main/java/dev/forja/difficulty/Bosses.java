package dev.forja.difficulty;

import dev.forja.combat.CombatConfig;
import net.minecraft.world.entity.LivingEntity;

/** The mod's bosses, which play by their own rules: the tightest hit cap, and finishers only late in the fight. */
public final class Bosses {
	private Bosses() {
	}

	public static boolean isBoss(LivingEntity entity) {
		return entity instanceof dev.forja.entity.FallenSmith;
	}

	/** Whether a boss is far enough gone that a finisher may land on it. Everyone else: always. */
	public static boolean finishable(LivingEntity entity) {
		return !isBoss(entity) || entity.getHealth() <= entity.getMaxHealth() * CombatConfig.get().bossFinisherHealth;
	}
}
