package dev.forja.block.entity;

import java.util.List;

import dev.forja.block.FarForgeBlock;
import dev.forja.forge.Alloys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The hearth of a far forge (block/FarForgeBlock): up to {@link #HEARTH} different ingredients, a store of its own
 * fuel, and a batch every {@link #BATCH_TICKS} while there is a whole recipe in the hearth and fuel to burn.
 */
public class FarForgeBlockEntity extends BlockEntity {
	/** How many different things the hearth holds. Every far forge recipe has four ingredients or fewer. */
	public static final int HEARTH = 4;
	/** How many of each. */
	public static final int STACK = 64;
	/** How many batches of fuel it can hold. */
	public static final int MAX_FUEL = 16;
	/** How long one batch takes: ten seconds. */
	public static final int BATCH_TICKS = 200;

	private NonNullList<ItemStack> hearth = NonNullList.withSize(HEARTH, ItemStack.EMPTY);
	private int fuel;
	private int progress;

	public FarForgeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FRAGUA_LEJANA, pos, state);
	}

	private FarForgeBlock.Kind kind() {
		return this.getBlockState().getBlock() instanceof FarForgeBlock forge ? forge.kind : FarForgeBlock.Kind.ALMAS;
	}

	/** Whether this forge has any use for that item: one of its ingredients. */
	public boolean takes(ItemStack stack) {
		for (Alloys.Recipe recipe : this.kind().recipes()) {
			for (Alloys.Part part : recipe.inputs()) {
				if (stack.is(part.item().get())) {
					return true;
				}
			}
		}
		return false;
	}

	/** Puts what the hand holds into the hearth, or into the fuel; anything else is turned away with a word. */
	public void handIn(Player player, ItemStack held) {
		FarForgeBlock.Kind kind = this.kind();
		if (held.is(kind.fuel.get())) {
			int room = MAX_FUEL - this.fuel;
			if (room <= 0) {
				player.sendSystemMessage(Component.translatable("gui.forja.fragua_lejana.combustible_lleno", MAX_FUEL));
				return;
			}
			int put = Math.min(room, held.getCount());
			this.fuel += put;
			held.consume(put, player);
			this.changed();
			player.sendSystemMessage(Component.translatable("gui.forja.fragua_lejana.combustible", this.fuel, MAX_FUEL));
			return;
		}
		if (!this.takes(held)) {
			player.sendSystemMessage(Component.translatable("gui.forja.fragua_lejana.no_va." + kind.id(), held.getHoverName()));
			return;
		}
		int put = this.insert(held.copy());
		if (put <= 0) {
			player.sendSystemMessage(Component.translatable("gui.forja.fragua_lejana.hogar_lleno"));
			return;
		}
		held.consume(put, player);
		this.changed();
		this.level.playSound(null, this.worldPosition, SoundEvents.SOUL_SAND_PLACE, SoundSource.BLOCKS, 0.6F, 0.8F);
		this.report(player);
	}

	/** Puts as much of the stack into the hearth as fits; how many went in. */
	public int insert(ItemStack stack) {
		int left = stack.getCount();
		for (int slot = 0; slot < HEARTH && left > 0; slot++) {
			ItemStack here = this.hearth.get(slot);
			if (!here.isEmpty() && ItemStack.isSameItemSameComponents(here, stack)) {
				int add = Math.min(left, STACK - here.getCount());
				here.grow(add);
				left -= add;
			}
		}
		for (int slot = 0; slot < HEARTH && left > 0; slot++) {
			if (this.hearth.get(slot).isEmpty()) {
				int add = Math.min(left, STACK);
				this.hearth.set(slot, stack.copyWithCount(add));
				left -= add;
			}
		}
		return stack.getCount() - left;
	}

	/** Adds fuel without a player, for a test or a command. */
	public void addFuel(int batches) {
		this.fuel = Math.max(0, Math.min(MAX_FUEL, this.fuel + batches));
		this.changed();
	}

	public int fuel() {
		return this.fuel;
	}

	public int progress() {
		return this.progress;
	}

	/** What the hearth holds, for the report and for tests. */
	public List<ItemStack> hearth() {
		return this.hearth.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
	}

	/** Everything in the hearth back to the player, or onto the floor if they have no room. */
	public void handOut(Player player) {
		boolean any = false;
		for (int slot = 0; slot < HEARTH; slot++) {
			ItemStack stack = this.hearth.get(slot);
			if (!stack.isEmpty()) {
				any = true;
				if (!player.getInventory().add(stack)) {
					player.drop(stack, false);
				}
				this.hearth.set(slot, ItemStack.EMPTY);
			}
		}
		this.progress = 0;
		this.changed();
		player.sendSystemMessage(Component.translatable(any ? "gui.forja.fragua_lejana.devuelve" : "gui.forja.fragua_lejana.vacia"));
	}

	/** Tells the player what is in the hearth, how much fuel is left, and what it is making or waiting for. */
	public void report(Player player) {
		FarForgeBlock.Kind kind = this.kind();
		MutableComponent held = Component.empty();
		boolean first = true;
		for (ItemStack stack : this.hearth) {
			if (stack.isEmpty()) {
				continue;
			}
			if (!first) {
				held.append(", ");
			}
			held.append(stack.getCount() + " ").append(stack.getHoverName());
			first = false;
		}
		player.sendSystemMessage(Component.translatable("gui.forja.fragua_lejana.hogar",
			first ? Component.translatable("gui.forja.fragua_lejana.nada") : held, this.fuel, new ItemStack(kind.fuel.get()).getHoverName()));
		if (this.level != null && this.level.dimension() != kind.dimension) {
			player.sendSystemMessage(Component.translatable("gui.forja.fragua_lejana.fuera." + kind.id()));
			return;
		}
		Alloys.Recipe ready = this.ready();
		if (ready != null) {
			player.sendSystemMessage(this.fuel > 0
				? Component.translatable("gui.forja.fragua_lejana.funde", ready.displayName(), Math.max(0, (BATCH_TICKS - this.progress) / 20))
				: Component.translatable("gui.forja.fragua_lejana.sin_combustible", new ItemStack(kind.fuel.get()).getHoverName()));
			return;
		}
		// Nothing whole: say what the closest recipe still wants.
		Alloys.Recipe closest = null;
		int best = -1;
		for (Alloys.Recipe recipe : kind.recipes()) {
			int have = 0;
			for (Alloys.Part part : recipe.inputs()) {
				have += Math.min(part.count(), this.count(part.item().get()));
			}
			if (have > best) {
				best = have;
				closest = recipe;
			}
		}
		if (closest == null) {
			return;
		}
		MutableComponent missing = Component.empty();
		first = true;
		for (Alloys.Part part : closest.inputs()) {
			int lack = part.count() - this.count(part.item().get());
			if (lack > 0) {
				if (!first) {
					missing.append(", ");
				}
				missing.append(lack + " ").append(new ItemStack(part.item().get()).getHoverName());
				first = false;
			}
		}
		player.sendSystemMessage(Component.translatable("gui.forja.fragua_lejana.falta", closest.displayName(), missing));
	}

	private int count(Item item) {
		int found = 0;
		for (ItemStack stack : this.hearth) {
			if (stack.is(item)) {
				found += stack.getCount();
			}
		}
		return found;
	}

	/** The first of this forge's recipes the hearth holds whole, or null. */
	public Alloys.@Nullable Recipe ready() {
		for (Alloys.Recipe recipe : this.kind().recipes()) {
			boolean whole = true;
			for (Alloys.Part part : recipe.inputs()) {
				if (this.count(part.item().get()) < part.count()) {
					whole = false;
					break;
				}
			}
			if (whole) {
				return recipe;
			}
		}
		return null;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, FarForgeBlockEntity forge) {
		if (!(state.getBlock() instanceof FarForgeBlock block) || !block.burns(level, state)) {
			forge.progress = 0;
			return;
		}
		Alloys.Recipe recipe = forge.fuel > 0 ? forge.ready() : null;
		if (recipe == null) {
			if (forge.progress != 0) {
				forge.progress = 0;
				forge.changed();
			}
			return;
		}
		forge.progress++;
		if (level instanceof ServerLevel server && forge.progress % 10 == 0) {
			server.sendParticles(block.kind == FarForgeBlock.Kind.ALMAS ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.REVERSE_PORTAL,
				pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.02);
		}
		if (forge.progress < BATCH_TICKS) {
			return;
		}
		forge.progress = 0;
		forge.fuel--;
		for (Alloys.Part part : recipe.inputs()) {
			forge.take(part.item().get(), part.count());
		}
		forge.pour(level, pos, recipe.result());
		level.playSound(null, pos, block.kind == FarForgeBlock.Kind.ALMAS ? SoundEvents.SOUL_ESCAPE.value() : SoundEvents.ENDER_EYE_DEATH,
			SoundSource.BLOCKS, 1.0F, 0.9F);
		level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8F, 0.7F);
		forge.changed();
	}

	private void take(Item item, int count) {
		int left = count;
		for (int slot = 0; slot < HEARTH && left > 0; slot++) {
			ItemStack stack = this.hearth.get(slot);
			if (stack.is(item)) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
				if (stack.isEmpty()) {
					this.hearth.set(slot, ItemStack.EMPTY);
				}
			}
		}
	}

	/** The batch goes into a hopper or chest right under the forge if there is one, and onto the forge if not. */
	private void pour(Level level, BlockPos pos, ItemStack made) {
		ItemStack left = made;
		if (level.getBlockEntity(pos.below()) instanceof Container below) {
			left = HopperBlockEntity.addItem(null, below, made, Direction.UP);
		}
		if (!left.isEmpty()) {
			ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, left, 0.0, 0.15, 0.0);
			drop.setDefaultPickUpDelay();
			level.addFreshEntity(drop);
		}
	}

	private void changed() {
		this.setChanged();
	}

	/** Breaking it (in creative; nothing else can) spills the hearth. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null) {
			for (ItemStack stack : this.hearth) {
				if (!stack.isEmpty()) {
					net.minecraft.world.Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
				}
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.hearth = NonNullList.withSize(HEARTH, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.hearth);
		this.fuel = Math.max(0, Math.min(MAX_FUEL, input.getIntOr("Fuel", 0)));
		this.progress = Math.max(0, input.getIntOr("Progress", 0));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.hearth);
		output.putInt("Fuel", this.fuel);
		output.putInt("Progress", this.progress);
	}
}
