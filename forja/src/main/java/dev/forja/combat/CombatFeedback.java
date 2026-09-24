package dev.forja.combat;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** The particles and sounds that make every dangerous moment readable before it lands. */
public final class CombatFeedback {
	private CombatFeedback() {
	}

	/** A mob is about to swing. */
	public static void telegraph(Entity mob) {
		particles(mob, ParticleTypes.CRIT, mob.getEyeY() + 0.35, 6, 0.25);
		sound(mob, SoundEvents.PLAYER_ATTACK_WEAK, 0.8F, 0.55F);
	}

	/** A mob crouches to leap. */
	public static void lungeTelegraph(Entity mob) {
		particles(mob, ParticleTypes.POOF, mob.getY() + 0.2, 8, 0.35);
		sound(mob, SoundEvents.RAVAGER_STEP, 0.7F, 1.4F);
	}

	/** A skeleton is about to loose a charged shot. */
	public static void chargedShotTelegraph(Entity mob) {
		particles(mob, ParticleTypes.ENCHANTED_HIT, mob.getEyeY(), 12, 0.3);
		sound(mob, SoundEvents.EVOKER_PREPARE_ATTACK, 0.8F, 1.6F);
	}

	public static void stagger(Entity entity) {
		particles(entity, ParticleTypes.CRIT, entity.getEyeY() + 0.2, 20, 0.5);
		sound(entity, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
	}

	public static void guardBreak(Entity entity) {
		particles(entity, ParticleTypes.CLOUD, entity.getEyeY() - 0.4, 8, 0.3);
		sound(entity, SoundEvents.ANVIL_LAND, 0.5F, 0.7F);
	}

	public static void dodge(Entity entity) {
		particles(entity, ParticleTypes.CLOUD, entity.getY() + 0.1, 6, 0.3);
		sound(entity, SoundEvents.PLAYER_ATTACK_SWEEP, 0.4F, 1.8F);
	}

	public static void dodgedHit(Entity entity) {
		sound(entity, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8F, 1.4F);
	}

	public static void headHit(Entity target) {
		particles(target, ParticleTypes.CRIT, target.getEyeY() + 0.1, 8, 0.2);
	}

	private static void particles(Entity at, ParticleOptions type, double y, int count, double spread) {
		if (at.level() instanceof ServerLevel level) {
			level.sendParticles(type, at.getX(), y, at.getZ(), count, spread, spread * 0.5, spread, 0.05);
		}
	}

	private static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
		at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
	}
}
