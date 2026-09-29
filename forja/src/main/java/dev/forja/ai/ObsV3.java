package dev.forja.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.forja.entity.ai.CasterGoal;
import dev.forja.forge.ForgeType;
import dev.forja.magic.Spellcasting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The block red_mob_v3 adds after the 200 inputs of v2 (the 102 of {@link ObsM1} and {@link ObsForja}):
 * what is flying at the mob (P), the areas on the floor it should not stand in (Z), and its side and its
 * line of fire (T). A v2 network never sees them - it asks for 200 inputs and gets those - so everything
 * trained so far keeps working.
 *
 * <p>The frame is ObsM1's: "delante" is the flat direction from the mob to its target, "derecha" is
 * (-delante_z, delante_x). Every input is declared once with its name and what it means, and the contract
 * (docs/red_mob_v3_contrato.json) is written from this very list.
 */
public final class ObsV3 {
	/** How far out a projectile is looked for, and how far ahead its flight is followed. */
	public static final double PROJECTILE_RANGE = 16.0;
	public static final int PROJECTILE_TICKS = 40;
	/** A projectile counts if its path comes within this of the mob's box. */
	public static final double PROJECTILE_MISS = 0.5;
	/** Drag per tick of an arrow in the air and in water (vanilla's AbstractArrow). */
	public static final double AIR_DRAG = 0.99;
	public static final double WATER_DRAG = 0.6;
	/** How far round the player a friendly caster's rune counts as one to push towards. */
	public static final double OWN_AREA_RANGE = 8.0;
	/** The player looks at somebody else when that one is this much nearer the middle of their view (radians). */
	public static final double LOOK_MARGIN = 0.35;
	public static final double ALLY_RANGE = 16.0;

	private static final String[] DIRECTIONS = {"adelante", "adelante_derecha", "derecha", "atras_derecha", "atras",
		"atras_izquierda", "izquierda", "adelante_izquierda"};

	/** Name and meaning of every input, in order. */
	private static final Map<String, String> MEANING = new LinkedHashMap<>();

