package dev.forja.test;

import dev.forja.ai.Decision;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsM1;
import dev.forja.ai.Reach;
import dev.forja.ai.Tactic;
import dev.forja.combat.AttackTokens;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * "Parece que los mobs tampoco tienen en cuenta el rango de su arma" (Andy, 2026-09-28): a monster strikes
 * from as far as its weapon reaches, stops there instead of walking into the player's face, keeps a spear's
 * point at its length, and waits its turn out of the reach of the player's weapon.
 */
public class AlcanceGameTests {
	private static ItemStack forged(ForgeType type) {
		return Assembler.create(type, Assembler.defaultMaterials(type));
	}

	private static void floor(GameTestHelper helper, int width, int depth) {
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** A zombie with this in its hand, a helmet against the sun, and nothing else. */
	private static Zombie armed(GameTestHelper helper, BlockPos pos, ItemStack weapon) {
		CombatGameTests.noRandomThreat();
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, pos);
		zombie.setBaby(false);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		zombie.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		return zombie;
	}

	/** A player holding this, with what it adds to their reach applied (a test player never ticks its equipment). */
	private static CombatGameTests.TestPlayer holding(GameTestHelper helper, BlockPos pos, ItemStack weapon) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			var instance = player.getAttribute(attribute);
			if (instance != null) {
				instance.addOrUpdateTransientModifier(modifier);
			}
		});
		return player;
	}

	/** The numbers: a flail adds its chain, a lance its length less the body's, a sword or a fist nothing. */
	@GameTest
	public void weaponReachNumbers(GameTestHelper helper) {
		double eps = 1.0E-4;
		helper.assertTrue(Math.abs(Reach.of(ItemStack.EMPTY) - ObsM1.REACH) < eps, "mano vacía: " + Reach.of(ItemStack.EMPTY));
		helper.assertTrue(Math.abs(Reach.of(new ItemStack(Items.IRON_SWORD)) - ObsM1.REACH) < eps, "espada vanilla");
		helper.assertTrue(Math.abs(Reach.of(forged(ForgeType.ESPADA)) - ObsM1.REACH) < eps, "espada forjada: " + Reach.of(forged(ForgeType.ESPADA)));
		helper.assertTrue(Math.abs(Reach.of(forged(ForgeType.DAGA)) - ObsM1.REACH) < eps, "daga");
		helper.assertTrue(Math.abs(Reach.extra(forged(ForgeType.MANGUAL)) - Assembler.MANGUAL_REACH) < eps, "mangual: " + Reach.extra(forged(ForgeType.MANGUAL)));
		helper.assertTrue(Math.abs(Reach.extra(forged(ForgeType.GUADANA)) - Assembler.GUADANA_REACH) < eps, "guadaña");
		helper.assertTrue(Math.abs(Reach.extra(forged(ForgeType.TRIDENTE)) - Assembler.TRIDENTE_REACH) < eps, "tridente");
		helper.assertTrue(Math.abs(Reach.extra(forged(ForgeType.LANZA)) - 1.5) < eps, "lanza: " + Reach.extra(forged(ForgeType.LANZA)));
		helper.assertTrue(Math.abs(Reach.min(forged(ForgeType.LANZA)) - 1.0) < eps, "la punta de la lanza no sirve a menos de 1: " + Reach.min(forged(ForgeType.LANZA)));
		helper.assertTrue(Reach.min(forged(ForgeType.MANGUAL)) == 0.0, "el mangual no tiene alcance mínimo");
		CombatGameTests.TestPlayer bare = holding(helper, new BlockPos(1, 1, 1), ItemStack.EMPTY);
		CombatGameTests.TestPlayer flail = holding(helper, new BlockPos(1, 1, 3), forged(ForgeType.MANGUAL));
		CombatGameTests.TestPlayer lance = holding(helper, new BlockPos(1, 1, 5), forged(ForgeType.LANZA));
		helper.assertTrue(Math.abs(Reach.player(bare) - 3.0) < eps, "jugador sin nada: " + Reach.player(bare));
		helper.assertTrue(Math.abs(Reach.player(flail) - 6.0) < eps, "jugador con mangual: " + Reach.player(flail));
		helper.assertTrue(Math.abs(Reach.player(lance) - 4.5) < eps, "jugador con lanza: " + Reach.player(lance));
		helper.assertTrue(Reach.outside(bare, 3.5) == 3.5, "sin arma larga, el anillo sigue en 3.5");
		helper.assertTrue(Math.abs(Reach.outside(flail, 3.5) - 6.5) < eps, "con mangual, el anillo se abre a 6.5");
		helper.succeed();
	}

	/**
	 * By the rules: a zombie with a flail strikes a still player from 3 blocks off, and holds there instead of
	 * walking in; a bare-handed zombie that cannot walk, as far off, never reaches (as before).
	 */
	@GameTest(maxTicks = 200)
	public void flailZombieStrikesFromItsReach(GameTestHelper helper) {
		floor(helper, 8, 8);
		CombatGameTests.TestPlayer near = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer far = CombatGameTests.player(helper, new BlockPos(1, 1, 6));
		Zombie flail = armed(helper, new BlockPos(4, 1, 1), forged(ForgeType.MANGUAL));
		Zombie bare = armed(helper, new BlockPos(4, 1, 6), ItemStack.EMPTY);
		bare.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		flail.setTarget(near);
		bare.setTarget(far);
		double[] closest = {Double.MAX_VALUE};
		String[] why = {""};
		helper.onEachTick(() -> {
			double gap = Reach.gap(flail, near);
			if (gap < closest[0]) {
				closest[0] = gap;
				why[0] = "tick " + flail.tickCount + ", decisión " + MobAi.mind(flail).decision + ", metas "
					+ ((dev.forja.mixin.MobGoalsAccess) flail).forjaGoals().getAvailableGoals().stream().filter(g -> g.isRunning())
						.map(g -> g.getGoal().getClass().getSimpleName()).toList()
					+ ", embestidas " + dev.forja.combat.CombatStats.count(flail, dev.forja.combat.CombatStats.LUNGE);
			}
		});
		helper.runAfterDelay(140, () -> {
			helper.assertTrue(near.getHealth() < near.getMaxHealth(), "el zombi del mangual no golpeó desde su alcance (a "
				+ flail.distanceTo(near) + ", decisión " + MobAi.mind(flail).decision + ")");
			helper.assertTrue(closest[0] > 2.0, "se quedó a su alcance, sin meterse encima: se acercó a " + closest[0] + " entre cajas (" + why[0] + ")");
			helper.assertTrue(far.getHealth() == far.getMaxHealth(), "un zombi sin arma no llega a 3 bloques, y le pegó");
			helper.succeed();
		});
	}

	/**
	 * With a network (towards + use): the one with the flail stops at its reach and strikes from there; the
	 * bare-handed one walks in and strikes from up close, as it always did.
	 */
	@GameTest(maxTicks = 200)
	public void networkFlailZombieStopsAtItsReach(GameTestHelper helper) {
		floor(helper, 8, 8);
		float[] bias = new float[NetBrain.V2_OUTPUTS];
		bias[1] = 20.0F;
		bias[10] = 20.0F;
		bias[NetBrain.TACTIC_AT + Tactic.LIBRE.ordinal()] = 20.0F;
		bias[NetBrain.SPECIAL_AT] = 20.0F;
		bias[NetBrain.DEFENSE_AT] = 20.0F;
		bias[NetBrain.FEINT_AT] = -20.0F;
		NetBrain net = NetBrain.fromJson(AiGameTests.fakeV2(bias));
		CombatGameTests.TestPlayer near = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer far = CombatGameTests.player(helper, new BlockPos(1, 1, 6));
		Zombie flail = armed(helper, new BlockPos(4, 1, 1), forged(ForgeType.MANGUAL));
		Zombie bare = armed(helper, new BlockPos(4, 1, 6), ItemStack.EMPTY);
		MobAi.mind(flail).override = net;
		MobAi.mind(bare).override = net;
		flail.setTarget(near);
		bare.setTarget(far);
		double[] closest = {Double.MAX_VALUE, Double.MAX_VALUE};
		helper.onEachTick(() -> {
			closest[0] = Math.min(closest[0], Reach.gap(flail, near));
			closest[1] = Math.min(closest[1], Reach.gap(bare, far));
		});
		helper.runAfterDelay(140, () -> {
			helper.assertTrue(MobAi.mind(flail).networked, "la red debería llevarlo");
			helper.assertTrue(near.getHealth() < near.getMaxHealth(), "el zombi del mangual (red) no golpeó (a " + flail.distanceTo(near) + ")");
			helper.assertTrue(closest[0] > 2.0, "se paró a su alcance: se acercó a " + closest[0] + " entre cajas");
			helper.assertTrue(far.getHealth() < far.getMaxHealth(), "el zombi sin arma (red) no golpeó");
			helper.assertTrue(closest[1] <= ObsM1.REACH, "el de la mano vacía se acercó hasta su alcance de siempre: " + closest[1]);
			helper.succeed();
		});
	}

	/**
	 * Waiting its turn against a player with a flail, it waits out of the flail's reach (6 blocks, not the 3
	 * of a sword): the ring and the waiting distance are pushed out by what the flail adds.
	 */
	@GameTest(maxTicks = 200)
	public void waitingMobStaysOutOfTheFlailsReach(GameTestHelper helper) {
		floor(helper, 12, 8);
		CombatGameTests.TestPlayer player = holding(helper, new BlockPos(0, 1, 3), forged(ForgeType.MANGUAL));
		// Every turn on the player taken by pigs that hold them and do nothing.
		for (int k = 0; k < 4; k++) {
			Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(k, 1, 7));
			pig.setNoAi(true);
			pig.setTarget(player);
			AttackTokens.tryAcquire(player, pig, 10);
		}
		Zombie zombie = armed(helper, new BlockPos(4, 1, 3), ItemStack.EMPTY);
		zombie.setTarget(player);
		helper.runAfterDelay(120, () -> {
			MobMind mind = MobAi.mind(zombie);
			helper.assertTrue(!AttackTokens.holds(player, zombie), "no debería tener turno");
			helper.assertTrue(mind.decision.tactic() == Tactic.ESPERAR, "espera su turno: " + mind.decision);
			double edge = Math.hypot(zombie.getX() - player.getX(), zombie.getZ() - player.getZ()) - zombie.getBbWidth() / 2.0;
			helper.assertTrue(edge > Reach.player(player), "espera fuera del mangual (" + Reach.player(player) + "): su caja a " + edge);
			helper.assertTrue(player.getHealth() == player.getMaxHealth(), "sin turno no golpea");
			helper.succeed();
		});
	}

	/** A lance right up against the player is too close for its point: it steps back. A sword does not. */
	@GameTest(maxTicks = 120)
	public void lanceStepsBackWhenTooClose(GameTestHelper helper) {
		floor(helper, 8, 8);
		CombatGameTests.noRandomThreat();
		CombatGameTests.TestPlayer lancePlayer = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer swordPlayer = CombatGameTests.player(helper, new BlockPos(1, 1, 6));
		Vindicator lance = helper.spawn(EntityTypes.VINDICATOR, new BlockPos(2, 1, 1));
		Vindicator sword = helper.spawn(EntityTypes.VINDICATOR, new BlockPos(2, 1, 6));
		lance.setItemSlot(EquipmentSlot.MAINHAND, forged(ForgeType.LANZA));
		sword.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		lance.setTarget(lancePlayer);
		sword.setTarget(swordPlayer);
		boolean[] backed = {false, false};
		double[] widest = {0.0};
		helper.onEachTick(() -> {
			backed[0] |= stepsBack(lance);
			backed[1] |= stepsBack(sword);
			widest[0] = Math.max(widest[0], Reach.gap(lance, lancePlayer));
		});
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(backed[0], "la lanza pegada al jugador debería retroceder");
			helper.assertTrue(widest[0] >= Reach.min(lance), "y salir de su alcance mínimo (" + Reach.min(lance) + "): llegó a " + widest[0]);
			helper.assertTrue(!backed[1], "con espada no hay por qué retroceder");
			helper.succeed();
		});
	}

	/** Whether the rules just told it to step straight back (the spear's rule; nothing else says that). */
	private static boolean stepsBack(Mob mob) {
		MobMind mind = MobAi.mind(mob);
		Decision decision = mind == null ? null : mind.decision;
		return decision != null && decision.tactic() == Tactic.LIBRE && decision.move() == 5;
	}

	/**
	 * Andy (2026-09-29): "un zombie con espada debería poder atacar de más lejos que uno con puños". A monster's
	 * reach grows by what it swings (CombatConfig mobReach*), while the contract's number the network sees does not.
	 */
	@GameTest
	public void aSwordReachesFurtherThanAFist(GameTestHelper helper) {
		double eps = 1.0E-4;
		var cfg = dev.forja.combat.CombatConfig.get();
		helper.assertTrue(Math.abs(Reach.actionExtra(ItemStack.EMPTY) - cfg.mobReachFist) < eps, "puños");
		helper.assertTrue(Math.abs(Reach.actionExtra(new ItemStack(Items.IRON_SWORD)) - cfg.mobReachSword) < eps, "espada vanilla");
		helper.assertTrue(Math.abs(Reach.actionExtra(forged(ForgeType.ESPADA)) - cfg.mobReachSword) < eps, "espada forjada");
		helper.assertTrue(Math.abs(Reach.actionExtra(forged(ForgeType.DAGA)) - cfg.mobReachDagger) < eps, "daga");
		helper.assertTrue(Math.abs(Reach.actionExtra(forged(ForgeType.ESPADON)) - cfg.mobReachGreatsword) < eps, "espadón");
		helper.assertTrue(Math.abs(Reach.actionExtra(new ItemStack(Items.IRON_AXE)) - cfg.mobReachAxe) < eps, "hacha");
		helper.assertTrue(Math.abs(Reach.actionExtra(new ItemStack(Items.MACE)) - cfg.mobReachAxe) < eps, "maza");
		helper.assertTrue(Math.abs(Reach.actionExtra(forged(ForgeType.MANGUAL)) - Reach.extra(forged(ForgeType.MANGUAL))) < eps, "el mangual se queda con lo suyo");
		// what the network is shown does not move
		helper.assertTrue(Math.abs(Reach.of(new ItemStack(Items.IRON_SWORD)) - ObsM1.REACH) < eps, "yo_arma_alcance de la espada sigue en el del contrato");

		floor(helper, 8, 8);
		Zombie sword = armed(helper, new BlockPos(4, 1, 1), new ItemStack(Items.IRON_SWORD));
		Zombie fist = armed(helper, new BlockPos(4, 1, 6), ItemStack.EMPTY);
		sword.setNoAi(true);
		fist.setNoAi(true);
		// each 0.3 further than a bare body reaches, box to box
		double centres = 0.6 + ObsM1.REACH + 0.3;
		CombatGameTests.TestPlayer swordTarget = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer fistTarget = CombatGameTests.player(helper, new BlockPos(1, 1, 6));
		swordTarget.setPos(sword.getX() - centres, sword.getY(), sword.getZ());
		fistTarget.setPos(fist.getX() - centres, fist.getY(), fist.getZ());
		helper.assertTrue(Reach.reaches(sword, swordTarget), "la espada llega");
		helper.assertTrue(!Reach.reaches(fist, fistTarget), "los puños no");
		helper.assertTrue(sword.isWithinMeleeAttackRange(swordTarget), "vanilla también lo ve al alcance de la espada");
		helper.assertTrue(!fist.isWithinMeleeAttackRange(fistTarget), "y fuera del de los puños");
		sword.discard();
		fist.discard();
		helper.succeed();
	}

	/**
	 * Contract v3.1 (docs/red_mob_v3_1.md): a network whose file says "alcance_v": 2 reads yo_arma_alcance as the
	 * full reach the monster strikes with; one without it reads the old number.
	 */
	@GameTest
	public void yoArmaAlcanceFollowsTheNetworksVersion(GameTestHelper helper) {
		com.google.gson.JsonObject json = AiGameTests.fakeV2(new float[NetBrain.V2_OUTPUTS]);
		helper.assertTrue(NetBrain.fromJson(json).reachVersion == 1, "sin el campo, la versión 1");
		json.addProperty("alcance_v", 2);
		helper.assertTrue(NetBrain.fromJson(json).reachVersion == 2, "con \"alcance_v\": 2, la 2");

		Zombie sword = armed(helper, new BlockPos(3, 1, 1), new ItemStack(Items.IRON_SWORD));
		Zombie lance = armed(helper, new BlockPos(3, 1, 4), forged(ForgeType.LANZA));
		sword.setNoAi(true);
		lance.setNoAi(true);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 2));
		helper.runAfterDelay(1, () -> {
			int at = dev.forja.ai.ObsV3.names().indexOf("yo_arma_alcance/6");
			helper.assertTrue(at >= 0, "la entrada existe");
			float[] out = new float[dev.forja.ai.ObsV3.size()];
			MobMind mind = MobAi.mind(sword);
			mind.reachVersion = 1;
			dev.forja.ai.ObsV3.fill(sword, player, out, 0);
			helper.assertTrue(Math.abs(out[at] - Reach.of(new ItemStack(Items.IRON_SWORD)) / 6.0) < 1.0E-4, "v1: la espada, como en el contrato: " + out[at] * 6.0);
			mind.reachVersion = 2;
			dev.forja.ai.ObsV3.fill(sword, player, out, 0);
			helper.assertTrue(Math.abs(out[at] - Reach.actionOf(new ItemStack(Items.IRON_SWORD)) / 6.0) < 1.0E-4, "v2: la espada con su extra: " + out[at] * 6.0);
			MobMind lanceMind = MobAi.mind(lance);
			lanceMind.reachVersion = 2;
			dev.forja.ai.ObsV3.fill(lance, player, out, 0);
			helper.assertTrue(Math.abs(out[at] * 6.0 - (ObsM1.REACH + 1.5)) < 1.0E-3, "v2: la lanza, su máximo (2,33): " + out[at] * 6.0);
			sword.discard();
			lance.discard();
			helper.succeed();
		});
	}
}
