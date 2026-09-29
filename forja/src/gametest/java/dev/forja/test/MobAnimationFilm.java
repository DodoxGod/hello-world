package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import dev.forja.combat.CombatAnim;
import dev.forja.combat.CombatFeedback;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's own monsters, filmed doing everything they do (FORJA_SOLO=animaciones_mobs): standing, walking and
 * running past the camera, warning of a blow and landing it, each of their special moves wound up and
 * landing, staggered, flinching from a blow, hopping back and dying. Everything is started by hand on the
 * server so each run films the same moments; the mob's own AI stays off. Andy, 2026-09-28: "haz animaciones
 * para los mobs del mod que las necesitan". The shots are laid out per mob by tools/hoja_animaciones_mobs.py.
 *
 * <p>An animation only advances while its mob is drawn, so the camera is on the mob the whole time, not only
 * for the shots.
 */
final class MobAnimationFilm {
	/** A blow's warning, as MeleeAttackGoalMixin gives it at the base config: the shot is three quarters in. */
	private static final int WARN_TICKS = 8;
	private static final int STAGGER_TICKS = 30;
	/** How long a pass across the camera lasts, and how fast, in blocks a tick. */
	private static final int PASS_TICKS = 20;
	private static final double WALK_SPEED = 0.12;
	private static final double RUN_SPEED = 0.22;

	/**
	 * One move of a mob's own: started on the server, filmed at each of {@code shots} ticks after that.
	 *
	 * @param after ticks to let it finish before the next step
	 */
	record Move(String name, BiConsumer<Mob, ArmorStand> start, int[] shots, int after, double targetAt) {
		Move(String name, BiConsumer<Mob, ArmorStand> start, int... shots) {
			this(name, start, shots, 30, 2.5);
		}

		Move at(double distance) {
			return new Move(this.name, this.start, this.shots, this.after, distance);
		}

		Move rest(int ticks) {
			return new Move(this.name, this.start, this.shots, ticks, this.targetAt);
		}
	}

	/**
	 * @param melee   whether it has a basic blow (warned of, then swung)
	 * @param dies    whether a plain death is filmed (the hauler dies of its own fuse instead)
	 */
	record Subject(EntityType<? extends Mob> type, String id, boolean melee, boolean dies, List<Move> moves) {
	}

	private MobAnimationFilm() {
	}

	private static ServerLevel level(Mob mob) {
		return (ServerLevel) mob.level();
	}

	/** Sends the smith back to his forge, which only his health does in a fight. */
	private static void reforge(dev.forja.entity.FallenSmith smith) {
		try {
			var start = dev.forja.entity.FallenSmith.class.getDeclaredMethod("startReforge", ServerLevel.class);
			start.setAccessible(true);
			start.invoke(smith, level(smith));
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError("could not send the smith to his forge", failure);
		}
	}

	/** Plays one of a mob's own clips as its code would, for the moves that have no public way in. */
	private static void trigger(Mob mob, String controller, String animation) {
		((com.geckolib.animatable.GeoEntity) mob).triggerAnim(controller, animation);
	}

