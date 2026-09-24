package dev.forja.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.forja.forge.Assembler;
import dev.forja.forge.Mastery;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Upgrade;
import dev.forja.forge.ForgeType;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.UpgradeRecipes;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static dev.forja.material.ForgeMaterial.ACERO;
import static dev.forja.material.ForgeMaterial.CUARZO;
import static dev.forja.material.ForgeMaterial.CUERO;
import static dev.forja.material.ForgeMaterial.DIAMANTE;
import static dev.forja.material.ForgeMaterial.ECO;
import static dev.forja.material.ForgeMaterial.ESCAMA;
import static dev.forja.material.ForgeMaterial.ESMERALDA;
import static dev.forja.material.ForgeMaterial.HIERRO;
import static dev.forja.material.ForgeMaterial.MADERA;
import static dev.forja.material.ForgeMaterial.NETHERITA;
import static dev.forja.material.ForgeMaterial.OBSIDIANA;
import static dev.forja.material.ForgeMaterial.OBSIDIANA_LLORONA;
import static dev.forja.material.ForgeMaterial.ORO;
import static dev.forja.material.ForgeMaterial.PIEDRA;
import static dev.forja.material.ForgeMaterial.PRISMARINA;
import static dev.forja.material.ForgeMaterial.PURPUR;
import static dev.forja.material.ForgeMaterial.RESINA;
import static dev.forja.material.ForgeMaterial.VARA_DE_BLAZE;

/**
 * Admin commands: {@code /forja kit} hands out both tables, the guide, gear and upgrade ingredients;
 * {@code /forja maestria <nivel>} and {@code /forja mejora <mejora> <porcentaje>} tune the held gear for testing;
 * {@code /forja orbe <mejora> <porcentaje>} hands out an upgrade orb.
 */
public final class ForjaCommand {
	private static final String KIT_TAG = "forja_kit_noche";
	private static final String GUIDE_TAG = "forja_guia_recibida";

