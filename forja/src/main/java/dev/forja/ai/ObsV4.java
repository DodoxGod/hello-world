package dev.forja.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.combat.AttackTokens;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A mob's observation for red_mob_v4 (docs/red_mob_v4_contrato.json, docs/red_mob_v4_diseno.md §2): 468 inputs, the
 * 280 of v3b first, in their order and worked out the same way, then 188 new ones in twelve blocks.
 *
 * <p>Step M1 of the design (§6.1; Andy, 2026-09-29): the base, and the blocks S (sectors, 280–295) and R (shortest
 * reach, 296–297), which are made of things the mod already knows, are observed for real. Every other block (M, Mo,
 * P, E, A, O, C, G, L, W) is 0 until the step that brings it (M2 to M6): the simulator's first v4 step (S1) feeds 0 for
 * all of them too, so a network migrated from v3b reads here exactly what it trained on. Where 0 is a misleading
 * "nothing" (an order one-hot with no order set, a morale of 0...), docs/red_mob_v4_neutros.md lists it for the
 * simulator to confirm; no other neutral is made up here.
 *
 * <p>yo_arma_alcance/6 always reads the full reach ("alcance_v": 2, docs/red_mob_v3_1.md), whatever the file says.
 */
public final class ObsV4 {
	/** The "formato" of a v4 mob network, and its sizes (docs/red_mob_v4_contrato.json). */
	public static final String FORMAT = "red_mob_v4";
	public static final int SIZE = 468;
	public static final int BASE = 280;
	/** The yo_arma_alcance a v4 network reads: always the full reach (contract, "alcance_v": 2). */
	public static final int REACH_VERSION = 2;
	/** Where each block starts. */
	public static final int S_AT = 280;
	public static final int R_AT = 296;
	public static final int M_AT = 298;
	public static final int MO_AT = 328;
	public static final int P_AT = 338;
	public static final int E_AT = 366;
	public static final int A_AT = 387;
	public static final int O_AT = 397;
	public static final int C_AT = 417;
	public static final int G_AT = 435;
	public static final int L_AT = 440;
	public static final int W_AT = 452;

	/** How slowly the "front" of a fight follows the player's look: a mean over about a second (red_mob_v4_contrato.propuesta, 280). */
	public static final double FRONT_TICKS = 20.0;
	/** A player whose look nobody has read for this long starts a fresh mean. */
	public static final long FRONT_FORGET = 100;
	/** How close another monster counts as crowding it (aliados_a_1.5/3). */
	public static final double CROWD = 1.5;
	/** How close to the player a squad member counts for the quadrants and for being engaged. */
	public static final double QUADRANT_RANGE = 6.0;
	public static final double ENGAGED_RANGE = 3.5;
	/** How close a lit creeper counts (creeper_encendido_cerca/7), and what is read when none is. */
	public static final double LIT_CREEPER_RANGE = 7.0;
	/** The steps an archer tries for a clear shot: sideways, and back (tiro_lado_libre, tiro_punto_dist/4). */
	public static final double SHOT_STEP = 2.0;
	public static final double SHOT_STEP_FAR = 4.0;

	private static final String[] DIRECTIONS = {"adelante", "adelante_derecha", "derecha", "atras_derecha", "atras",
		"atras_izquierda", "izquierda", "adelante_izquierda"};

	/** The 188 new names, in the contract's order. */
	private static final List<String> NEW = new ArrayList<>();
	private static List<String> all;

	/** The player's look, averaged slowly: x, z of the mean and the tick it was last brought up to date. */
	private static final Map<Player, double[]> LOOK = new WeakHashMap<>();

