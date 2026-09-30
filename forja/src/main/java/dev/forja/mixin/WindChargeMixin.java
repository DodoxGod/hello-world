package dev.forja.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A wind charge a monster throws (ai/MobItems, v4's object 7) pushes and lifts whoever it hits, as vanilla's does, but
 * leaves blocks alone: a player's wind charge opens doors and trapdoors and presses buttons (the explosion's TRIGGER),
 * and a closed base has to stay closed (Andy, 2026-09-29: "una base segura no se debe poder abrir"). A player's own
 * wind charges, and the breeze's (another class), are as vanilla made them.
 */
@Mixin(WindCharge.class)
abstract class WindChargeMixin {
	@ModifyArg(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V"), index = 8)
	private Level.ExplosionInteraction forja$monstersTriggerNothing(Level.ExplosionInteraction interaction) {
		return ((WindCharge) (Object) this).getOwner() instanceof Mob ? Level.ExplosionInteraction.NONE : interaction;
	}
}
