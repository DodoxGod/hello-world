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
 * fire a skill without its wait. Every sub-command takes an optional player at the end.
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
			.then(Commands.literal("aprender").then(Commands.argument("talento", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Talent.values()).map(Talent::id), b))
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
			.then(Commands.literal("habilidad").then(Commands.argument("tecla", IntegerArgumentType.integer(1, 2))
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
			data.level(), data.xp(), data.points(), Integer.bitCount(data.mask())), false);
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
		Talent talent = Talent.byId(StringArgumentType.getString(c, "talento"));
		if (talent == null) {
			c.getSource().sendFailure(Component.translatable("commands.forja.clase.desconocida"));
			return 0;
		}
		ClassProgress.Refusal why = ClassProgress.check(player, talent);
		if (why != ClassProgress.Refusal.NONE) {
			c.getSource().sendFailure(Component.translatable("gui.forja.talento.no." + why.name().toLowerCase(java.util.Locale.ROOT)));
			return 0;
		}
		ClassProgress.unlock(player, talent);
		return info(c, player);
	}

	/** Learns down the tree, branch by branch, while the points last. */
	private static int learnAll(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		if (!hasClass(c, player)) {
			return 0;
		}
		boolean learned = true;
		while (learned) {
			learned = false;
			for (Talent talent : ClassProgress.clazz(player).talents()) {
				if (ClassProgress.check(player, talent) == ClassProgress.Refusal.NONE && ClassProgress.unlock(player, talent)) {
					learned = true;
				}
			}
		}
		return info(c, player);
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
