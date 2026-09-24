package dev.forja.block.entity;

import dev.forja.block.CastingTableBlock;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Quality;
import dev.forja.item.CastingFrameItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * One casting table, once a tick.
 *
 * <p>The cycle is short enough to say in a sentence: a frame on the table, exactly the metal that tool
 * is worth pulled out of the tanks and into it, five seconds while it sets, and the finished thing on
 * the table where the frame was. Nothing about it is a screen — you can watch every step of it happen
 * on top of the block.
 *
 * <p>The heat is the whole of the difficulty. A table warms beside anything that burns and bleeds heat
 * when nothing does, at a rate its stone decides. With no heat left it will not start a pour; with the
 * last of it, the pour it already started sets early and comes out rough.
 */
public class CastingTableBlockEntity extends BlockEntity implements WorldlyContainer {
	public static final int SLOT_FRAME = 0;
	public static final int SLOT_OUTPUT = 1;
	public static final int SIZE = 2;

	/** Ticks one casting takes. The same on every table: they differ in heat, not in speed. */
	public static final int COOK = 100;

	/** The most heat a table holds. */
	public static final int HOT = 200;

	/** What one casting spends, so a hot table is good for five of them before it needs a fire. */
	public static final int SPEND = 40;

	/** What anything burning beside or beneath it puts back every second. */
	public static final int WARMS = 25;

	/** How often heat is settled. */
	public static final int EVERY = 20;

	private static final int[] TOP = {SLOT_FRAME};
	private static final int[] BOTTOM = {SLOT_OUTPUT};
	private static final int[] NONE = {};

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int progress;
	private int heat;
	/** What is in the frame right now, and how much of it. Empty until a pour starts. */
	private @Nullable Item metal;
	private int amount;
	/** Whether this pour went in on the last of the heat and will set before it is done. */
	private boolean rough;
	/**
	 * The tick the pour began on.
	 *
	 * <p>The client draws the metal rising in the frame, and sending it a packet every tick to say so
	 * would be a packet a tick per table. It is told when the pour started instead, once, and works the
	 * rest out off the world clock.
	 */
	private long startedAt;
	private int settleIn = -1;

