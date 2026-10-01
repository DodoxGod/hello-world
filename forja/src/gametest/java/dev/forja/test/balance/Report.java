package dev.forja.test.balance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.forja.Forja;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.ForjaDifficulty;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Potential;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.upgrade.Frenzy;
import dev.forja.upgrade.Upgrade;

/**
 * Writes docs/EQUILIBRIO.md out of an {@link Analysis} and its {@link Findings}. Every number in it is
 * measured on this run; every verdict is worked out from those numbers, so the page says what the code
 * does today and the day the code changes, it says that instead.
 */
public final class Report {
	/** From this line on EQUILIBRIO.md is written by hand, and a new report keeps it instead of wiping it. */
	public static final String HAND_WRITTEN = "<!-- escrito a mano:";
	public final Analysis analysis;
	public final Probe probe;
	public final Findings findings;
	public double seconds;
	public boolean written;
	public String writeError = "";
	private final StringBuilder out = new StringBuilder();

	public Report(Analysis analysis, Probe probe) {
		this.analysis = analysis;
		this.probe = probe;
		this.findings = new Findings(analysis);
	}

	public void measureOutliers() {
		this.findings.measure();
		this.variants.measure(this.analysis);
		this.measureMagic();
		this.smith = new SmithFight(this.analysis, this.probe);
		this.smith.measure(this.smithMelee, this.smithStaff);
	}

	/** The Herrero Caído's fight (SmithFight), and the weapons it is fought with: the quickest melee and the staff with Enjambre. */
	public SmithFight smith;
	private Build smithMelee;
	private Build smithStaff;

	// ---------------------------------------------------------------- magic against melee (2026-09-30)

	/** One scenario of the magic check: time to kill (s, geometric mean on the sample) and damage a second over 60 s. */
	public static final class MagicRow {
		public Analysis.Scenario scenario;
		public ForgeType fastestMelee;
		public double fastest;
		public double median;
		public double staff;
		public double staffMago;
		public double tome;
		public double tomeMago;
		public double meleeSustained;
		public double staffSustained;
		public double staffMagoSustained;
		public double tomeSustained;
		public double tomeMagoSustained;
	}

	/** Magic against melee in each scenario: what BalanceGameTests.magiaEnSuSitio holds magic to. */
	public final List<MagicRow> magic = new ArrayList<>();
	/** The big ones, a weapon and a class to its seconds to kill {warden, Herrero Caído} (NaN: not within the limit). */
	public final Map<String, double[]> bigFoes = new java.util.LinkedHashMap<>();

	private static double seconds(Fight.Result result) {
		return result == null || result.killedShare < 0.999 ? Double.NaN : result.seconds();
	}

	/**
	 * Andy, 2026-09-30: the magic weapons were broken. For a player without a magic class magic should be a tool
	 * for the moment, not the best weapon; for a Mago with its talents, level with melee but not above it. Each
	 * scenario's best staff and tome (chosen without a class) is fought again as a Mago with every talent that
	 * moves a spell or the bar (Analysis.mago), and set against the melee types' best of the same scenario.
	 */
	private void measureMagic() {
		Analysis an = this.analysis;
		Fight.Options plain = an.options;
		Fight.Options mago = Analysis.mago(plain, true);
		for (Analysis.Scenario scenario : Analysis.Scenario.values()) {
			MagicRow row = new MagicRow();
			row.scenario = scenario;
			Map<ForgeType, Double> melee = new java.util.EnumMap<>(ForgeType.class);
			Map<ForgeType, Double> meleeSustained = new java.util.EnumMap<>(ForgeType.class);
			row.fastest = Double.MAX_VALUE;
			for (ForgeType type : Analysis.TYPES) {
				if (dev.forja.magic.Spellcasting.casts(type)) {
					continue;
				}
				Analysis.Evaluated best = an.reports.get(type).best.get(scenario);
				melee.put(type, best.geo);
				meleeSustained.put(type, best.sustained.dps());
				if (best.geo < row.fastest) {
					row.fastest = best.geo;
					row.fastestMelee = type;
				}
			}
			row.median = this.median(melee);
			row.meleeSustained = this.median(meleeSustained);
			Analysis.Evaluated staff = an.reports.get(ForgeType.BACULO).best.get(scenario);
			Analysis.Evaluated tome = an.reports.get(ForgeType.GRIMORIO).best.get(scenario);
			row.staff = staff.geo;
			row.tome = tome.geo;
			row.staffSustained = staff.sustained.dps();
			row.tomeSustained = tome.sustained.dps();
			row.staffMago = an.geo(staff.build, an.search, Analysis.FINAL_RUNS, mago);
			row.tomeMago = an.geo(tome.build, an.search, Analysis.FINAL_RUNS, mago);
			row.staffMagoSustained = an.dps(staff.build, 1200, 4, null, mago).dps();
			row.tomeMagoSustained = an.dps(tome.build, 1200, 4, null, mago).dps();
			this.magic.add(row);
		}
		// The big ones: a warden and the Herrero Caído, with the best staff at 100 % and Enjambre on it
		// (Prisma and Buscador at 100 %), the best tome at 100 %, and the quickest melee weapon at 100 %.
		Target warden = this.probe.measure(net.minecraft.world.entity.EntityTypes.WARDEN);
		Target smith = an.target("forja:herrero_caido");
		if (warden != null) {
			Forja.LOGGER.info("equilibrio: warden vida {}, tope por golpe {}, por punto de rayo {}", warden.maxHealth, warden.cap,
				java.util.Arrays.toString(warden.bolt));
		}
		Analysis.Evaluated staff = an.reports.get(ForgeType.BACULO).best.get(Analysis.Scenario.MAXIMO);
		Map<Upgrade, Integer> swarm = new java.util.EnumMap<>(Upgrade.class);
		swarm.putAll(staff.build.upgrades);
		swarm.put(Upgrade.PRISMA, 100);
		swarm.put(Upgrade.BUSCADOR, 100);
		Build swarmStaff = an.build(ForgeType.BACULO, staff.build.materials, swarm);
		Build tome = an.reports.get(ForgeType.GRIMORIO).best.get(Analysis.Scenario.MAXIMO).build;
		MagicRow top = this.magic.getLast();
		Build melee = an.reports.get(top.fastestMelee).best.get(Analysis.Scenario.MAXIMO).build;
		java.util.function.BiFunction<Build, Fight.Options, double[]> fight = (build, options) -> new double[] {
			warden == null ? Double.NaN : seconds(an.ttk(build, warden, Analysis.SEARCH_RUNS, 8, null, options)),
			smith == null ? Double.NaN : seconds(an.ttk(build, smith, Analysis.SEARCH_RUNS, 8, null, options))};
		this.smithMelee = melee;
		this.smithStaff = swarmStaff;
		this.bigFoes.put("báculo con Enjambre, sin clase", fight.apply(swarmStaff, plain));
		this.bigFoes.put("báculo con Enjambre, Mago", fight.apply(swarmStaff, mago));
		this.bigFoes.put("grimorio, sin clase", fight.apply(tome, plain));
		this.bigFoes.put("grimorio, Mago", fight.apply(tome, mago));
		this.bigFoes.put(top.fastestMelee.id() + " (la más rápida cuerpo a cuerpo)", fight.apply(melee, plain));
	}

	private void magicSection() {
		CombatConfig cfg = CombatConfig.get();
		this.line("## Magia frente al cuerpo a cuerpo");
		this.line("");
		this.line("Andy, 2026-09-30: la magia estaba rota. Sin clase mágica debe ser un recurso para un momento, no la mejor arma; "
			+ "un Mago con sus talentos, a la altura del cuerpo a cuerpo pero no por encima. *Sin clase*: el maná vuelve a "
			+ f(cfg.manaRegenPerTick * 20, 1) + "/s lanzando y " + f(cfg.manaIdleRegenPerTick * 20, 1) + "/s en calma. *Mago*: la clase "
			+ "y todos los nodos de su árbol grande menos las claves (docs/ARBOLES.md: lo que tiene un Mago en el nivel 50). "
			+ "El báculo y el grimorio son los mejores de cada escenario sin clase. Las pruebas (`magiaEnSuSitio`) exigen que, "
			+ "sin clase, la magia no mate antes que la mediana cuerpo a cuerpo ni sostenga más de la mitad de su daño, y que el Mago "
			+ "quede entre la más rápida cuerpo a cuerpo y 1,4 veces la mediana (1,6 el grimorio, cuyo área muerde a todo lo que pisa la runa "
			+ "y aquí pelea contra un solo mob). Contra los grandes, nada mágico puede matar claramente (un 10 %) antes que la más rápida "
			+ "cuerpo a cuerpo.");
		this.line("");
		this.table("Escenario", "C/c más rápida", "Mediana c/c", "Báculo sin clase", "Báculo Mago", "Grimorio sin clase", "Grimorio Mago");
		for (MagicRow row : this.magic) {
			this.row(row.scenario.label, row.fastestMelee.id() + " " + f(row.fastest, 2), f(row.median, 2),
				f(row.staff, 2) + " (×" + f(row.staff / row.median, 2) + ")", f(row.staffMago, 2) + " (×" + f(row.staffMago / row.median, 2) + ")",
				f(row.tome, 2) + " (×" + f(row.tome / row.median, 2) + ")", f(row.tomeMago, 2) + " (×" + f(row.tomeMago / row.median, 2) + ")");
		}
		this.line("");
		this.line("TTK medio en segundos (entre paréntesis, frente a la mediana cuerpo a cuerpo). Daño por segundo sostenido en 60 s:");
		this.line("");
		this.table("Escenario", "Mediana c/c", "Báculo sin clase", "Báculo Mago", "Grimorio sin clase", "Grimorio Mago");
		for (MagicRow row : this.magic) {
			this.row(row.scenario.label, f(row.meleeSustained, 1), f(row.staffSustained, 1), f(row.staffMagoSustained, 1),
				f(row.tomeSustained, 1), f(row.tomeMagoSustained, 1));
		}
		this.line("");
		this.line("Los grandes, al 100 % (segundos para matar; «> 120» si no cae en dos minutos):");
		this.line("");
		this.table("Arma", "Warden", "Herrero Caído");
		this.bigFoes.forEach((label, ttk) -> this.row(label, Double.isNaN(ttk[0]) ? "> 120" : f(ttk[0], 1),
			Double.isNaN(ttk[1]) ? "> 120" : f(ttk[1], 1)));
		this.line("");
	}

	// ---------------------------------------------------------------- the Herrero Caído (2026-09-30)

