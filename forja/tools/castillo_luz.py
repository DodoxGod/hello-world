"""Light. The castle is black stone under roofs: left to its furniture it cannot be read, and what cannot be
read is not eerie, it is a dark screenshot. (The first interiors came out that way: three chandeliers in a hall
forty-six blocks long.)

So light is not furniture here, it is a pass over the finished building. It works block light out the way the
game does - fifteen at a lantern, one less for every block it travels, stopped by whatever is solid - finds
every roofed floor that ends up too dark to see, and hangs a light for it: a **sconce** on the nearest wall (an
upturned stair with a lantern under it), a **lantern on a chain** from the ceiling where the walls are too far
off, a **lamp post** where the ceiling is too. Each one lights the floors round it, so the next is put where
that light runs out, and the castle ends up lit in pools with dusk between them: every room can be read, no
floor is left at zero for something to spawn on, and none of it is bright.

Crypt, ossuary and soul forge get soul lanterns: colder, dimmer, and nobody keeps them trimmed.
"""
from collections import deque

from castillo import HORIZONTAL, stairs

# what a roofed floor has to reach to be left alone, and how far from a dark floor a wall still counts as "its" wall
NEED = 6
# among the dead a soul lantern is all there is, and it reaches two thirds as far: asking the same of it hung
# two hundred of them in the crypt. Half as much there - still never zero, so still nothing spawns.
NEED_AMONG_THE_DEAD = 3
WALL_REACH = 3
# how far into the dark a light is aimed from the first dark floor met, nearest last
AIM = (3, 2, 0)

EMITTERS = {"lantern": 15, "soul_lantern": 10, "campfire": 15, "soul_campfire": 10, "lava": 15, "magma_block": 3, "torch": 14,
            "wall_torch": 14, "glowstone": 15, "shroomlight": 15, "sea_lantern": 15, "fire": 15, "jack_o_lantern": 15, "end_rod": 14,
            "ochre_froglight": 15, "verdant_froglight": 15, "pearlescent_froglight": 15, "soul_torch": 10, "soul_fire": 10,
            "forja:farol_de_pavesa": 15}
SEE_THROUGH = ("air", "_stairs", "_slab", "_wall", "_bars", "_pane", "_fence", "lantern", "chain", "torch", "banner", "carpet", "trapdoor",
               "_door", "campfire", "chest", "lava", "water", "fire", "cobweb", "_sign", "candle", "lightning_rod", "anvil", "grate",
               "ladder", "vine", "button", "lever", "pressure_plate", "head", "skull", "pot", "_bed", "bell", "grindstone", "lectern",
               "cauldron", "stonecutter", "brewing", "rail", "glass", "leaves", "jigsaw", "structure_void", "end_rod", "scaffolding", "hopper")

# plan boxes (x0, y0, z0, x1, y1, z1) where the dead are: docs/castillo/plano_v3_completo.png
SOUL_BOXES = ((72, -11, 17, 129, -2, 70), (146, -11, 20, 164, -2, 40), (131, -26, 80, 153, -12, 104))


def _opaque(name):
    if name is None:
        return False
    if name.startswith("forja:"):
        return False                         # the mod's benches and foundry are all models with gaps in them
    short = name.split(":", 1)[1]
    return not any(part in short for part in SEE_THROUGH)


def _emits(name, props):
    if name is None:
        return 0
    if name in EMITTERS:
        return EMITTERS[name]
    short = name.split(":", 1)[1]
    level = EMITTERS.get(short, 0)
    if level and "campfire" in short and props.get("lit") == "false":
        return 0
    return level


