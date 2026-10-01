package dev.forja.ai;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What monsters hear of a player (docs/red_mob_v4_diseno.md §4.5, block P): the player's own game events, as the sculk
 * hears them, kept per player (the last {@link #KEEP}), and each hunting mob picks out the ones that reach it.
 *
 * <table>
 *   <caption>Sounds and how far they carry (blocks; half that with no line from the sound to the mob's ears)</caption>
 *   <tr><th>kind</th><th>events</th><th>radius</th></tr>
 *   <tr><td>movement</td><td>STEP walking / sprinting; HIT_GROUND</td><td>6 / 12; 10</td></tr>
 *   <tr><td>work</td><td>BLOCK_DESTROY, BLOCK_PLACE; BLOCK_OPEN/CLOSE, CONTAINER_OPEN/CLOSE</td><td>16; 12</td></tr>
 *   <tr><td>eating</td><td>EAT, DRINK</td><td>8</td></tr>
 *   <tr><td>combat</td><td>PROJECTILE_SHOOT; a blow of theirs landing (ENTITY_DAMAGE they caused)</td><td>16; 12</td></tr>
 * </table>
 *
 * <p>A crouching player's steps make no STEP event at all (vanilla), so sneaking is silent. One hook for every player's
 * events (ServerLevelGameEventMixin), not a listener per mob: it costs nothing when nobody is hunting.
 */
public final class Hearing {
	public static final int MOVEMENT = 0;
	public static final int WORK = 1;
	public static final int EATING = 2;
	public static final int COMBAT = 3;
	/** How many of a player's sounds are kept, and for how long (older ones are no use: sonidoK_edad/40 tops at 2). */
	public static final int KEEP = 12;
	public static final int MAX_AGE = 80;
	/** Heard within this many ticks: obj_oido. */
	public static final int HEARD_RECENTLY = 20;

	/** One sound a player made: where, when, how far it carries and what kind it is, and its number (in order). */
	public record Sound(Vec3 pos, long time, double radius, int kind, long seq) {
		public Sound(Vec3 pos, long time, double radius, int kind) {
			this(pos, time, radius, kind, 0L);
		}
	}

	/** Numbers the sounds in the order they happen, so a mob listens to each once whenever in the tick it came. */
	private static long counter;

	private static final Map<Player, ArrayDeque<Sound>> SOUNDS = new WeakHashMap<>();

	private Hearing() {
	}

	/** A game event (ServerLevelGameEventMixin): kept if a player made it and it is one monsters listen for. */
	public static void onEvent(Holder<GameEvent> event, Vec3 pos, Entity source) {
		if (!(source instanceof Player player) || player.isSpectator() || player.isCreative()) {
			return;
		}
		GameEvent e = event.value();
		double radius;
		int kind;
		if (e == GameEvent.STEP.value()) {
			radius = player.isSprinting() ? 12.0 : 6.0;
			kind = MOVEMENT;
		} else if (e == GameEvent.HIT_GROUND.value()) {
			radius = 10.0;
			kind = MOVEMENT;
		} else if (e == GameEvent.BLOCK_DESTROY.value() || e == GameEvent.BLOCK_PLACE.value()) {
			radius = 16.0;
			kind = WORK;
		} else if (e == GameEvent.BLOCK_OPEN.value() || e == GameEvent.BLOCK_CLOSE.value() || e == GameEvent.CONTAINER_OPEN.value()
			|| e == GameEvent.CONTAINER_CLOSE.value()) {
			radius = 12.0;
			kind = WORK;
		} else if (e == GameEvent.EAT.value() || e == GameEvent.DRINK.value()) {
			radius = 8.0;
			kind = EATING;
		} else if (e == GameEvent.PROJECTILE_SHOOT.value()) {
			radius = 16.0;
			kind = COMBAT;
		} else if (e == GameEvent.ENTITY_DAMAGE.value()) {
			radius = 12.0;
			kind = COMBAT;
		} else {
			return;
		}
		record(player, new Sound(pos, player.level().getGameTime(), radius, kind));
	}

	/** Records a sound of the player's (also for the tests). */
	public static void record(Player player, Sound heard) {
		Sound sound = new Sound(heard.pos, heard.time, heard.radius, heard.kind, ++counter);
		ArrayDeque<Sound> sounds = SOUNDS.computeIfAbsent(player, p -> new ArrayDeque<>());
		sounds.addLast(sound);
		while (sounds.size() > KEEP || !sounds.isEmpty() && sound.time - sounds.peekFirst().time > MAX_AGE) {
			sounds.removeFirst();
		}
	}

	public static void forget(Player player) {
		SOUNDS.remove(player);
	}

	/** The number of the player's latest sound (a mob that starts hunting them listens from there on). */
	public static long latest(Player player) {
		ArrayDeque<Sound> sounds = SOUNDS.get(player);
		return sounds == null || sounds.isEmpty() ? Long.MIN_VALUE / 2 : sounds.peekLast().seq;
	}

	/**
	 * Each tick, for a mob hunting this player: the player's sounds since it last listened that reach it (within their
	 * radius, halved when a block stands between the sound and its ears) become its two latest sounds, and the newest
	 * one heard is where it thinks the player is, if that is newer than where it last saw them.
	 */
	public static void listen(MobMind mind, Player player, long now) {
		ArrayDeque<Sound> sounds = SOUNDS.get(player);
		long since = mind.heardUpTo;
		if (sounds == null || sounds.isEmpty() || sounds.peekLast().seq <= since) {
			return;
		}
		mind.heardUpTo = sounds.peekLast().seq;
		Mob mob = mind.mob;
		Vec3 ears = mob.getEyePosition();
		for (Sound sound : sounds) {
			if (sound.seq <= since || now - sound.time > MAX_AGE) {
				continue;
			}
			double d = ears.distanceTo(sound.pos);
			if (d > sound.radius) {
				continue;
			}
			if (d > sound.radius / 2.0 && !open(mob, sound.pos, ears)) {
				continue;
			}
			mind.sound1 = mind.sound0;
			mind.sound0 = sound;
			if (sound.time > mind.lastHeardAt) {
				mind.lastHeard = sound.pos;
				mind.lastHeardAt = sound.time;
			}
		}
	}

	private static boolean open(Mob mob, Vec3 from, Vec3 to) {
		Vec3 start = from.add(0.0, 0.5, 0.0);
		return mob.level().clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
	}

	/** The ten inputs of one of block P's sounds, written from {@code at}: nothing when there is none, or it is too old. */
	static void observe(Sound sound, Mob mob, double fx, double fz, long now, float[] out, int at) {
		if (sound == null || now - sound.time > MAX_AGE) {
			return;
		}
		double ox = sound.pos.x - mob.getX();
		double oz = sound.pos.z - mob.getZ();
		out[at] = 1.0F;
		out[at + 1] = (float) ObsM1.clip((ox * fx + oz * fz) / 16.0, -2.0, 2.0);
		out[at + 2] = (float) ObsM1.clip((ox * -fz + oz * fx) / 16.0, -2.0, 2.0);
		out[at + 3] = (float) ObsM1.clip((sound.pos.y - mob.getY()) / 4.0, -2.0, 2.0);
		out[at + 4] = (float) ObsM1.clip((now - sound.time) / 40.0, 0.0, 2.0);
		out[at + 5] = (float) (sound.radius / 16.0);
		out[at + 6 + sound.kind] = 1.0F;
	}
}
