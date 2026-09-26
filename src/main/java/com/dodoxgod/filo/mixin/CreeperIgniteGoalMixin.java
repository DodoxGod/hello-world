package com.dodoxgod.filo.mixin;

import com.dodoxgod.filo.combat.CombatStats;
import com.dodoxgod.filo.config.FiloConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.CreeperIgniteGoal;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Finta: a veces el creeper sisea y se hincha, pero se detiene antes de explotar. Solo una vez por
 * encuentro: la siguiente mecha es de verdad.
 */
@Mixin(CreeperIgniteGoal.class)
public abstract class CreeperIgniteGoalMixin {
	@Shadow
	@Final
	private CreeperEntity creeper;

	@Shadow
	@Nullable
	private LivingEntity target;

	@Unique
	private int filo$fuseTicks;

	@Unique
	private int filo$pause;

	@Unique
	private boolean filo$feintPlanned;

	@Unique
	private boolean filo$feinted;

	@Inject(method = "start", at = @At("TAIL"))
	private void filo$onStart(CallbackInfo ci) {
		filo$fuseTicks = 0;
		filo$pause = 0;
		filo$feintPlanned = false;
		filo$feinted = false;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void filo$feint(CallbackInfo ci) {
		FiloConfig.Mobs cfg = FiloConfig.get().mobs;
		if (filo$pause > 0) {
			filo$pause--;
			creeper.setFuseSpeed(-1);
			return;
		}
		if (creeper.getFuseSpeed() <= 0) {
			filo$fuseTicks = 0;
			return;
		}
		if (filo$fuseTicks++ == 0) {
			filo$feintPlanned = !filo$feinted && target instanceof PlayerEntity
					&& creeper.getRandom().nextDouble() < cfg.creeperFeintChance;
		}
		if (filo$feintPlanned && filo$fuseTicks >= cfg.creeperFeintAtTicks) {
			creeper.setFuseSpeed(-1);
			filo$pause = cfg.creeperFeintPauseTicks;
			filo$feintPlanned = false;
			filo$feinted = true;
			filo$fuseTicks = 0;
			CombatStats.record(creeper, CombatStats.FEINT);
		}
	}
}
