"""El Bastión del Gremio: the great castle, built by code.

Andy's brief (docs/castillo/SIGUIENTE.md): dark, almost eerie, but a FORGE; not in the best state and
not a ruin, some parts better kept than others; "increíble, súper bien hecha, con detalle, cuidada, como
si fuera de Minecraft vanilla". So nothing here is a box of one block: every wall has a plinth, a
string course, slits, buttresses, machicolations and a parapet; every material is a gradient with
clustered noise in it; and the states vanilla would have worked out for itself (wall posts, bar arms,
stair corners) are worked out here, because a jigsaw piece is placed exactly as it is written.

Called from generate_assets.py, which lends its NBT writer.
"""
import json
import math
import zipfile
from pathlib import Path

CLIENT_JAR = Path.home() / ".gradle/caches/fabric-loom/26.2/minecraft-client.jar"
SEED = 20260920


# ------------------------------------------------------------------------------------------ noise

_ORIGIN = [0, 0, 0]


def noise_origin(x=0, y=0, z=0):
    """Where the piece being built stands in the castle: its noise is taken from there, not from its own corner."""
    _ORIGIN[:] = [x, y, z]


def _hash(x, y, z, salt=0):
    return _raw(x + _ORIGIN[0], y + _ORIGIN[1], z + _ORIGIN[2], salt)


def _raw(x, y, z, salt=0):
    n = (x * 374761393 + y * 668265263 + z * 2147483647 + (salt + SEED) * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1103515245) & 0xFFFFFFFF
    n ^= n >> 16
    return (n & 0xFFFFFF) / float(0x1000000)


def _smooth(x, y, z, scale, salt=0):
    """Value noise: the same number for a few blocks around, so decay comes in patches and not as static."""
    fx, fy, fz = (x + _ORIGIN[0]) / scale, (y + _ORIGIN[1]) / scale, (z + _ORIGIN[2]) / scale
    x0, y0, z0 = math.floor(fx), math.floor(fy), math.floor(fz)
    tx, ty, tz = fx - x0, fy - y0, fz - z0
    tx, ty, tz = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty), tz * tz * (3 - 2 * tz)
    total = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                weight = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                total += weight * _raw(x0 + dx, y0 + dy, z0 + dz, salt)
    return total


# ------------------------------------------------------------------------------------------ the canvas

class Canvas:
    """Blocks by position. Later writes win, except over what has been pinned."""

    def __init__(self):
        self.blocks = {}
        self.entities = []

    def put(self, x, y, z, block, props=None, nbt=None):
        if ":" not in block:
            block = "minecraft:" + block
        self.blocks[(x, y, z)] = (block, dict(props or {}), nbt)

    def air(self, x, y, z):
        self.put(x, y, z, "air")

    def name(self, x, y, z):
        entry = self.blocks.get((x, y, z))
        return entry[0] if entry else None

    def props(self, x, y, z):
        entry = self.blocks.get((x, y, z))
        return entry[1] if entry else {}

    def nbt_free(self, x, y, z):
        """Whether what is here is only a block: nothing with contents of its own that cutting into would lose."""
        entry = self.blocks.get((x, y, z))
        return entry is None or entry[2] is None

    def fill(self, x0, y0, z0, x1, y1, z1, chooser):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    if callable(chooser):
                        chosen = chooser(x, y, z)
                        if chosen is None:
                            continue
                        if isinstance(chosen, tuple):
                            self.put(x, y, z, chosen[0], chosen[1])
                        else:
                            self.put(x, y, z, chosen)
                    else:
                        self.put(x, y, z, chooser)

    def bounds(self):
        xs = [p[0] for p in self.blocks]
        ys = [p[1] for p in self.blocks]
        zs = [p[2] for p in self.blocks]
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


# ------------------------------------------------------------------------------------------ the dark palette

def masonry(decay=0.15, keep=False):
    """The castle's stone, by height: rough at the foot, dressed in the body, tiled at the head.

    `decay` is how far gone this stretch is (0 kept, 1 falling down); `keep` swaps deepslate for the
    blackstone of the great tower and the foundry.
    """
    def choose(x, y, z, t):
        patch = _smooth(x, y, z, 5.0, 1)
        grain = _hash(x, y, z, 2)
        worn = _smooth(x, y, z, 7.0, 3) * 0.6 + grain * 0.4 < decay * 0.9
        if keep:
            if t < 0.10:
                return "blackstone" if grain < 0.55 else "polished_blackstone"
            if patch > 0.74:
                return "deepslate_tiles" if not worn else "cracked_deepslate_tiles"
            if worn:
                return "cracked_polished_blackstone_bricks"
            return "polished_blackstone_bricks" if grain < 0.9 else "blackstone"
        if t < 0.10:
            return "cobbled_deepslate" if grain < 0.7 else "deepslate_bricks"
        if patch > 0.76 and t < 0.85:
            return "tuff_bricks"            # a paler patch, so a long wall is not one black plane
        if t > 0.8:
            if worn:
                return "cracked_deepslate_tiles"
            return "deepslate_tiles" if grain < 0.75 else "deepslate_bricks"
        if worn:
            return "cracked_deepslate_bricks" if grain < 0.8 else "cobbled_deepslate"
        return "deepslate_bricks" if grain < 0.86 else "deepslate_tiles"
    return choose


COPPER_ROOF = ("oxidized_cut_copper", "oxidized_cut_copper", "oxidized_cut_copper", "weathered_cut_copper", "oxidized_copper")
HORIZONTAL = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
LEFT_OF = {"north": "west", "west": "south", "south": "east", "east": "north"}       # counter-clockwise


def stairs(block, facing, top=False):
    return block, {"facing": facing, "half": "top" if top else "bottom", "shape": "straight", "waterlogged": "false"}


def slab(block, top=False):
    return block, {"type": "top" if top else "bottom", "waterlogged": "false"}


# ------------------------------------------------------------------------------------------ what vanilla would have worked out

NOT_FULL = ("air", "_stairs", "_slab", "_wall", "_bars", "_pane", "_fence", "lantern", "chain", "torch", "banner", "carpet", "trapdoor",
            "_door", "campfire", "chest", "barrel", "lava", "water", "fire", "cobweb", "_sign", "candle", "lightning_rod", "anvil", "grate",
            "ladder", "vine", "button", "lever", "pressure_plate", "head", "skull", "pot", "_bed", "bell", "grindstone", "lectern", "cauldron",
            "stonecutter", "brewing", "rail", "glass", "leaves", "farol", "jigsaw", "structure_void", "end_rod", "scaffolding", "hopper")


def is_full(name):
    if name is None:
        return False
    short = name.split(":", 1)[1]
    if short.endswith("glass") and "pane" not in short:
        return True                          # a full block for connecting purposes
    return not any(part in short for part in NOT_FULL)


def _is(name, *suffixes):
    return name is not None and any(name.endswith(s) for s in suffixes)


