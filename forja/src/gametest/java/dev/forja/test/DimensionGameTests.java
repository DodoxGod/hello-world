package dev.forja.test;

import dev.forja.registry.ModBlocks;
import dev.forja.world.StarYard;
import dev.forja.world.StarYardGenerator;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;

/**
 * El Cementerio entre Estrellas, first delivery (docs/HERRERO_DIMENSION.md, section 2): the dimension's
 * data and generator are registered, the test command is there, the arena is flat and clear, there is no
 * molten metal near it, the plain has its thousands of graves, its cold forges and its falls, and
 * generating it is cheap.
 *
 * <p>The gametest server bakes its worlds without the datapacks' dimensions, so the level itself only
 * exists in a real world: the client test (FORJA_SOLO=dimension) goes into it and back. Here the ground
 * is read straight off the generator, which is a pure function of the column.
 */
public class DimensionGameTests {
	/** The dimension's data is loaded: its type, its biome and its generator. */
	@GameTest(maxTicks = 20)
	public void theGraveyardIsRegistered(GameTestHelper helper) {
		var access = helper.getLevel().getServer().registryAccess();
		var type = access.lookupOrThrow(Registries.DIMENSION_TYPE)
			.get(ResourceKey.create(Registries.DIMENSION_TYPE, StarYard.LEVEL.identifier()));
		helper.assertTrue(type.isPresent(), "el tipo de dimensión forja:cementerio_estelar debería cargarse");
		helper.assertTrue(type.get().value().hasFixedTime(), "la hora está fija: noche eterna");
		helper.assertTrue(type.get().value().minY() == 0 && type.get().value().height() == 256, "de y 0 a 256");
		helper.assertTrue(access.lookupOrThrow(Registries.BIOME).get(StarYard.BIOME).isPresent(), "y su bioma");
		helper.assertTrue(BuiltInRegistries.CHUNK_GENERATOR.getValue(StarYard.LEVEL.identifier()) == StarYardGenerator.CODEC,
			"y su generador sin ruido");
		helper.succeed();
	}

	/** {@code /forja dimension} and {@code /forja dimension volver} are there, for the admins. */
	@GameTest(maxTicks = 20)
	public void theTestCommandExists(GameTestHelper helper) {
		var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("forja");
		helper.assertTrue(root != null && root.getChild("dimension") != null, "/forja dimension debería existir");
		helper.assertTrue(root.getChild("dimension").getChild("volver") != null, "y /forja dimension volver");
		helper.succeed();
	}

	/** The arena as the generator lays it: solid floor at SURFACE and five blocks of air over all of it. */
	@GameTest(maxTicks = 20)
	public void theArenaIsFlatAndClear(GameTestHelper helper) {
		for (int x = -22; x <= 22; x++) {
			for (int z = -22; z <= 22; z++) {
				if (StarYard.radius(x + 0.5, z + 0.5) > StarYard.ARENA) {
					continue;
				}
				BlockState[] column = StarYardGenerator.column(x, z);
				BlockState floor = column[StarYard.SURFACE];
				helper.assertTrue(floor != null && floor.isSolid(), "suelo de la arena en " + x + ", " + z + ": " + floor);
				for (int y = 1; y <= 5; y++) {
					BlockState above = column[StarYard.SURFACE + y];
					helper.assertTrue(above == null || above.isAir(),
						"nada estorba en la arena en " + x + ", " + (StarYard.SURFACE + y) + ", " + z + ": " + above);
				}
			}
		}
		// And where anyone arrives: on firm ground, with room to stand.
		BlockState[] arrival = StarYardGenerator.column(StarYard.ARRIVAL.getX(), StarYard.ARRIVAL.getZ());
		BlockState under = arrival[StarYard.ARRIVAL.getY() - 1];
		helper.assertTrue(under != null && under.isSolid(), "la plataforma de llegada es firme: " + under);
		helper.assertTrue(arrival[StarYard.ARRIVAL.getY()] == null && arrival[StarYard.ARRIVAL.getY() + 1] == null,
			"y hay sitio para estar de pie");
		helper.succeed();
	}

