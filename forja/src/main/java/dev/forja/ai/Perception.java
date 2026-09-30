package dev.forja.ai;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mojang.authlib.GameProfile;
import dev.forja.combat.Stamina;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Honest perception in the v4 observation (docs/red_mob_v4_diseno.md §2.4 and §4.5): what a mob knows of its player
 * when it does not perceive them.
 *
 * <ul>
 *   <li><b>Perceived</b> (obj_percibido): this tick, a line from its eyes to theirs, within the thinking range, and not
 *   hidden by the night rule (WorldFights.hiddenByNight). MobAi notes it in {@link MobMind#perceivedAt}.</li>
 *   <li><b>The estimate</b>: where it last saw them, or where it last heard them if that is newer (Hearing).</li>
 *   <li><b>The stand-in</b>: while it does not perceive them, a v4 network's observation (all 468 inputs, v3b's
 *   included) is worked out against a stand-in player standing at the estimate, with what it had of them when it last
 *   saw them: health, what they held and wore, where they looked, running or crouching, their stamina. Their real
 *   position never reaches the network. The stand-in is one fake player per level, set up for each mob in turn;
 *   what goes by who the player is rather than where (the group fighting them, the turns on them) reads the real
 *   player through {@link #real}.</li>
 * </ul>
 */
public final class Perception {
	/** How far a mob keeps hunting a player it has lost (the design's 48), and for how long (Personality.BORED_TICKS). */
	public static final double HUNT_RANGE = 48.0;
	/** The player's cone of sight, half its angle (a cone of 70°): me_ve_jugador, jug_sin_vernos. */
	public static final double CONE_COS = Math.cos(Math.toRadians(35.0));
	/** Seen in the dark only this close (me_ve_jugador: light ≥ 4 or nearer than 8). */
	public static final double DARK_SIGHT = 8.0;
	public static final int DARK = 4;

	/** What a mob had of its player when it last perceived them. */
	public static final class Snapshot {
		public Vec3 pos;
		public Vec3 motion = Vec3.ZERO;
		public float yRot;
		public float xRot;
		public float health;
		public float maxHealth;
		public float stamina;
		public boolean sprinting;
		public boolean crouching;
		public boolean onGround;
		public final ItemStack[] equipment = new ItemStack[SLOTS.length];
		public long at;
	}

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
		EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final GameProfile GHOST = new GameProfile(UUID.fromString("0f0f7a11-0000-4000-8000-00000000f04a"), "forja_estimacion");
	/** The real player the stand-in is standing for right now, or null. */
	private static Player standingFor;
	private static Player ghost;
	/** Per player: when some member of the group fighting them was last in their cone of sight. */
	private static final Map<Player, Long> SAW_GROUP = new WeakHashMap<>();

	private Perception() {
	}

	/** Whether it perceives its player this tick. */
	public static boolean perceived(MobMind mind, long now) {
		return mind.perceivedAt == now;
	}

	/** When it last knew where its player was (saw or heard them), or MIN for never. */
	public static long estimateAt(MobMind mind) {
		return Math.max(mind.lastSeenAt, mind.lastHeard == null ? Long.MIN_VALUE / 2 : mind.lastHeardAt);
	}

	/** Where it thinks its player is: where it last saw them, or where it last heard them if that is newer; null for neither. */
	public static Vec3 estimate(MobMind mind) {
		if (mind.lastHeard != null && (mind.lastSeen == null || mind.lastHeardAt > mind.lastSeenAt)) {
			return mind.lastHeard;
		}
		return mind.lastSeen;
	}

	/** Takes down what it perceives of its player now. */
	public static void take(MobMind mind, Player player, long now) {
		Snapshot s = mind.snapshot == null ? new Snapshot() : mind.snapshot;
		s.pos = player.position();
		s.motion = player.getDeltaMovement();
		s.yRot = player.getYRot();
		s.xRot = player.getXRot();
		s.health = player.getHealth();
		s.maxHealth = player.getMaxHealth();
		s.stamina = Stamina.value(player);
		s.sprinting = player.isSprinting();
		s.crouching = player.isShiftKeyDown();
		s.onGround = player.onGround();
		for (int i = 0; i < SLOTS.length; i++) {
			s.equipment[i] = player.getItemBySlot(SLOTS[i]);
		}
		s.at = now;
		mind.snapshot = s;
	}

	/**
	 * The stand-in for a player this mob does not perceive: at its estimate, as it last saw them. Until {@link #release}
	 * it stands for {@code real}. Null when there is nothing to stand in with (never seen).
	 */
	public static Player standIn(MobMind mind, Player real) {
		Snapshot s = mind.snapshot;
		Vec3 at = estimate(mind);
		if (s == null || at == null || !(real.level() instanceof ServerLevel level)) {
			return null;
		}
		if (ghost == null || ghost.level() != level) {
			ghost = FakePlayer.get(level, GHOST);
		}
		Player g = ghost;
		g.setPos(at.x, at.y, at.z);
		g.setYRot(s.yRot);
		g.setYHeadRot(s.yRot);
		g.setXRot(s.xRot);
		g.setDeltaMovement(Vec3.ZERO);
		g.setHealth(Math.min(g.getMaxHealth(), s.health));
		g.setSprinting(s.sprinting);
		g.setShiftKeyDown(s.crouching);
		g.setPose(s.crouching ? Pose.CROUCHING : Pose.STANDING);
		g.setOnGround(s.onGround);
		for (int i = 0; i < SLOTS.length; i++) {
			g.setItemSlot(SLOTS[i], s.equipment[i] == null ? ItemStack.EMPTY : s.equipment[i]);
		}
		Stamina.set(g, s.stamina);
		// what is kept per player is about the real one's surroundings: the stand-in's is worked out afresh where it stands
		Heights.forget(g);
		Lights.forget(g);
		ObsV4.forgetLook(g);
		standingFor = real;
		return g;
	}

	/** The stand-in stands for nobody any more. */
	public static void release() {
		standingFor = null;
	}

	/** The real player behind a stand-in, or the player itself. */
	public static Player real(Player player) {
		return player != null && player == ghost && standingFor != null ? standingFor : player;
	}

	/** Whether this is the stand-in. */
	public static boolean isStandIn(Player player) {
		return player != null && player == ghost;
	}

	/**
	 * me_ve_jugador: the player could notice the mob now: a clear line from their eyes to its, it inside their cone of
	 * 70°, and in a light of 4 or more where it stands, or nearer than 8.
	 */
	public static boolean seenBy(Mob mob, Player player) {
		Vec3 eyes = player.getEyePosition();
		Vec3 to = mob.getEyePosition().subtract(eyes);
		double d = to.length();
		if (d < 1.0E-4) {
			return true;
		}
		if (to.scale(1.0 / d).dot(player.getViewVector(1.0F)) < CONE_COS) {
			return false;
		}
		if (d >= DARK_SIGHT && mob.level().getMaxLocalRawBrightness(mob.blockPosition()) < DARK) {
			return false;
		}
		return mob.level().clip(new ClipContext(eyes, mob.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
			.getType() == HitResult.Type.MISS;
	}

	/** Each Squad pass (every 10 ticks): whether the player has any of the group fighting them in their sight. */
	static void watchGroup(Player player, java.util.List<MobMind> members, long now) {
		for (MobMind mind : members) {
			if (seenBy(mind.mob, player)) {
				SAW_GROUP.put(player, now);
				return;
			}
		}
		SAW_GROUP.putIfAbsent(player, now);
	}

	/** Ticks since the player last had one of the group fighting them in sight (jug_sin_vernos, before the /200). */
	public static long sinceSawGroup(Player player, long now) {
		Long at = SAW_GROUP.get(player);
		return at == null ? 0 : Math.max(0, now - at);
	}
}
