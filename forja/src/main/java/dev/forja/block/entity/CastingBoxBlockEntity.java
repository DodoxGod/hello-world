package dev.forja.block.entity;

import java.util.List;

import dev.forja.block.CastingBoxBlock;
import dev.forja.forge.Assembler;
import dev.forja.item.CastingMouldItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
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
 * The two jobs of a casting box, once a tick.
 *
 * <p><b>Cutting a mould.</b> A finished part in the pattern slot and refractory steel beside it: the
 * steel is poured over the part, the part is gone, and a mould of it comes out. It is the only thing in
 * the mod that deliberately destroys something you made, and it should be — a mould is worth a part
 * because from then on you never cut that part by hand again.
 *
 * <p><b>Casting.</b> A mould in the pattern slot and a tank of metal within reach: it pours the part in
 * whatever the tanks are holding, as long as the box is built of something that will stand that metal.
 *
 * <p><b>Cutting a frame.</b> The same trade one size up. A <i>finished tool</i> in the pattern slot and
 * enough steel beside it: the tool is destroyed and what comes out is a <b>frame</b> of the whole thing,
 * head and handle and binding at once. The box cannot fill one — that is what the casting tables are
 * for — but it is the only thing that can cut one.
 */
public class CastingBoxBlockEntity extends BlockEntity implements WorldlyContainer, net.minecraft.world.MenuProvider {
	public static final int SLOT_PATTERN = 0;
	public static final int SLOT_STEEL = 1;
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

	/**
	 * What a part poured cleanly is worth, as a percentage of one upgrade.
	 *
	 * <p>This is the whole reason to own a foundry. A part cut at the bench is a part; a part poured
	 * through a strainer that held, into a mould, off a table that was hot enough, comes out already
	 * <b>better than the sum of its metal</b> — and the upgrade rides up into whatever you build with
	 * it. A rough pour gets nothing at all, which is what makes the strainer worth infusing and the
	 * heat worth keeping.
	 */
	public static final int CAST_PERCENT = 15;

