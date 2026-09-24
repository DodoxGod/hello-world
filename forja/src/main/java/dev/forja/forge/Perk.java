package dev.forja.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import dev.forja.registry.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Dones: the gift a piece of gear earns at Maestria 10. One per item, chosen at the forge star by
 * spending the seal stamped with it, and never changed afterwards.
 */
public enum Perk {
	/** Weapons and tools: a quarter of the wear simply does not happen. */
	FILO_ETERNO(0xE8E8F0, kind -> kind == ForgeType.Kind.WEAPON || kind == ForgeType.Kind.TOOL),
	/** Weapons and bows: fresh, unhurt prey takes more. */
	CAZADOR(0xD0343F, kind -> kind == ForgeType.Kind.WEAPON || kind == ForgeType.Kind.RANGED),
	/** Tools: veins run deeper and ores teach more. */
	MINERO(0xE8DDCF, kind -> kind == ForgeType.Kind.TOOL),
	/** Armor and shields: one more point of armor, and a wider parry window. */
	BALUARTE(0x9BD37A, kind -> kind == ForgeType.Kind.ARMOR || kind == ForgeType.Kind.SHIELD),
	/** Armor and wings: softer landings and a quicker step. */
	VIAJERO(0xA9E2FF, kind -> kind == ForgeType.Kind.ARMOR || kind == ForgeType.Kind.ALAS),
	/** Rods: the fish come sooner and richer. */
	PESCADOR(0x4FD0C2, kind -> kind == ForgeType.Kind.PESCA),
	/** Arrows: a quarter of them come back to the quiver. */
	CARGADOR(0xD8C89A, kind -> kind == ForgeType.Kind.MUNICION),
	/** Barding and harnesses: what carries you shrugs off part of what comes at it. */
	JINETE(0xC9A27A, kind -> kind == ForgeType.Kind.MONTURA),
	/** Weapons: the combo starts one blow ahead and holds twice as long. */
	DUELISTA(0xE8A33C, kind -> kind == ForgeType.Kind.WEAPON),
	/** Bows and crossbows: everything they loose flies faster and straighter. */
	TIRADOR(0xB9D6F2, kind -> kind == ForgeType.Kind.RANGED),
	/** Shields: what the shield stops comes back at whoever swung, and nothing shifts you. */
	MURALLA(0x8C8C96, kind -> kind == ForgeType.Kind.SHIELD),
	/** Wings: the reserve fills again three times as fast. */
	AERONAUTA(0xCFE9FF, kind -> kind == ForgeType.Kind.ALAS);

	/** What Cargador gives back: the share of arrows that never leave the quiver. */
	public static final float CARGADOR_SHARE = 0.25F;

	/** Tirador: how much faster an arrow leaves a bow that has it. */
	public static final float TIRADOR_SPEED = 1.15F;

	/** Muralla: the share of a blocked blow that goes back to whoever landed it. */
	public static final float MURALLA_SHARE = 0.25F;

	/** Aeronauta: how many times faster the wings fill again. */
	public static final int AERONAUTA_RECHARGE = 3;

	/** What Jinete turns aside: the share of hits on the mount that simply do not land. */
	public static final float JINETE_SHARE = 0.20F;

	/** The Maestria level that unlocks a gift. */
	public static final int LEVEL = Mastery.MAX_LEVEL;

	public final int color;
	private final Predicate<ForgeType.Kind> kinds;

	Perk(int color, Predicate<ForgeType.Kind> kinds) {
		this.color = color;
		this.kinds = kinds;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public Component displayName() {
		return Component.translatable("perk.forja." + this.id());
	}

	public Component description() {
		return Component.translatable("perk.forja." + this.id() + ".desc");
	}

	public boolean appliesTo(ForgeType type) {
		return this.kinds.test(type.kind);
	}

	/**
	 * Draws this gift going off, in its own colour.
	 *
	 * <p>The same argument the synergies got, and for the same reason: a gift is the rarest thing on a
	 * piece — one per item, at Maestria 10, paid for with a seal — and until now the only evidence any
	 * of them existed was a line of tooltip. Every one of them already carries a colour for that line,
	 * so the colour is what gets used.
	 *
	 * <p>Small, and only where the gift <b>changed the outcome</b>. Cazador that hit a wounded target
	 * did nothing and draws nothing; a quarter of wear skipped on a blow that would not have worn
	 * anything draws nothing. Otherwise the mark stops meaning "it worked" and starts meaning "it is
	 * equipped", which the tooltip already said.
	 */
	public void spark(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 at, int count) {
		level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(this.color, 1.1F),
			at.x, at.y, at.z, count, 0.3, 0.3, 0.3, 0.02);
	}

	/** The same, centred on whatever it happened to. */
	public void spark(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity around, int count) {
		this.spark(level, around.position().add(0.0, around.getBbHeight() * 0.6, 0.0), count);
	}

	/** The gifts this kind of gear can take, in token order. */
	public static List<Perk> forType(ForgeType type) {
		List<Perk> options = new ArrayList<>();
		for (Perk perk : values()) {
			if (perk.appliesTo(type)) {
				options.add(perk);
			}
		}
		return options;
	}

	/** The gift a seal would engrave on this item, or null when the seal is not for it. */
	public static @Nullable Perk fromSeal(ForgeType type, ItemStack seal) {
		Perk perk = dev.forja.item.SealItem.perk(seal);
		return perk != null && perk.appliesTo(type) ? perk : null;
	}

	/** The gift engraved on an item, if it has one and still works. */
	public static @Nullable Perk of(ItemStack stack) {
		String id = stack.get(ModComponents.DON);
		if (id == null || stack.isBroken()) {
			return null;
		}
		for (Perk perk : values()) {
			if (perk.id().equals(id)) {
				return perk;
			}
		}
		return null;
	}

	public static boolean has(ItemStack stack, Perk perk) {
		return of(stack) == perk;
	}
}
