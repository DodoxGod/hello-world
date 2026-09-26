package dev.forja.mixin;

import dev.forja.difficulty.Scaling;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks a natural spawn, so the difficulty knows which mobs may bring a companion at night; see {@link Scaling}. */
@Mixin(Mob.class)
abstract class MobSpawnMixin {
	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void forja$markNatural(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		SpawnGroupData groupData, CallbackInfoReturnable<SpawnGroupData> cir) {
		if (reason == EntitySpawnReason.NATURAL) {
			((Mob) (Object) this).addTag(Scaling.NATURAL);
		}
		dev.forja.ai.Personality.settle((Mob) (Object) this, reason);
	}
}
