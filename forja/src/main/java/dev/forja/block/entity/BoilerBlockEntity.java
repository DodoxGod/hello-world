package dev.forja.block.entity;

import dev.forja.block.BoilerBlock;
import dev.forja.forge.Alloys;
import dev.forja.forge.HeatFluid;
import dev.forja.forge.HeatSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A boiler or a heat depot, once a tick: turns what it is fed into its heat fluid and holds it for the
 * pipes (see {@link BoilerBlock}, {@link HeatFluid} and {@link HeatSources}).
 *
 * <p>Two slots on the furnace's convention: what it is fed goes in from the top and the sides, and what is
 * left of it (the empty buckets) comes out of the bottom. One fluid at a time: something that makes another
 * fluid waits until the vessel has run dry.
 */
public class BoilerBlockEntity extends BlockEntity implements WorldlyContainer {
	public static final int SLOT_INPUT = 0;
	public static final int SLOT_OUTPUT = 1;
	public static final int SIZE = 2;

	/** What a boiler holds, in mB: four buckets. */
	public static final int CALDERA_CAPACITY = 4 * HeatFluid.BUCKET;
	/** What a heat depot holds, in mB: eight buckets of lava. */
	public static final int DEPOSITO_CAPACITY = 8 * HeatFluid.BUCKET;
	/** Ticks a boiler takes to boil one item down into its fluid. */
	public static final int BOIL_TICKS = 40;
	/** Ticks a depot takes to take one item in (a bucket poured, a magma block melted). */
	public static final int POUR_TICKS = 10;

	private static final int[] TOP = {SLOT_INPUT};
	private static final int[] BOTTOM = {SLOT_OUTPUT};

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private @Nullable HeatFluid fluid;
	private int amount;
	private int progress;
	/** Why it is not boiling, for the hand: 0 nothing to say, 1 wants a fire under it, 2 full or another fluid. */
	private int waiting;
	/** What the pipes were last told this vessel offers, so they are only told when it changes. */
	private HeatFluid.@Nullable Shown offered;

