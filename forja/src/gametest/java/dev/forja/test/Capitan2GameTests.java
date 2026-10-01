package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.forja.ai.Captain;
import dev.forja.ai.CaptainBrain;
import dev.forja.ai.Decision;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsV4;
import dev.forja.ai.Perception;
import dev.forja.ai.Tactic;
import dev.forja.combat.AttackTokens;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Captain 2 (docs/mod_spec_capitan2.md; docs/red_mob_v4_mod_estado.md, "Capitán 2"): shared vision, the acting veteran
 * (succession), the captain's protection and escorts, the new orders of contract revision 2 (CERRAR_SALIDAS,
 * FOCO_HERIDO, RETIRADA_FALSA) and the v2 captain network with its mando head (docs/red_capitan_v4_contrato_v2.json).
 * The tests with a network loaded each run in an environment of their own: the network is every group's while it lasts.
 */
public class Capitan2GameTests {
	private static final EnumSet<CaptainBrain.Piece> ALL = EnumSet.allOf(CaptainBrain.Piece.class);
	private static final EnumSet<CaptainBrain.Piece> NONE = EnumSet.noneOf(CaptainBrain.Piece.class);

	private static Zombie zombie(GameTestHelper helper, BlockPos at) {
		return RedV4CapitanGameTests.zombie(helper, at);
	}

	private static Zombie elite(GameTestHelper helper, BlockPos at) {
		Zombie elite = zombie(helper, at);
		Threat.ELITE.mark(elite);
		return elite;
	}

	/** Each tick: the player where they stood, whole; the mobs whole and after them (all but {@code free}). */
	private static void hold(GameTestHelper helper, Player player, List<Mob> mobs, Mob free) {
		Vec3 at = player.position();
		helper.onEachTick(() -> {
			player.setPos(at.x, at.y, at.z);
			player.setHealth(player.getMaxHealth());
			for (Mob mob : mobs) {
				if (mob.isAlive() && mob != free) {
					mob.setTarget(player);
					mob.setHealth(mob.getMaxHealth());
				}
			}
		});
	}

	private static void finish(GameTestHelper helper, int size, Player... players) {
		for (Player player : players) {
			CaptainBrain.override(player, null);
			CaptainBrain.overridePieces(player, null);
			Captain.forget(player);
			if (player instanceof net.minecraft.server.level.ServerPlayer sp && helper.getLevel().players().contains(sp)) {
				helper.getLevel().removePlayerImmediately(sp, Entity.RemovalReason.DISCARDED);
			}
		}
		RedV4CapitanGameTests.clear(helper, size);
		helper.succeed();
	}

	// ---------------------------------------------------------------- 1. shared vision

