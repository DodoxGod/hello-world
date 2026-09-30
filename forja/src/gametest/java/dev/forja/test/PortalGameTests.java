package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import dev.forja.Forja;
import dev.forja.block.DeadForgeBlock;
import dev.forja.block.StarBracketBlock;
import dev.forja.block.StarPortalBlock;
import dev.forja.block.entity.CastingTableBlockEntity;
import dev.forja.block.entity.MeltTankBlockEntity;
import dev.forja.entity.FallenSmith;
import dev.forja.forge.Alloys;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModItems;
import dev.forja.world.StarYard;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The way to the Cementerio entre Estrellas, delivery 2 (docs/HERRERO_DIMENSION.md, section 1): the
 * oricalco alloy of every renewable metal of the mod, the oricalco pearl cast over an ender pearl, the
 * star portal's frame that four pearls light and three do not, the old dead forge that no longer
 * summons anyone and opens into a frame, the castle's template with its frame, and the way back.
 */
public class PortalGameTests {
	/** The fourteen metals, one bar each, in the order the recipe lists them. */
	private static List<ItemStack> fourteen() {
		List<ItemStack> metals = new ArrayList<>();
		metals.add(new ItemStack(ModItems.HIERRO_ESTELAR));
		metals.add(new ItemStack(ModItems.PLACA_HUECA));
		metals.add(new ItemStack(ModItems.ESCORIA));
		for (String alloy : new String[] {"bronce", "laton", "peltre", "electro", "acero", "cinerio", "voltaico",
			"acero_estelar", "obsidiacero", "almacero", "vidriacero"}) {
			metals.add(new ItemStack(ModItems.alloy(alloy)));
		}
		return metals;
	}

	/** Every renewable metal of the mod makes oricalco at molten heat; one missing, or too cold, does not. */
	@GameTest(maxTicks = 20)
	public void oricalcoIsTheAlloyOfEveryRenewableMetal(GameTestHelper helper) {
		Alloys.Recipe recipe = Alloys.match(fourteen(), Alloys.Heat.FUNDIDA);
		helper.assertTrue(recipe != null && recipe.id().equals("oricalco"), "catorce metales dan oricalco: " + recipe);
		helper.assertTrue(recipe.result().is(ModItems.ORICALCO) && recipe.result().getCount() == 4, "cuatro lingotes: " + recipe.result());
		Alloys.Recipe cold = Alloys.match(fourteen(), Alloys.Heat.CALIENTE);
		helper.assertTrue(cold == null || !cold.id().equals("oricalco"), "sin fundir no sale");
		List<ItemStack> short1 = fourteen();
		short1.remove(0);
		Alloys.Recipe missing = Alloys.match(short1, Alloys.Heat.FUNDIDA);
		helper.assertTrue(missing == null || !missing.id().equals("oricalco"), "sin el hierro estelar no sale");
		// Nothing in it that runs out or that only the smith gives.
		for (Alloys.Part part : recipe.inputs()) {
			for (String gone : new String[] {"damasco", "solacero", "lunacero", "acero_vivo"}) {
				helper.assertFalse(part.item().get() == ModItems.alloy(gone), "el oricalco no lleva " + gone);
			}
			helper.assertFalse(part.item().get() == ModItems.CORAZON_DE_FORJA, "ni el corazón de forja");
		}
		helper.assertTrue(MeltTankBlockEntity.holds(ModItems.ORICALCO), "y las cubas lo guardan");
		helper.succeed();
	}

