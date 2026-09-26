package dev.forja.forge;

import dev.forja.ForjaAdvancements;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.HiddenEnchantments;
import dev.forja.upgrade.Upgrades;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Maestría: forged gear gains experience from use and levels up to {@link #MAX_LEVEL}. Each level adds
 * to the item's stats (see {@link ForgeStats#sheet}); the level is derived from the experience stored in
 * the forja:maestria component, and the item's components are rebuilt when it levels up.
 */
public final class Mastery {
	public static final int MAX_LEVEL = 10;

	private Mastery() {
	}

	/** Total experience needed to reach a level: 50, 150, 300, 500 ... 2750. */
	public static int experienceFor(int level) {
		return 25 * level * (level + 1);
	}

	public static int levelFor(int experience) {
		int level = 0;
		while (level < MAX_LEVEL && experience >= experienceFor(level + 1)) {
			level++;
		}
		return level;
	}

	public static int experience(ItemStack stack) {
		return stack.getOrDefault(ModComponents.MAESTRIA, 0);
	}

	public static int level(ItemStack stack) {
		return levelFor(experience(stack));
	}

	/** Adds experience to forged gear held or worn by the owner; on a level up the gear's stats are rebuilt. */
	/** The share the echo talisman adds to whatever the piece was about to learn. */
	private static int withTalisman(net.minecraft.world.entity.LivingEntity holder, int amount) {
		if (holder instanceof net.minecraft.world.entity.player.Player player
			&& dev.forja.item.Talisman.carried(player, dev.forja.item.Talisman.ECO)) {
			return Math.max(amount + 1, Math.round(amount * (1.0F + dev.forja.item.Talisman.MASTERY_BONUS)));
		}
		return amount;
	}

	public static void addExperience(LivingEntity owner, ItemStack stack, int amount) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null || amount <= 0 || stack.isEmpty() || stack.isBroken()) {
			return;
		}
		amount = withTalisman(owner, amount);
		Oxidation.use(owner, stack);
		int before = level(stack);
		if (before >= MAX_LEVEL) {
			return;
		}
		int experience = Math.min(experienceFor(MAX_LEVEL), experience(stack) + amount);
		stack.set(ModComponents.MAESTRIA, experience);
		int after = levelFor(experience);
		if (after <= before || !(owner.level() instanceof ServerLevel level)) {
			return;
		}

		int damage = stack.getDamageValue();
		Upgrades upgrades = stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		Assembler.write(parts.type(), parts.materials(), upgrades, after, Perk.of(stack), BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM, Assembler.sink(stack));
		HiddenEnchantments.write(stack, level.registryAccess());
		stack.setDamageValue(Math.min(damage, Math.max(0, stack.getMaxDamage() - 1)));

		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
		// In the colour of the thing that levelled, not the green of a villager trading. A piece
		// getting better is the mod's own idea and it should not borrow somebody else's flourish.
		level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(parts.primary().color, 1.0F),
			owner.getX(), owner.getY(1.0) + 0.2, owner.getZ(), 14, 0.4, 0.35, 0.4, 0.0);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
			owner.getX(), owner.getY(1.0), owner.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
		if (after >= MAX_LEVEL) {
			// Ten is the end of the road for a piece, and it only happens once in its life.
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
				SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9F, 1.5F);
			// In the colour of what the piece is made of, which is what it is the mastery of.
			dev.forja.entity.Shockwave.burst(level, owner.position(), 3.0, 12, parts.primary().color & 0xFFFFFF, 0.5F);
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
				owner.getX(), owner.getY(1.1), owner.getZ(), 30, 0.45, 0.5, 0.45, 0.4);
		}
		if (owner instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("gui.forja.maestria.sube", stack.getHoverName(), after));
			if (after >= MAX_LEVEL) {
				ForjaAdvancements.award(player, "maestria");
			}
		}
	}

	/** Sets gear straight to a level, for the test command. */
	public static void setLevel(ItemStack stack, int level, net.minecraft.core.HolderLookup.Provider registries) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null) {
			return;
		}
		int clamped = Math.max(0, Math.min(MAX_LEVEL, level));
		int damage = stack.getDamageValue();
		boolean broken = stack.isBroken();
		stack.set(ModComponents.MAESTRIA, experienceFor(clamped));
		Upgrades upgrades = stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		Assembler.write(parts.type(), parts.materials(), upgrades, clamped, Perk.of(stack), BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM, Assembler.sink(stack));
		HiddenEnchantments.write(stack, registries);
		stack.setDamageValue(broken ? stack.getMaxDamage() : Math.min(damage, Math.max(0, stack.getMaxDamage() - 1)));
	}

	/** Tooltip line with the level and the progress toward the next one. */
	public static Component describe(ItemStack stack) {
		int experience = experience(stack);
		int level = levelFor(experience);
		if (level >= MAX_LEVEL) {
			return Component.translatable("tooltip.forja.maestria.max", level);
		}
		return Component.translatable("tooltip.forja.maestria", level, experience, experienceFor(level + 1));
	}
}
