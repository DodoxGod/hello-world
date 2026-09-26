package dev.forja.world;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.item.TemplateItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.UpgradeRecipes;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Forja in the world: village smiths keep templates and some of their own forged gear in their
 * chests, and old structures hide engraved templates and worn gear that sometimes carries an upgrade.
 * Bastion treasure can hold netherite gear.
 */
public final class ForjaLoot {
	private static final List<PartType> TOOL_PARTS = List.of(
		PartType.CABEZA_PICO, PartType.CABEZA_HACHA, PartType.CABEZA_PALA, PartType.CABEZA_AZADA, PartType.CABEZA_MARTILLO, PartType.MANGO, PartType.ATADURA
	);
	private static final List<PartType> WEAPON_PARTS = List.of(
		PartType.HOJA, PartType.GUARDA, PartType.PUNTA_LANZA, PartType.CABEZA_MAZO, PartType.BRAZOS_ARCO, PartType.CUERDA, PartType.MANGO
	);
	private static final List<PartType> ARMOR_PARTS = List.of(
		PartType.PLACA_CASCO, PartType.PLACA_PECHERA, PartType.PLACA_GREBAS, PartType.PLACA_BOTAS, PartType.FORRO, PartType.PLACA_ESCUDO, PartType.BORDE_ESCUDO
	);
	private static final List<ForgeType> TOOLS = List.of(ForgeType.PICO, ForgeType.HACHA, ForgeType.PALA, ForgeType.AZADA, ForgeType.MARTILLO, ForgeType.PICAHACHA);
	private static final List<ForgeType> WEAPONS = List.of(ForgeType.ESPADA, ForgeType.DAGA, ForgeType.ESPADON, ForgeType.LANZA, ForgeType.MAZO, ForgeType.GUADANA, ForgeType.ARCO, ForgeType.BALLESTA);
	private static final List<ForgeType> ARMOR = List.of(ForgeType.CASCO, ForgeType.PECHERA, ForgeType.GREBAS, ForgeType.BOTAS, ForgeType.ESCUDO);
	private static final List<ResourceKey<LootTable>> RUINS = List.of(
		BuiltInLootTables.SIMPLE_DUNGEON, BuiltInLootTables.ABANDONED_MINESHAFT, BuiltInLootTables.STRONGHOLD_CORRIDOR,
		BuiltInLootTables.DESERT_PYRAMID, BuiltInLootTables.JUNGLE_TEMPLE, BuiltInLootTables.RUINED_PORTAL
	);
	/** The chest of the abandoned forge structure; its JSON holds the plain metals, this adds Forja's own finds. */
	public static final ResourceKey<LootTable> ABANDONED_FORGE = ResourceKey.create(Registries.LOOT_TABLE, dev.forja.Forja.id("chests/forja_abandonada"));
	private static final ForgeMaterial[] COMMON_HEADS = {ForgeMaterial.PIEDRA, ForgeMaterial.COBRE, ForgeMaterial.HIERRO, ForgeMaterial.HIERRO, ForgeMaterial.HUESO, ForgeMaterial.RESINA};
	private static final ForgeMaterial[] RARE_HEADS = {ForgeMaterial.ORO, ForgeMaterial.AMATISTA, ForgeMaterial.ESMERALDA, ForgeMaterial.PRISMARINA, ForgeMaterial.CUARZO, ForgeMaterial.DIAMANTE, ForgeMaterial.ECO};
	private static final ForgeMaterial[] BINDINGS = {ForgeMaterial.MADERA, ForgeMaterial.MADERA, ForgeMaterial.CUERO, ForgeMaterial.HUESO, ForgeMaterial.COBRE, ForgeMaterial.HIERRO, ForgeMaterial.ESCAMA};

