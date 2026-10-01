"""Plan v3: the double-ward castle, every level drawn, every space numbered. North up, a block is three pixels."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
S = 3
X0, Z0 = -20, -20
PW, PH = 241, 270
PANEL_W, PANEL_H = PW * S, PH * S
MARGIN, HEAD, GAP = 22, 92, 40

BG = (22, 20, 19)
PAPER = (38, 34, 31)
INK = (238, 227, 204)
SOFT = (170, 158, 138)
GOLD = (255, 208, 138)
FIRE = (196, 104, 44)
STEEL = (96, 112, 134)
NOBLE = (150, 62, 78)
LORE = (70, 130, 128)
SERVICE = (120, 108, 72)
WATER = (62, 110, 170)
SECRET = (196, 160, 60)
WALL = (92, 88, 84)
GHOST = (52, 48, 45)
YARD = (58, 66, 50)
LOW = (72, 70, 48)
MOAT = (120, 58, 24)
ROAD = (104, 94, 78)

badge = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 11)
small = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 12)
tiny = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 11)
index_font = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 12)
index_bold = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 12)
title = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 18)
big = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 27)

W = MARGIN * 4 + PANEL_W * 3
H = HEAD + (PANEL_H + GAP) * 2 + 10
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img, "RGBA")

index = []          # (number, name, extra, zone)
numbers = {}        # key -> number


def number_of(key, name=None, extra="", zone=""):
    if key not in numbers:
        numbers[key] = len(numbers) + 1
        index.append((numbers[key], name, extra, zone))
    return numbers[key]


class Panel:
    def __init__(self, column, row, name):
        self.ox = MARGIN + column * (PANEL_W + MARGIN)
        self.oy = HEAD + row * (PANEL_H + GAP)
        d.rectangle((self.ox, self.oy, self.ox + PANEL_W, self.oy + PANEL_H), fill=PAPER, outline=(70, 62, 54))
        d.text((self.ox + 4, self.oy - 25), name, font=title, fill=GOLD)

    def px(self, x, z):
        return self.ox + (x - X0) * S, self.oy + (z - Z0) * S

    def rect(self, x0, z0, x1, z1, fill, outline=(16, 14, 13), width=1, alpha=255):
        a, b = self.px(x0, z0), self.px(x1, z1)
        d.rectangle((a[0], a[1], b[0], b[1]), fill=fill + (alpha,), outline=outline, width=width)

    def circle(self, x, z, r, fill, outline=(16, 14, 13)):
        c = self.px(x, z)
        d.ellipse((c[0] - r * S, c[1] - r * S, c[0] + r * S, c[1] + r * S), fill=fill, outline=outline, width=1)

    def badge(self, number, x, z, name=None, width=0):
        cx, cy = self.px(x, z)
        text = str(number)
        shown = name is not None and d.textlength(name, font=tiny) <= width
        top = cy - (16 if shown else 8)
        d.ellipse((cx - 9, top, cx + 9, top + 17), fill=(20, 18, 17), outline=GOLD, width=1)
        d.text((cx - d.textlength(text, font=badge) / 2, top + 1), text, font=badge, fill=(255, 226, 170))
        if shown:
            d.text((cx - d.textlength(name, font=tiny) / 2, top + 18), name, font=tiny, fill=INK)

    def room(self, key, x0, z0, x1, z1, fill, name=None, extra="", zone="", show=True, dashed=False):
        self.rect(x0, z0, x1, z1, fill)
        if dashed:
            self.dashed([(x0, z0), (x1, z0), (x1, z1), (x0, z1), (x0, z0)], (255, 226, 120), 2)
        n = number_of(key, name, extra, zone)
        label = name if show and (z1 - z0) * S >= 36 else None
        self.badge(n, (x0 + x1) / 2, (z0 + z1) / 2, label, (x1 - x0) * S - 6)
        if extra:
            a = self.px(x1, z0)
            d.polygon([(a[0] - 8, a[1] + 2), (a[0] - 3, a[1] + 7), (a[0] - 8, a[1] + 12), (a[0] - 13, a[1] + 7)], fill=(225, 55, 55))

    def round_room(self, key, x, z, r, fill, name=None, extra="", zone=""):
        self.circle(x, z, r, fill)
        n = number_of(key, name, extra, zone)
        self.badge(n, x, z, name, r * 2 * S - 10)
        if extra:
            c = self.px(x + r * 0.6, z - r * 0.6)
            d.polygon([(c[0], c[1] - 5), (c[0] + 5, c[1]), (c[0], c[1] + 5), (c[0] - 5, c[1])], fill=(225, 55, 55))

    def door(self, x, z, horizontal=True):
        cx, cy = self.px(x, z)
        box = (cx - 5, cy - 2, cx + 5, cy + 2) if horizontal else (cx - 2, cy - 5, cx + 2, cy + 5)
        d.rectangle(box, fill=(255, 214, 90), outline=(40, 30, 10))

    def stairs(self, x, z, up=True, size=6):
        a, b = self.px(x, z), self.px(x + size, z + size)
        d.rectangle((a[0], a[1], b[0], b[1]), fill=(24, 22, 20), outline=(255, 255, 255), width=1)
        mark = "▲" if up else "▼"
        d.text((a[0] + size * S / 2 - 5, a[1] + size * S / 2 - 8), mark, font=badge, fill=(120, 255, 160) if up else (255, 140, 120))

    def dashed(self, points, colour=(232, 96, 220), width=2):
        for (x0, z0), (x1, z1) in zip(points, points[1:]):
            a, b = self.px(x0, z0), self.px(x1, z1)
            length = max(1.0, ((b[0] - a[0]) ** 2 + (b[1] - a[1]) ** 2) ** 0.5)
            steps = int(length // 8)
            for i in range(steps + 1):
                if i % 2 == 0:
                    t0, t1 = i / (steps + 1), min(1.0, (i + 1) / (steps + 1))
                    d.line((a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0, a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1),
                           fill=colour, width=width)

    def note(self, x, z, text, colour=SOFT):
        d.text(self.px(x, z), text, font=tiny, fill=colour)


OUTER_TOWERS = (("t_cobre", "Torre del Cobre", -8, -8, "Herrumbre"), ("t_eco", "Torre del Eco", 91, -8, ""), ("t_obsidiana", "Torre de la Obsidiana", 189, -8, ""),
                ("t_resina", "Torre de la Resina", -8, 91, ""), ("t_hundida", "Torre Hundida (sin tejado)", 189, 91, "Herrumbre"),
                ("t_escama", "Torre de la Escama", -8, 189, ""), ("t_vidrio", "Torre del Vidrio", 189, 189, ""))
INNER_TOWERS = (("t_archivo", "Torre del Archivo", 34, 4, ""), ("t_pavesas", "Torre de las Pavesas", 149, 4, "Pavesas"),
                ("t_fuelle", "Torre del Fuelle", 34, 114, ""), ("t_vigia", "Torre del Vigía", 149, 114, ""))


def ghost(p):
    """The walls above, faint, so an underground level can be placed by eye."""
    p.rect(0, 0, 201, 201, GHOST, outline=(60, 56, 52))
    p.rect(6, 6, 195, 195, PAPER, outline=(60, 56, 52))
    p.rect(42, 12, 159, 124, GHOST, outline=(60, 56, 52))
    p.rect(47, 17, 154, 119, PAPER, outline=(60, 56, 52))
    p.rect(72, 17, 129, 70, (46, 42, 40), outline=(60, 56, 52))


# ======================================================================= ground floor
g = Panel(2, 0, "PLANTA BAJA · recinto bajo y recinto alto")
g.rect(-16, -16, 217, 217, MOAT, outline=(60, 28, 10), alpha=150)
g.rect(-8, -8, 209, 209, PAPER, outline=(60, 28, 10))
g.rect(0, 0, 201, 201, WALL)
g.rect(6, 6, 195, 195, LOW)
g.rect(94, 134, 107, 184, ROAD, outline=ROAD)
g.rect(94, 212, 107, 246, (80, 74, 68))
Z = "RECINTO BAJO · planta baja"
g.badge(number_of("puente", "Puente de escoria", "", Z), 100.5, 230)
g.room("barbacana", 84, 184, 117, 212, STEEL, "Barbacana exterior", "2 Corazas vacías", Z)
g.room("guardia", 119, 186, 141, 194, STEEL, "Cuerpo de guardia", "", Z)
g.room("caballerizas", 8, 172, 44, 184, SERVICE, "Caballerizas", "", Z)
g.room("talabarteria", 48, 172, 84, 184, SERVICE, "Talabartería", "", Z)
g.room("liza", 8, 138, 84, 152, SERVICE, "Liza de justas y tribuna", "", Z)
g.room("tiro", 8, 156, 60, 168, SERVICE, "Campo de tiro", "", Z)
g.room("arquero", 64, 156, 84, 168, SERVICE, "Taller del arquero", "", Z)
g.room("aprendices", 117, 138, 193, 152, SERVICE, "Casas de aprendices (6)", "Aprendices de la fragua", Z)
g.room("taberna", 117, 156, 145, 168, SERVICE, "Taberna «El Yunque Roto»", "", Z)
g.room("encargos", 149, 156, 193, 168, SERVICE, "Casa de encargos", "", Z)
g.room("lonja", 117, 172, 193, 184, SERVICE, "Calle de la Lonja", "saqueadores", Z)
g.room("cantera", 8, 14, 36, 50, LORE, "Cantera del cincel", "", Z)
g.room("aserradero", 8, 54, 36, 88, SERVICE, "Aserradero y leñera", "", Z)
g.room("huerto", 14, 92, 32, 132, SERVICE, "Huerto y colmenar", "", Z)
g.room("cementerio", 165, 14, 193, 50, LORE, "Cementerio de herreros", "Corazas vacías de noche", Z)
g.room("nevero", 165, 54, 193, 72, WATER, "Nevero", "", Z)
g.room("polvorin", 169, 76, 187, 88, FIRE, "Polvorín", "", Z)
g.room("campamento", 169, 112, 193, 134, STEEL, "Campamento de saqueadores", "Capitán saqueador y banda", Z)
g.rect(195, 116, 201, 130, (30, 27, 25), outline=(255, 120, 90), width=2)
g.badge(number_of("brecha", "Brecha de la muralla (entrada alternativa)", "", Z), 198, 123)
for key, name, x, z, extra in OUTER_TOWERS:
    g.room(key, x, z, x + 20, z + 20, STEEL, name, extra, Z, show=False)

Z = "RECINTO ALTO · planta baja"
g.rect(42, 12, 159, 124, WALL)
g.rect(47, 17, 154, 119, YARD)
g.room("barbacana_int", 90, 114, 111, 134, STEEL, "Barbacana interior", "Yunque andante", Z, show=False)
g.badge(number_of("patio", "Patio de armas: monumento a la Fragua Muerta, pozo", "", Z), 100, 78, "Patio de armas", 120)
g.circle(100, 90, 5, FIRE)
g.circle(84, 96, 2, WATER)
g.room("biblioteca", 48, 24, 70, 44, LORE, "Biblioteca", "", Z)
g.room("scriptorium", 48, 46, 70, 56, LORE, "Scriptorium", "", Z)
g.room("armeria", 48, 58, 70, 78, STEEL, "Armería", "Corazas vacías", Z)
g.room("estandartes", 48, 80, 70, 90, STEEL, "Sala de estandartes", "", Z)
g.room("cuartel", 48, 92, 70, 112, SERVICE, "Cuartel", "Élites", Z)
g.room("comedor", 72, 102, 88, 118, SERVICE, "Comedor", "", Z, show=False)
g.room("capilla", 131, 24, 153, 44, LORE, "Capilla del Yunque", "", Z)
g.room("sacristia", 131, 46, 153, 54, LORE, "Sacristía de talismanes", "", Z)
g.room("fundicion", 131, 56, 153, 98, FIRE, "Gran fundición", "2 Autómatas, Escoria viviente, Ascua mayor", Z)
g.room("taller", 131, 100, 153, 112, FIRE, "Taller del maestro", "", Z)
g.room("aleaciones", 113, 102, 129, 118, FIRE, "Aleaciones", "", Z, show=False)
g.rect(72, 17, 129, 70, (70, 40, 46))
g.room("salon", 78, 39, 123, 68, NOBLE, "Gran salón del gremio", "Élites", Z)
g.room("cunos", 84, 19, 117, 37, NOBLE, "Sala de cuños", "Guardián de cuño + Tenaza + Percutor (3 sellos)", Z)
g.stairs(120, 22, True, 6)
g.stairs(75, 22, False, 6)
for key, name, x, z, extra in INNER_TOWERS:
    g.room(key, x, z, x + 18, z + 18, STEEL, name, extra, Z, show=False)
for x, z, horizontal in ((100.5, 184, True), (100.5, 212, True), (100.5, 134, True), (100.5, 114, True), (100.5, 68, True), (100.5, 38, True),
                         (70, 68, False), (70, 102, False), (131, 76, False), (131, 106, False), (59, 45, True), (142, 45, True), (59, 57, True),
                         (59, 79, True), (59, 91, True), (142, 55, True), (142, 99, True), (71, 34, False), (130, 34, False), (80, 102, True),
                         (121, 102, True), (44, 178, False), (84, 162, False), (117, 162, False), (147, 162, False), (22, 52, True), (22, 90, True),
                         (179, 52, True), (179, 74, True)):
    g.door(x, z, horizontal)

# ======================================================================= level 2
u = Panel(0, 1, "NIVEL 2 · los dos adarves y las plantas altas de las alas")
Z = "NIVEL 2"
u.rect(0, 0, 201, 201, WALL)
u.rect(6, 6, 195, 195, (30, 34, 28))
u.rect(42, 12, 159, 124, WALL)
u.rect(47, 17, 154, 119, (30, 34, 28))
u.badge(number_of("adarve_ext", "Adarve exterior (rodea todo el castillo)", "", Z), 60, 198)
u.badge(number_of("adarve_int", "Adarve interior y dos puentes cubiertos entre murallas", "", Z), 66, 121.5)
u.rect(12, 99, 42, 103, (110, 100, 90))
u.rect(159, 99, 189, 103, (110, 100, 90))
for key, name, x, z, extra in OUTER_TOWERS:
    u.rect(x, z, x + 20, z + 20, STEEL)
    u.badge(numbers[key], x + 10, z + 10)
for key, name, x, z, extra in INNER_TOWERS:
    u.rect(x, z, x + 18, z + 18, STEEL)
    u.badge(numbers[key], x + 9, z + 9)
u.room("rastrillo", 84, 184, 117, 212, STEEL, "Sala del rastrillo", "", Z)
u.room("biblioteca_alta", 48, 24, 70, 44, LORE, "Biblioteca alta", "", Z)
u.room("esgrima", 48, 58, 70, 78, STEEL, "Sala de esgrima", "Yunque andante", Z)
u.room("dormitorios", 48, 92, 70, 112, SERVICE, "Dormitorios", "", Z)
u.room("coro", 131, 24, 153, 44, LORE, "Coro de la capilla", "", Z)
u.room("gruas", 131, 56, 153, 98, FIRE, "Galería de grúas y moldes", "Moldes rotos", Z)
u.room("orbes", 131, 100, 153, 112, FIRE, "Gabinete de orbes", "", Z)
u.rect(72, 17, 129, 70, (70, 40, 46))
u.rect(78, 39, 123, 68, NOBLE)
u.rect(86, 45, 115, 62, (34, 24, 27))
u.badge(number_of("galeria_alta", "Galería alta del salón", "", Z), 100, 65)
u.note(88, 51, "(vacío: el salón)", (170, 130, 136))
u.room("trono", 84, 19, 117, 37, NOBLE, "Trono del Gran Maestre", "", Z)
u.stairs(120, 22, True, 6)
u.rect(70, 32, 72, 36, (110, 100, 90))
u.rect(129, 32, 131, 36, (110, 100, 90))
u.rect(98, 12, 103, 17, (110, 100, 90))
u.note(62, 140, "(abajo: el recinto bajo, a cielo abierto)", (120, 130, 110))
u.note(76, 84, "(abajo: el patio de armas)", (120, 130, 110))

# ======================================================================= the keep, levels 3 to 5, and the section
k = Panel(1, 1, "TORRE DEL HOMENAJE · niveles 3, 4 y 5 · y el corte norte-sur")
Z = "TORRE DEL HOMENAJE · niveles 3 a 5"


def keep(ox, oz, caption):
    k.rect(ox, oz, ox + 57, oz + 53, (70, 40, 46))
    k.note(ox, oz - 6, caption, GOLD)


keep(-14, -6, "NIVEL 3")
k.room("consejo", -2, -4, 31, 14, NOBLE, "Consejo de los Nueve", "", Z)
k.room("trofeos", -12, 16, 12, 45, NOBLE, "Trofeos", "Corazas vacías", Z)
k.room("mapas", 16, 16, 41, 45, NOBLE, "Sala de mapas", "", Z)
k.stairs(34, -3, True, 6)
keep(62, -6, "NIVEL 4")
k.room("aposentos", 74, -4, 107, 14, NOBLE, "Aposentos del Gran Maestre", "", Z)
k.room("estudio", 64, 16, 88, 32, NOBLE, "Estudio", "", Z, show=False)
k.room("contaduria", 92, 16, 117, 32, SECRET, "Contaduría", "", Z, show=False)
k.room("terraza", 64, 34, 117, 45, (88, 96, 84), "Terraza sur", "", Z, show=False)
k.stairs(110, -3, True, 6)
keep(138, -6, "NIVEL 5 (azotea)")
k.round_room("observatorio", 166, 18, 13, LORE, "Observatorio", "Núcleo estelar", Z)
k.room("campanario", 176, 34, 192, 45, STEEL, "Campanario del cuerno", "", Z, show=False)

# the section: heights in blocks, ground at zero
base_z = 190
scale_y = 1.55


def level_box(x0, x1, y0, y1, fill, text, colour=INK):
    a = k.px(x0, base_z - y1 * scale_y)
    b = k.px(x1, base_z - y0 * scale_y)
    d.rectangle((a[0], a[1], b[0], b[1]), fill=fill, outline=(16, 14, 13))
    d.text((a[0] + 4, (a[1] + b[1]) / 2 - 7), text, font=tiny, fill=colour)


k.note(-14, 62, "CORTE NORTE–SUR POR EL EJE (alturas en bloques; suelo del patio = 0)", GOLD)
a = k.px(-16, base_z)
b = k.px(216, base_z)
d.line((a[0], a[1], b[0], b[1]), fill=(120, 200, 120), width=2)
level_box(-10, 4, 0, 14, WALL, "muralla N")
level_box(8, 66, 0, 9, NOBLE, "0 · cuños | gran salón (doble altura)")
level_box(8, 30, 9, 18, NOBLE, "2 · trono")
level_box(30, 66, 9, 18, (110, 56, 66), "(salón) galería alta")
level_box(8, 66, 18, 27, NOBLE, "3 · consejo | trofeos | mapas")
level_box(8, 66, 27, 36, NOBLE, "4 · aposentos | estudio | terraza")
level_box(20, 46, 36, 50, LORE, "5 · observatorio")
level_box(30, 36, 50, 64, STEEL, "aguja")
level_box(70, 112, 0, 1, YARD, "patio de armas")
level_box(114, 124, 0, 16, WALL, "muralla int.")
level_box(126, 176, 0, 1, LOW, "recinto bajo")
level_box(178, 196, 0, 22, STEEL, "barbacana")
level_box(198, 214, -3, 0, MOAT, "foso")
level_box(8, 66, -9, -1, LORE, "−1 · cripta")
level_box(70, 112, -9, -1, WATER, "−1 · Sala del Temple")
level_box(24, 50, -20, -11, LORE, "−2 · antesala")
level_box(54, 112, -24, -11, FIRE, "−2 · LA FRAGUA PROFUNDA (bóveda de 13)")
k.note(-14, 236, "La torre del homenaje sube 50 bloques y la aguja llega a 64; las murallas, 14–16; las torres, 22–30.", SOFT)
k.note(-14, 241, "Bajo tierra: 9 de sótano y, debajo, 13 de bóveda en la Fragua Profunda.", SOFT)

# ======================================================================= basement -1
b1 = Panel(1, 0, "SÓTANO −1 · bajo el recinto alto")
Z = "SÓTANO −1"
ghost(b1)
b1.room("cripta", 72, 17, 129, 70, LORE, "Cripta de los Nueve Maestros", "Corazas vacías al abrir el cofre", Z)
b1.stairs(75, 22, True, 6)
b1.rect(129, 27, 178, 33, (70, 66, 62))
b1.room("osario", 146, 20, 164, 40, LORE, "Osario", "", Z, show=False)
b1.stairs(178, 27, True, 6)
b1.note(166, 40, "sube al cementerio", SOFT)
b1.room("guardia_mazmorras", 48, 58, 70, 68, STEEL, "Guardia", "Tenazas", Z, show=False)
b1.room("mazmorras", 48, 70, 70, 112, SERVICE, "Mazmorras", "forjadores presos", Z)
b1.room("bodega", 72, 102, 88, 118, SERVICE, "Bodega", "", Z, show=False)
b1.room("carbonera", 131, 56, 153, 98, SERVICE, "Carbonera y montacargas", "Cargadores de carbón", Z)
b1.room("lingotes", 131, 100, 153, 112, SECRET, "Almacén de lingotes", "", Z, show=False)
b1.room("temple", 76, 74, 125, 98, WATER, "Sala del Temple: agua · lava · nieve · miel", "Templadores", Z)
b1.round_room("cisterna", 100, 110, 7, WATER, None, "", Z)
index[-1] = (index[-1][0], "Cisterna", "", Z)
for x0, z0, x1, z1 in ((98, 70, 103, 74), (70, 84, 76, 88), (125, 84, 131, 88), (98, 98, 103, 103), (88, 108, 93, 112)):
    b1.rect(x0, z0, x1, z1, (70, 66, 62))
b1.rect(153, 62, 176, 66, (70, 66, 62))
b1.stairs(176, 60, True, 6)
b1.badge(number_of("minas", "Galería de minas (sale junto al nevero)", "Cargadores de carbón", Z), 165, 64)
b1.rect(153, 90, 209, 94, (70, 66, 62))
b1.badge(number_of("desague", "Desagüe de escoria (sale al foso: entrada furtiva)", "Escoria viviente", Z), 182, 92)
b1.stairs(98, 76, False, 6)
b1.note(105, 77, "baja a la antesala", (255, 150, 140))
b1.stairs(50, 60, True, 5)
b1.stairs(133, 58, True, 5)
b1.dashed([(59, 108), (59, 116)])
b1.note(40, 118, "celda 7: palanca → −2", (232, 140, 225))
b1.dashed([(43, 13), (43, 22)])
b1.note(10, 24, "escalera secreta del Archivo (pasa de largo)", (232, 140, 225))

# ======================================================================= basement -2
b2 = Panel(0, 0, "SÓTANO −2 · lo más hondo")
Z = "SÓTANO −2"
ghost(b2)
b2.room("antesala", 86, 40, 115, 62, LORE, "Antesala de los Nueve", "Aprendices de la fragua", Z)
b2.stairs(98, 42, True, 6)
b2.rect(98, 62, 103, 67, (70, 66, 62))
b2.round_room("fragua", 100, 92, 25, FIRE, "LA FRAGUA PROFUNDA", "ritual: EL HERRERO CAÍDO", Z)
b2.rect(125, 90, 131, 94, (70, 66, 62))
b2.room("almas", 131, 80, 153, 104, (88, 120, 140), "Forja de almas", "Corazas vacías", Z)
b2.room("camara", 48, 80, 72, 104, SECRET, "Cámara acorazada", "Guardián (trampa)", Z, dashed=True)
b2.dashed([(43, 13), (43, 92), (48, 92)])
b2.note(8, 60, "escalera secreta: baja", (232, 140, 225))
b2.note(8, 65, "de la Torre del Archivo", (232, 140, 225))
b2.dashed([(59, 116), (59, 104)])
b2.note(50, 118, "desde la celda 7", (232, 140, 225))
b2.note(60, 124, "Estrella de la mesa de forja trazada con canales de lava en el suelo;", SOFT)
b2.note(60, 129, "fragua muerta en el centro, columnas, cadenas, bóveda de 13 de alto.", SOFT)
number_of("escalera_secreta", "Escalera secreta del Archivo (estantería falsa → cámara acorazada)", "", Z)

# ======================================================================= the index
ix = MARGIN + 2 * (PANEL_W + MARGIN)
iy = HEAD + PANEL_H + GAP
d.rectangle((ix, iy, ix + PANEL_W, iy + PANEL_H), fill=(30, 27, 25), outline=(70, 62, 54))
d.text((ix + 4, iy - 25), f"ÍNDICE · {len(index)} espacios  (rombo rojo = enemigos con nombre)", font=title, fill=GOLD)
column_w = PANEL_W // 2
x, y = ix + 8, iy + 8
last_zone = None
for number, name, extra, zone in index:
    rows = 1 + (1 if zone != last_zone else 0)
    if y + rows * 15 > iy + PANEL_H - 8:
        x += column_w
        y = iy + 8
    if zone != last_zone:
        d.text((x, y), zone, font=index_bold, fill=GOLD)
        y += 15
        last_zone = zone
    text = f"{number:>2}  {name}"
    d.text((x, y), text, font=index_font, fill=INK)
    if extra:
        w = d.textlength(text, font=index_font)
        room_left = column_w - 16 - w
        shown = extra
        while d.textlength(shown, font=tiny) > room_left - 18 and len(shown) > 8:
            shown = shown[:-2]
        mx, my = x + w + 10, y + 8
        d.polygon([(mx, my - 4), (mx + 4, my), (mx, my + 4), (mx - 4, my)], fill=(225, 55, 55))
        d.text((mx + 8, y + 1), shown, font=tiny, fill=(255, 150, 140))
    y += 15

d.text((MARGIN, 6), f"EL BASTIÓN DEL GREMIO · plano v3 · doble recinto 201 × 201 · 8 niveles · {len(index)} espacios", font=big, fill=INK)
d.text((MARGIN, 40), "Amarillo = puerta · ▲▼ = escalera · rosa discontinuo = pasadizo secreto · rombo rojo = enemigos con nombre · norte arriba · 1 bloque = 3 píxeles",
       font=small, fill=SOFT)
img.save(OUT / "plano_v3_completo.png")
print(img.size, len(index), "spaces")
