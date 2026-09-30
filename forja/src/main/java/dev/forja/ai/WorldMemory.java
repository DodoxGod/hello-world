package dev.forja.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import dev.forja.Forja;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * The world's vector (docs/red_mob_v4_diseno.md §4.11, block W, 16 inputs): what the monsters of a world have learnt of
 * one player's way of killing them. No network learns anything in the game: this is what they see, kept per player and
 * per dimension in an attachment of the player's (saved with them, kept through death). The formula, exactly (the
 * simulator's campaigns use the same):
 *
 * <ul>
 *   <li><b>Causes of death</b> (8, the first of W), when a mob fighting the player dies: one of trap (lava, a fall,
 *   drowning, a wall), fire (burning, or an explosion the player set off), height (the player was up, overGround ≥ 2),
 *   area (killed by the player's blow with a weapon that strikes an area), arrow (the player's projectile), narrow (the
 *   player in a corridor or a doorway), open (the player's blow otherwise) and other; each is a moving average
 *   {@code v ← v + 0.1·(onehot − v)}, so they add up to 1.</li>
 *   <li><b>What works</b> (5): at a mob's death, or when it leaves the fight, the damage it did to the player over its
 *   life, / 10, goes into the average of the way it fought most (front: ACERCARSE, RODEAR, FORMACION, CUBRIRSE, LIBRE,
 *   CARGA; flank: FLANQUEAR, SECTOR; ranged: an archer's whole life, TIRO_LIBRE; ambush: EMBOSCAR, OCULTARSE; siege:
 *   ASEDIAR), {@code v ← v + 0.1·(damage/10 − v)}; a life with none of those counts nowhere.</li>
 *   <li><b>The player's style</b> (2), at the end of each fight (its group gone for 60 ticks): went up (a pillar or a tower
 *   for 40 ticks or more), and ran or shut themselves in (more than 24 blocks from the whole group, or perceived by
 *   none of it for 200 ticks while they hunted); each an average of 0/1 at 0.1.</li>
 *   <li><b>Confidence</b>: deaths recorded / 50, at most 1.</li>
 *   <li><b>Forgetting</b>: every in-game day that goes by, every value moves 10 % back towards where it started
 *   ({@code v ← start + 0.9·(v − start)} per day): causes 1/8 each, the rest 0 (confidence too).</li>
 * </ul>
 */
public final class WorldMemory {
	public static final int SIZE = 16;
	public static final int CAUSES = 0;
	public static final int SUCCESS = 8;
	public static final int STYLE = 13;
	public static final int CONFIDENCE = 15;
	public static final double RATE = 0.1;
	public static final double FORGET_PER_DAY = 0.9;
	public static final int FULL_CONFIDENCE = 50;

	public static final int OPEN = 0;
	public static final int ARROW = 1;
	public static final int HEIGHT = 2;
	public static final int NARROW = 3;
	public static final int TRAP = 4;
	public static final int AREA = 5;
	public static final int FIRE = 6;
	public static final int OTHER = 7;

	public static final int FRONT = 0;
	public static final int FLANK = 1;
	public static final int RANGED = 2;
	public static final int AMBUSH = 3;
	public static final int SIEGE = 4;

	/** Per dimension: the 16 values, then the deaths counted and the day it was last brought up to date. */
	public static final AttachmentType<Map<String, List<Float>>> MEMORY = AttachmentRegistry.<Map<String, List<Float>>>builder()
		.persistent(Codec.unboundedMap(Codec.STRING, Codec.FLOAT.listOf()))
		.copyOnDeath()
		.buildAndRegister(Forja.id("memoria_mundo"));

	private WorldMemory() {
	}

	/** Where every value starts, and goes back to. */
	public static float[] start() {
		float[] v = new float[SIZE + 2];
		for (int k = 0; k < 8; k++) {
			v[CAUSES + k] = 1.0F / 8.0F;
		}
		return v;
	}

