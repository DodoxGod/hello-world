package dev.forja.test;

import java.util.List;

import dev.forja.block.entity.AssemblerMachineBlockEntity;
import dev.forja.block.entity.CastingTableBlockEntity;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Masterpiece;
import dev.forja.forge.Potential;
import dev.forja.forge.Quality;
import dev.forja.item.CastingFrameItem;
import dev.forja.item.CastingMouldItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.menu.AssemblerMachineMenu;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * La montadora and the foundry that runs by itself (docs/FUNDICION_V2.md, part C; Andy, 2026-09-28: "alguna
 * forma para automatizar todo el sistema de forja", with "una máquina que monta herramientas con calidad
 * normal (el golpe perfecto sigue siendo del jugador)").
 *
 * <p>The assembler builds a pickaxe, a sword and a piece of armour from parts, fed by hopper or laid on its
 * star, always at a plain press and never a perfect one; it takes nothing but parts; it needs heat; a
 * comparator reads it; broken it drops what it held; it survives a save; a frame makes it build a
 * greatsword out of one pile of blades and keeps it from jamming. And the whole chain: raw iron in a hopper
 * to a finished pickaxe in a chest, with nobody touching anything.
 */
public class MontadoraGameTests {
	private static final BlockPos AT = new BlockPos(2, 2, 2);

	/** An assembler with a wisp lantern beside it: molten heat, so the quickest work there is. */
	private static AssemblerMachineBlockEntity machine(GameTestHelper helper, BlockPos at, boolean heated) {
		helper.setBlock(at, ModBlocks.MONTADORA.defaultBlockState());
		if (heated) {
			helper.setBlock(at.east(), ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		}
		return helper.getBlockEntity(at, AssemblerMachineBlockEntity.class);
	}

	/** Runs the machine by hand, many ticks inside one. */
	private static void run(GameTestHelper helper, BlockPos at, AssemblerMachineBlockEntity machine, int ticks) {
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		for (int tick = 0; tick < ticks; tick++) {
			AssemblerMachineBlockEntity.serverTick(level, where, level.getBlockState(where), machine);
		}
	}

	private static ItemStack part(PartType type, ForgeMaterial material) {
		return Assembler.createPart(type, material);
	}

	/** What a piece the machine made must be: a new piece of that type, a plain press, no hand on it. */
	private static void assertPlain(GameTestHelper helper, ItemStack made, ForgeType type) {
		ForgedParts parts = made.get(ModComponents.PARTS);
		helper.assertTrue(parts != null && parts.type() == type, "sale un " + type + ": " + made);
		helper.assertFalse(Quality.perfect(made), "nunca perfecta: el golpe perfecto es del jugador");
		helper.assertFalse(Masterpiece.is(made), "ni obra maestra");
		helper.assertTrue(Quality.smith(made) == null, "sin firma: no la forjó nadie");
		int potential = made.getOrDefault(ModComponents.POTENCIAL, -1);
		helper.assertTrue(potential == Potential.atForge(AssemblerMachineBlockEntity.QUALITY, null, null, false),
			"con el potencial de un golpe normal: " + potential);
	}

	private static Container container(GameTestHelper helper, BlockPos at) {
		return (Container) helper.getBlockEntity(at, BlockEntity.class);
	}

	private static int countIn(Container container, java.util.function.Predicate<ItemStack> which) {
		int count = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (which.test(container.getItem(i))) {
				count += container.getItem(i).getCount();
			}
		}
		return count;
	}

	/**
	 * Hoppers only: a hopper on top with a pick head, a handle and a binding, a hopper underneath, a chest.
	 * The pickaxe arrives in the chest a plain press, with nobody at the machine.
	 */
	@GameTest(maxTicks = 400)
	public void hopperFedPickaxe(GameTestHelper helper) {
		machine(helper, AT, true);
		helper.setBlock(AT.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.setBlock(AT.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
		helper.setBlock(AT.below().west(), Blocks.CHEST.defaultBlockState());
		Container feeder = container(helper, AT.above());
		feeder.setItem(0, part(PartType.CABEZA_PICO, ForgeMaterial.HIERRO));
		feeder.setItem(1, part(PartType.MANGO, ForgeMaterial.MADERA));
		feeder.setItem(2, part(PartType.ATADURA, ForgeMaterial.CUERO));
		helper.succeedWhen(() -> {
			Container chest = container(helper, AT.below().west());
			ItemStack pick = chest.getItem(0);
			helper.assertTrue(pick.has(ModComponents.PARTS), "el pico llega al cofre, hay " + pick);
			assertPlain(helper, pick, ForgeType.PICO);
			helper.assertTrue(pick.get(ModComponents.PARTS).materials().equals(List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO)),
				"de hierro, madera y cuero: " + pick.get(ModComponents.PARTS).materials());
			AssemblerMachineBlockEntity machine = helper.getBlockEntity(AT, AssemblerMachineBlockEntity.class);
			for (int i = 0; i < AssemblerMachineBlockEntity.POINTS; i++) {
				helper.assertTrue(machine.getItem(i).isEmpty(), "y gastó las tres piezas");
			}
		});
	}

	/**
	 * A sword and a chestplate laid on the star, exactly what the forge table would make of the same parts,
	 * and twenty pieces in a row without a single perfect one.
	 */
	@GameTest
	public void swordAndArmourAreAlwaysPlain(GameTestHelper helper) {
		AssemblerMachineBlockEntity machine = machine(helper, AT, true);
		List<ItemStack> sword = List.of(part(PartType.HOJA, ForgeMaterial.DIAMANTE), part(PartType.MANGO, ForgeMaterial.MADERA),
			part(PartType.GUARDA, ForgeMaterial.ORO));
		for (int i = 0; i < sword.size(); i++) {
			machine.setItem(i, sword.get(i).copyWithCount(20));
		}
		ItemStack table = Assembler.evaluate(sword, helper.getLevel().registryAccess()).stack();
		int made = 0;
		for (int piece = 0; piece < 20; piece++) {
			run(helper, AT, machine, AssemblerMachineBlockEntity.WORK_FUNDIDA + 12);
			ItemStack out = machine.removeItem(AssemblerMachineBlockEntity.SLOT_OUTPUT, 1);
			if (out.isEmpty()) {
				continue;
			}
			made++;
			assertPlain(helper, out, ForgeType.ESPADA);
			helper.assertTrue(out.get(ModComponents.PARTS).equals(table.get(ModComponents.PARTS)),
				"la misma espada que la mesa: " + out.get(ModComponents.PARTS));
			helper.assertTrue(dev.forja.forge.Temple.hot(out, helper.getLevel().getGameTime()), "y sale caliente, para templarla");
		}
		helper.assertTrue(made == 20, "veinte espadas de veinte juegos, salieron " + made);

		machine.setItem(0, part(PartType.PLACA_PECHERA, ForgeMaterial.HIERRO));
		machine.setItem(1, part(PartType.FORRO, ForgeMaterial.CUERO));
		run(helper, AT, machine, AssemblerMachineBlockEntity.WORK_FUNDIDA + 12);
		assertPlain(helper, machine.getItem(AssemblerMachineBlockEntity.SLOT_OUTPUT), ForgeType.PECHERA);
		helper.succeed();
	}

	/**
	 * Parts and only parts: not a stick, an ingot, a finished tool or a frame on a point, from a hopper or a
	 * tank; the screen's points take parts and nothing else; a hopper full of junk stays full.
	 */
	@GameTest(maxTicks = 200)
	public void refusesWhatIsNotAPart(GameTestHelper helper) {
		AssemblerMachineBlockEntity machine = machine(helper, AT, true);
		ItemStack pick = Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO));
		for (ItemStack junk : List.of(new ItemStack(Items.STICK), new ItemStack(Items.IRON_INGOT), pick, CastingFrameItem.of(ForgeType.PICO),
			new ItemStack(ModItems.ASCUA), CastingMouldItem.of(PartType.HOJA))) {
			for (int slot = 0; slot < AssemblerMachineBlockEntity.SIZE; slot++) {
				helper.assertFalse(machine.canPlaceItemThroughFace(slot, junk, Direction.UP), "no entra " + junk + " en " + slot);
				helper.assertFalse(machine.canPlaceItemThroughFace(slot, junk, Direction.NORTH), "ni de lado " + junk);
			}
		}
		helper.assertTrue(machine.canPlaceItemThroughFace(0, part(PartType.HOJA, ForgeMaterial.HIERRO), Direction.UP), "una pieza sí");
		helper.assertFalse(machine.canPlaceItemThroughFace(0, part(PartType.HOJA, ForgeMaterial.HIERRO), Direction.DOWN), "pero no por abajo");
		helper.assertFalse(machine.canTakeItemThroughFace(0, ItemStack.EMPTY, Direction.DOWN), "por abajo sólo sale lo hecho");

		var player = helper.makeMockServerPlayerInLevel();
		AssemblerMachineMenu menu = (AssemblerMachineMenu) machine.createMenu(1, player.getInventory(), player);
		helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.STICK)), "la pantalla tampoco acepta un palo");
		helper.assertFalse(menu.getSlot(0).mayPlace(pick), "ni una herramienta hecha en una punta");
		helper.assertTrue(menu.getSlot(AssemblerMachineBlockEntity.SLOT_FRAME).mayPlace(CastingFrameItem.of(ForgeType.PICO)), "el marco va al centro");
		helper.assertFalse(menu.getSlot(AssemblerMachineBlockEntity.SLOT_FRAME).mayPlace(part(PartType.HOJA, ForgeMaterial.HIERRO)), "y una pieza no");
		helper.assertFalse(menu.getSlot(AssemblerMachineBlockEntity.SLOT_OUTPUT).mayPlace(part(PartType.HOJA, ForgeMaterial.HIERRO)), "ni nada a la salida");
		player.getInventory().setItem(0, new ItemStack(Items.STICK, 5));
		menu.clicked(AssemblerMachineBlockEntity.SIZE + 27, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
		for (int i = 0; i < AssemblerMachineBlockEntity.POINTS; i++) {
			helper.assertFalse(machine.getItem(i).is(Items.STICK), "con mayúsculas el palo no entra");
		}
		player.getInventory().setItem(1, part(PartType.MANGO, ForgeMaterial.MADERA).copyWithCount(4));
		menu.clicked(AssemblerMachineBlockEntity.SIZE + 28, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
		helper.assertTrue(machine.getItem(0).getCount() == 4 && machine.getItem(0).getItem() == ModItems.part(PartType.MANGO),
			"con mayúsculas la pila de mangos va a una punta: " + machine.getItem(0));

		helper.setBlock(AT.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		Container feeder = container(helper, AT.above());
		feeder.setItem(0, new ItemStack(Items.STICK, 3));
		feeder.setItem(1, new ItemStack(Items.IRON_INGOT, 3));
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(countIn(feeder, stack -> stack.is(Items.STICK)) == 3 && countIn(feeder, stack -> stack.is(Items.IRON_INGOT)) == 3,
				"lo que no es pieza se queda en la tolva");
			helper.succeed();
		});
	}

	/**
	 * Heat, as the forge table wants it: nothing without a fire; a campfire underneath works at warm heat,
	 * a lantern beside it at molten heat and faster; take the fire away and it waits.
	 */
	@GameTest
	public void needsHeat(GameTestHelper helper) {
		AssemblerMachineBlockEntity machine = machine(helper, AT, false);
		machine.setItem(0, part(PartType.CABEZA_HACHA, ForgeMaterial.HIERRO));
		machine.setItem(1, part(PartType.MANGO, ForgeMaterial.MADERA));
		machine.setItem(2, part(PartType.ATADURA, ForgeMaterial.CUERO));
		run(helper, AT, machine, 300);
		helper.assertTrue(machine.job() == AssemblerMachineBlockEntity.JOB_COLD, "fría dice que le falta calor: " + machine.job());
		helper.assertTrue(machine.getItem(AssemblerMachineBlockEntity.SLOT_OUTPUT).isEmpty(), "y no monta nada");

		helper.setBlock(AT.below(), Blocks.CAMPFIRE.defaultBlockState());
		run(helper, AT, machine, 12);
		helper.assertTrue(machine.heat() == dev.forja.forge.Alloys.Heat.TEMPLADA, "una fogata debajo: templada, " + machine.heat());
		run(helper, AT, machine, AssemblerMachineBlockEntity.WORK_TEMPLADA - 20);
		helper.assertTrue(machine.getItem(AssemblerMachineBlockEntity.SLOT_OUTPUT).isEmpty(), "templada tarda más de " + (AssemblerMachineBlockEntity.WORK_FUNDIDA) + " ticks");
		run(helper, AT, machine, 20);
		assertPlain(helper, machine.getItem(AssemblerMachineBlockEntity.SLOT_OUTPUT), ForgeType.HACHA);

		// The same, with the fire beside it instead: the hopper's place underneath is free.
		helper.setBlock(AT.below(), Blocks.AIR.defaultBlockState());
		helper.setBlock(AT.west(), Blocks.MAGMA_BLOCK.defaultBlockState());
		machine.setItem(AssemblerMachineBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
		machine.setItem(0, part(PartType.CABEZA_PALA, ForgeMaterial.HIERRO));
		machine.setItem(1, part(PartType.MANGO, ForgeMaterial.MADERA));
		machine.setItem(2, part(PartType.ATADURA, ForgeMaterial.CUERO));
		run(helper, AT, machine, 12 + AssemblerMachineBlockEntity.WORK_CALIENTE);
		helper.assertTrue(machine.heat() == dev.forja.forge.Alloys.Heat.CALIENTE, "magma al lado: caliente, " + machine.heat());
		assertPlain(helper, machine.getItem(AssemblerMachineBlockEntity.SLOT_OUTPUT), ForgeType.PALA);

		// The fire out mid-piece: it stops, and says why.
		helper.setBlock(AT.west(), Blocks.AIR.defaultBlockState());
		machine.setItem(AssemblerMachineBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
		machine.setItem(0, part(PartType.CABEZA_PALA, ForgeMaterial.HIERRO));
		machine.setItem(1, part(PartType.MANGO, ForgeMaterial.MADERA));
		machine.setItem(2, part(PartType.ATADURA, ForgeMaterial.CUERO));
		run(helper, AT, machine, 200);
		helper.assertTrue(machine.job() == AssemblerMachineBlockEntity.JOB_COLD && machine.getItem(AssemblerMachineBlockEntity.SLOT_OUTPUT).isEmpty(),
			"sin fuego se para: " + machine.job());
		// The hook a heat pipe will use (part B): a registered source heats it with nothing burning nearby.
		helper.assertTrue(dev.forja.forge.Alloys.heatAt(helper.getLevel(), helper.absolutePos(AT)) == dev.forja.forge.Alloys.Heat.FRIA,
			"heatAt sin fuego ni tubos es fría");
		helper.succeed();
	}

	/**
	 * The comparator: 0 idle, 1 to 14 while it works and rising, 15 while a finished piece waits. And a
	 * real comparator against it lights up.
	 */
	@GameTest(maxTicks = 200)
	public void comparatorReadsIt(GameTestHelper helper) {
		AssemblerMachineBlockEntity machine = machine(helper, AT, true);
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(AT);
		java.util.function.IntSupplier read = () -> level.getBlockState(where).getAnalogOutputSignal(level, where, Direction.NORTH);
		helper.assertTrue(level.getBlockState(where).hasAnalogOutputSignal(), "da señal a un comparador");
		helper.assertTrue(read.getAsInt() == 0, "parada, 0");
		machine.setItem(0, part(PartType.CABEZA_PICO, ForgeMaterial.HIERRO).copyWithCount(2));
		machine.setItem(1, part(PartType.MANGO, ForgeMaterial.MADERA).copyWithCount(2));
		machine.setItem(2, part(PartType.ATADURA, ForgeMaterial.CUERO).copyWithCount(2));
		run(helper, AT, machine, 12);
		int early = read.getAsInt();
		run(helper, AT, machine, AssemblerMachineBlockEntity.WORK_FUNDIDA / 2);
		int later = read.getAsInt();
		helper.assertTrue(early >= 1 && early < later && later <= 14, "trabajando sube de 1 a 14: " + early + " -> " + later);
		run(helper, AT, machine, AssemblerMachineBlockEntity.WORK_FUNDIDA);
		helper.assertTrue(read.getAsInt() == 15, "con la pieza esperando, 15: " + read.getAsInt());
		// A comparator set against it, facing away: it reads the full signal.
		helper.setBlock(AT.north(), Blocks.COMPARATOR.defaultBlockState()
			.setValue(net.minecraft.world.level.block.ComparatorBlock.FACING, Direction.SOUTH));
		helper.setBlock(AT.north().below(), Blocks.STONE.defaultBlockState());
		helper.succeedWhen(() -> {
			var comparator = helper.getBlockEntity(AT.north(), net.minecraft.world.level.block.entity.ComparatorBlockEntity.class);
			helper.assertTrue(comparator.getOutputSignal() == 15, "el comparador marca 15: " + comparator.getOutputSignal());
		});
	}

	/** Broken, it drops everything it held: the parts on the star, the frame and the finished piece. */
	@GameTest
	public void dropsWhatItHeldWhenBroken(GameTestHelper helper) {
		AssemblerMachineBlockEntity machine = machine(helper, AT, false);
		machine.setItem(0, part(PartType.HOJA, ForgeMaterial.HIERRO).copyWithCount(3));
		machine.setItem(3, part(PartType.GUARDA, ForgeMaterial.ORO));
		machine.setItem(AssemblerMachineBlockEntity.SLOT_FRAME, CastingFrameItem.of(ForgeType.ESPADA));
		machine.setItem(AssemblerMachineBlockEntity.SLOT_OUTPUT, Assembler.create(ForgeType.DAGA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA)));
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(AT);
		level.destroyBlock(where, true);
		List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(where).inflate(2.0));
		java.util.function.ToIntFunction<java.util.function.Predicate<ItemStack>> count = which -> dropped.stream()
			.map(ItemEntity::getItem).filter(which).mapToInt(ItemStack::getCount).sum();
		helper.assertTrue(count.applyAsInt(stack -> stack.getItem() == ModItems.part(PartType.HOJA)) == 3, "suelta las tres hojas");
		helper.assertTrue(count.applyAsInt(stack -> stack.getItem() == ModItems.part(PartType.GUARDA)) == 1, "y la guarda");
		helper.assertTrue(count.applyAsInt(stack -> CastingFrameItem.typeOf(stack) == ForgeType.ESPADA) == 1, "y el marco");
		helper.assertTrue(count.applyAsInt(stack -> stack.get(ModComponents.PARTS) != null && stack.get(ModComponents.PARTS).type() == ForgeType.DAGA) == 1,
			"y la daga hecha");
		helper.assertTrue(count.applyAsInt(stack -> stack.is(ModItems.MONTADORA)) == 1, "y a sí misma");
		helper.succeed();
	}

	/** A save halfway through a piece keeps the parts, the frame and the work done. */
	@GameTest
	public void survivesASave(GameTestHelper helper) {
		AssemblerMachineBlockEntity machine = machine(helper, AT, true);
		machine.setItem(AssemblerMachineBlockEntity.SLOT_FRAME, CastingFrameItem.of(ForgeType.ESPADON));
		machine.setItem(0, part(PartType.HOJA, ForgeMaterial.HIERRO).copyWithCount(2));
		machine.setItem(1, part(PartType.MANGO, ForgeMaterial.MADERA));
		machine.setItem(2, part(PartType.GUARDA, ForgeMaterial.HIERRO));
		run(helper, AT, machine, 12 + AssemblerMachineBlockEntity.WORK_FUNDIDA / 2);
		int progress = machine.progress();
		helper.assertTrue(progress > 0, "está a medias: " + progress);
		var registries = helper.getLevel().registryAccess();
		var copy = (AssemblerMachineBlockEntity) BlockEntity.loadStatic(machine.getBlockPos(), machine.getBlockState(),
			machine.saveWithFullMetadata(registries), registries);
		helper.assertTrue(copy != null && copy.getItem(0).getCount() == 2 && CastingFrameItem.typeOf(copy.getItem(AssemblerMachineBlockEntity.SLOT_FRAME)) == ForgeType.ESPADON
			&& copy.getItem(2).getItem() == ModItems.part(PartType.GUARDA), "guarda las piezas y el marco");
		// Put the copy in the world in place of the original and let it finish what was started.
		helper.getLevel().setBlockEntity(copy);
		AssemblerMachineBlockEntity loaded = helper.getBlockEntity(AT, AssemblerMachineBlockEntity.class);
		run(helper, AT, loaded, 1);
		helper.assertTrue(loaded.progress() == progress + 1, "y sigue donde iba: " + loaded.progress() + " tras " + progress);
		run(helper, AT, loaded, AssemblerMachineBlockEntity.WORK_FUNDIDA);
		assertPlain(helper, loaded.getItem(AssemblerMachineBlockEntity.SLOT_OUTPUT), ForgeType.ESPADON);
		helper.succeed();
	}

	/**
	 * A frame in the centre: the machine builds a greatsword out of ONE pile of blades (the star would read
	 * one blade and make a sword), takes only that piece's parts from a hopper, and does not jam.
	 */
	@GameTest(maxTicks = 400)
	public void aFrameBuildsAGreatswordAndNeverJams(GameTestHelper helper) {
		AssemblerMachineBlockEntity machine = machine(helper, AT, true);
		machine.setItem(AssemblerMachineBlockEntity.SLOT_FRAME, CastingFrameItem.of(ForgeType.ESPADON));
		helper.setBlock(AT.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.setBlock(AT.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
		helper.setBlock(AT.below().west(), Blocks.CHEST.defaultBlockState());
		Container feeder = container(helper, AT.above());
		// A pick head the greatsword does not use, first in the hopper: it must stay there.
		feeder.setItem(0, part(PartType.CABEZA_PICO, ForgeMaterial.HIERRO));
		feeder.setItem(1, part(PartType.HOJA, ForgeMaterial.HIERRO).copyWithCount(2));
		feeder.setItem(2, part(PartType.MANGO, ForgeMaterial.MADERA));
		feeder.setItem(3, part(PartType.GUARDA, ForgeMaterial.HIERRO));
		helper.succeedWhen(() -> {
			Container chest = container(helper, AT.below().west());
			ItemStack made = chest.getItem(0);
			helper.assertTrue(made.has(ModComponents.PARTS), "llega un espadón al cofre, hay " + made);
			assertPlain(helper, made, ForgeType.ESPADON);
			helper.assertTrue(countIn(feeder, stack -> stack.getItem() == ModItems.part(PartType.CABEZA_PICO)) == 1,
				"la cabeza de pico no entra: el marco pide un espadón");
		});
	}

	/**
	 * No frame, and a hopper of cast pick heads that do not stack (each poured with a different upgrade):
	 * one head at a time goes on the star, so the handle and the binding still get in, and pickaxes come out.
	 */
	@GameTest(maxTicks = 600)
	public void castHeadsDoNotJamTheStar(GameTestHelper helper) {
		machine(helper, AT, true);
		helper.setBlock(AT.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.setBlock(AT.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
		helper.setBlock(AT.below().west(), Blocks.CHEST.defaultBlockState());
		Container feeder = container(helper, AT.above());
		dev.forja.upgrade.Upgrade[] upgrades = {dev.forja.upgrade.Upgrade.values()[0], dev.forja.upgrade.Upgrade.values()[1], dev.forja.upgrade.Upgrade.values()[2]};
		for (int i = 0; i < 3; i++) {
			ItemStack head = part(PartType.CABEZA_PICO, ForgeMaterial.HIERRO);
			head.set(ModComponents.COLADA, true);
			head.set(ModComponents.UPGRADES, dev.forja.upgrade.Upgrades.EMPTY.with(upgrades[i], CastingTableBlockEntity.CAST_PERCENT));
			feeder.setItem(i, head);
		}
		feeder.setItem(3, part(PartType.MANGO, ForgeMaterial.MADERA).copyWithCount(2));
		feeder.setItem(4, part(PartType.ATADURA, ForgeMaterial.CUERO).copyWithCount(2));
		helper.succeedWhen(() -> {
			Container chest = container(helper, AT.below().west());
			int picks = countIn(chest, stack -> stack.get(ModComponents.PARTS) != null && stack.get(ModComponents.PARTS).type() == ForgeType.PICO);
			helper.assertTrue(picks == 2, "dos picos con dos mangos y dos ataduras, hay " + picks);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).has(ModComponents.PARTS)) {
					assertPlain(helper, chest.getItem(i), ForgeType.PICO);
					helper.assertTrue((chest.getItem(i).getOrDefault(ModComponents.COLADAS, 0) & 1) != 0, "con su cabeza colada");
				}
			}
		});
	}

	/** Barding is the saddlery's, and the machine says so instead of building it. */
	@GameTest
	public void bardingStaysAtTheSaddlery(GameTestHelper helper) {
		AssemblerMachineBlockEntity machine = machine(helper, AT, true);
		machine.setItem(0, part(PartType.PLACA_BARDA, ForgeMaterial.HIERRO));
		machine.setItem(1, part(PartType.FORRO, ForgeMaterial.CUERO));
		run(helper, AT, machine, 12 + AssemblerMachineBlockEntity.WORK_FUNDIDA * 2);
		helper.assertTrue(machine.job() == AssemblerMachineBlockEntity.JOB_BEYOND, "dice que es de la talabartería: " + machine.job());
		helper.assertTrue(machine.getItem(AssemblerMachineBlockEntity.SLOT_OUTPUT).isEmpty(), "y no la monta");
		helper.assertTrue(machine.getItem(0).getCount() == 1, "ni gasta la placa");
		helper.succeed();
	}

	/**
	 * The foundry with nobody at it, end to end. Raw iron in a hopper over an iron crucible, embers in a
	 * hopper at its side; the tank beside it on a wisp lantern; a channel to a spout three blocks over a
	 * casting table with a clay strainer on it, a lantern beside the table and a pick-head mould fed to it by
	 * a hopper; under the table a hopper that hands each cast head to an assembler, which a hopper on top
	 * gives a handle and a binding; under the assembler a hopper and a chest. Nothing is touched after the
	 * first tick, and a finished iron pickaxe with a cast head ends up in the chest.
	 */
	@GameTest(maxTicks = 1500)
	public void theWholeLineRunsItself(GameTestHelper helper) {
		BlockPos pot = new BlockPos(1, 6, 1);
		BlockPos tankAt = pot.east();
		BlockPos spout = new BlockPos(4, 6, 1);
		BlockPos table = new BlockPos(4, 3, 1);
		BlockPos machineAt = new BlockPos(4, 2, 2);
		BlockPos chestAt = new BlockPos(5, 1, 2);

		helper.setBlock(pot, ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
		helper.setBlock(pot.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.setBlock(pot.west(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
		helper.setBlock(tankAt.below(), ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(tankAt, ModBlocks.CUBA_DE_COLADA.defaultBlockState());
		helper.setBlock(tankAt.east(), ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
		helper.setBlock(spout, ModBlocks.CANO_DE_COLADA.defaultBlockState());

		helper.setBlock(table, ModBlocks.MESA_DE_LOSA.defaultBlockState());
		helper.setBlock(table.above(), ModBlocks.COLADOR.defaultBlockState());
		helper.setBlock(table.east(), ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(table.west(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
		helper.setBlock(table.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));

		helper.setBlock(machineAt, ModBlocks.MONTADORA.defaultBlockState());
		helper.setBlock(machineAt.east(), ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(machineAt.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.setBlock(machineAt.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
		helper.setBlock(chestAt, Blocks.CHEST.defaultBlockState());

		container(helper, pot.above()).setItem(0, new ItemStack(Items.RAW_IRON, 6));
		container(helper, pot.west()).setItem(0, new ItemStack(ModItems.ASCUA, 4));
		container(helper, table.west()).setItem(0, CastingMouldItem.of(PartType.CABEZA_PICO));
		Container partsIn = container(helper, machineAt.above());
		partsIn.setItem(0, part(PartType.MANGO, ForgeMaterial.MADERA).copyWithCount(2));
		partsIn.setItem(1, part(PartType.ATADURA, ForgeMaterial.CUERO).copyWithCount(2));

		helper.succeedWhen(() -> {
			Container chest = container(helper, chestAt);
			int picks = countIn(chest, stack -> stack.get(ModComponents.PARTS) != null && stack.get(ModComponents.PARTS).type() == ForgeType.PICO);
			helper.assertTrue(picks >= 2, "dos picos en el cofre (seis de mena son dos cabezas), hay " + picks
				+ " · cuba " + helper.getBlockEntity(tankAt, dev.forja.block.entity.MeltTankBlockEntity.class).bankAmount()
				+ " · mesa " + helper.getBlockEntity(table, CastingTableBlockEntity.class).describe().getString()
				+ " · montadora " + helper.getBlockEntity(machineAt, AssemblerMachineBlockEntity.class).job());
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack pick = chest.getItem(i);
				if (!pick.has(ModComponents.PARTS)) {
					continue;
				}
				assertPlain(helper, pick, ForgeType.PICO);
				helper.assertTrue(pick.get(ModComponents.PARTS).material(0) == ForgeMaterial.HIERRO, "con cabeza de hierro");
				helper.assertTrue((pick.getOrDefault(ModComponents.COLADAS, 0) & 1) != 0, "colada en la mesa, por el colador");
			}
			helper.assertBlockPresent(ModBlocks.COLADOR, table.above());
		});
	}
}
