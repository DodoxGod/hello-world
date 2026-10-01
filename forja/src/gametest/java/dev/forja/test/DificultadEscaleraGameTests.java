package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import dev.forja.Forja;
import dev.forja.ai.CaptainBrain;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobSprint;
import dev.forja.ai.NetBrain;
import dev.forja.combat.AttackTokens;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.Posture;
import dev.forja.difficulty.ForjaDifficulty;
import dev.forja.difficulty.Ladder;
import dev.forja.difficulty.Pressure;
import dev.forja.difficulty.Threat;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The difficulty ladder (Andy, 2026-09-30; difficulty/Ladder): each level turns on exactly its systems, the run's bonus
 * is the level's share, Fácil softens the strong ones, the Extremo flag is the world's and survives a save, and
 * /forja dificultad sets both halves of it.
 *
 * <p>The level and the world's difficulty are the whole server's, and the tests of a batch run side by side: every test
 * here changes them and puts them back within the same tick, so no other test ever sees them changed.
 */
public class DificultadEscaleraGameTests {
	/** Runs {@code body} with the config forcing {@code level} (and the preset left to it), then puts the defaults back. */
	private static void at(Ladder level, Runnable body) {
		CombatConfig cfg = CombatConfig.get();
		String savedLevel = cfg.nivel;
		String savedPreset = cfg.dificultad;
		try {
			cfg.nivel = level.name();
			cfg.dificultad = "auto";
			body.run();
		} finally {
			cfg.nivel = savedLevel;
			cfg.dificultad = savedPreset;
		}
	}

