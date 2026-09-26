package dev.forja.menu;

import java.util.ArrayList;
import java.util.List;

import dev.forja.ForjaAdvancements;
import dev.forja.forge.Assembler;
import dev.forja.forge.Mastery;
import dev.forja.forge.Perk;
import dev.forja.item.PartItem;
import dev.forja.item.TemplateItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.UpgradeOrb;
import dev.forja.upgrade.UpgradeRecipes;
import dev.forja.item.UpgradeOrbItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The menu of both work tables.
 * <ul>
 *   <li>Parts table, Piezas: an engraved template plus material gives that part; the template stays.
 *       With a blank template in its slot, the pattern buttons engrave it for good.</li>
 *   <li>Parts table, Desarmar: breaks a finished item back into its parts; worn heads and plates are lost.</li>
 *   <li>Forge table: five slots in a star around the center. Parts on the points forge the gear into the
 *       center; with gear in the center, parts on the points swap those parts, upgrade items raise its
 *       upgrades and its repair material mends it. The forge button does whichever applies.</li>
 * </ul>
 * Slots of inactive tabs are hidden, and switching tabs hands their items back to the player.
 */
public class ForgeMenu extends AbstractContainerMenu {
	public static final int MODE_PARTS = 0;
	public static final int MODE_FORGE = 1;
	public static final int MODE_DISASSEMBLE = 2;
	/** The forge table's second tab: the three choices a smith makes along the way. */
	public static final int MODE_TECHNIQUES = 3;
	/** Button ids below the part count engrave a pattern; these pick a tab or act. */
	public static final int BUTTON_TAB = 100;
	public static final int BUTTON_DISASSEMBLE = 200;
	public static final int BUTTON_FORGE = 300;

	/** A timed press: this plus the quality of the swing, from a miss to a perfect strike. */
	public static final int BUTTON_PRESS = 400;

	/** One per technique, in the enum's order. */
	public static final int BUTTON_TECHNIQUE = 500;

	/** Maestria a donor piece needs before its work can be passed on. */
	public static final int INHERIT_LEVEL = 5;

	/** The share of every upgrade that survives being passed to another piece. */
	public static final float INHERIT_SHARE = 0.5F;

	public static final int IMAGE_WIDTH = 206;
	public static final int IMAGE_HEIGHT = 196;
	public static final int INVENTORY_X = 22;
	public static final int INVENTORY_Y = 114;

	public static final int MATERIAL_SLOT = 0;
	public static final int PART_RESULT_SLOT = 1;
	public static final int TEMPLATE_SLOT = 2;
	public static final int STAR_FIRST = 3;
	public static final int STAR_COUNT = 5;
	public static final int CENTER_SLOT = 8;
	public static final int DISASSEMBLE_SLOT = 9;
	private static final int INVENTORY_START = 10;
	private static final int INVENTORY_END = 46;

	/** Top-left corners of the star's point slots, clockwise from the top, and of the center slot. */
	public static final int[][] STAR_POINTS = {{48, 18}, {80, 42}, {68, 80}, {28, 80}, {16, 42}};
	public static final int CENTER_X = 48;
	public static final int CENTER_Y = 52;

	/** What the forge button would do with the forge table's current contents. */
	public enum Action {
		NONE,
		FORGE,
		SWAP,
		UPGRADE,
		REPAIR,
		BOOK,
		MERGE,
		DON,
		HERENCIA,
		ALEACION,
		/** Parts of one material, over lava, go back to being metal: half of what they cost, rounded down. */
		FUNDIR,
		/** Segunda templada: a master puts a finished piece back in the fire so it can be quenched again. */
		RECALENTAR
	}

	/** One upgrade an enchanted book on the star would raise. */
	public record BookTransfer(Upgrade upgrade, int before, int after) {
	}

	private final Station station;
	private final ContainerLevelAccess access;
	private final Level level;
	private final DataSlot mode = DataSlot.standalone();
	/** How hot the table is standing, synced so the client can show the alloy it would melt. */
	private final DataSlot heat = DataSlot.standalone();

	/** Whether the other two tables are within reach: a whole workshop, and worth something. */
	private final DataSlot workshop = DataSlot.standalone();
	private final SimpleContainer material = this.watched(1);
	private final ResultContainer partResult = new ResultContainer();
	private final SimpleContainer template = this.watched(1);
	private final SimpleContainer star = this.watched(STAR_COUNT);
	private final SimpleContainer center = this.watched(1);
	private final SimpleContainer disassembly = this.watched(1);
	private Action action = Action.NONE;
	private ItemStack forgePreview = ItemStack.EMPTY;
	private @Nullable List<PartType> missingParts;
	/** What this bench cannot build but the greater table could, so the screen can say which. */
	private dev.forja.forge.@Nullable ForgeType beyondBench;
	private @Nullable Refusal refusal;
	private UpgradeRecipes.@Nullable Application application;
	private final int[] repairUse = new int[STAR_COUNT];
	private final Player smith;
	private int inheritSlot = -1;
	private dev.forja.forge.Alloys.@Nullable Recipe alloy;
	private int[] alloyUse = new int[STAR_COUNT];
	private final List<BookTransfer> bookTransfers = new ArrayList<>();
	/**
	 * What each point of the star is left holding once the books and orbs on it have been read: nothing,
	 * for anything used up; the same orb with less in it, for one the piece could only take part of; the
	 * flux untouched, when nothing crossed ninety.
	 */
	private final ItemStack[] bookLeft = new ItemStack[STAR_COUNT];
	private @Nullable Perk perk;
	private long lastSoundTime;

	public ForgeMenu(Station station, int containerId, Inventory inventory) {
		this(station, containerId, inventory, ContainerLevelAccess.NULL);
	}