	private void smithSection() {
		SmithFight fight = this.smith;
		if (fight == null || fight.rows.isEmpty()) {
			return;
		}
		CombatConfig cfg = CombatConfig.get();
		this.line("## Herrero Caído");
		this.line("");
		this.line("Andy, 2026-09-30: «parece que puedes llegar a estar muy fuerte, o el Herrero Caído es muy débil, hazlo más fuerte». "
			+ "Objetivo: un jugador bien equipado de final de juego, solo, tarda de 3 a 5 minutos de pelea de verdad en Difícil (más en "
			+ "Implacable, menos en Normal y Fácil), y el jefe puede matar a un jugador equipado que se descuida. En Pacífico no hay pelea: "
			+ "un mundo pacífico no guarda ningún monstruo, tampoco a él. Lo mide `SmithFight` y lo exige `BalanceGameTests.herreroEnSuSitio`.");
		this.line("");
		this.line("**Qué se mide en el jefe de verdad** (vestido con su mangual y su placa, del tamaño que le da la pelea por nivel, "
			+ "jugadores y equipo, en cada una de sus tres fases y aturdido), por el mismo camino de daño del juego: lo que le quita un golpe de "
			+ "cada arma, lo que les quitan a sus aprendices y a los yunques andantes, y lo que quita cada golpe suyo (el normal, el revés, la "
			+ "onda, el garfio y las estrellas) a un jugador con la armadura de referencia (placa de obsidiacero sobre cuero, Protección al "
			+ "100 % en las cuatro piezas y Vitalidad en la pechera), recién llegado y con la presión de una pelea larga.");
		this.line("");
		this.line("**Qué es modelo** (los números están en `SmithFight`): el daño por segundo de un jugador contra cada fase es la pelea "
			+ "tick a tick de este informe durante " + (SmithFight.SIM_TICKS / 20) + " s; el tiempo que pasa pegándole es lo que queda tras "
			+ "esquivar cada movimiento suyo cada vez que vuelve (onda " + f(SmithFight.WAVE_DODGE, 1) + " s, revés " + f(SmithFight.STRIKE_DODGE, 1)
			+ " s, estrellas " + f(SmithFight.STAR_DODGE, 1) + " s, garfio desde lejos " + f(SmithFight.HOOK_DODGE, 1) + " s; un "
			+ pct(SmithFight.MELEE_LOSS) + " de moverse y seguirle cuerpo a cuerpo y un " + pct(SmithFight.RANGED_LOSS) + " desde lejos; los "
			+ "eventos del cielo de su lado); las pausas son las de la pelea (levantarse, las dos llamadas de aprendices, el aturdido); el "
			+ "Reforjado cuesta " + f(SmithFight.EMBER_SECONDS, 0) + " s por brasa más la última colada; los aprendices de las dos oleadas "
			+ "(y los guardianes de las brasas) y los seis yunques se matan uno tras otro con la misma arma, pegándoles el "
			+ pct(SmithFight.ADD_UPTIME) + " del tiempo. Con dos jugadores se reparten el daño, los aprendices y las brasas; el que no "
			+ "persigue solo esquiva la onda y el cielo. No cuenta la regeneración, las pociones ni las constelaciones que le hacen daño.");
		this.line("");
		List<String> kits = new ArrayList<>();
		for (SmithFight.Kit kit : SmithFight.Kit.values()) {
			SmithFight.Gear gear = fight.gear.get(kit);
			kits.add("**" + kit.name().toLowerCase(Locale.ROOT) + "**" + (kit == SmithFight.Kit.ESTRELLA ? " (la referencia)" : "") + ": "
				+ kit.label + " (" + gear.weapon.type.id() + ", equipo "
				+ f(gear.score, 2) + ", vida " + f(gear.maxHealth, 0) + ")");
		}
		this.line("Los equipos (todos con herrero de nivel 10, armadura de referencia con Maestría 10, y la puntuación de equipo de "
			+ "`GearScore` que ve el jefe): " + String.join("; ", kits) + ".");
		this.line("");
		this.line("### Sus números ahora");
		this.line("");
		this.line("Vida de base " + f(dev.forja.entity.FallenSmith.HEALTH, 0) + " (antes 320), por el nivel, +"
			+ pct(dev.forja.entity.FallenSmith.HEALTH_PER_PLAYER) + " por cada jugador de más que haya estado en la pelea y +"
			+ pct(dev.forja.entity.FallenSmith.GEAR_HEALTH_PER_TIER) + " y +" + f(dev.forja.entity.FallenSmith.GEAR_ARMOR_PER_TIER, 1)
			+ " de armadura por tramo de equipo (0 a 3) de quienes le pelean, como cualquier monstruo (`Scaling`, que da +"
			+ pct(cfg.gearHealthPerTier) + "). Pasado el techo de vida del juego (" + f(dev.forja.entity.FallenSmith.MOST_HEALTH, 0)
			+ "), lo que sobra se lo quita a cada golpe (`bulk`). Por fase (1 / 2 / 3): armadura +"
			+ join(dev.forja.entity.FallenSmith.STAGE_ARMOR) + ", dureza +" + join(dev.forja.entity.FallenSmith.STAGE_TOUGHNESS)
			+ ", daño de los golpes avisados ×" + join(dev.forja.entity.FallenSmith.STAGE_DAMAGE) + " y esperas ×"
			+ join(dev.forja.entity.FallenSmith.STAGE_COOLDOWN) + " (los avisos no cambian). Furia bajo un tercio: +"
			+ pct(dev.forja.entity.FallenSmith.ENRAGE_SPEED) + " de velocidad y +" + pct(dev.forja.entity.FallenSmith.ENRAGE_DAMAGE)
			+ " a su golpe normal. Segunda oleada de aprendices: +" + pct(dev.forja.entity.FallenSmith.WAVE_HEALTH[1]) + " de vida y +"
			+ f(dev.forja.entity.FallenSmith.SECOND_WAVE_ARMOR, 0) + " de armadura. Golpes avisados de base: revés "
			+ f(dev.forja.entity.FallenSmith.STRIKE_DAMAGE, 0) + " (antes 7), onda " + f(dev.forja.entity.FallenSmith.WAVE_DAMAGE, 0)
			+ " (antes 8), garfio " + f(dev.forja.entity.FallenSmith.HOOK_DAMAGE, 0) + " (antes 4), estrellas "
			+ f(dev.forja.entity.FallenSmith.STARFALL_DAMAGE, 0) + " (antes 9). Encima, el daño de cada nivel a todos los monstruos "
			+ "(`Ladder`) y el de su tramo de equipo (`GearScore.damageFactor`).");
		this.line("");
		this.table("Nivel", "Vida de uno solo sin equipo", "Daño avisado", "Esperas", "Brasas del Reforjado (uno solo)", "Guardianes al reforjarse");
		for (dev.forja.difficulty.Ladder level : SmithFight.LEVELS) {
			dev.forja.entity.FallenSmith.Grade grade = dev.forja.entity.FallenSmith.grade(level);
			// The level's own figures, whatever preset the tests force: what a world on that level gets.
			this.row(level.name().toLowerCase(Locale.ROOT), String.valueOf(Math.round(dev.forja.entity.FallenSmith.HEALTH * grade.health())),
				"×" + f(grade.moveDamage(), 2), "×" + f(grade.cooldown(), 2), String.valueOf(grade.embers()), String.valueOf(grade.keepers()));
		}
		this.line("");
		this.line("### La referencia, sola: antes y ahora");
		this.line("");
		this.table("Nivel", "Objetivo", "Antes", "Ahora", "Vida del jefe antes → ahora", "Descuidado aguanta (fase 1 / 3), antes → ahora",
			"Atento aguanta (fase 1 / 3)", "Golpe más grande: recién llegado / con presión");
		for (dev.forja.difficulty.Ladder level : SmithFight.LEVELS) {
			SmithFight.Row now = fight.row(level, 1, SmithFight.Kit.ESTRELLA);
			double[] window = SmithFight.WINDOW.get(level);
			double[] before = SmithFight.before(level, 1, SmithFight.Kit.ESTRELLA);
			this.row(level.name().toLowerCase(Locale.ROOT), minutes(window[0]) + " – " + minutes(window[1]),
				before == null ? "—" : minutes(before[0]), "**" + minutes(now.seconds) + "**",
				(before == null ? "—" : f(before[1], 0)) + " → " + f(now.health, 0),
				(before == null ? "—" : f(before[2], 0) + " / " + f(before[3], 0)) + " s → " + f(now.careless[0], 0) + " / "
					+ f(now.careless[2], 0) + " s",
				life(now.attentive[0]) + " / " + life(now.attentive[2]),
				pct(now.worst) + " (" + now.worstMove + ") / " + pct(now.worstPressed) + " (" + now.worstPressedMove + ")");
		}
		this.line("");
		this.line("### Tiempo de pelea por equipo (minutos: solo / dos jugadores; entre paréntesis, antes)");
		this.line("");
		List<String> headers = new ArrayList<>(List.of("Equipo"));
		for (dev.forja.difficulty.Ladder level : SmithFight.LEVELS) {
			headers.add(level.name().toLowerCase(Locale.ROOT));
		}
		this.table(headers.toArray(String[]::new));
		for (SmithFight.Kit kit : SmithFight.Kit.values()) {
			List<String> cells = new ArrayList<>(List.of(kit.name().toLowerCase(Locale.ROOT)));
			for (dev.forja.difficulty.Ladder level : SmithFight.LEVELS) {
				SmithFight.Row solo = fight.row(level, 1, kit);
				SmithFight.Row duo = fight.row(level, 2, kit);
				double[] soloBefore = SmithFight.before(level, 1, kit);
				double[] duoBefore = SmithFight.before(level, 2, kit);
				cells.add(minutes(solo.seconds) + " / " + minutes(duo.seconds)
					+ (soloBefore == null || duoBefore == null ? "" : " (" + minutes(soloBefore[0]) + " / " + minutes(duoBefore[0]) + ")"));
			}
			this.row(cells.toArray(String[]::new));
		}
		this.line("");
		this.line("### De qué está hecha la pelea de la referencia, sola");
		this.line("");
		this.line("*Sin parar*: lo que tardaría pegándole sin esquivar nada, sin pausas ni aprendices (la cuenta de antes, contra un "
			+ "maniquí). *Tope*: golpes del jugador que el tope por golpe recorta en la fase 1.");
		this.line("");
		this.table("Nivel", "Vida", "Daño/s por fase (1 / 2 / 3 / aturdido)", "Tiempo pegándole por fase", "Sin parar", "Pegándole", "Pausas",
			"Reforjado", "Aprendices y yunques", "Total", "Tope");
		for (dev.forja.difficulty.Ladder level : SmithFight.LEVELS) {
			SmithFight.Row row = fight.row(level, 1, SmithFight.Kit.ESTRELLA);
			this.row(level.name().toLowerCase(Locale.ROOT), f(row.health, 0),
				f(row.dps[0], 1) + " / " + f(row.dps[1], 1) + " / " + f(row.dps[2], 1) + " / " + f(row.dps[3], 1),
				pct(row.uptime[0]) + " / " + pct(row.uptime[1]) + " / " + pct(row.uptime[2]),
				f(row.raw, 0) + " s", f(row.hitting, 0) + " s", f(row.pauses, 0) + " s", f(row.reforge, 0) + " s", f(row.adds, 0) + " s",
				"**" + f(row.seconds, 0) + " s**", pct(row.capped));
		}
		this.line("");
		this.line("### Lo que aguanta un jugador delante de él, solo");
		this.line("");
		this.line("Segundos hasta morir con su vida entera. *Descuidado*: se queda delante y se lo come todo (su golpe cada "
			+ f(SmithFight.PLAIN_EVERY, 1) + " s, el revés, la onda, el garfio y las estrellas cada vez que vuelven, y "
			+ SmithFight.APPRENTICES_ON_YOU + " aprendices pegándole desde la fase 2), con la presión de una pelea larga. *Atento*: "
			+ "esquiva lo avisado y se come la mitad de sus golpes normales y el garfio (el mago, solo los golpes); ∞ si nada le alcanza. "
			+ "Golpe más grande: lo más que quita un solo golpe suyo, en cualquier fase, a ese jugador de su vida, recién llegado y con "
			+ "la armadura gastada por la presión de una pelea larga (la prueba exige menos del " + pct(SmithFight.MOST_OF_ONE_HIT)
			+ " y del " + pct(SmithFight.MOST_UNDER_PRESSURE) + ").");
		this.line("");
		this.table("Equipo", "Nivel", "Vida del jugador", "Descuidado (fase 1 / 2 / 3)", "Atento (fase 1 / 2 / 3)", "Golpe más grande: recién llegado / con presión");
		for (SmithFight.Kit kit : SmithFight.Kit.values()) {
			for (dev.forja.difficulty.Ladder level : SmithFight.LEVELS) {
				SmithFight.Row row = fight.row(level, 1, kit);
				this.row(kit.name().toLowerCase(Locale.ROOT), level.name().toLowerCase(Locale.ROOT), f(row.playerHealth, 0),
					f(row.careless[0], 0) + " / " + f(row.careless[1], 0) + " / " + f(row.careless[2], 0) + " s",
					life(row.attentive[0]) + " / " + life(row.attentive[1]) + " / " + life(row.attentive[2]),
					pct(row.worst) + " (" + row.worstMove + ") / " + pct(row.worstPressed) + " (" + row.worstPressedMove + ")");
			}
		}
		this.line("");
		SmithFight.Row hard = fight.row(dev.forja.difficulty.Ladder.DIFICIL, 1, SmithFight.Kit.ESTRELLA);
		this.line("El tope por golpe del jefe (`hitCapBoss`) es el " + pct(cfg.hitCapBoss) + " de su vida por golpe normal de un jugador "
			+ "(los remates y los golpes al aturdido lo pasan). Con su vida de ahora recorta el " + pct(hard.capped) + " de los golpes de la "
			+ "referencia en Difícil: no es lo que marca el ritmo de la pelea, sino la red contra un golpe suelto enorme, y se queda como "
			+ "estaba. Un golpe de algo que no es un jugador le sigue haciendo un " + pct(cfg.jefeDanoAjeno) + ", y La forja reclama sigue "
			+ "saltando con " + f(dev.forja.entity.FallenSmith.RECLAIM_HEAVY_DAMAGE, 0) + " de vida intentada por los grandes (lo que era "
			+ "una décima parte de sus 320).");
		this.line("");
	}

