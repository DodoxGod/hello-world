package dev.forja.menu;

import dev.forja.block.entity.CastingBoxBlockEntity;
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
 * The casting box's screen: a shape, the steel to cut it out of, and what comes out.
 *
 * <p>A strainer shift-clicked in goes to the pattern slot, to be bathed: there is no strainer gate to
 * guard any more, because the box no longer casts (the strainer stands on a casting table now).
 */
public class CastingBoxMenu extends AbstractContainerMenu {
	public static final int DATA_PROGRESS = 0;
	public static final int DATA_COOK = 1;
	public static final int DATA_HOLDS = 2;
	public static final int DATA_JOB = 3;
	public static final int DATA_METAL = 4;
	public static final int DATA_SIZE = 5;

	public static final int PATTERN_X = 34;
	public static final int PATTERN_Y = 34;
	public static final int STEEL_X = 34;
	public static final int STEEL_Y = 66;
	public static final int STRAINER_X = 58;
	public static final int STRAINER_Y = 66;
	public static final int OUTPUT_X = 140;
	public static final int OUTPUT_Y = 47;
	/** Where textures/gui paints the player's inventory on every one of Forja's panels. */
	public static final int INVENTORY_X = 22;
	public static final int INVENTORY_Y = 114;

	private final Container box;
	private final ContainerData data;

	public CastingBoxMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(CastingBoxBlockEntity.SIZE), new SimpleContainerData(DATA_SIZE));
	}

	public CastingBoxMenu(int id, Inventory inventory, Container box, ContainerData data) {
		super(ModMenus.CAJA, id);
		checkContainerSize(box, CastingBoxBlockEntity.SIZE);
		checkContainerDataCount(data, DATA_SIZE);
		this.box = box;
		this.data = data;

		// The same rule as the hoppers get (CastingBoxBlockEntity.allowed): the pattern slot here used to
		// take only moulds and parts, so a finished tool could not be cut into its frame from the screen,
		// nor a strainer infused.
		this.addSlot(new Slot(box, CastingBoxBlockEntity.SLOT_PATTERN, PATTERN_X, PATTERN_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return CastingBoxBlockEntity.allowed(CastingBoxBlockEntity.SLOT_PATTERN, stack);
			}
		});
		this.addSlot(new Slot(box, CastingBoxBlockEntity.SLOT_STEEL, STEEL_X, STEEL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return CastingBoxBlockEntity.allowed(CastingBoxBlockEntity.SLOT_STEEL, stack);
			}
		});
		// The old strainer gate. The box no longer casts, so nothing goes in; it only shows, and only while
		// a strainer from an older world is still sitting in it, so that strainer can be taken back out.
		this.addSlot(new Slot(box, CastingBoxBlockEntity.SLOT_STRAINER, STRAINER_X, STRAINER_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public boolean isActive() {
				return this.hasItem();
			}
		});
		this.addSlot(new Slot(box, CastingBoxBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y) {
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

	public float progress() {
		int cook = this.data.get(DATA_COOK);
		return cook <= 0 ? 0.0F : Math.min(1.0F, (float) this.data.get(DATA_PROGRESS) / cook);
	}

	/** The hardest material this box will pour, as a durability; Integer.MAX_VALUE for anything. */
	public int holds() {
		int holds = this.data.get(DATA_HOLDS);
		return holds >= Short.MAX_VALUE ? Integer.MAX_VALUE : holds;
	}

	/** What the box is doing, as CastingBoxBlockEntity.JOB_*. */
	public int job() {
		return this.data.get(DATA_JOB);
	}

	/** The metal it is pouring, as a material ordinal, or -1. */
	public int jobMetal() {
		return this.data.get(DATA_METAL);
	}

	public ItemStack pattern() {
		return this.box.getItem(CastingBoxBlockEntity.SLOT_PATTERN);
	}

	public ItemStack result() {
		return this.box.getItem(CastingBoxBlockEntity.SLOT_OUTPUT);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int inventoryStart = CastingBoxBlockEntity.SIZE;
		if (index < inventoryStart) {
			if (!this.moveItemStackTo(stack, inventoryStart, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(ModItems.alloy("acero_refractario"))) {
			if (!this.moveItemStackTo(stack, CastingBoxBlockEntity.SLOT_STEEL, CastingBoxBlockEntity.SLOT_STEEL + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, CastingBoxBlockEntity.SLOT_PATTERN, CastingBoxBlockEntity.SLOT_PATTERN + 1, false)) {
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
		return this.box.stillValid(player);
	}
}
