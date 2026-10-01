package dev.forja.test;

import java.util.List;

import dev.forja.entity.ThrownHead;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.registry.ModEntities;
import dev.forja.upgrade.ThrowReturns;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import dev.forja.upgrade.WeaponThrow;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown trident (2026-09-30). Andy threw it, it hit the ground, "broke" and lay there as an item,
 * and it was also back in his inventory: two tridents from one. The flight's end dropped a copy of the
 * weapon and then handed the original back as well. Now a thrown weapon is in exactly one place when
 * its flight ends, and the trident always comes home, whatever ends the flight.
 */
public class TridenteGameTests {
	/** Where each throw aims, in the test's own coordinates: the floor ahead, the wall ahead, the sky. */
	private static final Vec3 FLOOR = new Vec3(3.5, 0.5, 1.5);
	private static final Vec3 WALL = new Vec3(5.0, 2.6, 1.5);
	private static final Vec3 SKY = new Vec3(1.5, 40.0, 1.5);

	/**
	 * A forged weapon with wear, an enchantment and an upgrade, so that losing any of them shows. Named
	 * after its test, so the copies of a test run side by side (FORJA_VERIFICAR) never count each other's.
	 */
	private static ItemStack worn(GameTestHelper helper, ForgeType type) {
		ItemStack stack = Assembler.create(type, Assembler.defaultMaterials(type));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("prueba " + helper.absolutePos(BlockPos.ZERO).toShortString()));
		stack.setDamageValue(7);
		stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 2);
		if (type == ForgeType.TRIDENTE) {
			Upgrades.with(stack, Upgrade.CORRIENTE, 40);
		}
		return stack;
	}

	/** A crouching player holding the weapon and looking at a point of the test (whichever way it is turned). */
	private static CombatGameTests.TestPlayer thrower(GameTestHelper helper, ItemStack weapon, Vec3 target) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		player.getInventory().setSelectedSlot(0);
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
		player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(target));
		player.setShiftKeyDown(true);
		return player;
	}

	private static void throwIt(GameTestHelper helper, CombatGameTests.TestPlayer player, ForgeType type) {
		InteractionResult result = WeaponThrow.tryThrowWeapon(helper.getLevel(), player, InteractionHand.MAIN_HAND, type);
		helper.assertTrue(result == InteractionResult.SUCCESS, "no se lanzó: " + result);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "la mano debería quedarse vacía al lanzar");
		helper.assertTrue(flying(helper, player).size() == 1, "debería haber un arma en el aire");
	}

	private static List<? extends ThrownHead> flying(GameTestHelper helper, CombatGameTests.TestPlayer player) {
		return helper.getLevel().getEntities(ModEntities.THROWN_HEAD, head -> player.getUUID().equals(head.ownerId()) && head.isAlive());
	}

	/** Copies of this exact weapon in the player's inventory. */
	private static int inInventory(CombatGameTests.TestPlayer player, ItemStack weapon) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (ItemStack.matches(player.getInventory().getItem(slot), weapon)) {
				count++;
			}
		}
		return count;
	}

	/** Copies of this exact weapon lying on the ground anywhere near the test. */
	private static int onGround(GameTestHelper helper, ItemStack weapon) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(64.0),
			item -> item.isAlive() && ItemStack.matches(item.getItem(), weapon)).size();
	}

	private static void floorAndWall(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 2; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		for (int y = 1; y <= 3; y++) {
			for (int z = 0; z <= 2; z++) {
				helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
			}
		}
	}

	/** Thrown down at the floor: it hits, comes back, and there is one trident, in the inventory, unchanged. */
	@GameTest(maxTicks = 200)
	public void tridentHittingTheGroundComesBackOnce(GameTestHelper helper) {
		floorAndWall(helper);
		ItemStack trident = worn(helper, ForgeType.TRIDENTE);
		CombatGameTests.TestPlayer player = thrower(helper, trident, FLOOR);
		throwIt(helper, player, ForgeType.TRIDENTE);
		helper.succeedWhen(() -> {
			helper.assertTrue(flying(helper, player).isEmpty(), "el tridente sigue en el aire");
			helper.assertTrue(onGround(helper, trident) == 0, "el tridente quedó tirado en el suelo");
			helper.assertTrue(inInventory(player, trident) == 1, "debería haber exactamente un tridente igual al lanzado en el inventario: "
				+ inInventory(player, trident));
			helper.assertTrue(ItemStack.matches(player.getMainHandItem(), trident), "debería volver a la mano, con su desgaste, encantamientos, piezas y mejoras");
			helper.assertTrue(ThrowReturns.heldFor(helper.getLevel().getServer(), player.getUUID()).isEmpty(), "no debería quedar nada en espera");
		});
	}

	/** Thrown at the open sky it reaches its full range and turns back on its own. */
	@GameTest(maxTicks = 240)
	public void tridentAtFullRangeComesBack(GameTestHelper helper) {
		ItemStack trident = worn(helper, ForgeType.TRIDENTE);
		CombatGameTests.TestPlayer player = thrower(helper, trident, SKY);
		throwIt(helper, player, ForgeType.TRIDENTE);
		helper.runAfterDelay(20, () -> helper.assertTrue(flying(helper, player).size() == 1, "a los 20 ticks debería seguir volando hacia arriba"));
		helper.succeedWhen(() -> {
			helper.assertTrue(flying(helper, player).isEmpty(), "el tridente sigue en el aire");
			helper.assertTrue(onGround(helper, trident) == 0, "el tridente quedó tirado");
			helper.assertTrue(inInventory(player, trident) == 1, "no volvió exactamente un tridente: " + inInventory(player, trident));
		});
	}

	/** Gone below the world mid-flight, it is still not lost: it comes straight back to the hand. */
	@GameTest(maxTicks = 100)
	public void tridentInTheVoidComesBack(GameTestHelper helper) {
		ItemStack trident = worn(helper, ForgeType.TRIDENTE);
		CombatGameTests.TestPlayer player = thrower(helper, trident, SKY);
		throwIt(helper, player, ForgeType.TRIDENTE);
		helper.runAfterDelay(2, () -> {
			for (ThrownHead head : flying(helper, player)) {
				head.setPos(head.getX(), helper.getLevel().getMinY() - 100.0, head.getZ());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(flying(helper, player).isEmpty(), "el tridente sigue existiendo bajo el mundo");
			helper.assertTrue(onGround(helper, trident) == 0, "el tridente quedó tirado");
			helper.assertTrue(inInventory(player, trident) == 1, "el tridente se perdió en el vacío: " + inInventory(player, trident));
		});
	}

	/** Back to a full inventory: it is kept for the thrower, not dropped and not doubled, and arrives once there is room. */
	@GameTest(maxTicks = 200)
	public void tridentWaitsForAFullInventory(GameTestHelper helper) {
		floorAndWall(helper);
		ItemStack trident = worn(helper, ForgeType.TRIDENTE);
		CombatGameTests.TestPlayer player = thrower(helper, trident, WALL);
		throwIt(helper, player, ForgeType.TRIDENTE);
		// Everything full while it flies, the hand it left included.
		for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
			player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(flying(helper, player).isEmpty(), "el tridente sigue en el aire");
			helper.assertTrue(onGround(helper, trident) == 0, "con el inventario lleno lo tiró al suelo");
			helper.assertTrue(inInventory(player, trident) == 0, "no cabía, no debería estar en el inventario");
			List<ItemStack> held = ThrowReturns.heldFor(helper.getLevel().getServer(), player.getUUID());
			helper.assertTrue(held.size() == 1 && ItemStack.matches(held.getFirst(), trident), "debería esperar exactamente un tridente intacto: " + held);
			// Room again: it arrives, once, and nothing is left waiting.
			player.getInventory().setItem(5, ItemStack.EMPTY);
			helper.assertTrue(ThrowReturns.deliver(helper.getLevel().getServer(), player) == 1, "no se entregó al hacer hueco");
			helper.assertTrue(inInventory(player, trident) == 1, "al hacer hueco debería llegar un tridente: " + inInventory(player, trident));
			helper.assertTrue(ThrowReturns.heldFor(helper.getLevel().getServer(), player.getUUID()).isEmpty(), "sigue en espera tras entregarse");
			helper.assertTrue(ThrowReturns.deliver(helper.getLevel().getServer(), player) == 0, "se entregó dos veces");
			helper.assertTrue(inInventory(player, trident) == 1, "hay más de un tridente");
		});
	}

	/** The thrower dies mid-flight: nothing drops and nothing doubles; it waits and comes back once they live again. */
	@GameTest(maxTicks = 200)
	public void throwerDyingMidFlightDuplicatesNothing(GameTestHelper helper) {
		ItemStack trident = worn(helper, ForgeType.TRIDENTE);
		CombatGameTests.TestPlayer player = thrower(helper, trident, SKY);
		throwIt(helper, player, ForgeType.TRIDENTE);
		helper.runAfterDelay(5, () -> player.setHealth(0.0F));
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(flying(helper, player).isEmpty(), "con el dueño muerto el tridente debería dejar de volar");
			helper.assertTrue(onGround(helper, trident) == 0, "el tridente cayó al suelo al morir el dueño");
			helper.assertTrue(inInventory(player, trident) == 0, "un muerto no recoge nada");
			List<ItemStack> held = ThrowReturns.heldFor(helper.getLevel().getServer(), player.getUUID());
			helper.assertTrue(held.size() == 1 && ItemStack.matches(held.getFirst(), trident), "debería esperar un tridente: " + held);
			player.setHealth(player.getMaxHealth());
			helper.assertTrue(ThrowReturns.deliver(helper.getLevel().getServer(), player) == 1, "no se entregó al revivir");
			helper.assertTrue(inInventory(player, trident) == 1, "al revivir debería tener exactamente un tridente");
			helper.assertTrue(onGround(helper, trident) == 0, "y ninguno en el suelo");
			helper.succeed();
		});
	}

	/**
	 * The root of the duplication: a weapon that does not come back and hits a wall. A dagger without
	 * Retorno does exactly that, and must lie on the ground once, not also be in the inventory.
	 */
	@GameTest(maxTicks = 100)
	public void daggerWithoutReturnLandsOnceOnTheGround(GameTestHelper helper) {
		floorAndWall(helper);
		ItemStack dagger = worn(helper, ForgeType.DAGA);
		CombatGameTests.TestPlayer player = thrower(helper, dagger, WALL);
		throwIt(helper, player, ForgeType.DAGA);
		helper.succeedWhen(() -> {
			helper.assertTrue(flying(helper, player).isEmpty(), "la daga sigue en el aire");
			helper.assertTrue(onGround(helper, dagger) == 1, "debería haber exactamente una daga en el suelo: " + onGround(helper, dagger));
			helper.assertTrue(inInventory(player, dagger) == 0, "y ninguna de vuelta en el inventario (era el duplicado)");
		});
	}

	/** What waits for a thrower is saved with the world: the forged trident survives the trip through the codec whole. */
	@GameTest
	public void heldWeaponsSurviveASave(GameTestHelper helper) {
		ItemStack trident = worn(helper, ForgeType.TRIDENTE);
		var codec = com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, ItemStack.CODEC.listOf());
		var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
		var saved = codec.encodeStart(ops, java.util.Map.of("dueño", List.of(trident))).getOrThrow();
		var loaded = codec.parse(ops, saved).getOrThrow();
		helper.assertTrue(ItemStack.matches(loaded.get("dueño").getFirst(), trident), "el tridente guardado no vuelve igual: " + loaded);
		helper.succeed();
	}

	/** Pointed weapons fly tip first; axes and tool heads keep tumbling. */
	@GameTest
	public void pointedWeaponsFlyPointFirst(GameTestHelper helper) {
		helper.assertTrue(WeaponThrow.fliesPointFirst(worn(helper, ForgeType.TRIDENTE)), "el tridente vuela de punta");
		helper.assertTrue(WeaponThrow.fliesPointFirst(worn(helper, ForgeType.DAGA)), "la daga vuela de punta");
		helper.assertTrue(!WeaponThrow.fliesPointFirst(worn(helper, ForgeType.HACHA)), "el hacha sigue girando");
		helper.assertTrue(WeaponThrow.alwaysReturns(ForgeType.TRIDENTE) && !WeaponThrow.alwaysReturns(ForgeType.DAGA), "solo el tridente vuelve siempre");
		ItemStack trident = worn(helper, ForgeType.TRIDENTE);
		CombatGameTests.TestPlayer player = thrower(helper, trident, WALL);
		throwIt(helper, player, ForgeType.TRIDENTE);
		ThrownHead head = flying(helper, player).getFirst();
		helper.assertTrue(head.isPointFirst(), "el tridente lanzado debería marcarse para volar de punta");
		// Aimed the arrow way: the yaw and pitch of its own motion.
		Vec3 motion = head.getDeltaMovement();
		float yaw = (float) Math.toDegrees(Math.atan2(motion.x, motion.z));
		float pitch = (float) Math.toDegrees(Math.atan2(motion.y, motion.horizontalDistance()));
		helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(head.getYRot() - yaw)) < 1.0F && Math.abs(head.getXRot() - pitch) < 1.0F,
			"debería apuntar hacia donde vuela: " + head.getYRot() + " / " + head.getXRot() + " frente a " + yaw + " / " + pitch);
		head.discard();
		helper.succeed();
	}
}
