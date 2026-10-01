package dev.forja.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.forja.ai.MobDefense;
import dev.forja.block.entity.CastingBoxBlockEntity;
import dev.forja.block.entity.CastingTableBlockEntity;
import dev.forja.combat.ChargePayload;
import dev.forja.combat.ChargedStrike;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.Grip;
import dev.forja.combat.Posture;
import dev.forja.combat.Stamina;
import dev.forja.combat.Weight;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeStats;
import dev.forja.forge.ForgeType;
import dev.forja.item.CastingMouldItem;
import dev.forja.item.TemplateItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.part.PartVariant;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Heavy and light handles and bindings (combat/Grip, Andy 2026-09-29): a choice, not an upgrade. Each variant
 * assembles where the plain part goes, its weight moves the swing, and the charged blow, the posture, the
 * stamina, the push and the durability move as designed; items from before the variants load as plain ones.
 */
public class MangosGameTests {
	private static final ForgeMaterial IRON = ForgeMaterial.HIERRO;

	private static ItemStack forged(ForgeType type, List<ForgeMaterial> materials, PartVariant handle, PartVariant binding) {
		List<PartVariant> variants = new ArrayList<>();
		for (PartType slot : type.slots) {
			variants.add(slot == PartType.MANGO ? handle : slot == PartType.ATADURA ? binding : PartVariant.NORMAL);
		}
		return Assembler.create(new ForgedParts(type, materials, variants));
	}

	/** A pickaxe, all iron, with the handle and binding in the variants asked for. */
	private static ItemStack pick(PartVariant handle, PartVariant binding) {
		return forged(ForgeType.PICO, List.of(IRON, handle == PartVariant.LIGERO ? ForgeMaterial.MADERA : IRON,
			binding == PartVariant.LIGERO ? ForgeMaterial.CUERO : IRON), handle, binding);
	}

