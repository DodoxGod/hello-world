"""Three more logos, side by side: A the guild's seal, B the word with the star for its O, C the ember banner."""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
F = {
    "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."], "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"], "J": ["..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."],
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
}
S = 160
STEEL, DARK, EMBER, GOLD, NIGHT = (208, 212, 222), (24, 21, 22), (255, 122, 30), (255, 208, 138), (16, 14, 16)


def word(d, text, x, y, cell, colour, shadow=None, skip=()):
    for k, ch in enumerate(text):
        ox = x + k * (5 * cell + cell)
        if k in skip:
            continue
        for j, row in enumerate(F[ch]):
            for i, c in enumerate(row):
                if c == "#":
                    if shadow:
                        d.rectangle((ox + i * cell + 1, y + j * cell + 1, ox + (i + 1) * cell, y + (j + 1) * cell), fill=shadow)
                    d.rectangle((ox + i * cell, y + j * cell, ox + (i + 1) * cell - 1, y + (j + 1) * cell - 1), fill=colour(j) if callable(colour) else colour)


def width(text, cell):
    return len(text) * 6 * cell - cell


def star(d, cx, cy, r, colour, w=1):
    pts = [(cx + math.cos(math.tau * i / 8 - math.pi / 2) * r, cy + math.sin(math.tau * i / 8 - math.pi / 2) * r) for i in range(8)]
    for i in range(8):
        d.line((pts[i], pts[(i + 3) % 8]), fill=colour, width=w)


def hammer(d, cx, cy, colour, head):
    d.line((cx - 16, cy + 18, cx + 10, cy - 8), fill=colour, width=3)               # the haft, corner to corner
    d.polygon([(cx + 2, cy - 18), (cx + 20, cy), (cx + 12, cy + 8), (cx - 6, cy - 10)], fill=head)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---- A: the seal. A coin of dark iron, a ring of studs, the hammer over the star, the word on a ribbon across it.
a = Image.new("RGB", (S, S), NIGHT)
d = ImageDraw.Draw(a)
d.ellipse((8, 8, S - 9, S - 9), fill=(44, 40, 42), outline=GOLD, width=3)
d.ellipse((20, 20, S - 21, S - 21), outline=(120, 96, 60), width=1)
for i in range(24):
    ang = math.tau * i / 24
    x, y = S / 2 + math.cos(ang) * 65, S / 2 + math.sin(ang) * 65
    d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=GOLD)
star(d, S // 2, S // 2 - 8, 44, (92, 70, 48))
hammer(d, S // 2, S // 2 - 14, (150, 110, 70), STEEL)
d.rectangle((14, 96, S - 15, 124), fill=(120, 34, 20), outline=GOLD)
word(d, "FORJA", (S - width("FORJA", 3)) // 2, 100, 3, GOLD, (50, 14, 8))

# ---- B: the word alone. Flat steel on black, and the O is the star forge's star with the core burning in it.
b = Image.new("RGB", (S, S), NIGHT)
d = ImageDraw.Draw(b)
cell = 4
x0, y0 = (S - width("FORJA", cell)) // 2, 62
word(d, "FORJA", x0, y0, cell, STEEL, skip=(1,))
ocx, ocy = x0 + 6 * cell + 10, y0 + 14
d.ellipse((ocx - 15, ocy - 15, ocx + 15, ocy + 15), outline=STEEL, width=2)
star(d, ocx, ocy, 13, EMBER)
d.polygon([(ocx, ocy - 5), (ocx + 5, ocy), (ocx, ocy + 5), (ocx - 5, ocy)], fill=GOLD)
d.line((x0, y0 + 36, x0 + width("FORJA", cell), y0 + 36), fill=EMBER, width=2)

# ---- C: the guild's banner: ember rising into black, swallow-tailed, the hammer on it and the word under the bar.
c = Image.new("RGB", (S, S), NIGHT)
d = ImageDraw.Draw(c)
d.rectangle((30, 10, S - 31, 15), fill=(110, 84, 50))
for y in range(16, 140):
    t = (y - 16) / 124
    for x in range(38, S - 38):
        tail = y > 112 and abs(x - S / 2) < (y - 112) * 1.5
        if not tail:
            c.putpixel((x, y), lerp((26, 22, 24), (214, 78, 18), t ** 1.5))
d.rectangle((38, 16, S - 39, 19), fill=(16, 14, 16))
d.line((38, 16, 38, 139), fill=(16, 14, 16), width=2)
d.line((S - 39, 16, S - 39, 139), fill=(16, 14, 16), width=2)
hammer(d, S // 2, 56, (36, 28, 26), STEEL)
word(d, "FORJA", (S - width("FORJA", 2)) // 2 + 1, 90, 2, NIGHT)

font = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 22)
sheet = Image.new("RGB", (3 * 660 + 20, 700), (30, 28, 28))
ds = ImageDraw.Draw(sheet)
for i, (img, name) in enumerate(((a, "A · el cuño del gremio"), (b, "B · la palabra, con la estrella por O"), (c, "C · el estandarte de brasa"))):
    big = img.resize((640, 640), Image.NEAREST)
    big.save(OUT / f"forja_logo_{'ABC'[i]}.png")
    sheet.paste(big, (20 + i * 660, 50))
    ds.text((24 + i * 660, 12), name, font=font, fill=GOLD)
sheet.save(OUT / "tres_logos.png")
print("ok")
