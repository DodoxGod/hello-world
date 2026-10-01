"""The towers inside: a spiral stair round a newel in each, and every floor a room of its own.

Before this, every one of the eleven towers was a stack of solid plank discs with nothing between them and no
way from one to the next. The ground floor and the wall-walk floor had doors; the rest were sealed, and the
audit counted nought to two things in each. A tower is the one place a castle stacks rooms, so they get the
thing a real one has - a stair of stone steps winding round a central pillar, two wide, up through a hole in
every floor - and each floor is fitted out for what the tower is named after: the Copper tower works copper
and has rust in its top room, the Echo tower listens, the Archive keeps paper, the Bellows tower is the
forge's lungs. The Sunken tower has lost its roof and is what is left of all that.

The stair goes round the newel, not round the wall: the doors are in the wall, and a stair against it would
cross in front of every one of them. Round the middle it leaves a ring of floor along the wall for the room.

Furniture only - light is castillo_luz's business, which comes after and lights what it finds.
"""
import math
import zlib

from castillo import HORIZONTAL, _hash, disc, slab, stairs
from castillo_interiores import CHAIN, FIRE, banner, barrel, chest, mob
from castillo_salas import CANDLE, bed, shelf, stand

LOOT = "forja:chests/bastion_torre"
LOOT_RARE = "forja:chests/bastion_torre_cima"

# The newel's inner lane, a square ring two out from the middle, in walking order (up = round this way).
LANE = sorted([(dx, dz) for dx in range(-2, 3) for dz in range(-2, 3) if max(abs(dx), abs(dz)) == 2],
              key=lambda c: math.atan2(c[1], c[0]))
WELL = 3           # the stair's footprint, Chebyshev from the middle; the room starts beyond it
STEP = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}


def _outward(ax, az):
    """The outer lane's cells beside an inner-lane cell: the stair is two wide, three at a corner."""
    sx = (ax > 0) - (ax < 0)
    sz = (az > 0) - (az < 0)
    if abs(ax) == 2 and abs(az) == 2:
        return [(ax + sx, az), (ax, az + sz), (ax + sx, az + sz)]
    if abs(ax) == 2:
        return [(ax + sx, az)]
    return [(ax, az + sz)]


def newel_stair(w, cx, cz, base, floors, top, start, ragged=0.0):
    """A stair round a 3x3 newel from the ground to the highest floor, cutting its way through every floor.

    Step k stands at base + 1 + k, so the step that reaches a floor's height takes the place of that floor's
    plank and you walk off it onto the floor; the floor is then opened over the steps below it for headroom.
    """
    levels = sorted(base + f for f in floors)
    rise = levels[-1] - base
    steps = {}
    for k in range(rise):
        ax, az = LANE[(start + k) % len(LANE)]
        bx, bz = LANE[(start + k + 1) % len(LANE)]
        facing = STEP[(bx - ax, bz - az)]
        y = base + 1 + k
        # A broken stair in a broken tower: a slab where a step fell, never a gap you cannot climb.
        broken = ragged and y > base + 12 and _hash(cx + ax, y, cz + az, 71) < ragged * 0.35
        for x, z in [(ax, az)] + _outward(ax, az):
            steps[(cx + x, y, cz + z)] = slab("cobbled_deepslate_slab") if broken else stairs("deepslate_brick_stairs", facing)
    for (x, y, z), state in steps.items():
        w.put(x, y, z, *state)
        # solid under the step down to the floor it climbs from, so the stair is a stone spiral and not stilts
        below = max(level for level in levels if level < y) if any(level < y for level in levels) else base
        for yy in range(below + 1, y):
            if (x, yy, z) not in steps:
                w.put(x, yy, z, "deepslate_bricks" if _hash(x, yy, z, 72) < 0.8 else "cracked_deepslate_bricks")
    for (x, y, z) in steps:
        for yy in range(y + 1, y + 4):
            if (x, yy, z) not in steps and yy < top:
                w.air(x, yy, z)
    # the newel itself, banded with carved stone at every floor, up to the roof
    crown = top - 1 if not ragged else levels[-1] + 2
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            for y in range(base + 1, crown + 1):
                if dx == dz == 0:
                    w.put(cx, y, cz, "polished_basalt", {"axis": "y"})
                elif y in levels:
                    w.put(cx + dx, y, cz + dz, "chiseled_deepslate")
                else:
                    w.put(cx + dx, y, cz + dz, "deepslate_tiles" if _hash(cx + dx, y, cz + dz, 73) < 0.85 else "cracked_deepslate_tiles")


