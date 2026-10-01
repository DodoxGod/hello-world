package dev.forja.difficulty;

import java.util.Locale;

import dev.forja.Forja;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * The difficulty ladder (Andy, 2026-09-30: "no es para todos los jugadores"): the one place that says what each
 * level turns on. A level is Minecraft's own difficulty plus one flag of the world's, {@link #EXTREMO_RULE}, which
 * is on when the difficulty button went past Difícil (the world's vanilla difficulty is then HARD). Every system
 * asks here, never the vanilla difficulty or the config directly.
 *
 * <pre>
 *                      Pacífico  Fácil     Normal    Difícil   Extremo
 * cifras (preset)      Aprendiz  Aprendiz  Herrero   Herrero   Maestro
 * reglas de Forja      no        no        sí        sí        sí       (cerebro, avisos, turnos, anillo, capitán de reglas)
 *   en mobs de Forja   reglas    reglas    reglas    ...       ...      (los suyos piensan siempre)
 * penetración          no        no        sí        sí        sí       (presión, arma y rango del atacante)
 * redes v3             no        no        no        sí        sí       (config/forja/redes, también la del blaze)
 * redes v4             no        no        no        no        sí       (config/forja/redes_v4, donde haya)
 * red del capitán      no        no        no        no        sí       (redes_v4/red_capitan.json, si está)
 * capitán 2            no        no        no        no        sí       (visión, sucesión, protección, órdenes 2, visible)
 * entradas de puntería v4.1 no no no no sí (bloque J: aimInputs(); redes con "revision": "4.1")
 * carrera (del +35 %)  ×0,4     ×0,4      ×0,4      ×0,6      ×0,9     → +14 %, +14 %, +14 %, +21 %, +31,5 %
 * estamina fuertes     ×0,7     ×0,7      ×1        ×1        ×1       (veteranos, élites, campeones, mobs de Forja)
 * daño fuertes         ×0,7     ×0,7      ×1        ×1        ×1,25    (= el daño del preset)
 * mobs vanilla corrientes: en Fácil y Pacífico, vanilla del todo (vida, daño, IA); desde Normal, el preset
 * escalado por equipo  no        no        sí        sí        sí       (GearScore: vida, armadura y daño por tramo)
 *
 * carrera: velocidad = base × (1 + 0,35 · f) al correr; el cerco (rodeoSpeed ×2,3) igual: base × (1 + 1,3 · f)
 * </pre>
 *
 * <p>The config keeps two admin overrides, both "auto" by default: {@code nivel} forces a level for the whole
 * server whatever the button says, and {@code dificultad} forces only the preset (the multipliers of
 * {@link ForjaDifficulty}). See CombatConfig.
 */
public enum Ladder {
	//         vanilla              preset                     rules  pen    v3     v4     capNet cap2   sprint stamina gear
	PACIFICO(Difficulty.PEACEFUL, ForjaDifficulty.APRENDIZ, false, false, false, false, false, false, 0.4, 0.7, false),
	FACIL(   Difficulty.EASY,     ForjaDifficulty.APRENDIZ, false, false, false, false, false, false, 0.4, 0.7, false),
	NORMAL(  Difficulty.NORMAL,   ForjaDifficulty.HERRERO,  true,  true,  false, false, false, false, 0.4, 1.0, true),
	DIFICIL( Difficulty.HARD,     ForjaDifficulty.HERRERO,  true,  true,  true,  false, false, false, 0.6, 1.0, true),
	EXTREMO( Difficulty.HARD,     ForjaDifficulty.MAESTRO,  true,  true,  true,  true,  true,  true,  0.9, 1.0, true);

	/** The vanilla difficulty the level stands on. */
	public final Difficulty vanilla;
	/** Its multipliers (health, damage, posture, threat, cap, temperature, loot, feint), unless the config forces one. */
	private final ForjaDifficulty preset;
	/**
	 * Forja's combat rules on vanilla's monsters: the brain (TacticGoal, RuleBrain, specials), warned blows, turns, the
	 * ring, honest perception and the rules captain. Off, a vanilla monster keeps vanilla's AI; Forja's own monsters
	 * think at every level.
	 */
	public final boolean rules;
	/** Armor penetration on players: pressure, the weapon's own and the attacker's rank (Pressure). */
	public final boolean penetration;
	/** The trained networks of config/forja/redes (v1..v3.1) and the blaze's. */
	public final boolean nets;
	/** The v4 networks of config/forja/redes_v4, where there is one for the family. */
	public final boolean netsV4;
	/** The captain's network (redes_v4/red_capitan.json), when it is there; otherwise the rules captain. */
	public final boolean captainNet;
	/** Every piece of captain 2: shared vision, succession, protection, the new orders, the visible captain. */
	public final boolean captain2;
	/** The share of the sprint bonus a monster gets: +35 % × this (and the surround run's +130 % × this). */
	public final double sprint;
	/** Stamina of the strong ones (veterans, elites, champions, Forja's monsters): their posture bar and their run. */
	public final double stamina;
	/** Monsters come tougher by the tier of the nearest player's gear, and hit them harder (GearScore). */
	public final boolean gear;

	/** The world's flag: on, a HARD world is Extremo. Saved with the world (level.dat), set by the button and /forja dificultad. */
	public static final GameRule<Boolean> EXTREMO_RULE = GameRuleBuilder.forBoolean(false)
		.category(GameRuleCategory.MOBS)
		.buildAndRegister(Forja.id("extremo"));

	/** The +35 % of MobSprint, before the level's share. */
	public static final double SPRINT_BONUS = 0.35;

	private static volatile MinecraftServer server;
	/** The client's view (difficulty and synced flag) on a client connected to a server elsewhere. */
	private static volatile java.util.function.Supplier<Ladder> clientView;
	/** While the ladder itself sets the vanilla difficulty, so the hooks on vanilla's own changes leave the flag alone. */
	private static boolean setting;

	private static String overrideSource;
	private static Ladder overrideValue;
	private static String presetSource;
	private static ForjaDifficulty presetValue;

	Ladder(Difficulty vanilla, ForjaDifficulty preset, boolean rules, boolean penetration, boolean nets, boolean netsV4,
		boolean captainNet, boolean captain2, double sprint, double stamina, boolean gear) {
		this.vanilla = vanilla;
		this.preset = preset;
		this.rules = rules;
		this.penetration = penetration;
		this.nets = nets;
		this.netsV4 = netsV4;
		this.captainNet = captainNet;
		this.captain2 = captain2;
		this.sprint = sprint;
		this.stamina = stamina;
		this.gear = gear;
	}

	/** The level a world with this vanilla difficulty and flag is on. */
	public static Ladder of(Difficulty difficulty, boolean extremo) {
		return switch (difficulty) {
			case PEACEFUL -> PACIFICO;
			case EASY -> FACIL;
			case NORMAL -> NORMAL;
			case HARD -> extremo ? EXTREMO : DIFICIL;
		};
	}

	/**
	 * The level everything runs at now: the config's {@code nivel} when it forces one, else the server's world
	 * (its difficulty and {@link #EXTREMO_RULE}), else what a client was told, else Difícil.
	 */
	public static Ladder current() {
		Ladder forced = forced();
		if (forced != null) {
			return forced;
		}
		MinecraftServer running = server;
		if (running != null) {
			return world(running);
		}
		java.util.function.Supplier<Ladder> view = clientView;
		return view != null ? view.get() : DIFICIL;
	}

	/** The level of this server's world, without the config's override. */
	public static Ladder world(MinecraftServer server) {
		return of(server.getWorldData().getDifficulty(), extremo(server));
	}

	/** The world's flag. */
	public static boolean extremo(MinecraftServer server) {
		return server.getGameRules().get(EXTREMO_RULE);
	}

	/** The level the config forces ({@code nivel}), or null for the world's own. */
	public static Ladder forced() {
		String source = CombatConfig.get().nivel;
		if (source != overrideSource) {
			overrideValue = parse(source);
			overrideSource = source;
		}
		return overrideValue;
	}

	/** A level by name (pacifico, facil, normal, dificil, extremo; accents and case aside), or null. */
	public static Ladder parse(String name) {
		if (name == null) {
			return null;
		}
		String plain = java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toUpperCase(Locale.ROOT);
		for (Ladder level : values()) {
			if (level.name().equals(plain)) {
				return level;
			}
		}
		return null;
	}

	/** The multipliers this level runs with: the config's {@code dificultad} when it forces a preset, else its own. */
	public ForjaDifficulty preset() {
		String source = CombatConfig.get().dificultad;
		if (source != presetSource) {
			presetValue = ForjaDifficulty.forced(source);
			presetSource = source;
		}
		return presetValue != null ? presetValue : this.preset;
	}

	/** The level's own preset, without the config's override. */
	public ForjaDifficulty ownPreset() {
		return this.preset;
	}

	/** Whether the v4.1 aim inputs (block J, ai/Aim) are worked out at this level: Extremo only; elsewhere they read 0. */
	public boolean aimInputs() {
		return this == EXTREMO;
	}

	/** The run's speed over walking: 1 + 0,35 × sprint. */
	public double sprintMultiplier() {
		return 1.0 + SPRINT_BONUS * this.sprint;
	}

	/** The surround run's speed over walking: 1 + (rodeoSpeed − 1) × sprint. */
	public double rodeoMultiplier() {
		return 1.0 + (CombatConfig.get().rodeoSpeed - 1.0) * this.sprint;
	}

	public String key() {
		return "dificultad.forja.nivel." + this.name().toLowerCase(Locale.ROOT);
	}

	// ------------------------------------------------------------------ per mob

	/** One of the mod's own monsters (the boss and its apprentices included). */
	public static boolean forjaMob(LivingEntity entity) {
		return Forja.MOD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace());
	}

	/** A veteran, an elite, a champion, a boss or one of Forja's monsters: the ones Fácil softens. */
	public static boolean strong(LivingEntity entity) {
		return Threat.of(entity) != Threat.NORMAL || forjaMob(entity) || Bosses.isBoss(entity);
	}

	/** Whether Forja's brain and combat rules drive this monster at the current level. */
	public static boolean thinks(LivingEntity mob) {
		return current().rules || forjaMob(mob);
	}

	/** An ordinary vanilla monster on a level without Forja's rules: vanilla's own numbers for it. */
	public static boolean plainVanilla(LivingEntity mob) {
		return !current().rules && !strong(mob);
	}

	/** What a monster's blow on a player is multiplied by for the level: the preset's damage, 1 for a plain vanilla one. */
	public static double mobDamage(LivingEntity mob) {
		return plainVanilla(mob) ? 1.0 : ForjaDifficulty.current().damage;
	}

	/** The share of its stamina a monster has at this level (posture bar and run). */
	public static double stamina(LivingEntity mob) {
		return strong(mob) ? current().stamina : 1.0;
	}

	/** The player's gear tier as the level counts it: 0 where gear does not scale the monsters. */
	public static int gearTier(net.minecraft.world.entity.player.Player player) {
		return current().gear ? GearScore.tier(player) : 0;
	}

	// ------------------------------------------------------------------ setting it

	/**
	 * Puts the world on a level: its vanilla difficulty and the flag. With {@code ignoreLock} false a locked
	 * difficulty stays as it is (the button's way); the command ignores the lock, as /difficulty does. A hardcore
	 * world stays HARD (vanilla's rule): Fácil or Normal there leave it on Difícil.
	 */
	public static void apply(MinecraftServer server, Ladder level, boolean ignoreLock) {
		if (!ignoreLock && server.getWorldData().isDifficultyLocked()) {
			return;
		}
		setting = true;
		try {
			server.setDifficulty(level.vanilla, ignoreLock);
		} finally {
			setting = false;
		}
		boolean extremo = level == EXTREMO && server.getWorldData().getDifficulty() == Difficulty.HARD;
		if (extremo(server) != extremo) {
			server.getGameRules().set(EXTREMO_RULE, extremo, server);
		}
	}

	/**
	 * Vanilla changed the difficulty on its own (/difficulty, a vanilla client's button, server.properties): off the
	 * HARD it needs, the flag goes; and /difficulty hard means Difícil ({@code explicit}).
	 */
	public static void onVanillaChange(MinecraftServer server, Difficulty difficulty, boolean explicit) {
		if (setting || !extremo(server)) {
			return;
		}
		if (explicit || difficulty != Difficulty.HARD) {
			server.getGameRules().set(EXTREMO_RULE, false, server);
		}
	}

	// ------------------------------------------------------------------ network and lifecycle

	/** Server to client: the world's flag, for the difficulty button and the book. */
	public record Sync(boolean extremo) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Sync> TYPE = new CustomPacketPayload.Type<>(Forja.id("extremo"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Sync::extremo, Sync::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client to server: the difficulty button's choice, a level's ordinal. */
	public record Choose(byte level) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Choose> TYPE = new CustomPacketPayload.Type<>(Forja.id("elegir_dificultad"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, Choose::level, Choose::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Called once from Forja's initializer. */
	public static void register() {
		java.util.Objects.requireNonNull(EXTREMO_RULE);
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.STREAM_CODEC);
		ServerLifecycleEvents.SERVER_STARTING.register(started -> server = started);
		// A flag left on a world that is not HARD (its server.properties changed, say) means nothing: it goes.
		ServerLifecycleEvents.SERVER_STARTED.register(started -> onVanillaChange(started, started.getWorldData().getDifficulty(), false));
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			if (server == stopped) {
				server = null;
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> send(handler.player, extremo(joined)));
		GameRuleEvents.changeCallback(EXTREMO_RULE).register((value, changed) -> {
			for (ServerPlayer player : changed.getPlayerList().getPlayers()) {
				send(player, value);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Choose.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			MinecraftServer at = player.level().getServer();
			// Who may press vanilla's button may press this one (ServerGamePacketListenerImpl.handleChangeDifficulty).
			if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) && !at.isSingleplayerOwner(player.nameAndId())) {
				Forja.LOGGER.warn("{} intentó cambiar la dificultad sin permiso", player.getGameProfile().name());
				return;
			}
			int index = payload.level();
			if (index >= 0 && index < values().length) {
				apply(at, values()[index], false);
			}
		});
	}

	private static void send(ServerPlayer player, boolean extremo) {
		if (ServerPlayNetworking.canSend(player, Sync.TYPE)) {
			ServerPlayNetworking.send(player, new Sync(extremo));
		}
	}

	/** Set once by the client's initializer: how a client works the level out when no server runs in this game. */
	public static void clientView(java.util.function.Supplier<Ladder> view) {
		clientView = view;
	}
}
