package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import dev.forja.ai.Decision;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.MobSprint;
import dev.forja.ai.RuleBrain;
import dev.forja.ai.Squad;
import dev.forja.ai.Tactic;
import dev.forja.combat.AttackTokens;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * La IA de grupo de 2026-09-29 (Andy): el creeper que dudaba, el cerco que no rodeaba, los esqueletos que no
 * buscaban línea de tiro y los grupos que no corrían a su sitio. Ver docs/red_mob_v4_propuesta.md.
 */
public class FormacionGameTests {
	private static Zombie zombie(GameTestHelper helper, BlockPos pos) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, pos);
		zombie.setNoAi(true);
		return zombie;
	}

	/** Squad.update, which the mod runs every ten ticks, run now on these minds. */
	private static void squad(GameTestHelper helper, List<MobMind> minds, long now) {
		try {
			java.lang.reflect.Method update = Squad.class.getDeclaredMethod("update", ServerLevel.class, List.class, long.class);
			update.setAccessible(true);
			update.invoke(null, helper.getLevel(), minds, now);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static double wrap(double a) {
		return Math.IEEEremainder(a, Math.PI * 2.0);
	}

	/** The player's facing, in the ring's convention: atan2(z, x) of where they look. */
	private static double facing(CombatGameTests.TestPlayer player) {
		double yaw = Math.toRadians(player.getYRot());
		return Math.atan2(Math.cos(yaw), -Math.sin(yaw));
	}

	/**
	 * Andy's example: thirteen on one player come round in small groups, some behind, some on each side, some in
	 * front; never all of them on the side they came from.
	 */
	@GameTest(maxTicks = 60)
	public void thirteenSurroundInFourGroups(GameTestHelper helper) {
		CombatConfig.get().iaRepartirObjetivos = false;
		TwoPlayerGameTests.Listener player = new TwoPlayerGameTests.Listener(helper.getLevel());
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 1, 4)));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(-90.0F);
		List<Zombie> crowd = new ArrayList<>();
		for (int i = 0; i < 13; i++) {
			// All of them arrive from the same side, in a clump.
			Zombie zombie = zombie(helper, new BlockPos(4 + i % 3, 1, 3 + i / 3 % 3));
			zombie.setTarget(player);
			crowd.add(zombie);
		}
		helper.runAfterDelay(2, () -> {
			List<MobMind> minds = new ArrayList<>();
			crowd.forEach(z -> minds.add(MobAi.mind(z)));
			squad(helper, minds, helper.getLevel().getGameTime());
			int front = 0;
			int behind = 0;
			int left = 0;
			int right = 0;
			for (MobMind mind : minds) {
				double off = wrap(mind.ringAngle - facing(player));
				if (Math.abs(off) <= Math.PI / 4.0) {
					front++;
				} else if (Math.abs(off) >= Math.PI * 3.0 / 4.0) {
					behind++;
				} else if (off > 0.0) {
					left++;
				} else {
					right++;
				}
			}
			String count = "delante " + front + ", detrás " + behind + ", izquierda " + left + ", derecha " + right;
			for (int side : new int[] {front, behind, left, right}) {
				helper.assertTrue(side >= 3 && side <= 4, "13 en grupos de 3 o 4 por lado: " + count);
			}
			crowd.forEach(Mob::discard);
			helper.succeed();
		});
	}

	/** An archer and a creeper fight off the ring: they take no slot, and the four that fight up close share it. */
	@GameTest(maxTicks = 60)
	public void archersAndCreepersTakeNoRingSlot(GameTestHelper helper) {
		CombatConfig.get().iaRepartirObjetivos = false;
		TwoPlayerGameTests.Listener player = new TwoPlayerGameTests.Listener(helper.getLevel());
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 1, 4)));
		player.setPos(at.x, at.y, at.z);
		List<Mob> group = new ArrayList<>();
		for (BlockPos pos : List.of(new BlockPos(4, 1, 4), new BlockPos(5, 1, 3), new BlockPos(5, 1, 5), new BlockPos(6, 1, 4))) {
			group.add(zombie(helper, pos));
		}
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(3, 1, 6));
		var creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(3, 1, 2));
		skeleton.setNoAi(true);
		creeper.setNoAi(true);
		group.add(skeleton);
		group.add(creeper);
		group.forEach(m -> m.setTarget(player));
		helper.runAfterDelay(2, () -> {
			List<MobMind> minds = new ArrayList<>();
			group.forEach(m -> minds.add(MobAi.mind(m)));
			squad(helper, minds, helper.getLevel().getGameTime());
			List<Double> angles = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				angles.add(minds.get(i).ringAngle);
			}
			for (int i = 0; i < angles.size(); i++) {
				for (int j = i + 1; j < angles.size(); j++) {
					double gap = Math.abs(wrap(angles.get(i) - angles.get(j)));
					helper.assertTrue(gap > Math.PI / 2.0 - 0.01, "los cuatro zombis, cada 90°; hueco de " + Math.toDegrees(gap) + "°");
				}
			}
			for (Mob off : List.of(skeleton, creeper)) {
				MobMind mind = MobAi.mind(off);
				double own = Math.atan2(off.getZ() - player.getZ(), off.getX() - player.getX());
				helper.assertTrue(Math.abs(wrap(mind.ringAngle - own)) < 1.0E-6, off.getType() + " se queda donde está, sin hueco en el anillo");
			}
			group.forEach(Mob::discard);
			helper.succeed();
		});
	}

	/** The ring keeps its start while the fight goes on: another one getting nearest does not turn every slot round. */
	@GameTest(maxTicks = 60)
	public void theRingDoesNotTurnWhenTheNearestChanges(GameTestHelper helper) {
		CombatConfig.get().iaRepartirObjetivos = false;
		TwoPlayerGameTests.Listener player = new TwoPlayerGameTests.Listener(helper.getLevel());
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(4, 1, 4)));
		player.setPos(at.x, at.y, at.z);
		List<Zombie> crowd = new ArrayList<>();
		for (BlockPos pos : List.of(new BlockPos(6, 1, 4), new BlockPos(7, 1, 2), new BlockPos(7, 1, 6))) {
			Zombie zombie = zombie(helper, pos);
			zombie.setTarget(player);
			crowd.add(zombie);
		}
		helper.runAfterDelay(2, () -> {
			List<MobMind> minds = new ArrayList<>();
			crowd.forEach(z -> minds.add(MobAi.mind(z)));
			long now = helper.getLevel().getGameTime();
			squad(helper, minds, now);
			List<Double> before = new ArrayList<>();
			minds.forEach(m -> before.add(wrap(m.ringAngle)));
			// Another one comes nearest, from the other side.
			Vec3 other = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(3, 1, 4)));
			crowd.get(2).setPos(other.x, other.y, other.z);
			squad(helper, minds, now + Squad.PERIOD);
			for (MobMind mind : minds) {
				boolean kept = before.stream().anyMatch(a -> Math.abs(wrap(a - mind.ringAngle)) < 1.0E-6);
				helper.assertTrue(kept, "los huecos siguen donde estaban; uno se fue a " + Math.toDegrees(wrap(mind.ringAngle)) + "°");
			}
			crowd.forEach(Mob::discard);
			helper.succeed();
		});
	}

	/** With company, one far off and well round from its slot goes round to it instead of walking straight in. */
	@GameTest(maxTicks = 40)
	public void inCompanyTheyTakeTheirPlaceFromAfar(GameTestHelper helper) {
		CombatConfig.get().iaRepartirObjetivos = false;
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		Zombie zombie = zombie(helper, new BlockPos(12, 1, 1));
		Zombie friend = zombie(helper, new BlockPos(12, 1, 3));
		zombie.setTarget(player);
		friend.setTarget(player);
		helper.runAfterDelay(1, () -> {
			MobMind mind = MobAi.mind(zombie);
			// Its slot is on the far side of the player.
			mind.ringAngle = Math.atan2(zombie.getZ() - player.getZ(), zombie.getX() - player.getX()) + Math.PI;
			mind.ringRadius = Squad.ringRadius(2);
			AttackTokens.release(player, zombie);
			Decision decision = RuleBrain.decide(mind, player);
			helper.assertTrue(decision.tactic() == Tactic.RODEAR || decision.tactic() == Tactic.FLANQUEAR || decision.tactic() == Tactic.ESPERAR,
				"a 11 bloques, en grupo, debería ir a su sitio del anillo, decidió " + decision.tactic());
			// And it runs there: the slot is far off.
			mind.decision = Decision.tactic(Tactic.RODEAR);
			helper.assertTrue(MobSprint.rules(mind, player), "a más de " + MobSprint.SLOT_FAR + " bloques de su hueco, corre");
			zombie.discard();
			friend.discard();
			helper.succeed();
		});
	}

	/** A skeleton with a zombie in its line of fire steps two blocks aside, away from the zombie, to get a clear shot. */
	@GameTest
	public void anArcherStepsAsideForAClearShot(GameTestHelper helper) {
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 4));
		Zombie wall = zombie(helper, new BlockPos(3, 1, 4));
		skeleton.setNoAi(true);
		// Leaning to one side of the line, so the step has a way to go.
		wall.setPos(wall.getX(), wall.getY(), wall.getZ() + 0.3);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(7, 1, 4));
		Vec3 step = Squad.clearLineStep(skeleton, player);
		helper.assertTrue(step != null, "con un zombi delante debería apartarse");
		double dx = player.getX() - skeleton.getX();
		double dz = player.getZ() - skeleton.getZ();
		double sx = step.x - skeleton.getX();
		double sz = step.z - skeleton.getZ();
		helper.assertTrue(Math.abs(sx * dx + sz * dz) < 1.0E-3, "el paso es de lado, no hacia delante ni atrás");
		helper.assertTrue(Math.abs(Math.hypot(sx, sz) - 2.0) < 1.0E-3, "un paso de dos bloques");
		helper.assertTrue(sz < 0.0, "hacia el lado contrario al zombi (que se inclina a +z)");
		wall.discard();
		helper.assertTrue(Squad.clearLineStep(skeleton, player) == null, "con la línea libre, no se mueve");
		skeleton.discard();
		helper.succeed();
	}

	/** A creeper next to a player lights its fuse: no warning of a blow, no turn taken, no hop back. */
	@GameTest(maxTicks = 60)
	public void aCreeperNextToYouLightsItsFuse(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		double feint = cfg.creeperFeintChance;
		cfg.creeperFeintChance = 0.0;
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		var creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(2, 1, 1));
		creeper.setTarget(player);
		helper.onEachTick(() -> helper.assertFalse(AttackTokens.holds(player, creeper), "el creeper no coge turno de golpe"));
		helper.succeedWhen(() -> {
			helper.assertTrue(creeper.getSwellDir() > 0, "el creeper debería encender la mecha");
			cfg.creeperFeintChance = feint;
			creeper.discard();
		});
	}
}
