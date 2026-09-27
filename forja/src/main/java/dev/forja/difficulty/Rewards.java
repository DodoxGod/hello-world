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
 * Risk pays: a veteran or an elite brought down drops a little more (a veteran sometimes one thing, an elite
 * up to two) and teaches the weapon and the smith more. A champion gives its legend and nothing else
 * (world/Elites): "los campeones deben de dar solo la legendaria".
 *
 * <p>It used to drop its whole loot again, which doubled everything; Andy: "solo deben soltar 1 o 2 cosas
 * máximo". So the extra is counted in things (stacks) out of a fresh roll of its loot, not in rolls.
 *
 * <pre>
 * cosas extra = (veterano 0,35 · élite 1,0) × dificultad.botín; la parte entera siempre, el resto con esa probabilidad;
 *               como mucho 1 el veterano y 2 la élite; el campeón, ninguna
 * maestría y herrero: veterano +2, élite +5, campeón +10, × dificultad.botín
 * </pre>
 */
public final class Rewards {
	/** The most extra things a veteran and an elite drop. */
	public static final int MOST_VETERAN = 1;
	public static final int MOST_ELITE = 2;

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
			double share = switch (threat) {
				case NORMAL, CAMPEON -> 0.0;
				case VETERANO -> cfg.rewardVeteranLoot;
				case ELITE -> cfg.rewardEliteLoot;
			} * loot;
			int most = threat == Threat.VETERANO ? MOST_VETERAN : threat == Threat.ELITE ? MOST_ELITE : 0;
			int things = Math.min(most, (int) share + (level.getRandom().nextDouble() < share - (int) share ? 1 : 0));
			if (things > 0) {
				mob.getLootTable().ifPresent(table -> {
					java.util.List<net.minecraft.world.item.ItemStack> rolled = new java.util.ArrayList<>();
					// A roll can come up empty or with a single stack; a second one is allowed to make up the count.
					for (int roll = 0; roll < 2 && rolled.size() < things; roll++) {
						mob.dropFromLootTable(level, source, true, table, rolled::add);
					}
					net.minecraft.util.Util.shuffle(rolled, level.getRandom());
					for (int i = 0; i < Math.min(things, rolled.size()); i++) {
						mob.spawnAtLocation(level, rolled.get(i));
					}
				});
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
