package dev.forja.mixin;

import dev.forja.world.ForjaMobs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps some spawned vanilla gear for forged gear, after vanilla has picked and enchanted it. */
@Mixin(Mob.class)
abstract class MobMixin {
	@Inject(method = "populateDefaultEquipmentEnchantments", at = @At("TAIL"))
	private void forja$forgeSpawnedGear(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
		ForjaMobs.forgeEquipment((Mob) (Object) this, random, ForjaMobs.CHANCE);
	}
}
