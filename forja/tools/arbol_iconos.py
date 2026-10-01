"""Sprites for the class trees' icons (docs/ARBOLES.md, "Iconos"): the keystones' medallions and the heart.

Which glyph each keystone gets lives in arboles_datos.KEYSTONE_GLYPHS; this file only knows how to draw each glyph.
A keystone's sprite is a 16x16 octagonal medallion, gold-rimmed, dark in its class's colour, with the glyph in
cream on it. Written to src/main/resources/assets/forja/textures/gui/arbol/. Called from generate_assets.py (and
runnable alone: python tools/arbol_iconos.py).
"""
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "forja", "textures", "gui", "arbol")

G = 12  # the glyphs are drawn on a 12x12 mask: 255 is ink, 0 is a hole cut in it


def poly(d, points, fill=255):
    d.polygon(points, fill=fill)


def rect(d, x0, y0, x1, y1, fill=255):
    d.rectangle([x0, y0, x1, y1], fill=fill)


def muro(d):
    rect(d, 0, 1, 11, 10)
    for y in (4, 7):
        d.line([0, y, 11, y], fill=0)
    for x in (3, 8):
        d.line([x, 1, x, 3], fill=0)
    for x in (1, 5, 10):
        d.line([x, 5, x, 6], fill=0)
    for x in (3, 8):
        d.line([x, 8, x, 10], fill=0)


def rayo(d):
    poly(d, [(7, 0), (2, 6), (5, 6), (4, 11), (10, 4), (6, 4), (8, 0)])


def torre(d):
    poly(d, [(5, 0), (0, 4), (11, 4)])
    rect(d, 2, 4, 9, 11)
    rect(d, 4, 7, 7, 11, 0)
    rect(d, 4, 5, 7, 5, 0)


def espadas(d):
    d.line([1, 1, 10, 10], fill=255, width=2)
    d.line([10, 1, 1, 10], fill=255, width=2)
    rect(d, 0, 3, 3, 4)
    rect(d, 8, 3, 11, 4)


def martillo(d):
    d.line([3, 11, 7, 5], fill=255, width=2)
    poly(d, [(5, 0), (11, 3), (9, 7), (3, 4)])
    d.line([5, 2, 9, 5], fill=0)




def gota(d):
    d.ellipse([2, 4, 9, 11], fill=255)
    poly(d, [(5, 0), (2, 6), (9, 6)])
    rect(d, 3, 6, 4, 7, 0)


def viento(d):
    d.line([0, 3, 7, 3], fill=255, width=1)
    d.arc([5, 0, 10, 5], 270, 110, fill=255)
    d.line([0, 6, 9, 6], fill=255, width=1)
    d.arc([7, 3, 11, 8], 270, 110, fill=255)
    d.line([0, 9, 6, 9], fill=255, width=1)
    d.arc([4, 6, 8, 11], 270, 110, fill=255)


def fantasma(d):
    d.ellipse([1, 0, 10, 8], fill=255)
    rect(d, 1, 4, 10, 10)
    for x in (2, 5, 8):
        poly(d, [(x, 11), (x + 1, 8), (x + 2, 11)], 0)
    rect(d, 3, 3, 4, 5, 0)
    rect(d, 7, 3, 8, 5, 0)


def calavera(d):
    d.ellipse([1, 0, 10, 8], fill=255)
    rect(d, 3, 7, 8, 11)
    rect(d, 2, 3, 4, 5, 0)
    rect(d, 7, 3, 9, 5, 0)
    rect(d, 5, 6, 6, 6, 0)
    for x in (4, 6, 8):
        d.line([x, 9, x, 11], fill=0)


def garra(d):
    for x in (1, 4, 7):
        poly(d, [(x, 0), (x + 3, 0), (x + 4, 11)])


def cuerda(d):
    pts = [(0, 7), (2, 4), (4, 7), (6, 10), (8, 7), (10, 4), (11, 6)]
    d.line(pts, fill=255, width=2)
    d.ellipse([0, 8, 2, 10], fill=255)
    d.ellipse([9, 1, 11, 3], fill=255)


def mascara(d):
    d.ellipse([0, 0, 11, 11], fill=255)
    poly(d, [(1, 3), (4, 4), (4, 5), (2, 5)], 0)
    poly(d, [(10, 3), (7, 4), (7, 5), (9, 5)], 0)
    d.arc([3, 5, 8, 10], 20, 160, fill=0)


def pincho(d):
    poly(d, [(5, 0), (3, 8), (7, 8)])
    poly(d, [(1, 3), (0, 8), (3, 8)])
    poly(d, [(10, 3), (8, 8), (11, 8)])
    rect(d, 0, 8, 11, 11)


def almena(d):
    rect(d, 0, 3, 11, 11)
    for x0, x1 in ((0, 2), (4, 7), (9, 11)):
        rect(d, x0, 0, x1, 3)
    rect(d, 5, 6, 6, 9, 0)


def yunque(d):
    rect(d, 0, 1, 11, 4)
    rect(d, 3, 4, 8, 6)
    rect(d, 2, 6, 9, 8)
    rect(d, 1, 8, 10, 11)


