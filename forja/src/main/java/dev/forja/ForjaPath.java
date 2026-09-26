package dev.forja;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The first hours, in order: nine of the mod's own advancements lined up the way the code makes you
 * reach them, for the guide's "Siguiente paso" page and for the line in chat when one of them is done.
 *
 * <p>The order is not a matter of taste. A part needs an engraved template, and the bench cuts only
 * what is worked cold. The forge table builds from parts. A piece leaves the star hot for a minute, so
 * the quench comes straight after it. The first bench takes an upgrade half way. A sword already parries.
 * The crucible and the casting box are made of pewter, which is an alloy, and a mould of refractory
 * steel, which only a hot table melts. And the greater table is built round damascus, which wants lava
 * under the table and netherite scrap on it. PathGameTests reads every one of those facts back out of
 * the code, so if one of them changes it is a test that breaks and not a newcomer.
 *
 * <p>The next step is the first one not done yet, not the one after the furthest: a skipped step is a
 * skipped lesson, and every one of them is cheap to go back for.
 */
public final class ForjaPath {
	public enum Step {
		PLANTILLA("mesas"),
		PIEZA("piezas"),
		FORJA("objetos"),
		TEMPLE("temple"),
		MEJORA("mejoras"),
		PARADA("combate"),
		ALEACION("aleaciones"),
		COLADA("fundicion"),
		MESA_MAYOR("mesas");

		/** The guide chapter that explains it, by the book's own chapter key. */
		public final String chapter;

		Step(String chapter) {
			this.chapter = chapter;
		}

		/** The advancement under forja/ that marks it done, which is always the step's own word. */
		public String advancement() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		/** Counted from one, the way the page says it. */
		public int number() {
			return this.ordinal() + 1;
		}

		public Component title() {
			return Component.translatable("gui.forja.camino." + this.advancement());
		}

		/** What to do, where and with what. The numbers in it are read from the code, not written into it. */
		public Component description() {
			String key = "gui.forja.camino." + this.advancement() + ".desc";
			return switch (this) {
				case TEMPLE -> Component.translatable(key, dev.forja.forge.Temple.HOT_TICKS / 20);
				case MEJORA -> Component.translatable(key, dev.forja.menu.Station.FORJA.capacity());
				case COLADA -> Component.translatable(key, dev.forja.block.entity.CastingBoxBlockEntity.MOULD_COST);
				default -> Component.translatable(key);
			};
		}

		/** What the step is done with, for the page and the checklist. */
		public ItemStack icon() {
			return switch (this) {
				case PLANTILLA -> new ItemStack(ModItems.PLANTILLA);
				case PIEZA -> Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.PIEDRA);
				case FORJA -> Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.PIEDRA, ForgeMaterial.MADERA, ForgeMaterial.CUERO));
				case TEMPLE -> new ItemStack(Items.WATER_BUCKET);
				case MEJORA -> new ItemStack(Items.SUGAR);
				case PARADA -> Assembler.create(ForgeType.ESPADA, Assembler.defaultMaterials(ForgeType.ESPADA));
				case ALEACION -> new ItemStack(ModItems.alloy("bronce"));
				case COLADA -> new ItemStack(ModItems.CAJA_DE_MOLDEO);
				case MESA_MAYOR -> new ItemStack(ModItems.MESA_DE_FORJA_MAYOR);
			};
		}
	}

	public static final List<Step> STEPS = List.of(Step.values());

	private ForjaPath() {
	}

	/**
	 * The step to do next, given which of the forja/ advancements are done (by their id under forja/),
	 * or null once the whole path is walked.
	 */
	public static @Nullable Step next(Predicate<String> done) {
		for (Step step : STEPS) {
			if (!done.test(step.advancement())) {
				return step;
			}
		}
		return null;
	}

	/** How many of the steps are done, in any order. */
	public static int doneCount(Predicate<String> done) {
		int count = 0;
		for (Step step : STEPS) {
			if (done.test(step.advancement())) {
				count++;
			}
		}
		return count;
	}

	/** The step an advancement stands for, or null if it is not on the path. */
	public static @Nullable Step of(String advancement) {
		for (Step step : STEPS) {
			if (step.advancement().equals(advancement)) {
				return step;
			}
		}
		return null;
	}

	/**
	 * Whether the chat should say something, given the next step before and after some advancements came
	 * in: only when the path moved forward. A step done out of order leaves the next one where it was and
	 * says nothing, and the next step only ever moves forward, so no step is announced twice.
	 */
	public static boolean worthAHint(@Nullable Step before, @Nullable Step after) {
		if (before == null) {
			return false;
		}
		return after == null || after.ordinal() > before.ordinal();
	}

	/** The line in chat: the new next step and where the book explains it, or the end of the path. */
	public static Component hint(@Nullable Step next) {
		if (next == null) {
			return Component.translatable("gui.forja.camino.pista.completo").withColor(0xE8C77A);
		}
		return Component.translatable("gui.forja.camino.pista",
			next.title().copy().withColor(0xFFE9A8),
			Component.translatable("gui.forja.libro.cap." + next.chapter)).withColor(0xC9A96A);
	}
}
