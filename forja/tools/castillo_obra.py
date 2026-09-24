"""El Bastión del Gremio: the works. Where everything stands is docs/castillo/plano_v3_completo.png.

x runs east, z runs south (the gate looks south), y = 0 is the first air over the courtyard paving.
"""
import math

from castillo import (COPPER_ROOF, Canvas, HORIZONTAL, _hash, _smooth, blit, building, curtain_wall, disc, masonry, noise_origin, round_tower,
                      slab, stairs)

OUTER = (0, 0, 201, 201)            # the outer curtain, outer faces
INNER = (42, 12, 159, 124)          # the inner curtain
OUTER_H, OUTER_T = 14, 6
INNER_H, INNER_T = 18, 5
GATE_OUT = (84, 184, 117, 209)      # the outer barbican, x0 z0 x1 z1
GATE_IN = (90, 114, 111, 134)
BRIDGE = (95, 210, 106, 236)
MOAT_IN, MOAT_OUT = 3, 13           # how far from the outer face the ditch starts and ends


# ------------------------------------------------------------------------------------------ ground

def inside(box, x, z, grow=0):
    return box[0] - grow <= x <= box[2] + grow and box[1] - grow <= z <= box[3] + grow


def ground(w):
    """What is underfoot: paving in the upper ward, a road between the gates, and the lower ward gone to earth."""
    for x in range(OUTER[0], OUTER[2] + 1):
        for z in range(OUTER[1], OUTER[3] + 1):
            upper = inside(INNER, x, z)
            road = 94 <= x <= 107 and z > INNER[3]
            if upper:
                grain = _hash(x, 0, z, 40)
                if (x % 6 == 0 or z % 6 == 0):
                    block = "polished_deepslate" if grain < 0.85 else "cracked_deepslate_tiles"
                else:
                    block = "deepslate_tiles" if grain < 0.78 else "cracked_deepslate_tiles" if grain < 0.9 else "cobbled_deepslate"
            elif road:
                grain = _hash(x, 0, z, 41)
                edge = x in (94, 107)
                block = "polished_deepslate" if edge and grain < 0.7 else "cobbled_deepslate" if grain < 0.6 else "gravel" if grain < 0.8 else "deepslate_bricks"
            else:
                patch = _smooth(x, 0, z, 9.0, 42)
                grain = _hash(x, 0, z, 43)
                if patch < 0.32:
                    block = "coarse_dirt" if grain < 0.7 else "gravel"
                elif patch < 0.5:
                    block = "podzol" if grain < 0.6 else "rooted_dirt"
                elif patch < 0.72:
                    block = "grass_block" if grain < 0.75 else "coarse_dirt"
                else:
                    block = "coarse_dirt" if grain < 0.5 else "cobbled_deepslate" if grain < 0.7 else "gravel"
            w.put(x, -1, z, block)
            w.put(x, -2, z, "dirt" if not upper and not road else "cobbled_deepslate")
            w.put(x, -3, z, "stone")
            if upper or road:
                for y in range(0, 9):
                    w.air(x, y, z)


def moat(w):
    """A ditch of slag round the whole place: basalt and blackstone, magma in veins, a few pools of lava kept well off the far bank."""
    x0, z0, x1, z1 = OUTER
    for x in range(x0 - MOAT_OUT - 2, x1 + MOAT_OUT + 3):
        for z in range(z0 - MOAT_OUT - 2, z1 + MOAT_OUT + 3):
            if inside(OUTER, x, z):
                continue
            away = max(x0 - x, x - x1, z0 - z, z - z1)             # distance out from the wall's face
            if away < MOAT_IN - 1 or away > MOAT_OUT + 2:
                continue
            if inside(GATE_OUT, x, z, 2):
                continue
            if away > MOAT_OUT:                                     # the far bank, stepping up
                step = away - MOAT_OUT
                w.put(x, -5 + step * 2, z, "smooth_basalt" if _hash(x, 1, z, 50) < 0.5 else "gravel")
                for y in range(-4 + step * 2, 5):
                    w.air(x, y, z)
                continue
            if away < MOAT_IN:                                      # the near bank: a revetment against the wall's foot
                for y in range(-5, -1):
                    w.put(x, y, z, "cobbled_deepslate")
                w.put(x, -1, z, *stairs("cobbled_deepslate_stairs", "north"))
                continue
            vein = _smooth(x, 0, z, 6.0, 51)
            grain = _hash(x, 0, z, 52)
            mid = MOAT_IN + 2 <= away <= MOAT_OUT - 4
            w.put(x, -6, z, "blackstone")
            if mid and vein > 0.7:
                w.put(x, -5, z, "lava", {"level": "0"})
            elif vein > 0.56:
                w.put(x, -5, z, "magma_block")
            else:
                w.put(x, -5, z, "basalt" if grain < 0.4 else "smooth_basalt" if grain < 0.7 else "blackstone")
            for y in range(-4, 5):
                w.air(x, y, z)
    # lava stays where it is put only if what is beside it is not air: ring every pool with stone at its own level
    for (x, y, z), (name, props, nbt) in list(w.blocks.items()):
        if name == "minecraft:lava":
            for dx, dz in HORIZONTAL.values():
                beside = w.name(x + dx, y, z + dz)
                if beside in (None, "minecraft:air"):
                    w.put(x + dx, y, z + dz, "magma_block")


