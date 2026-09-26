package dev.forja.client;

import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What you are carrying, seen from outside.
 *
 * <p>A material's trait is the most interesting thing about a finished item and it lived entirely in
 * the tooltip: two swords that behave completely differently looked identical in the hand and looked
 * identical to whoever you were swinging at. This puts a thread of the right stuff at the hand — a
 * spark off slag, a mote of sky off star iron, a bead of charge off voltaic brass — so a weapon reads
 * as what it is made of from a few blocks away.
 *
 * <p>Deliberately <b>one particle every eight ticks</b>, and only within twenty blocks. A trait aura
 * is background: it has to survive being looked at for an hour without becoming the thing you are
 * looking at, and that ceiling is much lower than it feels while you are writing it.
 *
 * <p>Client only, and drawn from the components the server already syncs, so it costs the server
 * nothing and it works on somebody else's sword as well as on your own — which is the point of the
 * legendary mark.
 */
public final class GearAura {
	/** How often one mote comes off a piece, and how far away one is still drawn. */
	private static final int EVERY = 8;
	private static final double RANGE = 20.0;

	/** A legend's mark: pale gold, and it turns rather than drifts, so it is never a trait. */
	private static final DustParticleOptions LEGEND = new DustParticleOptions(0xF0D48A, 1.0F);
	/** A masterwork's: the same idea in white, for the one in a hundred that came out perfect. */
	private static final DustParticleOptions MASTERWORK = new DustParticleOptions(0xFFFFFF, 0.8F);
	private static final DustParticleOptions LIVING = new DustParticleOptions(0xE8231A, 0.9F);
	private static final DustParticleOptions SUN = new DustParticleOptions(0xFFC341, 0.8F);
	private static final DustParticleOptions MOON = new DustParticleOptions(0x5A6CC0, 0.8F);

