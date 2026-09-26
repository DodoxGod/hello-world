package dev.forja.menu;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.Assembler;
import dev.forja.forge.Potential;
import dev.forja.item.UpgradeOrbItem;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import dev.forja.registry.ModMenus;
import dev.forja.upgrade.HiddenEnchantments;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The extraction table: takes one upgrade, and only that one, off a forged piece.
 *
 * <p>Andy's: "una mesa que quita la mejora con los objetos de la misma, y además, el orbe para poder
 * guardar la mejora si quieres usarla después". So: the piece, the upgrade you point at, the things that
 * upgrade is made of, and — if you want to keep it — an empty orb. Without the orb the upgrade is gone.
 * With it, the orb comes out holding <em>all</em> of it, which is what sets this apart from taking the
 * whole piece to bits at the parts table, where an orb keeps half.
 *
 * <p>The price is one step of the upgrade's recipe for every quarter of it that is on the piece: a
 * Filo at 80 % asks for four amethyst, one at 20 % for one. Any of the upgrade's recipes will do. What
 * the sky left behind has no recipe, so it is paid for in glass bottles, and only ever into an orb:
 * there is no reason to pay to throw one away. Pacts do not come off. They never did.
 *
 * <p>Everything else about the piece is left exactly as it was: parts, quality, signature, history,
 * mastery, the other upgrades. Only what the upgrade itself wrote — its hidden enchantment, its
 * attribute — is written again without it. If what comes off is the anneal, the potential drops and
 * whatever now stands above it stays where it is; it just cannot be raised until there is room again.
 */
public class ExtractionMenu extends AbstractContainerMenu {
	public static final int GEAR_SLOT = 0;
	public static final int PAYMENT_FIRST = 1;
	public static final int PAYMENT_COUNT = 3;
	public static final int ORB_SLOT = 4;
	public static final int OUTPUT_SLOT = 5;
	public static final int OWN_SLOTS = 6;

	/**
	 * Where the slots sit, matching textures/gui/mesa_de_extraccion.png. This table has a panel of its own
	 * kind — a wheel with the piece in its hub — and a wider one, so none of these are where the benches
	 * keep theirs: see tools/generate_assets.py, generate_extraction_panel.
	 */
	public static final int GEAR_X = 48;
	public static final int GEAR_Y = 56;
	public static final int OUTPUT_X = 207;
	public static final int OUTPUT_Y = 70;
	public static final int PAYMENT_X = 111;
	public static final int PAYMENT_Y = 70;
	public static final int ORB_X = 178;
	public static final int ORB_Y = 70;
	public static final int INVENTORY_X = 37;
	public static final int INVENTORY_Y = 122;

	/** Button ids: below this, the index of the upgrade to point at; this one, do it. */
	public static final int BUTTON_EXTRACT = 1000;

	/** How much of an upgrade one step of its recipe pays for. */
	public static final int PERCENT_PER_STEP = 25;