# ------------------------------------------------------------------------------------------ places along the wall

def _inward(dx, dz):
    """The way a thing against the wall faces: toward the middle of the room, on the nearer axis."""
    if abs(dx) >= abs(dz):
        return "west" if dx > 0 else "east"
    return "north" if dz > 0 else "south"


def _yaw(facing):
    return {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}[facing]


def _back(dx, dz):
    """The way to the wall behind a place: the opposite of the way it faces."""
    ix, iz = HORIZONTAL[_inward(dx, dz)]
    return -ix, -iz


def wall_places(w, cx, cz, radius, y, doors_here, spacing=3, salt=0):
    """Free cells with stone behind them, a few apart, round the room, kept off this floor's doorways.

    Read off the built tower rather than worked out from its radius: the wall is not a clean circle everywhere
    (the ember windows under the crown are walled in with stone that stands proud into the top room), and a
    place worked out from geometry lands inside those lumps. Here a place is simply somewhere free with
    something solid at its back, which is what a piece of furniture needs.
    """
    inside = radius - 1.6
    kept = []
    for (dx, dz), depth in disc(inside).items():
        if max(abs(dx), abs(dz)) <= WELL + 1:
            continue
        if w.name(cx + dx, y, cz + dz) not in (None, "minecraft:air"):
            continue
        ox, oz = _back(dx, dz)
        if w.name(cx + dx + ox, y, cz + dz + oz) in (None, "minecraft:air"):
            continue
        blocked = False
        for side in doors_here:
            ux, uz = HORIZONTAL[side]
            along = dx * ux + dz * uz
            across = dx * -uz + dz * ux
            if along > 0 and -2.5 <= across <= 1.5:
                blocked = True
        if not blocked:
            kept.append((dx, dz))
    kept.sort(key=lambda c: math.atan2(c[1], c[0]))
    offset = int(_hash(salt, 0, 0, 74) * spacing)
    return kept[offset::spacing]


# ------------------------------------------------------------------------------------------ pieces
# Each piece stands at (x, y, z) against the wall, looking `facing` (into the room). y is the floor's walking level.

def p_barrels(w, x, y, z, facing, salt):
    barrel(w, x, y, z, LOOT if _hash(x, y, z, salt) < 0.3 else None)
    if _hash(x, y, z, salt + 1) < 0.6:
        barrel(w, x, y + 1, z)


def p_chest(w, x, y, z, facing, salt, loot=LOOT):
    chest(w, x, y, z, facing, loot)


def p_rare(w, x, y, z, facing, salt):
    chest(w, x, y, z, facing, LOOT_RARE)


def p_table(w, x, y, z, facing, salt):
    w.put(x, y, z, "dark_oak_fence")
    w.put(x, y + 1, z, "dark_oak_pressure_plate")
    dx, dz = HORIZONTAL[facing]
    w.put(x + dx, y, z + dz, *stairs("spruce_stairs", facing))
    if _hash(x, y, z, salt) < 0.5:
        w.put(x, y + 1, z, "candle", CANDLE)


def p_shelf(w, x, y, z, facing, salt, gone=0.15):
    for k in range(2):
        shelf(w, x, y + k, z, salt + k, gone)


def p_lectern(w, x, y, z, facing, salt):
    w.put(x, y, z, "lectern", {"facing": facing, "has_book": "false", "powered": "false"})


def p_bed(w, x, y, z, facing, salt):
    # head against the wall, foot toward the middle
    dx, dz = HORIZONTAL[facing]
    outward = {"north": "south", "south": "north", "east": "west", "west": "east"}[facing]
    bed(w, x + dx, y, z + dz, outward, "gray" if _hash(x, y, z, salt) < 0.5 else "brown")


def p_stand(w, x, y, z, facing, salt, pieces=None):
    stand(w, x, y, z, _yaw(facing), pieces or {"head": "chainmail_helmet", "chest": "iron_chestplate"})


def p_block(name, props=None, high=1):
    def place(w, x, y, z, facing, salt):
        for k in range(high):
            w.put(x, y + k, z, name, dict(props or {}))
    return place


def p_faced(name, props=None):
    def place(w, x, y, z, facing, salt):
        w.put(x, y, z, name, dict(props or {}, facing=facing))
    return place


def p_anvil(w, x, y, z, facing, salt):
    w.put(x, y, z, "chipped_anvil" if _hash(x, y, z, salt) < 0.5 else "anvil", {"facing": facing})


