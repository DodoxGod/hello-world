package dev.forja.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Memory and personality (ideas 61 to 70 of the plan). Everything a mob carries across a fight lives in
 * entity tags, so it survives a reload:
 * <ul>
 *   <li>a <b>trait</b> (agresivo, prudente, cobarde, astuto), rolled at spawn, at night leaning against
 *   the nearest player's habits;</li>
 *   <li><b>grudge</b>: a mob that got away from a player alive remembers them, hits them harder and
 *   goes for them first;</li>
 *   <li><b>veteranía</b>: every fight it survives counts; after three it becomes a veteran;</li>
 *   <li><b>home</b>: mobs born of a structure or a spawner defend it (fiercer within 24 blocks of it, and
 *   they go back when the fight is over).</li>
 * </ul>
 * Fear (a player who kills three of them in ten seconds scatters the rest), leaders who never retreat,
 * rivalry (the mod's monsters and vanilla's do not call each other), boredom (a player unseen for 30
 * seconds is given up) and curiosity (a forge worked at night draws the idle ones to look) are here too.
 */
public final class Personality {
	public enum Trait { AGRESIVO, PRUDENTE, COBARDE, ASTUTO }

	public static final String TRAIT_TAG = "forja_rasgo_";
	public static final String GRUDGE_TAG = "forja_rencor_";
	public static final String FIGHTS_TAG = "forja_peleas_";
	public static final String HOME_TAG = "forja_hogar_";
	public static final int VETERAN_FIGHTS = 3;
	public static final double HOME_RADIUS = 24.0;
	public static final int FEAR_KILLS = 3;
	public static final int FEAR_WINDOW = 200;
	public static final int FEAR_TICKS = 100;
	public static final double FEAR_RANGE = 12.0;
	public static final int BORED_TICKS = 600;
	public static final double CURIOUS_RANGE = 24.0;
	public static final int CURIOUS_TICKS = 200;

	private static final Map<Player, List<Long>> KILLS = new WeakHashMap<>();
	private static final Map<Mob, Long> AFRAID_UNTIL = new WeakHashMap<>();
	/** A forge being worked: where, and until when it draws the idle ones. */
	private static final List<Object[]> NOISES = new ArrayList<>();

