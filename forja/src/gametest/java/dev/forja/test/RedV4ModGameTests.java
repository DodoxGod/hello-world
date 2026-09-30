package dev.forja.test;

import java.util.List;

import dev.forja.ai.Decision;
import dev.forja.ai.GroundItems;
import dev.forja.ai.Heights;
import dev.forja.ai.Lights;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobItems;
import dev.forja.ai.MobKit;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsV4;
import dev.forja.ai.ShieldPlay;
import dev.forja.ai.SpecialRunner;
import dev.forja.ai.Tactic;
import dev.forja.ai.VanillaSpecials;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.Stamina;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Steps M2 and M3 of the v4 mob network (docs/red_mob_v4_diseno.md §6.1, docs/red_mob_v4_mod_estado.md): the smart
 * shield and its bash, things picked up from the floor, the kit (potions, food, pearls, wind charges), the tools against
 * a player up a pillar (knockback arrow, swipe, hook, siege), torches put out, and the blocks A, O, C, G and L of the v4
 * observation. The rules use every one of them, so most tests run a monster on the rules; a few drive it with a fake v4
 * network to check the heads and the mask.
 */
public class RedV4ModGameTests {
	// ---------------------------------------------------------------- helpers

	/**
	 * A stone floor from (0, 0, 0) to (size − 1, 0, size − 1). A test's 8×8 box is walled with barriers; a bigger floor
	 * takes the barriers in its way down (padding keeps the other tests clear of it).
	 */
	private static void floor(GameTestHelper helper, int size) {
		if (size > 8) {
			TestChunks.force(helper, size);
			for (int x = -1; x <= size; x++) {
				for (int z = -1; z <= size; z++) {
					for (int y = 0; y <= 10; y++) {
						BlockPos at = new BlockPos(x, y, z);
						if (helper.getBlockState(at).is(Blocks.BARRIER)) {
							helper.setBlock(at, Blocks.AIR);
						}
					}
				}
			}
		}
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** A stone column at (x, z) from y = 1 up to y = height (its top face at height + 1). */
	private static void pillar(GameTestHelper helper, int x, int z, int height) {
		for (int y = 1; y <= height; y++) {
			helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
		}
	}

	/**
	 * Clears what a test built: blocks back to air in the box, item entities and projectiles gone. The sweep reaches
	 * four blocks past what was built, so a test that builds past its 8 x 8 box asks for padding: without it the
	 * next test stood 5 blocks off, and two of them (10 and 12 wide) swept its mobs away when they finished.
	 */
	private static void clear(GameTestHelper helper, int size, int height) {
		for (int x = -1; x <= size; x++) {
			for (int z = -1; z <= size; z++) {
				for (int y = 1; y <= height; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		AABB box = new AABB(helper.absoluteVec(new Vec3(-4, -2, -4)), helper.absoluteVec(new Vec3(size + 4, height + 4, size + 4)));
		helper.getLevel().getEntitiesOfClass(Entity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
		if (size > 8) {
			TestChunks.release(helper);
		}
	}



	/** A player standing at a relative block (on whatever is below it), full health, looking along +X. */
	private static CombatGameTests.TestPlayer player(GameTestHelper helper, BlockPos at) {
		return CombatGameTests.player(helper, at);
	}

	/** A zombie with no veteran or elite roll and no shield of its own. */
	private static Zombie zombie(GameTestHelper helper, BlockPos at) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, at);
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		return zombie;
	}

	private static MobMind mind(GameTestHelper helper, Mob mob) {
		MobMind mind = MobAi.mind(mob);
		helper.assertTrue(mind != null, "el mob debería tener cerebro");
		return mind;
	}

	/** Keeps the player where they are and at full health every tick (a FakePlayer does not move itself). */
	private static void hold(GameTestHelper helper, CombatGameTests.TestPlayer player, Vec3 at) {
		helper.onEachTick(() -> {
			player.setPos(at.x, at.y, at.z);
			player.setHealth(player.getMaxHealth());
		});
	}

	/** Why a special is or is not available, for a failing test's message. */
	static String diag(Mob mob, SpecialRunner runner, int k, net.minecraft.world.entity.player.Player p) {
		return "present " + runner.present(k) + " ready " + runner.ready(k) + " canStart " + runner.moveset().get(k).canStart(mob, p)
			+ " sensing " + mob.getSensing().hasLineOfSight(p) + " sees " + dev.forja.ai.ObsM1.sees(mob, p.getX(), p.getEyeY(), p.getZ())
			+ " d " + mob.distanceTo(p) + " rod " + MobKit.hasRod(mob) + " spare " + MobKit.spare(mob) + " up " + Heights.of(p).overGround
			+ " edge " + Heights.of(p).edge + " p " + p.position() + " m " + mob.position();
	}

	/** The ground of the ring at 2.5 round the player, for a failing test's message. */
	static String ring(net.minecraft.world.entity.player.Player p) {
		StringBuilder out = new StringBuilder("feet " + p.getY() + " ring");
		int feetBlock = (int) Math.floor(p.getY() + 1.0E-3);
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4.0;
			double top = Heights.top(p.level(), p.getX() + Math.cos(a) * 2.5, p.getZ() + Math.sin(a) * 2.5, feetBlock + 1);
			out.append(' ').append(String.format(java.util.Locale.ROOT, "%.2f", top - p.getY()));
		}
		return out.toString();
	}

	/** The drop in each of the 8 directions at 1.5 round the player, for a failing test's message. */
	static String drops(net.minecraft.world.entity.player.Player p) {
		StringBuilder out = new StringBuilder("feet " + p.getY() + " drops");
		int feetBlock = (int) Math.floor(p.getY() + 1.0E-3);
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4.0;
			double top = Heights.top(p.level(), p.getX() + Math.cos(a) * 1.5, p.getZ() + Math.sin(a) * 1.5, feetBlock);
			out.append(' ').append(String.format(java.util.Locale.ROOT, "%.2f", p.getY() - top));
		}
		return out.toString();
	}

	// ---------------------------------------------------------------- G: the shield and its bash

	/**
	 * A zombie with its shield up blocks a player's blow; right after, its bash is ready (golpe_escudo_listo = 1 and the
	 * mask open), and once struck it lands after its 4-tick warning: the shield comes down, the player is shoved away
	 * and loses 15 stamina. 20 ticks after the block the bash is no longer offered.
	 */
	@GameTest(maxTicks = 80)
	public void shieldBashAfterABlock(GameTestHelper helper) {
		floor(helper, 8);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(3, 1, 3));
		Vec3 close = helper.absoluteVec(new Vec3(3.6, 1.0, 3.5));
		player.setPos(close.x, close.y, close.z);
		Zombie zombie = zombie(helper, new BlockPos(2, 1, 3));
		zombie.setNoAi(true);
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		zombie.setTarget(player);
		MobMind mind = mind(helper, zombie);
		long[] blockedAt = {0};
		float[] staminaBefore = {0.0F};
		helper.runAfterDelay(3, () -> {
			zombie.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
			zombie.setYBodyRot(zombie.getYRot());
			zombie.startUsingItem(InteractionHand.OFF_HAND);
		});
		helper.runAfterDelay(12, () -> {
			long now = helper.getLevel().getGameTime();
			helper.assertFalse(ShieldPlay.bashReady(zombie, mind, player, now), "sin un bloqueo antes no hay golpe de escudo");
			zombie.invulnerableTime = 0;
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 3.0F);
			blockedAt[0] = now;
			helper.assertTrue(mind.lastBlockAt == now, "el bloqueo debería quedar anotado: " + mind.lastBlockAt + " / " + now);
			helper.assertTrue(ShieldPlay.bashReady(zombie, mind, player, now), "tras bloquear, con el jugador al alcance, el golpe de escudo está listo");
			float[] obs = ObsV4.build(zombie, player, mind);
			helper.assertTrue(obs[ObsV4.G_AT + 4] == 1.0F, "golpe_escudo_listo debería ser 1: " + obs[ObsV4.G_AT + 4]);
			helper.assertTrue(obs[ObsV4.G_AT + 3] == 0.0F, "bloqueo_hace/20 recién bloqueado: " + obs[ObsV4.G_AT + 3]);
			helper.assertTrue(obs[ObsV4.G_AT] > 0.0F, "yo_escudo_ticks/20 con el escudo arriba: " + obs[ObsV4.G_AT]);
			boolean[] mask = new boolean[NetBrain.V4_OUTPUTS];
			java.util.Arrays.fill(mask, true);
			dev.forja.test.RedV4ModGameTests.maskV4(zombie, mind, player, mask);
			helper.assertTrue(mask[NetBrain.SHIELD_BASH_AT], "la máscara del golpe de escudo debería abrirse");
			staminaBefore[0] = Stamina.value(player);
			player.setDeltaMovement(Vec3.ZERO);
			helper.assertTrue(ShieldPlay.start(zombie, mind, player, now), "el golpe de escudo debería empezar");
		});
		helper.runAfterDelay(18, () -> {
			helper.assertTrue(mind.bashWindup == 0 && mind.lastBash > blockedAt[0], "el golpe de escudo debería haber llegado");
			helper.assertFalse(ShieldPlay.up(zombie), "tras el golpe el escudo baja");
			Vec3 push = player.getDeltaMovement();
			helper.assertTrue(push.x > 0.3, "el jugador debería salir empujado lejos del zombi (+X): " + push);
			helper.assertTrue(Stamina.value(player) <= staminaBefore[0] - 14.0F || Stamina.value(player) == 0.0F,
				"el jugador debería perder 15 de estamina: " + staminaBefore[0] + " -> " + Stamina.value(player));
		});
		helper.runAfterDelay(40, () -> {
			zombie.startUsingItem(InteractionHand.OFF_HAND);
			helper.assertFalse(ShieldPlay.bashReady(zombie, mind, player, helper.getLevel().getGameTime()),
				"20 ticks después del bloqueo ya no hay golpe de escudo");
			zombie.discard();
			clear(helper, 8, 3);
			helper.succeed();
		});
	}

	/** MobAi.maskV4 is package-private: reached through the mask of the whole network. */
	static void maskV4(Mob mob, MobMind mind, net.minecraft.world.entity.player.Player target, boolean[] mask) {
		boolean[] full = MobAi.mask(mob, mind, target, NetBrain.V4_OUTPUTS);
		System.arraycopy(full, 0, mask, 0, Math.min(full.length, mask.length));
	}

	// ---------------------------------------------------------------- O: things on the floor

	/**
	 * A zombie with a wooden sword fighting by the rules goes for the diamond sword the player dropped (once its pickup
	 * delay is over), takes it in 10 ticks, keeps the wooden one in its spare slot, and, killed, drops the diamond sword
	 * for sure. Its kit (a healing potion) is never dropped.
	 */
	@GameTest(maxTicks = 300)
	public void zombieTakesThePlayersSwordAndDropsItOnDeath(GameTestHelper helper) {
		floor(helper, 8);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 7));
		hold(helper, player, player.position());
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
		MobKit.add(zombie, MobKit.Kind.HEAL, "minecraft:healing", 1);
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 1, 5)));
		ItemEntity sword = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.DIAMOND_SWORD));
		sword.setThrower(player);
		sword.setPickUpDelay(40);
		sword.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(sword);
		zombie.setTarget(player);
		MobMind mind = mind(helper, zombie);
		// held where it is while the sword cannot be taken yet: with a turn free and the player near, it goes for the
		// player instead (a free turn is used), and it used to wait out vanilla's 20-tick look first
		Vec3 home = zombie.position();
		helper.onEachTick(() -> {
			if (sword.isAlive() && !GroundItems.free(sword)) {
				zombie.setPos(home.x, home.y, home.z);
				zombie.getNavigation().stop();
			}
		});
		helper.runAfterDelay(20, () -> {
			float[] obs = ObsV4.build(zombie, player, mind);
			helper.assertTrue(obs[ObsV4.O_AT] == 0.0F, "con el retraso de recogida la espada aún no cuenta: " + obs[ObsV4.O_AT]);
		});
		boolean[] seen = {false};
		helper.onEachTick(() -> {
			if (!seen[0] && sword.isAlive() && GroundItems.free(sword) && helper.getLevel().getGameTime() % 5 == 0) {
				float[] obs = ObsV4.build(zombie, player, mind);
				if (obs[ObsV4.O_AT] == 1.0F) {
					seen[0] = true;
					helper.assertTrue(obs[ObsV4.O_AT + 5] == 1.0F, "objeto0_del_jugador debería ser 1");
					helper.assertTrue(obs[ObsV4.O_AT + 6] == 1.0F, "objeto0_arma_cuerpo debería ser 1");
					helper.assertTrue(obs[ObsV4.O_AT + 4] > 0.4F, "la de diamante (11,2) mejora la de madera (6,4) en 4,8, /10: " + obs[ObsV4.O_AT + 4]);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.getMainHandItem().is(Items.DIAMOND_SWORD), "el zombi debería llevar la espada de diamante");
			helper.assertTrue(seen[0], "el bloque O debería haber visto la espada del jugador");
			helper.assertTrue(MobKit.spare(zombie).is(Items.WOODEN_SWORD), "la de madera debería ir a su ranura de repuesto: " + MobKit.spare(zombie));
			ServerLevel level = helper.getLevel();
			AABB around = zombie.getBoundingBox().inflate(3.0);
			level.getEntitiesOfClass(ItemEntity.class, around).forEach(Entity::discard);
			zombie.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, around);
			helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.DIAMOND_SWORD)), "al morir debería soltar la espada del jugador: " + drops);
			helper.assertFalse(drops.stream().anyMatch(e -> e.getItem().is(Items.POTION)), "los consumibles nunca se sueltan: " + drops);
			clear(helper, 8, 3);
		});
	}

	/** A shield on the floor goes to the off hand of a zombie without one; a wind charge into the kit; neither is dropped twice. */
	@GameTest(maxTicks = 40)
	public void shieldAndWindChargeFromTheFloor(GameTestHelper helper) {
		floor(helper, 8);
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(2, 1, 1)));
		ItemEntity shield = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.SHIELD));
		ItemEntity wind = new ItemEntity(helper.getLevel(), at.x, at.y, at.z + 0.4, new ItemStack(Items.WIND_CHARGE, 5));
		helper.getLevel().addFreshEntity(shield);
		helper.getLevel().addFreshEntity(wind);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(GroundItems.gain(zombie, shield.getItem()) > 0.0, "un escudo le sirve a un zombi sin escudo");
			helper.assertTrue(GroundItems.take(zombie, shield), "debería coger el escudo");
			helper.assertTrue(zombie.getOffhandItem().is(Items.SHIELD), "el escudo va a la mano izquierda");
			helper.assertTrue(GroundItems.gain(zombie, new ItemStack(Items.SHIELD)) <= 0.0, "un segundo escudo ya no le sirve");
			helper.assertTrue(GroundItems.take(zombie, wind), "debería coger las cargas de viento");
			helper.assertTrue(MobKit.count(zombie, MobKit.Kind.WIND) == 3, "como mucho 3 cargas de viento en la mochila: " + MobKit.count(zombie, MobKit.Kind.WIND));
			helper.assertTrue(wind.isAlive() && wind.getItem().getCount() == 2, "las que no caben se quedan en el suelo: " + wind.getItem());
			zombie.discard();
			clear(helper, 8, 3);
			helper.succeed();
		});
	}

	/** Weapon values: a diamond sword is worth more than an iron one, and a bow is worth nothing to a zombie. */
	@GameTest(maxTicks = 20)
	public void weaponValues(GameTestHelper helper) {
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		double iron = GroundItems.meleeValue(new ItemStack(Items.IRON_SWORD));
		double diamond = GroundItems.meleeValue(new ItemStack(Items.DIAMOND_SWORD));
		helper.assertTrue(Math.abs(iron - 6.0 * 1.6) < 0.05, "espada de hierro = 6 × 1,6 = 9,6: " + iron);
		helper.assertTrue(diamond > iron, "la de diamante vale más: " + diamond + " > " + iron);
		helper.assertTrue(GroundItems.valueFor(zombie, new ItemStack(Items.BOW)) == 0.0 || zombie.getMainHandItem().isEmpty(),
			"un arco no le vale de nada a un zombi armado");
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(3, 1, 1));
		skeleton.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		helper.assertTrue(GroundItems.gain(skeleton, new ItemStack(Items.DIAMOND_SWORD)) <= 0.0, "un esqueleto con arco no cambia el arco por una espada");
		zombie.discard();
		skeleton.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- C: the kit

	/** Badly hurt and out of the player's reach, a zombie on the rules drinks its healing potion: 32 ticks at half speed, +4. */
	@GameTest(maxTicks = 120)
	public void zombieDrinksItsHealingPotion(GameTestHelper helper) {
		floor(helper, 8);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 1));
		hold(helper, player, player.position());
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		MobKit.add(zombie, MobKit.Kind.HEAL, "minecraft:healing", 1);
		zombie.setHealth(5.0F);
		zombie.setTarget(player);
		MobMind mind = mind(helper, zombie);
		boolean[] drinking = {false};
		helper.onEachTick(() -> {
			if (mind.itemAction == MobItems.HEAL && mind.itemTicks > 0) {
				drinking[0] = true;
				helper.assertTrue(zombie.getOffhandItem().is(Items.POTION), "mientras bebe se le ve la poción en la mano");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(drinking[0], "debería haber bebido");
			helper.assertTrue(zombie.getHealth() >= 9.0F, "la curación da +4: " + zombie.getHealth());
			helper.assertTrue(MobKit.count(zombie, MobKit.Kind.HEAL) == 0, "la poción se gasta");
			helper.assertTrue(zombie.getOffhandItem().isEmpty(), "la mano izquierda vuelve a quedar vacía");
			zombie.discard();
			clear(helper, 8, 3);
		});
	}

	/** Hurt, a zombie on the rules eats the bread it carries: +4 health. */
	@GameTest(maxTicks = 120)
	public void zombieEatsItsBread(GameTestHelper helper) {
		floor(helper, 8);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 1));
		hold(helper, player, player.position());
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		MobKit.add(zombie, MobKit.Kind.FOOD, "minecraft:bread", 1);
		zombie.setHealth(8.0F);
		zombie.setTarget(player);
		helper.succeedWhen(() -> {
			helper.assertTrue(MobKit.count(zombie, MobKit.Kind.FOOD) == 0, "debería comerse el pan");
			helper.assertTrue(zombie.getHealth() >= 12.0F, "comer da +4: " + zombie.getHealth());
			zombie.discard();
			clear(helper, 8, 3);
		});
	}

	/** At 6 blocks, a zombie on the rules throws the slowness potion it carries at the player. */
	@GameTest(padding = 16, maxTicks = 120)
	public void zombieThrowsItsSplashPotion(GameTestHelper helper) {
		floor(helper, 10);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 1));
		hold(helper, player, player.position());
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		MobKit.add(zombie, MobKit.Kind.SPLASH, "minecraft:slowness", 1);
		zombie.setTarget(player);
		boolean[] thrown = {false};
		helper.onEachTick(() -> {
			if (!helper.getLevel().getEntitiesOfClass(ThrownSplashPotion.class, zombie.getBoundingBox().inflate(10.0), p -> p.getOwner() == zombie).isEmpty()) {
				thrown[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(thrown[0], "debería lanzar la poción");
			helper.assertTrue(MobKit.count(zombie, MobKit.Kind.SPLASH) == 0, "la poción arrojadiza se gasta");
			zombie.discard();
			clear(helper, 10, 3);
		});
	}

	/**
	 * A player up a pillar 4 high: an elite zombie on the rules with an ender pearl throws it once they have been up for
	 * 2 seconds, and lands up there by them (or on them): within 3 blocks, having taken the pearl's 2 damage.
	 */
	@GameTest(padding = 16, maxTicks = 300)
	public void pearlBringsTheZombieUpThePillar(GameTestHelper helper) {
		floor(helper, 12);
		pillar(helper, 9, 6, 4);
		Vec3 top = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(9, 5, 6)));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(9, 5, 6));
		hold(helper, player, top);
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 6));
		zombie.setNoAi(true);
		MobKit.add(zombie, MobKit.Kind.PEARL, "", 1);
		zombie.setTarget(player);
		float health = zombie.getHealth();
		helper.runAfterDelay(15, () -> {
			Heights.State h = Heights.of(player);
			helper.assertTrue(h.pillar && h.overGround > 3.5, "el jugador está en un pilar de 4: " + h.overGround + " pilar " + h.pillar + " " + drops(player));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(MobKit.count(zombie, MobKit.Kind.PEARL) == 0, "debería lanzar la perla");
			helper.assertTrue(zombie.distanceTo(player) < 3.0, "la perla debería dejarlo junto al jugador: " + zombie.distanceTo(player));
			helper.assertTrue(zombie.getHealth() <= health - 1.9F, "la perla le hace 2 de daño: " + health + " -> " + zombie.getHealth());
			zombie.discard();
			clear(helper, 12, 6);
		});
	}

	/**
	 * A player up a pillar 3 high: a zombie on the rules with wind charges throws one at their feet, which lifts them.
	 * And a monster's wind charge bursting against a closed oak door leaves it closed (a player's opens it).
	 */
	@GameTest(padding = 16, maxTicks = 200)
	public void windChargeLiftsThePlayerAndLeavesDoorsShut(GameTestHelper helper) {
		floor(helper, 12);
		pillar(helper, 7, 3, 3);
		Vec3 top = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(7, 4, 3)));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 4, 3));
		player.setPos(top.x, top.y, top.z);
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 3));
		zombie.setNoAi(true);
		MobKit.add(zombie, MobKit.Kind.WIND, "", 1);
		zombie.setTarget(player);
		// the door, well away from the player
		BlockPos door = new BlockPos(3, 1, 9);
		helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState());
		helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		boolean[] thrown = {false};
		double[] lift = {0.0};
		// A test player is not in the level's entity lists, so no blast finds it: the lift is checked on a pig, which is.
		var pig = helper.spawn(EntityTypes.PIG, new BlockPos(6, 1, 8));
		pig.setNoAi(true);
		helper.onEachTick(() -> {
			player.setPos(top.x, top.y, top.z);
			if (!helper.getLevel().getEntitiesOfClass(WindCharge.class, zombie.getBoundingBox().inflate(12.0), c -> c.getOwner() == zombie).isEmpty()) {
				thrown[0] = true;
			}
			lift[0] = Math.max(lift[0], pig.getDeltaMovement().y);
			player.setHealth(player.getMaxHealth());
		});
		helper.runAfterDelay(6, () -> MobItems.throwWind(zombie, pig));
		helper.runAfterDelay(4, () -> {
			// a monster's wind charge right against the door
			Vec3 at = helper.absoluteVec(Vec3.atCenterOf(door).add(0.0, 0.0, -1.2));
			WindCharge charge = new WindCharge(helper.getLevel(), at.x, at.y, at.z, Vec3.ZERO);
			charge.setOwner(zombie);
			charge.shoot(0.0, 0.0, 1.0, 1.0F, 0.0F);
			helper.getLevel().addFreshEntity(charge);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(thrown[0], "debería lanzar la carga de viento");
			helper.assertTrue(MobKit.count(zombie, MobKit.Kind.WIND) == 0, "la carga de viento se gasta");
			helper.assertTrue(lift[0] > 0.2, "la carga de viento debería levantar lo que alcanza: " + lift[0]);
			BlockState state = helper.getBlockState(door);
			helper.assertTrue(state.is(Blocks.OAK_DOOR) && !state.getValue(DoorBlock.OPEN), "la carga de un monstruo no abre puertas");
			zombie.discard();
			pig.discard();
			clear(helper, 12, 5);
		});
	}

	// ---------------------------------------------------------------- A: height, and the tools against a pillar

	/** Block A's numbers for a player on flat ground, up a 1×1 pillar 4 high, and in a walled tower 3 high. */
	@GameTest(padding = 16, maxTicks = 40)
	public void heightsOfAPillarAndATower(GameTestHelper helper) {
		floor(helper, 18);
		pillar(helper, 3, 3, 4);
		// the tower: a 3×3 platform 3 high with a wall round its top
		for (int x = 8; x <= 12; x++) {
			for (int z = 8; z <= 12; z++) {
				pillar(helper, x, z, 3);
				if (x == 8 || x == 12 || z == 8 || z == 12) {
					helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
					helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
				}
			}
		}
		CombatGameTests.TestPlayer flat = player(helper, new BlockPos(13, 1, 4));
		CombatGameTests.TestPlayer up = player(helper, new BlockPos(3, 5, 3));
		CombatGameTests.TestPlayer tower = player(helper, new BlockPos(10, 4, 10));
		helper.runAfterDelay(2, () -> {
			Heights.State f = Heights.of(flat);
			helper.assertTrue(Math.abs(f.overGround) < 0.1 && !f.pillar && !f.tower && f.edge >= Heights.EDGE_RANGE,
				"en llano: sobre el suelo " + f.overGround + ", pilar " + f.pillar + ", borde " + f.edge);
			Heights.State p = Heights.of(up);
			helper.assertTrue(Math.abs(p.overGround - 4.0) < 0.1, "en el pilar: 4 sobre el suelo, da " + p.overGround);
			helper.assertTrue(p.pillar, "debería contar como pilar");
			helper.assertTrue(p.edge <= 0.5, "en un pilar de 1×1 el borde está a 0,5: " + p.edge);
			helper.assertTrue(p.climbable, "un pilar se puede trepar (arañas)");
			Heights.State t = Heights.of(tower);
			helper.assertTrue(t.tower && !t.pillar, "en la torre: torre " + t.tower + ", pilar " + t.pillar + ", sobre el suelo " + t.overGround + " " + ring(tower));
			Zombie zombie = zombie(helper, new BlockPos(1, 1, 3));
			zombie.setNoAi(true);
			helper.assertFalse(Heights.reachable(zombie, up), "desde el suelo un zombi no alcanza a un jugador a 4 de altura");
			helper.assertTrue(Heights.pushFall(zombie, up) > 3.5, "empujado desde el pilar caería 4: " + Heights.pushFall(zombie, up));
			helper.assertTrue(Heights.besieged(up) && Heights.besieged(tower) && !Heights.besieged(flat), "ASEDIAR: pilar y torre sí, llano no");
			zombie.discard();
			clear(helper, 18, 6);
			helper.succeed();
		});
	}

	/**
	 * A skeleton's knockback arrow at a player up a pillar 3 high: offered only while they are up there, warned for 20
	 * ticks, and the arrow shoves them away from the skeleton.
	 */
	@GameTest(padding = 16, maxTicks = 120)
	public void knockbackArrowShovesThePlayerOffThePillar(GameTestHelper helper) {
		floor(helper, 12);
		pillar(helper, 9, 3, 3);
		Vec3 top = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(9, 4, 3)));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(9, 4, 3));
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 3));
		skeleton.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		double[] shove = {0.0};
		boolean[] released = {false};
		helper.onEachTick(() -> {
			if (!released[0]) {
				player.setPos(top.x, top.y, top.z);
				player.setDeltaMovement(Vec3.ZERO);
			} else {
				shove[0] = Math.max(shove[0], player.getDeltaMovement().x);
			}
			player.setHealth(player.getMaxHealth());
		});
		helper.runAfterDelay(3, () -> {
			SpecialRunner runner = mind(helper, skeleton).specials;
			helper.assertTrue(runner != null && runner.moveset().get(2) == VanillaSpecials.KNOCKBACK_ARROW, "el esqueleto lleva la flecha de empuje en el hueco 3");
			helper.assertTrue(runner.available(2, player), "con el jugador en el pilar la flecha de empuje está disponible: " + diag(skeleton, runner, 2, player));
			helper.assertTrue(runner.start(2, player), "debería empezar la flecha de empuje");
			// a still skeleton (no AI) runs no goals: its warning is ticked here, 20 ticks to the release
			for (int i = 0; i < VanillaSpecials.KNOCKBACK_ARROW.windup; i++) {
				runner.tick();
			}
		});
		helper.runAfterDelay(5, () -> {
			var arrows = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
				skeleton.getBoundingBox().inflate(6.0), a -> a.getOwner() == skeleton);
			helper.assertTrue(arrows.size() == 1 && arrows.get(0).entityTags().contains(VanillaSpecials.KNOCKBACK_TAG),
				"tras 20 ticks de aviso sale una flecha de empuje: " + arrows.size());
			// the test player is not in the level's entity lists, so the arrow cannot hit it: its hit is played directly
			released[0] = true;
			player.setDeltaMovement(Vec3.ZERO);
			VanillaSpecials.knockbackHit(player, helper.getLevel().damageSources().arrow(arrows.get(0), skeleton), false);
			shove[0] = player.getDeltaMovement().x;
			double lift = player.getDeltaMovement().y;
			helper.assertTrue(shove[0] > 0.8 && lift > 0.1, "la flecha de empuje lo empuja 0,9 hacia +X y 0,15 arriba: " + player.getDeltaMovement());
			player.setDeltaMovement(Vec3.ZERO);
			VanillaSpecials.knockbackHit(player, helper.getLevel().damageSources().arrow(arrows.get(0), skeleton), true);
			helper.assertTrue(player.getDeltaMovement().lengthSqr() < 1.0E-6, "bloqueada no empuja");
			arrows.forEach(Entity::discard);
			skeleton.discard();
			clear(helper, 12, 5);
			helper.succeed();
		});
	}

	/** On flat ground the knockback arrow is never offered (nothing to bring down). */
	@GameTest(padding = 16, maxTicks = 20)
	public void knockbackArrowNotOnFlatGround(GameTestHelper helper) {
		floor(helper, 12);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(6, 1, 6));
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 6));
		skeleton.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		helper.runAfterDelay(3, () -> {
			SpecialRunner runner = mind(helper, skeleton).specials;
			helper.assertFalse(runner.available(2, player), "en llano no hay flecha de empuje: " + diag(skeleton, runner, 2, player));
			skeleton.discard();
			clear(helper, 12, 3);
			helper.succeed();
		});
	}

	/** A spider up beside a player on a pillar swipes: a 6-tick crouch, then the player is shoved 1.0 away. */
	@GameTest(padding = 16, maxTicks = 60)
	public void spiderSwipesThePlayerOffThePillar(GameTestHelper helper) {
		floor(helper, 10);
		pillar(helper, 5, 3, 3);
		pillar(helper, 4, 3, 3);
		Vec3 top = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(5, 4, 3)));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(5, 4, 3));
		var spider = helper.spawn(EntityTypes.CAVE_SPIDER, new BlockPos(4, 4, 3));
		spider.setNoAi(true);
		boolean[] released = {false};
		double[] shove = {0.0};
		helper.onEachTick(() -> {
			if (!released[0]) {
				player.setPos(top.x, top.y, top.z);
				player.setDeltaMovement(Vec3.ZERO);
			} else {
				shove[0] = Math.max(shove[0], player.getDeltaMovement().x);
			}
			player.setHealth(player.getMaxHealth());
		});
		helper.runAfterDelay(3, () -> {
			SpecialRunner runner = mind(helper, spider).specials;
			helper.assertTrue(runner.available(2, player), "junto al jugador en lo alto, el zarpazo está disponible: " + spider.distanceTo(player));
			helper.assertTrue(runner.start(2, player), "debería empezar el zarpazo");
			for (int i = 0; i < 6; i++) {
				runner.tick();
			}
			released[0] = true;
			shove[0] = player.getDeltaMovement().x;
		});
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(shove[0] > 0.5, "el zarpazo debería empujarlo hacia +X: " + shove[0]);
			spider.discard();
			clear(helper, 10, 5);
			helper.succeed();
		});
	}

	/** A zombie with a fishing rod it picked up hooks a player 7 blocks off: 10 ticks, then a pull of 1.0 towards it. */
	@GameTest(padding = 16, maxTicks = 60)
	public void hookPullsThePlayer(GameTestHelper helper) {
		floor(helper, 12);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(8, 1, 3));
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 3));
		zombie.setNoAi(true);
		helper.runAfterDelay(3, () -> {
			SpecialRunner runner = mind(helper, zombie).specials;
			helper.assertFalse(runner.available(2, player), "sin caña no hay garfio");
			MobKit.setSpare(zombie, new ItemStack(Items.FISHING_ROD), true, 1.0F);
			helper.assertTrue(runner.available(2, player), "con la caña recogida el garfio está disponible: " + diag(zombie, runner, 2, player));
			player.setDeltaMovement(Vec3.ZERO);
			helper.assertTrue(runner.start(2, player), "debería empezar el garfio");
			for (int i = 0; i < 10; i++) {
				runner.tick();
			}
			helper.assertTrue(player.getDeltaMovement().x < -0.5, "el garfio debería tirar de él hacia el zombi (−X): " + player.getDeltaMovement());
			zombie.discard();
			clear(helper, 12, 3);
			helper.succeed();
		});
	}

	/**
	 * Three zombies on the rules and a player up a 5-high pillar they cannot reach: they take ASEDIAR and wait 6 to 10
	 * blocks from its foot, nobody under it; no block changes (no building, no digging).
	 */
	@GameTest(padding = 16, maxTicks = 260)
	public void zombiesBesiegeAPillar(GameTestHelper helper) {
		floor(helper, 16);
		pillar(helper, 8, 8, 5);
		Vec3 top = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(8, 6, 8)));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(8, 6, 8));
		hold(helper, player, top);
		Zombie[] zombies = {zombie(helper, new BlockPos(2, 1, 2)), zombie(helper, new BlockPos(14, 1, 3)), zombie(helper, new BlockPos(3, 1, 14))};
		for (Zombie zombie : zombies) {
			zombie.setTarget(player);
		}
		int[] sieging = new int[3];
		helper.onEachTick(() -> {
			for (int i = 0; i < 3; i++) {
				zombies[i].setTarget(player);
				MobMind mind = MobAi.mind(zombies[i]);
				if (mind != null && mind.decision.tactic() == Tactic.ASEDIAR) {
					sieging[i]++;
				}
			}
		});
		helper.runAfterDelay(240, () -> {
			Vec3 base = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(8, 1, 8)));
			for (int i = 0; i < 3; i++) {
				helper.assertTrue(sieging[i] > 100, "el zombi " + i + " debería asediar: " + sieging[i] + " ticks");
				double d = Math.hypot(zombies[i].getX() - base.x, zombies[i].getZ() - base.z);
				helper.assertTrue(d > 4.0 && d < 12.0, "el zombi " + i + " debería esperar a 6-10 bloques del pie del pilar: " + d);
			}
			for (int y = 1; y <= 5; y++) {
				helper.assertTrue(helper.getBlockState(new BlockPos(8, y, 8)).is(Blocks.STONE), "el pilar sigue entero");
			}
			for (int x = 1; x < 16; x++) {
				for (int z = 1; z < 16; z++) {
					if (x == 8 && z == 8) {
						continue;
					}
					helper.assertTrue(helper.getBlockState(new BlockPos(x, 1, z)).isAir(), "nadie pone bloques: (" + x + ", 1, " + z + ") " + helper.getBlockState(new BlockPos(x, 1, z)));
				}
			}
			for (Zombie zombie : zombies) {
				zombie.discard();
			}
			clear(helper, 16, 7);
			helper.succeed();
		});
	}

	/**
	 * A closed stone bunker with the player inside, lit by a torch inside, and outside an elite with pearls, wind charges
	 * and splash potions, and two zombies: in 12 seconds not a block of it changes, the torch inside stays lit, the
	 * player is untouched and nobody gets in (Andy's decision 5: a fully closed base is safe).
	 */
	@GameTest(padding = 16, maxTicks = 300)
	public void aClosedBunkerStaysSafe(GameTestHelper helper) {
		floor(helper, 14);
		// the bunker: walls and roof round a 3×3×3 room from (6..8, 1..3, 6..8)
		for (int x = 5; x <= 9; x++) {
			for (int z = 5; z <= 9; z++) {
				for (int y = 1; y <= 4; y++) {
					boolean inside = x >= 6 && x <= 8 && z >= 6 && z <= 8 && y <= 3;
					helper.setBlock(new BlockPos(x, y, z), inside ? Blocks.AIR : Blocks.STONE);
				}
			}
		}
		helper.setBlock(new BlockPos(6, 1, 6), Blocks.TORCH);
		helper.setBlock(new BlockPos(9, 2, 7), Blocks.GLASS);
		Vec3 inside = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(7, 1, 7)));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 7));
		hold(helper, player, inside);
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Zombie elite = zombie(helper, new BlockPos(1, 1, 7));
		dev.forja.difficulty.Threat.ELITE.mark(elite);
		MobKit.add(elite, MobKit.Kind.PEARL, "", 2);
		MobKit.add(elite, MobKit.Kind.WIND, "", 3);
		MobKit.add(elite, MobKit.Kind.SPLASH, "minecraft:harming", 3);
		Zombie[] others = {zombie(helper, new BlockPos(12, 1, 7)), zombie(helper, new BlockPos(7, 1, 12))};
		BlockState[][][] before = new BlockState[5][5][4];
		for (int x = 5; x <= 9; x++) {
			for (int z = 5; z <= 9; z++) {
				for (int y = 1; y <= 4; y++) {
					before[x - 5][z - 5][y - 1] = helper.getBlockState(new BlockPos(x, y, z));
				}
			}
		}
		helper.onEachTick(() -> {
			elite.setTarget(player);
			for (Zombie z : others) {
				z.setTarget(player);
			}
		});
		helper.runAfterDelay(260, () -> {
			for (int x = 5; x <= 9; x++) {
				for (int z = 5; z <= 9; z++) {
					for (int y = 1; y <= 4; y++) {
						helper.assertTrue(helper.getBlockState(new BlockPos(x, y, z)).equals(before[x - 5][z - 5][y - 1]),
							"el búnker no debería cambiar en (" + x + ", " + y + ", " + z + ")");
					}
				}
			}
			AABB room = new AABB(helper.absoluteVec(new Vec3(6, 1, 6)), helper.absoluteVec(new Vec3(9, 4, 9)));
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(Mob.class, room).isEmpty(), "ningún monstruo debería entrar en el búnker");
			elite.discard();
			for (Zombie z : others) {
				z.discard();
			}
			clear(helper, 14, 6);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- L: torches

	/**
	 * In a dark roofed room lit by one torch, a zombie on the rules without a turn puts the torch out (15 ticks of
	 * striking, the torch drops); a lantern beside it never counts. With mobGriefing off it never does.
	 *
	 * <p>mobGriefing is the world's, not the test's: for its first hundred ticks it was off for every test of the batch
	 * running beside this one. This test runs in a batch of its own (forja-test:reglas_de_mundo) for that.
	 */
	@GameTest(environment = "forja-test:reglas_de_mundo", padding = 16, maxTicks = 400)
	public void zombiePutsOutTheTorchOnlyWithGriefing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
		room(helper);
		BlockPos torch = new BlockPos(5, 1, 3);
		helper.setBlock(torch, Blocks.TORCH);
		helper.setBlock(new BlockPos(5, 1, 7), Blocks.LANTERN);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 5));
		hold(helper, player, player.position());
		Zombie zombie = zombie(helper, new BlockPos(2, 1, 5));
		zombie.setTarget(player);
		level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
		MobMind mind = mind(helper, zombie);
		// Others hold every turn on the player (still, beside them), so this one is free for the torch.
		int turns = dev.forja.ai.Aggression.maxAttackers(zombie, player);
		List<Zombie> holders = new java.util.ArrayList<>();
		for (int i = 0; i < turns; i++) {
			Zombie holder = zombie(helper, new BlockPos(8, 1, 3 + i));
			holder.setNoAi(true);
			holders.add(holder);
		}
		helper.onEachTick(() -> {
			zombie.setTarget(player);
			for (Zombie holder : holders) {
				holder.setTarget(player);
				dev.forja.combat.AttackTokens.tryAcquire(player, holder, turns);
			}
		});
		helper.runAfterDelay(20, () -> {
			List<Lights.Torch> torches = Lights.near(player);
			helper.assertTrue(torches.size() == 1, "solo la antorcha cuenta, el farol no: " + torches);
			helper.assertTrue(torches.get(0).share() >= 3, "en la sala oscura la antorcha da luz al jugador: " + torches.get(0));
			float[] obs = ObsV4.build(zombie, player, mind);
			helper.assertTrue(obs[ObsV4.L_AT] == 1.0F && obs[ObsV4.L_AT + 4] > 0.1F, "luz0_presente y su aporte: " + obs[ObsV4.L_AT] + " " + obs[ObsV4.L_AT + 4]);
			helper.assertTrue(obs[ObsV4.JUG_LUZ] < 1.0F, "jug_luz/15 en la sala oscura: " + obs[ObsV4.JUG_LUZ]);
			helper.assertFalse(Lights.canPutOut(zombie, player), "sin mobGriefing no se apaga nada");
		});
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(helper.getBlockState(torch).is(Blocks.TORCH), "sin mobGriefing la antorcha sigue");
			level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
			Lights.forget(player);
		});
		boolean[] on = {false};
		helper.runAfterDelay(101, () -> on[0] = true);
		helper.succeedWhen(() -> {
			helper.assertTrue(on[0], "aún sin mobGriefing");
			List<Lights.Torch> near = Lights.forMob(zombie, player);
			helper.assertTrue(helper.getBlockState(torch).isAir(), "con mobGriefing la antorcha debería apagarse: " + helper.getBlockState(torch)
				+ " (táctica " + mind.decision.tactic() + ", permitido " + Lights.allowed(level) + ", antorchas " + near
				+ ", alcanzable " + (near.isEmpty() ? null : Lights.reachable(zombie, near.get(0).pos())) + ", turno "
				+ dev.forja.combat.AttackTokens.holds(player, zombie) + "/" + dev.forja.combat.AttackTokens.free(player, dev.forja.ai.Aggression.maxAttackers(zombie, player))
				+ " de " + dev.forja.ai.Aggression.maxAttackers(zombie, player) + ", sitiado " + Heights.besieged(player) + ", zombi en " + zombie.position()
				+ " a " + zombie.position().distanceTo(Vec3.atCenterOf(helper.absolutePos(torch))) + " de la antorcha, golpes " + mind.lightTicks
				+ ", objetivo " + mind.lightTarget + ", camino " + zombie.getNavigation().getPath() + ")");
			helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 7)).is(Blocks.LANTERN), "el farol nunca se rompe");
			boolean dropped = !level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(torch)).inflate(3.0), e -> e.getItem().is(Items.TORCH)).isEmpty();
			helper.assertTrue(dropped, "la antorcha rota se suelta para el jugador");
			level.getGameRules().set(GameRules.MOB_GRIEFING, griefing, level.getServer());
			zombie.discard();
			for (Zombie holder : holders) {
				dev.forja.combat.AttackTokens.releaseAll(holder);
				holder.discard();
			}
			clear(helper, 10, 4);
		});
	}

	/**
	 * A dark room: stone floor, walls and roof, from (0..9, 0..3, 0..9) with the inside (1..8, 1..2, 1..8) empty. It
	 * reaches past the test's 8 x 8 box, so its chunks are forced as {@link #floor}'s are (TestChunks): where the box
	 * sat by a chunk's edge, a zombie that went round the far side of the room walked into a chunk that did not tick
	 * entities and stood there frozen, its brain still deciding, for the rest of the test (7 runs of 400).
	 */
	private static void room(GameTestHelper helper) {
		TestChunks.force(helper, 10);
		for (int x = 0; x <= 9; x++) {
			for (int z = 0; z <= 9; z++) {
				for (int y = 0; y <= 3; y++) {
					boolean inside = x >= 1 && x <= 8 && z >= 1 && z <= 8 && y >= 1 && y <= 2;
					helper.setBlock(new BlockPos(x, y, z), inside ? Blocks.AIR : Blocks.STONE);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the v4 network's heads

	/**
	 * A fake v4 network that wants RECOGER and "curarse" above all: RECOGER only once a useful thing lies on the floor
	 * (the mask), and then it walks over and takes the sword; "curarse" only with a potion in its kit.
	 */
	@GameTest(maxTicks = 200)
	public void v4NetworkPicksUpAndDrinks(GameTestHelper helper) {
		floor(helper, 8);
		float[] bias = new float[NetBrain.V4_OUTPUTS];
		bias[NetBrain.V4_TACTICS_AT + Tactic.RECOGER.ordinal() - Tactic.V3_COUNT] = 20.0F;
		bias[NetBrain.OBJECT_AT + MobItems.HEAL] = 20.0F;
		NetBrain net = NetBrain.fromJson(RedV4GameTests.fakeV4(5L, bias));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 7));
		hold(helper, player, player.position());
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		MobMind mind = mind(helper, zombie);
		mind.override = net;
		zombie.setTarget(player);
		zombie.setHealth(10.0F);
		int[] recoger = {0};
		helper.onEachTick(() -> {
			zombie.setTarget(player);
			if (mind.networked && mind.decision.tactic() == Tactic.RECOGER) {
				recoger[0]++;
			}
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(recoger[0] == 0, "sin nada en el suelo RECOGER está enmascarada: " + recoger[0]);
			helper.assertTrue(mind.itemAction == 0 && mind.itemTicks == 0, "sin poción en la mochila no puede beber");
			Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 1, 5)));
			ItemEntity sword = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.IRON_SWORD));
			sword.setDeltaMovement(Vec3.ZERO);
			helper.getLevel().addFreshEntity(sword);
			MobKit.add(zombie, MobKit.Kind.HEAL, "minecraft:healing", 1);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(recoger[0] > 0, "con la espada en el suelo la red elige RECOGER");
			helper.assertTrue(zombie.getMainHandItem().is(Items.IRON_SWORD), "debería coger la espada");
			helper.assertTrue(MobKit.count(zombie, MobKit.Kind.HEAL) == 0 && zombie.getHealth() > 10.0F, "y beberse la poción");
			zombie.discard();
			clear(helper, 8, 3);
		});
	}

	/** Under a v1..v3 network the v4 specials are not there at all (they read as an empty slot); for the rules they are. */
	@GameTest(maxTicks = 20)
	public void v3NetworkNeverSeesTheV4Specials(GameTestHelper helper) {
		floor(helper, 8);
		pillar(helper, 7, 3, 3);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 4, 3));
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 3));
		skeleton.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		helper.runAfterDelay(2, () -> {
			SpecialRunner runner = mind(helper, skeleton).specials;
			runner.hideV4 = true;
			helper.assertFalse(runner.present(2) || runner.available(2, player), "bajo una red v3 la flecha de empuje no existe");
			helper.assertTrue(runner.cooldownLeft(2) == 1.0, "y se lee como un hueco vacío");
			runner.hideV4 = false;
			helper.assertTrue(runner.present(2), "para las reglas y la v4 sí está");
			skeleton.discard();
			clear(helper, 8, 5);
			helper.succeed();
		});
	}

	/** The rules' decision carries v4's heads: a hurt zombie with a potion decides "curarse" (object 1). */
	@GameTest(maxTicks = 20)
	public void rulesDecideTheObjectHead(GameTestHelper helper) {
		floor(helper, 8);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 1));
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		MobKit.add(zombie, MobKit.Kind.HEAL, "minecraft:healing", 1);
		zombie.setHealth(4.0F);
		MobMind mind = mind(helper, zombie);
		mind.target = player;
		int item = MobItems.ruleItem(mind, player, helper.getLevel().getGameTime());
		helper.assertTrue(item == MobItems.HEAL, "herido y fuera de su alcance, las reglas beben: " + item);
		Decision d = Decision.APPROACH.withV4(item, false, false);
		helper.assertTrue(d.item() == MobItems.HEAL && d.tactic() == Tactic.ACERCARSE, "la decisión lleva la cabeza de objeto");
		zombie.discard();
		clear(helper, 8, 3);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the simulator's step v4c (docs/mod_spec_v4c.md)

	/**
	 * v4c, 1: a change of family mid-fight switches networks and starts the network's memory (GRU) afresh, as the
	 * simulator does. A skeleton on the archer's network that takes up a sword runs the body's; its bow back, the archer's
	 * again, each time with a fresh memory. And when the new family has no network (it fights by the rules while it holds
	 * the blade), taking its bow back does not bring back the memory it had before. In a batch of its own (its
	 * environment): the networks it loads are every mob's while they last.
	 */
	@GameTest(environment = "forja-test:familia_red", maxTicks = 120)
	public void aChangeOfFamilySwitchesNetworkAndMemory(GameTestHelper helper) throws java.io.IOException {
		floor(helper, 8);
		CombatConfig cfg = CombatConfig.get();
		String savedFolder = cfg.iaCarpetaRedes;
		String savedContract = cfg.iaContrato;
		String savedMode = cfg.iaModo;
		java.nio.file.Path root = java.nio.file.Files.createTempDirectory("forja_familia");
		java.nio.file.Path v3 = java.nio.file.Files.createDirectories(root.resolve("redes"));
		java.nio.file.Path v4 = java.nio.file.Files.createDirectories(root.resolve("redes_v4"));
		// the archer's network wants ESPERAR, the body's RODEAR: what the skeleton decides says which one ran
		float[] archer = new float[NetBrain.V4_OUTPUTS];
		archer[NetBrain.TACTIC_AT + Tactic.ESPERAR.ordinal()] = 30.0F;
		float[] body = new float[NetBrain.V4_OUTPUTS];
		body[NetBrain.TACTIC_AT + Tactic.RODEAR.ordinal()] = 30.0F;
		com.google.gson.JsonObject archerNet = RedV4GameTests.fakeV4(41L, archer);
		archerNet.addProperty("grupo", "arquero");
		java.nio.file.Files.writeString(v4.resolve("red_arquero.json"), new com.google.gson.Gson().toJson(archerNet));
		java.nio.file.Files.writeString(v4.resolve("red_cuerpo.json"), new com.google.gson.Gson().toJson(RedV4GameTests.fakeV4(42L, body)));
		cfg.iaCarpetaRedes = v3.toAbsolutePath().toString();
		cfg.iaContrato = "auto";
		cfg.iaModo = "auto";
		MobAi.reload();
		helper.assertTrue(MobAi.net("arquero") != null && MobAi.net("cuerpo") != null, "las dos redes se cargan: " + MobAi.problems());
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 4));
		hold(helper, player, player.position());
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 4));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		helper.onEachTick(() -> skeleton.setTarget(player));
		MobMind mind = mind(helper, skeleton);
		float[][] memory = {null};
		Runnable restore = () -> {
			cfg.iaCarpetaRedes = savedFolder;
			cfg.iaContrato = savedContract;
			cfg.iaModo = savedMode;
			MobAi.reload();
		};
		helper.runAfterDelay(10, () -> {
			helper.assertTrue("arquero".equals(mind.family) && mind.networked && mind.decision.tactic() == Tactic.ESPERAR,
				"con el arco, la red del arquero: " + mind.family + " " + mind.decision.tactic());
			memory[0] = mind.memory;
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue("cuerpo".equals(mind.family) && mind.networked && mind.decision.tactic() == Tactic.RODEAR,
				"con la espada, la red del cuerpo: " + mind.family + " " + mind.decision.tactic());
			helper.assertTrue(mind.memory != memory[0], "y su memoria empieza de cero");
			memory[0] = mind.memory;
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue("arquero".equals(mind.family) && mind.decision.tactic() == Tactic.ESPERAR,
				"otra vez con el arco, la del arquero: " + mind.family + " " + mind.decision.tactic());
			helper.assertTrue(mind.memory != memory[0], "otra vez desde cero");
			// no body network now: with the blade it fights by the rules
			try {
				java.nio.file.Files.delete(v4.resolve("red_cuerpo.json"));
			} catch (java.io.IOException failure) {
				throw new RuntimeException(failure);
			}
			MobAi.reload();
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(MobAi.net("cuerpo") == null, "ya no hay red de cuerpo");
			helper.assertTrue("arquero".equals(mind.family) && mind.networked, "sigue con la red del arquero");
			memory[0] = mind.memory;
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		});
		helper.runAfterDelay(50, () -> {
			helper.assertTrue("cuerpo".equals(mind.family) && !mind.networked, "con la espada y sin red de cuerpo, por reglas: " + mind.family);
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		});
		helper.runAfterDelay(60, () -> {
			try {
				helper.assertTrue("arquero".equals(mind.family) && mind.networked, "con el arco, otra vez la red del arquero");
				helper.assertTrue(mind.memory != null && mind.memory != memory[0], "y sin la memoria de antes de la espada");
			} finally {
				restore.run();
			}
			skeleton.discard();
			clear(helper, 8, 3);
			helper.succeed();
		});
	}

	/**
	 * v4c, 2: the spare can be a bow. A skeleton that takes its blade out of the spare (object 8) keeps its bow there, and
	 * takes it back later; a bow it picked up from the floor, carried in the spare, is dropped for sure when it dies (one
	 * it spawned with keeps vanilla's chance).
	 */
	@GameTest(maxTicks = 300)
	public void aBowInTheSpareSlot(GameTestHelper helper) {
		floor(helper, 8);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(7, 1, 4));
		hold(helper, player, player.position());
		float[] bias = new float[NetBrain.V4_OUTPUTS];
		bias[NetBrain.OBJECT_AT + MobItems.SWAP] = 30.0F;
		NetBrain swapper = NetBrain.fromJson(RedV4GameTests.fakeV4(43L, bias));
		// one that spawned with its bow (vanilla's 8.5 % chance to drop it) and a blade in the spare
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 2));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.085F);
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		MobKit.setSpare(skeleton, new ItemStack(Items.IRON_SWORD), false, 0.0F);
		MobMind mind = mind(helper, skeleton);
		mind.override = swapper;
		// one with empty hands that picks a bow up off the floor, and a blade in its spare
		var picker = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 6));
		picker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		picker.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		Vec3 at = picker.position();
		ItemEntity bow = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.BOW));
		bow.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(bow);
		helper.assertTrue(GroundItems.gain(picker, bow.getItem()) > 0.0, "un arco le sirve a un esqueleto sin nada");
		helper.assertTrue(GroundItems.take(picker, bow), "lo coge");
		MobKit.setSpare(picker, new ItemStack(Items.IRON_SWORD), false, 0.0F);
		MobMind pickerMind = mind(helper, picker);
		pickerMind.override = swapper;
		helper.onEachTick(() -> {
			skeleton.setTarget(player);
			picker.setTarget(player);
			skeleton.setHealth(skeleton.getMaxHealth());
			picker.setHealth(picker.getMaxHealth());
		});
		boolean[] drawn = {false};
		boolean[] back = {false};
		helper.onEachTick(() -> {
			if (!drawn[0] && skeleton.getMainHandItem().is(Items.IRON_SWORD)) {
				drawn[0] = true;
				helper.assertTrue(MobKit.spare(skeleton).is(Items.BOW), "saca la hoja y guarda el arco en el repuesto: " + MobKit.spare(skeleton));
				helper.assertTrue(dev.forja.ai.MobFamily.of(skeleton) == dev.forja.ai.MobFamily.CUERPO, "con la hoja, familia cuerpo");
				helper.assertTrue(Math.abs(skeleton.getDropChances().byEquipment(EquipmentSlot.BODY) - 0.085F) < 1.0E-4
					&& !skeleton.getDropChances().isPreserved(EquipmentSlot.BODY), "el arco de aparecer guarda su probabilidad de vanilla");
			}
			if (drawn[0] && !back[0] && skeleton.getMainHandItem().is(Items.BOW)) {
				back[0] = true;
				helper.assertTrue(MobKit.spare(skeleton).is(Items.IRON_SWORD), "y vuelve a sacar el arco: la hoja, al repuesto");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(drawn[0] && back[0], "el arco va al repuesto y vuelve");
			helper.assertTrue(picker.getMainHandItem().is(Items.IRON_SWORD) && MobKit.spare(picker).is(Items.BOW),
				"el que cogió el arco lo guarda al sacar la hoja: " + picker.getMainHandItem() + " / " + MobKit.spare(picker));
			ServerLevel level = helper.getLevel();
			AABB around = picker.getBoundingBox().inflate(3.0);
			level.getEntitiesOfClass(ItemEntity.class, around).forEach(Entity::discard);
			picker.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, around);
			helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.BOW)), "al morir suelta el arco recogido del repuesto: " + drops);
			skeleton.discard();
			clear(helper, 8, 3);
		});
	}

	/**
	 * v4c, 3: only shooters take up bows. A bow is worth something only to a skeleton, a stray or a bogged (a crossbow only
	 * to a pillager or a piglin); never to a zombie or a drowned, not even empty-handed; and vanilla's loot pickup leaves
	 * a bow on the floor for a zombie, while a skeleton takes it.
	 */
	@GameTest(maxTicks = 100)
	public void onlyShootersTakeUpBows(GameTestHelper helper) {
		floor(helper, 8);
		ItemStack bow = new ItemStack(Items.BOW);
		ItemStack crossbow = new ItemStack(Items.CROSSBOW);
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		var drowned = helper.spawn(EntityTypes.DROWNED, new BlockPos(3, 1, 1));
		drowned.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(5, 1, 1));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		var stray = helper.spawn(EntityTypes.STRAY, new BlockPos(7, 1, 1));
		stray.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		stray.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		var pillager = helper.spawn(EntityTypes.PILLAGER, new BlockPos(1, 1, 3));
		pillager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		for (Mob mob : List.<Mob>of(zombie, drowned, skeleton, stray, pillager)) {
			mob.setNoAi(true);
		}
		helper.assertTrue(GroundItems.gain(zombie, bow) <= 0.0 && GroundItems.gain(drowned, bow) <= 0.0,
			"un arco no le vale a un zombi ni a un ahogado, ni con las manos vacías");
		helper.assertTrue(GroundItems.gain(zombie, crossbow) <= 0.0, "ni una ballesta");
		helper.assertTrue(GroundItems.gain(skeleton, bow) > 0.0 && GroundItems.gain(stray, bow) > 0.0, "a un esqueleto y a un stray sí");
		helper.assertTrue(GroundItems.gain(skeleton, crossbow) <= 0.0, "una ballesta no le vale a un esqueleto (la usaría de palo)");
		helper.assertTrue(GroundItems.gain(pillager, crossbow) > 0.0 && GroundItems.gain(pillager, bow) <= 0.0,
			"al saqueador, la ballesta sí y el arco no");
		for (Mob mob : List.<Mob>of(drowned, stray, pillager)) {
			mob.discard();
		}
		// vanilla's loot pickup (called straight: no game rule, no timing): a zombie that can pick up loot leaves the bow
		// on the floor; a skeleton takes it
		Vec3 z = zombie.position();
		ItemEntity forZombie = new ItemEntity(helper.getLevel(), z.x, z.y, z.z, new ItemStack(Items.BOW));
		forZombie.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(forZombie);
		((dev.forja.test.mixin.MobPickupInvoker) zombie).forja$pickUpItem(helper.getLevel(), forZombie);
		helper.assertFalse(zombie.getMainHandItem().is(Items.BOW), "el zombi no coge el arco: " + zombie.getMainHandItem());
		helper.assertTrue(forZombie.isAlive() && forZombie.getItem().is(Items.BOW), "el arco sigue en el suelo");
		Vec3 s = skeleton.position();
		ItemEntity forSkeleton = new ItemEntity(helper.getLevel(), s.x, s.y, s.z, new ItemStack(Items.BOW));
		forSkeleton.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(forSkeleton);
		((dev.forja.test.mixin.MobPickupInvoker) skeleton).forja$pickUpItem(helper.getLevel(), forSkeleton);
		helper.assertTrue(skeleton.getMainHandItem().is(Items.BOW), "el esqueleto sí: " + skeleton.getMainHandItem());
		// and a zombie picks up what it can use all the same (a sword)
		ItemEntity sword = new ItemEntity(helper.getLevel(), z.x, z.y, z.z, new ItemStack(Items.IRON_SWORD));
		sword.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(sword);
		((dev.forja.test.mixin.MobPickupInvoker) zombie).forja$pickUpItem(helper.getLevel(), sword);
		helper.assertTrue(zombie.getMainHandItem().is(Items.IRON_SWORD), "una espada sí la coge: " + zombie.getMainHandItem());
		zombie.discard();
		skeleton.discard();
		clear(helper, 8, 3);
		helper.succeed();
	}
}