def resolve(canvas):
    """Arms of walls, bars, panes and fences, and the corners of stairs, from what is beside them."""
    for (x, y, z), (name, props, nbt) in list(canvas.blocks.items()):
        if _is(name, "_bars", "_pane"):
            for side, (dx, dz) in HORIZONTAL.items():
                other = canvas.name(x + dx, y, z + dz)
                props[side] = "true" if is_full(other) or _is(other, "_bars", "_pane", "_wall") else "false"
            props.setdefault("waterlogged", "false")
        elif name.startswith("forja:conducto_") or name == "forja:cano_de_colada":
            # the mod's melt channels join each other and whatever holds metal, the way MeltPipeBlock.joins does
            for side, (dx, dz) in HORIZONTAL.items():
                other = canvas.name(x + dx, y, z + dz) or ""
                props[side] = "true" if other.startswith(("forja:conducto_", "forja:cano_", "forja:cuba_", "forja:crisol_", "forja:mesa_de_losa",
                                                         "forja:mesa_de_brasa", "forja:mesa_de_almas", "forja:caja_de_moldeo")) else "false"
            props["up"] = "false"
            props["down"] = "false"
        elif _is(name, "_fence"):
            for side, (dx, dz) in HORIZONTAL.items():
                other = canvas.name(x + dx, y, z + dz)
                props[side] = "true" if is_full(other) or _is(other, "_fence", "_fence_gate") else "false"
            props.setdefault("waterlogged", "false")
        elif _is(name, "_wall"):
            above = canvas.name(x, y + 1, z)
            arms = {}
            for side, (dx, dz) in HORIZONTAL.items():
                other = canvas.name(x + dx, y, z + dz)
                joined = is_full(other) or _is(other, "_wall", "_bars", "_pane", "_fence_gate")
                if not joined:
                    arms[side] = "none"
                    continue
                over = canvas.name(x + dx, y + 1, z + dz)
                covered = is_full(above) or (_is(above, "_wall") and (is_full(over) or _is(over, "_wall", "_bars", "_pane")))
                arms[side] = "tall" if covered else "low"
            props.update(arms)
            joined = [side for side, state in arms.items() if state != "none"]
            straight = (sorted(joined) in (["north", "south"], ["east", "west"])
                        and len({arms[side] for side in joined}) == 1)
            post_above = above is not None and not is_full(above) and above != "minecraft:air" and not _is(above, "_wall")
            props["up"] = "false" if straight and not post_above else "true"
            props.setdefault("waterlogged", "false")
    for (x, y, z), (name, props, nbt) in list(canvas.blocks.items()):
        if not _is(name, "_stairs"):
            continue
        facing, half = props.get("facing", "north"), props.get("half", "bottom")

        def stair_at(direction):
            dx, dz = HORIZONTAL[direction]
            other = canvas.name(x + dx, y, z + dz)
            if _is(other, "_stairs"):
                p = canvas.props(x + dx, y, z + dz)
                if p.get("half", "bottom") == half:
                    return p.get("facing", "north")
            return None

        def can_take(direction):
            return stair_at(direction) != facing

        shape = "straight"
        front = stair_at(facing)
        if front is not None and front not in (facing, OPPOSITE[facing]) and can_take(OPPOSITE[front]):
            shape = "outer_left" if front == LEFT_OF[facing] else "outer_right"
        else:
            back = stair_at(OPPOSITE[facing])
            if back is not None and back not in (facing, OPPOSITE[facing]) and can_take(back):
                shape = "inner_left" if back == LEFT_OF[facing] else "inner_right"
        props["shape"] = shape


def validate(canvas):
    """Every block and every property against the game's own blockstate files: a typo here is an air block there."""
    problems = []
    seen = set()
    with zipfile.ZipFile(CLIENT_JAR) as jar:
        names = set(jar.namelist())
        for name, props, nbt in canvas.blocks.values():
            key = (name, tuple(sorted(props)))
            if key in seen or not name.startswith("minecraft:"):
                continue
            seen.add(key)
            path = f"assets/minecraft/blockstates/{name.split(':', 1)[1]}.json"
            if path not in names:
                problems.append(f"no such block: {name}")
                continue
            text = jar.read(path).decode("utf-8")
            data = json.loads(text)
            known = set()
            for variant in data.get("variants", {}):
                known.update(part.split("=")[0] for part in variant.split(",") if "=" in part)
            if "multipart" in data:
                known.update(p for p in props if f'"{p}"' in text)
            # waterlogged, and the like, change no model and so are in no blockstate file
            for prop in props:
                if prop not in known and prop not in ("waterlogged", "powered", "lit", "signal_fire", "open", "occupied", "has_book",
                                                       "shape", "up", "hanging", "type", "half", "facing", "axis", "level", "enabled", "moisture", "honey_level", "age", "face", "rotation"):
                    problems.append(f"{name} has no property {prop}")
    return problems


# ------------------------------------------------------------------------------------------ pieces of castle

