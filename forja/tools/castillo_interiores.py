"""Inside the castle: what makes a shell a room. Furniture out of vanilla's own tricks, the mod's benches and
foundry where they belong, banners in the guild's colours, chests with their loot, and whoever lives there.

Plan coordinates (docs/castillo/plano_v3_completo.png); y = 0 is the ground floor.
"""
import math

from castillo import HORIZONTAL, OPPOSITE, _hash, _smooth, disc, masonry, noise_origin, slab, stairs

CHAIN = {"axis": "y", "waterlogged": "false"}
FIRE = {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"}
EMBER_BANNER = [{"pattern": "minecraft:gradient_up", "color": "orange"}, {"pattern": "minecraft:triangle_bottom", "color": "red"},
                {"pattern": "minecraft:border", "color": "black"}]
ANVIL_BANNER = [{"pattern": "minecraft:stripe_bottom", "color": "gray"}, {"pattern": "minecraft:stripe_middle", "color": "light_gray"},
                {"pattern": "minecraft:border", "color": "orange"}]


# ------------------------------------------------------------------------------------------ furniture

def banner(w, x, y, z, facing, patterns=None, colour="black"):
    w.put(x, y, z, f"{colour}_wall_banner", {"facing": facing}, {"id": "minecraft:banner", "patterns": patterns or EMBER_BANNER})


def chest(w, x, y, z, facing, loot):
    w.put(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"}, {"id": "minecraft:chest", "LootTable": loot})


def barrel(w, x, y, z, loot=None):
    w.put(x, y, z, "barrel", {"facing": "up", "open": "false"}, {"id": "minecraft:barrel", "LootTable": loot} if loot else None)


def lantern(w, x, y, z, drop=1, soul=False):
    for k in range(drop):
        w.put(x, y - k, z, "iron_chain", CHAIN)
    w.put(x, y - drop, z, "soul_lantern" if soul else "lantern", {"hanging": "true", "waterlogged": "false"})


def chandelier(w, x, y, z, drop=2, soul=False):
    """A ring of lanterns on a wheel of fences, hung on a chain: the hall's light."""
    for k in range(drop):
        w.put(x, y - k, z, "iron_chain", CHAIN)
    hub = y - drop
    w.put(x, hub, z, "dark_oak_fence")
    for dx, dz in HORIZONTAL.values():
        w.put(x + dx, hub, z + dz, "dark_oak_fence")
        w.put(x + dx * 2, hub, z + dz * 2, "dark_oak_fence")
        w.put(x + dx * 2, hub - 1, z + dz * 2, "soul_lantern" if soul else "lantern", {"hanging": "true", "waterlogged": "false"})


def long_table(w, x0, z, x1, y, seats=True):
    """A table down the hall: slabs on fence legs, benches of stairs either side."""
    for x in range(x0, x1 + 1):
        w.put(x, y, z, *slab("dark_oak_slab", top=True))
        if x in (x0, x1) or (x - x0) % 4 == 0:
            w.put(x, y, z, "dark_oak_fence")
            w.put(x, y + 1, z, "dark_oak_pressure_plate") if False else None
        if seats and (x - x0) % 2 == 0:
            w.put(x, y, z - 1, *stairs("spruce_stairs", "south"))
            w.put(x, y, z + 1, *stairs("spruce_stairs", "north"))
    for x in range(x0, x1 + 1):
        if (x - x0) % 4 != 0 and x not in (x0, x1):
            w.put(x, y, z, *slab("dark_oak_slab", top=True))


def brazier(w, x, y, z):
    w.put(x, y, z, "polished_blackstone_brick_wall")
    w.put(x, y + 1, z, "campfire", FIRE)


def rug(w, x0, z0, x1, z1, y, inner="red_carpet", border="black_carpet"):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if w.name(x, y, z) in (None, "minecraft:air"):
                w.put(x, y, z, border if edge else inner)


def pillar(w, x, z, y0, y1, keep=True):
    for y in range(y0, y1):
        w.put(x, y, z, "polished_basalt", {"axis": "y"})
    w.put(x, y0, z, "chiseled_polished_blackstone" if keep else "chiseled_deepslate")
    family = "polished_blackstone_brick" if keep else "deepslate_brick"
    for side, (dx, dz) in HORIZONTAL.items():
        if w.name(x + dx, y1 - 1, z + dz) in (None, "minecraft:air"):
            w.put(x + dx, y1 - 1, z + dz, *stairs(f"{family}_stairs", OPPOSITE[side], top=True))


def partition(w, x0, z0, x1, z1, y0, y1, keep=True, decay=0.05):
    stone = masonry(decay, keep)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1):
                w.put(x, y, z, stone(x, y, z, 0.5))


def opening(w, x0, z0, x1, z1, y0, y1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1):
                w.air(x, y, z)


def mob(w, x, y, z, entity, extra=None):
    nbt = {"id": entity, "PersistenceRequired": 1}
    if extra:
        nbt.update(extra)
    w.entities.append({"pos": (x + 0.5, float(y), z + 0.5), "nbt": nbt})


# ------------------------------------------------------------------------------------------ the great tower, ground floor

def keep_ground(w):
    """The Sala de cuños at the head, the great hall two storeys high before it, the stair of honour in the north-east corner."""
    noise_origin(72, 0, 17)
    # the wall between the hall and the stamping hall, and the side walls of the stamping hall
    partition(w, 75, 38, 126, 38, 0, 18)
    partition(w, 83, 20, 83, 37, 0, 9)
    partition(w, 118, 20, 118, 37, 0, 9)
    opening(w, 98, 38, 103, 38, 0, 6)                                # the way through, under the masters' gallery
    w.put(98, 5, 38, *stairs("polished_blackstone_brick_stairs", "east", top=True))
    w.put(103, 5, 38, *stairs("polished_blackstone_brick_stairs", "west", top=True))
    opening(w, 83, 27, 83, 29, 0, 4)
    opening(w, 118, 27, 118, 29, 0, 4)
    # the great hall: take the floor above it out, leave a gallery three wide round the walls
    for x in range(78, 124):
        for z in range(42, 66):
            w.air(x, 8, z)
    for x in range(75, 127):
        for z in range(39, 68):
            gallery = x < 78 or x > 123 or z < 42 or z > 65
            if gallery and w.name(x, 8, z) in (None, "minecraft:air"):
                w.put(x, 8, z, "dark_oak_planks")
    for x in range(78, 124):
        for z in (42, 65):
            w.put(x, 9, z, "dark_oak_fence")
    for z in range(42, 66):
        for x in (78, 123):
            w.put(x, 9, z, "dark_oak_fence")
    # its floor: blackstone in squares, a gilded line down the middle to the door of the stamping hall
    for x in range(75, 127):
        for z in range(39, 68):
            grain = _hash(x, 0, z, 130)
            block = "polished_blackstone" if (x // 3 + z // 3) % 2 else "polished_blackstone_bricks"
            if abs(x - 100.5) < 1:
                block = "gilded_blackstone" if z % 4 == 0 else "chiseled_polished_blackstone"
            w.put(x, -1, z, block if grain < 0.95 else "cracked_polished_blackstone_bricks")
    rug(w, 98, 40, 103, 66, 0, "red_carpet", "black_carpet")
    # two rows of pillars carrying the gallery, a banner of the guild on each, chandeliers between
    for z in range(44, 66, 7):
        for x in (84, 117):
            pillar(w, x, z, 0, 8)
            banner(w, x + (1 if x < 100 else -1), 5, z, "east" if x < 100 else "west")
    for z in (47, 54, 61):
        chandelier(w, 100, 17, z, 6)
    for z in (46, 60):
        long_table(w, 88, z, 96, 0)
        long_table(w, 105, z, 113, 0)
    # the hearth in the east wall, under the great chimney
    for z in range(50, 55):
        for y in range(0, 5):
            w.air(126, y, z)
            w.air(127, y, z)
        w.put(127, -1, z, "magma_block")
        w.put(127, 0, z, "campfire", FIRE)
        w.put(126, 0, z, "iron_bars")
    for z in (49, 55):
        for y in range(0, 6):
            w.put(125, y, z, "polished_basalt", {"axis": "y"})
    for z in range(49, 56):
        w.put(125, 6, z, "chiseled_polished_blackstone")
    # the stamping hall: the three seals, the guardian and his two, and what they guard
    for x in range(84, 118):
        for z in range(20, 38):
            w.put(x, -1, z, "polished_deepslate" if (x + z) % 2 else "deepslate_tiles")
    for x in range(95, 106):
        for z in range(24, 33):
            w.put(x, -1, z, "chiseled_deepslate" if (x + z) % 2 else "gilded_blackstone" if (x, z) == (100, 28) else "polished_blackstone")
    for sx, sz in ((90, 24), (111, 24), (100, 34)):
        for y in range(0, 3):
            w.put(sx, y, sz, "polished_deepslate")
        w.put(sx, 3, sz, "forja:farol_de_pavesa")
        for dx, dz in HORIZONTAL.values():
            w.put(sx + dx, 0, sz + dz, *slab("deepslate_brick_slab"))
    chest(w, 100, 0, 21, "south", "forja:chests/castillo_de_forja")
    chest(w, 98, 0, 21, "south", "forja:chests/castillo_de_forja")
    w.put(96, 0, 21, "forja:yunque_del_herrero", {"facing": "south"})
    w.put(104, 0, 21, "forja:mesa_de_forja", {"facing": "south"})
    for x in (88, 94, 106, 112):
        banner(w, x, 4, 20, "south", ANVIL_BANNER, "gray")
    for x in (92, 108):
        lantern(w, x, 7, 30, 1)
    mob(w, 100, 0, 28, "forja:guardian_de_cuno")
    mob(w, 93, 0, 31, "forja:tenaza")
    mob(w, 107, 0, 31, "forja:percutor")
    # the stair of honour: a broad flight up the north-east corner, one storey
    for k in range(9):
        for x in range(120, 126):
            w.put(x, k, 21 + k, *stairs("polished_blackstone_brick_stairs", "south"))
            for y in range(k + 1, k + 6):
                w.air(x, y, 21 + k)
            for y in range(0, k):
                w.put(x, y, 21 + k, "polished_blackstone_bricks")
    for x in range(120, 126):
        for z in range(30, 37):
            w.air(x, 8, z) if False else None
    noise_origin()


# ------------------------------------------------------------------------------------------ the foundry and the workshop

def foundry(w):
    """The mod's whole line at the size of a hall: an obsidian crucible, a bank of tanks, channels across
    the floor to three casting tables, spouts from a gantry, coal by the wall; and the automatons that keep it lit."""
    x0, z0, x1, z1 = 133, 58, 151, 96
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            grain = _hash(x, 0, z, 131)
            w.put(x, -1, z, "polished_blackstone_bricks" if grain < 0.8 else "blackstone" if grain < 0.92 else "magma_block")
    # the bank of tanks along the east wall, a lit pavesa lantern under the first to keep them liquid
    for z in range(62, 74, 2):
        for y in (0, 1):
            w.put(150, y, z, "forja:cuba_de_colada")
    w.put(150, 0, 61, "forja:farol_de_pavesa")
    # the crucible on its hearth, fed from above
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            w.put(146 + dx, 0, 80 + dz, "polished_blackstone_bricks")
    w.put(146, 0, 80, "forja:farol_de_pavesa")
    w.put(146, 1, 80, "forja:crisol_de_obsidiana")
    w.put(146, 2, 80, "hopper", {"facing": "down", "enabled": "true"})
    # channels down the middle of the floor, from the tanks to three casting tables
    for z in range(62, 92):
        w.put(142, -1, z, "forja:conducto_de_damasco")
    for x in range(143, 150):
        w.put(x, -1, 66, "forja:conducto_de_damasco")
    for k, z in enumerate((70, 78, 86)):
        for x in range(137, 142):
            w.put(x, -1, z, "forja:conducto_de_acero")
        w.put(136, -1, z, "polished_blackstone")
        w.put(136, 0, z, ("forja:mesa_de_losa", "forja:mesa_de_brasa", "forja:mesa_de_almas")[k], {"lit": "false"})
    for z in (74, 82):
        w.put(136, 0, z, "forja:caja_de_moldeo" if z == 74 else "forja:caja_de_moldeo_de_acero")
    # the gantry: a walk of grates on posts along the west side at the first storey, with a spout over each table
    for z in range(62, 92):
        w.put(138, 8, z, "waxed_oxidized_copper_grate")
        w.put(139, 8, z, "waxed_oxidized_copper_grate")
        if z % 8 == 6:
            # what the walk stands on: a post under its inner edge, and a chain up to the roof over the outer one
            for y in range(0, 8):
                w.put(139, y, z, "polished_blackstone_brick_wall")
            w.put(138, 9, z, "iron_chain", CHAIN)
    for z in (70, 78, 86):
        w.put(137, 8, z, "forja:cano_de_colada")
    # coal against the north wall, barrels of ingots by the door, light from cages of fire
    for x in range(144, 151):
        for y in range(0, 3 - abs(x - 147) // 2):
            w.put(x, y, 59, "coal_block")
    for z in (60, 94):
        brazier(w, 134, 0, z)
    barrel(w, 134, 0, 90, "forja:chests/castillo_de_forja")
    barrel(w, 134, 0, 91)
    barrel(w, 134, 1, 90)
    for z in (66, 76, 86):
        lantern(w, 146, 16, z, 5)
        banner(w, 151, 10, z + 3, "west")
    mob(w, 144, 0, 72, "forja:automata_de_forja")
    mob(w, 140, 0, 88, "forja:automata_de_forja")
    mob(w, 147, 0, 90, "forja:escoria_viviente")


def workshop(w):
    """The master's workshop: every bench of the mod within reach of the others (a whole workshop), the anvil, the cabinets."""
    benches = (("forja:mesa_de_piezas", 135, 103), ("forja:mesa_de_forja_mayor", 139, 103), ("forja:mesa_de_talabarteria", 143, 103),
               ("forja:mesa_de_extraccion", 147, 103), ("forja:yunque_del_herrero", 141, 108))
    for block, x, z in benches:
        w.put(x, 0, z, block, {"facing": "south"} if "yunque" in block or "mesa_de_forja" in block or "mesa_de_p" in block or "talab" in block else None)
    w.put(139, -1, 103, "magma_block")                                 # what is under a forge table decides its heat
    for z in (109, 110):
        w.put(150, 0, z, "forja:armario_de_piezas", {"facing": "west"})
        w.put(150, 1, z, "forja:armario_de_piezas", {"facing": "west"})
    chest(w, 134, 0, 110, "east", "forja:chests/castillo_de_forja")
    w.put(134, 0, 108, "grindstone", {"face": "floor", "facing": "east"})
    w.put(134, 0, 106, "smithing_table")
    for x in (137, 145):
        lantern(w, x, 7, 106, 1)
    banner(w, 141, 5, 102, "south", ANVIL_BANNER, "gray")


def build(w):
    keep_ground(w)
    foundry(w)
    workshop(w)
    import castillo_salas
    castillo_salas.build(w)
