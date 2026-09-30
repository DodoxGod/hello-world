package dev.forja.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.forja.combat.AttackTokens;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.ForjaDifficulty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * The captain's brain (docs/red_mob_v4_diseno.md §3.2–§3.4): red_capitan.json from redes_v4 ("formato":
 * "red_capitan_v4", NetBrain's shape with a GRU of its own) when there is one that fits, the rules otherwise
 * (Captain.rules). Its observation is 213 numbers ({@link #names()}; docs/red_capitan_v4_contrato.json), its outputs 60.
 *
 * <p>Outputs: orden 0–8, formacion 9–12, sector 13–21, cuenta 22–25, foco 26–27, puesto_k (k = 0..7) 28–59, four each.
 * Each head is sampled on its own under the mask: foco 1 only with another player within 16 of the captain; a post of a
 * member that is not there is left at 0; ASEDIO only with the player up high (as the mob's ASEDIAR). Members 9 and on
 * take their post by kind (Captain.postByKind), as the design says.
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

	private static List<String> names;

	private CaptainBrain() {
	}

	/** Whether groups get captains at all (CombatConfig.iaCapitan). */
	public static boolean enabled() {
		return CombatConfig.get().enabled && CombatConfig.get().iaCapitan;
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
		// O: the order in force (14)
		for (Captain.Order o : Captain.Order.values()) {
			n.add("orden_" + o.name().toLowerCase(java.util.Locale.ROOT));
		}
		n.add("orden_edad/40");
		for (Captain.Formation f : Captain.Formation.values()) {
			n.add("formacion_" + (f == Captain.Formation.CUNA ? "cuna" : f.name().toLowerCase(java.util.Locale.ROOT)));
		}
		names = Collections.unmodifiableList(n);
		return names;
	}

	/** The output names, in order. */
	public static List<String> outputs() {
		List<String> out = new ArrayList<>();
		for (Captain.Order o : Captain.Order.values()) {
			out.add("orden_" + o.name().toLowerCase(java.util.Locale.ROOT));
		}
		for (Captain.Formation f : Captain.Formation.values()) {
			out.add("formacion_" + f.name().toLowerCase(java.util.Locale.ROOT));
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
		return out;
	}

	/** Why a network cannot be the captain, or null (formato, the 213 names in order, 60 outputs). */
	public static String check(NetBrain net) {
		if (!FORMAT.equals(net.format)) {
			return "formato '" + net.format + "': no es " + FORMAT;
		}
		List<String> ours = names();
		if (net.inputs() != ours.size() || net.names.size() != ours.size()) {
			return "espera " + net.inputs() + " entradas con " + net.names.size() + " nombres; el capitán v4 tiene " + ours.size();
		}
		for (int i = 0; i < ours.size(); i++) {
			if (!ours.get(i).equals(net.names.get(i))) {
				return "la entrada " + i + " es '" + net.names.get(i) + "' y el mod da '" + ours.get(i) + "'";
			}
		}
		return net.outputs() != OUTPUTS ? "da " + net.outputs() + " salidas y el capitán v4 tiene " + OUTPUTS : null;
	}

	/** The group's orders this pass: from the captain's network when there is one, else the rules. */
	static Captain.Command decide(Captain.Group g, Player player, long now) {
		NetBrain net = MobAi.captainNet();
		if (net == null) {
			Captain.Command c = Captain.rules(g, player, now);
			c.posts = Captain.rulesPosts(g);
			return c;
		}
		if (g.memory == null || g.memory.length != net.memory) {
			g.memory = new float[net.memory];
		}
		float[] obs = observe(g, player, now);
		float[] logits = net.forward(obs, g.memory);
		MobMind captain = MobAi.mind(g.captain);
		RandomSource random = captain != null ? captain.random : g.captain.getRandom();
		double temperature = CombatConfig.get().iaTemperatura * ForjaDifficulty.current().temperature;
		boolean[] mask = mask(g, player);
		Captain.Command c = new Captain.Command();
		c.order = Captain.Order.values()[pick(logits, ORDER_AT, 9, temperature, random, mask)];
		c.formation = Captain.Formation.values()[pick(logits, FORMATION_AT, 4, temperature, random, mask)];
		c.sector = pick(logits, SECTOR_AT, 9, temperature, random, mask);
		c.count = pick(logits, COUNT_AT, 4, temperature, random, mask);
		c.focus = pick(logits, FOCUS_AT, 2, temperature, random, mask);
		c.posts = new int[g.members.size()];
		for (int i = 0; i < c.posts.length; i++) {
			c.posts[i] = i < Captain.MEMBERS ? pick(logits, POSTS_AT + 4 * i, 4, temperature, random, mask) : Captain.postByKind(g.members.get(i).mob);
		}
		return c;
	}

	/** The captain's mask: ASEDIO with the player up high, focus on another player only with one about, posts of members there. */
	static boolean[] mask(Captain.Group g, Player player) {
		boolean[] mask = new boolean[OUTPUTS];
		java.util.Arrays.fill(mask, true);
		mask[ORDER_AT + Captain.Order.ASEDIO.ordinal()] = Heights.besieged(player);
		boolean other = false;
		for (Player p : g.captain.level().players()) {
			other |= p != player && p.isAlive() && !p.isSpectator() && p.distanceToSqr(g.captain) < 16.0 * 16.0;
		}
		mask[FOCUS_AT + 1] = other;
		for (int k = g.members.size(); k < Captain.MEMBERS; k++) {
			for (int j = 1; j < 4; j++) {
				mask[POSTS_AT + 4 * k + j] = false;
			}
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
		// O: the order in force
		out[O_AT + g.command.order.ordinal()] = 1.0F;
		out[O_AT + 9] = (float) ObsM1.clip((now - g.command.givenAt) / 40.0, 0.0, 2.0);
		out[O_AT + 10 + g.command.formation.ordinal()] = 1.0F;
		return out;
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
