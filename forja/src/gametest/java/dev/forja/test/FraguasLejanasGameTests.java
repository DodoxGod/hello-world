package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import dev.forja.Forja;
import dev.forja.block.FarForgeBlock;
import dev.forja.block.entity.CrucibleBlockEntity;
import dev.forja.block.entity.FarForgeBlockEntity;
import dev.forja.block.entity.MeltTankBlockEntity;
import dev.forja.forge.Alloys;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModEffects;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.TraitEffects;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The far forges and their alloys (docs/ALEACIONES_NETHER_END.md): each forge lights with its own item and wakes
 * its guards, makes its alloys with fuel and only in its own dimension, nothing else makes them, every part takes
 * them, their traits do what they say, and the ruins carry the forge, the guards and the note.
 */
public class FraguasLejanasGameTests {
	/** Where in the Nether these tests work: far from spawn and from each other, at a height the Nether has room at. */
	private static final BlockPos NETHER_SPOT = new BlockPos(4100, 70, 4100);

	private static BlockHitResult hit(BlockPos pos) {
		return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
	}

	/** A floor and air above it, so a forge, a chest under it and its guards have somewhere to be. */
	private static void clear(ServerLevel level, BlockPos centre, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				level.setBlockAndUpdate(centre.offset(dx, -2, dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				for (int dy = -1; dy <= 3; dy++) {
					level.setBlockAndUpdate(centre.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private static void fill(FarForgeBlockEntity forge, Alloys.Recipe recipe, int batches) {
		for (Alloys.Part part : recipe.inputs()) {
			forge.insert(new ItemStack(part.item().get(), part.count() * batches));
		}
	}

	private static Alloys.Recipe recipe(String id) {
		return Alloys.ALL.stream().filter(recipe -> recipe.id().equals(id)).findFirst().orElseThrow();
	}

	/** Runs the forge's own tick this many times: a far chunk does not tick on its own in a test. */
	private static void tick(ServerLevel level, BlockPos pos, int ticks) {
		for (int i = 0; i < ticks; i++) {
			BlockState state = level.getBlockState(pos);
			if (level.getBlockEntity(pos) instanceof FarForgeBlockEntity forge) {
				FarForgeBlockEntity.serverTick(level, pos, state, forge);
			}
		}
	}

	private static int count(Container container, net.minecraft.world.item.Item item) {
		int found = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).is(item)) {
				found += container.getItem(slot).getCount();
			}
		}
		return found;
	}

	/**
	 * The soul forge is found cold: a stick does nothing, a blaze rod lights it and is spent, every monster near it
	 * turns on whoever lit it, and two wisps rise.
	 */
	@GameTest(maxTicks = 40)
	public void theSoulForgeLightsWithABlazeRodAndWakesItsGuards(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos forge = helper.absolutePos(new BlockPos(4, 2, 4));
		clear(level, forge, 4);
		level.setBlockAndUpdate(forge, ModBlocks.FRAGUA_DE_ALMAS.defaultBlockState());
		// A survival player on Normal: monsters do not turn on a creative one, and Peaceful has none to turn.
		ServerPlayer player = CombatGameTests.player(helper, new BlockPos(4, 2, 1));
		Mob zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(7, 2, 4));
		zombie.setNoAi(true);
		int wisps = level.getEntitiesOfClass(dev.forja.entity.EmberWisp.class, new AABB(forge).inflate(8)).size();

		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		level.getBlockState(forge).useItemOn(player.getMainHandItem(), level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit(forge));
		helper.assertFalse(level.getBlockState(forge).getValue(FarForgeBlock.LIT), "un palo no enciende la fragua de almas");

		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BLAZE_ROD, 2));
		level.getBlockState(forge).useItemOn(player.getMainHandItem(), level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit(forge));
		helper.assertTrue(level.getBlockState(forge).getValue(FarForgeBlock.LIT), "una vara de blaze la enciende");
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "y se gasta una: quedan " + player.getMainHandItem().getCount());
		helper.assertTrue(zombie.getTarget() == player, "al encenderla, los monstruos de alrededor van a por quien la encendió: "
			+ zombie.getTarget());
		int risen = level.getEntitiesOfClass(dev.forja.entity.EmberWisp.class, new AABB(forge).inflate(8)).size() - wisps;
		helper.assertTrue(risen == FarForgeBlock.Kind.ALMAS.risen, "se levantan " + FarForgeBlock.Kind.ALMAS.risen + " pavesas, hay " + risen);
		for (dev.forja.entity.EmberWisp wisp : level.getEntitiesOfClass(dev.forja.entity.EmberWisp.class, new AABB(forge).inflate(8))) {
			helper.assertTrue(wisp.getTarget() == player, "y van a por él");
			wisp.discard();
		}
		zombie.discard();
		helper.succeed();
	}

	/**
	 * Lit and in the Nether, a whole recipe and one blaze powder make one batch of wispfire in ten seconds, into the
	 * chest under it. Without fuel it waits. The same forge lit in the Overworld makes nothing.
	 */
	@GameTest(maxTicks = 40)
	public void theSoulForgeMakesWispfireInTheNetherOnly(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		BlockPos pos = NETHER_SPOT;
		clear(nether, pos, 2);
		nether.setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState());
		nether.setBlockAndUpdate(pos, ModBlocks.FRAGUA_DE_ALMAS.defaultBlockState().setValue(FarForgeBlock.LIT, true));
		FarForgeBlockEntity forge = (FarForgeBlockEntity) nether.getBlockEntity(pos);
		Alloys.Recipe wispfire = recipe("fatuo");
		fill(forge, wispfire, 1);
		helper.assertTrue(forge.ready() == wispfire, "con la receta entera en el hogar, está lista para el fatuo");
		tick(nether, pos, FarForgeBlockEntity.BATCH_TICKS + 5);
		Container chest = (Container) nether.getBlockEntity(pos.below());
		helper.assertTrue(count(chest, ModItems.alloy("fatuo")) == 0, "sin combustible no funde");
		forge.addFuel(1);
		tick(nether, pos, FarForgeBlockEntity.BATCH_TICKS - 1);
		helper.assertTrue(count(chest, ModItems.alloy("fatuo")) == 0, "la tanda tarda " + FarForgeBlockEntity.BATCH_TICKS + " ticks");
		tick(nether, pos, 1);
		helper.assertTrue(count(chest, ModItems.alloy("fatuo")) == wispfire.output(),
			"una tanda da " + wispfire.output() + " lingotes de fatuo en el cofre de abajo, hay " + count(chest, ModItems.alloy("fatuo")));
		helper.assertTrue(forge.fuel() == 0 && forge.hearth().isEmpty(), "y gasta el combustible y la receta: " + forge.fuel() + ", " + forge.hearth());
		nether.setBlockAndUpdate(pos.below(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

		// The same forge, lit, in the Overworld: it burns blue and makes nothing.
		ServerLevel overworld = helper.getLevel();
		BlockPos here = helper.absolutePos(new BlockPos(3, 2, 3));
		overworld.setBlockAndUpdate(here, ModBlocks.FRAGUA_DE_ALMAS.defaultBlockState().setValue(FarForgeBlock.LIT, true));
		FarForgeBlockEntity away = (FarForgeBlockEntity) overworld.getBlockEntity(here);
		fill(away, wispfire, 1);
		away.addFuel(1);
		tick(overworld, here, FarForgeBlockEntity.BATCH_TICKS + 5);
		helper.assertTrue(away.fuel() == 1 && away.progress() == 0, "fuera del Nether no funde: combustible " + away.fuel());
		helper.assertTrue(overworld.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(here).inflate(2)).isEmpty(),
			"ni suelta nada");
		helper.succeed();
	}

	/** Magmasteel comes out of the same soul forge, from its own four ingredients, with the same fuel. */
	@GameTest(maxTicks = 40)
	public void theSoulForgeMakesMagmasteelToo(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		BlockPos pos = NETHER_SPOT.offset(16, 0, 0);
		clear(nether, pos, 2);
		nether.setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState());
		nether.setBlockAndUpdate(pos, ModBlocks.FRAGUA_DE_ALMAS.defaultBlockState().setValue(FarForgeBlock.LIT, true));
		FarForgeBlockEntity forge = (FarForgeBlockEntity) nether.getBlockEntity(pos);
		Alloys.Recipe magmasteel = recipe("magmacero");
		fill(forge, magmasteel, 2);
		forge.addFuel(2);
		tick(nether, pos, FarForgeBlockEntity.BATCH_TICKS * 2);
		Container chest = (Container) nether.getBlockEntity(pos.below());
		int made = count(chest, ModItems.alloy("magmacero"));
		helper.assertTrue(made == magmasteel.output() * 2, "dos tandas, " + magmasteel.output() * 2 + " lingotes de magmacero; hay " + made);
		helper.assertTrue(forge.fuel() == 0 && forge.hearth().isEmpty(), "y no queda nada: " + forge.fuel() + ", " + forge.hearth());
		nether.setBlockAndUpdate(pos.below(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	/**
	 * Volcánico: in magmasteel boots the lava round your feet cools into crust you can stand on (more pieces, wider),
	 * and with nobody on it the crust goes back to lava.
	 */
	@GameTest(maxTicks = 40)
	public void magmasteelCoolsTheLavaUnderfoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos centre = helper.absolutePos(new BlockPos(4, 1, 4));
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				level.setBlockAndUpdate(centre.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(centre.offset(dx, 0, dz), Blocks.LAVA.defaultBlockState());
				level.setBlockAndUpdate(centre.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(centre.offset(dx, 2, dz), Blocks.AIR.defaultBlockState());
			}
		}
		ServerPlayer player = CombatGameTests.player(helper, new BlockPos(4, 2, 4));
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,
			Assembler.create(ForgeType.BOTAS, List.of(ForgeMaterial.MAGMACERO, ForgeMaterial.CUERO), level.registryAccess()));
		int pieces = TraitEffects.armorPieces(player, ForgeMaterial.Trait.VOLCANICO);
		helper.assertTrue(pieces == 1, "las botas de magmacero son volcánicas: " + pieces);
		int one = TraitEffects.volcanicStep(level, player, pieces);
		helper.assertTrue(level.getBlockState(centre).is(ModBlocks.COSTRA_DE_MAGMA), "la lava bajo los pies se hace costra");
		helper.assertTrue(one >= 5 && one <= 9, "con una pieza, un bloque alrededor: " + one);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
			Assembler.create(ForgeType.CASCO, List.of(ForgeMaterial.MAGMACERO, ForgeMaterial.CUERO), level.registryAccess()));
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
			Assembler.create(ForgeType.PECHERA, List.of(ForgeMaterial.MAGMACERO, ForgeMaterial.CUERO), level.registryAccess()));
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,
			Assembler.create(ForgeType.GREBAS, List.of(ForgeMaterial.MAGMACERO, ForgeMaterial.CUERO), level.registryAccess()));
		int more = TraitEffects.volcanicStep(level, player, TraitEffects.armorPieces(player, ForgeMaterial.Trait.VOLCANICO));
		helper.assertTrue(level.getBlockState(centre.offset(3, 0, 0)).is(ModBlocks.COSTRA_DE_MAGMA) && more > one,
			"con las cuatro, tres bloques: " + more);
		// Nobody on the far one: its tick sends it back to lava. The one under the player holds.
		BlockPos far = centre.offset(3, 0, 0);
		level.getBlockState(far).tick(level, far, level.getRandom());
		helper.assertTrue(level.getBlockState(far).is(Blocks.LAVA), "sin nadie encima vuelve a ser lava");
		// The test player is not in the world, so something that is stands where he was.
		var standing = helper.spawn(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, new BlockPos(4, 2, 4));
		level.getBlockState(centre).tick(level, centre, level.getRandom());
		helper.assertTrue(level.getBlockState(centre).is(ModBlocks.COSTRA_DE_MAGMA), "con alguien encima aguanta");
		standing.discard();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-4, 0, -4), centre.offset(4, 0, 4))) {
			level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		}
		helper.succeed();
	}

	/** A magmasteel pick cuts netherrack and blackstone half again as fast as a plain one of the same speed would. */
	@GameTest(maxTicks = 20)
	public void magmasteelPicksCutNetherStone(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		ItemStack magma = Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.MAGMACERO, ForgeMaterial.MAGMACERO, ForgeMaterial.MAGMACERO), registries);
		float stone = magma.getDestroySpeed(Blocks.STONE.defaultBlockState());
		float netherrack = magma.getDestroySpeed(Blocks.NETHERRACK.defaultBlockState());
		float blackstone = magma.getDestroySpeed(Blocks.BLACKSTONE.defaultBlockState());
		helper.assertTrue(Math.abs(netherrack - stone * dev.forja.forge.Assembler.VOLCANIC_MINING) < 0.01F
			&& Math.abs(blackstone - netherrack) < 0.01F, "piedra " + stone + ", netherrack " + netherrack + ", piedra negra " + blackstone);
		ItemStack fatuo = Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.FATUO, ForgeMaterial.FATUO, ForgeMaterial.FATUO), registries);
		helper.assertTrue(Math.abs(fatuo.getDestroySpeed(Blocks.NETHERRACK.defaultBlockState()) - fatuo.getDestroySpeed(Blocks.STONE.defaultBlockState())) < 0.01F,
			"un pico que no es volcánico pica igual el netherrack que la piedra");
		helper.assertTrue(magma.isCorrectToolForDrops(Blocks.BLACKSTONE.defaultBlockState()), "y la piedra negra sigue cayendo");
		helper.succeed();
	}

	/** The void forge lights with an eye of ender (and not with a blaze rod), and two empty suits stand up beside it. */
	@GameTest(maxTicks = 40)
	public void theVoidForgeLightsWithAnEnderEye(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos forge = helper.absolutePos(new BlockPos(4, 2, 4));
		clear(level, forge, 4);
		level.setBlockAndUpdate(forge, ModBlocks.FRAGUA_DEL_VACIO.defaultBlockState());
		ServerPlayer player = CombatGameTests.player(helper, new BlockPos(4, 2, 1));
		int suits = level.getEntitiesOfClass(dev.forja.entity.HollowArmor.class, new AABB(forge).inflate(8)).size();
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BLAZE_ROD));
		level.getBlockState(forge).useItemOn(player.getMainHandItem(), level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit(forge));
		helper.assertFalse(level.getBlockState(forge).getValue(FarForgeBlock.LIT), "una vara de blaze no enciende la fragua del vacío");
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE));
		level.getBlockState(forge).useItemOn(player.getMainHandItem(), level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit(forge));
		helper.assertTrue(level.getBlockState(forge).getValue(FarForgeBlock.LIT), "un ojo de ender la enciende");
		var risen = level.getEntitiesOfClass(dev.forja.entity.HollowArmor.class, new AABB(forge).inflate(8));
		helper.assertTrue(risen.size() - suits == FarForgeBlock.Kind.VACIO.risen, "se levantan dos corazas vacías, hay " + (risen.size() - suits));
		risen.forEach(net.minecraft.world.entity.Entity::discard);
		helper.succeed();
	}

	/** Lit in the End it makes aetherium with ender pearls; it will not take the soul forge's soul soil, and away from the End it makes nothing. */
	@GameTest(maxTicks = 40)
	public void theVoidForgeMakesAetheriumInTheEndOnly(GameTestHelper helper) {
		ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
		BlockPos pos = new BlockPos(4100, 70, 4100);
		clear(end, pos, 2);
		end.setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState());
		end.setBlockAndUpdate(pos, ModBlocks.FRAGUA_DEL_VACIO.defaultBlockState().setValue(FarForgeBlock.LIT, true));
		FarForgeBlockEntity forge = (FarForgeBlockEntity) end.getBlockEntity(pos);
		helper.assertFalse(forge.takes(new ItemStack(Items.SOUL_SOIL)), "la fragua del vacío no quiere tierra de almas");
		Alloys.Recipe aetherium = recipe("eterio");
		fill(forge, aetherium, 1);
		forge.addFuel(1);
		tick(end, pos, FarForgeBlockEntity.BATCH_TICKS);
		Container chest = (Container) end.getBlockEntity(pos.below());
		helper.assertTrue(count(chest, ModItems.alloy("eterio")) == aetherium.output(), "una tanda de eterio en el End: " + count(chest, ModItems.alloy("eterio")));
		end.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
		end.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		BlockPos there = NETHER_SPOT.offset(0, 0, 16);
		clear(nether, there, 1);
		nether.setBlockAndUpdate(there, ModBlocks.FRAGUA_DEL_VACIO.defaultBlockState().setValue(FarForgeBlock.LIT, true));
		FarForgeBlockEntity away = (FarForgeBlockEntity) nether.getBlockEntity(there);
		fill(away, aetherium, 1);
		away.addFuel(1);
		tick(nether, there, FarForgeBlockEntity.BATCH_TICKS + 5);
		helper.assertTrue(away.fuel() == 1, "en el Nether la fragua del vacío no funde");
		nether.setBlockAndUpdate(there, Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	/** Flotante: an aetherium blade lifts what it strikes, not again for a while, and never a boss. */
	@GameTest(maxTicks = 20)
	public void aetheriumLiftsWhatItStrikes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(3, 2, 3));
		zombie.setNoAi(true);
		ServerPlayer player = CombatGameTests.player(helper, new BlockPos(1, 2, 3));
		ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.ETERIO, ForgeMaterial.ETERIO, ForgeMaterial.ETERIO),
			level.registryAccess());
		TraitEffects.onHit(level, player, zombie, level.damageSources().playerAttack(player), sword);
		helper.assertTrue(zombie.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION), "el golpe de eterio lo levanta");
		zombie.removeAllEffects();
		helper.assertFalse(TraitEffects.lift(level, zombie, player), "y no otra vez enseguida");
		Mob wither = helper.spawn(net.minecraft.world.entity.EntityTypes.WITHER, new BlockPos(5, 2, 5));
		wither.setNoAi(true);
		helper.assertFalse(TraitEffects.lift(level, wither, player), "a un jefe no lo levanta");
		wither.discard();
		zombie.discard();
		helper.succeed();
	}

	/** Flotante armour: fallen into the void, you are back on the last firm ground, falling slowly, and the pieces pay for it. */
	@GameTest(maxTicks = 20)
	public void theVoidHandsBackAnAetheriumWearer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlockAndUpdate(ground.below(), Blocks.STONE.defaultBlockState());
		player.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
		player.setOnGround(true);
		ItemStack boots = Assembler.create(ForgeType.BOTAS, List.of(ForgeMaterial.ETERIO, ForgeMaterial.CUERO), level.registryAccess());
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
		TraitEffects.rememberGround(level, player);
		player.teleportTo(ground.getX() + 0.5, level.getMinY() - TraitEffects.VOID_DEPTH - 4, ground.getZ() + 0.5);
		helper.assertTrue(TraitEffects.voidRescue(level, player), "el vacío lo devuelve");
		helper.assertTrue(player.blockPosition().closerThan(ground, 1.5), "al último suelo firme: " + player.blockPosition() + " en vez de " + ground);
		helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING), "con caída lenta");
		helper.assertTrue(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).getDamageValue() > 0, "y las botas lo pagan");
		player.teleportTo(ground.getX() + 0.5, level.getMinY() - TraitEffects.VOID_DEPTH - 4, ground.getZ() + 0.5);
		helper.assertFalse(TraitEffects.voidRescue(level, player), "no otra vez enseguida");
		player.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
		helper.succeed();
	}

	/** The End ruin carries the cold void forge, its three guards and the chest with the note, and generates on the outer islands. */
	@GameTest(maxTicks = 20)
	public void theVoidRuinCarriesItsForge(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		StructureTemplate template = server.getStructureManager().get(Forja.id("fragua_del_vacio/fragua")).orElseThrow();
		var forges = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), ModBlocks.FRAGUA_DEL_VACIO);
		helper.assertTrue(forges.size() == 1 && !forges.get(0).state().getValue(FarForgeBlock.LIT), "una fragua del vacío fría: " + forges.size());
		boolean note = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST).stream()
			.anyMatch(info -> info.nbt() != null && info.nbt().toString().contains("forja:chests/fragua_del_vacio"));
		helper.assertTrue(note, "un cofre con el botín de la fragua del vacío");
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
		AABB area = new AABB(origin).expandTowards(11, 8, 11);
		int suits = level.getEntitiesOfClass(dev.forja.entity.HollowArmor.class, area).size();
		int shulkers = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Shulker.class, area).size();
		helper.assertTrue(suits == 2 && shulkers == 1, "dos corazas y un shulker: " + suits + ", " + shulkers);
		level.getEntitiesOfClass(Mob.class, area).forEach(net.minecraft.world.entity.Entity::discard);
		var structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var ruin = structures.get(ResourceKey.create(Registries.STRUCTURE, Forja.id("fragua_del_vacio")));
		helper.assertTrue(ruin.isPresent(), "la estructura forja:fragua_del_vacio existe");
		var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
		helper.assertTrue(ruin.get().value().biomes().contains(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.END_HIGHLANDS)),
			"y sale en las tierras altas del End");
		helper.assertFalse(ruin.get().value().biomes().contains(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.THE_END)),
			"y no en la isla del dragón");
		helper.succeed();
	}

	/** The End ruin's chest always holds the note and an eye of ender to light its forge. */
	@GameTest(maxTicks = 20)
	public void theVoidRuinChestHoldsTheNote(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootTable table = level.getServer().reloadableRegistries().getLootTable(
			ResourceKey.create(Registries.LOOT_TABLE, Forja.id("chests/fragua_del_vacio")));
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
			.create(LootContextParamSets.CHEST);
		for (int roll = 0; roll < 5; roll++) {
			List<ItemStack> drops = table.getRandomItems(params);
			ItemStack paper = drops.stream().filter(stack -> stack.is(Items.PAPER)).findFirst().orElse(ItemStack.EMPTY);
			var lore = paper.get(net.minecraft.core.component.DataComponents.LORE);
			helper.assertTrue(lore != null && !lore.lines().isEmpty(), "siempre trae la nota con la receta: " + drops);
			helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.ENDER_EYE)), "y un ojo de ender: " + drops);
		}
		helper.succeed();
	}

	/**
	 * Nothing else makes them: not the forge table's star at any heat (Alloys.match, which the assembler uses too), not
	 * the crucible, which will not even take their soul soil, and not the tanks.
	 */
	@GameTest(maxTicks = 20)
	public void nothingElseMakesTheFarAlloys(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Alloys.Recipe recipe : Alloys.ALL) {
			if (Alloys.anywhere(recipe)) {
				continue;
			}
			List<ItemStack> inputs = new ArrayList<>();
			for (Alloys.Part part : recipe.inputs()) {
				inputs.add(new ItemStack(part.item().get(), part.count()));
			}
			for (Alloys.Heat heat : Alloys.Heat.values()) {
				if (Alloys.match(inputs, heat) != null) {
					wrong.add(recipe.id() + " sale de la estrella a " + heat.id());
				}
			}
			for (Alloys.Part part : recipe.inputs()) {
				ItemStack stack = new ItemStack(part.item().get());
				boolean elsewhere = Alloys.POURABLE.stream().filter(Alloys::anywhere)
					.anyMatch(other -> other.inputs().stream().anyMatch(input -> stack.is(input.item().get())));
				boolean material = ForgeMaterial.fromInput(stack) != null;
				// Iron and netherite scrap are metal anywhere; soul soil and chorus are the far forge's business only.
				if (!elsewhere && !material && CrucibleBlockEntity.takes(stack)) {
					wrong.add("el crisol acepta " + stack + " de " + recipe.id());
				}
				if (!elsewhere && !material && MeltTankBlockEntity.holds(stack.getItem())) {
					wrong.add("la cuba acepta " + stack + " de " + recipe.id());
				}
			}
		}
		helper.assertTrue(Alloys.ALL.stream().anyMatch(recipe -> !Alloys.anywhere(recipe)), "no hay aleaciones de fragua");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** Every far forge alloy is a material every part takes, and every item can be made all of it. */
	@GameTest(maxTicks = 20)
	public void theFarAlloysMakeEveryPart(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int parts = 0;
		int items = 0;
		for (Alloys.Recipe recipe : Alloys.ALL) {
			if (Alloys.anywhere(recipe)) {
				continue;
			}
			ForgeMaterial material = ForgeMaterial.fromInput(recipe.result());
			if (material == null) {
				wrong.add(recipe.id() + " no es un material");
				continue;
			}
			for (PartType type : PartType.values()) {
				if (!type.accepts(material)) {
					wrong.add(type.id() + " no acepta " + material.getSerializedName());
					continue;
				}
				ItemStack part = Assembler.createPart(type, material);
				if (part.isEmpty() || part.get(ModComponents.MATERIAL) != material) {
					wrong.add(type.id() + " de " + material.getSerializedName() + " sale mal: " + part);
				}
				parts++;
			}
			for (ForgeType type : ForgeType.values()) {
				List<ForgeMaterial> all = new ArrayList<>();
				for (int slot = 0; slot < type.slots.size(); slot++) {
					all.add(material);
				}
				ItemStack made = Assembler.create(type, all, helper.getLevel().registryAccess());
				ForgedParts forged = made.get(ModComponents.PARTS);
				if (made.isEmpty() || forged == null || !forged.hasTrait(material.trait)) {
					wrong.add(type.id() + " de " + material.getSerializedName() + " sale mal");
				}
				items++;
			}
		}
		helper.assertTrue(parts > 0 && items > 0, "no se probó nada");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Espectral: a wispfire blade leaves the blue fire on a blaze, which plain fire does not hurt, and it bites; a
	 * wispfire breastplate gives it to whoever strikes it.
	 */
	@GameTest(maxTicks = 80)
	public void wispfireBurnsWhatDoesNotBurn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
		Mob blaze = helper.spawn(net.minecraft.world.entity.EntityTypes.BLAZE, new BlockPos(3, 2, 3));
		blaze.setNoAi(true);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.FATUO, ForgeMaterial.FATUO, ForgeMaterial.FATUO),
			level.registryAccess());
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
		TraitEffects.onHit(level, player, blaze, level.damageSources().playerAttack(player), sword);
		helper.assertTrue(blaze.hasEffect(ModEffects.LLAMA_FATUA), "el golpe de fatuo deja la llama fatua en el blaze");
		float before = blaze.getHealth();

		Mob zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(5, 2, 3));
		zombie.setNoAi(true);
		ItemStack plate = Assembler.create(ForgeType.PECHERA, List.of(ForgeMaterial.FATUO, ForgeMaterial.CUERO), level.registryAccess());
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, plate);
		TraitEffects.onHit(level, zombie, player, level.damageSources().mobAttack(zombie), ItemStack.EMPTY);
		helper.assertTrue(zombie.hasEffect(ModEffects.LLAMA_FATUA), "la pechera de fatuo se la pasa al que le pega");
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(blaze.getHealth() < before, "y le quema aunque el fuego no le hace nada: " + before + " -> " + blaze.getHealth()
				+ ", vivo " + blaze.isAlive() + ", quitado " + blaze.isRemoved() + ", efecto " + blaze.getEffect(ModEffects.LLAMA_FATUA));
			blaze.discard();
			zombie.discard();
			helper.succeed();
		});
	}

	/** The Fragua caída carries the cold soul forge (and no dead forge), its seven guards, and the chest with the note. */
	@GameTest(maxTicks = 20)
	public void theFallenForgeCarriesTheSoulForge(GameTestHelper helper) {
		StructureTemplate template = helper.getLevel().getServer().getStructureManager().get(Forja.id("fragua_caida/fragua")).orElseThrow();
		var forges = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), ModBlocks.FRAGUA_DE_ALMAS);
		helper.assertTrue(forges.size() == 1 && !forges.get(0).state().getValue(FarForgeBlock.LIT), "una fragua de almas fría: " + forges.size());
		helper.assertTrue(template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), ModBlocks.FRAGUA_APAGADA).isEmpty(), "y ninguna fragua apagada");
		boolean note = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST).stream()
			.anyMatch(info -> info.nbt() != null && info.nbt().toString().contains("forja:chests/fragua_caida"));
		helper.assertTrue(note, "un cofre con el botín de la fragua caída");
		helper.succeed();
	}

	/** The Fragua caída's own chest always holds the note with the recipe and something to light the forge with. */
	@GameTest(maxTicks = 20)
	public void theFallenForgeChestHoldsTheNote(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootTable table = level.getServer().reloadableRegistries().getLootTable(
			ResourceKey.create(Registries.LOOT_TABLE, Forja.id("chests/fragua_caida")));
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
			.create(LootContextParamSets.CHEST);
		for (int roll = 0; roll < 5; roll++) {
			List<ItemStack> drops = table.getRandomItems(params);
			ItemStack paper = drops.stream().filter(stack -> stack.is(Items.PAPER)).findFirst().orElse(ItemStack.EMPTY);
			helper.assertFalse(paper.isEmpty(), "siempre trae la nota: " + drops);
			var lore = paper.get(net.minecraft.core.component.DataComponents.LORE);
			helper.assertTrue(lore != null && !lore.lines().isEmpty(), "y la nota lleva la receta escrita");
			helper.assertTrue(paper.getHoverName().getString().contains("fragua")
				|| paper.getHoverName().getString().contains("Forge"), "con su nombre: " + paper.getHoverName().getString());
			helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.BLAZE_ROD)), "y una vara de blaze para encenderla: " + drops);
		}
		helper.succeed();
	}
}
