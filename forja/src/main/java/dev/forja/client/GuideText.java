package dev.forja.client;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.ForgeStats;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.upgrade.Upgrade;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/** Tooltip text shared by the tables and the guide book. */
public final class GuideText {
	/** The ink the book's emphasis is written in: a rubricator's red-brown, the way a manuscript marks a name. */
	public static final int RUBRIC = 0x8A2A12;

	/**
	 * The same text with everything between a pair of {@code **} set in the rubric ink.
	 *
	 * <p>Seventy of the book's texts were written with {@code **this**} in them and nothing ever read it:
	 * the bestiary printed "**Cerrar filas**: toca el cuerno…" with the asterisks standing there. Colour
	 * rather than bold, because the book is set at three quarters size and bold at that size is a smear.
	 * A text with no asterisks in it is handed back untouched, styles and all.
	 */
	public static Component rubric(Component text) {
		String plain = text.getString();
		if (!plain.contains("**")) {
			return text;
		}
		MutableComponent marked = Component.empty();
		String[] pieces = plain.split("\\*\\*", -1);
		for (int i = 0; i < pieces.length; i++) {
			if (pieces[i].isEmpty()) {
				continue;
			}
			// Odd pieces sit between a pair of marks; a stray mark at the end just leaves its piece plain.
			boolean inside = i % 2 == 1 && i < pieces.length - 1;
			marked.append(inside ? Component.literal(pieces[i]).withColor(RUBRIC) : Component.literal(pieces[i]));
		}
		return marked;
	}

	/** Sections of the upgrade chapter, in book order. */
	public static final List<String> UPGRADE_SECTIONS = List.of(
		"herramientas", "herramientas_y_armas", "armas", "lanza_mazo", "magia", "arcos", "flechas", "pesca", "escudos",
		"armadura", "alas", "monturas", "todo"
	);

	private GuideText() {
	}

	/** Long lines would run off the screen; wrap them like vanilla's item descriptions. */
	public static List<FormattedCharSequence> wrap(Font font, List<Component> tooltip) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (Component line : tooltip) {
			lines.addAll(font.split(line, 240));
		}
		return lines;
	}

	/** "Pickaxe head: Durability 250 · Mining 6 · Damage +2", each number in its rank color. */
	public static Component materialRole(PartType part, ForgeMaterial material) {
		MutableComponent line = Component.translatable("gui.forja.guia.como", part.displayName()).withColor(0xAAAAAA);
		boolean first = true;
		for (ForgeStats.Line stat : ForgeStats.partLines(part, material)) {
			if (!ForgeStats.shown(stat)) {
				continue;
			}
			line.append(Component.literal(first ? " " : " · ").withColor(0x777777));
			line.append(ForgeStats.colored(part, stat));
			first = false;
		}
		return line;
	}

	public static List<Component> materialTooltip(ForgeMaterial material) {
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(material.displayName().copy().withColor(material.color));
		if (material.canBeHead) {
			tooltip.add(materialRole(PartType.CABEZA_PICO, material));
		} else {
			tooltip.add(Component.translatable("gui.forja.guia.material_blando").withColor(0xAAAAAA));
		}
		tooltip.add(materialRole(PartType.MANGO, material));
		tooltip.add(materialRole(PartType.PLACA_PECHERA, material));
		tooltip.add(Component.translatable("gui.forja.guia.conjunto", Component.translatable("conjunto.forja." + material.getSerializedName())).withColor(0x9FD3FF));
		if (material.trait != ForgeMaterial.Trait.NONE) {
			tooltip.add(material.trait.displayName().copy().withColor(0xFFD37F));
			tooltip.add(Component.translatable("trait.forja." + material.trait.id() + ".largo"));
		}
		return tooltip;
	}

	public static List<Component> upgradeTooltip(Upgrade upgrade) {
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(upgrade.displayName().copy().withColor(upgrade.color));
		tooltip.add(Component.translatable("gui.forja.guia.para", Component.translatable("gui.forja.guia.para." + category(upgrade))).withColor(0xFFD37F));
		tooltip.add(Component.translatable("gui.forja.guia.al_maximo", upgrade.effect(100)));
		for (Upgrade.Option option : upgrade.options) {
			MutableComponent line = Component.literal("+" + option.percent() + "%: ");
			for (int i = 0; i < option.requirements().size(); i++) {
				if (i > 0) {
					line.append(" + ");
				}
				Upgrade.Requirement requirement = option.requirements().get(i);
				line.append(requirement.tag() != null
					? Component.translatable("gui.forja.guia.cualquiera", requirement.displayStack().getHoverName())
					: requirement.displayStack().getHoverName());
			}
			tooltip.add(line.withColor(0xAAAAAA));
		}
		return tooltip;
	}

	private static List<ForgeType> accepted(Upgrade upgrade) {
		List<ForgeType> accepted = new ArrayList<>();
		for (ForgeType type : ForgeType.values()) {
			if (upgrade.appliesTo(type)) {
				accepted.add(type);
			}
		}
		return accepted;
	}

	/** Which gear an upgrade goes on, as a translation suffix for "Para: ...". */
	public static String category(Upgrade upgrade) {
		List<ForgeType> accepted = accepted(upgrade);
		if (accepted.size() == 1) {
			return accepted.getFirst().id();
		}
		if (accepted.size() == ForgeType.values().length) {
			return "todo";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.ARMOR)) {
			return "armadura";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.TOOL)) {
			return accepted.size() == 6 ? "herramientas" : "algunas_herramientas";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.WEAPON || t == ForgeType.HACHA)) {
			return "armas";
		}
		return "herramientas_y_armas";
	}

	/** The book section an upgrade is listed under, one of {@link #UPGRADE_SECTIONS}. */
	public static String section(Upgrade upgrade) {
		List<ForgeType> accepted = accepted(upgrade);
		if (accepted.size() == ForgeType.values().length) {
			return "todo";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.ARMOR)) {
			return "armadura";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.SHIELD)) {
			return "escudos";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.RANGED)) {
			return "arcos";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.PESCA)) {
			return "pesca";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.MUNICION)) {
			return "flechas";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.ALAS)) {
			return "alas";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.MONTURA)) {
			return "monturas";
		}
		if (accepted.stream().allMatch(t -> t == ForgeType.BACULO || t == ForgeType.GRIMORIO)) {
			return "magia";
		}
		if (accepted.stream().allMatch(t -> t == ForgeType.LANZA || t == ForgeType.MAZO)) {
			return "lanza_mazo";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.TOOL)) {
			return "herramientas";
		}
		if (accepted.stream().allMatch(t -> t.kind == ForgeType.Kind.WEAPON || t == ForgeType.HACHA)) {
			return "armas";
		}
		return "herramientas_y_armas";
	}

	/** A typical material for showing a part: iron heads and plates, leather linings, wooden handles. */
	public static ForgeMaterial sampleMaterial(PartType part) {
		return switch (part.role) {
			case HEAD, PLATE -> ForgeMaterial.HIERRO;
			case LINING -> ForgeMaterial.CUERO;
			default -> ForgeMaterial.MADERA;
		};
	}

	public static int darken(int color) {
		int r = (color >> 16 & 255) * 3 / 5;
		int g = (color >> 8 & 255) * 3 / 5;
		int b = (color & 255) * 3 / 5;
		return r << 16 | g << 8 | b;
	}
}
