package dev.forja.block;

import java.util.OptionalInt;

import com.mojang.serialization.MapCodec;
import dev.forja.GuideBooks;
import dev.forja.item.GuideBookItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SelectableSlotContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Estantería del herrero: a shelf with a place for each of the guide's eight books (docs/LIBROS_GUIA.md, 3.3), to
 * keep them together. Like vanilla's chiseled bookshelf: a book goes into its own place (each book has one, in the
 * shelf's order), a click on a filled place takes that book back out, and a comparator reads how many there are.
 * The books keep nothing of their own, so the shelf keeps only which ones it holds, in its block state, and needs
 * no block entity; breaking it drops them (its loot table asks the same state).
 */
public class SmithShelfBlock extends HorizontalDirectionalBlock implements SelectableSlotContainer {
	public static final MapCodec<SmithShelfBlock> CODEC = simpleCodec(SmithShelfBlock::new);
	/** One per book of the shelf, in GuideBooks.SHELF's order. */
	public static final BooleanProperty[] BOOKS = new BooleanProperty[8];

	static {
		for (int i = 0; i < BOOKS.length; i++) {
			BOOKS[i] = BooleanProperty.create("libro_" + i);
		}
	}

	public SmithShelfBlock(Properties properties) {
		super(properties);
		BlockState state = this.stateDefinition.any().setValue(FACING, Direction.NORTH);
		for (BooleanProperty book : BOOKS) {
			state = state.setValue(book, false);
		}
		this.registerDefaultState(state);
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
		builder.add(BOOKS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public int getRows() {
		return 1;
	}

	@Override
	public int getColumns() {
		return BOOKS.length;
	}

	/** The place a book of the guide goes in, or -1 for anything else. */
	public static int placeOf(ItemStack stack) {
		if (stack.getItem() instanceof GuideBookItem guide) {
			return GuideBooks.SHELF.indexOf(guide.book);
		}
		return -1;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		int place = placeOf(stack);
		if (place < 0) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (state.getValue(BOOKS[place])) {
			// Its place is taken: the click is for taking a book out instead.
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(BOOKS[place], true), Block.UPDATE_ALL);
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		OptionalInt slot = this.getHitSlot(hit, state.getValue(FACING));
		if (slot.isEmpty() || !state.getValue(BOOKS[slot.getAsInt()])) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			Item item = GuideBooks.SHELF.get(slot.getAsInt()).item();
			level.setBlock(pos, state.setValue(BOOKS[slot.getAsInt()], false), Block.UPDATE_ALL);
			if (item != null && !player.getInventory().add(new ItemStack(item))) {
				player.drop(new ItemStack(item), false);
			}
			level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		int books = 0;
		for (BooleanProperty book : BOOKS) {
			books += state.getValue(book) ? 1 : 0;
		}
		return books;
	}
}
