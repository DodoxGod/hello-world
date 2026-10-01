"""Forja's ingots and repair kits, drawn by hand: one family of bars, one light, one ramp per metal.

Two shapes, chosen by Andy on 2026-10-01 from tools/lingotes_propuesta.py's sheet:

* **variant_a(name)** - the ingot. Vanilla's diagonal bar (it sits beside an iron ingot in a chest without
  looking foreign), with each alloy's mark on the lit top face. Every alloy ingot, oricalco and the
  tempering bar are drawn with it (generate_assets.generate_alloy_textures, generate_temper_ingot_texture,
  dimension_assets.portal).
* **variant_b(name)** - the guild-sealed bar: a cast bar seen from above, the guild's cartouche stamped into
  its top face and the metal's glyph raised inside it. It is the **repair kit** of that metal
  (kit_de_reparacion_<material>, generate_assets.generate_repair_kit_textures).

What every bar shares: one silhouette per shape, drawn here instead of re-tinting Minecraft's iron ingot
(re-tinting carried the iron ingot's noise over, which is what made the marks fight the surface); one light
from the top left read off one five-tone ramp per metal (0 outline in shadow, 1 front face, 2 near end,
3 top face, 4 rim), shadows leaning cool and highlights warm; and a clean bevel where the top meets the front.

The metal tables (ALLOY_COLORS, ALLOY_SIDEWAYS, ALLOY_RAMPS, MATERIAL_COLORS) live in generate_assets. This
module is imported from inside generate_assets' own functions, so it looks the module up when it needs it
(_gen) instead of importing it at the top, which would load generate_assets a second time.
"""

import sys
import zlib
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import dimension_assets as dim  # noqa: E402  (it imports nothing of ours at the top)


def _gen():
    """generate_assets as it is already loaded (as itself or as the script being run), else imported."""
    for name in ("generate_assets", "__main__"):
        module = sys.modules.get(name)
        if module is not None and hasattr(module, "ALLOY_COLORS") and hasattr(module, "MATERIAL_COLORS"):
            return module
    import generate_assets
    return generate_assets


# ---------------------------------------------------------------- colour

def rgb(value):
    return ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def ramp_of(base):
    """Five tones for one metal, darkest first. Tone 3 is the colour of the metal itself (the top face).

    The shadow tones are the metal scaled down and leaned toward a cool violet; the rim is the metal
    leaned toward a warm white. Scaling (rather than moving lightness in HLS) keeps a pale, faintly
    tinted metal such as star steel pale in shadow instead of turning it into saturated blue.
    """
    scale = lambda f: clamp(v * f for v in base)
    return [
        mix(scale(0.36), (28, 24, 48), 0.22),
        mix(scale(0.60), (44, 42, 70), 0.10),
        scale(0.80),
        base,
        mix(clamp(v * 1.12 for v in base), (255, 250, 236), 0.42),
    ]


def ramp_between(dark, light):
    a, b = rgb(dark), rgb(light)
    return [mix(a, b, i / 4) for i in range(5)]


ORICALCO_RAMP = [dim.ramp(dim.ORICALCO, t) for t in (0.05, 0.35, 0.55, 0.72, 1.0)]
TEMPLE_RAMP = [(58, 54, 52), (104, 98, 94), (148, 142, 136), (192, 186, 178), (230, 224, 214)]
TEMPLE_HEAT = [(150, 52, 22), (208, 86, 24), (244, 140, 34), (255, 196, 96), (255, 244, 208)]


def painter(name):
    """A function (x, tone) -> colour for this metal, so a bar can change colour along its length."""
    if name in _gen().ALLOY_SIDEWAYS:
        left, right = (ramp_of(rgb(c)) for c in _gen().ALLOY_SIDEWAYS[name])
        return lambda x, tone: mix(left[tone], right[tone], max(0.0, min(1.0, (x - 1) / 13)))
    if name == "lingote_de_temple":
        # Cold on the left, white-hot on the right, the front edge of the heat a little ragged.
        ragged = [0.0, 0.1, -0.1, 0.05, 0.0, -0.05, 0.1, 0.0, -0.1, 0.05, 0.0, 0.1, -0.05, 0.0, 0.05, 0.0]

        def temple(x, tone):
            heat = max(0.0, min(1.0, (x - 8.5) / 5.0 + ragged[x]))
            return mix(TEMPLE_RAMP[tone], TEMPLE_HEAT[tone], heat)
        return temple
    if name == "oricalco":
        return lambda x, tone: ORICALCO_RAMP[tone]
    if name in _gen().ALLOY_RAMPS:
        r = ramp_between(*_gen().ALLOY_RAMPS[name])
        return lambda x, tone: r[tone]
    gen = _gen()
    # An alloy by its ingot's colour; a vanilla metal (the repair kits of iron, gold, copper and netherite)
    # by its material's colour.
    colour = gen.ALLOY_COLORS[name] if name in gen.ALLOY_COLORS else gen.MATERIAL_COLORS[name]
    r = ramp_of(rgb(colour))
    return lambda x, tone: r[tone]


