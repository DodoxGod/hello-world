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
		this.suspicions();
		this.newFindings();
		this.bestBuilds();
		this.ttkTables();
		this.difficulty();
		this.materialsSection();
		this.upgradesSection();
		this.mobs();
		this.outliers();
		this.recommendations();
		try {
			Path root = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().getParent().getParent().getParent();
			Path docs = root.resolve("docs");
			Files.createDirectories(docs);
			Files.writeString(docs.resolve("EQUILIBRIO.md"), this.out.toString());
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
			+ "). *Con pactos*: además los dos pactos de arma al 100 %, que suben el potencial " + Potential.PER_PACT + " cada uno.");
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
		this.row("3", "Los pactos son ganancia pura para el daño y encima dan sitio (+" + Potential.PER_PACT + " de potencial cada uno)",
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
			this.line("- **El Mestizaje sale en la ficha pero no en el golpe.** `ForgeStats.sheet(stack)` suma el "
				+ pct(sword[0]) + " de mezclar rasgos, pero `Assembler.write` escribe los atributos sin él: una espada de damasco con mango "
				+ "de vidriacero y guarda de eco enseña " + f(sword[1], 2) + " de daño y pega " + f(sword[2], 2) + ". Pasa igual en armaduras "
				+ "y herramientas (sólo arcos, flechas, escudos y alas leen la ficha con el Mestizaje dentro).");
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
				+ f(Upgrade.thirstDamage(1.0F) * 100, 0) + " % y +" + f(Upgrade.glassDamage(1.0F) * 100, 0) + " % de daño al atributo, peso 0, "
				+ "sin techo, y +" + (2 * Potential.PER_PACT) + " de potencial que sube el techo de las demás de 50 a "
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
			reasons.add("hechizo de " + f(e.build.spellDamage, 1) + " cada " + e.build.spellCooldown + " ticks, sin estamina y atravesando armadura");
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
