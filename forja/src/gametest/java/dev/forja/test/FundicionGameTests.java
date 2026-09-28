package dev.forja.test;

import java.util.List;

import dev.forja.block.entity.CastingBoxBlockEntity;
import dev.forja.block.entity.CastingTableBlockEntity;
import dev.forja.block.entity.CrucibleBlockEntity;
import dev.forja.block.entity.MeltTankBlockEntity;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.item.CastingFrameItem;
import dev.forja.item.CastingMouldItem;
import dev.forja.item.StrainerItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.menu.CastingBoxMenu;
import dev.forja.part.PartType;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The foundry (Andy, 2026-09-28: "se puede poner cualquier objeto en los contenedores", and the furnace,
 * the ores and all that "no funcione de forma correcta"). One test per thing that was wrong: what each
 * slot and each tank takes, the crucible melting ore and ingots at all, the crucible that stopped after
 * one pour into its own tank, the casting box that stopped after one casting, and what survives a save.
 */
public class FundicionGameTests {
	private static final BlockPos POT = new BlockPos(1, 1, 1);

	private static CrucibleBlockEntity pot(GameTestHelper helper, BlockPos at, Block tier) {
		helper.setBlock(at, tier.defaultBlockState());
		return helper.getBlockEntity(at, CrucibleBlockEntity.class);
	}