	static {
		// S: sectors (red_mob_v4_contrato.propuesta, unchanged)
		add("sector_delante", "sector_izquierda", "sector_derecha", "sector_detras", "hueco_error/pi", "hueco_estable/100",
			"aliados_a_1.5/3", "aliados_mi_sector/4", "jug_cuadrantes/4", "jug_enzarzado/4", "creeper_encendido_cerca/7",
			"tiro_lado_libre", "tiro_punto_dist/4", "grupo_n/13", "turno_espera/40", "soy_primero_sector");
		// R: the shortest reach (PROPUESTAS_IA_SIMULADOR.md §8.3)
		add("yo_arma_alcance_min/6", "jug_alcance_min/6");
		// M: command (captain, order, formation, post)
		add("tengo_capitan", "soy_capitan", "sin_mando", "orden_ninguna", "orden_cercar", "orden_carga", "orden_hostigar",
			"orden_retirada", "orden_reagrupar", "orden_emboscada", "orden_asedio", "orden_escolta", "orden_edad/40", "cuenta_atras/40",
			"orden_sector_delante", "orden_sector_derecha", "formacion_libre", "formacion_muro", "formacion_pinza", "formacion_cuna",
			"puesto_frente", "puesto_segunda", "puesto_flanco", "puesto_reserva", "puesto_delante/8", "puesto_derecha/8", "cubierto",
			"capitan_delante/16", "capitan_derecha/16", "capitan_vida_frac");
		// Mo: morale
		add("moral_grupo", "moral_propia", "bajas_frac", "bajas_recientes/5", "aliados_huyendo/5", "aliados_furia/5",
			"furia_disponible", "yo_furia/200", "yo_agotado/100", "retirada_ticks/100");
		// P: perception
		add("obj_percibido", "obj_oido", "obj_edad/100", "ultima_delante/16", "ultima_derecha/16", "ultima_dy/4", "ultima_edad/200");
		for (int k = 0; k < 2; k++) {
			String s = "sonido" + k + "_";
			add(s + "presente", s + "delante/16", s + "derecha/16", s + "dy/4", s + "edad/40", s + "fuerza", s + "movimiento",
				s + "trabajo", s + "comer", s + "combate");
		}
		add("buscando/200");
		// E: ambush, being seen and light
		for (String d : DIRECTIONS) {
			add("oculto_r3_" + d);
		}
		add("escondite_presente", "escondite_delante/8", "escondite_derecha/8", "escondite_luz/15", "escondite_dist_jug/16",
			"me_ve_jugador", "jug_luz/15", "jug_en_pasillo", "jug_en_puerta", "jug_bajo_techo", "jug_sin_vernos/200", "emboscados/5",
			"yo_emboscado/200");
		// A: height and pillar
		add("jug_sobre_suelo/8", "jug_en_pilar", "jug_en_torre", "jug_borde/2", "jug_caida_empuje/8", "jug_alcanzable",
			"pared_trepable", "jug_arriba/200", "jug_tira_desde_arriba", "veo_jug_arriba");
		// O: things on the ground
		for (int k = 0; k < 2; k++) {
			String o = "objeto" + k + "_";
			add(o + "presente", o + "delante/8", o + "derecha/8", o + "dy/4", o + "mejora/10", o + "del_jugador", o + "arma_cuerpo",
				o + "arma_distancia", o + "escudo", o + "consumible");
		}
		// C: consumables and effects
		add("inv_curacion/2", "inv_mejora/2", "inv_arrojadiza/3", "inv_comida/3", "inv_perla/2", "inv_carga_viento/3",
			"consumo_enfriamiento/40", "consumiendo", "yo_ef_fuerza", "yo_ef_velocidad", "yo_ef_regeneracion", "yo_ef_negativo",
			"jug_ef_lentitud", "jug_ef_veneno", "jug_ef_debilidad", "jug_ef_mejora", "perla_destino_ok", "lanzamiento_ok");
		// G: shield
		add("yo_escudo_ticks/20", "jug_golpe_en/10", "jug_golpe_fuerte", "bloqueo_hace/20", "golpe_escudo_listo");
		// L: lights
		for (int k = 0; k < 2; k++) {
			String l = "luz" + k + "_";
			add(l + "presente", l + "delante/8", l + "derecha/8", l + "dy/4", l + "aporte/15", l + "alcanzable");
		}
		// W: the world's vector
		add("mundo_muerte_abierto", "mundo_muerte_flecha", "mundo_muerte_altura", "mundo_muerte_estrecho", "mundo_muerte_trampa",
			"mundo_muerte_area", "mundo_muerte_fuego", "mundo_muerte_otra", "mundo_exito_frente", "mundo_exito_flanco",
			"mundo_exito_distancia", "mundo_exito_emboscada", "mundo_exito_asedio", "mundo_jug_pilar", "mundo_jug_huye", "mundo_confianza");
	}

