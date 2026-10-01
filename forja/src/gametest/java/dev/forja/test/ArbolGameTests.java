package dev.forja.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.mojang.serialization.JsonOps;
import dev.forja.clase.ActiveSkill;
import dev.forja.clase.ClassData;
import dev.forja.clase.ClassEffects;
import dev.forja.clase.ClassNetwork;
import dev.forja.clase.ClassProgress;
import dev.forja.clase.ClassSkills;
import dev.forja.clase.ClassStat;
import dev.forja.clase.ClassTree;
import dev.forja.clase.Hooks;
import dev.forja.clase.Milestones;
import dev.forja.clase.PlayerClass;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Los árboles grandes de clase (docs/ARBOLES.md): los datos, las reglas, los puntos, los hitos, el reinicio, la migración y las habilidades. */
public class ArbolGameTests {
	/** Learns the cheapest way from what is learned to {@code id}, node by node, as a player clicking would. */
	static boolean learnTo(ServerPlayer player, String id) {
		ClassTree.Tree tree = ClassProgress.clazz(player).tree();
		ClassData data = ClassProgress.data(player);
		if (data.has(id)) {
			return true;
		}
		Map<String, Integer> dist = new HashMap<>();
		Map<String, String> prev = new HashMap<>();
		PriorityQueue<String> open = new PriorityQueue<>((a, b) -> Integer.compare(dist.get(a), dist.get(b)));
		dist.put(tree.origin().id, 0);
		open.add(tree.origin().id);
		for (String owned : data.nodes()) {
			dist.put(owned, 0);
			open.add(owned);
		}
		while (!open.isEmpty()) {
			String at = open.poll();
			for (String link : tree.node(at).links) {
				ClassTree.Node next = tree.node(link);
				if (next.excludes != null && ClassProgress.data(player).has(next.excludes)) {
					continue;
				}
				int d = dist.get(at) + (data.has(link) ? 0 : next.cost);
				if (d < dist.getOrDefault(link, Integer.MAX_VALUE)) {
					dist.put(link, d);
					prev.put(link, at);
					open.add(link);
				}
			}
		}
		if (!prev.containsKey(id)) {
			return false;
		}
		List<String> path = new ArrayList<>();
		for (String at = id; at != null && !data.has(at) && !at.equals(tree.origin().id); at = prev.get(at)) {
			path.add(at);
		}
		Collections.reverse(path);
		for (String step : path) {
			if (!ClassProgress.unlock(player, step)) {
				return false;
			}
		}
		return true;
	}

	private static Map<String, Integer> costsFromOrigin(ClassTree.Tree tree) {
		Map<String, Integer> dist = new HashMap<>();
		PriorityQueue<String> open = new PriorityQueue<>((a, b) -> Integer.compare(dist.get(a), dist.get(b)));
		dist.put(tree.origin().id, 0);
		open.add(tree.origin().id);
		while (!open.isEmpty()) {
			String at = open.poll();
			for (String link : tree.node(at).links) {
				int d = dist.get(at) + tree.node(link).cost;
				if (d < dist.getOrDefault(link, Integer.MAX_VALUE)) {
					dist.put(link, d);
					open.add(link);
				}
			}
		}
		return dist;
	}

