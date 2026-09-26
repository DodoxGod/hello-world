package dev.forja.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import dev.forja.Forja;
import dev.forja.ForjaPath;
import dev.forja.ForjaPath.Step;
import dev.forja.block.CastingBoxBlock;
import dev.forja.combat.WeaponGuard;
import dev.forja.forge.Alloys;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.menu.Station;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.Upgrade;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * The guide's path (ForjaPath), on a real server: every step is one of the mod's advancements, the order
 * is the one the code imposes, the next step for a given progress is the right one, the chat speaks once
 * per step, and the two advancements the path added are earned by what they say. Run with
 * ./gradlew runGametest.
 */
public class PathGameTests {
	/** Every step names a real advancement of the forja tab, each its own, and each finds its way back. */
	@GameTest
	public void everyStepIsAnAdvancement(GameTestHelper helper) {
		var advancements = helper.getLevel().getServer().getAdvancements();
		Set<String> seen = new HashSet<>();
		for (Step step : ForjaPath.STEPS) {
			AdvancementHolder holder = advancements.get(Forja.id("forja/" + step.advancement()));
			helper.assertTrue(holder != null && holder.value().display().isPresent(),
				"el paso " + step + " no tiene un logro visible forja/" + step.advancement());
			helper.assertTrue(seen.add(step.advancement()), "dos pasos con el mismo logro: " + step.advancement());
			helper.assertTrue(ForjaPath.of(step.advancement()) == step, "forja/" + step.advancement() + " no lleva de vuelta a " + step);
			helper.assertTrue(step.number() == step.ordinal() + 1, "el paso " + step + " se numera " + step.number());
		}
		helper.assertTrue(ForjaPath.of("roto") == null && ForjaPath.of("guia") == null, "roto y guia no son pasos del camino");
		helper.assertTrue(ForjaPath.STEPS.size() == 9, "el camino tiene " + ForjaPath.STEPS.size() + " pasos, no 9");
		helper.succeed();
	}

	/** The next step is the first one not done: from nothing, in order, out of order, and at the very end. */
	@GameTest
	public void nextStepForProgress(GameTestHelper helper) {
		assertNext(helper, Set.of(), Step.PLANTILLA);
		assertNext(helper, Set.of("plantilla"), Step.PIEZA);
		assertNext(helper, Set.of("plantilla", "pieza", "forja"), Step.TEMPLE);
		// Done out of order: the upgrade, the parry and the alloy do not skip the quench that was missed.
		assertNext(helper, Set.of("plantilla", "pieza", "forja", "mejora", "parada", "aleacion"), Step.TEMPLE);
		// Advancements off the path change nothing.
		assertNext(helper, Set.of("guia", "roto", "desarmar", "plantilla"), Step.PIEZA);
		// Only the greater table missing.
		Set<String> allButLast = new HashSet<>();
		for (Step step : ForjaPath.STEPS) {
			if (step != Step.MESA_MAYOR) {
				allButLast.add(step.advancement());
			}
		}
		assertNext(helper, allButLast, Step.MESA_MAYOR);
		// Walked in order, each step points at the one after it and the count keeps up.
		Set<String> walked = new HashSet<>();
		for (int i = 0; i < ForjaPath.STEPS.size(); i++) {
			assertNext(helper, walked, ForjaPath.STEPS.get(i));
			helper.assertTrue(ForjaPath.doneCount(walked::contains) == i, "con " + walked + " hechos la cuenta es " + ForjaPath.doneCount(walked::contains));
			walked.add(ForjaPath.STEPS.get(i).advancement());
		}
		assertNext(helper, walked, null);
		helper.assertTrue(ForjaPath.doneCount(walked::contains) == ForjaPath.STEPS.size(), "el camino entero no cuenta entero");
		helper.succeed();
	}

	private static void assertNext(GameTestHelper helper, Set<String> done, Step expected) {
		Step next = ForjaPath.next(done::contains);
		helper.assertTrue(next == expected, "con " + done + " hechos el siguiente paso debería ser " + expected + ", es " + next);
	}

