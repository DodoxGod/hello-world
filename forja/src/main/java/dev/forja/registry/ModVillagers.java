package dev.forja.registry;

import com.google.common.collect.ImmutableSet;
import dev.forja.Forja;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;

/**
 * The Forjador: a villager who takes the Parts Table as a job site and trades templates, parts,
 * repair kits and upgrade orbs. Its trade sets are data in data/forja/trade_set/forjador.
 */
public final class ModVillagers {
	public static final ResourceKey<PoiType> MESA_DE_PIEZAS_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Forja.id("mesa_de_piezas"));
	public static final ResourceKey<VillagerProfession> FORJADOR = ResourceKey.create(Registries.VILLAGER_PROFESSION, Forja.id("forjador"));

	private ModVillagers() {
	}

	public static void init() {
		PoiHelper.register(MESA_DE_PIEZAS_POI.identifier(), 1, 1, ModBlocks.MESA_DE_PIEZAS);
		Int2ObjectMap<ResourceKey<TradeSet>> trades = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
		for (int level = 1; level <= 5; level++) {
			trades.put(level, ResourceKey.create(Registries.TRADE_SET, Forja.id("forjador/level_" + level)));
		}
		Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, FORJADOR, new VillagerProfession(
			Component.translatable("entity.forja.villager.forjador"),
			poi -> poi.is(MESA_DE_PIEZAS_POI),
			poi -> poi.is(MESA_DE_PIEZAS_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_TOOLSMITH,
			trades
		));
	}
}
