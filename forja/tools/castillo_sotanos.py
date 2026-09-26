"""Under the castle: level -1 (crypt, dungeons, the quenching hall, coal, ingots, cellar, cistern, ossuary,
mine and slag drain) and level -2 (the antechamber, the Deep Forge, the soul foundry, the vault).

Everything here is a shell round air: what is not written stays the world's own rock. Plan coordinates,
y = 0 the courtyard; level -1 is walked at y = -10 and level -2 at y = -25.
"""
import math

from castillo import HORIZONTAL, OPPOSITE, _hash, _smooth, disc, masonry, noise_origin, slab, stairs

L1, L2 = -10, -25
CHAIN = {"axis": "y", "waterlogged": "false"}


def _lantern(w, x, y, z, soul=False, drop=1):
    for k in range(drop):
        w.put(x, y - k, z, "iron_chain", CHAIN)
    w.put(x, y - drop, z, "soul_lantern" if soul else "lantern", {"hanging": "true", "waterlogged": "false"})


def cellar(w, x0, z0, x1, z1, floor_y, height, keep=False, decay=0.15, soul=False, pillars=9, floor=None, lit=10):
    """A vaulted room: walls, a patterned floor, ribs across the short span every six, pillars if it is wide, lanterns on chains."""
    stone = masonry(decay, keep)
    family = "polished_blackstone_brick" if keep else "deepslate_brick"
    top = floor_y + height                         # the ceiling's course
    noise_origin(x0, floor_y, z0)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            shell = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            for y in range(floor_y - 1, top + 1):
                if shell or y == top:
                    w.put(x, y, z, stone(x, y, z, 0.5))
                elif y == floor_y - 1:
                    grain = _hash(x, y, z, 120)
                    w.put(x, y, z, floor(x, z) if floor else ("polished_deepslate" if (x + z) % 2 else "deepslate_tiles") if grain < 0.9 else "cracked_deepslate_tiles")
                else:
                    w.air(x, y, z)
    along_x = (x1 - x0) >= (z1 - z0)               # ribs cross the short span
    span0, span1 = (z0, z1) if along_x else (x0, x1)
    run0, run1 = (x0, x1) if along_x else (z0, z1)
    for r in range(run0 + 3, run1 - 1, 6):
        for k, s in enumerate(range(span0, span1 + 1)):
            reach = min(s - span0, span1 - s)
            x, z = (r, s) if along_x else (s, r)
            if reach == 0:
                for y in range(floor_y, top):
                    w.put(x, y, z, "polished_basalt", {"axis": "y"})
            elif reach <= 2:
                facing = ("south" if s - span0 < span1 - s else "north") if along_x else ("east" if s - span0 < span1 - s else "west")
                w.put(x, top - 3 + reach, z, *stairs(f"{family}_stairs", OPPOSITE[facing], top=True))
                for y in range(top - 2 + reach, top):
                    w.put(x, y, z, stone(x, y, z, 0.9))
            else:
                w.put(x, top - 1, z, "polished_deepslate" if not keep else "polished_blackstone")
        mid = (span0 + span1) // 2
        if (r - run0) % 12 == 3 and lit:
            _lantern(w, r if along_x else mid, top - 2, mid if along_x else r, soul, 1)
    if pillars and (span1 - span0) > 14:
        for px in range(x0 + pillars // 2 + 2, x1 - 2, pillars):
            for pz in range(z0 + pillars // 2 + 2, z1 - 2, pillars):
                for y in range(floor_y, top):
                    w.put(px, y, pz, "polished_basalt", {"axis": "y"})
                for side, (dx, dz) in HORIZONTAL.items():
                    w.put(px + dx, top - 1, pz + dz, *stairs(f"{family}_stairs", OPPOSITE[side], top=True))
                w.put(px, floor_y, pz, "chiseled_deepslate" if not keep else "chiseled_polished_blackstone")
    noise_origin()


def corridor(w, a, b, floor_y, width=3, height=4, keep=False, soul=False):
    """A passage between two points on one axis, walled, floored and roofed, a lantern every eight."""
    stone = masonry(0.2, keep)
    (ax, az), (bx, bz) = a, b
    half = width // 2
    for x in range(min(ax, bx) - (half if az != bz else 0), max(ax, bx) + (half if az != bz else 0) + 1):
        for z in range(min(az, bz) - (half if ax != bx else 0), max(az, bz) + (half if ax != bx else 0) + 1):
            for y in range(floor_y - 1, floor_y + height + 1):
                if y in (floor_y - 1, floor_y + height):
                    w.put(x, y, z, stone(x, y, z, 0.4) if y > floor_y else "deepslate_tiles" if (x + z) % 3 else "polished_deepslate")
                else:
                    w.air(x, y, z)
            if (x + z) % 8 == 0 and x in (ax, bx) or (x + z) % 8 == 0 and z in (az, bz):
                _lantern(w, x, floor_y + height - 1, z, soul, 0)
    # the walls either side
    for x in range(min(ax, bx) - half - 1, max(ax, bx) + half + 2):
        for z in range(min(az, bz) - half - 1, max(az, bz) + half + 2):
            for y in range(floor_y, floor_y + height):
                if w.name(x, y, z) is None:
                    near = any(w.name(x + dx, y, z + dz) == "minecraft:air" for dx, dz in HORIZONTAL.values())
                    if near:
                        w.put(x, y, z, stone(x, y, z, 0.4))


def stair_run(w, x, z, y_top, direction, drop, width=3, keep=False):
    """A straight flight going down in `direction` from (x, z, y_top): a step a block, four of headroom, walled and roofed."""
    stone = masonry(0.15, keep)
    family = "polished_blackstone_brick" if keep else "deepslate_brick"
    dx, dz = HORIZONTAL[direction]
    px, pz = -dz, dx
    half = width // 2
    for k in range(drop + 1):
        cx, cz, y = x + dx * k, z + dz * k, y_top - k
        for across in range(-half - 1, half + 2):
            bx, bz = cx + px * across, cz + pz * across
            side = abs(across) == half + 1
            for yy in range(y - 1, y + 6):
                if side or yy == y + 5:
                    if w.name(bx, yy, bz) in (None,) or (w.name(bx, yy, bz) != "minecraft:air" and side):
                        w.put(bx, yy, bz, stone(bx, yy, bz, 0.5))
                elif yy == y - 1:
                    w.put(bx, yy, bz, *stairs(f"{family}_stairs", OPPOSITE[direction])) if k < drop else w.put(bx, yy, bz, "polished_deepslate")
                else:
                    w.air(bx, yy, bz)
        if k % 6 == 3:
            w.put(cx + px * half, y + 3, cz + pz * half, "lantern", {"hanging": "false", "waterlogged": "false"}) if False else None
    return x + dx * drop, z + dz * drop


def rotunda(w, cx, cz, r, floor_y, height):
    """The Deep Forge: a round hall under a dome, a ring of columns, the forge table's star laid in the
    floor in melt channels, and in the middle of it, on three steps, the dead forge the ritual is worked at."""
    stone = masonry(0.06, True)
    noise_origin(cx, floor_y, cz)
    for (dx, dz), depth in disc(r + 2.4).items():
        x, z = cx + dx, cz + dz
        d = r + 2.4 - depth                          # distance from the middle
        dome = floor_y + height + int(round(math.sqrt(max(0.0, 1.0 - (d / (r + 0.5)) ** 2)) * 6)) if d <= r else floor_y + height
        for y in range(floor_y - 1, dome + 2):
            if d > r:
                w.put(x, y, z, stone(x, y, z, 0.5))
            elif y == floor_y - 1:
                ring = int(d) % 6 == 5
                w.put(x, y, z, "polished_blackstone" if ring else "polished_blackstone_bricks" if _hash(x, y, z, 121) < 0.85 else "cracked_polished_blackstone_bricks")
            elif y >= dome:
                w.put(x, y, z, stone(x, y, z, 0.9))
            else:
                w.air(x, y, z)
    # the star: five points on a circle, joined two apart, drawn in the mod's own melt channels
    points = [(cx + math.cos(math.radians(-90 + 72 * k)) * (r - 6), cz + math.sin(math.radians(-90 + 72 * k)) * (r - 6)) for k in range(5)]
    for k in range(5):
        (ax, az), (bx, bz) = points[k], points[(k + 2) % 5]
        steps = int(max(abs(bx - ax), abs(bz - az)) * 2)
        last = None
        for i in range(steps + 1):
            x, z = int(round(ax + (bx - ax) * i / steps)), int(round(az + (bz - az) * i / steps))
            if last is not None and x != last[0] and z != last[1]:
                w.put(x, floor_y - 1, last[1], "forja:conducto_de_damasco")      # a diagonal step needs a corner to flow round
            w.put(x, floor_y - 1, z, "forja:conducto_de_damasco")
            last = (x, z)
    for px, pz in points:
        x, z = int(round(px)), int(round(pz))
        w.put(x, floor_y - 1, z, "magma_block")
        w.put(x, floor_y, z, "forja:farol_de_pavesa")
    # the ring of columns, a brazier hung between each pair
    for k in range(10):
        angle = math.radians(36 * k + 18)
        x, z = cx + int(round(math.cos(angle) * (r - 2))), cz + int(round(math.sin(angle) * (r - 2)))
        for y in range(floor_y, floor_y + height + 2):
            w.put(x, y, z, "polished_basalt", {"axis": "y"})
        w.put(x, floor_y, z, "chiseled_polished_blackstone")
        w.put(x, floor_y + height - 1, z, "gilded_blackstone")
        hx, hz = cx + int(round(math.cos(angle + math.radians(18)) * (r - 4))), cz + int(round(math.sin(angle + math.radians(18)) * (r - 4)))
        _lantern(w, hx, floor_y + height, hz, k % 2 == 0, 3)
    # the dais and the dead forge
    for (dx, dz), depth in disc(4.4).items():
        tier = 0 if depth < 1.2 else 1 if depth < 2.6 else 2
        for y in range(floor_y, floor_y + tier):
            w.put(cx + dx, y, cz + dz, "polished_blackstone_bricks")
        w.put(cx + dx, floor_y + tier, cz + dz, *slab("polished_blackstone_brick_slab")) if tier < 2 else w.put(cx + dx, floor_y + tier - 1, cz + dz, "chiseled_polished_blackstone")
    w.put(cx, floor_y + 2, cz, "forja:fragua_apagada")
    noise_origin()


def build(w):
    # ---- level -1
    cellar(w, 72, 17, 129, 70, L1, 8, keep=True, decay=0.1, soul=True, pillars=9)                  # crypt of the nine masters
    cellar(w, 48, 58, 70, 68, L1, 6, decay=0.2)                                                   # the dungeon's guard room
    cellar(w, 48, 70, 70, 112, L1, 6, decay=0.3, lit=0)                                           # dungeons
    cellar(w, 72, 102, 88, 118, L1, 6, decay=0.15)                                                # cellar and larder
    cellar(w, 131, 56, 153, 98, L1, 7, keep=True, decay=0.12)                                     # coal store
    cellar(w, 131, 100, 153, 112, L1, 6, keep=True, decay=0.05)                                   # ingot store
    cellar(w, 76, 74, 125, 98, L1, 8, decay=0.12, pillars=8)                                      # the quenching hall
    cellar(w, 146, 20, 164, 40, L1, 6, keep=True, decay=0.3, soul=True)                           # ossuary
    for (dx, dz), depth in disc(7.4).items():                                                    # the cistern
        for y in range(L1 - 4, L1 + 6):
            if depth < 1.2 or y in (L1 - 4, L1 + 5):
                w.put(100 + dx, y, 110 + dz, "deepslate_bricks" if _hash(100 + dx, y, 110 + dz, 122) < 0.8 else "mossy_cobblestone")
            elif y < L1:
                w.put(100 + dx, y, 110 + dz, "water", {"level": "0"})
            else:
                w.air(100 + dx, y, 110 + dz)
    corridor(w, (100, 71), (100, 73), L1)
    corridor(w, (71, 86), (75, 86), L1)
    corridor(w, (126, 86), (130, 86), L1)
    corridor(w, (100, 99), (100, 103), L1)
    corridor(w, (130, 30), (145, 30), L1, soul=True)
    corridor(w, (165, 30), (178, 30), L1, soul=True)                                              # the ossuary's tunnel to the graveyard
    corridor(w, (154, 64), (176, 64), L1)                                                         # the mine gallery
    corridor(w, (154, 92), (203, 92), L1, width=3, height=3, keep=True)                           # the slag drain, out to the ditch
    # the four quenches, each in its own pool
    for k, (px, pz, liquid) in enumerate(((84, 79, "water"), (108, 79, "lava"), (84, 89, "powder_snow"), (108, 89, "honey_block"))):
        for x in range(px, px + 9):
            for z in range(pz, pz + 5):
                rim = x in (px, px + 8) or z in (pz, pz + 4)
                if rim:
                    w.put(x, L1 - 1, z, "polished_deepslate")
                    w.put(x, L1, z, *slab("polished_deepslate_slab"))
                else:
                    w.put(x, L1 - 2, z, "polished_deepslate")
                    w.put(x, L1 - 1, z, liquid, {"level": "0"} if liquid in ("water", "lava") else None)
    # ---- ways down from above
    stair_run(w, 78, 27, 0, "south", 0)                                                           # placeholder landing in the keep
    stair_run(w, 78, 20, -1, "south", 9, keep=True)                                               # the keep's north-west stair to the crypt
    stair_run(w, 52, 60, -1, "south", 9)                                                          # the armoury's, to the guard room
    stair_run(w, 135, 58, -1, "south", 9, keep=True)                                              # the foundry's, to the coal
    stair_run(w, 181, 30, -1, "west", 9, keep=True)                                               # the tomb's, to the ossuary tunnel
    stair_run(w, 186, 64, -1, "west", 9)                                                          # by the ice house, to the mine
    # ---- level -2
    cellar(w, 86, 40, 115, 62, L2, 8, keep=True, decay=0.05, soul=True, pillars=7)                 # the antechamber of the nine
    rotunda(w, 100, 92, 25, L2, 9)
    cellar(w, 131, 80, 153, 104, L2, 7, keep=True, decay=0.08, soul=True)                         # the soul foundry
    cellar(w, 48, 80, 72, 104, L2, 6, keep=True, decay=0.02)                                      # the vault
    corridor(w, (100, 63), (100, 66), L2, keep=True)
    corridor(w, (126, 92), (130, 92), L2, keep=True, soul=True)
    stair_run(w, 100, 76, L1 - 0, "north", 0)
    stair_run(w, 100, 84, L1 - 1, "north", 14, keep=True)                                         # from the quenching hall down to the antechamber
