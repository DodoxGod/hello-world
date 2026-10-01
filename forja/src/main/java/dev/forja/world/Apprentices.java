package dev.forja.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;

/**
 * The fallen smith's apprentices: ordinary elite wither skeletons, told apart by a tag and handed the
 * one goal that makes them his — {@link dev.forja.entity.ai.DefendMasterGoal}.
 *
 * <p>A goal is not saved with the mob, so it is handed out again every time one of them loads.
 */
public final class Apprentices {
	/** The tag every apprentice carries. */
	public static final String TAG = "forja_aprendiz";

	private Apprentices() {
	}

	/** The tag of an apprentice still coming up out of the ground, and the one that remembers the floor it is coming up to. */
	public static final String RISING = "forja_sube";
	private static final String FLOOR = "forja_suelo_";

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && isApprentice(mob)) {
				guard(mob);
				// Caught half out of the ground by a save: the smith who was lifting it does not remember it,
				// so it is set on its feet at once.
				if (mob.entityTags().contains(RISING)) {
					for (String tag : java.util.List.copyOf(mob.entityTags())) {
						if (tag.startsWith(FLOOR)) {
							mob.setPos(mob.getX(), Double.parseDouble(tag.substring(FLOOR.length())), mob.getZ());
						}
					}
					unbury(mob);
				}
			}
		});
	}

	/** Puts a freshly called apprentice below the floor, still and untouchable, ready to come up. */
	public static void bury(Mob mob, double floor) {
		mob.setNoAi(true);
		mob.setInvulnerable(true);
		mob.setNoGravity(true);
		mob.noPhysics = true;
		mob.addTag(RISING);
		mob.addTag(FLOOR + floor);
	}

	/** Out of the ground: it moves, falls and can be hurt like anything else. */
	public static void unbury(Mob mob) {
		mob.setNoAi(false);
		mob.setInvulnerable(false);
		mob.setNoGravity(false);
		mob.noPhysics = false;
		for (String tag : java.util.List.copyOf(mob.entityTags())) {
			if (tag.equals(RISING) || tag.startsWith(FLOOR)) {
				mob.removeTag(tag);
			}
		}
	}

	/** Whether it is still coming up. */
	public static boolean rising(Mob mob) {
		return mob.entityTags().contains(RISING);
	}

	public static boolean isApprentice(Mob mob) {
		return mob.entityTags().contains(TAG);
	}

	/** Makes a freshly summoned mob one of his: tagged, and set to stand by him. */
	public static void enlist(Mob mob) {
		mob.addTag(TAG);
		guard(mob);
	}

	private static void guard(Mob mob) {
		var targets = ((dev.forja.mixin.MobGoalsAccess) mob).forjaTargets();
		boolean already = targets.getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal() instanceof dev.forja.entity.ai.DefendMasterGoal);
		if (!already) {
			targets.addGoal(0, new dev.forja.entity.ai.DefendMasterGoal(mob));
		}
	}
}
