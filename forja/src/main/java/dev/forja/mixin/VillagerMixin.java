package dev.forja.mixin;

import dev.forja.Forja;
import dev.forja.registry.ModVillagers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.TradeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Forjador sells book VI, El Bastión y el Herrero, from level 3, always (Andy, 2026-09-30). A level's trades are
 * drawn at random from its set, so the book comes in a set of its own, added when the Forjador reaches level 3.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {
	private static final ResourceKey<TradeSet> FORJA$BOOK = ResourceKey.create(Registries.TRADE_SET, Forja.id("forjador/libro_bastion"));

	@Inject(method = "updateTrades", at = @At("TAIL"))
	private void forja$sellTheBastionBook(ServerLevel level, CallbackInfo ci) {
		Villager villager = (Villager) (Object) this;
		var data = villager.getVillagerData();
		if (data.profession().is(ModVillagers.FORJADOR) && data.level() == 3) {
			((AbstractVillagerAccess) villager).forja$addOffersFromTradeSet(level, villager.getOffers(), FORJA$BOOK);
		}
	}
}