	static {
		for (int k = 0; k < 2; k++) {
			String p = "proy" + k + "_";
			String which = k == 0 ? "el proyectil que llega antes" : "el segundo que llega";
			MEANING.put(p + "presente", which + ": 1 si hay (se acerca, a <=16, y su trayectoria con gravedad pasa a <=0.5 de la caja del mob)");
			MEANING.put(p + "t_impacto/20", "ticks hasta su punto más cercano / 20 (0..2)");
			MEANING.put(p + "delante/8", "su posición ahora respecto al mob, eje delante / 8 (-2..2)");
			MEANING.put(p + "derecha/8", "su posición ahora, eje derecha / 8 (-2..2)");
			MEANING.put(p + "dy/4", "su altura ahora respecto a los pies del mob / 4 (-2..2)");
			MEANING.put(p + "vel_delante/3", "su velocidad (bloques/tick), eje delante / 3 (-2..2)");
			MEANING.put(p + "vel_derecha/3", "su velocidad, eje derecha / 3 (-2..2)");
			MEANING.put(p + "fallo/2", "a qué distancia de la caja pasa en su punto más cercano / 2 (0..0.25)");
			MEANING.put(p + "dueno_jugador", "1 si lo lanzó un jugador");
			MEANING.put(p + "dueno_aliado", "1 si lo lanzó otro monstruo");
			MEANING.put(p + "tipo_flecha", "1 si es una flecha o virote");
			MEANING.put(p + "tipo_magia", "1 si es un proyectil mágico o una bola de fuego/carga");
			MEANING.put(p + "tipo_arrojadiza", "1 si es un tridente u otro objeto lanzado");
		}
		for (String d : DIRECTIONS) {
			MEANING.put("area_r1.5_" + d, "1 si el punto a 1.5 bloques en esa dirección está dentro de un área que daña al mob");
		}
		MEANING.put("area_dentro", "1 si el mob está dentro de un área que le daña (runa de jugador o nube persistente de jugador)");
		MEANING.put("area_dist/8", "distancia al borde del área dañina más cercana / 8 (0 dentro, 1 si no hay o está a >=8)");
		MEANING.put("area_restante/120", "ticks que le quedan a esa área / 120 (0..1; 0 si no hay)");
		MEANING.put("area_propia_cerca", "1 si una runa de un lanzador aliado (no daña monstruos) está a <=8 del jugador: empujarle hacia ella");
		MEANING.put("aliado_en_linea_de_tiro", "1 si otro monstruo corta la línea de los ojos del mob a los del jugador (Squad.allyInLine)");
		MEANING.put("aliado_linea_dist/16", "distancia a ese aliado / 16 (0 si no hay)");
		MEANING.put("yo_arma_arco", "1 si lleva un arco (vanilla o forjado)");
		MEANING.put("yo_arma_ballesta", "1 si lleva una ballesta");
		MEANING.put("yo_arma_baculo", "1 si lleva un báculo (pelea por reglas, CasterGoal)");
		MEANING.put("yo_arma_grimorio", "1 si lleva un grimorio (pelea por reglas, CasterGoal)");
		MEANING.put("yo_arma_cuerpo", "1 si lo que lleva (o la mano vacía) es para cuerpo a cuerpo");
		MEANING.put("yo_arma_alcance/6", "alcance de su golpe: ObsM1.REACH + el alcance extra del arma (modificadores de entity_interaction_range; "
			+ "con componente attack_range, max_reach - 3) / 6");
		MEANING.put("jug_mira_a_otro", "1 si el jugador mira más a otro monstruo (a <=16) que a este, por 0.35 rad o más");
		MEANING.put("jug_de_espaldas", "1 si el mob está a más de 120° de hacia donde mira el jugador");
		MEANING.put("angulo_grupos/pi", "el mayor ángulo, visto desde el jugador, entre este mob y otro que le ataca / pi (1 = pinza)");
		MEANING.put("peligro_tras_jugador", "1 si hay lava o caída (Terrain.dangerNear, a 1.5 o 3) al otro lado del jugador visto desde el mob");
		MEANING.put("jug_bloquea_a_aliado", "1 si el jugador tiene el escudo arriba y mira más a otro monstruo que a este");
		MEANING.put("flanco_escudo", "1 si el jugador tiene el escudo arriba y el mob está fuera de sus 90° de delante");
		// --- D: the player fighting from afar
		MEANING.put("obj_mano_ballesta", "1 si el jugador lleva una ballesta en alguna mano");
		MEANING.put("obj_mano_baculo", "1 si lleva un báculo forjado");
		MEANING.put("obj_mano_grimorio", "1 si lleva un grimorio forjado");
		MEANING.put("obj_mano_arrojadiza", "1 si lleva algo que se lanza (tridente vanilla o forjado, bola de nieve, poción arrojadiza, "
			+ "carga de viento...: ProjectileItem que no sea flecha)");
		MEANING.put("obj_ballesta_cargada", "1 si su ballesta está cargada (dispara en cuanto quiera)");
		MEANING.put("obj_baculo_recarga/14", "ticks que le faltan al báculo para poder lanzar otra vez / 14 (0 = listo; 0..2)");
		MEANING.put("obj_grimorio_recarga/70", "ticks que le faltan al grimorio / 70 (0 = listo; 0..2)");
		MEANING.put("obj_arco_tensado_real/20", "ticks tensando × velocidad de tensado del arco (Tensión, material) / 20 (0..2): 1 = tiro completo");
		// --- W: the player's weapon and what it reaches
		MEANING.put("jug_alcance/6", "alcance real del jugador: atributo entity_interaction_range (base 3 + guadaña 0.75, tridente 0.5, "
			+ "mangual 3, Alcance...) o attack_range.max_reach de la lanza si es mayor / 6");
		MEANING.put("jug_area_radio/6", "radio del área que puede hacer su arma: especial (espadón 3.5, martillo/mazo 4.5, guadaña 6) o runa "
			+ "del grimorio (3) / 6; 0 si no tiene");
		MEANING.put("jug_area_lista", "1 si esa área está disponible ya (sin enfriamiento; el sismo además pide estar en el suelo)");
		MEANING.put("jug_sinergia_area", "1 si su arma golpea en área al impactar: mejoras Onda de choque o Tormenta, o sinergias Cadena de "
			+ "rayos, Meteoro o Vendaval");
		MEANING.put("aliados_en_radio_area/5", "monstruos dentro de ese radio alrededor del jugador / 5 (0..2; 0 si no tiene área)");
		MEANING.put("yo_aliado_mas_cerca/4", "distancia a su monstruo aliado más cercano / 4 (0..2; 2 si no hay)");
		// --- V: how the player moves
		MEANING.put("jug_vel_max", "atributo movement_speed del jugador / 0.1 (1 = andar normal; armaduras, conjuntos y efectos lo cambian)");
		MEANING.put("jug_planeando", "1 si el jugador planea (alas, élitros)");
		// --- E: what the player has in store
		MEANING.put("jug_replica", "1 si el jugador acaba de parar y su siguiente golpe hace el doble (réplica, 20 ticks; 40 si fue perfecta)");
		MEANING.put("jug_parada_perfecta_hab", "1 si tiene el escudo arriba y aún dentro de su ventana de parada perfecta");
		MEANING.put("jug_muralla", "1 si su escudo tiene el grabado Muralla (devuelve parte del golpe parado)");
		MEANING.put("jug_frenesi", "cuánto lleva de Frenesí, 0..1 (golpes seguidos / 5)");
		MEANING.put("jug_anclaje", "parte del empuje que resiste por la mejora Anclaje de sus botas (0..1)");
		MEANING.put("jug_sujeto", "1 si una Tenaza lo tiene sujeto");
		MEANING.put("jug_ocupado", "1 si está comiendo o bebiendo, con estamina < 25, o con el escudo en enfriamiento (guardia rota)");
		// --- Peso (combat/Weight): what each side carries into a blow
		MEANING.put("yo_peso/10", "kilos que lleva el mob (arma en mano + armadura, combat/Weight.carried) / 10; alarga su aviso "
			+ "(1.5 ticks por kilo, tope 14) y su espera entre golpes (+10 % por kilo)");
		MEANING.put("jug_peso/10", "kilos que lleva el jugador (arma + armadura) / 10: cuanto más, más tarda en cargar su golpe al 100 %");
		// --- Correr (ai/MobSprint): the monster's own breath for running
		MEANING.put("yo_estamina/100", "estamina de carrera del mob / 100 (0..1): correr gasta 2 por tick, se recupera 1 por tick tras 20 sin correr");
		MEANING.put("yo_corriendo", "1 si el mob está corriendo (+35 % de velocidad)");
		MEANING.put("aliados_corriendo/5", "monstruos a 16 bloques con el mismo objetivo que están corriendo / 5 (0..2)");
	}

