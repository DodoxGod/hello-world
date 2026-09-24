package dev.forja.forge;

import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Flight of a pair of forged wings. A membrane only holds so much air: the wings carry you for a few
 * seconds and then give out, and the bar on the screen is what is left of them. Aerodinamica trades
 * most of that reserve away and turns the range into speed.
 *
 * <p><b>Most</b>, not all. Nothing here makes flight free. Wings can be made to last a very long time
 * and to go very fast, and a pinch of gunpowder buys a shove, but no combination of upgrades ever
 * stops the bar going down — the way a rocket buys you height and an elytra still cannot climb on its
 * own. Two things used to break that rule and both are gone:
 *
 * <ul>
 *   <li>Aerodinamica at the full hundred skipped the drain altogether, so the best wings in the game
 *       simply flew forever.</li>
 *   <li>A Propulsion burst put back a tenth of the whole reserve for a cooldown of twelve ticks, so
 *       any wings worth more than six seconds <em>gained</em> flight by bursting. With Halcon it put
 *       the reserve back to full, which was perpetual on any wings at all.</li>
 * </ul>
 */
public final class Flight {
	/** Seconds on the ground to fill an empty reserve again. */
	private static final int RECHARGE_SECONDS = 3;

	/** How long a dive is remembered, so letting go of it still throws you up. */
	private static final int DIVE_MEMORY_TICKS = 4;

	/** Ticks between two Propulsion bursts at no upgrade at all. */
	private static final int BURST_COOLDOWN = 15;

	/**
	 * The most of the bill Aerodinamica can ever take off you.
	 *
	 * <p>At the full hundred the reserve lasts five times as long, which is an enormous upgrade and
	 * still not free. It used to be one, and one meant the drain never ran.
	 */
	private static final float MAX_RELIEF = 0.8F;

	/** Live flight of the players in the air; weak keys so a player who leaves takes their state along. */
	private static final Map<Player, State> STATES = new WeakHashMap<>();

	private static final class State {
		int diving;
		int burstCooldown;
	}

	private Flight() {
	}

