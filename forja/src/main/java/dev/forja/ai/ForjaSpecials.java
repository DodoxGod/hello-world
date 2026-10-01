package dev.forja.ai;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import dev.forja.entity.CoalHauler;
import dev.forja.entity.EmberWisp;
import dev.forja.entity.FallenSmith;
import dev.forja.entity.ForgeAutomaton;
import dev.forja.entity.HollowArmor;
import dev.forja.entity.StarCore;
import dev.forja.entity.Striker;
import dev.forja.entity.Tongs;
import dev.forja.entity.WalkingAnvil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The movesets of the mod's own monsters, for their networks. Each special starts one of the monster's
 * own moves (which carry their own warnings, drawn from their animations), so the warning here is 0. The
 * rules never start them (their chance is 0): the monsters' own AI already does, on its own timers.
 */
public final class ForjaSpecials {
	private ForjaSpecials() {
	}

	/** A move of the monster's own, wrapped: range [min, max] to the target, and how to start it. */
	private static <T extends Mob> Special own(String id, Class<T> type, double min, double max, int cooldownMin, int cooldownMax,
		BiConsumer<T, Player> start) {
		return own(id, type, (mob, target) -> {
			double d = mob.distanceTo(target);
			return d >= min && d <= max;
		}, cooldownMin, cooldownMax, start);
	}

	private static <T extends Mob> Special own(String id, Class<T> type, BiPredicate<T, Player> can, int cooldownMin, int cooldownMax,
		BiConsumer<T, Player> start) {
		return new Special(id, 0, cooldownMin, cooldownMax, 0.0) {
			@Override
			public boolean canStart(Mob mob, Player target) {
				return type.isInstance(mob) && can.test(type.cast(mob), target);
			}

			@Override
			public void release(Mob mob, Player target, SpecialRunner.Run run) {
				start.accept(type.cast(mob), target);
			}
		};
	}

	private static ServerLevel level(Mob mob) {
		return (ServerLevel) mob.level();
	}

	public static final Special SMITH_BACKHAND = own("reves_herrero", FallenSmith.class, 0.0, 4.0, 60, 100, (m, t) -> m.backhand(level(m)));
	public static final Special SMITH_WAVE = own("onda_herrero", FallenSmith.class, 0.0, 12.0, 160, 240, (m, t) -> m.anvilWave(level(m)));
	public static final Special AUTOMATON_STOMP = own("pisoton_automata", ForgeAutomaton.class, 0.0, 5.0, 100, 160, (m, t) -> m.slagStomp(level(m), t));
	public static final Special AUTOMATON_STEAM = own("vapor_automata", ForgeAutomaton.class, 0.0, 3.0, 200, 300, (m, t) -> m.steamPurge(level(m)));
	public static final Special HOLLOW_LUNGE = own("embestida_coraza", HollowArmor.class, 3.0, 9.0, 100, 160, (m, t) -> m.lunge(level(m), t));
	public static final Special HOLLOW_WAIL = own("lamento_coraza", HollowArmor.class, 0.0, 16.0, 300, 400, (m, t) -> m.wail(level(m)));
	public static final Special WISP_DIVE = own("picado_pavesa", EmberWisp.class, 3.0, 12.0, 100, 160, (m, t) -> m.dive(level(m)));
	public static final Special STRIKER_DROP = own("martillazo_percutor", Striker.class, 0.0, 4.0, 80, 140, (m, t) -> m.drop(level(m)));
	public static final Special TONGS_GRAB = own("agarre_tenaza", Tongs.class, 0.0, Tongs.GRAB_MAX, 120, 200, (m, t) -> m.grab(level(m), t));
	public static final Special CORE_RELEASE = own("descarga_nucleo", StarCore.class, 0.0, 20.0, 120, 200, (m, t) -> m.release(level(m)));
	public static final Special HAULER_PRIME = own("carga_carbon", CoalHauler.class, 0.0, 3.0, 400, 400, (m, t) -> m.prime(level(m)));
	public static final Special ANVIL_WELD = own("soldar_yunque", WalkingAnvil.class, 0.0, 10.0, 200, 300, (m, t) -> m.weld(level(m)));

	public static List<Special> of(Mob mob) {
		if (mob instanceof FallenSmith) return List.of(SMITH_BACKHAND, SMITH_WAVE);
		if (mob instanceof ForgeAutomaton) return List.of(AUTOMATON_STOMP, AUTOMATON_STEAM);
		if (mob instanceof HollowArmor) return List.of(HOLLOW_LUNGE, HOLLOW_WAIL);
		if (mob instanceof EmberWisp) return List.of(WISP_DIVE);
		if (mob instanceof Striker) return List.of(STRIKER_DROP);
		if (mob instanceof Tongs) return List.of(TONGS_GRAB);
		if (mob instanceof StarCore) return List.of(CORE_RELEASE);
		if (mob instanceof CoalHauler) return List.of(HAULER_PRIME);
		if (mob instanceof WalkingAnvil) return List.of(ANVIL_WELD);
		return List.of();
	}
}