	public BoilerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CALDERA, pos, state);
	}

	public HeatFluid.Vessel vessel() {
		return this.getBlockState().getBlock() instanceof BoilerBlock block ? block.vessel : HeatFluid.Vessel.CALDERA;
	}

	public int capacity() {
		return this.vessel() == HeatFluid.Vessel.DEPOSITO ? DEPOSITO_CAPACITY : CALDERA_CAPACITY;
	}

	/** The fluid it holds, or null when it is empty. */
	public @Nullable HeatFluid fluid() {
		return this.amount > 0 ? this.fluid : null;
	}

	public int amount() {
		return this.amount;
	}

	/** The comparator reading: 0 empty, 1 to 15 as it fills. */
	public int signal() {
		return this.amount <= 0 ? 0 : 1 + this.amount * 14 / this.capacity();
	}

	/** Takes up to {@code max} mB of this fluid out, and says how much it gave. */
	public int drain(HeatFluid wanted, int max) {
		if (this.fluid != wanted || this.amount <= 0 || max <= 0) {
			return 0;
		}
		int taken = Math.min(this.amount, max);
		this.amount -= taken;
		if (this.amount == 0) {
			this.fluid = null;
		}
		this.setChanged();
		return taken;
	}

	/** Puts fluid straight in, for tests and for whatever fills it from outside; says how much went in. */
	public int fill(HeatFluid what, int max) {
		if (this.amount > 0 && this.fluid != what) {
			return 0;
		}
		int put = Math.min(max, this.capacity() - this.amount);
		if (put <= 0) {
			return 0;
		}
		this.fluid = what;
		this.amount += put;
		this.setChanged();
		return put;
	}

	// ------------------------------------------------------------------ the work

	public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity vessel) {
		ItemStack input = vessel.items.get(SLOT_INPUT);
		HeatFluid.Yield yield = HeatFluid.yield(input, vessel.vessel());
		boolean working = false;
		vessel.waiting = 0;
		if (yield != null) {
			if (!vessel.takes(yield)) {
				vessel.waiting = 2;
			} else if (!vessel.fireFor(level, pos, yield)) {
				vessel.waiting = 1;
			} else {
				working = true;
				int needed = vessel.vessel() == HeatFluid.Vessel.DEPOSITO ? POUR_TICKS : BOIL_TICKS;
				if (++vessel.progress >= needed) {
					vessel.progress = 0;
					vessel.take(yield);
					input.shrink(1);
					if (!yield.leaves().isEmpty()) {
						ItemStack out = vessel.items.get(SLOT_OUTPUT);
						if (out.isEmpty()) {
							vessel.items.set(SLOT_OUTPUT, yield.leaves().copy());
						} else {
							out.grow(yield.leaves().getCount());
						}
					}
					level.playSound(null, pos, yield.fluid() == HeatFluid.LAVA ? SoundEvents.BUCKET_EMPTY_LAVA
						: yield.fluid().cools() ? SoundEvents.GLASS_BREAK : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.2F);
					vessel.setChanged();
				}
			}
		}
		if (!working) {
			vessel.progress = 0;
		}
		vessel.show(level, pos, state, working);
	}

	/** Whether there is room for what this item gives, of the same fluid, and somewhere for what it leaves. */
	private boolean takes(HeatFluid.Yield yield) {
		if (this.amount > 0 && this.fluid != yield.fluid()) {
			return false;
		}
		if (this.amount + yield.amount() > this.capacity()) {
			return false;
		}
		if (yield.leaves().isEmpty()) {
			return true;
		}
		ItemStack out = this.items.get(SLOT_OUTPUT);
		return out.isEmpty() || (ItemStack.isSameItemSameComponents(out, yield.leaves())
			&& out.getCount() + yield.leaves().getCount() <= out.getMaxStackSize());
	}

	/**
	 * Whether the fire under the vessel is hot enough for this: steam wants any, forge breath a hot one.
	 *
	 * <p>Only the block under ({@link Alloys#heatUnder}), never {@link Alloys#heatAt}: that counts heat pipes,
	 * and a boiler would boil its water on the steam in its own pipe for ever.
	 */
	private boolean fireFor(Level level, BlockPos pos, HeatFluid.Yield yield) {
		return yield.fire() == Alloys.Heat.FRIA || Alloys.heatUnder(level, pos).reaches(yield.fire());
	}

	private void take(HeatFluid.Yield yield) {
		this.fluid = yield.fluid();
		this.amount += yield.amount();
	}

	/** Keeps the block's look (fluid, how full, lit) and the pipes' colour in step with what is inside. */
	private void show(Level level, BlockPos pos, BlockState state, boolean working) {
		HeatFluid.Shown shown = HeatFluid.Shown.of(this.fluid());
		int fill = this.amount <= 0 ? 0 : 1 + Math.min(3, this.amount * 4 / (this.capacity() + 1));
		boolean lit = working && this.vessel() == HeatFluid.Vessel.CALDERA;
		if (state.getValue(BoilerBlock.FLUIDO) != shown || state.getValue(BoilerBlock.LEVEL) != fill
			|| state.getValue(BoilerBlock.LIT) != lit) {
			int signal = state.getValue(BoilerBlock.LEVEL);
			level.setBlock(pos, state.setValue(BoilerBlock.FLUIDO, shown).setValue(BoilerBlock.LEVEL, fill)
				.setValue(BoilerBlock.LIT, lit), Block.UPDATE_CLIENTS);
			if (signal != fill) {
				level.updateNeighbourForOutputSignal(pos, state.getBlock());
			}
		}
		if (this.offered != shown) {
			this.offered = shown;
			HeatSources.refreshAround(level, pos);
		}
	}

	// ------------------------------------------------------------------ hands

	/**
	 * A right click with something it takes. A bucket is poured at once, as into a cauldron, and the empty
	 * bucket comes back; anything else goes into the vessel's slot to be boiled down.
	 */
	public boolean handIn(Player player, ItemStack held) {
		HeatFluid.Yield yield = HeatFluid.yield(held, this.vessel());
		if (yield == null || this.level == null) {
			return false;
		}
		if (!yield.leaves().isEmpty()) {
			if (!this.takesNow(yield) || !this.fireFor(this.level, this.worldPosition, yield)) {
				this.say(player, this.describe());
				return false;
			}
			this.take(yield);
			held.shrink(1);
			player.getInventory().placeItemBackInInventory(yield.leaves().copy());
			this.level.playSound(null, this.worldPosition, yield.fluid() == HeatFluid.LAVA ? SoundEvents.BUCKET_EMPTY_LAVA
				: SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
			this.setChanged();
			return true;
		}
		ItemStack slot = this.items.get(SLOT_INPUT);
		if (slot.isEmpty()) {
			this.items.set(SLOT_INPUT, held.split(held.getCount()));
		} else if (ItemStack.isSameItemSameComponents(slot, held) && slot.getCount() < slot.getMaxStackSize()) {
			int moved = Math.min(held.getCount(), slot.getMaxStackSize() - slot.getCount());
			slot.grow(moved);
			held.shrink(moved);
		} else {
			this.say(player, this.describe());
			return false;
		}
		this.level.playSound(null, this.worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 0.8F);
		this.setChanged();
		return true;
	}

	/** Room for a bucket poured by hand: the same test as {@link #takes}, but the bucket goes back to the hand. */
	private boolean takesNow(HeatFluid.Yield yield) {
		return (this.amount <= 0 || this.fluid == yield.fluid()) && this.amount + yield.amount() <= this.capacity();
	}

	/** An empty hand: the empty buckets come out, or a look at what is inside. */
	public void handOut(Player player) {
		ItemStack out = this.items.get(SLOT_OUTPUT);
		if (!out.isEmpty()) {
			player.getInventory().placeItemBackInInventory(out.copy());
			this.items.set(SLOT_OUTPUT, ItemStack.EMPTY);
			this.setChanged();
			return;
		}
		this.say(player, this.describe());
	}

	/** What it holds, and why it is not boiling when that is worth saying. */
	public Component describe() {
		Component name = this.getBlockState().getBlock().getName();
		Component inside = this.fluid() == null
			? Component.translatable("gui.forja.caldera.vacia", name, this.capacity())
			: Component.translatable("gui.forja.caldera.tiene", name, this.fluid().displayName(), this.amount, this.capacity());
		if (this.waiting == 1) {
			HeatFluid.Yield yield = HeatFluid.yield(this.items.get(SLOT_INPUT), this.vessel());
			if (yield != null) {
				return Component.empty().append(inside).append(" · ")
					.append(Component.translatable("gui.forja.caldera.sin_fuego", yield.fire().displayName()));
			}
		}
		if (this.waiting == 2) {
			return Component.empty().append(inside).append(" · ").append(Component.translatable("gui.forja.caldera.espera"));
		}
		return inside;
	}

	private void say(Player player, Component line) {
		if (player instanceof net.minecraft.server.level.ServerPlayer smith) {
			smith.sendSystemMessage(line);
		}
	}

	// ------------------------------------------------------------------ container

	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? BOTTOM : TOP;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return side != Direction.DOWN && this.canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == SLOT_OUTPUT;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == SLOT_INPUT && HeatFluid.yield(stack, this.vessel()) != null;
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
		stack.limitSize(this.getMaxStackSize(stack));
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		this.items.clear();
		this.setChanged();
	}

	/** Breaking it spills what is in its slots; the fluid is lost with the vessel. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null) {
			net.minecraft.world.Containers.dropContents(this.level, pos, this);
		}
	}

	// ------------------------------------------------------------------ saving

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		String id = input.getStringOr("Fluid", "");
		this.fluid = null;
		for (HeatFluid each : HeatFluid.values()) {
			if (each.id().equals(id)) {
				this.fluid = each;
			}
		}
		this.amount = this.fluid == null ? 0 : Math.max(0, input.getIntOr("Amount", 0));
		this.progress = input.getIntOr("Progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		if (this.fluid != null && this.amount > 0) {
			output.putString("Fluid", this.fluid.id());
			output.putInt("Amount", this.amount);
		}
		output.putInt("Progress", this.progress);
	}

	/** How far along boiling the item in its slot is, in ticks. */
	public int progress() {
		return this.progress;
	}
}