	private GearAura() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null || client.isPaused() || client.level.getGameTime() % EVERY != 0) {
				return;
			}
			ClientLevel level = client.level;
			Vec3 eye = client.player == null ? Vec3.ZERO : client.player.position();
			for (Player player : level.players()) {
				if (player.position().distanceToSqr(eye) > RANGE * RANGE) {
					continue;
				}
				draw(level, player, player.getMainHandItem(), true);
				draw(level, player, player.getOffhandItem(), false);
				// One armour slot per tick rather than four: a full suit of star iron would otherwise
				// put out five times what a sword does and stop being a thread.
				EquipmentSlot slot = ARMOUR[(int) ((level.getGameTime() / EVERY) % ARMOUR.length)];
				draw(level, player, player.getItemBySlot(slot), null);
			}
		});
	}

	private static final EquipmentSlot[] ARMOUR = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
	};

	/**
	 * One mote off one piece.
	 *
	 * @param mainHand true for the right hand, false for the left, null for a worn piece, which
	 *     decides where on the body the mote comes off
	 */
	private static void draw(ClientLevel level, Player player, ItemStack stack, @Nullable Boolean mainHand) {
		if (stack.isEmpty()) {
			return;
		}
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null) {
			return;
		}
		RandomSource random = player.getRandom();
		Vec3 at = mainHand == null ? worn(player, random) : hand(player, mainHand, random);

		ParticleOptions trait = moteFor(stack, level.isBrightOutside(), random);
		if (trait != null) {
			level.addParticle(trait, at.x, at.y, at.z, 0.0, 0.01, 0.0);
		}
		// The two marks ride on top of the trait rather than replacing it: a legendary blade of star
		// iron is both things, and hiding one behind the other would be throwing information away.
		if (stack.has(ModComponents.LEYENDA)) {
			double angle = level.getGameTime() * 0.09 + (mainHand == null ? Math.PI : 0.0);
			level.addParticle(LEGEND,
				at.x + Math.cos(angle) * 0.28, at.y + 0.05, at.z + Math.sin(angle) * 0.28, 0.0, 0.004, 0.0);
		}
		if (stack.getOrDefault(ModComponents.OBRA_MAESTRA, false)) {
			level.addParticle(MASTERWORK, at.x, at.y + 0.18, at.z, 0.0, 0.01, 0.0);
		}
	}

	/**
	 * The particle a piece's traits call for, or nothing for the traits that are better left quiet.
	 *
	 * <p>Takes the daylight as a flag rather than a level so it can be asked the question outside a
	 * running client, which is the only part of this class worth a test.
	 */
	public static @Nullable ParticleOptions moteFor(ItemStack stack, boolean bright, RandomSource random) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null) {
			return null;
		}
		if (parts.hasTrait(ForgeMaterial.Trait.IGNEO)) {
			return dev.forja.registry.ModParticles.CHISPA;
		}
		if (parts.hasTrait(ForgeMaterial.Trait.ASCUA)) {
			return dev.forja.registry.ModParticles.CENIZA;
		}
		if (parts.hasTrait(ForgeMaterial.Trait.CARGADO)) {
			// Only once there is something in it, so the bead is the charge and not the brass.
			return stack.getOrDefault(ModComponents.CARGA, 0) > 0 ? ParticleTypes.ELECTRIC_SPARK : null;
		}
		if (parts.hasTrait(ForgeMaterial.Trait.ESTELAR)) {
			return ParticleTypes.END_ROD;
		}
		if (parts.hasTrait(ForgeMaterial.Trait.ANIMADO)) {
			return dev.forja.registry.ModParticles.ALMA;
		}
		if (parts.hasTrait(ForgeMaterial.Trait.VIVO)) {
			return LIVING;
		}
		if (parts.hasTrait(ForgeMaterial.Trait.LLANTO)) {
			// It mends itself at night, so it only weeps at night.
			return bright ? null : ParticleTypes.DRIPPING_OBSIDIAN_TEAR;
		}
		if (parts.hasTrait(ForgeMaterial.Trait.SOLAR)) {
			return bright ? SUN : null;
		}
		if (parts.hasTrait(ForgeMaterial.Trait.NOCTURNO)) {
			return bright ? null : MOON;
		}
		if (parts.hasTrait(ForgeMaterial.Trait.RESONANTE)) {
			return random.nextFloat() < 0.5F ? ParticleTypes.SCULK_CHARGE_POP : null;
		}
		if (parts.hasTrait(ForgeMaterial.Trait.DEL_END)) {
			return random.nextFloat() < 0.5F ? ParticleTypes.PORTAL : null;
		}
		return null;
	}

	/**
	 * Which way a player's right hand is, for a given yaw.
	 *
	 * <p>Worth its own method because I got it wrong twice by deriving it in my head. Forward is
	 * {@code (-sin t, 0, cos t)} and the right of that is {@code forward x up}, which comes out as
	 * {@code (-cos t, 0, -sin t)} — not {@code (cos t, sin t)}, which is what it looks like it should
	 * be and is the left hand. Facing south, your right hand points west; the test asserts exactly
	 * that, so the next person to be sure about it has something to be sure against.
	 */
	public static Vec3 rightOf(float yawDegrees) {
		float yaw = yawDegrees * ((float) Math.PI / 180.0F);
		return new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
	}

	/** Roughly where a held item is, from the body rather than from the first-person model. */
	private static Vec3 hand(Player player, boolean mainHand, RandomSource random) {
		boolean right = mainHand == (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
		Vec3 side = rightOf(player.getYRot()).scale(right ? 0.45 : -0.45);
		return new Vec3(
			player.getX() + side.x + (random.nextDouble() - 0.5) * 0.12,
			player.getY(0.62) + (random.nextDouble() - 0.5) * 0.14,
			player.getZ() + side.z + (random.nextDouble() - 0.5) * 0.12
		);
	}

	/** And somewhere on the body, for a worn piece. */
	private static Vec3 worn(Player player, RandomSource random) {
		return new Vec3(
			player.getX() + (random.nextDouble() - 0.5) * 0.7,
			player.getY(0.2 + random.nextDouble() * 0.9),
			player.getZ() + (random.nextDouble() - 0.5) * 0.7
		);
	}
}