	private Personality() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob mob && entity instanceof Enemy && source.getEntity() instanceof Player player) {
				onKill(mob, player);
			}
		});
		// 70: a forge worked at night is heard.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel server && server.isDarkOutside()
				&& "forja".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(hit.getBlockPos()).getBlock()).getNamespace())) {
				noise(server, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});
	}

	// --- 61: traits ---------------------------------------------------------------------------------

	public static Trait trait(LivingEntity mob) {
		for (Trait trait : Trait.values()) {
			if (mob.entityTags().contains(TRAIT_TAG + trait.name().toLowerCase(java.util.Locale.ROOT))) {
				return trait;
			}
		}
		return Trait.AGRESIVO;
	}

	/**
	 * Rolls a trait at spawn: 40 % aggressive, 25 % careful, 15 % cowardly, 20 % cunning. At night the
	 * rolls lean against the nearest player (idea 63): against one who parries a lot, more cunning ones
	 * (they feint); against one who dodges a lot, more aggressive ones (they press).
	 */
	public static void roll(Mob mob, ServerLevel level) {
		double aggressive = 0.40;
		double careful = 0.25;
		double coward = 0.15;
		double cunning = 0.20;
		Player near = level.getNearestPlayer(mob, 64.0);
		if (near != null && level.isDarkOutside()) {
			cunning += 0.3 * PlayerHabits.get(near, PlayerHabits.PARRY);
			aggressive += 0.3 * PlayerHabits.get(near, PlayerHabits.DODGE);
		}
		double roll = mob.getRandom().nextDouble() * (aggressive + careful + coward + cunning);
		Trait trait = roll < aggressive ? Trait.AGRESIVO : roll < aggressive + careful ? Trait.PRUDENTE
			: roll < aggressive + careful + coward ? Trait.COBARDE : Trait.ASTUTO;
		mob.addTag(TRAIT_TAG + trait.name().toLowerCase(java.util.Locale.ROOT));
	}

	/** Share of its health below which a mob backs off, by trait. */
	public static double retreatHealth(Mob mob) {
		return switch (trait(mob)) {
			case AGRESIVO -> 0.1;
			case PRUDENTE -> 0.25;
			case COBARDE -> 0.4;
			case ASTUTO -> 0.2;
		};
	}

	// --- 62: grudge, 64: veterans -------------------------------------------------------------------------

	public static boolean grudge(LivingEntity mob, Player player) {
		return mob.entityTags().contains(GRUDGE_TAG + player.getUUID());
	}

	public static int fights(LivingEntity mob) {
		for (String tag : mob.entityTags()) {
			if (tag.startsWith(FIGHTS_TAG)) {
				try {
					return Integer.parseInt(tag.substring(FIGHTS_TAG.length()));
				} catch (NumberFormatException ignored) {
					return 0;
				}
			}
		}
		return 0;
	}

	/**
	 * A fight is over and the mob is still alive (it lost its player): it counts, and if it was hurt in it, it
	 * holds a grudge. The third fight survived makes it a veteran.
	 */
	public static void survived(Mob mob, Player player) {
		int before = fights(mob);
		mob.removeTag(FIGHTS_TAG + before);
		mob.addTag(FIGHTS_TAG + (before + 1));
		if (mob.getHealth() < mob.getMaxHealth()) {
			mob.addTag(GRUDGE_TAG + player.getUUID());
		}
		if (before + 1 >= VETERAN_FIGHTS && dev.forja.difficulty.Threat.of(mob) == dev.forja.difficulty.Threat.NORMAL) {
			dev.forja.difficulty.Threat.VETERANO.mark(mob);
			mob.setCustomName(Component.translatable("entity.forja.amenaza.veterano", mob.getType().getDescription()));
		}
	}

	/** Multiplier on the damage a mob deals this player: grudge and experience make it hit harder. */
	public static double damage(Mob mob, Player player) {
		double m = 1.0 + 0.05 * Math.min(fights(mob), 6);
		if (grudge(mob, player)) {
			m *= 1.15;
		}
		if (trait(mob) == Trait.AGRESIVO) {
			m *= 1.05;
		}
		if (atHome(mob)) {
			m *= 1.1;
		}
		return m;
	}

	// --- 65: fear, 66: leaders -------------------------------------------------------------------------------

	private static void onKill(Mob dead, Player player) {
		long now = dead.level().getGameTime();
		List<Long> kills = KILLS.computeIfAbsent(player, p -> new ArrayList<>());
		kills.add(now);
		kills.removeIf(t -> now - t > FEAR_WINDOW);
		if (kills.size() < FEAR_KILLS) {
			return;
		}
		kills.clear();
		for (Mob other : dead.level().getEntitiesOfClass(Mob.class, dead.getBoundingBox().inflate(FEAR_RANGE),
			m -> m != dead && m.isAlive() && m instanceof Enemy)) {
			if (trait(other) != Trait.AGRESIVO && dev.forja.difficulty.Threat.of(other).ordinal() < dev.forja.difficulty.Threat.ELITE.ordinal()) {
				AFRAID_UNTIL.put(other, now + FEAR_TICKS);
			}
		}
	}

	public static boolean afraid(Mob mob) {
		return AFRAID_UNTIL.getOrDefault(mob, Long.MIN_VALUE) > mob.level().getGameTime();
	}

	/** Elites and champions never fall back, whatever happens around them. */
	public static boolean fearless(Mob mob) {
		return dev.forja.difficulty.Threat.of(mob).ordinal() >= dev.forja.difficulty.Threat.ELITE.ordinal()
			|| dev.forja.difficulty.Bosses.isBoss(mob);
	}

	// --- 67: rivalry ---------------------------------------------------------------------------------

	/** The mod's monsters and vanilla's are not on the same side: they do not answer each other's calls. */
	public static boolean sameSide(Mob a, Mob b) {
		return ForjaTraits.forja(a) == ForjaTraits.forja(b);
	}

	// --- 68: home --------------------------------------------------------------------------------------

	/** Mobs born of a structure, a spawner or the world's generation have a home to defend. */
	public static void settle(Mob mob, EntitySpawnReason reason) {
		if (reason == EntitySpawnReason.STRUCTURE || reason == EntitySpawnReason.SPAWNER || reason == EntitySpawnReason.TRIAL_SPAWNER
			|| reason == EntitySpawnReason.CHUNK_GENERATION) {
			BlockPos home = mob.blockPosition();
			mob.addTag(HOME_TAG + home.getX() + "_" + home.getY() + "_" + home.getZ());
		}
	}

	public static BlockPos home(LivingEntity mob) {
		for (String tag : mob.entityTags()) {
			if (tag.startsWith(HOME_TAG)) {
				String[] xyz = tag.substring(HOME_TAG.length()).split("_");
				try {
					return new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
				} catch (RuntimeException ignored) {
					return null;
				}
			}
		}
		return null;
	}

	public static boolean atHome(LivingEntity mob) {
		BlockPos home = home(mob);
		return home != null && mob.blockPosition().distSqr(home) <= HOME_RADIUS * HOME_RADIUS;
	}

	// --- 70: curiosity -------------------------------------------------------------------------------

	private static void noise(ServerLevel level, BlockPos at) {
		NOISES.removeIf(n -> level.getGameTime() > (Long) n[2]);
		NOISES.add(new Object[] {level, Vec3.atCenterOf(at), level.getGameTime() + CURIOUS_TICKS});
	}

	/** The nearest forge being worked that this idle mob can hear, or null. */
	public static Vec3 heard(Mob mob) {
		Vec3 best = null;
		double bestSq = CURIOUS_RANGE * CURIOUS_RANGE;
		for (Object[] noise : NOISES) {
			if (noise[0] != mob.level() || mob.level().getGameTime() > (Long) noise[2]) {
				continue;
			}
			Vec3 at = (Vec3) noise[1];
			double sq = mob.distanceToSqr(at);
			if (sq < bestSq) {
				bestSq = sq;
				best = at;
			}
		}
		return best;
	}

	/** Whether the player is the one this mob holds a grudge against (used to pick targets). */
	public static UUID grudgeTarget(LivingEntity mob) {
		for (String tag : mob.entityTags()) {
			if (tag.startsWith(GRUDGE_TAG)) {
				try {
					return UUID.fromString(tag.substring(GRUDGE_TAG.length()));
				} catch (IllegalArgumentException ignored) {
					return null;
				}
			}
		}
		return null;
	}
}
