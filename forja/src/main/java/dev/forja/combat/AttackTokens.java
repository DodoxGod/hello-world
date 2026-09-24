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
		Set<Mob> holders = HOLDERS.computeIfAbsent(target, t -> Collections.newSetFromMap(new WeakHashMap<>()));
		holders.removeIf(m -> !m.isAlive() || m.isRemoved() || m.getTarget() != target);
		if (holders.contains(mob)) return true;
		if (holders.size() >= max) return false;
		holders.add(mob);
		return true;
	}

	public static void release(LivingEntity target, Mob mob) {
		if (target == null) return;
		Set<Mob> holders = HOLDERS.get(target);
		if (holders != null) holders.remove(mob);
	}
}
