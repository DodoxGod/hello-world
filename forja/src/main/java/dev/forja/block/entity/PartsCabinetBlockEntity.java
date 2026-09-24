package dev.forja.block.entity;

import dev.forja.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The drawer of a workshop: twenty-seven slots that take nothing but a smith's things. Parts, templates,
 * orbs, seals, the mod's own materials and forged gear go in; cobblestone does not. It is not more room
 * than a chest, it is room that stays tidy.
 */
public class PartsCabinetBlockEntity extends BaseContainerBlockEntity {
	public static final int SIZE = 27;

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	public PartsCabinetBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ARMARIO, pos, state);
	}

	/** Whether the cabinet will hold this: anything the mod made, and the materials only it uses. */
	public static boolean accepts(ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}
		if (stack.has(ModComponents.PARTS) || stack.has(ModComponents.MATERIAL) || stack.has(ModComponents.PATTERN)
			|| stack.has(ModComponents.ORBE) || stack.has(ModComponents.SELLO) || stack.has(ModComponents.TALISMAN)) {
			return true;
		}
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(dev.forja.Forja.MOD_ID);
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.forja.armario_de_piezas");
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		// Its own menu, not a chest's: a chest's slots take anything the player hands them, and the rule
		// below was only ever being put to hoppers.
		return new dev.forja.menu.CabinetMenu(containerId, inventory, this);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return accepts(stack);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
	}
}