def p_forja(block):
    """One of the mod's own work blocks: they are what makes this a smith's castle and not anybody's."""
    def place(w, x, y, z, facing, salt):
        w.put(x, y, z, f"forja:{block}")
    return place


def p_brazier(w, x, y, z, facing, salt):
    w.put(x, y, z, "polished_blackstone_brick_wall")
    w.put(x, y + 1, z, "campfire", FIRE)


def p_cobweb(w, x, y, z, facing, salt):
    w.put(x, y + (1 if _hash(x, y, z, salt) < 0.5 else 0), z, "cobweb")


def p_rubble(w, x, y, z, facing, salt):
    w.put(x, y, z, "cobbled_deepslate" if _hash(x, y, z, salt) < 0.5 else "gravel")
    if _hash(x, y, z, salt + 1) < 0.4:
        w.put(x, y + 1, z, *slab("cobbled_deepslate_slab"))


def p_banner(colour="black"):
    def place(w, x, y, z, facing, salt):
        # a wall banner hangs on the wall it is placed against, facing out of it
        banner(w, x, y + 2, z, facing, colour=colour)
    return place


def p_copper_pile(w, x, y, z, facing, salt):
    pick = ("exposed_copper", "weathered_copper", "oxidized_copper", "copper_grate", "weathered_cut_copper")
    w.put(x, y, z, pick[int(_hash(x, y, z, salt) * len(pick))])
    if _hash(x, y, z, salt + 2) < 0.5:
        w.put(x, y + 1, z, *slab("oxidized_cut_copper_slab"))


def p_sculk(w, x, y, z, facing, salt):
    w.put(x, y, z, "sculk")
    roll = _hash(x, y, z, salt)
    if roll < 0.35:
        w.put(x, y + 1, z, "sculk_sensor", {"sculk_sensor_phase": "inactive"})
    elif roll < 0.55:
        w.put(x, y + 1, z, "amethyst_cluster", {"facing": "up"})


def p_note(w, x, y, z, facing, salt):
    w.put(x, y, z, "note_block")


def p_obsidian(w, x, y, z, facing, salt):
    w.put(x, y, z, "crying_obsidian" if _hash(x, y, z, salt) < 0.3 else "obsidian")
    if _hash(x, y, z, salt + 1) < 0.5:
        w.put(x, y + 1, z, "obsidian")


def p_magma_cage(w, x, y, z, facing, salt):
    # embers under a grate: a single iron bar on the block, joined to nothing, read as a stray post (Andy, 2026-09-29)
    w.put(x, y, z, "magma_block")
    w.put(x, y + 1, z, "copper_grate")


def p_resin(w, x, y, z, facing, salt):
    w.put(x, y, z, "resin_block" if _hash(x, y, z, salt) < 0.5 else "resin_bricks")
    if _hash(x, y, z, salt + 1) < 0.5:
        w.put(x, y + 1, z, *stairs("resin_brick_stairs", facing))


def p_sand(w, x, y, z, facing, salt):
    w.put(x, y, z, "sand" if _hash(x, y, z, salt) < 0.6 else "red_sand")


def p_glass(w, x, y, z, facing, salt):
    pick = ("orange_stained_glass", "red_stained_glass", "tinted_glass", "glass")
    for k in range(2):
        w.put(x, y + k, z, pick[int(_hash(x, y + k, z, salt) * len(pick))])


def p_bellows(w, x, y, z, facing, salt):
    """A forge bellows: two boards and a leather belly, hinged at the nozzle end."""
    w.put(x, y, z, *slab("dark_oak_slab", top=True))
    w.put(x, y + 1, z, "brown_wool")
    w.put(x, y + 2, z, *slab("dark_oak_slab"))


def p_duct(w, x, y, z, facing, salt):
    w.put(x, y, z, "copper_grate")
    w.put(x, y + 1, z, "lightning_rod", {"facing": "up", "powered": "false"})


def p_hay(w, x, y, z, facing, salt):
    w.put(x, y, z, "hay_block", {"axis": "y"})


def p_ember_lamp(w, x, y, z, facing, salt):
    w.put(x, y, z, "polished_blackstone_brick_wall")
    w.put(x, y + 1, z, "forja:farol_de_pavesa")


def p_bell(w, x, y, z, facing, salt):
    w.put(x, y, z, "bell", {"attachment": "floor", "facing": facing})


