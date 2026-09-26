package dev.forja.registry;

import dev.forja.Forja;
import dev.forja.entity.ThrownHead;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> THROWN_HEAD_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("cabeza_lanzada"));

	public static final EntityType<ThrownHead> THROWN_HEAD = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		THROWN_HEAD_KEY,
		EntityType.Builder.<ThrownHead>of(ThrownHead::new, MobCategory.MISC)
			.noLootTable()
			.noSave()
			.sized(0.5F, 0.5F)
			.clientTrackingRange(6)
			.updateInterval(1)
			.build(THROWN_HEAD_KEY)
	);

	private static final ResourceKey<EntityType<?>> BOLT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("proyectil_magico"));

	/** What the crescent staff throws. Drawn as dust by the server, so the client keeps nothing for it but its place. */
	public static final EntityType<dev.forja.entity.MagicBolt> PROYECTIL_MAGICO = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		BOLT_KEY,
		EntityType.Builder.<dev.forja.entity.MagicBolt>of(dev.forja.entity.MagicBolt::new, MobCategory.MISC)
			.noLootTable()
			.noSave()
			.sized(0.3F, 0.3F)
			.clientTrackingRange(4)
			.updateInterval(10)
			.build(BOLT_KEY)
	);

	private static final ResourceKey<EntityType<?>> ARROW_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("flecha_forjada"));

	public static final EntityType<dev.forja.entity.ForgedArrow> FLECHA_FORJADA = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		ARROW_KEY,
		EntityType.Builder.<dev.forja.entity.ForgedArrow>of(dev.forja.entity.ForgedArrow::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.5F, 0.5F)
			.clientTrackingRange(4)
			.updateInterval(20)
			.build(ARROW_KEY)
	);

	private static final ResourceKey<EntityType<?>> SMITH_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("herrero_caido"));

	public static final EntityType<dev.forja.entity.FallenSmith> HERRERO_CAIDO = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		SMITH_KEY,
		EntityType.Builder.<dev.forja.entity.FallenSmith>of(dev.forja.entity.FallenSmith::new, MobCategory.MONSTER)
			.sized(2.2F, 4.9F)
			.eyeHeight(4.1F)
			.fireImmune()
			.clientTrackingRange(16)
			.build(SMITH_KEY)
	);

	private static final ResourceKey<EntityType<?>> AUTOMATA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("automata_de_forja"));

	public static final EntityType<dev.forja.entity.ForgeAutomaton> AUTOMATA = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		AUTOMATA_KEY,
		EntityType.Builder.<dev.forja.entity.ForgeAutomaton>of(dev.forja.entity.ForgeAutomaton::new, MobCategory.MONSTER)
			.sized(1.3F, 2.4F)
			.eyeHeight(2.0F)
			.fireImmune()
			.clientTrackingRange(10)
			.build(AUTOMATA_KEY)
	);

	private static final ResourceKey<EntityType<?>> CORAZA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("coraza_vacia"));

	public static final EntityType<dev.forja.entity.HollowArmor> CORAZA = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		CORAZA_KEY,
		EntityType.Builder.<dev.forja.entity.HollowArmor>of(dev.forja.entity.HollowArmor::new, MobCategory.MONSTER)
			.sized(0.85F, 2.6F)
			.eyeHeight(2.2F)
			.fireImmune()
			.clientTrackingRange(10)
			.build(CORAZA_KEY)
	);

	private static final ResourceKey<EntityType<?>> PAVESA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("pavesa"));

	public static final EntityType<dev.forja.entity.EmberWisp> PAVESA = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		PAVESA_KEY,
		EntityType.Builder.<dev.forja.entity.EmberWisp>of(dev.forja.entity.EmberWisp::new, MobCategory.MONSTER)
			.sized(0.7F, 0.9F)
			.eyeHeight(0.6F)
			.fireImmune()
			.clientTrackingRange(8)
			.build(PAVESA_KEY)
	);

	private static final ResourceKey<EntityType<?>> HERRUMBRE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("herrumbre"));

	public static final EntityType<dev.forja.entity.RustSwarm> HERRUMBRE = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		HERRUMBRE_KEY,
		EntityType.Builder.<dev.forja.entity.RustSwarm>of(dev.forja.entity.RustSwarm::new, MobCategory.MONSTER)
			// Low and wide, like the thing it is drawn as: a flake, not a beetle standing up.
			.sized(0.8F, 0.35F)
			.eyeHeight(0.25F)
			.clientTrackingRange(8)
			.build(HERRUMBRE_KEY)
	);

	private static final ResourceKey<EntityType<?>> ASCUA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("ascua_mayor"));

	public static final EntityType<dev.forja.entity.GreaterEmber> ASCUA_MAYOR = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		ASCUA_KEY,
		EntityType.Builder.<dev.forja.entity.GreaterEmber>of(dev.forja.entity.GreaterEmber::new, MobCategory.MONSTER)
			.sized(1.3F, 1.5F)
			.eyeHeight(1.0F)
			.fireImmune()
			.clientTrackingRange(10)
			.build(ASCUA_KEY)
	);

	private static final ResourceKey<EntityType<?>> ESCORIA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("escoria_viviente"));

	public static final EntityType<dev.forja.entity.LivingSlag> ESCORIA = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		ESCORIA_KEY,
		EntityType.Builder.<dev.forja.entity.LivingSlag>of(dev.forja.entity.LivingSlag::new, MobCategory.MONSTER)
			.sized(1.0F, 0.95F)
			.eyeHeight(0.7F)
			.fireImmune()
			.clientTrackingRange(8)
			.build(ESCORIA_KEY)
	);

	private static final ResourceKey<EntityType<?>> YUNQUE_ANDANTE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("yunque_andante"));

	public static final EntityType<dev.forja.entity.WalkingAnvil> YUNQUE_ANDANTE = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		YUNQUE_ANDANTE_KEY,
		EntityType.Builder.<dev.forja.entity.WalkingAnvil>of(dev.forja.entity.WalkingAnvil::new, MobCategory.MONSTER)
			.sized(1.5F, 1.1F)
			.eyeHeight(0.9F)
			.fireImmune()
			.clientTrackingRange(10)
			.build(YUNQUE_ANDANTE_KEY)
	);

	private static final ResourceKey<EntityType<?>> PERCUTOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("percutor"));

	public static final EntityType<dev.forja.entity.Striker> PERCUTOR = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		PERCUTOR_KEY,
		EntityType.Builder.<dev.forja.entity.Striker>of(dev.forja.entity.Striker::new, MobCategory.MONSTER)
			.sized(1.1F, 2.5F)
			.eyeHeight(2.2F)
			.clientTrackingRange(12)
			.build(PERCUTOR_KEY)
	);

	private static final ResourceKey<EntityType<?>> TENAZA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("tenaza"));

	public static final EntityType<dev.forja.entity.Tongs> TENAZA = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		TENAZA_KEY,
		EntityType.Builder.<dev.forja.entity.Tongs>of(dev.forja.entity.Tongs::new, MobCategory.MONSTER)
			.sized(0.9F, 2.0F)
			.eyeHeight(1.7F)
			.clientTrackingRange(12)
			.build(TENAZA_KEY)
	);

	private static final ResourceKey<EntityType<?>> CARGADOR_DE_CARBON_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("cargador_de_carbon"));

	public static final EntityType<dev.forja.entity.CoalHauler> CARGADOR_DE_CARBON = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		CARGADOR_DE_CARBON_KEY,
		EntityType.Builder.<dev.forja.entity.CoalHauler>of(dev.forja.entity.CoalHauler::new, MobCategory.MONSTER)
			.sized(1.9F, 1.5F)
			.eyeHeight(1.3F)
			.fireImmune()
			.clientTrackingRange(12)
			.build(CARGADOR_DE_CARBON_KEY)
	);

	private static final ResourceKey<EntityType<?>> TEMPLADOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("templador"));

	public static final EntityType<dev.forja.entity.Quencher> TEMPLADOR = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		TEMPLADOR_KEY,
		EntityType.Builder.<dev.forja.entity.Quencher>of(dev.forja.entity.Quencher::new, MobCategory.MONSTER)
			.sized(0.8F, 1.95F)
			.eyeHeight(1.75F)
			.clientTrackingRange(12)
			.build(TEMPLADOR_KEY)
	);

	private static final ResourceKey<EntityType<?>> NUCLEO_ESTELAR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("nucleo_estelar"));

	public static final EntityType<dev.forja.entity.StarCore> NUCLEO_ESTELAR = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		NUCLEO_ESTELAR_KEY,
		EntityType.Builder.<dev.forja.entity.StarCore>of(dev.forja.entity.StarCore::new, MobCategory.MONSTER)
			.sized(1.4F, 1.8F)
			.eyeHeight(1.1F)
			.fireImmune()
			.clientTrackingRange(14)
			.build(NUCLEO_ESTELAR_KEY)
	);

	private static final ResourceKey<EntityType<?>> MOLDE_ROTO_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("molde_roto"));

	public static final EntityType<dev.forja.entity.BrokenMould> MOLDE_ROTO = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		MOLDE_ROTO_KEY,
		EntityType.Builder.<dev.forja.entity.BrokenMould>of(dev.forja.entity.BrokenMould::new, MobCategory.MONSTER)
			.sized(1.2F, 2.6F)
			.eyeHeight(2.3F)
			.fireImmune()
			.clientTrackingRange(12)
			.build(MOLDE_ROTO_KEY)
	);

	private static final ResourceKey<EntityType<?>> GUARDIAN_DE_CUNO_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("guardian_de_cuno"));

	public static final EntityType<dev.forja.entity.CuneGuardian> GUARDIAN_DE_CUNO = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		GUARDIAN_DE_CUNO_KEY,
		EntityType.Builder.<dev.forja.entity.CuneGuardian>of(dev.forja.entity.CuneGuardian::new, MobCategory.MONSTER)
			.sized(1.8F, 3.2F)
			.eyeHeight(2.6F)
			.fireImmune()
			.clientTrackingRange(16)
			.build(GUARDIAN_DE_CUNO_KEY)
	);

	private static final ResourceKey<EntityType<?>> ONDA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Forja.id("onda_expansiva"));

	/** Not a mob: the ring an area attack draws on the floor, and the warning before it. */
	public static final EntityType<dev.forja.entity.Shockwave> ONDA = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		ONDA_KEY,
		EntityType.Builder.<dev.forja.entity.Shockwave>of(dev.forja.entity.Shockwave::new, MobCategory.MISC)
			.noLootTable()
			.noSave()
			.fireImmune()
			.sized(0.5F, 0.5F)
			.clientTrackingRange(10)
			// Every tick: it follows whoever is winding up, and the blow has to reach the client on
			// the tick it lands rather than up to a second later.
			.updateInterval(1)
			.build(ONDA_KEY)
	);

	private ModEntities() {
	}

	public static void init() {
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(HERRERO_CAIDO, dev.forja.entity.FallenSmith.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(AUTOMATA, dev.forja.entity.ForgeAutomaton.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(CORAZA, dev.forja.entity.HollowArmor.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(PAVESA, dev.forja.entity.EmberWisp.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(HERRUMBRE, dev.forja.entity.RustSwarm.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(ASCUA_MAYOR, dev.forja.entity.GreaterEmber.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(ESCORIA, dev.forja.entity.LivingSlag.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(YUNQUE_ANDANTE, dev.forja.entity.WalkingAnvil.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(PERCUTOR, dev.forja.entity.Striker.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(TENAZA, dev.forja.entity.Tongs.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(CARGADOR_DE_CARBON, dev.forja.entity.CoalHauler.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(TEMPLADOR, dev.forja.entity.Quencher.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(NUCLEO_ESTELAR, dev.forja.entity.StarCore.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(MOLDE_ROTO, dev.forja.entity.BrokenMould.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(GUARDIAN_DE_CUNO, dev.forja.entity.CuneGuardian.attributes());
	}
}
