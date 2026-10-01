package dev.forja.test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import dev.forja.combat.Posture;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModEntities;
import dev.forja.test.balance.Analysis;
import dev.forja.test.balance.Build;
import dev.forja.test.balance.Fight;
import dev.forja.test.balance.Probe;
import dev.forja.test.balance.Report;
import dev.forja.test.balance.SmithFight;
import dev.forja.difficulty.Ladder;
import dev.forja.test.balance.Target;
import dev.forja.upgrade.CombatUpgrades;
import dev.forja.upgrade.Frenzy;
import dev.forja.upgrade.Upgrade;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The balance report, measured: every weapon type, material and damage upgrade fought against every mob
 * through the mod's own formulas, written to docs/EQUILIBRIO.md on every ./gradlew runGametest, with a
 * guard on the few things that must never happen. See dev.forja.test.balance.
 *
 * <p>The fight model writes down the order in which the mod does things; the tests below hold that
 * order to the real code: the upgrade handler, Player#attack, vanilla's invulnerability after a hit, the
 * posture bar.
 */
public class BalanceGameTests {
	/**
	 * A test environment of their own (data/forja-test/test_environment/equilibrio.json, which changes
	 * nothing), so these run in a batch apart. In the default batch their mobs, their ten-second tick and the
	 * reshuffled layout were enough to upset the timing of the AI tests that share it.
	 */
	static final String ENVIRONMENT = "forja-test:equilibrio";

	/** Worked out once per run, by whichever test gets there first. */
	private static Report report;

	static synchronized Report report(GameTestHelper helper) {
		if (report == null) {
			long started = System.nanoTime();
			Probe probe = new Probe(helper, Analysis.TYPES);
			try {
				List<Target> targets = probe.measureAll();
				Analysis analysis = new Analysis(helper.getLevel(), helper.getLevel().damageSources().playerAttack(probe.player()), targets);
				analysis.runAll();
				report = new Report(analysis, probe);
				long findings = System.nanoTime();
				report.measureOutliers();
				analysis.timings.put("hallazgos", (System.nanoTime() - findings) / 1.0E9);
				dev.forja.Forja.LOGGER.info("equilibrio: tiempos {}", analysis.timings);
				report.seconds = (System.nanoTime() - started) / 1.0E9;
				report.write();
			} finally {
				probe.cleanUp();
			}
		}
		return report;
	}

	/** Writes docs/EQUILIBRIO.md. */
	@GameTest(environment = ENVIRONMENT, maxTicks = 40)
	public void equilibrio(GameTestHelper helper) {
		Report written = report(helper);
		helper.assertTrue(written.written, "docs/EQUILIBRIO.md no se pudo escribir: " + written.writeError);
		helper.succeed();
	}

