package dev.forja.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * What the benchmark measures, and nothing else: the length of every server tick, inclusive timers
 * around Forja's own paths and counts of its small helpers (the test mixins call in here), the
 * particles sent, and a JFR recording of the server thread for the share nobody wrapped by hand.
 *
 * <p>Everything is off unless a window is open ({@link #begin}), so the ordinary test suite pays one
 * branch per wrapped call. Server thread only: nothing here is synchronised.
 */
public final class Perf {
	/** The timed paths. Inclusive: a timer contains whatever it calls, other timers included. */
	public enum T {
		MOBAI_TICK("MobAi.tick (todos los cerebros + escuadra)"),
		SQUAD("↳ Squad.update (cada 10 ticks)"),
		THINK("↳ MobAi.think (un mob)"),
		RULES("↳↳ RuleBrain.decide"),
		OBS_M1("↳↳ ObsM1.of (red)"),
		OBS_FORJA("↳↳ ObsForja.full (red)"),
		MASK("↳↳ MobAi.mask (red)"),
		FORWARD("↳↳ NetBrain.forward (red)"),
		SAMPLE("↳↳ NetBrain.sample (red)"),
		ALLIES("ObsM1.allies (búsqueda de aliados)"),
		TACTIC_TICK("TacticGoal.tick"),
		TACTIC_USE("TacticGoal.canUse"),
		SPECIAL_USE("SpecialGoal.canUse (reglas: ¿empieza un especial?)"),
		SPECIAL_TICK("SpecialGoal.tick"),
		MOVEMENT("MovementGoals Track/Home/Curious/HighGround canUse"),
		SHOCKWAVE("Shockwave.tick"),
		SMITH_TICK("FallenSmith.tick (incluye su cuerpo vanilla)"),
		SMITH_MOVES("↳ FallenSmith.heavyMoves");

		public final String label;

		T(String label) {
			this.label = label;
		}
	}

	/** Small helpers, counted rather than timed: a clock read would cost as much as they do. */
	public enum C {
		TOKENS_ACQUIRE("AttackTokens.tryAcquire"),
		TOKENS_FREE("AttackTokens.free"),
		TOKENS_HOLDS("AttackTokens.holds"),
		POSTURE_STAGGERED("Posture.isStaggered"),
		POSTURE_FILL("Posture.fill"),
		TRAIT("Personality.trait"),
		TACTIC_PATH("TacticGoal.pathTo"),
		TACTIC_MOVETO("TacticGoal.pathTo → moveTo (ruta nueva)"),
		TACTIC_MOVETO_DONE("  de ellas antes de los 10 ticks, por navegación terminada"),
		PATHFINDS("PathFinder.findPath (todas las rutas, vanilla incluida)");

		public final String label;

		C(String label) {
			this.label = label;
		}
	}

	private static boolean on;
	private static final long[] STARTED = new long[T.values().length];
	private static final long[] TOTAL = new long[T.values().length];
	private static final long[] CALLS = new long[T.values().length];
	private static final long[] COUNTS = new long[C.values().length];

	private static long particleCalls;
	private static long particleCount;
	private static final Map<String, long[]> PARTICLE_TYPES = new HashMap<>();
	private static final Map<String, long[]> PARTICLE_SOURCES = new HashMap<>();
	/** One call in this many has its sender looked up on the stack; the rest are only counted. */
	private static final int SOURCE_SAMPLE = 8;
	private static final StackWalker WALKER = StackWalker.getInstance();

	private static long tickStart;
	private static long[] ticks = new long[0];
	private static int tickCount;
	private static boolean hooked;

	private Perf() {
	}

	public static void start(T timer) {
		if (on) {
			STARTED[timer.ordinal()] = System.nanoTime();
		}
	}

	public static void stop(T timer) {
		if (on) {
			int i = timer.ordinal();
			TOTAL[i] += System.nanoTime() - STARTED[i];
			CALLS[i]++;
		}
	}

	public static void count(C counter) {
		if (on) {
			COUNTS[counter.ordinal()]++;
		}
	}

	public static void particles(ParticleOptions options, int count) {
		if (!on) {
			return;
		}
		particleCalls++;
		particleCount += Math.max(1, count);
		Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
		long[] byType = PARTICLE_TYPES.computeIfAbsent(String.valueOf(id), k -> new long[2]);
		byType[0]++;
		byType[1] += Math.max(1, count);
		if (particleCalls % SOURCE_SAMPLE == 0) {
			String source = WALKER.walk(frames -> frames
				.filter(f -> f.getClassName().startsWith("dev.forja.") && !f.getClassName().startsWith("dev.forja.test."))
				.findFirst()
				.map(f -> f.getClassName().substring("dev.forja.".length()) + "." + f.getMethodName())
				.orElse("(vanilla)"));
			long[] bySource = PARTICLE_SOURCES.computeIfAbsent(source, k -> new long[2]);
			bySource[0]++;
			bySource[1] += Math.max(1, count);
		}
	}

	/**
	 * Hooks the clock round every server tick: the start in a phase before everyone else's, the end in
	 * one after, so every mod's tick handlers fall inside it. Done once, the first time a window opens,
	 * which is long after every mod has registered its own.
	 */
	private static void hook() {
		if (hooked) {
			return;
		}
		hooked = true;
		Identifier first = Identifier.fromNamespaceAndPath("forja-test", "rendimiento_primero");
		Identifier last = Identifier.fromNamespaceAndPath("forja-test", "rendimiento_ultimo");
		ServerTickEvents.START_SERVER_TICK.addPhaseOrdering(first, Event.DEFAULT_PHASE);
		ServerTickEvents.END_SERVER_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, last);
		ServerTickEvents.START_SERVER_TICK.register(first, server -> {
			if (on) {
				tickStart = System.nanoTime();
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(last, server -> {
			if (on && tickStart != 0L) {
				if (tickCount == ticks.length) {
					ticks = Arrays.copyOf(ticks, Math.max(256, ticks.length * 2));
				}
				ticks[tickCount++] = System.nanoTime() - tickStart;
			}
		});
	}

	/** What one window measured. */
	public static final class Result {
		public String name;
		public String description;
		public long[] tickNanos;
		public long[] timerNanos;
		public long[] timerCalls;
		public long[] counts;
		public long particleCalls;
		public long particleCount;
		public Map<String, long[]> particleTypes;
		public Map<String, long[]> particleSources;
		public Jfr jfr;
		/** Whatever the scenario itself kept track of: mobs alive, respawns, rings... */
		public final Map<String, String> notes = new java.util.LinkedHashMap<>();

		public int ticks() {
			return this.tickNanos.length;
		}

		public double meanMs() {
			return Arrays.stream(this.tickNanos).average().orElse(0.0) / 1.0E6;
		}

		public double percentileMs(double p) {
			if (this.tickNanos.length == 0) {
				return 0.0;
			}
			long[] sorted = this.tickNanos.clone();
			Arrays.sort(sorted);
			int index = (int) Math.ceil(p * sorted.length) - 1;
			return sorted[Math.max(0, Math.min(sorted.length - 1, index))] / 1.0E6;
		}

		public double maxMs() {
			return Arrays.stream(this.tickNanos).max().orElse(0L) / 1.0E6;
		}

		/** A timer's time per tick, in ms. */
		public double perTickMs(T timer) {
			return this.ticks() == 0 ? 0.0 : this.timerNanos[timer.ordinal()] / 1.0E6 / this.ticks();
		}
	}

	private static Recording recording;
	private static String windowName;

	/** Opens a measurement window: counters to zero, the clock on, a JFR recording running. */
	public static void begin(String name) {
		hook();
		windowName = name;
		Arrays.fill(STARTED, 0L);
		Arrays.fill(TOTAL, 0L);
		Arrays.fill(CALLS, 0L);
		Arrays.fill(COUNTS, 0L);
		particleCalls = 0L;
		particleCount = 0L;
		PARTICLE_TYPES.clear();
		PARTICLE_SOURCES.clear();
		ticks = new long[1024];
		tickCount = 0;
		tickStart = 0L;
		try {
			recording = new Recording();
			recording.setName("forja-" + name);
			recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1)).withStackTrace();
			recording.enable("jdk.GarbageCollection");
			recording.start();
		} catch (RuntimeException | Error failure) {
			recording = null;
		}
		on = true;
	}

	/** Closes the window and hands back what it saw; the JFR file is kept in {@code folder}. */
	public static Result end(Path folder) {
		on = false;
		Result result = new Result();
		result.name = windowName;
		result.tickNanos = Arrays.copyOf(ticks, tickCount);
		result.timerNanos = TOTAL.clone();
		result.timerCalls = CALLS.clone();
		result.counts = COUNTS.clone();
		result.particleCalls = particleCalls;
		result.particleCount = particleCount;
		result.particleTypes = new HashMap<>(PARTICLE_TYPES);
		result.particleSources = new HashMap<>(PARTICLE_SOURCES);
		if (recording != null) {
			try {
				recording.stop();
				Files.createDirectories(folder);
				Path file = folder.resolve(windowName + ".jfr");
				recording.dump(file);
				result.jfr = Jfr.read(file);
			} catch (IOException | RuntimeException failure) {
				result.notes.put("jfr", "no se pudo leer: " + failure);
			} finally {
				recording.close();
				recording = null;
			}
		}
		return result;
	}

	public static boolean measuring() {
		return on;
	}

	/**
	 * The server thread's samples, sorted by where Forja's code is on the stack. A frame is Forja's when
	 * its class is in dev.forja (not these tests), or when it is one of the mod's mixin handlers merged
	 * into a vanilla class (their names carry "forja$").
	 */
	public static final class Jfr {
		public int samples;
		public int truncated;
		/** Samples caught inside the benchmark's own clocks and counters (left out of every share below). */
		public int measurement;
		/** Any Forja frame on the stack. */
		public int anyForja;
		/** The innermost Forja frame is Forja's own logic. */
		public int forjaLogic;
		/**
		 * The innermost Forja frame only passes a lifecycle call on to vanilla (a Forja mob's tick calling
		 * super.tick): that is the vanilla body of one of the mod's mobs, not something Forja asked for.
		 */
		public int forjaBodies;
		public final Map<String, Integer> innermost = new HashMap<>();
		/** For each innermost Forja frame, the methods its samples were caught in. */
		public final Map<String, Map<String, Integer>> innermostLeaves = new HashMap<>();
		public final Map<String, Integer> outermost = new HashMap<>();
		public final Map<String, Integer> leaves = new HashMap<>();
		public int gcCount;
		public double gcPauseMs;

		static boolean forja(RecordedFrame frame) {
			if (frame.getMethod() == null || frame.getMethod().getType() == null) {
				return false;
			}
			String type = frame.getMethod().getType().getName();
			String method = frame.getMethod().getName();
			return type.startsWith("dev.forja.") && !type.startsWith("dev.forja.test.") || method.contains("forja$");
		}

		/** This benchmark's own clocks and counters, wherever the mixins merged them. */
		static boolean measurement(RecordedFrame frame) {
			if (frame.getMethod() == null || frame.getMethod().getType() == null) {
				return false;
			}
			return frame.getMethod().getType().getName().startsWith("dev.forja.test.") || frame.getMethod().getName().contains("bench$");
		}

		static String name(RecordedFrame frame) {
			String type = frame.getMethod().getType().getName();
			// A lambda or method reference lives in a hidden class named after the class that wrote it.
			int lambda = type.indexOf("$$Lambda");
			if (lambda >= 0) {
				String owner = type.substring(0, lambda);
				return owner.substring(owner.lastIndexOf('.') + 1) + "(λ)." + frame.getMethod().getName();
			}
			int dot = type.lastIndexOf('.');
			return type.substring(dot + 1) + "." + frame.getMethod().getName();
		}

		static Jfr read(Path file) throws IOException {
			Jfr jfr = new Jfr();
			for (RecordedEvent event : RecordingFile.readAllEvents(file)) {
				String type = event.getEventType().getName();
				if (type.equals("jdk.GarbageCollection")) {
					jfr.gcCount++;
					jfr.gcPauseMs += event.getDuration("sumOfPauses").toNanos() / 1.0E6;
					continue;
				}
				if (!type.equals("jdk.ExecutionSample")) {
					continue;
				}
				var thread = event.getThread("sampledThread");
				if (thread == null || !"Server thread".equals(thread.getJavaName())) {
					continue;
				}
				RecordedStackTrace stack = event.getStackTrace();
				if (stack == null || stack.getFrames().isEmpty()) {
					continue;
				}
				jfr.samples++;
				if (stack.isTruncated()) {
					jfr.truncated++;
				}
				List<RecordedFrame> frames = stack.getFrames();
				if (frames.stream().anyMatch(Jfr::measurement)) {
					jfr.measurement++;
					continue;
				}
				jfr.leaves.merge(name(frames.get(0)), 1, Integer::sum);
				int inner = -1;
				int outer = -1;
				for (int i = 0; i < frames.size(); i++) {
					if (forja(frames.get(i))) {
						if (inner < 0) {
							inner = i;
						}
						outer = i;
					}
				}
				if (inner < 0) {
					continue;
				}
				jfr.anyForja++;
				jfr.outermost.merge(name(frames.get(outer)), 1, Integer::sum);
				RecordedFrame f = frames.get(inner);
				boolean passthrough = inner > 0 && !forja(frames.get(inner - 1))
					&& frames.get(inner - 1).getMethod().getName().equals(f.getMethod().getName());
				if (passthrough) {
					jfr.forjaBodies++;
				} else {
					jfr.forjaLogic++;
					jfr.innermost.merge(name(f), 1, Integer::sum);
					// The call the Forja frame made, and where the sample landed under it.
					String below = inner >= 1 ? name(frames.get(inner - 1)) : "(propio)";
					jfr.innermostLeaves.computeIfAbsent(name(f), k -> new HashMap<>()).merge(below + " … " + name(frames.get(0)), 1, Integer::sum);
				}
			}
			return jfr;
		}
	}

	// --- The report ---------------------------------------------------------------------------------------

	static String pct(double part, double whole) {
		return whole <= 0.0 ? "-" : String.format(Locale.ROOT, "%.1f %%", 100.0 * part / whole);
	}

	static String ms(double v) {
		return String.format(Locale.ROOT, "%.3f", v);
	}

	private static <K> List<Map.Entry<K, long[]>> topLong(Map<K, long[]> map, int index, int n) {
		List<Map.Entry<K, long[]>> list = new ArrayList<>(map.entrySet());
		list.sort((a, b) -> Long.compare(b.getValue()[index], a.getValue()[index]));
		return list.subList(0, Math.min(n, list.size()));
	}

	private static List<Map.Entry<String, Integer>> top(Map<String, Integer> map, int n) {
		List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		return list.subList(0, Math.min(n, list.size()));
	}

	/** One window as Markdown, in Spanish: it goes straight into the notes for Andy. */
	public static String markdown(Result r) {
		StringBuilder out = new StringBuilder();
		int n = Math.max(1, r.ticks());
		double mean = r.meanMs();
		out.append("## ").append(r.name).append("\n\n");
		if (r.description != null) {
			out.append(r.description).append("\n\n");
		}
		out.append(String.format(Locale.ROOT, "- Tick del servidor (%d ticks): media **%s ms**, p50 %s, p95 **%s**, p99 %s, máx **%s ms** (TPS posible con esa media: %.0f)%n",
			r.ticks(), ms(mean), ms(r.percentileMs(0.50)), ms(r.percentileMs(0.95)), ms(r.percentileMs(0.99)), ms(r.maxMs()), mean > 0 ? 1000.0 / mean : 0.0));
		for (Map.Entry<String, String> note : r.notes.entrySet()) {
			out.append("- ").append(note.getKey()).append(": ").append(note.getValue()).append('\n');
		}
		out.append(String.format(Locale.ROOT, "- Partículas: %.1f llamadas a sendParticles por tick (= paquetes por tick a cada jugador cercano), %.1f partículas por tick%n",
			r.particleCalls / (double) n, r.particleCount / (double) n));
		if (!r.particleTypes.isEmpty()) {
			out.append("  - por tipo (partículas/tick): ");
			List<String> parts = new ArrayList<>();
			for (var e : topLong(r.particleTypes, 1, 8)) {
				parts.add(String.format(Locale.ROOT, "%s %.1f", e.getKey().replace("minecraft:", ""), e.getValue()[1] / (double) n));
			}
			out.append(String.join(", ", parts)).append('\n');
		}
		if (!r.particleSources.isEmpty()) {
			long sampled = r.particleSources.values().stream().mapToLong(v -> v[0]).sum();
			out.append("  - quién las manda (muestreo 1 de ").append(SOURCE_SAMPLE).append(", % de llamadas): ");
			List<String> parts = new ArrayList<>();
			for (var e : topLong(r.particleSources, 0, 8)) {
				parts.add(e.getKey() + " " + pct(e.getValue()[0], sampled));
			}
			out.append(String.join(", ", parts)).append('\n');
		}
		out.append("\n| Ruta de Forja (envoltorio, inclusivo) | ms/tick | % del tick | llamadas/tick | µs/llamada |\n|---|---:|---:|---:|---:|\n");
		for (T t : T.values()) {
			long calls = r.timerCalls[t.ordinal()];
			if (calls == 0) {
				continue;
			}
			double perTick = r.perTickMs(t);
			out.append(String.format(Locale.ROOT, "| %s | %s | %s | %.1f | %.2f |%n", t.label, ms(perTick), pct(perTick, mean),
				calls / (double) n, r.timerNanos[t.ordinal()] / 1000.0 / calls));
		}
		List<String> counts = new ArrayList<>();
		for (C c : C.values()) {
			if (r.counts[c.ordinal()] > 0) {
				counts.add(String.format(Locale.ROOT, "%s %.1f", c.label, r.counts[c.ordinal()] / (double) n));
			}
		}
		if (!counts.isEmpty()) {
			out.append("\nLlamadas por tick a los ayudantes pequeños: ").append(String.join(", ", counts)).append(".\n");
		}
		Jfr j = r.jfr;
		if (j != null && j.samples > 0) {
			out.append(String.format(Locale.ROOT, "%nJFR (muestreo cada 1 ms del hilo del servidor): %d muestras, %s truncadas, %s dentro de la propia medición. GC: %d pausas, %.1f ms en total.%n%n",
				j.samples, pct(j.truncated, j.samples), pct(j.measurement, j.samples), j.gcCount, j.gcPauseMs));
			out.append(String.format(Locale.ROOT, "- Con algún marco de Forja en la pila: **%s** del tiempo del servidor%n", pct(j.anyForja, j.samples)));
			out.append(String.format(Locale.ROOT, "  - lógica propia de Forja: **%s** (≈ %s ms/tick)%n", pct(j.forjaLogic, j.samples), ms(mean * j.forjaLogic / j.samples)));
			out.append(String.format(Locale.ROOT, "  - cuerpo vanilla de los mobs de Forja (su tick llama a super.tick): %s (≈ %s ms/tick)%n",
				pct(j.forjaBodies, j.samples), ms(mean * j.forjaBodies / j.samples)));
			out.append("- Dónde se va la lógica de Forja (marco de Forja más interno, incluye lo vanilla que llama): ");
			List<String> parts = new ArrayList<>();
			for (var e : top(j.innermost, 12)) {
				parts.add(e.getKey() + " " + pct(e.getValue(), j.samples));
			}
			out.append(String.join(", ", parts)).append('\n');
			for (var e : top(j.innermost, 5)) {
				parts.clear();
				for (var leaf : top(j.innermostLeaves.getOrDefault(e.getKey(), Map.of()), 4)) {
					parts.add(leaf.getKey() + " " + pct(leaf.getValue(), e.getValue()));
				}
				out.append("  - dentro de ").append(e.getKey()).append(" (llamada que hace … hoja): ").append(String.join(", ", parts)).append('\n');
			}
			out.append("- Por dónde entra (marco de Forja más externo): ");
			parts.clear();
			for (var e : top(j.outermost, 10)) {
				parts.add(e.getKey() + " " + pct(e.getValue(), j.samples));
			}
			out.append(String.join(", ", parts)).append('\n');
			out.append("- Métodos hoja más calientes (cualquier código): ");
			parts.clear();
			for (var e : top(j.leaves, 10)) {
				parts.add(e.getKey() + " " + pct(e.getValue(), j.samples));
			}
			out.append(String.join(", ", parts)).append('\n');
		}
		out.append('\n');
		return out.toString();
	}

	/** One line per window for a spreadsheet: the numbers to compare between runs. */
	public static String csvHeader() {
		StringBuilder out = new StringBuilder("ventana;ticks;media_ms;p50_ms;p95_ms;p99_ms;max_ms;particulas_llamadas_tick;particulas_tick;jfr_muestras;jfr_forja_logica_pct;jfr_cuerpos_forja_pct");
		for (T t : T.values()) {
			out.append(';').append(t.name().toLowerCase(Locale.ROOT)).append("_ms_tick");
		}
		return out.append('\n').toString();
	}

	public static String csvLine(Result r) {
		int n = Math.max(1, r.ticks());
		StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "%s;%d;%.4f;%.4f;%.4f;%.4f;%.4f;%.2f;%.2f", r.name, r.ticks(), r.meanMs(),
			r.percentileMs(0.5), r.percentileMs(0.95), r.percentileMs(0.99), r.maxMs(), r.particleCalls / (double) n, r.particleCount / (double) n));
		if (r.jfr != null && r.jfr.samples > 0) {
			out.append(String.format(Locale.ROOT, ";%d;%.2f;%.2f", r.jfr.samples, 100.0 * r.jfr.forjaLogic / r.jfr.samples, 100.0 * r.jfr.forjaBodies / r.jfr.samples));
		} else {
			out.append(";0;;");
		}
		for (T t : T.values()) {
			out.append(String.format(Locale.ROOT, ";%.4f", r.perTickMs(t)));
		}
		return out.append('\n').toString();
	}
}
