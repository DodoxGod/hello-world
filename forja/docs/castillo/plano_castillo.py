"""Floor plans of the castle proposal: basements, ground floor, upper floors. Blocks -> pixels, north up."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
OUT.mkdir(parents=True, exist_ok=True)

S = 6                      # pixels a block
X0, Z0 = -10, -10          # world coordinate at the panel's top-left
PW, PH = 122, 144          # panel size in blocks
PANEL_W, PANEL_H = PW * S, PH * S
MARGIN = 26
HEAD = 84
FOOT = 250

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
SECRET = (196, 160, 60)
WALL = (92, 88, 84)
YARD = (58, 66, 50)
MOAT = (120, 58, 24)

font = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 13)
small = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 13)
tiny = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 12)
title = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 22)
big = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 30)

W = MARGIN * 4 + PANEL_W * 3
H = HEAD + PANEL_H + FOOT
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img, "RGBA")


class Panel:
    def __init__(self, index, name):
        self.ox = MARGIN + index * (PANEL_W + MARGIN)
        self.oy = HEAD
        d.rectangle((self.ox, self.oy, self.ox + PANEL_W, self.oy + PANEL_H), fill=PAPER, outline=(70, 62, 54))
        d.text((self.ox + 8, self.oy - 30), name, font=title, fill=(255, 208, 138))

    def px(self, x, z):
        return self.ox + (x - X0) * S, self.oy + (z - Z0) * S

    def rect(self, x0, z0, x1, z1, fill, outline=(16, 14, 13), width=2, alpha=255):
        a = self.px(x0, z0)
        b = self.px(x1, z1)
        d.rectangle((a[0], a[1], b[0], b[1]), fill=fill + (alpha,), outline=outline, width=width)

    def circle(self, x, z, r, fill, outline=(16, 14, 13), width=2):
        c = self.px(x, z)
        d.ellipse((c[0] - r * S, c[1] - r * S, c[0] + r * S, c[1] + r * S), fill=fill, outline=outline, width=width)

    def room(self, number, name, x0, z0, x1, z1, fill, dashed=False):
        self.rect(x0, z0, x1, z1, fill)
        if dashed:
            self.dashed_box(x0, z0, x1, z1, (255, 226, 120))
        self.label(number, name, (x0 + x1) / 2, (z0 + z1) / 2, (x1 - x0) * S - 8)

    def round_room(self, number, name, x, z, r, fill):
        self.circle(x, z, r, fill)
        self.label(number, name, x, z, r * 2 * S - 16)

    def label(self, number, name, x, z, width):
        cx, cy = self.px(x, z)
        words = name.split(" ")
        lines, line = [], ""
        for word in words:
            trial = (line + " " + word).strip()
            if d.textlength(trial, font=small) <= width or not line:
                line = trial
            else:
                lines.append(line)
                line = word
        lines.append(line)
        total = 20 + len(lines) * 14
        top = cy - total / 2
        d.ellipse((cx - 10, top, cx + 10, top + 20), fill=(20, 18, 17), outline=(255, 208, 138), width=2)
        text = str(number)
        d.text((cx - d.textlength(text, font=font) / 2, top + 1), text, font=font, fill=(255, 226, 170))
        for i, part in enumerate(lines):
            d.text((cx - d.textlength(part, font=small) / 2, top + 21 + i * 14), part, font=small, fill=INK)

    def door(self, x, z, horizontal=True):
        cx, cy = self.px(x, z)
        if horizontal:
            d.rectangle((cx - 9, cy - 3, cx + 9, cy + 3), fill=(255, 214, 90), outline=(40, 30, 10))
        else:
            d.rectangle((cx - 3, cy - 9, cx + 3, cy + 9), fill=(255, 214, 90), outline=(40, 30, 10))

    def stairs(self, x, z, up=True, size=5):
        a = self.px(x, z)
        b = self.px(x + size, z + size)
        d.rectangle((a[0], a[1], b[0], b[1]), fill=(24, 22, 20), outline=(255, 255, 255), width=1)
        for i in range(1, size):
            y = a[1] + i * S
            d.line((a[0], y, b[0], y), fill=(200, 200, 200), width=1)
        mark = "▲" if up else "▼"
        d.text((a[0] + size * S / 2 - 5, a[1] + size * S / 2 - 8), mark, font=font, fill=(120, 255, 160) if up else (255, 140, 120))

    def dashed(self, points, colour=(232, 96, 220), width=3):
        for (x0, z0), (x1, z1) in zip(points, points[1:]):
            a = self.px(x0, z0)
            b = self.px(x1, z1)
            length = max(1.0, ((b[0] - a[0]) ** 2 + (b[1] - a[1]) ** 2) ** 0.5)
            steps = int(length // 10)
            for i in range(steps + 1):
                if i % 2 == 0:
                    t0, t1 = i / (steps + 1), min(1.0, (i + 1) / (steps + 1))
                    d.line((a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0, a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1),
                           fill=colour, width=width)

    def dashed_box(self, x0, z0, x1, z1, colour):
        self.dashed([(x0, z0), (x1, z0), (x1, z1), (x0, z1), (x0, z0)], colour, 2)

    def danger(self, x, z, text):
        cx, cy = self.px(x, z)
        d.polygon([(cx, cy - 8), (cx + 8, cy), (cx, cy + 8), (cx - 8, cy)], fill=(220, 50, 50), outline=(40, 0, 0))
        d.text((cx + 10, cy - 7), text, font=tiny, fill=(255, 150, 140))

    def note(self, x, z, text, colour=SOFT):
        cx, cy = self.px(x, z)
        d.text((cx, cy), text, font=tiny, fill=colour)


def shell(p, moat=True, towers=True, filled_wall=True):
    """What every level shares: the moat, the curtain wall and the four towers."""
    if moat:
        p.rect(-9, -9, 110, 111, MOAT, outline=(60, 28, 10), alpha=150)
        p.rect(-3, -3, 104, 104, PAPER, outline=(60, 28, 10))
    p.rect(0, 0, 101, 101, WALL)
    p.rect(5, 5, 96, 96, YARD if filled_wall else PAPER)


# ================================================================ ground floor
g = Panel(1, "PLANTA BAJA  ·  patio, alas y torre del homenaje")
shell(g)
# the bridge and the gatehouse
g.rect(46, 106, 55, 132, (80, 74, 68))
g.label(1, "Puente de escoria", 50.5, 121, 120)
g.room(2, "Barbacana", 40, 88, 61, 106, STEEL)
# towers
for number, name, x, z in ((4, "Torre del Fuelle", -6, 91), (5, "Torre del Vigía", 91, 91), (6, "Torre del Archivo", -6, -6), (7, "Torre de las Pavesas", 91, -6)):
    g.room(number, name, x, z, x + 16, z + 16, STEEL)
    g.stairs(x + 1 if x < 50 else x + 11, z + 1 if z < 50 else z + 11, True, 4)
# the keep
g.rect(30, 6, 71, 45, (70, 40, 46))
g.room(17, "Gran salón del gremio (doble altura)", 33, 19, 68, 43, NOBLE)
g.room(18, "Trono del Gran Maestre", 40, 8, 61, 18, NOBLE)
g.stairs(63, 8, True, 6)
g.stairs(32, 8, False, 6)
# north blocks
g.room(16, "Biblioteca de plantillas", 11, 11, 27, 27, LORE)
g.room(15, "Capilla del Yunque", 74, 11, 90, 27, LORE)
# wings
g.room(11, "Armería", 6, 31, 27, 55, STEEL)
g.room(12, "Cuartel y comedor", 6, 58, 27, 84, SERVICE)
g.room(13, "Gran fundición (doble altura)", 74, 31, 95, 63, FIRE)
g.room(14, "Taller del maestro", 74, 66, 95, 84, FIRE)
g.room(9, "Caballerizas y talabartería", 12, 86, 38, 95, SERVICE)
g.room(10, "Lonja del gremio", 63, 86, 89, 95, SERVICE)
# courtyard
g.label(3, "Patio de armas", 50, 54, 160)
g.danger(93, 7.5, "Pavesas")
g.circle(50, 68, 5, FIRE)
g.note(43, 74.5, "Monumento: la Fragua Muerta", (255, 190, 120))
g.circle(38, 80, 2, WATER)
g.note(33, 83, "pozo", SOFT)
g.note(56, 80, "campo de prácticas", SOFT)
# doors
for x, z, horizontal in ((50.5, 88, True), (50.5, 106, True), (50.5, 43, True), (50.5, 18.5, True), (27, 43, False), (27, 70, False), (74, 47, False),
                         (74, 75, False), (26, 86, True), (75, 86, True), (28.5, 14, False), (72.5, 14, False), (16, 29, True), (84, 29, True),
                         (16, 56.5, True), (84, 64.5, True), (11, 13, False), (90, 13, False), (12, 91, False), (89, 91, False)):
    g.door(x, z, horizontal)
g.danger(43, 103, "2 Corazas vacías")
g.danger(77, 58, "2 Autómatas de forja")
g.danger(8, 80, "Capitán saqueador y banda")
g.danger(37, 39, "Élites · Guardián de cuño")

# ================================================================ basements
b = Panel(0, "SÓTANOS  ·  nivel −1 y, debajo, nivel −2")
b.rect(0, 0, 101, 101, (48, 44, 41), outline=(70, 62, 54))
b.room(26, "Cripta de los Nueve Maestros", 30, 6, 71, 45, LORE)
b.stairs(32, 8, True, 6)
b.room(24, "Mazmorras (forjadores presos)", 6, 31, 27, 84, SERVICE)
b.room(23, "Carbonera y montacargas", 74, 31, 95, 63, SERVICE)
b.room(25, "Sala del Temple: cuatro estanques (agua · lava · nieve · miel)", 33, 51, 68, 84, WATER)
# corridors
b.rect(48, 45, 53, 51, (70, 66, 62))
b.rect(27, 64, 33, 69, (70, 66, 62))
b.rect(68, 56, 74, 61, (70, 66, 62))
for x, z, horizontal in ((50.5, 45, True), (50.5, 51, True), (27, 66.5, False), (33, 66.5, False), (68, 58.5, False), (74, 58.5, False)):
    b.door(x, z, horizontal)
b.stairs(76, 33, True, 5)
b.note(82, 34, "sube a la fundición", SOFT)
b.stairs(8, 33, True, 5)
b.note(14, 34, "sube a la armería", SOFT)
b.stairs(36, 74, False, 6)
b.note(43, 76, "baja al nivel −2", (255, 150, 140))
b.danger(77, 58, "Cargadores de carbón")
b.danger(36, 55, "Templadores")
b.danger(34, 40, "Corazas vacías (despiertan al abrir el cofre)")
# level -2, drawn below as its own inset
b.rect(-6, 92, 108, 131, (30, 27, 25), outline=(255, 208, 138))
b.note(34, 93, "NIVEL −2  (bajo la Sala del Temple)", (255, 208, 138))
b.round_room(28, "La Fragua Profunda: estrella de lava, fragua muerta (ritual del Herrero Caído)", 58, 112, 17, FIRE)
b.room(27, "Cámara acorazada", 6, 103, 30, 121, SECRET, dashed=True)
b.rect(30, 110, 41, 114, (70, 66, 62))
b.door(30, 112, False)
b.door(41, 112, False)
b.stairs(80, 97, True, 5)
b.note(86, 98, "sube al Temple", SOFT)
b.dashed([(2, 2), (2, 96), (10, 103)])
b.note(4, 86.5, "escalera secreta: baja de la Torre del Archivo", (232, 140, 225))
b.dashed([(20, 80), (20, 103)])
b.note(22, 97, "celda 7: palanca oculta", (232, 140, 225))
b.danger(52, 125, "JEFE (si haces el ritual)")

# ================================================================ upper floors
u = Panel(2, "PLANTAS ALTAS  ·  adarve, nivel 2 y, en recuadro, niveles 3 y 4")
shell(u, moat=False)
u.rect(5, 5, 96, 96, (30, 34, 28))
u.note(34, 58, "(vacío: el patio, abajo)", (120, 130, 110))
u.label(8, "Adarve (camino de ronda)", 25, 98.5, 170)
for x, z in ((-6, 91), (91, 91), (-6, -6), (91, -6)):
    u.rect(x, z, x + 16, z + 16, STEEL)
    u.stairs(x + 1 if x < 50 else x + 11, z + 1 if z < 50 else z + 11, False, 4)
u.note(-5, 100, "4 · sala de fuelles", INK)
u.note(92, 96, "5 · almenara y", INK)
u.note(92, 99, "catalejo", INK)
u.note(-5, 3, "6 · archivo alto", INK)
u.note(92, 0, "7 · brasero", INK)
u.note(92, 3, "abierto", INK)
u.room(2, "Sala del rastrillo (torno)", 40, 88, 61, 106, STEEL)
# keep level 2
u.rect(30, 6, 71, 45, (70, 40, 46))
u.rect(38, 23, 63, 39, (34, 24, 27))
u.note(41, 30, "(vacío: el Gran salón)", (170, 130, 136))
u.label(19, "Galería alta del salón", 50.5, 41.5, 200)
u.room(21, "Aposentos del Gran Maestre", 40, 8, 61, 18, NOBLE)
u.stairs(63, 8, True, 6)
u.room(16, "Biblioteca alta", 11, 11, 27, 27, LORE)
u.room(15, "Coro de la capilla", 74, 11, 90, 27, LORE)
# bridges from the keep to the wall walk and the side blocks
u.rect(27, 12, 30, 16, (110, 100, 90))
u.rect(71, 12, 74, 16, (110, 100, 90))
u.rect(48, 0, 53, 6, (110, 100, 90))
u.note(54, 1.5, "pasarela al adarve norte", INK)
u.rect(74, 31, 95, 63, (60, 40, 28))
u.note(76, 44, "pasarela de grúas sobre", (255, 190, 120))
u.note(76, 47, "la fundición (caños)", (255, 190, 120))
for x, z, horizontal in ((28.5, 14, False), (72.5, 14, False), (50.5, 6, True), (50.5, 18.5, True), (84, 29, True)):
    u.door(x, z, horizontal)
# inset: levels 3 and 4
u.rect(-6, 108, 108, 131, (30, 27, 25), outline=(255, 208, 138))
u.note(-4, 109, "TORRE DEL HOMENAJE · niveles 3 y 4", (255, 208, 138))
u.room(20, "Sala de mapas", 2, 113, 24, 129, NOBLE)
u.room(19, "Galería de trofeos (leyendas)", 27, 113, 53, 129, NOBLE)
u.round_room(22, "Observatorio", 78, 121, 9, LORE)
u.stairs(55, 118, True, 5)
u.note(61, 116, "nivel 4 →", SOFT)

# ================================================================ heading and legend
d.text((MARGIN, 10), "EL BASTIÓN DEL GREMIO  ·  propuesta de castillo (101 × 101 bloques de muralla, 4 plantas + 2 sótanos, 28 espacios)", font=big, fill=INK)
ly = HEAD + PANEL_H + 18
items = [
    (FIRE, "Forja y fuego"), (STEEL, "Militar: puertas, torres, armas"), (NOBLE, "Torre del homenaje"), (LORE, "Saber y culto"),
    (SERVICE, "Servicio"), (WATER, "Temple / agua"), (SECRET, "Secreto"),
]
x = MARGIN
for colour, text in items:
    d.rectangle((x, ly, x + 22, ly + 16), fill=colour, outline=(16, 14, 13))
    d.text((x + 28, ly - 1), text, font=small, fill=INK)
    x += 48 + int(d.textlength(text, font=small))
d.rectangle((x, ly + 5, x + 18, ly + 11), fill=(255, 214, 90), outline=(40, 30, 10))
d.text((x + 24, ly - 1), "puerta", font=small, fill=INK)
x += 84
d.rectangle((x, ly, x + 16, ly + 16), fill=(24, 22, 20), outline=(255, 255, 255))
d.text((x + 3, ly - 2), "▲", font=font, fill=(120, 255, 160))
d.text((x + 22, ly - 1), "escalera (▲ sube · ▼ baja)", font=small, fill=INK)
x += 200
d.line((x, ly + 8, x + 12, ly + 8), fill=(232, 96, 220), width=3)
d.line((x + 18, ly + 8, x + 30, ly + 8), fill=(232, 96, 220), width=3)
d.text((x + 38, ly - 1), "pasadizo secreto", font=small, fill=INK)
x += 160
d.polygon([(x + 8, ly), (x + 16, ly + 8), (x + 8, ly + 16), (x, ly + 8)], fill=(220, 50, 50))
d.text((x + 24, ly - 1), "enemigos con nombre", font=small, fill=INK)

route = [
    "RECORRIDO PENSADO:  puente (1) → barbacana (2) → patio (3).  Desde el patio se elige: el ALA OESTE (armería 11 → cuartel 12 → mazmorras 24), el ALA ESTE (fundición 13 → taller 14 → carbonera 23)",
    "o la TORRE DEL HOMENAJE (salón 17 → trono 18 → arriba: aposentos 21, trofeos 19, mapas 20, observatorio 22).  La escalera noroeste del salón baja a la CRIPTA (26) → SALA DEL TEMPLE (25) → LA FRAGUA PROFUNDA (28).",
    "Todo el sótano −1 se comunica (mazmorras ↔ temple ↔ carbonera), así que hay tres maneras de llegar al jefe.  La CÁMARA ACORAZADA (27) sólo se alcanza por secreto: la estantería falsa de la Torre del Archivo (6) o la palanca de la celda 7.",
    "El ADARVE (8) une las cuatro torres, la sala del rastrillo y la torre del homenaje por pasarelas: se puede rodear el castillo entero por arriba sin pisar el patio.",
]
for i, line in enumerate(route):
    d.text((MARGIN, ly + 34 + i * 20), line, font=small, fill=(215, 204, 182))
d.text((MARGIN, ly + 34 + len(route) * 20 + 10),
       "Tamaño real: muralla 101 × 101, torres 16 × 16, torre del homenaje 41 × 39 y ~40 de alto con el observatorio; foso de escoria (magma y basalto) de 6 de ancho. Norte arriba; cada píxel gordo = 1 bloque a escala 6.",
       font=tiny, fill=SOFT)
img.save(OUT / "plano_v1.png")
print(img.size)
