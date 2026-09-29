package dev.forja.block.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A deposit: tanks of the same metal touching each other, which are one container.
 *
 * <p>Andy, 2026-09-28, asked for the containers to join up both ways — by pipe (see {@link MeltNetwork})
 * and by <b>merging</b>: "cubas del mismo material pegadas (en cualquier dirección) forman un depósito: una
 * sola capacidad (la suma), un solo metal, un solo nivel que se dibuja repartido entre los bloques de
 * abajo a arriba". So a deposit holds one number — how much metal — and the tanks are only where it is
 * drawn: the bottom layer fills first, every tank in a layer to the same height, then the layer above.
 *
 * <p>The tanks still keep their own share of it, written back every time the level moves. That is what
 * makes everything else simple: a tank saves its share, so a world saved with a deposit loads as loose
 * tanks that form the same deposit again (and an older world, whose tanks were never levelled, simply
 * gets levelled the first time it is looked at); breaking one tank spills that tank's share and the
 * pieces left behind become deposits of what they were holding; and a client only ever needs to be told
 * what is in each block of glass.
 *
 * <p>Heat is the deposit's too. It is settled once a second for the whole deposit — each tank's own
 * tick casts a vote, and the deposit settles when every one of them has — so a wall of forty tanks does
 * not cool forty times faster than one.
 */
public final class MeltDeposit {
	/** Lowest first, and a fixed order within a layer, so the same tanks always level the same way. */
	static final Comparator<MeltTankBlockEntity> ORDER = Comparator
		.comparingInt((MeltTankBlockEntity tank) -> tank.getBlockPos().getY())
		.thenComparingInt(tank -> tank.getBlockPos().getX())
		.thenComparingInt(tank -> tank.getBlockPos().getZ());

	final @Nullable Level level;
	final List<MeltTankBlockEntity> members;
	private @Nullable Item metal;
	private int amount;
	private int heat;
	boolean valid = true;
	/** The other deposits of the same cluster of glass, when two metals touch; null when this is all of it. */
	@Nullable List<MeltDeposit> cluster;
	/** What the comparators were last told, so they are only told again when it changes. */
	private int signal = -1;
	private int votes;
	/** Whether this is a tank on its own outside any level (a test's copy), which is never levelled. */
	private final boolean detached;

	MeltDeposit(@Nullable Level level, List<MeltTankBlockEntity> members) {
		this(level, members, false);
	}

	private MeltDeposit(@Nullable Level level, List<MeltTankBlockEntity> members, boolean detached) {
		this.level = level;
		this.detached = detached;
		List<MeltTankBlockEntity> sorted = new ArrayList<>(members);
		sorted.sort(ORDER);
		this.members = List.copyOf(sorted);
		long warmth = 0;
		for (MeltTankBlockEntity tank : this.members) {
			if (tank.metal() != null && tank.amount() > 0) {
				if (this.metal == null) {
					this.metal = tank.metal();
				}
				if (tank.metal() == this.metal) {
					this.amount += tank.amount();
					warmth += (long) tank.amount() * tank.heat();
				}
			}
		}
		// What each tank held, weighed by how much it held: two halves of a deposit that are joined again
		// come back at the heat of the bigger half, not at whichever tank was looked at first.
		this.heat = this.amount > 0 ? (int) (warmth / this.amount) : 0;
	}

	/** One tank as its own deposit, for a tank with no level to look around in. */
	static MeltDeposit alone(MeltTankBlockEntity tank) {
		return new MeltDeposit(tank.getLevel(), List.of(tank), true);
	}

	// ------------------------------------------------------------------ what it is

	public @Nullable Item metal() {
		return this.amount > 0 ? this.metal : null;
	}

	public int amount() {
		return this.amount;
	}

	public int capacity() {
		return this.members.size() * MeltTankBlockEntity.CAPACITY;
	}

	public int room() {
		return this.capacity() - this.amount;
	}

	public boolean full() {
		return this.amount >= this.capacity();
	}

	public int heat() {
		return this.heat;
	}

	public boolean isSet() {
		return this.amount > 0 && this.heat < MeltTankBlockEntity.SET;
	}

	public List<MeltTankBlockEntity> members() {
		return this.members;
	}

	public boolean valid() {
		return this.valid;
	}

	/** Whether this metal may go in: the deposit is empty or already holding it. One deposit, one metal. */
	public boolean accepts(Item item) {
		return this.amount <= 0 || this.metal == item;
	}

	/** What a comparator reads beside any of its tanks: 0 empty, 15 full, like a chest. */
	public int signal() {
		if (this.amount <= 0) {
			return 0;
		}
		return Math.min(15, 1 + (int) (14L * this.amount / this.capacity()));
	}

	// ------------------------------------------------------------------ what can be done to it

	/**
	 * Pours in, and gives back how much did not fit.
	 *
	 * <p>{@code bled} is what the way here took out of the metal (MeltPipeBlock.Grade#bleeds, the fall out
	 * of a spout). The heat of the deposit becomes the average of what was there and what came in,
	 * weighed by how much of each: a trickle of hot metal does not un-set a cold wall, a big pour does.
	 */
	public int fill(Item item, int count, int bled) {
		if (count <= 0 || !this.accepts(item)) {
			return count;
		}
		int put = Math.min(count, this.room());
		if (put <= 0) {
			return count;
		}
		boolean wasEmpty = this.amount <= 0;
		int arriving = Math.max(0, Math.min(MeltTankBlockEntity.HOT, MeltTankBlockEntity.HOT - bled));
		this.heat = (int) (((long) this.amount * this.heat + (long) put * arriving) / (this.amount + put));
		this.amount += put;
		this.metal = item;
		this.level();
		if (wasEmpty && this.level != null) {
			MeltNetwork.regroup(this.level, this);
		}
		return count - put;
	}

