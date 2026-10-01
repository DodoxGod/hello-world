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


def apply(ga):
    """Puts this module's drawings in place of generate_assets' own, before anything is generated."""
    global GA
    GA = ga
    ga.chisel = chisel
    ga.TOOL_LAYERS["cincel"] = chisel
    ga.LANTERN_STAFF[:] = LANTERN_STAFF
    ga.generate_belt_texture = generate_belt_texture