class Grid:
    """The castle as flat arrays, one cell of margin all round so that nobody has to ask where the edge is."""

    def __init__(self, world):
        (x0, y0, z0), (x1, y1, z1) = world.bounds()
        self.x0, self.y0, self.z0 = x0 - 1, y0 - 1, z0 - 1
        self.nx, self.ny, self.nz = x1 - x0 + 3, y1 - y0 + 3, z1 - z0 + 3
        self.sx, self.sy, self.sz = self.ny * self.nz, self.nz, 1
        size = self.nx * self.ny * self.nz
        # Whatever was never written is the world the castle is set into: earth under the courtyard, air over it.
        self.solid = bytearray(size)
        for ix in range(self.nx):
            base = ix * self.sx
            for iy in range(0, -self.y0):
                start = base + iy * self.sy
                self.solid[start:start + self.nz] = b"\x01" * self.nz
        self.taken = bytearray(size)         # something is here, solid or not: nothing else may be put in it
        self.light = bytearray(size)
        sources = []
        for (x, y, z), (name, props, _) in world.blocks.items():
            i = self.index(x, y, z)
            self.solid[i] = 1 if _opaque(name) else 0
            self.taken[i] = 0 if name == "minecraft:air" else 1
            level = _emits(name, props)
            if level:
                sources.append((i, level))
        # the margin is a wall: light and searches stop at it without being asked to
        for ix in range(self.nx):
            for iy in range(self.ny):
                for iz in (0, self.nz - 1):
                    self.solid[ix * self.sx + iy * self.sy + iz] = 1
        for ix in range(self.nx):
            for iz in range(self.nz):
                for iy in (0, self.ny - 1):
                    self.solid[ix * self.sx + iy * self.sy + iz] = 1
        for iy in range(self.ny):
            for iz in range(self.nz):
                for ix in (0, self.nx - 1):
                    self.solid[ix * self.sx + iy * self.sy + iz] = 1
        self.spread(sources)
        # The highest thing in each column: a floor under it is a roofed floor (the moat's is not, a cellar's is).
        # Anything counts, not only what is solid: the foundry's roof is all stairs, and asking for solid left
        # the one hall that most needed reading out of the count.
        self.roof = {}
        for (x, y, z), (name, _, _) in world.blocks.items():
            if name != "minecraft:air" and y > self.roof.get((x, z), -99):
                self.roof[(x, z)] = y

    def index(self, x, y, z):
        return (x - self.x0) * self.sx + (y - self.y0) * self.sy + (z - self.z0)

    def spread(self, sources):
        """Block light, as the game floods it: breadth first from every source at once, brightest first."""
        light, solid = self.light, self.solid
        steps = (self.sx, -self.sx, self.sy, -self.sy, 1, -1)
        queues = [deque() for _ in range(16)]
        for i, level in sources:
            if level > light[i]:
                light[i] = level
                queues[level].append(i)
        for level in range(15, 1, -1):
            queue = queues[level]
            lower = level - 1
            below = queues[lower]
            while queue:
                i = queue.popleft()
                if light[i] != level:
                    continue
                for step in steps:
                    j = i + step
                    if not solid[j] and light[j] < lower:
                        light[j] = lower
                        below.append(j)


def _rooms_only(floors, least=9):
    """Drops the floors nobody will ever stand on: the sill of an arrow slit, the top of a buttress under its
    own cap, the inside of a machicolation. Seven hundred of the castle's nine hundred "rooms" are one to three
    blocks of those, and each was getting a lantern. A room is floors joined to their neighbours, a step up or
    down allowed, and it has to be at least `least` of them."""
    cells = {(x, y, z) for y, x, z in floors}
    seen = set()
    kept = []
    for cell in cells:
        if cell in seen:
            continue
        seen.add(cell)
        room = [cell]
        queue = deque(room)
        while queue:
            x, y, z = queue.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for dy in (0, 1, -1):
                    other = (x + dx, y + dy, z + dz)
                    if other in cells and other not in seen:
                        seen.add(other)
                        room.append(other)
                        queue.append(other)
        if len(room) >= least:
            kept.extend((y, x, z) for x, y, z in room)
    return kept


def _soul(x, y, z):
    return any(x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1 for x0, y0, z0, x1, y1, z1 in SOUL_BOXES)


def anchor(world, reach=40):
    """Whatever hangs, hangs from something. A chain or a hanging lantern with nothing over it is carried on up,
    chain by chain, until it meets the roof: the furniture is written with drops guessed at, and under a gable the
    roof is never where the guess was. What meets nothing within `reach` is left alone and counted."""
    loose = 0
    for (x, y, z), (name, props, _) in list(world.blocks.items()):
        hangs = name == "minecraft:iron_chain" and props.get("axis") == "y" or name.endswith("lantern") and props.get("hanging") == "true"
        if not hangs or world.name(x, y + 1, z) not in (None, "minecraft:air"):
            continue
        top = next((k for k in range(1, reach) if world.name(x, y + k, z) not in (None, "minecraft:air")), None)
        if top is None:
            loose += 1
            continue
        for k in range(1, top):
            world.put(x, y + k, z, "iron_chain", {"axis": "y", "waterlogged": "false"})
    if loose:
        print(f"bastion light: {loose} hanging things with nothing over them")


