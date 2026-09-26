package dev.forja.item;

import dev.forja.ForjaAdvancements;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The forge guide: using it opens a book with every table, recipe, part, material, trait and upgrade. */
public class GuideBookItem extends Item {
	/** Set by the client initializer; the item itself never touches client classes. */
	public static Runnable opener = () -> {
	};

	public GuideBookItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			opener.run();
		} else {
			ForjaAdvancements.award(player, "guia");
		}
		return InteractionResult.SUCCESS;
	}
}