	private static String key(Player player) {
		return player.level().dimension().identifier().toString();
	}

	private static long day(Player player) {
		return player.level().getOverworldClockTime() / 24000L;
	}

	/** What was read last, per player: the tick, the dimension and the values (read once a tick at most). */
	private static final Map<Player, Object[]> READ = new java.util.WeakHashMap<>();

	/** The player's memory in this dimension (16 values, deaths, day), brought up to date with forgetting. A copy. */
	public static float[] of(Player player) {
		long now = player.level().getGameTime();
		Object[] read = READ.get(player);
		if (read != null && (long) read[0] == now && read[1] == player.level()) {
			return ((float[]) read[2]).clone();
		}
		float[] v = compute(player);
		READ.put(player, new Object[] {now, player.level(), v.clone()});
		return v;
	}

	private static float[] compute(Player player) {
		Map<String, List<Float>> all = player.getAttached(MEMORY);
		List<Float> raw = all == null ? null : all.get(key(player));
		float[] v = start();
		if (raw != null) {
			for (int i = 0; i < Math.min(raw.size(), v.length); i++) {
				v[i] = raw.get(i);
			}
		}
		long today = day(player);
		long last = raw == null ? today : (long) v[SIZE + 1];
		if (today > last) {
			float[] s = start();
			double keep = Math.pow(FORGET_PER_DAY, today - last);
			for (int i = 0; i < SIZE; i++) {
				v[i] = (float) (s[i] + keep * (v[i] - s[i]));
			}
			v[SIZE] = (float) (v[SIZE] * keep);
		}
		v[SIZE + 1] = today;
		return v;
	}

	private static void store(Player player, float[] v) {
		Map<String, List<Float>> all = player.getAttached(MEMORY);
		Map<String, List<Float>> next = all == null ? new HashMap<>() : new HashMap<>(all);
		List<Float> list = new ArrayList<>(v.length);
		for (float f : v) {
			list.add(f);
		}
		next.put(key(player), List.copyOf(list));
		player.setAttached(MEMORY, Map.copyOf(next));
		READ.remove(player);
	}

	/** Forgets it all, in this dimension (/forja ia mundo borrar). */
	public static void clear(Player player) {
		Map<String, List<Float>> all = player.getAttached(MEMORY);
		if (all != null) {
			Map<String, List<Float>> next = new HashMap<>(all);
			next.remove(key(player));
			player.setAttached(MEMORY, Map.copyOf(next));
		}
		READ.remove(player);
	}

	/** The 16 of block W, written from {@code at}. */
	public static void observe(Player player, float[] out, int at) {
		float[] v = of(Perception.real(player));
		System.arraycopy(v, 0, out, at, SIZE);
	}

	// ---------------------------------------------------------------- recording

