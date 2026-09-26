package dev.forja.block.entity;

import java.util.List;

import dev.forja.block.CrucibleBlock;
import dev.forja.forge.Alloys;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * What the crucible actually does, once a tick.
 *
 * <p>Two jobs, in this order. If the two input slots hold an alloy's ingredients and the crucible runs
 * hot enough, it pours that alloy. If one of them holds something a smith made, it melts it back down
 * into the material it was cut from, at a share that depends on what the crucible is built of — which is
 * the reason to keep upgrading it long after the alloys have stopped being a problem.
 *
 * <p>It is a {@link WorldlyContainer} on the furnace's own convention: in from the top, fuel from the
 * sides, out from the bottom. That is the whole automation story, and it is deliberately the one every
 * player already knows.
 */
public class CrucibleBlockEntity extends BlockEntity implements WorldlyContainer, net.minecraft.world.MenuProvider {
	public static final int SLOT_FIRST = 0;
	public static final int SLOT_SECOND = 1;
	public static final int SLOT_FUEL = 2;
	public static final int SLOT_OUTPUT = 3;
	public static final int SIZE = 4;

	private static final int[] TOP = {SLOT_FIRST, SLOT_SECOND};
	private static final int[] SIDES = {SLOT_FUEL};
	private static final int[] BOTTOM = {SLOT_OUTPUT};

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int burning;
	private int ticks;
	private int remelted;
	private int burnLength;
	private int progress;

