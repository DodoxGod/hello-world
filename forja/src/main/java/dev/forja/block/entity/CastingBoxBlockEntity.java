package dev.forja.block.entity;

import java.util.List;

import dev.forja.block.CastingBoxBlock;
import dev.forja.item.CastingMouldItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The preparation jobs of a casting box, once a tick.
 *
 * <p><b>Cutting a mould.</b> A finished part in the pattern slot and refractory steel beside it: the
 * steel is poured over the part, the part is gone, and a mould of it comes out. It is the only thing in
 * the mod that deliberately destroys something you made, and it should be — a mould is worth a part
 * because from then on you never cut that part by hand again.
 *
 * <p><b>Casting is not done here any more.</b> Parts used to be poured inside this menu, from the tanks,
 * with a strainer in a slot. Andy asked for the metal to have to fall — through the strainer and onto
 * the mould — so a mould now goes on a casting table like a frame does, with the strainer standing on
 * top of it (CastingTableBlockEntity). A mould left in a box from an older world just sits in the pattern
 * slot until it is taken out; so does a strainer left in the old strainer slot.
 *
 * <p><b>Bathing a strainer.</b> A strainer in the pattern slot and a harder metal in a tank it reaches:
 * the metal runs over it and it comes back out made of that metal.
 *
 * <p><b>Cutting a frame.</b> The same trade one size up. A <i>finished tool</i> in the pattern slot and
 * enough steel beside it: the tool is destroyed and what comes out is a <b>frame</b> of the whole thing,
 * head and handle and binding at once. The box cannot fill one — that is what the casting tables are
 * for — but it is the only thing that can cut one.
 */
public class CastingBoxBlockEntity extends BlockEntity implements WorldlyContainer, net.minecraft.world.MenuProvider {
	public static final int SLOT_PATTERN = 0;
	public static final int SLOT_STEEL = 1;
	/**
	 * Where the strainer used to guard the pour, back when the box cast parts. Nothing goes in any more; it
	 * is kept, and kept at this index, so a strainer left there in an older world is still there to be
	 * taken out (and the output slot is still where saves say it is).
	 */
	public static final int SLOT_STRAINER = 2;
	public static final int SLOT_OUTPUT = 3;
	public static final int SIZE = 4;

	/** How much refractory steel one mould costs. */
	public static final int MOULD_COST = 2;

	/**
	 * And what a frame costs, which is more because it is more: a mould is one hollow, a frame is every
	 * hollow of a finished tool at once.
	 */
	public static final int FRAME_COST = 6;

	/** How much metal it takes to infuse a strainer into something better. */
	public static final int BATH_COST = 8;

	private static final int[] TOP = {SLOT_PATTERN};
	private static final int[] SIDES = {SLOT_STEEL};
	private static final int[] BOTTOM = {SLOT_OUTPUT};

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int progress;

	/**
	 * What the box is doing, for the screen, which cannot see the tanks and so used to promise a casting
	 * ("Molde de cabeza de pico · 3 de metal") over a box with no metal to pour.
	 */
	public static final int JOB_NONE = 0;
	public static final int JOB_MOULD = 1;
	public static final int JOB_FRAME = 2;
	// 3 and 4 were casting cleanly and casting rough: that happens on the casting tables now.
	public static final int JOB_INFUSE = 5;
	private int job;
	/** The metal being poured, as a material ordinal, or -1. */
	private int jobMetal = -1;

