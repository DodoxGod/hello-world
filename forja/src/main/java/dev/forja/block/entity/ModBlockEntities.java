package dev.forja.block.entity;

import dev.forja.Forja;
import dev.forja.registry.ModBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** The mod's block entities: the cabinet, and the crucibles, tanks, boxes and tables of the foundry. */
public final class ModBlockEntities {
	private static final ResourceKey<BlockEntityType<?>> ARMARIO_KEY =
		ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Forja.id("armario_de_piezas"));

	public static final BlockEntityType<PartsCabinetBlockEntity> ARMARIO = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		ARMARIO_KEY,
		new BlockEntityType<>(PartsCabinetBlockEntity::new, java.util.Set.of(ModBlocks.ARMARIO_DE_PIEZAS))
	);

	private static final ResourceKey<BlockEntityType<?>> CRISOL_KEY =
		ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Forja.id("crisol"));

	/** One type for all three crucibles: they differ in what they are built of, not in what they are. */
	public static final BlockEntityType<CrucibleBlockEntity> CRISOL = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		CRISOL_KEY,
		new BlockEntityType<>(CrucibleBlockEntity::new, java.util.Set.of(
			ModBlocks.CRISOL_DE_BARRO, ModBlocks.CRISOL_DE_HIERRO, ModBlocks.CRISOL_DE_OBSIDIANA))
	);

	private static final ResourceKey<BlockEntityType<?>> CUBA_KEY =
		ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Forja.id("cuba_de_colada"));

	public static final BlockEntityType<MeltTankBlockEntity> CUBA = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		CUBA_KEY,
		new BlockEntityType<>(MeltTankBlockEntity::new, java.util.Set.of(ModBlocks.CUBA_DE_COLADA))
	);

	private static final ResourceKey<BlockEntityType<?>> CAJA_KEY =
		ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Forja.id("caja_de_moldeo"));

	public static final BlockEntityType<CastingBoxBlockEntity> CAJA = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		CAJA_KEY,
		new BlockEntityType<>(CastingBoxBlockEntity::new, java.util.Set.of(
			ModBlocks.CAJA_DE_MOLDEO, ModBlocks.CAJA_DE_MOLDEO_DE_ACERO, ModBlocks.CAJA_DE_MOLDEO_DE_DAMASCO))
	);

	private static final ResourceKey<BlockEntityType<?>> MESA_KEY =
		ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Forja.id("mesa_de_colada"));

	/** One type for all three casting tables, the way the crucibles and the boxes each share one. */
	public static final BlockEntityType<CastingTableBlockEntity> MESA_DE_COLADA = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		MESA_KEY,
		new BlockEntityType<>(CastingTableBlockEntity::new, java.util.Set.of(
			ModBlocks.MESA_DE_LOSA, ModBlocks.MESA_DE_BRASA, ModBlocks.MESA_DE_ALMAS))
	);

	private static final ResourceKey<BlockEntityType<?>> COLADA_KEY =
		ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Forja.id("colada"));

	/**
	 * Every piece of channel there is, spout included. None of them hold anything: this is what lets
	 * them be drawn as what is actually going through them.
	 */
	public static final BlockEntityType<MeltFlowBlockEntity> COLADA = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		COLADA_KEY,
		new BlockEntityType<>(MeltFlowBlockEntity::new, java.util.Set.of(
			ModBlocks.CONDUCTO_DE_COLADA, ModBlocks.CONDUCTO_DE_ACERO,
			ModBlocks.CONDUCTO_DE_DAMASCO, ModBlocks.CANO_DE_COLADA))
	);

	private ModBlockEntities() {
	}

	public static void init() {
	}
}
