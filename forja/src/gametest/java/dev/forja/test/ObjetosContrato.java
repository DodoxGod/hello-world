package dev.forja.test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.forja.combat.AttackClassifier;
import dev.forja.combat.ArmorCalculator;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.SwingStyle;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeStats;
import dev.forja.forge.ForgeType;
import dev.forja.forge.SpecialAttacks;
import dev.forja.item.ForgedItems;
import dev.forja.magic.Spellcasting;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.ArmorSets;
import dev.forja.upgrade.Synergy;
import dev.forja.upgrade.Upgrade;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * The table of the mod's things for the simulator (docs/objetos_contrato.json), written from the mod itself the
 * way the network contract is (AiGameTests.writeContract), so the two can never drift apart.
 *
 * <p>Every forged item is built for real, with every material its head takes and every material its handle
 * takes (armour: every plate with every lining), and written down with all its components exactly as the game
 * stores them, plus what the mod works out from them at the moment of a blow: the stat sheet, the swing
 * style, the kind of damage and how much of the armour it goes through, a bow's draw, a crossbow's charge, a
 * spell's damage and wait, an armour piece's resistances and weight. Around that: the materials, the part
 * slots, the specials, the upgrades and the numbers they come to, the set bonuses, every monster's
 * attributes, the rule mobs are kitted out by, the legends, and the constants of combat, magic and the AI.
 *
 * <p>Run with {@code FORJA_OBJETOS=<absolute path> ./gradlew runGametest}; without it the test does nothing.
 */
public class ObjetosContrato {
	/** Components that are only looks, left out so the table is about what things do. */
	private static final Set<String> COSMETIC = Set.of("minecraft:custom_model_data", "minecraft:item_model", "minecraft:item_name",
		"minecraft:lore", "minecraft:tooltip_display", "minecraft:tooltip_style", "minecraft:break_sound",
		// what it is repaired with, and the parts again (they are "materiales")
		"minecraft:repairable", "forja:partes");

	/** The plates vanilla dresses monsters in, which ForjaMobs turns into forged ones of the same material. */
	private static final List<ForgeMaterial> VANILLA_PLATES = List.of(ForgeMaterial.CUERO, ForgeMaterial.COBRE, ForgeMaterial.ORO,
		ForgeMaterial.HIERRO, ForgeMaterial.DIAMANTE);

	/** Vanilla's monsters the AI drives, alongside every one of the mod's own. */
	private static final List<EntityType<?>> VANILLA_MOBS = List.of(EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.DROWNED,
		EntityTypes.ZOMBIE_VILLAGER, EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.WITHER_SKELETON,
		EntityTypes.CREEPER, EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER, EntityTypes.PILLAGER, EntityTypes.VINDICATOR,
		EntityTypes.EVOKER, EntityTypes.WITCH, EntityTypes.ENDERMAN, EntityTypes.BLAZE, EntityTypes.PIGLIN_BRUTE);

