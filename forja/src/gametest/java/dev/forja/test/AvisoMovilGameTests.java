package dev.forja.test;

import java.util.function.Consumer;

import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.WindupChase;
import dev.forja.combat.CombatStats;
import dev.forja.combat.Stamina;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * "Aviso en movimiento" (Andy, 2026-09-30: "cuando los mobs preparan un ataque ya no se pueden mover, por lo que es muy fácil
 * esquivarlos"): a monster warning its blow keeps following the player (ai.WindupChase), so walking back is no longer
 * enough on its own; running, a dodge, a shield or a parry still are.
 *
 * <p>Each test puts a zombie three blocks from a player on a flat floor and lets it walk in. When its first warned blow
 * starts (the melee goal's or the executor's), the player does what the test says, from {@link #REACTION} ticks into the
 * warning: quicker than anybody reacts to a warning, so a player who escapes here would escape anywhere. A feint is not
 * the blow being tested: the player then stands again and the zombie's next warning is the one watched.
 */
public class AvisoMovilGameTests {
	/** Vanilla's walk on the ground, blocks a tick (movement speed 0.1). */
	static final double WALK = 0.216;
	/** And its sprint (×1.3). */
	static final double SPRINT = 0.281;
	/** Ticks into the warning the player starts moving. */
	static final int REACTION = 2;
	static final int SIZE = 24;

	/** What the player does, tick by tick into the warning (t = 0 the tick it was seen to start). */
	interface Script {
		void tick(CombatGameTests.TestPlayer player, Zombie zombie, int t, int windup);
	}

	/** How the watched blow went. */
	static final class Outcome {
		boolean ended;
		boolean hit;
		double startDistance;
		double endDistance;
		/** How far the zombie walked during the warning. */
		double walked;
		int windup;
		String path = "";
	}

	/** The player backs straight away from the zombie at {@code speed} from {@link #REACTION} ticks in, facing it. */
	static Script backAway(double speed) {
		return (player, zombie, t, windup) -> {
			if (t >= REACTION) {
				player.setPos(player.getX() + speed, player.getY(), player.getZ());
				player.setDeltaMovement(speed, 0.0, 0.0);
			}
		};
	}

	/**
	 * The scene: floor, player at (12, 1, 12) looking along -x at a zombie walking in from (9, 1, 12). Runs {@code script}
	 * through the first warning that is not a feint, then hands the outcome to {@code check}. {@code executor} puts that
	 * warning on the executor's path (TacticGoal) instead of the melee goal's.
	 */
	static void scene(GameTestHelper helper, Script script, boolean executor, Consumer<Outcome> check) {
		RedV4CapitanGameTests.floor(helper, SIZE);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(12, 1, 12));
		player.setYRot(90.0F);
		player.setYHeadRot(90.0F);
		Zombie zombie = RedV4CapitanGameTests.zombie(helper, new BlockPos(9, 1, 12));
		for (dev.forja.ai.Personality.Trait trait : dev.forja.ai.Personality.Trait.values()) {
			zombie.removeTag(dev.forja.ai.Personality.TRAIT_TAG + trait.name().toLowerCase(java.util.Locale.ROOT));
		}
		zombie.addTag(dev.forja.ai.Personality.TRAIT_TAG + "agresivo");
		// no lunge: the warned blow is what is watched
		MobMind first = MobAi.mind(zombie);
		if (first != null) {
			first.specials = null;
		}
		Outcome out = new Outcome();
		long[] warnedAt = {-1};
		Vec3[] mobAt = {null};
		int[] feints = {0};
		helper.onEachTick(() -> {
			if (out.ended) {
				return;
			}
			long now = helper.getLevel().getGameTime();
			zombie.setTarget(player);
			MobMind mind = MobAi.mind(zombie);
			if (mind == null) {
				return;
			}
			mind.specials = null;
			if (executor && warnedAt[0] < 0) {
				// only the executor warns here: the melee goal waits, and the executor's warning is put in by hand
				mind.nextBlowAt = now + 1000;
				if (zombie.distanceTo(player) <= 1.6 && mind.windup == 0) {
					mind.windupTotal = dev.forja.ai.MobDefense.windup(zombie);
					mind.windup = mind.windupTotal;
				}
			}
			boolean warning = mind.warning || mind.windup > 0;
			if (warnedAt[0] < 0) {
				if (warning) {
					warnedAt[0] = now;
					mobAt[0] = zombie.position();
					out.startDistance = zombie.distanceTo(player);
					out.windup = mind.windup > 0 ? mind.windupTotal : dev.forja.ai.MobDefense.windup(zombie);
					out.path = mind.windup > 0 ? "tactica" : "vanilla";
				} else {
					return;
				}
			}
			if (!warning) {
				// over: a feint is not the blow being tested, the next warning is
				int feinted = CombatStats.count(zombie, CombatStats.FEINT);
				if (feinted > feints[0] && player.getHealth() >= player.getMaxHealth()) {
					feints[0] = feinted;
					warnedAt[0] = -1;
					// the scene again: three blocks off, for the zombie to walk in and warn as it did the first time
					player.setPos(zombie.getX() + 3.0, player.getY(), zombie.getZ());
					player.setDeltaMovement(Vec3.ZERO);
					return;
				}
				out.ended = true;
				out.hit = player.getHealth() < player.getMaxHealth();
				out.endDistance = zombie.distanceTo(player);
				out.walked = Math.hypot(zombie.getX() - mobAt[0].x, zombie.getZ() - mobAt[0].z);
				return;
			}
			script.tick(player, zombie, (int) (now - warnedAt[0]), out.windup);
		});
		helper.runAfterDelay(180, () -> {
			helper.assertTrue(out.ended, "el zombi avisó y acabó un golpe: empezado " + (warnedAt[0] >= 0));
			check.accept(out);
			RedV4CapitanGameTests.clear(helper, SIZE);
			helper.succeed();
		});
	}

	static String told(Outcome out) {
		return String.format(java.util.Locale.ROOT, "camino %s, aviso %d, distancia %.2f -> %.2f, el zombi anduvo %.2f, da %s", out.path, out.windup,
			out.startDistance, out.endDistance, out.walked, out.hit);
	}

	/**
	 * A zombie warning its blow follows a player who walks back from it at full walking speed, closes on them and lands.
	 * Standing still through the warning, as it used to, it fell 1.3 blocks behind and missed.
	 */
	@GameTest(padding = 16, maxTicks = 200)
	public void aWarningZombieFollowsAWalkerAndLands(GameTestHelper helper) {
		scene(helper, backAway(WALK), false, out -> {
			helper.assertTrue(out.walked > 0.5, "el zombi siguió al jugador durante el aviso: " + told(out));
			helper.assertTrue(out.hit, "y el golpe llega aunque el jugador se aleje andando: " + told(out));
		});
	}

	/** The same on the executor's path (TacticGoal: a network's blow, or the rules' under a tactic). */
	@GameTest(padding = 16, maxTicks = 200)
	public void theExecutorsWarningFollowsToo(GameTestHelper helper) {
		scene(helper, backAway(WALK), true, out -> {
			helper.assertTrue("tactica".equals(out.path), "el aviso es del ejecutor: " + told(out));
			helper.assertTrue(out.walked > 0.5, "el zombi siguió al jugador durante el aviso: " + told(out));
			helper.assertTrue(out.hit, "y el golpe llega: " + told(out));
		});
	}

	/** Running away from the same warning, from the same moment, gets out of its reach: the blow misses. */
	@GameTest(padding = 16, maxTicks = 200)
	public void aSprintingPlayerEscapes(GameTestHelper helper) {
		scene(helper, backAway(SPRINT), false, out -> {
			helper.assertTrue(!out.hit, "corriendo se escapa: " + told(out));
			helper.assertTrue(out.endDistance > out.startDistance, "y acaba más lejos que al empezar: " + told(out));
		});
	}

	/**
	 * A dodge to the side timed for the blow (3 ticks before it lands, inside the 6 ticks the dodge gives) is not hit, as
	 * before: the warning is as long as ever and a dodge is still the answer to it. The player walks back first.
	 */
	@GameTest(padding = 16, maxTicks = 200)
	public void aTimedSideDodgeEscapes(GameTestHelper helper) {
		Script walk = backAway(WALK);
		double[] push = {0.0};
		scene(helper, (player, zombie, t, windup) -> {
			walk.tick(player, zombie, t, windup);
			if (t == windup - 3) {
				Stamina.onDodge(player, 0.0F, 1.0F);
				push[0] = 0.75;
			}
			if (push[0] > 0.01) {
				// the dodge's push, as the client moves it: 0.75 and the ground's friction after
				player.setPos(player.getX(), player.getY(), player.getZ() + push[0]);
				push[0] *= 0.546;
			}
		}, false, out -> helper.assertTrue(!out.hit, "la esquiva a tiempo evita el golpe: " + told(out)));
	}

	/**
	 * A forged shield raised as the blow comes still stops it: the zombie followed the player into reach (the blow was in
	 * reach and did nothing) and the player took no damage.
	 */
	@GameTest(padding = 16, maxTicks = 200)
	public void aShieldStillStopsIt(GameTestHelper helper) {
		Script walk = backAway(WALK);
		Zombie[] seen = {null};
		CombatGameTests.TestPlayer[] who = {null};
		scene(helper, (player, zombie, t, windup) -> {
			walk.tick(player, zombie, t, windup);
			seen[0] = zombie;
			who[0] = player;
			if (t == windup - 2) {
				player.setItemInHand(InteractionHand.OFF_HAND, Assembler.create(ForgeType.ESCUDO, Assembler.defaultMaterials(ForgeType.ESCUDO)));
				player.startUsingItem(InteractionHand.OFF_HAND);
			}
		}, false, out -> {
			helper.assertTrue(!out.hit, "el escudo para el golpe: " + told(out));
			helper.assertTrue(out.walked > 0.5, "el zombi lo siguió durante el aviso: " + told(out));
			helper.assertTrue(CombatStats.count(seen[0], CombatStats.WARNED_NO_DAMAGE) >= 1,
				"que llegó a su alcance (lo siguió) y no hizo daño: " + told(out));
			who[0].stopUsingItem();
		});
	}

	/** The rule's numbers, as docs/red_mob_v4_mod_estado.md "Aviso en movimiento" gives them. */
	@GameTest(maxTicks = 20)
	public void theFollowingSpeedByWeight(GameTestHelper helper) {
		Zombie bare = RedV4CapitanGameTests.zombie(helper, new BlockPos(2, 1, 2));
		bare.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
		bare.setNoAi(true);
		helper.assertTrue(Math.abs(dev.forja.combat.Weight.chaseFactor(bare) - 1.0F) < 1.0E-4, "con las manos vacías, toda su velocidad");
		bare.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, Assembler.create(ForgeType.MARTILLO, Assembler.defaultMaterials(ForgeType.MARTILLO)));
		float hammer = dev.forja.combat.Weight.chaseFactor(bare);
		float kg = dev.forja.combat.Weight.carried(bare);
		helper.assertTrue(Math.abs(hammer - Math.max(0.7F, 1.0F - 0.1F * (kg - 1.0F))) < 1.0E-4 && hammer < 0.9F,
			"con un martillo de guerra (" + kg + " kg), menos: " + hammer);
		helper.assertTrue(Math.abs(WindupChase.pressAt(bare, bare) - Math.max(0.7, dev.forja.ai.Reach.landing(bare, bare) - 0.8)) < 1.0E-6,
			"se para a 0,8 dentro de donde llega su golpe");
		bare.discard();
		helper.succeed();
	}
}
