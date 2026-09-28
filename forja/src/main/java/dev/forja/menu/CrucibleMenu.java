package dev.forja.menu;

import dev.forja.block.entity.CrucibleBlockEntity;
import dev.forja.registry.ModItems;
import dev.forja.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The crucible's screen, server side.
 *
 * <p>Four slots and five numbers. The numbers are what makes the screen worth looking at: how far the
 * pour has got, how much ember is left, how full the pot is and what it is allowed to reach, so the
 * screen can draw the melt rather than print a percentage at you.
 */
public class CrucibleMenu extends AbstractContainerMenu {
	public static final int DATA_PROGRESS = 0;
	public static final int DATA_COOK = 1;
	public static final int DATA_BURNING = 2;
	public static final int DATA_BURN_LENGTH = 3;
	public static final int DATA_HEAT = 4;
	public static final int DATA_CAPACITY = 5;
	/** What the pot is doing (CrucibleBlockEntity.JOB_*), and with what. */
	public static final int DATA_JOB = 6;
	public static final int DATA_WHAT = 7;
	public static final int DATA_SIZE = 8;

	/** Where the four slots sit, and where the screen draws its basin, matching textures/gui/crisol.png. */
	public static final int FIRST_X = 30;
	public static final int FIRST_Y = 26;
	public static final int SECOND_X = 30;
	public static final int SECOND_Y = 50;
	public static final int FUEL_X = 30;
	public static final int FUEL_Y = 82;
	public static final int OUTPUT_X = 148;
	public static final int OUTPUT_Y = 47;
	/** Where textures/gui paints the player's inventory on every one of Forja's panels. */
	public static final int INVENTORY_X = 22;
	public static final int INVENTORY_Y = 114;

	private final Container crucible;
	private final ContainerData data;

	public CrucibleMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(CrucibleBlockEntity.SIZE), new SimpleContainerData(DATA_SIZE));
	}

	public CrucibleMenu(int id, Inventory inventory, Container crucible, ContainerData data) {
		super(ModMenus.CRISOL, id);
		checkContainerSize(crucible, CrucibleBlockEntity.SIZE);
		checkContainerDataCount(data, DATA_SIZE);
		this.crucible = crucible;
		this.data = data;

		// The two input slots take only what the pot can use, and never more between them than the pot
		// holds: both rules were only ever written for hoppers, and a click, a shift-click or a drag put a
		// stick, a sword or a whole stack in a clay pot that holds eight.
		this.addSlot(new InputSlot(crucible, CrucibleBlockEntity.SLOT_FIRST, FIRST_X, FIRST_Y, CrucibleBlockEntity.SLOT_SECOND));
		this.addSlot(new InputSlot(crucible, CrucibleBlockEntity.SLOT_SECOND, SECOND_X, SECOND_Y, CrucibleBlockEntity.SLOT_FIRST));
		this.addSlot(new Slot(crucible, CrucibleBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ModItems.ASCUA);
			}
		});
		this.addSlot(new Slot(crucible, CrucibleBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y) {
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
		this.addDataSlots(data);
	}

	/** One of the two slots the pot is filled through. */
	private final class InputSlot extends Slot {
		private final int other;

		InputSlot(Container container, int slot, int x, int y, int other) {
			super(container, slot, x, y);
			this.other = other;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return CrucibleBlockEntity.takes(stack) && this.getMaxStackSize(stack) > 0;
		}

		/** What is left of the pot's capacity once the other slot has had its share. */
		@Override
		public int getMaxStackSize(ItemStack stack) {
			int left = CrucibleMenu.this.capacity() - CrucibleMenu.this.crucible.getItem(this.other).getCount();
			return Math.max(0, Math.min(super.getMaxStackSize(stack), left));
		}
	}

	/** What the pot is doing, as CrucibleBlockEntity.JOB_*. */
	public int job() {
		return this.data.get(DATA_JOB);
	}

	/** And with what: an index into Alloys.POURABLE for an alloy, a material's ordinal otherwise. */
	public int jobWhat() {
		return this.data.get(DATA_WHAT);
	}

	/** How far along the pour is, 0..1. */
	public float pourProgress() {
		int cook = this.data.get(DATA_COOK);
		return cook <= 0 ? 0.0F : Math.min(1.0F, (float) this.data.get(DATA_PROGRESS) / cook);
	}

	/** How much ember is left, 0..1. */
	public float emberLeft() {
		int length = this.data.get(DATA_BURN_LENGTH);
		return length <= 0 ? 0.0F : Math.min(1.0F, (float) this.data.get(DATA_BURNING) / length);
	}

	public boolean isLit() {
		return this.data.get(DATA_BURNING) > 0;
	}

	/** The hottest this crucible will ever get, as an ordinal into Alloys.Heat. */
	public int heat() {
		return this.data.get(DATA_HEAT);
	}

	public int capacity() {
		return this.data.get(DATA_CAPACITY);
	}

	/** How much of the capacity is taken, 0..1, which is what the level in the basin is drawn from. */
	public float fill() {
		int capacity = this.capacity();
		if (capacity <= 0) {
			return 0.0F;
		}
		int inside = this.crucible.getItem(CrucibleBlockEntity.SLOT_FIRST).getCount()
			+ this.crucible.getItem(CrucibleBlockEntity.SLOT_SECOND).getCount();
		return Math.min(1.0F, (float) inside / capacity);
	}

	public ItemStack first() {
		return this.crucible.getItem(CrucibleBlockEntity.SLOT_FIRST);
	}

	public ItemStack second() {
		return this.crucible.getItem(CrucibleBlockEntity.SLOT_SECOND);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int inventoryStart = CrucibleBlockEntity.SIZE;
		if (index < inventoryStart) {
			if (!this.moveItemStackTo(stack, inventoryStart, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(ModItems.ASCUA)) {
			if (!this.moveItemStackTo(stack, CrucibleBlockEntity.SLOT_FUEL, CrucibleBlockEntity.SLOT_FUEL + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, CrucibleBlockEntity.SLOT_FIRST, CrucibleBlockEntity.SLOT_FUEL, false)) {
			return ItemStack.EMPTY;
		}
		// Nothing actually moved: say so, or the click keeps asking for the same move for ever.
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.crucible.stillValid(player);
	}
}
