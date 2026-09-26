package dev.forja.client;

import dev.forja.combat.CombatAnim;
import dev.forja.combat.CombatRules;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;

/**
 * The client's memory of what each entity is doing in a fight, fed by {@link CombatAnim}: when a mob
 * started winding up, when it was staggered, which way a player dodged. The renderers ask it for a pose;
 * nothing here draws anything.
 *
 * <p>Times are in level ticks plus the partial tick, so a pose moves smoothly between ticks and stops
 * when the game is paused.
 */
public final class CombatAnims {
	/** Everything we remember about one entity. A field of {@code NEVER} means it has not happened. */
	public static final class State {
		static final double NEVER = -1.0E9;
		double telegraphAt = NEVER;
		int telegraphTicks;
		double lungeAt = NEVER;
		int lungeTicks;
		double staggerAt = NEVER;
		int staggerTicks;
		double dodgeAt = NEVER;
		int dodgeTicks;
		float dodgeX;
		float dodgeZ;
		double parryAt = NEVER;
		boolean parryPerfect;
		double guardBreakAt = NEVER;
		/** A charge being held since then (NEVER when not charging), and how long a full one takes. */
		double chargeAt = NEVER;
		int chargeTicks = 1;
		/** The next swing finishes a combo until then; the swing that did keeps its pose until finisherUntil. */
		double comboReadyFrom = NEVER;
		double comboReadyUntil = NEVER;
		double comboFinisherUntil = NEVER;
		double finisherAt = NEVER;
		double counterUntil = NEVER;
		double postureAt = NEVER;
		float posture;
		float postureDrain;
		int postureDelay;
		double lastSeen;
	}

	private static final Int2ObjectMap<State> STATES = new Int2ObjectOpenHashMap<>();
	/** Forget an entity this long after the last thing that happened to it. */
	private static final double FORGET_AFTER = 20.0 * 30.0;

	/** How long a combo waits for its next hit, in ticks; matches the server's default window. */
	static final double COMBO_WINDOW = 30.0;

	private static CombatRules rules;
	private static int lastHitEntity = -1;
	private static double lastHitAt = State.NEVER;

	/** The pressure on the local player: value when last told, when, the wait and the drain per tick. */
	private static float pressure;
	private static double pressureAt = State.NEVER;
	private static int pressureDelay;
	private static float pressureDrain;

	private static long hitstopUntil;
	private static float frozenAttack;
	private static boolean frozen;