	/** A player that has not swung for a long time, holding this with its attributes on. */
	private static CombatGameTests.TestPlayer holding(GameTestHelper helper, BlockPos at, ItemStack stack) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, at);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			var instance = player.getAttribute(attribute);
			if (instance != null && !instance.hasModifier(modifier.id())) {
				instance.addTransientModifier(modifier);
			}
		});
		rested(player);
		return player;
	}

	private static void rested(Player player) {
		try {
			Field ticker = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			ticker.setAccessible(true);
			ticker.setInt(player, 1000);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("attackStrengthTicker", failure);
		}
	}

	private static Zombie target(GameTestHelper helper, BlockPos pos) {
		CombatGameTests.noRandomThreat();
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, pos);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		zombie.setNoAi(true);
		return zombie;
	}

	private static void face(Player player, LivingEntity target) {
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
	}

	// ---------------------------------------------------------------- making them

	/** Each variant is a part of its own that goes where the plain one goes, and the piece remembers it. */
	@GameTest
	public void eachVariantAssembles(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		for (PartVariant handle : PartVariant.values()) {
			for (PartVariant binding : PartVariant.values()) {
				PartType handlePart = PartType.of(PartType.MANGO, handle);
				PartType bindingPart = PartType.of(PartType.ATADURA, binding);
				ForgeMaterial handleMaterial = handle == PartVariant.LIGERO ? ForgeMaterial.HUESO : IRON;
				ForgeMaterial bindingMaterial = binding == PartVariant.LIGERO ? ForgeMaterial.CUERO : IRON;
				List<ItemStack> inputs = List.of(Assembler.createPart(bindingPart, bindingMaterial), Assembler.createPart(PartType.CABEZA_HACHA, IRON),
					Assembler.createPart(handlePart, handleMaterial));
				ItemStack axe = Assembler.evaluate(inputs, registries).stack();
				ForgedParts parts = axe.get(ModComponents.PARTS);
				helper.assertTrue(parts != null && parts.type() == ForgeType.HACHA, "sale un hacha con " + handlePart + " y " + bindingPart);
				helper.assertTrue(Grip.handle(axe) == handle && Grip.binding(axe) == binding, "y recuerda sus piezas: " + parts);
				helper.assertTrue(parts.materials().equals(List.of(IRON, handleMaterial, bindingMaterial)), "con sus materiales en su sitio");
				helper.assertTrue(parts.hasVariants() == (handle != PartVariant.NORMAL || binding != PartVariant.NORMAL),
					"sólo guarda variantes si hay alguna");
				// Taken apart, a heavy handle comes back as a heavy handle.
				var returned = Assembler.disassemble(axe).returned();
				helper.assertTrue(returned.stream().anyMatch(stack -> stack.getItem() == ModItems.part(handlePart)),
					"al desarmar vuelve el " + handlePart);
			}
		}
		// Swapped on the star: a plain pickaxe given a light handle becomes a light-handled one, and back.
		ItemStack plain = pick(PartVariant.NORMAL, PartVariant.NORMAL);
		ItemStack swapped = Assembler.evaluate(List.of(plain, Assembler.createPart(PartType.MANGO_LIGERO, ForgeMaterial.MADERA)), registries).stack();
		helper.assertTrue(Grip.handle(swapped) == PartVariant.LIGERO, "cambiar el mango por uno ligero lo aligera");
		ItemStack back = Assembler.evaluate(List.of(swapped, Assembler.createPart(PartType.MANGO, ForgeMaterial.MADERA)), registries).stack();
		helper.assertTrue(!back.get(ModComponents.PARTS).hasVariants(), "y uno normal lo deja normal");
		// A variant on a slot without variants is dropped; a sword has a handle, and takes a heavy one.
		ItemStack sword = forged(ForgeType.ESPADA, List.of(IRON, IRON, IRON), PartVariant.PESADO, PartVariant.PESADO);
		helper.assertTrue(Grip.handle(sword) == PartVariant.PESADO && Grip.binding(sword) == PartVariant.NORMAL, "la espada: mango pesado, sin atadura");
		// The item model reads the slot's string.
		var model = pick(PartVariant.PESADO, PartVariant.LIGERO).get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
		helper.assertTrue(model != null && model.strings().equals(List.of("", "pesado", "ligero")), "el modelo sabe qué dibujar: " + model);
		helper.succeed();
	}

	/**
	 * Every material a plain handle or binding takes makes every variant (Andy, 2026-09-30), each the way its
	 * material is worked: what is cut is cut at the bench in any shape, and metal is poured through the mould the
	 * variant's template becomes in the casting box.
	 */
	@GameTest
	public void variantsAreMadeTheModsWay(GameTestHelper helper) {
		for (PartType variant : List.of(PartType.MANGO_PESADO, PartType.MANGO_LIGERO, PartType.ATADURA_PESADA, PartType.ATADURA_LIGERA)) {
			helper.assertTrue(variant.cuttable(), variant + " se corta en la mesa");
			ItemStack template = new ItemStack(ModItems.PLANTILLA);
			TemplateItem.engrave(template, variant);
			helper.assertTrue(CastingBoxBlockEntity.mouldTemplate(template) == variant, "su plantilla da su molde en la caja de colada");
			helper.assertTrue(CastingBoxBlockEntity.allowed(CastingBoxBlockEntity.SLOT_PATTERN, template), "y la caja la acepta");
			ItemStack mould = CastingMouldItem.of(variant);
			for (ForgeMaterial material : ForgeMaterial.values()) {
				helper.assertTrue(variant.accepts(material) == variant.base().accepts(material),
					variant + " acepta lo mismo que " + variant.base() + ": " + material);
				if (!variant.accepts(material)) {
					continue;
				}
				if (material.isBasic()) {
					ItemStack input = material.displayStack().copyWithCount(variant.cost);
					ItemStack cut = Assembler.partResult(variant, input);
					helper.assertTrue(!cut.isEmpty() && cut.get(ModComponents.MATERIAL) == material, variant + " de " + material + " se corta");
				} else {
					helper.assertTrue(Assembler.partResult(variant, material.displayStack().copyWithCount(variant.cost)).isEmpty(),
						variant + " de " + material + " no se corta: se cuela");
					helper.assertTrue(CastingTableBlockEntity.castable(mould, material), variant + " se cuela en " + material);
				}
			}
		}
		helper.assertTrue(!Assembler.partResult(PartType.MANGO_LIGERO, new ItemStack(Items.BAMBOO_PLANKS, 4)).isEmpty(), "un mango ligero de bambú se corta");
		helper.assertTrue(!Assembler.partResult(PartType.MANGO_PESADO, new ItemStack(Items.OAK_PLANKS, 4)).isEmpty(), "y uno pesado de roble también");
		ItemStack plainTemplate = new ItemStack(ModItems.PLANTILLA);
		TemplateItem.engrave(plainTemplate, PartType.MANGO);
		helper.assertTrue(CastingBoxBlockEntity.mouldTemplate(plainTemplate) == null, "la plantilla de un mango normal se queda en la mesa");
		helper.assertTrue(ForgeType.match(List.of(PartType.CABEZA_PICO, PartType.MANGO_PESADO, PartType.ATADURA_LIGERA)) == ForgeType.PICO,
			"la estrella reconoce el pico");
		helper.succeed();
	}

	/**
	 * A light handle of netherite (poured) and a heavy one of oak (cut): both are made, go into a piece, keep
	 * their material and shape, and come back out of it as themselves.
	 */
	@GameTest
	public void lightNetheriteAndHeavyOak(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		ItemStack oak = Assembler.partResult(PartType.MANGO_PESADO, new ItemStack(Items.OAK_PLANKS, 4));
		helper.assertTrue(oak.getItem() == ModItems.part(PartType.MANGO_PESADO) && oak.get(ModComponents.MATERIAL) == ForgeMaterial.MADERA,
			"el mango pesado de roble sale de la mesa: " + oak);
		// The wooden one is also a pattern: in the casting box it would become a heavy mould like the template.
		helper.assertTrue(CastingBoxBlockEntity.allowed(CastingBoxBlockEntity.SLOT_PATTERN, oak), "y sirve de modelo en la caja de colada");
		helper.assertTrue(Assembler.partResult(PartType.MANGO_LIGERO, new ItemStack(Items.NETHERITE_INGOT, 4)).isEmpty(), "la netherita no se talla");
		ItemStack mould = CastingMouldItem.of(PartType.MANGO_LIGERO);
		helper.assertTrue(CastingTableBlockEntity.castable(mould, ForgeMaterial.NETHERITA), "el molde del mango ligero se llena de netherita");
		ItemStack netherite = CastingTableBlockEntity.castPart(helper.getLevel(), PartType.MANGO_LIGERO, ForgeMaterial.NETHERITA, false);
		helper.assertTrue(netherite.getItem() == ModItems.part(PartType.MANGO_LIGERO) && netherite.get(ModComponents.MATERIAL) == ForgeMaterial.NETHERITA
			&& netherite.getOrDefault(ModComponents.COLADA, false), "sale el mango ligero de netherita, colado");

		ItemStack pick = Assembler.evaluate(List.of(Assembler.createPart(PartType.CABEZA_PICO, IRON), netherite,
			Assembler.createPart(PartType.ATADURA, ForgeMaterial.CUERO)), registries).stack();
		ForgedParts pickParts = pick.get(ModComponents.PARTS);
		helper.assertTrue(pickParts != null && pickParts.type() == ForgeType.PICO && Grip.handle(pick) == PartVariant.LIGERO
			&& pickParts.material(1) == ForgeMaterial.NETHERITA, "un pico con mango ligero de netherita: " + pickParts);
		helper.assertTrue((pick.getOrDefault(ModComponents.COLADAS, 0) & 2) != 0, "y su mango cuenta como colado para el potencial");
		ItemStack sword = Assembler.evaluate(List.of(Assembler.createPart(PartType.HOJA, IRON), oak, Assembler.createPart(PartType.GUARDA, IRON)),
			registries).stack();
		ForgedParts swordParts = sword.get(ModComponents.PARTS);
		helper.assertTrue(swordParts != null && swordParts.type() == ForgeType.ESPADA && Grip.handle(sword) == PartVariant.PESADO
			&& swordParts.materials().equals(List.of(IRON, ForgeMaterial.MADERA, IRON)), "una espada con mango pesado de roble: " + swordParts);

		var pickBack = Assembler.disassemble(pick).returned();
		helper.assertTrue(pickBack.stream().anyMatch(stack -> stack.getItem() == ModItems.part(PartType.MANGO_LIGERO)
			&& stack.get(ModComponents.MATERIAL) == ForgeMaterial.NETHERITA && stack.getOrDefault(ModComponents.COLADA, false)),
			"al desarmar el pico vuelve el mango ligero de netherita, colado: " + pickBack);
		var swordBack = Assembler.disassemble(sword).returned();
		helper.assertTrue(swordBack.stream().anyMatch(stack -> stack.getItem() == ModItems.part(PartType.MANGO_PESADO)
			&& stack.get(ModComponents.MATERIAL) == ForgeMaterial.MADERA), "y de la espada el mango pesado de roble: " + swordBack);
		helper.succeed();
	}

	/**
	 * A part weighs its material's density times its shape: a heavy oak handle adds less than a heavy iron one,
	 * a light netherite one takes off more than a light oak one and still weighs more than it.
	 */
	@GameTest
	public void weightFollowsMaterialTimesShape(GameTestHelper helper) {
		for (ForgeType type : List.of(ForgeType.ESPADA, ForgeType.HACHA, ForgeType.MAZO, ForgeType.DAGA)) {
			java.util.Map<ForgeMaterial, float[]> kg = new java.util.EnumMap<>(ForgeMaterial.class);
			for (ForgeMaterial material : List.of(ForgeMaterial.MADERA, IRON, ForgeMaterial.NETHERITA, ForgeMaterial.VIDRIACERO)) {
				List<ForgeMaterial> materials = new ArrayList<>(Assembler.defaultMaterials(type));
				materials.set(type.slots.indexOf(PartType.MANGO), material);
				float[] three = new float[3];
				for (PartVariant variant : PartVariant.values()) {
					three[variant.ordinal()] = Weight.kg(forged(type, materials, variant, PartVariant.NORMAL).get(ModComponents.PARTS));
				}
				kg.put(material, three);
				helper.assertTrue(three[1] > three[0] && three[2] < three[0], type + " " + material + ": pesado > normal > ligero " + java.util.Arrays.toString(three));
				// The shape multiplies the part's own weight, so what it adds or takes is in proportion to the density.
				float heavyRatio = (three[1] - three[0]) / (Grip.HEAVY_HANDLE_SHAPE - 1.0F);
				float lightRatio = (three[0] - three[2]) / (1.0F - Grip.LIGHT_HANDLE_SHAPE);
				helper.assertTrue(Math.abs(heavyRatio - lightRatio) < 1.0E-3F, type + " " + material + ": las dos formas mueven la misma masa de mango");
			}
			float ironStep = kg.get(IRON)[1] - kg.get(IRON)[0];
			for (ForgeMaterial material : List.of(ForgeMaterial.MADERA, ForgeMaterial.NETHERITA, ForgeMaterial.VIDRIACERO)) {
				float step = kg.get(material)[1] - kg.get(material)[0];
				float expected = Weight.density(material) / Weight.density(IRON);
				helper.assertTrue(Math.abs(step / ironStep - expected) < 0.01F,
					type + ": el contrapeso de " + material + " pesa su densidad: " + step / ironStep + " frente a " + expected);
			}
			helper.assertTrue(kg.get(ForgeMaterial.MADERA)[1] - kg.get(ForgeMaterial.MADERA)[0] < ironStep, type + ": el pesado de roble es un contrapeso modesto");
			helper.assertTrue(kg.get(ForgeMaterial.NETHERITA)[2] > kg.get(ForgeMaterial.MADERA)[2], type + ": el ligero de netherita sigue pesando más que el de roble");
			helper.assertTrue(kg.get(ForgeMaterial.NETHERITA)[0] - kg.get(ForgeMaterial.NETHERITA)[2] > kg.get(ForgeMaterial.MADERA)[0] - kg.get(ForgeMaterial.MADERA)[2],
				type + ": aunque aligerarlo le quita más");
		}
		// The loose part says the same: density times shape.
		helper.assertTrue(Math.abs(Grip.partWeight(PartType.MANGO_PESADO, ForgeMaterial.MADERA) - Weight.density(ForgeMaterial.MADERA) * Grip.HEAVY_HANDLE_SHAPE) < 1.0E-5F,
			"el peso de la pieza suelta");
		helper.assertTrue(Grip.looseSwing(PartType.MANGO_PESADO, ForgeMaterial.NETHERITA) < Grip.looseSwing(PartType.MANGO_PESADO, ForgeMaterial.MADERA)
			&& Grip.looseSwing(PartType.MANGO_PESADO, ForgeMaterial.MADERA) < 1.0F && Grip.looseSwing(PartType.MANGO_LIGERO, ForgeMaterial.MADERA) > 1.0F,
			"y la velocidad que cuesta o da depende del material");
		helper.succeed();
	}

	/**
	 * Whatever a plain handle or binding of a material gives, the variant of it gives too: its stats on the
	 * part, its trait (and the enchantment the trait hides), its durability; the shape's trade goes on top.
	 */
	@GameTest
	public void traitsAndStatsCarryOver(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		for (ForgeMaterial material : List.of(ForgeMaterial.VARA_DE_BLAZE, ForgeMaterial.ESMERALDA, ForgeMaterial.NETHERITA, ForgeMaterial.HUESO)) {
			for (PartType variant : List.of(PartType.MANGO_PESADO, PartType.MANGO_LIGERO, PartType.ATADURA_PESADA, PartType.ATADURA_LIGERA)) {
				helper.assertTrue(ForgeStats.partLines(variant, material).equals(ForgeStats.partLines(variant.base(), material)),
					variant + " de " + material + " da lo mismo que " + variant.base());
			}
			for (PartVariant handle : PartVariant.values()) {
				for (PartVariant binding : PartVariant.values()) {
					List<ItemStack> parts = List.of(Assembler.createPart(PartType.CABEZA_HACHA, IRON),
						Assembler.createPart(PartType.of(PartType.MANGO, handle), material), Assembler.createPart(PartType.of(PartType.ATADURA, binding), material));
					List<ItemStack> plainParts = List.of(Assembler.createPart(PartType.CABEZA_HACHA, IRON),
						Assembler.createPart(PartType.MANGO, material), Assembler.createPart(PartType.ATADURA, material));
					ItemStack axe = Assembler.evaluate(parts, registries).stack();
					ItemStack plain = Assembler.evaluate(plainParts, registries).stack();
					String what = "hacha de mango " + handle + " y atadura " + binding + " de " + material;
					helper.assertTrue(axe.get(ModComponents.PARTS).hasTrait(material.trait) || material.trait == ForgeMaterial.Trait.NONE, what + ": tiene su rasgo");
					helper.assertTrue(java.util.Objects.equals(axe.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS),
						plain.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS)), what + ": los encantamientos del rasgo son los mismos");
					int expected = Math.max(1, Math.round(plain.getMaxDamage() * Grip.durability(axe.get(ModComponents.PARTS))));
					helper.assertTrue(axe.getMaxDamage() == expected, what + ": durabilidad " + axe.getMaxDamage() + ", se esperaba " + expected);
					ForgeStats.Sheet sheet = ForgeStats.sheet(axe, axe.get(ModComponents.PARTS));
					ForgeStats.Sheet plainSheet = ForgeStats.sheet(plain, plain.get(ModComponents.PARTS));
					helper.assertTrue(Math.abs(sheet.attackDamage - plainSheet.attackDamage) < 1.0E-4F, what + ": el mismo daño");
				}
			}
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- what they do

	/** The heavy handle weighs more and swings to full strength later, the light one sooner; a mob holding it warns longer. */
	@GameTest
	public void weightMovesTheSwing(GameTestHelper helper) {
		for (ForgeType type : List.of(ForgeType.PICO, ForgeType.MAZO, ForgeType.ESPADA, ForgeType.LANZA)) {
			List<ForgeMaterial> iron = new ArrayList<>(java.util.Collections.nCopies(type.slots.size(), IRON));
			ItemStack normal = forged(type, iron, PartVariant.NORMAL, PartVariant.NORMAL);
			ItemStack heavy = forged(type, iron, PartVariant.PESADO, PartVariant.PESADO);
			List<ForgeMaterial> wood = new ArrayList<>(iron);
			wood.set(type.slots.indexOf(PartType.MANGO), ForgeMaterial.MADERA);
			ItemStack plainWood = forged(type, wood, PartVariant.NORMAL, PartVariant.NORMAL);
			ItemStack light = forged(type, wood, PartVariant.LIGERO, PartVariant.NORMAL);
			ForgeStats.Sheet n = ForgeStats.sheet(normal, normal.get(ModComponents.PARTS));
			ForgeStats.Sheet h = ForgeStats.sheet(heavy, heavy.get(ModComponents.PARTS));
			ForgeStats.Sheet w = ForgeStats.sheet(plainWood, plainWood.get(ModComponents.PARTS));
			ForgeStats.Sheet l = ForgeStats.sheet(light, light.get(ModComponents.PARTS));
			helper.assertTrue(h.weight > n.weight && l.weight < w.weight, type + " pesos: " + h.weight + " > " + n.weight + ", " + l.weight + " < " + w.weight);
			helper.assertTrue(h.attackSpeed < n.attackSpeed, type + ": el pesado va más lento " + h.attackSpeed + " < " + n.attackSpeed);
			helper.assertTrue(l.attackSpeed > w.attackSpeed, type + ": el ligero va más rápido " + l.attackSpeed + " > " + w.attackSpeed);
			float slower = (4.0F + h.attackSpeed) / (4.0F + n.attackSpeed);
			helper.assertTrue(slower > 0.85F && slower < 0.97F, type + ": el pesado pierde entre un 3 y un 15 %: " + slower);
			helper.assertTrue(Math.abs(Weight.kg(heavy) - h.weight) < 1.0E-4F, "Weight.kg de la pila lee las variantes");
		}
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.EVENT);
		CombatGameTests.emptyHands(zombie);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		List<ForgeMaterial> mace = List.of(IRON, IRON, IRON);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, forged(ForgeType.MAZO, List.of(IRON, ForgeMaterial.MADERA, ForgeMaterial.CUERO), PartVariant.LIGERO, PartVariant.LIGERO));
		int lightWarn = MobDefense.windup(zombie);
		int lightWait = Weight.interval(zombie, 40);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, forged(ForgeType.MAZO, mace, PartVariant.PESADO, PartVariant.PESADO));
		int heavyWarn = MobDefense.windup(zombie);
		int heavyWait = Weight.interval(zombie, 40);
		helper.assertTrue(heavyWarn > lightWarn && heavyWait > lightWait,
			"el zombi avisa y espera más con el mazo pesado: " + heavyWarn + "/" + heavyWait + " frente a " + lightWarn + "/" + lightWait);
		helper.succeed();
	}

	/** Stamina a swing costs follows the handle: more with a counterweight, less with a light handle. */
	@GameTest
	public void staminaFollowsTheHandle(GameTestHelper helper) {
		float cost = CombatConfig.get().attackCost;
		float[] spent = new float[3];
		PartVariant[] handles = {PartVariant.NORMAL, PartVariant.PESADO, PartVariant.LIGERO};
		for (int i = 0; i < 3; i++) {
			CombatGameTests.TestPlayer player = holding(helper, new BlockPos(1, 1, 1 + i), pick(handles[i], PartVariant.NORMAL));
			float before = Stamina.value(player);
			Stamina.onAttack(player);
			spent[i] = before - Stamina.value(player);
		}
		helper.assertTrue(Math.abs(spent[0] - cost) < 1.0E-3F, "normal: " + spent[0] + " de " + cost);
		helper.assertTrue(Math.abs(spent[1] - cost * Grip.HEAVY_SWING_COST) < 1.0E-3F, "pesado: " + spent[1]);
		helper.assertTrue(Math.abs(spent[2] - cost * Grip.LIGHT_SWING_COST) < 1.0E-3F, "ligero: " + spent[2]);
		helper.succeed();
	}

	/** A plain blow: the heavy handle shakes the balance more and throws further; the light one less. */
	@GameTest(maxTicks = 60)
	public void postureAndKnockbackFollowTheHandle(GameTestHelper helper) {
		PartVariant[] handles = {PartVariant.NORMAL, PartVariant.PESADO, PartVariant.LIGERO};
		double[] shake = new double[3];
		double[] push = new double[3];
		for (int i = 0; i < 3; i++) {
			Zombie zombie = target(helper, new BlockPos(3, 1, 1 + 3 * i));
			// Same head, same damage: only the handle differs (and the light one is wood, which hits alike).
			ItemStack weapon = forged(ForgeType.PICO, List.of(IRON, handles[i] == PartVariant.LIGERO ? ForgeMaterial.MADERA : IRON, IRON), handles[i], PartVariant.NORMAL);
			CombatGameTests.TestPlayer player = holding(helper, new BlockPos(1, 1, 1 + 3 * i), weapon);
			face(player, zombie);
			float before = zombie.getHealth();
			zombie.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			player.attack(zombie);
			float dealt = before - zombie.getHealth();
			helper.assertTrue(dealt > 0.0F, "el golpe entra (" + handles[i] + ")");
			shake[i] = Posture.fill(zombie) / dealt;
			push[i] = zombie.getDeltaMovement().horizontalDistance();
		}
		helper.assertTrue(Math.abs(shake[1] / shake[0] - Grip.HEAVY_POSTURE) < 0.02, "postura pesado/normal " + shake[1] / shake[0]);
		helper.assertTrue(Math.abs(shake[2] / shake[0] - Grip.LIGHT_POSTURE) < 0.02, "postura ligero/normal " + shake[2] / shake[0]);
		helper.assertTrue(push[1] > push[0] && push[0] > push[2], "empuje: pesado " + push[1] + " > normal " + push[0] + " > ligero " + push[2]);
		helper.succeed();
	}

	/** A charged blow with a counterweight lands about 18 % harder; with a light handle a little softer. */
	@GameTest(maxTicks = 100)
	public void chargedBlowFollowsTheHandle(GameTestHelper helper) {
		PartVariant[] handles = {PartVariant.NORMAL, PartVariant.PESADO, PartVariant.LIGERO};
		Zombie[] zombies = new Zombie[3];
		CombatGameTests.TestPlayer[] players = new CombatGameTests.TestPlayer[3];
		for (int i = 0; i < 3; i++) {
			zombies[i] = target(helper, new BlockPos(3, 1, 1 + 3 * i));
			// A wooden dagger: weak enough that no charged blow reaches the per-hit cap.
			ItemStack dagger = forged(ForgeType.DAGA, List.of(ForgeMaterial.MADERA, handles[i] == PartVariant.PESADO ? IRON : ForgeMaterial.MADERA),
				handles[i], PartVariant.NORMAL);
			players[i] = holding(helper, new BlockPos(1, 1, 1 + 3 * i), dagger);
			face(players[i], zombies[i]);
			ChargedStrike.onPayload(players[i], ChargePayload.START);
		}
		helper.runAfterDelay(CombatConfig.get().chargeFullTicks + 1, () -> {
			float[] dealt = new float[3];
			for (int i = 0; i < 3; i++) {
				float before = zombies[i].getHealth();
				zombies[i].invulnerableTime = 0;
				rested(players[i]);
				ChargedStrike.onPayload(players[i], ChargePayload.RELEASE);
				dealt[i] = before - zombies[i].getHealth();
			}
			helper.assertTrue(dealt[0] > 0.0F, "el golpe cargado entra");
			helper.assertTrue(Math.abs(dealt[1] / dealt[0] - Grip.HEAVY_CHARGE) < 0.03F, "cargado pesado/normal " + dealt[1] / dealt[0]);
			helper.assertTrue(Math.abs(dealt[2] / dealt[0] - Grip.LIGHT_CHARGE) < 0.03F, "cargado ligero/normal " + dealt[2] / dealt[0]);
			helper.succeed();
		});
	}

	/** The binding: a heavy one lasts longer, holds a charge through a bash and brings a broken guard back sooner. */
	@GameTest
	public void bindingMovesDurabilityAndGuard(GameTestHelper helper) {
		ItemStack normal = pick(PartVariant.NORMAL, PartVariant.NORMAL);
		ItemStack heavy = pick(PartVariant.NORMAL, PartVariant.PESADO);
		ItemStack light = forged(ForgeType.PICO, List.of(IRON, IRON, ForgeMaterial.CUERO), PartVariant.NORMAL, PartVariant.LIGERO);
		ItemStack plainLeather = forged(ForgeType.PICO, List.of(IRON, IRON, ForgeMaterial.CUERO), PartVariant.NORMAL, PartVariant.NORMAL);
		helper.assertTrue(heavy.getMaxDamage() == Math.round(normal.getMaxDamage() * Grip.HEAVY_DURABILITY),
			"atadura pesada: " + heavy.getMaxDamage() + " de " + normal.getMaxDamage());
		helper.assertTrue(light.getMaxDamage() == Math.round(plainLeather.getMaxDamage() * Grip.LIGHT_DURABILITY),
			"atadura ligera: " + light.getMaxDamage() + " de " + plainLeather.getMaxDamage());
		int ticks = CombatConfig.get().guardBreakTicks;
		helper.assertTrue(Grip.guardBreakTicks(heavy, ticks) < ticks && Grip.guardBreakTicks(light, ticks) > ticks
			&& Grip.guardBreakTicks(normal, ticks) == ticks, "la guardia rota vuelve antes con la pesada y después con la ligera");
		helper.assertTrue(Grip.blockCost(heavy) < 1.0F && Grip.blockCost(normal) == 1.0F, "bloquear cuesta menos con la pesada");
		// A shield bash knocks a charge loose, but not past rivets and bands.
		CombatGameTests.TestPlayer bound = holding(helper, new BlockPos(1, 1, 1), heavy);
		CombatGameTests.TestPlayer plain = holding(helper, new BlockPos(1, 1, 3), normal);
		ChargedStrike.onPayload(bound, ChargePayload.START);
		ChargedStrike.onPayload(plain, ChargePayload.START);
		helper.assertTrue(!ChargedStrike.interrupt(bound) && ChargedStrike.isCharging(bound), "la atadura pesada no suelta la carga");
		helper.assertTrue(ChargedStrike.interrupt(plain) && !ChargedStrike.isCharging(plain), "la normal sí");
		ChargedStrike.onPayload(bound, ChargePayload.CANCEL);
		helper.succeed();
	}

	// ---------------------------------------------------------------- old saves

	/** A piece saved before the variants existed loads as a plain one, and a plain one saves as it always did. */
	@GameTest
	public void oldItemsStillLoad(GameTestHelper helper) {
		var old = JsonParser.parseString("{\"type\":\"pico\",\"materials\":[\"hierro\",\"madera\",\"cuero\"]}");
		ForgedParts parts = ForgedParts.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(parts.type() == ForgeType.PICO && !parts.hasVariants() && Grip.handle(parts) == PartVariant.NORMAL,
			"el pico de antes se lee como normal: " + parts);
		var plain = ForgedParts.CODEC.encodeStart(JsonOps.INSTANCE, parts).getOrThrow();
		helper.assertTrue(plain.equals(old), "y se guarda igual que antes: " + plain);
		// A whole stack, as a save writes it: the variant goes and comes back.
		var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
		ItemStack heavy = pick(PartVariant.PESADO, PartVariant.LIGERO);
		var saved = ItemStack.CODEC.encodeStart(ops, heavy).getOrThrow();
		ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(ItemStack.isSameItemSameComponents(heavy, loaded) && Grip.handle(loaded) == PartVariant.PESADO,
			"una pieza con variantes se guarda y se carga entera");
		// The variant parts and pieces of the first version (heavy only in dense metal, light only in wood, bone or
		// leather), as a save wrote them, load and keep their shape and material.
		ItemStack oldHeavy = ItemStack.CODEC.parse(ops, JsonParser.parseString(
			"{\"id\":\"forja:mango_pesado\",\"count\":1,\"components\":{\"forja:material\":\"hierro\",\"forja:colada\":true}}")).getOrThrow();
		helper.assertTrue(oldHeavy.getItem() == ModItems.part(PartType.MANGO_PESADO) && oldHeavy.get(ModComponents.MATERIAL) == IRON,
			"un mango pesado de hierro de antes se carga: " + oldHeavy);
		ItemStack oldLight = ItemStack.CODEC.parse(ops, JsonParser.parseString(
			"{\"id\":\"forja:atadura_ligera\",\"count\":2,\"components\":{\"forja:material\":\"cuero\"}}")).getOrThrow();
		helper.assertTrue(oldLight.getItem() == ModItems.part(PartType.ATADURA_LIGERA) && oldLight.getCount() == 2,
			"y una atadura ligera de cuero");
		ItemStack oldPick = ItemStack.CODEC.parse(ops, JsonParser.parseString(
			"{\"id\":\"forja:pico\",\"count\":1,\"components\":{\"forja:partes\":{\"type\":\"pico\",\"materials\":[\"hierro\",\"hierro\",\"cuero\"],"
				+ "\"variants\":[\"normal\",\"pesado\",\"ligero\"]}}}")).getOrThrow();
		helper.assertTrue(Grip.handle(oldPick) == PartVariant.PESADO && Grip.binding(oldPick) == PartVariant.LIGERO,
			"y un pico de antes con mango pesado y atadura ligera: " + oldPick.get(ModComponents.PARTS));
		ItemStack rebuilt = Assembler.evaluate(List.of(oldHeavy, oldLight.copyWithCount(1), Assembler.createPart(PartType.CABEZA_PICO, IRON)),
			helper.getLevel().registryAccess()).stack();
		helper.assertTrue(rebuilt.get(ModComponents.PARTS).equals(oldPick.get(ModComponents.PARTS)), "y sus piezas siguen montando lo mismo");
		helper.assertTrue(Math.abs(Weight.kg(oldPick) - Weight.kg(rebuilt)) < 1.0E-5F, "con el mismo peso");
		// And an old stack with no variants stacks with a plain one made today.
		ItemStack today = pick(PartVariant.NORMAL, PartVariant.NORMAL);
		ItemStack before = Assembler.create(ForgeType.PICO, List.of(IRON, IRON, IRON));
		helper.assertTrue(ItemStack.isSameItemSameComponents(today, before), "un pico normal de hoy es igual que uno de antes");
		helper.succeed();
	}
}
