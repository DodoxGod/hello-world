package dev.forja.test;

import java.util.List;
import java.util.Map;

import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.ObsV4;
import dev.forja.ai.WorldMemory;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Step M6 of the v4 mob network (docs/red_mob_v4_diseno.md §4.11): the world's vector W, what the monsters of a world
 * have learnt of how one player kills them (causes of death, what works, the player's style, confidence), its
 * forgetting, and block W of the observation.
 */
public class RedV4MundoGameTests {
	private static Zombie zombie(GameTestHelper helper, BlockPos at) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, at);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setNoAi(true);
		return zombie;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static float sum(float[] v, int from, int n) {
		float s = 0.0F;
		for (int i = from; i < from + n; i++) {
			s += v[i];
		}
		return s;
	}

	/**
	 * A fresh player: causes 1/8 each, the rest 0. Killed with a blow in the open, then with an arrow, then in lava: each
	 * death moves its cause's average by a tenth towards 1 (the others towards 0), they still add up to 1, confidence
	 * counts the deaths over 50, and a v4 observation reads the same 16 in block W.
	 */
	@GameTest(maxTicks = 60)
	public void deathsTeachTheWorld(GameTestHelper helper) {
		floor(helper);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(5, 1, 3));
		float[] start = WorldMemory.of(player);
		helper.assertTrue(Math.abs(start[WorldMemory.CAUSES + WorldMemory.OPEN] - 0.125F) < 1.0E-6 && start[WorldMemory.CONFIDENCE] == 0.0F,
			"al principio 1/8 cada causa y confianza 0");
		Zombie open = zombie(helper, new BlockPos(3, 1, 3));
		Zombie shot = zombie(helper, new BlockPos(2, 1, 5));
		Zombie burnt = zombie(helper, new BlockPos(2, 1, 1));
		for (Zombie z : List.of(open, shot, burnt)) {
			z.setTarget(player);
		}
		helper.runAfterDelay(3, () -> {
			var level = helper.getLevel();
			// Forja caps what one blow takes (hitCap): the death event is played with the killing blow's source, then a real
			// death (genericKill, a cause of no player's) shows the event itself counts
			WorldMemory.onDeath(open, level.damageSources().playerAttack(player));
			float[] one = WorldMemory.of(player);
			helper.assertTrue(Math.abs(one[WorldMemory.OPEN] - (0.125F + 0.1F * 0.875F)) < 1.0E-5, "golpe en llano: abierto sube a 0,2125: " + one[WorldMemory.OPEN] + " (vivo " + open.isAlive() + ", muerto " + open.isDeadOrDying()
				+ ", objetivo " + (MobAi.mind(open) == null ? null : MobAi.mind(open).target) + ", memoria " + player.getAttached(WorldMemory.MEMORY) + ")");
			Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
			WorldMemory.onDeath(shot, level.damageSources().arrow(arrow, player));
			WorldMemory.onDeath(burnt, level.damageSources().lava());
			open.hurtServer(level, level.damageSources().genericKill(), 10000.0F);
			helper.assertTrue(!open.isAlive() && WorldMemory.of(player)[WorldMemory.SIZE] == 4.0F, "la muerte de verdad también cuenta: "
				+ WorldMemory.of(player)[WorldMemory.SIZE]);
			float[] v = WorldMemory.of(player);
			helper.assertTrue(v[WorldMemory.ARROW] > 0.15F && v[WorldMemory.TRAP] > 0.15F, "flecha y trampa suben (y bajan un poco con cada muerte después): " + v[WorldMemory.ARROW] + " " + v[WorldMemory.TRAP]);
			helper.assertTrue(Math.abs(sum(v, WorldMemory.CAUSES, 8) - 1.0F) < 1.0E-5, "las causas suman 1: " + sum(v, WorldMemory.CAUSES, 8));
			helper.assertTrue(Math.abs(v[WorldMemory.CONFIDENCE] - 4.0F / 50.0F) < 1.0E-6, "confianza 4/50: " + v[WorldMemory.CONFIDENCE]);
			helper.assertTrue(v[WorldMemory.OTHER] > 0.15F, "la muerte sin jugador cuenta como otra: " + v[WorldMemory.OTHER]);
			Zombie watcher = zombie(helper, new BlockPos(1, 1, 3));
			watcher.setTarget(player);
			MobMind mind = MobAi.mind(watcher);
			float[] obs = ObsV4.build(watcher, player, mind);
			for (int i = 0; i < WorldMemory.SIZE; i++) {
				helper.assertTrue(Math.abs(obs[ObsV4.W_AT + i] - v[i]) < 1.0E-6, "el bloque W es el vector: " + ObsV4.names().get(ObsV4.W_AT + i));
			}
			watcher.discard();
			helper.succeed();
		});
	}

	/** What works: a mob that fought from the front and did 8 damage dies; mundo_exito_frente moves a tenth towards 0.8. */
	@GameTest(maxTicks = 40)
	public void whatWorksCountsTheDamage(GameTestHelper helper) {
		floor(helper);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(5, 1, 3));
		Zombie zombie = zombie(helper, new BlockPos(3, 1, 3));
		zombie.setTarget(player);
		helper.runAfterDelay(4, () -> {
			MobMind mind = MobAi.mind(zombie);
			helper.assertTrue(mind.styleTicks[WorldMemory.FRONT] > 0, "acercándose cuenta como de frente: " + java.util.Arrays.toString(mind.styleTicks));
			WorldMemory.onHit(zombie, 8.0F);
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 10000.0F);
			float[] v = WorldMemory.of(player);
			helper.assertTrue(Math.abs(v[WorldMemory.SUCCESS + WorldMemory.FRONT] - 0.08F) < 1.0E-5, "exito_frente = 0,1 · 0,8: " + v[WorldMemory.SUCCESS + WorldMemory.FRONT]);
			helper.assertTrue(v[WorldMemory.SUCCESS + WorldMemory.FLANK] == 0.0F, "el flanco no se toca");
			helper.succeed();
		});
	}

	/**
	 * The player's style: a fight in which they stood up a pillar for 2 seconds, then the group gone for 3 seconds (the
	 * fight is over): mundo_jug_pilar moves a tenth towards 1.
	 */
	@GameTest(maxTicks = 200)
	public void aFightUpAPillarIsRemembered(GameTestHelper helper) {
		floor(helper);
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(new BlockPos(5, y, 3), Blocks.STONE);
		}
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(5, 4, 3));
		Vec3 top = player.position();
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 3));
		helper.onEachTick(() -> {
			player.setPos(top.x, top.y, top.z);
			if (zombie.isAlive()) {
				zombie.setTarget(player);
			}
		});
		helper.runAfterDelay(70, zombie::discard);
		helper.succeedWhen(() -> {
			float[] v = WorldMemory.of(player);
			helper.assertTrue(Math.abs(v[WorldMemory.STYLE] - 0.1F) < 1.0E-5, "mundo_jug_pilar = 0,1 tras una pelea en un pilar: " + v[WorldMemory.STYLE]);
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(5, y, 3), Blocks.AIR);
			}
		});
	}

	/** Forgetting: two days on, every value is 0.81 of the way from where it started; clearing starts it all again. */
	@GameTest(maxTicks = 20)
	public void theWorldForgets(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(5, 1, 3));
		long today = helper.getLevel().getOverworldClockTime() / 24000L;
		float[] v = WorldMemory.start();
		v[WorldMemory.OPEN] = 0.9F;
		v[WorldMemory.ARROW] = 0.1F / 7.0F;
		v[WorldMemory.SUCCESS] = 1.0F;
		v[WorldMemory.SIZE] = 20.0F;
		v[WorldMemory.SIZE + 1] = today - 2;
		List<Float> list = new java.util.ArrayList<>();
		for (float f : v) {
			list.add(f);
		}
		player.setAttached(WorldMemory.MEMORY, Map.of(helper.getLevel().dimension().identifier().toString(), List.copyOf(list)));
		float[] now = WorldMemory.of(player);
		helper.assertTrue(Math.abs(now[WorldMemory.OPEN] - (0.125F + 0.81F * (0.9F - 0.125F))) < 1.0E-4, "abierto olvida: " + now[WorldMemory.OPEN]);
		helper.assertTrue(Math.abs(now[WorldMemory.SUCCESS] - 0.81F) < 1.0E-4, "exito olvida: " + now[WorldMemory.SUCCESS]);
		WorldMemory.clear(player);
		helper.assertTrue(Math.abs(WorldMemory.of(player)[WorldMemory.OPEN] - 0.125F) < 1.0E-6, "borrada vuelve al principio");
		helper.succeed();
	}
}
