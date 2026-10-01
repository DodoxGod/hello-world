"""What is in the cellars: the rooms castillo_sotanos dug and left bare.

The audit found seven of them with nothing in them at all - the dungeons, their guard room, the larder, the ingot
store, the ossuary, the antechamber of the nine and the soul forge - and the crypt and the coal store with a
handful of things across floors the size of the great hall. The vault was worse than empty: it had no way in.
The plan always said "cell 7: a lever to level -2"; it gets the honest version of that, a shaft under the straw
of the seventh cell with a ladder down into the vault's ceiling.

Names and roles are the plan's (docs/castillo/plano_v3_completo.png, basements): the crypt of the Nine Masters
with its nine tombs, the ossuary, the dungeon with forgers held in it, the larder, the ingot store, the coal store
the haulers work, the antechamber where nine apprentices stand in the dark, the soul forge, and the vault.

Furniture only - light is castillo_luz's business, which comes after and lights what it finds.
"""
from castillo import HORIZONTAL, _hash, slab, stairs
from castillo_interiores import FIRE, banner, barrel, chest, mob, rug
from castillo_salas import CANDLE, stand
from castillo_sotanos import L1, L2

SOUL_FIRE = {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"}


def free(w, x, y, z):
    return w.name(x, y, z) in (None, "minecraft:air")


def put_if_free(w, x, y, z, block, props=None, nbt=None):
    if free(w, x, y, z):
        w.put(x, y, z, block, props, nbt)


# ------------------------------------------------------------------------------------------ level -1

def crypt(w):
    """The crypt of the Nine Masters: nine tombs in three rows, set in the bays between the vault's pillars so
    none of them stands on one, each with its effigy's head and its candles; the chest of the order on an altar
    against the north wall, which is the way they all lie."""
    y = L1
    for tx in (82, 100, 118):
        for tz in (27, 45, 63):
            for dx in range(-1, 2):
                for dz in range(-2, 3):
                    w.put(tx + dx, y, tz + dz, "polished_blackstone_bricks")
                    w.put(tx + dx, y + 1, tz + dz, *slab("polished_blackstone_slab"))
            w.put(tx, y + 1, tz - 2, "chiseled_polished_blackstone")
            w.put(tx, y + 2, tz - 2, "skeleton_skull", {"rotation": "8"})
            # the candles at the foot, set into the lid on the stone: a candle cannot stand on a half slab
            for dx in (-1, 1):
                if _hash(tx + dx, y, tz, 201) < 0.7:
                    w.put(tx + dx, y + 1, tz + 2, "candle", CANDLE)
            put_if_free(w, tx, y, tz + 3, "polished_blackstone_brick_wall")
            if _hash(tx, y, tz, 202) < 0.5:
                put_if_free(w, tx - 2, y, tz, "cobweb")
    # the altar: two steps up against the north wall, the order's chest, soul fire at its front corners
    for r, block in ((2, "polished_blackstone_bricks"), (1, "chiseled_polished_blackstone")):
        for dx in range(-r, r + 1):
            for dz in range(0, r + 1):
                w.put(100 + dx, y + (2 - r), 18 + dz, block)
    chest(w, 100, y + 2, 18, "south", "forja:chests/bastion_cripta")
    for dx in (-2, 2):
        w.put(100 + dx, y + 1, 21, "polished_blackstone_brick_wall")
        w.put(100 + dx, y + 2, 21, "soul_campfire", SOUL_FIRE)
    for x in (72, 129):
        for z in range(22, 68, 9):
            banner(w, x, y + 4, z, "east" if x == 72 else "west", colour="black")
    # Two of the nine are not quite asleep (the plan: "corazas vacias al abrir el cofre").
    for x, z in ((91, 45), (109, 45)):
        mob(w, x, y, z, "forja:coraza_vacia")


def ossuary(w):
    """Bones stacked to the vault in the niches, skulls on the shelves, a path down the middle."""
    y = L1
    for x in range(146, 165):
        for z in (20, 40):
            for dy in range(0, 4):
                if _hash(x, y + dy, z, 210) < 0.75:
                    put_if_free(w, x, y + dy, z, "bone_block", {"axis": "y" if dy % 2 else "x"})
            if x % 3 == 0 and free(w, x, y + 4, z):
                w.put(x, y + 4, z, "skeleton_skull", {"rotation": "0" if z == 40 else "8"})
    for z in range(22, 39, 4):
        for x in (147, 163):
            put_if_free(w, x, y, z, "bone_block", {"axis": "y"})
            put_if_free(w, x, y + 1, z, "skeleton_skull", {"rotation": "4" if x == 147 else "12"})
    for x in range(149, 162):
        for z in range(22, 39):
            if _hash(x, y, z, 211) < 0.12:
                put_if_free(w, x, y, z, "cobweb")
            elif _hash(x, y, z, 212) < 0.08:
                w.put(x, y - 1, z, "soul_soil")
    chest(w, 155, y, 38, "north", "forja:chests/bastion_cripta")


def dungeon(w):
    """Seven cells down each side of a middle passage, bars on their fronts, straw on their floors; three hold forgers
    who were taken for what they knew. The seventh on the west side has a shaft under its straw to the vault."""
    y = L1
    for side, (x_front, x_back) in (("west", (56, 48)), ("east", (62, 70))):
        step = 1 if side == "east" else -1
        for cell in range(7):
            z0 = 71 + cell * 6
            # the walls between cells, and the bars on the front with a gap for a door on some
            for dz in (0, 6):
                for x in range(min(x_front, x_back), max(x_front, x_back) + 1):
                    for dy in range(0, 6):
                        put_if_free(w, x, y + dy, z0 + dz, "deepslate_bricks" if _hash(x, y + dy, z0 + dz, 220) < 0.85 else "cracked_deepslate_bricks")
            broken = _hash(cell, 0, 0 if side == "west" else 1, 221) < 0.3
            for z in range(z0 + 1, z0 + 6):
                for dy in range(0, 3):
                    door = z in (z0 + 2, z0 + 3) and dy < 2 and broken
                    if not door:
                        w.put(x_front, y + dy, z, "iron_bars")
                # Over the bars, wall to the vault: bars three high under a ceiling six up read as a row of low
                # railings, not as cells (Andy, 2026-09-29: "a corridor of portcullises").
                for dy in range(3, 6):
                    w.put(x_front, y + dy, z, "deepslate_bricks" if _hash(x_front, y + dy, z, 224) < 0.85 else "cracked_deepslate_bricks")
                # straw on the floor, a chain on the back wall
                if _hash(x_back, y, z, 222) < 0.5:
                    put_if_free(w, x_back + step * -1, y, z, "hay_block", {"axis": "z"})
            put_if_free(w, x_back + step * -1, y + 3, z0 + 3, "iron_chain", {"axis": "y"})
            if _hash(cell, 1, side == "west", 223) < 0.5:
                put_if_free(w, x_back + step * -2, y, z0 + 4, "cauldron")
            if cell in (1, 4) and side == "east" or cell == 2 and side == "west":
                mob(w, (x_front + x_back) // 2, y, z0 + 3, "minecraft:villager", {
                    "VillagerData": {"profession": "forja:forjador", "level": 2, "type": "minecraft:plains"},
                    "CustomName": {"text": "Forjador preso"}})
    # The shaft to the vault, under a trapdoor in the straw of the sixth west cell - the one that sits over the
    # vault (the plan's "cell 7" is past the vault's end). A ladder all the way down with stone at its back,
    # which down in the vault stands as a pillar with the ladder on its face.
    sx, sz = 51, 103
    for yy in range(L2, y - 1):
        w.put(sx, yy, sz - 1, "deepslate_bricks")
        w.put(sx, yy, sz, "ladder", {"facing": "south", "waterlogged": "false"})
    w.put(sx, y - 1, sz, "spruce_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
    put_if_free(w, sx + 1, y, sz, "hay_block", {"axis": "z"})
    # the passage: a table for the gaolers, a rack of chains
    for z in (74, 92, 108):
        put_if_free(w, 59, y, z, "dark_oak_fence")
        put_if_free(w, 59, y + 1, z, "candle", CANDLE)


def dungeon_guard(w):
    y = L1
    for x in range(58, 66):
        w.put(x, y, 63, *slab("dark_oak_slab", top=True))
    for x in (58, 65):
        w.put(x, y, 63, "dark_oak_fence")
    for x in range(59, 65, 2):
        put_if_free(w, x, y, 62, *stairs("spruce_stairs", "south"))
        put_if_free(w, x, y, 64, *stairs("spruce_stairs", "north"))
    w.put(61, y + 1, 63, "candle", CANDLE)
    for x in (67, 68, 69):
        barrel(w, x, y, 59, "forja:chests/bastion_guardia" if x == 68 else None)
    stand(w, 68, y, 66, 90.0, {"head": "iron_helmet", "chest": "chainmail_chestplate", "mainhand": "iron_sword"})
    stand(w, 66, y, 66, 90.0, {"head": "chainmail_helmet", "chest": "iron_chestplate"})
    for x in (58, 62):
        mob(w, x, y, 66, "forja:tenaza")


def larder(w):
    """Casks racked three high down both long walls, a table in the middle, and what is left of the salt meat."""
    y = L1
    for z in range(103, 118):
        for x, facing in ((73, "east"), (87, "west")):
            for dy in range(0, 3):
                if _hash(x, y + dy, z, 230) < 0.85:
                    w.put(x, y + dy, z, "barrel", {"facing": facing, "open": "false"},
                          {"id": "minecraft:barrel", "LootTable": "forja:chests/bastion_bodega"} if dy == 0 and z % 5 == 0 else None)
    for z in range(106, 115):
        w.put(80, y, z, *slab("spruce_slab", top=True))
    for z in (106, 114):
        w.put(80, y, z, "spruce_fence")
    for z in range(107, 114, 2):
        put_if_free(w, 79, y, z, *stairs("dark_oak_stairs", "east"))
        put_if_free(w, 81, y, z, *stairs("dark_oak_stairs", "west"))
    for z in (108, 112):
        put_if_free(w, 80, y + 1, z, "candle", CANDLE)
    for x in range(75, 86, 3):
        put_if_free(w, x, y + 4, 110, "hay_block", {"axis": "x"})


def ingots(w):
    """The strong room of the working castle: raw ore in a cage, the chests of bar, the scales and the anvil."""
    y = L1
    for x in range(132, 153):
        w.put(x, y, 111, "iron_bars")
        w.put(x, y + 1, 111, "iron_bars")
        w.put(x, y + 2, 111, "iron_bars")
    for x, block in ((134, "raw_iron_block"), (137, "raw_copper_block"), (140, "raw_iron_block"), (143, "raw_gold_block"), (146, "raw_copper_block")):
        w.put(x, y, 112, block)
    for x in range(133, 152, 3):
        chest(w, x, y, 101, "south", "forja:chests/bastion_lingotes")
    w.put(142, y, 106, "anvil", {"facing": "east"})
    w.put(145, y, 106, "cauldron")
    w.put(139, y, 106, "forja:mesa_de_forja")
    for x in (135, 149):
        w.put(x, y, 106, "lectern", {"facing": "south", "has_book": "false", "powered": "false"})


def coal_store(w):
    """Coal heaped to the vault, a rail out to the mine gallery, and the haulers who work it."""
    y = L1
    for x in range(132, 153):
        for z in range(57, 98):
            if x > 146 or z < 62:
                height = int(_hash(x, 0, z, 240) * 4) if (x > 148 or z < 60) else int(_hash(x, 0, z, 241) * 2)
                for dy in range(height):
                    put_if_free(w, x, y + dy, z, "coal_block")
    for z in range(64, 97):
        put_if_free(w, 140, y, z, "rail", {"shape": "north_south", "waterlogged": "false"})
    for z in (70, 84):
        w.entities.append({"pos": (140.5, float(y), z + 0.5), "nbt": {"id": "minecraft:minecart"}})
    for z in range(66, 96, 4):
        barrel(w, 133, y, z, "forja:chests/bastion_carbonera" if z % 8 == 2 else None)
    for x, z in ((136, 90), (144, 76)):
        mob(w, x, y, z, "forja:cargador_de_carbon")


# ------------------------------------------------------------------------------------------ level -2

def antechamber(w):
    """Nine apprentices stand in the dark in two ranks, where they were told to wait. They are only armour now."""
    y = L2
    ranks = [(x, 44) for x in range(89, 113, 5)] + [(x, 58) for x in range(92, 112, 5)]
    for x, z in ranks[:9]:
        w.put(x, y, z, "polished_blackstone")
        stand(w, x, y + 1, z, 0.0 if z == 44 else 180.0,
              {"head": "chainmail_helmet", "chest": "iron_chestplate", "legs": "chainmail_leggings", "mainhand": "iron_axe"})
    for x in (88, 113):
        for z in (47, 55):
            w.put(x, y, z, "polished_blackstone_brick_wall")
            w.put(x, y + 1, z, "soul_campfire", SOUL_FIRE)
    rug(w, 98, 42, 102, 61, y, inner="black_carpet", border="gray_carpet")
    for x in (86, 115):
        for z in (45, 51, 57):
            banner(w, x, y + 4, z, "east" if x == 86 else "west", colour="gray")


def soul_forge(w):
    """A forge that burns blue: soul fire in the hearths, soul soil under them, the anvils cold, the suits waiting."""
    y = L2
    for x in range(132, 153):
        for z in range(81, 104):
            if _hash(x, y, z, 250) < 0.25:
                w.put(x, y - 1, z, "soul_soil")
    for x, z in ((136, 84), (148, 84), (136, 100), (148, 100)):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                w.put(x + dx, y, z + dz, "polished_blackstone_bricks")
        w.put(x, y + 1, z, "soul_campfire", SOUL_FIRE)
        w.put(x, y + 3, z, "iron_chain", {"axis": "y"})
    for x, z in ((142, 88), (142, 96)):
        w.put(x, y, z, "chipped_anvil", {"facing": "east"})
    w.put(142, y, 92, "forja:mesa_de_forja")
    for z in (86, 98):
        put_if_free(w, 151, y, z, "blast_furnace", {"facing": "west", "lit": "false"})
    chest(w, 152, y, 92, "west", "forja:chests/bastion_almas")
    for x, z in ((139, 92), (145, 92)):
        mob(w, x, y, z, "forja:coraza_vacia")


def vault(w):
    """The vault: a floor of iron plate, the order's best chest on a pedestal, and the suits that were its keys."""
    y = L2
    for x in range(49, 72):
        for z in range(81, 104):
            w.put(x, y - 1, z, "chiseled_polished_blackstone" if (x + z) % 5 == 0 else "polished_blackstone_bricks")
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            w.put(60 + dx, y, 92 + dz, "gold_block" if dx == dz == 0 else "polished_blackstone_bricks")
    chest(w, 60, y + 1, 92, "south", "forja:chests/bastion_camara")
    for x, z, yaw in ((55, 86, 45.0), (65, 86, -45.0), (55, 98, 135.0), (65, 98, -135.0)):
        stand(w, x, y, z, yaw, {"head": "golden_helmet", "chest": "iron_chestplate", "legs": "chainmail_leggings", "feet": "golden_boots"})
    for x in range(50, 71, 4):
        for z in (82, 102):
            w.put(x, y, z, "polished_blackstone_brick_wall")
            w.put(x, y + 1, z, "candle", CANDLE)


def build(w):
    crypt(w)
    ossuary(w)
    dungeon(w)
    dungeon_guard(w)
    larder(w)
    ingots(w)
    coal_store(w)
    antechamber(w)
    soul_forge(w)
    vault(w)
