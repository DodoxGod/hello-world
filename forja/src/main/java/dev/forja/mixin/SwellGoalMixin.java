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

/**
 * The feint: now and then a creeper hisses and swells, then stops. Once per encounter; the next fuse is real.
 * Only a creeper on its own feints (no ally after the same target): in a group the feint only cost it the kill (the simulator, 2026-09-29).
 *
 * <p>And the commitment (PROPUESTAS_IA_SIMULADOR.md 2.5): lit, it keeps walking at the player at half speed
 * instead of standing still, and once the fuse has burnt {@link #COMMIT} ticks it only lets it go out beyond
 * {@link #COMMIT_REACH} blocks, not vanilla's 7. A player who simply stepped back put out nine fuses in ten.
 */
@Mixin(SwellGoal.class)
abstract class SwellGoalMixin {
	@Shadow
	@Final
	private Creeper creeper;

	@Shadow
	private @Nullable LivingEntity target;

	@Unique
	private static final int COMMIT = 15;

	@Unique
	private static final double COMMIT_REACH = 9.0;

	@Unique
	private static final double LIT_SPEED = 0.5;

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

	// every return, not only the last: vanilla puts the fuse out and leaves, and the commitment has to see that
	@Inject(method = "tick", at = @At("RETURN"))
	private void forja$feint(CallbackInfo ci) {
		CombatConfig cfg = CombatConfig.get();
		// the feint and the commitment are Forja's rules: below Normal a creeper is vanilla's (Ladder)
		if (!cfg.enabled || !dev.forja.difficulty.Ladder.thinks(creeper)) return;
		if (forja$pause > 0) {
			forja$pause--;
			creeper.setSwellDir(-1);
			return;
		}
		// before the rest: vanilla has just put the fuse out if the player is past 7, and a burnt one is relit here
		forja$commit();
		if (creeper.getSwellDir() <= 0) {
			forja$fuseTicks = 0;
			return;
		}
		if (forja$fuseTicks++ == 0) {
			forja$feintPlanned = !forja$feinted && target instanceof Player && creeper.getRandom().nextDouble() < cfg.creeperFeintChance
				&& dev.forja.ai.ObsM1.allies(creeper).stream().noneMatch(ally -> ally.getTarget() == target);
		}
		if (forja$feintPlanned && forja$fuseTicks >= cfg.creeperFeintAtTicks) {
			creeper.setSwellDir(-1);
			forja$pause = cfg.creeperFeintPauseTicks;
			forja$feintPlanned = false;
			forja$feinted = true;
			forja$fuseTicks = 0;
			CombatStats.record(creeper, CombatStats.FEINT);
			dev.forja.ai.HopBack.afterFeint(creeper, target);
		}
	}

	/** Lit: on at the player at half speed, and a fuse well under way is not given up for a step back. */
	@Unique
	private void forja$commit() {
		if (target == null || !target.isAlive()) {
			return;
		}
		boolean lit = creeper.getSwellDir() > 0;
		boolean burnt = ((CreeperAiAccess) creeper).forja$swell() >= COMMIT;
		if (!lit && burnt && creeper.distanceToSqr(target) < COMMIT_REACH * COMMIT_REACH && creeper.getSensing().hasLineOfSight(target)) {
			creeper.setSwellDir(1);
			lit = true;
		}
		if (lit && creeper.distanceToSqr(target) > 1.5 * 1.5) {
			creeper.getNavigation().moveTo(target, LIT_SPEED);
		}
	}
}
