package dev.forja.test;

import dev.forja.combat.ArmorCalculator;
import dev.forja.combat.AttackProfile;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.DamageKind;
import dev.forja.combat.HitZone;
import dev.forja.difficulty.Pressure;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Armour pressure, as Andy decided it on 2026-09-30: it starts at 0, grows with every blow taken (a blocked one
 * half as much) up to 60 %, and drains gradually after 3 s without a blow; its penetration is chained with the
 * weapon's and the attacker's rank, those two capped together at 30 % and the whole at 60 %, so no single blow
 * takes more than a third of the armour ("tiene que existir un balance").
 */
public class PresionGameTests {
	private static final double EPS = 1.0E-9;

	/** A fresh player in diamond: nothing has hit them yet. */
	private static CombatGameTests.TestPlayer diamond(GameTestHelper helper, BlockPos at) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, at);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		return player;
	}

	@GameTest
	public void pressureStartsAtZero(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		helper.assertTrue(Pressure.of(player) == 0.0, "sin golpes, presión 0: " + Pressure.of(player));
		helper.assertTrue(Pressure.integrity(Pressure.of(player), CombatConfig.get().pressureMax) == 1.0, "y la armadura entera");
		helper.succeed();
	}

	/** Each blow adds one step, never more; it takes 8 to 10 to reach the cap, and the cap is 0.60. */
	@GameTest
	public void pressureGrowsPerHitAndCaps(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		helper.assertTrue(Math.abs(cfg.pressureMax - 0.60) < EPS, "el tope es 0,60: " + cfg.pressureMax);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		int hits = 0;
		double before = 0.0;
		while (Pressure.of(player) < cfg.pressureMax - EPS && hits < 40) {
			Pressure.onHit(player);
			hits++;
			double after = Pressure.of(player);
			helper.assertTrue(after - before <= cfg.pressurePerHit + EPS, "un golpe, un paso como mucho: " + before + " -> " + after);
			helper.assertTrue(after > before, "cada golpe sube la presión: " + before + " -> " + after);
			before = after;
		}
		helper.assertTrue(hits >= 8 && hits <= 10, "de 0 al tope en 8 a 10 golpes: " + hits);
		for (int i = 0; i < 20; i++) {
			Pressure.onHit(player);
		}
		helper.assertTrue(Math.abs(Pressure.of(player) - 0.60) < EPS, "y no pasa de 0,60: " + Pressure.of(player));
		helper.assertTrue(Pressure.integrity(Pressure.of(player), cfg.pressureMax) == 0.0, "al tope la armadura está rota (0)");
		helper.succeed();
	}

	/** A blow caught on a shield or parried adds half a step. */
	@GameTest
	public void blockedHitAddsHalf(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		CombatGameTests.TestPlayer blocked = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer taken = CombatGameTests.player(helper, new BlockPos(1, 1, 4));
		Pressure.onBlocked(blocked);
		Pressure.onHit(taken);
		helper.assertTrue(Math.abs(Pressure.of(taken) - cfg.pressurePerHit) < EPS, "un golpe, un paso: " + Pressure.of(taken));
		helper.assertTrue(Math.abs(Pressure.of(blocked) - cfg.pressurePerHit / 2.0) < EPS, "uno parado, medio paso: " + Pressure.of(blocked));
		helper.succeed();
	}

	/** The three chained, in numbers worked out by hand, and the two caps. */
	@GameTest
	public void chainedPenetration(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		// weapon 10 %, veteran 10 %: base 19 %, under the cap; with 20 % of pressure on top: 1 - 0.81 * 0.80
		helper.assertTrue(Math.abs(Pressure.total(0.10, 0.10, 0.0, cfg) - 0.19) < EPS, "arma y rango: " + Pressure.total(0.10, 0.10, 0.0, cfg));
		helper.assertTrue(Math.abs(Pressure.total(0.10, 0.10, 0.20, cfg) - 0.352) < EPS, "con presión: " + Pressure.total(0.10, 0.10, 0.20, cfg));
		// weapon 20 %, elite 20 %: 36 % is over the 30 % base cap; with 30 % of pressure: 1 - 0.70 * 0.70
		helper.assertTrue(Math.abs(Pressure.total(0.20, 0.20, 0.0, cfg) - 0.30) < EPS, "la base topa en 0,30: " + Pressure.total(0.20, 0.20, 0.0, cfg));
		helper.assertTrue(Math.abs(Pressure.total(0.20, 0.20, 0.30, cfg) - 0.51) < EPS, "base topada y presión: " + Pressure.total(0.20, 0.20, 0.30, cfg));
		// and the damage follows it: the same blow on the same armour takes more through with the three than with the weapon alone
		CombatGameTests.TestPlayer player = diamond(helper, new BlockPos(1, 1, 1));
		AttackProfile weaponOnly = new AttackProfile(DamageKind.SLASH, 0.10, HitZone.WHOLE, false);
		AttackProfile all = new AttackProfile(DamageKind.SLASH, Pressure.total(0.10, 0.10, 0.20, cfg), HitZone.WHOLE, false);
		float plain = ArmorCalculator.apply(player, 10.0F, weaponOnly, 0.0);
		float chained = ArmorCalculator.apply(player, 10.0F, all, 0.0);
		float viaExtra = ArmorCalculator.apply(player, 10.0F, new AttackProfile(DamageKind.SLASH, 0.0, HitZone.WHOLE, false),
			Pressure.total(0.10, 0.10, 0.20, cfg));
		helper.assertTrue(chained > plain + 0.05F, "con arma, rango y presión pasa más: " + chained + " vs " + plain);
		helper.assertTrue(Math.abs(chained - viaExtra) < 1.0E-4F, "la penetración final pesa igual por donde entre: " + chained + " vs " + viaExtra);
		helper.succeed();
	}

	/** Never more than 60 % in total, whatever the weapon, the rank and the pressure; and never more than 30 % before pressure. */
	@GameTest
	public void penetrationHasTwoCaps(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		double worst = 0.0;
		double worstBase = 0.0;
		for (double weapon = 0.0; weapon <= 1.0001; weapon += 0.05) {
			for (double rank = 0.0; rank <= 1.0001; rank += 0.05) {
				worstBase = Math.max(worstBase, Pressure.total(weapon, rank, 0.0, cfg));
				for (double pressure = 0.0; pressure <= 0.60001; pressure += 0.05) {
					worst = Math.max(worst, Pressure.total(weapon, rank, pressure, cfg));
				}
			}
		}
		helper.assertTrue(worst <= 0.60 + EPS && worst > 0.59, "el total no pasa de 0,60: " + worst);
		helper.assertTrue(worstBase <= 0.30 + EPS, "sin presión, no pasa de 0,30: " + worstBase);
		helper.succeed();
	}

	/** A champion's axe (35 % and 35 %) on a fresh player takes 30 % of the armour, not 58 %: and the real blow agrees. */
	@GameTest
	public void championAxeOnAFreshPlayerTakesAtMostThirty(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		CombatGameTests.TestPlayer fresh = diamond(helper, new BlockPos(1, 1, 1));
		double pen = Pressure.penetration(fresh, 0.35, Threat.CAMPEON.penetration());
		helper.assertTrue(pen <= 0.30 + EPS, "un golpe a un jugador entero: " + pen);
		helper.assertTrue(Pressure.chain(0.35, 0.35) > 0.57, "(sin tope serían " + Pressure.chain(0.35, 0.35) + ")");
		// The real blow, from a champion zombie: with the cap it takes less through the diamond than without it.
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
		zombie.setNoAi(true);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		Threat.CAMPEON.mark(zombie);
		CombatGameTests.TestPlayer capped = diamond(helper, new BlockPos(1, 1, 4));
		CombatGameTests.TestPlayer uncapped = diamond(helper, new BlockPos(1, 1, 7));
		float withCap = hit(capped, zombie, 8.0F);
		double saved = cfg.penetrationBaseMax;
		float without;
		try {
			cfg.penetrationBaseMax = 1.0;
			without = hit(uncapped, zombie, 8.0F);
		} finally {
			cfg.penetrationBaseMax = saved;
		}
		helper.assertTrue(withCap > 0.0F && withCap < without, "con tope entra menos (" + withCap + ") que sin él (" + without + ")");
		helper.assertTrue(Pressure.of(fresh) == 0.0, "y el jugador entero seguía sin presión");
		helper.succeed();
	}

	private static float hit(LivingEntity target, Zombie attacker, float amount) {
		float before = target.getHealth();
		target.invulnerableTime = 0;
		target.hurtServer((ServerLevel) target.level(), target.level().damageSources().mobAttack(attacker), amount);
		return before - target.getHealth();
	}

	/** Nothing drains for 60 ticks after the last blow; then it goes down a little each tick, and is gone in about 90 more. */
	@GameTest(maxTicks = 260)
	public void recoveryWaitsThenDrainsGradually(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		for (int i = 0; i < 12; i++) {
			Pressure.onHit(player);
		}
		double top = Pressure.of(player);
		helper.assertTrue(Math.abs(top - 0.60) < EPS, "empieza al tope: " + top);
		long total = Math.round(cfg.pressureMax / cfg.pressureDrainPerTick);
		helper.assertTrue(total >= 80 && total <= 100, "del tope a cero en unos 90 ticks (4,5 s): " + total);
		helper.runAfterDelay(cfg.pressureDelayTicks - 1, () -> {
			helper.assertTrue(Math.abs(Pressure.of(player) - top) < EPS, "durante la espera no baja: " + Pressure.of(player));
		});
		double[] seen = new double[3];
		helper.runAfterDelay(cfg.pressureDelayTicks + 11, () -> seen[0] = Pressure.of(player));
		helper.runAfterDelay(cfg.pressureDelayTicks + 41, () -> seen[1] = Pressure.of(player));
		helper.runAfterDelay(cfg.pressureDelayTicks + 71, () -> seen[2] = Pressure.of(player));
		helper.runAfterDelay(cfg.pressureDelayTicks + total + 3, () -> {
			helper.assertTrue(seen[0] < top && seen[0] > seen[1] && seen[1] > seen[2] && seen[2] > 0.0,
				"baja poco a poco, sin saltos: " + seen[0] + " > " + seen[1] + " > " + seen[2]);
			helper.assertTrue(top - seen[0] < 0.12, "a los 10 ticks de empezar ha bajado poco: " + (top - seen[0]));
			helper.assertTrue(Pressure.of(player) == 0.0, "y se acaba a cero: " + Pressure.of(player));
			helper.succeed();
		});
	}

	/** A new blow in the middle of the drain starts from where it had got to, and restarts the wait. */
	@GameTest(maxTicks = 200)
	public void aNewBlowRestartsTheWait(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		for (int i = 0; i < 12; i++) {
			Pressure.onHit(player);
		}
		helper.runAfterDelay(cfg.pressureDelayTicks + 20, () -> {
			double drained = Pressure.of(player);
			helper.assertTrue(drained < 0.60 - 0.1, "ha bajado: " + drained);
			Pressure.onHit(player);
			helper.assertTrue(Math.abs(Pressure.of(player) - (drained + cfg.pressurePerHit)) < EPS, "parte de donde iba: " + Pressure.of(player));
			double now = Pressure.of(player);
			helper.runAfterDelay(cfg.pressureDelayTicks - 1, () -> {
				helper.assertTrue(Math.abs(Pressure.of(player) - now) < EPS, "y vuelve a esperar sus 60 ticks: " + Pressure.of(player));
				helper.succeed();
			});
		});
	}
}
