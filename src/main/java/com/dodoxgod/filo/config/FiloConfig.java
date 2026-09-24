package com.dodoxgod.filo.config;

import com.dodoxgod.filo.Filo;
import com.dodoxgod.filo.combat.DamageKind;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todos los números del mod. Se guardan en {@code config/filo.json} para ajustar la dificultad
 * sin tocar código. Los campos que falten en el archivo toman el valor por defecto.
 */
public class FiloConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static FiloConfig instance = new FiloConfig();

	public Armor armor = new Armor();
	public Penetration penetration = new Penetration();
	public Stamina stamina = new Stamina();
	public Dodge dodge = new Dodge();
	public Parry parry = new Parry();
	public Posture posture = new Posture();
	public Mobs mobs = new Mobs();
	/** Tipo de daño de los mobs que atacan sin arma, por id de entidad. El resto hace BLUNT. */
	public Map<String, DamageKind> naturalAttacks = defaultNaturalAttacks();

	public static class Armor {
		public boolean enabled = true;
		/** K de la curva armadura / (armadura + K). Más alto = la armadura protege menos. */
		public double curveK = 20.0;
		/** Máximo de daño que puede bloquear la armadura (0 a 1). */
		public double maxReduction = 0.8;
		/** Dureza a la que la penetración del atacante se reduce a la mitad. */
		public double toughnessScale = 10.0;
		/** Protección de una pieza a 0 de durabilidad (1 = no importa la durabilidad). */
		public double minDurabilityFactor = 0.6;
		/** Si es false, todas las piezas protegen por igual en cualquier golpe. */
		public boolean hitZones = true;
		/** Multiplicador de daño en golpes precisos a la cabeza. */
		public double headMultiplier = 1.3;
		/** Resistencias y movilidad por material vanilla (id del material de armadura). */
		public Map<String, MaterialStats> materials = defaultMaterials();
	}

	public static class MaterialStats {
		public double slash = 1.0;
		public double blunt = 1.0;
		public double pierce = 1.0;
		/** Velocidad con el conjunto completo puesto (1 = sin penalización). */
		public double mobility = 1.0;

		public MaterialStats() {
		}

		public MaterialStats(double slash, double blunt, double pierce, double mobility) {
			this.slash = slash;
			this.blunt = blunt;
			this.pierce = pierce;
			this.mobility = mobility;
		}

		public double resistance(DamageKind kind) {
			return switch (kind) {
				case SLASH -> slash;
				case BLUNT -> blunt;
				case PIERCE -> pierce;
				case OTHER -> 1.0;
			};
		}
	}

	/** Fracción de armadura que ignora cada ataque (0 a 1). */
	public static class Penetration {
		public double fist = 0.0;
		public double sword = 0.10;
		public double axe = 0.35;
		public double mace = 0.25;
		public double trident = 0.30;
		public double pickaxe = 0.25;
		public double otherTool = 0.05;
		public double mobNatural = 0.0;
		public double thrownTrident = 0.40;
		/** Flechas: base + velocidad * porBloque, con tope. Un arco tensado del todo va a ~3 bloques/tick. */
		public double arrowBase = 0.05;
		public double arrowPerSpeed = 0.12;
		public double arrowMax = 0.50;
	}

	public static class Stamina {
		public boolean enabled = true;
		public float max = 100f;
		public float regenPerTick = 1.0f;
		/** Ticks sin gastar estamina antes de que empiece a recuperarse. */
		public int regenDelayTicks = 20;
		public float attackCost = 12f;
		public float dodgeCost = 25f;
		/** Estamina que cuesta bloquear con escudo, por punto de daño. */
		public float blockCostPerDamage = 3f;
		/** Daño que haces si atacas sin estamina suficiente. */
		public double tiredDamageMultiplier = 0.6;
		/** Cuánto frena la regeneración el peso de la armadura (peso 0.1 * 3 = 30 % más lenta). */
		public double regenPenaltyPerWeight = 3.0;
		/** Aplicar la reducción de velocidad por peso de armadura. */
		public boolean armorWeightSlows = true;
	}

	public static class Dodge {
		public boolean enabled = true;
		/** Ticks de invulnerabilidad a ataques tras esquivar (20 ticks = 1 segundo). */
		public int iframeTicks = 6;
		public int cooldownTicks = 15;
		public double strength = 0.75;
		public double lift = 0.2;
	}

	public static class Parry {
		public boolean enabled = true;
		/** Ticks tras levantar el escudo en los que el bloqueo cuenta como parry. */
		public int windowTicks = 6;
		public float staminaRefund = 15f;
		/** Máxima distancia del atacante cuerpo a cuerpo para aturdirlo. */
		public double stunRange = 5.0;
	}

	public static class Posture {
		public boolean enabled = true;
		/** Postura máxima = vida máxima * healthFactor + base. */
		public double healthFactor = 0.6;
		public double base = 5.0;
		public int regenDelayTicks = 60;
		public double regenPerTick = 0.3;
		public int staggerTicks = 40;
		/** Daño extra que recibe una criatura aturdida. */
		public double staggerDamageMultiplier = 1.25;
		public double slashFactor = 1.0;
		public double bluntFactor = 1.5;
		public double pierceFactor = 0.6;
		public double otherFactor = 0.5;

		public double factor(DamageKind kind) {
			return switch (kind) {
				case SLASH -> slashFactor;
				case BLUNT -> bluntFactor;
				case PIERCE -> pierceFactor;
				case OTHER -> otherFactor;
			};
		}
	}

	public static class Mobs {
		/** Los mobs cuerpo a cuerpo avisan (partículas + sonido) y se paran antes de golpear. */
		public boolean telegraph = true;
		public int windupTicks = 8;
		/** Cuántos mobs pueden atacar a la vez a un mismo jugador. El resto espera su turno. */
		public int maxSimultaneousAttackers = 2;
		/** Margen de alcance al final del aviso; si te alejas más, el golpe falla. */
		public double strikeReachBonus = 0.5;
	}

	private static Map<String, MaterialStats> defaultMaterials() {
		Map<String, MaterialStats> m = new LinkedHashMap<>();
		m.put("minecraft:leather", new MaterialStats(0.8, 1.4, 0.7, 1.00));
		m.put("minecraft:chain", new MaterialStats(1.4, 0.7, 1.0, 0.97));
		m.put("minecraft:gold", new MaterialStats(0.8, 0.8, 0.8, 1.00));
		m.put("minecraft:iron", new MaterialStats(1.0, 1.0, 1.0, 0.93));
		m.put("minecraft:turtle", new MaterialStats(1.0, 1.0, 1.0, 0.97));
		m.put("minecraft:diamond", new MaterialStats(1.15, 1.15, 1.0, 0.92));
		m.put("minecraft:netherite", new MaterialStats(1.2, 1.2, 1.2, 0.90));
		return m;
	}

	private static Map<String, DamageKind> defaultNaturalAttacks() {
		Map<String, DamageKind> m = new LinkedHashMap<>();
		m.put("minecraft:spider", DamageKind.SLASH);
		m.put("minecraft:cave_spider", DamageKind.PIERCE);
		m.put("minecraft:wolf", DamageKind.SLASH);
		m.put("minecraft:polar_bear", DamageKind.SLASH);
		m.put("minecraft:cat", DamageKind.SLASH);
		m.put("minecraft:ocelot", DamageKind.SLASH);
		m.put("minecraft:fox", DamageKind.SLASH);
		m.put("minecraft:bee", DamageKind.PIERCE);
		m.put("minecraft:silverfish", DamageKind.PIERCE);
		m.put("minecraft:endermite", DamageKind.PIERCE);
		m.put("minecraft:phantom", DamageKind.SLASH);
		m.put("minecraft:vex", DamageKind.SLASH);
		m.put("minecraft:hoglin", DamageKind.PIERCE);
		m.put("minecraft:zoglin", DamageKind.PIERCE);
		return m;
	}

	public MaterialStats material(String id) {
		MaterialStats stats = armor.materials.get(id);
		return stats != null ? stats : new MaterialStats();
	}

	public static FiloConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("filo.json");
		FiloConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, FiloConfig.class);
			} catch (Exception e) {
				Filo.LOGGER.error("No se pudo leer {}, se usan los valores por defecto", path, e);
			}
		}
		instance = loaded != null ? loaded : new FiloConfig();
		// Reescribir para añadir campos nuevos que falten en archivos antiguos.
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			Filo.LOGGER.warn("No se pudo guardar {}", path, e);
		}
	}
}
