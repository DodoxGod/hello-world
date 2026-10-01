package dev.forja.test;

import dev.forja.ai.ForjaFamily;
import dev.forja.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "Vi que no hay huevos para spawnear a los mobs" (Andy, 2026-09-28): every monster of the mod has a spawn
 * egg, and each egg, used on the ground the way a right click uses it, puts that monster there.
 */
public class HuevosGameTests {
	/** All the eggs, in the order they sit in the creative tab. */
	static List<Item> eggs() {
		return List.of(ModItems.HUEVO_HERRERO_CAIDO, ModItems.HUEVO_AUTOMATA, ModItems.HUEVO_CORAZA, ModItems.HUEVO_PAVESA,
			ModItems.HUEVO_HERRUMBRE, ModItems.HUEVO_ASCUA_MAYOR, ModItems.HUEVO_ESCORIA, ModItems.HUEVO_YUNQUE_ANDANTE,
			ModItems.HUEVO_PERCUTOR, ModItems.HUEVO_TENAZA, ModItems.HUEVO_CARGADOR, ModItems.HUEVO_TEMPLADOR,
			ModItems.HUEVO_NUCLEO, ModItems.HUEVO_MOLDE_ROTO, ModItems.HUEVO_GUARDIAN);
	}

	/** One egg per monster, none left over, and the game finds it from the monster (creative pick-block). */
	@GameTest(maxTicks = 20)
	public void everyMonsterHasItsEgg(GameTestHelper helper) {
		List<Item> eggs = eggs();
		helper.assertTrue(eggs.size() == ForjaFamily.TYPES.size(), "un huevo por mob: " + eggs.size() + " / " + ForjaFamily.TYPES.size());
		for (EntityType<?> type : ForjaFamily.TYPES) {
			String name = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
			long matching = eggs.stream().filter(egg -> SpawnEggItem.spawnsEntity(new ItemStack(egg), type)).count();
			helper.assertTrue(matching == 1, "huevos de " + name + ": " + matching);
			helper.assertTrue(SpawnEggItem.byId(type).map(holder -> holder.value() instanceof SpawnEggItem).orElse(false),
				"el juego encuentra el huevo de " + name);
			Item egg = SpawnEggItem.byId(type).get().value();
			helper.assertTrue(BuiltInRegistries.ITEM.getKey(egg).getPath().equals("huevo_" + name),
				"el huevo de " + name + " se llama " + BuiltInRegistries.ITEM.getKey(egg));
		}
		helper.succeed();
	}

	/** Each egg, clicked on the floor by a player, spawns its monster on the block above; the stack in survival shrinks. */
	@GameTest(maxTicks = 40)
	public void eachEggSpawnsItsMonster(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		BlockPos floor = helper.absolutePos(new BlockPos(2, 0, 2));
		AABB around = new AABB(floor).inflate(4.0, 6.0, 4.0);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() - 2.5, 0.0F, 30.0F);
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		for (Item egg : eggs()) {
			ItemStack stack = new ItemStack(egg, 2);
			EntityType<?> type = SpawnEggItem.getType(stack);
			String name = BuiltInRegistries.ITEM.getKey(egg).getPath();
			helper.assertTrue(type != null, name + " no lleva mob");
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0), Direction.UP, floor, false)));
			helper.assertTrue(result.consumesAction(), name + " se usa: " + result);
			List<? extends Entity> spawned = helper.getLevel().getEntities(type, around, Entity::isAlive);
			helper.assertTrue(spawned.size() == 1, name + " saca un " + BuiltInRegistries.ENTITY_TYPE.getKey(type) + ": " + spawned.size());
			Entity mob = spawned.get(0);
			helper.assertTrue(mob.blockPosition().getY() == floor.getY() + 1, name + ": encima del suelo, en " + mob.blockPosition());
			helper.assertTrue(stack.getCount() == 1, name + ": gasta uno en supervivencia (" + stack.getCount() + ")");
			// Out of the way before the next one: a boss or a guardian left standing would start its fight.
			mob.discard();
		}
		helper.succeed();
	}
}
