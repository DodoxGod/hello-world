package dev.forja.world;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.ForgeType;
import dev.forja.forge.SmithLevel;
import dev.forja.item.UpgradeOrbItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import dev.forja.registry.ModVillagers;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Encargos: what the Forjador actually wants. Each smith asks for one exact piece, down to the
 * material of every part and an upgrade already filled, and pays for it like the piece cost you
 * something: emeralds, a strong upgrade orb and a template. The order changes with the day, so the
 * same villager is worth coming back to.
 *
 * <p>Crouch and use on a Forjador to hear the order, and again holding the piece to hand it in.
 */
public final class Commissions {
	/** The materials an order can ask for: the ones that cost you a trip, not the ones in your pocket. */
	private static final ForgeMaterial[] WANTED = {
		ForgeMaterial.DIAMANTE, ForgeMaterial.NETHERITA, ForgeMaterial.ESMERALDA, ForgeMaterial.OBSIDIANA,
		ForgeMaterial.AMATISTA, ForgeMaterial.CUARZO, ForgeMaterial.PURPUR, ForgeMaterial.ECO,
		ForgeMaterial.VARA_DE_BLAZE, ForgeMaterial.PRISMARINA, ForgeMaterial.OBSIDIANA_LLORONA, ForgeMaterial.ESCAMA,
	};

	/** The kinds of piece a smith asks for. */
	private static final ForgeType[] ASKED = {
		ForgeType.DAGA, ForgeType.ESPADA, ForgeType.ESPADON, ForgeType.GUADANA, ForgeType.MANGUAL, ForgeType.GUANTELETES,
		ForgeType.PICO, ForgeType.HACHA, ForgeType.MARTILLO, ForgeType.LANZA, ForgeType.ARCO, ForgeType.ESCUDO, ForgeType.ALAS,
		ForgeType.TRIDENTE, ForgeType.CINCEL, ForgeType.BALLESTA,
	};

	/** How filled the asked-for upgrade has to be. */
	public static final int UPGRADE_PERCENT = 50;

	private Commissions() {
	}

	/** One order: the exact piece, and the upgrade it has to carry. */
	public record Commission(ForgeType type, List<ForgeMaterial> materials, Upgrade upgrade, int emeralds, Upgrade reward, int rewardPercent) {
		/** Whether this stack is what was asked for, part by part. */
		public boolean matches(ItemStack stack) {
			ForgedParts parts = stack.get(ModComponents.PARTS);
			if (parts == null || parts.type() != this.type || stack.isBroken()) {
				return false;
			}
			for (int slot = 0; slot < this.materials.size(); slot++) {
				if (parts.material(slot) != this.materials.get(slot)) {
					return false;
				}
			}
			return stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(this.upgrade) >= UPGRADE_PERCENT;
		}

		public Component describe() {
			List<Component> parts = new ArrayList<>();
			for (int slot = 0; slot < this.materials.size(); slot++) {
				PartType part = this.type.slots.get(slot);
				parts.add(Component.translatable("tooltip.forja.parte", part.displayName(), this.materials.get(slot).displayName()));
			}
			Component list = Component.empty();
			for (int i = 0; i < parts.size(); i++) {
				list = list.copy().append(i == 0 ? Component.empty() : Component.literal(", ")).append(parts.get(i));
			}
			return Component.translatable(
				"gui.forja.encargo",
				Component.translatable("item.forja." + this.type.id()),
				list,
				this.upgrade.displayName(),
				UPGRADE_PERCENT
			);
		}

		public Component describeReward() {
			return Component.translatable("gui.forja.encargo.paga", this.emeralds, this.reward.displayName(), this.rewardPercent);
		}
	}

	/** The order a given smith has today. The same villager and the same day always give the same one. */
	public static Commission of(Villager villager, long day) {
		return of(villager.getUUID(), day);
	}

	/** The same, by the smith's id, which is all the order actually depends on. */
	public static Commission of(java.util.UUID smith, long day) {
		RandomSource random = RandomSource.create(smith.hashCode() * 31L + day);
		ForgeType type = ASKED[random.nextInt(ASKED.length)];
		List<ForgeMaterial> materials = new ArrayList<>();
		for (int slot = 0; slot < type.slots.size(); slot++) {
			materials.add(WANTED[random.nextInt(WANTED.length)]);
		}
		List<Upgrade> possible = new ArrayList<>();
		for (Upgrade upgrade : Upgrade.values()) {
			if (upgrade.appliesTo(type) && !upgrade.name().startsWith("PACTO")) {
				possible.add(upgrade);
			}
		}
		Upgrade asked = possible.get(random.nextInt(possible.size()));
		Upgrade reward = possible.get(random.nextInt(possible.size()));
		// What it costs to gather is what it pays: the rarer the parts, the fatter the purse.
		int worth = 0;
		for (int slot = 0; slot < materials.size(); slot++) {
			worth += type.slots.get(slot).cost * (materials.get(slot).enchantability >= 20 ? 3 : 2);
		}
		int emeralds = Math.min(64, 12 + worth);
		return new Commission(type, materials, asked, emeralds, reward, 50 + random.nextInt(6) * 10);
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Villager villager) || !player.isShiftKeyDown()
				|| !villager.getVillagerData().profession().is(ModVillagers.FORJADOR)) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer smith) || !(level instanceof ServerLevel server)) {
				// The client only needs to know the hand went through.
				return InteractionResult.SUCCESS;
			}
			Commission commission = of(villager, server.getOverworldClockTime() / 24000L);
			ItemStack held = smith.getItemInHand(hand);
			if (commission.matches(held)) {
				deliver(server, smith, villager, commission, held);
			} else {
				smith.sendSystemMessage(commission.describe().copy().withStyle(ChatFormatting.YELLOW));
				smith.sendSystemMessage(commission.describeReward().copy().withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static void deliver(ServerLevel level, ServerPlayer player, Villager villager, Commission commission, ItemStack piece) {
		piece.shrink(1);
		give(player, new ItemStack(Items.EMERALD, commission.emeralds()));
		give(player, UpgradeOrbItem.create(commission.reward(), commission.rewardPercent()));
		give(player, new ItemStack(ModItems.PLANTILLA));
		SmithLevel.award(player, SmithLevel.XP_GIFT);
		villager.setVillagerXp(villager.getVillagerXp() + 5);
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY(1.5), villager.getZ(), 20, 0.4, 0.4, 0.4, 0.1);
		player.sendSystemMessage(Component.translatable("gui.forja.encargo.hecho").withStyle(ChatFormatting.GREEN));
		dev.forja.ForjaAdvancements.award(player, "encargo");
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/** The order of the smith nearest to a player, for the tests. */
	public static @Nullable Commission nearest(ServerLevel level, ServerPlayer player) {
		Villager villager = level.getNearestEntity(
			Villager.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().range(16.0),
			player, player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(16.0)
		);
		return villager == null ? null : of(villager, level.getOverworldClockTime() / 24000L);
	}
}
