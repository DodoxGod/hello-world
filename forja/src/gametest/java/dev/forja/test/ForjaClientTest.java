package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import dev.forja.client.ForgeScreen;
import dev.forja.client.GuideBookScreen;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeStats;
import dev.forja.forge.ForgeType;
import dev.forja.item.PartItem;
import dev.forja.item.TemplateItem;
import dev.forja.menu.ForgeMenu;
import dev.forja.menu.Station;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.UpgradeRecipes;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import static dev.forja.material.ForgeMaterial.AMATISTA;
import static dev.forja.material.ForgeMaterial.COBRE;
import static dev.forja.material.ForgeMaterial.CUARZO;
import static dev.forja.material.ForgeMaterial.CUERO;
import static dev.forja.material.ForgeMaterial.ESMERALDA;
import static dev.forja.material.ForgeMaterial.ESTELAR;
import static dev.forja.material.ForgeMaterial.PIEDRA;
import static dev.forja.material.ForgeMaterial.PRISMARINA;
import static dev.forja.material.ForgeMaterial.PURPUR;
import static dev.forja.material.ForgeMaterial.VARA_DE_BLAZE;
import static dev.forja.material.ForgeMaterial.DIAMANTE;
import static dev.forja.material.ForgeMaterial.HIERRO;
import static dev.forja.material.ForgeMaterial.MADERA;
import static dev.forja.material.ForgeMaterial.HUESO;
import static dev.forja.material.ForgeMaterial.NETHERITA;
import static dev.forja.material.ForgeMaterial.OBSIDIANA_LLORONA;
import static dev.forja.material.ForgeMaterial.OBSIDIANA;
import static dev.forja.material.ForgeMaterial.ORO;
import static dev.forja.material.ForgeMaterial.PIEDRA;

/**
 * Boots the client in Spanish and checks the mod end to end: part stats, the upgrade system
 * (percentages, block items, combos, conflicts, enchantment-backed levels, attributes), both tables
 * and the guide book with screenshots, worn armor, and the code-driven upgrades (Lanzacabezas, Veta,
 * the hammer's 3x3, Fundicion, Telequinesis, Multidisparo, Zancada, Ejecucion, Nutricion,
 * Purificacion, Luz and Absorcion).
 */
public class ForjaClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		CompletableFuture<Void> reload = context.computeOnClient(mc -> {
			mc.options.languageCode = "es_mx";
			mc.getLanguageManager().setSelected("es_mx");
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		if ("mundo".equals(System.getenv("FORJA_SOLO"))) {
			visitBastionInTheWild(context);
			log("ALL CHECKS PASSED (solo mundo)");
			return;
		}
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerConnection connection = singleplayer.getConnection();
			TestServerContext server = singleplayer.getServer();
			connection.waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("difficulty peaceful");
			server.runCommand("gamemode survival @a");

			Vec3 spawn = server.computeOnServer(s -> connection.getServerPlayer().position());
			int x = (int) Math.floor(spawn.x);
			int y = (int) Math.floor(spawn.y);
			int z = (int) Math.floor(spawn.z);

			// One section by itself. Something that is judged by eye gets looked at a dozen times, and
			// the whole file is five minutes a look: FORJA_SOLO=onda runs the ring and nothing else.
			String solo = System.getenv("FORJA_SOLO");
			if ("onda".equals(solo)) {
				checkShockwave(context, server, connection, x, y, z);
				log("ALL CHECKS PASSED (solo " + solo + ")");
				return;
			}
			if ("meteorito".equals(solo)) {
				filmMeteor(context, server, connection, x, y, z);
				log("ALL CHECKS PASSED (solo " + solo + ")");
				return;
			}
			if ("hud".equals(solo)) {
				shotHudAndPools(context, server, connection, x, y, z);
				log("ALL CHECKS PASSED (solo " + solo + ")");
				return;
			}
			if ("libro".equals(solo)) {
				checkEverythingIsNamed(context);
				checkGuideBook(context);
				log("ALL CHECKS PASSED (solo " + solo + ")");
				return;
			}
			if ("potencial".equals(solo)) {
				checkPotential(context, server, connection, x, y, z);
				checkLoad(context, server, connection, x, y, z);
				checkExtraction(context, server, connection, x, y, z);
				log("ALL CHECKS PASSED (solo " + solo + ")");
				return;
			}
			if ("magia".equals(solo)) {
				checkMagic(context, server, connection, x, y, z);
				checkMagicUpgrades(context, server, connection, x, y, z);
				filmMagicUpgrades(context, server, connection, x, y, z);
				log("ALL CHECKS PASSED (solo " + solo + ")");
				return;
			}
			if ("castillo".equals(solo)) {
				filmBastion(context, server, connection, x, y, z);
				log("ALL CHECKS PASSED (solo " + solo + ")");
				return;
			}
			if ("particulas".equals(solo)) {
				filmParticles(context, server, connection, x, y, z);
				log("ALL CHECKS PASSED (solo " + solo + ")");
				return;
			}
			if ("pantallas".equals(solo)) {
				shotStationScreens(context, server, connection, x, y, z);
				checkPartsCabinet(context, server, connection, x, y, z);
				log("ALL CHECKS PASSED (solo " + solo + ")");
				return;
			}
			if ("cielo".equals(solo)) {
				shotSkies(context, server, connection, x, y, z);
				log("ALL CHECKS PASSED (solo " + solo + ")");
				return;
			}

			checkStats(server);
			checkEverythingForges(context, server, connection);
			checkUpgrades(server, connection);
			checkPotential(context, server, connection, x, y, z);
			checkLoad(context, server, connection, x, y, z);
			checkExtraction(context, server, connection, x, y, z);
			checkMagic(context, server, connection, x, y, z);
			checkMagicUpgrades(context, server, connection, x, y, z);
			checkForgeTable(context, server, connection, x, y, z);
			checkEverythingIsNamed(context);
			checkGuideBook(context);
			checkLoot(server, connection);
			checkAbandonedForge(context, server, connection, x, y, z);
			checkNewUpgrades(context, server, connection, x, y, z);
			checkTraits(server, connection);
			checkNewTraits(context, server, connection, x, y, z);
			checkBrokenGear(context, server, connection);
			checkParry(context, server, connection);
			checkStatColors(server);
			checkSpearAndMace(context, server, connection, x, y);
			checkBowAndShield(context, server, connection, x, y, z);
			checkDisassemble(context, server, connection, x, y, z);
			checkArmorAndIcons(context, server, connection, x, y, z);
			checkHeadThrow(context, server, connection, x, y, z);
			checkGrapple(context, server, connection, x, y, z);
			shotGrips(context, server, connection, x, y, z);
			filmAttacks(context, server, connection, x, y, z);
			shotSkies(context, server, connection, x, y, z);
			shotForge(context, server, connection, x, y, z);
			filmMeteor(context, server, connection, x, y, z);
			shotNewMobs(context, server, connection, x, y, z);
			checkMiningUpgrades(server, connection, x, y, z);
			writeUpgradeDoc(server, connection);
			checkBalanceLimits(server, connection);
			checkPartsCabinet(context, server, connection, x, y, z);
			checkMobPortraits(context, server, connection, x, y, z);
			checkNewCombatUpgrades(context, server, connection, x, y, z);
			checkTechniqueEffects(context, server, connection, x, y, z);
			checkNewAttacks(context, server, connection, x, y, z);
			checkShockwave(context, server, connection, x, y, z);
			shotHudAndPools(context, server, connection, x, y, z);
			checkCrucibles(context, server, connection, x, y, z);
			shotStationScreens(context, server, connection, x, y, z);
			filmParticles(context, server, connection, x, y, z);
			checkFoundryAlloys(context, server, connection, x, y, z);
			checkCastingTables(context, server, connection, x, y, z);
			checkSpout(context, server, connection, x, y, z);
			checkFlow(context, server, connection, x, y, z);
			checkCastOnly(context, server, connection, x, y, z);
			checkTwoBenches(context, server, connection, x, y, z);
			shotFoundry(context, server, connection, x, y, z);
			checkEssenceJar(context, server, connection, x, y, z);
			shotWorkshop(context, server, connection, x, y, z);
			checkRaiderCamp(context, server, connection, x, y, z);
			log("ALL CHECKS PASSED");
		}
	}

	private static void checkStats(TestServerContext server) {
		server.runOnServer(s -> {
			ItemStack diamondPick = Assembler.create(ForgeType.PICO, List.of(DIAMANTE, PIEDRA, HIERRO));
			ItemStack stonePick = Assembler.create(ForgeType.PICO, List.of(PIEDRA, MADERA, MADERA));
			check(diamondPick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "diamond head should mine obsidian");
			check(!stonePick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "stone head must not mine obsidian");
			check(stonePick.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()), "stone head should mine iron ore");

			ItemStack swapped = Assembler.evaluate(List.of(stonePick, Assembler.createPart(PartType.CABEZA_PICO, DIAMANTE)), s.registryAccess()).stack();
			check(swapped.get(ModComponents.PARTS).material(0) == DIAMANTE, "swapping the head should change its material");
			check(ForgeType.match(List.of(PartType.MANGO, PartType.HOJA, PartType.GUARDA, PartType.HOJA)) == ForgeType.ESPADON, "two blades + handle + guard is a greatsword");
			var tabs = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB;
			check(tabs.containsKey(dev.forja.Forja.id("forja")) && tabs.containsKey(dev.forja.Forja.id("piezas")), "Forja should have a gear tab and a parts tab");
			log("stats ok: diamond pickaxe durability " + diamondPick.getMaxDamage());
		});
	}

	/**
	 * Safety net for new parts and items: every part can be cut from some material, every item can be
	 * forged from its parts and names itself, and the client has a model for every item Forja registers.
	 */
	private static void checkEverythingForges(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runOnServer(s -> {
			HolderLookup.Provider registries = connection.getServerLevel().registryAccess();
			for (PartType part : PartType.values()) {
				// Only the basic materials are cut at a bench now; the rest have to be poured, so this
				// sweep asks for the first basic material each part will take.
				dev.forja.material.ForgeMaterial material = java.util.Arrays.stream(dev.forja.material.ForgeMaterial.values())
					.filter(m -> m.isBasic() && part.accepts(m)).findFirst().orElseThrow();
				ItemStack cut = Assembler.partResult(part, new ItemStack(material.displayStack().getItem(), 64));
				check(!cut.isEmpty() && cut.get(ModComponents.MATERIAL) == material, "the parts table should cut a " + part.id() + " from " + material.getSerializedName());
			}
			for (ForgeType type : ForgeType.values()) {
				List<ItemStack> parts = new java.util.ArrayList<>();
				List<dev.forja.material.ForgeMaterial> materials = Assembler.defaultMaterials(type);
				for (int slot = 0; slot < type.slots.size(); slot++) {
					parts.add(Assembler.createPart(type.slots.get(slot), materials.get(slot)));
				}
				Assembler.Result forged = Assembler.evaluate(parts, registries);
				check(!forged.stack().isEmpty() && forged.stack().get(ModComponents.PARTS).type() == type,
					"the star should forge a " + type.id() + " from its parts");
				ItemStack made = Assembler.create(type, materials, registries);
				// Arrows are the one forged thing with no durability: they stack instead.
				boolean lasting = type.kind == ForgeType.Kind.MUNICION ? made.getMaxStackSize() > 1 : made.getMaxDamage() > 0;
				check(lasting && !made.getHoverName().getString().isBlank(), "a forged " + type.id() + " needs durability and a name");
			}
			log("every part cuts and every one of " + ForgeType.values().length + " items forges");
		});
		String missingModels = context.computeOnClient(mc -> {
			List<String> missing = new ArrayList<>();
			for (var entry : net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet()) {
				if (!entry.getKey().identifier().getNamespace().equals("forja")) {
					continue;
				}
				var model = mc.getModelManager().getItemModel(entry.getKey().identifier());
				if (model instanceof net.minecraft.client.renderer.item.MissingItemModel) {
					missing.add(entry.getKey().identifier().getPath());
				}
			}
			return String.join(", ", missing);
		});
		check(missingModels.isEmpty(), "every Forja item needs an item model, these have none: " + missingModels);
	}

	private static void checkUpgrades(TestServerContext server, TestServerConnection connection) {
		server.runOnServer(s -> {
			HolderLookup.Provider registries = connection.getServerLevel().registryAccess();

			// Presteza: 5 redstone blocks (9% each) then 10 redstone dust (1% each). That was 55% when every
			// ingredient was worth what it said all the way up; past fifty it is worth half (forge/Potential),
			// so the last five dust buy two and a half between them.
			int prestezaNow = dev.forja.forge.Potential.raised(dev.forja.forge.Potential.raised(0, 45), 10);
			ItemStack leggings = Assembler.create(ForgeType.GREBAS, List.of(HIERRO, CUERO));
			UpgradeRecipes.Application speed = UpgradeRecipes.apply(
				leggings, List.of(new ItemStack(Items.REDSTONE, 10), new ItemStack(Items.REDSTONE_BLOCK, 5), ItemStack.EMPTY), registries
			);
			check(speed != null && speed.upgrade() == Upgrade.PRESTEZA && speed.after() == prestezaNow, "redstone should raise Presteza to " + prestezaNow + "%, got " + describe(speed));
			check(speed.consumed()[0] == 10 && speed.consumed()[1] == 5, "Presteza should use every item it needs, used " + speed.consumed()[0] + " dust and " + speed.consumed()[1] + " blocks");
			ItemStack fastLeggings = speed.result();
			boolean hasSpeed = fastLeggings.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, null).modifiers().stream()
				.anyMatch(entry -> entry.attribute().equals(Attributes.MOVEMENT_SPEED) && entry.modifier().amount() > 0.1);
			check(hasSpeed, "Presteza must add a movement speed modifier to the leggings");

			// Filo: amethyst blocks stop at 100% and become Sharpness V.
			ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(DIAMANTE, MADERA, ORO));
			UpgradeRecipes.Application sharp = UpgradeRecipes.apply(sword, List.of(new ItemStack(Items.AMETHYST_BLOCK, 64), ItemStack.EMPTY, ItemStack.EMPTY), registries);
			// As many blocks as it takes and not one more. It was seven when a hundred percent cost a hundred;
			// it costs two hundred now, so the count is worked out the way the recipe works it out.
			int blockWorth = Upgrade.FILO.options.stream().filter(option -> option.isSingle() && option.requirements().getFirst().test(new ItemStack(Items.AMETHYST_BLOCK)))
				.mapToInt(Upgrade.Option::percent).findFirst().orElse(0);
			int blocksNeeded = 0;
			for (int progress = 0; progress / 4 < 100; blocksNeeded++) {
				progress = dev.forja.forge.Potential.spend(progress, blockWorth);
			}
			check(sharp != null && sharp.upgrade() == Upgrade.FILO && sharp.after() == 100 && sharp.consumed()[0] == blocksNeeded,
				"64 amethyst blocks should use " + blocksNeeded + " and max Filo, got " + describe(sharp));
			check(blocksNeeded > 7, "and the second half of an upgrade should cost more than the first: " + blocksNeeded + " blocks");
			int sharpness = EnchantmentHelper.getItemEnchantmentLevel(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), sharp.result());
			check(sharpness == 5, "Filo 100% should be Sharpness V, got " + sharpness);

			// Castigo shares the damage group with Filo.
			UpgradeRecipes.Application smite = UpgradeRecipes.apply(sharp.result(), List.of(new ItemStack(Items.ROTTEN_FLESH, 10), ItemStack.EMPTY, ItemStack.EMPTY), registries);
			check(smite != null && smite.conflict() == Upgrade.FILO && smite.result().isEmpty(), "Castigo must be refused next to Filo, got " + describe(smite));

			// Lanzacabezas is a combo: piston + slime ball + ender pearl, 25% per set.
			ItemStack pick = Assembler.create(ForgeType.PICO, List.of(DIAMANTE, PIEDRA, ORO));
			UpgradeRecipes.Application toss = UpgradeRecipes.apply(
				pick, List.of(new ItemStack(Items.PISTON, 3), new ItemStack(Items.ENDER_PEARL, 2), new ItemStack(Items.SLIME_BALL, 5)), registries
			);
			check(toss != null && toss.upgrade() == Upgrade.LANZACABEZAS && toss.after() == 50, "2 full sets should give Lanzacabezas 50%, got " + describe(toss));

			// Enchanting books do not work on forged gear anymore.
			boolean enchantable = pick.canBeEnchantedWith(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), EnchantingContext.ACCEPTABLE);
			log("vanilla Efficiency accepted by a forged pickaxe: " + enchantable);
			check(!enchantable, "forged gear must not accept vanilla enchantments");
			log("upgrades ok");
		});
	}

	private static String describe(UpgradeRecipes.Application application) {
		return application == null ? "no match" : application.upgrade() + " " + application.before() + "->" + application.after() + " conflict " + application.conflict();
	}

	/** Both tables side by side: templates and parts on the parts table, the star on the forge table. */
	private static void checkForgeTable(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		BlockPos partsTable = new BlockPos(x, y, z + 2);
		BlockPos forgeTable = new BlockPos(x + 1, y, z + 2);
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_piezas", partsTable.getX(), partsTable.getY(), partsTable.getZ()));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_forja", forgeTable.getX(), forgeTable.getY(), forgeTable.getZ()));
		tp(server, x + 1.0, y, z + 0.3, 0.0F, 30.0F);
		context.waitTicks(5);
		context.takeScreenshot("forja_00_mesas");

		openTable(context, server, connection, partsTable);
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			ServerPlayer player = connection.getServerPlayer();
			check(menu.station() == Station.PIEZAS && menu.getMode() == ForgeMenu.MODE_PARTS, "the parts table should open on the parts tab");
			check(!menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "the parts table must not forge");
			// Amethyst rather than diamond: metal and diamond are poured now, not cut at a bench.
			menu.getSlot(ForgeMenu.MATERIAL_SLOT).set(new ItemStack(Items.AMETHYST_SHARD, 5));
			check(menu.getSlot(ForgeMenu.PART_RESULT_SLOT).getItem().isEmpty(), "without a template the parts table must not cut anything");
			check(!menu.clickMenuButton(player, PartType.CABEZA_PICO.ordinal()), "patterns need a template to engrave");
			menu.getSlot(ForgeMenu.TEMPLATE_SLOT).set(new ItemStack(ModItems.PLANTILLA));
		});
		context.waitTicks(10);
		context.takeScreenshot("forja_01_plantilla_base");
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			check(menu.clickMenuButton(connection.getServerPlayer(), PartType.CABEZA_PICO.ordinal()), "a blank template should take a shape");
			ItemStack template = menu.getSlot(ForgeMenu.TEMPLATE_SLOT).getItem();
			check(TemplateItem.pattern(template) == PartType.CABEZA_PICO, "the template should now be a pickaxe head template");
			check(!menu.clickMenuButton(connection.getServerPlayer(), PartType.HOJA.ordinal()), "an engraved template must not take another shape");
			check(TemplateItem.pattern(menu.getSlot(ForgeMenu.TEMPLATE_SLOT).getItem()) == PartType.CABEZA_PICO, "the engraving must stay a pickaxe head");
			ItemStack result = menu.getSlot(ForgeMenu.PART_RESULT_SLOT).getItem();
			check(result.getItem() instanceof PartItem part && part.type == PartType.CABEZA_PICO, "amethyst + pickaxe head template should make a head, got " + result);
		});
		context.waitTicks(10);
		context.takeScreenshot("forja_01_piezas");
		hover(context, 13 + PartType.PUNTA_LANZA.ordinal() % 11 * 17 + 8, 24 + PartType.PUNTA_LANZA.ordinal() / 11 * 17 + 8);
		context.waitTicks(5);
		context.takeScreenshot("forja_01b_pieza_tooltip");
		context.getInput().setCursorPos(0, 0);
		server.runOnServer(s -> connection.getServerPlayer().closeContainer());
		context.waitTicks(5);

		openTable(context, server, connection, forgeTable);
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			check(menu.station() == Station.FORJA && menu.getMode() == ForgeMenu.MODE_FORGE, "the forge table should open on the star");
			check(!menu.clickMenuButton(connection.getServerPlayer(), PartType.CABEZA_PICO.ordinal()), "the forge table must not engrave templates");
			menu.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.CABEZA_PICO, DIAMANTE));
			menu.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.MANGO, PIEDRA));
			menu.getSlot(ForgeMenu.STAR_FIRST + 4).set(Assembler.createPart(PartType.ATADURA, ORO));
			check(menu.action() == ForgeMenu.Action.FORGE && menu.forgePreview().has(ModComponents.PARTS), "three parts on the star should preview a pickaxe");
		});
		context.waitTicks(10);
		context.takeScreenshot("forja_02_estrella");
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			check(menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_FORGE), "the forge button should forge");
			ItemStack gear = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			check(gear.has(ModComponents.PARTS) && gear.get(ModComponents.PARTS).type() == ForgeType.PICO, "forging should leave the pickaxe in the center, got " + gear);
			check(!menu.getSlot(ForgeMenu.STAR_FIRST).hasItem() && !menu.getSlot(ForgeMenu.STAR_FIRST + 2).hasItem(), "forging should use up the parts");
		});
		context.waitTicks(10);
		hover(context, ForgeMenu.CENTER_X + 8, ForgeMenu.CENTER_Y + 8);
		context.waitTicks(5);
		context.takeScreenshot("forja_02b_objeto_tooltip");
		context.getInput().setCursorPos(0, 0);

		// Fundir: parts of one material on a table over lava go back to being metal.
		ItemStack[] centerBefore = new ItemStack[1];
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			centerBefore[0] = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().copy();
			for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
				menu.getSlot(ForgeMenu.STAR_FIRST + i).set(ItemStack.EMPTY);
			}
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.CABEZA_PICO, HIERRO));
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(Assembler.createPart(PartType.MANGO, HIERRO));
			check(menu.action() == ForgeMenu.Action.NONE || menu.action() != ForgeMenu.Action.FUNDIR,
				"a cold table should not melt anything, got " + menu.action());
		});
		context.waitTicks(5);

		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			// Lava under the table is what makes it hot enough; the player's own feet do not matter.
			level.setBlockAndUpdate(new BlockPos(x + 1, y - 1, z + 2), net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
		});
		context.waitTicks(5);
		int[] melted = server.computeOnServer(s -> {
			ForgeMenu menu = menu(connection);
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			ItemStack preview = menu.forgePreview();
			return new int[] {menu.action() == ForgeMenu.Action.FUNDIR ? 1 : 0, preview.getCount(),
				preview.is(net.minecraft.world.item.Items.IRON_INGOT) ? 1 : 0};
		});
		log("fundir: accion " + (melted[0] == 1) + ", devuelve " + melted[1] + " lingotes de hierro " + (melted[2] == 1));
		check(melted[0] == 1, "parts of one material over lava should melt down");
		check(melted[2] == 1, "and come back as that material");
		check(melted[1] == 2, "half of what the two parts cost, rounded down, got " + melted[1]);
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			check(menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_FORGE), "the button should melt them");
			check(menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().is(net.minecraft.world.item.Items.IRON_INGOT),
				"and leave the metal in the middle");
			check(!menu.getSlot(ForgeMenu.STAR_FIRST).hasItem(), "the parts are gone");
			// Put back whatever was in the middle before, because the rest of the suite expects it there.
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(centerBefore[0]);
			connection.getServerLevel().setBlockAndUpdate(new BlockPos(x + 1, y - 1, z + 2), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
		});
		context.waitTicks(5);

		// Tecnicas: the three choices a smith makes on the way up, in the forge table's second tab.
		int[] smithBefore = new int[1];
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			smithBefore[0] = dev.forja.forge.SmithLevel.experience(player);
			for (dev.forja.forge.Technique technique : dev.forja.forge.Technique.values()) {
				check(!dev.forja.forge.Techniques.canLearn(player, technique),
					"a smith with no Maestria should not be able to take " + technique);
			}
			dev.forja.forge.SmithLevel.award(player, 9 * 9 * 40 - smithBefore[0]);
			check(dev.forja.forge.SmithLevel.level(player) == 9,
				"the test smith should stand at Maestria nine, got " + dev.forja.forge.SmithLevel.level(player));
			check(dev.forja.forge.Techniques.pending(player) == 3,
				"three choices should be open, got " + dev.forja.forge.Techniques.pending(player));
			check(menu(connection).clickMenuButton(player, ForgeMenu.BUTTON_TAB + ForgeMenu.MODE_TECHNIQUES),
				"the forge table should open its techniques tab");
		});
		context.waitTicks(10);
		context.takeScreenshot("forja_26_tecnicas");
		int clientLevel = context.computeOnClient(mc -> dev.forja.forge.SmithLevel.level(mc.player));
		int clientMask = context.computeOnClient(mc -> dev.forja.forge.Techniques.mask(mc.player));
		log("tecnicas (cliente): maestria " + clientLevel + ", mascara " + clientMask);
		check(clientLevel == 9, "the client should see the smith's Maestria, got " + clientLevel);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ForgeMenu menu = menu(connection);
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_TECHNIQUE + dev.forja.forge.Technique.PULSO_FIRME.ordinal()),
				"the first tier should take a choice");
			check(dev.forja.forge.Techniques.has(player, dev.forja.forge.Technique.PULSO_FIRME), "and keep it");
			check(!dev.forja.forge.Techniques.canLearn(player, dev.forja.forge.Technique.AHORRO_DE_METAL),
				"the other two of that tier should close");
			check(!menu.clickMenuButton(player, ForgeMenu.BUTTON_TECHNIQUE + dev.forja.forge.Technique.AHORRO_DE_METAL.ordinal()),
				"and the table should refuse a second one from the same tier");
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_TECHNIQUE + dev.forja.forge.Technique.MANO_DE_ORFEBRE.ordinal()),
				"the second tier should take one too");
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_TECHNIQUE + dev.forja.forge.Technique.ALMA_DE_FORJA.ordinal()),
				"and the third");
			check(dev.forja.forge.Techniques.pending(player) == 0,
				"with all three taken nothing should be left open, got " + dev.forja.forge.Techniques.pending(player));
			log("tecnicas: maestria " + dev.forja.forge.SmithLevel.level(player)
				+ ", elegidas " + dev.forja.forge.Techniques.chosen(player, 1).displayName().getString()
				+ " / " + dev.forja.forge.Techniques.chosen(player, 2).displayName().getString()
				+ " / " + dev.forja.forge.Techniques.chosen(player, 3).displayName().getString());
		});
		context.waitTicks(10);
		hover(context, 19, 30);
		context.waitTicks(5);
		context.takeScreenshot("forja_26b_tecnica_tooltip");
		context.getInput().setCursorPos(0, 0);
		server.runOnServer(s -> {
			// Put the smith back where the rest of the suite expects to find them.
			ServerPlayer player = connection.getServerPlayer();
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 0);
			player.setAttached(dev.forja.forge.SmithLevel.EXPERIENCE, smithBefore[0]);
			check(menu(connection).clickMenuButton(player, ForgeMenu.BUTTON_TAB + ForgeMenu.MODE_FORGE), "and go back to the star");
		});
		context.waitTicks(5);

		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.PISTON, 2));
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(new ItemStack(Items.SLIME_BALL, 2));
			menu.getSlot(ForgeMenu.STAR_FIRST + 3).set(new ItemStack(Items.ENDER_PEARL, 2));
			check(menu.action() == ForgeMenu.Action.UPGRADE, "ingredients on the star should upgrade the center gear, got " + menu.action());
		});
		context.waitTicks(10);
		context.takeScreenshot("forja_03_mejoras");
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			// Two sets are fifty percent, and this pickaxe was forged a moment ago with a press that was not
			// even timed: a plain piece, with the potential of one. The star gives it what it can take.
			int room = Math.min(50, dev.forja.forge.Potential.of(menu.getSlot(ForgeMenu.CENTER_SLOT).getItem()));
			check(menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_FORGE), "the forge button should upgrade");
			ItemStack gear = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			int tossed = gear.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.LANZACABEZAS);
			check(tossed == room, "two sets should give Lanzacabezas as far as the piece can take it (" + room + "%), got " + tossed);
			check(!menu.getSlot(ForgeMenu.STAR_FIRST).hasItem(), "the upgrade should use up the ingredients");

			menu.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.CABEZA_PICO, NETHERITA));
			check(menu.action() == ForgeMenu.Action.SWAP, "a part with gear in the center should swap it");
			menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_FORGE);
			ItemStack swapped = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			check(swapped.get(ModComponents.PARTS).material(0) == NETHERITA, "the swap should put the netherite head on");
			check(swapped.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.LANZACABEZAS) == tossed, "swapping parts keeps the upgrades");

			// Repair: the netherite head takes netherite ingots, a quarter of the durability each.
			swapped.setDamageValue(swapped.getMaxDamage() * 7 / 10);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(swapped);
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(new ItemStack(Items.NETHERITE_INGOT, 5));
			check(menu.action() == ForgeMenu.Action.REPAIR, "its repair material with worn gear in the center should repair, got " + menu.action());
			int damage = swapped.getDamageValue();
			menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_FORGE);
			ItemStack repaired = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			int left = menu.getSlot(ForgeMenu.STAR_FIRST + 1).getItem().getCount();
			log("repair: damage " + damage + " -> " + repaired.getDamageValue() + ", ingots left " + left);
			check(repaired.getDamageValue() == 0 && left == 2, "three ingots should fully repair 70% wear and leave two");

			// Enchanted books: Sharpness III of V is Filo 60%, Unbreaking III is Irrompible 100%.
			ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, MADERA));
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(sword);
			var enchantments = connection.getServerLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			var stored = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
			stored.set(enchantments.getOrThrow(Enchantments.SHARPNESS), 3);
			stored.set(enchantments.getOrThrow(Enchantments.UNBREAKING), 3);
			book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
			menu.getSlot(ForgeMenu.STAR_FIRST).set(book);
			check(menu.action() == ForgeMenu.Action.BOOK, "an enchanted book on the star should become upgrades, got " + menu.action());
			menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_FORGE);
			Upgrades fromBook = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
			log("book: filo " + fromBook.percent(Upgrade.FILO) + "%, irrompible " + fromBook.percent(Upgrade.IRROMPIBLE) + "%");
			// At the first bench, which stops at fifty whatever the book says: Sharpness III would be sixty and
			// Unbreaking III a hundred at the greater table.
			int benchStops = Station.FORJA.capacity();
			check(fromBook.percent(Upgrade.FILO) == Math.min(60, benchStops) && fromBook.percent(Upgrade.IRROMPIBLE) == Math.min(100, benchStops)
				&& !menu.getSlot(ForgeMenu.STAR_FIRST).hasItem(),
				"the book should give Filo and Irrompible as far as the bench goes and be used up");
			// Two Filo orbs on an empty star fuse into one.
			ItemStack forgedSword = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().copy();
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 20));
			menu.getSlot(ForgeMenu.STAR_FIRST + 3).set(dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 10));
			check(menu.action() == ForgeMenu.Action.MERGE, "two Filo orbs on an empty star should fuse, got " + menu.action());
			menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_FORGE);
			ItemStack fused = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			var fusedOrb = dev.forja.item.UpgradeOrbItem.orb(fused);
			check(fusedOrb != null && fusedOrb.upgrade() == Upgrade.FILO && fusedOrb.percent() == 30 && !menu.getSlot(ForgeMenu.STAR_FIRST).hasItem(),
				"fusing Filo 20% and 10% orbs should give one Filo 30% orb");
			// Dones: a Maestria 10 item takes a gift for one token.
			ItemStack master = Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO), connection.getServerLevel().registryAccess());
			dev.forja.forge.Mastery.setLevel(master, 10, connection.getServerLevel().registryAccess());
			int armorBefore = master.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.filter(entry -> entry.attribute().equals(Attributes.ARMOR)).mapToInt(entry -> (int) entry.modifier().amount()).sum();
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(master);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(dev.forja.item.SealItem.create(dev.forja.forge.Perk.MINERO));
			check(menu.action() != ForgeMenu.Action.DON, "a miner's seal means nothing to a chestplate");
			ItemStack seals = dev.forja.item.SealItem.create(dev.forja.forge.Perk.BALUARTE);
			seals.setCount(3);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(seals);
			check(menu.action() == ForgeMenu.Action.DON && menu.perk() == dev.forja.forge.Perk.BALUARTE,
				"a bulwark seal should offer its gift to a chestplate, got " + menu.action() + "/" + menu.perk());
			menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_FORGE);
			ItemStack gifted = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			int armorAfter = gifted.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.filter(entry -> entry.attribute().equals(Attributes.ARMOR)).mapToInt(entry -> (int) entry.modifier().amount()).sum();
			log("don: armor " + armorBefore + " -> " + armorAfter + ", perk " + dev.forja.forge.Perk.of(gifted));
			check(dev.forja.forge.Perk.of(gifted) == dev.forja.forge.Perk.BALUARTE && armorAfter == armorBefore + 1
				&& menu.getSlot(ForgeMenu.STAR_FIRST).getItem().getCount() == 2,
				"Baluarte should be engraved, add a point of armor and spend one seal");
			menu.getSlot(ForgeMenu.STAR_FIRST).set(dev.forja.item.SealItem.create(dev.forja.forge.Perk.BALUARTE));
			check(menu.action() != ForgeMenu.Action.DON, "an item that already has a gift takes no other");
			menu.getSlot(ForgeMenu.STAR_FIRST).set(ItemStack.EMPTY);
			// The sword the book was read into is at the bench's ceiling already, so the orb has nothing to give
			// it here: the star must not offer to, and the orb must not be touched.
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(forgedSword);
			menu.getSlot(ForgeMenu.STAR_FIRST + 2).set(fused.copy());
			check(menu.action() != ForgeMenu.Action.BOOK, "an orb must not be offered to a piece already at the bench's ceiling, got " + menu.action());
			// On a bare blade it gives all it holds.
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, MADERA)));
			check(menu.action() == ForgeMenu.Action.BOOK, "an upgrade orb on the star should add its upgrade, got " + menu.action());
			menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_FORGE);
			int fromOrb = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.FILO);
			check(fromOrb == 30 && !menu.getSlot(ForgeMenu.STAR_FIRST + 2).hasItem(), "a Filo 30% orb should put Filo 30% on a bare blade and be used up, got " + fromOrb);

			ServerPlayer player = connection.getServerPlayer();
			for (String id : List.of("plantilla", "forja", "mejora", "cambio", "reparar", "fusion")) {
				var holder = player.level().getServer().getAdvancements().get(dev.forja.Forja.id("forja/" + id));
				check(holder != null && player.getAdvancements().getOrStartProgress(holder).isDone(), "the advancement forja/" + id + " should be granted");
			}
		});
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			var registries = connection.getServerLevel().registryAccess();
			ItemStack master = Assembler.create(ForgeType.ESPADA, List.of(DIAMANTE, MADERA, ORO), registries);
			dev.forja.forge.Mastery.setLevel(master, 10, registries);
			for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
				menu.getSlot(ForgeMenu.STAR_FIRST + i).set(ItemStack.EMPTY);
			}
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(master);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(dev.forja.item.SealItem.create(dev.forja.forge.Perk.FILO_ETERNO));
			check(menu.action() == ForgeMenu.Action.DON, "a mastered sword with a seal should offer a gift, got " + menu.action());
		});
		context.runOnClient(mc -> mc.gui.toastManager().clear());
		context.waitTicks(10);
		context.takeScreenshot("forja_25_don");
		context.waitTicks(10);
		context.takeScreenshot("forja_03b_cambio");
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
				menu.getSlot(ForgeMenu.STAR_FIRST + i).set(ItemStack.EMPTY);
			}
			ItemStack used = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO));
			dev.forja.forge.Mastery.setLevel(used, 3, connection.getServerLevel().registryAccess());
			used.set(ModComponents.MAESTRIA, dev.forja.forge.Mastery.experienceFor(3) + 60);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(used);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.HOJA, DIAMANTE));
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(Assembler.createPart(PartType.MANGO, ORO));
			check(menu.action() == ForgeMenu.Action.SWAP, "a blade and a handle on a sword should swap parts, got " + menu.action());
		});
		context.runOnClient(mc -> mc.gui.toastManager().clear());
		context.waitTicks(10);
		context.takeScreenshot("forja_03c_cambio_diferencias");
		server.runOnServer(s -> connection.getServerPlayer().closeContainer());
		context.waitTicks(5);
	}

	private static void openTable(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos table) {
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ServerLevel level = connection.getServerLevel();
			player.openMenu(level.getBlockState(table).getMenuProvider(level, table));
		});
		context.waitForScreen(ForgeScreen.class);
	}

	/** The guide book: cover and index, a chapter jump, and hover tooltips on materials and upgrades. */
	private static void checkGuideBook(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.gui.setScreen(new GuideBookScreen()));
		context.waitForScreen(GuideBookScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("forja_04_libro_indice");

		// The ways round a book this long, used the way a reader would: a tab down the side, the strip
		// along the foot, and the way back. Each is a click at the place the screen says it is.
		int[] walked = context.computeOnClient(mc -> {
			GuideBookScreen book = (GuideBookScreen) mc.gui.screen();
			int start = book.openSpread();
			boolean tabbed = book.clickAt(book.tabPoint(3));
			int afterTab = book.openSpread();
			boolean stripped = book.clickAt(book.stripPoint(0.5));
			int afterStrip = book.openSpread();
			boolean back = book.back();
			int afterBack = book.openSpread();
			book.back();
			return new int[] {start, tabbed ? 1 : 0, afterTab, stripped ? 1 : 0, afterStrip, back ? 1 : 0, afterBack, book.openSpread(),
				book.chapterPage("eventos") / 2, book.pageCount()};
		});
		log("libro, navegacion: pestana 'mundo' lleva a la doble " + walked[2] + " (eventos esta en " + walked[8] + "), la tira a la "
			+ walked[4] + " de " + (walked[9] / 2) + ", atras vuelve a " + walked[6] + " y luego a " + walked[7]);
		check(walked[1] == 1 && walked[2] == walked[8], "the world tab should open the first chapter of its section, got spread " + walked[2]);
		check(walked[3] == 1 && Math.abs(walked[4] - walked[9] / 4) <= 2, "the middle of the strip should open the middle of the book, got " + walked[4]);
		check(walked[5] == 1 && walked[6] == walked[2], "back should return to where the last jump was made from, got " + walked[6]);
		check(walked[7] == walked[0], "and back again to where it all started, got " + walked[7]);

		// Page by page: nothing in the book clips, so anything that does not fit is drawn outside it,
		// over the world and over the buttons. The index was exactly that — five headings and
		// twenty-four entries dropped onto one page — and it went unnoticed because the only thing
		// checking the layout was a screenshot of one page.
		String[] layout = context.computeOnClient(mc -> {
			GuideBookScreen book = (GuideBookScreen) mc.gui.screen();
			return new String[] {
				String.valueOf(book.pageCount()),
				book.overflowingPages().toString(),
				book.misplacedChapters().toString(),
				book.wideElements().toString(),
				book.elidedElements().toString(),
				book.rawKeys().toString(),
			};
		});
		log("libro: " + layout[0] + " paginas · se salen por abajo " + layout[1]
			+ " · por la derecha " + layout[3] + " · texto cortado " + layout[4]
			+ " · capitulos mal situados " + layout[2] + " · claves sin traducir " + layout[5]);
		check("[]".equals(layout[1]), "no page of the guide may be taller than the page, got " + layout[1]);
		check("[]".equals(layout[2]), "every chapter's index number must land on its own first page, got " + layout[2]);
		// The half the first version of this check missed: a line one pixel tall can still run off the
		// right-hand edge, across the spine and over the other page.
		check("[]".equals(layout[3]), "nothing in the guide may be drawn wider than the page, got " + layout[3]);
		// And fitting is only half of it: a page can fit perfectly and be unreadable, which is what
		// cutting every line to "Cuesta 3 · Da nivel, velocid…" did to the parts chapter.
		check("[]".equals(layout[4]), "nothing in the guide may have its text cut to fit, got " + layout[4]);
		// And a key is not a text. Three of the nine sky events had no description, and the generator's
		// check could not see it because the key is built from the event's id when the page is.
		check("[]".equals(layout[5]), "the guide must never print a translation key, got " + layout[5]);

		// The bestiary, which draws the creatures themselves: three spreads of it, so that a model that
		// fails to draw on a page shows up here and not in somebody's world.
		List<Integer> drawn = context.computeOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).portraitPages());
		check(drawn.size() >= 15, "the bestiary should draw every creature the mod has, found " + drawn.size() + " portraits");
		// Every other one: neighbours are usually the two sides of one spread.
		for (int spread = 0; spread < drawn.size(); spread += 2) {
			int page = drawn.get(spread);
			context.runOnClient(mc -> {
				((GuideBookScreen) mc.gui.screen()).goToPage(page);
				mc.gui.toastManager().clear();
			});
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(6);
			context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_06h_libro_bestiario_%02d", spread)).disableCounterPrefix());
		}

		int materials = context.computeOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).chapterPage("materiales"));
		int upgrades = context.computeOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).chapterPage("mejoras"));
		int tables = context.computeOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).chapterPage("mesas"));
		log("guide chapters: tables page " + (tables + 1) + ", materials page " + (materials + 1) + ", upgrades page " + (upgrades + 1));
		check(tables > 1 && materials > tables && upgrades > materials, "guide chapters should come in order after the index");

		// The chapter the book opens on: the whole loop in six steps.
		int steps = context.computeOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).chapterPage("primeros_pasos"));
		check(steps > 0 && steps < tables, "first steps should be the first chapter after the index, got page " + steps);
		context.runOnClient(mc -> {
			GuideBookScreen book = (GuideBookScreen) mc.gui.screen();
			book.goToPage(steps);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(5);
		context.takeScreenshot("forja_04b_libro_primeros_pasos");

		context.runOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).goToPage(tables));
		context.waitTicks(5);
		context.takeScreenshot("forja_05_libro_mesas");

		context.runOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).goToPage(materials));
		bookHover(context, materials % 2, 40, 58);
		context.waitTicks(5);
		context.takeScreenshot("forja_06_libro_materiales");

		context.runOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).goToPage(upgrades + 1));
		bookHover(context, (upgrades + 1) % 2, 40, 30);
		context.waitTicks(5);
		context.takeScreenshot("forja_06b_libro_mejoras");
		context.getInput().setCursorPos(0, 0);
		// The potential's own chapter: every number on it is read out of forge/Potential.
		int potentialPage = context.computeOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).chapterPage("potencial"));
		check(potentialPage > 0, "the guide should have a chapter on the potential");
		context.runOnClient(mc -> {
			((GuideBookScreen) mc.gui.screen()).goToPage(potentialPage);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(6);
		context.takeScreenshot(TestScreenshotOptions.of("forja_06i_libro_potencial").disableCounterPrefix());
		context.runOnClient(mc -> {
			GuideBookScreen book = (GuideBookScreen) mc.gui.screen();
			book.goToPage(book.chapterPage("mundo"));
			mc.gui.toastManager().clear();
		});
		context.waitTicks(5);
		context.takeScreenshot("forja_06c_libro_mundo");
		context.runOnClient(mc -> {
			GuideBookScreen book = (GuideBookScreen) mc.gui.screen();
			book.goToPage(book.chapterPage("objetos") + 3);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(5);
		context.takeScreenshot("forja_06d_libro_objetos");
		// The foundry walkthrough: the one chapter that is instructions rather than reference.
		int foundry = context.computeOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).chapterPage("fundicion"));
		check(foundry > 0, "the book should carry the foundry walkthrough");
		context.runOnClient(mc -> {
			GuideBookScreen book = (GuideBookScreen) mc.gui.screen();
			book.goToPage(book.chapterPage("fundicion"));
			mc.gui.toastManager().clear();
		});
		context.waitTicks(5);
		context.takeScreenshot("forja_06e_libro_fundicion");
		// The book turns two pages at a time, so the next spread is +2 rather than +1.
		context.runOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).goToPage(foundry + 2));
		context.waitTicks(5);
		context.takeScreenshot("forja_06f_libro_fundicion_2");
		context.runOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).goToPage(foundry + 4));
		context.waitTicks(5);
		context.takeScreenshot("forja_06g_libro_fundicion_3");
		// The page about the reader: Maestria, techniques and the tally of what they have forged.
		int mine = context.computeOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).chapterPage("mi_taller"));
		check(mine > 0, "the book should carry the smith's own page");
		context.runOnClient(mc -> {
			GuideBookScreen book = (GuideBookScreen) mc.gui.screen();
			book.goToPage(mine);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(5);
		context.takeScreenshot("forja_06e_libro_mi_taller");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(5);
	}

	/** Village smith chests and dungeon chests carry templates and forged gear. */
	private static void checkLoot(TestServerContext server, TestServerConnection connection) {
		int[] found = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, connection.getServerPlayer().position())
				.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
			int templates = 0;
			int gear = 0;
			int orbs = 0;
			var endCity = level.getServer().reloadableRegistries().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.END_CITY_TREASURE);
			for (int i = 0; i < 40; i++) {
				for (ItemStack stack : endCity.getRandomItems(params)) {
					orbs += dev.forja.item.UpgradeOrbItem.orb(stack) != null ? 1 : 0;
				}
			}
			log("orbs in 40 end city chests: " + orbs);
			check(orbs > 0, "end city chests should sometimes hold upgrade orbs");
			for (var key : List.of(net.minecraft.world.level.storage.loot.BuiltInLootTables.VILLAGE_TOOLSMITH, net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON)) {
				var table = level.getServer().reloadableRegistries().getLootTable(key);
				for (int i = 0; i < 40; i++) {
					for (ItemStack stack : table.getRandomItems(params)) {
						templates += stack.getItem() instanceof TemplateItem ? 1 : 0;
						gear += stack.has(ModComponents.PARTS) ? 1 : 0;
					}
				}
			}
			return new int[] {templates, gear};
		});
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var random = level.getRandom();
			for (int i = 0; i < 20; i++) {
				ItemStack legend = dev.forja.world.Legends.create(random, level.registryAccess());
				var parts = legend.get(ModComponents.PARTS);
				Upgrades upgrades = legend.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
				check(parts != null && legend.get(DataComponents.RARITY) == net.minecraft.world.item.Rarity.EPIC
					&& dev.forja.world.Legends.ALL.stream().anyMatch(known -> known.id().equals(legend.get(ModComponents.LEYENDA))),
					"a legend should be forged gear of epic rarity marked with its own id");
				check(upgrades.percents().values().stream().anyMatch(percent -> percent == 100) && !legend.isBroken(),
					"a legend should carry a maxed upgrade and still work, got " + upgrades.percents());
			}
			ItemStack dagger = Assembler.create(ForgeType.DAGA, List.of(HIERRO, MADERA), level.registryAccess());
			var toss = UpgradeRecipes.apply(dagger, List.of(new ItemStack(Items.PISTON, 4), new ItemStack(Items.SLIME_BALL, 4), new ItemStack(Items.ENDER_PEARL, 4)), level.registryAccess());
			check(toss != null && toss.upgrade() == Upgrade.LANZACABEZAS, "a dagger should take Lanzacabezas, got " + describe(toss));
			log("leyendas: " + dev.forja.world.Legends.ALL.size() + " named pieces; the dagger can be thrown");
		});
		log("loot in 40 toolsmith + 40 dungeon chests: templates " + found[0] + ", forged gear " + found[1]);
		check(found[0] > 0 && found[1] > 0, "chests should hold templates and forged gear");
	}

	/** The abandoned forge structure places both tables and a chest with its loot table. */
	private static void checkAbandonedForge(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int originX = x - 40;
		int originZ = z - 40;
		// Structures only generate into loaded chunks: stand next to the spot first.
		tp(server, originX - 11.5, y + 2, originZ - 36.5, 0.0F, 25.0F);
		context.waitTicks(40);
		// Each variant has to place its tables, chest and hermit, so check them one by one.
		List<String> variants = List.of("forja", "forja_derrumbada", "forja_en_pie");
		for (int i = 0; i < variants.size(); i++) {
			String variant = variants.get(i);
			int variantX = originX + 40 + i * 12;
			server.runCommand("place template forja:forja_abandonada/" + variant + " " + variantX + " " + y + " " + originZ);
			int[] placed = server.computeOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				int tables = 0;
				int chests = 0;
				for (BlockPos pos : BlockPos.betweenClosed(variantX - 2, y - 2, originZ - 2, variantX + 10, y + 8, originZ + 10)) {
					var state = level.getBlockState(pos);
					tables += state.is(dev.forja.registry.ModBlocks.MESA_DE_FORJA) || state.is(dev.forja.registry.ModBlocks.MESA_DE_PIEZAS) ? 1 : 0;
					chests += level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
						&& dev.forja.world.ForjaLoot.ABANDONED_FORGE.equals(chest.getLootTable()) ? 1 : 0;
				}
				return new int[] {tables, chests};
			});
			check(placed[0] == 2 && placed[1] == 1, "the ruin variant " + variant + " should place both tables and its chest, got " + placed[0] + "/" + placed[1]);
		}
		server.runCommand("gamemode spectator @a");
		tp(server, originX + 58.5, y + 12, originZ - 16.5, -35.0F, 30.0F);
		context.waitTicks(30);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(2);
		context.takeScreenshot("forja_24_ruinas");
		server.runCommand("gamemode survival @a");
		server.runCommand("kill @e[type=villager]");
		server.runCommand(String.format(Locale.ROOT, "place structure forja:forja_abandonada %d %d %d", originX, y, originZ));
		context.waitTicks(10);
		int[] found = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int tables = 0;
			int lootChests = 0;
			// Jigsaw starts sit around the chunk corner, so search the chunks around the target.
			for (BlockPos pos : BlockPos.betweenClosed(originX - 24, y - 6, originZ - 24, originX + 24, y + 8, originZ + 24)) {
				var state = level.getBlockState(pos);
				tables += state.is(dev.forja.registry.ModBlocks.MESA_DE_FORJA) || state.is(dev.forja.registry.ModBlocks.MESA_DE_PIEZAS) ? 1 : 0;
				if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
					&& dev.forja.world.ForjaLoot.ABANDONED_FORGE.equals(chest.getLootTable())) {
					lootChests++;
				}
			}
			var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, connection.getServerPlayer().position())
				.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
			int templates = 0;
			for (ItemStack stack : level.getServer().reloadableRegistries().getLootTable(dev.forja.world.ForjaLoot.ABANDONED_FORGE).getRandomItems(params)) {
				templates += dev.forja.item.TemplateItem.pattern(stack) != null ? 1 : 0;
			}
			return new int[] {tables, lootChests, templates};
		});
		int smithies = server.computeOnServer(s -> {
			var pools = s.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
			int added = 0;
			for (String path : List.of("village/plains/houses", "village/savanna/houses", "village/taiga/houses", "village/snowy/houses", "village/desert/houses")) {
				var pool = pools.getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.TEMPLATE_POOL, net.minecraft.resources.Identifier.withDefaultNamespace(path)));
				String wanted = path.contains("desert") ? "forja_de_aldea_desierto" : "forja_de_aldea";
				added += ((dev.forja.mixin.StructureTemplatePoolAccess) pool.value()).forjaTemplates().stream()
					.anyMatch(element -> element.toString().contains(wanted)) ? 1 : 0;
			}
			return added;
		});
		log("village house pools carrying the smithy: " + smithies);
		check(smithies == 5, "the village smithy should join every village house pool, got " + smithies);
		server.runCommand("place template forja:forja_de_aldea " + (originX + 20) + " " + y + " " + originZ);
		int villageTables = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int tables = 0;
			for (BlockPos pos : BlockPos.betweenClosed(originX + 18, y - 2, originZ - 2, originX + 28, y + 8, originZ + 8)) {
				tables += level.getBlockState(pos).is(dev.forja.registry.ModBlocks.MESA_DE_FORJA) || level.getBlockState(pos).is(dev.forja.registry.ModBlocks.MESA_DE_PIEZAS) ? 1 : 0;
			}
			return tables;
		});
		server.runCommand("place template forja:forja_de_aldea_desierto " + (originX + 20) + " " + y + " " + (originZ + 10));
		int desertTables = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int tables = 0;
			for (BlockPos pos : BlockPos.betweenClosed(originX + 18, y - 2, originZ + 8, originX + 28, y + 8, originZ + 18)) {
				tables += level.getBlockState(pos).is(dev.forja.registry.ModBlocks.MESA_DE_FORJA) || level.getBlockState(pos).is(dev.forja.registry.ModBlocks.MESA_DE_PIEZAS) ? 1 : 0;
			}
			return tables;
		});
		log("village smithy tables: " + villageTables + ", desert variant: " + desertTables);
		check(villageTables == 2 && desertTables == 2, "both smithy templates should hold both tables, got " + villageTables + " and " + desertTables);

		int hermits = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			net.minecraft.world.entity.npc.villager.Villager.class,
			new net.minecraft.world.phys.AABB(new BlockPos(originX, y, originZ)).inflate(32),
			villager -> villager.getVillagerData().profession().is(dev.forja.registry.ModVillagers.FORJADOR)
		).size());
		log("forja abandonada: tables " + found[0] + ", loot chests " + found[1] + ", engraved templates in one roll " + found[2] + ", hermit smiths " + hermits);
		check(hermits == 1, "the abandoned forge should come with its hermit Forjador, got " + hermits);
		check(found[0] == 2 && found[1] == 1, "the abandoned forge should have both tables and its loot chest");
		check(found[2] >= 2, "the abandoned forge chest should hold engraved templates");

		// The empty suit of plate that guards the old forges, and what it does and does not feel.
		int hollows = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			dev.forja.entity.HollowArmor.class, new net.minecraft.world.phys.AABB(new BlockPos(originX, y, originZ)).inflate(32)
		).size());
		float[] plate = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			dev.forja.entity.HollowArmor armor = new dev.forja.entity.HollowArmor(dev.forja.registry.ModEntities.CORAZA, level);
			armor.snapTo(player.getX() + 2.0, player.getY(), player.getZ(), 0.0F, 0.0F);
			armor.setNoAi(true);
			level.addFreshEntity(armor);
			// A shop-bought sword first.
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
			float before = armor.getHealth();
			armor.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
			float plain = before - armor.getHealth();
			armor.setHealth(armor.getMaxHealth());
			armor.invulnerableTime = 0;
			// And then something that came off a star.
			player.setItemInHand(InteractionHand.MAIN_HAND, Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries));
			float second = armor.getHealth();
			armor.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
			float forged = second - armor.getHealth();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			armor.discard();
			return new float[] {plain, forged};
		});
		log("coraza vacia: en la forja " + hollows + ", golpe corriente " + plate[0] + ", golpe forjado " + plate[1]);
		check(hollows >= 1, "the abandoned forge should come with its hollow plate, got " + hollows);

		// The forge in the smith: banked down while he is fresh, violet once he is down to his last
		// quarter, and the flag has to be synced or the client draws the wrong fire.
		boolean[] forge = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			dev.forja.entity.FallenSmith smith = dev.forja.registry.ModEntities.HERRERO_CAIDO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(smith != null, "the boss should be creatable");
			smith.snapTo(player.getX() + 4.0, player.getY(), player.getZ(), 0.0F, 0.0F);
			smith.setNoAi(true);
			level.addFreshEntity(smith);
			for (int i = 0; i < 4; i++) {
				smith.tick();
			}
			boolean calm = smith.isRaging();
			smith.setHealth(smith.getMaxHealth() * 0.2F);
			for (int i = 0; i < 4; i++) {
				smith.tick();
			}
			boolean hot = smith.isRaging();
			// Dropping him that low also sets off the apprentices and the reforge, and both of those keep
			// the forge violet on their own, so healing him only cools it once those windows run out.
			smith.setHealth(smith.getMaxHealth());
			for (int i = 0; i < dev.forja.entity.FallenSmith.REFORGE_TICKS + dev.forja.entity.FallenSmith.RAGE_TICKS * 2 + 20; i++) {
				smith.tick();
				smith.setHealth(smith.getMaxHealth());
			}
			boolean back = smith.isRaging();
			smith.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
			return new boolean[] {calm, hot, back};
		});
		log("fragua del herrero: entero " + forge[0] + ", ultimo cuarto " + forge[1] + ", curado " + forge[2]);
		check(!forge[0], "the forge should be banked down while he is still fresh");
		check(forge[1], "the forge should run violet in his last quarter");
		check(!forge[2], "and go back to orange once he is healed and the heavy move has worn off");

		// Every clip the smith asks for has to be in the file, including the two the fire runs on.
		java.util.Set<String> clips = context.computeOnClient(mc -> {
			var resource = mc.getResourceManager().getResource(dev.forja.Forja.id("geckolib/animations/entity/herrero_caido.animation.json"));
			check(resource.isPresent(), "the smith should have an animation file");
			try (var reader = resource.get().openAsReader()) {
				var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
				return new java.util.HashSet<>(json.keySet());
			} catch (java.io.IOException failure) {
				throw new IllegalStateException(failure);
			}
		});
		log("animaciones del herrero: " + new java.util.TreeSet<>(clips));
		// The light through the cracks has to follow the forge. It is on its own bones, one pair per
		// crack, and if a clip forgets one of them that coal stays orange while the rest goes violet.
		java.util.Map<String, java.util.Set<String>> fireBones = context.computeOnClient(mc -> {
			var resource = mc.getResourceManager().getResource(dev.forja.Forja.id("geckolib/animations/entity/herrero_caido.animation.json"));
			check(resource.isPresent(), "the smith should have an animation file");
			java.util.Map<String, java.util.Set<String>> out = new java.util.HashMap<>();
			try (var reader = resource.get().openAsReader()) {
				var animations = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
				for (String clip : List.of("fire_calm", "fire_rage", "fire_flash", "fire_flash_hot")) {
					out.put(clip, new java.util.HashSet<>(animations.getAsJsonObject(clip).getAsJsonObject("bones").keySet()));
				}
			} catch (java.io.IOException failure) {
				throw new IllegalStateException(failure);
			}
			return out;
		});
		for (String clip : List.of("fire_calm", "fire_rage", "fire_flash", "fire_flash_hot")) {
			for (String coal : List.of("coal_chest", "coal_chest_left", "coal_hips", "coal_thigh", "coal_shoulder")) {
				check(fireBones.get(clip).contains(coal), clip + " should scale the '" + coal + "' bone");
				check(fireBones.get(clip).contains(coal + "_hot"), clip + " should scale the '" + coal + "_hot' bone");
			}
		}
		log("brasas de las grietas: los 10 huesos en las 4 animaciones de fuego");
		for (String clip : List.of("idle", "walk", "slam", "roar", "strike", "fire_calm", "fire_rage", "fire_flash", "fire_flash_hot")) {
			check(clips.contains(clip), "the smith should have a '" + clip + "' animation");
		}
		check(plate[1] > plate[0] * 2.0F, "forged steel should bite far harder than shop steel, got "
			+ plate[1] + " against " + plate[0]);

		// Breaking one does not kill what was inside it: if another suit is standing close it moves in,
		// and that one comes back up healed and angry.
		float[] jump = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			dev.forja.entity.HollowArmor dying = new dev.forja.entity.HollowArmor(dev.forja.registry.ModEntities.CORAZA, level);
			dying.snapTo(player.getX() + 3.0, player.getY(), player.getZ(), 0.0F, 0.0F);
			dying.setNoAi(true);
			level.addFreshEntity(dying);
			dev.forja.entity.HollowArmor neighbour = new dev.forja.entity.HollowArmor(dev.forja.registry.ModEntities.CORAZA, level);
			neighbour.snapTo(player.getX() + 6.0, player.getY(), player.getZ(), 0.0F, 0.0F);
			neighbour.setNoAi(true);
			level.addFreshEntity(neighbour);
			neighbour.setHealth(20.0F);
			float before = neighbour.getHealth();
			dying.die(level.damageSources().playerAttack(player));
			float after = neighbour.getHealth();
			boolean fast = neighbour.hasEffect(net.minecraft.world.effect.MobEffects.SPEED);
			boolean strong = neighbour.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
			// And with nothing in reach it just goes out, leaving the survivor alone.
			neighbour.setHealth(20.0F);
			dev.forja.entity.HollowArmor alone = new dev.forja.entity.HollowArmor(dev.forja.registry.ModEntities.CORAZA, level);
			alone.snapTo(player.getX() + 60.0, player.getY(), player.getZ(), 0.0F, 0.0F);
			alone.setNoAi(true);
			level.addFreshEntity(alone);
			alone.die(level.damageSources().playerAttack(player));
			float untouched = neighbour.getHealth();
			for (dev.forja.entity.HollowArmor armor : List.of(dying, neighbour, alone)) {
				armor.discard();
			}
			level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(80)).forEach(net.minecraft.world.entity.Entity::discard);
			return new float[] {after - before, fast && strong ? 1.0F : 0.0F, untouched - 20.0F};
		});
		log("alma de la coraza: cura al vecino " + jump[0] + ", lo enfurece " + (jump[1] > 0) + ", sin vecino cerca " + jump[2]);
		check(jump[0] >= dev.forja.entity.HollowArmor.SOUL_HEAL, "the soul should heal the suit it moves into, got " + jump[0]);
		check(jump[1] > 0, "the suit it moves into should come back faster and stronger");
		check(jump[2] == 0.0F, "a soul with nothing in reach should not heal anything, got " + jump[2]);

		// The automaton the abandoned forge comes with, and the two new structures.
		int automatons = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			dev.forja.entity.ForgeAutomaton.class, new net.minecraft.world.phys.AABB(new BlockPos(originX, y, originZ)).inflate(32)
		).size());
		log("forja abandonada: automatas " + automatons);
		check(automatons >= 1, "the abandoned forge should come with its automaton, got " + automatons);

		int workshopZ = originZ + 40;
		server.runCommand(String.format(Locale.ROOT, "place template forja:taller_de_montana/taller %d %d %d", originX, y, workshopZ));
		int[] workshop = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int saddleries = 0;
			int chests = 0;
			for (BlockPos pos : BlockPos.betweenClosed(originX - 2, y - 2, workshopZ - 2, originX + 15, y + 9, workshopZ + 15)) {
				saddleries += level.getBlockState(pos).is(dev.forja.registry.ModBlocks.MESA_DE_TALABARTERIA) ? 1 : 0;
				chests += level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.CHEST) ? 1 : 0;
			}
			return new int[] {saddleries, chests};
		});
		int horses = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			net.minecraft.world.entity.animal.equine.Horse.class,
			new net.minecraft.world.phys.AABB(new BlockPos(originX, y, workshopZ)).inflate(24)
		).size());
		log("taller de montana: talabarterias " + workshop[0] + ", cofres " + workshop[1] + ", caballos " + horses);
		check(workshop[0] == 1, "the mountain workshop should hold the one saddlery table, got " + workshop[0]);
		check(workshop[1] >= 1, "and its chest");
		check(horses >= 1, "and the horse it keeps, got " + horses);

		int fraguaZ = workshopZ + 30;
		server.runCommand(String.format(Locale.ROOT, "place template forja:fragua_caida/fragua %d %d %d", originX, y, fraguaZ));
		int[] fallen = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int forges = 0;
			int tables = 0;
			for (BlockPos pos : BlockPos.betweenClosed(originX - 2, y - 2, fraguaZ - 2, originX + 17, y + 11, fraguaZ + 17)) {
				forges += level.getBlockState(pos).is(dev.forja.registry.ModBlocks.FRAGUA_APAGADA) ? 1 : 0;
				tables += level.getBlockState(pos).is(dev.forja.registry.ModBlocks.MESA_DE_FORJA)
					|| level.getBlockState(pos).is(dev.forja.registry.ModBlocks.MESA_DE_PIEZAS) ? 1 : 0;
			}
			return new int[] {forges, tables};
		});
		int guards = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			dev.forja.entity.ForgeAutomaton.class, new net.minecraft.world.phys.AABB(new BlockPos(originX, y, fraguaZ)).inflate(24)
		).size());
		log("fragua caida: fraguas apagadas " + fallen[0] + ", mesas " + fallen[1] + ", guardias " + guards);
		check(fallen[0] == 1, "the fallen forge should hold exactly one dead forge, got " + fallen[0]);
		check(fallen[1] == 2, "and both of his tables, got " + fallen[1]);
		check(guards == 2, "and the two automatons guarding it, got " + guards);
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(new BlockPos(originX, y, workshopZ)).inflate(64);
			level.getEntitiesOfClass(dev.forja.entity.ForgeAutomaton.class, around).forEach(net.minecraft.world.entity.Entity::discard);
			level.getEntitiesOfClass(net.minecraft.world.entity.animal.equine.Horse.class, around).forEach(net.minecraft.world.entity.Entity::discard);
		});
		// A spectator camera keeps the player from falling and hides the HUD for the shot.
		server.runCommand("gamemode spectator @a");
		tp(server, originX - 11.5, y + 7, originZ - 22.5, 0.0F, 32.0F);
		context.waitTicks(20);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(2);
		context.takeScreenshot("forja_21_forja_abandonada");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var villager = new net.minecraft.world.entity.npc.villager.Villager(net.minecraft.world.entity.EntityTypes.VILLAGER, level);
			villager.snapTo(player.getX(), player.getY(), player.getZ() + 3.5, 180.0F, 0.0F);
			villager.setYHeadRot(180.0F);
			villager.setNoAi(true);
			villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(dev.forja.registry.ModVillagers.FORJADOR)).withLevel(3));
			level.addFreshEntity(villager);
		});
		tp(server, originX - 11.5, y + 1, originZ - 36.5, 0.0F, 5.0F);
		context.waitTicks(20);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(2);
		context.takeScreenshot("forja_22_forjador");
		tp(server, originX + 14.5, y + 5, originZ - 5.5, -45.0F, 22.0F);
		context.waitTicks(20);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(2);
		context.takeScreenshot("forja_23_forja_de_aldea");
		server.runCommand("kill @e[type=villager]");
		server.runCommand("gamemode survival @a");
	}

	private static void bookHover(ClientGameTestContext context, int side, int dx, int dy) {
		double[] point = context.computeOnClient(mc -> {
			double[] gui = ((GuideBookScreen) mc.gui.screen()).contentPoint(side, dx, dy);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(point[0], point[1]);
	}

	/** Multidisparo, Zancada, Ejecucion, Nutricion, Purificacion, Luz and Absorcion doing their job in the world. */
	private static void checkNewUpgrades(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int areaX = x + 60;
		tp(server, areaX + 0.5, y, z + 0.5, 0.0F, 0.0F);
		context.waitTicks(10);

		int arrows = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			HolderLookup.Provider registries = level.registryAccess();
			ItemStack bow = maxed(connection, Assembler.create(ForgeType.ARCO, List.of(HIERRO, CUERO, MADERA), registries), new ItemStack(Items.ARROW, 4), new ItemStack(Items.FEATHER, 4));
			int multishot = EnchantmentHelper.getItemEnchantmentLevel(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MULTISHOT), bow);
			check(multishot == 1, "Multidisparo 100% should be Multishot I, got " + multishot);
			player.getInventory().clearContent();
			player.getInventory().setItem(0, bow);
			player.getInventory().setItem(8, new ItemStack(Items.ARROW, 16));
			player.getInventory().setSelectedSlot(0);
			bow.releaseUsing(level, player, bow.getUseDuration(player) - 30);
			return level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.Arrow.class, player.getBoundingBox().inflate(4)).size();
		});
		log("multidisparo: arrows fired " + arrows);
		check(arrows >= 3, "a Multidisparo bow should fire three arrows, got " + arrows);
		server.runCommand("kill @e[type=arrow]");

		server.runOnServer(s -> connection.getServerPlayer().setItemSlot(
			EquipmentSlot.FEET, maxed(connection, Assembler.create(ForgeType.BOTAS, List.of(HIERRO, CUERO)), new ItemStack(Items.RABBIT_HIDE, 10))
		));
		// Equipment attributes are applied on the player's next tick.
		context.waitTicks(3);
		server.runCommand("kill @e[type=arrow]");
		int bolts = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			HolderLookup.Provider registries = level.registryAccess();
			check(ForgeType.match(List.of(PartType.BRAZOS_ARCO, PartType.CUERDA, PartType.MANGO, PartType.GUARDA)) == ForgeType.BALLESTA, "limbs + string + handle + guard is a crossbow");
			ItemStack crossbow = maxed(connection, Assembler.create(ForgeType.BALLESTA, List.of(DIAMANTE, CUERO, MADERA, HIERRO), registries), new ItemStack(Items.TRIPWIRE_HOOK, 10));
			int quickCharge = EnchantmentHelper.getItemEnchantmentLevel(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.QUICK_CHARGE), crossbow);
			check(crossbow.getItem() instanceof net.minecraft.world.item.CrossbowItem && quickCharge == 3, "the forged crossbow should be a crossbow with Quick Charge III, got " + quickCharge);
			crossbow.set(DataComponents.CHARGED_PROJECTILES, net.minecraft.world.item.component.ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
			player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
			crossbow.use(level, player, InteractionHand.MAIN_HAND);
			return level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.Arrow.class, player.getBoundingBox().inflate(4)).size();
		});
		log("ballesta: bolts fired " + bolts);
		check(bolts >= 1, "a loaded forged crossbow should fire");
		server.runCommand("kill @e[type=arrow]");

		int reaped = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			check(ForgeType.match(List.of(PartType.HOJA, PartType.MANGO, PartType.ATADURA)) == ForgeType.GUADANA, "blade + handle + binding is a scythe");
			ItemStack scythe = Assembler.create(ForgeType.GUADANA, List.of(HIERRO, MADERA, CUERO), level.registryAccess());
			check(scythe.is(net.minecraft.tags.ItemTags.SWORDS), "the scythe should sweep like a sword");
			double reach = scythe.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.filter(entry -> entry.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE)).mapToDouble(entry -> entry.modifier().amount()).sum();
			check(reach > 0.7, "the scythe should reach further, got " + reach);
			BlockPos field = new BlockPos(x + 64, y, z + 6);
			for (BlockPos pos : BlockPos.betweenClosed(field.offset(-1, 0, -1), field.offset(1, 0, 1))) {
				level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), 3);
				level.setBlock(pos, ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
			}
			player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
			player.gameMode.useItemOn(player, level, scythe, InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(field), net.minecraft.core.Direction.UP, field, false));
			int replanted = 0;
			for (BlockPos pos : BlockPos.betweenClosed(field.offset(-1, 0, -1), field.offset(1, 0, 1))) {
				var state = level.getBlockState(pos);
				replanted += state.is(Blocks.WHEAT) && ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getAge(state) == 0 ? 1 : 0;
			}
			return replanted;
		});
		int gathered = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			// Telekinesis alone should pick a harvest up, not just what you mine.
			ItemStack hoe = Assembler.create(ForgeType.AZADA, List.of(HIERRO, MADERA, HIERRO), registries);
			hoe = UpgradeRecipes.upgraded(hoe, ForgeType.AZADA, Upgrade.COSECHADOR, 100, registries);
			hoe = UpgradeRecipes.upgraded(hoe, ForgeType.AZADA, Upgrade.TELEQUINESIS, 100, registries);
			check(dev.forja.upgrade.Synergy.SEGADOR.active(hoe), "a maxed hoe should wake Segador");
			BlockPos field = new BlockPos(x + 64, y, z + 10);
			for (BlockPos pos : BlockPos.betweenClosed(field.offset(-1, 0, -1), field.offset(1, 0, 1))) {
				level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), 3);
				level.setBlock(pos, ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
			}
			player.getInventory().clearContent();
			player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
			player.gameMode.useItemOn(player, level, hoe, InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(field), net.minecraft.core.Direction.UP, field, false));
			int wheat = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				wheat += player.getInventory().getItem(i).is(Items.WHEAT) ? player.getInventory().getItem(i).getCount() : 0;
			}
			player.getInventory().clearContent();
			return wheat;
		});
		log("segador: trigo recogido sin tocar el suelo " + gathered);
		check(gathered > 0, "harvesting with Telekinesis should put the crop in the inventory, got " + gathered);

		log("guadana: reaped and replanted " + reaped + " wheat");
		check(reaped == 9, "a scythe should reap and replant 3x3 wheat, got " + reaped);

		double step = server.computeOnServer(s -> connection.getServerPlayer().getAttributeValue(Attributes.STEP_HEIGHT));
		log("zancada: step height " + step);
		check(step >= 1.05, "Zancada boots should step a full block, got " + step);

		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			HolderLookup.Provider registries = level.registryAccess();
			check(ForgeType.match(List.of(PartType.MEMBRANA, PartType.FORRO)) == ForgeType.ALAS, "membrane + lining is a pair of wings");
			ItemStack light = Assembler.create(ForgeType.ALAS, List.of(ORO, CUERO), registries);
			ItemStack heavy = Assembler.create(ForgeType.ALAS, List.of(OBSIDIANA, CUERO), registries);
			check(light.has(DataComponents.GLIDER) && net.minecraft.world.entity.LivingEntity.canGlideUsing(light, EquipmentSlot.CHEST),
				"forged wings should glide from the chest slot");
			float lightGlide = ForgeStats.glideRatio(ORO);
			float heavyGlide = ForgeStats.glideRatio(OBSIDIANA);
			double lightGravity = light.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.filter(entry -> entry.attribute().equals(Attributes.GRAVITY)).mapToDouble(entry -> entry.modifier().amount()).sum();
			double heavyGravity = heavy.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.filter(entry -> entry.attribute().equals(Attributes.GRAVITY)).mapToDouble(entry -> entry.modifier().amount()).sum();
			log("alas: planeo oro " + lightGlide + " (gravedad " + lightGravity + "), obsidiana " + heavyGlide + " (gravedad " + heavyGravity + "), durabilidad " + light.getMaxDamage() + "/" + heavy.getMaxDamage());
			check(lightGlide > heavyGlide && lightGravity < 0.0 && heavyGravity > 0.0, "light membranes should glide further than heavy ones");
			check(heavy.getMaxDamage() > light.getMaxDamage(), "an obsidian membrane should outlast a golden one");
			ItemStack fast = maxed(connection, light, new ItemStack(Items.ELYTRA, 2));
			check(fast.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.AERODINAMICA) == 100,
				"two elytras should max Aerodinamica on wings");
			check(dev.forja.forge.Perk.forType(ForgeType.ALAS).contains(dev.forja.forge.Perk.VIAJERO), "wings should take the Viajero gift");

			// The reserve: a stone membrane barely gets you off a roof, gold crosses a valley.
			int lightTicks = dev.forja.forge.Flight.maxTicks(light);
			int heavyTicks = dev.forja.forge.Flight.maxTicks(heavy);
			log("vuelo: oro " + lightTicks / 20.0F + " s, obsidiana " + heavyTicks / 20.0F + " s, piedra " + ForgeStats.flightSeconds(PIEDRA) + " s");
			check(lightTicks > heavyTicks, "a light membrane should hold more flight than a heavy one");
			check(ForgeStats.flightSeconds(PIEDRA) < ForgeStats.flightSeconds(ORO), "stone wings should give out long before golden ones");
			check(!dev.forja.forge.Flight.exhausted(light) && dev.forja.forge.Flight.canFly(light), "fresh wings should be able to fly");
			ItemStack spent = light.copy();
			spent.set(ModComponents.VUELO, 0);
			check(dev.forja.forge.Flight.exhausted(spent) && !dev.forja.forge.Flight.canFly(spent), "wings with an empty reserve should stop gliding");
			// Aerodinamica makes the reserve go a very long way. It must never make it go forever: an
			// empty pair is an empty pair no matter what is on it, or the best wings in the mod are a
			// creative-mode fly button.
			ItemStack tireless = fast.copy();
			tireless.set(ModComponents.VUELO, 0);
			check(dev.forja.forge.Flight.exhausted(tireless) && !dev.forja.forge.Flight.canFly(tireless),
				"Aerodinamica at 100% must not make an empty pair of wings fly");
			check(Upgrade.glideSpeed(1.0F, lightTicks / 20.0F) > Upgrade.glideSpeed(1.0F, heavyTicks / 20.0F),
				"the more reserve Aerodinamica trades, the faster the wings");
			check(Upgrade.PROPULSION.appliesTo(ForgeType.ALAS), "wings should take Propulsion");

			// A forged shield covers you the instant it goes up; the plate pays for it in parry window.
			ItemStack shield = Assembler.create(ForgeType.ESCUDO, List.of(HIERRO, HIERRO, CUERO), registries);
			check(shield.get(DataComponents.BLOCKS_ATTACKS).blockDelaySeconds() == 0.0F, "a forged shield should block from the first tick");
			int window = dev.forja.upgrade.CombatUpgrades.parryWindow(shield);
			log("parada: ventana " + window + " ticks");
			check(window >= 3, "the parry window should be a few ticks wide, got " + window);

			// A perfect press lifts every number of the piece.
			ItemStack plain = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			dev.forja.part.ForgedParts swordParts = plain.get(ModComponents.PARTS);
			ItemStack pressed = plain.copy();
			dev.forja.forge.Quality.markPerfect(pressed);
			float plainDamage = ForgeStats.sheet(plain, swordParts).attackDamage;
			float pressedDamage = ForgeStats.sheet(pressed, swordParts).attackDamage;
			log("forja perfecta: dano " + plainDamage + " -> " + pressedDamage + ", durabilidad " + plain.getMaxDamage() + " -> " + pressed.getMaxDamage());
			check(pressedDamage > plainDamage, "a perfect forge should raise the damage");
			check(dev.forja.forge.Quality.perfect(pressed) && !dev.forja.forge.Quality.perfect(plain), "only the pressed one is perfect");

			// Temple: hot for a minute, then whatever put it out stays in it.
			check(!dev.forja.forge.Temple.hot(plain, level.getGameTime()), "an old piece should not be hot");
			ItemStack fresh = plain.copy();
			dev.forja.forge.Temple.markHot(fresh, level);
			check(dev.forja.forge.Temple.hot(fresh, level.getGameTime()), "a freshly forged piece should be hot");
			fresh.set(ModComponents.TEMPLE, dev.forja.forge.Temple.AGUA.id());
			check(dev.forja.forge.Temple.of(fresh) == dev.forja.forge.Temple.AGUA && !dev.forja.forge.Temple.hot(fresh, level.getGameTime()),
				"a quenched piece keeps its quench and cools down");

			// The smith grows with what they make, and the ladder gets steeper.
			check(dev.forja.forge.SmithLevel.levelOf(0) == 0 && dev.forja.forge.SmithLevel.levelOf(40) == 1
				&& dev.forja.forge.SmithLevel.levelOf(160) == 2 && dev.forja.forge.SmithLevel.levelOf(999999) == dev.forja.forge.SmithLevel.MAX_LEVEL,
				"smith levels should follow the experience curve");
			check(dev.forja.forge.SmithLevel.upgradeBonus(player) >= 0, "the upgrade bonus should never be negative");

			// Frenesi: a plain upgrade climbs further than an expensive one.
			log("frenesi: veta " + Upgrade.VETA.frenzyCeiling() + ", lanzacabezas " + Upgrade.LANZACABEZAS.frenzyCeiling());
			check(Upgrade.VETA.frenzyCeiling() > Upgrade.LANZACABEZAS.frenzyCeiling(), "cheap upgrades should gain more from a combo");
			check(dev.forja.upgrade.Frenzy.level(player) == 0.0F, "a player who has not swung has no combo");

			// Bleeding is a real effect, and Desgarro decides how deep it goes.
			check(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.containsKey(dev.forja.Forja.id("sangrado")), "bleeding should be registered");
			check(Upgrade.bleedStacks(1.0F) > Upgrade.bleedStacks(0.0F), "Desgarro should allow more wounds");

			// Throwing whole weapons, and the shield that comes back.
			check(dev.forja.upgrade.WeaponThrow.throwable(ForgeType.HACHA) && dev.forja.upgrade.WeaponThrow.throwable(ForgeType.DAGA)
				&& !dev.forja.upgrade.WeaponThrow.throwable(ForgeType.ESPADA), "axes and daggers are the throwable ones");
			check(Upgrade.shieldBounces(1.0F) == 3 && Upgrade.shieldBounces(0.0F) == 0, "a maxed Bumeran should shove three");
			check(Upgrade.RETORNO.appliesTo(ForgeType.HACHA) && Upgrade.BUMERAN.appliesTo(ForgeType.ESCUDO), "the throw upgrades go on the right gear");

			// The two new weapons, and the pacts.
			ItemStack flail = Assembler.create(ForgeType.MANGUAL, List.of(HIERRO, HIERRO, MADERA), registries);
			ItemStack fists = Assembler.create(ForgeType.GUANTELETES, List.of(CUERO, HIERRO, ORO), registries);
			check(ForgeType.match(List.of(PartType.BOLA, PartType.CADENA, PartType.MANGO)) == ForgeType.MANGUAL, "ball + chain + handle is a flail");
			// Three parts now: the mitt carries the band and the band is riveted to it.
			check(ForgeType.match(List.of(PartType.MANOPLA, PartType.NUDILLOS, PartType.REMACHE)) == ForgeType.GUANTELETES,
				"mitt + knuckles + rivets are gauntlets");
			check(ForgeType.match(List.of(PartType.MANOPLA, PartType.NUDILLOS)) != ForgeType.GUANTELETES,
				"and two of the three are not");
			double flailSpeed = attackSpeedOf(flail);
			double fistSpeed = attackSpeedOf(fists);
			log("armas nuevas: mangual " + flailSpeed + " ataques/s, guanteletes " + fistSpeed + " ataques/s");
			check(fistSpeed > flailSpeed, "gauntlets should swing far faster than a flail");
			check(Upgrade.ATURDIMIENTO.appliesTo(ForgeType.MANGUAL) && Upgrade.RAFAGA.appliesTo(ForgeType.GUANTELETES), "the new upgrades go on the new weapons");
			check(Upgrade.flailSplash(0.0F) > 0.0F, "a flail should splash even with no upgrade");

			// Pactos: more bite, and more taken away than given.
			ItemStack pact = maxed(connection, plain.copy(), new ItemStack(Items.GLASS, 4));
			check(pact.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.PACTO_DE_VIDRIO) == 100, "four glass should seal the pact of glass");
			log("pacto de vidrio: dano " + damageOf(pact) + " (antes " + damageOf(plain) + "), durabilidad " + pact.getMaxDamage() + " (antes " + plain.getMaxDamage() + ")");
			check(damageOf(pact) > damageOf(plain), "the pact should raise the damage");
			check(pact.getMaxDamage() < plain.getMaxDamage() * 0.6, "and take more durability than it gives in damage");

			// Arrows: a handful at a time, and the fletching is what makes them quick.
			check(ForgeType.match(List.of(PartType.PUNTA_FLECHA, PartType.EMPLUMADO)) == ForgeType.FLECHA, "tip + fletching is an arrow");
			ItemStack forgedArrows = Assembler.evaluate(List.of(
				Assembler.createPart(PartType.PUNTA_FLECHA, HIERRO), Assembler.createPart(PartType.EMPLUMADO, CUERO)
			), registries).stack();
			log("flechas: " + forgedArrows.getCount() + " por forja, dano " + ForgeStats.arrowDamage(HIERRO) + ", pila " + forgedArrows.getMaxStackSize());
			check(forgedArrows.getCount() == Assembler.ARROWS_PER_FORGE, "one set of parts should make a handful of arrows");
			check(forgedArrows.getMaxStackSize() > 1 && !forgedArrows.has(DataComponents.MAX_DAMAGE), "arrows stack and never wear out");
			check(forgedArrows.is(net.minecraft.tags.ItemTags.ARROWS), "forged arrows must count as ammunition");
			check(ForgeStats.arrowDamage(DIAMANTE) > ForgeStats.arrowDamage(PIEDRA), "a better tip should hit harder");
			check(Upgrade.PUNTA_ENVENENADA.appliesTo(ForgeType.FLECHA) && !Upgrade.PUNTA_ENVENENADA.appliesTo(ForgeType.ESPADA), "arrow upgrades stay on arrows");

			// Talismanes: one stone works, the rest are dead weight.
			ItemStack quartz = dev.forja.item.Talisman.CUARZO.create();
			ItemStack diamond = dev.forja.item.Talisman.DIAMANTE.create();
			check(dev.forja.item.Talisman.of(quartz) == dev.forja.item.Talisman.CUARZO, "a talisman should know its own stone");
			player.getInventory().clearContent();
			player.getInventory().setItem(0, quartz);
			player.getInventory().setItem(1, diamond);
			check(dev.forja.item.Talisman.active(player) == dev.forja.item.Talisman.CUARZO, "only the first talisman in the bag counts");
			player.getInventory().clearContent();

			// The tool belt: four digging tools, and nothing else.
			ItemStack belt = new ItemStack(dev.forja.registry.ModItems.CINTURON);
			check(dev.forja.item.ToolBeltItem.fits(Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, HIERRO), registries)), "a pick belongs on the belt");
			check(!dev.forja.item.ToolBeltItem.fits(plain), "a sword does not");
			check(dev.forja.item.ToolBeltItem.tools(belt).isEmpty() && belt.getMaxDamage() > 0, "a fresh belt is empty and wears out");

			// Alloys: the same two metals are different things at different heat.
			check(dev.forja.forge.Alloys.ALL.size() >= 8, "there should be at least eight alloys");
			List<ItemStack> bronzeInputs = List.of(new ItemStack(Items.COPPER_INGOT, 2), new ItemStack(Items.IRON_INGOT, 1));
			check(dev.forja.forge.Alloys.match(bronzeInputs, dev.forja.forge.Alloys.Heat.FRIA) == null, "a cold table melts nothing");
			dev.forja.forge.Alloys.Recipe bronze = dev.forja.forge.Alloys.match(bronzeInputs, dev.forja.forge.Alloys.Heat.TEMPLADA);
			check(bronze != null && bronze.id().equals("bronce"), "copper and iron over a fire are bronze");
			check(dev.forja.forge.Alloys.match(List.of(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.COAL, 2)), dev.forja.forge.Alloys.Heat.TEMPLADA) == null,
				"steel needs more than a campfire");
			dev.forja.forge.Alloys.Recipe steel = dev.forja.forge.Alloys.match(List.of(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.COAL, 2)), dev.forja.forge.Alloys.Heat.FUNDIDA);
			check(steel != null && steel.id().equals("acero"), "and a hotter table still makes it");
			log("aleaciones: " + dev.forja.forge.Alloys.ALL.size() + ", bronce " + bronze.output() + " por tanda, acero " + steel.output());
			for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.forge.Alloys.ALL) {
				ItemStack ingot = recipe.result();
				check(!ingot.isEmpty(), "every alloy needs its ingot: " + recipe.id());
				// Refractory steel is the one alloy that is not gear metal: it exists to be cut into
				// moulds, and making it a material would put it in the armour texture matrix for nothing.
				if (dev.forja.forge.Alloys.SHAPING_ONLY.contains(recipe.id())) {
					check(dev.forja.material.ForgeMaterial.fromInput(ingot) == null,
						"a shaping-only alloy must not be a gear material: " + recipe.id());
					continue;
				}
				dev.forja.material.ForgeMaterial material = dev.forja.material.ForgeMaterial.fromInput(ingot);
				check(material != null && material.getSerializedName().equals(recipe.id()), "every alloy ingot must be a material: " + recipe.id());
				check(!Assembler.create(ForgeType.ESPADA, List.of(material, material, material), registries).isEmpty(),
					"and the star should forge with it: " + recipe.id());
			}

			// Barding and wolf armor: only the saddlery makes them, and they go on the body slot.
			check(ForgeType.match(List.of(PartType.PLACA_BARDA, PartType.FORRO)) == ForgeType.BARDA, "barding plate + lining is barding");
			ItemStack barding = Assembler.create(ForgeType.BARDA, List.of(DIAMANTE, CUERO), registries);
			ItemStack wolfArmor = Assembler.create(ForgeType.ARMADURA_DE_LOBO, List.of(HIERRO, CUERO), registries);
			var equippable = barding.get(DataComponents.EQUIPPABLE);
			check(equippable != null && equippable.slot() == EquipmentSlot.BODY, "barding goes on the body slot");
			check(equippable.allowedEntities().isPresent(), "and only on something that can wear it");
			double bardingArmor = barding.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.filter(entry -> entry.attribute().equals(Attributes.ARMOR)).mapToDouble(entry -> entry.modifier().amount()).sum();
			log("montura: barda de diamante " + bardingArmor + " de armadura, " + barding.getMaxDamage() + " de durabilidad; lobo " + wolfArmor.getMaxDamage());
			check(bardingArmor >= 8.0, "diamond barding should be worth real armor, got " + bardingArmor);
			check(wolfArmor.get(DataComponents.EQUIPPABLE).slot() == EquipmentSlot.BODY, "wolf armor goes on the body slot too");
			check(dev.forja.menu.Station.TALABARTERIA.forMounts() && !dev.forja.menu.Station.FORJA.forMounts(),
				"only the saddlery works on mounts");

			// Star iron and the sky: the events, their upgrades and the flask that keeps them.
			check(dev.forja.material.ForgeMaterial.ESTELAR.trait == dev.forja.material.ForgeMaterial.Trait.ESTELAR, "star iron carries the stellar trait");
			check(dev.forja.material.ForgeMaterial.fromInput(new ItemStack(dev.forja.registry.ModItems.HIERRO_ESTELAR)) == dev.forja.material.ForgeMaterial.ESTELAR,
				"a star iron ingot should count as its material");
			java.util.Set<Upgrade> eventUpgrades = new java.util.HashSet<>();
			for (dev.forja.world.WorldEvents event : dev.forja.world.WorldEvents.values()) {
				check(event.upgrade.options.isEmpty(), "an event upgrade must not be feedable with items: " + event.upgrade.id());
				check(eventUpgrades.add(event.upgrade), "two events must not carry the same upgrade: " + event.upgrade.id());
			}
			log("eventos: " + dev.forja.world.WorldEvents.values().length + ", cada uno con su mejora");

			// The three pictures for the wall, and the tag that lets them turn up on their own.
			var paintings = level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
			int ours = (int) paintings.listElementIds().filter(key -> key.identifier().getNamespace().equals("forja")).count();
			int placeable = (int) paintings.getOrThrow(net.minecraft.tags.TagKey.create(
				Registries.PAINTING_VARIANT, net.minecraft.resources.Identifier.withDefaultNamespace("placeable")
			)).stream().filter(holder -> holder.unwrapKey().orElseThrow().identifier().getNamespace().equals("forja")).count();
			log("cuadros: " + ours + " propios, " + placeable + " en la etiqueta de colocables");
			check(ours == 3, "the mod should add its three paintings, got " + ours);
			check(placeable == 3, "and all three should be placeable, got " + placeable);
			check(dev.forja.world.WorldEvents.active(level) == null, "no event should be running in a fresh world");
			dev.forja.world.WorldEvents.start(level, dev.forja.world.WorldEvents.AURORA);
			check(dev.forja.world.WorldEvents.active(level) == dev.forja.world.WorldEvents.AURORA, "starting an event should make it the one running");
			log("evento: " + dev.forja.world.WorldEvents.AURORA.id() + " da " + dev.forja.world.WorldEvents.AURORA.upgrade.id());

			// The three spawn eggs, so the mobs can be looked at without hunting for a ruin.
			for (net.minecraft.world.item.Item egg : List.of(dev.forja.registry.ModItems.HUEVO_HERRERO_CAIDO,
				dev.forja.registry.ModItems.HUEVO_AUTOMATA, dev.forja.registry.ModItems.HUEVO_CORAZA)) {
				ItemStack stack = new ItemStack(egg);
				var data = stack.get(DataComponents.ENTITY_DATA);
				check(data != null, "a spawn egg should carry the entity it spawns: " + egg);
				check(net.minecraft.world.item.SpawnEggItem.getType(stack) != null,
					"and the game should read the type back off it: " + egg);
			}

			// The config: written with its defaults, and the world reads it rather than the constants.
			var configPath = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("forja.json");
			log("config: " + configPath.getFileName() + " existe " + java.nio.file.Files.exists(configPath)
				+ ", saqueadores " + dev.forja.ForjaConfig.get().saqueadores
				+ ", corazas " + dev.forja.ForjaConfig.get().corazas);
			check(java.nio.file.Files.exists(configPath), "the mod should write its config file on first run");
			check(dev.forja.ForjaConfig.get().saqueadores > 0.0F && dev.forja.ForjaConfig.get().corazas > 0.0F,
				"and the chances it holds should be the ones the world uses");

			// Elites: one in a hundred, and what they carry is a legend.
			check(dev.forja.world.Elites.CHANCE > 0.0F && dev.forja.world.Elites.HEALTH >= 3.0F, "an elite should be rare and hard to kill");

			// Encargos: the order is the same for the same smith on the same day, and different tomorrow.
			java.util.UUID smith = java.util.UUID.nameUUIDFromBytes("forja-test-smith".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			{
				dev.forja.world.Commissions.Commission today = dev.forja.world.Commissions.of(smith, 1L);
				dev.forja.world.Commissions.Commission again = dev.forja.world.Commissions.of(smith, 1L);
				dev.forja.world.Commissions.Commission tomorrow = dev.forja.world.Commissions.of(smith, 2L);
				log("encargo: " + today.type().id() + " por " + today.emeralds() + " esmeraldas, mejora " + today.upgrade().id());
				check(today.equals(again), "the same smith on the same day should ask for the same thing");
				check(!today.equals(tomorrow), "and for something else the next day");
				check(today.emeralds() >= 12 && today.upgrade().appliesTo(today.type()), "an order should pay well and be possible");
				ItemStack asked = Assembler.create(today.type(), today.materials(), registries);
				check(!today.matches(asked), "the piece alone is not enough without the upgrade");
				ItemStack upgraded = dev.forja.upgrade.UpgradeRecipes.upgraded(asked, today.type(), today.upgrade(), 100, registries);
				check(today.matches(upgraded), "the piece with the upgrade is what was asked for");
			}

			// A balance table in the log: every weapon in iron, what it hits for and how often.
			StringBuilder table = new StringBuilder("balance (hierro):");
			for (ForgeType type : ForgeType.values()) {
				if (type.kind != ForgeType.Kind.WEAPON) {
					continue;
				}
				List<dev.forja.material.ForgeMaterial> weaponMaterials = new java.util.ArrayList<>();
				for (int slot = 0; slot < type.slots.size(); slot++) {
					weaponMaterials.add(type.slots.get(slot).role == PartType.Role.HANDLE ? MADERA : HIERRO);
				}
				ForgeStats.Sheet sheet = ForgeStats.sheet(type, weaponMaterials, Upgrades.EMPTY);
				float damage = 1.0F + sheet.attackDamage;
				float speed = Math.max(0.2F, 4.0F + sheet.attackSpeed);
				table.append(String.format(Locale.ROOT, " %s %.1fx%.2f=%.1f", type.id(), damage, speed, damage * speed));
			}
			log(table.toString());

			// The history of a piece.
			ItemStack veteran = plain.copy();
			dev.forja.forge.ItemHistory.addKill(veteran);
			dev.forja.forge.ItemHistory.addBlocks(veteran, 7);
			check(dev.forja.forge.ItemHistory.of(veteran).kills() == 1 && dev.forja.forge.ItemHistory.of(veteran).blocks() == 7
				&& !dev.forja.forge.ItemHistory.of(veteran).isEmpty(), "a piece should remember what it has done");
		});
		int hooks = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			HolderLookup.Provider registries = level.registryAccess();
			check(ForgeType.match(List.of(PartType.MANGO, PartType.CUERDA)) == ForgeType.CANA, "handle + string is a fishing rod");
			ItemStack rod = maxed(connection, Assembler.create(ForgeType.CANA, List.of(HIERRO, CUERO), registries), new ItemStack(Items.WHEAT_SEEDS, 20));
			int lure = EnchantmentHelper.getItemEnchantmentLevel(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LURE), rod);
			check(rod.getItem() instanceof net.minecraft.world.item.FishingRodItem && lure == 3, "a maxed Cebo rod should be a rod with Lure III, got " + lure);
			check(rod.getMaxDamage() > 64, "a forged rod should outlast vanilla's, got " + rod.getMaxDamage());
			player.getInventory().clearContent();
			player.setItemInHand(InteractionHand.MAIN_HAND, rod);
			rod.use(level, player, InteractionHand.MAIN_HAND);
			return level.getEntitiesOfClass(net.minecraft.world.entity.projectile.FishingHook.class, player.getBoundingBox().inflate(16)).size();
		});
		log("cana: hooks in the water " + hooks);
		check(hooks == 1, "casting a forged rod should put out a hook, got " + hooks);
		context.waitTicks(10);
		int stillFishing = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			net.minecraft.world.entity.projectile.FishingHook.class, connection.getServerPlayer().getBoundingBox().inflate(16)).size());
		check(stillFishing == 1, "the hook must stay out while the forged rod is held, got " + stillFishing);
		server.runOnServer(s -> connection.getServerPlayer().getInventory().clearContent());


		float[] pigHealth = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			ItemStack sword = maxed(connection, Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, MADERA)), new ItemStack(Items.NETHER_WART, 20));
			player.getInventory().setItem(0, sword);
			player.getInventory().setSelectedSlot(0);
			float[] health = new float[2];
			for (int i = 0; i < 2; i++) {
				net.minecraft.world.entity.animal.pig.Pig pig = net.minecraft.world.entity.EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				pig.snapTo(player.getX() + 2 + i * 2, player.getY(), player.getZ() + 2, 0.0F, 0.0F);
				pig.setNoAi(true);
				level.addFreshEntity(pig);
				pig.setHealth(i == 0 ? 5.0F : 9.0F);
				pig.hurtServer(level, level.damageSources().playerAttack(player), 2.5F);
				health[i] = pig.getHealth();
			}
			return health;
		});
		log("ejecucion: wounded pig 5 -> " + pigHealth[0] + ", healthy pig 9 -> " + pigHealth[1]);
		check(pigHealth[0] < 2.0F, "Ejecucion should hit a pig under 30% health harder, it kept " + pigHealth[0]);
		check(pigHealth[1] >= 6.4F, "Ejecucion must not add damage above 30% health, the pig kept " + pigHealth[1]);
		server.runCommand("kill @e[type=pig]");

		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ItemStack chest = maxed(connection, Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO)), new ItemStack(Items.BREAD, 20));
			chest = maxed(connection, chest, new ItemStack(Items.HONEY_BOTTLE, 10));
			player.setItemSlot(EquipmentSlot.CHEST, chest);
			player.getFoodData().setFoodLevel(8);
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 400, 0));
		});
		context.waitTicks(110);
		boolean[] armor = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			return new boolean[] {player.getFoodData().getFoodLevel() > 8, !player.hasEffect(net.minecraft.world.effect.MobEffects.POISON)};
		});
		log("nutricion fed: " + armor[0] + ", purificacion cleaned poison: " + armor[1]);
		check(armor[0], "Nutricion should restore hunger within 5 seconds");
		check(armor[1], "Purificacion should clear poison within 5 seconds");

		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.setItemSlot(EquipmentSlot.HEAD, Assembler.create(ForgeType.CASCO, List.of(HIERRO, CUERO)));
			player.setItemSlot(EquipmentSlot.CHEST, Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO)));
			player.setItemSlot(EquipmentSlot.LEGS, Assembler.create(ForgeType.GREBAS, List.of(HIERRO, CUERO)));
			player.setItemSlot(EquipmentSlot.FEET, Assembler.create(ForgeType.BOTAS, List.of(HIERRO, MADERA)));
		});
		context.waitTicks(25);
		double[] toughness = server.computeOnServer(s -> new double[] {
			connection.getServerPlayer().getAttributeValue(Attributes.ARMOR_TOUGHNESS), connection.getServerPlayer().getAttributeValue(Attributes.ARMOR),
			connection.getServerPlayer().getAttributeValue(Attributes.MAX_HEALTH)
		});
		server.runOnServer(s -> connection.getServerPlayer().setItemSlot(EquipmentSlot.HEAD, Assembler.create(ForgeType.CASCO, List.of(DIAMANTE, CUERO))));
		context.waitTicks(25);
		double[] broken = server.computeOnServer(s -> new double[] {
			connection.getServerPlayer().getAttributeValue(Attributes.ARMOR_TOUGHNESS), connection.getServerPlayer().getAttributeValue(Attributes.ARMOR),
			connection.getServerPlayer().getAttributeValue(Attributes.MAX_HEALTH)
		});
		log("conjunto: full iron set toughness " + toughness[0] + " armor " + toughness[1] + "; with a diamond helmet toughness " + broken[0] + " armor " + broken[1]);
		check(toughness[0] >= 2.0 && toughness[1] >= 17.0, "a full iron set should add 2 armor and 2 toughness");
		check(Math.abs(broken[1] - 16.0) < 0.01, "breaking the set should drop its +2 armor, got " + broken[1]);
		log("conjunto de hierro: max health " + toughness[2] + " -> " + broken[2]);
		check(toughness[2] == 24.0 && broken[2] == 20.0, "a full iron set should add 2 hearts and lose them when broken up");
		check(server.computeOnServer(s -> advancementDone(connection.getServerPlayer(), "conjunto")), "a full set should grant forja/conjunto");
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		});

		// Test commands tune the held gear.
		server.runOnServer(s -> connection.getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, MADERA))));
		server.runCommand("execute as @a run forja orbe filo 35");
		int orbPercent = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				var orb = dev.forja.item.UpgradeOrbItem.orb(player.getInventory().getItem(i));
				if (orb != null && orb.upgrade() == Upgrade.FILO) {
					player.getInventory().setItem(i, ItemStack.EMPTY);
					return orb.percent();
				}
			}
			return 0;
		});
		check(orbPercent == 35, "/forja orbe should give a Filo 35% orb, got " + orbPercent);
		server.runCommand("execute as @a run forja maestria 5");
		server.runCommand("execute as @a run forja mejora eficiencia 60");
		int[] tuned = server.computeOnServer(s -> {
			ItemStack held = connection.getServerPlayer().getMainHandItem();
			return new int[] {dev.forja.forge.Mastery.level(held), held.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.EFICIENCIA)};
		});
		log("commands: maestria " + tuned[0] + ", eficiencia " + tuned[1] + "%");
		check(tuned[0] == 5 && tuned[1] == 60, "/forja maestria and /forja mejora should tune the held pickaxe");

		BlockPos dark = new BlockPos(areaX + 4, y + 1, z + 4);
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone hollow", areaX + 2, y, z + 2, areaX + 6, y + 4, z + 6));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d stone", dark.getX(), dark.getY(), dark.getZ()));
		context.waitTicks(20);
		int torches = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ItemStack pick = maxed(connection, Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, MADERA)), new ItemStack(Items.GLOWSTONE, 25));
			player.getInventory().setItem(0, pick);
			player.getInventory().setItem(5, new ItemStack(Items.TORCH, 4));
			player.getInventory().setSelectedSlot(0);
			player.gameMode.destroyBlock(dark);
			return player.getInventory().getItem(5).getCount();
		});
		boolean lit = server.computeOnServer(s -> connection.getServerLevel().getBlockState(dark).is(Blocks.TORCH));
		int pickExperience = server.computeOnServer(s -> dev.forja.forge.Mastery.experience(connection.getServerPlayer().getInventory().getItem(0)));
		log("maestria: pickaxe experience after one block " + pickExperience);
		check(pickExperience == 1, "mining a block should give the forged pickaxe one Maestria experience, got " + pickExperience);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, MADERA));
			int baseDurability = sword.getMaxDamage();
			double baseDamage = sword.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE)).mapToDouble(entry -> entry.modifier().amount()).sum();
			dev.forja.forge.Mastery.addExperience(player, sword, 150);
			double damage = sword.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE)).mapToDouble(entry -> entry.modifier().amount()).sum();
			log("maestria: sword level " + dev.forja.forge.Mastery.level(sword) + ", durability " + baseDurability + " -> " + sword.getMaxDamage() + ", damage " + baseDamage + " -> " + damage);
			check(dev.forja.forge.Mastery.level(sword) == 2, "150 experience should be Maestria 2");
			check(sword.get(DataComponents.RARITY) == net.minecraft.world.item.Rarity.COMMON, "a sword below Maestria 10 keeps a plain name");
			ItemStack master = sword.copy();
			dev.forja.forge.Mastery.setLevel(master, 10, connection.getServerLevel().registryAccess());
			check(master.get(DataComponents.RARITY) == net.minecraft.world.item.Rarity.EPIC, "Maestria 10 should make the name epic");
			master.setDamageValue(master.getMaxDamage());
			dev.forja.forge.Mastery.setLevel(master, 3, connection.getServerLevel().registryAccess());
			check(master.isBroken() && master.get(DataComponents.RARITY) == net.minecraft.world.item.Rarity.COMMON, "rewriting a broken sword must keep it broken");
			check(sword.getMaxDamage() == Math.round(baseDurability * 1.06F) && Math.abs(damage - baseDamage - 0.4) < 0.01, "Maestria 2 should add 6% durability and 0.4 damage");
		});
		log("luz: torch placed " + lit + ", torches left " + torches);
		check(lit && torches == 3, "Luz should place one of the player's torches in the dark");

		server.runCommand("difficulty easy");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			ItemStack shield = maxed(connection, Assembler.create(ForgeType.ESCUDO, List.of(MADERA, HIERRO, MADERA)), new ItemStack(Items.GOLD_INGOT, 6));
			player.setItemSlot(EquipmentSlot.OFFHAND, shield);
			player.startUsingItem(InteractionHand.OFF_HAND);
			net.minecraft.world.entity.animal.pig.Pig pig = net.minecraft.world.entity.EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			pig.snapTo(player.getX(), player.getY(), player.getZ() + 1.5, 180.0F, 0.0F);
			pig.setNoAi(true);
			level.addFreshEntity(pig);
		});
		context.waitTicks(20);
		boolean absorbed = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var pig = level.getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class, player.getBoundingBox().inflate(4)).getFirst();
			player.hurtServer(level, level.damageSources().mobAttack(pig), 4.0F);
			return player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
		});
		server.runCommand("difficulty peaceful");
		server.runCommand("kill @e[type=pig]");
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.stopUsingItem();
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			player.getInventory().clearContent();
		});
		log("absorcion after a blocked hit: " + absorbed);
		check(absorbed, "Absorcion 100% should give absorption when the shield blocks");
	}

	private static void checkTraits(TestServerContext server, TestServerConnection connection) {
		server.runOnServer(s -> {
			HolderLookup.Provider registries = connection.getServerLevel().registryAccess();
			var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);

			ItemStack emeraldSword = Assembler.create(ForgeType.ESPADA, List.of(ESMERALDA, MADERA, HIERRO), registries);
			int looting = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.LOOTING), emeraldSword);
			check(looting == 1, "an emerald blade should give Looting I, got " + looting);

			ItemStack blazeSword = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, VARA_DE_BLAZE, HIERRO), registries);
			int fire = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.FIRE_ASPECT), blazeSword);
			check(fire == 1, "a blaze rod handle should give Fire Aspect I, got " + fire);

			ItemStack prismarinePick = Assembler.create(ForgeType.PICO, List.of(PRISMARINA, MADERA, MADERA), registries);
			boolean underwater = prismarinePick.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, null).modifiers().stream()
				.anyMatch(entry -> entry.attribute().equals(Attributes.SUBMERGED_MINING_SPEED));
			check(underwater, "a prismarine pickaxe should mine faster underwater");

			ServerPlayer wearer = connection.getServerPlayer();
			ItemStack goldHelmet = Assembler.create(ForgeType.CASCO, List.of(ORO, CUERO), registries);
			wearer.setItemSlot(EquipmentSlot.HEAD, goldHelmet);
			boolean calm = net.minecraft.world.entity.monster.piglin.PiglinAi.isWearingSafeArmor(wearer);
			wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			check(calm, "gold plates should keep piglins calm");

			ItemStack quartzHelmet = Assembler.create(ForgeType.CASCO, List.of(CUARZO, CUERO), registries);
			int thorns = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.THORNS), quartzHelmet);
			check(thorns == 1, "quartz armor should give Thorns I, got " + thorns);

			// Escoria: the point of the material is that it burns things without a trip to the Nether,
			// and that it falls apart while doing it. Both are the same one assertion of worth.
			ItemStack slagSword = Assembler.create(ForgeType.ESPADA,
				List.of(dev.forja.material.ForgeMaterial.ESCORIA, MADERA, HIERRO), registries);
			int slagFire = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.FIRE_ASPECT), slagSword);
			int slagDurability = slagSword.getMaxDamage();
			ItemStack ironSword = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			int ironDurability = ironSword.getMaxDamage();
			log("escoria: prende " + (slagFire == 1) + ", aguanta " + slagDurability
				+ " contra " + ironDurability + " del hierro");
			check(slagFire == 1, "a slag blade should give Fire Aspect I, got " + slagFire);
			check(slagDurability > 0 && slagDurability < ironDurability,
				"and should break sooner than iron, " + slagDurability + " against " + ironDurability);

			// The aura: what a piece puts out is decided by its trait, and two traits that only show
			// at one end of the day have to actually be quiet at the other.
			var random = net.minecraft.util.RandomSource.create(7);
			var slagMote = dev.forja.client.GearAura.moteFor(slagSword, true, random);
			var starMote = dev.forja.client.GearAura.moteFor(
				Assembler.create(ForgeType.ESPADA, List.of(ESTELAR, MADERA, HIERRO), registries), true, random);
			ItemStack cryingSword = Assembler.create(ForgeType.ESPADA, List.of(OBSIDIANA_LLORONA, MADERA, HIERRO), registries);
			var cryingDay = dev.forja.client.GearAura.moteFor(cryingSword, true, random);
			var cryingNight = dev.forja.client.GearAura.moteFor(cryingSword, false, random);
			var plainMote = dev.forja.client.GearAura.moteFor(ironSword, true, random);
			var vanillaMote = dev.forja.client.GearAura.moteFor(new ItemStack(net.minecraft.world.item.Items.IRON_SWORD), true, random);
			log("aura: escoria " + (slagMote == dev.forja.registry.ModParticles.CHISPA)
				+ ", estelar " + (starMote == net.minecraft.core.particles.ParticleTypes.END_ROD)
				+ ", llorona de dia " + (cryingDay == null) + " de noche " + (cryingNight != null)
				+ ", hierro " + (plainMote == null) + ", vanilla " + (vanillaMote == null));
			check(slagMote == dev.forja.registry.ModParticles.CHISPA, "a slag blade should throw sparks");
			check(starMote == net.minecraft.core.particles.ParticleTypes.END_ROD, "star iron should throw sky");
			check(cryingDay == null && cryingNight != null, "crying obsidian should only weep at night");
			check(plainMote == null, "plain iron should be quiet");
			check(vanillaMote == null, "and an unforged item should have no aura at all");

			// Facing south your right hand points west, which is -X. Asserted rather than reasoned
			// about: I talked myself into the opposite sign twice.
			var rightFacingSouth = dev.forja.client.GearAura.rightOf(0.0F);
			var rightFacingEast = dev.forja.client.GearAura.rightOf(-90.0F);
			log("mano derecha mirando al sur " + rightFacingSouth + ", mirando al este " + rightFacingEast);
			check(rightFacingSouth.x < -0.9 && Math.abs(rightFacingSouth.z) < 0.01,
				"facing south the right hand is west, got " + rightFacingSouth);
			// And facing east it points south. Written down wrong here the first time too, which is
			// the third go at this one sign and the reason it is nailed to a test.
			check(rightFacingEast.z > 0.9 && Math.abs(rightFacingEast.x) < 0.01,
				"facing east the right hand is south, got " + rightFacingEast);

			java.util.Set<Integer> perkColours = new java.util.HashSet<>();
			for (var perk : dev.forja.forge.Perk.values()) {
				perkColours.add(perk.color);
			}
			log("dones: " + dev.forja.forge.Perk.values().length + " con " + perkColours.size() + " colores distintos");
			check(perkColours.size() == dev.forja.forge.Perk.values().length,
				"two gifts sharing a colour makes both marks meaningless, got "
					+ perkColours.size() + " colours for " + dev.forja.forge.Perk.values().length + " gifts");

			log("traits ok: looting " + looting + ", fire aspect " + fire + ", thorns " + thorns);
		});
	}

	/** Forged gear that runs out of durability stays in the hand, broken and useless until repaired. */
	private static void checkBrokenGear(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			HolderLookup.Provider registries = level.registryAccess();
			player.getInventory().clearContent();
			ItemStack sword = UpgradeRecipes.upgraded(Assembler.create(ForgeType.ESPADA, List.of(DIAMANTE, MADERA, HIERRO), registries), ForgeType.ESPADA, Upgrade.FILO, 100, registries);
			sword.setDamageValue(sword.getMaxDamage() - 1);
			player.setItemInHand(InteractionHand.MAIN_HAND, sword);
			sword.hurtAndBreak(5, player, EquipmentSlot.MAINHAND);
			ItemStack held = player.getMainHandItem();
			check(!held.isEmpty() && held.isBroken() && dev.forja.forge.BrokenGear.isBroken(held), "a forged sword should stay in hand broken instead of vanishing");

			net.minecraft.world.entity.animal.pig.Pig pig = net.minecraft.world.entity.EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			float brokenDamage = EnchantmentHelper.modifyDamage(level, held, pig, level.damageSources().playerAttack(player), 5.0F);
			ItemStack fresh = held.copy();
			fresh.setDamageValue(0);
			float sharpDamage = EnchantmentHelper.modifyDamage(level, fresh, pig, level.damageSources().playerAttack(player), 5.0F);
			log("broken sword: damage with Filo " + brokenDamage + " (repaired " + sharpDamage + ")");
			check(brokenDamage == 5.0F && sharpDamage > 5.0F, "a broken sword should lose its Sharpness until repaired");
			pig.discard();

			ItemStack pick = Assembler.create(ForgeType.PICO, List.of(DIAMANTE, MADERA, HIERRO), registries);
			pick.setDamageValue(pick.getMaxDamage());
			check(pick.getDestroySpeed(Blocks.STONE.defaultBlockState()) == 1.0F && !pick.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()),
				"a broken pickaxe should mine like a fist and drop nothing");
			check(pick.use(level, player, InteractionHand.OFF_HAND) == net.minecraft.world.InteractionResult.FAIL, "a broken tool cannot be used");
			// A tempering bar right-clicked onto the broken pickaxe in the inventory brings it back.
			player.getInventory().setItem(5, pick);
			ItemStack bar = new ItemStack(dev.forja.registry.ModItems.LINGOTE_DE_TEMPLE, 2);
			var inventorySlot = player.inventoryMenu.slots.stream().filter(slot -> slot.container == player.getInventory() && slot.getContainerSlot() == 5).findFirst().orElseThrow();
			boolean used = bar.getItem().overrideStackedOnOther(bar, inventorySlot, net.minecraft.world.inventory.ClickAction.SECONDARY, player);
			ItemStack mended = player.getInventory().getItem(5);
			log("lingote de temple: usado " + used + ", pico " + (mended.getMaxDamage() - mended.getDamageValue()) + "/" + mended.getMaxDamage() + ", quedan " + bar.getCount());
			check(used && !mended.isBroken() && bar.getCount() == 1 && mended.getMaxDamage() - mended.getDamageValue() == dev.forja.item.TemperIngotItem.repairAmount(mended),
				"a tempering bar should mend a broken pickaxe and be used up");
			// And the whole point of the bar: what it is worth depends on the head, not on the piece.
			ItemStack wooden = Assembler.create(ForgeType.PICO, List.of(MADERA, MADERA, MADERA), registries);
			int onDiamond = dev.forja.item.TemperIngotItem.repairAmount(mended);
			int onWood = dev.forja.item.TemperIngotItem.repairAmount(wooden);
			log("un lingote devuelve " + onDiamond + " a una cabeza de diamante y " + onWood + " a una de madera");
			check(onDiamond == DIAMANTE.durability / dev.forja.item.TemperIngotItem.SHARE,
				"a diamond head should get back a quarter of what diamond is worth, got " + onDiamond);
			check(onWood == dev.forja.item.TemperIngotItem.MINIMUM,
				"and a wooden one only the floor, got " + onWood);
			check(dev.forja.item.TemperIngotItem.repairAmount(new ItemStack(Items.IRON_PICKAXE)) == 0,
				"a vanilla pickaxe is not forged gear and cannot be tempered");
			// And that a crucible will actually pour one, which is the only way to get it.
			dev.forja.forge.Alloys.Recipe pour = dev.forja.forge.Alloys.match(
				List.of(new ItemStack(dev.forja.registry.ModItems.alloy("acero_refractario")), new ItemStack(Items.CLAY_BALL, 2)),
				dev.forja.forge.Alloys.Heat.CALIENTE);
			log("colada del lingote de temple: " + (pour == null ? "ninguna" : pour.result().getCount() + "x " + pour.result().getHoverName().getString()));
			check(pour != null && pour.result().is(dev.forja.registry.ModItems.LINGOTE_DE_TEMPLE) && pour.result().getCount() == 2,
				"a hot crucible with refractory steel and clay should pour two tempering bars");
			check(dev.forja.forge.Alloys.ALL.stream().noneMatch(r -> r.id().equals("lingote_de_temple")),
				"the tempering bar is not an alloy and must stay out of the material tables");
			player.getInventory().setItem(5, ItemStack.EMPTY);
			pick.setDamageValue(10);
			check(pick.getDestroySpeed(Blocks.STONE.defaultBlockState()) > 1.0F && pick.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()), "a repaired pickaxe works again");

			net.minecraft.world.entity.monster.zombie.Zombie zombie = new net.minecraft.world.entity.monster.zombie.Zombie(level);
			zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
			zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			dev.forja.world.ForjaMobs.forgeEquipment(zombie, level.getRandom(), 1.0F);
			var helmetParts = zombie.getItemBySlot(EquipmentSlot.HEAD).get(ModComponents.PARTS);
			var bootsParts = zombie.getItemBySlot(EquipmentSlot.FEET).get(ModComponents.PARTS);
			var swordParts = zombie.getMainHandItem().get(ModComponents.PARTS);
			check(helmetParts != null && helmetParts.type() == ForgeType.CASCO && helmetParts.primary() == HIERRO, "a zombie's iron helmet can spawn forged");
			check(bootsParts != null && bootsParts.primary() == ORO, "golden boots become forged gold boots");
			check(swordParts != null && swordParts.type() == ForgeType.ESPADA && swordParts.primary() == HIERRO, "an iron sword becomes a forged iron sword");
			zombie.discard();

			// Village smiths trade templates and orbs.
			var trades = registries.lookupOrThrow(Registries.VILLAGER_TRADE);
			var villager = new net.minecraft.world.entity.npc.villager.Villager(net.minecraft.world.entity.EntityTypes.VILLAGER, level);
			villager.snapTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
			var tradeContext = new net.minecraft.world.level.storage.loot.LootContext.Builder(
				new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, player.position())
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, villager)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, net.minecraft.util.Unit.INSTANCE)
					.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.VILLAGER_TRADE)
			).create(java.util.Optional.empty());
			var bladeTrade = trades.getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.VILLAGER_TRADE, dev.forja.Forja.id("weaponsmith/2/plantilla_hoja")));
			var bladeOffer = bladeTrade.value().getOffer(tradeContext);
			check(bladeOffer != null && dev.forja.item.TemplateItem.pattern(bladeOffer.getResult()) == PartType.HOJA, "the weaponsmith should sell a blade template");
			var orbOffer = trades.getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.VILLAGER_TRADE, dev.forja.Forja.id("armorer/4/orbe_proteccion"))).value().getOffer(tradeContext);
			var tradedOrb = orbOffer == null ? null : dev.forja.item.UpgradeOrbItem.orb(orbOffer.getResult());
			check(tradedOrb != null && tradedOrb.upgrade() == Upgrade.PROTECCION && tradedOrb.percent() == 30, "the armorer should sell a Proteccion 30% orb");
			var weaponsmithLevel2 = trades.getOrThrow(net.minecraft.tags.TagKey.create(Registries.VILLAGER_TRADE, net.minecraft.resources.Identifier.withDefaultNamespace("weaponsmith/level_2")));
			check(weaponsmithLevel2.contains(bladeTrade), "the blade template should join the weaponsmith's level 2 trades");
			var mapTrade = trades.getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.VILLAGER_TRADE, dev.forja.Forja.id("toolsmith/5/mapa_forja_abandonada")));
			check(trades.getOrThrow(net.minecraft.tags.TagKey.create(Registries.VILLAGER_TRADE, net.minecraft.resources.Identifier.withDefaultNamespace("toolsmith/level_5"))).contains(mapTrade),
				"the master toolsmith should offer the abandoned forge map");
			// The three maps the smith sells, each pointing at one structure of its own.
			var structures = registries.lookupOrThrow(Registries.STRUCTURE);
			int mapped = structures.getOrThrow(net.minecraft.tags.TagKey.create(Registries.STRUCTURE, dev.forja.Forja.id("on_forja_maps"))).size();
			int alone = 0;
			for (String destination : List.of("on_forja_abandonada", "on_taller_de_montana", "on_campamento_saqueadores")) {
				alone += structures.getOrThrow(net.minecraft.tags.TagKey.create(Registries.STRUCTURE, dev.forja.Forja.id(destination))).size() == 1 ? 1 : 0;
			}
			log("mapas de forja: " + mapped + " estructuras, con destino propio " + alone + " de 3");
			check(mapped == 3, "the forge maps should cover all three surface structures, got " + mapped);
			check(alone == 3, "and each map should point at exactly one of them, got " + alone);
			// A villager with the Forjador profession gets offers from Forja's own trade sets.
			var forjadorHolder = level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(dev.forja.registry.ModVillagers.FORJADOR);
			var forjador = new net.minecraft.world.entity.npc.villager.Villager(net.minecraft.world.entity.EntityTypes.VILLAGER, level);
			forjador.snapTo(player.getX() + 3, player.getY(), player.getZ(), 0.0F, 0.0F);
			forjador.setVillagerData(forjador.getVillagerData().withProfession(forjadorHolder).withLevel(1));
			int forjaOffers = 0;
			for (var offer : forjador.getOffers()) {
				forjaOffers += net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).getNamespace().equals("forja") ? 1 : 0;
			}
			log("forjador: " + forjador.getOffers().size() + " offers, " + forjaOffers + " of Forja items, profession name " + forjadorHolder.value().name().getString());
			check(forjaOffers == 2, "a novice Forjador should offer two Forja trades, got " + forjaOffers);
			var jobSite = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(dev.forja.registry.ModVillagers.MESA_DE_PIEZAS_POI);
			check(jobSite.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE) && forjadorHolder.value().acquirableJobSite().test(jobSite),
				"the Parts Table should be a job site unemployed villagers can take");
			log("trades: " + bladeOffer.getResult().getHoverName().getString() + " for " + bladeOffer.getCostA().getCount() + " emeralds, " + orbOffer.getResult().getHoverName().getString());

			net.minecraft.world.inventory.GrindstoneMenu grindstone = new net.minecraft.world.inventory.GrindstoneMenu(99, player.getInventory());
			grindstone.getSlot(0).set(fresh);
			check(grindstone.getSlot(2).getItem().isEmpty(), "the grindstone must refuse forged gear instead of stripping its upgrade enchantments");
			grindstone.getSlot(0).set(ItemStack.EMPTY);
		});
		context.waitTicks(3);
		double brokenAttack = server.computeOnServer(s -> connection.getServerPlayer().getAttributeValue(Attributes.ATTACK_DAMAGE));
		server.runOnServer(s -> connection.getServerPlayer().getMainHandItem().setDamageValue(0));
		context.waitTicks(3);
		double repairedAttack = server.computeOnServer(s -> connection.getServerPlayer().getAttributeValue(Attributes.ATTACK_DAMAGE));
		log("broken sword attack " + brokenAttack + ", repaired " + repairedAttack);
		check(brokenAttack <= 1.0 && repairedAttack > 5.0, "a broken sword hits like a fist until repaired");
		check(server.computeOnServer(s -> advancementDone(connection.getServerPlayer(), "roto")), "breaking forged gear should grant forja/roto");
		server.runOnServer(s -> connection.getServerPlayer().getInventory().clearContent());
	}

	/** The parry window: a block in the first moments of raising the shield throws the hit back. */
	private static void checkParry(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		// Perfect parry: block in the first moments of raising the shield. Mob damage is zero on peaceful.
		server.runCommand("difficulty easy");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			player.setItemSlot(EquipmentSlot.OFFHAND, Assembler.create(ForgeType.ESCUDO, List.of(MADERA, HIERRO, MADERA), level.registryAccess()));
			net.minecraft.world.entity.animal.pig.Pig pig = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, level);
			pig.snapTo(player.getX(), player.getY(), player.getZ() + 1.5, 180.0F, 0.0F);
			pig.setNoAi(true);
			level.addFreshEntity(pig);
			player.startUsingItem(InteractionHand.OFF_HAND);
		});
		boolean[] parried = {false, false, false};
		for (int attempt = 0; attempt < 20 && !parried[0]; attempt++) {
			context.waitTicks(1);
			boolean[] outcome = server.computeOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				ServerPlayer player = connection.getServerPlayer();
				if (!dev.forja.upgrade.CombatUpgrades.isParry(player, player.getOffhandItem())) {
					return new boolean[] {false, false, false};
				}
				var pig = level.getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class, player.getBoundingBox().inflate(4)).getFirst();
				float before = pig.getHealth();
				player.invulnerableTime = 0;
				player.hurtServer(level, level.damageSources().mobAttack(pig), 4.0F);
				return new boolean[] {true, pig.hasEffect(MobEffects.SLOWNESS) && pig.hasEffect(MobEffects.WEAKNESS), pig.getHealth() < before};
			});
			System.arraycopy(outcome, 0, parried, 0, outcome.length);
		}
		server.runOnServer(s -> {
			HolderLookup.Provider registries = connection.getServerLevel().registryAccess();
			ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			check(dev.forja.upgrade.Synergy.on(sword).isEmpty(), "a plain sword has no synergies");
			sword = UpgradeRecipes.upgraded(sword, ForgeType.ESPADA, Upgrade.VAMPIRISMO, 60, registries);
			check(dev.forja.upgrade.Synergy.on(sword).isEmpty(), "one upgrade is not a synergy");
			sword = UpgradeRecipes.upgraded(sword, ForgeType.ESPADA, Upgrade.EJECUCION, 50, registries);
			check(dev.forja.upgrade.Synergy.on(sword).equals(List.of(dev.forja.upgrade.Synergy.SED_DE_SANGRE)),
				"Vampirismo and Ejecucion at 50% should wake Sed de sangre, got " + dev.forja.upgrade.Synergy.on(sword));
			sword.setDamageValue(sword.getMaxDamage());
			check(dev.forja.upgrade.Synergy.on(sword).isEmpty(), "a broken weapon loses its synergies");
			ItemStack pick = Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, HIERRO), registries);
			pick = UpgradeRecipes.upgraded(pick, ForgeType.PICO, Upgrade.VETA, 100, registries);
			pick = UpgradeRecipes.upgraded(pick, ForgeType.PICO, Upgrade.FORTUNA, 100, registries);
			check(dev.forja.upgrade.Synergy.FILON.active(pick), "Veta and Fortuna at 100% should wake Filon");
			// Fortaleza is worked out in the stats, not in a handler, so check the armor it adds.
			ItemStack chest = Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO), registries);
			int plainArmor = chest.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.filter(entry -> entry.attribute().equals(Attributes.ARMOR)).mapToInt(entry -> (int) entry.modifier().amount()).sum();
			ItemStack fortified = UpgradeRecipes.upgraded(chest, ForgeType.PECHERA, Upgrade.PROTECCION, 50, registries);
			fortified = UpgradeRecipes.upgraded(fortified, ForgeType.PECHERA, Upgrade.VITALIDAD, 50, registries);
			int fortifiedArmor = fortified.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.filter(entry -> entry.attribute().equals(Attributes.ARMOR)).mapToInt(entry -> (int) entry.modifier().amount()).sum();
			check(dev.forja.upgrade.Synergy.FORTALEZA.active(fortified) && fortifiedArmor == plainArmor + 1,
				"Fortaleza should add a point of armor, " + plainArmor + " -> " + fortifiedArmor);
			ItemStack rod = Assembler.create(ForgeType.CANA, List.of(HIERRO, CUERO), registries);
			rod = UpgradeRecipes.upgraded(rod, ForgeType.CANA, Upgrade.CEBO, 100, registries);
			rod = UpgradeRecipes.upgraded(rod, ForgeType.CANA, Upgrade.SUERTE_DEL_MAR, 100, registries);
			check(dev.forja.upgrade.Synergy.BANCO_DE_PECES.active(rod), "a maxed rod should wake Banco de peces");
			ItemStack mace = Assembler.create(ForgeType.MAZO, List.of(HIERRO, MADERA, HIERRO), registries);
			mace = UpgradeRecipes.upgraded(mace, ForgeType.MAZO, Upgrade.DENSIDAD, 60, registries);
			mace = UpgradeRecipes.upgraded(mace, ForgeType.MAZO, Upgrade.ONDA_DE_CHOQUE, 60, registries);
			check(dev.forja.upgrade.Synergy.METEORO.active(mace), "Densidad and Onda de choque should wake Meteoro on a mace");
			ItemStack gale = Assembler.create(ForgeType.MAZO, List.of(HIERRO, MADERA, HIERRO), registries);
			gale = UpgradeRecipes.upgraded(gale, ForgeType.MAZO, Upgrade.DENSIDAD, 60, registries);
			gale = UpgradeRecipes.upgraded(gale, ForgeType.MAZO, Upgrade.ESTALLIDO_DE_VIENTO, 60, registries);
			check(dev.forja.upgrade.Synergy.VENDAVAL.active(gale), "Densidad and Estallido de viento should wake Vendaval");
			// The chisel: it carves stone in place and knows its way around every family.
			ItemStack chisel = Assembler.create(ForgeType.CINCEL, List.of(HIERRO, MADERA), registries);
			check(chisel.has(ModComponents.PARTS), "a chisel should forge out of a blade and a handle");
			check(dev.forja.forge.Chisel.next(net.minecraft.world.level.block.Blocks.STONE, false)
				== net.minecraft.world.level.block.Blocks.SMOOTH_STONE, "stone should step to smooth stone");
			check(dev.forja.forge.Chisel.next(net.minecraft.world.level.block.Blocks.STONE, true)
				== net.minecraft.world.level.block.Blocks.CRACKED_STONE_BRICKS, "and back to the last face of its family");
			check(dev.forja.forge.Chisel.next(net.minecraft.world.level.block.Blocks.DIRT, false) == null,
				"and it should not know what to do with dirt");
			// Carving a pillar must not lay it flat: the axis travels with the cut.
			var pillar = net.minecraft.world.level.block.Blocks.PURPUR_PILLAR.defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X);
			check(dev.forja.forge.Chisel.next(pillar.getBlock(), false) == net.minecraft.world.level.block.Blocks.PURPUR_BLOCK,
				"a purpur pillar should step back to the block");
			log("cincel: " + dev.forja.forge.Chisel.knownBlocks() + " bloques en "
				+ dev.forja.forge.Chisel.families().size() + " familias");
			check(dev.forja.forge.Chisel.knownBlocks() >= 40, "the chisel should know a good few faces, got "
				+ dev.forja.forge.Chisel.knownBlocks());

			// The trident: thrown whole like an axe, and its two upgrades belong to it alone.
			ItemStack trident = Assembler.create(ForgeType.TRIDENTE, List.of(HIERRO, MADERA, CUERO), registries);
			check(trident.has(ModComponents.PARTS), "a trident should forge out of head, shaft and binding");
			check(dev.forja.upgrade.WeaponThrow.throwable(ForgeType.TRIDENTE), "the trident should be throwable whole");
			check(Upgrade.CORRIENTE.appliesTo(ForgeType.TRIDENTE) && Upgrade.CANALIZACION.appliesTo(ForgeType.TRIDENTE),
				"Corriente and Canalizacion should go on a trident");
			check(!Upgrade.CORRIENTE.appliesTo(ForgeType.ESPADA) && !Upgrade.CANALIZACION.appliesTo(ForgeType.LANZA),
				"and nowhere else");
			check(PartType.PUNTA_TRIDENTE.cost > PartType.PUNTA_LANZA.cost,
				"three prongs should cost more than one, got " + PartType.PUNTA_TRIDENTE.cost);
			trident = UpgradeRecipes.upgraded(trident, ForgeType.TRIDENTE, Upgrade.CORRIENTE, 100, registries);
			check(Upgrade.riptidePower(Upgrades.fraction(trident, Upgrade.CORRIENTE)) > 0.0F, "a full Corriente should throw you");
			log("tridente: dano " + damageOf(trident) + ", punta cuesta " + PartType.PUNTA_TRIDENTE.cost
				+ ", corriente " + Upgrade.riptidePower(1.0F));

			ItemStack spear = Assembler.create(ForgeType.LANZA, List.of(HIERRO, MADERA, CUERO), registries);
			spear = UpgradeRecipes.upgraded(spear, ForgeType.LANZA, Upgrade.EMBESTIDA, 100, registries);
			spear = UpgradeRecipes.upgraded(spear, ForgeType.LANZA, Upgrade.ALCANCE, 60, registries);
			check(dev.forja.upgrade.Synergy.JUSTA.active(spear), "Embestida and Alcance should wake Justa on a spear");
			ItemStack quarry = Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, HIERRO), registries);
			quarry = UpgradeRecipes.upgraded(quarry, ForgeType.PICO, Upgrade.EXCAVACION, 50, registries);
			quarry = UpgradeRecipes.upgraded(quarry, ForgeType.PICO, Upgrade.EFICIENCIA, 50, registries);
			check(dev.forja.upgrade.Synergy.CANTERA.active(quarry), "Excavacion and Eficiencia at 50% should wake Cantera");
			ItemStack tough = Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO), registries);
			tough = UpgradeRecipes.upgraded(tough, ForgeType.PECHERA, Upgrade.VITALIDAD, 50, registries);
			tough = UpgradeRecipes.upgraded(tough, ForgeType.PECHERA, Upgrade.REGENERACION, 50, registries);
			check(dev.forja.upgrade.Synergy.SEGUNDO_ALIENTO.active(tough), "Vitalidad and Regeneracion should wake Segundo aliento");
			ItemStack helm = Assembler.create(ForgeType.CASCO, List.of(HIERRO, CUERO), registries);
			helm = UpgradeRecipes.upgraded(helm, ForgeType.CASCO, Upgrade.SONAR, 50, registries);
			helm = UpgradeRecipes.upgraded(helm, ForgeType.CASCO, Upgrade.VISION_NOCTURNA, 100, registries);
			check(dev.forja.upgrade.Synergy.VIGIA.active(helm), "Sonar and Vision nocturna should wake Vigia");
			ItemStack storm = Assembler.create(ForgeType.TRIDENTE, List.of(HIERRO, MADERA, CUERO), registries);
			storm = UpgradeRecipes.upgraded(storm, ForgeType.TRIDENTE, Upgrade.CORRIENTE, 60, registries);
			storm = UpgradeRecipes.upgraded(storm, ForgeType.TRIDENTE, Upgrade.CANALIZACION, 60, registries);
			check(dev.forja.upgrade.Synergy.TEMPESTAD.active(storm), "Corriente and Canalizacion should wake Tempestad");
			ItemStack harvest = Assembler.create(ForgeType.GUADANA, List.of(HIERRO, MADERA, CUERO), registries);
			harvest = UpgradeRecipes.upgraded(harvest, ForgeType.GUADANA, Upgrade.COSECHADOR, 60, registries);
			harvest = UpgradeRecipes.upgraded(harvest, ForgeType.GUADANA, Upgrade.SIEGA_DE_ALMAS, 60, registries);
			check(dev.forja.upgrade.Synergy.SIEGA_NEGRA.active(harvest), "Cosechador and Siega de almas should wake Siega negra");
			log("sinergias: " + dev.forja.upgrade.Synergy.values().length + " pairs, Filon on a maxed pickaxe, Fortaleza +1 armor");
		});
		log("parada: parried " + parried[0] + ", attacker shaken " + parried[1] + ", damage reflected " + parried[2]);
		check(parried[0] && parried[1] && parried[2], "blocking inside the window should parry: reflect and shake the attacker");
		check(server.computeOnServer(s -> advancementDone(connection.getServerPlayer(), "parada")), "a parry should grant forja/parada");

		// Segundo aliento in the field: the breastplate answers a bad wound on its own.
		float[] wind = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			ItemStack chest = Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO), registries);
			chest = UpgradeRecipes.upgraded(chest, ForgeType.PECHERA, Upgrade.VITALIDAD, 60, registries);
			chest = UpgradeRecipes.upgraded(chest, ForgeType.PECHERA, Upgrade.REGENERACION, 60, registries);
			player.setItemSlot(EquipmentSlot.CHEST, chest);
			player.setHealth(4.0F);
			player.setAbsorptionAmount(0.0F);
			return new float[] {player.getHealth(), player.getAbsorptionAmount()};
		});
		context.waitTicks(10);
		float[] windAfter = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			float[] state = new float[] {player.getAbsorptionAmount(), player.hasEffect(MobEffects.REGENERATION) ? 1.0F : 0.0F};
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.removeAllEffects();
			player.setAbsorptionAmount(0.0F);
			player.setHealth(player.getMaxHealth());
			return state;
		});
		log("segundo aliento: vida " + wind[0] + ", absorcion " + windAfter[0] + ", regeneracion " + (windAfter[1] > 0.0F));
		check(windAfter[0] > 0.0F, "a wounded smith in that breastplate should get absorption, got " + windAfter[0]);
		check(windAfter[1] > 0.0F, "and a moment of regeneration");

		// Holding the shield up leaves the window, and then a hit is just a block.
		context.waitTicks(20);
		boolean lateStagger = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			check(!dev.forja.upgrade.CombatUpgrades.isParry(player, player.getOffhandItem()), "holding the shield up should leave the parry window");
			var pig = level.getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class, player.getBoundingBox().inflate(4)).getFirst();
			pig.removeAllEffects();
			player.invulnerableTime = 0;
			player.hurtServer(level, level.damageSources().mobAttack(pig), 4.0F);
			boolean shaken = pig.hasEffect(MobEffects.SLOWNESS);
			pig.discard();
			player.stopUsingItem();
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			return shaken;
		});
		check(!lateStagger, "a late block must not parry");
		server.runCommand("kill @e[type=pig]");
		server.runCommand("difficulty peaceful");
	}

	/** Eco (Resonante), resina (Pegajoso) and escama de armadillo (Acorazado). */
	private static void checkNewTraits(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int echoes = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			HolderLookup.Provider registries = level.registryAccess();
			var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);

			check(!PartType.CABEZA_PICO.accepts(dev.forja.material.ForgeMaterial.ESCAMA), "armadillo scute is too soft for a head");
			ItemStack scuteChest = Assembler.create(ForgeType.PECHERA, List.of(dev.forja.material.ForgeMaterial.ESCAMA, CUERO), registries);
			int projectile = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.PROJECTILE_PROTECTION), scuteChest);
			int blast = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.BLAST_PROTECTION), scuteChest);
			check(projectile == 1 && blast == 1, "scute armor should give Projectile and Blast Protection I, got " + projectile + "/" + blast);
			ItemStack scuteSword = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, dev.forja.material.ForgeMaterial.ESCAMA, HIERRO), registries);
			boolean steady = scuteSword.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().anyMatch(entry -> entry.attribute().equals(Attributes.KNOCKBACK_RESISTANCE));
			check(steady, "a scute handle should resist knockback");

			ItemStack resinSword = Assembler.create(ForgeType.ESPADA, List.of(dev.forja.material.ForgeMaterial.RESINA, MADERA, HIERRO), registries);
			int unbreaking = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.UNBREAKING), resinSword);
			check(unbreaking == 1, "resin should give Unbreaking I, got " + unbreaking);
			net.minecraft.world.entity.animal.pig.Pig pig = net.minecraft.world.entity.EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			pig.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0.0F, 0.0F);
			pig.setNoAi(true);
			level.addFreshEntity(pig);
			dev.forja.upgrade.TraitEffects.onHit(level, player, pig, level.damageSources().playerAttack(player), resinSword);
			var slow = pig.getEffect(MobEffects.SLOWNESS);
			check(slow != null && slow.getAmplifier() == 1, "a resin blade should give Slowness II");
			pig.discard();

			ItemStack echoBow = Assembler.create(ForgeType.ARCO, List.of(dev.forja.material.ForgeMaterial.ECO, CUERO, MADERA), registries);
			int piercing = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.PIERCING), echoBow);
			check(piercing == 1, "echo limbs should give Piercing I, got " + piercing);
			player.setItemSlot(EquipmentSlot.HEAD, Assembler.create(ForgeType.CASCO, List.of(dev.forja.material.ForgeMaterial.ECO, CUERO), registries));
			boolean darkened = player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200));
			check(!darkened && !player.hasEffect(MobEffects.DARKNESS), "an echo helmet should keep Darkness away");
			player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);

			// A little buried iron vein: breaking one ore outlines the other three.
			BlockPos origin = new BlockPos(x + 8, y - 1, z + 8);
			for (BlockPos pos : List.of(origin, origin.east(2).below(), origin.below(2), origin.north(3).below())) {
				level.setBlock(pos, Blocks.IRON_ORE.defaultBlockState(), 3);
			}
			level.setBlock(origin.west(10), Blocks.IRON_ORE.defaultBlockState(), 3);
			ItemStack echoPick = Assembler.create(ForgeType.PICO, List.of(dev.forja.material.ForgeMaterial.ECO, MADERA, HIERRO), registries);
			return dev.forja.upgrade.TraitEffects.echoOres(level, player, origin, Blocks.IRON_ORE.defaultBlockState(), echoPick);
		});
		log("resonante: ores echoed " + echoes);
		check(echoes == 3, "an echo pickaxe should outline the three iron ores in range, got " + echoes);
		check(server.computeOnServer(s -> advancementDone(connection.getServerPlayer(), "eco")), "echolocating ores should grant forja/eco");
		tp(server, x + 8.5, y, z + 3.5, 0.0F, 45.0F);
		context.waitTicks(8);
		context.takeScreenshot("forja_20_eco_menas");
		context.waitTicks(70);
		int left = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			net.minecraft.world.entity.Display.BlockDisplay.class, new net.minecraft.world.phys.AABB(new BlockPos(x + 8, y - 1, z + 8)).inflate(12),
			e -> e.entityTags().contains(dev.forja.upgrade.TraitEffects.ECHO_TAG)
		).size());
		check(left == 0, "the ore echoes should fade after three seconds, " + left + " left");
		log("new traits ok: resonante, pegajoso, acorazado");
	}

	private static void checkStatColors(TestServerContext server) {
		server.runOnServer(s -> {
			long started = System.nanoTime();
			for (ForgeType type : ForgeType.values()) {
				ForgeStats.range(type, ForgeStats.Stat.DURABILIDAD);
			}
			log(String.format(Locale.ROOT, "stat ranges for every item type computed in %.1f ms", (System.nanoTime() - started) / 1e6));
			// The hardest-hitting blade there is, whichever material that turns out to be.
			dev.forja.material.ForgeMaterial hardest = dev.forja.material.ForgeMaterial.MADERA;
			for (dev.forja.material.ForgeMaterial material : dev.forja.material.ForgeMaterial.values()) {
				if (material.attackDamageBonus > hardest.attackDamageBonus) {
					hardest = material;
				}
			}
			ForgeStats.Line best = damageLine(ForgeType.ESPADA, List.of(hardest, MADERA, MADERA));
			ForgeStats.Line worst = damageLine(ForgeType.ESPADA, List.of(MADERA, MADERA, MADERA));
			int bestColor = ForgeStats.color(ForgeType.ESPADA, best);
			int worstColor = ForgeStats.color(ForgeType.ESPADA, worst);
			log(String.format(Locale.ROOT, "sword damage colors: %s %06X, wood %06X, range %s", hardest.getSerializedName(), bestColor, worstColor, ForgeStats.range(ForgeType.ESPADA, ForgeStats.Stat.DANO)));
			check(bestColor == 0xB45AFF, "the best sword damage should be purple");
			check(worstColor == 0xAA0000, "the worst sword damage should be dark red");
			// Forged shields block from the first tick, so what the plate buys is the width of the parry window.
			ForgeStats.Range window = ForgeStats.range(ForgeType.ESCUDO, ForgeStats.Stat.BLOQUEO);
			check(ForgeStats.rank(window.max(), window, true) == 1.0, "for shields the widest parry window is the best");
			dev.forja.material.ForgeMaterial toughest = dev.forja.material.ForgeMaterial.MADERA;
			for (dev.forja.material.ForgeMaterial material : dev.forja.material.ForgeMaterial.values()) {
				if (material.canBeHead && material.durability > toughest.durability) {
					toughest = material;
				}
			}
			int ironHead = ForgeStats.color(PartType.CABEZA_PICO, ForgeStats.partLines(PartType.CABEZA_PICO, HIERRO).getFirst());
			int bestHead = ForgeStats.color(PartType.CABEZA_PICO, ForgeStats.partLines(PartType.CABEZA_PICO, toughest).getFirst());
			log(String.format(Locale.ROOT, "pickaxe head durability colors: iron %06X, %s %06X", ironHead, toughest.getSerializedName(), bestHead));
			check(bestHead == 0xB45AFF, "the most durable pickaxe head should be purple, " + toughest.getSerializedName() + " was not");
		});
	}

	private static ForgeStats.Line damageLine(ForgeType type, List<dev.forja.material.ForgeMaterial> materials) {
		return ForgeStats.sheet(type, materials, Upgrades.EMPTY).lines().stream().filter(line -> line.stat() == ForgeStats.Stat.DANO).findFirst().orElseThrow();
	}

	private static void checkSpearAndMace(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y) {
		server.runOnServer(s -> {
			HolderLookup.Provider registries = connection.getServerLevel().registryAccess();
			var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);

			check(ForgeType.match(List.of(PartType.MANGO, PartType.PUNTA_LANZA, PartType.ATADURA)) == ForgeType.LANZA, "tip + handle + binding is a spear");
			ItemStack woodSpear = Assembler.create(ForgeType.LANZA, List.of(MADERA, MADERA, CUERO), registries);
			ItemStack spear = Assembler.create(ForgeType.LANZA, List.of(NETHERITA, MADERA, CUERO), registries);
			check(spear.has(DataComponents.KINETIC_WEAPON) && spear.has(DataComponents.PIERCING_WEAPON) && spear.has(DataComponents.DAMAGE_TYPE), "the spear needs vanilla's spear components");
			check(spear.getUseAnimation() == net.minecraft.world.item.ItemUseAnimation.SPEAR, "holding use with the spear should charge it");
			float charge = spear.get(DataComponents.KINETIC_WEAPON).damageMultiplier();
			float woodCharge = woodSpear.get(DataComponents.KINETIC_WEAPON).damageMultiplier();
			log("spear charge multiplier: netherite " + charge + ", wood " + woodCharge);
			check(charge > woodCharge, "a netherite tip should charge harder than wood");
			UpgradeRecipes.Application lunge = UpgradeRecipes.apply(spear, List.of(new ItemStack(Items.SUGAR, 64), ItemStack.EMPTY, ItemStack.EMPTY), registries);
			check(lunge != null && lunge.upgrade() == Upgrade.EMBESTIDA && lunge.after() > 50, "sugar should be Embestida on a spear, got " + describe(lunge));
			ItemStack lunging = UpgradeRecipes.upgraded(spear, ForgeType.LANZA, Upgrade.EMBESTIDA, 100, registries);
			int lungeLevel = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.LUNGE), lunging);
			check(lungeLevel == 3, "Embestida 100% should be Lunge III, got " + lungeLevel);

			UpgradeRecipes.Application reach = UpgradeRecipes.apply(spear, List.of(new ItemStack(Items.END_ROD, 10), ItemStack.EMPTY, ItemStack.EMPTY), registries);
			check(reach != null && reach.upgrade() == Upgrade.ALCANCE && reach.after() > 50, "end rods should be Alcance on a spear, got " + describe(reach));
			float spearReach = UpgradeRecipes.upgraded(spear, ForgeType.LANZA, Upgrade.ALCANCE, 100, registries).get(DataComponents.ATTACK_RANGE).maxReach();
			log("spear reach with Alcance 100%: " + spearReach);
			check(spearReach > 5.9F, "Alcance should lengthen the spear's jab, got " + spearReach);

			ItemStack mace = Assembler.create(ForgeType.MAZO, List.of(HIERRO, MADERA, HIERRO), registries);
			ItemStack heavyMace = Assembler.create(ForgeType.MAZO, List.of(NETHERITA, MADERA, HIERRO), registries);
			check(mace.getItem() instanceof net.minecraft.world.item.MaceItem, "the forged mace should keep the mace smash attack");
			check(mace.getMaxDamage() > 450 && mace.getMaxDamage() <= 650, "an iron mace should last about as long as vanilla's, got " + mace.getMaxDamage());
			float ironSmash = dev.forja.item.ForgedItems.ForgedMaceItem.smashMultiplier(mace);
			float netheriteSmash = dev.forja.item.ForgedItems.ForgedMaceItem.smashMultiplier(heavyMace);
			log("mace smash multiplier: iron " + ironSmash + ", netherite " + netheriteSmash + ", iron durability " + mace.getMaxDamage());
			check(ironSmash == 1.0F && netheriteSmash > ironSmash, "iron smashes like vanilla and netherite harder");
			UpgradeRecipes.Application density = UpgradeRecipes.apply(mace, List.of(new ItemStack(Items.HEAVY_CORE, 2), ItemStack.EMPTY, ItemStack.EMPTY), registries);
			check(density != null && density.upgrade() == Upgrade.DENSIDAD && density.after() > 50, "heavy cores should be Densidad, got " + describe(density));
			int densityLevel = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.DENSITY),
				UpgradeRecipes.upgraded(mace, ForgeType.MAZO, Upgrade.DENSIDAD, 100, registries));
			check(densityLevel == 5, "Densidad 100% should be Density V, got " + densityLevel);
			UpgradeRecipes.Application breach = UpgradeRecipes.apply(density.result(), List.of(new ItemStack(Items.DIAMOND, 5), ItemStack.EMPTY, ItemStack.EMPTY), registries);
			check(breach != null && breach.conflict() == Upgrade.DENSIDAD, "Brecha must be refused next to Densidad, got " + describe(breach));

			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			player.getInventory().setItem(0, lunge.result());
			player.getInventory().setItem(1, Assembler.create(ForgeType.MAZO, List.of(OBSIDIANA_LLORONA, VARA_DE_BLAZE, ORO), registries));
			player.getInventory().setItem(2, Assembler.create(ForgeType.BALLESTA, List.of(ORO, CUERO, MADERA, DIAMANTE), registries));
			ItemStack loaded = Assembler.create(ForgeType.BALLESTA, List.of(NETHERITA, CUERO, VARA_DE_BLAZE, ORO), registries);
			loaded.set(DataComponents.CHARGED_PROJECTILES, net.minecraft.world.item.component.ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
			player.getInventory().setItem(3, loaded);
			player.getInventory().setItem(8, new ItemStack(Items.ARROW, 16));
		});
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		tp(server, x + 40.5, y, 0.5, 0.0F, 0.0F);
		context.waitTicks(15);
		context.takeScreenshot("forja_14_lanza");
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(15);
		context.takeScreenshot("forja_15_lanza_cargando");
		context.getInput().releaseKey(options -> options.keyUse);
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
		context.waitTicks(15);
		context.takeScreenshot("forja_16_mazo");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(10);
		context.takeScreenshot("forja_17_mazo_tercera");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(2));
		context.waitTicks(10);
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(12);
		context.takeScreenshot("forja_18_ballesta_cargando");
		context.getInput().releaseKey(options -> options.keyUse);
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(3));
		context.waitTicks(15);
		context.takeScreenshot("forja_19_ballesta_cargada");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(10);
		context.takeScreenshot("forja_19b_ballesta_tercera");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		context.waitTicks(5);
	}

	/** Moves the real cursor over a point of the open forge screen, given relative to its panel. */
	private static void hover(ClientGameTestContext context, int panelX, int panelY) {
		double[] point = context.computeOnClient(mc -> {
			double[] gui = ((ForgeScreen) mc.gui.screen()).guiPoint(panelX, panelY);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(point[0], point[1]);
	}

	private static void checkBowAndShield(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int areaX = x + 30;
		tp(server, areaX + 0.5, y, z + 0.5, 0.0F, 0.0F);
		context.waitTicks(10);

		// Bow: a diamond-limbed bow draws faster than wood and actually shoots.
		int arrows = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			ItemStack bow = Assembler.create(ForgeType.ARCO, List.of(DIAMANTE, CUERO, MADERA));
			ItemStack woodBow = Assembler.create(ForgeType.ARCO, List.of(MADERA, CUERO, MADERA));
			check(bow.getItem() instanceof dev.forja.item.ForgedItems.ForgedBowItem, "the bow should use the forged bow item");
			check(dev.forja.item.ForgedItems.ForgedBowItem.drawSpeed(bow) > dev.forja.item.ForgedItems.ForgedBowItem.drawSpeed(woodBow), "diamond limbs should draw faster than wood");
			player.getInventory().setItem(0, bow);
			player.getInventory().setItem(8, new ItemStack(Items.ARROW, 16));
			player.getInventory().setSelectedSlot(0);
			bow.releaseUsing(connection.getServerLevel(), player, bow.getUseDuration(player) - 30);
			return connection.getServerLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.Arrow.class, player.getBoundingBox().inflate(4)).size();
		});
		log("bow shot, arrows near the player: " + arrows);
		check(arrows >= 1, "releasing the forged bow should shoot an arrow");

		// First person: draw the bow on the client.
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(18);
		context.takeScreenshot("forja_10_arco_tensado");
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitTicks(10);

		// Shield bash: sneaking and using a forged shield shoves what is in front of the player.
		float[] bashed = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			ItemStack shield = Assembler.create(ForgeType.ESCUDO, List.of(HIERRO, HIERRO, MADERA), level.registryAccess());
			net.minecraft.world.entity.animal.pig.Pig pig = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, level);
			pig.snapTo(player.getX(), player.getY(), player.getZ() + 1.6, 0.0F, 0.0F);
			pig.setNoAi(true);
			level.addFreshEntity(pig);
			float before = pig.getHealth();
			player.setShiftKeyDown(true);
			player.setYRot(0.0F);
			player.setXRot(0.0F);
			player.setItemInHand(InteractionHand.MAIN_HAND, shield);
			shield.use(level, player, InteractionHand.MAIN_HAND);
			player.setShiftKeyDown(false);
			float after = pig.getHealth();
			boolean cooling = player.getCooldowns().isOnCooldown(shield);
			pig.discard();
			player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(shield));
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return new float[] {before - after, cooling ? 1.0F : 0.0F, dev.forja.item.ForgedItems.ForgedShieldItem.bashDamage(shield)};
		});
		// Whirl and quake: the heavy weapons' own move.
		float[] specials = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			java.util.List<net.minecraft.world.entity.animal.pig.Pig> pigs = new java.util.ArrayList<>();
			for (int i = 0; i < 3; i++) {
				net.minecraft.world.entity.animal.pig.Pig pig = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, level);
				pig.snapTo(player.getX() + 1.0 + i, player.getY(), player.getZ(), 0.0F, 0.0F);
				pig.setNoAi(true);
				level.addFreshEntity(pig);
				pigs.add(pig);
			}
			float healthBefore = pigs.stream().map(net.minecraft.world.entity.LivingEntity::getHealth).reduce(0.0F, Float::sum);
			ItemStack greatsword = Assembler.create(ForgeType.ESPADON, List.of(DIAMANTE, DIAMANTE, MADERA, HIERRO), level.registryAccess());
			player.setItemInHand(InteractionHand.MAIN_HAND, greatsword);
			player.setShiftKeyDown(true);
			greatsword.use(level, player, InteractionHand.MAIN_HAND);
			float healthAfterWhirl = pigs.stream().map(net.minecraft.world.entity.LivingEntity::getHealth).reduce(0.0F, Float::sum);
			boolean cooling = player.getCooldowns().isOnCooldown(greatsword);

			ItemStack hammer = Assembler.create(ForgeType.MARTILLO, List.of(HIERRO, MADERA, HIERRO), level.registryAccess());
			player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
			pigs.forEach(pig -> pig.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO));
			hammer.use(level, player, InteractionHand.MAIN_HAND);
			double lift = pigs.stream().mapToDouble(pig -> pig.getDeltaMovement().y).max().orElse(0.0);
			player.setShiftKeyDown(false);
			pigs.forEach(net.minecraft.world.entity.Entity::discard);
			player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(greatsword));
			player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(hammer));
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return new float[] {healthBefore - healthAfterWhirl, cooling ? 1.0F : 0.0F, (float) lift};
		});
		// Vendaval: whatever stands next to the target goes straight up, not sideways.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			ItemStack gale = Assembler.create(ForgeType.MAZO, List.of(HIERRO, MADERA, HIERRO), registries);
			gale = UpgradeRecipes.upgraded(gale, ForgeType.MAZO, Upgrade.DENSIDAD, 60, registries);
			gale = UpgradeRecipes.upgraded(gale, ForgeType.MAZO, Upgrade.ESTALLIDO_DE_VIENTO, 60, registries);
			net.minecraft.world.entity.animal.pig.Pig target = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, level);
			target.snapTo(player.getX() + 1.5, player.getY(), player.getZ(), 0.0F, 0.0F);
			target.setNoAi(true);
			level.addFreshEntity(target);
			net.minecraft.world.entity.animal.pig.Pig bystander = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, level);
			bystander.snapTo(player.getX() + 3.0, player.getY(), player.getZ(), 0.0F, 0.0F);
			bystander.setNoAi(true);
			level.addFreshEntity(bystander);
			dev.forja.upgrade.CombatUpgrades.onWeaponHitForTest(level, player, target, gale, 5.0F);
			bystander.setDeltaMovement(0.4, 0.0, 0.4);
		});
		context.waitTicks(1);
		double[] gust = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var pigs = level.getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class, player.getBoundingBox().inflate(6));
			double up = pigs.stream().mapToDouble(pig -> pig.getDeltaMovement().y).max().orElse(0.0);
			double sideways = pigs.stream().mapToDouble(pig -> pig.getDeltaMovement().horizontalDistance()).max().orElse(0.0);
			pigs.forEach(net.minecraft.world.entity.Entity::discard);
			return new double[] {up, sideways};
		});
		log("vendaval: subida " + gust[0] + ", desplazamiento horizontal " + gust[1]);
		check(gust[0] > 0.6 && gust[1] < 0.05, "Vendaval should throw them up without pushing them sideways");

		// Siega and Embestida: the two moves the scythe and the gauntlets learned.
		double[] moves = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			ItemStack scythe = Assembler.create(ForgeType.GUADANA, List.of(HIERRO, MADERA, CUERO), registries);
			net.minecraft.world.entity.animal.pig.Pig far = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, level);
			far.snapTo(player.getX() + 4.5, player.getY(), player.getZ(), 0.0F, 0.0F);
			far.setNoAi(true);
			level.addFreshEntity(far);
			player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
			player.setShiftKeyDown(true);
			dev.forja.forge.SpecialAttacks.tryUse(level, player, InteractionHand.MAIN_HAND, ForgeType.GUADANA);
			double towards = far.getDeltaMovement().x;

			player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(scythe));
			ItemStack gloves = Assembler.create(ForgeType.GUANTELETES, List.of(CUERO, HIERRO, ORO), registries);
			player.setItemInHand(InteractionHand.MAIN_HAND, gloves);
			player.snapTo(player.getX(), player.getY(), player.getZ(), -90.0F, 0.0F);
			float pigHealth = far.getHealth();
			dev.forja.forge.SpecialAttacks.tryUse(level, player, InteractionHand.MAIN_HAND, ForgeType.GUANTELETES);
			double push = player.getDeltaMovement().horizontalDistance();

			player.setShiftKeyDown(false);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.setDeltaMovement(0.0, 0.0, 0.0);
			float lost = pigHealth - far.getHealth();
			far.discard();
			return new double[] {towards, push, lost};
		});
		log("siega: arrastre " + moves[0] + ", embestida: empuje " + moves[1] + ", golpe " + moves[2]);
		check(moves[0] < -0.2, "the scythe's reap should drag what it catches towards the smith, got " + moves[0]);
		check(moves[1] > 0.8, "the gauntlets should throw the smith forward, got " + moves[1]);

		log("especiales: torbellino quitó " + specials[0] + " de vida, enfriamiento " + (specials[1] > 0) + ", sismo levantó " + specials[2]);
		check(specials[0] > 5.0F && specials[1] > 0.0F, "the greatsword's whirl should cut everything around and go on cooldown, dealt " + specials[0]);
		check(specials[2] > 0.3F, "the hammer's quake should throw nearby mobs into the air, got " + specials[2]);

		log("shield bash: pig lost " + bashed[0] + " health, cooldown " + (bashed[1] > 0) + ", bash damage " + bashed[2]);
		check(bashed[0] > 0.0F && bashed[1] > 0.0F, "a sneaking shield use should bash and go on cooldown");

		// Shield with Puas in the off hand: block a pig's hit and hurt the pig back.
		float pigHealth = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			ItemStack shield = UpgradeRecipes.apply(
				Assembler.create(ForgeType.ESCUDO, List.of(OBSIDIANA, NETHERITA, MADERA)),
				List.of(new ItemStack(Items.POINTED_DRIPSTONE, 20), ItemStack.EMPTY, ItemStack.EMPTY),
				level.registryAccess()
			).result();
			check(shield.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS), "the forged shield must be able to block");
			player.getInventory().setItem(0, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.OFFHAND, shield);
			player.startUsingItem(InteractionHand.OFF_HAND);
			net.minecraft.world.entity.animal.pig.Pig pig = net.minecraft.world.entity.EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			pig.snapTo(player.getX(), player.getY(), player.getZ() + 1.5, 180.0F, 0.0F);
			pig.setNoAi(true);
			level.addFreshEntity(pig);
			return pig.getHealth();
		});
		// Peaceful zeroes mob damage to players before any shield check, so hit on Easy.
		server.runCommand("difficulty easy");
		context.waitTicks(20);
		float after = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var pig = level.getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class, player.getBoundingBox().inflate(4)).getFirst();
			check(player.isBlocking(), "the player should be blocking with the forged shield");
			player.hurtServer(level, level.damageSources().mobAttack(pig), 4.0F);
			return pig.getHealth();
		});
		server.runCommand("difficulty peaceful");
		log("puas: pig health " + pigHealth + " -> " + after);
		check(after < pigHealth, "Puas should hurt whoever hits the shield");

		context.getInput().holdKey(options -> options.keyUse);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(15);
		context.takeScreenshot("forja_11_escudo_bloqueando");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(5);
		context.takeScreenshot("forja_12_escudo_primera_persona");
		context.getInput().releaseKey(options -> options.keyUse);
		server.runOnServer(s -> connection.getServerPlayer().setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY));
		server.runCommand("kill @e[type=pig]");

	}

	/**
	 * The raiders' camp. It goes last of all, because a fifteen-block camp dropped next to the tables
	 * would bury them, and by now nothing else needs them.
	 */
	/**
	 * The techniques are choices with teeth, so each one is checked by what it actually changes at the
	 * star rather than by the tab that hands it out.
	 */
	/**
	 * The upgrades the newest events and the trident brought: each one checked by what it does to
	 * whatever is standing in front of the smith.
	 */
	/**
	 * Writes docs/MEJORAS.md out of the enums themselves: every upgrade, what feeds it and what it does
	 * at 100%. The README used to carry that table by hand and it fell three dozen upgrades behind, so
	 * now the test that already knows all of this writes it instead.
	 */
	/**
	 * A fence around the numbers. Nothing here is a design rule, it is a tripwire: if a change sends a
	 * weapon, a piece of armour or a tool outside the range the mod has lived in, the test says so before
	 * anyone finds out in a world.
	 */
	/** The parts cabinet: a chest that only takes a smith's things, and drops them when it is broken. */
	/**
	 * A portrait of every mob the mod puts in the world, taken against clean ground at noon: the three
	 * of our own and an elite, which is a vanilla monster wearing a legend.
	 */
	/**
	 * The ten moves added on 2026-09-18, checked by their effect rather than by their existence: a move
	 * that compiles and does nothing is the failure mode worth catching.
	 */
	private static void checkNewAttacks(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("difficulty easy");
		int px = x + 60;
		int pz = z + 60;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", px - 16, y - 1, pz - 16, px + 16, y - 1, pz + 16));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 16, y, pz - 16, px + 16, y + 6, pz + 16));

		// ---- Herrero caido: the ring off the hammer, and the hook on the dead arm.
		float[] wave = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.FallenSmith smith = spawnSmith(level, px, y, pz);
			net.minecraft.world.entity.monster.zombie.Zombie victim = zombie(level, px + 5.0, y, pz);
			smith.setTarget(victim);
			float before = victim.getHealth();
			double beforeY = victim.getY();
			// Long enough for the wave to come off cooldown and for the ring to reach him.
			for (int tick = 0; tick < dev.forja.entity.FallenSmith.WAVE_COOLDOWN + dev.forja.entity.FallenSmith.WAVE_TICKS + 20; tick++) {
				smith.setTarget(victim);
				smith.tick();
			}
			float damage = before - victim.getHealth();
			boolean launched = victim.getDeltaMovement().y > 0.2 || victim.getY() > beforeY + 0.1;
			boolean burning = victim.isOnFire();
			victim.discard();
			smith.discard();
			return new float[] {damage, launched ? 1 : 0, burning ? 1 : 0};
		});
		log("onda de yunque: dano " + wave[0] + ", lo levanta " + (wave[1] > 0) + ", lo prende " + (wave[2] > 0));
		// The dummy wears a little armour, so the blow lands short of the number on the tin.
		check(wave[0] >= dev.forja.entity.FallenSmith.WAVE_DAMAGE * 0.8F, "the shockwave should hit for about its damage, got " + wave[0]);
		check(wave[1] > 0, "the shockwave should take them off the floor");
		check(wave[2] > 0, "the shockwave should set them alight");

		float[] hook = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.FallenSmith smith = spawnSmith(level, px, y, pz);
			net.minecraft.world.entity.monster.zombie.Zombie victim = zombie(level, px + 12.0, y, pz);
			float before = victim.getHealth();
			double away = victim.distanceTo(smith);
			smith.setTarget(victim);
			smith.hookIn(level, victim);
			// The claw does not leave his hand on the tick he decides to throw it any more: he cocks
			// the dead arm first. Measuring during that is the point of measuring at all — an attack
			// that lands the instant it starts cannot be answered, and until now every one of his did.
			float midCharge = 0.0F;
			for (int tick = 0; tick < dev.forja.entity.FallenSmith.HOOK_WINDUP - 1; tick++) {
				smith.setTarget(victim);
				smith.tick();
				midCharge = Math.max(midCharge, before - victim.getHealth());
			}
			smith.setTarget(victim);
			smith.tick();
			double pull = victim.getDeltaMovement().horizontalDistance();
			float damage = before - victim.getHealth();
			// The pull has to point at him, not just move him.
			net.minecraft.world.phys.Vec3 toward = smith.position().subtract(victim.position()).normalize();
			double aligned = toward.x * victim.getDeltaMovement().x + toward.z * victim.getDeltaMovement().z;
			victim.discard();
			smith.discard();
			return new float[] {damage, (float) pull, (float) aligned, (float) away, midCharge};
		});
		log("garfio: dano " + hook[0] + ", tiron " + hook[1] + ", hacia el herrero " + hook[2]
			+ ", dano durante el aviso " + hook[4]);
		check(hook[4] == 0.0F, "the hook should not bite while he is still cocking his arm, got " + hook[4]);
		check(hook[0] >= dev.forja.entity.FallenSmith.HOOK_DAMAGE * 0.8F, "the hook should bite, got " + hook[0]);
		check(hook[1] > 1.0F, "the hook should haul them in hard, got " + hook[1]);
		check(hook[2] > 0.0F, "the hook should pull them toward the smith, got " + hook[2]);

		// ---- Automata: the ember it spits, and the steam it vents when you crowd it.
		int[] ember = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.ForgeAutomaton automaton = dev.forja.registry.ModEntities.AUTOMATA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(automaton != null, "the automaton should be creatable");
			automaton.snapTo(px, y, pz, 0.0F, 0.0F);
			automaton.setNoAi(true);
			level.addFreshEntity(automaton);
			net.minecraft.world.entity.monster.zombie.Zombie victim = zombie(level, px + 8.0, y, pz);
			for (int tick = 0; tick < dev.forja.entity.ForgeAutomaton.EMBER_COOLDOWN + dev.forja.entity.ForgeAutomaton.EMBER_WINDUP + 10; tick++) {
				automaton.setTarget(victim);
				automaton.tick();
			}
			int fireballs = level.getEntitiesOfClass(
				net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball.class,
				automaton.getBoundingBox().inflate(20.0)
			).size();
			level.getEntitiesOfClass(net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball.class,
				automaton.getBoundingBox().inflate(20.0)).forEach(net.minecraft.world.entity.Entity::discard);
			victim.discard();
			automaton.discard();
			return new int[] {fireballs};
		});
		log("brasa del automata: proyectiles escupidos " + ember[0]);
		check(ember[0] >= 1, "the automaton should spit an ember at something it cannot reach, got " + ember[0]);

		// Coz de escoria: the cone in front, the wind-up before it, and the ground that stays lit.
		float[] slag = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.ForgeAutomaton automaton = dev.forja.registry.ModEntities.AUTOMATA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(automaton != null, "the automaton should be creatable");
			automaton.snapTo(px, y, pz, 0.0F, 0.0F);
			automaton.setNoAi(true);
			level.addFreshEntity(automaton);
			// One in front of it and one directly behind: a cone that catches both is not a cone.
			var ahead = zombie(level, px + 4.0, y, pz);
			var behind = zombie(level, px - 4.0, y, pz);
			ahead.setNoAi(true);
			behind.setNoAi(true);
			float aheadBefore = ahead.getHealth();
			float behindBefore = behind.getHealth();
			automaton.setTarget(ahead);
			automaton.slagStomp(level, ahead);
			float midCharge = 0.0F;
			for (int tick = 0; tick < dev.forja.entity.ForgeAutomaton.SLAG_WINDUP - 1; tick++) {
				automaton.setTarget(ahead);
				automaton.tick();
				midCharge = Math.max(midCharge, aheadBefore - ahead.getHealth());
			}
			automaton.setTarget(ahead);
			automaton.tick();
			float onLanding = aheadBefore - ahead.getHealth();
			// And then it is left burning: nobody touches them again, the floor does.
			ahead.setRemainingFireTicks(0);
			float afterLanding = ahead.getHealth();
			for (int tick = 0; tick < dev.forja.entity.ForgeAutomaton.SLAG_POOL_EVERY * 3; tick++) {
				automaton.setTarget(ahead);
				automaton.tick();
			}
			float lingering = afterLanding - ahead.getHealth();
			float behindTook = behindBefore - behind.getHealth();
			ahead.discard();
			behind.discard();
			automaton.discard();
			return new float[] {onLanding, midCharge, lingering, behindTook};
		});
		log("coz de escoria: dano al caer " + slag[0] + ", durante el aviso " + slag[1]
			+ ", escoria despues " + slag[2] + ", a lo que tiene detras " + slag[3]);
		check(slag[1] == 0.0F, "the stomp should not bite while its fists are still up, got " + slag[1]);
		check(slag[0] >= dev.forja.entity.ForgeAutomaton.SLAG_DAMAGE * 0.7F,
			"the stomp should land for about its damage, got " + slag[0]);
		check(slag[2] > 0.0F, "the slag should keep burning what stands in it, got " + slag[2]);
		check(slag[3] == 0.0F, "the stomp should not reach round behind it, got " + slag[3]);

		// ---- Herrumbre: it costs you durability, not health, and it goes for the piece nearest to
		// breaking. That last part is the design: eating your *best* plate would mean the only sensible
		// answer is never to wear it, which is not a choice worth offering anybody.
		float[] rust = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var registries = level.registryAccess();
			var swarm = dev.forja.registry.ModEntities.HERRUMBRE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(swarm != null, "the rust flake should be creatable");
			swarm.snapTo(px, y, pz, 0.0F, 0.0F);
			swarm.setNoAi(true);
			level.addFreshEntity(swarm);

			var victim = zombie(level, px + 0.6, y, pz);
			// A fresh helmet and a chestplate that has already taken a beating.
			ItemStack helmet = Assembler.create(ForgeType.CASCO, List.of(HIERRO, CUERO), registries);
			ItemStack chest = Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO), registries);
			chest.setDamageValue(chest.getMaxDamage() * 3 / 4);
			victim.setItemSlot(EquipmentSlot.HEAD, helmet);
			victim.setItemSlot(EquipmentSlot.CHEST, chest);
			int helmetBefore = helmet.getDamageValue();
			int chestBefore = chest.getDamageValue();
			float healthBefore = victim.getHealth();

			swarm.doHurtTarget(level, victim);

			int helmetAfter = victim.getItemBySlot(EquipmentSlot.HEAD).getDamageValue();
			int chestAfter = victim.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
			float healthLost = healthBefore - victim.getHealth();

			// And once more against somebody wearing nothing, which must not throw.
			var bare = zombie(level, px + 1.4, y, pz);
			bare.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			bare.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			float bareBefore = bare.getHealth();
			swarm.doHurtTarget(level, bare);
			float bareLost = bareBefore - bare.getHealth();

			victim.discard();
			bare.discard();
			swarm.discard();
			return new float[] {chestAfter - chestBefore, helmetAfter - helmetBefore, healthLost, bareLost};
		});
		log("herrumbre: desgaste a la pechera tocada " + (int) rust[0] + ", al casco nuevo " + (int) rust[1]
			+ ", vida quitada " + rust[2] + ", a uno sin armadura " + rust[3]);
		check(rust[0] >= dev.forja.entity.RustSwarm.WEAR,
			"the rust flake should eat the piece nearest to breaking, got " + (int) rust[0]);
		check(rust[1] == 0.0F,
			"and leave the fresh one alone, got " + (int) rust[1]);
		check(rust[2] <= dev.forja.entity.RustSwarm.BITE + 0.01F,
			"its bite should cost almost no health, got " + rust[2]);
		check(rust[3] >= 0.0F, "and biting somebody with no armour should not throw");

		// ---- Ascua mayor: the interesting question is not whether you can kill it, it is where.
		int[] bigEmber = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var before = level.getEntitiesOfClass(dev.forja.entity.EmberWisp.class,
				new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(20)).size();
			var mayor = dev.forja.registry.ModEntities.ASCUA_MAYOR.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(mayor != null, "the greater ember should be creatable");
			mayor.snapTo(px, y + 1.0, pz, 0.0F, 0.0F);
			mayor.setNoAi(true);
			level.addFreshEntity(mayor);
			mayor.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
			var shards = level.getEntitiesOfClass(dev.forja.entity.EmberWisp.class,
				new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(20));
			int born = shards.size() - before;
			for (var shard : shards) {
				shard.discard();
			}
			mayor.discard();
			return new int[] {born};
		});
		log("ascua mayor: pavesas al morir " + bigEmber[0]);
		check(bigEmber[0] == dev.forja.entity.GreaterEmber.SHARDS,
			"the greater ember should leave " + dev.forja.entity.GreaterEmber.SHARDS + " wisps, got " + bigEmber[0]);

		// ---- Escoria viviente: it halves, the last pieces cool into escoria, and the floor stays hot.
		int[] lump = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var box = new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(20);
			for (var stray : level.getEntitiesOfClass(dev.forja.entity.LivingSlag.class, box)) {
				stray.discard();
			}
			var big = dev.forja.registry.ModEntities.ESCORIA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(big != null, "the living slag should be creatable");
			big.setSize(dev.forja.entity.LivingSlag.BIG);
			big.snapTo(px, y, pz, 0.0F, 0.0F);
			big.setNoAi(true);
			level.addFreshEntity(big);
			int poolsBefore = dev.forja.world.SlagPools.count();
			int fullHealth = (int) big.getMaxHealth();
			big.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
			// Only the halves. The one that just died is still in the world until the next tick, so a
			// plain count of everything nearby comes back one too many and the first of them is the
			// corpse — which is how the first version of this check read "three halves of size three".
			var halves = level.getEntitiesOfClass(dev.forja.entity.LivingSlag.class, box,
				piece -> piece.isAlive() && piece.size() < dev.forja.entity.LivingSlag.BIG);
			int halfSize = halves.isEmpty() ? -1 : halves.get(0).size();
			int poolsAfter = dev.forja.world.SlagPools.count();

			// And the smallest one leaves the material instead of more of itself.
			var last = dev.forja.registry.ModEntities.ESCORIA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			last.setSize(dev.forja.entity.LivingSlag.SMALLEST);
			last.snapTo(px + 4.0, y, pz, 0.0F, 0.0F);
			last.setNoAi(true);
			level.addFreshEntity(last);
			last.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
			int dropped = 0;
			for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(new BlockPos((int) px + 4, y, (int) pz)).inflate(4))) {
				if (item.getItem().is(dev.forja.registry.ModItems.ESCORIA)) {
					dropped += item.getItem().getCount();
				}
				item.discard();
			}
			for (var piece : level.getEntitiesOfClass(dev.forja.entity.LivingSlag.class, box)) {
				piece.discard();
			}
			return new int[] {halves.size(), halfSize, fullHealth, poolsAfter - poolsBefore, dropped};
		});
		log("escoria viviente: mitades " + lump[0] + " de tamano " + lump[1] + ", vida del grande " + lump[2]
			+ ", charcos nuevos " + lump[3] + ", escoria del pequeno " + lump[4]);
		check(lump[0] == 2, "a big slag should halve into two, got " + lump[0]);
		check(lump[1] == dev.forja.entity.LivingSlag.BIG - 1,
			"and the halves should be one size smaller, got " + lump[1]);
		check(lump[3] >= 1, "it should leave the ground burning, got " + lump[3] + " pools");
		check(lump[4] >= 1, "the smallest should cool into escoria, got " + lump[4]);

		// ---- Yunque andante: it mends ours, worst first, and never more than three at a time.
		float[] anvil = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var box = new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(20);
			for (var stray : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box)) {
				stray.discard();
			}
			var anvilMob = dev.forja.registry.ModEntities.YUNQUE_ANDANTE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(anvilMob != null, "the walking anvil should be creatable");
			anvilMob.snapTo(px, y, pz, 0.0F, 0.0F);
			anvilMob.setNoAi(true);
			level.addFreshEntity(anvilMob);

			// One of ours, hurt, and a zombie hurt just as badly: only one of them is its problem.
			var plate = hollow(level, px + 2.0, y, pz);
			plate.setHealth(plate.getMaxHealth() * 0.3F);
			float plateBefore = plate.getHealth();
			var outsider = zombie(level, px + 3.0, y, pz);
			outsider.setHealth(outsider.getMaxHealth() * 0.3F);
			float outsiderBefore = outsider.getHealth();

			anvilMob.weld(level);

			float mended = plate.getHealth() - plateBefore;
			float strayMended = outsider.getHealth() - outsiderBefore;
			plate.discard();
			outsider.discard();
			anvilMob.discard();
			return new float[] {mended, strayMended};
		});
		log("yunque andante: suelda a los nuestros " + anvil[0] + ", a un zombi " + anvil[1]);
		check(anvil[0] >= dev.forja.entity.WalkingAnvil.WELD_AMOUNT * 0.9F,
			"the anvil should mend our own, got " + anvil[0]);
		check(anvil[1] == 0.0F, "and leave everything else alone, got " + anvil[1]);

		// ---- Percutor: the ram takes a long time coming and takes the shield with it.
		float[] striker = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			player.setPos(px + 2.0, y, pz);
			player.getInventory().clearContent();
			ItemStack shield = Assembler.create(ForgeType.ESCUDO, List.of(HIERRO, MADERA, CUERO), registries);
			player.setItemSlot(EquipmentSlot.OFFHAND, shield);
			player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
			boolean blockingBefore = player.isBlocking();

			var ram = dev.forja.registry.ModEntities.PERCUTOR.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(ram != null, "the striker should be creatable");
			ram.snapTo(px, y, pz, 0.0F, 0.0F);
			ram.setNoAi(true);
			level.addFreshEntity(ram);
			ram.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.position());
			ram.setTarget(player);
			ram.drop(level);

			float healthBefore = player.getHealth();
			float midCharge = 0.0F;
			for (int tick = 0; tick < dev.forja.entity.Striker.DROP_WINDUP - 1; tick++) {
				ram.setTarget(player);
				ram.tick();
				midCharge = Math.max(midCharge, healthBefore - player.getHealth());
			}
			ram.setTarget(player);
			ram.tick();
			float landed = healthBefore - player.getHealth();
			boolean blockingAfter = player.isBlocking();
			int cooldown = player.getCooldowns().isOnCooldown(shield) ? 1 : 0;

			player.stopUsingItem();
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			player.setHealth(player.getMaxHealth());
			ram.discard();
			return new float[] {landed, midCharge, blockingBefore ? 1 : 0, blockingAfter ? 1 : 0, cooldown};
		});
		log("percutor: dano al caer " + striker[0] + ", durante el aviso " + striker[1]
			+ ", bloqueaba antes " + (striker[2] > 0) + " despues " + (striker[3] > 0)
			+ ", escudo en enfriamiento " + (striker[4] > 0));
		check(striker[1] == 0.0F, "the ram should not land while it is still climbing, got " + striker[1]);
		check(striker[0] > 0.0F, "and should land for something, got " + striker[0]);
		check(striker[2] > 0, "the test should have had the shield up to begin with");
		check(striker[3] == 0.0F, "the ram should break the guard, still blocking after");
		check(striker[4] > 0, "and put the shield on cooldown");

		// ---- Tenaza: it holds you where you are, and costs almost nothing to be held by.
		float[] tongs = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var grip = dev.forja.registry.ModEntities.TENAZA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(grip != null, "the tongs should be creatable");
			grip.snapTo(px, y, pz, 0.0F, 0.0F);
			grip.setNoAi(true);
			level.addFreshEntity(grip);
			var caught = zombie(level, px + 5.0, y, pz);
			caught.setDeltaMovement(0.5, 0.0, 0.0);
			float before = caught.getHealth();
			grip.setTarget(caught);
			grip.grab(level, caught);
			float midCharge = 0.0F;
			for (int tick = 0; tick < dev.forja.entity.Tongs.GRAB_WINDUP - 1; tick++) {
				grip.setTarget(caught);
				grip.tick();
				midCharge = Math.max(midCharge, before - caught.getHealth());
			}
			grip.setTarget(caught);
			grip.tick();
			float taken = before - caught.getHealth();
			// It used to hold you with two mob effects and both were wrong: slowness at amplifier six
			// is a field-of-view change first, and JUMP_BOOST 128 stopped wrapping to -128 when the
			// amplifier became an int, so the "cannot jump" trick fired you into the sky. It holds you
			// properly now, so the test asks the question properly: does it stop you walking out.
			int held = grip.holding() == caught ? 1 : 0;
			int effects = caught.getActiveEffects().size();
			// Walk hard for a second and see how far it gets.
			net.minecraft.world.phys.Vec3 escapedFrom = caught.position();
			for (int tick = 0; tick < 20; tick++) {
				caught.setDeltaMovement(0.8, 0.0, 0.0);
				grip.tick();
				caught.move(net.minecraft.world.entity.MoverType.SELF, caught.getDeltaMovement());
			}
			float drift = (float) caught.position().distanceTo(escapedFrom);
			caught.discard();
			grip.discard();
			return new float[] {taken, midCharge, drift, held, effects};
		});
		log("tenaza: dano " + tongs[0] + ", durante el aviso " + tongs[1] + ", se aleja " + tongs[2]
			+ " en un segundo tirando, sujeto " + (tongs[3] > 0) + ", efectos puestos " + (int) tongs[4]);
		check(tongs[1] == 0.0F, "the tongs should not bite while they are still opening, got " + tongs[1]);
		check(tongs[0] <= dev.forja.entity.Tongs.GRAB_DAMAGE + 0.01F,
			"being held should cost almost no health, got " + tongs[0]);
		check(tongs[3] > 0, "but it should hold whatever it caught");
		check(tongs[2] < 1.5F, "and a second of walking should get it almost nowhere, got " + tongs[2]);
		check(tongs[4] == 0.0F, "and it should do it without a single mob effect, put on " + (int) tongs[4]);

		// ---- Cargador de carbon: the fuse is long, the blast is real, and it leaves its load behind.
		float[] hauler = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var box = new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(20);
			for (var stray : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box)) {
				stray.discard();
			}
			for (var stray : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
				stray.discard();
			}
			var beast = dev.forja.registry.ModEntities.CARGADOR_DE_CARBON.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(beast != null, "the coal hauler should be creatable");
			beast.snapTo(px, y, pz, 0.0F, 0.0F);
			beast.setNoAi(true);
			level.addFreshEntity(beast);

			// One inside the circle and one well outside it, so the reach is measured rather than assumed.
			var close = zombie(level, px + 1.5, y, pz);
			close.setHealth(close.getMaxHealth());
			float closeBefore = close.getHealth();
			var far = zombie(level, px + 9.0, y, pz);
			far.setHealth(far.getMaxHealth());
			float farBefore = far.getHealth();

			beast.prime(level);
			float midFuse = 0.0F;
			for (int tick = 0; tick < dev.forja.entity.CoalHauler.PRIME_WINDUP - 1; tick++) {
				beast.tick();
				midFuse = Math.max(midFuse, closeBefore - close.getHealth());
			}
			beast.tick();
			float hit = closeBefore - close.getHealth();
			float missed = farBefore - far.getHealth();
			boolean gone = !beast.isAlive();
			int coal = 0;
			for (var dropped : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
				if (dropped.getItem().is(net.minecraft.world.item.Items.COAL)) {
					coal += dropped.getItem().getCount();
				}
				dropped.discard();
			}
			close.discard();
			far.discard();
			beast.discard();
			return new float[] {hit, midFuse, missed, gone ? 1 : 0, coal};
		});
		log("cargador: dano dentro " + hauler[0] + ", durante la mecha " + hauler[1]
			+ ", fuera del circulo " + hauler[2] + ", se consume " + (hauler[3] > 0)
			+ ", carbon en el suelo " + hauler[4]);
		check(hauler[1] == 0.0F, "the hauler should not hurt anything while the fuse runs, got " + hauler[1]);
		check(hauler[0] > 0.0F, "and should hurt what is inside the circle, got " + hauler[0]);
		check(hauler[2] == 0.0F, "and nothing outside it, got " + hauler[2]);
		check(hauler[3] > 0, "a hauler that blows is spent");
		check(hauler[4] >= 4, "and leaves its load, got " + hauler[4]);

		// ---- Templador: no damage at all, and the combo goes out.
		float[] quencher = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			player.setPos(px + 4.0, y, pz);
			player.removeAllEffects();
			player.setHealth(player.getMaxHealth());
			player.setRemainingFireTicks(80);
			// Four blows into a combo, which is where Frenesi starts paying.
			for (int hit = 0; hit < 4; hit++) {
				dev.forja.upgrade.Frenzy.onHit(player);
			}
			int comboBefore = dev.forja.upgrade.Frenzy.shownHits(player);

			var smith = dev.forja.registry.ModEntities.TEMPLADOR.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(smith != null, "the quencher should be creatable");
			smith.snapTo(px, y, pz, 0.0F, 0.0F);
			smith.setNoAi(true);
			level.addFreshEntity(smith);
			float healthBefore = player.getHealth();
			smith.douse(level, player.position());
			for (int tick = 0; tick < dev.forja.entity.Quencher.DOUSE_WINDUP; tick++) {
				smith.tick();
			}
			int comboAfter = dev.forja.upgrade.Frenzy.shownHits(player);
			float taken = healthBefore - player.getHealth();
			int burning = player.getRemainingFireTicks();
			int pools = dev.forja.world.OilPools.count();
			player.setHealth(player.getMaxHealth());
			smith.discard();
			return new float[] {comboBefore, comboAfter, taken, burning, pools};
		});
		log("templador: combo antes " + (int) quencher[0] + " despues " + (int) quencher[1]
			+ ", vida quitada " + quencher[2] + ", fuego restante " + (int) quencher[3]
			+ ", charcos " + (int) quencher[4]);
		check(quencher[0] >= 4, "the test should have built a combo first, got " + quencher[0]);
		check(quencher[1] == 0.0F, "the oil should wipe the combo, still at " + quencher[1]);
		check(quencher[2] == 0.0F, "and should cost no health at all, took " + quencher[2]);
		check(quencher[3] == 0.0F, "and should put the fire out, still burning " + quencher[3]);
		check(quencher[4] >= 1, "and should leave a pool behind");

		// ---- Nucleo estelar: hitting it is worse than not hitting it.
		float[] core = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var stone = dev.forja.registry.ModEntities.NUCLEO_ESTELAR.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(stone != null, "the star core should be creatable");
			stone.snapTo(px, y + 1, pz, 0.0F, 0.0F);
			stone.setNoAi(true);
			level.addFreshEntity(stone);

			// Whatever feeds it is also what the beam comes back at, and it needs two things: room for
			// a thirty-point return, which rules out a player's twenty hit points, and to not be one
			// of ours, which rules out the percutor this used to use — the truce now covers the core
			// in both directions, so our own mobs can neither fill it nor be hit by it.
			var feeder = net.minecraft.world.entity.EntityTypes.IRON_GOLEM.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(feeder != null, "an iron golem should be creatable");
			feeder.snapTo(px + 3.0, y, pz, 0.0F, 0.0F);
			feeder.setNoAi(true);
			level.addFreshEntity(feeder);

			float coreHealthBefore = stone.getHealth();
			int coldTint = stone.tint();
			// Eight blows of three, which is exactly its capacity.
			float fed = 0.0F;
			for (int hit = 0; hit < 8; hit++) {
				stone.invulnerableTime = 0;
				stone.hurtServer(level, level.damageSources().mobAttack(feeder), 3.0F);
				fed += 3.0F;
			}
			float stored = stone.stored();
			float coreLost = coreHealthBefore - stone.getHealth();
			int fullTint = stone.tint();

			float feederBefore = feeder.getHealth();
			stone.release(level);
			float midBeam = 0.0F;
			for (int tick = 0; tick < dev.forja.entity.StarCore.RELEASE_WINDUP - 1; tick++) {
				feeder.invulnerableTime = 0;
				stone.tick();
				midBeam = Math.max(midBeam, feederBefore - feeder.getHealth());
			}
			feeder.invulnerableTime = 0;
			stone.tick();
			float returned = feederBefore - feeder.getHealth();
			feeder.discard();
			stone.discard();
			return new float[] {fed, stored, coreLost, returned, midBeam,
				coldTint == fullTint ? 0 : 1, stone.charge()};
		});
		log("nucleo: le metimos " + core[0] + ", guardo " + core[1] + ", vida perdida " + core[2]
			+ ", devolvio " + core[3] + ", durante el aviso " + core[4]
			+ ", cambia de color " + (core[5] > 0));
		check(core[2] == 0.0F, "a core that had room should take no damage at all, lost " + core[2]);
		check(core[1] >= dev.forja.entity.StarCore.CAPACITY - 0.01F,
			"eight blows of three should fill it, holds " + core[1]);
		check(core[4] == 0.0F, "the beam should not land while it is still aiming, got " + core[4]);
		check(core[3] > core[0], "and should give back more than went in, gave " + core[3] + " for " + core[0]);
		check(core[5] > 0, "and should look different full than empty");
		check(core[6] == 0.0F, "and should be empty afterwards, still at " + core[6]);

		// ---- Molde roto: it copies what hit it, and dies holding the pattern.
		float[] mould = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			player.setPos(px + 2.0, y, pz);
			player.getInventory().clearContent();
			ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, CUERO), registries);
			player.setItemSlot(EquipmentSlot.MAINHAND, sword);

			var golem = dev.forja.registry.ModEntities.MOLDE_ROTO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(golem != null, "the broken mould should be creatable");
			golem.snapTo(px, y, pz, 0.0F, 0.0F);
			golem.setNoAi(true);
			level.addFreshEntity(golem);
			golem.setTarget(player);

			float before = (float) golem.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
			int shapeBefore = golem.copied() == null ? 0 : 1;
			golem.consider(level, sword);
			for (int tick = 0; tick < dev.forja.entity.BrokenMould.RECAST_WINDUP; tick++) {
				golem.tick();
			}
			float after = (float) golem.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
			int shapeAfter = golem.copied() == dev.forja.part.PartType.HOJA ? 1 : 0;
			float swordDamage = (float) dev.forja.entity.BrokenMould.damageOf(sword);

			// A second one, fed something worth copying. An iron sword is weaker than the bar of hot
			// metal it already swings, so copying one correctly changes nothing — the interesting
			// assertion is that a good weapon does move the number.
			// Four slots on a greatsword: two blade halves, a handle and a guard.
			ItemStack greatsword = Assembler.create(ForgeType.ESPADON,
				List.of(NETHERITA, NETHERITA, MADERA, HIERRO), registries);
			var rich = dev.forja.registry.ModEntities.MOLDE_ROTO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(rich != null, "the broken mould should be creatable");
			rich.snapTo(px + 10.0, y, pz, 0.0F, 0.0F);
			rich.setNoAi(true);
			level.addFreshEntity(rich);
			float richBefore = (float) rich.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
			rich.consider(level, greatsword);
			for (int tick = 0; tick < dev.forja.entity.BrokenMould.RECAST_WINDUP; tick++) {
				rich.tick();
			}
			float richAfter = (float) rich.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
			float greatswordDamage = (float) dev.forja.entity.BrokenMould.damageOf(greatsword);
			rich.discard();

			// And a vanilla stick, which it has no shape for: it should stay exactly as it is.
			var plain = dev.forja.registry.ModEntities.MOLDE_ROTO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(plain != null, "the broken mould should be creatable");
			plain.snapTo(px + 6.0, y, pz, 0.0F, 0.0F);
			plain.setNoAi(true);
			level.addFreshEntity(plain);
			plain.consider(level, new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
			for (int tick = 0; tick < dev.forja.entity.BrokenMould.RECAST_WINDUP; tick++) {
				plain.tick();
			}
			int copiedVanilla = plain.copied() == null ? 0 : 1;
			plain.discard();

			// Killing it: the shape it was wearing comes off as an engraved template.
			var box = new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(20);
			for (var stray : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
				stray.discard();
			}
			golem.die(level.damageSources().playerAttack(player));
			int templates = 0;
			for (var dropped : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
				if (dev.forja.item.TemplateItem.pattern(dropped.getItem()) == dev.forja.part.PartType.HOJA) {
					templates++;
				}
				dropped.discard();
			}
			player.getInventory().clearContent();
			golem.discard();
			return new float[] {before, after, swordDamage, shapeBefore, shapeAfter, copiedVanilla, templates,
				richBefore, richAfter, greatswordDamage};
		});
		log("molde: con espada de hierro (pega " + mould[2] + ") va de " + mould[0] + " a " + mould[1]
			+ ", con espadon de netherita (pega " + mould[9] + ") va de " + mould[7] + " a " + mould[8]
			+ ", copia la hoja " + (mould[4] > 0) + ", copia una vanilla " + (mould[5] > 0)
			+ ", plantillas soltadas " + (int) mould[6]);
		check(mould[3] == 0.0F, "a fresh mould has copied nothing");
		check(mould[4] > 0, "it should copy the blade of a forged sword");
		check(mould[1] >= mould[0], "and should never come out of the furnace worse, went from " + mould[0] + " to " + mould[1]);
		check(mould[8] > mould[7], "a weapon worth copying should move the number, went from " + mould[7] + " to " + mould[8]);
		check(mould[8] <= dev.forja.entity.BrokenMould.COPY_CEILING + 0.01F,
			"but never past the ceiling, got " + mould[8]);
		check(mould[5] == 0.0F, "but should have no shape for a vanilla weapon");
		check(mould[6] >= 1, "and should drop the pattern it died wearing, got " + (int) mould[6]);

		// ---- Guardian de cuno: sealed it takes nothing, unsealed it takes everything.
		float[] cune = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			player.setPos(px + 4.0, y, pz);
			player.removeAllEffects();
			player.setHealth(player.getMaxHealth());
			var box = new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(20);
			for (var stray : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box)) {
				stray.discard();
			}

			var die = dev.forja.registry.ModEntities.GUARDIAN_DE_CUNO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(die != null, "the cune guardian should be creatable");
			die.snapTo(px, y, pz, 0.0F, 0.0F);
			die.setNoAi(true);
			level.addFreshEntity(die);

			// Three seals on the floor around it, the way the castle stands them up.
			BlockPos[] lanterns = {
				new BlockPos((int) px + 5, y, (int) pz),
				new BlockPos((int) px - 5, y, (int) pz),
				new BlockPos((int) px, y, (int) pz + 6),
			};
			for (BlockPos spot : lanterns) {
				level.setBlockAndUpdate(spot, dev.forja.registry.ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
			}
			die.tick();
			int seen = die.seals();

			float sealedBefore = die.getHealth();
			for (int blow = 0; blow < 6; blow++) {
				die.invulnerableTime = 0;
				die.hurtServer(level, level.damageSources().playerAttack(player), 12.0F);
			}
			float sealedLost = sealedBefore - die.getHealth();

			// Two out of three is still sealed, which is the part that has to be true or the fight is
			// "break one lantern" rather than "break them all".
			level.removeBlock(lanterns[0], false);
			level.removeBlock(lanterns[1], false);
			for (int tick = 0; tick < 12; tick++) {
				die.tick();
			}
			int leftStanding = die.seals();
			float partialBefore = die.getHealth();
			die.invulnerableTime = 0;
			die.hurtServer(level, level.damageSources().playerAttack(player), 12.0F);
			float partialLost = partialBefore - die.getHealth();

			level.removeBlock(lanterns[2], false);
			for (int tick = 0; tick < 12; tick++) {
				die.tick();
			}
			int afterAll = die.seals();
			float openBefore = die.getHealth();
			die.invulnerableTime = 0;
			die.hurtServer(level, level.damageSources().playerAttack(player), 12.0F);
			float openLost = openBefore - die.getHealth();

			// And the stamp, which is the longest wind-up in the mod.
			var near = zombie(level, px + 1.0, y, pz);
			near.setHealth(near.getMaxHealth());
			float nearBefore = near.getHealth();
			var away = zombie(level, px + 12.0, y, pz);
			away.setHealth(away.getMaxHealth());
			float awayBefore = away.getHealth();
			die.stamp(level, near.position());
			float midStamp = 0.0F;
			for (int tick = 0; tick < dev.forja.entity.CuneGuardian.STAMP_WINDUP - 1; tick++) {
				die.tick();
				midStamp = Math.max(midStamp, nearBefore - near.getHealth());
			}
			die.tick();
			float stamped = nearBefore - near.getHealth();
			float missed = awayBefore - away.getHealth();

			near.discard();
			away.discard();
			die.discard();
			player.setHealth(player.getMaxHealth());
			return new float[] {seen, sealedLost, leftStanding, partialLost, afterAll, openLost,
				stamped, midStamp, missed};
		});
		log("guardian de cuno: sellos vistos " + (int) cune[0] + ", dano sellado " + cune[1]
			+ ", quedando " + (int) cune[2] + " sigue sin entrar " + cune[3]
			+ ", con " + (int) cune[4] + " entra " + cune[5]
			+ " | cuno: dano " + cune[6] + ", durante el aviso " + cune[7] + ", fuera " + cune[8]);
		check(cune[0] == 3, "it should see the three seals round it, saw " + cune[0]);
		check(cune[1] == 0.0F, "a sealed guardian should take nothing at all, lost " + cune[1]);
		check(cune[2] == 1, "two lanterns broken leaves one, it sees " + cune[2]);
		check(cune[3] == 0.0F, "and one seal is still a seal, lost " + cune[3]);
		check(cune[4] == 0, "the last lantern broken leaves none, it sees " + cune[4]);
		check(cune[5] > 0.0F, "and then it takes damage, lost " + cune[5]);
		check(cune[7] == 0.0F, "the die should not land while it is still up, got " + cune[7]);
		check(cune[6] > 0.0F, "and should land for something, got " + cune[6]);
		check(cune[8] == 0.0F, "and nothing outside the print, got " + cune[8]);

		// ---- The truce. The hauler's blast is the widest thing in the mod and it was landing on
		// everything, which is how the smith's own apprentices were finishing him off.
		float[] truce = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var box = new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(20);
			for (var stray : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box)) {
				stray.discard();
			}
			var beast = dev.forja.registry.ModEntities.CARGADOR_DE_CARBON.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(beast != null, "the coal hauler should be creatable");
			beast.snapTo(px, y, pz, 0.0F, 0.0F);
			beast.setNoAi(true);
			level.addFreshEntity(beast);

			var friend = dev.forja.registry.ModEntities.TENAZA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(friend != null, "the tongs should be creatable");
			friend.snapTo(px + 1.5, y, pz, 0.0F, 0.0F);
			friend.setNoAi(true);
			level.addFreshEntity(friend);
			float friendBefore = friend.getHealth();
			var outsider = zombie(level, px + 1.5, y, pz + 0.5);
			float outsiderBefore = outsider.getHealth();

			beast.prime(level);
			for (int tick = 0; tick < dev.forja.entity.CoalHauler.PRIME_WINDUP; tick++) {
				beast.tick();
			}
			float friendLost = friendBefore - friend.getHealth();
			float outsiderLost = outsiderBefore - outsider.getHealth();
			int ours = dev.forja.world.Truce.ours(friend) ? 1 : 0;
			int theirs = dev.forja.world.Truce.ours(outsider) ? 1 : 0;
			friend.discard();
			outsider.discard();
			beast.discard();
			for (var dropped : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
				dropped.discard();
			}
			return new float[] {friendLost, outsiderLost, ours, theirs};
		});
		log("tregua: a una tenaza " + truce[0] + ", a un zombi " + truce[1]
			+ " (de los nuestros " + (truce[2] > 0) + " / " + (truce[3] > 0) + ")");
		check(truce[2] > 0 && truce[3] == 0.0F, "the truce should know its own from a zombie");
		check(truce[0] == 0.0F, "our own should take nothing from our own blast, lost " + truce[0]);
		check(truce[1] > 0.0F, "and everything else should still take it, lost " + truce[1]);

		// ---- The kit: Andy's fifteen per cent, and never a breastplate in a fist.
		float[] kit = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var registries = level.registryAccess();
			var random = net.minecraft.util.RandomSource.create(1234);
			// Every legend a mob can be handed has to be a thing with a handle. Five of the fourteen
			// are not: a breastplate, boots, a shield, wings and a horse's barding.
			int wrong = 0;
			for (int roll = 0; roll < 200; roll++) {
				ItemStack legend = dev.forja.world.Legends.createWeapon(random, registries);
				var parts = legend.get(dev.forja.registry.ModComponents.PARTS);
				if (parts == null || !dev.forja.world.Legends.heldInHand(parts.type())) {
					wrong++;
				}
			}
			return new float[] {
				wrong,
				dev.forja.world.ForjaMobs.weaponChance(0),
				dev.forja.world.ForjaMobs.weaponChance(2),
				dev.forja.world.ForjaMobs.weaponChance(4),
			};
		});
		log("equipo: leyendas que no se empunan " + (int) kit[0]
			+ ", arma con 0 piezas " + kit[1] + ", con 2 " + kit[2] + ", con 4 " + kit[3]);
		check(kit[0] == 0.0F, "no mob should be handed a legend it cannot hold, got " + (int) kit[0] + " of 200");
		check(Math.abs(kit[1] - 1.0F) < 0.001F, "bare means always armed, got " + kit[1]);
		check(Math.abs(kit[2] - 0.7F) < 0.001F, "two pieces should leave seventy per cent, got " + kit[2]);
		check(Math.abs(kit[3] - 0.4F) < 0.001F, "four pieces should leave forty per cent, got " + kit[3]);

		// ---- The core's window: after it fires it is an ordinary mob, and that is how it dies.
		float[] spent = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var stone = dev.forja.registry.ModEntities.NUCLEO_ESTELAR.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(stone != null, "the star core should be creatable");
			stone.snapTo(px, y + 1, pz, 0.0F, 0.0F);
			stone.setNoAi(true);
			level.addFreshEntity(stone);
			var feeder = net.minecraft.world.entity.EntityTypes.IRON_GOLEM.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(feeder != null, "an iron golem should be creatable");
			feeder.snapTo(px + 3.0, y, pz, 0.0F, 0.0F);
			feeder.setNoAi(true);
			level.addFreshEntity(feeder);
			for (int hit = 0; hit < 8; hit++) {
				stone.invulnerableTime = 0;
				stone.hurtServer(level, level.damageSources().mobAttack(feeder), 3.0F);
			}
			stone.release(level);
			for (int tick = 0; tick < dev.forja.entity.StarCore.RELEASE_WINDUP; tick++) {
				feeder.invulnerableTime = 0;
				stone.tick();
			}
			int open = stone.spent() ? 1 : 0;
			float before = stone.getHealth();
			stone.invulnerableTime = 0;
			stone.hurtServer(level, level.damageSources().mobAttack(feeder), 4.0F);
			float taken = before - stone.getHealth();
			feeder.discard();
			stone.discard();
			return new float[] {open, taken};
		});
		log("nucleo: abierto tras disparar " + (spent[0] > 0) + ", golpe de 4 le quita " + spent[1]);
		check(spent[0] > 0, "a core that has just fired should be spent");
		check(spent[1] >= 4.0F * dev.forja.entity.StarCore.SPENT_MULTIPLIER - 0.01F,
			"and should take double while it is, took " + spent[1] + " from a blow of 4");

		// ---- The stamp: a third of it ignores armour, and the armour itself is marked.
		float[] stamp = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			player.setPos(px + 1.0, y, pz);
			player.removeAllEffects();
			player.getInventory().clearContent();
			ItemStack chest = Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO), registries);
			player.setItemSlot(EquipmentSlot.CHEST, chest);
			int wearBefore = chest.getDamageValue();
			player.setHealth(player.getMaxHealth());

			var die = dev.forja.registry.ModEntities.GUARDIAN_DE_CUNO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(die != null, "the cune guardian should be creatable");
			die.snapTo(px, y, pz, 0.0F, 0.0F);
			die.setNoAi(true);
			level.addFreshEntity(die);
			float before = player.getHealth();
			die.stamp(level, player.position());
			for (int tick = 0; tick < dev.forja.entity.CuneGuardian.STAMP_WINDUP; tick++) {
				die.tick();
			}
			float taken = before - player.getHealth();
			int wear = chest.getDamageValue() - wearBefore;
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.setHealth(player.getMaxHealth());
			die.discard();
			return new float[] {taken, wear};
		});
		log("cuno: con pechera de hierro quita " + stamp[0] + " y le mete " + (int) stamp[1] + " de desgaste");
		check(stamp[0] > 0.0F, "the stamp should get through plate, took " + stamp[0]);
		check(stamp[1] > 0.0F, "and should mark the plate on the way, wore " + (int) stamp[1]);

		// ---- And the two forge tables are two different menus, which is the whole of that bug.
		server.runOnServer(s -> {
			var lesser = dev.forja.menu.Station.FORJA.menuType();
			var greater = dev.forja.menu.Station.FORJA_MAYOR.menuType();
			log("mesas: tipo de menu distinto " + (lesser != greater));
			check(lesser != greater,
				"the two forge tables must not share a MenuType: the client builds the menu from it "
					+ "and has no idea which block was clicked, so sharing one means the greater table "
					+ "is always drawn as the lesser bench");
		});

		float[] steam = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.ForgeAutomaton automaton = dev.forja.registry.ModEntities.AUTOMATA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(automaton != null, "the automaton should be creatable");
			automaton.snapTo(px, y, pz, 0.0F, 0.0F);
			automaton.setNoAi(true);
			level.addFreshEntity(automaton);
			net.minecraft.world.entity.monster.zombie.Zombie attacker = zombie(level, px + 1.2, y, pz);
			float before = attacker.getHealth();
			automaton.hurtServer(level, level.damageSources().mobAttack(attacker), 5.0F);
			float taken = before - attacker.getHealth();
			boolean slowed = attacker.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
			boolean guarded = automaton.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
			// And it cannot do it again on the next blow.
			attacker.setHealth(attacker.getMaxHealth());
			automaton.invulnerableTime = 0;
			automaton.hurtServer(level, level.damageSources().mobAttack(attacker), 5.0F);
			float again = attacker.getMaxHealth() - attacker.getHealth();
			attacker.discard();
			automaton.discard();
			return new float[] {taken, slowed ? 1 : 0, guarded ? 1 : 0, again};
		});
		log("vapor del automata: dano " + steam[0] + ", ralentiza " + (steam[1] > 0) + ", se protege " + (steam[2] > 0) + ", segunda purga " + steam[3]);
		check(steam[0] >= dev.forja.entity.ForgeAutomaton.STEAM_DAMAGE * 0.8F, "the steam should scald whoever is on top of it, got " + steam[0]);
		check(steam[1] > 0, "the steam should slow them down");
		check(steam[2] > 0, "the automaton should buy itself a moment with it");
		check(steam[3] == 0.0F, "and it should not be able to vent again straight away, got " + steam[3]);

		// ---- Coraza vacia: the lunge across the gap, and the wail that wakes the room.
		float[] dash = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.HollowArmor hollow = hollow(level, px, y, pz);
			net.minecraft.world.entity.monster.zombie.Zombie victim = zombie(level, px + 8.0, y, pz);
			// A mob with its AI switched off is never moved by the game, so the jump is read off the
			// velocity it is given rather than off the ground it covers.
			hollow.lunge(level, victim);
			// It braces before it goes, and while it is bracing it is not going anywhere: that is what
			// makes the move dodgeable and it is the half of it worth asserting. A charge that steers
			// is not a charge, it is a homing missile with a wind-up noise.
			double moved = 0.0;
			for (int tick = 0; tick < dev.forja.entity.HollowArmor.DASH_WINDUP - 1; tick++) {
				hollow.setTarget(victim);
				hollow.tick();
				moved = Math.max(moved, hollow.getDeltaMovement().horizontalDistance());
			}
			hollow.setTarget(victim);
			hollow.tick();
			net.minecraft.world.phys.Vec3 thrown = hollow.getDeltaMovement();
			net.minecraft.world.phys.Vec3 toward = victim.position().subtract(hollow.position()).normalize();
			double aimed = toward.x * thrown.x + toward.z * thrown.z;
			// Then it is brought under the lunge to check that going through somebody costs them.
			victim.snapTo(hollow.getX() + 0.4, hollow.getY(), hollow.getZ(), 0.0F, 0.0F);
			float before = victim.getHealth();
			for (int tick = 0; tick < dev.forja.entity.HollowArmor.DASH_TICKS; tick++) {
				hollow.setTarget(victim);
				hollow.tick();
			}
			float damage = before - victim.getHealth();
			victim.discard();
			hollow.discard();
			return new float[] {damage, (float) thrown.horizontalDistance(), (float) aimed, (float) thrown.y, (float) moved};
		});
		log("embestida de la coraza: dano " + dash[0] + ", impulso " + dash[1] + ", hacia el objetivo " + dash[2]
			+ ", salto " + dash[3] + ", se mueve durante el aviso " + dash[4]);
		check(dash[4] < 0.1F, "the lunge should hold still while it braces, got " + dash[4]);
		check(dash[1] > 0.5F, "the lunge should throw it across the gap, got " + dash[1]);
		check(dash[2] > 0.0F, "the lunge should go at what it is looking at, got " + dash[2]);
		check(dash[3] > 0.1F, "the lunge should leave the floor, got " + dash[3]);
		check(dash[0] >= dev.forja.entity.HollowArmor.DASH_DAMAGE * 0.8F, "and cut whoever it goes through, got " + dash[0]);

		float[] wail = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.HollowArmor hollow = hollow(level, px, y, pz);
			dev.forja.entity.HollowArmor sibling = hollow(level, px + 9.0, y, pz);
			sibling.setHealth(20.0F);
			net.minecraft.world.entity.monster.zombie.Zombie victim = zombie(level, px + 3.0, y, pz);
			float siblingBefore = sibling.getHealth();
			for (int tick = 0; tick < dev.forja.entity.HollowArmor.WAIL_COOLDOWN + 40; tick++) {
				hollow.setTarget(victim);
				hollow.tick();
			}
			boolean slowed = victim.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
			boolean heavy = victim.hasEffect(net.minecraft.world.effect.MobEffects.MINING_FATIGUE);
			float mended = sibling.getHealth() - siblingBefore;
			victim.discard();
			sibling.discard();
			hollow.discard();
			return new float[] {slowed ? 1 : 0, heavy ? 1 : 0, mended};
		});
		log("lamento de la coraza: ralentiza " + (wail[0] > 0) + ", pesa el arma " + (wail[1] > 0) + ", cura a la otra " + wail[2]);
		check(wail[0] > 0, "the wail should slow whoever hears it");
		check(wail[1] > 0, "the wail should make their arms heavy");
		check(wail[2] >= dev.forja.entity.HollowArmor.WAIL_MEND, "the wail should put the other suit back on its feet, got " + wail[2]);

		// ---- Elite: the charge and the one second wind, driven straight through their goals.
		float[] elite = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			net.minecraft.world.entity.monster.zombie.Zombie fighter = zombie(level, px, y, pz);
			dev.forja.world.Elites.makeElite(fighter, level.getRandom());
			int moves = 0;
			for (var goal : ((dev.forja.mixin.MobGoalsAccess) fighter).forjaGoals().getAvailableGoals()) {
				if (goal.getGoal() instanceof dev.forja.entity.ai.LeapStrikeGoal
					|| goal.getGoal() instanceof dev.forja.entity.ai.SecondWindGoal) {
					moves++;
				}
			}
			net.minecraft.world.entity.monster.zombie.Zombie victim = zombie(level, px + 7.0, y, pz);
			fighter.setTarget(victim);
			var charge = new dev.forja.entity.ai.LeapStrikeGoal(fighter,
				dev.forja.world.Elites.CHARGE_MIN, dev.forja.world.Elites.CHARGE_MAX,
				dev.forja.world.Elites.CHARGE_COOLDOWN, dev.forja.world.Elites.CHARGE_DAMAGE,
				dev.forja.world.Elites.CHARGE_REACH, net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
				net.minecraft.sounds.SoundEvents.RAVAGER_ROAR);
			int waited = 0;
			while (!charge.canUse() && waited < dev.forja.world.Elites.CHARGE_COOLDOWN * 2) {
				waited++;
			}
			charge.start();
			double lift = fighter.getDeltaMovement().y;
			// Bring the victim under it and let it come down.
			victim.snapTo(fighter.getX() + 1.0, fighter.getY(), fighter.getZ(), 0.0F, 0.0F);
			float before = victim.getHealth();
			// A mob that has never been moved by the game still reads as airborne, and the goal only
			// lands on something it thinks it has come down onto.
			fighter.setOnGround(true);
			for (int tick = 0; tick < 24 && charge.canContinueToUse(); tick++) {
				charge.tick();
			}
			float landed = before - victim.getHealth();

			fighter.setHealth(fighter.getMaxHealth() * 0.2F);
			float wounded = fighter.getHealth();
			var wind = new dev.forja.entity.ai.SecondWindGoal(fighter,
				dev.forja.world.Elites.WIND_SHARE, dev.forja.world.Elites.WIND_MEND, dev.forja.world.Elites.WIND_TICKS);
			boolean opens = wind.canUse();
			wind.start();
			float mended = fighter.getHealth() - wounded;
			boolean strong = fighter.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
			boolean twice = wind.canUse();
			victim.discard();
			fighter.discard();
			return new float[] {moves, (float) lift, landed, opens ? 1 : 0, mended, strong ? 1 : 0, twice ? 1 : 0};
		});
		log("elite: movimientos propios " + (int) elite[0] + ", salto " + elite[1] + ", dano al caer " + elite[2]
			+ ", segundo aliento " + elite[4] + " de vida, fuerza " + (elite[5] > 0) + ", repetible " + (elite[6] > 0));
		check(elite[0] == 2, "an elite should carry both of its own moves, got " + (int) elite[0]);
		check(elite[1] > 0.2F, "the charge should get it off the floor, got " + elite[1]);
		check(elite[2] >= dev.forja.world.Elites.CHARGE_DAMAGE * 0.8F, "and land on what it jumped at, got " + elite[2]);
		check(elite[3] > 0, "the second wind should open at a quarter of its health");
		check(elite[4] > 0.0F, "the second wind should heal it, got " + elite[4]);
		check(elite[5] > 0, "the second wind should leave it stronger");
		check(elite[6] == 0.0F, "and it should only ever get one");

		// ---- Capitan saqueador: the horn.
		float[] rally = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var captain = net.minecraft.world.entity.EntityTypes.VINDICATOR.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(captain != null, "a vindicator should be creatable");
			captain.snapTo(px, y, pz, 0.0F, 0.0F);
			captain.setNoAi(true);
			level.addFreshEntity(captain);
			var follower = net.minecraft.world.entity.EntityTypes.PILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(follower != null, "a pillager should be creatable");
			follower.snapTo(px + 4.0, y, pz, 0.0F, 0.0F);
			follower.setNoAi(true);
			level.addFreshEntity(follower);
			net.minecraft.world.entity.monster.zombie.Zombie victim = zombie(level, px + 6.0, y, pz);
			captain.setTarget(victim);
			var horn = new dev.forja.entity.ai.RallyGoal(captain,
				dev.forja.world.ForgeRaiders.RALLY_REACH, dev.forja.world.ForgeRaiders.RALLY_COOLDOWN,
				dev.forja.world.ForgeRaiders.RALLY_TICKS);
			int waited = 0;
			while (!horn.canUse() && waited < dev.forja.world.ForgeRaiders.RALLY_COOLDOWN * 2) {
				waited++;
			}
			horn.start();
			boolean fast = follower.hasEffect(net.minecraft.world.effect.MobEffects.SPEED);
			boolean strong = follower.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
			boolean aimed = follower.getTarget() == victim;
			boolean cooling = !horn.canUse();
			victim.discard();
			follower.discard();
			captain.discard();
			return new float[] {fast ? 1 : 0, strong ? 1 : 0, aimed ? 1 : 0, cooling ? 1 : 0};
		});
		log("cerrar filas: prisa " + (rally[0] > 0) + ", fuerza " + (rally[1] > 0) + ", les pasa el objetivo " + (rally[2] > 0));
		check(rally[0] > 0, "the horn should speed the band up");
		check(rally[1] > 0, "the horn should make them hit harder");
		check(rally[2] > 0, "the horn should point them at what the captain is fighting");
		check(rally[3] > 0, "and he should not be able to wind it again straight away");

		// ---- Pavesa: it dives, and it feeds on anything hot enough to be worth sitting next to.
		float[] wisp = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.EmberWisp pavesa = dev.forja.registry.ModEntities.PAVESA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(pavesa != null, "the wisp should be creatable");
			pavesa.snapTo(px, y + 3, pz, 0.0F, 0.0F);
			pavesa.setNoAi(true);
			level.addFreshEntity(pavesa);
			net.minecraft.world.entity.monster.zombie.Zombie victim = zombie(level, px + 6.0, y, pz);

			// Cold first: it dives, and the dive costs you.
			boolean coldBefore = pavesa.isFed();
			pavesa.setTarget(victim);
			// It has to be caught mid-dive: the whole thing is over in twelve ticks, and a mob with its
			// AI off is never moved by the game, so the target is brought to it instead.
			double thrown = 0.0;
			float before = victim.getHealth();
			boolean caught = false;
			for (int tick = 0; tick < dev.forja.entity.EmberWisp.DIVE_COOLDOWN * 2; tick++) {
				pavesa.setTarget(victim);
				pavesa.tick();
				if (!caught && pavesa.getDeltaMovement().length() > 0.4) {
					caught = true;
					thrown = pavesa.getDeltaMovement().length();
					victim.snapTo(pavesa.getX(), pavesa.getY(), pavesa.getZ(), 0.0F, 0.0F);
					before = victim.getHealth();
				}
			}
			float bite = before - victim.getHealth();
			boolean lit = victim.isOnFire();

			// Then put a fire under it and see it swell.
			level.setBlockAndUpdate(new BlockPos(px, y, pz), net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
			pavesa.snapTo(px, y + 2, pz, 0.0F, 0.0F);
			for (int tick = 0; tick < dev.forja.entity.EmberWisp.FLARE_CHECK * 2; tick++) {
				pavesa.tick();
			}
			boolean fedNow = pavesa.isFed();
			level.setBlockAndUpdate(new BlockPos(px, y, pz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			victim.discard();
			pavesa.discard();
			return new float[] {coldBefore ? 1 : 0, (float) thrown, bite, lit ? 1 : 0, fedNow ? 1 : 0};
		});
		log("pavesa: empieza fria " + (wisp[0] == 0) + ", picado " + wisp[1] + ", mordisco " + wisp[2]
			+ ", te prende " + (wisp[3] > 0) + ", se aviva con lava " + (wisp[4] > 0));
		check(wisp[0] == 0.0F, "a wisp away from any fire should start cold");
		check(wisp[1] > 0.4F, "the dive should throw it at what it is after, got " + wisp[1]);
		check(wisp[2] >= dev.forja.entity.EmberWisp.DIVE_DAMAGE * 0.7F, "the dive should bite, got " + wisp[2]);
		check(wisp[3] > 0, "and set them alight");
		check(wisp[4] > 0, "a wisp over lava should feed on it");

		// ---- And catching one: only while it is fed, and what comes back is worth lava under a table.
		float[] caught = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			player.setPos(px, y, pz);
			dev.forja.entity.EmberWisp cold = dev.forja.registry.ModEntities.PAVESA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(cold != null, "the wisp should be creatable");
			cold.snapTo(px + 1.0, y, pz, 0.0F, 0.0F);
			cold.setNoAi(true);
			level.addFreshEntity(cold);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.LANTERN));
			net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
				.interact(player, level, InteractionHand.MAIN_HAND, cold, null);
			boolean coldTook = player.getInventory().contains(stack -> stack.is(dev.forja.registry.ModItems.FAROL_DE_PAVESA));
			boolean coldAlive = cold.isAlive();

			// Now over lava, where it will take.
			level.setBlockAndUpdate(new BlockPos(px + 2, y, pz), net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
			cold.snapTo(px + 2, y + 2, pz, 0.0F, 0.0F);
			for (int tick = 0; tick < dev.forja.entity.EmberWisp.FLARE_CHECK * 2; tick++) {
				cold.tick();
			}
			boolean fed = cold.isFed();
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.LANTERN));
			net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
				.interact(player, level, InteractionHand.MAIN_HAND, cold, null);
			boolean gotIt = player.getInventory().contains(stack -> stack.is(dev.forja.registry.ModItems.FAROL_DE_PAVESA));
			boolean gone = !cold.isAlive() || cold.isRemoved();
			// The empty lantern is gone; what is in the hand now may well be the full one, because the
			// inventory happily drops it into the slot the lantern just left.
			boolean lanternSpent = !player.getInventory().contains(stack -> stack.is(net.minecraft.world.item.Items.LANTERN));

			// And what it is for: it reads as the hottest heat there is under a forge table.
			level.setBlockAndUpdate(new BlockPos(px + 2, y, pz), dev.forja.registry.ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
			var heat = dev.forja.forge.Alloys.heatUnder(level, new BlockPos(px + 2, y + 1, pz));
			level.setBlockAndUpdate(new BlockPos(px + 2, y, pz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			player.getInventory().clearContent();
			cold.discard();
			return new float[] {coldTook ? 1 : 0, coldAlive ? 1 : 0, fed ? 1 : 0, gotIt ? 1 : 0,
				gone ? 1 : 0, lanternSpent ? 1 : 0, heat == dev.forja.forge.Alloys.Heat.FUNDIDA ? 1 : 0};
		});
		log("farol de pavesa: apagada no entra " + (caught[0] == 0) + " y sigue viva " + (caught[1] > 0)
			+ ", avivada entra " + (caught[3] > 0) + ", desaparece " + (caught[4] > 0)
			+ ", gasta el farol " + (caught[5] > 0) + ", calor fundida " + (caught[6] > 0));
		check(caught[0] == 0.0F, "a cold wisp should not go into the lantern");
		check(caught[1] > 0, "and should still be there afterwards");
		check(caught[2] > 0, "the wisp over lava should be fed");
		check(caught[3] > 0, "a fed wisp should go into the lantern");
		check(caught[4] > 0, "and be taken out of the world by it");
		check(caught[5] > 0, "the empty lantern should be spent");
		check(caught[6] > 0, "and the caught one should read as molten heat under a table");

		// ---- And the three answers to all of that: hold your ground, put yourself out, shake it off.
		float[] answers = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			player.setPos(px, y, pz);

			// Anclaje on the boots against the hook, which moves you directly and ignores resistance.
			player.getInventory().clearContent();
			ItemStack boots = Assembler.create(ForgeType.BOTAS, List.of(HIERRO, CUERO), registries);
			dev.forja.entity.FallenSmith smith = spawnSmith(level, px + 10, y, pz);
			net.minecraft.world.entity.monster.zombie.Zombie bare = zombie(level, px, y, pz);
			smith.hookIn(level, bare);
			throwIt(smith);
			double bareTug = bare.getDeltaMovement().horizontalDistance();
			net.minecraft.world.entity.monster.zombie.Zombie shod = zombie(level, px, y, pz);
			shod.setItemSlot(EquipmentSlot.FEET, Upgrades.with(boots.copy(), Upgrade.ANCLAJE, 100));
			smith.hookIn(level, shod);
			throwIt(smith);
			double heldTug = shod.getDeltaMovement().horizontalDistance();
			bare.discard();
			shod.discard();
			smith.discard();

			// Aislante: clay under the plate puts you out sooner.
			player.getInventory().setItem(0, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.setRemainingFireTicks(200);
			dev.forja.upgrade.FieldUpgrades.tickForTest(level, player);
			int bareFire = player.getRemainingFireTicks();
			player.setItemSlot(EquipmentSlot.CHEST,
				Upgrades.with(Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO), registries), Upgrade.AISLANTE, 100));
			player.setRemainingFireTicks(200);
			dev.forja.upgrade.FieldUpgrades.tickForTest(level, player);
			int clayFire = player.getRemainingFireTicks();

			// Temple: the wail runs down faster on someone wearing it.
			//
			// Cleared first, and that is not belt and braces. `addEffect` will not replace an effect
			// with a stronger amplifier, so anything the world had already put on the player — a
			// Tenaza waking up in one of the ruins the structure tests placed and taking hold of them,
			// for instance — silently becomes what this measures. It read "80 without Temple, 190
			// with", which is backwards, and the upgrade was fine.
			player.removeAllEffects();
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 200, 0));
			dev.forja.upgrade.FieldUpgrades.tickForTest(level, player);
			int bareSlow = player.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS).getDuration();
			player.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
			player.setItemSlot(EquipmentSlot.CHEST,
				Upgrades.with(Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO), registries), Upgrade.TEMPLE, 100));
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 200, 0));
			dev.forja.upgrade.FieldUpgrades.tickForTest(level, player);
			int templeSlow = player.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS).getDuration();
			player.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.setRemainingFireTicks(0);
			return new float[] {(float) bareTug, (float) heldTug, bareFire, clayFire, bareSlow, templeSlow};
		});
		log("respuestas: tiron sin anclaje " + answers[0] + " con anclaje " + answers[1]
			+ ", fuego sin aislante " + (int) answers[2] + " con aislante " + (int) answers[3]
			+ ", lentitud sin temple " + (int) answers[4] + " con temple " + (int) answers[5]);
		check(answers[1] < answers[0] * 0.75F, "Anclaje should take the hook out of the hook, got "
			+ answers[1] + " against " + answers[0]);
		check(answers[3] < answers[2], "Aislante should put the fire out sooner, got " + (int) answers[3]
			+ " against " + (int) answers[2]);
		check(answers[5] < answers[4], "Temple should run a bad effect down faster, got " + (int) answers[5]
			+ " against " + (int) answers[4]);

		// ---- Rescoldo: fire stops being an emergency and becomes a cost of the job.
		float[] warmth = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			player.setPos(px, y, pz);
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.setHealth(player.getMaxHealth());
			player.invulnerableTime = 0;
			player.hurtServer(level, level.damageSources().inFire(), 4.0F);
			float bare = player.getMaxHealth() - player.getHealth();
			player.setHealth(player.getMaxHealth());
			player.invulnerableTime = 0;
			player.setItemSlot(EquipmentSlot.CHEST,
				Upgrades.with(Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO), registries), Upgrade.RESCOLDO, 100));
			player.hurtServer(level, level.damageSources().inFire(), 4.0F);
			float warm = player.getMaxHealth() - player.getHealth();
			// And with Fire Protection on the same piece, Salamandra hands it all back and puts you out.
			player.setHealth(player.getMaxHealth());
			player.invulnerableTime = 0;
			ItemStack salamander = Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO), registries);
			Upgrades.with(salamander, Upgrade.RESCOLDO, 100);
			Upgrades.with(salamander, Upgrade.PROTECCION_CONTRA_FUEGO, 100);
			player.setItemSlot(EquipmentSlot.CHEST, salamander);
			player.setRemainingFireTicks(100);
			player.hurtServer(level, level.damageSources().inFire(), 4.0F);
			float whole = player.getMaxHealth() - player.getHealth();
			boolean doused = player.getRemainingFireTicks() == 0;

			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.setHealth(player.getMaxHealth());
			player.setRemainingFireTicks(0);
			return new float[] {bare, warm, whole, doused ? 1 : 0};
		});
		log("rescoldo: fuego sin la mejora " + warmth[0] + ", con la mejora " + warmth[1]
			+ ", con Salamandra " + warmth[2] + ", te apaga " + (warmth[3] > 0));
		check(warmth[1] < warmth[0] * 0.5F, "Rescoldo should give most of the fire back, got " + warmth[1]
			+ " against " + warmth[0]);
		check(warmth[2] <= 0.0F, "Salamandra should give the fire back whole, got " + warmth[2]);
		check(warmth[3] > 0, "and put the wearer out");

		server.runCommand("difficulty peaceful");
	}

	/**
	 * The crucibles: what each tier will and will not melt, that a hopper can drive one, and that the
	 * dear one pays back a whole tool where the cheap one pays back half.
	 */
	private static void checkCrucibles(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 90;
		int pz = z + 90;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", px - 4, y - 1, pz - 4, px + 4, y - 1, pz + 4));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 4, y, pz - 4, px + 4, y + 4, pz + 4));

		int[] alloys = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int[] made = new int[3];
			var tiers = List.of(dev.forja.registry.ModBlocks.CRISOL_DE_BARRO,
				dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO, dev.forja.registry.ModBlocks.CRISOL_DE_OBSIDIANA);
			for (int i = 0; i < tiers.size(); i++) {
				BlockPos at = new BlockPos(px + i * 2, y, pz);
				level.setBlockAndUpdate(at, tiers.get(i).defaultBlockState());
				var crucible = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(at);
				check(crucible != null, "the crucible should have its block entity");
				// Steel: iron and coal, which only a Caliente crucible or better will pour.
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND, new ItemStack(net.minecraft.world.item.Items.COAL, 2));
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA, 4));
				for (int tick = 0; tick < 260; tick++) {
					dev.forja.block.entity.CrucibleBlockEntity.serverTick(level, at, level.getBlockState(at), crucible);
				}
				made[i] = crucible.getItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_OUTPUT).getCount();
				level.removeBlock(at, false);
			}
			return made;
		});
		log("crisoles, acero: barro " + alloys[0] + ", hierro " + alloys[1] + ", obsidiana " + alloys[2]);
		check(alloys[0] == 0, "a clay crucible should not reach steel, got " + alloys[0]);
		check(alloys[1] == 2, "an iron crucible should pour steel, got " + alloys[1]);
		check(alloys[2] == 3, "and an obsidian one should throw in a bar, got " + alloys[2]);

		int[] scrap = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var registries = level.registryAccess();
			int[] back = new int[2];
			var tiers = List.of(dev.forja.registry.ModBlocks.CRISOL_DE_BARRO, dev.forja.registry.ModBlocks.CRISOL_DE_OBSIDIANA);
			for (int i = 0; i < tiers.size(); i++) {
				BlockPos at = new BlockPos(px + i * 2, y, pz + 2);
				level.setBlockAndUpdate(at, tiers.get(i).defaultBlockState());
				var crucible = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(at);
				check(crucible != null, "the crucible should have its block entity");
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST,
					Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries));
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA, 4));
				for (int tick = 0; tick < 260; tick++) {
					dev.forja.block.entity.CrucibleBlockEntity.serverTick(level, at, level.getBlockState(at), crucible);
				}
				back[i] = crucible.getItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_OUTPUT).getCount();
				level.removeBlock(at, false);
			}
			return back;
		});
		log("crisoles, fundir una espada: barro " + scrap[0] + " lingotes, obsidiana " + scrap[1]);
		check(scrap[0] > 0, "even a clay crucible should give something back, got " + scrap[0]);
		check(scrap[1] > scrap[0], "and the obsidian one should give more, got " + scrap[1] + " against " + scrap[0]);

		boolean[] pipes = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos at = new BlockPos(px, y + 1, pz - 2);
			level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
			var crucible = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(at);
			check(crucible != null, "the crucible should have its block entity");
			// A hopper drops in from above, feeds from the side and pulls out from below.
			boolean topIn = crucible.getSlotsForFace(net.minecraft.core.Direction.UP).length == 2
				&& crucible.canPlaceItemThroughFace(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST,
					new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), net.minecraft.core.Direction.UP);
			boolean sideFuel = crucible.canPlaceItemThroughFace(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL,
				new ItemStack(dev.forja.registry.ModItems.ASCUA), net.minecraft.core.Direction.NORTH)
				// And it takes nothing else: the fuel is the mod's own and only the mod's own.
				&& !crucible.canPlaceItemThroughFace(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL,
					new ItemStack(net.minecraft.world.item.Items.COAL), net.minecraft.core.Direction.NORTH);
			boolean bottomOut = crucible.canTakeItemThroughFace(dev.forja.block.entity.CrucibleBlockEntity.SLOT_OUTPUT,
				ItemStack.EMPTY, net.minecraft.core.Direction.DOWN)
				&& !crucible.canTakeItemThroughFace(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST,
					ItemStack.EMPTY, net.minecraft.core.Direction.DOWN);
			// And the capacity is real: a hopper cannot pour a stack into a clay pot.
			crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 16));
			boolean capped = !crucible.canPlaceItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND,
				new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
			level.removeBlock(at, false);
			return new boolean[] {topIn, sideFuel, bottomOut, capped};
		});
		log("crisol y tolvas: entra por arriba " + pipes[0] + ", solo ascuas por el lado " + pipes[1]
			+ ", sale por abajo " + pipes[2] + ", respeta la cabida " + pipes[3]);
		check(pipes[0], "a hopper on top should reach both input slots");
		check(pipes[1], "a hopper on the side should feed it embers and nothing else");
		check(pipes[2], "a hopper underneath should only ever pull the pour");
		check(pipes[3], "and the capacity should hold against a hopper");

		boolean[] lantern = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos at = new BlockPos(px, y + 1, pz + 4);
			level.setBlockAndUpdate(at.below(), dev.forja.registry.ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
			level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
			var crucible = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(at);
			check(crucible != null, "the crucible should have its block entity");
			// No fuel at all in it: the lantern underneath is the fuel.
			crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
			crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND, new ItemStack(net.minecraft.world.item.Items.COAL, 2));
			for (int tick = 0; tick < 160; tick++) {
				dev.forja.block.entity.CrucibleBlockEntity.serverTick(level, at, level.getBlockState(at), crucible);
			}
			boolean poured = !crucible.getItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_OUTPUT).isEmpty();
			level.removeBlock(at, false);
			level.removeBlock(at.below(), false);
			return new boolean[] {poured};
		});
		log("crisol sobre un farol de pavesa: cuela sin combustible " + lantern[0]);
		check(lantern[0], "a crucible over a wisp lantern should run with no fuel in it");

		// ---- The tanks: they join up, they fill from the bottom, they drain from the top, a crucible
		// beside one pours into it, and one that is fed from a tank pours twice as fast.
		int[] tanks = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			// A column of three, which is one bank.
			for (int dy = 0; dy < 3; dy++) {
				level.setBlockAndUpdate(new BlockPos(px, y + dy, pz + 6), dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			}
			var bottom = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(new BlockPos(px, y, pz + 6));
			var top = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(new BlockPos(px, y + 2, pz + 6));
			check(bottom != null && top != null, "the tanks should have their block entities");
			int joined = bottom.bank().size();
			int capacity = bottom.bankCapacity();
			// Filling goes into the lowest tank first, so the glass fills from the floor up.
			int leftOver = bottom.fill(net.minecraft.world.item.Items.IRON_INGOT, 300);
			int inBottom = bottom.amount();
			int inMiddle = ((dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(new BlockPos(px, y + 1, pz + 6))).amount();
			int inTop = top.amount();
			// One bank, one metal.
			int refused = bottom.fill(net.minecraft.world.item.Items.GOLD_INGOT, 10);
			// And draining comes off the top.
			int drawn = top.drain(50);
			int afterTop = ((dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(new BlockPos(px, y + 1, pz + 6))).amount();
			int total = bottom.bankAmount();
			return new int[] {joined, capacity, leftOver, inBottom, inMiddle, inTop, refused, drawn, afterTop, total};
		});
		log("cubas: conectadas " + tanks[0] + ", cabida " + tanks[1] + ", sobra " + tanks[2]
			+ ", reparto " + tanks[3] + "/" + tanks[4] + "/" + tanks[5]
			+ ", otro metal rechazado " + (tanks[6] == 10) + ", sacadas " + tanks[7] + ", total " + tanks[9]);
		check(tanks[0] == 3, "three touching tanks should be one bank, got " + tanks[0]);
		check(tanks[1] == 3 * dev.forja.block.entity.MeltTankBlockEntity.CAPACITY, "and their capacity should add up, got " + tanks[1]);
		check(tanks[2] == 0, "300 ingots should fit in three tanks, got " + tanks[2] + " left over");
		check(tanks[3] == dev.forja.block.entity.MeltTankBlockEntity.CAPACITY, "the bottom tank should fill first, got " + tanks[3]);
		check(tanks[5] == 0, "and the top one should still be empty, got " + tanks[5]);
		check(tanks[6] == 10, "a bank holding iron should refuse gold");
		check(tanks[7] == 50, "draining should come off the top, got " + tanks[7]);
		check(tanks[8] < 44 + 1, "and take from the highest tank that has any, got " + tanks[8]);
		check(tanks[9] == 250, "which leaves the bank at 250, got " + tanks[9]);

		boolean[] foundry = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos tankAt = new BlockPos(px + 4, y, pz + 6);
			BlockPos potAt = tankAt.east();
			level.setBlockAndUpdate(tankAt, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			level.setBlockAndUpdate(potAt, dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
			var tank = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(tankAt);
			var pot = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(potAt);
			check(tank != null && pot != null, "the foundry should have its block entities");

			// The pour goes into the tank, not into the crucible's own slot.
			pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
			pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND, new ItemStack(net.minecraft.world.item.Items.COAL, 2));
			pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA, 4));
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CrucibleBlockEntity.serverTick(level, potAt, level.getBlockState(potAt), pot);
			}
			boolean intoTank = tank.bankAmount() > 0
				&& pot.getItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_OUTPUT).isEmpty();

			// And a crucible fed out of a tank pours in half the time.
			tank.drain(tank.bankAmount());
			tank.fill(net.minecraft.world.item.Items.IRON_INGOT, 64);
			pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST, ItemStack.EMPTY);
			pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND, new ItemStack(net.minecraft.world.item.Items.COAL, 2));
			pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA, 4));
			int half = dev.forja.block.CrucibleBlock.Tier.HIERRO.cook / 2 + 4;
			int before = tank.bankAmount();
			for (int tick = 0; tick < half; tick++) {
				dev.forja.block.entity.CrucibleBlockEntity.serverTick(level, potAt, level.getBlockState(potAt), pot);
			}
			// It ate two iron out of the glass and put steel back, all inside half the usual time.
			boolean fast = tank.bankAmount() != before;
			level.removeBlock(potAt, false);
			level.removeBlock(tankAt, false);
			return new boolean[] {intoTank, fast};
		});
		log("fundicion: el crisol vuelca en la cuba " + foundry[0] + ", y alimentado por ella cuela al doble " + foundry[1]);
		check(foundry[0], "a crucible beside a tank should pour into it");
		check(foundry[1], "and a crucible fed from a tank should finish in half the time");

		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (int dy = 0; dy < 3; dy++) {
				level.removeBlock(new BlockPos(px, y + dy, pz + 6), false);
			}
		});

		// ---- The casting box: a part becomes a mould, and the mould casts out of the tanks; and a clay
		// box refuses a metal it cannot hold while a damascus one takes it.
		int[] casting = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var registries = level.registryAccess();
			BlockPos boxAt = new BlockPos(px + 8, y, pz + 6);
			BlockPos tankAt = boxAt.east();
			level.setBlockAndUpdate(boxAt, dev.forja.registry.ModBlocks.CAJA_DE_MOLDEO.defaultBlockState());
			level.setBlockAndUpdate(tankAt, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			var box = (dev.forja.block.entity.CastingBoxBlockEntity) level.getBlockEntity(boxAt);
			var tank = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(tankAt);
			check(box != null && tank != null, "the casting bench should have its block entities");

			// A pick head and two refractory steel: the head is spent and its mould comes out.
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN,
				Assembler.createPart(PartType.CABEZA_PICO, HIERRO));
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STEEL,
				new ItemStack(dev.forja.registry.ModItems.alloy("acero_refractario"), 4));
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, boxAt, level.getBlockState(boxAt), box);
			}
			ItemStack out = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT);
			int gotMould = dev.forja.item.CastingMouldItem.partOf(out) == PartType.CABEZA_PICO ? 1 : 0;
			int partSpent = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN).isEmpty() ? 1 : 0;
			int steelLeft = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STEEL).getCount();

			// Now cast with it: iron in the tank, and the clay box will take iron.
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN,
				dev.forja.item.CastingMouldItem.of(PartType.CABEZA_PICO));
			tank.fill(net.minecraft.world.item.Items.IRON_INGOT, 60);
			int before = tank.bankAmount();
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, boxAt, level.getBlockState(boxAt), box);
			}
			ItemStack cast = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT);
			int castIron = cast.getItem() == dev.forja.registry.ModItems.part(PartType.CABEZA_PICO)
				&& cast.get(dev.forja.registry.ModComponents.MATERIAL) == HIERRO ? cast.getCount() : 0;
			int drained = before - tank.bankAmount();
			int mouldKept = dev.forja.item.CastingMouldItem.partOf(
				box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN)) != null ? 1 : 0;

			// And diamond: too hard for a clay box, fine for a damascus one.
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
			tank.drain(tank.bankAmount());
			tank.fill(net.minecraft.world.item.Items.DIAMOND, 60);
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, boxAt, level.getBlockState(boxAt), box);
			}
			int clayRefused = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT).isEmpty() ? 1 : 0;

			level.setBlockAndUpdate(boxAt, dev.forja.registry.ModBlocks.CAJA_DE_MOLDEO_DE_DAMASCO.defaultBlockState());
			var hard = (dev.forja.block.entity.CastingBoxBlockEntity) level.getBlockEntity(boxAt);
			check(hard != null, "the damascus bench should have its block entity");
			hard.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN,
				dev.forja.item.CastingMouldItem.of(PartType.CABEZA_PICO));
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, boxAt, level.getBlockState(boxAt), hard);
			}
			int damascusTook = hard.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT).isEmpty() ? 0 : 1;

			level.removeBlock(boxAt, false);
			level.removeBlock(tankAt, false);
			return new int[] {gotMould, partSpent, steelLeft, castIron, drained, mouldKept, clayRefused, damascusTook};
		});
		log("caja de moldeo: sale el molde " + (casting[0] > 0) + ", se gasta la pieza " + (casting[1] > 0)
			+ ", acero restante " + casting[2] + ", cuela hierro " + casting[3] + " gastando " + casting[4]
			+ ", conserva el molde " + (casting[5] > 0) + ", el barro rechaza el diamante " + (casting[6] > 0)
			+ ", el damasco lo cuela " + (casting[7] > 0));
		check(casting[0] > 0, "pouring steel over a part should give its mould");
		check(casting[1] > 0, "and the part should be gone");
		check(casting[2] == 4 - dev.forja.block.entity.CastingBoxBlockEntity.MOULD_COST,
			"a mould should cost its refractory steel, got " + casting[2] + " left");
		check(casting[3] > 0, "a mould over a tank of iron should cast the part in iron");
		check(casting[4] == PartType.CABEZA_PICO.cost * casting[3],
			"and take exactly what the part costs, got " + casting[4]);
		check(casting[5] > 0, "the mould itself is not used up");
		check(casting[6] > 0, "a clay box should refuse diamond");
		check(casting[7] > 0, "and a damascus one should take it");

		// ---- The metal cools: a bank left alone sets, one over a wisp lantern does not, and a lit
		// crucible melts a set one back down for a price.
		int[] cold = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos loneAt = new BlockPos(px + 12, y, pz + 6);
			BlockPos warmAt = new BlockPos(px + 14, y, pz + 6);
			level.setBlockAndUpdate(loneAt, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			level.setBlockAndUpdate(warmAt, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			level.setBlockAndUpdate(warmAt.below(), dev.forja.registry.ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
			var lone = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(loneAt);
			var warm = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(warmAt);
			check(lone != null && warm != null, "both tanks should have their block entities");
			lone.fill(net.minecraft.world.item.Items.IRON_INGOT, 100);
			warm.fill(net.minecraft.world.item.Items.IRON_INGOT, 100);
			int startHeat = lone.heat();
			// Long enough for one of them to go out and the other to stay lit.
			int ticks = dev.forja.block.entity.MeltTankBlockEntity.HOT
				/ dev.forja.block.entity.MeltTankBlockEntity.COOLS * dev.forja.block.entity.MeltTankBlockEntity.PUSH_EVERY + 100;
			for (int tick = 0; tick < ticks; tick++) {
				dev.forja.block.entity.MeltTankBlockEntity.serverTick(level, loneAt, level.getBlockState(loneAt), lone);
				dev.forja.block.entity.MeltTankBlockEntity.serverTick(level, warmAt, level.getBlockState(warmAt), warm);
			}
			int loneSet = lone.isSet() ? 1 : 0;
			int warmSet = warm.isSet() ? 1 : 0;
			int heldAmount = lone.amount();

			// A lit crucible beside the cold one melts it again, and some of it is lost doing so.
			BlockPos potAt = loneAt.north();
			level.setBlockAndUpdate(potAt, dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
			var pot = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(potAt);
			check(pot != null, "the crucible should have its block entity");
			pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL,
				new ItemStack(dev.forja.registry.ModItems.ASCUA, 4));
			for (int tick = 0; tick < 60; tick++) {
				dev.forja.block.entity.CrucibleBlockEntity.serverTick(level, potAt, level.getBlockState(potAt), pot);
			}
			int afterMelt = lone.amount();
			int stillSet = lone.isSet() ? 1 : 0;

			for (BlockPos at : List.of(loneAt, warmAt, warmAt.below(), potAt)) {
				level.removeBlock(at, false);
			}
			return new int[] {startHeat, loneSet, warmSet, heldAmount, afterMelt, stillSet};
		});
		log("enfriamiento: al llenar " + cold[0] + " de calor, sola cuaja " + (cold[1] > 0)
			+ ", sobre farol " + (cold[2] > 0) + ", guarda " + cold[3]
			+ " y tras refundir " + cold[4] + ", sigue cuajada " + (cold[5] > 0));
		check(cold[0] == dev.forja.block.entity.MeltTankBlockEntity.HOT, "metal arrives molten, got " + cold[0]);
		check(cold[1] > 0, "a tank left alone should set");
		check(cold[2] == 0, "one over a wisp lantern should not");
		check(cold[3] == 100, "and setting should not eat the metal, got " + cold[3]);
		check(cold[5] == 0, "a lit crucible beside it should melt it again");
		check(cold[4] < cold[3] && cold[4] > 0, "and melting it again should cost some of it, got " + cold[4]);

		// ---- The pipes bleed heat by what they are made of, and a strainer that will not hold breaks.
		int[] grades = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos from = new BlockPos(px + 12, y, pz + 9);
			BlockPos to = from.east().east().east();
			for (BlockPos at : List.of(from.east(), from.east().east())) {
				level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			}
			level.setBlockAndUpdate(to, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			int bronze = dev.forja.block.MeltPipeBlock.bleedBetween(level, from, to);
			for (BlockPos at : List.of(from.east(), from.east().east())) {
				level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CONDUCTO_DE_DAMASCO.defaultBlockState());
			}
			int damascus = dev.forja.block.MeltPipeBlock.bleedBetween(level, from, to);
			for (BlockPos at : List.of(from.east(), from.east().east(), to)) {
				level.removeBlock(at, false);
			}
			return new int[] {bronze, damascus};
		});
		log("conductos: el bronce cuesta " + grades[0] + " de calor, el damasco " + grades[1]);
		check(grades[0] > grades[1], "bronze should bleed more heat than damascus, got "
			+ grades[0] + " against " + grades[1]);
		check(grades[1] > 0, "and even damascus should cost something, got " + grades[1]);

		int[] strainers = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos boxAt = new BlockPos(px + 12, y, pz + 12);
			BlockPos tankAt = boxAt.east();
			level.setBlockAndUpdate(boxAt, dev.forja.registry.ModBlocks.CAJA_DE_MOLDEO_DE_DAMASCO.defaultBlockState());
			level.setBlockAndUpdate(tankAt, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			var box = (dev.forja.block.entity.CastingBoxBlockEntity) level.getBlockEntity(boxAt);
			var tank = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(tankAt);
			check(box != null && tank != null, "the strainer bench should have its block entities");

			// Diamond through a clay strainer: it goes with the pour and the casting comes out rough.
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN,
				dev.forja.item.CastingMouldItem.of(PartType.CABEZA_PICO));
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STRAINER,
				dev.forja.item.StrainerItem.of(null));
			tank.fill(net.minecraft.world.item.Items.DIAMOND, 60);
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, boxAt, level.getBlockState(boxAt), box);
			}
			ItemStack rough = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT);
			int wasRough = rough.getOrDefault(dev.forja.registry.ModComponents.ROUGH, false) ? 1 : 0;
			int strainerGone = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STRAINER).isEmpty() ? 1 : 0;

			// The same pour through a damascus strainer comes out clean and the strainer survives.
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STRAINER,
				dev.forja.item.StrainerItem.of(dev.forja.material.ForgeMaterial.NETHERITA));
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, boxAt, level.getBlockState(boxAt), box);
			}
			ItemStack clean = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT);
			int wasClean = clean.isEmpty() || clean.getOrDefault(dev.forja.registry.ModComponents.ROUGH, false) ? 0 : 1;
			int strainerKept = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STRAINER).isEmpty() ? 0 : 1;

			// And infusing: a clay strainer in the pattern slot over a bath of diamond comes out better.
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN,
				dev.forja.item.StrainerItem.of(null));
			tank.fill(net.minecraft.world.item.Items.DIAMOND, 60);
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, boxAt, level.getBlockState(boxAt), box);
			}
			ItemStack infused = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT);
			int infusedHolds = dev.forja.item.StrainerItem.materialOf(infused) == null ? 0
				: dev.forja.item.StrainerItem.holds(infused);

			level.removeBlock(boxAt, false);
			level.removeBlock(tankAt, false);
			return new int[] {wasRough, strainerGone, wasClean, strainerKept, infusedHolds};
		});
		log("coladores: el de barro revienta con el diamante " + (strainers[0] > 0)
			+ " y desaparece " + (strainers[1] > 0) + ", el bueno cuela limpio " + (strainers[2] > 0)
			+ " y sobrevive " + (strainers[3] > 0) + ", infusionado aguanta " + strainers[4]);
		check(strainers[0] > 0, "diamond through a clay strainer should come out rough");
		check(strainers[1] > 0, "and take the strainer with it");
		check(strainers[2] > 0, "a strainer that holds should give a clean casting");
		check(strainers[3] > 0, "and survive the pour");
		check(strainers[4] > dev.forja.item.StrainerItem.CLAY_HOLDS,
			"infusing should leave a strainer that holds more, got " + strainers[4]);

		// And the price of a rough casting shows up on the finished piece.
		float[] roughCost = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var registries = level.registryAccess();
			// Built from the type's own slot list, so it really is a pick and not two parts in a bag.
			java.util.List<ItemStack> parts = new java.util.ArrayList<>();
			for (PartType slot : ForgeType.PICO.slots) {
				parts.add(Assembler.createPart(slot, HIERRO));
			}
			ItemStack clean = Assembler.evaluate(parts.stream().map(ItemStack::copy).toList(), registries).stack();
			java.util.List<ItemStack> roughParts = parts.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toList());
			roughParts.getFirst().set(dev.forja.registry.ModComponents.ROUGH, true);
			ItemStack rough = Assembler.evaluate(roughParts, registries).stack();
			check(!clean.isEmpty() && !rough.isEmpty(), "both picks should assemble");
			return new float[] {clean.getMaxDamage(), rough.getMaxDamage()};
		});
		log("pieza basta: durabilidad limpia " + (int) roughCost[0] + ", basta " + (int) roughCost[1]);
		check(roughCost[1] < roughCost[0], "a rough casting should cost the finished piece durability, got "
			+ (int) roughCost[1] + " against " + (int) roughCost[0]);

		// ---- The pipes: a crucible reaches a tank down a run of them, and a bank empties down one.
		boolean[] pipes2 = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos potAt = new BlockPos(px - 6, y, pz + 6);
			BlockPos tankAt = potAt.west().west().west().west();
			for (BlockPos at : List.of(potAt.west(), potAt.west().west(), potAt.west().west().west())) {
				level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			}
			level.setBlockAndUpdate(potAt, dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
			level.setBlockAndUpdate(tankAt, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			// The pipes have to have noticed both ends.
			boolean joined = level.getBlockState(potAt.west()).getValue(dev.forja.block.MeltPipeBlock.EAST)
				&& level.getBlockState(potAt.west().west().west()).getValue(dev.forja.block.MeltPipeBlock.WEST);

			var pot = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(potAt);
			var tank = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(tankAt);
			check(pot != null && tank != null, "the piped foundry should have its block entities");
			pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
			pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND, new ItemStack(net.minecraft.world.item.Items.COAL, 2));
			pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA, 4));
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CrucibleBlockEntity.serverTick(level, potAt, level.getBlockState(potAt), pot);
			}
			boolean pouredDownThePipe = tank.bankAmount() > 0
				&& pot.getItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_OUTPUT).isEmpty();

			// And the bank sends its metal down a pipe into a chest at the far end.
			BlockPos chestAt = tankAt.north().north();
			level.setBlockAndUpdate(tankAt.north(), dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			level.setBlockAndUpdate(chestAt, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
			var chest = (net.minecraft.world.Container) level.getBlockEntity(chestAt);
			check(chest != null, "the chest should have its block entity");
			for (int tick = 0; tick < 80; tick++) {
				dev.forja.block.entity.MeltTankBlockEntity.serverTick(level, tankAt, level.getBlockState(tankAt), tank);
			}
			boolean delivered = !chest.isEmpty();
			for (BlockPos at : List.of(potAt, tankAt, chestAt, tankAt.north(), potAt.west(),
				potAt.west().west(), potAt.west().west().west())) {
				level.removeBlock(at, false);
			}
			return new boolean[] {joined, pouredDownThePipe, delivered};
		});
		log("conductos: se enganchan a los dos extremos " + pipes2[0] + ", el crisol cuela por la tuberia "
			+ pipes2[1] + ", la cuba reparte por la tuberia " + pipes2[2]);
		check(pipes2[0], "a pipe should take the shape of what it is touching");
		check(pipes2[1], "a crucible should reach a tank three pipes away");
		check(pipes2[2], "and a bank should empty down a pipe into whatever is on the end of it");

		// A picture of the three, with the middle one working, because "which is which" should be
		// answerable by looking at them.
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d smooth_stone", px - 4, y - 1, pz - 1, px + 4, y - 1, pz + 3));
		server.runOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			net.minecraft.world.entity.item.ItemEntity.class,
			new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(20.0)
		).forEach(net.minecraft.world.entity.Entity::discard));
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			level.setBlockAndUpdate(new BlockPos(px - 2, y, pz), dev.forja.registry.ModBlocks.CRISOL_DE_BARRO.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(px, y, pz), dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState()
				.setValue(dev.forja.block.CrucibleBlock.LIT, true));
			level.setBlockAndUpdate(new BlockPos(px + 2, y, pz), dev.forja.registry.ModBlocks.CRISOL_DE_OBSIDIANA.defaultBlockState()
				.setValue(dev.forja.block.CrucibleBlock.LIT, true));
			// And a bank of tanks behind them, filled part way, because the level in the glass is the
			// whole idea and an empty one would show nothing.
			for (int dx = -2; dx <= 2; dx += 2) {
				for (int dy = 0; dy < 2; dy++) {
					level.setBlockAndUpdate(new BlockPos(px + dx, y + dy, pz + 2), dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
				}
			}
			var bank = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(new BlockPos(px - 2, y, pz + 2));
			check(bank != null, "the bank should have its block entity");
			bank.fill(net.minecraft.world.item.Items.IRON_INGOT, 300);
			var right = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(new BlockPos(px + 2, y, pz + 2));
			check(right != null, "the right tank should have its block entity");
			right.fill(net.minecraft.world.item.Items.GOLD_INGOT, 120);
			var middle = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(new BlockPos(px, y, pz + 2));
			check(middle != null, "the middle tank should have its block entity");
			middle.fill(dev.forja.registry.ModItems.alloy("acero"), 180);
			// And the pipes that join the pots to the glass behind them.
			for (int dx = -2; dx <= 2; dx += 2) {
				level.setBlockAndUpdate(new BlockPos(px + dx, y, pz + 1), dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			}
			for (int dx = -1; dx <= 1; dx += 2) {
				level.setBlockAndUpdate(new BlockPos(px + dx, y, pz + 1), dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			}
		});
		server.runCommand("time set noon");
		server.runCommand("weather clear 1000000");
		server.runCommand("gamemode spectator @a");
		tp(server, px + 0.5, y + 3.4, pz - 5.5, 0.0F, 22.0F);
		// Long enough for the sky to actually clear and for whatever the last check said to stop
		// arriving in chat, because both of those show up in the picture otherwise.
		context.waitTicks(60);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(5);
		// Before the picture: does the client even know what is in the glass? A tank that renders as
		// empty is either a sync problem or a drawing problem, and this says which.
		int[] seen = context.computeOnClient(mc -> {
			var at = new BlockPos(px - 2, y, pz + 2);
			if (mc.level != null && mc.level.getBlockEntity(at) instanceof dev.forja.block.entity.MeltTankBlockEntity tank) {
				return new int[] {tank.amount(), tank.metal() == null ? 0 : 1};
			}
			return new int[] {-1, -1};
		});
		log("cuba en el cliente: cantidad " + seen[0] + ", metal " + (seen[1] > 0));
		check(seen[0] == dev.forja.block.entity.MeltTankBlockEntity.CAPACITY,
			"the client should see the bottom tank full, got " + seen[0]);
		check(seen[1] > 0, "and which metal it is");

		context.takeScreenshot("forja_31_crisoles");
		// And the screen itself, with the pot half full and the fire in it, so the basin has something
		// to draw. A screenshot of an empty crucible would prove nothing.
		server.runCommand("gamemode creative @a");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos at = new BlockPos(px, y, pz);
			level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
			var crucible = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(at);
			check(crucible != null, "the crucible should have its block entity");
			crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 6));
			crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND, new ItemStack(net.minecraft.world.item.Items.COAL, 4));
			crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA, 6));
			for (int tick = 0; tick < 40; tick++) {
				dev.forja.block.entity.CrucibleBlockEntity.serverTick(level, at, level.getBlockState(at), crucible);
			}
			connection.getServerPlayer().openMenu(crucible);
		});
		context.waitTicks(10);
		context.takeScreenshot("forja_32_crisol_pantalla");
		context.runOnClient(mc -> mc.player.closeContainer());
		context.waitTicks(3);
		server.runCommand("gamemode survival @a");
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (int dx = -2; dx <= 2; dx += 2) {
				level.removeBlock(new BlockPos(px + dx, y, pz), false);
				for (int dy = 0; dy < 2; dy++) {
					level.removeBlock(new BlockPos(px + dx, y + dy, pz + 2), false);
				}
				level.removeBlock(new BlockPos(px + dx, y, pz + 1), false);
			}
			level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(12.0)).forEach(net.minecraft.world.entity.Entity::discard);
		});
	}

	/**
	 * The foundry alloys: that white heat is genuinely out of a table's reach, that the obsidian
	 * crucible pours the three that need it, and that each of the seven traits does the thing its
	 * tooltip claims rather than merely existing in the enum.
	 */
	private static void checkFoundryAlloys(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 90;
		int pz = z + 60;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", px - 4, y - 1, pz - 4, px + 8, y - 1, pz + 8));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 4, y, pz - 4, px + 8, y + 4, pz + 8));
		// Nothing hostile can even be built on peaceful, and half of these traits need something to hit.
		server.runCommand("difficulty easy");

		// ---- white heat is not something you can stand a table over.
		int[] reach = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos tableAt = new BlockPos(px, y, pz);
			int worst = 0;
			for (net.minecraft.world.level.block.Block under : List.of(Blocks.LAVA,
				Blocks.LAVA_CAULDRON,
				Blocks.MAGMA_BLOCK,
				dev.forja.registry.ModBlocks.FAROL_DE_PAVESA)) {
				level.setBlockAndUpdate(tableAt.below(), under.defaultBlockState());
				worst = Math.max(worst, dev.forja.forge.Alloys.heatUnder(level, tableAt).ordinal());
			}
			level.setBlockAndUpdate(tableAt.below(), Blocks.STONE.defaultBlockState());
			// And the bellows technique, which reads the fire one step hotter, still stops at molten.
			int bellows = dev.forja.forge.Alloys.Heat.FUNDIDA.hotter().ordinal();
			return new int[] {worst, bellows, dev.forja.forge.Alloys.Heat.FORJA_BLANCA.ordinal()};
		});
		log("forja blanca: lo mas caliente bajo una mesa es " + dev.forja.forge.Alloys.Heat.values()[reach[0]].name()
			+ ", con fuelle " + dev.forja.forge.Alloys.Heat.values()[reach[1]].name());
		check(reach[0] < reach[2], "no block under a table should ever give white heat");
		check(reach[1] < reach[2], "and no technique should read its way up to it either");

		// ---- the recipes: four any hot crucible will take, three only the obsidian one will.
		int[] counts = server.computeOnServer(s -> {
			int normal = 0;
			int white = 0;
			for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.forge.Alloys.ALL) {
				boolean only = dev.forja.forge.Alloys.WHITE_HEAT_ONLY.contains(recipe.id());
				check(only == (recipe.heat() == dev.forja.forge.Alloys.Heat.FORJA_BLANCA),
					"white-heat-only and white heat must agree: " + recipe.id());
				if (only) {
					white++;
				} else if (recipe.id().equals("cinerio") || recipe.id().equals("voltaico")
					|| recipe.id().equals("almacero") || recipe.id().equals("vidriacero")) {
					normal++;
				}
			}
			return new int[] {normal, white};
		});
		log("aleaciones nuevas: " + counts[0] + " normales y " + counts[1] + " exclusivas de la forja blanca");
		check(counts[0] == 4, "there should be four new ordinary alloys, got " + counts[0]);
		check(counts[1] == 3, "and three the best crucible keeps to itself, got " + counts[1]);

		// ---- pour one of each in the crucible that should and should not manage it.
		int[] poured = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int[] made = new int[2];
			var tiers = List.of(dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO, dev.forja.registry.ModBlocks.CRISOL_DE_OBSIDIANA);
			for (int i = 0; i < tiers.size(); i++) {
				BlockPos at = new BlockPos(px + 2 + i * 2, y, pz);
				level.setBlockAndUpdate(at, tiers.get(i).defaultBlockState());
				var crucible = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(at);
				check(crucible != null, "the crucible should have its block entity");
				// Sun steel: damascus, blaze rods and gold, which is white heat or nothing.
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST,
					new ItemStack(dev.forja.registry.ModItems.alloy("damasco"), 1));
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND,
					new ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 3));
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL,
					new ItemStack(dev.forja.registry.ModItems.ASCUA, 4));
				for (int tick = 0; tick < 260; tick++) {
					dev.forja.block.entity.CrucibleBlockEntity.serverTick(level, at, level.getBlockState(at), crucible);
				}
				made[i] = crucible.getItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_OUTPUT).getCount();
				level.removeBlock(at, false);
			}
			return made;
		});
		log("solacero: el crisol de hierro saca " + poured[0] + ", el de obsidiana " + poured[1]);
		check(poured[0] == 0, "an iron crucible must not reach sun steel, got " + poured[0]);

		// ---- the seven traits, each measured by what it does rather than by being declared.
		float[] traits = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var registries = level.registryAccess();
			var player = connection.getServerPlayer();
			player.snapTo(px + 0.5, y, pz + 4.5, 0.0F, 0.0F);

			// Ascua: the same sword, once cold and once with the smith on fire.
			ItemStack ember = Assembler.create(ForgeType.ESPADA,
				List.of(dev.forja.material.ForgeMaterial.CINERIO, dev.forja.material.ForgeMaterial.CINERIO, dev.forja.material.ForgeMaterial.CINERIO), registries);
			var target = zombie(level, px + 2.5, y, pz + 4.5);
			player.setRemainingFireTicks(0);
			float cold = dev.forja.upgrade.TraitEffects.weaponBonus(level, player, target, ember);
			player.setRemainingFireTicks(100);
			float burning = dev.forja.upgrade.TraitEffects.weaponBonus(level, player, target, ember);
			player.setRemainingFireTicks(0);

			// Cargado: three blows go in, the fourth comes out sideways.
			ItemStack volt = Assembler.create(ForgeType.ESPADA,
				List.of(dev.forja.material.ForgeMaterial.VOLTAICO, dev.forja.material.ForgeMaterial.VOLTAICO, dev.forja.material.ForgeMaterial.VOLTAICO), registries);
			var bystander = zombie(level, px + 3.5, y, pz + 4.5);
			int arcs = 0;
			int stored = 0;
			for (int hit = 0; hit < dev.forja.upgrade.TraitEffects.VOLTAIC_FULL; hit++) {
				arcs = dev.forja.upgrade.TraitEffects.charge(level, player, target, volt).size();
				stored = volt.getOrDefault(dev.forja.registry.ModComponents.CARGA, 0);
			}

			// Animado: it stands one blow, and then it does not stand the next.
			ItemStack soul = Assembler.create(ForgeType.ESPADA,
				List.of(dev.forja.material.ForgeMaterial.ALMACERO, dev.forja.material.ForgeMaterial.ALMACERO, dev.forja.material.ForgeMaterial.ALMACERO), registries);
			ItemStack heldBefore = player.getMainHandItem().copy();
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, soul);
			int first = dev.forja.upgrade.TraitEffects.guards(level, player) ? 1 : 0;
			int second = dev.forja.upgrade.TraitEffects.guards(level, player) ? 1 : 0;
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, heldBefore);

			// Diafano: glass steel armour weighs so little it leaves you faster than you were.
			ItemStack glass = Assembler.create(ForgeType.PECHERA,
				List.of(dev.forja.material.ForgeMaterial.VIDRIACERO, dev.forja.material.ForgeMaterial.CUERO), registries);
			ItemStack heavy = Assembler.create(ForgeType.PECHERA,
				List.of(dev.forja.material.ForgeMaterial.OBSIDIACERO, dev.forja.material.ForgeMaterial.CUERO), registries);
			float light = modifierTotal(glass, Attributes.MOVEMENT_SPEED);
			float lead = modifierTotal(heavy, Attributes.MOVEMENT_SPEED);

			// Vivo: a kill mends it, and a whole one hands the rest to the smith.
			ItemStack living = Assembler.create(ForgeType.ESPADA,
				List.of(dev.forja.material.ForgeMaterial.ACERO_VIVO, dev.forja.material.ForgeMaterial.ACERO_VIVO, dev.forja.material.ForgeMaterial.ACERO_VIVO), registries);
			living.setDamageValue(100);
			dev.forja.upgrade.TraitEffects.onKill(level, player, living);
			int mended = 100 - living.getDamageValue();
			player.setHealth(player.getMaxHealth() - 4.0F);
			living.setDamageValue(0);
			dev.forja.upgrade.TraitEffects.onKill(level, player, living);
			float healed = player.getHealth() - (player.getMaxHealth() - 4.0F);
			player.setHealth(player.getMaxHealth());

			for (var mob : List.of(target, bystander)) {
				mob.discard();
			}
			return new float[] {cold, burning, arcs, stored, first, second, light, lead, mended, healed};
		});

		// Solar and nocturno read the sky, so the clock has to move between the two measurements, and
		// only the command will move it.
		float[] noon = weaponBonusNow(context, server, connection, px, y, pz, "noon");
		float[] midnight = weaponBonusNow(context, server, connection, px, y, pz, "midnight");
		server.runCommand("time set noon");
		log("ascua: frio " + traits[0] + ", ardiendo " + traits[1]
			+ " | cargado: salta a " + (int) traits[2] + " y la carga vuelve a " + (int) traits[3]
			+ " | animado: para el primero " + (traits[4] > 0) + ", el segundo " + (traits[5] > 0));
		log("diafano: pechera de vidriacero " + traits[6] + " de velocidad frente a " + traits[7]
			+ " | solar: mediodia " + noon[0] + ", medianoche " + midnight[0]
			+ " | nocturno: mediodia " + noon[1] + ", medianoche " + midnight[1]
			+ " | vivo: repara " + (int) traits[8] + " y cura " + traits[9]);
		check(traits[0] == 0.0F, "cinereous steel should do nothing while the smith is cold");
		check(traits[1] >= dev.forja.upgrade.TraitEffects.EMBER_DAMAGE, "and hit harder while he burns");
		check(traits[2] > 0.0F, "the fourth voltaic blow should find something to arc to");
		check(traits[3] == 0.0F, "and empty the charge doing it");
		check(traits[4] > 0.0F && traits[5] == 0.0F, "soul steel stands one blow, not two in a row");
		check(traits[6] > traits[7], "glass steel should leave you faster than obsidian steel");
		check(noon[0] > midnight[0], "sun steel should be worth more at noon than at midnight");
		check(midnight[1] > noon[1], "and moon steel the other way round");
		check(traits[8] > 0.0F, "a kill should mend living steel");
		check(traits[9] > 0.0F, "and mend the smith once the blade is whole");
		server.runCommand("difficulty peaceful");
	}

	/**
	 * The casting tables: that the box cuts a frame off a finished tool, that a table pulls exactly what
	 * the tool is worth and no more, that what comes off it is the finished thing, and that the three
	 * differ in the one way they are supposed to — how long the stone holds its heat.
	 */
	private static void checkCastingTables(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 70;
		int pz = z + 60;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", px - 4, y - 1, pz - 4, px + 10, y - 1, pz + 6));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 4, y, pz - 4, px + 10, y + 4, pz + 6));

		// ---- the box cuts a frame off a finished pickaxe, and it costs the pickaxe.
		int[] cut = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var registries = level.registryAccess();
			BlockPos boxAt = new BlockPos(px, y, pz);
			level.setBlockAndUpdate(boxAt, dev.forja.registry.ModBlocks.CAJA_DE_MOLDEO.defaultBlockState());
			var box = (dev.forja.block.entity.CastingBoxBlockEntity) level.getBlockEntity(boxAt);
			check(box != null, "the casting box should have its block entity");
			ItemStack pick = Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, HIERRO), registries);
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN, pick);
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STEEL,
				new ItemStack(dev.forja.registry.ModItems.alloy("acero_refractario"),
					dev.forja.block.entity.CastingBoxBlockEntity.FRAME_COST));
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, boxAt, level.getBlockState(boxAt), box);
			}
			ItemStack out = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT);
			var framed = dev.forja.item.CastingFrameItem.typeOf(out);
			int gone = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN).isEmpty() ? 1 : 0;
			int steelLeft = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STEEL).getCount();
			level.removeBlock(boxAt, false);
			return new int[] {framed == ForgeType.PICO ? 1 : 0, gone, steelLeft,
				dev.forja.item.CastingFrameItem.cost(ForgeType.PICO)};
		});
		log("marco: la caja saca el del pico " + (cut[0] > 0) + ", se come la herramienta " + (cut[1] > 0)
			+ ", acero restante " + cut[2] + ", cuesta " + cut[3] + " de metal por colada");
		check(cut[0] > 0, "a finished pickaxe in the box should give the pickaxe's frame");
		check(cut[1] > 0, "and the pickaxe should not survive it");
		check(cut[2] == 0, "and it should cost all six of the refractory steel, got " + cut[2]);
		check(cut[3] > 0, "a frame has to cost some metal to fill");

		// ---- a table with a frame and a tank beside it pours the whole tool.
		int[] poured = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos tableAt = new BlockPos(px + 3, y, pz);
			BlockPos tankAt = tableAt.east();
			level.setBlockAndUpdate(tableAt, dev.forja.registry.ModBlocks.MESA_DE_LOSA.defaultBlockState());
			level.setBlockAndUpdate(tankAt, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			// A lantern under it, so this measurement is about the pour and not about the heat.
			level.setBlockAndUpdate(tableAt.below(), dev.forja.registry.ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
			var table = (dev.forja.block.entity.CastingTableBlockEntity) level.getBlockEntity(tableAt);
			var tank = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(tankAt);
			check(table != null && tank != null, "the table and its tank should have their block entities");

			int cost = dev.forja.item.CastingFrameItem.cost(ForgeType.PICO);
			tank.fill(net.minecraft.world.item.Items.IRON_INGOT, cost + 40);
			table.setItem(dev.forja.block.entity.CastingTableBlockEntity.SLOT_FRAME,
				dev.forja.item.CastingFrameItem.of(ForgeType.PICO));
			int before = tank.bankAmount();
			for (int tick = 0; tick < dev.forja.block.entity.CastingTableBlockEntity.COOK + 60; tick++) {
				dev.forja.block.entity.CastingTableBlockEntity.serverTick(level, tableAt, level.getBlockState(tableAt), table);
			}
			ItemStack made = table.getItem(dev.forja.block.entity.CastingTableBlockEntity.SLOT_OUTPUT);
			var parts = made.get(dev.forja.registry.ModComponents.PARTS);
			int isPick = parts != null && parts.type() == ForgeType.PICO ? 1 : 0;
			int allIron = parts != null && parts.materials().stream().allMatch(m -> m == HIERRO) ? 1 : 0;
			int spent = before - tank.bankAmount();
			int frameKept = table.getItem(dev.forja.block.entity.CastingTableBlockEntity.SLOT_FRAME).isEmpty() ? 0 : 1;

			for (BlockPos at : List.of(tableAt, tankAt, tableAt.below())) {
				level.removeBlock(at, false);
			}
			return new int[] {isPick, allIron, spent, cost, frameKept};
		});
		log("mesa de colada: sale un pico " + (poured[0] > 0) + " todo de hierro " + (poured[1] > 0)
			+ ", gasta " + poured[2] + " de metal para un coste de " + poured[3]
			+ ", el marco se queda " + (poured[4] > 0));
		check(poured[0] > 0, "the table should turn out a finished pickaxe");
		check(poured[1] > 0, "cast in the one metal the tank was holding");
		check(poured[2] == poured[3], "and take exactly what the tool is worth, got " + poured[2] + " of " + poured[3]);
		check(poured[4] > 0, "the frame is not spent by a pour");

		// ---- the heat: the better the stone, the longer it lasts with nothing warming it.
		int[] heat = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int[] left = new int[3];
			var tiers = List.of(dev.forja.registry.ModBlocks.MESA_DE_LOSA,
				dev.forja.registry.ModBlocks.MESA_DE_BRASA, dev.forja.registry.ModBlocks.MESA_DE_ALMAS);
			for (int i = 0; i < tiers.size(); i++) {
				BlockPos at = new BlockPos(px + 6, y, pz + i * 2);
				level.setBlockAndUpdate(at, tiers.get(i).defaultBlockState());
				BlockPos lantern = at.below();
				level.setBlockAndUpdate(lantern, dev.forja.registry.ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
				var table = (dev.forja.block.entity.CastingTableBlockEntity) level.getBlockEntity(at);
				check(table != null, "each casting table should have its block entity");
				// Fill it up over the lantern, then take the lantern away and let it stand a while.
				for (int tick = 0; tick < 20 * dev.forja.block.entity.CastingTableBlockEntity.EVERY; tick++) {
					dev.forja.block.entity.CastingTableBlockEntity.serverTick(level, at, level.getBlockState(at), table);
				}
				level.removeBlock(lantern, false);
				for (int tick = 0; tick < 25 * dev.forja.block.entity.CastingTableBlockEntity.EVERY; tick++) {
					dev.forja.block.entity.CastingTableBlockEntity.serverTick(level, at, level.getBlockState(at), table);
				}
				left[i] = table.heat();
				level.removeBlock(at, false);
			}
			return left;
		});
		log("calor tras 25 s sin fuego: losa " + heat[0] + ", brasa " + heat[1] + ", almas " + heat[2]
			+ " (de " + dev.forja.block.entity.CastingTableBlockEntity.HOT + ")");
		check(heat[2] > heat[1] && heat[1] > heat[0], "better stone should hold heat longer, got "
			+ heat[0] + " / " + heat[1] + " / " + heat[2]);
		check(heat[0] < dev.forja.block.entity.CastingTableBlockEntity.HOT, "and the worst should have lost some");

		// ---- a cold table starts nothing, and one started on the last of the heat comes out rough.
		int[] cold = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos tableAt = new BlockPos(px + 9, y, pz);
			BlockPos tankAt = tableAt.east();
			level.setBlockAndUpdate(tableAt, dev.forja.registry.ModBlocks.MESA_DE_LOSA.defaultBlockState());
			level.setBlockAndUpdate(tankAt, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			var table = (dev.forja.block.entity.CastingTableBlockEntity) level.getBlockEntity(tableAt);
			var tank = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(tankAt);
			check(table != null && tank != null, "the cold table and its tank should have their block entities");
			tank.fill(net.minecraft.world.item.Items.IRON_INGOT, 200);
			table.setItem(dev.forja.block.entity.CastingTableBlockEntity.SLOT_FRAME,
				dev.forja.item.CastingFrameItem.of(ForgeType.PICO));
			// Stone cold: it should not touch the tank at all.
			int held = tank.bankAmount();
			for (int tick = 0; tick < dev.forja.block.entity.CastingTableBlockEntity.COOK + 40; tick++) {
				dev.forja.block.entity.CastingTableBlockEntity.serverTick(level, tableAt, level.getBlockState(tableAt), table);
			}
			int stillHeld = tank.bankAmount();
			int madeNothing = table.getItem(dev.forja.block.entity.CastingTableBlockEntity.SLOT_OUTPUT).isEmpty() ? 1 : 0;

			// Now warm it just under what a pour costs, and the one it starts comes out rough.
			level.setBlockAndUpdate(tableAt.below(), dev.forja.registry.ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
			for (int tick = 0; tick < dev.forja.block.entity.CastingTableBlockEntity.EVERY + 2; tick++) {
				dev.forja.block.entity.CastingTableBlockEntity.serverTick(level, tableAt, level.getBlockState(tableAt), table);
			}
			level.removeBlock(tableAt.below(), false);
			int warmed = table.heat();
			for (int tick = 0; tick < dev.forja.block.entity.CastingTableBlockEntity.COOK + 40; tick++) {
				dev.forja.block.entity.CastingTableBlockEntity.serverTick(level, tableAt, level.getBlockState(tableAt), table);
			}
			ItemStack made = table.getItem(dev.forja.block.entity.CastingTableBlockEntity.SLOT_OUTPUT);
			int rough = made.getOrDefault(dev.forja.registry.ModComponents.ROUGH, false) ? 1 : 0;
			int madeSomething = made.isEmpty() ? 0 : 1;

			for (BlockPos at : List.of(tableAt, tankAt)) {
				level.removeBlock(at, false);
			}
			return new int[] {held == stillHeld ? 1 : 0, madeNothing, warmed, madeSomething, rough};
		});
		log("mesa fría: no toca la cuba " + (cold[0] > 0) + " y no saca nada " + (cold[1] > 0)
			+ " · con " + cold[2] + " de calor cuela " + (cold[3] > 0) + " y sale basta " + (cold[4] > 0));
		check(cold[0] > 0, "a cold table should not touch the tanks");
		check(cold[1] > 0, "and should turn out nothing at all");
		check(cold[2] > 0 && cold[2] < dev.forja.block.entity.CastingTableBlockEntity.SPEND,
			"the test wants it warm but under the price of a pour, got " + cold[2]);
		check(cold[3] > 0, "warmed a little, it should still pour");
		check(cold[4] > 0, "but a pour started on the last of the heat should come out rough");

		// And the luck is the other half of what the stone buys: the best stone is the likeliest.
		float[] luck = server.computeOnServer(s -> new float[] {
			dev.forja.block.CastingTableBlock.Tier.LOSA.luck,
			dev.forja.block.CastingTableBlock.Tier.BRASA.luck,
			dev.forja.block.CastingTableBlock.Tier.ALMAS.luck,
		});
		log("perfecta: losa " + Math.round(luck[0] * 100) + "%, brasa " + Math.round(luck[1] * 100)
			+ "%, almas " + Math.round(luck[2] * 100) + "%");
		check(luck[2] > luck[1] && luck[1] > luck[0], "better stone should be likelier to pour a perfect piece");
		check(luck[0] > 0.0F, "and even the plainest should manage it sometimes");

		// ---- and a picture of all three, mid-pour, because the whole interface is what you can see.
		int shotZ = pz + 4;
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var tiers = List.of(dev.forja.registry.ModBlocks.MESA_DE_LOSA,
				dev.forja.registry.ModBlocks.MESA_DE_BRASA, dev.forja.registry.ModBlocks.MESA_DE_ALMAS);
			var metals = List.of(net.minecraft.world.item.Items.IRON_INGOT,
				net.minecraft.world.item.Items.GOLD_INGOT, net.minecraft.world.item.Items.DIAMOND);
			var made = List.of(ForgeType.PICO, ForgeType.ESPADA, ForgeType.HACHA);
			for (int i = 0; i < tiers.size(); i++) {
				BlockPos at = new BlockPos(px + i * 3, y, shotZ);
				level.setBlockAndUpdate(at, tiers.get(i).defaultBlockState());
				level.setBlockAndUpdate(at.below(), dev.forja.registry.ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
				level.setBlockAndUpdate(at.east(), dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
				var table = (dev.forja.block.entity.CastingTableBlockEntity) level.getBlockEntity(at);
				var tank = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(at.east());
				check(table != null && tank != null, "the three tables should have their block entities");
				tank.fill(metals.get(i), 200);
				// Only warmed here. The frames go in after the sky has cleared, because the block ticks
				// for real while the test waits and a pour started now would be finished by then.
				for (int tick = 0; tick < dev.forja.block.entity.CastingTableBlockEntity.EVERY * 8; tick++) {
					dev.forja.block.entity.CastingTableBlockEntity.serverTick(level, at, level.getBlockState(at), table);
				}
			}
		});
		server.runCommand("time set noon");
		server.runCommand("weather clear 1000000");
		server.runCommand("gamemode spectator @a");
		tp(server, px + 3.5, y + 2.6, shotZ - 4.5, 0.0F, 34.0F);
		context.waitTicks(60);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(5);
		// Now the frames, and just long enough for the metal to be falling into them: the stream only
		// draws over the first third of the pour, and the picture is meant to show it.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var made = List.of(ForgeType.PICO, ForgeType.ESPADA, ForgeType.HACHA);
			for (int i = 0; i < 3; i++) {
				if (level.getBlockEntity(new BlockPos(px + i * 3, y, shotZ))
					instanceof dev.forja.block.entity.CastingTableBlockEntity table) {
					table.setItem(dev.forja.block.entity.CastingTableBlockEntity.SLOT_FRAME,
						dev.forja.item.CastingFrameItem.of(made.get(i)));
				}
			}
		});
		context.waitTicks(20);
		// Does the client know there is metal in the middle table? An empty-looking pour is either a
		// sync problem or a drawing problem, and this says which before the picture is taken.
		int[] seen = context.computeOnClient(mc -> {
			var at = new BlockPos(px + 3, y, shotZ);
			if (mc.level != null && mc.level.getBlockEntity(at) instanceof dev.forja.block.entity.CastingTableBlockEntity table) {
				// progressAt, not progress: the client is never sent the counter, it works the pour out
				// off the world clock, and that is the number the renderer draws from.
				return new int[] {table.metal() == null ? 0 : 1,
					Math.round(table.progressAt(mc.level.getGameTime()) * 100.0F),
					table.frame().isEmpty() ? 0 : 1};
			}
			return new int[] {-1, -1, -1};
		});
		log("mesa en el cliente: metal " + (seen[0] > 0) + ", colada al " + seen[1] + "%, marco puesto " + (seen[2] > 0));
		check(seen[2] > 0, "the client should see the frame lying on the table");
		check(seen[0] > 0, "and the metal that is in it");
		check(seen[1] > 0 && seen[1] < 100, "and should be part way through the pour, got " + seen[1] + "%");
		context.takeScreenshot("forja_33_mesas_de_colada");
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (int i = 0; i < 3; i++) {
				BlockPos at = new BlockPos(px + i * 3, y, shotZ);
				for (BlockPos gone : List.of(at, at.below(), at.east())) {
					level.removeBlock(gone, false);
				}
			}
		});
	}

	/**
	 * The two forge benches: that the plain one builds the fourteen a smith needs and refuses the rest,
	 * that the greater one builds everything, and that neither of them touches barding.
	 */
	private static void checkTwoBenches(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int[] counts = server.computeOnServer(s -> {
			int bench = 0;
			int greater = 0;
			int saddlery = 0;
			int beyond = 0;
			for (ForgeType type : ForgeType.values()) {
				bench += Station.FORJA.canForge(type) ? 1 : 0;
				greater += Station.FORJA_MAYOR.canForge(type) ? 1 : 0;
				saddlery += Station.TALABARTERIA.canForge(type) ? 1 : 0;
				beyond += Station.FORJA.needsGreater(type) ? 1 : 0;
			}
			// The ones the split is actually about.
			int bow = Station.FORJA.canForge(ForgeType.ARCO) ? 1 : 0;
			int flail = Station.FORJA.canForge(ForgeType.MANGUAL) ? 1 : 0;
			int flailGreater = Station.FORJA_MAYOR.canForge(ForgeType.MANGUAL) ? 1 : 0;
			int bardingOnGreater = Station.FORJA_MAYOR.canForge(ForgeType.BARDA) ? 1 : 0;
			return new int[] {bench, greater, saddlery, beyond, bow, flail, flailGreater, bardingOnGreater};
		});
		log("bancos: el basico monta " + counts[0] + " tipos, el mayor " + counts[1]
			+ ", la talabarteria " + counts[2] + " · " + counts[3] + " piden mesa mayor"
			+ " · arco en el basico " + (counts[4] > 0) + ", mangual " + (counts[5] > 0)
			+ " y en el mayor " + (counts[6] > 0) + " · barda en el mayor " + (counts[7] > 0));
		check(counts[0] == 14, "the plain bench should build fourteen types, got " + counts[0]);
		check(counts[1] == ForgeType.values().length - 2,
			"the greater one should build everything but the two mount pieces, got " + counts[1]);
		check(counts[2] == 2, "the saddlery should build only barding and wolf armour, got " + counts[2]);
		check(counts[3] == counts[1] - counts[0], "everything the bench refuses should be offered by the greater one");
		check(counts[4] > 0, "the bow belongs on the plain bench");
		check(counts[5] == 0 && counts[6] > 0, "the flail belongs only on the greater one");
		check(counts[7] == 0, "and barding on neither: that is the saddlery's alone");

		// And the block really carries that station, rather than the rule living only in the enum.
		int[] blocks = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos at = new BlockPos(x + 20, y, z + 74);
			level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.MESA_DE_FORJA_MAYOR.defaultBlockState());
			boolean right = level.getBlockState(at).getBlock() instanceof dev.forja.block.ForgeTableBlock table
				&& table.station == Station.FORJA_MAYOR;
			level.removeBlock(at, false);
			return new int[] {right ? 1 : 0};
		});
		check(blocks[0] > 0, "the greater table block should carry the greater station");

		// The fallen smith's anvil: it stands in for both of the other tables, and it answers when you
		// click it, because otherwise the one thing it does is invisible.
		int[] anvil = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos table = new BlockPos(x + 22, y, z + 74);
			BlockPos at = table.offset(2, 0, 0);
			level.setBlockAndUpdate(table, dev.forja.registry.ModBlocks.MESA_DE_FORJA.defaultBlockState());
			level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.YUNQUE_DEL_HERRERO.defaultBlockState());
			boolean answers = level.getBlockState(at).useWithoutItem(level, connection.getServerPlayer(),
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at),
					net.minecraft.core.Direction.UP, at, false)).consumesAction();
			boolean shaped = level.getBlockState(at).getShape(level, at) != net.minecraft.world.phys.shapes.Shapes.block();
			level.removeBlock(at, false);
			level.removeBlock(table, false);
			return new int[] {answers ? 1 : 0, shaped ? 1 : 0};
		});
		log("yunque del herrero: contesta al clic " + (anvil[0] > 0) + ", forma propia " + (anvil[1] > 0));
		check(anvil[0] > 0, "the anvil should answer a click instead of doing nothing at all");
		check(anvil[1] > 0, "and it should have its own shape rather than a full cube");
	}

	/**
	 * Metal is poured, not carved: that a parts table cuts the basic materials and refuses the rest,
	 * and that pouring a part cleanly is worth an upgrade that rides up into what you build with it.
	 */
	private static void checkCastOnly(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 32;
		int pz = z + 74;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", px - 3, y - 1, pz - 3, px + 6, y - 1, pz + 4));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 3, y, pz - 3, px + 6, y + 4, pz + 4));

		int[] table = server.computeOnServer(s -> {
			// The bench: wood and stone still cut, iron and steel no longer do.
			int wood = Assembler.partResult(PartType.MANGO, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 16)).isEmpty() ? 0 : 1;
			int stone = Assembler.partResult(PartType.CABEZA_PICO, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 16)).isEmpty() ? 0 : 1;
			int echo = Assembler.partResult(PartType.HOJA, new ItemStack(net.minecraft.world.item.Items.ECHO_SHARD, 16)).isEmpty() ? 0 : 1;
			int iron = Assembler.partResult(PartType.CABEZA_PICO, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 16)).isEmpty() ? 0 : 1;
			int diamond = Assembler.partResult(PartType.CABEZA_PICO, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 16)).isEmpty() ? 0 : 1;
			int steel = Assembler.partResult(PartType.HOJA,
				new ItemStack(dev.forja.registry.ModItems.alloy("acero"), 16)).isEmpty() ? 0 : 1;
			int basics = 0;
			for (dev.forja.material.ForgeMaterial material : dev.forja.material.ForgeMaterial.values()) {
				basics += material.isBasic() ? 1 : 0;
			}
			return new int[] {wood, stone, echo, iron, diamond, steel, basics};
		});
		log("mesa de piezas: madera " + (table[0] > 0) + ", piedra " + (table[1] > 0) + ", eco " + (table[2] > 0)
			+ " | hierro " + (table[3] > 0) + ", diamante " + (table[4] > 0) + ", acero " + (table[5] > 0)
			+ " · " + table[6] + " materiales basicos");
		check(table[0] > 0 && table[1] > 0 && table[2] > 0, "wood, stone and echo are still cut at the bench");
		check(table[3] == 0 && table[4] == 0 && table[5] == 0, "iron, diamond and steel must be poured instead");
		check(table[6] == 12, "there should be twelve basic materials, got " + table[6]);

		// ---- and the pour: clean gives an upgrade, rough gives nothing, and it rides up on assembly.
		float[] cast = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var registries = level.registryAccess();
			BlockPos boxAt = new BlockPos(px, y, pz);
			BlockPos tankAt = boxAt.east();
			level.setBlockAndUpdate(boxAt, dev.forja.registry.ModBlocks.CAJA_DE_MOLDEO_DE_DAMASCO.defaultBlockState());
			level.setBlockAndUpdate(tankAt, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			var box = (dev.forja.block.entity.CastingBoxBlockEntity) level.getBlockEntity(boxAt);
			var tank = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(tankAt);
			check(box != null && tank != null, "the casting box and its tank should have their block entities");
			tank.fill(net.minecraft.world.item.Items.IRON_INGOT, 200);

			// A strainer that holds: the pour is clean.
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN,
				dev.forja.item.CastingMouldItem.of(PartType.CABEZA_PICO));
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STRAINER,
				dev.forja.item.StrainerItem.of(dev.forja.material.ForgeMaterial.NETHERITA));
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, boxAt, level.getBlockState(boxAt), box);
			}
			ItemStack clean = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT).copy();
			var cleanUp = clean.getOrDefault(dev.forja.registry.ModComponents.UPGRADES, dev.forja.upgrade.Upgrades.EMPTY);
			int cleanCount = cleanUp.percents().size();

			// No strainer at all: the pour is rough and worth nothing.
            box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STRAINER, ItemStack.EMPTY);
			for (int tick = 0; tick < 200; tick++) {
				dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, boxAt, level.getBlockState(boxAt), box);
			}
			ItemStack rough = box.getItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_OUTPUT).copy();
			int roughCount = rough.getOrDefault(dev.forja.registry.ModComponents.UPGRADES,
				dev.forja.upgrade.Upgrades.EMPTY).percents().size();

			// And the upgrade has to survive being built into something.
			ItemStack handle = Assembler.createPart(PartType.MANGO, MADERA);
			ItemStack binding = Assembler.createPart(PartType.ATADURA, MADERA);
			var built = Assembler.evaluate(List.of(clean.copy(), handle, binding), registries).stack();
			int builtCount = built.getOrDefault(dev.forja.registry.ModComponents.UPGRADES,
				dev.forja.upgrade.Upgrades.EMPTY).percents().size();
			int percent = cleanUp.percents().isEmpty() ? 0
				: cleanUp.percents().values().iterator().next();

			level.removeBlock(boxAt, false);
			level.removeBlock(tankAt, false);
			return new float[] {cleanCount, roughCount, builtCount, percent};
		});
		log("colada: limpia lleva " + (int) cast[0] + " mejora al " + (int) cast[3] + "%, basta lleva "
			+ (int) cast[1] + ", y montada la pieza el objeto lleva " + (int) cast[2]);
		check(cast[0] == 1, "a clean pour should carry one upgrade, got " + (int) cast[0]);
		check(cast[1] == 0, "a rough pour should carry none, got " + (int) cast[1]);
		check(cast[2] == 1, "and the upgrade should ride up into what is built with it");
		check(cast[3] == dev.forja.block.entity.CastingBoxBlockEntity.CAST_PERCENT,
			"at the percentage the box pours, got " + (int) cast[3]);
	}

	/**
	 * What a channel is carrying: that an unconnected one is drawn <b>empty</b>, that a connected one
	 * takes the colour of the metal actually going past, and that the band spreads along a run and
	 * drains back out of it when the tank runs dry.
	 */
	private static void checkFlow(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 44;
		int pz = z + 74;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", px - 3, y - 1, pz - 3, px + 10, y - 1, pz + 4));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 3, y, pz - 3, px + 10, y + 4, pz + 4));

		// The colour is carried by a timestamp that only goes stale with real time, so this check has to
		// let the world actually tick rather than driving the block entity by hand inside one tick.
		BlockPos lone = new BlockPos(px, y, pz + 3);
		BlockPos tankAt = new BlockPos(px, y, pz);
		BlockPos near = new BlockPos(px + 1, y, pz);
		BlockPos far = new BlockPos(px + 4, y, pz);
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			// A stub on its own in the middle of nowhere, and a run out of a tank of gold.
			level.setBlockAndUpdate(lone, dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			level.setBlockAndUpdate(tankAt, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			for (int dx = 1; dx <= 4; dx++) {
				level.setBlockAndUpdate(new BlockPos(px + dx, y, pz),
					dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			}
			if (level.getBlockEntity(tankAt) instanceof dev.forja.block.entity.MeltTankBlockEntity tank) {
				tank.fill(net.minecraft.world.item.Items.GOLD_INGOT, 120);
			}
		});
		// Long enough for the colour to walk the whole run, a block at a time.
		context.waitTicks(40);
		int[] wet = server.computeOnServer(s -> carrying(connection, List.of(near, far, lone)));
		log("colada: junto a la cuba lleva oro " + (wet[0] == 1) + ", cuatro bloques más allá " + (wet[1] == 1)
			+ ", el conducto suelto va seco " + (wet[2] == 0));
		check(wet[0] == 1, "a channel touching a tank of gold should be carrying gold");
		check(wet[1] == 1, "and so should the far end of the run");
		check(wet[2] == 0, "a channel connected to nothing should be drawn empty");

		// Empty the tank: with nothing stamping the run any more, the whole thing goes out.
		server.runOnServer(s -> {
			if (connection.getServerLevel().getBlockEntity(tankAt) instanceof dev.forja.block.entity.MeltTankBlockEntity tank) {
				tank.drain(1000);
			}
		});
		context.waitTicks(dev.forja.block.entity.MeltFlowBlockEntity.LIVE + 30);
		int[] dry = server.computeOnServer(s -> carrying(connection, List.of(near, far, lone)));
		log("colada: al vaciar la cuba se apaga junto a ella " + (dry[0] == 0) + " y al final del tramo " + (dry[1] == 0));
		check(dry[0] == 0 && dry[1] == 0, "a run should go out when the tank it came from is emptied");

		// And the same run carrying iron is a different metal, not the gold it used to hold.
		server.runOnServer(s -> {
			if (connection.getServerLevel().getBlockEntity(tankAt) instanceof dev.forja.block.entity.MeltTankBlockEntity tank) {
				tank.fill(net.minecraft.world.item.Items.IRON_INGOT, 120);
			}
		});
		context.waitTicks(40);
		int[] iron = server.computeOnServer(s -> carrying(connection, List.of(near, far, lone)));
		log("colada: con hierro el tramo cambia de metal " + (iron[0] == 2) + " hasta el final " + (iron[1] == 2));
		check(iron[0] == 2 && iron[1] == 2, "the same run carrying iron should report iron, not the gold it used to hold");

		// ---- and the picture: three runs of three metals, and one with nothing feeding it.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var metals = List.of(net.minecraft.world.item.Items.GOLD_INGOT,
				net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.DIAMOND);
			for (int row = 0; row < 3; row++) {
				BlockPos at = new BlockPos(px, y, pz + row);
				level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
				if (level.getBlockEntity(at) instanceof dev.forja.block.entity.MeltTankBlockEntity tank) {
					tank.fill(metals.get(row), dev.forja.block.entity.MeltTankBlockEntity.CAPACITY);
				}
				for (int dx = 1; dx <= 5; dx++) {
					level.setBlockAndUpdate(new BlockPos(px + dx, y, pz + row),
						dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
				}
			}
			// The fourth row is the same channel with nothing feeding it at all.
			for (int dx = 1; dx <= 5; dx++) {
				level.setBlockAndUpdate(new BlockPos(px + dx, y, pz + 3),
					dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			}
		});
		server.runCommand("time set noon");
		server.runCommand("gamemode spectator @a");
		tp(server, px + 3.0, y + 4.0, pz - 3.5, 0.0F, 48.0F);
		context.waitTicks(60);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(5);
		// The client is told what every run is carrying, even though only the spout draws with it today.
		int[] shown = context.computeOnClient(mc -> {
			BlockPos at = new BlockPos(px + 3, y, pz + 1);
			if (mc.level == null) {
				return new int[] {-1, -1};
			}
			var flow = mc.level.getBlockEntity(at);
			if (!(flow instanceof dev.forja.block.entity.MeltFlowBlockEntity channel)) {
				return new int[] {0, 0};
			}
			return new int[] {1, channel.metal() == null ? 0 : 1};
		});
		log("colada en el cliente: hay entidad " + (shown[0] > 0) + ", lleva metal " + (shown[1] > 0));
		check(shown[0] > 0, "the client should have a block entity on every channel");
		check(shown[1] > 0, "and know what the run is carrying");
		context.takeScreenshot("forja_37_colada");
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (int row = 0; row <= 3; row++) {
				for (int dx = 0; dx <= 5; dx++) {
					level.removeBlock(new BlockPos(px + dx, y, pz + row), false);
				}
			}
		});
	}

	/** What each of these channels says it is carrying: 0 nothing, 1 gold, 2 iron, 3 something else. */
	private static int[] carrying(TestServerConnection connection, List<BlockPos> at) {
		ServerLevel level = connection.getServerLevel();
		int[] out = new int[at.size()];
		for (int i = 0; i < at.size(); i++) {
			var flow = level.getBlockEntity(at.get(i));
			check(flow instanceof dev.forja.block.entity.MeltFlowBlockEntity, "every channel should have its block entity");
			var metal = ((dev.forja.block.entity.MeltFlowBlockEntity) flow).metal();
			out[i] = metal == null ? 0
				: metal == net.minecraft.world.item.Items.GOLD_INGOT ? 1
				: metal == net.minecraft.world.item.Items.IRON_INGOT ? 2 : 3;
		}
		return out;
	}

	/**
	 * The spout: that a run of channel reaches across open air through it, that the fall costs the metal
	 * more than any pipe does, and that a casting table standing under one is fed by it.
	 */
	private static void checkSpout(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 56;
		int pz = z + 74;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", px - 3, y - 1, pz - 3, px + 6, y - 1, pz + 6));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 3, y, pz - 3, px + 6, y + 8, pz + 6));

		int[] drop = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			// A tank on a gantry, a length of channel, a spout, and three blocks of nothing below it.
			BlockPos shelf = new BlockPos(px, y + 4, pz);
			level.setBlockAndUpdate(shelf, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			level.setBlockAndUpdate(shelf.east(), dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			BlockPos spoutAt = shelf.east().east();
			level.setBlockAndUpdate(spoutAt, dev.forja.registry.ModBlocks.CANO_DE_COLADA.defaultBlockState());
			BlockPos tableAt = new BlockPos(spoutAt.getX(), y, spoutAt.getZ());
			level.setBlockAndUpdate(tableAt, dev.forja.registry.ModBlocks.MESA_DE_ALMAS.defaultBlockState());

			// Where the spout says its metal lands, and what the run costs across the gap.
			BlockPos lands = dev.forja.block.MeltPipeBlock.landing(level, spoutAt);
			int found = lands != null && lands.equals(tableAt) ? 1 : 0;
			int cost = dev.forja.block.MeltPipeBlock.bleedBetween(level, shelf, tableAt);

			// And the other way round: the table has to notice the spout overhead by itself, because
			// it is the table that goes looking for metal, not the tank that goes looking for tables.
			int seesTank = 0;
			for (var tank : dev.forja.block.entity.MeltTankBlockEntity.reachableFrom(level, tableAt)) {
				if (tank.getBlockPos().equals(shelf)) {
					seesTank = 1;
				}
			}

			// A spout over a hole in the ground pours into nothing and must say so.
			level.removeBlock(tableAt, false);
			int overNothing = dev.forja.block.MeltPipeBlock.landing(level, spoutAt) == null ? 1 : 0;
			level.setBlockAndUpdate(tableAt, dev.forja.registry.ModBlocks.MESA_DE_ALMAS.defaultBlockState());

			// Now pour: fill the tank, warm the table, and let it run.
			var tank = (dev.forja.block.entity.MeltTankBlockEntity) level.getBlockEntity(shelf);
			var table = (dev.forja.block.entity.CastingTableBlockEntity) level.getBlockEntity(tableAt);
			check(tank != null && table != null, "the gantry tank and the table below should have their block entities");
			tank.fill(net.minecraft.world.item.Items.IRON_INGOT, 200);
			level.setBlockAndUpdate(tableAt.north(), dev.forja.registry.ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
			table.setItem(dev.forja.block.entity.CastingTableBlockEntity.SLOT_FRAME,
				dev.forja.item.CastingFrameItem.of(ForgeType.DAGA));
			int before = tank.bankAmount();
			for (int tick = 0; tick < dev.forja.block.entity.CastingTableBlockEntity.COOK * 2; tick++) {
				dev.forja.block.entity.CastingTableBlockEntity.serverTick(level, tableAt, level.getBlockState(tableAt), table);
			}
			ItemStack made = table.getItem(dev.forja.block.entity.CastingTableBlockEntity.SLOT_OUTPUT);
			var parts = made.get(dev.forja.registry.ModComponents.PARTS);
			int cast = parts != null && parts.type() == ForgeType.DAGA ? 1 : 0;
			int spent = before - tank.bankAmount();

			for (BlockPos at : List.of(shelf, shelf.east(), spoutAt, tableAt, tableAt.north())) {
				level.removeBlock(at, false);
			}
			return new int[] {found, cost, seesTank, overNothing, cast, spent};
		});
		log("caño: cae en la mesa " + (drop[0] > 0) + " a un coste de " + drop[1] + " de calor, la mesa lo ve "
			+ (drop[2] > 0) + ", sobre el vacío no cuela " + (drop[3] > 0)
			+ " · cuela una daga " + (drop[4] > 0) + " gastando " + drop[5]);
		check(drop[0] > 0, "a spout should find the table three blocks under it");
		check(drop[1] > dev.forja.block.MeltPipeBlock.Grade.BRONCE.bleeds,
			"and the fall should cost more than a block of the worst pipe, got " + drop[1]);
		check(drop[2] > 0, "the table has to see the tank back through the spout");
		check(drop[3] > 0, "a spout over nothing lands nowhere");
		check(drop[4] > 0, "and the table under it should cast");
		check(drop[5] == dev.forja.item.CastingFrameItem.cost(ForgeType.DAGA),
			"taking exactly what the dagger is worth, got " + drop[5]);
	}

	/**
	 * A whole foundry, built and photographed: crucible, tanks, channels, casting box and two casting
	 * tables, laid out the way a smith would lay them.
	 *
	 * <p>It checks nothing a measurement could check. It exists because the melt channel is a shape
	 * whose whole job is to be looked at, and the only way to know whether it reads is to look at it.
	 */
	/**
	 * Every block Forja adds, stood in three rows on a stone floor and photographed.
	 *
	 * <p>This is the only check in the file that asserts nothing. It exists because block art cannot
	 * be judged from a texture file: a face that reads beautifully at sixteen pixels can vanish at
	 * playing distance, and the only way to know is to stand in front of the thing.
	 */
	/**
	 * The essence jar: it has to catch a night, keep it, wear its colour, and pour out the orb.
	 *
	 * <p>The colour is checked as a real component rather than by eye, because the whole point of the
	 * two-step jar is that a shelf of them is readable, and a jar that catches an event but comes out
	 * the same grey as an empty one would pass every other test in this file.
	 */
	private static void checkEssenceJar(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", x + 90, y + 40, z + 90));
		context.waitTicks(5);
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			dev.forja.world.WorldEvents.start(level, dev.forja.world.WorldEvents.AURORA);
			check(dev.forja.world.WorldEvents.active(level) == dev.forja.world.WorldEvents.AURORA,
				"the aurora should be running after it is started by hand");

			// An empty jar under the open sky catches it.
			ItemStack empty = new ItemStack(dev.forja.registry.ModItems.JARRA);
			player.setItemInHand(InteractionHand.OFF_HAND, empty);
			empty.getItem().use(level, player, InteractionHand.OFF_HAND);
			ItemStack caught = find(player, stack -> dev.forja.item.EssenceJarItem.held(stack) != null);
			var colours = caught.isEmpty() ? null : caught.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
			int painted = colours == null || colours.colors().isEmpty() ? 0 : colours.colors().get(0);
			log("jarra: guarda " + (caught.isEmpty() ? "nada" : dev.forja.item.EssenceJarItem.held(caught).name())
				+ ", color 0x" + Integer.toHexString(painted) + ", se llama " + caught.getHoverName().getString());
			check(!caught.isEmpty() && dev.forja.item.EssenceJarItem.held(caught) == dev.forja.world.WorldEvents.AURORA,
				"a jar used under an aurora should come back holding it");
			check(painted == dev.forja.world.WorldEvents.AURORA.color,
				"and wearing the aurora's own colour, got 0x" + Integer.toHexString(painted));
			check(caught.getHoverName().getString().contains(dev.forja.world.WorldEvents.AURORA.displayName().getString()),
				"and naming the night it holds, got " + caught.getHoverName().getString());

			// Pouring it out gives the orb, and the jar goes with it.
			player.getInventory().clearContent();
			player.setItemInHand(InteractionHand.OFF_HAND, caught);
			caught.getItem().use(level, player, InteractionHand.OFF_HAND);
			ItemStack orb = find(player, stack -> stack.getItem() instanceof dev.forja.item.UpgradeOrbItem);
			var inside = orb.isEmpty() ? null : dev.forja.item.UpgradeOrbItem.orb(orb);
			log("al vaciarla sale: " + (inside == null ? "nada" : inside.upgrade() + " " + inside.percent() + "%"));
			check(inside != null && inside.upgrade() == dev.forja.world.WorldEvents.AURORA.upgrade && inside.percent() == 100,
				"emptying the jar should give the aurora's orb at full strength");
			check(find(player, stack -> stack.getItem() instanceof dev.forja.item.EssenceJarItem).isEmpty(),
				"and the jar should be gone with it");

			// And an empty jar with nothing in the sky does nothing at all.
			player.getInventory().clearContent();
			dev.forja.world.WorldEvents.stop(connection.getServerLevel());
			ItemStack spare = new ItemStack(dev.forja.registry.ModItems.JARRA);
			player.setItemInHand(InteractionHand.OFF_HAND, spare);
			spare.getItem().use(level, player, InteractionHand.OFF_HAND);
			check(dev.forja.item.EssenceJarItem.held(spare) == null && spare.getCount() == 1,
				"a jar held up at a quiet sky should stay empty");
			player.getInventory().clearContent();
		});
	}

	/** The first stack in the player's inventory that matches, or empty. */
	private static ItemStack find(ServerPlayer player, java.util.function.Predicate<ItemStack> wanted) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.isEmpty() && wanted.test(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static void shotWorkshop(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 90;
		int pz = z + 74;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d smooth_stone", px - 3, y - 1, pz - 3, px + 9, y - 1, pz + 9));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 3, y, pz - 3, px + 9, y + 6, pz + 9));

		// Front row: the benches, which share a kit but should not share a silhouette.
		String[] benches = {"mesa_de_forja", "mesa_de_forja_mayor", "mesa_de_piezas",
			"mesa_de_talabarteria", "armario_de_piezas"};
		for (int i = 0; i < benches.length; i++) {
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:%s", px + i * 2, y, pz, benches[i]));
		}
		// Middle row: the pots and the flasks, half of them working so both faces get seen.
		String[] hot = {"crisol_de_barro[lit=true]", "crisol_de_hierro[lit=false]",
			"crisol_de_obsidiana[lit=true]", "caja_de_moldeo[lit=true]",
			"caja_de_moldeo_de_damasco[lit=false]"};
		for (int i = 0; i < hot.length; i++) {
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:%s", px + i * 2, y, pz + 3, hot[i]));
		}
		// Back row: the four that are each their own thing.
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:farol_de_pavesa", px, y, pz + 6));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:fragua_apagada", px + 2, y, pz + 6));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:yunque_del_herrero[facing=east]", px + 4, y, pz + 6));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:cuba_de_colada", px + 6, y, pz + 6));
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos tank = new BlockPos(px + 6, y, pz + 6);
			if (level.getBlockEntity(tank) instanceof dev.forja.block.entity.MeltTankBlockEntity held) {
				held.fill(net.minecraft.world.item.Items.GOLD_INGOT, 120);
			}
		});

		server.runCommand("time set noon");
		server.runCommand("weather clear 1000000");
		server.runCommand("gamemode spectator @a");
		// The chat is cleared after each move rather than once up front: the gamemode messages land a
		// tick or two later and would otherwise sit across the picture.
		tp(server, px + 4.5, y + 3.2, pz - 4.5, 0.0F, 26.0F);
		context.waitTicks(40);
		quiet(context);
		context.takeScreenshot("forja_36_bloques");
		// And one from standing height, which is the distance they are actually played at.
		tp(server, px + 4.5, y + 1.6, pz - 2.5, 0.0F, 6.0F);
		context.waitTicks(20);
		quiet(context);
		context.takeScreenshot("forja_36_bloques_de_pie");
		// The anvil close up: it is the only one with a shape of its own, so it gets its own picture.
		tp(server, px + 4.2, y + 1.3, pz + 4.2, 6.0F, 20.0F);
		context.waitTicks(20);
		quiet(context);
		context.takeScreenshot("forja_36_yunque");
		log("bloques: fotografiados");
	}

	private static void shotFoundry(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 68;
		int pz = z + 74;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d smooth_stone", px - 3, y - 1, pz - 3, px + 8, y - 1, pz + 7));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 3, y, pz - 3, px + 8, y + 5, pz + 7));

		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var blocks = dev.forja.registry.ModBlocks.class;

			// Back row: the pot, two lengths of channel and the bank of tanks it fills.
			BlockPos potAt = new BlockPos(px, y, pz);
			level.setBlockAndUpdate(potAt, dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
			if (level.getBlockEntity(potAt) instanceof dev.forja.block.entity.CrucibleBlockEntity pot) {
				pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL,
					new ItemStack(dev.forja.registry.ModItems.ASCUA, 8));
				pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST,
					new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
				pot.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND,
					new ItemStack(net.minecraft.world.item.Items.COAL, 2));
			}
			for (int dx = 1; dx <= 2; dx++) {
				level.setBlockAndUpdate(new BlockPos(px + dx, y, pz),
					dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			}
			// Two tanks wide and two high, filled to different levels so the bank reads as a bank.
			for (int dx = 3; dx <= 4; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos at = new BlockPos(px + dx, y + dy, pz);
					level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CUBA_DE_COLADA.defaultBlockState());
					if (level.getBlockEntity(at) instanceof dev.forja.block.entity.MeltTankBlockEntity tank) {
						tank.fill(net.minecraft.world.item.Items.IRON_INGOT,
							dy == 0 ? dev.forja.block.entity.MeltTankBlockEntity.CAPACITY : 90);
					}
				}
			}

			// Down each end to the working row, and the line of channel along it.
			level.setBlockAndUpdate(new BlockPos(px, y, pz + 1),
				dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(px + 4, y, pz + 1),
				dev.forja.registry.ModBlocks.CONDUCTO_DE_ACERO.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(px, y, pz + 2),
				dev.forja.registry.ModBlocks.CAJA_DE_MOLDEO.defaultBlockState());
			for (int dx = 1; dx <= 4; dx++) {
				// The line mixes grades on purpose: the stone is shared, so it should still read as one run.
				level.setBlockAndUpdate(new BlockPos(px + dx, y, pz + 2), (dx == 4
					? dev.forja.registry.ModBlocks.CONDUCTO_DE_ACERO
					: dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA).defaultBlockState());
			}

			// A gantry over the left-hand table: a spur of channel a few blocks up, ending in a spout
			// that pours down onto it. This is what the falling stream is for.
			for (int dz = 0; dz <= 2; dz++) {
				level.setBlockAndUpdate(new BlockPos(px + 1, y + 3, pz + dz),
					dev.forja.registry.ModBlocks.CONDUCTO_DE_COLADA.defaultBlockState());
			}
			level.setBlockAndUpdate(new BlockPos(px + 1, y + 3, pz + 3),
				dev.forja.registry.ModBlocks.CANO_DE_COLADA.defaultBlockState());

			// And the two tables hanging off the line, with a lantern each to keep them hot.
			level.setBlockAndUpdate(new BlockPos(px + 1, y, pz + 3), dev.forja.registry.ModBlocks.MESA_DE_LOSA.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(px + 3, y, pz + 3), dev.forja.registry.ModBlocks.MESA_DE_BRASA.defaultBlockState());
			for (int dx : new int[] {0, 2, 4}) {
				level.setBlockAndUpdate(new BlockPos(px + dx, y, pz + 3),
					dev.forja.registry.ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
			}
			// Warm the tables before the frames go in, or their first pour comes out rough.
			for (int dx : new int[] {1, 3}) {
				BlockPos at = new BlockPos(px + dx, y, pz + 3);
				if (level.getBlockEntity(at) instanceof dev.forja.block.entity.CastingTableBlockEntity table) {
					for (int tick = 0; tick < dev.forja.block.entity.CastingTableBlockEntity.EVERY * 8; tick++) {
						dev.forja.block.entity.CastingTableBlockEntity.serverTick(level, at, level.getBlockState(at), table);
					}
				}
			}
		});

		server.runCommand("time set noon");
		server.runCommand("weather clear 1000000");
		server.runCommand("gamemode spectator @a");
		tp(server, px + 2.5, y + 4.2, pz - 5.0, 0.0F, 34.0F);
		context.waitTicks(60);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		// The frames last, so the tables are caught mid-pour rather than already finished.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var made = List.of(ForgeType.PICO, ForgeType.ESPADA);
			int[] xs = {1, 3};
			for (int i = 0; i < xs.length; i++) {
				if (level.getBlockEntity(new BlockPos(px + xs[i], y, pz + 3))
					instanceof dev.forja.block.entity.CastingTableBlockEntity table) {
					table.setItem(dev.forja.block.entity.CastingTableBlockEntity.SLOT_FRAME,
						dev.forja.item.CastingFrameItem.of(made.get(i)));
				}
			}
		});
		context.waitTicks(20);
		// Does the client agree there is a spout up there with somewhere to pour? An invisible stream
		// is either a sync problem or a drawing problem, and this says which before the picture.
		int[] over = context.computeOnClient(mc -> {
			BlockPos spoutAt = new BlockPos(px + 1, y + 3, pz + 3);
			if (mc.level == null) {
				return new int[] {-1, -1};
			}
			boolean isSpout = mc.level.getBlockState(spoutAt).getBlock() instanceof dev.forja.block.MeltSpoutBlock;
			BlockPos lands = dev.forja.block.MeltPipeBlock.landing(mc.level, spoutAt);
			return new int[] {isSpout ? 1 : 0, lands == null ? -1 : spoutAt.getY() - lands.getY()};
		});
		log("caño en el cliente: es un caño " + (over[0] > 0) + ", cae " + over[1] + " bloques");
		check(over[0] > 0, "the client should see the spout on the gantry");
		check(over[1] > 0, "and know how far its metal falls, got " + over[1]);
		context.takeScreenshot("forja_34_fundicion");
		// And one from down at floor level, which is where the channel is meant to be read from.
		tp(server, px + 2.5, y + 1.6, pz - 3.0, 0.0F, 18.0F);
		context.waitTicks(10);
		context.takeScreenshot("forja_35_canal");
		// And the spout side on, because a stream falling three blocks is the one thing in the foundry
		// you cannot judge from above.
		tp(server, px + 5.5, y + 2.4, pz + 3.5, 90.0F, 8.0F);
		context.waitTicks(10);
		context.takeScreenshot("forja_36_cano");
		server.runCommand("gamemode survival @a");
	}

	/** What sun steel and moon steel are worth at one time of day, measured under open sky. */
	private static float[] weaponBonusNow(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int px, int y, int pz, String when) {
		server.runCommand("time set " + when);
		// The sky brightness these two traits read is cached and only refreshed on the level tick, so
		// setting the clock is not the same as the world having noticed.
		context.waitTicks(5);
		return server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var registries = level.registryAccess();
			var player = connection.getServerPlayer();
			player.snapTo(px + 0.5, y, pz + 4.5, 0.0F, 0.0F);
			ItemStack sun = Assembler.create(ForgeType.ESPADA,
				List.of(dev.forja.material.ForgeMaterial.SOLACERO, dev.forja.material.ForgeMaterial.SOLACERO, dev.forja.material.ForgeMaterial.SOLACERO), registries);
			ItemStack moon = Assembler.create(ForgeType.ESPADA,
				List.of(dev.forja.material.ForgeMaterial.LUNACERO, dev.forja.material.ForgeMaterial.LUNACERO, dev.forja.material.ForgeMaterial.LUNACERO), registries);
			var victim = zombie(level, px + 4.5, y, pz + 4.5);
			float sunlit = dev.forja.upgrade.TraitEffects.weaponBonus(level, player, victim, sun);
			float moonlit = dev.forja.upgrade.TraitEffects.weaponBonus(level, player, victim, moon);
			victim.discard();
			return new float[] {sunlit, moonlit};
		});
	}

	/** Every modifier one stack puts on an attribute, added up: what wearing it is actually worth. */
	private static float modifierTotal(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		float total = 0.0F;
		var modifiers = stack.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers == null) {
			return 0.0F;
		}
		for (var entry : modifiers.modifiers()) {
			if (entry.attribute().equals(attribute)) {
				total += (float) entry.modifier().amount();
			}
		}
		return total;
	}

	/** A smith standing still with his feet on the floor, which is all these checks need of him. */
	private static dev.forja.entity.FallenSmith spawnSmith(ServerLevel level, int px, int y, int pz) {
		dev.forja.entity.FallenSmith smith = dev.forja.registry.ModEntities.HERRERO_CAIDO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		check(smith != null, "the boss should be creatable");
		smith.snapTo(px, y, pz, 0.0F, 0.0F);
		smith.setNoAi(true);
		level.addFreshEntity(smith);
		return smith;
	}

	private static dev.forja.entity.HollowArmor hollow(ServerLevel level, double px, int y, double pz) {
		dev.forja.entity.HollowArmor armor = dev.forja.registry.ModEntities.CORAZA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		check(armor != null, "the hollow plate should be creatable");
		armor.snapTo(px, y, pz, 0.0F, 0.0F);
		armor.setNoAi(true);
		level.addFreshEntity(armor);
		return armor;
	}

	/** Something to hit that is not the player, and that will not walk out of the test. */
	private static net.minecraft.world.entity.monster.zombie.Zombie zombie(ServerLevel level, double px, int y, double pz) {
		var victim = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		check(victim != null, "a zombie should be creatable");
		victim.snapTo(px, y, pz, 0.0F, 0.0F);
		victim.setNoAi(true);
		victim.setPersistenceRequired();
		level.addFreshEntity(victim);
		return victim;
	}

	private static void checkMobPortraits(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 24;
		int pz = z + 24;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d grass_block", px - 6, y - 1, pz - 8, px + 6, y - 1, pz + 6));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 6, y, pz - 8, px + 6, y + 5, pz + 6));
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("gamemode spectator @a");
		// A monster cannot even be created on peaceful, and two of these portraits are monsters.
		server.runCommand("difficulty easy");

		portrait(context, server, connection, "herrero_caido", px, y, pz, 7.5, 2.6, level -> {
			var boss = dev.forja.registry.ModEntities.HERRERO_CAIDO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(boss != null, "the boss should be creatable");
			return boss;
		});
		// And the same smith with the forge running violet, which is what the last quarter looks like.
		portrait(context, server, connection, "herrero_caido_furia", px, y, pz, 7.5, 2.6, level -> {
			var boss = dev.forja.registry.ModEntities.HERRERO_CAIDO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(boss != null, "the boss should be creatable");
			// Light the forge rather than wounding him: dropping his health that low would also bring the
			// apprentices, and they would be standing in front of the camera.
			boss.lightForge(400);
			return boss;
		});
		portrait(context, server, connection, "automata_de_forja", px, y, pz, 6.0, 1.9, level -> {
			var automaton = dev.forja.registry.ModEntities.AUTOMATA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(automaton != null, "the automaton should be creatable");
			return automaton;
		});
		portrait(context, server, connection, "coraza_vacia", px, y, pz, 4.6, 1.6, level -> {
			var hollow = dev.forja.registry.ModEntities.CORAZA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(hollow != null, "the hollow plate should be creatable");
			return hollow;
		});
		portrait(context, server, connection, "pavesa", px, y, pz, 2.4, 1.1, level -> {
			var wisp = dev.forja.registry.ModEntities.PAVESA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(wisp != null, "the wisp should be creatable");
			return wisp;
		});
		portrait(context, server, connection, "elite", px, y, pz, 3.5, 1.4, level -> {
			var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(zombie != null, "a zombie should be creatable");
			dev.forja.world.Elites.makeElite(zombie, level.getRandom());
			return zombie;
		});
		portrait(context, server, connection, "capitan_saqueador", px, y, pz, 3.5, 1.4, level -> {
			var vindicator = net.minecraft.world.entity.EntityTypes.VINDICATOR.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(vindicator != null, "a vindicator should be creatable");
			vindicator.addTag("forja_campamento");
			vindicator.addTag("forja_campamento_capitan");
			return vindicator;
		});

		server.runCommand("gamemode survival @a");
		server.runCommand("difficulty peaceful");
	}

	/** Spawns one mob facing the camera, waits for it to reach the client, and takes its picture. */
	private static void portrait(
		ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String name,
		int px, int y, int pz, double distance, double height,
		java.util.function.Function<ServerLevel, net.minecraft.world.entity.Mob> maker
	) {
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			net.minecraft.world.entity.Mob mob = maker.apply(level);
			mob.snapTo(px + 0.5, y, pz + 0.5, 180.0F, 0.0F);
			mob.setYHeadRot(180.0F);
			mob.setNoAi(true);
			mob.setPersistenceRequired();
			level.addFreshEntity(mob);
		});
		tp(server, px + 0.5, y + height - 1.62, pz + 0.5 - distance, 0.0F, 0.0F);
		context.waitTicks(20);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(3);
		context.takeScreenshot("forja_30_mob_" + name);
		server.runOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			net.minecraft.world.entity.Mob.class,
			new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(8.0)
		).forEach(net.minecraft.world.entity.Entity::discard));
		context.waitTicks(3);
	}

	private static void checkPartsCabinet(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		BlockPos cabinet = new BlockPos(x + 4, y, z + 2);
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:armario_de_piezas", cabinet.getX(), cabinet.getY(), cabinet.getZ()));
		tp(server, x + 4.5, y, z - 0.5, 0.0F, 20.0F);
		context.waitTicks(5);

		int[] held = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var entity = level.getBlockEntity(cabinet);
			check(entity instanceof dev.forja.block.entity.PartsCabinetBlockEntity, "the cabinet should have its block entity");
			var drawer = (dev.forja.block.entity.PartsCabinetBlockEntity) entity;
			boolean takesPart = drawer.canPlaceItem(0, Assembler.createPart(PartType.CABEZA_PICO, HIERRO));
			boolean takesOrb = drawer.canPlaceItem(0, dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 40));
			boolean takesGear = drawer.canPlaceItem(0, Assembler.create(ForgeType.ESPADA,
				List.of(HIERRO, MADERA, HIERRO), level.registryAccess()));
			boolean takesAlloy = drawer.canPlaceItem(0, new ItemStack(dev.forja.registry.ModItems.alloy("acero")));
			boolean takesRubbish = drawer.canPlaceItem(0, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
			drawer.setItem(0, Assembler.createPart(PartType.MANGO, MADERA));
			player.openMenu(drawer);
			return new int[] {drawer.getContainerSize(), takesPart ? 1 : 0, takesOrb ? 1 : 0, takesGear ? 1 : 0,
				takesAlloy ? 1 : 0, takesRubbish ? 1 : 0};
		});
		context.waitTicks(10);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		context.takeScreenshot("forja_28_armario");
		// By hand as well as by hopper. It opened as a vanilla chest, whose slots take anything, so the
		// rule above held for a hopper and for nobody standing in front of it.
		int[] byHand = server.computeOnServer(s -> {
			var menu = connection.getServerPlayer().containerMenu;
			check(menu instanceof dev.forja.menu.CabinetMenu, "the cabinet should open its own menu, got " + menu.getClass().getSimpleName());
			boolean rubbish = menu.getSlot(1).mayPlace(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
			boolean part = menu.getSlot(1).mayPlace(Assembler.createPart(PartType.HOJA, HIERRO));
			// And shift-clicked: cobblestone in the first inventory slot must stay where it is.
			int first = dev.forja.block.entity.PartsCabinetBlockEntity.SIZE;
			menu.getSlot(first).set(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 3));
			menu.quickMoveStack(connection.getServerPlayer(), first);
			boolean stayed = menu.getSlot(first).getItem().is(net.minecraft.world.item.Items.COBBLESTONE);
			menu.getSlot(first).set(Assembler.createPart(PartType.GUARDA, HIERRO));
			menu.quickMoveStack(connection.getServerPlayer(), first);
			boolean moved = menu.getSlot(first).getItem().isEmpty();
			return new int[] {rubbish ? 1 : 0, part ? 1 : 0, stayed ? 1 : 0, moved ? 1 : 0};
		});
		log("armario, a mano: adoquín en una casilla " + (byHand[0] == 1) + ", pieza " + (byHand[1] == 1)
			+ "; con mayús el adoquín se queda " + (byHand[2] == 1) + " y la pieza entra " + (byHand[3] == 1));
		check(byHand[0] == 0 && byHand[1] == 1, "the cabinet's slots should refuse cobblestone and take a part");
		check(byHand[2] == 1 && byHand[3] == 1, "and a shift-click should move a part in and leave cobblestone where it was");
		String cabinetOff = context.computeOnClient(mc -> slotsOffTheirSquares(mc, "textures/gui/armario.png"));
		check(cabinetOff.equals("[]"), "every cabinet slot should sit on a square of its panel, off: " + cabinetOff);
		log("armario: " + held[0] + " huecos, acepta pieza " + (held[1] == 1) + ", orbe " + (held[2] == 1)
			+ ", objeto forjado " + (held[3] == 1) + ", lingote del mod " + (held[4] == 1)
			+ ", adoquín " + (held[5] == 1));
		check(held[0] == dev.forja.block.entity.PartsCabinetBlockEntity.SIZE, "the cabinet should hold 27 things");
		check(held[1] == 1 && held[2] == 1 && held[3] == 1 && held[4] == 1, "and take anything a smith made");
		check(held[5] == 0, "but not the cobblestone in your pocket");

		int dropped = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			connection.getServerPlayer().closeContainer();
			level.destroyBlock(cabinet, true);
			return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(cabinet).inflate(4.0)).size();
		});
		log("armario: al romperlo caen " + dropped + " montones");
		check(dropped >= 2, "breaking it should drop the cabinet and what was inside, got " + dropped);
		server.runOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(cabinet).inflate(6.0)
		).forEach(net.minecraft.world.entity.Entity::discard));
	}

	private static void checkBalanceLimits(TestServerContext server, TestServerConnection connection) {
		String report = server.computeOnServer(s -> {
			var registries = connection.getServerLevel().registryAccess();
			StringBuilder out = new StringBuilder();
			double worst = 99.0;
			double best = 0.0;
			String worstName = "";
			String bestName = "";
			for (ForgeType type : ForgeType.values()) {
				if (type.kind != ForgeType.Kind.WEAPON) {
					continue;
				}
				List<dev.forja.material.ForgeMaterial> materials = new ArrayList<>();
				for (PartType slot : type.slots) {
					materials.add(slot.role == PartType.Role.HANDLE ? MADERA : HIERRO);
				}
				// The same arithmetic the balance table in the log uses: the player's base 1 damage and
				// base attack speed of 4, so the two numbers can be compared.
				ForgeStats.Sheet sheet = ForgeStats.sheet(type, materials, Upgrades.EMPTY);
				double dps = (1.0F + sheet.attackDamage) * Math.max(0.2F, 4.0F + sheet.attackSpeed);
				if (dps < worst) {
					worst = dps;
					worstName = type.id();
				}
				if (dps > best) {
					best = dps;
					bestName = type.id();
				}
			}
			out.append(String.format(Locale.ROOT, "armas de hierro: el más flojo %s con %.1f, el más fuerte %s con %.1f",
				worstName, worst, bestName, best));

			// The best full set of armour anyone can wear, and the toughest tool head.
			int bestArmor = 0;
			String bestArmorName = "";
			for (dev.forja.material.ForgeMaterial material : dev.forja.material.ForgeMaterial.values()) {
				int armor = 0;
				for (ForgeType type : List.of(ForgeType.CASCO, ForgeType.PECHERA, ForgeType.GREBAS, ForgeType.BOTAS)) {
					armor += dev.forja.forge.ForgeStats.sheet(type, List.of(material, CUERO),
						dev.forja.upgrade.Upgrades.EMPTY, 0, null).armor;
				}
				if (armor > bestArmor) {
					bestArmor = armor;
					bestArmorName = material.getSerializedName();
				}
			}
			out.append(String.format(Locale.ROOT, ", conjunto más duro %s con %d de armadura", bestArmorName, bestArmor));
			return out.toString() + "|" + worst + "|" + best + "|" + bestArmor;
		});
		String[] parts = report.split("\\|");
		log("balance: " + parts[0]);
		double worst = Double.parseDouble(parts[1]);
		double best = Double.parseDouble(parts[2]);
		int bestArmor = Integer.parseInt(parts[3]);
		check(worst >= 3.0, "no weapon should fall under 3 damage a second in plain iron, got " + worst);
		check(best <= 14.0, "and none should pass 14, got " + best);
		check(bestArmor <= 24, "no full set should pass 24 armor before upgrades, got " + bestArmor);
	}

	private static void writeUpgradeDoc(TestServerContext server, TestServerConnection connection) {
		String markdown = server.computeOnServer(s -> {
			StringBuilder out = new StringBuilder();
			out.append("# Mejoras\n\n");
			out.append("Esta lista la escribe el propio test (`./gradlew runClientGameTest`), así que no se queda vieja.\n");
			out.append("Cada objeto suma el porcentaje indicado; las que dicen \"+\" necesitan los dos objetos juntos.\n\n");
			out.append("| Mejora | Va en | Se alimenta con | Al 100% |\n|---|---|---|---|\n");
			for (Upgrade upgrade : Upgrade.values()) {
				List<String> types = new ArrayList<>();
				for (ForgeType type : ForgeType.values()) {
					if (upgrade.appliesTo(type)) {
						types.add(Component.translatable("item.forja." + type.id()).getString());
					}
				}
				String where = types.size() == ForgeType.values().length ? "todo"
					: types.size() > 6 ? types.size() + " objetos" : String.join(", ", types);
				List<String> feeds = new ArrayList<>();
				for (Upgrade.Option option : upgrade.options) {
					List<String> items = new ArrayList<>();
					for (var requirement : option.requirements()) {
						items.add(requirement.displayStack().getHoverName().getString());
					}
					feeds.add(String.join(" + ", items) + " " + option.percent() + "%");
				}
				String food = feeds.isEmpty() ? "solo de un evento" : String.join(" / ", feeds);
				out.append("| ").append(upgrade.displayName().getString())
					.append(" | ").append(where)
					.append(" | ").append(food)
					.append(" | ").append(upgrade.effect(100).getString().replace("|", "/"))
					.append(" |\n");
			}
			out.append("\n## Sinergias\n\n");
			out.append("Dos mejoras al ").append(dev.forja.upgrade.Synergy.THRESHOLD).append("% en la misma pieza.\n\n");
			out.append("| Sinergia | Pareja | Qué hace |\n|---|---|---|\n");
			for (dev.forja.upgrade.Synergy synergy : dev.forja.upgrade.Synergy.values()) {
				out.append("| ").append(synergy.displayName().getString())
					.append(" | ").append(synergy.first.displayName().getString())
					.append(" + ").append(synergy.second.displayName().getString())
					.append(" | ").append(synergy.description().getString())
					.append(" |\n");
			}
			return out.toString();
		});
		String materials = server.computeOnServer(s -> {
			StringBuilder out = new StringBuilder();
			out.append("# Materiales\n\n");
			out.append("Esta lista la escribe el propio test, igual que la de mejoras.\n");
			out.append("El **rasgo** lo lleva cualquier pieza hecha de ese material; el **conjunto** es lo que dan las\n");
			out.append("cuatro placas de armadura del mismo material puestas a la vez.\n\n");
			out.append("| Material | Durabilidad | Minado | Daño | Rasgo | Conjunto |\n|---|---|---|---|---|---|\n");
			for (dev.forja.material.ForgeMaterial material : dev.forja.material.ForgeMaterial.values()) {
				// The set bonus already has a sentence written for it, the same one the tooltip shows.
				String written = Component.translatable("conjunto.forja." + material.getSerializedName()).getString();
				List<String> bonuses = dev.forja.upgrade.ArmorSets.bonuses(material).isEmpty()
					? List.of() : List.of(written);
				out.append("| ").append(material.displayName().getString())
					.append(" | ").append(material.durability)
					.append(" | ").append(String.format(Locale.ROOT, "%.1f", material.miningSpeed))
					.append(" | ").append(String.format(Locale.ROOT, "%+.1f", material.attackDamageBonus))
					.append(" | ").append(material.trait == dev.forja.material.ForgeMaterial.Trait.NONE
						? "-" : material.trait.displayName().getString())
					.append(" | ").append(bonuses.isEmpty() ? "-" : String.join(", ", bonuses))
					.append(" |\n");
			}
			return out.toString();
		});
		try {
			// The run directory is build/run/clientGameTest, so the project is three levels up.
			java.nio.file.Path root = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
				.getParent().getParent().getParent();
			java.nio.file.Path docs = root.resolve("docs");
			java.nio.file.Files.createDirectories(docs);
			java.nio.file.Files.writeString(docs.resolve("MEJORAS.md"), markdown);
			java.nio.file.Files.writeString(docs.resolve("MATERIALES.md"), materials);
			log("docs: escritos MEJORAS.md (" + Upgrade.values().length + " mejoras, "
				+ dev.forja.upgrade.Synergy.values().length + " sinergias) y MATERIALES.md ("
				+ dev.forja.material.ForgeMaterial.values().length + " materiales)");
		} catch (java.io.IOException failure) {
			check(false, "the tables should be writable: " + failure.getMessage());
		}
	}

	private static void checkNewCombatUpgrades(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		// Resaca and Tempano: the two upgrades the new events leave behind.
		// Whatever the tests before left behind: empty hands, no effects, and a smith that can be hurt.
		// On peaceful a mob's blow is worth nothing at all, so the difficulty goes up for this stretch.
		server.runCommand("gamemode survival @a");
		server.runCommand("difficulty easy");
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.stopUsingItem();
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.removeAllEffects();
			player.setInvulnerable(false);
			player.invulnerableTime = 0;
			player.setHealth(player.getMaxHealth());
		});
		context.waitTicks(5);

		double[] undertow = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			ItemStack trident = Assembler.create(ForgeType.TRIDENTE, List.of(HIERRO, MADERA, CUERO), registries);
			trident = UpgradeRecipes.upgraded(trident, ForgeType.TRIDENTE, Upgrade.RESACA, 100, registries);
			net.minecraft.world.entity.animal.pig.Pig pig = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, level);
			pig.snapTo(player.getX() + 3.0, player.getY(), player.getZ(), 0.0F, 0.0F);
			pig.setNoAi(true);
			level.addFreshEntity(pig);
			dev.forja.upgrade.CombatUpgrades.onWeaponHitForTest(level, player, pig, trident, 5.0F);
			double towards = pig.getDeltaMovement().x;
			pig.discard();
			return new double[] {towards};
		});
		log("resaca: el golpe mueve al enemigo " + undertow[0] + " en x (hacia el herrero si es negativo)");
		check(undertow[0] < -0.1, "Resaca should drag what it hits back towards the smith, got " + undertow[0]);

		boolean[] frost = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			ItemStack plate = Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO), registries);
			plate = UpgradeRecipes.upgraded(plate, ForgeType.PECHERA, Upgrade.TEMPANO, 100, registries);
			player.setItemSlot(EquipmentSlot.CHEST, plate);
			net.minecraft.world.entity.animal.pig.Pig pig = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, level);
			pig.snapTo(player.getX() + 1.0, player.getY(), player.getZ(), 0.0F, 0.0F);
			pig.setNoAi(true);
			level.addFreshEntity(pig);
			boolean bitten = false;
			// It is a chance, not a rule: swing enough times that the test is not a coin toss.
			for (int attempt = 0; attempt < 24 && !bitten; attempt++) {
				player.invulnerableTime = 0;
				player.setHealth(player.getMaxHealth());
				player.hurtServer(level, level.damageSources().mobAttack(pig), 2.0F);
				bitten = pig.hasEffect(MobEffects.SLOWNESS) || pig.getTicksFrozen() > 0;
			}
			pig.discard();
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.setHealth(player.getMaxHealth());
			return new boolean[] {bitten};
		});
		log("tempano: el que golpea acaba congelado " + frost[0]);
		check(frost[0], "Tempano should catch whoever swings at you up close");

		// The trident's own two: the water throws you, and the storm answers a hit.

		// A pool dug on purpose: stone walls so the water stays where it is put.
		int poolX = x + 8;
		int poolZ = z + 8;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", poolX - 2, y - 1, poolZ - 2, poolX + 2, y + 2, poolZ + 2));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", poolX - 1, y, poolZ - 1, poolX + 1, y + 2, poolZ + 1));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d water", poolX - 1, y, poolZ - 1, poolX + 1, y + 1, poolZ + 1));
		tp(server, poolX + 0.5, y, poolZ + 0.5, 0.0F, 0.0F);
		context.waitTicks(20);
		double[] thrown = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var registries = level.registryAccess();
			ItemStack trident = Assembler.create(ForgeType.TRIDENTE, List.of(HIERRO, MADERA, CUERO), registries);
			trident = UpgradeRecipes.upgraded(trident, ForgeType.TRIDENTE, Upgrade.CORRIENTE, 100, registries);
			player.setItemInHand(InteractionHand.MAIN_HAND, trident);
			player.setDeltaMovement(0.0, 0.0, 0.0);
			player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
			boolean wet = player.isInWaterOrRain();
			dev.forja.upgrade.WeaponThrow.tryRiptide(level, player, InteractionHand.MAIN_HAND, ForgeType.TRIDENTE);
			double pushed = player.getDeltaMovement().length();
			player.setDeltaMovement(0.0, 0.0, 0.0);
			return new double[] {pushed, wet ? 1.0 : 0.0};
		});
		log("corriente: dentro del agua " + (thrown[1] > 0) + ", te lanza con fuerza " + thrown[0]);
		check(thrown[1] > 0.0, "the smith should be standing in water for this");
		check(thrown[0] > 0.3, "Corriente should throw the smith, got " + thrown[0]);

		tp(server, x + 0.5, y, z - 6.5, 0.0F, 0.0F);
		context.waitTicks(10);
		boolean[] bolt = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			var weather = level.getWeatherData();
			weather.setRaining(true);
			weather.setRainTime(6000);
			weather.setThundering(true);
			weather.setThunderTime(6000);
			weather.setClearWeatherTime(0);
			// The saved data only says a storm is coming; this is the storm itself.
			level.setRainLevel(1.0F);
			level.setThunderLevel(1.0F);
			var registries = level.registryAccess();
			ItemStack trident = Assembler.create(ForgeType.TRIDENTE, List.of(HIERRO, MADERA, CUERO), registries);
			trident = UpgradeRecipes.upgraded(trident, ForgeType.TRIDENTE, Upgrade.CANALIZACION, 100, registries);
			net.minecraft.world.entity.animal.pig.Pig pig = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, level);
			pig.snapTo(player.getX() + 2.0, player.getY(), player.getZ(), 0.0F, 0.0F);
			pig.setNoAi(true);
			pig.setInvulnerable(true);
			level.addFreshEntity(pig);
			boolean struck = false;
			for (int attempt = 0; attempt < 24 && !struck; attempt++) {
				dev.forja.upgrade.CombatUpgrades.onWeaponHitForTest(level, player, pig, trident, 5.0F);
				struck = !level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class,
					player.getBoundingBox().inflate(8.0)).isEmpty();
			}
			pig.discard();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			var clear = level.getWeatherData();
			clear.setRaining(false);
			clear.setThundering(false);
			clear.setClearWeatherTime(6000);
			player.setRemainingFireTicks(0);
			return new boolean[] {struck};
		});
		log("canalizacion: la tormenta responde al golpe " + bolt[0]);
		check(bolt[0], "Canalizacion should call the lightning down on what you hit");
		server.runCommand("difficulty peaceful");

	}

	private static void checkTechniqueEffects(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		BlockPos table = new BlockPos(x + 1, y, z + 2);
		tp(server, x + 0.5, y, z + 0.5, 0.0F, 30.0F);
		context.waitTicks(5);
		openTable(context, server, connection, table);

		int[] repairs = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ForgeMenu menu = menu(connection);
			var registries = connection.getServerLevel().registryAccess();
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 0);
			ItemStack worn = Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, HIERRO), registries);
			worn.setDamageValue(worn.getMaxDamage() - 1);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(worn);
			// One ingot only, or the repair finishes either way and says nothing.
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 1));
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			int plain = menu.forgePreview().getDamageValue();
			// Ahorro de metal: the same ingot closes more of the damage.
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 1 << dev.forja.forge.Technique.AHORRO_DE_METAL.ordinal());
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			int thrifty = menu.forgePreview().getDamageValue();
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(ItemStack.EMPTY);
			return new int[] {plain, thrifty};
		});
		log("ahorro de metal: daño tras reparar " + repairs[0] + " -> " + repairs[1]);
		check(repairs[1] < repairs[0], "Ahorro de metal should mend further with the same ingots, got "
			+ repairs[1] + " against " + repairs[0]);

		int[] orbs = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ForgeMenu menu = menu(connection);
			var registries = connection.getServerLevel().registryAccess();
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 0);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries));
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 1));
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			int plain = menu.application() == null ? 0 : menu.application().after();
			// Mano de orfebre: five more points out of the same diamond.
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 1 << dev.forja.forge.Technique.MANO_DE_ORFEBRE.ordinal());
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			int rich = menu.application() == null ? 0 : menu.application().after();
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(ItemStack.EMPTY);
			return new int[] {plain, rich};
		});
		log("mano de orfebre: mejora " + orbs[0] + "% -> " + orbs[1] + "%");
		check(orbs[1] == orbs[0] + dev.forja.forge.Technique.ORB_BONUS,
			"Mano de orfebre should add its points, got " + orbs[1] + " against " + orbs[0]);

		int[] inherit = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ForgeMenu menu = menu(connection);
			var registries = connection.getServerLevel().registryAccess();
			ItemStack heir = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			ItemStack donor = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			// Sixty, so that both shares of it land under the fifty this bench stops at and the difference
			// between them is the technique's and not the ceiling's.
			donor = UpgradeRecipes.upgraded(donor, ForgeType.ESPADA, Upgrade.FILO, 60, registries);
			dev.forja.forge.Mastery.setLevel(donor, dev.forja.forge.Mastery.MAX_LEVEL, registries);
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 0);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(heir);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(donor);
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			int half = menu.forgePreview().getOrDefault(ModComponents.UPGRADES, dev.forja.upgrade.Upgrades.EMPTY).percent(Upgrade.FILO);
			// Herencia limpia: four fifths instead of half.
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 1 << dev.forja.forge.Technique.HERENCIA_LIMPIA.ordinal());
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			int clean = menu.forgePreview().getOrDefault(ModComponents.UPGRADES, dev.forja.upgrade.Upgrades.EMPTY).percent(Upgrade.FILO);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(ItemStack.EMPTY);
			return new int[] {half, clean};
		});
		log("herencia limpia: pasa " + inherit[0] + "% -> " + inherit[1] + "%");
		check(inherit[1] > inherit[0], "Herencia limpia should pass more of what the old piece knew, got "
			+ inherit[1] + " against " + inherit[0]);

		// The two that need the fire: a warm table melting what asks for a hot one, and one more ingot.
		server.runOnServer(s -> connection.getServerLevel().setBlockAndUpdate(
			new BlockPos(x + 1, y - 1, z + 2), net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState()));
		context.waitTicks(5);
		int[] heat = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ForgeMenu menu = menu(connection);
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 0);
			// Steel asks for a hot table (two iron and two coal); a campfire is only warm.
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(new ItemStack(net.minecraft.world.item.Items.COAL, 2));
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			int cold = menu.action() == ForgeMenu.Action.ALEACION ? 1 : 0;
			int coldCount = menu.forgePreview().getCount();
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 1 << dev.forja.forge.Technique.FUELLE_LARGO.ordinal());
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			int bellows = menu.action() == ForgeMenu.Action.ALEACION ? 1 : 0;
			int bellowsCount = menu.forgePreview().getCount();
			player.setAttached(dev.forja.forge.Techniques.LEARNED,
				1 << dev.forja.forge.Technique.FUELLE_LARGO.ordinal() | 1 << dev.forja.forge.Technique.OJO_PARA_EL_METAL.ordinal());
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			int eyeCount = menu.forgePreview().getCount();
			for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
				menu.getSlot(ForgeMenu.STAR_FIRST + i).set(ItemStack.EMPTY);
			}
			return new int[] {cold, bellows, bellowsCount, eyeCount};
		});
		log("fuelle largo: sobre fogata funde " + (heat[0] == 1) + " -> " + (heat[1] == 1)
			+ ", ojo para el metal " + heat[2] + " -> " + heat[3] + " lingotes");
		check(heat[0] == 0 && heat[1] == 1, "Fuelle largo should melt a step colder than the sheet says");
		check(heat[3] == heat[2] + dev.forja.forge.Technique.ALLOY_EXTRA,
			"Ojo para el metal should pour one more ingot, got " + heat[3] + " against " + heat[2]);

		// Segunda templada: a cold piece over a hot table goes back in the fire.
		server.runOnServer(s -> connection.getServerLevel().setBlockAndUpdate(
			new BlockPos(x + 1, y - 1, z + 2), net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState()));
		context.waitTicks(5);
		int[] reheat = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ForgeMenu menu = menu(connection);
			var registries = connection.getServerLevel().registryAccess();
			ItemStack cooled = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			cooled.set(ModComponents.TEMPLE, dev.forja.forge.Temple.AGUA.id());
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 0);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(cooled);
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			int without = menu.action() == ForgeMenu.Action.RECALENTAR ? 1 : 0;
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 1 << dev.forja.forge.Technique.SEGUNDA_TEMPLADA.ordinal());
			menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
			int with = menu.action() == ForgeMenu.Action.RECALENTAR ? 1 : 0;
			int hot = 0;
			if (with == 1) {
				menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE);
				ItemStack result = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
				hot = dev.forja.forge.Temple.hot(result, connection.getServerLevel().getGameTime())
					&& dev.forja.forge.Temple.of(result) == null ? 1 : 0;
			}
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 0);
			return new int[] {without, with, hot};
		});
		log("segunda templada: sin técnica " + (reheat[0] == 1) + ", con ella " + (reheat[1] == 1)
			+ ", vuelve a estar al rojo y sin temple " + (reheat[2] == 1));
		check(reheat[0] == 0 && reheat[1] == 1, "only a master should be able to put a piece back in the fire");
		check(reheat[2] == 1, "and the piece should come out hot again, with its old quench gone");

		// Alma de forja: a piece in ten is born with something nobody put on it. Forged in bulk, because
		// one roll in ten says nothing.
		int[] soul = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ForgeMenu menu = menu(connection);
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 1 << dev.forja.forge.Technique.ALMA_DE_FORJA.ordinal());
			int born = 0;
			int gifted = 0;
			for (int attempt = 0; attempt < 120; attempt++) {
				menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
				menu.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.HOJA, HIERRO));
				menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(Assembler.createPart(PartType.MANGO, MADERA));
				menu.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.GUARDA, HIERRO));
				menu.slotsChanged(menu.getSlot(ForgeMenu.STAR_FIRST).container);
				if (menu.action() != ForgeMenu.Action.FORGE || !menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE)) {
					continue;
				}
				born++;
				ItemStack made = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
				if (!made.getOrDefault(ModComponents.UPGRADES, dev.forja.upgrade.Upgrades.EMPTY).isEmpty()) {
					gifted++;
				}
			}
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 0);
			player.setAttached(dev.forja.forge.SmithLevel.EXPERIENCE, 0);
			player.setAttached(dev.forja.forge.SmithRecord.FORGED, 0);
			player.setAttached(dev.forja.forge.SmithRecord.PERFECT, 0);
			return new int[] {born, gifted};
		});
		log("alma de forja: de " + soul[0] + " piezas, " + soul[1] + " nacieron con mejora");
		check(soul[0] > 100, "the bulk forge should have made plenty of pieces, got " + soul[0]);
		check(soul[1] > 0, "and some of them should carry an upgrade nobody put there");
		check(soul[1] < soul[0] / 2, "but not most of them, got " + soul[1] + " of " + soul[0]);

		// Pacto de la prisa: a tool that bites faster through everything and wears out for it.
		float[] haste = server.computeOnServer(s -> {
			var registries = connection.getServerLevel().registryAccess();
			ItemStack plain = Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, HIERRO), registries);
			ItemStack hasty = UpgradeRecipes.upgraded(plain.copy(), ForgeType.PICO, Upgrade.PACTO_DE_LA_PRISA, 100, registries);
			float plainSpeed = plain.getDestroySpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			float hastySpeed = hasty.getDestroySpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			return new float[] {plainSpeed, hastySpeed, plain.getMaxDamage(), hasty.getMaxDamage()};
		});
		log("pacto de la prisa: minado " + haste[0] + " -> " + haste[1] + ", durabilidad " + haste[2] + " -> " + haste[3]);
		check(haste[1] > haste[0], "the haste pact should mine faster, got " + haste[1] + " against " + haste[0]);
		check(haste[3] < haste[2], "and wear out sooner, got " + haste[3] + " against " + haste[2]);

		// Placa hueca: the material the suits are made of, and what wearing it is worth.
		int[] hollowPlate = server.computeOnServer(s -> {
			var registries = connection.getServerLevel().registryAccess();
			ItemStack plate = new ItemStack(dev.forja.registry.ModItems.PLACA_HUECA);
			boolean reads = dev.forja.material.ForgeMaterial.fromInput(plate) == dev.forja.material.ForgeMaterial.HUECO;
			ItemStack chest = Assembler.create(ForgeType.PECHERA, List.of(dev.forja.material.ForgeMaterial.HUECO, CUERO), registries);
			boolean trait = chest.get(ModComponents.PARTS).hasTrait(dev.forja.material.ForgeMaterial.Trait.VACIO);
			int armor = Math.round(dev.forja.forge.ForgeStats.sheet(ForgeType.PECHERA,
				List.of(dev.forja.material.ForgeMaterial.HUECO, CUERO), dev.forja.upgrade.Upgrades.EMPTY, 0, null).armor);
			return new int[] {reads ? 1 : 0, trait ? 1 : 0, armor};
		});
		log("placa hueca: se lee como material " + (hollowPlate[0] == 1) + ", rasgo Vacío " + (hollowPlate[1] == 1)
			+ ", armadura de pechera " + hollowPlate[2]);
		check(hollowPlate[0] == 1, "a hollow plate should count as its own material");
		check(hollowPlate[1] == 1, "and carry the hollow trait");

		// The two newest talismans, and that every one of them has a stone to be made from.
		int[] charms = server.computeOnServer(s -> {
			int made = 0;
			for (dev.forja.item.Talisman talisman : dev.forja.item.Talisman.values()) {
				made += dev.forja.item.Talisman.of(talisman.create()) == talisman ? 1 : 0;
			}
			return new int[] {made, dev.forja.item.Talisman.values().length};
		});
		log("talismanes: " + charms[1] + ", todos se identifican " + (charms[0] == charms[1]));
		check(charms[0] == charms[1], "every talisman should read back as itself");
		check(charms[1] >= 7, "there should be seven talismans now, got " + charms[1]);

		// The three newest gifts: one for the bow, one for the shield, one for the wings.
		int[] gifts = server.computeOnServer(s -> {
			var registries = connection.getServerLevel().registryAccess();
			int sealed = 0;
			for (dev.forja.forge.Perk perk : List.of(dev.forja.forge.Perk.TIRADOR,
				dev.forja.forge.Perk.MURALLA, dev.forja.forge.Perk.AERONAUTA)) {
				sealed += dev.forja.item.SealItem.create(perk).has(ModComponents.SELLO) ? 1 : 0;
			}
			int bows = dev.forja.forge.Perk.forType(ForgeType.ARCO).size();
			int shields = dev.forja.forge.Perk.forType(ForgeType.ESCUDO).size();
			int wings = dev.forja.forge.Perk.forType(ForgeType.ALAS).size();
			return new int[] {sealed, bows, shields, wings, dev.forja.forge.Perk.values().length};
		});
		log("dones: " + gifts[4] + " en total, sellos nuevos " + gifts[0]
			+ ", para arcos " + gifts[1] + ", escudos " + gifts[2] + ", alas " + gifts[3]);
		check(gifts[0] == 3, "the three new gifts should each have their seal");
		check(gifts[1] >= 2 && gifts[2] >= 2 && gifts[3] >= 2,
			"bows, shields and wings should each have a choice of gift now");

		// El martillo del maestro: the one thing that takes a technique back.
		int[] hammer = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.setAttached(dev.forja.forge.Techniques.LEARNED,
				1 << dev.forja.forge.Technique.PULSO_FIRME.ordinal() | 1 << dev.forja.forge.Technique.MANO_DE_ORFEBRE.ordinal());
			ItemStack tool = new ItemStack(dev.forja.registry.ModItems.MARTILLO_DEL_MAESTRO);
			player.setItemInHand(InteractionHand.MAIN_HAND, tool);
			int before = dev.forja.forge.Techniques.mask(player);
			tool.getItem().use(connection.getServerLevel(), player, InteractionHand.MAIN_HAND);
			int after = dev.forja.forge.Techniques.mask(player);
			int left = player.getMainHandItem().getCount();
			// And with nothing to forget it should refuse rather than waste itself.
			ItemStack second = new ItemStack(dev.forja.registry.ModItems.MARTILLO_DEL_MAESTRO);
			player.setItemInHand(InteractionHand.MAIN_HAND, second);
			second.getItem().use(connection.getServerLevel(), player, InteractionHand.MAIN_HAND);
			int spare = player.getMainHandItem().getCount();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return new int[] {before, after, left, spare};
		});
		log("martillo del maestro: máscara " + hammer[0] + " -> " + hammer[1] + ", se gasta " + (hammer[2] == 0)
			+ ", con nada que olvidar se guarda " + (hammer[3] == 1));
		check(hammer[0] != 0 && hammer[1] == 0, "the hammer should forget every technique");
		check(hammer[2] == 0, "and be spent doing it");
		check(hammer[3] == 1, "but keep itself when there is nothing to forget");

		// Obra maestra: the star notices when a piece has had everything a smith can give it.
		float[] crowned = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ForgeMenu menu = menu(connection);
			var registries = connection.getServerLevel().registryAccess();
			ItemStack piece = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			dev.forja.forge.Quality.markPerfect(piece);
			dev.forja.forge.Quality.sign(piece, player);
			dev.forja.forge.Mastery.setLevel(piece, dev.forja.forge.Mastery.MAX_LEVEL, registries);
			double before = damageOf(piece);
			boolean early = dev.forja.forge.Masterpiece.crown(piece, player);
			// Without a gift it is only a very good sword.
			piece.set(ModComponents.DON, dev.forja.forge.Perk.FILO_ETERNO.id());
			boolean now = dev.forja.forge.Masterpiece.crown(piece, player);
			double after = damageOf(piece);
			boolean styled = piece.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE) != null;
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			return new float[] {early ? 1.0F : 0.0F, now ? 1.0F : 0.0F, (float) before, (float) after, styled ? 1.0F : 0.0F};
		});
		log("obra maestra: sin don " + (crowned[0] > 0) + ", con don " + (crowned[1] > 0)
			+ ", daño " + crowned[2] + " -> " + crowned[3] + ", con marco " + (crowned[4] > 0));
		check(crowned[0] == 0.0F, "a piece without a gift is not a masterpiece yet");
		check(crowned[1] == 1.0F, "with everything on it, the star should crown it");
		check(crowned[3] > crowned[2], "and a masterpiece should be worth more, got " + crowned[3] + " against " + crowned[2]);
		check(crowned[4] == 1.0F, "and wear its own frame");

		// Firma del maestro: your own work answers to you twice as well.
		float[] signature = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			ItemStack mine = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			dev.forja.forge.Quality.sign(mine, player);
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 0);
			float plain = dev.forja.forge.Quality.affinity(mine, player);
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 1 << dev.forja.forge.Technique.FIRMA_DEL_MAESTRO.ordinal());
			float master = dev.forja.forge.Quality.affinity(mine, player);
			player.setAttached(dev.forja.forge.Techniques.LEARNED, 0);
			return new float[] {plain, master};
		});
		log("firma del maestro: afinidad " + signature[0] + " -> " + signature[1]);
		check(signature[1] > signature[0], "Firma del maestro should double the edge on your own work");

		server.runOnServer(s -> {
			connection.getServerLevel().setBlockAndUpdate(new BlockPos(x + 1, y - 1, z + 2),
				net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			connection.getServerPlayer().closeContainer();
		});
		context.waitTicks(5);
	}

	private static void checkRaiderCamp(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		// The smith's barrow: a room underground with the coffin, the anvil and two suits standing guard.
		int barrowX = x - 10;
		int barrowZ = z - 10;
		server.runCommand("difficulty easy");
		server.runCommand(String.format(Locale.ROOT, "place template forja:tumulo_del_herrero/tumulo %d %d %d", barrowX, y, barrowZ));
		int[] barrow = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int chests = 0;
			int anvils = 0;
			int lanterns = 0;
			for (BlockPos pos : BlockPos.betweenClosed(barrowX - 1, y - 1, barrowZ - 1, barrowX + 13, y + 7, barrowZ + 13)) {
				var state = level.getBlockState(pos);
				chests += state.is(net.minecraft.world.level.block.Blocks.CHEST) || state.is(net.minecraft.world.level.block.Blocks.BARREL) ? 1 : 0;
				anvils += state.is(dev.forja.registry.ModBlocks.YUNQUE_DEL_HERRERO) ? 1 : 0;
				lanterns += state.is(net.minecraft.world.level.block.Blocks.SOUL_LANTERN) ? 1 : 0;
			}
			int guards = level.getEntitiesOfClass(dev.forja.entity.HollowArmor.class,
				new net.minecraft.world.phys.AABB(new BlockPos(barrowX, y, barrowZ)).inflate(16)).size();
			return new int[] {chests, anvils, lanterns, guards};
		});
		log("tumulo del herrero: cofres " + barrow[0] + ", yunque " + barrow[1] + ", faroles " + barrow[2]
			+ ", corazas de guardia " + barrow[3]);
		check(barrow[0] == 2, "the barrow should hold its chest and barrel, got " + barrow[0]);
		check(barrow[1] == 1, "and the smith's own anvil, got " + barrow[1]);
		check(barrow[3] == 2, "with two suits of plate standing over it, got " + barrow[3]);
		server.runOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			dev.forja.entity.HollowArmor.class, new net.minecraft.world.phys.AABB(new BlockPos(barrowX, y, barrowZ)).inflate(32)
		).forEach(net.minecraft.world.entity.Entity::discard));

		// The castillo de forja: the keep, its three seals and what it is guarding.
		int castleX = x - 10;
		int castleZ = z - 30;
		server.runCommand("difficulty easy");
		server.runCommand(String.format(Locale.ROOT, "place template forja:castillo_de_forja/castillo %d %d %d", castleX, y, castleZ));
		int[] castle = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int seals = 0;
			int chests = 0;
			int anvils = 0;
			int gilded = 0;
			for (BlockPos pos : BlockPos.betweenClosed(castleX - 1, y - 1, castleZ - 1, castleX + 21, y + 14, castleZ + 21)) {
				var state = level.getBlockState(pos);
				seals += state.is(dev.forja.registry.ModBlocks.FAROL_DE_PAVESA) ? 1 : 0;
				chests += state.is(net.minecraft.world.level.block.Blocks.CHEST) ? 1 : 0;
				anvils += state.is(dev.forja.registry.ModBlocks.YUNQUE_DEL_HERRERO) ? 1 : 0;
				gilded += state.is(net.minecraft.world.level.block.Blocks.GILDED_BLACKSTONE) ? 1 : 0;
			}
			var around = new net.minecraft.world.phys.AABB(new BlockPos(castleX + 10, y, castleZ + 10)).inflate(20);
			int guardians = level.getEntitiesOfClass(dev.forja.entity.CuneGuardian.class, around).size();
			int hallSeals = 0;
			for (var die : level.getEntitiesOfClass(dev.forja.entity.CuneGuardian.class, around)) {
				hallSeals = die.findSeals(level).size();
			}
			return new int[] {seals, chests, anvils, gilded, guardians, hallSeals};
		});
		log("castillo de forja: faroles " + castle[0] + ", cofre " + castle[1] + ", yunque " + castle[2]
			+ ", bloques dorados " + castle[3] + ", guardianes " + castle[4]
			+ ", sellos que ve el guardian " + castle[5]);
		check(castle[4] == 1, "the castle should stand up one guardian, got " + castle[4]);
		check(castle[1] == 1, "and hold one chest of templates, got " + castle[1]);
		check(castle[2] == 1, "and the anvil behind it, got " + castle[2]);
		// Seven lanterns in the castle, three of them in the hall: the four in the towers are out of
		// range on purpose, so a player cannot unseal it from the wall walk without going inside.
		check(castle[0] == 7, "the castle should light seven lanterns, got " + castle[0]);
		check(castle[5] == 3, "and the guardian should be sealed by exactly the three in its hall, sees " + castle[5]);
		// The keep from outside, and then the hall with the tethers running to its three seals. Both
		// need daylight and a camera that is not falling, which is what spectator is for.
		server.runCommand("time set noon");
		server.runCommand("gamemode spectator @a");
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		// From the north-east corner. Two earlier attempts failed for two different reasons: looking
		// north from forty blocks south of it put the raiders' camp in the way, and backing off to
		// thirty-four blocks put the castle outside what this world keeps rendered, so the shot came
		// back as plain sky. Close, high, and from the side nothing else is built on.
		context.runOnClient(mc -> mc.options.renderDistance().set(12));
		mobShot(context, server, castleX + 26.0, y + 16.0, castleZ - 6.0,
			castleX + 10.0, y + 3.0, castleZ + 10.0, 30, "forja_castillo_fuera");

		// Inside: from a corner of the hall, so no pillar is in the way, and with night vision because
		// the room has a roof on it and three lanterns is not a photographer's light.
		server.runCommand("effect give @a minecraft:night_vision 30 0 true");
		mobShot(context, server, castleX + 6.0, y + 3.4, castleZ + 13.4,
			castleX + 10.5, y + 2.0, castleZ + 9.5, 30, "forja_castillo_sala");
		server.runCommand("effect clear @a");
		server.runCommand("gamemode creative @a");

		server.runOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			net.minecraft.world.entity.Mob.class,
			new net.minecraft.world.phys.AABB(new BlockPos(castleX + 10, y, castleZ + 10)).inflate(40)
		).forEach(net.minecraft.world.entity.Entity::discard));

		// The raiders' camp, and the gear its band is handed when it loads in.
		int campX = x - 10;
		int campZ = z;
		// Its band is made of vanilla raiders, and on peaceful those are taken away again.
		server.runCommand("difficulty easy");
		server.runCommand(String.format(Locale.ROOT, "place template forja:campamento_saqueadores/campamento %d %d %d", campX, y, campZ));
		int[] camp = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int loot = 0;
			int bells = 0;
			for (BlockPos pos : BlockPos.betweenClosed(campX - 2, y - 2, campZ - 2, campX + 17, y + 10, campZ + 17)) {
				var state = level.getBlockState(pos);
				loot += state.is(net.minecraft.world.level.block.Blocks.CHEST) || state.is(net.minecraft.world.level.block.Blocks.BARREL) ? 1 : 0;
				bells += state.is(net.minecraft.world.level.block.Blocks.BELL) ? 1 : 0;
			}
			return new int[] {loot, bells};
		});
		int[] band = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var raiders = level.getEntitiesOfClass(
				net.minecraft.world.entity.raid.Raider.class,
				new net.minecraft.world.phys.AABB(new BlockPos(campX, y, campZ)).inflate(20)
			);
			int forged = 0;
			int captains = 0;
			int mastered = 0;
			for (var raider : raiders) {
				for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
					if (raider.getItemBySlot(slot).has(dev.forja.registry.ModComponents.PARTS)) {
						forged++;
						break;
					}
				}
				if (raider.entityTags().contains("forja_capitan")) {
					captains++;
					ItemStack held = raider.getMainHandItem();
					mastered += held.has(dev.forja.registry.ModComponents.LEYENDA)
						&& dev.forja.forge.Mastery.level(held) == dev.forja.forge.Mastery.MAX_LEVEL ? 1 : 0;
				}
			}
			return new int[] {raiders.size(), forged, captains, mastered};
		});
		log("campamento saqueador: cofres " + camp[0] + ", campanas " + camp[1] + ", banda " + band[0]
			+ ", con hierro forjado " + band[1] + ", capitanes " + band[2] + " (leyenda a maestria maxima " + band[3] + ")");
		check(camp[0] >= 2, "the camp should hold its plunder chest and barrel, got " + camp[0]);
		check(camp[1] == 1, "and the bell on the watchtower, got " + camp[1]);
		check(band[0] == 4, "the camp should come with its band of four, got " + band[0]);
		check(band[1] == band[0], "every raider in the camp should be handed forged gear, got " + band[1] + " of " + band[0]);
		check(band[2] == 1, "exactly one of them is the captain, got " + band[2]);
		check(band[3] == 1, "and he carries a legend at full maestria, got " + band[3]);

		// The snowy camp: the same plan built out of dark oak and snow.
		server.runCommand(String.format(Locale.ROOT, "place template forja:campamento_saqueadores/campamento_nevado %d %d %d", campX, y, campZ + 20));
		int[] snowy = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			int snow = 0;
			int logs = 0;
			for (BlockPos pos : BlockPos.betweenClosed(campX - 2, y - 2, campZ + 18, campX + 17, y + 10, campZ + 37)) {
				var state = level.getBlockState(pos);
				snow += state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK) ? 1 : 0;
				logs += state.is(net.minecraft.world.level.block.Blocks.DARK_OAK_LOG) ? 1 : 0;
			}
			return new int[] {snow, logs};
		});
		log("campamento nevado: bloques de nieve " + snowy[0] + ", troncos de roble oscuro " + snowy[1]);
		check(snowy[0] > 20 && snowy[1] > 20, "the snowy camp should be built of dark oak on snow, got "
			+ snowy[1] + " logs and " + snowy[0] + " snow");
		server.runOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			net.minecraft.world.entity.raid.Raider.class,
			new net.minecraft.world.phys.AABB(new BlockPos(campX, y, campZ + 20)).inflate(32)
		).forEach(net.minecraft.world.entity.Entity::discard));

		server.runOnServer(s -> connection.getServerLevel().getEntitiesOfClass(
			net.minecraft.world.entity.raid.Raider.class,
			new net.minecraft.world.phys.AABB(new BlockPos(campX, y, campZ)).inflate(64)
		).forEach(net.minecraft.world.entity.Entity::discard));
		server.runCommand("difficulty peaceful");
	}

	private static void checkDisassemble(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		BlockPos table = new BlockPos(x, y, z + 2);
		tp(server, x + 0.5, y, z + 0.5, 0.0F, 30.0F);
		context.waitTicks(5);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ServerLevel level = connection.getServerLevel();
			player.getInventory().clearContent();
			player.openMenu(level.getBlockState(table).getMenuProvider(level, table));
		});
		context.waitForScreen(ForgeScreen.class);
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_TAB + ForgeMenu.MODE_DISASSEMBLE);
			HolderLookup.Provider registries = connection.getServerLevel().registryAccess();
			ItemStack worn = Assembler.create(ForgeType.PICO, List.of(DIAMANTE, PIEDRA, ORO));
			worn = UpgradeRecipes.upgraded(worn, ForgeType.PICO, Upgrade.VETA, 80, registries);
			worn = UpgradeRecipes.upgraded(worn, ForgeType.PICO, Upgrade.FORTUNA, 50, registries);
			worn.setDamageValue(worn.getMaxDamage() * 9 / 10);
			menu.getSlot(ForgeMenu.DISASSEMBLE_SLOT).set(worn);
		});
		context.waitTicks(10);
		context.takeScreenshot("forja_07_desarmar");
		int[] salvaged = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			menu(connection).clickMenuButton(player, ForgeMenu.BUTTON_DISASSEMBLE);
			int count = 0;
			int orbPercent = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack item = player.getInventory().getItem(i);
				count += item.getItem() instanceof PartItem ? item.getCount() : 0;
				dev.forja.upgrade.UpgradeOrb orb = dev.forja.item.UpgradeOrbItem.orb(item);
				orbPercent += orb != null ? orb.percent() : 0;
			}
			return new int[] {count, orbPercent};
		});
		int parts = salvaged[0];
		log("salvaged upgrade orbs worth " + salvaged[1] + "%");
		check(salvaged[1] == 65, "Veta 80% and Fortuna 50% should come out as 40% and 25% orbs, got " + salvaged[1]);
		check(server.computeOnServer(s -> advancementDone(connection.getServerPlayer(), "orbe")), "salvaging an orb should grant forja/orbe");
		int recycled = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			ForgeMenu menu = menu(connection);
			menu.getSlot(ForgeMenu.DISASSEMBLE_SLOT).set(Assembler.createPart(PartType.CABEZA_PICO, DIAMANTE));
			menu.clickMenuButton(player, ForgeMenu.BUTTON_DISASSEMBLE);
			int diamonds = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				diamonds += player.getInventory().getItem(i).is(Items.DIAMOND) ? player.getInventory().getItem(i).getCount() : 0;
			}
			return diamonds;
		});
		log("recycled a diamond pickaxe head into " + recycled + " diamonds");
		check(recycled == Math.max(1, PartType.CABEZA_PICO.cost / 2), "a loose diamond head should recycle into half its diamonds, got " + recycled);
		log("disassembled a 90% worn pickaxe into " + parts + " parts");
		check(parts == 2, "a worn pickaxe should give back its handle and binding but lose the head, got " + parts);
		server.runOnServer(s -> connection.getServerPlayer().closeContainer());
		context.waitTicks(5);
	}

	private static void checkArmorAndIcons(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			HolderLookup.Provider registries = connection.getServerLevel().registryAccess();
			player.getInventory().clearContent();
			player.setItemSlot(EquipmentSlot.HEAD, Assembler.create(ForgeType.CASCO, List.of(OBSIDIANA, ORO)));
			ItemStack chest = Assembler.create(ForgeType.PECHERA, List.of(DIAMANTE, CUERO));
			// Forged armor takes vanilla armor trims.
			check(chest.is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR), "forged armor should be trimmable");
			chest.set(DataComponents.TRIM, new net.minecraft.world.item.equipment.trim.ArmorTrim(
				registries.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(net.minecraft.world.item.equipment.trim.TrimMaterials.GOLD),
				registries.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(net.minecraft.world.item.equipment.trim.TrimPatterns.SENTRY)
			));
			player.setItemSlot(EquipmentSlot.CHEST, chest);
			ItemStack leggings = UpgradeRecipes.apply(
				Assembler.create(ForgeType.GREBAS, List.of(AMATISTA, HIERRO)), List.of(new ItemStack(Items.REDSTONE_BLOCK, 12), ItemStack.EMPTY, ItemStack.EMPTY), registries
			).result();
			player.setItemSlot(EquipmentSlot.LEGS, leggings);
			player.setItemSlot(EquipmentSlot.FEET, Assembler.create(ForgeType.BOTAS, List.of(COBRE, CUERO)));
			List<ItemStack> hotbar = List.of(
				Assembler.create(ForgeType.ARCO, List.of(ESMERALDA, CUERO, ORO)),
				Assembler.create(ForgeType.PICO, List.of(DIAMANTE, PIEDRA, ORO)),
				Assembler.create(ForgeType.HACHA, List.of(COBRE, MADERA, HIERRO)),
				Assembler.create(ForgeType.ESCUDO, List.of(PURPUR, ORO, MADERA)),
				Assembler.create(ForgeType.LANZA, List.of(DIAMANTE, MADERA, CUERO)),
				Assembler.create(ForgeType.MARTILLO, List.of(OBSIDIANA, HIERRO, ORO)),
				Assembler.create(ForgeType.GUADANA, List.of(DIAMANTE, NETHERITA, ORO)),
				Assembler.create(ForgeType.MAZO, List.of(NETHERITA, HUESO, ORO)),
				Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, ORO))
			);
			for (int i = 0; i < hotbar.size(); i++) {
				player.getInventory().setItem(i, hotbar.get(i));
			}
			// The last sword is broken, to show the cracked icon.
			hotbar.getLast().setDamageValue(hotbar.getLast().getMaxDamage());
		});
		context.waitTicks(5);
		String comparison = context.computeOnClient(mc -> {
			ItemStack ironChest = Assembler.create(ForgeType.PECHERA, List.of(HIERRO, CUERO));
			StringBuilder text = new StringBuilder();
			for (Component line : ironChest.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.NORMAL)) {
				text.append(line.getString()).append(" | ");
			}
			return text.toString();
		});
		int[] busiest = context.computeOnClient(mc -> {
			ItemStack loaded = Assembler.create(ForgeType.PECHERA, List.of(DIAMANTE, dev.forja.material.ForgeMaterial.ESCAMA));
			var registries = mc.level.registryAccess();
			for (Upgrade upgrade : List.of(Upgrade.PROTECCION, Upgrade.IRROMPIBLE, Upgrade.PRESTEZA, Upgrade.VITALIDAD, Upgrade.MAGNETISMO)) {
				loaded = UpgradeRecipes.upgraded(loaded, ForgeType.PECHERA, upgrade, 100, registries);
			}
			dev.forja.forge.Mastery.setLevel(loaded, 10, registries);
			int lines = loaded.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.NORMAL).size();
			// The row of parts under the name is part of the tooltip's height too, now that it is drawn.
			int strip = loaded.getTooltipImage()
				.map(image -> image instanceof dev.forja.item.PartsStrip data ? new dev.forja.client.PartsStripTooltip(data).getHeight(mc.font) : 0)
				.orElse(0);
			return new int[] {lines, mc.getWindow().getGuiScaledHeight(), strip};
		});
		int tall = busiest[0] * 10 + 6 + busiest[2];
		log("busiest tooltip: " + busiest[0] + " lines + " + busiest[2] + " px of parts (" + tall + " px) against a " + busiest[1] + " px tall screen");
		check(busiest[2] > 0, "a forged chestplate's tooltip should draw its parts");
		check(tall < busiest[1], "the busiest tooltip must fit on screen");
		log("tooltip against the worn diamond chestplate: " + comparison);
		check(comparison.contains("Comparado con") && comparison.contains("-"), "an iron chestplate's tooltip should compare against the worn diamond one");
		double speed = server.computeOnServer(s -> connection.getServerPlayer().getAttributeValue(Attributes.MOVEMENT_SPEED));
		log("movement speed with Presteza 100% leggings: " + speed);
		check(speed > 0.11, "Presteza leggings should raise movement speed above 0.1, got " + speed);

		tp(server, x + 0.5, y, z - 3.5, 0.0F, 10.0F);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(30);
		context.takeScreenshot("forja_08_armadura_y_herramientas");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(10);
		context.takeScreenshot("forja_09_armadura_espalda");
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.setItemSlot(EquipmentSlot.CHEST, Assembler.create(ForgeType.ALAS, List.of(ESMERALDA, CUERO), connection.getServerLevel().registryAccess()));
			player.getInventory().setItem(0, Assembler.create(ForgeType.ALAS, List.of(ORO, CUERO), connection.getServerLevel().registryAccess()));
		});
		context.waitTicks(10);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(2);
		context.takeScreenshot("forja_26_alas");
		checkFallenSmith(context, server, connection, x, y, z);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	/** The Fallen Smith: his offering, his stages, and a look at the model GeckoLib draws for him. */
	private static void checkFallenSmith(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.getInventory().clearContent();
			check(dev.forja.block.DeadForgeBlock.OFFERING.size() == 4, "the offering should be four things");
			check(!dev.forja.block.DeadForgeBlock.hasOffering(player), "an empty bag is not an offering");
			for (dev.forja.block.DeadForgeBlock.Offering offering : dev.forja.block.DeadForgeBlock.OFFERING) {
				player.getInventory().add(new ItemStack(offering.item().get(), offering.count()));
			}
			check(dev.forja.block.DeadForgeBlock.hasOffering(player), "the whole offering should count");
		});
		int health = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.FallenSmith smith = dev.forja.entity.FallenSmith.summon(level, new net.minecraft.core.BlockPos(x + 4, y, z + 4));
			check(!smith.getMainHandItem().isEmpty() && smith.getMainHandItem().has(ModComponents.PARTS), "he should be carrying his own flail");
			check(smith.getItemBySlot(EquipmentSlot.CHEST).has(ModComponents.PARTS), "and wearing his own plate");
			check(!smith.isReforging(), "he does not start out reforging");
			check(dev.forja.material.ForgeMaterial.fromInput(new ItemStack(dev.forja.registry.ModItems.CORAZON_DE_FORJA)) == dev.forja.material.ForgeMaterial.CORAZON,
				"his heart should be a material");
			return Math.round(smith.getMaxHealth());
		});
		log("herrero caido: " + health + " de vida, ofrenda de " + dev.forja.block.DeadForgeBlock.OFFERING.size() + " cosas");
		check(health >= 300, "the boss should have the health of a boss, got " + health);
		// Out of his reach, looking straight at him, with nothing of ours in the way.
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		});
		tp(server, x + 4.5, y, z - 4.5, 0.0F, 0.0F);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(25);
		String renderer = context.computeOnClient(mc -> {
			for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof dev.forja.entity.FallenSmith) {
					return mc.getEntityRenderDispatcher().getRenderer(entity).getClass().getSimpleName();
				}
			}
			return "no llegó al cliente";
		});
		log("herrero caido: lo dibuja " + renderer);
		int automatonHealth = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.ForgeAutomaton automaton = dev.forja.registry.ModEntities.AUTOMATA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(automaton != null, "the automaton should be creatable");
			automaton.snapTo(x + 7.5, y, z + 4.5, 180.0F, 0.0F);
			level.addFreshEntity(automaton);
			return Math.round(automaton.getMaxHealth());
		});
		context.waitTicks(20);
		String automatonRenderer = context.computeOnClient(mc -> {
			for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof dev.forja.entity.ForgeAutomaton) {
					return mc.getEntityRenderDispatcher().getRenderer(entity).getClass().getSimpleName();
				}
			}
			return "no llegó al cliente";
		});
		int hollowHealth = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.HollowArmor hollow = dev.forja.registry.ModEntities.CORAZA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(hollow != null, "the hollow plate should be creatable");
			hollow.snapTo(x + 1.5, y, z + 4.5, 180.0F, 0.0F);
			hollow.setNoAi(true);
			level.addFreshEntity(hollow);
			return Math.round(hollow.getMaxHealth());
		});
		context.waitTicks(20);
		String hollowRenderer = context.computeOnClient(mc -> {
			for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof dev.forja.entity.HollowArmor) {
					return mc.getEntityRenderDispatcher().getRenderer(entity).getClass().getSimpleName();
				}
			}
			return "no llegó al cliente";
		});
		log("coraza vacia: " + hollowHealth + " de vida, la dibuja " + hollowRenderer);
		check(hollowRenderer.equals("HollowArmorRenderer"), "the hollow plate should be drawn by its own renderer, got " + hollowRenderer);
		log("automata: " + automatonHealth + " de vida, lo dibuja " + automatonRenderer);
		check(automatonRenderer.equals("ForgeAutomatonRenderer"), "the automaton should be drawn by its own renderer, got " + automatonRenderer);
		check(renderer.equals("FallenSmithRenderer"), "the Fallen Smith should be drawn by his own GeckoLib renderer, got " + renderer);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(5);
		context.takeScreenshot("forja_27_herrero_caido");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			net.minecraft.world.phys.AABB around = connection.getServerPlayer().getBoundingBox().inflate(64.0);
			level.getEntitiesOfClass(dev.forja.entity.FallenSmith.class, around).forEach(net.minecraft.world.entity.Entity::discard);
			level.getEntitiesOfClass(dev.forja.entity.ForgeAutomaton.class, around).forEach(net.minecraft.world.entity.Entity::discard);
			level.getEntitiesOfClass(dev.forja.entity.HollowArmor.class, around).forEach(net.minecraft.world.entity.Entity::discard);
			connection.getServerPlayer().getInventory().clearContent();
		});
	}

	/**
	 * The grappling hook: it has to pull, and it has to not come back as a second hook.
	 *
	 * <p>It did. The claw is a separate entity carrying a copy of the hook, and when it landed the
	 * entity handed that copy back "to the hand it left" — except a hook never leaves the hand, so the
	 * copy went straight into the inventory. One free hook per swing, and nothing in the game said so:
	 * the hook worked, the pull worked, and the count quietly climbed.
	 */
	/**
	 * How the flail, the gauntlets and the hook sit in the hand.
	 *
	 * <p>Nothing else in this file looks at that, and it shows: all three are held by the wrong end,
	 * because a sprite whose handle is not in the bottom-left corner does not fit the transform every
	 * handheld item in Minecraft shares. It cannot be judged from the icon — only from the hand.
	 */
	/**
	 * Runs a wound-up blow out to where it lands.
	 *
	 * <p>Tests that call an attack straight rather than waiting for the mob to choose it used to read
	 * the result on the next line, because the result was there on the next line. It is not any more:
	 * the blow is committed when the method returns and lands when the animation gets to it.
	 */
	private static void throwIt(net.minecraft.world.entity.Mob mob) {
		for (int tick = 0; tick < dev.forja.entity.FallenSmith.HOOK_WINDUP; tick++) {
			mob.tick();
		}
	}

	/**
	 * Films every heavy move a tick at a time.
	 *
	 * <p>A still cannot show a telegraph. The whole point of the wind-up is that it happens over time —
	 * the hammer goes up, it stays up, and then it comes down — and a screenshot of the middle of that
	 * is just a screenshot of a man holding a hammer. So this takes one shot per tick for the length of
	 * each move, which stitches back together into footage at exactly the speed the game runs it.
	 *
	 * <p>The camera is parked side-on rather than behind, because side-on is where an anticipation
	 * reads: a body leaning back and then snapping forward is invisible head-on.
	 */
	/**
	 * Where the camera stands for the footage: side-on, a little above the floor, aimed across the pair.
	 *
	 * <p>Worked out rather than nudged. Eleven blocks back through a 48-degree lens shows about nine
	 * blocks of height, so a three-block boss fills a third of the frame and his victim four blocks
	 * behind him still fits. Six blocks back — the first guess — showed five, and he came out with his
	 * head cut off. The yaw is a flat ninety because the two of them are lined up along Z: anything
	 * else and the shot is not square to the move it is filming.
	 */
	private static final double CAMERA_Y = 2.2;
	private static final double CAMERA_Z = 3.0;
	private static final float CAMERA_YAW = 90.0F;
	private static final float CAMERA_PITCH = 5.2F;
	/** How far a player's eyes are above their feet, which is what `tp` actually places. */
	private static final double EYE_HEIGHT = 1.62;

	private static double CAMERA_X;
	private static double CAMERA_ABS_Y;
	private static double CAMERA_ABS_Z;

	/**
	 * The night sky under each event.
	 *
	 * <p>Worth a shot each, because until now none of this reached the client at all: the event lived
	 * in two static fields on the server and the only thing a player could see of it was a line of
	 * chat and some particles thrown in the air. A blood moon and a blizzard looked identical.
	 *
	 * <p>Taken at midnight on purpose. The colour is strongest there and absent at noon — a tinted
	 * daytime sky reads as a broken shader, a tinted night sky reads as something happening — so
	 * midnight is both the interesting case and the one worth keeping an eye on.
	 */
	/**
	 * The forge, lit.
	 *
	 * <p>The block the mod is named after had no ambient anything: a crafting table that happened to be
	 * hot, sitting next to a crucible that smokes and a tank that runs. Taken at night because that is
	 * where embers read — at noon a spark is a grey dot.
	 */
	/**
	 * A meteorite on its way down.
	 *
	 * <p>It lands within twenty blocks of a player, wherever it likes, so the lens goes wide and the
	 * camera looks up: a narrow shot aimed at one spot would miss it nine times in ten. Thirty-four
	 * ticks of fall plus the crater fits in fifty frames.
	 */
	/**
	 * Portraits of the mobs built from the approved models.
	 *
	 * <p>Separate from the fight footage and much closer to them. That camera stands eleven blocks back
	 * to fit a boss and his victim in; a Herrumbre is a third of a block tall and at eleven blocks it
	 * is a smudge on the grass. This one walks up to whatever it is photographing.
	 */
	private static void shotNewMobs(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set noon");
		server.runCommand("difficulty easy");
		context.runOnClient(mc -> {
			mc.options.fov().set(60);
			mc.options.fovEffectScale().set(0.0);
		});

		double px = x + 70.0;
		double pz = z + 70.0;
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			// A clean floor: whatever else the tests have set on fire is not in this picture.
			for (int fx = -5; fx <= 5; fx++) {
				for (int fz = -5; fz <= 5; fz++) {
					level.setBlockAndUpdate(new BlockPos((int) px + fx, y - 1, (int) pz + fz),
						net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
					for (int fy = 0; fy <= 3; fy++) {
						level.setBlockAndUpdate(new BlockPos((int) px + fx, y + fy, (int) pz + fz),
							net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
					}
				}
			}
			for (int i = 0; i < 4; i++) {
				var swarm = dev.forja.registry.ModEntities.HERRUMBRE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
				check(swarm != null, "the rust flake should be creatable");
				swarm.snapTo(px - 1.2 + i * 0.8, y, pz - 0.6 + (i % 2) * 1.2, 150.0F + i * 20.0F, 0.0F);
				swarm.setNoAi(true);
				swarm.setPersistenceRequired();
				level.addFreshEntity(swarm);
			}
		});
		// Three blocks out and barely above them, looking at the floor they are standing on.
		mobShot(context, server, px, y + 0.9, pz + 3.0, px, y + 0.2, pz, 40, "forja_mob_herrumbre");
		mobShot(context, server, px + 2.4, y + 2.0, pz + 2.4, px, y + 0.2, pz, 20, "forja_mob_herrumbre_alto");
		clearStage(server, connection, px, y, pz);

		// The greater ember, next to an ordinary wisp so the size reads.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var ember = dev.forja.registry.ModEntities.ASCUA_MAYOR.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(ember != null, "the greater ember should be creatable");
			ember.snapTo(px - 0.8, y + 0.6, pz, 160.0F, 0.0F);
			ember.setNoAi(true);
			ember.setPersistenceRequired();
			level.addFreshEntity(ember);
			var wisp = dev.forja.registry.ModEntities.PAVESA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(wisp != null, "the wisp should be creatable");
			wisp.snapTo(px + 1.6, y + 0.4, pz + 0.4, 160.0F, 0.0F);
			wisp.setNoAi(true);
			wisp.setPersistenceRequired();
			level.addFreshEntity(wisp);
		});
		mobShot(context, server, px, y + 1.5, pz + 4.0, px, y + 1.0, pz, 40, "forja_mob_ascua_mayor");
		clearStage(server, connection, px, y, pz);

		// And the slag at all three sizes, which is one model and three scales.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (int size = dev.forja.entity.LivingSlag.SMALLEST; size <= dev.forja.entity.LivingSlag.BIG; size++) {
				var slag = dev.forja.registry.ModEntities.ESCORIA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
				check(slag != null, "the living slag should be creatable");
				slag.setSize(size);
				slag.snapTo(px - 2.0 + size * 1.6, y, pz, 165.0F, 0.0F);
				slag.setNoAi(true);
				slag.setPersistenceRequired();
				level.addFreshEntity(slag);
			}
		});
		mobShot(context, server, px + 0.4, y + 1.3, pz + 4.2, px + 0.4, y + 0.5, pz, 40, "forja_mob_escoria");
		clearStage(server, connection, px, y, pz);

		// The three constructs, side by side, so the sizes read against each other.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var anvilMob = dev.forja.registry.ModEntities.YUNQUE_ANDANTE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(anvilMob != null, "the walking anvil should be creatable");
			anvilMob.snapTo(px - 2.6, y, pz, 165.0F, 0.0F);
			anvilMob.setNoAi(true);
			anvilMob.setPersistenceRequired();
			level.addFreshEntity(anvilMob);
			var ram = dev.forja.registry.ModEntities.PERCUTOR.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(ram != null, "the striker should be creatable");
			ram.snapTo(px, y, pz, 175.0F, 0.0F);
			ram.setNoAi(true);
			ram.setPersistenceRequired();
			level.addFreshEntity(ram);
			var grip = dev.forja.registry.ModEntities.TENAZA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(grip != null, "the tongs should be creatable");
			grip.snapTo(px + 2.6, y, pz, 185.0F, 0.0F);
			grip.setNoAi(true);
			grip.setPersistenceRequired();
			level.addFreshEntity(grip);
		});
		mobShot(context, server, px, y + 2.0, pz + 7.6, px, y + 1.2, pz, 40, "forja_mob_constructos");
		clearStage(server, connection, px, y, pz);

		// The foundry pair, and the two that fight about what you are carrying.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var beast = dev.forja.registry.ModEntities.CARGADOR_DE_CARBON.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(beast != null, "the coal hauler should be creatable");
			beast.snapTo(px - 2.4, y, pz, 200.0F, 0.0F);
			beast.setNoAi(true);
			beast.setPersistenceRequired();
			level.addFreshEntity(beast);
			var smith = dev.forja.registry.ModEntities.TEMPLADOR.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(smith != null, "the quencher should be creatable");
			smith.snapTo(px + 2.6, y, pz, 130.0F, 0.0F);
			smith.setNoAi(true);
			smith.setPersistenceRequired();
			level.addFreshEntity(smith);
		});
		mobShot(context, server, px, y + 1.8, pz + 7.0, px, y + 1.0, pz, 40, "forja_mob_fundicion");
		clearStage(server, connection, px, y, pz);

		// The core, shown twice: empty, then fed until it is about to let go. Two shots on purpose —
		// the whole point of it is that it looks different when it is dangerous.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var stone = dev.forja.registry.ModEntities.NUCLEO_ESTELAR.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(stone != null, "the star core should be creatable");
			stone.snapTo(px, y + 1, pz, 180.0F, 0.0F);
			stone.setNoAi(true);
			stone.setPersistenceRequired();
			level.addFreshEntity(stone);
		});
		mobShot(context, server, px, y + 2.6, pz + 7.0, px, y + 1.6, pz, 40, "forja_mob_nucleo_vacio");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			for (var stone : level.getEntitiesOfClass(dev.forja.entity.StarCore.class,
				new net.minecraft.world.phys.AABB(new BlockPos((int) px, y, (int) pz)).inflate(10))) {
				// Filled straight to the top in one go: the picture is of a full core, not of
				// somebody hitting it eight times in one tick.
				stone.fillForTest();
			}
		});
		mobShot(context, server, px, y + 2.6, pz + 7.0, px, y + 1.6, pz, 30, "forja_mob_nucleo_lleno");
		clearStage(server, connection, px, y, pz);

		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var golem = dev.forja.registry.ModEntities.MOLDE_ROTO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(golem != null, "the broken mould should be creatable");
			golem.snapTo(px, y, pz, 180.0F, 0.0F);
			golem.setNoAi(true);
			golem.setPersistenceRequired();
			level.addFreshEntity(golem);
		});
		mobShot(context, server, px, y + 2.2, pz + 6.4, px, y + 1.2, pz, 40, "forja_mob_molde");
		clearStage(server, connection, px, y, pz);

		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var die = dev.forja.registry.ModEntities.GUARDIAN_DE_CUNO.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(die != null, "the cune guardian should be creatable");
			die.snapTo(px, y, pz, 180.0F, 0.0F);
			die.setNoAi(true);
			die.setPersistenceRequired();
			level.addFreshEntity(die);
			// One seal beside it, so the tether that teaches the whole fight is in the picture.
			level.setBlockAndUpdate(new BlockPos((int) px + 4, y + 1, (int) pz + 1),
				dev.forja.registry.ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		});
		mobShot(context, server, px + 1.0, y + 2.6, pz + 8.0, px, y + 1.6, pz, 40, "forja_mob_cuno");

		// The aura, on a player rather than on a mob: spectator hides the body, so this one is taken
		// in creative with the camera in front. A legendary star-iron blade is both marks at once.
		// The stage is cleared first — the guardian was still standing where the camera looks, and
		// the first attempt came back as a photograph of its back.
		clearStage(server, connection, px, y, pz);
		server.runCommand("gamemode creative @a");
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			ItemStack blade = Assembler.create(ForgeType.ESPADA,
				List.of(dev.forja.material.ForgeMaterial.ESTELAR, dev.forja.material.ForgeMaterial.MADERA,
					dev.forja.material.ForgeMaterial.HIERRO), registries);
			blade.set(dev.forja.registry.ModComponents.LEYENDA, "prueba");
			blade.set(dev.forja.registry.ModComponents.OBRA_MAESTRA, true);
			player.setItemSlot(EquipmentSlot.MAINHAND, blade);
			player.setItemSlot(EquipmentSlot.CHEST, Assembler.create(ForgeType.PECHERA,
				List.of(dev.forja.material.ForgeMaterial.ESCORIA, dev.forja.material.ForgeMaterial.CUERO), registries));
		});
		context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
		for (int tick = 0; tick < 90; tick++) {
			tp(server, px, y, pz, 180.0F, 0.0F);
			context.waitTicks(1);
		}
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.waitTicks(2);
		context.takeScreenshot(TestScreenshotOptions.of("forja_aura").disableCounterPrefix().withSize(1280, 720));
		context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		});
		server.runCommand("gamemode spectator @a");
		server.runOnServer(s -> connection.getServerLevel().removeBlock(
			new BlockPos((int) px + 4, y + 1, (int) pz + 1), false));
		clearStage(server, connection, px, y, pz);

		clearStage(server, connection, px, y, pz);
		context.runOnClient(mc -> mc.options.fov().set(70));
		server.runCommand("gamemode survival @a");
		server.runCommand("difficulty peaceful");
	}

	/**
	 * Holds a camera on a point and takes one picture of whatever is standing there.
	 *
	 * <p>Two things about aiming a camera in this game, both of which cost runs to find.
	 *
	 * <p>`tp ... facing` **moves the player and does not turn them**. The position takes and the
	 * rotation keeps whatever it was, so every shot aimed with it came out pointing wherever the
	 * previous shot had been pointing. So the angles are worked out here instead.
	 *
	 * <p>And `tp` places the **feet**, while the camera sits at the eyes, 1.62 above them. Working the
	 * pitch out from the number handed to `tp` aims the shot about ten blocks past whatever it was
	 * supposed to be looking at, which is why every framing attempt so far has put its subject along
	 * the bottom edge. `cy` here is where the **eye** goes; the feet are worked back from it.
	 */
	/** Empties the portrait floor between subjects. */
	private static void clearStage(TestServerContext server, TestServerConnection connection, double px, int y, double pz) {
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var box = new net.minecraft.world.phys.AABB(new BlockPos((int) px, y, (int) pz)).inflate(14);
			for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box)) {
				mob.discard();
			}
		});
	}

	private static void mobShot(ClientGameTestContext context, TestServerContext server,
		double cx, double cy, double cz, double tx, double ty, double tz, int settle, String name) {
		double flat = Math.sqrt((tx - cx) * (tx - cx) + (tz - cz) * (tz - cz));
		float yaw = (float) -Math.toDegrees(Math.atan2(tx - cx, tz - cz));
		float pitch = (float) Math.toDegrees(Math.atan2(cy - ty, Math.max(0.01, flat)));
		double feet = cy - EYE_HEIGHT;
		// Whatever the last portrait threw into the air is still there, and it outlives the mob that
		// threw it: two shots in a row came back with somebody else's sparks all over them.
		context.runOnClient(mc -> mc.particleEngine.clearParticles());
		for (int tick = 0; tick < settle; tick++) {
			tp(server, cx, feet, cz, yaw, pitch);
			context.waitTicks(1);
		}
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.waitTicks(2);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(1280, 720));
	}

	private static void filmMeteor(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set midnight");
		server.runOnServer(s -> dev.forja.world.WorldEvents.stop(connection.getServerLevel()));
		context.runOnClient(mc -> {
			mc.options.fov().set(100);
			mc.options.fovEffectScale().set(0.0);
			mc.gui.hud.getChat().clearMessages(false);
		});
		// Aimed at a spot twenty blocks north, and told to land exactly there.
		double camX = x + 90.5;
		double camZ = z + 92.0;
		double hitX = x + 90.5;
		double hitZ = z + 72.0;
		for (int tick = 0; tick < 20; tick++) {
			server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f",
				camX, y + 2.0, camZ, hitX, y + 12.0, hitZ));
			context.waitTicks(1);
		}
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.world.WorldEvents.start(level, dev.forja.world.WorldEvents.METEORITOS);
			dev.forja.world.WorldEvents.meteorForTest(level, new BlockPos((int) hitX, y, (int) hitZ));
		});
		for (int frame = 0; frame < 50; frame++) {
			server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f",
				camX, y + 2.0, camZ, hitX, y + 12.0, hitZ));
			context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
			context.takeScreenshot(TestScreenshotOptions.of(
				String.format(Locale.ROOT, "forja_film_12_meteorito_%03d", frame))
				.disableCounterPrefix().withSize(960, 540));
			context.waitTicks(1);
		}
		server.runOnServer(s -> dev.forja.world.WorldEvents.stop(connection.getServerLevel()));
		context.runOnClient(mc -> mc.options.fov().set(70));
		server.runCommand("gamemode survival @a");
		server.runCommand("time set day");
	}

	private static void shotForge(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set midnight");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			// On the same patch the mob footage uses, which is flat, open and known to frame well.
			int bx = x + 88;
			int bz = z + 82;
			net.minecraft.world.level.block.Block[] row = {
				dev.forja.registry.ModBlocks.MESA_DE_FORJA,
				dev.forja.registry.ModBlocks.MESA_DE_FORJA_MAYOR,
				dev.forja.registry.ModBlocks.YUNQUE_DEL_HERRERO,
				dev.forja.registry.ModBlocks.MESA_DE_PIEZAS,
			};
			for (int fx = -2; fx <= row.length * 2 + 1; fx++) {
				for (int fz = -2; fz <= 2; fz++) {
					level.setBlockAndUpdate(new BlockPos(bx + fx, y - 1, bz + fz),
						net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE.defaultBlockState());
					level.setBlockAndUpdate(new BlockPos(bx + fx, y, bz + fz),
						net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
			for (int i = 0; i < row.length; i++) {
				level.setBlockAndUpdate(new BlockPos(bx + i * 2, y, bz), row[i].defaultBlockState());
			}
			// Two lanterns behind them: enough to read the blocks without washing out the embers.
			level.setBlockAndUpdate(new BlockPos(bx - 2, y + 2, bz + 2),
				net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(bx + row.length * 2, y + 2, bz + 2),
				net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState());
		});
		context.runOnClient(mc -> {
			mc.options.fov().set(70);
			mc.options.fovEffectScale().set(0.0);
			mc.gui.hud.getChat().clearMessages(false);
		});
		// Pinned every tick: a spectator is not held up by anything, and this has to wait a full minute
		// because the spark off a small forge is one tick in sixteen and a single frame would miss it.
		// At the blocks' own height. Any camera above them has to look down at them, and looking down
		// at a one-block-tall thing puts it at the bottom edge of the frame no matter how the angle is
		// worked out — which is what the first four attempts all did, at four different angles.
		// Look at the middle of the row, from five blocks out and a little above it.
		forgeShot(context, server, x + 91.0, y + 2.0, z + 77.0, x + 91.0, y + 0.6, z + 82.0,
			70, "forja_bloques_00_fila");
		server.runCommand("time set noon");
		forgeShot(context, server, x + 91.0, y + 2.0, z + 77.0, x + 91.0, y + 0.6, z + 82.0,
			20, "forja_bloques_01_de_dia");
		server.runCommand("time set midnight");
		// And the great forge alone, close.
		forgeShot(context, server, x + 88.6, y + 1.6, z + 79.0, x + 90.0, y + 0.7, z + 82.0,
			60, "forja_bloques_02_cerca");

		server.runCommand("gamemode survival @a");
		server.runCommand("time set day");
	}

	/**
	 * Holds the camera still for a while and then takes the shot, with the chat out of the way.
	 *
	 * <p>Aimed with `tp ... facing` rather than with a yaw and a pitch. Five attempts went into working
	 * those out by hand and every one of them put the blocks along the bottom edge of the frame — the
	 * arithmetic was right each time and something about where the arena actually sits was not. Naming
	 * the point to look at removes the question.
	 */
	private static void forgeShot(ClientGameTestContext context, TestServerContext server,
		double cx, double cy, double cz, double tx, double ty, double tz, int settle, String name) {
		for (int tick = 0; tick < settle; tick++) {
			server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f",
				cx, cy, cz, tx, ty, tz));
			context.waitTicks(1);
		}
		// Right before the shutter: the gamemode and time commands keep putting lines back.
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.waitTicks(2);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(1280, 720));
	}

	private static final java.util.Set<dev.forja.world.WorldEvents> FILMED_SKIES = java.util.EnumSet.of(
		dev.forja.world.WorldEvents.AURORA, dev.forja.world.WorldEvents.METEORITOS,
		dev.forja.world.WorldEvents.TORMENTA_ARCANA, dev.forja.world.WorldEvents.LLUVIA_DE_PAVESAS);

	/** {time of day, yaw, pitch}: when and which way each event's sky is worth looking at. */
	private static float[] skyView(dev.forja.world.WorldEvents event) {
		return switch (event) {
			// The moon a third of the way up in the east, early in the night.
			case LUNA_DE_SANGRE, MAREA_VIVA -> new float[] {14200.0F, -90.0F, -30.0F};
			// Mid-morning, the sun half way up the eastern sky with the disc across it.
			case ECLIPSE -> new float[] {3000.0F, -90.0F, -42.0F};
			case TORMENTA_ARCANA -> new float[] {18000.0F, 180.0F, -58.0F};
			case AURORA -> new float[] {18000.0F, 180.0F, -26.0F};
			case METEORITOS -> new float[] {18000.0F, 180.0F, -36.0F};
			case LLUVIA_DE_PAVESAS -> new float[] {18000.0F, 180.0F, -10.0F};
			case NIEBLA_DE_ALMAS, VENTISCA -> new float[] {18000.0F, 180.0F, -8.0F};
		};
	}

	private static void shotSkies(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("weather clear");
		// High up and out in the open, looking a little above the horizon so the shot is mostly sky
		// with just enough ground to tell what the fog is doing.
		tp(server, x + 90.5, y + 40.0, z + 90.5, 140.0F, -12.0F);
		context.runOnClient(mc -> {
			mc.options.fov().set(80);
			mc.options.fovEffectScale().set(0.0);
			mc.gui.hud.getChat().clearMessages(false);
		});
		server.runCommand("time set midnight");
		// Clear whatever an earlier test left running, or the control shot is not a control. One of
		// them starts an Aurora and never ends it, and the first version of this sheet had a green
		// "no event" sky sitting at the top of it looking like the whole thing was broken.
		server.runOnServer(s -> dev.forja.world.WorldEvents.stop(connection.getServerLevel()));
		context.waitTicks(110);
		context.takeScreenshot(TestScreenshotOptions.of("forja_cielo_00_sin_evento").disableCounterPrefix().withSize(960, 540));

		// Started on the server directly rather than through `/forja evento`. The command asks its
		// source for a player and `runCommand` speaks as the console, so every one of these failed
		// silently and the sky kept showing whatever an earlier test had left running — which looked
		// exactly like the tint being broken and was not.
		dev.forja.world.WorldEvents[] events = dev.forja.world.WorldEvents.values();
		for (int index = 0; index < events.length; index++) {
			int which = index;
			server.runOnServer(s -> dev.forja.world.WorldEvents.start(connection.getServerLevel(), events[which]));
			// Each night is photographed where its own sky is. They used to share one camera, because
			// an event was a colour and a colour is the same wherever you look; now one has a moon
			// rising in the east, one a sun with a disc across it at mid-morning, one a sigil overhead.
			float[] view = skyView(events[which]);
			server.runCommand("time set " + (int) view[0]);
			// The card that comes down to say so, photographed once it has settled and then put away,
			// so that it is not across the top of every sky shot after it.
			context.waitTicks(30);
			if (which < 2) {
				context.takeScreenshot(TestScreenshotOptions.of("forja_cartel_" + events[which].id()).disableCounterPrefix().withSize(960, 540));
			}
			context.runOnClient(mc -> dev.forja.client.EventBannerHud.dismiss());
			// Long enough for the ease to finish: it takes four seconds on purpose so that an event
			// arriving does not snap the world to another colour.
			context.waitTicks(80);
			tp(server, x + 90.5, y + 40.0, z + 90.5, view[1], view[2]);
			context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
			context.waitTicks(4);
			// What the client actually thinks is going on, which is the only way to tell a sky that is
			// wrong from a screenshot that is stale.
			String showing = context.computeOnClient(mc -> {
				var mood = dev.forja.client.SkyMood.showing();
				return (mood == null ? "nada" : mood.id())
					+ " mezcla " + String.format(Locale.ROOT, "%.2f", dev.forja.client.SkyMood.blend())
					+ " fuerza " + String.format(Locale.ROOT, "%.2f",
						dev.forja.client.SkyMood.skyStrength(mc.level.getOverworldClockTime()));
			});
			log("cielo " + events[index].id() + ": el cliente muestra " + showing);
			check(showing.startsWith(events[index].id()),
				"the client should be showing " + events[index].id() + ", got " + showing);
			context.takeScreenshot(TestScreenshotOptions.of(
				String.format(Locale.ROOT, "forja_cielo_%02d_%s", index + 1, events[index].id()))
				.disableCounterPrefix().withSize(960, 540));
			// The ones that move get a strip of film as well: an aurora, a meteor shower and sheet
			// lightning are none of them a thing a single frame can show. Small frames, every other
			// tick; tools outside the game turn them into a loop.
			if (FILMED_SKIES.contains(events[which])) {
				for (int frame = 0; frame < 44; frame++) {
					tp(server, x + 90.5, y + 40.0, z + 90.5, view[1], view[2]);
					context.waitTicks(2);
					context.takeScreenshot(TestScreenshotOptions.of(
						String.format(Locale.ROOT, "forja_film_cielo_%s_%03d", events[which].id(), frame))
						.disableCounterPrefix().withSize(480, 270));
				}
			}
			// And one from the ground, where the event's own weather is. The sheet above is all sky;
			// the embers, the snow and the souls happen around the player and need the world in frame.
			int settle = 30;
			for (int tick = 0; tick < settle; tick++) {
				// The same way the sky shot looked, but from the floor and nearer the horizon, so the
				// ground, the fog and the weather are in frame with whatever is up there.
				tp(server, x + 90.5, y + 1.0, z + 84.0, view[1], Math.min(-4.0F, view[2] * 0.45F));
				context.waitTicks(1);
			}
			context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
			context.waitTicks(2);
			context.takeScreenshot(TestScreenshotOptions.of(
				String.format(Locale.ROOT, "forja_tiempo_%02d_%s", index + 1, events[index].id()))
				.disableCounterPrefix().withSize(960, 540));
			tp(server, x + 90.5, y + 40.0, z + 90.5, 140.0F, -12.0F);
		}
		server.runCommand("time set midnight");

		// And that it really does go back to normal rather than staying lit.
		server.runOnServer(s -> dev.forja.world.WorldEvents.stop(connection.getServerLevel()));
		context.waitTicks(110);
		tp(server, x + 90.5, y + 40.0, z + 90.5, 140.0F, -12.0F);
		context.waitTicks(4);
		context.takeScreenshot(TestScreenshotOptions.of("forja_cielo_99_apagado").disableCounterPrefix().withSize(960, 540));

		context.runOnClient(mc -> {
			mc.options.fov().set(70);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
		server.runCommand("gamemode survival @a");
		server.runCommand("time set day");
	}

	private static void filmAttacks(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("time set day");
		// Peaceful refuses to make a hostile mob at all, and the rest of the file leaves it there: every
		// other mob test raises it first and puts it back. Without this the cast simply does not exist.
		server.runCommand("difficulty easy");
		server.runCommand("gamemode spectator @a");
		double stageX = x + 90.0;
		double stageZ = z + 82.0;
		CAMERA_X = stageX + 11.0;
		CAMERA_ABS_Y = y + CAMERA_Y;
		CAMERA_ABS_Z = stageZ + CAMERA_Z;
		// Six blocks east, looking west across the pair of them, raised enough to see the floor.
		tp(server, CAMERA_X, y + CAMERA_Y, stageZ + CAMERA_Z, CAMERA_YAW, CAMERA_PITCH);
		context.waitTicks(20);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			// Narrower than the default seventy. At 960x540 a two-block mob six blocks off is about
			// ninety pixels across the default lens, which is a dot: the anticipation is in how a body
			// leans and none of that survives being ninety pixels tall.
			mc.options.fov().set(48);
			// And no speed-widening. A spectator with nothing under it picks up speed, and the lens
			// opens up with the speed, so the scene shrinks away over the length of the shot even once
			// the camera is nailed down. It looked exactly like the camera drifting and it was not.
			mc.options.fovEffectScale().set(0.0);
		});

		// (name, frames): long enough to cover the wind-up, the blow and the recovery of each.
		filmOne(context, server, connection, "01_herrero_onda", 88, level -> {
			dev.forja.entity.FallenSmith smith = spawnSmith(level, (int) stageX, y, (int) stageZ);
			var victim = zombie(level, stageX, y, stageZ + 4.0);
			smith.setTarget(victim);
			smith.anvilWave(level);
			return List.of(smith, victim);
		});
		filmOne(context, server, connection, "02_herrero_reves", 26, level -> {
			dev.forja.entity.FallenSmith smith = spawnSmith(level, (int) stageX, y, (int) stageZ);
			var victim = zombie(level, stageX, y, stageZ + 2.5);
			smith.setTarget(victim);
			smith.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, victim.position());
			smith.backhand(level);
			return List.of(smith, victim);
		});
		filmOne(context, server, connection, "03_coraza_embestida", 58, level -> {
			dev.forja.entity.HollowArmor armor = hollow(level, stageX, y, stageZ);
			var victim = zombie(level, stageX, y, stageZ + 6.0);
			armor.setTarget(victim);
			armor.lunge(level, victim);
			return List.of(armor, victim);
		});
		filmOne(context, server, connection, "04_pavesa_picado", 30, level -> {
			var wisp = dev.forja.registry.ModEntities.PAVESA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(wisp != null, "the wisp should be creatable");
			wisp.snapTo(stageX, y + 2.0, stageZ, 0.0F, 0.0F);
			wisp.setNoAi(true);
			level.addFreshEntity(wisp);
			var victim = zombie(level, stageX, y, stageZ + 5.0);
			wisp.setTarget(victim);
			wisp.dive(level);
			return List.of(wisp, victim);
		});
		filmOne(context, server, connection, "05_automata_vapor", 28, level -> {
			var golem = dev.forja.registry.ModEntities.AUTOMATA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(golem != null, "the automaton should be creatable");
			golem.snapTo(stageX, y, stageZ, 0.0F, 0.0F);
			golem.setNoAi(true);
			level.addFreshEntity(golem);
			var victim = zombie(level, stageX, y, stageZ + 2.0);
			golem.setTarget(victim);
			golem.steamPurge(level);
			return List.of(golem, victim);
		});
		filmOne(context, server, connection, "06_automata_coz", 74, level -> {
			var golem = dev.forja.registry.ModEntities.AUTOMATA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(golem != null, "the automaton should be creatable");
			golem.snapTo(stageX, y, stageZ, 0.0F, 0.0F);
			golem.setNoAi(true);
			level.addFreshEntity(golem);
			var victim = zombie(level, stageX, y, stageZ + 5.0);
			golem.setTarget(victim);
			golem.slagStomp(level, victim);
			return List.of(golem, victim);
		});
		// The three deaths. Each mob goes in alive, the camera settles for a second, and then it dies.
		filmOne(context, server, connection, "08_coraza_muerte", 44, level -> {
			dev.forja.entity.HollowArmor armor = hollow(level, stageX, y, stageZ);
			return List.of(armor);
		}, 14, level -> {
			for (dev.forja.entity.HollowArmor armor : level.getEntitiesOfClass(dev.forja.entity.HollowArmor.class,
				new net.minecraft.world.phys.AABB(new BlockPos((int) stageX, y, (int) stageZ)).inflate(6))) {
				armor.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
			}
		});
		filmOne(context, server, connection, "09_automata_muerte", 44, level -> {
			var golem = dev.forja.registry.ModEntities.AUTOMATA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(golem != null, "the automaton should be creatable");
			golem.snapTo(stageX, y, stageZ, 0.0F, 0.0F);
			golem.setNoAi(true);
			level.addFreshEntity(golem);
			return List.of(golem);
		}, 14, level -> {
			for (dev.forja.entity.ForgeAutomaton golem : level.getEntitiesOfClass(dev.forja.entity.ForgeAutomaton.class,
				new net.minecraft.world.phys.AABB(new BlockPos((int) stageX, y, (int) stageZ)).inflate(6))) {
				golem.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
			}
		});
		filmOne(context, server, connection, "10_pavesa_muerte", 40, level -> {
			var wisp = dev.forja.registry.ModEntities.PAVESA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(wisp != null, "the wisp should be creatable");
			wisp.snapTo(stageX, y + 1.5, stageZ, 0.0F, 0.0F);
			wisp.setNoAi(true);
			level.addFreshEntity(wisp);
			return List.of(wisp);
		}, 14, level -> {
			for (dev.forja.entity.EmberWisp wisp : level.getEntitiesOfClass(dev.forja.entity.EmberWisp.class,
				new net.minecraft.world.phys.AABB(new BlockPos((int) stageX, y, (int) stageZ)).inflate(6))) {
				wisp.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
			}
		});
		// And the boss crossing into his last quarter, which fires on a health threshold.
		filmOne(context, server, connection, "11_herrero_ultima_fase", 56, level -> {
			dev.forja.entity.FallenSmith smith = spawnSmith(level, (int) stageX, y, (int) stageZ);
			smith.setHealth(smith.getMaxHealth() * 0.6F);
			return List.of(smith);
		}, 16, level -> {
			for (dev.forja.entity.FallenSmith smith : level.getEntitiesOfClass(dev.forja.entity.FallenSmith.class,
				new net.minecraft.world.phys.AABB(new BlockPos((int) stageX, y, (int) stageZ)).inflate(8))) {
				// Just under a quarter, so the next tick of his own logic trips the phase.
				smith.setHealth(smith.getMaxHealth() * 0.2F);
			}
		});

		filmOne(context, server, connection, "07_coraza_lamento", 30, level -> {
			dev.forja.entity.HollowArmor armor = hollow(level, stageX, y, stageZ);
			var victim = zombie(level, stageX, y, stageZ + 2.0);
			armor.setTarget(victim);
			armor.wail(level);
			return List.of(armor, victim);
		});

		server.runCommand("gamemode survival @a");
		server.runCommand("difficulty peaceful");
		context.runOnClient(mc -> {
			mc.options.fov().set(70);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
	}

	/**
	 * The ring off the smith's hammer, now that it is an entity of its own.
	 *
	 * <p>Checked the way the rest of this file checks things, by what it does: the warning is on the
	 * floor as soon as the hammer goes up and the client can see it, the blow turns that same entity
	 * into the ring, nobody is hurt before the ring has reached them, it clears up after itself, and a
	 * smith who goes with the hammer still up takes his warning with him. Then it is photographed, by
	 * day and by night, over a step and a ditch — because none of the above says whether it looks
	 * like fire, or whether it follows the floor.
	 */
	private static void checkShockwave(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("difficulty easy");
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("gamemode spectator @a");
		int px = x + 140;
		int pz = z + 60;
		// Up in one corner looking down across the whole ring, which is eighteen blocks wide.
		double camX = px + 12.5;
		double camY = y + 9.0;
		double camZ = pz + 12.5;
		tp(server, camX, camY, camZ, 135.0F, 30.0F);
		context.waitTicks(20);
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", px - 18, y - 1, pz - 18, px + 18, y - 1, pz + 18));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 18, y, pz - 18, px + 18, y + 8, pz + 18));
		// A step up on one side and a ditch on the other: a ring that ignores the floor shows it here.
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d polished_blackstone", px - 12, y, pz - 7, px + 1, y, pz - 5));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 7, y - 1, pz - 1, px - 5, y - 1, pz + 12));
		context.waitTicks(30);
		// Tighter than the default seventy, so the ring fills the frame instead of the horizon. What the
		// lens was set to is put back afterwards exactly, because the sections after this one were
		// all photographed with whatever the ones before it left behind.
		double[] lens = context.computeOnClient(mc -> {
			double[] was = {mc.options.fov().get(), mc.options.fovEffectScale().get()};
			mc.gui.hud.getChat().clearMessages(false);
			mc.options.fov().set(50);
			mc.options.fovEffectScale().set(0.0);
			return was;
		});

		java.util.UUID[] cast = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.FallenSmith smith = spawnSmith(level, px, y, pz);
			// A husk, not a zombie: this one stands in the noon sun for four seconds, and a zombie
			// catching fire by itself would look exactly like the ring having hit it early.
			var husk = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(husk != null, "a husk should be creatable");
			husk.snapTo(px + 2.5, y, pz + 5.5, 0.0F, 0.0F);
			husk.setNoAi(true);
			husk.setPersistenceRequired();
			level.addFreshEntity(husk);
			smith.anvilWave(level);
			return new java.util.UUID[] {smith.getUUID(), husk.getUUID()};
		});
		double away = Math.sqrt(2.5 * 2.5 + 5.5 * 5.5);

		context.waitTicks(3);
		double[] early = waveState(server, connection, px, y, pz, cast[1]);
		check(early[0] == 1 && early[1] == 0, "the hammer going up should put one warning on the floor, found " + early[0] + " fired " + early[1]);
		check(Math.abs(early[3] - dev.forja.entity.FallenSmith.WAVE_REACH) < 0.01 && early[4] == dev.forja.entity.FallenSmith.WAVE_WINDUP,
			"the warning should carry the ring's reach and the hammer's timing, got " + early[3] + " and " + early[4]);
		int[] seen = clientWaves(context, px, y, pz);
		check(seen[0] == 1 && seen[1] == 0, "the client should have the warning too, saw " + seen[0]);
		check(seen[2] == Math.round(dev.forja.entity.FallenSmith.WAVE_REACH * 10), "and know how far it reaches, got " + seen[2] / 10.0);
		double full = early[5];

		int shot = 0;
		for (int target : new int[] {14, 34, 50}) {
			for (int guard = 0; guard < 80 && waveState(server, connection, px, y, pz, cast[1])[2] < target; guard++) {
				context.waitTicks(1);
			}
			shootWave(context, server, camX, camY, camZ, String.format(Locale.ROOT, "forja_onda_%02d_aviso", shot++));
		}

		// The run itself, a tick at a time: when the husk is first hurt, and a frame every few ticks.
		double hurtAt = -1.0;
		boolean wasFired = false;
		int[] frames = {2, 6, 10, 14, 18, 23};
		int frame = 0;
		for (int guard = 0; guard < 120; guard++) {
			double[] now = waveState(server, connection, px, y, pz, cast[1]);
			if (now[0] == 0) {
				break;
			}
			if (now[1] > 0) {
				wasFired = true;
				if (hurtAt < 0.0 && now[5] < full) {
					hurtAt = now[2];
				}
				if (frame < frames.length && now[2] >= frames[frame]) {
					shootWave(context, server, camX, camY, camZ, String.format(Locale.ROOT, "forja_onda_%02d_frente_%02d", shot++, (int) now[2]));
					frame++;
					continue;
				}
			}
			context.waitTicks(1);
		}
		check(wasFired, "the blow should turn the warning into the ring");
		check(hurtAt >= 0.0, "the ring should reach a husk six blocks out");
		double reached = dev.forja.entity.Shockwave.radiusAt(dev.forja.entity.FallenSmith.WAVE_REACH, (float) hurtAt, dev.forja.entity.FallenSmith.WAVE_TICKS);
		log("onda: el husk a " + String.format(Locale.ROOT, "%.2f", away) + " bloques cae en el tick " + (int) hurtAt + ", con el frente en " + String.format(Locale.ROOT, "%.2f", reached));
		// Half a block of slack is one tick of travel. The old band, 1.2 either side of the ring, fails this.
		check(reached >= away - 0.3 - 0.5, "nobody should be hurt before the ring has reached them: hurt with the front at " + reached + " of " + away);

		context.waitTicks(14);
		double[] after = waveState(server, connection, px, y, pz, cast[1]);
		check(after[0] == 0, "the ring should clear itself up when it has run, " + after[0] + " left");
		check(clientWaves(context, px, y, pz)[0] == 0, "and the client should lose it too");

		// By night, which is where it is meant to be seen.
		server.runCommand("time set midnight");
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			if (level.getEntity(cast[0]) instanceof dev.forja.entity.FallenSmith smith) {
				smith.anvilWave(level);
			}
		});
		for (int guard = 0; guard < 80 && waveState(server, connection, px, y, pz, cast[1])[2] < 40; guard++) {
			context.waitTicks(1);
		}
		shootWave(context, server, camX, camY, camZ, String.format(Locale.ROOT, "forja_onda_%02d_noche_aviso", shot++));
		int[] nightFrames = {5, 11, 17};
		frame = 0;
		for (int guard = 0; guard < 120 && frame < nightFrames.length; guard++) {
			double[] now = waveState(server, connection, px, y, pz, cast[1]);
			if (now[1] > 0 && now[2] >= nightFrames[frame]) {
				shootWave(context, server, camX, camY, camZ, String.format(Locale.ROOT, "forja_onda_%02d_noche_frente_%02d", shot++, (int) now[2]));
				frame++;
				continue;
			}
			context.waitTicks(1);
		}
		// From the floor, the way whoever it is coming for sees it.
		context.waitTicks(30);
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			if (level.getEntity(cast[0]) instanceof dev.forja.entity.FallenSmith smith) {
				smith.anvilWave(level);
			}
		});
		for (int guard = 0; guard < 160; guard++) {
			double[] now = waveState(server, connection, px, y, pz, cast[1]);
			if (now[1] > 0 && now[2] >= 9) {
				break;
			}
			context.waitTicks(1);
		}
		tp(server, px + 1.5, y + 0.2, pz + 11.5, 172.0F, 2.0F);
		context.waitTicks(1);
		context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_onda_%02d_noche_desde_el_suelo", shot++)).disableCounterPrefix().withSize(960, 540));
		context.waitTicks(30);

		// A smith who goes with the hammer still up: the warning must not outlive him.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			if (level.getEntity(cast[0]) instanceof dev.forja.entity.FallenSmith smith) {
				smith.anvilWave(level);
			}
		});
		context.waitTicks(8);
		check(waveState(server, connection, px, y, pz, cast[1])[0] == 1, "a second blow should put a second warning down");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			net.minecraft.world.entity.Entity smith = level.getEntity(cast[0]);
			if (smith != null) {
				smith.discard();
			}
		});
		context.waitTicks(4);
		check(waveState(server, connection, px, y, pz, cast[1])[0] == 0, "a smith who is gone should take his warning with him");

		// "Se salta", says the guide. Two husks the same distance out, one standing and one held a
		// jump's height off the floor: the ring takes the first and goes under the second.
		float[] jumped = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.FallenSmith smith = spawnSmith(level, px, y, pz + 9);
			var standing = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			var airborne = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(standing != null && airborne != null, "husks should be creatable");
			standing.snapTo(px + 5.0, y, pz + 9, 0.0F, 0.0F);
			airborne.snapTo(px - 5.0, y + 1.1, pz + 9, 0.0F, 0.0F);
			for (var husk : List.of(standing, airborne)) {
				husk.setNoAi(true);
				husk.setNoGravity(true);
				level.addFreshEntity(husk);
			}
			float standingBefore = standing.getHealth();
			float airborneBefore = airborne.getHealth();
			smith.anvilWave(level);
			for (int tick = 0; tick < dev.forja.entity.FallenSmith.WAVE_WINDUP + dev.forja.entity.FallenSmith.WAVE_TICKS + 5; tick++) {
				smith.tick();
			}
			float[] lost = {standingBefore - standing.getHealth(), airborneBefore - airborne.getHealth()};
			standing.discard();
			airborne.discard();
			smith.discard();
			return lost;
		});
		log("onda: de pie pierde " + jumped[0] + ", en el aire pierde " + jumped[1]);
		check(jumped[0] > 0.0F, "the ring should hit a husk standing on the floor");
		check(jumped[1] == 0.0F, "the ring should pass under a husk that is a jump off the floor, it took " + jumped[1]);

		// The same ring is what every other area attack in the mod is drawn with now. Two of them stand
		// for the two ways it is used: a burst with no warning, and a warning that a charge owns.
		float[] others = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz - 10)).inflate(10.0);
			level.getEntitiesOfClass(dev.forja.entity.Shockwave.class, around).forEach(net.minecraft.world.entity.Entity::discard);

			var golem = dev.forja.registry.ModEntities.AUTOMATA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(golem != null, "the automaton should be creatable");
			golem.snapTo(px, y, pz - 10, 0.0F, 0.0F);
			golem.setNoAi(true);
			level.addFreshEntity(golem);
			golem.steamPurge(level);
			var steam = level.getEntitiesOfClass(dev.forja.entity.Shockwave.class, around);
			float steamOk = steam.size() == 1 && steam.get(0).fired() && steam.get(0).colour() == dev.forja.entity.Shockwave.STEAM
				&& Math.abs(steam.get(0).reach() - dev.forja.entity.ForgeAutomaton.STEAM_REACH) < 0.01 ? 1.0F : 0.0F;
			steam.forEach(net.minecraft.world.entity.Entity::discard);
			golem.discard();

			var hauler = dev.forja.registry.ModEntities.CARGADOR_DE_CARBON.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(hauler != null, "the hauler should be creatable");
			hauler.snapTo(px, y, pz - 10, 0.0F, 0.0F);
			hauler.setNoAi(true);
			level.addFreshEntity(hauler);
			hauler.prime(level);
			var fuse = level.getEntitiesOfClass(dev.forja.entity.Shockwave.class, around);
			float warned = fuse.size() == 1 && !fuse.get(0).fired() && fuse.get(0).windup() == dev.forja.entity.CoalHauler.PRIME_WINDUP ? 1.0F : 0.0F;
			for (int tick = 0; tick < dev.forja.entity.CoalHauler.PRIME_WINDUP + 2; tick++) {
				hauler.tick();
			}
			var blast = level.getEntitiesOfClass(dev.forja.entity.Shockwave.class, around);
			float blown = blast.size() == 1 && blast.get(0).fired() ? 1.0F : 0.0F;
			blast.forEach(net.minecraft.world.entity.Entity::discard);
			hauler.discard();
			return new float[] {steamOk, warned, blown};
		});
		log("onda en los demas: purga de vapor " + (others[0] > 0) + ", mecha del cargador avisa " + (others[1] > 0) + " y estalla con el mismo circulo " + (others[2] > 0));
		check(others[0] > 0, "the automaton's purge should be one fired ring of steam as wide as it scalds");
		check(others[1] > 0, "a primed hauler should put its blast circle on the floor for as long as the fuse");
		check(others[2] > 0, "and that same circle should be what goes off, not a second one");
		checkAttackTimings();
		log("onda de yunque: aviso, frente, limpieza, cancelacion y salto comprobados");

		server.runOnServer(s -> {
			net.minecraft.world.entity.Entity husk = connection.getServerLevel().getEntity(cast[1]);
			if (husk != null) {
				husk.discard();
			}
		});
		context.runOnClient(mc -> {
			mc.options.fov().set((int) lens[0]);
			mc.options.fovEffectScale().set(lens[1]);
		});
		server.runCommand("time set noon");
		server.runCommand("gamemode survival @a");
		server.runCommand("difficulty peaceful");
	}

	/**
	 * Every charged attack lands on a tick the code counts to, and is drawn by an animation that has
	 * to reach its blow on that same tick. Nothing tied the two together, and on 2026-09-19 it showed:
	 * three windups had been tripled and their animations left alone, so the smith's hammer hit the
	 * floor two seconds before his ring left it, the hollow plate lunged on the spot and then set off,
	 * and the automaton's move asked for an animation that did not exist. So: each one must have a
	 * keyframe on its landing tick, and last at least that long.
	 */
	private static void checkAttackTimings() {
		record Timing(String mob, String animation, int lands) {
		}
		List<Timing> timings = List.of(
			new Timing("herrero_caido", "slam", dev.forja.entity.FallenSmith.WAVE_WINDUP),
			new Timing("herrero_caido", "hook", dev.forja.entity.FallenSmith.HOOK_WINDUP),
			new Timing("herrero_caido", "strike", dev.forja.entity.FallenSmith.STRIKE_WINDUP),
			new Timing("automata_de_forja", "coz", dev.forja.entity.ForgeAutomaton.SLAG_WINDUP),
			new Timing("coraza_vacia", "dash", dev.forja.entity.HollowArmor.DASH_WINDUP),
			new Timing("percutor", "drop", dev.forja.entity.Striker.DROP_WINDUP),
			new Timing("guardian_de_cuno", "stamp", dev.forja.entity.CuneGuardian.STAMP_WINDUP),
			new Timing("cargador_de_carbon", "prime", dev.forja.entity.CoalHauler.PRIME_WINDUP),
			new Timing("templador", "douse", dev.forja.entity.Quencher.DOUSE_WINDUP),
			new Timing("molde_roto", "recast", dev.forja.entity.BrokenMould.RECAST_WINDUP),
			new Timing("tenaza", "grab", dev.forja.entity.Tongs.GRAB_WINDUP),
			new Timing("nucleo_estelar", "release", dev.forja.entity.StarCore.RELEASE_WINDUP)
		);
		List<String> wrong = new ArrayList<>();
		for (Timing timing : timings) {
			String path = "/assets/forja/geckolib/animations/entity/" + timing.mob() + ".animation.json";
			try (java.io.InputStream in = ForjaClientTest.class.getResourceAsStream(path)) {
				check(in != null, "no animation file for " + timing.mob());
				com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(
					new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
				com.google.gson.JsonObject animation = root.getAsJsonObject("animations").getAsJsonObject(timing.animation());
				if (animation == null) {
					wrong.add(timing.mob() + ":" + timing.animation() + " does not exist");
					continue;
				}
				boolean keyed = false;
				for (var bone : animation.getAsJsonObject("bones").entrySet()) {
					for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
						if (!channel.getValue().isJsonObject()) {
							continue;
						}
						for (String time : channel.getValue().getAsJsonObject().keySet()) {
							keyed |= Math.abs(Float.parseFloat(time) * 20.0F - timing.lands()) < 0.01F;
						}
					}
				}
				float length = animation.get("animation_length").getAsFloat() * 20.0F;
				if (!keyed || length < timing.lands()) {
					wrong.add(timing.mob() + ":" + timing.animation() + " lands on tick " + timing.lands()
						+ (keyed ? "" : " with no keyframe there") + ", animation is " + Math.round(length) + " ticks");
				}
			} catch (java.io.IOException problem) {
				throw new AssertionError("could not read " + path, problem);
			}
		}
		log("animaciones de ataque: " + timings.size() + " comprobadas, " + wrong.size() + " desfasadas");
		check(wrong.isEmpty(), "attack animations out of step with their windups: " + String.join("; ", wrong));
	}

	/**
	 * The two bars over the hotbar, and the two kinds of ground that stays dangerous.
	 *
	 * <p>Both pairs are things that were drawn with the least that would do — flat rectangles, and
	 * particles scattered over a circle — and are now drawn as objects. The check is that a poured
	 * patch is one entity on both sides and goes when its time is up; the rest is for looking at.
	 */
	/**
	 * Potencial: the ceiling on a piece's upgrades (forge/Potential, docs/POTENCIAL.md).
	 *
	 * <p>Andy's rules, each asked of the real menu at a real table rather than of the arithmetic: the first
	 * bench stops at fifty, a piece stops at its own potential, nothing passes ninety without master flux
	 * and exactly one is spent when something does, a ceiling never lowers what is already there, pacts
	 * and what the sky leaves go past every ceiling, and an orb is worth what it cost and not what it says.
	 */
	private static void checkPotential(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		BlockPos bench = new BlockPos(x + 3, y, z - 6);
		BlockPos greater = new BlockPos(x + 5, y, z - 6);
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_forja", bench.getX(), bench.getY(), bench.getZ()));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_forja_mayor", greater.getX(), greater.getY(), greater.getZ()));
		tp(server, x + 4.5, y, z - 8.5, 0.0F, 20.0F);
		context.waitTicks(5);

		// ---- the arithmetic, which everything else leans on
		server.runOnServer(s -> {
			check(dev.forja.forge.Potential.raised(0, 50) == 50, "the first half of an upgrade costs what it says");
			check(dev.forja.forge.Potential.raised(50, 50) == 75, "from fifty an ingredient is worth half, got " + dev.forja.forge.Potential.raised(50, 50));
			check(dev.forja.forge.Potential.raised(75, 100) == 100, "and from seventy-five a quarter, got " + dev.forja.forge.Potential.raised(75, 100));
			check(dev.forja.forge.Potential.raised(49, 36) == 67, "an ingredient across a line is split over it: 1 at full, 35 at half, got " + dev.forja.forge.Potential.raised(49, 36));
			for (int percent : new int[] {1, 37, 50, 51, 75, 76, 100}) {
				int back = dev.forja.forge.Potential.raised(0, dev.forja.forge.Potential.value(percent));
				check(back == percent, "an orb on a bare piece gives back what went into it: " + percent + " came back as " + back);
			}
		});

		// ---- forged at the first bench, from parts cut at the table, with a decent blow
		openTable(context, server, connection, bench);
		int[] first = server.computeOnServer(s -> {
			ForgeMenu menu = menu(connection);
			ServerPlayer player = connection.getServerPlayer();
			int smithThen = dev.forja.forge.SmithLevel.level(player);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.CABEZA_PICO, HIERRO));
			menu.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.MANGO, MADERA));
			menu.getSlot(ForgeMenu.STAR_FIRST + 4).set(Assembler.createPart(PartType.ATADURA, CUERO));
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_PRESS + 1), "a decent press should forge");
			ItemStack pick = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			int potential = dev.forja.forge.Potential.of(pick);
			// Sugar is Eficiencia at two percent a lump, and there is a great deal of it.
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.SUGAR, 64));
			var application = menu.application();
			check(application != null && application.upgrade() == Upgrade.EFICIENCIA, "sugar on the star should be Eficiencia");
			int planned = application.after();
			int limit = application.limit().ordinal();
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "the upgrade should apply");
			ItemStack upgraded = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			int reached = upgraded.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.EFICIENCIA);
			int sugarLeft = menu.getSlot(ForgeMenu.STAR_FIRST).getItem().getCount();
			// Again: it is at its ceiling, so nothing may be taken and nothing may change.
			var again = menu.application();
			boolean stuck = again != null && again.result().isEmpty() && menu.action() != ForgeMenu.Action.UPGRADE;
			return new int[] {potential, planned, limit, reached, sugarLeft, stuck ? 1 : 0, smithThen};
		});
		int smith = first[6];
		int expected = dev.forja.forge.Potential.FLOOR + dev.forja.forge.Potential.PER_QUALITY + smith;
		log("potencial: pico de mesa con golpe decente y herrero " + smith + " -> " + first[0] + " (esperado " + expected + "); el azucar lo sube a "
			+ first[3] + ", tope " + dev.forja.forge.Potential.Limit.values()[first[2]] + ", sobran " + first[4] + " de azucar");
		check(first[0] == expected, "a bench pickaxe of cut parts should have potential " + expected + ", got " + first[0]);
		check(first[3] == Math.min(expected, 50) && first[1] == first[3], "its Eficiencia should stop at its potential or the bench's fifty, got " + first[3]);
		check(first[4] > 0, "and the sugar it could not use stays on the star");
		check(first[5] == 1, "at its ceiling the star must offer no upgrade at all");

		// ---- a piece from before the potential existed, already past the bench: never lowered, never raised here
		int[] legacy = server.computeOnServer(s -> {
			ForgeMenu menu = menu(connection);
			var registries = connection.getServerLevel().registryAccess();
			ItemStack old = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			old = UpgradeRecipes.upgraded(old, ForgeType.ESPADA, Upgrade.FILO, 80, registries);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(old);
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(new ItemStack(Items.AMETHYST_SHARD, 32));
			var application = menu.application();
			boolean refused = application != null && application.result().isEmpty() && application.limit() == dev.forja.forge.Potential.Limit.STATION;
			int still = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.FILO);
			int potential = dev.forja.forge.Potential.of(old);
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			connection.getServerPlayer().closeContainer();
			return new int[] {refused ? 1 : 0, still, potential};
		});
		log("potencial: espada antigua con Filo 80 en la mesa basica -> rechazada " + (legacy[0] == 1) + ", sigue en " + legacy[1] + ", potencial " + legacy[2]);
		check(legacy[0] == 1 && legacy[1] == 80, "the bench must neither raise nor lower an upgrade that is already past it");
		check(legacy[2] == dev.forja.forge.Potential.LEGACY, "a piece with no potential of its own is a middling one");
		context.waitTicks(5);

		// ---- the greater table: past ninety only with flux, and one pinch spent for it
		openTable(context, server, connection, greater);
		int[] flux = server.computeOnServer(s -> {
			ForgeMenu menu = menu(connection);
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			ItemStack blade = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			blade.set(ModComponents.POTENCIAL, 100);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(blade);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.AMETHYST_SHARD, 64));
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(new ItemStack(Items.AMETHYST_SHARD, 64));
			var without = menu.application();
			int stopsAt = without == null ? -1 : without.after();
			boolean saysFlux = without != null && without.limit() == dev.forja.forge.Potential.Limit.FLUX;
			int shards = without == null ? 0 : without.consumed()[0] + without.consumed()[1];
			menu.getSlot(ForgeMenu.STAR_FIRST + 2).set(new ItemStack(dev.forja.registry.ModItems.FUNDENTE_MAESTRO, 3));
			var with = menu.application();
			int reaches = with == null ? -1 : with.after();
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "with flux the upgrade should go all the way");
			int fluxLeft = menu.getSlot(ForgeMenu.STAR_FIRST + 2).getItem().getCount();
			int filo = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.FILO);
			return new int[] {stopsAt, saysFlux ? 1 : 0, shards, reaches, fluxLeft, filo};
		});
		log("potencial: Filo sin fundente se queda en " + flux[0] + " (avisa " + (flux[1] == 1) + ", " + flux[2] + " amatistas); con fundente llega a "
			+ flux[3] + " -> " + flux[5] + ", quedan " + flux[4] + " de 3 pizcas");
		check(flux[0] == dev.forja.forge.Potential.WITHOUT_FLUX && flux[1] == 1, "without flux an upgrade stops at ninety and says why, got " + flux[0]);
		check(flux[2] > 25, "and the way there costs more than the old twenty-five shards, took " + flux[2]);
		check(flux[3] == 100 && flux[5] == 100, "with flux it reaches a hundred, got " + flux[5]);
		check(flux[4] == 2, "and exactly one pinch is spent, " + flux[4] + " left of 3");

		// ---- pacts go past every ceiling and widen it; an orb is worth what it cost
		int[] rest = server.computeOnServer(s -> {
			ForgeMenu menu = menu(connection);
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
				menu.getSlot(ForgeMenu.STAR_FIRST + i).set(ItemStack.EMPTY);
			}
			ItemStack poor = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			poor.set(ModComponents.POTENCIAL, 45);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(poor);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.GLASS, 64));
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "a pact should go on whatever the piece's potential");
			ItemStack sworn = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			int pact = sworn.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.PACTO_DE_VIDRIO);
			int widened = dev.forja.forge.Potential.of(sworn);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(ItemStack.EMPTY);

			// An orb of Filo 50 on a bare blade is Filo 50, all of it; a second one only takes it to the blade's ceiling
			// and what it could not give stays in the orb, on the star.
			menu.getSlot(ForgeMenu.STAR_FIRST).set(dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 50));
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "an orb should go on");
			int afterFirst = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.FILO);
			boolean emptied = menu.getSlot(ForgeMenu.STAR_FIRST).getItem().isEmpty();
			menu.getSlot(ForgeMenu.STAR_FIRST).set(dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 50));
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "a second orb should go on as far as it can");
			int afterSecond = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.FILO);
			var leftover = dev.forja.item.UpgradeOrbItem.orb(menu.getSlot(ForgeMenu.STAR_FIRST).getItem());
			int kept = leftover == null ? 0 : leftover.percent();

			// And two halves merged are not a whole.
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 50));
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 50));
			var merged = dev.forja.item.UpgradeOrbItem.orb(menu.forgePreview());
			int mergedTo = merged == null ? 0 : merged.percent();
			for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
				menu.getSlot(ForgeMenu.STAR_FIRST + i).set(ItemStack.EMPTY);
			}
			player.closeContainer();
			return new int[] {pact, widened, afterFirst, emptied ? 1 : 0, afterSecond, kept, mergedTo};
		});
		log("potencial: pacto de vidrio a " + rest[0] + " en una pieza de 45 -> potencial " + rest[1] + "; orbe 50 -> " + rest[2] + ", otro orbe 50 -> "
			+ rest[4] + " y quedan " + rest[5] + " en el orbe; dos orbes de 50 se funden en uno de " + rest[6]);
		check(rest[0] == 100, "a pact is outside the ceiling, got " + rest[0]);
		check(rest[1] == 45 + dev.forja.forge.Potential.PER_PACT, "and widens it for the rest, got " + rest[1]);
		check(rest[2] == 50 && rest[3] == 1, "an orb on a bare piece gives all it holds and is used up");
		check(rest[4] == 55 && rest[5] > 0, "a second one stops at the potential and keeps the rest, reached " + rest[4] + " kept " + rest[5]);
		check(rest[6] == 75, "two orbs of fifty are worth one of seventy-five, got " + rest[6]);

		// ---- which slots were poured follows the parts that are actually in the piece
		int[] poured = server.computeOnServer(s -> {
			var registries = connection.getServerLevel().registryAccess();
			ItemStack head = Assembler.createPart(PartType.CABEZA_PICO, HIERRO);
			head.set(ModComponents.COLADA, true);
			ItemStack handle = Assembler.createPart(PartType.MANGO, MADERA);
			handle.set(ModComponents.COLADA, true);
			ItemStack binding = Assembler.createPart(PartType.ATADURA, CUERO);
			ItemStack pick = Assembler.evaluate(List.of(head, handle, binding), registries).stack();
			pick.set(ModComponents.POTENCIAL, 40);
			int two = dev.forja.forge.Potential.fromParts(pick);
			ItemStack swapped = Assembler.evaluate(List.of(pick, Assembler.createPart(PartType.MANGO, PIEDRA)), registries).stack();
			int one = dev.forja.forge.Potential.fromParts(swapped);
			// Swapping the same cast handle back in, twice, gives back exactly what it took and no more.
			ItemStack back = Assembler.evaluate(List.of(swapped, handle.copy()), registries).stack();
			back = Assembler.evaluate(List.of(back, handle.copy()), registries).stack();
			int again = dev.forja.forge.Potential.fromParts(back);
			long kept = Assembler.disassemble(back).returned().stream().filter(part -> part.getOrDefault(ModComponents.COLADA, false)).count();
			return new int[] {two, one, again, (int) kept};
		});
		log("potencial: dos piezas coladas de tres dan +" + poured[0] + ", cambiar una por una de mesa lo deja en +" + poured[1]
			+ ", volver a ponerla (dos veces) en +" + poured[2] + "; al desarmar salen " + poured[3] + " piezas coladas");
		check(poured[0] == Math.round(dev.forja.forge.Potential.CAST_PARTS * 2 / 3.0F) && poured[1] == Math.round(dev.forja.forge.Potential.CAST_PARTS / 3.0F),
			"the cast share should follow the parts, got " + poured[0] + " then " + poured[1]);
		check(poured[2] == poured[0], "and swapping a cast part in again must not print potential, got " + poured[2]);
		check(poured[3] == 2, "taken apart, the poured parts still know they were poured, got " + poured[3]);
		// ---- and what the player sees of all that: the table saying why, the piece saying how much
		server.runCommand("gamemode creative @a");
		openTable(context, server, connection, bench);
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			ItemStack pick = Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, CUERO), connection.getServerLevel().registryAccess());
			pick.set(ModComponents.POTENCIAL, 45);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(pick);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.SUGAR, 64));
		});
		context.waitTicks(10);
		quiet(context);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(3);
		context.takeScreenshot(TestScreenshotOptions.of("forja_42_potencial_mesa_basica").disableCounterPrefix());
		hover(context, ForgeMenu.CENTER_X + 8, ForgeMenu.CENTER_Y + 8);
		context.waitTicks(5);
		context.takeScreenshot(TestScreenshotOptions.of("forja_42_potencial_tooltip").disableCounterPrefix());
		context.getInput().setCursorPos(0, 0);
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(ItemStack.EMPTY);
			connection.getServerPlayer().closeContainer();
		});
		context.waitTicks(5);
		openTable(context, server, connection, greater);
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			var registries = connection.getServerLevel().registryAccess();
			ItemStack blade = Assembler.create(ForgeType.ESPADA, List.of(DIAMANTE, MADERA, ORO), registries);
			blade.set(ModComponents.POTENCIAL, 100);
			blade = UpgradeRecipes.upgraded(blade, ForgeType.ESPADA, Upgrade.FILO, 84, registries);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(blade);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.AMETHYST_SHARD, 64));
		});
		context.waitTicks(10);
		quiet(context);
		context.takeScreenshot(TestScreenshotOptions.of("forja_42_potencial_sin_fundente").disableCounterPrefix());
		server.runOnServer(s -> menu(connection).getSlot(ForgeMenu.STAR_FIRST + 1).set(new ItemStack(dev.forja.registry.ModItems.FUNDENTE_MAESTRO, 2)));
		context.waitTicks(10);
		context.takeScreenshot(TestScreenshotOptions.of("forja_42_potencial_con_fundente").disableCounterPrefix());
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(ItemStack.EMPTY);
			connection.getServerPlayer().closeContainer();
		});
		context.waitTicks(5);
		server.runCommand("gamemode survival @a");
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d air", bench.getX(), bench.getY(), bench.getZ()));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d air", greater.getX(), greater.getY(), greater.getZ()));
	}

	/**
	 * The load: what a piece carries against what its potential lets it hold, and the seven upgrades that
	 * are all or nothing. Through the real menu, because the rule lives in Potential.ceiling and every way
	 * onto a piece - ingredients, orbs, books - has to come out the same.
	 */
	private static void checkLoad(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		BlockPos bench = new BlockPos(x + 3, y, z - 6);
		BlockPos greater = new BlockPos(x + 5, y, z - 6);
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_forja", bench.getX(), bench.getY(), bench.getZ()));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_forja_mayor", greater.getX(), greater.getY(), greater.getZ()));
		server.runCommand("gamemode creative @a");
		tp(server, x + 4.5, y, z - 8.5, 0.0F, 20.0F);
		context.waitTicks(5);

		// ---- the arithmetic
		server.runOnServer(s -> {
			check(dev.forja.forge.Potential.capacity(dev.forja.forge.Potential.FLOOR) == 5, "the worst piece there is should hold five points, got "
				+ dev.forja.forge.Potential.capacity(dev.forja.forge.Potential.FLOOR));
			check(dev.forja.forge.Potential.capacity(100) == 20 && dev.forja.forge.Potential.MOST_CAPACITY == 20, "and a perfect one twenty");
			check(dev.forja.forge.Potential.weight(Upgrade.FILO) == 4 && dev.forja.forge.Potential.weight(Upgrade.CASTIGO) == 2
				&& dev.forja.forge.Potential.weight(Upgrade.EMPUJE) == 1, "Filo weighs four, Castigo two, Empuje one");
			check(dev.forja.forge.Potential.weight(Upgrade.PACTO_DE_VIDRIO) == 0 && dev.forja.forge.Potential.weight(Upgrade.AURORA) == 0
				&& dev.forja.forge.Potential.weight(Upgrade.RECOCIDO) == 0, "pacts, what the sky leaves and the anneal weigh nothing");
			List<Upgrade> whole = new java.util.ArrayList<>();
			for (Upgrade upgrade : Upgrade.values()) {
				if (dev.forja.forge.Potential.allOrNothing(upgrade)) {
					whole.add(upgrade);
				}
			}
			check(whole.size() == 7 && whole.contains(Upgrade.TOQUE_DE_SEDA) && whole.contains(Upgrade.REPARACION) && whole.contains(Upgrade.VISION_NOCTURNA),
				"seven upgrades are all or nothing, got " + whole);
			// A synergy is only worth having if one piece can carry both halves of it.
			for (dev.forja.upgrade.Synergy synergy : dev.forja.upgrade.Synergy.values()) {
				int pair = dev.forja.forge.Potential.weight(synergy.first) + dev.forja.forge.Potential.weight(synergy.second);
				check(pair <= dev.forja.forge.Potential.MOST_CAPACITY / 2, synergy + " weighs " + pair + ": no pair should take more than half a perfect piece");
			}
		});

		// ---- a poor blade at the bench: five points, and what does not fit does not go on
		openTable(context, server, connection, bench);
		int[] first = server.computeOnServer(s -> {
			ForgeMenu menu = menu(connection);
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			sword.set(ModComponents.POTENCIAL, 40);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(sword);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.AMETHYST_SHARD, 3));
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "Filo should go on a bare blade");
			int loaded = dev.forja.forge.Potential.load(menu.getSlot(ForgeMenu.CENTER_SLOT).getItem());
			// Aspecto igneo weighs two and one point is left: turned away whole, and the powder stays where it is.
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.BLAZE_POWDER, 8));
			var refused = menu.application();
			boolean saysLoad = refused != null && refused.upgrade() == Upgrade.ASPECTO_IGNEO && refused.result().isEmpty()
				&& refused.limit() == dev.forja.forge.Potential.Limit.LOAD && menu.action() != ForgeMenu.Action.UPGRADE;
			menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE);
			int powder = menu.getSlot(ForgeMenu.STAR_FIRST).getItem().getCount();
			return new int[] {loaded, saysLoad ? 1 : 0, powder, dev.forja.forge.Potential.capacity(sword)};
		});
		check(first[0] == 4 && first[3] == 5, "Filo on a blade of forty is four points of five, got " + first[0] + " of " + first[3]);
		check(first[1] == 1 && first[2] == 8, "an upgrade that does not fit must be refused for its weight and spend nothing, powder left " + first[2]);
		context.waitTicks(10);
		quiet(context);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(3);
		context.takeScreenshot(TestScreenshotOptions.of("forja_43_carga_no_cabe").disableCounterPrefix());

		int[] rest = server.computeOnServer(s -> {
			ForgeMenu menu = menu(connection);
			ServerPlayer player = connection.getServerPlayer();
			// Empuje weighs one, and one is what is left.
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.PISTON, 1));
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "Empuje weighs one and should fit");
			// Full. What is already on it can still be raised.
			int filoBefore = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.FILO);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.AMETHYST_SHARD, 2));
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "a full piece should still let what is on it rise");
			int filoAfter = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.FILO);
			// An orb of something too heavy stays an orb, and the table says which and why.
			menu.getSlot(ForgeMenu.STAR_FIRST).set(dev.forja.item.UpgradeOrbItem.create(Upgrade.VENENO, 40));
			var turned = menu.refusal();
			boolean orbRefused = menu.action() != ForgeMenu.Action.BOOK && turned != null && turned.upgrade() == Upgrade.VENENO
				&& turned.limit() == dev.forja.forge.Potential.Limit.LOAD;
			// A pact weighs nothing, goes on a full piece, and the ten points it brings open two more of load...
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.GLASS, 64));
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "a pact should go on a full piece: it weighs nothing");
			int widened = dev.forja.forge.Potential.capacity(menu.getSlot(ForgeMenu.CENTER_SLOT).getItem());
			// ...which is exactly what Aspecto igneo was short of.
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.BLAZE_POWDER, 8));
			boolean fitsNow = menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE);
			ItemStack blade = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			int load = dev.forja.forge.Potential.load(blade);

			// ---- all or nothing at the plain bench: the heavy ones not one percent, the light ones all the way
			var registries = connection.getServerLevel().registryAccess();
			ItemStack pick = Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, CUERO), registries);
			pick.set(ModComponents.POTENCIAL, 40);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(pick);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.COBWEB, 64));
			var silk = menu.application();
			boolean silkTurned = silk != null && silk.upgrade() == Upgrade.TOQUE_DE_SEDA && silk.result().isEmpty()
				&& silk.limit() == dev.forja.forge.Potential.Limit.GREATER && menu.action() != ForgeMenu.Action.UPGRADE;
			menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE);
			int webs = menu.getSlot(ForgeMenu.STAR_FIRST).getItem().getCount();
			return new int[] {filoBefore, filoAfter, orbRefused ? 1 : 0, widened, fitsNow ? 1 : 0, load, silkTurned ? 1 : 0, webs};
		});
		log("carga: espada de 40 -> Filo (4) cabe, Aspecto igneo (2) no; Empuje (1) si; Filo sube de " + rest[0] + " a " + rest[1]
			+ " estando llena; orbe de Veneno rechazado " + (rest[2] == 1) + "; con un pacto la capacidad pasa a " + rest[3] + " y Aspecto igneo entra "
			+ (rest[4] == 1) + " (carga " + rest[5] + ")");
		check(rest[1] > rest[0], "an upgrade already on a full piece should still rise, " + rest[0] + " -> " + rest[1]);
		check(rest[2] == 1, "an orb that does not fit stays an orb and the table says why");
		check(rest[3] == 7 && rest[4] == 1 && rest[5] == 7, "a pact opens room: capacity 7 and Aspecto igneo in, got capacity " + rest[3] + " load " + rest[5]);
		check(rest[6] == 1 && rest[7] == 64, "Silk Touch is all or nothing and heavy: the bench takes not one percent and not one cobweb, left " + rest[7]);
		context.waitTicks(10);
		quiet(context);
		context.takeScreenshot(TestScreenshotOptions.of("forja_43_todo_o_nada_mesa").disableCounterPrefix());

		int[] light = server.computeOnServer(s -> {
			ForgeMenu menu = menu(connection);
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			// A Mending book is turned away the same as the cobwebs were.
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			var stored = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
			stored.set(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
			book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
			menu.getSlot(ForgeMenu.STAR_FIRST).set(book);
			var turned = menu.refusal();
			boolean bookTurned = menu.action() != ForgeMenu.Action.BOOK && turned != null && turned.upgrade() == Upgrade.REPARACION
				&& turned.limit() == dev.forja.forge.Potential.Limit.GREATER;
			// The light ones go all the way, here, on a piece of forty, with no flux anywhere.
			ItemStack helmet = Assembler.create(ForgeType.CASCO, List.of(HIERRO, CUERO), registries);
			helmet.set(ModComponents.POTENCIAL, 40);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(helmet);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.SPONGE, 64));
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "Afinidad acuatica is light: the bench should take it");
			ItemStack wet = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			int affinity = wet.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.AFINIDAD_ACUATICA);
			int level = EnchantmentHelper.getItemEnchantmentLevel(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.AQUA_AFFINITY), wet);
			// For the picture: a good blade with a few things on it, lying on the star with nothing else.
			ItemStack fine = Assembler.create(ForgeType.ESPADA, List.of(DIAMANTE, MADERA, ORO), registries);
			fine.set(ModComponents.POTENCIAL, 80);
			// Short-worded upgrades, so the tooltip fits the test's small window and the bar is in the picture.
			fine = UpgradeRecipes.upgraded(fine, ForgeType.ESPADA, Upgrade.FILO, 70, registries);
			fine = UpgradeRecipes.upgraded(fine, ForgeType.ESPADA, Upgrade.CRITICO, 55, registries);
			fine = UpgradeRecipes.upgraded(fine, ForgeType.ESPADA, Upgrade.IRROMPIBLE, 60, registries);
			fine = UpgradeRecipes.upgraded(fine, ForgeType.ESPADA, Upgrade.EMPUJE, 100, registries);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(fine);
			return new int[] {bookTurned ? 1 : 0, affinity, level};
		});
		log("carga: libro de Reparacion rechazado en la mesa normal " + (light[0] == 1) + "; Afinidad acuatica en un casco de 40, en la mesa normal y sin fundente -> "
			+ light[1] + "% (nivel " + light[2] + ")");
		check(light[0] == 1, "a Mending book at the plain bench must be turned away to the greater table");
		check(light[1] == 100 && light[2] == 1, "a light all-or-nothing upgrade goes to a hundred at any table on any piece, got " + light[1]);
		context.waitTicks(10);
		quiet(context);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(3);
		context.takeScreenshot(TestScreenshotOptions.of("forja_43_carga_mesa").disableCounterPrefix());
		hover(context, ForgeMenu.CENTER_X + 8, ForgeMenu.CENTER_Y + 8);
		context.waitTicks(5);
		context.takeScreenshot(TestScreenshotOptions.of("forja_43_carga_tooltip").disableCounterPrefix());
		context.getInput().setCursorPos(0, 0);
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(ItemStack.EMPTY);
			connection.getServerPlayer().closeContainer();
		});
		context.waitTicks(5);

		// ---- and at the greater table the heavy ones go all the way: no potential asked, no flux spent
		openTable(context, server, connection, greater);
		int[] heavy = server.computeOnServer(s -> {
			ForgeMenu menu = menu(connection);
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			ItemStack pick = Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, CUERO), registries);
			pick.set(ModComponents.POTENCIAL, 40);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(pick);
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.COBWEB, 64));
			menu.getSlot(ForgeMenu.STAR_FIRST + 1).set(new ItemStack(dev.forja.registry.ModItems.FUNDENTE_MAESTRO, 2));
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "Silk Touch should be made at the greater table");
			ItemStack silky = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem();
			int silk = silky.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.TOQUE_DE_SEDA);
			int level = EnchantmentHelper.getItemEnchantmentLevel(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), silky);
			int fluxLeft = menu.getSlot(ForgeMenu.STAR_FIRST + 1).getItem().getCount();
			// It weighs three of the pickaxe's five, so Fortuna (four) would not fit even if the two did not clash,
			// and Eficiencia (three) does not either.
			menu.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.SUGAR, 16));
			var sugar = menu.application();
			boolean counted = sugar != null && sugar.limit() == dev.forja.forge.Potential.Limit.LOAD && sugar.result().isEmpty();
			// The Mending book, here, goes on whole.
			ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, HIERRO), registries);
			sword.set(ModComponents.POTENCIAL, 40);
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(sword);
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			var stored = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
			stored.set(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
			book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
			menu.getSlot(ForgeMenu.STAR_FIRST).set(book);
			check(menu.action() == ForgeMenu.Action.BOOK, "a Mending book at the greater table should go on, got " + menu.action());
			check(menu.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "and the button should put it on");
			int mending = menu.getSlot(ForgeMenu.CENTER_SLOT).getItem().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.REPARACION);
			int fluxAfterBook = menu.getSlot(ForgeMenu.STAR_FIRST + 1).getItem().getCount();
			for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
				menu.getSlot(ForgeMenu.STAR_FIRST + i).set(ItemStack.EMPTY);
			}
			menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
			player.closeContainer();
			return new int[] {silk, level, fluxLeft, counted ? 1 : 0, mending, fluxAfterBook};
		});
		log("carga: en la mesa mayor, Toque de seda en un pico de 40 -> " + heavy[0] + "% (nivel " + heavy[1] + "), fundente intacto " + heavy[2]
			+ "/2; su peso cuenta " + (heavy[3] == 1) + "; libro de Reparacion -> " + heavy[4] + "%, fundente " + heavy[5] + "/2");
		check(heavy[0] == 100 && heavy[1] == 1, "at the greater table Silk Touch goes to a hundred whatever the potential, got " + heavy[0]);
		check(heavy[2] == 2 && heavy[5] == 2, "and the flux is not what it needs, so none is spent: " + heavy[2] + " then " + heavy[5] + " of 2");
		check(heavy[3] == 1, "what it weighs counts: three of five leaves no room for Eficiencia");
		check(heavy[4] == 100, "and a Mending book there is Reparacion whole, got " + heavy[4]);
		context.waitTicks(5);
		server.runCommand("gamemode survival @a");
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d air", bench.getX(), bench.getY(), bench.getZ()));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d air", greater.getX(), greater.getY(), greater.getZ()));
	}

	/**
	 * The two magic weapons Andy chose: the crescent staff throws a bolt that hurts what it reaches, and the
	 * forged tome opens its area five blocks ahead, hurts what stands there and leaves a rune that goes on
	 * hurting. Both in the colour of their núcleo. Filmed, because how it looks is half of what was asked for.
	 */
	private static void checkMagic(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 60;
		int pz = z - 60;
		server.runCommand("gamemode creative @a");
		server.runCommand("time set 13000");
		tp(server, px + 0.5, y, pz + 0.5, 180.0F, 8.0F);
		context.waitTicks(20);
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:cow %d %d %d {NoAI:1b,Tags:[\"forja_diana\"]}", px, y, pz - 5));
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			ItemStack made = Assembler.create(ForgeType.BACULO, List.of(dev.forja.material.ForgeMaterial.AMATISTA, HIERRO, MADERA), registries);
			check(!made.isEmpty() && made.get(ModComponents.PARTS) != null, "the staff should forge");
			player.getInventory().clearContent();
			player.getInventory().setItem(0, made);
			player.getInventory().setItem(1, Assembler.create(ForgeType.BACULO, List.of(DIAMANTE, ORO, dev.forja.material.ForgeMaterial.HUESO), registries));
			player.getInventory().setItem(2, Assembler.create(ForgeType.GRIMORIO, List.of(dev.forja.material.ForgeMaterial.VARA_DE_BLAZE, dev.forja.material.ForgeMaterial.NETHERITA, ORO), registries));
			player.getInventory().setItem(3, Assembler.create(ForgeType.GRIMORIO, List.of(dev.forja.material.ForgeMaterial.ESMERALDA, HIERRO, HIERRO), registries));
			player.getInventory().setSelectedSlot(0);
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(0));
		});
		context.waitTicks(30);
		quiet(context);
		float[] staff = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var level = connection.getServerLevel();
			var registries = level.registryAccess();
			ItemStack made = player.getMainHandItem();
			var cow = level.getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class, player.getBoundingBox().inflate(12.0)).getFirst();
			float before = cow.getHealth();
			var cast = dev.forja.magic.Spellcasting.tryCast(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, ForgeType.BACULO);
			check(cast == net.minecraft.world.InteractionResult.SUCCESS, "using the staff should cast, got " + cast);
			int flying = level.getEntitiesOfClass(dev.forja.entity.MagicBolt.class, player.getBoundingBox().inflate(16.0)).size();
			check(flying == 1, "one bolt should be in the air, saw " + flying);
			check(player.getCooldowns().isOnCooldown(made), "and the staff should be cooling down");
			// Nothing of the mod's wears the enchantment glint, however many enchantments it hides; vanilla's own still does.
			ItemStack sharp = UpgradeRecipes.upgraded(Assembler.create(ForgeType.ESPADA, List.of(DIAMANTE, MADERA, ORO), registries), ForgeType.ESPADA, Upgrade.FILO, 100, registries);
			check(sharp.isEnchanted() && !sharp.hasFoil(), "a forged sword with Filo hides Sharpness and must not shine");
			check(!player.getInventory().getItem(2).hasFoil(), "nor a tome of a trait material");
			ItemStack vanilla = new ItemStack(Items.DIAMOND_SWORD);
			vanilla.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
			check(vanilla.hasFoil(), "a vanilla enchanted sword is none of the mod's business and keeps its glint");
			return new float[] {before, dev.forja.magic.Spellcasting.boltDamage(dev.forja.material.ForgeMaterial.AMATISTA)};
		});
		for (int frame = 0; frame < 8; frame++) {
			context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_60f_baculo_%02d", frame)).disableCounterPrefix());
			context.waitTicks(1);
		}
		context.waitTicks(10);
		float afterBolt = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class,
			connection.getServerPlayer().getBoundingBox().inflate(12.0)).getFirst().getHealth());
		log("magia: el baculo de amatista quita " + (staff[0] - afterBolt) + " (esperado " + staff[1] + ") a la vaca a 5 bloques");
		check(afterBolt < staff[0], "the bolt should have hurt what it hit, health " + staff[0] + " -> " + afterBolt);

		// ---- the tome: the area opens five blocks ahead, and a rune stays
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().setSelectedSlot(2);
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(2));
		});
		context.waitTicks(40);
		quiet(context);
		double[] tome = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var level = connection.getServerLevel();
			var cow = level.getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class, player.getBoundingBox().inflate(12.0)).getFirst();
			cow.setHealth(cow.getMaxHealth());
			int runes = dev.forja.magic.Spellcasting.runes();
			net.minecraft.world.phys.Vec3 where = dev.forja.magic.Spellcasting.target(level, player);
			var cast = dev.forja.magic.Spellcasting.tryCast(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, ForgeType.GRIMORIO);
			check(cast == net.minecraft.world.InteractionResult.SUCCESS, "using the tome should cast, got " + cast);
			check(dev.forja.magic.Spellcasting.runes() == runes + 1, "and leave a rune");
			return new double[] {Math.sqrt(player.distanceToSqr(where.x, player.getY(), where.z)), cow.getHealth(), cow.getMaxHealth()};
		});
		log("magia: el area del grimorio cae a " + String.format(Locale.ROOT, "%.2f", tome[0]) + " bloques; la vaca queda en " + tome[1] + " de " + tome[2]);
		check(Math.abs(tome[0] - dev.forja.magic.Spellcasting.TOME_DISTANCE) < 0.3, "the tome's area should land five blocks ahead, landed " + tome[0]);
		check(tome[1] < tome[2], "and hurt what stands in it");
		// long enough to see the letters go round one way and the star the other, and the rune flare as it bites
		for (int frame = 0; frame < 22; frame++) {
			context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_60f_grimorio_%02d", frame)).disableCounterPrefix());
			context.waitTicks(2);
		}
		context.waitTicks(14);
		// The rune may well have finished it off: a cow that is no longer there has certainly been hurt.
		float afterRune = server.computeOnServer(s -> {
			var cows = connection.getServerLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class,
				connection.getServerPlayer().getBoundingBox().inflate(12.0));
			return cows.isEmpty() ? 0.0F : cows.getFirst().getHealth();
		});
		log("magia: tras estar sobre la runa la vaca baja a " + afterRune);
		check(afterRune < tome[1], "the rune should go on hurting what stands on it, " + tome[1] + " -> " + afterRune);
		// ---- the rune itself, read from above: the other tome, so the other colour
		context.waitTicks(dev.forja.magic.Spellcasting.TOME_COOLDOWN);
		server.runCommand("kill @e[tag=forja_diana]");
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().setSelectedSlot(3);
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(3));
			var cast = dev.forja.magic.Spellcasting.tryCast(connection.getServerLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, ForgeType.GRIMORIO);
			check(cast == net.minecraft.world.InteractionResult.SUCCESS, "the second tome should cast once the first has cooled, got " + cast);
		});
		server.runCommand("gamemode spectator @a");
		tp(server, px + 0.5, y + 7.0, pz + 0.5 - dev.forja.magic.Spellcasting.TOME_DISTANCE, 180.0F, 90.0F);
		context.waitTicks(16);
		quiet(context);
		context.takeScreenshot(TestScreenshotOptions.of("forja_60_runa_desde_arriba").disableCounterPrefix());
		context.waitTicks(5);
		context.takeScreenshot(TestScreenshotOptions.of("forja_60_runa_desde_arriba_b").disableCounterPrefix());
		server.runCommand("gamemode creative @a");
		tp(server, px + 0.5, y, pz + 0.5, 180.0F, 8.0F);
		context.waitTicks(10);
		// ---- what is forged onto a staff rides on its bolt: Escarcha chills what it hits, Filo makes it hit harder
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:iron_golem %d %d %d {NoAI:1b,Tags:[\"forja_diana\"]}", px, y, pz - 5));
		context.waitTicks(10);
		float[] golem = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var level = connection.getServerLevel();
			var registries = level.registryAccess();
			ItemStack frost = Assembler.create(ForgeType.BACULO, List.of(DIAMANTE, HIERRO, MADERA), registries);
			frost = UpgradeRecipes.upgraded(frost, ForgeType.BACULO, Upgrade.ESCARCHA, 100, registries);
			frost = UpgradeRecipes.upgraded(frost, ForgeType.BACULO, Upgrade.FILO, 100, registries);
			player.getInventory().setItem(4, frost);
			player.getInventory().setSelectedSlot(4);
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(4));
			var cast = dev.forja.magic.Spellcasting.tryCast(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, ForgeType.BACULO);
			check(cast == net.minecraft.world.InteractionResult.SUCCESS, "the upgraded staff should cast, got " + cast);
			var target = level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, player.getBoundingBox().inflate(12.0)).getFirst();
			return new float[] {target.getHealth(), dev.forja.magic.Spellcasting.boltDamage(DIAMANTE)};
		});
		context.waitTicks(12);
		float[] struck = server.computeOnServer(s -> {
			var target = connection.getServerLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class,
				connection.getServerPlayer().getBoundingBox().inflate(12.0)).getFirst();
			return new float[] {target.getHealth(), target.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) ? 1.0F : 0.0F};
		});
		log("magia: un baculo con Escarcha y Filo quita " + (golem[0] - struck[0]) + " (sin mejoras " + golem[1] + ") y deja lentitud: " + (struck[1] == 1.0F));
		check(struck[1] == 1.0F, "Escarcha on a staff should chill what its bolt hits");
		check(golem[0] - struck[0] > golem[1] + 0.5F, "and Filo should make the bolt hit harder than " + golem[1] + ", took " + (golem[0] - struck[0]));
		// the things themselves, in the hand
		server.runCommand("time set 6000");
		context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
		for (int slot = 0; slot < 4; slot++) {
			int chosen = slot;
			// the hand that is drawn is the client's: choosing the slot on the server alone changes what is used, not what is seen
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(chosen));
			server.runOnServer(s -> connection.getServerPlayer().getInventory().setSelectedSlot(chosen));
			context.waitTicks(10);
			quiet(context);
			context.takeScreenshot(TestScreenshotOptions.of("forja_60_magia_en_mano_" + slot).disableCounterPrefix());
		}
		context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		server.runCommand("kill @e[tag=forja_diana]");
		server.runCommand("time set 6000");
		server.runOnServer(s -> connection.getServerPlayer().getInventory().clearContent());
		server.runCommand("gamemode survival @a");
		tp(server, x + 90.5, y, z + 75.5, 0.0F, 0.0F);
		context.waitTicks(20);
	}

	/** Film only, and only when the magic is run by itself: the new upgrades as they look, for Andy to judge. */
	private static void filmMagicUpgrades(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 100;
		int pz = z - 100;
		server.runCommand("gamemode creative @a");
		server.runCommand("difficulty easy");
		server.runCommand("time set 13000");
		tp(server, px + 0.5, y, pz + 0.5, 180.0F, 6.0F);
		context.waitTicks(30);

		// ---- Enjambre: five bolts, and every one of them comes round
		for (int[] at : new int[][] {{-6, -10}, {-2, -13}, {3, -12}, {7, -9}}) {
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:zombie %d %d %d {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f],Tags:[\"forja_diana\"]}", px + at[0], y, pz + at[1]));
		}
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			player.getInventory().clearContent();
			ItemStack swarm = Assembler.create(ForgeType.BACULO, List.of(dev.forja.material.ForgeMaterial.AMATISTA, ORO, MADERA), registries);
			swarm = UpgradeRecipes.upgraded(swarm, ForgeType.BACULO, Upgrade.PRISMA, 100, registries);
			swarm = UpgradeRecipes.upgraded(swarm, ForgeType.BACULO, Upgrade.BUSCADOR, 100, registries);
			player.getInventory().setItem(0, swarm);
			player.getInventory().setSelectedSlot(0);
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(0));
		});
		context.waitTicks(50);
		quiet(context);
		for (int volley = 0; volley < 2; volley++) {
			int first = volley * 12;
			server.runOnServer(s -> castWith(connection.getServerPlayer(), connection.getServerPlayer().getMainHandItem(), ForgeType.BACULO));
			for (int frame = 0; frame < 12; frame++) {
				context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_61f_enjambre_%02d", first + frame)).disableCounterPrefix());
				context.waitTicks(1);
			}
		}
		server.runCommand("kill @e[tag=forja_diana]");
		context.waitTicks(10);

		// ---- Sobrecarga: the third spell says the next is the big one, and the fourth is
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:iron_golem %d %d %d {NoAI:1b,Rotation:[0f,0f],Tags:[\"forja_diana\"]}", px, y, pz - 9));
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			ItemStack charged = Assembler.create(ForgeType.BACULO, List.of(dev.forja.material.ForgeMaterial.VARA_DE_BLAZE, HIERRO, MADERA), registries);
			charged = UpgradeRecipes.upgraded(charged, ForgeType.BACULO, Upgrade.SOBRECARGA, 100, registries);
			player.getInventory().setItem(0, charged);
		});
		context.waitTicks(50);
		quiet(context);
		int shown = 0;
		for (int spell = 0; spell < 4; spell++) {
			server.runOnServer(s -> castWith(connection.getServerPlayer(), connection.getServerPlayer().getMainHandItem(), ForgeType.BACULO));
			for (int frame = 0; frame < 9; frame++) {
				context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_61f_sobrecarga_%02d", shown++)).disableCounterPrefix());
				context.waitTicks(1);
			}
			context.waitTicks(4);
		}
		server.runCommand("kill @e[tag=forja_diana]");
		context.waitTicks(10);

		// ---- Vortice, Santuario and Colapso: the rune gathers what stands on it, and goes out with a bang
		for (int[] at : new int[][] {{-3, -5}, {3, -5}, {0, -8}, {-2, -3}, {2, -7}}) {
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:zombie %d %d %d {PersistenceRequired:1b,Tags:[\"forja_diana\"],attributes:[{id:\"minecraft:max_health\",base:60.0}],Health:60.0f}",
				px + at[0], y, pz + at[1]));
		}
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			ItemStack tome = Assembler.create(ForgeType.GRIMORIO, List.of(dev.forja.material.ForgeMaterial.ESMERALDA, HIERRO, ORO), registries);
			tome = UpgradeRecipes.upgraded(tome, ForgeType.GRIMORIO, Upgrade.VORTICE, 100, registries);
			tome = UpgradeRecipes.upgraded(tome, ForgeType.GRIMORIO, Upgrade.TINTA_INDELEBLE, 50, registries);
			player.getInventory().setItem(0, tome);
		});
		context.waitTicks(40);
		quiet(context);
		server.runOnServer(s -> castWith(connection.getServerPlayer(), connection.getServerPlayer().getMainHandItem(), ForgeType.GRIMORIO));
		for (int frame = 0; frame < 30; frame++) {
			context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_61f_vortice_%02d", frame)).disableCounterPrefix());
			context.waitTicks(2);
		}
		for (int guard = 0; guard < 220; guard++) {
			int left = server.computeOnServer(s -> dev.forja.magic.Spellcasting.longestRune());
			if (left <= 8) {
				break;
			}
			context.waitTicks(1);
		}
		for (int frame = 0; frame < 16; frame++) {
			context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_61f_colapso_%02d", frame)).disableCounterPrefix());
			context.waitTicks(1);
		}
		server.runCommand("kill @e[tag=forja_diana]");
		context.waitTicks(10);

		// ---- Resonancia on a tome: the area opens, and opens again
		for (int[] at : new int[][] {{-1, -5}, {1, -6}}) {
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:zombie %d %d %d {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f],Tags:[\"forja_diana\"],attributes:[{id:\"minecraft:max_health\",base:60.0}],Health:60.0f}",
				px + at[0], y, pz + at[1]));
		}
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			ItemStack tome = Assembler.create(ForgeType.GRIMORIO, List.of(dev.forja.material.ForgeMaterial.AMATISTA, dev.forja.material.ForgeMaterial.OBSIDIANA, ORO), registries);
			tome = UpgradeRecipes.upgraded(tome, ForgeType.GRIMORIO, Upgrade.RESONANCIA, 100, registries);
			tome = UpgradeRecipes.upgraded(tome, ForgeType.GRIMORIO, Upgrade.SOBRECARGA, 100, registries);
			player.getInventory().setItem(0, tome);
		});
		context.waitTicks(40);
		quiet(context);
		server.runOnServer(s -> castWith(connection.getServerPlayer(), connection.getServerPlayer().getMainHandItem(), ForgeType.GRIMORIO));
		for (int frame = 0; frame < 22; frame++) {
			context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_61f_resonancia_%02d", frame)).disableCounterPrefix());
			context.waitTicks(2);
		}
		server.runCommand("kill @e[tag=forja_diana]");
		server.runCommand("difficulty peaceful");
		server.runCommand("time set 6000");
		server.runOnServer(s -> connection.getServerPlayer().getInventory().clearContent());
		server.runCommand("gamemode survival @a");
		tp(server, x + 90.5, y, z + 75.5, 0.0F, 0.0F);
		context.waitTicks(20);
	}

	/** A staff or a tome of plain diamond, with these upgrades at these percentages. */
	private static ItemStack magicWeapon(net.minecraft.core.HolderLookup.Provider registries, ForgeType type, Object... upgrades) {
		ItemStack made = Assembler.create(type, type == ForgeType.BACULO ? List.of(DIAMANTE, HIERRO, MADERA) : List.of(DIAMANTE, HIERRO, HIERRO), registries);
		for (int i = 0; i < upgrades.length; i += 2) {
			made = UpgradeRecipes.upgraded(made, type, (Upgrade) upgrades[i], (Integer) upgrades[i + 1], registries);
		}
		return made;
	}

	/** Puts a weapon in the player's hand with its wait cleared, and casts it. */
	private static void castWith(ServerPlayer player, ItemStack weapon, ForgeType type) {
		player.getInventory().setItem(0, weapon);
		player.getInventory().setSelectedSlot(0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(0));
		player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(weapon));
		var cast = dev.forja.magic.Spellcasting.tryCast(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND, type);
		check(cast == net.minecraft.world.InteractionResult.SUCCESS, "the " + type + " should cast, got " + cast);
	}

	/**
	 * "tambien dame mejoras para las armas magicas": the eight upgrades that are about the casting and the two
	 * pairs, each against the real spell. The ones a blade already had ride on a spell too; checkMagic asks that.
	 */
	private static void checkMagicUpgrades(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 100;
		int pz = z - 60;
		server.runCommand("gamemode creative @a");
		server.runCommand("difficulty easy");
		server.runCommand("time set 13000");
		tp(server, px + 0.5, y, pz + 0.5, 180.0F, 8.0F);
		context.waitTicks(20);

		// ---- Conjuro veloz, Tinta indeleble: numbers the spell reads
		server.runOnServer(s -> {
			var registries = connection.getServerLevel().registryAccess();
			ItemStack plain = magicWeapon(registries, ForgeType.BACULO);
			ItemStack quick = magicWeapon(registries, ForgeType.BACULO, Upgrade.CONJURO_VELOZ, 100);
			ItemStack quickTome = magicWeapon(registries, ForgeType.GRIMORIO, Upgrade.CONJURO_VELOZ, 100, Upgrade.TINTA_INDELEBLE, 100);
			int slow = dev.forja.magic.Spellcasting.cooldown(plain, ForgeType.BACULO);
			int fast = dev.forja.magic.Spellcasting.cooldown(quick, ForgeType.BACULO);
			int tome = dev.forja.magic.Spellcasting.cooldown(quickTome, ForgeType.GRIMORIO);
			log("mejoras magicas: Conjuro veloz deja la espera del baculo en " + fast + " de " + slow + " tics y la del grimorio en " + tome
				+ " de " + dev.forja.magic.Spellcasting.TOME_COOLDOWN + "; Tinta indeleble, la runa en " + dev.forja.magic.Spellcasting.runeTicks(quickTome) + " tics");
			check(slow == 14 && fast == 8 && tome == 42, "Conjuro veloz should take two fifths off the wait: " + slow + " -> " + fast + ", tome " + tome);
			check(dev.forja.magic.Spellcasting.runeTicks(quickTome) == 240, "Tinta indeleble at a hundred should double the rune's six seconds");
			for (Upgrade upgrade : List.of(Upgrade.CONJURO_VELOZ, Upgrade.SOBRECARGA, Upgrade.RESONANCIA)) {
				check(upgrade.appliesTo(ForgeType.BACULO) && upgrade.appliesTo(ForgeType.GRIMORIO) && !upgrade.appliesTo(ForgeType.ESPADA), upgrade + " is for the staff and the tome alone");
			}
			for (Upgrade upgrade : List.of(Upgrade.PRISMA, Upgrade.BUSCADOR)) {
				check(upgrade.appliesTo(ForgeType.BACULO) && !upgrade.appliesTo(ForgeType.GRIMORIO), upgrade + " is the staff's");
			}
			for (Upgrade upgrade : List.of(Upgrade.TINTA_INDELEBLE, Upgrade.VORTICE, Upgrade.SANTUARIO)) {
				check(upgrade.appliesTo(ForgeType.GRIMORIO) && !upgrade.appliesTo(ForgeType.BACULO), upgrade + " is the tome's");
			}
		});

		// ---- Prisma and Enjambre: how many bolts leave the staff
		int[] bolts = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var level = connection.getServerLevel();
			var registries = level.registryAccess();
			player.getInventory().clearContent();
			int[] seen = new int[3];
			ItemStack[] staffs = {magicWeapon(registries, ForgeType.BACULO), magicWeapon(registries, ForgeType.BACULO, Upgrade.PRISMA, 100),
				magicWeapon(registries, ForgeType.BACULO, Upgrade.PRISMA, 100, Upgrade.BUSCADOR, 100)};
			for (int i = 0; i < staffs.length; i++) {
				level.getEntitiesOfClass(dev.forja.entity.MagicBolt.class, player.getBoundingBox().inflate(80.0)).forEach(net.minecraft.world.entity.Entity::discard);
				castWith(player, staffs[i], ForgeType.BACULO);
				seen[i] = level.getEntitiesOfClass(dev.forja.entity.MagicBolt.class, player.getBoundingBox().inflate(80.0), bolt -> bolt.isAlive()).size();
			}
			level.getEntitiesOfClass(dev.forja.entity.MagicBolt.class, player.getBoundingBox().inflate(80.0)).forEach(net.minecraft.world.entity.Entity::discard);
			return seen;
		});
		log("mejoras magicas: proyectiles por disparo - sin nada " + bolts[0] + ", con Prisma " + bolts[1] + ", con Prisma y Buscador (Enjambre) " + bolts[2]);
		check(bolts[0] == 1 && bolts[1] == 3 && bolts[2] == 5, "one bolt, three with Prisma, five with Enjambre: " + bolts[0] + ", " + bolts[1] + ", " + bolts[2]);
		context.waitTicks(5);

		// ---- Sobrecarga: the fourth spell hits twice as hard. Resonancia: a bolt and its echo, half as much again.
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:iron_golem %d %d %d {NoAI:1b,Tags:[\"forja_diana\"]}", px, y, pz - 5));
		context.waitTicks(10);
		float[] taken = new float[4];
		for (int shot = 0; shot < 4; shot++) {
			float before = server.computeOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				var level = connection.getServerLevel();
				var target = level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, player.getBoundingBox().inflate(12.0)).getFirst();
				target.setHealth(target.getMaxHealth());
				ItemStack held = player.getMainHandItem();
				boolean charged = held.has(ModComponents.PARTS) && dev.forja.upgrade.Upgrades.fraction(held, Upgrade.SOBRECARGA) > 0.0F;
				castWith(player, charged ? held : magicWeapon(level.registryAccess(), ForgeType.BACULO, Upgrade.SOBRECARGA, 100), ForgeType.BACULO);
				return target.getHealth();
			});
			context.waitTicks(14);
			float after = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class,
				connection.getServerPlayer().getBoundingBox().inflate(12.0)).getFirst().getHealth());
			taken[shot] = before - after;
		}
		log("mejoras magicas: Sobrecarga - los cuatro hechizos quitan " + taken[0] + ", " + taken[1] + ", " + taken[2] + " y " + taken[3]);
		check(Math.abs(taken[0] - taken[2]) < 0.01F && taken[0] > 0.0F, "the first three spells are ordinary ones: " + taken[0] + ", " + taken[1] + ", " + taken[2]);
		check(Math.abs(taken[3] - taken[0] * 2.0F) < 0.05F, "and the fourth, overcharged at a hundred, should hit twice as hard: " + taken[3] + " against " + taken[0]);

		float echoBefore = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var level = connection.getServerLevel();
			var target = level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, player.getBoundingBox().inflate(12.0)).getFirst();
			target.setHealth(target.getMaxHealth());
			castWith(player, magicWeapon(level.registryAccess(), ForgeType.BACULO, Upgrade.RESONANCIA, 100), ForgeType.BACULO);
			return target.getHealth();
		});
		context.waitTicks(24);
		float echoAfter = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class,
			connection.getServerPlayer().getBoundingBox().inflate(12.0)).getFirst().getHealth());
		log("mejoras magicas: Resonancia - el proyectil y su eco quitan " + (echoBefore - echoAfter) + " (uno solo, " + taken[0] + ")");
		check(Math.abs((echoBefore - echoAfter) - taken[0] * 1.5F) < 0.05F, "a bolt and its echo at a half should come to one and a half bolts: " + (echoBefore - echoAfter));
		server.runCommand("kill @e[tag=forja_diana]");
		context.waitTicks(5);

		// ---- Buscador: a zombie well off the line. A plain bolt goes past it; a seeking one comes round.
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:zombie %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"forja_diana\"]}", px + 4, y, pz - 9));
		context.waitTicks(10);
		float[] seeking = new float[2];
		for (int pass = 0; pass < 2; pass++) {
			boolean seeks = pass == 1;
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				var level = connection.getServerLevel();
				ItemStack staff = seeks ? magicWeapon(level.registryAccess(), ForgeType.BACULO, Upgrade.BUSCADOR, 100) : magicWeapon(level.registryAccess(), ForgeType.BACULO);
				castWith(player, staff, ForgeType.BACULO);
			});
			context.waitTicks(20);
			seeking[pass] = server.computeOnServer(s -> {
				var zombies = connection.getServerLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
					connection.getServerPlayer().getBoundingBox().inflate(16.0));
				return zombies.isEmpty() ? 0.0F : zombies.getFirst().getHealth();
			});
		}
		log("mejoras magicas: Buscador - el zombi a un lado queda en " + seeking[0] + " sin la mejora y en " + seeking[1] + " con ella");
		check(seeking[0] == 20.0F, "a plain bolt thrown straight ahead should go past a zombie four blocks off the line, it is at " + seeking[0]);
		check(seeking[1] < seeking[0], "and a seeking one should come round and hit it");
		server.runCommand("kill @e[tag=forja_diana]");
		context.waitTicks(5);

		// ---- the tome: Vortice drags to the middle, Santuario shelters the reader, Colapso ends it with a bang
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:zombie %d %d %d {PersistenceRequired:1b,Tags:[\"forja_diana\"],attributes:[{id:\"minecraft:max_health\",base:200.0}],Health:200.0f}",
			px + 2, y, pz - 4));
		context.waitTicks(10);
		double[] far = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var level = connection.getServerLevel();
			ItemStack tome = magicWeapon(level.registryAccess(), ForgeType.GRIMORIO, Upgrade.VORTICE, 100, Upgrade.TINTA_INDELEBLE, 50, Upgrade.SANTUARIO, 100);
			check(dev.forja.upgrade.Synergy.COLAPSO.active(tome), "Vortice and Tinta indeleble at fifty should wake Colapso");
			net.minecraft.world.phys.Vec3 at = dev.forja.magic.Spellcasting.target(level, player);
			var zombie = level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, player.getBoundingBox().inflate(16.0)).getFirst();
			zombie.teleportTo(at.x + 2.4, at.y, at.z);
			castWith(player, tome, ForgeType.GRIMORIO);
			return new double[] {2.4, at.x, at.y, at.z, dev.forja.magic.Spellcasting.longestRune()};
		});
		check(far[4] >= 175 && far[4] <= 180, "with Tinta indeleble at fifty the rune should last nine seconds, has " + far[4] + " ticks");
		// the reader steps onto their own rune
		tp(server, far[1], far[2], far[3], 180.0F, 30.0F);
		context.waitTicks(45);
		double[] dragged = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var zombie = connection.getServerLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, player.getBoundingBox().inflate(16.0)).getFirst();
			double dx = zombie.getX() - far[1];
			double dz = zombie.getZ() - far[3];
			return new double[] {Math.sqrt(dx * dx + dz * dz), player.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE) ? 1.0 : 0.0};
		});
		log("mejoras magicas: Vortice - el zombi empezo a 2.40 del centro de la runa y a los dos segundos esta a " + String.format(Locale.ROOT, "%.2f", dragged[0])
			+ "; Santuario - el lector sobre su runa tiene Resistencia: " + (dragged[1] == 1.0));
		check(dragged[0] < 1.4, "Vortice should have dragged the zombie towards the middle of the rune, it is " + dragged[0] + " from it");
		check(dragged[1] == 1.0, "and Santuario should be sheltering the reader who stands on their own rune");
		// wait for the end of the rune, and look either side of it
		float[] ending = new float[2];
		for (int guard = 0; guard < 220; guard++) {
			int left = server.computeOnServer(s -> dev.forja.magic.Spellcasting.longestRune());
			if (left <= 4) {
				break;
			}
			context.waitTicks(1);
		}
		ending[0] = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
			connection.getServerPlayer().getBoundingBox().inflate(16.0)).getFirst().getHealth());
		context.waitTicks(9);
		ending[1] = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
			connection.getServerPlayer().getBoundingBox().inflate(16.0)).getFirst().getHealth());
		float opening = dev.forja.magic.Spellcasting.areaDamage(DIAMANTE);
		log("mejoras magicas: Colapso - al apagarse la runa el zombi pierde " + (ending[0] - ending[1]) + " (el area se abrio con " + opening + ")");
		check(Math.abs((ending[0] - ending[1]) - opening * dev.forja.magic.Spellcasting.COLLAPSE_SHARE) < 0.05F,
			"the rune going out should be worth three quarters of its opening, took " + (ending[0] - ending[1]));
		check(server.computeOnServer(s -> dev.forja.magic.Spellcasting.runes()) == 0, "and then it is gone");

		server.runCommand("kill @e[tag=forja_diana]");
		server.runCommand("difficulty peaceful");
		server.runCommand("time set 6000");
		server.runOnServer(s -> {
			connection.getServerPlayer().getInventory().clearContent();
			connection.getServerPlayer().removeAllEffects();
		});
		server.runCommand("gamemode survival @a");
		tp(server, x + 90.5, y, z + 75.5, 0.0F, 0.0F);
		context.waitTicks(20);
	}

	/**
	 * The great castle, photographed: what tools/castillo.py wrote, stood up in the world and looked at
	 * from fixed places, by day and after dark. Pictures only - it is how the building is judged.
	 */
	private static void filmBastion(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int bx = x + 400;
		int bz = z + 400;
		// A spectator's flight widens the lens; pictures of a building want it still and ordinary.
		context.runOnClient(mc -> {
			mc.options.fov().set(70);
			mc.options.fovEffectScale().set(0.0);
		});
		server.runCommand("gamemode spectator @a");
		server.runCommand("weather clear");
		server.runCommand("time set 6000");
		tp(server, bx + 24.5, y + 9, bz + 47.5, 180.0F, 6.0F);
		context.waitTicks(60);
		server.runCommand(String.format(Locale.ROOT, "place template forja:bastion/muestra_exterior %d %d %d", bx, y - 1, bz));
		context.waitTicks(30);
		quiet(context);
		context.waitTicks(5);
		String[] names = {"frente", "tres_cuartos", "brecha", "adarve", "torre"};
		double[][] places = {
			{24.5, 9, 47.5, 180.0, 6.0},
			{-6.5, 14, 40.5, -131.0, 8.0},
			{12.5, 3, 30.5, 180.0, -12.0},
			{2.5, 15.7, 12.5, -90.0, 14.0},
			{58.5, 20, 30.5, 132.0, -6.0},
		};
		for (int i = 0; i < names.length; i++) {
			tp(server, bx + places[i][0], y + places[i][1], bz + places[i][2], (float) places[i][3], (float) places[i][4]);
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("forja_50_bastion_" + names[i]).disableCounterPrefix());
		}
		server.runCommand("time set 14500");
		for (int i : new int[] {1, 4}) {
			tp(server, bx + places[i][0], y + places[i][1], bz + places[i][2], (float) places[i][3], (float) places[i][4]);
			context.waitTicks(30);
			context.takeScreenshot(TestScreenshotOptions.of("forja_50_bastion_noche_" + names[i]).disableCounterPrefix());
		}
		server.runCommand("time set 6000");
		if ("sotanos".equals(System.getenv("FORJA_CASTILLO"))) {
			filmBastionCellars(context, server, connection, x, y, z);
		} else {
			filmWholeBastion(context, server, connection, x, y, z);
		}
		server.runCommand("gamemode survival @a");
		tp(server, x + 90.5, y, z + 75.5, 0.0F, 0.0F);
		context.waitTicks(20);
	}

	/**
	 * The whole castle, built the way the world builds it (every jigsaw piece finding its neighbour), found
	 * again by the lodestone its start piece carries, and photographed from places given in the plan's own
	 * coordinates whichever way the game has turned it.
	 */
	private static void filmWholeBastion(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		// a jigsaw structure starts at the corner of the chunk it is asked for, not at the block
		int cx = Math.floorDiv(x + 1500, 16) * 16;
		int cz = Math.floorDiv(z + 1500, 16) * 16;
		context.runOnClient(mc -> {
			// Andy: "pon el renderizado a 32 chunks, que no ves bien lo que estas haciendo". The castle is thirteen
			// chunks across: at sixteen the far wall of it is the edge of the world from most of where it is looked at.
			mc.options.renderDistance().set(32);
			mc.options.simulationDistance().set(12);
			// written down too: Andy looked in the run's options and found the old sixteen there
			mc.options.save();
		});
		context.waitTicks(10);
		int[] seen = context.computeOnClient(mc -> new int[] {mc.options.renderDistance().get(), mc.options.getEffectiveRenderDistance()});
		int served = server.computeOnServer(s -> s.getPlayerList().getViewDistance());
		log("castillo: distancia de dibujado pedida " + seen[0] + ", efectiva " + seen[1] + ", la del servidor " + served);
		tp(server, cx + 0.5, y + 140, cz + 0.5, 0.0F, 90.0F);
		context.waitTicks(260);
		// On peaceful the castle's garrison is gone the tick it is placed; a spectator is nobody's target.
		server.runCommand("difficulty easy");
		server.runCommand(String.format(Locale.ROOT, "place structure forja:bastion_prueba %d %d %d", cx, y, cz));
		context.waitTicks(60);
		int[] found = server.computeOnServer(s -> {
			var level = connection.getServerLevel();
			for (int dy = -60; dy <= 20; dy++) {
				int[][] spots = {{2, 0}, {0, 2}, {-2, 0}, {0, -2}};
				for (int turn = 0; turn < 4; turn++) {
					BlockPos at = new BlockPos(cx + spots[turn][0], y + dy, cz + spots[turn][1]);
					if (level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.LODESTONE)) {
						return new int[] {turn, y + dy};
					}
				}
			}
			// Not where it was looked for: say where it is, so the looking can be put right.
			for (int dx = -60; dx <= 60; dx++) {
				for (int dz = -60; dz <= 60; dz++) {
					for (int dy = -20; dy <= 10; dy++) {
						if (level.getBlockState(new BlockPos(cx + dx, y + dy, cz + dz)).is(net.minecraft.world.level.block.Blocks.LODESTONE)) {
							log("castillo: la piedra iman esta en +" + dx + ", " + (y + dy) + ", +" + dz + " respecto al punto pedido");
						}
					}
				}
			}
			return new int[] {-1, 0};
		});
		if (found[0] < 0) {
			log("castillo: NO ENCUENTRO la pieza central; fotografio a ciegas");
		}
		int turn = Math.max(0, found[0]);
		int bottom = found[0] < 0 ? y - 4 : found[1] + 2 + BastionLayout.START_Y;
		log("castillo: girado " + turn + " cuartos, base de la pieza central en y=" + bottom);
		// What the server has, whatever the client manages to draw: one block of every part of the curtain.
		int[][] probes = {{100, 12, 2}, {2, 12, 100}, {199, 12, 60}, {199, 12, 160}, {100, 12, 199}, {20, 12, 199}, {180, 12, 199},
			{3, 20, 3}, {198, 20, 3}, {3, 20, 198}, {198, 20, 198}, {100, 40, 40}, {60, 10, 60}, {140, 10, 80}, {100, -1, 160}, {20, -1, 100}, {180, -1, 100}};
		StringBuilder have = new StringBuilder();
		for (int[] probe : probes) {
			double[] at = bastionToWorld(probe[0], probe[1], probe[2], turn, cx, bottom, cz);
			BlockPos pos = BlockPos.containing(at[0], at[1], at[2]);
			String id = server.computeOnServer(s -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(connection.getServerLevel().getBlockState(pos).getBlock()).getPath());
			have.append(probe[0]).append(',').append(probe[1]).append(',').append(probe[2]).append('=').append(id).append("  ");
		}
		log("castillo: sondas " + have);
		// name, camera (plan x, y, z), target (plan x, y, z), night
		Object[][] shots = {
			{"aerea_cenital", 100.0, 190.0, 118.0, 100.0, 0.0, 110.0, false},
			{"aerea_sur", 100.0, 90.0, 300.0, 100.0, 5.0, 120.0, false},
			{"aerea_sureste", 262.0, 80.0, 262.0, 110.0, 5.0, 110.0, false},
			{"puerta", 100.5, 7.0, 262.0, 100.5, 12.0, 205.0, false},
			{"recinto_bajo", 60.0, 16.0, 176.0, 100.0, 14.0, 124.0, false},
			{"patio", 100.0, 34.0, 112.0, 100.0, 6.0, 40.0, false},
			{"brecha", 246.0, 10.0, 118.0, 201.0, 5.0, 123.0, false},
			{"adarve", 150.0, 17.0, 198.5, 60.0, 14.0, 198.5, false},
			{"noche_sureste", 262.0, 80.0, 262.0, 110.0, 5.0, 110.0, true},
			{"noche_puerta", 100.5, 7.0, 262.0, 100.5, 12.0, 205.0, true},
			{"torre_homenaje", 100.0, 22.0, 108.0, 100.0, 30.0, 70.0, false},
			{"alas", 100.0, 40.0, 60.0, 142.0, 8.0, 80.0, false},
			{"casas", 150.0, 18.0, 132.0, 150.0, 4.0, 160.0, false},
			{"int_gran_salon", 100.5, 4.0, 66.0, 100.5, 6.0, 40.0, false},
			{"int_gran_salon_galeria", 79.0, 11.5, 64.0, 118.0, 6.0, 44.0, false},
			{"int_sala_de_cunos", 100.5, 4.5, 36.5, 100.5, 1.5, 22.0, false},
			{"int_fundicion", 135.0, 11.0, 94.0, 148.0, 1.0, 64.0, false},
			{"int_fundicion_suelo", 148.0, 3.0, 92.0, 138.0, 1.0, 66.0, false},
			{"int_taller", 134.5, 4.0, 110.0, 148.0, 1.0, 103.0, false},
			{"int_biblioteca", 66.5, 4.5, 43.0, 52.0, 1.5, 28.0, false},
			{"int_scriptorium", 67.0, 4.0, 55.5, 52.0, 1.0, 47.0, false},
			{"int_armeria", 59.5, 4.5, 77.5, 59.5, 1.5, 60.0, false},
			{"int_estandartes", 66.5, 5.0, 89.5, 52.0, 3.0, 81.0, false},
			{"int_cuartel", 66.5, 4.5, 109.0, 52.0, 1.0, 94.0, false},
			{"int_esgrima", 66.5, 13.0, 77.0, 52.0, 10.0, 60.0, false},
			{"int_dormitorios", 59.5, 13.5, 109.0, 59.5, 10.0, 93.0, false},
			{"int_capilla", 134.0, 5.0, 34.5, 150.0, 4.0, 34.5, false},
			{"int_aleaciones", 126.5, 4.5, 104.5, 116.0, 1.0, 114.0, false},
			{"int_comedor", 75.0, 4.5, 104.5, 85.0, 1.0, 115.0, false},
			{"int_trono", 100.5, 13.0, 36.5, 100.5, 11.0, 20.0, false},
			{"int_consejo", 112.0, 23.0, 36.0, 100.0, 19.0, 28.0, false},
			{"int_trofeos", 98.0, 22.5, 66.0, 78.0, 19.0, 42.0, false},
			{"int_mapas", 102.0, 22.5, 66.0, 122.0, 19.0, 44.0, false},
			{"int_aposentos", 112.0, 31.5, 36.0, 99.0, 28.0, 21.0, false},
			{"int_contaduria", 102.0, 31.5, 40.0, 114.0, 28.0, 66.0, false},
			{"int_observatorio", 94.0, 50.0, 30.0, 102.0, 47.0, 40.0, false},
			{"int_almacen", 78.0, 40.5, 66.0, 116.0, 37.0, 42.0, false},
			{"int_taberna", 143.5, 4.0, 166.5, 120.0, 1.0, 158.0, false},
			{"int_encargos", 191.5, 4.0, 166.5, 156.0, 1.0, 158.0, false},
			{"int_caballerizas", 42.5, 4.0, 174.0, 12.0, 1.0, 182.0, false},
			{"int_guardia", 139.5, 4.0, 187.5, 122.0, 1.0, 193.0, false},
			{"aerea_cenital_tarde", 100.0, 190.0, 118.0, 100.0, 0.0, 110.0, false},
		};
		boolean dark = false;
		for (Object[] shot : shots) {
			if ((boolean) shot[7] != dark) {
				dark = (boolean) shot[7];
				server.runCommand(dark ? "time set 15000" : "time set 6000");
				context.waitTicks(20);
			}
			double[] eye = bastionToWorld((double) shot[1], (double) shot[2], (double) shot[3], turn, cx, bottom, cz);
			double[] aim = bastionToWorld((double) shot[4], (double) shot[5], (double) shot[6], turn, cx, bottom, cz);
			double dx = aim[0] - eye[0];
			double dy = aim[1] - eye[1];
			double dz = aim[2] - eye[2];
			float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
			float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			tp(server, eye[0], eye[1] - 1.62, eye[2], yaw, pitch);
			context.waitTicks(20);
			// From the air the whole castle is in the picture, and a picture taken before the far chunks are
			// drawn is a picture of a castle with its edges missing. Waited for properly, however long it takes.
			context.waitTicks(((String) shot[0]).startsWith("aerea") ? 160 : 80);
			quiet(context);
			context.takeScreenshot(TestScreenshotOptions.of("forja_51_castillo_" + shot[0]).disableCounterPrefix());
		}
		server.runCommand("time set 6000");
		server.runCommand("difficulty peaceful");
		context.runOnClient(mc -> mc.options.renderDistance().set(12));
	}

	/**
	 * The rooms under the castle. The flat test world is three blocks deep, so the pieces that hold them are
	 * stood on the ground instead (the courtyard ends up twenty-six blocks in the air) and walked into:
	 * a cellar looks the same from inside wherever it is.
	 */
	private static void filmBastionCellars(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int bx = Math.floorDiv(x + 2600, 16) * 16;
		int bz = Math.floorDiv(z + 2600, 16) * 16;
		context.runOnClient(mc -> mc.options.gamma().set(1.0));
		// the pieces under the upper ward: columns 1 to 3, rows 0 to 3, bottom layer
		for (int i = 0; i <= 4; i++) {
			for (int k = 0; k <= 3; k++) {
				int px = bx + i * 48;
				int pz = bz + k * 48;
				tp(server, px + 24.5, y + 80, pz + 24.5, 0.0F, 90.0F);
				context.waitTicks(30);
				server.runCommand(String.format(Locale.ROOT, "place template forja:bastion/p_%d_0_%d %d %d %d", i, k, px, y, pz));
			}
		}
		context.waitTicks(40);
		// name, eye (plan x, y, z), target (plan x, y, z)
		Object[][] shots = {
			{"cripta", 100.0, -6.0, 66.0, 100.0, -7.0, 20.0},
			{"temple", 78.0, -5.0, 86.0, 124.0, -8.0, 86.0},
			{"temple_estanques", 100.0, -4.0, 97.0, 100.0, -10.0, 80.0},
			{"mazmorras", 59.0, -6.5, 110.0, 59.0, -7.5, 72.0},
			{"carbonera", 143.5, -6.5, 94.5, 142.0, -7.5, 58.0},
			{"antesala", 100.0, -20.0, 60.0, 100.0, -22.0, 42.0},
			{"fragua_profunda", 100.0, -17.0, 114.0, 100.0, -24.0, 90.0},
			{"fragua_profunda_estrella", 100.0, -13.5, 92.5, 100.0, -25.0, 92.0},
			{"forja_de_almas", 133.0, -21.0, 102.0, 152.0, -23.0, 82.0},
			{"camara_acorazada", 50.0, -21.5, 102.0, 70.0, -23.0, 82.0},
		};
		for (Object[] shot : shots) {
			double ex = bx + ((double) shot[1] - BastionLayout.ORIGIN_X);
			double ey = y + ((double) shot[2] - BastionLayout.ORIGIN_Y);
			double ez = bz + ((double) shot[3] - BastionLayout.ORIGIN_Z);
			double dx = (double) shot[4] - (double) shot[1];
			double dy = (double) shot[5] - (double) shot[2];
			double dz = (double) shot[6] - (double) shot[3];
			float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
			float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			tp(server, ex, ey - 1.62, ez, yaw, pitch);
			context.waitTicks(70);
			quiet(context);
			context.takeScreenshot(TestScreenshotOptions.of("forja_52_sotano_" + shot[0]).disableCounterPrefix());
		}
		context.runOnClient(mc -> mc.options.gamma().set(0.5));
	}

	/**
	 * The one thing the flat test world cannot say: whether the great castle generates by itself, on real ground. A
	 * world like any player's, the nearest Bastion found the way the Forjador's map finds it, and looked at.
	 */
	private static void visitBastionInTheWild(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().setUseConsistentSettings(false).create()) {
			TestServerConnection connection = singleplayer.getConnection();
			TestServerContext server = singleplayer.getServer();
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(32);
				mc.options.simulationDistance().set(8);
				mc.options.save();
			});
			server.runCommand("gamemode spectator @a");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamerule spawn_mobs false");
			context.waitTicks(40);
			long started = System.currentTimeMillis();
			int[] found = server.computeOnServer(s -> {
				var level = connection.getServerLevel();
				var tag = net.minecraft.tags.TagKey.create(Registries.STRUCTURE, dev.forja.Forja.id("on_bastion_del_gremio"));
				BlockPos at = level.findNearestMapStructure(tag, connection.getServerPlayer().blockPosition(), 200, false);
				return at == null ? null : new int[] {at.getX(), at.getZ()};
			});
			check(found != null, "no Bastion within 200 chunks of spawn: the set, its biomes or its exclusion zone are wrong");
			log("mundo: el Bastion mas cercano esta en x=" + found[0] + " z=" + found[1] + " (buscarlo costo " + (System.currentTimeMillis() - started) + " ms)");
			// over its start first, so that the whole of it is asked for and built
			tp(server, found[0] + 0.5, 200.0, found[1] + 0.5, 0.0F, 90.0F);
			context.waitTicks(600);
			int[] ground = server.computeOnServer(s -> {
				var level = connection.getServerLevel();
				int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, found[0], found[1]);
				String biome = level.getBiome(new BlockPos(found[0], top, found[1])).unwrapKey().map(key -> key.identifier().getPath()).orElse("?");
				log("mundo: bioma " + biome + ", superficie en y=" + top);
				int lanterns = 0;
				for (BlockPos pos : BlockPos.betweenClosed(found[0] - 40, top - 10, found[1] - 40, found[0] + 40, top + 30, found[1] + 40)) {
					if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LANTERN)) {
						lanterns++;
					}
				}
				return new int[] {top, lanterns};
			});
			log("mundo: faroles en 80x80 alrededor del inicio: " + ground[1]);
			check(ground[1] > 10, "the castle should be standing where the map says, found " + ground[1] + " lanterns");
			Object[][] shots = {
				{"cenital", 0.0, 230.0, 0.5, 90.0F, 0.0F},
				{"sur", 0.0, 95.0, 210.0, 28.0F, 180.0F},
				{"este", 210.0, 95.0, 0.0, 28.0F, 90.0F},
				{"norte", 0.0, 95.0, -210.0, 28.0F, 0.0F},
				{"oeste", -210.0, 95.0, 0.0, 28.0F, -90.0F},
			};
			for (Object[] shot : shots) {
				tp(server, found[0] + (double) shot[1], ground[0] + (double) shot[2],
					found[1] + (double) shot[3], (float) shot[5], (float) shot[4]);
				context.waitTicks(300);
				quiet(context);
				context.takeScreenshot(TestScreenshotOptions.of("forja_53_mundo_" + shot[0]).disableCounterPrefix());
			}
		}
	}

	/** A place in the castle's plan, as a place in the world: the plan turned about the start piece's corner. */
	private static double[] bastionToWorld(double px, double py, double pz, int turn, int startX, int bottomY, int startZ) {
		double lx = px - BastionLayout.START_X;
		double lz = pz - BastionLayout.START_Z;
		for (int i = 0; i < turn; i++) {
			double was = lx;
			lx = -lz;
			lz = was;
		}
		return new double[] {startX + lx, bottomY + (py - BastionLayout.START_Y), startZ + lz};
	}

	/**
	 * The extraction table: one upgrade off a piece, into an orb or into nothing, and the rest of the piece
	 * exactly as it was. Clicked with the real cursor as well as asked of the menu, because a list of rows
	 * and a button are the whole of how a player reaches it.
	 */
	private static void checkExtraction(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		BlockPos table = new BlockPos(x + 7, y, z - 6);
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_extraccion", table.getX(), table.getY(), table.getZ()));
		server.runCommand("gamemode creative @a");
		// The block's portrait first, from a step up and to one side: its top is the wheel the screen opens
		// onto, and only from above does it show. The step is a barrier, so nothing stands in the picture.
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d barrier", table.getX() + 1, table.getY(), table.getZ() - 1));
		tp(server, x + 8.7, y + 1, z - 7.0, 38.7F, 42.0F);
		context.waitTicks(8);
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.waitTicks(2);
		context.takeScreenshot(TestScreenshotOptions.of("forja_41_extraccion_bloque").disableCounterPrefix());
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d air", table.getX() + 1, table.getY(), table.getZ() - 1));
		tp(server, x + 7.5, y, z - 8.5, 0.0F, 20.0F);
		context.waitTicks(5);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var state = connection.getServerLevel().getBlockState(table);
			player.openMenu(state.getMenuProvider(connection.getServerLevel(), table));
			check(player.containerMenu instanceof dev.forja.menu.ExtractionMenu, "the extraction table should open its own menu");
			var menu = (dev.forja.menu.ExtractionMenu) player.containerMenu;
			var registries = connection.getServerLevel().registryAccess();
			ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(DIAMANTE, MADERA, ORO), registries);
			sword.set(ModComponents.POTENCIAL, 80);
			dev.forja.forge.Quality.markPerfect(sword);
			dev.forja.forge.Mastery.setLevel(sword, 4, registries);
			sword = UpgradeRecipes.upgraded(sword, ForgeType.ESPADA, Upgrade.FILO, 80, registries);
			sword = UpgradeRecipes.upgraded(sword, ForgeType.ESPADA, Upgrade.IRROMPIBLE, 60, registries);
			sword = UpgradeRecipes.upgraded(sword, ForgeType.ESPADA, Upgrade.PACTO_DE_VIDRIO, 100, registries);
			menu.getSlot(dev.forja.menu.ExtractionMenu.GEAR_SLOT).set(sword);
			// Filo at eighty is four steps of its recipe: four amethyst shards.
			menu.getSlot(dev.forja.menu.ExtractionMenu.PAYMENT_FIRST).set(new ItemStack(Items.AMETHYST_SHARD, 10));
			menu.getSlot(dev.forja.menu.ExtractionMenu.ORB_SLOT).set(new ItemStack(dev.forja.registry.ModItems.ORBE_VACIO, 2));
		});
		context.waitTicks(10);
		context.getInput().setCursorPos(0, 0);
		String off = context.computeOnClient(mc -> slotsOffTheirSquares(mc, "textures/gui/mesa_de_extraccion.png", 0x101016, 0x22222A));
		check(off.equals("[]"), "every extraction table slot should sit on a square of its panel, off: " + off);
		// The pact is in the list and cannot be chosen; Filo can. Find both rows on the client's own list.
		int[] rows = context.computeOnClient(mc -> {
			var menu = ((dev.forja.client.ExtractionScreen) mc.gui.screen()).getMenu();
			return new int[] {menu.upgrades().indexOf(Upgrade.FILO), menu.upgrades().indexOf(Upgrade.PACTO_DE_VIDRIO), menu.upgrades().size()};
		});
		check(rows[0] >= 0 && rows[1] >= 0 && rows[2] == 3, "the list should show all three upgrades, got " + rows[2]);
		boolean picked = context.computeOnClient(mc -> {
			var screen = (dev.forja.client.ExtractionScreen) mc.gui.screen();
			return screen.clickAt(screen.gemPoint(rows[0]));
		});
		check(picked, "clicking Filo's gem should choose it");
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.waitTicks(6);
		context.takeScreenshot(TestScreenshotOptions.of("forja_41_extraccion_elegida").disableCounterPrefix());
		boolean pressed = context.computeOnClient(mc -> {
			var screen = (dev.forja.client.ExtractionScreen) mc.gui.screen();
			return screen.clickAt(screen.buttonPoint());
		});
		check(pressed, "the button should take the click once the price is on the table");
		// The gem leaves its socket and crosses to the cradle: filmed, a frame a tick.
		boolean inTheAir = context.computeOnClient(mc -> ((dev.forja.client.ExtractionScreen) mc.gui.screen()).flying());
		check(inTheAir, "the extracted gem should fly to the cradle");
		for (int frame = 0; frame < 10; frame++) {
			context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_41f_vuelo_%02d", frame)).disableCounterPrefix());
			context.waitTicks(1);
		}
		context.waitTicks(8);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		context.takeScreenshot(TestScreenshotOptions.of("forja_41_extraccion_hecha").disableCounterPrefix());
		int[] done = server.computeOnServer(s -> {
			var menu = (dev.forja.menu.ExtractionMenu) connection.getServerPlayer().containerMenu;
			ItemStack sword = menu.getSlot(dev.forja.menu.ExtractionMenu.GEAR_SLOT).getItem();
			Upgrades left = sword.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
			var orb = dev.forja.item.UpgradeOrbItem.orb(menu.getSlot(dev.forja.menu.ExtractionMenu.OUTPUT_SLOT).getItem());
			int sharpness = EnchantmentHelper.getItemEnchantmentLevel(connection.getServerLevel().registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), sword);
			return new int[] {
				left.percent(Upgrade.FILO), left.percent(Upgrade.IRROMPIBLE), left.percent(Upgrade.PACTO_DE_VIDRIO),
				orb == null ? -1 : orb.percent(), orb != null && orb.upgrade() == Upgrade.FILO ? 1 : 0,
				menu.getSlot(dev.forja.menu.ExtractionMenu.PAYMENT_FIRST).getItem().getCount(),
				menu.getSlot(dev.forja.menu.ExtractionMenu.ORB_SLOT).getItem().getCount(),
				dev.forja.forge.Mastery.level(sword), dev.forja.forge.Quality.perfect(sword) ? 1 : 0, sharpness,
				sword.getOrDefault(ModComponents.POTENCIAL, -1)};
		});
		log("extraccion: Filo queda en " + done[0] + ", Irrompible " + done[1] + ", pacto " + done[2] + "; orbe de Filo " + done[3]
			+ "%; quedan " + done[5] + " amatistas de 10 y " + done[6] + " orbes vacios de 2; maestria " + done[7] + ", perfecta " + (done[8] == 1)
			+ ", Filo oculto nivel " + done[9]);
		check(done[0] == 0 && done[9] == 0, "Filo should be gone from the piece, and its hidden enchantment with it");
		check(done[1] == 60 && done[2] == 100, "and nothing else on it touched");
		check(done[3] == 80 && done[4] == 1, "the orb should hold all of it, got " + done[3]);
		check(done[5] == 6 && done[6] == 1, "four shards and one empty orb should have been spent, left " + done[5] + " and " + done[6]);
		check(done[7] == 4 && done[8] == 1 && done[10] == 80, "mastery, quality and potential are the piece's and stay with it");

		// The pact will not come off, by click or by packet, and without an orb the button erases.
		int[] more = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var menu = (dev.forja.menu.ExtractionMenu) player.containerMenu;
			int pactRow = menu.upgrades().indexOf(Upgrade.PACTO_DE_VIDRIO);
			menu.clickMenuButton(player, pactRow);
			boolean refused = !menu.clickMenuButton(player, dev.forja.menu.ExtractionMenu.BUTTON_EXTRACT);
			// Irrompible at sixty is three obsidian; with the output still full it must wait, and then with no orb it erases.
			menu.getSlot(dev.forja.menu.ExtractionMenu.PAYMENT_FIRST).set(new ItemStack(Items.OBSIDIAN, 3));
			menu.clickMenuButton(player, menu.upgrades().indexOf(Upgrade.IRROMPIBLE));
			boolean waits = !menu.clickMenuButton(player, dev.forja.menu.ExtractionMenu.BUTTON_EXTRACT);
			menu.getSlot(dev.forja.menu.ExtractionMenu.OUTPUT_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(dev.forja.menu.ExtractionMenu.ORB_SLOT).set(ItemStack.EMPTY);
			boolean erased = menu.clickMenuButton(player, dev.forja.menu.ExtractionMenu.BUTTON_EXTRACT);
			ItemStack sword = menu.getSlot(dev.forja.menu.ExtractionMenu.GEAR_SLOT).getItem();
			int unbreaking = sword.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.IRROMPIBLE);
			boolean nothingOut = menu.getSlot(dev.forja.menu.ExtractionMenu.OUTPUT_SLOT).getItem().isEmpty();
			menu.getSlot(dev.forja.menu.ExtractionMenu.GEAR_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(dev.forja.menu.ExtractionMenu.PAYMENT_FIRST).set(ItemStack.EMPTY);
			player.closeContainer();
			return new int[] {refused ? 1 : 0, waits ? 1 : 0, erased ? 1 : 0, unbreaking, nothingOut ? 1 : 0};
		});
		log("extraccion: pacto rechazado " + (more[0] == 1) + ", espera con la salida llena " + (more[1] == 1) + ", sin orbe borra " + (more[2] == 1)
			+ " (Irrompible " + more[3] + ", sale algo " + (more[4] == 0) + ")");
		check(more[0] == 1, "a pact must not come off");
		check(more[1] == 1, "with an orb still in the output the table should wait");
		check(more[2] == 1 && more[3] == 0 && more[4] == 1, "with no empty orb the upgrade is erased and nothing comes out");
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d air", table.getX(), table.getY(), table.getZ()));
		server.runCommand("gamemode survival @a");
	}

	/**
	 * Every name and description the mod builds from an id at run time, asked for and read.
	 *
	 * <p>The language generator checks the keys the code names outright, and cannot check the ones the
	 * code assembles — {@code "trait.forja." + id + ".largo"} passes as long as one trait has a long
	 * text. Two did not, three sky events had no description in the guide, and in each case the game
	 * printed the key at the player. This asks every enum for every text it can produce and looks for
	 * anything that came back still shaped like a key.
	 */
	private static void checkEverythingIsNamed(ClientGameTestContext context) {
		String raw = context.computeOnClient(mc -> {
			List<Component> said = new ArrayList<>();
			for (Upgrade upgrade : Upgrade.values()) {
				said.add(upgrade.displayName());
				said.add(upgrade.effect(50));
			}
			for (dev.forja.material.ForgeMaterial material : dev.forja.material.ForgeMaterial.values()) {
				said.add(material.displayName());
				said.add(Component.translatable("conjunto.forja." + material.getSerializedName()));
			}
			for (dev.forja.material.ForgeMaterial.Trait trait : dev.forja.material.ForgeMaterial.Trait.values()) {
				if (trait != dev.forja.material.ForgeMaterial.Trait.NONE) {
					said.add(trait.displayName());
					said.add(trait.description());
					said.add(Component.translatable("trait.forja." + trait.id() + ".largo"));
				}
			}
			for (PartType part : PartType.values()) {
				said.add(part.displayName());
				said.add(Component.translatable("part.forja." + part.id() + ".de", "x"));
				said.add(Component.translatable("gui.forja.rol." + part.role.name().toLowerCase(Locale.ROOT)));
			}
			for (ForgeType type : ForgeType.values()) {
				said.add(Component.translatable("item.forja." + type.id()));
				said.add(Component.translatable("item.forja." + type.id() + ".de", "x"));
			}
			for (dev.forja.forge.Perk perk : dev.forja.forge.Perk.values()) {
				said.add(perk.displayName());
				said.add(perk.description());
			}
			for (dev.forja.forge.Technique technique : dev.forja.forge.Technique.values()) {
				said.add(technique.displayName());
				said.add(technique.description());
			}
			for (dev.forja.forge.Temple temple : dev.forja.forge.Temple.values()) {
				said.add(temple.displayName());
				said.add(temple.description());
			}
			for (dev.forja.item.Talisman talisman : dev.forja.item.Talisman.values()) {
				said.add(talisman.displayName());
				said.add(talisman.description());
			}
			for (dev.forja.upgrade.Synergy synergy : dev.forja.upgrade.Synergy.values()) {
				said.add(synergy.displayName());
				said.add(synergy.description());
			}
			for (dev.forja.world.Legends.Legend legend : dev.forja.world.Legends.ALL) {
				said.add(Component.translatable("legend.forja." + legend.id()));
			}
			for (dev.forja.world.WorldEvents event : dev.forja.world.WorldEvents.values()) {
				said.add(event.displayName());
				said.add(Component.translatable("gui.forja.libro.evento." + event.id()));
			}
			for (dev.forja.forge.Alloys.Heat heat : dev.forja.forge.Alloys.Heat.values()) {
				said.add(heat.displayName());
			}
			for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.forge.Alloys.POURABLE) {
				said.add(recipe.displayName());
			}
			for (Station station : Station.values()) {
				said.add(station.title());
			}
			java.util.regex.Pattern key = java.util.regex.Pattern.compile("[a-zA-Z_]+\\.forja\\.[a-z0-9_.]+");
			java.util.Set<String> found = new java.util.TreeSet<>();
			for (Component text : said) {
				java.util.regex.Matcher matcher = key.matcher(text.getString());
				while (matcher.find()) {
					found.add(matcher.group());
				}
			}
			return said.size() + "|" + found;
		});
		String[] parts = raw.split("\\|", 2);
		log("nombres y descripciones: " + parts[0] + " textos pedidos, sin traducir " + parts[1]);
		check("[]".equals(parts[1]), "every name the mod builds from an id needs its text, missing: " + parts[1]);
	}

	/**
	 * Steam and molten beads, filmed: an automaton venting, and beads of three metals falling side by
	 * side. Also the one thing about them a picture cannot say — that the client has a behaviour and a
	 * sprite for both, because a particle type registered without either is an exception the first time
	 * anything throws one, in somebody's world and not here.
	 */
	private static void filmParticles(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		// Near the screens' corner of the world, not off on its own. The first place this was filmed was
		// 190 blocks from the check that runs after it, which moves the player and spawns its zombies in
		// one server task: the chunks it landed in were not loaded yet, the zombies went into nothing, and
		// a voltaic blow that had arced for weeks found nobody to arc to.
		int px = x + 60;
		int pz = z - 70;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d polished_blackstone", px - 8, y - 1, pz - 8, px + 8, y - 1, pz + 8));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 8, y, pz - 8, px + 8, y + 6, pz + 8));
		server.runCommand("time set 12800");
		server.runCommand("gamemode spectator @a");
		tp(server, px + 0.5, y + 2.2, pz - 5.5, 0.0F, 12.0F);
		context.waitTicks(20);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
			mc.options.particles().set(net.minecraft.server.level.ParticleStatus.ALL);
		});
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			dev.forja.entity.ForgeAutomaton automaton = dev.forja.registry.ModEntities.AUTOMATA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			automaton.setPos(px + 0.5, y, pz + 0.5);
			automaton.setNoAi(true);
			automaton.setYRot(180.0F);
			automaton.yBodyRot = 180.0F;
			automaton.yHeadRot = 180.0F;
			level.addFreshEntity(automaton);
		});
		context.waitTicks(10);
		server.runOnServer(s -> connection.getServerLevel().getEntitiesOfClass(dev.forja.entity.ForgeAutomaton.class,
			new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(4.0)).forEach(a -> a.steamPurge(connection.getServerLevel())));
		for (int frame = 0; frame < 14; frame++) {
			context.waitTicks(2);
			context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_40_vapor_%02d", frame)).disableCounterPrefix());
		}
		server.runOnServer(s -> connection.getServerLevel().getEntitiesOfClass(dev.forja.entity.ForgeAutomaton.class,
			new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(6.0)).forEach(net.minecraft.world.entity.Entity::discard));

		// Beads: gold, iron and copper, dropped from two and a half blocks up, a few a tick.
		tp(server, px + 0.5, y + 1.2, pz - 3.5, 0.0F, 0.0F);
		context.waitTicks(10);
		int[][] metals = {{0xFF, 0xC8, 0x3A}, {0xFF, 0x7A, 0x28}, {0xFF, 0x96, 0x5A}};
		for (int frame = 0; frame < 12; frame++) {
			context.runOnClient(mc -> {
				for (int metal = 0; metal < metals.length; metal++) {
					for (int bead = 0; bead < 3; bead++) {
						mc.level.addParticle(dev.forja.registry.ModParticles.GOTA,
							px + 0.5 + (metal - 1) * 1.2 + (mc.level.getRandom().nextDouble() - 0.5) * 0.15, y + 2.6,
							pz + 0.5 + (mc.level.getRandom().nextDouble() - 0.5) * 0.15,
							metals[metal][0] / 255.0, metals[metal][1] / 255.0, metals[metal][2] / 255.0);
					}
				}
			});
			context.waitTicks(2);
			context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_40_gotas_%02d", frame)).disableCounterPrefix());
		}
		server.runCommand("time set noon");
		server.runCommand("gamemode survival @a");
		// And back to the foundry's end of the world with time for its chunks to load, so whatever runs
		// next finds the ground it expects whether or not it thought to wait for it.
		tp(server, x + 90.5, y, z + 75.5, 0.0F, 0.0F);
		context.waitTicks(30);
	}

	/**
	 * The crucible's and the casting box's screens, with things in the player's own inventory.
	 *
	 * <p>That last part is the point. Both panels painted the inventory in one place and both menus put
	 * the slots in another, seven pixels left and eight down, and it went unseen because the only picture
	 * ever taken of either screen was taken with empty pockets. So: pockets full, and every slot of
	 * either menu has to sit on a square the texture actually drew.
	 */
	private static void shotStationScreens(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int px = x + 60;
		int pz = z - 40;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", px - 3, y - 1, pz - 3, px + 3, y - 1, pz + 3));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 3, y, pz - 3, px + 3, y + 3, pz + 3));
		server.runCommand("gamemode creative @a");
		tp(server, px + 0.5, y, pz - 2.5, 0.0F, 20.0F);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(dev.forja.registry.ModItems.ASCUA, 12));
			player.getInventory().setItem(4, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 32));
			player.getInventory().setItem(8, new ItemStack(net.minecraft.world.item.Items.COAL, 9));
			player.getInventory().setItem(9, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 5));
			player.getInventory().setItem(22, Assembler.createPart(PartType.HOJA, HIERRO));
			player.getInventory().setItem(35, new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 16));
		});
		context.waitTicks(10);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});

		// The forge table first, filmed: the star catching fire when what is on it would make something,
		// the sparks running its lines, and the strike when the button does its work.
		for (String table : List.of("mesa_de_forja", "mesa_de_forja_mayor")) {
			BlockPos forgeTable = new BlockPos(px, y, pz);
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:%s", px, y, pz, table));
			openTable(context, server, connection, forgeTable);
			server.runOnServer(s -> {
				ForgeMenu menu = menu(connection);
				menu.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.CABEZA_PICO, DIAMANTE));
				menu.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.MANGO, PIEDRA));
				menu.getSlot(ForgeMenu.STAR_FIRST + 4).set(Assembler.createPart(PartType.ATADURA, ORO));
				check(menu.action() == ForgeMenu.Action.FORGE, "three parts on the star should be ready to forge");
			});
			context.waitTicks(10);
			context.getInput().setCursorPos(0, 0);
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			context.waitTicks(2);
			String off = context.computeOnClient(mc -> slotsOffTheirSquares(mc, "textures/gui/mesa_de_forja.png"));
			check(off.equals("[]"), "every forge table slot should sit on a square of its panel, off: " + off);
			boolean greater = table.endsWith("mayor");
			int frames = greater ? 6 : 12;
			for (int frame = 0; frame < frames; frame++) {
				context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_02f_%s_%02d", greater ? "mayor" : "estrella", frame)).disableCounterPrefix());
				context.waitTicks(2);
			}
			if (!greater) {
				server.runOnServer(s -> check(menu(connection).clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_PRESS + 2), "a perfect press should forge"));
				context.runOnClient(mc -> ((dev.forja.client.ForgeScreen) mc.gui.screen()).showBurst(2));
				for (int frame = 0; frame < 8; frame++) {
					context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_02f_golpe_%02d", frame)).disableCounterPrefix());
					context.waitTicks(1);
				}
				// What came out, under the cursor: its parts drawn in a row under its name, one cell to a part
				// and none of them written out as a line any more.
				context.waitTicks(12);
				hover(context, ForgeMenu.CENTER_X + 8, ForgeMenu.CENTER_Y + 8);
				context.waitTicks(5);
				context.takeScreenshot(TestScreenshotOptions.of("forja_02g_tooltip_pico").disableCounterPrefix());
				context.getInput().setCursorPos(0, 0);
				server.runOnServer(s -> menu(connection).getSlot(ForgeMenu.CENTER_SLOT).set(
					Assembler.create(ForgeType.ESPADON, List.of(PIEDRA, DIAMANTE, ORO, HIERRO))));
				context.waitTicks(8);
				hover(context, ForgeMenu.CENTER_X + 8, ForgeMenu.CENTER_Y + 8);
				context.waitTicks(5);
				context.takeScreenshot(TestScreenshotOptions.of("forja_02g_tooltip_espadon").disableCounterPrefix());
				context.getInput().setCursorPos(0, 0);
				int[] strip = context.computeOnClient(mc -> {
					ItemStack sword = Assembler.create(ForgeType.ESPADON, List.of(PIEDRA, DIAMANTE, ORO, HIERRO));
					var image = sword.getTooltipImage();
					if (image.isEmpty() || !(image.get() instanceof dev.forja.item.PartsStrip data)) {
						return new int[] {-1, 0, 0, 0};
					}
					int[] shape = new dev.forja.client.PartsStripTooltip(data).shape(mc.font);
					boolean written = false;
					for (Component line : sword.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.NORMAL)) {
						written |= line.getString().startsWith(PartType.HOJA.displayName().getString() + ":");
					}
					return new int[] {shape[0], shape[1], shape[2], written ? 1 : 0};
				});
				log("tira de piezas del espadon: " + strip[0] + " celdas en " + strip[1] + " filas, " + strip[2] + " px de ancho; lineas de texto de piezas: " + strip[3]);
				check(strip[0] == ForgeType.ESPADON.slots.size(), "a greatsword's tooltip should draw one cell per part, got " + strip[0]);
				check(strip[2] <= 210, "and keep the row inside its width, got " + strip[2]);
				check(strip[3] == 0, "and no longer write the parts out as lines");
			}
			// The techniques tab, which is all painted by the screen and none of it by the texture: on the
			// greater table it has to come out in the greater table's colours, not the forge's.
			server.runOnServer(s -> {
				ForgeMenu menu = menu(connection);
				for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
					menu.getSlot(ForgeMenu.STAR_FIRST + i).set(ItemStack.EMPTY);
				}
				menu.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
				check(menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_TAB + ForgeMenu.MODE_TECHNIQUES), "the table should open its techniques");
			});
			context.waitTicks(8);
			context.takeScreenshot(TestScreenshotOptions.of("forja_02h_tecnicas_" + (greater ? "mayor" : "forja")).disableCounterPrefix());
			server.runOnServer(s -> connection.getServerPlayer().closeContainer());
			context.waitTicks(5);
		}

		// The other tables and tabs, for the same question and no picture: a slot the panel did not draw.
		String[][] panels = {
			{"mesa_de_piezas", String.valueOf(ForgeMenu.MODE_PARTS), "textures/gui/mesa_de_piezas.png"},
			{"mesa_de_piezas", String.valueOf(ForgeMenu.MODE_DISASSEMBLE), "textures/gui/mesa_de_piezas_desarmar.png"},
			{"mesa_de_talabarteria", String.valueOf(ForgeMenu.MODE_FORGE), "textures/gui/mesa_de_forja.png"},
		};
		for (String[] panel : panels) {
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:%s", px, y, pz, panel[0]));
			openTable(context, server, connection, new BlockPos(px, y, pz));
			int mode = Integer.parseInt(panel[1]);
			server.runOnServer(s -> {
				ForgeMenu menu = menu(connection);
				if (menu.getMode() != mode) {
					check(menu.clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_TAB + mode), "the table should switch to tab " + mode);
				}
			});
			context.waitTicks(8);
			int shown = context.computeOnClient(mc -> ((ForgeMenu) ((dev.forja.client.ForgeScreen) mc.gui.screen()).getMenu()).getMode());
			check(shown == mode, "the client should be on tab " + mode + " of " + panel[0] + ", is on " + shown);
			String off = context.computeOnClient(mc -> slotsOffTheirSquares(mc, panel[2]));
			log("casillas de " + panel[0] + " (pestaña " + mode + ") fuera de su cuadro: " + off);
			check(off.equals("[]"), "every slot of " + panel[0] + " tab " + mode + " should sit on a square of its panel, off: " + off);
			server.runOnServer(s -> connection.getServerPlayer().closeContainer());
			context.waitTicks(4);
		}

		// Every slot of a menu on a square of its texture: the slot's corner, one pixel up and left, has
		// to be the dark corner gui_slot paints, in the panel's own picture.
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos at = new BlockPos(px, y, pz);
			level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
			var crucible = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(at);
			check(crucible != null, "the crucible should have its block entity");
			crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 6));
			crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND, new ItemStack(net.minecraft.world.item.Items.COAL, 4));
			crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA, 6));
			for (int tick = 0; tick < 40; tick++) {
				dev.forja.block.entity.CrucibleBlockEntity.serverTick(level, at, level.getBlockState(at), crucible);
			}
			connection.getServerPlayer().openMenu(crucible);
		});
		context.waitTicks(10);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		String crucibleOff = context.computeOnClient(mc -> slotsOffTheirSquares(mc, "textures/gui/crisol.png"));
		log("pantalla del crisol: casillas fuera de su cuadro " + crucibleOff);
		check(crucibleOff.equals("[]"), "every crucible slot should sit on a square of its panel, off: " + crucibleOff);
		context.takeScreenshot(TestScreenshotOptions.of("forja_32b_crisol_inventario").disableCounterPrefix());
		context.runOnClient(mc -> mc.player.closeContainer());
		context.waitTicks(3);

		// The same pot with four different things in it. The melt was pushed four fifths of the way to
		// orange whatever it was, so gold, copper and steel were one puddle; each has to be its own colour.
		Object[][] melts = {
			{"oro", new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 10), ItemStack.EMPTY},
			{"cobre", new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 10), ItemStack.EMPTY},
			{"diamante", new ItemStack(net.minecraft.world.item.Items.DIAMOND, 10), ItemStack.EMPTY},
			{"acero_vivo", new ItemStack(dev.forja.registry.ModItems.alloy("acero_vivo"), 10), ItemStack.EMPTY},
		};
		List<Integer> seen = new ArrayList<>();
		for (Object[] melt : melts) {
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				BlockPos at = new BlockPos(px, y, pz);
				level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
				var crucible = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(at);
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST, ((ItemStack) melt[1]).copy());
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_SECOND, ((ItemStack) melt[2]).copy());
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA, 6));
				connection.getServerPlayer().openMenu(crucible);
			});
			context.waitTicks(10);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(2);
			context.takeScreenshot(TestScreenshotOptions.of("forja_32d_crisol_" + melt[0]).disableCounterPrefix());
			dev.forja.material.ForgeMaterial metal = dev.forja.material.ForgeMaterial.fromInput((ItemStack) melt[1]);
			check(metal != null, melt[0] + " should be a material the pot knows");
			seen.add(metal.moltenColor());
			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitTicks(3);
			server.runOnServer(s -> connection.getServerLevel().removeBlock(new BlockPos(px, y, pz), false));
		}
		// Far enough apart to be told apart at a glance: no two of the four within 60 of each other, summed
		// over the three channels. Under the old arithmetic gold and copper were 14 apart.
		int closest = Integer.MAX_VALUE;
		for (int i = 0; i < seen.size(); i++) {
			for (int j = i + 1; j < seen.size(); j++) {
				int a = seen.get(i);
				int b = seen.get(j);
				closest = Math.min(closest, Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF)));
			}
		}
		log("colores de colada: " + seen.stream().map(c -> String.format(Locale.ROOT, "%06X", c)).toList() + ", los dos más parecidos a " + closest);
		check(closest >= 60, "molten metals should be told apart at a glance, closest pair is " + closest + " apart");

		// And out in the world, where there is no screen to read it off. The client's copy of a crucible is
		// empty — a container's contents go only to whoever has it open — so the pot has to say what colour
		// it is; until it did, the dust over the rim never drew and every lit pot glowed the same orange.
		ItemStack[] pots = {
			new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 8),
			new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 8),
			new ItemStack(net.minecraft.world.item.Items.DIAMOND, 8),
		};
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (int i = 0; i < pots.length; i++) {
				BlockPos at = new BlockPos(px - 2 + i * 2, y, pz + 1);
				level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CRISOL_DE_HIERRO.defaultBlockState());
				var crucible = (dev.forja.block.entity.CrucibleBlockEntity) level.getBlockEntity(at);
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FIRST, pots[i].copy());
				crucible.setItem(dev.forja.block.entity.CrucibleBlockEntity.SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA, 6));
				// Lit by hand: a lone metal is nothing to pour, so the pot would not light itself for it.
				level.setBlockAndUpdate(at, level.getBlockState(at).setValue(dev.forja.block.CrucibleBlock.LIT, true));
			}
		});
		server.runCommand("gamemode spectator @a");
		tp(server, px + 0.5, y + 3.0, pz - 1.6, 0.0F, 48.0F);
		context.waitTicks(30);
		String told = context.computeOnClient(mc -> {
			StringBuilder colours = new StringBuilder();
			for (int i = 0; i < pots.length; i++) {
				var entity = mc.level.getBlockEntity(new BlockPos(px - 2 + i * 2, y, pz + 1));
				colours.append(entity instanceof dev.forja.block.entity.CrucibleBlockEntity pot ? String.format(Locale.ROOT, "%06X", pot.meltColour() & 0xFFFFFF) : "??????");
				colours.append(i + 1 < pots.length ? "," : "");
			}
			return colours.toString();
		});
		String expected = String.format(Locale.ROOT, "%06X,%06X,%06X", dev.forja.material.ForgeMaterial.ORO.color,
			dev.forja.material.ForgeMaterial.COBRE.color, dev.forja.material.ForgeMaterial.DIAMANTE.color);
		log("crisoles en el mundo: el cliente ve " + told + " (oro, cobre, diamante: " + expected + ")");
		check(told.equals(expected), "the client should be told the colour of what is in each pot, got " + told + " wanted " + expected);
		context.takeScreenshot(TestScreenshotOptions.of("forja_32e_crisoles_en_el_mundo").disableCounterPrefix());
		server.runOnServer(s -> {
			for (int i = 0; i < pots.length; i++) {
				connection.getServerLevel().removeBlock(new BlockPos(px - 2 + i * 2, y, pz + 1), false);
			}
			connection.getServerLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(8.0)).forEach(net.minecraft.world.entity.Entity::discard);
		});
		server.runCommand("gamemode creative @a");
		tp(server, px + 0.5, y, pz - 2.5, 0.0F, 20.0F);
		context.waitTicks(10);

		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			BlockPos at = new BlockPos(px, y, pz);
			level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.CAJA_DE_MOLDEO.defaultBlockState());
			var box = (dev.forja.block.entity.CastingBoxBlockEntity) level.getBlockEntity(at);
			check(box != null, "the casting box should have its block entity");
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_PATTERN, Assembler.createPart(PartType.CABEZA_PICO, HIERRO));
			box.setItem(dev.forja.block.entity.CastingBoxBlockEntity.SLOT_STEEL, new ItemStack(dev.forja.registry.ModItems.alloy("acero_refractario"), 4));
			// Part of the way through taking the mould, so the print in the sand is half pressed.
			for (int tick = 0; tick < 45; tick++) {
				dev.forja.block.entity.CastingBoxBlockEntity.serverTick(level, at, level.getBlockState(at), box);
			}
			connection.getServerPlayer().openMenu(box);
		});
		context.waitTicks(10);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		String boxOff = context.computeOnClient(mc -> slotsOffTheirSquares(mc, "textures/gui/caja_de_moldeo.png"));
		log("pantalla de la caja de moldeo: casillas fuera de su cuadro " + boxOff);
		check(boxOff.equals("[]"), "every casting box slot should sit on a square of its panel, off: " + boxOff);
		context.takeScreenshot(TestScreenshotOptions.of("forja_32c_caja_de_moldeo").disableCounterPrefix());
		context.runOnClient(mc -> mc.player.closeContainer());
		context.waitTicks(3);
		server.runOnServer(s -> {
			connection.getServerLevel().removeBlock(new BlockPos(px, y, pz), false);
			connection.getServerPlayer().getInventory().clearContent();
		});
		server.runCommand("gamemode survival @a");
	}

	/**
	 * The slots of the open menu that do not sit on a slot drawn in the panel's texture, as a list of
	 * "index@x,y". Read from the texture itself, so it is the picture the player sees that is checked.
	 */
	private static String slotsOffTheirSquares(net.minecraft.client.Minecraft mc, String texture) {
		// The benches' slots: gui_slot's dark corner and its fill.
		return slotsOffTheirSquares(mc, texture, 0x3C3228, 0x8C806E);
	}

	/** The same, for a panel whose slots are painted in other colours: the corner pixel's, and the fill's. */
	private static String slotsOffTheirSquares(net.minecraft.client.Minecraft mc, String texture, int cornerColour, int insideColour) {
		if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
			return "[no screen]";
		}
		List<String> off = new ArrayList<>();
		try (var stream = mc.getResourceManager().open(dev.forja.Forja.id(texture));
			var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
			for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
				// The forge menu carries the slots of tabs that are not showing; those are not on the panel.
				if (!slot.isActive() || slot.x < 1 || slot.y < 1 || slot.x >= image.getWidth() || slot.y >= image.getHeight()) {
					continue;
				}
				// gui_slot's top-left border pixel is (60, 50, 40); NativeImage hands pixels back as ARGB.
				int corner = image.getPixel(slot.x - 1, slot.y - 1) & 0xFFFFFF;
				int inside = image.getPixel(slot.x, slot.y) & 0xFFFFFF;
				if (corner != cornerColour || inside != insideColour) {
					off.add(slot.index + "@" + slot.x + "," + slot.y);
				}
			}
		} catch (java.io.IOException e) {
			return "[" + e.getMessage() + "]";
		}
		return off.toString();
	}

	private static void shotHudAndPools(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("difficulty peaceful");
		server.runCommand("gamemode survival @a");
		int px = x + 140;
		int pz = z + 20;
		tp(server, px + 0.5, y, pz + 0.5, 0.0F, 24.0F);
		context.waitTicks(20);
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d smooth_stone", px - 12, y - 1, pz - 6, px + 12, y - 1, pz + 22));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 12, y, pz - 6, px + 12, y + 6, pz + 22));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone_bricks", px + 2, y, pz + 9, px + 5, y, pz + 10));
		context.waitTicks(10);
		int[] made = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			ItemStack wings = Assembler.create(ForgeType.ALAS, List.of(ORO, CUERO), level.registryAccess());
			wings.set(ModComponents.VUELO, Math.max(1, dev.forja.forge.Flight.maxTicks(wings) / 6));
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, wings);
			for (int blow = 0; blow < dev.forja.upgrade.Frenzy.MAX_HITS; blow++) {
				dev.forja.upgrade.Frenzy.onHit(player);
			}
			net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz + 8)).inflate(12.0);
			level.getEntitiesOfClass(dev.forja.entity.Shockwave.class, around).forEach(net.minecraft.world.entity.Entity::discard);
			dev.forja.world.SlagPools.pour(level, new Vec3(px - 3.5, y, pz + 8.5), 3.0, 120);
			dev.forja.world.OilPools.pour(level, new Vec3(px + 4.0, y, pz + 9.0), 4.0, 120);
			var patches = level.getEntitiesOfClass(dev.forja.entity.Shockwave.class, around, dev.forja.entity.Shockwave::pool);
			return new int[] {patches.size(), dev.forja.upgrade.Frenzy.shownHits(player)};
		});
		check(made[0] == 2, "a slag pool and an oil pool should each put one patch on the floor, found " + made[0]);
		check(made[1] == dev.forja.upgrade.Frenzy.MAX_HITS, "five blows should fill the frenzy bar, got " + made[1]);
		context.waitTicks(12);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		context.waitTicks(2);
		context.takeScreenshot(TestScreenshotOptions.of("forja_hud_barras_y_charcos").disableCounterPrefix().withSize(960, 540));
		context.waitTicks(60);
		context.takeScreenshot(TestScreenshotOptions.of("forja_charcos_a_medias").disableCounterPrefix().withSize(960, 540));
		context.waitTicks(70);
		int left = server.computeOnServer(s -> connection.getServerLevel().getEntitiesOfClass(dev.forja.entity.Shockwave.class,
			new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz + 8)).inflate(12.0), dev.forja.entity.Shockwave::pool).size());
		log("charcos: 2 manchas al verter, " + left + " cuando se acaba su tiempo");
		check(left == 0, "a patch should go when the pool it shows has dried, " + left + " left");

		// The automaton's slag: the one area attack that is a wedge. Same warning, with straight sides.
		server.runCommand("difficulty easy");
		java.util.UUID[] pair = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var golem = dev.forja.registry.ModEntities.AUTOMATA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(golem != null, "the automaton should be creatable");
			golem.snapTo(px + 0.5, y, pz + 12.5, 180.0F, 0.0F);
			golem.setNoAi(true);
			level.addFreshEntity(golem);
			var husk = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(husk != null, "a husk should be creatable");
			husk.snapTo(px + 0.5, y, pz + 8.5, 0.0F, 0.0F);
			husk.setNoAi(true);
			level.addFreshEntity(husk);
			golem.slagStomp(level, husk);
			return new java.util.UUID[] {golem.getUUID(), husk.getUUID()};
		});
		context.waitTicks(3);
		float[] wedge = server.computeOnServer(s -> {
			var waves = connection.getServerLevel().getEntitiesOfClass(dev.forja.entity.Shockwave.class,
				new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz + 12)).inflate(6.0));
			return waves.size() != 1 ? new float[] {waves.size(), 0.0F, 0.0F}
				: new float[] {1.0F, waves.get(0).arc(), waves.get(0).fired() ? 1.0F : 0.0F};
		});
		check(wedge[0] == 1.0F && wedge[2] == 0.0F, "the slag stomp should put one warning down, found " + wedge[0]);
		check(Math.abs(wedge[1] - dev.forja.entity.ForgeAutomaton.SLAG_ARC) < 0.01F, "and it should be a wedge as wide as the slag, got " + wedge[1]);
		context.waitTicks(22);
		context.takeScreenshot(TestScreenshotOptions.of("forja_cuna_aviso").disableCounterPrefix().withSize(960, 540));
		context.waitTicks(dev.forja.entity.ForgeAutomaton.SLAG_WINDUP - 25 + 8);
		context.takeScreenshot(TestScreenshotOptions.of("forja_cuna_ardiendo").disableCounterPrefix().withSize(960, 540));
		float[] burning = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var pools = level.getEntitiesOfClass(dev.forja.entity.Shockwave.class,
				new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz + 12)).inflate(6.0), dev.forja.entity.Shockwave::pool);
			float hurt = level.getEntity(pair[1]) instanceof net.minecraft.world.entity.LivingEntity husk ? husk.getMaxHealth() - husk.getHealth() : -1.0F;
			for (java.util.UUID id : pair) {
				net.minecraft.world.entity.Entity entity = level.getEntity(id);
				if (entity != null) {
					entity.discard();
				}
			}
			return new float[] {pools.size(), pools.isEmpty() ? 0.0F : pools.get(0).arc(), hurt};
		});
		log("cuna del automata: aviso en cuna de " + String.format(Locale.ROOT, "%.2f", wedge[1]) + " rad, deja " + (int) burning[0]
			+ " charco en cuna, el husk de delante pierde " + burning[2]);
		check(burning[0] == 1.0F && Math.abs(burning[1] - dev.forja.entity.ForgeAutomaton.SLAG_ARC) < 0.01F, "the fists should leave one burning wedge, found " + burning[0]);
		check(burning[2] > 0.0F, "and whoever stood in front of it should have been hit");

		// The smith's own bar, whole and then in his last quarter, where it goes the violet of his forge.
		server.runCommand("gamemode creative @a");
		java.util.UUID boss = server.computeOnServer(s -> spawnSmith(connection.getServerLevel(), px, y, pz + 14).getUUID());
		context.waitTicks(30);
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.waitTicks(2);
		context.takeScreenshot(TestScreenshotOptions.of("forja_barra_herrero_entera").disableCounterPrefix().withSize(960, 540));
		server.runOnServer(s -> {
			if (connection.getServerLevel().getEntity(boss) instanceof dev.forja.entity.FallenSmith smith) {
				smith.setHealth(smith.getMaxHealth() * 0.22F);
			}
		});
		context.waitTicks(30);
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.waitTicks(2);
		context.takeScreenshot(TestScreenshotOptions.of("forja_barra_herrero_ultimo_cuarto").disableCounterPrefix().withSize(960, 540));
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (net.minecraft.world.entity.Entity entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
				new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz + 10)).inflate(40.0), other -> !(other instanceof ServerPlayer))) {
				entity.discard();
			}
		});
		server.runCommand("gamemode survival @a");
		server.runCommand("difficulty peaceful");
		server.runOnServer(s -> connection.getServerPlayer().getInventory().clearContent());
	}

	/** {waves nearby, fired, age in ticks (of the run once fired, of the warning before), reach, windup, the victim's health}. */
	private static double[] waveState(TestServerContext server, TestServerConnection connection, int px, int y, int pz, java.util.UUID victim) {
		return server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			var waves = level.getEntitiesOfClass(dev.forja.entity.Shockwave.class,
				new net.minecraft.world.phys.AABB(new BlockPos(px, y, pz)).inflate(8.0));
			double health = level.getEntity(victim) instanceof net.minecraft.world.entity.LivingEntity living ? living.getHealth() : -1.0;
			if (waves.isEmpty()) {
				return new double[] {0, 0, 0, 0, 0, health};
			}
			dev.forja.entity.Shockwave wave = waves.get(0);
			double age = level.getGameTime() - (wave.fired() ? wave.firedAt() : wave.startedAt());
			return new double[] {waves.size(), wave.fired() ? 1 : 0, age, wave.reach(), wave.windup(), health};
		});
	}

	/** What the client has of it: {waves nearby, how many of them fired, reach in tenths of a block}. */
	private static int[] clientWaves(ClientGameTestContext context, int px, int y, int pz) {
		return context.computeOnClient(mc -> {
			int count = 0;
			int fired = 0;
			float reach = 0.0F;
			if (mc.level != null) {
				for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
					if (entity instanceof dev.forja.entity.Shockwave wave && wave.distanceToSqr(px, y, pz) < 64.0) {
						count++;
						fired += wave.fired() ? 1 : 0;
						reach = wave.reach();
					}
				}
			}
			return new int[] {count, fired, Math.round(reach * 10.0F)};
		});
	}

	/** Pins the camera, then shoots: a spectator sinks, and a shot taken the tick it is moved shows the old place. */
	private static void shootWave(ClientGameTestContext context, TestServerContext server, double camX, double camY, double camZ, String name) {
		tp(server, camX, camY, camZ, 135.0F, 30.0F);
		context.waitTicks(1);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(960, 540));
	}

	/** Sets a move going and then holds the shutter open on it, one frame per tick. */
	private static void filmOne(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
		String name, int frames, java.util.function.Function<ServerLevel, List<net.minecraft.world.entity.Entity>> stage) {
		filmOne(context, server, connection, name, frames, stage, -1, level -> {
		});
	}

	/**
	 * The same, with a beat partway through.
	 *
	 * <p>A death cannot be staged at frame zero: the whole shot would be of a corpse. The cast goes in
	 * alive, the camera settles, and then at `actAt` something happens to them — which is also how the
	 * boss's phase break has to be filmed, because it fires on a health threshold and not on command.
	 */
	private static void filmOne(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
		String name, int frames, java.util.function.Function<ServerLevel, List<net.minecraft.world.entity.Entity>> stage,
		int actAt, java.util.function.Consumer<ServerLevel> act) {
		List<java.util.UUID> cast = server.computeOnServer(s -> stage.apply(connection.getServerLevel()).stream()
			.map(net.minecraft.world.entity.Entity::getUUID).toList());
		// A spectator is not held up by anything. Left alone the camera falls away from the scene for
		// the length of the shot, which is why the first cut of this had the mob shrinking into the
		// distance mid-swing. Pinning it every frame is the whole fix.
		for (int frame = 0; frame < frames; frame++) {
			// Through the command rather than through the entity. Moving a player server-side and
			// screenshotting on the same tick photographs where the client still thinks it is: the
			// client owns its own position and has not been told yet. /tp sends it the correction, and
			// the tick that follows is what applies it, so the shot after that is where we asked for.
			tp(server, CAMERA_X, CAMERA_ABS_Y, CAMERA_ABS_Z, CAMERA_YAW, CAMERA_PITCH);
			if (frame == actAt) {
				server.runOnServer(s -> act.accept(connection.getServerLevel()));
			}
			context.waitTicks(1);
			// Small frames: this is footage, not a portrait, and there are a couple of hundred of them.
			context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "forja_film_%s_%03d", name, frame))
				.disableCounterPrefix()
				.withSize(960, 540));
		}
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (java.util.UUID id : cast) {
				net.minecraft.world.entity.Entity entity = level.getEntity(id);
				if (entity != null) {
					entity.discard();
				}
			}
		});
		context.waitTicks(4);
	}

	private static void shotGrips(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("time set day");
		server.runCommand("gamemode survival @a");
		tp(server, x + 90.5, y, z + 78.5, 0.0F, 0.0F);
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var registries = connection.getServerLevel().registryAccess();
			player.getInventory().clearContent();
			player.getInventory().setItem(0, Assembler.create(ForgeType.MANGUAL, List.of(HIERRO, HIERRO, MADERA), registries));
			player.getInventory().setItem(1, Assembler.create(ForgeType.GUANTELETES, List.of(CUERO, HIERRO, ORO), registries));
			player.getInventory().setItem(2, Assembler.create(ForgeType.GANCHO, List.of(HIERRO, CUERO, MADERA), registries));
			player.getInventory().setSelectedSlot(0);
		});
		context.waitTicks(10);
		String[] names = {"mangual", "guanteletes", "gancho"};
		for (int slot = 0; slot < names.length; slot++) {
			int which = slot;
			context.runOnClient(mc -> {
				mc.player.getInventory().setSelectedSlot(which);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.gui.hud.getChat().clearMessages(false);
			});
			context.waitTicks(10);
			// Four times the pixels, only for these. At the window's own 854x480 the player is sixty
			// pixels tall in third person and whatever the hand is doing is about fifteen of them —
			// small enough that the hook hung upside down off the wrist through three of these runs
			// without any of the shots showing it.
			context.takeScreenshot(TestScreenshotOptions.of("forja_37_mano_" + names[slot]).withSize(1920, 1080));
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("forja_37_mano_" + names[slot] + "_tercera").withSize(1920, 1080));
		}
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runOnServer(s -> connection.getServerPlayer().getInventory().clearContent());
	}

	private static void checkGrapple(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int gx = x + 12;
		int gz = z + 40;
		// A floor to stand on and a wall to bite, with air between them.
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", gx - 2, y - 1, gz - 2, gx + 2, y - 1, gz + 12));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", gx - 2, y, gz - 2, gx + 2, y + 5, gz + 11));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", gx - 2, y, gz + 12, gx + 2, y + 6, gz + 12));
		tp(server, gx + 0.5, y, gz + 0.5, 0.0F, 0.0F);
		context.waitTicks(10);

		int[] before = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			ItemStack hook = Assembler.create(ForgeType.GANCHO, List.of(HIERRO, CUERO, MADERA), connection.getServerLevel().registryAccess());
			player.getInventory().setSelectedSlot(0);
			player.setItemInHand(InteractionHand.MAIN_HAND, hook);
			dev.forja.upgrade.WeaponThrow.tryHook(connection.getServerLevel(), player, InteractionHand.MAIN_HAND);
			return new int[] {hooks(player), (int) (player.getZ() * 100)};
		});
		check(before[0] == 1, "the smith should be holding exactly one hook, got " + before[0]);

		// Long enough for the claw to fly out, bite, haul for its thirty ticks, come back and finish.
		context.waitTicks(160);
		int[] after = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			long loose = connection.getServerLevel().getEntitiesOfClass(
					net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(24.0))
				.stream()
				.filter(item -> item.getItem().has(dev.forja.registry.ModComponents.PARTS))
				.count();
			return new int[] {hooks(player), (int) (player.getZ() * 100), (int) loose};
		});
		log("gancho: antes " + before[0] + " en mano, despues " + after[0] + " en inventario, "
			+ after[2] + " tirados por el suelo · se movio " + (after[1] - before[1]) / 100.0 + " bloques en z");
		check(after[0] == 1, "one grapple should leave exactly one hook, got " + after[0]);
		check(after[2] == 0, "and nothing forged lying on the floor, got " + after[2]);
		// Not just "moved": hauled. A single shove used to move him about three blocks and drop him,
		// which passed a test that only asked whether he had budged at all.
		double moved = (after[1] - before[1]) / 100.0;
		check(moved > 6.0, "the rope should haul the smith most of the way to the wall, moved " + moved);
		server.runOnServer(s -> connection.getServerPlayer().getInventory().clearContent());
	}

	/** Every grappling hook the smith is carrying, in hand or not. */
	private static int hooks(ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			dev.forja.part.ForgedParts parts = stack.get(dev.forja.registry.ModComponents.PARTS);
			if (parts != null && parts.type() == ForgeType.GANCHO) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void checkHeadThrow(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int tunnelX = x + 12;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", tunnelX - 2, y, z + 4, tunnelX + 2, y + 3, z + 30));
		tp(server, tunnelX + 0.5, y, z + 0.5, 0.0F, 0.0F);
		context.waitTicks(10);

		server.runOnServer(s -> {
			ItemStack pick = maxed(connection, Assembler.create(ForgeType.PICO, List.of(DIAMANTE, PIEDRA, ORO)),
				new ItemStack(Items.PISTON, 4), new ItemStack(Items.SLIME_BALL, 4), new ItemStack(Items.ENDER_PEARL, 4));
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().setSelectedSlot(0);
			player.setItemInHand(InteractionHand.MAIN_HAND, pick);
			player.gameMode.useItem(player, connection.getServerLevel(), pick, InteractionHand.MAIN_HAND);
		});
		context.waitTicks(9);
		context.takeScreenshot("forja_13_lanzacabezas_vuelo");

		int returnedAfter = -1;
		for (int tick = 0; tick < 120; tick++) {
			context.waitTick();
			boolean onCooldown = server.computeOnServer(s -> connection.getServerPlayer().getCooldowns().isOnCooldown(connection.getServerPlayer().getMainHandItem()));
			if (!onCooldown) {
				returnedAfter = tick;
				break;
			}
		}
		int mined = server.computeOnServer(s -> {
			int count = 0;
			for (int dz = 4; dz <= 30; dz++) {
				if (connection.getServerLevel().getBlockState(new BlockPos(tunnelX, y + 1, z + dz)).isAir()) {
					count++;
				}
			}
			return count;
		});
		log("head throw: returned after " + returnedAfter + " ticks, mined " + mined + " blocks");
		check(returnedAfter >= 0, "the head never came back");
		check(mined == 16, "Lanzacabezas 100% should mine 16 blocks, mined " + mined);
	}

	private static void checkMiningUpgrades(TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int baseX = x - 12;
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			ServerPlayer player = connection.getServerPlayer();

			for (BlockPos pos : BlockPos.betweenClosed(baseX, y, z, baseX + 1, y + 1, z + 1)) {
				level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState());
			}
			ItemStack vein = maxed(connection, Assembler.create(ForgeType.PICO, List.of(DIAMANTE, MADERA, MADERA)), new ItemStack(Items.QUARTZ_BLOCK, 7));
			player.setItemInHand(InteractionHand.MAIN_HAND, vein);
			player.gameMode.destroyBlock(new BlockPos(baseX, y, z));
			int left = 0;
			for (BlockPos pos : BlockPos.betweenClosed(baseX, y, z, baseX + 1, y + 1, z + 1)) {
				left += level.getBlockState(pos).is(Blocks.IRON_ORE) ? 1 : 0;
			}
			log("veta: iron ore left in the vein: " + left);
			check(left == 0, "Veta should break the whole 8 block vein, " + left + " left");

			int wallZ = z + 6;
			for (BlockPos pos : BlockPos.betweenClosed(baseX + 4, y + 1, wallZ, baseX + 6, y + 3, wallZ)) {
				level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
			}
			player.snapTo(baseX + 5.5, y, z + 3.5, 0.0F, 0.0F);
			player.setItemInHand(InteractionHand.MAIN_HAND, Assembler.create(ForgeType.MARTILLO, List.of(HIERRO, MADERA, MADERA)));
			player.gameMode.destroyBlock(new BlockPos(baseX + 5, y + 2, wallZ));
			int wallLeft = 0;
			for (BlockPos pos : BlockPos.betweenClosed(baseX + 4, y + 1, wallZ, baseX + 6, y + 3, wallZ)) {
				wallLeft += level.getBlockState(pos).isAir() ? 0 : 1;
			}
			log("martillo: stone left in the 3x3 wall: " + wallLeft);

			// Cantera: the blocks the area takes with it cost the tool nothing.
			int quarryZ = z + 10;
			var registries = level.registryAccess();
			ItemStack quarry = Assembler.create(ForgeType.MARTILLO, List.of(HIERRO, MADERA, MADERA), registries);
			quarry = UpgradeRecipes.upgraded(quarry, ForgeType.MARTILLO, Upgrade.EXCAVACION, 50, registries);
			int[] damage = new int[2];
			for (int round = 0; round < 2; round++) {
				for (BlockPos pos : BlockPos.betweenClosed(baseX + 4, y + 1, quarryZ, baseX + 6, y + 3, quarryZ)) {
					level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
				}
				ItemStack tool = quarry.copy();
				if (round == 1) {
					tool = UpgradeRecipes.upgraded(tool, ForgeType.MARTILLO, Upgrade.EFICIENCIA, 50, registries);
				}
				player.snapTo(baseX + 5.5, y, z + 7.5, 0.0F, 0.0F);
				player.setItemInHand(InteractionHand.MAIN_HAND, tool);
				player.gameMode.destroyBlock(new BlockPos(baseX + 5, y + 2, quarryZ));
				damage[round] = player.getMainHandItem().getDamageValue();
			}
			log("cantera: desgaste sin sinergia " + damage[0] + ", con ella " + damage[1]);
			check(damage[0] > 1, "breaking a wall should cost the tool more than one point, got " + damage[0]);
			check(damage[1] <= 1, "Cantera should leave the tool paying only for the block you hit, got " + damage[1]);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			check(wallLeft == 0, "the hammer should break the whole 3x3, " + wallLeft + " left");

			BlockPos ore = new BlockPos(baseX + 8, y, z);
			level.setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
			player.getInventory().clearContent();
			ItemStack smelter = maxed(connection, Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, MADERA)), new ItemStack(Items.BLAZE_ROD, 5), new ItemStack(Items.COAL_BLOCK, 5));
			smelter = maxed(connection, smelter, new ItemStack(Items.ENDER_PEARL, 4), new ItemStack(Items.HOPPER, 4));
			player.setItemInHand(InteractionHand.MAIN_HAND, smelter);
			player.gameMode.destroyBlock(ore);
			int ingots = player.getInventory().countItem(Items.IRON_INGOT);
			log("fundicion + telequinesis: iron ingots in inventory: " + ingots);
			check(ingots >= 1, "Fundicion + Telequinesis at 100% should put an iron ingot in the inventory");
		});
	}

	/** Applies the given upgrade items to a stack and returns the upgraded copy. */
	private static ItemStack maxed(TestServerConnection connection, ItemStack stack, ItemStack... ingredients) {
		List<ItemStack> slots = new java.util.ArrayList<>(List.of(ingredients));
		while (slots.size() < 3) {
			slots.add(ItemStack.EMPTY);
		}
		UpgradeRecipes.Application application = UpgradeRecipes.apply(stack, slots, connection.getServerLevel().registryAccess());
		check(application != null && !application.result().isEmpty(), "upgrade items did not apply to " + stack);
		// What the callers want is the upgrade at its height, to measure what it does there. How many items
		// that takes is forge/Potential's business and is tested where it belongs; counting them out here
		// again would break fifteen unrelated checks every time a price moved.
		dev.forja.part.ForgedParts parts = stack.get(ModComponents.PARTS);
		return UpgradeRecipes.upgraded(stack, parts.type(), application.upgrade(), 100, connection.getServerLevel().registryAccess());
	}

	private static ForgeMenu menu(TestServerConnection connection) {
		return (ForgeMenu) connection.getServerPlayer().containerMenu;
	}

	private static boolean advancementDone(ServerPlayer player, String id) {
		var holder = player.level().getServer().getAdvancements().get(dev.forja.Forja.id("forja/" + id));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** Clear the chat and the toasts, so a screenshot is of the world and not of the test's own noise. */
	private static void quiet(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void log(String message) {
		System.out.println("[forja-test] " + message);
	}

	/** The attack damage an item grants, straight from its modifiers. */
	private static double damageOf(ItemStack stack) {
		return stack.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
			.filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE)).mapToDouble(entry -> entry.modifier().amount()).sum();
	}

	/** Attacks per second an item allows, from the attack speed modifier on top of the player base of 4. */
	private static double attackSpeedOf(ItemStack stack) {
		double modifier = stack.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
			.filter(entry -> entry.attribute().equals(Attributes.ATTACK_SPEED)).mapToDouble(entry -> entry.modifier().amount()).sum();
		return 4.0 + modifier;
	}
}