	private CombatAnims() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CombatAnim.TYPE, (payload, context) -> receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(CombatRules.TYPE, (payload, context) -> rules = payload);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			STATES.clear();
			rules = null;
			lastHitEntity = -1;
			pressureAt = State.NEVER;
			frozen = false;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null || client.level.getGameTime() % 100 != 0) {
				return;
			}
			double now = client.level.getGameTime();
			STATES.values().removeIf(state -> now - state.lastSeen > FORGET_AFTER);
		});
	}

	/** The server's rules when it sent them, this side's own config otherwise (an older server, or none). */
	public static CombatRules rules() {
		return rules != null ? rules : CombatRules.local();
	}

	/** Level time plus the partial tick, or 0 with no level. */
	public static double now(float partialTick) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0.0 : minecraft.level.getGameTime() + partialTick;
	}

	public static State get(int entityId) {
		return STATES.get(entityId);
	}

	private static void receive(CombatAnim payload) {
		CombatAnim.Kind kind = CombatAnim.Kind.of(payload.kind());
		Minecraft minecraft = Minecraft.getInstance();
		if (kind == null || minecraft.level == null) {
			return;
		}
		double now = minecraft.level.getGameTime();
		State state = STATES.computeIfAbsent(payload.entity(), id -> new State());
		state.lastSeen = now;
		boolean self = minecraft.player != null && payload.entity() == minecraft.player.getId();
		switch (kind) {
			case TELEGRAPH -> {
				state.telegraphAt = now;
				state.telegraphTicks = Math.max(1, payload.ticks());
			}
			case LUNGE -> {
				state.lungeAt = now;
				state.lungeTicks = Math.max(1, payload.ticks());
			}
			case STAGGER -> {
				state.staggerAt = now;
				state.staggerTicks = Math.max(1, payload.ticks());
				state.posture = 0.0F;
				// Breaking a guard you were beating on is the biggest thing you can do to a mob: feel it.
				if (payload.entity() == lastHitEntity && now - lastHitAt < 30.0) {
					ScreenShake.add(0.3F);
					hitstop(110L);
				}
			}
			case DODGE -> {
				state.dodgeAt = now;
				state.dodgeTicks = Math.max(1, payload.ticks());
				state.dodgeX = payload.a();
				state.dodgeZ = payload.b();
			}
			case PARRY -> {
				state.parryAt = now;
				state.parryPerfect = payload.a() > 0.5F;
				if (self) {
					ScreenShake.add(state.parryPerfect ? 0.45F : 0.3F);
					hitstop(state.parryPerfect ? 120L : 80L);
				}
			}
			case GUARD_BREAK -> {
				state.guardBreakAt = now;
				if (self) {
					ScreenShake.add(0.5F);
				}
			}
			case HIT -> {
				lastHitEntity = payload.entity();
				lastHitAt = now;
				float damage = Math.max(0.0F, payload.a());
				// A light jab barely pauses; a greatsword at full charge stops the world for a tenth of a second.
				hitstop(Math.round(35.0F + Mth.clamp(damage, 0.0F, 12.0F) * 7.0F));
				ScreenShake.add(Mth.clamp(0.05F + damage * 0.02F, 0.0F, 0.25F));
			}
			case HURT -> ScreenShake.add(Mth.clamp(0.08F + payload.a() * 0.035F, 0.0F, 0.45F));
			case CHARGE -> {
				if (payload.a() > 0.5F) {
					state.chargeAt = now;
					state.chargeTicks = Math.max(1, payload.ticks());
				} else {
					state.chargeAt = State.NEVER;
				}
			}
			case COMBO -> {
				if (payload.ticks() >= 3) {
					state.comboReadyUntil = State.NEVER;
					state.comboFinisherUntil = now + 8.0;
				} else {
					// The second swing is still under way when the server says so: leave it be.
					state.comboReadyFrom = now + 4.0;
					state.comboReadyUntil = now + CombatAnims.COMBO_WINDOW;
				}
			}
			case FINISHER -> {
				state.finisherAt = now;
				state.staggerAt = State.NEVER;
				state.posture = 0.0F;
				if (payload.entity() == lastHitEntity && now - lastHitAt < 30.0) {
					ScreenShake.add(0.55F);
					hitstop(170L);
				}
			}
			case PERFECT_DODGE -> {
				state.counterUntil = now + payload.ticks();
				if (self) {
					ScreenShake.add(0.15F);
					hitstop(60L);
				}
			}
			case PRESSURE -> {
				pressure = payload.a();
				pressureAt = now;
				pressureDelay = payload.ticks();
				pressureDrain = payload.b();
			}
			case POSTURE -> {
				if (now >= state.staggerAt + state.staggerTicks) {
					state.postureAt = now;
					state.posture = Mth.clamp(payload.a(), 0.0F, 1.0F);
					state.postureDrain = Math.max(0.0F, payload.b());
					state.postureDelay = Math.max(0, payload.ticks());
				}
			}
		}
	}

	// --- Queries for the renderers ------------------------------------------------------------------

	/** 0 to 1 over a span that started at {@code at} and lasts {@code ticks}; -1 outside it. */
	static float progress(double at, int ticks, double now) {
		double t = (now - at) / Math.max(1, ticks);
		return t < 0.0 || t > 1.0 ? -1.0F : (float) t;
	}

	/** How full an entity's posture bar is right now, draining as the server's does. -1 if unknown. */
	public static float posture(int entityId, float partialTick) {
		State state = STATES.get(entityId);
		if (state == null || state.postureAt == State.NEVER) {
			return -1.0F;
		}
		double idle = now(partialTick) - state.postureAt - state.postureDelay;
		return idle <= 0.0 ? state.posture : (float) Math.max(0.0, state.posture - idle * state.postureDrain);
	}

	/** How much of a stagger is left, 1 at its start and 0 at its end; -1 when not staggered. */
	public static float staggerLeft(int entityId, float partialTick) {
		State state = STATES.get(entityId);
		if (state == null) {
			return -1.0F;
		}
		float t = progress(state.staggerAt, state.staggerTicks, now(partialTick));
		return t < 0.0F ? -1.0F : 1.0F - t;
	}

	/** The pressure on the local player right now (0 to 0.7), draining as the server's does. */
	public static float pressure(float partialTick) {
		if (pressureAt == State.NEVER) {
			return 0.0F;
		}
		double idle = now(partialTick) - pressureAt - pressureDelay;
		return idle <= 0.0 ? pressure : (float) Math.max(0.0, pressure - idle * pressureDrain);
	}

	/** How far an entity's charge has got, 0 to 1, or -1 when it is not charging. */
	public static float charge(int entityId, float partialTick) {
		State state = STATES.get(entityId);
		if (state == null || state.chargeAt == State.NEVER) {
			return -1.0F;
		}
		return (float) Math.min(1.0, (now(partialTick) - state.chargeAt) / state.chargeTicks);
	}

	/** Whether the swing an entity is making (or about to make) finishes a combo. */
	public static boolean comboFinishing(int entityId, float partialTick) {
		State state = STATES.get(entityId);
		if (state == null) {
			return false;
		}
		double now = now(partialTick);
		return now >= state.comboReadyFrom && now < state.comboReadyUntil || now < state.comboFinisherUntil;
	}

	/** Whether a counter is open for this entity after a perfect dodge. */
	public static boolean counterOpen(int entityId, float partialTick) {
		State state = STATES.get(entityId);
		return state != null && now(partialTick) < state.counterUntil;
	}

	/** The entity the player last landed a blow on, and how long ago in ticks. */
	public static int lastHitEntity() {
		return lastHitEntity;
	}

	public static double ticksSinceLastHit(float partialTick) {
		return now(partialTick) - lastHitAt;
	}

	// --- Hitstop ------------------------------------------------------------------------------------

	/**
	 * Holds the player's own swing where it is for a moment. Real time, not ticks: a hitstop is a few
	 * frames, shorter than a tick, and it has to hold still even if the tick rate stutters.
	 */
	public static void hitstop(long millis) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.options.screenEffectScale().get() <= 0.0) {
			return;
		}
		long until = Util.getMillis() + millis;
		if (!frozen || until > hitstopUntil) {
			if (!frozen) {
				frozenAttack = minecraft.player.getAttackAnim(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
			}
			hitstopUntil = until;
			frozen = true;
		}
	}

	/** The swing to show instead of the real one while a hitstop holds, or NaN when none does. */
	public static float frozenAttack(Entity entity) {
		if (!frozen) {
			return Float.NaN;
		}
		if (Util.getMillis() >= hitstopUntil) {
			frozen = false;
			return Float.NaN;
		}
		Minecraft minecraft = Minecraft.getInstance();
		return entity == minecraft.player ? frozenAttack : Float.NaN;
	}
}
