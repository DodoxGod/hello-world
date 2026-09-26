package dev.forja.upgrade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import dev.forja.forge.ForgeType;
import dev.forja.forge.Mastery;
import dev.forja.item.ForgedItems;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Veta (vein mining), Leñador (tree felling), Excavacion (area mining) and the hammer's built-in
 * 3x3. Extra blocks are broken through the player's own game mode, so tool damage, Fortune, drops
 * and protection all behave exactly like a normal break. Sneaking turns all of those off.
 * Luz (torches in the dark) and Sabiduria (ore experience) keep working while sneaking.
 */
public final class MiningUpgrades {
	/** Set while we break extra blocks, so those breaks do not chain into more. */
	/** The thread drawn between a block and the ones that came with it. */
	private static final net.minecraft.core.particles.DustParticleOptions VEIN =
		new net.minecraft.core.particles.DustParticleOptions(0xE8DDCF, 0.7F);

	private static final ThreadLocal<Boolean> BREAKING = ThreadLocal.withInitial(() -> false);

	private MiningUpgrades() {
	}

	public static void register() {
		// The tool belt puts the right tool in your hand the moment you start on a block.
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (player instanceof ServerPlayer server && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
				dev.forja.item.ToolBeltItem.swapFor(server, level.getBlockState(pos));
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (BREAKING.get() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
				return;
			}
			ItemStack tool = serverPlayer.getMainHandItem();
			if (!(tool.getItem() instanceof ForgedItems.Forged forged) || tool.isBroken()) {
				return;
			}

			BREAKING.set(true);
			try {
				if (!player.isShiftKeyDown()) {
					areaUpgrades(serverLevel, serverPlayer, pos, state, tool, forged);
				}
				oreExperience(serverLevel, pos, state, tool);
				placeTorch(serverLevel, serverPlayer, pos, tool);
				TraitEffects.echoOres(serverLevel, serverPlayer, pos, state, tool);
				// Maestria: a tool learns from every block it breaks that takes some effort.
				if (forged.forgeType().kind == ForgeType.Kind.TOOL && state.getDestroySpeed(serverLevel, pos) > 0.0F) {
					Mastery.addExperience(serverPlayer, tool, 1);
					dev.forja.forge.ItemHistory.addBlocks(tool, 1);
				}
			} finally {
				BREAKING.set(false);
			}
		});
	}

	private static void areaUpgrades(ServerLevel serverLevel, ServerPlayer serverPlayer, BlockPos pos, BlockState state, ItemStack tool, ForgedItems.Forged forged) {
		int vein = Upgrade.veinLimit(Upgrades.fraction(tool, Upgrade.VETA));
		if (vein > 0 && dev.forja.forge.Perk.has(tool, dev.forja.forge.Perk.MINERO)) {
			// Minero: the seam keeps going.
			vein = Math.round(vein * 1.5F);
		}
		if (vein > 0 && Synergy.FILON.active(tool)) {
			// Filon: quartz and lapis together follow the seam half again as far.
			vein = Math.round(vein * 1.5F);
		}
		if (vein > 0 && state.is(ConventionalBlockTags.ORES)) {
			breakAll(serverPlayer, connected(serverLevel, pos, s -> sameOre(state, s), vein));
		}

		int tree = Upgrade.treeLimit(Upgrades.fraction(tool, Upgrade.LENADOR));
		if (tree > 0 && state.is(BlockTags.LOGS)) {
			breakAll(serverPlayer, connected(serverLevel, pos, s -> s.is(BlockTags.LOGS), tree));
		}

		int excavation = Upgrade.excavationRadius(Upgrades.fraction(tool, Upgrade.EXCAVACION));
		int radius = forged.forgeType().areaRadius > 0
			? forged.forgeType().areaRadius + (excavation >= 2 ? 1 : 0)
			: excavation;
		if (radius > 0 && tool.getDestroySpeed(state) > 1.0F) {
			// Cantera: the tool only pays for the block you actually hit.
			int before = tool.getDamageValue();
			breakAll(serverPlayer, area(serverLevel, serverPlayer, pos, state, tool, radius));
			if (Synergy.CANTERA.active(tool) && tool.getDamageValue() > before) {
				tool.setDamageValue(before);
			}
		}
	}

	/** Sabiduria: mined ores give a little extra experience, unless Silk Touch keeps the ore whole. */
	private static void oreExperience(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
		int experience = Upgrade.oreExperience(Upgrades.fraction(tool, Upgrade.SABIDURIA))
			+ (dev.forja.forge.Perk.has(tool, dev.forja.forge.Perk.MINERO) ? 2 : 0);
		if (experience > 0 && state.is(ConventionalBlockTags.ORES) && tool.isCorrectToolForDrops(state) && Upgrades.fraction(tool, Upgrade.TOQUE_DE_SEDA) <= 0.0F) {
			ExperienceOrb.award(level, Vec3.atCenterOf(pos), experience);
		}
	}

	/** Luz: in the dark, the mined spot gets one of the player's torches, on the floor or on a wall. */
	private static void placeTorch(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack tool) {
		float chance = Upgrade.torchChance(Upgrades.fraction(tool, Upgrade.LUZ));
		if (chance <= 0.0F || !level.getBlockState(pos).isAir() || level.getMaxLocalRawBrightness(pos) > 7 || level.getRandom().nextFloat() >= chance) {
			return;
		}
		int torchSlot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(Items.TORCH)) {
				torchSlot = i;
				break;
			}
		}
		if (torchSlot < 0 && !player.hasInfiniteMaterials()) {
			return;
		}
		BlockState torch = Blocks.TORCH.defaultBlockState();
		if (!torch.canSurvive(level, pos)) {
			torch = null;
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockState wall = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, direction);
				if (wall.canSurvive(level, pos)) {
					torch = wall;
					break;
				}
			}
		}
		if (torch == null) {
			return;
		}
		level.setBlock(pos, torch, Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
		if (torchSlot >= 0 && !player.hasInfiniteMaterials()) {
			player.getInventory().getItem(torchSlot).shrink(1);
		}
	}

	private static void breakAll(ServerPlayer player, List<BlockPos> positions) {
		net.minecraft.core.BlockPos from = positions.isEmpty() ? player.blockPosition() : positions.get(0);
		for (BlockPos target : positions) {
			if (player.getMainHandItem().isEmpty()) {
				return;
			}
			if (player.level() instanceof ServerLevel level) {
				thread(level, from, target);
			}
			player.gameMode.destroyBlock(target);
		}
	}

	/**
	 * Draws the line from the block you hit to a block that went with it.
	 *
	 * <p>Vein mining and area mining are the two upgrades that do the most and show the least: you
	 * swing once and eleven blocks vanish somewhere behind the one you were looking at, with no way to
	 * tell whether that was the upgrade working or the wrong block breaking. The thread says which
	 * blocks went and that they went <b>because of this one</b>.
	 */
	private static void thread(ServerLevel level, BlockPos from, BlockPos to) {
		if (from.equals(to)) {
			return;
		}
		double dx = to.getX() - from.getX();
		double dy = to.getY() - from.getY();
		double dz = to.getZ() - from.getZ();
		int steps = Math.max(2, (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 2.0));
		for (int step = 1; step <= steps; step++) {
			double fraction = step / (double) steps;
			level.sendParticles(VEIN,
				from.getX() + 0.5 + dx * fraction, from.getY() + 0.5 + dy * fraction, from.getZ() + 0.5 + dz * fraction,
				1, 0.03, 0.03, 0.03, 0.0);
		}
	}

	/** Deepslate and regular variants of the same ore count as one vein. */
	static boolean sameOre(BlockState origin, BlockState other) {
		if (origin.is(other.getBlock())) {
			return true;
		}
		return other.is(ConventionalBlockTags.ORES) && baseName(origin.getBlock()).equals(baseName(other.getBlock()));
	}

	private static String baseName(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath().replace("deepslate_", "");
	}

	/** Breadth-first search over the 26 neighbours, not including the origin, which is already broken. */
	private static List<BlockPos> connected(ServerLevel level, BlockPos origin, Predicate<BlockState> matches, int limit) {
		List<BlockPos> result = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		seen.add(origin);
		queue.add(origin);
		while (!queue.isEmpty() && result.size() < limit) {
			BlockPos current = queue.poll();
			for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
				if (result.size() >= limit) {
					break;
				}
				BlockPos immutable = next.immutable();
				if (seen.add(immutable) && matches.test(level.getBlockState(immutable))) {
					result.add(immutable);
					queue.add(immutable);
				}
			}
		}
		return result;
	}

	/** The square around the broken block, facing the way the player looks. Skips blocks much harder than the one mined. */
	private static List<BlockPos> area(ServerLevel level, ServerPlayer player, BlockPos center, BlockState origin, ItemStack tool, int radius) {
		Vec3 look = player.getLookAngle();
		float originHardness = origin.getDestroySpeed(level, center);
		List<BlockPos> result = new ArrayList<>();
		for (int a = -radius; a <= radius; a++) {
			for (int b = -radius; b <= radius; b++) {
				if (a == 0 && b == 0) {
					continue;
				}
				BlockPos target;
				if (Math.abs(look.y) > 0.7) {
					target = center.offset(a, 0, b);
				} else if (Math.abs(look.x) > Math.abs(look.z)) {
					target = center.offset(0, a, b);
				} else {
					target = center.offset(a, b, 0);
				}
				BlockState state = level.getBlockState(target);
				float hardness = state.getDestroySpeed(level, target);
				if (!state.isAir() && hardness >= 0.0F && hardness <= originHardness + 2.0F && tool.getDestroySpeed(state) > 1.0F) {
					result.add(target);
				}
			}
		}
		return result;
	}
}
