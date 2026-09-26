package dev.forja.difficulty;

import com.mojang.serialization.Codec;
import dev.forja.Forja;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Nights survived in this world, counted at each dawn while someone is playing. They make the dark a
 * little more dangerous, with a ceiling: night spawns are a bit more likely to come in twos, and a bit
 * more likely to be veterans or elites. Past the ceilings nothing grows in number; night 1000 is not a
 * hundred zombies, only better ones.
 *
 * <pre>
 * compañero (solo apariciones naturales de noche) = min(máx, porDoble · log2(1 + noches))
 * amenaza × (1 + min(máxAmenaza, porNoche · noches))
 * </pre>
 */
public final class Nights {
	@SuppressWarnings("deprecation")
	public static final AttachmentType<Integer> COUNT = AttachmentRegistry.<Integer>builder()
		.initializer(() -> 0)
		.persistent(Codec.INT)
		.buildAndRegister(Forja.id("noches"));

	@SuppressWarnings("deprecation")
	public static final AttachmentType<Long> LAST_DAY = AttachmentRegistry.<Long>builder()
		.initializer(() -> -1L)
		.persistent(Codec.LONG)
		.buildAndRegister(Forja.id("ultimo_dia"));

	private Nights() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Nights::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		long day = overworld.getOverworldClockTime() / 24000L;
		long last = overworld.getAttachedOrElse(LAST_DAY, -1L);
		if (last < 0) {
			overworld.setAttached(LAST_DAY, day);
		} else if (day > last) {
			overworld.setAttached(LAST_DAY, day);
			if (!server.getPlayerList().getPlayers().isEmpty()) {
				overworld.setAttached(COUNT, count(overworld) + 1);
			}
		}
	}

	public static int count(ServerLevel level) {
		return level.getServer().overworld().getAttachedOrElse(COUNT, 0);
	}

	/** Chance a natural night spawn brings a companion. */
	public static double companionChance(ServerLevel level) {
		CombatConfig cfg = CombatConfig.get();
		int nights = count(level);
		return Math.min(cfg.nightCompanionMax, cfg.nightCompanionPerDoubling * (Math.log(1.0 + nights) / Math.log(2.0)));
	}

	/** Multiplier on threat chances from the nights survived. */
	public static double threatMultiplier(ServerLevel level) {
		CombatConfig cfg = CombatConfig.get();
		return 1.0 + Math.min(cfg.nightThreatMax, cfg.nightThreatPerNight * count(level));
	}
}