	/**
	 * One line per step and never the same one twice: the chat speaks only when the next step moves
	 * forward. A whole playthrough in a scrambled order, counted.
	 */
	@GameTest
	public void hintsOnlyWhenThePathMovesOn(GameTestHelper helper) {
		helper.assertTrue(ForjaPath.worthAHint(Step.PLANTILLA, Step.PIEZA), "la primera plantilla debería señalar la pieza");
		helper.assertTrue(!ForjaPath.worthAHint(Step.TEMPLE, Step.TEMPLE), "un paso hecho a destiempo no dice nada");
		helper.assertTrue(!ForjaPath.worthAHint(Step.PIEZA, Step.PLANTILLA), "hacia atrás (un logro revocado) no dice nada");
		helper.assertTrue(ForjaPath.worthAHint(Step.MESA_MAYOR, null), "el final del camino se anuncia");
		helper.assertTrue(!ForjaPath.worthAHint(null, null), "después del final, silencio");

		List<Step> played = List.of(Step.PLANTILLA, Step.PIEZA, Step.FORJA, Step.MEJORA, Step.PARADA, Step.TEMPLE,
			Step.ALEACION, Step.MESA_MAYOR, Step.COLADA);
		Set<String> done = new HashSet<>();
		Step before = ForjaPath.next(done::contains);
		List<String> said = new ArrayList<>();
		for (Step step : played) {
			done.add(step.advancement());
			Step after = ForjaPath.next(done::contains);
			if (ForjaPath.worthAHint(before, after)) {
				said.add(after == null ? "fin" : after.advancement());
			}
			before = after;
		}
		// The quench was missed, so the upgrade and the parry say nothing; doing it jumps straight to the
		// alloy; the greater table before the foundry says nothing; the foundry ends the path.
		List<String> expected = List.of("pieza", "forja", "temple", "aleacion", "colada", "fin");
		helper.assertTrue(said.equals(expected), "la partida desordenada dijo " + said + ", debía decir " + expected);

		// The line itself names the step and the chapter that explains it.
		Component hint = ForjaPath.hint(Step.PIEZA);
		helper.assertTrue(hint.getContents() instanceof TranslatableContents line && line.getKey().equals("gui.forja.camino.pista")
			&& line.getArgs().length == 2
			&& line.getArgs()[0] instanceof Component title && title.getContents() instanceof TranslatableContents titleKey
			&& titleKey.getKey().equals("gui.forja.camino.pieza")
			&& line.getArgs()[1] instanceof Component chapter && chapter.getContents() instanceof TranslatableContents chapterKey
			&& chapterKey.getKey().equals("gui.forja.libro.cap.piezas"), "la pista no nombra el paso y su capítulo: " + hint);
		helper.assertTrue(ForjaPath.hint(null).getContents() instanceof TranslatableContents end && end.getKey().equals("gui.forja.camino.pista.completo"),
			"el final del camino no tiene su propia línea");
		helper.succeed();
	}

