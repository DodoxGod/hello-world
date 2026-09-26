package dev.forja.entity.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Segundo aliento: once, and only once, a wounded elite gets back up.
 *
 * <p>It fires at a share of its health rather than on a timer, so it always lands at the point in the
 * fight where you thought you had it. One use per mob: a second one would just be a longer fight.
 */
public class SecondWindGoal extends Goal {
	private final Mob mob;
	private final float share;
	private final float mend;
	private final int ticks;
	private boolean spent;

	public SecondWindGoal(Mob mob, float share, float mend, int ticks) {
		this.mob = mob;
		this.share = share;
		this.mend = mend;
		this.ticks = ticks;
	}

	@Override
	public boolean canUse() {
		return !this.spent && this.mob.getTarget() != null
			&& this.mob.getHealth() / this.mob.getMaxHealth() <= this.share;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		this.spent = true;
		this.mob.heal(this.mob.getMaxHealth() * this.mend);
		this.mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, this.ticks, 0));
		this.mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, this.ticks, 0));
		this.mob.addEffect(new MobEffectInstance(MobEffects.SPEED, this.ticks, 0));
		if (this.mob.level() instanceof ServerLevel level) {
			level.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
				SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 1.4F, 0.7F);
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, this.mob.getX(), this.mob.getY(1.0), this.mob.getZ(),
				48, 0.4, 0.6, 0.4, 0.3);
		}
	}
}
