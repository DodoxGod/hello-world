package dev.forja.mixin;

import dev.forja.combat.CombatConfig;
import dev.forja.combat.CombatStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The feint: now and then a creeper hisses and swells, then stops. Once per encounter; the next fuse is real. */
@Mixin(SwellGoal.class)
abstract class SwellGoalMixin {
	@Shadow
	@Final
	private Creeper creeper;

	@Shadow
	private @Nullable LivingEntity target;

	@Unique
	private int forja$fuseTicks;

	@Unique
	private int forja$pause;

	@Unique
	private boolean forja$feintPlanned;

	@Unique
	private boolean forja$feinted;

	@Inject(method = "start", at = @At("TAIL"))
	private void forja$onStart(CallbackInfo ci) {
		forja$fuseTicks = 0;
		forja$pause = 0;
		forja$feintPlanned = false;
		forja$feinted = false;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void forja$feint(CallbackInfo ci) {
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.enabled) return;
		if (forja$pause > 0) {
			forja$pause--;
			creeper.setSwellDir(-1);
			return;
		}
		if (creeper.getSwellDir() <= 0) {
			forja$fuseTicks = 0;
			return;
		}
		if (forja$fuseTicks++ == 0) {
			forja$feintPlanned = !forja$feinted && target instanceof Player && creeper.getRandom().nextDouble() < cfg.creeperFeintChance;
		}
		if (forja$feintPlanned && forja$fuseTicks >= cfg.creeperFeintAtTicks) {
			creeper.setSwellDir(-1);
			forja$pause = cfg.creeperFeintPauseTicks;
			forja$feintPlanned = false;
			forja$feinted = true;
			forja$fuseTicks = 0;
			CombatStats.record(creeper, CombatStats.FEINT);
		}
	}
}
