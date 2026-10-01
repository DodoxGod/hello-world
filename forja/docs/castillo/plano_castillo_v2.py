"""Plan v2: the double-ward castle, 201 x 201, ground floor, with a size comparison beside it. North up."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
S = 4                       # pixels a block
X0, Z0 = -14, -14
PW, PH = 230, 262
HEAD, MARGIN, SIDE = 70, 24, 560

BG = (22, 20, 19)
PAPER = (38, 34, 31)
INK = (238, 227, 204)
SOFT = (170, 158, 138)
FIRE = (196, 104, 44)
STEEL = (96, 112, 134)
NOBLE = (150, 62, 78)
LORE = (70, 130, 128)
SERVICE = (120, 108, 72)
WATER = (62, 110, 170)
WALL = (92, 88, 84)
YARD = (58, 66, 50)
LOW = (72, 70, 48)
MOAT = (120, 58, 24)
GOLD = (255, 208, 138)

font = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 13)
small = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 13)
tiny = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 12)
title = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 20)
big = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 28)

W = MARGIN * 3 + PW * S + SIDE
H = HEAD + PH * S + MARGIN
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img, "RGBA")
OX, OY = MARGIN, HEAD
d.rectangle((OX, OY, OX + PW * S, OY + PH * S), fill=PAPER, outline=(70, 62, 54))


def px(x, z):
    return OX + (x - X0) * S, OY + (z - Z0) * S


def rect(x0, z0, x1, z1, fill, outline=(16, 14, 13), width=2, alpha=255):
    a, b = px(x0, z0), px(x1, z1)
    d.rectangle((a[0], a[1], b[0], b[1]), fill=fill + (alpha,), outline=outline, width=width)


def circle(x, z, r, fill):
    c = px(x, z)
    d.ellipse((c[0] - r * S, c[1] - r * S, c[0] + r * S, c[1] + r * S), fill=fill, outline=(16, 14, 13), width=2)


def label(number, name, x, z, width, colour=INK):
    cx, cy = px(x, z)
    lines, line = [], ""
    for word in name.split(" "):
        trial = (line + " " + word).strip()
        if d.textlength(trial, font=small) <= width or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    lines.append(line)
    top = cy - (20 + len(lines) * 14) / 2
    if number:
        d.ellipse((cx - 10, top, cx + 10, top + 20), fill=(20, 18, 17), outline=GOLD, width=2)
        text = str(number)
        d.text((cx - d.textlength(text, font=font) / 2, top + 1), text, font=font, fill=(255, 226, 170))
    for i, part in enumerate(lines):
        d.text((cx - d.textlength(part, font=small) / 2, top + (21 if number else 6) + i * 14), part, font=small, fill=colour)


def room(number, name, x0, z0, x1, z1, fill):
    rect(x0, z0, x1, z1, fill)
    label(number, name, (x0 + x1) / 2, (z0 + z1) / 2, (x1 - x0) * S - 6)


def door(x, z, horizontal=True):
    cx, cy = px(x, z)
    box = (cx - 8, cy - 3, cx + 8, cy + 3) if horizontal else (cx - 3, cy - 8, cx + 3, cy + 8)
    d.rectangle(box, fill=(255, 214, 90), outline=(40, 30, 10))


def note(x, z, text, colour=SOFT):
    d.text(px(x, z), text, font=tiny, fill=colour)


def danger(x, z, text):
    cx, cy = px(x, z)
    d.polygon([(cx, cy - 7), (cx + 7, cy), (cx, cy + 7), (cx - 7, cy)], fill=(220, 50, 50), outline=(40, 0, 0))
    d.text((cx + 10, cy - 7), text, font=tiny, fill=(255, 150, 140))


# ---------------------------------------------------------------- the outer ward
rect(-12, -12, 213, 213, MOAT, outline=(60, 28, 10), alpha=150)
rect(-5, -5, 206, 206, PAPER, outline=(60, 28, 10))
rect(0, 0, 201, 201, WALL)
rect(5, 5, 196, 196, LOW)
rect(94, 212, 107, 244, (80, 74, 68))
label(1, "Puente de escoria", 100.5, 232, 110)
room(2, "Barbacana exterior", 86, 186, 115, 212, STEEL)
# eight outer towers: corners and mid-wall
for x, z in ((-7, -7), (190, -7), (-7, 190), (190, 190), (92, -7), (-7, 92), (190, 92)):
    rect(x, z, x + 18, z + 18, STEEL)
note(-6, 209, "8 torres exteriores (esquinas y centro de cada lienzo)", INK)

# what lives between the two walls
room(29, "Liza de justas", 8, 128, 84, 142, SERVICE)
room(30, "Campo de tiro", 8, 146, 60, 160, SERVICE)
room(9, "Caballerizas y talabartería", 8, 164, 60, 182, SERVICE)
room(10, "Calle de la Lonja (puestos de mercado)", 118, 164, 192, 182, SERVICE)
room(31, "Casas de aprendices (6)", 118, 128, 192, 158, SERVICE)
room(32, "Cantera del cincel", 8, 8, 34, 60, LORE)
room(33, "Cementerio de herreros", 166, 8, 192, 60, LORE)
room(34, "Huerto y leñera", 8, 66, 34, 120, SERVICE)
room(35, "Campamento de saqueadores", 166, 66, 192, 120, STEEL)
label(0, "RECINTO BAJO", 100, 170, 140, (220, 214, 170))
danger(168, 112, "Capitán y banda")
danger(170, 52, "Corazas vacías (de noche)")

# ---------------------------------------------------------------- the inner ward
rect(40, 8, 161, 122, WALL)
rect(45, 13, 156, 117, YARD)
room(36, "Barbacana interior", 90, 110, 111, 128, STEEL)
for number, name, x, z in ((4, "Torre del Fuelle", 34, 106), (5, "Torre del Vigía", 149, 106), (6, "Torre del Archivo", 34, 2), (7, "Torre de las Pavesas", 149, 2)):
    rect(x, z, x + 18, z + 18, STEEL)
    label(number, name, x + 9, z + 9, 66)
# the keep
rect(72, 14, 129, 66, (70, 40, 46))
room(17, "Gran salón del gremio (doble altura) 47 × 33", 77, 30, 124, 63, NOBLE)
room(18, "Trono del Gran Maestre", 86, 16, 115, 28, NOBLE)
note(74, 17, "▼ cripta", (255, 140, 120))
note(118, 17, "▲ plantas", (120, 255, 160))
# north blocks and wings
room(16, "Biblioteca de plantillas", 54, 14, 70, 40, LORE)
room(15, "Capilla del Yunque", 131, 14, 147, 40, LORE)
room(11, "Armería", 46, 44, 70, 72, STEEL)
room(12, "Cuartel y comedor", 46, 75, 70, 104, SERVICE)
room(13, "Gran fundición (doble altura) 25 × 45", 131, 44, 155, 89, FIRE)
room(14, "Taller del maestro", 131, 92, 155, 104, FIRE)
label(3, "Patio de armas", 100, 76, 120)
circle(100, 92, 6, FIRE)
note(86, 99.5, "Monumento: la Fragua Muerta", (255, 190, 120))
danger(134, 84, "Autómatas")
danger(80, 58, "Élites · Guardián de cuño")
for x, z, horizontal in ((100.5, 186, True), (100.5, 212, True), (100.5, 110, True), (100.5, 128, True), (100.5, 63, True), (100.5, 29, True),
                         (70, 58, False), (70, 90, False), (131, 66, False), (131, 98, False), (62, 42, True), (139, 42, True),
                         (71, 27, False), (130, 27, False), (58, 73.5, True), (143, 90.5, True)):
    door(x, z, horizontal)

# ---------------------------------------------------------------- heading
d.text((MARGIN, 8), "EL BASTIÓN DEL GREMIO · plano v2: DOBLE RECINTO, 201 × 201", font=big, fill=INK)
d.text((MARGIN, 42), "Planta baja. Sótanos y plantas altas son los del plano v1, a la nueva escala (cripta, mazmorras, carbonera, Sala del Temple de 45 × 45, Fragua Profunda de Ø 51, cámara acorazada).",
       font=small, fill=SOFT)

# ---------------------------------------------------------------- the comparison, to one scale
cx0 = MARGIN * 2 + PW * S
d.text((cx0, HEAD), "¿CUÁNTO ES ENORME?  Todo a la misma escala", font=title, fill=GOLD)
C = 2.2
base_y = HEAD + 50
things = [
    ("Bastión v2 (doble recinto)", 201, 201, (150, 62, 78)),
    ("Bastión v1 (el primer plano)", 101, 101, (96, 112, 134)),
    ("Mansión del bosque (aprox.)", 70, 70, (120, 108, 72)),
    ("Monumento oceánico", 58, 58, (62, 110, 170)),
]
d.rectangle((cx0, base_y, cx0 + 250 * C, base_y + 250 * C), outline=(232, 96, 220), width=2)
d.text((cx0 + 6, base_y + 250 * C - 20), "tope técnico del jigsaw ≈ 250 × 250 (128 bloques desde el centro)", font=tiny, fill=(232, 140, 225))
for name, w, h, colour in things:
    d.rectangle((cx0, base_y, cx0 + w * C, base_y + h * C), fill=colour + (230,), outline=(16, 14, 13), width=2)
    d.text((cx0 + 6, base_y + h * C - 20), f"{name} · {w} × {h}", font=tiny, fill=INK)
ty = base_y + 250 * C + 24
lines = [
    ("NUEVO EN EL RECINTO BAJO", GOLD),
    ("29 Liza de justas: carril de 76, palenque y tribuna (sinergia Justa, bardas)", INK),
    ("30 Campo de tiro: dianas a 20/35/50, arcos y flechas modulares", INK),
    ("31 Casas de aprendices: 6 casitas con su fragua pequeña", INK),
    ("32 Cantera del cincel: los 52 bloques tallados del cincel, a la vista", INK),
    ("33 Cementerio de herreros: lápidas con yunque; de noche, corazas", INK),
    ("34 Huerto y leñera (Cosechador, Leñador)", INK),
    ("35 Campamento de saqueadores: tienen tomado el recinto bajo", INK),
    ("36 Barbacana interior: segundo rastrillo antes del patio", INK),
    (" 8 Adarves: uno por muralla, unidos por dos puentes cubiertos", INK),
    ("", INK),
    ("Torre del homenaje de ~65 de alto · 12 torres · dos adarves.", SOFT),
    ("El recinto interior es el del plano v1 un 30 % más grande.", SOFT),
]
for i, (text, colour) in enumerate(lines):
    d.text((cx0, ty + i * 19), text, font=small, fill=colour)
img.save(OUT / "plano_v2_doble_recinto.png")
print(img.size)
