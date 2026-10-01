package dev.forja.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.serialization.JsonOps;
import dev.forja.clase.ActiveSkill;
import dev.forja.clase.ClassData;
import dev.forja.clase.ClassNetwork;
import dev.forja.clase.ClassProgress;
import dev.forja.clase.ClassSkills;
import dev.forja.clase.ClassTree;
import dev.forja.clase.PlayerClass;
import dev.forja.combat.Posture;
import dev.forja.combat.Stamina;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.magic.Mana;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The three ultimates of every class (docs/ARBOLES.md, "Las habilidades finales"): one at the end of each senda, only
 * one of them learned at a time, the rest of the sendas still there to buy, the candle and the medallion to change it,
 * a save of the old tree keeping its N, and every one of the eighteen doing what it says. The ones that hurt are
 * measured through the real damage pipeline (the class's factors and nodes included), as the balance probes measure
 * the weapons, and their numbers printed for docs/ARBOLES.md.
 */
public class UltimasGameTests {
	/** A sturdy target: a zombie with nothing in its hands, frozen, with health enough that no ultimate kills it. */
	private static Zombie target(GameTestHelper helper, BlockPos at) {
		CombatGameTests.noRandomThreat();
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, at);
		CombatGameTests.emptyHands(zombie);
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(200.0F);
		return zombie;
	}

	/** A player of this class at the top level, rested, with a forged iron sword in hand (its attributes on). */
	private static CombatGameTests.TestPlayer player(GameTestHelper helper, PlayerClass clazz) {
		CombatGameTests.floor(helper);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, clazz);
		ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
		ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.HIERRO),
			helper.getLevel().registryAccess());
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		sword.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			var instance = player.getAttribute(attribute);
			if (instance != null) {
				instance.addOrReplacePermanentModifier(modifier);
			}
		});
		return player;
	}

	/** Swaps the player's ultimate for this one (and its II if asked), the way a medallion and a few clicks would. */
	private static void take(GameTestHelper helper, CombatGameTests.TestPlayer player, int ultimate, boolean upgraded) {
		ClassProgress.resetTalents(player);
		ClassTree.Tree tree = ClassProgress.clazz(player).tree();
		helper.assertTrue(ArbolGameTests.learnTo(player, tree.ultimateNode(ultimate, upgraded).id), "se aprende la final " + ultimate);
		Stamina.set(player, Stamina.max(player));
		Mana.set(player, Mana.max(player));
		player.removeAllEffects();
		player.setAbsorptionAmount(0.0F);
		player.setHealth(player.getMaxHealth());
		player.setYRot(-90.0F);
		player.setYHeadRot(-90.0F);
		player.setXRot(0.0F);
		Vec3 home = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 1, 1)));
		player.teleportTo(home.x, home.y, home.z);
		player.setDeltaMovement(Vec3.ZERO);
		// A test player never ticks: the last trial's hit would still shield it.
		player.invulnerableTime = 0;
	}

	/** One cast to try: which ultimate, I or II, where the targets stand, how long to wait, and what must be true then. */
	private record Trial(int ultimate, boolean upgraded, List<BlockPos> targets, Look look, int ticks, Check check,
		Consumer<CombatGameTests.TestPlayer> before) {
		Trial(int ultimate, boolean upgraded, List<BlockPos> targets, Look look, int ticks, Check check) {
			this(ultimate, upgraded, targets, look, ticks, check, player -> {
			});
		}
	}

	private enum Look {
		/** Straight along +X, level. */
		AHEAD,
		/** At the first target's body. */
		BODY,
		/** At the first target's feet: the ground under it, for the skills that land where you look. */
		FEET
	}

	@FunctionalInterface
	private interface Check {
		void check(CombatGameTests.TestPlayer player, List<Zombie> targets, long castAt);
	}

	/** Runs the trials one after another, then the bounds every ultimate that hurts must keep. */
	private static void run(GameTestHelper helper, PlayerClass clazz, List<Trial> trials) {
		CombatGameTests.TestPlayer player = player(helper, clazz);
		GameTestSequence sequence = helper.startSequence();
		List<Zombie> live = new ArrayList<>();
		long[] castAt = {0L};
		for (Trial trial : trials) {
			sequence = sequence.thenExecute(() -> {
				for (Zombie old : live) {
					old.discard();
				}
				live.clear();
				take(helper, player, trial.ultimate, trial.upgraded);
				for (BlockPos at : trial.targets) {
					live.add(target(helper, at));
				}
				if (trial.look != Look.AHEAD && !live.isEmpty()) {
					Zombie first = live.getFirst();
					player.lookAt(EntityAnchorArgument.Anchor.EYES, trial.look == Look.FEET ? first.position()
						: first.position().add(0.0, first.getBbHeight() * 0.6, 0.0));
				}
				trial.before.accept(player);
				ActiveSkill skill = ClassSkills.skill(player, 3);
				ActiveSkill expected = clazz.ultimates().get(trial.ultimate - 1);
				helper.assertTrue(skill == expected, clazz + ": la N es " + expected + ", es " + skill);
				helper.assertTrue(skill.upgraded(player) == trial.upgraded, skill + ": la II " + (trial.upgraded ? "aprendida" : "no"));
				castAt[0] = helper.getLevel().getGameTime();
				helper.assertTrue(ClassSkills.use(player, 3, true), skill + (trial.upgraded ? " II" : "") + " se lanza");
				helper.assertTrue(ClassProgress.data(player).thirdReady() > castAt[0], skill + ": empieza su espera");
			}).thenIdle(trial.ticks).thenExecute(() -> {
				ActiveSkill skill = clazz.ultimates().get(trial.ultimate - 1);
				// Measured first: a check may finish a target off.
				if (!live.isEmpty() && hurts(skill)) {
					Zombie first = live.getFirst();
					float dealt = first.getMaxHealth() - first.getHealth();
					helper.assertTrue(dealt > 0.0F, skill + ": hace daño");
					int cooldown = skill.cooldownSeconds(player);
					String row = String.format(Locale.ROOT, "%s|%s%s|%.1f|%d|%.2f", clazz.id(), skill.id(), trial.upgraded ? " II" : "", dealt, cooldown,
						dealt / cooldown);
					System.out.println("[forja-ultimas] " + row);
					// The bounds: no ultimate one-shots a 30-health foe, and what it adds over its wait stays a small
					// share of what a weapon does in that time (a forged sword is some 5 a second).
					helper.assertTrue(dealt <= 30.0F, skill + ": un golpe de " + dealt + " es demasiado");
					helper.assertTrue(dealt / cooldown <= 1.0F, skill + ": " + (dealt / cooldown) + " de daño por segundo de espera es demasiado");
				}
				trial.check.check(player, live, castAt[0]);
			});
		}
		sequence.thenExecute(() -> ClassProgress.clear(player)).thenSucceed();
	}

	/** Hurt and poisoned, for the healing ones. */
	private static void hurt(CombatGameTests.TestPlayer player) {
		player.setHealth(6.0F);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200, 0));
	}

	private static boolean hurts(ActiveSkill skill) {
		return switch (skill) {
			case HENDEDURA, TORBELLINO, DANZA_DE_SOMBRAS, EJECUCION, ABANICO_DE_DAGAS, GOLPE_SISMICO, EMBESTIDA_DE_ESCUDO, RELAMPAGO_EN_CADENA,
				PRISION_DE_HIELO, METEORO, SAETA_LETAL, FLECHA_EXPLOSIVA -> true;
			default -> false;
		};
	}

	private static List<Trial> both(int ultimate, List<BlockPos> targets, Look look, int ticks, Check check) {
		return List.of(new Trial(ultimate, false, targets, look, ticks, check), new Trial(ultimate, true, targets, look, ticks, check));
	}

	private static List<Trial> all(List<List<Trial>> parts) {
		List<Trial> out = new ArrayList<>();
		parts.forEach(out::addAll);
		return out;
	}

	private static final BlockPos FRONT = new BlockPos(3, 1, 1);

	private static Check hurtAll(GameTestHelper helper, String what) {
		return (player, targets, at) -> {
			for (Zombie zombie : targets) {
				helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), what + ": le pega a " + zombie.blockPosition());
			}
		};
	}

	private static Check none() {
		return (player, targets, at) -> {
		};
	}

	// ------------------------------------------------------------------ the data

	/** Three ultimates a class, each at the end of its senda with its II, the bridges off the senda, not off the ultimate. */
	@GameTest
	public void threeUltimatesPerClass(GameTestHelper helper) {
		Set<String> ids = new HashSet<>();
		for (PlayerClass clazz : PlayerClass.values()) {
			ClassTree.Tree tree = clazz.tree();
			helper.assertTrue(tree.ultimates.size() == 3 && clazz.ultimates().size() == 3, clazz + ": tres finales");
			for (int k = 1; k <= 3; k++) {
				ClassTree.Node ultimate = tree.ultimateNode(k, false);
				ClassTree.Node upgrade = tree.ultimateNode(k, true);
				String senda = clazz.id() + ".s" + k + ".";
				helper.assertTrue(ultimate != null && upgrade != null, clazz + ": la final " + k + " y su II");
				helper.assertTrue(ultimate.id.equals(senda + "ultima") && upgrade.id.equals(senda + "ultima_ii"), clazz + ": " + ultimate.id);
				helper.assertTrue(ultimate.region.equals("senda_" + k) && ultimate.isUltimate(), ultimate.id + ": en su senda");
				helper.assertTrue(ultimate.links.contains(senda + "3") && ultimate.links.contains(upgrade.id) && ultimate.links.size() == 2,
					ultimate.id + ": cuelga del final de su senda y lleva su II: " + ultimate.links);
				helper.assertTrue(upgrade.links.equals(List.of(ultimate.id)), upgrade.id + ": la II solo cuelga de su final");
				ClassTree.Node bridge = tree.node(senda + "puente_1");
				helper.assertTrue(bridge != null && bridge.links.contains(senda + "3") && !bridge.links.contains(ultimate.id),
					clazz + ": el puente de la senda " + k + " no pasa por la final");
				ActiveSkill skill = clazz.ultimates().get(k - 1);
				helper.assertTrue(skill.key() == 3 && skill.ultimate() == k && skill.owner() == clazz, skill + ": tecla N, final " + k);
				helper.assertTrue(tree.skill(ultimate) == skill.def() && skill.upgradeNode() == upgrade, skill + ": su nodo y su II");
				helper.assertTrue(ids.add(skill.id()), skill + ": repetida");
			}
			// V II and B are no longer on a senda.
			helper.assertTrue(tree.bySlot(ClassTree.Slot.V2).region.equals("rama_a") && tree.bySlot(ClassTree.Slot.B).region.equals("rama_b")
				&& tree.bySlot(ClassTree.Slot.B2).region.equals("rama_b"), clazz + ": V II en la rama A, B y B II en la B");
		}
		helper.assertTrue(ids.size() == 18, "dieciocho finales distintas: " + ids.size());
		helper.succeed();
	}

	/** Learning one ultimate locks the other two and their II; the rest of their sendas stays; the candle and the medallion free it. */
	@GameTest
	public void onlyOneUltimate(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, PlayerClass.GUERRERO);
		ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
		ClassTree.Tree tree = PlayerClass.GUERRERO.tree();
		helper.assertTrue(ClassSkills.skill(player, 3) == null, "sin final, la N está vacía");
		helper.assertTrue(ArbolGameTests.learnTo(player, "guerrero.s1.ultima_ii"), "el Bramido y su II");
		helper.assertTrue(ClassSkills.skill(player, 3) == ActiveSkill.BRAMIDO && ActiveSkill.BRAMIDO.upgraded(player), "N: Bramido II");
		for (int k = 2; k <= 3; k++) {
			helper.assertTrue(ArbolGameTests.learnTo(player, "guerrero.s" + k + ".3"), "la senda " + k + " se sigue aprendiendo");
			helper.assertTrue(ClassProgress.check(player, tree.ultimateNode(k, false).id) == ClassProgress.Refusal.ULTIMATE,
				"la final " + k + " queda cerrada: " + ClassProgress.check(player, tree.ultimateNode(k, false).id));
			helper.assertTrue(ClassProgress.check(player, tree.ultimateNode(k, true).id) == ClassProgress.Refusal.ULTIMATE, "y su II también");
			helper.assertTrue(ArbolGameTests.learnTo(player, "guerrero.s" + k + ".puente_3"), "el puente de la senda " + k + " y lo de detrás, sí");
		}
		helper.assertTrue(!ClassProgress.unlock(player, "guerrero.s2.ultima"), "no se aprende a la fuerza");
		ClassData data = ClassProgress.data(player);
		// The candle: the ultimate and its II go together, as one leaf at the ultimate's price.
		helper.assertTrue(ClassProgress.forgetCost(tree, List.of("guerrero.s1.ultima", "guerrero.s1.ultima_ii")) == 3, "la final con su II cuesta 3 de vela");
		helper.assertTrue(!ClassProgress.canForget(data, List.of("guerrero.s1.ultima"), 4), "sin su II no: la II quedaría suelta");
		player.getInventory().add(new ItemStack(ModItems.VELA_DEL_OLVIDO));
		int points = data.points();
		helper.assertTrue(ClassNetwork.forget(player, List.of("guerrero.s1.ultima", "guerrero.s1.ultima_ii")), "la vela quita la final");
		helper.assertTrue(ClassProgress.data(player).points() == points + 5 && ClassSkills.skill(player, 3) == null, "vuelven sus 5 puntos y la N queda vacía");
		helper.assertTrue(ClassProgress.check(player, "guerrero.s2.ultima") == ClassProgress.Refusal.NONE, "y se puede elegir otra");
		helper.assertTrue(ClassProgress.unlock(player, "guerrero.s2.ultima") && ClassSkills.skill(player, 3) == ActiveSkill.HENDEDURA, "N: Hendedura");
		helper.assertTrue(ClassProgress.check(player, "guerrero.s1.ultima") == ClassProgress.Refusal.ULTIMATE, "ahora la cerrada es el Bramido");
		// The medallion on one's own class empties everything.
		ClassProgress.choose(player, PlayerClass.GUERRERO);
		helper.assertTrue(ClassSkills.skill(player, 3) == null && ClassProgress.data(player).nodes().isEmpty(), "el medallón la quita con todo");
		ClassProgress.clear(player);
		helper.succeed();
	}

	/** A save of the tree with one N (version 2) keeps it as the chosen ultimate, its II too; what moved and is left loose comes back as points. */
	@GameTest
	public void oldSaveKeepsItsUltimate(GameTestHelper helper) {
		com.google.gson.JsonObject old = new com.google.gson.JsonObject();
		old.addProperty("clase", "guerrero");
		old.addProperty("nivel", 30);
		old.addProperty("arbol", 2);
		com.google.gson.JsonArray nodes = new com.google.gson.JsonArray();
		for (String id : List.of("nucleo_6", "s3.1", "s3.2", "s3.3", "s3.4", "s3.lado", "nucleo_2", "s1.1", "s1.2", "s1.3", "nucleo_4", "s2.1", "s2.2",
			"s2.lado")) {
			nodes.add("guerrero." + id);
		}
		old.add("nodos", nodes);
		ClassData loaded = ClassData.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(loaded.version() == 2, "se lee como del árbol de una sola N");
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		player.setAttached(ClassProgress.DATA, loaded);
		helper.assertTrue(ClassProgress.migrate(player), "se migra");
		ClassData now = ClassProgress.data(player);
		helper.assertTrue(now.version() == ClassData.VERSION, "a la versión " + ClassData.VERSION + ": " + now.version());
		helper.assertTrue(now.has("guerrero.s3.ultima") && now.has("guerrero.s3.ultima_ii"), "el Torbellino y su II se quedan: " + now.nodes());
		helper.assertTrue(ClassSkills.skill(player, 3) == ActiveSkill.TORBELLINO && ActiveSkill.TORBELLINO.upgraded(player), "N: Torbellino II");
		// Old s1.3 is s1.2 now; V II and B moved to the branches, whose trunks this save never learned: they go.
		helper.assertTrue(now.has("guerrero.s1.2") && !now.has("guerrero.s1.3"), "los menores de la senda 1 conservan su sitio: " + now.nodes());
		helper.assertTrue(!now.has("guerrero.a.habilidad_v2") && !now.has("guerrero.b.habilidad_b"), "V II y B, sueltos, vuelven como puntos");
		helper.assertTrue(ClassProgress.connected(PlayerClass.GUERRERO.tree(), new HashSet<>(now.nodes())), "todo lo que queda cuelga del origen");
		helper.assertTrue(now.points() == loaded.withVersion(ClassData.VERSION).earned() - now.spent(), "las cuentas cuadran");
		helper.assertTrue(!ClassProgress.migrate(player), "y solo una vez");
		ClassProgress.clear(player);
		helper.succeed();
	}

	// ------------------------------------------------------------------ every ultimate, at work

	@GameTest(maxTicks = 400)
	public void ultimasDelGuerrero(GameTestHelper helper) {
		run(helper, PlayerClass.GUERRERO, all(List.of(
			both(1, List.of(FRONT, new BlockPos(1, 1, 4)), Look.AHEAD, 2, (player, targets, at) -> {
				for (Zombie zombie : targets) {
					helper.assertTrue(zombie.hasEffect(MobEffects.WEAKNESS) && Posture.fill(zombie) > 0.0, "Bramido: Debilidad y postura");
				}
				helper.assertTrue(player.getAbsorptionAmount() >= 4.0F, "Bramido: absorción por cada uno: " + player.getAbsorptionAmount());
			}),
			both(2, List.of(FRONT), Look.AHEAD, 2, hurtAll(helper, "Hendedura")),
			both(3, List.of(FRONT), Look.AHEAD, 15, hurtAll(helper, "Torbellino")))));
	}

	@GameTest(maxTicks = 400)
	public void ultimasDelAsesino(GameTestHelper helper) {
		run(helper, PlayerClass.ASESINO, all(List.of(
			both(1, List.of(FRONT, new BlockPos(5, 1, 5)), Look.AHEAD, 15, (player, targets, at) -> {
				hurtAll(helper, "Danza de sombras").check(player, targets, at);
				helper.assertTrue(player.hasEffect(MobEffects.RESISTANCE), "nada te toca mientras bailas");
			}),
			List.of(new Trial(2, false, List.of(new BlockPos(6, 1, 1)), Look.BODY, 2, (player, targets, at) -> {
				hurtAll(helper, "Ejecución").check(player, targets, at);
				helper.assertTrue(player.distanceTo(targets.getFirst()) < 2.5F, "apareces a su espalda: " + player.distanceTo(targets.getFirst()));
			})),
			List.of(new Trial(2, true, List.of(new BlockPos(6, 1, 1)), Look.BODY, 2, (player, targets, at) -> {
				hurtAll(helper, "Ejecución II").check(player, targets, at);
				// A kill halves the wait: a weak foe, and the same blow again.
				Zombie weak = targets.getFirst();
				weak.setHealth(1.0F);
				ClassSkills.use(player, 3, true);
				long now = helper.getLevel().getGameTime();
				long wait = ClassProgress.data(player).thirdReady() - now;
				long full = ActiveSkill.EJECUCION.cooldownTicks(player);
				helper.assertTrue(!weak.isAlive() && wait <= full / 2 + 1, "Ejecución II: si muere, media espera: " + wait + " de " + full);
			})),
			both(3, List.of(FRONT), Look.AHEAD, 12, hurtAll(helper, "Abanico de dagas")))));
	}

	@GameTest(maxTicks = 400)
	public void ultimasDelTanque(GameTestHelper helper) {
		run(helper, PlayerClass.TANQUE, all(List.of(
			both(1, List.of(FRONT, new BlockPos(1, 1, 4)), Look.AHEAD, 2, (player, targets, at) -> {
				hurtAll(helper, "Golpe sísmico").check(player, targets, at);
				for (Zombie zombie : targets) {
					helper.assertTrue(zombie.hasEffect(MobEffects.SLOWNESS), "Golpe sísmico: Lentitud");
				}
			}),
			both(2, List.of(new BlockPos(2, 1, 2)), Look.AHEAD, 14, (player, targets, at) -> {
				helper.assertTrue(player.hasEffect(MobEffects.RESISTANCE) && player.hasEffect(MobEffects.REGENERATION), "Santuario: Resistencia y Regeneración");
				helper.assertTrue(player.getEffect(MobEffects.RESISTANCE).getAmplifier() == (ActiveSkill.SANTUARIO_DE_ACERO.upgraded(player) ? 1 : 0),
					"Santuario II: Resistencia II para el Tanque");
				Zombie zombie = targets.getFirst();
				helper.assertTrue(zombie.getDeltaMovement().horizontalDistanceSqr() > 0.0 || zombie.distanceTo(player) > 1.6F, "echa fuera al zombi");
			}),
			both(3, List.of(FRONT), Look.AHEAD, 2, hurtAll(helper, "Embestida de escudo")))));
	}

	@GameTest(maxTicks = 400)
	public void ultimasDelMago(GameTestHelper helper) {
		run(helper, PlayerClass.MAGO, all(List.of(
			both(1, List.of(new BlockPos(4, 1, 1), new BlockPos(6, 1, 2), new BlockPos(6, 1, 5)), Look.BODY, 2,
				hurtAll(helper, "Relámpago en cadena")),
			both(2, List.of(new BlockPos(5, 1, 1), new BlockPos(5, 1, 3)), Look.FEET, 2, (player, targets, at) -> {
				hurtAll(helper, "Prisión de hielo").check(player, targets, at);
				for (Zombie zombie : targets) {
					helper.assertTrue(zombie.getEffect(MobEffects.SLOWNESS) != null && zombie.getEffect(MobEffects.SLOWNESS).getAmplifier() >= 6,
						"congelado: Lentitud VII");
					helper.assertTrue(Posture.isStaggered(zombie, helper.getLevel().getGameTime()), "y aturdido: su ataque se corta");
				}
			}),
			both(3, List.of(new BlockPos(5, 1, 1)), Look.FEET, 40, hurtAll(helper, "Meteoro")))));
	}

	@GameTest(maxTicks = 400)
	public void ultimasDelCurandero(GameTestHelper helper) {
		run(helper, PlayerClass.CURANDERO, all(List.of(
			List.of(new Trial(1, false, List.of(new BlockPos(2, 1, 2)), Look.AHEAD, 2, (player, targets, at) -> {
				helper.assertTrue(player.getHealth() > 6.0F && player.hasEffect(MobEffects.REGENERATION), "Oleada de vida: cura y Regeneración II: "
					+ player.getHealth());
				helper.assertTrue(player.hasEffect(MobEffects.POISON), "sin la II, el veneno se queda");
				helper.assertTrue(targets.getFirst().getDeltaMovement().horizontalDistanceSqr() > 0.0, "y empuja al zombi");
			}, UltimasGameTests::hurt)),
			List.of(new Trial(1, true, List.of(), Look.AHEAD, 2, (player, targets, at) -> {
				helper.assertTrue(player.getHealth() > 6.0F && !player.hasEffect(MobEffects.POISON), "Oleada II: cura y quita el veneno");
			}, UltimasGameTests::hurt)),
			both(2, List.of(), Look.AHEAD, 2, (player, targets, at) -> {
				helper.assertTrue(ClassSkills.warded(player), "Segunda vida: protegido");
				player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000.0F);
				float share = ActiveSkill.SEGUNDA_VIDA.numbers(player)[2];
				helper.assertTrue(player.isAlive() && Math.abs(player.getHealth() - player.getMaxHealth() * share) < 0.6F,
					"un golpe mortal le deja con el " + share + ": " + player.getHealth() + " de " + player.getMaxHealth());
				helper.assertTrue(!ClassSkills.warded(player), "y se gasta");
			}),
			both(3, List.of(), Look.AHEAD, 2, (player, targets, at) -> helper.assertTrue(player.getAbsorptionAmount() >= 6.0F && player.hasEffect(MobEffects.RESISTANCE),
				"Escudo de luz: absorción y Resistencia")))));
	}

	@GameTest(maxTicks = 400)
	public void ultimasDelArquero(GameTestHelper helper) {
		run(helper, PlayerClass.ARQUERO, all(List.of(
			both(1, List.of(new BlockPos(4, 1, 1), new BlockPos(6, 1, 1)), Look.BODY, 20, hurtAll(helper, "Saeta letal")),
			both(2, List.of(new BlockPos(5, 1, 1), new BlockPos(5, 1, 3)), Look.FEET, 2, (player, targets, at) -> {
				hurtAll(helper, "Flecha explosiva").check(player, targets, at);
				helper.assertTrue(Posture.fill(targets.getFirst()) > 0.0, "y postura");
			}),
			both(3, List.of(new BlockPos(5, 1, 1)), Look.FEET, 2, (player, targets, at) -> helper.assertTrue(
				targets.getFirst().getEffect(MobEffects.SLOWNESS) != null && targets.getFirst().getEffect(MobEffects.SLOWNESS).getAmplifier() == 3,
				"Flecha de red: Lentitud IV")))));
	}
}
