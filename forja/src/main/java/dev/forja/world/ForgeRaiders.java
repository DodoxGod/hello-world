package dev.forja.world;

import dev.forja.forge.ForgeType;
import dev.forja.forge.Mastery;
import dev.forja.item.UpgradeOrbItem;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Upgrade;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypeIds;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Saqueadores de forja: a band that wants what you have made. They turn up at night, out in the open,
 * carrying forged gear of their own, and the one in front carries a legend. Kill the captain and the
 * band loses its nerve, and what he was carrying stays on the ground: his legend and the orbs the band
 * had melted down out of other people's work.
 */
public final class ForgeRaiders {
	/** How often a band turns up: checked once a minute per player, at night. */
	public static final float CHANCE = 0.06F;

	/** How many follow the captain. */
	public static final int BAND = 4;

	/** Cerrar filas: how far the horn carries, how often he winds it and how long the band keeps it. */
	public static final double RALLY_REACH = 16.0;
	public static final int RALLY_COOLDOWN = 260;
	public static final int RALLY_TICKS = 160;

	/** Carga del capitan: the jump he closes the gap with. */
	public static final double CHARGE_MIN = 5.0;
	public static final double CHARGE_MAX = 12.0;
	public static final int CHARGE_COOLDOWN = 200;
	public static final float CHARGE_DAMAGE = 5.0F;
	public static final double CHARGE_REACH = 2.5;

	/** How far from the player they arrive. */
	private static final int DISTANCE = 40;

