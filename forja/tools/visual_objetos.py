"""Forja's item and block look-overs (the visual pass of 2026-10-01), kept apart from generate_assets.py.

generate_assets.py calls `apply(module)` before it draws anything: the functions here take the place of the ones
there that drew the weakest sprites, so the regular pipeline regenerates everything and no PNG is ever painted
by hand. The rules every sprite here follows, which are vanilla's:

- 16x16, light from the top left: the lit edge of a shape faces up and left, its shadowed edge down and right;
- a ramp of three to five tones per material, never a single flat colour, and a dark outline around the whole;
- one clear silhouette that reads at 1x in a hotbar slot, before any detail.

Layered gear (the forged pieces) is drawn in grey: the game tints each layer with its part's material, so the
tones here are luminance only, and every layer keeps its pixels to itself (two layers never share one).
"""

import math

from PIL import Image

GA = None


# ---------------------------------------------------------------------------------------------- helpers
def from_rows(rows, palette):
    """An RGBA image from letter rows; '.' is empty."""
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, letter in enumerate(row):
            if letter != ".":
                image.putpixel((x, y), palette[letter] + (255,) if len(palette[letter]) == 3 else palette[letter])
    return image


def layered_rows(rows, palette):
    """Gear layers from letter rows: palette maps a letter to (slot, luminance)."""
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    return {(x, y): palette[letter] for y, row in enumerate(rows) for x, letter in enumerate(row) if letter != "."}


def stroke(pixels, start, end, profile, label_at, outline=40):
    """A bevelled bar along a line, lit on its top-left side: `profile(t)` gives the half width at distance t
    from `start`, `label_at(t)` the layer the pixel belongs to. Pixels already in `pixels` are left alone,
    so strokes drawn first stay on top. A dark outline closes it."""
    (x0, y0), (x1, y1) = start, end
    length = math.hypot(x1 - x0, y1 - y0)
    dx, dy = (x1 - x0) / length, (y1 - y0) / length
    # The normal that points to the shadowed side: right of the direction of travel, down and right.
    nx, ny = -dy, dx
    if nx + ny < 0:
        nx, ny = -nx, -ny
    inside = {}
    for y in range(16):
        for x in range(16):
            qx, qy = x + 0.5 - x0, y + 0.5 - y0
            t = qx * dx + qy * dy
            s = qx * nx + qy * ny
            if t < 0 or t > length:
                continue
            w = profile(t)
            if w is None or abs(s) > w:
                continue
            side = s / max(w, 0.01)
            if side < -0.45:
                lum = 236
            elif side > 0.45:
                lum = 120
            else:
                lum = 176
            inside[(x, y)] = (label_at(t), lum)
    for point, value in inside.items():
        pixels.setdefault(point, value)
    for (x, y), (label, _) in list(inside.items()):
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + ox, y + oy)
            if 0 <= q[0] < 16 and 0 <= q[1] < 16 and q not in pixels:
                pixels[q] = (label, outline)
    return pixels


# ---------------------------------------------------------------------------------------------- the chisel
def chisel():
    """The chisel: a round wooden handle, a steel collar where the tang goes into it, a slim shank, and a blade
    that flares to a bright cutting edge square across the tip. Steel (blade and collar) is 0, wood is 1.

    The old one was two three-pixel bands of the same width end to end, which at 1x read as a thin stick: the
    flare at the tip and the collar are what make it a chisel and not a skewer."""
    start, end = (0.6, 15.4), (15.4, 0.6)
    length = math.hypot(end[0] - start[0], end[1] - start[1])

    def profile(t):
        if t < 1.0:
            return 1.05
        if t < 8.6:
            return 1.45
        if t < 10.2:
            return 1.75
        if t < 12.6:
            return 0.75
        return 0.75 + (t - 12.6) * 0.12

    pixels = stroke({}, start, end, profile, lambda t: 1 if t < 8.6 else 0)
    # The cutting edge: the last row of steel across the tip is honed bright.
    for (x, y), (label, lum) in list(pixels.items()):
        t = (x + 0.5 - start[0]) * (end[0] - start[0]) / length + (y + 0.5 - start[1]) * (end[1] - start[1]) / length
        if label == 0 and lum > 40 and t > length - 2.2:
            pixels[(x, y)] = (0, 255 if lum >= 176 else 200)
        # The collar is a ring: one darker line where it meets the wood.
        if label == 0 and lum > 40 and 8.6 <= t < 9.3:
            pixels[(x, y)] = (0, min(lum, 150))
    return pixels


