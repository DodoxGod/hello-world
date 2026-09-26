"""The mod's logo: FORJA in forged letters cooling from the foot up, over the eight-pointed star, on an anvil."""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent
LETTERS = {
    "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "J": ["..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."],
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
}
ANVIL = ["..##############....", ".################...", "##################..", ".###############....", "....#########.......", ".....#######........",
         ".....#######........", "....#########.......", "..#############.....", ".###############...."]
W, H, PX = 192, 108, 1
rng = random.Random(26)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


img = Image.new("RGB", (W, H), (14, 12, 12))
d = ImageDraw.Draw(img)
# the glow of the hearth from below
for y in range(H):
    t = max(0.0, (y - 40) / (H - 40)) ** 1.6
    for x in range(W):
        fall = 1.0 - min(1.0, abs(x - W / 2) / (W * 0.62)) ** 2
        img.putpixel((x, y), lerp((14, 12, 12), (120, 44, 10), t * fall * 0.9))
# the star of the star forge, faint behind everything
cx, cy, r = W // 2, 46, 40
pts = [(cx + math.cos(math.tau * i / 8 - math.pi / 2) * r, cy + math.sin(math.tau * i / 8 - math.pi / 2) * r) for i in range(8)]
for i in range(8):
    d.line((pts[i], pts[(i + 3) % 8]), fill=(74, 40, 22), width=1)
d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(74, 40, 22))
# the anvil
ax, ay = W // 2 - 20, 74
for j, row in enumerate(ANVIL):
    for i, c in enumerate(row):
        if c == "#":
            shade = 58 - j * 3 + (10 if j == 0 else 0)
            d.rectangle((ax + i * 2, ay + j * 2, ax + i * 2 + 1, ay + j * 2 + 1), fill=(shade, shade, shade + 6))
# the letters: 4 px a cell, steel at the head and still red at the foot
cell, gap = 4, 4
total = 5 * 5 * cell + 4 * gap
lx, ly = (W - total) // 2, 30
for k, ch in enumerate("FORJA"):
    ox = lx + k * (5 * cell + gap)
    for j, row in enumerate(LETTERS[ch]):
        for i, c in enumerate(row):
            if c != "#":
                continue
            heat = (j / 6.0) ** 1.4
            base = lerp((206, 210, 220), (255, 120, 24), heat)
            x0, y0 = ox + i * cell, ly + j * cell
            d.rectangle((x0 + 1, y0 + 1, x0 + cell, y0 + cell), fill=(10, 8, 8))            # shadow
            d.rectangle((x0, y0, x0 + cell - 1, y0 + cell - 1), fill=base)
            d.line((x0, y0, x0 + cell - 1, y0), fill=lerp(base, (255, 255, 255), 0.45))      # bevel
            d.line((x0, y0 + cell - 1, x0 + cell - 1, y0 + cell - 1), fill=lerp(base, (0, 0, 0), 0.35))
# sparks
for _ in range(46):
    x, y = rng.randrange(20, W - 20), rng.randrange(8, 78)
    img.putpixel((x, y), lerp((255, 200, 90), (255, 110, 30), rng.random()))
big = img.resize((W * 8, H * 8), Image.NEAREST)
glow = big.filter(ImageFilter.GaussianBlur(14))
Image.blend(big, Image.composite(glow, big, Image.new("L", big.size, 90)), 0.35).save(OUT / "forja_logo.png")
# square icon: the star, the anvil and an F
icon = img.crop((W // 2 - 54, 0, W // 2 + 54, 108)).resize((512, 512), Image.NEAREST)
icon.save(OUT / "forja_icono.png")
print("ok")
