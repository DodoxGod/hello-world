package dev.forja.difficulty;

import dev.forja.Forja;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Sizes up every hostile mob once, the first time it enters the world: the difficulty, the gear of the
 * nearest player, how far from spawn and how deep it is, the nights survived and how the player has been
 * doing decide whether it comes as a veteran or an elite, and how much tougher it is. Done once and marked
 * with a tag, so a mob keeps what it came with across reloads.
 *
 * <pre>
 * p(veterano) = min(máxV, baseV · k),  p(élite) = min(máxE, baseE · k)
 * k = dificultad.amenaza · distancia · profundidad · noches · (1 + equipo) · adaptativa
 * distancia = 1 + min(2, distancia al spawn / 1500);  profundidad = 1,3 bajo y = 0, 1,5 en el Nether
 * vida × dificultad.vida · amenaza.vida · (1 + 0,2 · tramo);  armadura + amenaza.armadura + 1,5 · tramo
 * </pre>
 */
public final class Scaling {
	public static final String SIZED = "forja_nivelado";
	/** Put on a mob by its natural spawn (see MobSpawnMixin) and taken off when it is sized up. */
	public static final String NATURAL = "forja_natural";
	/** The mob a pack formed round. Other natural spawns close by come without a pack of their own. */
	public static final String LEADER = "forja_lider_grupo";
	/** A companion: always an ordinary one, never a veteran, an elite or a champion of its own. */
	public static final String COMPANION = "forja_acompanante";
	private static final Identifier MODIFIER = Forja.id("dificultad");
	/** How far away the player whose gear counts may be. */
	private static final double GEAR_RANGE = 128.0;

