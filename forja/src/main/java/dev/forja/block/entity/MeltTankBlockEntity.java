package dev.forja.block.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.forja.block.MeltTankBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
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
 * A tank of molten metal, and the bank of tanks it belongs to.
 *
 * <p>There is deliberately no master block and no multiblock to validate. Every tank holds its own
 * metal, and an operation walks the bank it is touching and spreads the work over it: a fill goes into
 * the lowest tank with room, a draw comes out of the highest tank with metal, so the level in the glass
 * rises and falls the way a real bank of them would. Breaking one out of the middle of a wall therefore
 * cannot corrupt anything: the two halves are simply two banks from the next tick on.
 *
 * <p>One bank holds one metal. That is the rule the whole thing is built around — it is what turns a
 * foundry into rows of labelled banks instead of one undifferentiated warehouse.
 */
public class MeltTankBlockEntity extends BlockEntity {
	/** How much one tank holds, counted in ingots. */
	public static final int CAPACITY = 256;

	/** How far a bank is allowed to run, so a wall of them can never cost more than this to walk. */
	public static final int MAX_TANKS = 256;

	/** How often it pushes into whatever is underneath, and how much goes each time. */
	public static final int PUSH_EVERY = 20;

	/**
	 * How hot the metal is, 0..{@link #HOT}. A tank left alone cools; one kept over a fire does not.
	 *
	 * <p>This is what stops a foundry being a wall you build once and never look at again. Metal that
	 * goes cold has to be melted again, and melting it again costs you some of it.
	 */
	public static final int HOT = 200;

	/** What it loses a second with nothing keeping it warm, and what a heat source under it gives back. */
	public static final int COOLS = 2;
	public static final int WARMS = 8;

	/** Below this the metal has set: it is still there, but nothing will pour it until it is melted again. */
	public static final int SET = 1;

	/** The share of a bank that is lost when a crucible melts it back down. */
	public static final float REMELT_LOSS = 0.15F;

	private @Nullable Item metal;
	private int amount;

	/**
	 * Ticks until this tank next tries to empty itself.
	 *
	 * <p>It is a counter of its own rather than a test against the world clock, because with the clock
	 * every tank in the world pushes on the same tick: a wall of two hundred would spike once a second
	 * and idle the rest of it. Started off the position so a bank staggers itself.
	 */
	private int pushIn = -1;

	/** How hot this tank is. Saved, because a foundry left overnight should be cold in the morning. */
	private int heat;

