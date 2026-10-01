package dev.forja.test;

import dev.forja.block.HeatPipeBlock;
import dev.forja.block.MeltPipeBlock;
import dev.forja.block.entity.BoilerBlockEntity;
import dev.forja.block.entity.CastingTableBlockEntity;
import dev.forja.block.entity.CrucibleBlockEntity;
import dev.forja.block.entity.MeltTankBlockEntity;
import dev.forja.block.entity.StrainerBlockEntity;
import dev.forja.forge.Alloys;
import dev.forja.forge.ForgeType;
import dev.forja.forge.HeatFluid;
import dev.forja.forge.HeatSources;
import dev.forja.forge.Temple;
import dev.forja.item.CastingFrameItem;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The heat line (docs/FUNDICION_V2.md, part B; Andy, 2026-09-28: "formas para calentar la forja, como tubos de
 * calor que transportan diferentes tipos de fluidos"): each fluid made out of its input, heat carried down a
 * pipe run to a crucible, a casting table and a forge table, what makes each fluid different (steam will not
 * melt iron, blaze blood melts half again as fast, forge breath reaches white heat in a clay pot, ice brine
 * quenches a cast tool and cools the table and puts a crucible out), that the heat pipes never join the metal
 * ones, and what a save keeps.
 */
public class CalorGameTests {
	/** A boiler (or depot) with this under it, and nothing in it yet. */
	private static BoilerBlockEntity vessel(GameTestHelper helper, BlockPos at, Block kind, BlockState under) {
		helper.setBlock(at.below(), under);
		helper.setBlock(at, kind.defaultBlockState());
		return helper.getBlockEntity(at, BoilerBlockEntity.class);
	}

	private static void runVessel(GameTestHelper helper, BlockPos at, BoilerBlockEntity vessel, int ticks) {
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		for (int tick = 0; tick < ticks; tick++) {
			BoilerBlockEntity.serverTick(level, where, level.getBlockState(where), vessel);
		}
	}

	private static void runPot(GameTestHelper helper, BlockPos at, CrucibleBlockEntity pot, int ticks) {
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		for (int tick = 0; tick < ticks; tick++) {
			CrucibleBlockEntity.serverTick(level, where, level.getBlockState(where), pot);
		}
	}

	private static void runTable(GameTestHelper helper, BlockPos at, CastingTableBlockEntity table, int ticks) {
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		for (int tick = 0; tick < ticks; tick++) {
			CastingTableBlockEntity.serverTick(level, where, level.getBlockState(where), table);
		}
	}

	private static CrucibleBlockEntity pot(GameTestHelper helper, BlockPos at, Block tier) {
		helper.setBlock(at, tier.defaultBlockState());
		return helper.getBlockEntity(at, CrucibleBlockEntity.class);
	}

	private static MeltTankBlockEntity tank(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.CUBA_DE_COLADA.defaultBlockState());
		return helper.getBlockEntity(at, MeltTankBlockEntity.class);
	}

	private static void pipe(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.TUBO_DE_CALOR.defaultBlockState());
	}

	/** One boil of this item in this vessel over this fire: what it makes, and how much. */
	private static void boils(GameTestHelper helper, BlockPos at, Block kind, BlockState under, Item input, HeatFluid expected) {
		BoilerBlockEntity vessel = vessel(helper, at, kind, under);
		vessel.setItem(BoilerBlockEntity.SLOT_INPUT, new ItemStack(input));
		HeatFluid.Yield yield = HeatFluid.yield(new ItemStack(input), vessel.vessel());
		helper.assertTrue(yield != null && yield.fluid() == expected, input + " debería dar " + expected + " en " + kind);
		runVessel(helper, at, vessel, BoilerBlockEntity.BOIL_TICKS + 1);
		helper.assertTrue(vessel.fluid() == expected && vessel.amount() == yield.amount(),
			input + " da " + yield.amount() + " mB de " + expected + ", hay " + vessel.amount() + " de " + vessel.fluid());
		helper.assertTrue(vessel.getItem(BoilerBlockEntity.SLOT_INPUT).isEmpty(), "y se gasta " + input);
	}

	/** Each of the five fluids comes out of what makes it, in the vessel that makes it, over the fire it needs. */
	@GameTest
	public void eachFluidComesFromItsInput(GameTestHelper helper) {
		BlockState campfire = Blocks.CAMPFIRE.defaultBlockState();
		BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
		BlockState stone = Blocks.STONE.defaultBlockState();
		boils(helper, new BlockPos(0, 2, 0), ModBlocks.CALDERA, campfire, Items.WATER_BUCKET, HeatFluid.VAPOR);
		helper.assertTrue(helper.getBlockEntity(new BlockPos(0, 2, 0), BoilerBlockEntity.class)
			.getItem(BoilerBlockEntity.SLOT_OUTPUT).is(Items.BUCKET), "el cubo vacío sale por abajo");
		boils(helper, new BlockPos(2, 2, 0), ModBlocks.DEPOSITO_DE_CALOR, stone, Items.LAVA_BUCKET, HeatFluid.LAVA);
		boils(helper, new BlockPos(4, 2, 0), ModBlocks.CALDERA, stone, Items.BLAZE_ROD, HeatFluid.SANGRE_DE_BLAZE);
		boils(helper, new BlockPos(0, 2, 2), ModBlocks.CALDERA, magma, ModItems.ESCORIA, HeatFluid.ALIENTO_DE_FORJA);
		boils(helper, new BlockPos(2, 2, 2), ModBlocks.CALDERA, stone, Items.PACKED_ICE, HeatFluid.SALMUERA_HELADA);

		// The fire matters: water over stone does not boil, and slag over a campfire is not hot enough.
		BoilerBlockEntity cold = vessel(helper, new BlockPos(4, 2, 2), ModBlocks.CALDERA, stone);
		cold.setItem(BoilerBlockEntity.SLOT_INPUT, new ItemStack(Items.WATER_BUCKET));
		runVessel(helper, new BlockPos(4, 2, 2), cold, BoilerBlockEntity.BOIL_TICKS * 2);
		helper.assertTrue(cold.amount() == 0 && cold.getItem(BoilerBlockEntity.SLOT_INPUT).is(Items.WATER_BUCKET),
			"sin fuego debajo el agua no hierve");
		BoilerBlockEntity warm = vessel(helper, new BlockPos(6, 2, 2), ModBlocks.CALDERA, campfire);
		warm.setItem(BoilerBlockEntity.SLOT_INPUT, new ItemStack(ModItems.ESCORIA));
		runVessel(helper, new BlockPos(6, 2, 2), warm, BoilerBlockEntity.BOIL_TICKS * 2);
		helper.assertTrue(warm.amount() == 0, "la escoria pide un fuego fuerte, una fogata no basta");
		// One fluid at a time: ice in a boiler of blaze blood waits.
		BoilerBlockEntity blaze = helper.getBlockEntity(new BlockPos(4, 2, 0), BoilerBlockEntity.class);
		blaze.setItem(BoilerBlockEntity.SLOT_INPUT, new ItemStack(Items.PACKED_ICE));
		runVessel(helper, new BlockPos(4, 2, 0), blaze, BoilerBlockEntity.BOIL_TICKS * 2);
		helper.assertTrue(blaze.fluid() == HeatFluid.SANGRE_DE_BLAZE && blaze.getItem(BoilerBlockEntity.SLOT_INPUT).is(Items.PACKED_ICE),
			"el hielo espera a que la caldera de sangre se vacíe");
		// And a vessel only takes what it can use.
		helper.assertFalse(blaze.canPlaceItem(BoilerBlockEntity.SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET)), "la caldera no toma lava");
		helper.assertFalse(helper.getBlockEntity(new BlockPos(2, 2, 0), BoilerBlockEntity.class)
			.canPlaceItem(BoilerBlockEntity.SLOT_INPUT, new ItemStack(Items.BLAZE_ROD)), "el depósito sólo toma lava");
		helper.succeed();
	}

	/**
	 * A depot of lava, a run of pipe, and at the end of it a clay crucible with no ember, a casting table with
	 * no fire and a forge table on stone: all three get molten heat, the pipes show lava, and cutting the run
	 * takes it away again.
	 */
	@GameTest
	public void heatReachesThroughAPipeRun(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos depotAt = new BlockPos(0, 1, 1);
		BoilerBlockEntity depot = vessel(helper, depotAt, ModBlocks.DEPOSITO_DE_CALOR, Blocks.STONE.defaultBlockState());
		depot.fill(HeatFluid.LAVA, 4 * HeatFluid.BUCKET);
		for (int x = 1; x <= 5; x++) {
			pipe(helper, new BlockPos(x, 1, 1));
		}
		// The crucible at the end, the casting table off one side of the run, the forge table off the other.
		BlockPos potAt = new BlockPos(6, 1, 1);
		CrucibleBlockEntity pot = pot(helper, potAt, ModBlocks.CRISOL_DE_BARRO);
		MeltTankBlockEntity bank = tank(helper, potAt.north());
		BlockPos tableAt = new BlockPos(3, 1, 2);
		helper.setBlock(tableAt, ModBlocks.MESA_DE_LOSA.defaultBlockState());
		CastingTableBlockEntity table = helper.getBlockEntity(tableAt, CastingTableBlockEntity.class);
		BlockPos forgeAt = new BlockPos(4, 1, 0);
		helper.setBlock(forgeAt, ModBlocks.MESA_DE_FORJA.defaultBlockState());

		helper.assertTrue(Alloys.heatAt(level, helper.absolutePos(forgeAt)) == Alloys.Heat.FUNDIDA,
			"la mesa de forja al lado del tubo está fundida: " + Alloys.heatAt(level, helper.absolutePos(forgeAt)));
		helper.assertTrue(Alloys.heatUnder(level, helper.absolutePos(forgeAt)) == Alloys.Heat.FRIA, "y debajo sólo tiene piedra");

		// The pipes take the colour of what they carry once the depot has had a tick.
		runVessel(helper, depotAt, depot, 1);
		helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(3, 1, 1))).getValue(HeatPipeBlock.FLUIDO) == HeatFluid.Shown.LAVA,
			"el tubo se ve de lava");

		// Diamond (FUNDIDA) melts in a CLAY crucible with no ember, on lava down a pipe.
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.DIAMOND, 2));
		int before = depot.amount();
		runPot(helper, potAt, pot, dev.forja.block.CrucibleBlock.Tier.BARRO.cook + 5);
		helper.assertTrue(bank.bankMetal() == Items.DIAMOND && bank.bankAmount() == 2,
			"el crisol de barro funde diamante con lava por tubo: " + bank.bankAmount() + " de " + bank.bankMetal());
		helper.assertTrue(pot.heat() == Alloys.Heat.FUNDIDA, "y dice que está fundido: " + pot.heat());
		helper.assertTrue(depot.amount() < before && before - depot.amount() <= dev.forja.block.CrucibleBlock.Tier.BARRO.cook + 5,
			"gastó lava del depósito, 1 mB por tick: " + (before - depot.amount()));
		helper.assertTrue(pot.getItem(CrucibleBlockEntity.SLOT_FUEL).isEmpty(), "sin ascuas");

		// The casting table warms with no fire beside it.
		runTable(helper, tableAt, table, 100);
		helper.assertTrue(table.heat() >= 4 * HeatFluid.LAVA.tableWarms, "la mesa de colada se calienta por el tubo: " + table.heat());

		// Cut the run: the cache forgets, and the forge table is cold again.
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.AIR.defaultBlockState());
		helper.assertTrue(Alloys.heatAt(level, helper.absolutePos(forgeAt)) == Alloys.Heat.FRIA,
			"con el tubo cortado, la mesa de forja se queda fría: " + Alloys.heatAt(level, helper.absolutePos(forgeAt)));
		helper.succeed();
	}

	/** Steam into an iron crucible with no ember in it melts copper and leaves the iron alone. */
	@GameTest
	public void steamCannotMeltIron(GameTestHelper helper) {
		BlockPos potAt = new BlockPos(2, 1, 2);
		CrucibleBlockEntity pot = pot(helper, potAt, ModBlocks.CRISOL_DE_HIERRO);
		MeltTankBlockEntity bank = tank(helper, potAt.north());
		BoilerBlockEntity boiler = vessel(helper, potAt.west(), ModBlocks.CALDERA, Blocks.CAMPFIRE.defaultBlockState());
		boiler.fill(HeatFluid.VAPOR, 4 * HeatFluid.BUCKET);
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.RAW_IRON, 4));
		pot.setItem(CrucibleBlockEntity.SLOT_SECOND, new ItemStack(Items.RAW_COPPER, 4));
		runPot(helper, potAt, pot, dev.forja.block.CrucibleBlock.Tier.HIERRO.cook + 5);
		helper.assertTrue(pot.heat() == Alloys.Heat.TEMPLADA, "el vapor da calor templado: " + pot.heat());
		helper.assertTrue(bank.bankMetal() == Items.COPPER_INGOT && bank.bankAmount() == 4, "el cobre se funde: " + bank.bankAmount());
		helper.assertTrue(pot.getItem(CrucibleBlockEntity.SLOT_FIRST).getCount() == 4, "el hierro no: queda " + pot.getItem(0));
		runPot(helper, potAt, pot, dev.forja.block.CrucibleBlock.Tier.HIERRO.cook * 2);
		helper.assertTrue(pot.getItem(CrucibleBlockEntity.SLOT_FIRST).getCount() == 4, "ni dándole más tiempo");
		// An ember of its own and the iron pot burns hotter than steam: now the iron goes.
		MeltTankBlockEntity ironBank = tank(helper, potAt.south());
		pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 2));
		runPot(helper, potAt, pot, dev.forja.block.CrucibleBlock.Tier.HIERRO.cook + 5);
		helper.assertTrue(pot.getItem(CrucibleBlockEntity.SLOT_FIRST).isEmpty() && ironBank.bankMetal() == Items.IRON_INGOT,
			"con su propia ascua el crisol de hierro funde el hierro");
		helper.succeed();
	}

	/** Two iron crucibles, one on lava and one on blaze blood: the blaze one pours in two thirds of the time. */
	@GameTest
	public void blazeBloodMeltsHalfAgainAsFast(GameTestHelper helper) {
		BlockPos lavaPot = new BlockPos(1, 1, 1);
		BlockPos blazePot = new BlockPos(1, 1, 5);
		CrucibleBlockEntity slow = pot(helper, lavaPot, ModBlocks.CRISOL_DE_HIERRO);
		CrucibleBlockEntity fast = pot(helper, blazePot, ModBlocks.CRISOL_DE_HIERRO);
		MeltTankBlockEntity slowBank = tank(helper, lavaPot.east());
		MeltTankBlockEntity fastBank = tank(helper, blazePot.east());
		vessel(helper, lavaPot.west(), ModBlocks.DEPOSITO_DE_CALOR, Blocks.STONE.defaultBlockState()).fill(HeatFluid.LAVA, 4000);
		vessel(helper, blazePot.west(), ModBlocks.CALDERA, Blocks.STONE.defaultBlockState()).fill(HeatFluid.SANGRE_DE_BLAZE, 4000);
		slow.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.RAW_COPPER, 8));
		fast.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.RAW_COPPER, 8));
		int slowAt = -1;
		int fastAt = -1;
		for (int tick = 1; tick <= 400 && (slowAt < 0 || fastAt < 0); tick++) {
			runPot(helper, lavaPot, slow, 1);
			runPot(helper, blazePot, fast, 1);
			if (slowAt < 0 && slowBank.bankAmount() > 0) {
				slowAt = tick;
			}
			if (fastAt < 0 && fastBank.bankAmount() > 0) {
				fastAt = tick;
			}
		}
		helper.assertTrue(slowAt > 0 && fastAt > 0, "los dos funden: lava " + slowAt + ", sangre " + fastAt);
		float ratio = slowAt / (float) fastAt;
		helper.assertTrue(Math.abs(ratio - 1.5F) < 0.05F,
			"la sangre de blaze funde un 50 % más rápido: lava en " + slowAt + " ticks, sangre en " + fastAt + " (x" + ratio + ")");
		helper.succeed();
	}

	/** Forge breath takes a clay crucible to white heat: it pours sun steel, which only the obsidian one could. */
	@GameTest
	public void forgeBreathReachesWhiteHeat(GameTestHelper helper) {
		BlockPos potAt = new BlockPos(2, 1, 2);
		CrucibleBlockEntity pot = pot(helper, potAt, ModBlocks.CRISOL_DE_BARRO);
		BoilerBlockEntity boiler = vessel(helper, potAt.west(), ModBlocks.CALDERA, Blocks.MAGMA_BLOCK.defaultBlockState());
		boiler.fill(HeatFluid.ALIENTO_DE_FORJA, 4000);
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(ModItems.alloy("damasco"), 1));
		pot.setItem(CrucibleBlockEntity.SLOT_SECOND, new ItemStack(Items.BLAZE_ROD, 3));
		runPot(helper, potAt, pot, dev.forja.block.CrucibleBlock.Tier.BARRO.cook + 5);
		helper.assertTrue(pot.heat() == Alloys.Heat.FORJA_BLANCA, "el crisol de barro llega a forja blanca: " + pot.heat());
		ItemStack out = pot.getItem(CrucibleBlockEntity.SLOT_OUTPUT);
		helper.assertTrue(out.is(ModItems.alloy("solacero")), "y cuela acero solar: " + out);
		helper.assertTrue(boiler.amount() < 4000, "gastando aliento: " + boiler.amount());
		// A forge table against the same boiler is at white heat too.
		BlockPos forgeAt = potAt.west().west();
		helper.setBlock(forgeAt, ModBlocks.MESA_DE_FORJA.defaultBlockState());
		helper.assertTrue(Alloys.heatAt(helper.getLevel(), helper.absolutePos(forgeAt)) == Alloys.Heat.FORJA_BLANCA,
			"la mesa de forja también");
		helper.succeed();
	}

	/**
	 * Ice brine against a casting table: the tool it pours comes off quenched in water (for good), a table
	 * with no fire loses heat twice as fast, and a crucible it touches goes out.
	 */
	@GameTest(maxTicks = 200)
	public void brineQuenchesACastingAndCools(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos lanternAt = new BlockPos(2, 1, 2);
		BlockPos tableAt = lanternAt.above();
		helper.setBlock(lanternAt, ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(tableAt, ModBlocks.MESA_DE_LOSA.defaultBlockState());
		helper.setBlock(tableAt.above(), ModBlocks.COLADOR.defaultBlockState());
		helper.getBlockEntity(tableAt.above(), StrainerBlockEntity.class).setMaterial(dev.forja.material.ForgeMaterial.ACERO);
		tank(helper, tableAt.east()).fill(Items.IRON_INGOT, 120);
		BoilerBlockEntity brine = vessel(helper, tableAt.west(), ModBlocks.CALDERA, Blocks.STONE.defaultBlockState());
		brine.fill(HeatFluid.SALMUERA_HELADA, 1000);
		CastingTableBlockEntity table = helper.getBlockEntity(tableAt, CastingTableBlockEntity.class);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingFrameItem.of(ForgeType.PICO));
		runTable(helper, tableAt, table, 60 + CastingTableBlockEntity.COOK);
		ItemStack tool = table.result();
		helper.assertTrue(tool.has(ModComponents.PARTS), "sale un pico: " + tool);
		helper.assertTrue(Temple.of(tool) == Temple.AGUA, "templado en agua por la salmuera: " + tool.get(ModComponents.TEMPLE));
		helper.assertTrue(brine.amount() == 1000 - HeatFluid.QUENCH_COST, "gastó " + (1000 - brine.amount()) + " mB de salmuera");

		// Cooling: warm it full, take the lantern away, one second without brine, one second with.
		table.setItem(CastingTableBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, ItemStack.EMPTY);
		helper.setBlock(tableAt.west(), Blocks.AIR.defaultBlockState());
		runTable(helper, tableAt, table, 20 * 10);
		helper.assertTrue(table.heat() == CastingTableBlockEntity.HOT, "la mesa se llena de calor: " + table.heat());
		helper.setBlock(lanternAt, Blocks.STONE.defaultBlockState());
		int full = table.heat();
		runTable(helper, tableAt, table, CastingTableBlockEntity.EVERY + 1);
		int plain = full - table.heat();
		BoilerBlockEntity back = vessel(helper, tableAt.west(), ModBlocks.CALDERA, Blocks.STONE.defaultBlockState());
		back.fill(HeatFluid.SALMUERA_HELADA, 1000);
		int then = table.heat();
		runTable(helper, tableAt, table, CastingTableBlockEntity.EVERY + 1);
		int chilled = then - table.heat();
		helper.assertTrue(plain > 0 && chilled == 2 * plain, "sin fuego pierde " + plain + " por segundo; con salmuera, " + chilled);

		// And a crucible against brine will not burn: its ember stays, its ore stays.
		BlockPos potAt = new BlockPos(5, 1, 5);
		CrucibleBlockEntity pot = pot(helper, potAt, ModBlocks.CRISOL_DE_HIERRO);
		MeltTankBlockEntity bank = tank(helper, potAt.north());
		vessel(helper, potAt.west(), ModBlocks.CALDERA, Blocks.STONE.defaultBlockState()).fill(HeatFluid.SALMUERA_HELADA, 1000);
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.RAW_COPPER, 4));
		pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 2));
		runPot(helper, potAt, pot, dev.forja.block.CrucibleBlock.Tier.HIERRO.cook + 5);
		helper.assertTrue(bank.bankAmount() == 0 && !pot.isLit() && pot.getItem(CrucibleBlockEntity.SLOT_FUEL).getCount() == 2,
			"la salmuera apaga el crisol: " + bank.bankAmount() + " fundido, ascuas " + pot.getItem(CrucibleBlockEntity.SLOT_FUEL).getCount());
		helper.assertTrue(level.getBlockState(helper.absolutePos(potAt)).getBlock() instanceof dev.forja.block.CrucibleBlock, "sigue ahí");
		helper.succeed();
	}

	/** The two pipe systems never meet: no arm either way, and heat does not cross a metal pipe. */
	@GameTest
	public void heatPipesNeverJoinMetalPipes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// Depot, heat pipe, METAL pipe, heat pipe, forge table, in a row.
		BlockPos depotAt = new BlockPos(0, 1, 2);
		vessel(helper, depotAt, ModBlocks.DEPOSITO_DE_CALOR, Blocks.STONE.defaultBlockState()).fill(HeatFluid.LAVA, 4000);
		helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
		pipe(helper, new BlockPos(1, 1, 2));
		pipe(helper, new BlockPos(3, 1, 2));
		BlockPos forgeAt = new BlockPos(4, 1, 2);
		helper.setBlock(forgeAt, ModBlocks.MESA_DE_FORJA.defaultBlockState());
		BlockPos heatPipe = helper.absolutePos(new BlockPos(1, 1, 2));
		BlockPos metalPipe = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.assertFalse(HeatPipeBlock.joins(level, metalPipe), "un tubo de calor no alcanza un conducto de metal");
		helper.assertFalse(MeltPipeBlock.joins(level, heatPipe), "ni un conducto de metal un tubo de calor");
		// The shapes as the blocks work them out from their neighbours, which is what is drawn.
		BlockState heatShape = Block.updateFromNeighbourShapes(level.getBlockState(heatPipe), level, heatPipe);
		BlockState metalShape = Block.updateFromNeighbourShapes(level.getBlockState(metalPipe), level, metalPipe);
		helper.assertFalse(heatShape.getValue(HeatPipeBlock.EAST), "no se dibuja el brazo del tubo hacia el conducto");
		helper.assertFalse(metalShape.getValue(MeltPipeBlock.WEST) || metalShape.getValue(MeltPipeBlock.EAST),
			"ni los del conducto hacia los tubos");
		helper.assertTrue(heatShape.getValue(HeatPipeBlock.WEST), "pero el tubo sí alcanza el depósito");
		BlockPos lastPipe = helper.absolutePos(new BlockPos(3, 1, 2));
		helper.assertTrue(Block.updateFromNeighbourShapes(level.getBlockState(lastPipe), level, lastPipe).getValue(HeatPipeBlock.EAST),
			"y la mesa de forja");
		helper.assertTrue(Alloys.heatAt(level, helper.absolutePos(forgeAt)) == Alloys.Heat.FRIA,
			"el calor no pasa por el conducto de metal");
		helper.assertTrue(HeatSources.network(level, heatPipe).pipes().size() == 1, "la red del tubo es sólo él");
		// Swap the metal pipe for a heat pipe, and it does.
		pipe(helper, new BlockPos(2, 1, 2));
		helper.assertTrue(Alloys.heatAt(level, helper.absolutePos(forgeAt)) == Alloys.Heat.FUNDIDA, "con tubo de calor, sí");
		helper.succeed();
	}

	/**
	 * The crucible and the casting table read heat through Alloys.heatAt now, like the forge table: a clay pot
	 * burning an ember over a cauldron of lava burns molten (and melts diamond), but the lava is not a fire by
	 * itself — with no ember the pot stays cold; and a casting table over a campfire warms.
	 */
	@GameTest
	public void whatThePotStandsOnCounts(GameTestHelper helper) {
		BlockPos potAt = new BlockPos(2, 2, 2);
		helper.setBlock(potAt.below(), Blocks.LAVA_CAULDRON.defaultBlockState());
		CrucibleBlockEntity pot = pot(helper, potAt, ModBlocks.CRISOL_DE_BARRO);
		MeltTankBlockEntity bank = tank(helper, potAt.east());
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.DIAMOND, 2));
		runPot(helper, potAt, pot, dev.forja.block.CrucibleBlock.Tier.BARRO.cook + 5);
		helper.assertTrue(bank.bankAmount() == 0 && !pot.isLit(), "sin ascua, la lava debajo no enciende el crisol");
		pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 1));
		runPot(helper, potAt, pot, dev.forja.block.CrucibleBlock.Tier.BARRO.cook + 5);
		helper.assertTrue(pot.heat() == Alloys.Heat.FUNDIDA, "con su ascua arde fundido sobre la lava: " + pot.heat());
		helper.assertTrue(bank.bankMetal() == Items.DIAMOND && bank.bankAmount() == 2, "y funde el diamante: " + bank.bankAmount());

		BlockPos tableAt = new BlockPos(5, 2, 5);
		helper.setBlock(tableAt.below(), Blocks.CAMPFIRE.defaultBlockState());
		helper.setBlock(tableAt, ModBlocks.MESA_DE_LOSA.defaultBlockState());
		CastingTableBlockEntity table = helper.getBlockEntity(tableAt, CastingTableBlockEntity.class);
		runTable(helper, tableAt, table, 3 * (CastingTableBlockEntity.EVERY + 1));
		helper.assertTrue(table.heat() >= 2 * CastingTableBlockEntity.WARMS, "una fogata debajo calienta la mesa de colada: " + table.heat());
		helper.succeed();
	}

	/** A boiler keeps its fluid, its amount and what is in its slots through a save. */
	@GameTest
	public void heatLineSurvivesASave(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		BlockPos at = new BlockPos(2, 2, 2);
		BoilerBlockEntity boiler = vessel(helper, at, ModBlocks.CALDERA, Blocks.CAMPFIRE.defaultBlockState());
		boiler.fill(HeatFluid.VAPOR, 2500);
		boiler.setItem(BoilerBlockEntity.SLOT_INPUT, new ItemStack(Items.WATER_BUCKET));
		boiler.setItem(BoilerBlockEntity.SLOT_OUTPUT, new ItemStack(Items.BUCKET));
		var copy = (BoilerBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(boiler.getBlockPos(),
			boiler.getBlockState(), boiler.saveWithFullMetadata(registries), registries);
		helper.assertTrue(copy != null && copy.fluid() == HeatFluid.VAPOR && copy.amount() == 2500
			&& copy.getItem(BoilerBlockEntity.SLOT_INPUT).is(Items.WATER_BUCKET) && copy.getItem(BoilerBlockEntity.SLOT_OUTPUT).is(Items.BUCKET),
			"la caldera guarda su vapor, su agua y su cubo");
		// An empty one saves as empty, and a crucible saved before the heat line existed still loads.
		BoilerBlockEntity empty = vessel(helper, new BlockPos(4, 2, 2), ModBlocks.DEPOSITO_DE_CALOR, Blocks.STONE.defaultBlockState());
		var emptyCopy = (BoilerBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(empty.getBlockPos(),
			empty.getBlockState(), empty.saveWithFullMetadata(registries), registries);
		helper.assertTrue(emptyCopy != null && emptyCopy.fluid() == null && emptyCopy.amount() == 0, "el depósito vacío vuelve vacío");
		helper.succeed();
	}
}
