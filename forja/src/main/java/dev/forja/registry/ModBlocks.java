package dev.forja.registry;

import dev.forja.Forja;
import dev.forja.block.ForgeTableBlock;
import dev.forja.menu.Station;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final Block MESA_DE_FORJA = table(Station.FORJA, MapColor.WOOD);
	public static final Block MESA_DE_PIEZAS = table(Station.PIEZAS, MapColor.PODZOL);
	public static final Block MESA_DE_TALABARTERIA = table(Station.TALABARTERIA, MapColor.COLOR_BROWN);
	/** The greater table: everything the plain bench will not attempt. See menu/Station. */
	public static final Block MESA_DE_FORJA_MAYOR = table(Station.FORJA_MAYOR, MapColor.DEEPSLATE);

	/** The forge of the fallen smith: only found in his fortress, and the only way to wake him. */
	public static final Block FRAGUA_APAGADA = register(
		"fragua_apagada",
		new dev.forja.block.DeadForgeBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BLACK)
				.strength(40.0F, 1200.0F)
				.sound(SoundType.ANCIENT_DEBRIS)
				.requiresCorrectToolForDrops()
				.lightLevel(state -> 5)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("fragua_apagada")))
		)
	);

	/**
	 * The anvil of the fallen smith, which he leaves behind. Standing on its own next to a forge table it
	 * is worth the other two tables: the master's anvil is a whole workshop by itself.
	 */
	public static final Block YUNQUE_DEL_HERRERO = register(
		"yunque_del_herrero",
		new dev.forja.block.SmithAnvilBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BLACK)
				.strength(6.0F, 1200.0F)
				.sound(SoundType.ANVIL)
				.requiresCorrectToolForDrops()
				.lightLevel(state -> 3)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("yunque_del_herrero")))
		)
	);

	/**
	 * A wisp in a cage it cannot get out of.
	 *
	 * <p>It is a lamp, but that is not the point of it: set under a forge table it counts as the hottest
	 * heat there is, so a smith who can catch one never has to haul lava into the workshop again. It is
	 * the only thing in the mod you get by taking something alive rather than by killing it.
	 */
	public static final Block FAROL_DE_PAVESA = register(
		"farol_de_pavesa",
		new Block(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_ORANGE)
				.strength(3.5F, 6.0F)
				.sound(SoundType.LANTERN)
				.requiresCorrectToolForDrops()
				.lightLevel(state -> 15)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("farol_de_pavesa")))
		)
	);

	/** The three crucibles. What one is built of decides what it will melt; see block/CrucibleBlock. */
	public static final Block CRISOL_DE_BARRO = crucible(dev.forja.block.CrucibleBlock.Tier.BARRO, MapColor.TERRACOTTA_ORANGE, 2.0F);
	public static final Block CRISOL_DE_HIERRO = crucible(dev.forja.block.CrucibleBlock.Tier.HIERRO, MapColor.METAL, 4.0F);
	public static final Block CRISOL_DE_OBSIDIANA = crucible(dev.forja.block.CrucibleBlock.Tier.OBSIDIANA, MapColor.COLOR_BLACK, 8.0F);

	private static Block crucible(dev.forja.block.CrucibleBlock.Tier tier, MapColor color, float strength) {
		return register(tier.id(), new dev.forja.block.CrucibleBlock(
			BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(strength, strength * 3.0F)
				.sound(SoundType.STONE)
				.requiresCorrectToolForDrops()
				.lightLevel(state -> state.getValue(dev.forja.block.CrucibleBlock.LIT) ? 13 : 0)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id(tier.id()))),
			tier));
	}

	/** A tank of molten metal; set two touching and they are one. See block/MeltTankBlock. */
	public static final Block CUBA_DE_COLADA = register(
		"cuba_de_colada",
		new dev.forja.block.MeltTankBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_ORANGE)
				.strength(3.0F, 6.0F)
				.sound(SoundType.GLASS)
				.requiresCorrectToolForDrops()
				.noOcclusion()
				.lightLevel(state -> state.getValue(dev.forja.block.MeltTankBlock.LEVEL) * 2)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("cuba_de_colada")))
		)
	);

	/** The three grades of pipe; they hold nothing. See block/MeltPipeBlock. */
	public static final Block CONDUCTO_DE_COLADA = pipe(dev.forja.block.MeltPipeBlock.Grade.BRONCE, MapColor.TERRACOTTA_ORANGE, 2.5F);
	public static final Block CONDUCTO_DE_ACERO = pipe(dev.forja.block.MeltPipeBlock.Grade.ACERO, MapColor.METAL, 3.5F);
	public static final Block CONDUCTO_DE_DAMASCO = pipe(dev.forja.block.MeltPipeBlock.Grade.DAMASCO, MapColor.COLOR_GRAY, 5.0F);

	private static Block pipe(dev.forja.block.MeltPipeBlock.Grade grade, MapColor color, float strength) {
		return register(grade.id(), new dev.forja.block.MeltPipeBlock(
			BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(strength, strength * 2.0F)
				.sound(SoundType.COPPER)
				.requiresCorrectToolForDrops()
				.noOcclusion()
				// There is molten metal lying open in it, so it lights the floor it is laid in.
				.lightLevel(state -> 7)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id(grade.id()))),
			grade));
	}


	/** The end of a run: a channel with a hole in its floor. See block/MeltSpoutBlock. */
	public static final Block CANO_DE_COLADA = register(
		"cano_de_colada",
		new dev.forja.block.MeltSpoutBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.TERRACOTTA_ORANGE)
				.strength(2.5F, 5.0F)
				.sound(SoundType.COPPER)
				.requiresCorrectToolForDrops()
				.noOcclusion()
				.lightLevel(state -> 9)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("cano_de_colada")))
		)
	);

	/** The three casting boxes; see block/CastingBoxBlock. */
	public static final Block CAJA_DE_MOLDEO = castingBox(dev.forja.block.CastingBoxBlock.Tier.BARRO, MapColor.TERRACOTTA_ORANGE, 2.5F);
	public static final Block CAJA_DE_MOLDEO_DE_ACERO = castingBox(dev.forja.block.CastingBoxBlock.Tier.ACERO, MapColor.METAL, 4.5F);
	public static final Block CAJA_DE_MOLDEO_DE_DAMASCO = castingBox(dev.forja.block.CastingBoxBlock.Tier.DAMASCO, MapColor.COLOR_GRAY, 7.0F);

	private static Block castingBox(dev.forja.block.CastingBoxBlock.Tier tier, MapColor color, float strength) {
		return register(tier.id(), new dev.forja.block.CastingBoxBlock(
			BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(strength, strength * 3.0F)
				.sound(SoundType.STONE)
				.requiresCorrectToolForDrops()
				.lightLevel(state -> state.getValue(dev.forja.block.CastingBoxBlock.LIT) ? 7 : 0)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id(tier.id()))),
			tier));
	}

	/**
	 * The three casting tables: dark stone that pours a whole tool at once. They do the same work and
	 * differ only in how long the stone holds its heat, and how steady the pour is because of it.
	 */
	public static final Block MESA_DE_LOSA = castingTable(dev.forja.block.CastingTableBlock.Tier.LOSA, MapColor.DEEPSLATE, 3.5F);
	public static final Block MESA_DE_BRASA = castingTable(dev.forja.block.CastingTableBlock.Tier.BRASA, MapColor.COLOR_BLACK, 5.0F);
	public static final Block MESA_DE_ALMAS = castingTable(dev.forja.block.CastingTableBlock.Tier.ALMAS, MapColor.TERRACOTTA_BLACK, 7.0F);

	private static Block castingTable(dev.forja.block.CastingTableBlock.Tier tier, MapColor color, float strength) {
		return register(tier.id(), new dev.forja.block.CastingTableBlock(
			BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(strength, strength * 3.0F)
				.sound(SoundType.DEEPSLATE)
				.requiresCorrectToolForDrops()
				// The melt on top lights the table, and the soul table glows a little on its own.
				.lightLevel(state -> state.getValue(dev.forja.block.CastingTableBlock.LIT) ? 9
					: tier == dev.forja.block.CastingTableBlock.Tier.ALMAS ? 4 : 0)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id(tier.id()))),
			tier));
	}

	/** The workshop's drawer: a chest that only takes what a smith makes. */
	public static final Block ARMARIO_DE_PIEZAS = register(
		"armario_de_piezas",
		new dev.forja.block.PartsCabinetBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.PODZOL)
				.strength(2.5F)
				.sound(SoundType.WOOD)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("armario_de_piezas")))
		)
	);

	/** Where one upgrade comes off a piece, into an orb if you brought one. See menu/ExtractionMenu. */
	public static final Block MESA_DE_EXTRACCION = register(
		"mesa_de_extraccion",
		new dev.forja.block.ExtractionTableBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.DEEPSLATE)
				.strength(3.0F, 6.0F)
				.sound(SoundType.STONE)
				.requiresCorrectToolForDrops()
				.lightLevel(state -> 5)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("mesa_de_extraccion")))
		)
	);

	private ModBlocks() {
	}

	private static Block table(Station station, MapColor color) {
		String name = "mesa_de_" + station.id();
		// The two with a fire in them light the room. Not brightly — a forge is a working fire in a
		// hearth, not a torch — but enough that a workshop lit only by its own forges reads as a place
		// somebody works in, and enough to keep mobs off the floor around it, which matters more.
		int light = station == Station.FORJA ? 8 : station == Station.FORJA_MAYOR ? 11 : 0;
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(color)
			.strength(2.5F)
			.sound(SoundType.WOOD)
			.setId(ResourceKey.create(Registries.BLOCK, Forja.id(name)));
		if (light > 0) {
			properties = properties.lightLevel(state -> light);
		}
		return register(name, new ForgeTableBlock(station, properties));
	}

	private static Block register(String name, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, Forja.id(name), block);
	}

	public static void init() {
	}
}
