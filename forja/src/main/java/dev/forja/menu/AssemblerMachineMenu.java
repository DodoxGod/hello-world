package dev.forja.menu;

import java.util.ArrayList;
import java.util.List;

import dev.forja.block.entity.AssemblerMachineBlockEntity;
import dev.forja.forge.Alloys;
import dev.forja.forge.Assembler;
import dev.forja.item.CastingFrameItem;
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
 * The assembler's screen, server side: the forge star's five points, the frame in its centre, the
 * finished piece, and four numbers — how far the piece is, how long one takes at this heat, the heat,
 * and what the machine is doing.
 *
 * <p>The slots sit where the forge table's do (ForgeMenu.STAR_POINTS), so a smith who has used the table
 * reads this at a glance. By hand a part goes on whichever point it is put on, as on the star; the
 * stricter order the hoppers keep to is the block entity's (AssemblerMachineBlockEntity#fits).
 */
public class AssemblerMachineMenu extends AbstractContainerMenu {
	public static final int OUTPUT_X = 148;
	public static final int OUTPUT_Y = 47;
	/** Where textures/gui paints the player's inventory on every one of Forja's panels. */
	public static final int INVENTORY_X = 22;
	public static final int INVENTORY_Y = 114;

	private final Container machine;
	private final ContainerData data;

	public AssemblerMachineMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(AssemblerMachineBlockEntity.SIZE), new SimpleContainerData(AssemblerMachineBlockEntity.DATA_SIZE));
	}

	public AssemblerMachineMenu(int id, Inventory inventory, Container machine, ContainerData data) {
		super(ModMenus.MONTADORA, id);
		checkContainerSize(machine, AssemblerMachineBlockEntity.SIZE);
		checkContainerDataCount(data, AssemblerMachineBlockEntity.DATA_SIZE);
		this.machine = machine;
		this.data = data;

		for (int i = 0; i < AssemblerMachineBlockEntity.POINTS; i++) {
			this.addSlot(new Slot(machine, i, ForgeMenu.STAR_POINTS[i][0], ForgeMenu.STAR_POINTS[i][1]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					// Parts, and only parts: the same rule the star forges by (Assembler.loosePart).
					return Assembler.loosePart(stack);
				}
			});
		}
		this.addSlot(new Slot(machine, AssemblerMachineBlockEntity.SLOT_FRAME, ForgeMenu.CENTER_X, ForgeMenu.CENTER_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return CastingFrameItem.typeOf(stack) != null;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		this.addSlot(new Slot(machine, AssemblerMachineBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y) {
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

	/** How far along the piece on the star is, 0..1. */
	public float progress() {
		int work = this.data.get(AssemblerMachineBlockEntity.DATA_WORK);
		return work <= 0 ? 0.0F : Math.min(1.0F, (float) this.data.get(AssemblerMachineBlockEntity.DATA_PROGRESS) / work);
	}

	/** Ticks one piece takes at the heat it is standing in; 0 when it is too cold to work. */
	public int work() {
		return this.data.get(AssemblerMachineBlockEntity.DATA_WORK);
	}

	public Alloys.Heat heat() {
		Alloys.Heat[] all = Alloys.Heat.values();
		return all[Math.max(0, Math.min(all.length - 1, this.data.get(AssemblerMachineBlockEntity.DATA_HEAT)))];
	}

	/** What it is doing, as AssemblerMachineBlockEntity.JOB_*, as the server sees it. */
	public int job() {
		return this.data.get(AssemblerMachineBlockEntity.DATA_JOB);
	}

	/** The five points, in order. */
	public List<ItemStack> points() {
		List<ItemStack> points = new ArrayList<>();
		for (int i = 0; i < AssemblerMachineBlockEntity.POINTS; i++) {
			points.add(this.machine.getItem(i));
		}
		return points;
	}

	public ItemStack frame() {
		return this.machine.getItem(AssemblerMachineBlockEntity.SLOT_FRAME);
	}

	public ItemStack output() {
		return this.machine.getItem(AssemblerMachineBlockEntity.SLOT_OUTPUT);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int inventoryStart = AssemblerMachineBlockEntity.SIZE;
		if (index < inventoryStart) {
			if (!this.moveItemStackTo(stack, inventoryStart, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (CastingFrameItem.typeOf(stack) != null) {
			if (!this.moveItemStackTo(stack, AssemblerMachineBlockEntity.SLOT_FRAME, AssemblerMachineBlockEntity.SLOT_FRAME + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (Assembler.loosePart(stack)) {
			// Onto a pile of the same part if there is one, else the first free point: moveItemStackTo does
			// the piles first.
			if (!this.moveItemStackTo(stack, 0, AssemblerMachineBlockEntity.POINTS, false)) {
				return ItemStack.EMPTY;
			}
		} else if (index < inventoryStart + 27) {
			if (!this.moveItemStackTo(stack, inventoryStart + 27, this.slots.size(), false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, inventoryStart, inventoryStart + 27, false)) {
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
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.machine.stillValid(player);
	}
}
