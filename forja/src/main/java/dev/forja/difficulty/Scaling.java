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
		double gear = GearScore.of(near);
		int tier = GearScore.tier(gear);

		Threat threat = Threat.of(mob);
		if (threat == Threat.NORMAL && !Bosses.isBoss(mob)) {
			threat = roll(mob, level, random, difficulty, gear, near);
			threat.mark(mob);
			if (threat != Threat.NORMAL) {
				mob.setCustomName(Component.translatable("entity.forja.amenaza." + threat.name().toLowerCase(java.util.Locale.ROOT),
					mob.getType().getDescription()));
			}
		}

		double health = difficulty.health * threat.health * (1.0 + CombatConfig.get().gearHealthPerTier * tier);
		raise(mob, Attributes.MAX_HEALTH, health - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		double armor = threat.armor + CombatConfig.get().gearArmorPerTier * tier;
		if (armor > 0.0) {
			raise(mob, Attributes.ARMOR, armor, AttributeModifier.Operation.ADD_VALUE);
		}
		mob.setHealth(mob.getMaxHealth());
		dev.forja.ai.MobDefense.arm(mob);
		dev.forja.ai.Personality.roll(mob, level);
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
	 * then, as they always did. Same kind, beside it, and never past the crowd.
	 */
	static void pack(Mob mob, ServerLevel level, Threat threat, RandomSource random) {
		CombatConfig cfg = CombatConfig.get();
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
		for (int i = 1; i < group && i <= room; i++) {
			companion(mob, level);
		}
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

	/** One more of the same kind, beside it. Not natural itself, so it never brings one of its own. */
	private static void companion(Mob mob, ServerLevel level) {
		var other = mob.getType().create(level, EntitySpawnReason.EVENT);
		if (!(other instanceof Mob friend)) {
			return;
		}
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
			return;
		}
		friend.finalizeSpawn(level, level.getCurrentDifficultyAt(friend.blockPosition()), EntitySpawnReason.EVENT, null);
		level.addFreshEntity(friend);
	}

	private static void raise(LivingEntity mob, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && !instance.hasModifier(MODIFIER) && Math.abs(amount) > 1.0E-6) {
			instance.addPermanentModifier(new AttributeModifier(MODIFIER, amount, operation));
		}
	}
}