# ---------------------------------------------------------------------------------------------- the healing lantern
LANTERN_STAFF = [
    ".....SSSS.......",
    "....SS..SS......",
    "...SS....SS.....",
    "...SS.....E.....",
    "...SS....EEE....",
    "..SS....EEEEE...",
    "..SS....EGGGE.g.",
    "..SS....EGgGE...",
    ".SS.....EGGGE...",
    ".SS.....EGGGE...",
    ".SS.....EEEEE.g.",
    "SS.......EEE....",
    "SS....g.........",
    "SS..............",
    "S...............",
    "................",
]
"""The healing lantern on its crook (docs/CLASES.md): the crook two pixels thick so it reads as a staff and not a
wire, and a lantern big enough to see its cap, its cage and the light inside (G the núcleo, E the cage and the
chain, S the crook). The old one hung a three-pixel lantern off a one-pixel line."""


# ---------------------------------------------------------------------------------------------- the wolf armour
WOLF_ARMOR = [
    "................",
    "................",
    "............oo..",
    "......oooooHHo..",
    "....ooHHsHHsLHo.",
    "...oHHLLsLLsLLMo",
    "..oHLLLLsLLsLLMo",
    ".oHLLLLLsLLsLLDo",
    ".oLMMMMMsMMsMMDo",
    ".oRMMRMMsMRsMRDo",
    ".oDDDDDDDDDDDDo.",
    "..ooFFoooooFFo..",
    "....Ff.....Ff...",
    "....Ff.....Ff...",
    "....oo.....oo...",
    "................",
]


def wolf_armor():
    """The wolf armour, seen from the side: an arched shell of three riveted plates over the back, rising into
    a guard at the neck on the right, and two leather straps that go under the belly. Plate is 0, lining (the straps) is 1.

    It used to be vanilla's armadillo-scute harness re-tinted, whose pale scales and loose strap pixels came
    out as a washed-out smudge in every metal."""
    pixels = {}
    plate = {"H": 236, "L": 200, "M": 160, "D": 112, "s": 118, "R": 250, "o": 40}
    lining = {"F": 200, "f": 120, "o": 40}
    for y, row in enumerate(WOLF_ARMOR):
        for x, letter in enumerate(row):
            if letter == ".":
                continue
            if letter in "Ff" or (letter == "o" and y >= 14):
                pixels[(x, y)] = (1, lining[letter])
            else:
                pixels[(x, y)] = (0, plate[letter])
    return pixels


# ---------------------------------------------------------------------------------------------- the tool belt
BELT = [
    "................",
    "................",
    ".....oooooo.....",
    "...ooHHHHHHoo...",
    "..oHLddddddLMo..",
    ".oHLo......oMDo.",
    ".oLo........oDo.",
    "oLMo........oMDo",
    "oLMo........oMDo",
    "oLMMo......oMMDo",
    ".oLMMoIIIIoMMDo.",
    ".oDMMMIkkIMMDDo.",
    "..oDDMIkPIDDDo..",
    "....ooIIIIoo....",
    "......oooo......",
    "................",
]
BELT_COLOURS = {
    "o": (46, 26, 12), "H": (214, 160, 100), "L": (184, 124, 70), "M": (146, 92, 50), "D": (104, 62, 32),
    "d": (78, 46, 24),
    # The buckle, iron: its frame, the hole through it and the tongue.
    "I": (196, 200, 210), "k": (40, 38, 40), "P": (232, 236, 242),
}


def generate_belt_texture():
    """The tool belt: a leather belt buckled into a loop, seen from above and in front, with its iron buckle.
    The old icon was a flat brown slab with four white squares, which nobody took
    for a belt."""
    from_rows(BELT, BELT_COLOURS).save(GA.ASSETS / "textures/item/cinturon.png")


