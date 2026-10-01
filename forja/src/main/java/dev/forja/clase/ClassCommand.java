package dev.forja.clase;

import java.util.Arrays;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /forja clase ...} for testing and admins, never charging for anything: pick a class, set the level
 * or the experience, learn a talent or everything the points allow, empty the tree, take the class away, and
 * fire a skill without its wait; grant a milestone, or list them. Every sub-command takes an optional player at the end.
 */
public final class ClassCommand {
	private ClassCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("clase")
			.executes(c -> info(c, c.getSource().getPlayerOrException()))
			.then(Commands.literal("info")
				.executes(c -> info(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("jugador", EntityArgument.player()).executes(c -> info(c, EntityArgument.getPlayer(c, "jugador")))))
			.then(Commands.literal("elegir").then(Commands.argument("clase", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PlayerClass.values()).map(PlayerClass::id), b))
				.executes(c -> choose(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("jugador", EntityArgument.player()).executes(c -> choose(c, EntityArgument.getPlayer(c, "jugador"))))))
			.then(Commands.literal("nivel").then(Commands.argument("nivel", IntegerArgumentType.integer(1, ClassProgress.MAX_LEVEL))
				.executes(c -> level(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("jugador", EntityArgument.player()).executes(c -> level(c, EntityArgument.getPlayer(c, "jugador"))))))
			.then(Commands.literal("xp").then(Commands.argument("xp", IntegerArgumentType.integer(1))
				.executes(c -> xp(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("jugador", EntityArgument.player()).executes(c -> xp(c, EntityArgument.getPlayer(c, "jugador"))))))
			.then(Commands.literal("aprender").then(Commands.argument("talento", StringArgumentType.greedyString())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PlayerClass.values())
					.flatMap(clazz -> clazz.tree().nodes.stream().map(node -> node.id)).distinct(), b))
				.executes(c -> learn(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("jugador", EntityArgument.player()).executes(c -> learn(c, EntityArgument.getPlayer(c, "jugador"))))))
			.then(Commands.literal("puntos")
				.executes(c -> learnAll(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("jugador", EntityArgument.player()).executes(c -> learnAll(c, EntityArgument.getPlayer(c, "jugador")))))
			.then(Commands.literal("reiniciar")
				.executes(c -> reset(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("jugador", EntityArgument.player()).executes(c -> reset(c, EntityArgument.getPlayer(c, "jugador")))))
			.then(Commands.literal("quitar")
				.executes(c -> clear(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("jugador", EntityArgument.player()).executes(c -> clear(c, EntityArgument.getPlayer(c, "jugador")))))
			.then(Commands.literal("hito").then(Commands.argument("hito", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(ClassTree.milestones().stream().map(ClassTree.Milestone::id), b))
				.executes(c -> milestone(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("jugador", EntityArgument.player()).executes(c -> milestone(c, EntityArgument.getPlayer(c, "jugador"))))))
			.then(Commands.literal("hitos")
				.executes(c -> milestones(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("jugador", EntityArgument.player()).executes(c -> milestones(c, EntityArgument.getPlayer(c, "jugador")))))
			.then(Commands.literal("habilidad").then(Commands.argument("tecla", IntegerArgumentType.integer(1, 3))
				.executes(c -> skill(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("jugador", EntityArgument.player()).executes(c -> skill(c, EntityArgument.getPlayer(c, "jugador"))))));
	}

	private static int info(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		ClassData data = ClassProgress.data(player);
		PlayerClass clazz = data.playerClass();
		if (clazz == null) {
			c.getSource().sendSuccess(() -> Component.translatable("commands.forja.clase.ninguna", player.getDisplayName()), false);
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("commands.forja.clase.info", player.getDisplayName(), clazz.displayName(),
			data.level(), data.xp(), data.points(), data.nodes().size(), data.milestonePoints()), false);
		return 1;
	}

	private static int choose(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		PlayerClass clazz = PlayerClass.byId(StringArgumentType.getString(c, "clase"));
		if (clazz == null) {
			c.getSource().sendFailure(Component.translatable("commands.forja.clase.desconocida"));
			return 0;
		}
		ClassProgress.choose(player, clazz);
		return info(c, player);
	}

	private static boolean hasClass(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		if (ClassProgress.clazz(player) == null) {
			c.getSource().sendFailure(Component.translatable("commands.forja.clase.ninguna", player.getDisplayName()));
			return false;
		}
		return true;
	}

	private static int level(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		if (!hasClass(c, player)) {
			return 0;
		}
		ClassProgress.setLevel(player, IntegerArgumentType.getInteger(c, "nivel"));
		return info(c, player);
	}

	private static int xp(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		if (!hasClass(c, player)) {
			return 0;
		}
		ClassProgress.award(player, IntegerArgumentType.getInteger(c, "xp"));
		return info(c, player);
	}

	private static int learn(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		String id = StringArgumentType.getString(c, "talento").trim();
		ClassProgress.Refusal why = ClassProgress.check(player, id);
		if (why != ClassProgress.Refusal.NONE) {
			c.getSource().sendFailure(Component.translatable("gui.forja.talento.no." + why.name().toLowerCase(java.util.Locale.ROOT)));
			return 0;
		}
		ClassProgress.unlock(player, id);
		return info(c, player);
	}

	/** Learns outwards from the origin, the cheapest first, while the points last. */
	private static int learnAll(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		if (!hasClass(c, player)) {
			return 0;
		}
		ClassTree.Tree tree = ClassProgress.clazz(player).tree();
		boolean learned = true;
		while (learned) {
			learned = false;
			ClassTree.Node best = null;
			for (ClassTree.Node node : tree.nodes) {
				if (ClassProgress.check(player, node.id) == ClassProgress.Refusal.NONE && (best == null || node.cost < best.cost)) {
					best = node;
				}
			}
			if (best != null) {
				ClassProgress.set(player, ClassProgress.data(player).plus(best.id));
				learned = true;
			}
		}
		return info(c, player);
	}

	private static int milestone(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		String id = StringArgumentType.getString(c, "hito");
		if (ClassTree.milestone(id) == null) {
			c.getSource().sendFailure(Component.translatable("commands.forja.clase.desconocida"));
			return 0;
		}
		ClassProgress.reach(player, id);
		return info(c, player);
	}

	private static int milestones(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		ClassData data = ClassProgress.data(player);
		for (ClassTree.Milestone milestone : ClassTree.milestones()) {
			boolean done = data.hasMilestone(milestone.id());
			c.getSource().sendSuccess(() -> Component.literal(done ? "✔ " : "· ").append(milestone.displayName())
				.append(" (+" + milestone.points() + ")").withColor(done ? 0xFF7FD34E : 0xFF9A9A9A), false);
		}
		return data.milestones().size();
	}

	private static int reset(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		if (!hasClass(c, player)) {
			return 0;
		}
		ClassProgress.resetTalents(player);
		return info(c, player);
	}

	private static int clear(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		ClassProgress.clear(player);
		return info(c, player);
	}

	private static int skill(CommandContext<CommandSourceStack> c, ServerPlayer player) throws CommandSyntaxException {
		if (!hasClass(c, player)) {
			return 0;
		}
		return ClassSkills.use(player, IntegerArgumentType.getInteger(c, "tecla"), true) ? 1 : 0;
	}
}
