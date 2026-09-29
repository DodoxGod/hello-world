package dev.forja.test;

import java.util.List;

import dev.forja.block.entity.CastingBoxBlockEntity;
import dev.forja.block.entity.CastingTableBlockEntity;
import dev.forja.block.entity.CrucibleBlockEntity;
import dev.forja.block.entity.MeltTankBlockEntity;
import dev.forja.block.entity.StrainerBlockEntity;
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
 * one pour into its own tank, and what survives a save.
 *
 * <p>And casting on the tables (Andy, same day: "¿se podría hacer que el líquido tenga que caer en la
 * herramienta? pasando primero por el colador antes de que llegue a la mesa con el molde"): a mould on a
 * table with a strainer standing on it, the strainer that breaks, no strainer, a frame, the stone's limit,
 * the strainer block itself, a spout pouring through it, hoppers — and the box that no longer casts.
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
	 * The casting box no longer casts. Andy, 2026-09-28: the liquid has to fall onto the mould through the
	 * strainer, so a mould in the box over a full tank just waits there to be taken out, and the old strainer
	 * gate keeps what an older world left in it without taking anything new.
	 */
	@GameTest
	public void castingBoxNoLongerCasts(GameTestHelper helper) {
		helper.setBlock(POT, ModBlocks.CAJA_DE_MOLDEO_DE_DAMASCO.defaultBlockState());
		CastingBoxBlockEntity box = helper.getBlockEntity(POT, CastingBoxBlockEntity.class);
		MeltTankBlockEntity tank = tank(helper, POT.east());
		tank.fill(Items.IRON_INGOT, 60);
		box.setItem(CastingBoxBlockEntity.SLOT_PATTERN, CastingMouldItem.of(PartType.CABEZA_PICO));
		// What an older world could have left in the strainer gate.
		box.setItem(CastingBoxBlockEntity.SLOT_STRAINER, StrainerItem.of(ForgeMaterial.NETHERITA));
		runBox(helper, POT, box, dev.forja.block.CastingBoxBlock.Tier.DAMASCO.cook * 3);
		helper.assertTrue(box.getItem(CastingBoxBlockEntity.SLOT_OUTPUT).isEmpty(), "la caja ya no cuela piezas");
		helper.assertTrue(tank.bankAmount() == 60, "ni toca la cuba: " + tank.bankAmount());
		helper.assertTrue(CastingMouldItem.partOf(box.getItem(CastingBoxBlockEntity.SLOT_PATTERN)) == PartType.CABEZA_PICO,
			"el molde se queda ahí para sacarlo");
		helper.assertTrue(StrainerItem.materialOf(box.getItem(CastingBoxBlockEntity.SLOT_STRAINER)) == ForgeMaterial.NETHERITA,
			"y el colador viejo también");
		helper.assertFalse(CastingBoxBlockEntity.allowed(CastingBoxBlockEntity.SLOT_STRAINER, StrainerItem.of(null)),
			"al hueco del colador ya no entra nada");
		helper.assertTrue(box.describe().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents said
			&& said.getKey().equals("gui.forja.caja.molde_a_mesa"), "y dice dónde se cuela ahora");

		// Through the screen: the old gate shows only while it holds that strainer, and gives it back.
		Player player = helper.makeMockServerPlayerInLevel();
		// A mock player comes with a full bag (every stack the test world hands out); make room first.
		player.getInventory().clearContent();
		AbstractContainerMenu menu = box.createMenu(1, player.getInventory(), player);
		helper.assertTrue(menu.getSlot(CastingBoxBlockEntity.SLOT_STRAINER).isActive(), "el hueco viejo se ve mientras tiene algo");
		menu.clicked(CastingBoxBlockEntity.SLOT_STRAINER, 0, ContainerInput.QUICK_MOVE, player);
		helper.assertTrue(box.getItem(CastingBoxBlockEntity.SLOT_STRAINER).isEmpty(), "el colador sale del hueco viejo: queda "
			+ box.getItem(CastingBoxBlockEntity.SLOT_STRAINER) + ", en el menú " + menu.getSlot(CastingBoxBlockEntity.SLOT_STRAINER).getItem()
			+ ", en el inventario " + player.getInventory().countItem(ModItems.COLADOR));
		helper.assertFalse(menu.getSlot(CastingBoxBlockEntity.SLOT_STRAINER).isActive(), "y vacío ya no se ve");
		helper.assertFalse(menu.getSlot(CastingBoxBlockEntity.SLOT_STRAINER).mayPlace(StrainerItem.of(null)), "ni admite nada");
		// And a box from an older world that is saved and loaded keeps its slots where they were.
		var registries = helper.getLevel().registryAccess();
		box.setItem(CastingBoxBlockEntity.SLOT_STRAINER, StrainerItem.of(null));
		var copy = (CastingBoxBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(box.getBlockPos(),
			box.getBlockState(), box.saveWithFullMetadata(registries), registries);
		helper.assertTrue(copy != null && CastingMouldItem.partOf(copy.getItem(CastingBoxBlockEntity.SLOT_PATTERN)) == PartType.CABEZA_PICO
			&& copy.getItem(CastingBoxBlockEntity.SLOT_STRAINER).getItem() instanceof StrainerItem,
			"una caja guardada con molde y colador carga igual");
		helper.succeed();
	}

	/** The box's screen takes a finished tool (to cut its frame) and strainers (to bathe), and nothing else odd. */
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
		helper.assertTrue(box.getItem(CastingBoxBlockEntity.SLOT_PATTERN).getItem() instanceof StrainerItem,
			"con mayúsculas el colador va arriba, a bañarse");
		helper.assertTrue(box.getItem(CastingBoxBlockEntity.SLOT_STRAINER).isEmpty(), "y no al hueco viejo del colador");
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

	// ------------------------------------------------------------------ casting on the tables

	/** Where the table stands in these tests: on a wisp lantern, with its strainer on top and a tank beside. */
	private static final BlockPos TABLE = POT.above();

	/** A table on a lantern, a tank of this metal beside it, and (if given) a strainer of this make on top. */
	private static CastingTableBlockEntity castingRig(GameTestHelper helper, Block tier, net.minecraft.world.item.Item metal,
		@org.jspecify.annotations.Nullable ForgeMaterial strainer, boolean withStrainer) {
		helper.setBlock(POT, ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(TABLE, tier.defaultBlockState());
		tank(helper, TABLE.east()).fill(metal, 120);
		if (withStrainer) {
			helper.setBlock(TABLE.above(), ModBlocks.COLADOR.defaultBlockState());
			helper.getBlockEntity(TABLE.above(), StrainerBlockEntity.class).setMaterial(strainer);
		}
		return helper.getBlockEntity(TABLE, CastingTableBlockEntity.class);
	}

	private static void runTable(GameTestHelper helper, BlockPos at, CastingTableBlockEntity table, int ticks) {
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		for (int tick = 0; tick < ticks; tick++) {
			CastingTableBlockEntity.serverTick(level, where, level.getBlockState(where), table);
		}
	}

	/** Two seconds on the lantern to warm up, and a whole pour. */
	private static final int ONE_POUR = 60 + CastingTableBlockEntity.COOK;

	/**
	 * A mould on a table, a strainer that holds on top of it: the part comes out clean, marked as poured,
	 * carrying the one upgrade the box used to give it, for exactly what that part is worth.
	 */
	@GameTest
	public void tableCastsAPartThroughItsStrainer(GameTestHelper helper) {
		CastingTableBlockEntity table = castingRig(helper, ModBlocks.MESA_DE_LOSA, Items.IRON_INGOT, null, true);
		MeltTankBlockEntity tank = helper.getBlockEntity(TABLE.east(), MeltTankBlockEntity.class);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingMouldItem.of(PartType.CABEZA_PICO));
		runTable(helper, TABLE, table, ONE_POUR);
		ItemStack part = table.result();
		helper.assertTrue(part.getItem() == ModItems.part(PartType.CABEZA_PICO) && part.get(ModComponents.MATERIAL) == ForgeMaterial.HIERRO,
			"sale una cabeza de pico de hierro: " + part);
		helper.assertTrue(part.getOrDefault(ModComponents.COLADA, false) && !part.getOrDefault(ModComponents.ROUGH, false),
			"limpia, por un colador de barro que aguanta el hierro");
		var upgrades = part.getOrDefault(ModComponents.UPGRADES, dev.forja.upgrade.Upgrades.EMPTY).percents();
		helper.assertTrue(upgrades.size() == 1 && upgrades.values().iterator().next() == CastingTableBlockEntity.CAST_PERCENT,
			"con su mejora al " + CastingTableBlockEntity.CAST_PERCENT + "%: " + upgrades);
		helper.assertTrue(tank.bankAmount() == 120 - PartType.CABEZA_PICO.cost, "gasta lo que vale la pieza: " + (120 - tank.bankAmount()));
		helper.assertTrue(CastingMouldItem.partOf(table.frame()) == PartType.CABEZA_PICO, "y el molde se queda en la mesa");
		helper.assertTrue(helper.getBlockEntity(TABLE.above(), StrainerBlockEntity.class) != null, "el colador sigue en pie");
		// It waits with its upgrade until it is taken, and the next one does not overwrite it.
		ItemStack first = part.copy();
		runTable(helper, TABLE, table, ONE_POUR);
		helper.assertTrue(ItemStack.isSameItemSameComponents(table.result(), first), "espera a que la saquen, sin cambiar de mejora");
		helper.succeed();
	}

	/** Diamond through a clay strainer: the strainer breaks in the pour, drops nothing, and the part is rough. */
	@GameTest
	public void aStrainerThatCannotTakeItBreaks(GameTestHelper helper) {
		CastingTableBlockEntity table = castingRig(helper, ModBlocks.MESA_DE_ALMAS, Items.DIAMOND, null, true);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingMouldItem.of(PartType.CABEZA_PICO));
		runTable(helper, TABLE, table, 60);
		helper.assertTrue(table.metal() == Items.DIAMOND, "empezó a colar diamante");
		helper.assertTrue(table.pouringRough(), "y ya sabe que saldrá basta");
		helper.assertBlockNotPresent(ModBlocks.COLADOR, TABLE.above());
		int strainers = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
			new net.minecraft.world.phys.AABB(helper.absolutePos(TABLE)).inflate(3.0)).stream()
			.filter(item -> item.getItem().getItem() instanceof StrainerItem).mapToInt(item -> item.getItem().getCount()).sum();
		helper.assertTrue(strainers == 0, "el colador roto se va con la colada, no cae al suelo");
		runTable(helper, TABLE, table, CastingTableBlockEntity.COOK);
		ItemStack part = table.result();
		helper.assertTrue(!part.isEmpty() && part.getOrDefault(ModComponents.ROUGH, false), "la pieza sale basta: " + part);
		helper.assertFalse(part.has(ModComponents.UPGRADES), "y sin mejora");
		helper.succeed();
	}

	/** No strainer on the table at all: the pour is rough, part or tool. */
	@GameTest
	public void noStrainerPoursRough(GameTestHelper helper) {
		CastingTableBlockEntity table = castingRig(helper, ModBlocks.MESA_DE_LOSA, Items.IRON_INGOT, null, false);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingMouldItem.of(PartType.HOJA));
		runTable(helper, TABLE, table, ONE_POUR);
		ItemStack part = table.result();
		helper.assertTrue(!part.isEmpty() && part.getOrDefault(ModComponents.ROUGH, false) && !part.has(ModComponents.UPGRADES),
			"sin colador la hoja sale basta y sin mejora: " + part);
		table.setItem(CastingTableBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingFrameItem.of(ForgeType.PICO));
		runTable(helper, TABLE, table, CastingTableBlockEntity.COOK + 20);
		ItemStack tool = table.result();
		helper.assertTrue(tool.has(ModComponents.PARTS) && tool.getOrDefault(ModComponents.ROUGH, false),
			"y un pico entero también: " + tool);
		helper.succeed();
	}

	/** A frame through a strainer that holds: the whole tool comes out clean, every slot of it cast. */
	@GameTest
	public void aFrameThroughAStrainerIsClean(GameTestHelper helper) {
		CastingTableBlockEntity table = castingRig(helper, ModBlocks.MESA_DE_LOSA, Items.IRON_INGOT, ForgeMaterial.ACERO, true);
		MeltTankBlockEntity tank = helper.getBlockEntity(TABLE.east(), MeltTankBlockEntity.class);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingFrameItem.of(ForgeType.PICO));
		runTable(helper, TABLE, table, ONE_POUR);
		ItemStack tool = table.result();
		var parts = tool.get(ModComponents.PARTS);
		helper.assertTrue(parts != null && parts.type() == ForgeType.PICO, "sale un pico: " + tool);
		helper.assertFalse(tool.getOrDefault(ModComponents.ROUGH, false), "limpio por un colador de acero");
		helper.assertTrue(tool.getOrDefault(ModComponents.COLADAS, 0) == (1 << ForgeType.PICO.slots.size()) - 1, "con todas sus piezas coladas");
		helper.assertTrue(120 - tank.bankAmount() == CastingFrameItem.cost(ForgeType.PICO), "gastando lo que vale el pico");
		helper.succeed();
	}

	/** The table's stone limits the metal, as the box's material did: slate refuses diamond, soul takes it. */
	@GameTest
	public void theTableStoneLimitsTheMetal(GameTestHelper helper) {
		CastingTableBlockEntity slate = castingRig(helper, ModBlocks.MESA_DE_LOSA, Items.DIAMOND, ForgeMaterial.NETHERITA, true);
		MeltTankBlockEntity tank = helper.getBlockEntity(TABLE.east(), MeltTankBlockEntity.class);
		slate.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingMouldItem.of(PartType.CABEZA_PICO));
		runTable(helper, TABLE, slate, ONE_POUR);
		helper.assertTrue(slate.result().isEmpty() && tank.bankAmount() == 120, "la mesa de losa no aguanta el diamante");
		helper.setBlock(TABLE, ModBlocks.MESA_DE_ALMAS.defaultBlockState());
		CastingTableBlockEntity soul = helper.getBlockEntity(TABLE, CastingTableBlockEntity.class);
		soul.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingMouldItem.of(PartType.CABEZA_PICO));
		runTable(helper, TABLE, soul, ONE_POUR);
		ItemStack part = soul.result();
		helper.assertTrue(part.get(ModComponents.MATERIAL) == ForgeMaterial.DIAMANTE && part.getOrDefault(ModComponents.COLADA, false),
			"la de almas sí, y limpia por un colador de netherita: " + part);
		helper.succeed();
	}

	/**
	 * The strainer as a block: placed by using the colador on the table (from its side too), it keeps the
	 * metal it was made of, and broken it drops the very same colador. Clicking it with a mould or an empty
	 * hand reaches the table under it.
	 */
	@GameTest
	public void theStrainerBlockKeepsItsMetal(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.MESA_DE_BRASA.defaultBlockState());
		ServerLevel level = helper.getLevel();
		BlockPos table = helper.absolutePos(TABLE);
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(table.getX() + 3.5, table.getY(), table.getZ() + 0.5, 90.0F, 0.0F);
		// A mock player comes in creative, where a block placed is not taken from the hand.
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		ItemStack held = StrainerItem.of(ForgeMaterial.DAMASCO);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
		// Clicked on the table's east side: it still goes on top.
		var result = held.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
			new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(table).add(0.5, 0.0, 0.0),
				net.minecraft.core.Direction.EAST, table, false)));
		helper.assertTrue(result.consumesAction(), "el colador se pone: " + result);
		helper.assertBlockPresent(ModBlocks.COLADOR, TABLE.above());
		StrainerBlockEntity strainer = helper.getBlockEntity(TABLE.above(), StrainerBlockEntity.class);
		helper.assertTrue(strainer.material() == ForgeMaterial.DAMASCO && strainer.holds() == ForgeMaterial.DAMASCO.durability,
			"y es de damasco: " + strainer.material());
		helper.assertTrue(held.isEmpty(), "y sale de la mano");

		// A mould on the strainer is a mould on the table.
		ItemStack mould = CastingMouldItem.of(PartType.MANGO);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, mould);
		BlockPos over = helper.absolutePos(TABLE.above());
		var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(over),
			net.minecraft.core.Direction.UP, over, false);
		// Through the same path a real right click takes, so an empty hand is tried the way the game tries it.
		player.gameMode.useItemOn(player, level, mould, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		CastingTableBlockEntity bench = helper.getBlockEntity(TABLE, CastingTableBlockEntity.class);
		helper.assertTrue(CastingMouldItem.partOf(bench.frame()) == PartType.MANGO, "el molde, puesto a través del colador");
		// A finished piece on the table comes off with an empty hand clicked on the strainer, then the mould.
		bench.setItem(CastingTableBlockEntity.SLOT_OUTPUT, dev.forja.forge.Assembler.createPart(PartType.MANGO, ForgeMaterial.HIERRO));
		player.getInventory().clearContent();
		player.gameMode.useItemOn(player, level, ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(bench.result().isEmpty() && player.getInventory().countItem(ModItems.part(PartType.MANGO)) == 1,
			"con la mano vacía sobre el colador se recoge lo colado");
		// And a different mould swaps places with the one on the table, instead of being refused.
		ItemStack other = CastingMouldItem.of(PartType.HOJA);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, other);
		player.gameMode.useItemOn(player, level, other, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(CastingMouldItem.partOf(bench.frame()) == PartType.HOJA
			&& player.getInventory().countItem(ModItems.MOLDE_DE_FUNDICION) == 1, "otro molde se cambia por el que había");
		player.getInventory().clearContent();
		player.gameMode.useItemOn(player, level, ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(bench.frame().isEmpty(), "y con la mano vacía se recoge el molde");

		// Saved and loaded, it is still damascus.
		var registries = level.registryAccess();
		var copy = (StrainerBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(strainer.getBlockPos(),
			strainer.getBlockState(), strainer.saveWithFullMetadata(registries), registries);
		helper.assertTrue(copy != null && copy.material() == ForgeMaterial.DAMASCO, "guardado y cargado sigue siendo de damasco");

		// Broken, it drops the same colador: its metal and its tint.
		level.destroyBlock(over, true, player);
		List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(over).inflate(2.0));
		ItemStack back = dropped.stream().map(ItemEntity::getItem).filter(stack -> stack.getItem() instanceof StrainerItem)
			.findFirst().orElse(ItemStack.EMPTY);
		helper.assertTrue(ItemStack.isSameItemSameComponents(back, StrainerItem.of(ForgeMaterial.DAMASCO)),
			"roto suelta el mismo colador de damasco: " + back + " " + back.getComponentsPatch());

		// And a plain one knocked off by taking the table away drops a plain one.
		helper.setBlock(TABLE.above(), ModBlocks.COLADOR.defaultBlockState());
		dropped.forEach(net.minecraft.world.entity.Entity::discard);
		level.destroyBlock(table, false);
		List<ItemEntity> fell = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(over).inflate(2.0));
		helper.assertBlockNotPresent(ModBlocks.COLADOR, TABLE.above());
		helper.assertTrue(fell.stream().anyMatch(item -> ItemStack.isSameItemSameComponents(item.getItem(), StrainerItem.of(null))),
			"sin la mesa debajo se cae, y cae el colador de barro");
		helper.succeed();
	}

	/** The stream from a spout overhead goes through the strainer: the table still finds the tank, and casts. */
	@GameTest
	public void aSpoutPoursThroughTheStrainer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(POT, ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(TABLE, ModBlocks.MESA_DE_BRASA.defaultBlockState());
		helper.setBlock(TABLE.above(), ModBlocks.COLADOR.defaultBlockState());
		BlockPos spout = TABLE.above(2);
		helper.setBlock(spout.east(), ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
		tank(helper, spout.east().east()).fill(Items.GOLD_INGOT, 60);
		helper.setBlock(spout, ModBlocks.CANO_DE_COLADA.defaultBlockState());
		BlockPos lands = dev.forja.block.MeltPipeBlock.landing(level, helper.absolutePos(spout));
		helper.assertTrue(helper.absolutePos(TABLE).equals(lands), "el caño cae en la mesa a través del colador: " + lands);
		helper.assertTrue(helper.absolutePos(TABLE.above()).equals(dev.forja.block.MeltPipeBlock.strainerUnder(level, helper.absolutePos(spout))),
			"y sabe que pasa por el colador");
		CastingTableBlockEntity table = helper.getBlockEntity(TABLE, CastingTableBlockEntity.class);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingMouldItem.of(PartType.CABEZA_PICO));
		runTable(helper, TABLE, table, ONE_POUR);
		ItemStack part = table.result();
		helper.assertTrue(part.get(ModComponents.MATERIAL) == ForgeMaterial.ORO && part.getOrDefault(ModComponents.COLADA, false),
			"cuela oro limpio desde el caño: " + part);
		helper.succeed();
	}

	/**
	 * Automation: a hopper under the table takes the cast part out, a hopper beside it puts the mould in
	 * (its top is the strainer's now), and the strainer on top gets in the way of neither.
	 */
	@GameTest(maxTicks = 400)
	public void hoppersRunATableUnderAStrainer(GameTestHelper helper) {
		// The table is kept hot from the side here, because the hopper is underneath.
		BlockPos table = new BlockPos(2, 2, 2);
		helper.setBlock(table.north(), ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(table, ModBlocks.MESA_DE_LOSA.defaultBlockState());
		helper.setBlock(table.above(), ModBlocks.COLADOR.defaultBlockState());
		tank(helper, table.east()).fill(Items.IRON_INGOT, 120);
		helper.setBlock(table.below(), Blocks.HOPPER.defaultBlockState()
			.setValue(net.minecraft.world.level.block.HopperBlock.FACING, net.minecraft.core.Direction.DOWN));
		helper.setBlock(table.west(), Blocks.HOPPER.defaultBlockState()
			.setValue(net.minecraft.world.level.block.HopperBlock.FACING, net.minecraft.core.Direction.EAST));
		var feeder = (net.minecraft.world.Container) helper.getBlockEntity(table.west(), net.minecraft.world.level.block.entity.BlockEntity.class);
		feeder.setItem(0, CastingMouldItem.of(PartType.CABEZA_PICO));
		feeder.setItem(1, new ItemStack(Items.STICK));
		helper.succeedWhen(() -> {
			var below = (net.minecraft.world.Container) helper.getBlockEntity(table.below(), net.minecraft.world.level.block.entity.BlockEntity.class);
			ItemStack out = below.getItem(0);
			helper.assertTrue(out.getItem() == ModItems.part(PartType.CABEZA_PICO) && out.getOrDefault(ModComponents.COLADA, false),
				"la tolva de abajo recibe la cabeza colada, tiene " + out);
			helper.assertTrue(feeder.getItem(1).is(Items.STICK), "y el palo no entra en la mesa");
			helper.assertBlockPresent(ModBlocks.COLADOR, table.above());
		});
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
	 * (With a strainer on it: without one every pour is rough now, see noStrainerPoursRough.)
	 */
	@GameTest
	public void tableOnALanternPoursClean(GameTestHelper helper) {
		BlockPos at = POT.above();
		helper.setBlock(POT, ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(at, ModBlocks.MESA_DE_LOSA.defaultBlockState());
		helper.setBlock(at.above(), ModBlocks.COLADOR.defaultBlockState());
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

		// A table halfway through pouring a part through no strainer: the mould, the metal, and that it is
		// going to come out rough, all survive. (The gold tank goes first: the table's own iron one would
		// stand on it and make one bank of the two.)
		helper.setBlock(POT.east(), Blocks.AIR.defaultBlockState());
		CastingTableBlockEntity table = castingRig(helper, ModBlocks.MESA_DE_LOSA, Items.IRON_INGOT, null, false);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingMouldItem.of(PartType.HOJA));
		runTable(helper, TABLE, table, 60);
		helper.assertTrue(table.metal() == Items.IRON_INGOT, "la mesa está colando");
		var tableCopy = (CastingTableBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(table.getBlockPos(),
			table.getBlockState(), table.saveWithFullMetadata(registries), registries);
		helper.assertTrue(tableCopy != null && CastingMouldItem.partOf(tableCopy.frame()) == PartType.HOJA
			&& tableCopy.metal() == Items.IRON_INGOT && tableCopy.amount() == PartType.HOJA.cost && tableCopy.pouringRough(),
			"la mesa guarda su molde, su colada y que saldrá basta");
		helper.succeed();
	}
}
