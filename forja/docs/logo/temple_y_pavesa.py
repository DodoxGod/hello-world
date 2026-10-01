"""TEMPLE Y PAVESA, built the way Minecraft's own logo is: every letter a wall of blocks with a face, a lit top edge,
a body going back into the dark, and a texture you could mine. TEMPLE is quenched steel, cold and cracked with frost;
PAVESA is the same metal still in the fire, glowing from inside and throwing sparks. Between them, the star."""
import math
import random
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent
FONT = {
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."], "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"], "P": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"], "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "V": ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."], "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "Y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
}
W, H = 1280, 640
G = 4                                   # the grain: one "pixel" of the texture
rng = random.Random(262)


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def cells(text, x, y, cell, gap):
    out = []
    for k, ch in enumerate(text):
        ox = x + k * (5 * cell + gap)
        for j, row in enumerate(FONT[ch]):
            for i, c in enumerate(row):
                if c == "#":
                    out.append((ox + i * cell, y + j * cell, i, j, k))
    return out


def span(text, cell, gap):
    return len(text) * 5 * cell + (len(text) - 1) * gap


def word(img, text, y, cell, gap, depth, face, top, body, cracks, heat=False):
    d = ImageDraw.Draw(img)
    x = (W - span(text, cell, gap)) // 2
    blocks = cells(text, x, y, cell, gap)
    taken = {(bx, by) for bx, by, *_ in blocks}
    # the body, going down and back: drawn first, darkest at the far end
    for step in range(depth, 0, -1):
        shade = lerp(body[0], body[1], step / depth)
        for bx, by, *_ in blocks:
            d.rectangle((bx + step // 3, by + step, bx + cell - 1 + step // 3, by + cell - 1 + step), fill=shade)
    # the face, block by block: its own tone, a grain, a lit edge where nothing is above it, a dark one where nothing is below
    for bx, by, i, j, k in blocks:
        if isinstance(face, list):
            pair, lit = face[k % len(face)]
        else:
            pair, lit = face, top
        tone = rng.uniform(-0.12, 0.12)
        for gy in range(0, cell, G):
            for gx in range(0, cell, G):
                t = (j * cell + gy) / (7 * cell)
                colour = lerp(pair[0], pair[1], t + tone + rng.uniform(-0.08, 0.08))
                if heat:
                    colour = lerp(colour, (255, 236, 170), max(0.0, 0.55 - abs((gx + gy) / (2 * cell) - 0.5)) * rng.random() * 0.9)
                d.rectangle((bx + gx, by + gy, bx + gx + G - 1, by + gy + G - 1), fill=colour)
        if (bx, by - cell) not in taken:
            d.rectangle((bx, by, bx + cell - 1, by + G - 1), fill=lit)
        if (bx - cell, by) not in taken:
            d.rectangle((bx, by, bx + G - 1, by + cell - 1), fill=lerp(lit, pair[0], 0.5))
        if (bx, by + cell) not in taken:
            d.rectangle((bx, by + cell - G, bx + cell - 1, by + cell - 1), fill=lerp(pair[1], (0, 0, 0), 0.45))
        if (bx + cell, by) not in taken:
            d.rectangle((bx + cell - G, by, bx + cell - 1, by + cell - 1), fill=lerp(pair[1], (0, 0, 0), 0.3))
    # cracks: short walks of dark grain across the face, never off it
    for _ in range(cracks):
        bx, by, *_ = rng.choice(blocks)
        px, py = bx + rng.randrange(0, cell, G), by
        for _ in range(rng.randrange(5, 14)):
            if (px // cell * cell - (x % cell) + (x % cell), 0) and any(cx <= px < cx + cell and cy <= py < cy + cell for cx, cy in taken):
                d.rectangle((px, py, px + G - 1, py + G - 1), fill=(30, 30, 40) if not heat else (255, 244, 200))
            px += rng.choice((-G, 0, 0, G))
            py += G
    return blocks, x


img = Image.new("RGB", (W, H), (10, 9, 11))
d = ImageDraw.Draw(img)
# the forge behind: dark above, the hearth's breath from below, a vignette
for yy in range(0, H, G):
    for xx in range(0, W, G):
        t = max(0.0, (yy - 250) / (H - 250)) ** 1.5 * (1.0 - min(1.0, abs(xx - W / 2) / (W * 0.6)) ** 2)
        d.rectangle((xx, yy, xx + G - 1, yy + G - 1), fill=lerp((10, 9, 11), (150, 52, 8), t * (0.85 + rng.random() * 0.15)))
# the star forge's star, big and faint, behind both words
cx, cy, r = W // 2, 300, 250
pts = [(cx + math.cos(math.tau * i / 8 - math.pi / 2) * r, cy + math.sin(math.tau * i / 8 - math.pi / 2) * r * 0.62) for i in range(8)]
for i in range(8):
    d.line((pts[i], pts[(i + 3) % 8]), fill=(52, 30, 20), width=G)

tex = Path(__file__).resolve().parents[2] / "src/main/resources/assets/forja/textures/entity"
rune = Image.new("RGB", (W, H), (0, 0, 0))
for name, size, tint in (("onda_runa.png", 620, (150, 80, 235)), ("onda_runa_centro.png", 620, (110, 60, 200))):
    layer = Image.open(tex / name).convert("RGBA").resize((size, size), Image.NEAREST).resize((size * 2, int(size * 0.9)), Image.NEAREST)
    solid = Image.new("RGB", layer.size, tint)
    rune.paste(solid, ((W - layer.width) // 2, 310 - layer.height // 2), layer.getchannel("A").point(lambda v: int(v * 0.24)))
img = ImageChops.screen(img, rune)
img = ImageChops.screen(img, rune.filter(ImageFilter.GaussianBlur(12)))
d = ImageDraw.Draw(img)
STEEL = ((226, 232, 242), (120, 132, 156))
MATERIALS = [(((236, 238, 244), (132, 140, 160)), (255, 255, 255)),      # hierro
             (((255, 232, 120), (196, 140, 30)), (255, 250, 200)),       # oro
             (((150, 240, 232), (40, 150, 170)), (225, 255, 252)),       # diamante
             (((214, 170, 250), (120, 70, 190)), (245, 225, 255)),       # amatista
             (((240, 160, 110), (160, 80, 50)), (255, 215, 180)),        # cobre
             (((120, 104, 108), (52, 42, 48)), (170, 150, 156))]         # netherita
word(img, "TEMPLE", 70, 28, 16, 26, MATERIALS, (255, 255, 255), ((70, 78, 98), (22, 24, 34)), 16)
glow = Image.new("RGB", (W, H), (0, 0, 0))
ember_blocks, ex = word(glow, "PAVESA", 360, 28, 16, 0, ((255, 170, 40), (255, 90, 10)), (255, 200, 90), ((0, 0, 0), (0, 0, 0)), 0)
img = ImageChops.screen(img, glow.filter(ImageFilter.GaussianBlur(34)))
EMBER = ((255, 214, 96), (214, 60, 8))
word(img, "PAVESA", 360, 28, 16, 26, EMBER, (255, 250, 210), ((120, 34, 6), (30, 8, 4)), 22, heat=True)

# the Y between them, small, in brass, with the core burning over it
d = ImageDraw.Draw(img)
word(img, "Y", 268, 12, 0, 10, ((255, 208, 138), (170, 120, 60)), (255, 240, 200), ((90, 60, 30), (30, 20, 12)), 0)
ANVIL = ["..################....", ".###################..", "######################", ".##################...", "....############......",
         ".....##########.......", ".....##########.......", "...##############.....", ".##################..."]
ax, ay, a = W // 2 - 11 * 6 + 230, 292, 6
for j, row in enumerate(ANVIL):
    for i, c in enumerate(row):
        if c == "#":
            v = 96 - j * 7 + (40 if j == 0 else 0) + rng.randrange(-6, 7)
            d.rectangle((ax + i * a, ay + j * a, ax + i * a + a - 1, ay + j * a + a - 1), fill=(v, v, v + 10))
hx, hy = ax + 40, ay - 86                                     # the hammer, caught a moment before it lands
for k in range(14):
    d.rectangle((hx + 40 + k * 6, hy + 56 - k * 6, hx + 40 + k * 6 + 9, hy + 56 - k * 6 + 9), fill=(120, 84, 48))
for j in range(5):
    for i in range(9):
        v = 170 - j * 14 + rng.randrange(-8, 9)
        d.rectangle((hx + i * 8 - j * 8 + 8, hy + 40 + j * 8 + i * 0, hx + i * 8 - j * 8 + 15, hy + 47 + j * 8), fill=(v, v, v + 12))
strike = (ax + 60, ay - 4)
for _ in range(90):                                           # and what flies off where it is about to
    ang, far = rng.uniform(-math.pi, 0), rng.uniform(6, 120) * rng.random()
    sx, sy = int(strike[0] + math.cos(ang) * far) // G * G, int(strike[1] + math.sin(ang) * far * 0.8) // G * G
    d.rectangle((sx, sy, sx + G - 1, sy + G - 1), fill=lerp((255, 255, 220), (255, 130, 20), far / 120))
# the núcleo on the other side: the stone every staff and tome is built round, lit from inside
gx, gy = W // 2 - 300, 300
for r_, colour in ((26, (90, 40, 170)), (20, (150, 90, 235)), (12, (214, 170, 250)), (5, (255, 245, 255))):
    d.polygon([(gx, gy - r_), (gx + r_, gy), (gx, gy + r_), (gx - r_, gy)], fill=colour)
for _ in range(40):
    ang, far = rng.uniform(0, math.tau), rng.uniform(30, 110)
    sx, sy = int(gx + math.cos(ang) * far) // G * G, int(gy + math.sin(ang) * far * 0.6) // G * G
    d.rectangle((sx, sy, sx + G - 1, sy + G - 1), fill=lerp((230, 200, 255), (130, 70, 220), rng.random()))
for side in ():
    x0 = W // 2 + side * 80
    d.rectangle((min(x0, x0 + side * 330), 310, max(x0, x0 + side * 330), 310 + G - 1), fill=(170, 120, 60))
    d.rectangle((min(x0, x0 + side * 330), 318, max(x0, x0 + side * 330), 318 + G - 1), fill=(60, 40, 22))
# sparks off the hot word, brighter near it
for _ in range(420):
    bx, by, *_ = rng.choice(ember_blocks)
    sx, sy = bx + rng.randrange(-40, 60), by - rng.randrange(0, 260) * rng.random()
    sx, sy = int(sx) // G * G, int(sy) // G * G
    if 0 <= sx < W and 0 <= sy < H:
        hot = rng.random()
        for tail in range(rng.randrange(1, 4)):
            d.rectangle((sx - tail * G // 2, sy + tail * G, sx - tail * G // 2 + G - 1, sy + tail * G + G - 1), fill=lerp((255, 250, 200), (255, 90, 10), min(1.0, hot + tail * 0.3)))
for _ in range(16):                                           # drips of what has not set yet
    bx, by, *_ = rng.choice([b for b in ember_blocks if b[3] == 6])
    sx = bx + rng.randrange(0, 28, G)
    for k in range(rng.randrange(3, 10)):
        d.rectangle((sx, by + 28 + 26 + k * G, sx + G - 1, by + 28 + 26 + k * G + G - 1), fill=lerp((255, 220, 120), (200, 50, 8), k / 9))
# frost settling on the cold one
for _ in range(60):
    sx, sy = rng.randrange(180, W - 180) // G * G, rng.randrange(30, 300) // G * G
    d.rectangle((sx, sy, sx + G - 1, sy + G - 1), fill=lerp((200, 225, 255), (120, 150, 200), rng.random()))

bloom = img.filter(ImageFilter.GaussianBlur(10))
final = ImageChops.screen(img, bloom.point(lambda v: int(v * 0.24)))
final.save(OUT / "temple_y_pavesa.png")
print(final.size)
