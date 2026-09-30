package dev.forja.world;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.forja.Forja;
import dev.forja.entity.FallenSmith;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Fallen Smith's fight in the Cementerio entre Estrellas (docs/HERRERO_DIMENSION.md, section 3), as the
 * dimension runs it: when he comes down, what the sky does while he fights, the four braseros that put out
 * his forge, what happens when he dies, and calling him back.
 *
 * <p>What has to outlive a save, a restart or an empty dimension is kept on the level itself ({@link #STATE}):
 * which stage the fight is at, who he is, who has fought him, who is still owed a star. What belongs to him
 * (his phase, his apprentices called, his forge fires) is kept on him. When nobody is in the graveyard
 * nothing here runs and he does not move, so the fight is exactly where it was left.
 */
public final class StarFight {
	public enum Stage { IDLE, ARRIVING, FIGHTING, WON }

	/** The fight's own record: stage, the smith, everyone who fought, everyone still owed a star, and the clocks. */
	public record State(Stage stage, Optional<UUID> boss, List<UUID> participants, List<UUID> owed, long at, long nextSky, int fights) {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.xmap(Stage::valueOf, Stage::name).fieldOf("etapa").forGetter(State::stage),
			UUIDUtil.CODEC.optionalFieldOf("herrero").forGetter(State::boss),
			UUIDUtil.CODEC.listOf().fieldOf("participantes").forGetter(State::participants),
			UUIDUtil.CODEC.listOf().fieldOf("debidas").forGetter(State::owed),
			Codec.LONG.fieldOf("en").forGetter(State::at),
			Codec.LONG.fieldOf("cielo").forGetter(State::nextSky),
			Codec.INT.fieldOf("peleas").forGetter(State::fights)
		).apply(instance, State::new));

		static final State FRESH = new State(Stage.IDLE, Optional.empty(), List.of(), List.of(), 0L, 0L, 0);

		State with(Stage stage) {
			return new State(stage, this.boss, this.participants, this.owed, this.at, this.nextSky, this.fights);
		}
	}

	@SuppressWarnings("deprecation")
	public static final AttachmentType<State> STATE = AttachmentRegistry.<State>builder()
		.persistent(State.CODEC)
		.buildAndRegister(Forja.id("pelea_estelar"));

	/** How long after the first player arrives before he falls, and after he dies before his star lands. */
	public static final int ARRIVAL_DELAY = 60;
	public static final int STAR_DELAY = 60;
	/** A constellation every 30 to 40 s; the Martillo every 20 s while he reforges; 3 s of casting before it lands. */
	public static final int SKY_MIN = 600;
	public static final int SKY_SPREAD = 200;
	public static final int REFORGE_SKY = 400;
	public static final int CAST_TICKS = 60;
	/** The five colours of a constellation, weakest first, and what each multiplies its event by. */
	public static final float[] STRENGTH = {0.6F, 0.8F, 1.0F, 1.3F, 1.7F};
	/** How likely each colour is in each phase, in percent (the doc's table). */
	public static final int[][] WEIGHTS = {{50, 35, 15, 0, 0}, {25, 30, 25, 20, 0}, {10, 20, 30, 25, 15}};
	/** Which constellations help the smith (the rest help the players): Espada, Hacha, Escudo, Guadaña. */
	public static final boolean[] FOR_BOSS = {true, true, false, false, true, false, false, true};
	/** Indices into StarChart.CONSTELLATIONS. */
	public static final int ESPADA = 0, HACHA = 1, MARTILLO = 2, LANZA = 3, ESCUDO = 4, YUNQUE = 5, TENAZAS = 6, GUADANA = 7;
	/** A tipped brasero's run: from the pillar to the middle, 3 across, in 3 s, and gone 2 s after. */
	public static final int FLOW_TICKS = 60;
	public static final int FLOW_COOLS = 40;
	/** Star iron that fills an empty brasero again (Andy approved). */
	public static final int REFILL_IRON = 4;

	private static final List<Cast> CASTS = new ArrayList<>();
	private static final List<Flow> FLOWS = new ArrayList<>();
	private static final List<Cooling> COOLING = new ArrayList<>();
	private static long stormUntil;
	private static Vec3 wind = Vec3.ZERO;

	private record Cast(ServerLevel level, int index, int tier, long fireAt, Vec3 at, double angle) {
	}

	private record Flow(ServerLevel level, int brazier, long startedAt) {
	}

	private record Cooling(ServerLevel level, BlockPos pos, long at) {
	}

	private StarFight() {
	}

	public static void register() {
		StarCast.register();
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() == StarYard.LEVEL) {
				tick(level);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof FallenSmith smith && smith.level() instanceof ServerLevel level && level.dimension() == StarYard.LEVEL) {
				won(level, smith);
			}
		});
		// A blow on a brasero's bowl tips it; anything else on the pillars is the arena's, and is not broken.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level instanceof ServerLevel server && server.dimension() == StarYard.LEVEL && !player.isSpectator()) {
				int brazier = brazierAt(pos);
				if (brazier >= 0) {
					tip(server, brazier);
					return InteractionResult.FAIL;
				}
			}
			return InteractionResult.PASS;
		});
		// Star iron on a pillar fills its brasero again.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel server && server.dimension() == StarYard.LEVEL) {
				int brazier = pillarAt(hit.getBlockPos());
				ItemStack held = player.getItemInHand(hand);
				if (brazier >= 0 && held.is(ModItems.HIERRO_ESTELAR)) {
					if (held.getCount() < REFILL_IRON) {
						player.sendOverlayMessage(Component.translatable("gui.forja.brasero.hierro", REFILL_IRON));
						return InteractionResult.FAIL;
					}
					if (refill(server, brazier)) {
						held.consume(REFILL_IRON, player);
						return InteractionResult.SUCCESS;
					}
				}
			}
			return InteractionResult.PASS;
		});
	}

	// ------------------------------------------------------------------ the record

	public static State state(ServerLevel level) {
		return level.getAttachedOrElse(STATE, State.FRESH);
	}

	private static void save(ServerLevel level, State state) {
		level.setAttached(STATE, state);
	}

	/** Players who are really in the graveyard: not spectating. */
	public static List<ServerPlayer> fighters(ServerLevel level) {
		return level.players().stream().filter(player -> !player.isSpectator() && player.isAlive()).toList();
	}

	/** Everyone who counts as fighting a smith: the graveyard's players, or (in a test) the players round him. */
	public static List<? extends Player> fightersNear(ServerLevel level, FallenSmith smith) {
		if (level.dimension() == StarYard.LEVEL) {
			return fighters(level);
		}
		return level.getEntitiesOfClass(Player.class, smith.getBoundingBox().inflate(16.0), player -> player.isAlive() && !player.isSpectator());
	}

	public static @Nullable FallenSmith boss(ServerLevel level) {
		State state = state(level);
		return state.boss().map(level::getEntity).filter(entity -> entity instanceof FallenSmith smith && smith.isAlive())
			.map(entity -> (FallenSmith) entity).orElse(null);
	}

	// ------------------------------------------------------------------ the fight, tick by tick

	/** For the footage of the plateau: no fight while it is filmed. Never set in play. */
	public static boolean peaceForFootage = false;

	private static void tick(ServerLevel level) {
		if (peaceForFootage) {
			return;
		}
		long now = level.getGameTime();
		rollFlows(level, now);
		List<ServerPlayer> here = fighters(level);
		if (here.isEmpty()) {
			return;
		}
		State state = state(level);
		payDebts(level, state, here);
		state = state(level);
		switch (state.stage()) {
			case IDLE -> save(level, new State(Stage.ARRIVING, Optional.empty(), List.of(), state.owed(), now + ARRIVAL_DELAY, 0L, state.fights()));
			case ARRIVING -> {
				if (now >= state.at()) {
					FallenSmith smith = FallenSmith.fallFromSky(level);
					save(level, new State(Stage.FIGHTING, Optional.of(smith.getUUID()), List.of(), state.owed(), now, now + SKY_MIN + FallenSmith.FALL_TICKS, state.fights()));
				}
			}
			case FIGHTING -> {
				Set<UUID> fought = new LinkedHashSet<>(state.participants());
				here.forEach(player -> fought.add(player.getUUID()));
				if (fought.size() != state.participants().size()) {
					state = new State(state.stage(), state.boss(), List.copyOf(fought), state.owed(), state.at(), state.nextSky(), state.fights());
					save(level, state);
				}
				FallenSmith smith = boss(level);
				if (smith != null) {
					sky(level, smith, state, now);
					arrows(level);
				}
			}
			case WON -> {
				if (now < state.at()) {
					// The star on its way down: a line of light out of the sky onto the middle of the arena.
					double share = (state.at() - now) / (double) STAR_DELAY;
					double y = StarYard.SURFACE + 1.0 + 110.0 * share * share;
					level.sendParticles(ParticleTypes.END_ROD, 2.5, y, 0.5, 12, 0.3, 0.6, 0.3, 0.02);
					level.sendParticles(ParticleTypes.FIREWORK, 2.5, y + 2.0, 0.5, 6, 0.2, 1.0, 0.2, 0.01);
				} else if (!level.getBlockState(forgeAt()).is(ModBlocks.FRAGUA_FRIA_ESTELAR)) {
					land(level);
				}
			}
		}
		rollCasts(level, now);
		blowStorm(level, now);
	}

	/** Where the cold forge of a rematch stands: the middle of the arena, where he fell. */
	public static BlockPos forgeAt() {
		return new BlockPos(0, StarYard.SURFACE + 1, 0);
	}

	/** Where the star that takes you home lands: beside the forge. */
	public static BlockPos returnStarAt() {
		return new BlockPos(2, StarYard.SURFACE + 1, 0);
	}

	// ------------------------------------------------------------------ the end, and again

	/** How many Estrellas each player has been handed, for the tests (a test player's bag does not always take one). */
	private static final java.util.Map<UUID, Integer> PAID = new java.util.HashMap<>();

	private static void paid(UUID who) {
		PAID.merge(who, 1, Integer::sum);
	}

	public static int paidForTests(UUID who) {
		return PAID.getOrDefault(who, 0);
	}

	/** For the tests: the fight as it ends, and the star landing, without waiting on the graveyard's tick. */
	public static void winForTests(ServerLevel level, FallenSmith smith) {
		won(level, smith);
	}

	public static void landForTests(ServerLevel level) {
		land(level);
	}

	public static void payForTests(ServerLevel level, List<ServerPlayer> here) {
		payDebts(level, state(level), here);
	}

	public static void flowsForTests(ServerLevel level) {
		rollFlows(level, level.getGameTime());
	}

	public static void setStateForTests(ServerLevel level, State state) {
		save(level, state);
	}

	private static void won(ServerLevel level, FallenSmith smith) {
		State state = state(level);
		List<UUID> owed = new ArrayList<>(state.owed());
		Set<UUID> fought = new LinkedHashSet<>(state.participants());
		fighters(level).forEach(player -> fought.add(player.getUUID()));
		owed.addAll(fought);
		save(level, new State(Stage.WON, Optional.empty(), List.of(), owed, level.getGameTime() + STAR_DELAY, 0L, state.fights()));
		// His apprentices go with him, and whatever was still on its way from the sky.
		for (Mob apprentice : level.getEntitiesOfClass(Mob.class, new AABB(smith.blockPosition()).inflate(64.0), Apprentices::isApprentice)) {
			level.sendParticles(dev.forja.registry.ModParticles.CENIZA, apprentice.getX(), apprentice.getY(1.0), apprentice.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
			apprentice.discard();
		}
		CASTS.removeIf(cast -> cast.level() == level);
		stormUntil = 0L;
		payDebts(level, state(level), fighters(level));
	}

	/** The star comes down: the way home, and the cold forge that can call him again. */
	private static void land(ServerLevel level) {
		level.setBlockAndUpdate(returnStarAt(), ModBlocks.ESTRELLA_DE_VUELTA.defaultBlockState());
		level.setBlockAndUpdate(forgeAt(), ModBlocks.FRAGUA_FRIA_ESTELAR.defaultBlockState());
		Vec3 at = Vec3.atBottomCenterOf(returnStarAt());
		dev.forja.entity.Shockwave.burst(level, at, 8.0, 14, 0xFFF0C0, 1.0F);
		level.playSound(null, returnStarAt(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0F, 1.2F);
		level.playSound(null, returnStarAt(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3.0F, 0.7F);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 80, 1.0, 1.5, 1.0, 0.15);
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(Component.translatable("gui.forja.pelea.estrella_de_vuelta").withColor(0xFFE9A0));
		}
	}

	/** The cold forge revived: forge and star go, and he comes down out of the sky again. */
	public static void rematch(ServerLevel level, @Nullable ServerPlayer who) {
		State state = state(level);
		level.setBlockAndUpdate(forgeAt(), Blocks.AIR.defaultBlockState());
		if (level.getBlockState(returnStarAt()).is(ModBlocks.ESTRELLA_DE_VUELTA)) {
			level.setBlockAndUpdate(returnStarAt(), Blocks.AIR.defaultBlockState());
		}
		level.playSound(null, forgeAt(), SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 3.0F, 0.5F);
		level.sendParticles(ParticleTypes.FLAME, 0.5, StarYard.SURFACE + 1.5, 0.5, 60, 0.6, 0.6, 0.6, 0.05);
		for (int i = 0; i < 4; i++) {
			refill(level, i);
		}
		save(level, new State(Stage.ARRIVING, Optional.empty(), List.of(), state.owed(), level.getGameTime() + ARRIVAL_DELAY, 0L, state.fights() + 1));
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(Component.translatable("gui.forja.pelea.revancha").withColor(0xFF7A3A));
		}
	}

	/** An Estrella forjada to every participant who is here now; the rest keep waiting for theirs. */
	private static void payDebts(ServerLevel level, State state, List<ServerPlayer> here) {
		if (state.owed().isEmpty()) {
			return;
		}
		List<UUID> owed = new ArrayList<>(state.owed());
		boolean paid = false;
		for (ServerPlayer player : here) {
			while (owed.remove(player.getUUID())) {
				paid(player.getUUID());
				ItemStack star = new ItemStack(ModItems.ESTRELLA_FORJADA);
				if (!player.getInventory().add(star)) {
					player.drop(star, false);
				}
				player.sendSystemMessage(Component.translatable("gui.forja.pelea.recompensa").withColor(0xFFD27A));
				level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
				paid = true;
			}
		}
		if (paid) {
			save(level, new State(state.stage(), state.boss(), state.participants(), List.copyOf(owed), state.at(), state.nextSky(), state.fights()));
		}
	}

	// ------------------------------------------------------------------ the braseros

	/** The four braseros, one on each pillar round the arena, where the generator put them. */
	public static BlockPos brazier(int index) {
		double angle = Math.PI / 4.0 + index * Math.PI / 2.0;
		int x = (int) Math.round(Math.cos(angle) * StarYard.PILLAR_RING);
		int z = (int) Math.round(Math.sin(angle) * StarYard.PILLAR_RING);
		return new BlockPos(x, StarYard.SURFACE + 7, z);
	}

	/** The brasero a block of a pillar's top belongs to (its bowl and the ring round it), or -1. */
	public static int brazierAt(BlockPos pos) {
		for (int i = 0; i < 4; i++) {
			BlockPos bowl = brazier(i);
			if (Math.abs(pos.getX() - bowl.getX()) <= 1 && Math.abs(pos.getZ() - bowl.getZ()) <= 1 && Math.abs(pos.getY() - bowl.getY()) <= 1) {
				return i;
			}
		}
		return -1;
	}

	/** The brasero whose pillar this block is part of, anywhere from its foot to its bowl, or -1. */
	public static int pillarAt(BlockPos pos) {
		for (int i = 0; i < 4; i++) {
			BlockPos bowl = brazier(i);
			if (Math.abs(pos.getX() - bowl.getX()) <= 1 && Math.abs(pos.getZ() - bowl.getZ()) <= 1
				&& pos.getY() >= StarYard.SURFACE && pos.getY() <= bowl.getY()) {
				return i;
			}
		}
		return -1;
	}

	public static boolean full(ServerLevel level, int index) {
		return level.getBlockState(brazier(index)).is(ModBlocks.METAL_FUNDIDO);
	}

	/** Tips a full brasero: its metal runs down the pillar and along the floor to the middle. */
	public static boolean tip(ServerLevel level, int index) {
		if (!full(level, index) || FLOWS.stream().anyMatch(flow -> flow.level() == level && flow.brazier() == index)) {
			return false;
		}
		BlockPos bowl = brazier(index);
		level.setBlockAndUpdate(bowl, Blocks.AIR.defaultBlockState());
		level.playSound(null, bowl, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 3.0F, 0.6F);
		level.playSound(null, bowl, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.5F, 0.5F);
		level.sendParticles(ParticleTypes.LAVA, bowl.getX() + 0.5, bowl.getY() + 0.5, bowl.getZ() + 0.5, 30, 0.6, 0.4, 0.6, 0.0);
		FLOWS.add(new Flow(level, index, level.getGameTime()));
		for (ServerPlayer player : level.players()) {
			player.sendOverlayMessage(Component.translatable("gui.forja.brasero.volcado").withColor(0xFFA040));
		}
		return true;
	}

	/** Fills an empty brasero again. */
	public static boolean refill(ServerLevel level, int index) {
		if (full(level, index)) {
			return false;
		}
		BlockPos bowl = brazier(index);
		level.setBlockAndUpdate(bowl, ModBlocks.METAL_FUNDIDO.defaultBlockState());
		level.playSound(null, bowl, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 2.0F, 0.8F);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA, bowl.getX() + 0.5, bowl.getY() + 1.0, bowl.getZ() + 0.5, 20, 0.4, 0.3, 0.4, 0.2);
		return true;
	}

	/** Where along its run a flow is, as a point on the floor: from the pillar's foot (0) to the middle (1). */
	private static Vec3 along(int index, double share) {
		BlockPos bowl = brazier(index);
		Vec3 foot = new Vec3(bowl.getX() + 0.5, StarYard.SURFACE + 1, bowl.getZ() + 0.5);
		Vec3 middle = new Vec3(0.5, StarYard.SURFACE + 1, 0.5);
		return foot.lerp(middle, share);
	}

	private static void rollFlows(ServerLevel level, long now) {
		for (int i = FLOWS.size() - 1; i >= 0; i--) {
			Flow flow = FLOWS.get(i);
			if (flow.level() != level) {
				continue;
			}
			long t = now - flow.startedAt();
			// Down the pillar first, as a fall.
			BlockPos bowl = brazier(flow.brazier());
			level.sendParticles(ParticleTypes.FALLING_LAVA, bowl.getX() + 0.5, bowl.getY() - 1.0, bowl.getZ() + 0.5, 6, 0.6, 2.5, 0.6, 0.0);
			double share = Math.min(1.0, t / (double) FLOW_TICKS);
			Vec3 head = along(flow.brazier(), share);
			Vec3 dir = along(flow.brazier(), 1.0).subtract(along(flow.brazier(), 0.0)).normalize();
			Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
			for (int w = -1; w <= 1; w++) {
				BlockPos cell = BlockPos.containing(head.add(side.scale(w)));
				BlockState there = level.getBlockState(cell);
				if (there.is(ModBlocks.BRASA_ESTELAR)) {
					// A forge fire under the run goes out.
					level.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
					level.playSound(null, cell, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 3.0F, 0.6F);
					level.sendParticles(ParticleTypes.LARGE_SMOKE, cell.getX() + 0.5, cell.getY() + 0.6, cell.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
					there = level.getBlockState(cell);
				}
				if (there.isAir()) {
					level.setBlock(cell, ModBlocks.METAL_FUNDIDO.defaultBlockState(), 3);
					COOLING.add(new Cooling(level, cell, now + FLOW_COOLS));
				}
			}
			if (t >= FLOW_TICKS) {
				FLOWS.remove(i);
			}
		}
		for (int i = COOLING.size() - 1; i >= 0; i--) {
			Cooling cooling = COOLING.get(i);
			if (cooling.level() == level && now >= cooling.at()) {
				if (level.getBlockState(cooling.pos()).is(ModBlocks.METAL_FUNDIDO)) {
					level.setBlockAndUpdate(cooling.pos(), Blocks.AIR.defaultBlockState());
					level.sendParticles(ParticleTypes.SMOKE, cooling.pos().getX() + 0.5, cooling.pos().getY() + 0.3, cooling.pos().getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
				}
				COOLING.remove(i);
			}
		}
	}

	/** Whether a tipped brasero is still running (for the tests). */
	public static boolean flowing(ServerLevel level) {
		return FLOWS.stream().anyMatch(flow -> flow.level() == level);
	}

	/** An arrow, a trident or a snowball a player threw at a full brasero's bowl tips it. */
	private static void arrows(ServerLevel level) {
		for (int i = 0; i < 4; i++) {
			if (!full(level, i)) {
				continue;
			}
			BlockPos bowl = brazier(i);
			AABB box = new AABB(bowl).inflate(1.3, 1.0, 1.3);
			for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box, shot -> shot.getOwner() instanceof Player)) {
				tip(level, i);
				projectile.discard();
				break;
			}
		}
	}

	// ------------------------------------------------------------------ the constellations

	/** The colour this phase lights a constellation in: 0 silver to 4 crimson, by the doc's table. */
	public static int tier(int phase, RandomSource random) {
		int[] weights = WEIGHTS[Mth.clamp(phase, 1, 3) - 1];
		int roll = random.nextInt(100);
		for (int tier = 0; tier < weights.length; tier++) {
			roll -= weights[tier];
			if (roll < 0) {
				return tier;
			}
		}
		return 2;
	}

	private static void sky(ServerLevel level, FallenSmith smith, State state, long now) {
		if (smith.busy() || now < state.nextSky()) {
			return;
		}
		RandomSource random = level.getRandom();
		int index;
		long next;
		if (smith.isReforging()) {
			// While his forge burns, the sky only rains star iron: what the braseros are filled with.
			index = MARTILLO;
			next = now + REFORGE_SKY;
		} else {
			List<Integer> up = new ArrayList<>();
			float spin = StarChart.spin(now / 20.0);
			double floor = Math.sin(Math.toRadians(15.0));
			for (int i = 0; i < StarChart.CONSTELLATIONS.size(); i++) {
				if (StarChart.turn(StarChart.CONSTELLATIONS.get(i).centre(), spin).y >= floor) {
					up.add(i);
				}
			}
			index = up.isEmpty() ? random.nextInt(8) : up.get(random.nextInt(up.size()));
			next = now + SKY_MIN + random.nextInt(SKY_SPREAD + 1);
		}
		cast(level, smith, index, tier(smith.phase(), random));
		save(level, new State(state.stage(), state.boss(), state.participants(), state.owed(), state.at(), next, state.fights()));
	}

	/**
	 * A constellation lights up and casts: every client is told to draw it (its colour, and the thread of
	 * red for his side or of pale gold for the players'), the warning goes down on the floor where it will
	 * land, and CAST_TICKS later it does.
	 */
	public static void cast(ServerLevel level, FallenSmith smith, int index, int tier) {
		RandomSource random = level.getRandom();
		ServerPlayer target = smith.getTarget() instanceof ServerPlayer player ? player : null;
		List<ServerPlayer> here = fighters(level);
		if (target == null && !here.isEmpty()) {
			target = here.get(random.nextInt(here.size()));
		}
		Vec3 at = target != null ? target.position() : smith.position();
		double angle = index == HACHA ? Math.atan2(at.z - smith.getZ(), at.x - smith.getX()) : random.nextDouble() * Math.PI;
		float strength = STRENGTH[tier];
		int storm = index == GUADANA ? Math.round(160 * strength) : 0;
		StarCast.sendAll(level, new StarCast(index, tier, FOR_BOSS[index], storm));
		boolean forBoss = FOR_BOSS[index];
		Component name = Component.translatable("gui.forja.constelacion." + StarChart.CONSTELLATIONS.get(index).name());
		Component colour = Component.translatable("gui.forja.constelacion.color." + tier);
		for (ServerPlayer player : level.players()) {
			player.sendOverlayMessage(Component.translatable(forBoss ? "gui.forja.constelacion.herrero" : "gui.forja.constelacion.jugadores", name, colour)
				.withColor(forBoss ? 0xFF5A48 : 0x9CFFD8));
		}
		level.playSound(null, smith.blockPosition(), forBoss ? SoundEvents.BELL_RESONATE : SoundEvents.AMETHYST_BLOCK_RESONATE,
			SoundSource.HOSTILE, 4.0F, forBoss ? 0.5F : 1.4F);
		CASTS.add(new Cast(level, index, tier, level.getGameTime() + CAST_TICKS, at, angle));
	}

	private static void rollCasts(ServerLevel level, long now) {
		for (int i = CASTS.size() - 1; i >= 0; i--) {
			Cast cast = CASTS.get(i);
			if (cast.level() != level) {
				continue;
			}
			FallenSmith smith = boss(level);
			if (smith == null) {
				CASTS.remove(i);
				continue;
			}
			long left = cast.fireAt() - now;
			if (left > 0) {
				warn(level, smith, cast, left);
				continue;
			}
			if (left > -40) {
				land(level, smith, cast, -left);
				continue;
			}
			CASTS.remove(i);
		}
	}

	/** The line of the Tajo celeste: 16 blocks through where the cast was aimed, at its angle. */
	private static Vec3[] blade(Cast cast) {
		Vec3 dir = new Vec3(Math.cos(cast.angle()), 0.0, Math.sin(cast.angle()));
		Vec3 centre = new Vec3(cast.at().x, StarYard.SURFACE + 1.05, cast.at().z);
		return new Vec3[] {centre.subtract(dir.scale(8.0)), centre.add(dir.scale(8.0))};
	}

	private static double toSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double t = Mth.clamp(point.subtract(a).dot(ab) / ab.lengthSqr(), 0.0, 1.0);
		Vec3 nearest = a.add(ab.scale(t));
		return Math.hypot(point.x - nearest.x, point.z - nearest.z);
	}

	/** The warnings on the floor while the constellation is still casting. */
	private static void warn(ServerLevel level, FallenSmith smith, Cast cast, long left) {
		if (left % 4 != 0) {
			return;
		}
		switch (cast.index()) {
			case ESPADA -> {
				Vec3[] line = blade(cast);
				for (int s = 0; s <= 32; s++) {
					Vec3 p = line[0].lerp(line[1], s / 32.0);
					level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
			case HACHA -> {
				for (int s = 0; s <= 24; s++) {
					double a = cast.angle() - Math.PI / 4.0 + Math.PI / 2.0 * s / 24.0;
					level.sendParticles(ParticleTypes.CRIMSON_SPORE, smith.getX() + Math.cos(a) * 12.0, StarYard.SURFACE + 1.1,
						smith.getZ() + Math.sin(a) * 12.0, 2, 0.1, 0.0, 0.1, 0.0);
				}
			}
			case LANZA -> {
				for (Vec3 spear : spears(level, smith, cast)) {
					level.sendParticles(ParticleTypes.END_ROD, spear.x, StarYard.SURFACE + 1.2, spear.z, 3, 0.3, 0.0, 0.3, 0.0);
				}
			}
			case YUNQUE -> {
				for (ServerPlayer player : fighters(level)) {
					level.sendParticles(ParticleTypes.GLOW, player.getX(), player.getY() + 3.0, player.getZ(), 4, 0.5, 0.5, 0.5, 0.0);
				}
			}
			default -> {
			}
		}
	}

	/** Where the light lances of the Lanza come down: on him, on his apprentices, then round him. */
	private static List<Vec3> spears(ServerLevel level, FallenSmith smith, Cast cast) {
		int count = new int[] {3, 4, 5, 6, 8}[cast.tier()];
		List<Vec3> at = new ArrayList<>();
		at.add(smith.position());
		for (Mob apprentice : level.getEntitiesOfClass(Mob.class, new AABB(smith.blockPosition()).inflate(24.0), Apprentices::isApprentice)) {
			if (at.size() >= count) {
				break;
			}
			at.add(apprentice.position());
		}
		RandomSource fixed = RandomSource.create(cast.fireAt());
		while (at.size() < count) {
			double a = fixed.nextDouble() * Math.PI * 2.0;
			double r = 2.0 + fixed.nextDouble() * 4.0;
			at.add(smith.position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r));
		}
		return at;
	}

	/** The constellation lands. {@code into} ticks since it did: most act once, the Tajo burns 2 s. */
	private static void land(ServerLevel level, FallenSmith smith, Cast cast, long into) {
		float m = STRENGTH[cast.tier()];
		if (cast.index() == ESPADA) {
			Vec3[] line = blade(cast);
			if (into % 5 == 0) {
				for (int s = 0; s <= 32; s += 2) {
					Vec3 p = line[0].lerp(line[1], s / 32.0);
					level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.15, 0.1, 0.15, 0.01);
				}
				for (ServerPlayer player : fighters(level)) {
					if (toSegment(player.position(), line[0], line[1]) < 0.9) {
						player.invulnerableTime = 0;
						player.hurtServer(level, level.damageSources().indirectMagic(smith, smith), 6.0F * m / 8.0F);
						player.igniteForSeconds(2.0F);
					}
				}
			}
			if (into == 0) {
				level.playSound(null, BlockPos.containing(cast.at()), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 3.0F, 0.6F);
				// A full brasero the burning line crosses tips over by itself.
				for (int i = 0; i < 4; i++) {
					BlockPos bowl = brazier(i);
					if (toSegment(Vec3.atBottomCenterOf(bowl), line[0], line[1]) < 2.0) {
						tip(level, i);
					}
				}
			}
			return;
		}
		if (into != 0) {
			return;
		}
		switch (cast.index()) {
			case HACHA -> {
				for (ServerPlayer player : fighters(level)) {
					Vec3 off = player.position().subtract(smith.position());
					double reach = off.horizontalDistance();
					double a = Math.atan2(off.z, off.x);
					if (reach <= 12.0 && Math.abs(Mth.wrapDegrees(Math.toDegrees(a - cast.angle()))) <= 45.0) {
						player.invulnerableTime = 0;
						player.hurtServer(level, level.damageSources().indirectMagic(smith, smith), 7.0F * m);
						player.push(off.x / Math.max(1.0, reach) * 1.2, 0.4, off.z / Math.max(1.0, reach) * 1.2);
						player.hurtMarked = true;
					}
				}
				level.playSound(null, smith.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 4.0F, 0.5F);
			}
			case ESCUDO -> smith.aegis(20.0F * m, 200);
			case GUADANA -> {
				stormUntil = level.getGameTime() + Math.round(160 * m);
				double a = level.getRandom().nextDouble() * Math.PI * 2.0;
				wind = new Vec3(Math.cos(a), 0.0, Math.sin(a)).scale(0.06 * m);
				level.playSound(null, smith.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 3.0F, 0.5F);
			}
			case MARTILLO -> {
				int meteors = new int[] {1, 1, 2, 2, 3}[cast.tier()];
				for (int i = 0; i < meteors; i++) {
					BlockPos ground = BlockPos.containing(smith.getX() + (i == 0 ? 0 : level.getRandom().nextInt(5) - 2),
						StarYard.SURFACE + 1, smith.getZ() + (i == 0 ? 0 : level.getRandom().nextInt(5) - 2));
					WorldEvents.skyMeteor(level, ground, 10.0F * m);
				}
				// And one by every player, for the iron the braseros are filled with.
				if (smith.isReforging()) {
					for (ServerPlayer player : fighters(level)) {
						double a = level.getRandom().nextDouble() * Math.PI * 2.0;
						WorldEvents.skyMeteor(level, BlockPos.containing(player.getX() + Math.cos(a) * 4.0, StarYard.SURFACE + 1,
							player.getZ() + Math.sin(a) * 4.0), 10.0F * m);
					}
				}
			}
			case LANZA -> {
				for (Vec3 spear : spears(level, smith, cast)) {
					level.sendParticles(ParticleTypes.END_ROD, spear.x, StarYard.SURFACE + 4.0, spear.z, 40, 0.1, 3.0, 0.1, 0.0);
					for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, new AABB(BlockPos.containing(spear)).inflate(1.2, 2.0, 1.2),
						living -> living.isAlive() && !(living instanceof Player))) {
						hit.invulnerableTime = 0;
						hit.hurtServer(level, level.damageSources().magic(), 5.0F * m);
					}
				}
				level.playSound(null, smith.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.AMBIENT, 2.5F, 1.4F);
			}
			case YUNQUE -> {
				for (ServerPlayer player : fighters(level)) {
					player.heal(4.0F * m);
					player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, m >= 1.3F ? 1 : 0));
					player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0));
					level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 3.0, player.getZ(), 30, 0.6, 1.5, 0.6, 0.02);
				}
				level.playSound(null, smith.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.AMBIENT, 2.0F, 1.2F);
			}
			case TENAZAS -> {
				for (Mob apprentice : level.getEntitiesOfClass(Mob.class, new AABB(smith.blockPosition()).inflate(32.0), Apprentices::isApprentice)) {
					Player near = level.getNearestPlayer(apprentice, 32.0);
					Vec3 away = near == null ? apprentice.position().subtract(smith.position()) : apprentice.position().subtract(near.position());
					away = away.multiply(1.0, 0.0, 1.0).normalize().scale(1.4 * m);
					apprentice.push(away.x, 0.45, away.z);
					apprentice.hurtMarked = true;
					apprentice.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
				}
				// And whoever is closest to going down is pulled clear of him.
				fighters(level).stream().filter(player -> player.getHealth() < player.getMaxHealth() * 0.3F).findFirst().ifPresent(player -> {
					Vec3 out = player.position().subtract(smith.position()).multiply(1.0, 0.0, 1.0).normalize().scale(1.3 * m);
					player.push(out.x, 0.5, out.z);
					player.hurtMarked = true;
				});
				level.playSound(null, smith.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.AMBIENT, 3.0F, 0.7F);
			}
			default -> {
			}
		}
	}

	/** The Guadaña's ash storm: the wind on everyone, while it lasts. */
	private static void blowStorm(ServerLevel level, long now) {
		if (now >= stormUntil) {
			return;
		}
		for (ServerPlayer player : fighters(level)) {
			player.push(wind.x, 0.0, wind.z);
			player.hurtMarked = true;
		}
		if (now % 3 == 0) {
			for (ServerPlayer player : fighters(level)) {
				level.sendParticles(dev.forja.registry.ModParticles.CENIZA, player.getX(), player.getY() + 1.5, player.getZ(), 12, 4.0, 2.0, 4.0, 0.05);
			}
		}
	}

	/** Whether the Guadaña's storm is blowing (the smith sees less in it). */
	public static boolean storming(ServerLevel level) {
		return level.getGameTime() < stormUntil;
	}
}
