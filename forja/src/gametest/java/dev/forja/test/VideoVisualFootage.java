package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.forja.clase.ClassProgress;
import dev.forja.clase.PlayerClass;
import dev.forja.client.GuideBookScreen;
import dev.forja.client.TalentTreeScreen;
import dev.forja.combat.CombatFeedback;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.menu.ForgeMenu;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModEntities;
import dev.forja.world.ApprenticeKits;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The visual pass on film (FORJA_SOLO=video_visual, Andy 2026-10-01): frame sequences, a screenshot every tick or
 * two for anything that moves, one frame for anything held still, put together into a video by video_visual.py
 * (kept beside the video, in Forja_capturas_mejoras/video_visual). Every frame is "vv_&lt;shot&gt;_&lt;nnnn&gt;"; a shot whose name ends in "_hold" is a
 * single frame meant to be held.
 *
 * <p>The sections, in the video's order: monsters (a), particles (b), screens (c), HUD (d), items and blocks (e),
 * books (f) and the star dimension's sky (g). FORJA_VIDEO=a,c films only those; a section that fails is logged and
 * the rest still run. The HUD is hidden everywhere except in the HUD section.
 */
final class VideoVisualFootage {
	private static final int W = 1280;
	private static final int H = 720;
	private static String shot = "x";
	private static int frame;

	private VideoVisualFootage() {
	}

	// ------------------------------------------------------------------------------------------ frames

	private static void begin(String name) {
		shot = name;
		frame = 0;
		log("video_visual: toma " + name);
	}

	private static void frame(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		TestScreenshotOptions options = TestScreenshotOptions.of(String.format(Locale.ROOT, "vv_%s_%04d", shot, frame++)).disableCounterPrefix();
		// A screen or the HUD is laid out for the window, not for a bigger shot (it would fill only its top-left
		// corner): those are taken at the window's own size and scaled up when the video is put together.
		context.takeScreenshot(gui ? options : options.withSize(W, H));
	}

	/** Whether the frames being taken now show a screen or the HUD. */
	private static boolean gui;

	/** One frame of its own, to be held in the video. */
	private static void hold(ClientGameTestContext context, String name) {
		begin(name + "_hold");
		frame(context);
	}

	/** {@code count} frames, {@code every} ticks apart. */
	private static void roll(ClientGameTestContext context, int count, int every) {
		for (int i = 0; i < count; i++) {
			context.waitTicks(every);
			frame(context);
		}
	}

	private static void log(String message) {
		System.out.println("[forja-test] " + message);
	}

