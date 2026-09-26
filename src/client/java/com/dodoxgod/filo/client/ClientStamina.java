package com.dodoxgod.filo.client;

/** Copia en el cliente de la estamina que manda el servidor. */
public final class ClientStamina {
	private static float value = -1f;
	private static float max = 100f;
	private static long lastChange;

	private ClientStamina() {
	}

	public static void update(float newValue, float newMax) {
		if (newValue != value) lastChange = System.currentTimeMillis();
		value = newValue;
		max = newMax;
	}

	public static void reset() {
		value = -1f;
	}

	/** -1 si el servidor no tiene el mod. */
	public static float value() {
		return value < 0 ? max : value;
	}

	public static float max() {
		return max;
	}

	public static boolean known() {
		return value >= 0;
	}

	public static long millisSinceChange() {
		return System.currentTimeMillis() - lastChange;
	}
}
