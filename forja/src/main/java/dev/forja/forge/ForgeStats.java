package dev.forja.forge;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Every number a forged item or part gets from its materials, in one place. The assembler writes
 * these into components, and screens and tooltips show them colored by how they rank against every
 * other material choice for the same item or part: dark red for the worst, white around the average,
 * then green, blue and purple for the best.
 */
public final class ForgeStats {
	private static final int[] SCALE_COLORS = {0xAA0000, 0xFF5555, 0xFFFFFF, 0x55FF55, 0x55AAFF, 0xB45AFF};
	private static final double[] SCALE_STOPS = {-1.0, -0.5, 0.0, 1.0 / 3.0, 2.0 / 3.0, 1.0};

	public enum Format {
		PLAIN, MULTIPLIER, SECONDS, PLUS, SIGNED, PERCENT
	}

	public enum Stat {
		DURABILIDAD(true, Format.PLAIN),
		DANO(true, Format.PLAIN),
		VELOCIDAD(true, Format.PLAIN),
		MINADO(true, Format.PLAIN),
		CARGA(true, Format.MULTIPLIER),
		PREPARACION(false, Format.SECONDS),
		CAIDA(true, Format.MULTIPLIER),
		ARMADURA(true, Format.PLAIN),
		DUREZA(true, Format.PLAIN),
		EMPUJE(true, Format.PERCENT),
		TENSADO(true, Format.MULTIPLIER),
		FLECHA(true, Format.PLAIN),
		BLOQUEO(true, Format.SECONDS),
		HACHA(false, Format.MULTIPLIER),
		// What a single part adds to the item it goes into.
		PLANEO(true, Format.MULTIPLIER),
		VUELO(true, Format.SECONDS),
		DURABILIDAD_MULT(true, Format.MULTIPLIER),
		DURABILIDAD_MAS(true, Format.PLUS),
		DANO_MAS(true, Format.PLUS),
		VELOCIDAD_MAS(true, Format.SIGNED),
		MINADO_MULT(true, Format.MULTIPLIER),
		FLECHA_MAS(true, Format.PLUS),
		DUREZA_MAS(true, Format.PLUS);

		public final boolean higherIsBetter;
		public final Format format;

