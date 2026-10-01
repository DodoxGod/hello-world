"""Light. The castle is black stone under roofs: left to its furniture it cannot be read, and what cannot be
read is not eerie, it is a dark screenshot. (The first interiors came out that way: three chandeliers in a hall
forty-six blocks long.)

So light is not furniture here, it is a pass over the finished building. It works block light out the way the
game does - fifteen at a lantern, one less for every block it travels, stopped by whatever is solid - finds
every roofed floor that ends up too dark to see, and hangs a light for it: a **sconce** on the nearest wall (a
bracket of the wall's brick with a lantern under it), a **lantern on a chain** from the ceiling - or the roof, or
the gallery - where the walls are too far off, a lantern in a **niche** in a low passage, a sconce on a wall further
off, and only where there is none of those a **lamp post** (none, as the castle stands: Andy, 2026-09-29, found
halls with posts standing in the middle of them). Each one lights the floors round it, so the next is put where
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
# how far a sconce may be from the floor it is for before a lamp post is stood on the floor itself
FAR_WALL = 6
# how high a chain may go looking for something to hang from: the foundry is open to its ridge, thirty up
CHAIN_REACH = 32
# chain lanterns stand at least this far apart (horizontally, on the same storey): aimed at every dark floor, a
# great hall grew a forest of them (the second pass's screenshots, 2026-09-29), where a few light it as well
CHAIN_SPACING = 7
# a lamp post standing in the middle of a room is the last resort, and Andy found them ugly: none. A floor that no
# wall, ceiling or niche can light stays dimmer, never dark (NEED is well above the monsters' 0).
POSTS = False
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


# what a chain cannot be hung from: things that hang or stand themselves, and what is not there
NO_HOLD = ("lantern", "chain", "torch", "banner", "carpet", "candle", "cobweb", "rail", "pressure_plate", "button",
           "ladder", "vine", "fire", "water", "lava", "_sign", "head", "skull", "farol")


def _holds(name):
    return name not in (None, "minecraft:air") and not any(part in name.split(":", 1)[1] for part in NO_HOLD)


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
    hung = {"sconce": 0, "chain": 0, "niche": 0, "floor": 0, "post": 0}
    chains = []

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
        # and not the top of a wall: a partition that stops short of the roof is a line of "floor" a block wide, and
        # had a lamp post stood on it in the roof space over every room of the west range
        ridge = ((not solid[i - sy + grid.sx] and not solid[i - sy - grid.sx])
                 or (not solid[i - sy + 1] and not solid[i - sy - 1]))
        if solid[i - sy] and free(i) and not solid[i + sy] and roofed(x, y, z) and not ridge:
            floors.append((y, x, z))
    floors = _rooms_only(floors)
    # The star portal's 3 x 3 hole in the Deep Forge (castillo_sotanos.rotunda) is where the portal lights, not a
    # floor: a chain lantern hung over it came down into the portal. The committed castle never had that lantern
    # (it was taken out of the piece by hand), so only a regenerated one grew it back.
    hole = set()
    for (mx, my, mz), (name, props, _) in list(world.blocks.items()):
        if name == "forja:mensula_estelar" and props.get("facing") == "west":
            hole.update((mx - 2 + dx, my, mz + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1))
    floors = [(y, x, z) for y, x, z in floors if (x, y, z) not in hole]
    floors.sort()

    standing = {(x, y, z) for y, x, z in floors}

    def sconce(x, y, z, reach, lamp):
        """A sconce on the nearest stretch of real wall within `reach`: four blocks of it, floor to bracket, so never
        over a doorway. A bracket of the wall's own brick for the lantern to hang from: an upside-down stair with a
        lantern under it is a thing no player could build (Andy, 2026-09-29)."""
        best = None
        for cx in range(x - reach, x + reach + 1):
            for cz in range(z - reach, z + reach + 1):
                c = grid.index(cx, y, cz)
                if not (solid[c - sy] and free(c) and free(c + sy) and free(c + 2 * sy) and free(c + 3 * sy)):
                    continue
                for side, (dx, dz) in HORIZONTAL.items():
                    w = grid.index(cx + dx, y, cz + dz)
                    if solid[w] and solid[w + sy] and solid[w + 2 * sy] and solid[w + 3 * sy]:
                        score = abs(cx - x) + abs(cz - z)
                        if best is None or score < best[0]:
                            best = (score, cx, cz, side)
        if best is None:
            return None
        _, cx, cz, side = best
        wall = world.name(cx + HORIZONTAL[side][0], y + 2, cz + HORIZONTAL[side][1]) or ""
        place(cx, y + 3, cz, "polished_blackstone_bricks" if "blackstone" in wall else "deepslate_bricks")
        hung["sconce"] += 1
        return place(cx, y + 2, cz, lamp, {"hanging": "true", "waterlogged": "false"})

    def hang(x, y, z):
        """A light for the floor at (x, y, z): on the nearest real wall, else from the ceiling, else in a niche, else on
        a wall further off, and only when there is none of those on a post."""
        i = grid.index(x, y, z)
        lamp = "soul_lantern" if _soul(x, y, z) else "lantern"
        source = sconce(x, y, z, WALL_REACH, lamp)
        if source is None:
            # no wall near: from the ceiling, on as much chain as brings it down to a little over head height. A roof of
            # stairs and slabs, a gallery of planks and fences, a beam of wall: whatever is over it carries a chain. Only
            # asking for a solid block put a lamp post in the middle of every hall under a pitched roof (the mess had two).
            top = None
            for up in range(3, CHAIN_REACH):
                if y + up >= grid.y0 + grid.ny - 1:
                    break
                if solid[i + up * sy]:
                    top = up
                    break
                if taken[i + up * sy]:
                    if _holds(world.name(x, y + up, z)):
                        top = up
                    break
            crowded = any(abs(cx - x) < CHAIN_SPACING and abs(cz - z) < CHAIN_SPACING and abs(cy - y) <= 3 for cx, cy, cz in chains)
            if top is not None and top >= 3 and not crowded:
                # never more than five over the floor however high the roof, and under a low one (the ice house's
                # dome) straight under it, over the head
                drop = min(top - 1, max(3, min(top - 7, 5)))
                if all(free(i + k * sy) for k in range(drop, top)):
                    for k in range(drop + 1, top):
                        place(x, y + k, z, "iron_chain", {"axis": "y", "waterlogged": "false"})
                    source = place(x, y + drop, z, lamp, {"hanging": "true", "waterlogged": "false"})
                    hung["chain"] += 1
                    chains.append((x, y, z))
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
        if source is None:
            # a wall further off still lights the middle of a room better than a post standing in it does
            source = sconce(x, y, z, FAR_WALL, lamp)
        if source is None and free(i) and free(i + sy) and not free(i + 2 * sy):
            # a room too low for a post (the ice house): the lantern stands on the floor
            source = place(x, y, z, lamp, {"hanging": "false", "waterlogged": "false"})
            hung["floor"] += 1
        if source is None and free(i) and free(i + sy) and free(i + 2 * sy) and POSTS:
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
    print(f"bastion light: {len(floors)} roofed floors; hung {hung['sconce']} sconces, {hung['chain']} chain lanterns, {hung['niche']} niches, {hung['floor']} on the floor, {hung['post']} lamp posts;"
          f" {dim} floors still short of what they need, {dark} at zero; mean floor light {mean:.1f}")
    return hung