# ---------------------------------------------------------------- the two silhouettes
#
# o  outline in shadow (tone 0)     e  outline in light (tone 1)     F  front face (tone 1)
# L  near end (tone 2)              T  top face (tone 3)             R  rim / bevel (tone 4)
# M  the near corner's lit edge (tone 3)  G  B's front face (tone 2)   D  B's right slope (tone 1)
# Letters in lower case inside B's cartouche: s wall in shadow (0), f recessed floor (1), w lit wall (3).

TONE = {"o": 0, "e": 1, "F": 1, "D": 1, "L": 2, "G": 2, "T": 3, "M": 3, "R": 4,
        "s": 0, "f": 1, "w": 3, "c": 2}

SHAPE_A = [
    "................",
    "................",
    "..........ee....",
    ".......eeeTTo...",
    "....eeeTTTTTTo..",
    ".eeeTTTTTTTTTTo.",
    "eRRTTTTTTTTTTRRo",
    "eLRTTTTTTTRRRFFo",
    "eLLRTTTRRRFFFFFo",
    "eLLLRRRFFFFFFFFo",
    "eLLLMFFFFFFFFoo.",
    ".eLLMFFFFFooo...",
    "..eLMFFooo......",
    "...eooo.........",
    "................",
    "................",
]

SHAPE_B = [
    "................",
    "................",
    "...eeeeeeeeee...",
    "..eRRTTTTTTTTo..",
    "..LTTssssssTTD..",
    ".LLTTsffffwTTDo.",
    ".LLTTsffffwTTDo.",
    ".LLTTsffffwTTDo.",
    ".LLTTcwwwwwTTDo.",
    "LLLRRRRRRRRRRDDo",
    "LLLGGGGGGGGGGDDo",
    "LLLGGGGGGGGGGDDo",
    ".oooooooooooooo.",
    "................",
    "................",
    "................",
]

# The glyph raised inside B's cartouche, 4 wide by 3 high, one per metal. '#' is lit metal.
GLYPHS = {
    "bronce": ("....", ".##.", "...."),
    "laton": (".##.", "....", ".##."),
    "peltre": ("####", "....", "####"),
    "acero": ("####", ".##.", ".##."),
    "electro": ("#..#", ".##.", "#..#"),
    "acero_refractario": ("##.#", "....", "#.##"),
    "cinerio": (".#..", ".##.", "####"),
    "voltaico": ("..##", ".##.", "##.."),
    "damasco": ("#.#.", ".#.#", "#.#."),
    "acero_estelar": (".##.", "####", ".##."),
    "obsidiacero": ("#...", "###.", "####"),
    "almacero": ("#..#", "#..#", ".##."),
    "vidriacero": ("#..#", "....", "#..#"),
    "solacero": (".##.", "#..#", ".##."),
    "lunacero": (".###", "#...", ".###"),
    "acero_vivo": (".#..", "####", "..#."),
    "oricalco": ("#..#", "####", "#..#"),
    "lingote_de_temple": None,
    # The vanilla metals, which have a repair kit but no Forja ingot.
    "cobre": ("#.##", "#...", "####"),
    "hierro": ("####", "#..#", "####"),
    "oro": (".##.", ".##.", "####"),
    "netherita": ("#.#.", "####", ".#.#"),
}