# ---------------------------------------------------------------------------------------------- blocks
def rgba(colour):
    return tuple(colour[:3]) + (255,)


def scaled(colour, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in colour[:3]) + (255,)


def grained(colour, rng, spread):
    return tuple(max(0, min(255, c + rng.randint(-spread, spread))) for c in colour[:3]) + (255,)


def smith_anvil_top():
    """The face of the fallen smith's anvil, seen from above.

    The old face was a round warm glow with the two holes either side of it, level with each other: from
    above it read as a face, two eyes and a pink nose. Here the face is polished steel lit along its left
    edge, the gold at both ends stays, the warmth is a faint band across the middle, and the hardy and
    pritchel holes sit one behind the other at the heel, where an anvil has them. The model squeezes the
    texture across (u) onto ten texels and lays it lengthwise (v) along the block.
    """
    import random
    rng = random.Random(551907)
    steel = (58, 54, 62)
    gold_l, gold_d = (226, 190, 104), (106, 82, 34)
    top = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            top.putpixel((x, y), grained(steel, rng, 5))
    for y in (0, 1, 14, 15):
        for x in range(16):
            top.putpixel((x, y), rgba(gold_l if y < 2 else gold_d))
    # The working face: lit edge, polished body, shadowed edge; a ramp of four greys across it.
    ramp = {1: (150, 146, 158), 2: (128, 124, 136), 13: (84, 80, 92), 14: (66, 62, 72)}
    for y in range(2, 14):
        for x in range(1, 15):
            base = ramp.get(x, (112, 108, 120))
            warm = max(0.0, 1.0 - abs(y - 7.0) / 3.5) * 0.55
            colour = (base[0] + int(46 * warm), base[1] + int(10 * warm), base[2] - int(12 * warm))
            top.putpixel((x, y), grained(colour, rng, 3))
    # Polish streaks running the length of the face, where the hammer slides.
    for y in range(3, 12):
        if y % 3 != 1:
            top.putpixel((4, y), rgba((172, 168, 180)))
    for y in range(4, 11, 2):
        top.putpixel((9, y), rgba((140, 136, 148)))
    # The horn end (v near 0) catches the most light.
    for x in range(2, 14):
        top.putpixel((x, 2), rgba((168, 164, 176) if x < 9 else (138, 134, 146)))
    # Hardy hole (square) and pritchel hole (round) one behind the other at the heel: their upper and left
    # walls in shadow, the lower and right walls lit, which is what makes a hole read as a hole.
    hole = (16, 14, 18)
    for y in (9, 10):
        for x in (6, 7, 8):
            top.putpixel((x, y), rgba(hole))
    for x in (6, 7, 8):
        top.putpixel((x, 11), rgba((176, 172, 184)))
    for y in (9, 10):
        top.putpixel((9, y), rgba((160, 156, 168)))
    top.putpixel((7, 12), rgba(hole))
    top.putpixel((8, 12), rgba(hole))
    top.putpixel((7, 13), rgba((150, 146, 158)))
    top.putpixel((8, 13), rgba((150, 146, 158)))
    return top


