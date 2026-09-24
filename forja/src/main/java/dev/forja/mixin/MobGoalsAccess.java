package dev.forja.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches a mob's goal list from outside the class.
 *
 * <p>Elites and raider captains are ordinary vanilla monsters that Forja dresses up after they spawn,
 * and the only way to give one of those a move of its own is to add a goal to it. `goalSelector` is
 * protected, so it has to come through here; see world/Elites and world/ForgeRaiders.
 */
@Mixin(Mob.class)
public interface MobGoalsAccess {
	@Accessor("goalSelector")
	GoalSelector forjaGoals();
}
