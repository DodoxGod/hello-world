package dev.forja.entity.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.raid.Raider;

/**
 * Cerrar filas: the captain winds the horn and the band comes on faster and hits harder for a while.
 *
 * <p>It is a captain's move rather than a fighter's one: on his own it does nothing at all, and it is
 * what makes killing him first the right answer instead of a preference.
 */
public class RallyGoal extends Goal {
	private final Mob captain;
	private final double reach;
	private final int cooldown;
	private final int ticks;
	private int wait;

	public RallyGoal(Mob captain, double reach, int cooldown, int ticks) {
		this.captain = captain;
		this.reach = reach;
		this.cooldown = cooldown;
		this.ticks = ticks;
		this.wait = cooldown / 3;
	}

	@Override
	public boolean canUse() {
		if (this.wait > 0) {
			this.wait--;
			return false;
		}
		return this.captain.getTarget() != null && this.captain.level() instanceof ServerLevel level
			&& !level.getEntitiesOfClass(Raider.class, this.captain.getBoundingBox().inflate(this.reach),
				other -> other != this.captain && other.isAlive()).isEmpty();
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		this.wait = this.cooldown;
		if (!(this.captain.level() instanceof ServerLevel level)) {
			return;
		}
		level.playSound(null, this.captain.getX(), this.captain.getY(), this.captain.getZ(),
			SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0F, 1.2F);
		// The horn, drawn: a pale ring out to as far as the band hears it, low and quick.
		dev.forja.entity.Shockwave.burst(level, this.captain.position(), this.reach, 10, dev.forja.entity.Shockwave.RALLY, 0.25F);
		level.sendParticles(ParticleTypes.CRIT, this.captain.getX(), this.captain.getY(1.2), this.captain.getZ(), 12, 0.5, 0.3, 0.5, 0.05);
		for (Raider raider : level.getEntitiesOfClass(Raider.class, this.captain.getBoundingBox().inflate(this.reach),
			other -> other != this.captain && other.isAlive())) {
			raider.addEffect(new MobEffectInstance(MobEffects.SPEED, this.ticks, 0));
			raider.addEffect(new MobEffectInstance(MobEffects.STRENGTH, this.ticks, 0));
			if (raider.getTarget() == null) {
				raider.setTarget(this.captain.getTarget());
			}
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, raider.getX(), raider.getY(1.6), raider.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
		}
	}
}
