package dev.forja.menu;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import dev.forja.forge.ForgeType;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModMenus;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;

/**
 * The work tables. The parts table cuts parts from materials with a template and salvages finished gear
 * back into parts; the two forge tables assemble parts into gear and upgrade it on their star; the
 * saddlery does mounts and nothing else. They all share one menu class, which enables only the tabs of
 * its own table and, now, only the things that table is able to build.
 */
public enum Station implements StringRepresentable {
	PIEZAS(List.of(ForgeMenu.MODE_PARTS, ForgeMenu.MODE_DISASSEMBLE)),
	/** The bench every smith starts with: tools, a sword, a bow and a suit of armour. */
	FORJA(List.of(ForgeMenu.MODE_FORGE, ForgeMenu.MODE_TECHNIQUES)),
	/**
	 * The greater table: the same star, and everything the first one would not attempt.
	 *
	 * <p>The split is not about difficulty, it is about <em>reach</em>. A bench will put a pickaxe
	 * together for you on your first day; a greatsword, a flail, a gauntlet or a pair of wings are the
	 * reason you build the second one.
	 */
	FORJA_MAYOR(List.of(ForgeMenu.MODE_FORGE, ForgeMenu.MODE_TECHNIQUES)),
	/** The saddlery: the same star, but the only table that puts plate on a horse or a wolf. */
	TALABARTERIA(List.of(ForgeMenu.MODE_FORGE));

	/**
	 * What the plain bench will assemble. Everything else needs the greater table.
	 *
	 * <p>These are the fourteen a smith needs to be equipped: the tools, the two short blades, the bow,
	 * arrows, a rod and the four pieces of armour. What is missing from the list is everything you would
	 * choose on purpose — the two-handed weapons, the reach weapons, the shield, the wings.
	 */
	public static final java.util.Set<ForgeType> BENCH = java.util.Collections.unmodifiableSet(
		java.util.EnumSet.of(ForgeType.PICO, ForgeType.HACHA, ForgeType.PALA, ForgeType.AZADA,
			ForgeType.CINCEL, ForgeType.ESPADA, ForgeType.DAGA, ForgeType.FLECHA, ForgeType.CANA,
			ForgeType.ARCO, ForgeType.CASCO, ForgeType.PECHERA, ForgeType.GREBAS, ForgeType.BOTAS));

	/**
	 * How far this table can take an upgrade. The first bench stops half way; the rest of an upgrade is
	 * work for the greater table. The saddlery goes all the way because nothing else will touch barding.
	 * A table never lowers what is already on a piece: past its reach it simply does nothing.
	 */
	public int capacity() {
		return this == FORJA ? 50 : 100;
	}

	/** Whether this table will put that together at all. */
	public boolean canForge(ForgeType type) {
		if (type.kind == ForgeType.Kind.MONTURA) {
			// Barding belongs to the saddlery, and nothing else does.
			return this == TALABARTERIA;
		}
		return switch (this) {
			case FORJA -> BENCH.contains(type);
			case FORJA_MAYOR -> true;
			default -> false;
		};
	}

	/** Whether the greater table would manage what this one will not. */
	public boolean needsGreater(ForgeType type) {
		return this == FORJA && !this.canForge(type) && FORJA_MAYOR.canForge(type);
	}

	public static final Codec<Station> CODEC = StringRepresentable.fromEnum(Station::values);

	/** Tabs in display order; the first one is open when the table is used. */
	public final List<Integer> modes;

	Station(List<Integer> modes) {
		this.modes = modes;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String getSerializedName() {
		return this.id();
	}

	public Component title() {
		return Component.translatable("container.forja.mesa_de_" + this.id());
	}

	public MenuType<ForgeMenu> menuType() {
		return switch (this) {
			case FORJA -> ModMenus.FORGE;
			case FORJA_MAYOR -> ModMenus.FORGE_MAYOR;
			case TALABARTERIA -> ModMenus.TALABARTERIA;
			default -> ModMenus.PARTS;
		};
	}

	public Block block() {
		return switch (this) {
			case FORJA -> ModBlocks.MESA_DE_FORJA;
			case FORJA_MAYOR -> ModBlocks.MESA_DE_FORJA_MAYOR;
			case TALABARTERIA -> ModBlocks.MESA_DE_TALABARTERIA;
			default -> ModBlocks.MESA_DE_PIEZAS;
		};
	}

	/** Whether this table is the one that works on mounts, which no other table will. */
	public boolean forMounts() {
		return this == TALABARTERIA;
	}
}
