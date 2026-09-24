package dev.forja.upgrade;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import dev.forja.forge.Mastery;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Cosechador: right-clicking a ripe crop with the hoe harvests and replants it (1x1 up to 5x5).
 * Magnetismo: armor that pulls dropped items and experience toward the player; sneaking pauses it.
 * Nutricion, Sonar and Purificacion: chestplate, helmet and any-piece effects every 5 seconds.
 * Vision nocturna: a helmet at 100% keeps night vision on. Regeneracion and Autorreparacion heal the
 * wearer and mend items every 5 seconds.
 */
public final class FieldUpgrades {

	/** Segundo aliento wakes below this share of health, and the absorption it gives lasts this long. */
	private static final float SECOND_WIND_HEALTH = 0.3F;
	private static final int SECOND_WIND_TICKS = 400;

	/** The armor a diamond talisman lends, as a modifier that comes and goes with the stone. */
	private static final net.minecraft.resources.Identifier TALISMAN_ARMOR = dev.forja.Forja.id("talisman");
	private static final net.minecraft.resources.Identifier TALISMAN_KNOCKBACK = dev.forja.Forja.id("talisman_empuje");

	private FieldUpgrades() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			ItemStack stack = player.getItemInHand(hand);
			int radius = Upgrade.harvestRadius(Upgrades.fraction(stack, Upgrade.COSECHADOR));
			ForgedParts held = stack.get(ModComponents.PARTS);
			if (Synergy.SEGADOR.active(stack)) {
				radius = Math.min(3, radius + 1);
			}
			if (held != null && held.type() == dev.forja.forge.ForgeType.GUADANA && !stack.isBroken()) {
				// The scythe reaps 3x3 on its own; Cosechador widens it.
				radius = Math.min(3, radius + 2);
			}
			BlockState clicked = level.getBlockState(hitResult.getBlockPos());
			if (radius < 0 || !(clicked.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(clicked)) {
				return InteractionResult.PASS;
			}
			if (!(level instanceof ServerLevel serverLevel)) {
				return InteractionResult.SUCCESS;
			}

			int harvested = 0;
			for (BlockPos pos : BlockPos.betweenClosed(hitResult.getBlockPos().offset(-radius, 0, -radius), hitResult.getBlockPos().offset(radius, 0, radius))) {
				BlockState state = serverLevel.getBlockState(pos);
				if (state.getBlock() instanceof CropBlock ripe && ripe.isMaxAge(state)) {
					harvestAndReplant(serverLevel, (ServerPlayer) player, pos.immutable(), state, ripe, stack);
					harvested++;
				}
			}
			if (harvested > 0) {
				stack.hurtAndBreak(1, player, hand);
				serverLevel.playSound(null, hitResult.getBlockPos(), SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		});

		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			long time = level.getGameTime();
			if (time % 2 != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) {
					continue;
				}
				meteor(level, player);
				talisman(player);
				setAura(level, player, time);
				// Pacto de sombra: crouching in that armor takes you out of sight.
				if (player.isShiftKeyDown() && Upgrades.armorFraction(player, Upgrade.PACTO_DE_SOMBRA) >= 1.0F) {
					player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, true, false));
				}
				secondWind(player);
				float magnet = Upgrade.magnetRadius(Upgrades.armorFraction(player, Upgrade.MAGNETISMO));
				if (magnet > 0.0F && !player.isShiftKeyDown()) {
					pull(level, player, magnet);
				}
				if (time % 40 == 0 && Upgrades.fraction(player.getItemBySlot(EquipmentSlot.HEAD), Upgrade.VISION_NOCTURNA) >= 1.0F) {
					// 20 s refreshed every 2 s: never drops into the flickering last 10 seconds.
					player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
				}
				if (time % 20 == 0) {
					ArmorSets.update(player);
					anchorHold(player);
					douse(player);
					shakeOff(player);
				}
				if (time % 100 == 0) {
					float regeneration = Upgrade.regeneration(Upgrades.armorFraction(player, Upgrade.REGENERACION));
					if (regeneration > 0.0F && player.getHealth() < player.getMaxHealth()) {
						player.heal(regeneration);
					}
					selfRepair(level, player);
					armorEffects(level, player);
					TraitEffects.armorTick(level, player);
				}
			}
		});
	}

	private static void harvestAndReplant(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, CropBlock crop, ItemStack tool) {
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, tool);
		// The crop's own item is its seed: keep one back to replant.
		for (ItemStack drop : drops) {
			if (drop.is(crop.asItem())) {
				drop.shrink(1);
				break;
			}
		}
		level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
		level.levelEvent(2001, pos, Block.getId(state));
		// Telekinesis sends a share of what you harvest to your pockets, the same as what you mine;
		// Segador (with Cosechador) makes it every last crop.
		float toInventory = Upgrades.fraction(tool, Upgrade.TELEQUINESIS);
		ForgedParts held = tool.get(ModComponents.PARTS);
		if (held != null && held.hasTrait(ForgeMaterial.Trait.DEL_END)) {
			toInventory = Math.max(toInventory, 0.3F);
		}
		if (Synergy.SEGADOR.active(tool)) {
			toInventory = 1.0F;
		}
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			if (toInventory > 0.0F && level.getRandom().nextFloat() < toInventory && player.getInventory().add(drop) && drop.isEmpty()) {
				continue;
			}
			Block.popResource(level, pos, drop);
		}
	}

	/** The one talisman the pack found: the two that simply sit there and work go through here. */
	private static void talisman(ServerPlayer player) {
		dev.forja.item.Talisman carried = dev.forja.item.Talisman.active(player);
		if (carried == dev.forja.item.Talisman.ESMERALDA) {
			// The emerald opens doors: village prices drop the way they do for a hero.
			player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 60, 0, true, false));
		}
		if (carried == dev.forja.item.Talisman.PRISMARINA && player.isInWater()) {
			// The sea stops arguing: breath, sight and a working pair of hands under it.
			player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 60, 0, true, false));
		}
		AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
		if (armor == null) {
			return;
		}
		boolean shouldHave = carried == dev.forja.item.Talisman.DIAMANTE;
		boolean has = armor.getModifier(TALISMAN_ARMOR) != null;
		if (shouldHave && !has) {
			armor.addTransientModifier(new AttributeModifier(TALISMAN_ARMOR, dev.forja.item.Talisman.ARMOR, AttributeModifier.Operation.ADD_VALUE));
		} else if (!shouldHave && has) {
			armor.removeModifier(TALISMAN_ARMOR);
		}

		AttributeInstance steady = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (steady == null) {
			return;
		}
		boolean shouldStand = carried == dev.forja.item.Talisman.NETERITA;
		boolean stands = steady.getModifier(TALISMAN_KNOCKBACK) != null;
		if (shouldStand && !stands) {
			steady.addTransientModifier(new AttributeModifier(TALISMAN_KNOCKBACK, dev.forja.item.Talisman.KNOCKBACK, AttributeModifier.Operation.ADD_VALUE));
		} else if (!shouldStand && stands) {
			steady.removeModifier(TALISMAN_KNOCKBACK);
		}
	}

	/**
	 * Meteoro: sneaking in mid-air with a mace that has Densidad and Onda de choque drags the player
	 * straight down, so the smash lands when you want it to.
	 */
	private static void meteor(ServerLevel level, ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (player.onGround() || !player.isShiftKeyDown() || !Synergy.METEORO.active(held)) {
			return;
		}
		Vec3 motion = player.getDeltaMovement();
		if (motion.y > -1.6) {
			player.setDeltaMovement(motion.x * 0.6, Math.min(motion.y - 0.55, -0.6), motion.z * 0.6);
			player.hurtMarked = true;
			level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY(), player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
		}
	}

	/** Nutricion feeds, Sonar outlines nearby monsters, Purificacion cleans poison and wither. Every 5 seconds. */
	/**
	 * Segundo aliento: a breastplate that is both tough and mending answers a bad wound once with two
	 * hearts of absorption and a moment of regeneration. The absorption is its own cooldown: while it
	 * holds, nothing more comes.
	 */
	private static void secondWind(ServerPlayer player) {
		if (player.getAbsorptionAmount() > 0.0F || player.getHealth() > player.getMaxHealth() * SECOND_WIND_HEALTH) {
			return;
		}
		if (!Synergy.SEGUNDO_ALIENTO.active(player.getItemBySlot(EquipmentSlot.CHEST))) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, SECOND_WIND_TICKS, 0, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.6F);
	}

	private static void armorEffects(ServerLevel level, ServerPlayer player) {
		float feed = Upgrade.nutritionChance(Upgrades.fraction(player.getItemBySlot(EquipmentSlot.CHEST), Upgrade.NUTRICION));
		if (feed > 0.0F && player.getFoodData().needsFood() && level.getRandom().nextFloat() < feed) {
			player.getFoodData().eat(1, 0.5F);
		}

		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		float sonar = Upgrade.sonarRadius(Upgrades.fraction(helmet, Upgrade.SONAR));
		if (sonar > 0.0F) {
			// Vigia: the helmet reaches half again as far and what it finds keeps glowing twice as long.
			boolean watchman = Synergy.VIGIA.active(helmet);
			float reach = watchman ? sonar * 1.5F : sonar;
			int glow = watchman ? 240 : 120;
			for (LivingEntity monster : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach), e -> e instanceof Enemy && e.isAlive())) {
				monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, glow, 0, true, false), player);
			}
		}

		float purify = Upgrade.purifyChance(Upgrades.armorFraction(player, Upgrade.PURIFICACION));
		if (purify > 0.0F && (player.hasEffect(MobEffects.POISON) || player.hasEffect(MobEffects.WITHER)) && level.getRandom().nextFloat() < purify) {
			player.removeEffect(MobEffects.POISON);
			player.removeEffect(MobEffects.WITHER);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.6F, 1.2F);
		}
	}

	/** The bad effects Temple will work on. Anything that is your own doing is left alone. */
	private static final List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> SHAKEABLE = List.of(
		MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS,
		MobEffects.BLINDNESS, MobEffects.NAUSEA, MobEffects.DARKNESS, MobEffects.UNLUCK
	);

	private static final net.minecraft.resources.Identifier ANCHOR_ID = dev.forja.Forja.id("anclaje");

	/**
	 * The once-a-second pass on one player, on demand.
	 *
	 * <p>The gametest drives this instead of waiting for the clock, so an upgrade that quietly does
	 * nothing is caught by its effect rather than by its presence in the enum.
	 */
	public static void tickForTest(ServerLevel level, ServerPlayer player) {
		ArmorSets.update(player);
		anchorHold(player);
		douse(player);
		shakeOff(player);
	}

	/**
	 * A full set of one material, worn, showing.
	 *
	 * <p>Wearing four pieces of the same material is a real commitment — it costs you the freedom to
	 * mix plates for the stats you want — and it paid two points of armour and a line in a tooltip. So
	 * a mote of that material's own colour, at the feet, now and then.
	 *
	 * <p>Once a second, one particle. That is on purpose: this is on all the time, for hours, and the
	 * job is to be noticeable when somebody looks rather than to be noticed when they are not.
	 */
	private static void setAura(ServerLevel level, ServerPlayer player, long time) {
		if (time % 20 != 0) {
			return;
		}
		dev.forja.material.ForgeMaterial set = ArmorSets.fullSet(player);
		if (set == null) {
			return;
		}
		double angle = time * 0.09;
		level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(set.color, 0.9F),
			player.getX() + Math.cos(angle) * 0.55, player.getY() + 0.12, player.getZ() + Math.sin(angle) * 0.55,
			1, 0.02, 0.01, 0.02, 0.0);
	}

	/** Anclaje against ordinary knockback; the pulls the mod's own mobs use ask Upgrades.anchor instead. */
	private static void anchorHold(ServerPlayer player) {
		var instance = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
		if (instance == null) {
			return;
		}
		float hold = dev.forja.upgrade.Upgrades.anchor(player);
		var present = instance.getModifier(ANCHOR_ID);
		if (hold <= 0.0F) {
			if (present != null) {
				instance.removeModifier(ANCHOR_ID);
			}
		} else if (present == null || Math.abs(present.amount() - hold) > 0.001) {
			instance.removeModifier(ANCHOR_ID);
			instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
				ANCHOR_ID, hold, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Aislante: clay under the plate, so what sets you alight does not keep you that way. */
	private static void douse(ServerPlayer player) {
		int shed = Upgrade.dousing(Upgrades.armorFraction(player, Upgrade.AISLANTE));
		if (shed > 0 && player.getRemainingFireTicks() > 0) {
			player.setRemainingFireTicks(Math.max(0, player.getRemainingFireTicks() - shed));
		}
	}

	/** Temple: whatever was put on you runs down faster, which is the answer to the hollow plate's wail. */
	private static void shakeOff(ServerPlayer player) {
		float resolve = Upgrade.resolveShare(Upgrades.armorFraction(player, Upgrade.TEMPLE));
		if (resolve <= 0.0F) {
			return;
		}
		int shed = Math.max(1, Math.round(20 * resolve));
		for (var effect : SHAKEABLE) {
			MobEffectInstance active = player.getEffect(effect);
			if (active == null || active.isInfiniteDuration()) {
				continue;
			}
			int left = active.getDuration() - shed;
			player.removeEffect(effect);
			if (left > 0) {
				player.addEffect(new MobEffectInstance(effect, left, active.getAmplifier(),
					active.isAmbient(), active.isVisible(), active.showIcon()));
			}
		}
	}

	/** Autorreparacion, and crying obsidian parts at night: carried or worn items mend a little. */
	private static void selfRepair(ServerLevel level, ServerPlayer player) {
		Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			seen.add(player.getInventory().getItem(i));
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			seen.add(player.getItemBySlot(slot));
		}
		for (ItemStack stack : seen) {
			// Mending itself still works on broken gear.
			int repair = Upgrade.selfRepair(stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.AUTORREPARACION) / 100.0F);
			ForgedParts parts = stack.get(ModComponents.PARTS);
			if (parts != null && parts.hasTrait(ForgeMaterial.Trait.LLANTO) && level.isDarkOutside()) {
				repair += 2;
			}
			// Ascua: cinereous gear mends while the smith burns, which is the only way to use it well.
			repair += TraitEffects.emberRepair(stack, player);
			if (repair > 0 && stack.isDamaged()) {
				stack.setDamageValue(Math.max(0, stack.getDamageValue() - repair));
			}
		}
	}

	private static void pull(ServerLevel level, ServerPlayer player, double radius) {
		Vec3 target = player.position().add(0.0, 0.6, 0.0);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(radius), e -> !e.hasPickUpDelay())) {
			item.setDeltaMovement(target.subtract(item.position()).normalize().scale(0.35));
		}
		for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, player.getBoundingBox().inflate(radius), e -> true)) {
			orb.setDeltaMovement(target.subtract(orb.position()).normalize().scale(0.35));
		}
	}
}