	/**
	 * The order is the code's. Every fact the steps' texts state, and every prerequisite that puts one
	 * step after another, read back out of the code and the recipes: if one of them changes, the path
	 * has to change with it.
	 */
	@GameTest
	public void orderFollowsTheCode(GameTestHelper helper) {
		// Pieza: the parts table cuts what is worked cold, and metal has to be cast.
		for (ForgeMaterial cold : List.of(ForgeMaterial.MADERA, ForgeMaterial.PIEDRA, ForgeMaterial.HUESO, ForgeMaterial.CUERO, ForgeMaterial.CUARZO)) {
			helper.assertTrue(cold.isBasic(), cold + " debería cortarse en la mesa de piezas");
		}
		for (ForgeMaterial metal : List.of(ForgeMaterial.HIERRO, ForgeMaterial.COBRE, ForgeMaterial.ORO, ForgeMaterial.DIAMANTE)) {
			helper.assertTrue(!metal.isBasic(), metal + " no debería cortarse: se cuela");
		}
		helper.assertTrue(!Assembler.partResult(PartType.CABEZA_PICO, new ItemStack(ForgeMaterial.PIEDRA.displayStack().getItem(), 64)).isEmpty(),
			"la mesa de piezas debería cortar una cabeza de pico de piedra");
		helper.assertTrue(Assembler.partResult(PartType.CABEZA_PICO, new ItemStack(Items.IRON_INGOT, 64)).isEmpty(),
			"la mesa de piezas no debería cortar hierro");

		// Forja: a pickaxe is a head, a handle and a binding, and the first bench puts it together.
		helper.assertTrue(ForgeType.PICO.slots.equals(List.of(PartType.CABEZA_PICO, PartType.MANGO, PartType.ATADURA)),
			"un pico ya no es cabeza, mango y atadura: " + ForgeType.PICO.slots);
		helper.assertTrue(Station.FORJA.canForge(ForgeType.PICO) && Station.FORJA.canForge(ForgeType.ESPADA),
			"la primera mesa de forja debería montar picos y espadas");
		helper.assertTrue(!Station.PIEZAS.canForge(ForgeType.PICO), "la mesa de piezas no forja");

		// Mejora: sugar is Efficiency on a pickaxe, and the bench takes an upgrade only part of the way.
		Upgrade.Option first = Upgrade.EFICIENCIA.options.getFirst();
		helper.assertTrue(Upgrade.EFICIENCIA.appliesTo(ForgeType.PICO) && first.isSingle()
			&& first.requirements().getFirst().displayStack().is(Items.SUGAR), "el azúcar debería dar Eficiencia a un pico");
		helper.assertTrue(Station.FORJA.capacity() < Station.FORJA_MAYOR.capacity(), "la mesa mayor debería llevar las mejoras más lejos");

		// Parada: a sword off the first bench already guards, so it can parry.
		helper.assertTrue(WeaponGuard.is(Assembler.create(ForgeType.ESPADA, Assembler.defaultMaterials(ForgeType.ESPADA))),
			"una espada forjada debería poder parar");

		// Aleacion: bronze is two copper and one iron, pewter copper and a resin brick, both at a campfire's
		// heat, and a lit campfire under a table is that heat.
		assertAlloy(helper, "bronce", Alloys.Heat.TEMPLADA, Items.COPPER_INGOT, 2, Items.IRON_INGOT, 1);
		assertAlloy(helper, "peltre", Alloys.Heat.TEMPLADA, Items.COPPER_INGOT, 2, Items.RESIN_BRICK, 1);
		BlockPos fire = new BlockPos(1, 1, 1);
		helper.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		Alloys.Heat heat = Alloys.heatUnder(helper.getLevel(), helper.absolutePos(fire.above()));
		helper.assertTrue(heat == Alloys.Heat.TEMPLADA, "una fogata bajo la mesa debería dar calor templado, da " + heat);

		// Colada: the crucible and the box are pewter, the tank bronze, the mould refractory steel, which
		// only a hot table melts; and a clay box stands iron.
		Item pewter = ModItems.alloy("peltre");
		Item bronze = ModItems.alloy("bronce");
		helper.assertTrue(recipeUses(helper, "crisol_de_barro", pewter), "el crisol de barro debería ser de peltre");
		helper.assertTrue(recipeUses(helper, "caja_de_moldeo", pewter), "la caja de moldeo debería ser de peltre");
		helper.assertTrue(recipeUses(helper, "cuba_de_colada", bronze), "la cuba debería ser de bronce");
		helper.assertTrue(alloy(helper, "acero_refractario").heat() == Alloys.Heat.CALIENTE, "el acero refractario debería pedir una mesa caliente");
		helper.assertTrue(ForgeMaterial.HIERRO.durability <= CastingBoxBlock.Tier.BARRO.holds, "la caja de barro debería aguantar hierro");

		// Mesa mayor: built round damascus, which wants lava and netherite scrap, and it makes what the
		// bench will not.
		for (Item ingredient : List.of(ModItems.alloy("damasco"), ModItems.MESA_DE_FORJA, Items.GOLD_INGOT, Items.POLISHED_BLACKSTONE)) {
			helper.assertTrue(recipeUses(helper, "mesa_de_forja_mayor", ingredient), "la mesa mayor debería llevar " + ingredient);
		}
		Alloys.Recipe damascus = alloy(helper, "damasco");
		helper.assertTrue(damascus.heat() == Alloys.Heat.FUNDIDA
			&& damascus.inputs().stream().anyMatch(part -> part.item().get() == Items.NETHERITE_SCRAP),
			"el damasco debería ser acero y chatarra de netherita sobre lava");
		for (ForgeType beyond : List.of(ForgeType.ESPADON, ForgeType.ESCUDO, ForgeType.MANGUAL, ForgeType.ALAS)) {
			helper.assertTrue(Station.FORJA.needsGreater(beyond), "la primera mesa no debería montar " + beyond);
		}

		// And so the heat only goes up along the path: a campfire, then a hot table, then lava.
		helper.assertTrue(Step.ALEACION.ordinal() < Step.COLADA.ordinal() && Step.COLADA.ordinal() < Step.MESA_MAYOR.ordinal()
			&& Alloys.Heat.TEMPLADA.ordinal() < Alloys.Heat.CALIENTE.ordinal() && Alloys.Heat.CALIENTE.ordinal() < Alloys.Heat.FUNDIDA.ordinal(),
			"el camino debería subir de calor paso a paso");
		helper.succeed();
	}