	/** Every tree: all of it hangs from the origin, the costs add up, every keystone is eleven points away, the links go both ways. */
	@GameTest
	public void theTreesHoldTogether(GameTestHelper helper) {
		helper.assertTrue(PlayerClass.values().length == 6, "seis clases, sin Herrero: " + PlayerClass.values().length);
		int top = ClassTree.levelPoints(ClassTree.maxLevel()) + ClassTree.allMilestonePoints();
		helper.assertTrue(ClassTree.maxLevel() == 50, "el tope es 50: " + ClassTree.maxLevel());
		helper.assertTrue(top == 103, "en el tope hay 103 puntos: " + top);
		Set<String> textHooks = new HashSet<>();
		Set<String> allHooks = new HashSet<>();
		for (PlayerClass clazz : PlayerClass.values()) {
			ClassTree.Tree tree = clazz.tree();
			helper.assertTrue(tree.nodes.size() == 97, clazz + ": 97 nodos, tiene " + tree.nodes.size());
			helper.assertTrue(tree.totalCost() == 117, clazz + ": el árbol entero cuesta 117, cuesta " + tree.totalCost());
			float share = top / (float) tree.totalCost();
			helper.assertTrue(share > 0.85F && share < 0.9F, clazz + ": en el tope se compra ~87 %: " + share);
			// Connected, and every link both ways.
			Set<String> seen = new HashSet<>();
			Deque<String> open = new ArrayDeque<>(List.of(tree.origin().id));
			while (!open.isEmpty()) {
				String id = open.poll();
				if (!seen.add(id)) {
					continue;
				}
				for (String link : tree.node(id).links) {
					helper.assertTrue(tree.node(link) != null, clazz + ": " + id + " enlaza con " + link + ", que no existe");
					helper.assertTrue(tree.node(link).links.contains(id), clazz + ": " + id + " → " + link + " no vuelve");
					open.add(link);
				}
			}
			helper.assertTrue(seen.size() == tree.nodes.size(), clazz + ": " + (tree.nodes.size() - seen.size()) + " nodos sueltos");
			Map<String, Integer> dist = costsFromOrigin(tree);
			int keystones = 0;
			for (ClassTree.Node node : tree.nodes) {
				if (node.kind == ClassTree.Kind.CLAVE) {
					keystones++;
					helper.assertTrue(dist.get(node.id) == 11, clazz + ": la clave " + node.id + " está a " + dist.get(node.id) + " puntos, no a 11");
					helper.assertTrue(node.excludes != null && tree.node(node.excludes).kind == ClassTree.Kind.CLAVE
						&& node.id.equals(tree.node(node.excludes).excludes), clazz + ": " + node.id + " y su pareja se excluyen");
				}
				if (node.hook != null) {
					allHooks.add(node.hook);
					if (node.hasText()) {
						textHooks.add(node.hook);
					}
				}
			}
			helper.assertTrue(keystones == 6, clazz + ": seis claves, tiene " + keystones);
			for (ClassTree.Slot slot : ClassTree.Slot.values()) {
				helper.assertTrue(tree.bySlot(slot) != null, clazz + ": falta el nodo de habilidad " + slot);
			}
			for (int key = 1; key <= 3; key++) {
				ActiveSkill skill = clazz.skill(key);
				helper.assertTrue(skill.owner() == clazz, clazz + ": la habilidad " + key + " es suya");
				helper.assertTrue(skill.def().upgradeNumbers.length >= skill.def().numbers.length, clazz + ": " + skill + " II tiene todos sus números");
			}
			helper.assertTrue(dist.get(tree.bySlot(ClassTree.Slot.N).id) == 7, clazz + ": la habilidad N está a 7 puntos");
		}
		// The code knows every node that does something a number cannot say, and nothing it knows is missing.
		for (String hook : Hooks.all()) {
			helper.assertTrue(allHooks.contains(hook), "Hooks." + hook + " no es ningún nodo");
		}
		for (String hook : textHooks) {
			helper.assertTrue(Hooks.all().contains(hook), "el nodo " + hook + " tiene efecto pero ningún código lo hace");
		}
		helper.succeed();
	}