def forge_table_textures():
    """The forge table (mesa de forja), in the stone and iron of Direction A that Andy picked, made to read.

    The old faces put mid-grey iron on mid-grey deepslate, so the anvil, the tools and the drawer melted into
    the stone a few blocks away. Same materials, same layout; what changed is the contrast: the stone panels
    are a step darker, every iron shape has a lit edge and a dark one, and the shapes are the real ones: an
    anvil with its horn on the top, a hammer and a pair of tongs on the side rack, a drawer with a pull.
    """
    import random
    rng = random.Random(9781)
    vanilla = GA.vanilla
    iron_h, iron_l, iron, iron_d, iron_o = (226, 228, 236), (188, 190, 200), (140, 142, 152), (82, 84, 92), (34, 34, 40)
    wood_l, wood, wood_d = (176, 124, 76), (134, 90, 52), (86, 56, 30)
    slate = vanilla("block/polished_deepslate.png")
    rough = vanilla("block/deepslate.png")

    def panel(base, factor=0.78):
        image = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                image.putpixel((x, y), scaled(base.getpixel((x, y)), factor))
        return image

    def band(image):
        """The iron band across the top of a side and the legs down both ends, each with a lit and a dark edge."""
        for x in range(16):
            image.putpixel((x, 0), rgba(iron_h))
            image.putpixel((x, 1), rgba(iron_l))
            image.putpixel((x, 2), rgba(iron_d))
        for y in range(3, 16):
            image.putpixel((0, y), rgba(iron_l))
            image.putpixel((1, y), rgba(iron))
            image.putpixel((14, y), rgba(iron_d))
            image.putpixel((15, y), rgba(iron_o))
        for x in range(2, 14):
            image.putpixel((x, 15), rgba(iron_o))
        # Rivets through the band.
        for x in (3, 12):
            image.putpixel((x, 1), rgba(iron_h))
            image.putpixel((x, 2), rgba(iron_o))
        return image

    # ---- the top: the stone in an iron rim, and a sunken bed with an anvil in it.
    top = panel(slate, 0.92)
    for i in range(16):
        top.putpixel((i, 0), rgba(iron))
        top.putpixel((0, i), rgba(iron))
        top.putpixel((i, 15), rgba(iron_o))
        top.putpixel((15, i), rgba(iron_o))
    for y in range(3, 13):
        for x in range(3, 13):
            top.putpixel((x, y), scaled(rough.getpixel((x, y)), 0.50))
    for i in range(3, 13):
        top.putpixel((i, 3), rgba(iron_o))
        top.putpixel((3, i), rgba(iron_o))
        top.putpixel((i, 12), rgba(iron_l))
        top.putpixel((12, i), rgba(iron_l))
    anvil = [
        "..........",
        "...HHHHHH.",
        "HHHLLLLLLo",
        ".oLLLLLLLo",
        "..oMMMMMMo",
        "...oooooo.",
    ]
    colours = {"H": iron_h, "L": iron_l, "M": iron, "o": iron_o}
    for dy, row in enumerate(anvil):
        for dx, letter in enumerate(row):
            if letter != ".":
                top.putpixel((3 + dx, 5 + dy), rgba(colours[letter]))
    top.putpixel((10, 7), rgba(iron_o))
    top.putpixel((11, 8), rgba(iron_l))

    # ---- the side: a hammer and a pair of tongs on the rack.
    side = band(panel(slate))
    hammer = [(x, 5) for x in range(4, 9)] + [(x, 6) for x in range(4, 9)]
    for x, y in hammer:
        side.putpixel((x, y), rgba(iron_h if y == 5 else iron))
    side.putpixel((8, 6), rgba(iron_d))
    side.putpixel((4, 7), rgba(iron_o))
    for x in range(5, 9):
        side.putpixel((x, 7), rgba(iron_o))
    for y in range(7, 14):
        side.putpixel((6, y), rgba(wood_l))
        side.putpixel((7, y), rgba(wood_d))
    side.putpixel((6, 13), rgba(wood))
    tongs = [((10, 5), (12, 13)), ((12, 5), (10, 13))]
    for (x0, y0), (x1, y1) in tongs:
        for step in range(9):
            t = step / 8
            x, y = round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)
            side.putpixel((x, y), rgba(iron_l if x0 < x1 else iron))
    side.putpixel((11, 9), rgba(iron_h))
    side.putpixel((11, 10), rgba(iron_o))

    # ---- the front: a drawer, sunk into the stone, with its pull.
    front = band(panel(slate))
    for y in range(5, 13):
        for x in range(3, 13):
            front.putpixel((x, y), scaled(rough.getpixel((x, y)), 0.55))
    for x in range(3, 13):
        front.putpixel((x, 5), rgba(iron_o))
        front.putpixel((x, 12), rgba(iron_l))
    for y in range(5, 13):
        front.putpixel((3, y), rgba(iron_o))
        front.putpixel((12, y), rgba(iron_l))
    for x in range(6, 10):
        front.putpixel((x, 8), rgba(iron_h))
        front.putpixel((x, 9), rgba(iron_d))
    front.putpixel((5, 8), rgba(iron_l))
    front.putpixel((10, 8), rgba(iron_l))
    front.putpixel((5, 9), rgba(iron_o))
    front.putpixel((10, 9), rgba(iron_o))
    folder = GA.ASSETS / "textures/block"
    top.save(folder / "mesa_de_forja_top.png")
    side.save(folder / "mesa_de_forja_side.png")
    front.save(folder / "mesa_de_forja_front.png")


