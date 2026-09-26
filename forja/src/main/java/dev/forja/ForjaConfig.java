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

	/** Reads config/forja.json, writing it with the defaults if it is not there yet. */
	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("forja.json");
		try {
			if (Files.exists(path)) {
				ForjaConfig read = GSON.fromJson(Files.readString(path), ForjaConfig.class);
				if (read != null) {
					current = read;
				}
				// Written back so keys added in a newer version show up in an older file.
				Files.writeString(path, GSON.toJson(current));
				return;
			}
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(current));
		} catch (IOException | JsonSyntaxException failure) {
			// A broken or unreadable file is not worth stopping the game for: the defaults stand.
			Forja.LOGGER.warn("No se pudo leer config/forja.json, se usan los valores por defecto", failure);
		}
	}
}
