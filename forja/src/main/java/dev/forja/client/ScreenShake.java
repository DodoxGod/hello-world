package dev.forja.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * The camera being knocked about by something heavy landing nearby.
 *
 * <p>One number, {@code trauma}, that anything can add to and that drains away by itself. The shake is
 * the <b>square</b> of it, so a small knock is barely there and a big one is violent, and as it drains
 * the shake dies off quickly at the end instead of trembling on. The offsets come off sines at rates
 * that share no factor, which wanders like noise but is smooth between frames — random numbers per
 * frame would be a buzz, not a shake.
 *
 * <p>It obeys the vanilla "distortion effects" slider, because that slider is where a player who gets
 * motion sick has already said so.
 */
public final class ScreenShake {
	private static final float MAX_YAW = 1.5F;
	private static final float MAX_PITCH = 1.1F;
	/** Trauma lost per tick: a full knock is gone in well under a second. */
	private static final float DRAIN = 0.07F;

	private static float trauma;
	private static int ticks;

	private ScreenShake() {
	}

	public static void register() {
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (trauma <= 0.0F) {
				// Back to zero whenever it is still, so the clock the sines run on never grows large
				// enough for a float to start dropping the part of a tick.
				ticks = 0;
			} else if (!client.isPaused()) {
				ticks++;
				trauma = Math.max(0.0F, trauma - DRAIN);
			}
		});
	}

	/** A knock. They add up, to a ceiling: two blows at once are worse than one and not twice as bad. */
	public static void add(float amount) {
		trauma = Mth.clamp(trauma + amount, 0.0F, 1.0F);
	}

	public static boolean active() {
		return trauma > 0.0F;
	}

	public static float yaw(float partialTicks) {
		float time = ticks + partialTicks;
		return strength() * MAX_YAW * (Mth.sin(time * 2.9F) * 0.6F + Mth.sin(time * 4.7F + 1.3F) * 0.4F);
	}

	public static float pitch(float partialTicks) {
		float time = ticks + partialTicks;
		return strength() * MAX_PITCH * (Mth.sin(time * 3.7F + 0.7F) * 0.6F + Mth.sin(time * 5.3F + 2.1F) * 0.4F);
	}

	private static float strength() {
		double scale = Minecraft.getInstance().options.screenEffectScale().get();
		return trauma * trauma * (float) scale;
	}
}
