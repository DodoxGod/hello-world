package dev.forja.entity;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.constant.dataticket.DataTicket;

/**
 * The run of the mod's own monsters (Andy, 2026-09-27: "¿correr tiene animación o harás una?"). None of
 * their models has a run of its own, so a running one plays its walk faster, as far as it is into its run:
 * the client works that out with the rest of a monster's gait (client.MobGaits, which also leans it into the
 * run) and hands it over in the render state. The speed goes back to normal for anything else the
 * controller plays.
 */
public final class GeoGait {
	/** How far into a run the monster is, 0 to 1, eased; set on the client for every frame. */
	public static final DataTicket<Float> RUN = DataTicket.create("forja_run", Float.class);
	/** The walk at a full run plays this much faster. */
	public static final float RATE = 0.6F;

	private GeoGait() {
	}

	/** Walks when moving and idles otherwise, the walk sped up while running. */
	public static <T extends GeoAnimatable> PlayState walk(AnimationTest<T> test, RawAnimation walk, RawAnimation idle) {
		boolean moving = test.isMoving();
		float run = moving ? test.getDataOrDefault(RUN, 0.0F) : 0.0F;
		test.setControllerSpeed(1.0F + RATE * run);
		return test.setAndContinue(moving ? walk : idle);
	}
}
