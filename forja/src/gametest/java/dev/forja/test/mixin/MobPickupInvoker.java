package dev.forja.test.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's loot pickup of a mob, called straight (no game rule, no timing): who takes up a bow (RedV4ModGameTests). */
@Mixin(Mob.class)
public interface MobPickupInvoker {
	@Invoker("pickUpItem")
	void forja$pickUpItem(ServerLevel level, ItemEntity entity);
}