	private ObsV3() {
	}

	public static List<String> names() {
		return Collections.unmodifiableList(new ArrayList<>(MEANING.keySet()));
	}

	public static Map<String, String> meanings() {
		return Collections.unmodifiableMap(MEANING);
	}

	public static int size() {
		return MEANING.size();
	}

	/** What each of v3's new tactics does, with the executor's own numbers (TacticGoal). */
	public static Map<String, String> tactics() {
		Map<String, String> out = new LinkedHashMap<>();
		out.put("tactica_cebo", "corre al aliado más cercano que pelea con el mismo jugador y " + TacticGoal.BAIT_PAST
			+ " bloques más allá (lejos del jugador), velocidad 1.2; sin aliado = RETIRARSE. Máscara: hace falta un aliado.");
		out.put("tactica_relevo", "fuera del alcance del jugador: a " + TacticGoal.RELAY_RADIUS + " bloques más lo que jug_alcance pase de "
			+ Reach.PLAYER_BASE + " (mangual +3, lanza +1.5), girando " + TacticGoal.RELAY_SWING
			+ " rad hacia su lado fijo (id par = +, impar = -), velocidad 1.0. Siempre permitida.");
		out.put("tactica_ocultarse", "tras un bloque que corte la vista del jugador a <=4 (Terrain.cover), si no a " + TacticGoal.SHADOW_BEHIND
			+ " bloques detrás del aliado más cercano (el aliado entre él y el jugador), si no RETIRARSE; velocidad 1.2/1.1. "
			+ "Máscara: aliado o cobertura.");
		out.put("tactica_empujar", "al lado del jugador contrario a la lava/caída más cercana (a 1.5 o 3 de él), a " + TacticGoal.PUSH_SIDE
			+ " bloques; una vez allí, directo al jugador (sus golpes empujan 0.35 extra hacia el peligro: MobAi.pushToDanger); "
			+ "sin peligro = ACERCARSE. Máscara: peligro junto al jugador.");
		return out;
	}

