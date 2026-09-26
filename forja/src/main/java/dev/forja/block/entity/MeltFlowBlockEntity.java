package dev.forja.block.entity;

import dev.forja.block.MeltPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * What is running through one block of channel, which is the only thing a pipe ever knows about itself.
 *
 * <p>A pipe still holds nothing. It does not store metal, it does not buffer, it cannot be filled and
 * emptied — all of that is still the tanks' job. What it keeps is two things so it can be <b>drawn
 * honestly</b>: which metal is going past, and when it was last seen coming.
 *
 * <p>The second number is a <b>timestamp</b>, and it is what makes a foundry readable. Anything touching
 * a tank or a pot with metal in it stamps the current tick; every other channel takes the newest stamp
 * off its neighbours and passes it on. So the colour spreads out from wherever the metal actually is,
 * one block a tick, and when the tank runs dry nothing stamps anything any more and the whole run
 * expires — the far end first, the drain running back toward the pot. You can stand at the end of a long
 * one and watch it go out.
 *
 * <p>It has to be a stamp rather than a distance. Two neighbouring blocks each counting "one more than
 * the other one" hold each other up for ever after the metal stops: a stale number cannot be refreshed
 * by passing it back and forth, but a stale <em>time</em> can only ever get staler.
 *
 * <p>It costs six block lookups every {@link #EVERY} ticks and no searching at all: no block walks the
 * network, each one only ever asks the six blocks it is touching.
 */
public class MeltFlowBlockEntity extends BlockEntity {
	/** How long a stamp stays good for. After this with nothing new coming in, the channel is dry. */
	public static final int LIVE = 40;

	/** Ticks between one block of channel asking its neighbours what is going on. */
	public static final int EVERY = 2;

	private @Nullable Item metal;
	/** The tick the metal was last seen leaving a tank or a pot, as passed along the run. */
	private long stamp = Long.MIN_VALUE;
	private int tickIn = -1;

	public MeltFlowBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.COLADA, pos, state);
	}

	/**
	 * The metal going past, or null when the run is dry and the channel should be drawn empty.
	 *
	 * <p>Plainly the field, not the field weighed against the clock. The stamp goes stale on the server
	 * and the server clears the metal when it does; the client is only ever told the answer. Working it
	 * out on both sides would mean the client deciding a channel had gone dry off a stamp nobody had
	 * sent it an update for, which is exactly the bug this comment exists to stop coming back.
	 */
	public @Nullable Item metal() {
		return this.metal;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, MeltFlowBlockEntity flow) {
		if (flow.tickIn < 0) {
			// Staggered, so a hundred blocks of channel do not all think on the same tick.
			flow.tickIn = Math.floorMod(pos.hashCode(), EVERY);
		}
		if (flow.tickIn-- > 0) {
			return;
		}
		flow.tickIn = EVERY;

		long now = level.getGameTime();
		Item found = null;
		long best = Long.MIN_VALUE;
		for (Direction side : Direction.values()) {
			BlockPos at = pos.relative(side);
			BlockEntity neighbour = level.getBlockEntity(at);
			// Something actually holding molten metal stamps the moment: the run starts here.
			if (neighbour instanceof MeltTankBlockEntity tank && tank.bankMetal() != null && !tank.isSet()) {
				best = now;
				found = tank.bankMetal();
				break;
			}
			if (neighbour instanceof CrucibleBlockEntity pot && pot.pouring() != null) {
				best = now;
				found = pot.pouring();
				break;
			}
			// Or another length of channel, passing on the newest it has seen.
			if (neighbour instanceof MeltFlowBlockEntity other && other.stamp > best) {
				best = other.stamp;
				found = other.metal;
			}
		}
		// A spout only carries metal on if it has somewhere to pour it; one over a drop to nowhere is
		// as dry as an unconnected stub, and it should look like it.
		if (state.getBlock() instanceof dev.forja.block.MeltSpoutBlock && MeltPipeBlock.landing(level, pos) == null) {
			best = Long.MIN_VALUE;
			found = null;
		}
		Item had = flow.metal;
		if (best > flow.stamp) {
			flow.stamp = best;
			flow.metal = found;
		}
		if (now - flow.stamp > LIVE) {
			// Nothing has stamped this block for a while: whatever was coming has stopped coming.
			flow.metal = null;
		}
		if (had != flow.metal) {
			flow.setChanged();
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
		}
	}

	// ------------------------------------------------------------------ saving and syncing

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.stamp = input.getLongOr("Stamp", Long.MIN_VALUE);
		String id = input.getStringOr("Metal", "");
		this.metal = id.isEmpty() ? null
			: BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(id)).orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("Stamp", this.stamp);
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
