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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
		out.put("tactica_relevo", "fuera del alcance del jugador: a " + TacticGoal.RELAY_RADIUS + " bloques, girando " + TacticGoal.RELAY_SWING
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
		return o;
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

	/** How far its blow reaches: the body's reach and whatever the weapon adds. */
	static double reach(ItemStack held) {
		double extra = 0.0;
		var modifiers = held.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers != null) {
			for (var entry : modifiers.modifiers()) {
				if (entry.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE
					&& entry.slot().test(EquipmentSlot.MAINHAND)) {
					extra += entry.modifier().amount();
				}
			}
		}
		var range = held.get(DataComponents.ATTACK_RANGE);
		if (range != null) {
			extra += Math.max(0.0, range.maxReach() - 3.0);
		}
		return ObsM1.REACH + extra;
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
