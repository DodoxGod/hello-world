package dev.forja.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.forja.registry.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Pairs of upgrades that do something extra together. Both have to be at least {@link #THRESHOLD}%, so
 * a synergy is a choice: two upgrades taken far instead of five taken a little.
 */
public enum Synergy {
	/** Escarcha + Tormenta: the lightning also freezes whatever it hits. */
	TORMENTA_HELADA(Upgrade.ESCARCHA, Upgrade.TORMENTA, 0xB3E5FF),
	/** Vampirismo + Ejecucion: the wounded feed you twice over. */
	SED_DE_SANGRE(Upgrade.VAMPIRISMO, Upgrade.EJECUCION, 0xD0343F),
	/** Veta + Fortuna: veins run half again as deep. */
	FILON(Upgrade.VETA, Upgrade.FORTUNA, 0xE8DDCF),
	/** Puas + Repulsion: the shield answers every blow with spikes and a shove. */
	MURO(Upgrade.PUAS, Upgrade.REPULSION, 0x9BD37A),
	/** Cosechador + Telequinesis: the harvest walks itself into your pockets. */
	SEGADOR(Upgrade.COSECHADOR, Upgrade.TELEQUINESIS, 0xC8E07A),
	/** Botin + Decapitador: heads roll twice as often. */
	CAZARRECOMPENSAS(Upgrade.BOTIN, Upgrade.DECAPITADOR, 0x3FD46A),
	/** Proteccion + Vitalidad: one more point of armor on top of the rest. */
	FORTALEZA(Upgrade.PROTECCION, Upgrade.VITALIDAD, 0xC9A27A),
	/** Tormenta + Onda de choque: the bolt jumps to whatever stands next to the target. */
	CADENA_DE_RAYOS(Upgrade.TORMENTA, Upgrade.ONDA_DE_CHOQUE, 0xFFE45C),
	/** Densidad + Estallido de viento: the smash throws everything around the target into the air. */
	VENDAVAL(Upgrade.DENSIDAD, Upgrade.ESTALLIDO_DE_VIENTO, 0xB8E6F5),
	/** Cebo + Suerte del mar: now and then the line comes up twice as full. */
	BANCO_DE_PECES(Upgrade.CEBO, Upgrade.SUERTE_DEL_MAR, 0x4FD0C2),
	/** Densidad + Onda de choque: sneak in the air and the mace drags you straight down. */
	METEORO(Upgrade.DENSIDAD, Upgrade.ONDA_DE_CHOQUE, 0x6E6E7E),
	/** Embestida + Alcance: the spear runs through whatever stands behind the target. */
	JUSTA(Upgrade.EMBESTIDA, Upgrade.ALCANCE, 0x9FE2BF),
	/** Aturdimiento + Segunda cabeza: the whole ring around the target is left reeling, not just the one hit. */
	MARTILLO_PILON(Upgrade.ATURDIMIENTO, Upgrade.SEGUNDA_CABEZA, 0xC9B45A),
	/** Rafaga + Nudillos de hierro: a full frenzy turns the gloves into a hail of punches. */
	CIEN_MANOS(Upgrade.RAFAGA, Upgrade.NUDILLOS_DE_HIERRO, 0xFF9AB0),
	/** Multidisparo + Perforacion: the crossbow puts three bolts through everything in a line. */
	TORMENTA_DE_FLECHAS(Upgrade.MULTIDISPARO, Upgrade.PERFORACION, 0xD8C89A),
	/** Punta envenenada + Punta ignea: the wound turns on the body it is in. */
	PUNTA_MALDITA(Upgrade.PUNTA_ENVENENADA, Upgrade.PUNTA_IGNEA, 0x7F5FBF),
	/** Punta afilada + Asta ligera: an arrow that is both quick and sharp finds the gap. */
	ASTA_PERFECTA(Upgrade.PUNTA_AFILADA, Upgrade.ASTA_LIGERA, 0xE8E8F0),
	/** Aerodinamica + Propulsion: the burst throws you and the wings keep the speed. */
	HALCON(Upgrade.AERODINAMICA, Upgrade.PROPULSION, 0xA9E2FF),
	/** Herradura + Peto: a mount that neither slows down nor gives ground. */
	CARGA(Upgrade.HERRADURA, Upgrade.PETO, 0xC9A27A),
	/** Excavacion + Eficiencia: the blocks the area takes with it cost the tool nothing. */
	CANTERA(Upgrade.EXCAVACION, Upgrade.EFICIENCIA, 0xBFAE95),
	/** Vitalidad + Regeneracion: the breastplate answers a bad wound with a second wind. */
	SEGUNDO_ALIENTO(Upgrade.VITALIDAD, Upgrade.REGENERACION, 0xFF8FA8),
	/** Sonar + Vision nocturna: the helmet sees further and holds what it saw. */
	VIGIA(Upgrade.SONAR, Upgrade.VISION_NOCTURNA, 0x7FE0D0),
	/** Corriente + Canalizacion: the water throws you and the storm marks where you were. */
	TEMPESTAD(Upgrade.CORRIENTE, Upgrade.CANALIZACION, 0x6FD3E8),
	/** Cosechador + Siega de almas: what the scythe drags in feeds whoever swung it. */
	SIEGA_NEGRA(Upgrade.COSECHADOR, Upgrade.SIEGA_DE_ALMAS, 0x8FD98F),
	/** Anclaje + Temple: boots that will not be moved and will not be told how to feel about it. */
	FIRME(Upgrade.ANCLAJE, Upgrade.TEMPLE, 0x8E8578),
	/** Rescoldo + Proteccion contra fuego: the fire stops counting at all. */
	SALAMANDRA(Upgrade.RESCOLDO, Upgrade.PROTECCION_CONTRA_FUEGO, 0xFF8A3A),
	/** Prisma + Buscador: the fan is five bolts wide, and every one of them hunts. */
	ENJAMBRE(Upgrade.PRISMA, Upgrade.BUSCADOR, 0x8FE0B0),
	/** Vortice + Tinta indeleble: a rune that has held its prey that long goes out with a bang. */
	COLAPSO(Upgrade.VORTICE, Upgrade.TINTA_INDELEBLE, 0x9A6AE0);

	/** How far both upgrades have to be for the pair to work. */
	public static final int THRESHOLD = 50;

	public final Upgrade first;
	public final Upgrade second;
	public final int color;

	Synergy(Upgrade first, Upgrade second, int color) {
		this.first = first;
		this.second = second;
		this.color = color;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public Component displayName() {
		return Component.translatable("synergy.forja." + this.id());
	}

	public Component description() {
		return Component.translatable("synergy.forja." + this.id() + ".desc");
	}

	/** Whether this item has both upgrades far enough along; broken gear has none of it. */
	public boolean active(ItemStack stack) {
		return !stack.isBroken() && this.active(stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY));
	}

	/** Whether these upgrades alone wake the pair, for the stats that are worked out without a stack. */
	public boolean active(Upgrades upgrades) {
		return upgrades.percent(this.first) >= THRESHOLD && upgrades.percent(this.second) >= THRESHOLD;
	}

	/**
	 * Draws this synergy going off, in its own colour.
	 *
	 * <p>Every synergy has carried a colour since the day they were written and the only thing it was
	 * ever used for was the line in the tooltip. That is a waste of the one thing that already tells
	 * them apart: a player who has taken two upgrades to fifty per cent each has earned something rare
	 * and deserves to be told, in the world and not in a menu, which one just fired.
	 *
	 * <p>Deliberately small. These go off on ordinary hits, several times a second in a fight, so what
	 * works is a handful of motes in a recognisable colour rather than anything that fills the screen.
	 */
	public void spark(ServerLevel level, Vec3 at, int count) {
		level.sendParticles(new DustParticleOptions(this.color, 1.0F),
			at.x, at.y, at.z, count, 0.35, 0.35, 0.35, 0.02);
	}

	/** The same, centred on whatever it happened to. */
	public void spark(ServerLevel level, Entity around, int count) {
		this.spark(level, around.position().add(0.0, around.getBbHeight() * 0.55, 0.0), count);
	}

	/**
	 * A ring of the colour on the floor, for the ones whose whole point is an area.
	 *
	 * <p>Chain lightning, the gale, the pile driver: what matters about those is how far they reached,
	 * and a cluster of motes on the victim cannot say that. The ring can.
	 */
	public void ring(ServerLevel level, Vec3 centre, double radius) {
		dev.forja.entity.Shockwave.burst(level, centre, radius, Math.max(6, (int) Math.round(radius * 1.6)), this.color & 0xFFFFFF, 0.5F);
	}

	/** Every synergy awake on this item. */
	public static List<Synergy> on(ItemStack stack) {
		List<Synergy> found = new ArrayList<>();
		for (Synergy synergy : values()) {
			if (synergy.active(stack)) {
				found.add(synergy);
			}
		}
		return found;
	}
}
