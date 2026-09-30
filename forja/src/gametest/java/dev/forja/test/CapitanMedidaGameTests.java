package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import dev.forja.ai.Captain;
import dev.forja.ai.CaptainBrain;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Does the captain help? A group of 8 (an elite and 7: zombies, one with a shield, a husk, a spider and two skeletons)
 * against a scripted player, the same seeded fights once per mode (CaptainBrain.Mode: no captain, a captain that gives no
 * orders, the rules captain). The player holds, backs away and strafes in seeded phases, faces the nearest monster and
 * hits whoever is within 3.5 for 5 every 16 ticks; its health is put back each tick and what it lost is added up.
 *
 * <p>Only with FORJA_CAPITAN_MEDIR=&lt;file&gt; (a line per fight goes to it; FORJA_CAPITAN_N fights per mode, 8 by default):
 * the fights are long and their outcome is a measurement, not a pass or a fail. The three modes run at once, in a batch
 * of their own (environment capitan_medida), each on its own player (CaptainBrain.override).
 */
public class CapitanMedidaGameTests {
	/** Ticks per fight. */
	static final int FIGHT = 600;
	/** Ticks between fights (clearing up, the group's memory forgotten). */
	static final int GAP = 80;
	static final int SIZE = 40;
	/** The player hits for this much every {@link #SWING} ticks, whoever is nearest within {@link #PLAYER_REACH}. */
	static final float PLAYER_DAMAGE = 5.0F;
	static final int SWING = 16;
	static final double PLAYER_REACH = 3.5;

	/** With FORJA_CAPITAN_TRAZA=<file>, every tick of the first fights of each mode, mob by mob, goes to it. */
	static final int TRACED = 2;
	static final int MAX_FIGHTS = 100;

	/** Whether FORJA_CAPITAN_ABLACION (a comma list) names this switch. */
	static boolean ablation(String name) {
		String list = System.getenv("FORJA_CAPITAN_ABLACION");
		return list != null && java.util.Arrays.asList(list.split(",")).contains(name);
	}

	static int fights() {
		String n = System.getenv("FORJA_CAPITAN_N");
		return n == null || n.isBlank() ? 8 : Integer.parseInt(n.trim());
	}

	@GameTest(environment = "forja-test:capitan_medida", padding = 32, maxTicks = MAX_FIGHTS * (FIGHT + GAP) + 200)
	public void measureNoCaptain(GameTestHelper helper) {
		measure(helper, CaptainBrain.Mode.SIN_CAPITAN);
	}

	@GameTest(environment = "forja-test:capitan_medida", padding = 32, maxTicks = MAX_FIGHTS * (FIGHT + GAP) + 200)
	public void measureFreeCaptain(GameTestHelper helper) {
		measure(helper, CaptainBrain.Mode.LIBRE);
	}

	@GameTest(environment = "forja-test:capitan_medida", padding = 32, maxTicks = MAX_FIGHTS * (FIGHT + GAP) + 200)
	public void measureRulesCaptain(GameTestHelper helper) {
		measure(helper, CaptainBrain.Mode.REGLAS);
	}

	private static void measure(GameTestHelper helper, CaptainBrain.Mode mode) {
		String out = System.getenv("FORJA_CAPITAN_MEDIR");
		if (out == null || out.isBlank()) {
			helper.succeed();
			return;
		}
		int fights = Math.min(MAX_FIGHTS, fights());
		CombatConfig.get().veteranChance = 0.0;
		// FORJA_CAPITAN_ABLACION: switches for looking for what slows the group down (they apply to every mode at once)
		if (ablation("sinaviso")) {
			CombatConfig.get().telegraph = false;
		}
		if (ablation("sinpostura")) {
			CombatConfig.get().posture = false;
		}
		// the variants measured for the defaults: set either way (the run's config file keeps whatever it had)
		CombatConfig.get().iaTurnoGrupoGrande = !ablation("sinturno");
		CombatConfig.get().iaCapitanPinza = !ablation("sinpinza");
		CombatConfig.get().eliteChance = 0.0;
		CombatConfig.get().shieldChance = 0.0;
		dev.forja.ForjaConfig.get().baculos = 0.0F;
		dev.forja.ForjaConfig.get().grimorios = 0.0F;
		floor(helper);
		Path file = Path.of(out.trim());
		// One fight after another, driven from a single tick listener (scheduling from inside a scheduled task broke the
		// framework's own iteration over its schedule).
		int[] next = {0};
		long[] startAt = {helper.getLevel().getGameTime() + 5};
		java.util.function.BooleanSupplier[] current = {null};
		boolean[] over = {false};
		helper.onEachTick(() -> {
			if (over[0]) {
				return;
			}
			long now = helper.getLevel().getGameTime();
			if (current[0] != null) {
				if (current[0].getAsBoolean()) {
					current[0] = null;
					startAt[0] = now + GAP;
				}
				return;
			}
			if (now < startAt[0]) {
				return;
			}
			if (next[0] >= fights) {
				over[0] = true;
				TestChunks.release(helper);
				helper.succeed();
				return;
			}
			current[0] = fight(helper, mode, ++next[0], file);
		});
	}

