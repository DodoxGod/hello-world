package dev.forja.test.mixin;

import java.util.Set;

import dev.forja.test.Perf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every path search on the server, whoever asked for it. */
@Mixin(PathFinder.class)
abstract class PathFinderCountMixin {
	@Inject(method = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;",
		at = @At("HEAD"))
	private void bench$search(PathNavigationRegion region, Mob mob, Set<BlockPos> targets, float maxRange, int accuracy, float searchDepth,
		CallbackInfoReturnable<Path> cir) {
		Perf.count(Perf.C.PATHFINDS);
	}
}
