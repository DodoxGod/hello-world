package dev.forja.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.forja.Forja;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Stamina, server side. Swinging, dodging and holding a shield up against a blow spend it; it comes
 * back after a moment of rest, slower the heavier the armor. The value is synced to its own player
 * for the bar on screen.
 */
public final class Stamina {
	public static final AttachmentType<Float> VALUE = AttachmentRegistry.<Float>builder()
		.initializer(() -> CombatConfig.get().staminaMax)
		.syncWith(ByteBufCodecs.FLOAT.cast(), AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Forja.id("estamina"));

	/**
	 * The rest of what the bar needs on the client: its max, which Aguante raises piece by piece, and what kills
	 * have left waiting to flow in (combat/KillFlow), which the bar draws as a lighter stretch.
	 */
	public record Reserve(float max, float pending) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Reserve> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Reserve::max,
			ByteBufCodecs.FLOAT, Reserve::pending,
			Reserve::new
		);
	}

	public static final AttachmentType<Reserve> RESERVE = AttachmentRegistry.create(Forja.id("estamina_reserva"), builder -> builder
		.initializer(() -> new Reserve(CombatConfig.get().staminaMax, 0.0F))
		.syncWith(Reserve.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Paso arcano: the blink keeps you out of reach this many ticks longer than a dodge. */
	public static final int BLINK_IFRAMES = 3;

	private static final Identifier WEIGHT_MODIFIER = Forja.id("peso_armadura");
	private static final Map<UUID, Data> DATA = new HashMap<>();

	private static final class Data {
		float stamina = CombatConfig.get().staminaMax;
		float synced = -1F;
		/** What kills have left waiting to flow in, and what the client was last told of it and of the max. */
		float pending;
		float syncedPending = -1F;
		float syncedMax = -1F;
		long lastSpend;
		double weight = -1.0;
		boolean tiredAttack;
		long dodgeUntil = -1;
		long dodgeCooldownUntil;
		/** A blow was dodged that would have landed: the counter stays open until then. */
		long counterUntil = -1;
	}

	private Stamina() {
	}

	private static Data data(Player player) {
		return DATA.computeIfAbsent(player.getUUID(), id -> new Data());
	}

	public static void forget(Player player) {
		DATA.remove(player.getUUID());
	}

	private static boolean exempt(Player player) {
		return !CombatConfig.get().enabled || !CombatConfig.get().stamina || player.isCreative() || player.isSpectator();
	}

	public static float value(Player player) {
		return data(player).stamina;
	}

	/** The bar's max: the config's, and Aguante on every piece added up. On the client, what the server said. */
	public static float max(Player player) {
		if (player.level().isClientSide()) {
			return player.getAttachedOrElse(RESERVE, new Reserve(CombatConfig.get().staminaMax, 0.0F)).max();
		}
		return maxOf(player);
	}

	public static float maxOf(LivingEntity entity) {
		float max = CombatConfig.get().staminaMax;
		for (EquipmentSlot slot : ARMOUR) {
			max += Upgrade.enduranceBonus(Upgrades.fraction(entity.getItemBySlot(slot), Upgrade.AGUANTE));
		}
		// The class (clase/ClassEffects): an Asesino's bar is longer, a Tanque's as it always was.
		return entity instanceof Player player ? max * dev.forja.clase.ClassEffects.staminaMaxMultiplier(player) : max;
	}

	/** How much faster than bare the breath comes back: Fuelle on every piece, added up. */
	public static float regenFactor(LivingEntity entity) {
		float factor = 1.0F;
		for (EquipmentSlot slot : ARMOUR) {
			factor += Upgrade.bellowsRegen(Upgrades.fraction(entity.getItemBySlot(slot), Upgrade.FUELLE));
		}
		return entity instanceof Player player ? factor * dev.forja.clase.ClassEffects.staminaRegenMultiplier(player) : factor;
	}

	/**
	 * What a move costs this player: {@code base}, less what Soltura takes off it (the best piece counts). Jumps,
	 * dodges and the weapons' special moves go through here; a swing and a raised shield do not.
	 */
	public static float cost(LivingEntity entity, float base) {
		return base * (1.0F - Upgrade.easeShare(Upgrades.armorFraction(entity, Upgrade.SOLTURA)));
	}

	/**
	 * How much further than bare a dodge carries this player: Quiebro on the boots, and Paso arcano on the blade
	 * in hand while the bar holds a blink's worth of mana. The client moves the player by this; the server takes
	 * the mana when the dodge reaches it.
	 */
	public static double dodgeReach(Player player) {
		double reach = 1.0 + Upgrade.dodgeBoost(Upgrades.fraction(player.getItemBySlot(EquipmentSlot.FEET), Upgrade.QUIEBRO));
		float blink = Upgrades.fraction(player.getMainHandItem(), Upgrade.PASO_ARCANO);
		if (blink > 0.0F && dev.forja.magic.Mana.canAfford(player, Upgrade.BLINK_COST)) {
			reach += Upgrade.blinkBoost(blink);
		}
		return reach;
	}

	/** What kills have left waiting. On the client, what the server said. */
	public static float pending(Player player) {
		if (player.level().isClientSide()) {
			return player.getAttachedOrElse(RESERVE, new Reserve(CombatConfig.get().staminaMax, 0.0F)).pending();
		}
		return data(player).pending;
	}

	/** A kill: its worth waits in the pool and flows in from there, at most 3 % of the bar every 5 ticks (KillFlow). */
	public static void onKill(Player killer, LivingEntity victim) {
		if (exempt(killer)) {
			return;
		}
		CombatConfig cfg = CombatConfig.get();
		Data data = data(killer);
		float max = maxOf(killer);
		float worth = KillFlow.reward(cfg.killStaminaBase, cfg.killStaminaPerHealth, cfg.killStaminaCapShare, max, victim);
		data.pending += KillFlow.accept(data.stamina, data.pending, max, worth);
	}

	/** Spends stamina if there is enough; otherwise empties it and says no. */
	public static boolean trySpend(Player player, float amount) {
		if (exempt(player)) return true;
		Data data = data(player);
		data.lastSpend = player.level().getGameTime();
		if (data.stamina >= amount) {
			data.stamina -= amount;
			return true;
		}
		data.stamina = 0F;
		return false;
	}

	/** A jump: a little, twice that at a run. It goes regardless; out of stamina, the wait before it comes back starts again. */
	public static void onJump(Player player) {
		CombatConfig cfg = CombatConfig.get();
		trySpend(player, cost(player, player.isSprinting() ? cfg.sprintJumpCost : cfg.jumpCost) * dev.forja.clase.ClassEffects.staminaCostMultiplier(player));
	}

	/**
	 * Whether there is this much to spend. On the client, what the server last said (the synced value), so
	 * a move the server would refuse is not shown going off.
	 */
	public static boolean canAfford(Player player, float amount) {
		if (exempt(player)) {
			return true;
		}
		float have = player.level().isClientSide() ? player.getAttachedOrElse(VALUE, max(player)) : data(player).stamina;
		return have >= amount;
	}

	public static void restore(Player player, float amount) {
		Data data = data(player);
		data.stamina = Math.min(maxOf(player), data.stamina + amount);
	}

	public static boolean isDodging(Player player, long now) {
		return data(player).dodgeUntil >= now;
	}

	/**
	 * A blow met the i-frames of a dodge: the dodge was perfect. The next hit inside the window is a
	 * counter, and a little breath comes back. Only the first blow of a dodge counts.
	 */
	public static void markPerfectDodge(Player player) {
		Data data = data(player);
		long now = player.level().getGameTime();
		if (data.counterUntil >= now) {
			return;
		}
		CombatConfig cfg = CombatConfig.get();
		data.counterUntil = now + cfg.counterWindowTicks;
		restore(player, cfg.counterStaminaRefund);
		dev.forja.clase.ClassEvents.onPerfectDodge(player);
		CombatAnim.broadcast(player, CombatAnim.Kind.PERFECT_DODGE, cfg.counterWindowTicks);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("gui.forja.esquiva_perfecta"));
		}
	}

	/** Whether the blow landing now is a counter after a perfect dodge. Reading it clears it. */
	public static boolean consumeCounter(Player player) {
		Data data = data(player);
		boolean open = data.counterUntil >= player.level().getGameTime();
		data.counterUntil = -1;
		return open;
	}

	/** Whether a counter is open for this player. */
	public static boolean counterOpen(Player player) {
		return data(player).counterUntil >= player.level().getGameTime();
	}

	/** Ticks until the player can dodge again (0 when ready). */
	public static int dodgeCooldown(Player player) {
		return (int) Math.max(0L, data(player).dodgeCooldownUntil - player.level().getGameTime());
	}

	public static void onAttack(Player player) {
		data(player).tiredAttack = !trySpend(player, CombatConfig.get().attackCost * dev.forja.clase.ClassEffects.staminaCostMultiplier(player));
	}

	/** Whether the swing that is landing now was made out of breath. Reading it clears it. */
	public static boolean consumeTiredAttack(Player player) {
		Data data = data(player);
		boolean tired = data.tiredAttack;
		data.tiredAttack = false;
		return tired;
	}

	/** Ticks of cooldown the server lets slide: the client counts its own, and a packet can arrive a tick or two late. */
	private static final int DODGE_COOLDOWN_SLACK = 2;
	/** Stamina the server lets slide: the client's copy is synced in half-point steps and can run a little behind. */
	private static final float DODGE_STAMINA_SLACK = 3.0F;

	/**
	 * The client has already moved, so turning a dodge down here leaves the player jumping without the
	 * i-frames. The checks mirror the client's with a little slack, so the two only disagree when the
	 * client is really out of line.
	 */
	public static void onDodge(ServerPlayer player, float x, float z) {
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.enabled || !cfg.dodge || player.isSpectator()) return;
		Data data = data(player);
		long now = player.level().getGameTime();
		if (now + DODGE_COOLDOWN_SLACK < data.dodgeCooldownUntil) return;
		// The class (clase/ClassEffects): what a dodge costs, how long it shields you and how soon the next one comes.
		float dodgeCost = cost(player, cfg.dodgeCost) * dev.forja.clase.ClassEffects.dodgeCostMultiplier(player);
		if (!exempt(player)) {
			if (data.stamina + DODGE_STAMINA_SLACK < dodgeCost) return;
			data.stamina = Math.max(0.0F, data.stamina - dodgeCost);
			data.lastSpend = now;
		}
		data.dodgeUntil = now + cfg.dodgeIframeTicks + dev.forja.clase.ClassEffects.dodgeIframeBonus(player);
		// Paso arcano: the blade in hand turns the dodge into a blink, further, for mana. The client has already
		// moved as far as it thought the mana would carry it; here it is paid for, shown, and a little safer.
		if (Upgrades.fraction(player.getMainHandItem(), Upgrade.PASO_ARCANO) > 0.0F && dev.forja.magic.Mana.trySpend(player, Upgrade.BLINK_COST)) {
			data.dodgeUntil += BLINK_IFRAMES;
			ServerLevel level = player.level();
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(0.5), player.getZ(), 24, 0.3, 0.5, 0.3, 0.05);
			level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(Upgrade.PASO_ARCANO.color, 1.0F),
				player.getX(), player.getY(0.5), player.getZ(), 10, 0.3, 0.5, 0.3, 0.0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_TELEPORT,
				net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F);
		}
		data.dodgeCooldownUntil = now + Math.round(cfg.dodgeCooldownTicks * dev.forja.clase.ClassEffects.dodgeCooldownMultiplier(player));
		CombatFeedback.dodge(player);
		double length = Math.sqrt(x * x + z * z);
		if (Double.isFinite(length) && length > 1.0E-4) {
			CombatAnim.broadcast(player, CombatAnim.Kind.DODGE, cfg.dodgeIframeTicks + 4, (float) (x / length), (float) (z / length));
			// Which way it went, seen from the nearest monster after this player: habits the others learn.
			var hunters = player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(8.0),
				mob -> mob.getTarget() == player && mob instanceof net.minecraft.world.entity.monster.Enemy);
			hunters.stream().min(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(player))).ifPresent(mob -> {
				double ux = player.getX() - mob.getX();
				double uz = player.getZ() - mob.getZ();
				double d = Math.max(1.0E-6, Math.hypot(ux, uz));
				double side = (x * (-uz / d) + z * (ux / d)) / length;
				dev.forja.ai.PlayerHabits.onDodge(player, (float) Math.signum(side));
			});
		}
	}

	public static void tick(MinecraftServer server) {
		CombatConfig cfg = CombatConfig.get();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Data data = data(player);
			long now = player.level().getGameTime();
			if (now % 10 == 0) {
				updateWeight(player, data, cfg);
			}
			tick(player, data, now, cfg);
		}
	}

	/** One tick of one player's bar; public for the tests' players, which the server does not tick. */
	public static void tick(ServerPlayer player, long now) {
		tick(player, data(player), now, CombatConfig.get());
	}

	private static void tick(ServerPlayer player, Data data, long now, CombatConfig cfg) {
		float max = maxOf(player);
		if (exempt(player)) {
			data.stamina = max;
			data.pending = 0.0F;
		} else {
			if (now - data.lastSpend >= cfg.staminaRegenDelayTicks && data.stamina < max) {
				double penalty = Math.min(0.9, Math.max(0.0, data.weight) * cfg.regenPenaltyPerWeight);
				data.stamina = (float) Math.min(max, data.stamina + cfg.staminaRegenPerTick * regenFactor(player) * (1.0 - penalty));
			}
			// Kills flow in whether or not the breath is coming back by itself: it is not breath, it is spoils.
			float flow = KillFlow.flow(now, data.stamina, data.pending, max);
			data.stamina += flow;
			data.pending = KillFlow.trim(data.stamina, data.pending - flow, max);
		}
		// Aguante taken off: the bar shrinks with it.
		data.stamina = Math.min(max, data.stamina);
		if (Math.abs(data.stamina - data.synced) >= 0.5F || (data.stamina == max && data.synced != max)) {
			data.synced = data.stamina;
			player.setAttached(VALUE, data.stamina);
		}
		if (max != data.syncedMax || Math.abs(data.pending - data.syncedPending) >= 0.5F || (data.pending <= 0.0F && data.syncedPending > 0.0F)) {
			data.syncedMax = max;
			data.syncedPending = data.pending;
			player.setAttached(RESERVE, new Reserve(max, data.pending));
		}
	}

	/** The heavier the worn set, the slower the walk: up to maxArmorSlow with the heaviest plate. */
	private static void updateWeight(ServerPlayer player, Data data, CombatConfig cfg) {
		double weight = cfg.enabled ? ArmorCalculator.armorWeight(player) : 0.0;
		double slow = weight * cfg.maxArmorSlow;
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && (weight != data.weight || (slow > 0.0001) != speed.hasModifier(WEIGHT_MODIFIER))) {
			speed.removeModifier(WEIGHT_MODIFIER);
			if (slow > 0.0001) {
				speed.addTransientModifier(new AttributeModifier(WEIGHT_MODIFIER, -slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		data.weight = weight;
		// Peso (combat/Weight): heavy plate also slows the arm, and a set that quickens the walk quickens it.
		double swing = cfg.enabled ? Weight.armourSwing(player) : 0.0;
		AttributeInstance attack = player.getAttribute(Attributes.ATTACK_SPEED);
		if (attack != null) {
			AttributeModifier current = attack.getModifier(WEIGHT_MODIFIER);
			if (current == null ? Math.abs(swing) > 0.0001 : Math.abs(current.amount() - swing) > 0.0001) {
				attack.removeModifier(WEIGHT_MODIFIER);
				if (Math.abs(swing) > 0.0001) {
					attack.addTransientModifier(new AttributeModifier(WEIGHT_MODIFIER, swing, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			}
		}
	}
}