	private ForgeRaiders() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 1200 != 0 || !level.dimensionType().hasSkyLight() || level.isBrightOutside()) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				// They only bother the players who have something worth taking.
				if (carriesForged(player) && level.getRandom().nextFloat() < dev.forja.ForjaConfig.get().saqueadores) {
					spawnBand(level, player);
				}
			}
		});

		// The camp is built with plain raiders in it, because a structure cannot carry their gear. The
		// first time one of them loads in, it is given the same forged iron a roaming band carries, and
		// the tag is dropped so this only ever happens once.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof Mob mob) || !mob.entityTags().contains("forja_campamento")) {
				return;
			}
			mob.removeTag("forja_campamento");
			mob.setPersistenceRequired();
			armCampRaider(mob, level);
			if (mob.entityTags().contains("forja_campamento_capitan")) {
				equipCaptain(mob, level);
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && entity.entityTags().contains("forja_capitan")) {
				captainFell(level, entity);
			}
		});
	}

	private static boolean carriesForged(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).has(ModComponents.PARTS)) {
				return true;
			}
		}
		return false;
	}

	/** The band arrives: a captain with a legend and four raiders in forged iron. */
	public static void spawnBand(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		BlockPos around = player.blockPosition().offset(
			(int) Math.round(Math.cos(angle) * DISTANCE), 0, (int) Math.round(Math.sin(angle) * DISTANCE)
		);
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around);
		if (!level.isLoaded(ground) || level.getBlockState(ground.below()).isAir()) {
			return;
		}
		Mob captain = raider(level, EntityTypeIds.VINDICATOR);
		if (captain == null) {
			return;
		}
		captain.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		equipCaptain(captain, level);
		ForjaMobs.forgeEquipment(captain, random, 1.0F);
		level.addFreshEntity(captain);

		for (int i = 0; i < BAND; i++) {
			Mob raider = raider(level, i % 2 == 0 ? EntityTypeIds.PILLAGER : EntityTypeIds.VINDICATOR);
			if (raider == null) {
				continue;
			}
			raider.snapTo(ground.getX() + 0.5 + random.nextInt(5) - 2, ground.getY(), ground.getZ() + 0.5 + random.nextInt(5) - 2, 0.0F, 0.0F);
			raider.setPersistenceRequired();
			ForjaMobs.forgeEquipment(raider, random, 1.0F);
			level.addFreshEntity(raider);
		}
		player.sendSystemMessage(Component.translatable("gui.forja.saqueadores").withColor(0xFF9A5A));
		level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 1.1F);
	}

	/**
	 * The camp is written into a structure, and a structure cannot carry gear: these arrive with empty
	 * hands, so they are dressed here. Forged iron on the head, often on the chest, and iron in the hands
	 * of the ones who fight up close; the crossbowmen keep their crossbow or they cannot fight at all.
	 */
	private static void armCampRaider(Mob mob, ServerLevel level) {
		RandomSource random = level.getRandom();
		mob.setItemSlot(EquipmentSlot.HEAD, forgedIron(ForgeType.CASCO, mob, random));
		if (random.nextFloat() < 0.6F) {
			mob.setItemSlot(EquipmentSlot.CHEST, forgedIron(ForgeType.PECHERA, mob, random));
		}
		if (mob instanceof net.minecraft.world.entity.monster.illager.Vindicator) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, forgedIron(ForgeType.HACHA, mob, random));
		} else if (mob.getMainHandItem().isEmpty()) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.CROSSBOW));
		}
	}

	/** One of ours in plain iron, with whatever the type asks for in the other slots. */
	private static ItemStack forgedIron(ForgeType type, Mob mob, RandomSource random) {
		java.util.List<dev.forja.material.ForgeMaterial> materials =
			new java.util.ArrayList<>(dev.forja.forge.Assembler.defaultMaterials(type));
		materials.set(0, dev.forja.material.ForgeMaterial.HIERRO);
		return dev.forja.forge.Assembler.create(type, materials, mob.registryAccess());
	}

	/** Names the captain, hands him a legend at full maestria and makes sure only that drops. */
	private static void equipCaptain(Mob captain, ServerLevel level) {
		captain.addTag("forja_capitan");
		captain.setCustomName(Component.translatable("entity.forja.capitan_saqueador"));
		captain.setCustomNameVisible(true);
		captain.setPersistenceRequired();
		ItemStack legend = Legends.createWeapon(level.getRandom(), level.registryAccess());
		Mastery.setLevel(legend, Mastery.MAX_LEVEL, level.registryAccess());
		captain.setItemSlot(EquipmentSlot.MAINHAND, legend);
		// What makes him a captain rather than the biggest raider: he winds the horn, and he charges.
		var goals = ((dev.forja.mixin.MobGoalsAccess) captain).forjaGoals();
		goals.addGoal(1, new dev.forja.entity.ai.RallyGoal(captain, RALLY_REACH, RALLY_COOLDOWN, RALLY_TICKS));
		goals.addGoal(2, new dev.forja.entity.ai.LeapStrikeGoal(
			captain, CHARGE_MIN, CHARGE_MAX, CHARGE_COOLDOWN, CHARGE_DAMAGE, CHARGE_REACH,
			net.minecraft.core.particles.ParticleTypes.CRIT, SoundEvents.RAVAGER_ROAR));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			captain.setDropChance(slot, 0.0F);
		}
	}

	private static Mob raider(ServerLevel level, net.minecraft.resources.ResourceKey<EntityType<?>> type) {
		EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(type);
		return entityType.create(level, EntitySpawnReason.EVENT) instanceof Mob mob ? mob : null;
	}

	/** The captain goes down: his legend and the orbs the band was carrying stay where he fell. */
	private static void captainFell(ServerLevel level, LivingEntity captain) {
		ItemStack legend = captain.getMainHandItem().copy();
		if (!legend.isEmpty()) {
			level.addFreshEntity(new ItemEntity(level, captain.getX(), captain.getY(0.5), captain.getZ(), legend));
		}
		int orbs = 2 + level.getRandom().nextInt(3);
		for (int i = 0; i < orbs; i++) {
			Upgrade upgrade = Upgrade.values()[level.getRandom().nextInt(Upgrade.values().length)];
			int percent = 25 + level.getRandom().nextInt(4) * 25;
			level.addFreshEntity(new ItemEntity(
				level, captain.getX(), captain.getY(0.5), captain.getZ(), UpgradeOrbItem.create(upgrade, Math.min(100, percent))
			));
		}
		level.playSound(null, captain.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 0.7F);
		level.sendParticles(ParticleTypes.ENCHANT, captain.getX(), captain.getY(1.0), captain.getZ(), 40, 0.5, 0.5, 0.5, 0.2);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(captain) < 48.0 * 48.0) {
				dev.forja.ForjaAdvancements.award(player, "saqueadores");
			}
		}
	}
}
