package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.forja.combat.CombatFeedback;
import dev.forja.registry.ModEntities;
import dev.forja.world.ApprenticeKits;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

/**
 * How the mod's monsters look, rather than how they move (FORJA_SOLO=visual_mobs): every one of them from the
 * front, the side and three quarters, standing and warning of a blow, at noon and at midnight, so a texture,
 * a glowmask or a silhouette is judged where it is seen. Then the Fallen Smith's apprentices, the vanilla bodies
 * that carry forged plate and forged weapons. The shots are laid out by tools/visual_mobs.py (hoja).
 *
 * <p>FORJA_MOBS=pavesa,tenaza films only those; FORJA_MOBS=aprendices only the apprentices.
 */
final class VisualMobsFootage {
	private static final int WARN_TICKS = 8;

	private VisualMobsFootage() {
	}

	record Subject(EntityType<? extends Mob> type, String id, boolean melee) {
	}

	static List<Subject> subjects() {
		List<Subject> all = new ArrayList<>();
		all.add(new Subject(ModEntities.HERRERO_CAIDO, "herrero_caido", true));
		all.add(new Subject(ModEntities.AUTOMATA, "automata_de_forja", true));
		all.add(new Subject(ModEntities.CORAZA, "coraza_vacia", true));
		all.add(new Subject(ModEntities.PAVESA, "pavesa", true));
		all.add(new Subject(ModEntities.HERRUMBRE, "herrumbre", true));
		all.add(new Subject(ModEntities.ASCUA_MAYOR, "ascua_mayor", true));
		all.add(new Subject(ModEntities.ESCORIA, "escoria_viviente", true));
		all.add(new Subject(ModEntities.YUNQUE_ANDANTE, "yunque_andante", true));
		all.add(new Subject(ModEntities.PERCUTOR, "percutor", true));
		all.add(new Subject(ModEntities.TENAZA, "tenaza", true));
		all.add(new Subject(ModEntities.CARGADOR_DE_CARBON, "cargador_de_carbon", true));
		all.add(new Subject(ModEntities.TEMPLADOR, "templador", false));
		all.add(new Subject(ModEntities.NUCLEO_ESTELAR, "nucleo_estelar", false));
		all.add(new Subject(ModEntities.MOLDE_ROTO, "molde_roto", true));
		all.add(new Subject(ModEntities.GUARDIAN_DE_CUNO, "guardian_de_cuno", true));
		return all;
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("weather clear");
		server.runCommand("difficulty easy");
		server.runCommand("gamemode creative @a");
		server.runCommand("gamerule doDaylightCycle false");
		server.runCommand("gamerule doMobLoot false");
		server.runOnServer(s -> {
			var player = connection.getServerPlayer();
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		});
		int sx = x + 340;
		int sz = z + 60;
		camera(server, sx + 0.5, y + 3.0, sz + 8.5, sx + 0.5, y + 1.0, sz + 0.5);
		context.waitTicks(30);
		connection.waitForChunksRender();
		// A floor that is neither white nor black, so dark plate and pale steel both stand off it.
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d polished_andesite", sx - 14, y - 1, sz - 14, sx + 14, y - 1, sz + 14));
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
		List<String> wanted = only == null || only.isBlank() ? List.of() : List.of(only.split(","));
		for (Subject subject : subjects()) {
			if (!wanted.isEmpty() && !wanted.contains(subject.id())) {
				continue;
			}
			filmOne(context, server, connection, subject, sx + 0.5, y, sz + 0.5);
		}
		if (wanted.isEmpty() || wanted.contains("aprendices")) {
			filmApprentices(context, server, connection, sx + 0.5, y, sz + 0.5);
		}
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.fov().set(70);
			mc.options.fovEffectScale().set(1.0);
		});
		server.runCommand("gamerule doMobLoot true");
		server.runCommand("gamerule doDaylightCycle true");
		server.runCommand("time set noon");
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
		server.runCommand("time set noon");
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
		double distance = Math.max(2.6, height * 1.25 + 1.3);
		String prefix = "visualmob_" + subject.id() + "_";
		int[] n = {0};
		// Front, three quarters and side, at noon; three quarters and front again at midnight.
		Runnable front = () -> view(server, px, y, pz, height, distance, 0.0);
		Runnable threeQuarter = () -> view(server, px, y, pz, height, distance, 40.0);
		Runnable side = () -> view(server, px, y, pz, height, distance, 90.0);
		front.run();
		context.waitTicks(30);
		front.run();
		shot(context, prefix, n, "dia_frente");
		threeQuarter.run();
		context.waitTicks(6);
		shot(context, prefix, n, "dia_34");
		side.run();
		context.waitTicks(6);
		shot(context, prefix, n, "dia_lado");
		// Walking, side on: the gait is half of how a monster reads.
		server.runOnServer(s -> {
			Mob mob = (Mob) connection.getServerLevel().getEntity(id);
			face(mob, -90.0F);
		});
		side.run();
		for (int t = 0; t < 14; t++) {
			double along = -0.8 + t * 0.12;
			server.runOnServer(s -> {
				Mob mob = (Mob) connection.getServerLevel().getEntity(id);
				mob.setPos(px + along, y, pz);
				face(mob, -90.0F);
			});
			context.waitTicks(1);
		}
		shot(context, prefix, n, "dia_andando");
		place(server, connection, id, px, y, pz);
		threeQuarter.run();
		context.waitTicks(20);
		// The warning of a blow, then the blow, or the first special of the ones that have no blow.
		if (subject.melee()) {
			server.runOnServer(s -> CombatFeedback.telegraph(connection.getServerLevel().getEntity(id), WARN_TICKS));
			context.waitTicks(WARN_TICKS * 3 / 4);
			shot(context, prefix, n, "dia_aviso");
			context.waitTicks(WARN_TICKS - WARN_TICKS * 3 / 4);
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				Mob mob = (Mob) level.getEntity(id);
				mob.swing(InteractionHand.MAIN_HAND);
				mob.doHurtTarget(level, level.getEntity(standId));
			});
			context.waitTicks(3);
			shot(context, prefix, n, "dia_golpe");
		} else {
			for (MobAnimationFilm.Subject film : MobAnimationFilm.subjects()) {
				if (film.id().equals(subject.id()) && !film.moves().isEmpty()) {
					MobAnimationFilm.Move move = film.moves().get(0);
					server.runOnServer(s -> {
						ServerLevel level = connection.getServerLevel();
						Mob mob = (Mob) level.getEntity(id);
						ArmorStand stand = (ArmorStand) level.getEntity(standId);
						stand.setPos(px, y, pz + move.targetAt());
						move.start().accept(mob, stand);
					});
					context.waitTicks(move.shots()[0]);
					shot(context, prefix, n, "dia_ataque");
				}
			}
		}
		context.waitTicks(40);
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air replace fire",
			(int) px - 14, y, (int) pz - 14, (int) px + 14, y + 3, (int) pz + 14));
		place(server, connection, id, px, y, pz);
		server.runCommand("time set midnight");
		threeQuarter.run();
		context.waitTicks(20);
		shot(context, prefix, n, "noche_34");
		front.run();
		context.waitTicks(6);
		shot(context, prefix, n, "noche_frente");
		clear(server, connection, px, y, pz);
		server.runCommand("time set noon");
	}

	/** Five apprentices side by side, dressed as the smith dresses them: the vanilla bodies wearing forged plate. */
	private static void filmApprentices(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
		double px, int y, double pz) {
		clear(server, connection, px, y, pz);
		server.runCommand("time set noon");
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
		int[] n = {0};
		String prefix = "visualmob_aprendices_";
		camera(server, px, y + 1.9, pz + 5.2, px, y + 1.1, pz);
		context.waitTicks(30);
		shot(context, prefix, n, "dia_frente");
		camera(server, px + 3.6, y + 1.9, pz + 3.8, px, y + 1.1, pz);
		context.waitTicks(6);
		shot(context, prefix, n, "dia_34");
		camera(server, px - 1.5, y + 1.5, pz + 2.4, px - 1.5, y + 1.2, pz);
		context.waitTicks(6);
		shot(context, prefix, n, "dia_cerca");
		server.runCommand("time set midnight");
		camera(server, px, y + 1.9, pz + 5.2, px, y + 1.1, pz);
		context.waitTicks(20);
		shot(context, prefix, n, "noche_frente");
		clear(server, connection, px, y, pz);
		server.runCommand("time set noon");
	}

	/** The camera on a circle round the mob: 0 is straight in front of it, 90 its left side. */
	private static void view(TestServerContext server, double px, int y, double pz, double height, double distance, double degrees) {
		double angle = Math.toRadians(degrees);
		double camX = px - Math.sin(angle) * distance;
		double camZ = pz + Math.cos(angle) * distance;
		double lookY = y + height * 0.5;
		double eyeY = y + height * 0.5 + distance * 0.16;
		camera(server, camX, eyeY, camZ, px, lookY, pz);
	}

	private static void face(Mob mob, float yaw) {
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
	}

	private static void place(TestServerContext server, TestServerConnection connection, int id, double px, int y, double pz) {
		server.runOnServer(s -> {
			Mob mob = (Mob) connection.getServerLevel().getEntity(id);
			if (mob == null) {
				return;
			}
			mob.setNoAi(true);
			mob.setTarget(null);
			mob.snapTo(px, y, pz, 0.0F, 0.0F);
			face(mob, 0.0F);
		});
	}

	private static void camera(TestServerContext server, double cx, double cy, double cz, double tx, double ty, double tz) {
		double flat = Math.sqrt((tx - cx) * (tx - cx) + (tz - cz) * (tz - cz));
		float yaw = (float) -Math.toDegrees(Math.atan2(tx - cx, tz - cz));
		float pitch = (float) Math.toDegrees(Math.atan2(cy - ty, Math.max(0.01, flat)));
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", cx, cy - 1.62, cz, yaw, pitch));
	}

	private static void shot(ClientGameTestContext context, String prefix, int[] n, String name) {
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.particleEngine.clearParticles();
		});
		context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "%s%02d_%s", prefix, n[0]++, name))
			.disableCounterPrefix().withSize(960, 540));
	}

	private static void clear(TestServerContext server, TestServerConnection connection, double px, int y, double pz) {
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			AABB box = new AABB(px - 16, y - 2, pz - 16, px + 16, y + 12, pz + 16);
			for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null, box,
				e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
				entity.discard();
			}
		});
	}
}
