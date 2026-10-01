package dev.forja.clase;

import java.util.Map;

import dev.forja.Forja;
import dev.forja.difficulty.Threat;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Where the milestones (hitos, docs/ARBOLES.md) are noticed. Most are advancements or kill statistics the game
 * already keeps, so a periodic look at each player is enough — and it grants them retroactively, to a player
 * who did the thing before the trees existed. The few with nothing to look up (the first elite, an ancient city)
 * are caught as they happen. Their points live in the tree's file (ClassTree.milestones).
 */
public final class Milestones {
	/** How often each player is looked at, in ticks. */
	public static final int EVERY = 100;

	/** Milestone → the advancement that says it is done. */
	private static final Map<String, Identifier> ADVANCEMENTS = Map.ofEntries(
		Map.entry("campeon", Forja.id("forja/elite")),
		Map.entry("capitan", Forja.id("forja/saqueadores")),
		Map.entry("herrero_caido", Forja.id("forja/herrero_caido")),
		Map.entry("dragon", Identifier.withDefaultNamespace("end/kill_dragon")),
		Map.entry("heroe", Identifier.withDefaultNamespace("adventure/hero_of_the_village")),
		Map.entry("nether", Identifier.withDefaultNamespace("story/enter_the_nether")),
		Map.entry("end", Identifier.withDefaultNamespace("story/enter_the_end")),
		Map.entry("forja_profunda", Forja.id("forja/portal")),
		Map.entry("golpe_limpio", Forja.id("forja/perfecta")),
		Map.entry("obra_maestra", Forja.id("forja/obra_maestra")),
		Map.entry("maestro_forjador", Forja.id("forja/maestro")));

	private Milestones() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				check(player);
			}
		});
	}

	/** Everything that can be looked up: advancements, kill counts, the smith's mastery, an ancient city underfoot. */
	public static void check(ServerPlayer player) {
		ClassData data = ClassProgress.data(player);
		for (Map.Entry<String, Identifier> entry : ADVANCEMENTS.entrySet()) {
			if (!data.hasMilestone(entry.getKey())) {
				AdvancementHolder holder = player.level().getServer().getAdvancements().get(entry.getValue());
				if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
					ClassProgress.reach(player, entry.getKey());
				}
			}
		}
		killed(player, "warden", net.minecraft.world.entity.EntityTypes.WARDEN);
		killed(player, "wither", net.minecraft.world.entity.EntityTypes.WITHER);
		killed(player, "guardian_anciano", net.minecraft.world.entity.EntityTypes.ELDER_GUARDIAN);
		killed(player, "guardian_de_cuno", ModEntities.GUARDIAN_DE_CUNO);
		int mastery = dev.forja.forge.SmithLevel.level(player);
		if (mastery >= 5) {
			ClassProgress.reach(player, "maestria_5");
		}
		if (mastery >= 10) {
			ClassProgress.reach(player, "maestria_10");
		}
		if (!ClassProgress.data(player).hasMilestone("ciudad_antigua") && inAncientCity(player)) {
			ClassProgress.reach(player, "ciudad_antigua");
		}
	}

	private static void killed(ServerPlayer player, String milestone, EntityType<?> type) {
		if (!ClassProgress.data(player).hasMilestone(milestone) && player.getStats().getValue(Stats.ENTITY_KILLED.get(type)) > 0) {
			ClassProgress.reach(player, milestone);
		}
	}

	private static boolean inAncientCity(ServerPlayer player) {
		ServerLevel level = player.level();
		ResourceKey<Structure> city = BuiltinStructures.ANCIENT_CITY;
		return level.structureManager().getStructureWithPieceAt(player.blockPosition(), holder -> holder.is(city)).isValid();
	}

	/** A kill as it happens: the first elite has no statistic to look up afterwards. */
	public static void onKill(ServerPlayer killer, LivingEntity victim) {
		Threat threat = Threat.of(victim);
		if (threat == Threat.ELITE || threat == Threat.CAMPEON) {
			ClassProgress.reach(killer, "primer_elite");
		}
	}
}
