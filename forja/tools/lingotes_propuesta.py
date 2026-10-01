"""A proposal, not a change: every Forja ingot redrawn as one family, in two shapes, for Andy to choose.

Run from the project root:  python tools/lingotes_propuesta.py

It reads the committed ingot textures (to show them as they are now) and writes **only** preview files
under E:/IA/Claude/Forja_capturas_mejoras/lingotes_propuesta/: a/<name>.png, b/<name>.png and the
contact sheet hoja_lingotes.jpg. No texture in the mod is touched.

What the redesign keeps from today: each metal's colour (ALLOY_COLORS, the two sideways alloys, moon
steel's travelling ramp, oricalco's ramp, the tempering bar's heat) and the idea that every alloy wears
one small mark. What it changes:

* **One silhouette for the whole family**, drawn here by hand instead of re-tinting Minecraft's iron
  ingot pixel by pixel. Re-tinting carried over the iron ingot's own noise, which is what made marks
  fight with the surface.
* **One light**, from the top left, read off **one five-tone ramp per metal**: 0 outline in shadow,
  1 front face, 2 near end, 3 top face, 4 rim. Shadows lean cool and highlights lean warm, as vanilla's
  do, so a grey metal still looks like metal and not like plastic.
* **A clean bevel**: the edge where the top meets the front is one unbroken line of tone 4, and the
  near corner has its own lit edge.

Variant A keeps vanilla's diagonal ingot (it sits beside an iron ingot in a chest without looking
foreign) and puts each alloy's mark on the lit top face, along the bar.

Variant B is Forja's own bar: a cast bar seen from above, sloping sides, the guild's cartouche
stamped into the top face and the alloy's glyph raised inside it (3 rows by 4 columns). The tempering bar's cartouche is left
empty, because it has not been assayed as anything yet.

To adopt one, see the note at the bottom of this file (apply_to()).
"""

import io
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import generate_assets as gen  # noqa: E402
import dimension_assets as dim  # noqa: E402

OUT = Path("E:/IA/Claude/Forja_capturas_mejoras/lingotes_propuesta")
ITEMS = gen.ASSETS / "textures/item"

# Every ingot in the mod, in the order the foundry unlocks them, then the two that are not alloys.
INGOTS = list(gen.ALLOY_COLORS) + ["oricalco", "lingote_de_temple"]


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
    if name in gen.ALLOY_SIDEWAYS:
        left, right = (ramp_of(rgb(c)) for c in gen.ALLOY_SIDEWAYS[name])
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
    if name in gen.ALLOY_RAMPS:
        r = ramp_between(*gen.ALLOY_RAMPS[name])
        return lambda x, tone: r[tone]
    r = ramp_of(rgb(gen.ALLOY_COLORS[name]))
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
}


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
    glyph = GLYPHS.get(name)
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


# ---------------------------------------------------------------- the contact sheet

def vanilla_sprite(path):
    jar = zipfile.ZipFile(gen.CLIENT_JAR)
    return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + path))).convert("RGBA")


PANEL = (198, 198, 198)


def slot(item, scale=3):
    """The item in an inventory slot, at GUI scale 3, with a bit of the grey panel round it."""
    tile = Image.new("RGBA", (22, 22), PANEL + (255,))
    d = ImageDraw.Draw(tile)
    d.rectangle((2, 2, 19, 19), fill=(139, 139, 139, 255))
    d.line((2, 2, 18, 2), fill=(55, 55, 55, 255))
    d.line((2, 2, 2, 18), fill=(55, 55, 55, 255))
    d.line((3, 19, 19, 19), fill=(255, 255, 255, 255))
    d.line((19, 3, 19, 19), fill=(255, 255, 255, 255))
    tile.alpha_composite(item, (3, 3))
    return tile.resize((22 * scale, 22 * scale), Image.NEAREST)


def hotbar(items, scale=3):
    bar = vanilla_sprite("gui/sprites/hud/hotbar.png")
    for i, item in enumerate(items[:9]):
        bar.alpha_composite(item, (3 + 20 * i, 3))
    return bar.resize((bar.width * scale, bar.height * scale), Image.NEAREST)


