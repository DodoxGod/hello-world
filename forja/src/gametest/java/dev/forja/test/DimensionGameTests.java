package dev.forja.test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;

import dev.forja.block.GraveWeaponBlock;
import dev.forja.registry.ModBlocks;
import dev.forja.world.StarYard;
import dev.forja.world.StarYardGenerator;
import dev.forja.world.StarYardLayout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * El Cementerio entre Estrellas, first delivery (docs/HERRERO_DIMENSION.md, section 2): the dimension's
 * data and generator are registered, the test command is there, the arena is flat and clear in every
 * world, there is no molten metal near it, the plain has its thousands of graves, its cold forges and its
 * falls, the seed changes all of that but the arena, generating it is cheap, and a grave weapon goes with
 * the ground under it and never drops anything.
 *
 * <p>The gametest server bakes its worlds without the datapacks' dimensions, so the level itself only
 * exists in a real world: the client test (FORJA_SOLO=dimension) goes into it and back. Here the ground
 * is read straight off the generator, a pure function of the column and the seed.
 */
public class DimensionGameTests {
	/** A handful of seeds, so no rule below holds for one world only by luck. */
	private static final long[] SEEDS = {0L, 1L, 42L, -7_300_115_977L, 20_260_929L};

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

	/** In every world: solid floor at SURFACE and five blocks of air over the whole arena, and firm ground where you arrive. */
	@GameTest(maxTicks = 20)
	public void theArenaIsFlatAndClear(GameTestHelper helper) {
		for (long seed : SEEDS) {
			StarYardLayout layout = StarYardLayout.of(seed);
			for (int x = -22; x <= 22; x++) {
				for (int z = -22; z <= 22; z++) {
					if (StarYard.radius(x + 0.5, z + 0.5) > StarYard.ARENA) {
						continue;
					}
					BlockState[] column = StarYardGenerator.column(layout, x, z);
					BlockState floor = column[StarYard.SURFACE];
					helper.assertTrue(floor != null && floor.isSolid(), "semilla " + seed + ", suelo de la arena en " + x + ", " + z + ": " + floor);
					for (int y = 1; y <= 5; y++) {
						BlockState above = column[StarYard.SURFACE + y];
						helper.assertTrue(above == null || above.isAir(),
							"semilla " + seed + ", nada estorba en la arena en " + x + ", " + (StarYard.SURFACE + y) + ", " + z + ": " + above);
					}
				}
			}
			BlockState[] arrival = StarYardGenerator.column(layout, StarYard.ARRIVAL.getX(), StarYard.ARRIVAL.getZ());
			BlockState under = arrival[StarYard.ARRIVAL.getY() - 1];
			helper.assertTrue(under != null && under.isSolid(), "semilla " + seed + ", la plataforma de llegada es firme: " + under);
			helper.assertTrue(arrival[StarYard.ARRIVAL.getY()] == null && arrival[StarYard.ARRIVAL.getY() + 1] == null,
				"semilla " + seed + ", y hay sitio para estar de pie");
		}
		helper.succeed();
	}

	/** In every world: no molten metal within MOLTEN_CLEARANCE of the arena's edge, but for the braziers up on the pillars. */
	@GameTest(maxTicks = 20)
	public void noMoltenMetalNearTheArena(GameTestHelper helper) {
		double reach = StarYard.RIM + StarYard.MOLTEN_CLEARANCE;
		int span = (int) Math.ceil(reach);
		for (long seed : SEEDS) {
			StarYardLayout layout = StarYardLayout.of(seed);
			for (int x = -span; x <= span; x++) {
				for (int z = -span; z <= span; z++) {
					double r = StarYard.radius(x + 0.5, z + 0.5);
					if (r > reach) {
						continue;
					}
					BlockState[] column = StarYardGenerator.column(layout, x, z);
					for (int y = 0; y < column.length; y++) {
						if (column[y] != null && column[y].is(ModBlocks.METAL_FUNDIDO)) {
							// The braziers are lamps seven blocks up on the pillars, out of anybody's reach.
							helper.assertTrue(y == StarYard.SURFACE + 7 && r > StarYard.PILLAR_RING - 2 && r < StarYard.PILLAR_RING + 2,
								"semilla " + seed + ", metal fundido a " + String.format("%.1f", r) + " bloques del centro, en " + x + ", " + y + ", " + z);
						}
					}
				}
			}
		}
		helper.succeed();
	}

