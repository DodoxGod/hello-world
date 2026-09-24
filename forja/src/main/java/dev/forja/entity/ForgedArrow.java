package dev.forja.entity;

import dev.forja.forge.ForgeStats;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModEntities;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An arrow forged from a tip and a fletching. The tip decides what it does on impact and the fletching
 * how fast it leaves the string, which is why a light fletching is worth as much as a heavy tip.
 */
public class ForgedArrow extends Arrow {
	private float speed = 1.0F;
	private byte pierce;

	public ForgedArrow(EntityType<? extends ForgedArrow> type, Level level) {
		super(type, level);
	}

	public ForgedArrow(Level level, LivingEntity shooter, ItemStack arrow, ItemStack weapon) {
		super(ModEntities.FLECHA_FORJADA, level);
		this.setOwner(shooter);
		this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
		this.setPickupItemStack(arrow.copyWithCount(1));
		this.apply(arrow);
		// Tirador: a bow with the gift throws everything it looses harder.
		if (dev.forja.forge.Perk.has(weapon, dev.forja.forge.Perk.TIRADOR)) {
			this.speed *= dev.forja.forge.Perk.TIRADOR_SPEED;
		}
	}

	/** Reads the parts and upgrades of the stack it came from, once, when it is created. */
	private void apply(ItemStack arrow) {
		ForgedParts parts = arrow.get(ModComponents.PARTS);
		if (parts == null) {
			return;
		}
		Upgrades upgrades = arrow.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		ForgeStats.Sheet sheet = ForgeStats.sheet(arrow, parts);
		this.setBaseDamage(sheet.arrowDamage);
		this.speed = sheet.drawSpeed;
		this.pierce = (byte) Math.round(Upgrade.arrowPierce(upgrades.percent(Upgrade.PUNTA_PERFORANTE) / 100.0F));
		int poison = Math.round(Upgrade.arrowPoisonSeconds(upgrades.percent(Upgrade.PUNTA_ENVENENADA) / 100.0F) * 20.0F);
		if (poison > 0) {
			this.addEffect(new MobEffectInstance(MobEffects.POISON, poison, 0));
		}
		int fire = Math.round(Upgrade.arrowFireSeconds(upgrades.percent(Upgrade.PUNTA_IGNEA) / 100.0F));
		if (fire > 0) {
			this.igniteForSeconds(fire);
		}
		// Punta maldita: poison and fire in the same tip end in wither.
		if (dev.forja.upgrade.Synergy.PUNTA_MALDITA.active(arrow)) {
			this.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
		}
		// Asta perfecta: quick and sharp at once, so it leaves the string critical.
		if (dev.forja.upgrade.Synergy.ASTA_PERFECTA.active(arrow)) {
			this.setCritArrow(true);
		}
		// Cargador: a quarter of these arrows never really left the quiver.
		if (dev.forja.forge.Perk.has(arrow, dev.forja.forge.Perk.CARGADOR)
			&& this.getOwner() instanceof net.minecraft.world.entity.player.Player archer
			&& archer.getRandom().nextFloat() < dev.forja.forge.Perk.CARGADOR_SHARE) {
			ItemStack spare = arrow.copyWithCount(1);
			if (!archer.getInventory().add(spare)) {
				archer.drop(spare, false);
			}
		}
	}

	@Override
	public byte getPierceLevel() {
		// Punta perforante lives on the arrow, not on the bow, so this is where the piercing comes from.
		return (byte) Math.max(super.getPierceLevel(), this.pierce);
	}

	@Override
	public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
		// The fletching is the only thing in the mod that changes how fast an arrow leaves the string.
		super.shoot(x, y, z, velocity * this.speed, inaccuracy);
	}
}
