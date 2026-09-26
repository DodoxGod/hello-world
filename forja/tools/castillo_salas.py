"""The rooms of the upper ward's two ranges, the chapel, the mess and the alloy room: docs/castillo/plano_v3_completo.png,
numbers 26 to 36 and their upper floors. Furniture only - light is castillo_luz's business, which comes after.

The west range is the worse kept side: shelves with gaps, cobwebs, a bed frame with no bed. The east one works.
"""
import math

from castillo import HORIZONTAL, _hash, disc, noise_origin, slab, stairs
from castillo_interiores import ANVIL_BANNER, EMBER_BANNER, banner, barrel, chest, mob, opening, partition, rug

LOOT = "forja:chests/castillo_de_forja"
CANDLE = {"candles": "3", "lit": "true", "waterlogged": "false"}


def clear(w, x, y, z):
    return w.name(x, y, z) in (None, "minecraft:air")


def shelf(w, x, y, z, salt, gone=0.18):
    """A bookshelf, or what is left where one was."""
    roll = _hash(x, y, z, salt)
    if roll < gone:
        if roll < gone * 0.4:
            w.put(x, y, z, "cobweb")
        return
    w.put(x, y, z, "bookshelf")


def desk(w, x, z, y, facing):
    """A writing desk: a slab on the wall side, a stair to sit on, a candle to see by."""
    dx, dz = HORIZONTAL[facing]
    w.put(x, y, z, *slab("spruce_slab", top=True))
    w.put(x - dx, y, z - dz, *stairs("dark_oak_stairs", facing))
    if _hash(x, y, z, 140) < 0.6:
        w.put(x, y + 1, z, "candle", CANDLE)


def stand(w, x, y, z, yaw, pieces):
    """An armour stand wearing what the armoury still has."""
    equipment = {slot: {"id": f"minecraft:{item}", "count": 1} for slot, item in pieces.items()}
    w.entities.append({"pos": (x + 0.5, float(y), z + 0.5), "nbt": {"id": "minecraft:armor_stand", "Rotation": [float(yaw), 0.0],
                                                                      "equipment": equipment, "ShowArms": 1, "NoBasePlate": 0}})


def bed(w, x, y, z, facing, colour="gray"):
    dx, dz = HORIZONTAL[facing]
    w.put(x, y, z, f"{colour}_bed", {"facing": facing, "part": "foot", "occupied": "false"})
    w.put(x + dx, y, z + dz, f"{colour}_bed", {"facing": facing, "part": "head", "occupied": "false"})


def flight(w, x0, x1, z, y0, rise, facing="south", block="deepslate_brick_stairs", fill="deepslate_bricks"):
    """A straight stair `rise` high starting at z, climbing the way it faces, with the floor above opened over it."""
    dz = HORIZONTAL[facing][1]
    for k in range(rise):
        for x in range(x0, x1 + 1):
            w.put(x, y0 + k, z + dz * k, *stairs(block, facing))
            for y in range(y0, y0 + k):
                w.put(x, y, z + dz * k, fill)
            for y in range(y0 + k + 1, y0 + k + 5):
                w.air(x, y, z + dz * k)


# ------------------------------------------------------------------------------------------ the west range