	private ForjaCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
			Commands.literal("forja")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("kit").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					giveKit(player);
					c.getSource().sendSuccess(() -> Component.translatable("commands.forja.kit"), false);
					return 1;
				}))
				.then(Commands.literal("evento").then(Commands.argument("evento", StringArgumentType.word())
					.suggests((c, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(dev.forja.world.WorldEvents.values()).map(dev.forja.world.WorldEvents::id), builder))
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						String name = StringArgumentType.getString(c, "evento");
						for (dev.forja.world.WorldEvents event : dev.forja.world.WorldEvents.values()) {
							if (event.id().equals(name)) {
								dev.forja.world.WorldEvents.start(player.level(), event);
								return 1;
							}
						}
						c.getSource().sendFailure(Component.translatable("commands.forja.sin_evento", name));
						return 0;
					})))
				// A row of every mob the mod has, stood still and named, for looking at rather than
				// fighting. Eleven of them are new and none of them can be seen together any other
				// way: three need a ruin, two need a lit forge, one needs a castle and one only turns
				// up in an open field at midnight.
				.then(Commands.literal("museo").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					museum(player);
					c.getSource().sendSuccess(() -> Component.literal("Los mobs, en fila."), false);
					return 1;
				}))
				// The smith's ring on demand. He is stood in front of you with his mind switched off, so he
				// does this and nothing else, as often as it is asked for: the one way to look at the
				// wave, or to practise the jump over it, without him also trying to kill you meanwhile.
				.then(Commands.literal("onda").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					if (!wave(player)) {
						c.getSource().sendFailure(Component.literal("En pacífico no hay Herrero: sube la dificultad (/difficulty easy)."));
						return 0;
					}
					c.getSource().sendSuccess(() -> Component.literal("El Herrero levanta el martillo. Salta cuando llegue el anillo."), false);
					return 1;
				}))
				.then(Commands.literal("herrero").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					c.getSource().sendSuccess(() -> dev.forja.forge.SmithLevel.describe(player), false);
					return 1;
				})
					// The level itself, so the techniques and the press window can be tried without grinding.
					.then(Commands.argument("nivel", IntegerArgumentType.integer(0, dev.forja.forge.SmithLevel.MAX_LEVEL)).executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						int level = IntegerArgumentType.getInteger(c, "nivel");
						player.setAttached(dev.forja.forge.SmithLevel.EXPERIENCE, level * level * dev.forja.forge.SmithLevel.STEP);
						c.getSource().sendSuccess(() -> dev.forja.forge.SmithLevel.describe(player), false);
						return 1;
					})))
				.then(Commands.literal("tecnica").then(Commands.argument("tecnica", StringArgumentType.word())
					.suggests((c, builder) -> SharedSuggestionProvider.suggest(
						java.util.stream.Stream.concat(
							java.util.Arrays.stream(dev.forja.forge.Technique.values()).map(dev.forja.forge.Technique::id),
							java.util.stream.Stream.of("ninguna")
						), builder))
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						String name = StringArgumentType.getString(c, "tecnica");
						if (name.equals("ninguna")) {
							player.setAttached(dev.forja.forge.Techniques.LEARNED, 0);
							c.getSource().sendSuccess(() -> Component.translatable("commands.forja.tecnicas_borradas"), false);
							return 1;
						}
						for (dev.forja.forge.Technique technique : dev.forja.forge.Technique.values()) {
							if (!technique.id().equals(name)) {
								continue;
							}
							// The command does not care about the tier being open: it is for trying things out.
							player.setAttached(dev.forja.forge.Techniques.LEARNED,
								dev.forja.forge.Techniques.mask(player) | 1 << technique.ordinal());
							c.getSource().sendSuccess(() -> Component.translatable("gui.forja.tecnica.aprendida", technique.displayName()), false);
							return 1;
						}
						c.getSource().sendFailure(Component.translatable("commands.forja.sin_tecnica", name));
						return 0;
					})))
				.then(Commands.literal("maestria").then(Commands.argument("nivel", IntegerArgumentType.integer(0, Mastery.MAX_LEVEL)).executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					ItemStack held = player.getMainHandItem();
					if (!held.has(ModComponents.PARTS)) {
						c.getSource().sendFailure(Component.translatable("commands.forja.sin_objeto"));
						return 0;
					}
					int level = IntegerArgumentType.getInteger(c, "nivel");
					Mastery.setLevel(held, level, player.level().registryAccess());
					c.getSource().sendSuccess(() -> Component.translatable("commands.forja.maestria", held.getHoverName(), level), false);
					return 1;
				})))
				.then(Commands.literal("mejora").then(Commands.argument("mejora", StringArgumentType.word())
					.suggests((c, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Upgrade.values()).map(Upgrade::id), builder))
					.then(Commands.argument("porcentaje", IntegerArgumentType.integer(0, 100)).executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						ItemStack held = player.getMainHandItem();
						ForgedParts parts = held.get(ModComponents.PARTS);
						if (parts == null) {
							c.getSource().sendFailure(Component.translatable("commands.forja.sin_objeto"));
							return 0;
						}
						String id = StringArgumentType.getString(c, "mejora");
						Upgrade upgrade = java.util.Arrays.stream(Upgrade.values()).filter(u -> u.id().equals(id)).findFirst().orElse(null);
						if (upgrade == null || !upgrade.appliesTo(parts.type())) {
							c.getSource().sendFailure(Component.translatable("commands.forja.mejora_invalida", id));
							return 0;
						}
						int percent = IntegerArgumentType.getInteger(c, "porcentaje");
						player.setItemInHand(InteractionHand.MAIN_HAND, UpgradeRecipes.upgraded(held, parts.type(), upgrade, percent, player.level().registryAccess()));
						c.getSource().sendSuccess(() -> Component.translatable("commands.forja.mejora", upgrade.displayName(), percent), false);
						return 1;
					}))))
				.then(Commands.literal("orbe").then(Commands.argument("mejora", StringArgumentType.word())
					.suggests((c, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Upgrade.values()).map(Upgrade::id), builder))
					.then(Commands.argument("porcentaje", IntegerArgumentType.integer(1, 100)).executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						String id = StringArgumentType.getString(c, "mejora");
						Upgrade upgrade = java.util.Arrays.stream(Upgrade.values()).filter(u -> u.id().equals(id)).findFirst().orElse(null);
						if (upgrade == null) {
							c.getSource().sendFailure(Component.translatable("commands.forja.mejora_invalida", id));
							return 0;
						}
						int percent = IntegerArgumentType.getInteger(c, "porcentaje");
						ItemStack orb = dev.forja.item.UpgradeOrbItem.create(upgrade, percent);
						if (!player.getInventory().add(orb)) {
							player.drop(orb, false);
						}
						c.getSource().sendSuccess(() -> Component.translatable("commands.forja.orbe", upgrade.displayName(), percent), false);
						return 1;
					}))))
		));

		// Everyone gets the guide the first time they join, so the tables explain themselves.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (player.addTag(GUIDE_TAG) && !player.getInventory().add(new ItemStack(ModItems.GUIA_DE_FORJA))) {
				player.drop(new ItemStack(ModItems.GUIA_DE_FORJA), false);
			}
		});

		// In the dev client every player gets the kit once, so new gear can be tried right away.
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
				ServerPlayer player = handler.getPlayer();
				if (player.addTag(KIT_TAG)) {
					giveKit(player);
					player.sendSystemMessage(Component.translatable("commands.forja.kit"));
				}
			});
		}
	}

	/** Every mob in the mod, in a line in front of you, holding still. */
	/**
	 * Has a mindless smith drop the hammer: the one already standing nearby if there is one, so asking
	 * again repeats the move instead of filling the field with bosses.
	 *
	 * @return false when he could not be made, which is what peaceful does to every monster
	 */
	private static boolean wave(ServerPlayer player) {
		if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return false;
		}
		dev.forja.entity.FallenSmith smith = level.getEntitiesOfClass(dev.forja.entity.FallenSmith.class,
			player.getBoundingBox().inflate(24.0), net.minecraft.world.entity.Mob::isNoAi).stream().findFirst().orElse(null);
		if (smith == null) {
			smith = dev.forja.registry.ModEntities.HERRERO_CAIDO.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			if (smith == null) {
				return false;
			}
			// Seven blocks off, inside the ring's nine, so it has to be jumped rather than watched go by.
			net.minecraft.world.phys.Vec3 forward = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
			net.minecraft.world.phys.Vec3 spot = player.position().add(forward.scale(7.0));
			smith.snapTo(spot.x, player.getY(), spot.z, net.minecraft.util.Mth.wrapDegrees(player.getYRot() + 180.0F), 0.0F);
			smith.setNoAi(true);
			level.addFreshEntity(smith);
		}
		smith.anvilWave(level);
		return true;
	}

	private static void museum(ServerPlayer player) {
		if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}
		net.minecraft.world.entity.EntityType<?>[] all = {
			dev.forja.registry.ModEntities.HERRUMBRE,
			dev.forja.registry.ModEntities.PAVESA,
			dev.forja.registry.ModEntities.ASCUA_MAYOR,
			dev.forja.registry.ModEntities.ESCORIA,
			dev.forja.registry.ModEntities.CORAZA,
			dev.forja.registry.ModEntities.AUTOMATA,
			dev.forja.registry.ModEntities.YUNQUE_ANDANTE,
			dev.forja.registry.ModEntities.PERCUTOR,
			dev.forja.registry.ModEntities.TENAZA,
			dev.forja.registry.ModEntities.CARGADOR_DE_CARBON,
			dev.forja.registry.ModEntities.TEMPLADOR,
			dev.forja.registry.ModEntities.NUCLEO_ESTELAR,
			dev.forja.registry.ModEntities.MOLDE_ROTO,
			dev.forja.registry.ModEntities.GUARDIAN_DE_CUNO,
		};
		// Four blocks apart and eight in front, facing you, on a line across your view.
		net.minecraft.world.phys.Vec3 forward = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
		net.minecraft.world.phys.Vec3 across = new net.minecraft.world.phys.Vec3(-forward.z, 0.0, forward.x);
		net.minecraft.world.phys.Vec3 start = player.position().add(forward.scale(8.0))
			.subtract(across.scale((all.length - 1) * 4.0 / 2.0));
		float facing = net.minecraft.util.Mth.wrapDegrees(player.getYRot() + 180.0F);
		for (int index = 0; index < all.length; index++) {
			net.minecraft.world.phys.Vec3 spot = start.add(across.scale(index * 4.0));
			if (!(all[index].create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND) instanceof net.minecraft.world.entity.Mob mob)) {
				continue;
			}
			mob.snapTo(spot.x, player.getY(), spot.z, facing, 0.0F);
			mob.setNoAi(true);
			mob.setPersistenceRequired();
			mob.setCustomName(all[index].getDescription());
			mob.setCustomNameVisible(true);
			level.addFreshEntity(mob);
		}
		// One core left fed, beside the empty one, because the whole point of it is that the two do
		// not look alike.
		net.minecraft.world.phys.Vec3 spot = start.add(across.scale(all.length * 4.0));
		dev.forja.entity.StarCore full = dev.forja.registry.ModEntities.NUCLEO_ESTELAR.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (full != null) {
			full.snapTo(spot.x, player.getY() + 1.0, spot.z, facing, 0.0F);
			full.setNoAi(true);
			full.setPersistenceRequired();
			full.fillForTest();
			full.setCustomName(Component.literal("Nucleo estelar (lleno)"));
			full.setCustomNameVisible(true);
			level.addFreshEntity(full);
		}
	}

	public static void giveKit(ServerPlayer player) {
		HolderLookup.Provider registries = player.level().registryAccess();
		List<ItemStack> kit = new ArrayList<>();

		kit.add(new ItemStack(ModItems.MESA_DE_PIEZAS));
		kit.add(new ItemStack(ModItems.MESA_DE_FORJA));
		kit.add(new ItemStack(ModItems.GUIA_DE_FORJA));
		kit.add(new ItemStack(ModItems.PLANTILLA, 4));
		kit.add(Assembler.create(ForgeType.ARCO, List.of(DIAMANTE, CUERO, ORO), registries));
		kit.add(Assembler.create(ForgeType.ARCO, List.of(MADERA, CUERO, MADERA), registries));
		kit.add(Assembler.create(ForgeType.ARCO, List.of(HIERRO, CUERO, VARA_DE_BLAZE), registries));
		kit.add(Assembler.create(ForgeType.BALLESTA, List.of(HIERRO, CUERO, MADERA, HIERRO), registries));
		add(kit, Items.TRIPWIRE_HOOK, 10);
		add(kit, Items.FLINT, 20);
		kit.add(Assembler.create(ForgeType.ESCUDO, List.of(MADERA, HIERRO, MADERA), registries));
		ItemStack shield = Assembler.create(ForgeType.ESCUDO, List.of(OBSIDIANA, NETHERITA, MADERA), registries);
		shield = upgrade(shield, registries, new ItemStack(Items.POINTED_DRIPSTONE, 20));
		shield = upgrade(shield, registries, new ItemStack(Items.SLIME_BALL, 5), new ItemStack(Items.ARROW, 5));
		shield = upgrade(shield, registries, new ItemStack(Items.RABBIT_FOOT, 5));
		kit.add(shield);

		kit.add(Assembler.create(ForgeType.ESPADA, List.of(ESMERALDA, MADERA, HIERRO), registries));
		kit.add(Assembler.create(ForgeType.ESPADA, List.of(CUARZO, MADERA, HIERRO), registries));
		kit.add(Assembler.create(ForgeType.ESPADA, List.of(PURPUR, MADERA, HIERRO), registries));
		kit.add(Assembler.create(ForgeType.ESPADA, List.of(HIERRO, VARA_DE_BLAZE, HIERRO), registries));
		kit.add(Assembler.create(ForgeType.PICO, List.of(PRISMARINA, MADERA, MADERA), registries));
		kit.add(Assembler.create(ForgeType.PICO, List.of(ECO, MADERA, HIERRO), registries));
		kit.add(Assembler.create(ForgeType.ESPADA, List.of(RESINA, MADERA, HIERRO), registries));
		kit.add(Assembler.create(ForgeType.CASCO, List.of(ECO, ESCAMA), registries));
		kit.add(Assembler.create(ForgeType.PECHERA, List.of(ESCAMA, CUERO), registries));
		ItemStack weeping = Assembler.create(ForgeType.PICO, List.of(OBSIDIANA_LLORONA, MADERA, MADERA), registries);
		weeping.setDamageValue(weeping.getMaxDamage() / 2);
		kit.add(weeping);
		ItemStack worn = Assembler.create(ForgeType.PICO, List.of(DIAMANTE, PIEDRA, ORO), registries);
		worn.setDamageValue(worn.getMaxDamage() * 9 / 10);
		kit.add(worn);

		kit.add(Assembler.create(ForgeType.LANZA, List.of(HIERRO, MADERA, CUERO), registries));
		kit.add(upgrade(Assembler.create(ForgeType.LANZA, List.of(NETHERITA, VARA_DE_BLAZE, ORO), registries), registries, new ItemStack(Items.SUGAR, 25)));
		kit.add(Assembler.create(ForgeType.MAZO, List.of(HIERRO, MADERA, HIERRO), registries));
		kit.add(Assembler.create(ForgeType.GUADANA, List.of(HIERRO, MADERA, CUERO), registries));
		kit.add(Assembler.create(ForgeType.CANA, List.of(VARA_DE_BLAZE, CUERO), registries));
		kit.add(Assembler.create(ForgeType.MANGUAL, List.of(HIERRO, HIERRO, MADERA), registries));
		kit.add(Assembler.create(ForgeType.GUANTELETES, List.of(CUERO, HIERRO, ORO), registries));
		kit.add(Assembler.create(ForgeType.ALAS, List.of(ORO, CUERO), registries));
		kit.add(Assembler.create(ForgeType.ALAS, List.of(OBSIDIANA, CUERO), registries));
		add(kit, Items.ELYTRA, 2);
		kit.add(Assembler.create(ForgeType.FLECHA, List.of(HIERRO, CUERO), registries).copyWithCount(16));
		kit.add(dev.forja.item.Talisman.CUARZO.create());
		kit.add(dev.forja.item.Talisman.DIAMANTE.create());
		kit.add(new ItemStack(dev.forja.registry.ModItems.CINTURON));
		kit.add(Assembler.create(ForgeType.MANGUAL, List.of(ACERO, ACERO, MADERA), registries));
		kit.add(Assembler.create(ForgeType.GANCHO, List.of(HIERRO, CUERO, MADERA), registries));
		kit.add(Assembler.create(ForgeType.BACULO, List.of(dev.forja.material.ForgeMaterial.AMATISTA, HIERRO, MADERA), registries));
		kit.add(Assembler.create(ForgeType.GRIMORIO, List.of(VARA_DE_BLAZE, NETHERITA, ORO), registries));
		kit.add(Assembler.create(ForgeType.BARDA, List.of(DIAMANTE, CUERO), registries));
		kit.add(Assembler.create(ForgeType.ARMADURA_DE_LOBO, List.of(HIERRO, CUERO), registries));
		kit.add(new ItemStack(dev.forja.registry.ModItems.MESA_DE_TALABARTERIA));
		kit.add(new ItemStack(dev.forja.registry.ModItems.JARRA, 2));
		kit.add(new ItemStack(dev.forja.registry.ModItems.HIERRO_ESTELAR, 8));
		kit.add(new ItemStack(dev.forja.registry.ModItems.alloy("damasco"), 4));
		kit.add(new ItemStack(dev.forja.registry.ModItems.CORAZON_DE_FORJA));
		kit.add(new ItemStack(dev.forja.registry.ModItems.FRAGUA_APAGADA));
		add(kit, net.minecraft.world.item.Items.NETHER_STAR, 1);
		add(kit, net.minecraft.world.item.Items.ECHO_SHARD, 1);
		add(kit, Items.NAUTILUS_SHELL, 20);
		add(kit, Items.WHEAT_SEEDS, 16);
		kit.add(new ItemStack(ModItems.LINGOTE_DE_TEMPLE, 4));
		for (dev.forja.forge.Perk perk : dev.forja.forge.Perk.values()) {
			kit.add(dev.forja.item.SealItem.create(perk));
		}
		kit.add(upgrade(Assembler.create(ForgeType.MAZO, List.of(OBSIDIANA, NETHERITA, ORO), registries), registries, new ItemStack(Items.BREEZE_ROD, 10)));

		for (ForgeType piece : List.of(ForgeType.CASCO, ForgeType.PECHERA, ForgeType.GREBAS, ForgeType.BOTAS)) {
			kit.add(Assembler.create(piece, List.of(PURPUR, CUERO), registries));
		}

		// Bow and shield upgrade ingredients, then raw materials for the new trait materials.
		add(kit, Items.ARROW, 64);
		add(kit, Items.PISTON, 16);
		add(kit, Items.FIRE_CHARGE, 8);
		add(kit, Items.SPECTRAL_ARROW, 5);
		add(kit, Items.GOLD_BLOCK, 5);
		add(kit, Items.STRING, 64);
		add(kit, Items.SLIME_BALL, 16);
		add(kit, Items.POINTED_DRIPSTONE, 32);
		add(kit, Items.RABBIT_FOOT, 8);
		add(kit, Items.EMERALD, 32);
		add(kit, Items.PRISMARINE_SHARD, 32);
		add(kit, Items.BLAZE_ROD, 16);
		add(kit, Items.QUARTZ, 32);
		add(kit, Items.PURPUR_BLOCK, 32);
		add(kit, Items.CRYING_OBSIDIAN, 32);
		add(kit, Items.ECHO_SHARD, 16);
		kit.add(dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 20));
		kit.add(dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 30));
		add(kit, Items.RESIN_BRICK, 32);
		add(kit, Items.ARMADILLO_SCUTE, 32);
		add(kit, Items.SUGAR, 32);
		add(kit, Items.HEAVY_WEIGHTED_PRESSURE_PLATE, 25);
		add(kit, Items.BREEZE_ROD, 16);
		// Ingredients for the newest upgrades.
		add(kit, Items.GLOWSTONE, 25);
		add(kit, Items.TORCH, 32);
		add(kit, Items.BOOK, 20);
		add(kit, Items.NETHER_WART, 20);
		add(kit, Items.PHANTOM_MEMBRANE, 10);
		add(kit, Items.FEATHER, 16);
		add(kit, Items.GOLD_INGOT, 6);
		add(kit, Items.STICKY_PISTON, 10);
		add(kit, Items.RABBIT_HIDE, 10);
		add(kit, Items.BREAD, 20);
		add(kit, Items.SPYGLASS, 4);
		add(kit, Items.HONEY_BOTTLE, 10);

		for (ItemStack stack : kit) {
			if (!player.getInventory().add(stack) && !stack.isEmpty()) {
				player.drop(stack, false);
			}
		}
	}

	private static ItemStack upgrade(ItemStack target, HolderLookup.Provider registries, ItemStack... ingredients) {
		List<ItemStack> slots = new ArrayList<>(List.of(ingredients));
		while (slots.size() < 3) {
			slots.add(ItemStack.EMPTY);
		}
		UpgradeRecipes.Application application = UpgradeRecipes.apply(target, slots, registries);
		return application == null ? target : application.result();
	}

	private static void add(List<ItemStack> kit, Item item, int count) {
		kit.add(new ItemStack(item, count));
	}
}