def glyph_of(name):
    """The metal's glyph; a metal added later without one gets a mirrored glyph read off its name, so two new
    alloys never share an empty cartouche (the tempering bar's is empty on purpose)."""
    if name in GLYPHS:
        return GLYPHS[name]
    seed = zlib.crc32(name.encode("utf-8"))
    taken = {g for g in GLYPHS.values() if g}
    while True:
        rows = []
        for row in range(3):
            left = (seed >> (row * 2)) & 0b11
            half = ("#" if left & 0b10 else ".") + ("#" if left & 0b01 else ".")
            rows.append(half + half[::-1])
        glyph = tuple(rows)
        if glyph not in taken and any("#" in r for r in rows):
            return glyph
        seed = (seed * 1103515245 + 12345) & 0xFFFFFFFF


def draw(shape, name):
    paint_at = painter(name)
    bar = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch in TONE:
                bar.putpixel((x, y), paint_at(x, TONE[ch]) + (255,))
    return bar


def cells(shape, letters):
    return {(x, y) for y, row in enumerate(shape) for x, ch in enumerate(row) if ch in letters}


# ---------------------------------------------------------------- variant A: vanilla's bar, the mark on top

FACE_A = cells(SHAPE_A, "T")


def axis(c, step=1):
    """Points along the bar's length on the top face, c being how far down the face (8.5 is its middle)."""
    return [(x, int(round(c - 0.42 * x))) for x in range(0, 16, step)]


def mark_a(bar, name):
    """The alloy's mark on the lit top face. One rule makes them read at 1x: every dark pixel has a lit
    one beside it, and the dark is the darkest tone of the metal, or on pale metals it sinks into the face."""
    paint_at = painter(name)
    dark = 0

    def put(x, y, tone=None, colour=None):
        if (x, y) in FACE_A and y > 3:
            bar.putpixel((x, y), (colour or paint_at(x, tone)) + (255,))

    def pit(x, y, w=1):
        """A dent: its upper wall in shadow, its lower lip catching the light from the top left."""
        for i in range(w):
            put(x + i, y, dark)
            put(x + i, y + 1, 4)

    if name == "bronce":
        # Verdigris: the green bronze grows where it has been handled, specks on both ends.
        for (x, y) in ((11, 5), (12, 5), (5, 7)):
            put(x, y, colour=(70, 140, 106))
        for (x, y) in ((11, 4), (6, 7)):
            put(x, y, colour=(132, 196, 156))
    elif name == "laton":
        # Brass: the assayer's punch, one wide dent in the middle of the bar.
        pit(7, 6, 3)
    elif name == "peltre":
        # Pewter: soft and dull, three casting pits.
        for (x, y) in ((5, 7), (8, 5), (11, 5)):
            pit(x, y)
    elif name == "acero":
        # Steel: the fold seam along the bar with its lit lip, and nothing else.
        for (x, y) in axis(7.6):
            pit(x, y)
    elif name == "electro":
        # Electrum: two metals that never quite mixed, silver blotches on the pale gold, each with its shadow.
        for (x, y) in ((5, 6), (9, 5), (11, 4)):
            put(x, y, colour=(246, 246, 240))
            put(x + 1, y, colour=(246, 246, 240))
            put(x, y + 1, dark)
            put(x + 1, y + 1, 2)
    elif name == "damasco":
        # Damascus: two waves running along the bar, a dark line with a lit edge under each.
        for c in (7.0, 9.2):
            for (x, y) in axis(c):
                wave = 1 if x % 4 in (1, 2) else 0
                put(x, y + wave, dark)
                put(x, y + wave + 1, 4)
    elif name == "acero_estelar":
        # Star steel: two small stars, a white heart and four lit points, a shadow under each.
        for (cx, cy) in ((6, 6), (10, 5)):
            for (dx, dy) in ((-1, 0), (1, 0), (0, -1)):
                put(cx + dx, cy + dy, 4)
            put(cx, cy, colour=(255, 255, 255))
            put(cx, cy + 1, dark)
    elif name == "obsidiacero":
        # Obsidian steel: glassy facets, short lit strokes across the bar with a black edge.
        for (x, y) in ((5, 8), (8, 7), (11, 6)):
            put(x, y, 0)
            put(x + 1, y - 1, 4)
            put(x + 1, y - 2, 4)
            put(x + 2, y - 1, 0)
    elif name == "acero_refractario":
        # Refractory steel: sintered grain, a scatter of small dents.
        for (x, y) in ((4, 6), (7, 6), (10, 4), (6, 4) , (9, 6), (12, 4)):
            put(x, y, dark if (x + y) % 2 == 0 else 2)
            put(x, y + 1, 4)
    elif name == "cinerio":
        # Cinereous: a crack along the bar with the fire still in it.
        for (x, y) in axis(7.6):
            put(x, y, 0)
            put(x, y + 1, colour=(255, 150, 50))
        for x in (6, 10):
            put(x, axis(7.6)[x][1] + 1, colour=(255, 226, 150))
    elif name == "voltaico":
        # Voltaic: a bolt along the bar, white with its own shadow.
        for (x, y) in axis(7.6):
            zig = 1 if x % 3 == 1 else 0
            put(x, y + zig, colour=(248, 255, 255))
            put(x, y + zig + 1, dark)
    elif name == "vidriacero":
        # Glass steel: a band you can see through, its far edge dark, with one glint.
        for (x, y) in axis(7.6):
            put(x, y, dark)
            put(x, y + 1, colour=(232, 252, 255))
        put(8, 6, colour=(255, 255, 255))
    elif name == "solacero":
        # Sun steel: a small sun in the middle of the top, its rays short dark strokes.
        for (x, y) in ((8, 5), (7, 5), (9, 5), (8, 4), (8, 6)):
            put(x, y, colour=(255, 252, 226))
        for (x, y) in ((6, 6), (10, 4), (6, 4), (10, 6)):
            put(x, y, dark)
    elif name == "lunacero":
        # Moon steel: two craters, each a dark pair with a lit lower rim.
        for (cx, cy) in ((5, 6), (9, 5)):
            pit(cx, cy, 2)
    elif name == "acero_vivo":
        # Living steel: a vein that pulses along the bar, bright blood over a dark channel.
        for (x, y) in axis(7.6):
            wave = 1 if x % 4 in (1, 2) else 0
            put(x, y + wave, colour=(255, 150, 120))
            put(x, y + wave + 1, 0)
    elif name == "almacero":
        # Soul steel: the soul blue already runs along it; a soul flame on the blue end.
        for (x, y) in ((10, 4), (11, 4), (11, 3), (10, 5), (11, 5)):
            put(x, y, colour=(186, 255, 250))
        for (x, y) in ((10, 6), (11, 6), (12, 5)):
            put(x, y, 0)
    elif name == "oricalco":
        # Oricalco: one star caught in it, as in its pearl.
        put(8, 5, colour=(255, 252, 214))
        for (dx, dy) in ((-1, 0), (1, 0), (0, -1)):
            put(8 + dx, 5 + dy, 4)
        put(8, 6, dark)
        put(7, 6, 2)
        put(9, 6, 2)
    elif name == "lingote_de_temple":
        # The tempering bar: one hammer mark on the cold end.
        pit(5, 6, 2)


