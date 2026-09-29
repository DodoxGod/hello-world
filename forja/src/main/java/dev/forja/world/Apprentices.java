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

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && isApprentice(mob)) {
				guard(mob);
			}
		});
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