	/**
	 * Shared vision: a zombie shut in a stone box never perceives the player, but its captain's group does; at the next
	 * pass it gets where they were seen as a sound heard (obj_oido, its estimate there, compartida counts it). With the
	 * piece off, a zombie shut in the same way knows nothing.
	 */
	@GameTest(padding = 24, maxTicks = 80)
	public void sharedVisionReachesTheBlind(GameTestHelper helper) {
		RedV4CapitanGameTests.floor(helper, 32);
		CombatGameTests.TestPlayer on = CombatGameTests.player(helper, new BlockPos(8, 1, 8));
		CombatGameTests.TestPlayer off = CombatGameTests.player(helper, new BlockPos(24, 1, 8));
		CaptainBrain.overridePieces(on, ALL);
		CaptainBrain.overridePieces(off, NONE);
		CaptainBrain.override(on, CaptainBrain.Mode.REGLAS);
		CaptainBrain.override(off, CaptainBrain.Mode.REGLAS);
		List<Mob> blind = new ArrayList<>();
		List<Mob> all = new ArrayList<>();
		for (int side = 0; side < 2; side++) {
			int x = 8 + 16 * side;
			Player player = side == 0 ? on : off;
			// a stone box 4 from the player: the zombie in it never sees them
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int y = 1; y <= 3; y++) {
						if (dx != 0 || dz != 0 || y == 3) {
							helper.setBlock(new BlockPos(x + dx, y, 13 + dz), Blocks.STONE);
						}
					}
				}
			}
			Zombie shut = zombie(helper, new BlockPos(x, 1, 13));
			blind.add(shut);
			List<Mob> mobs = new ArrayList<>(List.of(elite(helper, new BlockPos(x + 4, 1, 8)), zombie(helper, new BlockPos(x - 4, 1, 8)),
				zombie(helper, new BlockPos(x, 1, 4)), shut));
			for (Mob mob : mobs) {
				mob.setTarget(player);
			}
			hold(helper, player, mobs, null);
			all.addAll(mobs);
		}
		int[] shared = {0, 0};
		helper.onEachTick(() -> {
			Captain.Group g = Captain.group(on);
			shared[0] = Math.max(shared[0], g == null ? 0 : g.shared);
			Captain.Group o = Captain.group(off);
			shared[1] = Math.max(shared[1], o == null ? 0 : o.shared);
		});
		helper.runAfterDelay(60, () -> {
			long now = helper.getLevel().getGameTime();
			MobMind mind = MobAi.mind(blind.get(0));
			helper.assertTrue(mind.perceivedAt < now - 40, "el encerrado no ve al jugador");
			helper.assertTrue(mind.lastHeard != null && mind.lastHeard.distanceTo(on.position()) < 1.0 && now - mind.lastHeardAt < 20,
				"le llega dónde lo vio el grupo: " + mind.lastHeard + " hace " + (now - mind.lastHeardAt));
			Vec3 estimate = Perception.estimate(mind);
			helper.assertTrue(estimate != null && estimate.distanceTo(on.position()) < 1.0, "su estimación es la del grupo: " + estimate);
			float[] obs = ObsV4.build(blind.get(0), on, mind);
			helper.assertTrue(obs[ObsV4.P_AT + 1] == 1.0F, "obj_oido = 1");
			helper.assertTrue(shared[0] >= 1, "compartida cuenta al encerrado: " + shared[0]);
			helper.assertTrue(shared[1] == 0, "sin la pieza, nadie recibe nada: " + shared[1]);
			finish(helper, 32, on, off);
		});
	}

	// ---------------------------------------------------------------- 2. succession

	/**
	 * Succession (Andy, 2026-09-30): the elite captain dies; 60 ticks later the group's veteran with the most health leads
	 * as an acting captain. It decides on the passes with now % 20 == 0, never orders CARGA though the rules want one (the
	 * player has a shield up: exposed), and the group's morale is 0.15 lower while it leads. When it dies, the other
	 * veteran does not take over (one succession a fight).
	 */
	@GameTest(padding = 24, maxTicks = 320)
	public void aVeteranTakesCommandWithoutCharges(GameTestHelper helper) {
		RedV4CapitanGameTests.floor(helper, 24);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(8, 1, 8));
		CaptainBrain.overridePieces(player, ALL);
		CaptainBrain.override(player, CaptainBrain.Mode.REGLAS);
		// a shield up: the rules see the player exposed and want to charge, and the blows are blocked
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		player.startUsingItem(InteractionHand.OFF_HAND);
		Zombie elite = elite(helper, new BlockPos(20, 1, 8));
		Zombie veteran = zombie(helper, new BlockPos(4, 1, 8));
		Threat.VETERANO.mark(veteran);
		veteran.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(30.0);
		Zombie second = zombie(helper, new BlockPos(8, 1, 12));
		Threat.VETERANO.mark(second);
		List<Mob> mobs = List.of(elite, veteran, second, zombie(helper, new BlockPos(8, 1, 4)), zombie(helper, new BlockPos(5, 1, 5)),
			zombie(helper, new BlockPos(11, 1, 11)), zombie(helper, new BlockPos(11, 1, 5)), zombie(helper, new BlockPos(5, 1, 11)));
		for (Mob mob : mobs) {
			mob.setTarget(player);
		}
		Vec3 at = player.position();
		helper.onEachTick(() -> {
			player.setPos(at.x, at.y, at.z);
			player.setHealth(player.getMaxHealth());
			if (!player.isUsingItem()) {
				player.startUsingItem(InteractionHand.OFF_HAND);
			}
			for (Mob mob : mobs) {
				if (mob.isAlive()) {
					mob.setTarget(player);
					mob.setHealth(mob.getMaxHealth());
				}
			}
		});
		long[] died = {-1};
		long[] took = {-1};
		String[] wrong = {null};
		int[] rulesCharge = {0};
		int[] interimPasses = {0};
		long[] lastGiven = {Long.MIN_VALUE};
		helper.runAfterDelay(25, () -> {
			elite.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 10000.0F);
			died[0] = helper.getLevel().getGameTime();
			// a fresh rest between charges: the rules may want one again at once (the elite may have charged already)
			Captain.group(player).lastChargeAt = Long.MIN_VALUE / 2;
		});
		helper.onEachTick(() -> {
			Captain.Group g = Captain.group(player);
			long now = helper.getLevel().getGameTime();
			if (g == null || died[0] < 0) {
				return;
			}
			if (g.captain == veteran && g.interim && took[0] < 0) {
				took[0] = now;
			}
			if (g.interim && g.captain != null) {
				if (g.command.order == Captain.Order.CARGA) {
					wrong[0] = "un interino dio CARGA";
				}
				if (Captain.rules(g, player, now).order == Captain.Order.CARGA) {
					rulesCharge[0]++;
				}
				if (g.command.givenAt != lastGiven[0]) {
					lastGiven[0] = g.command.givenAt;
					if (g.command.givenAt > died[0] && g.command.givenAt % Captain.INTERIM_EVERY != 0) {
						wrong[0] = "orden nueva fuera de su pase: " + g.command.givenAt;
					}
				}
				interimPasses[0]++;
			}
		});
		helper.runAfterDelay(25 + 60 + 40, () -> {
			long now = helper.getLevel().getGameTime();
			Captain.Group g = Captain.group(player);
			helper.assertTrue(took[0] >= died[0] + Captain.SUCCESSION && took[0] <= died[0] + Captain.SUCCESSION + 10,
				"el veterano de más vida manda a los 60 ticks: murió en " + died[0] + ", manda desde " + took[0]);
			helper.assertTrue(g.captain == veteran && g.interim, "el interino es el veterano de 30 de vida");
			MobMind member = MobAi.mind(mobs.get(3));
			double with = Captain.groupMorale(member, player, now);
			g.interim = false;
			double without = Captain.groupMorale(member, player, now);
			g.interim = true;
			helper.assertTrue(Math.abs(without - with - Captain.INTERIM_MORALE) < 1.0E-6, "la moral lleva −0,15: " + with + " / " + without);
			float[] obs = ObsV4.build(mobs.get(3), player, member);
			helper.assertTrue(obs[ObsV4.M_AT] == 1.0F, "tengo_capitan = 1 con el interino");
			float[] captain = CaptainBrain.observeV2(g, player, now);
			helper.assertTrue(captain[CaptainBrain.C2_AT + 4] == 1.0F && captain[CaptainBrain.C2_AT + 5] > 0.0F,
				"capitan_interino = 1 y sin_mando sigue bajando: " + captain[CaptainBrain.C2_AT + 5]);
			veteran.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 10000.0F);
		});
		helper.runAfterDelay(25 + 60 + 40 + 100, () -> {
			Captain.Group g = Captain.group(player);
			helper.assertTrue(wrong[0] == null, String.valueOf(wrong[0]));
			helper.assertTrue(rulesCharge[0] > 0 && interimPasses[0] > 0, "las reglas querían cargar (" + rulesCharge[0] + ") y el interino no");
			helper.assertTrue(g.captain == null, "muerto el interino, no hay segunda sucesión: " + g.captain);
			// (the other veteran, if it still stands, is not captain either: nobody is)
			player.stopUsingItem();
			finish(helper, 24, player);
		});
	}

	// ---------------------------------------------------------------- 3. protection

	/**
	 * The captain's protection, first with every mob still (the geometry exactly): with four that fight up close besides
	 * it, its point is 7 from the player towards the middle of the others, in the reserve post; with the player within 10,
	 * two escorts without a turn, the one with a shield first, 2 ahead of it and 1 to each side (the first on the right),
	 * in the front post. Under 35 % health, 14 from the player on its side. Then moving: it keeps 5–9 from the player
	 * (its rules say FORMACION). With the piece off, no guard and no escorts.
	 */
	@GameTest(padding = 24, maxTicks = 260)
	public void theCaptainStaysBehindWithEscorts(GameTestHelper helper) {
		RedV4CapitanGameTests.floor(helper, 32);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(16, 1, 16));
		CaptainBrain.overridePieces(player, ALL);
		CaptainBrain.override(player, CaptainBrain.Mode.REGLAS);
		Zombie elite = elite(helper, new BlockPos(20, 1, 16));
		Zombie shield = zombie(helper, new BlockPos(13, 1, 13));
		shield.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		Zombie near = zombie(helper, new BlockPos(21, 1, 18));
		List<Mob> mobs = new ArrayList<>(List.of(elite, shield, near, zombie(helper, new BlockPos(12, 1, 19)), zombie(helper, new BlockPos(16, 1, 11))));
		for (Mob mob : mobs) {
			mob.setNoAi(true);
			mob.setTarget(player);
		}
		Vec3 at = player.position();
		boolean[] hurt = {false};
		helper.onEachTick(() -> {
			player.setPos(at.x, at.y, at.z);
			player.setHealth(player.getMaxHealth());
			for (Mob mob : mobs) {
				mob.setTarget(player);
				if (!(hurt[0] && mob == elite)) {
					mob.setHealth(mob.getMaxHealth());
				}
			}
		});
		// the elite comes near once it leads (an elite within 10 of a player may offer a duel before it is captain)
		helper.runAfterDelay(12, () -> {
			Vec3 close = helper.absoluteVec(new Vec3(20.5, 1.0, 16.5));
			elite.setPos(close.x, close.y, close.z);
		});
		helper.runAfterDelay(25, () -> {
			Captain.Group g = Captain.group(player);
			helper.assertTrue(g != null && g.captain == elite, "el élite manda");
			helper.assertTrue(g.guard == Captain.GUARD_BEHIND_FRONT, "tras el frente: " + g.guard + " (miembros " + g.members.size() + ", cuerpo "
				+ Captain.meleeCount(g) + ", protección " + g.protection + ", pieza " + CaptainBrain.piece(player, CaptainBrain.Piece.PROTECCION)
				+ ", vida " + elite.getHealth() + ", visto " + (helper.getLevel().getGameTime() - g.seenAt) + ")");
			MobMind cm = MobAi.mind(elite);
			double mx = 0.0;
			double mz = 0.0;
			for (Mob mob : mobs.subList(1, mobs.size())) {
				mx += mob.getX() / 4.0;
				mz += mob.getZ() / 4.0;
			}
			Vec3 toGroup = new Vec3(mx - at.x, 0.0, mz - at.z).normalize();
			Vec3 expected = at.add(toGroup.scale(Captain.GUARD_BEHIND));
			helper.assertTrue(cm.post == Captain.RESERVA && cm.postPoint.distanceTo(expected) < 1.0E-6,
				"su punto, a 7 hacia el centro de los demás: " + cm.postPoint + " / " + expected);
			List<Mob> escorts = mobs.stream().filter(m -> MobAi.mind(m).escort).toList();
			helper.assertTrue(escorts.size() == 2, "dos escoltas: " + escorts.size());
			Vec3 ahead = at.subtract(elite.position()).multiply(1.0, 0.0, 1.0).normalize();
			Vec3 right = new Vec3(-ahead.z, 0.0, ahead.x);
			Vec3 first = elite.position().add(ahead.scale(2.0)).add(right);
			Vec3 other = elite.position().add(ahead.scale(2.0)).subtract(right);
			MobMind sm = MobAi.mind(shield);
			MobMind nm = MobAi.mind(near);
			helper.assertTrue(sm.escort && sm.post == Captain.FRENTE && Math.hypot(sm.postPoint.x - first.x, sm.postPoint.z - first.z) < 1.0E-6,
				"el del escudo, primero y a la derecha: " + sm.postPoint + " / " + first);
			helper.assertTrue(nm.escort && Math.hypot(nm.postPoint.x - other.x, nm.postPoint.z - other.z) < 1.0E-6,
				"el más cercano al capitán, el segundo, a la izquierda: " + nm.postPoint + " / " + other);
			float[] obs = CaptainBrain.observeV2(g, player, helper.getLevel().getGameTime());
			helper.assertTrue(obs[CaptainBrain.C2_AT + 17] == 1.0F, "proteccion_regla = 1");
			// under 35 %: back to 14
			hurt[0] = true;
			elite.setHealth(elite.getMaxHealth() * 0.3F);
		});
		helper.runAfterDelay(40, () -> {
			Captain.Group g = Captain.group(player);
			MobMind cm = MobAi.mind(elite);
			Vec3 away = elite.position().subtract(at).multiply(1.0, 0.0, 1.0).normalize();
			Vec3 expected = at.add(away.scale(Captain.GUARD_RETREAT));
			helper.assertTrue(g.guard == Captain.GUARD_RETREATING && Math.hypot(cm.postPoint.x - expected.x, cm.postPoint.z - expected.z) < 1.0E-6,
				"con menos del 35 %, a 14 por su lado: " + cm.postPoint + " / " + expected);
			helper.assertFalse(AttackTokens.holds(player, elite), "y suelta su turno");
			hurt[0] = false;
			// the captain moves, the others stay put: their middle, and so its point, stays where it is (with the others
			// fighting all round the player their middle falls near the player and the point swings round: see the doc)
			// and it starts on the right side, 4 from the player, 3 short of its point
			double mx = 0.0;
			double mz = 0.0;
			for (Mob mob : mobs.subList(1, mobs.size())) {
				mx += mob.getX() / 4.0;
				mz += mob.getZ() / 4.0;
			}
			Vec3 toGroup = new Vec3(mx - at.x, 0.0, mz - at.z).normalize();
			Vec3 start = at.add(toGroup.scale(4.0));
			elite.setPos(start.x, at.y, start.z);
			elite.setNoAi(false);
		});
		// the last 40 ticks before 200: it has gone round the player to its point, and holds there
		int[] formation = {0};
		double[] far = {0.0, 0.0};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			MobMind cm = MobAi.mind(elite);
			if (t > 160 && t <= 200 && cm != null && cm.postPoint != null) {
				formation[0] += cm.decision.tactic() == Tactic.FORMACION ? 1 : 0;
				far[0] += elite.distanceTo(player) / 40.0;
				far[1] += Math.hypot(cm.postPoint.x - elite.getX(), cm.postPoint.z - elite.getZ()) / 40.0;
			}
		});
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(formation[0] > 30, "sus reglas dicen FORMACION: " + formation[0] + " de 40 ticks");
			helper.assertTrue(far[1] < 1.5 && far[0] >= 6.0 && far[0] <= 8.0, "llega a su punto y se queda a 7 del jugador: a su punto " + far[1]
				+ ", al jugador " + far[0]);
			CaptainBrain.overridePieces(player, NONE);
		});
		helper.runAfterDelay(225, () -> {
			Captain.Group g = Captain.group(player);
			helper.assertTrue(g.guard == Captain.GUARD_NONE && mobs.stream().noneMatch(m -> MobAi.mind(m).escort), "sin la pieza, nada");
			finish(helper, 32, player);
		});
	}

	// ---------------------------------------------------------------- 4. contract revision 2

	/** The contract revision 2's file (docs/red_capitan_v4_contrato_v2.json), found walking up from the game. */
	private static Path contractV2() {
		for (Path start : List.of(FabricLoader.getInstance().getGameDir().toAbsolutePath(), Path.of("").toAbsolutePath())) {
			for (Path at = start; at != null; at = at.getParent()) {
				for (String relative : new String[] {"docs/red_capitan_v4_contrato_v2.json", "forja/docs/red_capitan_v4_contrato_v2.json"}) {
					if (Files.exists(at.resolve(relative))) {
						return at.resolve(relative);
					}
				}
			}
		}
		return null;
	}

	/**
	 * The 253 input names and the 68 outputs of CaptainBrain's revision 2 are the contract's, in order; its heads' indices
	 * too. The 253 inputs of a real group are finite, and the rules' order is one-hot in its three blocks.
	 */
	@GameTest(padding = 16, maxTicks = 60)
	public void v2NamesMatchTheContract(GameTestHelper helper) throws java.io.IOException {
		Path file = contractV2();
		helper.assertTrue(file != null, "no se encuentra docs/red_capitan_v4_contrato_v2.json");
		JsonObject contract = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		List<String> theirs = new ArrayList<>();
		contract.getAsJsonArray("nombres_obs").forEach(n -> theirs.add(n.getAsString()));
		helper.assertTrue(contract.get("n_obs").getAsInt() == CaptainBrain.SIZE_V2 && theirs.size() == CaptainBrain.SIZE_V2, "253 entradas");
		for (int i = 0; i < theirs.size(); i++) {
			helper.assertTrue(theirs.get(i).equals(CaptainBrain.namesV2().get(i)),
				"la entrada " + i + ": el contrato dice '" + theirs.get(i) + "' y el mod '" + CaptainBrain.namesV2().get(i) + "'");
		}
		helper.assertTrue(theirs.subList(0, CaptainBrain.SIZE).equals(CaptainBrain.names()), "las 213 primeras, las de v1");
		List<String> outs = new ArrayList<>();
		contract.getAsJsonArray("salidas").forEach(n -> outs.add(n.getAsString()));
		helper.assertTrue(outs.equals(CaptainBrain.outputsV2()) && contract.get("n_salidas").getAsInt() == CaptainBrain.OUTPUTS_V2,
			"las 68 salidas: " + CaptainBrain.outputsV2());
		int[][] heads = {{0, 12}, {CaptainBrain.FORMATION_AT_V2, 4}, {CaptainBrain.SECTOR_AT_V2, 9}, {CaptainBrain.COUNT_AT_V2, 4},
			{CaptainBrain.FOCUS_AT_V2, 2}};
		for (int[] head : heads) {
			boolean found = false;
			for (var h : contract.getAsJsonArray("cabezas")) {
				JsonArray idx = h.getAsJsonObject().getAsJsonArray("indices");
				found |= idx.get(0).getAsInt() == head[0] && idx.size() == head[1];
			}
			helper.assertTrue(found, "una cabeza empieza en " + head[0] + " con " + head[1]);
		}
		for (var h : contract.getAsJsonArray("cabezas")) {
			JsonObject o = h.getAsJsonObject();
			String name = o.get("nombre").getAsString();
			int first = o.getAsJsonArray("indices").get(0).getAsInt();
			if (name.equals("proteccion")) {
				helper.assertTrue(first == CaptainBrain.PROTECTION_AT, "proteccion en " + first);
			} else if (name.equals("mando")) {
				helper.assertTrue(first == CaptainBrain.MANDO_AT, "mando en " + first);
			} else if (name.equals("puesto0")) {
				helper.assertTrue(first == CaptainBrain.POSTS_AT_V2, "puesto0 en " + first);
			}
		}
		// a real group's 253 inputs
		RedV4CapitanGameTests.floor(helper, 16);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(8, 1, 8));
		CaptainBrain.override(player, CaptainBrain.Mode.REGLAS);
		List<Mob> mobs = List.of(elite(helper, new BlockPos(12, 1, 8)), zombie(helper, new BlockPos(4, 1, 8)), zombie(helper, new BlockPos(8, 1, 12)));
		for (Mob mob : mobs) {
			mob.setTarget(player);
		}
		hold(helper, player, mobs, null);
		helper.runAfterDelay(25, () -> {
			Captain.Group g = Captain.group(player);
			long now = helper.getLevel().getGameTime();
			float[] obs = CaptainBrain.observeV2(g, player, now);
			helper.assertTrue(obs.length == CaptainBrain.SIZE_V2, "253: " + obs.length);
			for (int i = 0; i < obs.length; i++) {
				helper.assertTrue(Float.isFinite(obs[i]), "entrada " + i + " (" + CaptainBrain.namesV2().get(i) + ") no finita");
			}
			int[][] blocks = {{CaptainBrain.RULE_AT, 12}, {CaptainBrain.RULE_AT + 12, 4}, {CaptainBrain.RULE_AT + 16, 4}, {CaptainBrain.C2_AT + 17, 3}};
			for (int[] b : blocks) {
				float sum = 0.0F;
				for (int k = 0; k < b[1]; k++) {
					sum += obs[b[0] + k];
				}
				helper.assertTrue(sum == 1.0F, "one-hot en " + b[0] + ": " + sum);
			}
			Captain.Command rules = CaptainBrain.ruleOrder(g, player, now);
			helper.assertTrue(obs[CaptainBrain.RULE_AT + rules.order.ordinal()] == 1.0F, "regla_orden_" + rules.order);
			helper.assertTrue(obs[CaptainBrain.C2_AT + 16] == player.getHealth() / player.getMaxHealth(), "mi_jug_vida_frac: " + obs[CaptainBrain.C2_AT + 16]);
			finish(helper, 16, player);
		});
	}

	// ---------------------------------------------------------------- networks of revision 2

	/**
	 * A v2 captain network with its size and names ("contrato_version": 2) and output biases as given. With
	 * {@code residual}, as the simulator starts one: the mando rows at 0 and its bias (+6, 0), so mando is always 0.
	 */
	static JsonObject fakeCaptainV2(long seed, float[] outBias, boolean residual) {
		List<String> names = CaptainBrain.namesV2();
		Random random = new Random(seed);
		JsonObject json = new JsonObject();
		json.addProperty("formato", CaptainBrain.FORMAT);
		json.addProperty("contrato_version", 2);
		json.addProperty("grupo", "capitan");
		json.addProperty("ticks_por_decision", 10);
		JsonArray namesJson = new JsonArray();
		names.forEach(namesJson::add);
		json.add("nombres_obs", namesJson);
		int hidden = 8;
		int memory = 6;
		json.add("w1", RedV4CapitanGameTests.noise(random, names.size(), hidden));
		json.add("b1", RedV4CapitanGameTests.noise(random, 1, hidden).get(0));
		json.add("w2", RedV4CapitanGameTests.noise(random, hidden, hidden));
		json.add("b2", RedV4CapitanGameTests.noise(random, 1, hidden).get(0));
		json.add("gru_ih", RedV4CapitanGameTests.noise(random, 3 * memory, hidden));
		json.add("gru_hh", RedV4CapitanGameTests.noise(random, 3 * memory, memory));
		json.add("gru_bih", RedV4CapitanGameTests.noise(random, 1, 3 * memory).get(0));
		json.add("gru_bhh", RedV4CapitanGameTests.noise(random, 1, 3 * memory).get(0));
		JsonArray out = RedV4CapitanGameTests.noise(random, hidden + memory, CaptainBrain.OUTPUTS_V2);
		if (residual) {
			for (var row : out) {
				row.getAsJsonArray().set(CaptainBrain.MANDO_AT, new com.google.gson.JsonPrimitive(0.0F));
				row.getAsJsonArray().set(CaptainBrain.MANDO_AT + 1, new com.google.gson.JsonPrimitive(0.0F));
			}
		}
		json.add("w_out", out);
		JsonArray bias = new JsonArray();
		for (int k = 0; k < CaptainBrain.OUTPUTS_V2; k++) {
			float b = k < outBias.length ? outBias[k] : 0.0F;
			if (residual && k == CaptainBrain.MANDO_AT) {
				b = 6.0F;
			} else if (residual && k == CaptainBrain.MANDO_AT + 1) {
				b = 0.0F;
			}
			bias.add(b);
		}
		json.add("b_out", bias);
		return json;
	}

	/** A v2 network in charge (mando_red) that wants this order. */
	static float[] commanding(Captain.Order order) {
		float[] bias = new float[CaptainBrain.OUTPUTS_V2];
		bias[CaptainBrain.MANDO_AT + 1] = 30.0F;
		bias[order.ordinal()] = 30.0F;
		// the same formation and sector every pass: an order carries on only while those stay the same
		bias[CaptainBrain.FORMATION_AT_V2 + Captain.Formation.LIBRE.ordinal()] = 30.0F;
		bias[CaptainBrain.SECTOR_AT_V2] = 30.0F;
		return bias;
	}

	/** Loads a captain network into a fresh redes_v4 for this test; returns what to put back. */
	private static Runnable load(GameTestHelper helper, JsonObject net) {
		CombatConfig cfg = CombatConfig.get();
		String savedFolder = cfg.iaCarpetaRedes;
		String savedContract = cfg.iaContrato;
		try {
			Path root = Files.createTempDirectory("forja_capitan2");
			Path v3 = Files.createDirectories(root.resolve("redes"));
			Path v4 = Files.createDirectories(root.resolve("redes_v4"));
			Files.writeString(v4.resolve("red_" + MobAi.V4_CAPTAIN + ".json"), new com.google.gson.Gson().toJson(net));
			cfg.iaCarpetaRedes = v3.toAbsolutePath().toString();
			cfg.iaContrato = "auto";
			MobAi.reload();
		} catch (java.io.IOException failure) {
			throw new RuntimeException(failure);
		}
		return () -> {
			cfg.iaCarpetaRedes = savedFolder;
			cfg.iaContrato = savedContract;
			MobAi.reload();
		};
	}

	/**
	 * The mando head. A network fresh from its start (mando rows at 0, bias (+6, 0)) gives, pass after pass, the rules'
	 * order exactly (order, formation, sector, countdown, focus and posts), charges included, and decide() called on its own
	 * gives what the rules give. Then one in charge (mando_red) overrides them (HOSTIGAR in a wedge); and a v1 network
	 * still loads and gives its orders.
	 */
	@GameTest(environment = "forja-test:capitan2_mando", padding = 24, maxTicks = 260)
	public void theMandoHeadFollowsOrOverridesTheRules(GameTestHelper helper) {
		RedV4CapitanGameTests.floor(helper, 16);
		Runnable restore = load(helper, fakeCaptainV2(7L, new float[0], true));
		helper.assertTrue(MobAi.captainNet() != null && MobAi.captainNet().contractVersion == 2 && CaptainBrain.check(MobAi.captainNet()) == null,
			"la red v2 se carga: " + MobAi.problems());
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(8, 1, 8));
		// the rules captain off for this group: the network gives the orders all the same, and with mando 0 they are the rules'
		CaptainBrain.override(player, CaptainBrain.Mode.LIBRE);
		CaptainBrain.overridePieces(player, ALL);
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		player.startUsingItem(InteractionHand.OFF_HAND);
		List<Mob> mobs = List.of(elite(helper, new BlockPos(12, 1, 8)), zombie(helper, new BlockPos(4, 1, 8)), zombie(helper, new BlockPos(8, 1, 12)),
			zombie(helper, new BlockPos(8, 1, 4)));
		for (Mob mob : mobs) {
			mob.setTarget(player);
		}
		hold(helper, player, mobs, null);
		int[] passes = {0};
		int[] charges = {0};
		String[] wrong = {null};
		long[] seen = {Long.MIN_VALUE};
		int[] phase = {0};
		helper.onEachTick(() -> {
			if (!player.isUsingItem()) {
				player.startUsingItem(InteractionHand.OFF_HAND);
			}
			Captain.Group g = Captain.group(player);
			if (phase[0] != 0 || g == null || g.captain == null || g.rulesCommand == null || g.seenAt == seen[0]) {
				return;
			}
			seen[0] = g.seenAt;
			passes[0]++;
			if (g.mando || !g.command.sameAs(g.rulesCommand)) {
				wrong[0] = "pase " + passes[0] + ": " + g.command + " / reglas " + g.rulesCommand + " mando " + g.mando;
			}
			charges[0] += g.command.order == Captain.Order.CARGA ? 1 : 0;
			// decide() on its own: the rules' order
			long now = helper.getLevel().getGameTime();
			Captain.Command rules = CaptainBrain.ruleOrder(g, player, now);
			Captain.Command net = CaptainBrain.decide(g, player, now);
			if (!net.sameAs(rules) || g.mando) {
				wrong[0] = "decide() " + net + " / reglas " + rules;
			}
		});
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(wrong[0] == null, "con mando 0, la orden de las reglas: " + wrong[0]);
			helper.assertTrue(passes[0] >= 8 && charges[0] > 0, "pases " + passes[0] + ", con carga " + charges[0]);
			phase[0] = 1;
			float[] bias = commanding(Captain.Order.HOSTIGAR);
			bias[CaptainBrain.FORMATION_AT_V2 + Captain.Formation.LIBRE.ordinal()] = 0.0F;
			bias[CaptainBrain.FORMATION_AT_V2 + Captain.Formation.CUNA.ordinal()] = 30.0F;
			restore.run();
			load(helper, fakeCaptainV2(8L, bias, false));
		});
		helper.runAfterDelay(150, () -> {
			Captain.Group g = Captain.group(player);
			helper.assertTrue(g.mando && g.command.order == Captain.Order.HOSTIGAR && g.command.formation == Captain.Formation.CUNA,
				"con mando 1 la red manda HOSTIGAR en CUÑA: " + g.command + " (reglas " + g.rulesCommand + ")");
			helper.assertTrue(g.rulesCommand.order != Captain.Order.HOSTIGAR, "que las reglas nunca dan");
			// v1 still loads and runs
			float[] bias = new float[CaptainBrain.OUTPUTS];
			bias[CaptainBrain.ORDER_AT + Captain.Order.ESCOLTA.ordinal()] = 30.0F;
			restore.run();
			load(helper, RedV4CapitanGameTests.fakeCaptain(9L, bias));
			NetBrain v1 = MobAi.captainNet();
			helper.assertTrue(v1 != null && v1.contractVersion == 1 && v1.inputs() == CaptainBrain.SIZE, "una red v1 se sigue cargando: " + MobAi.problems());
		});
		helper.runAfterDelay(180, () -> {
			Captain.Group g = Captain.group(player);
			helper.assertTrue(g.command.order == Captain.Order.ESCOLTA && !g.mando, "y da sus órdenes: " + g.command);
			// a v2 file with v1's names is refused
			JsonObject wrongNames = fakeCaptainV2(10L, new float[0], true);
			JsonArray names = new JsonArray();
			CaptainBrain.names().forEach(names::add);
			wrongNames.add("nombres_obs", names);
			restore.run();
			load(helper, wrongNames);
			helper.assertTrue(MobAi.captainNet() == null && String.valueOf(MobAi.problems().get(MobAi.V4_CAPTAIN)).startsWith("v4:"),
				"una v2 con 213 nombres se rechaza: " + MobAi.problems());
			restore.run();
			player.stopUsingItem();
			finish(helper, 24, player);
		});
	}

	/**
	 * CERRAR_SALIDAS (a v2 network in charge): the player about to leave along +x (their motion, and away from the group),
	 * the ones that fight up close are spread over ±60° of it at 5 from the player, one angle each, evenly; the members see
	 * CERCAR in block M; without a turn their rules go to the point (FORMACION). The captain shouts the new order.
	 */
	@GameTest(environment = "forja-test:capitan2_cerrar", padding = 16, maxTicks = 80)
	public void closingTheWaysOut(GameTestHelper helper) {
		RedV4CapitanGameTests.floor(helper, 16);
		Runnable restore = load(helper, fakeCaptainV2(11L, commanding(Captain.Order.CERRAR_SALIDAS), false));
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(9, 1, 8));
		CaptainBrain.overridePieces(player, EnumSet.of(CaptainBrain.Piece.ORDENES2, CaptainBrain.Piece.VISIBLE));
		// symmetric about the player's z: the way out is +x whether it comes from their motion or from the group's middle
		List<Mob> mobs = new ArrayList<>(List.of(elite(helper, new BlockPos(3, 1, 8)), zombie(helper, new BlockPos(4, 1, 5)), zombie(helper, new BlockPos(4, 1, 11)),
			zombie(helper, new BlockPos(2, 1, 8))));
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 8));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		mobs.add(skeleton);
		for (Mob mob : mobs) {
			mob.setNoAi(true);
			mob.setTarget(player);
		}
		Vec3 at = player.position();
		helper.onEachTick(() -> {
			player.setPos(at.x, at.y, at.z);
			player.setDeltaMovement(0.15, 0.0, 0.0);
			player.setHealth(player.getMaxHealth());
			for (Mob mob : mobs) {
				mob.setTarget(player);
			}
		});
		helper.runAfterDelay(25, () -> {
			Captain.Group g = Captain.group(player);
			helper.assertTrue(g != null && g.command.order == Captain.Order.CERRAR_SALIDAS, "la red cierra las salidas: " + (g == null ? null : g.command));
			List<Double> angles = new ArrayList<>();
			for (Mob mob : mobs) {
				MobMind mind = MobAi.mind(mob);
				if (mob == skeleton) {
					helper.assertTrue(Math.abs(mind.postPoint.distanceTo(at) - 5.0) > 1.0E-3, "el arquero, a su puesto de formación");
					continue;
				}
				double r = Math.hypot(mind.postPoint.x - at.x, mind.postPoint.z - at.z);
				helper.assertTrue(Math.abs(r - Captain.EXITS_RADIUS) < 1.0E-6, "a 5 del jugador: " + r);
				angles.add(Math.toDegrees(Math.atan2(mind.postPoint.z - at.z, mind.postPoint.x - at.x)));
			}
			angles.sort(Double::compare);
			helper.assertTrue(angles.size() == 4 && Math.abs(angles.get(0) + 60.0) < 1.0E-3 && Math.abs(angles.get(3) - 60.0) < 1.0E-3
				&& Math.abs(angles.get(1) + 20.0) < 1.0E-3 && Math.abs(angles.get(2) - 20.0) < 1.0E-3, "en ±60° de +x, repartidos: " + angles);
			float[] obs = ObsV4.build(mobs.get(1), player, MobAi.mind(mobs.get(1)));
			helper.assertTrue(obs[ObsV4.M_AT + 3 + Captain.Order.CERCAR.ordinal()] == 1.0F, "los mobs ven CERCAR");
			helper.assertTrue(g.shouts >= 1, "el capitán grita la orden: " + g.shouts);
			// the rules obeying (RuleBrain.obey): with a turn (held or free), in; without one, to its point
			MobMind mind = MobAi.mind(mobs.get(1));
			long now = helper.getLevel().getGameTime();
			Decision in = dev.forja.ai.RuleBrain.obey(mind, player, true, now);
			Decision wait = dev.forja.ai.RuleBrain.obey(mind, player, false, now);
			helper.assertTrue(in == Decision.APPROACH, "con turno, ataca: " + (in == null ? null : in.tactic()));
			helper.assertTrue(wait != null && wait.tactic() == Tactic.FORMACION, "sin turno, a su punto: " + (wait == null ? null : wait.tactic()));
			restore.run();
			finish(helper, 16, player);
		});
	}

	/**
	 * FOCO_HERIDO (a v2 network in charge), with two players: while the other is not more hurt it is masked; once they are,
	 * the members without a turn but the captain go for them, and the ones left see no order.
	 */
	@GameTest(environment = "forja-test:capitan2_foco", padding = 24, maxTicks = 90)
	public void focusOnTheHurtOne(GameTestHelper helper) {
		RedV4CapitanGameTests.floor(helper, 16);
		Runnable restore = load(helper, fakeCaptainV2(12L, commanding(Captain.Order.FOCO_HERIDO), false));
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(8, 1, 6));
		CombatGameTests.TestPlayer hurt = CombatGameTests.player(helper, new BlockPos(8, 1, 12));
		helper.getLevel().addNewPlayer(player);
		helper.getLevel().addNewPlayer(hurt);
		CaptainBrain.overridePieces(player, EnumSet.of(CaptainBrain.Piece.ORDENES2));
		CaptainBrain.overridePieces(hurt, NONE);
		Zombie elite = elite(helper, new BlockPos(12, 1, 6));
		List<Mob> mobs = List.of(elite, zombie(helper, new BlockPos(4, 1, 6)), zombie(helper, new BlockPos(8, 1, 3)), zombie(helper, new BlockPos(5, 1, 9)));
		for (Mob mob : mobs) {
			mob.setNoAi(true);
			mob.setTarget(player);
		}
		Vec3 at = player.position();
		Vec3 other = hurt.position();
		float[] hurtHealth = {hurt.getMaxHealth()};
		boolean[] retarget = {true};
		String[] wrong = {null};
		helper.onEachTick(() -> {
			player.setPos(at.x, at.y, at.z);
			hurt.setPos(other.x, other.y, other.z);
			player.setHealth(player.getMaxHealth());
			hurt.setHealth(hurtHealth[0]);
			for (Mob mob : mobs) {
				if (retarget[0]) {
					mob.setTarget(player);
				}
			}
			Captain.Group g = Captain.group(player);
			if (retarget[0] && g != null && g.command.order == Captain.Order.FOCO_HERIDO) {
				wrong[0] = "FOCO_HERIDO sin otro más herido";
			}
		});
		helper.runAfterDelay(35, () -> {
			helper.assertTrue(wrong[0] == null, String.valueOf(wrong[0]));
			hurtHealth[0] = 6.0F;
			retarget[0] = false;
		});
		helper.runAfterDelay(65, () -> {
			Captain.Group g = Captain.group(player);
			long moved = mobs.stream().filter(m -> m != elite && m.getTarget() == hurt).count();
			helper.assertTrue(moved >= 2, "los de sin turno van a por el herido: " + moved + " (orden " + (g == null ? null : g.command) + ")");
			helper.assertTrue(elite.getTarget() == player, "el capitán no");
			float[] obs = ObsV4.build(elite, player, MobAi.mind(elite));
			helper.assertTrue(g.command.order != Captain.Order.FOCO_HERIDO || obs[ObsV4.M_AT + 3] == 1.0F, "los mobs lo ven como ninguna");
			restore.run();
			finish(helper, 24, player, hurt);
		});
	}

	/**
	 * RETIRADA_FALSA (a v2 network in charge): first everyone to 10 from the player, going away, as FORMACION (not
	 * RETIRARSE) and seen as RETIRADA; at 40 ticks the attack: a shout, one more turn, all in (seen as CARGA at 0). Given
	 * again, a player coming 4 on towards the group turns it to the attack before its 40 ticks.
	 */
	@GameTest(environment = "forja-test:capitan2_falsa", padding = 24, maxTicks = 160)
	public void theFalseRetreatTurns(GameTestHelper helper) {
		RedV4CapitanGameTests.floor(helper, 32);
		Runnable restore = load(helper, fakeCaptainV2(13L, commanding(Captain.Order.RETIRADA_FALSA), false));
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(16, 1, 16));
		CaptainBrain.overridePieces(player, EnumSet.of(CaptainBrain.Piece.ORDENES2));
		List<Mob> mobs = List.of(elite(helper, new BlockPos(20, 1, 16)), zombie(helper, new BlockPos(12, 1, 16)), zombie(helper, new BlockPos(16, 1, 20)),
			zombie(helper, new BlockPos(16, 1, 12)), zombie(helper, new BlockPos(19, 1, 19)));
		for (Mob mob : mobs) {
			mob.setNoAi(true);
			mob.setTarget(player);
		}
		Vec3[] at = {player.position()};
		// the player's walk towards the group (phase 3): from this tick, this step, 10 ticks
		long[] walkFrom = {-1};
		Vec3[] step = {Vec3.ZERO};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (walkFrom[0] >= 0 && t >= walkFrom[0] && t < walkFrom[0] + 10) {
				at[0] = at[0].add(step[0]);
			}
			player.setPos(at[0].x, at[0].y, at[0].z);
			player.setHealth(player.getMaxHealth());
			for (Mob mob : mobs) {
				mob.setTarget(player);
				mob.setHealth(mob.getMaxHealth());
			}
		});
		long[] given = {0};
		helper.runAfterDelay(25, () -> {
			Captain.Group g = Captain.group(player);
			helper.assertTrue(g != null && g.command.order == Captain.Order.RETIRADA_FALSA && !g.command.falseAttack, "retirada falsa: " + (g == null ? null : g.command));
			given[0] = g.command.givenAt;
			for (Mob mob : mobs) {
				MobMind mind = MobAi.mind(mob);
				Vec3 away = mob.position().subtract(at[0]).multiply(1.0, 0.0, 1.0).normalize();
				Vec3 expected = at[0].add(away.scale(Captain.FALSE_RADIUS));
				helper.assertTrue(Math.hypot(mind.postPoint.x - expected.x, mind.postPoint.z - expected.z) < 1.0E-6, "a 10, alejándose: " + mind.postPoint);
			}
			MobMind one = MobAi.mind(mobs.get(1));
			long now = helper.getLevel().getGameTime();
			for (boolean turn : new boolean[] {true, false}) {
				Decision d = dev.forja.ai.RuleBrain.obey(one, player, turn, now);
				helper.assertTrue(d != null && d.tactic() == Tactic.FORMACION, "las reglas van con FORMACION, no RETIRARSE: " + (d == null ? null : d.tactic()));
			}
			float[] obs = ObsV4.build(mobs.get(1), player, one);
			helper.assertTrue(obs[ObsV4.M_AT + 3 + Captain.Order.RETIRADA.ordinal()] == 1.0F, "los mobs ven RETIRADA");
		});
		helper.runAfterDelay(25 + 45, () -> {
			Captain.Group g = Captain.group(player);
			long now = helper.getLevel().getGameTime();
			helper.assertTrue(g.command.order == Captain.Order.RETIRADA_FALSA && g.command.falseAttack, "a los 40 ticks, al ataque");
			helper.assertTrue(g.command.chargeAt >= given[0] + Captain.FALSE_TICKS && g.command.chargeAt <= given[0] + Captain.FALSE_TICKS + 1,
				"justo a los 40: " + (g.command.chargeAt - given[0]));
			helper.assertTrue(Captain.chargeTurn(player, now), "con un turno más");
			MobMind one = MobAi.mind(mobs.get(1));
			for (boolean turn : new boolean[] {true, false}) {
				helper.assertTrue(dev.forja.ai.RuleBrain.obey(one, player, turn, now) == Decision.APPROACH, "todos al ataque, con turno o sin él");
			}
			float[] obs = ObsV4.build(mobs.get(1), player, one);
			helper.assertTrue(obs[ObsV4.M_AT + 3 + Captain.Order.CARGA.ordinal()] == 1.0F && obs[ObsV4.M_AT + 13] == 0.0F, "los mobs ven CARGA en 0");
			float[] cap = CaptainBrain.observeV2(g, player, now);
			helper.assertTrue(cap[CaptainBrain.C2_AT + 2] == 1.0F && cap[CaptainBrain.C2_AT + 3] == 1.0F, "orden_retirada_falsa y falsa_ataque");
			// given again (as a fresh one), and the player comes on towards the group
			Captain.Command c = g.command;
			c.falseAttack = false;
			c.givenAt = now;
			c.chargeAt = Long.MIN_VALUE / 2;
			given[0] = now;
		});
		// the group's middle is about +x of the player; they walk 5 blocks that way over 10 ticks
		helper.runAfterDelay(25 + 46, () -> {
			Captain.Group g = Captain.group(player);
			helper.assertFalse(g.command.falseAttack, "otra vez en la fase 1");
			Vec3 middle = Vec3.ZERO;
			for (Mob mob : mobs) {
				middle = middle.add(mob.position().scale(1.0 / mobs.size()));
			}
			Vec3 toGroup = middle.subtract(at[0]).multiply(1.0, 0.0, 1.0).normalize();
			// the stored direction is from when it was first given; the members have not moved (no AI)
			step[0] = toGroup.scale(0.5);
			walkFrom[0] = helper.getTick() + 1;
		});
		helper.runAfterDelay(25 + 46 + 14, () -> {
			Captain.Group g = Captain.group(player);
			long now = helper.getLevel().getGameTime();
			helper.assertTrue(g.command.falseAttack && now - given[0] < Captain.FALSE_TICKS,
				"el jugador avanza 4: al ataque antes de los 40 (" + (now - given[0]) + ")");
			restore.run();
			finish(helper, 32, player);
		});
	}
}