	public CastingTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MESA_DE_COLADA, pos, state);
	}

	/**
	 * The colour of whatever is cooling in it, or -1 if nothing is.
	 *
	 * <p>Only the block's ambient particles want this, and they want it on the client, so it reads the
	 * stored metal rather than going near the assembler.
	 */
	public int metalColour() {
		if (this.metal == null) {
			return -1;
		}
		ForgeMaterial material = ForgeMaterial.fromInput(new ItemStack(this.metal));
		return material == null ? -1 : material.color;
	}

	public CastingTableBlock.Tier tier() {
		return this.getBlockState().getBlock() instanceof CastingTableBlock table ? table.tier : CastingTableBlock.Tier.LOSA;
	}

	// ------------------------------------------------------------------ what the renderer reads

	public ItemStack frame() {
		return this.items.get(SLOT_FRAME);
	}

	public ItemStack result() {
		return this.items.get(SLOT_OUTPUT);
	}

	public @Nullable Item metal() {
		return this.metal;
	}

	public int amount() {
		return this.amount;
	}

	public int heat() {
		return this.heat;
	}

	/** How far along the pour is, 0 to 1, as the server counts it. */
	public float progress() {
		return this.metal == null ? 0.0F : Math.min(1.0F, this.progress / (float) COOK);
	}

	/** The same thing read off the world clock, which is what the renderer has to go on. */
	public float progressAt(double gameTime) {
		return this.metal == null ? 0.0F
			: (float) Math.max(0.0, Math.min(1.0, (gameTime - this.startedAt) / COOK));
	}

	// ------------------------------------------------------------------ the work

	public static void serverTick(Level level, BlockPos pos, BlockState state, CastingTableBlockEntity table) {
		if (table.settleIn < 0) {
			// Staggered by position, so a row of twenty tables does not settle on the same tick.
			table.settleIn = Math.floorMod(pos.hashCode(), EVERY);
		}
		if (table.settleIn-- <= 0) {
			table.settleIn = EVERY;
			table.settle(level, pos);
		}
		if (table.metal == null) {
			table.progress = 0;
			table.start(level);
		} else if (++table.progress >= COOK) {
			table.finish(level, pos);
		}
		boolean pouring = table.metal != null;
		if (state.getValue(CastingTableBlock.LIT) != pouring) {
			level.setBlock(pos, state.setValue(CastingTableBlock.LIT, pouring), Block.UPDATE_ALL);
		}
	}

	/**
	 * One second of heat: what the fire gives, less what the stone lets go of.
	 *
	 * <p>Underneath counts as well as beside, because a table is a thing you stand over a fire, and the
	 * wisp lantern is still the tidiest way to do it.
	 */
	private void settle(Level level, BlockPos pos) {
		boolean warmed = false;
		for (Direction side : Direction.values()) {
			BlockState beside = level.getBlockState(pos.relative(side));
			if (beside.is(dev.forja.registry.ModBlocks.FAROL_DE_PAVESA)
				|| beside.is(net.minecraft.world.level.block.Blocks.LAVA)
				|| beside.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
				|| beside.is(net.minecraft.world.level.block.Blocks.FIRE)
				|| (beside.getBlock() instanceof dev.forja.block.CrucibleBlock
					&& beside.getValue(dev.forja.block.CrucibleBlock.LIT))) {
				warmed = true;
				break;
			}
		}
		int was = this.heat;
		this.heat = Math.max(0, Math.min(HOT, this.heat + (warmed ? WARMS : -this.tier().cools)));
		if (was != this.heat) {
			this.setChanged();
		}
	}

	/** Pulls exactly what the tool is worth out of the tanks, if everything is ready for it. */
	private void start(Level level) {
		if (this.heat <= 0) {
			return;
		}
		ForgeType type = CastingFrameItem.typeOf(this.frame());
		if (type == null || !this.result().isEmpty()) {
			return;
		}
		int cost = CastingFrameItem.cost(type);
		for (MeltTankBlockEntity tank : MeltTankBlockEntity.reachableFrom(level, this.worldPosition)) {
			Item held = tank.bankMetal();
			if (held == null || tank.isSet() || tank.bankAmount() < cost) {
				continue;
			}
			ForgeMaterial material = ForgeMaterial.fromInput(new ItemStack(held));
			if (material == null || !CastingFrameItem.castable(type, material)) {
				continue;
			}
			if (tank.drain(cost) < cost) {
				continue;
			}
			this.metal = held;
			this.amount = cost;
			// Started on the last of the heat: the metal will set in the frame before it is done.
			this.rough = this.heat < SPEND;
			this.progress = 0;
			this.startedAt = level.getGameTime();
			if (level instanceof ServerLevel server) {
				server.playSound(null, this.worldPosition, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.5F, 1.4F);
			}
			this.setChanged();
			return;
		}
	}

	/** The tool comes off the frame, better or worse than it went in. */
	private void finish(Level level, BlockPos pos) {
		ForgeType type = CastingFrameItem.typeOf(this.frame());
		ForgeMaterial material = this.metal == null ? null : ForgeMaterial.fromInput(new ItemStack(this.metal));
		this.progress = 0;
		this.metal = null;
		this.amount = 0;
		boolean wasRough = this.rough;
		this.rough = false;
		if (type == null || material == null) {
			return;
		}
		ItemStack cast = Assembler.create(type, java.util.Collections.nCopies(type.slots.size(), material),
			level.registryAccess());
		// Nobody forged this, so nothing gave it a potential: the table does, and every slot of it is cast.
		boolean steady = !wasRough && level.getRandom().nextFloat() < this.tier().luck;
		cast.set(ModComponents.POTENCIAL, dev.forja.forge.Potential.atCasting(wasRough, steady));
		if (!wasRough) {
			cast.set(ModComponents.COLADAS, (1 << type.slots.size()) - 1);
		}
		if (wasRough) {
			// The metal set in the frame before it was done, and the piece carries it for good.
			cast.set(ModComponents.ROUGH, true);
			Assembler.rewrite(cast, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		} else if (steady) {
			// The steady hand of good stone: the same +5% a smith gets for stopping the hammer dead centre.
			Quality.markPerfect(cast);
			// Every number of the piece goes up, so the components have to be written again.
			Assembler.rewrite(cast, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
			if (level instanceof ServerLevel server) {
				server.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.4F, 1.8F);
				server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
					pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.02);
			}
		}
		this.items.set(SLOT_OUTPUT, cast);
		this.heat = Math.max(0, this.heat - SPEND);
		if (level instanceof ServerLevel server) {
			server.playSound(null, pos, wasRough ? SoundEvents.LAVA_EXTINGUISH : SoundEvents.ANVIL_LAND,
				SoundSource.BLOCKS, 0.5F, wasRough ? 0.7F : 1.4F);
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
				pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 4, 0.2, 0.02, 0.2, 0.0);
		}
		this.setChanged();
	}

	// ------------------------------------------------------------------ by hand

	/** A frame goes down with a full hand and the finished tool comes up with an empty one. */
	public boolean hand(Player player, ItemStack held) {
		if (this.level == null) {
			return false;
		}
		if (!this.result().isEmpty()) {
			// Whatever is on the table, the finished thing comes off first.
			player.getInventory().placeItemBackInInventory(this.result().copy());
			this.items.set(SLOT_OUTPUT, ItemStack.EMPTY);
			this.setChanged();
			return true;
		}
		if (!held.isEmpty() && CastingFrameItem.typeOf(held) != null && this.frame().isEmpty()) {
			this.items.set(SLOT_FRAME, held.split(1));
			this.level.playSound(null, this.worldPosition, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
			this.setChanged();
			return true;
		}
		if (held.isEmpty() && !this.frame().isEmpty()) {
			if (this.metal != null) {
				// Not while there is metal in it: that is how you get molten iron on the floor.
				this.say(player, Component.translatable("gui.forja.mesa_colada.colando"));
				return false;
			}
			player.getInventory().placeItemBackInInventory(this.frame().copy());
			this.items.set(SLOT_FRAME, ItemStack.EMPTY);
			this.setChanged();
			return true;
		}
		this.say(player, this.describe());
		return false;
	}

	/** What the table is doing, in one line. */
	public Component describe() {
		ForgeType type = CastingFrameItem.typeOf(this.frame());
		if (type == null) {
			return Component.translatable("gui.forja.mesa_colada.vacia");
		}
		if (this.metal != null) {
			return Component.translatable("gui.forja.mesa_colada.colando_de",
				new ItemStack(this.metal).getHoverName(), Math.round(this.progress() * 100.0F));
		}
		return Component.translatable("gui.forja.mesa_colada.espera",
			type.displayName(), CastingFrameItem.cost(type), this.heat * 100 / HOT);
	}

	private void say(Player player, Component line) {
		if (player instanceof net.minecraft.server.level.ServerPlayer smith) {
			smith.sendSystemMessage(line);
		}
	}

	// ------------------------------------------------------------------ container

	@Override
	public int[] getSlotsForFace(Direction side) {
		return switch (side) {
			case UP -> TOP;
			case DOWN -> BOTTOM;
			default -> NONE;
		};
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return slot == SLOT_FRAME && this.canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == SLOT_OUTPUT;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		// A frame goes in and nothing else does; the table has no second job to confuse it with.
		return slot == SLOT_FRAME && CastingFrameItem.typeOf(stack) != null && this.metal == null;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack taken = ContainerHelper.removeItem(this.items, slot, count);
		if (!taken.isEmpty()) {
			this.setChanged();
		}
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(this.items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	// ------------------------------------------------------------------ saving and syncing

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.progress = input.getIntOr("Progress", 0);
		this.heat = input.getIntOr("Heat", 0);
		this.amount = input.getIntOr("Amount", 0);
		this.rough = input.getBooleanOr("Rough", false);
		this.startedAt = input.getLongOr("Started", 0L);
		String id = input.getStringOr("Metal", "");
		this.metal = id.isEmpty() ? null
			: BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(id)).orElse(null);
		if (this.metal == null) {
			this.amount = 0;
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("Progress", this.progress);
		output.putInt("Heat", this.heat);
		output.putInt("Amount", this.amount);
		output.putBoolean("Rough", this.rough);
		output.putLong("Started", this.startedAt);
		if (this.metal != null) {
			output.putString("Metal", BuiltInRegistries.ITEM.getKey(this.metal).toString());
		}
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (this.level != null && !this.level.isClientSide()) {
			// The frame and the melt are drawn on top of the block, so the client needs both.
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
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
