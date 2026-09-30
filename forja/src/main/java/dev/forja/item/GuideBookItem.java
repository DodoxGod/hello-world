package dev.forja.item;

import dev.forja.ForjaAdvancements;
import dev.forja.GuideBooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * One of the guide's books (GuideBooks): using it opens that book. The starter notebook keeps the id the single
 * guide had, so every guide already in a world is a notebook now.
 */
public class GuideBookItem extends Item {
	/** Set by the client initializer; the item itself never touches client classes. */
	public static java.util.function.Consumer<GuideBooks.Book> opener = book -> {
	};

	public final GuideBooks.Book book;

	public GuideBookItem(GuideBooks.Book book, Item.Properties properties) {
		super(properties);
		this.book = book;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			opener.accept(this.book);
		} else {
			ForjaAdvancements.award(player, "guia");
		}
		return InteractionResult.SUCCESS;
	}
}
