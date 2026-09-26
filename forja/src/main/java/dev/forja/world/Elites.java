package dev.forja.world;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.Mastery;
import dev.forja.registry.ModComponents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Elites: about one monster in a hundred comes up wearing a legend. It is named, it glows through
 * walls, it is far harder to put down than anything around it, and it only ever gives up one piece of
 * what it carries, so killing it is worth doing and never worth farming.
 */
public final class Elites {
	/** How often a monster that could carry forged gear turns out to be an elite. */
	public static final float CHANCE = 0.01F;

	/** What being an elite multiplies its health by. */
	public static final float HEALTH = 5.0F;

	/** Embate de leyenda: the run-up it opens with when you try to keep your distance. */
	public static final double CHARGE_MIN = 4.0;
	public static final double CHARGE_MAX = 11.0;
	public static final int CHARGE_COOLDOWN = 180;
	public static final float CHARGE_DAMAGE = 6.0F;
	public static final double CHARGE_REACH = 2.6;

	/** Segundo aliento: once per elite, at a quarter of its health. */
	public static final float WIND_SHARE = 0.25F;
	public static final float WIND_MEND = 0.3F;
	public static final int WIND_TICKS = 160;

	private Elites() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && isElite(entity)) {
				drop(level, entity);
			}
		});
	}

	/** Days before elites start turning up, and how far from spawn they stay. */
	public static final int FIRST_DAY = 5;

	public static final int SPAWN_DISTANCE = 200;

	/** Whether an elite may appear here and now: not in the first days, not next to spawn. */
	public static boolean canAppear(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		if (level.getOverworldClockTime() < FIRST_DAY * 24000L) {
			return false;
		}
		net.minecraft.core.BlockPos spawn = level.getRespawnData().pos();
		return mob.blockPosition().distSqr(spawn) > (double) SPAWN_DISTANCE * SPAWN_DISTANCE;
	}

	public static boolean isElite(LivingEntity entity) {
		return entity.entityTags().contains("forja_elite");
	}

	/**
	 * Turns a freshly spawned monster into an elite: a legend in its hands, a name of its own, and
	 * enough health and reach that it reads as the thing that killed you.
	 */
	public static void makeElite(Mob mob, RandomSource random) {
		mob.addTag("forja_elite");
		ItemStack weapon = Legends.createWeapon(random, mob.registryAccess());
		Mastery.setLevel(weapon, Mastery.MAX_LEVEL, mob.registryAccess());
		mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		// Nothing it wears falls off on its own; the one piece it gives up is handled on death.
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0F);
		}
		mob.setCustomName(Component.translatable("entity.forja.elite", weapon.getHoverName(), mob.getType().getDescription()));
		mob.setCustomNameVisible(true);
		mob.setGlowingTag(true);
		mob.setPersistenceRequired();
		raise(mob, Attributes.MAX_HEALTH, HEALTH - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		raise(mob, Attributes.ATTACK_DAMAGE, 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		raise(mob, Attributes.ARMOR, 8.0, AttributeModifier.Operation.ADD_VALUE);
		raise(mob, Attributes.KNOCKBACK_RESISTANCE, 0.6, AttributeModifier.Operation.ADD_VALUE);
		raise(mob, Attributes.MOVEMENT_SPEED, 0.15F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		mob.setHealth(mob.getMaxHealth());
		// Two moves of its own. They are goals rather than a class of ours because an elite is an
		// ordinary vanilla monster underneath, and a goal is the only way to hand one a move.
		var goals = ((dev.forja.mixin.MobGoalsAccess) mob).forjaGoals();
		goals.addGoal(2, new dev.forja.entity.ai.LeapStrikeGoal(
			mob, CHARGE_MIN, CHARGE_MAX, CHARGE_COOLDOWN, CHARGE_DAMAGE, CHARGE_REACH,
			ParticleTypes.SOUL_FIRE_FLAME, SoundEvents.RAVAGER_ROAR));
		goals.addGoal(0, new dev.forja.entity.ai.SecondWindGoal(mob, WIND_SHARE, WIND_MEND, WIND_TICKS));
		if (mob.level() instanceof ServerLevel level) {
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 0.6F);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY(1.0), mob.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
		}
	}

	private static void raise(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.addPermanentModifier(new AttributeModifier(dev.forja.Forja.id("elite"), amount, operation));
		}
	}

	/** One piece, chosen at random out of everything it was carrying. */
	private static void drop(ServerLevel level, LivingEntity entity) {
		List<ItemStack> carried = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = entity.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.has(ModComponents.PARTS)) {
				carried.add(stack.copy());
			}
		}
		if (carried.isEmpty()) {
			return;
		}
		ItemStack prize = carried.get(level.getRandom().nextInt(carried.size()));
		level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY(0.5), entity.getZ(), prize));
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 1.0F, 1.2F);
		level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY(1.0), entity.getZ(), 40, 0.4, 0.6, 0.4, 0.1);
	}
}