		Stat(boolean higherIsBetter, Format format) {
			this.higherIsBetter = higherIsBetter;
			this.format = format;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	public record Range(double min, double mean, double max) {
		static final Range FLAT = new Range(0, 0, 0);
	}

	public record Line(Stat stat, double value) {
		public Component text() {
			return Component.translatable("gui.forja.stat." + this.stat.id(), format(this.stat.format, this.value));
		}
	}

	private static final Map<ForgeType, Map<Stat, Range>> TYPE_RANGES = new EnumMap<>(ForgeType.class);
	private static final Map<PartType, Map<Stat, Range>> PART_RANGES = new EnumMap<>(PartType.class);

	private ForgeStats() {
	}

	// ------------------------------------------------------------------ material formulas shared by parts and items

	public static float spearChargeMultiplier(ForgeMaterial tip) {
		return 0.7F + 0.125F * tip.attackDamageBonus;
	}

	/** Seconds the spear is held before a charge counts; heavier tips settle faster. */
	public static float spearChargeDelay(ForgeMaterial tip) {
		return Math.max(0.2F, 0.75F - 0.0875F * tip.attackDamageBonus);
	}

	/** Seconds of one jab; better tips are heavier and a little slower, like vanilla spears. */
	public static float spearAttackDuration(ForgeMaterial tip) {
		return 0.65F + 0.125F * tip.attackDamageBonus;
	}

	public static float maceSmashMultiplier(ForgeMaterial head) {
		return 0.8F + 0.1F * head.attackDamageBonus;
	}

	/** Wooden limbs draw like the vanilla bow; better and lighter materials draw faster. */
	public static float bowDrawSpeed(ForgeMaterial limbs) {
		return 0.93F + limbs.miningSpeed * 0.035F;
	}

	public static float arrowDamageBonus(ForgeMaterial limbs) {
		return limbs.attackDamageBonus * 0.25F;
	}

	/**
	 * How long the window for a perfect parry stays open on a shield. Forged shields raise instantly, so
	 * the plate no longer buys you a slower block: a light plate buys you a wider moment to catch the blow.
	 */
	public static float shieldBlockDelay(ForgeMaterial plate) {
		return Math.max(0.15F, 0.45F - plate.armorDurability / 40.0F * 0.2F);
	}

	public static float shieldAxeDisable(ForgeMaterial rim) {
		return Math.max(0.3F, 1.2F - rim.toughness * 0.15F - rim.knockbackResistance);
	}

	// ------------------------------------------------------------------ item sheet

	/** Every stat of an assembled item, straight off the stack: parts, upgrades, Maestria, gift and press. */
	public static Sheet sheet(ItemStack stack, dev.forja.part.ForgedParts parts) {
		Sheet sheet = sheet(
			parts.type(), parts.materials(), stack.getOrDefault(dev.forja.registry.ModComponents.UPGRADES, dev.forja.upgrade.Upgrades.EMPTY),
			Mastery.level(stack), Perk.of(stack)
		);
		sheet.scale(Quality.bonus(stack) + mixBonus(parts));
		if (Oxidation.full(stack)) {
			// Copper that has gone all the way green holds together better than new copper.
			sheet.durability = Math.round(sheet.durability * (1.0F + Oxidation.PATINA_DURABILITY));
		}
		return sheet;
	}

	/**
	 * Mestizaje: a piece built out of materials with different traits is worth more than the sum of them.
	 * Two traits in one item is 5% on every number, three is 10%, and nobody gets there by accident.
	 */
	public static float mixBonus(dev.forja.part.ForgedParts parts) {
		java.util.Set<ForgeMaterial.Trait> traits = java.util.EnumSet.noneOf(ForgeMaterial.Trait.class);
		for (int slot = 0; slot < parts.type().slots.size(); slot++) {
			ForgeMaterial.Trait trait = parts.material(slot).trait;
			if (trait != ForgeMaterial.Trait.NONE) {
				traits.add(trait);
			}
		}
		return Math.max(0, traits.size() - 1) * 0.05F;
	}

	/** All stats of one material combination. Only the fields for the item's kind are filled. */
	public static final class Sheet {
		public final ForgeType type;
		public int durability;
		/** Attribute amounts: the player's base 1 damage and 4 attack speed are not included. */
		public float attackDamage;
		public float attackSpeed;
		/** Mining speed of each slot, 0 where the slot is not a mining head. */
		public final float[] miningSpeeds;
		public float chargeMultiplier;
		public float chargeDelay;
		public float attackDuration;
		public float smashMultiplier;
		public int armor;
		public float toughness;
		public float knockbackResistance;
		public float drawSpeed;
		public float arrowDamage;
		public float blockDelay;
		public float axeDisable;
		public float glide;
		public float flight;

		/** A perfect press lifts everything the piece does by the same small share. */
		/**
		 * Scales every stat by a share, up or down.
		 *
		 * <p>It only ever went up until rough castings existed: a piece poured through a strainer that
		 * did not hold is the opposite of a well timed press, so the same dial has to turn both ways.
		 * The factor is floored so that no amount of bad luck can take a piece to nothing.
		 */
		public void scale(float share) {
			if (share == 0.0F) {
				return;
			}
			share = Math.max(-0.75F, share);
			this.durability = Math.round(this.durability * (1.0F + share));
			this.attackDamage *= 1.0F + share;
			this.attackSpeed *= 1.0F + share;
			for (int slot = 0; slot < this.miningSpeeds.length; slot++) {
				this.miningSpeeds[slot] *= 1.0F + share;
			}
			this.chargeMultiplier *= 1.0F + share;
			this.smashMultiplier *= 1.0F + share;
			this.armor = Math.round(this.armor * (1.0F + share));
			this.toughness *= 1.0F + share;
			this.knockbackResistance *= 1.0F + share;
			this.drawSpeed *= 1.0F + share;
			this.arrowDamage *= 1.0F + share;
			this.blockDelay *= 1.0F + share;
			this.glide *= 1.0F + share;
			this.flight *= 1.0F + share;
		}

		Sheet(ForgeType type) {
			this.type = type;
			this.miningSpeeds = new float[type.slots.size()];
		}

		public float bestMiningSpeed() {
			float best = 0.0F;
			for (float speed : this.miningSpeeds) {
				best = Math.max(best, speed);
			}
			return best;
		}

		public List<Line> lines() {
			List<Line> lines = new ArrayList<>();
			lines.add(new Line(Stat.DURABILIDAD, this.durability));
			switch (this.type.kind) {
				case ARMOR -> {
					lines.add(new Line(Stat.ARMADURA, this.armor));
					lines.add(new Line(Stat.DUREZA, this.toughness));
					lines.add(new Line(Stat.EMPUJE, this.knockbackResistance));
				}
				case RANGED -> {
					// A crossbow's charge time is vanilla's (Quick Charge speeds it up); only bows draw by material.
					if (this.type == ForgeType.ARCO) {
						lines.add(new Line(Stat.TENSADO, this.drawSpeed));
					}
					lines.add(new Line(Stat.FLECHA, this.arrowDamage));
				}
				case MONTURA -> {
					lines.add(new Line(Stat.ARMADURA, this.armor));
					lines.add(new Line(Stat.DUREZA, this.toughness));
				}
				case MUNICION -> {
					lines.add(new Line(Stat.FLECHA, this.arrowDamage));
					lines.add(new Line(Stat.TENSADO, this.drawSpeed));
				}
				case ALAS -> {
					lines.add(new Line(Stat.PLANEO, this.glide));
					lines.add(new Line(Stat.VUELO, this.flight));
				}
				case SHIELD -> {
					lines.add(new Line(Stat.BLOQUEO, this.blockDelay));
					lines.add(new Line(Stat.HACHA, this.axeDisable));
				}
				default -> {
					lines.add(new Line(Stat.DANO, 1.0F + this.attackDamage));
					lines.add(new Line(Stat.VELOCIDAD, 4.0F + this.attackSpeed));
					if (this.type.kind == ForgeType.Kind.TOOL) {
						lines.add(new Line(Stat.MINADO, this.bestMiningSpeed()));
					}
					if (this.type == ForgeType.LANZA) {
						lines.add(new Line(Stat.CARGA, this.chargeMultiplier));
						lines.add(new Line(Stat.PREPARACION, this.chargeDelay));
					}
					if (this.type == ForgeType.MAZO) {
						lines.add(new Line(Stat.CAIDA, this.smashMultiplier));
					}
				}
			}
			return lines;
		}
	}

	public static Sheet sheet(ForgeType type, List<ForgeMaterial> materials, Upgrades upgrades) {
		return sheet(type, materials, upgrades, 0);
	}

	/** The stats of a material combination with its upgrades, Maestria level and engraved gift. */
	public static Sheet sheet(ForgeType type, List<ForgeMaterial> materials, Upgrades upgrades, int mastery) {
		return sheet(type, materials, upgrades, mastery, null);
	}

	public static Sheet sheet(ForgeType type, List<ForgeMaterial> materials, Upgrades upgrades, int mastery, @org.jspecify.annotations.Nullable Perk perk) {
		Sheet sheet = new Sheet(type);
		switch (type.kind) {
			case ARMOR -> {
				ArmorType armorType = type.armorType;
				ForgeMaterial plate = materials.get(type.slotOf(PartType.Role.PLATE));
				ForgeMaterial lining = materials.get(type.slotOf(PartType.Role.LINING));
				sheet.durability = Math.max(1, Math.round(armorType.getDurability(plate.armorDurability) * lining.handleDurability));
				sheet.armor = plate.defense(armorType);
				sheet.toughness = plate.toughness + lining.toughness * 0.25F;
				if (dev.forja.upgrade.Synergy.FORTALEZA.active(upgrades)) {
					sheet.armor += 1;
				}
				// Pacto de sombra: the shadow hides you, and eats part of the plate doing it.
				sheet.armor = Math.max(0, Math.round(sheet.armor * (1.0F - Upgrade.shadowArmor(upgrades.percent(Upgrade.PACTO_DE_SOMBRA) / 100.0F))));
				sheet.knockbackResistance = plate.knockbackResistance;
			}
			case RANGED -> {
				ForgeMaterial limbs = materials.get(type.slotOf(PartType.Role.HEAD));
				ForgeMaterial string = materials.get(type.slotOf(PartType.Role.EXTRA));
				ForgeMaterial grip = materials.get(type.slotOf(PartType.Role.HANDLE));
				float base = type == ForgeType.BALLESTA ? 400.0F : 330.0F;
				float durability = Math.max(96.0F, base + limbs.durability * 0.3F + string.durability * 0.05F) * grip.handleDurability;
				sheet.durability = Math.round(durability);
				sheet.drawSpeed = bowDrawSpeed(limbs) * (1.0F + Upgrade.drawSpeedBonus(upgrades.percent(Upgrade.TENSION) / 100.0F));
				sheet.arrowDamage = 2.0F + arrowDamageBonus(limbs);
			}
			case SHIELD -> {
				ForgeMaterial plate = materials.get(type.slotOf(PartType.Role.PLATE));
				ForgeMaterial rim = materials.get(type.slotOf(PartType.Role.EXTRA));
				ForgeMaterial handle = materials.get(type.slotOf(PartType.Role.HANDLE));
				sheet.durability = Math.max(1, Math.round((plate.armorDurability * 16.0F + rim.durability * 0.3F) * handle.handleDurability));
				float reflexes = Upgrade.blockDelayReduction(upgrades.percent(Upgrade.REFLEJOS) / 100.0F);
				sheet.blockDelay = shieldBlockDelay(plate) * (1.0F + reflexes);
				sheet.axeDisable = shieldAxeDisable(rim);
			}
			case MONTURA -> {
				ForgeMaterial plate = materials.get(type.slotOf(PartType.Role.PLATE));
				ForgeMaterial lining = materials.get(type.slotOf(PartType.Role.LINING));
				// A mount carries more plate than a person does, and the lining is what holds it on.
				sheet.armor = Math.round(plate.defense(ArmorType.CHESTPLATE) * (type == ForgeType.BARDA ? 1.4F : 1.0F));
				sheet.toughness = plate.toughness * 0.5F;
				sheet.durability = Math.max(100, Math.round(plate.armorDurability * 22.0F * lining.handleDurability));
			}
			case MUNICION -> {
				ForgeMaterial tip = materials.get(type.slotOf(PartType.Role.HEAD));
				ForgeMaterial fletching = materials.get(type.slotOf(PartType.Role.EXTRA));
				sheet.arrowDamage = arrowDamage(tip) * (1.0F + Upgrade.arrowDamageBonus(upgrades.percent(Upgrade.PUNTA_AFILADA) / 100.0F));
				// The lighter the fletching, the faster it leaves the string, same as a quick handle.
				sheet.drawSpeed = Math.max(0.6F, 1.0F + fletching.handleAttackSpeed * 0.8F)
					* (1.0F + Upgrade.arrowSpeedBonus(upgrades.percent(Upgrade.ASTA_LIGERA) / 100.0F));
				sheet.durability = 0;
			}
			case ALAS -> {
				ForgeMaterial membrane = materials.get(type.slotOf(PartType.Role.PLATE));
				ForgeMaterial harness = materials.get(type.slotOf(PartType.Role.LINING));
				sheet.durability = Math.max(80, Math.round((membrane.armorDurability * 14.0F + membrane.durability * 0.08F) * harness.handleDurability));
				sheet.glide = glideRatio(membrane);
				sheet.flight = flightSeconds(membrane) * harness.handleDurability;
			}
			case PESCA -> {
				ForgeMaterial rod = materials.get(type.slotOf(PartType.Role.HANDLE));
				ForgeMaterial line = materials.get(type.slotOf(PartType.Role.EXTRA));
				// Vanilla's rod lasts 64 casts; a sturdy shaft and a strong line last much longer.
				sheet.durability = Math.max(64, Math.round((64.0F + rod.durability * 0.25F + line.durability * 0.15F) * rod.handleDurability));
			}
			default -> toolOrWeapon(sheet, type, materials, upgrades);
		}
		if (mastery > 0) {
			applyMastery(sheet, mastery);
		}
		if (perk == Perk.BALUARTE) {
			sheet.armor += 1;
		} else if (perk == Perk.CAZADOR && type.kind == ForgeType.Kind.RANGED) {
			sheet.arrowDamage += 1.0F;
		}
		return sheet;
	}

	/** Every level: +3% durability, and +4% mining speed, +0.2 damage, +0.1 toughness, bow and shield handling. */
	private static void applyMastery(Sheet sheet, int level) {
		sheet.durability = Math.max(1, Math.round(sheet.durability * (1.0F + 0.03F * level)));
		switch (sheet.type.kind) {
			case TOOL -> {
				for (int i = 0; i < sheet.miningSpeeds.length; i++) {
					sheet.miningSpeeds[i] *= 1.0F + 0.04F * level;
				}
			}
			case WEAPON -> sheet.attackDamage += 0.2F * level;
			case ARMOR -> sheet.toughness += 0.1F * level;
			case RANGED -> {
				sheet.drawSpeed *= 1.0F + 0.03F * level;
				sheet.arrowDamage += 0.05F * level;
			}
			case SHIELD -> {
				sheet.blockDelay = sheet.blockDelay * (1.0F + 0.02F * level);
				sheet.axeDisable = Math.max(0.3F, sheet.axeDisable * (1.0F - 0.02F * level));
			}
			case ALAS -> {
				sheet.glide += 0.02F * level;
				sheet.flight *= 1.0F + 0.04F * level;
			}
		}
	}

	private static void toolOrWeapon(Sheet sheet, ForgeType type, List<ForgeMaterial> materials, Upgrades upgrades) {
		List<Integer> headSlots = type.slotsOf(PartType.Role.HEAD);
		ForgeMaterial handle = materials.get(type.slotOf(PartType.Role.HANDLE));
		ForgeMaterial head = materials.get(headSlots.getFirst());

		float headDurability = 0.0F;
		float damageBonus = 0.0F;
		for (int slot : headSlots) {
			headDurability += materials.get(slot).durability;
			damageBonus += materials.get(slot).attackDamageBonus;
		}
		headDurability /= headSlots.size();
		damageBonus /= headSlots.size();
		float extraDurability = 0.0F;
		for (int slot : type.slotsOf(PartType.Role.EXTRA)) {
			extraDurability += materials.get(slot).durability * 0.2F;
		}
		sheet.durability = Math.max(1, Math.round((headDurability + extraDurability) * handle.handleDurability * type.durabilityMultiplier));

		// Hoes deal flat damage in vanilla no matter the material.
		sheet.attackDamage = type.attackDamage + (type == ForgeType.AZADA ? 0.0F : damageBonus);
		// Pactos: a blade that bites harder than it should, and pays for it.
		float thirst = Upgrade.thirstDamage(upgrades.percent(Upgrade.PACTO_DE_SED) / 100.0F);
		float glass = Upgrade.glassDamage(upgrades.percent(Upgrade.PACTO_DE_VIDRIO) / 100.0F);
		sheet.attackDamage *= 1.0F + thirst + glass;
		sheet.durability = Math.max(1, Math.round(sheet.durability * (1.0F - Upgrade.glassWear(upgrades.percent(Upgrade.PACTO_DE_VIDRIO) / 100.0F))));
		// Pacto de la prisa: the durability half of it; the speed is applied once the heads are read.
		float haste = upgrades.percent(Upgrade.PACTO_DE_LA_PRISA) / 100.0F;
		sheet.durability = Math.max(1, Math.round(sheet.durability * (1.0F - Upgrade.hasteWear(haste))));
		float frenzy = Upgrade.attackSpeedBoost(upgrades.percent(Upgrade.FRENESI) / 100.0F);
		if (type == ForgeType.LANZA) {
			sheet.attackDuration = spearAttackDuration(head);
			sheet.attackSpeed = 1.0F / sheet.attackDuration - 4.0F + handle.handleAttackSpeed + frenzy;
			sheet.chargeMultiplier = spearChargeMultiplier(head);
			sheet.chargeDelay = spearChargeDelay(head);
		} else {
			sheet.attackSpeed = type.attackSpeed + handle.handleAttackSpeed + frenzy;
		}
		if (type == ForgeType.MAZO) {
			sheet.smashMultiplier = maceSmashMultiplier(head);
		}

		for (int slot : headSlots) {
			if (type.mineableFor(type.slots.get(slot)) != null) {
				sheet.miningSpeeds[slot] = Math.max(1.0F, materials.get(slot).miningSpeed * type.miningSpeedMultiplier * handle.handleMiningSpeed);
				// The pact bites faster through everything the head can mine at all.
				sheet.miningSpeeds[slot] *= 1.0F + Upgrade.hasteSpeed(haste);
			}
		}
	}

	// ------------------------------------------------------------------ part lines

	public static List<Line> partLines(PartType part, ForgeMaterial material) {
		List<Line> lines = new ArrayList<>();
		switch (part) {
			case CABEZA_PICO, CABEZA_HACHA, CABEZA_PALA, CABEZA_MARTILLO -> {
				lines.add(new Line(Stat.DURABILIDAD, material.durability));
				lines.add(new Line(Stat.MINADO, material.miningSpeed));
				lines.add(new Line(Stat.DANO_MAS, material.attackDamageBonus));
			}
			case CABEZA_AZADA -> {
				lines.add(new Line(Stat.DURABILIDAD, material.durability));
				lines.add(new Line(Stat.MINADO, material.miningSpeed));
			}
			case HOJA -> {
				lines.add(new Line(Stat.DURABILIDAD, material.durability));
				lines.add(new Line(Stat.DANO_MAS, material.attackDamageBonus));
			}
			case PUNTA_LANZA -> {
				lines.add(new Line(Stat.DURABILIDAD, material.durability));
				lines.add(new Line(Stat.DANO_MAS, material.attackDamageBonus));
				lines.add(new Line(Stat.CARGA, spearChargeMultiplier(material)));
			}
			case CABEZA_MAZO -> {
				lines.add(new Line(Stat.DURABILIDAD, material.durability));
				lines.add(new Line(Stat.DANO_MAS, material.attackDamageBonus));
				lines.add(new Line(Stat.CAIDA, maceSmashMultiplier(material)));
			}
			case MANGO -> {
				lines.add(new Line(Stat.DURABILIDAD_MULT, material.handleDurability));
				lines.add(new Line(Stat.VELOCIDAD_MAS, material.handleAttackSpeed));
				lines.add(new Line(Stat.MINADO_MULT, material.handleMiningSpeed));
			}
			case ATADURA, GUARDA -> lines.add(new Line(Stat.DURABILIDAD_MAS, Math.round(material.durability * 0.2F)));
			case BRAZOS_ARCO -> {
				lines.add(new Line(Stat.TENSADO, bowDrawSpeed(material)));
				lines.add(new Line(Stat.FLECHA_MAS, arrowDamageBonus(material)));
				lines.add(new Line(Stat.DURABILIDAD_MAS, Math.round(material.durability * 0.3F)));
			}
			case CUERDA -> lines.add(new Line(Stat.DURABILIDAD_MAS, Math.round(material.durability * 0.05F)));
			case PLACA_ESCUDO -> {
				lines.add(new Line(Stat.DURABILIDAD_MAS, material.armorDurability * 16));
				lines.add(new Line(Stat.BLOQUEO, shieldBlockDelay(material)));
			}
			case BORDE_ESCUDO -> {
				lines.add(new Line(Stat.HACHA, shieldAxeDisable(material)));
				lines.add(new Line(Stat.DURABILIDAD_MAS, Math.round(material.durability * 0.3F)));
			}
			case PLACA_CASCO, PLACA_PECHERA, PLACA_GREBAS, PLACA_BOTAS -> {
				ArmorType armorType = armorTypeOf(part);
				lines.add(new Line(Stat.ARMADURA, material.defense(armorType)));
				lines.add(new Line(Stat.DUREZA, material.toughness));
				lines.add(new Line(Stat.DURABILIDAD, armorType.getDurability(material.armorDurability)));
				lines.add(new Line(Stat.EMPUJE, material.knockbackResistance));
			}
			case MEMBRANA -> {
				lines.add(new Line(Stat.PLANEO, glideRatio(material)));
				lines.add(new Line(Stat.VUELO, flightSeconds(material)));
			}
			case GARFIO -> {
				lines.add(new Line(Stat.DANO_MAS, hookPull(material)));
				lines.add(new Line(Stat.DURABILIDAD_MAS, Math.round(material.durability * 0.4F)));
			}
			case PLACA_BARDA, PLACA_LOBO -> {
				lines.add(new Line(Stat.ARMADURA, material.defense(ArmorType.CHESTPLATE)));
				lines.add(new Line(Stat.DUREZA_MAS, material.toughness * 0.5F));
			}
			case PUNTA_FLECHA -> lines.add(new Line(Stat.FLECHA, arrowDamage(material)));
			case EMPLUMADO -> lines.add(new Line(Stat.TENSADO, Math.max(0.6F, 1.0F + material.handleAttackSpeed * 0.8F)));
			case FORRO -> {
				lines.add(new Line(Stat.DURABILIDAD_MULT, material.handleDurability));
				lines.add(new Line(Stat.DUREZA_MAS, material.toughness * 0.25F));
			}
		}
		return lines;
	}

	private static ArmorType armorTypeOf(PartType plate) {
		return switch (plate) {
			case PLACA_CASCO -> ArmorType.HELMET;
			case PLACA_PECHERA -> ArmorType.CHESTPLATE;
			case PLACA_GREBAS -> ArmorType.LEGGINGS;
			default -> ArmorType.BOOTS;
		};
	}

	// ------------------------------------------------------------------ ranges and colors

	/**
	 * How well a membrane flies. A light material (gold, amethyst, leather) carries much further than a
	 * heavy one (obsidian, stone): the same lightness that makes a quick handle makes a good wing.
	 */
	public static float glideRatio(ForgeMaterial membrane) {
		return Math.max(0.4F, 1.0F + membrane.handleAttackSpeed * 2.0F);
	}

	/** How far a grappling hook reaches, from the material of its rope. */
	public static float hookReach(ForgeMaterial rope) {
		return 12.0F + rope.handleDurability * 8.0F;
	}

	/** How hard a hook pulls, from the claw it is made of. */
	public static float hookPull(ForgeMaterial claw) {
		return 0.8F + claw.toughness * 0.12F + claw.attackDamageBonus * 0.05F;
	}

	/** The rope a plain hook has, and the length every other length is measured against. */
	public static final float HOOK_BASE_REACH = 18.0F;

	/**
	 * How much harder a hook of this reach pulls.
	 *
	 * <p>A longer rope is a bigger winch: it has more line on the drum and it drags you further per
	 * turn. Squared off at the ends so a short rope is not useless and a long one is not a catapult.
	 */
	public static float hookHaul(float reach) {
		return Math.max(0.75F, Math.min(1.6F, reach / HOOK_BASE_REACH));
	}

	/**
	 * How fast the claw flies, so that every hook takes the same time to run out its own rope.
	 *
	 * <p>Otherwise a long hook feels slower than a short one, which is backwards: more rope should
	 * mean the claw goes further in the same throw, not that you wait longer for it.
	 */
	public static final int HOOK_FLIGHT_TICKS = 16;

	public static double hookSpeed(double reach) {
		return Math.max(0.6, reach / HOOK_FLIGHT_TICKS);
	}

	/** What an arrow tip is worth on impact: the same bite it would give a blade, halved. */
	public static float arrowDamage(ForgeMaterial tip) {
		return Math.max(1.0F, 2.0F + tip.attackDamageBonus * 0.5F);
	}

	/**
	 * Seconds of flight a membrane holds. A stone wing barely carries you off the roof before it gives
	 * out; gold, amethyst or leather keep you up long enough to cross a valley.
	 */
	public static float flightSeconds(ForgeMaterial membrane) {
		// A curve, not a line: only the lightest membranes keep you up for long.
		return 1.0F + (float) Math.pow(Math.max(0.0F, glideRatio(membrane) - 0.4F), 1.6) * 8.5F;
	}

	/** Worst, average and best value of a stat over every material combination of this item, without upgrades. */
	public static synchronized Range range(ForgeType type, Stat stat) {
		Map<Stat, Range> ranges = TYPE_RANGES.get(type);
		if (ranges == null) {
			Accumulator accumulator = new Accumulator();
			List<ForgeMaterial> combo = new ArrayList<>();
			for (int i = 0; i < type.slots.size(); i++) {
				combo.add(ForgeMaterial.MADERA);
			}
			enumerate(type, 0, combo, accumulator);
			ranges = accumulator.ranges();
			TYPE_RANGES.put(type, ranges);
		}
		return ranges.getOrDefault(stat, Range.FLAT);
	}

	private static void enumerate(ForgeType type, int slot, List<ForgeMaterial> combo, Accumulator accumulator) {
		if (slot == type.slots.size()) {
			accumulator.add(sheet(type, combo, Upgrades.EMPTY).lines());
			return;
		}
		for (ForgeMaterial material : ForgeMaterial.values()) {
			if (type.slots.get(slot).accepts(material)) {
				combo.set(slot, material);
				enumerate(type, slot + 1, combo, accumulator);
			}
		}
	}

	/** Worst, average and best value of a stat over every material this part can be cut from. */
	public static synchronized Range partRange(PartType part, Stat stat) {
		Map<Stat, Range> ranges = PART_RANGES.get(part);
		if (ranges == null) {
			Accumulator accumulator = new Accumulator();
			for (ForgeMaterial material : ForgeMaterial.values()) {
				if (part.accepts(material)) {
					accumulator.add(partLines(part, material));
				}
			}
			ranges = accumulator.ranges();
			PART_RANGES.put(part, ranges);
		}
		return ranges.getOrDefault(stat, Range.FLAT);
	}

	private static final class Accumulator {
		final Map<Stat, double[]> values = new EnumMap<>(Stat.class);

		void add(List<Line> lines) {
			for (Line line : lines) {
				double[] v = this.values.computeIfAbsent(line.stat(), s -> new double[] {Double.MAX_VALUE, 0.0, -Double.MAX_VALUE, 0.0});
				v[0] = Math.min(v[0], line.value());
				v[1] += line.value();
				v[2] = Math.max(v[2], line.value());
				v[3]++;
			}
		}

		Map<Stat, Range> ranges() {
			Map<Stat, Range> ranges = new EnumMap<>(Stat.class);
			this.values.forEach((stat, v) -> ranges.put(stat, new Range(v[0], v[1] / v[3], v[2])));
			return ranges;
		}
	}

	/** -1 for the worst value, 0 for the average, 1 for the best; beyond the range it is clamped. */
	public static double rank(double value, Range range, boolean higherIsBetter) {
		double t;
		double epsilon = 1.0E-6;
		if (Math.abs(value - range.mean()) < epsilon) {
			t = 0.0;
		} else if (value > range.mean()) {
			t = range.max() - range.mean() > epsilon ? (value - range.mean()) / (range.max() - range.mean()) : 1.0;
		} else {
			t = range.mean() - range.min() > epsilon ? -(range.mean() - value) / (range.mean() - range.min()) : -1.0;
		}
		t = higherIsBetter ? t : -t;
		return Math.max(-1.0, Math.min(1.0, t));
	}

	/** Dark red, red, white, green, blue, purple from worst to best. */
	public static int color(double rank) {
		for (int i = 1; i < SCALE_STOPS.length; i++) {
			if (rank <= SCALE_STOPS[i]) {
				double f = (rank - SCALE_STOPS[i - 1]) / (SCALE_STOPS[i] - SCALE_STOPS[i - 1]);
				return lerp(SCALE_COLORS[i - 1], SCALE_COLORS[i], Math.max(0.0, Math.min(1.0, f)));
			}
		}
		return SCALE_COLORS[SCALE_COLORS.length - 1];
	}

	public static int color(ForgeType type, Line line) {
		return color(rank(line.value(), range(type, line.stat()), line.stat().higherIsBetter));
	}

	public static int color(PartType part, Line line) {
		return color(rank(line.value(), partRange(part, line.stat()), line.stat().higherIsBetter));
	}

	/** Knockback resistance is zero for most materials; it only shows when there is some. */
	public static boolean shown(Line line) {
		return line.stat() != Stat.EMPUJE || line.value() > 0.0;
	}

	public static MutableComponent colored(ForgeType type, Line line) {
		return line.text().copy().withColor(color(type, line));
	}

	public static MutableComponent colored(PartType part, Line line) {
		return line.text().copy().withColor(color(part, line));
	}

	private static int lerp(int from, int to, double f) {
		int r = (int) Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * f);
		int g = (int) Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * f);
		int b = (int) Math.round((from & 255) + ((to & 255) - (from & 255)) * f);
		return r << 16 | g << 8 | b;
	}

