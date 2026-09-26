package dev.forja.upgrade;

import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.Forja;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Frenesi: what a chain of blows does to your gear. From the second hit inside the window a bar
 * shows up and every hit after that pushes your upgrades further past their own numbers, up to the
 * ceiling of each one. Plain upgrades gain the most out of it; the expensive ones barely move,
 * because they were already strong before the combo started.
 */
public final class Frenzy {
	/** Hits to reach the top of the bar. */
	/** The colour of a full combo: the same hot orange the forge burns with. */
	private static final net.minecraft.core.particles.DustParticleOptions FRENZY =
		new net.minecraft.core.particles.DustParticleOptions(0xFF7A1E, 1.2F);

	public static final int MAX_HITS = 5;

	/** A combo only lives this long without a new hit. */
	public static final int WINDOW_TICKS = 60;

	/** The bar appears from this hit on, so a single swing never clutters the screen. */
	private static final int SHOW_FROM = 2;

	private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

	/** How far along the combo is, synced to its own player so the bar on screen can follow it. */
	public static final AttachmentType<Integer> HITS = AttachmentRegistry.<Integer>builder()
		.initializer(() -> 0)
		.syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Forja.id("frenesi"));

	private static final class State {
		int hits;
		int expires;
	}

	private Frenzy() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			STATES.entrySet().removeIf(entry -> {
				if (now <= entry.getValue().expires) {
					return false;
				}
				if (entry.getKey() instanceof ServerPlayer player) {
					player.setAttached(HITS, 0);
				}
				return true;
			});
		});
	}

	public static void onHit(LivingEntity attacker) {
		onHit(attacker, net.minecraft.world.item.ItemStack.EMPTY);
	}

	/**
	 * A blow landed: the combo grows, and the bar follows it. A Duelista weapon counts every blow twice
	 * and holds the combo open for twice as long.
	 */
	public static void onHit(LivingEntity attacker, net.minecraft.world.item.ItemStack weapon) {
		if (!(attacker instanceof ServerPlayer player)) {
			return;
		}
		boolean duelist = dev.forja.forge.Perk.has(weapon, dev.forja.forge.Perk.DUELISTA);
		int now = player.level().getServer().getTickCount();
		State state = STATES.computeIfAbsent(player, ignored -> new State());
		int before = state.hits;
		state.hits = Math.min(MAX_HITS, state.hits + (duelist ? 2 : 1));
		state.expires = now + (duelist ? WINDOW_TICKS * 2 : WINDOW_TICKS);
		player.setAttached(HITS, state.hits >= SHOW_FROM ? state.hits : 0);
		// Topping out is the one thing in the mod that pushes an upgrade past what its tooltip says,
		// and the only sign of it was a number on a bar. It gets a ring and a note now — once, on the
		// blow that gets there, never again until the combo drops and is built back up.
		if (before < MAX_HITS && state.hits >= MAX_HITS && player.level() instanceof ServerLevel level) {
			dev.forja.entity.Shockwave.burst(level, player.position(), 2.4, 8, 0xFFB43C, 0.45F);
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
				player.getX(), player.getY(1.0), player.getZ(), 16, 0.35, 0.5, 0.35, 0.25);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.9F);
		}
	}

	/**
	 * Puts the combo out.
	 *
	 * <p>The Templador's oil is the only thing that does this, and it is the point of him: everything
	 * else in the mod takes health, which comes back on its own, while a combo is several seconds of
	 * work that cannot be got back except by doing it again.
	 *
	 * @return the combo that was lost, so the mob can tell whether the throw was worth anything
	 */
	public static int douse(LivingEntity target) {
		State state = STATES.remove(target);
		if (target instanceof ServerPlayer player) {
			player.setAttached(HITS, 0);
		}
		return state == null ? 0 : state.hits;
	}

	/** The hits the bar should show for this player, from either side of the connection. */
	public static int shownHits(Player player) {
		return player.getAttachedOrElse(HITS, 0);
	}

	/** How far along the combo is, from 0 to 1. */
	public static float level(LivingEntity attacker) {
		State state = STATES.get(attacker);
		if (state == null || attacker.level().getServer().getTickCount() > state.expires) {
			return 0.0F;
		}
		return state.hits / (float) MAX_HITS;
	}

	/**
	 * The upgrade, seen through the frenzy. The returned share can pass 100%: the combo is exactly the
	 * one thing in the mod that pushes an upgrade past what it says on the tooltip.
	 */
	public static float fraction(LivingEntity attacker, net.minecraft.world.item.ItemStack weapon, Upgrade upgrade) {
		float base = Upgrades.fraction(weapon, upgrade);
		if (base <= 0.0F) {
			return base;
		}
		return base * (1.0F + (upgrade.frenzyCeiling() - 1.0F) * level(attacker));
	}

}
