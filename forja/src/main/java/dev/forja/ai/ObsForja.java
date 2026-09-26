package dev.forja.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.forja.combat.ArmorCalculator;
import dev.forja.combat.ChargedStrike;
import dev.forja.combat.Combos;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.Stamina;
import dev.forja.combat.SwingStyle;
import dev.forja.difficulty.ForjaDifficulty;
import dev.forja.difficulty.GearScore;
import dev.forja.difficulty.Pressure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The Forja block of a mob's observation (red_mob_v2): what the simulator's 102 numbers do not cover.
 * Each input is declared once, name and value side by side, so the list of names a network is checked
 * against and the numbers it is fed can never drift apart. They come after the 102 of {@link ObsM1}.
 */
public final class ObsForja {
	/** One input: its name in the network file and how it is worked out. */
	private record Input(String name, Source source) {
	}

	@FunctionalInterface
	private interface Source {
		double of(Mob mob, Player target, MobMind mind);
	}

	private static final List<Input> INPUTS = new ArrayList<>();

	static {
		// --- The player (fase 2) ---
		add("jug_estamina/100", (m, p, k) -> Stamina.value(p) / Math.max(1.0F, CombatConfig.get().staminaMax));
		add("jug_cargando", (m, p, k) -> p instanceof ServerPlayer sp && ChargedStrike.share(sp) >= 0.0 ? 1.0 : 0.0);
		add("jug_carga", (m, p, k) -> p instanceof ServerPlayer sp ? Math.max(0.0, ChargedStrike.share(sp)) : 0.0);
		add("jug_guardia/10", (m, p, k) -> Math.min(10, ObsM1.shieldTicks(p)) / 10.0);
		add("jug_esquiva_enfriamiento/15", (m, p, k) -> Math.min(1.0, Stamina.dodgeCooldown(p) / 15.0));
		add("jug_esquivando", (m, p, k) -> Stamina.isDodging(p, p.level().getGameTime()) ? 1.0 : 0.0);
		add("jug_contraataque", (m, p, k) -> Stamina.counterOpen(p) ? 1.0 : 0.0);
		add("jug_combo/2", (m, p, k) -> Combos.step(p) / 2.0);
		add("jug_peso_armadura", (m, p, k) -> ArmorCalculator.armorWeight(p));
		add("jug_estilo_tajo", (m, p, k) -> SwingStyle.of(p.getMainHandItem()) == SwingStyle.SLASH ? 1.0 : 0.0);
		add("jug_estilo_golpe", (m, p, k) -> SwingStyle.of(p.getMainHandItem()) == SwingStyle.CHOP ? 1.0 : 0.0);
		add("jug_estilo_estocada", (m, p, k) -> SwingStyle.of(p.getMainHandItem()) == SwingStyle.THRUST ? 1.0 : 0.0);
		add("jug_estilo_otro", (m, p, k) -> SwingStyle.of(p.getMainHandItem()) == SwingStyle.VANILLA ? 1.0 : 0.0);
		add("jug_presion", (m, p, k) -> Pressure.of(p));
		add("jug_equipo", (m, p, k) -> GearScore.of(p));
		// --- What the monsters know of the player (fase 2) ---
		add("hab_parada", (m, p, k) -> PlayerHabits.get(p, PlayerHabits.PARRY));
		add("hab_esquiva", (m, p, k) -> PlayerHabits.get(p, PlayerHabits.DODGE));
		add("hab_bloqueo", (m, p, k) -> PlayerHabits.get(p, PlayerHabits.BLOCK));
		add("hab_lado", (m, p, k) -> PlayerHabits.get(p, PlayerHabits.SIDE));
		add("hab_distancia/8", (m, p, k) -> Math.min(2.0, PlayerHabits.get(p, PlayerHabits.DISTANCE) / 8.0));
		add("hab_carga", (m, p, k) -> PlayerHabits.get(p, PlayerHabits.CHARGE));
		add("hab_nivel", (m, p, k) -> PlayerHabits.skill(p));
		add("turnos_max/4", (m, p, k) -> Aggression.maxAttackers(p) / 4.0);
		// --- The difficulty chosen (fase 0b) ---
		for (ForjaDifficulty difficulty : ForjaDifficulty.values()) {
			add("dif_" + difficulty.name().toLowerCase(java.util.Locale.ROOT), (m, p, k) -> ForjaDifficulty.current() == difficulty ? 1.0 : 0.0);
		}
	}

