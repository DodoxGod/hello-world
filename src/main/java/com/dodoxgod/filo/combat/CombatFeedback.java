package com.dodoxgod.filo.combat;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Partículas y sonidos que hacen legible el combate: todo lo peligroso se anuncia. */
public final class CombatFeedback {
	private CombatFeedback() {
	}

	/** Un mob está a punto de golpear. */
	public static void telegraph(Entity mob) {
		particles(mob, ParticleTypes.CRIT, mob.getEyeY() + 0.35, 6, 0.25);
		sound(mob, SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, 0.8f, 0.55f);
	}

	/** Un mob se agacha para embestir. */
	public static void lungeTelegraph(Entity mob) {
		particles(mob, ParticleTypes.POOF, mob.getY() + 0.2, 8, 0.35);
		sound(mob, SoundEvents.ENTITY_RAVAGER_STEP, 0.7f, 1.4f);
	}

	/** Un esqueleto está a punto de soltar un disparo cargado. */
	public static void chargedShotTelegraph(Entity mob) {
		particles(mob, ParticleTypes.ENCHANTED_HIT, mob.getEyeY(), 12, 0.3);
		sound(mob, SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 0.8f, 1.6f);
	}

	public static void parry(Entity defender) {
		particles(defender, ParticleTypes.ENCHANTED_HIT, defender.getEyeY() - 0.3, 14, 0.4);
		sound(defender, SoundEvents.ITEM_SHIELD_BLOCK, 1.0f, 1.6f);
		sound(defender, SoundEvents.BLOCK_ANVIL_PLACE, 0.35f, 2.0f);
	}

	public static void stagger(Entity entity) {
		particles(entity, ParticleTypes.CRIT, entity.getEyeY() + 0.2, 20, 0.5);
		sound(entity, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
	}

	public static void guardBreak(Entity entity) {
		particles(entity, ParticleTypes.CLOUD, entity.getEyeY() - 0.4, 8, 0.3);
	}

	public static void dodge(Entity entity) {
		particles(entity, ParticleTypes.CLOUD, entity.getY() + 0.1, 6, 0.3);
		sound(entity, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.4f, 1.8f);
	}

	public static void dodgedHit(Entity entity) {
		sound(entity, SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, 0.8f, 1.4f);
	}

	public static void headHit(Entity target) {
		particles(target, ParticleTypes.CRIT, target.getEyeY() + 0.1, 8, 0.2);
	}

	private static void particles(Entity at, ParticleEffect type, double y, int count, double spread) {
		if (at.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(type, at.getX(), y, at.getZ(), count, spread, spread * 0.5, spread, 0.05);
		}
	}

	private static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
		at.getWorld().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