	public static boolean isWings(ItemStack stack) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		return parts != null && parts.type().kind == ForgeType.Kind.ALAS && !stack.isBroken();
	}

	/** The whole reserve of a pair of wings, in ticks. */
	public static int maxTicks(ItemStack wings) {
		ForgedParts parts = wings.get(ModComponents.PARTS);
		if (parts == null) {
			return 0;
		}
		float seconds = ForgeStats.sheet(wings, parts).flight;
		return Math.max(20, Math.round(seconds * 20.0F));
	}

	/** What is left of the reserve, in ticks. Wings that have never flown are full. */
	public static int remaining(ItemStack wings) {
		return Math.min(wings.getOrDefault(ModComponents.VUELO, Integer.MAX_VALUE), maxTicks(wings));
	}

	private static void set(ItemStack wings, int ticks) {
		wings.set(ModComponents.VUELO, ticks);
	}

	/**
	 * Whether the reserve has run dry. Read straight off the component, without working the stats out, so
	 * the mixin that stops the glide can ask this every tick.
	 */
	public static boolean exhausted(ItemStack wings) {
		return isWings(wings) && wings.getOrDefault(ModComponents.VUELO, 1) <= 0;
	}

	/** Whether these wings still have air in them, which is what lets a glide start and go on. */
	public static boolean canFly(ItemStack wings) {
		return isWings(wings) && remaining(wings) > 0;
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator()) {
					tick(level, player);
				}
			}
		});
	}

	private static void tick(ServerLevel level, ServerPlayer player) {
		ItemStack wings = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!isWings(wings)) {
			STATES.remove(player);
			return;
		}
		int max = maxTicks(wings);
		int left = remaining(wings);
		if (!player.isFallFlying()) {
			STATES.remove(player);
			// Wings fill up again while your feet are on something, or while the water holds you.
			if (left < max && (player.onGround() || player.isInWater() || player.isPassenger())) {
				// Aeronauta fills the reserve three times as fast as anyone else's wings.
				int speed = Perk.has(wings, Perk.AERONAUTA) ? Perk.AERONAUTA_RECHARGE : 1;
				set(wings, Math.min(max, left + Math.max(1, max * speed / (RECHARGE_SECONDS * 20))));
			}
			return;
		}
		if (level.getGameTime() % 20 == 0) {
			Mastery.addExperience(player, wings, 1);
			ItemHistory.addFlight(wings, 20);
		}
		State state = STATES.computeIfAbsent(player, ignored -> new State());
		float aero = Upgrades.fraction(wings, Upgrade.AERODINAMICA);
		// Aerodinamica pays part of the bill: a half filled one drains only half of the ticks, and a
		// full one a fifth. Never all of it — the bar always goes down.
		if (level.getGameTime() % 20 >= Math.round(Math.min(aero, MAX_RELIEF) * 20.0F)) {
			left = Math.max(0, left - 1);
			set(wings, left);
		}
		if (left == 0) {
			// The mixin on canGlide stops the glide itself; this is only the warning.
			player.sendOverlayMessage(Component.translatable("gui.forja.vuelo.agotado"));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.4F, 0.6F);
			return;
		}
		trail(level, player, wings, left, max);
		speed(level, player, wings, aero, max);
		dive(level, player, state);
		burst(level, player, wings, state, max);
	}

	/**
	 * The wake off a pair of wings in flight.
	 *
	 * <p>Flying is the loudest thing a player can do in this mod and it left nothing behind at all —
	 * the same empty sky whether you were gliding on a pair of leather wings or a pair of star iron
	 * ones. The trail is in the wings' own material, which is the only place the mod ever shows you
	 * what somebody is wearing from a distance.
	 *
	 * <p>It <b>thins as the reserve runs out</b>. That is the useful part: the bar is on your own
	 * screen and useless to anybody watching, but a trail that has gone from a stream to the odd mote
	 * says "that one is about to drop" to you and to everyone else.
	 */
	private static void trail(ServerLevel level, ServerPlayer player, ItemStack wings, int left, int max) {
		float share = max <= 0 ? 0.0F : left / (float) max;
		// One every other tick when full, one in six when nearly out.
		int every = share > 0.6F ? 2 : share > 0.25F ? 3 : 6;
		if (level.getGameTime() % every != 0) {
			return;
		}
		var parts = wings.get(dev.forja.registry.ModComponents.PARTS);
		int colour = parts == null || parts.primary() == null ? 0xE8ECF5 : parts.primary().color;
		// Behind the shoulders, not at the feet: it has to read as coming off the wings.
		Vec3 back = player.getLookAngle().normalize().scale(-0.7);
		Vec3 side = new Vec3(-back.z, 0.0, back.x).normalize().scale(0.55);
		for (int wing = -1; wing <= 1; wing += 2) {
			Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.7, 0.0).add(back).add(side.scale(wing));
			level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(colour, 0.9F),
				at.x, at.y, at.z, 1, 0.04, 0.04, 0.04, 0.0);
		}
	}

	/** Aerodinamica: the reserve the wings gave up comes back as push, on top of what your boots give you. */
	private static void speed(ServerLevel level, ServerPlayer player, ItemStack wings, float aero, int max) {
		if (aero <= 0.0F) {
			return;
		}
		float range = Upgrade.glideSpeed(aero, max / 20.0F);
		double base = player.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
		double boots = Math.max(0.0, player.getAttributeValue(Attributes.MOVEMENT_SPEED) / base - 1.0);
		double push = range + boots * Upgrade.glideBoost(aero) * 0.06;
		Vec3 motion = player.getDeltaMovement();
		// A ceiling instead of a lasting acceleration, so the wings are fast but never absurd.
		if (motion.horizontalDistance() >= 0.9 + range * 18.0) {
			return;
		}
		player.setDeltaMovement(motion.add(player.getLookAngle().normalize().scale(push)));
		player.hurtMarked = true;
		if (level.getGameTime() % 4 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	/** Crouching in the air makes the wings stoop; letting go turns the speed of the fall into height. */
	private static void dive(ServerLevel level, ServerPlayer player, State state) {
		Vec3 motion = player.getDeltaMovement();
		if (player.isShiftKeyDown()) {
			state.diving = DIVE_MEMORY_TICKS;
			player.setDeltaMovement(motion.add(player.getLookAngle().normalize().scale(0.035)).add(0.0, -0.06, 0.0));
			player.hurtMarked = true;
			return;
		}
		if (state.diving <= 0) {
			return;
		}
		state.diving--;
		if (state.diving == DIVE_MEMORY_TICKS - 1 && motion.y < -0.6) {
			// The turn of a hawk: the faster the stoop, the higher it throws you.
			double climb = Math.min(1.1, 0.35 + Math.abs(motion.y) * 0.5);
			player.setDeltaMovement(motion.x * 1.15, climb, motion.z * 1.15);
			player.hurtMarked = true;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.7F, 1.4F);
			level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY(), player.getZ(), 3, 0.2, 0.1, 0.2, 0.0);
		}
	}

	/** Propulsion: crouch and jump in the air to burn a pinch of gunpowder for a shove forward. */
	private static void burst(ServerLevel level, ServerPlayer player, ItemStack wings, State state, int max) {
		if (state.burstCooldown > 0) {
			state.burstCooldown--;
			return;
		}
		float power = Upgrades.fraction(wings, Upgrade.PROPULSION);
		Input input = player.getLastClientInput();
		if (power <= 0.0F || !input.jump() || !input.shift()) {
			return;
		}
		int slot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.GUNPOWDER));
		if (slot < 0) {
			player.sendOverlayMessage(Component.translatable("gui.forja.vuelo.sin_polvora"));
			state.burstCooldown = BURST_COOLDOWN;
			return;
		}
		player.getInventory().removeItem(slot, 1);
		state.burstCooldown = Math.max(6, Math.round(BURST_COOLDOWN * (1.4F - power * 0.6F)));
		player.setDeltaMovement(player.getDeltaMovement().add(player.getLookAngle().normalize().scale(Upgrade.burstPower(power))));
		player.hurtMarked = true;
		// It costs a gunpowder every time and showed nothing for it. A cone of sparks out the back, so
		// the thing you are paying for is visible from inside and from outside.
		Vec3 behind = player.getLookAngle().normalize().scale(-1.0);
		Vec3 from = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0).add(behind.scale(0.6));
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
			from.x, from.y, from.z, 24, 0.25, 0.25, 0.25, 0.6);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
			from.x, from.y, from.z, 10, 0.2, 0.2, 0.2, 0.12);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
		// What a burst buys is a shove, not time in the air. Halcon blows some air back into the wings
		// with it, but never more than the cooldown of the burst itself costs, so bursting can stretch
		// a flight and can never pay for it.
		boolean falcon = dev.forja.upgrade.Synergy.HALCON.active(wings);
		if (falcon) {
			set(wings, Math.min(max, remaining(wings) + state.burstCooldown / 2));
			player.setDeltaMovement(player.getDeltaMovement().add(player.getLookAngle().normalize().scale(0.35)));
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.6F, 1.3F);
		level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY(), player.getZ(), 12, 0.2, 0.2, 0.2, 0.05);
	}
}