	private static void hud(ClientGameTestContext context, boolean shown) {
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() == shown) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static void safely(ClientGameTestContext context, String name, Runnable part) {
		try {
			part.run();
		} catch (Throwable failure) {
			log("video_visual: FALLO en " + name + ": " + failure);
			failure.printStackTrace(System.out);
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}

	/** A camera at (cx, cy, cz) looking at (tx, ty, tz): the player's eyes put there. */
	private static void camera(TestServerContext server, double cx, double cy, double cz, double tx, double ty, double tz) {
		double flat = Math.sqrt((tx - cx) * (tx - cx) + (tz - cz) * (tz - cz));
		float yaw = (float) -Math.toDegrees(Math.atan2(tx - cx, tz - cz));
		float pitch = (float) Math.toDegrees(Math.atan2(cy - ty, Math.max(0.01, flat)));
		server.runCommand(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.2f %.2f", cx, cy - 1.62, cz, yaw, pitch));
	}

	/** The camera on a circle round a point: 0 straight south of it (in front of a mob facing south), 90 to its east. */
	private static void orbit(TestServerContext server, double px, double ly, double pz, double distance, double rise, double degrees) {
		double angle = Math.toRadians(degrees);
		camera(server, px - Math.sin(angle) * distance, ly + rise, pz + Math.cos(angle) * distance, px, ly, pz);
	}

	private static BlockState block(String id) {
		String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "forja";
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(namespace, path)).defaultBlockState();
	}

	private static void flying(TestServerContext server, TestServerConnection connection, boolean fly) {
		server.runOnServer(s -> {
			var player = connection.getServerPlayer();
			player.getAbilities().flying = fly;
			player.onUpdateAbilities();
		});
	}

	private static void clearMobs(TestServerContext server, TestServerConnection connection, double px, int y, double pz, double reach) {
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			AABB box = new AABB(px - reach, y - 3, pz - reach, px + reach, y + 14, pz + reach);
			for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null, box,
				e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
				entity.discard();
			}
		});
	}

	private static void noFire(TestServerContext server, double px, int y, double pz, int reach) {
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air replace fire",
			(int) px - reach, y, (int) pz - reach, (int) px + reach, y + 3, (int) pz + reach));
	}

	private static ItemStack forged(ForgeType type, ForgeMaterial... materials) {
		List<ForgeMaterial> chosen = new ArrayList<>();
		for (int i = 0; i < type.slots.size(); i++) {
			PartType slot = type.slots.get(i);
			ForgeMaterial wanted = materials[Math.min(i, materials.length - 1)];
			chosen.add(slot.accepts(wanted) ? wanted : slot.showcase());
		}
		return Assembler.create(type, chosen);
	}

	// ------------------------------------------------------------------------------------------ the run

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		String only = System.getenv("FORJA_VIDEO");
		List<String> wanted = only == null || only.isBlank() ? List.of("a", "b", "c", "d", "e", "f", "g") : List.of(only.split(","));
		server.runCommand("weather clear 1000000");
		server.runCommand("difficulty easy");
		server.runCommand("gamerule doDaylightCycle false");
		server.runCommand("gamerule doMobSpawning false");
		server.runCommand("gamerule doMobLoot false");
		server.runCommand("gamerule doFireTick false");
		server.runOnServer(s -> dev.forja.world.WorldEvents.stop(connection.getServerLevel()));
		context.runOnClient(mc -> {
			mc.options.particles().set(net.minecraft.server.level.ParticleStatus.ALL);
			mc.options.fovEffectScale().set(0.0);
			mc.options.bobView().set(false);
		});
		hud(context, false);
		if (wanted.contains("a")) {
			safely(context, "monstruos", () -> monsters(context, server, connection, x, y, z));
		}
		if (wanted.contains("b")) {
			safely(context, "particulas", () -> particles(context, server, connection, x, y, z));
		}
		if (wanted.contains("c")) {
			gui = true;
			safely(context, "pantallas", () -> screens(context, server, connection, x, y, z));
			gui = false;
		}
		if (wanted.contains("d")) {
			gui = true;
			safely(context, "hud", () -> hudLanes(context, server, connection, x, y, z));
			gui = false;
			hud(context, false);
		}
		if (wanted.contains("e")) {
			safely(context, "objetos", () -> items(context, server, connection, x, y, z));
		}
		if (wanted.contains("f")) {
			gui = true;
			safely(context, "libros", () -> books(context, server, connection));
			gui = false;
		}
		if (wanted.contains("g")) {
			safely(context, "cielo", () -> sky(context, server, connection));
		}
		hud(context, true);
		context.runOnClient(mc -> {
			mc.gui.setScreen(null);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			mc.options.fov().set(70);
			mc.options.fovEffectScale().set(1.0);
		});
		server.runCommand("gamerule doDaylightCycle true");
		server.runCommand("gamerule doMobSpawning true");
		server.runCommand("gamerule doMobLoot true");
		server.runCommand("time set noon");
		server.runCommand("gamemode survival @a");
		server.runCommand("difficulty peaceful");
	}

	// ------------------------------------------------------------------------------------------ a: monsters

	/** The order Andy listed them in. */
	private static final List<String> MONSTERS = List.of("automata_de_forja", "guardian_de_cuno", "yunque_andante", "percutor", "tenaza",
		"cargador_de_carbon", "ascua_mayor", "templador", "herrumbre", "escoria_viviente", "coraza_vacia", "pavesa", "nucleo_estelar",
		"molde_roto", "herrero_caido");

	/** The blow each one is filmed with: one of its own moves (MobAnimationFilm), or null for its plain warned blow. */
	private static final Map<String, String> ATTACK = Map.ofEntries(
		Map.entry("automata_de_forja", "coz"), Map.entry("guardian_de_cuno", "sello"), Map.entry("percutor", "martillazo"),
		Map.entry("tenaza", "agarre"), Map.entry("ascua_mayor", "picado"), Map.entry("templador", "aceite"),
		Map.entry("coraza_vacia", "embestida"), Map.entry("pavesa", "picado"), Map.entry("nucleo_estelar", "lleno"),
		Map.entry("herrero_caido", "reves"));

	private static void monsters(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("gamemode creative @a");
		flying(server, connection, true);
		int sx = x + 340;
		int sz = z + 60;
		camera(server, sx + 0.5, y + 3.0, sz + 8.5, sx + 0.5, y + 1.0, sz + 0.5);
		context.waitTicks(30);
		connection.waitForChunksRender();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d polished_andesite", sx - 26, y - 1, sz - 14, sx + 26, y - 1, sz + 14));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", sx - 26, y, sz - 14, sx + 26, y + 10, sz + 14));
		server.runCommand("time set 6000");
		context.runOnClient(mc -> mc.options.fov().set(60));
		List<VisualMobsFootage.Subject> subjects = VisualMobsFootage.subjects();
		int index = 1;
		// FORJA_VIDEO_NOCHE=1: only the night shots, to look at them again without the whole parade.
		boolean nightOnly = System.getenv("FORJA_VIDEO_NOCHE") != null;
		for (String id : nightOnly ? List.<String>of() : MONSTERS) {
			for (VisualMobsFootage.Subject subject : subjects) {
				if (subject.id().equals(id)) {
					String tag = String.format(Locale.ROOT, "a%02d_%s", index, id);
					safely(context, id, () -> monster(context, server, connection, subject, tag, sx + 0.5, y, sz + 0.5));
				}
			}
			index++;
		}
		if (!nightOnly) safely(context, "aprendices", () -> apprentices(context, server, connection, sx + 0.5, y, sz + 0.5));
		safely(context, "noche", () -> nightRow(context, server, connection, sx + 0.5, y, sz + 0.5));
		clearMobs(server, connection, sx + 0.5, y, sz + 0.5, 30);
		server.runCommand("time set 6000");
		flying(server, connection, false);
	}

	private static int[] spawn(TestServerContext server, TestServerConnection connection, VisualMobsFootage.Subject subject, double px, int y, double pz) {
		return server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			Mob mob = subject.type().create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				throw new AssertionError("could not create " + subject.id());
			}
			mob.snapTo(px, y, pz, 0.0F, 0.0F);
			face(mob, 0.0F);
			mob.setNoAi(true);
			mob.setPersistenceRequired();
			mob.setInvulnerable(true);
			level.addFreshEntity(mob);
			if (mob instanceof dev.forja.entity.BrokenMould mould) {
				// Its two-handed grip: the greatsword it copies, held in both hands.
				ItemStack weapon = Assembler.create(ForgeType.ESPADON, List.of(ForgeMaterial.DIAMANTE, ForgeMaterial.DIAMANTE,
					ForgeMaterial.MADERA, ForgeMaterial.HIERRO), level.registryAccess());
				mould.consider(level, weapon);
				for (int tick = 0; tick < dev.forja.entity.BrokenMould.RECAST_WINDUP; tick++) {
					mould.tick();
				}
			}
			ArmorStand stand = new ArmorStand(level, px, y, pz + 2.5);
			stand.setInvisible(true);
			stand.setInvulnerable(true);
			stand.setNoGravity(true);
			level.addFreshEntity(stand);
			return new int[] {mob.getId(), stand.getId()};
		});
	}

	private static void face(Mob mob, float yaw) {
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
	}

	private static void place(TestServerContext server, TestServerConnection connection, int id, double px, int y, double pz, float yaw) {
		server.runOnServer(s -> {
			if (connection.getServerLevel().getEntity(id) instanceof Mob mob) {
				mob.setTarget(null);
				mob.snapTo(px, y, pz, yaw, 0.0F);
				face(mob, yaw);
			}
		});
	}

	/** One monster: a turn all the way round it, a walk past, and one of its blows. */
	private static void monster(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
		VisualMobsFootage.Subject subject, String tag, double px, int y, double pz) {
		clearMobs(server, connection, px, y, pz, 16);
		context.runOnClient(mc -> mc.particleEngine.clearParticles());
		server.runCommand("time set 6000");
		int[] ids = spawn(server, connection, subject, px, y, pz);
		int id = ids[0];
		int standId = ids[1];
		double height = server.computeOnServer(s -> (double) connection.getServerLevel().getEntity(id).getBbHeight());
		double distance = Math.max(2.3, height * 1.5 + 0.7);
		double lookY = y + height * 0.5;
		double rise = distance * 0.18;
		orbit(server, px, lookY, pz, distance, rise, 20.0);
		context.waitTicks(30);

		// The turntable: the camera all the way round it, from in front and a little to its left.
		begin(tag + "_1giro");
		int turn = 66;
		for (int i = 0; i < turn; i++) {
			orbit(server, px, lookY, pz, distance, rise, 20.0 + i * 360.0 / turn);
			context.waitTicks(1);
			frame(context);
		}

		// Walking past, side on, the camera still.
		double walk = 0.11;
		int steps = 34;
		double start = -walk * steps / 2;
		place(server, connection, id, px + start, y, pz, -90.0F);
		orbit(server, px, lookY, pz, distance + 0.4, rise, 12.0);
		context.waitTicks(6);
		begin(tag + "_2andar");
		for (int t = 1; t <= steps; t++) {
			double along = start + t * walk;
			server.runOnServer(s -> {
				if (connection.getServerLevel().getEntity(id) instanceof Mob mob) {
					mob.setPos(px + along, y, pz);
					face(mob, -90.0F);
				}
			});
			context.waitTicks(1);
			frame(context);
		}

		// One blow: its own move, or the warned blow every melee monster has.
		place(server, connection, id, px, y, pz, 0.0F);
		String moveName = ATTACK.get(subject.id());
		MobAnimationFilm.Move move = null;
		if (moveName != null) {
			for (MobAnimationFilm.Subject film : MobAnimationFilm.subjects()) {
				if (film.id().equals(subject.id())) {
					for (MobAnimationFilm.Move candidate : film.moves()) {
						if (candidate.name().equals(moveName)) {
							move = candidate;
						}
					}
				}
			}
		}
		if (move != null) {
			MobAnimationFilm.Move chosen = move;
			double reach = chosen.targetAt();
			double side = Math.max(distance, reach * 0.8 + 1.8);
			orbit(server, px, lookY, pz + reach / 2.0, side, side * 0.2, 62.0);
			context.waitTicks(20);
			begin(tag + "_3ataque");
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				Mob mob = (Mob) level.getEntity(id);
				ArmorStand stand = (ArmorStand) level.getEntity(standId);
				stand.setPos(px, y, pz + reach);
				face(mob, 0.0F);
				chosen.start().accept(mob, stand);
			});
			int[] shots = chosen.shots();
			int length = Math.min(70, shots[shots.length - 1] + 12);
			for (int t = 0; t < length; t++) {
				context.waitTicks(1);
				boolean alive = server.computeOnServer(s -> connection.getServerLevel().getEntity(id) != null);
				if (!alive) {
					break;
				}
				frame(context);
			}
		} else {
			orbit(server, px, lookY, pz + 1.0, Math.max(distance, 2.9), distance * 0.2, 55.0);
			context.waitTicks(20);
			begin(tag + "_3ataque");
			int warn = 10;
			server.runOnServer(s -> CombatFeedback.telegraph(connection.getServerLevel().getEntity(id), warn));
			for (int t = 0; t < warn; t++) {
				context.waitTicks(1);
				frame(context);
			}
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				Mob mob = (Mob) level.getEntity(id);
				mob.swing(InteractionHand.MAIN_HAND);
				mob.doHurtTarget(level, level.getEntity(standId));
			});
			for (int t = 0; t < 16; t++) {
				context.waitTicks(1);
				frame(context);
			}
		}
		context.waitTicks(10);
		noFire(server, px, y, pz, 14);
		clearMobs(server, connection, px, y, pz, 16);
	}

	/** The Fallen Smith's apprentices in their forged plate, the camera moving along the five. */
	private static void apprentices(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, double px, int y, double pz) {
		clearMobs(server, connection, px, y, pz, 16);
		server.runCommand("time set 6000");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			RandomSource random = RandomSource.create(20261001L);
			List<ApprenticeKits.Role> roles = List.of(ApprenticeKits.MELEE.get(0), ApprenticeKits.MELEE.get(3),
				ApprenticeKits.ARCHER, ApprenticeKits.MELEE.get(5), ApprenticeKits.MELEE.get(8));
			for (int i = 0; i < roles.size(); i++) {
				ApprenticeKits.Role role = roles.get(i);
				Mob mob = role.body().create(level, EntitySpawnReason.EVENT);
				mob.snapTo(px - 3.0 + i * 1.5, y, pz, 0.0F, 0.0F);
				face(mob, 0.0F);
				mob.setNoAi(true);
				mob.setPersistenceRequired();
				mob.setInvulnerable(true);
				ApprenticeKits.equip(mob, role, i, 3, random);
				level.addFreshEntity(mob);
			}
		});
		camera(server, px - 4.0, y + 1.9, pz + 3.6, px - 2.0, y + 1.1, pz);
		context.waitTicks(30);
		begin("a16_aprendices");
		int count = 80;
		for (int i = 0; i < count; i++) {
			double k = i / (double) (count - 1);
			double along = -4.0 + 8.0 * k;
			camera(server, px + along, y + 1.9, pz + 3.6 - Math.sin(k * Math.PI) * 0.6, px + along * 0.6, y + 1.1, pz);
			context.waitTicks(1);
			frame(context);
		}
		clearMobs(server, connection, px, y, pz, 16);
	}

	/** Every monster in a row at midnight, the glowmasks lit, the camera along the row; then the Templador up close. */
	private static void nightRow(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, double px, int y, double pz) {
		clearMobs(server, connection, px, y, pz, 30);
		List<VisualMobsFootage.Subject> subjects = VisualMobsFootage.subjects();
		int[] templador = {-1};
		double spacing = 2.9;
		double first = px - (MONSTERS.size() - 1) * spacing / 2.0;
		for (int i = 0; i < MONSTERS.size(); i++) {
			String id = MONSTERS.get(i);
			double at = first + i * spacing;
			for (VisualMobsFootage.Subject subject : subjects) {
				if (subject.id().equals(id)) {
					int[] ids = spawn(server, connection, subject, at, y, pz);
					if (id.equals("templador")) {
						templador[0] = ids[0];
					}
				}
			}
		}
		server.runCommand("time set 18000");
		double last = first + (MONSTERS.size() - 1) * spacing;
		camera(server, first - 2.0, y + 2.6, pz + 5.2, first + 1.0, y + 1.0, pz);
		context.waitTicks(40);
		begin("a17_noche_fila");
		int count = 150;
		for (int i = 0; i < count; i++) {
			double k = i / (double) (count - 1);
			double along = first - 2.0 + (last - first + 4.0) * k;
			camera(server, along, y + 2.6, pz + 5.2, along + 2.0, y + 1.0, pz);
			context.waitTicks(1);
			frame(context);
		}
		if (templador[0] >= 0) {
			double tx = first + MONSTERS.indexOf("templador") * spacing;
			double height = server.computeOnServer(s -> {
				var mob = connection.getServerLevel().getEntity(templador[0]);
				return mob == null ? 2.0 : (double) mob.getBbHeight();
			});
			// Close on the head, its steam cleared off each frame so the amber glass is what is seen.
			double look = y + height * 0.75;
			orbit(server, tx, look, pz, 2.3, 0.2, -25.0);
			context.waitTicks(10);
			begin("a18_noche_templador");
			for (int i = 0; i < 60; i++) {
				orbit(server, tx, look, pz, 2.3, 0.2, -25.0 + i * 0.8);
				context.waitTicks(1);
				context.runOnClient(mc -> mc.particleEngine.clearParticles());
				frame(context);
			}
		}
		clearMobs(server, connection, px, y, pz, 30);
		server.runCommand("time set 6000");
	}

	// ------------------------------------------------------------------------------------------ b: particles

	private static ArmorStand caster(ServerLevel level, double x, int y, double z, float yaw, ItemStack held) {
		ArmorStand stand = new ArmorStand(level, x, y, z);
		stand.setYRot(yaw);
		stand.setYBodyRot(yaw);
		stand.setYHeadRot(yaw);
		stand.setNoGravity(true);
		stand.setInvulnerable(true);
		stand.setShowArms(true);
		stand.setItemSlot(EquipmentSlot.MAINHAND, held);
		stand.setItemSlot(EquipmentSlot.HEAD, forged(ForgeType.CASCO, ForgeMaterial.ORO, ForgeMaterial.CUERO));
		stand.setItemSlot(EquipmentSlot.CHEST, forged(ForgeType.PECHERA, ForgeMaterial.HIERRO, ForgeMaterial.CUERO));
		level.addFreshEntity(stand);
		return stand;
	}

	private static void particles(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 60;
		int pz = z - 70;
		server.runCommand("gamemode creative @a");
		flying(server, connection, true);
		camera(server, px + 3.5, y + 2.0, pz - 5.5, px + 3.5, y + 1.2, pz + 0.5);
		context.waitTicks(30);
		connection.waitForChunksRender();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d polished_andesite", px - 10, y - 1, pz - 10, px + 16, y - 1, pz + 10));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 10, y, pz - 10, px + 16, y + 8, pz + 10));
		// A wall for the bolt to break on.
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d deepslate_bricks", px + 13, y, pz - 3, px + 13, y + 4, pz + 3));
		server.runCommand("time set 14200");
		context.runOnClient(mc -> mc.options.fov().set(70));
		double cx = px + 0.5;
		double cz = pz + 0.5;

		// The staff's bolt: thrown east along the floor, its trail of glints, its burst on the wall.
		safely(context, "baculo", () -> {
			int[] stand = {server.computeOnServer(s -> caster(connection.getServerLevel(), cx, y, cz, -90.0F,
				forged(ForgeType.BACULO, ForgeMaterial.AMATISTA, ForgeMaterial.HIERRO, ForgeMaterial.MADERA)).getId())};
			camera(server, cx + 4.0, y + 1.8, cz - 5.0, cx + 4.0, y + 1.1, cz);
			context.waitTicks(20);
			begin("b01_baculo");
			for (int cast = 0; cast < 3; cast++) {
				ForgeMaterial core = cast == 0 ? ForgeMaterial.AMATISTA : cast == 1 ? ForgeMaterial.VARA_DE_BLAZE : ForgeMaterial.PRISMARINA;
				server.runOnServer(s -> {
					ServerLevel level = connection.getServerLevel();
					var caster = (ArmorStand) level.getEntity(stand[0]);
					ItemStack staff = forged(ForgeType.BACULO, core, ForgeMaterial.HIERRO, ForgeMaterial.MADERA);
					caster.setItemSlot(EquipmentSlot.MAINHAND, staff);
					dev.forja.magic.Spellcasting.cast(level, caster, staff, ForgeType.BACULO, null, 0.6F);
				});
				roll(context, 26, 1);
			}
			clearMobs(server, connection, cx, y, cz, 14);
		});

		// The grimoire: the area opening, and the rune it leaves on the floor.
		safely(context, "grimorio", () -> {
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				ItemStack tome = forged(ForgeType.GRIMORIO, ForgeMaterial.VARA_DE_BLAZE, ForgeMaterial.NETHERITA, ForgeMaterial.ORO);
				caster(level, cx, y, cz, -90.0F, tome);
			});
			camera(server, cx + 2.6, y + 3.6, cz - 5.4, cx + 3.2, y + 0.4, cz);
			context.waitTicks(20);
			begin("b02_grimorio");
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				ArmorStand caster = level.getEntitiesOfClass(ArmorStand.class, new AABB(cx - 1, y - 1, cz - 1, cx + 1, y + 3, cz + 1)).get(0);
				ItemStack tome = caster.getMainHandItem();
				dev.forja.magic.Spellcasting.cast(level, caster, tome, ForgeType.GRIMORIO, new Vec3(cx + 4.0, y, cz), 1.0F);
			});
			roll(context, 30, 1);
			roll(context, 25, 2);
			clearMobs(server, connection, cx, y, cz, 14);
		});

		// The healing lantern, gathered full: its ring of light.
		safely(context, "farol", () -> {
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				ItemStack lantern = forged(ForgeType.FAROL, ForgeMaterial.ESMERALDA, ForgeMaterial.ORO, ForgeMaterial.MADERA);
				caster(level, cx + 3.0, y, cz, 0.0F, lantern);
			});
			camera(server, cx + 3.0, y + 2.6, cz - 5.0, cx + 3.0, y + 0.8, cz);
			context.waitTicks(20);
			begin("b03_farol");
			for (int cast = 0; cast < 2; cast++) {
				float charge = cast == 0 ? 0.0F : 1.0F;
				server.runOnServer(s -> {
					ServerLevel level = connection.getServerLevel();
					ArmorStand caster = level.getEntitiesOfClass(ArmorStand.class, new AABB(cx + 2, y - 1, cz - 1, cx + 4, y + 3, cz + 1)).get(0);
					dev.forja.magic.Healing.lantern(level, caster, caster.getMainHandItem(), charge);
				});
				roll(context, 28, 1);
			}
			clearMobs(server, connection, cx, y, cz, 14);
		});

		// A parry: a zombie's blow caught on a forged shield, the disc of sparks and the ring.
		safely(context, "parada", () -> {
			int[] ids = server.computeOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				ItemStack shield = forged(ForgeType.ESCUDO, ForgeMaterial.HIERRO, ForgeMaterial.ORO, ForgeMaterial.MADERA);
				ArmorStand defender = caster(level, cx + 2.0, y, cz, -90.0F, ItemStack.EMPTY);
				defender.setItemSlot(EquipmentSlot.OFFHAND, shield);
				var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
				zombie.snapTo(cx + 3.8, y, cz, 90.0F, 0.0F);
				zombie.setYHeadRot(90.0F);
				zombie.setYBodyRot(90.0F);
				zombie.setNoAi(true);
				zombie.setPersistenceRequired();
				zombie.setInvulnerable(true);
				level.addFreshEntity(zombie);
				return new int[] {defender.getId(), zombie.getId()};
			});
			camera(server, cx + 2.9, y + 1.7, cz - 3.4, cx + 2.9, y + 1.1, cz);
			context.waitTicks(20);
			begin("b04_parada");
			for (int blow = 0; blow < 2; blow++) {
				server.runOnServer(s -> {
					ServerLevel level = connection.getServerLevel();
					var defender = (ArmorStand) level.getEntity(ids[0]);
					var zombie = (Mob) level.getEntity(ids[1]);
					zombie.swing(InteractionHand.MAIN_HAND);
					try {
						var parry = dev.forja.upgrade.CombatUpgrades.class.getDeclaredMethod("parry", ServerLevel.class,
							net.minecraft.world.entity.LivingEntity.class, net.minecraft.world.damagesource.DamageSource.class, ItemStack.class);
						parry.setAccessible(true);
						parry.invoke(null, level, defender, level.damageSources().mobAttack(zombie), defender.getOffhandItem());
					} catch (ReflectiveOperationException failure) {
						throw new AssertionError("could not parry", failure);
					}
				});
				roll(context, 22, 1);
				server.runOnServer(s -> {
					ServerLevel level = connection.getServerLevel();
					var zombie = (Mob) level.getEntity(ids[1]);
					zombie.snapTo(cx + 3.8, y, cz, 90.0F, 0.0F);
					zombie.setYHeadRot(90.0F);
					zombie.setYBodyRot(90.0F);
					zombie.setDeltaMovement(Vec3.ZERO);
				});
			}
			clearMobs(server, connection, cx, y, cz, 14);
		});

		// The class skills: the war cry's ring of glints round the player, from behind and above.
		safely(context, "habilidades", () -> {
			// The skill is used where a dressed stand stands, and the camera is put back up in the same moment: the
			// glints stay where they were thrown, round the stand, and are seen from above.
			double rx = cx + 3.0;
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				player.getInventory().clearContent();
				ClassProgress.choose(player, PlayerClass.GUERRERO);
				ClassProgress.setLevel(player, 30);
				ArbolGameTests.learnTo(player, "guerrero.b.tronco_4");
				ArbolGameTests.learnTo(player, "guerrero.s1.2");
				caster(connection.getServerLevel(), rx, y, cz, 180.0F,
					forged(ForgeType.ESPADA, ForgeMaterial.DIAMANTE, ForgeMaterial.MADERA, ForgeMaterial.ORO));
			});
			Runnable view = () -> camera(server, rx, y + 5.0, cz - 6.5, rx, y + 0.3, cz + 1.0);
			view.run();
			context.waitTicks(20);
			begin("b05_habilidades");
			for (int slot = 1; slot <= 2; slot++) {
				int which = slot;
				server.runOnServer(s -> {
					ServerPlayer player = connection.getServerPlayer();
					player.teleportTo(rx, y, cz);
					dev.forja.clase.ClassSkills.use(player, which, true);
					// The camera carries no swirl of its own effects.
					player.removeAllEffects();
				});
				view.run();
				roll(context, 32, 1);
			}
			clearMobs(server, connection, cx, y, cz, 14);
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				player.removeAllEffects();
				player.getInventory().clearContent();
				ClassProgress.clear(player);
			});
			server.runCommand("gamemode creative @a");
			flying(server, connection, true);
		});

		// The glint by itself: points of light in the spells' colours, lighting the floor round them.
		safely(context, "destello", () -> {
			server.runCommand("time set 18000");
			camera(server, cx + 3.0, y + 1.7, cz - 3.0, cx + 3.0, y + 0.9, cz);
			context.waitTicks(10);
			begin("b06_destello");
			int[] colours = {0xB98CFF, 0xFF8A3D, 0x6CF0D0, 0xFFD24A, 0x7FB4FF, 0xFF5A6E};
			for (int t = 0; t < 60; t++) {
				int tick = t;
				context.runOnClient(mc -> {
					for (int k = 0; k < 5; k++) {
						double angle = tick * 0.35 + k * Math.PI * 2.0 / 5.0;
						int colour = colours[(tick / 10 + k) % colours.length];
						mc.level.addParticle(new dev.forja.registry.GlintOptions(colour, 1.6F),
							cx + 3.0 + Math.cos(angle) * 1.1, y + 0.3 + (tick % 20) * 0.06, cz + Math.sin(angle) * 1.1,
							0.0, 0.02, 0.0);
					}
				});
				context.waitTicks(1);
				frame(context);
			}
		});

		safely(context, "fundicion", () -> foundrySparks(context, server, connection, x, y, z));
		safely(context, "meteorito", () -> meteor(context, server, connection, x, y, z));
		server.runCommand("time set 6000");
		flying(server, connection, false);
	}

	/** A foundry in the dark: a roofed room, its blocks lit, sparks off the press and the anvil, metal dropping into a box. */
	private static void foundrySparks(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int fx = x + 60;
		int fz = z - 95;
		camera(server, fx + 0.5, y + 2.0, fz - 3.5, fx + 0.5, y + 1.0, fz + 2.0);
		context.waitTicks(30);
		connection.waitForChunksRender();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d deepslate_bricks", fx - 6, y - 1, fz - 6, fx + 6, y + 5, fz + 6));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", fx - 5, y, fz - 5, fx + 5, y + 4, fz + 5));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d deepslate_tiles", fx - 5, y - 1, fz - 5, fx + 5, y - 1, fz + 5));
		BlockPos press = new BlockPos(fx - 2, y, fz + 2);
		BlockPos anvil = new BlockPos(fx + 2, y, fz + 2);
		BlockPos box = new BlockPos(fx, y, fz + 3);
		BlockPos pot = new BlockPos(fx, y + 1, fz + 3);
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			level.setBlock(press, block("montadora").trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true), Block.UPDATE_ALL);
			level.setBlock(anvil, block("yunque_del_herrero"), Block.UPDATE_ALL);
			level.setBlock(box, block("caja_de_moldeo").trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true), Block.UPDATE_ALL);
			level.setBlock(new BlockPos(fx + 4, y, fz + 4), block("farol_de_pavesa"), Block.UPDATE_ALL);
			level.setBlock(new BlockPos(fx - 4, y, fz + 4), block("mesa_de_forja"), Block.UPDATE_ALL);
		});
		server.runCommand("time set 18000");
		camera(server, fx + 0.5, y + 1.7, fz - 0.6, fx + 0.5, y + 0.9, fz + 2.8);
		context.waitTicks(20);
		begin("b07_fundicion");
		for (int t = 0; t < 90; t++) {
			int tick = t;
			context.runOnClient(mc -> {
				var random = mc.level.getRandom();
				// Off the press: the piston coming down on the star.
				for (int i = 0; i < 6; i++) {
					mc.level.addParticle(dev.forja.registry.ModParticles.CHISPA, press.getX() + 0.4 + random.nextDouble() * 0.2, press.getY() + 1.02,
						press.getZ() + 0.4 + random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.18, 0.12 + random.nextDouble() * 0.12,
						(random.nextDouble() - 0.5) * 0.18);
				}
				// The anvil, struck every half second.
				if (tick % 7 == 0) {
					for (int i = 0; i < 36; i++) {
						mc.level.addParticle(dev.forja.registry.ModParticles.CHISPA, anvil.getX() + 0.5, anvil.getY() + 1.05, anvil.getZ() + 0.5,
							(random.nextDouble() - 0.5) * 0.45, 0.1 + random.nextDouble() * 0.25, (random.nextDouble() - 0.5) * 0.45);
					}
				}
				// Gold dropping into the casting box, spitting sparks where it lands.
				for (int i = 0; i < 2; i++) {
					mc.level.addParticle(dev.forja.registry.ModParticles.GOTA, box.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.1, pot.getY() + 1.6,
						box.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.1, 1.0, 0.78, 0.23);
				}
			});
			context.waitTicks(1);
			frame(context);
		}
	}

	/** The meteor coming down with its fire tail, at night. */
	private static void meteor(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("time set 18000");
		server.runOnServer(s -> dev.forja.world.WorldEvents.stop(connection.getServerLevel()));
		context.runOnClient(mc -> mc.options.fov().set(90));
		double camX = x + 90.5;
		double camZ = z + 92.0;
		double hitX = x + 90.5;
		double hitZ = z + 72.0;
		for (int tick = 0; tick < 30; tick++) {
			server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", camX, y + 2.0, camZ, hitX, y + 12.0, hitZ));
			context.waitTicks(1);
		}
		connection.waitForChunksRender();
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.world.WorldEvents.start(level, dev.forja.world.WorldEvents.METEORITOS);
			dev.forja.world.WorldEvents.meteorForTest(level, new BlockPos((int) hitX, y, (int) hitZ));
		});
		begin("b08_meteorito");
		for (int t = 0; t < 70; t++) {
			server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", camX, y + 2.0, camZ, hitX, y + 12.0, hitZ));
			context.waitTicks(1);
			frame(context);
		}
		server.runOnServer(s -> dev.forja.world.WorldEvents.stop(connection.getServerLevel()));
		context.runOnClient(mc -> {
			mc.options.fov().set(70);
			dev.forja.client.EventBannerHud.dismiss();
		});
		noFire(server, hitX, y, hitZ, 8);
	}

	// ------------------------------------------------------------------------------------------ c: screens

	private static ForgeMenu menu(TestServerConnection connection) {
		return (ForgeMenu) connection.getServerPlayer().containerMenu;
	}

	private static void open(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos at) {
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			connection.getServerPlayer().openMenu(level.getBlockState(at).getMenuProvider(level, at));
		});
		context.waitTicks(10);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
	}

	private static void close(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runOnServer(s -> connection.getServerPlayer().closeContainer());
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(4);
	}

	private static void screens(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 60;
		int pz = z - 40;
		server.runCommand("time set 6000");
		server.runCommand("gamemode creative @a");
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone_bricks", px - 4, y - 1, pz - 4, px + 4, y - 1, pz + 4));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 4, y, pz - 4, px + 4, y + 3, pz + 4));
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f 0 20", px + 0.5, y, pz - 2.5));
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(dev.forja.registry.ModItems.ASCUA, 12));
			player.getInventory().setItem(4, new ItemStack(Items.IRON_INGOT, 32));
			player.getInventory().setItem(8, new ItemStack(Items.COAL, 9));
			player.getInventory().setItem(9, new ItemStack(Items.GOLD_INGOT, 5));
			player.getInventory().setItem(22, Assembler.createPart(PartType.HOJA, ForgeMaterial.HIERRO));
			player.getInventory().setItem(35, new ItemStack(Items.COPPER_INGOT, 16));
		});
		context.waitTicks(20);
		BlockPos at = new BlockPos(px, y, pz);

		// The forge table with its star lit, then the strike.
		safely(context, "mesa_de_forja", () -> {
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_forja", px, y, pz));
			open(context, server, connection, at);
			server.runOnServer(s -> {
				ForgeMenu menu = menu(connection);
				menu.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.DIAMANTE));
				menu.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.MANGO, ForgeMaterial.PIEDRA));
				menu.getSlot(ForgeMenu.STAR_FIRST + 4).set(Assembler.createPart(PartType.ATADURA, ForgeMaterial.ORO));
			});
			context.waitTicks(8);
			begin("c01_mesa_de_forja");
			roll(context, 40, 2);
			server.runOnServer(s -> menu(connection).clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_PRESS + 2));
			context.runOnClient(mc -> ((dev.forja.client.ForgeScreen) mc.gui.screen()).showBurst(2));
			roll(context, 24, 1);
			roll(context, 10, 2);
			close(context, server, connection);
		});

		safely(context, "mesa_mayor", () -> {
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_forja_mayor", px, y, pz));
			open(context, server, connection, at);
			server.runOnServer(s -> {
				ForgeMenu menu = menu(connection);
				menu.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.HOJA, ForgeMaterial.DIAMANTE));
				menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(Assembler.createPart(PartType.HOJA, ForgeMaterial.DIAMANTE));
				menu.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.MANGO, ForgeMaterial.MADERA));
				menu.getSlot(ForgeMenu.STAR_FIRST + 4).set(Assembler.createPart(PartType.GUARDA, ForgeMaterial.ORO));
			});
			context.waitTicks(8);
			begin("c02_mesa_mayor");
			roll(context, 40, 2);
			close(context, server, connection);
		});

		safely(context, "crisol", () -> {
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
				var crucible = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(at);
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST, new ItemStack(Items.IRON_INGOT, 6));
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND, new ItemStack(Items.COAL, 4));
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA, 6));
				for (int tick = 0; tick < 40; tick++) {
					dev.forja.block.entity.CrucibleBlockEntity.serverTick(level, at, level.getBlockState(at), crucible);
				}
				connection.getServerPlayer().openMenu(crucible);
			});
			context.waitTicks(10);
			context.getInput().setCursorPos(0, 0);
			begin("c03_crisol");
			roll(context, 40, 2);
			close(context, server, connection);
			server.runOnServer(s -> connection.getServerLevel().removeBlock(at, false));
			clearMobs(server, connection, px, y, pz, 8);
		});

		safely(context, "caja_de_moldeo", () -> {
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CAJA_DE_MOLDEO.defaultBlockState());
				var box = (dev.forja.block.entity.CastingBoxBlockEntity) level.getBlockEntity(at);
				box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN, Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO));
				box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STEEL, new ItemStack(dev.forja.registry.ModItems.alloy("acero_refractario"), 4));
				for (int tick = 0; tick < 30; tick++) {
					dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, at, level.getBlockState(at), box);
				}
				connection.getServerPlayer().openMenu(box);
			});
			context.waitTicks(10);
			context.getInput().setCursorPos(0, 0);
			begin("c04_caja_de_moldeo");
			roll(context, 40, 2);
			close(context, server, connection);
			server.runOnServer(s -> connection.getServerLevel().removeBlock(at, false));
			clearMobs(server, connection, px, y, pz, 8);
		});

		safely(context, "montadora", () -> {
			BlockPos machineAt = at;
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				level.setBlockAndUpdate(machineAt, block("montadora"));
				level.setBlockAndUpdate(machineAt.east(), block("farol_de_pavesa"));
			});
			open(context, server, connection, machineAt);
			server.runOnServer(s -> {
				var menu = connection.getServerPlayer().containerMenu;
				menu.getSlot(0).set(Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO));
				menu.getSlot(1).set(Assembler.createPart(PartType.MANGO, ForgeMaterial.MADERA));
				menu.getSlot(2).set(Assembler.createPart(PartType.ATADURA, ForgeMaterial.CUERO));
			});
			context.waitTicks(4);
			begin("c05_montadora");
			roll(context, 50, 2);
			close(context, server, connection);
			server.runOnServer(s -> {
				connection.getServerLevel().removeBlock(machineAt, false);
				connection.getServerLevel().removeBlock(machineAt.east(), false);
			});
			clearMobs(server, connection, px, y, pz, 8);
		});

		safely(context, "extraccion", () -> {
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_extraccion", px, y, pz));
			open(context, server, connection, at);
			server.runOnServer(s -> {
				var menu = (dev.forja.menu.ExtractionMenu) connection.getServerPlayer().containerMenu;
				var registries = connection.getServerLevel().registryAccess();
				ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.DIAMANTE, ForgeMaterial.MADERA, ForgeMaterial.ORO), registries);
				sword.set(ModComponents.POTENCIAL, 80);
				sword = dev.forja.upgrade.UpgradeRecipes.upgraded(sword, ForgeType.ESPADA, dev.forja.upgrade.Upgrade.FILO, 80, registries);
				sword = dev.forja.upgrade.UpgradeRecipes.upgraded(sword, ForgeType.ESPADA, dev.forja.upgrade.Upgrade.IRROMPIBLE, 60, registries);
				menu.getSlot(dev.forja.menu.ExtractionMenu.GEAR_SLOT).set(sword);
				menu.getSlot(dev.forja.menu.ExtractionMenu.PAYMENT_FIRST).set(new ItemStack(Items.AMETHYST_SHARD, 10));
				menu.getSlot(dev.forja.menu.ExtractionMenu.ORB_SLOT).set(new ItemStack(dev.forja.registry.ModItems.ORBE_VACIO, 2));
			});
			context.waitTicks(8);
			context.runOnClient(mc -> {
				var screen = (dev.forja.client.ExtractionScreen) mc.gui.screen();
				screen.clickAt(screen.gemPoint(0));
			});
			context.waitTicks(4);
			begin("c06_extraccion");
			roll(context, 20, 2);
			context.runOnClient(mc -> {
				var screen = (dev.forja.client.ExtractionScreen) mc.gui.screen();
				screen.clickAt(screen.buttonPoint());
			});
			roll(context, 16, 1);
			roll(context, 10, 2);
			close(context, server, connection);
			server.runOnServer(s -> connection.getServerLevel().removeBlock(at, false));
			clearMobs(server, connection, px, y, pz, 8);
		});

		// The class choice, then the tree with the three ultimates as golden diamonds.
		safely(context, "clases", () -> {
			context.runOnClient(mc -> mc.gui.setScreen(new dev.forja.client.ClassChoiceScreen(false)));
			context.waitTicks(10);
			context.getInput().setCursorPos(0, 0);
			begin("c07_elegir_clase");
			roll(context, 30, 2);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				ClassProgress.choose(player, PlayerClass.GUERRERO);
				ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
				for (String id : List.of("guerrero.s1.3", "guerrero.s2.1", "guerrero.s3.2", "guerrero.a.tronco_2", "guerrero.b.habilidad_b2")) {
					ArbolGameTests.learnTo(player, id);
				}
				ArbolGameTests.learnTo(player, PlayerClass.GUERRERO.tree().ultimateNode(1, true).id);
			});
			context.waitTicks(5);
			context.runOnClient(mc -> mc.gui.setScreen(new TalentTreeScreen()));
			context.waitForScreen(TalentTreeScreen.class);
			context.getInput().setCursorPos(0, 0);
			begin("c08_arbol");
			roll(context, 16, 2);
			// In towards the three finals, a little at a time.
			for (int i = 0; i < 40; i++) {
				context.runOnClient(mc -> ((TalentTreeScreen) mc.gui.screen()).zoomAt(mc.getWindow().getGuiScaledWidth() * 0.4F,
					mc.getWindow().getGuiScaledHeight() * 0.53F, 1.0075F));
				context.waitTicks(1);
				frame(context);
			}
			roll(context, 14, 2);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			server.runOnServer(s -> ClassProgress.clear(connection.getServerPlayer()));
		});
		server.runOnServer(s -> connection.getServerPlayer().getInventory().clearContent());
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d air", px, y, pz));
	}

	// ------------------------------------------------------------------------------------------ d: HUD

	private static void hudLanes(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("time set 6000");
		server.runCommand("gamemode survival @a");
		int px = x + 140;
		int pz = z + 60;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d smooth_stone", px - 8, y - 1, pz - 8, px + 8, y - 1, pz + 8));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 8, y, pz - 8, px + 8, y + 5, pz + 8));
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f 0 10", px + 0.5, y, pz + 0.5));
		context.waitTicks(20);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var level = connection.getServerLevel();
			player.getInventory().clearContent();
			player.getInventory().setItem(0, Assembler.create(ForgeType.BACULO, List.of(ForgeMaterial.AMATISTA, ForgeMaterial.HIERRO, ForgeMaterial.MADERA), level.registryAccess()));
			player.getInventory().setItem(1, Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.DIAMANTE, ForgeMaterial.MADERA, ForgeMaterial.ORO), level.registryAccess()));
			player.getInventory().setSelectedSlot(0);
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			ItemStack wings = Assembler.create(ForgeType.ALAS, List.of(ForgeMaterial.ORO, ForgeMaterial.CUERO), level.registryAccess());
			wings.set(ModComponents.VUELO, Math.max(1, dev.forja.forge.Flight.maxTicks(wings) / 2));
			player.setItemSlot(EquipmentSlot.CHEST, wings);
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 20 * 120, 2, false, false));
			ClassProgress.choose(player, PlayerClass.GUERRERO);
			ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
			ArbolGameTests.learnTo(player, "guerrero.b.tronco_4");
			ArbolGameTests.learnTo(player, "guerrero.s1.2");
			// The second skill (B) and an ultimate (N), so all three keys are on the bar.
			var tree = PlayerClass.GUERRERO.tree();
			var second = tree.bySlot(dev.forja.clase.ClassTree.Slot.B);
			if (second != null) {
				ArbolGameTests.learnTo(player, second.id);
			}
			var ultimate = tree.ultimateNode(1, false);
			if (ultimate != null) {
				ArbolGameTests.learnTo(player, ultimate.id);
			}
		});
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			for (int blow = 0; blow < dev.forja.upgrade.Frenzy.MAX_HITS; blow++) {
				dev.forja.upgrade.Frenzy.onHit(player);
			}
			dev.forja.magic.Mana.set(player, dev.forja.magic.Mana.max(player) * 0.55F);
			dev.forja.combat.Stamina.set(player, dev.forja.combat.Stamina.max(player) * 0.5F);
		});
		hud(context, true);
		context.waitTicks(30);
		context.runOnClient(mc -> mc.particleEngine.clearParticles());

		// Every bar in its lane; the held item's name and an action-bar line lifted over them.
		begin("d01_carriles");
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
		context.waitTicks(2);
		context.runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(0);
			mc.gui.hud.setOverlayMessage(Component.literal("Grito de guerra II"), false);
		});
		roll(context, 50, 1);

		// V and B: the wait running down as a shutter, the last second in gold, the flash when they are back.
		begin("d02_habilidades");
		int sweep = 72;
		for (int i = 0; i < sweep; i++) {
			double left = 1.0 - (i + 1) / (double) sweep;
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				long now = player.level().getGameTime();
				var data = ClassProgress.data(player);
				for (int slot = 1; slot <= 3; slot++) {
					var skill = dev.forja.clase.ClassSkills.skill(player, slot);
					if (skill == null) {
						continue;
					}
					// Each key a little behind the one before, so the shutters are seen at different heights.
					double share = Math.max(0.0, Math.min(1.0, left * (1.0 + 0.25 * (slot - 1)) - 0.02 * (slot - 1)));
					data = data.withReady(slot, now + Math.round(skill.cooldownTicks(player) * share));
				}
				ClassProgress.set(player, data);
			});
			context.waitTicks(1);
			frame(context);
		}
		roll(context, 24, 1);

		// The framed event banner.
		begin("d03_cartel");
		context.runOnClient(mc -> dev.forja.client.EventBannerHud.show(dev.forja.world.WorldEvents.METEORITOS, true));
		roll(context, 40, 1);
		context.runOnClient(mc -> dev.forja.client.EventBannerHud.show(dev.forja.world.WorldEvents.LUNA_DE_SANGRE, true));
		roll(context, 34, 1);
		context.runOnClient(mc -> dev.forja.client.EventBannerHud.dismiss());
		hud(context, false);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.removeAllEffects();
			player.getInventory().clearContent();
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
			ClassProgress.clear(player);
		});
	}

	// ------------------------------------------------------------------------------------------ e: items and blocks

	/** A page of item icons, large and centred, each with its name under it. */
	static final class Showcase extends Screen {
		private final Component heading;
		private final List<ItemStack> stacks;
		private final float scale;
		private final int columns;

		Showcase(Component heading, List<ItemStack> stacks, float scale, int columns) {
			super(heading);
			this.heading = heading;
			this.stacks = stacks;
			this.scale = scale;
			this.columns = columns;
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			g.fill(0, 0, this.width, this.height, 0xFF1C1814);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			super.extractRenderState(g, mouseX, mouseY, a);
			g.centeredText(this.font, this.heading, this.width / 2, 10, 0xFFF0C878);
			int icon = Math.round(16 * this.scale);
			int cellW = Math.max(icon + 16, 100);
			int cellH = icon + 22;
			int rows = (this.stacks.size() + this.columns - 1) / this.columns;
			int top = Math.max(26, (this.height - rows * cellH) / 2 + 6);
			for (int i = 0; i < this.stacks.size(); i++) {
				int row = i / this.columns;
				int inRow = Math.min(this.columns, this.stacks.size() - row * this.columns);
				int left = (this.width - inRow * cellW) / 2;
				int cx = left + (i % this.columns) * cellW + cellW / 2;
				int y = top + row * cellH;
				g.fill(cx - icon / 2 - 4, y - 4, cx + icon / 2 + 4, y + icon + 4, 0xFF3A322A);
				g.fill(cx - icon / 2 - 3, y - 3, cx + icon / 2 + 3, y + icon + 3, 0xFF8B8378);
				g.pose().pushMatrix();
				g.pose().translate(cx - icon / 2.0F, y);
				g.pose().scale(this.scale, this.scale);
				g.item(this.stacks.get(i), 0, 0);
				g.pose().popMatrix();
				Component name = this.stacks.get(i).getHoverName();
				g.pose().pushMatrix();
				g.pose().translate(cx, y + icon + 7);
				g.pose().scale(0.6F, 0.6F);
				g.centeredText(this.font, name, 0, 0, 0xFFE8DCC8);
				g.pose().popMatrix();
			}
		}
	}

	private static void items(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		gui = true;
		safely(context, "iconos", () -> {
			List<ItemStack> tools = List.of(
				forged(ForgeType.CINCEL, ForgeMaterial.HIERRO, ForgeMaterial.MADERA),
				forged(ForgeType.CINCEL, ForgeMaterial.DIAMANTE, ForgeMaterial.HUESO),
				forged(ForgeType.FAROL, ForgeMaterial.ESMERALDA, ForgeMaterial.ORO, ForgeMaterial.MADERA),
				new ItemStack(dev.forja.registry.ModItems.CINTURON),
				forged(ForgeType.ARMADURA_DE_LOBO, ForgeMaterial.HIERRO, ForgeMaterial.CUERO),
				forged(ForgeType.ARMADURA_DE_LOBO, ForgeMaterial.ORO, ForgeMaterial.CUERO));
			List<ItemStack> moulds = List.of(
				new ItemStack(dev.forja.registry.ModItems.MOLDE_DE_FUNDICION),
				new ItemStack(dev.forja.registry.ModItems.MARCO),
				new ItemStack(dev.forja.registry.ModItems.alloy("peltre")),
				new ItemStack(dev.forja.registry.ModItems.alloy("damasco")));
			context.runOnClient(mc -> mc.gui.setScreen(new Showcase(Component.literal("Objetos redibujados"), tools, 3.0F, 3)));
			context.waitTicks(5);
			hold(context, "e01_objetos");
			context.runOnClient(mc -> mc.gui.setScreen(new Showcase(Component.literal("Moldes, marcos y lingotes"), moulds, 3.0F, 4)));
			context.waitTicks(5);
			hold(context, "e02_moldes");
			context.runOnClient(mc -> mc.gui.setScreen(null));
		});
		gui = false;
		safely(context, "bloques", () -> blocks(context, server, connection, x, y, z));
	}

	private static void blocks(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int ox = x + 40;
		int oz = z + 40;
		int floor = y - 1;
		server.runCommand("gamemode creative @a");
		flying(server, connection, true);
		camera(server, ox + 0.5, y + 2.4, oz - 3.0, ox + 0.5, y + 0.5, oz + 0.5);
		context.waitTicks(30);
		connection.waitForChunksRender();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d polished_andesite", ox - 6, floor, oz - 8, ox + 36, floor, oz + 8));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", ox - 6, floor + 1, oz - 8, ox + 36, floor + 8, oz + 8));
		server.runCommand("time set 6000");
		var lit = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
		List<String> row = List.of("mesa_de_forja", "yunque_del_herrero", "montadora", "caja_de_moldeo", "caja_de_moldeo_de_acero",
			"caja_de_moldeo_de_damasco", "farol_de_pavesa");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (int i = 0; i < row.size(); i++) {
				BlockState state = block(row.get(i)).trySetValue(lit, true);
				level.setBlock(new BlockPos(ox + i * 2, floor + 1, oz), state, Block.UPDATE_ALL);
			}
			// The dimension's ash, loose and trodden.
			for (int dx = 0; dx < 5; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					boolean trodden = Math.abs(dz) <= 0 && dx > 0 && dx < 4;
					level.setBlock(new BlockPos(ox + 15 + dx, floor, oz + dz), block(trodden ? "ceniza_prensada" : "ceniza"), Block.UPDATE_ALL);
				}
			}
			level.setBlock(new BlockPos(ox + 17, floor + 1, oz + 1), block("arma_clavada"), Block.UPDATE_ALL);
			// Forged armour worn: gold, copper, leather.
			List<ForgeMaterial> armour = List.of(ForgeMaterial.ORO, ForgeMaterial.COBRE, ForgeMaterial.CUERO);
			for (int i = 0; i < armour.size(); i++) {
				ForgeMaterial material = armour.get(i);
				ArmorStand stand = new ArmorStand(level, ox + 23.5 + i * 2.2, floor + 1.0, oz + 0.5);
				stand.setYRot(180.0F);
				stand.setYBodyRot(180.0F);
				stand.setYHeadRot(180.0F);
				stand.setNoGravity(true);
				stand.setItemSlot(EquipmentSlot.HEAD, Assembler.create(ForgeType.CASCO, List.of(material, ForgeMaterial.CUERO)));
				stand.setItemSlot(EquipmentSlot.CHEST, Assembler.create(ForgeType.PECHERA, List.of(material, ForgeMaterial.CUERO)));
				stand.setItemSlot(EquipmentSlot.LEGS, Assembler.create(ForgeType.GREBAS, List.of(material, ForgeMaterial.CUERO)));
				stand.setItemSlot(EquipmentSlot.FEET, Assembler.create(ForgeType.BOTAS, List.of(material, ForgeMaterial.CUERO)));
				level.addFreshEntity(stand);
			}
		});
		context.waitTicks(10);
		// Along the row, close and a little above, so the lids of the casting boxes read from the top.
		double startX = ox - 1.0;
		double endX = ox + 25.0;
		camera(server, startX, y + 2.2, oz - 3.0, startX + 1.0, y + 0.6, oz + 0.5);
		context.waitTicks(15);
		begin("e03_bloques");
		int count = 210;
		for (int i = 0; i < count; i++) {
			double k = i / (double) (count - 1);
			double along = startX + (endX - startX) * k;
			double lookY = along > ox + 22 ? y + 1.1 : y + 0.6;
			camera(server, along, y + 2.2, oz - 3.0, along + 1.0, lookY, oz + 0.5);
			context.waitTicks(1);
			frame(context);
		}
		// The ember lantern and the lit lids at dusk.
		server.runCommand("time set 13200");
		camera(server, ox + 9.0, y + 2.6, oz - 3.2, ox + 9.0, y + 0.5, oz + 0.5);
		context.waitTicks(20);
		begin("e04_bloques_anochecer");
		for (int i = 0; i < 60; i++) {
			double along = ox + 7.5 + i * 0.07;
			camera(server, along, y + 2.6, oz - 3.2, along, y + 0.5, oz + 0.5);
			context.waitTicks(1);
			frame(context);
		}
		server.runCommand("time set 6000");
		clearMobs(server, connection, ox + 15, y, oz, 30);
		flying(server, connection, false);
	}

	// ------------------------------------------------------------------------------------------ f: books

	/** The spreads of a book that carry one of the new page elements (Formula, TablePanel), found on its pages. */
	private static List<Integer> spreadsWith(GuideBookScreen book, int limit) {
		List<Integer> found = new ArrayList<>();
		List<Integer> plain = new ArrayList<>();
		try {
			int count = book.pageCount();
			var field = GuideBookScreen.class.getDeclaredField("pages");
			field.setAccessible(true);
			List<?> pages = (List<?>) field.get(book);
			for (int i = 0; i < count; i++) {
				boolean special = false;
				for (Object element : (List<?>) pages.get(i)) {
					String kind = element.getClass().getSimpleName();
					special |= kind.equals("Formula") || kind.equals("TablePanel");
				}
				int spread = i / 2;
				if (special && !found.contains(spread)) {
					found.add(spread);
				} else if (!plain.contains(spread)) {
					plain.add(spread);
				}
			}
		} catch (ReflectiveOperationException failure) {
			log("video_visual: no pude leer las paginas: " + failure);
		}
		log("video_visual: pliegos con formula o mesa: " + found);
		List<Integer> chosen = new ArrayList<>(found.subList(0, Math.min(limit, found.size())));
		for (int spread : plain) {
			if (chosen.size() >= limit) {
				break;
			}
			if (spread > 0 && !chosen.contains(spread)) {
				chosen.add(spread);
			}
		}
		chosen.sort(Integer::compare);
		return chosen;
	}

	private static void books(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runCommand("gamemode survival @a");
		int index = 1;
		for (dev.forja.GuideBooks.Book which : List.of(dev.forja.GuideBooks.Book.CUADERNO, dev.forja.GuideBooks.Book.YUNQUE)) {
			int limit = which == dev.forja.GuideBooks.Book.CUADERNO ? 7 : 4;
			List<Integer> spreads = context.computeOnClient(mc -> spreadsWith(new GuideBookScreen(which), limit));
			context.runOnClient(mc -> mc.gui.setScreen(new GuideBookScreen(which)));
			context.waitForScreen(GuideBookScreen.class);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(6);
			hold(context, String.format(Locale.ROOT, "f%02d_%s_portada", index++, which.name().toLowerCase(Locale.ROOT)));
			for (int spread : spreads) {
				context.runOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).goToPage(spread * 2));
				context.waitTicks(4);
				hold(context, String.format(Locale.ROOT, "f%02d_%s_pliego%02d", index++, which.name().toLowerCase(Locale.ROOT), spread));
			}
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(4);
		}
	}

	// ------------------------------------------------------------------------------------------ g: the star dimension's sky

	private static void sky(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		String dim = dev.forja.world.StarYard.LEVEL.identifier().toString();
		int surface = dev.forja.world.StarYard.SURFACE;
		server.runCommand("gamemode spectator @a");
		double ex = 0.5;
		double ey = surface + 2.0;
		double ez = -20.5;
		for (int t = 0; t < 10; t++) {
			server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", dim, ex, ey, ez, ex + 1, ey + 30, ez + 30));
			context.waitTicks(1);
		}
		context.waitTicks(160);
		context.runOnClient(mc -> mc.options.fov().set(80));
		begin("g01_cielo");
		for (int i = 0; i < 150; i++) {
			double yaw = Math.toRadians(-30 + i * 0.4);
			server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", dim, ex, ey, ez,
				ex + Math.sin(yaw) * 30, ey + 22 + i * 0.05, ez + Math.cos(yaw) * 30));
			context.waitTicks(1);
			frame(context);
		}
		context.runOnClient(mc -> mc.options.fov().set(70));
		server.runCommand("execute in minecraft:overworld run tp @a 0 100 0");
		context.waitTicks(20);
		server.runCommand("gamemode survival @a");
	}
}
