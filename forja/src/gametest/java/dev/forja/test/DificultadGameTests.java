package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import dev.forja.Forja;
import dev.forja.ai.Aggression;
import dev.forja.ai.Decision;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.MobSprint;
import dev.forja.ai.Tactic;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.GearScore;
import dev.forja.difficulty.Pressure;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Andy's difficulty decisions of 2026-09-29: good gear no longer makes a crowd harmless. Monsters hit harder and
 * more of them swing at once against a well-equipped player, the stronger ones find the gaps in armor, pressure
 * builds faster (blocked blows too), and a player backing away gets surrounded anyway (the surround mode).
 */
public class DificultadGameTests {
	/** A player whose weapon hits hard enough for gear tier 1 or more: nothing else about them changes. */
	private static CombatGameTests.TestPlayer geared(GameTestHelper helper, BlockPos at) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, at);
		player.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(
			new AttributeModifier(Forja.id("prueba_equipo"), 20.0, AttributeModifier.Operation.ADD_VALUE));
		return player;
	}

	private static float hit(LivingEntity target, Mob attacker, float amount) {
		float before = target.getHealth();
		target.invulnerableTime = 0;
		target.hurtServer(levelOf(target), target.level().damageSources().mobAttack(attacker), amount);
		return before - target.getHealth();
	}

	private static net.minecraft.server.level.ServerLevel levelOf(LivingEntity entity) {
		return (net.minecraft.server.level.ServerLevel) entity.level();
	}

	private static Zombie zombie(GameTestHelper helper, BlockPos at) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, at);
		zombie.setNoAi(true);
		return zombie;
	}

	/** +15 % a gear tier: the same zombie hits the geared player harder than the bare one. */
	@GameTest
	public void gearTierRaisesMobDamage(GameTestHelper helper) {
		CombatGameTests.TestPlayer bare = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer strong = geared(helper, new BlockPos(1, 1, 4));
		int tier = GearScore.tier(strong);
		helper.assertTrue(GearScore.tier(bare) == 0, "sin nada, tramo 0: " + GearScore.tier(bare));
		helper.assertTrue(tier >= 1, "con un arma que pega fuerte, tramo 1 o más: " + tier);
		helper.assertTrue(Math.abs(GearScore.damageFactor(3) - 1.45) < 1.0E-9, "en el tramo 3, +45 %: " + GearScore.damageFactor(3));
		Zombie zombie = zombie(helper, new BlockPos(3, 1, 2));
		float toBare = hit(bare, zombie, 4.0F);
		float toStrong = hit(strong, zombie, 4.0F);
		double ratio = toStrong / toBare;
		helper.assertTrue(toBare > 0.0F && ratio >= GearScore.damageFactor(tier) - 0.03,
			"al de tramo " + tier + " le quita x" + GearScore.damageFactor(tier) + ": " + toBare + " frente a " + toStrong);
		zombie.discard();
		helper.succeed();
	}

	/** One more monster may swing at once per gear tier. */
	@GameTest
	public void moreAttackersAtAHigherGearTier(GameTestHelper helper) {
		CombatGameTests.TestPlayer bare = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer strong = geared(helper, new BlockPos(1, 1, 4));
		int tier = GearScore.tier(strong);
		int more = Aggression.maxAttackers(strong) - Aggression.maxAttackers(bare);
		helper.assertTrue(more == tier * CombatConfig.get().attackersPerGearTier,
			"con tramo " + tier + " pegan " + more + " más a la vez, y deberían ser " + tier);
		helper.succeed();
	}

	/** An elite's blow gets through diamond armor better than its damage multiplier alone would explain. */
	@GameTest
	public void anEliteFindsTheGapsInArmor(GameTestHelper helper) {
		helper.assertTrue(Threat.VETERANO.penetration() == 0.10 && Threat.ELITE.penetration() == 0.20
			&& Threat.CAMPEON.penetration() == 0.35 && Threat.NORMAL.penetration() == 0.0, "penetración por amenaza: 0, 10, 20, 35 %");
		List<CombatGameTests.TestPlayer> players = new ArrayList<>();
		for (int z : new int[] {1, 4}) {
			CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, z));
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
			players.add(player);
		}
		// the same zombie both times, a normal one and then an elite: nothing else about it changes
		Zombie zombie = zombie(helper, new BlockPos(3, 1, 2));
		float normal = hit(players.get(0), zombie, 6.0F);
		zombie.addTag("forja_amenaza_elite");
		float elite = hit(players.get(1), zombie, 6.0F);
		double ratio = elite / normal;
		helper.assertTrue(normal > 0.0F && ratio > Threat.ELITE.damage + 0.02,
			"la élite pasa la armadura: x" + ratio + " (solo por su daño sería x" + Threat.ELITE.damage + ")");
		zombie.discard();
		helper.succeed();
	}

	/** Pressure: 0.065 a blow taken, and half that for a blow caught on a shield or parried. */
	@GameTest
	public void blockedBlowsAddHalfThePressure(GameTestHelper helper) {
		CombatGameTests.TestPlayer blocked = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer taken = CombatGameTests.player(helper, new BlockPos(1, 1, 4));
		Pressure.onBlocked(blocked);
		Pressure.onHit(taken);
		helper.assertTrue(Math.abs(Pressure.of(taken) - 0.065) < 1.0E-9, "un golpe recibido, 0,065: " + Pressure.of(taken));
		helper.assertTrue(Math.abs(Pressure.of(blocked) - 0.0325) < 1.0E-9, "un golpe parado, 0,0325: " + Pressure.of(blocked));
		helper.succeed();
	}

	/**
	 * The surround mode itself: a zombie with its slot ahead of a player who is backing away runs at x2.3 its
	 * walking speed and pays 1.4 of its breath a tick for it (about 3.5 s from full). The player's movement is
	 * read off their positions: a fake player's client reports nothing.
	 */
	@GameTest
	public void theSurroundModeRunsAtTwoPointThree(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(3, 1, 3));
		Zombie zombie = zombie(helper, new BlockPos(7, 1, 3));
		zombie.setTarget(player);
		helper.runAfterDelay(1, () -> {
			MobSprint.motion(player);
			player.setPos(player.getX() - 0.28, player.getY(), player.getZ());
		});
		helper.runAfterDelay(2, () -> {
			MobMind mind = MobAi.mind(zombie);
			helper.assertTrue(mind != null, "el zombi tiene mente");
			mind.target = player;
			// its slot straight ahead of the way the player is going (-X), well away from the zombie
			mind.ringAngle = Math.PI;
			mind.ringRadius = 3.5;
			mind.stamina = MobSprint.MAX;
			mind.winded = false;
			mind.wantsRun = true;
			// running away, it runs at the ordinary x1.35: the surround mode is for the way to its slot
			mind.decision = Decision.tactic(Tactic.RETIRARSE);
			MobSprint.tick(mind, helper.getLevel().getGameTime());
			helper.assertTrue(mind.running && !mind.rodeo, "huyendo corre, pero no en modo rodeo");
			mind.stamina = MobSprint.MAX;
			mind.decision = Decision.tactic(Tactic.RODEAR);
			helper.assertTrue(MobSprint.rodeo(mind), "retrocediendo, con el hueco por delante: modo rodeo (se mueve a "
				+ MobSprint.motion(player) + ")");
			MobSprint.tick(mind, helper.getLevel().getGameTime());
			helper.assertTrue(mind.rodeo && mind.running && zombie.isSprinting(), "corre en modo rodeo");
			// vanilla's sprint (x1.3) and the mod's modifier on top of it make the level's share of x2.3 (Ladder.sprint)
			var extra = zombie.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Forja.id("carrera"));
			double total = 1.3 * (1.0 + (extra == null ? 0.0 : extra.amount()));
			double expected = dev.forja.difficulty.Ladder.current().rodeoMultiplier();
			helper.assertTrue(Math.abs(total - expected) < 0.01, "a x" + expected + " de su paso: x" + total);
			helper.assertTrue(Math.abs(mind.stamina - (MobSprint.MAX - CombatConfig.get().rodeoCostPerTick)) < 1.0E-4,
				"paga " + CombatConfig.get().rodeoCostPerTick + " por tick: le queda " + mind.stamina);
			zombie.discard();
			helper.succeed();
		});
	}
}