	public ForgeMenu(Station station, int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(station.menuType(), containerId);
		this.station = station;
		this.access = access;
		this.level = inventory.player.level();
		this.smith = inventory.player;

		this.addSlot(new ModeSlot(this.material, 0, 64, 85, MODE_PARTS) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return ForgeMaterial.fromInput(stack) != null;
			}
		});
		this.addSlot(new ModeSlot(this.partResult, 0, 136, 85, MODE_PARTS) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack carried) {
				PartType type = ForgeMenu.this.selectedPartType();
				if (type != null) {
					ForgeMenu.this.material.removeItem(0, type.cost);
				}
				ForgeMenu.this.playSound(SoundEvents.SMITHING_TABLE_USE);
				ForgeMenu.this.updateResults();
				ForjaAdvancements.award(player, "pieza");
				super.onTake(player, carried);
			}
		});
		this.addSlot(new ModeSlot(this.template, 0, 22, 85, MODE_PARTS) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof TemplateItem;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});

		for (int i = 0; i < STAR_COUNT; i++) {
			this.addSlot(new ModeSlot(this.star, i, STAR_POINTS[i][0], STAR_POINTS[i][1], MODE_FORGE));
		}
		this.addSlot(new ModeSlot(this.center, 0, CENTER_X, CENTER_Y, MODE_FORGE) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.has(ModComponents.PARTS);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});

		this.addSlot(new ModeSlot(this.disassembly, 0, 20, 45, MODE_DISASSEMBLE) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.has(ModComponents.PARTS) || stack.getItem() instanceof PartItem;
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Slot(inventory, 9 + column + row * 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
		}

		this.addDataSlot(this.mode);
		this.addDataSlot(this.heat);
		this.addDataSlot(this.workshop);
		this.mode.set(station.modes.getFirst());
	}

	private SimpleContainer watched(int size) {
		return new SimpleContainer(size) {
			@Override
			public void setChanged() {
				super.setChanged();
				ForgeMenu.this.slotsChanged(this);
			}
		};
	}

	private class ModeSlot extends Slot {
		private final int requiredMode;

		ModeSlot(Container container, int index, int x, int y, int requiredMode) {
			super(container, index, x, y);
			this.requiredMode = requiredMode;
		}

		@Override
		public boolean isActive() {
			return ForgeMenu.this.mode.get() == this.requiredMode;
		}
	}

	public Station station() {
		return this.station;
	}

	public int getMode() {
		return this.mode.get();
	}

	/** How far a table has to look for the rest of the workshop. */
	public static final int WORKSHOP_RANGE = 6;

	/** Whether this table is part of a whole workshop: a parts table and a saddlery within reach. */
	public boolean wholeWorkshop() {
		return this.workshop.get() != 0;
	}

	/** The heat under the table, which is what decides which alloys it can melt. */
	public dev.forja.forge.Alloys.Heat heat() {
		return dev.forja.forge.Alloys.Heat.values()[Math.clamp(this.heat.get(), 0, dev.forja.forge.Alloys.Heat.values().length - 1)];
	}

	/** The shape engraved on the template in the template slot, or null. */
	public @Nullable PartType selectedPartType() {
		return TemplateItem.pattern(this.template.getItem(0));
	}

	public boolean hasTemplate() {
		return !this.template.getItem(0).isEmpty();
	}

	/** A blank template is in the slot, so clicking a pattern engraves it. */
	public boolean canEngrave() {
		return this.hasTemplate() && this.selectedPartType() == null;
	}

	public Action action() {
		return this.action;
	}

	/** The gear the forge button would leave in the center. */
	public ItemStack forgePreview() {
		return this.forgePreview;
	}

	/** Parts the star still needs for the closest recipe. */
	public @Nullable List<PartType> missingParts() {
		return this.missingParts;
	}

	/** The thing this table will not build, when a greater one would. */
	public dev.forja.forge.@Nullable ForgeType beyondBench() {
		return this.beyondBench;
	}

	/**
	 * An upgrade that was on its way onto the piece — from an orb, a book or an inheritance — and was
	 * turned away whole, and by what: the piece has no room for anything that heavy, or it is a heavy
	 * all-or-nothing one and this is a plain bench. Ingredients say the same through their Application.
	 */
	public record Refusal(Upgrade upgrade, dev.forja.forge.Potential.Limit limit) {
	}

	public @Nullable Refusal refusal() {
		return this.refusal;
	}

	private void noteRefusal(Upgrade upgrade, dev.forja.forge.Potential.Ceiling ceiling) {
		if (ceiling.limit() == dev.forja.forge.Potential.Limit.LOAD || ceiling.limit() == dev.forja.forge.Potential.Limit.GREATER) {
			this.refusal = new Refusal(upgrade, ceiling.limit());
		}
	}

	/** What the ingredients on the star would do to the center gear, or null when they match nothing. */
	public UpgradeRecipes.@Nullable Application application() {
		return this.application;
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId >= 0 && buttonId < PartType.values().length && this.station.modes.contains(MODE_PARTS)) {
			ItemStack held = this.template.getItem(0);
			if (!(held.getItem() instanceof TemplateItem)) {
				return false;
			}
			// Engraving is permanent: only blank templates take a shape.
			if (TemplateItem.pattern(held) != null) {
				return false;
			}
			TemplateItem.engrave(held, PartType.values()[buttonId]);
			this.template.setChanged();
			this.playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT);
			this.particles(ParticleTypes.SCRAPE, 12);
			ForjaAdvancements.award(player, "plantilla");
			this.updateResults();
			return true;
		}
		if (buttonId == BUTTON_DISASSEMBLE && this.station.modes.contains(MODE_DISASSEMBLE)) {
			ItemStack forged = this.disassembly.getItem(0);
			if (forged.isEmpty()) {
				return false;
			}
			if (player instanceof ServerPlayer) {
				Assembler.Disassembly salvage = Assembler.disassemble(forged);
				for (ItemStack part : salvage.returned()) {
					player.getInventory().placeItemBackInInventory(part);
				}
				for (ItemStack orb : salvage.orbs()) {
					player.getInventory().placeItemBackInInventory(orb);
				}
				if (!salvage.orbs().isEmpty()) {
					ForjaAdvancements.award(player, "orbe");
				}
				this.disassembly.setItem(0, ItemStack.EMPTY);
				this.playSound(SoundEvents.GRINDSTONE_USE);
				this.particles(ParticleTypes.SMOKE, 12);
				ForjaAdvancements.award(player, "desarmar");
			}
			return true;
		}
		if ((buttonId == BUTTON_FORGE || (buttonId >= BUTTON_PRESS && buttonId <= BUTTON_PRESS + 2)) && this.station.modes.contains(MODE_FORGE)) {
			this.updateResults();
			if (this.action == Action.NONE) {
				return false;
			}
			if (player instanceof ServerPlayer) {
				this.forge(player, buttonId >= BUTTON_PRESS ? buttonId - BUTTON_PRESS : 0);
			}
			return true;
		}
		if (buttonId >= BUTTON_TECHNIQUE && buttonId < BUTTON_TECHNIQUE + dev.forja.forge.Technique.values().length
			&& this.station.modes.contains(MODE_TECHNIQUES)) {
			dev.forja.forge.Technique technique = dev.forja.forge.Technique.values()[buttonId - BUTTON_TECHNIQUE];
			// Checked on both sides: the client has its own Maestria and techniques synced to it.
			if (!dev.forja.forge.Techniques.canLearn(player, technique)) {
				return false;
			}
			dev.forja.forge.Techniques.learn(player, technique);
			this.playSound(SoundEvents.PLAYER_LEVELUP);
			this.particles(ParticleTypes.ENCHANT, 24);
			return true;
		}
		int newMode = buttonId - BUTTON_TAB;
		if (this.station.modes.contains(newMode)) {
			if (newMode != this.mode.get() && player instanceof ServerPlayer) {
				this.clearContainer(player, this.material);
				this.clearContainer(player, this.template);
				this.clearContainer(player, this.disassembly);
			}
			this.mode.set(newMode);
			this.updateResults();
			return true;
		}
		return false;
	}

	private void forge(Player player, int quality) {
		// Taking items off the star recomputes the preview, so keep what this press produces first.
		Action performed = this.action;
		ItemStack result = this.forgePreview.copy();
		UpgradeRecipes.Application applied = this.application;
		int[] repairUse = this.repairUse.clone();
		int[] alloyUse = this.alloyUse.clone();
		switch (performed) {
			case FORGE, SWAP -> {
				for (int i = 0; i < STAR_COUNT; i++) {
					this.star.removeItem(i, 1);
				}
				if (performed == Action.FORGE) {
					// A piece leaves the star signed, still hot, and already broken in by a practised smith.
					dev.forja.forge.Quality.sign(result, player);
					// And with a ceiling on its upgrades that is its own: this press, this smith, this table.
					// What its parts add was marked slot by slot when the star put them together.
					result.set(ModComponents.POTENCIAL, dev.forja.forge.Potential.atForge(quality, player, this.station, this.wholeWorkshop()));
					dev.forja.forge.SmithRecord.add(player, dev.forja.forge.SmithRecord.FORGED);
					if (quality >= 2) {
						dev.forja.forge.SmithRecord.add(player, dev.forja.forge.SmithRecord.PERFECT);
						dev.forja.forge.Quality.markPerfect(result);
						// Every number of the piece goes up, so the components have to be written again.
						Assembler.rewrite(result, net.minecraft.core.registries.BuiltInRegistries.BLOCK, net.minecraft.core.registries.BuiltInRegistries.ITEM);
						dev.forja.forge.SmithLevel.award(player, dev.forja.forge.SmithLevel.XP_PERFECT);
						ForjaAdvancements.award(player, "perfecta");
					}
					dev.forja.forge.Mastery.addExperience(player, result, dev.forja.forge.SmithLevel.masteryHeadStart(player) + quality * 20);
					// Alma de forja: now and then something of the smith stays in the piece.
					if (dev.forja.forge.Techniques.has(player, dev.forja.forge.Technique.ALMA_DE_FORJA)
						&& this.level.getRandom().nextFloat() < dev.forja.forge.Technique.SOUL_CHANCE) {
						this.soul(player, result);
					}
					this.access.execute((level, pos) -> {
						if (level instanceof ServerLevel serverLevel) {
							dev.forja.forge.Temple.markHot(result, serverLevel);
						}
					});
					dev.forja.forge.SmithLevel.award(player, dev.forja.forge.SmithLevel.XP_FORGE);
				}
				this.center.setItem(0, result);
				if (performed == Action.FORGE) {
					this.playSound(quality >= 2 ? SoundEvents.ANVIL_LAND : SoundEvents.ANVIL_USE);
					this.forgeFlourish(result, quality >= 2 ? 34 : 14);
					if (quality >= 2) {
						// A masterwork gets the fire on top of the sparks, which is the only thing that
						// separated a good piece from an ordinary one before.
						this.particles(ParticleTypes.FLAME, 12);
					}
					ForjaAdvancements.forged(player, result);
				} else {
					dev.forja.forge.SmithLevel.award(player, dev.forja.forge.SmithLevel.XP_SWAP);
					this.playSound(SoundEvents.SMITHING_TABLE_USE);
					this.particles(ParticleTypes.CRIT, 14);
					ForjaAdvancements.award(player, "cambio");
				}
			}
			case UPGRADE -> {
				for (int i = 0; i < STAR_COUNT; i++) {
					this.star.removeItem(i, applied.consumed()[i]);
				}
				this.center.setItem(0, result);
				this.playSound(SoundEvents.ENCHANTMENT_TABLE_USE);
				this.particles(ParticleTypes.ENCHANT, 26);
				// And in the upgrade's own colour. Every Upgrade has carried one since they were
				// written and, like the synergies', it only ever reached a tooltip — so applying
				// Vampirismo and applying Escarcha looked like the same purple sparkle.
				this.starBurst(applied.upgrade(), applied.after());
				dev.forja.forge.SmithLevel.award(player, dev.forja.forge.SmithLevel.XP_UPGRADE);
				dev.forja.forge.SmithRecord.add(player, dev.forja.forge.SmithRecord.UPGRADED);
				ForjaAdvancements.upgraded(player, result, applied.after());
			}
			case BOOK -> {
				int best = 0;
				for (BookTransfer transfer : this.bookTransfers) {
					best = Math.max(best, transfer.after());
				}
				ItemStack[] left = this.bookLeft.clone();
				for (int i = 0; i < STAR_COUNT; i++) {
					ItemStack stays = left[i];
					if (stays != null && !stays.isEmpty() && ItemStack.isSameItemSameComponents(stays, this.star.getItem(i))) {
						// Untouched - an orb that gave nothing, flux that was not what made the difference - so it
						// stays as it lies, all of the pile, instead of one of it taking a walk to the inventory.
						continue;
					}
					this.star.removeItem(i, 1);
					if (stays != null && !stays.isEmpty() && this.star.getItem(i).isEmpty()) {
						this.star.setItem(i, stays);
					} else if (stays != null && !stays.isEmpty()) {
						// A pile of more than one: what is left of the orb cannot go back on top of it.
						player.getInventory().placeItemBackInInventory(stays);
					}
				}
				this.center.setItem(0, result);
				this.playSound(SoundEvents.ENCHANTMENT_TABLE_USE);
				this.particles(ParticleTypes.ENCHANT, 60);
				ForjaAdvancements.upgraded(player, result, best);
			}
			case DON -> {
				for (int i = 0; i < STAR_COUNT; i++) {
					this.star.removeItem(i, 1);
				}
				this.center.setItem(0, result);
				this.playSound(SoundEvents.BEACON_POWER_SELECT);
				this.particles(ParticleTypes.END_ROD, 40);
				dev.forja.forge.SmithLevel.award(player, dev.forja.forge.SmithLevel.XP_GIFT);
				ForjaAdvancements.award(player, "don");
			}
			case MERGE -> {
				for (int i = 0; i < STAR_COUNT; i++) {
					this.star.removeItem(i, 1);
				}
				this.center.setItem(0, result);
				this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE);
				this.particles(ParticleTypes.ENCHANT, 30);
				ForjaAdvancements.award(player, "fusion");
			}
			case ALEACION -> {
				for (int i = 0; i < STAR_COUNT; i++) {
					this.star.removeItem(i, alloyUse[i]);
				}
				this.center.setItem(0, result);
				this.playSound(SoundEvents.LAVA_EXTINGUISH);
				this.particles(ParticleTypes.LAVA, 20);
				dev.forja.forge.SmithLevel.award(player, dev.forja.forge.SmithLevel.XP_SWAP);
				ForjaAdvancements.award(player, "aleacion");
			}
			case HERENCIA -> {
				if (this.inheritSlot >= 0) {
					this.star.removeItem(this.inheritSlot, 1);
				}
				this.center.setItem(0, result);
				this.playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE);
				this.particles(ParticleTypes.SOUL, 30);
				dev.forja.forge.SmithLevel.award(player, dev.forja.forge.SmithLevel.XP_UPGRADE);
				ForjaAdvancements.award(player, "herencia");
			}
			case FUNDIR -> {
				for (int i = 0; i < STAR_COUNT; i++) {
					this.star.removeItem(i, 1);
				}
				this.center.setItem(0, result);
				this.playSound(SoundEvents.LAVA_EXTINGUISH);
				this.particles(ParticleTypes.LAVA, 24);
				dev.forja.forge.SmithLevel.award(player, dev.forja.forge.SmithLevel.XP_SWAP);
				ForjaAdvancements.award(player, "fundir");
			}
			case RECALENTAR -> {
				this.access.execute((level, pos) -> {
					if (level instanceof ServerLevel serverLevel) {
						dev.forja.forge.Temple.markHot(result, serverLevel);
					}
				});
				this.center.setItem(0, result);
				this.playSound(SoundEvents.FIRE_AMBIENT);
				this.particles(ParticleTypes.FLAME, 20);
				dev.forja.forge.SmithLevel.award(player, dev.forja.forge.SmithLevel.XP_SWAP);
			}
			case REPAIR -> {
				for (int i = 0; i < STAR_COUNT; i++) {
					this.star.removeItem(i, repairUse[i]);
				}
				this.center.setItem(0, result);
				this.playSound(SoundEvents.ANVIL_USE);
				this.particles(ParticleTypes.HAPPY_VILLAGER, 12);
				ForjaAdvancements.award(player, "reparar");
			}
			default -> {
			}
		}
		// Whatever just happened may have been the last thing the piece needed.
		dev.forja.forge.Masterpiece.crown(this.center.getItem(0), player);
		this.updateResults();
	}

	/** Puts one upgrade the piece accepts on it, as a gift from the smith's own hands. */
	private void soul(Player player, ItemStack result) {
		ForgedParts parts = result.get(ModComponents.PARTS);
		if (parts == null) {
			return;
		}
		List<Upgrade> possible = new ArrayList<>();
		for (Upgrade upgrade : Upgrade.values()) {
			if (upgrade.appliesTo(parts.type())) {
				possible.add(upgrade);
			}
		}
		if (possible.isEmpty()) {
			return;
		}
		Upgrade chosen = possible.get(this.level.getRandom().nextInt(possible.size()));
		Upgrades upgrades = result.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		if (upgrades.percent(chosen) > 0) {
			return;
		}
		result.set(ModComponents.UPGRADES, upgrades.with(chosen, dev.forja.forge.Technique.SOUL_PERCENT));
		Assembler.rewrite(result, net.minecraft.core.registries.BuiltInRegistries.BLOCK, net.minecraft.core.registries.BuiltInRegistries.ITEM);
		if (player instanceof ServerPlayer server) {
			server.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
				"gui.forja.tecnica.alma", chosen.displayName()).withColor(0xF0C070));
		}
	}

	/**
	 * Fundir: parts of one material, on a table standing over lava, go back to being metal. Each part
	 * gives half of what it cost to cut, rounded down and never less than one, so melting is a way out
	 * of a drawer full of wrong parts rather than a way to make material.
	 */
	private boolean planMelt(List<ItemStack> points) {
		if (!this.heat().reaches(dev.forja.forge.Alloys.Heat.FUNDIDA)) {
			return false;
		}
		ForgeMaterial melted = null;
		int total = 0;
		for (ItemStack point : points) {
			if (point.isEmpty()) {
				continue;
			}
			if (!(point.getItem() instanceof PartItem part)) {
				return false;
			}
			ForgeMaterial material = point.get(ModComponents.MATERIAL);
			if (material == null || (melted != null && material != melted)) {
				return false;
			}
			melted = material;
			total += Math.max(1, part.type.cost / 2) * point.getCount();
		}
		if (melted == null || total <= 0) {
			return false;
		}
		ItemStack ingots = this.ingotOf(melted);
		if (ingots.isEmpty()) {
			return false;
		}
		ingots.setCount(Math.min(ingots.getMaxStackSize(), total));
		this.forgePreview = ingots;
		return true;
	}

	/** What one unit of a material looks like as an item, alloys and tagged materials included. */
	private ItemStack ingotOf(ForgeMaterial material) {
		var items = this.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM);
		return material.repairItems(items).stream().findFirst()
			.map(holder -> new ItemStack(holder.value()))
			.orElse(ItemStack.EMPTY);
	}

	/**
	 * Segunda templada: the piece alone on the star, over a table hot enough to melt, goes back to being
	 * freshly forged for a minute. Only a master can do it, and only to a piece that has cooled.
	 */
	private boolean planReheat(ItemStack gear, List<ItemStack> points) {
		if (!dev.forja.forge.Techniques.has(this.smith, dev.forja.forge.Technique.SEGUNDA_TEMPLADA)
			|| !gear.has(ModComponents.PARTS) || !this.heat().reaches(dev.forja.forge.Alloys.Heat.CALIENTE)) {
			return false;
		}
		for (ItemStack point : points) {
			if (!point.isEmpty()) {
				return false;
			}
		}
		if (dev.forja.forge.Temple.hot(gear, this.level.getGameTime())) {
			return false;
		}
		ItemStack reheated = gear.copy();
		reheated.remove(ModComponents.TEMPLE);
		this.forgePreview = reheated;
		return true;
	}

	/**
	 * Herencia: an old piece of the same kind, worn to Maestria five, gives half of everything it learned
	 * to the one on the center, and its gift with it if the new one has none. The old piece is spent.
	 */
	private boolean planInherit(ItemStack gear, List<ItemStack> points) {
		this.inheritSlot = -1;
		ForgedParts target = gear.get(ModComponents.PARTS);
		if (target == null || gear.isBroken()) {
			return false;
		}
		for (int i = 0; i < points.size(); i++) {
			ItemStack donor = points.get(i);
			ForgedParts donorParts = donor.get(ModComponents.PARTS);
			if (donorParts == null || donor == gear || donorParts.type() != target.type()
				|| dev.forja.forge.Mastery.level(donor) < INHERIT_LEVEL || donor.isBroken()) {
				continue;
			}
			Upgrades inherited = gear.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
			boolean any = false;
			// Asked of the heir as it stands after each one, because every upgrade taken leaves less room for
			// the next; and what weighs nothing goes first, since a pact handed down is room handed down.
			ItemStack heir = gear.copy();
			List<java.util.Map.Entry<Upgrade, Integer>> handed = new ArrayList<>(donor.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percents().entrySet());
			handed.sort(java.util.Comparator.comparingInt(one -> dev.forja.forge.Potential.weight(one.getKey()) == 0 ? 0 : 1));
			for (var entry : handed) {
				int passed = Math.round(entry.getValue() * (dev.forja.forge.Techniques.has(this.smith, dev.forja.forge.Technique.HERENCIA_LIMPIA)
					? dev.forja.forge.Technique.INHERIT_SHARE
					: INHERIT_SHARE));
				if (passed <= 0 || !entry.getKey().appliesTo(target.type())) {
					continue;
				}
				int before = inherited.percent(entry.getKey());
				// What is handed down is worth what it cost, like an orb, and the heir takes it as far as its
				// own potential and this table allow: an inheritance is not a way round either.
				dev.forja.forge.Potential.Ceiling ceiling = dev.forja.forge.Potential.ceiling(heir, entry.getKey(), this.station, false);
				this.noteRefusal(entry.getKey(), ceiling);
				int after = Math.min(ceiling.percent(), dev.forja.forge.Potential.raised(before, dev.forja.forge.Potential.value(passed)));
				if (after > before) {
					inherited = inherited.with(entry.getKey(), after);
					heir.set(ModComponents.UPGRADES, inherited);
					any = true;
				}
			}
			String don = donor.get(ModComponents.DON);
			boolean passesGift = don != null && !gear.has(ModComponents.DON);
			if (!any && !passesGift) {
				continue;
			}
			ItemStack result = gear.copy();
			result.set(ModComponents.UPGRADES, inherited);
			if (passesGift) {
				result.set(ModComponents.DON, don);
			}
			this.forgePreview = result;
			this.inheritSlot = i;
			return true;
		}
		return false;
	}

	private void particles(ParticleOptions particle, int count) {
		this.access.execute((level, pos) -> {
			if (level instanceof ServerLevel serverLevel) {
				serverLevel.sendParticles(particle, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, count, 0.25, 0.1, 0.25, 0.02);
			}
		});
	}

	/**
	 * The star lighting up in the colour of the upgrade that just went into it.
	 *
	 * <p>The ring is as wide as the upgrade is strong: a first orb draws a small one and the one that
	 * takes it to a hundred draws a full circle round the table. You can see how far along a piece is
	 * from across the workshop, which is the sort of thing a tooltip cannot do.
	 */
	private void starBurst(dev.forja.upgrade.Upgrade upgrade, int after) {
		this.access.execute((level, pos) -> {
			if (!(level instanceof ServerLevel serverLevel)) {
				return;
			}
			double x = pos.getX() + 0.5;
			double y = pos.getY() + 1.08;
			double z = pos.getZ() + 0.5;
			var dust = new net.minecraft.core.particles.DustParticleOptions(upgrade.color, 1.1F);
			serverLevel.sendParticles(dust, x, y, z, 18, 0.28, 0.12, 0.28, 0.02);
			double radius = 0.5 + after / 100.0 * 0.9;
			int points = 14 + after / 8;
			for (int step = 0; step < points; step++) {
				double angle = step * Math.PI * 2.0 / points;
				serverLevel.sendParticles(dust,
					x + Math.cos(angle) * radius, y + 0.05, z + Math.sin(angle) * radius,
					1, 0.02, 0.01, 0.02, 0.0);
			}
		});
	}

	/**
	 * The shower off a piece that has just been struck, in the colour of what it is made of.
	 *
	 * <p>Forging used to throw vanilla flame at you and that was that — the same puff whether you had
	 * made a wooden haft or a star-iron head. The spark is the mod's own and the dust takes the
	 * material's colour, so what comes off the anvil tells you what you made before you pick it up.
	 */
	private void forgeFlourish(ItemStack result, int count) {
		this.access.execute((level, pos) -> {
			if (!(level instanceof ServerLevel serverLevel)) {
				return;
			}
			double x = pos.getX() + 0.5;
			double y = pos.getY() + 1.05;
			double z = pos.getZ() + 0.5;
			serverLevel.sendParticles(dev.forja.registry.ModParticles.CHISPA, x, y, z, count, 0.22, 0.08, 0.22, 0.35);
			// The parts live on the stack as a component; the primary material is the one the piece
			// reads as, so it is the one the dust takes its colour from.
			dev.forja.part.ForgedParts parts = result.get(dev.forja.registry.ModComponents.PARTS);
			if (parts == null || parts.primary() == null) {
				return;
			}
			int colour = parts.primary().color;
			serverLevel.sendParticles(
				new net.minecraft.core.particles.DustParticleOptions(colour, 1.1F),
				x, y + 0.1, z, Math.max(6, count / 2), 0.3, 0.16, 0.3, 0.0);
		});
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		this.updateResults();
	}

	private void updateResults() {
		PartType type = this.selectedPartType();
		this.partResult.setItem(0, type == null ? ItemStack.EMPTY : Assembler.partResult(type, this.material.getItem(0)));
		this.updateForge();
		this.broadcastChanges();
	}

	private void updateForge() {
		this.action = Action.NONE;
		this.alloy = null;
		this.forgePreview = ItemStack.EMPTY;
		this.bookTransfers.clear();
		this.perk = null;
		this.missingParts = null;
		this.application = null;
		this.refusal = null;

		List<ItemStack> points = contents(this.star);
		boolean anyPoint = points.stream().anyMatch(stack -> !stack.isEmpty());
		boolean onlyParts = points.stream().allMatch(stack -> stack.isEmpty() || stack.getItem() instanceof PartItem);
		ItemStack gear = this.center.getItem(0);
		if (!anyPoint && gear.isEmpty()) {
			return;
		}

		// What the table is standing on decides what it can melt; the server reads it, the client is told.
		this.access.execute((level, pos) -> {
			this.heat.set(dev.forja.forge.Alloys.heatUnder(level, pos).ordinal());
			// A forge on its own is a forge; with the parts table and the saddlery around it, it is a workshop.
			boolean parts = false;
			boolean saddlery = false;
			for (net.minecraft.core.BlockPos at : net.minecraft.core.BlockPos.betweenClosed(
				pos.offset(-WORKSHOP_RANGE, -2, -WORKSHOP_RANGE), pos.offset(WORKSHOP_RANGE, 2, WORKSHOP_RANGE)
			)) {
				net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
				parts |= state.is(dev.forja.registry.ModBlocks.MESA_DE_PIEZAS);
				saddlery |= state.is(dev.forja.registry.ModBlocks.MESA_DE_TALABARTERIA);
				// His anvil is worth both of them.
				if (state.is(dev.forja.registry.ModBlocks.YUNQUE_DEL_HERRERO)) {
					parts = true;
					saddlery = true;
				}
			}
			this.workshop.set(parts && saddlery ? 1 : 0);
		});
		if (!anyPoint) {
			if (this.planReheat(gear, points)) {
				this.action = Action.RECALENTAR;
			}
			return;
		}

		if (gear.isEmpty() && !onlyParts) {
			// Fuelle largo: the smith works the fire well enough to melt one step colder than the sheet says.
			dev.forja.forge.Alloys.Heat read = dev.forja.forge.Techniques.has(this.smith, dev.forja.forge.Technique.FUELLE_LARGO)
				? this.heat().hotter()
				: this.heat();
			this.alloy = dev.forja.forge.Alloys.match(points, read);
			if (this.alloy != null) {
				this.alloyUse = dev.forja.forge.Alloys.consumption(this.alloy, points);
				this.forgePreview = this.alloy.result();
				// Ojo para el metal: the pour goes further than it should.
				if (dev.forja.forge.Techniques.has(this.smith, dev.forja.forge.Technique.OJO_PARA_EL_METAL)) {
					this.forgePreview.grow(dev.forja.forge.Technique.ALLOY_EXTRA);
				}
				this.action = Action.ALEACION;
				return;
			}
		}

		if (onlyParts && gear.isEmpty() && this.planMelt(points)) {
			this.action = Action.FUNDIR;
			return;
		}

		if (onlyParts) {
			List<ItemStack> inputs = new ArrayList<>();
			if (!gear.isEmpty()) {
				inputs.add(gear);
			}
			for (ItemStack point : points) {
				if (!point.isEmpty()) {
					inputs.add(point.copyWithCount(1));
				}
			}
			Assembler.Result result = Assembler.evaluate(inputs, this.level.registryAccess());
			this.forgePreview = result.stack();
			// Not every table builds everything: barding is the saddlery's alone, and half the arsenal
			// is beyond a plain bench.
			ForgedParts preview = this.forgePreview.get(ModComponents.PARTS);
			if (preview != null && !this.station.canForge(preview.type())) {
				// Say so when a greater table would manage it, or the smith is left staring at a star
				// that simply does nothing.
				this.beyondBench = this.station.needsGreater(preview.type()) ? preview.type() : null;
				this.forgePreview = ItemStack.EMPTY;
				this.missingParts = null;
				return;
			}
			this.beyondBench = null;
			this.missingParts = gear.isEmpty() ? result.missing() : null;
			if (!this.forgePreview.isEmpty()) {
				this.action = gear.isEmpty() ? Action.FORGE : Action.SWAP;
			}
		} else if (!gear.isEmpty() && this.planPerk(gear, points)) {
			this.action = Action.DON;
		} else if (gear.isEmpty() && this.planMerge(points)) {
			this.action = Action.MERGE;
		} else if (!gear.isEmpty() && this.planReheat(gear, points)) {
			this.action = Action.RECALENTAR;
		} else if (!gear.isEmpty() && this.planInherit(gear, points)) {
			this.action = Action.HERENCIA;
		} else if (!gear.isEmpty() && this.planBooks(gear, points)) {
			this.action = Action.BOOK;
		} else if (!gear.isEmpty()) {
			// A whole workshop is worth as much as another level of smith when the ingredients go in.
			int bonus = dev.forja.forge.SmithLevel.upgradeBonus(this.smith) + (this.wholeWorkshop() ? 1 : 0)
				+ (dev.forja.forge.Techniques.has(this.smith, dev.forja.forge.Technique.MANO_DE_ORFEBRE) ? dev.forja.forge.Technique.ORB_BONUS : 0);
			this.application = UpgradeRecipes.apply(gear, points, this.level.registryAccess(), bonus,
				(upgrade, flux) -> dev.forja.forge.Potential.ceiling(gear, upgrade, this.station, flux));
			if (this.application != null && this.application.conflict() == null && !this.application.result().isEmpty()) {
				this.forgePreview = this.application.result();
				this.action = Action.UPGRADE;
			} else if (this.planHeartRepair(gear, points) || this.planRepair(gear, points)) {
				this.application = null;
				this.action = Action.REPAIR;
			}
		}
	}

	/**
	 * Repair material on the star mends the center gear a quarter of its durability per item, like the
	 * anvil, using only what it needs. Upgrades win when the same items could do both.
	 */
	/** The heart of the fallen smith puts any forged piece back the way it was, once. */
	private boolean planHeartRepair(ItemStack gear, List<ItemStack> points) {
		if (!gear.isDamaged()) {
			return false;
		}
		for (int i = 0; i < points.size(); i++) {
			if (points.get(i).is(dev.forja.registry.ModItems.CORAZON_DE_FORJA)) {
				// Whole again, and no longer broken: the heart is the only thing that does both at once.
				ItemStack result = gear.copy();
				result.setDamageValue(0);
				this.forgePreview = result;
				java.util.Arrays.fill(this.repairUse, 0);
				this.repairUse[i] = 1;
				return true;
			}
		}
		return false;
	}

	private boolean planRepair(ItemStack gear, List<ItemStack> points) {
		java.util.Arrays.fill(this.repairUse, 0);
		if (!gear.isDamaged()) {
			return false;
		}
		for (ItemStack point : points) {
			if (!point.isEmpty() && !gear.isValidRepairItem(point)) {
				return false;
			}
		}
		int perItem = Math.max(1, gear.getMaxDamage() / 4);
		// Ahorro de metal: the same ingot closes a third more of the damage.
		if (dev.forja.forge.Techniques.has(this.smith, dev.forja.forge.Technique.AHORRO_DE_METAL)) {
			perItem = perItem * dev.forja.forge.Technique.REPAIR_NUMERATOR / dev.forja.forge.Technique.REPAIR_DENOMINATOR;
		}
		int damage = gear.getDamageValue();
		for (int i = 0; i < STAR_COUNT && damage > 0; i++) {
			int take = Math.min(points.get(i).getCount(), (damage + perItem - 1) / perItem);
			this.repairUse[i] = take;
			damage = Math.max(0, damage - take * perItem);
		}
		ItemStack repaired = gear.copy();
		repaired.setDamageValue(damage);
		this.forgePreview = repaired;
		return true;
	}

	/**
	 * Enchanted books and upgrade orbs on the star become upgrades. Each enchantment that matches an
	 * upgrade the gear accepts sets it to the level's percentage (Sharpness III of V is 60%), never
	 * lowering it; an orb adds its percentage. Both skip upgrades that clash with ones already there.
	 */
	private boolean planBooks(ItemStack gear, List<ItemStack> points) {
		ForgedParts parts = gear.get(ModComponents.PARTS);
		java.util.Arrays.fill(this.bookLeft, null);
		boolean anyBook = false;
		int fluxSlot = -1;
		for (int i = 0; i < points.size(); i++) {
			ItemStack point = points.get(i);
			if (point.isEmpty()) {
				continue;
			}
			if (dev.forja.forge.Potential.isFlux(point)) {
				if (fluxSlot >= 0) {
					return false;
				}
				fluxSlot = i;
				continue;
			}
			boolean book = point.is(Items.ENCHANTED_BOOK) && point.has(DataComponents.STORED_ENCHANTMENTS);
			if (!book && UpgradeOrbItem.orb(point) == null) {
				return false;
			}
			anyBook = true;
		}
		if (!anyBook || parts == null) {
			return false;
		}
		boolean flux = fluxSlot >= 0;
		boolean crossed = false;
		ItemStack result = gear;
		for (int i = 0; i < points.size(); i++) {
			ItemStack point = points.get(i);
			if (point.isEmpty() || i == fluxSlot) {
				continue;
			}
			UpgradeOrb orb = UpgradeOrbItem.orb(point);
			if (orb != null) {
				// Whatever happens, an orb that is not emptied stays an orb.
				this.bookLeft[i] = point.copyWithCount(1);
				Upgrades current = result.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
				int before = current.percent(orb.upgrade());
				boolean clashes = current.percents().keySet().stream().anyMatch(existing -> !orb.upgrade().isCompatibleWith(existing));
				if (!orb.upgrade().appliesTo(parts.type()) || clashes) {
					continue;
				}
				dev.forja.forge.Potential.Ceiling held = dev.forja.forge.Potential.ceiling(result, orb.upgrade(), this.station, flux);
				this.noteRefusal(orb.upgrade(), held);
				int ceiling = held.percent();
				// An orb holds what its percentage cost, not the percentage: on a bare piece that comes to
				// the same thing, and on top of what is already there it pays the rising price.
				int worth = dev.forja.forge.Potential.value(orb.percent());
				int after = Math.min(ceiling, dev.forja.forge.Potential.raised(before, worth));
				if (after <= before) {
					continue;
				}
				int spent = dev.forja.forge.Potential.value(after) - dev.forja.forge.Potential.value(before);
				int kept = percentWorth(Math.max(0, worth - spent));
				this.bookLeft[i] = kept > 0 ? UpgradeOrbItem.create(orb.upgrade(), kept) : ItemStack.EMPTY;
				crossed |= before <= dev.forja.forge.Potential.WITHOUT_FLUX && after > dev.forja.forge.Potential.WITHOUT_FLUX
					&& dev.forja.forge.Potential.needsFlux(orb.upgrade());
				result = UpgradeRecipes.upgraded(result, parts.type(), orb.upgrade(), after, this.level.registryAccess());
				this.bookTransfers.add(new BookTransfer(orb.upgrade(), before, after));
				continue;
			}
			ItemEnchantments stored = point.get(DataComponents.STORED_ENCHANTMENTS);
			for (Holder<Enchantment> enchantment : stored.keySet()) {
				for (Upgrade upgrade : Upgrade.values()) {
					if (upgrade.enchantment == null || !enchantment.is(upgrade.enchantment) || !upgrade.appliesTo(parts.type())) {
						continue;
					}
					Upgrades current = result.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
					int before = current.percent(upgrade);
					dev.forja.forge.Potential.Ceiling held = dev.forja.forge.Potential.ceiling(result, upgrade, this.station, flux);
					this.noteRefusal(upgrade, held);
					int ceiling = held.percent();
					int after = Math.min(ceiling, (stored.getLevel(enchantment) * 100 + upgrade.maxLevel - 1) / upgrade.maxLevel);
					boolean clashes = current.percents().keySet().stream().anyMatch(existing -> !upgrade.isCompatibleWith(existing));
					if (after > before && !clashes) {
						crossed |= before <= dev.forja.forge.Potential.WITHOUT_FLUX && after > dev.forja.forge.Potential.WITHOUT_FLUX
							&& dev.forja.forge.Potential.needsFlux(upgrade);
						result = UpgradeRecipes.upgraded(result, parts.type(), upgrade, after, this.level.registryAccess());
						this.bookTransfers.add(new BookTransfer(upgrade, before, after));
					}
				}
			}
		}
		if (this.bookTransfers.isEmpty()) {
			return false;
		}
		if (flux && !crossed) {
			// The flux was not what made the difference, so it is not what gets spent.
			this.bookLeft[fluxSlot] = points.get(fluxSlot).copyWithCount(1);
		}
		this.forgePreview = result;
		return true;
	}

	/** The highest percentage this much value still buys from nothing: what an orb with that left in it reads. */
	private static int percentWorth(int value) {
		return dev.forja.forge.Potential.raised(0, value);
	}

	/**
	 * A Maestria 10 item with no gift yet takes one when a single seal sits on the star, and only if the
	 * gift stamped on that seal is one this kind of gear can hold.
	 */
	private boolean planPerk(ItemStack gear, List<ItemStack> points) {
		ForgedParts parts = gear.get(ModComponents.PARTS);
		if (parts == null || Mastery.level(gear) < Perk.LEVEL || gear.has(ModComponents.DON)) {
			return false;
		}
		ItemStack token = ItemStack.EMPTY;
		for (ItemStack point : points) {
			if (point.isEmpty()) {
				continue;
			}
			if (!token.isEmpty()) {
				return false;
			}
			token = point;
		}
		Perk perk = token.isEmpty() ? null : Perk.fromSeal(parts.type(), token);
		if (perk == null) {
			return false;
		}
		this.perk = perk;
		this.forgePreview = Assembler.engrave(gear, perk, this.level.registryAccess());
		return !this.forgePreview.isEmpty();
	}

	/** The gift the star would engrave, for the screen to name. */
	public @Nullable Perk perk() {
		return this.perk;
	}

	/** Two or more orbs of the same upgrade on an empty star fuse into one worth what they were worth together. */
	private boolean planMerge(List<ItemStack> points) {
		Upgrade upgrade = null;
		int total = 0;
		int orbs = 0;
		for (ItemStack point : points) {
			if (point.isEmpty()) {
				continue;
			}
			UpgradeOrb orb = UpgradeOrbItem.orb(point);
			if (orb == null || (upgrade != null && orb.upgrade() != upgrade)) {
				return false;
			}
			upgrade = orb.upgrade();
			// What they cost, added; not what they read. Two halves are not a whole: the second half of an
			// upgrade costs twice the first, whether it is paid in ingredients or in orbs.
			total += dev.forja.forge.Potential.value(orb.percent());
			orbs++;
		}
		if (orbs < 2) {
			return false;
		}
		this.forgePreview = UpgradeOrbItem.create(upgrade, Math.max(1, percentWorth(total)));
		return true;
	}

	/** What the enchanted books on the star would do. */
	public List<BookTransfer> bookTransfers() {
		return this.bookTransfers;
	}

	/** How many items of each star point the repair would use. */
	public int[] repairUse() {
		return this.repairUse;
	}

	private static List<ItemStack> contents(SimpleContainer container) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			stacks.add(container.getItem(i));
		}
		return stacks;
	}

	private void playSound(SoundEvent sound) {
		this.access.execute((level, pos) -> {
			long time = level.getGameTime();
			if (this.lastSoundTime != time) {
				level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.8F, 1.0F);
				this.lastSoundTime = time;
			}
		});
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, this.station.block());
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return !(target.container instanceof ResultContainer) && super.canTakeItemForPickAll(carried, target);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (!slot.hasItem() || !slot.isActive()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int mode = this.mode.get();
		if (slotIndex == PART_RESULT_SLOT) {
			if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, original);
		} else if (slotIndex < INVENTORY_START) {
			if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (mode == MODE_PARTS && stack.getItem() instanceof TemplateItem && this.template.isEmpty()) {
			this.template.setItem(0, stack.split(1));
		} else if (mode == MODE_PARTS && ForgeMaterial.fromInput(stack) != null) {
			if (!this.moveItemStackTo(stack, MATERIAL_SLOT, MATERIAL_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (mode == MODE_FORGE && stack.has(ModComponents.PARTS) && this.center.isEmpty()) {
			this.center.setItem(0, stack.split(1));
		} else if (mode == MODE_FORGE && (stack.getItem() instanceof PartItem || UpgradeOrbItem.orb(stack) != null || stack.is(Items.ENCHANTED_BOOK))) {
			if (!this.moveOneInto(stack, this.star)) {
				return ItemStack.EMPTY;
			}
		} else if (mode == MODE_FORGE && !stack.has(ModComponents.PARTS)) {
			if (!this.moveItemStackTo(stack, STAR_FIRST, STAR_FIRST + STAR_COUNT, false)) {
				return ItemStack.EMPTY;
			}
		} else if (mode == MODE_DISASSEMBLE && (stack.has(ModComponents.PARTS) || stack.getItem() instanceof PartItem) && this.disassembly.isEmpty()) {
			this.disassembly.setItem(0, stack.split(1));
		} else if (slotIndex < INVENTORY_START + 27) {
			if (!this.moveItemStackTo(stack, INVENTORY_START + 27, INVENTORY_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_START + 27, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}

	/** Shift-clicking a part, orb or book puts one onto the first empty star point, since the star uses one of each. */
	private boolean moveOneInto(ItemStack stack, SimpleContainer container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).isEmpty()) {
				container.setItem(i, stack.split(1));
				return true;
			}
		}
		return false;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.partResult.removeItemNoUpdate(0);
		this.access.execute((level, pos) -> {
			this.clearContainer(player, this.material);
			this.clearContainer(player, this.template);
			this.clearContainer(player, this.star);
			this.clearContainer(player, this.center);
			this.clearContainer(player, this.disassembly);
		});
	}
}
