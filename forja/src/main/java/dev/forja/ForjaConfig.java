package dev.forja;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The handful of numbers worth arguing about, kept in config/forja.json so they can be changed without
 * a new jar. Everything else is balance the mod means; these are the ones that decide how often the
 * world bothers you. Written with its defaults the first time the mod runs.
 */
public final class ForjaConfig {
	/** How often a band of raiders turns up: checked once a minute per player, at night. */
	public float saqueadores = 0.06F;

	/** The share of monsters that spawn as an elite carrying a legend. */
	public float elites = 0.01F;

	/**
	 * The share of skeletons (strays, bogged and parched too) that spawn with a forged staff instead of a
	 * bow and throw its bolts at you: 3 in 100 by default (world/ForjaMobs, entity/ai/CasterGoal).
	 */
	public float baculos = 0.03F;

	/**
	 * The share of zombies (husks and zombie villagers too) that spawn reading a forged tome: they open
	 * its area under you after a warning on the floor. 3 in 100 by default.
	 */
	public float grimorios = 0.03F;

	/** How often an event starts when none is running: checked once a minute. */
	public float eventos = 0.02F;

	/** How often an empty suit stands up near a workshop at night: checked once a minute per player. */
	public float corazas = 0.05F;

	/** How often wisps are drawn to a fire: checked once a minute per player, day or night. */
	public float pavesas = 0.08F;

	/** Herrumbres: a nest opening under a player who is underground, in the dark and wearing metal. */
	public float herrumbres = 0.12F;

	/** Escorias: one getting up next to a real pool of lava underground. */
	public float escorias = 0.10F;

	/** Constructos: tenazas, percutores y yunques despertando dentro de un taller en ruinas. */
	public float constructos = 0.20F;

	/** How often the hauler and the quencher come to a working forge. */
	public float fundicion = 0.14F;

	/** How often a star core is hanging over an open field at night. */
	public float nucleos = 0.05F;

	/** The combat overhaul: armor formula, stamina, dodge, posture and vanilla mob behaviour. */
	public dev.forja.combat.CombatConfig combate = new dev.forja.combat.CombatConfig();

	/**
	 * One line in chat pointing at the next step of the guide's path (ForjaPath) each time the path moves
	 * on. Not a chance but a switch: it is read by each client for its own player, so on a server every
	 * player decides for themselves.
	 */
	public boolean pistas = true;

	/** Everything in this file is a chance per check, from 0 (never) to 1 (always). */
	public String _comentario = "Probabilidades por comprobacion, de 0 (nunca) a 1 (siempre).";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ForjaConfig current = new ForjaConfig();

	public static ForjaConfig get() {
		return current;
	}

	/** Writes the config as it is now to config/forja.json (after a command changed it). */
	public static void save() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("forja.json");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(current));
		} catch (IOException failure) {
			Forja.LOGGER.warn("No se pudo guardar config/forja.json", failure);
		}
	}

	/**
	 * The monsters' v4 actions (Andy, 2026-09-29): a file from before M2 had both switches off, because nothing used them
	 * yet. They come on once, here; whatever is chosen after that stays (CombatConfig.iaAccionesRevision).
	 */
	private static void migrateActions(ForjaConfig config) {
		if (config.combate != null && config.combate.iaAccionesRevision < 1) {
			config.combate.mobActionsV4 = true;
			config.combate.mobsBreakLights = true;
			config.combate.iaAccionesRevision = 1;
		}
	}

	/**
	 * The difficulty ladder (Andy, 2026-09-30): "dificultad" was the difficulty itself, HERRERO by default; now the
	 * world's difficulty button decides and the field only forces the multipliers. Once, here: the old default becomes
	 * "auto" (the ladder's own), any other preset an admin chose stays as the override it now is, and is logged.
	 */
	static void migrateDifficulty(ForjaConfig config) {
		dev.forja.combat.CombatConfig combat = config.combate;
		if (combat == null || combat.dificultadRevision >= 1) {
			return;
		}
		String old = combat.dificultad;
		if (old == null || old.isBlank() || "HERRERO".equalsIgnoreCase(old.trim())) {
			combat.dificultad = "auto";
		} else if (!"auto".equalsIgnoreCase(old.trim())) {
			Forja.LOGGER.info("config/forja.json: dificultad = {} se queda como forzado de las cifras sobre la escalera de dificultad; "
				+ "\"auto\" la deja en manos del botón de dificultad", old);
		}
		if (combat.nivel == null || combat.nivel.isBlank()) {
			combat.nivel = "auto";
		}
		combat.dificultadRevision = 1;
	}

	/** Reads config/forja.json, writing it with the defaults if it is not there yet. */
	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("forja.json");
		try {
			if (Files.exists(path)) {
				ForjaConfig read = GSON.fromJson(Files.readString(path), ForjaConfig.class);
				if (read != null) {
					current = read;
				}
				// The old default for what others do to a boss, never changed by hand, follows the new one
				// (Andy, 2026-09-29: a third, not a half). Anything else somebody typed in is theirs.
				if (current.combate != null && current.combate.jefeDanoAjeno == 0.5) {
					current.combate.jefeDanoAjeno = 0.333;
				}
				// The same for pressure (Andy, 2026-09-29): faster to build, slower to drain.
				if (current.combate != null && current.combate.pressurePerHit == 0.07) {
					current.combate.pressurePerHit = 0.10;
				}
				if (current.combate != null && current.combate.pressureDelayTicks == 40) {
					current.combate.pressureDelayTicks = 60;
				}
				// And again (Andy, 2026-09-30): it starts at 0 and tops at 60 %, takes about 9 blows to get there and
				// drains in 4.5 s. The defaults of the day before (0.10, 0.70, 0.02) follow the new ones.
				if (current.combate != null && current.combate.pressurePerHit == 0.10) {
					current.combate.pressurePerHit = 0.065;
				}
				if (current.combate != null && current.combate.pressureMax == 0.70) {
					current.combate.pressureMax = 0.60;
				}
				if (current.combate != null && current.combate.pressureDrainPerTick == 0.02) {
					current.combate.pressureDrainPerTick = 0.0067;
				}
				// Mana (Andy, 2026-09-30): very slow without a magic class. The old quick defaults follow the new ones.
				if (current.combate != null && current.combate.manaRegenPerTick == 0.3F) {
					current.combate.manaRegenPerTick = 0.02F;
				}
				if (current.combate != null && current.combate.manaIdleRegenPerTick == 1.0F) {
					current.combate.manaIdleRegenPerTick = 0.04F;
				}
				if (current.combate != null && current.combate.manaIdleDelayTicks == 40) {
					current.combate.manaIdleDelayTicks = 100;
				}
				// The tome's price, with its rune alone on the floor now (Andy, 2026-09-30: the magic was broken).
				if (current.combate != null && current.combate.manaTomeCost == 30.0F) {
					current.combate.manaTomeCost = 20.0F;
				}
				if (current.combate != null && current.combate.manaBoltCost == 8.0F) {
					current.combate.manaBoltCost = 10.0F;
				}
				migrateActions(current);
				migrateDifficulty(current);
				// Written back so keys added in a newer version show up in an older file.
				Files.writeString(path, GSON.toJson(current));
				return;
			}
			migrateActions(current);
			migrateDifficulty(current);
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(current));
		} catch (IOException | JsonSyntaxException failure) {
			// A broken or unreadable file is not worth stopping the game for: the defaults stand.
			Forja.LOGGER.warn("No se pudo leer config/forja.json, se usan los valores por defecto", failure);
		}
	}
}
