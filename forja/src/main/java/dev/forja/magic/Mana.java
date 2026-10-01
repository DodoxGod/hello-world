package dev.forja.magic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.forja.Forja;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.KillFlow;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.ArmorSets;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Mana, server side: the bar the staff and the tome spend, and the blades that take magic.
 *
 * <p>Andy, 2026-09-28: "¿podrías hacer una barra de maná? y mejoras que tengan que ver con el maná". Every spell
 * costs mana (a bolt little, an area more, a full charge a quarter more again) and in exchange the wait between
 * two spells is short, so a fight is a burst until the bar is empty and a moment's patience while it fills. It
 * comes back "solo, con el tiempo": slowly while spells keep coming, twice as fast once you have stopped for five
 * seconds. Andy, 2026-09-30: "se debe regenerar lentísimo si no tienes la clase" — without a magic class an empty
 * bar takes about two minutes; the Mago (x6) and the Curandero (x4), and their talents, are the ones who get it
 * back at a useful pace (ClassStat.MANA_REGEN). Kills add to it through {@link KillFlow}, at most 3 % of the bar
 * every 5 ticks, and for everyone else they are the real source. The numbers are in
 * {@link CombatConfig}; the upgrades that move them (Reserva and Flujo on armour, Meditación, Concentración,
 * Sifón and Descarga on the magic weapons, Filo arcano, Estallido arcano and Paso arcano on the blades) are in
 * upgrade/Upgrade.
 *
 * <p>Like stamina, the exact value lives here and the player carries a copy that is synced to them for the bar
 * and saved with them. Unlike stamina, it is saved: logging out with an empty bar is not a way to fill it.
 */