def puno(d):
    rect(d, 3, 1, 10, 9)
    for y in (3, 5, 7):
        d.line([6, y, 10, y], fill=0)
    rect(d, 0, 4, 3, 8)
    rect(d, 3, 9, 9, 11)




def bandera(d):
    rect(d, 1, 0, 2, 11)
    poly(d, [(3, 0), (11, 3), (3, 7)])
    rect(d, 0, 10, 5, 11)


def iman(d):
    d.ellipse([1, 0, 10, 9], fill=255)
    d.ellipse([4, 3, 7, 9], fill=0)
    rect(d, 4, 6, 7, 11, 0)
    rect(d, 1, 6, 3, 11)
    rect(d, 8, 6, 10, 11)
    rect(d, 1, 8, 3, 8, 0)
    rect(d, 8, 8, 10, 8, 0)


def cadena(d):
    d.ellipse([0, 0, 6, 6], outline=255, width=2)
    d.ellipse([5, 5, 11, 11], outline=255, width=2)
    d.line([4, 4, 7, 7], fill=255, width=1)


def mitad(d):
    d.ellipse([0, 0, 11, 11], outline=255, width=2)
    d.pieslice([3, 3, 8, 8], 90, 270, fill=255)




def espiral(d):
    pts = []
    for i in range(0, 62):
        a = i * 0.22
        r = 0.3 + i * 0.085
        pts.append((5.5 + r * math.cos(a), 5.5 + r * math.sin(a)))
    d.line(pts, fill=255, width=1)




def gota_estrella(d):
    gota(d)
    rect(d, 4, 6, 5, 9, 0)
    rect(d, 3, 7, 6, 8, 0)


def destello(d):
    poly(d, [(5.5, 0), (7, 4.5), (11, 5.5), (7, 7), (5.5, 11), (4, 7), (0, 5.5), (4, 4.5)])


def escudo(d):
    poly(d, [(0, 1), (11, 1), (11, 6), (5.5, 11), (0, 6)])
    d.line([5, 3, 5, 8], fill=0)
    d.line([6, 3, 6, 8], fill=0)


def cruz(d):
    rect(d, 4, 0, 7, 11)
    rect(d, 0, 4, 11, 7)


def flor(d):
    for box in ([4, 0, 7, 4], [4, 7, 7, 11], [0, 4, 4, 7], [7, 4, 11, 7]):
        d.ellipse(box, fill=255)
    d.ellipse([3, 3, 8, 8], fill=255)
    d.ellipse([5, 5, 6, 6], fill=0)


def runa(d):
    d.ellipse([0, 0, 11, 11], outline=255, width=1)
    d.line([5.5, 2, 2, 9], fill=255)
    d.line([5.5, 2, 9, 9], fill=255)
    d.line([2, 9, 9, 9], fill=255)
    rect(d, 5, 5, 6, 6)


def anillos(d):
    d.ellipse([0, 2, 7, 9], outline=255, width=2)
    d.ellipse([4, 2, 11, 9], outline=255, width=2)


def sol(d):
    d.ellipse([3, 3, 8, 8], fill=255)
    for a in range(0, 360, 45):
        r0, r1 = 4.6, 6.0 if a % 90 else 6.0
        x0, y0 = 5.5 + r0 * math.cos(math.radians(a)), 5.5 + r0 * math.sin(math.radians(a))
        x1, y1 = 5.5 + r1 * math.cos(math.radians(a)), 5.5 + r1 * math.sin(math.radians(a))
        d.line([x0, y0, x1, y1], fill=255)


def martillo_luz(d):
    martillo(d)
    rect(d, 5, 0, 6, 4, 0)
    rect(d, 3, 1, 8, 2, 0)


def mira(d):
    d.ellipse([1, 1, 10, 10], outline=255, width=1)
    for box in ([5, 0, 6, 3], [5, 8, 6, 11], [0, 5, 3, 6], [8, 5, 11, 6]):
        rect(d, *box)
    rect(d, 5, 5, 6, 6)


def ojo(d):
    d.ellipse([0, 1, 11, 10], fill=255)
    d.ellipse([3, 2, 8, 9], fill=0)
    d.ellipse([4, 3, 7, 8], fill=255)
    rect(d, 5, 4, 6, 7, 0)


def rafaga(d):
    for y in (2, 5, 9):
        d.line([0, y, 8, y], fill=255)
        poly(d, [(7, y - 2), (11, y), (7, y + 2)])
    rect(d, 0, 0, 1, 0)


def perforante(d):
    d.line([1, 10, 8, 3], fill=255, width=2)
    poly(d, [(11, 0), (11, 6), (5, 0)])
    poly(d, [(0, 7), (4, 11), (0, 11)])
    poly(d, [(0, 5), (3, 8), (0, 8)], 0)


def bota(d):
    rect(d, 2, 0, 6, 8)
    rect(d, 2, 7, 11, 10)
    rect(d, 2, 11, 11, 11)
    rect(d, 2, 1, 6, 1, 0)


