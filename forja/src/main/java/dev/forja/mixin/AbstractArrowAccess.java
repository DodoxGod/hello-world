package dev.forja.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads an arrow's base damage, which 26.2 only lets you set. A mob's arrow gets its bite from the draw
 * with a little luck on it, and a forged bow's bonus has to go on top of that rather than in its place
 * (see ProjectileUtilMixin).
 */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccess {
	@Accessor("baseDamage")
	double forjaBaseDamage();
}
