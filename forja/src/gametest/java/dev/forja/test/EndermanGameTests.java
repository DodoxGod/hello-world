package dev.forja.test;

import java.util.List;

import dev.forja.ai.EnderDodge;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.CombatStats;
import dev.forja.combat.Posture;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * "Quiero que el enderman tenga un 34 % de probabilidad de esquivar por teletransportación, pero que tenga cooldown
 * de 7 segundos si hay una teletransportación exitosa" (Andy, 2026-09-28): about a third of the blows it is dealt,
 * it blinks away instead; after a blink that went through it cannot for 140 ticks; boxed in, it takes the blow and
 * rolls again next time; and nothing without an attacker is ever dodged.
 *
 * <p>The endermen here have no AI running (the dodge lives in the damage path, not in any brain). The tests reach
 * past the structure, so they reserve room around it with {@code padding}.
 */
public class EndermanGameTests {
	private static final BlockPos AT = new BlockPos(4, 3, 4);

	/** A floor three blocks thick, wide enough for every spot a blink from {@link #AT} can reach. */
	private static void ground(GameTestHelper helper) {
		for (int x = -6; x <= 14; x++) {
			for (int z = -6; z <= 14; z++) {
				for (int y = 0; y <= 2; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
	}

	private static EnderMan enderman(GameTestHelper helper) {
		CombatGameTests.noRandomThreat();
		EnderMan ender = helper.spawn(EntityTypes.ENDERMAN, AT);
		ender.setNoAi(true);
		ender.getRandom().setSeed(34L);
		EnderDodge.forget(ender);
		return ender;
	}

	/** Back where it stood, whole, with no hurt cooldown and no stagger. */
	private static void fresh(EnderMan ender, Vec3 home) {
		ender.teleportTo(home.x, home.y, home.z);
		ender.setDeltaMovement(Vec3.ZERO);
		ender.setHealth(ender.getMaxHealth());
		ender.invulnerableTime = 0;
		Posture.endStagger(ender);
	}

	private static int blinks(EnderMan ender) {
		return CombatStats.count(ender, CombatStats.TELEPORT_DODGE);
	}

	/**
	 * One blow of 1 from the player, fresh from home. Checks that a blink and a hit are each what they should be,
	 * and returns whether it blinked.
	 */
	private static boolean strike(GameTestHelper helper, EnderMan ender, Player player, Vec3 home) {
		fresh(ender, home);
		ServerLevel level = helper.getLevel();
		int before = blinks(ender);
		boolean hurt = ender.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
		boolean blinked = blinks(ender) > before;
		if (blinked) {
			double moved = Math.sqrt(ender.position().subtract(home).horizontalDistanceSqr());
			helper.assertTrue(!hurt && ender.getHealth() == ender.getMaxHealth(), "una esquiva no debería hacerle daño");
			helper.assertTrue(moved >= 3.99 && moved <= 8.01, "la esquiva debería llevarlo de 4 a 8 bloques: " + moved);
		} else {
			helper.assertTrue(hurt && ender.getHealth() < ender.getMaxHealth(), "sin esquiva, el golpe debería entrar");
		}
		return blinked;
	}

	/** Strikes until it blinks, at most {@code tries} times. */
	private static boolean untilBlink(GameTestHelper helper, EnderMan ender, Player player, Vec3 home, int tries) {
		for (int i = 0; i < tries; i++) {
			if (strike(helper, ender, player, home)) {
				return true;
			}
		}
		return false;
	}

	/** The numbers Andy gave, and about a third of 1000 blows blinked away from (fixed seed). */
	@GameTest(padding = 8)
	public void dodgesAboutAThirdOfBlows(GameTestHelper helper) {
		CombatConfig defaults = new CombatConfig();
		helper.assertTrue(defaults.endermanDodgeChance == 0.34, "probabilidad por defecto: " + defaults.endermanDodgeChance);
		helper.assertTrue(defaults.endermanDodgeCooldownTicks == 140, "enfriamiento por defecto: " + defaults.endermanDodgeCooldownTicks);
		ground(helper);
		EnderMan ender = enderman(helper);
		Player player = CombatGameTests.player(helper, new BlockPos(1, 3, 4));
		Vec3 home = ender.position();
		int trials = 1000;
		int dodged = 0;
		for (int i = 0; i < trials; i++) {
			EnderDodge.forget(ender);
			dodged += strike(helper, ender, player, home) ? 1 : 0;
		}
		double rate = dodged / (double) trials;
		dev.forja.Forja.LOGGER.info("enderman: esquivó {} de {} golpes", dodged, trials);
		helper.assertTrue(Math.abs(rate - CombatConfig.get().endermanDodgeChance) < 0.04,
			"esquivó " + dodged + " de " + trials + " golpes (" + rate + "), debería rondar el 34 %");
		helper.succeed();
	}

	/** After a blink that went through, no dodge for 140 ticks; then it dodges again. */
	@GameTest(padding = 8, maxTicks = 200)
	public void cooldownAfterASuccessfulBlink(GameTestHelper helper) {
		ground(helper);
		EnderMan ender = enderman(helper);
		Player player = CombatGameTests.player(helper, new BlockPos(1, 3, 4));
		Vec3 home = ender.position();
		helper.assertTrue(untilBlink(helper, ender, player, home, 60), "no esquivó ninguno de 60 golpes");
		long blinkedAt = helper.getLevel().getGameTime();
		helper.assertTrue(EnderDodge.cooldownLeft(ender) == 140, "enfriamiento tras esquivar: " + EnderDodge.cooldownLeft(ender));
		for (int i = 0; i < 40; i++) {
			helper.assertFalse(strike(helper, ender, player, home), "esquivó en pleno enfriamiento (golpe " + i + ")");
		}
		helper.runAfterDelay(139, () -> {
			long elapsed = helper.getLevel().getGameTime() - blinkedAt;
			helper.assertTrue(elapsed < 140 && EnderDodge.cooldownLeft(ender) == 140 - elapsed,
				"a los " + elapsed + " ticks le quedan " + EnderDodge.cooldownLeft(ender));
			for (int i = 0; i < 40; i++) {
				helper.assertFalse(strike(helper, ender, player, home), "esquivó a los " + elapsed + " ticks (golpe " + i + ")");
			}
		});
		helper.runAfterDelay(140, () -> {
			long elapsed = helper.getLevel().getGameTime() - blinkedAt;
			helper.assertTrue(elapsed >= 140 && EnderDodge.cooldownLeft(ender) == 0,
				"a los " + elapsed + " ticks le quedan " + EnderDodge.cooldownLeft(ender));
			helper.assertTrue(untilBlink(helper, ender, player, home, 60), "pasado el enfriamiento no volvió a esquivar en 60 golpes");
			helper.succeed();
		});
	}

	/**
	 * Boxed in (a floor, a ceiling two blocks above it, all around as far as a blink goes): the roll comes up, the
	 * teleport finds nowhere to land, and the blow lands; no cooldown, so the next blow rolls again.
	 */
	@GameTest(padding = 8)
	public void boxedInTakesTheBlow(GameTestHelper helper) {
		for (int x = -6; x <= 14; x++) {
			for (int z = -6; z <= 14; z++) {
				for (int y : new int[] {0, 1, 2, 5, 6, 7}) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		// Its own cell is the only one tall enough for it.
		helper.setBlock(AT.above(2), Blocks.AIR);
		EnderMan ender = enderman(helper);
		Player player = CombatGameTests.player(helper, new BlockPos(2, 3, 4));
		Vec3 home = ender.position();
		CombatConfig cfg = CombatConfig.get();
		double chance = cfg.endermanDodgeChance;
		try {
			cfg.endermanDodgeChance = 1.0;
			for (int i = 1; i <= 3; i++) {
				helper.assertFalse(strike(helper, ender, player, home), "encerrado no debería poder esquivar");
				helper.assertTrue(ender.position().distanceTo(home) < 0.01, "no debería haberse movido: " + ender.position());
				helper.assertTrue(CombatStats.count(ender, CombatStats.TELEPORT_DODGE_FAILED) == i,
					"cada golpe debería intentarlo: " + CombatStats.count(ender, CombatStats.TELEPORT_DODGE_FAILED) + " de " + i);
				helper.assertTrue(EnderDodge.cooldownLeft(ender) == 0, "un intento fallido no da enfriamiento");
			}
		} finally {
			cfg.endermanDodgeChance = chance;
		}
		helper.succeed();
	}

	/**
	 * Even with a sure dodge: nothing without an attacker is dodged (a fall, lava, fire, cactus, drowning, magic, the
	 * void, /kill), nor a generic_kill a creative player dealt, nor an arrow (vanilla's own blink, never ours). A blow
	 * from the player, with the same setup, is.
	 */
	@GameTest(padding = 8)
	public void environmentalDamageIsNeverDodged(GameTestHelper helper) {
		ground(helper);
		EnderMan ender = enderman(helper);
		Vec3 home = ender.position();
		ServerLevel level = helper.getLevel();
		var sources = level.damageSources();
		Player creative = helper.makeMockPlayer(GameType.CREATIVE);
		DamageSource kill = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GENERIC_KILL), creative);
		List<DamageSource> environment = List.of(sources.fall(), sources.lava(), sources.inFire(), sources.cactus(), sources.drown(),
			sources.magic(), sources.generic(), sources.fellOutOfWorld(), sources.genericKill(), kill);
		CombatConfig cfg = CombatConfig.get();
		double chance = cfg.endermanDodgeChance;
		try {
			cfg.endermanDodgeChance = 1.0;
			for (DamageSource source : environment) {
				for (int i = 0; i < 5; i++) {
					fresh(ender, home);
					boolean hurt = ender.hurtServer(level, source, 2.0F);
					helper.assertTrue(hurt && ender.getHealth() < ender.getMaxHealth(), source.getMsgId() + " debería hacerle daño");
				}
			}
			Arrow arrow = new Arrow(EntityTypes.ARROW, level);
			fresh(ender, home);
			ender.hurtServer(level, sources.arrow(arrow, creative), 2.0F);
			helper.assertTrue(blinks(ender) == 0 && CombatStats.count(ender, CombatStats.TELEPORT_DODGE_FAILED) == 0
				&& EnderDodge.cooldownLeft(ender) == 0, "ni el entorno ni una flecha deberían pasar por su esquiva");
			Player player = CombatGameTests.player(helper, new BlockPos(1, 3, 4));
			helper.assertTrue(strike(helper, ender, player, home), "con la esquiva segura, el golpe del jugador debería esquivarse");
		} finally {
			cfg.endermanDodgeChance = chance;
		}
		helper.succeed();
	}
}