PIECES = {
    "store": [p_barrels, p_chest, p_barrels, p_table, p_barrels, p_hay],
    "store_grain": [p_hay, p_barrels, p_hay, p_block("composter"), p_chest, p_hay],
    "store_tools": [p_barrels, p_block("grindstone", {"face": "floor", "facing": "north"}), p_table, p_chest, p_block("crafting_table")],
    "guard": [p_brazier, p_stand, p_barrels, p_table, p_chest, p_banner("black")],
    "guard_mess": [p_table, p_barrels, p_stand, p_table, p_banner("gray"), p_chest],
    "guard_watch": [p_stand, p_block("cartography_table"), p_brazier, p_chest, p_stand, p_barrels],
    "barracks": [p_bed, p_bed, p_chest, p_bed, p_table, p_stand],
    "copper_shop": [p_anvil, p_copper_pile, p_block("smithing_table"), p_copper_pile, p_forja("mesa_de_forja"), p_barrels],
    "rust_nest": [p_copper_pile, p_cobweb, p_copper_pile, p_rare, p_copper_pile, p_cobweb],
    "listening": [p_sculk, p_note, p_sculk, p_lectern, p_sculk, p_note],
    "echo_top": [p_sculk, p_block("amethyst_block"), p_rare, p_sculk, p_block("amethyst_block"), p_sculk],
    "obsidian_cut": [p_obsidian, p_faced("stonecutter"), p_obsidian, p_block("grindstone", {"face": "floor", "facing": "north"}), p_obsidian],
    "crucible": [p_magma_cage, p_forja("crisol_de_obsidiana"), p_magma_cage, p_rare, p_magma_cage, p_anvil],
    "resin_store": [p_resin, p_barrels, p_resin, p_block("cauldron"), p_resin, p_chest],
    "resin_shop": [p_resin, p_block("brewing_stand"), p_block("cauldron"), p_rare, p_resin, p_table],
    "ruin": [p_rubble, p_cobweb, p_rubble, p_barrels, p_cobweb, p_rubble],
    "tannery": [p_stand, p_block("loom"), p_block("brown_wool"), p_barrels, p_stand, p_table],
    "scale_armoury": [p_stand, p_stand, p_rare, p_stand, p_anvil, p_banner("brown")],
    "glassworks": [p_faced("furnace", {"lit": "false"}), p_sand, p_faced("blast_furnace", {"lit": "false"}), p_glass, p_sand, p_barrels],
    "glass_gallery": [p_glass, p_lectern, p_glass, p_rare, p_glass, p_table],
    "archive": [p_shelf, p_shelf, p_lectern, p_shelf, p_shelf, p_table],
    "archive_top": [p_shelf, p_block("cartography_table"), p_shelf, p_rare, p_shelf, p_lectern],
    "ember_cages": [p_magma_cage, p_ember_lamp, p_magma_cage, p_barrels, p_ember_lamp, p_magma_cage],
    "wisp_roost": [p_brazier, p_ember_lamp, p_rare, p_brazier, p_magma_cage, p_ember_lamp],
    "bellows": [p_bellows, p_duct, p_bellows, p_barrels, p_bellows, p_duct],
    "ducts": [p_duct, p_duct, p_barrels, p_duct, p_anvil, p_duct],
    "lookout": [p_block("cartography_table"), p_bell, p_table, p_rare, p_bed, p_stand],
}

# Each tower, floor by floor from the ground. Outer towers have four floors (the third is the wall walk's);
# inner ones five (the fourth is the wall walk's). The wall walk's floor is always a guard post.
THEMES = {
    "t_cobre": ["store", "copper_shop", "guard", "rust_nest"],
    "t_eco": ["store", "listening", "guard", "echo_top"],
    "t_obsidiana": ["store", "obsidian_cut", "guard", "crucible"],
    "t_resina": ["store", "resin_store", "guard", "resin_shop"],
    "t_hundida": ["ruin", "ruin", "ruin", "ruin"],
    "t_escama": ["store", "tannery", "guard", "scale_armoury"],
    "t_vidrio": ["store", "glassworks", "guard", "glass_gallery"],
    "t_archivo": ["archive", "archive", "archive", "guard", "archive_top"],
    "t_pavesas": ["store", "ember_cages", "ember_cages", "guard", "wisp_roost"],
    "t_fuelle": ["store", "bellows", "ducts", "guard", "bellows"],
    "t_vigia": ["store", "barracks", "barracks", "guard", "lookout"],
}

