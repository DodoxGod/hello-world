package dev.forja.mixin;

import dev.forja.ai.GroundItems;
import dev.forja.ai.Reach;
import dev.forja.world.ForjaMobs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps some spawned vanilla gear for forged gear, after vanilla has picked and enchanted it; and gives a
 * monster's melee the reach of the weapon it holds.
 */
@Mixin(Mob.class)
abstract class MobMixin {
	@Shadow
	protected abstract AABB getAttackBoundingBox(double horizontalExpansion);

	/**
	 * Vanilla's melee reach for a mob is its body's, or a spear's attack_range scaled down for monsters; an
	 * entity_interaction_range modifier on the weapon (a flail's chain, a scythe's shaft, Alcance) counts for
	 * players only. With a weapon that adds reach, the mob's reach is Forja's ({@link Reach}: the body's plus
	 * what the weapon adds, the same number the network sees), and a spear's shortest reach still holds.
	 * Anything that adds no reach is left entirely to vanilla.
	 */
	@Inject(method = "isWithinMeleeAttackRange", at = @At("HEAD"), cancellable = true)
	private void forja$weaponReach(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		ItemStack held = ((Mob) (Object) this).getMainHandItem();
		if (Reach.actionExtra(held) <= 0.0) {
			return;
		}
		AABB hitbox = ((LivingEntityAiAccess) target).forja$hitbox();
		double min = Reach.min(held);
		cir.setReturnValue(this.getAttackBoundingBox(Reach.actionOf(held)).intersects(hitbox) && (min <= 0.0 || !this.getAttackBoundingBox(min).intersects(hitbox)));
	}

	/**
	 * Only shooters take up a bow (docs/mod_spec_v4c.md, 3): vanilla's loot pickup (a zombie that can pick up loot)
	 * leaves a bow on the floor for anyone that cannot shoot it (GroundItems.canShoot), as RECOGER does.
	 */
	@Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
	private void forja$onlyShootersTakeBows(ServerLevel level, ItemEntity entity, CallbackInfo ci) {
		ItemStack stack = entity.getItem();
		if (stack.getItem() instanceof BowItem && !GroundItems.canShoot((Mob) (Object) this, stack)) {
			ci.cancel();
		}
	}

	@Inject(method = "populateDefaultEquipmentEnchantments", at = @At("TAIL"))
	private void forja$forgeSpawnedGear(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
		ForjaMobs.forgeEquipment((Mob) (Object) this, random, ForjaMobs.CHANCE);
	}
}
