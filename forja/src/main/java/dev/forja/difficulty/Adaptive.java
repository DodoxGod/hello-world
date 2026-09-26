package dev.forja.difficulty;

import com.mojang.serialization.Codec;
import dev.forja.Forja;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * A difficulty that follows how the player is doing, gently and in the open: every hostile kill made
 * without taking a scratch in the last ten seconds nudges it up, every death knocks it down. It moves
 * mob damage by up to ±15 % and threat chances by up to ±50 %, and /forja dificultad shows where it is.
 *
 * <pre>
 * a ∈ [−1, 1], empieza en 0; baja muerte sin daño recibido en 200 ticks: a += 0,02; muerte del jugador: a −= 0,15
 * daño de los mobs × (1 + 0,15·a); probabilidad de amenaza × (1 + 0,5·a)
 * </pre>
 */
public final class Adaptive {
	@SuppressWarnings("deprecation")
	public static final AttachmentType<Float> VALUE = AttachmentRegistry.<Float>builder()
		.initializer(() -> 0.0F)
		.persistent(Codec.FLOAT)
		.copyOnDeath()
		.buildAndRegister(Forja.id("dificultad_adaptativa"));

	private static final float PER_CLEAN_KILL = 0.02F;
	private static final float PER_DEATH = 0.15F;
	private static final int CLEAN_TICKS = 200;

	private Adaptive() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!CombatConfig.get().adaptive) {
				return;
			}
			if (entity instanceof ServerPlayer player) {
				nudge(player, -PER_DEATH);
			} else if (entity instanceof Mob && entity instanceof Enemy && source.getEntity() instanceof ServerPlayer killer
				&& killer.level().getGameTime() - Pressure.lastHit(killer) > CLEAN_TICKS) {
				nudge(killer, PER_CLEAN_KILL);
			}
		});
	}

	private static void nudge(Player player, float by) {
		player.setAttached(VALUE, Mth.clamp(value(player) + by, -1.0F, 1.0F));
	}

	/** Where the player's adaptive difficulty is, −1 (going badly) to 1 (going too well). */
	public static float value(Player player) {
		if (player == null || !CombatConfig.get().adaptive) {
			return 0.0F;
		}
		return player.getAttachedOrElse(VALUE, 0.0F);
	}

	public static double damageMultiplier(Player player) {
		return 1.0 + 0.15 * value(player);
	}

	public static double threatMultiplier(Player player) {
		return 1.0 + 0.5 * value(player);
	}
}