	public static String format(Format format, double value) {
		String number = number(Math.abs(value));
		return switch (format) {
			case PLAIN, SECONDS -> number(value);
			case MULTIPLIER -> "x" + number(value);
			case PLUS -> "+" + number(value);
			case SIGNED -> value > 0 ? "+" + number : value < 0 ? "-" + number : "0";
			case PERCENT -> String.valueOf(Math.round(value * 100));
		};
	}

	/** Every stat value of a forged stack, upgrades and Maestria included; empty for anything else. */
	public static Map<Stat, Double> values(net.minecraft.world.item.ItemStack stack) {
		Map<Stat, Double> values = new EnumMap<>(Stat.class);
		dev.forja.part.ForgedParts parts = stack.get(dev.forja.registry.ModComponents.PARTS);
		if (parts != null) {
			for (Line line : sheet(parts.type(), parts.materials(), stack.getOrDefault(dev.forja.registry.ModComponents.UPGRADES, Upgrades.EMPTY), Mastery.level(stack)).lines()) {
				values.put(line.stat(), line.value());
			}
		}
		return values;
	}

	/** The line's text followed by its change against a previous value, green when better and red when worse. */
	public static Component withDelta(Component text, Line line, @org.jspecify.annotations.Nullable Double before) {
		if (before == null || Math.abs(line.value() - before) <= 1e-4) {
			return text;
		}
		boolean better = (line.value() > before) == line.stat().higherIsBetter;
		return text.copy().append(Component.literal(" " + formatDelta(line.stat(), line.value() - before)).withColor(better ? 0x55FF55 : 0xFF5555));
	}

	/** A signed change of a stat, in the same units its line shows. */
	public static String formatDelta(Stat stat, double delta) {
		String sign = delta > 0 ? "+" : "-";
		return stat.format == Format.PERCENT ? sign + Math.round(Math.abs(delta) * 100) : sign + number(Math.abs(delta));
	}

	private static String number(double value) {
		double rounded = Math.round(value * 100.0) / 100.0;
		return rounded == Math.floor(rounded) ? String.valueOf((long) rounded) : String.format(Locale.ROOT, "%.2f", rounded).replaceAll("0$", "");
	}
}
