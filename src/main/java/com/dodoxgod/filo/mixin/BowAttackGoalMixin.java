package com.dodoxgod.filo.mixin;

import com.dodoxgod.filo.combat.ChargedArrows;
import com.dodoxgod.filo.combat.CombatFeedback;
import com.dodoxgod.filo.combat.CombatStats;
import com.dodoxgod.filo.config.FiloConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.ai.goal.BowAttackGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disparo cargado: cada N disparos el esqueleto tensa el arco más tiempo, brilla justo antes de
 * soltarlo y la flecha hace más daño y atraviesa más armadura.
 */
@Mixin(BowAttackGoal.class)
public abstract class BowAttackGoalMixin {
	@Shadow
	@Final
	private HostileEntity actor;

	@Unique
	private int filo$shots;

	@Unique
	private boolean filo$flashed;

	@Unique
	private boolean filo$isChargedShot() {
		int every = FiloConfig.get().mobs.skeletonChargedEvery;
		return every > 0 && (filo$shots + 1) % every == 0;
	}

	/** Vanilla dispara cuando el tiempo tensando llega a 20: en el cargado hacemos que tarde más. */
	@ModifyExpressionValue(method = "tick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/mob/HostileEntity;getItemUseTime()I"))
	private int filo$chargedDraw(int useTime) {
		if (!filo$isChargedShot()) return useTime;
		int extra = Math.max(0, FiloConfig.get().mobs.chargedExtraDrawTicks);
		if (!filo$flashed && useTime >= 10 + extra) {
			filo$flashed = true;
			CombatFeedback.chargedShotTelegraph(actor);
		}
		return useTime - extra;
	}

	@Inject(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/ai/RangedAttackMob;shootAt(Lnet/minecraft/entity/LivingEntity;F)V",
			shift = At.Shift.AFTER))
	private void filo$afterShot(CallbackInfo ci) {
		if (filo$isChargedShot()) {
			actor.getWorld().getEntitiesByClass(PersistentProjectileEntity.class, actor.getBoundingBox().expand(3.0),
					arrow -> arrow.getOwner() == actor && arrow.age == 0).forEach(ChargedArrows::mark);
			CombatStats.record(actor, CombatStats.CHARGED_SHOT);
		}
		filo$shots++;
		filo$flashed = false;
	}
}