# Who lives there, as the plan has it: rust in the copper and in the ruin, wisps in their own tower.
TENANTS = {
    "t_cobre": (3, "forja:herrumbre", 3),
    "t_hundida": (1, "forja:herrumbre", 4),
    "t_pavesas": (4, "forja:pavesa", 3),
}


# The colour each tower's rooms are dressed in: the runner round the stair and the banners on the wall.
COLOURS = {"t_cobre": "orange", "t_eco": "cyan", "t_obsidiana": "purple", "t_resina": "orange", "t_escama": "brown",
           "t_vidrio": "light_blue", "t_archivo": "red", "t_pavesas": "red", "t_fuelle": "gray", "t_vigia": "green"}


def dress_floor(w, key, cx, cz, radius, y, level, doors_here):
    """A runner of carpet round the stair's well, and the tower's colours hung on the wall above the furniture.
    Plank floor and bare stone were most of what the pictures showed; this is what makes it somebody's room."""
    colour = COLOURS.get(key)
    if colour is None:
        return
    ring = WELL + 1
    for dx in range(-ring, ring + 1):
        for dz in range(-ring, ring + 1):
            if max(abs(dx), abs(dz)) != ring:
                continue
            x, z = cx + dx, cz + dz
            if w.name(x, y, z) in (None, "minecraft:air") and w.name(x, y - 1, z) not in (None, "minecraft:air"):
                corner = abs(dx) == ring and abs(dz) == ring
                w.put(x, y, z, "black_carpet" if corner else f"{colour}_carpet")
    # banners three blocks up the wall, where nothing stands that high, kept off the doorways
    for index, (dx, dz) in enumerate(wall_places(w, cx, cz, radius, y + 3, doors_here, spacing=5, salt=level * 7 + 3)):
        banner(w, cx + dx, y + 3, cz + dz, _inward(dx, dz), colour="black" if index % 2 else colour)


# The rooms every tower has - a store on the ground floor and a guard post on the wall walk's - were furnished the same
# in all of them, piece for piece (Andy, 2026-09-29: "the rooms do not vary"). Each tower takes one of these.
VARIANTS = {"store": ("store", "store_grain", "store_tools"), "guard": ("guard", "guard_mess", "guard_watch")}


def furnish(w, key, cx, cz, radius, level, floor_y, doors_here):
    theme = THEMES[key][level]
    salt = zlib.crc32(f"{key}/{level}".encode()) & 0xFFFF
    if theme in VARIANTS:
        theme = VARIANTS[theme][salt % len(VARIANTS[theme])]
    pieces = PIECES[theme]
    turn = (salt >> 4) % len(pieces)                  # and the round of pieces starts somewhere else in each
    pieces = pieces[turn:] + pieces[:turn]
    y = floor_y + 1
    # the top room has no stair going on up through it, and is the smallest: closer together there
    top = level == len(THEMES[key]) - 1
    places = wall_places(w, cx, cz, radius, y, doors_here, spacing=2 if top or theme in ("archive", "archive_top") else 3,
                         salt=salt)
    for index, (dx, dz) in enumerate(places):
        x, z = cx + dx, cz + dz
        if w.name(x, y, z) in (None, "minecraft:air"):
            pieces[index % len(pieces)](w, x, y, z, _inward(dx, dz), 80 + index)
    if theme != "ruin":
        dress_floor(w, key, cx, cz, radius, y, level, doors_here)


def build(w):
    import castillo_obra

    for key, name, cx, cz, radius, base, height, floors, doors, ragged in castillo_obra.TOWER_SPECS:
        top = base + height - 1
        start = int(_hash(cx, 0, cz, 70) * len(LANE))
        newel_stair(w, cx, cz, base, floors, top, start, ragged)
        for level, floor in enumerate(floors):
            doors_here = [side for side, up in doors if up == floor]
            furnish(w, key, cx, cz, radius, level, base + floor, doors_here)
        if key in TENANTS:
            level, entity, count = TENANTS[key]
            floor_y = base + floors[level] + 1
            # on the walkway round the stair, which nothing is ever put on, so nothing spawns inside furniture
            walk = sorted([(dx, dz) for dx in range(-WELL - 1, WELL + 2) for dz in range(-WELL - 1, WELL + 2)
                           if max(abs(dx), abs(dz)) == WELL + 1], key=lambda c: math.atan2(c[1], c[0]))
            for k in range(count):
                dx, dz = walk[(k * len(walk)) // count]
                mob(w, cx + dx, floor_y, cz + dz, entity)