	private static MeltTankBlockEntity tank(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.CUBA_DE_COLADA.defaultBlockState());
		return helper.getBlockEntity(at, MeltTankBlockEntity.class);
	}

	/** Runs a crucible by hand, which is a minute of work in a single tick. */
	private static void run(GameTestHelper helper, BlockPos at, CrucibleBlockEntity pot, int ticks) {
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		for (int tick = 0; tick < ticks; tick++) {
			CrucibleBlockEntity.serverTick(level, where, level.getBlockState(where), pot);
		}
	}

	private static void runBox(GameTestHelper helper, BlockPos at, CastingBoxBlockEntity box, int ticks) {
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		for (int tick = 0; tick < ticks; tick++) {
			CastingBoxBlockEntity.serverTick(level, where, level.getBlockState(where), box);
		}
	}

	/** The menu slot a player's hotbar slot is shown in, on both of the foundry's screens. */
	private static int hotbar(int column) {
		return 4 + 27 + column;
	}

	/** What each crucible slot will take, from a hopper and from the screen: only what the pot can use. */
	@GameTest
	public void crucibleRefusesWhatItCannotUse(GameTestHelper helper) {
		CrucibleBlockEntity pot = pot(helper, POT, ModBlocks.CRISOL_DE_HIERRO);
		ItemStack forged = Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO);
		for (ItemStack junk : List.of(new ItemStack(Items.STICK), new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIRT),
			new ItemStack(Items.OAK_PLANKS))) {
			helper.assertFalse(pot.canPlaceItem(CrucibleBlockEntity.SLOT_FIRST, junk), "el crisol no debe aceptar " + junk);
			helper.assertFalse(pot.canPlaceItemThroughFace(CrucibleBlockEntity.SLOT_SECOND, junk, net.minecraft.core.Direction.UP),
				"ni por una tolva: " + junk);
		}
		for (ItemStack good : List.of(new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COAL),
			new ItemStack(Items.IRON_ORE), forged, new ItemStack(ModItems.alloy("bronce")))) {
			helper.assertTrue(pot.canPlaceItem(CrucibleBlockEntity.SLOT_FIRST, good), "el crisol debe aceptar " + good);
		}
		helper.assertFalse(pot.canPlaceItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL)), "sólo ascuas de combustible");
		helper.assertTrue(pot.canPlaceItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA)), "las ascuas sí");
		helper.assertFalse(pot.canPlaceItem(CrucibleBlockEntity.SLOT_OUTPUT, new ItemStack(Items.IRON_INGOT)), "nada entra en la salida");

		// And through the screen: a click, a shift-click, and the capacity.
		Player player = helper.makeMockServerPlayerInLevel();
		AbstractContainerMenu menu = pot.createMenu(1, player.getInventory(), player);
		player.getInventory().setItem(0, new ItemStack(Items.STICK, 5));
		menu.clicked(hotbar(0), 0, ContainerInput.QUICK_MOVE, player);
		helper.assertTrue(player.getInventory().getItem(0).getCount() == 5 && pot.getItem(0).isEmpty() && pot.getItem(1).isEmpty(),
			"un palo con mayúsculas no entra en el crisol");
		player.getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 64));
		menu.clicked(hotbar(1), 0, ContainerInput.QUICK_MOVE, player);
		int cap = pot.tier().capacity;
		helper.assertTrue(pot.getItem(0).getCount() + pot.getItem(1).getCount() == cap,
			"mayúsculas mete sólo lo que cabe (" + cap + "), metió " + pot.getItem(0).getCount() + "+" + pot.getItem(1).getCount());
		helper.assertTrue(player.getInventory().getItem(1).getCount() == 64 - cap, "y el resto se queda en la mano");
		// Full: a second shift-click moves nothing, and returns rather than asking for ever.
		player.getInventory().setItem(2, new ItemStack(Items.COAL, 10));
		menu.clicked(hotbar(2), 0, ContainerInput.QUICK_MOVE, player);
		helper.assertTrue(player.getInventory().getItem(2).getCount() == 10, "lleno, no entra más carbón");
		// A sword on the cursor does not go down on an empty input slot.
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.IRON_INGOT, 4));
		pot.setItem(CrucibleBlockEntity.SLOT_SECOND, ItemStack.EMPTY);
		menu.setCarried(new ItemStack(Items.DIAMOND_SWORD));
		menu.clicked(1, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(pot.getItem(1).isEmpty() && menu.getCarried().is(Items.DIAMOND_SWORD), "una espada no se deja en el crisol");
		// Gold on the cursor: only what is left of the capacity goes down.
		menu.setCarried(new ItemStack(Items.GOLD_INGOT, 64));
		menu.clicked(1, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(pot.getItem(1).getCount() == cap - 4, "con el cursor sólo cabe lo que queda: " + pot.getItem(1).getCount());
		helper.succeed();
	}

	/**
	 * A crucible pouring into its own tank keeps pouring. The bronze in the tank used to count as an
	 * ingredient no recipe used, so after the first three bars the pot stopped for good.
	 */
	@GameTest
	public void crucibleKeepsPouringIntoItsTank(GameTestHelper helper) {
		CrucibleBlockEntity pot = pot(helper, POT, ModBlocks.CRISOL_DE_HIERRO);
		MeltTankBlockEntity tank = tank(helper, POT.east());
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.COPPER_INGOT, 8));
		pot.setItem(CrucibleBlockEntity.SLOT_SECOND, new ItemStack(Items.IRON_INGOT, 4));
		pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 8));
		run(helper, POT, pot, dev.forja.block.CrucibleBlock.Tier.HIERRO.cook * 4 + 20);
		helper.assertTrue(tank.bankMetal() == ModItems.alloy("bronce") && tank.bankAmount() == 12,
			"cuatro coladas de bronce en la cuba, hay " + tank.bankAmount() + " de " + tank.bankMetal());
		helper.assertTrue(pot.getItem(0).isEmpty() && pot.getItem(1).isEmpty(), "y el crisol se vació");
		helper.succeed();
	}

	/** A second bank on the line (gold) no longer stops the pot alloying bronze into the empty one. */
	@GameTest
	public void crucibleAlloysBesideAnotherBank(GameTestHelper helper) {
		CrucibleBlockEntity pot = pot(helper, POT, ModBlocks.CRISOL_DE_HIERRO);
		MeltTankBlockEntity gold = tank(helper, POT.east());
		MeltTankBlockEntity empty = tank(helper, POT.west());
		gold.fill(Items.GOLD_INGOT, 50);
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.COPPER_INGOT, 2));
		pot.setItem(CrucibleBlockEntity.SLOT_SECOND, new ItemStack(Items.IRON_INGOT, 1));
		pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 2));
		run(helper, POT, pot, dev.forja.block.CrucibleBlock.Tier.HIERRO.cook + 10);
		helper.assertTrue(empty.bankMetal() == ModItems.alloy("bronce") && empty.bankAmount() == 3,
			"el bronce va a la cuba vacía: " + empty.bankAmount() + " de " + empty.bankMetal());
		helper.assertTrue(gold.bankAmount() == 50, "y el oro no se toca");
		helper.succeed();
	}

	/** "Mena al crisol, colada a las cubas": raw iron in a clay pot melts into the tank beside it. */
	@GameTest
	public void oreMeltsIntoTheTank(GameTestHelper helper) {
		CrucibleBlockEntity pot = pot(helper, POT, ModBlocks.CRISOL_DE_BARRO);
		MeltTankBlockEntity tank = tank(helper, POT.east());
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.RAW_IRON, 8));
		pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 1));
		run(helper, POT, pot, dev.forja.block.CrucibleBlock.Tier.BARRO.cook + 10);
		helper.assertTrue(tank.bankMetal() == Items.IRON_INGOT && tank.bankAmount() == 8,
			"ocho de mena de hierro son ocho de hierro fundido: " + tank.bankAmount() + " de " + tank.bankMetal());
		helper.assertTrue(pot.getItem(CrucibleBlockEntity.SLOT_FIRST).isEmpty(), "y la mena se gastó");
		helper.assertTrue(pot.getItem(CrucibleBlockEntity.SLOT_FUEL).isEmpty(), "fundir mena no regala ascuas");
		helper.succeed();
	}

	/** With no tank, ore comes out the bottom as bars; a bar that is already a bar waits for a tank. */
	@GameTest
	public void ingotsWaitForATankAndOreDoesNot(GameTestHelper helper) {
		CrucibleBlockEntity pot = pot(helper, POT, ModBlocks.CRISOL_DE_BARRO);
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.RAW_IRON_BLOCK, 1));
		pot.setItem(CrucibleBlockEntity.SLOT_SECOND, new ItemStack(Items.GOLD_INGOT, 5));
		pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 4));
		run(helper, POT, pot, dev.forja.block.CrucibleBlock.Tier.BARRO.cook * 3);
		ItemStack out = pot.getItem(CrucibleBlockEntity.SLOT_OUTPUT);
		helper.assertTrue(out.is(Items.IRON_INGOT) && out.getCount() == 9, "un bloque de mena son nueve lingotes: " + out);
		helper.assertTrue(pot.getItem(CrucibleBlockEntity.SLOT_SECOND).getCount() == 5, "el oro sin cuba se queda donde está");
		MeltTankBlockEntity tank = tank(helper, POT.north());
		run(helper, POT, pot, dev.forja.block.CrucibleBlock.Tier.BARRO.cook + 10);
		helper.assertTrue(tank.bankMetal() == Items.GOLD_INGOT && tank.bankAmount() == 5, "con cuba, el oro se funde en ella: " + tank.bankAmount());
		helper.succeed();
	}

	/** The clay pot does not melt diamond; the obsidian one does. */
	@GameTest
	public void hardMetalWantsAHotterPot(GameTestHelper helper) {
		CrucibleBlockEntity clay = pot(helper, POT, ModBlocks.CRISOL_DE_BARRO);
		MeltTankBlockEntity tank = tank(helper, POT.east());
		clay.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.DIAMOND, 4));
		clay.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 2));
		run(helper, POT, clay, dev.forja.block.CrucibleBlock.Tier.BARRO.cook + 10);
		helper.assertTrue(clay.getItem(CrucibleBlockEntity.SLOT_FIRST).getCount() == 4 && tank.bankAmount() == 0,
			"el crisol de barro no toca el diamante");
		helper.assertTrue(clay.getItem(CrucibleBlockEntity.SLOT_FUEL).getCount() == 2, "ni quema ascuas por intentarlo");
		CrucibleBlockEntity obsidian = pot(helper, POT, ModBlocks.CRISOL_DE_OBSIDIANA);
		obsidian.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.DIAMOND, 4));
		obsidian.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 2));
		run(helper, POT, obsidian, dev.forja.block.CrucibleBlock.Tier.OBSIDIANA.cook + 10);
		helper.assertTrue(tank.bankMetal() == Items.DIAMOND && tank.bankAmount() == 4, "el de obsidiana sí: " + tank.bankAmount());
		helper.succeed();
	}

	/** A tank takes metal and nothing else, and a tank over a pot does not stuff the pot's slots. */
	@GameTest
	public void tankTakesOnlyMetal(GameTestHelper helper) {
		MeltTankBlockEntity tank = tank(helper, POT.above());
		Player player = helper.makeMockServerPlayerInLevel();
		ItemStack stick = new ItemStack(Items.STICK, 10);
		helper.assertFalse(tank.hand(player, stick), "un palo no es metal");
		ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.HIERRO));
		helper.assertFalse(tank.hand(player, sword), "una espada forjada no se funde en la cuba a mano");
		ItemStack named = new ItemStack(Items.IRON_INGOT, 3);
		named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Mío"));
		helper.assertFalse(tank.hand(player, named), "ni un lingote con nombre, que saldría sin él");
		helper.assertTrue(stick.getCount() == 10 && !sword.isEmpty() && named.getCount() == 3 && tank.bankAmount() == 0,
			"y nada se pierde");
		ItemStack iron = new ItemStack(Items.IRON_INGOT, 10);
		helper.assertTrue(tank.hand(player, iron) && iron.isEmpty() && tank.bankAmount() == 10, "el hierro sí");

		// Over a crucible: the tank keeps its metal, the pot keeps its slots empty.
		CrucibleBlockEntity pot = pot(helper, POT, ModBlocks.CRISOL_DE_BARRO);
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(POT.above());
		for (int tick = 0; tick < MeltTankBlockEntity.PUSH_EVERY * 3; tick++) {
			MeltTankBlockEntity.serverTick(level, where, level.getBlockState(where), tank);
		}
		helper.assertTrue(pot.isEmpty() && tank.bankAmount() == 10, "la cuba no mete su metal en el crisol de debajo");
		helper.succeed();
	}

	/**
	 * The casting box keeps casting. It checked the output against a plain part, and nothing it makes
	 * is plain, so it stopped after one; and a clean part's upgrade was drawn again every tick.
	 */
	@GameTest
	public void castingBoxKeepsCasting(GameTestHelper helper) {
		helper.setBlock(POT, ModBlocks.CAJA_DE_MOLDEO.defaultBlockState());
		CastingBoxBlockEntity box = helper.getBlockEntity(POT, CastingBoxBlockEntity.class);
		MeltTankBlockEntity tank = tank(helper, POT.east());
		tank.fill(Items.IRON_INGOT, 60);
		box.setItem(CastingBoxBlockEntity.SLOT_PATTERN, CastingMouldItem.of(PartType.CABEZA_PICO));
		int cook = dev.forja.block.CastingBoxBlock.Tier.BARRO.cook;
		runBox(helper, POT, box, cook * 3 + 3);
		ItemStack rough = box.getItem(CastingBoxBlockEntity.SLOT_OUTPUT);
		helper.assertTrue(rough.getCount() == 3 && rough.getOrDefault(ModComponents.ROUGH, false),
			"tres coladas bastas seguidas sin colador, hay " + rough);

		// Clean, through a strainer that holds: one part, and it waits with its upgrade for the output.
		helper.setBlock(POT, ModBlocks.CAJA_DE_MOLDEO_DE_DAMASCO.defaultBlockState());
		CastingBoxBlockEntity good = helper.getBlockEntity(POT, CastingBoxBlockEntity.class);
		good.setItem(CastingBoxBlockEntity.SLOT_PATTERN, CastingMouldItem.of(PartType.CABEZA_PICO));
		good.setItem(CastingBoxBlockEntity.SLOT_STRAINER, StrainerItem.of(ForgeMaterial.NETHERITA));
		int fast = dev.forja.block.CastingBoxBlock.Tier.DAMASCO.cook;
		runBox(helper, POT, good, fast + 1);
		ItemStack clean = good.getItem(CastingBoxBlockEntity.SLOT_OUTPUT).copy();
		helper.assertTrue(clean.getCount() == 1 && clean.getOrDefault(ModComponents.COLADA, false) && clean.has(ModComponents.UPGRADES),
			"una colada limpia lleva su mejora: " + clean);
		runBox(helper, POT, good, fast * 2);
		helper.assertTrue(ItemStack.isSameItemSameComponents(good.getItem(CastingBoxBlockEntity.SLOT_OUTPUT), clean)
			&& good.getItem(CastingBoxBlockEntity.SLOT_OUTPUT).getCount() == 1, "espera a que la saquen, sin cambiar de mejora");
		good.setItem(CastingBoxBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
		runBox(helper, POT, good, fast + 1);
		helper.assertTrue(!good.getItem(CastingBoxBlockEntity.SLOT_OUTPUT).isEmpty(), "y al sacarla cuela la siguiente");
		helper.succeed();
	}

	/** The box's screen takes a finished tool (to cut its frame) and strainers, and nothing else odd. */
	@GameTest
	public void castingBoxScreenTakesToolsAndStrainers(GameTestHelper helper) throws ReflectiveOperationException {
		ItemStack tool = Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO));
		helper.assertTrue(CastingBoxBlockEntity.allowed(CastingBoxBlockEntity.SLOT_PATTERN, tool), "una herramienta acabada, para su marco");
		helper.assertTrue(CastingBoxBlockEntity.allowed(CastingBoxBlockEntity.SLOT_PATTERN, StrainerItem.of(null)), "un colador, para bañarlo");
		helper.assertFalse(CastingBoxBlockEntity.allowed(CastingBoxBlockEntity.SLOT_PATTERN, new ItemStack(Items.STICK)), "un palo no");
		helper.assertFalse(CastingBoxBlockEntity.allowed(CastingBoxBlockEntity.SLOT_PATTERN, CastingFrameItem.of(ForgeType.PICO)), "un marco no");
		helper.assertFalse(CastingBoxBlockEntity.allowed(CastingBoxBlockEntity.SLOT_STEEL, new ItemStack(Items.IRON_INGOT)), "hierro no es acero refractario");

		helper.setBlock(POT, ModBlocks.CAJA_DE_MOLDEO_DE_DAMASCO.defaultBlockState());
		CastingBoxBlockEntity box = helper.getBlockEntity(POT, CastingBoxBlockEntity.class);
		Player player = helper.makeMockServerPlayerInLevel();
		AbstractContainerMenu menu = box.createMenu(1, player.getInventory(), player);
		helper.assertTrue(menu.getSlot(CastingBoxBlockEntity.SLOT_PATTERN).mayPlace(tool), "la pantalla acepta la herramienta");
		player.getInventory().setItem(0, StrainerItem.of(null));
		menu.clicked(hotbar(0), 0, ContainerInput.QUICK_MOVE, player);
		helper.assertTrue(box.getItem(CastingBoxBlockEntity.SLOT_STRAINER).getItem() instanceof StrainerItem,
			"con mayúsculas el colador va a su hueco");
		player.getInventory().setItem(1, StrainerItem.of(null));
		menu.clicked(hotbar(1), 0, ContainerInput.QUICK_MOVE, player);
		helper.assertTrue(box.getItem(CastingBoxBlockEntity.SLOT_PATTERN).getItem() instanceof StrainerItem,
			"y el segundo, al de arriba para bañarlo");
		player.getInventory().setItem(2, tool.copy());
		box.setItem(CastingBoxBlockEntity.SLOT_PATTERN, ItemStack.EMPTY);
		menu.clicked(hotbar(2), 0, ContainerInput.QUICK_MOVE, player);
		helper.assertTrue(box.getItem(CastingBoxBlockEntity.SLOT_PATTERN).has(ModComponents.PARTS), "la herramienta con mayúsculas");
		player.getInventory().setItem(3, new ItemStack(Items.STICK, 3));
		menu.clicked(hotbar(3), 0, ContainerInput.QUICK_MOVE, player);
		helper.assertTrue(player.getInventory().getItem(3).getCount() == 3, "un palo no entra");

		// The numbers a container sends go as shorts. "Holds anything" has to survive the trip.
		java.lang.reflect.Field field = AbstractContainerMenu.class.getDeclaredField("dataSlots");
		field.setAccessible(true);
		List<?> slots = (List<?>) field.get(menu);
		CastingBoxMenu client = new CastingBoxMenu(2, player.getInventory());
		for (int i = 0; i < slots.size(); i++) {
			client.setData(i, (short) ((net.minecraft.world.inventory.DataSlot) slots.get(i)).get());
		}
		helper.assertTrue(client.holds() == Integer.MAX_VALUE, "la caja de damasco dice que aguanta todo, no " + client.holds());
		helper.succeed();
	}

	/** Breaking a casting table mid-pour spills the metal instead of losing it. */
	@GameTest
	public void castingTableSpillsItsMetal(GameTestHelper helper) {
		BlockPos at = POT.above();
		helper.setBlock(POT, ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(at, ModBlocks.MESA_DE_LOSA.defaultBlockState());
		CastingTableBlockEntity table = helper.getBlockEntity(at, CastingTableBlockEntity.class);
		MeltTankBlockEntity tank = tank(helper, at.east());
		tank.fill(Items.IRON_INGOT, 60);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingFrameItem.of(ForgeType.PICO));
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		// Two seconds of lantern: a whole pour's worth of heat before it starts.
		for (int tick = 0; tick < 60; tick++) {
			CastingTableBlockEntity.serverTick(level, where, level.getBlockState(where), table);
		}
		int cost = CastingFrameItem.cost(ForgeType.PICO);
		helper.assertTrue(table.metal() == Items.IRON_INGOT && table.amount() == cost, "la mesa empezó a colar " + table.amount());
		level.destroyBlock(where, true);
		int iron = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(where).inflate(2.0)).stream()
			.filter(item -> item.getItem().is(Items.IRON_INGOT)).mapToInt(item -> item.getItem().getCount()).sum();
		helper.assertTrue(iron == cost, "al romperla suelta el hierro que tenía dentro: " + iron + " de " + cost);
		helper.succeed();
	}

	/**
	 * A table set down on a wisp lantern — the guide's cure for rough pours — pours clean. It started the
	 * moment its first 25 heat came in, under the 40 a pour costs, so the first tool always came out rough.
	 */
	@GameTest
	public void tableOnALanternPoursClean(GameTestHelper helper) {
		BlockPos at = POT.above();
		helper.setBlock(POT, ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(at, ModBlocks.MESA_DE_LOSA.defaultBlockState());
		CastingTableBlockEntity table = helper.getBlockEntity(at, CastingTableBlockEntity.class);
		tank(helper, at.east()).fill(Items.IRON_INGOT, 60);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingFrameItem.of(ForgeType.PICO));
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		for (int tick = 0; tick < CastingTableBlockEntity.COOK + 60; tick++) {
			CastingTableBlockEntity.serverTick(level, where, level.getBlockState(where), table);
		}
		ItemStack made = table.result();
		helper.assertTrue(!made.isEmpty() && !made.getOrDefault(ModComponents.ROUGH, false), "sobre un farol sale limpio: " + made);
		helper.succeed();
	}

	/** A loose part melts back down like finished gear does, and gives the ember back for it. */
	@GameTest
	public void loosePartMeltsBack(GameTestHelper helper) {
		CrucibleBlockEntity pot = pot(helper, POT, ModBlocks.CRISOL_DE_OBSIDIANA);
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.ORO));
		pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 1));
		run(helper, POT, pot, dev.forja.block.CrucibleBlock.Tier.OBSIDIANA.cook + 5);
		ItemStack out = pot.getItem(CrucibleBlockEntity.SLOT_OUTPUT);
		helper.assertTrue(out.is(Items.GOLD_INGOT) && out.getCount() == PartType.CABEZA_PICO.cost,
			"una cabeza de pico de oro vuelve a ser oro: " + out);
		helper.assertTrue(pot.getItem(CrucibleBlockEntity.SLOT_FIRST).isEmpty(), "y la pieza se fundió");
		helper.succeed();
	}

	/** Breaking a pot, a box or a tank drops what was in it. Every one of them used to lose it all. */
	@GameTest
	public void brokenBlocksDropWhatTheyHeld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CrucibleBlockEntity pot = pot(helper, POT, ModBlocks.CRISOL_DE_BARRO);
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.RAW_IRON, 5));
		pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 3));
		BlockPos boxAt = POT.east().east();
		helper.setBlock(boxAt, ModBlocks.CAJA_DE_MOLDEO.defaultBlockState());
		helper.getBlockEntity(boxAt, CastingBoxBlockEntity.class).setItem(CastingBoxBlockEntity.SLOT_PATTERN,
			CastingMouldItem.of(PartType.HOJA));
		BlockPos tankAt = POT.south().south();
		tank(helper, tankAt).fill(Items.COPPER_INGOT, 70);
		for (BlockPos at : List.of(POT, boxAt, tankAt)) {
			level.destroyBlock(helper.absolutePos(at), true);
		}
		List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(POT)).inflate(6.0));
		java.util.function.Predicate<ItemStack> any = stack -> true;
		java.util.function.ToIntFunction<java.util.function.Predicate<ItemStack>> count = which -> dropped.stream()
			.map(ItemEntity::getItem).filter(which).mapToInt(ItemStack::getCount).sum();
		helper.assertTrue(count.applyAsInt(stack -> stack.is(Items.RAW_IRON)) == 5 && count.applyAsInt(stack -> stack.is(ModItems.ASCUA)) == 3,
			"el crisol roto suelta su mena y sus ascuas");
		helper.assertTrue(count.applyAsInt(stack -> CastingMouldItem.partOf(stack) == PartType.HOJA) == 1, "la caja rota suelta su molde");
		helper.assertTrue(count.applyAsInt(stack -> stack.is(Items.COPPER_INGOT)) == 70, "la cuba rota derrama su cobre");
		helper.assertTrue(count.applyAsInt(any) > 0, "algo cayó");
		helper.succeed();
	}

	/** What a save keeps: the pot's slots and fire, the tank's metal and heat, the table's pour. */
	@GameTest
	public void foundrySurvivesASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		CrucibleBlockEntity pot = pot(helper, POT, ModBlocks.CRISOL_DE_HIERRO);
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.COPPER_INGOT, 4));
		pot.setItem(CrucibleBlockEntity.SLOT_SECOND, new ItemStack(Items.IRON_INGOT, 2));
		pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 3));
		run(helper, POT, pot, 30);
		var copy = (CrucibleBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pot.getBlockPos(),
			pot.getBlockState(), pot.saveWithFullMetadata(registries), registries);
		helper.assertTrue(copy != null && copy.getItem(0).getCount() == 4 && copy.getItem(1).getCount() == 2
			&& copy.getItem(2).getCount() == 2 && copy.isLit() && copy.progressPercent() == pot.progressPercent(),
			"el crisol guarda lo que tiene, su fuego y su colada a medias");

		MeltTankBlockEntity tank = tank(helper, POT.east());
		tank.fill(Items.GOLD_INGOT, 77);
		var tankCopy = (MeltTankBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(tank.getBlockPos(),
			tank.getBlockState(), tank.saveWithFullMetadata(registries), registries);
		helper.assertTrue(tankCopy != null && tankCopy.metal() == Items.GOLD_INGOT && tankCopy.amount() == 77
			&& tankCopy.heat() == tank.heat(), "la cuba guarda su metal y su calor");
		helper.succeed();
	}
}