	static {
		// --- The squad (fase 3): handed out by rules, never decided by the network ---
		for (SquadRole role : SquadRole.values()) {
			add("rol_" + role.name().toLowerCase(java.util.Locale.ROOT), (m, p, k) -> k != null && k.role == role ? 1.0 : 0.0);
		}
		add("hueco_delante", (m, p, k) -> slot(m, p, k)[0]);
		add("hueco_derecha", (m, p, k) -> slot(m, p, k)[1]);
		add("turnos_ocupados/4", (m, p, k) -> dev.forja.combat.AttackTokens.held(p) / 4.0);
		add("tengo_turno", (m, p, k) -> dev.forja.combat.AttackTokens.holds(p, m) ? 1.0 : 0.0);
		add("aliado_aturdido", (m, p, k) -> k != null && k.guarding ? 1.0 : 0.0);
		add("desbandada", (m, p, k) -> k != null && k.routed ? 1.0 : 0.0);
		add("otros_esperan", (m, p, k) -> k != null && k.othersWaiting ? 1.0 : 0.0);
	}

	static {
		// --- Its own specials and warnings (fase 4): slot k of its moveset, if it has one ---
		for (int slot = 0; slot < Movesets.SLOTS; slot++) {
			int k = slot;
			add("esp" + (k + 1) + "_disponible", (m, p, mind) -> mind != null && mind.specials != null && mind.specials.available(k, p) ? 1.0 : 0.0);
			add("esp" + (k + 1) + "_enfriamiento", (m, p, mind) -> mind != null && mind.specials != null && k < mind.specials.moveset().size()
				? mind.specials.cooldownLeft(k) : 1.0);
		}
		add("especial_en_curso", (m, p, k) -> k != null && k.specials != null && k.specials.active() ? 1.0 : 0.0);
		add("avisando", (m, p, k) -> k != null && (k.windup > 0 || k.specials != null && k.specials.warningProgress() >= 0.0) ? 1.0 : 0.0);
		add("aviso_progreso", (m, p, k) -> {
			if (k == null) return 0.0;
			if (k.windup > 0) return 1.0 - k.windup / (double) Math.max(1, k.windupTotal);
			return k.specials != null ? Math.max(0.0, k.specials.warningProgress()) : 0.0;
		});
	}

	static {
		// --- The mob itself: balance, guard, dodge, threat (fases 0b y 5) ---
		add("yo_postura", (m, p, k) -> dev.forja.combat.Posture.fill(m));
		add("yo_aturdido/40", (m, p, k) -> Math.min(1.0, dev.forja.combat.Posture.staggerLeft(m) / 40.0));
		add("yo_resistencia_aturdimiento", (m, p, k) -> dev.forja.combat.Posture.resistance(m));
		add("yo_escudo", (m, p, k) -> MobDefense.hasShield(m) ? 1.0 : 0.0);
		add("yo_escudo_arriba", (m, p, k) -> m.isUsingItem() && MobDefense.hasShield(m) ? 1.0 : 0.0);
		add("yo_guardia_rota", (m, p, k) -> MobDefense.guardBroken(m) ? 1.0 : 0.0);
		add("yo_esquiva_lista", (m, p, k) -> MobDefense.dodgeReady(m) ? 1.0 : 0.0);
		add("yo_esquivando", (m, p, k) -> MobDefense.dodging(m) ? 1.0 : 0.0);
		add("yo_contraataque", (m, p, k) -> MobDefense.counterReady(m) ? 1.0 : 0.0);
		for (dev.forja.difficulty.Threat threat : dev.forja.difficulty.Threat.values()) {
			add("amenaza_" + threat.name().toLowerCase(java.util.Locale.ROOT), (m, p, k) -> dev.forja.difficulty.Threat.of(m) == threat ? 1.0 : 0.0);
		}
		add("yo_jefe", (m, p, k) -> dev.forja.difficulty.Bosses.isBoss(m) ? 1.0 : 0.0);
	}