def west_range(w):
    noise_origin(48, 0, 24)
    x0, x1 = 50, 68
    for storey, y in enumerate((0, 9)):
        for z in (45, 57, 79, 91):
            partition(w, x0, z, x1, z, y, y + 8, keep=False, decay=0.2)
            opening(w, 58, z, 60, z, y, y + 4)
            w.put(58, y + 3, z, *stairs("deepslate_brick_stairs", "east", top=True))
            w.put(60, y + 3, z, *stairs("deepslate_brick_stairs", "west", top=True))
    # the way up: in the hall of banners, along the west wall
    flight(w, 50, 52, 81, 0, 9)

    # -- library (26..44): shelves round the walls to the ceiling beams, three rows of double stacks, a reading table
    for y in range(0, 5):
        for z in range(26, 45):
            shelf(w, x0, y, z, 141)
        for x in range(x0, x1 + 1):
            if not 57 <= x <= 61:
                shelf(w, x, y, 26, 142)
    for z in (30, 34, 38):
        for x in list(range(53, 58)) + list(range(62, 67)):
            for y in range(0, 3):
                shelf(w, x, y, z, 143, 0.12)
    for x in (58, 60):
        w.put(x, 0, 42, "lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    rug(w, 57, 28, 61, 43, 0, "cyan_carpet", "black_carpet")
    chest(w, 67, 0, 43, "west", LOOT)
    w.put(66, 0, 43, "cartography_table")
    banner(w, 68, 5, 36, "west")

    # -- scriptorium (46..56): two rows of desks facing the windows, the master's at the head
    for z in (48, 51, 54):
        for x in (52, 55, 63, 66):
            desk(w, x, z, 0, "north")
    w.put(59, 0, 47, "lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    barrel(w, 50, 0, 46, LOOT)
    barrel(w, 50, 1, 46)
    barrel(w, 51, 0, 46)

    # -- armoury (58..78): stands down both walls, racks of tools in the middle, two hollow cuirasses that are not stands
    suits = ({"head": "iron_helmet", "chest": "iron_chestplate", "legs": "iron_leggings", "feet": "iron_boots"},
             {"head": "chainmail_helmet", "chest": "chainmail_chestplate"}, {"chest": "iron_chestplate", "mainhand": "iron_sword"},
             {"head": "iron_helmet", "mainhand": "iron_axe"}, {})
    for k, z in enumerate(range(60, 78, 3)):
        stand(w, 51, 0, z, -90, suits[k % len(suits)])
        if not 67 <= z <= 71:
            stand(w, 67, 0, z, 90, suits[(k + 2) % len(suits)])
    for z in (63, 73):
        for x in range(56, 63):
            w.put(x, 0, z, "polished_deepslate_wall" if x % 2 else "deepslate_brick_wall")
            w.put(x, 1, z, *slab("polished_deepslate_slab"))
    w.put(59, 0, 68, "forja:yunque_del_herrero", {"facing": "east"})
    w.put(57, 0, 68, "grindstone", {"face": "floor", "facing": "north"})
    w.put(61, 0, 68, "smithing_table")
    chest(w, 55, 0, 58, "south", LOOT)
    chest(w, 63, 0, 58, "south", LOOT)
    for x in (53, 59, 65):
        banner(w, x, 5, 58, "south", ANVIL_BANNER, "gray")
    mob(w, 54, 0, 70, "forja:coraza_vacia")
    mob(w, 64, 0, 66, "forja:coraza_vacia")

    # -- hall of banners (80..90): the guild's colours down both walls, the stair against the west one
    for z in range(81, 90, 2):
        banner(w, 68, 5, z, "west", EMBER_BANNER if z % 4 == 1 else ANVIL_BANNER, "black" if z % 4 == 1 else "gray")
    rug(w, 57, 80, 61, 90, 0, "orange_carpet", "black_carpet")

    # -- barracks (92..110): bunks down the west wall, a table, the rack by the door
    for z in range(93, 110, 3):
        bed(w, 51, 0, z, "west")
        w.put(53, 0, z, "barrel", {"facing": "up", "open": "false"})
    for x in range(61, 66):
        w.put(x, 0, 104, *slab("dark_oak_slab", top=True))
        w.put(x, 0, 103, *stairs("spruce_stairs", "south"))
        w.put(x, 0, 105, *stairs("spruce_stairs", "north"))
    chest(w, 67, 0, 109, "west", LOOT)
    stand(w, 66, 0, 94, 90, suits[0])

    # -- upstairs: the upper library, the fencing hall, the dormitory
    for y in range(9, 13):
        for z in range(26, 45):
            shelf(w, x0, y, z, 144, 0.3)
            shelf(w, x1, y, z, 145, 0.3)
    for z in (31, 39):
        for x in range(55, 64):
            for y in (9, 10):
                shelf(w, x, y, z, 146, 0.25)
    w.put(59, 9, 35, "enchanting_table")
    rug(w, 56, 60, 62, 76, 9, "gray_carpet", "red_carpet")
    for z in (60, 76):
        stand(w, 59, 9, z, 0 if z == 76 else 180, {"chest": "iron_chestplate", "mainhand": "iron_sword", "head": "iron_helmet"})
    for z in (62, 68, 74):
        w.put(51, 9, z, "target")
        w.put(51, 10, z, "target")
    mob(w, 59, 9, 68, "forja:yunque_andante")
    for z in range(93, 110, 3):
        for x, facing in ((51, "west"), (66, "east")):
            if _hash(x, 9, z, 147) < 0.75:
                bed(w, x, 9, z, facing, "red" if _hash(x, 9, z, 148) < 0.3 else "gray")
            else:
                w.put(x, 9, z, "cobweb")
        w.put(59, 9, z, "barrel", {"facing": "up", "open": "false"})
    chest(w, 59, 9, 109, "north", LOOT)
    noise_origin()


# ------------------------------------------------------------------------------------------ the chapel and the sacristy

def chapel(w):
    """The Chapel of the Anvil: benches either side of a nave, and at the head not an altar but the anvil, on three steps,
    under the red window. Half the vault is down, so rubble lies where it fell and the benches under it are gone."""
    noise_origin(131, 0, 24)
    for x in range(133, 152):
        for z in range(26, 43):
            w.put(x, -1, z, "polished_blackstone" if (x + z) % 2 else "polished_deepslate")
    rug(w, 135, 33, 148, 35, 0, "red_carpet", "black_carpet")
    for x in range(136, 146, 2):
        for z in list(range(28, 32)) + list(range(37, 41)):
            if _hash(x, 0, z, 150) < 0.82:
                w.put(x, 0, z, *stairs("dark_oak_stairs", "west"))
            elif _hash(x, 0, z, 151) < 0.5:
                w.put(x, 0, z, "cobbled_deepslate")
    for step, x in enumerate((148, 149, 150)):
        for z in range(31 + step, 38 - step):
            for y in range(step + 1):
                w.put(x, y, z, "polished_blackstone_bricks")
    w.put(150, 3, 34, "forja:yunque_del_herrero", {"facing": "west"})
    for z in (32, 36):
        w.put(150, 3, z, "candle", {"candles": "4", "lit": "true", "waterlogged": "false"})
        w.put(149, 2, z - 1 if z == 32 else z + 1, "forja:farol_de_pavesa")
    for z in (28, 40):
        banner(w, 151, 8, z, "west", ANVIL_BANNER, "gray")
    w.put(134, 0, 27, "bell", {"attachment": "floor", "facing": "north", "powered": "false"})
    chest(w, 150, 0, 27, "west", LOOT)
    noise_origin()


def sacristy(w):
    """Where the talismans are kept: item frames would want entities with the mod's items in them, so they are in the chests,
    and what shows is the cases - glass over gilded blackstone - and the ledger."""
    for x in range(135, 150, 3):
        w.put(x, 0, 48, "gilded_blackstone")
        w.put(x, 1, 48, "glass")
    chest(w, 150, 0, 52, "west", LOOT)
    chest(w, 150, 0, 50, "west", LOOT)
    w.put(142, 0, 52, "lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    rug(w, 134, 49, 149, 51, 0, "purple_carpet", "black_carpet")


# ------------------------------------------------------------------------------------------ the mess and the alloy room

def mess(w):
    for z in (106, 110, 114):
        for x in range(75, 86):
            w.put(x, 0, z, *slab("spruce_slab", top=True))
            if x in (75, 80, 85):
                w.put(x, 0, z, "spruce_fence")
        for x in range(76, 85, 2):
            w.put(x, 0, z - 1, *stairs("dark_oak_stairs", "south"))
    for x in (74, 75, 76):
        w.put(x, 0, 116, "barrel", {"facing": "up", "open": "false"})
    w.put(74, 1, 116, "barrel", {"facing": "up", "open": "false"})
    w.put(86, 0, 116, "smoker", {"facing": "north", "lit": "false"})
    w.put(85, 0, 116, "cauldron")
    chest(w, 84, 0, 116, "north", LOOT)


def alloys(w):
    """The alloy room: two crucibles over embers, the bins of what goes into them, and the book of mixtures."""
    for x in (117, 121):
        w.put(x, -1, 114, "magma_block")
        w.put(x, 0, 114, "forja:farol_de_pavesa")
        w.put(x, 1, 114, "forja:crisol_de_obsidiana")
    w.put(119, 0, 114, "forja:cuba_de_colada")
    for k, ore in enumerate(("raw_iron_block", "raw_copper_block", "raw_gold_block", "coal_block", "quartz_block")):
        w.put(115 + k * 2, 0, 104, ore)
        if k % 2 == 0:
            w.put(115 + k * 2, 1, 104, ore)
    w.put(126, 0, 108, "lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    w.put(126, 0, 110, "forja:mesa_de_losa", {"lit": "false"})
    barrel(w, 126, 0, 112, LOOT)
    banner(w, 120, 5, 103, "south")
    mob(w, 120, 0, 110, "forja:molde_roto")


# ------------------------------------------------------------------------------------------ the great tower, upstairs

FIRE = {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"}
GUARD = {"head": "golden_helmet", "chest": "iron_chestplate", "legs": "iron_leggings", "feet": "iron_boots"}


def keep_upstairs(w):
    """Storeys one to four of the torre del homenaje and the observatory on its roof. The best kept rooms in the castle:
    whole carpets, no cobwebs, and the garrison that keeps them so. Two stair lanes against the east wall, used in turn,
    so that each flight has over it only the one two floors up."""
    noise_origin(72, 0, 17)
    lanes = ((124, 125, 40, "south"), (121, 122, 48, "north"))
    for storey, y in enumerate((9, 18, 27, 36)):
        x_a, x_b, z, facing = lanes[storey % 2]
        if y > 9:
            partition(w, 75, 38, 126, 38, y, y + 8)
            opening(w, 98, 38, 103, 38, y, y + 4)
        flight(w, x_a, x_b, z, y, 9, facing, "polished_blackstone_brick_stairs", "polished_blackstone_bricks")
    for y in (18, 27):
        partition(w, 100, 39, 100, 67, y, y + 8)
        opening(w, 100, 52, 100, 54, y, y + 4)

    # -- 1: the throne of the Grand Master, over the stamping hall (the rest of this floor is the hall's gallery)
    rug(w, 98, 23, 103, 37, 9, "red_carpet", "black_carpet")
    for x in range(96, 106):
        for z in (20, 21):
            w.put(x, 9, z, "polished_blackstone_bricks")
        w.put(x, 9, 22, *stairs("polished_blackstone_brick_stairs", "north"))
    for x in (100, 101):
        w.put(x, 10, 21, *stairs("polished_blackstone_stairs", "south"))
        w.put(x, 10, 20, "gilded_blackstone")
        w.put(x, 11, 20, "gold_block")
    for x in (99, 102):
        w.put(x, 10, 21, "gilded_blackstone")
        w.put(x, 10, 20, "gilded_blackstone")
        w.put(x, 11, 20, "gilded_blackstone")
    for x in (92, 109):
        w.put(x, 9, 24, "polished_blackstone_brick_wall")
        w.put(x, 10, 24, "campfire", FIRE)
    for x in (88, 94, 107, 113):
        banner(w, x, 14, 20, "south")
    for z in (26, 32):
        stand(w, 87, 9, z, -90, dict(GUARD, mainhand="iron_sword"))
        stand(w, 114, 9, z, 90, dict(GUARD, mainhand="iron_axe"))

    # -- 2: the Council of the Nine, the trophies, the maps
    for dx, dz in disc(4.4):
        w.put(100 + dx, 18, 28 + dz, *slab("dark_oak_slab", top=True))
    for k in range(9):
        angle = math.tau * k / 9
        sx, sz = round(100 + math.cos(angle) * 6), round(28 + math.sin(angle) * 6)
        facing = "west" if math.cos(angle) > 0.7 else "east" if math.cos(angle) < -0.7 else "north" if math.sin(angle) > 0 else "south"
        w.put(sx, 18, sz, *stairs("dark_oak_stairs", facing))
    w.put(100, 19, 28, "candle", {"candles": "4", "lit": "true", "waterlogged": "false"})
    for x in (80, 120):
        banner(w, x, 23, 20, "south", ANVIL_BANNER, "gray")
    heads = ("skeleton_skull", "zombie_head", "creeper_head", "wither_skeleton_skull", "piglin_head")
    for k, z in enumerate(range(42, 66, 5)):
        w.put(76, 18, z, "polished_blackstone_brick_wall")
        w.put(76, 19, z, heads[k % len(heads)], {"rotation": "12", "powered": "false"})
        stand(w, 97, 18, z, 90, {"chest": "diamond_chestplate"} if k == 2 else {"head": "iron_helmet", "chest": "chainmail_chestplate", "mainhand": "iron_sword"})
    rug(w, 84, 44, 92, 62, 18, "gray_carpet", "orange_carpet")
    chest(w, 76, 18, 66, "east", LOOT)
    mob(w, 88, 18, 50, "forja:coraza_vacia")
    mob(w, 88, 18, 58, "forja:coraza_vacia")
    for x in range(108, 118):
        for z in range(48, 58):
            w.put(x, 18, z, *slab("spruce_slab", top=True))
            if (x + z) % 3 == 0:
                w.put(x, 19, z, "green_carpet" if (x * 7 + z) % 2 else "light_gray_carpet")
    for z in (42, 46, 60, 64):
        w.put(126, 18, z, "cartography_table")
    w.put(104, 18, 66, "lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    chest(w, 118, 18, 66, "north", LOOT)

    # -- 3: the Grand Master's rooms, the study, the counting house
    bed(w, 100, 27, 21, "north", "red")
    bed(w, 101, 27, 21, "north", "red")
    rug(w, 96, 24, 105, 34, 27, "red_carpet", "orange_carpet")
    for x in (98, 103):
        w.put(x, 27, 20, "bookshelf")
        w.put(x, 28, 20, "bookshelf")
    chest(w, 96, 27, 20, "south", LOOT)
    stand(w, 106, 27, 21, 0, {"head": "golden_helmet", "chest": "golden_chestplate", "legs": "golden_leggings", "feet": "golden_boots"})
    for z in range(40, 67):
        for y in range(27, 31):
            shelf(w, 75, y, z, 160, 0.05)
    for x in range(84, 90):
        w.put(x, 27, 52, *slab("dark_oak_slab", top=True))
    w.put(86, 27, 53, *stairs("dark_oak_stairs", "north"))
    w.put(88, 28, 52, "candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    w.put(85, 27, 50, "lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    rug(w, 82, 48, 92, 58, 27, "brown_carpet", "black_carpet")
    for x in range(104, 118):
        for y in range(27, 31):
            w.put(x, y, 58, "iron_bars")
    opening(w, 110, 58, 111, 58, 27, 29)
    for x in range(106, 117, 2):
        if x % 4 == 2:
            chest(w, x, 27, 66, "north", LOOT)
        else:
            barrel(w, x, 27, 66)
        w.put(x, 27, 62, "gold_block" if x % 4 == 0 else "raw_gold_block")
    mob(w, 110, 27, 50, "forja:tenaza")

    # -- 4: the stores under the roof: what a garrison eats and burns, stacked the way somebody who counts it stacks it
    for x in range(78, 118, 6):
        for z in (44, 50, 56, 62):
            for dx in (0, 1):
                for dz in (0, 1):
                    tall = 1 + int(_hash(x + dx, 36, z + dz, 170) * 3)
                    for y in range(tall):
                        w.put(x + dx, 36 + y, z + dz, "barrel", {"facing": "up" if _hash(x + dx, y, z + dz, 171) < 0.7 else "north", "open": "false"})
    for x in range(80, 120, 8):
        w.put(x, 36, 24, "hay_block", {"axis": "y"})
        w.put(x + 1, 36, 24, "hay_block", {"axis": "x"})
        w.put(x, 37, 24, "hay_block", {"axis": "z"})
        w.put(x + 3, 36, 24, "coal_block")
        w.put(x + 3, 36, 25, "coal_block")
    chest(w, 76, 36, 22, "east", LOOT)
    chest(w, 76, 36, 24, "east", LOOT)

    # -- what holds all those floors up: two rows of pillars down every storey, a beam across each pair. Rooms this
    # wide with nothing standing in them are not halls, they are car parks.
    for y in (18, 27, 36):
        for z in range(42, 67, 8):
            for x in (82, 94, 106, 118):
                if all(clear(w, x, y + k, z) or "carpet" in (w.name(x, y + k, z) or "") for k in range(8)):
                    for k in range(8):
                        w.put(x, y + k, z, "polished_basalt", {"axis": "y"})
                    w.put(x, y, z, "chiseled_polished_blackstone")
                    for side, (dx, dz) in HORIZONTAL.items():
                        if clear(w, x + dx, y + 7, z + dz):
                            w.put(x + dx, y + 7, z + dz, *stairs("polished_blackstone_brick_stairs", {"north": "south", "south": "north", "east": "west", "west": "east"}[side], top=True))
            for x in range(83, 118):
                if x not in (94, 106) and clear(w, x, y + 7, z) and not 99 <= x <= 101:
                    w.put(x, y + 7, z, "dark_oak_log", {"axis": "x"})

    # -- the observatory, inside the drum on the roof: the glass on its mount, and what came down it
    ox, oz = 100, 37
    for k in range(5):
        w.put(ox, 46 + k, oz + 2 + k, "waxed_copper_block" if k < 4 else "tinted_glass")
    for dx, dz in ((3, 0), (-3, 0), (0, -3)):
        w.put(ox + dx, 46, oz + dz, "lodestone")
        w.put(ox + dx, 47, oz + dz, "amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    chest(w, ox - 6, 46, oz - 6, "south", LOOT)
    mob(w, ox, 47, oz - 1, "forja:nucleo_estelar")
    noise_origin()


# ------------------------------------------------------------------------------------------ the lower ward, indoors

def table(w, x, z, y=0, wood="spruce"):
    """A small table and two stools: a fence, a pressure plate, two stairs."""
    w.put(x, y, z, f"{wood}_fence")
    w.put(x, y + 1, z, f"{wood}_pressure_plate", {"powered": "false"})
    w.put(x - 1, y, z, *stairs("dark_oak_stairs", "west"))
    w.put(x + 1, y, z, *stairs("dark_oak_stairs", "east"))


def lower_ward_rooms(w):
    """What the raiders have left of the houses between the walls. They drink in the tavern and sleep in the guard
    house; the rest they have only turned over."""
    # -- the tavern "El Yunque Roto" (117..145 x 156..168, two floors): the bar along the north wall, tables, rooms upstairs
    for x in range(121, 133):
        w.put(x, 0, 159, *slab("dark_oak_slab", top=True)) if x % 4 else w.put(x, 0, 159, "barrel", {"facing": "south", "open": "false"})
    for x in (122, 126, 130):
        w.put(x, 0, 157, "barrel", {"facing": "up", "open": "false"})
        w.put(x, 1, 157, "barrel", {"facing": "south", "open": "true" if x == 126 else "false"})
    w.put(134, 0, 157, "brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
    for x, z in ((121, 164), (127, 165), (133, 163), (139, 165)):
        table(w, x, z)
    w.put(143, 0, 160, "forja:yunque_del_herrero", {"facing": "west"})        # the broken anvil the house is named for
    banner(w, 144, 4, 162, "west", ANVIL_BANNER, "gray")
    chest(w, 144, 0, 157, "west", "forja:chests/campamento_saqueadores")
    flight(w, 136, 137, 158, 0, 7, "south", "spruce_stairs", "spruce_planks")
    for x in range(124, 143, 6):
        bed(w, x, 7, 158, "north", "brown")
        w.put(x + 1, 7, 157, "barrel", {"facing": "up", "open": "false"})
    chest(w, 144, 7, 166, "west", LOOT)

    # -- the commissions house (149..193 x 156..168): the counter, the ledgers, the racks of finished work nobody came for
    for x in range(156, 187):
        if x % 10 != 1:
            w.put(x, 0, 161, *slab("dark_oak_slab", top=True))
    for x in (160, 170, 180):
        w.put(x, 0, 159, "lectern", {"facing": "south", "has_book": "false", "powered": "false"})
        stand(w, x + 3, 0, 158, 180, {"chest": "iron_chestplate"} if x != 170 else {"head": "iron_helmet", "mainhand": "iron_pickaxe"})
    for x in range(152, 190, 5):
        w.put(x, 0, 166, "barrel", {"facing": "up", "open": "false"})
        if _hash(x, 0, 166, 180) < 0.5:
            w.put(x, 1, 166, "barrel", {"facing": "north", "open": "false"})
    chest(w, 191, 0, 158, "west", LOOT)
    w.put(190, 0, 166, "forja:armario_de_piezas", {"facing": "north"})
    flight(w, 150, 151, 158, 0, 7, "south", "spruce_stairs", "spruce_planks")
    for x in range(158, 188, 6):
        table(w, x, 162, 7, "dark_oak")

    # -- the guard house (119..141 x 186..194): where the band sleeps now
    for x in range(121, 140, 4):
        bed(w, x, 0, 192, "south", "black")
    w.put(139, 0, 188, "grindstone", {"face": "floor", "facing": "west"})
    chest(w, 140, 0, 187, "west", "forja:chests/campamento_saqueadores")
    banner(w, 130, 4, 187, "south", None, "black")

    # -- stables (8..44 x 172..184): six stalls, hay, the tack; saddlery next door with the mod's own bench
    for x in range(12, 42, 5):
        for z in range(179, 184):
            w.put(x, 0, z, "spruce_fence")
        w.put(x + 2, 0, 182, "hay_block", {"axis": "y"})
        if _hash(x, 0, 182, 181) < 0.5:
            w.put(x + 3, 0, 183, "hay_block", {"axis": "x"})
        w.put(x + 2, 0, 179, "spruce_fence_gate", {"facing": "north", "in_wall": "false", "open": "true" if _hash(x, 1, 179, 182) < 0.4 else "false", "powered": "false"})
    w.put(10, 0, 174, "cauldron")
    w.put(52, 0, 174, "forja:mesa_de_talabarteria", {"facing": "south"})
    for x in range(56, 80, 4):
        w.put(x, 0, 183, "loom", {"facing": "north"}) if x % 8 == 0 else stand(w, x, 0, 182, 180, {"chest": "leather_chestplate", "legs": "leather_leggings"})
    chest(w, 82, 0, 182, "west", LOOT)
    barrel(w, 50, 0, 183, LOOT)

    # -- the fletcher's (64..84 x 156..168) and the powder house (169..187 x 76..88)
    w.put(68, 0, 158, "fletching_table")
    w.put(70, 0, 158, "fletching_table")
    for z in (160, 163, 166):
        w.put(82, 0, z, "target")
        w.put(82, 1, z, "target")
    barrel(w, 66, 0, 166, LOOT)
    for x in range(172, 186, 3):
        for z in (79, 85):
            w.put(x, 0, z, "barrel", {"facing": "up", "open": "false"})
            w.put(x, 1, z, "barrel", {"facing": "up", "open": "false"})
    chest(w, 185, 0, 82, "west", LOOT)


def build(w):
    lower_ward_rooms(w)
    west_range(w)
    chapel(w)
    sacristy(w)
    mess(w)
    alloys(w)
    keep_upstairs(w)