	private ObsV4() {
	}

	private static void add(String... names) {
		Collections.addAll(NEW, names);
	}

	/** The 468 names, in the contract's order: v3b's 280 (ObsM1, ObsForja, ObsV3) and the 188 new ones. */
	public static synchronized List<String> names() {
		if (all == null) {
			List<String> names = new ArrayList<>(ObsNames.M1);
			names.addAll(ObsForja.names());
			names.addAll(ObsV3.names());
			names.addAll(NEW);
			all = Collections.unmodifiableList(names);
		}
		return all;
	}

	public static int size() {
		return names().size();
	}

	/**
	 * The observation a v4 network is fed: v3b's 280 as ObsForja.full works them out (with the full reach), then S and
	 * R, and 0 for every block not brought in yet. Sets the mind's reachVersion to 2 on the way, so what ObsV3 reads is
	 * the v4 contract's.
	 */
	public static float[] build(Mob mob, Player target, MobMind mind) {
		if (mind != null) {
			mind.reachVersion = REACH_VERSION;
		}
		float[] m1 = ObsM1.of(mob, target, mind == null ? 0 : mind.cooldown, mind == null ? 0 : mind.draw);
		float[] out = ObsForja.full(mob, target, mind, m1, SIZE);
		if (out.length != SIZE) {
			out = java.util.Arrays.copyOf(out, SIZE);
		}
		// v3b's own inputs stop at BASE; anything ObsForja wrote past them (it never should) is not ours to keep
		for (int i = BASE; i < SIZE; i++) {
			out[i] = 0.0F;
		}
		if (mind != null) {
			sectors(mob, target, mind, out, S_AT);
		}
		out[R_AT] = (float) ObsM1.clip(Reach.min(mob) / 6.0, 0.0, 2.0);
		AttackRange range = target.getMainHandItem().get(DataComponents.ATTACK_RANGE);
		out[R_AT + 1] = range == null ? 0.0F : (float) ObsM1.clip(range.minReach() / 6.0, 0.0, 2.0);
		if (mind != null) {
			// M2/M3 (docs/red_mob_v4_mod_estado.md): the player's light, height and pillar, the things on the floor, the
			// kit and the effects, the shield, the torches.
			long now = mob.level().getGameTime();
			out[JUG_LUZ] = Lights.playerLight(target) / 15.0F;
			Heights.observe(mob, target, now, out, A_AT);
			GroundItems.observe(mob, target, out, O_AT);
			MobItems.observe(mob, mind, target, now, out, C_AT);
			ShieldPlay.observe(mob, mind, target, now, out, G_AT);
			Lights.observe(mob, target, out, L_AT);
		}
		return out;
	}

	/** jug_luz/15, the one input of block E that M3 brings (the rest of E comes with M4). */
	public static final int JUG_LUZ = E_AT + 14;

	// ---------------------------------------------------------------- S: sectors

