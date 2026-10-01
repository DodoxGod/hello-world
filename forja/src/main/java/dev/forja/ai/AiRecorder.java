package dev.forja.ai;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import dev.forja.Forja;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;

/**
 * Records every network decision as one JSON line, so the simulator's side can compare, input by input,
 * what a mob sees here with what it sees there (a sense that arrives different here is the commonest way
 * for a trained network to behave oddly in the real game):
 *
 * <pre>
 * {"t":…, "uuid":…, "tipo":…, "obs":[…], "mascara":[…], "accion":{"mover","saltar","usar","tactica","especial","defensa","fintar"},
 *  "logits":[…], "vida_mob":…, "vida_jugador":…, "evento":…}
 * </pre>
 *
 * Written to config/forja/grabaciones/, one file per recording, started and stopped with /forja ia grabar.
 */
public final class AiRecorder {
	private static BufferedWriter out;
	private static Path file;
	private static long lines;

	private AiRecorder() {
	}

	public static synchronized boolean recording() {
		return out != null;
	}

	public static synchronized Path start() throws IOException {
		return start(FabricLoader.getInstance().getConfigDir().resolve("forja").resolve("grabaciones"));
	}

	public static synchronized Path start(Path folder) throws IOException {
		stop();
		Files.createDirectories(folder);
		file = folder.resolve("grabacion_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".jsonl");
		out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		lines = 0;
		return file;
	}

	/** Stops recording; returns how many decisions were written. */
	public static synchronized long stop() {
		long written = lines;
		if (out != null) {
			try {
				out.close();
			} catch (IOException failure) {
				Forja.LOGGER.warn("No se pudo cerrar la grabación {}", file, failure);
			}
			out = null;
		}
		return written;
	}

	static synchronized void record(MobMind mind, Player target, float[] obs, boolean[] mask, long now) {
		if (out == null) {
			return;
		}
		Decision d = mind.decision;
		StringBuilder line = new StringBuilder(2048);
		line.append("{\"t\":").append(now)
			.append(",\"uuid\":\"").append(mind.mob.getUUID()).append('"')
			.append(",\"tipo\":\"").append(BuiltInRegistries.ENTITY_TYPE.getKey(mind.mob.getType())).append('"')
			.append(",\"obs\":");
		floats(line, obs);
		line.append(",\"mascara\":[");
		for (int i = 0; i < mask.length; i++) {
			line.append(i > 0 ? "," : "").append(mask[i] ? 1 : 0);
		}
		line.append("],\"accion\":{\"mover\":").append(d.move())
			.append(",\"saltar\":").append(d.jump() ? 1 : 0)
			.append(",\"usar\":").append(d.use() ? 1 : 0)
			.append(",\"tactica\":\"").append(d.tactic().name()).append('"')
			.append(",\"especial\":").append(d.special())
			.append(",\"defensa\":").append(d.defense())
			.append(",\"fintar\":").append(d.feint() ? 1 : 0)
			.append("},\"logits\":");
		floats(line, mind.lastLogits);
		line.append(",\"vida_mob\":").append(String.format(Locale.ROOT, "%.2f", mind.mob.getHealth()))
			.append(",\"vida_jugador\":").append(String.format(Locale.ROOT, "%.2f", target.getHealth()))
			.append(",\"evento\":\"").append(mind.windup > 0 ? "avisando" : mind.draw > 0 ? "tensando" : "").append("\"}\n");
		try {
			out.write(line.toString());
			lines++;
		} catch (IOException failure) {
			Forja.LOGGER.warn("Grabación interrumpida", failure);
			stop();
		}
	}

	private static void floats(StringBuilder line, float[] values) {
		line.append('[');
		if (values != null) {
			for (int i = 0; i < values.length; i++) {
				if (i > 0) {
					line.append(',');
				}
				line.append(String.format(Locale.ROOT, "%.4f", values[i]));
			}
		}
		line.append(']');
	}
}