	@GameTest(maxTicks = 400)
	public void writeItemTable(GameTestHelper helper) throws Exception {
		String file = System.getenv("FORJA_OBJETOS");
		if (file == null || file.isBlank()) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		RegistryOps<JsonElement> ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		JsonObject out = new JsonObject();
		out.addProperty("formato", "objetos_contrato_v1");
		out.addProperty("generado", java.time.LocalDateTime.now().withNano(0).toString());
		out.addProperty("version_mod", FabricLoader.getInstance().getModContainer("forja")
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
		out.addProperty("como_leer", "Cada objeto forjado se construye de verdad (Assembler.create) con cada material que acepta su "
			+ "cabeza y cada uno que acepta su mango (las armaduras, cada placa con cada forro), el resto de piezas por defecto. "
			+ "'componentes' son los componentes del objeto tal como los guarda el juego (atributos, alcance, arma cinética...). "
			+ "'hoja' es ForgeStats.Sheet, lo que el mod calcula de los materiales. 'golpe' es AttackClassifier: tipo de daño y "
			+ "penetración (fracción de la armadura que se ignora). Las mejoras suben por fracción 0..1 (Upgrades.fraction), "
			+ "limitada por el Potencial de la pieza; 'funciones_de_mejora' da cada función a 0.25/0.5/0.75/1. Ticks = 1/20 s. "
			+ "Peso de armadura: 'perfil.weight' de cada pieza, sumado con ArmorCalculator.SLOT_SHARE; SOLO frena a jugadores: "
			+ "velocidad × (1 − peso × combate_config.maxArmorSlow); a los mobs no les cambia la velocidad. Las armaduras van "
			+ "con cada placa (forro por defecto), cada forro (placa por defecto) y todas las combinaciones con que nace un mob.");

		// ---------------------------------------------------------------- materials and parts
		JsonArray materials = new JsonArray();
		for (ForgeMaterial material : ForgeMaterial.values()) {
			JsonObject row = fields(material, false);
			row.addProperty("id", id(material));
			row.addProperty("basico", material.isBasic());
			JsonObject defense = new JsonObject();
			for (net.minecraft.world.item.equipment.ArmorType armor : net.minecraft.world.item.equipment.ArmorType.values()) {
				defense.addProperty(armor.getSerializedName(), material.defense(armor));
			}
			row.add("defensa_por_pieza", defense);
			row.add("perfil_combate", reflect(dev.forja.combat.MaterialCombat.forged(material, ForgeMaterial.CUERO), 0));
			materials.add(row);
		}
		out.add("materiales", materials);
		JsonArray parts = new JsonArray();
		for (PartType part : PartType.values()) {
			JsonObject row = fields(part, false);
			row.addProperty("id", part.id());
			JsonArray accepts = new JsonArray();
			for (ForgeMaterial material : ForgeMaterial.values()) {
				if (part.accepts(material)) {
					accepts.add(id(material));
				}
			}
			row.add("acepta", accepts);
			parts.add(row);
		}
		out.add("piezas", parts);
		JsonArray types = new JsonArray();
		for (ForgeType type : ForgeType.values()) {
			JsonObject row = fields(type, false);
			row.addProperty("id", type.id());
			JsonArray defaults = new JsonArray();
			Assembler.defaultMaterials(type).forEach(m -> defaults.add(id(m)));
			row.add("materiales_por_defecto", defaults);
			row.addProperty("especial", SpecialAttacks.has(type));
			row.addProperty("arma_de_leyenda_cuerpo_a_cuerpo", dev.forja.world.Legends.swung(type));
			types.add(row);
		}
		out.add("tipos", types);

		// ---------------------------------------------------------------- every forged thing, built
		Zombie holder = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		Zombie struck = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		struck.setPos(1.0, 0.0, 0.0);
		JsonArray items = new JsonArray();
		for (ForgeType type : ForgeType.values()) {
			for (List<ForgeMaterial> combo : combos(type)) {
				items.add(item(level, ops, type, combo, holder, struck));
			}
		}
		out.addProperty("objetos", "__OBJETOS__");

		// ---------------------------------------------------------------- what the mod does with them
		JsonArray specials = new JsonArray();
		for (ForgeType type : ForgeType.values()) {
			if (!SpecialAttacks.has(type)) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("tipo", type.id());
			switch (type) {
				case ESPADON -> special(row, "torbellino", SpecialAttacks.WHIRL_RANGE, SpecialAttacks.WHIRL_COOLDOWN, SpecialAttacks.WHIRL_DAMAGE,
					"agachado + clic derecho: golpea a todo lo que hay alrededor y lo empuja");
				case MARTILLO, MAZO -> special(row, "sismo", SpecialAttacks.QUAKE_RANGE, SpecialAttacks.QUAKE_COOLDOWN, SpecialAttacks.QUAKE_DAMAGE,
					"agachado + clic derecho en el suelo: golpe al suelo que lanza al aire lo que hay cerca");
				case GUADANA -> special(row, "siega", SpecialAttacks.REAP_RANGE, SpecialAttacks.REAP_COOLDOWN, SpecialAttacks.REAP_DAMAGE,
					"agachado + clic derecho: barrido amplio que arrastra hacia el jugador");
				case GUANTELETES -> special(row, "embestida", SpecialAttacks.CHARGE_PUSH, SpecialAttacks.CHARGE_COOLDOWN, SpecialAttacks.CHARGE_DAMAGE,
					"agachado + clic derecho: el jugador se lanza hacia delante (alcance = impulso) y golpea al primero");
				default -> row.addProperty("nombre", "?");
			}
			specials.add(row);
		}
		out.add("especiales", specials);
		JsonArray upgrades = new JsonArray();
		for (Upgrade upgrade : Upgrade.values()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", id(upgrade));
			row.addProperty("grupo", id(upgrade.group));
			row.addProperty("encantamiento", upgrade.enchantment == null ? null : upgrade.enchantment.identifier().toString());
			row.addProperty("nivel_max", upgrade.maxLevel);
			row.addProperty("pacto", upgrade.isPact());
			if (upgrade.isPact()) {
				net.minecraft.world.item.Item offering = dev.forja.upgrade.Pacts.offering(upgrade);
				row.addProperty("ofrenda", offering == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offering).toString());
			}
			JsonArray on = new JsonArray();
			for (ForgeType type : ForgeType.values()) {
				if (upgrade.appliesTo(type)) {
					on.add(type.id());
				}
			}
			row.add("se_aplica_a", on);
			upgrades.add(row);
		}
		out.add("mejoras", upgrades);
		JsonObject effects = new JsonObject();
		// by name: reflection hands methods over in no fixed order
		Method[] methods = Upgrade.class.getDeclaredMethods();
		java.util.Arrays.sort(methods, java.util.Comparator.comparing(Method::getName));
		for (Method method : methods) {
			if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()) && method.getParameterCount() == 1
				&& method.getParameterTypes()[0] == float.class && (method.getReturnType() == float.class || method.getReturnType() == int.class)) {
				JsonObject values = new JsonObject();
				for (float f : new float[] {0.25F, 0.5F, 0.75F, 1.0F}) {
					values.add(Float.toString(f), number(method.invoke(null, f)));
				}
				effects.add(method.getName(), values);
			}
		}
		out.add("funciones_de_mejora", effects);
		JsonArray synergies = new JsonArray();
		for (Synergy synergy : Synergy.values()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", id(synergy));
			row.addProperty("primera", id(synergy.first));
			row.addProperty("segunda", id(synergy.second));
			synergies.add(row);
		}
		out.add("sinergias", synergies);
		JsonObject rules = new JsonObject();
		rules.addProperty("sinergia_umbral", Synergy.THRESHOLD);
		rules.addProperty("sinergias_max_despiertas", Synergy.MOST);
		rules.addProperty("sinergias_orden", "suma de los dos porcentajes, mayor primero; empate por orden de la lista");
		rules.addProperty("pactos_max_por_objeto", dev.forja.upgrade.Pacts.MOST);
		rules.addProperty("pactos_desbloqueo", "una vez por jugador, ofreciendo 'ofrenda' en la estrella con los ingredientes");
		out.add("reglas_mejoras", rules);
		JsonObject sets = new JsonObject();
		sets.addProperty("armadura_extra", ArmorSets.ARMOR_BONUS);
		sets.addProperty("dureza_extra", ArmorSets.TOUGHNESS_BONUS);
		sets.addProperty("condicion", "las cuatro piezas con placa del mismo material");
		JsonObject bonus = new JsonObject();
		for (ForgeMaterial material : ForgeMaterial.values()) {
			JsonArray list = new JsonArray();
			for (ArmorSets.Bonus b : ArmorSets.bonuses(material)) {
				JsonObject one = new JsonObject();
				one.addProperty("atributo", b.attribute().getRegisteredName());
				one.addProperty("cantidad", b.amount());
				one.addProperty("operacion", b.operation().getSerializedName());
				list.add(one);
			}
			bonus.add(id(material), list);
		}
		sets.add("por_material", bonus);
		out.add("conjuntos", sets);
		// Carried, not worn: the first one in the pack works (item/Talisman).
		JsonObject talismans = constants(dev.forja.item.Talisman.class);
		JsonArray gems = new JsonArray();
		for (dev.forja.item.Talisman talisman : dev.forja.item.Talisman.values()) {
			gems.add(id(talisman));
		}
		talismans.add("gemas", gems);
		out.add("talismanes", talismans);
		JsonArray perks = new JsonArray();
		for (dev.forja.forge.Perk perk : dev.forja.forge.Perk.values()) {
			JsonObject row = fields(perk, false);
			row.addProperty("id", id(perk));
			perks.add(row);
		}
		out.add("grabados", perks);

		// ---------------------------------------------------------------- the monsters
		JsonObject mobs = new JsonObject();
		JsonObject kit = constants(dev.forja.world.ForjaMobs.class);
		JsonObject chance = new JsonObject();
		for (int worn = 0; worn <= 4; worn++) {
			chance.addProperty(Integer.toString(worn), dev.forja.world.ForjaMobs.weaponChance(worn));
		}
		kit.add("prob_arma_segun_piezas", chance);
		kit.addProperty("regla", "Mobs propios de Forja (ForjaMobs.arm): cada pieza de armadura al 15 % por separado; cada pieza puesta "
			+ "resta 15 % a la probabilidad de llevar arma (sin armadura siempre arma; con las cuatro, 40 %); solo armas de mano "
			+ "(HAND_WEAPONS). Vanilla (forgeEquipment): lo que vanilla les da se cambia por forjado con probabilidad CHANCE; "
			+ "los esqueletos llevan arco (a veces forjado) y los saqueadores ballesta; baculos/grimorios de ForjaConfig "
			+ "para esqueletos/zombis; elites (ForjaConfig.elites) con un arma de leyenda que pueden usar.");
		mobs.add("equipo", kit);
		// The defaults as the code has them, not the config the running tests have turned down (veterans, elites,
		// shields, staffs and tomes are all set to nothing by tests that must not meet them by chance).
		dev.forja.ForjaConfig defaults = new dev.forja.ForjaConfig();
		mobs.add("config", fields(defaults, false));
		mobs.add("elites", constants(dev.forja.world.Elites.class));
		JsonArray legends = new JsonArray();
		for (dev.forja.world.Legends.Legend legend : dev.forja.world.Legends.ALL) {
			legends.add(reflect(legend, 0));
		}
		mobs.add("leyendas", legends);
		JsonArray beings = new JsonArray();
		List<EntityType<?>> all = new ArrayList<>(VANILLA_MOBS);
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (dev.forja.Forja.MOD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace())) {
				all.add(type);
			}
		}
		for (EntityType<?> type : all) {
			if (!(type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob)) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
			row.addProperty("ancho", mob.getBbWidth());
			row.addProperty("alto", mob.getBbHeight());
			row.addProperty("familia", dev.forja.ai.MobAi.familyOf(mob));
			row.addProperty("familia_vanilla", dev.forja.ai.MobFamily.of(mob).file);
			row.addProperty("avisa_su_golpe", dev.forja.combat.AttackTokens.warns(mob));
			JsonObject attributes = new JsonObject();
			for (Holder<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.asHolderIdMap()) {
				if (mob.getAttributes().hasAttribute(attribute)) {
					attributes.addProperty(attribute.getRegisteredName(), mob.getAttributeValue(attribute));
				}
			}
			row.add("atributos", attributes);
			// Its special moves as its AI runs them (ai/Movesets): warning, cooldown, how often the rules start
			// it, and whatever numbers the move itself carries (reach, speed, radius...).
			JsonArray moves = new JsonArray();
			try {
				for (dev.forja.ai.Special special : dev.forja.ai.Movesets.of(mob)) {
					JsonObject move = fields(special, true);
					move.addProperty("clase", special.getClass().getSimpleName());
					moves.add(move);
				}
			} catch (RuntimeException problem) {
				row.addProperty("especiales_error", String.valueOf(problem));
			}
			row.add("especiales", moves);
			beings.add(row);
		}
		mobs.add("tipos", beings);
		out.add("mobs", mobs);

		// ---------------------------------------------------------------- constants
		JsonObject constants = new JsonObject();
		constants.add("combate_config", fields(new dev.forja.ForjaConfig().combate, false));
		for (Class<?> c : new Class<?>[] {Spellcasting.class, dev.forja.entity.ai.CasterGoal.class, dev.forja.entity.MagicBolt.class,
			SpecialAttacks.class, Assembler.class, ArmorSets.class, ArmorCalculator.class, dev.forja.combat.MaterialCombat.class,
			dev.forja.combat.Stamina.class, dev.forja.combat.Posture.class, dev.forja.combat.AttackTokens.class, dev.forja.combat.Combos.class,
			dev.forja.combat.ChargedStrike.class, dev.forja.combat.ParryRhythm.class, dev.forja.combat.WeaponGuard.class,
			dev.forja.ai.Squad.class, dev.forja.ai.TacticGoal.class, dev.forja.ai.RuleBrain.class, dev.forja.ai.Aggression.class,
			dev.forja.ai.MobDefense.class, dev.forja.ai.ObsM1.class, dev.forja.forge.Potential.class, dev.forja.forge.Mastery.class,
			dev.forja.forge.Quality.class, dev.forja.combat.Weight.class, BowItem.class, CrossbowItem.class}) {
			constants.add(c.getSimpleName(), constants(c));
		}
		JsonObject vanilla = new JsonObject();
		var arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.EVENT);
		vanilla.addProperty("flecha_gravedad", arrow == null ? 0.05 : arrow.getGravity());
		vanilla.addProperty("flecha_rozamiento_aire", 0.99);
		vanilla.addProperty("flecha_rozamiento_agua", 0.6);
		vanilla.addProperty("esqueleto_velocidad_disparo", 1.6);
		vanilla.addProperty("esqueleto_imprecision", "14 - 4 * dificultad (0 pacifico .. 3 dificil)");
		vanilla.addProperty("esqueleto_intervalo_disparo", "20 ticks en dificil, 40 en el resto (RangedBowAttackGoal)");
		vanilla.addProperty("arco_tensado_completo", BowItem.MAX_DRAW_DURATION);
		vanilla.addProperty("arco_potencia", "BowItem.getPowerForTime(t): t/20, (t^2 + 2t)/3, tope 1; velocidad = 3 * potencia");
		constants.add("vanilla_26_2", vanilla);
		out.add("constantes", constants);

		Path path = Path.of(file);
		Files.createDirectories(path.toAbsolutePath().getParent());
		// Pretty, but the items one to a line: there are some two thousand of them.
		var compact = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
		StringBuilder rows = new StringBuilder("[\n");
		for (int i = 0; i < items.size(); i++) {
			rows.append("    ").append(compact.toJson(items.get(i))).append(i + 1 < items.size() ? ",\n" : "\n");
		}
		rows.append("  ]");
		String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create().toJson(out)
			.replace("\"__OBJETOS__\"", rows.toString());
		Files.writeString(path, text, StandardCharsets.UTF_8);
		helper.succeed();
	}

	/** The head in every material it takes and each handle in every material it takes; armour, plate by lining. */
	private static Set<List<ForgeMaterial>> combos(ForgeType type) {
		List<ForgeMaterial> defaults = Assembler.defaultMaterials(type);
		Set<List<ForgeMaterial>> combos = new LinkedHashSet<>();
		combos.add(defaults);
		if (type.slots.isEmpty()) {
			return combos;
		}
		if (type.kind == ForgeType.Kind.ARMOR && type.slots.size() >= 2) {
			// Every plate on the default lining and every lining under the default plate: plate by lining is six
			// thousand rows, and the lining only moves durability, weight and the resistances a little. What a
			// monster can actually be born wearing is here whole: vanilla's plates by ForjaMobs' linings.
			List<ForgeMaterial> linings = mobLinings();
			for (ForgeMaterial material : ForgeMaterial.values()) {
				for (int slot = 0; slot < 2; slot++) {
					if (type.slots.get(slot).accepts(material)) {
						List<ForgeMaterial> combo = new ArrayList<>(defaults);
						combo.set(slot, material);
						combos.add(combo);
					}
				}
			}
			for (ForgeMaterial plate : VANILLA_PLATES) {
				for (ForgeMaterial lining : linings) {
					if (type.slots.get(0).accepts(plate) && type.slots.get(1).accepts(lining)) {
						List<ForgeMaterial> combo = new ArrayList<>(defaults);
						combo.set(0, plate);
						combo.set(1, lining);
						combos.add(combo);
					}
				}
			}
			return combos;
		}
		for (int slot = 0; slot < type.slots.size(); slot++) {
			if (slot != 0 && type.slots.get(slot).role != PartType.Role.HANDLE) {
				continue;
			}
			for (ForgeMaterial material : ForgeMaterial.values()) {
				if (type.slots.get(slot).accepts(material)) {
					List<ForgeMaterial> combo = new ArrayList<>(defaults);
					combo.set(slot, material);
					combos.add(combo);
				}
			}
		}
		return combos;
	}

	/** ForjaMobs' linings, read off the class so the table follows it. */
	private static List<ForgeMaterial> mobLinings() {
		try {
			Field field = dev.forja.world.ForjaMobs.class.getDeclaredField("LININGS");
			field.setAccessible(true);
			return new ArrayList<>(new LinkedHashSet<>(List.of((ForgeMaterial[]) field.get(null))));
		} catch (ReflectiveOperationException problem) {
			return List.of(ForgeMaterial.CUERO);
		}
	}

	private static JsonObject item(ServerLevel level, RegistryOps<JsonElement> ops, ForgeType type, List<ForgeMaterial> combo, Zombie holder, Zombie struck) {
		JsonObject row = new JsonObject();
		row.addProperty("tipo", type.id());
		row.addProperty("clase", id(type.kind));
		JsonArray names = new JsonArray();
		combo.forEach(m -> names.add(id(m)));
		row.add("materiales", names);
		ItemStack stack;
		try {
			stack = Assembler.create(type, combo, level.registryAccess());
		} catch (RuntimeException problem) {
			row.addProperty("error", String.valueOf(problem));
			return row;
		}
		row.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		// sorted by name: the stack's own order is a hash map's, and would change the file on every run
		java.util.TreeMap<String, JsonElement> sorted = new java.util.TreeMap<>();
		for (TypedDataComponent<?> component : stack.getComponents()) {
			DataComponentType<?> kind = component.type();
			Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(kind);
			if (key == null || COSMETIC.contains(key.toString()) || kind.codec() == null) {
				continue;
			}
			component.encodeValue(ops).result().ifPresent(value -> sorted.put(key.toString(), canonical(trim(key.toString(), value))));
		}
		JsonObject components = new JsonObject();
		sorted.forEach(components::add);
		row.add("componentes", components);
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts != null) {
			row.add("hoja", nonZero(reflect(ForgeStats.sheet(stack, parts), 0)));
		}
		switch (type.kind) {
			case WEAPON, TOOL, SHIELD -> {
				row.addProperty("estilo", SwingStyle.of(stack).name().toLowerCase(Locale.ROOT));
				row.addProperty("carga_golpe", dev.forja.combat.ChargedStrike.charges(stack));
				holder.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
				try {
					row.add("golpe", reflect(AttackClassifier.classify(level.damageSources().mobAttack(holder), struck), 0));
				} catch (RuntimeException problem) {
					row.addProperty("golpe_error", String.valueOf(problem));
				}
			}
			case ARMOR -> {
				row.add("perfil", reflect(ArmorCalculator.profileOf(stack), 0));
			}
			case RANGED -> {
				row.addProperty("tensado_velocidad", ForgedItems.ForgedBowItem.drawSpeed(stack));
				row.addProperty("flecha_dano_extra", ForgedItems.ForgedBowItem.arrowDamageBonus(stack));
				if (stack.getItem() instanceof CrossbowItem) {
					row.addProperty("ballesta_carga_ticks", CrossbowItem.getChargeDuration(stack, holder));
				}
			}
			default -> {
			}
		}
		if (parts != null && Spellcasting.casts(type)) {
			ForgeMaterial core = Spellcasting.core(parts);
			JsonObject magic = new JsonObject();
			magic.addProperty("nucleo", id(core));
			magic.addProperty("espera_ticks", Spellcasting.cooldown(stack, type));
			if (type == ForgeType.BACULO) {
				magic.addProperty("dano_proyectil", Spellcasting.boltDamage(core));
			} else {
				magic.addProperty("dano_area", Spellcasting.areaDamage(core));
				magic.addProperty("runa_ticks", Spellcasting.runeTicks(stack));
			}
			row.add("magia", magic);
		}
		return row;
	}

	/** What of a component is about fighting: where it is worn, not how it looks or sounds; how fast it digs, not what. */
	private static JsonElement trim(String key, JsonElement value) {
		if (!(value instanceof JsonObject object)) {
			return value;
		}
		if (key.equals("minecraft:equippable")) {
			JsonObject kept = new JsonObject();
			if (object.has("slot")) {
				kept.add("slot", object.get("slot"));
			}
			return kept;
		}
		if (key.equals("minecraft:tool")) {
			JsonObject kept = object.deepCopy();
			kept.remove("rules");
			return kept;
		}
		return value;
	}

	/** The same value with every object's keys in order, all the way down, so the file only changes when a value does. */
	private static JsonElement canonical(JsonElement value) {
		if (value instanceof JsonObject object) {
			JsonObject out = new JsonObject();
			new java.util.TreeMap<>(object.asMap()).forEach((k, v) -> out.add(k, canonical(v)));
			return out;
		}
		if (value instanceof JsonArray array) {
			JsonArray out = new JsonArray();
			array.forEach(v -> out.add(canonical(v)));
			return out;
		}
		return value;
	}

	/** A stat sheet with only what this kind of thing has: the other kinds' numbers are all nought. */
	private static JsonElement nonZero(JsonElement sheet) {
		if (!(sheet instanceof JsonObject object)) {
			return sheet;
		}
		JsonObject kept = new JsonObject();
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			JsonElement v = entry.getValue();
			boolean zero = v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() && v.getAsDouble() == 0.0
				|| v.isJsonArray() && v.getAsJsonArray().asList().stream().allMatch(e -> e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() && e.getAsDouble() == 0.0);
			if (!zero) {
				kept.add(entry.getKey(), v);
			}
		}
		return kept;
	}

	private static void special(JsonObject row, String name, double range, int cooldown, float damage, String how) {
		row.addProperty("nombre", name);
		row.addProperty("radio", range);
		row.addProperty("recarga_ticks", cooldown);
		row.addProperty("dano_frac_del_golpe", damage);
		row.addProperty("como", how);
	}

	// ---------------------------------------------------------------- reflection, so nothing is copied by hand

	/** Every static final number, text, flag, enum or array of them a class declares, private ones too. */
	private static JsonObject constants(Class<?> c) {
		JsonObject out = new JsonObject();
		Field[] declared = c.getDeclaredFields();
		java.util.Arrays.sort(declared, java.util.Comparator.comparing(Field::getName));
		for (Field field : declared) {
			int mods = field.getModifiers();
			if (!Modifier.isStatic(mods) || !Modifier.isFinal(mods) || field.isSynthetic()) {
				continue;
			}
			Class<?> t = field.getType();
			boolean plain = t.isPrimitive() || t == String.class || Number.class.isAssignableFrom(t) || t.isEnum()
				|| t.isArray() && (t.getComponentType().isPrimitive() || t.getComponentType().isEnum() || t.getComponentType() == String.class);
			if (!plain) {
				continue;
			}
			try {
				field.setAccessible(true);
				out.add(field.getName(), reflect(field.get(null), 0));
			} catch (ReflectiveOperationException | RuntimeException problem) {
				out.addProperty(field.getName(), "?" + problem.getClass().getSimpleName());
			}
		}
		return out;
	}

	/** An object's own fields (public only, or all), each written plainly. */
	private static JsonObject fields(Object value, boolean all) {
		JsonObject out = new JsonObject();
		for (Field field : value.getClass().getFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			try {
				out.add(field.getName(), reflect(field.get(value), 1));
			} catch (ReflectiveOperationException | RuntimeException problem) {
				out.addProperty(field.getName(), "?");
			}
		}
		if (all) {
			for (Field field : value.getClass().getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) && !out.has(field.getName())) {
					try {
						field.setAccessible(true);
						out.add(field.getName(), reflect(field.get(value), 1));
					} catch (ReflectiveOperationException | RuntimeException problem) {
						out.addProperty(field.getName(), "?");
					}
				}
			}
		}
		return out;
	}

	private static JsonElement reflect(Object value, int depth) {
		if (value == null) {
			return JsonNull.INSTANCE;
		}
		if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
			return number(value);
		}
		if (value instanceof String s) {
			return new JsonPrimitive(s);
		}
		if (value instanceof Enum<?> e) {
			return new JsonPrimitive(id(e));
		}
		if (value instanceof Holder<?> h) {
			return new JsonPrimitive(h.getRegisteredName());
		}
		if (value.getClass().isArray()) {
			JsonArray a = new JsonArray();
			for (int i = 0; i < Array.getLength(value); i++) {
				a.add(reflect(Array.get(value, i), depth + 1));
			}
			return a;
		}
		if (value instanceof Collection<?> list) {
			JsonArray a = new JsonArray();
			for (Object o : list) {
				a.add(reflect(o, depth + 1));
			}
			return a;
		}
		if (value instanceof Map<?, ?> map) {
			JsonObject o = new JsonObject();
			map.forEach((k, v) -> o.add(k instanceof Enum<?> e ? id(e) : String.valueOf(k), reflect(v, depth + 1)));
			return o;
		}
		if (depth > 4) {
			return new JsonPrimitive(String.valueOf(value));
		}
		if (value instanceof Record record) {
			JsonObject o = new JsonObject();
			for (RecordComponent component : record.getClass().getRecordComponents()) {
				try {
					Method accessor = component.getAccessor();
					accessor.setAccessible(true);
					o.add(component.getName(), reflect(accessor.invoke(record), depth + 1));
				} catch (ReflectiveOperationException | RuntimeException problem) {
					o.addProperty(component.getName(), "?");
				}
			}
			return o;
		}
		if (value.getClass().getName().startsWith("dev.forja")) {
			return fields(value, false);
		}
		return new JsonPrimitive(String.valueOf(value));
	}

	private static JsonElement number(Object value) {
		if (value instanceof Float f) {
			// through its own text, so 0.1F is 0.1 and not 0.10000000149011612
			return f.isNaN() || f.isInfinite() ? new JsonPrimitive(f.toString()) : new JsonPrimitive(new java.math.BigDecimal(Float.toString(f)));
		}
		if (value instanceof Double d && (d.isNaN() || d.isInfinite())) {
			return new JsonPrimitive(d.toString());
		}
		if (value instanceof Number n) {
			return new JsonPrimitive(n);
		}
		if (value instanceof Boolean b) {
			return new JsonPrimitive(b);
		}
		return new JsonPrimitive(String.valueOf(value));
	}

	private static String id(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}
}