	private static void floor(GameTestHelper helper) {
		TestChunks.force(helper, SIZE);
		for (int x = -1; x <= SIZE; x++) {
			for (int z = -1; z <= SIZE; z++) {
				for (int y = 0; y <= 10; y++) {
					BlockPos at = new BlockPos(x, y, z);
					if (helper.getBlockState(at).is(Blocks.BARRIER)) {
						helper.setBlock(at, Blocks.AIR);
					}
				}
			}
		}
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** One fight: the group comes in from a seeded side, the player follows its seeded script, for FIGHT ticks. */
	private static java.util.function.BooleanSupplier fight(GameTestHelper helper, CaptainBrain.Mode mode, int seed, Path out) {
		Random dice = new Random(seed * 7919L);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(SIZE / 2, 1, SIZE / 2));
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		CaptainBrain.override(player, mode);
		double side = dice.nextDouble() * Math.PI * 2.0;
		List<Mob> mobs = new ArrayList<>();
		List<EntityType<? extends Mob>> kinds = List.of(EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.ZOMBIE,
			EntityTypes.HUSK, EntityTypes.SPIDER, EntityTypes.SKELETON, EntityTypes.SKELETON);
		for (int k = 0; k < kinds.size(); k++) {
			double a = side + (k - 3.5) * 0.18;
			double r = 11.0 + dice.nextDouble() * 3.0;
			BlockPos at = new BlockPos((int) Math.round(SIZE / 2.0 + Math.cos(a) * r), 1, (int) Math.round(SIZE / 2.0 + Math.sin(a) * r));
			Mob mob = helper.spawn(kinds.get(k), at);
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			if (kinds.get(k) == EntityTypes.SKELETON) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			}
			mobs.add(mob);
		}
		Mob elite = mobs.get(0);
		Threat.ELITE.mark(elite);
		elite.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		mobs.get(1).setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		for (Mob mob : mobs) {
			mob.setTarget(player);
		}
		long start = helper.getLevel().getGameTime();
		double[] taken = {0.0};
		long[] killedAt = {-1};
		int[] phase = {0};
		int[] phaseLeft = {40};
		double[] strafe = {1.0};
		Map<Captain.Order, Integer> orders = new EnumMap<>(Captain.Order.class);
		boolean[] done = {false};
		// ticks summed: mobs within reach, turns taken, ticks with a turn free, ticks
		double[] watch = new double[4];
		String traced = System.getenv("FORJA_CAPITAN_TRAZA");
		Path trace = traced == null || traced.isBlank() ? null : Path.of(traced.trim());
		Vec3 low = helper.absoluteVec(new Vec3(4, 1, 4));
		Vec3 high = helper.absoluteVec(new Vec3(SIZE - 4, 1, SIZE - 4));
		return () -> {
			if (done[0]) {
				return true;
			}
			long t = helper.getLevel().getGameTime() - start;
			// what the monsters did to the player since the last tick
			float lost = player.getMaxHealth() - player.getHealth();
			if (lost > 0.0F) {
				taken[0] += lost;
				if (killedAt[0] < 0 && taken[0] >= 20.0) {
					killedAt[0] = t;
				}
			}
			player.setHealth(player.getMaxHealth());
			player.invulnerableTime = Math.max(0, player.invulnerableTime - 1);
			player.hurtTime = Math.max(0, player.hurtTime - 1);
			player.removeAllEffects();
			player.clearFire();
			List<Mob> alive = mobs.stream().filter(Entity::isAlive).toList();
			if (t >= FIGHT || alive.isEmpty()) {
				done[0] = true;
				finish(helper, mode, seed, out, mobs, taken[0], killedAt[0], t, orders, player, watch);
				return true;
			}
			Captain.Group g = Captain.group(player);
			Captain.Order order = g == null || g.captain == null ? Captain.Order.NINGUNA : g.command.order;
			orders.merge(order, 1, Integer::sum);
			// where the free ring stalls: how many are within reach of the player, and how many turns are taken
			int near = 0;
			for (Mob mob : alive) {
				near += mob.distanceTo(player) <= PLAYER_REACH ? 1 : 0;
			}
			int held = dev.forja.combat.AttackTokens.held(player);
			int max = dev.forja.ai.Aggression.maxAttackers(player);
			watch[0] += near;
			watch[1] += held;
			watch[2] += held < max ? 1 : 0;
			watch[3]++;
			if (trace != null && seed <= TRACED) {
				StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "%s\t%d\t%d\t%d\t%d/%d\t%.2f,%.2f\t%s", mode.name().toLowerCase(Locale.ROOT),
					seed, t, near, held, max, player.getX() - low.x, player.getZ() - low.z, order.name().toLowerCase(Locale.ROOT)));
				for (Mob mob : alive) {
					dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
					StringBuilder goals = new StringBuilder();
					((dev.forja.mixin.MobGoalsAccess) mob).forjaGoals().getAvailableGoals().stream().filter(w -> w.isRunning())
						.forEach(w -> goals.append(w.getGoal().getClass().getSimpleName(), 0, Math.min(5, w.getGoal().getClass().getSimpleName().length())).append('+'));
					row.append(String.format(Locale.ROOT, "\t%s:%.1f:%s:%s%s%s%s:%s", mob.getType().toShortString().substring(0, 3), mob.distanceTo(player),
						mind == null ? "-" : mind.decision.tactic().name().toLowerCase(Locale.ROOT),
						dev.forja.combat.AttackTokens.holds(player, mob) ? "T" : "", mind != null && mind.windup > 0 ? "W" : "",
						mind != null && mind.running ? "R" : "", dev.forja.combat.Posture.isStaggered(mob, helper.getLevel().getGameTime()) ? "S" : "",
						goals));
				}
				row.append('\n');
				try {
					Files.writeString(trace, row, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				} catch (java.io.IOException failure) {
					throw new RuntimeException(failure);
				}
			}
			for (Mob mob : alive) {
				if (mob.getTarget() != player) {
					mob.setTarget(player);
				}
			}
			// the script: hold, back away from the group, or strafe round it
			Mob nearest = null;
			double best = Double.MAX_VALUE;
			double cx = 0.0;
			double cz = 0.0;
			for (Mob mob : alive) {
				double d = mob.distanceToSqr(player);
				if (d < best) {
					best = d;
					nearest = mob;
				}
				cx += mob.getX();
				cz += mob.getZ();
			}
			cx /= alive.size();
			cz /= alive.size();
			if (--phaseLeft[0] <= 0) {
				phase[0] = dice.nextInt(3);
				phaseLeft[0] = 25 + dice.nextInt(30);
				strafe[0] = dice.nextBoolean() ? 1.0 : -1.0;
			}
			double ax = player.getX() - cx;
			double az = player.getZ() - cz;
			double ad = Math.max(1.0E-6, Math.hypot(ax, az));
			ax /= ad;
			az /= ad;
			double vx = 0.0;
			double vz = 0.0;
			if (phase[0] == 1) {
				vx = ax * 0.18;
				vz = az * 0.18;
			} else if (phase[0] == 2) {
				vx = -az * strafe[0] * 0.15;
				vz = ax * strafe[0] * 0.15;
			}
			double nx = player.getX() + vx;
			double nz = player.getZ() + vz;
			if (nx < low.x || nx > high.x || nz < low.z || nz > high.z) {
				nx = Math.max(low.x, Math.min(high.x, nx));
				nz = Math.max(low.z, Math.min(high.z, nz));
				phaseLeft[0] = 0;
			}
			player.setPos(nx, player.getY(), nz);
			player.setDeltaMovement(vx, 0.0, vz);
			if (nearest != null) {
				float yaw = (float) (Math.toDegrees(Math.atan2(nearest.getZ() - player.getZ(), nearest.getX() - player.getX())) - 90.0);
				player.setYRot(yaw);
				player.setYHeadRot(yaw);
				if (t % SWING == 0 && Math.sqrt(best) <= PLAYER_REACH) {
					Vec3 before = nearest.getDeltaMovement();
					nearest.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), PLAYER_DAMAGE);
					if (ablation("sinempuje")) {
						nearest.setDeltaMovement(before);
					}
				}
			}
			return false;
		};
	}

	private static void finish(GameTestHelper helper, CaptainBrain.Mode mode, int seed, Path out, List<Mob> mobs, double taken, long killedAt,
		long ticks, Map<Captain.Order, Integer> orders, Player player, double[] watch) {
		int dead = (int) mobs.stream().filter(m -> !m.isAlive()).count();
		StringBuilder o = new StringBuilder();
		int total = orders.values().stream().mapToInt(Integer::intValue).sum();
		for (Map.Entry<Captain.Order, Integer> e : orders.entrySet()) {
			o.append(o.isEmpty() ? "" : ",").append(e.getKey().name().toLowerCase(Locale.ROOT)).append('=')
				.append(String.format(Locale.ROOT, "%.2f", e.getValue() / (double) Math.max(1, total)));
		}
		double n = Math.max(1.0, watch[3]);
		String line = String.format(Locale.ROOT, "%s\t%d\t%.1f\t%d\t%d\t%d\t%s\t%.2f\t%.2f\t%.2f%n", mode.name().toLowerCase(Locale.ROOT), seed,
			taken * 60.0 * 20.0 / Math.max(1, ticks), killedAt, dead, ticks, o, watch[0] / n, watch[1] / n, watch[2] / n);
		try {
			Files.writeString(out, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (java.io.IOException failure) {
			throw new RuntimeException(failure);
		}
		CaptainBrain.override(player, null);
		Captain.forget(player);
		AABB box = new AABB(helper.absoluteVec(new Vec3(-4, -2, -4)), helper.absoluteVec(new Vec3(SIZE + 4, 10, SIZE + 4)));
		helper.getLevel().getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player)).forEach(Entity::discard);
		player.discard();
	}
}
