package dev.forja.difficulty;

import dev.forja.combat.CombatConfig;
import dev.forja.forge.Mastery;
import dev.forja.forge.SmithLevel;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Risk pays: a veteran or an elite brought down drops its loot again (a veteran sometimes, an elite
 * always, more often on the higher difficulties) and teaches the weapon and the smith more.
 *
 * <pre>
 * tiradas extra = (veterano 0,35 · élite/campeón 1,0) × dificultad.botín; la parte entera siempre, el resto con esa probabilidad
 * maestría y herrero: veterano +2, élite +5, campeón +10, × dificultad.botín
 * </pre>
 */
public final class Rewards {
	private Rewards() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof Mob mob) || !(entity instanceof Enemy) || !(source.getEntity() instanceof ServerPlayer killer)
				|| !(entity.level() instanceof ServerLevel level) || !CombatConfig.get().enabled) {
				return;
			}
			Threat threat = Threat.of(mob);
			double loot = ForjaDifficulty.current().loot;
			CombatConfig cfg = CombatConfig.get();
			double rolls = switch (threat) {
				case NORMAL -> 0.0;
				case VETERANO -> cfg.rewardVeteranLoot;
				case ELITE, CAMPEON -> cfg.rewardEliteLoot;
			} * loot;
			while (rolls > 0.0) {
				if (rolls >= 1.0 || level.getRandom().nextDouble() < rolls) {
					mob.getLootTable().ifPresent(table -> mob.dropFromLootTable(level, source, true, table));
				}
				rolls -= 1.0;
			}
			int lesson = (int) Math.round(switch (threat) {
				case NORMAL -> 0;
				case VETERANO -> 2;
				case ELITE -> 5;
				case CAMPEON -> 10;
			} * loot);
			if (lesson > 0) {
				Mastery.addExperience(killer, killer.getMainHandItem(), lesson);
				SmithLevel.award(killer, lesson);
			}
		});
	}
}
