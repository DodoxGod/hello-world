package dev.forja.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.combat.CombatConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/**
 * The torches round a player (docs/red_mob_v4_diseno.md §2.10, block L, and §4.10): the one kind of block a monster
 * may break (Andy, 2026-09-29: torch, wall torch, soul torch, soul wall torch; nothing else) and only with mobGriefing
 * on. Found once per player at most every {@link #PERIOD} ticks, section by section (a section with no torch in its
 * palette is skipped whole), within {@link #RANGE} blocks sideways and {@link #HEIGHT} up and down.
 *
 * <p><b>aporte</b> (what putting one out takes off the block light at the player's feet): a torch lights
 * {@code emit − manhattan distance} there (14 for a torch, 10 for a soul torch); without it the feet keep the most of
 * the other torches found, the sky light as it stands now (sky − darkening) and 0, so the loss is
 * {@code max(0, min(block light, its own) − that)}. By day in the open the sky covers it and the loss is 0.
 */
public final class Lights {
	public static final int PERIOD = 10;
	public static final int RANGE = 16;
	public static final int HEIGHT = 8;
	/** A torch whose path came to nothing is left alone this long (luzK_alcanzable reads 0 meanwhile). */
	public static final int UNREACHABLE_TICKS = 100;
	/** How far below a torch a mob may stand to reach it (ASEDIAR and APAGAR_LUZ: "a ≤ 2 de altura sobre un suelo"). */
	public static final int REACH_BELOW = 2;

	/** One torch near the player: where, how much of their light it gives, and its light. */
	public record Torch(BlockPos pos, int emit, int share) {
	}

	private static final class State {
		long scannedAt = Long.MIN_VALUE / 2;
		List<Torch> torches = List.of();
		int blockLight;
	}

	private static final Map<Player, State> STATES = new WeakHashMap<>();
	/** Torches a path to which failed, and until when they are skipped. */
	private static final Map<Level, Map<BlockPos, Long>> UNREACHABLE = new WeakHashMap<>();

	private Lights() {
	}

	/** Whether a block is one a monster may put out (MobActions.isLight: the four torches). */
	public static boolean breakable(BlockState state) {
		return MobActions.isLight(state);
	}

	/** Whether monsters may put torches out at all here: the switches on and mobGriefing on (Andy). */
	public static boolean allowed(Level level) {
		CombatConfig cfg = CombatConfig.get();
		return cfg.mobActionsV4 && cfg.mobsBreakLights && level instanceof ServerLevel server && Terrain.griefing(server);
	}

	/** The block light at the player's feet /15 is jug_luz's cousin: this is the full light (sky and blocks) at their feet. */
	public static int playerLight(Player player) {
		return player.level().getMaxLocalRawBrightness(player.blockPosition());
	}

	/** The torches near the player, nearest to them first, each with its share of their light. */
	public static List<Torch> near(Player player) {
		long now = player.level().getGameTime();
		State state = STATES.computeIfAbsent(player, p -> new State());
		if (now - state.scannedAt >= PERIOD || now < state.scannedAt) {
			scan(player, state);
			state.scannedAt = now;
		}
		return state.torches;
	}

	public static void forget(Player player) {
		STATES.remove(player);
	}