	private static Alloys.Recipe alloy(GameTestHelper helper, String id) {
		Optional<Alloys.Recipe> found = Alloys.POURABLE.stream().filter(recipe -> recipe.id().equals(id)).findFirst();
		helper.assertTrue(found.isPresent(), "no existe la aleación " + id);
		return found.get();
	}

	private static void assertAlloy(GameTestHelper helper, String id, Alloys.Heat heat, Item first, int firstCount, Item second, int secondCount) {
		Alloys.Recipe recipe = alloy(helper, id);
		boolean right = recipe.heat() == heat && recipe.inputs().size() == 2
			&& recipe.inputs().get(0).item().get() == first && recipe.inputs().get(0).count() == firstCount
			&& recipe.inputs().get(1).item().get() == second && recipe.inputs().get(1).count() == secondCount;
		helper.assertTrue(right, "la aleación " + id + " ya no es " + firstCount + " " + first + " y " + secondCount + " " + second + " con calor " + heat);
	}

	private static boolean recipeUses(GameTestHelper helper, String recipe, Item item) {
		var holder = helper.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Forja.id(recipe)));
		if (holder.isEmpty() || !(holder.get().value() instanceof ShapedRecipe shaped)) {
			return false;
		}
		ItemStack stack = new ItemStack(item);
		return shaped.getIngredients().stream().anyMatch(ingredient -> Ingredient.testOptionalIngredient(ingredient, stack));
	}

	/** The two advancements the path added are earned by holding what they are about, and by nothing less. */
	@GameTest
	public void foundryAndGreaterTableAdvancements(GameTestHelper helper) {
		ServerPlayer smith = smith(helper);
		hold(smith, Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.PIEDRA));
		hold(smith, new ItemStack(ModItems.MESA_DE_FORJA));
		// The same trigger, on the same player, does earn the tab's root: the negatives below mean something.
		helper.assertTrue(done(helper, smith, "root"), "tener la mesa de forja debería abrir la pestaña de Forja");
		helper.assertTrue(!done(helper, smith, "colada"), "una pieza cortada en la mesa no es una colada");
		helper.assertTrue(!done(helper, smith, "mesa_mayor"), "la mesa de forja no es la mayor");

		ItemStack cast = Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO);
		cast.set(ModComponents.COLADA, true);
		hold(smith, cast);
		helper.assertTrue(done(helper, smith, "colada"), "una pieza colada debería dar forja/colada");
		hold(smith, new ItemStack(ModItems.MESA_DE_FORJA_MAYOR));
		helper.assertTrue(done(helper, smith, "mesa_mayor"), "la mesa mayor en el inventario debería dar forja/mesa_mayor");

		// The rough pour counts too, and so does a whole tool off a casting table.
		ServerPlayer rough = smith(helper);
		ItemStack roughPart = Assembler.createPart(PartType.HOJA, ForgeMaterial.COBRE);
		roughPart.set(ModComponents.ROUGH, true);
		hold(rough, roughPart);
		helper.assertTrue(done(helper, rough, "colada"), "una pieza basta también es una colada");
		ServerPlayer table = smith(helper);
		ItemStack pick = Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.HIERRO, ForgeMaterial.HIERRO));
		pick.set(ModComponents.COLADAS, (1 << ForgeType.PICO.slots.size()) - 1);
		hold(table, pick);
		helper.assertTrue(done(helper, table, "colada"), "un pico entero de una mesa de colada también es una colada");
		helper.succeed();
	}

	/**
	 * A player who can earn advancements. Not the combat tests' FakePlayer: Fabric keeps fake players out of
	 * advancements altogether. Not placed in the world either, so no other test's mob can see it.
	 */
	private static ServerPlayer smith(GameTestHelper helper) {
		return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
			new GameProfile(UUID.randomUUID(), "forja_camino"), ClientInformation.createDefault());
	}

	private static void hold(ServerPlayer player, ItemStack stack) {
		player.getInventory().add(stack.copy());
		CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), stack);
	}

	private static boolean done(GameTestHelper helper, ServerPlayer player, String id) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Forja.id("forja/" + id));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}
}
