package dev.forja.world;

import dev.forja.entity.BrokenMould;
import dev.forja.entity.CoalHauler;
import dev.forja.entity.CuneGuardian;
import dev.forja.entity.EmberWisp;
import dev.forja.entity.FallenSmith;
import dev.forja.entity.ForgeAutomaton;
import dev.forja.entity.GreaterEmber;
import dev.forja.entity.HollowArmor;
import dev.forja.entity.LivingSlag;
import dev.forja.entity.Quencher;
import dev.forja.entity.RustSwarm;
import dev.forja.entity.StarCore;
import dev.forja.entity.Striker;
import dev.forja.entity.Tongs;
import dev.forja.entity.WalkingAnvil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The mod's own monsters do not hurt each other.
 *
 * <p>They were, and it was ruining the one fight it mattered most in: the fallen smith calls up four
 * apprentices, then swings an anvil wave that catches everything within nine blocks — including them
 * — and they hit back. Andy watched his own apprentices finish him off. Nothing about that reads as a
 * fight; it reads as a bug, because it is one.
 *
 * <p>It matters more now than it used to. Half the new mobs are <b>area</b> attacks: the hauler's
 * blast, the guardian's stamp, the slag's pools, the quencher's oil. A mod where the monsters group
 * up and then delete each other with their own signature moves is a mod where the interesting fights
 * never happen.
 *
 * <p>It covers the Nucleo Estelar in both directions, which took a failing test to get right: it was
 * absorbing blows from our own mobs and then unable to give them back, because its own
 * {@code hurtServer} override answers before the blow ever reaches this rule.
 */
public final class Truce {
	private Truce() {
	}

	/** Whether an entity belongs to Forja's side of the world. */
	public static boolean ours(Entity entity) {
		return entity instanceof FallenSmith
			|| entity instanceof ForgeAutomaton
			|| entity instanceof HollowArmor
			|| entity instanceof EmberWisp
			|| entity instanceof GreaterEmber
			|| entity instanceof RustSwarm
			|| entity instanceof LivingSlag
			|| entity instanceof WalkingAnvil
			|| entity instanceof Striker
			|| entity instanceof Tongs
			|| entity instanceof CoalHauler
			|| entity instanceof Quencher
			|| entity instanceof StarCore
			|| entity instanceof BrokenMould
			|| entity instanceof CuneGuardian
			// The elites and the raid captains are vanilla monsters wearing our gear, and they get the
			// same deal: they are on this side of the fight and they are the ones with the area moves.
			|| entity.entityTags().contains("forja_elite")
			|| entity.entityTags().contains("forja_capitan");
	}

	/**
	 * Whether a blow should simply not happen.
	 *
	 * <p>Reads the {@link DamageSource#getEntity() owner} rather than the direct entity, so an arrow,
	 * a thrown head or a blast still counts as coming from whoever set it off.
	 */
	public static boolean blocks(LivingEntity victim, DamageSource source) {
		Entity attacker = source.getEntity();
		return attacker != null && attacker != victim && ours(victim) && ours(attacker);
	}
}