	private ForjaLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			RandomSource random = context.getRandom();
			HolderLookup.Provider registries = context.getLevel().registryAccess();
			if (table.is(BuiltInLootTables.VILLAGE_TOOLSMITH)) {
				smith(random, registries, drops, TOOL_PARTS, TOOLS);
			} else if (table.is(BuiltInLootTables.VILLAGE_WEAPONSMITH)) {
				smith(random, registries, drops, WEAPON_PARTS, WEAPONS);
			} else if (table.is(BuiltInLootTables.VILLAGE_ARMORER)) {
				smith(random, registries, drops, ARMOR_PARTS, ARMOR);
			} else if (table.is(ABANDONED_FORGE)) {
				abandonedForge(random, registries, drops);
			} else if (table.is(BuiltInLootTables.FISHING_TREASURE)) {
				if (random.nextFloat() < 0.15F) {
					drops.add(orb(random, 10, 30));
				}
			} else if (table.is(BuiltInLootTables.TRIAL_CHAMBERS_REWARD) || table.is(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS)) {
				float ominous = table.is(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS) ? 2.0F : 1.0F;
				if (random.nextFloat() < 0.25F * ominous) {
					drops.add(orb(random, 20, 40 + 10 * (int) ominous));
				}
				if (random.nextFloat() < 0.2F) {
					drops.add(template(PartType.values()[random.nextInt(PartType.values().length)]));
				}
			} else if (table.is(BuiltInLootTables.END_CITY_TREASURE) || table.is(BuiltInLootTables.ANCIENT_CITY)) {
				if (random.nextFloat() < 0.3F) {
					drops.add(orb(random, 25, 50));
				}
				if (random.nextFloat() < 0.1F) {
					drops.add(Legends.create(random, registries));
				}
				if (random.nextFloat() < 0.2F) {
					ForgeMaterial head = table.is(BuiltInLootTables.ANCIENT_CITY) ? ForgeMaterial.ECO : ForgeMaterial.PURPUR;
					List<ForgeType> all = new ArrayList<>(TOOLS);
					all.addAll(WEAPONS);
					all.addAll(ARMOR);
					drops.add(gear(random, registries, pick(random, all), head, 0.6F));
				}
			} else if (table.is(BuiltInLootTables.PIGLIN_BARTERING)) {
				// Bartering rolls one entry per ingot, so keep this rare.
				if (random.nextFloat() < 0.01F) {
					drops.add(dev.forja.item.UpgradeOrbItem.create(random.nextBoolean() ? Upgrade.ABSORCION : Upgrade.ASPECTO_IGNEO, 10 + 5 * random.nextInt(3)));
				}
			} else if (table.is(BuiltInLootTables.BASTION_TREASURE)) {
				if (random.nextFloat() < 0.35F) {
					drops.add(gear(random, registries, pick(random, WEAPONS), ForgeMaterial.NETHERITA, 0.35F));
				}
				if (random.nextFloat() < 0.08F) {
					drops.add(Legends.create(random, registries));
				}
			} else {
				for (ResourceKey<LootTable> ruin : RUINS) {
					if (table.is(ruin)) {
						ruins(random, registries, drops);
						break;
					}
				}
			}
		});
	}

	private static void smith(RandomSource random, HolderLookup.Provider registries, List<ItemStack> drops, List<PartType> parts, List<ForgeType> types) {
		if (random.nextFloat() < 0.6F) {
			drops.add(new ItemStack(ModItems.PLANTILLA, 1 + random.nextInt(2)));
		}
		if (random.nextFloat() < 0.4F) {
			drops.add(template(pick(random, parts)));
		}
		if (random.nextFloat() < 0.15F) {
			drops.add(gear(random, registries, pick(random, types), COMMON_HEADS[random.nextInt(COMMON_HEADS.length)], 0.0F));
		}
	}

	private static void ruins(RandomSource random, HolderLookup.Provider registries, List<ItemStack> drops) {
		if (random.nextFloat() < 0.25F) {
			drops.add(template(PartType.values()[random.nextInt(PartType.values().length)]));
		}
		if (random.nextFloat() < 0.12F) {
			List<ForgeType> all = new ArrayList<>(TOOLS);
			all.addAll(WEAPONS);
			all.addAll(ARMOR);
			ForgeMaterial head = random.nextFloat() < 0.3F ? RARE_HEADS[random.nextInt(RARE_HEADS.length)] : COMMON_HEADS[random.nextInt(COMMON_HEADS.length)];
			drops.add(gear(random, registries, pick(random, all), head, 0.5F));
		}
	}

	/** Two engraved templates, often a piece of worn gear with an upgrade, sometimes an upgrade orb. */
	private static void abandonedForge(RandomSource random, HolderLookup.Provider registries, List<ItemStack> drops) {
		for (int i = 0; i < 2; i++) {
			drops.add(template(PartType.values()[random.nextInt(PartType.values().length)]));
		}
		if (random.nextFloat() < 0.6F) {
			List<ForgeType> all = new ArrayList<>(TOOLS);
			all.addAll(WEAPONS);
			all.addAll(ARMOR);
			ForgeMaterial head = random.nextFloat() < 0.4F ? RARE_HEADS[random.nextInt(RARE_HEADS.length)] : COMMON_HEADS[random.nextInt(COMMON_HEADS.length)];
			drops.add(gear(random, registries, pick(random, all), head, 0.7F));
		}
		if (random.nextFloat() < 0.35F) {
			drops.add(orb(random, 10, 30));
		}
		// The forge's folklore: now and then a ruin keeps a named piece.
		if (random.nextFloat() < 0.12F) {
			drops.add(Legends.create(random, registries));
		}
	}

	/** An orb of any upgrade, its percentage a multiple of 5 between the bounds. */
	public static ItemStack orb(RandomSource random, int min, int max) {
		Upgrade upgrade = Upgrade.values()[random.nextInt(Upgrade.values().length)];
		int steps = (max - min) / 5;
		return dev.forja.item.UpgradeOrbItem.create(upgrade, min + 5 * random.nextInt(steps + 1));
	}

	public static ItemStack template(PartType part) {
		ItemStack stack = new ItemStack(ModItems.PLANTILLA);
		TemplateItem.engrave(stack, part);
		return stack;
	}

	/** Worn found gear: the given head or plate, random handle and bindings, maybe one upgrade. */
	public static ItemStack gear(RandomSource random, HolderLookup.Provider registries, ForgeType type, ForgeMaterial head, float upgradeChance) {
		List<ForgeMaterial> materials = new ArrayList<>();
		for (PartType slot : type.slots) {
			ForgeMaterial material = switch (slot.role) {
				case HEAD, PLATE -> head;
				case LINING -> random.nextBoolean() ? ForgeMaterial.CUERO : ForgeMaterial.HIERRO;
				default -> BINDINGS[random.nextInt(BINDINGS.length)];
			};
			materials.add(slot.accepts(material) ? material : ForgeMaterial.HIERRO);
		}
		ItemStack stack = Assembler.create(type, materials, registries);
		// Somebody made it once, and how well is part of what you find out.
		stack.set(dev.forja.registry.ModComponents.POTENCIAL, dev.forja.forge.Potential.found(random));
		if (random.nextFloat() < upgradeChance) {
			List<Upgrade> options = new ArrayList<>();
			for (Upgrade upgrade : Upgrade.values()) {
				if (upgrade.appliesTo(type)) {
					options.add(upgrade);
				}
			}
			if (!options.isEmpty()) {
				stack = UpgradeRecipes.upgraded(stack, type, pick(random, options), 10 * (1 + random.nextInt(5)), registries);
			}
		}
		stack.setDamageValue(Math.round(stack.getMaxDamage() * (0.1F + random.nextFloat() * 0.5F)));
		return stack;
	}

	private static <T> T pick(RandomSource random, List<T> values) {
		return values.get(random.nextInt(values.size()));
	}
}