	/** Writes the block into {@code out} from {@code at}, as far as {@code out} goes. */
	public static void fill(Mob mob, Player target, float[] out, int at) {
		double[] v = of(mob, target);
		for (int i = 0; i < v.length && at + i < out.length; i++) {
			out[at + i] = (float) v[i];
		}
	}

	public static double[] of(Mob mob, Player target) {
		double[] o = new double[size()];
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double ux = dx / d;
		double uz = dz / d;
		double rx = -uz;
		double rz = ux;
		int i = 0;

		// --- P: what is flying at it
		List<Incoming> incoming = incoming(mob);
		for (int k = 0; k < 2; k++) {
			if (k < incoming.size()) {
				Incoming p = incoming.get(k);
				Vec3 rel = p.from.subtract(mob.position());
				o[i] = 1.0;
				o[i + 1] = ObsM1.clip(p.ticks / 20.0, 0.0, 2.0);
				o[i + 2] = ObsM1.clip((rel.x * ux + rel.z * uz) / 8.0, -2.0, 2.0);
				o[i + 3] = ObsM1.clip((rel.x * rx + rel.z * rz) / 8.0, -2.0, 2.0);
				o[i + 4] = ObsM1.clip(rel.y / 4.0, -2.0, 2.0);
				o[i + 5] = ObsM1.clip((p.velocity.x * ux + p.velocity.z * uz) / 3.0, -2.0, 2.0);
				o[i + 6] = ObsM1.clip((p.velocity.x * rx + p.velocity.z * rz) / 3.0, -2.0, 2.0);
				o[i + 7] = ObsM1.clip(p.miss / 2.0, 0.0, 1.0);
				o[i + 8] = p.owner == 1 ? 1.0 : 0.0;
				o[i + 9] = p.owner == 2 ? 1.0 : 0.0;
				o[i + 10] = p.kind == 0 ? 1.0 : 0.0;
				o[i + 11] = p.kind == 1 ? 1.0 : 0.0;
				o[i + 12] = p.kind == 2 ? 1.0 : 0.0;
			}
			i += 13;
		}

		// --- Z: the areas on the floor
		List<Zone> zones = zones(mob, target);
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4.0;
			double wx = ux * Math.cos(a) + rx * Math.sin(a);
			double wz = uz * Math.cos(a) + rz * Math.sin(a);
			o[i++] = inside(zones, mob.getX() + wx * 1.5, mob.getY(), mob.getZ() + wz * 1.5) ? 1.0 : 0.0;
		}
		Zone nearest = null;
		double edge = Double.MAX_VALUE;
		for (Zone zone : zones) {
			if (!zone.hurts) {
				continue;
			}
			double e = Math.max(0.0, Math.hypot(mob.getX() - zone.at.x, mob.getZ() - zone.at.z) - zone.reach);
			if (e < edge) {
				edge = e;
				nearest = zone;
			}
		}
		o[i++] = inside(zones, mob.getX(), mob.getY(), mob.getZ()) ? 1.0 : 0.0;
		o[i++] = nearest == null ? 1.0 : ObsM1.clip(edge / 8.0, 0.0, 1.0);
		o[i++] = nearest == null ? 0.0 : ObsM1.clip(nearest.ticksLeft / 120.0, 0.0, 1.0);
		boolean own = false;
		for (Zone zone : zones) {
			if (!zone.hurts && Math.hypot(target.getX() - zone.at.x, target.getZ() - zone.at.z) <= OWN_AREA_RANGE) {
				own = true;
			}
		}
		o[i++] = own ? 1.0 : 0.0;