	private static void scan(Player player, State state) {
		Level level = player.level();
		BlockPos feet = player.blockPosition();
		List<BlockPos> found = new ArrayList<>();
		int minX = feet.getX() - RANGE;
		int maxX = feet.getX() + RANGE;
		int minY = Math.max(level.getMinY(), feet.getY() - HEIGHT);
		int maxY = Math.min(level.getMaxY(), feet.getY() + HEIGHT);
		int minZ = feet.getZ() - RANGE;
		int maxZ = feet.getZ() + RANGE;
		for (int cx = SectionPos.blockToSectionCoord(minX); cx <= SectionPos.blockToSectionCoord(maxX); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(minZ); cz <= SectionPos.blockToSectionCoord(maxZ); cz++) {
				if (!(level.getChunkSource().getChunkNow(cx, cz) instanceof LevelChunk chunk)) {
					continue;
				}
				for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
					int index = level.getSectionIndexFromSectionY(sy);
					if (index < 0 || index >= chunk.getSections().length) {
						continue;
					}
					LevelChunkSection section = chunk.getSections()[index];
					if (section == null || section.hasOnlyAir() || !section.maybeHas(Lights::breakable)) {
						continue;
					}
					int x0 = Math.max(minX, cx << 4);
					int x1 = Math.min(maxX, (cx << 4) + 15);
					int y0 = Math.max(minY, sy << 4);
					int y1 = Math.min(maxY, (sy << 4) + 15);
					int z0 = Math.max(minZ, cz << 4);
					int z1 = Math.min(maxZ, (cz << 4) + 15);
					for (int y = y0; y <= y1; y++) {
						for (int z = z0; z <= z1; z++) {
							for (int x = x0; x <= x1; x++) {
								if (breakable(section.getBlockState(x & 15, y & 15, z & 15))) {
									found.add(new BlockPos(x, y, z));
								}
							}
						}
					}
				}
			}
		}
		found.sort(java.util.Comparator.comparingInt(p -> p.distManhattan(feet)));
		int blockLight = level.getBrightness(LightLayer.BLOCK, feet);
		int sky = Math.max(0, level.getBrightness(LightLayer.SKY, feet) - level.getSkyDarken());
		int[] own = new int[found.size()];
		int best = 0;
		int second = 0;
		for (int i = 0; i < found.size(); i++) {
			BlockPos pos = found.get(i);
			BlockState at = level.getBlockState(pos);
			int emit = at.is(Blocks.SOUL_TORCH) || at.is(Blocks.SOUL_WALL_TORCH) ? 10 : 14;
			own[i] = Math.max(0, emit - pos.distManhattan(feet));
			if (own[i] > best) {
				second = best;
				best = own[i];
			} else if (own[i] > second) {
				second = own[i];
			}
		}
		List<Torch> torches = new ArrayList<>(found.size());
		for (int i = 0; i < found.size(); i++) {
			int others = own[i] == best ? second : best;
			int share = Math.max(0, Math.min(blockLight, own[i]) - Math.max(others, sky));
			BlockState at = level.getBlockState(found.get(i));
			torches.add(new Torch(found.get(i), at.is(Blocks.SOUL_TORCH) || at.is(Blocks.SOUL_WALL_TORCH) ? 10 : 14, share));
		}
		state.torches = torches;
		state.blockLight = blockLight;
	}

	/** The torches a mob sees in block L: near the player, within {@link #RANGE} of the mob too, the two nearest the player. */
	public static List<Torch> forMob(Mob mob, Player player) {
		List<Torch> out = new ArrayList<>(2);
		double range = RANGE * RANGE;
		for (Torch torch : near(player)) {
			if (mob.distanceToSqr(Vec3.atCenterOf(torch.pos)) <= range) {
				out.add(torch);
				if (out.size() == 2) {
					break;
				}
			}
		}
		return out;
	}

	/**
	 * Where a mob could stand to reach a torch: a spot in its own column or the four beside it, at most
	 * {@link #REACH_BELOW} below it, with room for a mob and a floor under it; null for none.
	 */
	public static BlockPos standing(Level level, BlockPos torch) {
		BlockPos[] columns = {torch, torch.north(), torch.south(), torch.east(), torch.west()};
		for (int dy = 0; dy <= REACH_BELOW; dy++) {
			for (BlockPos column : columns) {
				BlockPos spot = column.below(dy);
				if (open(level, spot) && open(level, spot.above()) && floor(level, spot.below())) {
					return spot;
				}
			}
		}
		return null;
	}

	private static boolean open(Level level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	private static boolean floor(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.getCollisionShape(level, pos).isEmpty() && !state.is(Blocks.LAVA) && !state.is(Blocks.MAGMA_BLOCK);
	}

	/**
	 * luzK_alcanzable: a spot to stand on under the torch ({@link #standing}), not far above or below the mob (4 blocks),
	 * and no path to it has failed lately. The path itself is only asked for when a mob sets off (APAGAR_LUZ).
	 */
	public static boolean reachable(Mob mob, BlockPos torch) {
		Map<BlockPos, Long> failed = UNREACHABLE.get(mob.level());
		if (failed != null) {
			Long until = failed.get(torch);
			if (until != null && until > mob.level().getGameTime()) {
				return false;
			}
		}
		BlockPos spot = standing(mob.level(), torch);
		return spot != null && Math.abs(spot.getY() - mob.getY()) <= 4.0;
	}

	/** A path to this torch came to nothing: skip it for a while. */
	public static void unreachable(Level level, BlockPos torch) {
		UNREACHABLE.computeIfAbsent(level, l -> new java.util.HashMap<>()).put(torch.immutable(), level.getGameTime() + UNREACHABLE_TICKS);
	}

	/** The twelve of block L, written from {@code at}. */
	public static void observe(Mob mob, Player player, float[] out, int at) {
		List<Torch> torches = forMob(mob, player);
		double dx = player.getX() - mob.getX();
		double dz = player.getZ() - mob.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double fx = dx / d;
		double fz = dz / d;
		for (int k = 0; k < torches.size(); k++) {
			Torch torch = torches.get(k);
			int i = at + k * 6;
			Vec3 c = Vec3.atCenterOf(torch.pos);
			double ox = c.x - mob.getX();
			double oz = c.z - mob.getZ();
			out[i] = 1.0F;
			out[i + 1] = (float) ObsM1.clip((ox * fx + oz * fz) / 8.0, -2.0, 2.0);
			out[i + 2] = (float) ObsM1.clip((ox * -fz + oz * fx) / 8.0, -2.0, 2.0);
			out[i + 3] = (float) ObsM1.clip((torch.pos.getY() - mob.getY()) / 4.0, -2.0, 2.0);
			out[i + 4] = torch.share / 15.0F;
			out[i + 5] = reachable(mob, torch.pos) ? 1.0F : 0.0F;
		}
	}

	/** APAGAR_LUZ's mask: a torch in luz0 that can be reached, and torches may be put out here at all. */
	public static boolean canPutOut(Mob mob, Player player) {
		if (!allowed(mob.level())) {
			return false;
		}
		List<Torch> torches = forMob(mob, player);
		return !torches.isEmpty() && reachable(mob, torches.get(0).pos);
	}
}
