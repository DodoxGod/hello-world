package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import dev.forja.Forja;
import dev.forja.ai.Aggression;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.GearScore;
import dev.forja.difficulty.Pressure;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

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

	/** Pressure: 0.10 a blow taken, and half that for a blow caught on a shield or parried. */
	@GameTest
	public void blockedBlowsAddHalfThePressure(GameTestHelper helper) {
		CombatGameTests.TestPlayer blocked = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer taken = CombatGameTests.player(helper, new BlockPos(1, 1, 4));
		Pressure.onBlocked(blocked);
		Pressure.onHit(taken);
		helper.assertTrue(Math.abs(Pressure.of(taken) - 0.10) < 1.0E-9, "un golpe recibido, 0,10: " + Pressure.of(taken));
		helper.assertTrue(Math.abs(Pressure.of(blocked) - 0.05) < 1.0E-9, "un golpe parado, 0,05: " + Pressure.of(blocked));
		helper.succeed();
	}

	/**
	 * The surround mode: four zombies after a player who backs away at a sprint (0.28 blocks a tick, straight
	 * back). Before, they trailed behind him in a line; now one of them gets round past him within six seconds.
	 */
	@GameTest(maxTicks = 160)
	public void aPackSurroundsAPlayerBackingAway(GameTestHelper helper) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		double packs = CombatConfig.get().packChance;
		CombatConfig.get().packChance = 0.0;
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(2, 1, 4));
		player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		List<Zombie> pack = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(6, 1, 2 + i));
			zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			zombie.setTarget(player);
			pack.add(zombie);
		}
		double step = 0.28;
		Vec3 back = new Vec3(-step, 0.0, 0.0);
		int[] ticks = {0};
		helper.onEachTick(() -> {
			if (!player.isAlive()) {
				return;
			}
			ticks[0]++;
			player.setHealth(player.getMaxHealth());
			player.setPos(player.getX() + back.x, player.getY(), player.getZ());
			known(player, back);
			for (Zombie zombie : pack) {
				if (zombie.getTarget() != player) {
					zombie.setTarget(player);
				}
				// past him along the way he is backing: he is walking into it
				if (zombie.getX() < player.getX() - 0.5) {
					CombatConfig.get().packChance = packs;
					pack.forEach(Mob::discard);
					helper.succeed();
					return;
				}
			}
			if (ticks[0] >= 120) {
				StringBuilder where = new StringBuilder();
				pack.forEach(z -> where.append(String.format(java.util.Locale.ROOT, " %.1f", z.getX() - player.getX())));
				CombatConfig.get().packChance = packs;
				pack.forEach(Mob::discard);
				helper.fail("en 6 s ningún zombi le adelantó (distancias en x:" + where + ")");
			}
		});
	}

	/**
	 * The movement a player is known to make, as the server takes it from their client. A fake player has no
	 * client, so it is set here; where the method is missing, its own motion stands in.
	 */
	private static void known(ServerPlayer player, Vec3 movement) {
		try {
			ServerPlayer.class.getMethod("setKnownMovement", Vec3.class).invoke(player, movement);
		} catch (ReflectiveOperationException missing) {
			player.setDeltaMovement(movement);
		}
	}
}