	private static final int[] TOP = {SLOT_PATTERN};
	private static final int[] SIDES = {SLOT_STEEL, SLOT_STRAINER};
	private static final int[] BOTTOM = {SLOT_OUTPUT};

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int progress;

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
			return bath == null ? null : new Job(dev.forja.item.StrainerItem.of(bath), null, 0, bath, false);
		}
		// A mould in the slot means casting; anything else means cutting a mould out of it.
		PartType mould = CastingMouldItem.partOf(pattern);
		if (mould != null) {
			ForgeMaterial metal = this.pourable(mould);
			if (metal == null) {
				return null;
			}
			ItemStack strainer = this.items.get(SLOT_STRAINER);
			// No strainer at all, or one that will not take this metal: the casting comes out rough.
			boolean rough = strainer.isEmpty() || !dev.forja.item.StrainerItem.survives(strainer, metal);
			ItemStack part = Assembler.createPart(mould, metal);
			if (rough) {
				part.set(ModComponents.ROUGH, true);
			} else {
				// Poured, and poured well: whatever is built with it has more room for upgrades than the
				// same thing cut at the bench (forge/Potential).
				part.set(ModComponents.COLADA, true);
				bless(part, mould);
			}
			return new Job(part, null, 0, metal, rough);
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
				? new Job(frame, assembled.type().displayName(), FRAME_COST, null, false)
				: null;
		}
		if (!(pattern.getItem() instanceof dev.forja.item.PartItem made) || !this.hasSteel(MOULD_COST)) {
			return null;
		}
		ItemStack result = CastingMouldItem.of(made.type);
		return this.fits(result) ? new Job(result, made.type.displayName(), MOULD_COST, null, false) : null;
	}

	/**
	 * Puts one upgrade on a cleanly poured part, chosen from the ones anything built with that part
	 * could actually use.
	 *
	 * <p>A part has no {@link ForgeType} of its own, so the choice is made over every type this part
	 * goes into: a blade could become a sword, a dagger or a scythe, and an upgrade that suits any of
	 * them suits the blade.
	 */
	private void bless(ItemStack part, PartType mould) {
		if (!(this.level instanceof ServerLevel server)) {
			return;
		}
		java.util.List<dev.forja.upgrade.Upgrade> possible = new java.util.ArrayList<>();
		for (dev.forja.upgrade.Upgrade upgrade : dev.forja.upgrade.Upgrade.values()) {
			for (dev.forja.forge.ForgeType type : dev.forja.forge.ForgeType.values()) {
				if (type.slots.contains(mould) && upgrade.appliesTo(type)) {
					possible.add(upgrade);
					break;
				}
			}
		}
		if (possible.isEmpty()) {
			return;
		}
		dev.forja.upgrade.Upgrade chosen = possible.get(server.getRandom().nextInt(possible.size()));
		part.set(ModComponents.UPGRADES,
			dev.forja.upgrade.Upgrades.EMPTY.with(chosen, CAST_PERCENT));
	}

	/** Whether the side slot holds at least this much refractory steel. */
	private boolean hasSteel(int needed) {
		ItemStack steel = this.items.get(SLOT_STEEL);
		return steel.getCount() >= needed && steel.is(ModItems.alloy("acero_refractario"));
	}

	/**
	 * The metal a connected tank is holding, if this box will stand it and there is enough of it.
	 *
	 * <p>The limit is the material's own durability against the tier's: a clay box cracks on anything
	 * harder than bronze, and a metal added to the mod tomorrow lands in the right tier by itself.
	 */
	private @Nullable ForgeMaterial pourable(PartType part) {
		if (this.level == null) {
			return null;
		}
		for (MeltTankBlockEntity tank : this.tanks()) {
			Item metal = tank.bankMetal();
			// Metal that has set will not pour, however much of it there is.
			if (metal == null || tank.isSet() || tank.bankAmount() < part.cost) {
				continue;
			}
			ForgeMaterial material = ForgeMaterial.fromInput(new ItemStack(metal));
			if (material == null || !part.accepts(material) || material.durability > this.tier().holds) {
				continue;
			}
			if (this.fits(Assembler.createPart(part, material))) {
				return material;
			}
		}
		return null;
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
		if (job.cutting() != null) {
			// Cutting a mould or a frame: what it was taken from is poured over and does not come back.
			this.items.get(SLOT_PATTERN).shrink(1);
			this.items.get(SLOT_STEEL).shrink(job.steel());
		} else if (job.metal() != null) {
			ItemStack pattern = this.items.get(SLOT_PATTERN);
			PartType part = CastingMouldItem.partOf(pattern);
			boolean infusing = pattern.getItem() instanceof dev.forja.item.StrainerItem;
			int spend = infusing ? BATH_COST : part == null ? 0 : part.cost;
			for (MeltTankBlockEntity tank : this.tanks()) {
				if (tank.bankMetal() != null && !tank.isSet()
					&& ForgeMaterial.fromInput(new ItemStack(tank.bankMetal())) == job.metal()
					&& tank.bankAmount() >= spend) {
					tank.drain(spend);
					break;
				}
			}
			if (infusing) {
				// The old strainer goes into the bath and does not come back out as itself.
				pattern.shrink(1);
			}
			if (job.rough()) {
				// The strainer could not take it and went with the pour.
				this.items.set(SLOT_STRAINER, ItemStack.EMPTY);
				if (level instanceof ServerLevel server) {
					server.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8F, 0.7F);
				}
			}
		}
		ItemStack out = this.items.get(SLOT_OUTPUT);
		if (out.isEmpty()) {
			this.items.set(SLOT_OUTPUT, job.result().copy());
		} else {
			out.grow(job.result().getCount());
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
	 * casting or an infusion is being <b>poured</b>, and costs metal out of the tanks.
	 */
	private record Job(ItemStack result, @Nullable Component cutting, int steel, @Nullable ForgeMaterial metal, boolean rough) {
	}

	/** What it is doing, in one line, for the screen and for Jade. */
	public Component describe() {
		Job job = this.pending();
		if (job != null) {
			if (job.cutting() != null) {
				return Component.translatable("gui.forja.caja.moldeando", job.cutting());
			}
			return job.rough()
				? Component.translatable("gui.forja.caja.basta", job.result().getHoverName())
				: Component.translatable("gui.forja.caja.colando", job.result().getHoverName());
		}
		PartType mould = CastingMouldItem.partOf(this.items.get(SLOT_PATTERN));
		if (mould != null) {
			return Component.translatable("gui.forja.caja.sin_metal");
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
				case dev.forja.menu.CastingBoxMenu.DATA_HOLDS -> CastingBoxBlockEntity.this.tier().holds;
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
		return switch (slot) {
			case SLOT_PATTERN -> CastingMouldItem.partOf(stack) != null
				|| stack.getItem() instanceof dev.forja.item.PartItem
				|| stack.getItem() instanceof dev.forja.item.StrainerItem
				// A finished tool, to be cut into a frame. A frame itself has no business in here.
				|| (stack.has(ModComponents.PARTS) && dev.forja.item.CastingFrameItem.typeOf(stack) == null);
			case SLOT_STEEL -> stack.is(ModItems.alloy("acero_refractario"));
			case SLOT_STRAINER -> stack.getItem() instanceof dev.forja.item.StrainerItem;
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
