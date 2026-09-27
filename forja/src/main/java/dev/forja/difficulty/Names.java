package dev.forja.difficulty;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

/**
 * The names of the monsters worth naming. "Quita el nombre feo y un nombre bonito a los veteranos, élites y
 * campeones": "Zombi veterano" and "Tridente de prismarina (Zombi de élite)" said what they were and nothing
 * about who; the badge over the head already says the rank, so the name is free to be a name.
 *
 * <p>A name of its own and a byname that goes with its rank: a veteran has scars to show for it, an elite a
 * reputation, a champion a legend. Picked from the monster's UUID, so the same monster keeps the same name
 * whoever looks and however often it is worked out.
 */
public final class Names {
	private static final String[] GIVEN = {
		"Karn", "Morvek", "Ulgar", "Sethra", "Vadrik", "Oskra", "Brann", "Hesk", "Draven", "Ysolde", "Grisha", "Tormund",
		"Kael", "Maugra", "Rhosk", "Veyla", "Durgan", "Zerith", "Ashka", "Borvin", "Nyx", "Garruk", "Iskra", "Thorvald",
		"Morra", "Skarn", "Velkan", "Ruska", "Jorik", "Halvard", "Sabra", "Korrin", "Ondra", "Fenrik", "Varga", "Ebrin",
		"Grom", "Lysk", "Ravna", "Tolvar", "Wulfric", "Zadra", "Belok", "Cirra", "Drask", "Myrna", "Kolt", "Eshra"
	};
	/** How many bynames each rank has in the lang files: name.forja.apodo.<rango>.<n>. */
	public static final int VETERAN_BYNAMES = 12;
	public static final int ELITE_BYNAMES = 12;
	public static final int CHAMPION_BYNAMES = 12;

	private Names() {
	}

	/** Its name for this rank: "Karn Rompehuesos", "Morvek la Hoja Gris", "Ulgar Azote de Reinos". */
	public static Component of(Mob mob, Threat threat) {
		long seed = mob.getUUID().getMostSignificantBits() ^ mob.getUUID().getLeastSignificantBits();
		String given = GIVEN[(int) Math.floorMod(seed, (long) GIVEN.length)];
		String rank = switch (threat) {
			case VETERANO -> "veterano";
			case ELITE -> "elite";
			default -> "campeon";
		};
		int count = switch (threat) {
			case VETERANO -> VETERAN_BYNAMES;
			case ELITE -> ELITE_BYNAMES;
			default -> CHAMPION_BYNAMES;
		};
		int byname = (int) Math.floorMod(seed >>> 17, (long) count);
		return Component.translatable("name.forja.nombre." + rank, given, Component.translatable("name.forja.apodo." + rank + "." + byname));
	}

	/** Gives it the name of its rank, if it has one worth naming. */
	public static void give(Mob mob, Threat threat) {
		if (threat != Threat.NORMAL) {
			mob.setCustomName(of(mob, threat));
		}
	}
}
