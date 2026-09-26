package dev.forja.registry;

import dev.forja.Forja;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The three things the mod throws into the air that are its own.
 *
 * <p>Everything the mod drew before this was borrowed: vanilla flame for a forge, vanilla soul for a
 * dead suit of armour, vanilla cloud for a boiler. It reads fine and it is nobody's own — walk into a
 * ruined forge and it looks like a nether portal that got knocked over. These are small and there are
 * only three, because three that belong to us do more than a dozen that do not: a spark off struck
 * metal, the ash that settles where a forge burned out, and whatever is left of the person inside the
 * armour when the armour gives up.
 */
public final class ModParticles {
	/** Chispa: struck metal. Bright, heavy, gone in half a second. */
	public static final SimpleParticleType CHISPA = register("chispa");

	/** Ceniza: what a forge leaves behind. Drifts sideways more than it falls. */
	public static final SimpleParticleType CENIZA = register("ceniza");

	/** Alma: the thing inside the plate, on its way out of it. */
	public static final SimpleParticleType ALMA = register("alma");

	/**
	 * Vapor: water that met something hot. A boiler venting, a blade going into the trough. The three
	 * above were joined by this one because vanilla's cloud is a cloud — round, slow, going nowhere —
	 * and steam is none of those: it leaves fast, spreads as it climbs and comes apart on the way.
	 */
	public static final SimpleParticleType VAPOR = register("vapor");

	/**
	 * Gota: a bead of molten metal, falling. It takes its colour from the three numbers a particle is
	 * normally thrown with, read as red, green and blue from 0 to 1, because what drips from a spout is
	 * the metal in the run and a drip of gold should not look like a drip of iron.
	 */
	public static final SimpleParticleType GOTA = register("gota");

	private ModParticles() {
	}

	private static SimpleParticleType register(String name) {
		// Not "always show": these are detail, and a player who has turned particles down has said so.
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Forja.id(name), FabricParticleTypes.simple());
	}

	public static void init() {
	}
}
