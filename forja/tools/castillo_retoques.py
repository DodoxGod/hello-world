"""The rooms that were built and then left bare: furniture along their walls.

The audit (every room measured at the height a player sees) found the stamping hall with sixteen things in five
hundred blocks of floor, the stores under the keep's roof with a row of hay down the middle and nothing else, the
fletcher's and the saddlery with a bench each, the orb cabinet with nothing at all. This does what the other
modules do for the rooms they were written for, but for rooms somebody else already built: it reads the walls off
the built castle - a free cell with stone at its back, away from any doorway, stair or hole in the floor - and
stands the room's things there, a few apart, leaving the middle to walk and fight in.

Runs after the towers and the cellars and before the light, which lights what it finds.
"""
import math

import castillo
from castillo import HORIZONTAL, _hash, slab, stairs
from castillo_interiores import CHAIN, FIRE, banner, barrel, chest, mob
from castillo_salas import CANDLE, desk, shelf, stand

SOUL_FIRE = {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"}
AIR = (None, "minecraft:air", "minecraft:cave_air")


def _free(w, x, y, z, high=3):
    return all(w.name(x, y + k, z) in AIR for k in range(high))


def _solid(w, x, y, z):
    name = w.name(x, y, z)
    return name not in AIR and castillo.is_full(name)


def _awkward(w, x, y, z):
    """Near something a piece must not stand against: a stair, a ladder, a door, a hole in the floor."""
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in (-1, 0, 1):
                name = w.name(x + dx, y + dy, z + dz) or ""
                if "stairs" in name or "ladder" in name or "_door" in name or "trapdoor" in name or "fence_gate" in name:
                    return True
            if abs(dx) <= 1 and abs(dz) <= 1 and w.name(x + dx, y - 1, z + dz) in AIR:
                return True
    return False


def wall_cells(w, box, y, keep_out=None, spacing=3, salt=0, high=3):
    """(x, z, facing) for free cells in `box` with a wall at their back, kept off doorways, `spacing` apart.

    A doorway is any cell just outside the box, or inside it against its edge, that is open at y and y + 1: a
    piece within two of one would stand in somebody's way in. `keep_out(x, z)` says where the room wants floor.
    """
    x0, z0, x1, z1 = box
    doors = []
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            edge = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            if edge and w.name(x, y, z) in AIR and w.name(x, y + 1, z) in AIR:
                doors.append((x, z))
    cells = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if keep_out and keep_out(x, z):
                continue
            if not _free(w, x, y, z, high) or w.name(x, y - 1, z) in AIR:
                continue
            back = None
            for side, (dx, dz) in HORIZONTAL.items():
                if all(_solid(w, x + dx, y + k, z + dz) for k in range(high)):
                    back = side
                    break
            if back is None:
                continue
            if any(max(abs(x - dx), abs(z - dz)) <= 2 for dx, dz in doors):
                continue
            if _awkward(w, x, y, z):
                continue
            facing = {"north": "south", "south": "north", "east": "west", "west": "east"}[back]
            cells.append((x, z, facing))
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    cells.sort(key=lambda c: math.atan2(c[1] - cz, c[0] - cx))
    offset = int(_hash(salt, y, 0, 301) * spacing)
    return cells[offset::spacing]


def dress(w, box, y, pieces, keep_out=None, spacing=3, salt=0, limit=None):
    """Stand `pieces` in turn along the room's walls; returns how many went in."""
    count = 0
    for index, (x, z, facing) in enumerate(wall_cells(w, box, y, keep_out, spacing, salt)):
        if limit is not None and count >= limit:
            break
        if _free(w, x, y, z):
            pieces[index % len(pieces)](w, x, y, z, facing, salt + index)
            count += 1
    return count


def outside(x0, z0, x1, z1):
    """keep_out for a rectangle of floor the room keeps clear."""
    return lambda x, z: x0 <= x <= x1 and z0 <= z <= z1


# ------------------------------------------------------------------------------------------ pieces
# Each stands at (x, y, z) with the wall behind it, looking `facing` into the room. y is the walking level.

def p_barrels(loot=None, share=0.3):
    def place(w, x, y, z, facing, salt):
        barrel(w, x, y, z, loot if loot and _hash(x, y, z, salt) < share else None)
        if _hash(x, y, z, salt + 1) < 0.55:
            barrel(w, x, y + 1, z)
    return place


def p_chest(loot):
    def place(w, x, y, z, facing, salt):
        chest(w, x, y, z, facing, loot)
    return place


def p_block(name, props=None, high=1):
    def place(w, x, y, z, facing, salt):
        for k in range(high):
            w.put(x, y + k, z, name, dict(props or {}))
    return place


def p_faced(name, props=None):
    def place(w, x, y, z, facing, salt):
        w.put(x, y, z, name, dict(props or {}, facing=facing))
    return place


def p_stand(pieces):
    def place(w, x, y, z, facing, salt):
        stand(w, x, y, z, {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}[facing], pieces)
    return place


def p_shelves(w, x, y, z, facing, salt):
    for k in range(3):
        shelf(w, x, y + k, z, salt + k, 0.1)


def p_chiseled(w, x, y, z, facing, salt):
    props = {"facing": facing}
    for slot in range(6):
        props[f"slot_{slot}_occupied"] = "true" if _hash(x, y + slot, z, salt) < 0.6 else "false"
    w.put(x, y, z, "chiseled_bookshelf", props)
    w.put(x, y + 1, z, "bookshelf")


def p_desk(w, x, y, z, facing, salt):
    # desk() puts the seat on the room side of the slab
    back = {"north": "south", "south": "north", "east": "west", "west": "east"}[facing]
    dx, dz = HORIZONTAL[facing]
    if _free(w, x + dx, y, z + dz):
        desk(w, x, z, y, back)
    else:
        w.put(x, y, z, *slab("spruce_slab", top=True))


def p_table(w, x, y, z, facing, salt):
    w.put(x, y, z, "spruce_fence")
    w.put(x, y + 1, z, "spruce_pressure_plate", {"powered": "false"})
    dx, dz = HORIZONTAL[facing]
    if _free(w, x + dx, y, z + dz):
        w.put(x + dx, y, z + dz, *stairs("dark_oak_stairs", facing))
    if _hash(x, y, z, salt) < 0.5:
        w.put(x, y + 1, z, "candle", CANDLE)


def p_lectern(w, x, y, z, facing, salt):
    w.put(x, y, z, "lectern", {"facing": facing, "has_book": "false", "powered": "false"})


def p_anvil(w, x, y, z, facing, salt):
    w.put(x, y, z, "chipped_anvil" if _hash(x, y, z, salt) < 0.4 else "anvil", {"facing": facing})


def p_brazier(w, x, y, z, facing, salt):
    w.put(x, y, z, "polished_blackstone_brick_wall")
    w.put(x, y + 1, z, "campfire", FIRE)


def p_soul_brazier(w, x, y, z, facing, salt):
    w.put(x, y, z, "polished_blackstone_brick_wall")
    w.put(x, y + 1, z, "soul_campfire", SOUL_FIRE)


def p_banner(colour="black", patterns=None):
    def place(w, x, y, z, facing, salt):
        banner(w, x, y + 2, z, facing, patterns, colour)
    return place


def p_pot(w, x, y, z, facing, salt):
    w.put(x, y, z, "decorated_pot", {"facing": facing, "waterlogged": "false"})


def p_cauldron(w, x, y, z, facing, salt):
    if _hash(x, y, z, salt) < 0.6:
        w.put(x, y, z, "water_cauldron", {"level": "3"})
    else:
        w.put(x, y, z, "cauldron")


def p_hay(w, x, y, z, facing, salt):
    w.put(x, y, z, "hay_block", {"axis": "y"})
    if _hash(x, y, z, salt) < 0.4:
        w.put(x, y + 1, z, "hay_block", {"axis": "x" if facing in ("north", "south") else "z"})


def p_forja(block, props=None):
    def place(w, x, y, z, facing, salt):
        w.put(x, y, z, f"forja:{block}", dict(props) if props else None)
    return place


# the stamping hall: presses, dies, the coin as it comes off them, the ledgers it is counted into
def p_press(w, x, y, z, facing, salt):
    w.put(x, y, z, "anvil", {"facing": facing})
    w.put(x, y + 1, z, "iron_chain", CHAIN)
    w.put(x, y + 2, z, "piston", {"facing": "down", "extended": "false"})


def p_die(w, x, y, z, facing, salt):
    w.put(x, y, z, "polished_blackstone_brick_wall")
    w.put(x, y + 1, z, "heavy_core", {"waterlogged": "false"})


def p_coins(w, x, y, z, facing, salt):
    w.put(x, y, z, "barrel", {"facing": "up", "open": "false"})
    w.put(x, y + 1, z, "light_weighted_pressure_plate", {"power": "0"})


# the orb cabinet: the stones on show, the glass they are kept under, the books they are written up in
def p_case(w, x, y, z, facing, salt):
    w.put(x, y, z, "gilded_blackstone")
    w.put(x, y + 1, z, "amethyst_cluster", {"facing": "up", "waterlogged": "false"})


def p_amethyst(w, x, y, z, facing, salt):
    w.put(x, y, z, "amethyst_block" if _hash(x, y, z, salt) < 0.5 else "budding_amethyst")
    w.put(x, y + 1, z, "large_amethyst_bud", {"facing": "up", "waterlogged": "false"})


def p_glass_case(w, x, y, z, facing, salt):
    w.put(x, y, z, "polished_blackstone_bricks")
    w.put(x, y + 1, z, "tinted_glass" if _hash(x, y, z, salt) < 0.4 else "glass")


# leather, horses, arrows
def p_hide(w, x, y, z, facing, salt):
    w.put(x, y, z, "brown_wool")
    w.put(x, y + 1, z, "brown_carpet")


def p_bow_stand(w, x, y, z, facing, salt):
    p_stand({"mainhand": "bow", "chest": "leather_chestplate"})(w, x, y, z, facing, salt)


def p_arrow_bin(w, x, y, z, facing, salt):
    w.put(x, y, z, "fletching_table")
    w.put(x, y + 1, z, "white_carpet")


def p_target(w, x, y, z, facing, salt):
    w.put(x, y, z, "hay_block", {"axis": "y"})
    w.put(x, y + 1, z, "target", {"power": "0"})


# the deep places
def p_bones(w, x, y, z, facing, salt):
    w.put(x, y, z, "bone_block", {"axis": "y"})
    if _hash(x, y, z, salt) < 0.5:
        w.put(x, y + 1, z, "skeleton_skull", {"rotation": str(int(_hash(x, y, z, salt + 1) * 16)), "powered": "false"})


def p_soul_pile(w, x, y, z, facing, salt):
    w.put(x, y, z, "soul_soil")
    if _hash(x, y, z, salt) < 0.5:
        w.put(x, y + 1, z, "soul_lantern", {"hanging": "false", "waterlogged": "false"})


def p_statue(w, x, y, z, facing, salt):
    """A master's likeness: a stand in the order's iron, raised on a plinth."""
    w.put(x, y, z, "chiseled_polished_blackstone")
    stand(w, x, y + 1, z, {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}[facing],
          {"head": "iron_helmet", "chest": "iron_chestplate", "legs": "iron_leggings", "feet": "iron_boots", "mainhand": "iron_axe"})


def p_quench(w, x, y, z, facing, salt):
    w.put(x, y, z, "water_cauldron", {"level": "3"})


def p_ingots(w, x, y, z, facing, salt):
    pick = ("raw_iron_block", "raw_copper_block", "iron_bars", "chain")
    name = pick[int(_hash(x, y, z, salt) * len(pick))]
    if name == "chain":
        w.put(x, y, z, "iron_chain", {"axis": "x" if facing in ("north", "south") else "z", "waterlogged": "false"})
    elif name == "iron_bars":
        w.put(x, y, z, "iron_bars")
    else:
        w.put(x, y, z, name)


# ------------------------------------------------------------------------------------------ rooms

def stamping_hall(w):
    """The Sala de cuños keeps its middle for the Guardian's fight: the presses and the dies stand round the walls."""
    arena = outside(88, 22, 113, 37)
    dress(w, (84, 20, 117, 37), 0, [p_press, p_coins, p_die, p_lectern, p_press, p_pot, p_die,
                                    p_chest("forja:chests/bastion_lingotes"), p_coins], keep_out=arena, spacing=2, salt=11)


def keep_upstairs(w):
    # storey 2, the council's north end and the map room east
    dress(w, (75, 18, 126, 37), 18, [p_shelves, p_banner("gray"), p_brazier, p_stand({"head": "iron_helmet", "chest": "iron_chestplate"}), p_pot],
          keep_out=outside(90, 20, 110, 36), spacing=3, salt=12)
    dress(w, (101, 39, 126, 67), 18, [p_chiseled, p_lectern, p_shelves, p_desk, p_block("cartography_table")],
          keep_out=outside(106, 46, 119, 59), spacing=3, salt=13)
    # storey 3, the counting house: clerks' desks along the north of it, the ledgers
    dress(w, (101, 39, 126, 57), 27, [p_desk, p_chiseled, p_coins, p_desk, p_lectern, p_shelves], spacing=2, salt=14)
    # storey 4, the stores under the roof: what a garrison eats and burns, round every wall of it
    store = [p_barrels("forja:chests/bastion_bodega", 0.15), p_hay, p_barrels(), p_block("coal_block"), p_pot, p_barrels(), p_hay]
    dress(w, (75, 18, 126, 37), 36, store, spacing=2, salt=15)
    # and in the middle of the north end, stacks in rows as the south end has them, an aisle down the middle
    for x in range(78, 123, 5):
        if 96 <= x <= 105:
            continue
        for z in (27, 32):
            for dx in (0, 1):
                for dz in (0, 1):
                    if not _free(w, x + dx, 36, z + dz):
                        continue
                    tall = 1 + int(_hash(x + dx, 36, z + dz, 302) * 3)
                    for k in range(tall):
                        if _free(w, x + dx, 36 + k, z + dz, 1):
                            w.put(x + dx, 36 + k, z + dz, "barrel", {"facing": "up" if _hash(x + dx, k, z + dz, 303) < 0.7 else "north", "open": "false"})
    dress(w, (101, 39, 126, 67), 36, store, spacing=2, salt=16)
    dress(w, (75, 39, 99, 67), 36, store, spacing=2, salt=17)


def tavern_upstairs(w):
    dress(w, (118, 157, 144, 167), 7, [p_table, p_barrels(), p_chest("forja:chests/bastion_bodega"), p_pot, p_table, p_block("brown_wool")],
          spacing=3, salt=21, limit=12)


def fletcher(w):
    dress(w, (65, 157, 81, 167), 0, [p_bow_stand, p_arrow_bin, p_barrels("forja:chests/bastion_guardia", 0.3), p_hay,
                                     p_block("crafting_table"), p_bow_stand, p_pot], spacing=2, salt=22)


def saddlery(w):
    dress(w, (49, 173, 83, 183), 0, [p_hide, p_cauldron, p_stand({"chest": "leather_chestplate", "legs": "leather_leggings"}), p_barrels(),
                                     p_banner("brown"), p_block("loom", {"facing": "north"}), p_table, p_hide], spacing=2, salt=23)


def stables(w):
    dress(w, (9, 173, 43, 178), 0, [p_cauldron, p_hay, p_barrels(), p_stand({"mainhand": "saddle"}), p_hay, p_block("grindstone", {"face": "floor", "facing": "north"})],
          spacing=3, salt=24)


def bench_table(w, x0, x1, z, y, wood="dark_oak", seats=True):
    """A trestle table along x with stools either side, where the floor is free for it."""
    for x in range(x0, x1 + 1):
        if not all(_free(w, x, y, z + dz, 2) for dz in (-1, 0, 1)):
            continue
        w.put(x, y, z, *slab(f"{wood}_slab", top=True))
        if seats and x % 2 == 0:
            w.put(x, y, z - 1, *stairs("spruce_stairs", "south"))
            w.put(x, y, z + 1, *stairs("spruce_stairs", "north"))
        if _hash(x, y, z, 304) < 0.3:
            w.put(x, y + 1, z, "candle", CANDLE)


def guard_house(w):
    # the band's table down the middle: dice, drink, a candle
    bench_table(w, 125, 134, 189, 0)
    dress(w, (120, 187, 140, 191), 0, [p_brazier, p_stand({"head": "chainmail_helmet", "chest": "chainmail_chestplate", "mainhand": "crossbow"}),
                                       p_table, p_barrels("forja:chests/bastion_guardia", 0.25), p_banner("black")], spacing=3, salt=25)


def powder_house(w):
    dress(w, (170, 77, 186, 87), 0, [p_cauldron, p_block("sand"), p_barrels("forja:chests/campamento_saqueadores", 0.2), p_block("iron_bars", high=2)],
          spacing=2, salt=26)


def inner_barbican(w):
    # the gate passage down the middle stays open
    dress(w, (91, 115, 110, 133), 0, [p_brazier, p_stand({"head": "iron_helmet", "chest": "chainmail_chestplate", "mainhand": "iron_sword"}),
                                      p_barrels(), p_banner("black"), p_anvil], keep_out=outside(96, 113, 105, 135), spacing=3, salt=27)


def alloy_room(w):
    # the assay bench across the middle, where the mixtures are weighed out before they go in the crucibles
    bench_table(w, 116, 122, 109, 0, "spruce", seats=False)
    for x, block in ((116, "cauldron"), (122, "forja:mesa_de_losa")):
        if w.name(x, 0, 109) and "slab" in w.name(x, 0, 109):
            w.put(x, 0, 109, block, {"lit": "false"} if "mesa" in block else None)
    dress(w, (114, 103, 128, 117), 0, [p_ingots, p_shelves, p_barrels("forja:chests/bastion_lingotes", 0.2), p_quench, p_ingots],
          spacing=2, salt=28, limit=10)


def sacristy(w):
    dress(w, (132, 47, 152, 53), 0, [p_shelves, p_banner("gray"), p_pot, p_lectern], spacing=3, salt=29)


def orb_cabinet(w):
    """The Gabinete de orbes, upstairs of the master's workshop: never furnished at all until now."""
    box = (132, 101, 152, 111)
    y = 9
    placed = dress(w, box, y, [p_case, p_chiseled, p_glass_case, p_amethyst, p_shelves, p_block("brewing_stand"), p_case, p_lectern],
                   spacing=2, salt=30)
    # a long table down the middle with the stones laid out on it
    for x in range(138, 147):
        if _free(w, x, y, 106) and _free(w, x, y, 105) and _free(w, x, y, 107):
            w.put(x, y, 106, *slab("dark_oak_slab", top=True))
            if x % 3 == 0:
                w.put(x, y + 1, 106, "small_amethyst_bud", {"facing": "up", "waterlogged": "false"})
            elif x % 3 == 1:
                w.put(x, y + 1, 106, "candle", CANDLE)
    if _free(w, 142, y, 104):
        w.put(142, y, 104, "enchanting_table")
    return placed


def armoury_and_barracks(w):
    dress(w, (49, 59, 69, 77), 0, [p_anvil, p_barrels("forja:chests/bastion_guardia", 0.2), p_block("grindstone", {"face": "floor", "facing": "north"})],
          keep_out=outside(55, 58, 63, 78), spacing=4, salt=31, limit=6)
    dress(w, (49, 93, 69, 111), 0, [p_stand({"head": "iron_helmet", "chest": "iron_chestplate"}), p_brazier, p_chest("forja:chests/bastion_guardia")],
          keep_out=outside(55, 92, 63, 112), spacing=4, salt=32, limit=4)


def hall_of_banners(w):
    """The Sala de estandartes had its rug and a row of banners high on one wall. Now the guild's colours stand
    down both sides of the rug on their poles, as they would be carried, and the old ones lie folded in chests."""
    from castillo_interiores import ANVIL_BANNER, EMBER_BANNER
    for k, z in enumerate((82, 85, 88)):
        for x, rotation in ((55, "12"), (63, "4")):
            if _free(w, x, 0, z, 2):
                ember = (k + (x > 60)) % 2 == 0
                w.put(x, -1, z, "chiseled_polished_blackstone")
                w.put(x, 0, z, "black_banner" if ember else "gray_banner", {"rotation": rotation},
                      {"id": "minecraft:banner", "patterns": EMBER_BANNER if ember else ANVIL_BANNER})
    dress(w, (53, 81, 67, 89), 0, [p_pot, p_chest("forja:chests/bastion_torre"), p_stand({"head": "iron_helmet", "chest": "iron_chestplate", "mainhand": "iron_sword"})],
          keep_out=outside(54, 80, 64, 90), spacing=3, salt=36, limit=4)


def workshop(w):
    dress(w, (132, 101, 152, 111), 0, [p_barrels(), p_shelves, p_block("smithing_table"), p_pot, p_quench],
          keep_out=outside(133, 102, 148, 104), spacing=3, salt=37, limit=6)


def deep_forge(w):
    """The ambulatory behind the Deep Forge's columns: the masters' likenesses, quenches and anvils in the bays;
    inside the columns the floor stays the fight's."""
    cx, cz, r = 100, 92, 25
    y = -25
    ring = lambda x, z: (x - cx) ** 2 + (z - cz) ** 2 < (r - 1.6) ** 2
    dress(w, (cx - r, cz - r, cx + r, cz + r), y, [p_statue, p_anvil, p_quench, p_soul_brazier, p_statue, p_ingots, p_bones],
          keep_out=ring, spacing=5, salt=33)


def vault_and_soul_forge(w):
    dress(w, (49, 81, 71, 103), -25, [p_coins, p_pot, p_glass_case, p_chest("forja:chests/bastion_lingotes"), p_coins, p_pot],
          keep_out=outside(56, 88, 64, 96), spacing=3, salt=34, limit=10)
    dress(w, (132, 81, 152, 103), -25, [p_soul_pile, p_anvil, p_soul_brazier, p_bones, p_forja("mesa_de_forja")],
          spacing=3, salt=35, limit=10)


def build(w):
    stamping_hall(w)
    keep_upstairs(w)
    tavern_upstairs(w)
    fletcher(w)
    saddlery(w)
    stables(w)
    guard_house(w)
    powder_house(w)
    inner_barbican(w)
    alloy_room(w)
    sacristy(w)
    orb_cabinet(w)
    armoury_and_barracks(w)
    hall_of_banners(w)
    workshop(w)
    deep_forge(w)
    vault_and_soul_forge(w)