	/** An ender pearl on a casting table, oricalco in the tank beside it: out comes an oricalco pearl, for two bars. */
	@GameTest(maxTicks = 20)
	public void aPearlIsCastInOricalco(GameTestHelper helper) {
		BlockPos lantern = new BlockPos(1, 1, 1);
		BlockPos table = lantern.above();
		helper.setBlock(lantern, ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(table, ModBlocks.MESA_DE_LOSA.defaultBlockState());
		helper.setBlock(table.east(), ModBlocks.CUBA_DE_COLADA.defaultBlockState());
		MeltTankBlockEntity tank = helper.getBlockEntity(table.east(), MeltTankBlockEntity.class);
		tank.fill(ModItems.ORICALCO, 10);
		CastingTableBlockEntity cast = helper.getBlockEntity(table, CastingTableBlockEntity.class);
		helper.assertTrue(cast.canPlaceItem(CastingTableBlockEntity.SLOT_FRAME, new ItemStack(Items.ENDER_PEARL)), "la perla de ender va en la mesa");
		cast.setItem(CastingTableBlockEntity.SLOT_FRAME, new ItemStack(Items.ENDER_PEARL));
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(table);
		for (int tick = 0; tick < 60 + CastingTableBlockEntity.COOK; tick++) {
			CastingTableBlockEntity.serverTick(level, where, level.getBlockState(where), cast);
		}
		helper.assertTrue(cast.result().is(ModItems.PERLA_DE_ORICALCO), "sale una perla de oricalco: " + cast.result());
		helper.assertTrue(cast.frame().isEmpty(), "y la perla de ender se fue en ella");
		helper.assertTrue(tank.bankAmount() == 10 - CastingTableBlockEntity.PEARL_COST, "por dos lingotes: quedan " + tank.bankAmount());
		helper.succeed();
	}

	/** Three pearls in the frame light nothing; the fourth lights the 3 by 3 hole, and it stays lit. */
	@GameTest(maxTicks = 20)
	public void fourPearlsLightTheFrameAndThreeDoNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos centre = helper.absolutePos(new BlockPos(3, 1, 3));
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.setBlockAndUpdate(centre.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
			}
		}
		StarBracketBlock.buildFrame(level, centre);
		Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		for (int i = 0; i < 3; i++) {
			StarBracketBlock.setPearl(level, centre.relative(sides[i], StarBracketBlock.REACH));
		}
		helper.assertTrue(StarBracketBlock.pearlsIn(level, centre.north(2)) == 3, "tres perlas puestas");
		helper.assertFalse(level.getBlockState(centre).is(ModBlocks.PORTAL_ESTELAR), "con tres no se enciende");
		StarBracketBlock.setPearl(level, centre.relative(sides[3], StarBracketBlock.REACH));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.assertTrue(level.getBlockState(centre.offset(dx, 0, dz)).is(ModBlocks.PORTAL_ESTELAR),
					"con cuatro se enciende el hueco entero: " + dx + ", " + dz);
			}
		}
		helper.assertTrue(level.getBlockState(centre.north(2)).getDestroySpeed(level, centre.north(2)) < 0.0F, "y las ménsulas no se rompen");
		helper.succeed();
	}

	/**
	 * The old dead forge wakes nobody any more, even with the whole old offering in the bag: it opens into
	 * an empty frame, and the offering stays where it was.
	 */
	@GameTest(maxTicks = 40)
	public void theOldForgeNoLongerSummons(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos forge = helper.absolutePos(new BlockPos(3, 2, 3));
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.setBlockAndUpdate(forge.offset(dx, -2, dz), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(forge.offset(dx, -1, dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
			}
		}
		level.setBlockAndUpdate(forge, ModBlocks.FRAGUA_APAGADA.defaultBlockState());
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		int slot = 0;
		for (DeadForgeBlock.Offering offering : DeadForgeBlock.OFFERING) {
			player.getInventory().setItem(slot++, new ItemStack(offering.item().get(), offering.count()));
		}
		int before = player.getInventory().countItem(ModItems.HIERRO_ESTELAR);
		level.getBlockState(forge).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(forge), Direction.UP, forge, false));
		int after = player.getInventory().countItem(ModItems.HIERRO_ESTELAR);
		helper.assertTrue(before == 8 && after == before, "la ofrenda sigue en la bolsa: " + before + " antes, " + after + " después");
		helper.runAfterDelay(20, () -> {
			// Near the forge, where the old summoning put him: forty-eight blocks reached the boss fights of other
			// tests running beside this one, and failed whenever the batches happened to lay them out that close.
			helper.assertTrue(level.getEntitiesOfClass(FallenSmith.class, new AABB(forge).inflate(8.0)).isEmpty(), "no aparece el Herrero Caído");
			helper.assertTrue(level.getBlockState(forge).isAir(), "la fragua se abre: " + level.getBlockState(forge));
			BlockPos centre = forge.below();
			for (Direction side : Direction.Plane.HORIZONTAL) {
				helper.assertTrue(level.getBlockState(centre.relative(side, 2)).is(ModBlocks.MENSULA_ESTELAR), "ménsula al " + side);
			}
			helper.succeed();
		});
	}

	/** The castle's deep forge now holds the empty frame: four brackets and no dead forge. */
	@GameTest(maxTicks = 20)
	public void theCastleHoldsTheFrame(GameTestHelper helper) {
		var template = helper.getLevel().getServer().getStructureManager().get(Forja.id("bastion/p_2_0_3")).orElseThrow();
		int brackets = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), ModBlocks.MENSULA_ESTELAR).size();
		int forges = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), ModBlocks.FRAGUA_APAGADA).size();
		helper.assertTrue(brackets == 4, "ménsulas en la Forja Profunda: " + brackets);
		helper.assertTrue(forges == 0, "y ninguna fragua apagada: " + forges);
		helper.succeed();
	}

	/** Coming in through a portal remembers a place to stand just outside its frame, and the way back goes there. */
	@GameTest(maxTicks = 20)
	public void theWayBackIsRemembered(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				level.setBlockAndUpdate(centre.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
			}
		}
		StarBracketBlock.buildFrame(level, centre);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			StarBracketBlock.setPearl(level, centre.relative(side, StarBracketBlock.REACH));
		}
		Vec3 from = Vec3.atBottomCenterOf(centre.south(4));
		Vec3 stand = StarPortalBlock.outside(level, centre.north(), from);
		helper.assertTrue(stand.distanceTo(Vec3.atBottomCenterOf(centre)) > 3.0, "fuera del marco: " + stand);
		helper.assertTrue(stand.z > centre.getZ(), "del lado por el que se entró: " + stand);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		StarYard.remember(player, stand, 0.0F);
		var back = StarYard.departure(player, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING);
		helper.assertTrue(back.position().distanceTo(stand) < 0.01 && back.newLevel() == level, "y la vuelta va allí: " + back.position());
		helper.succeed();
	}
}
