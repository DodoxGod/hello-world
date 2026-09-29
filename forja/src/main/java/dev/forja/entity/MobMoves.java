package dev.forja.entity;

import java.util.function.Function;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.constant.dataticket.DataTicket;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Everything the mod's own monsters do with their bodies that is not a special move of their own (Andy,
 * 2026-09-28: "haz animaciones para los mobs del mod que las necesitan"): the warning before a plain blow,
 * a stagger, dying, and a run of their own where the walk sped up does not do. One controller per monster
 * plays all of it, in this order:
 *
 * <ol>
 *   <li>dying: its death, over whatever it was doing;</li>
 *   <li>a clip the server triggered (a blow, a special): played to its end;</li>
 *   <li>staggered, its posture broken: its stagger;</li>
 *   <li>warning of a blow (CombatFeedback.telegraph): its wind-up, stretched or squeezed so the pose is
 *       fully drawn on the tick the blow comes, and held there until the swing takes over;</li>
 *   <li>a state of its own (a fed wisp, a full core, tongs holding someone...);</li>
 *   <li>otherwise running, walking or standing (see {@link GeoGait}).</li>
 * </ol>
 *
 * The warning and the stagger are the client's own knowledge (client.CombatAnims), handed over in the render
 * state by the client every frame, as the run is. Nothing here allocates per frame: the clips are built once.
 */
public final class MobMoves {
	/** How many ticks of a blow's warning there are, while one is under way (0 when not). Set on the client. */
	public static final DataTicket<Float> WINDUP = DataTicket.create("forja_windup", Float.class);
	/** Whether its posture is broken right now, 1 or 0. Set on the client. */
	public static final DataTicket<Float> STAGGER = DataTicket.create("forja_stagger", Float.class);
	/** The one boxed zero, so that a frame with nothing to say allocates nothing. */
	public static final Float NONE = 0.0F;
	public static final Float ONE = 1.0F;
	/** How long every wind-up clip takes to draw, as written (WINDUP in tools/generate_assets.py): 0.4 s. */
	public static final int WINDUP_TICKS = 8;
	/** Ticks every change of clip blends over, except into a triggered clip, which starts on its tick. */
	public static final int BLEND = 3;
	/** A run faster than this share of a full one plays the run clip, when there is one. */
	private static final float RUN_CLIP = 0.45F;

	private MobMoves() {
	}

	/** What a monster has to play; built once, in its registerControllers. */
	public static final class Clips<T extends LivingEntity & GeoAnimatable> {
		final RawAnimation idle;
		final RawAnimation walk;
		@Nullable RawAnimation run;
		@Nullable RawAnimation windup;
		float windupTicks;
		@Nullable RawAnimation stagger;
		@Nullable RawAnimation death;
		@Nullable Function<T, @Nullable RawAnimation> state;

		private Clips(RawAnimation idle, RawAnimation walk) {
			this.idle = idle;
			this.walk = walk;
		}

		public static <T extends LivingEntity & GeoAnimatable> Clips<T> of(RawAnimation idle, RawAnimation walk) {
			return new Clips<>(idle, walk);
		}

		public Clips<T> run(RawAnimation run) {
			this.run = run;
			return this;
		}

		/** The wind-up before a plain blow, which reaches its drawn pose at {@code ticks} (held after). */
		public Clips<T> windup(RawAnimation windup, int ticks) {
			this.windup = windup;
			this.windupTicks = ticks;
			return this;
		}

		public Clips<T> stagger(RawAnimation stagger) {
			this.stagger = stagger;
			return this;
		}

		public Clips<T> death(RawAnimation death) {
			this.death = death;
			return this;
		}

		/** A state of the monster's own, over its walk and idle: a clip to play, or null for none. */
		public Clips<T> state(Function<T, @Nullable RawAnimation> state) {
			this.state = state;
			return this;
		}
	}

	/**
	 * A controller for all of it. Triggered clips start on their own tick (no blend), so a special's
	 * keyframes still land on the tick its code does; everything else blends over {@link #BLEND} ticks.
	 */
	public static <T extends LivingEntity & GeoAnimatable> AnimationController<T> controller(String name, Clips<T> clips) {
		return new Controller<>(name, clips);
	}

	private static final class Controller<T extends LivingEntity & GeoAnimatable> extends AnimationController<T> {
		Controller(String name, Clips<T> clips) {
			super(name, BLEND, test -> handle(test, clips));
			this.receiveTriggeredAnimations();
		}

		@Override
		public boolean triggerAnimation(String animName) {
			// Read by the next frame as it starts the clip: no blend into a blow.
			this.setTransitionTicks(0);
			return super.triggerAnimation(animName);
		}

		/**
		 * A triggered clip under way, including one just triggered that has not started yet (a clip that could
		 * not be started at all leaves no timeline, and does not count).
		 */
		boolean triggered() {
			return this.triggeredAnimTime >= 0 && (this.animationPoint == null ? this.timeline != null : this.timelineTime >= 0);
		}
	}

	private static <T extends LivingEntity & GeoAnimatable> PlayState handle(AnimationTest<T> test, Clips<T> clips) {
		Controller<T> controller = (Controller<T>) test.controller();
		T mob = test.animatable();
		if (mob.isDeadOrDying() && clips.death != null) {
			controller.stopTriggeredAnimation();
			controller.setTransitionTicks(BLEND);
			test.setControllerSpeed(1.0F);
			return test.setAndContinue(clips.death);
		}
		if (controller.triggered()) {
			test.setControllerSpeed(1.0F);
			return PlayState.CONTINUE;
		}
		controller.setTransitionTicks(BLEND);
		if (clips.stagger != null && test.getDataOrDefault(STAGGER, NONE) > 0.0F) {
			test.setControllerSpeed(1.0F);
			return test.setAndContinue(clips.stagger);
		}
		float warning = test.getDataOrDefault(WINDUP, NONE);
		if (clips.windup != null && warning > 0.0F) {
			// The blend in is part of the warning too: the pose is drawn on the tick the blow comes.
			test.setControllerSpeed((clips.windupTicks + BLEND) / warning);
			return test.setAndContinue(clips.windup);
		}
		if (clips.state != null) {
			RawAnimation own = clips.state.apply(mob);
			if (own != null) {
				test.setControllerSpeed(1.0F);
				return test.setAndContinue(own);
			}
		}
		// As GeoGait.walk, without boxing a default every frame: the walk sped up as far as it is into a run.
		boolean moving = test.isMoving();
		float run = moving ? test.getDataOrDefault(GeoGait.RUN, NONE) : 0.0F;
		if (clips.run != null && run > RUN_CLIP) {
			test.setControllerSpeed(1.0F);
			return test.setAndContinue(clips.run);
		}
		test.setControllerSpeed(1.0F + GeoGait.RATE * run);
		return test.setAndContinue(moving ? clips.walk : clips.idle);
	}
}
