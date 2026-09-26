"""The lower ward's open-air works: what stands between the two walls that is not a house.

The raiders have held this ward for years, so everything here is half of what it was: the stand at
the lists has lost its awning, a market stall has fallen in, the orchard has gone wild. Coordinates are
the plan's (docs/castillo/plano_v3_completo.png); y = 0 is the first air over the ground.
"""
import math

from castillo import _hash, _smooth, disc, noise_origin, slab, stairs

LIT_FIRE = {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"}
DEAD_FIRE = {"facing": "north", "lit": "false", "signal_fire": "false", "waterlogged": "false"}


def _floor(w, x0, z0, x1, z1, chooser):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            block = chooser(x, z)
            if block:
                w.put(x, -1, z, block)


def _clear(w, x0, z0, x1, z1, height=6):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(0, height):
                w.air(x, y, z)


def lists(w):
    """The tilt yard: a sanded run with a barrier down the middle, a quintain at each end, and a stand for whoever watched."""
    x0, z0, x1, z1 = 8, 138, 84, 152
    _clear(w, x0, z0, x1, z1, 8)
    _floor(w, x0, z0 + 4, x1, z1, lambda x, z: "sand" if _smooth(x, 0, z, 5.0, 100) > 0.35 else "coarse_dirt")
    for x in range(x0 + 6, x1 - 5):
        w.put(x, 0, 147, "dark_oak_fence")
        if (x - x0) % 6 == 0:
            w.put(x, 1, 147, "dark_oak_fence")
            w.put(x, 2, 147, "black_carpet" if (x // 6) % 2 else "orange_carpet")
    for x in (x0 + 2, x1 - 2):                                  # quintains: a post, an arm, a target
        for y in range(0, 3):
            w.put(x, y, 150, "dark_oak_fence")
        w.put(x, 3, 150, "target")
        w.put(x, 0, 151, "hay_block", {"axis": "y"})
    # the stand along the north side: three tiers of benches under what is left of a striped awning
    for x in range(x0 + 10, x1 - 9):
        for tier in range(3):
            w.put(x, tier, z0 + 3 - tier, *stairs("dark_oak_stairs", "north"))
            for y in range(0, tier):
                w.put(x, y, z0 + 3 - tier, "dark_oak_planks")
        if (x - x0) % 8 == 2:
            for y in range(0, 6):
                w.put(x, y, z0, "dark_oak_log", {"axis": "y"})
            for y in range(0, 5):
                w.put(x, y, z0 + 4, "dark_oak_fence")
        # the beam the awning is nailed to: without it what is left of the cloth between two posts hangs on nothing
        w.put(x, 6, z0, "dark_oak_log", {"axis": "x"})
        if _smooth(x, 6, z0, 7.0, 101) > 0.42:
            for k in range(5):
                w.put(x, 6 - (k // 2), z0 + k, "black_wool" if (x // 3) % 2 else "orange_wool")


def archery(w):
    """The butts: three lanes, targets on bales at the far end, a firing step and arrow barrels at the near one."""
    x0, z0, x1, z1 = 8, 156, 60, 168
    _clear(w, x0, z0, x1, z1, 7)
    _floor(w, x0, z0, x1, z1, lambda x, z: "coarse_dirt" if _hash(x, 0, z, 102) < 0.6 else "grass_block")
    for lane, z in enumerate((158, 162, 166)):
        w.put(x0 + 1, 0, z, "hay_block", {"axis": "x"})
        w.put(x0 + 1, 1, z, "target")
        w.put(x0 + 2, 0, z, "hay_block", {"axis": "x"})
        if lane == 1:
            w.put(x0 + 1, 2, z, "hay_block", {"axis": "x"})
        for x in range(x0 + 6, x1 - 4, 1):
            if z != 166:
                w.put(x, 0, z + 2, "spruce_fence") if x % 3 else None
        w.put(x1 - 2, 0, z, *slab("spruce_slab"))
        w.put(x1 - 1, 0, z + 1, "barrel", {"facing": "up", "open": "false"})


def quarry(w):
    """The mason's pit: terraces cut into the ground, a crane over them, and the cut stone of every family stacked along the rim."""
    x0, z0, x1, z1 = 8, 14, 36, 50
    _clear(w, x0, z0, x1, z1, 10)
    for x in range(x0 + 2, x1 - 1):
        for z in range(z0 + 8, z1 - 1):
            ring = min(x - (x0 + 2), (x1 - 2) - x, z - (z0 + 8), (z1 - 2) - z)
            depth = min(5, ring // 2)
            for y in range(-1, -1 - depth, -1):
                w.air(x, y, z)
            grain = _hash(x, depth, z, 103)
            layer = ("stone", "andesite", "tuff", "deepslate", "deepslate", "deepslate")[depth]
            w.put(x, -1 - depth, z, layer if grain < 0.8 else "cobblestone" if depth < 2 else "cobbled_deepslate")
    # the yard along the north rim: a pile of each stone the chisel works, rough beside dressed beside carved
    families = (("stone", "stone_bricks", "chiseled_stone_bricks"), ("andesite", "polished_andesite", "polished_andesite"),
                ("tuff", "tuff_bricks", "chiseled_tuff_bricks"), ("cobbled_deepslate", "deepslate_bricks", "chiseled_deepslate"),
                ("blackstone", "polished_blackstone_bricks", "chiseled_polished_blackstone"), ("basalt", "smooth_basalt", "polished_basalt"))
    for k, family in enumerate(families):
        px = x0 + 2 + k * 4
        for i, block in enumerate(family):
            for y in range(0, 3 - i):
                w.put(px + i, y, z0 + 2, block)
                if y == 0:
                    w.put(px + i, y, z0 + 3, block)
    # the crane: a mast, a jib, a chain, and the block it never finished lifting
    mx, mz = x0 + 14, z0 + 8
    for y in range(0, 9):
        w.put(mx, y, mz, "dark_oak_log", {"axis": "y"})
    for k in range(1, 8):
        w.put(mx, 8, mz + k, "dark_oak_log", {"axis": "z"})
    w.put(mx, 7, mz + 1, "dark_oak_fence")
    for y in range(3, 8):
        w.put(mx, y, mz + 7, "iron_chain", {"axis": "y", "waterlogged": "false"})
    w.put(mx, 2, mz + 7, "chiseled_deepslate")


def sawmill(w):
    """The woodyard: an open shed on posts, the saw bench under it, and logs stacked to dry."""
    x0, z0, x1, z1 = 8, 54, 36, 88
    _clear(w, x0, z0, x1, z1, 8)
    for x in range(x0 + 2, x1 - 1):
        for z in range(z0 + 2, z0 + 14):
            post = (x in (x0 + 2, x1 - 2, (x0 + x1) // 2)) and (z in (z0 + 2, z0 + 13))
            if post:
                for y in range(0, 5):
                    w.put(x, y, z, "spruce_log", {"axis": "y"})
            if _smooth(x, 5, z, 5.0, 104) > 0.3:
                w.put(x, 5, z, *slab("spruce_slab"))
    w.put(x0 + 8, 0, z0 + 7, "stonecutter", {"facing": "south"})
    for k in range(4):
        w.put(x0 + 10 + k, 0, z0 + 7, "stripped_spruce_log", {"axis": "x"})
    for pile, (px, pz, wood) in enumerate(((x0 + 4, z0 + 20, "dark_oak_log"), (x0 + 14, z0 + 22, "spruce_log"), (x0 + 5, z0 + 28, "dark_oak_log"))):
        for row in range(3):
            for k in range(3 - row):
                for length in range(6):
                    w.put(px + length, row, pz + k + row * 0 + (row // 2), wood, {"axis": "x"})


def orchard(w):
    """The kitchen garden gone wild, and the hives along its wall: honey is one of the four quenches."""
    x0, z0, x1, z1 = 14, 92, 32, 132
    _clear(w, x0, z0, x1, z1, 6)
    for z in range(z0 + 2, z1 - 8, 4):
        for x in range(x0 + 2, x1 - 1):
            tended = _smooth(x, 0, z, 6.0, 105) > 0.45
            w.put(x, -1, z, "farmland", {"moisture": "7"}) if tended else w.put(x, -1, z, "coarse_dirt")
            if tended:
                crop = ("wheat", "beetroots", "carrots", "potatoes")[(z // 4) % 4]
                w.put(x, 0, z, crop, {"age": "3" if crop == "beetroots" else "7"})
            elif _hash(x, 0, z, 106) < 0.3:
                w.put(x, 0, z, "dead_bush")
            w.put(x, -1, z + 1, "water", {"level": "0"}) if x % 5 == 0 else None
    for k in range(4):
        hx = x0 + 3 + k * 4
        w.put(hx, 0, z1 - 3, "spruce_fence")
        w.put(hx, 1, z1 - 3, "beehive", {"facing": "north", "honey_level": "0"})
        w.put(hx + 1, 0, z1 - 4, ("allium", "azure_bluet", "cornflower", "oxeye_daisy")[k])
    w.put(x0 + 1, 0, z1 - 6, "composter", {"level": "3"})


def graveyard(w):
    """Where the guild buried its smiths: a railed yard, rows of graves each under its own anvil, a dead tree, a tomb."""
    x0, z0, x1, z1 = 165, 14, 193, 50
    _clear(w, x0, z0, x1, z1, 9)
    _floor(w, x0, z0, x1, z1, lambda x, z: "podzol" if _smooth(x, 0, z, 6.0, 107) > 0.45 else "coarse_dirt" if _hash(x, 0, z, 108) < 0.7 else "rooted_dirt")
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if not (z == z1 and abs(x - (x0 + x1) // 2) < 2):
                w.put(x, 0, z, "cobbled_deepslate_wall")
                w.put(x, 1, z, "iron_bars") if _hash(x, 1, z, 109) < 0.85 else None
    for z in range(z0, z1 + 1):
        w.put(x0, 0, z, "cobbled_deepslate_wall")
        w.put(x0, 1, z, "iron_bars") if _hash(x0, 1, z, 109) < 0.85 else None
    for row, z in enumerate(range(z0 + 5, z1 - 8, 6)):
        for col, x in enumerate(range(x0 + 4, x1 - 3, 5)):
            kind = int(_hash(x, 0, z, 110) * 4)
            w.put(x, -1, z + 1, "rooted_dirt")
            w.put(x, -1, z + 2, "coarse_dirt")
            if kind == 0:
                w.put(x, 0, z, "polished_deepslate")
                w.put(x, 1, z, "damaged_anvil" if _hash(x, 1, z, 111) < 0.6 else "chipped_anvil", {"facing": "south"})
            elif kind == 1:
                w.put(x, 0, z, "chiseled_deepslate")
                w.put(x, 1, z, *slab("deepslate_brick_slab"))
            elif kind == 2:
                w.put(x, 0, z, "deepslate_brick_wall")
                w.put(x, 1, z, "deepslate_brick_wall")
            else:
                w.put(x, 0, z, *stairs("cobbled_deepslate_stairs", "south"))
            if _hash(x, 2, z, 112) < 0.18:
                w.put(x + 1, 0, z, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
            elif _hash(x, 2, z, 112) < 0.4:
                w.put(x + 1, 0, z + 1, "dead_bush")
    # the dead tree
    tx, tz = x0 + 8, z1 - 5
    for y in range(0, 7):
        w.put(tx, y, tz, "dark_oak_log", {"axis": "y"})
    for dx, dz, y, axis in ((1, 0, 4, "x"), (2, 0, 5, "x"), (-1, 0, 5, "x"), (0, 1, 3, "z"), (0, -1, 6, "z"), (0, -2, 6, "z")):
        w.put(tx + dx, y, tz + dz, "dark_oak_log", {"axis": axis})
    # the tomb, whose stair goes down to the ossuary
    mx, mz = x1 - 9, z0 + 3
    for x in range(mx, mx + 7):
        for z in range(mz, mz + 7):
            edge = x in (mx, mx + 6) or z in (mz, mz + 6)
            for y in range(0, 5):
                if edge:
                    w.put(x, y, z, "polished_blackstone_bricks" if _hash(x, y, z, 113) < 0.8 else "cracked_polished_blackstone_bricks")
                else:
                    w.air(x, y, z)
            w.put(x, 5, z, *slab("polished_blackstone_brick_slab")) if edge else w.put(x, 5, z, "polished_blackstone")
    for y in range(0, 3):
        w.air(mx + 3, y, mz + 6)
    w.put(mx + 3, 1, mz + 3, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
    w.put(mx + 3, 0, mz + 3, "chiseled_polished_blackstone")


def icehouse(w):
    """A low dome sunk in the ground with the winter's snow packed inside it: snow is one of the four quenches."""
    cx, cz, r = 179, 63, 6
    for (dx, dz), depth in disc(r + 0.4).items():
        rise = int(round(math.sqrt(max(0.0, (r + 0.4) ** 2 - dx * dx - dz * dz)) * 0.7))
        for y in range(-3, rise + 1):
            shell = y == rise or depth < 1.2
            if shell:
                w.put(cx + dx, y, cz + dz, "cobbled_deepslate" if _hash(cx + dx, y, cz + dz, 114) < 0.6 else "deepslate_bricks")
            elif y < 0:
                w.put(cx + dx, y, cz + dz, "packed_ice" if _hash(cx + dx, y, cz + dz, 115) < 0.5 else "snow_block")
            else:
                w.air(cx + dx, y, cz + dz)
        w.put(cx + dx, -4, cz + dz, "cobbled_deepslate")
    for y in (0, 1):
        for d in (0, 1):
            w.air(cx - r + d, y, cz)
    w.put(cx - r - 1, -1, cz, *stairs("cobbled_deepslate_stairs", "east"))


def camp(w):
    """The raiders' camp, hard by the breach they came in through: three tents round a fire, a palisade across the gap."""
    x0, z0, x1, z1 = 169, 112, 193, 134
    _clear(w, x0, z0, x1, z1, 7)
    fx, fz = (x0 + x1) // 2, (z0 + z1) // 2
    w.put(fx, -1, fz, "magma_block")
    w.put(fx, 0, fz, "campfire", LIT_FIRE)
    for dx, dz, facing in ((-2, 0, "east"), (2, 0, "west"), (0, -2, "south"), (0, 2, "north")):
        w.put(fx + dx, 0, fz + dz, *stairs("spruce_stairs", facing))
    for tx, tz, colour in ((x0 + 3, z0 + 3, "gray_wool"), (x0 + 3, z1 - 8, "black_wool"), (x1 - 9, z0 + 4, "red_wool")):
        for k in range(6):
            for rise in range(3):
                w.put(tx + rise, rise, tz + k, colour)
                w.put(tx + 5 - rise, rise, tz + k, colour)
            w.put(tx + 2, 0, tz + k, "air")
            w.put(tx + 3, 0, tz + k, "air")
            w.put(tx + 2, 1, tz + k, "air")
            w.put(tx + 3, 1, tz + k, "air")
        w.put(tx + 2, 0, tz + 4, "red_bed", {"facing": "north", "part": "head", "occupied": "false"}) if False else None
        w.put(tx + 3, 0, tz + 1, "barrel", {"facing": "up", "open": "false"})
    for z in range(114, 132):                                   # stakes across the breach
        if abs(z - 123) > 1:
            height = 3 + int(_hash(196, 0, z, 116) * 2)
            for y in range(0, height):
                w.put(196, y, z, "spruce_log", {"axis": "y"})
            w.put(196, height, z, "spruce_fence")


def market(w):
    """The guild's market street: six stalls under striped awnings, two of them down."""
    z0 = 174
    for k in range(6):
        sx = 119 + k * 12
        fallen = k in (2, 5)
        for px in (sx, sx + 7):
            for pz in (z0, z0 + 4):
                for y in range(0, 1 if fallen and px == sx else 3):
                    w.put(px, y, pz, "dark_oak_fence")
        for x in range(sx + 1, sx + 7):
            w.put(x, 0, z0, "barrel", {"facing": "up", "open": "false"}) if x % 3 == 0 else w.put(x, 0, z0, *slab("dark_oak_slab", top=True))
        if not fallen:
            for x in range(sx - 1, sx + 9):
                stripe = "orange_wool" if (x - sx) % 4 < 2 else "black_wool"
                for k2 in range(6):
                    w.put(x, 3 + (0 if k2 < 3 else 1) - 1 + 1, z0 - 1 + k2, stripe) if _smooth(x, 3, z0 + k2, 4.0, 117) > 0.25 else None
        else:
            for x in range(sx, sx + 8):
                if _hash(x, 0, z0 + 2, 118) < 0.5:
                    w.put(x, 0, z0 + 2, "orange_carpet" if x % 2 else "black_carpet")


def yard_furniture(w):
    """In the upper ward: the monument to the dead forge, and the well."""
    cx, cz = 100, 90
    for (dx, dz), depth in disc(6.4).items():
        w.put(cx + dx, -1, cz + dz, "polished_blackstone" if depth > 1 else "polished_blackstone_bricks")
        if depth > 1.4:
            w.put(cx + dx, 0, cz + dz, "polished_blackstone_bricks" if depth > 2.6 else "air")
            if 1.4 < depth <= 2.6:
                angle = math.degrees(math.atan2(dz, dx)) % 360
                facing = "west" if 45 <= angle < 135 and False else None
                w.put(cx + dx, 0, cz + dz, *slab("polished_blackstone_brick_slab"))
    # an anvil the size of a cart, cast in blackstone: foot, waist, face, horn
    for dx in range(-2, 3):
        for dz in range(-1, 2):
            w.put(cx + dx, 1, cz + dz, "polished_blackstone")
    for dx in range(-1, 2):
        w.put(cx + dx, 2, cz, "chiseled_polished_blackstone")
    for dx in range(-3, 4):
        for dz in range(-1, 2):
            w.put(cx + dx, 3, cz + dz, "polished_blackstone_bricks")
    w.put(cx + 4, 3, cz, *stairs("polished_blackstone_brick_stairs", "west", top=True))
    w.put(cx - 4, 3, cz, *slab("polished_blackstone_brick_slab", top=True))
    w.put(cx, 4, cz, "forja:fragua_apagada")
    for dx, dz in ((-5, -5), (5, -5), (-5, 5), (5, 5)):
        if abs(dx) + abs(dz) <= 10:
            w.put(cx + dx - (1 if dx > 0 else -1), 0, cz + dz - (1 if dz > 0 else -1), "polished_blackstone_brick_wall")
            w.put(cx + dx - (1 if dx > 0 else -1), 1, cz + dz - (1 if dz > 0 else -1), "soul_lantern", {"hanging": "false", "waterlogged": "false"})
    # the well
    wx, wz = 84, 96
    for (dx, dz), depth in disc(2.4).items():
        if depth < 1.0:
            w.put(wx + dx, 0, wz + dz, "deepslate_bricks")
            w.put(wx + dx, -1, wz + dz, "deepslate_bricks")
        else:
            for y in range(-3, 0):
                w.put(wx + dx, y, wz + dz, "water", {"level": "0"})
            w.put(wx + dx, -4, wz + dz, "cobbled_deepslate")
    for px in (wx - 2, wx + 2):
        for y in range(1, 4):
            w.put(px, y, wz, "dark_oak_fence")
    for x in range(wx - 2, wx + 3):
        w.put(x, 4, wz, *slab("deepslate_tile_slab"))
    w.put(wx, 3, wz, "iron_chain", {"axis": "y", "waterlogged": "false"})
    w.put(wx, 2, wz, "cauldron")


def build(w):
    for works in (lists, archery, quarry, sawmill, orchard, graveyard, icehouse, camp, market, yard_furniture):
        noise_origin()
        works(w)
