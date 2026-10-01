package com.dodoxgod.filo.combat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * "Turnos" de ataque: solo unos pocos mobs pueden atacar a la vez al mismo objetivo.
 * Los demás esperan rodeándote. Así una horda es peligrosa, pero no un linchamiento imposible de leer.
 */
public final class AttackTokens {
	private static final Map<LivingEntity, Set<MobEntity>> HOLDERS = new WeakHashMap<>();

	private AttackTokens() {
	}

	public static boolean tryAcquire(LivingEntity target, MobEntity mob, int max) {
		Set<MobEntity> holders = HOLDERS.computeIfAbsent(target,
				t -> Collections.newSetFromMap(new WeakHashMap<>()));
		holders.removeIf(m -> !m.isAlive() || m.isRemoved() || m.getTarget() != target);
		if (holders.contains(mob)) return true;
		if (holders.size() >= max) return false;
		holders.add(mob);
		return true;
	}

	public static void release(LivingEntity target, MobEntity mob) {
		if (target == null) return;
		Set<MobEntity> holders = HOLDERS.get(target);
		if (holders != null) holders.remove(mob);
	}
}