	/** No molten metal within MOLTEN_CLEARANCE of the arena's edge, but for the braziers up on the pillars. */
	@GameTest(maxTicks = 20)
	public void noMoltenMetalNearTheArena(GameTestHelper helper) {
		double reach = StarYard.RIM + StarYard.MOLTEN_CLEARANCE;
		int span = (int) Math.ceil(reach);
		int checked = 0;
		for (int x = -span; x <= span; x++) {
			for (int z = -span; z <= span; z++) {
				double r = StarYard.radius(x + 0.5, z + 0.5);
				if (r > reach) {
					continue;
				}
				BlockState[] column = StarYardGenerator.column(x, z);
				for (int y = 0; y < column.length; y++) {
					if (column[y] != null && column[y].is(ModBlocks.METAL_FUNDIDO)) {
						// The braziers are lamps seven blocks up on the pillars, out of anybody's reach.
						helper.assertTrue(y == StarYard.SURFACE + 7 && r > StarYard.PILLAR_RING - 2 && r < StarYard.PILLAR_RING + 2,
							"metal fundido a " + String.format("%.1f", r) + " bloques del centro, en " + x + ", " + y + ", " + z);
					}
				}
				checked++;
			}
		}
		helper.assertTrue(checked > 5000, "se miraron las columnas: " + checked);
		helper.succeed();
	}

	/** Thousands of graves, some thirty cold forges, and three rivers that pour off the edge. */
	@GameTest(maxTicks = 20)
	public void thePlainHasItsGravesForgesAndFalls(GameTestHelper helper) {
		int graves = 0;
		int falls = 0;
		for (int x = -180; x <= 180; x++) {
			for (int z = -180; z <= 180; z++) {
				BlockState[] column = StarYardGenerator.column(x, z);
				for (int y = StarYard.SURFACE - 6; y <= StarYard.SURFACE + 8; y++) {
					if (column[y] != null && column[y].is(ModBlocks.ARMA_CLAVADA)) {
						graves++;
					}
				}
				if (!StarYard.onPlateau(x, z) && column[20] != null && column[20].is(ModBlocks.METAL_FUNDIDO)) {
					falls++;
				}
			}
		}
		int forges = StarYardGenerator.forges().size();
		dev.forja.Forja.LOGGER.info("Cementerio entre Estrellas: {} tumbas, {} forjas frías, {} columnas de cascada", graves, forges, falls);
		helper.assertTrue(graves >= 2500 && graves <= 6000, "tumbas en la llanura: " + graves);
		helper.assertTrue(forges >= 20 && forges <= 60, "forjas frías: " + forges);
		helper.assertTrue(falls >= 3, "cascadas por el borde: " + falls + " columnas");
		helper.succeed();
	}

	/** Cheap: every column of 400 chunks at the edge, where there is the most in them, in well under three seconds. */
	@GameTest(maxTicks = 20)
	public void generatingItIsCheap(GameTestHelper helper) {
		long start = System.nanoTime();
		int blocks = 0;
		for (int x = 80; x < 80 + 16 * 20; x++) {
			for (int z = -160; z < -160 + 16 * 20; z++) {
				for (BlockState state : StarYardGenerator.column(x, z)) {
					if (state != null) {
						blocks++;
					}
				}
			}
		}
		double seconds = (System.nanoTime() - start) / 1.0E9;
		dev.forja.Forja.LOGGER.info("Cementerio entre Estrellas: 400 chunks ({} bloques) en {} s", blocks, String.format("%.2f", seconds));
		helper.assertTrue(blocks > 100000, "hay meseta en esos chunks: " + blocks);
		helper.assertTrue(seconds < 3.0, "400 chunks tardaron " + seconds + " s");
		helper.succeed();
	}
}
