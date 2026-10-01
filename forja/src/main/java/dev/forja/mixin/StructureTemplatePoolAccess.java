package dev.forja.mixin;

import java.util.List;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Forja add its village smithy to the vanilla village house pools; see world/ForjaVillages. */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccess {
	@Accessor("rawTemplates")
	List<Pair<StructurePoolElement, Integer>> forjaRawTemplates();

	@Mutable
	@Accessor("rawTemplates")
	void forjaSetRawTemplates(List<Pair<StructurePoolElement, Integer>> templates);

	@Accessor("templates")
	ObjectArrayList<StructurePoolElement> forjaTemplates();
}
