package com.dodoxgod.filo.mixin;

import com.dodoxgod.filo.ai.LungeGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los zombis (y husks) ganan la embestida, con más prioridad que el ataque normal. */
@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin extends HostileEntity {
	protected ZombieEntityMixin(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	@Inject(method = "initCustomGoals", at = @At("TAIL"))
	private void filo$addLunge(CallbackInfo ci) {
		this.goalSelector.add(1, new LungeGoal(this));
	}
}
