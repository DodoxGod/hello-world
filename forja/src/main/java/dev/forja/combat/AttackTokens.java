package dev.forja.combat;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Turns: only a few mobs may swing at the same target at once, the rest circle and wait. A horde
 * stays dangerous without turning into a pile-on nobody can read.
 */
public final class AttackTokens {
	private static final Map<LivingEntity, Set<Mob>> HOLDERS = new WeakHashMap<>();

	private AttackTokens() {
	}

	public static boolean tryAcquire(LivingEntity target, Mob mob, int max) {
		// A staggered mob cannot swing, so it does not get to hold a turn it cannot use; nor one spent after a fury.
		if (Posture.isStaggered(mob, mob.level().getGameTime()) || dev.forja.ai.Fury.spent(mob)) return false;
		Set<Mob> holders = HOLDERS.computeIfAbsent(target, t -> Collections.newSetFromMap(new WeakHashMap<>()));
		holders.removeIf(m -> !current(m, target));
		if (holders.contains(mob)) return true;
		if (holders.size() >= max) return false;
		holders.add(mob);
		return true;
	}

	/**
	 * A turn only counts while its mob is alive and still after that target. One that turned on somebody
	 * else let go of it the moment it did: with two players about, the first one's turns used to go on
	 * counting it (in the network's "turnos_ocupados" and "tengo_turno" too) until some other mob happened
	 * to ask for a turn on them.
	 */
	private static boolean current(Mob mob, LivingEntity target) {
		return mob.isAlive() && !mob.isRemoved() && mob.getTarget() == target;
	}

	/** Whether this mob holds one of the target's turns right now. */
	public static boolean holds(LivingEntity target, Mob mob) {
		Set<Mob> holders = HOLDERS.get(target);
		return holders != null && holders.contains(mob) && current(mob, target);
	}

	/** Whether a turn on this target is free. */
	public static boolean free(LivingEntity target, int max) {
		return held(target) < max;
	}

	/** How many mobs hold a turn on this target. */
	public static int held(LivingEntity target) {
		Set<Mob> holders = HOLDERS.get(target);
		if (holders == null) return 0;
		holders.removeIf(m -> !current(m, target));
		return holders.size();
	}

	public static void release(LivingEntity target, Mob mob) {
		if (target == null) return;
		Set<Mob> holders = HOLDERS.get(target);
		if (holders != null) holders.remove(mob);
	}

	/**
	 * Lets go of every turn this mob holds, on whoever.
	 *
	 * <p>Called the moment a stagger lands. The network's goal already dropped its warning when it was
	 * staggered but kept the turn until its next blow, so in a group one stunned mob stood there
	 * holding a turn it could not use and the rest of the pack waited behind it.
	 */
	public static void releaseAll(Mob mob) {
		for (Set<Mob> holders : HOLDERS.values()) {
			holders.remove(mob);
		}
	}

	/**
	 * Whether this mob's plain melee blow comes with the warning: vanilla's monsters and Forja's own.
	 *
	 * <p>Forja's used to be left out, on the idea that their Windup specials were telegraph enough. But
	 * their ordinary blow landed with no warning at all, while a network-driven mob of the same family
	 * always warned — so the rules were being measured against the network with a head start. Andy's
	 * call: everybody warns. Other mods' mobs are left as they come. Vanilla's warn only at a level with Forja's
	 * rules (Ladder: Normal and up); Forja's own always.
	 */
	public static boolean warns(Mob mob) {
		String namespace = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace();
		return "minecraft".equals(namespace) && dev.forja.difficulty.Ladder.current().rules || dev.forja.Forja.MOD_ID.equals(namespace);
	}
}