	static List<Subject> subjects() {
		List<Subject> all = new ArrayList<>();
		all.add(new Subject(ModEntities.HERRERO_CAIDO, "herrero_caido", true, true, List.of(
			new Move("reves", (m, t) -> ((dev.forja.entity.FallenSmith) m).backhand(level(m)), 5, 11),
			new Move("onda", (m, t) -> ((dev.forja.entity.FallenSmith) m).anvilWave(level(m)), 30, 55).rest(40),
			new Move("garfio", (m, t) -> ((dev.forja.entity.FallenSmith) m).hookIn(level(m), t), 6, 10).at(7.0),
			new Move("rugido", (m, t) -> trigger(m, "boss", "roar"), 12),
			// Back at the forge (FallenSmith.startReforge is his own to call): the slam, then the hammering.
			new Move("reforja", (m, t) -> reforge((dev.forja.entity.FallenSmith) m), 30, 85).rest(10))));
		all.add(new Subject(ModEntities.AUTOMATA, "automata_de_forja", true, true, List.of(
			new Move("coz", (m, t) -> ((dev.forja.entity.ForgeAutomaton) m).slagStomp(level(m), t), 20, 37).rest(40),
			new Move("vapor", (m, t) -> ((dev.forja.entity.ForgeAutomaton) m).steamPurge(level(m)), 4),
			new Move("escupe", (m, t) -> trigger(m, "automata", "vent"), 10, 15))));
		all.add(new Subject(ModEntities.CORAZA, "coraza_vacia", true, true, List.of(
			new Move("embestida", (m, t) -> ((dev.forja.entity.HollowArmor) m).lunge(level(m), t), 18, 34).at(6.0).rest(40),
			new Move("lamento", (m, t) -> ((dev.forja.entity.HollowArmor) m).wail(level(m)), 8),
			// Playing dead (ai.ForjaTraits) and getting up again.
			new Move("finge", (m, t) -> ((dev.forja.entity.HollowArmor) m).feignDeath(), 15, 55).rest(8),
			new Move("levanta", (m, t) -> ((dev.forja.entity.HollowArmor) m).rise(), 8, 16))));
		all.add(new Subject(ModEntities.PAVESA, "pavesa", true, true, List.of(
			new Move("picado", (m, t) -> ((dev.forja.entity.EmberWisp) m).diveAt(level(m), t), 6, 11).at(5.0),
			new Move("avivada", (m, t) -> ((dev.forja.entity.EmberWisp) m).stoke(), 12))));
		all.add(new Subject(ModEntities.HERRUMBRE, "herrumbre", true, true, List.of()));
		all.add(new Subject(ModEntities.ASCUA_MAYOR, "ascua_mayor", true, true, List.of(
			new Move("picado", (m, t) -> ((dev.forja.entity.GreaterEmber) m).diveAt(level(m), t), 8, 14).at(6.0))));
		all.add(new Subject(ModEntities.ESCORIA, "escoria_viviente", true, true, List.of()));
		all.add(new Subject(ModEntities.YUNQUE_ANDANTE, "yunque_andante", true, true, List.of(
			new Move("soldar", (m, t) -> trigger(m, "yunque", "weld"), 8))));
		all.add(new Subject(ModEntities.PERCUTOR, "percutor", true, true, List.of(
			new Move("martillazo", (m, t) -> ((dev.forja.entity.Striker) m).drop(level(m)), 20, 39).rest(40))));
		all.add(new Subject(ModEntities.TENAZA, "tenaza", true, true, List.of(
			new Move("agarre", (m, t) -> ((dev.forja.entity.Tongs) m).grab(level(m), t), 10, 19, 45).at(4.0).rest(40))));
		all.add(new Subject(ModEntities.TEMPLADOR, "templador", false, true, List.of(
			new Move("aceite", (m, t) -> ((dev.forja.entity.Quencher) m).douse(level(m), t.position()), 12, 23).at(5.0))));
		all.add(new Subject(ModEntities.NUCLEO_ESTELAR, "nucleo_estelar", false, true, List.of(
			new Move("lleno", (m, t) -> ((dev.forja.entity.StarCore) m).fillForTest(), 12),
			new Move("descarga", (m, t) -> m.hurtServer(level(m), m.damageSources().mobAttack(t), 30.0F), 8, 20, 40).at(5.0).rest(40))));
		all.add(new Subject(ModEntities.GUARDIAN_DE_CUNO, "guardian_de_cuno", true, true, List.of(
			new Move("sello", (m, t) -> ((dev.forja.entity.CuneGuardian) m).stamp(level(m), t.position()), 22, 41).at(4.0).rest(40),
			new Move("abierto", (m, t) -> trigger(m, "cuno", "unsealed"), 7))));
		// Last: its fuse is its death, and it sets the floor on fire.
		all.add(new Subject(ModEntities.CARGADOR_DE_CARBON, "cargador_de_carbon", true, false, List.of(
			new Move("mecha", (m, t) -> ((dev.forja.entity.CoalHauler) m).prime(level(m)), 18, 35, 44).rest(30))));
		return all;
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("difficulty easy");
		// Creative and flying rather than spectator, which draws invisible things (the dummy target) see-through.
		server.runCommand("gamemode creative @a");
		server.runOnServer(s -> {
			var player = connection.getServerPlayer();
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		});
		server.runCommand("gamerule doMobLoot false");
		int sx = x + 300;
		int sz = z + 40;
		// Out here first, so the stage's chunks are loaded before anything is built or spawned on them.
		camera(server, sx + 0.5, y + 3.0, sz + 8.5, sx + 0.5, y + 1.0, sz + 0.5);
		context.waitTicks(30);
		connection.waitForChunksRender();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d smooth_stone", sx - 14, y - 1, sz - 14, sx + 14, y - 1, sz + 14));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", sx - 14, y, sz - 14, sx + 14, y + 10, sz + 14));
		context.runOnClient(mc -> {
			mc.options.fov().set(60);
			mc.options.fovEffectScale().set(0.0);
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		String only = System.getenv("FORJA_MOBS");
		for (Subject subject : subjects()) {
			if (only != null && !only.isBlank() && !List.of(only.split(",")).contains(subject.id())) {
				continue;
			}
			filmOne(context, server, connection, subject, sx + 0.5, y, sz + 0.5);
		}
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.fov().set(70);
			mc.options.fovEffectScale().set(1.0);
		});
		server.runCommand("gamerule doMobLoot true");
		server.runOnServer(s -> {
			var player = connection.getServerPlayer();
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		});
		server.runCommand("gamemode survival @a");
		server.runCommand("difficulty peaceful");
	}

	private static void filmOne(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, Subject subject,
		double px, int y, double pz) {
		clear(server, connection, px, y, pz);
		context.runOnClient(mc -> mc.particleEngine.clearParticles());
		int[] ids = server.computeOnServer(s -> {
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
			ArmorStand stand = new ArmorStand(level, px, y, pz + 2.5);
			stand.setInvisible(true);
			stand.setInvulnerable(true);
			stand.setNoGravity(true);
			level.addFreshEntity(stand);
			return new int[] {mob.getId(), stand.getId()};
		});
		int id = ids[0];
		int standId = ids[1];
		double height = server.computeOnServer(s -> (double) connection.getServerLevel().getEntity(id).getBbHeight());
		// Seen from the front and a little to its right, far enough back for the whole of it and a leap.
		double distance = Math.max(3.0, height * 1.35 + 1.4);
		double angle = Math.toRadians(32.0);
		double camX = px - Math.sin(angle) * distance;
		double camZ = pz + Math.cos(angle) * distance;
		double lookY = y + height * 0.55;
		double eyeY = y + height * 0.55 + distance * 0.18;
		Runnable aim = () -> camera(server, camX, eyeY, camZ, px, lookY, pz);
		aim.run();
		context.waitTicks(25);
		int[] n = {0};
		String prefix = "mobanim_" + subject.id() + "_";
		aim.run();
		shot(context, prefix, n, "quieto");

		// Walking and running past, side-on to the camera.
		pass(context, server, connection, id, px, y, pz, false, prefix, n, "andando");
		pass(context, server, connection, id, px, y, pz, true, prefix, n, "corriendo");
		placeBack(server, connection, id, px, y, pz);
		aim.run();
		context.waitTicks(15);

		if (subject.melee()) {
			// The warning of a basic blow, three quarters in, and the blow itself.
			server.runOnServer(s -> CombatFeedback.telegraph(connection.getServerLevel().getEntity(id), WARN_TICKS));
			context.waitTicks(WARN_TICKS * 3 / 4);
			shot(context, prefix, n, "aviso");
			context.waitTicks(WARN_TICKS - WARN_TICKS * 3 / 4);
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				Mob mob = (Mob) level.getEntity(id);
				mob.swing(InteractionHand.MAIN_HAND);
				mob.doHurtTarget(level, level.getEntity(standId));
			});
			context.waitTicks(2);
			shot(context, prefix, n, "golpe_a");
			context.waitTicks(3);
			shot(context, prefix, n, "golpe_b");
			context.waitTicks(35);
			// Once the blow is over it has to be back on its own clips, not frozen in its rest pose.
			log(subject.id() + " tras el golpe: " + playing(context, id));
			walksAgain(context, server, connection, subject, id, px, y, pz, "el golpe");
		}

		for (Move move : subject.moves()) {
			double at = move.targetAt();
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				Mob mob = (Mob) level.getEntity(id);
				ArmorStand stand = (ArmorStand) level.getEntity(standId);
				stand.setPos(px, y, pz + at);
				face(mob, 0.0F);
				move.start().accept(mob, stand);
			});
			int waited = 0;
			for (int shotAt : move.shots()) {
				for (; waited < shotAt; waited++) {
					if (waited % 5 == 0) {
						aim.run();
					}
					context.waitTicks(1);
				}
				if (!alive(context, id)) {
					break;
				}
				shot(context, prefix, n, move.name() + "_" + shotAt);
			}
			if (!alive(context, id)) {
				break;
			}
			context.waitTicks(move.after());
			log(subject.id() + " tras " + move.name() + ": " + playing(context, id));
			// Whatever a move set alight goes out (and a smith at his forge leaves it once his embers do).
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air replace fire",
				(int) px - 14, y, (int) pz - 14, (int) px + 14, y + 3, (int) pz + 14));
			context.waitTicks(12);
			server.runOnServer(s -> {
				Mob mob = (Mob) connection.getServerLevel().getEntity(id);
				if (mob != null) {
					mob.setTarget(null);
				}
			});
			placeBack(server, connection, id, px, y, pz);
		}
		if (!alive(context, id)) {
			clear(server, connection, px, y, pz);
			return;
		}
		if (!subject.moves().isEmpty() && subject.type() != ModEntities.NUCLEO_ESTELAR) {
			walksAgain(context, server, connection, subject, id, px, y, pz, "sus especiales");
		}
		aim.run();

		// Staggered: the posture broken.
		server.runOnServer(s -> CombatFeedback.stagger(connection.getServerLevel().getEntity(id), STAGGER_TICKS));
		context.waitTicks(6);
		shot(context, prefix, n, "aturdido_a");
		context.waitTicks(8);
		shot(context, prefix, n, "aturdido_b");
		context.waitTicks(STAGGER_TICKS);

		// A blow taken.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			Mob mob = (Mob) level.getEntity(id);
			mob.setInvulnerable(false);
			mob.hurtServer(level, level.damageSources().generic(), 1.0F);
			mob.setInvulnerable(true);
		});
		context.waitTicks(2);
		shot(context, prefix, n, "herido");
		context.waitTicks(15);

		// The hop back after a blow lands (ai.HopBack): the crouch, then the hop.
		server.runOnServer(s -> CombatAnim.broadcast(connection.getServerLevel().getEntity(id), CombatAnim.Kind.LUNGE, 3));
		context.waitTicks(2);
		shot(context, prefix, n, "salto_agacha");
		server.runOnServer(s -> {
			Mob mob = (Mob) connection.getServerLevel().getEntity(id);
			mob.setNoAi(false);
			mob.setDeltaMovement(0.0, 0.3, -0.5);
			mob.hurtMarked = true;
			CombatAnim.broadcast(mob, CombatAnim.Kind.LEAP, 20, CombatAnim.Kind.LEAP_BACK, 0.0F);
		});
		context.waitTicks(4);
		shot(context, prefix, n, "salto_aire");
		context.waitTicks(20);
		placeBack(server, connection, id, px, y, pz);
		aim.run();
		context.waitTicks(10);

		if (subject.dies()) {
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				Mob mob = (Mob) level.getEntity(id);
				mob.setInvulnerable(false);
				mob.kill(level);
			});
			context.waitTicks(4);
			shot(context, prefix, n, "muerte_a");
			context.waitTicks(6);
			shot(context, prefix, n, "muerte_b");
			context.waitTicks(6);
			shot(context, prefix, n, "muerte_c");
			context.waitTicks(10);
		}
		clear(server, connection, px, y, pz);
	}

	/** What each of a mob's controllers is playing on the client, and whether it is moving any bone. */
	static String playing(ClientGameTestContext context, int id) {
		return context.computeOnClient(mc -> {
			if (!(mc.level.getEntity(id) instanceof com.geckolib.animatable.GeoEntity geo)) {
				return "(no mob)";
			}
			StringBuilder out = new StringBuilder();
			geo.getAnimatableInstanceCache().getManagerForId(id).getAnimationControllers().forEach((name, controller) -> {
				var raw = controller.getCurrentRawAnimation();
				out.append(name).append('=').append(raw == null ? "none" : raw.getAnimationStages().get(0).animationName())
					.append(controller.isAnimatingBones() ? "" : "(parado)").append(' ');
			});
			return out.toString().trim();
		});
	}

	/**
	 * After a clip the server triggered, moved, it walks again. GeckoLib keeps a triggered clip as the
	 * controller's clip once it is over and never asks the controller's own logic again, so every one of these
	 * monsters used to stop dead in its rest pose after its first blow or special (entity.MobMoves).
	 */
	private static void walksAgain(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
		Subject subject, int id, double px, int y, double pz, String after) {
		for (int t = 1; t <= 10; t++) {
			double along = t * 0.12;
			server.runOnServer(s -> {
				Mob mob = (Mob) connection.getServerLevel().getEntity(id);
				mob.setPos(px + along, y, pz);
				face(mob, -90.0F);
			});
			context.waitTicks(1);
		}
		String now = playing(context, id);
		log(subject.id() + " andando tras " + after + ": " + now);
		boolean moving = now.contains("=walk") || now.contains("=fly") || now.contains("=run") || now.contains("=flare");
		if (now.contains("(parado)") || !moving) {
			throw new AssertionError(subject.id() + " should walk again after " + after + ", its controllers play: " + now);
		}
		placeBack(server, connection, id, px, y, pz);
		context.waitTicks(5);
	}

	private static void log(String message) {
		System.out.println("[forja-test] " + message);
	}

	private static boolean alive(ClientGameTestContext context, int id) {
		return context.computeOnClient(mc -> mc.level.getEntity(id) instanceof LivingEntity living && living.isAlive());
	}

	private static void face(Mob mob, float yaw) {
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
	}

	/** Back on its spot, facing the camera's side, AI off again. */
	private static void placeBack(TestServerContext server, TestServerConnection connection, int id, double px, int y, double pz) {
		server.runOnServer(s -> {
			Mob mob = (Mob) connection.getServerLevel().getEntity(id);
			if (mob == null) {
				return;
			}
			mob.setNoAi(true);
			mob.setSprinting(false);
			mob.setDeltaMovement(Vec3.ZERO);
			mob.snapTo(px, y, pz, 0.0F, 0.0F);
			face(mob, 0.0F);
		});
	}

	/** Across the camera's view from its left to its right, walking or running, filmed twice on the way. */
	private static void pass(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int id,
		double px, int y, double pz, boolean run, String prefix, int[] n, String name) {
		double speed = run ? RUN_SPEED : WALK_SPEED;
		double start = -PASS_TICKS * speed * 0.6;
		server.runOnServer(s -> {
			Mob mob = (Mob) connection.getServerLevel().getEntity(id);
			mob.setSprinting(false);
			mob.snapTo(px + start, y, pz, -90.0F, 0.0F);
			face(mob, -90.0F);
		});
		context.waitTicks(6);
		for (int t = 1; t <= PASS_TICKS; t++) {
			double along = start + t * speed;
			server.runOnServer(s -> {
				Mob mob = (Mob) connection.getServerLevel().getEntity(id);
				mob.setSprinting(run);
				mob.setPos(px + along, y, pz);
				face(mob, -90.0F);
			});
			context.waitTicks(1);
			if (t == PASS_TICKS / 2 || t == PASS_TICKS - 2) {
				shot(context, prefix, n, name + (t == PASS_TICKS / 2 ? "_a" : "_b"));
			}
		}
	}

	private static void camera(TestServerContext server, double cx, double cy, double cz, double tx, double ty, double tz) {
		double flat = Math.sqrt((tx - cx) * (tx - cx) + (tz - cz) * (tz - cz));
		float yaw = (float) -Math.toDegrees(Math.atan2(tx - cx, tz - cz));
		float pitch = (float) Math.toDegrees(Math.atan2(cy - ty, Math.max(0.01, flat)));
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", cx, cy - 1.62, cz, yaw, pitch));
	}

	private static void shot(ClientGameTestContext context, String prefix, int[] n, String name) {
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "%s%02d_%s", prefix, n[0]++, name))
			.disableCounterPrefix().withSize(960, 540));
	}

	/** Everything the last subject left on the stage: mobs, the dummy, its loot, its fire and its rings. */
	private static void clear(TestServerContext server, TestServerConnection connection, double px, int y, double pz) {
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			AABB box = new AABB(px - 16, y - 2, pz - 16, px + 16, y + 12, pz + 16);
			for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null, box,
				e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
				entity.discard();
			}
		});
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air replace fire",
			(int) px - 14, y, (int) pz - 14, (int) px + 14, y + 3, (int) pz + 14));
	}
}
