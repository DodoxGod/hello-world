package dev.forja.mixin;

import dev.forja.combat.LungeGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zombies and husks get the lunge, ahead of their plain melee attack. */
@Mixin(Zombie.class)
abstract class ZombieLungeMixin extends Monster {
	protected ZombieLungeMixin(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	@Inject(method = "addBehaviourGoals", at = @At("TAIL"))
	private void forja$addLunge(CallbackInfo ci) {
		this.goalSelector.addGoal(1, new LungeGoal(this));
	}
}
