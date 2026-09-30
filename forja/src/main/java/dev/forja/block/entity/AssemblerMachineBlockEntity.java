package dev.forja.block.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.forja.block.AssemblerMachineBlock;
import dev.forja.forge.Alloys;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.item.CastingFrameItem;
import dev.forja.item.PartItem;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * La montadora: the forge star without the smith.
 *
 * <p>Andy, 2026-09-28, asked for "alguna forma para automatizar todo el sistema de forja" and settled it
 * as "una máquina que monta herramientas con calidad normal (el golpe perfecto sigue siendo del
 * jugador)". So this does exactly one of the things the forge table does — the FORGE press, parts on the
 * star into a new piece — by exactly the same rule (ForgeType's multiset, then Assembler.evaluate, which
 * is the one function the star calls too), and nothing else: no upgrades, no repairs, no swaps, no gifts,
 * no techniques. Those stay at the table, in a smith's hands.
 *
 * <p>What it gives up is the hammer. Every piece comes out as a decent press would leave it
 * ({@link #QUALITY}): never perfect, so never on the road to a masterpiece, unsigned because nobody
 * forged it, and with the potential of a plain press by a smith of no level at no particular table.
 *
 * <p>The inventory is the star's: five points ({@code 0..4}) that take parts and nothing else, the centre
 * ({@link #SLOT_FRAME}) where a casting frame can lie to say which piece to build, and the finished piece
 * ({@link #SLOT_OUTPUT}). Parts go in from the top and the sides, the piece comes out of the bottom, on the
 * foundry's own convention. Heat is the forge's (Alloys.heatAt: the fire under it, or a heat pipe when
 * part B brings them) or a fire beside it, because the bottom is where the hopper goes.
 */
public class AssemblerMachineBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
	/** The star's five points. */
	public static final int POINTS = 5;
	/** The centre of the star: a casting frame here names the piece to build. */
	public static final int SLOT_FRAME = 5;
	public static final int SLOT_OUTPUT = 6;
	public static final int SIZE = 7;

	/**
	 * The press the machine gives every piece: 1, a decent one. The table's scale is 0 a miss, 1 decent,
	 * 2 perfect, and 2 is the player's alone.
	 */
	public static final int QUALITY = 1;

	/** Ticks one piece takes over a campfire, a magma block, and lava or hotter. Hotter is quicker. */
	public static final int WORK_TEMPLADA = 100;
	public static final int WORK_CALIENTE = 60;
	public static final int WORK_FUNDIDA = 40;

	/** The least heat it works with: a campfire's. Cold, it waits. */
	public static final Alloys.Heat NEEDS = Alloys.Heat.TEMPLADA;

	/** How often the heat is looked at again: it is a block read, not worth doing every tick. */
	private static final int HEAT_EVERY = 10;

	/** Nothing on the star. */
	public static final int JOB_EMPTY = 0;
	/** Assembling. */
	public static final int JOB_WORKING = 1;
	/** A whole set, and no fire. */
	public static final int JOB_COLD = 2;
	/** A whole set, and the last piece is still in the output. */
	public static final int JOB_FULL = 3;
	/** Parts that are on their way to something, and something is missing. */
	public static final int JOB_MISSING = 4;
	/** Parts that make nothing at all. */
	public static final int JOB_NOTHING = 5;
	/** A set that makes barding: that is the saddlery's, and a machine does not change that. */
	public static final int JOB_BEYOND = 6;
	/** Internal: the plan is complete. The job it becomes depends on the heat and the output. */
	public static final int JOB_READY = 7;

	public static final int DATA_PROGRESS = 0;
	public static final int DATA_WORK = 1;
	public static final int DATA_HEAT = 2;
	public static final int DATA_JOB = 3;
	public static final int DATA_SIZE = 4;

	private static final int[] INPUTS = {0, 1, 2, 3, 4};
	private static final int[] OUTPUT = {SLOT_OUTPUT};

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int progress;
	private Alloys.Heat heat = Alloys.Heat.FRIA;
	private int heatIn;
	private int job = JOB_EMPTY;
	/** Worked out again only when the contents change, never on a tick that changed nothing. */
	private Plan plan = Plan.NONE;
	private boolean dirty = true;
	/** What the comparators were last told, so they are only told again when it changes. */
	private int signal;
	/** Work done before a load, restored on the first tick once the plan it belongs to is known again. */
	private int keepProgress;

	public AssemblerMachineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MONTADORA, pos, state);
	}

	// ------------------------------------------------------------------ what it would build

	/**
	 * What the star would build, how many parts it takes off each point, and if nothing, why not.
	 *
	 * @param use how many to take off each of the five points
	 * @param type what the parts make, or were meant to make
	 * @param missing what a set on its way to something still lacks, as the star says it
	 */
	public record Plan(ItemStack result, int[] use, @Nullable ForgeType type, @Nullable List<PartType> missing, int reason) {
		public static final Plan NONE = new Plan(ItemStack.EMPTY, new int[POINTS], null, null, JOB_EMPTY);

		public boolean ready() {
			return this.reason == JOB_READY;
		}

		/** Whether two plans build the same thing out of the same parts, so work under way can go on. */
		boolean same(Plan other) {
			return this.reason == other.reason && this.type == other.type && Arrays.equals(this.use, other.use)
				&& ItemStack.isSameItemSameComponents(this.result, other.result);
		}
	}

	/**
	 * The plan for these five points and this frame. Static and pure, so the screen works out what it
	 * will make from the slots it can see, by the same code the machine uses to make it.
	 *
	 * <p>Without a frame it is the forge star exactly: one part off every point that has one, and
	 * whatever ForgeType that multiset is. With a frame the machine knows what it is building, so it looks
	 * for that piece's parts wherever they lie — which is what lets it build a greatsword out of one stack
	 * of blades, where the star would read one blade and make a sword.
	 */
	public static Plan plan(List<ItemStack> points, ItemStack frame, HolderLookup.Provider registries) {
		int[] use = new int[POINTS];
		ForgeType wanted = CastingFrameItem.typeOf(frame);
		boolean any = points.stream().anyMatch(stack -> !stack.isEmpty());
		if (!any) {
			return new Plan(ItemStack.EMPTY, use, wanted, wanted == null ? null : List.copyOf(wanted.slots), JOB_EMPTY);
		}
		if (wanted != null && !builds(wanted)) {
			return new Plan(ItemStack.EMPTY, use, wanted, null, JOB_BEYOND);
		}
		List<ItemStack> inputs = new ArrayList<>();
		if (wanted != null) {
			List<PartType> missing = new ArrayList<>();
			for (PartType needed : wanted.slots) {
				int found = -1;
				for (int i = 0; i < POINTS && i < points.size(); i++) {
					ItemStack stack = points.get(i);
					if (stack.getItem() instanceof PartItem part && part.type == needed && Assembler.loosePart(stack)
						&& stack.getCount() > use[i]) {
						found = i;
						break;
					}
				}
				if (found < 0) {
					missing.add(needed);
				} else {
					use[found]++;
					inputs.add(points.get(found).copyWithCount(1));
				}
			}
			if (!missing.isEmpty()) {
				return new Plan(ItemStack.EMPTY, new int[POINTS], wanted, missing, JOB_MISSING);
			}
		} else {
			for (int i = 0; i < POINTS && i < points.size(); i++) {
				if (!points.get(i).isEmpty()) {
					use[i] = 1;
					inputs.add(points.get(i).copyWithCount(1));
				}
			}
		}
		Assembler.Result made = Assembler.evaluate(inputs, registries);
		ForgedParts parts = made.stack().get(ModComponents.PARTS);
		if (parts == null) {
			return new Plan(ItemStack.EMPTY, new int[POINTS], wanted, made.missing(), made.missing() != null ? JOB_MISSING : JOB_NOTHING);
		}
		if (!builds(parts.type())) {
			return new Plan(ItemStack.EMPTY, new int[POINTS], parts.type(), null, JOB_BEYOND);
		}
		return new Plan(made.stack(), use, parts.type(), null, JOB_READY);
	}

	/**
	 * What the machine builds: everything the greater table does, except barding. Its recipe has a greater
	 * table in it, and the saddlery keeps the one thing that was only ever the saddlery's.
	 */
	public static boolean builds(ForgeType type) {
		return type.kind != ForgeType.Kind.MONTURA;
	}

	/** Ticks one piece takes at this heat, or 0 if it is too cold to work at all. */
	public static int work(Alloys.Heat heat) {
		if (!heat.reaches(NEEDS)) {
			return 0;
		}
		return heat.reaches(Alloys.Heat.FUNDIDA) ? WORK_FUNDIDA : heat.reaches(Alloys.Heat.CALIENTE) ? WORK_CALIENTE : WORK_TEMPLADA;
	}

	/**
	 * The heat it works with: the forge's own (heatAt — the fire under it, or a heat pipe once part B
	 * registers one) or a fire beside it, whichever is hotter.
	 */
	public static Alloys.Heat heatFor(Level level, BlockPos pos) {
		Alloys.Heat under = Alloys.heatAt(level, pos);
		Alloys.Heat beside = Alloys.heatBeside(level, pos);
		return beside.ordinal() > under.ordinal() ? beside : under;
	}

	// ------------------------------------------------------------------ the work

	public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblerMachineBlockEntity machine) {
		if (--machine.heatIn <= 0) {
			machine.heatIn = HEAT_EVERY;
			machine.heat = heatFor(level, pos);
		}
		if (machine.dirty) {
			machine.dirty = false;
			Plan next = plan(machine.points(), machine.items.get(SLOT_FRAME), level.registryAccess());
			if (machine.keepProgress > 0) {
				// Just loaded: the work done before the save belongs to the plan it was saved with.
				machine.progress = next.ready() ? machine.keepProgress : 0;
				machine.keepProgress = 0;
			} else if (!next.same(machine.plan)) {
				// A part more on a pile that was already being used changes nothing; a different piece starts over.
				machine.progress = 0;
			}
			machine.plan = next;
		}
		Plan plan = machine.plan;
		int job;
		if (!plan.ready()) {
			job = plan.reason();
		} else if (work(machine.heat) <= 0) {
			job = JOB_COLD;
		} else if (!machine.outputTakes(plan.result())) {
			job = JOB_FULL;
		} else {
			job = JOB_WORKING;
		}
		machine.job = job;
		if (job == JOB_WORKING) {
			if (++machine.progress >= work(machine.heat)) {
				// Like a press at the forge table: heat that came down a heat pipe is paid for, per piece.
				dev.forja.forge.HeatSources.Supply supply = dev.forja.forge.HeatSources.at(level, pos);
				if (supply.piped()) {
					supply.draw(level, supply.fluid().draw * dev.forja.forge.HeatFluid.FORGE_ACTION_TICKS);
				}
				machine.finish(level, pos);
			}
		} else if (job == JOB_COLD) {
			// The fire went out under a piece half made: it cools off it slowly rather than all at once.
			machine.progress = Math.max(0, machine.progress - 2);
		} else if (job != JOB_FULL) {
			machine.progress = 0;
		}
		boolean lit = machine.job == JOB_WORKING;
		if (state.getValue(AssemblerMachineBlock.LIT) != lit) {
			level.setBlock(pos, state.setValue(AssemblerMachineBlock.LIT, lit), Block.UPDATE_ALL);
		}
		int signal = machine.signal();
		if (signal != machine.signal) {
			machine.signal = signal;
			level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
		}
	}

	/** The piece comes off the star: the parts it took are spent, and it is put in the output. */
	private void finish(Level level, BlockPos pos) {
		Plan plan = this.plan;
		ItemStack made = plan.result().copy();
		for (int i = 0; i < POINTS; i++) {
			if (plan.use()[i] > 0) {
				this.items.get(i).shrink(plan.use()[i]);
			}
		}
		// A decent press, by nobody in particular, at no particular table: the floor of what a forge gives.
		made.set(ModComponents.POTENCIAL, dev.forja.forge.Potential.atForge(QUALITY, null, null, false));
		// Still hot off the star, like anything forged, so a smith waiting at the chest can quench it. Not a
		// handful of arrows: the hour they were made would keep every handful from stacking with the last.
		if (made.getMaxStackSize() == 1 && level instanceof ServerLevel server) {
			dev.forja.forge.Temple.markHot(made, server);
		}
		ItemStack out = this.items.get(SLOT_OUTPUT);
		if (out.isEmpty()) {
			this.items.set(SLOT_OUTPUT, made);
		} else {
			out.grow(made.getCount());
		}
		this.progress = 0;
		this.dirty = true;
		if (level instanceof ServerLevel server) {
			server.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.25F);
			server.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4F, 0.8F);
			server.sendParticles(dev.forja.registry.ModParticles.CHISPA, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
				10, 0.2, 0.05, 0.2, 0.25);
		}
		this.setChanged();
	}

	/** Whether the output has room for this: empty, or the same arrows with room on the pile. */
	private boolean outputTakes(ItemStack made) {
		ItemStack out = this.items.get(SLOT_OUTPUT);
		return out.isEmpty() || (ItemStack.isSameItemSameComponents(out, made) && out.getCount() + made.getCount() <= out.getMaxStackSize());
	}

	/**
	 * What a comparator reads: 15 while a finished piece waits in the output, 1 to 14 as a piece is
	 * assembled, 0 when the machine is idle. A clock for the one who wants to know when to come back.
	 */
	public int signal() {
		if (!this.items.get(SLOT_OUTPUT).isEmpty()) {
			return 15;
		}
		if (this.job == JOB_WORKING) {
			int work = Math.max(1, work(this.heat));
			return 1 + Math.min(13, this.progress * 13 / work);
		}
		return 0;
	}

	private List<ItemStack> points() {
		return this.items.subList(0, POINTS);
	}

	public int job() {
		return this.job;
	}

	public int progress() {
		return this.progress;
	}

	public Alloys.Heat heat() {
		return this.heat;
	}

	public Plan currentPlan() {
		return this.plan;
	}

	// ------------------------------------------------------------------ what goes in

	/**
	 * Whether a hopper may put this part on this point.
	 *
	 * <p>Parts only, and in an order that cannot choke the star: a part joins the pile of the very same
	 * part if there is one, and only takes an empty point if its kind of part has not already got as many
	 * points as the piece needs of it — one each, or with a frame, what the frame's piece takes (two blades
	 * for a greatsword). Without that, a hopper of cast pick heads, every one carrying a different upgrade
	 * and so none of them stacking, filled all five points with heads and the handle never got in. With a
	 * frame, a part the frame's piece does not use stays in the hopper.
	 */
	public boolean fits(int slot, ItemStack stack) {
		if (slot < 0 || slot >= POINTS || !Assembler.loosePart(stack)) {
			return false;
		}
		PartType type = ((PartItem) stack.getItem()).type.base();
		ForgeType wanted = CastingFrameItem.typeOf(this.items.get(SLOT_FRAME));
		if (wanted != null && !wanted.slots.contains(type)) {
			return false;
		}
		ItemStack there = this.items.get(slot);
		if (!there.isEmpty()) {
			return ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize();
		}
		int holding = 0;
		for (int i = 0; i < POINTS; i++) {
			ItemStack other = this.items.get(i);
			if (other.isEmpty()) {
				continue;
			}
			if (ItemStack.isSameItemSameComponents(other, stack) && other.getCount() < other.getMaxStackSize()) {
				// It has a pile to join: that point, not this one.
				return false;
			}
			if (other.getItem() instanceof PartItem part && part.type.base() == type) {
				holding++;
			}
		}
		int allowed = wanted == null ? 1 : (int) wanted.slots.stream().filter(needed -> needed == type).count();
		return holding < allowed;
	}

	// ------------------------------------------------------------------ the screen

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_PROGRESS -> AssemblerMachineBlockEntity.this.progress;
				case DATA_WORK -> work(AssemblerMachineBlockEntity.this.heat);
				case DATA_HEAT -> AssemblerMachineBlockEntity.this.heat.ordinal();
				case DATA_JOB -> AssemblerMachineBlockEntity.this.job;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA_SIZE;
		}
	};

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.forja.montadora");
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new dev.forja.menu.AssemblerMachineMenu(id, inventory, this, this.data);
	}

	// ------------------------------------------------------------------ container

	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? OUTPUT : INPUTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return side != Direction.DOWN && this.canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == SLOT_OUTPUT && side == Direction.DOWN;
	}

	/** What a hopper, or a tank at the end of a pipe, may put in: see {@link #fits}. The frame goes in by hand. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return this.fits(slot, stack);
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
	public void setChanged() {
		this.dirty = true;
		super.setChanged();
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

	/**
	 * Breaking it spills the parts, the frame and the finished piece. Done here and not in the block,
	 * whose affectNeighborsAfterRemoval only runs once this block entity is gone (the crucible and the
	 * casting table both lost what they held that way).
	 */
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
		this.progress = input.getIntOr("Progress", 0);
		this.dirty = true;
		// Loaded mid-piece: keep the work done. The plan worked out on the first tick is the same one.
		this.plan = Plan.NONE;
		this.keepProgress = this.progress;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("Progress", this.progress);
	}
}