# ------------------------------------------------------------------------------------------ walls

def wall_run(w, a, b, outward, height, thickness, decay, breach=None, keep=False):
    """A curtain between two points of its OUTER face, given as (x, z); `outward` is the way that face looks."""
    length = abs(b[0] - a[0]) + abs(b[1] - a[1])
    z_out = thickness - 1
    piece = Canvas()
    turns = {"south": 0, "west": 1, "north": 2, "east": 3}[outward]
    if outward == "south":
        origin = (min(a[0], b[0]), a[1] - z_out)
    elif outward == "west":
        origin = (a[0] + z_out, min(a[1], b[1]))
    elif outward == "north":
        origin = (max(a[0], b[0]), a[1] + z_out)
    else:
        origin = (a[0] - z_out, max(a[1], b[1]))
    noise_origin(origin[0], 0, origin[1])
    curtain_wall(piece, 0, length, z_out, 0, height, decay=decay, thickness=thickness, breach=breach, keep=keep)
    noise_origin()
    blit(w, piece, origin[0], 0, origin[1], turns)


def walls(w):
    x0, z0, x1, z1 = OUTER
    # the outer curtain: the front is kept up, the back less, and the east has been breached
    wall_run(w, (x0, z1), (GATE_OUT[0], z1), "south", OUTER_H, OUTER_T, 0.12)
    wall_run(w, (GATE_OUT[2], z1), (x1, z1), "south", OUTER_H, OUTER_T, 0.14)
    wall_run(w, (x0, z0), (x0, z1), "west", OUTER_H, OUTER_T, 0.22)
    wall_run(w, (x0, z0), (x1, z0), "north", OUTER_H, OUTER_T, 0.28)
    wall_run(w, (x1, z0), (x1, z1), "east", OUTER_H, OUTER_T, 0.34, breach=(z1 - 123, 6))
    ix0, iz0, ix1, iz1 = INNER
    wall_run(w, (ix0, iz1), (GATE_IN[0], iz1), "south", INNER_H, INNER_T, 0.08)
    wall_run(w, (GATE_IN[2], iz1), (ix1, iz1), "south", INNER_H, INNER_T, 0.08)
    wall_run(w, (ix0, iz0), (ix0, iz1), "west", INNER_H, INNER_T, 0.16)
    wall_run(w, (ix0, iz0), (ix1, iz0), "north", INNER_H, INNER_T, 0.12)
    wall_run(w, (ix1, iz0), (ix1, iz1), "east", INNER_H, INNER_T, 0.10)


