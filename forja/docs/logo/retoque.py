from pathlib import Path

p = Path(__file__).resolve().parent / "temple_y_pavesa.py"
s = p.read_text(encoding="utf-8")


def swap(old, new):
    global s
    assert s.count(old) == 1, old[:50]
    s = s.replace(old, new)


# 3 - a combination of materials: every letter of TEMPLE is cut from a different one, the way every piece of the mod is
swap('''    for bx, by, i, j, k in blocks:
        tone = rng.uniform(-0.12, 0.12)''', '''    for bx, by, i, j, k in blocks:
        if isinstance(face, list):
            pair, lit = face[k % len(face)]
        else:
            pair, lit = face, top
        tone = rng.uniform(-0.12, 0.12)''')
swap("colour = lerp(face[0], face[1], t + tone + rng.uniform(-0.08, 0.08))", "colour = lerp(pair[0], pair[1], t + tone + rng.uniform(-0.08, 0.08))")
swap("            d.rectangle((bx, by, bx + cell - 1, by + G - 1), fill=top)", "            d.rectangle((bx, by, bx + cell - 1, by + G - 1), fill=lit)")
swap("fill=lerp(top, face[0], 0.5))", "fill=lerp(lit, pair[0], 0.5))")
swap("fill=lerp(face[1], (0, 0, 0), 0.45))", "fill=lerp(pair[1], (0, 0, 0), 0.45))")
swap("fill=lerp(face[1], (0, 0, 0), 0.3))", "fill=lerp(pair[1], (0, 0, 0), 0.3))")
swap("fill=lerp(face[1], (0, 0, 0), 0.6) if not heat else (255, 244, 200))", "fill=(30, 30, 40) if not heat else (255, 244, 200))")
swap('''word(img, "TEMPLE", 70, 28, 16, 26, STEEL, (255, 255, 255), ((70, 78, 98), (22, 24, 34)), 16)''',
     '''MATERIALS = [(((236, 238, 244), (132, 140, 160)), (255, 255, 255)),      # hierro
             (((255, 232, 120), (196, 140, 30)), (255, 250, 200)),       # oro
             (((150, 240, 232), (40, 150, 170)), (225, 255, 252)),       # diamante
             (((214, 170, 250), (120, 70, 190)), (245, 225, 255)),       # amatista
             (((240, 160, 110), (160, 80, 50)), (255, 215, 180)),        # cobre
             (((120, 104, 108), (52, 42, 48)), (170, 150, 156))]         # netherita
word(img, "TEMPLE", 70, 28, 16, 26, MATERIALS, (255, 255, 255), ((70, 78, 98), (22, 24, 34)), 16)''')

# 2 - magic: the tome's own rune, violet, turning behind the words, and the core burning on the anvil
swap('''STEEL = ((226, 232, 242), (120, 132, 156))''', '''tex = Path(__file__).resolve().parents[2] / "src/main/resources/assets/forja/textures/entity"
rune = Image.new("RGB", (W, H), (0, 0, 0))
for name, size, tint in (("onda_runa.png", 620, (150, 80, 235)), ("onda_runa_centro.png", 620, (110, 60, 200))):
    layer = Image.open(tex / name).convert("RGBA").resize((size, size), Image.NEAREST).resize((size * 2, int(size * 0.9)), Image.NEAREST)
    solid = Image.new("RGB", layer.size, tint)
    rune.paste(solid, ((W - layer.width) // 2, 310 - layer.height // 2), layer.getchannel("A").point(lambda v: int(v * 0.5)))
img = ImageChops.screen(img, rune)
img = ImageChops.screen(img, rune.filter(ImageFilter.GaussianBlur(12)))
d = ImageDraw.Draw(img)
STEEL = ((226, 232, 242), (120, 132, 156))''')

# 1 - the forge: the anvil between the two words with the hammer coming down on it, in place of the rule
swap('''for side in (-1, 1):
    x0 = W // 2 + side * 80''', '''ANVIL = ["..################....", ".###################..", "######################", ".##################...", "....############......",
         ".....##########.......", ".....##########.......", "...##############.....", ".##################..."]
ax, ay, a = W // 2 - 11 * 8 + 210, 268, 8
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
    x0 = W // 2 + side * 80''')

# 4 - hot and spitting: three times the sparks, each with a tail, and the hot word dripping
swap("for _ in range(150):", "for _ in range(420):")
swap('''    if 0 <= sx < W and 0 <= sy < H:
        d.rectangle((sx, sy, sx + G - 1, sy + G - 1), fill=lerp((255, 240, 180), (255, 110, 20), rng.random()))''',
     '''    if 0 <= sx < W and 0 <= sy < H:
        hot = rng.random()
        for tail in range(rng.randrange(1, 4)):
            d.rectangle((sx - tail * G // 2, sy + tail * G, sx - tail * G // 2 + G - 1, sy + tail * G + G - 1), fill=lerp((255, 250, 200), (255, 90, 10), min(1.0, hot + tail * 0.3)))
for _ in range(16):                                           # drips of what has not set yet
    bx, by, *_ = rng.choice([b for b in ember_blocks if b[3] == 6])
    sx = bx + rng.randrange(0, 28, G)
    for k in range(rng.randrange(3, 10)):
        d.rectangle((sx, by + 28 + 26 + k * G, sx + G - 1, by + 28 + 26 + k * G + G - 1), fill=lerp((255, 220, 120), (200, 50, 8), k / 9))''')
swap("bloom.point(lambda v: int(v * 0.35))", "bloom.point(lambda v: int(v * 0.5))")
p.write_text(s, encoding="utf-8", newline="\n")
print("retocado")
