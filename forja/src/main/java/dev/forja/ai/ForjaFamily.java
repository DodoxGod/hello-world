package dev.forja.ai;

import java.util.List;

import dev.forja.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * The families the mod's own monsters are trained in (red_&lt;file&gt;.json), grouped by how they fight so each
 * network gets enough of them to learn from; the exact type goes in as a one-hot (see {@link #TYPES}).
 * The fallen smith, a boss with phases, has a network of his own.
 */
public enum ForjaFamily {
	CUERPO("forja_cuerpo"),
	DISTANCIA("forja_distancia"),
	AREA("forja_area"),
	TANQUE("forja_tanque"),
	ENJAMBRE("forja_enjambre"),
	JEFE("forja_jefe");

	public final String file;

	ForjaFamily(String file) {
		this.file = file;
	}

	/** The mod's monsters in the order of the one-hot "tipo_forja_*". */
	public static final List<EntityType<?>> TYPES = List.of(
		ModEntities.HERRERO_CAIDO, ModEntities.AUTOMATA, ModEntities.CORAZA, ModEntities.PAVESA, ModEntities.HERRUMBRE,
		ModEntities.ASCUA_MAYOR, ModEntities.ESCORIA, ModEntities.YUNQUE_ANDANTE, ModEntities.PERCUTOR, ModEntities.TENAZA,
		ModEntities.CARGADOR_DE_CARBON, ModEntities.TEMPLADOR, ModEntities.NUCLEO_ESTELAR, ModEntities.MOLDE_ROTO,
		ModEntities.GUARDIAN_DE_CUNO
	);

	/** Null for a monster that is not one of the mod's. */
	public static ForjaFamily of(Mob mob) {
		EntityType<?> t = mob.getType();
		if (t == ModEntities.HERRERO_CAIDO) return JEFE;
		if (t == ModEntities.YUNQUE_ANDANTE || t == ModEntities.AUTOMATA || t == ModEntities.GUARDIAN_DE_CUNO) return TANQUE;
		if (t == ModEntities.HERRUMBRE || t == ModEntities.ESCORIA) return ENJAMBRE;
		if (t == ModEntities.CARGADOR_DE_CARBON || t == ModEntities.NUCLEO_ESTELAR || t == ModEntities.ASCUA_MAYOR || t == ModEntities.PAVESA) return AREA;
		if (t == ModEntities.TEMPLADOR) return DISTANCIA;
		if (t == ModEntities.CORAZA || t == ModEntities.PERCUTOR || t == ModEntities.TENAZA || t == ModEntities.MOLDE_ROTO) return CUERPO;
		return null;
	}
}
