package dev.forja.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.combat.AttackTokens;
import dev.forja.combat.CombatConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The mod's rules when a monster fights something that is not a player (Andy, 2026-09-29: "sí, las reglas del
 * mod también contra otros mobs"). The networks were only ever trained against players, so against a golem, a
 * villager or another monster it is the rules: the warning before a blow and the turns ({@code
 * MeleeAttackGoalMixin}), and this ring - the ones without a turn wait round the target, evenly spaced, instead
 * of piling into it.
 *
 * <p>Deliberately simpler than {@link Squad}: no roles, no flanker, no bait. Slots are handed out by entity id,
 * so they never swap while the fight lasts, from a start angle kept per target (the side the first came from).
 */
public final class MobRing {
	/** How far round the ring the wait is, beyond the attack's reach. */
	public static final double WAIT_OUTSIDE = 1.5;
	/** How far round a target the fight is counted, and how often each mob works its slot out again, in ticks. */
	public static final double GATHER = 16.0;
	public static final int PERIOD = 10;

	private static final Map<LivingEntity, Double> START = new WeakHashMap<>();
	private static final Map<Mob, Slot> SLOTS = new WeakHashMap<>();

	private record Slot(LivingEntity target, double angle, double radius, long at) {
	}

	private MobRing() {
	}

	/** Whether this fight is one for the ring: a warning mob on something that is not a player. */
	public static boolean applies(Mob mob, @Nullable LivingEntity target) {
		return target != null && !(target instanceof Player) && target.isAlive() && CombatConfig.get().enabled
			&& AttackTokens.warns(mob);
	}

	/** Whether the mob should wait on the ring rather than go in: others hold every turn on this target. */
	public static boolean waits(Mob mob, LivingEntity target) {
		return !AttackTokens.holds(target, mob) && !AttackTokens.free(target, Aggression.maxAttackers(mob, target));
	}

	/** Where on the ring round {@code target} this mob waits. */
	public static Vec3 place(Mob mob, LivingEntity target) {
		long now = mob.level().getGameTime();
		Slot slot = SLOTS.get(mob);
		if (slot == null || slot.target != target || now - slot.at >= PERIOD) {
			slot = assign(mob, target, now);
			SLOTS.put(mob, slot);
		}
		return new Vec3(target.getX() + Math.cos(slot.angle) * slot.radius, target.getY(), target.getZ() + Math.sin(slot.angle) * slot.radius);
	}

	private static Slot assign(Mob mob, LivingEntity target, long now) {
		List<Mob> ring = mob.level().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(GATHER),
			other -> other.isAlive() && other.getTarget() == target && AttackTokens.warns(other));
		ring.sort(Comparator.comparingInt(Mob::getId));
		int n = Math.max(1, ring.size());
		int index = Math.max(0, ring.indexOf(mob));
		double start = START.computeIfAbsent(target, t -> Math.atan2(mob.getZ() - t.getZ(), mob.getX() - t.getX()));
		double radius = Squad.ringRadius(n) + WAIT_OUTSIDE;
		return new Slot(target, start + index * 2.0 * Math.PI / n, radius, now);
	}
}