	/** In every world: thousands of graves, a few dozen cold forges, and rivers that pour off the edge. */
	@GameTest(maxTicks = 20)
	public void thePlainHasItsGravesForgesAndFalls(GameTestHelper helper) {
		for (long seed : SEEDS) {
			StarYardLayout layout = StarYardLayout.of(seed);
			int[] counts = count(layout);
			int forges = StarYardGenerator.forges(layout).size();
			dev.forja.Forja.LOGGER.info("Cementerio entre Estrellas, semilla {}: {} tumbas, {} forjas frías, {} columnas de cascada, {} ríos, {} islotes",
				seed, counts[0], forges, counts[1], layout.rivers.length, layout.islets.length);
			helper.assertTrue(counts[0] >= 2000 && counts[0] <= 8000, "semilla " + seed + ", tumbas en la llanura: " + counts[0]);
			helper.assertTrue(forges >= 15 && forges <= 80, "semilla " + seed + ", forjas frías: " + forges);
			helper.assertTrue(counts[1] >= layout.rivers.length, "semilla " + seed + ", cascadas por el borde: " + counts[1] + " columnas");
			helper.assertTrue(layout.islets.length >= 5, "semilla " + seed + ", islotes: " + layout.islets.length);
		}
		helper.succeed();
	}

	/** Graves and fall columns (at y 20, below every plateau) over the whole plateau. */
	private static int[] count(StarYardLayout layout) {
		int graves = 0;
		int falls = 0;
		for (int x = -200; x <= 200; x++) {
			for (int z = -200; z <= 200; z++) {
				BlockState[] column = StarYardGenerator.column(layout, x, z);
				for (int y = StarYard.SURFACE - 6; y <= StarYard.SURFACE + 8; y++) {
					if (column[y] != null && column[y].is(ModBlocks.ARMA_CLAVADA)) {
						graves++;
					}
				}
				if (!layout.onPlateau(x, z) && column[20] != null && column[20].is(ModBlocks.METAL_FUNDIDO)) {
					falls++;
				}
			}
		}
		return new int[] {graves, falls};
	}

	/**
	 * The seed shapes the plateau (Andy, 2026-09-29): two worlds differ in their outline, rivers, islets,
	 * graves and forges; the same seed always gives the same ground; and the arena is the same in all of
	 * them. Writes a map of two seeds from above, for the contact sheet.
	 */
	@GameTest(maxTicks = 40)
	public void theSeedShapesThePlateauButNotTheArena(GameTestHelper helper) {
		StarYardLayout one = StarYardLayout.of(1L);
		StarYardLayout two = StarYardLayout.of(42L);
		helper.assertTrue(StarYardLayout.of(1L) == one, "la misma semilla da el mismo trazado");
		boolean outline = false;
		for (int i = 0; i < 16; i++) {
			double theta = i * Math.PI / 8.0;
			outline |= Math.abs(one.edge(theta) - two.edge(theta)) > 4.0;
		}
		helper.assertTrue(outline, "el borde cambia con la semilla");
		helper.assertTrue(one.rivers.length != two.rivers.length || Math.abs(one.rivers[0] - two.rivers[0]) > 0.2, "y los ríos");
		helper.assertTrue(!Arrays.deepEquals(one.islets, two.islets), "y los islotes");
		helper.assertTrue(!StarYardGenerator.forges(one).equals(StarYardGenerator.forges(two)), "y las forjas frías");
		int differing = 0;
		for (int x = -22; x <= 22; x++) {
			for (int z = -22; z <= 22; z++) {
				if (StarYard.radius(x + 0.5, z + 0.5) > StarYard.RIM) {
					continue;
				}
				// The rock under the arena may take the seed's hash; the floor and all that stands on it may not.
				BlockState[] a = StarYardGenerator.column(one, x, z);
				BlockState[] b = StarYardGenerator.column(two, x, z);
				for (int y = StarYard.SURFACE; y < a.length; y++) {
					if (a[y] != b[y]) {
						differing++;
					}
				}
			}
		}
		helper.assertTrue(differing == 0, "la arena es la misma en todos los mundos: " + differing + " bloques cambian");
		// The same world twice gives the same ground, column by column.
		for (int x = -200; x <= 200; x += 7) {
			for (int z = -200; z <= 200; z += 7) {
				helper.assertTrue(Arrays.equals(StarYardGenerator.column(one, x, z), StarYardGenerator.column(StarYardLayout.of(1L), x, z)),
					"la misma semilla da la misma columna en " + x + ", " + z);
			}
		}
		map(one, "dimension_mapa_semilla_1.png");
		map(two, "dimension_mapa_semilla_42.png");
		helper.succeed();
	}

