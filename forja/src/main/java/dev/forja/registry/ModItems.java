package dev.forja.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.forja.Forja;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.item.ForgedItems;
import dev.forja.item.GuideBookItem;
import dev.forja.item.PartItem;
import dev.forja.item.TemplateItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.Weapon;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {
	private static final Map<PartType, PartItem> PARTS = new EnumMap<>(PartType.class);
	private static final Map<ForgeType, Item> FORGED = new EnumMap<>(ForgeType.class);
	public static Item MESA_DE_FORJA;
	public static Item MESA_DE_TALABARTERIA;
	public static Item YUNQUE_DEL_HERRERO;
	public static Item FAROL_DE_PAVESA;
	public static Item ASCUA;
	/** Escoria: what the last piece of a Escoria Viviente cools into, and the only place it comes from. */
	public static Item ESCORIA;
	public static Item MOLDE_DE_FUNDICION;
	public static Item COLADOR;
	public static Item CUBA_DE_COLADA;
	public static Item CONDUCTO_DE_COLADA;
	public static Item CONDUCTO_DE_ACERO;
	public static Item CONDUCTO_DE_DAMASCO;
	public static Item CANO_DE_COLADA;
	public static Item CAJA_DE_MOLDEO;
	public static Item CAJA_DE_MOLDEO_DE_ACERO;
	public static Item CAJA_DE_MOLDEO_DE_DAMASCO;
	public static Item CRISOL_DE_BARRO;
	public static Item CRISOL_DE_HIERRO;
	public static Item CRISOL_DE_OBSIDIANA;
	public static Item MESA_DE_LOSA;
	public static Item MESA_DE_BRASA;
	public static Item MESA_DE_ALMAS;
	public static Item MARCO;
	public static Item MESA_DE_PIEZAS;
	public static Item MESA_DE_FORJA_MAYOR;
	public static Item GUIA_DE_FORJA;
	public static Item PLANTILLA;
	public static Item ORBE_DE_MEJORA;
	/** The blank bar that mends gear in the gear's own metal. See item/TemperIngotItem. */
	public static Item LINGOTE_DE_TEMPLE;
	public static Item YUNQUE_PORTATIL;
	public static Item PLACA_HUECA;
	/** Master flux: what an upgrade asks for to go past ninety. See forge/Potential. */
	public static Item FUNDENTE_MAESTRO;
	/** An orb with nothing in it yet: the extraction table fills one with the upgrade it takes off a piece. */
	public static Item ORBE_VACIO;
	public static Item MARTILLO_DEL_MAESTRO;
	public static Item ARMARIO_DE_PIEZAS;
	public static Item MESA_DE_EXTRACCION;
	public static Item HUEVO_HERRERO_CAIDO;
	public static Item HUEVO_AUTOMATA;
	public static Item HUEVO_CORAZA;
	public static Item HUEVO_PAVESA;
	public static Item SELLO;

	private ModItems() {
	}

	public static PartItem part(PartType type) {
		return PARTS.get(type);
	}

	public static Item TALISMAN;
	public static Item HIERRO_ESTELAR;
	private static final java.util.Map<String, Item> ALLOYS = new java.util.LinkedHashMap<>();
	/** The glass jar that keeps a night; see item/EssenceJarItem. */
	public static Item JARRA;
	public static Item CORAZON_DE_FORJA;
	public static Item FRAGUA_APAGADA;
	public static Item CINTURON;

	/** The ingot of one alloy, by its id. */
	public static Item alloy(String id) {
		return ALLOYS.get(id);
	}

	public static Item forged(ForgeType type) {
		return FORGED.get(type);
	}

	public static void init() {
		// Star iron is a material, so it has to exist before any part is built out of it.
		HIERRO_ESTELAR = register("hierro_estelar", Item::new, new Item.Properties());
		// The alloys are materials too, so their ingots come before anything is built out of them.
		for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.forge.Alloys.ALL) {
			ALLOYS.put(recipe.id(), register(recipe.id(), Item::new, new Item.Properties()));
		}
		JARRA = register("jarra", dev.forja.item.EssenceJarItem::new, new Item.Properties().stacksTo(16));
		CORAZON_DE_FORJA = register("corazon_de_forja", Item::new, new Item.Properties().stacksTo(4).rarity(net.minecraft.world.item.Rarity.EPIC));

		for (PartType type : PartType.values()) {
			Item.Properties properties = new Item.Properties();
			Assembler.writePart(type, ForgeMaterial.HIERRO, propertiesSink(properties));
			PARTS.put(type, (PartItem) register(type.id(), p -> new PartItem(type, p), properties));
		}

		for (ForgeType type : ForgeType.values()) {
			Item.Properties properties = new Item.Properties().durability(1);
			// Not every forged thing is a tool: arrows are ammunition and stack.
			if (type == ForgeType.ESCUDO) {
				shieldDefaults(properties);
			} else if (type == ForgeType.LANZA) {
				spearDefaults(properties);
			} else if (type.kind == ForgeType.Kind.MUNICION) {
				properties = new Item.Properties().stacksTo(64);
			} else if (type == ForgeType.BALLESTA) {
				properties.component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
			}
			Assembler.write(
				type,
				Assembler.defaultMaterials(type),
				dev.forja.upgrade.Upgrades.EMPTY,
				BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK),
				BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ITEM),
				propertiesSink(properties)
			);
			FORGED.put(type, register(type.id(), factory(type), properties));
		}

		FRAGUA_APAGADA = register("fragua_apagada", p -> new BlockItem(ModBlocks.FRAGUA_APAGADA, p),
			new Item.Properties().useBlockDescriptionPrefix().rarity(net.minecraft.world.item.Rarity.EPIC));
		MESA_DE_PIEZAS = register("mesa_de_piezas", p -> new BlockItem(ModBlocks.MESA_DE_PIEZAS, p), new Item.Properties().useBlockDescriptionPrefix());
		MESA_DE_FORJA_MAYOR = register("mesa_de_forja_mayor", p -> new BlockItem(ModBlocks.MESA_DE_FORJA_MAYOR, p),
			new Item.Properties().useBlockDescriptionPrefix());
		YUNQUE_DEL_HERRERO = register("yunque_del_herrero", p -> new BlockItem(ModBlocks.YUNQUE_DEL_HERRERO, p),
			new Item.Properties().useBlockDescriptionPrefix().rarity(net.minecraft.world.item.Rarity.EPIC));
		FAROL_DE_PAVESA = register("farol_de_pavesa", p -> new BlockItem(ModBlocks.FAROL_DE_PAVESA, p),
			new Item.Properties().useBlockDescriptionPrefix().rarity(net.minecraft.world.item.Rarity.RARE));
		ASCUA = register("ascua", Item::new, new Item.Properties());
		ESCORIA = register("escoria", Item::new, new Item.Properties());
		MOLDE_DE_FUNDICION = register("molde_de_fundicion", dev.forja.item.CastingMouldItem::new,
			new Item.Properties().stacksTo(1));
		COLADOR = register("colador", dev.forja.item.StrainerItem::new, new Item.Properties().stacksTo(1));
		MARCO = register("marco", dev.forja.item.CastingFrameItem::new, new Item.Properties().stacksTo(1));
		MESA_DE_LOSA = register("mesa_de_losa", p -> new BlockItem(ModBlocks.MESA_DE_LOSA, p), new Item.Properties().useBlockDescriptionPrefix());
		MESA_DE_BRASA = register("mesa_de_brasa", p -> new BlockItem(ModBlocks.MESA_DE_BRASA, p), new Item.Properties().useBlockDescriptionPrefix());
		MESA_DE_ALMAS = register("mesa_de_almas", p -> new BlockItem(ModBlocks.MESA_DE_ALMAS, p),
			new Item.Properties().useBlockDescriptionPrefix().rarity(net.minecraft.world.item.Rarity.UNCOMMON));
		CONDUCTO_DE_COLADA = register("conducto_de_colada", p -> new BlockItem(ModBlocks.CONDUCTO_DE_COLADA, p), new Item.Properties().useBlockDescriptionPrefix());
		CONDUCTO_DE_ACERO = register("conducto_de_acero", p -> new BlockItem(ModBlocks.CONDUCTO_DE_ACERO, p), new Item.Properties().useBlockDescriptionPrefix());
		CONDUCTO_DE_DAMASCO = register("conducto_de_damasco", p -> new BlockItem(ModBlocks.CONDUCTO_DE_DAMASCO, p),
			new Item.Properties().useBlockDescriptionPrefix().rarity(net.minecraft.world.item.Rarity.UNCOMMON));
		CUBA_DE_COLADA = register("cuba_de_colada", p -> new BlockItem(ModBlocks.CUBA_DE_COLADA, p), new Item.Properties().useBlockDescriptionPrefix());
		CANO_DE_COLADA = register("cano_de_colada", p -> new BlockItem(ModBlocks.CANO_DE_COLADA, p), new Item.Properties().useBlockDescriptionPrefix());
		CAJA_DE_MOLDEO = register("caja_de_moldeo", p -> new BlockItem(ModBlocks.CAJA_DE_MOLDEO, p), new Item.Properties().useBlockDescriptionPrefix());
		CAJA_DE_MOLDEO_DE_ACERO = register("caja_de_moldeo_de_acero", p -> new BlockItem(ModBlocks.CAJA_DE_MOLDEO_DE_ACERO, p), new Item.Properties().useBlockDescriptionPrefix());
		CAJA_DE_MOLDEO_DE_DAMASCO = register("caja_de_moldeo_de_damasco", p -> new BlockItem(ModBlocks.CAJA_DE_MOLDEO_DE_DAMASCO, p),
			new Item.Properties().useBlockDescriptionPrefix().rarity(net.minecraft.world.item.Rarity.RARE));
		CRISOL_DE_BARRO = register("crisol_de_barro", p -> new BlockItem(ModBlocks.CRISOL_DE_BARRO, p), new Item.Properties().useBlockDescriptionPrefix());
		CRISOL_DE_HIERRO = register("crisol_de_hierro", p -> new BlockItem(ModBlocks.CRISOL_DE_HIERRO, p), new Item.Properties().useBlockDescriptionPrefix());
		CRISOL_DE_OBSIDIANA = register("crisol_de_obsidiana", p -> new BlockItem(ModBlocks.CRISOL_DE_OBSIDIANA, p),
			new Item.Properties().useBlockDescriptionPrefix().rarity(net.minecraft.world.item.Rarity.RARE));
		MESA_DE_TALABARTERIA = register("mesa_de_talabarteria", p -> new BlockItem(ModBlocks.MESA_DE_TALABARTERIA, p), new Item.Properties().useBlockDescriptionPrefix());
		MESA_DE_FORJA = register("mesa_de_forja", p -> new BlockItem(ModBlocks.MESA_DE_FORJA, p), new Item.Properties().useBlockDescriptionPrefix());
		GUIA_DE_FORJA = register("guia_de_forja", GuideBookItem::new, new Item.Properties().stacksTo(1));
		PLANTILLA = register("plantilla", TemplateItem::new, new Item.Properties().stacksTo(16));
		LINGOTE_DE_TEMPLE = register("lingote_de_temple", dev.forja.item.TemperIngotItem::new, new Item.Properties().stacksTo(16));
		PLACA_HUECA = register("placa_hueca", Item::new, new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON));
		ORBE_VACIO = register("orbe_vacio", Item::new, new Item.Properties().stacksTo(16));
		FUNDENTE_MAESTRO = register("fundente_maestro", Item::new, new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)
			.component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
				java.util.List.of(net.minecraft.network.chat.Component.translatable("item.forja.fundente_maestro.desc")))));
		MARTILLO_DEL_MAESTRO = register("martillo_del_maestro", dev.forja.item.MasterHammerItem::new,
			new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)
				.component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
					java.util.List.of(net.minecraft.network.chat.Component.translatable("item.forja.martillo_del_maestro.desc")))));
		HUEVO_HERRERO_CAIDO = spawnEgg("herrero_caido", ModEntities.HERRERO_CAIDO);
		HUEVO_AUTOMATA = spawnEgg("automata_de_forja", ModEntities.AUTOMATA);
		HUEVO_CORAZA = spawnEgg("coraza_vacia", ModEntities.CORAZA);
		HUEVO_PAVESA = spawnEgg("pavesa", ModEntities.PAVESA);
		ARMARIO_DE_PIEZAS = register("armario_de_piezas", p -> new BlockItem(ModBlocks.ARMARIO_DE_PIEZAS, p),
			new Item.Properties().useBlockDescriptionPrefix());
		MESA_DE_EXTRACCION = register("mesa_de_extraccion", p -> new BlockItem(ModBlocks.MESA_DE_EXTRACCION, p),
			new Item.Properties().useBlockDescriptionPrefix());
		YUNQUE_PORTATIL = register("yunque_portatil", dev.forja.item.PortableAnvilItem::new,
			new Item.Properties().stacksTo(1).durability(128).rarity(net.minecraft.world.item.Rarity.UNCOMMON)
				.component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
					java.util.List.of(net.minecraft.network.chat.Component.translatable("item.forja.yunque_portatil.desc")))));
		SELLO = register("sello", dev.forja.item.SealItem::new, new Item.Properties().stacksTo(16));
		TALISMAN = register("talisman", dev.forja.item.TalismanItem::new, new Item.Properties().stacksTo(1));
		CINTURON = register("cinturon", dev.forja.item.ToolBeltItem::new, new Item.Properties().stacksTo(1).durability(512)
			.component(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY));
		ORBE_DE_MEJORA = register("orbe_de_mejora", dev.forja.item.UpgradeOrbItem::new, new Item.Properties().stacksTo(16));

		Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			Forja.id("forja"),
			FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.forja"))
				.icon(() -> Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.DIAMANTE, ForgeMaterial.PIEDRA, ForgeMaterial.HIERRO)))
				.displayItems((parameters, output) -> creativeStacks(parameters.holders()).forEach(output::accept))
				.build()
		);
		// Every part in every material would bury the gear, so the parts get a tab of their own.
		Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			Forja.id("piezas"),
			FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.forja.piezas"))
				.icon(() -> Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO))
				.displayItems((parameters, output) -> partStacks().forEach(output::accept))
				.build()
		);
	}

	/** The same blocking setup as the vanilla shield; stacks adjust delay and axe cooldown per material. */
	private static void shieldDefaults(Item.Properties properties) {
		properties.equippableUnswappable(EquipmentSlot.OFFHAND)
			.delayedComponent(
				DataComponents.BLOCKS_ATTACKS,
				context -> new BlocksAttacks(
					0.25F,
					1.0F,
					List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
					new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
					Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
					Optional.of(SoundEvents.SHIELD_BLOCK),
					Optional.of(SoundEvents.SHIELD_BREAK)
				)
			)
			.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK);
	}

	/** What every vanilla spear shares; the charge attack and jab speed are written per material. */
	private static void spearDefaults(Item.Properties properties) {
		properties.delayedHolderComponent(DataComponents.DAMAGE_TYPE, DamageTypes.SPEAR)
			.component(DataComponents.ATTACK_RANGE, new AttackRange(2.0F, 4.5F, 2.0F, 6.5F, 0.125F, 0.5F))
			.component(DataComponents.MINIMUM_ATTACK_CHARGE, 1.0F)
			.component(DataComponents.USE_EFFECTS, new UseEffects(true, false, 1.0F))
			.component(DataComponents.WEAPON, new Weapon(1));
	}

	private static Function<Item.Properties, Item> factory(ForgeType type) {
		return switch (type) {
			case ARCO -> p -> new ForgedItems.ForgedBowItem(type, p);
			case MAZO -> ForgedItems.ForgedMaceItem::new;
			case BALLESTA -> ForgedItems.ForgedCrossbowItem::new;
			case CANA -> ForgedItems.ForgedFishingRodItem::new;
			case ESCUDO -> ForgedItems.ForgedShieldItem::new;
			case HACHA, PICAHACHA -> p -> new ForgedItems.ForgedAxeItem(type, p);
			case PALA -> p -> new ForgedItems.ForgedShovelItem(type, p);
			case AZADA -> p -> new ForgedItems.ForgedHoeItem(type, p);
			case FLECHA -> ForgedItems.ForgedArrowItem::new;
			default -> p -> new ForgedItems.ForgedItem(type, p);
		};
	}

	private static Assembler.ComponentSink propertiesSink(Item.Properties properties) {
		return new Assembler.ComponentSink() {
			@Override
			public <T> void set(DataComponentType<T> type, T value) {
				properties.component(type, value);
			}
		};
	}

	/** A spawn egg for one of our own mobs: the type travels in the stack, the picture is its own. */
	private static Item spawnEgg(String name, net.minecraft.world.entity.EntityType<?> type) {
		return register("huevo_" + name, net.minecraft.world.item.SpawnEggItem::new, new Item.Properties()
			.component(net.minecraft.core.component.DataComponents.ENTITY_DATA,
				net.minecraft.world.item.component.TypedEntityData.of(type, new net.minecraft.nbt.CompoundTag())));
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Forja.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** Every part in every material it can be cut from. */
	private static List<ItemStack> partStacks() {
		List<ItemStack> stacks = new ArrayList<>();
		for (PartType type : PartType.values()) {
			ItemStack engraved = new ItemStack(PLANTILLA);
			TemplateItem.engrave(engraved, type);
			stacks.add(engraved);
			for (ForgeMaterial material : ForgeMaterial.values()) {
				if (type.accepts(material)) {
					stacks.add(Assembler.createPart(type, material));
				}
			}
		}
		return stacks;
	}

	/** The tables, the guide, templates, orbs and a showcase of finished gear. */
	private static List<ItemStack> creativeStacks(HolderLookup.Provider registries) {
		List<ItemStack> stacks = new ArrayList<>();
		stacks.add(new ItemStack(MESA_DE_PIEZAS));
		stacks.add(new ItemStack(MESA_DE_FORJA_MAYOR));
		stacks.add(new ItemStack(MESA_DE_FORJA));
		stacks.add(new ItemStack(MESA_DE_TALABARTERIA));
		stacks.add(new ItemStack(GUIA_DE_FORJA));
		stacks.add(new ItemStack(PLANTILLA));
		stacks.add(new ItemStack(LINGOTE_DE_TEMPLE));
		stacks.add(new ItemStack(YUNQUE_PORTATIL));
		stacks.add(new ItemStack(PLACA_HUECA));
		stacks.add(new ItemStack(FUNDENTE_MAESTRO));
		stacks.add(new ItemStack(ORBE_VACIO));
		stacks.add(new ItemStack(MARTILLO_DEL_MAESTRO));
		stacks.add(new ItemStack(ARMARIO_DE_PIEZAS));
		stacks.add(new ItemStack(MESA_DE_EXTRACCION));
		stacks.add(new ItemStack(HUEVO_HERRERO_CAIDO));
		stacks.add(new ItemStack(HUEVO_AUTOMATA));
		stacks.add(new ItemStack(HUEVO_CORAZA));
		stacks.add(new ItemStack(HUEVO_PAVESA));
		stacks.add(new ItemStack(CINTURON));
		stacks.add(new ItemStack(HIERRO_ESTELAR));
		for (Item ingot : ALLOYS.values()) {
			stacks.add(new ItemStack(ingot));
		}
		stacks.add(new ItemStack(JARRA));
		stacks.add(new ItemStack(CORAZON_DE_FORJA));
		stacks.add(new ItemStack(FRAGUA_APAGADA));
		stacks.add(new ItemStack(YUNQUE_DEL_HERRERO));
		stacks.add(new ItemStack(FAROL_DE_PAVESA));
		stacks.add(new ItemStack(ASCUA));
		stacks.addAll(dev.forja.item.CastingMouldItem.all());
		stacks.add(dev.forja.item.StrainerItem.of(null));
		stacks.add(dev.forja.item.StrainerItem.of(dev.forja.material.ForgeMaterial.ACERO));
		stacks.add(dev.forja.item.StrainerItem.of(dev.forja.material.ForgeMaterial.DAMASCO));
		stacks.add(new ItemStack(CUBA_DE_COLADA));
		stacks.add(new ItemStack(CONDUCTO_DE_COLADA));
		stacks.add(new ItemStack(CONDUCTO_DE_ACERO));
		stacks.add(new ItemStack(CONDUCTO_DE_DAMASCO));
		stacks.add(new ItemStack(CANO_DE_COLADA));
		stacks.add(new ItemStack(CAJA_DE_MOLDEO));
		stacks.add(new ItemStack(CAJA_DE_MOLDEO_DE_ACERO));
		stacks.add(new ItemStack(CAJA_DE_MOLDEO_DE_DAMASCO));
		stacks.add(new ItemStack(CRISOL_DE_BARRO));
		stacks.add(new ItemStack(CRISOL_DE_HIERRO));
		stacks.add(new ItemStack(CRISOL_DE_OBSIDIANA));
		stacks.add(new ItemStack(MESA_DE_LOSA));
		stacks.add(new ItemStack(MESA_DE_BRASA));
		stacks.add(new ItemStack(MESA_DE_ALMAS));
		stacks.addAll(dev.forja.item.CastingFrameItem.all());
		for (dev.forja.item.Talisman talisman : dev.forja.item.Talisman.values()) {
			stacks.add(talisman.create());
		}
		for (dev.forja.forge.Perk perk : dev.forja.forge.Perk.values()) {
			stacks.add(dev.forja.item.SealItem.create(perk));
		}
		for (dev.forja.upgrade.Upgrade upgrade : dev.forja.upgrade.Upgrade.values()) {
			stacks.add(dev.forja.item.UpgradeOrbItem.create(upgrade, 100));
		}
		for (ForgeType type : ForgeType.values()) {
			for (ForgeMaterial head : List.of(ForgeMaterial.PIEDRA, ForgeMaterial.HIERRO, ForgeMaterial.DIAMANTE, ForgeMaterial.NETHERITA, ForgeMaterial.ESMERALDA)) {
				List<ForgeMaterial> materials = new ArrayList<>(Assembler.defaultMaterials(type));
				materials.set(0, head);
				stacks.add(Assembler.create(type, materials, registries));
			}
		}
		return stacks;
	}
}
