package dev.forja.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.serialization.Codec;
import dev.forja.Forja;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.ForjaDifficulty;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The fights the world starts on its own (ideas 91 to 100 of the plan): sieges of a forge, thieves who
 * run off with what you made (and fight with it), named foes who come back, duels, and the dark and the
 * rain changing how the monsters fight.
 */
public final class WorldFights {
	/** How often each is considered, per player, in ticks. */
	public static final int CHECK = 1200;
	public static final double SIEGE_FORGE_RANGE = 16.0;
	public static final int SIEGE_MIN = 6;
	public static final int SIEGE_MAX = 10;
	public static final double THIEF_CHANCE = 0.1;
	public static final double NEMESIS_CHANCE = 0.1;

	/** The named foes that got away from each player: "type|name" entries, kept on the player. */
	@SuppressWarnings("deprecation")
	public static final AttachmentType<List<String>> NEMESES = AttachmentRegistry.<List<String>>builder()
		.initializer(List::of)
		.persistent(Codec.STRING.listOf())
		.copyOnDeath()
		.buildAndRegister(Forja.id("nemesis"));

	private static final Map<Mob, Boolean> THIEVES = new WeakHashMap<>();

	private WorldFights() {
	}

	public static void register() {
		java.util.Objects.requireNonNull(NEMESES);
		ServerTickEvents.END_SERVER_TICK.register(WorldFights::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!blocked && taken > 0.0F && entity instanceof ServerPlayer player && source.getEntity() instanceof Mob mob
				&& source.getDirectEntity() == mob) {
				steal(mob, player);
			}
		});
		Duels.register();
	}

	private static void tick(MinecraftServer server) {
		if (!CombatConfig.get().enabled || server.getTickCount() % CHECK != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || player.isSpectator() || !(player.level() instanceof ServerLevel level) || !level.isDarkOutside()) {
				continue;
			}
			if (level.getRandom().nextDouble() < CombatConfig.get().siegeChance * ForjaDifficulty.current().threat && forgeNear(player) != null) {
				siege(level, player);
			} else if (level.getRandom().nextDouble() < NEMESIS_CHANCE) {
				nemesisReturns(level, player);
			}
		}
	}

	// --- 91: sieges ------------------------------------------------------------------------------------

	/** A block of Forja's within 16 of the player (their forge), or null. */
	public static BlockPos forgeNear(Player player) {
		BlockPos center = player.blockPosition();
		int r = (int) SIEGE_FORGE_RANGE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 4, r))) {
			if (Forja.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()).getNamespace())) {
				return pos.immutable();
			}
		}
		return null;
	}

	/**
	 * A band comes for the player's forge: 6 to 10 zombies and skeletons (more after many nights, with a
	 * cap), led by an elite, from about 30 blocks off. They take the forge as their home, so they defend
	 * the ground they take, and zombies dig and climb their way in.
	 */
	public static int siege(ServerLevel level, Player player) {
		BlockPos forge = forgeNear(player);
		BlockPos home = forge != null ? forge : player.blockPosition();
		int count = Math.min(SIEGE_MAX, SIEGE_MIN + dev.forja.difficulty.Nights.count(level) / 10);
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		int made = 0;
		for (int i = 0; i < count; i++) {
			EntityType<? extends Mob> type = i % 3 == 2 ? EntityTypes.SKELETON : EntityTypes.ZOMBIE;
			double a = angle + (i - count / 2.0) * 0.15;
			double x = player.getX() + Math.cos(a) * 30.0;
			double z = player.getZ() + Math.sin(a) * 30.0;
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
			Mob mob = type.create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			mob.snapTo(x, y, z, 0.0F, 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.EVENT, null);
			if (i == 0) {
				Threat.ELITE.mark(mob);
			}
			mob.addTag(Personality.HOME_TAG + home.getX() + "_" + home.getY() + "_" + home.getZ());
			mob.setPersistenceRequired();
			level.addFreshEntity(mob);
			mob.setTarget(player);
			made++;
		}
		player.sendOverlayMessage(Component.translatable("gui.forja.asedio"));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0F, 0.8F);
		return made;
	}

	// --- 92, 94: thieves -----------------------------------------------------------------------------------

	/**
	 * A cunning monster with an empty hand that lands a blow may take something forged out of the player's
	 * bag (never what they wear or hold) and run with it. It stays in the world until killed, and drops it
	 * then; a weapon it takes, it fights with, upgrades and all.
	 */
	private static void steal(Mob mob, ServerPlayer player) {
		if (Personality.trait(mob) != Personality.Trait.ASTUTO || !mob.getMainHandItem().isEmpty() && !mob.getOffhandItem().isEmpty()
			|| THIEVES.containsKey(mob) || mob.getRandom().nextDouble() >= THIEF_CHANCE) {
			return;
		}
		var inventory = player.getInventory();
		List<Integer> forged = new ArrayList<>();
		for (int slot = 9; slot < 36; slot++) {
			if (inventory.getItem(slot).has(dev.forja.registry.ModComponents.PARTS) || inventory.getItem(slot).getItem() instanceof dev.forja.item.PartItem) {
				forged.add(slot);
			}
		}
		if (forged.isEmpty()) {
			return;
		}
		int slot = forged.get(mob.getRandom().nextInt(forged.size()));
		ItemStack taken = inventory.removeItemNoUpdate(slot);
		boolean weapon = dev.forja.combat.SwingStyle.of(taken) != dev.forja.combat.SwingStyle.VANILLA;
		EquipmentSlot hand = weapon && mob.getMainHandItem().isEmpty() ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
		if (!mob.getItemBySlot(hand).isEmpty()) {
			hand = hand == EquipmentSlot.MAINHAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
		}
		mob.setItemSlot(hand, taken);
		mob.setDropChance(hand, 1.0F);
		mob.setPersistenceRequired();
		mob.addTag("forja_ladron");
		THIEVES.put(mob, true);
		player.sendOverlayMessage(Component.translatable("gui.forja.robado", taken.getHoverName()));
		mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	public static boolean thief(Mob mob) {
		return mob.entityTags().contains("forja_ladron");
	}

	// --- 95: named foes who come back ------------------------------------------------------------------------

	/** An elite or champion got away from the player: it will be back. */
	public static void remember(Mob mob, Player player) {
		if (Threat.of(mob).ordinal() < Threat.ELITE.ordinal()) {
			return;
		}
		List<String> list = new ArrayList<>(player.getAttachedOrElse(NEMESES, List.of()));
		String name = mob.hasCustomName() ? mob.getCustomName().getString() : mob.getType().getDescription().getString();
		list.add(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()) + "|" + name);
		while (list.size() > 5) {
			list.remove(0);
		}
		player.setAttached(NEMESES, List.copyOf(list));
	}

	public static Mob nemesisReturns(ServerLevel level, Player player) {
		List<String> list = new ArrayList<>(player.getAttachedOrElse(NEMESES, List.of()));
		if (list.isEmpty()) {
			return null;
		}
		String entry = list.remove(0);
		player.setAttached(NEMESES, List.copyOf(list));
		String[] parts = entry.split("\\|", 2);
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(parts[0]));
		if (type == null || !(type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob)) {
			return null;
		}
		double a = level.getRandom().nextDouble() * Math.PI * 2.0;
		double x = player.getX() + Math.cos(a) * 26.0;
		double z = player.getZ() + Math.sin(a) * 26.0;
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
		mob.snapTo(x, y, z, 0.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.EVENT, null);
		if (Threat.of(mob) != Threat.CAMPEON) {
			Threat.ELITE.mark(mob);
		}
		mob.setCustomName(Component.translatable("entity.forja.nemesis", parts.length > 1 ? parts[1] : type.getDescription().getString()));
		mob.setCustomNameVisible(true);
		mob.addTag(Personality.GRUDGE_TAG + player.getUUID());
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		mob.setTarget(player);
		player.sendOverlayMessage(Component.translatable("gui.forja.nemesis", mob.getCustomName()));
		return mob;
	}

	// --- 98: night, 99: rain ---------------------------------------------------------------------------------

	/** At night, a player in the dark and more than 12 blocks off cannot be followed by sight. */
	public static boolean hiddenByNight(Mob mob, Player player) {
		return mob.level().isDarkOutside() && mob.distanceTo(player) > 12.0
			&& mob.level().getMaxLocalRawBrightness(player.blockPosition()) < 4;
	}

	/** In the rain, an arrow flies wider: up to 6° off to the side and 3° up or down. */
	public static void rainSpread(Mob archer, net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow) {
		if (!archer.level().isRainingAt(archer.blockPosition())) {
			return;
		}
		var random = archer.getRandom();
		Vec3 v = arrow.getDeltaMovement().yRot((float) Math.toRadians((random.nextDouble() * 2.0 - 1.0) * 6.0))
			.xRot((float) Math.toRadians((random.nextDouble() * 2.0 - 1.0) * 3.0));
		arrow.setDeltaMovement(v);
	}

	// --- 100: the smith remembers --------------------------------------------------------------------------

	/** The fallen smith's memory of a player: how often his wave and his backhand missed them. */
	public static final String SMITH_WAVE_MISS = "forja_herrero_onda_fallo";
	public static final String SMITH_CLOSE_MISS = "forja_herrero_reves_fallo";

	/** Whether the smith should open close (backhand, hook) against this player rather than with the wave. */
	public static boolean smithPrefersClose(net.minecraft.world.entity.LivingEntity player) {
		return count(player, SMITH_WAVE_MISS) > count(player, SMITH_CLOSE_MISS) + 1;
	}

	/** A heavy blow of the smith's landed; if the player it was meant for took nothing, it missed. */
	public static void smithBlowLanded(dev.forja.entity.FallenSmith smith, boolean wave) {
		if (!(smith.getTarget() instanceof Player player)) {
			return;
		}
		float health = player.getHealth();
		long at = smith.level().getGameTime() + 10;
		PENDING.add(new Object[] {player, health, at, wave});
	}

	private static final List<Object[]> PENDING = new ArrayList<>();

	static void checkSmithMisses(long now) {
		for (Iterator<Object[]> it = PENDING.iterator(); it.hasNext();) {
			Object[] p = it.next();
			if (now < (Long) p[2]) {
				continue;
			}
			it.remove();
			Player player = (Player) p[0];
			if (player.isAlive() && player.getHealth() >= (Float) p[1]) {
				bump(player, (Boolean) p[3] ? SMITH_WAVE_MISS : SMITH_CLOSE_MISS);
			}
		}
	}

	private static int count(net.minecraft.world.entity.LivingEntity entity, String prefix) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(prefix + "_")) {
				try {
					return Integer.parseInt(tag.substring(prefix.length() + 1));
				} catch (NumberFormatException ignored) {
					return 0;
				}
			}
		}
		return 0;
	}

	private static void bump(Player player, String prefix) {
		int n = count(player, prefix);
		player.removeTag(prefix + "_" + n);
		player.addTag(prefix + "_" + (n + 1));
	}

	static {
		ServerTickEvents.END_SERVER_TICK.register(server -> checkSmithMisses(server.overworld().getGameTime()));
	}

	/** Whether this monster is on the side a duel or a siege counts: any hostile. */
	static boolean hostile(Mob mob) {
		return mob instanceof Enemy;
	}

	/** Visible ring on the ground, for duels and marks. */
	static void ring(ServerLevel level, Vec3 center, double radius) {
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2.0 / 24.0;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}
}
