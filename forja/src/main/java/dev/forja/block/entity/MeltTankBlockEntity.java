package dev.forja.block.entity;

import java.util.ArrayList;
import java.util.List;

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
 * A tank of molten metal, and its share of the deposit it belongs to.
 *
 * <p>Tanks of the same metal touching each other are one {@link MeltDeposit} (Andy, 2026-09-28: "cubas
 * del mismo material pegadas forman un depósito"): one capacity, one metal, one level drawn across the
 * glass from the bottom up. There is still no master block and no multiblock to validate. The deposit is
 * worked out from the tanks when it is first asked for and remembered until a tank next to it is placed,
 * broken, loaded or unloaded (see {@link MeltNetwork}); every operation goes through it; and each tank
 * keeps its own share of it, written back every time the level moves. That share is what a tank saves,
 * spills when broken and tells the client, so breaking one out of the middle of a wall cannot corrupt
 * anything: the pieces are simply deposits of what they were holding.
 *
 * <p>One deposit holds one metal. That is the rule the whole thing is built around — it is what turns a
 * foundry into rows of labelled banks instead of one undifferentiated warehouse.
 */
public class MeltTankBlockEntity extends BlockEntity {
	/** How much one tank holds, counted in ingots. */
	public static final int CAPACITY = 256;

	/** How far a deposit is allowed to run, so a wall of them can never cost more than this to work out. */
	public static final int MAX_TANKS = 256;

	/** How often it pushes into whatever is underneath, and how much goes each time. */
	public static final int PUSH_EVERY = 20;

	/**
	 * How hot the metal is, 0..{@link #HOT}. A deposit left alone cools; one kept over a fire does not.
	 *
	 * <p>This is what stops a foundry being a wall you build once and never look at again. Metal that
	 * goes cold has to be melted again, and melting it again costs you some of it.
	 */
	public static final int HOT = 200;

	/** What it loses a second with nothing keeping it warm, and what a heat source beside it gives back. */
	public static final int COOLS = 2;
	public static final int WARMS = 8;

	/** Below this the metal has set: it is still there, but nothing will pour it until it is melted again. */
	public static final int SET = 1;

	/** The share of a deposit that is lost when a crucible melts it back down. */
	public static final float REMELT_LOSS = 0.15F;

	/** This tank's share of the deposit: which metal, and how much of it is drawn in this block of glass. */
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

	/** The deposit's heat, mirrored here so it is saved. A foundry left overnight should be cold in the morning. */
	private int heat;

	/** The deposit this tank belongs to, while nothing around it has changed. Never saved. */
	private @Nullable MeltDeposit deposit;

	public MeltTankBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CUBA, pos, state);
	}

	// ------------------------------------------------------------------ this one block of glass

	/** The metal drawn in this block, or null when the level of the deposit does not reach it. */
	public @Nullable Item metal() {
		return this.metal;
	}

	/** How much of the deposit is drawn in this block. */
	public int amount() {
		return this.amount;
	}

	public int heat() {
		return this.heat;
	}

	/** Whether the metal in the deposit has set. A set deposit holds its metal; it simply will not pour it. */
	public boolean isSet() {
		return this.deposit().isSet();
	}

	/** Puts heat back into the deposit, which is what a pipe carrying hot metal and a fire beside it do. */
	public void warm(int by) {
		this.deposit().warm(by);
	}

	/** Room left in this block of glass alone. The deposit's room is {@link MeltDeposit#room()}. */
	public int room() {
		return CAPACITY - this.amount;
	}

	/**
	 * Whether this is something a tank holds at all: a metal that has to be poured, or something an
	 * alloy is made of (the foundry alloys want a bank of redstone or quartz behind the pot).
	 *
	 * <p>It took anything. A right click with a stick, a block of dirt or an enchanted sword poured it in
	 * as "metal", kept the item and threw away everything that made it that sword, and the casting box
	 * would then happily look for a material in a bank of sticks.
	 */
	public static boolean holds(Item item) {
		ItemStack stack = new ItemStack(item);
		dev.forja.material.ForgeMaterial material = dev.forja.material.ForgeMaterial.fromInput(stack);
		if (material != null && !material.isBasic()) {
			return true;
		}
		for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.forge.Alloys.POURABLE) {
			for (dev.forja.forge.Alloys.Part part : recipe.inputs()) {
				if (item == part.item().get()) {
					return true;
				}
			}
		}
		// Oricalco is no gear metal and goes into nothing else, but it is poured into tanks and cast from them.
		return item == dev.forja.registry.ModItems.ORICALCO;
	}

	/** Whether the deposit would take that metal: either it is empty or it is already holding it. */
	public boolean accepts(Item item) {
		return this.deposit().accepts(item);
	}

	/**
	 * The deposit writes this tank's share. Heat is only mirrored; the metal and how much of it are what
	 * the glass shows, so only those send anything to the client.
	 */
	void share(@Nullable Item metal, int amount, int heat) {
		this.heat = heat;
		if (this.metal == metal && this.amount == amount) {
			return;
		}
		this.metal = amount > 0 ? metal : null;
		this.amount = Math.max(0, amount);
		this.changed();
	}

	/** The deposit's heat moved. Saved with the tank; the client never draws heat, so it is not told. */
	void mirrorHeat(int heat) {
		this.heat = heat;
		if (this.level != null && !this.isRemoved()) {
			this.level.blockEntityChanged(this.worldPosition);
		}
	}

	/** Saves, tells the client, and moves the level in the glass if it crossed a quarter. */
	private void changed() {
		this.setChanged();
		if (this.level == null || this.isRemoved()) {
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

	// ------------------------------------------------------------------ the deposit

	/** The deposit this tank belongs to, worked out now if nothing is remembered for it. */
	public MeltDeposit deposit() {
		return MeltNetwork.deposit(this);
	}

	@Nullable MeltDeposit cachedDeposit() {
		return this.deposit;
	}

	void setDeposit(MeltDeposit deposit) {
		this.deposit = deposit;
	}

	void forgetDeposit(MeltDeposit deposit) {
		if (this.deposit == deposit) {
			this.deposit = null;
		}
	}

	/** Every tank of this deposit, lowest first. */
	public List<MeltTankBlockEntity> bank() {
		return this.deposit().members();
	}

	/** What the whole deposit is holding, for the hand, the tables and the comparators. */
	public int bankAmount() {
		return this.deposit().amount();
	}

	public int bankCapacity() {
		return this.deposit().capacity();
	}

	/** The metal the deposit is holding, or null if it is dry. */
	public @Nullable Item bankMetal() {
		return this.deposit().metal();
	}

	/** Pours into the deposit (bottom up) and gives back how much did not fit. */
	public int fill(Item item, int count) {
		return this.fill(item, count, 0);
	}

	/**
	 * The same, for metal that has come a long way.
	 *
	 * <p>{@code bled} is what the run of pipe took out of it, so a deposit at the end of a cheap bronze
	 * tendril is a deposit that sets sooner than one built against the pot. It is the whole reason the
	 * grades exist.
	 */
	public int fill(Item item, int count, int bled) {
		return this.deposit().fill(item, count, bled);
	}

	/** Draws out of the deposit (off the top) and gives back what it actually got. */
	public int drain(int count) {
		return this.deposit().drain(count);
	}

	/**
	 * Melts a deposit that has set, losing part of it.
	 *
	 * <p>What is lost is the price of having walked away: the metal is still yours, it just does not all
	 * come back. A crucible beside the deposit does this by itself.
	 */
	public int remelt() {
		return this.deposit().remelt();
	}

	/** What a comparator beside this tank reads: how full the whole deposit is. */
	public int signal() {
		return this.deposit().signal();
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
		MeltDeposit deposit = tank.deposit();
		deposit.vote(level);
		if (deposit.isSet()) {
			// Set metal goes nowhere. Melt it again first.
			return;
		}
		// The tap: whatever holds items right UNDER a tank gets a stack a second, as ingots. It is the one
		// way metal leaves the network as items, and it has to be built on purpose — tank, hopper, chest.
		// It used to be any container at the end of any pipe as well, and a hopper that merely touched a
		// pipe (the one feeding the crucible its ore) was handed the tank's metal as bars and fed it
		// straight back into the pot: see MeltPipeBlock#joins.
		BlockEntity under = level.getBlockEntity(pos.below());
		if (!(under instanceof Container out) || feedsACrucible(level, under)) {
			return;
		}
		Item metal = deposit.metal();
		if (metal == null) {
			return;
		}
		int wanted = Math.min(new ItemStack(metal).getMaxStackSize(), deposit.amount());
		int placed = wanted <= 0 ? 0 : place(out, new ItemStack(metal, wanted), Direction.UP);
		if (placed > 0) {
			deposit.drain(placed);
		}
	}

	/**
	 * Whether this is a crucible, or a hopper pouring into one. A tank over a pot filled the pot's slots with
	 * the metal the pot then melted straight back into the tank, burning embers to go round in a circle; a
	 * hopper between the two is the same circle one block longer.
	 */
	private static boolean feedsACrucible(Level level, BlockEntity under) {
		if (under instanceof CrucibleBlockEntity) {
			return true;
		}
		if (under instanceof net.minecraft.world.level.block.entity.HopperBlockEntity) {
			BlockState state = under.getBlockState();
			if (state.hasProperty(net.minecraft.world.level.block.HopperBlock.FACING)) {
				BlockPos into = under.getBlockPos().relative(state.getValue(net.minecraft.world.level.block.HopperBlock.FACING));
				return level.getBlockEntity(into) instanceof CrucibleBlockEntity;
			}
		}
		return false;
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
	 * One tank out of every deposit something standing at this position can reach, nearest first: the
	 * ones it touches, the ones at the end of any pipe run attached to it however long, and the one a
	 * spout overhead is pouring from.
	 *
	 * <p>The casting boxes and the casting tables both feed out of the same deposits by the same rule, so
	 * the rule lives here rather than twice over in two block entities. The answer comes out of the
	 * network's cache (MeltNetwork), which is only worked out again when a block of the network changes.
	 */
	public static List<MeltTankBlockEntity> reachableFrom(Level level, BlockPos pos) {
		List<MeltTankBlockEntity> found = new ArrayList<>();
		for (MeltNetwork.Fed fed : MeltNetwork.deposits(level, pos)) {
			found.add(fed.via());
		}
		return found;
	}

	/**
	 * Breaking one spills what was in that tank, not what was in the deposit.
	 *
	 * <p>The rest of the wall keeps its metal, which is what makes taking a tank out of the middle of a
	 * deposit a cheap mistake instead of an expensive one. It is done here and not in the block, whose
	 * affectNeighborsAfterRemoval only runs once this block entity is gone: every broken tank lost its
	 * metal outright.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level == null || this.metal == null || this.amount <= 0) {
			return;
		}
		ItemStack spilled = new ItemStack(this.metal, this.amount);
		while (!spilled.isEmpty()) {
			net.minecraft.world.Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				spilled.split(spilled.getMaxStackSize()));
		}
	}

	/**
	 * Placed, loaded, broken or unloaded: the deposits and the pipe runs around this position are worked
	 * out again the next time they are asked for. This is how an older world's loose tanks become a
	 * deposit — and how breaking one splits the deposit it was in.
	 */
	@Override
	public void clearRemoved() {
		super.clearRemoved();
		MeltNetwork.changed(this.level, this.worldPosition);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		MeltNetwork.changed(this.level, this.worldPosition);
	}

	/** A full hand pours in, an empty hand draws out, and either way it says where it is at. */
	public boolean hand(Player player, ItemStack held) {
		if (this.level == null) {
			return false;
		}
		if (!held.isEmpty()) {
			// Only a plain metal goes in: a stack with anything on it (a name, an enchantment, the parts
			// of a forged piece) would come back out as the bare item.
			if (!holds(held.getItem()) || !held.getComponentsPatch().isEmpty()) {
				this.say(player, Component.translatable("gui.forja.cuba.no_metal", held.getHoverName()));
				return false;
			}
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

	/** What the deposit is holding, in one line. */
	public Component describe() {
		MeltDeposit deposit = this.deposit();
		Item metal = deposit.metal();
		if (metal == null) {
			return Component.translatable("gui.forja.cuba.vacia", deposit.members().size(), deposit.capacity());
		}
		if (deposit.isSet()) {
			return Component.translatable("gui.forja.cuba.cuajada");
		}
		return Component.translatable("gui.forja.cuba.dentro", new ItemStack(metal).getHoverName(),
			deposit.amount(), deposit.capacity(), deposit.members().size())
			.copy().append(" · ").append(Component.translatable("gui.forja.cuba.calor", deposit.heat() * 100 / HOT));
	}

	private void say(Player player, Component line) {
		if (player instanceof net.minecraft.server.level.ServerPlayer smith) {
			smith.sendSystemMessage(line);
		}
	}

	// ------------------------------------------------------------------ saving and syncing

	/**
	 * The same three numbers a tank has always saved — its metal, its share and its heat — so a world from
	 * before deposits loads as it was and becomes a deposit the first time anything looks at it.
	 */
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
