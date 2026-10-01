package dev.forja.menu;

import dev.forja.block.entity.PartsCabinetBlockEntity;
import dev.forja.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The parts cabinet's own menu: three drawers of nine.
 *
 * <p>It opened as a vanilla chest, which was wrong twice over. It looked like a chest, grey among
 * panels of wood and stone; and it behaved like one — a chest's slots take anything, so the rule that
 * makes the cabinet what it is, that only a smith's things go in, held for hoppers and for nobody else.
 * You could walk up and put cobblestone in it by hand. The slots here ask
 * {@link PartsCabinetBlockEntity#accepts}, the same question the hopper was already being asked.
 */
public class CabinetMenu extends AbstractContainerMenu {
	public static final int ROWS = 3;
	public static final int COLUMNS = 9;
	/** Where the drawers' slots sit, matching textures/gui/armario.png: one drawer every 22 pixels. */
	public static final int DRAWER_X = 22;
	public static final int DRAWER_Y = 23;
	public static final int DRAWER_PITCH = 22;
	/** Where textures/gui paints the player's inventory on every one of Forja's panels. */
	public static final int INVENTORY_X = 22;
	public static final int INVENTORY_Y = 114;

	private final Container cabinet;

	public CabinetMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(PartsCabinetBlockEntity.SIZE));
	}

	public CabinetMenu(int id, Inventory inventory, Container cabinet) {
		super(ModMenus.ARMARIO, id);
		checkContainerSize(cabinet, PartsCabinetBlockEntity.SIZE);
		this.cabinet = cabinet;
		cabinet.startOpen(inventory.player);
		for (int row = 0; row < ROWS; row++) {
			for (int column = 0; column < COLUMNS; column++) {
				this.addSlot(new Slot(cabinet, column + row * COLUMNS, DRAWER_X + column * 18, DRAWER_Y + row * DRAWER_PITCH) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return PartsCabinetBlockEntity.accepts(stack);
					}
				});
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Slot(inventory, 9 + column + row * 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int drawers = PartsCabinetBlockEntity.SIZE;
		if (index < drawers) {
			if (!this.moveItemStackTo(stack, drawers, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!PartsCabinetBlockEntity.accepts(stack) || !this.moveItemStackTo(stack, 0, drawers, false)) {
			// moveItemStackTo asks each slot's mayPlace for empty slots but not when topping up a stack
			// that is already there, so the question is asked here first.
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
		return this.cabinet.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.cabinet.stopOpen(player);
	}
}