	/** A map of the plateau from above, one pixel a block, coloured by what is on top. */
	private static void map(StarYardLayout layout, String name) {
		int half = 300;
		BufferedImage image = new BufferedImage(half * 2, half * 2, BufferedImage.TYPE_INT_RGB);
		for (int x = -half; x < half; x++) {
			for (int z = -half; z < half; z++) {
				BlockState[] column = StarYardGenerator.column(layout, x, z);
				int colour = 0x0A0812;
				for (int y = column.length - 1; y >= 0; y--) {
					if (column[y] != null && !column[y].isAir()) {
						colour = colourOf(column[y], y);
						break;
					}
				}
				image.setRGB(x + half, z + half, colour);
			}
		}
		try {
			Path out = Path.of(name).toAbsolutePath();
			javax.imageio.ImageIO.write(image, "png", out.toFile());
			dev.forja.Forja.LOGGER.info("Cementerio entre Estrellas: mapa de la semilla {} en {}", layout.seed, out);
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int colourOf(BlockState state, int y) {
		int base;
		if (state.is(ModBlocks.METAL_FUNDIDO)) {
			base = 0xFF8A1E;
		} else if (state.is(ModBlocks.ARMA_CLAVADA)) {
			base = 0xB07050;
		} else if (state.is(ModBlocks.CENIZA)) {
			base = 0x8A8494;
		} else if (state.is(ModBlocks.CENIZA_PRENSADA)) {
			base = 0x4A4652;
		} else if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) {
			base = 0x2A1E3C;
		} else {
			base = 0x3A3840;
		}
		// Shaded by height, so the islets and the roll of the ash show.
		float shade = Math.max(0.5F, Math.min(1.25F, 0.6F + (y - 40) / 100.0F));
		int r = Math.min(255, (int) ((base >> 16 & 0xFF) * shade));
		int g = Math.min(255, (int) ((base >> 8 & 0xFF) * shade));
		int b = Math.min(255, (int) ((base & 0xFF) * shade));
		return r << 16 | g << 8 | b;
	}

	/** Cheap: every column of 400 chunks at the edge, where there is the most in them, in well under three seconds. */
	@GameTest(maxTicks = 20)
	public void generatingItIsCheap(GameTestHelper helper) {
		StarYardLayout layout = StarYardLayout.of(20_260_929L);
		long start = System.nanoTime();
		int blocks = 0;
		for (int x = 80; x < 80 + 16 * 20; x++) {
			for (int z = -160; z < -160 + 16 * 20; z++) {
				for (BlockState state : StarYardGenerator.column(layout, x, z)) {
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

	/**
	 * A grave weapon goes with the ground under it, dropping nothing, and broken by hand drops nothing
	 * either (Andy, 2026-09-29: it used to hang in the air).
	 */
	@GameTest(maxTicks = 40)
	public void aGraveFallsWithItsGroundAndDropsNothing(GameTestHelper helper) {
		var level = helper.getLevel();
		BlockPos ground = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos grave = ground.above();
		BlockPos other = helper.absolutePos(new BlockPos(3, 2, 1));
		level.setBlockAndUpdate(ground, ModBlocks.CENIZA.defaultBlockState());
		level.setBlockAndUpdate(other.below(), ModBlocks.CENIZA.defaultBlockState());
		BlockState weapon = ModBlocks.ARMA_CLAVADA.defaultBlockState().setValue(GraveWeaponBlock.FACING, Direction.SOUTH);
		level.setBlockAndUpdate(grave, weapon);
		level.setBlockAndUpdate(other, weapon.setValue(GraveWeaponBlock.ARMA, GraveWeaponBlock.Weapon.HACHA));
		helper.assertTrue(level.getBlockState(grave).is(ModBlocks.ARMA_CLAVADA), "el arma clavada se pone sobre la ceniza");
		level.destroyBlock(ground, false);
		level.destroyBlock(other, true);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(level.getBlockState(grave).isAir(), "sin suelo, el arma desaparece: " + level.getBlockState(grave));
			helper.assertTrue(level.getBlockState(other).isAir(), "rota a mano, también");
			var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(grave).inflate(4.0));
			helper.assertTrue(drops.isEmpty(), "y no suelta nada: " + drops);
			helper.assertFalse(ModBlocks.ARMA_CLAVADA.defaultBlockState().canSurvive(level, grave), "ni se puede poner en el aire");
			helper.succeed();
		});
	}
}
