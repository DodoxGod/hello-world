package dev.forja.difficulty;

import dev.forja.combat.CombatConfig;
import net.minecraft.world.entity.LivingEntity;

/** The mod's bosses, which play by their own rules: the tightest hit cap, and finishers only late in the fight. */
public final class Bosses {
	private Bosses() {
	}

	public static boolean isBoss(LivingEntity entity) {
		return entity instanceof dev.forja.entity.FallenSmith;
	}

	/** Whether a boss is far enough gone that a finisher may land on it. Everyone else: always. */
	public static boolean finishable(LivingEntity entity) {
		return !isBoss(entity) || entity.getHealth() <= entity.getMaxHealth() * CombatConfig.get().bossFinisherHealth;
	}

	/**
	 * Whether a player is behind whoever struck a blow.
	 *
	 * <p>Takes the {@link net.minecraft.world.damagesource.DamageSource#getEntity() owner} of a blow, so
	 * a player's arrow, trident or lit TNT already arrives here as the player. A tamed animal (a wolf, a
	 * cat, a horse) counts as whoever tamed it: setting your wolves on a boss is still you fighting it —
	 * even with its owner logged off or in another world, which is why an owner that cannot be looked up
	 * still counts for a tamed animal. A vex belongs to its evoker, not to anybody. An iron golem has no
	 * owner, even one a player built, and it is the classic way to have something else do the fight.
	 */
	public static boolean fromPlayer(net.minecraft.world.entity.Entity attacker) {
		if (attacker instanceof net.minecraft.world.entity.player.Player) {
			return true;
		}
		if (attacker instanceof net.minecraft.world.entity.OwnableEntity pet && pet.getOwnerReference() != null) {
			net.minecraft.world.entity.LivingEntity owner = pet.getRootOwner();
			return owner instanceof net.minecraft.world.entity.player.Player
				|| owner == null && attacker instanceof net.minecraft.world.entity.TamableAnimal;
		}
		return false;
	}

	/**
	 * What a blow on a boss is worth, by who struck it: full from a player (or their pet, or their arrow),
	 * {@link CombatConfig#jefeDanoAjeno} from any other creature. A blow with nobody behind it at all —
	 * lava, a fall, the player's own lightning and frost procs, which carry no attacker — is left alone:
	 * those are either the player's doing already or not a way of cheating the fight.
	 */
	public static float othersShare(net.minecraft.world.damagesource.DamageSource source) {
		net.minecraft.world.entity.Entity attacker = source.getEntity();
		if (attacker == null || fromPlayer(attacker)) {
			return 1.0F;
		}
		return (float) Math.max(0.0, CombatConfig.get().jefeDanoAjeno);
	}
}
