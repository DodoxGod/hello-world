package dev.forja.world;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.forja.block.GraveWeaponBlock;
import dev.forja.block.MoltenMetalBlock;
import dev.forja.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The generator of the Cementerio entre Estrellas (docs/HERRERO_DIMENSION.md, 2.2 to 2.6).
 *
 * <p>Deliberately cheap. There is no noise here at all: every column is worked out from a handful of
 * sines and a hash ({@link StarYard}), everything off the plateau is air, and there are no structures
 * or features to place. A chunk costs the blocks it sets and nothing else, so the arena loads the
 * moment somebody arrives and {@code /locate} has nothing to search.
 *
 * <p>Two passes per chunk. The columns first — the plateau's strata, the arena, the rivers and their
 * walls, the falls, the graves — each of which depends on its own column alone. Then the cold forges,
 * which are small ruins a few blocks across: each belongs to a 24-block cell, and every chunk the ruin
 * overlaps stamps the part of it that falls inside that chunk, so no chunk ever waits on another.
 */
public final class StarYardGenerator extends ChunkGenerator {
	public static final MapCodec<StarYardGenerator> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(RegistryOps.retrieveElement(StarYard.BIOME)).apply(instance, instance.stable(StarYardGenerator::new))
	);

	private static final int MIN_Y = 0;
	private static final int HEIGHT = 256;
	/** How far a fall of molten metal pours before the void has it. */
	private static final int FALL_BOTTOM = 4;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
	private static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
	private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
	private static final BlockState POLISHED = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
	private static final BlockState BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	private static final BlockState CRACKED = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	private static final BlockState CHISELED = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
	private static final BlockState GILDED = Blocks.GILDED_BLACKSTONE.defaultBlockState();
	private static final BlockState WALL = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
	private static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
	private static final BlockState SMOOTH_BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();

	public StarYardGenerator(Holder.Reference<Biome> biome) {
		super(new FixedBiomeSource(biome));
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	// ------------------------------------------------------------------ what ChunkGenerator asks for

	@Override
	public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
		StructureManager structureManager, ChunkAccess chunk) {
	}

	@Override
	public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
	}

	@Override
	public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
	}

	@Override
	public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager,
		ChunkAccess chunk, StructureTemplateManager templates, ResourceKey<Level> level) {
	}

	/** Nothing to find here, and saying so at once is what keeps {@code /locate} from walking the void. */
	@Override
	public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> wanted,
		BlockPos pos, int radius, boolean createReference) {
		return null;
	}

	@Override
	public int getGenDepth() {
		return HEIGHT;
	}

	@Override
	public int getSeaLevel() {
		return MIN_Y;
	}

	@Override
	public int getMinY() {
		return MIN_Y;
	}

	@Override
	public int getSpawnHeight(LevelHeightAccessor heightAccessor) {
		return StarYard.SURFACE + 1;
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
		BlockState[] states = column(x, z);
		for (int y = states.length - 1; y >= 0; y--) {
			if (states[y] != null && type.isOpaque().test(states[y])) {
				return MIN_Y + y + 1;
			}
		}
		return MIN_Y;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
		BlockState[] states = column(x, z);
		for (int y = 0; y < states.length; y++) {
			if (states[y] == null) {
				states[y] = AIR;
			}
		}
		return new NoiseColumn(MIN_Y, states);
	}

	@Override
	public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feet) {
		result.add("Cementerio entre Estrellas: r " + Math.round(StarYard.radius(feet.getX() + 0.5, feet.getZ() + 0.5)));
	}

	/** Everything the generator puts in one column, bottom (y 0) up, with null for air: for the tests too. */
	public static BlockState[] column(int x, int z) {
		BlockState[] states = new BlockState[HEIGHT];
		fillColumn(x, z, (y, state, reshape) -> {
			if (y >= MIN_Y && y < MIN_Y + HEIGHT) {
				states[y - MIN_Y] = state;
			}
		});
		return states;
	}

	// ------------------------------------------------------------------ the chunk

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
		ChunkPos at = chunk.getPos();
		int minX = at.getMinBlockX();
		int minZ = at.getMinBlockZ();
		Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int x = minX + lx;
				int z = minZ + lz;
				fillColumn(x, z, (y, state, reshape) -> put(chunk, pos.set(x, y, z), state, reshape, floor, surface));
			}
		}
		// The cold forges, over the columns: every cell whose ruin could reach into this chunk.
		for (int cellX = Math.floorDiv(minX - 8, FORGE_CELL); cellX <= Math.floorDiv(minX + 23, FORGE_CELL); cellX++) {
			for (int cellZ = Math.floorDiv(minZ - 8, FORGE_CELL); cellZ <= Math.floorDiv(minZ + 23, FORGE_CELL); cellZ++) {
				Forge forge = forgeAt(cellX, cellZ);
				if (forge != null) {
					stampForge(forge, minX, minZ, (x, y, z, state, reshape) -> put(chunk, pos.set(x, y, z), state, reshape, floor, surface));
				}
			}
		}
		return CompletableFuture.completedFuture(chunk);
	}

	private static void put(ChunkAccess chunk, BlockPos pos, BlockState state, boolean reshape, Heightmap floor, Heightmap surface) {
		if (pos.getY() < MIN_Y || pos.getY() >= MIN_Y + HEIGHT) {
			return;
		}
		chunk.setBlockState(pos, state);
		floor.update(pos.getX() & 15, pos.getY(), pos.getZ() & 15, state);
		surface.update(pos.getX() & 15, pos.getY(), pos.getZ() & 15, state);
		if (reshape) {
			// Walls join their neighbours when the chunk is finished, the way structures ask for it.
			chunk.markPosForPostProcessing(pos.immutable());
		}
	}

	/** Somewhere a column's blocks go: the chunk, or an array for {@link #getBaseColumn}. */
	@FunctionalInterface
	interface ColumnSink {
		void set(int y, BlockState state, boolean reshape);
	}

	@FunctionalInterface
	interface BlockSink {
		void set(int x, int y, int z, BlockState state, boolean reshape);
	}

	// ------------------------------------------------------------------ one column

	/**
	 * Everything in the column at (x, z), bottom to top. The one place the plateau's look is decided;
	 * see the doc's table in 2.3 for the zones.
	 */
	static void fillColumn(int x, int z, ColumnSink sink) {
		double cx = x + 0.5;
		double cz = z + 0.5;
		double r = StarYard.radius(cx, cz);
		double theta = StarYard.bearing(cx, cz);
		double reach = StarYard.edge(theta);
		double river = StarYard.riverDistance(cx, cz);
		if (r >= reach) {
			// Off the edge: a fall where a river pours over it, and the islets; otherwise the void.
			if (river < StarYard.RIVER_HALF && r < reach + 2.2) {
				int top = StarYard.surface(x, z) - 1;
				BlockState fall = ModBlocks.METAL_FUNDIDO.defaultBlockState().setValue(MoltenMetalBlock.CAE, true);
				for (int y = FALL_BOTTOM; y <= top; y++) {
					sink.set(y, fall, false);
				}
			}
			islets(x, z, sink);
			return;
		}
		int top = StarYard.surface(x, z);
		int bottom = StarYard.bottom(x, z);
		// The strata: basalt at the very bottom, blackstone, then the obsidian slab of the workshop.
		for (int y = bottom; y < top; y++) {
			sink.set(y, stratum(x, y, z, top, bottom, r), false);
		}
		if (r <= StarYard.RIM) {
			arena(x, z, r, theta, top, sink);
			return;
		}
		if (pillar(x, z, top, sink)) {
			return;
		}
		double ash = StarYard.APRON + 2.5 * Math.sin(5.0 * theta + 0.3) + (StarYard.hash(x, z, 3) % 3L);
		if (r < ash) {
			apron(x, z, top, sink);
			return;
		}
		// The ash plain, with its rivers, springs, graves and rim.
		if (spring(x, z, cx, cz, top, river, sink)) {
			return;
		}
		boolean bridge = StarYard.onBridge(cx, cz);
		if (river < StarYard.RIVER_HALF) {
			sink.set(top - 2, molten(), false);
			sink.set(top - 1, molten(), false);
			if (bridge) {
				sink.set(top, BRICKS, false);
				// The deck's two sides are walled over the bed, so nobody walks off a bridge into it.
				double across = bridgeOffset(cx, cz);
				if (across >= 1.5) {
					sink.set(top + 1, WALL, true);
				}
			} else {
				sink.set(top, AIR, false);
			}
			return;
		}
		if (river < StarYard.BANK) {
			sink.set(top, BRICKS, false);
			if (!bridge) {
				sink.set(top + 1, WALL, true);
			}
			return;
		}
		if (reach - r < 4.0) {
			// The lip of the plateau: bare rock, nothing buried in it.
			long h = StarYard.hash(x, z, 4) % 10L;
			sink.set(top, h < 5 ? OBSIDIAN : h < 8 ? BLACKSTONE : BASALT, false);
			return;
		}
		plain(x, z, cx, cz, r, top, river, sink);
	}

	private static BlockState molten() {
		return ModBlocks.METAL_FUNDIDO.defaultBlockState();
	}

	/** How far across a bridge's deck this point is from its middle line. */
	private static double bridgeOffset(double x, double z) {
		double r = StarYard.radius(x, z);
		double best = Double.MAX_VALUE;
		for (double ring : StarYard.BRIDGES) {
			best = Math.min(best, Math.abs(r - ring));
		}
		return best;
	}

	private static BlockState stratum(int x, int y, int z, int top, int bottom, double r) {
		if (y == bottom) {
			return BASALT;
		}
		int depth = top - y;
		if (depth == 1 && r > StarYard.APRON + 3.0) {
			return ModBlocks.CENIZA.defaultBlockState();
		}
		// The workshop's slab: the black band that shows in every cliff.
		if (depth <= 4) {
			return OBSIDIAN;
		}
		if (StarYard.hash(x * 31 + y, z, 5) % 97L == 0L) {
			return CRYING;
		}
		return y % 9 == 0 || y % 9 == 1 ? BASALT : BLACKSTONE;
	}

	/**
	 * The arena: flat obsidian, a crying obsidian disc where he will land, eight spokes and two rings,
	 * and a low wall at 23 with four doors five wide. Nothing in it stands up to trip anybody.
	 */
	private static void arena(int x, int z, double r, double theta, int top, ColumnSink sink) {
		BlockState floor = OBSIDIAN;
		double spoke = Math.abs(Math.IEEEremainder(theta, Math.PI / 4.0)) * r;
		if (r < 3.0) {
			floor = CRYING;
		} else if (r < 3.9) {
			floor = CHISELED;
		} else if (r >= 10.0 && r < 11.0) {
			floor = CHISELED;
		} else if (r >= 20.0) {
			floor = StarYard.hash(x, z, 6) % 5L == 0L ? CRACKED : BRICKS;
		} else if (spoke < 0.55) {
			floor = POLISHED;
		} else if (StarYard.hash(x, z, 7) % 61L == 0L) {
			floor = GILDED;
		}
		sink.set(top, floor, false);
		if (r >= 22.5 && r < 23.5 && Math.abs(x + 0.5) > 2.5 && Math.abs(z + 0.5) > 2.5) {
			sink.set(top + 1, WALL, true);
		}
	}

	/** The four braziers at the arena's corners: a pillar of brick with molten metal in a bowl on top. */
	private static boolean pillar(int x, int z, int top, ColumnSink sink) {
		for (int k = 0; k < 4; k++) {
			double angle = Math.PI / 4.0 + k * Math.PI / 2.0;
			int px = (int) Math.round(Math.cos(angle) * StarYard.PILLAR_RING);
			int pz = (int) Math.round(Math.sin(angle) * StarYard.PILLAR_RING);
			int dx = x - px;
			int dz = z - pz;
			if (Math.abs(dx) > 1 || Math.abs(dz) > 1) {
				continue;
			}
			sink.set(top, BRICKS, false);
			for (int y = top + 1; y <= top + 6; y++) {
				sink.set(y, y == top + 3 && dx == 0 && dz == 0 ? CHISELED : (dx == 0 || dz == 0) ? BRICKS : POLISHED, false);
			}
			if (dx == 0 && dz == 0) {
				sink.set(top + 7, molten(), false);
			} else {
				sink.set(top + 7, WALL, true);
			}
			return true;
		}
		return false;
	}

	/** The apron between the arena and the ash: obsidian with a little of everything black in it. */
	private static void apron(int x, int z, int top, ColumnSink sink) {
		double ax = x + 0.5 - (StarYard.ARRIVAL.getX() + 0.5);
		double az = z + 0.5 - (StarYard.ARRIVAL.getZ() + 0.5);
		double arrival = Math.sqrt(ax * ax + az * az);
		if (arrival < 2.6) {
			sink.set(top, BRICKS, false);
			return;
		}
		if (arrival < 3.6) {
			sink.set(top, CRYING, false);
			return;
		}
		long h = StarYard.hash(x, z, 8) % 100L;
		sink.set(top, h < 68 ? OBSIDIAN : h < 80 ? BLACKSTONE : h < 92 ? POLISHED : h < 96 ? CRACKED : CRYING, false);
	}

	/**
	 * Where each river rises: a round basin walled all round but for where the river leaves it, and a
	 * lintel on two pillars across it with the metal pouring out from under it.
	 */
	private static boolean spring(int x, int z, double cx, double cz, int top, double river, ColumnSink sink) {
		for (int k = 0; k < StarYard.RIVERS.length; k++) {
			Vec3 source = StarYard.spring(k);
			double dx = cx - source.x;
			double dz = cz - source.z;
			double distance = Math.sqrt(dx * dx + dz * dz);
			if (distance > 6.0) {
				continue;
			}
			double ux = source.x / StarYard.RIVER_START;
			double uz = source.z / StarYard.RIVER_START;
			double along = dx * ux + dz * uz;
			double across = -dx * uz + dz * ux;
			boolean arch = Math.abs(along) < 0.5;
			if (arch && Math.abs(Math.abs(across) - 4.5) < 0.5) {
				sink.set(top, BRICKS, false);
				for (int y = top + 1; y <= top + 7; y++) {
					sink.set(y, y == top + 7 ? CHISELED : BRICKS, false);
				}
				return true;
			}
			if (arch && Math.abs(across) < 4.0) {
				sink.set(top + 7, Math.abs(across) < 0.5 ? CHISELED : BRICKS, false);
			}
			if (distance < 3.5) {
				sink.set(top - 2, molten(), false);
				sink.set(top - 1, molten(), false);
				sink.set(top, AIR, false);
				if (arch && Math.abs(across) < 0.5) {
					BlockState fall = molten().setValue(MoltenMetalBlock.CAE, true);
					for (int y = top; y <= top + 6; y++) {
						sink.set(y, fall, false);
					}
				}
				return true;
			}
			if (distance < 4.6 && river >= StarYard.RIVER_HALF) {
				sink.set(top, BRICKS, false);
				sink.set(top + 1, WALL, true);
				return true;
			}
		}
		return false;
	}

	/** The ash, and the graves in it. */
	private static void plain(int x, int z, double cx, double cz, double r, int top, double river, ColumnSink sink) {
		double patch = Math.sin(cx * 0.071 + Math.sin(cz * 0.05)) * Math.cos(cz * 0.064 - 0.8);
		double outcrop = Math.sin(cx * 0.11 + 1.7) * Math.sin(cz * 0.097 + 0.4);
		boolean nearWater = river < StarYard.BANK + 1.5;
		if (outcrop > 0.86 && !nearWater) {
			sink.set(top, SMOOTH_BASALT, false);
			if (StarYard.hash(x, z, 9) % 3L != 0L) {
				sink.set(top + 1, StarYard.hash(x, z, 10) % 2L == 0L ? SMOOTH_BASALT : BASALT, false);
			}
			return;
		}
		BlockState ground = patch > 0.55 ? ModBlocks.CENIZA_PRENSADA.defaultBlockState() : ModBlocks.CENIZA.defaultBlockState();
		BlockState grave = nearWater || nearSpring(cx, cz) ? null : grave(x, z, cx, cz, r);
		if (grave != null && StarYard.hash(x, z, 22) % 5L == 0L) {
			ground = ModBlocks.CENIZA_PRENSADA.defaultBlockState();
		}
		sink.set(top, ground, false);
		if (grave != null) {
			sink.set(top + 1, grave, false);
		}
	}

	private static boolean nearSpring(double cx, double cz) {
		for (int k = 0; k < StarYard.RIVERS.length; k++) {
			Vec3 source = StarYard.spring(k);
			if (Math.abs(cx - source.x) < 7.0 && Math.abs(cz - source.z) < 7.0) {
				return true;
			}
		}
		return false;
	}

	/** Rows of graves round the arena out to 72, facing it; scattered ones, and fields of them, beyond. */
	static @Nullable BlockState grave(int x, int z, double cx, double cz, double r) {
		if (r < StarYard.APRON + 2.0) {
			return null;
		}
		if (r <= StarYard.ROWS_END) {
			int ring = (int) Math.round((r - 38.0) / 4.0);
			if (ring < 0) {
				return null;
			}
			double rr = 38.0 + 4.0 * ring;
			int slots = (int) Math.round(Math.PI * 2.0 * rr / 3.2);
			double theta = StarYard.bearing(cx, cz);
			int slot = Math.floorMod((int) Math.round(theta / (Math.PI * 2.0) * slots), slots);
			double at = slot * Math.PI * 2.0 / slots;
			// The one column the slot's exact point falls in, so each grave in a row is one block.
			if ((int) Math.floor(Math.cos(at) * rr) != x || (int) Math.floor(Math.sin(at) * rr) != z) {
				return null;
			}
			if (StarYard.hash(ring, slot, 21) % 7L == 0L) {
				return null;
			}
			Direction facing = Math.abs(cz) > Math.abs(cx) ? Direction.NORTH : Direction.EAST;
			return graveState(StarYard.hash(ring, slot, 23), facing);
		}
		boolean field = StarYard.hash(x >> 4, z >> 4, 30) % 5L == 0L;
		long roll = StarYard.hash(x, z, 31) % 1000L;
		if (roll >= (field ? 160L : 50L)) {
			return null;
		}
		long h = StarYard.hash(x, z, 32);
		return graveState(h, Direction.from2DDataValue((int) (h >> 40 & 3L)));
	}

	static BlockState graveState(long h, Direction facing) {
		GraveWeaponBlock.Weapon[] weapons = GraveWeaponBlock.Weapon.values();
		return ModBlocks.ARMA_CLAVADA.defaultBlockState()
			.setValue(GraveWeaponBlock.FACING, facing)
			.setValue(GraveWeaponBlock.ARMA, weapons[(int) (h % weapons.length)])
			.setValue(GraveWeaponBlock.INCLINACION, (int) (h >> 20 & 0xFFL) % 3);
	}

	/** The islets that float round the plateau: what is on the horizon when you look off the edge. */
	private static void islets(int x, int z, ColumnSink sink) {
		for (int i = 0; i < StarYard.ISLETS.length; i++) {
			int[] islet = StarYard.ISLETS[i];
			double dx = x + 0.5 - islet[0];
			double dz = z + 0.5 - islet[1];
			double d = Math.sqrt(dx * dx + dz * dz);
			double reach = islet[3] * (1.0 + 0.14 * Math.sin(5.0 * Math.atan2(dz, dx) + i));
			if (d >= reach) {
				continue;
			}
			int top = islet[2] + (int) Math.round(Math.sin(x * 0.3) * Math.cos(z * 0.27));
			int bottom = top - 2 - (int) Math.round((reach - d) * 1.4) - (int) (StarYard.hash(x, z, 40) & 1L);
			for (int y = bottom; y < top; y++) {
				sink.set(y, top - y <= 2 ? OBSIDIAN : y % 5 == 0 ? BASALT : BLACKSTONE, false);
			}
			boolean ash = d < reach * 0.55;
			sink.set(top, ash ? ModBlocks.CENIZA.defaultBlockState() : OBSIDIAN, false);
			if (ash && StarYard.hash(x, z, 41) % 12L == 0L) {
				long h = StarYard.hash(x, z, 42);
				sink.set(top + 1, graveState(h, Direction.from2DDataValue((int) (h >> 40 & 3L))), false);
			}
			return;
		}
	}

	// ------------------------------------------------------------------ the cold forges

	static final int FORGE_CELL = 24;

	/** One cold forge: where its floor's middle is, which of the three it is and which way it faces. */
	record Forge(int x, int z, int floor, int kind, Rotation rotation) {
	}

	/** The forge of this cell, if it has one: one cell in two, away from the arena, the rivers and the edge. */
	static @Nullable Forge forgeAt(int cellX, int cellZ) {
		long h = StarYard.hash(cellX, cellZ, 50);
		if (h % 2L != 0L) {
			return null;
		}
		int x = cellX * FORGE_CELL + 12 + (int) (h >> 8 & 0xFFL) % 9 - 4;
		int z = cellZ * FORGE_CELL + 12 + (int) (h >> 16 & 0xFFL) % 9 - 4;
		double cx = x + 0.5;
		double cz = z + 0.5;
		double r = StarYard.radius(cx, cz);
		if (r < 76.0 || r > StarYard.edge(StarYard.bearing(cx, cz)) - 14.0) {
			return null;
		}
		if (StarYard.riverDistance(cx, cz) < 12.0 || StarYard.onBridge(cx, cz)) {
			return null;
		}
		return new Forge(x, z, StarYard.surface(x, z), (int) (h >> 24 & 0xFFL) % 3, Rotation.values()[(int) (h >> 32 & 3L)]);
	}

	/** Every cold forge on the plateau, for the tests and for anyone who wants to find one. */
	public static java.util.List<BlockPos> forges() {
		java.util.List<BlockPos> found = new java.util.ArrayList<>();
		int cells = 200 / FORGE_CELL + 1;
		for (int i = -cells; i <= cells; i++) {
			for (int j = -cells; j <= cells; j++) {
				Forge forge = forgeAt(i, j);
				if (forge != null) {
					found.add(new BlockPos(forge.x(), forge.floor(), forge.z()));
				}
			}
		}
		return found;
	}

	/** The part of this forge that falls inside the chunk starting at (minX, minZ). */
	private static void stampForge(Forge forge, int minX, int minZ, BlockSink sink) {
		long seed = StarYard.hash(forge.x(), forge.z(), 51);
		// Its floor first: level, cleared of graves and ash above it, filled below it.
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int x = forge.x() + dx;
				int z = forge.z() + dz;
				if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16) {
					continue;
				}
				boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
				int local = StarYard.surface(x, z);
				for (int y = forge.floor() + 1; y <= Math.max(local + 2, forge.floor() + 8); y++) {
					sink.set(x, y, z, AIR, false);
				}
				for (int y = Math.min(local, forge.floor()) - 1; y < forge.floor(); y++) {
					sink.set(x, y, z, BLACKSTONE, false);
				}
				BlockState floor = corner && forge.kind() == 1 ? ModBlocks.CENIZA.defaultBlockState()
					: StarYard.hash(x, z, 52) % 10L < 3L ? CRACKED : BRICKS;
				sink.set(x, forge.floor(), z, floor, false);
			}
		}
		for (Piece piece : pieces(forge.kind(), seed)) {
			BlockPos turned = new BlockPos(piece.dx(), piece.dy(), piece.dz()).rotate(forge.rotation());
			int x = forge.x() + turned.getX();
			int z = forge.z() + turned.getZ();
			if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16) {
				continue;
			}
			sink.set(x, forge.floor() + turned.getY(), z, piece.state().rotate(forge.rotation()), piece.reshape());
		}
	}

	record Piece(int dx, int dy, int dz, BlockState state, boolean reshape) {
		Piece(int dx, int dy, int dz, BlockState state) {
			this(dx, dy, dz, state, false);
		}
	}

	/** The three ruins, laid out facing south (their open side towards +Z) before they are turned. */
	private static List<Piece> pieces(int kind, long seed) {
		List<Piece> pieces = new java.util.ArrayList<>();
		BlockState ash = ModBlocks.CENIZA.defaultBlockState();
		switch (kind) {
			case 0 -> {
				// La fragua fría: a broken back wall, a cold blast furnace under its chimney, an anvil,
				// a cauldron and a grindstone.
				for (int dx = -3; dx <= 3; dx++) {
					int height = 1 + (int) ((seed >> (dx + 3) * 2) & 3L) % 3;
					for (int dy = 1; dy <= height; dy++) {
						pieces.add(new Piece(dx, dy, -3, (seed >> (dx + 10)) % 3L == 0L ? CRACKED : BRICKS));
					}
				}
				for (int side = -3; side <= 3; side += 6) {
					pieces.add(new Piece(side, 1, -2, BRICKS));
					pieces.add(new Piece(side, 1, -1, WALL, true));
				}
				pieces.add(new Piece(0, 1, -2, Blocks.BLAST_FURNACE.defaultBlockState()
					.setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH).setValue(AbstractFurnaceBlock.LIT, false)));
				pieces.add(new Piece(-1, 1, -2, BRICKS));
				pieces.add(new Piece(1, 1, -2, BRICKS));
				for (int dy = 2; dy <= 5; dy++) {
					pieces.add(new Piece(0, dy, -2, dy == 2 ? CHISELED : BRICKS));
				}
				pieces.add(new Piece(0, 6, -2, WALL, true));
				pieces.add(new Piece(0, 1, 1, Blocks.DAMAGED_ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST)));
				pieces.add(new Piece(2, 1, -1, Blocks.CAULDRON.defaultBlockState()));
				pieces.add(new Piece(-2, 1, 0, Blocks.GRINDSTONE.defaultBlockState()
					.setValue(GrindstoneBlock.FACE, AttachFace.FLOOR).setValue(GrindstoneBlock.FACING, Direction.NORTH)));
				pieces.add(new Piece(-2, 1, -2, ash));
				pieces.add(new Piece(2, 1, 2, graveState(seed >> 3, Direction.NORTH)));
			}
			case 1 -> {
				// Los yunques rotos: three anvils round a grindstone, a broken pillar at two corners.
				pieces.add(new Piece(0, 1, 0, Blocks.GRINDSTONE.defaultBlockState()
					.setValue(GrindstoneBlock.FACE, AttachFace.FLOOR).setValue(GrindstoneBlock.FACING, Direction.EAST)));
				pieces.add(new Piece(-2, 1, -1, Blocks.CHIPPED_ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.NORTH)));
				pieces.add(new Piece(2, 1, -1, Blocks.DAMAGED_ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST)));
				pieces.add(new Piece(0, 1, 2, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.WEST)));
				for (int dy = 1; dy <= 3; dy++) {
					pieces.add(new Piece(-3, dy, -3, dy == 3 ? CRACKED : BRICKS));
				}
				pieces.add(new Piece(-3, 4, -3, WALL, true));
				pieces.add(new Piece(3, 1, 3, BRICKS));
				pieces.add(new Piece(3, 2, 3, WALL, true));
				pieces.add(new Piece(1, 1, 1, graveState(seed >> 5, Direction.EAST)));
				pieces.add(new Piece(-1, 1, 1, graveState(seed >> 9, Direction.NORTH)));
				pieces.add(new Piece(2, 1, 2, graveState(seed >> 13, Direction.EAST)));
			}
			default -> {
				// El horno hundido: a cold smoker in a brick hood, a dead soul fire, coal under ash.
				pieces.add(new Piece(0, 1, -2, Blocks.SMOKER.defaultBlockState()
					.setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH).setValue(AbstractFurnaceBlock.LIT, false)));
				for (int dx = -1; dx <= 1; dx++) {
					if (dx != 0) {
						pieces.add(new Piece(dx, 1, -2, BRICKS));
					}
					pieces.add(new Piece(dx, 2, -2, dx == 0 ? CHISELED : CRACKED));
				}
				pieces.add(new Piece(0, 3, -2, WALL, true));
				pieces.add(new Piece(0, 1, 0, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)));
				pieces.add(new Piece(2, 1, 1, Blocks.COAL_BLOCK.defaultBlockState()));
				pieces.add(new Piece(2, 1, 2, Blocks.COAL_BLOCK.defaultBlockState()));
				pieces.add(new Piece(1, 1, 2, Blocks.COAL_BLOCK.defaultBlockState()));
				pieces.add(new Piece(2, 2, 2, ash));
				pieces.add(new Piece(1, 2, 2, ash));
				for (int dz = -3; dz <= 0; dz++) {
					pieces.add(new Piece(-3, 1, dz, WALL, true));
					if (dz < -1) {
						pieces.add(new Piece(-3, 2, dz, WALL, true));
					}
				}
				pieces.add(new Piece(-1, 1, 2, graveState(seed >> 7, Direction.NORTH)));
			}
		}
		return pieces;
	}
}