public final class Mana {
	/** What the bar shows and the world keeps: the mana, its max, what kills have left waiting, whether the bar was ever needed, and how many casts were refused. */
	public record State(float mana, float max, float pending, boolean awake, int denied) {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.fieldOf("mana").forGetter(State::mana),
			Codec.FLOAT.optionalFieldOf("pendiente", 0.0F).forGetter(State::pending),
			Codec.BOOL.optionalFieldOf("despierta", false).forGetter(State::awake)
		).apply(instance, (mana, pending, awake) -> new State(mana, CombatConfig.get().manaMax, pending, awake, 0)));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, State::mana,
			ByteBufCodecs.FLOAT, State::max,
			ByteBufCodecs.FLOAT, State::pending,
			ByteBufCodecs.BOOL, State::awake,
			ByteBufCodecs.VAR_INT, State::denied,
			State::new
		);
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(Forja.id("mana"), builder -> builder
		.initializer(() -> new State(CombatConfig.get().manaMax, CombatConfig.get().manaMax, 0.0F, false, 0))
		.persistent(State.CODEC)
		.copyOnDeath()
		.syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Amatista: a full set of it is a reservoir. Eco: a full set of it hums with what comes back. */
	public static final float AMETHYST_SET_MANA = 40.0F;
	public static final float ECHO_SET_REGEN = 0.3F;
	/** A refused cast says so no oftener than this, in ticks: holding the button on an empty bar is not a drum roll. */
	private static final int DENY_SOUND_EVERY = 8;

	/** Refused casts the local player's own client turned away before the server heard of them: the bar flashes for these too. */
	public static volatile int clientDenied;

	private static final Map<UUID, Data> DATA = new HashMap<>();

	private static final class Data {
		float mana;
		float pending;
		float max;
		boolean awake;
		int denied;
		long lastCast = Long.MIN_VALUE / 4;
		long lastDenySound = Long.MIN_VALUE / 4;
		/** Sifón: what the last spell cost, and whether it has already paid something back. */
		float lastCost;
		boolean siphonOpen;
		State synced;
	}

	private Mana() {
	}

	private static Data data(Player player) {
		return DATA.computeIfAbsent(player.getUUID(), id -> {
			Data data = new Data();
			State kept = player.getAttached(STATE);
			float max = maxOf(player);
			data.max = max;
			data.mana = kept == null ? max : Math.max(0.0F, Math.min(max, kept.mana()));
			data.pending = kept == null ? 0.0F : KillFlow.trim(data.mana, kept.pending(), max);
			data.awake = kept != null && kept.awake();
			return data;
		});
	}

	/** Drops the exact value; the next read takes the saved copy again. */
	public static void forget(Player player) {
		DATA.remove(player.getUUID());
	}

	/** Creative, spectators, and a world with mana switched off: nothing is spent and the bar is always full. */
	public static boolean exempt(Player player) {
		return !CombatConfig.get().mana || player.isCreative() || player.isSpectator();
	}

	/** The mana there is now: the exact value on the server, the synced one on the client. */
	public static float value(Player player) {
		if (player.level().isClientSide()) {
			State state = player.getAttached(STATE);
			return state == null ? CombatConfig.get().manaMax : state.mana();
		}
		return data(player).mana;
	}

	/** What kills have left waiting to flow in. */
	public static float pending(Player player) {
		if (player.level().isClientSide()) {
			State state = player.getAttached(STATE);
			return state == null ? 0.0F : state.pending();
		}
		return data(player).pending;
	}

	/** The bar's max: the synced one on the client, worked out from what is worn on the server. */
	public static float max(Player player) {
		if (player.level().isClientSide()) {
			State state = player.getAttached(STATE);
			return state == null ? CombatConfig.get().manaMax : state.max();
		}
		return maxOf(player);
	}

	/** The base, Reserva on every piece added up, and a full set of amethyst. */
	public static float maxOf(LivingEntity entity) {
		float max = CombatConfig.get().manaMax;
		for (EquipmentSlot slot : ARMOUR) {
			max += Upgrade.manaReserve(Upgrades.fraction(entity.getItemBySlot(slot), Upgrade.RESERVA));
		}
		if (ArmorSets.fullSet(entity) == ForgeMaterial.AMATISTA) {
			max += AMETHYST_SET_MANA;
		}
		// The class (clase/ClassEffects): a Mago's bar is deeper.
		return entity instanceof Player player ? max * (1.0F + dev.forja.clase.ClassEffects.manaMaxBonus(player)) : max;
	}

	/**
	 * What comes back this tick: the slow rate while spells keep coming, the quicker one once they have stopped,
	 * times Flujo on every piece added up, a full set of echo, and Meditación on whatever is in the hands, and all
	 * of that times the class (a Mago x6, a Curandero x4, more with their talents).
	 */
	public static float regenPerTick(Player player, long now) {
		CombatConfig cfg = CombatConfig.get();
		Data data = data(player);
		float base = now - data.lastCast >= cfg.manaIdleDelayTicks ? cfg.manaIdleRegenPerTick : cfg.manaRegenPerTick;
		return base * regenFactor(player);
	}

	/** How much faster than bare mana comes back for this player, 1 being not at all. */
	public static float regenFactor(LivingEntity entity) {
		float factor = 1.0F;
		for (EquipmentSlot slot : ARMOUR) {
			factor += Upgrade.manaFlow(Upgrades.fraction(entity.getItemBySlot(slot), Upgrade.FLUJO));
		}
		if (ArmorSets.fullSet(entity) == ForgeMaterial.ECO) {
			factor += ECHO_SET_REGEN;
		}
		float meditation = Math.max(Upgrades.fraction(entity.getMainHandItem(), Upgrade.MEDITACION),
			Upgrades.fraction(entity.getOffhandItem(), Upgrade.MEDITACION));
		factor += Upgrade.meditationRegen(meditation);
		return entity instanceof Player player ? factor * (1.0F + dev.forja.clase.ClassEffects.manaRegenBonus(player)) : factor;
	}

	/** Whether there is this much to spend. On the client, what the server last said. */
	public static boolean canAfford(Player player, float cost) {
		return exempt(player) || value(player) >= cost;
	}

	/** Spends it if it is there, and only then; nothing is taken from a bar that cannot pay the whole of it. */
	public static boolean trySpend(Player player, float cost) {
		if (exempt(player)) {
			return true;
		}
		Data data = data(player);
		if (data.mana + 1.0E-4F < cost) {
			return false;
		}
		data.mana = Math.max(0.0F, data.mana - cost);
		data.lastCast = player.level().getGameTime();
		return true;
	}

	/** Descarga: spends everything there is and says how much that was. */
	public static float spendAll(Player player) {
		if (exempt(player)) {
			return max(player);
		}
		Data data = data(player);
		float all = data.mana;
		data.mana = 0.0F;
		data.lastCast = player.level().getGameTime();
		return all;
	}

	/** Mana straight into the bar, up to its max (Sifón). */
	public static void give(Player player, float amount) {
		Data data = data(player);
		data.mana = Math.min(maxOf(player), data.mana + Math.max(0.0F, amount));
		data.pending = KillFlow.trim(data.mana, data.pending, maxOf(player));
	}

	/** Sets the bar outright, for the tests and the command. */
	public static void set(Player player, float mana) {
		Data data = data(player);
		data.mana = Math.max(0.0F, Math.min(maxOf(player), mana));
		data.pending = KillFlow.trim(data.mana, data.pending, maxOf(player));
	}

	/** A spell has just been paid for: Sifón may give back part of it the first time it lands. */
	public static void cast(Player player, float cost) {
		Data data = data(player);
		data.lastCost = cost;
		data.siphonOpen = true;
		data.awake = true;
	}

	/** A blow of a spell of {@code weapon} landed: Sifón pays back, once a spell. */
	public static void onSpellLanded(Player player, ItemStack weapon) {
		float share = Upgrade.siphonShare(Upgrades.fraction(weapon, Upgrade.SIFON));
		Data data = data(player);
		if (share <= 0.0F || !data.siphonOpen || exempt(player)) {
			return;
		}
		data.siphonOpen = false;
		give(player, data.lastCost * share);
		if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
			level.sendParticles(new dev.forja.registry.GlintOptions(Upgrade.SIFON.color, 0.8F),
				player.getX(), player.getY(1.0), player.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
		}
	}

	/**
	 * A cast turned away for want of mana: a small fizzle, and the bar flashes. Never a line in chat — the
	 * bar is right there, and a message every time the button is pressed on an empty one would drown it.
	 */
	public static void deny(Player player) {
		if (player.level().isClientSide()) {
			clientDenied++;
			return;
		}
		Data data = data(player);
		data.denied++;
		data.awake = true;
		long now = player.level().getGameTime();
		if (now - data.lastDenySound >= DENY_SOUND_EVERY) {
			data.lastDenySound = now;
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.35F, 1.9F);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.3F, 1.8F);
		}
		if (player instanceof ServerPlayer server) {
			sync(server, data, true);
		}
	}

	/** How many casts this player has had refused. */
	public static int denied(Player player) {
		return data(player).denied;
	}

	/** A kill: its worth waits in the pool and flows in from there. */
	public static void onKill(ServerPlayer killer, LivingEntity victim) {
		CombatConfig cfg = CombatConfig.get();
		Data data = data(killer);
		if (exempt(killer) || !data.awake) {
			// A player who has never had a use for mana is not handed a bar of it.
			return;
		}
		float max = maxOf(killer);
		float worth = KillFlow.reward(cfg.killManaBase, cfg.killManaPerHealth, cfg.killManaCapShare, max, victim);
		data.pending += KillFlow.accept(data.mana, data.pending, max, worth);
	}

	/** Whether this item is something mana is spent on or grown by: then the bar has a reason to be on screen. */
	public static boolean usesMana(ItemStack stack) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null) {
			return false;
		}
		if (parts.type() == ForgeType.BACULO || parts.type() == ForgeType.GRIMORIO || parts.type() == ForgeType.FAROL) {
			return true;
		}
		Upgrades upgrades = stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		for (Upgrade upgrade : MANA_UPGRADES) {
			if (upgrades.percent(upgrade) > 0) {
				return true;
			}
		}
		return false;
	}

	private static final Upgrade[] MANA_UPGRADES = {
		Upgrade.CONCENTRACION, Upgrade.SIFON, Upgrade.DESCARGA, Upgrade.MEDITACION, Upgrade.RESERVA, Upgrade.FLUJO,
		Upgrade.FILO_ARCANO, Upgrade.ESTALLIDO_ARCANO, Upgrade.PASO_ARCANO
	};
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Whether anything in the hands or on the body has a use for mana. */
	public static boolean carriesMana(Player player) {
		if (usesMana(player.getMainHandItem()) || usesMana(player.getOffhandItem())) {
			return true;
		}
		for (EquipmentSlot slot : ARMOUR) {
			if (usesMana(player.getItemBySlot(slot))) {
				return true;
			}
		}
		ForgeMaterial set = ArmorSets.fullSet(player);
		return set == ForgeMaterial.AMATISTA || set == ForgeMaterial.ECO;
	}

	/** Whether the bar has ever had a reason to be on this player's screen. */
	public static boolean awake(Player player) {
		if (player.level().isClientSide()) {
			State state = player.getAttached(STATE);
			return state != null && state.awake();
		}
		return data(player).awake;
	}

	/** Death and the way back: the bar comes back whole and nothing is left waiting. */
	public static void refill(ServerPlayer player) {
		Data data = data(player);
		data.max = maxOf(player);
		data.mana = data.max;
		data.pending = 0.0F;
		data.lastCast = Long.MIN_VALUE / 4;
		data.siphonOpen = false;
		sync(player, data, true);
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tick(player, player.level().getGameTime());
		}
	}

	/** One tick of one player's bar: its max, what comes back, what flows in from kills, and what the client is told. */
	public static void tick(ServerPlayer player, long now) {
		Data data = data(player);
		float max = maxOf(player);
		data.max = max;
		if (now % 10 == 0 && !data.awake && carriesMana(player)) {
			data.awake = true;
		}
		if (exempt(player)) {
			data.mana = max;
			data.pending = 0.0F;
		} else {
			data.mana = Math.min(max, data.mana + regenPerTick(player, now));
			float flow = KillFlow.flow(now, data.mana, data.pending, max);
			data.mana += flow;
			data.pending = KillFlow.trim(data.mana, data.pending - flow, max);
		}
		sync(player, data, false);
	}

	/** Tells the client, when anything it draws has moved far enough to see. */
	private static void sync(ServerPlayer player, Data data, boolean force) {
		State now = new State(data.mana, data.max, data.pending, data.awake, data.denied);
		State last = data.synced;
		boolean changed = force || last == null
			|| Math.abs(now.mana() - last.mana()) >= 0.5F || Math.abs(now.pending() - last.pending()) >= 0.5F
			|| now.max() != last.max() || now.awake() != last.awake() || now.denied() != last.denied()
			|| now.mana() >= now.max() && last.mana() < last.max()
			|| now.pending() <= 0.0F && last.pending() > 0.0F;
		if (changed) {
			data.synced = now;
			player.setAttached(STATE, now);
		}
	}

	/** Writes the exact value onto the player, for a save that must not lose the last half point. */
	public static void save(ServerPlayer player) {
		Data data = DATA.get(player.getUUID());
		if (data != null) {
			sync(player, data, true);
		}
	}

	public static void register() {
		java.util.Objects.requireNonNull(STATE);
		ServerTickEvents.END_SERVER_TICK.register(Mana::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			save(handler.getPlayer());
			forget(handler.getPlayer());
		});
		// Whoever comes back, comes back with a full bar: an empty one on respawn would only punish the dead twice.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refill(newPlayer));
	}
}