def variant_a(name):
    bar = draw(SHAPE_A, name)
    mark_a(bar, name)
    return bar


# ---------------------------------------------------------------- variant B: the guild's stamped bar

FLOOR_B = cells(SHAPE_B, "f")


def variant_b(name):
    bar = draw(SHAPE_B, name)
    paint_at = painter(name)
    glyph = glyph_of(name)
    if glyph:
        x0, y0 = min(x for x, _ in FLOOR_B), min(y for _, y in FLOOR_B)
        for dy, row in enumerate(glyph):
            for dx, ch in enumerate(row):
                if ch == "#":
                    bar.putpixel((x0 + dx, y0 + dy), paint_at(x0 + dx, 4) + (255,))
    # Touches that are part of a metal's identity, not of the stamp.
    if name == "bronce":
        for (x, y) in ((11, 3), (12, 3), (3, 7)):
            bar.putpixel((x, y), (70, 140, 106, 255))
    if name == "acero_vivo":
        # The vein runs round the bar's front, one unbroken line.
        for x in range(3, 13):
            bar.putpixel((x, 10), (255, 112, 90, 255))
        bar.putpixel((7, 10), (255, 196, 176, 255))
    if name == "cinerio":
        # And the fire is still in the crack along the foot of the bar.
        for x in range(3, 13):
            bar.putpixel((x, 11), (255, 146, 44, 255))
        bar.putpixel((9, 11), (255, 222, 140, 255))
    return bar
