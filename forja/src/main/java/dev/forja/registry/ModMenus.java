package dev.forja.registry;

import dev.forja.Forja;
import dev.forja.menu.ForgeMenu;
import dev.forja.menu.Station;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<ForgeMenu> FORGE = Registry.register(
		BuiltInRegistries.MENU, Forja.id("mesa_de_forja"), new MenuType<>((id, inventory) -> new ForgeMenu(Station.FORJA, id, inventory), FeatureFlags.VANILLA_SET)
	);
	/**
	 * The greater table's own type.
	 *
	 * <p>A MenuType carries a factory that the <b>client</b> runs to build its copy of the menu, and
	 * that factory has no idea which block was clicked. Sharing one type between the two forge tables
	 * therefore meant the client built {@code Station.FORJA} either way, so standing at the greater
	 * table you were told a greatsword needed the greater table.
	 */
	public static final MenuType<ForgeMenu> FORGE_MAYOR = Registry.register(
		BuiltInRegistries.MENU, Forja.id("mesa_de_forja_mayor"),
		new MenuType<>((id, inventory) -> new ForgeMenu(Station.FORJA_MAYOR, id, inventory), FeatureFlags.VANILLA_SET)
	);

	public static final MenuType<ForgeMenu> PARTS = Registry.register(
		BuiltInRegistries.MENU, Forja.id("mesa_de_piezas"), new MenuType<>((id, inventory) -> new ForgeMenu(Station.PIEZAS, id, inventory), FeatureFlags.VANILLA_SET)
	);

	public static final MenuType<ForgeMenu> TALABARTERIA = Registry.register(
		BuiltInRegistries.MENU, Forja.id("mesa_de_talabarteria"),
		new MenuType<>((id, inventory) -> new ForgeMenu(Station.TALABARTERIA, id, inventory), FeatureFlags.VANILLA_SET)
	);

	public static final MenuType<dev.forja.menu.CrucibleMenu> CRISOL = Registry.register(
		BuiltInRegistries.MENU, Forja.id("crisol"),
		new MenuType<>(dev.forja.menu.CrucibleMenu::new, FeatureFlags.VANILLA_SET)
	);

	public static final MenuType<dev.forja.menu.CastingBoxMenu> CAJA = Registry.register(
		BuiltInRegistries.MENU, Forja.id("caja_de_moldeo"),
		new MenuType<>(dev.forja.menu.CastingBoxMenu::new, FeatureFlags.VANILLA_SET)
	);

	/** The parts cabinet: a chest in size, but with slots that only take a smith's things. */
	public static final MenuType<dev.forja.menu.CabinetMenu> ARMARIO = Registry.register(
		BuiltInRegistries.MENU, Forja.id("armario_de_piezas"),
		new MenuType<>(dev.forja.menu.CabinetMenu::new, FeatureFlags.VANILLA_SET)
	);

	/** The extraction table: one upgrade off a piece, kept in an orb or not. */
	public static final MenuType<dev.forja.menu.ExtractionMenu> EXTRACCION = Registry.register(
		BuiltInRegistries.MENU, Forja.id("mesa_de_extraccion"),
		new MenuType<>(dev.forja.menu.ExtractionMenu::new, FeatureFlags.VANILLA_SET)
	);

	private ModMenus() {
	}

	public static void init() {
	}
}
