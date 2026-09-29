package dev.forja.test;

import java.util.List;

import dev.forja.ai.MobActions;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Las acciones preparadas para el contrato v4 de la red (Andy, 2026-09-29): coger un arma mejor del suelo, beber y
 * lanzar pociones, comer, lanzar perlas, romper luces y subir el escudo. Nada las llama todavía; cada prueba enciende
 * {@code mobActionsV4} y la deja como estaba. Ver docs/red_mob_v4_propuesta.md.
 */
public class AccionesGameTests {
	/** A still zombie with no veteran or elite roll and an empty off hand. */
	private static Zombie zombie(GameTestHelper helper, BlockPos pos) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, pos);
		zombie.setNoAi(true);
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		return zombie;
	}

	/** Switches the actions on; returns the value to put back. */
	private static boolean enable() {
		boolean before = CombatConfig.get().mobActionsV4;
		CombatConfig.get().mobActionsV4 = true;
		return before;
	}

	private static ItemEntity drop(GameTestHelper helper, BlockPos pos, ItemStack stack) {
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(pos));
		ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
		item.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(item);
		return item;
	}

	/** A zombie with a wooden sword takes the diamond one at its feet and lets go of its own. */
	@GameTest
	public void zombiePicksUpBetterSword(GameTestHelper helper) {
		boolean before = enable();
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
		drop(helper, new BlockPos(2, 1, 1), new ItemStack(Items.DIAMOND_SWORD));
		boolean took = MobActions.pickUpBetterWeapon(zombie);
		CombatConfig.get().mobActionsV4 = before;
		helper.assertTrue(took, "el zombi debería coger la espada de diamante");
		helper.assertTrue(zombie.getMainHandItem().is(Items.DIAMOND_SWORD), "debería llevar la espada de diamante, lleva " + zombie.getMainHandItem());
		List<ItemEntity> floor = helper.getLevel().getEntitiesOfClass(ItemEntity.class, zombie.getBoundingBox().inflate(3.0));
		helper.assertTrue(floor.stream().anyMatch(e -> e.getItem().is(Items.WOODEN_SWORD)), "la espada de madera debería quedar en el suelo");
		helper.assertFalse(floor.stream().anyMatch(e -> e.getItem().is(Items.DIAMOND_SWORD)), "la espada de diamante ya no debería estar en el suelo");
		helper.assertFalse(MobActions.pickUpBetterWeapon(zombie), "la de madera es peor: no debería volver a por ella");
		floor.forEach(net.minecraft.world.entity.Entity::discard);
		zombie.discard();
		helper.succeed();
	}

	/** A vindicator (not undead, so healing heals it) drinks a healing potion from its off hand. */
	@GameTest(maxTicks = 40)
	public void mobDrinksHealingPotion(GameTestHelper helper) {
		boolean before = enable();
		var vindicator = helper.spawn(EntityTypes.VINDICATOR, new BlockPos(1, 1, 1));
		vindicator.setNoAi(true);
		vindicator.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		vindicator.setItemSlot(EquipmentSlot.OFFHAND, PotionContents.createItemStack(Items.POTION, Potions.HEALING));
		vindicator.setHealth(8.0F);
		boolean drank = MobActions.drinkPotion(vindicator);
		CombatConfig.get().mobActionsV4 = before;
		helper.assertTrue(drank, "debería beberse la poción");
		helper.assertTrue(vindicator.getOffhandItem().isEmpty(), "la poción debería gastarse");
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(vindicator.getHealth() > 8.0F, "la curación debería subirle la vida, tiene " + vindicator.getHealth());
			vindicator.discard();
			helper.succeed();
		});
	}

	/** A zombie throws the splash potion in its off hand at a player in front of it. */
	@GameTest
	public void mobThrowsSplashPotion(GameTestHelper helper) {
		boolean before = enable();
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(6, 1, 1));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.SLOWNESS));
		boolean threw = MobActions.throwSplash(zombie, player);
		CombatConfig.get().mobActionsV4 = before;
		helper.assertTrue(threw, "debería lanzar la poción");
		helper.assertTrue(zombie.getOffhandItem().isEmpty(), "la poción debería salir de la mano");
		var potions = helper.getLevel().getEntitiesOfClass(ThrownSplashPotion.class, zombie.getBoundingBox().inflate(4.0));
		helper.assertTrue(!potions.isEmpty(), "debería haber una poción en el aire");
		potions.forEach(net.minecraft.world.entity.Entity::discard);
		zombie.discard();
		helper.succeed();
	}

	/** A zombie eats the bread in its off hand only when it is below half its health, and heals the bread's 5. */
	@GameTest
	public void mobEatsBreadWhenHurt(GameTestHelper helper) {
		boolean before = enable();
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BREAD, 2));
		boolean ateWhole = MobActions.eat(zombie);
		zombie.setHealth(zombie.getMaxHealth() * 0.3F);
		float hurt = zombie.getHealth();
		boolean ateHurt = MobActions.eat(zombie);
		CombatConfig.get().mobActionsV4 = before;
		helper.assertFalse(ateWhole, "con la vida entera no debería comer");
		helper.assertTrue(ateHurt, "herido debería comer");
		helper.assertTrue(Math.abs(zombie.getHealth() - (hurt + 5.0F)) < 0.01F, "el pan cura 5, tiene " + zombie.getHealth() + " de " + hurt);
		helper.assertTrue(zombie.getOffhandItem().getCount() == 1, "debería quedar un pan");
		zombie.discard();
		helper.succeed();
	}

	/** A pearl thrown from the off hand carries its mob across: vanilla's pearl moves any owner, not just players. */
	@GameTest(maxTicks = 100)
	public void pearlMovesTheMob(GameTestHelper helper) {
		boolean before = enable();
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		Vec3 start = zombie.position();
		helper.assertFalse(MobActions.throwPearl(zombie, helper.absoluteVec(new Vec3(6.5, 1.0, 1.5))), "sin perla no debería lanzar nada");
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ENDER_PEARL));
		boolean threw = MobActions.throwPearl(zombie, helper.absoluteVec(new Vec3(6.5, 1.0, 1.5)));
		CombatConfig.get().mobActionsV4 = before;
		helper.assertTrue(threw, "debería lanzar la perla");
		helper.assertTrue(zombie.getOffhandItem().isEmpty(), "la perla debería gastarse");
		helper.runAfterDelay(60, () -> {
			double moved = new Vec3(zombie.getX() - start.x, 0.0, zombie.getZ() - start.z).length();
			helper.assertTrue(moved > 2.0, "la perla debería llevar al zombi lejos, se movió " + moved);
			zombie.discard();
			helper.succeed();
		});
	}

	/**
	 * Breaking lights is off by default (Andy: monsters never break blocks); with mobsBreakLights on, a torch within
	 * reach goes, and nearestLight finds the one nearest the player.
	 */
	@GameTest
	public void breakLightOnlyWithTheFlag(GameTestHelper helper) {
		boolean before = enable();
		boolean lightsBefore = CombatConfig.get().mobsBreakLights;
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(6, 1, 1));
		BlockPos torch = new BlockPos(2, 1, 1);
		BlockPos lantern = new BlockPos(6, 1, 3);
		helper.setBlock(torch, Blocks.TORCH);
		helper.setBlock(lantern, Blocks.LANTERN);
		BlockPos nearest = MobActions.nearestLight(zombie, player, 6);
		CombatConfig.get().mobsBreakLights = false;
		boolean brokeOff = MobActions.breakLight(zombie, helper.absolutePos(torch));
		boolean torchStayed = helper.getBlockState(torch).is(Blocks.TORCH);
		CombatConfig.get().mobsBreakLights = true;
		boolean brokeOn = MobActions.breakLight(zombie, helper.absolutePos(torch));
		boolean farLantern = MobActions.breakLight(zombie, helper.absolutePos(lantern));
		CombatConfig.get().mobsBreakLights = lightsBefore;
		CombatConfig.get().mobActionsV4 = before;
		helper.assertTrue(helper.absolutePos(lantern).equals(nearest), "la luz más cercana al jugador es el farol, dio " + nearest);
		helper.assertFalse(brokeOff, "sin la bandera no debería romper nada");
		helper.assertTrue(torchStayed, "sin la bandera la antorcha sigue");
		helper.assertTrue(brokeOn, "con la bandera debería romper la antorcha");
		helper.assertTrue(helper.getBlockState(torch).isAir(), "la antorcha debería desaparecer");
		helper.assertFalse(farLantern, "el farol está fuera de su alcance");
		helper.assertTrue(helper.getBlockState(lantern).is(Blocks.LANTERN), "el farol sigue");
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, zombie.getBoundingBox().inflate(4.0)).forEach(net.minecraft.world.entity.Entity::discard);
		zombie.discard();
		helper.succeed();
	}

	/** The shield goes up against a player drawing a bow at the zombie, and down once the bow is lowered. */
	@GameTest
	public void shieldUpAgainstADrawnBow(GameTestHelper helper) {
		boolean before = enable();
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(6, 1, 1));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		// Facing -X, straight at the zombie five blocks off (the helper's player faces +X).
		player.setYRot(90.0F);
		player.setYHeadRot(90.0F);
		player.setXRot(0.0F);
		boolean idle = MobActions.raiseShieldSmart(zombie, player);
		boolean idleUp = zombie.isUsingItem();
		player.startUsingItem(InteractionHand.MAIN_HAND);
		boolean drawn = MobActions.raiseShieldSmart(zombie, player);
		boolean drawnUp = zombie.isUsingItem() && zombie.getUsedItemHand() == InteractionHand.OFF_HAND;
		player.stopUsingItem();
		boolean lowered = MobActions.raiseShieldSmart(zombie, player);
		boolean loweredUp = zombie.isUsingItem();
		CombatConfig.get().mobActionsV4 = before;
		helper.assertFalse(idle || idleUp, "sin arco tensado el escudo sigue abajo");
		helper.assertTrue(drawn && drawnUp, "con el arco tensado apuntándole debería subir el escudo");
		helper.assertFalse(lowered || loweredUp, "al bajar el arco debería bajar el escudo");
		zombie.discard();
		helper.succeed();
	}
}
