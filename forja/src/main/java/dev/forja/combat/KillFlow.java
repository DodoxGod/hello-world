package dev.forja.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * What a kill gives back, for the stamina bar and the mana bar alike.
 *
 * <p>Andy, 2026-09-28: "al matar recuperas un máximo de 3 % de maná por cada 5 ticks", and then "matar también
 * debe regenerar stamina, con el mismo límite". So a kill never refills a bar at once. What it is worth goes
 * into a pending pool that the bar shows as a lighter stretch after its end, and from there it flows in at most
 * {@link CombatConfig#killFlowShare} of the bar every {@link CombatConfig#killFlowEveryTicks} ticks. Ten kills
 * in a second are worth what they are worth; they just take their time arriving.
 *
 * <p>The pool never holds more than the room left in the bar: what would not fit is not kept for later, the
 * way a potion does not heal past full. Both bars ask the same three questions here, so the two cannot drift.
 */
public final class KillFlow {
	private KillFlow() {
	}

	/** How much of {@code amount} the pool may take: never more than the room the bar and the pool leave. */
	public static float accept(float value, float pending, float max, float amount) {
		return Math.max(0.0F, Math.min(amount, max - value - pending));
	}

	/**
	 * What flows out of the pool into the bar on tick {@code now}: nothing between the beats, and on a beat
	 * as much as the pool has, up to the share, up to what fits.
	 */
	public static float flow(long now, float value, float pending, float max) {
		CombatConfig cfg = CombatConfig.get();
		int every = Math.max(1, cfg.killFlowEveryTicks);
		if (pending <= 0.0F || now % every != 0) {
			return 0.0F;
		}
		return Math.max(0.0F, Math.min(pending, Math.min(max * cfg.killFlowShare, max - value)));
	}

	/**
	 * The pool after a tick in which the bar has come to {@code value}: whatever no longer fits is let go,
	 * so the lighter stretch always ends inside the bar and a full bar has nothing waiting.
	 */
	public static float trim(float value, float pending, float max) {
		return value >= max ? 0.0F : Math.max(0.0F, Math.min(pending, max - value));
	}

	/** What a kill is worth to a bar of {@code max}: a base, a little more for a bigger victim, up to a share of the bar. */
	public static float reward(float base, float perHealth, float capShare, float max, LivingEntity victim) {
		return Math.min(max * capShare, base + perHealth * victim.getMaxHealth());
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			// Anything a player kills that was alive in its own right: monsters, animals, other players.
			if (source.getEntity() instanceof ServerPlayer killer && killer != victim && (victim instanceof Mob || victim instanceof ServerPlayer)) {
				onKill(killer, victim);
			}
		});
	}

	/** A kill, for both bars. Public so the tests can hand one over without staging a death. */
	public static void onKill(ServerPlayer killer, LivingEntity victim) {
		Stamina.onKill(killer, victim);
		dev.forja.magic.Mana.onKill(killer, victim);
	}
}
