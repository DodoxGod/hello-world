package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.forja.ai.Decision;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobFamily;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsM1;
import dev.forja.ai.ObsNames;
import dev.forja.ai.Tactic;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The mob brains: what a mob sees (the simulator's 102 numbers), the networks trained there (read from
 * the bot's folder when it exists on this machine; skipped otherwise), and a network actually driving a
 * zombie into a player.
 */
public class AiGameTests {
	/** Where the simulator writes its trained mob networks, on the machine these tests were written on. */
	private static final Path TRAINED = Path.of("E:/IA/agente/minecraft/combate/mobs");

	private static CombatGameTests.TestPlayer player(GameTestHelper helper, BlockPos pos) {
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(helper.getLevel());
		var at = helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos));
		player.setPos(at.x, at.y, at.z);
		player.setHealth(player.getMaxHealth());
		return player;
	}

	private static Zombie zombie(GameTestHelper helper, BlockPos pos) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		return helper.spawn(EntityTypes.ZOMBIE, pos);
	}

	/** The numbers a zombie sees of a player four blocks off, in the simulator's frame and scale. */
	@GameTest
	public void observationMatchesTheSimulatorsLayout(GameTestHelper helper) {
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		CombatGameTests.TestPlayer target = player(helper, new BlockPos(5, 1, 1));
		float[] obs = ObsM1.of(zombie, target, 0, 0);
		helper.assertTrue(obs.length == ObsNames.M1.size(), "tamaño " + obs.length);
		helper.assertTrue(Math.abs(obs[0] - 4.0F / 16.0F) < 0.02F, "distancia/16 = " + obs[0]);
		helper.assertTrue(obs[2] == 0.0F, "a 4 bloques no alcanza");
		helper.assertTrue(obs[16] == 1.0F, "el jugador no lleva nada en la mano");
		helper.assertTrue(obs[22] == 1.0F, "vida propia entera");
		helper.assertTrue(obs[33] == 1.0F && obs[34] == 0.0F, "tipo zombi");
		helper.assertTrue(obs[41] == 1.0F, "lo ve");
		// Flat ground: the ground ahead is level with the ground under the mob (it may still be falling at tick 0).
		double under = ObsM1.ground(helper.getLevel(), zombie.getX(), zombie.getZ(), zombie.getY(), 0.3);
		float expected = (float) Math.max(-1.0, Math.min(1.0, (under - zombie.getY()) / 4.0));
		helper.assertTrue(Math.abs(obs[43] - expected) < 1.0E-4F, "suelo llano delante: " + obs[43] + " vs " + expected);
		helper.succeed();
	}

	/** Every trained network on this machine loads, fits the mod's inputs, and gives a decision. */
	@GameTest
	public void trainedNetworksLoadAndDecide(GameTestHelper helper) throws java.io.IOException {
		if (!Files.isDirectory(TRAINED)) {
			helper.succeed();
			return;
		}
		int found = 0;
		for (MobFamily family : MobFamily.values()) {
			Path file = TRAINED.resolve("red_" + family.file + ".json");
			if (!Files.exists(file)) {
				continue;
			}
			found++;
			NetBrain net = NetBrain.load(file);
			String problem = MobAi.check(net);
			helper.assertTrue(problem == null, family.file + ": " + problem);
			float[] memory = new float[net.memory];
			float[] logits = net.forward(new float[ObsM1.SIZE], memory);
			helper.assertTrue(logits.length >= 11, family.file + ": " + logits.length + " salidas");
			Decision decision = NetBrain.sample(logits, 1.0, RandomSource.create(1), null);
			helper.assertTrue(decision.move() >= 0 && decision.move() <= 8, family.file + ": mover " + decision.move());
		}
		helper.assertTrue(found > 0, "no hay redes en " + TRAINED);
		helper.succeed();
	}

	/** The trained melee network drives a zombie to a player and hurts them. Only this zombie gets it. */
	@GameTest(maxTicks = 400)
	public void networkZombieFights(GameTestHelper helper) throws java.io.IOException {
		Path file = TRAINED.resolve("red_cuerpo.json");
		if (!Files.exists(file)) {
			helper.succeed();
			return;
		}
		NetBrain net = NetBrain.load(file);
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer target = player(helper, new BlockPos(6, 1, 1));
		MobMind mind = MobAi.mind(zombie);
		helper.assertTrue(mind != null, "el zombi debería tener cerebro");
		mind.override = net;
		zombie.setTarget(target);
		float start = target.getHealth();
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(mind.networked, "la red debería llevar al zombi");
			helper.assertTrue(mind.decision.tactic() == Tactic.LIBRE, "una red v1 decide en modo libre");
			helper.assertTrue(target.getHealth() < start, "el zombi de la red no llegó a hacer daño en 15 s");
			helper.succeed();
		});
	}

	/**
	 * Recordings for the simulator's side to check the observations against: a zombie, a skeleton and a
	 * creeper, each driven by its trained network, against a player. Only with FORJA_GRABAR set to a folder.
	 */
	@GameTest(maxTicks = 700)
	public void recordNetworkFights(GameTestHelper helper) throws java.io.IOException {
		String folder = System.getenv("FORJA_GRABAR");
		if (folder == null || folder.isBlank() || !Files.isDirectory(TRAINED)) {
			helper.succeed();
			return;
		}
		dev.forja.ai.AiRecorder.start(Path.of(folder));
		String[][] cast = {{"zombie", "cuerpo"}, {"skeleton", "arquero"}, {"creeper", "arana"}};
		net.minecraft.world.entity.EntityType<?>[] types = {EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CREEPER};
		String[] files = {"red_cuerpo.json", "red_arquero.json", "red_creeper.json"};
		for (int i = 0; i < types.length; i++) {
			CombatConfig.get().veteranChance = 0.0;
			CombatConfig.get().eliteChance = 0.0;
			var mob = (net.minecraft.world.entity.Mob) helper.spawn(types[i], new BlockPos(1, 1, 1 + i * 3));
			CombatGameTests.TestPlayer target = player(helper, new BlockPos(7, 1, 1 + i * 3));
			// A simulated player never runs its own physics: stand it on the floor (it does not fall) and
			// wipe the knockback it would otherwise keep forever. A helmet keeps the mob out of the sun.
			double floor = ObsM1.ground(helper.getLevel(), target.getX(), target.getZ(), target.getY(), 0.3);
			target.setPos(target.getX(), floor, target.getZ());
			helper.onEachTick(() -> target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO));
			mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
			MobMind mind = MobAi.mind(mob);
			if (mind != null) {
				mind.override = NetBrain.load(TRAINED.resolve(files[i]));
			}
			mob.setTarget(target);
		}
		helper.runAfterDelay(600, () -> {
			long lines = dev.forja.ai.AiRecorder.stop();
			helper.assertTrue(lines > 0, "no se grabó ninguna decisión");
			helper.succeed();
		});
	}

	/** Parries, dodges and blows taken move the player's habits, and the monsters read them. */
	@GameTest
	public void habitsFollowWhatThePlayerDoes(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		float parryBefore = dev.forja.ai.PlayerHabits.get(player, dev.forja.ai.PlayerHabits.PARRY);
		for (int i = 0; i < 30; i++) {
			dev.forja.ai.PlayerHabits.onBlow(player, dev.forja.ai.PlayerHabits.Outcome.PARRIED);
		}
		float parry = dev.forja.ai.PlayerHabits.get(player, dev.forja.ai.PlayerHabits.PARRY);
		helper.assertTrue(parry > parryBefore + 0.4F, "la parada debería subir: " + parryBefore + " -> " + parry);
		helper.assertTrue(dev.forja.ai.PlayerHabits.skill(player) > 0.6F, "parar mucho es de buen jugador");
		helper.assertTrue(dev.forja.ai.Aggression.feintChance(player) > 0.3, "contra quien para mucho, más fintas");
		for (int i = 0; i < 10; i++) {
			dev.forja.ai.PlayerHabits.onDodge(player, 1.0F);
		}
		helper.assertTrue(dev.forja.ai.PlayerHabits.get(player, dev.forja.ai.PlayerHabits.SIDE) > 0.5F, "esquiva a la derecha");
		helper.succeed();
	}

	/** More monsters may swing at once at a player who is out of breath or nearly dead. */
	@GameTest
	public void pressingAWeakPlayer(GameTestHelper helper) {
		CombatGameTests.TestPlayer fresh = player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer spent = player(helper, new BlockPos(1, 1, 4));
		dev.forja.combat.Stamina.trySpend(spent, 95.0F);
		spent.setHealth(4.0F);
		int calm = dev.forja.ai.Aggression.maxAttackers(fresh);
		int pressed = dev.forja.ai.Aggression.maxAttackers(spent);
		helper.assertTrue(pressed >= calm + 2, "turnos: descansado " + calm + ", agotado y herido " + pressed);
		helper.succeed();
	}

	/** A zombie steps out of the reach of a player charging a heavy blow. */
	@GameTest
	public void backsOffAChargingPlayer(GameTestHelper helper) {
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(3, 1, 1));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
		dev.forja.combat.ChargedStrike.onPayload(player, dev.forja.combat.ChargePayload.START);
		zombie.setTarget(player);
		MobMind mind = MobAi.mind(zombie);
		Decision decision = dev.forja.ai.RuleBrain.decide(mind, player);
		dev.forja.combat.ChargedStrike.onPayload(player, dev.forja.combat.ChargePayload.CANCEL);
		helper.assertTrue(decision.tactic() == Tactic.RETIRARSE, "debería apartarse de la carga, decidió " + decision.tactic());
		helper.succeed();
	}

	/** The Forja block: names and values line up, after the simulator's 102. */
	@GameTest
	public void forjaBlockLinesUp(GameTestHelper helper) {
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 1));
		int n = ObsNames.M1.size() + dev.forja.ai.ObsForja.size();
		float[] obs = dev.forja.ai.ObsForja.full(zombie, player, MobAi.mind(zombie), ObsM1.of(zombie, player, 0, 0), n);
		helper.assertTrue(obs.length == n, "tamaño " + obs.length + " vs " + n);
		int stamina = ObsNames.M1.size() + dev.forja.ai.ObsForja.names().indexOf("jug_estamina/100");
		helper.assertTrue(Math.abs(obs[stamina] - 1.0F) < 1.0E-4F, "estamina llena = 1, dio " + obs[stamina]);
		int smith = ObsNames.M1.size() + dev.forja.ai.ObsForja.names().indexOf("dif_herrero");
		helper.assertTrue(obs[smith] == (dev.forja.difficulty.ForjaDifficulty.current() == dev.forja.difficulty.ForjaDifficulty.HERRERO ? 1.0F : 0.0F), "dificultad");
		helper.succeed();
	}

	/** Four zombies on one player get four different slots on the ring, a quarter turn apart, and a flanker. */
	@GameTest(maxTicks = 60)
	public void squadSpreadsRoundThePlayer(GameTestHelper helper) {
		// Other tests' players stand next door: no monster of this one may be handed to them.
		CombatConfig.get().iaRepartirObjetivos = false;
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 4));
		java.util.List<Zombie> zombies = new java.util.ArrayList<>();
		int[][] at = {{1, 1}, {2, 1}, {1, 2}, {7, 7}};
		for (int[] p : at) {
			Zombie z = zombie(helper, new BlockPos(p[0], 1, p[1]));
			z.setNoAi(true);
			z.setTarget(player);
			zombies.add(z);
		}
		helper.runAfterDelay(15, () -> {
			java.util.List<Double> angles = new java.util.ArrayList<>();
			int flankers = 0;
			for (Zombie z : zombies) {
				MobMind mind = MobAi.mind(z);
				helper.assertTrue(mind != null && !Double.isNaN(mind.ringAngle), "cada zombi debería tener hueco");
				angles.add(mind.ringAngle);
				if (mind.role == dev.forja.ai.SquadRole.FLANCO) flankers++;
			}
			for (int i = 0; i < angles.size(); i++) {
				for (int j = i + 1; j < angles.size(); j++) {
					double gap = Math.abs(Math.IEEEremainder(angles.get(i) - angles.get(j), 2.0 * Math.PI));
					helper.assertTrue(gap > Math.PI / 2.0 - 0.01, "huecos demasiado juntos: " + gap);
				}
			}
			helper.assertTrue(flankers == 1, "con 3 o más debería haber un flanco, hay " + flankers);
			CombatConfig.get().iaRepartirObjetivos = true;
			helper.succeed();
		});
	}

	/** A zombie badly hurt by a player calls the idle ones near it. */
	@GameTest
	public void hurtZombieCallsForHelp(GameTestHelper helper) {
		Zombie hurt = zombie(helper, new BlockPos(1, 1, 1));
		Zombie idle = zombie(helper, new BlockPos(5, 1, 5));
		hurt.setNoAi(true);
		idle.setNoAi(true);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(3, 1, 1));
		hurt.setHealth(hurt.getMaxHealth() * 0.6F);
		hurt.invulnerableTime = 0;
		hurt.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 4.0F);
		helper.assertTrue(idle.getTarget() == player, "el zombi herido debería llamar al otro");
		helper.succeed();
	}

	/** A skeleton does not loose through one of its own. */
	@GameTest
	public void archerSeesAllyInLine(GameTestHelper helper) {
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 1));
		Zombie wall = zombie(helper, new BlockPos(3, 1, 1));
		skeleton.setNoAi(true);
		wall.setNoAi(true);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(6, 1, 1));
		helper.assertTrue(dev.forja.ai.Squad.allyInLine(skeleton, player), "el zombi está en la línea de tiro");
		wall.discard();
		helper.assertFalse(dev.forja.ai.Squad.allyInLine(skeleton, player), "sin nadie en medio, puede disparar");
		skeleton.discard();
		helper.succeed();
	}

	private static dev.forja.ai.SpecialRunner specials(net.minecraft.world.entity.Mob mob) {
		MobMind mind = MobAi.mind(mob);
		return mind == null ? null : mind.specials;
	}

	/** Starts a special and runs its warning to the release. */
	private static void release(GameTestHelper helper, dev.forja.ai.SpecialRunner runner, int slot, net.minecraft.world.entity.player.Player target) {
		helper.assertTrue(runner != null && runner.start(slot, target), "no pudo empezar el especial " + slot);
		int guard = 0;
		while (runner.warningProgress() >= 0.0 && guard++ < 80) {
			runner.tick();
		}
	}

	private static void standOnFloor(GameTestHelper helper, CombatGameTests.TestPlayer player) {
		double floor = ObsM1.ground(helper.getLevel(), player.getX(), player.getZ(), player.getY(), 0.3);
		player.setPos(player.getX(), floor, player.getZ());
		player.setOnGround(true);
	}

	/** A spider crouches, then pounces: up and at the player. */
	@GameTest
	public void spiderPounces(GameTestHelper helper) {
		var spider = helper.spawn(EntityTypes.SPIDER, new BlockPos(1, 1, 1));
		spider.setNoAi(true);
		spider.setPos(spider.getX(), ObsM1.ground(helper.getLevel(), spider.getX(), spider.getZ(), spider.getY(), 0.3), spider.getZ());
		spider.setOnGround(true);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(5, 1, 1));
		helper.runAfterDelay(5, () -> {
			var runner = specials(spider);
			helper.assertTrue(runner != null && runner.moveset().get(0).id.equals("salto_arana"), "la araña tiene salto");
			release(helper, runner, 0, player);
			helper.assertTrue(spider.getDeltaMovement().y > 0.3, "el salto debería levantarla: " + spider.getDeltaMovement());
			helper.succeed();
		});
	}

	/** A spider marks the player's feet and a web appears there; five seconds later it is gone. */
	@GameTest(maxTicks = 200)
	public void spiderWebComesAndGoes(GameTestHelper helper) {
		var spider = helper.spawn(EntityTypes.SPIDER, new BlockPos(1, 1, 1));
		spider.setNoAi(true);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 1));
		standOnFloor(helper, player);
		helper.runAfterDelay(5, () -> {
			release(helper, specials(spider), 1, player);
			BlockPos at = BlockPos.containing(player.position());
			helper.assertTrue(helper.getLevel().getBlockState(at).is(net.minecraft.world.level.block.Blocks.COBWEB), "debería haber telaraña");
			helper.runAfterDelay(110, () -> {
				helper.assertFalse(helper.getLevel().getBlockState(at).is(net.minecraft.world.level.block.Blocks.COBWEB), "la telaraña debería quitarse");
				helper.succeed();
			});
		});
	}

	/** An enderman shows where it will land behind the player, and lands there. */
	@GameTest
	public void endermanBlinksBehind(GameTestHelper helper) {
		var enderman = helper.spawn(EntityTypes.ENDERMAN, new BlockPos(1, 1, 1));
		enderman.setNoAi(true);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(5, 1, 4));
		standOnFloor(helper, player);
		player.setYRot(0.0F);
		helper.runAfterDelay(5, () -> {
			release(helper, specials(enderman), 0, player);
			// Looking south (+Z), behind is north (-Z).
			helper.assertTrue(enderman.getZ() < player.getZ() - 1.0, "debería aparecer detrás: " + enderman.position() + " vs " + player.position());
			helper.succeed();
		});
	}

	/** A vindicator's chop breaks a raised shield. */
	@GameTest
	public void vindicatorBreaksTheGuard(GameTestHelper helper) {
		var vindicator = helper.spawn(EntityTypes.VINDICATOR, new BlockPos(1, 1, 1));
		vindicator.setNoAi(true);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(3, 1, 1));
		net.minecraft.world.item.ItemStack shield = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD);
		player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, shield);
		player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
		helper.runAfterDelay(5, () -> {
			release(helper, specials(vindicator), 0, player);
			helper.assertTrue(player.getCooldowns().isOnCooldown(shield), "el hachazo debería romper la guardia");
			helper.succeed();
		});
	}

	/** A witch marks a spot and throws at it. */
	@GameTest
	public void witchThrowsAtTheMark(GameTestHelper helper) {
		var witch = helper.spawn(EntityTypes.WITCH, new BlockPos(1, 1, 1));
		witch.setNoAi(true);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 1));
		helper.runAfterDelay(5, () -> {
			release(helper, specials(witch), 0, player);
			var potions = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion.class,
				witch.getBoundingBox().inflate(4.0));
			helper.assertTrue(!potions.isEmpty(), "la bruja debería lanzar la poción");
			potions.forEach(net.minecraft.world.entity.Entity::discard);
			helper.succeed();
		});
	}

	/** A skeleton's volley looses three arrows at once. */
	@GameTest
	public void skeletonVolley(GameTestHelper helper) {
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 1));
		skeleton.setNoAi(true);
		skeleton.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 7));
		helper.runAfterDelay(5, () -> {
			var runner = specials(skeleton);
			helper.assertTrue(runner != null, "sin especiales");
			helper.assertTrue(runner.ready(1), "andanada en enfriamiento");
			helper.assertTrue(skeleton.getSensing().hasLineOfSight(player), "no lo ve");
			helper.assertFalse(dev.forja.ai.Squad.allyInLine(skeleton, player), "aliado en la línea");
			helper.assertTrue(skeleton.distanceTo(player) >= 6.0, "distancia " + skeleton.distanceTo(player));
			release(helper, runner, 1, player);
			int arrows = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
				skeleton.getBoundingBox().inflate(4.0), a -> a.getOwner() == skeleton).size();
			helper.assertTrue(arrows == 3, "la andanada son 3 flechas, salieron " + arrows);
			helper.succeed();
		});
	}

	private static Zombie shielded(GameTestHelper helper, BlockPos pos) {
		Zombie zombie = zombie(helper, pos);
		zombie.setNoAi(true);
		zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
		return zombie;
	}

	/** A shield raised in time parries a player's blow: nothing gets through, and the player reels. */
	@GameTest
	public void zombieParries(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(3, 1, 1));
		Zombie zombie = shielded(helper, new BlockPos(1, 1, 1));
		zombie.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
		zombie.setYBodyRot(zombie.getYRot());
		zombie.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
		float before = zombie.getHealth();
		zombie.invulnerableTime = 0;
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 5.0F);
		helper.assertTrue(zombie.getHealth() == before, "la parada debería parar todo el golpe");
		helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "el jugador debería quedar tambaleándose");
		helper.assertTrue(dev.forja.ai.MobDefense.counterReady(zombie), "tras parar, el zombi tiene contraataque");
		helper.assertTrue(dev.forja.ai.MobDefense.windup(zombie) < CombatConfig.get().windupTicks, "el contraataque avisa menos");
		helper.succeed();
	}

	/** Blocking costs balance: enough blocked blows break the guard, and the shield goes down. */
	@GameTest(maxTicks = 100)
	public void blockedBlowsBreakTheGuard(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(3, 1, 1));
		Zombie zombie = shielded(helper, new BlockPos(1, 1, 1));
		zombie.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
		zombie.setYBodyRot(zombie.getYRot());
		zombie.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
		helper.runAfterDelay(10, () -> {
			for (int i = 0; i < 12 && !dev.forja.ai.MobDefense.guardBroken(zombie); i++) {
				zombie.invulnerableTime = 0;
				zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 4.0F);
			}
			helper.assertTrue(dev.forja.ai.MobDefense.guardBroken(zombie), "los golpes bloqueados deberían romper la guardia");
			helper.assertFalse(zombie.isUsingItem(), "con la guardia rota, el escudo baja");
			helper.succeed();
		});
	}

	/** A dodge gives a mob a moment where blows pass through. */
	@GameTest
	public void zombieDodges(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(3, 1, 1));
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		zombie.setOnGround(true);
		helper.assertTrue(dev.forja.ai.MobDefense.dodge(zombie, player), "debería poder esquivar");
		float before = zombie.getHealth();
		zombie.invulnerableTime = 0;
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 5.0F);
		helper.assertTrue(zombie.getHealth() == before, "durante la esquiva no entra el golpe");
		helper.assertFalse(dev.forja.ai.MobDefense.dodgeReady(zombie), "la esquiva tiene enfriamiento");
		helper.succeed();
	}

	/** With the chance forced up, zombies come with shields. */
	@GameTest
	public void zombiesSpawnWithShields(GameTestHelper helper) {
		double previous = CombatConfig.get().shieldChance;
		CombatConfig.get().shieldChance = 10.0;
		Zombie zombie;
		try {
			zombie = zombie(helper, new BlockPos(1, 1, 1));
		} finally {
			CombatConfig.get().shieldChance = previous;
		}
		helper.assertTrue(dev.forja.ai.MobDefense.hasShield(zombie), "debería llevar escudo");
		zombie.discard();
		helper.succeed();
	}

	/** The walking anvil only loses its balance to blunt blows. */
	@GameTest
	public void anvilOnlyFeelsBluntBlows(GameTestHelper helper) {
		var anvil = helper.spawn(dev.forja.registry.ModEntities.YUNQUE_ANDANTE, new BlockPos(1, 1, 1));
		anvil.setNoAi(true);
		long now = helper.getLevel().getGameTime();
		double edge = dev.forja.ai.ForjaTraits.postureFactor(anvil, dev.forja.combat.DamageKind.SLASH, now);
		double blunt = dev.forja.ai.ForjaTraits.postureFactor(anvil, dev.forja.combat.DamageKind.BLUNT, now);
		helper.assertTrue(blunt > edge * 4.0, "golpe " + blunt + " vs corte " + edge);
		helper.assertTrue(dev.forja.combat.Posture.max(anvil) > anvil.getMaxHealth() * 0.6 * 1.9, "el yunque tiene más postura");
		anvil.discard();
		helper.succeed();
	}

	/** The fallen smith can only be staggered in the opening after one of his heavy blows. */
	@GameTest
	public void smithOpensAfterHisBlow(GameTestHelper helper) {
		var smith = helper.spawn(dev.forja.registry.ModEntities.HERRERO_CAIDO, new BlockPos(2, 1, 2));
		smith.setNoAi(true);
		long now = helper.getLevel().getGameTime();
		double shut = dev.forja.ai.ForjaTraits.postureFactor(smith, dev.forja.combat.DamageKind.SLASH, now);
		dev.forja.ai.ForjaTraits.smithStruck(smith);
		double open = dev.forja.ai.ForjaTraits.postureFactor(smith, dev.forja.combat.DamageKind.SLASH, now);
		helper.assertTrue(open > shut * 10.0, "abierto " + open + " vs cerrado " + shut);
		helper.assertTrue(dev.forja.ai.Movesets.of(smith).size() == 2, "el herrero tiene revés y onda");
		smith.discard();
		helper.succeed();
	}

	/** An edge splits a piece off the slag. */
	@GameTest
	public void slagSplitsUnderAnEdge(GameTestHelper helper) {
		var slag = helper.spawn(dev.forja.registry.ModEntities.ESCORIA, new BlockPos(2, 1, 2));
		slag.setNoAi(true);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 2));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
		int before = helper.getLevel().getEntitiesOfClass(dev.forja.entity.LivingSlag.class, slag.getBoundingBox().inflate(4.0)).size();
		slag.invulnerableTime = 0;
		slag.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 6.0F);
		int after = helper.getLevel().getEntitiesOfClass(dev.forja.entity.LivingSlag.class, slag.getBoundingBox().inflate(4.0)).size();
		helper.assertTrue(after == before + 1, "debería separarse un trozo: " + before + " -> " + after);
		helper.succeed();
	}

	/** The hollow armor plays dead instead of going under 30 %, and gets up again. */
	@GameTest(maxTicks = 120)
	public void hollowArmorPlaysDead(GameTestHelper helper) {
		var hollow = helper.spawn(dev.forja.registry.ModEntities.CORAZA, new BlockPos(2, 1, 2));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(5, 1, 2));
		hollow.invulnerableTime = 0;
		hollow.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);
		helper.assertTrue(hollow.isAlive() && hollow.isNoAi(), "debería hacerse el muerto");
		helper.assertTrue(hollow.getHealth() >= hollow.getMaxHealth() * 0.29F, "se queda con el 30 %: " + hollow.getHealth());
		helper.runAfterDelay(dev.forja.ai.ForjaTraits.HOLLOW_FEIGN_TICKS + 5, () -> {
			helper.assertFalse(hollow.isNoAi(), "debería levantarse");
			helper.succeed();
		});
	}

	/** Lava next to the player is found, and a monster's blow pushes them towards it. */
	@GameTest
	public void blowsPushTowardsLava(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(3, 1, 3));
		standOnFloor(helper, player);
		BlockPos floor = BlockPos.containing(player.getX() + 2.0, player.getY() - 0.5, player.getZ());
		helper.getLevel().setBlockAndUpdate(floor, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
		net.minecraft.world.phys.Vec3 danger = dev.forja.ai.Terrain.dangerNear(player);
		helper.assertTrue(danger != null && danger.x > 0.5, "debería ver la lava al este: " + danger);
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 3));
		zombie.setNoAi(true);
		player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		player.invulnerableTime = 0;
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie), 2.0F);
		helper.assertTrue(player.getDeltaMovement().x > 0.2, "el golpe debería empujar hacia la lava: " + player.getDeltaMovement());
		helper.getLevel().setBlockAndUpdate(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
		helper.succeed();
	}

	/** A wall between mob and player is found as cover from arrows. */
	@GameTest
	public void findsCoverBehindAWall(GameTestHelper helper) {
		Zombie zombie = zombie(helper, new BlockPos(3, 1, 3));
		zombie.setNoAi(true);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(3, 1, 7));
		for (int x = 0; x <= 7; x++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(x, y, 5), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		helper.setBlock(new BlockPos(3, 1, 5), net.minecraft.world.level.block.Blocks.AIR);
		helper.setBlock(new BlockPos(3, 2, 5), net.minecraft.world.level.block.Blocks.AIR);
		net.minecraft.world.phys.Vec3 cover = dev.forja.ai.Terrain.cover(zombie, player);
		helper.assertTrue(cover != null, "debería encontrar un parapeto tras el muro");
		helper.succeed();
	}

	/** A zombie that cannot get to its player digs through leaves in the way. */
	@GameTest(maxTicks = 200)
	public void zombieDigsThroughLeaves(GameTestHelper helper) {
		for (int z = 0; z <= 7; z++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(3, y, z), net.minecraft.world.level.block.Blocks.OAK_LEAVES);
			}
		}
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 3));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(6, 1, 3));
		zombie.setTarget(player);
		helper.succeedWhen(() -> {
			boolean dug = false;
			for (int z = 0; z <= 7 && !dug; z++) {
				for (int y = 1; y <= 2; y++) {
					if (helper.getBlockState(new BlockPos(3, y, z)).isAir()) {
						dug = true;
					}
				}
			}
			helper.assertTrue(dug, "el zombi debería abrirse paso entre las hojas");
		});
	}

	/** Every hostile comes with one trait; surviving fights counts, a wound leaves a grudge, the third makes a veteran. */
	@GameTest
	public void memoryAndPersonality(GameTestHelper helper) {
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 1));
		long traits = zombie.entityTags().stream().filter(t -> t.startsWith(dev.forja.ai.Personality.TRAIT_TAG)).count();
		helper.assertTrue(traits == 1, "debería tener un rasgo, tiene " + traits);
		zombie.setHealth(zombie.getMaxHealth() - 2.0F);
		for (int i = 0; i < dev.forja.ai.Personality.VETERAN_FIGHTS; i++) {
			dev.forja.ai.Personality.survived(zombie, player);
		}
		helper.assertTrue(dev.forja.ai.Personality.grudge(zombie, player), "herido y vivo: rencor");
		helper.assertTrue(dev.forja.difficulty.Threat.of(zombie) == dev.forja.difficulty.Threat.VETERANO, "tres peleas: veterano");
		helper.assertTrue(dev.forja.ai.Personality.damage(zombie, player) > 1.2, "con rencor y experiencia pega más");
		helper.succeed();
	}

	/** Three kills in quick succession scatter the rest, but never an elite. */
	@GameTest
	public void killingSpreeScatters(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 4));
		Zombie witness = zombie(helper, new BlockPos(6, 1, 6));
		Zombie elite = zombie(helper, new BlockPos(6, 1, 2));
		witness.setNoAi(true);
		elite.setNoAi(true);
		for (dev.forja.ai.Personality.Trait trait : dev.forja.ai.Personality.Trait.values()) {
			witness.removeTag(dev.forja.ai.Personality.TRAIT_TAG + trait.name().toLowerCase(java.util.Locale.ROOT));
		}
		witness.addTag(dev.forja.ai.Personality.TRAIT_TAG + "prudente");
		dev.forja.difficulty.Threat.ELITE.mark(elite);
		for (int i = 0; i < 3; i++) {
			Zombie victim = zombie(helper, new BlockPos(2, 1, 1 + i));
			victim.setNoAi(true);
			// The per-hit cap keeps any one blow from killing: leave it on its last point first.
			victim.setHealth(1.0F);
			victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 5.0F);
			helper.assertFalse(victim.isAlive(), "el zombi debería morir");
		}
		helper.assertTrue(dev.forja.ai.Personality.afraid(witness), "tres muertes seguidas deberían asustar");
		helper.assertFalse(dev.forja.ai.Personality.afraid(elite), "un élite no se asusta");
		helper.succeed();
	}

	/** Mobs of a structure have a home; the mod's monsters and vanilla's are not on the same side. */
	@GameTest
	public void homesAndSides(GameTestHelper helper) {
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		dev.forja.ai.Personality.settle(zombie, net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
		helper.assertTrue(dev.forja.ai.Personality.atHome(zombie), "nacido en estructura: en casa");
		var anvil = helper.spawn(dev.forja.registry.ModEntities.YUNQUE_ANDANTE, new BlockPos(5, 1, 5));
		anvil.setNoAi(true);
		helper.assertFalse(dev.forja.ai.Personality.sameSide(zombie, anvil), "vanilla y Forja no se ayudan");
		anvil.discard();
		helper.succeed();
	}

	/** A siege: at least six come, led by an elite, and they hold the forge as their home. */
	@GameTest
	public void siegeComes(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(3, 1, 3));
		helper.getLevel().getServer().getPlayerList();
		int made = dev.forja.ai.WorldFights.siege(helper.getLevel(), player);
		var band = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(40.0),
			m -> m.getTarget() == player);
		helper.assertTrue(made >= dev.forja.ai.WorldFights.SIEGE_MIN, "asedio de " + made);
		helper.assertTrue(band.stream().anyMatch(m -> dev.forja.difficulty.Threat.of(m) == dev.forja.difficulty.Threat.ELITE), "con un élite al frente");
		helper.assertTrue(band.stream().allMatch(m -> dev.forja.ai.Personality.home(m) != null), "defienden la forja como su casa");
		band.forEach(net.minecraft.world.entity.Entity::discard);
		helper.succeed();
	}

	/** An elite that got away comes back another night: named, holding a grudge. */
	@GameTest
	public void nemesisComesBack(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(3, 1, 3));
		Zombie elite = zombie(helper, new BlockPos(1, 1, 1));
		elite.setNoAi(true);
		dev.forja.difficulty.Threat.ELITE.mark(elite);
		elite.setCustomName(net.minecraft.network.chat.Component.literal("Garra"));
		dev.forja.ai.Personality.survived(elite, player);
		elite.discard();
		var back = dev.forja.ai.WorldFights.nemesisReturns(helper.getLevel(), player);
		helper.assertTrue(back != null, "debería volver");
		helper.assertTrue(back.getCustomName().getString().contains("Garra"), "con su nombre: " + back.getCustomName().getString());
		helper.assertTrue(dev.forja.ai.Personality.grudge(back, player), "y rencor");
		back.discard();
		helper.succeed();
	}

	/** A duel: stepping in accepts, the others watch, beating the challenger scatters them. */
	@GameTest(maxTicks = 60)
	public void duelPlaysOut(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		Zombie challenger = zombie(helper, new BlockPos(3, 1, 3));
		Zombie watcher = zombie(helper, new BlockPos(6, 1, 6));
		challenger.setNoAi(true);
		watcher.setNoAi(true);
		challenger.setTarget(player);
		watcher.setTarget(player);
		for (dev.forja.ai.Personality.Trait trait : dev.forja.ai.Personality.Trait.values()) {
			watcher.removeTag(dev.forja.ai.Personality.TRAIT_TAG + trait.name().toLowerCase(java.util.Locale.ROOT));
		}
		watcher.addTag(dev.forja.ai.Personality.TRAIT_TAG + "prudente");
		var duel = dev.forja.ai.Duels.start(challenger, player);
		helper.assertTrue(dev.forja.ai.Duels.watching(watcher), "el otro zombi mira");
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(duel.stage == dev.forja.ai.Duels.Stage.DUELO, "dentro del círculo, el duelo se acepta");
			challenger.setHealth(1.0F);
			challenger.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 5.0F);
			helper.assertFalse(challenger.isAlive(), "el retador cae");
			helper.assertTrue(dev.forja.ai.Personality.afraid(watcher), "ganar el duelo asusta a los que miraban");
			helper.succeed();
		});
	}

	/** The smith remembers: against a player who keeps escaping his wave, he goes in close first. */
	@GameTest
	public void smithRemembers(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		helper.assertFalse(dev.forja.ai.WorldFights.smithPrefersClose(player), "sin recuerdos, abre con la onda");
		player.addTag(dev.forja.ai.WorldFights.SMITH_WAVE_MISS + "_3");
		helper.assertTrue(dev.forja.ai.WorldFights.smithPrefersClose(player), "tras esquivar su onda, va de cerca");
		helper.succeed();
	}

	/** A v2 network with every input and head: all zeros but for its output biases. */
	private static com.google.gson.JsonObject fakeV2(float[] outBias) {
		java.util.List<String> names = new java.util.ArrayList<>(ObsNames.M1);
		names.addAll(dev.forja.ai.ObsForja.names());
		int n = names.size();
		com.google.gson.JsonObject json = new com.google.gson.JsonObject();
		json.addProperty("formato", "red_mob_v2");
		json.addProperty("grupo", "cuerpo");
		json.addProperty("ticks_por_decision", 2);
		com.google.gson.JsonArray namesJson = new com.google.gson.JsonArray();
		names.forEach(namesJson::add);
		json.add("nombres_obs", namesJson);
		json.add("w1", zeros(n, 8));
		json.add("b1", zeros(8));
		json.add("w2", zeros(8, 8));
		json.add("b2", zeros(8));
		json.add("gru_ih", zeros(12, 8));
		json.add("gru_hh", zeros(12, 4));
		json.add("gru_bih", zeros(12));
		json.add("gru_bhh", zeros(12));
		json.add("w_out", zeros(12, outBias.length));
		com.google.gson.JsonArray bias = new com.google.gson.JsonArray();
		for (float b : outBias) {
			bias.add(b);
		}
		json.add("b_out", bias);
		return json;
	}

	private static com.google.gson.JsonArray zeros(int n) {
		com.google.gson.JsonArray a = new com.google.gson.JsonArray();
		for (int i = 0; i < n; i++) {
			a.add(0.0F);
		}
		return a;
	}

	private static com.google.gson.JsonArray zeros(int rows, int cols) {
		com.google.gson.JsonArray a = new com.google.gson.JsonArray();
		for (int i = 0; i < rows; i++) {
			a.add(zeros(cols));
		}
		return a;
	}

	/** A v2 network is accepted, and its heads obey the mask: a special on cooldown is never asked for. */
	@GameTest
	public void v2HeadsAndMask(GameTestHelper helper) {
		float[] bias = new float[NetBrain.V2_OUTPUTS];
		bias[NetBrain.TACTIC_AT + dev.forja.ai.Tactic.RODEAR.ordinal()] = 20.0F;
		bias[NetBrain.SPECIAL_AT + 1] = 20.0F;
		NetBrain net = NetBrain.fromJson(fakeV2(bias));
		helper.assertTrue(MobAi.check(net) == null, "la red v2 debería encajar: " + MobAi.check(net));
		boolean[] mask = new boolean[NetBrain.V2_OUTPUTS];
		java.util.Arrays.fill(mask, true);
		Decision free = NetBrain.sample(net.forward(new float[net.inputs()], new float[net.memory]), 1.0, RandomSource.create(3), mask);
		helper.assertTrue(free.tactic() == dev.forja.ai.Tactic.RODEAR, "la cabeza táctica manda: " + free.tactic());
		helper.assertTrue(free.special() == 1, "con el especial disponible, lo pide");
		mask[NetBrain.SPECIAL_AT + 1] = false;
		Decision masked = NetBrain.sample(net.forward(new float[net.inputs()], new float[net.memory]), 1.0, RandomSource.create(3), mask);
		helper.assertTrue(masked.special() != 1, "con el especial en enfriamiento, no lo pide");
		helper.succeed();
	}

	/** Writes the v2 contract (inputs, outputs, tactics, families) when FORJA_CONTRATO names a file. */
	@GameTest
	public void writeContract(GameTestHelper helper) throws java.io.IOException {
		String file = System.getenv("FORJA_CONTRATO");
		if (file == null || file.isBlank()) {
			helper.succeed();
			return;
		}
		com.google.gson.JsonObject json = new com.google.gson.JsonObject();
		json.addProperty("formato", "red_mob_v2");
		java.util.List<String> names = new java.util.ArrayList<>(ObsNames.M1);
		names.addAll(dev.forja.ai.ObsForja.names());
		json.addProperty("n_obs", names.size());
		com.google.gson.JsonArray obs = new com.google.gson.JsonArray();
		names.forEach(obs::add);
		json.add("nombres_obs", obs);
		com.google.gson.JsonArray outs = new com.google.gson.JsonArray();
		for (String o : new String[] {"quieto", "adelante", "adelante_derecha", "derecha", "atras_derecha", "atras", "atras_izquierda",
			"izquierda", "adelante_izquierda", "saltar", "usar"}) {
			outs.add(o);
		}
		for (dev.forja.ai.Tactic t : dev.forja.ai.Tactic.values()) {
			outs.add("tactica_" + t.name().toLowerCase(java.util.Locale.ROOT));
		}
		for (int k = 0; k <= 4; k++) {
			outs.add("especial_" + k);
		}
		for (String d : new String[] {"defensa_nada", "defensa_escudo", "defensa_esquivar"}) {
			outs.add(d);
		}
		outs.add("fintar");
		json.add("salidas", outs);
		json.addProperty("n_salidas", outs.size());
		com.google.gson.JsonObject families = new com.google.gson.JsonObject();
		for (String f : MobAi.families()) {
			families.addProperty(f, "red_" + f + ".json");
		}
		json.add("familias", families);
		json.addProperty("ticks_por_decision", 2);
		java.nio.file.Files.writeString(Path.of(file),
			new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json));
		helper.succeed();
	}
}