		// --- T: its side and its line of fire
		Mob inLine = Squad.allyInLineOf(mob, target);
		o[i++] = inLine != null ? 1.0 : 0.0;
		o[i++] = inLine != null ? ObsM1.clip(mob.distanceTo(inLine) / 16.0, 0.0, 1.0) : 0.0;
		ItemStack held = mob.getMainHandItem();
		ForgeType spell = CasterGoal.spell(mob);
		boolean bow = held.getItem() instanceof BowItem;
		boolean crossbow = held.getItem() instanceof CrossbowItem;
		o[i++] = bow ? 1.0 : 0.0;
		o[i++] = crossbow ? 1.0 : 0.0;
		o[i++] = spell == ForgeType.BACULO ? 1.0 : 0.0;
		o[i++] = spell == ForgeType.GRIMORIO ? 1.0 : 0.0;
		o[i++] = !bow && !crossbow && spell == null ? 1.0 : 0.0;
		o[i++] = ObsM1.clip(reach(held) / 6.0, 0.0, 2.0);
		List<Mob> side = new ArrayList<>();
		for (Mob other : ObsM1.allies(mob)) {
			if (other.getTarget() == target && other.distanceTo(target) <= ALLY_RANGE) {
				side.add(other);
			}
		}
		double atMe = offLook(target, mob.getX(), mob.getZ());
		double atOther = Double.MAX_VALUE;
		double widest = 0.0;
		double meAngle = Math.atan2(mob.getZ() - target.getZ(), mob.getX() - target.getX());
		for (Mob other : side) {
			atOther = Math.min(atOther, offLook(target, other.getX(), other.getZ()));
			double otherAngle = Math.atan2(other.getZ() - target.getZ(), other.getX() - target.getX());
			widest = Math.max(widest, Math.abs(wrap(otherAngle - meAngle)));
		}
		boolean looksElsewhere = atOther + LOOK_MARGIN < atMe;
		o[i++] = looksElsewhere ? 1.0 : 0.0;
		o[i++] = atMe > Math.toRadians(120.0) ? 1.0 : 0.0;
		o[i++] = widest / Math.PI;
		Vec3 danger = Terrain.dangerNear(target);
		o[i++] = danger != null && danger.x * ux + danger.z * uz > 0.5 ? 1.0 : 0.0;
		boolean shield = target.isBlocking();
		o[i++] = shield && looksElsewhere ? 1.0 : 0.0;
		o[i++] = shield && atMe > Math.toRadians(90.0) ? 1.0 : 0.0;

		// --- D: the player fighting from afar
		ItemStack main = target.getMainHandItem();
		ItemStack off = target.getOffhandItem();
		ItemStack crossbowHeld = main.getItem() instanceof CrossbowItem ? main : off.getItem() instanceof CrossbowItem ? off : ItemStack.EMPTY;
		ItemStack staff = forgedOf(main, ForgeType.BACULO) ? main : forgedOf(off, ForgeType.BACULO) ? off : ItemStack.EMPTY;
		ItemStack tome = forgedOf(main, ForgeType.GRIMORIO) ? main : forgedOf(off, ForgeType.GRIMORIO) ? off : ItemStack.EMPTY;
		o[i++] = crossbowHeld.isEmpty() ? 0.0 : 1.0;
		o[i++] = staff.isEmpty() ? 0.0 : 1.0;
		o[i++] = tome.isEmpty() ? 0.0 : 1.0;
		o[i++] = thrown(main) || thrown(off) ? 1.0 : 0.0;
		o[i++] = !crossbowHeld.isEmpty() && CrossbowItem.isCharged(crossbowHeld) ? 1.0 : 0.0;
		o[i++] = staff.isEmpty() ? 0.0 : ObsM1.clip(waitLeft(target, staff, ForgeType.BACULO) / (double) Spellcasting.BOLT_COOLDOWN, 0.0, 2.0);
		o[i++] = tome.isEmpty() ? 0.0 : ObsM1.clip(waitLeft(target, tome, ForgeType.GRIMORIO) / (double) Spellcasting.TOME_COOLDOWN, 0.0, 2.0);
		double drawn = target.isUsingItem() && target.getUseItem().getItem() instanceof BowItem
			? target.getTicksUsingItem() * dev.forja.item.ForgedItems.ForgedBowItem.drawSpeed(target.getUseItem()) : 0.0;
		o[i++] = ObsM1.clip(drawn / 20.0, 0.0, 2.0);

