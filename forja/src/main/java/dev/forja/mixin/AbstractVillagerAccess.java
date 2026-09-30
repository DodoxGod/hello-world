package dev.forja.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets VillagerMixin add a trade set of Forja's to a villager's offers, as the villager does with its own. */
@Mixin(AbstractVillager.class)
public interface AbstractVillagerAccess {
	@Invoker("addOffersFromTradeSet")
	void forja$addOffersFromTradeSet(ServerLevel level, MerchantOffers offers, ResourceKey<TradeSet> tradeSet);
}
