package dev.forja.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import dev.forja.combat.AttackTokens;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.ForjaDifficulty;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The captain's brain (docs/red_mob_v4_diseno.md §3.2–§3.4): red_capitan.json from redes_v4 ("formato":
 * "red_capitan_v4", NetBrain's shape with a GRU of its own) when there is one that fits, the rules otherwise
 * (Captain.rules). Its observation is 213 numbers ({@link #names()}; docs/red_capitan_v4_contrato.json), its outputs 60.
 *
 * <p>Outputs: orden 0–8, formacion 9–12, sector 13–21, cuenta 22–25, foco 26–27, puesto_k (k = 0..7) 28–59, four each.
 * Each head is sampled on its own under the mask: foco 1 only with another player within 16 of the captain; a post of a
 * member that is not there is left at 0; ASEDIO only with the player up high (as the mob's ASEDIAR). Members 9 and on
 * take their post by kind (Captain.postByKind), as the design says.
 *
 * <p><b>Contract revision 2</b> (captain 2, docs/red_capitan_v4_contrato_v2.json, docs/mod_spec_capitan2.md §6): a file
 * with "contrato_version": 2 has 253 inputs ({@link #namesV2()}: v1's 213, then the order the rules would give now and
 * the captain-2 state) and 68 outputs ({@link #outputsV2()}: 12 orders, then the same heads, a protection head and the
 * mando head). It is residual: mando = 1 only when logit 67 > logit 66, with no temperature and no draw; with mando 0 the
 * group gets the rules' order as it is (posts by kind, protection by the rules), so a network fresh from its start (mando
 * rows at 0, bias (+6, 0)) plays exactly as the rules captain. A v1 file still loads and runs as before.
 */
public final class CaptainBrain {
	public static final String FORMAT = "red_capitan_v4";
	public static final int OUTPUTS = 60;
	public static final int ORDER_AT = 0;
	public static final int FORMATION_AT = 9;
	public static final int SECTOR_AT = 13;
	public static final int COUNT_AT = 22;
	public static final int FOCUS_AT = 26;
	public static final int POSTS_AT = 28;
	/** Where each block of the observation starts. */
	public static final int J_AT = 0;
	public static final int G_AT = 38;
	public static final int K_AT = 63;
	public static final int K_EACH = 15;
	public static final int W_AT = 183;
	public static final int O_AT = 199;
	public static final int SIZE = 213;

	/** Contract revision 2: its outputs, and where its heads start. */
	public static final int OUTPUTS_V2 = 68;
	public static final int FORMATION_AT_V2 = 12;
	public static final int SECTOR_AT_V2 = 16;
	public static final int COUNT_AT_V2 = 25;
	public static final int FOCUS_AT_V2 = 29;
	public static final int POSTS_AT_V2 = 31;
	public static final int PROTECTION_AT = 63;
	public static final int MANDO_AT = 66;
	/** Contract revision 2: its inputs, and where the new ones start (the rules' order, then the captain-2 state). */
	public static final int SIZE_V2 = 253;
	public static final int RULE_AT = 213;
	public static final int C2_AT = 233;

	/** Where each head of a captain network's outputs starts, and how many orders it has. */
	record Layout(int orders, int formationAt, int sectorAt, int countAt, int focusAt, int postsAt, int outputs) {
	}

	static final Layout V1 = new Layout(Captain.Order.V1, FORMATION_AT, SECTOR_AT, COUNT_AT, FOCUS_AT, POSTS_AT, OUTPUTS);
	static final Layout V2 = new Layout(Captain.Order.values().length, FORMATION_AT_V2, SECTOR_AT_V2, COUNT_AT_V2, FOCUS_AT_V2, POSTS_AT_V2, OUTPUTS_V2);

	private static List<String> names;
	private static List<String> namesV2;

	private CaptainBrain() {
	}

	/** Whether groups get captains at all (CombatConfig.iaCapitan). */
	public static boolean enabled() {
		return CombatConfig.get().enabled && CombatConfig.get().iaCapitan;
	}

	/**
	 * How a group without a captain network is led: no captain at all (SIN_CAPITAN), a captain that gives no orders and
	 * leaves its members free (LIBRE: the ring and the turns as without one; its banner, its morale and its fall still
	 * count), or the rules captain (REGLAS, Captain.rules). A loaded captain network gives the orders whatever this says.
	 */
	public enum Mode { SIN_CAPITAN, LIBRE, REGLAS }

	/**
	 * The pieces of captain 2 (docs/mod_spec_capitan2.md), each with its switch in CombatConfig: shared vision
	 * (iaCapitanVision), succession (iaCapitanSucesion), protection (iaCapitanProteccion), the new orders
	 * (iaCapitanOrdenes2) and the visible captain (iaCapitanVisible).
	 */
	public enum Piece { VISION, SUCESION, PROTECCION, ORDENES2, VISIBLE }

	/** Per player overrides of the mode (the measurement tests pit the modes against each other at once). */
	private static final java.util.Map<Player, Mode> OVERRIDES = new java.util.WeakHashMap<>();
	/** Per player overrides of captain 2's pieces: the ones on (the rest off), over the config. */
	private static final java.util.Map<Player, EnumSet<Piece>> PIECES = new java.util.WeakHashMap<>();

	/** Sets (or with null clears) the mode for the group fighting this player, over the config. For tests. */
	public static void override(Player player, Mode mode) {
		if (mode == null) {
			OVERRIDES.remove(player);
		} else {
			OVERRIDES.put(player, mode);
		}
	}

	/** Sets (or with null clears) which of captain 2's pieces are on for the group fighting this player. For tests. */
	public static void overridePieces(Player player, EnumSet<Piece> on) {
		if (on == null) {
			PIECES.remove(player);
		} else {
			PIECES.put(player, EnumSet.copyOf(on));
		}
	}

	/** Whether a piece of captain 2 is on for the group fighting this player: its override, else the config. */
	public static boolean piece(Player player, Piece piece) {
		EnumSet<Piece> forced = player == null ? null : PIECES.get(Perception.real(player));
		if (forced != null) {
			return forced.contains(piece);
		}
		CombatConfig cfg = CombatConfig.get();
		return switch (piece) {
			case VISION -> cfg.iaCapitanVision;
			case SUCESION -> cfg.iaCapitanSucesion;
			case PROTECCION -> cfg.iaCapitanProteccion;
			case ORDENES2 -> cfg.iaCapitanOrdenes2;
			case VISIBLE -> cfg.iaCapitanVisible;
		};
	}

	/** The mode for the group fighting this player: its override, else the config (iaCapitan, iaCapitanReglas). */
	public static Mode mode(Player player) {
		Mode forced = player == null ? null : OVERRIDES.get(player);
		if (forced != null) {
			return forced;
		}
		if (!enabled()) {
			return Mode.SIN_CAPITAN;
		}
		return CombatConfig.get().iaCapitanReglas ? Mode.REGLAS : Mode.LIBRE;
	}

	/** Whether the group fighting this player gets a captain. */
	static boolean enabled(Player player) {
		return CombatConfig.get().enabled && mode(player) != Mode.SIN_CAPITAN;
	}

	private static String lower(Enum<?> e) {
		return e.name().toLowerCase(java.util.Locale.ROOT);
	}

	/** The 213 names, in order (docs/red_capitan_v4_contrato.json). */
	public static synchronized List<String> names() {
		if (names != null) {
			return names;
		}
		List<String> n = new ArrayList<>();
		// J: the player (38)
		Collections.addAll(n, "jug_dist_grupo/16", "jug_vida/20", "jug_escudo_arriba");
		for (String hand : new String[] {"espada", "hacha", "arco", "ballesta", "baculo", "grimorio", "arrojadiza", "otro"}) {
			n.add("jug_mano_" + hand);
		}
		Collections.addAll(n, "jug_alcance/6", "jug_area_radio/6", "jug_estamina/100", "jug_presion", "jug_equipo", "jug_peso/10",
			"hab_parada", "hab_esquiva", "hab_bloqueo", "hab_lado", "hab_distancia/8", "hab_carga", "hab_nivel",
			"dif_aprendiz", "dif_herrero", "dif_maestro", "dif_leyenda",
			"jug_sobre_suelo/8", "jug_en_pilar", "jug_en_torre", "jug_en_pasillo", "jug_luz/15", "noche",
			"jug_percibido", "jug_edad/100", "jug_arriba/200", "jug_de_espaldas_grupo");
		// G: the group (25)
		Collections.addAll(n, "grupo_n/13", "fam_cuerpo/5", "fam_arquero/5", "fam_creeper/5", "fam_arana/5", "fam_otro/5",
			"vida_media", "moral_grupo", "bajas_frac", "bajas_recientes/5",
			"rodeado", "cobertura/360", "jug_enzarzado/4", "turnos_ocupados/4", "arqueros_con_linea/4", "creepers_listos/2",
			"especiales_listos/8", "consumibles/8", "ocultos/13", "en_puesto_frac", "huyendo/13", "furia/13",
			"capitan_vida_frac", "capitan_dist_jug/16", "tiempo/1200");
		// K: 8 members (15 each), nearest the player first, in the frame captain → player
		for (int k = 0; k < Captain.MEMBERS; k++) {
			String m = "miembro" + k + "_";
			Collections.addAll(n, m + "presente", m + "delante/16", m + "derecha/16", m + "dist_jug/16", m + "g_cuerpo", m + "g_arquero",
				m + "g_creeper", m + "g_arana", m + "g_otro", m + "vida_frac", m + "escudo", m + "velocidad/0.3", m + "oculto", m + "en_puesto",
				m + "turno");
		}
		// W: the world's vector, as the mob's (16)
		n.addAll(ObsV4.names().subList(ObsV4.W_AT, ObsV4.W_AT + 16));
		// O: the order in force (14): v1's nine orders (the new ones read as the members see them)
		for (int i = 0; i < Captain.Order.V1; i++) {
			n.add("orden_" + lower(Captain.Order.values()[i]));
		}
		n.add("orden_edad/40");
		for (Captain.Formation f : Captain.Formation.values()) {
			n.add("formacion_" + lower(f));
		}
		names = Collections.unmodifiableList(n);
		return names;
	}

	/** The 253 names of contract revision 2, in order (docs/red_capitan_v4_contrato_v2.json). */
	public static synchronized List<String> namesV2() {
		if (namesV2 != null) {
			return namesV2;
		}
		List<String> n = new ArrayList<>(names());
		// 213..232: the order Captain.rules() would give now (with an acting captain, CARGA → none)
		for (Captain.Order o : Captain.Order.values()) {
			n.add("regla_orden_" + lower(o));
		}
		for (Captain.Formation f : Captain.Formation.values()) {
			n.add("regla_formacion_" + lower(f));
		}
		for (int c : Captain.COUNTS) {
			n.add("regla_cuenta_" + c);
		}
		// 233..252: captain 2's state
		Collections.addAll(n, "orden_cerrar_salidas", "orden_foco_herido", "orden_retirada_falsa", "falsa_ataque", "capitan_interino",
			"sin_mando", "capitan_tras_frente/8", "escoltas/2", "jug_mira_capitan", "capitan_golpeado/3", "compartida/8", "jug_aleja/0.2",
			"salidas_libres/8", "otro_jug_presente", "otro_vida_frac", "otro_dist/16", "mi_jug_vida_frac", "proteccion_regla",
			"proteccion_sin", "proteccion_retirada");
		namesV2 = Collections.unmodifiableList(n);
		return namesV2;
	}

	/** The output names of contract v1, in order. */
	public static List<String> outputs() {
		return outputs(V1);
	}

	/** The output names of contract revision 2, in order. */
	public static List<String> outputsV2() {
		return outputs(V2);
	}

	private static List<String> outputs(Layout layout) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < layout.orders(); i++) {
			out.add("orden_" + lower(Captain.Order.values()[i]));
		}
		for (Captain.Formation f : Captain.Formation.values()) {
			out.add("formacion_" + lower(f));
		}
		for (int s = 0; s <= 8; s++) {
			out.add("sector_" + s);
		}
		for (int c : Captain.COUNTS) {
			out.add("cuenta_" + c);
		}
		out.add("foco_mio");
		out.add("foco_otro");
		for (int k = 0; k < Captain.MEMBERS; k++) {
			for (String post : new String[] {"frente", "segunda", "flanco", "reserva"}) {
				out.add("puesto" + k + "_" + post);
			}
		}
		if (layout == V2) {
			Collections.addAll(out, "proteccion_regla", "proteccion_sin", "proteccion_retirada", "mando_regla", "mando_red");
		}
		return out;
	}

	/**
	 * Why a network cannot be the captain, or null: formato, then by its "contrato_version" either v1's 213 names in
	 * order and 60 outputs, or revision 2's 253 and 68.
	 */
	public static String check(NetBrain net) {
		if (net.hasNonFinite()) {
			return net.nonFiniteProblem();
		}
		if (!FORMAT.equals(net.format)) {
			return "formato '" + net.format + "': no es " + FORMAT;
		}
		boolean v2 = net.contractVersion >= 2;
		if (net.contractVersion > 2) {
			return "contrato_version " + net.contractVersion + ": este mod lee la 1 y la 2";
		}
		List<String> ours = v2 ? namesV2() : names();
		String which = v2 ? "el capitán v4 (contrato 2)" : "el capitán v4";
		if (net.inputs() != ours.size() || net.names.size() != ours.size()) {
			return "espera " + net.inputs() + " entradas con " + net.names.size() + " nombres; " + which + " tiene " + ours.size();
		}
		for (int i = 0; i < ours.size(); i++) {
			if (!ours.get(i).equals(net.names.get(i))) {
				return "la entrada " + i + " es '" + net.names.get(i) + "' y el mod da '" + ours.get(i) + "'";
			}
		}
		int outputs = v2 ? OUTPUTS_V2 : OUTPUTS;
		return net.outputs() != outputs ? "da " + net.outputs() + " salidas y " + which + " tiene " + outputs : null;
	}

	/**
	 * The rules' order now, as a captain of this group may give it: Captain.rules, with an acting captain's CARGA turned
	 * into no order (the formation the rules say). The posts are by kind.
	 */
	public static Captain.Command ruleOrder(Captain.Group g, Player player, long now) {
		Captain.Command c = Captain.rules(g, player, now);
		if (g.interim && c.order == Captain.Order.CARGA) {
			c.order = Captain.Order.NINGUNA;
			c.count = 0;
		}
		c.posts = Captain.rulesPosts(g);
		return c;
	}

	/** The group's orders this pass: from the captain's network when there is one, else the rules. */
	public static Captain.Command decide(Captain.Group g, Player player, long now) {
		NetBrain net = MobAi.captainNet();
		g.mando = false;
		if (net == null) {
			// No network: the rules captain when it is switched on (iaCapitanReglas); otherwise no orders, and the members
			// fight free, as without a captain.
			g.protection = Captain.PROTECT_RULES;
			if (mode(player) == Mode.REGLAS) {
				return ruleOrder(g, player, now);
			}
			Captain.Command c = new Captain.Command();
			c.posts = Captain.rulesPosts(g);
			return c;
		}
		if (g.memory == null || g.memory.length != net.memory) {
			g.memory = new float[net.memory];
		}
		boolean v2 = net.contractVersion >= 2;
		Captain.Command rules = null;
		float[] obs;
		if (v2) {
			rules = ruleOrder(g, player, now);
			g.rulesCommand = rules;
			obs = observeV2(g, player, now, rules);
		} else {
			obs = observe(g, player, now);
		}
		float[] logits = net.forward(obs, g.memory);
		if (!NetBrain.finiteLogits(logits)) {
			// Never sample from NaN: the rules' order this pass (20 passes switch the network off).
			g.memory = new float[net.memory];
			MobAi.badLogits(net, MobAi.V4_CAPTAIN);
			g.protection = Captain.PROTECT_RULES;
			return ruleOrder(g, player, now);
		}
		if (v2 && !(logits[MANDO_AT + 1] > logits[MANDO_AT])) {
			// mando 0 (argmax, no temperature, no draw): the rules' order as it is, and protection by the rules
			g.protection = Captain.PROTECT_RULES;
			return rules;
		}
		g.mando = v2;
		Layout layout = v2 ? V2 : V1;
		MobMind captain = MobAi.mind(g.captain);
		RandomSource random = captain != null ? captain.random : g.captain.getRandom();
		double temperature = CombatConfig.get().iaTemperatura * ForjaDifficulty.current().temperature;
		boolean[] mask = mask(g, player, now, layout);
		Captain.Command c = new Captain.Command();
		c.order = Captain.Order.values()[pick(logits, 0, layout.orders(), temperature, random, mask)];
		c.formation = Captain.Formation.values()[pick(logits, layout.formationAt(), 4, temperature, random, mask)];
		c.sector = pick(logits, layout.sectorAt(), 9, temperature, random, mask);
		c.count = pick(logits, layout.countAt(), 4, temperature, random, mask);
		c.focus = pick(logits, layout.focusAt(), 2, temperature, random, mask);
		c.posts = new int[g.members.size()];
		for (int i = 0; i < c.posts.length; i++) {
			c.posts[i] = i < Captain.MEMBERS ? pick(logits, layout.postsAt() + 4 * i, 4, temperature, random, mask) : Captain.postByKind(g.members.get(i).mob);
		}
		g.protection = v2 ? pick(logits, PROTECTION_AT, 3, temperature, random, mask) : Captain.PROTECT_RULES;
		return c;
	}

	/** Contract v1's mask (see {@link #mask(Captain.Group, Player, long, Layout)}). */
	static boolean[] mask(Captain.Group g, Player player) {
		return mask(g, player, g.captain.level().getGameTime(), V1);
	}

	/**
	 * The captain's mask: ASEDIO with the player up high, focus on another player only with one about, posts of members
	 * there; never CARGA for an acting captain. Revision 2 adds: CERRAR_SALIDAS with 2 or more that fight up close (the
	 * captain counted), FOCO_HERIDO with a more hurt player about, RETIRADA_FALSA with 3 or more up close and the group's
	 * morale at 0.5 or more (all three with the piece "ordenes" and never for an acting captain); protection 1 and 2 only
	 * with the piece "proteccion"; mando always both. Index 0 of a head is never forbidden.
	 */
	static boolean[] mask(Captain.Group g, Player player, long now, Layout layout) {
		boolean[] mask = new boolean[layout.outputs()];
		java.util.Arrays.fill(mask, true);
		mask[Captain.Order.ASEDIO.ordinal()] = Heights.besieged(player);
		mask[Captain.Order.CARGA.ordinal()] = !g.interim;
		boolean other = false;
		for (Player p : g.captain.level().players()) {
			other |= p != Perception.real(player) && p.isAlive() && !p.isSpectator() && p.distanceToSqr(g.captain) < Captain.FOCUS_RANGE * Captain.FOCUS_RANGE;
		}
		mask[layout.focusAt() + 1] = other;
		for (int k = g.members.size(); k < Captain.MEMBERS; k++) {
			for (int j = 1; j < 4; j++) {
				mask[layout.postsAt() + 4 * k + j] = false;
			}
		}
		if (layout == V2) {
			boolean orders = piece(player, Piece.ORDENES2) && !g.interim;
			int melee = Captain.meleeCount(g);
			MobMind cm = MobAi.mind(g.captain);
			double morale = cm == null ? 1.0 : Captain.groupMorale(cm, player, now);
			mask[Captain.Order.CERRAR_SALIDAS.ordinal()] = orders && melee >= 2;
			mask[Captain.Order.FOCO_HERIDO.ordinal()] = orders && Captain.hurtOther(g, player) != null;
			mask[Captain.Order.RETIRADA_FALSA.ordinal()] = orders && melee >= 3 && morale >= 0.5;
			boolean guard = piece(player, Piece.PROTECCION);
			mask[PROTECTION_AT + Captain.PROTECT_NONE] = guard;
			mask[PROTECTION_AT + Captain.PROTECT_RETREAT] = guard;
		}
		return mask;
	}

	private static int pick(float[] logits, int at, int n, double temperature, RandomSource random, boolean[] mask) {
		double t = Math.max(0.05, temperature);
		double max = Double.NEGATIVE_INFINITY;
		for (int k = 0; k < n; k++) {
			if (k == 0 || mask[at + k]) {
				max = Math.max(max, logits[at + k] / t);
			}
		}
		double[] p = new double[n];
		double sum = 0.0;
		for (int k = 0; k < n; k++) {
			p[k] = k == 0 || mask[at + k] ? Math.exp(logits[at + k] / t - max) : 0.0;
			sum += p[k];
		}
		double roll = random.nextDouble() * sum;
		for (int k = 0; k < n; k++) {
			roll -= p[k];
			if (roll <= 0.0) {
				return k;
			}
		}
		return 0;
	}

	/** The 213 inputs (see {@link #names()}; the meanings are in docs/red_capitan_v4_contrato.json). */
	public static float[] observe(Captain.Group g, Player player, long now) {
		float[] out = new float[SIZE];
		Mob captain = g.captain;
		MobMind cm = MobAi.mind(captain);
		List<String> mob = ObsV4.names();
		// J: most of it is what the captain's own v4 observation says of the player
		float[] own = cm == null ? new float[ObsV4.SIZE] : ObsV4.build(captain, player, cm);
		double cx = 0.0;
		double cz = 0.0;
		boolean perceived = false;
		long lastSeen = Long.MIN_VALUE / 2;
		for (MobMind m : g.members) {
			cx += m.mob.getX();
			cz += m.mob.getZ();
			perceived |= Perception.perceived(m, now);
			lastSeen = Math.max(lastSeen, m.lastSeenAt);
		}
		int n = Math.max(1, g.members.size());
		cx /= n;
		cz /= n;
		out[0] = (float) ObsM1.clip(Math.hypot(player.getX() - cx, player.getZ() - cz) / 16.0, 0.0, 2.0);
		out[1] = own[mob.indexOf("obj_vida/20")];
		out[2] = own[mob.indexOf("obj_escudo_arriba")];
		String[] hands = {"obj_mano_espada", "obj_mano_hacha", "obj_mano_arco", "obj_mano_ballesta", "obj_mano_baculo", "obj_mano_grimorio",
			"obj_mano_arrojadiza"};
		float any = 0.0F;
		for (int i = 0; i < hands.length; i++) {
			out[3 + i] = own[mob.indexOf(hands[i])];
			any += out[3 + i];
		}
		out[10] = any > 0.0F ? 0.0F : 1.0F;
		String[] copied = {"jug_alcance/6", "jug_area_radio/6", "jug_estamina/100", "jug_presion", "jug_equipo", "jug_peso/10", "hab_parada",
			"hab_esquiva", "hab_bloqueo", "hab_lado", "hab_distancia/8", "hab_carga", "hab_nivel", "dif_aprendiz", "dif_herrero", "dif_maestro",
			"dif_leyenda", "jug_sobre_suelo/8", "jug_en_pilar", "jug_en_torre", "jug_en_pasillo", "jug_luz/15", "noche"};
		for (int i = 0; i < copied.length; i++) {
			out[11 + i] = own[mob.indexOf(copied[i])];
		}
		out[34] = perceived ? 1.0F : 0.0F;
		out[35] = perceived ? 0.0F : (float) ObsM1.clip((now - lastSeen) / 100.0, 0.0, 2.0);
		out[36] = own[mob.indexOf("jug_arriba/200")];
		double look = Squad.facing(Perception.real(player));
		double toGroup = Math.atan2(cz - player.getZ(), cx - player.getX());
		out[37] = Math.abs(Squad.wrap(look - toGroup)) > Math.PI * 0.5 ? 1.0F : 0.0F;
		// G
		int[] fam = new int[5];
		double health = 0.0;
		int archersClear = 0;
		int creepersReady = 0;
		int specialsReady = 0;
		int carrying = 0;
		int hidden = 0;
		int inPost = 0;
		int withPost = 0;
		int running = 0;
		int raging = 0;
		boolean[] quadrants = new boolean[4];
		List<Double> angles = new ArrayList<>();
		for (MobMind m : g.members) {
			Mob mm = m.mob;
			fam[familyIndex(mm)]++;
			health += mm.getHealth() / Math.max(1.0F, mm.getMaxHealth());
			if (MobFamily.of(mm) == MobFamily.ARQUERO && Squad.allyInLineOf(mm, player) == null) {
				archersClear++;
			}
			if (MobFamily.of(mm) == MobFamily.CREEPER && mm.distanceTo(player) < 3.0) {
				creepersReady++;
			}
			if (m.specials != null) {
				for (int k = 0; k < m.specials.moveset().size(); k++) {
					if (m.specials.available(k, player)) {
						specialsReady++;
						break;
					}
				}
			}
			carrying += MobKit.entries(mm).isEmpty() ? 0 : 1;
			hidden += Perception.seenBy(mm, player) ? 0 : 1;
			if (m.postPoint != null) {
				withPost++;
				inPost += mm.distanceToSqr(m.postPoint.x, mm.getY(), m.postPoint.z) < Captain.IN_POST * Captain.IN_POST ? 1 : 0;
			}
			running += m.retreatSince > Long.MIN_VALUE / 4 && now - m.retreatSince >= 40 ? 1 : 0;
			raging += m.furyActive(now) ? 1 : 0;
			double d = mm.distanceTo(player);
			if (d < ObsV4.QUADRANT_RANGE) {
				double a = Squad.angle(mm, player);
				quadrants[Math.floorMod((int) Math.floor(a / (Math.PI / 2.0)), 4)] = true;
				angles.add(a);
			}
		}
		out[G_AT] = (float) ObsM1.clip(g.members.size() / 13.0, 0.0, 2.0);
		for (int i = 0; i < 5; i++) {
			out[G_AT + 1 + i] = (float) ObsM1.clip(fam[i] / 5.0, 0.0, 2.0);
		}
		out[G_AT + 6] = (float) (health / n);
		out[G_AT + 7] = cm == null ? 1.0F : (float) Captain.groupMorale(cm, player, now);
		out[G_AT + 8] = g.peak <= 0 ? 0.0F : (float) Math.min(1.0, g.dead / (double) g.peak);
		int recent = 0;
		for (long t : g.deaths) {
			recent += now - t <= Captain.RECENT ? 1 : 0;
		}
		out[G_AT + 9] = (float) ObsM1.clip(recent / 5.0, 0.0, 2.0);
		int quads = 0;
		for (boolean q : quadrants) {
			quads += q ? 1 : 0;
		}
		out[G_AT + 10] = quads >= 3 ? 1.0F : 0.0F;
		out[G_AT + 11] = (float) (coverage(angles) / (2.0 * Math.PI));
		out[G_AT + 12] = own[mob.indexOf("jug_enzarzado/4")];
		out[G_AT + 13] = (float) ObsM1.clip(AttackTokens.held(Perception.real(player)) / 4.0, 0.0, 2.0);
		out[G_AT + 14] = (float) ObsM1.clip(archersClear / 4.0, 0.0, 2.0);
		out[G_AT + 15] = (float) ObsM1.clip(creepersReady / 2.0, 0.0, 2.0);
		out[G_AT + 16] = (float) ObsM1.clip(specialsReady / 8.0, 0.0, 2.0);
		out[G_AT + 17] = (float) ObsM1.clip(carrying / 8.0, 0.0, 2.0);
		out[G_AT + 18] = (float) ObsM1.clip(hidden / 13.0, 0.0, 2.0);
		out[G_AT + 19] = withPost == 0 ? 0.0F : inPost / (float) withPost;
		out[G_AT + 20] = (float) ObsM1.clip(running / 13.0, 0.0, 2.0);
		out[G_AT + 21] = (float) ObsM1.clip(raging / 13.0, 0.0, 2.0);
		out[G_AT + 22] = captain.getHealth() / Math.max(1.0F, captain.getMaxHealth());
		out[G_AT + 23] = (float) ObsM1.clip(captain.distanceTo(player) / 16.0, 0.0, 2.0);
		out[G_AT + 24] = (float) ObsM1.clip((now - g.startedAt) / 1200.0, 0.0, 2.0);
		// K: the frame captain → player
		double dx = player.getX() - captain.getX();
		double dz = player.getZ() - captain.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double fx = dx / d;
		double fz = dz / d;
		for (int k = 0; k < Captain.MEMBERS && k < g.members.size(); k++) {
			MobMind m = g.members.get(k);
			Mob mm = m.mob;
			int i = K_AT + k * K_EACH;
			double ox = mm.getX() - captain.getX();
			double oz = mm.getZ() - captain.getZ();
			out[i] = 1.0F;
			out[i + 1] = (float) ObsM1.clip((ox * fx + oz * fz) / 16.0, -2.0, 2.0);
			out[i + 2] = (float) ObsM1.clip((ox * -fz + oz * fx) / 16.0, -2.0, 2.0);
			out[i + 3] = (float) ObsM1.clip(mm.distanceTo(player) / 16.0, 0.0, 2.0);
			out[i + 4 + familyIndex(mm)] = 1.0F;
			out[i + 9] = mm.getHealth() / Math.max(1.0F, mm.getMaxHealth());
			out[i + 10] = MobDefense.hasShield(mm) ? 1.0F : 0.0F;
			out[i + 11] = (float) ObsM1.clip(mm.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.3, 0.0, 2.0);
			out[i + 12] = Perception.seenBy(mm, player) ? 0.0F : 1.0F;
			out[i + 13] = m.postPoint != null && mm.distanceToSqr(m.postPoint.x, mm.getY(), m.postPoint.z) < Captain.IN_POST * Captain.IN_POST ? 1.0F : 0.0F;
			out[i + 14] = AttackTokens.holds(Perception.real(player), mm) ? 1.0F : 0.0F;
		}
		// W: the world's vector, as the captain's own observation has it
		System.arraycopy(own, ObsV4.W_AT, out, W_AT, 16);
		// O: the order in force, the new orders of revision 2 as the members see them (Captain.seenAs)
		out[O_AT + Captain.seenAs(g.command).ordinal()] = 1.0F;
		out[O_AT + 9] = (float) ObsM1.clip((now - g.command.givenAt) / 40.0, 0.0, 2.0);
		out[O_AT + 10 + g.command.formation.ordinal()] = 1.0F;
		return out;
	}

	/** The 253 inputs of contract revision 2, with the rules' order worked out here (tests, tools). */
	public static float[] observeV2(Captain.Group g, Player player, long now) {
		return observeV2(g, player, now, ruleOrder(g, player, now));
	}

	/**
	 * The 253 inputs of contract revision 2 ({@link #namesV2()}; docs/red_capitan_v4_contrato_v2.json, "entradas_nuevas"):
	 * v1's 213, then the rules' order ({@code rules}), then captain 2's state.
	 */
	public static float[] observeV2(Captain.Group g, Player player, long now, Captain.Command rules) {
		float[] out = new float[SIZE_V2];
		System.arraycopy(observe(g, player, now), 0, out, 0, SIZE);
		Mob captain = g.captain;
		Player real = Perception.real(player);
		// 213..232: the rules' order, formation and countdown now
		out[RULE_AT + rules.order.ordinal()] = 1.0F;
		out[RULE_AT + 12 + rules.formation.ordinal()] = 1.0F;
		out[RULE_AT + 16 + Math.max(0, Math.min(3, rules.count))] = 1.0F;
		int at = C2_AT;
		// 233..236: the order in force, if it is one of the new ones, and whether RETIRADA_FALSA has turned to the attack
		Captain.Command c = g.command;
		out[at] = c.order == Captain.Order.CERRAR_SALIDAS ? 1.0F : 0.0F;
		out[at + 1] = c.order == Captain.Order.FOCO_HERIDO ? 1.0F : 0.0F;
		out[at + 2] = c.order == Captain.Order.RETIRADA_FALSA ? 1.0F : 0.0F;
		out[at + 3] = c.order == Captain.Order.RETIRADA_FALSA && c.falseAttack ? 1.0F : 0.0F;
		// 237, 238: an acting captain; sin_mando 1 → 0 over the 200 ticks after the last captain fell
		out[at + 4] = g.interim ? 1.0F : 0.0F;
		out[at + 5] = now - g.captainDiedAt < Captain.LEADERLESS ? (float) (1.0 - (now - g.captainDiedAt) / (double) Captain.LEADERLESS) : 0.0F;
		// 239: the captain's distance to the player less the nearest one that fights up close (itself left out), /8, ±2
		double nearest = Double.NaN;
		int escorts = 0;
		for (MobMind m : g.members) {
			if (m.mob == captain) {
				continue;
			}
			if (Captain.melee(m.mob)) {
				double d = m.mob.distanceTo(real);
				nearest = Double.isNaN(nearest) ? d : Math.min(nearest, d);
			}
			escorts += m.mob.distanceTo(captain) < 3.0 ? 1 : 0;
		}
		out[at + 6] = Double.isNaN(nearest) ? 0.0F : (float) ObsM1.clip((captain.distanceTo(real) - nearest) / 8.0, -2.0, 2.0);
		// 240: members within 3 of the captain, /2
		out[at + 7] = (float) ObsM1.clip(escorts / 2.0, 0.0, 2.0);
		// 241: the player has it in a cone of 30° (15° each side) with a clear line
		out[at + 8] = looksAt(real, captain) ? 1.0F : 0.0F;
		// 242: the player's blows on it in the last 100 ticks, /3
		MobMind cm = MobAi.mind(captain);
		int hits = 0;
		if (cm != null) {
			for (long t : cm.playerHits) {
				hits += now - t <= MobMind.HITS_KEPT ? 1 : 0;
			}
		}
		out[at + 9] = (float) ObsM1.clip(hits / 3.0, 0.0, 2.0);
		// 243: members given the group's estimate at the last pass, /8
		out[at + 10] = (float) ObsM1.clip(g.shared / 8.0, 0.0, 2.0);
		// 244: the player's speed away from the group's middle, /0.2, ±2
		Vec3 middle = Captain.middle(g.members, null);
		Vec3 away = real.position().subtract(middle).multiply(1.0, 0.0, 1.0);
		Vec3 moving = MobSprint.motion(real);
		out[at + 11] = away.lengthSqr() < 1.0E-6 ? 0.0F
			: (float) ObsM1.clip((moving.x * away.x + moving.z * away.z) / away.length() / 0.2, -2.0, 2.0);
		// 245: of 8 ways out, the free ones, /8
		out[at + 12] = freeExits(real) / 8.0F;
		// 246..249: the other player (the nearest to the captain within 16) and mine
		Player other = null;
		double best = Captain.FOCUS_RANGE * Captain.FOCUS_RANGE;
		for (Player p : captain.level().players()) {
			if (p != real && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(captain) <= best) {
				best = p.distanceToSqr(captain);
				other = p;
			}
		}
		if (other != null) {
			out[at + 13] = 1.0F;
			out[at + 14] = other.getHealth() / Math.max(1.0F, other.getMaxHealth());
			out[at + 15] = (float) ObsM1.clip(other.distanceTo(captain) / 16.0, 0.0, 2.0);
		}
		out[at + 16] = real.getHealth() / Math.max(1.0F, real.getMaxHealth());
		// 250..252: the protection in force
		out[at + 17 + Math.max(0, Math.min(2, g.protection))] = 1.0F;
		return out;
	}

	/** Half the player's cone for jug_mira_capitan (a cone of 30°). */
	static final double LOOK_COS = Math.cos(Math.toRadians(15.0));

	/** jug_mira_capitan: the captain inside the player's cone of 30° with a clear line from their eyes to its. */
	static boolean looksAt(Player player, Mob captain) {
		Vec3 eyes = player.getEyePosition();
		Vec3 to = captain.getEyePosition().subtract(eyes);
		double d = to.length();
		if (d < 1.0E-4) {
			return true;
		}
		if (to.scale(1.0 / d).dot(player.getViewVector(1.0F)) < LOOK_COS) {
			return false;
		}
		return captain.level().clip(new ClipContext(eyes, captain.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
			.getType() == HitResult.Type.MISS;
	}

	/**
	 * salidas_libres: of the 8 ways from the player (45° apart), the ones where the spot 4 blocks off does not rise more
	 * than 1 over their feet (the block at feet + 1 there has no collision, and there is room for them on it) and can be
	 * seen from their eyes (a clear line to the spot at their eye height). A drop does not close a way.
	 */
	public static int freeExits(Player player) {
		var level = player.level();
		int free = 0;
		int feet = (int) Math.floor(player.getY() + 1.0E-3);
		Vec3 eyes = player.getEyePosition();
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4.0;
			double x = player.getX() + Math.cos(a) * 4.0;
			double z = player.getZ() + Math.sin(a) * 4.0;
			BlockPos step = BlockPos.containing(x, feet + 1, z);
			if (!level.getBlockState(step).getCollisionShape(level, step).isEmpty()
				|| !level.getBlockState(step.above()).getCollisionShape(level, step.above()).isEmpty()) {
				continue;
			}
			Vec3 spot = new Vec3(x, eyes.y, z);
			if (level.clip(new ClipContext(eyes, spot, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
				free++;
			}
		}
		return free;
	}

	/** 0 body, 1 archer, 2 creeper, 3 spider, 4 anything else (the g_* one-hot). */
	private static int familyIndex(Mob mob) {
		return switch (MobFamily.of(mob)) {
			case CUERPO -> 0;
			case ARQUERO -> 1;
			case CREEPER -> 2;
			case ARANA -> 3;
			default -> 4;
		};
	}

	/** How much of the circle round the player the group covers: 2π less its widest gap (0 with nobody near). */
	private static double coverage(List<Double> angles) {
		if (angles.isEmpty()) {
			return 0.0;
		}
		if (angles.size() == 1) {
			return 0.0;
		}
		List<Double> sorted = new ArrayList<>(angles);
		Collections.sort(sorted);
		double widest = 0.0;
		for (int i = 0; i < sorted.size(); i++) {
			double next = i + 1 < sorted.size() ? sorted.get(i + 1) : sorted.get(0) + 2.0 * Math.PI;
			widest = Math.max(widest, next - sorted.get(i));
		}
		return 2.0 * Math.PI - widest;
	}
}
