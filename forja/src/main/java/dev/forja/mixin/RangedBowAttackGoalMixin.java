package dev.forja.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.forja.combat.ChargedArrows;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.CombatFeedback;
import dev.forja.combat.CombatStats;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The charged shot: every few shots a skeleton draws for longer, flashes just before it lets go, and
 * the arrow hits harder and goes further through armor.
 */
@Mixin(RangedBowAttackGoal.class)
abstract class RangedBowAttackGoalMixin {
	@Shadow
	@Final
	private Monster mob;

	@Unique
	private int forja$shots;

	@Unique
	private boolean forja$flashed;

	@Unique
	private boolean forja$chargedShot() {
		CombatConfig cfg = CombatConfig.get();
		return cfg.enabled && cfg.skeletonChargedEvery > 0 && (forja$shots + 1) % cfg.skeletonChargedEvery == 0;
	}

	/** Vanilla lets go once the bow has been drawn for 20 ticks: a charged shot takes longer. */
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Monster;getTicksUsingItem()I"))
	private int forja$chargedDraw(int ticks) {
		// Covering fire: with one of its own in the way, it holds the draw until the line is clear.
		if (ticks >= 19 && mob.getTarget() instanceof net.minecraft.world.entity.player.Player player
			&& dev.forja.ai.Squad.allyInLine(mob, player)) {
			ticks = Math.min(ticks, 19 + (forja$chargedShot() ? Math.max(0, CombatConfig.get().chargedExtraDrawTicks) : 0));
			return forja$chargedShot() ? ticks - Math.max(0, CombatConfig.get().chargedExtraDrawTicks) : ticks;
		}
		if (!forja$chargedShot()) return ticks;
		int extra = Math.max(0, CombatConfig.get().chargedExtraDrawTicks);
		if (!forja$flashed && ticks >= 10 + extra) {
			forja$flashed = true;
			CombatFeedback.chargedShotTelegraph(mob);
		}
		return ticks - extra;
	}

	@Inject(method = "tick", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/world/entity/monster/RangedAttackMob;performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V"))
	private void forja$afterShot(CallbackInfo ci) {
		if (forja$chargedShot()) {
			mob.level().getEntitiesOfClass(AbstractArrow.class, mob.getBoundingBox().inflate(3.0),
				arrow -> arrow.getOwner() == mob && arrow.tickCount == 0).forEach(ChargedArrows::mark);
			CombatStats.record(mob, CombatStats.CHARGED_SHOT);
		}
		forja$shots++;
		forja$flashed = false;
	}
}
