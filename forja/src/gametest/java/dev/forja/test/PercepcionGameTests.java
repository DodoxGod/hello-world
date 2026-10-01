package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * What the monsters know and which brains they load, on the way to the v4 (docs/red_mob_v4_diseno.md §1.6 and §4.5;
 * Andy, 2026-09-29): a player they lost sight of is looked for where they were last seen, never found through a wall,
 * and a v4 network in redes_v4 that does not fit the v4 contract is refused, the family keeping its v3 or the rules
 * (the one that fits is RedV4GameTests').
 */
public class PercepcionGameTests {
	/**
	 * A zombie sees a player for five ticks, then the player is behind a stone wall with a way round it at the far end.
	 * Vanilla's path went to the real position, round the wall; now the zombie goes to where it last saw them and waits
	 * there, and in ten seconds never gets within two blocks of where the player really is.
	 */
	@GameTest(maxTicks = 260)
	public void lostPlayerIsSoughtWhereLastSeen(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		// The wall along x = 4, three high, open at z = 6 and 7: the way round it.
		for (int z = 0; z <= 5; z++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
			}
		}
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		// No idle stroll before its melee goal's first try (AlcanceGameTests.noStroll): a stroll changed the way it came
		// to where it last saw the player, BUSCAR's fan of points goes on the way it came, and one fan (3 runs of 400)
		// led it to the gap at the end of the wall, from where it saw the player again, honestly, and went on to them.
		AlcanceGameTests.noStroll(zombie);
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(helper.getLevel());
		Vec3 open = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 1, 5)));
		Vec3 hidden = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(6, 1, 1)));
		player.setPos(open.x, open.y, open.z);
		player.setHealth(player.getMaxHealth());
		helper.onEachTick(() -> player.setDeltaMovement(Vec3.ZERO));
		zombie.setTarget(player);
		MobMind mind = MobAi.mind(zombie);
		helper.assertTrue(mind != null, "el zombi debería tener cerebro");
		boolean[] moved = {false};
		Vec3[] seen = {null};
		helper.runAfterDelay(5, () -> {
			seen[0] = mind.lastSeen;
			helper.assertTrue(seen[0] != null, "a los 5 ticks el zombi ya debería haber visto al jugador");
			player.setPos(hidden.x, hidden.y, hidden.z);
			moved[0] = true;
		});
		helper.onEachTick(() -> {
			if (moved[0] && zombie.isAlive()) {
				helper.assertTrue(zombie.distanceToSqr(hidden.x, zombie.getY(), hidden.z) > 2.0 * 2.0,
					"el zombi llegó a menos de 2 bloques de la posición real del jugador, que no puede percibir: "
						+ zombie.position() + " (tick " + helper.getLevel().getGameTime() + ")");
			}
		});
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(mind.lastSeen != null && mind.lastSeen.distanceTo(seen[0]) < 0.5,
				"la última posición conocida debería seguir siendo donde lo vio, no " + mind.lastSeen);
			double off = Math.sqrt(zombie.distanceToSqr(seen[0].x, zombie.getY(), seen[0].z));
			helper.assertTrue(off < 2.5, "el zombi debería esperar donde lo vio por última vez; está a " + off + " bloques");
			zombie.discard();
			helper.succeed();
		});
	}

	/**
	 * A file in redes_v4 that says it is a v4 but has v2's inputs is refused (since M1 a v4 is run, if it fits ObsV4):
	 * the family keeps its v3 file, or the rules, and the reason is in problems(). The v4 captain is only noted (M5). A
	 * v4 file put in the v3 folder by mistake is refused by its formato, and with iaContrato "v3" redes_v4 is not even
	 * looked at.
	 */
	@GameTest
	public void wrongV4NetworkIsRefusedAndV3Kept(GameTestHelper helper) throws java.io.IOException {
		CombatConfig cfg = CombatConfig.get();
		String savedFolder = cfg.iaCarpetaRedes;
		String savedContract = cfg.iaContrato;
		Path root = Files.createTempDirectory("forja_redes");
		Path v3 = Files.createDirectories(root.resolve("redes"));
		Path v4 = Files.createDirectories(root.resolve("redes_v4"));
		com.google.gson.Gson gson = new com.google.gson.Gson();
		Files.writeString(v3.resolve("red_cuerpo.json"), gson.toJson(AiGameTests.fakeV2(new float[NetBrain.V2_OUTPUTS])));
		com.google.gson.JsonObject four = AiGameTests.fakeV2(new float[NetBrain.V2_OUTPUTS]);
		four.addProperty("formato", MobAi.V4_FORMAT);
		Files.writeString(v4.resolve("red_cuerpo.json"), gson.toJson(four));
		Files.writeString(v4.resolve("red_arquero.json"), gson.toJson(four));
		Files.writeString(v3.resolve("red_arana.json"), gson.toJson(four));
		com.google.gson.JsonObject captain = AiGameTests.fakeV2(new float[NetBrain.V2_OUTPUTS]);
		captain.addProperty("formato", "red_capitan_v4");
		Files.writeString(v4.resolve("red_" + MobAi.V4_CAPTAIN + ".json"), gson.toJson(captain));
		try {
			cfg.iaCarpetaRedes = v3.toAbsolutePath().toString();
			cfg.iaContrato = "auto";
			MobAi.reload();
			helper.assertTrue(MobAi.netFolderV4().equals(v4.toAbsolutePath()), "redes_v4 va junto a redes: " + MobAi.netFolderV4());
			helper.assertTrue(MobAi.v4Waiting().isEmpty() && MobAi.captainNet() == null
				&& String.valueOf(MobAi.problems().get(MobAi.V4_CAPTAIN)).startsWith("v4:"),
				"el capitán v4 que no encaja se rechaza y se anota (M5): " + MobAi.v4Waiting().keySet() + " " + MobAi.problems());
			NetBrain cuerpo = MobAi.net("cuerpo");
			helper.assertTrue(cuerpo != null && "red_mob_v2".equals(cuerpo.format),
				"el cuerpo debería seguir con su red v3 (aquí una v2), no la v4 que no encaja: " + (cuerpo == null ? null : cuerpo.format));
			helper.assertTrue(String.valueOf(MobAi.problems().get("cuerpo")).startsWith("v4:"),
				"el motivo del rechazo de la v4 debería quedar anotado: " + MobAi.problems().get("cuerpo"));
			helper.assertTrue(MobAi.net("arquero") == null && MobAi.problems().containsKey("arquero"),
				"el arquero, con solo una v4 que no encaja, debería pelear por reglas");
			helper.assertTrue(MobAi.checkV4(NetBrain.fromJson(four)) != null, "checkV4 no acepta una v4 con las entradas de la v2");
			helper.assertTrue(MobAi.net("arana") == null && MobAi.problems().containsKey("arana"),
				"una v4 en la carpeta de las v3 se rechaza por su formato");
			helper.assertTrue(MobAi.check(NetBrain.fromJson(four)) != null, "check no acepta una red v4 aunque sus nombres encajen");
			cfg.iaContrato = "v3";
			MobAi.reload();
			helper.assertTrue(MobAi.v4Waiting().isEmpty() && !MobAi.problems().containsKey("cuerpo"), "con iaContrato v3 no se mira redes_v4");
			helper.assertTrue(MobAi.net("cuerpo") != null, "con iaContrato v3 el cuerpo sigue con su red");
		} finally {
			cfg.iaCarpetaRedes = savedFolder;
			cfg.iaContrato = savedContract;
			MobAi.reload();
		}
		helper.succeed();
	}
}
