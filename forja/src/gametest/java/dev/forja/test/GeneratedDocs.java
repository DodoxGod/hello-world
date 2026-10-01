package dev.forja.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import dev.forja.forge.ForgeType;
import dev.forja.upgrade.Upgrade;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Server-side generated reference tables, run by the normal gametest suite. */
public class GeneratedDocs {
	@GameTest
	public void generatedReferenceTables(GameTestHelper helper) throws IOException {
		write();
		Path docs = docs();
		long materialRows = Files.readString(docs.resolve("MATERIALES.md")).lines().filter(line -> line.startsWith("| ")).count() - 1;
		long upgradeRows = Files.readString(docs.resolve("MEJORAS.md")).split("## Sinergias")[0].lines()
			.filter(line -> line.startsWith("| ")).count() - 1;
		helper.assertTrue(materialRows == dev.forja.material.ForgeMaterial.values().length, "material rows: " + materialRows);
		helper.assertTrue(upgradeRows == Upgrade.values().length, "upgrade rows: " + upgradeRows);
		helper.succeed();
	}

	public static void write() throws IOException {
		Path docs = docs();
		Files.createDirectories(docs);
		Language original = Language.getInstance();
		Language.inject(spanish(original));
		try {
			Files.writeString(docs.resolve("MEJORAS.md"), upgrades());
			Files.writeString(docs.resolve("MATERIALES.md"), materials());
		} finally {
			Language.inject(original);
		}
	}

	private static Language spanish(Language original) throws IOException {
		var translations = new HashMap<String, String>();
		Path gradle = Path.of(System.getenv().getOrDefault("GRADLE_USER_HOME",
			Path.of(System.getProperty("user.home"), ".gradle").toString()));
		Path assets = gradle.resolve("caches/fabric-loom/assets");
		try (var indexes = Files.newDirectoryStream(assets.resolve("indexes"), "26.2-*.json")) {
			for (Path index : indexes) {
				try (var reader = Files.newBufferedReader(index)) {
					String hash = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("objects")
						.getAsJsonObject("minecraft/lang/es_es.json").get("hash").getAsString();
					try (InputStream input = Files.newInputStream(assets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash))) {
						Language.loadFromJson(input, translations::put);
					}
				break;
				}
			}
		}
		try (InputStream input = GeneratedDocs.class.getResourceAsStream("/assets/forja/lang/es_mx.json")) {
			if (input == null) {
				throw new IOException("Missing Forja Spanish translations");
			}
			Language.loadFromJson(input, translations::put);
		}
		return new Language() {
			@Override public String getOrDefault(String key, String fallback) {
				return translations.getOrDefault(key, original.getOrDefault(key, fallback));
			}
			@Override public boolean has(String key) {
				return translations.containsKey(key) || original.has(key);
			}
			@Override public boolean isDefaultRightToLeft() {
				return false;
			}
			@Override public FormattedCharSequence getVisualOrder(FormattedText text) {
				return original.getVisualOrder(text);
			}
		};
	}

	private static Path docs() {
		return FabricLoader.getInstance().getGameDir().getParent().getParent().getParent().resolve("docs");
	}

	private static String upgrades() {
		StringBuilder out = new StringBuilder();
		out.append("# Mejoras\n\n");
		out.append("Esta lista la escribe `./gradlew runGametest`, así que no se queda vieja.\n");
		out.append("Cada objeto suma el porcentaje indicado; las que dicen \"+\" necesitan los dos objetos juntos.\n\n");
		out.append("| Mejora | Va en | Se alimenta con | Al 100% |\n|---|---|---|---|\n");
		for (Upgrade upgrade : Upgrade.values()) {
			List<String> types = new ArrayList<>();
			for (ForgeType type : ForgeType.values()) {
				if (upgrade.appliesTo(type)) {
					types.add(Component.translatable("item.forja." + type.id()).getString());
				}
			}
			String where = types.size() == ForgeType.values().length ? "todo"
				: types.size() > 6 ? types.size() + " objetos" : String.join(", ", types);
			List<String> feeds = new ArrayList<>();
			for (Upgrade.Option option : upgrade.options) {
				List<String> items = new ArrayList<>();
				for (var requirement : option.requirements()) {
					items.add(requirement.displayStack().getHoverName().getString());
				}
				feeds.add(String.join(" + ", items) + " " + option.percent() + "%");
			}
			String food = feeds.isEmpty() ? "solo de un evento" : String.join(" / ", feeds);
			out.append("| ").append(upgrade.displayName().getString())
				.append(" | ").append(where)
				.append(" | ").append(food)
				.append(" | ").append(upgrade.effect(100).getString().replace("|", "/"))
				.append(" |\n");
		}
		out.append("\n## Sinergias\n\n");
		out.append("Dos mejoras al ").append(dev.forja.upgrade.Synergy.THRESHOLD).append("% en la misma pieza.\n\n");
		out.append("| Sinergia | Pareja | Qué hace |\n|---|---|---|\n");
		for (dev.forja.upgrade.Synergy synergy : dev.forja.upgrade.Synergy.values()) {
			out.append("| ").append(synergy.displayName().getString())
				.append(" | ").append(synergy.first.displayName().getString())
				.append(" + ").append(synergy.second.displayName().getString())
				.append(" | ").append(synergy.description().getString())
				.append(" |\n");
		}
		return out.toString();
	}

	private static String materials() {
		StringBuilder out = new StringBuilder();
		out.append("# Materiales\n\n");
		out.append("Esta lista la escribe `./gradlew runGametest`, igual que la de mejoras.\n");
		out.append("El **rasgo** lo lleva cualquier pieza hecha de ese material; el **conjunto** es lo que dan las\n");
		out.append("cuatro placas de armadura del mismo material puestas a la vez.\n\n");
		out.append("| Material | Durabilidad | Minado | Daño | Rasgo | Conjunto |\n|---|---|---|---|---|---|\n");
		for (dev.forja.material.ForgeMaterial material : dev.forja.material.ForgeMaterial.values()) {
			String written = Component.translatable("conjunto.forja." + material.getSerializedName()).getString();
			List<String> bonuses = dev.forja.upgrade.ArmorSets.bonuses(material).isEmpty()
				? List.of() : List.of(written);
			out.append("| ").append(material.displayName().getString())
				.append(" | ").append(material.durability)
				.append(" | ").append(String.format(Locale.ROOT, "%.1f", material.miningSpeed))
				.append(" | ").append(String.format(Locale.ROOT, "%+.1f", material.attackDamageBonus))
				.append(" | ").append(material.trait == dev.forja.material.ForgeMaterial.Trait.NONE
					? "-" : material.trait.displayName().getString())
				.append(" | ").append(bonuses.isEmpty() ? "-" : String.join(", ", bonuses))
				.append(" |\n");
		}
		return out.toString();
	}
}