	static {
		// --- The mod's own monsters (fase 6): which one, and the states their fights turn on ---
		for (net.minecraft.world.entity.EntityType<?> type : ForjaFamily.TYPES) {
			String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
			add("tipo_forja_" + id, (m, p, k) -> m.getType() == type ? 1.0 : 0.0);
		}
		add("yo_abierto", (m, p, k) -> ForjaTraits.smithOpen(m) ? 1.0 : 0.0);
		add("yo_sujetando", (m, p, k) -> m instanceof dev.forja.entity.Tongs tongs && tongs.holding() != null ? 1.0 : 0.0);
		add("yo_tamano/3", (m, p, k) -> m instanceof dev.forja.entity.LivingSlag slag ? slag.size() / 3.0 : 0.0);
	}

	static {
		// --- Memory and personality (fase 8), and the world ---
		for (Personality.Trait trait : Personality.Trait.values()) {
			add("rasgo_" + trait.name().toLowerCase(java.util.Locale.ROOT), (m, p, k) -> Personality.trait(m) == trait ? 1.0 : 0.0);
		}
		add("veterania", (m, p, k) -> Math.min(1.0, Personality.fights(m) / (double) Personality.VETERAN_FIGHTS));
		add("rencor", (m, p, k) -> Personality.grudge(m, p) ? 1.0 : 0.0);
		add("miedo", (m, p, k) -> Personality.afraid(m) ? 1.0 : 0.0);
		add("intrepido", (m, p, k) -> Personality.fearless(m) ? 1.0 : 0.0);
		add("en_casa", (m, p, k) -> Personality.atHome(m) ? 1.0 : 0.0);
		add("noche", (m, p, k) -> m.level().isDarkOutside() ? 1.0 : 0.0);
		add("lluvia", (m, p, k) -> m.level().isRainingAt(m.blockPosition()) ? 1.0 : 0.0);
		add("luz/15", (m, p, k) -> m.level().getMaxLocalRawBrightness(m.blockPosition()) / 15.0);
	}

	static {
		// --- The world's fights (fase 9) ---
		add("duelo_retador", (m, p, k) -> Duels.challenger(m) ? 1.0 : 0.0);
		add("duelo_espectador", (m, p, k) -> Duels.watching(m) ? 1.0 : 0.0);
		add("ladron", (m, p, k) -> WorldFights.thief(m) ? 1.0 : 0.0);
		add("enfurecido", (m, p, k) -> k != null && k.enraged ? 1.0 : 0.0);
	}

	/** Where the mob's ring slot is from it, in its own frame (forward, right), divided by 8. */
	private static double[] slot(Mob mob, Player player, MobMind mind) {
		if (mind == null || Double.isNaN(mind.ringAngle)) {
			return new double[] {0.0, 0.0};
		}
		double sx = player.getX() + Math.cos(mind.ringAngle) * TacticGoal.RING_RADIUS - mob.getX();
		double sz = player.getZ() + Math.sin(mind.ringAngle) * TacticGoal.RING_RADIUS - mob.getZ();
		double dx = player.getX() - mob.getX();
		double dz = player.getZ() - mob.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double ux = dx / d;
		double uz = dz / d;
		return new double[] {ObsM1.clip((sx * ux + sz * uz) / 8.0, -2.0, 2.0), ObsM1.clip((sx * -uz + sz * ux) / 8.0, -2.0, 2.0)};
	}

	private ObsForja() {
	}

	private static void add(String name, Source source) {
		INPUTS.add(new Input(name, source));
	}

	public static List<String> names() {
		List<String> names = new ArrayList<>(INPUTS.size());
		for (Input input : INPUTS) {
			names.add(input.name());
		}
		return Collections.unmodifiableList(names);
	}

	public static int size() {
		return INPUTS.size();
	}

	/** The 102 of the simulator followed by the Forja block, cut to the {@code wanted} inputs a network takes. */
	public static float[] full(Mob mob, Player target, MobMind mind, float[] m1, int wanted) {
		float[] out = new float[Math.max(wanted, m1.length)];
		System.arraycopy(m1, 0, out, 0, m1.length);
		for (int i = 0; i < INPUTS.size() && m1.length + i < out.length; i++) {
			out[m1.length + i] = (float) INPUTS.get(i).source().of(mob, target, mind);
		}
		return out;
	}
}