	private static Zombie zombie(GameTestHelper helper, BlockPos at) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, at);
		zombie.setNoAi(true);
		return zombie;
	}

	private static float hit(LivingEntity target, Mob attacker, float amount) {
		float before = target.getHealth();
		target.invulnerableTime = 0;
		target.hurtServer(helperLevel(target), target.level().damageSources().mobAttack(attacker), amount);
		return before - target.getHealth();
	}

	private static net.minecraft.server.level.ServerLevel helperLevel(LivingEntity entity) {
		return (net.minecraft.server.level.ServerLevel) entity.level();
	}

	/**
	 * Each level, forced in turn: the brain on vanilla's monsters (and always on Forja's), warned blows, which network a
	 * family runs (none, v3, v4), the captain's network, the rules captain and captain 2, armor penetration and pressure,
	 * gear scaling and the run's bonus, against the table Andy decided.
	 */
	@GameTest
	public void eachLevelTurnsOnItsSystems(GameTestHelper helper) throws Exception {
		CombatConfig cfg = CombatConfig.get();
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		Mob slag = helper.spawn(ModEntities.ESCORIA, new BlockPos(3, 1, 1));
		slag.setNoAi(true);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 3));
		CombatGameTests.TestPlayer geared = CombatGameTests.player(helper, new BlockPos(3, 1, 3));
		geared.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(
			new AttributeModifier(Forja.id("prueba_escalera"), 20.0, AttributeModifier.Operation.ADD_VALUE));

		// a v3 network (redes) and a v4 one (redes_v4) for bodies, and a captain's network
		String savedFolder = cfg.iaCarpetaRedes;
		String savedContract = cfg.iaContrato;
		Path root = Files.createTempDirectory("forja_escalera");
		Path v3 = Files.createDirectories(root.resolve("redes"));
		Path v4 = Files.createDirectories(root.resolve("redes_v4"));
		Gson gson = new Gson();
		Files.writeString(v3.resolve("red_cuerpo.json"), gson.toJson(AiGameTests.fakeV2(new float[NetBrain.V2_OUTPUTS])));
		Files.writeString(v4.resolve("red_cuerpo.json"), gson.toJson(RedV4GameTests.fakeV4(7L, new float[0])));
		Files.writeString(v4.resolve("red_" + MobAi.V4_CAPTAIN + ".json"), gson.toJson(Capitan2GameTests.fakeCaptainV2(7L, new float[0], true)));
		StringBuilder wrong = new StringBuilder();
		try {
			cfg.iaCarpetaRedes = v3.toAbsolutePath().toString();
			cfg.iaContrato = "auto";
			MobAi.reload();
			//                      level            rules  pen    v3     v4     capNet cap2   gear   run
			Object[][] table = {
				{Ladder.PACIFICO, false, false, false, false, false, false, false, 1.14},
				{Ladder.FACIL,    false, false, false, false, false, false, false, 1.14},
				{Ladder.NORMAL,   true,  true,  false, false, false, false, true,  1.14},
				{Ladder.DIFICIL,  true,  true,  true,  false, false, false, true,  1.21},
				{Ladder.EXTREMO,  true,  true,  true,  true,  true,  true,  true,  1.315},
			};
			for (Object[] row : table) {
				Ladder level = (Ladder) row[0];
				at(level, () -> {
					boolean rules = (boolean) row[1];
					boolean penetration = (boolean) row[2];
					boolean netV3 = (boolean) row[3];
					boolean netV4 = (boolean) row[4];
					boolean captainNet = (boolean) row[5];
					boolean captain2 = (boolean) row[6];
					boolean gear = (boolean) row[7];
					double run = (double) row[8];
					String at = level.name() + ": ";
					check(wrong, Ladder.current() == level, at + "el nivel forzado manda: " + Ladder.current());
					check(wrong, MobAi.thinks(zombie) == rules, at + "cerebro en el zombi " + MobAi.thinks(zombie));
					check(wrong, MobAi.thinks(slag), at + "un monstruo de Forja piensa siempre");
					check(wrong, AttackTokens.warns(zombie) == rules, at + "avisos del zombi " + AttackTokens.warns(zombie));
					NetBrain body = MobAi.net("cuerpo");
					String format = body == null ? "reglas" : body.format;
					String expected = netV4 ? MobAi.V4_FORMAT : netV3 ? "red_mob_v2" : "reglas";
					check(wrong, expected.equals(format), at + "red del cuerpo " + format + ", se esperaba " + expected);
					check(wrong, (MobAi.captainNet() != null) == captainNet, at + "red del capitán " + MobAi.captainNet());
					check(wrong, CaptainBrain.enabled() == rules, at + "capitán de reglas " + CaptainBrain.enabled());
					check(wrong, CaptainBrain.piece(player, CaptainBrain.Piece.VISION) == captain2
						&& CaptainBrain.piece(player, CaptainBrain.Piece.SUCESION) == captain2
						&& CaptainBrain.piece(player, CaptainBrain.Piece.PROTECCION) == captain2, at + "capitán 2");
					check(wrong, level.penetration == penetration, at + "penetración " + level.penetration);
					Pressure.onHit(player);
					check(wrong, (Pressure.of(player) > 0.0) == penetration, at + "presión tras un golpe " + Pressure.of(player));
					check(wrong, (Ladder.gearTier(geared) > 0) == gear, at + "tramo de equipo " + Ladder.gearTier(geared));
					check(wrong, Math.abs(MobSprint.runMultiplier(false) - run) < 1.0E-9, at + "carrera x" + MobSprint.runMultiplier(false));
					check(wrong, level.aimInputs() == (level == Ladder.EXTREMO), at + "entradas de puntería v4.1");
				});
			}
		} finally {
			cfg.iaCarpetaRedes = savedFolder;
			cfg.iaContrato = savedContract;
			MobAi.reload();
		}
		helper.assertTrue(wrong.isEmpty(), wrong.toString());
		zombie.discard();
		slag.discard();
		helper.succeed();
	}

	private static void check(StringBuilder wrong, boolean ok, String what) {
		if (!ok) {
			wrong.append(what).append("; ");
		}
	}

	/**
	 * The run's bonus by level, the exact numbers: +35 % × 0,4 / 0,6 / 0,9 (Difícil +21 %, Extremo +31,5 %), walking
	 * untouched; and the modifier a running zombie gets on top of vanilla's sprint makes exactly that.
	 */
	@GameTest
	public void theRunBonusIsTheLevelsShare(GameTestHelper helper) {
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		double walk = zombie.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
		StringBuilder wrong = new StringBuilder();
		double[] expected = {1.14, 1.14, 1.14, 1.21, 1.315};
		for (Ladder level : Ladder.values()) {
			at(level, () -> {
				double total = MobSprint.VANILLA_SPRINT * (1.0 + MobSprint.extraAmount(false));
				check(wrong, Math.abs(total - expected[level.ordinal()]) < 1.0E-9, level + ": x" + total);
				double rodeo = MobSprint.VANILLA_SPRINT * (1.0 + MobSprint.extraAmount(true));
				check(wrong, Math.abs(rodeo - (1.0 + (CombatConfig.get().rodeoSpeed - 1.0) * level.sprint)) < 1.0E-9, level + ": cerco x" + rodeo);
			});
		}
		check(wrong, Math.abs(zombie.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - walk) < 1.0E-9, "andar no cambia");
		helper.assertTrue(wrong.isEmpty(), wrong.toString());
		zombie.discard();
		helper.succeed();
	}

	/**
	 * Fácil: the strong ones (a veteran, Forja's monsters) have 70 % of their posture bar and their run, and hit for 70 %;
	 * a plain vanilla zombie keeps vanilla's numbers. Against Normal, both with their own presets.
	 */
	@GameTest
	public void easySoftensTheStrongOnes(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		double vet = cfg.veteranChance;
		double elite = cfg.eliteChance;
		cfg.veteranChance = 0.0;
		cfg.eliteChance = 0.0;
		Zombie plain = zombie(helper, new BlockPos(1, 1, 1));
		Zombie veteran = zombie(helper, new BlockPos(3, 1, 1));
		cfg.veteranChance = vet;
		cfg.eliteChance = elite;
		Threat.VETERANO.mark(veteran);
		CombatGameTests.TestPlayer a = CombatGameTests.player(helper, new BlockPos(1, 1, 4));
		CombatGameTests.TestPlayer b = CombatGameTests.player(helper, new BlockPos(3, 1, 4));
		double[] posture = new double[4];
		float[] run = new float[2];
		float[] damage = new float[4];
		at(Ladder.NORMAL, () -> {
			posture[0] = Posture.max(veteran) / ForjaDifficulty.current().posture;
			posture[1] = Posture.max(plain) / ForjaDifficulty.current().posture;
			damage[0] = hit(a, veteran, 4.0F);
			damage[1] = hit(b, plain, 4.0F);
		});
		a.setHealth(a.getMaxHealth());
		b.setHealth(b.getMaxHealth());
		at(Ladder.FACIL, () -> {
			posture[2] = Posture.max(veteran) / ForjaDifficulty.current().posture;
			posture[3] = Posture.max(plain) / ForjaDifficulty.current().posture;
			run[0] = MobSprint.max(veteran);
			run[1] = MobSprint.max(plain);
			damage[2] = hit(a, veteran, 4.0F);
			damage[3] = hit(b, plain, 4.0F);
		});
		helper.assertTrue(Math.abs(posture[2] / posture[0] - 0.7) < 1.0E-6, "postura del veterano en Fácil: x" + posture[2] / posture[0]);
		helper.assertTrue(Math.abs(posture[3] / posture[1] - 1.0) < 1.0E-6, "postura del zombi corriente en Fácil: x" + posture[3] / posture[1]);
		helper.assertTrue(Math.abs(run[0] - 70.0F) < 1.0E-4 && Math.abs(run[1] - MobSprint.MAX) < 1.0E-4,
			"aliento en Fácil: veterano " + run[0] + ", corriente " + run[1]);
		helper.assertTrue(Math.abs(damage[2] / damage[0] - 0.7) < 0.02, "daño del veterano en Fácil: x" + damage[2] / damage[0]
			+ " (" + damage[2] + " contra " + damage[0] + ")");
		helper.assertTrue(Math.abs(damage[3] / damage[1] - 1.0) < 0.02, "el zombi corriente pega como en vanilla: x" + damage[3] / damage[1]);
		helper.assertTrue(Ladder.mobDamage(veteran) > 0.0, "sin efectos fuera de la prueba");
		plain.discard();
		veteran.discard();
		helper.succeed();
	}

	/**
	 * The Extremo flag is the world's: /forja dificultad extremo (or the button) puts the world on HARD with the flag; the
	 * flag goes into the world's game rules, which come back the same from what is saved (their codec, as level.dat
	 * keeps them); a locked difficulty stops the button but not the command; /difficulty hard and anything off HARD
	 * take the flag off.
	 */
	@GameTest
	public void theExtremoFlagIsTheWorlds(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Difficulty before = server.getWorldData().getDifficulty();
		boolean locked = server.getWorldData().isDifficultyLocked();
		StringBuilder wrong = new StringBuilder();
		try {
			Ladder.apply(server, Ladder.EXTREMO, true);
			check(wrong, server.getWorldData().getDifficulty() == Difficulty.HARD && Ladder.extremo(server), "extremo: HARD con la bandera");
			check(wrong, Ladder.world(server) == Ladder.EXTREMO, "el mundo está en " + Ladder.world(server));
			// saved and read back as level.dat does
			GameRules rules = server.getGameRules();
			var features = server.getWorldData().enabledFeatures();
			Tag saved = GameRules.codec(features).encodeStart(NbtOps.INSTANCE, rules).getOrThrow();
			GameRules read = GameRules.codec(features).parse(NbtOps.INSTANCE, saved).getOrThrow();
			check(wrong, read.get(Ladder.EXTREMO_RULE), "la bandera vuelve de lo guardado: " + saved);
			// the button's way on a locked world changes nothing
			server.setDifficultyLocked(true);
			Ladder.apply(server, Ladder.FACIL, false);
			check(wrong, Ladder.world(server) == Ladder.EXTREMO, "con la dificultad bloqueada el botón no cambia nada: " + Ladder.world(server));
			// /difficulty hard: Difícil
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "difficulty hard");
			check(wrong, server.getWorldData().getDifficulty() == Difficulty.HARD && !Ladder.extremo(server),
				"/difficulty hard deja Difícil: " + Ladder.world(server));
			Ladder.apply(server, Ladder.EXTREMO, true);
			// anything off HARD takes the flag
			server.setDifficulty(Difficulty.NORMAL, true);
			check(wrong, !Ladder.extremo(server), "fuera de HARD no queda bandera");
			GameRules after = GameRules.codec(features).parse(NbtOps.INSTANCE,
				GameRules.codec(features).encodeStart(NbtOps.INSTANCE, server.getGameRules()).getOrThrow()).getOrThrow();
			check(wrong, !after.get(Ladder.EXTREMO_RULE), "y lo guardado tampoco la lleva");
		} finally {
			server.getGameRules().set(Ladder.EXTREMO_RULE, false, server);
			server.setDifficulty(before, true);
			server.setDifficultyLocked(locked);
		}
		helper.assertTrue(wrong.isEmpty(), wrong.toString());
		helper.succeed();
	}

	/** /forja dificultad <nivel> sets the world's difficulty and flag together; an unknown name changes nothing. */
	@GameTest
	public void theCommandSetsBoth(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Difficulty before = server.getWorldData().getDifficulty();
		StringBuilder wrong = new StringBuilder();
		try {
			var source = server.createCommandSourceStack().withSuppressedOutput();
			Object[][] cases = {
				{"extremo", Difficulty.HARD, true},
				{"facil", Difficulty.EASY, false},
				{"dificil", Difficulty.HARD, false},
				{"extremo", Difficulty.HARD, true},
				{"normal", Difficulty.NORMAL, false},
				{"pacifico", Difficulty.PEACEFUL, false},
			};
			for (Object[] c : cases) {
				server.getCommands().performPrefixedCommand(source, "forja dificultad " + c[0]);
				check(wrong, server.getWorldData().getDifficulty() == c[1] && Ladder.extremo(server) == (boolean) c[2],
					"/forja dificultad " + c[0] + " → " + server.getWorldData().getDifficulty() + " bandera " + Ladder.extremo(server));
			}
			server.getCommands().performPrefixedCommand(source, "forja dificultad extremo");
			server.getCommands().performPrefixedCommand(source, "forja dificultad leyenda");
			check(wrong, Ladder.world(server) == Ladder.EXTREMO, "un nombre que no existe no cambia nada: " + Ladder.world(server));
		} finally {
			server.getGameRules().set(Ladder.EXTREMO_RULE, false, server);
			server.setDifficulty(before, true);
		}
		helper.assertTrue(wrong.isEmpty(), wrong.toString());
		helper.succeed();
	}
}
