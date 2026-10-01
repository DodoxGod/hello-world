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
	 * The soul forge of the Fragua caída (docs/ALEACIONES_NETHER_END.md): found cold, lit with a blaze rod, and the
	 * only thing that makes wispfire and magmasteel. Nothing breaks it, so it stays the ruin's. See block/FarForgeBlock.
	 */
	public static final Block FRAGUA_DE_ALMAS = register(
		"fragua_de_almas",
		new dev.forja.block.FarForgeBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BLACK)
				.strength(-1.0F, 3600000.0F)
				.sound(SoundType.ANCIENT_DEBRIS)
				.noLootTable()
				.lightLevel(state -> state.getValue(dev.forja.block.FarForgeBlock.LIT) ? 13 : 4)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("fragua_de_almas"))),
			dev.forja.block.FarForgeBlock.Kind.ALMAS
		)
	);

	/**
	 * Costra de magma: lava cooled under the feet of someone in magmasteel (the Volcánico trait), back to lava a few
	 * seconds after nobody stands on it. Nothing breaks it and it drops nothing: it is lava, for a moment. See
	 * block/MagmaCrustBlock.
	 */
	public static final Block COSTRA_DE_MAGMA = register(
		"costra_de_magma",
		new dev.forja.block.MagmaCrustBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.NETHER)
				.strength(-1.0F, 3600000.0F)
				.sound(SoundType.BASALT)
				.noLootTable()
				.pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
				.lightLevel(state -> 3)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("costra_de_magma")))
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

	/** A length of channel with a gate in it: open it passes, closed it cuts the network. See block/MeltValveBlock. */
	public static final Block LLAVE_DE_PASO = register(
		"llave_de_paso",
		new dev.forja.block.MeltValveBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.strength(3.5F, 7.0F)
				.sound(SoundType.COPPER)
				.requiresCorrectToolForDrops()
				.noOcclusion()
				.lightLevel(state -> 7)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("llave_de_paso")))
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

	/**
	 * The strainer, set down on top of a casting table: the pour falls through it on its way into the
	 * mould. No tool needed and quick to knock off, because it is a clay grate on four legs and the item
	 * it drops is the same colador that went down (see block/StrainerBlock).
	 */
	public static final Block COLADOR = register(
		"colador",
		new dev.forja.block.StrainerBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.TERRACOTTA_ORANGE)
				.strength(0.4F, 3.0F)
				.sound(SoundType.DECORATED_POT)
				.noOcclusion()
				// A piston does not shove a grate along the table: it knocks it off, and it drops.
				.pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("colador")))
		)
	);

	/**
	 * La montadora: the forge star without the smith. Parts in by hopper, the finished piece out
	 * underneath, always at a plain press (see block/entity/AssemblerMachineBlockEntity).
	 */
	public static final Block MONTADORA = register(
		"montadora",
		new dev.forja.block.AssemblerMachineBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.strength(4.0F, 8.0F)
				.sound(SoundType.METAL)
				.requiresCorrectToolForDrops()
				.lightLevel(state -> state.getValue(dev.forja.block.AssemblerMachineBlock.LIT) ? 9 : 0)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("montadora")))
		)
	);

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

	// ---- the heat line (docs/FUNDICION_V2.md, part B): pipes, and the two vessels that fill them.

	/** A heat pipe: carries a heat fluid to what needs heat, never metal. See block/HeatPipeBlock. */
	public static final Block TUBO_DE_CALOR = register(
		"tubo_de_calor",
		new dev.forja.block.HeatPipeBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_ORANGE)
				.strength(2.0F, 6.0F)
				.sound(SoundType.COPPER)
				.requiresCorrectToolForDrops()
				.noOcclusion()
				// The hot fluids glow through the slit in the casing; steam and brine do not.
				.lightLevel(state -> state.getValue(dev.forja.block.HeatPipeBlock.FLUIDO).light())
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("tubo_de_calor")))
		)
	);

	/** The boiler: boils water, blaze rods, slag or ice into a heat fluid. See block/BoilerBlock. */
	public static final Block CALDERA = vessel("caldera", dev.forja.forge.HeatFluid.Vessel.CALDERA, MapColor.METAL, 3.5F);
	/** The heat depot: holds lava poured in by the bucket. See block/BoilerBlock. */
	public static final Block DEPOSITO_DE_CALOR = vessel("deposito_de_calor", dev.forja.forge.HeatFluid.Vessel.DEPOSITO,
		MapColor.TERRACOTTA_RED, 4.0F);

	private static Block vessel(String name, dev.forja.forge.HeatFluid.Vessel vessel, MapColor color, float strength) {
		return register(name, new dev.forja.block.BoilerBlock(
			BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(strength, strength * 3.0F)
				.sound(SoundType.METAL)
				.requiresCorrectToolForDrops()
				.noOcclusion()
				.lightLevel(state -> state.getValue(dev.forja.block.BoilerBlock.LIT) ? 11
					: state.getValue(dev.forja.block.BoilerBlock.LEVEL) > 0 ? state.getValue(dev.forja.block.BoilerBlock.FLUIDO).light() : 0)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id(name))),
			vessel));
	}

	/**
	 * Pararrayos de estrellas: draws a meteorite of the shower onto itself instead of letting it dig its
	 * crater somewhere else. See block/StarRodBlock.
	 */
	public static final Block PARARRAYOS = register(
		"pararrayos",
		new dev.forja.block.StarRodBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_ORANGE)
				.strength(3.0F, 6.0F)
				.sound(SoundType.COPPER)
				.requiresCorrectToolForDrops()
				.noOcclusion()
				// The star glass at the top still has some of the sky in it.
				.lightLevel(state -> 7 - state.getValue(dev.forja.block.StarRodBlock.DESGASTE) * 2)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("pararrayos")))
		)
	);

	// ------------------------------------------------------------------ El Cementerio entre Estrellas

	/** Ceniza: the ash the plain of the smiths' graveyard is made of. Soft, grey, and it holds a footprint. */
	public static final Block CENIZA = register(
		"ceniza",
		new Block(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(0.5F)
				.sound(SoundType.SAND)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("ceniza")))
		)
	);

	/** Ceniza prensada: ash that has lain long enough to be walked hard; the mounds over the graves. */
	public static final Block CENIZA_PRENSADA = register(
		"ceniza_prensada",
		new Block(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BLACK)
				.strength(0.8F)
				.sound(SoundType.SOUL_SOIL)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("ceniza_prensada")))
		)
	);

	/** Metal fundido: the rivers and falls of the graveyard. Scenery; see block/MoltenMetalBlock. */
	public static final Block METAL_FUNDIDO = register(
		"metal_fundido",
		new dev.forja.block.MoltenMetalBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_ORANGE)
				.strength(-1.0F, 3600000.0F)
				.noCollision()
				.noLootTable()
				.lightLevel(state -> 15)
				.pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("metal_fundido")))
		)
	);

	/** Arma clavada: a weapon stood point first in the ash, as a grave. See block/GraveWeaponBlock. */
	public static final Block ARMA_CLAVADA = register(
		"arma_clavada",
		new dev.forja.block.GraveWeaponBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.strength(0.6F)
				.sound(SoundType.CHAIN)
				.noCollision()
				.noOcclusion()
				.noLootTable()
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("arma_clavada")))
		)
	);

	/** Ménsula estelar: a bracket of the star portal's frame, where an oricalco pearl goes. See block/StarBracketBlock. */
	/** The smith's shelf: a place for each of the guide's eight books (docs/LIBROS_GUIA.md, 3.3). */
	public static final Block ESTANTERIA_DEL_HERRERO = register(
		"estanteria_del_herrero",
		new dev.forja.block.SmithShelfBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.WOOD)
				.strength(1.5F)
				.sound(SoundType.CHISELED_BOOKSHELF)
				.ignitedByLava()
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("estanteria_del_herrero")))
		)
	);

	/** The lectern with book VII, put beside the star portal when it is lit. */
	public static final Block ATRIL_DEL_HERRERO = register(
		"atril_del_herrero",
		new dev.forja.block.SmithLecternBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.WOOD)
				.strength(2.5F)
				.sound(SoundType.WOOD)
				.noOcclusion()
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("atril_del_herrero")))
		)
	);

	public static final Block MENSULA_ESTELAR = register(
		"mensula_estelar",
		new dev.forja.block.StarBracketBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BLACK)
				.strength(-1.0F, 3600000.0F)
				.sound(SoundType.STONE)
				.noLootTable()
				.lightLevel(state -> state.getValue(dev.forja.block.StarBracketBlock.PERLA) ? 7 : 1)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("mensula_estelar")))
		)
	);

	/** Portal estelar: the lit hole of the frame, to the Cementerio entre Estrellas and back. See block/StarPortalBlock. */
	public static final Block PORTAL_ESTELAR = register(
		"portal_estelar",
		new dev.forja.block.StarPortalBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_PURPLE)
				.strength(-1.0F, 3600000.0F)
				.noCollision()
				.noLootTable()
				.lightLevel(state -> 15)
				.pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("portal_estelar")))
		)
	);

	/** Brasa estelar: one of the Fallen Smith's forge fires in the Reforjado estelar. See block/StarEmberBlock. */
	public static final Block BRASA_ESTELAR = register(
		"brasa_estelar",
		new dev.forja.block.StarEmberBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.GOLD)
				.strength(-1.0F, 3600000.0F)
				.noCollision()
				.noOcclusion()
				.noLootTable()
				.lightLevel(state -> 13)
				.pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("brasa_estelar")))
		)
	);

	/** Estrella de vuelta: the star that lands when he dies, and takes you home. See block/ReturnStarBlock. */
	public static final Block ESTRELLA_DE_VUELTA = register(
		"estrella_de_vuelta",
		new dev.forja.block.ReturnStarBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.GOLD)
				.strength(-1.0F, 3600000.0F)
				.noOcclusion()
				.noLootTable()
				.lightLevel(state -> 15)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("estrella_de_vuelta")))
		)
	);

	/** Fragua fría estelar: the forge a rematch is called at. See block/StarForgeBlock. */
	public static final Block FRAGUA_FRIA_ESTELAR = register(
		"fragua_fria_estelar",
		new dev.forja.block.StarForgeBlock(
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BLACK)
				.strength(-1.0F, 3600000.0F)
				.sound(SoundType.ANCIENT_DEBRIS)
				.noLootTable()
				.lightLevel(state -> 4)
				.setId(ResourceKey.create(Registries.BLOCK, Forja.id("fragua_fria_estelar")))
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
