package dev.forja.world;

import java.util.List;

import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.UpgradeRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Legends: gear that turns up in treasure already named, with two upgrades maxed out. They are the
 * forge's own folklore, and the pair of upgrades is usually a synergy.
 */
public final class Legends {
	/** id: what it can be, the material of its head or plate, and the two upgrades it carries maxed. */
	public record Legend(String id, List<ForgeType> types, ForgeMaterial head, Upgrade first, Upgrade second) {
	}

	public static final List<Legend> ALL = List.of(
		new Legend("aliento_de_invierno", List.of(ForgeType.ESPADA, ForgeType.ESPADON), ForgeMaterial.DIAMANTE, Upgrade.ESCARCHA, Upgrade.TORMENTA),
		new Legend("sed_del_ocaso", List.of(ForgeType.ESPADA, ForgeType.DAGA), ForgeMaterial.NETHERITA, Upgrade.VAMPIRISMO, Upgrade.EJECUCION),
		new Legend("veta_madre", List.of(ForgeType.PICO, ForgeType.MARTILLO), ForgeMaterial.ESMERALDA, Upgrade.VETA, Upgrade.FORTUNA),
		new Legend("muralla", List.of(ForgeType.ESCUDO), ForgeMaterial.OBSIDIANA, Upgrade.PUAS, Upgrade.REPULSION),
		new Legend("paso_del_viento", List.of(ForgeType.BOTAS), ForgeMaterial.PURPUR, Upgrade.PRESTEZA, Upgrade.RESORTE),
		new Legend("ojo_de_halcon", List.of(ForgeType.ARCO, ForgeType.BALLESTA), ForgeMaterial.CUARZO, Upgrade.PODER, Upgrade.LLAMA),
		new Legend("yunque_andante", List.of(ForgeType.PECHERA), ForgeMaterial.NETHERITA, Upgrade.PROTECCION, Upgrade.REPRESALIA),
		// The gear the mod added later has its own legends, or the new weapons would never turn up named.
		new Legend("cadena_del_juicio", List.of(ForgeType.MANGUAL), ForgeMaterial.OBSIDIACERO, Upgrade.ATURDIMIENTO, Upgrade.SEGUNDA_CABEZA),
		new Legend("punos_del_yunque", List.of(ForgeType.GUANTELETES), ForgeMaterial.DAMASCO, Upgrade.RAFAGA, Upgrade.NUDILLOS_DE_HIERRO),
		new Legend("garra_del_abismo", List.of(ForgeType.GANCHO), ForgeMaterial.ECO, Upgrade.SIRGA, Upgrade.IRROMPIBLE),
		new Legend("alas_del_alba", List.of(ForgeType.ALAS), ForgeMaterial.ACERO_ESTELAR, Upgrade.AERODINAMICA, Upgrade.PROPULSION),
		new Legend("barda_del_invicto", List.of(ForgeType.BARDA), ForgeMaterial.OBSIDIACERO, Upgrade.PETO, Upgrade.HERRADURA),
		new Legend("marea_del_ahogado", List.of(ForgeType.TRIDENTE), ForgeMaterial.PRISMARINA, Upgrade.CORRIENTE, Upgrade.CANALIZACION),
		new Legend("filo_de_la_guadana", List.of(ForgeType.GUADANA), ForgeMaterial.ECO, Upgrade.COSECHADOR, Upgrade.SIEGA_DE_ALMAS)
	);

	private Legends() {
	}

	/** Builds one of the legends at random, with its name, its rarity and both upgrades at 100%. */
	/**
	 * A legend a mob can be handed and will actually use.
	 *
	 * <p>Fourteen legends and only nine of them are weapons: the rest are a breastplate, a pair of
	 * boots, a shield, a set of wings and a horse's barding. {@code makeElite} was dropping whichever
	 * one came up straight into the main hand, so a raider captain could turn up holding a saddle
	 * blanket and swing it at you. This picks from the ones that belong in a fist.
	 */
	public static ItemStack createWeapon(RandomSource random, HolderLookup.Provider registries) {
		return createWeapon(random, registries, Legends::heldInHand);
	}

	/**
	 * A legend this particular mob can fight with.
	 *
	 * <p>Something held is not yet something used: a zombie elite could come up with Ojo de halcón and
	 * walk at you holding a bow it cannot draw, and a skeleton handed a sword put its own bow away. So an
	 * archer gets a legend with a bow, a pillager one with a crossbow, and everything that fights up close
	 * one it swings (see {@link #swung}).
	 */
	public static ItemStack createWeaponFor(net.minecraft.world.entity.Mob mob, RandomSource random) {
		ForgeType shot = ForjaMobs.launcher(mob);
		return createWeapon(random, mob.registryAccess(), shot != null ? type -> type == shot : Legends::swung);
	}

	private static ItemStack createWeapon(RandomSource random, HolderLookup.Provider registries, java.util.function.Predicate<ForgeType> usable) {
		List<Legend> fits = new java.util.ArrayList<>();
		for (Legend legend : ALL) {
			if (legend.types().stream().anyMatch(usable)) {
				fits.add(legend);
			}
		}
		Legend legend = fits.get(random.nextInt(fits.size()));
		List<ForgeType> options = legend.types().stream().filter(usable).toList();
		return build(legend, options.get(random.nextInt(options.size())), random, registries);
	}

	/**
	 * Whether a mob fighting up close can use this: a weapon, or one of the tools that is one — an axe, a
	 * hammer. Not a bow, not the hook (a claw on a rope is for throwing), not a staff or a tome, whose magic
	 * wants the rules of entity/ai/CasterGoal and a mob born to them.
	 */
	public static boolean swung(ForgeType type) {
		return type.kind == ForgeType.Kind.WEAPON && !dev.forja.magic.Spellcasting.casts(type)
			|| type == ForgeType.HACHA || type == ForgeType.MARTILLO || type == ForgeType.PICAHACHA;
	}

	/** Whether a kind of gear is swung rather than worn. */
	public static boolean heldInHand(ForgeType type) {
		return switch (type.kind) {
			case WEAPON, TOOL, RANGED -> true;
			default -> false;
		};
	}

	public static ItemStack create(RandomSource random, HolderLookup.Provider registries) {
		Legend legend = ALL.get(random.nextInt(ALL.size()));
		return build(legend, legend.types().get(random.nextInt(legend.types().size())), random, registries);
	}

	private static ItemStack build(Legend legend, ForgeType type, RandomSource random, HolderLookup.Provider registries) {
		ItemStack stack = ForjaLoot.gear(random, registries, type, legend.head(), 0.0F);
		for (Upgrade upgrade : List.of(legend.first(), legend.second())) {
			if (upgrade.appliesTo(type)) {
				stack = UpgradeRecipes.upgraded(stack, type, upgrade, 100, registries);
			}
		}
		stack.set(dev.forja.registry.ModComponents.LEYENDA, legend.id());
		// A legend has nothing left to prove.
		stack.set(dev.forja.registry.ModComponents.POTENCIAL, dev.forja.forge.Potential.MOST);
		stack.set(DataComponents.ITEM_NAME, Component.translatable("legend.forja." + legend.id()));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		stack.set(DataComponents.TOOLTIP_STYLE, dev.forja.Forja.id("leyenda"));
		// A legend is old, not worn out.
		stack.setDamageValue(Math.round(stack.getMaxDamage() * 0.15F));
		return stack;
	}
}