def assembler_sides():
    """The assembler's side window, redrawn so the press reads at 1x.

    The old window held a two-pixel piston rod over a one-row anvil, and at 1x the thin lines inside a dark
    frame read as a ribcage. Now the press is the size of the window: a steel head on its rod coming down,
    and under it an anvil with a face, a waist and a foot, all with a lit top and a dark underside; lit,
    the piece on the anvil and the anvil's face glow.
    """
    import random
    rng = random.Random(52611)
    steel, light, mid, dark = (118, 120, 128), (190, 192, 202), (140, 142, 150), (60, 62, 70)
    brass, brass_dark = (206, 162, 80), (120, 86, 34)
    hot, hot_light = (255, 132, 40), (255, 214, 140)
    folder = GA.ASSETS / "textures/block"
    for lit in (False, True):
        side = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                side.putpixel((x, y), grained(steel, rng, 7))
        for i in range(16):
            side.putpixel((i, 0), rgba(light))
            side.putpixel((0, i), rgba(light))
            side.putpixel((i, 15), rgba(dark))
            side.putpixel((15, i), rgba(dark))
        for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
            side.putpixel((x, y), rgba(brass))
        # The window, sunk: dark frame on the top and left, lit lip on the bottom and right.
        for y in range(2, 14):
            for x in range(3, 13):
                side.putpixel((x, y), rgba((26, 24, 28)))
        for x in range(3, 13):
            side.putpixel((x, 2), rgba((40, 40, 46)))
            side.putpixel((x, 13), rgba(light))
        for y in range(2, 14):
            side.putpixel((3, y), rgba((40, 40, 46)))
            side.putpixel((12, y), rgba(light))
        # The rod and the press head.
        for y in (3, 4):
            side.putpixel((7, y), rgba(light))
            side.putpixel((8, y), rgba(dark))
        for x in range(5, 11):
            side.putpixel((x, 5), rgba(light))
            side.putpixel((x, 6), rgba(mid))
            side.putpixel((x, 7), rgba(dark))
        # The piece on the anvil, and the anvil: face, waist, foot.
        if lit:
            for x in range(6, 10):
                side.putpixel((x, 8), rgba(hot_light if x in (7, 8) else hot))
        for x in range(4, 12):
            side.putpixel((x, 9), rgba(hot if lit and 5 < x < 10 else light))
            side.putpixel((x, 10), rgba(mid))
        side.putpixel((4, 10), rgba(dark))
        for x in range(6, 10):
            side.putpixel((x, 11), rgba(mid if x < 8 else dark))
        for x in range(5, 11):
            side.putpixel((x, 12), rgba(light if x < 8 else mid))
        for x in range(3, 16, 5):
            side.putpixel((x, 14), rgba(brass))
            side.putpixel((x, 15), rgba(brass_dark))
        side.save(folder / f"montadora_side{'_lit' if lit else ''}.png")


CASTING_BOXES = {
    # (light, dark, sand), as generate_melt_tank_assets draws the boxes' frames.
    "caja_de_moldeo": ((196, 152, 106), (96, 64, 40), (198, 178, 140)),
    "caja_de_moldeo_de_acero": ((188, 190, 200), (70, 72, 80), (206, 194, 168)),
    "caja_de_moldeo_de_damasco": ((168, 170, 186), (52, 54, 64), (192, 186, 176)),
}