	/**
	 * Andy, 2026-10-01: the far forge alloys (docs/ALEACIONES_NETHER_END.md) must not be "better netherite", and none
	 * may sit in more than half of the best weapons. Reads the "Aleaciones de fragua" section of the report.
	 */
	@GameTest(environment = ENVIRONMENT, maxTicks = 40)
	public void aleacionesDeFraguaEnSuSitio(GameTestHelper helper) {
		Report written = report(helper);
		List<String> problems = new java.util.ArrayList<>();
		for (Report.FarAlloy far : written.farAlloys) {
			if (far.betterNetherite()) {
				problems.add(far.material().getSerializedName() + " es netherita mejor");
			}
			if (far.usedIn() * 2 > far.builds()) {
				problems.add(far.material().getSerializedName() + " está en " + far.usedIn() + " de " + far.builds() + " mejores armas");
			}
		}
		helper.assertTrue(!written.farAlloys.isEmpty(), "el informe no mide ninguna aleación de fragua");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Weapon types some other type beats against every mob in every scenario when this guard was written. None. */
	private static final java.util.Set<String> KNOWN_DOMINATED = java.util.Set.of();
	/**
	 * Clear tier faults already in the data when this guard was written ("part:higher<lower"): reported in
	 * EQUILIBRIO.md and to Andy, not failed on. A new one fails the guard.
	 */
	private static final java.util.Set<String> KNOWN_INVERSIONS = java.util.Set.of();

	/**
	 * The guard: fails only on what is clearly wrong and new. A weapon type that another beats against
	 * every mob at 0, 50 and 100 % (2 % or more, everywhere); a plain material of a higher tier worse in every
	 * stat of a part than a plain one of a lower tier; a weapon type that cannot kill a mob it can hurt within
	 * two minutes, bare (a boss aside: herreroEnSuSitio holds him to a fight of minutes).
	 */
	@GameTest(environment = ENVIRONMENT, maxTicks = 40)
	public void equilibrioSinDominados(GameTestHelper helper) {
		Report written = report(helper);
		List<String> problems = new java.util.ArrayList<>();
		written.findings.dominated.forEach((type, by) -> {
			if (!KNOWN_DOMINATED.contains(type.id())) {
				problems.add(type.id() + " dominada en todo por " + by.id());
			}
		});
		for (var inversion : written.findings.inversions) {
			String key = inversion.part().id() + ":" + inversion.higher().getSerializedName() + "<" + inversion.lower().getSerializedName();
			if (!KNOWN_INVERSIONS.contains(key)) {
				problems.add("nivel invertido " + key);
			}
		}
		// Heavy and light handles and bindings are a choice (Andy, 2026-09-29), in any material (2026-09-30): none may
		// beat the plain one of its material at everything, and no mix of material and shape may beat all the others.
		for (String variant : written.variants.dominant) {
			problems.add("variante dominante " + variant);
		}
		for (String combination : written.variants.overall) {
			problems.add("combinación que gana a todas " + combination);
		}
		for (ForgeType type : Analysis.TYPES) {
			var bare = written.analysis.reports.get(type).best.get(Analysis.Scenario.BASE);
			boolean magic = dev.forja.magic.Spellcasting.casts(type);
			for (Target target : written.analysis.targets) {
				var result = bare.ttk.get(target.id);
				if (target.hurtable() && result != null && result.killedShare < 0.999 && magic) {
					// Andy, 2026-09-30: without a magic class mana comes back "lentísimo", so a bar is a few spells
					// and a boss outlasts it. The magic weapons are held to what a Mago with its talents does instead;
					// and a boss outlasting even a Mago's bar with a bare staff is the price of magic, not a fault.
					result = written.analysis.ttk(bare.build, target, Analysis.SEARCH_RUNS, Analysis.SEARCH_RUNS, null,
						Analysis.mago(written.analysis.options, true));
					if (result.killedShare < 0.999 && target.boss) {
						continue;
					}
				}
				// A boss is a fight of minutes on purpose (Andy, 2026-09-30): herreroEnSuSitio holds him to his own window.
				if (target.hurtable() && result != null && result.killedShare < 0.999 && !target.boss) {
					problems.add(type.id() + " sin mejoras no mata a " + target.id + " en " + (Analysis.MAX_TICKS / 20) + " s"
						+ (magic ? " ni con el maná de un Mago" : ""));
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Andy, 2026-09-30: the magic weapons were broken. In every scenario (0, 50 and 100 %), measured on the balance
	 * report's sample (Report.measureMagic):
	 * <ul>
	 *   <li>without a magic class the staff and the tome kill no sooner than the median melee weapon, and over a
	 *       long fight do at most half its damage: magic is for the moment, not the weapon;</li>
	 *   <li>a Mago with its talents kills no sooner than the quickest melee weapon and no later than 1.4 times the
	 *       median (1.6 for the tome, whose area bites everything on the rune and is fought here one mob at a time):
	 *       level with melee, not above it;</li>
	 *   <li>and nothing magic takes a warden or the Herrero Caído apart clearly faster (a tenth) than the quickest melee
	 *       weapon; with Enjambre on the staff it used to take a second.</li>
	 * </ul>
	 */
	@GameTest(environment = ENVIRONMENT, maxTicks = 40)
	public void magiaEnSuSitio(GameTestHelper helper) {
		Report written = report(helper);
		List<String> problems = new java.util.ArrayList<>();
		for (Report.MagicRow row : written.magic) {
			String at = " al " + row.scenario.label;
			for (Object[] weapon : new Object[][] {{"báculo", row.staff, row.staffMago, row.staffSustained}, {"grimorio", row.tome, row.tomeMago, row.tomeSustained}}) {
				String name = (String) weapon[0];
				double plain = (Double) weapon[1];
				double mage = (Double) weapon[2];
				double sustained = (Double) weapon[3];
				if (plain < row.median * MAGIC_PLAIN_FLOOR) {
					problems.add(name + " sin clase" + at + " mata en " + fmt(plain) + " s, antes que la mediana cuerpo a cuerpo (" + fmt(row.median) + " s)");
				}
				if (sustained > row.meleeSustained * MAGIC_PLAIN_SUSTAINED) {
					problems.add(name + " sin clase" + at + " sostiene " + fmt(sustained) + "/s, más de la mitad de la mediana (" + fmt(row.meleeSustained) + "/s)");
				}
				if (mage < row.fastest * MAGIC_MAGE_FLOOR) {
					problems.add(name + " de Mago" + at + " mata en " + fmt(mage) + " s, antes que " + row.fastestMelee.id() + " (" + fmt(row.fastest) + " s)");
				}
				// The tome's area bites everything on the rune; against one mob at a time it is allowed to be slower.
				double ceiling = "grimorio".equals(name) ? MAGIC_TOME_CEILING : MAGIC_MAGE_CEILING;
				if (mage > row.median * ceiling) {
					problems.add(name + " de Mago" + at + " mata en " + fmt(mage) + " s, más de " + ceiling + " veces la mediana (" + fmt(row.median) + " s)");
				}
			}
		}
		double[] melee = null;
		for (var entry : written.bigFoes.entrySet()) {
			if (entry.getKey().contains("cuerpo a cuerpo")) {
				melee = entry.getValue();
			}
		}
		if (melee != null) {
			for (var entry : written.bigFoes.entrySet()) {
				double[] ttk = entry.getValue();
				for (int foe = 0; foe < 2; foe++) {
					if (!Double.isNaN(ttk[foe]) && !Double.isNaN(melee[foe]) && ttk[foe] < melee[foe] * MAGIC_BIG_FLOOR) {
						problems.add(entry.getKey() + " mata " + (foe == 0 ? "al warden" : "al Herrero Caído") + " en " + fmt(ttk[foe])
							+ " s, antes que la más rápida cuerpo a cuerpo (" + fmt(melee[foe]) + " s)");
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Andy, 2026-09-30: "parece que puedes llegar a estar muy fuerte, o el Herrero Caído es muy débil, hazlo más fuerte".
	 * His fight, measured on the real boss (balance/SmithFight), on every level with a fight:
	 * <ul>
	 *   <li>the reference endgame kit (the quickest melee weapon at 100 %, starred, a Guerrero at level 50, starred
	 *       armour), alone, beats him within the level's window (SmithFight.WINDOW: 3 to 5 minutes in Difícil);</li>
	 *   <li>no other kit, alone, beats him in less than SmithFight.FASTEST_SHARE of the window's start;</li>
	 *   <li>two players beat him sooner than one, but not in half the time: he grows with them;</li>
	 *   <li>no blow of his takes half of a geared player's health, whichever stage, nor four fifths of it once the pressure of a
	 *       long fight has worn their armour down: nothing unavoidable kills outright;</li>
	 *   <li>and from Difícil on, a careless geared player standing in front of him dies within SmithFight.CARELESS_MOST
	 *       seconds even in his first stage.</li>
	 * </ul>
	 */
	@GameTest(environment = ENVIRONMENT, maxTicks = 40)
	public void herreroEnSuSitio(GameTestHelper helper) {
		Report written = report(helper);
		SmithFight fight = written.smith;
		List<String> problems = new java.util.ArrayList<>();
		helper.assertTrue(fight != null && !fight.rows.isEmpty(), "la pelea del Herrero no se midió");
		for (Ladder level : SmithFight.LEVELS) {
			String at = " en " + level.name().toLowerCase(java.util.Locale.ROOT);
			double[] window = SmithFight.WINDOW.get(level);
			SmithFight.Row solo = fight.row(level, 1, SmithFight.Kit.ESTRELLA);
			if (solo.seconds < window[0] || solo.seconds > window[1]) {
				problems.add("la referencia sola tarda " + fmt(solo.seconds) + " s" + at + ", fuera de " + fmt(window[0]) + "–" + fmt(window[1]) + " s");
			}
			SmithFight.Row duo = fight.row(level, 2, SmithFight.Kit.ESTRELLA);
			if (duo.seconds >= solo.seconds || duo.seconds < solo.seconds * 0.5) {
				problems.add("dos jugadores tardan " + fmt(duo.seconds) + " s" + at + " frente a " + fmt(solo.seconds) + " s solo");
			}
			for (SmithFight.Kit kit : SmithFight.Kit.values()) {
				SmithFight.Row row = fight.row(level, 1, kit);
				if (row.seconds < window[0] * SmithFight.FASTEST_SHARE) {
					problems.add(kit.name().toLowerCase(java.util.Locale.ROOT) + " lo mata en " + fmt(row.seconds) + " s" + at);
				}
				if (row.worst >= SmithFight.MOST_OF_ONE_HIT) {
					problems.add("un golpe suyo quita el " + fmt(row.worst * 100.0) + " % de la vida a " + kit.name().toLowerCase(java.util.Locale.ROOT)
						+ at + ": " + row.worstMove);
				}
				if (row.worstPressed >= SmithFight.MOST_UNDER_PRESSURE) {
					problems.add("con la presión al máximo, un golpe suyo quita el " + fmt(row.worstPressed * 100.0) + " % de la vida a "
						+ kit.name().toLowerCase(java.util.Locale.ROOT) + at + ": " + row.worstPressedMove);
				}
			}
			if (level.ordinal() >= Ladder.DIFICIL.ordinal() && solo.careless[0] > SmithFight.CARELESS_MOST) {
				problems.add("un jugador equipado que se descuida aguanta " + fmt(solo.careless[0]) + " s en la fase 1" + at);
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The band magiaEnSuSitio holds magic to, against melee. */
	static final double MAGIC_PLAIN_FLOOR = 1.0;
	static final double MAGIC_PLAIN_SUSTAINED = 0.5;
	static final double MAGIC_MAGE_FLOOR = 1.0;
	static final double MAGIC_MAGE_CEILING = 1.4;
	static final double MAGIC_TOME_CEILING = 1.6;
	/** Against the big ones a fully built Mago may come level with the quickest melee weapon, within a tenth. */
	static final double MAGIC_BIG_FLOOR = 0.9;

	private static String fmt(double value) {
		return String.format(java.util.Locale.ROOT, "%.2f", value);
	}

	// ---------------------------------------------------------------- the model, held to the real code

	/** A player that has not swung for a long time: its next blow is at full strength. */
	private static void rested(Player player) {
		try {
			Field ticker = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			ticker.setAccessible(true);
			ticker.setInt(player, 1000);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("attackStrengthTicker", failure);
		}
	}

	/** Puts a stack in a fake player's hand with its attributes, which a player that never ticks would not pick up. */
	private static CombatGameTests.TestPlayer holding(GameTestHelper helper, ItemStack stack, LivingEntity facing) {
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(helper.getLevel());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			var instance = player.getAttribute(attribute);
			if (instance != null) {
				instance.addOrReplacePermanentModifier(modifier);
			}
		});
		player.setPos(facing.getX() - 2.0, facing.getY(), facing.getZ());
		player.lookAt(EntityAnchorArgument.Anchor.EYES, facing.position().add(0.0, facing.getBbHeight() * 0.6, 0.0));
		rested(player);
		return player;
	}

	/** The upgrade damage a foe has already taken this tick; the real handler keeps it per tick, a test needs it clear. */
	private static void clearProcs() {
		try {
			Field procs = CombatUpgrades.class.getDeclaredField("PROCS_THIS_TICK");
			procs.setAccessible(true);
			((Map<?, ?>) procs.get(null)).clear();
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("PROCS_THIS_TICK", failure);
		}
	}

	/**
	 * The damage event runs the upgrade handler with its "extra damage" flag up, so that the damage the
	 * upgrades add does not set the upgrades off again. Called bare, the test hook would let every proc
	 * start another round of procs; this puts the flag up the way the event does.
	 */
	@SuppressWarnings("unchecked")
	private static void asTheDamageEventDoes(Runnable handler) {
		ThreadLocal<Boolean> flag;
		try {
			Field field = CombatUpgrades.class.getDeclaredField("EXTRA_DAMAGE");
			field.setAccessible(true);
			flag = (ThreadLocal<Boolean>) field.get(null);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("EXTRA_DAMAGE", failure);
		}
		flag.set(true);
		try {
			handler.run();
		} finally {
			flag.set(false);
		}
	}

	private static double handle(GameTestHelper helper, Player player, LivingEntity victim, ItemStack weapon, float taken, float health) {
		Probe.heal(victim);
		victim.setHealth(health);
		Posture.endStagger(victim);
		clearProcs();
		float before = victim.getHealth();
		asTheDamageEventDoes(() -> CombatUpgrades.onWeaponHitForTest(helper.getLevel(), player, victim, weapon, taken));
		double lost = before - victim.getHealth();
		Probe.heal(victim);
		Posture.endStagger(victim);
		return lost;
	}

	/**
	 * Three procs in one blow (Ejecución, Matagigantes, Afilado), each softened by the ones before it
	 * (CombatUpgrades.softened): the model takes off exactly what the real handler takes off.
	 */
	@GameTest(environment = ENVIRONMENT, maxTicks = 20)
	public void modelMatchesTheUpgradeHandler(GameTestHelper helper) {
		Probe probe = new Probe(helper, List.of(ForgeType.ESPADA));
		try {
			Target anvil = probe.measure(ModEntities.YUNQUE_ANDANTE);
			var registries = helper.getLevel().registryAccess();
			Build build = new Build(ForgeType.ESPADA, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.MADERA, ForgeMaterial.MADERA),
				Map.of(Upgrade.EJECUCION, 100, Upgrade.MATAGIGANTES, 100), registries);
			CombatGameTests.TestPlayer player = holding(helper, build.stack.copy(), anvil.entity());
			float health = (float) (anvil.maxHealth * 0.2);
			double real = handle(helper, player, anvil.entity(), player.getMainHandItem(), 6.0F, health);
			Fight fight = new Fight(build, anvil, new Fight.Options(), helper.getLevel(), helper.getLevel().damageSources().playerAttack(player));
			double model = fight.extrasOnce(6.0, health, 0, 1L);
			helper.assertTrue(real > 0.5, "el manejador real debería quitar algo: " + real);
			helper.assertTrue(Math.abs(real - model) <= 0.002 * Math.max(1.0, real),
				String.format(java.util.Locale.ROOT, "extras: real %.4f, modelo %.4f", real, model));
			// And the softening is really in play: three procs unsoftened would take noticeably more.
			double[] factors = anvil.melee.get(ForgeType.ESPADA)[0];
			float frenzy = 1.0F + (Upgrade.EJECUCION.frenzyCeiling() - 1.0F) / Frenzy.MAX_HITS;
			double raw = 6.0 * Upgrade.executeBonus(frenzy) + 6.0 * Upgrade.giantBonus(frenzy) + 3.0;
			helper.assertTrue(real < raw * factors[Math.min(Target.BREACH_LEVELS - 1, build.breach)] * 0.95,
				"el ablandado de CombatUpgrades.softened debería notarse: " + real + " contra " + raw * factors[0]);
		} finally {
			probe.cleanUp();
		}
		helper.succeed();
	}

	/** Random procs (Crítico, Tormenta, Lluvia estelar) at a full frenzy: the model's mean is the real handler's mean. */
	@GameTest(environment = ENVIRONMENT, maxTicks = 20)
	public void modelRollsLikeTheUpgradeHandler(GameTestHelper helper) {
		Probe probe = new Probe(helper, List.of(ForgeType.ESPADA));
		try {
			Target anvil = probe.measure(ModEntities.YUNQUE_ANDANTE);
			var registries = helper.getLevel().registryAccess();
			Build build = new Build(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.MADERA),
				Map.of(Upgrade.CRITICO, 100, Upgrade.TORMENTA, 100, Upgrade.LLUVIA_ESTELAR, 100), registries);
			CombatGameTests.TestPlayer player = holding(helper, build.stack.copy(), anvil.entity());
			for (int i = 0; i < Frenzy.MAX_HITS; i++) {
				Frenzy.onHit(player, player.getMainHandItem());
			}
			int samples = 800;
			double sum = 0.0;
			double squares = 0.0;
			for (int i = 0; i < samples; i++) {
				double lost = handle(helper, player, anvil.entity(), player.getMainHandItem(), 6.0F, (float) anvil.maxHealth);
				sum += lost;
				squares += lost * lost;
			}
			double real = sum / samples;
			double spread = Math.sqrt(Math.max(0.0, squares / samples - real * real));
			Fight fight = new Fight(build, anvil, new Fight.Options(), helper.getLevel(), helper.getLevel().damageSources().playerAttack(player));
			double model = 0.0;
			for (int i = 0; i < samples; i++) {
				model += fight.extrasOnce(6.0, anvil.maxHealth, Frenzy.MAX_HITS, i);
			}
			model /= samples;
			double error = 4.0 * spread * Math.sqrt(2.0 / samples);
			helper.assertTrue(real > 0.5, "con tres mejoras al azar algo debería caer: " + real);
			helper.assertTrue(Math.abs(real - model) <= error,
				String.format(java.util.Locale.ROOT, "media de extras: real %.3f, modelo %.3f (margen %.3f)", real, model, error));
		} finally {
			probe.cleanUp();
		}
		helper.succeed();
	}

	/** A real swing through Player#attack takes what the model's first swing takes, for every melee type. */
	@GameTest(environment = ENVIRONMENT, maxTicks = 20)
	public void modelSwingsLikePlayerAttack(GameTestHelper helper) {
		List<ForgeType> melee = Analysis.TYPES.stream().filter(type -> !dev.forja.magic.Spellcasting.casts(type)).toList();
		Probe probe = new Probe(helper, melee);
		StringBuilder failures = new StringBuilder();
		try {
			List<Target> targets = List.of(probe.measure(EntityTypes.HUSK), probe.measure(ModEntities.YUNQUE_ANDANTE));
			var registries = helper.getLevel().registryAccess();
			for (ForgeType type : melee) {
				for (Map<Upgrade, Integer> upgrades : List.<Map<Upgrade, Integer>>of(Map.of(), Map.of(Upgrade.FILO, 100))) {
					List<ForgeMaterial> materials = new java.util.ArrayList<>();
					for (var part : type.slots) {
						materials.add(part.role == dev.forja.part.PartType.Role.HANDLE ? ForgeMaterial.VIDRIACERO : ForgeMaterial.DAMASCO);
					}
					Build build = new Build(type, materials, upgrades, registries);
					for (Target target : targets) {
						// Swung at a fresh one of the kind: the measured one has had its bar filled over and over.
						@SuppressWarnings("unchecked")
						var kind = (net.minecraft.world.entity.EntityType<? extends LivingEntity>) target.entity().getType();
						LivingEntity mob = helper.spawn(kind, new net.minecraft.core.BlockPos(3, 2, 6));
						Probe.strip(mob);
						CombatGameTests.TestPlayer player = holding(helper, build.stack.copy(), mob);
						clearProcs();
						float before = mob.getHealth();
						player.attack(mob);
						double real = before - mob.getHealth();
						mob.discard();
						Fight fight = new Fight(build, target, new Fight.Options(), helper.getLevel(), helper.getLevel().damageSources().playerAttack(player));
						double model = fight.run(new Fight.Policy(1, Fight.Charge.NONE, Fight.Breath.SPAM), 1, 1, false).damage;
						if (Math.abs(real - model) > 0.01 * Math.max(1.0, real)) {
							failures.append(String.format(java.util.Locale.ROOT, "%s en %s: real %.3f, modelo %.3f; ", build, target.id, real, model));
						}
					}
				}
			}
		} finally {
			probe.cleanUp();
		}
		helper.assertTrue(failures.isEmpty(), failures.toString());
		helper.succeed();
	}

	/**
	 * Vanilla's invulnerability after a hit, which the model follows: a second blow ten ticks or less after
	 * the first takes only what it has over the first, and an upgrade proc in between lowers that bar.
	 */
	@GameTest(environment = ENVIRONMENT, maxTicks = 60)
	public void quickSecondBlowIsSwallowed(GameTestHelper helper) {
		LivingEntity plain = helper.spawn(EntityTypes.HUSK, new net.minecraft.core.BlockPos(1, 1, 1));
		LivingEntity sharp = helper.spawn(EntityTypes.HUSK, new net.minecraft.core.BlockPos(4, 1, 4));
		Probe.strip(plain);
		Probe.strip(sharp);
		var registries = helper.getLevel().registryAccess();
		Build iron = new Build(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.MADERA), Map.of(), registries);
		Build damascus = new Build(ForgeType.ESPADA, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.MADERA, ForgeMaterial.MADERA), Map.of(), registries);
		CombatGameTests.TestPlayer a = holding(helper, iron.stack.copy(), plain);
		CombatGameTests.TestPlayer b = holding(helper, damascus.stack.copy(), sharp);
		float[] first = new float[2];
		first[0] = swing(a, plain);
		first[1] = swing(b, sharp);
		helper.runAfterDelay(5, () -> {
			float quickPlain = swing(a, plain);
			float quickSharp = swing(b, sharp);
			helper.assertTrue(first[0] > 1.0F, "el primer golpe debería entrar: " + first[0]);
			helper.assertTrue(quickPlain == 0.0F, "a los 5 ticks, un golpe igual al anterior no debería hacer nada: " + quickPlain);
			helper.assertTrue(quickSharp > 1.0F && quickSharp < first[1],
				"con Afilado el último golpe que cuenta es su extra de 3, así que el segundo entra casi entero: " + quickSharp + " de " + first[1]);
			// Past the combo window too (Player#attack counts combos through the attack callback): a plain full blow.
			helper.runAfterDelay(dev.forja.combat.CombatConfig.get().comboWindowTicks + 2, () -> {
				float late = swing(a, plain);
				helper.assertTrue(Math.abs(late - first[0]) < 0.01F, "pasados 10 ticks el golpe entra entero: " + late + " contra " + first[0]);
				helper.succeed();
			});
		});
	}

	private static float swing(Player player, LivingEntity target) {
		rested(player);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target.position().add(0.0, target.getBbHeight() * 0.6, 0.0));
		target.setRemainingFireTicks(0);
		float before = target.getHealth();
		player.attack(target);
		return before - target.getHealth();
	}

	/** Blows to stagger a mob: the model's posture bar fills on the same blow as the real one. */
	@GameTest(environment = ENVIRONMENT, maxTicks = 20)
	public void modelStaggersLikePosture(GameTestHelper helper) {
		Probe probe = new Probe(helper, List.of(ForgeType.MAZO, ForgeType.ESPADA));
		try {
			int spot = 0;
			for (var type : List.<net.minecraft.world.entity.EntityType<? extends LivingEntity>>of(EntityTypes.HUSK, ModEntities.YUNQUE_ANDANTE)) {
				// Measured on one of its kind; staggered for real on a fresh one, whose bar has never filled.
				Target target = probe.measure(type);
				for (ForgeType weapon : List.of(ForgeType.MAZO, ForgeType.ESPADA)) {
					LivingEntity mob = helper.spawn(type, new net.minecraft.core.BlockPos(1 + spot, 2, 6));
					spot += 2;
					Probe.strip(mob);
					CombatGameTests.TestPlayer player = holding(helper,
						new Build(weapon, dev.forja.forge.Assembler.defaultMaterials(weapon), Map.of(), helper.getLevel().registryAccess()).stack, mob);
					Probe.heal(mob);
					Posture.endStagger(mob);
					int real = 0;
					while (!Posture.isStaggered(mob, helper.getLevel().getGameTime()) && real < 2000) {
						Probe.heal(mob);
						mob.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 3.0F);
						real++;
					}
					mob.discard();
					int model = (int) Math.ceil(target.postureMax / (3.0 * target.posture.get(weapon)) - 1.0E-9);
					helper.assertTrue(real == model, target.id + " con " + weapon.id() + ": aturdido al golpe " + real + ", el modelo dice " + model
						+ " (barra " + target.postureMax + ", postura por punto " + target.posture.get(weapon) + ")");
				}
			}
		} finally {
			probe.cleanUp();
		}
		helper.succeed();
	}
}