def font(size):
    for name in ("arialbd.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def contact_sheet(rows, path):
    big = 8
    label_w, cell_gap = 170, 26
    col_w = 16 + 10 + 16 * big + 10 + 66
    width = label_w + 3 * (col_w + cell_gap) + 10
    row_h = 16 * big + 14
    header = 70
    world_bg = (122, 108, 92)
    hot_h = 22 * 3 + 12
    hot_block = 3 * (2 * hot_h + 40)
    height = header + row_h * len(rows) + hot_block + 30
    sheet = Image.new("RGB", (width, height), (34, 32, 38))
    d = ImageDraw.Draw(sheet)
    f_title, f_head, f_row = font(26), font(20), font(16)
    d.text((14, 12), "Forja: lingotes - actual | A (forma vanilla) | B (barra sellada del gremio)", fill=(240, 232, 214), font=f_title)
    titles = ("actual", "A: forma vanilla", "B: barra sellada")
    for i, t in enumerate(titles):
        d.text((label_w + i * (col_w + cell_gap), header - 26), t, fill=(250, 210, 140), font=f_head)
    for r, (name, images) in enumerate(rows):
        y = header + r * row_h
        if r % 2:
            d.rectangle((0, y, width, y + row_h - 1), fill=(42, 40, 47))
        d.text((12, y + row_h // 2 - 10), name, fill=(230, 226, 220), font=f_row)
        for c, image in enumerate(images):
            x = label_w + c * (col_w + cell_gap)
            # 1x on a mid-grey square, so it reads against something.
            d.rectangle((x - 2, y + 54, x + 17, y + 73), fill=(139, 139, 139))
            sheet.paste(image, (x, y + 56), image)
            # 8x on a dark checker, nearest neighbour.
            bx = x + 26
            checker = Image.new("RGB", (16 * big, 16 * big), (60, 58, 66))
            cd = ImageDraw.Draw(checker)
            for cy in range(16):
                for cx in range(16):
                    if (cx + cy) % 2:
                        cd.rectangle((cx * big, cy * big, cx * big + big - 1, cy * big + big - 1), fill=(70, 68, 76))
            sheet.paste(checker, (bx, y + 6))
            scaled = image.resize((16 * big, 16 * big), Image.NEAREST)
            sheet.paste(scaled, (bx, y + 6), scaled)
            s = slot(image)
            sheet.paste(s, (bx + 16 * big + 10, y + 6 + (16 * big - s.height) // 2), s)
    y = header + row_h * len(rows) + 20
    d.text((14, y), "Hotbar (escala GUI 3): toda la familia junta", fill=(240, 232, 214), font=f_head)
    y += 34
    for c, t in enumerate(titles):
        d.text((14, y), t, fill=(250, 210, 140), font=f_row)
        y += 22
        group = [images[c] for _, images in rows]
        for start in (0, 9):
            hb = hotbar(group[start:start + 9])
            # A grassy-earth backdrop, as the hotbar is seen in play.
            d.rectangle((10, y - 4, 10 + hb.width + 8, y + hb.height + 4), fill=world_bg)
            sheet.paste(hb, (14, y), hb)
            y += hot_h
        y += 18 - 22
    sheet = sheet.crop((0, 0, width, min(height, y + 20)))
    sheet.save(path, quality=93, subsampling=0, optimize=True)


def main():
    for folder in ("a", "b"):
        (OUT / folder).mkdir(parents=True, exist_ok=True)
    rows = []
    for name in INGOTS:
        current = Image.open(ITEMS / f"{name}.png").convert("RGBA")
        a, b = variant_a(name), variant_b(name)
        a.save(OUT / "a" / f"{name}.png")
        b.save(OUT / "b" / f"{name}.png")
        rows.append((name, (current, a, b)))
    sheet = OUT / "hoja_lingotes.jpg"
    contact_sheet(rows, sheet)
    print(f"{len(rows)} lingotes; hoja en {sheet} ({sheet.stat().st_size // 1024} KB)")


# ---------------------------------------------------------------- adopting one variant
#
# Nothing below runs on its own. When Andy picks a variant, the switch is one call in each generator:
#
#   generate_assets.generate_alloy_textures()      -> for name in ALLOY_COLORS:
#                                                        variant_X(name).save(ASSETS / f"textures/item/{name}.png")
#   generate_assets.generate_temper_ingot_texture() -> variant_X("lingote_de_temple").save(... lingote_de_temple.png)
#   dimension_assets.portal()                       -> variant_X("oricalco").save(items / "oricalco.png")
#
# apply_to(variant_a) (or variant_b) does exactly that, for a one-off run before the generators change.

def apply_to(variant):
    for name in INGOTS:
        variant(name).save(ITEMS / f"{name}.png")


if __name__ == "__main__":
    main()
