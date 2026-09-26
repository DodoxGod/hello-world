package dev.forja.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Duels (idea 96). An elite or a champion fighting a player alongside at least two others may step
 * forward and challenge them: a roar, a ring of fire 6 blocks across on the ground, and the others stand
 * back round it and watch.
 * <ul>
 *   <li>The player <b>accepts</b> by stepping into the ring or striking the challenger; <b>refuses</b> by
 *   letting 5 seconds go by, or by shooting at it from outside. A refusal enrages the whole group.</li>
 *   <li>During the duel, leaving the ring for 2 seconds, striking one of the watchers, or another player
 *   striking the challenger <b>breaks</b> it: the watchers join in, enraged.</li>
 *   <li>Beating the challenger scatters the watchers and pays an extra roll of its loot and 10 mastery; a
 *   player who dies or runs leaves it as a named foe who will come back (see {@link WorldFights#remember}).</li>
 * </ul>
 */
public final class Duels {
	public static final double RING = 6.0;
	public static final int ANSWER_TICKS = 100;
	public static final int OUTSIDE_TICKS = 40;
	public static final double CHALLENGE_CHANCE = 0.005;

	public enum Stage { RETO, DUELO }

	/** One duel under way. */
	public static final class Duel {
		public final Mob challenger;
		public final ServerPlayer player;
		public final Vec3 center;
		public Stage stage = Stage.RETO;
		public long since;
		int outside;

		Duel(Mob challenger, ServerPlayer player, long now) {
			this.challenger = challenger;
			this.player = player;
			this.center = challenger.position();
			this.since = now;
		}
	}

	private static final List<Duel> DUELS = new ArrayList<>();

	private Duels() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Duels::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> onHit(entity, source));
		ServerLivingEntityEvents.AFTER_DEATH.register(Duels::onDeath);
	}

	public static Duel of(LivingEntity entity) {
		for (Duel duel : DUELS) {
			if (duel.challenger == entity || duel.player == entity) {
				return duel;
			}
		}
		return null;
	}

	/** Whether this mob is watching a duel (it keeps out of the ring and takes no turn). */
	public static boolean watching(Mob mob) {
		if (!(mob.getTarget() instanceof ServerPlayer player)) {
			return false;
		}
		Duel duel = of(player);
		return duel != null && duel.challenger != mob;
	}

	public static boolean challenger(Mob mob) {
		Duel duel = of(mob);
		return duel != null && duel.challenger == mob;
	}

	/** Under the rules: an elite or champion with company, close to its player, may challenge them. */
	public static boolean mayChallenge(MobMind mind, Player target) {
		Mob mob = mind.mob;
		if (!(target instanceof ServerPlayer player) || of(player) != null || of(mob) != null
			|| Threat.of(mob).ordinal() < Threat.ELITE.ordinal() || mob.distanceTo(target) > 10.0
			|| mob.getRandom().nextDouble() >= CHALLENGE_CHANCE) {
			return false;
		}
		long company = mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(16.0),
			m -> m != mob && m.isAlive() && m.getTarget() == target).size();
		if (company < 2) {
			return false;
		}
		start(mob, player);
		return true;
	}

	public static Duel start(Mob challenger, ServerPlayer player) {
		Duel duel = new Duel(challenger, player, challenger.level().getGameTime());
		DUELS.add(duel);
		String name = challenger.hasCustomName() ? challenger.getCustomName().getString() : challenger.getType().getDescription().getString();
		player.sendOverlayMessage(Component.translatable("gui.forja.duelo.reto", name));
		challenger.level().playSound(null, challenger.getX(), challenger.getY(), challenger.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.8F);
		return duel;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Duel> it = DUELS.iterator(); it.hasNext();) {
			Duel duel = it.next();
			if (!(duel.challenger.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (!duel.challenger.isAlive() || !duel.player.isAlive() || duel.player.level() != level) {
				if (!duel.player.isAlive() && duel.challenger.isAlive()) {
					WorldFights.remember(duel.challenger, duel.player);
				}
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (now % 5 == 0) {
				WorldFights.ring(level, duel.center, RING);
			}
			boolean inside = duel.player.position().distanceTo(duel.center) <= RING;
			if (duel.stage == Stage.RETO) {
				if (inside) {
					accept(duel, now);
				} else if (now - duel.since > ANSWER_TICKS) {
					refuse(duel);
					it.remove();
				}
				continue;
			}
			duel.outside = inside ? 0 : duel.outside + 1;
			if (duel.outside > OUTSIDE_TICKS) {
				// Ran for it: the duel is off and the challenger will remember.
				WorldFights.remember(duel.challenger, duel.player);
				broken(duel, "gui.forja.duelo.huida");
				it.remove();
			}
		}
	}

	private static void accept(Duel duel, long now) {
		duel.stage = Stage.DUELO;
		duel.since = now;
		duel.challenger.setTarget(duel.player);
		duel.player.sendOverlayMessage(Component.translatable("gui.forja.duelo.aceptado"));
	}

	private static void refuse(Duel duel) {
		duel.player.sendOverlayMessage(Component.translatable("gui.forja.duelo.rechazado"));
		enrage(duel);
	}

	private static void broken(Duel duel, String key) {
		duel.player.sendOverlayMessage(Component.translatable(key));
		enrage(duel);
	}

	/** Every watcher goes for the player, pressing harder. */
	private static void enrage(Duel duel) {
		for (Mob other : duel.challenger.level().getEntitiesOfClass(Mob.class, duel.challenger.getBoundingBox().inflate(16.0),
			m -> m.isAlive() && m.getTarget() == duel.player)) {
			other.addTag("forja_enfurecido");
			MobMind mind = MobAi.mind(other);
			if (mind != null) {
				mind.enraged = true;
			}
		}
	}

	private static void onHit(LivingEntity entity, DamageSource source) {
		for (Iterator<Duel> it = DUELS.iterator(); it.hasNext();) {
			Duel duel = it.next();
			if (entity == duel.challenger && source.getEntity() == duel.player) {
				if (duel.stage == Stage.RETO) {
					boolean fromOutside = duel.player.position().distanceTo(duel.center) > RING
						&& source.getDirectEntity() != duel.player;
					if (fromOutside) {
						refuse(duel);
						it.remove();
					} else {
						accept(duel, entity.level().getGameTime());
					}
				}
			} else if (duel.stage == Stage.DUELO && source.getEntity() == duel.player && entity instanceof Mob watcher
				&& watcher.getTarget() == duel.player) {
				broken(duel, "gui.forja.duelo.trampa");
				it.remove();
			} else if (duel.stage == Stage.DUELO && entity == duel.challenger && source.getEntity() instanceof Player other && other != duel.player) {
				broken(duel, "gui.forja.duelo.trampa");
				it.remove();
			}
		}
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		for (Iterator<Duel> it = DUELS.iterator(); it.hasNext();) {
			Duel duel = it.next();
			if (entity == duel.challenger && duel.stage == Stage.DUELO && source.getEntity() == duel.player
				&& entity.level() instanceof ServerLevel level) {
				it.remove();
				duel.player.sendOverlayMessage(Component.translatable("gui.forja.duelo.victoria"));
				// The watchers lose heart; the winner is paid.
				for (Mob other : level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(16.0),
					m -> m.isAlive() && m.getTarget() == duel.player)) {
					Personality.scare(other, level.getGameTime());
				}
				if (entity instanceof Mob mob) {
					mob.getLootTable().ifPresent(table -> mob.dropFromLootTable(level, source, true, table));
				}
				dev.forja.forge.Mastery.addExperience(duel.player, duel.player.getMainHandItem(), 10);
			}
		}
	}

	static boolean active() {
		return !DUELS.isEmpty() && CombatConfig.get().enabled;
	}
}