	private static String join(double[] values) {
		List<String> parts = new ArrayList<>();
		for (double value : values) {
			parts.add(f(value, value == Math.rint(value) ? 0 : 2));
		}
		return String.join(" / ", parts);
	}

	private static String join(float[] values) {
		double[] wide = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			wide[i] = values[i];
		}
		return join(wide);
	}

	private static String minutes(double seconds) {
		if (Double.isNaN(seconds)) {
			return "—";
		}
		long whole = Math.round(seconds);
		return String.format(Locale.ROOT, "%d:%02d", whole / 60, whole % 60);
	}

	private static String life(double seconds) {
		return Double.isInfinite(seconds) || seconds > 9999 ? "∞" : f(seconds, 0) + " s";
	}


	// ---------------------------------------------------------------- small helpers

	private static String f(double value, int decimals) {
		if (Double.isNaN(value)) {
			return "—";
		}
		return String.format(Locale.ROOT, "%." + decimals + "f", value).replace('.', ',');
	}

	private static String pct(double share) {
		return Double.isNaN(share) ? "—" : String.format(Locale.ROOT, "%.0f %%", share * 100.0);
	}

	/** A change as a signed percentage: 0.8 of the time to kill is "−20 %". */
	private static String change(double ratio) {
		if (Double.isNaN(ratio)) {
			return "—";
		}
		double delta = (ratio - 1.0) * 100.0;
		return (delta >= 0 ? "+" : "−") + String.format(Locale.ROOT, "%.0f %%", Math.abs(delta));
	}

	private static String mob(Target target) {
		String id = target.id.substring(target.id.indexOf(':') + 1);
		return target.forja ? id + " *(Forja)*" : id;
	}

	private static String materials(Build build) {
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < build.materials.size(); i++) {
			parts.add(build.type.slots.get(i).id() + " " + build.materials.get(i).getSerializedName());
		}
		return String.join(" · ", parts);
	}

	private static String upgrades(Build build) {
		if (build.upgrades.isEmpty()) {
			return "—";
		}
		List<String> names = new ArrayList<>();
		build.upgrades.forEach((upgrade, percent) -> names.add(upgrade.id() + (percent < 100 ? " " + percent + " %" : "")));
		return String.join(", ", names);
	}

	private static String upgradeList(List<Upgrade> list) {
		if (list.isEmpty()) {
			return "—";
		}
		List<String> names = new ArrayList<>();
		for (Upgrade upgrade : list) {
			names.add(upgrade.id());
		}
		return String.join(", ", names);
	}

	private String ttk(Analysis.Evaluated evaluated, Target target) {
		if (!target.hurtable()) {
			return "—";
		}
		Fight.Result result = evaluated.ttk.get(target.id);
		if (result == null) {
			return "—";
		}
		if (result.killedShare < 0.999) {
			return "> " + (Analysis.MAX_TICKS / 20);
		}
		return f(result.seconds(), 2);
	}

	private void line(String text) {
		this.out.append(text).append('\n');
	}

	private void table(String... headers) {
		this.line("| " + String.join(" | ", headers) + " |");
		StringBuilder rule = new StringBuilder("|");
		for (int i = 0; i < headers.length; i++) {
			rule.append("---|");
		}
		this.line(rule.toString());
	}

	private void row(String... cells) {
		this.line("| " + String.join(" | ", cells) + " |");
	}

	private double median(Map<ForgeType, Double> values) {
		List<Double> list = new ArrayList<>();
		for (double value : values.values()) {
			if (!Double.isNaN(value)) {
				list.add(value);
			}
		}
		if (list.isEmpty()) {
			return Double.NaN;
		}
		list.sort(Comparator.naturalOrder());
		return list.get(list.size() / 2);
	}

	// ---------------------------------------------------------------- the page

	public void write() {
		Findings fd = this.findings;
		Analysis an = this.analysis;
		CombatConfig cfg = CombatConfig.get();
		this.line("# Equilibrio de Forja, medido");
		this.line("");
		this.line("> Esta página la escribe `./gradlew runGametest` (prueba `BalanceGameTests.equilibrio`, código en "
			+ "`src/gametest/java/dev/forja/test/balance`). No se edita a mano: se regenera sola cada vez que se pasan las pruebas, "
			+ "así que siempre dice lo que hace el código de hoy. Dificultad medida: **" + ForjaDifficulty.current().name() + "**. "
			+ String.format(Locale.ROOT, "Esta vez: %d mobs, %d tipos de arma, %d peleas simuladas.",
				an.targets.size(), Analysis.TYPES.size(), an.evaluations()));
		this.line("");
		this.method();
		this.summary();
		this.magicSection();
		this.smithSection();
		this.suspicions();
		this.newFindings();
		this.bestBuilds();
		this.variantsSection();
		this.ttkTables();
		this.difficulty();
		this.materialsSection();
		this.farAlloysSection();
		this.upgradesSection();
		this.mobs();
		this.outliers();
		this.recommendations();
		try {
			Path root = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().getParent().getParent().getParent();
			Path docs = root.resolve("docs");
			Files.createDirectories(docs);
			// What was written by hand under the marker (the armour section, 2026-09-29) is kept as it was.
			Path file = docs.resolve("EQUILIBRIO.md");
			String kept = "";
			if (Files.exists(file)) {
				String before = Files.readString(file);
				int at = before.indexOf(HAND_WRITTEN);
				if (at >= 0) {
					kept = "\n" + before.substring(at);
				}
			}
			Files.writeString(file, this.out.toString() + kept);
			this.written = true;
			Forja.LOGGER.info("equilibrio: escrito docs/EQUILIBRIO.md ({} peleas, {} s)", an.evaluations(), String.format(Locale.ROOT, "%.1f", this.seconds));
		} catch (java.io.IOException failure) {
			this.writeError = failure.getMessage();
		}
	}

	private void method() {
		CombatConfig cfg = CombatConfig.get();
		this.line("## Cómo se mide");
		this.line("");
		this.line("- **Nada se vuelve a escribir a mano.** Cada arma se forja de verdad (`Assembler`, con sus mejoras escritas como las "
			+ "escribe la forja) y cada número sale del objeto: daño, velocidad, encantamientos ocultos.");
		this.line("- **Cada mob se mide golpeándolo.** Se invoca, se le quita el equipo y la tirada de veterano/élite, se congela y se "
			+ "le dan golpes pequeños con cada tipo de arma (y con Brecha 0..IV y Resonante), con rayos, magia, fuego y marchitez. "
			+ "Lo que pierde de vida por punto de golpe ya incluye su armadura, la penetración del arma, su resistencia al tipo de golpe, "
			+ "la guardia de jefes y élites y el aturdido. El tope por golpe, la barra de postura y lo demás se leen del mob.");
		this.line("- **La pelea se simula tick a tick** con el orden del mod: fuerza del golpe de `Player#attack` (0,2 + 0,8·s²), "
			+ "los 10 ticks de invulnerabilidad de vanilla, estamina (" + f(cfg.attackCost, 0) + " por golpe, vuelve a "
			+ f(cfg.staminaRegenPerTick * 20, 0) + "/s tras " + cfg.staminaRegenDelayTicks + " ticks sin gastar, golpe cansado ×"
			+ f(cfg.tiredDamageMultiplier, 1) + "), combos (3.º golpe ×" + f(cfg.comboFinisherDamage, 1) + "), golpe cargado (×"
			+ f(1 + cfg.chargeDamageBonus, 1) + ", " + f(cfg.chargeStaminaCost + cfg.chargeStaminaPerShare, 0) + " de estamina), postura y aturdido "
			+ "(×" + f(cfg.staggerDamageMultiplier, 2) + ", sin tope), remates (×" + f(cfg.finisherMultiplier, 0) + "), tope por golpe, frenesí "
			+ "y el orden exacto de `CombatUpgrades.onWeaponHit` con su ablandado (`CombatUpgrades.softened`). Fuego, veneno, sangrado "
			+ "y marchitez a los ritmos de vanilla.");
		this.line("- **Las pruebas atan el modelo al código real**: los extras de las mejoras dan lo mismo que el manejador real "
			+ "(al 0,2 %, y en media con tiradas al azar), el primer golpe da lo mismo que `Player#attack` en los diez tipos cuerpo a "
			+ "cuerpo (±1 %), el aturdido cae en el mismo golpe que en `Posture`, y un segundo golpe a los 5 ticks se lo traga la "
			+ "invulnerabilidad como en el juego.");
		this.line("- **Búsqueda**: por cada hueco de cada arma se quitan los materiales dominados (Pareto: cabeza por daño y durabilidad, "
			+ "mango por velocidad y durabilidad, atadura/guarda por durabilidad; sólo se comparan materiales con el mismo rasgo). Lo que "
			+ "queda se combina y se agrupa por lo que cambia en una pelea. Las mejoras se eligen con un **knapsack exacto por programación "
			+ "dinámica** sobre la carga (" + Potential.CAPACITY_FROM + " + 4·puntos de potencial; pesos de `Potential.weight`, un solo miembro "
			+ "de cada grupo exclusivo, sinergias como pareja) y se **comprueba peleando todos los conjuntos legales**.");
		this.line("- **Vara de medir**: la media geométrica del tiempo para matar (TTK) a " + Analysis.SEARCH_TARGETS.size()
			+ " mobs de muestra (" + String.join(", ", Analysis.SEARCH_TARGETS) + "); el jugador elige, contra cada mob, el ritmo que "
			+ "antes lo mata (cada cuántos ticks golpea, si carga, si espera a tener estamina o descansa hasta llenarla).");
		this.line("- **Escenarios**: 0 % = sin mejoras; 50 % = pieza de potencial 50 (mejoras al 50 %, carga "
			+ Potential.capacity(50) + "); 100 % = potencial 100 con fundente (mejoras al 100 %, carga " + Potential.capacity(100)
			+ "). *Con pactos*: además los dos pactos de arma al 100 %, que suben el potencial " + Potential.PER_WEAPON_PACT + " cada uno y pesan " + Potential.WEAPON_PACT_WEIGHT + " en la carga.");
		this.line("- **Supuestos**: el mob está quieto y no se defiende (sin escudo, sin esquiva, sin IA); el jugador sin armadura, "
			+ "no salta (sin críticos de salto), no ataca por la espalda, apunta al centro del mob; el arma nueva (Afilado entero); "
			+ "sin Maestría ni don; de día sin sol directo (los rasgos solar, nocturno y ascua apagados). Contra mobs bajos (arañas, "
			+ "herrumbre, escorias, pavesas) el golpe entra por arriba y cuenta como a la cabeza (×" + f(cfg.headMultiplier, 1)
			+ "): así pasa también en el juego. El núcleo estelar queda fuera: se come los golpes hasta llenarse y se pelea por "
			+ "fases, así que un golpe suelto no dice nada de él. Las mejoras de área (Onda de choque, Segunda cabeza, Filo arrasador, Conductor, "
			+ "Carnicero, Cadena de rayos...) valen 0 aquí: todo es contra un solo mob. Arcos y ballestas no entran.");
		this.line("");
	}

	private void summary() {
		Findings fd = this.findings;
		Analysis an = this.analysis;
		this.line("## Resumen");
		this.line("");
		List<Map.Entry<ForgeType, Double>> ranked = new ArrayList<>();
		for (ForgeType type : Analysis.TYPES) {
			ranked.add(Map.entry(type, an.reports.get(type).best.get(Analysis.Scenario.MAXIMO).geo));
		}
		ranked.sort(Map.Entry.comparingByValue());
		List<String> order = new ArrayList<>();
		for (Map.Entry<ForgeType, Double> entry : ranked) {
			order.add(entry.getKey().id() + " " + f(entry.getValue(), 2) + " s");
		}
		this.line("- **Orden al 100 %** (TTK medio en la muestra, menos es mejor): " + String.join(" < ", order) + ".");
		ForgeType fastest = ranked.getFirst().getKey();
		ForgeType slowest = ranked.getLast().getKey();
		this.line("- La más rápida mata en " + f(ranked.getFirst().getValue(), 2) + " s de media y la más lenta en "
			+ f(ranked.getLast().getValue(), 2) + " s: **" + f(ranked.getLast().getValue() / ranked.getFirst().getValue(), 1)
			+ " veces** más (" + fastest.id() + " contra " + slowest.id() + ").");
		if (!fd.dominated.isEmpty()) {
			List<String> dominated = new ArrayList<>();
			fd.dominated.forEach((type, by) -> dominated.add(type.id() + " (por " + by.id() + ")"));
			this.line("- **Tipos dominados en todo** (otro mata antes a todos los mobs en 0, 50 y 100 %): " + String.join(", ", dominated) + ".");
		} else {
			this.line("- Ningún tipo de arma está dominado en todo: cada uno gana a los demás contra algún mob en algún escenario.");
		}
		this.line("- **Estamina**: en una pelea larga casi todos los golpes son cansados (mediana " + pct(this.median(fd.tiredShare))
			+ " de los golpes de las mejores armas cuerpo a cuerpo al 100 %): se golpea sin parar a ×" + f(CombatConfig.get().tiredDamageMultiplier, 1)
			+ " en vez de esperar.");
		this.line("- **Los extras de las mejoras no tienen el tope de un golpe**: cada uno es un golpe aparte con su propio tope, y son el "
			+ pct(this.median(fd.extraShare)) + " del daño de las mejores armas al 100 % (mediana). Un zombi muere en "
			+ f(this.median(fd.zombieSwings), 1) + " golpes, no en " + (int) Math.ceil(1.0 / CombatConfig.get().hitCapNormal - 1.0E-9) + ".");
		java.util.Map<ForgeMaterial, Integer> used = new java.util.EnumMap<>(ForgeMaterial.class);
		int builds = 0;
		for (Analysis.TypeReport report : an.reports.values()) {
			for (Map<Analysis.Scenario, Analysis.Evaluated> map : List.of(report.best, report.bestWithPacts)) {
				for (Analysis.Evaluated evaluated : map.values()) {
					builds++;
					for (ForgeMaterial material : java.util.EnumSet.copyOf(evaluated.build.materials)) {
						used.merge(material, 1, Integer::sum);
					}
				}
			}
		}
		List<Map.Entry<ForgeMaterial, Integer>> common = new ArrayList<>(used.entrySet());
		common.sort(Map.Entry.<ForgeMaterial, Integer>comparingByValue().reversed());
		List<String> top = new ArrayList<>();
		for (int i = 0; i < Math.min(4, common.size()); i++) {
			top.add(common.get(i).getKey().getSerializedName() + " (" + common.get(i).getValue() + ")");
		}
		this.line("- **Pocos materiales deciden casi todo**: de " + builds + " mejores armas (12 tipos × 0/50/100 % con y sin pactos), "
			+ "los que más aparecen son " + String.join(", ", top) + ". Ver *Hallazgos* (Afilado en cualquier pieza, el mango de vidriacero).");
		this.line("- **Pactos**: al 50 % los dos pactos bajan el TTK medio una mediana de " + change(this.median(this.column(fd.pactGain, 0)))
			+ " (daño, techo y carga a la vez, peso 0).");
		this.line("- Consulta *Las siete sospechas* para los veredictos, *Hallazgos que no estaban en la lista* y *Valores atípicos* para el porqué.");
		this.line("");
	}

	private void suspicions() {
		Findings fd = this.findings;
		Analysis an = this.analysis;
		CombatConfig cfg = CombatConfig.get();
		this.line("## Las siete sospechas, medidas");
		this.line("");
		this.table("#", "Sospecha", "Veredicto", "Lo medido");

		// 1. Stamina.
		List<String> fast = new ArrayList<>();
		List<String> slow = new ArrayList<>();
		int fastSpam = 0;
		int fastCount = 0;
		for (ForgeType type : Analysis.TYPES) {
			if (dev.forja.magic.Spellcasting.casts(type)) {
				continue;
			}
			Build build = an.reports.get(type).best.get(Analysis.Scenario.MAXIMO).build;
			double ratio = fd.staminaRatio.get(type);
			String text = type.id() + " " + pct(ratio);
			if (build.attackSpeed >= 1.0) {
				fast.add(text);
				fastCount++;
				if (!fd.sustainedPolicy.get(type).pace()) {
					fastSpam++;
				}
			} else {
				slow.add(text);
			}
		}
		double medianTired = this.median(fd.tiredShare);
		boolean staminaConfirmed = fastCount > 0 && fastSpam * 2 >= fastCount && medianTired >= 0.4;
		this.row("1", "La estamina apenas frena a las armas rápidas: golpear cansado (×" + f(cfg.tiredDamageMultiplier, 1)
			+ ") sale mejor que esperar", staminaConfirmed ? "**Confirmada**" : "Refutada",
			"En la pelea larga (60 s) el mejor ritmo de " + fastSpam + " de " + fastCount + " armas rápidas no espera a la estamina; "
				+ "mediana de golpes cansados " + pct(medianTired) + ". Daño con estamina / sin estamina: " + String.join(", ", fast)
				+ (slow.isEmpty() ? "" : "; lentas: " + String.join(", ", slow)) + ".");

		// 2. The cap.
		double medianCapped = this.median(fd.cappedShare);
		double medianZombie = this.median(fd.zombieSwings);
		int minimum = (int) Math.ceil(1.0 / cfg.hitCapNormal - 1.0E-9);
		String capVerdict = medianCapped < 0.2 ? "Refutada" : medianZombie >= minimum ? "**Confirmada**" : "**Confirmada a medias**";
		this.row("2", "El tope por golpe (" + pct(cfg.hitCapNormal) + " de la vida) deja a las mejores armas en un mínimo de " + minimum
			+ " golpes contra mobs de 20 de vida, y decide la velocidad", capVerdict,
			"Al 100 %, mediana de golpes que el tope recorta contra los vanilla: " + pct(medianCapped) + ". Pero cada extra de mejora es "
				+ "otro golpe con su propio tope: golpes para matar un zombi, mediana " + f(medianZombie, 1) + " (" + f(this.min(fd.zombieSwings), 1)
				+ "–" + f(this.max(fd.zombieSwings), 1) + "), no " + minimum + "; los extras son el " + pct(this.median(fd.extraShare))
				+ " del daño de las mejores armas al 100 % (mediana).");

		// 3. Pacts.
		int pactsHelp = 0;
		double pactBest = 1.0;
		for (double[] gain : fd.pactGain.values()) {
			if (gain[0] < 0.99) {
				pactsHelp++;
			}
			pactBest = Math.min(pactBest, gain[0]);
		}
		this.row("3", "Los pactos son ganancia pura para el daño y encima dan sitio (+" + Potential.PER_WEAPON_PACT + " de potencial y peso " + Potential.WEAPON_PACT_WEIGHT + " cada uno)",
			pactsHelp * 2 > Analysis.TYPES.size() ? "**Confirmada**" : "Refutada",
			"Al 50 % el potencial pasa de 50 a " + Analysis.potential(Analysis.Scenario.MEDIO, true) + " (carga "
				+ Potential.capacity(50) + " → " + Potential.capacity(Analysis.potential(Analysis.Scenario.MEDIO, true)) + ") y el TTK baja en "
				+ pactsHelp + " de " + Analysis.TYPES.size() + " tipos (hasta " + change(pactBest) + "). Al 100 %: mediana "
				+ change(this.median(this.column(fd.pactGain, 1))) + ".");

		// 4. Frenesí on the mace.
		double[] mace = fd.frenesiSpeed.get(ForgeType.MAZO);
		double[] plainMace = fd.plainMace;
		Double maceGain = fd.frenesiGain.get(ForgeType.MAZO);
		double medianFrenesi = this.median(fd.frenesiGain);
		boolean doubles = plainMace != null && plainMace[1] >= plainMace[0] * 1.8;
		boolean outsized = maceGain != null && maceGain >= medianFrenesi * 1.5;
		this.row("4", "Frenesí suma velocidad plana (+" + f(Upgrade.attackSpeedBoost(1.0F), 1) + "), así que casi dobla el mazo",
			doubles && outsized ? "**Confirmada**" : doubles ? "**Confirmada a medias**" : "Refutada",
			mace == null ? "—" : "Un mazo corriente pasa de " + f(plainMace[0], 2) + " a " + f(plainMace[1], 2) + " golpes/s (×"
				+ f(plainMace[1] / plainMace[0], 2) + "); el mejor mazo, de " + f(mace[0], 2) + " a " + f(mace[1], 2) + " (×" + f(mace[1] / mace[0], 2)
				+ "). Pero su TTK sólo baja " + pct(maceGain == null ? Double.NaN : maceGain) + " con Frenesí sola, frente a una mediana de "
				+ pct(medianFrenesi) + " en todos los tipos: la estamina (golpes cansados) y la invulnerabilidad se comen la cadencia extra.");

		// 5. Frenzy-fed upgrades.
		int beatFilo = 0;
		int compared = 0;
		for (Map<Upgrade, Double> worth : fd.perWeight.values()) {
			Double filo = worth.get(Upgrade.FILO);
			if (filo == null) {
				continue;
			}
			for (Upgrade upgrade : List.of(Upgrade.MATAGIGANTES, Upgrade.EJECUCION, Upgrade.CRITICO)) {
				Double value = worth.get(upgrade);
				if (value != null) {
					compared++;
					if (value > filo) {
						beatFilo++;
					}
				}
			}
		}
		this.row("5", "Con el frenesí lleno, Matagigantes, Ejecución y Crítico rinden mucho para lo que pesan",
			beatFilo * 2 > compared ? "**Confirmada**" : "Refutada",
			"Por punto de carga, esas tres superan a Filo en " + beatFilo + " de " + compared + " casos (tipo × mejora). Sin el frenesí, "
				+ "el TTK medio al 100 % sube una mediana de " + change(this.median(fd.frenzyRatio)) + " (techo del frenesí: ×"
				+ f(Upgrade.EJECUCION.frenzyCeiling(), 1) + " para las de un solo ingrediente).");

		// 6. Event upgrades.
		double medianEvent = this.median(fd.eventGain);
		this.row("6", "Las mejoras de evento no pesan ni tienen techo: poder gratis",
			medianEvent < 0.95 ? "**Confirmada**" : medianEvent < 0.999 ? "**Confirmada a medias** (gratis, pero rinde poco)" : "Refutada",
			"Lluvia estelar al 100 % encima de la mejor al 100 % (peso " + Potential.weight(Upgrade.LLUVIA_ESTELAR) + "): TTK medio "
				+ change(medianEvent) + " de mediana (" + change(this.min(fd.eventGain)) + " a " + change(this.max(fd.eventGain))
				+ "). Carnicero y Conductor sólo cuentan con más enemigos cerca, y aquí hay uno.");

		// 7. SwingStyle and the head.
		List<String> noCharge = new ArrayList<>();
		fd.charges.forEach((type, charges) -> {
			if (!charges) {
				noCharge.add(type.id());
			}
		});
		this.row("7", "SwingStyle es sólo animación; el ×" + f(CombatConfig.get().headMultiplier, 1) + " a la cabeza es igual para todos",
			"**Confirmada a medias**", "SwingStyle no entra en ningún número de daño salvo en *quién puede cargar*: "
				+ (noCharge.isEmpty() ? "hoy cargan todas las armas forjadas" : "no cargan " + String.join(", ", noCharge))
				+ ". Pero no es sólo animación: la IA de los mobs lo lee (`ObsForja`, `RuleBrain`: reaccionan "
				+ "distinto a un tajo, un golpe desde arriba o una estocada), y eso aquí no se mide. Todo a la cabeza: TTK "
				+ change(this.min(fd.headshotRatio)) + " a " + change(this.max(fd.headshotRatio))
				+ " según el tipo, no igual para todos: el tope por golpe y los extras (que no llevan el ×" + f(CombatConfig.get().headMultiplier, 1)
				+ ") se comen parte.");
		this.line("");
	}

	private double min(Map<ForgeType, Double> values) {
		return values.values().stream().filter(v -> !Double.isNaN(v)).mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
	}

	private double max(Map<ForgeType, Double> values) {
		return values.values().stream().filter(v -> !Double.isNaN(v)).mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
	}

	private Map<ForgeType, Double> column(Map<ForgeType, double[]> values, int index) {
		Map<ForgeType, Double> column = new java.util.EnumMap<>(ForgeType.class);
		values.forEach((type, array) -> column.put(type, array[index]));
		return column;
	}

	private void newFindings() {
		Findings fd = this.findings;
		Analysis an = this.analysis;
		this.line("## Hallazgos que no estaban en la lista");
		this.line("");
		// i-frames
		List<String> swallowed = new ArrayList<>();
		fd.iframesRatio.forEach((type, ratio) -> swallowed.add(type.id() + " " + pct(ratio)));
		this.line("- **La invulnerabilidad de vanilla (10 ticks) recorta los golpes rápidos, y los extras la esquivan.** Un golpe a menos de "
			+ "10 ticks del anterior sólo quita lo que tenga *por encima* del último daño (medido con golpes reales: a los 5 ticks, un golpe "
			+ "igual no hace nada). Pero cada extra de mejora pone `invulnerableTime = 0` y deja como \"último daño\" el suyo, pequeño, así "
			+ "que el siguiente golpe rápido entra casi entero (medido: con una hoja de damasco, el segundo golpe a los 5 ticks entra). "
			+ "Daño en 60 s con / sin esa regla: "
			+ String.join(", ", swallowed) + ".");
		// The cap and the extras
		List<String> shares = new ArrayList<>();
		fd.extraShare.forEach((type, share) -> shares.add(type.id() + " " + pct(share)));
		this.line("- **El tope por golpe no alcanza a los extras.** `CombatHooks.capped` corta cada llamada a `hurtServer`; cada extra de "
			+ "mejora es otra llamada, con su propio tope, así que un golpe con extras puede quitar más del " + pct(CombatConfig.get().hitCapNormal)
			+ " de la vida (con Ráfaga y Cien manos los guanteletes matan a un esqueleto de un puñetazo). Parte del daño que viene de "
			+ "extras en las mejores armas al 100 %: " + String.join(", ", shares) + ".");
		// Afilado
		List<String> afilado = new ArrayList<>();
		fd.afiladoRatio.forEach((type, ratio) -> {
			if (!Double.isNaN(ratio)) {
				afilado.add(type.id() + " " + change(ratio));
			}
		});
		if (!afilado.isEmpty()) {
			this.line("- **Afilado (+3 por golpe mientras el arma está nueva) vale en cualquier pieza**, también en una atadura o una "
				+ "guarda, y se suma a cada golpe sin mirar la velocidad. Cambiar las piezas con Afilado de la mejor al 100 % por netherita "
				+ "sube el TTK: " + String.join(", ", afilado) + ".");
		}
		List<String> glass = new ArrayList<>();
		int withGlass = 0;
		for (ForgeType type : Analysis.TYPES) {
			for (Analysis.Evaluated evaluated : an.reports.get(type).best.values()) {
				if (evaluated.build.hasTrait(ForgeMaterial.Trait.DIAFANO)) {
					withGlass++;
				}
			}
			Double ratio = fd.vidriaceroRatio.get(type);
			if (ratio != null && !Double.isNaN(ratio)) {
				glass.add(type.id() + " " + change(ratio));
			}
		}
		this.line("- **El mango de vidriacero está en " + withGlass + " de " + (Analysis.TYPES.size() * 3) + " mejores armas.** Su velocidad "
			+ "de mango (+0,30) más la de su rasgo Diáfano (+0,3 al atributo) le dan el doble que cualquier otro mango. Cambiarlo por acero "
			+ "estelar (el mejor mango sin rasgo): " + (glass.isEmpty() ? "—" : String.join(", ", glass)) + ".");
		// Mestizaje
		if (!fd.mestizaje.isEmpty()) {
			double[] sword = fd.mestizaje.getOrDefault(ForgeType.ESPADA, fd.mestizaje.values().iterator().next());
			this.line("- **El Mestizaje se aplica al atributo real.** Una espada de damasco con mango de vidriacero "
				+ "y guarda de eco recibe " + pct(sword[0]) + " por mezclar rasgos: la ficha enseña " + f(sword[1], 2)
				+ " de daño y el atributo da " + f(sword[2], 2) + ".");
		}
		// Magic
		Double staff = an.reports.get(ForgeType.BACULO).best.get(Analysis.Scenario.MAXIMO).geo;
		Double tome = an.reports.get(ForgeType.GRIMORIO).best.get(Analysis.Scenario.MAXIMO).geo;
		Target witch = an.target("minecraft:witch");
		Target enderman = an.target("minecraft:enderman");
		Target anvil = an.target("forja:yunque_andante");
		this.line("- **La magia no gasta estamina ni se cansa**, no le afecta la invulnerabilidad (cada proyectil y cada mordisco de runa "
			+ "la ponen a 0) y pasa por encima de la armadura (daño mágico: el yunque andante, con 10 de armadura, pierde "
			+ (anvil == null ? "—" : f(anvil.bolt[0], 2)) + " por punto de rayo del báculo y " + (anvil == null ? "—" : this.factor(anvil, ForgeType.ESPADA))
			+ " por punto de espada). TTK medio al 100 %: báculo " + f(staff, 2) + " s, grimorio " + f(tome, 2)
			+ " s, frente a una mediana cuerpo a cuerpo de " + f(this.meleeMedian(), 2) + " s."
			+ (witch == null ? "" : " La bruja sólo recibe el " + pct(witch.bolt[0]) + " de la magia.")
			+ (enderman == null ? "" : enderman.bolt[0] > 0.5
				? " El enderman no se teletransporta ante el rayo del báculo como ante las flechas; lo esquiva como un golpe "
					+ "(34 %, luego 7 s sin esquivar), y eso el modelo no lo cuenta."
				: " El enderman esquiva el rayo del báculo."));
		this.line("");
	}

	private double meleeMedian() {
		Map<ForgeType, Double> melee = new java.util.EnumMap<>(ForgeType.class);
		for (ForgeType type : Analysis.TYPES) {
			if (!dev.forja.magic.Spellcasting.casts(type)) {
				melee.put(type, this.analysis.reports.get(type).best.get(Analysis.Scenario.MAXIMO).geo);
			}
		}
		return this.median(melee);
	}

	private void bestBuilds() {
		Analysis an = this.analysis;
		this.line("## Mejor conjunto por tipo de arma");
		this.line("");
		this.line("Ráfaga: daño en los 3 primeros segundos con la estamina llena. Sostenido: daño por segundo en 60 s. Los dos contra un "
			+ "maniquí neutro (sin armadura, sin tope, sin resistencias, barra de postura de un mob de 20 de vida): lo que el arma pone. "
			+ "TTK medio: media geométrica de los segundos para matar a los mobs de muestra. Cansados / en invulnerabilidad: parte de los golpes "
			+ "de la pelea larga dados sin estamina / que cayeron dentro de los 10 ticks de invulnerabilidad y sólo quitaron lo que "
			+ "superaba al último daño.");
		this.line("");
		this.table("Tipo", "Escenario", "Materiales", "Mejoras", "Ráfaga (daño/s)", "Sostenido (daño/s)", "TTK medio (s)", "Ritmo sostenido",
			"Cansados", "En invulnerabilidad");
		for (ForgeType type : Analysis.TYPES) {
			Analysis.TypeReport report = an.reports.get(type);
			for (Analysis.Scenario scenario : Analysis.Scenario.values()) {
				this.buildRow(type, scenario.label, report.best.get(scenario));
				Analysis.Evaluated pacts = report.bestWithPacts.get(scenario);
				if (pacts != null) {
					this.buildRow(type, scenario.label + " con pactos", pacts);
				}
			}
		}
		this.line("");
		this.line("**Las tres mejores combinaciones de materiales sin mejoras** (TTK medio en la muestra):");
		this.line("");
		for (ForgeType type : Analysis.TYPES) {
			Analysis.TypeReport report = an.reports.get(type);
			List<String> top = new ArrayList<>();
			for (int i = 0; i < Math.min(3, report.ranking.size()); i++) {
				Analysis.Evaluated evaluated = report.ranking.get(i);
				top.add(materials(evaluated.build) + " (" + f(evaluated.geo, 2) + " s)");
			}
			this.line("- **" + type.id() + "** (" + report.combinations + " combinaciones tras podar cada hueco, " + report.distinct
				+ " distintas en pelea, " + report.ranking.size() + " en el frente): " + String.join("; ", top) + ".");
		}
		this.line("");
		this.line("**Mejor arma de cada tipo** (100 %, sin pactos):");
		this.line("");
		for (ForgeType type : Analysis.TYPES) {
			Analysis.Evaluated best = an.reports.get(type).best.get(Analysis.Scenario.MAXIMO);
			this.line("- **" + type.id() + "**: " + materials(best.build) + "; " + upgrades(best.build) + " — TTK medio "
				+ f(best.geo, 2) + " s, " + f(best.sustained.dps(), 1) + " daño/s sostenido. *No se mide aquí:* " + unmeasured(type) + ".");
		}
		this.line("");
	}

	/** Heavy and light handles and bindings against the plain ones (combat/Grip, test/balance/Variants). */
	public final Variants variants = new Variants();

	private void variantsSection() {
		CombatConfig cfg = CombatConfig.get();
		this.line("## Mangos y ataduras: normal, pesado y ligero, en cualquier material");
		this.line("");
		this.line("Cabeza de hierro y nada más, sin mejoras; el mango y la atadura del material de la fila, en las tres formas: normal, "
			+ "pesada (mango con contrapeso y atadura remachada) y ligera (mango fino y atadura delgada); la espada no lleva atadura, "
			+ "sólo cambia su mango. Cada pieza pesa la densidad de su material por su forma (mango ×"
			+ f(dev.forja.combat.Grip.HEAVY_HANDLE_SHAPE, 2) + " pesado, ×" + f(dev.forja.combat.Grip.LIGHT_HANDLE_SHAPE, 2)
			+ " ligero; atadura ×" + f(dev.forja.combat.Grip.HEAVY_BINDING_SHAPE, 2) + " y ×" + f(dev.forja.combat.Grip.LIGHT_BINDING_SHAPE, 2)
			+ "), y el trato de la forma va encima. Cada celda: normal / pesado / ligero. Ráfaga, sostenido y TTK medio como arriba "
			+ "(el TTK, sobre los mobs de la búsqueda). Aturdidos/min: veces que la barra de postura del maniquí (un mob de 20 de vida) "
			+ "se llena en la pelea larga. Estamina por golpe: la de un golpe normal (" + f(cfg.attackCost, 0) + " de base). Lo que el "
			+ "maniquí no mide y también cuenta: la atadura pesada abarata los bloqueos y no suelta la carga con un golpe de escudo, y con "
			+ "la ligera la guardia rota tarda más en volver.");
		this.line("");
		this.table("Tipo", "Material", "Peso (kg)", "Golpes/s", "Durabilidad", "Ráfaga (daño/s)", "Sostenido (daño/s)", "TTK medio (s)",
			"Estamina por golpe", "Aturdidos/min");
		for (ForgeType type : Variants.TABLE_TYPES) {
			for (dev.forja.material.ForgeMaterial material : Variants.MATERIALS) {
				List<Variants.Row> three = new ArrayList<>();
				for (Variants.Setup setup : Variants.threeShapes(type)) {
					three.add(this.variants.row(type, material, setup));
				}
				this.row(type.id(), material.getSerializedName(),
					triple(three, r -> f(r.kg(), 2)), triple(three, r -> f(r.build().attackSpeed, 2)),
					triple(three, r -> String.valueOf(r.build().durability)), triple(three, r -> f(r.burst(), 1)),
					triple(three, r -> f(r.sustained(), 1)), triple(three, r -> f(r.ttk(), 2)),
					triple(three, r -> f(r.staminaPerSwing(), 1)), triple(three, r -> f(r.staggersPerMinute(), 1)));
			}
		}
		this.line("");
		int measured = this.variants.rows.size();
		this.line("La prueba mide " + measured + " combinaciones: " + Variants.TYPES.size() + " tipos (también la lanza), "
			+ Variants.MATERIALS.size() + " materiales y las " + Variants.SETUPS.size() + " formas de mango y atadura (mango pesado o ligero "
			+ "solo, atadura pesada o ligera sola, todo pesado y todo ligero).");
		this.line("");
		this.line(this.variants.dominant.isEmpty()
			? "**Ninguna variante domina:** ninguna gana a la normal de su tipo y su material en ráfaga, sostenido y TTK a la vez sin "
				+ "pagarlo en estamina, postura (aturdidos o equilibrio quitado por segundo) o durabilidad."
			: "**Dominan** (ganan en todo a la normal del mismo material): " + String.join(", ", this.variants.dominant) + ".");
		this.line("");
		this.line(this.variants.overall.isEmpty()
			? "**Ninguna combinación gana a todas:** ninguna mezcla de material y forma es mejor que todas las demás de su tipo en todo a la vez."
			: "**Gana a todas** las demás de su tipo: " + String.join(", ", this.variants.overall) + ".");
		this.line("");
	}

	/** "a / b / c": one number for each of the three shapes. */
	private static String triple(List<Variants.Row> rows, java.util.function.Function<Variants.Row, String> cell) {
		List<String> cells = new ArrayList<>();
		for (Variants.Row row : rows) {
			cells.add(row == null ? "—" : cell.apply(row));
		}
		return String.join(" / ", cells);
	}

	private void buildRow(ForgeType type, String scenario, Analysis.Evaluated e) {
		double swings = Math.max(1.0E-9, e.sustained.swings);
		this.row(type.id(), scenario, materials(e.build), upgrades(e.build), f(e.burst.damage / 3.0, 1), f(e.sustained.dps(), 1),
			f(e.geo, 2), e.sustainedPolicy.describe(), pct(e.sustained.tired / swings), pct((e.sustained.iframed + e.sustained.swallowed) / swings));
	}

	private void ttkTables() {
		Analysis an = this.analysis;
		for (Analysis.Scenario scenario : List.of(Analysis.Scenario.MAXIMO, Analysis.Scenario.MEDIO, Analysis.Scenario.BASE)) {
			this.line("## Tiempo para matar (s), mejores armas al " + scenario.label);
			this.line("");
			this.line("En negrita el tipo más rápido contra ese mob. \"—\": no se le puede hacer daño así (sellos, absorción). \"> "
				+ (Analysis.MAX_TICKS / 20) + "\": no muere en dos minutos.");
			this.line("");
			List<String> headers = new ArrayList<>();
			headers.add("Mob");
			for (ForgeType type : Analysis.TYPES) {
				headers.add(type.id());
			}
			this.table(headers.toArray(String[]::new));
			for (Target target : an.targets) {
				List<String> cells = new ArrayList<>();
				cells.add(mob(target));
				double best = Double.MAX_VALUE;
				for (ForgeType type : Analysis.TYPES) {
					Fight.Result result = an.reports.get(type).best.get(scenario).ttk.get(target.id);
					if (result != null && result.killedShare >= 0.999) {
						best = Math.min(best, result.ticks);
					}
				}
				for (ForgeType type : Analysis.TYPES) {
					Analysis.Evaluated evaluated = an.reports.get(type).best.get(scenario);
					String cell = this.ttk(evaluated, target);
					Fight.Result result = evaluated.ttk.get(target.id);
					if (result != null && result.killedShare >= 0.999 && Math.abs(result.ticks - best) < 1.0E-6) {
						cell = "**" + cell + "**";
					}
					cells.add(cell);
				}
				this.row(cells.toArray(String[]::new));
			}
			this.line("");
		}
		this.line("### Comprobación: pelea simulada contra vida ÷ daño sostenido");
		this.line("");
		this.line("Para cada pelea de 2 s o más al 100 %, el TTK simulado dividido por la cuenta simple (vida máxima entre el daño por "
			+ "segundo sostenido contra ese mismo mob, con el mismo ritmo). Cerca de 1: la pelea es su daño sostenido. Por debajo: la "
			+ "pelea mata antes de lo que dice su daño sostenido (los primeros golpes van con la estamina llena, un aturdido temprano, "
			+ "Ejecución por debajo del 30 %). Por encima: pausas que la cuenta no ve (la finta de la coraza, esperar a la estamina). "
			+ "Las peleas más cortas no entran: con 2 o 3 golpes el primero cae en el segundo 0 y la cuenta no tiene sentido.");
		this.line("");
		this.table("Tipo", "Peleas", "Mediana", "Rango", "La más lejos de 1");
		for (ForgeType type : Analysis.TYPES) {
			double[] check = this.findings.crossCheck.get(type);
			if (check == null) {
				this.row(type.id(), "0", "—", "—", "—");
			} else {
				this.row(type.id(), String.valueOf((int) check[3]), "×" + f(check[0], 2), "×" + f(check[1], 2) + " – ×" + f(check[2], 2),
					this.findings.crossWorst.get(type));
			}
		}
		this.line("");
	}

	private void difficulty() {
		Findings fd = this.findings;
		this.line("## Dificultad: factores sobre " + ForjaDifficulty.current().name());
		this.line("");
		this.line("Cada preset multiplica la vida de los monstruos, el tope por golpe y la barra de postura (`ForjaDifficulty`). Aquí, el TTK "
			+ "medio de la mejor arma al 100 % contra **todos** los mobs bajo cada preset, dividido por el de " + ForjaDifficulty.current().name() + ".");
		this.line("");
		List<String> headers = new ArrayList<>();
		headers.add("Tipo");
		headers.add("TTK medio " + ForjaDifficulty.current().name() + " (s)");
		for (ForjaDifficulty preset : ForjaDifficulty.values()) {
			headers.add(preset.name() + " (vida ×" + f(preset.health, 1) + ", tope ×" + f(preset.cap, 2) + ")");
		}
		this.table(headers.toArray(String[]::new));
		for (ForgeType type : Analysis.TYPES) {
			List<String> cells = new ArrayList<>();
			cells.add(type.id());
			cells.add(f(fd.allMobsGeo.get(type), 2));
			for (double factor : fd.difficulty.get(type)) {
				cells.add("×" + f(factor, 2));
			}
			this.row(cells.toArray(String[]::new));
		}
		this.line("");
	}

	private void materialsSection() {
		Findings fd = this.findings;
		this.line("## Materiales");
		this.line("");
		this.line("### Dominados por hueco (Pareto, con el mismo rasgo)");
		this.line("");
		this.line("Un material dominado es igual o peor en todo lo que ese hueco usa que otro con el mismo rasgo: en ese hueco, nunca "
			+ "hay razón para elegirlo (fuera de coste y disponibilidad, que aquí no se miden).");
		this.line("");
		this.table("Pieza", "Sobreviven", "Dominados (→ por cuál)");
		fd.fronts.forEach((part, front) -> {
			List<String> kept = new ArrayList<>();
			for (ForgeMaterial material : front.kept()) {
				kept.add(material.getSerializedName());
			}
			List<String> beaten = new ArrayList<>();
			front.dominatedBy().forEach((loser, winner) -> beaten.add(loser.getSerializedName() + " → " + winner.getSerializedName()));
			this.row(part.id(), String.join(", ", kept), beaten.isEmpty() ? "—" : String.join(", ", beaten));
		});
		this.line("");
		List<String> useless = new ArrayList<>();
		for (ForgeMaterial material : fd.uselessForWeapons) {
			useless.add(material.getSerializedName());
		}
		this.line("**Dominados en todos los huecos de arma en que caben** (para armas no hay razón para usarlos): "
			+ (useless.isEmpty() ? "ninguno." : String.join(", ", useless) + "."));
		this.line("");
		List<String> never = new ArrayList<>();
		for (ForgeMaterial material : fd.neverTop) {
			never.add(material.getSerializedName());
		}
		this.line("**Nunca en ninguna de las 5 mejores combinaciones de ningún tipo de arma, ni en ninguna mejor arma:** "
			+ (never.isEmpty() ? "ninguno." : String.join(", ", never) + ".") + " (Para armas; muchos son materiales de armadura, "
			+ "herramienta o principio de partida.)");
		this.line("");
		this.line("### Un material de más nivel peor en todo que uno de menos");
		this.line("");
		this.line("Nivel = lo que puede minar (`incorrectBlocksForDrops`): madera y oro 0, piedra 1, cobre 1,5, hierro 2, diamante 3, "
			+ "netherita 4. Se compara cada pieza en todas las líneas de su ficha (`ForgeStats.partLines`).");
		this.line("");
		this.line("**Fallos claros** (los dos materiales sin rasgo y de los que pueden ser cabeza, en una cabeza o una placa, que es donde "
			+ "el nivel importa, y con dos líneas de ficha o más; esto es lo que vigila la prueba `equilibrioSinDominados`): "
			+ (fd.inversions.isEmpty() ? "ninguno." : ""));
		if (!fd.inversions.isEmpty()) {
			this.line("");
			this.table("Pieza", "Nivel más alto", "peor en todo que");
			for (Materials.Inversion inversion : fd.inversions) {
				this.row(inversion.part().id(), inversion.higher().getSerializedName() + " (" + f(Materials.tier(inversion.higher()), 1) + ")",
					inversion.lower().getSerializedName() + " (" + f(Materials.tier(inversion.lower()), 1) + ")");
			}
		}
		this.line("");
		Map<String, List<String>> byPair = new java.util.LinkedHashMap<>();
		for (Materials.Inversion inversion : fd.weaponInversions) {
			byPair.computeIfAbsent(inversion.higher().getSerializedName() + " (" + f(Materials.tier(inversion.higher()), 1) + ") peor que "
				+ inversion.lower().getSerializedName() + " (" + f(Materials.tier(inversion.lower()), 1) + ")", k -> new ArrayList<>()).add(inversion.part().id());
		}
		this.line("**En piezas de arma, contando rasgos y mangos** (el rasgo puede ser el precio y un mango es cosa de peso; no son fallos "
			+ "por sí solos, pero merecen una mirada): "
			+ (byPair.isEmpty() ? "ninguno." : ""));
		this.line("");
		byPair.forEach((pair, parts) -> this.line("- " + pair + ": " + String.join(", ", parts)));
		this.line("");
	}

	/** One far forge alloy (docs/ALEACIONES_NETHER_END.md) next to netherite, for the section and the guard. */
	public record FarAlloy(ForgeMaterial material, String place, int usedIn, int builds, boolean betterNetherite) {
	}

	/** The far forge alloys as measured this run; BalanceGameTests.aleacionesDeFraguaEnSuSitio reads it. */
	public final List<FarAlloy> farAlloys = new ArrayList<>();

	/** A full set of plates of this material: the armour of its four pieces. */
	private static int setArmor(ForgeMaterial material) {
		int total = 0;
		for (net.minecraft.world.item.equipment.ArmorType type : List.of(net.minecraft.world.item.equipment.ArmorType.HELMET,
			net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, net.minecraft.world.item.equipment.ArmorType.LEGGINGS,
			net.minecraft.world.item.equipment.ArmorType.BOOTS)) {
			total += material.defense(type);
		}
		return total;
	}

	/**
	 * The far forge alloys (Andy, 2026-10-01): two from the Nether's soul forge and one from the End's void forge, each
	 * only made at its own forge. None may be "netherite but better" (as good or better in head damage, durability, set
	 * armour and toughness at once), and none may decide the best weapons the way damascus and glass steel do.
	 */
	private void farAlloysSection() {
		Analysis an = this.analysis;
		this.line("## Aleaciones de fragua frente a la netherita");
		this.line("");
		this.line("Las aleaciones que solo funde una fragua lejana (docs/ALEACIONES_NETHER_END.md). *¿Netherita mejor?*: igual o mejor que "
			+ "la netherita a la vez en daño de cabeza, durabilidad, armadura del conjunto y dureza (la prueba "
			+ "`aleacionesDeFraguaEnSuSitio` lo prohíbe, y que estén en más de la mitad de las mejores armas). *En las mejores armas*: "
			+ "en cuántas de las mejores armas de cada tipo y escenario, con y sin pactos, entra alguna pieza suya.");
		this.line("");
		int builds = 0;
		java.util.Map<ForgeMaterial, Integer> used = new java.util.EnumMap<>(ForgeMaterial.class);
		for (Analysis.TypeReport report : an.reports.values()) {
			for (Map<Analysis.Scenario, Analysis.Evaluated> map : List.of(report.best, report.bestWithPacts)) {
				for (Analysis.Evaluated evaluated : map.values()) {
					builds++;
					for (ForgeMaterial material : java.util.EnumSet.copyOf(evaluated.build.materials)) {
						used.merge(material, 1, Integer::sum);
					}
				}
			}
		}
		ForgeMaterial netherite = ForgeMaterial.NETHERITA;
		this.table("Material", "Fragua", "Rasgo", "Daño de cabeza", "Durabilidad", "Armadura del conjunto", "Dureza", "Ataque de mango",
			"En las mejores armas", "¿Netherita mejor?");
		this.row("netherita (referencia)", "—", "—", f(netherite.attackDamageBonus, 1), String.valueOf(netherite.durability),
			String.valueOf(setArmor(netherite)), f(netherite.toughness, 1), f(netherite.handleAttackSpeed, 2),
			used.getOrDefault(netherite, 0) + " de " + builds, "—");
		this.farAlloys.clear();
		for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.forge.Alloys.ALL) {
			if (dev.forja.forge.Alloys.anywhere(recipe)) {
				continue;
			}
			ForgeMaterial material = ForgeMaterial.fromInput(recipe.result());
			if (material == null) {
				continue;
			}
			boolean better = material.attackDamageBonus >= netherite.attackDamageBonus && material.durability >= netherite.durability
				&& setArmor(material) >= setArmor(netherite) && material.toughness >= netherite.toughness;
			FarAlloy far = new FarAlloy(material, dev.forja.forge.Alloys.place(recipe).id(), used.getOrDefault(material, 0), builds, better);
			this.farAlloys.add(far);
			this.row(material.getSerializedName(), far.place(), material.trait.id(), f(material.attackDamageBonus, 1),
				String.valueOf(material.durability), String.valueOf(setArmor(material)), f(material.toughness, 1),
				f(material.handleAttackSpeed, 2), far.usedIn() + " de " + builds, better ? "**sí**" : "no");
		}
		this.line("");
	}

	private void upgradesSection() {
		Analysis an = this.analysis;
		Findings fd = this.findings;
		this.line("## Mejoras de daño");
		this.line("");
		this.line("### Lo que vale cada una sola, al 100 %, sobre la mejor combinación de materiales");
		this.line("");
		this.line("Cambio del TTK medio al añadirla sola (negativo es mejor), y entre paréntesis por punto de carga. \"·\" = no se puede poner "
			+ "en ese tipo.");
		this.line("");
		List<String> headers = new ArrayList<>();
		headers.add("Mejora (peso)");
		for (ForgeType type : Analysis.TYPES) {
			headers.add(type.id());
		}
		this.table(headers.toArray(String[]::new));
		for (Upgrade upgrade : Analysis.DAMAGE_UPGRADES) {
			boolean any = false;
			List<String> cells = new ArrayList<>();
			cells.add(upgrade.id() + " (" + Potential.weight(upgrade) + ")");
			for (ForgeType type : Analysis.TYPES) {
				Double value = an.reports.get(type).knapsack.get(Analysis.Scenario.MAXIMO).single.get(upgrade);
				if (value == null) {
					cells.add("·");
				} else {
					any = true;
					double ratio = Math.exp(-value);
					cells.add(change(ratio) + " (" + change(1.0 - (1.0 - ratio) / Potential.weight(upgrade)) + ")");
				}
			}
			if (any) {
				this.row(cells.toArray(String[]::new));
			}
		}
		this.line("");
		this.line("### Programación dinámica contra todos los conjuntos peleados");
		this.line("");
		this.line("La programación dinámica es exacta para valores que se suman; en una pelea no se suman (el tope, la invulnerabilidad y "
			+ "el ablandado de los extras cortan), así que se pelean todos los conjuntos legales y se queda el mejor.");
		this.line("");
		this.table("Tipo", "Escenario", "Carga", "Conjuntos", "Elige la PD", "TTK (s)", "Mejor peleado", "TTK (s)", "Diferencia");
		int agree = 0;
		int total = 0;
		for (ForgeType type : Analysis.TYPES) {
			Analysis.TypeReport report = an.reports.get(type);
			for (boolean pacts : new boolean[] {false, true}) {
				for (Map.Entry<Analysis.Scenario, Analysis.Knapsack> entry : (pacts ? report.knapsackPacts : report.knapsack).entrySet()) {
					Analysis.Knapsack k = entry.getValue();
					total++;
					if (k.dpGeo <= k.enumGeo * 1.005) {
						agree++;
					}
					this.row(type.id(), entry.getKey().label + (pacts ? " con pactos" : ""), String.valueOf(k.capacity), String.valueOf(k.subsets),
						upgradeList(k.dpPick), f(k.dpGeo, 2), upgradeList(k.enumPick), f(k.enumGeo, 2), change(k.dpGeo / k.enumGeo));
				}
			}
		}
		this.line("");
		this.line("La PD acierta (a menos de un 0,5 %) en " + agree + " de " + total + " casos.");
		this.line("");
		// Chosen and never chosen.
		java.util.Map<Upgrade, Integer> chosen = new java.util.EnumMap<>(Upgrade.class);
		for (Analysis.TypeReport report : an.reports.values()) {
			for (Map<Analysis.Scenario, Analysis.Evaluated> map : List.of(report.best, report.bestWithPacts)) {
				for (Analysis.Evaluated evaluated : map.values()) {
					for (Upgrade upgrade : evaluated.build.upgrades.keySet()) {
						chosen.merge(upgrade, 1, Integer::sum);
					}
				}
			}
		}
		List<String> used = new ArrayList<>();
		List<String> unused = new ArrayList<>();
		for (Upgrade upgrade : Analysis.DAMAGE_UPGRADES) {
			boolean applies = false;
			for (ForgeType type : Analysis.TYPES) {
				applies |= upgrade.appliesTo(type);
			}
			if (!applies) {
				continue;
			}
			Integer count = chosen.get(upgrade);
			if (count == null) {
				unused.add(upgrade.id());
			} else {
				used.add(upgrade.id() + " ×" + count);
			}
		}
		this.line("**Veces que entra en una mejor arma** (" + (Analysis.TYPES.size() * 4) + " armas con mejoras): " + String.join(", ", used) + ".");
		this.line("");
		this.line("**Mejoras de daño que no entran en ninguna mejor arma:** " + (unused.isEmpty() ? "ninguna." : String.join(", ", unused) + ".")
			+ " Contra un solo mob quieto nunca compensan lo que pesan. Densidad sólo pega cayendo; Sobrecarga es un hechizo de cada "
			+ Upgrade.OVERCHARGE_EVERY + "; el daño en el tiempo (Veneno, Aspecto ígneo) apenas llega antes de que el mob muera y la "
			+ "invulnerabilidad se traga sus puntos sueltos.");
		this.line("");
	}

	private void mobs() {
		Analysis an = this.analysis;
		this.line("## Los mobs, medidos");
		this.line("");
		this.line("Vida y tope por golpe en " + ForjaDifficulty.current().name() + ", sin veteranos ni élites (un veterano es ×"
			+ f(dev.forja.difficulty.Threat.VETERANO.health, 1) + " de vida y tope " + pct(CombatConfig.get().hitCapVeteran)
			+ "; un élite ×" + f(dev.forja.difficulty.Threat.ELITE.health, 1) + ", tope " + pct(CombatConfig.get().hitCapElite)
			+ " y guardia). Factor: vida que pierde por punto de golpe, de pie / aturdido (espada, mazo, lanza; sin Brecha).");
		this.line("");
		this.table("Mob", "Vida", "Tope por golpe", "Barra de postura", "Espada", "Mazo", "Lanza", "Báculo", "Notas");
		for (Target target : an.targets) {
			this.row(mob(target), f(target.maxHealth, 0), f(target.cap, 1), f(target.postureMax, 0),
				this.factor(target, ForgeType.ESPADA), this.factor(target, ForgeType.MAZO), this.factor(target, ForgeType.LANZA),
				f(target.bolt[0], 2), (target.excluded ? "fuera de las tablas: " : "") + target.special);
		}
		this.line("");
	}

	private String factor(Target target, ForgeType type) {
		double[][] factors = target.melee.get(type);
		return factors == null ? "—" : f(factors[0][0], 2) + " / " + f(factors[1][0], 2);
	}

	private void outliers() {
		Analysis an = this.analysis;
		Findings fd = this.findings;
		this.line("## Valores atípicos y el porqué");
		this.line("");
		int n = 1;
		// Fastest and slowest type.
		List<Map.Entry<ForgeType, Double>> ranked = new ArrayList<>();
		for (ForgeType type : Analysis.TYPES) {
			ranked.add(Map.entry(type, an.reports.get(type).best.get(Analysis.Scenario.MAXIMO).geo));
		}
		ranked.sort(Map.Entry.comparingByValue());
		ForgeType top = ranked.getFirst().getKey();
		ForgeType bottom = ranked.getLast().getKey();
		Analysis.Evaluated topBest = an.reports.get(top).best.get(Analysis.Scenario.MAXIMO);
		Analysis.Evaluated bottomBest = an.reports.get(bottom).best.get(Analysis.Scenario.MAXIMO);
		this.line(n++ + ". **" + top.id() + " es la más rápida al 100 % (" + f(topBest.geo, 2) + " s).** Porqué: " + this.why(topBest) + ".");
		this.line(n++ + ". **" + bottom.id() + " es la más lenta al 100 % (" + f(bottomBest.geo, 2) + " s).** Porqué: " + this.why(bottomBest) + ".");
		// Upgrade with the best value per weight point.
		String bestUpgrade = "";
		double bestValue = 0.0;
		for (ForgeType type : Analysis.TYPES) {
			for (Map.Entry<Upgrade, Double> entry : fd.perWeight.get(type).entrySet()) {
				if (entry.getValue() > bestValue) {
					bestValue = entry.getValue();
					bestUpgrade = entry.getKey().id() + " en " + type.id();
				}
			}
		}
		if (!bestUpgrade.isEmpty()) {
			this.line(n++ + ". **La mejora que más rinde por punto de carga: " + bestUpgrade + "** (" + pct(bestValue) + " menos de TTK por "
				+ "punto). Porqué: el frenesí la multiplica por su techo (×" + f(Upgrade.MATAGIGANTES.frenzyCeiling(), 1) + " las de un solo "
				+ "ingrediente) en cuanto se encadenan " + Frenzy.MAX_HITS + " golpes, y pesa poco.");
		}
		// Pacts.
		double pactBest = 1.0;
		ForgeType pactType = null;
		for (Map.Entry<ForgeType, double[]> entry : fd.pactGain.entrySet()) {
			if (entry.getValue()[0] < pactBest) {
				pactBest = entry.getValue()[0];
				pactType = entry.getKey();
			}
		}
		// A type far ahead of the rest against one mob.
		for (Target target : an.targets) {
			if (!target.hurtable()) {
				continue;
			}
			List<double[]> times = new ArrayList<>();
			for (ForgeType type : Analysis.TYPES) {
				Fight.Result result = an.reports.get(type).best.get(Analysis.Scenario.MAXIMO).ttk.get(target.id);
				if (result != null && result.killedShare >= 0.999) {
					times.add(new double[] {result.seconds(), type.ordinal()});
				}
			}
			if (times.size() < 3) {
				continue;
			}
			times.sort(Comparator.comparingDouble(t -> t[0]));
			double median = times.get(times.size() / 2)[0];
			double[] first = times.getFirst();
			if (median >= 2.0 && first[0] * 2.0 <= median) {
				ForgeType winner = ForgeType.values()[(int) first[1]];
				this.line(n++ + ". **Contra " + mob(target) + ", " + winner.id() + " mata en " + f(first[0], 1) + " s y la mediana de los tipos en "
					+ f(median, 1) + " s.** Porqué: " + this.whyAgainst(winner, target) + ".");
			}
		}
		if (pactType != null) {
			this.line(n++ + ". **Los pactos en " + pactType.id() + " al 50 %: TTK " + change(pactBest) + ".** Porqué: +"
				+ f(Upgrade.thirstDamage(1.0F) * 100, 0) + " % y +" + f(Upgrade.glassDamage(1.0F) * 100, 0) + " % de daño al atributo, peso "
				+ Potential.WEAPON_PACT_WEIGHT + " cada uno, "
				+ "sin techo, y +" + Analysis.bothPactsPotential() + " de potencial que sube el techo de las demás de 50 a "
				+ Analysis.potential(Analysis.Scenario.MEDIO, true) + " % y la carga de " + Potential.capacity(50) + " a "
				+ Potential.capacity(Analysis.potential(Analysis.Scenario.MEDIO, true)) + ". Lo que cuestan (hambre, durabilidad) no es daño.");
		}
		this.line("");
	}

	/** Why one type does so well against one mob: what a point of its blow is worth there against the others'. */
	private String whyAgainst(ForgeType winner, Target target) {
		List<String> reasons = new ArrayList<>();
		if (dev.forja.magic.Spellcasting.casts(winner)) {
			double[] spell = winner == ForgeType.BACULO ? target.bolt : target.area;
			reasons.add("cada punto de su hechizo le quita " + f(spell[0], 2) + " (magia: sin armadura, sin estamina)");
		} else {
			double[][] factors = target.melee.get(winner);
			if (factors != null) {
				reasons.add("cada punto de su golpe le quita " + f(factors[0][0], 2));
			}
		}
		List<Double> others = new ArrayList<>();
		for (ForgeType type : Analysis.TYPES) {
			double[][] factors = target.melee.get(type);
			if (type != winner && factors != null && !dev.forja.magic.Spellcasting.casts(type)) {
				others.add(factors[0][0]);
			}
		}
		if (!others.isEmpty()) {
			others.sort(Comparator.naturalOrder());
			reasons.add("el de las armas cuerpo a cuerpo, " + f(others.getFirst(), 2) + " a " + f(others.getLast(), 2));
		}
		if (target.boss) {
			reasons.add("es jefe: guardia al " + pct(CombatConfig.get().guardHealthShare) + " y tope del " + pct(CombatConfig.get().hitCapBoss)
				+ " por golpe, que los extras y la cadencia esquivan mejor");
		}
		reasons.add(this.why(this.analysis.reports.get(winner).best.get(Analysis.Scenario.MAXIMO)));
		return String.join("; ", reasons);
	}

	/** What a type is for that a fight against one standing mob does not see. */
	static String unmeasured(ForgeType type) {
		return switch (type) {
			case LANZA -> "la carga a la carrera o a caballo (arma cinética) y el alcance";
			case MAZO -> "el golpe cayendo (Densidad, Estallido de viento)";
			case TRIDENTE -> "lanzarlo (Retorno, Corriente, Canalización) y el alcance";
			case MANGUAL -> "el área (Segunda cabeza, Martillo pilón), el aturdimiento y el alcance de su cadena";
			case GUADANA -> "el barrido, el alcance y la cosecha";
			case ESPADA, ESPADON -> "el barrido (Filo arrasador) y la guardia con parada";
			case DAGA -> "lanzar la hoja (Lanzacabezas) y la guardia con parada";
			case HACHA -> "romper escudos y talar";
			case GUANTELETES -> "el combo de Nudillos y la Maestría más rápida";
			case BACULO -> "el abanico de Prisma y los proyectiles que buscan (Buscador)";
			case GRIMORIO -> "el área entera de la runa (todo lo que pisa), Vórtice y Santuario";
			default -> "";
		};
	}

	/** A short reason for how a build fares: the mechanics its fights leaned on, measured. */
	private String why(Analysis.Evaluated e) {
		List<String> reasons = new ArrayList<>();
		double swings = Math.max(1.0E-9, e.sustained.swings);
		boolean magic = dev.forja.magic.Spellcasting.casts(e.build.type);
		if (!magic) {
			reasons.add(f(e.build.attackDamage, 1) + " de daño a " + f(e.build.attackSpeed, 2) + " golpes/s");
			reasons.add("ritmo " + e.sustainedPolicy.describe());
		}
		if (e.sustained.tired / swings > 0.3) {
			reasons.add(pct(e.sustained.tired / swings) + " de los golpes cansado");
		}
		if (e.sustained.swallowed / swings > 0.1) {
			reasons.add(pct(e.sustained.swallowed / swings) + " de los golpes tragados por la invulnerabilidad");
		}
		double capped = 0.0;
		double hits = 0.0;
		double staggers = 0.0;
		for (Fight.Result result : e.ttk.values()) {
			capped += result.capped;
			hits += result.swings;
			staggers += result.staggers;
		}
		if (hits > 0 && capped / hits > 0.15) {
			reasons.add(pct(capped / hits) + " de los golpes recortados por el tope");
		}
		reasons.add(f(staggers / Math.max(1, e.ttk.size()), 1) + " aturdidos por pelea");
		if (e.build.hasTrait(ForgeMaterial.Trait.AFILADO)) {
			reasons.add("Afilado +3 en cada golpe");
		}
		if (magic) {
			reasons.add("hechizo de " + f(e.build.spellDamage, 1) + " cada " + e.build.spellCooldown + " ticks mientras dura el maná, sin estamina y atravesando armadura");
		} else if (!e.build.charges) {
			reasons.add("no puede cargar golpes");
		}
		return String.join("; ", reasons);
	}

	private void recommendations() {
		Findings fd = this.findings;
		Analysis an = this.analysis;
		CombatConfig cfg = CombatConfig.get();
		this.line("## Recomendaciones");
		this.line("");
		this.line("Sólo propuestas: ningún número se ha tocado. Cada una sale de una medida de arriba; decide Andy.");
		this.line("");
		int n = 1;
		if (this.median(fd.tiredShare) >= 0.4) {
			this.line(n++ + ". **Estamina**: golpear sin estamina sigue saliendo a cuenta (×" + f(cfg.tiredDamageMultiplier, 1) + "). Si la "
				+ "estamina tiene que marcar el ritmo, bajar `tiredDamageMultiplier` (0,3–0,4) o no dejar atacar a 0, o que la estamina "
				+ "vuelva también mientras se ataca despacio.");
		}
		if (!fd.iframesRatio.isEmpty()) {
			this.line(n++ + ". **Invulnerabilidad y extras**: hoy cada extra de mejora deja el \"último daño\" en el suyo, pequeño, y el "
				+ "siguiente golpe rápido entra casi entero; sin extras, por encima de 2 golpes/s el golpe sólo quita lo que supera al "
				+ "anterior. O se acepta y se documenta, o `extraDamage` guarda y restaura `invulnerableTime` y `lastHurt` del objetivo.");
		}
		if (fd.afiladoRatio.values().stream().anyMatch(v -> !Double.isNaN(v) && v > 1.03)) {
			this.line(n++ + ". **Afilado**: que cuente sólo en la cabeza o la hoja (no en atadura ni guarda), o que sea un porcentaje del "
				+ "golpe en vez de +3 plano, que pesa mucho más en armas rápidas.");
		}
		if (fd.vidriaceroRatio.values().stream().anyMatch(v -> !Double.isNaN(v) && v > 1.03)) {
			this.line(n++ + ". **Vidriacero**: su +0,3 de velocidad por Diáfano se suma a un mango que ya es el más rápido; que Diáfano "
				+ "sólo quite peso en armadura, o bajar su `handleAttackSpeed`.");
		}
		if (!fd.mestizaje.isEmpty()) {
			this.line(n++ + ". **Mestizaje**: o se aplica en `Assembler.write` (y entonces se nota en el daño) o se quita de la ficha; "
				+ "hoy promete un número que el arma no tiene.");
		}
		double[] mace = fd.plainMace;
		if (mace != null && mace[1] >= mace[0] * 1.8) {
			this.line(n++ + ". **Frenesí**: que sea un porcentaje de la velocidad del arma (p. ej. +" + f(Upgrade.attackSpeedBoost(1.0F) / 1.6 * 100, 0)
				+ " %, lo que hoy es para una espada) en vez de +" + f(Upgrade.attackSpeedBoost(1.0F), 1) + " plano, que dobla el mazo.");
		}
		boolean pactsStrong = fd.pactGain.values().stream().filter(g -> g[0] < 0.99).count() * 2 > Analysis.TYPES.size();
		if (pactsStrong) {
			this.line(n++ + ". **Pactos**: que pesen (p. ej. 2) o que no sumen potencial; hoy dan daño, techo y carga a la vez, y su "
				+ "coste no es de combate.");
		}
		if (this.median(fd.extraShare) > 0.3) {
			this.line(n++ + ". **Tope y extras**: los extras son el " + pct(this.median(fd.extraShare)) + " del daño y cada uno lleva su "
				+ "propio tope. Si el tope ha de decir \"nada muere de un golpe\", que los extras de un golpe cuenten contra el mismo tope "
				+ "(sumarlos en `CombatUpgrades` antes de `capped`).");
		}
		double staffGeo = an.reports.get(ForgeType.BACULO).best.get(Analysis.Scenario.MAXIMO).geo;
		if (staffGeo < this.meleeMedian() * 0.7) {
			this.line(n++ + ". **Báculo**: mata en " + f(staffGeo, 2) + " s de media contra " + f(this.meleeMedian(), 2)
				+ " s del cuerpo a cuerpo, sin estamina y por encima de la armadura; contra jefes es el mejor con diferencia. Hoy la magia "
				+ "sólo paga la espera entre hechizos: un coste de estamina por hechizo la pondría en su sitio.");
		}
		if (this.median(fd.eventGain) < 0.95) {
			this.line(n++ + ". **Mejoras de evento**: peso 1 o 2, como cualquier otra; si no, en cuanto se tiene un orbe es obligatorio.");
		}
		if (this.median(fd.frenzyRatio) > 1.1) {
			this.line(n++ + ". **Techo del frenesí**: ×" + f(Upgrade.EJECUCION.frenzyCeiling(), 1) + " para las de un ingrediente convierte "
				+ "Matagigantes y Ejecución en el centro de todo; bajar el techo de las de daño (p. ej. ×1,5) o dejar el ×2,5 para las de "
				+ "utilidad.");
		}
		if (!fd.dominated.isEmpty()) {
			List<String> dominated = new ArrayList<>();
			fd.dominated.forEach((type, by) -> dominated.add(type.id() + " (por " + by.id() + ")"));
			this.line(n++ + ". **Tipos dominados**: " + String.join(", ", dominated) + " no ganan en nada medible aquí; necesitan algo que "
				+ "sólo ellos hagan (alcance, área, lanzar) y que este informe no mide, o números nuevos.");
		}
		this.line("");
	}
}