	/** Draws out, off the top, and gives back what it actually got. */
	public int drain(int count) {
		int got = Math.max(0, Math.min(count, this.amount));
		if (got <= 0) {
			return 0;
		}
		this.amount -= got;
		if (this.amount <= 0) {
			this.amount = 0;
			this.metal = null;
			this.heat = 0;
		}
		this.level();
		if (this.amount <= 0 && this.level != null) {
			MeltNetwork.regroup(this.level, this);
		}
		return got;
	}

	/** Melts a deposit that has set, losing part of it, and says how much was lost. */
	public int remelt() {
		if (!this.isSet()) {
			return 0;
		}
		int burnt = Math.max(1, Math.round(this.amount * MeltTankBlockEntity.REMELT_LOSS));
		burnt = Math.min(burnt, this.amount);
		this.amount -= burnt;
		this.heat = MeltTankBlockEntity.HOT;
		if (this.amount <= 0) {
			this.amount = 0;
			this.metal = null;
		}
		this.level();
		return burnt;
	}

	/** Puts heat back, as a pipe of hot metal or a fire might. */
	public void warm(int by) {
		this.setHeat(Math.min(MeltTankBlockEntity.HOT, this.heat + by));
	}

	/**
	 * One tank's tick has come round. When every tank of the deposit has had its turn, the deposit settles
	 * its heat once: what a fire beside any of its tanks gives, less what the air takes.
	 */
	void vote(Level level) {
		if (++this.votes < this.members.size()) {
			return;
		}
		this.votes = 0;
		this.settle(level);
	}

	private void settle(Level level) {
		if (this.amount <= 0) {
			this.setHeat(0);
			return;
		}
		boolean warmed = false;
		for (MeltTankBlockEntity tank : this.members) {
			if (warmedBeside(level, tank.getBlockPos())) {
				warmed = true;
				break;
			}
		}
		int was = this.heat;
		this.setHeat(Math.max(0, Math.min(MeltTankBlockEntity.HOT,
			this.heat + (warmed ? MeltTankBlockEntity.WARMS : -MeltTankBlockEntity.COOLS))));
		if (was >= MeltTankBlockEntity.SET && this.heat < MeltTankBlockEntity.SET
			&& level instanceof net.minecraft.server.level.ServerLevel server) {
			server.playSound(null, this.members.getFirst().getBlockPos(), net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH,
				net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 0.6F);
		}
	}

	/** Anything that burns beside this position: the wisp lantern above all, lava, magma, fire, a lit pot. */
	static boolean warmedBeside(Level level, BlockPos pos) {
		for (Direction side : Direction.values()) {
			BlockState beside = level.getBlockState(pos.relative(side));
			if (beside.is(dev.forja.registry.ModBlocks.FAROL_DE_PAVESA)
				|| beside.is(net.minecraft.world.level.block.Blocks.LAVA)
				|| beside.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
				|| beside.is(net.minecraft.world.level.block.Blocks.FIRE)
				|| (beside.getBlock() instanceof dev.forja.block.CrucibleBlock
					&& beside.getValue(dev.forja.block.CrucibleBlock.LIT))) {
				return true;
			}
		}
		return false;
	}

	private void setHeat(int heat) {
		if (heat == this.heat) {
			return;
		}
		this.heat = heat;
		for (MeltTankBlockEntity tank : this.members) {
			tank.mirrorHeat(heat);
		}
	}

	// ------------------------------------------------------------------ drawing it across the glass

	/** Called once when the deposit is formed: level what the tanks brought with them, and tell the comparators. */
	void settleIn() {
		this.level();
	}

	/**
	 * Writes each tank's share: the bottom layer first, every tank of a layer to the same height (what is
	 * left over going one each to the first of them), then the layer above.
	 *
	 * <p>Only a tank whose share actually moved is touched, so a pour of three ingots into a deposit of a
	 * hundred tanks sends three packets, not a hundred.
	 */
	private void level() {
		if (this.detached) {
			// A copy with no world round it: it only has its own share, which is already right.
			MeltTankBlockEntity only = this.members.getFirst();
			only.share(this.metal(), this.amount, this.heat);
			return;
		}
		int left = this.amount;
		Item shown = this.metal();
		int start = 0;
		while (start < this.members.size()) {
			int y = this.members.get(start).getBlockPos().getY();
			int end = start;
			while (end < this.members.size() && this.members.get(end).getBlockPos().getY() == y) {
				end++;
			}
			int count = end - start;
			int layer = Math.min(left, count * MeltTankBlockEntity.CAPACITY);
			left -= layer;
			int each = layer / count;
			int extra = layer % count;
			for (int i = start; i < end; i++) {
				int share = each + (i - start < extra ? 1 : 0);
				this.members.get(i).share(share > 0 ? shown : null, share, this.heat);
			}
			start = end;
		}
		this.tellComparators();
	}

	private void tellComparators() {
		int now = this.signal();
		if (now == this.signal || this.level == null) {
			return;
		}
		this.signal = now;
		for (MeltTankBlockEntity tank : this.members) {
			if (!tank.isRemoved()) {
				this.level.updateNeighbourForOutputSignal(tank.getBlockPos(), tank.getBlockState().getBlock());
			}
		}
	}
}
