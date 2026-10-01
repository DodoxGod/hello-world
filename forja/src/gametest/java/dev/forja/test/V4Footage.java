package dev.forja.test;

import java.util.Locale;

import dev.forja.ai.Captain;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobItems;
import dev.forja.ai.MobKit;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ShieldPlay;
import dev.forja.ai.SpecialRunner;
import dev.forja.ai.Tactic;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The v4 mob AI on film (FORJA_SOLO=v4; docs/red_mob_v4_mod_estado.md): the shield and its bash, a sword picked up
 * off the floor, an ender pearl up a pillar, a torch put out, a player brought down from a pillar (the knockback
 * arrow and a wind charge), a zombie lying in wait, and a captain's formation and its charge. Each scene is built
 * somewhere of its own, well away from spawn, and filmed from an invisible armour stand. The sheet is
 * tools/hoja_v4.py.
 */
final class V4Footage {
	private V4Footage() {
	}

	private static void log(String message) {
		System.out.println("[forja-test] " + message);
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private static void run(TestServerContext server, String format, Object... args) {
		server.runCommand(String.format(Locale.ROOT, format, args));
	}

	/** A stone floor round (cx, cz), cleared above. */
	private static void ground(TestServerContext server, int cx, int y, int cz, int half) {
		run(server, "fill %d %d %d %d %d %d smooth_stone", cx - half, y - 1, cz - half, cx + half, y - 1, cz + half);
		run(server, "fill %d %d %d %d %d %d air", cx - half, y, cz - half, cx + half, y + 12, cz + half);
	}

	/** The camera: an invisible, still armour stand at (x, y, z) looking along yaw/pitch; returns its id. */
	private static int eye(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, double x, double y, double z,
		float yaw, float pitch) {
		int id = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ArmorStand stand = new ArmorStand(level, x, y, z);
			stand.setInvisible(true);
			stand.setNoGravity(true);
			stand.snapTo(x, y, z, yaw, pitch);
			// the camera looks where the head does
			stand.setYHeadRot(yaw);
			stand.setYBodyRot(yaw);
			level.addFreshEntity(stand);
			return stand.getId();
		});
		context.waitFor(mc -> mc.level.getEntity(id) != null, 100);
		context.runOnClient(mc -> {
			mc.setCameraEntity(mc.level.getEntity(id));
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		context.waitTicks(2);
		return id;
	}

	private static void shot(ClientGameTestContext context, int eye, String name) {
		context.runOnClient(mc -> {
			var stand = mc.level.getEntity(eye);
			if (stand != null && mc.getCameraEntity() != stand) {
				mc.setCameraEntity(stand);
			}
		});
		context.waitTicks(1);
		context.takeScreenshot(name);
		log("v4: " + name);
	}

	private static void back(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			mc.setCameraEntity(mc.player);
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, double x, double y, double z, float yaw) {
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		mob.snapTo(x, y, z, yaw, 0.0F);
		mob.setPersistenceRequired();
		// noon, for the light: a helmet keeps them from burning
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		level.addFreshEntity(mob);
		return mob;
	}

	private static void clear(TestServerContext server) {
		run(server, "kill @e[type=!player]");
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		CombatConfig cfg = CombatConfig.get();
		cfg.veteranChance = 0.0;
		cfg.eliteChance = 0.0;
		cfg.packChance = 0.0;
		run(server, "difficulty normal");
		run(server, "gamemode survival @a");
		run(server, "effect give @a resistance infinite 4 true");
		run(server, "effect give @a saturation infinite 0 true");
		context.runOnClient(mc -> mc.options.gamma().set(1.0));
		shield(context, server, connection, x + 600, y, z);
		pickup(context, server, connection, x + 660, y, z);
		pearl(context, server, connection, x + 720, y, z);
		torch(context, server, connection, x + 780, y, z);
		pillar(context, server, connection, x + 840, y, z);
		ambush(context, server, connection, x + 900, y, z);
		captain(context, server, connection, x + 960, y, z);
		run(server, "effect clear @a");
		run(server, "time set noon");
		cfg.packChance = new CombatConfig().packChance;
	}

	/**
	 * Captain 2 on film (FORJA_SOLO=capitan2; docs/red_mob_v4_mod_estado.md, "Capitán 2"): an elite captain with an axe,
	 * four zombies (one with a shield) and two skeletons on the player, from above. The captain stands back from the front
	 * with its banner's gold dust over it, and two escorts stand ahead of it; the log says who leads and where.
	 */
	static void filmCaptain2(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		CombatConfig cfg = CombatConfig.get();
		cfg.veteranChance = 0.0;
		cfg.eliteChance = 0.0;
		cfg.packChance = 0.0;
		run(server, "difficulty normal");
		run(server, "gamemode survival @a");
		run(server, "effect give @a resistance infinite 4 true");
		run(server, "effect give @a saturation infinite 0 true");
		context.runOnClient(mc -> mc.options.gamma().set(1.0));
		int bx = x + 1020;
		int bz = z;
		run(server, "time set noon");
		ground(server, bx, y, bz, 24);
		run(server, "attribute @a minecraft:knockback_resistance base set 1");
		tp(server, bx + 0.5, y, bz + 0.5, 180.0F, 0.0F);
		context.waitTicks(10);
		server.runOnServer(s -> {
			Captain.forget(connection.getServerPlayer());
			dev.forja.ai.Squad.forget(connection.getServerPlayer());
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			Mob elite = spawn(level, EntityTypes.ZOMBIE, bx + 0.5, y, bz - 12.5, 0.0F);
			Threat.ELITE.mark(elite);
			elite.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			elite.setTarget(player);
			for (int i = 0; i < 4; i++) {
				Mob zombie = spawn(level, EntityTypes.ZOMBIE, bx - 3.5 + 2 * i, y, bz - 9.5, 0.0F);
				if (i == 1) {
					zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				}
				zombie.setTarget(player);
			}
			for (int i = 0; i < 2; i++) {
				Mob skeleton = spawn(level, EntityTypes.SKELETON, bx - 2.5 + 5 * i, y, bz - 13.5, 0.0F);
				skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
				skeleton.setTarget(player);
			}
		});
		context.waitTicks(100);
		// the camera over the middle of the player and the captain, low enough to read who is who
		double[] middle = server.computeOnServer(s -> {
			Captain.Group g = Captain.group(connection.getServerPlayer());
			ServerPlayer player = connection.getServerPlayer();
			return g == null || g.captain == null ? new double[] {player.getX(), player.getZ()}
				: new double[] {(player.getX() + g.captain.getX()) / 2.0, (player.getZ() + g.captain.getZ()) / 2.0};
		});
		int eye = eye(context, server, connection, middle[0], y + 9.0, middle[1], 0.0F, 90.0F);
		// wait for the banner's dust (every 20 ticks) to be fresh when the shot is taken
		context.waitFor(mc -> mc.level.getGameTime() % 20 == 3, 40);
		String state = server.computeOnServer(s -> {
			Captain.Group g = Captain.group(connection.getServerPlayer());
			if (g == null || g.captain == null) {
				return "sin capitán";
			}
			StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "capitán a %.1f del jugador, guardia %d, orden %s/%s, escoltas",
				g.captain.distanceTo(connection.getServerPlayer()), g.guard, g.command.order, g.command.formation));
			for (MobMind mind : g.members) {
				if (mind.escort) {
					out.append(String.format(Locale.ROOT, " %.1f", mind.mob.distanceTo(g.captain)));
				}
			}
			return out.toString();
		});
		log("capitan2: " + state);
		shot(context, eye, "capitan2_01_detras_con_escoltas");
		back(context);
		clear(server);
		run(server, "attribute @a minecraft:knockback_resistance base set 0");
		run(server, "effect clear @a");
		cfg.packChance = new CombatConfig().packChance;
	}

	// ---------------------------------------------------------------- the shield and its bash

	private static void shield(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int bx, int y, int bz) {
		run(server, "time set noon");
		ground(server, bx, y, bz, 10);
		tp(server, bx + 0.5, y, bz + 0.5, -90.0F, 0.0F);
		context.waitTicks(10);
		int zombieId = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			Mob zombie = spawn(level, EntityTypes.ZOMBIE, bx + 2.3, y, bz + 0.5, 90.0F);
			zombie.setNoAi(true);
			zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			zombie.setTarget(connection.getServerPlayer());
			zombie.setYHeadRot(90.0F);
			zombie.setYBodyRot(90.0F);
			return zombie.getId();
		});
		int eye = eye(context, server, connection, bx + 1.4, y + 1.6, bz + 4.0, 180.0F, 10.0F);
		context.waitTicks(10);
		server.runOnServer(s -> {
			Mob zombie = (Mob) connection.getServerLevel().getEntity(zombieId);
			zombie.startUsingItem(InteractionHand.OFF_HAND);
		});
		context.waitTicks(8);
		shot(context, eye, "v4_01_escudo_arriba");
		String started = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			Mob zombie = (Mob) level.getEntity(zombieId);
			zombie.invulnerableTime = 0;
			zombie.hurtServer(level, level.damageSources().playerAttack(player), 3.0F);
			MobMind mind = MobAi.mind(zombie);
			boolean ok = ShieldPlay.start(zombie, mind, player, level.getGameTime());
			return "bloqueo " + mind.lastBlockAt + ", golpe de escudo " + ok;
		});
		log("v4 escudo: " + started);
		context.waitTicks(2);
		shot(context, eye, "v4_02_golpe_de_escudo_aviso");
		context.waitTicks(4);
		shot(context, eye, "v4_03_golpe_de_escudo");
		back(context);
		clear(server);
	}

	// ---------------------------------------------------------------- a sword off the floor

	private static void pickup(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int bx, int y, int bz) {
		run(server, "time set noon");
		ground(server, bx, y, bz, 12);
		tp(server, bx + 0.5, y, bz + 8.5, 180.0F, 0.0F);
		context.waitTicks(10);
		int zombieId = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			Mob zombie = spawn(level, EntityTypes.ZOMBIE, bx - 5.5, y, bz + 0.5, -90.0F);
			zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
			ItemEntity sword = new ItemEntity(level, bx + 0.5, y, bz + 0.5, new ItemStack(Items.DIAMOND_SWORD));
			sword.setThrower(player);
			sword.setPickUpDelay(10);
			sword.setDeltaMovement(Vec3.ZERO);
			level.addFreshEntity(sword);
			zombie.setTarget(player);
			return zombie.getId();
		});
		int eye = eye(context, server, connection, bx - 2.5, y + 3.0, bz - 6.0, 0.0F, 22.0F);
		context.waitTicks(30);
		shot(context, eye, "v4_04_va_a_por_la_espada");
		for (int i = 0; i < 40; i++) {
			boolean has = server.computeOnServer(s -> {
				Mob zombie = (Mob) connection.getServerLevel().getEntity(zombieId);
				return zombie != null && zombie.getMainHandItem().is(Items.DIAMOND_SWORD);
			});
			if (has) {
				break;
			}
			context.waitTicks(5);
		}
		String held = server.computeOnServer(s -> {
			Mob zombie = (Mob) connection.getServerLevel().getEntity(zombieId);
			return zombie.getMainHandItem() + ", repuesto " + MobKit.spare(zombie);
		});
		log("v4 recoger: " + held);
		context.waitTicks(4);
		shot(context, eye, "v4_05_con_la_espada");
		back(context);
		clear(server);
	}

	// ---------------------------------------------------------------- an ender pearl up a pillar

	private static void pearl(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int bx, int y, int bz) {
		run(server, "time set noon");
		ground(server, bx, y, bz, 12);
		run(server, "fill %d %d %d %d %d %d stone", bx, y, bz, bx, y + 3, bz);
		tp(server, bx + 0.5, y + 4, bz + 0.5, 90.0F, 10.0F);
		context.waitTicks(10);
		int zombieId = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			Mob zombie = spawn(level, EntityTypes.ZOMBIE, bx - 8.5, y, bz + 0.5, -90.0F);
			Threat.ELITE.mark(zombie);
			MobKit.add(zombie, MobKit.Kind.PEARL, "", 1);
			zombie.setTarget(connection.getServerPlayer());
			return zombie.getId();
		});
		int eye = eye(context, server, connection, bx - 4.0, y + 3.0, bz + 9.0, 180.0F, 5.0F);
		String[] names = {"v4_06_perla_aviso", "v4_07_perla_en_vuelo", "v4_08_perla_llega"};
		int taken = 0;
		for (int i = 0; i < 200 && taken < 3; i++) {
			int stage = server.computeOnServer(s -> {
				Mob zombie = (Mob) connection.getServerLevel().getEntity(zombieId);
				MobMind mind = zombie == null ? null : MobAi.mind(zombie);
				if (mind == null) {
					return -1;
				}
				if (mind.itemAction == MobItems.PEARL_IN && mind.itemTicks > 0 && mind.itemTicks < 5) {
					return 0;
				}
				if (mind.pearl != null && mind.pearl.isAlive()) {
					return 1;
				}
				return MobKit.count(zombie, MobKit.Kind.PEARL) == 0 && mind.pearl == null ? 2 : -1;
			});
			if (stage == taken) {
				shot(context, eye, names[taken]);
				taken++;
				if (taken == 3) {
					break;
				}
				continue;
			}
			if (stage > taken) {
				taken = stage;
				continue;
			}
			context.waitTicks(1);
		}
		String where = server.computeOnServer(s -> {
			Mob zombie = (Mob) connection.getServerLevel().getEntity(zombieId);
			return zombie == null ? "sin zombi" : "a " + String.format(Locale.ROOT, "%.1f", zombie.distanceTo(connection.getServerPlayer())) + " del jugador";
		});
		log("v4 perla: " + where);
		back(context);
		clear(server);
	}

	// ---------------------------------------------------------------- a torch put out

	private static void torch(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int bx, int y, int bz) {
		run(server, "time set midnight");
		ground(server, bx, y, bz, 12);
		run(server, "setblock %d %d %d torch", bx + 2, y, bz - 2);
		tp(server, bx + 0.5, y, bz + 0.5, 90.0F, 0.0F);
		context.waitTicks(20);
		int zombieId = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			Mob zombie = spawn(level, EntityTypes.ZOMBIE, bx + 7.5, y, bz - 4.5, 90.0F);
			float[] bias = new float[NetBrain.V4_OUTPUTS];
			bias[NetBrain.V4_TACTICS_AT + Tactic.APAGAR_LUZ.ordinal() - Tactic.V3_COUNT] = 20.0F;
			MobAi.mind(zombie).override = NetBrain.fromJson(RedV4GameTests.fakeV4(31L, bias));
			zombie.setTarget(connection.getServerPlayer());
			return zombie.getId();
		});
		int eye = eye(context, server, connection, bx + 2.5, y + 2.2, bz + 5.0, 180.0F, 18.0F);
		context.waitTicks(15);
		shot(context, eye, "v4_09_antorcha_encendida");
		boolean struck = false;
		for (int i = 0; i < 60 && !struck; i++) {
			struck = server.computeOnServer(s -> {
				Mob zombie = (Mob) connection.getServerLevel().getEntity(zombieId);
				return zombie != null && MobAi.mind(zombie).lightTicks >= 6;
			});
			if (!struck) {
				context.waitTicks(3);
			}
		}
		shot(context, eye, "v4_10_antorcha_golpes");
		context.waitTicks(15);
		shot(context, eye, "v4_11_antorcha_apagada");
		boolean out = server.computeOnServer(s -> connection.getServerLevel().getBlockState(new net.minecraft.core.BlockPos(bx + 2, y, bz - 2)).isAir());
		log("v4 antorcha: apagada " + out);
		back(context);
		clear(server);
		run(server, "time set noon");
	}

	// ---------------------------------------------------------------- a player brought down from a pillar

	private static void pillar(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int bx, int y, int bz) {
		run(server, "time set noon");
		ground(server, bx, y, bz, 12);
		run(server, "fill %d %d %d %d %d %d stone", bx, y, bz, bx, y + 2, bz);
		tp(server, bx + 0.5, y + 3, bz + 0.5, 90.0F, 20.0F);
		context.waitTicks(10);
		int skeletonId = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			Mob skeleton = spawn(level, EntityTypes.SKELETON, bx - 8.5, y, bz + 0.5, -90.0F);
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			skeleton.setNoAi(true);
			skeleton.setTarget(connection.getServerPlayer());
			return skeleton.getId();
		});
		int eye = eye(context, server, connection, bx - 4.0, y + 2.5, bz + 9.0, 180.0F, 5.0F);
		context.waitTicks(10);
		String began = server.computeOnServer(s -> {
			Mob skeleton = (Mob) connection.getServerLevel().getEntity(skeletonId);
			SpecialRunner runner = MobAi.mind(skeleton).specials;
			return "flecha de empuje disponible " + runner.available(2, connection.getServerPlayer()) + ", empieza "
				+ runner.start(2, connection.getServerPlayer());
		});
		log("v4 pilar: " + began);
		// a skeleton without AI runs no goals: its warning is ticked by hand, one a game tick
		for (int i = 0; i < 20; i++) {
			server.runOnServer(s -> MobAi.mind((Mob) connection.getServerLevel().getEntity(skeletonId)).specials.tick());
			context.waitTicks(1);
			if (i == 12) {
				shot(context, eye, "v4_12_flecha_de_empuje_aviso");
			}
		}
		context.waitTicks(4);
		shot(context, eye, "v4_13_flecha_de_empuje");
		context.waitTicks(25);
		String landed = server.computeOnServer(s -> String.format(Locale.ROOT, "el jugador está a %.1f sobre el suelo",
			connection.getServerPlayer().getY() - y));
		log("v4 pilar: " + landed);
		shot(context, eye, "v4_14_bajado_del_pilar");
		// again up the pillar, and a zombie with wind charges
		clear(server);
		tp(server, bx + 0.5, y + 3, bz + 0.5, 90.0F, 20.0F);
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			Mob zombie = spawn(level, EntityTypes.ZOMBIE, bx - 6.5, y, bz + 0.5, -90.0F);
			zombie.setNoAi(true);
			MobKit.add(zombie, MobKit.Kind.WIND, "", 1);
			zombie.setTarget(connection.getServerPlayer());
		});
		eye = eye(context, server, connection, bx - 3.0, y + 2.5, bz + 9.0, 180.0F, 5.0F);
		for (int i = 0; i < 80; i++) {
			boolean flying = server.computeOnServer(s -> !connection.getServerLevel().getEntitiesOfClass(
				net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge.class,
				connection.getServerPlayer().getBoundingBox().inflate(12.0)).isEmpty());
			if (flying) {
				break;
			}
			context.waitTicks(1);
		}
		context.waitTicks(3);
		shot(context, eye, "v4_15_carga_de_viento");
		context.waitTicks(4);
		shot(context, eye, "v4_16_carga_de_viento_levanta");
		back(context);
		clear(server);
	}

	// ---------------------------------------------------------------- lying in wait

	private static void ambush(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int bx, int y, int bz) {
		run(server, "time set midnight");
		ground(server, bx, y, bz, 12);
		// an L of wall: the zombie's side and the player's
		run(server, "fill %d %d %d %d %d %d stone_bricks", bx - 2, y, bz - 6, bx - 2, y + 2, bz + 1);
		run(server, "fill %d %d %d %d %d %d stone_bricks", bx - 2, y, bz + 1, bx - 7, y + 2, bz + 1);
		tp(server, bx + 3.5, y, bz + 4.5, 135.0F, 0.0F);
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			Mob zombie = spawn(level, EntityTypes.ZOMBIE, bx - 0.5, y, bz - 3.5, 0.0F);
			float[] bias = new float[NetBrain.V4_OUTPUTS];
			bias[NetBrain.V4_TACTICS_AT + Tactic.EMBOSCAR.ordinal() - Tactic.V3_COUNT] = 20.0F;
			MobAi.mind(zombie).override = NetBrain.fromJson(RedV4GameTests.fakeV4(37L, bias));
			zombie.setTarget(connection.getServerPlayer());
		});
		context.waitTicks(120);
		int top = eye(context, server, connection, bx - 1.0, y + 14.0, bz - 1.0, 0.0F, 90.0F);
		shot(context, top, "v4_17_emboscada_desde_arriba");
		back(context);
		context.waitTicks(2);
		context.takeScreenshot("v4_18_emboscada_vista_jugador");
		log("v4: v4_18_emboscada_vista_jugador");
		clear(server);
		run(server, "time set noon");
	}

	// ---------------------------------------------------------------- the captain's formation and its charge

	private static void captain(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int bx, int y, int bz) {
		run(server, "time set noon");
		ground(server, bx, y, bz, 24);
		run(server, "attribute @a minecraft:knockback_resistance base set 1");
		tp(server, bx + 0.5, y, bz + 0.5, 0.0F, 0.0F);
		context.waitTicks(10);
		// a fresh fight: the scenes before killed their mobs, and those deaths would count as this group's losses
		server.runOnServer(s -> {
			Captain.forget(connection.getServerPlayer());
			dev.forja.ai.Squad.forget(connection.getServerPlayer());
		});
		int eliteId = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			Mob elite = spawn(level, EntityTypes.ZOMBIE, bx + 0.5, y, bz - 14.5, 0.0F);
			Threat.ELITE.mark(elite);
			elite.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			elite.setTarget(player);
			for (int i = 0; i < 4; i++) {
				Mob z = spawn(level, EntityTypes.ZOMBIE, bx - 3.5 + 2 * i, y, bz - 12.5, 0.0F);
				if (i < 2) {
					z.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				}
				z.setTarget(player);
			}
			for (int i = 0; i < 2; i++) {
				Mob skeleton = spawn(level, EntityTypes.SKELETON, bx - 1.5 + 3 * i, y, bz - 15.5, 0.0F);
				skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
				skeleton.setTarget(player);
			}
			return elite.getId();
		});
		int eye = eye(context, server, connection, bx + 0.5, y + 13.0, bz - 3.5, 0.0F, 90.0F);
		context.waitTicks(100);
		String order = server.computeOnServer(s -> {
			Captain.Group g = Captain.group(connection.getServerPlayer());
			return g == null ? "sin grupo" : "capitán " + (g.captain == null ? null : g.captain.getId()) + " (élite " + eliteId + "), orden "
				+ g.command.order + " " + g.command.formation;
		});
		log("v4 capitán: " + order);
		shot(context, eye, "v4_19_capitan_formacion");
		// the charge: a countdown of 20, the shout, and in
		server.runOnServer(s -> {
			Captain.Group g = Captain.group(connection.getServerPlayer());
			if (g != null && g.captain != null) {
				long now = connection.getServerLevel().getGameTime();
				Captain.Command c = new Captain.Command();
				c.order = Captain.Order.CARGA;
				c.formation = g.command.formation;
				c.count = 2;
				c.givenAt = now;
				c.chargeAt = now + Captain.COUNTS[2];
				g.command = c;
			}
		});
		context.waitTicks(40);
		shot(context, eye, "v4_20_capitan_carga");
		back(context);
		clear(server);
		run(server, "attribute @a minecraft:knockback_resistance base set 0");
	}
}