	public CrucibleBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CRISOL, pos, state);
	}

	/**
	 * The colour of the metal in the pot — the material's own, not yet molten — or -1 if there is none.
	 *
	 * <p>On the client this is what the server last said. It has to be: the client's copy of a crucible
	 * has nothing in it, because what a container holds is only ever sent to somebody with its screen
	 * open. The dust over the rim was written to be "the colour of the metal" and read the client's empty
	 * slots, so it never once drew; and every lit pot in a workshop glowed the same orange. The colour is
	 * now the one thing about the pot that is sent (see {@link #getUpdateTag}), when it changes.
	 */
	public int meltColour() {
		if (this.level != null && this.level.isClientSide()) {
			return this.shownColour;
		}
		return this.colourOf(this.pending());
	}

	/** What the pot is turning into if it is turning into something, else the first metal that is in it. */
	private int colourOf(@Nullable Pour pour) {
		if (pour != null) {
			ForgeMaterial made = ForgeMaterial.fromInput(pour.result());
			if (made != null) {
				return made.color;
			}
		}
		for (int slot : new int[] {SLOT_FIRST, SLOT_SECOND}) {
			ItemStack stack = this.items.get(slot);
			ForgedParts parts = stack.get(ModComponents.PARTS);
			ForgeMaterial material = parts != null && !parts.materials().isEmpty() ? parts.materials().getFirst() : ForgeMaterial.fromInput(stack);
			if (material != null) {
				return material.color;
			}
		}
		return -1;
	}

	/** The colour the clients were last told, on the server; the colour this client was told, on a client. */
	private int shownColour = -1;

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	/** The colour and nothing else: what is in the slots is nobody's business but the screen's. */
	@Override
	public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putInt("Colour", this.colourOf(this.pending()));
		return tag;
	}

	/** Its own clock, so melting a set bank does not ride on the world's. */
	private int tickCount() {
		return ++this.ticks;
	}

	/** How much metal this crucible has burnt melting set banks back down, for the tests. */
	public int remelted() {
		return this.remelted;
	}

	public CrucibleBlock.Tier tier() {
		return this.getBlockState().getBlock() instanceof CrucibleBlock crucible ? crucible.tier : CrucibleBlock.Tier.BARRO;
	}

	// ------------------------------------------------------------------ the work

	public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, CrucibleBlockEntity crucible) {
		boolean wasLit = crucible.burning > 0;
		if (crucible.burning > 0) {
			crucible.burning--;
		}
		Pour pour = crucible.pending();
		// A bank that has set beside the pot is work too: it fires up by itself to save your metal,
		// because a smith who walked away and came back to a cold wall should not also have to be told
		// which button melts it.
		MeltTankBlockEntity frozen = pour != null ? null : crucible.setTank();
		if (pour == null && frozen == null) {
			crucible.progress = 0;
		} else {
			if (crucible.burning == 0 && crucible.light(level)) {
				// Fresh fuel: the pour keeps its place in the queue rather than starting over.
				crucible.setChanged();
			}
			if (crucible.burning > 0 && frozen != null) {
				if (crucible.tickCount() % 20 == 0) {
					crucible.remelted += frozen.remelt();
				}
			} else if (crucible.burning > 0) {
				crucible.progress++;
				int cook = crucible.fedByTank(pour) ? Math.max(1, crucible.tier().cook / 2) : crucible.tier().cook;
				if (crucible.progress >= cook) {
					crucible.progress = 0;
					crucible.finish(level, pos, pour);
				}
			} else {
				crucible.progress = Math.max(0, crucible.progress - 2);
			}
		}
		boolean lit = crucible.burning > 0;
		if (lit != wasLit) {
			level.setBlock(pos, state.setValue(CrucibleBlock.LIT, lit), net.minecraft.world.level.block.Block.UPDATE_ALL);
			crucible.setChanged();
		}
		// Twice a second, and a packet only when the answer is a different one: a pot changes colour when
		// somebody puts something else in it, which is rare, and a packet a tick per pot is not.
		if (level.getGameTime() % 10L == 0L) {
			int colour = crucible.colourOf(pour);
			if (colour != crucible.shownColour) {
				crucible.shownColour = colour;
				BlockState now = level.getBlockState(pos);
				level.sendBlockUpdated(pos, now, now, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
			}
		}
	}

	/** How long one ember keeps a crucible going. */
	public static final int EMBER_TICKS = 400;

	/**
	 * Burns one ember, or nothing at all if there is a wisp lantern underneath.
	 *
	 * <p>A crucible does not take coal, or charcoal, or a bucket of lava. It takes **ascuas**, which come
	 * out of a pavesa and out of every tool this thing melts down, so the whole loop belongs to the mod:
	 * break something, melt it, and what you get back pays for the next melt. Catching a wisp and setting
	 * the lantern under the pot skips the fuel entirely, which is the point of catching one.
	 */
	private boolean light(net.minecraft.world.level.Level level) {
		if (level.getBlockState(this.worldPosition.below()).is(dev.forja.registry.ModBlocks.FAROL_DE_PAVESA)) {
			this.burning = EMBER_TICKS;
			this.burnLength = EMBER_TICKS;
			return true;
		}
		ItemStack fuel = this.items.get(SLOT_FUEL);
		if (!fuel.is(dev.forja.registry.ModItems.ASCUA)) {
			return false;
		}
		this.burning = EMBER_TICKS;
		this.burnLength = EMBER_TICKS;
		fuel.shrink(1);
		return true;
	}

	/**
	 * The metal this pot is about to pour, or null when it is idle.
	 *
	 * <p>Only the channels ask, and only so they can be drawn the colour of what is coming out of it.
	 */
	public net.minecraft.world.item.@Nullable Item pouring() {
		Pour pour = this.pending();
		return pour == null ? null : pour.result().getItem();
	}

	/** What this crucible would pour right now, or null if it has nothing to do. */
	private @Nullable Pour pending() {
		List<ItemStack> inputs = new java.util.ArrayList<>(List.of(this.items.get(SLOT_FIRST), this.items.get(SLOT_SECOND)));
		// Whatever the tanks beside it are holding counts as being in the pot already.
		for (MeltTankBlockEntity tank : this.tanks()) {
			if (tank.bankMetal() != null && tank.bankAmount() > 0 && !tank.isSet()) {
				inputs.add(new ItemStack(tank.bankMetal(), Math.min(64, tank.bankAmount())));
			}
		}
		Alloys.Recipe recipe = Alloys.match(inputs, this.tier().heat);
		if (recipe != null) {
			ItemStack result = recipe.result();
			result.setCount(result.getCount() + this.tier().bonus);
			return this.fits(result) ? new Pour(result, recipe, null) : null;
		}
		// Nothing to alloy: see whether one of the two is something a smith made.
		for (int slot : TOP) {
			ItemStack scrap = this.items.get(slot);
			ItemStack back = this.recovered(scrap);
			if (!back.isEmpty() && this.fits(back)) {
				return new Pour(back, null, slot);
			}
		}
		return null;
	}

	/** What melting one forged part or one finished piece of gear gives back at this tier. */
	private ItemStack recovered(ItemStack stack) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null || parts.materials().isEmpty()) {
			return ItemStack.EMPTY;
		}
		// Everything goes back as the material of its first part, which is the one that names the piece.
		ForgeMaterial material = parts.materials().getFirst();
		int cost = 0;
		List<PartType> needed = parts.type().slots;
		for (int i = 0; i < needed.size() && i < parts.materials().size(); i++) {
			if (parts.materials().get(i) == material) {
				cost += needed.get(i).cost;
			}
		}
		int back = Math.round(cost * this.tier().recovery);
		if (back <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack ingot = material.displayStack();
		ingot.setCount(Math.min(back, ingot.getMaxStackSize()));
		return ingot;
	}

	private boolean fits(ItemStack result) {
		ItemStack out = this.items.get(SLOT_OUTPUT);
		if (out.isEmpty()) {
			return true;
		}
		return ItemStack.isSameItemSameComponents(out, result)
			&& out.getCount() + result.getCount() <= out.getMaxStackSize();
	}

	private void finish(net.minecraft.world.level.Level level, BlockPos pos, Pour pour) {
		if (pour.recipe() != null) {
			// An alloy eats exactly what the recipe asked for out of both slots.
			for (Alloys.Part part : pour.recipe().inputs()) {
				boolean paid = false;
				for (int slot : TOP) {
					ItemStack stack = this.items.get(slot);
					if (part.test(stack)) {
						stack.shrink(part.count());
						paid = true;
						break;
					}
				}
				if (!paid) {
					MeltTankBlockEntity tank = this.tankHolding(part.item().get());
					if (tank != null) {
						tank.drain(part.count());
					}
				}
			}
		} else if (pour.slot() != null) {
			this.items.get(pour.slot()).shrink(1);
		}
		// A tank against the pot takes the pour: a foundry is crucibles emptying into glass, not
		// crucibles filling their own little output slot and stopping when it is full.
		int left = this.pourIntoTank(pour.result());
		if (left > 0) {
			ItemStack out = this.items.get(SLOT_OUTPUT);
			if (out.isEmpty()) {
				this.items.set(SLOT_OUTPUT, pour.result().copyWithCount(left));
			} else {
				out.grow(left);
			}
		}
		// What is left in the bottom of the pot after a tool goes in: enough fire for the next one.
		if (pour.recipe() == null) {
			ItemStack fuel = this.items.get(SLOT_FUEL);
			if (fuel.isEmpty()) {
				this.items.set(SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA));
			} else if (fuel.is(dev.forja.registry.ModItems.ASCUA) && fuel.getCount() < fuel.getMaxStackSize()) {
				fuel.grow(1);
			}
		}
		if (level instanceof ServerLevel server) {
			server.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.6F);
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
				pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.0);
		}
		this.setChanged();
	}

	/** Pours into the first tank the crucible can reach, and says how much would not go. */
	private int pourIntoTank(ItemStack result) {
		if (this.level == null) {
			return result.getCount();
		}
		for (MeltTankBlockEntity tank : this.tanks()) {
			// What the run of pipe between here and there took out of it, which is nothing when the
			// glass is built against the pot and a great deal at the end of a long bronze tendril.
			int bled = dev.forja.block.MeltPipeBlock.bleedBetween(this.level, this.worldPosition, tank.getBlockPos());
			int left = tank.fill(result.getItem(), result.getCount(), bled);
			if (left < result.getCount()) {
				return left;
			}
		}
		return result.getCount();
	}

	/**
	 * Every tank this crucible can reach: the ones it is touching, and the ones on the end of a pipe.
	 *
	 * <p>A pipe is only a connection, so reaching through one is the same as being built against the
	 * glass — which is the point of having them at all.
	 */
	private List<MeltTankBlockEntity> tanks() {
		List<MeltTankBlockEntity> found = new java.util.ArrayList<>();
		if (this.level == null) {
			return found;
		}
		boolean piped = false;
		for (Direction side : Direction.values()) {
			BlockPos at = this.worldPosition.relative(side);
			if (this.level.getBlockEntity(at) instanceof MeltTankBlockEntity tank) {
				found.add(tank);
			} else if (this.level.getBlockState(at).getBlock() instanceof dev.forja.block.MeltPipeBlock) {
				piped = true;
			}
		}
		if (piped) {
			for (BlockPos end : dev.forja.block.MeltPipeBlock.reachable(this.level, this.worldPosition)) {
				if (this.level.getBlockEntity(end) instanceof MeltTankBlockEntity tank && !found.contains(tank)) {
					found.add(tank);
				}
			}
		}
		return found;
	}

	/**
	 * A tank touching the crucible that is holding what it needs.
	 *
	 * <p>This is what a bank of tanks is for: the crucible eats out of the glass instead of out of its
	 * own slots, so a line of them runs off one wall of metal and nobody carries anything.
	 */
	private @Nullable MeltTankBlockEntity tankHolding(Item wanted) {
		for (MeltTankBlockEntity tank : this.tanks()) {
			if (tank.bankMetal() == wanted && tank.bankAmount() > 0 && !tank.isSet()) {
				return tank;
			}
		}
		return null;
	}

	/** The first tank it can reach whose metal has set, if any. */
	private @Nullable MeltTankBlockEntity setTank() {
		for (MeltTankBlockEntity tank : this.tanks()) {
			if (tank.isSet()) {
				return tank;
			}
		}
		return null;
	}

	/** Whether any tank is feeding this pour, which is what halves the time it takes. */
	private boolean fedByTank(Pour pour) {
		if (pour.recipe() == null) {
			return false;
		}
		for (Alloys.Part part : pour.recipe().inputs()) {
			if (this.tankHolding(part.item().get()) != null) {
				return true;
			}
		}
		return false;
	}

	/** One thing the crucible is about to pour: what comes out, and what it came from. */
	private record Pour(ItemStack result, Alloys.@Nullable Recipe recipe, @Nullable Integer slot) {
	}

	// ------------------------------------------------------------------ hands

	/** A full hand: the item goes into the first slot that will take it. */
	public boolean handIn(Player player, ItemStack held) {
		if (held.isEmpty()) {
			return false;
		}
		int room = this.tier().capacity;
		int inside = this.items.get(SLOT_FIRST).getCount() + this.items.get(SLOT_SECOND).getCount();
		int slot = this.slotFor(held);
		if (slot < 0 || inside >= room) {
			if (player instanceof net.minecraft.server.level.ServerPlayer smith) {
				smith.sendSystemMessage(Component.translatable("gui.forja.crisol.lleno", room));
			}
			return false;
		}
		int moved = Math.min(held.getCount(), room - inside);
		ItemStack there = this.items.get(slot);
		if (there.isEmpty()) {
			this.items.set(slot, held.split(moved));
		} else {
			moved = Math.min(moved, there.getMaxStackSize() - there.getCount());
			there.grow(moved);
			held.shrink(moved);
		}
		this.setChanged();
		return true;
	}

	/** An empty hand: whatever the crucible has poured comes out, and a look at what is inside. */
	public void handOut(Player player) {
		ItemStack out = this.items.get(SLOT_OUTPUT);
		if (!out.isEmpty()) {
			player.getInventory().placeItemBackInInventory(out.copy());
			this.items.set(SLOT_OUTPUT, ItemStack.EMPTY);
			this.setChanged();
			return;
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer smith) {
			smith.sendSystemMessage(this.describe());
		}
	}

	/** What it is holding and what it is doing, for the hand and for Jade. */
	public Component describe() {
		ItemStack first = this.items.get(SLOT_FIRST);
		ItemStack second = this.items.get(SLOT_SECOND);
		if (first.isEmpty() && second.isEmpty()) {
			return Component.translatable("gui.forja.crisol.vacio", this.tier().heat.displayName());
		}
		Component load = first.isEmpty() ? second.getHoverName()
			: second.isEmpty() ? first.getHoverName()
			: Component.translatable("gui.forja.crisol.dos", first.getHoverName(), second.getHoverName());
		return Component.translatable("gui.forja.crisol.dentro", load, this.progressPercent());
	}

	public int progressPercent() {
		return Math.round(100.0F * this.progress / this.tier().cook);
	}

	public boolean isLit() {
		return this.burning > 0;
	}

	private int slotFor(ItemStack stack) {
		for (int slot : TOP) {
			ItemStack there = this.items.get(slot);
			if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
				return slot;
			}
		}
		for (int slot : TOP) {
			if (this.items.get(slot).isEmpty()) {
				return slot;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------ the screen

	/** The six numbers the screen draws itself from. */
	private final net.minecraft.world.inventory.ContainerData data = new net.minecraft.world.inventory.ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case dev.forja.menu.CrucibleMenu.DATA_PROGRESS -> CrucibleBlockEntity.this.progress;
				case dev.forja.menu.CrucibleMenu.DATA_COOK -> CrucibleBlockEntity.this.tier().cook;
				case dev.forja.menu.CrucibleMenu.DATA_BURNING -> CrucibleBlockEntity.this.burning;
				case dev.forja.menu.CrucibleMenu.DATA_BURN_LENGTH -> CrucibleBlockEntity.this.burnLength;
				case dev.forja.menu.CrucibleMenu.DATA_HEAT -> CrucibleBlockEntity.this.tier().heat.ordinal();
				case dev.forja.menu.CrucibleMenu.DATA_CAPACITY -> CrucibleBlockEntity.this.tier().capacity;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return dev.forja.menu.CrucibleMenu.DATA_SIZE;
		}
	};

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.forja." + this.tier().id());
	}

	@Override
	public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
		return new dev.forja.menu.CrucibleMenu(id, inventory, this, this.data);
	}

	// ------------------------------------------------------------------ container

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
	public ItemStack removeItem(int slot, int amount) {
		ItemStack taken = ContainerHelper.removeItem(this.items, slot, amount);
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
		stack.limitSize(this.getMaxStackSize(stack));
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return this.level != null && this.level.getBlockEntity(this.worldPosition) == this
			&& player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clearContent() {
		this.items.clear();
		this.setChanged();
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return switch (side) {
			case DOWN -> BOTTOM;
			case UP -> TOP;
			default -> SIDES;
		};
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return this.canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == SLOT_OUTPUT;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == SLOT_OUTPUT) {
			return false;
		}
		if (slot == SLOT_FUEL) {
			return stack.is(dev.forja.registry.ModItems.ASCUA);
		}
		// The capacity is the point of the tiers, so a hopper cannot walk around it.
		int inside = this.items.get(SLOT_FIRST).getCount() + this.items.get(SLOT_SECOND).getCount();
		return inside < this.tier().capacity;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.burning = input.getIntOr("Burning", 0);
		this.burnLength = input.getIntOr("BurnLength", 0);
		this.progress = input.getIntOr("Progress", 0);
		// Only ever present in what the server sends a client; a saved pot has no such key and works it out.
		this.shownColour = input.getIntOr("Colour", -1);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("Burning", this.burning);
		output.putInt("BurnLength", this.burnLength);
		output.putInt("Progress", this.progress);
	}
}