	/** The sixteen of block S (red_mob_v4_contrato.propuesta, inputs 280..295), written from {@code at}. */
	private static void sectors(Mob mob, Player target, MobMind mind, float[] out, int at) {
		long now = mob.level().getGameTime();
		double front = front(target, now);
		List<MobMind> squad = squad(mob, target, mind);
		double mine = slot(mind, target);
		int sector = Double.isNaN(mine) ? -1 : sector(mine, front);
		if (sector >= 0) {
			out[at + sector] = 1.0F;
			out[at + 4] = (float) (Squad.wrap(mine - Squad.angle(mob, target)) / Math.PI);
			out[at + 5] = mind.slotOf == target && !Double.isNaN(mind.slotAngle) && mind.slotSince > Long.MIN_VALUE / 4
				? (float) ObsM1.clip((now - mind.slotSince) / 100.0, 0.0, 2.0) : 0.0F;
		}
		int crowd = 0;
		for (Mob other : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(CROWD),
			m -> m != mob && m.isAlive() && m instanceof Enemy)) {
			crowd += other.distanceTo(mob) < CROWD ? 1 : 0;
		}
		out[at + 6] = (float) ObsM1.clip(crowd / 3.0, 0.0, 2.0);
		int inSector = 0;
		boolean first = sector >= 0;
		double myDistance = mob.distanceToSqr(target);
		boolean[] quadrants = new boolean[4];
		int engaged = 0;
		for (MobMind other : squad) {
			double d = other.mob.distanceTo(target);
			if (d < QUADRANT_RANGE) {
				double a = Squad.angle(other.mob, target);
				quadrants[Math.floorMod((int) Math.floor(a / (Math.PI / 2.0)), 4)] = true;
			}
			engaged += d < ENGAGED_RANGE ? 1 : 0;
			if (other == mind || sector < 0) {
				continue;
			}
			double theirs = slot(other, target);
			if (!Double.isNaN(theirs) && sector(theirs, front) == sector) {
				inSector++;
				if (other.mob.distanceToSqr(target) < myDistance) {
					first = false;
				}
			}
		}
		out[at + 7] = (float) ObsM1.clip(inSector / 4.0, 0.0, 2.0);
		int covered = 0;
		for (boolean q : quadrants) {
			covered += q ? 1 : 0;
		}
		out[at + 8] = covered / 4.0F;
		out[at + 9] = (float) ObsM1.clip(engaged / 4.0, 0.0, 2.0);
		double lit = Double.MAX_VALUE;
		for (Creeper creeper : mob.level().getEntitiesOfClass(Creeper.class, mob.getBoundingBox().inflate(LIT_CREEPER_RANGE),
			c -> c != mob && c.isAlive() && c.getSwellDir() > 0)) {
			lit = Math.min(lit, creeper.distanceTo(mob));
		}
		out[at + 10] = lit < LIT_CREEPER_RANGE ? (float) (lit / LIT_CREEPER_RANGE) : 2.0F;
		if (MobFamily.of(mob) == MobFamily.ARQUERO && Squad.allyInLineOf(mob, target) != null) {
			double[] shot = clearShot(mob, target);
			out[at + 11] = (float) shot[0];
			out[at + 12] = (float) shot[1];
		}
		out[at + 13] = (float) ObsM1.clip(squad.size() / 13.0, 0.0, 2.0);
		boolean waiting = mind.othersWaiting && !AttackTokens.holds(target, mob);
		if (!waiting || mind.waitingSince <= Long.MIN_VALUE / 4) {
			mind.waitingSince = now;
		}
		out[at + 14] = (float) ObsM1.clip((now - mind.waitingSince) / 40.0, 0.0, 2.0);
		out[at + 15] = first ? 1.0F : 0.0F;
	}

	/**
	 * The "front" of the fight: the player's look, averaged over about a second (the proposal's "media lenta (1 s) de
	 * la mirada del jugador"), as an angle in {@link Squad#angle}'s convention. Brought up to date at most once a tick,
	 * however many mobs ask.
	 */
	static double front(Player player, long now) {
		double facing = Squad.facing(player);
		double lx = Math.cos(facing);
		double lz = Math.sin(facing);
		double[] mean = LOOK.get(player);
		if (mean == null || now - (long) mean[2] > FRONT_FORGET || now < (long) mean[2]) {
			mean = new double[] {lx, lz, now};
			LOOK.put(player, mean);
		} else if (now > (long) mean[2]) {
			double share = 1.0 - Math.exp(-(now - mean[2]) / FRONT_TICKS);
			mean[0] += (lx - mean[0]) * share;
			mean[1] += (lz - mean[1]) * share;
			mean[2] = now;
		}
		return Math.atan2(mean[1], mean[0]);
	}

	/** 0 in front (under 45° off the front), 1 left, 2 right (45° to 135°), 3 behind (over 135°). */
	static int sector(double slot, double front) {
		double off = Squad.wrap(slot - front);
		double a = Math.abs(off);
		if (a < Math.PI / 4.0) {
			return 0;
		}
		if (a > 3.0 * Math.PI / 4.0) {
			return 3;
		}
		// Squad.angle's convention: turning by +90° from a direction gives the frame's "derecha" (-z, x)
		return off > 0.0 ? 2 : 1;
	}

	/**
	 * The mob's slot on the ring, as the Squad handed it out; for an archer or a creeper, which take no slot on the
	 * ring, the "slot" the Squad gives them where they already stand (docs/red_mob_v4_propuesta.md). NaN for none.
	 */
	private static double slot(MobMind mind, Player target) {
		if (mind.slotOf == target && !Double.isNaN(mind.slotAngle)) {
			return mind.slotAngle;
		}
		if (mind.target == target && !Squad.onRing(mind.mob) && !Double.isNaN(mind.ringAngle)) {
			return mind.ringAngle;
		}
		return Double.NaN;
	}

	/** Every living mob fighting this player in this level (the Squad's group for them), this one included. */
	private static List<MobMind> squad(Mob mob, Player target, MobMind mind) {
		List<MobMind> squad = new ArrayList<>();
		for (MobMind other : MobAi.minds()) {
			if (other.target == target && other.mob.isAlive() && other.mob.level() == mob.level()) {
				squad.add(other);
			}
		}
		if (!squad.contains(mind)) {
			squad.add(mind);
		}
		return squad;
	}

	/**
	 * tiro_lado_libre and tiro_punto_dist/4 for an archer whose line is blocked by a friend: -1 / +1 if two blocks to
	 * its left / right the line is clear (left tried first), 0 if neither; and the distance to the nearest clear spot
	 * of ±2 sideways, 2 back and ±4 sideways, over 4, or 2 for none.
	 */
	private static double[] clearShot(Mob mob, Player target) {
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double ux = dx / d;
		double uz = dz / d;
		double rx = -uz;
		double rz = ux;
		Vec3 feet = mob.position();
		double side = 0.0;
		if (clear(mob, feet.add(-rx * SHOT_STEP, 0.0, -rz * SHOT_STEP), target)) {
			side = -1.0;
		} else if (clear(mob, feet.add(rx * SHOT_STEP, 0.0, rz * SHOT_STEP), target)) {
			side = 1.0;
		}
		double distance = 2.0;
		if (side != 0.0 || clear(mob, feet.add(-ux * SHOT_STEP, 0.0, -uz * SHOT_STEP), target)) {
			distance = SHOT_STEP / 4.0;
		} else if (clear(mob, feet.add(-rx * SHOT_STEP_FAR, 0.0, -rz * SHOT_STEP_FAR), target)
			|| clear(mob, feet.add(rx * SHOT_STEP_FAR, 0.0, rz * SHOT_STEP_FAR), target)) {
			distance = SHOT_STEP_FAR / 4.0;
		}
		return new double[] {side, distance};
	}

	/** Whether a shot from the mob's eyes, were it standing at {@code feet}, reaches the player's eyes: no friend, no block. */
	private static boolean clear(Mob mob, Vec3 feet, Player target) {
		Vec3 eye = feet.add(0.0, mob.getEyeHeight(), 0.0);
		if (Squad.allyInLineFrom(mob, eye, target) != null) {
			return false;
		}
		HitResult hit = mob.level().clip(new ClipContext(eye, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
		return hit.getType() == HitResult.Type.MISS;
	}
}