def towers(w):
    x0, z0, x1, z1 = OUTER
    walk = OUTER_H - 1
    floors = [0, 6, walk, walk + 7]
    outer = (
        (x0 + 3, z0 + 3, ("south", "east"), 0.2, 0.0),          # Cobre
        ((x0 + x1) // 2, z0 + 2, ("east", "west"), 0.22, 0.0),    # Eco
        (x1 - 3, z0 + 3, ("south", "west"), 0.25, 0.0),          # Obsidiana
        (x0 + 2, (z0 + z1) // 2, ("north", "south"), 0.18, 0.0),  # Resina
        (x1 - 2, (z0 + z1) // 2 - 4, ("north", "south"), 0.5, 0.9),   # Hundida: no roof, and not all of its head
        (x0 + 3, z1 - 3, ("north", "east"), 0.12, 0.0),          # Escama
        (x1 - 3, z1 - 3, ("north", "west"), 0.14, 0.0),          # Vidrio
    )
    for cx, cz, sides, decay, ragged in outer:
        noise_origin(cx, 0, cz)
        inward = "south" if cz < 50 else "north" if cz > 150 else "east" if cx < 100 else "west"
        doors = [(side, walk) for side in sides] + [(inward, 0)]
        round_tower(w, cx, cz, 9, 0, 26, decay=decay, floors=floors, doors=doors, roof=not ragged, lit=not ragged, ragged=ragged)
    ix0, iz0, ix1, iz1 = INNER
    walk = INNER_H - 1
    floors = [0, 6, 12, walk, walk + 7]
    for cx, cz, sides, ground_door in (
        (ix0 + 1, iz0 + 1, ("south", "east"), "south"), (ix1 - 1, iz0 + 1, ("south", "west"), "south"),
        (ix0 + 1, iz1 - 1, ("north", "east"), "north"), (ix1 - 1, iz1 - 1, ("north", "west"), "north"),
    ):
        noise_origin(cx, 0, cz)
        round_tower(w, cx, cz, 8, 0, 32, decay=0.08, floors=floors, doors=[(side, walk) for side in sides] + [(ground_door, 0)])
    noise_origin()


# ------------------------------------------------------------------------------------------ gatehouses

def gatehouse(w, box, height, decay, keep=False):
    """A gate: a block of masonry with a vaulted passage through it, a portcullis half up, murder holes, a
    chamber for the winch over the passage, two drums flanking the front, and a fighting top."""
    x0, z0, x1, z1 = box
    stone = masonry(decay, keep)
    family = "polished_blackstone_brick" if keep else "deepslate_brick"
    mid = (x0 + x1) // 2
    noise_origin(x0, 0, z0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(-6, height):
                t = max(0.0, y / float(height))
                edge = x in (x0, x1) or z in (z0, z1)
                w.put(x, y, z, stone(x, y, z, t) if edge or y in (height - 1,) else "cobbled_deepslate")
    # the passage: seven wide, eight high, its head rounded off with stairs upside down
    for z in range(z0, z1 + 1):
        for x in range(mid - 3, mid + 4):
            w.put(x, -1, z, "polished_deepslate" if (x + z) % 4 == 0 else "deepslate_tiles")
            for y in range(0, 8):
                w.air(x, y, z)
        w.put(mid - 3, 7, z, *stairs(f"{family}_stairs", "east", top=True))
        w.put(mid + 3, 7, z, *stairs(f"{family}_stairs", "west", top=True))
    # two portcullises, the front one three up off the ground, and the holes in the vault between them
    for z, lift in ((z1 - 3, 3), (z0 + 3, 5)):
        for x in range(mid - 3, mid + 4):
            for y in range(lift, 8):
                if w.name(x, y, z) == "minecraft:air":
                    w.put(x, y, z, "iron_bars")
    for z in range(z0 + 6, z1 - 5, 4):
        for x in (mid - 1, mid + 1):
            w.put(x, 8, z, "iron_bars")
    # lanterns on chains down the passage
    for z in range(z0 + 5, z1 - 3, 6):
        w.put(mid, 7, z, "iron_chain", {"axis": "y", "waterlogged": "false"})
        w.put(mid, 6, z, "lantern", {"hanging": "true", "waterlogged": "false"})
    # the winch chamber over the passage
    for x in range(x0 + 3, x1 - 2):
        for z in range(z0 + 3, z1 - 2):
            w.put(x, 9, z, "spruce_planks" if (x + z) % 3 else "dark_oak_planks")
            for y in range(10, height - 2):
                w.air(x, y, z)
    for x in (mid - 2, mid + 2):
        w.put(x, 10, z1 - 4, "grindstone", {"face": "floor", "facing": "south"})
        for y in range(11, height - 2):
            w.put(x, y, z1 - 4, "iron_chain", {"axis": "y", "waterlogged": "false"})
    for x in range(x0 + 5, x1 - 3, 5):
        for y in (11, 12):
            w.air(x, y, z1)
            w.air(x, y, z1 - 1)
    # the fighting top
    top = height - 1
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            rim = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            if not rim:
                continue
            w.put(x, top - 1, z, *(slab("deepslate_tile_slab", top=True)))
            w.put(x, top, z, "deepslate_tiles")
            if (x + z) % 3 != 2 and _smooth(x, top, z, 6.0, 60) > decay:
                w.put(x, top + 1, z, stone(x, top + 1, z, 1.0))
                w.put(x, top + 2, z, stone(x, top + 2, z, 1.0))
            else:
                w.put(x, top + 1, z, *slab("deepslate_tile_slab"))
    noise_origin()
    # the drums either side of the way in
    for cx in (x0 + 2, x1 - 2):
        noise_origin(cx, 0, z1)
        round_tower(w, cx, z1 - 3, 6, 0, height + 8, decay=decay, keep=keep, floors=[0, 9, height - 1], doors=[("north", height - 1)])
    noise_origin()
    # the passage again, because the drums have just been stood in part of it
    for z in range(z0 - 1, z1 + 2):
        for x in range(mid - 2, mid + 3):
            for y in range(0, 7):
                if w.name(x, y, z) not in ("minecraft:iron_bars", "minecraft:lantern", "minecraft:iron_chain"):
                    w.air(x, y, z)


def bridge(w):
    """Stone, because the ditch under it burns: three arches on piers, a parapet, and a gap where a span has gone."""
    x0, z0, x1, z1 = BRIDGE
    for z in range(z0, z1 + 1):
        gone = 222 <= z <= 224
        pier = (z - z0) % 9 in (0, 1)
        for x in range(x0, x1 + 1):
            side = x in (x0, x1)
            if not gone:
                w.put(x, -1, z, "polished_deepslate" if side else "deepslate_tiles" if _hash(x, 0, z, 61) < 0.8 else "cracked_deepslate_tiles")
                w.put(x, -2, z, "deepslate_bricks")
                if side:
                    w.put(x, 0, z, "deepslate_brick_wall" if (z % 4) else "polished_deepslate")
                    if z % 8 == 0:
                        w.put(x, 1, z, "lantern", {"hanging": "false", "waterlogged": "false"})
            elif side and z == 223:
                w.put(x, -1, z, *slab("deepslate_brick_slab"))
            if pier:
                for y in range(-6, -2):
                    w.put(x, y, z, "cobbled_deepslate" if _hash(x, y, z, 62) < 0.6 else "deepslate_bricks")
            elif not gone:
                if (z - z0) % 9 in (2, 8):
                    w.put(x, -3, z, *stairs("deepslate_brick_stairs", "south" if (z - z0) % 9 == 2 else "north", top=True))
            for y in range(0 if not side else 2, 8):
                w.air(x, y, z)


# ------------------------------------------------------------------------------------------ the great tower

KEEP = (72, 17, 129, 70)


def keep(w):
    """The torre del homenaje: five storeys of blackstone nine high, a turret at each corner, a fighting
    top, and on it the drum and copper dome of the observatory. The best kept thing in the castle:
    the automatons still see to it."""
    x0, z0, x1, z1 = KEEP
    noise_origin(x0, 0, z0)
    building(w, x0, z0, x1, z1, 0, [9, 9, 9, 9, 9], decay=0.04, keep=True, wall=3, roof="flat", windows=5,
             glass="orange_stained_glass_pane", doors=[("south", 25, 7, 8)], footing=6)
    # buttresses up the long faces, stepping back as they rise
    for x in range(x0 + 6, x1 - 4, 9):
        for z, out in ((z0 - 1, "north"), (z1 + 1, "south")):
            if out == "south" and abs(x - (x0 + x1) // 2) < 8:
                continue
            for y in range(-4, 34):
                w.put(x, y, z, "polished_blackstone_bricks" if _hash(x, y, z, 80) < 0.85 else "blackstone")
            w.put(x, 34, z, *stairs("polished_blackstone_brick_stairs", "south" if out == "north" else "north"))
    for z in range(z0 + 6, z1 - 4, 9):
        for x, out in ((x0 - 1, "west"), (x1 + 1, "east")):
            for y in range(-4, 34):
                w.put(x, y, z, "polished_blackstone_bricks" if _hash(x, y, z, 80) < 0.85 else "blackstone")
            w.put(x, 34, z, *stairs("polished_blackstone_brick_stairs", "east" if out == "west" else "west"))
    noise_origin()
    # the four turrets; the north-east one is the horn tower and stands a storey over the rest
    for cx, cz, height in ((x0, z0, 56), (x1, z0, 66), (x0, z1, 56), (x1, z1, 56)):
        noise_origin(cx, 0, cz)
        inward = [("east" if cx == x0 else "west", level) for level in (0, 45)]
        round_tower(w, cx, cz, 6, 0, height, decay=0.04, keep=True, floors=[0, 9, 18, 27, 36, 45, height - 9], doors=inward)
    noise_origin()
    # the observatory: a drum on the roof and a dome of green copper, slit open to the south for the glass
    ox, oz, r = (x0 + x1) // 2, z0 + 20, 11
    for (dx, dz), depth in disc(r + 0.4).items():
        for y in range(46, 56):
            if depth < 2.0:
                w.put(ox + dx, y, oz + dz, "polished_blackstone_bricks" if _hash(ox + dx, y, oz + dz, 81) < 0.8 else "chiseled_polished_blackstone" if y == 55 else "blackstone")
            else:
                w.air(ox + dx, y, oz + dz)
        rise = math.sqrt(max(0.0, (r + 0.4) ** 2 - (dx * dx + dz * dz)))
        shell_y = 56 + int(round(rise * 0.85))
        slit = abs(dx) <= 1 and dz > 2
        if not slit:
            w.put(ox + dx, shell_y, oz + dz, COPPER_ROOF[int(_hash(ox + dx, shell_y, oz + dz, 82) * len(COPPER_ROOF))])
            if depth < 1.2:
                for y in range(56, shell_y):
                    w.put(ox + dx, y, oz + dz, "oxidized_cut_copper")
    for y in range(66, 72):
        w.put(ox, y, oz, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"}) if y == 71 else w.put(ox, y, oz, "oxidized_copper")
    for k in range(4):
        w.air(ox - 1 + k % 2, 47 + k // 2, oz + r)          # the door from the roof
    # the hall's great chimney on the east face, smoking
    for y in range(0, 58):
        for dx in (0, 1, 2):
            for dz in (0, 1, 2):
                rim = dx != 1 or dz != 1
                if rim:
                    w.put(x1 + 1 + dx, y, z0 + 34 + dz, "polished_blackstone_bricks" if _hash(x1 + dx, y, z0 + dz, 83) < 0.8 else "blackstone")
                elif y > 2:
                    w.air(x1 + 1 + dx, y, z0 + 34 + dz)
    w.put(x1 + 2, 1, z0 + 35, "magma_block")
    w.put(x1 + 2, 2, z0 + 35, "campfire", {"facing": "north", "lit": "true", "signal_fire": "true", "waterlogged": "false"})


# ------------------------------------------------------------------------------------------ the upper ward's ranges

def wings(w):
    # the west range: library, scriptorium, armoury, hall of banners, barracks, all under one long roof; the worse kept side
    noise_origin(48, 0, 24)
    building(w, 48, 24, 70, 112, 0, [9, 9], decay=0.2, wall=2, roof="tile", ridge="z", windows=3,
             doors=[("east", 8, 3, 5), ("east", 44, 3, 5), ("east", 76, 3, 5)], chimneys=[(50, 50), (50, 100)], open_roof=0.12)
    # the chapel of the anvil: tall, copper-roofed, half its vault down
    noise_origin(131, 0, 24)
    building(w, 131, 24, 153, 44, 0, [16], decay=0.25, wall=2, roof="copper", ridge="x", windows=8, glass="red_stained_glass_pane",
             doors=[("west", 8, 3, 6)], open_roof=0.3)
    # the sacristy, the great foundry with its three stacks, and the master's workshop: the east range, kept working
    noise_origin(131, 0, 46)
    building(w, 131, 46, 153, 54, 0, [8], decay=0.1, wall=2, roof="tile", ridge="x", doors=[("west", 3, 2, 4)])
    noise_origin(131, 0, 56)
    building(w, 131, 56, 153, 98, 0, [18], decay=0.06, keep=True, wall=2, roof="black", ridge="z", windows=6, glass="iron_bars",
             doors=[("west", 18, 5, 7)], chimneys=[(149, 62), (149, 76), (149, 90)])
    noise_origin(131, 0, 100)
    building(w, 131, 100, 153, 112, 0, [9, 9], decay=0.08, wall=2, roof="tile", ridge="x", doors=[("west", 5, 3, 5)], chimneys=[(149, 104)])
    # the mess and the alloy room, either side of the inner gate
    noise_origin(72, 0, 102)
    building(w, 72, 102, 88, 118, 0, [8], decay=0.15, wall=1, roof="tile", ridge="x", doors=[("north", 7, 3, 4)], chimneys=[(74, 114)])
    noise_origin(113, 0, 102)
    building(w, 113, 102, 129, 118, 0, [8], decay=0.08, keep=True, wall=1, roof="black", ridge="x", doors=[("north", 7, 3, 4)], chimneys=[(125, 114)])
    noise_origin()


# ------------------------------------------------------------------------------------------ the lower ward

def lower_ward(w):
    """What is between the two walls. The raiders have had it for years: roofs are open, one house is burnt out."""
    houses = (
        # x0, z0, x1, z1, storeys, roof, ridge, decay, open, doors, chimneys
        (8, 172, 44, 184, [7], "wood", "x", 0.3, 0.25, [("north", 6, 4, 5), ("north", 20, 4, 5)], []),              # stables
        (48, 172, 84, 184, [7], "wood", "x", 0.22, 0.1, [("north", 16, 3, 4)], [(80, 174)]),                          # saddlery
        (64, 156, 84, 168, [6], "wood", "x", 0.25, 0.15, [("west", 5, 2, 4)], []),                                     # fletcher
        (117, 156, 145, 168, [7, 7], "tile", "x", 0.15, 0.0, [("west", 5, 3, 4)], [(141, 158)]),                       # tavern
        (149, 156, 193, 168, [7, 7], "tile", "x", 0.2, 0.2, [("north", 20, 3, 4)], [(151, 164)]),                      # commissions house
        (119, 186, 141, 194, [7], "tile", "x", 0.1, 0.0, [("north", 9, 2, 4)], []),                                   # guard house
        (169, 76, 187, 88, [6], "black", "x", 0.1, 0.0, [("west", 5, 2, 3)], []),                                     # powder house
    )
    for x0, z0, x1, z1, storeys, roof, ridge, decay, open_roof, doors, chimneys in houses:
        noise_origin(x0, 0, z0)
        building(w, x0, z0, x1, z1, 0, storeys, decay=decay, wall=1, roof=roof, ridge=ridge, doors=doors, chimneys=chimneys,
                 open_roof=open_roof, floor="dark_oak_planks", footing=3)
    # the apprentices' houses: six in a row, one of them burnt out
    for k in range(6):
        hx = 118 + k * 13
        noise_origin(hx, 0, 138)
        burnt = k == 4
        building(w, hx, 140, hx + 9, 151, 0, [6], decay=0.5 if burnt else 0.2, wall=1, roof="wood", ridge="z",
                 doors=[("north", 4, 2, 3)], chimneys=[] if burnt else [(hx + 1, 148)], open_roof=0.75 if burnt else 0.08,
                 floor="dark_oak_planks", footing=3)
    noise_origin()


def build():
    w = Canvas()
    ground(w)
    moat(w)
    walls(w)
    towers(w)
    gatehouse(w, GATE_OUT, 22, 0.1)
    gatehouse(w, GATE_IN, 24, 0.06, keep=True)
    bridge(w)
    keep(w)
    wings(w)
    lower_ward(w)
    import castillo_bajo
    castillo_bajo.build(w)
    # last, because its stairs are cut down through floors that have to be there first
    import castillo_sotanos
    castillo_sotanos.build(w)
    import castillo_interiores
    castillo_interiores.build(w)
    # and when everything is where it is going to be, enough light to read it by
    import castillo_luz
    castillo_luz.light(w)
    return w
