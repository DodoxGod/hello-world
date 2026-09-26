package dev.forja.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.datafixers.util.Pair;
import dev.forja.Forja;
import dev.forja.mixin.StructureTemplatePoolAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Adds Forja's village smithy to the house pools of every village kind (a sandstone build for deserts),
 * so some villages come with both tables and a villager who can take them over as a Forjador. Vanilla
 * template pools are immutable data, so the element is appended to the loaded pools on server start.
 */
public final class ForjaVillages {
	public static final Identifier SMITHY = Forja.id("forja_de_aldea");
	public static final Identifier DESERT_SMITHY = Forja.id("forja_de_aldea_desierto");
	private static final int WEIGHT = 2;
	/** House pool to the smithy that matches its palette. */
	private static final Map<String, Identifier> HOUSE_POOLS = Map.of(
		"village/plains/houses", SMITHY,
		"village/savanna/houses", SMITHY,
		"village/taiga/houses", SMITHY,
		"village/snowy/houses", SMITHY,
		"village/desert/houses", DESERT_SMITHY
	);
	/** Pools are rebuilt on every data pack reload, so track the instances already dealt with. */
	private static final Map<StructureTemplatePool, Boolean> PATCHED = Collections.synchronizedMap(new WeakHashMap<>());

	private ForjaVillages() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(ForjaVillages::addSmithy);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> addSmithy(server));
	}

	public static void addSmithy(MinecraftServer server) {
		Registry<StructureTemplatePool> pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		HOUSE_POOLS.forEach((path, smithy) -> pools
			.getOptional(ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.withDefaultNamespace(path)))
			.ifPresent(pool -> append(pool, smithy)));
	}

	private static void append(StructureTemplatePool pool, Identifier template) {
		if (PATCHED.putIfAbsent(pool, Boolean.TRUE) != null) {
			return;
		}
		StructureTemplatePoolAccess access = (StructureTemplatePoolAccess) pool;
		StructurePoolElement smithy = StructurePoolElement.legacy(template.toString()).apply(StructureTemplatePool.Projection.RIGID);
		List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(access.forjaRawTemplates());
		raw.add(Pair.of(smithy, WEIGHT));
		access.forjaSetRawTemplates(raw);
		for (int i = 0; i < WEIGHT; i++) {
			access.forjaTemplates().add(smithy);
		}
	}
}