	private Scaling() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && entity instanceof Enemy && !mob.entityTags().contains(SIZED) && CombatConfig.get().enabled) {
				sizeUp(mob, level);
			}
		});
	}

	static void sizeUp(Mob mob, ServerLevel level) {
		mob.addTag(SIZED);
		boolean natural = mob.removeTag(NATURAL);
		RandomSource random = mob.getRandom();
		ForjaDifficulty difficulty = ForjaDifficulty.current();
		Player near = level.getNearestPlayer(mob, GEAR_RANGE);
		// The gear of the nearest player counts only where the level scales by it (Ladder.gear: not in Fácil).
		boolean geared = Ladder.current().gear;
		double gear = geared ? GearScore.of(near) : 0.0;
		int tier = GearScore.tier(gear);

		Threat threat = Threat.of(mob);
		// A companion stays what it is: a pack with a second veteran or elite in it was twelve zombies and
		// two champions (Andy, 2026-09-29).
		if (threat == Threat.NORMAL && !Bosses.isBoss(mob) && !mob.entityTags().contains(COMPANION)) {
			threat = roll(mob, level, random, difficulty, gear, near);
			threat.mark(mob);
			Names.give(mob, threat);
		}

		// A plain vanilla monster on a level without Forja's rules (Fácil) keeps vanilla's health.
		double health = (Ladder.plainVanilla(mob) ? 1.0 : difficulty.health) * threat.health * (1.0 + CombatConfig.get().gearHealthPerTier * tier);
		raise(mob, Attributes.MAX_HEALTH, health - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		double armor = threat.armor + CombatConfig.get().gearArmorPerTier * tier;
		if (armor > 0.0) {
			raise(mob, Attributes.ARMOR, armor, AttributeModifier.Operation.ADD_VALUE);
		}
		mob.setHealth(mob.getMaxHealth());
		dev.forja.ai.MobDefense.arm(mob);
		dev.forja.ai.Personality.roll(mob, level);
		// What it carries (docs/red_mob_v4_diseno.md §4.8): natural spawns and their packs only, never a test or an egg.
		if (natural || mob.entityTags().contains(COMPANION)) {
			dev.forja.ai.MobKit.roll(mob, random, difficulty.threat);
		}
		// The hordes of a sky event come out fighting (idea 93).
		if (dev.forja.world.WorldEvents.active(level) != null) {
			for (dev.forja.ai.Personality.Trait trait : dev.forja.ai.Personality.Trait.values()) {
				mob.removeTag(dev.forja.ai.Personality.TRAIT_TAG + trait.name().toLowerCase(java.util.Locale.ROOT));
			}
			mob.addTag(dev.forja.ai.Personality.TRAIT_TAG + "agresivo");
		}

		if (natural) {
			pack(mob, level, threat, random);
		}
	}

	/**
	 * The companions a natural spawn brings: a veteran or an elite never comes alone (three to six in all),
	 * an ordinary one rarely (two or three, most of the time), and the nights survived add one more now and
	 * then, as they always did. Beside it, of its kind or another common one, and never past the crowd.
	 * Vanilla spawns its own groups of up to four at once; only the first of them leads a pack, or four packs
	 * would pile up on the same spot.
	 */
	static void pack(Mob mob, ServerLevel level, Threat threat, RandomSource random) {
		CombatConfig cfg = CombatConfig.get();
		if (!level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(cfg.packLeaderSpacing),
			other -> other != mob && other.isAlive() && other.entityTags().contains(LEADER)).isEmpty()) {
			return;
		}
		mob.addTag(LEADER);
		int group = 1;
		if (threat == Threat.VETERANO || threat == Threat.ELITE) {
			group = cfg.packVeteranMin + random.nextInt(Math.max(1, cfg.packVeteranMax - cfg.packVeteranMin + 1));
		} else if (random.nextDouble() < cfg.packChance) {
			group = cfg.packMin + random.nextInt(Math.max(1, cfg.packMax - cfg.packMin + 1));
		}
		if (level.isDarkOutside() && random.nextDouble() < Nights.companionChance(level)) {
			group++;
		}
		int room = room(mob, level, cfg);
		boolean creeper = mob.getType() == net.minecraft.world.entity.EntityTypes.CREEPER;
		java.util.List<Mob> members = new java.util.ArrayList<>();
		members.add(mob);
		for (int i = 1; i < group && i <= room; i++) {
			net.minecraft.world.entity.EntityType<?> kind = companionKind(mob, level, random, cfg, creeper);
			creeper |= kind == net.minecraft.world.entity.EntityTypes.CREEPER;
			Mob friend = companion(mob, level, kind);
			if (friend != null) {
				members.add(friend);
			}
		}
		// Who of the pack can go into a fury (v4, Andy's decision 3): a tenth, one at least in five or more.
		dev.forja.ai.Fury.choose(members, random);
	}

	/**
	 * The leader's kind, or another of the common night monsters with {@link CombatConfig#packMixChance}.
	 * A husk's pack draws on the desert's kinds and a stray's on the snow's; anything uncommon (a witch, an
	 * enderman, anything of the Nether's or the mod's own) brings only its own kind.
	 */
	static net.minecraft.world.entity.EntityType<?> companionKind(Mob leader, ServerLevel level, RandomSource random, CombatConfig cfg, boolean creeperTaken) {
		net.minecraft.world.entity.EntityType<?> own = leader.getType();
		net.minecraft.world.entity.EntityType<?> zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE;
		net.minecraft.world.entity.EntityType<?> skeleton = net.minecraft.world.entity.EntityTypes.SKELETON;
		if (own == net.minecraft.world.entity.EntityTypes.HUSK) {
			zombie = own;
		} else if (own == net.minecraft.world.entity.EntityTypes.STRAY) {
			skeleton = own;
		}
		boolean common = own == zombie || own == skeleton || own == net.minecraft.world.entity.EntityTypes.SPIDER
			|| own == net.minecraft.world.entity.EntityTypes.CREEPER;
		if (!common || level.dimension() != Level.OVERWORLD || random.nextDouble() >= cfg.packMixChance) {
			return own;
		}
		java.util.List<net.minecraft.world.entity.EntityType<?>> pool = new java.util.ArrayList<>(java.util.List.of(
			zombie, skeleton, net.minecraft.world.entity.EntityTypes.SPIDER));
		if (!creeperTaken) {
			pool.add(net.minecraft.world.entity.EntityTypes.CREEPER);
		}
		pool.remove(own);
		return pool.get(random.nextInt(pool.size()));
	}

	/**
	 * How many more hostiles may come here: no more than the crowd within 32 blocks leaves room for. The
	 * world's monster cap is not asked again: the spawner asked it before this one came, and vanilla's own
	 * packs are let out whole on that one question too.
	 */
	private static int room(Mob mob, ServerLevel level, CombatConfig cfg) {
		int near = level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(32.0), other -> other instanceof Enemy && other.isAlive()).size();
		return Math.max(0, cfg.packCrowd - near);
	}

	/** Veteran, elite or nothing. The champion is decided elsewhere (Elites), with its own rules. */
	private static Threat roll(Mob mob, ServerLevel level, RandomSource random, ForjaDifficulty difficulty, double gear, Player near) {
		CombatConfig cfg = CombatConfig.get();
		double distance = Math.sqrt(mob.blockPosition().distSqr(level.getRespawnData().pos()));
		double k = difficulty.threat
			* (1.0 + Math.min(2.0, distance / 1500.0))
			* (level.dimension() == Level.NETHER ? 1.5 : mob.getY() < 0.0 ? 1.3 : 1.0)
			* Nights.threatMultiplier(level)
			* (1.0 + gear)
			* Adaptive.threatMultiplier(near)
			* (dev.forja.world.WorldEvents.active(level) != null ? 1.5 : 1.0);
		double roll = random.nextDouble();
		double elite = Math.min(cfg.eliteChanceMax, cfg.eliteChance * k);
		if (roll < elite) {
			return Threat.ELITE;
		}
		if (roll < elite + Math.min(cfg.veteranChanceMax, cfg.veteranChance * k)) {
			return Threat.VETERANO;
		}
		return Threat.NORMAL;
	}

	/** One more, beside it. Not natural itself, so it never brings one of its own, and never more than ordinary. */
	private static Mob companion(Mob mob, ServerLevel level, net.minecraft.world.entity.EntityType<?> kind) {
		var other = kind.create(level, EntitySpawnReason.EVENT);
		if (!(other instanceof Mob friend)) {
			return null;
		}
		friend.addTag(COMPANION);
		// A few tries round it, one to three blocks off, on something to stand on and with room to stand.
		boolean placed = false;
		for (int attempt = 0; attempt < 6 && !placed; attempt++) {
			double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
			double reach = 1.0 + mob.getRandom().nextDouble() * 2.0;
			friend.snapTo(mob.getX() + Math.cos(angle) * reach, mob.getY(), mob.getZ() + Math.sin(angle) * reach, mob.getYRot(), 0.0F);
			net.minecraft.core.BlockPos below = friend.blockPosition().below();
			placed = level.noCollision(friend) && level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP);
		}
		if (!placed) {
			return null;
		}
		friend.finalizeSpawn(level, level.getCurrentDifficultyAt(friend.blockPosition()), EntitySpawnReason.EVENT, null);
		level.addFreshEntity(friend);
		return friend;
	}

	private static void raise(LivingEntity mob, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && !instance.hasModifier(MODIFIER) && Math.abs(amount) > 1.0E-6) {
			instance.addPermanentModifier(new AttributeModifier(MODIFIER, amount, operation));
		}
	}
}
