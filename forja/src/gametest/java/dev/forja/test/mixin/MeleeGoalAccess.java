package dev.forja.test.mixin;

import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The melee goal's wait before its next blow (CapitanMedidaGameTests: why a mob in reach does not warn). */
@Mixin(MeleeAttackGoal.class)
public interface MeleeGoalAccess {
	@Accessor("ticksUntilNextAttack")
	int forja$ticksUntilNextAttack();

	@Invoker("resetAttackCooldown")
	void forja$resetAttackCooldown();
}