def casting_box_tops():
    """The casting boxes' tops: sand in the flask, a round pouring cup, a runner and two bar cavities.

    The old top put a square cup in the corner, a diagonal runner and a flat bar under it, and at a glance the
    three made the outline of a hammer drawn on the sand. Here every cut into the sand has its upper and left
    wall in shadow and its lower and right lip lit, the cup is round, and the cavity is the shape of what comes
    out, two bars fed from the cup by a runner down the side. Lit, the cup, the runner and the bars glow.
    """
    import random
    rng = random.Random(771221)
    glow, glow_hot = (255, 170, 70), (255, 226, 150)
    cold = (44, 36, 30)
    for name, (light, dark, sand) in CASTING_BOXES.items():
        for lit in (False, True):
            top = Image.new("RGBA", (16, 16))
            for y in range(16):
                for x in range(16):
                    top.putpixel((x, y), grained(sand, rng, 9))
            for i in range(16):
                top.putpixel((i, 0), rgba(light))
                top.putpixel((0, i), rgba(light))
                top.putpixel((i, 15), rgba(dark))
                top.putpixel((15, i), rgba(dark))
            # The cup, at the left end, where you pour.
            cx, cy = 3.5, 3.5
            for y in range(1, 7):
                for x in range(1, 7):
                    d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                    if d < 1.5:
                        top.putpixel((x, y), rgba(glow_hot if lit and d < 0.8 else glow if lit else cold))
                    elif d < 2.5:
                        upper_left = (x + 0.5 - cx) + (y + 0.5 - cy) < 0
                        top.putpixel((x, y), scaled(sand, 0.50 if upper_left else 1.12))
            # Two bars lying across the box, fed from the cup by a runner down the left side.
            cavity = {(x, y) for y in (7, 8, 11, 12) for x in range(6, 14)}
            gate = {(3, y) for y in range(5, 12)} | {(4, 8), (5, 8), (4, 12), (5, 12)}
            for (x, y) in cavity | gate:
                if lit:
                    heat = 1.0 - abs(y - (7.5 if y < 10 else 11.5)) / 1.6
                    top.putpixel((x, y), (255, int(140 + 90 * heat), int(40 + 60 * heat), 255))
                else:
                    top.putpixel((x, y), rgba(cold))
            for (x, y) in cavity | gate:
                for (ox, oy, factor) in ((-1, 0, 0.48), (0, -1, 0.48), (1, 0, 1.14), (0, 1, 1.14)):
                    q = (x + ox, y + oy)
                    if q not in cavity and q not in gate and 0 < q[0] < 15 and 0 < q[1] < 15                             and math.hypot(q[0] + 0.5 - cx, q[1] + 0.5 - cy) >= 2.5:
                        top.putpixel(q, scaled(sand, factor))
            # The riser at the far corner, so the air has somewhere to go.
            top.putpixel((13, 3), rgba(glow if lit else cold))
            top.putpixel((12, 3), scaled(sand, 0.48))
            top.putpixel((13, 2), scaled(sand, 0.48))
            top.putpixel((14, 3), scaled(sand, 1.14))
            top.putpixel((13, 4), scaled(sand, 1.14))
            top.save(GA.ASSETS / f"textures/block/{name}_top{'_lit' if lit else ''}.png")


def _after(original, extra):
    def run(*args, **kwargs):
        result = original(*args, **kwargs)
        extra()
        return result
    run.__name__ = original.__name__
    return run


def apply(ga):
    """Puts this module's drawings in place of generate_assets' own, before anything is generated."""
    global GA
    GA = ga
    ga.chisel = chisel
    ga.TOOL_LAYERS["cincel"] = chisel
    ga.LANTERN_STAFF[:] = LANTERN_STAFF
    ga.generate_belt_texture = generate_belt_texture
    ga.split_wolf_armor = lambda state: wolf_armor()
    ga.TOOL_LAYERS["armadura_de_lobo"] = wolf_armor
    ga.PART_LAYERS["placa_lobo"] = lambda: ga.centered(wolf_armor(), 0)
    ga.generate_block_textures = _after(ga.generate_block_textures, forge_table_textures)
    ga.generate_smith_anvil_assets = _after(ga.generate_smith_anvil_assets,
                                            lambda: smith_anvil_top().save(GA.ASSETS / "textures/block/yunque_del_herrero_top.png"))
    ga.generate_assembler_assets = _after(ga.generate_assembler_assets, assembler_sides)
    ga.generate_melt_tank_assets = _after(ga.generate_melt_tank_assets, casting_box_tops)
