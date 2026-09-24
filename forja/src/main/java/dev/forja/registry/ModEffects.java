package dev.forja.registry;

import dev.forja.Forja;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** The effects Forja adds. Bleeding is the one the dagger and the scythe live off. */
public final class ModEffects {
	/**
	 * Sangrado: a wound that keeps costing blood. It ignores armor, it stacks, and the last stack is
	 * what turns into wither, so a shallow cut is nothing and a deep one kills on its own.
	 */
	public static final Holder<MobEffect> SANGRADO = register("sangrado", new Bleeding());

	private ModEffects() {
	}

	public static void init() {
	}

	private static Holder<MobEffect> register(String name, MobEffect effect) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Forja.id(name), effect);
	}

	private static final class Bleeding extends MobEffect {
		/** Ticks between two drops of blood. */
		private static final int PERIOD = 20;

		private Bleeding() {
			super(MobEffectCategory.HARMFUL, 0xB3241F, ParticleTypes.DAMAGE_INDICATOR);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return duration % PERIOD == 0;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
			// Magic damage: mail and plate do not stop a wound that is already open.
			entity.invulnerableTime = 0;
			entity.hurtServer(level, level.damageSources().magic(), 1.0F + amplifier * 0.5F);
			return true;
		}
	}
}