def ala(d):
    poly(d, [(0, 10), (1, 4), (5, 0), (11, 1), (8, 3), (11, 5), (7, 6), (9, 8), (4, 9)])
    d.line([2, 8, 8, 2], fill=0)


GLYPHS = {name: fn for name, fn in globals().items() if callable(fn) and name in (
    "muro rayo torre espadas martillo gota viento fantasma calavera garra cuerda mascara pincho almena yunque puno bandera "
    "iman cadena mitad espiral gota_estrella destello escudo cruz flor runa anillos sol martillo_luz mira ojo rafaga "
    "perforante bota ala").split()}

CREAM, CREAM_DARK = (250, 238, 200), (214, 190, 130)
RIM_LIGHT, RIM_DARK, EDGE = (255, 214, 110), (176, 122, 36), (26, 18, 10)


def octagon(cut=3):
    """The medallion's shape, 16x16."""
    m = Image.new("L", (16, 16), 0)
    d = ImageDraw.Draw(m)
    d.polygon([(cut, 0), (15 - cut, 0), (15, cut), (15, 15 - cut), (15 - cut, 15), (cut, 15), (0, 15 - cut), (0, cut)], fill=255)
    return m


def medallion(glyph, colour):
    """A keystone's sprite: a dark gem of the class's colour in a gold octagon, the glyph in cream on it."""
    shape = octagon()
    inner = Image.new("L", (16, 16), 0)
    ImageDraw.Draw(inner).polygon([(3, 2), (12, 2), (13, 3), (13, 12), (12, 13), (3, 13), (2, 12), (2, 3)], fill=255)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    sp, ip = shape.load(), inner.load()
    for y in range(16):
        for x in range(16):
            if not sp[x, y]:
                continue
            edge = any(x + dx < 0 or x + dx > 15 or y + dy < 0 or y + dy > 15 or not sp[x + dx, y + dy]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                px[x, y] = EDGE + (255,)
            elif not ip[x, y]:
                px[x, y] = (RIM_LIGHT if x + y < 15 else RIM_DARK) + (255,)
            else:
                k = 0.30 + 0.30 * (1 - y / 15.0)
                px[x, y] = tuple(int(c * k) for c in colour) + (255,)
    mask = Image.new("L", (G, G), 0)
    GLYPHS[glyph](ImageDraw.Draw(mask))
    # 12 -> 10 pixels, so the glyph sits inside the rim with a margin.
    mask = mask.resize((10, 10), Image.BOX)
    mp = mask.load()
    for y in range(10):
        for x in range(10):
            if mp[x, y] > 100 and ip_inside(ip, x + 4, y + 4):
                px[x + 4, y + 4] = tuple(int(c * 0.35) for c in colour) + (255,)
    for y in range(10):
        for x in range(10):
            if mp[x, y] > 100:
                px[x + 3, y + 3] = (CREAM if y < 5 else CREAM_DARK) + (255,)
    return img


def ip_inside(ip, x, y):
    return 0 <= x < 16 and 0 <= y < 16 and ip[x, y] > 0


HEART = [
    "..oo...oo..",
    ".oRRo.oRRo.",
    "oRWWRoRRRRo",
    "oRWRRRRRRRo",
    "oRRRRRRRRDo",
    ".oRRRRRRDo.",
    "..oRRRRDo..",
    "...oRRDo...",
    "....oDo....",
    ".....o.....",
]
HEART_COLOURS = {"o": (60, 8, 12, 255), "R": (214, 34, 44, 255), "W": (255, 150, 150, 255), "D": (140, 16, 28, 255)}


def heart():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(HEART):
        for x, ch in enumerate(row):
            if ch in HEART_COLOURS:
                img.putpixel((x + 2, y + 3), HEART_COLOURS[ch])
    return img


def keystone_classes():
    """Spanish name of every keystone -> its class."""
    from arboles_datos import CLASSES
    found = {}

    def walk(x, cid):
        if isinstance(x, dict):
            if x.get("tipo") == "clave":
                found[x["nombre"][0]] = cid
                return
            for v in x.values():
                walk(v, cid)
        elif isinstance(x, (list, tuple)):
            for v in x:
                walk(v, cid)
    for cid, c in CLASSES.items():
        walk({k: v for k, v in c.items() if k in ("puertas", "ramas", "sendas")}, cid)
    return found


def generate(out=OUT):
    from arboles import CLASS_COLORS
    from arboles_datos import KEYSTONE_GLYPHS, key_sprite
    os.makedirs(out, exist_ok=True)
    classes = keystone_classes()
    for name, glyph in KEYSTONE_GLYPHS.items():
        hexcolour = CLASS_COLORS[classes[name]].lstrip("#")
        colour = tuple(int(hexcolour[i:i + 2], 16) for i in (0, 2, 4))
        medallion(glyph, colour).save(os.path.join(out, key_sprite(name).split(":", 1)[1] + ".png"))
    heart().save(os.path.join(out, "corazon.png"))
    return len(KEYSTONE_GLYPHS) + 1


if __name__ == "__main__":
    print(generate(), "sprites in", OUT)