	private final Container gear = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			super.setChanged();
			ExtractionMenu.this.slotsChanged(this);
		}
	};
	private final Container payment = new SimpleContainer(PAYMENT_COUNT + 1);
	private final Container output = new SimpleContainer(1);
	private final ContainerLevelAccess access;
	private final DataSlot selected = DataSlot.standalone();

	public ExtractionMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public ExtractionMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(ModMenus.EXTRACCION, id);
		this.access = access;
		this.selected.set(-1);
		this.addSlot(new Slot(this.gear, 0, GEAR_X, GEAR_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.has(ModComponents.PARTS);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		for (int i = 0; i < PAYMENT_COUNT; i++) {
			this.addSlot(new Slot(this.payment, i, PAYMENT_X + i * 20, PAYMENT_Y));
		}
		this.addSlot(new Slot(this.payment, PAYMENT_COUNT, ORB_X, ORB_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ModItems.ORBE_VACIO);
			}
		});
		this.addSlot(new Slot(this.output, 0, OUTPUT_X, OUTPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
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
		this.addDataSlot(this.selected);
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		// Another piece, or none: what was pointed at belonged to the last one.
		if (container == this.gear && this.selected.get() >= this.upgrades().size()) {
			this.selected.set(-1);
		}
	}

	/** The upgrades on the piece in the slot, in the order the screen lists them. */
	public List<Upgrade> upgrades() {
		ItemStack stack = this.gear.getItem(0);
		return new ArrayList<>(stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percents().keySet());
	}

	public ItemStack gear() {
		return this.gear.getItem(0);
	}

	public int percent(Upgrade upgrade) {
		return this.gear().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(upgrade);
	}

	public @Nullable Upgrade selected() {
		List<Upgrade> all = this.upgrades();
		int index = this.selected.get();
		return index >= 0 && index < all.size() ? all.get(index) : null;
	}

	public boolean hasOrb() {
		return this.payment.getItem(PAYMENT_COUNT).is(ModItems.ORBE_VACIO);
	}

	/** Whether this upgrade can come off at all. A pact cannot: that is what makes it one. */
	public static boolean removable(Upgrade upgrade) {
		return !upgrade.isPact();
	}

	/** How many steps of its recipe an upgrade at this percentage costs to take off. */
	public static int steps(int percent) {
		return Math.max(1, (percent + PERCENT_PER_STEP - 1) / PERCENT_PER_STEP);
	}

	/**
	 * What one way of paying for this upgrade asks for, as stacks to show: the first recipe it has, or
	 * glass bottles for the ones that have none.
	 */
	public static List<ItemStack> price(Upgrade upgrade, int percent) {
		int steps = steps(percent);
		List<ItemStack> price = new ArrayList<>();
		if (upgrade.options.isEmpty()) {
			price.add(new ItemStack(Items.GLASS_BOTTLE, steps));
			return price;
		}
		for (Upgrade.Requirement requirement : upgrade.options.getFirst().requirements()) {
			price.add(requirement.displayStack().copyWithCount(steps));
		}
		return price;
	}

	/**
	 * How much to take from each payment slot, or null if what is there does not pay for it. Any of the
	 * upgrade's recipes is accepted, and nothing that is not part of the one being paid may be lying there:
	 * a slot the table would leave untouched is a slot somebody put there expecting it to count.
	 */
	private int @Nullable [] paid(Upgrade upgrade, int percent) {
		int steps = steps(percent);
		List<List<Upgrade.Requirement>> ways = new ArrayList<>();
		if (upgrade.options.isEmpty()) {
			ways.add(List.of(new Upgrade.Requirement(Items.GLASS_BOTTLE, null, Items.GLASS_BOTTLE)));
		}
		for (Upgrade.Option option : upgrade.options) {
			ways.add(option.requirements());
		}
		for (List<Upgrade.Requirement> way : ways) {
			int[] take = new int[PAYMENT_COUNT];
			boolean[] met = new boolean[way.size()];
			boolean stray = false;
			for (int slot = 0; slot < PAYMENT_COUNT; slot++) {
				ItemStack stack = this.payment.getItem(slot);
				if (stack.isEmpty()) {
					continue;
				}
				boolean used = false;
				for (int r = 0; r < way.size() && !used; r++) {
					if (!met[r] && way.get(r).test(stack) && stack.getCount() >= steps) {
						met[r] = true;
						take[slot] = steps;
						used = true;
					}
				}
				stray |= !used;
			}
			boolean all = true;
			for (boolean one : met) {
				all &= one;
			}
			if (all && !stray) {
				return take;
			}
		}
		return null;
	}

	/** Why the button would do nothing, as a key the screen can say, or null when it would work. */
	public @Nullable String blocked() {
		Upgrade upgrade = this.selected();
		if (this.gear().isEmpty()) {
			return "gui.forja.extraccion.pon_pieza";
		}
		if (this.upgrades().isEmpty()) {
			return "gui.forja.extraccion.sin_mejoras";
		}
		if (upgrade == null) {
			return "gui.forja.extraccion.elige";
		}
		if (!removable(upgrade)) {
			return "gui.forja.extraccion.pacto";
		}
		if (upgrade.options.isEmpty() && !this.hasOrb()) {
			return "gui.forja.extraccion.evento_sin_orbe";
		}
		if (this.paid(upgrade, this.percent(upgrade)) == null) {
			return "gui.forja.extraccion.falta_pago";
		}
		if (!this.output.getItem(0).isEmpty()) {
			return "gui.forja.extraccion.salida_llena";
		}
		return null;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id >= 0 && id < this.upgrades().size()) {
			this.selected.set(id);
			return true;
		}
		if (id != BUTTON_EXTRACT || this.blocked() != null) {
			return false;
		}
		Upgrade upgrade = this.selected();
		int percent = this.percent(upgrade);
		int[] take = this.paid(upgrade, percent);
		boolean keep = this.hasOrb();
		if (player.level().isClientSide()) {
			// The client only says whether the press is worth sending; the work is the server's.
			return true;
		}
		ItemStack piece = this.gear().copy();
		ForgedParts parts = piece.get(ModComponents.PARTS);
		Upgrades before = piece.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		java.util.EnumMap<Upgrade, Integer> left = new java.util.EnumMap<>(Upgrade.class);
		left.putAll(before.percents());
		left.remove(upgrade);
		if (left.isEmpty()) {
			piece.remove(ModComponents.UPGRADES);
		} else {
			piece.set(ModComponents.UPGRADES, new Upgrades(left));
		}
		// Only what the upgrade wrote is written again; nothing else about the piece is touched.
		Assembler.rewrite(piece, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		HiddenEnchantments.write(piece, player.level().registryAccess());
		for (int slot = 0; slot < PAYMENT_COUNT; slot++) {
			this.payment.removeItem(slot, take[slot]);
		}
		if (keep) {
			this.payment.removeItem(PAYMENT_COUNT, 1);
			this.output.setItem(0, UpgradeOrbItem.create(upgrade, percent));
		}
		this.gear.setItem(0, piece);
		this.selected.set(-1);
		this.access.execute((level, pos) -> level.playSound(null, pos,
			keep ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.9F, keep ? 1.2F : 0.9F));
		// Parts are not asked about: they were there before and are there after.
		assert parts == piece.get(ModComponents.PARTS) || parts.equals(piece.get(ModComponents.PARTS));
		this.broadcastChanges();
		return true;
	}

	/** The potential the piece would be left with, for the screen to warn about when the anneal comes off. */
	public int potentialAfter(Upgrade upgrade) {
		ItemStack piece = this.gear().copy();
		java.util.EnumMap<Upgrade, Integer> left = new java.util.EnumMap<>(Upgrade.class);
		left.putAll(piece.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percents());
		left.remove(upgrade);
		piece.set(ModComponents.UPGRADES, new Upgrades(left));
		return Potential.of(piece);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < OWN_SLOTS) {
			if (!this.moveItemStackTo(stack, OWN_SLOTS, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.has(ModComponents.PARTS)) {
			if (!this.moveItemStackTo(stack, GEAR_SLOT, GEAR_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(ModItems.ORBE_VACIO)) {
			if (!this.moveItemStackTo(stack, ORB_SLOT, ORB_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, PAYMENT_FIRST, PAYMENT_FIRST + PAYMENT_COUNT, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, ModBlocks.MESA_DE_EXTRACCION);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.access.execute((level, pos) -> {
			this.clearContainer(player, this.gear);
			this.clearContainer(player, this.payment);
			this.clearContainer(player, this.output);
		});
	}
}