def curtain_wall(c, x0, x1, z_out, base, height, decay=0.15, thickness=5, breach=None, keep=False, footing=6, brazier=True):
    """A stretch of curtain wall along x, its outer face looking south (+z).

    Foot to head: a battered plinth; the body with buttresses and arrow slits between them; a string
    course; machicolations carrying a parapet that overhangs by one; merlons two wide with a crenel
    between; and the wall walk behind it, with a low wall on the courtyard side.
    """
    stone = masonry(decay, keep)
    z_in = z_out - thickness + 1
    top = base + height - 1                     # the wall walk's floor
    family = "polished_blackstone_brick" if keep else "deepslate_brick"
    tile = "deepslate_tile"

    def gone(x, y):
        """How much of the wall the breach has taken at this column: a V, ragged at the edges."""
        if breach is None:
            return False
        centre, width = breach
        reach = width * (0.25 + 0.75 * (y - base) / float(height))
        return abs(x - centre) + (_hash(x, y, 0, 9) - 0.5) * 2.5 < reach

    for x in range(x0, x1 + 1):
        for y in range(base, top + 1):
            if gone(x, y):
                continue
            t = (y - base) / float(height)
            for z in range(z_in, z_out + 1):
                inner = z_in < z < z_out
                c.put(x, y, z, "cobbled_deepslate" if inner and y < top else stone(x, y, z, t))
        # what it stands on, so a dip in the ground under it is not a gap under it
        for y in range(base - footing, base):
            for z in range(z_in, z_out + 2):
                c.put(x, y, z, "cobbled_deepslate" if _hash(x, y, z, 32) < 0.7 else "deepslate")
        # the plinth: one block proud for three courses, then a splay of stairs back to the face
        if not gone(x, base + 1):
            for y in range(base, base + 3):
                c.put(x, y, z_out + 1, stone(x, y, z_out + 1, 0.0))
            c.put(x, base + 3, z_out + 1, *stairs("cobbled_deepslate_stairs" if _hash(x, 0, 0, 4) < 0.6 else f"{family}_stairs", "north"))
        # the string course
        if not gone(x, top - 4):
            c.put(x, top - 4, z_out, "chiseled_deepslate" if (x - x0) % 4 == 2 else "polished_deepslate")

    # buttresses, and a slit between each pair
    for bx in range(x0 + 4, x1 - 2, 8):
        for dx in (0, 1):
            for y in range(base, top - 5):
                if not gone(bx + dx, y):
                    c.put(bx + dx, y, z_out + 1, stone(bx + dx, y, z_out + 1, (y - base) / float(height)))
            if not gone(bx + dx, top - 5):
                c.put(bx + dx, top - 5, z_out + 1, *stairs(f"{family}_stairs", "north"))
        sx = bx + 5
        if sx < x1 - 1 and not gone(sx, base + 6):
            for y in (base + 6, base + 7):
                c.air(sx, y, z_out)
                c.air(sx, y, z_out - 1)
                for z in range(z_in, z_out - 1):
                    for dx in (-1, 0, 1):
                        c.air(sx + dx, y, z)
            c.put(sx, base + 8, z_out, *stairs(f"{family}_stairs", "north", top=True))
            c.put(sx, base + 5, z_out, *stairs(f"{family}_stairs", "north"))

    # machicolations and the parapet they carry
    for x in range(x0, x1 + 1):
        if gone(x, top):
            continue
        lost = _smooth(x, top, z_out, 6.0, 5) < decay * 0.9
        c.put(x, top - 1, z_out + 1, *(stairs(f"{tile}_stairs", "north", top=True) if x % 2 == 0 else slab(f"{tile}_slab", top=True)))
        c.put(x, top, z_out + 1, "deepslate_tiles")
        merlon = (x - x0) % 3 != 2
        if merlon and not lost:
            c.put(x, top + 1, z_out + 1, stone(x, top + 1, z_out + 1, 1.0))
            c.put(x, top + 2, z_out + 1, stone(x, top + 2, z_out + 1, 1.0) if _hash(x, 1, 1, 6) > decay else slab(f"{tile}_slab")[0],
                  None if _hash(x, 1, 1, 6) > decay else slab(f"{tile}_slab")[1])
        elif not merlon:
            c.put(x, top + 1, z_out + 1, *slab(f"{tile}_slab"))
        # the walk, and the low wall on the courtyard side
        for z in range(z_in, z_out + 1):
            c.put(x, top, z, "polished_deepslate" if (x + z) % 5 == 0 else "deepslate_tiles")
        if (x - x0) % 6 == 3:
            c.put(x, top + 1, z_in, f"{family}_wall")
            c.put(x, top + 2, z_in, "lantern", {"hanging": "false", "waterlogged": "false"})
        elif _hash(x, 2, 2, 7) > decay * 0.6:
            c.put(x, top + 1, z_in, f"{family}_wall")

    # lanterns on chains under the overhang, between a buttress and its slit: they light the foot of
    # the wall, which is where anyone coming at it after dark is standing
    for bx in range(x0 + 4, x1 - 2, 8):
        hx = bx + 3
        if hx <= x1 and not gone(hx, top) and c.name(hx, top - 1, z_out + 1) is not None:
            c.put(hx, top - 2, z_out + 1, "iron_chain", {"axis": "y", "waterlogged": "false"})
            c.put(hx, top - 3, z_out + 1, "lantern", {"hanging": "true", "waterlogged": "false"})
    # a brazier on the walk: magma under a fire between two posts, so the wall smokes like the forge it guards
    fx = x1 - 4
    if brazier and not gone(fx, top):
        zc = z_in + 2
        c.put(fx, top, zc, "magma_block")
        c.put(fx, top + 1, zc, "campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
        for dx in (-1, 1):
            c.put(fx + dx, top + 1, zc, f"{family}_wall")
            c.put(fx + dx, top + 2, zc, "iron_bars")

    # what the breach left at its foot
    if breach is not None:
        centre, width = breach
        for x in range(centre - width - 3, centre + width + 4):
            for z in range(z_out + 1, z_out + 6):
                heap = int((1.0 - abs(x - centre) / float(width + 4)) * (3.5 - (z - z_out) * 0.6) + _hash(x, 0, z, 8) * 1.6)
                for y in range(base, base + max(0, heap)):
                    r = _hash(x, y, z, 10)
                    c.put(x, y, z, "cobbled_deepslate" if r < 0.5 else "deepslate_bricks" if r < 0.7 else "tuff" if r < 0.85 else "gravel")
                if heap > 0 and _hash(x, 3, z, 11) < 0.4:
                    c.put(x, base + heap, z, *slab("cobbled_deepslate_slab"))


def disc(radius):
    """The columns of a filled circle, with how far each is from the rim (0 on it)."""
    cells = {}
    limit = int(math.ceil(radius))
    for dx in range(-limit, limit + 1):
        for dz in range(-limit, limit + 1):
            d = math.hypot(dx, dz)
            if d <= radius + 0.01:
                cells[(dx, dz)] = radius - d
    return cells


def round_tower(c, cx, cz, radius, base, height, decay=0.12, keep=False, roof=True, lit=True, floors=None, doors=(), footing=6,
                ragged=0.0):
    """A drum tower: battered foot, slits, a corbelled head wider than the drum, merlons, and a copper spire.

    `floors` are heights above the base (the wall walk has to meet one); `doors` are (side, height)
    pairs, a side being the way out of the tower; `ragged` takes bites out of the top, for a tower
    that has lost its roof and some of its head.
    """
    if floors is None:
        floors = list(range(0, height - 4, 7))
    stone = masonry(decay, keep)
    family = "polished_blackstone_brick" if keep else "deepslate_brick"
    top = base + height - 1
    body = disc(radius + 0.4)
    for (dx, dz), depth in body.items():
        for y in range(base, top + 1):
            t = (y - base) / float(height)
            if ragged and y > top - 9 and _smooth(cx + dx, y, cz + dz, 4.0, 30) * 9 < (y - (top - 9)) * ragged:
                continue
            if depth < 2.0:
                c.put(cx + dx, y, cz + dz, stone(cx + dx, y, cz + dz, t))
            elif (y - base) in floors:
                c.put(cx + dx, y, cz + dz, "dark_oak_planks" if (dx + dz) % 3 else "spruce_planks")
            else:
                c.air(cx + dx, y, cz + dz)
        if depth < 2.4:
            for y in range(base - footing, base):
                c.put(cx + dx, y, cz + dz, "cobbled_deepslate" if _hash(cx + dx, y, cz + dz, 31) < 0.7 else "deepslate")
    # the foot, one block proud for three courses
    for (dx, dz), depth in disc(radius + 1.4).items():
        if depth < 1.0:
            for y in range(base, base + 3):
                c.put(cx + dx, y, cz + dz, stone(cx + dx, y, cz + dz, 0.0))
    # slits on the eight winds, staggered from floor to floor
    for level, floor in enumerate(floors[1:], start=1):
        y = base + floor + 2
        if y > top - 7:
            continue
        for k in range(8):
            if (k + level) % 2:
                continue
            angle = math.radians(45 * k)
            for r in (radius, radius - 1):
                c.air(cx + int(round(math.cos(angle) * r)), y, cz + int(round(math.sin(angle) * r)))
                c.air(cx + int(round(math.cos(angle) * r)), y + 1, cz + int(round(math.sin(angle) * r)))
    # the head: a ring one wider than the drum, carried on a course of chiselled stone
    head = disc(radius + 1.4)
    for (dx, dz), depth in head.items():
        if ragged and _smooth(cx + dx, top, cz + dz, 4.0, 30) * 9 < 9 * ragged:
            continue
        if depth < 1.0:
            c.put(cx + dx, top - 1, cz + dz, "chiseled_deepslate" if (dx + dz) % 2 == 0 else "polished_deepslate")
            c.put(cx + dx, top, cz + dz, stone(cx + dx, top, cz + dz, 1.0))
            angle = math.degrees(math.atan2(dz, dx)) % 360
            if int(angle // 15) % 2 == 0 and _smooth(cx + dx, top, cz + dz, 5.0, 12) > decay:
                c.put(cx + dx, top + 1, cz + dz, stone(cx + dx, top + 1, cz + dz, 1.0))
                c.put(cx + dx, top + 2, cz + dz, *slab("deepslate_tile_slab"))
        elif depth >= 1.0:
            c.put(cx + dx, top, cz + dz, "deepslate_tiles")
    if lit:
        # The crown: eight ember windows under the roof, each a pane of stained glass with a cell of
        # lava walled in behind it. Glass does not shine and lava does, so at night the tower has eyes.
        # The lava is shut in on every side but the glass, or it would be on the stairs by morning.
        for k in range(8):
            angle = math.radians(45 * k + 22.5)
            ux, uz = math.cos(angle), math.sin(angle)
            cells = [(cx + int(round(ux * r)), cz + int(round(uz * r))) for r in (radius, radius - 1, radius - 2)]
            for yy in (top - 5, top - 4):
                c.put(cells[0][0], yy, cells[0][1], "orange_stained_glass" if k % 2 else "red_stained_glass")
                c.put(cells[1][0], yy, cells[1][1], "lava", {"level": "0"})
                c.put(cells[2][0], yy, cells[2][1], stone(cells[2][0], yy, cells[2][1], 0.9))
            for yy in (top - 6, top - 5, top - 4, top - 3):
                for ddx, ddz in ((1, 0), (-1, 0), (0, 1), (0, -1), (0, 0)):
                    nx, nz = cells[1][0] + ddx, cells[1][1] + ddz
                    if (nx, nz) == cells[0] and yy in (top - 5, top - 4):
                        continue
                    if c.name(nx, yy, nz) in (None, "minecraft:air") or (c.name(nx, yy, nz) or "").endswith("_planks"):
                        if not ((nx, nz) == cells[1] and yy in (top - 5, top - 4)):
                            c.put(nx, yy, nz, stone(nx, yy, nz, 0.9))
    # the ways in: an arch two wide and three high through the drum, wherever the builder says
    for side, up in doors:
        ux, uz = HORIZONTAL[side]
        px, pz = -uz, ux                          # across the opening
        for across in (0, 1):
            for depth in range(-1, 4):
                for yy in range(base + up + 1, base + up + 4):
                    c.air(cx + ux * (radius - depth) + px * (across - 1) + (0 if ux else 0), yy, cz + uz * (radius - depth) + pz * (across - 1))
        left = {"north": "east", "south": "west", "east": "south", "west": "north"}[side]
        c.put(cx + ux * radius - px, base + up + 3, cz + uz * radius - pz, *stairs(f"{family}_stairs", left, top=True))
        c.put(cx + ux * radius, base + up + 3, cz + uz * radius, *stairs(f"{family}_stairs", OPPOSITE[left], top=True))
    if not roof:
        return
    # the spire: copper gone green, a ring narrower every two courses, with a skirt of slabs at each step
    r = radius + 1.0
    y = top + 1
    inner = disc(radius - 1.6)
    while r > 0.2:
        ring = disc(r)
        for (dx, dz), depth in ring.items():
            if depth < 1.6 and ((dx, dz) not in inner or r < radius - 1):
                grain = _hash(cx + dx, y, cz + dz, 13)
                c.put(cx + dx, y, cz + dz, COPPER_ROOF[int(grain * len(COPPER_ROOF))])
        if int(r * 2) % 2 == 0:
            for (dx, dz), depth in disc(r + 1.0).items():
                if depth < 0.9 and c.name(cx + dx, y, cz + dz) in (None, "minecraft:air"):
                    c.put(cx + dx, y, cz + dz, *slab("oxidized_cut_copper_slab"))
        y += 1
        r -= 0.5
    c.put(cx, y, cz, "oxidized_cut_copper")
    c.put(cx, y + 1, cz, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})


ROOFS = {
    "tile": ("deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles"),
    "copper": ("oxidized_cut_copper_stairs", "oxidized_cut_copper_slab", "oxidized_cut_copper"),
    "wood": ("dark_oak_stairs", "dark_oak_slab", "dark_oak_planks"),
    "black": ("polished_blackstone_brick_stairs", "polished_blackstone_brick_slab", "polished_blackstone_bricks"),
}


def building(c, x0, z0, x1, z1, base, storeys, decay=0.12, keep=False, wall=2, roof="tile", ridge="z", doors=(), windows=3,
             glass="iron_bars", chimneys=(), footing=5, floor="spruce_planks", open_roof=0.0, pave=None):
    """A house of the castle, from a shed to a hall: walls with corner posts, a course at every floor,
    windows in every bay, a gabled roof with eaves, chimneys that smoke.

    `storeys` are the heights of each floor, walls included; `ridge` is the axis the roof's ridge runs
    along; `doors` are (side, along, width, height); `open_roof` is how much of the roof has fallen in.
    """
    stone = masonry(decay, keep)
    family = "polished_blackstone_brick" if keep else "deepslate_brick"
    top = base + sum(storeys)                    # first course of the roof
    post = "polished_basalt"
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = min(x - x0, x1 - x, z - z0, z1 - z)
            corner = (x in (x0, x1)) and (z in (z0, z1))
            for y in range(base - footing, top):
                t = max(0.0, (y - base) / float(max(1, top - base)))
                if y < base:
                    if edge < wall:
                        c.put(x, y, z, "cobbled_deepslate" if _hash(x, y, z, 70) < 0.7 else "deepslate")
                    continue
                if corner:
                    c.put(x, y, z, post, {"axis": "y"})
                elif edge < wall:
                    c.put(x, y, z, stone(x, y, z, t))
                else:
                    c.air(x, y, z)
            if edge >= wall:
                c.put(x, base - 1, z, pave(x, z) if pave else ("polished_deepslate" if (x + z) % 2 else "deepslate_tiles"))
    # a floor and a course at every storey line
    level = base
    for height in storeys[:-1]:
        level += height
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = min(x - x0, x1 - x, z - z0, z1 - z)
                if edge >= wall:
                    c.put(x, level - 1, z, floor if (x + z) % 4 else "dark_oak_planks")
                elif edge == 0 and not ((x in (x0, x1)) and (z in (z0, z1))):
                    c.put(x, level - 1, z, "polished_deepslate" if not keep else "polished_blackstone")
    # windows, one to a bay on every storey, barred or glazed, with a sill and a lintel
    level = base
    for height in storeys:
        sill = level + 2
        tall = min(windows, height - 4)
        if tall >= 1:
            for x in range(x0 + 3, x1 - 2, 5):
                for z, facing in ((z0, "north"), (z1, "south")):
                    _window(c, x, sill, z, tall, wall, "z", glass, family, facing)
            for z in range(z0 + 3, z1 - 2, 5):
                for x, facing in ((x0, "west"), (x1, "east")):
                    _window(c, x, sill, z, tall, wall, "x", glass, family, facing)
        level += height
    # doors
    for side, along, width, height in doors:
        for k in range(width):
            for depth in range(wall):
                for y in range(base, base + height):
                    if side in ("north", "south"):
                        c.air(x0 + along + k, y, (z0 + depth) if side == "north" else (z1 - depth))
                    else:
                        c.air((x0 + depth) if side == "west" else (x1 - depth), y, z0 + along + k)
        if width >= 2:
            if side in ("north", "south"):
                z = z0 if side == "north" else z1
                c.put(x0 + along, base + height - 1, z, *stairs(f"{family}_stairs", "east", top=True))
                c.put(x0 + along + width - 1, base + height - 1, z, *stairs(f"{family}_stairs", "west", top=True))
            else:
                x = x0 if side == "west" else x1
                c.put(x, base + height - 1, z0 + along, *stairs(f"{family}_stairs", "south", top=True))
                c.put(x, base + height - 1, z0 + along + width - 1, *stairs(f"{family}_stairs", "north", top=True))
    if roof == "flat":
        # a deck, and round it machicolations carrying a parapet one block proud, two merlons to a crenel
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                rim = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
                if not rim:
                    c.put(x, top, z, "polished_blackstone" if (x + z) % 7 == 0 else "deepslate_tiles")
                    continue
                c.put(x, top - 1, z, *slab("deepslate_tile_slab", top=True))
                c.put(x, top, z, "deepslate_tiles")
                if (x + z) % 3 != 2 and _smooth(x, top, z, 6.0, 72) > decay:
                    c.put(x, top + 1, z, stone(x, top + 1, z, 1.0))
                    c.put(x, top + 2, z, stone(x, top + 2, z, 1.0))
                else:
                    c.put(x, top + 1, z, *slab("deepslate_tile_slab"))
        return
    # the roof: stairs climbing from eaves that overhang by one to a ridge, and the gables walled up under it
    stair, half, full = ROOFS[roof]
    if ridge == "z":
        span = x1 - x0 + 3
        for i in range((span + 1) // 2):
            for z in range(z0 - 1, z1 + 2):
                for x, facing in ((x0 - 1 + i, "east"), (x1 + 1 - i, "west")):
                    if open_roof and _smooth(x, top + i, z, 5.0, 71) < open_roof:
                        continue
                    if x0 - 1 + i == x1 + 1 - i:
                        c.put(x, top + i, z, *slab(half))
                    else:
                        c.put(x, top + i, z, *stairs(stair, facing))
            for z in (z0, z1):
                for x in range(x0 + i, x1 - i + 1):
                    if i > 0 and x0 - 1 + i < x < x1 + 1 - i:
                        c.put(x, top + i - 1, z, stone(x, top + i - 1, z, 1.0))
    else:
        span = z1 - z0 + 3
        for i in range((span + 1) // 2):
            for x in range(x0 - 1, x1 + 2):
                for z, facing in ((z0 - 1 + i, "south"), (z1 + 1 - i, "north")):
                    if open_roof and _smooth(x, top + i, z, 5.0, 71) < open_roof:
                        continue
                    if z0 - 1 + i == z1 + 1 - i:
                        c.put(x, top + i, z, *slab(half))
                    else:
                        c.put(x, top + i, z, *stairs(stair, facing))
            for x in (x0, x1):
                for z in range(z0 + i, z1 - i + 1):
                    if i > 0 and z0 - 1 + i < z < z1 + 1 - i:
                        c.put(x, top + i - 1, z, stone(x, top + i - 1, z, 1.0))
    # chimneys: a stack two square through the roof, a fire in its throat, a pot of copper on top
    rise = ((x1 - x0 if ridge == "z" else z1 - z0) + 3) // 2
    for cx, cz in chimneys:
        for y in range(base, top + rise + 3):
            for dx in (0, 1):
                for dz in (0, 1):
                    c.put(cx + dx, y, cz + dz, stone(cx + dx, y, cz + dz, 0.5 if y < top else 1.0))
        c.put(cx, top + rise + 3, cz, "campfire", {"facing": "north", "lit": "true", "signal_fire": "true", "waterlogged": "false"})
        c.put(cx + 1, top + rise + 3, cz, f"{family}_wall")
        c.put(cx, top + rise + 3, cz + 1, f"{family}_wall")
        c.put(cx + 1, top + rise + 3, cz + 1, f"{family}_wall")


def _window(c, x, y, z, tall, wall, axis, glass, family, facing):
    """One window: a slot through the wall, glazed or barred on its outer face, a lintel of stairs over it."""
    for depth in range(wall):
        px, pz = x, z
        if axis == "z":
            pz = z + depth if facing == "north" else z - depth
        else:
            px = x + depth if facing == "west" else x - depth
        for k in range(tall):
            if depth == 0:
                c.put(px, y + k, pz, glass)
            else:
                c.air(px, y + k, pz)
    c.put(x, y + tall, z, *stairs(f"{family}_stairs", OPPOSITE[facing], top=True))


# ------------------------------------------------------------------------------------------ turning a piece

TURN = {"north": "east", "east": "south", "south": "west", "west": "north"}      # a quarter clockwise, seen from above


def turned(props, turns):
    out = dict(props)
    for _ in range(turns % 4):
        if out.get("facing") in TURN:
            out["facing"] = TURN[out["facing"]]
        if out.get("axis") in ("x", "z"):
            out["axis"] = "z" if out["axis"] == "x" else "x"
        if "rotation" in out:
            out["rotation"] = str((int(out["rotation"]) + 4) % 16)
        arms = {side: out[side] for side in TURN if side in out}
        for side, value in arms.items():
            out[TURN[side]] = value
    return out


def blit(world, piece, ox, oy, oz, turns=0, skip_air=False):
    """Stands a piece, built facing south, into the castle: a quarter turn takes south to west."""
    for (x, y, z), (name, props, nbt) in piece.blocks.items():
        if skip_air and name == "minecraft:air":
            continue
        for _ in range(turns % 4):
            x, z = -z, x
        world.blocks[(ox + x, oy + y, oz + z)] = (name, turned(props, turns), nbt)


# ------------------------------------------------------------------------------------------ looking at it without the game

_COLOURS = {}
_FIXED = {"lava": (255, 120, 20), "water": (50, 90, 200), "air": None, "farol_de_pavesa": (255, 160, 60), "campfire": (230, 120, 40),
          "lantern": (250, 200, 110), "soul_lantern": (110, 220, 230), "iron_chain": (70, 74, 84), "iron_bars": (120, 122, 126),
          "grass_block": (96, 128, 62), "magma_block": (150, 60, 20), "jigsaw": (120, 60, 140), "lightning_rod": (200, 120, 80)}


def colour_of(name):
    """The average colour of a block's texture in the game's own jar; a guess by family for the shaped ones."""
    if name in _COLOURS:
        return _COLOURS[name]
    short = name.split(":", 1)[1]
    colour = _FIXED.get(short, False)
    if colour is False:
        from PIL import Image
        import io
        tries = [short, short + "_top", short + "_side"]
        for suffix in ("_stairs", "_slab", "_wall", "_fence", "_trapdoor", "_pane", "_door", "_carpet"):
            if short.endswith(suffix):
                stem = short[:-len(suffix)]
                tries += [stem, stem + "s", stem + "_planks", stem + "_top", stem.replace("brick", "bricks").replace("tile", "tiles")]
        colour = (110, 110, 118)
        with zipfile.ZipFile(CLIENT_JAR) as jar:
            names = set(jar.namelist())
            for attempt in tries:
                path = f"assets/minecraft/textures/block/{attempt}.png"
                if path in names:
                    image = Image.open(io.BytesIO(jar.read(path))).convert("RGBA").resize((1, 1), Image.BOX)
                    colour = image.getpixel((0, 0))[:3]
                    break
    _COLOURS[name] = colour
    return colour


def render(world, path, turns=0, scale=3, tilt=0.55, storey=None):
    """An oblique picture from the south (after `turns` quarter turns of the castle): fronts and tops, far to near.
    `storey` = (lowest, highest) keeps only those courses: a level with its ceiling lifted off."""
    from PIL import Image, ImageDraw

    cells = {}
    for (x, y, z), (name, props, nbt) in world.blocks.items():
        if name == "minecraft:air" or (storey and not storey[0] <= y <= storey[1]):
            continue
        for _ in range(turns % 4):
            x, z = -z, x
        cells[(x, y, z)] = name
    xs = [p[0] for p in cells]
    ys = [p[1] for p in cells]
    zs = [p[2] for p in cells]
    x0, y0, z0 = min(xs), min(ys), min(zs)
    width = (max(xs) - x0 + 1) * scale
    depth = int((max(zs) - z0 + 2) * scale * tilt)
    height = (max(ys) - y0 + 2) * scale
    picture = Image.new("RGB", (width, depth + height + 8), (28, 30, 38))
    draw = ImageDraw.Draw(picture)
    top_h = max(1, int(round(scale * tilt)))
    for (x, y, z) in sorted(cells, key=lambda p: (p[2], p[1])):
        name_front = cells.get((x, y, z + 1))
        name_top = cells.get((x, y + 1, z))
        show_top = name_top is None or not is_full(name_top)
        show_front = name_front is None or not is_full(name_front)
        if not (show_top or show_front):
            continue
        colour = colour_of(cells[(x, y, z)])
        if colour is None:
            continue
        px = (x - x0) * scale
        foot = height + int((z - z0 + 1) * scale * tilt) - (y - y0) * scale
        if show_top:
            shade = 1.0 if (x + z) % 2 else 0.95
            draw.rectangle((px, foot - scale - top_h, px + scale - 1, foot - scale - 1), fill=tuple(min(255, int(v * shade)) for v in colour))
        if show_front:
            draw.rectangle((px, foot - scale, px + scale - 1, foot - 1), fill=tuple(int(v * 0.66) for v in colour))
    picture.save(path)


# ------------------------------------------------------------------------------------------ cutting it into jigsaw pieces

CELL = 48


def state_string(name, props):
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"


def cut(world, api, folder, start_height_offset=0):
    """The castle as jigsaw pieces of at most 48 a side, in a fixed arrangement.

    One pool to a piece and one piece to a pool, so there is nothing for the game to choose: the pieces
    go back together the only way they can. They are joined as a tree grown out from the middle piece,
    each joint a pair of jigsaw blocks facing each other across the cut, put where both sides already
    have a block so that what they turn back into is simply what was there.
    """
    (x0, y0, z0), (x1, y1, z1) = world.bounds()
    cells = {}
    for (x, y, z), value in world.blocks.items():
        cells.setdefault(((x - x0) // CELL, (y - y0) // CELL, (z - z0) // CELL), {})[(x, y, z)] = value
    middle = min(cells, key=lambda k: (k[1], abs(k[0] - (x1 - x0) // CELL / 2.0) + abs(k[2] - (z1 - z0) // CELL / 2.0)))

    # Where two neighbouring pieces can be joined: a pair of blocks facing each other across the cut,
    # the plainer and the lower the better. Two pieces that touch only with air between them cannot be.
    links = {}
    taken = set()                      # a block is one joint's or nobody's: two joints on one block is one joint lost
    for key, blocks in cells.items():
        bx, by, bz = x0 + key[0] * CELL, y0 + key[1] * CELL, z0 + key[2] * CELL
        for d in ((1, 0, 0), (0, 1, 0), (0, 0, 1)):
            other = (key[0] + d[0], key[1] + d[1], key[2] + d[2])
            if other not in cells:
                continue
            best = None
            centre = (bx + CELL // 2, by + CELL // 2, bz + CELL // 2)
            for (x, y, z), value in blocks.items():
                if (d[0] and x != bx + CELL - 1) or (d[1] and y != by + CELL - 1) or (d[2] and z != bz + CELL - 1):
                    continue
                across = (x + d[0], y + d[1], z + d[2])
                facing = cells[other].get(across)
                if facing is None or value[0] == "minecraft:air" or facing[0] == "minecraft:air":
                    continue
                if (x, y, z) in taken or across in taken:
                    continue
                plain = is_full(value[0]) and is_full(facing[0]) and value[2] is None and facing[2] is None
                # plain before fancy, low before high, and the middle of the face before its edges: two
                # faces of one piece meet at an edge, and both used to pick the same corner block
                off = abs(x - centre[0]) * (not d[0]) + abs(z - centre[2]) * (not d[2])
                score = (0 if plain else 1, y if not d[1] else 0, off)
                if best is None or score < best[0]:
                    best = (score, (x, y, z), across)
            if best is None and not d[1]:
                # Side by side with only air between them (a bridge with a span gone): joined under the
                # ground instead, by a block of deepslate either side that nobody will ever see.
                ox, oz = key[0] * CELL + x0, key[2] * CELL + z0
                here = (ox + CELL - 1, by, oz + CELL // 2) if d[0] else (ox + CELL // 2, by, oz + CELL - 1)
                there = (here[0] + d[0], here[1], here[2] + d[2])
                blocks[here] = ("minecraft:deepslate", {}, None)
                cells[other][there] = ("minecraft:deepslate", {}, None)
                world.blocks[here] = blocks[here]
                world.blocks[there] = cells[other][there]
                best = ((0, by), here, there)
            if best is not None:
                taken.update((best[1], best[2]))
                links[(key, other)] = (best[1], best[2], d)
                links[(other, key)] = (best[2], best[1], (-d[0], -d[1], -d[2]))

    # a spanning tree over those, breadth first from the middle
    parent = {middle: None}
    order = [middle]
    for key in order:
        for (a, b) in links:
            if a == key and b not in parent:
                parent[b] = key
                order.append(b)
    lost = [key for key in cells if key not in parent]
    assert not lost, f"pieces nothing joins to the rest: {lost}"
    depth = {middle: 0}
    for key in order[1:]:
        depth[key] = depth[parent[key]] + 1

    def piece_name(key):
        return f"{folder}/p_{key[0]}_{key[1]}_{key[2]}"

    orientation = {(1, 0, 0): "east_up", (-1, 0, 0): "west_up", (0, 0, 1): "south_up", (0, 0, -1): "north_up", (0, 1, 0): "up_north", (0, -1, 0): "down_north"}
    joints = {key: [] for key in cells}
    for child, par in parent.items():
        if par is None:
            continue
        here, there, d = links[(par, child)]
        link = f"forja:{folder.replace('/', '_')}_{child[0]}_{child[1]}_{child[2]}"
        joints[par].append((here, orientation[d], {"name": "minecraft:empty", "target": link, "pool": f"forja:{piece_name(child)}"}))
        joints[child].append((there, orientation[(-d[0], -d[1], -d[2])], {"name": link, "target": "minecraft:empty", "pool": "minecraft:empty"}))

    # A lodestone two along from the start piece's corner, at its very bottom: the game turns the whole
    # castle a random quarter when it builds it, and this is how whoever photographs it tells which.
    mx, my, mz = x0 + middle[0] * CELL, y0 + middle[1] * CELL, z0 + middle[2] * CELL
    cells[middle][(mx + 2, -2, mz)] = ("minecraft:lodestone", {}, None)
    cells[middle][(mx, my, mz)] = cells[middle].get((mx, my, mz), ("minecraft:deepslate", {}, None))

    for key, blocks in cells.items():
        blocks = dict(blocks)
        for position, facing, data in joints[key]:
            was = blocks[position]
            nbt = {"id": "minecraft:jigsaw", "joint": "aligned", "final_state": state_string(was[0], was[1]),
                   "selection_priority": 0, "placement_priority": 0}
            nbt.update(data)
            blocks[position] = ("minecraft:jigsaw", {"orientation": facing}, nbt)
        bx, by, bz = x0 + key[0] * CELL, y0 + key[1] * CELL, z0 + key[2] * CELL
        size = (min(CELL, x1 - bx + 1), min(CELL, y1 - by + 1), min(CELL, z1 - bz + 1))
        local = {(x - bx, y - by, z - bz): value for (x, y, z), value in blocks.items()}
        living = []
        for being in world.entities:
            ex, ey, ez = being["pos"]
            if ((int(ex // 1) - x0) // CELL, (int(ey // 1) - y0) // CELL, (int(ez // 1) - z0) // CELL) != key:
                continue
            nbt = {k: (api.NbtByte(v) if k == "PersistenceRequired" else v) for k, v in being["nbt"].items()}
            living.append({"pos": [api.NbtDouble(ex - bx), api.NbtDouble(ey - by), api.NbtDouble(ez - bz)],
                           "blockPos": [int(ex // 1) - bx, int(ey // 1) - by, int(ez // 1) - bz], "nbt": nbt})
        api.write_structure_nbt(piece_name(key), size, local, living)
        api.write_json(api.DATA / f"worldgen/template_pool/{piece_name(key)}.json", {
            "elements": [{"element": {"element_type": "minecraft:single_pool_element", "location": f"forja:{piece_name(key)}",
                                      "processors": "minecraft:empty", "projection": "rigid"}, "weight": 1}],
            "fallback": "minecraft:empty",
        })
    return {"start": piece_name(middle), "depth": max(depth.values()), "pieces": len(cells),
            "start_y": y0 + middle[1] * CELL, "origin": (x0, y0, z0), "middle": middle}


# ------------------------------------------------------------------------------------------ the sample

def sample_exterior():
    c = Canvas()
    base = 1
    for x in range(0, 48):
        for z in range(0, 30):
            r = _hash(x, 0, z, 20)
            c.put(x, 0, z, "coarse_dirt" if r < 0.2 else "podzol" if r < 0.3 else "grass_block")
    curtain_wall(c, 0, 31, 14, base, 14, decay=0.22, breach=(12, 4), footing=0)
    round_tower(c, 38, 12, 7, base, 24, decay=0.1, doors=[("north", 0)], footing=0)
    # the ditch of slag at the wall's foot
    for x in range(0, 48):
        for z in range(24, 30):
            c.put(x, 0, z, "magma_block" if _hash(x, 0, z, 21) < 0.3 else "basalt" if _hash(x, 0, z, 22) < 0.6 else "smooth_basalt")
    resolve(c)
    return c


def write(api, canvas, name):
    (x0, y0, z0), (x1, y1, z1) = canvas.bounds()
    blocks = {(x - x0, y - y0, z - z0): value for (x, y, z), value in canvas.blocks.items()}
    size = (x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
    assert max(size) <= 48, f"{name} is {size}: a template may not pass 48"
    api.write_structure_nbt(name, size, blocks, canvas.entities)
    return size


def clear_the_site(world, height=24, margin=9):
    """Air over the whole plan, moat and all, wherever nothing was built. A jigsaw piece only replaces what it
    writes: on real ground whatever was left unwritten stays as the world made it, and the first castle found in
    a normal world had a hill standing in its moat and a spruce in its lists. Sixteen blocks up is what a hill
    next to a surface-projected start can be expected to reach; taller than that and the site was wrong anyway."""
    (x0, _, z0), (x1, _, z1) = world.bounds()
    blocks = world.blocks
    air = ("minecraft:air", {}, None)
    for x in range(max(x0, -margin), min(x1, 200 + margin) + 1):
        for z in range(max(z0, -margin), min(z1, 200 + margin) + 1):
            for y in range(0, height):
                if (x, y, z) not in blocks:
                    blocks[(x, y, z)] = air


def underpin(world, depth=18, margin=9, ring=16):
    """Rock under the rim of the plan, down to `depth` below the courtyard. The second castle found in a normal world
    stood on the lip of a river valley with a third of its moat hanging in the air: a jigsaw piece does not ask what is
    under it. The middle needs none - the cellars are there - so it is a ring: the moat, the outer wall and a little
    more, rough stone that reads as the crag the castle was built on."""
    blocks = world.blocks
    low, high = -margin, 200 + margin
    for x in range(low, high + 1):
        for z in range(low, high + 1):
            if min(x - low, high - x, z - low, high - z) >= ring:
                continue
            column = [y for y in range(-depth, 0) if (x, y, z) in blocks]
            top = min(column) if column else 0
            for y in range(-depth, top):
                grain = _hash(x, y, z, 190)
                lump = _smooth(x, y, z, 6.0, 191)
                # ragged at the foot, so that where it does show it is a crag and not a box
                if y < -depth + 5 and lump * 6.0 < (-depth + 5 - y):
                    continue
                world.put(x, y, z, "deepslate" if lump > 0.62 else "cobbled_deepslate" if grain < 0.35 else "stone" if grain < 0.8 else "andesite")


def no_saplings(world):
    """Trodden earth for bare earth, wherever the sky is over it. The world plants its trees after it has placed its
    structures, on any grass or dirt it finds: the third castle found in a normal world had a spruce wood standing
    in its lower ward. A path and packed mud are not soil, and a ward that a garrison walks every day is both."""
    soil = {"minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:rooted_dirt"}
    changed = 0
    for (x, y, z), (name, _, _) in list(world.blocks.items()):
        if name in soil and world.name(x, y + 1, z) in (None, "minecraft:air"):
            grain = _hash(x, y, z, 195)
            patch = _smooth(x, y, z, 5.0, 196)
            world.put(x, y, z, "dirt_path" if patch < 0.55 and grain < 0.85 else "packed_mud" if grain < 0.6 else "gravel")
            changed += 1
    return changed


def generate(api):
    import castillo_obra

    noise_origin()
    exterior = sample_exterior()
    problems = validate(exterior)
    write(api, exterior, "bastion/muestra_exterior")

    world = castillo_obra.build()
    clear_the_site(world)
    underpin(world)
    no_saplings(world)
    resolve(world)
    problems += validate(world)
    docs = Path(__file__).resolve().parent.parent / "docs/castillo"
    for turns, view in ((0, "sur"), (2, "norte")):
        try:
            render(world, docs / f"render_{view}.png", turns)
        except Exception as problem:            # a picture for the builder's eye: never a reason not to build
            print("bastion: no render:", problem)
    for view, storey in (("sotano_1", (-11, -6)), ("sotano_2", (-26, -20)), ("planta_baja", (-1, 3))):
        try:
            render(world, docs / f"render_{view}.png", 0, scale=4, tilt=0.5, storey=storey)
        except Exception as problem:
            print("bastion: no render:", problem)
    info = cut(world, api, "bastion")
    shallow = Canvas()
    shallow.blocks = {position: value for position, value in world.blocks.items() if position[1] >= -3}
    shallow.entities = [being for being in world.entities if being["pos"][1] >= -3]
    for (x, y, z), value in world.blocks.items():
        if y == -5 and shallow.name(x, -3, z) == "minecraft:air":
            shallow.blocks[(x, -3, z)] = value if value[0] != "minecraft:lava" else ("minecraft:magma_block", {}, None)
    test_info = cut(shallow, api, "bastion_prueba")
    structure = {
        "type": "minecraft:jigsaw", "biomes": "#forja:has_structure/bastion_del_gremio", "max_distance_from_center": 128,
        "project_start_to_heightmap": "WORLD_SURFACE_WG", "size": min(20, test_info["depth"] + 2), "spawn_overrides": {},
        "start_height": {"absolute": test_info["start_y"] + 1}, "start_pool": f"forja:{test_info['start']}",
        "step": "surface_structures", "terrain_adaptation": "none", "use_expansion_hack": False,
    }
    api.write_json(api.DATA / "worldgen/structure/bastion_prueba.json", structure)
    api.write_json(api.DATA / "worldgen/structure/bastion_del_gremio.json", {
        "type": "minecraft:jigsaw",
        "biomes": "#forja:has_structure/bastion_del_gremio",
        "max_distance_from_center": 128,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": min(20, info["depth"] + 2),
        "spawn_overrides": {},
        # the middle piece starts underground: its floor is this far under the courtyard, which is what meets the surface
        "start_height": {"absolute": info["start_y"] + 1},
        "start_pool": f"forja:{info['start']}",
        "step": "surface_structures",
        "terrain_adaptation": "none",
        "use_expansion_hack": False,
    })
    # Very rare, and never within twelve chunks of the small castle: where the big one fits, the small one is its
    # outpost, not its neighbour. The test's clipped twin has no set of its own, so it is never generated.
    api.write_json(api.DATA / "worldgen/structure_set/bastion_del_gremio.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 920260920, "spacing": 140, "separation": 60,
                      "exclusion_zone": {"other_set": "forja:castillo_de_forja", "chunk_count": 12}},
        "structures": [{"structure": "forja:bastion_del_gremio", "weight": 1}],
    })
    api.write_json(api.DATA / "tags/worldgen/biome/has_structure/bastion_del_gremio.json", {"values": [
        "minecraft:dark_forest", "minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
        "minecraft:snowy_taiga", "minecraft:snowy_plains", "minecraft:swamp", "minecraft:badlands", "minecraft:plains",
    ]})
    ox, oy, oz = test_info["origin"]
    mx, my, mz = ox + test_info["middle"][0] * CELL, oy + test_info["middle"][1] * CELL, oz + test_info["middle"][2] * CELL
    layout = Path(__file__).resolve().parent.parent / "src/gametest/java/dev/forja/test/BastionLayout.java"
    layout.write_text(f"""package dev.forja.test;

/** Written by tools/castillo.py: where the castle's start piece stands in the plan's own coordinates. */
final class BastionLayout {{
	static final int START_X = {mx};
	static final int START_Y = {my};
	static final int START_Z = {mz};
	/** The real castle's pieces: forja:bastion/p_i_j_k holds the plan from ORIGIN + 48 * (i, j, k). */
	static final int ORIGIN_X = {info["origin"][0]};
	static final int ORIGIN_Y = {info["origin"][1]};
	static final int ORIGIN_Z = {info["origin"][2]};

	private BastionLayout() {{
	}}
}}
""", encoding="utf-8", newline="\n")
    print(f"bastion: {len(world.blocks)} blocks in {info['pieces']} pieces, tree {info['depth']} deep, middle {info['middle']}",
          "| PROBLEMS: " + "; ".join(problems[:6]) if problems else "| blocks check out")
    return problems