	public CastingBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CAJA, pos, state);
	}

	public CastingBoxBlock.Tier tier() {
		return this.getBlockState().getBlock() instanceof CastingBoxBlock box ? box.tier : CastingBoxBlock.Tier.BARRO;
	}

	// ------------------------------------------------------------------ the work

	public static void serverTick(Level level, BlockPos pos, BlockState state, CastingBoxBlockEntity box) {
		Job job = box.pending();
		boolean working = job != null;
		box.job = job == null ? JOB_NONE
			: job.cutting() != null ? (dev.forja.item.CastingFrameItem.typeOf(job.result()) != null ? JOB_FRAME : JOB_MOULD)
			: JOB_INFUSE;
		box.jobMetal = job == null || job.metal() == null ? -1 : job.metal().ordinal();
		if (!working) {
			box.progress = 0;
		} else {
			box.progress++;
			if (box.progress >= box.tier().cook) {
				box.progress = 0;
				box.finish(level, pos, job);
			}
		}
		if (state.getValue(CastingBoxBlock.LIT) != working) {
			level.setBlock(pos, state.setValue(CastingBoxBlock.LIT, working), Block.UPDATE_ALL);
		}
	}

	/** What the box would do right now, or null if it has nothing to do. */
	private @Nullable Job pending() {
		ItemStack pattern = this.items.get(SLOT_PATTERN);
		if (pattern.isEmpty()) {
			return null;
		}
		// A strainer in the pattern slot is being infused rather than used: metal runs over it and it
		// comes back out made of that metal.
		if (pattern.getItem() instanceof dev.forja.item.StrainerItem) {
			ForgeMaterial bath = this.bathMetal(pattern);
			return bath == null ? null : new Job(dev.forja.item.StrainerItem.of(bath), null, 0, bath);
		}
		// A mould is filled on a casting table now, not in here: a mould in the slot is only waiting to be
		// taken out (an older world's box, or a smith who put it back by habit).
		if (CastingMouldItem.partOf(pattern) != null) {
			return null;
		}
		// A finished tool is not one shape but all of its shapes at once, so what comes off it is not a
		// mould but a frame, and only a casting table will ever fill one.
		dev.forja.part.ForgedParts assembled = pattern.get(ModComponents.PARTS);
		if (assembled != null && !(pattern.getItem() instanceof dev.forja.item.PartItem)) {
			if (!this.hasSteel(FRAME_COST)) {
				return null;
			}
			ItemStack frame = dev.forja.item.CastingFrameItem.of(assembled.type());
			return this.fits(frame)
				? new Job(frame, assembled.type().displayName(), FRAME_COST, null)
				: null;
		}
		if (!(pattern.getItem() instanceof dev.forja.item.PartItem made) || !this.hasSteel(MOULD_COST)) {
			return null;
		}
		ItemStack result = CastingMouldItem.of(made.type);
		return this.fits(result) ? new Job(result, made.type.displayName(), MOULD_COST, null) : null;
	}

	/** Whether the side slot holds at least this much refractory steel. */
	private boolean hasSteel(int needed) {
		ItemStack steel = this.items.get(SLOT_STEEL);
		return steel.getCount() >= needed && steel.is(ModItems.alloy("acero_refractario"));
	}

	/** The metal a connected tank is holding that would make this strainer better than it is. */
	private @Nullable ForgeMaterial bathMetal(ItemStack strainer) {
		int holds = dev.forja.item.StrainerItem.holds(strainer);
		for (MeltTankBlockEntity tank : this.tanks()) {
			Item metal = tank.bankMetal();
			if (metal == null || tank.isSet() || tank.bankAmount() < BATH_COST) {
				continue;
			}
			ForgeMaterial material = ForgeMaterial.fromInput(new ItemStack(metal));
			// Only worth doing if it comes out standing more than it does now.
			if (material == null || material.durability <= holds || material.durability > this.tier().holds) {
				continue;
			}
			if (this.fits(dev.forja.item.StrainerItem.of(material))) {
				return material;
			}
		}
		return null;
	}

	/** Every tank this box can reach: the ones it touches, and the ones down a pipe. */
	private List<MeltTankBlockEntity> tanks() {
		return this.level == null ? List.of() : MeltTankBlockEntity.reachableFrom(this.level, this.worldPosition);
	}

	private boolean fits(ItemStack result) {
		ItemStack out = this.items.get(SLOT_OUTPUT);
		if (out.isEmpty()) {
			return true;
		}
		return ItemStack.isSameItemSameComponents(out, result)
			&& out.getCount() + result.getCount() <= out.getMaxStackSize();
	}

	private void finish(Level level, BlockPos pos, Job job) {
		ItemStack result = job.result().copy();
		if (job.cutting() != null) {
			// Cutting a mould or a frame: what it was taken from is poured over and does not come back.
			this.items.get(SLOT_PATTERN).shrink(1);
			this.items.get(SLOT_STEEL).shrink(job.steel());
		} else if (job.metal() != null) {
			// Bathing a strainer: the bath comes out of the tanks, and the old strainer goes into it and
			// does not come back out as itself.
			for (MeltTankBlockEntity tank : this.tanks()) {
				if (tank.bankMetal() != null && !tank.isSet()
					&& ForgeMaterial.fromInput(new ItemStack(tank.bankMetal())) == job.metal()
					&& tank.bankAmount() >= BATH_COST) {
					tank.drain(BATH_COST);
					break;
				}
			}
			this.items.get(SLOT_PATTERN).shrink(1);
		}
		ItemStack out = this.items.get(SLOT_OUTPUT);
		if (out.isEmpty()) {
			this.items.set(SLOT_OUTPUT, result);
		} else {
			out.grow(result.getCount());
		}
		if (level instanceof ServerLevel server) {
			server.playSound(null, pos, job.cutting() != null ? SoundEvents.ANVIL_USE : SoundEvents.LAVA_EXTINGUISH,
				SoundSource.BLOCKS, 0.6F, job.cutting() != null ? 0.8F : 1.5F);
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
				pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 2, 0.2, 0.05, 0.2, 0.0);
		}
		this.setChanged();
	}

	/**
	 * What the box is about to turn out.
	 *
	 * <p>{@code cutting} is the name of whatever is being copied, and is what tells the three jobs
	 * apart: a mould or a frame is being <b>cut</b>, and costs steel and the thing it was taken from; a
	 * strainer's bath is being <b>poured</b>, and costs metal out of the tanks.
	 */
	private record Job(ItemStack result, @Nullable Component cutting, int steel, @Nullable ForgeMaterial metal) {
	}

	/** What it is doing, in one line, for the screen and for Jade. */
	public Component describe() {
		Job job = this.pending();
		if (job != null) {
			if (job.cutting() != null) {
				return Component.translatable("gui.forja.caja.moldeando", job.cutting());
			}
			return Component.translatable("gui.forja.caja.infundiendo", job.metal() == null
				? job.result().getHoverName() : job.metal().displayName());
		}
		if (CastingMouldItem.partOf(this.items.get(SLOT_PATTERN)) != null) {
			// Somebody expecting the old box: say where casting went.
			return Component.translatable("gui.forja.caja.molde_a_mesa");
		}
		return Component.translatable("gui.forja.caja.vacia", this.tier().holds == Integer.MAX_VALUE
			? Component.translatable("gui.forja.caja.todo")
			: Component.literal(String.valueOf(this.tier().holds)));
	}

	public int progressPercent() {
		return Math.round(100.0F * this.progress / this.tier().cook);
	}

	// ------------------------------------------------------------------ the screen

	private final net.minecraft.world.inventory.ContainerData data = new net.minecraft.world.inventory.ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case dev.forja.menu.CastingBoxMenu.DATA_PROGRESS -> CastingBoxBlockEntity.this.progress;
				case dev.forja.menu.CastingBoxMenu.DATA_COOK -> CastingBoxBlockEntity.this.tier().cook;
				// A container's numbers go to the client as shorts: "holds anything" was Integer.MAX_VALUE
				// and arrived on a server as -1, "holds up to -1 hardness". Capped, and read back as anything.
				case dev.forja.menu.CastingBoxMenu.DATA_HOLDS -> Math.min(Short.MAX_VALUE, CastingBoxBlockEntity.this.tier().holds);
				case dev.forja.menu.CastingBoxMenu.DATA_JOB -> CastingBoxBlockEntity.this.job;
				case dev.forja.menu.CastingBoxMenu.DATA_METAL -> CastingBoxBlockEntity.this.jobMetal;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return dev.forja.menu.CastingBoxMenu.DATA_SIZE;
		}
	};

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.forja." + this.tier().id());
	}

	@Override
	public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
		return new dev.forja.menu.CastingBoxMenu(id, inventory, this, this.data);
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

	/** Breaking the box spills what is in it (see CrucibleBlockEntity#preRemoveSideEffects for why here). */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null) {
			net.minecraft.world.Containers.dropContents(this.level, pos, this);
		}
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
		return allowed(slot, stack);
	}

	/**
	 * What each slot will take, for hoppers and for the screen alike.
	 *
	 * <p>One rule in one place because there were two: the screen's pattern slot only took moulds and
	 * parts, so the two other jobs the box has — cutting a finished tool into a frame (the guide's step 7)
	 * and infusing a strainer — could only ever be done with a hopper.
	 */
	public static boolean allowed(int slot, ItemStack stack) {
		return switch (slot) {
			case SLOT_PATTERN -> CastingMouldItem.partOf(stack) != null
				|| stack.getItem() instanceof dev.forja.item.PartItem
				|| stack.getItem() instanceof dev.forja.item.StrainerItem
				// A finished tool, to be cut into a frame. A frame itself has no business in here.
				|| (stack.has(ModComponents.PARTS) && dev.forja.item.CastingFrameItem.typeOf(stack) == null);
			case SLOT_STEEL -> stack.is(ModItems.alloy("acero_refractario"));
			// The old strainer gate takes nothing new: the strainer stands on the casting table now.
			default -> false;
		};
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.progress = input.getIntOr("Progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("Progress", this.progress);
	}
}