	public MeltTankBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CUBA, pos, state);
	}

	// ------------------------------------------------------------------ this one tank

	public @Nullable Item metal() {
		return this.metal;
	}

	public int amount() {
		return this.amount;
	}

	public int heat() {
		return this.heat;
	}

	/** Whether the metal in here has set. A set bank holds its metal; it simply will not pour it. */
	public boolean isSet() {
		return this.amount > 0 && this.heat < SET;
	}

	/** Puts heat back into this tank, which is what a pipe carrying hot metal and a fire under it do. */
	public void warm(int by) {
		int was = this.heat;
		this.heat = Math.min(HOT, this.heat + by);
		if ((was < SET) != (this.heat < SET)) {
			this.changed();
		}
	}

	public int room() {
		return CAPACITY - this.amount;
	}

	/** Whether this tank would take that metal: either it is empty or it is already holding it. */
	public boolean accepts(Item item) {
		return this.amount <= 0 || this.metal == item;
	}

	private void put(Item item, int count, int heat) {
		this.metal = item;
		this.amount += count;
		// Metal arriving is metal that was just molten, so it brings its own heat with it — less
		// whatever the road here took out of it.
		this.heat = Math.max(0, Math.min(HOT, heat));
		this.changed();
	}

	private void take(int count) {
		this.amount -= count;
		if (this.amount <= 0) {
			this.amount = 0;
			this.metal = null;
		}
		this.changed();
	}

	/** Saves, tells the client, and moves the level in the glass if it crossed a quarter. */
	private void changed() {
		this.setChanged();
		if (this.level == null) {
			return;
		}
		int shown = MeltTankBlock.levelFor(this.amount, CAPACITY);
		BlockState state = this.getBlockState();
		if (state.hasProperty(MeltTankBlock.LEVEL) && state.getValue(MeltTankBlock.LEVEL) != shown) {
			this.level.setBlock(this.worldPosition, state.setValue(MeltTankBlock.LEVEL, shown), Block.UPDATE_ALL);
		} else {
			this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
		}
	}

	// ------------------------------------------------------------------ the bank

	/** Every tank touching this one, directly or through others, lowest first. */
	public List<MeltTankBlockEntity> bank() {
		List<MeltTankBlockEntity> found = new ArrayList<>();
		if (this.level == null) {
			return found;
		}
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(this.worldPosition);
		seen.add(this.worldPosition);
		while (!queue.isEmpty() && found.size() < MAX_TANKS) {
			BlockPos at = queue.poll();
			if (!(this.level.getBlockEntity(at) instanceof MeltTankBlockEntity tank)) {
				continue;
			}
			found.add(tank);
			for (Direction side : Direction.values()) {
				BlockPos next = at.relative(side);
				if (seen.add(next) && this.level.getBlockState(next).getBlock() instanceof MeltTankBlock) {
					queue.add(next);
				}
			}
		}
		found.sort(Comparator.comparingInt(tank -> tank.getBlockPos().getY()));
		return found;
	}

	/** What the whole bank is holding, for the hand and for Jade. */
	public int bankAmount() {
		int total = 0;
		for (MeltTankBlockEntity tank : this.bank()) {
			total += tank.amount;
		}
		return total;
	}

	public int bankCapacity() {
		return this.bank().size() * CAPACITY;
	}

	/** The metal the bank is holding, or null if every tank in it is dry. */
	public @Nullable Item bankMetal() {
		for (MeltTankBlockEntity tank : this.bank()) {
			if (tank.metal != null) {
				return tank.metal;
			}
		}
		return null;
	}

	/**
	 * Pours into the bank, lowest tank first, and gives back how much did not fit.
	 *
	 * <p>Filling from the bottom is not only for looks: it means a bank that is being drawn from the top
	 * and filled from the bottom keeps its metal where the glass shows it.
	 */
	public int fill(Item item, int count) {
		return this.fill(item, count, 0);
	}

	/**
	 * The same, for metal that has come a long way.
	 *
	 * <p>{@code bled} is what the run of pipe took out of it, so a bank at the end of a cheap bronze
	 * tendril is a bank that sets sooner than one built against the pot. It is the whole reason the
	 * grades exist.
	 */
	public int fill(Item item, int count, int bled) {
		List<MeltTankBlockEntity> bank = this.bank();
		Item held = this.bankMetal();
		if (held != null && held != item) {
			return count;
		}
		int left = count;
		for (MeltTankBlockEntity tank : bank) {
			if (left <= 0) {
				break;
			}
			int room = Math.min(tank.room(), left);
			if (room > 0) {
				tank.put(item, room, HOT - bled);
				left -= room;
			}
		}
		return left;
	}

	/** Draws out of the bank, highest tank first, and gives back what it actually got. */
	public int drain(int count) {
		List<MeltTankBlockEntity> bank = this.bank();
		int taken = 0;
		for (int i = bank.size() - 1; i >= 0 && taken < count; i--) {
			MeltTankBlockEntity tank = bank.get(i);
			int got = Math.min(tank.amount, count - taken);
			if (got > 0) {
				tank.take(got);
				taken += got;
			}
		}
		return taken;
	}

	// ------------------------------------------------------------------ the world

	public static void serverTick(Level level, BlockPos pos, BlockState state, MeltTankBlockEntity tank) {
		if (tank.pushIn < 0) {
			tank.pushIn = Math.floorMod(pos.hashCode(), PUSH_EVERY);
		}
		if (tank.pushIn-- > 0) {
			return;
		}
		tank.pushIn = PUSH_EVERY;
		tank.settle(level, pos);
		if (tank.isSet()) {
			// Set metal goes nowhere. Melt it again first.
			return;
		}
		// Anything that can hold items under the tank gets a stack a second, which is the whole of the
		// automation story out of one: tank, hopper, chest. The container is checked before the bank is
		// walked, because in a wall of two hundred tanks only the bottom row has anything underneath and
		// the other hundred and ninety should not be paying for a search every second.
		List<Container> outs = new ArrayList<>();
		if (level.getBlockEntity(pos.below()) instanceof Container below) {
			outs.add(below);
		}
		// And down a pipe, if one is attached. The pipe check comes first so a tank with neither a
		// container under it nor a pipe on it costs one block lookup a second and nothing more.
		boolean piped = false;
		for (Direction side : Direction.values()) {
			if (level.getBlockState(pos.relative(side)).getBlock() instanceof dev.forja.block.MeltPipeBlock) {
				piped = true;
				break;
			}
		}
		if (piped) {
			outs.addAll(dev.forja.block.MeltPipeBlock.containersFrom(level, pos));
		}
		if (outs.isEmpty()) {
			return;
		}
		Item metal = tank.bankMetal();
		if (metal == null) {
			return;
		}
		for (Container out : outs) {
			int wanted = Math.min(new ItemStack(metal).getMaxStackSize(), tank.bankAmount());
			if (wanted <= 0) {
				return;
			}
			int placed = place(out, new ItemStack(metal, wanted), Direction.UP);
			if (placed > 0) {
				tank.drain(placed);
			}
		}
	}

	/**
	 * One second of heat: what the fire under it gives, less what the air takes.
	 *
	 * <p>Anything that burns counts, but the wisp lantern is the one built for it — a bank standing on a
	 * floor of them never sets, which is the second job the lantern was always going to get.
	 */
	private void settle(Level level, BlockPos pos) {
		if (this.amount <= 0) {
			this.heat = 0;
			return;
		}
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
		this.heat = Math.max(0, Math.min(HOT, this.heat + (warmed ? WARMS : -COOLS)));
		if ((was < SET) != (this.heat < SET)) {
			if (this.heat < SET && level instanceof net.minecraft.server.level.ServerLevel server) {
				server.playSound(null, pos, net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH,
					net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 0.6F);
			}
			this.changed();
		}
	}

	/**
	 * Melts a bank that has set, losing part of it.
	 *
	 * <p>What is lost is the price of having walked away: the metal is still yours, it just does not all
	 * come back. A crucible beside the bank does this by itself.
	 */
	public int remelt() {
		int lost = 0;
		for (MeltTankBlockEntity tank : this.bank()) {
			if (tank.amount <= 0 || tank.heat >= SET) {
				continue;
			}
			int burnt = Math.max(1, Math.round(tank.amount * REMELT_LOSS));
			tank.amount = Math.max(0, tank.amount - burnt);
			lost += burnt;
			if (tank.amount <= 0) {
				tank.metal = null;
			}
			tank.heat = HOT;
			tank.changed();
		}
		return lost;
	}

	/** Puts as much of a stack as will go into a container, and says how much went. */
	private static int place(Container container, ItemStack stack, Direction side) {
		int[] slots = container instanceof net.minecraft.world.WorldlyContainer worldly
			? worldly.getSlotsForFace(side)
			: java.util.stream.IntStream.range(0, container.getContainerSize()).toArray();
		int left = stack.getCount();
		for (int slot : slots) {
			if (left <= 0) {
				break;
			}
			if (!container.canPlaceItem(slot, stack)) {
				continue;
			}
			if (container instanceof net.minecraft.world.WorldlyContainer worldly
				&& !worldly.canPlaceItemThroughFace(slot, stack, side)) {
				continue;
			}
			ItemStack there = container.getItem(slot);
			if (there.isEmpty()) {
				int moved = Math.min(left, stack.getMaxStackSize());
				container.setItem(slot, stack.copyWithCount(moved));
				left -= moved;
			} else if (ItemStack.isSameItemSameComponents(there, stack)) {
				int moved = Math.min(left, there.getMaxStackSize() - there.getCount());
				there.grow(moved);
				left -= moved;
			}
		}
		if (left < stack.getCount()) {
			container.setChanged();
		}
		return stack.getCount() - left;
	}

	/**
	 * Every tank something standing at this position can reach: the ones it touches, and the ones at the
	 * end of any pipe run attached to it.
	 *
	 * <p>The casting boxes and the casting tables both feed out of the same tanks by the same rule, so
	 * the rule lives here rather than twice over in two block entities.
	 */
	public static List<MeltTankBlockEntity> reachableFrom(Level level, BlockPos pos) {
		List<MeltTankBlockEntity> found = new ArrayList<>();
		boolean piped = false;
		for (Direction side : Direction.values()) {
			BlockPos at = pos.relative(side);
			if (level.getBlockEntity(at) instanceof MeltTankBlockEntity tank) {
				found.add(tank);
			} else if (level.getBlockState(at).getBlock() instanceof dev.forja.block.MeltPipeBlock) {
				piped = true;
			}
		}
		// A spout pouring from a gantry is not touching anything down here, so a block that only ever
		// looked at its own six sides would never find the metal falling into it.
		if (!piped && dev.forja.block.MeltPipeBlock.spoutAbove(level, pos) != null) {
			piped = true;
		}
		if (piped) {
			for (BlockPos end : dev.forja.block.MeltPipeBlock.reachable(level, pos)) {
				if (level.getBlockEntity(end) instanceof MeltTankBlockEntity tank && !found.contains(tank)) {
					found.add(tank);
				}
			}
		}
		return found;
	}

	/** A full hand pours in, an empty hand draws out, and either way it says where it is at. */
	public boolean hand(Player player, ItemStack held) {
		if (this.level == null) {
			return false;
		}
		if (!held.isEmpty()) {
			Item metal = this.bankMetal();
			if (metal != null && metal != held.getItem()) {
				this.say(player, Component.translatable("gui.forja.cuba.otro", new ItemStack(metal).getHoverName()));
				return false;
			}
			int left = this.fill(held.getItem(), held.getCount());
			if (left == held.getCount()) {
				this.say(player, Component.translatable("gui.forja.cuba.llena"));
				return false;
			}
			held.setCount(left);
			this.say(player, this.describe());
			return true;
		}
		Item metal = this.bankMetal();
		if (metal == null) {
			this.say(player, Component.translatable("gui.forja.cuba.vacia", this.bank().size(), this.bankCapacity()));
			return false;
		}
		int wanted = player.isShiftKeyDown() ? new ItemStack(metal).getMaxStackSize() : 1;
		int got = this.drain(wanted);
		if (got > 0) {
			player.getInventory().placeItemBackInInventory(new ItemStack(metal, got));
		}
		this.say(player, this.describe());
		return true;
	}

	/** What the bank is holding, in one line. */
	public Component describe() {
		Item metal = this.bankMetal();
		if (metal == null) {
			return Component.translatable("gui.forja.cuba.vacia", this.bank().size(), this.bankCapacity());
		}
		if (this.isSet()) {
			return Component.translatable("gui.forja.cuba.cuajada");
		}
		return Component.translatable("gui.forja.cuba.dentro", new ItemStack(metal).getHoverName(),
			this.bankAmount(), this.bankCapacity(), this.bank().size())
			.copy().append(" · ").append(Component.translatable("gui.forja.cuba.calor", this.heat * 100 / HOT));
	}

	private void say(Player player, Component line) {
		if (player instanceof net.minecraft.server.level.ServerPlayer smith) {
			smith.sendSystemMessage(line);
		}
	}

	// ------------------------------------------------------------------ saving and syncing

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.amount = input.getIntOr("Amount", 0);
		this.heat = input.getIntOr("Heat", HOT);
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
		output.putInt("Amount", this.amount);
		output.putInt("Heat", this.heat);
		if (this.metal != null) {
			output.putString("Metal", BuiltInRegistries.ITEM.getKey(this.metal).toString());
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
