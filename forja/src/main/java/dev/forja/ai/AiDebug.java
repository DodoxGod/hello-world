package dev.forja.ai;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.forja.ForjaConfig;
import dev.forja.combat.CombatConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * /forja ia: seeing and steering the brains.
 * <ul>
 *   <li>{@code ver}: the mob you look at says, over the hotbar, which brain drives it and what it decided;</li>
 *   <li>{@code modo auto|reglas|red}: networks where there are any, only rules, or networks only;</li>
 *   <li>{@code temperatura x}: how sure the networks are (1 as trained, lower is sharper);</li>
 *   <li>{@code grabar}: start or stop recording every network decision (see {@link AiRecorder});</li>
 *   <li>{@code recargar}: read the network files again;</li>
 *   <li>{@code estadisticas}: what the brains have been doing.</li>
 * </ul>
 */
public final class AiDebug {
	private static final Set<ServerPlayer> WATCHING = Collections.newSetFromMap(new WeakHashMap<>());

	private AiDebug() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("ia")
			.executes(c -> {
				c.getSource().sendSuccess(() -> Component.literal(status()), false);
				return 1;
			})
			.then(Commands.literal("ver").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				boolean on = WATCHING.add(player) || !WATCHING.remove(player);
				c.getSource().sendSuccess(() -> Component.literal(on ? "IA: mira a un mob para ver qué decide." : "IA: vista apagada."), false);
				return 1;
			}))
			.then(Commands.literal("modo").then(Commands.argument("modo", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"auto", "reglas", "red"}, b))
				.executes(c -> {
					String mode = StringArgumentType.getString(c, "modo").toLowerCase(Locale.ROOT);
					if (!mode.equals("auto") && !mode.equals("reglas") && !mode.equals("red")) {
						c.getSource().sendFailure(Component.literal("Modos: auto, reglas, red"));
						return 0;
					}
					CombatConfig.get().iaModo = mode;
					ForjaConfig.save();
					c.getSource().sendSuccess(() -> Component.literal("IA en modo " + mode), true);
					return 1;
				})))
			.then(Commands.literal("temperatura").then(Commands.argument("t", DoubleArgumentType.doubleArg(0.05, 5.0)).executes(c -> {
				double t = DoubleArgumentType.getDouble(c, "t");
				CombatConfig.get().iaTemperatura = t;
				ForjaConfig.save();
				c.getSource().sendSuccess(() -> Component.literal("Temperatura de las redes: " + t), true);
				return 1;
			})))
			.then(Commands.literal("grabar").executes(c -> {
				if (AiRecorder.recording()) {
					long lines = AiRecorder.stop();
					c.getSource().sendSuccess(() -> Component.literal("Grabación parada: " + lines + " decisiones."), false);
				} else {
					try {
						var file = AiRecorder.start();
						c.getSource().sendSuccess(() -> Component.literal("Grabando decisiones en " + file), false);
					} catch (java.io.IOException failure) {
						c.getSource().sendFailure(Component.literal("No se pudo empezar a grabar: " + failure.getMessage()));
						return 0;
					}
				}
				return 1;
			}))
			.then(Commands.literal("recargar").executes(c -> {
				MobAi.reload();
				c.getSource().sendSuccess(() -> Component.literal(status()), false);
				return 1;
			}))
			.then(Commands.literal("estadisticas").executes(c -> {
				c.getSource().sendSuccess(() -> Component.literal(AiStats.summary()), false);
				return 1;
			}));
	}

	private static String status() {
		StringBuilder out = new StringBuilder("IA: modo ").append(MobAi.mode().name().toLowerCase(Locale.ROOT))
			.append(", temperatura ").append(CombatConfig.get().iaTemperatura).append(". Redes (").append(MobAi.netFolder()).append("):");
		for (String family : MobAi.families()) {
			NetBrain net = MobAi.net(family);
			String problem = MobAi.problems().get(family);
			out.append("\n  ").append(family).append(": ")
				.append(net != null ? "cargada (" + net.ticksPerDecision + " ticks por decisión)" : problem != null ? "descartada: " + problem : "sin archivo, reglas");
		}
		return out.toString();
	}

	static void tick(ServerLevel level, long now) {
		if (WATCHING.isEmpty() || now % 5 != 0) {
			return;
		}
		for (ServerPlayer player : WATCHING) {
			if (player.level() != level) {
				continue;
			}
			Mob mob = lookedAt(player);
			if (mob == null) {
				continue;
			}
			MobMind mind = MobAi.mind(mob);
			if (mind == null) {
				continue;
			}
			Decision d = mind.decision;
			String line = mob.getType().getDescription().getString() + " · " + (mind.networked ? "red" : "reglas") + " · " + d.tactic().name()
				+ (d.tactic() == Tactic.LIBRE ? " mover=" + d.move() + (d.jump() ? " saltar" : "") + (d.use() ? " usar" : "") : "")
				+ (mind.windup > 0 ? " · avisando" : "") + (mind.draw > 0 ? " · tensando " + mind.draw : "")
				+ (mind.target == null ? " · sin objetivo" : "");
			player.sendOverlayMessage(Component.literal(line));
		}
	}

	private static Mob lookedAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(24.0));
		EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end,
			player.getBoundingBox().expandTowards(player.getViewVector(1.0F).scale(24.0)).inflate(1.0),
			entity -> entity instanceof Mob, 24.0 * 24.0);
		return hit != null && hit.getEntity() instanceof Mob mob ? mob : null;
	}
}
