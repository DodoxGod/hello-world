package dev.forja.client;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.ForgeStats;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Mastery;
import dev.forja.upgrade.ArmorSets;
import dev.forja.item.GuideBookItem;
import dev.forja.item.PartItem;
import dev.forja.item.TemplateItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModEntities;
import dev.forja.registry.ModMenus;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ForjaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Stat colors rank against every material combination; work those ranges out now instead of on the first tooltip.
		Thread warmup = new Thread(() -> {
			for (ForgeType type : ForgeType.values()) {
				ForgeStats.range(type, ForgeStats.Stat.DURABILIDAD);
			}
			for (PartType part : PartType.values()) {
				ForgeStats.partRange(part, ForgeStats.Stat.DURABILIDAD);
			}
		}, "Forja stat ranges");
		warmup.setDaemon(true);
		warmup.start();
		MenuScreens.register(ModMenus.FORGE, ForgeScreen::new);
		MenuScreens.register(ModMenus.FORGE_MAYOR, ForgeScreen::new);
		MenuScreens.register(ModMenus.PARTS, ForgeScreen::new);
		MenuScreens.register(ModMenus.TALABARTERIA, ForgeScreen::new);
		MenuScreens.register(ModMenus.CRISOL, CrucibleScreen::new);
		MenuScreens.register(ModMenus.CAJA, CastingBoxScreen::new);
		MenuScreens.register(ModMenus.ARMARIO, CabinetScreen::new);
		MenuScreens.register(ModMenus.EXTRACCION, ExtractionScreen::new);
		net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
			dev.forja.block.entity.ModBlockEntities.CUBA, MeltTankRenderer::new);
		net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
			dev.forja.block.entity.ModBlockEntities.CRISOL, CrucibleRenderer::new);
		net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
			dev.forja.block.entity.ModBlockEntities.MESA_DE_COLADA, CastingTableRenderer::new);
		net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
			dev.forja.block.entity.ModBlockEntities.COLADA, MeltFlowRenderer::new);
		GuideBookItem.opener = () -> Minecraft.getInstance().gui.setScreen(new GuideBookScreen());
		// The mod's own three. A particle needs its behaviour registered on the client and its sprites
		// listed in assets/forja/particles; the registry hands over the loaded sprite set here.
		SkyMood.register();

		var particles = net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance();
		particles.register(dev.forja.registry.ModParticles.CHISPA,
			sprites -> new ForjaParticles.Maker(sprites, ForjaParticles.Maker.Kind.SPARK));
		particles.register(dev.forja.registry.ModParticles.CENIZA,
			sprites -> new ForjaParticles.Maker(sprites, ForjaParticles.Maker.Kind.ASH));
		particles.register(dev.forja.registry.ModParticles.ALMA,
			sprites -> new ForjaParticles.Maker(sprites, ForjaParticles.Maker.Kind.SOUL));
		particles.register(dev.forja.registry.ModParticles.VAPOR,
			sprites -> new ForjaParticles.Maker(sprites, ForjaParticles.Maker.Kind.STEAM));
		particles.register(dev.forja.registry.ModParticles.GOTA,
			sprites -> new ForjaParticles.Maker(sprites, ForjaParticles.Maker.Kind.DRIP));

		EntityRenderers.register(ModEntities.THROWN_HEAD, ThrownHeadRenderer::new);
		EntityRenderers.register(ModEntities.PROYECTIL_MAGICO, net.minecraft.client.renderer.entity.NoopRenderer::new);
		EntityRenderers.register(ModEntities.HERRERO_CAIDO, FallenSmithRenderer::new);
		EntityRenderers.register(ModEntities.AUTOMATA, ForgeAutomatonRenderer::new);
		EntityRenderers.register(ModEntities.CORAZA, HollowArmorRenderer::new);
		EntityRenderers.register(ModEntities.PAVESA, EmberWispRenderer::new);
		EntityRenderers.register(ModEntities.HERRUMBRE, RustSwarmRenderer::new);
		EntityRenderers.register(ModEntities.ASCUA_MAYOR, GreaterEmberRenderer::new);
		EntityRenderers.register(ModEntities.ESCORIA, LivingSlagRenderer::new);
		EntityRenderers.register(ModEntities.YUNQUE_ANDANTE, WalkingAnvilRenderer::new);
		EntityRenderers.register(ModEntities.PERCUTOR, StrikerRenderer::new);
		EntityRenderers.register(ModEntities.TENAZA, TongsRenderer::new);
		EntityRenderers.register(ModEntities.CARGADOR_DE_CARBON, CoalHaulerRenderer::new);
		EntityRenderers.register(ModEntities.TEMPLADOR, QuencherRenderer::new);
		EntityRenderers.register(ModEntities.NUCLEO_ESTELAR, StarCoreRenderer::new);
		EntityRenderers.register(ModEntities.MOLDE_ROTO, BrokenMouldRenderer::new);
		EntityRenderers.register(ModEntities.GUARDIAN_DE_CUNO, CuneGuardianRenderer::new);
		EntityRenderers.register(ModEntities.ONDA, ShockwaveRenderer::new);
		ShockwaveFx.register();
		ScreenShake.register();
		GearAura.register();
		// Forged arrows fly like vanilla ones, so they use the vanilla renderer.
		EntityRenderers.register(ModEntities.FLECHA_FORJADA, context -> new net.minecraft.client.renderer.entity.TippableArrowRenderer(context));
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addPartLines(stack, lines));
		net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback.EVENT.register(
			data -> data instanceof dev.forja.item.PartsStrip strip ? new PartsStripTooltip(strip) : null);
		registerGuideKey();
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.AIR_BAR, dev.forja.Forja.id("barra_vuelo"), new FlightHud()
		);
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.AIR_BAR, dev.forja.Forja.id("barra_frenesi"), new FrenzyHud()
		);
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.BOSS_BAR, dev.forja.Forja.id("cartel_evento"), new EventBannerHud()
		);
	}

	/**
	 * A key for the guide: with the book anywhere in the bag, one press opens it. The book is where the
	 * mod explains itself, so reaching it should not mean digging through the inventory first.
	 */
	private static void registerGuideKey() {
		net.minecraft.client.KeyMapping key = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(
			new net.minecraft.client.KeyMapping("key.forja.guia", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
				org.lwjgl.glfw.GLFW.GLFW_KEY_G, net.minecraft.client.KeyMapping.Category.INVENTORY)
		);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (key.consumeClick()) {
				if (client.player == null || client.gui.screen() != null) {
					continue;
				}
				boolean carried = false;
				for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
					carried |= client.player.getInventory().getItem(slot).getItem() instanceof GuideBookItem;
				}
				if (carried) {
					client.gui.setScreen(new GuideBookScreen());
				} else {
					client.gui.hud.setOverlayMessage(Component.translatable("gui.forja.sin_libro"), false);
				}
			}
		});
	}

	/**
	 * The forged item of the same type the player has equipped where this one would go (worn armor, the
	 * off hand for shields, the main hand otherwise), when it is a different stack.
	 */
	private static ItemStack equippedCounterpart(ItemStack stack, ForgeType type) {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return ItemStack.EMPTY;
		}
		ItemStack equipped = switch (type.kind) {
			case ARMOR -> player.getItemBySlot(type.armorType.getSlot());
			case SHIELD -> player.getOffhandItem();
			default -> player.getMainHandItem();
		};
		ForgedParts parts = equipped.get(ModComponents.PARTS);
		return equipped != stack && parts != null && parts.type() == type && !ItemStack.matches(equipped, stack) ? equipped : ItemStack.EMPTY;
	}

	/** Lists the colored stats, traits and upgrades, right under the item name and its row of parts. */
	private static void addPartLines(ItemStack stack, List<Component> lines) {
		int insertAt = Math.min(1, lines.size());
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts != null) {
			List<Component> added = new ArrayList<>();
			if (stack.has(ModComponents.LEYENDA)) {
				added.add(Component.translatable("tooltip.forja.leyenda").withStyle(ChatFormatting.ITALIC).withColor(0xFFD75E));
			}
			if (stack.isBroken()) {
				added.add(Component.translatable("tooltip.forja.rota").withColor(0xFF5555));
			}
			// What it is made of is drawn, not written: item/PartsStrip puts the parts themselves in a row
			// under the name, which is where these lines used to be, one to a part.
			Upgrades stackUpgrades = stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
			ItemStack equipped = equippedCounterpart(stack, parts.type());
			java.util.Map<ForgeStats.Stat, Double> before = ForgeStats.values(equipped);
			if (!equipped.isEmpty()) {
				added.add(Component.translatable("tooltip.forja.comparado", equipped.getHoverName()).withColor(0x8A8A8A));
			}
			for (ForgeStats.Line line : ForgeStats.sheet(stack, parts).lines()) {
				if (line.stat() == ForgeStats.Stat.DURABILIDAD) {
					int max = stack.getMaxDamage();
					Component durability = Component.translatable("tooltip.forja.durabilidad", max - stack.getDamageValue(), max).withColor(ForgeStats.color(parts.type(), line));
					added.add(ForgeStats.withDelta(durability, line, before.get(line.stat())));
				} else if (ForgeStats.shown(line)) {
					added.add(ForgeStats.withDelta(ForgeStats.colored(parts.type(), line), line, before.get(line.stat())));
				}
			}
			if (dev.forja.forge.Oxidation.isCopper(stack)) {
				added.add(Component.translatable(
					dev.forja.forge.Oxidation.waxed(stack) ? "tooltip.forja.oxido.encerado" : "tooltip.forja.oxido",
					dev.forja.forge.Oxidation.stage(stack), dev.forja.forge.Oxidation.STAGES
				).withColor(0xFF8FAE8A));
			}
			added.add(dev.forja.forge.Potential.describe(stack));
			if (dev.forja.forge.Masterpiece.is(stack)) {
				added.add(Component.translatable("tooltip.forja.obra_maestra").withColor(0xFFFFF0C0));
			} else if (dev.forja.forge.Quality.perfect(stack)) {
				added.add(Component.translatable("tooltip.forja.perfecta").withColor(0xFFE8A33C));
			}
			dev.forja.forge.Temple temple = dev.forja.forge.Temple.of(stack);
			if (temple != null) {
				added.add(Component.translatable("tooltip.forja.temple", temple.displayName(), temple.description()).withColor(temple.color));
			} else if (dev.forja.forge.Temple.hot(stack, Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime())) {
				added.add(Component.translatable("tooltip.forja.caliente").withColor(0xFFE2622B));
			}
			added.add(Mastery.describe(stack).copy().withColor(0xFFC857));
			if (parts.type().kind == ForgeType.Kind.ARMOR && Minecraft.getInstance().player != null) {
				int worn = ArmorSets.count(Minecraft.getInstance().player, parts.primary());
				added.add(Component.translatable("tooltip.forja.conjunto", parts.primary().displayName(), worn).withColor(worn >= 4 ? 0x55FF55 : 0xAAAAAA));
				added.add(Component.translatable("tooltip.forja.conjunto.bono", Component.translatable("conjunto.forja." + parts.primary().getSerializedName())).withColor(worn >= 4 ? 0x55FF55 : 0x777777));
			}
			for (ForgeMaterial.Trait trait : ForgeMaterial.Trait.values()) {
				if (trait != ForgeMaterial.Trait.NONE && parts.hasTrait(trait)) {
					added.add(Component.translatable("tooltip.forja.rasgo", trait.displayName(), trait.description()).withColor(0xFFD37F));
				}
			}
			Upgrades upgrades = stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
			if (!upgrades.isEmpty()) {
				added.add(Component.translatable("tooltip.forja.mejoras").withStyle(ChatFormatting.GRAY));
				upgrades.percents().forEach((upgrade, percent) -> added.add(
					Component.translatable("tooltip.forja.mejora", upgrade.displayName(), percent, upgrade.effect(percent)).withColor(upgrade.color)
				));
			}
			dev.forja.forge.Perk perk = dev.forja.forge.Perk.of(stack);
			if (perk != null) {
				added.add(Component.translatable("tooltip.forja.don.largo", perk.displayName(), perk.description()).withColor(perk.color));
			}
			for (dev.forja.upgrade.Synergy synergy : dev.forja.upgrade.Synergy.on(stack)) {
				added.add(Component.translatable("tooltip.forja.sinergia", synergy.displayName(), synergy.description()).withColor(synergy.color));
			}
			// And the ones within reach: both upgrades are on it, one of them is still short.
			for (dev.forja.upgrade.Synergy synergy : dev.forja.upgrade.Synergy.values()) {
				int first = upgrades.percent(synergy.first);
				int second = upgrades.percent(synergy.second);
				if (synergy.active(stack) || first <= 0 || second <= 0) {
					continue;
				}
				added.add(Component.translatable("tooltip.forja.sinergia_cerca", synergy.displayName(),
					Math.min(first, second), dev.forja.upgrade.Synergy.THRESHOLD).withColor(0xFF7A7A7A));
			}
			if (dev.forja.forge.Quality.smith(stack) != null) {
				added.add(dev.forja.forge.Quality.signatureLine(stack, Minecraft.getInstance().player).copy().withColor(0xFFA9A9A9));
			}
			dev.forja.forge.ItemHistory history = dev.forja.forge.ItemHistory.of(stack);
			if (!history.isEmpty()) {
				for (Component line : history.lines()) {
					added.add(line.copy().withColor(0xFF8A8A8A));
				}
			}
			lines.addAll(insertAt, added);
			return;
		}
		// A loose part that was poured cleanly carries an upgrade, and it has to say so: an upgrade you
		// cannot see is an upgrade nobody will pour for.
		if (stack.getItem() instanceof dev.forja.item.PartItem) {
			Upgrades cast = stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
			if (!cast.isEmpty()) {
				lines.add(insertAt, Component.translatable("tooltip.forja.colada").withStyle(ChatFormatting.GRAY));
				cast.percents().forEach((upgrade, percent) -> lines.add(insertAt + 1,
					Component.translatable("tooltip.forja.mejora", upgrade.displayName(), percent,
						upgrade.effect(percent)).withColor(upgrade.color)));
			}
			if (stack.getOrDefault(ModComponents.ROUGH, false)) {
				lines.add(insertAt, Component.translatable("tooltip.forja.basta").withColor(0xFFB06030));
			}
			return;
		}
		dev.forja.forge.Perk sealed = dev.forja.item.SealItem.perk(stack);
		if (sealed != null) {
			lines.add(insertAt, Component.translatable("tooltip.forja.sello").withStyle(ChatFormatting.GRAY));
			lines.add(insertAt, sealed.description().copy().withColor(sealed.color));
			return;
		}
		dev.forja.item.Talisman talisman = dev.forja.item.Talisman.of(stack);
		if (talisman != null) {
			boolean active = Minecraft.getInstance().player != null && dev.forja.item.Talisman.carried(Minecraft.getInstance().player, talisman);
			lines.add(insertAt, Component.translatable("tooltip.forja.talisman").withStyle(ChatFormatting.DARK_GRAY));
			lines.add(insertAt, talisman.description().copy().withColor(active ? talisman.color : 0xFF808080));
			if (active) {
				lines.add(insertAt, Component.translatable("tooltip.forja.talisman.activo").withColor(0xFF55FF55));
			}
			return;
		}
		if (stack.getItem() instanceof dev.forja.item.ToolBeltItem) {
			java.util.List<ItemStack> tools = dev.forja.item.ToolBeltItem.tools(stack);
			lines.add(insertAt, Component.translatable("tooltip.forja.cinturon").withStyle(ChatFormatting.GRAY));
			if (tools.isEmpty()) {
				lines.add(insertAt + 1, Component.translatable("tooltip.forja.cinturon.vacio").withStyle(ChatFormatting.DARK_GRAY));
			} else {
				for (ItemStack tool : tools) {
					lines.add(insertAt + 1, Component.translatable("tooltip.forja.cinturon.lleva", tool.getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
				}
			}
			return;
		}
		if (stack.getItem() instanceof dev.forja.item.TemperIngotItem) {
			lines.add(insertAt, Component.translatable("tooltip.forja.temple").withStyle(ChatFormatting.GRAY));
			return;
		}
		dev.forja.upgrade.UpgradeOrb orb = dev.forja.item.UpgradeOrbItem.orb(stack);
		if (orb != null) {
			lines.add(insertAt, Component.translatable("tooltip.forja.orbe.uso").withStyle(ChatFormatting.GRAY));
			lines.add(insertAt, Component.translatable("tooltip.forja.mejora", orb.upgrade().displayName(), orb.percent(), orb.upgrade().effect(orb.percent())).withColor(orb.upgrade().color));
			return;
		}
		if (stack.getItem() instanceof TemplateItem) {
			PartType pattern = TemplateItem.pattern(stack);
			lines.add(insertAt, pattern == null
				? Component.translatable("tooltip.forja.plantilla.base").withStyle(ChatFormatting.GRAY)
				: Component.translatable("tooltip.forja.plantilla.molde", pattern.cost).withStyle(ChatFormatting.GRAY));
			return;
		}
		ForgeMaterial material = stack.get(ModComponents.MATERIAL);
		if (material != null && stack.getItem() instanceof PartItem part) {
			List<Component> added = new ArrayList<>();
			for (ForgeStats.Line line : ForgeStats.partLines(part.type, material)) {
				if (ForgeStats.shown(line)) {
					added.add(ForgeStats.colored(part.type, line));
				}
			}
			if (material.trait != ForgeMaterial.Trait.NONE) {
				added.add(Component.translatable("tooltip.forja.rasgo", material.trait.displayName(), material.trait.description()).withColor(0xFFD37F));
			}
			added.add(Component.translatable("tooltip.forja.coste", part.type.cost, material.displayName()).withStyle(ChatFormatting.DARK_GRAY));
			lines.addAll(insertAt, added);
		}
	}

}