def light(world):
    """Hangs what light the finished castle still needs. Returns what it hung, for the log."""
    anchor(world)
    grid = Grid(world)
    solid, taken, lit = grid.solid, grid.taken, grid.light
    sx, sy = grid.sx, grid.sy
    hung = {"sconce": 0, "chain": 0, "niche": 0, "post": 0}

    def free(i):
        return not solid[i] and not taken[i]

    def place(x, y, z, block, props=None):
        world.put(x, y, z, block, props)
        i = grid.index(x, y, z)
        taken[i] = 1
        return i

    def roofed(x, y, z):
        return all(grid.roof.get((x + dx, z + dz), -99) > y for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)))

    # a floor is the empty cell on top of something solid, with headroom, under a roof
    floors = []
    for (x, below, z) in list(world.blocks):
        y = below + 1
        i = grid.index(x, y, z)
        if solid[i - sy] and free(i) and not solid[i + sy] and roofed(x, y, z):
            floors.append((y, x, z))
    floors = _rooms_only(floors)
    floors.sort()

    standing = {(x, y, z) for y, x, z in floors}

    def hang(x, y, z):
        """A light for the floor at (x, y, z): on the nearest real wall, else from the ceiling, else on a post."""
        i = grid.index(x, y, z)
        lamp = "soul_lantern" if _soul(x, y, z) else "lantern"
        source = None
        # a sconce on the nearest stretch of real wall: four blocks of it, floor to bracket, so never over a doorway
        best = None
        for cx in range(x - WALL_REACH, x + WALL_REACH + 1):
            for cz in range(z - WALL_REACH, z + WALL_REACH + 1):
                c = grid.index(cx, y, cz)
                if not (solid[c - sy] and free(c) and free(c + sy) and free(c + 2 * sy) and free(c + 3 * sy)):
                    continue
                for side, (dx, dz) in HORIZONTAL.items():
                    w = grid.index(cx + dx, y, cz + dz)
                    if solid[w] and solid[w + sy] and solid[w + 2 * sy] and solid[w + 3 * sy]:
                        score = abs(cx - x) + abs(cz - z)
                        if best is None or score < best[0]:
                            best = (score, cx, cz, side)
        if best is not None:
            _, cx, cz, side = best
            wall = world.name(cx + HORIZONTAL[side][0], y + 2, cz + HORIZONTAL[side][1]) or ""
            family = "polished_blackstone_brick" if "blackstone" in wall else "deepslate_brick"
            place(cx, y + 3, cz, *stairs(f"{family}_stairs", side, top=True))
            source = place(cx, y + 2, cz, lamp, {"hanging": "true", "waterlogged": "false"})
            hung["sconce"] += 1
        else:
            # no wall near: from the ceiling, on as much chain as brings it down to a little over head height
            top = None
            for up in range(3, 15):
                if solid[i + up * sy]:
                    top = up
                    break
                if taken[i + up * sy]:
                    break
            if top is not None and top >= 4:
                drop = max(3, top - 7)
                if all(free(i + k * sy) for k in range(drop, top)):
                    for k in range(drop + 1, top):
                        place(x, y + k, z, "iron_chain", {"axis": "y", "waterlogged": "false"})
                    source = place(x, y + drop, z, lamp, {"hanging": "true", "waterlogged": "false"})
                    hung["chain"] += 1
            if source is None:
                # a low passage has neither the height for a bracket nor a ceiling to hang from: the lantern goes
                # into the wall, in a niche cut at shoulder height - if the wall is thick enough to keep it
                for cx, cz in ((x, z), (x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                    c = grid.index(cx, y, cz)
                    if not (solid[c - sy] and free(c) and free(c + sy)):
                        continue
                    for dx, dz in HORIZONTAL.values():
                        w = grid.index(cx + dx, y + 1, cz + dz)
                        behind = grid.index(cx + 2 * dx, y + 1, cz + 2 * dz)
                        if (solid[w] and solid[w - sy] and solid[w + sy] and solid[behind] and solid[w + grid.sx * dz + dx]
                                and solid[w - grid.sx * dz - dx] and world.nbt_free(cx + dx, y + 1, cz + dz)):
                            source = place(cx + dx, y + 1, cz + dz, lamp, {"hanging": "false", "waterlogged": "false"})
                            solid[source] = 0
                            hung["niche"] += 1
                            break
                    if source is not None:
                        break
            if source is None and free(i) and free(i + sy) and free(i + 2 * sy):
                place(x, y, z, "polished_blackstone_brick_wall" if y < 0 else "deepslate_brick_wall")
                place(x, y + 1, z, "polished_blackstone_brick_wall" if y < 0 else "deepslate_brick_wall")
                source = place(x, y + 2, z, lamp, {"hanging": "false", "waterlogged": "false"})
                hung["post"] += 1
        if source is not None:
            grid.spread([(source, 10 if lamp == "soul_lantern" else 15)])
        return source is not None

    # The floors come in order, so a dark one is always the near corner of whatever is dark beyond it. A light
    # hung right there spends half of itself on the wall behind. So it is aimed a few blocks on into the dark -
    # as far as still leaves this floor lit - and only if that did not do it is one hung over the floor itself.
    def need(x, y, z):
        return NEED_AMONG_THE_DEAD if _soul(x, y, z) else NEED

    for y, x, z in floors:
        i = grid.index(x, y, z)
        wanted = need(x, y, z)
        for ahead in AIM:
            if lit[i] >= wanted:
                break
            for ax, az in ((x + ahead, z + ahead), (x + ahead, z), (x, z + ahead)) if ahead else ((x, z),):
                if (ax, y, az) in standing and lit[grid.index(ax, y, az)] < wanted and hang(ax, y, az):
                    break

    mean = sum(lit[grid.index(x, y, z)] for y, x, z in floors) / max(1, len(floors))
    dark = sum(1 for y, x, z in floors if lit[grid.index(x, y, z)] == 0)
    dim = sum(1 for y, x, z in floors if lit[grid.index(x, y, z)] < need(x, y, z))
    print(f"bastion light: {len(floors)} roofed floors; hung {hung['sconce']} sconces, {hung['chain']} chain lanterns, {hung['niche']} niches, {hung['post']} lamp posts;"
          f" {dim} floors still short of what they need, {dark} at zero; mean floor light {mean:.1f}")
    return hung