	/** Which way the player killed a mob fighting them (see the class). */
	public static int cause(Mob mob, DamageSource source, Player player) {
		if (source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.DROWN)
			|| source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
			|| source.is(DamageTypes.SWEET_BERRY_BUSH)) {
			return TRAP;
		}
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() == player) {
			return FIRE;
		}
		Entity by = source.getEntity();
		if (by != player) {
			return OTHER;
		}
		if (Heights.of(player).overGround >= Heights.UP) {
			return HEIGHT;
		}
		if (source.getDirectEntity() instanceof Projectile) {
			return ARROW;
		}
		if (ObsV3.areaRadius(player.getMainHandItem()) > 0.0 || ObsV3.areaOnHit(player.getMainHandItem())) {
			return AREA;
		}
		if (Ambush.corridor(player) || Ambush.doorway(player)) {
			return NARROW;
		}
		return OPEN;
	}

	/** A mob fighting this player died (MobAi's death event): its cause of death, and what its life was worth. */
	public static void onDeath(Mob mob, DamageSource source) {
		MobMind mind = MobAi.mind(mob);
		Player player = mind != null && mind.target != null ? mind.target : source.getEntity() instanceof Player p ? p : null;
		if (player == null || player.level() != mob.level()) {
			return;
		}
		float[] v = of(player);
		int cause = cause(mob, source, player);
		for (int k = 0; k < 8; k++) {
			v[CAUSES + k] += (float) (RATE * ((k == cause ? 1.0 : 0.0) - v[CAUSES + k]));
		}
		v[SIZE] += 1.0F;
		v[CONFIDENCE] = Math.min(1.0F, v[SIZE] / FULL_CONFIDENCE);
		if (mind != null) {
			life(mind, v);
		}
		store(player, v);
	}

	/** A mob left the fight alive: what its life was worth counts all the same. */
	public static void onLeave(MobMind mind, Player player) {
		if (player == null || mind.lifeDamage <= 0.0F && total(mind.styleTicks) == 0) {
			return;
		}
		float[] v = of(player);
		life(mind, v);
		store(player, v);
	}

	private static void life(MobMind mind, float[] v) {
		int best = -1;
		int most = 0;
		for (int s = 0; s < mind.styleTicks.length; s++) {
			if (mind.styleTicks[s] > most) {
				most = mind.styleTicks[s];
				best = s;
			}
		}
		if (best >= 0) {
			double worth = Math.min(2.0, mind.lifeDamage / 10.0);
			v[SUCCESS + best] += (float) (RATE * (worth - v[SUCCESS + best]));
		}
		mind.lifeDamage = 0.0F;
		java.util.Arrays.fill(mind.styleTicks, 0);
	}

	private static int total(int[] counts) {
		int n = 0;
		for (int c : counts) {
			n += c;
		}
		return n;
	}

	/** Each decision: the way it is fighting now counts one more (for what works). */
	public static void count(MobMind mind) {
		int style = style(mind);
		if (style >= 0) {
			mind.styleTicks[style]++;
		}
	}

	/** The way of fighting a decision is (see the class), or -1 for none. */
	static int style(MobMind mind) {
		if (MobFamily.of(mind.mob) == MobFamily.ARQUERO) {
			return RANGED;
		}
		return switch (mind.decision.tactic()) {
			case ACERCARSE, RODEAR, FORMACION, CUBRIRSE, LIBRE -> FRONT;
			case FLANQUEAR, SECTOR -> FLANK;
			case TIRO_LIBRE -> RANGED;
			case EMBOSCAR, OCULTARSE -> AMBUSH;
			case ASEDIAR -> SIEGE;
			default -> -1;
		};
	}

	/** The damage a mob did to a player (MobAi's damage event). */
	public static void onHit(Mob mob, float damage) {
		MobMind mind = MobAi.mind(mob);
		if (mind != null && damage > 0.0F) {
			mind.lifeDamage += damage;
		}
	}

	/** A fight is over (Captain, its group gone): the player's style in it. */
	public static void onFightEnd(Player player, boolean wentUp, boolean ranOff) {
		if (player == null || !player.isAlive()) {
			return;
		}
		float[] v = of(player);
		v[STYLE] += (float) (RATE * ((wentUp ? 1.0 : 0.0) - v[STYLE]));
		v[STYLE + 1] += (float) (RATE * ((ranOff ? 1.0 : 0.0) - v[STYLE + 1]));
		store(player, v);
	}

	/** One line per value, for /forja ia mundo ver. */
	public static String describe(Player player) {
		float[] v = of(player);
		String[] names = ObsV4.names().subList(ObsV4.W_AT, ObsV4.W_AT + SIZE).toArray(new String[0]);
		StringBuilder out = new StringBuilder("Memoria del mundo (").append(key(player)).append("), ").append((int) v[SIZE]).append(" muertes:");
		for (int i = 0; i < SIZE; i++) {
			out.append(String.format(java.util.Locale.ROOT, "%n  %s = %.3f", names[i], v[i]));
		}
		return out.toString();
	}
}
