package dev.forja.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import dev.forja.world.BastionGround;
import dev.forja.world.BastionHome;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * A great castle near spawn in every world (world/BastionHome): Andy's nearest one was 26,000 blocks out
 * (2026-09-30), and he wants one within 5,000 of the origin every time, still on flat, dry ground.
 *
 * <p>These build real overworlds for other seeds (the generator, its structure state, the castle's placement and
 * site test, exactly as a new world does) without generating a single chunk, and ask them where the castle is.
 */
public class BastionCercaGameTests {
	/** Half the side of the square around the origin that must hold a castle. */
	static final int SQUARE = 5000;

	/** One overworld for one seed: what a new world builds before its first chunk. */
	record World(long seed, NoiseBasedChunkGenerator generator, RandomState random, ChunkGeneratorStructureState state,
		RandomSpreadStructurePlacement placement, Holder<Structure> structure, LevelHeightAccessor heights) {
		static World of(RegistryAccess access, long seed) {
			var parameters = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
			var settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
			NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), settings);
			RandomState random = RandomState.create(access, NoiseGeneratorSettings.OVERWORLD, seed);
			ChunkGeneratorStructureState state = generator.createState(access.lookupOrThrow(Registries.STRUCTURE_SET), random, seed);
			StructureSet set = access.lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(BastionHome.SET).value();
			return new World(seed, generator, random, state, (RandomSpreadStructurePlacement) set.placement(), set.structures().getFirst().structure(),
				LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth()));
		}

		/** Whether the castle really starts at this chunk: the placement with its exclusion zone, then the structure itself. */
		String verdict(GameTestHelper helper, ChunkPos at) {
			if (!this.placement.isStructureChunk(this.state, at.x(), at.z())) {
				return "EXCLUSION";
			}
			var context = new Structure.GenerationContext(helper.getLevel().registryAccess(), this.generator, this.generator.getBiomeSource(), this.random,
				helper.getLevel().getServer().getStructureManager(), this.seed, at, this.heights, this.structure.value().biomes()::contains);
			Optional<Structure.GenerationStub> stub = this.structure.value().findValidGenerationPoint(context);
			if (stub.isPresent()) {
				return "OK";
			}
			BastionGround.Verdict why = BastionGround.lastVerdict();
			return why == BastionGround.Verdict.OK ? "VANILLA_BIOME" : why.name();
		}

		/** The grid's candidate for a cell, whatever the home says. */
		ChunkPos candidate(int cellX, int cellZ) {
			int spacing = this.placement.spacing();
			return this.placement.getPotentialStructureChunk(this.seed, cellX * spacing, cellZ * spacing);
		}
	}

	/** What {@code /locate} does: rings of cells outwards from the origin, the first ring with a castle wins. */
	static ChunkPos locate(GameTestHelper helper, World world, int maxRings) {
		for (int ring = 0; ring <= maxRings; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					ChunkPos at = world.candidate(dx, dz);
					if (world.verdict(helper, at).equals("OK")) {
						return at;
					}
				}
			}
		}
		return null;
	}

	/** The nearest castle to the origin (by the block /locate reports), looking one ring past the first found. */
	static ChunkPos nearest(GameTestHelper helper, World world, int maxRings, Map<String, Integer> inSquare) {
		ChunkPos best = null;
		int stopAt = maxRings;
		for (int ring = 0; ring <= stopAt; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					ChunkPos at = world.candidate(dx, dz);
					String verdict = world.verdict(helper, at);
					if (inSquare != null && inside(at)) {
						inSquare.merge(verdict, 1, Integer::sum);
					}
					if (verdict.equals("OK") && (best == null || distance(at) < distance(best))) {
						best = at;
						stopAt = Math.min(stopAt, ring + 1);
					}
				}
			}
		}
		return best;
	}

	static boolean inside(ChunkPos at) {
		return Math.abs(at.getMinBlockX()) <= SQUARE && Math.abs(at.getMinBlockZ()) <= SQUARE;
	}

	static double distance(ChunkPos at) {
		return Math.sqrt((double) at.getMinBlockX() * at.getMinBlockX() + (double) at.getMinBlockZ() * at.getMinBlockZ());
	}

	/**
	 * Every world, whatever its seed, has a castle inside the square around the origin, on a site that passes the
	 * home test, which the structure itself accepts, and which {@code /locate} finds first, in seconds.
	 */
	@GameTest(maxTicks = 40)
	public void everySeedHasAHomeCastle(GameTestHelper helper) {
		RegistryAccess access = helper.getLevel().registryAccess();
		long[] seeds = {0L, 1L, -4172144997902289642L, 8486642829451938811L, 20260930L};
		for (long seed : seeds) {
			World world = World.of(access, seed);
			BastionHome home = BastionHome.forSeed(seed);
			helper.assertTrue(home != null, "la semilla " + seed + " no tiene hogar del castillo");
			ChunkPos at = home.chunk();
			helper.assertTrue(at != null, "la semilla " + seed + " no encontró sitio para el castillo de casa");
			double blocks = distance(at);
			helper.assertTrue(inside(at) && blocks >= BastionHome.MIN_BLOCKS - 16 && blocks <= BastionHome.MAX_BLOCKS + 16,
				"semilla " + seed + ": castillo de casa en " + at + " a " + (int) blocks + " bloques");
			helper.assertTrue(home.survey().rise() <= BastionHome.LIMITS.maxRise() && home.survey().wet() <= BastionHome.LIMITS.maxWet(),
				"semilla " + seed + ": sitio " + home.survey());
			helper.assertTrue(home.millis() < 3000, "semilla " + seed + ": buscar el sitio costó " + home.millis() + " ms");
			helper.assertValueEqual(world.verdict(helper, at), "OK", "semilla " + seed + ": la estructura acepta el sitio de casa");
			// the grid hands the home out for its own cell, so /locate and the map see it
			int spacing = world.placement().spacing();
			helper.assertValueEqual(world.candidate(Math.floorDiv(at.x(), spacing), Math.floorDiv(at.z(), spacing)), at, "la celda del hogar lo reparte");
			long started = System.nanoTime();
			ChunkPos found = locate(helper, world, 4);
			long ms = (System.nanoTime() - started) / 1_000_000L;
			helper.assertTrue(found != null && inside(found), "semilla " + seed + ": /locate encuentra " + found);
			helper.assertTrue(ms < 4400, "semilla " + seed + ": /locate costaría " + ms + " ms");
		}
		helper.succeed();
	}

	/**
	 * The measurement behind the home castle, only with FORJA_BASTION_SEMILLAS=&lt;file&gt;: for many seeds, the nearest
	 * castle with the grid alone and with the home, why the grid's candidates inside the square were turned down, and
	 * what the home search and a cold {@code /locate} cost. Writes a Markdown table to the file.
	 */
	@GameTest(maxTicks = 40)
	public void measureManySeeds(GameTestHelper helper) {
		String out = System.getenv("FORJA_BASTION_SEMILLAS");
		if (out == null || out.isBlank()) {
			helper.succeed();
			return;
		}
		int count = Integer.parseInt(Optional.ofNullable(System.getenv("FORJA_BASTION_N")).orElse("60"));
		RegistryAccess access = helper.getLevel().registryAccess();
		Random dice = new Random(20260930L);
		StringBuilder table = new StringBuilder();
		table.append("| # | semilla | solo rejilla: más cercano (bloques) | en ±5000 | con hogar: más cercano | en ±5000 | hogar (x, z) | desnivel | fuera de mediana | mojadas | sitios | sondeos | búsqueda ms | /locate frío ms |\n");
		table.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
		Map<String, Integer> reasons = new java.util.TreeMap<>();
		List<Double> gridOnly = new ArrayList<>();
		List<Double> withHome = new ArrayList<>();
		List<Long> locates = new ArrayList<>();
		List<Long> searches = new ArrayList<>();
		int gridInside = 0;
		int homeInside = 0;
		for (int i = 0; i < count; i++) {
			long seed = i == 0 ? 0L : dice.nextLong();
			BastionHome.setEnabled(true);
			World world = World.of(access, seed);
			long started = System.nanoTime();
			ChunkPos located = locate(helper, world, 20);
			long locateMs = (System.nanoTime() - started) / 1_000_000L;
			BastionHome home = BastionHome.forSeed(seed);
			ChunkPos homeAt = home == null ? null : home.chunk();
			ChunkPos withHomeNearest = nearest(helper, world, 20, null);
			BastionHome.setEnabled(false);
			ChunkPos gridNearest = nearest(helper, world, 20, reasons);
			BastionHome.setEnabled(true);
			double g = gridNearest == null ? Double.NaN : distance(gridNearest);
			double h = withHomeNearest == null ? Double.NaN : distance(withHomeNearest);
			gridOnly.add(g);
			withHome.add(h);
			locates.add(locateMs);
			searches.add(home == null ? -1 : home.millis());
			boolean gIn = gridNearest != null && inside(gridNearest);
			boolean hIn = withHomeNearest != null && inside(withHomeNearest);
			gridInside += gIn ? 1 : 0;
			homeInside += hIn ? 1 : 0;
			BastionGround.Survey survey = home == null ? null : home.survey();
			table.append(String.format(Locale.ROOT, "| %d | %d | %s | %s | %s | %s | %s | %s | %s | %s | %d | %d | %d | %d |%n", i, seed, Double.isNaN(g) ? "ninguno" : String.format(Locale.ROOT, "%.0f", g), gIn ? "sí" : "no", Double.isNaN(h) ? "ninguno" : String.format(Locale.ROOT, "%.0f", h),
				hIn ? "sí" : "no", homeAt == null ? "-" : homeAt.getMinBlockX() + ", " + homeAt.getMinBlockZ(), survey == null ? "-" : survey.rise(),
				survey == null ? "-" : survey.offMedian(), survey == null ? "-" : survey.wet(), home == null ? 0 : home.sites(), home == null ? 0 : home.surveys(),
				home == null ? -1 : home.millis(), locateMs));
			if (located == null || !inside(located)) {
				table.append("  (/locate encontró " + located + ")\n");
			}
		}
		StringBuilder summary = new StringBuilder();
		summary.append("# Bastión cerca: ").append(count).append(" semillas\n\n");
		summary.append(stats("Solo rejilla, distancia al más cercano", gridOnly)).append(String.format(Locale.ROOT, "; en ±5000: %d/%d (%.0f %%)%n%n", gridInside, count, 100.0 * gridInside / count));
		summary.append(stats("Con hogar, distancia al más cercano", withHome)).append(String.format(Locale.ROOT, "; en ±5000: %d/%d (%.0f %%)%n%n", homeInside, count, 100.0 * homeInside / count));
		summary.append(statsLong("Búsqueda del hogar (ms)", searches)).append("\n\n");
		summary.append(statsLong("/locate frío desde el origen (ms, con la búsqueda del hogar dentro)", locates)).append("\n\n");
		summary.append("Candidatos de la rejilla dentro de ±5000 (sin hogar), por motivo: ").append(reasons).append("\n\n");
		summary.append(table);
		try {
			Files.writeString(Path.of(out.trim()), summary.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		helper.assertValueEqual(homeInside, count, "semillas con castillo en ±5000");
		helper.succeed();
	}

	private static String stats(String name, List<Double> values) {
		List<Double> sorted = new ArrayList<>(values);
		sorted.replaceAll(v -> v.isNaN() ? Double.MAX_VALUE : v);
		sorted.sort(null);
		return String.format(Locale.ROOT, "%s: mín %s, mediana %s, máx %s", name, blocks(sorted.getFirst()), blocks(sorted.get(sorted.size() / 2)),
			blocks(sorted.getLast()));
	}

	private static String blocks(double value) {
		return value == Double.MAX_VALUE ? "ninguno en 20 anillos (35.000 bloques)" : String.format(Locale.ROOT, "%.0f", value);
	}

	private static String statsLong(String name, List<Long> values) {
		List<Long> sorted = new ArrayList<>(values);
		sorted.sort(null);
		return String.format(Locale.ROOT, "%s: mín %d, mediana %d, máx %d", name, sorted.getFirst(), sorted.get(sorted.size() / 2), sorted.getLast());
	}
}