	/** Only what touches something learned, only with points, never another class's, and one keystone a branch. */
	@GameTest
	public void theTreeRules(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, PlayerClass.GUERRERO);
		ClassData data = ClassProgress.data(player);
		helper.assertTrue(data.points() == ClassTree.levelPoints(1) && data.points() == 1, "en el nivel 1, un punto: " + data.points());
		helper.assertTrue(ClassProgress.check(player, "guerrero.a.tronco_1") == ClassProgress.Refusal.PREREQUISITE, "el tronco pide su puerta");
		helper.assertTrue(ClassProgress.check(player, "asesino.nucleo_1") == ClassProgress.Refusal.OTHER_CLASS, "nada de otra clase");
		helper.assertTrue(ClassProgress.unlock(player, "guerrero.nucleo_1"), "la puerta de la rama A se aprende");
		helper.assertTrue(ClassProgress.check(player, "guerrero.a.tronco_1") == ClassProgress.Refusal.POINTS, "sin puntos no hay más");
		helper.assertTrue(ClassProgress.check(player, "guerrero.nucleo_1") == ClassProgress.Refusal.ALREADY, "no dos veces");
		ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
		helper.assertTrue(learnTo(player, "guerrero.a1.5"), "Muro de carne, en el nivel 50");
		helper.assertTrue(ClassProgress.check(player, "guerrero.a2.5") != ClassProgress.Refusal.NONE, "la otra clave de la rama no");
		learnTo(player, "guerrero.a2.4");
		helper.assertTrue(ClassProgress.check(player, "guerrero.a2.5") == ClassProgress.Refusal.EXCLUDED,
			"Adrenalina queda excluida por Muro de carne: " + ClassProgress.check(player, "guerrero.a2.5"));
		// A plan from "Probar": learned in order, stopping at the first that cannot be.
		int before = ClassProgress.data(player).nodes().size();
		int learned = ClassProgress.unlockAll(player, List.of("guerrero.nucleo_3", "guerrero.b.tronco_1", "guerrero.c.tronco_4", "guerrero.b.tronco_2"));
		helper.assertTrue(learned == 2 && ClassProgress.data(player).nodes().size() == before + 2, "el plan se para en lo que no toca: " + learned);
		ClassProgress.clear(player);
		helper.succeed();
	}

	/** Andy (2026-09-30): the cap is 50, points come one or two a level, and milestones only as the level allows. */
	@GameTest
	public void milestonePointsWaitForLevels(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, PlayerClass.MAGO);
		for (ClassTree.Milestone milestone : ClassTree.milestones()) {
			ClassProgress.reach(player, milestone.id());
		}
		ClassData data = ClassProgress.data(player);
		helper.assertTrue(data.milestonePoints() == ClassTree.allMilestonePoints(), "todos los hitos: " + data.milestonePoints());
		helper.assertTrue(data.usableMilestonePoints() == ClassTree.milestoneCapPerLevel(), "en el nivel 1 solo se gasta " + data.usableMilestonePoints());
		helper.assertTrue(data.earned() == 2, "nivel 1: 1 de nivel y 1 de hitos, " + data.earned());
		ClassProgress.setLevel(player, 10);
		helper.assertTrue(ClassProgress.data(player).earned() == 14 + 10, "nivel 10: 14 y 10, " + ClassProgress.data(player).earned());
		ClassProgress.setLevel(player, 50);
		helper.assertTrue(ClassProgress.data(player).earned() == 103, "nivel 50: 103, " + ClassProgress.data(player).earned());
		// The points of a level, one or two at a time, and a curve that gets there.
		int[] expected = {0, 1, 2, 4, 5, 7};
		for (int level = 1; level <= 5; level++) {
			helper.assertTrue(ClassTree.levelPoints(level) == expected[level], "nivel " + level + ": " + ClassTree.levelPoints(level));
		}
		helper.assertTrue(ClassProgress.step(1) == 30 && ClassProgress.totalFor(50) == 7350, "la curva: " + ClassProgress.totalFor(50));
		// A change of class: level 1 again, but the milestones are the player's.
		ClassProgress.choose(player, PlayerClass.TANQUE);
		helper.assertTrue(ClassProgress.data(player).level() == 1, "cambiar vuelve al nivel 1");
		helper.assertTrue(ClassProgress.data(player).milestonePoints() == ClassTree.allMilestonePoints(), "y los hitos se quedan");
		helper.assertTrue(!ClassProgress.reach(player, "dragon"), "un hito no se cuenta dos veces");
		ClassProgress.clear(player);
		helper.assertTrue(ClassProgress.data(player).milestones().size() == ClassTree.milestones().size(), "ni sin clase se pierden");
		helper.succeed();
	}

	/** Milestones are noticed afterwards too: a kill the statistics remember, an elite as it dies. */
	@GameTest
	public void milestonesAreNoticed(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		// A FakePlayer ignores awardStat: the counter is set straight on its statistics.
		player.getStats().setValue(player, Stats.ENTITY_KILLED.get(EntityTypes.WARDEN), 1);
		Milestones.check(player);
		helper.assertTrue(ClassProgress.data(player).hasMilestone("warden"), "el Warden, por la estadística");
		var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
		dev.forja.difficulty.Threat.ELITE.mark(zombie);
		Milestones.onKill(player, zombie);
		helper.assertTrue(ClassProgress.data(player).hasMilestone("primer_elite"), "el primer élite");
		helper.succeed();
	}

	/** The numbers reach the systems: an attribute, a hook, the forge. */
	@GameTest
	public void nodesDoWhatTheySay(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, PlayerClass.GUERRERO);
		ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
		double health = player.getAttributeValue(Attributes.MAX_HEALTH);
		helper.assertTrue(learnTo(player, "guerrero.nucleo_4"), "la puerta de vida");
		helper.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) > health, "+3 % de vida: " + player.getAttributeValue(Attributes.MAX_HEALTH));
		helper.assertTrue(!ClassEffects.has(player, Hooks.INQUEBRANTABLE), "sin Inquebrantable todavía");
		helper.assertTrue(learnTo(player, "guerrero.a1.3"), "Inquebrantable");
		helper.assertTrue(ClassEffects.has(player, Hooks.INQUEBRANTABLE), "Inquebrantable aprendido");
		var source = helper.getLevel().damageSources().generic();
		float healthy = ClassEffects.taken(player, source, 4.0F);
		player.setHealth(player.getMaxHealth() * 0.2F);
		float hurt = ClassEffects.taken(player, source, 4.0F);
		helper.assertTrue(hurt < healthy - 0.15F, "por debajo del 30 %, un 20 % menos: " + healthy + " → " + hurt);
		// The forge, the same in every tree.
		int potential = ClassEffects.potentialBonus(player);
		helper.assertTrue(learnTo(player, "forja.alma_del_metal"), "Alma del metal");
		helper.assertTrue(ClassEffects.potentialBonus(player) == potential + 10, "Metal dócil y Alma del metal: +10 de potencial");
		helper.assertTrue(learnTo(player, "forja.carga_honda") && ClassEffects.capacityBonus(player) == 2, "Carga honda: +2 de carga");
		ItemStack sword = dev.forja.forge.Assembler.create(dev.forja.forge.ForgeType.ESPADA,
			List.of(dev.forja.material.ForgeMaterial.HIERRO, dev.forja.material.ForgeMaterial.MADERA, dev.forja.material.ForgeMaterial.HIERRO),
			helper.getLevel().registryAccess());
		int capacity = dev.forja.forge.Potential.capacity(sword);
		sword.set(dev.forja.registry.ModComponents.CARGA_EXTRA, ClassEffects.capacityBonus(player));
		helper.assertTrue(dev.forja.forge.Potential.capacity(sword) == capacity + 2, "la pieza guarda la carga de más");
		// Temple de campaña: forged gear mends a little on its own.
		helper.assertTrue(learnTo(player, "forja.temple_de_campana"), "Temple de campaña");
		sword.setDamageValue(sword.getMaxDamage() / 2);
		int worn = sword.getDamageValue();
		dev.forja.clase.ClassEvents.temper(sword, ClassEffects.hook(player, Hooks.TEMPLE_DE_CAMPANA)[0]);
		helper.assertTrue(sword.getDamageValue() < worn, "se repara sola: " + worn + " → " + sword.getDamageValue());
		// The damage factors are the class's, whatever the tree: a Guerrero's magic stays at ×0.4.
		helper.assertTrue(dev.forja.clase.ClassDamage.factor(player, dev.forja.clase.ClassDamage.Blow.MAGIC) == dev.forja.clase.ClassDamage.GUERRERO_MAGIC,
			"el factor de magia no cambia");
		ClassProgress.clear(player);
		helper.succeed();
	}

	/** The Medallón empties the tree and keeps the level; the Vela takes off a few points at the edge, and is spent. */
	@GameTest
	public void theMedallionAndTheCandle(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, PlayerClass.ARQUERO);
		ClassProgress.setLevel(player, 20);
		learnTo(player, "arquero.a.tronco_4");
		ClassData data = ClassProgress.data(player);
		helper.assertTrue(!ClassProgress.canForget(data, List.of("arquero.a.tronco_2"), 4), "lo de en medio no se olvida: dejaría el resto suelto");
		helper.assertTrue(ClassProgress.canForget(data, List.of("arquero.a.tronco_4", "arquero.a.tronco_3"), 4), "el borde sí");
		helper.assertTrue(!ClassProgress.canForget(data, List.of("arquero.a.tronco_4", "arquero.a.tronco_3", "arquero.a.tronco_2", "arquero.a.tronco_1",
			"arquero.nucleo_1"), 4), "más de 4 puntos no");
		int points = data.points();
		player.getInventory().add(new ItemStack(ModItems.VELA_DEL_OLVIDO, 2));
		helper.assertTrue(ClassNetwork.forget(player, List.of("arquero.a.tronco_4", "arquero.a.tronco_3")), "la vela los quita");
		helper.assertTrue(ClassProgress.data(player).points() == points + 2, "y vuelven sus puntos");
		
		// A forge node at the edge goes the same way.
		helper.assertTrue(learnTo(player, "forja.alma_del_metal"), "Alma del metal");
		helper.assertTrue(ClassProgress.canForget(ClassProgress.data(player), List.of("forja.alma_del_metal"), 4), "un nodo de forja del borde se puede olvidar");
		helper.assertTrue(ClassNetwork.forget(player, List.of("forja.alma_del_metal")), "la vela quita un nodo de forja");
		// The medallion on your own class: the tree empties, the level stays.
		ClassProgress.choose(player, PlayerClass.ARQUERO);
		helper.assertTrue(ClassProgress.data(player).nodes().isEmpty() && ClassProgress.data(player).level() == 20, "el medallón vacía el árbol y deja el nivel");
		ClassProgress.clear(player);
		helper.succeed();
	}

	/** A save from the small trees still loads: its talents go (their points come back) and a Herrero has no class. */
	@GameTest
	public void oldSavesAreMigrated(GameTestHelper helper) {
		com.google.gson.JsonObject old = new com.google.gson.JsonObject();
		old.addProperty("clase", "guerrero");
		old.addProperty("nivel", 7);
		old.addProperty("xp", 900);
		old.addProperty("talentos", 0b1011);
		ClassData loaded = ClassData.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(loaded.version() == 1 && loaded.legacyMask() == 0b1011, "se lee como guardado viejo");
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		player.setAttached(ClassProgress.DATA, loaded);
		helper.assertTrue(ClassProgress.migrate(player), "se migra");
		ClassData now = ClassProgress.data(player);
		helper.assertTrue(now.version() == ClassData.VERSION && now.nodes().isEmpty() && now.level() == 7 && now.playerClass() == PlayerClass.GUERRERO,
			"guerrero de nivel 7, árbol vacío: " + now);
		helper.assertTrue(!ClassProgress.migrate(player), "y solo una vez");
		com.google.gson.JsonObject smith = new com.google.gson.JsonObject();
		smith.addProperty("clase", "herrero");
		smith.addProperty("nivel", 9);
		player.setAttached(ClassProgress.DATA, ClassData.CODEC.parse(JsonOps.INSTANCE, smith).getOrThrow());
		ClassProgress.migrate(player);
		helper.assertTrue(ClassProgress.clazz(player) == null, "el Herrero ya no es clase");
		// And what is saved now comes back the same.
		ClassData sample = now.plus("guerrero.nucleo_1").withMilestone("nether");
		ClassData back = ClassData.CODEC.parse(JsonOps.INSTANCE, ClassData.CODEC.encodeStart(JsonOps.INSTANCE, sample).getOrThrow()).getOrThrow();
		helper.assertTrue(back.equals(sample), "guardar y leer: " + back);
		ClassProgress.clear(player);
		helper.succeed();
	}

	/** The three keys: V always, B and N once learned, the II's numbers once upgraded, and the new skills doing their thing. */
	@GameTest
	public void threeSkillsAndTheirII(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, PlayerClass.GUERRERO);
		ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
		helper.assertTrue(ClassSkills.skill(player, 1) == ActiveSkill.GRITO_DE_GUERRA, "V: Grito de guerra");
		helper.assertTrue(ClassSkills.skill(player, 2) == null && ClassSkills.skill(player, 3) == null, "B y N, aún no");
		learnTo(player, ClassTree.tree(PlayerClass.GUERRERO).bySlot(ClassTree.Slot.B).id);
		helper.assertTrue(ClassSkills.skill(player, 2) == ActiveSkill.POSTURA_DE_HIERRO, "B: Postura de hierro");
		helper.assertTrue(ClassSkills.use(player, 2, true), "se usa");
		helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "con Lentitud, sin la II");
		player.removeAllEffects();
		learnTo(player, ClassTree.tree(PlayerClass.GUERRERO).bySlot(ClassTree.Slot.B2).id);
		helper.assertTrue(ActiveSkill.POSTURA_DE_HIERRO.upgraded(player) && ActiveSkill.POSTURA_DE_HIERRO.numbers(player)[0] == 8.0F, "la II: 8 s");
		ClassSkills.use(player, 2, true);
		helper.assertTrue(!player.hasEffect(MobEffects.SLOWNESS) && player.getEffect(MobEffects.RESISTANCE).getDuration() > 150, "la II, sin Lentitud y más larga");
		// The Torbellino: everything around takes part of the weapon.
		learnTo(player, ClassTree.tree(PlayerClass.GUERRERO).bySlot(ClassTree.Slot.N).id);
		helper.assertTrue(ClassSkills.skill(player, 3) == ActiveSkill.TORBELLINO, "N: Torbellino");
		CombatGameTests.noRandomThreat();
		var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
		zombie.setNoAi(true);
		float before = zombie.getHealth();
		dev.forja.combat.Stamina.set(player, 100.0F);
		helper.assertTrue(ClassSkills.use(player, 3, true), "el torbellino gira");
		helper.assertTrue(zombie.getHealth() < before, "y le pega al zombi: " + before + " → " + zombie.getHealth());
		helper.assertTrue(ClassProgress.data(player).thirdReady() > helper.getLevel().getGameTime(), "y empieza su espera");
		ClassProgress.clear(player);
		helper.succeed();
	}

	/** The Mago's Meteoro costs its mana and lands a moment later. */
	@GameTest(maxTicks = 100)
	public void theMeteorLands(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, PlayerClass.MAGO);
		ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
		learnTo(player, ClassTree.tree(PlayerClass.MAGO).bySlot(ClassTree.Slot.N).id);
		CombatGameTests.noRandomThreat();
		var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 1));
		zombie.setNoAi(true);
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, zombie.position());
		dev.forja.magic.Mana.set(player, dev.forja.magic.Mana.max(player));
		float mana = dev.forja.magic.Mana.value(player);
		float health = zombie.getHealth();
		helper.assertTrue(ClassSkills.use(player, 3, true), "el meteoro sale");
		helper.assertTrue(dev.forja.magic.Mana.value(player) < mana - 30.0F, "y cuesta maná: " + mana + " → " + dev.forja.magic.Mana.value(player));
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(zombie.getHealth() < health, "el meteoro le cae encima: " + health + " → " + zombie.getHealth());
			ClassProgress.clear(player);
			helper.succeed();
		});
	}

	/** GearScore counts the points spent on the tree, against all a player can have at the top. */
	@GameTest
	public void spentPointsCountForTheMonsters(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, PlayerClass.TANQUE);
		double bare = dev.forja.difficulty.GearScore.of(player);
		ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
		double unspent = dev.forja.difficulty.GearScore.of(player);
		helper.assertTrue(Math.abs(unspent - bare) < 1.0E-6, "el nivel solo no cuenta: " + bare + " / " + unspent);
		learnTo(player, "tanque.b2.5");
		double spent = dev.forja.difficulty.GearScore.of(player);
		helper.assertTrue(spent > unspent, "los puntos gastados sí: " + unspent + " → " + spent);
		ClassProgress.clear(player);
		helper.succeed();
	}

	/** The stat a small node gives: the class's base plus three per cent. */
	@GameTest
	public void aSmallNodeIsThreePerCent(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, PlayerClass.ASESINO);
		float before = ClassEffects.stat(player, ClassStat.DODGE_COST);
		ClassProgress.unlock(player, "asesino.nucleo_1");
		helper.assertTrue(Math.abs(ClassEffects.stat(player, ClassStat.DODGE_COST) - (before - 0.03F)) < 1.0E-5, "coste de esquiva −3 %");
		ClassProgress.clear(player);
		helper.succeed();
	}
}