		// --- W: the player's weapon and what it reaches
		o[i++] = ObsM1.clip(Reach.player(target) / 6.0, 0.0, 2.0);
		double area = areaRadius(main);
		o[i++] = area / 6.0;
		boolean quake = forgedOf(main, ForgeType.MARTILLO) || forgedOf(main, ForgeType.MAZO);
		o[i++] = area > 0.0 && !target.getCooldowns().isOnCooldown(main) && (!quake || target.onGround()) ? 1.0 : 0.0;
		o[i++] = areaOnHit(main) ? 1.0 : 0.0;
		int caught = 0;
		if (area > 0.0) {
			for (Mob other : mob.level().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(area + 0.5),
				m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy)) {
				if (other.distanceTo(target) <= area + 0.5) {
					caught++;
				}
			}
		}
		o[i++] = ObsM1.clip(caught / 5.0, 0.0, 2.0);
		List<Mob> allies = ObsM1.allies(mob);
		o[i++] = allies.isEmpty() ? 2.0 : ObsM1.clip(mob.distanceTo(allies.get(0)) / 4.0, 0.0, 2.0);

		// --- V: how the player moves
		o[i++] = target.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1;
		o[i++] = target.isFallFlying() ? 1.0 : 0.0;

		// --- E: what the player has in store
		ItemStack guard = forgedOf(off, ForgeType.ESCUDO) ? off : main;
		o[i++] = dev.forja.upgrade.CombatUpgrades.riposteReady(target) ? 1.0 : 0.0;
		o[i++] = target.isBlocking() && dev.forja.upgrade.CombatUpgrades.isPerfectParry(target, target.getUseItem()) ? 1.0 : 0.0;
		o[i++] = dev.forja.forge.Perk.has(guard, dev.forja.forge.Perk.MURALLA) ? 1.0 : 0.0;
		o[i++] = ObsM1.clip(dev.forja.upgrade.Frenzy.level(target), 0.0, 1.0);
		o[i++] = ObsM1.clip(dev.forja.upgrade.Upgrade.anchorShare(dev.forja.upgrade.Upgrades.armorFraction(target, dev.forja.upgrade.Upgrade.ANCLAJE)), 0.0, 1.0);
		boolean pinned = false;
		for (dev.forja.entity.Tongs tongs : mob.level().getEntitiesOfClass(dev.forja.entity.Tongs.class, target.getBoundingBox().inflate(8.0))) {
			if (tongs.holding() == target) {
				pinned = true;
				break;
			}
		}
		o[i++] = pinned ? 1.0 : 0.0;
		boolean eating = target.isUsingItem() && target.getUseItem().has(DataComponents.CONSUMABLE);
		boolean guardDown = off.has(DataComponents.BLOCKS_ATTACKS) && target.getCooldowns().isOnCooldown(off)
			|| main.has(DataComponents.BLOCKS_ATTACKS) && target.getCooldowns().isOnCooldown(main);
		o[i++] = eating || dev.forja.combat.Stamina.value(target) < 25.0F || guardDown ? 1.0 : 0.0;
		o[i++] = dev.forja.combat.Weight.carried(mob) / 10.0;
		o[i++] = dev.forja.combat.Weight.carried(target) / 10.0;
		MobMind self = MobAi.mind(mob);
		o[i++] = self == null ? 1.0 : self.stamina / MobSprint.MAX;
		o[i++] = self != null && self.running ? 1.0 : 0.0;
		int runners = 0;
		for (Mob other : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(16.0),
			m -> m != mob && m.isAlive() && m.getTarget() == target)) {
			MobMind them = MobAi.mind(other);
			runners += them != null && them.running ? 1 : 0;
		}
		o[i++] = ObsM1.clip(runners / 5.0, 0.0, 2.0);
		if (i != o.length) {
			// a name declared and never worked out, or the other way round: the contract would lie
			throw new IllegalStateException("ObsV3 rellena " + i + " de " + o.length + " entradas");
		}
		return o;
	}

	private static boolean forgedOf(ItemStack stack, ForgeType type) {
		dev.forja.part.ForgedParts parts = stack.get(dev.forja.registry.ModComponents.PARTS);
		return parts != null && parts.type() == type;
	}

	/** Something thrown by hand: a projectile item that is not an arrow, or a trident of any make. */
	private static boolean thrown(ItemStack stack) {
		return stack.getItem() instanceof net.minecraft.world.item.ProjectileItem && !(stack.getItem() instanceof net.minecraft.world.item.ArrowItem)
			|| ObsM1.trident(stack);
	}

	/** Ticks until a spell item can be used again: the share of its cooldown left, times the full wait. */
	private static double waitLeft(Player player, ItemStack stack, ForgeType type) {
		return player.getCooldowns().getCooldownPercent(stack, 0.0F) * Spellcasting.cooldown(stack, type);
	}

	/** The radius of the area the player's weapon can make: its special, or a tome's rune; 0 for none. */
	static double areaRadius(ItemStack weapon) {
		if (forgedOf(weapon, ForgeType.ESPADON)) {
			return dev.forja.forge.SpecialAttacks.WHIRL_RANGE;
		}
		if (forgedOf(weapon, ForgeType.MARTILLO) || forgedOf(weapon, ForgeType.MAZO)) {
			return dev.forja.forge.SpecialAttacks.QUAKE_RANGE;
		}
		if (forgedOf(weapon, ForgeType.GUADANA)) {
			return dev.forja.forge.SpecialAttacks.REAP_RANGE;
		}
		if (forgedOf(weapon, ForgeType.GRIMORIO)) {
			return Spellcasting.RUNE_REACH;
		}
		return 0.0;
	}

	/** Whether the weapon strikes an area when it hits: a shockwave or a storm on it, or one of the synergies built on them. */
	static boolean areaOnHit(ItemStack weapon) {
		return dev.forja.upgrade.Upgrades.fraction(weapon, dev.forja.upgrade.Upgrade.ONDA_DE_CHOQUE) > 0.0F
			|| dev.forja.upgrade.Upgrades.fraction(weapon, dev.forja.upgrade.Upgrade.TORMENTA) > 0.0F
			|| dev.forja.upgrade.Synergy.CADENA_DE_RAYOS.active(weapon) || dev.forja.upgrade.Synergy.METEORO.active(weapon)
			|| dev.forja.upgrade.Synergy.VENDAVAL.active(weapon);
	}

	// ---------------------------------------------------------------- projectiles

	/** A projectile on its way: where it is, how it moves, when it gets closest, how close, whose, what. */
	private record Incoming(Vec3 from, Vec3 velocity, int ticks, double miss, int owner, int kind) {
	}

	private static List<Incoming> incoming(Mob mob) {
		AABB box = mob.getBoundingBox();
		List<Incoming> found = new ArrayList<>();
		for (Projectile projectile : mob.level().getEntitiesOfClass(Projectile.class, box.inflate(PROJECTILE_RANGE),
			p -> p.isAlive() && p.getOwner() != mob && p.getDeltaMovement().lengthSqr() > 1.0E-4)) {
			Vec3 at = projectile.position();
			Vec3 v = projectile.getDeltaMovement();
			if (v.dot(box.getCenter().subtract(at)) <= 0.0) {
				continue;
			}
			double gravity = projectile.getGravity();
			double drag = projectile.isNoGravity() && !(projectile instanceof AbstractArrow) ? 1.0 : projectile.isInWater() ? WATER_DRAG : AIR_DRAG;
			double best = distance(box, at);
			int bestTick = 0;
			Vec3 p = at;
			Vec3 w = v;
			for (int t = 1; t <= PROJECTILE_TICKS; t++) {
				p = p.add(w);
				w = new Vec3(w.x * drag, w.y * drag - gravity, w.z * drag);
				double dist = distance(box, p);
				if (dist < best) {
					best = dist;
					bestTick = t;
				}
			}
			if (best > PROJECTILE_MISS) {
				continue;
			}
			var owner = projectile.getOwner();
			int whose = owner instanceof Player ? 1 : owner instanceof Mob ? 2 : 0;
			int kind = projectile instanceof ThrownTrident ? 2 : projectile instanceof AbstractArrow ? 0
				: projectile instanceof dev.forja.entity.MagicBolt || projectile instanceof net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile ? 1 : 2;
			found.add(new Incoming(at, v, bestTick, best, whose, kind));
		}
		found.sort(Comparator.comparingInt(Incoming::ticks));
		return found;
	}

	private static double distance(AABB box, Vec3 p) {
		double dx = Math.max(Math.max(box.minX - p.x, 0.0), p.x - box.maxX);
		double dy = Math.max(Math.max(box.minY - p.y, 0.0), p.y - box.maxY);
		double dz = Math.max(Math.max(box.minZ - p.z, 0.0), p.z - box.maxZ);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	// ---------------------------------------------------------------- areas

	/** An area on the floor: where, how far, how long left, and whether it hurts a monster. */
	private record Zone(Vec3 at, double reach, int ticksLeft, boolean hurts) {
	}

	private static List<Zone> zones(Mob mob, Player target) {
		List<Zone> zones = new ArrayList<>();
		if (!(mob.level() instanceof ServerLevel level)) {
			return zones;
		}
		double range = 24.0;
		for (Spellcasting.Area area : Spellcasting.areas(level)) {
			if (area.at().distanceToSqr(mob.position()) < range * range || area.at().distanceToSqr(target.position()) < range * range) {
				zones.add(new Zone(area.at(), area.reach(), area.ticksLeft(), !area.sparesMonsters()));
			}
		}
		for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class, mob.getBoundingBox().inflate(range))) {
			if (cloud.getOwner() instanceof Player) {
				zones.add(new Zone(cloud.position(), cloud.getRadius(), Math.max(0, cloud.getDuration() - cloud.tickCount), true));
			}
		}
		return zones;
	}

	private static boolean inside(List<Zone> zones, double x, double y, double z) {
		for (Zone zone : zones) {
			if (zone.hurts && Math.abs(y - zone.at.y) <= 2.0 && Math.hypot(x - zone.at.x, z - zone.at.z) <= zone.reach) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the rest

	/** How far its blow reaches: the body's reach and whatever the weapon adds (the executor strikes by the same, {@link Reach}). */
	static double reach(ItemStack held) {
		return Reach.of(held);
	}

	/** How far off the middle of the player's view a point is, flat (radians, 0..pi). */
	private static double offLook(Player player, double x, double z) {
		double yaw = Math.toRadians(player.getYRot());
		double lx = -Math.sin(yaw);
		double lz = Math.cos(yaw);
		double tx = x - player.getX();
		double tz = z - player.getZ();
		double t = Math.max(1.0E-6, Math.hypot(tx, tz));
		return Math.acos(ObsM1.clip((lx * tx + lz * tz) / t, -1.0, 1.0));
	}

	private static double wrap(double a) {
		while (a > Math.PI) {
			a -= 2.0 * Math.PI;
		}
		while (a < -Math.PI) {
			a += 2.0 * Math.PI;
		}
		return a;
	}
}
