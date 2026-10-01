package dev.forja.block.entity;

import dev.forja.item.StrainerItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * What a strainer set down in the world is made of, which is the only thing it has to remember.
 *
 * <p>It is the same number the {@link StrainerItem colador item} carries in its component, and it travels
 * both ways: placing the item hands the material to this block entity ({@link #applyImplicitComponents}),
 * and breaking the block hands it back to the item that drops ({@link #collectImplicitComponents}, read by
 * the loot table's {@code copy_components}). A damascus strainer picked up is still a damascus strainer.
 *
 * <p>It is a field and not only a component because the client needs it: the grate is tinted the colour
 * of its metal, and the update packet carries a block entity's own fields, not its components.
 */
public class StrainerBlockEntity extends BlockEntity {
	private @Nullable ForgeMaterial material;

	public StrainerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.COLADOR, pos, state);
	}

	/** What it is made of; null for the plain fired-clay one. */
	public @Nullable ForgeMaterial material() {
		return this.material;
	}

	public void setMaterial(@Nullable ForgeMaterial material) {
		this.material = material;
		this.setChanged();
		if (this.level != null && !this.level.isClientSide()) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
		}
	}

	/** The hardest metal it lets through without going with it: the same rule as the item. */
	public int holds() {
		return this.material == null ? StrainerItem.CLAY_HOLDS : this.material.durability;
	}

	/** Whether pouring that metal through it leaves it standing. */
	public boolean survives(ForgeMaterial melt) {
		return melt.durability <= this.holds();
	}

	/** The item it goes back to being: exactly the colador that was set down. */
	public ItemStack asItem() {
		return StrainerItem.of(this.material);
	}

	/** The colour its grate is drawn in, clay until something better has been poured through it. */
	public int colour() {
		return this.material == null ? StrainerItem.CLAY_COLOUR : this.material.color;
	}

	// ------------------------------------------------------------------ to and from the item

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		this.material = components.get(ModComponents.STRAINER);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		// Both components the item has, or the one that drops would say damascus and be drawn in clay.
		ItemStack same = this.asItem();
		if (this.material != null) {
			components.set(ModComponents.STRAINER, this.material);
			components.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
				same.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA));
		}
	}

	// ------------------------------------------------------------------ saving and syncing

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.material = input.read("Material", ForgeMaterial.CODEC).orElse(null);
		// On the client the grate's colour is baked into the chunk, so a strainer that learns what it is
		// made of after its block arrived has to ask to be drawn again.
		if (this.level != null && this.level.isClientSide()) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (this.material != null) {
			output.store("Material", ForgeMaterial.CODEC, this.material);
		}
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
