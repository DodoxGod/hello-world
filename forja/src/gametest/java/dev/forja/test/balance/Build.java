package dev.forja.test.balance;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.forja.combat.ChargedStrike;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.magic.Spellcasting;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.HiddenEnchantments;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * One forged weapon, made for real: the stack comes out of Assembler with its upgrades written the way
 * the forge writes them (Assembler.rewrite, HiddenEnchantments.write), and every number the fight
 * simulation needs is read back off that stack rather than worked out again.
 */
public final class Build {
	public final ForgeType type;
	public final List<ForgeMaterial> materials;
	public final Map<Upgrade, Integer> upgrades;
	public final ItemStack stack;
	/** The player's attack damage with this in hand (base 1 plus the stack's modifiers). */
	public final double attackDamage;
	/** Swings a second (base 4 plus the stack's modifiers). */
	public final double attackSpeed;
	public final boolean charges;
	public final int fireAspect;
	public final int breach;
	public final int durability;
	/** Staff and tome: the spell's base damage, its wait in ticks and how long a rune lasts. */
	public final double spellDamage;
	public final int spellCooldown;
	public final int runeTicks;

	private final Map<String, Double> enchantBonus = new java.util.concurrent.ConcurrentHashMap<>();

	public Build(ForgeType type, List<ForgeMaterial> materials, Map<Upgrade, Integer> upgrades, HolderLookup.Provider registries) {
		this.type = type;
		this.materials = List.copyOf(materials);
		EnumMap<Upgrade, Integer> sorted = new EnumMap<>(Upgrade.class);
		sorted.putAll(upgrades);
		this.upgrades = sorted;
		ItemStack made = Assembler.create(type, materials, registries);
		if (!upgrades.isEmpty()) {
			made.set(ModComponents.UPGRADES, new Upgrades(sorted));
			Assembler.rewrite(made, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
			HiddenEnchantments.write(made, registries);
		}
		this.stack = made;
		double[] damage = {1.0};
		double[] speed = {4.0};
		made.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
				damage[0] += modifier.amount();
			} else if (attribute.equals(Attributes.ATTACK_SPEED)) {
				speed[0] += modifier.amount();
			}
		});
		this.attackDamage = damage[0];
		this.attackSpeed = speed[0];
		this.charges = ChargedStrike.charges(made);
		this.fireAspect = level(made, registries, Enchantments.FIRE_ASPECT);
		this.breach = level(made, registries, Enchantments.BREACH);
		this.durability = made.getMaxDamage();
		if (Spellcasting.casts(type)) {
			ForgeMaterial core = Spellcasting.core(made.get(ModComponents.PARTS));
			this.spellDamage = type == ForgeType.BACULO ? Spellcasting.boltDamage(core) : Spellcasting.areaDamage(core);
			this.spellCooldown = Spellcasting.cooldown(made, type);
			this.runeTicks = Spellcasting.runeTicks(made);
		} else {
			this.spellDamage = 0.0;
			this.spellCooldown = 0;
			this.runeTicks = 0;
		}
	}

	private static int level(ItemStack stack, HolderLookup.Provider registries, ResourceKey<Enchantment> key) {
		return registries.lookupOrThrow(Registries.ENCHANTMENT).get(key)
			.map(holder -> stack.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).getLevel(holder))
			.orElse(0);
	}

	public ForgedParts parts() {
		return this.stack.get(ModComponents.PARTS);
	}

	public boolean hasTrait(ForgeMaterial.Trait trait) {
		return this.parts().hasTrait(trait);
	}

	/** The upgrade's own fraction, 0..1, before any frenzy. */
	public float fraction(Upgrade upgrade) {
		return Upgrades.fraction(this.stack, upgrade);
	}

	public boolean synergy(dev.forja.upgrade.Synergy synergy) {
		return synergy.active(this.stack);
	}

	/**
	 * What the stack's enchantments add to one blow on this target (Filo, Castigo, Perdición, Empalamiento...),
	 * straight from EnchantmentHelper.modifyDamage on the live entity, the way Player.attack and spells ask.
	 */
	public double enchantBonus(Target target, ServerLevel level, DamageSource source, double base) {
		Double flat = this.flatBonus.get(target.id);
		if (flat != null) {
			return flat;
		}
		String key = target.id + "|" + base;
		Double cached = this.enchantBonus.get(key);
		if (cached != null) {
			return cached;
		}
		double bonus = target.entity == null ? 0.0
			: EnchantmentHelper.modifyDamage(level, this.stack, target.entity, source, (float) base) - base;
		this.enchantBonus.put(key, bonus);
		return bonus;
	}

	/** Target id to the enchantment bonus when it is the same whatever the blow (Filo, Castigo... all add a flat amount). */
	private final Map<String, Double> flatBonus = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Asks the enchantments about every target now, on the server thread, so that the fights can then run
	 * on other threads without touching the world. Every weapon enchantment the mod writes adds a flat
	 * amount, which is checked here (two different blows, the same bonus); a build where that were not so
	 * returns false and has to be fought on the server thread.
	 */
	public boolean prepare(List<Target> targets, ServerLevel level, DamageSource source) {
		boolean flat = true;
		for (Target target : targets) {
			if (this.flatBonus.containsKey(target.id)) {
				continue;
			}
			double small = target.entity == null ? 0.0 : EnchantmentHelper.modifyDamage(level, this.stack, target.entity, source, 1.0F) - 1.0;
			double big = target.entity == null ? 0.0 : EnchantmentHelper.modifyDamage(level, this.stack, target.entity, source, 10.0F) - 10.0;
			if (Math.abs(small - big) < 1.0E-5) {
				this.flatBonus.put(target.id, small);
			} else {
				flat = false;
			}
		}
		return flat;
	}

	/** Short name: "Espada de netherita / mango de latón", materials only where they differ from the head. */
	public String materialName() {
		List<String> names = new ArrayList<>();
		ForgeMaterial previous = null;
		for (ForgeMaterial material : this.materials) {
			if (material != previous) {
				names.add(material.getSerializedName());
			}
			previous = material;
		}
		return String.join(" / ", names);
	}

	public String upgradeName() {
		if (this.upgrades.isEmpty()) {
			return "—";
		}
		List<String> names = new ArrayList<>();
		this.upgrades.forEach((upgrade, percent) -> names.add(upgrade.id() + (percent < 100 ? " " + percent + "%" : "")));
		return String.join(", ", names);
	}

	@Override
	public String toString() {
		return String.format(Locale.ROOT, "%s[%s]{%s}", this.type.id(), this.materialName(), this.upgradeName());
	}
}
