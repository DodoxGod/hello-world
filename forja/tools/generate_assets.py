"""Generates Forja's textures and the JSON files derived from the Java enums.

Run from the project root:  python tools/generate_assets.py   (needs Pillow and the Minecraft 26.2
client jar that Loom caches under ~/.gradle/caches/fabric-loom/26.2).

Item textures start from Minecraft's own sprites (iron pickaxe, axe, shovel, hoe, sword, mace, bow
and iron armor); the hammer and the spear use Forja's own pixel art. Each sprite is split into parts, head, handle, binding, guard, plate, lining, and every
part is saved as its own grayscale layer. The game tints each layer with its material's color
through custom_model_data tints, so one set of textures covers every material combination. Layers
never share a pixel, which avoids z-fighting when the item is drawn in 3D.

Worn armor uses Forja's own textures (plates with rivets and bands, a cloth lining at the edges),
also split into a plate layer and a lining layer and tinted per material through one equipment asset
per plate and lining combination.
"""

import io
import math
import json
import shutil
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/forja"
DATA = RES / "data/forja"
CLIENT_JAR = Path.home() / ".gradle/caches/fabric-loom/26.2/minecraft-client.jar"

# Mirrors of the Java enums. Keep in sync with PartType, ForgeType and ForgeMaterial.
PARTS = {
    "cabeza_pico": "HEAD", "cabeza_hacha": "HEAD", "cabeza_pala": "HEAD", "cabeza_azada": "HEAD",
    "cabeza_martillo": "HEAD", "hoja": "HEAD", "punta_lanza": "HEAD", "punta_tridente": "HEAD", "punta_cincel": "HEAD", "cabeza_mazo": "HEAD", "membrana": "PLATE", "bola": "HEAD", "cadena": "EXTRA",
    "nudillos": "HEAD", "manopla": "HANDLE", "remache": "EXTRA", "punta_flecha": "HEAD", "emplumado": "EXTRA",
    "placa_barda": "PLATE", "placa_lobo": "PLATE", "garfio": "HEAD", "mango": "HANDLE", "atadura": "EXTRA", "guarda": "EXTRA",
    "placa_casco": "PLATE", "placa_pechera": "PLATE", "placa_grebas": "PLATE", "placa_botas": "PLATE", "forro": "LINING",
    "brazos_arco": "HEAD", "cuerda": "EXTRA", "placa_escudo": "PLATE", "borde_escudo": "EXTRA",
    "nucleo": "HEAD", "engaste": "EXTRA", "tapas": "HANDLE",
}
TYPES = {
    "pico": ["cabeza_pico", "mango", "atadura"],
    "hacha": ["cabeza_hacha", "mango", "atadura"],
    "pala": ["cabeza_pala", "mango", "atadura"],
    "azada": ["cabeza_azada", "mango", "atadura"],
    "martillo": ["cabeza_martillo", "mango", "atadura"],
    "picahacha": ["cabeza_pico", "cabeza_hacha", "mango"],
    "cincel": ["punta_cincel", "mango"],
    "espada": ["hoja", "mango", "guarda"],
    "daga": ["hoja", "mango"],
    "espadon": ["hoja", "hoja", "mango", "guarda"],
    "lanza": ["punta_lanza", "mango", "atadura"],
    "tridente": ["punta_tridente", "mango", "atadura"],
    "mazo": ["cabeza_mazo", "mango", "atadura"],
    "guadana": ["hoja", "mango", "atadura"],
    "mangual": ["bola", "cadena", "mango"],
    # The mitt is drawn first because the other two sit on it: slots composite in order.
    "guanteletes": ["manopla", "nudillos", "remache"],
    "flecha": ["punta_flecha", "emplumado"],
    "gancho": ["garfio", "cuerda", "mango"],
    "barda": ["placa_barda", "forro"],
    "armadura_de_lobo": ["placa_lobo", "forro"],
    "casco": ["placa_casco", "forro"],
    "pechera": ["placa_pechera", "forro"],
    "grebas": ["placa_grebas", "forro"],
    "botas": ["placa_botas", "forro"],
    "arco": ["brazos_arco", "cuerda", "mango"],
    "ballesta": ["brazos_arco", "cuerda", "mango", "guarda"],
    "escudo": ["placa_escudo", "borde_escudo", "mango"],
    "cana": ["mango", "cuerda"],
    "alas": ["membrana", "forro"],
    "baculo": ["nucleo", "engaste", "mango"],
    "grimorio": ["nucleo", "tapas", "remache"],
}
THROWABLE = {"pico", "hacha", "pala", "martillo", "picahacha", "daga", "tridente"}
ARMOR = {"casco", "pechera", "grebas", "botas"}
MATERIAL_COLORS = {
    "madera": 0xB8894F, "piedra": 0xA3A3A3, "hueso": 0xF1ECD2, "cuero": 0xA86B3C, "cobre": 0xE58A5C, "hierro": 0xE4E4E4,
    "oro": 0xFFD83D, "amatista": 0xB57BEA, "diamante": 0x5FF0E2, "obsidiana": 0x6A4C9C, "netherita": 0x6B5A5A,
    "esmeralda": 0x3FD46A, "prismarina": 0x6CC7B2, "vara_de_blaze": 0xFFB02E, "cuarzo": 0xEEE6DA, "purpur": 0xA97AA9,
    "obsidiana_llorona": 0x7A33C9, "eco": 0x2A8C94, "resina": 0xE8762B, "escama": 0xAD716D, "estelar": 0xCFE9FF, "corazon": 0xFF7A3C,
    "bronce": 0xC98A3C, "laton": 0xE0B94A, "peltre": 0xA3A79A, "acero": 0xBFC4CC, "electro": 0xF2E29A, "damasco": 0xA0A6B4, "acero_estelar": 0xC2BFE3, "obsidiacero": 0x5B4A77, "hueco": 0x9AA4B0,
    # Poured in the crucible rather than melted at the star, and the last three only at white heat.
    "cinerio": 0xC4562A, "voltaico": 0x6FD6E8, "almacero": 0x77CCD3, "vidriacero": 0xA8D8E0,
    "solacero": 0xFFC341, "lunacero": 0x5A6CC0, "acero_vivo": 0xE8231A,
    # Skimmed off a melt rather than poured out of one.
    "escoria": 0x7A6A5E,
}
SPECIAL = {"arco", "escudo", "lanza", "ballesta", "cana"}
DEFAULT_COLORS = {"HEAD": 0xE4E4E4, "PLATE": 0xE4E4E4, "HANDLE": 0xB8894F, "EXTRA": 0xB8894F, "LINING": 0xA86B3C}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- vanilla sprites

_jar = None


def jar_read(path):
    global _jar
    if _jar is None:
        _jar = zipfile.ZipFile(CLIENT_JAR)
    return _jar.read(path)


def vanilla(path):
    return Image.open(io.BytesIO(jar_read("assets/minecraft/textures/" + path))).convert("RGBA").crop((0, 0, 16, 16))


def opaque(image):
    return {(x, y): image.getpixel((x, y)) for y in range(16) for x in range(16) if image.getpixel((x, y))[3] > 0}


def luminance(pixel):
    r, g, b, _ = pixel
    return (r * 299 + g * 587 + b * 114) // 1000


def is_wood(pixel):
    r, g, b, _ = pixel
    return r - b > 25 and r >= g >= b


def neighbours(pos, diagonal=True):
    x, y = pos
    steps = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (1, -1), (-1, 1), (-1, -1)] if diagonal else [])
    return [(x + dx, y + dy) for dx, dy in steps]


def adjacent(pos, others, diagonal=True):
    return any(n in others for n in neighbours(pos, diagonal))


def split_tool(sprite_path):
    """Head, handle and binding of a vanilla tool: wood-colored pixels are the handle, the rest the head,
    and handle pixels touching the lit metal become the binding. Near-black outline pixels that hug the
    handle without touching lit metal (the hoe draws its handle outline in grey) belong to the handle."""
    pixels = opaque(vanilla(sprite_path))
    handle = {p for p, c in pixels.items() if is_wood(c)}
    dark = {p for p, c in pixels.items() if p not in handle and luminance(c) < 40}
    metal = set(pixels) - handle - dark
    handle |= {p for p in dark if adjacent(p, handle) and not adjacent(p, metal)}
    head = set(pixels) - handle
    binding = {p for p in handle if adjacent(p, metal) and p not in dark}
    layers = {p: (0, luminance(c)) for p, c in pixels.items() if p in head}
    layers.update({p: (1, luminance(pixels[p])) for p in handle - binding})
    layers.update({p: (2, luminance(pixels[p])) for p in binding})
    return layers


def trident():
    """The vanilla trident cut into its three pieces: the fork at the top (0), the shaft running down to
    the bottom left (1), and the collar where the two meet (2)."""
    pixels = opaque(vanilla("item/trident.png"))
    layers = {}
    for (x, y), color in pixels.items():
        if y <= 7:
            label = 0
        elif y == 8:
            label = 2
        else:
            label = 1
        layers[(x, y)] = (label, luminance(color))
    return layers


def chisel():
    """The chisel: a short steel blade with a bright bevel on a stubby wooden handle, laid on the
    diagonal like the sword so it sits the same way in the hand. Blade is 0, handle is 1."""
    pixels = {}

    def band(x0, y0, length, width, label, colors):
        for i in range(length):
            for w in range(width):
                x, y = x0 + i + w, y0 - i
                if 0 <= x < 16 and 0 <= y < 16:
                    pixels[(x, y)] = (label, colors[w])

    band(2, 14, 6, 3, 1, [(150, 101, 58), (116, 76, 42), (72, 46, 24)])
    band(8, 8, 6, 3, 0, [(226, 228, 236), (176, 178, 186), (104, 106, 114)])
    # The bevelled tip catches the light.
    for point in ((14, 2), (15, 2), (14, 1)):
        pixels[point] = (0, (248, 250, 255))
    return as_layers(pixels)


def split_sword():
    """Blade, grip and guard of the iron sword, split along the blade's diagonal (u = x - y)."""
    pixels = opaque(vanilla("item/iron_sword.png"))
    layers = {}
    for (x, y), c in pixels.items():
        u = x - y
        if is_wood(c) or u <= -12:
            label = 1
        elif u <= -2:
            label = 2
        else:
            label = 0
        layers[(x, y)] = (label, luminance(c))
    return layers


def dagger():
    """The sword cut down to a short blade and moved to the middle of the sprite."""
    layers = {}
    for (x, y), (label, lum) in split_sword().items():
        if label == 2:
            label = 1  # the dagger's small crossguard belongs to the grip
        if label == 0 and x - y > 6:
            continue
        if label == 0 and x - y >= 5:
            lum = min(lum, 60)
        layers[(x + 3, y - 3)] = (label, lum)
    return {p: v for p, v in layers.items() if 0 <= p[0] < 16 and 0 <= p[1] < 16}


def greatsword():
    """The sword with its blade widened by one pixel on each side; the new edge is the second blade layer."""
    layers = {p: ((0 if l == 0 else 2 if l == 1 else 3), lum) for p, (l, lum) in split_sword().items()}
    blade = {p for p, (l, _) in layers.items() if l == 0}
    edge = set()
    for p in blade:
        for q in neighbours(p, diagonal=False):
            if 0 <= q[0] < 16 and 0 <= q[1] < 16 and q not in layers and q[0] - q[1] >= 1:
                edge.add(q)
    for q in edge:
        outside = not all(n in layers or n in edge for n in neighbours(q, diagonal=False))
        layers[q] = (1, 60 if outside else 170)
    for p in blade:
        if layers[p][1] < 70 and adjacent(p, edge, diagonal=False):
            layers[p] = (0, 150)
    return layers


def mace():
    """The mace: everything below the head's diagonal is the handle; the grey pommel and the handle
    pixels next to the head are the binding."""
    pixels = opaque(vanilla("item/mace.png"))
    handle = {p for p in pixels if p[0] - p[1] <= -2}
    head = set(pixels) - handle
    pommel = {p for p in handle if p[1] >= 13 and p[0] <= 2}
    binding = {p for p in handle if adjacent(p, head)} | pommel
    layers = {p: (0, luminance(pixels[p])) for p in head}
    layers.update({p: (1, luminance(pixels[p])) for p in handle - binding})
    layers.update({p: (2, luminance(pixels[p])) for p in binding})
    return layers


OUTLINE = (20, 20, 22)
SIDES4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def on_edge(pos, region):
    return any((pos[0] + dx, pos[1] + dy) not in region for dx, dy in SIDES4)


def as_layers(pixels):
    return {p: (label, luminance(color + (255,))) for p, (label, color) in pixels.items()}


def sledgehammer():
    """Forja's hammer: a tilted block head with a bevel, the handle poking out of its top. Labels are
    head (0), handle (1) and the binding at the socket (2)."""
    head = set()
    for y in range(16):
        for x in range(max(9 - y, y - 1), min(y + 9, 21 - y) + 1):
            head.add((x, y))
    head -= {(9, 0), (10, 11), (4, 5), (15, 6)}
    pixels = {}
    for (x, y) in head:
        a, b = x - y, x + y
        if on_edge((x, y), head):
            color = OUTLINE
        elif b <= 10 or (a >= 7 and b <= 14):
            color = (176, 176, 180)
        elif a <= 0 or b >= 19:
            color = (62, 62, 66)
        elif a >= 7:
            color = (140, 140, 144)
        else:
            color = (112, 112, 116)
        pixels[(x, y)] = (0, color)
    for y in range(16):
        for x in range(16):
            a, b = x - y, x + y
            if (x, y) in head or not 13 <= b <= 16:
                continue
            if -13 <= a <= -1:
                color = OUTLINE if b in (13, 16) or a == -13 else (156, 108, 62) if b == 14 else (110, 74, 40)
                pixels[(x, y)] = (2 if a >= -2 and b in (14, 15) else 1, color)
            elif 10 <= a <= 11:
                color = OUTLINE if b in (13, 16) or a == 11 else (156, 108, 62) if b == 14 else (110, 74, 40)
                pixels[(x, y)] = (1, color)
    return as_layers(pixels)


SCYTHE_BLADE = [
    "......oooooo....",
    "....ooLLLLLLoo..",
    "..ooLLMMMMMMMMo.",
    ".oLLMMooooooMDDo",
    "oLMMoo......oDbo",
    "oLMo.......obbo.",
    "oMo.......ohHo..",
    "oo.......ohHo...",
]


def scythe():
    """Forja's scythe: a long shaft from the bottom left and a curved blade sweeping left from its top,
    drawn by hand in SCYTHE_BLADE. Labels are blade (0), shaft (1) and the binding where they meet (2)."""
    shades = {"L": (240, 242, 248), "M": (170, 172, 180), "D": (104, 106, 114), "h": (156, 108, 62), "H": (110, 74, 40), "b": (150, 150, 156)}
    pixels = {}
    for y, row in enumerate(SCYTHE_BLADE):
        for x, char in enumerate(row):
            if char == ".":
                continue
            if char in "hH":
                label = 1
            elif char == "b":
                label = 2
            elif char == "o":
                label = 1 if y >= 6 and x >= 9 else 2 if y in (4, 5) and x >= 11 else 0
            else:
                label = 0
            pixels[(x, y)] = (label, OUTLINE if char == "o" else shades[char])
    for y in range(8, 15):
        pixels[(16 - y, y)] = (1, OUTLINE)
        pixels[(17 - y, y)] = (1, shades["h"])
        pixels[(18 - y, y)] = (1, shades["H"])
        pixels[(19 - y, y)] = (1, OUTLINE)
    pixels[(3, 15)] = (1, OUTLINE)
    pixels[(4, 15)] = (1, OUTLINE)
    return as_layers(pixels)


SPEAR_TIP_WIDTH = [0, 1, 1, 2, 2, 3, 3, 3, 3, 3, 2, 2, 2, 1, 1, 0]


# The mangual, as Andy drew it: the haft running down to the left, a chain of linked rings hanging
# off its head, and a spiked ball swinging at the bottom right. The old one was three loose pieces
# that never touched each other, which is why it read as a stick, a line and a blob.
#
#   o outline   D dark   M mid   l pale   L highlight      w/W/g/G the haft, dark to light
FLAIL = [
    "......DMLDo.....",
    ".....WGDoo......",
    "....WgWwl.......",
    "...WGw.MMM......",
    "..Wgw..M.L......",
    ".WGw...l.M......",
    "WGw....MLM......",
    "ww....oLoooo....",
    "......oDDDDMo...",
    ".....oMMDoLDMo..",
    ".....ooDMMoLMMo.",
    ".....oDMMMLoLLDo",
    ".....oMDoMMLLDoo",
    "......oMDoMMLoo.",
    ".......oMDMLoo..",
    "........oMLoo...",
]

# The fist, from the back of the hand, taken off the mitts Andy photographed. Three things make a
# hand read at sixteen pixels and none of them is the padding: **knuckles that step**, a **thumb lobe**
# breaking one side, and a **wrist that draws in** to the cuff. The first attempt at these had a
# rounded rectangle with bands across it, and a rounded rectangle with bands is a crate at any size.
#
#   # the back of the hand   T the thumb   C the cuff
GAUNTLET = [
    "................",
    "....######......",
    "..##########....",
    ".############...",
    ".############...",
    ".############...",
    ".############...",
    "TT###########...",
    "TT###########...",
    "TT###########...",
    ".############...",
    "..##########....",
    "...CCCCCCCC.....",
    "...CCCCCCCC.....",
    "...CCCCCCCC.....",
    "................",
]


def split_arrow():
    """Forja's arrow from the vanilla sprite: the metal tip is the head, the rest is the fletching."""
    pixels = opaque(vanilla("item/arrow.png"))
    # The tip is the bright corner at the top right of the vanilla sprite; the rest is shaft and feathers.
    return {(x, y): (0 if x + (15 - y) >= 20 else 1, luminance(colour)) for (x, y), colour in pixels.items()}


def grapple():
    """Forja's gancho: the stick you throw it by, the chain, and the grapnel at the far end.

    Labels are the claw (0), the chain (1) and the handle (2), which is the slot order of the type.

    Two things decided the shape. It used to be a **V** with a stick under it and read as a slingshot;
    hung the other way up, as a grapnel with its arms curving out, it reads as what it is. And the
    chain is the vanilla chain sprite used **straight down and untouched** — laid on the diagonal like
    a pickaxe it had to be stepped link by link and turned to mush where it crossed the stick.

    It is drawn here claw-down, handle-up, because that is the picture: a grapnel dangling off its
    chain. Then it is turned over at the end, and that is not a detail. **Minecraft grips a handheld
    item by the bottom left corner of its sprite** — every sword, every pickaxe, every axe in the game
    puts its handle there, and the transform that lays an item along the fist is built around it. Drawn
    the way the icon wanted it, the handle was up in the top right instead, so the player took hold of
    the claw and the hook hung upside down off the wrist. Moving the transform about only chased it:
    gripping the far corner made the whole thing hang **downwards** out of the fist, when every item in
    the game points up and forward. The sprite was what was backwards, so the sprite is what turns.
    """
    claw = {"C": 226, "M": 166, "D": 98}
    rows = [
        "......CD........",
        "...C..CD..C.....",
        "...CDCCDCCD.....",
        "....CCCMDC......",
        "......CMD.......",
    ]
    pixels = {}

    def put(point, label, value):
        pixels[point] = (label, (value, value, value))

    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char in claw:
                put((x, y + 11), 0, claw[char])
    # The band where the chain is shackled to the shank. Without it the chain simply stopped and the
    # claw simply started, at the same pixel, and the join was the weakest part of the sprite.
    for x in (5, 6, 7, 8):
        put((x, 10), 0, 250 if x < 7 else 150)
    put((4, 10), 0, 120)
    put((9, 10), 0, 120)

    # The chain: Minecraft's own, straight down. Its levels are stretched on the way in because the
    # sprite is drawn very dark — vanilla never tints it, and ours is multiplied by the rope's metal,
    # so left alone it comes out near black whatever you make it of.
    links = opaque(vanilla("item/iron_chain.png"))
    levels = sorted(luminance(c) for c in links.values())
    low = levels[0]
    span = max(1, levels[-1] - low)
    for (x, y), colour in links.items():
        target = y - 1 + 7
        if 7 <= target <= 10:
            put((x, target), 1, int(76 + 168 * (luminance(colour) - low) / span))

    # And the handle: the vanilla stick, which already comes drawn on the diagonal, cut short so its
    # butt lands where the chain starts.
    for (x, y), colour in opaque(vanilla("item/stick.png")).items():
        if 2 <= y <= 7 and 0 <= x - 1 < 16:
            put((x - 1, y), 2, luminance(colour))
    # Over it goes: handle into the bottom left corner where the hand is, claw pointing up and away.
    return as_layers({(15 - x, 15 - y): value for (x, y), value in pixels.items()})


def arrow_tip():
    """A loose arrow tip: a small flint or metal point."""
    rows = [
        "....oo..",
        "...oLLo.",
        "..oLLMo.",
        "..oLMMo.",
        ".oLMMDo.",
        ".oMMDDo.",
        "..oDDo..",
        "...oo...",
    ]
    shades = {"L": (224, 226, 234), "M": (166, 168, 176), "D": (108, 110, 118), "o": OUTLINE}
    pixels = {}
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char != ".":
                pixels[(x + 4, y + 3)] = (0, shades[char])
    return as_layers(pixels)


def fletching():
    """Three feathers bound to a nock: the part that decides how fast an arrow leaves the string."""
    rows = [
        "..o.....",
        ".oLo....",
        "oLLLo...",
        ".oLLLo..",
        "..oLLLo.",
        "...oLLo.",
        "....oLo.",
        ".....o..",
    ]
    shades = {"L": (238, 236, 226), "o": OUTLINE}
    pixels = {}
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char != ".":
                pixels[(x + 4, y + 4)] = (0, shades[char])
    return as_layers(pixels)


def flail():
    """Forja's mangual: haft to the lower left, chain down the middle, spiked ball at the lower right.

    Labels are ball (0), chain (1) and haft (2). The split is by colour and by height rather than by
    a hand-written mask: anything drawn in wood is the haft, everything above the ball's top edge is
    chain, and the rest is the ball. That way the drawing stays the one thing being edited.
    """
    shades = {
        "o": OUTLINE, "D": (108, 108, 114), "M": (152, 152, 160), "l": (196, 196, 204),
        "L": (238, 238, 246),
        "w": (80, 54, 30), "W": (134, 92, 50), "g": (168, 118, 66), "G": (218, 172, 114),
    }
    pixels = {}
    for y, row in enumerate(FLAIL):
        for x, char in enumerate(row):
            if char == ".":
                continue
            if char in "wWgG":
                label = 2
            elif y < 7 or (y == 7 and x <= 7):
                label = 1
            else:
                label = 0
            pixels[(x, y)] = (label, shades[char])
    return as_layers(pixels)


def rivet():
    """One rivet, big enough to be an item: a domed head with the shank behind it.

    Cut from the weapon the way the other parts are, a rivet would be four loose pixels, which is not
    an icon. So it is drawn once, on its own, at a size you can see — the same trick the mod uses for
    a bowstring, which is also a thing that is nearly nothing on the weapon itself.
    """
    rows = [
        "....oooo....",
        "..ooLLLLoo..",
        ".oLLLLLLMMo.",
        ".oLLLLMMMMo.",
        "oLLLMMMMMDDo",
        "oLLMMMMMDDDo",
        "oLMMMMMDDDDo",
        "oMMMMMDDDDDo",
        ".oMMMDDDDDo.",
        ".oMMDDDDDDo.",
        "..ooDDDDoo..",
        "....oooo....",
    ]
    shades = {"L": (226, 228, 236), "M": (166, 168, 176), "D": (102, 104, 112), "o": OUTLINE}
    pixels = {}
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char != ".":
                pixels[(x + 2, y + 2)] = (0, shades[char])
    # The slot across the head, which is what tells you it is a rivet and not a ball.
    for x in range(5, 11):
        pixels[(x, 7)] = (0, (74, 76, 82))
        pixels[(x, 8)] = (0, (208, 210, 218))
    return as_layers(pixels)


def gauntlet():
    """Forja's guanteletes: a padded mitt with a band over the knuckles and rivets through it.

    Labels are the mitt (0), the knuckle band (1) and the rivets (2), which is also the order they are
    drawn in: the leather is behind, the steel band sits on it, and the rivets sit on the band. Three
    parts rather than two because that is what the thing is — you can put good steel on the knuckles
    of a cheap glove, or rivet a poor band with gold, and both should look like what they are.
    """
    hand = {(x, y) for y, row in enumerate(GAUNTLET) for x, char in enumerate(row) if char != "."}
    thumb = {(x, y) for y, row in enumerate(GAUNTLET) for x, char in enumerate(row) if char == "T"}
    cuff = {(x, y) for y, row in enumerate(GAUNTLET) for x, char in enumerate(row) if char == "C"}
    # The band takes the top of the hand as well as the knuckles, so that lifting it off for the part
    # icon does not leave the mitt with a loose arc floating above it.
    band = {(x, y) for (x, y) in hand if y in (1, 2, 3, 4)} - thumb - cuff
    rim = {p for p in hand
           if any((p[0] + dx, p[1] + dy) not in hand for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    top = min(y for (_, y) in cuff)

    pixels = {}

    def put(point, label, value):
        pixels[point] = (label, (max(0, min(255, int(value))),) * 3)

    for (x, y) in hand:
        if (x, y) in band:
            put((x, y), 1, 236 if y <= 2 else 190 - (y - 2) * 26)
        elif (x, y) in cuff:
            put((x, y), 0, 226 if y == top else 150)
        elif (x, y) in thumb:
            put((x, y), 0, 196 - (y - 7) * 14)
        else:
            # The padded back of the hand, lit from the top left, with the seam down the far side.
            put((x, y), 0, 214 - (y - 1) * 9 - max(0, x - 9) * 18)
    # The grooves that turn one lump into four knuckles.
    for x in (4, 7, 10):
        for y in (2, 3, 4):
            if (x, y) in hand:
                put((x, y), 1, 96)
    # The groove that separates the thumb from the hand, and the strap across the cuff.
    for y in (7, 8, 9):
        put((2, y), 0, 108)
    for x in range(3, 11):
        put((x, top + 1), 0, 250 if x < 7 else 190)
    # The rivets, where Andy put them: a head on each knuckle with a skirt under it, four groups of
    # two or three pixels rather than four lone dots. They are their own part, so what matters is not
    # that these pixels are empty on the band — it is that they belong to the rivet and take the
    # rivet's metal.
    heads = ((2, 3), (5, 3), (9, 3), (11, 3))
    skirts = ((2, 4), (3, 4), (5, 4), (6, 4), (8, 4), (9, 4), (11, 4))
    # Flat, the way Andy painted them. A rivet head this size has no room for a gradient: shading the
    # skirt darker just read as a smudge under each one.
    for point in heads + skirts:
        put(point, 2, 250)
    # The outline, on whichever layer owns the pixel, so no part floats without one.
    for (x, y) in rim:
        put((x, y), pixels[(x, y)][0], 38)
    return {point: (label, luminance(colour + (255,))) for point, (label, colour) in pixels.items()}


def spear():
    """Forja's spear icon: a leaf-shaped tip lit from the top left on a light wooden shaft. Labels are
    tip (0), shaft (1) and the collar under the tip (2)."""
    tip = {(x, y) for y in range(16) for x in range(16) if 1 <= x - y <= 15 and abs(x + y - 15) <= SPEAR_TIP_WIDTH[x - y]}
    pixels = {}
    for (x, y) in tip:
        a, b = x - y, x + y
        if on_edge((x, y), tip):
            color = OUTLINE
        elif b < 15:
            color = (244, 244, 248) if a <= 9 else (212, 214, 220)
        elif b > 15:
            color = (92, 94, 102)
        else:
            color = (156, 158, 166)
        pixels[(x, y)] = (0, color)
    for y in range(16):
        for x in range(16):
            a, b = x - y, x + y
            if (x, y) in tip or not -14 <= a <= 0 or not 14 <= b <= 16:
                continue
            color = (58, 38, 20) if b == 14 or a == -14 else (210, 164, 106) if b == 15 else (132, 88, 48)
            pixels[(x, y)] = (2 if a >= -1 else 1, color)
    return as_layers(pixels)


SPEAR_HAND_TIP_WIDTH = [0, 1, 1, 2, 2, 3, 3, 3, 4, 4, 4, 3, 3, 3, 2, 2, 1, 1]


def spear_in_hand():
    """The 32x32 held spear, laid out like vanilla's spear_in_hand with the tip at the top left. The
    collar and the metal butt cap are the binding."""
    tip = {(x, y) for y in range(32) for x in range(32) if x + y <= 17 and abs(x - y) <= SPEAR_HAND_TIP_WIDTH[x + y]}
    pixels = {}
    for (x, y) in tip:
        a, b = x - y, x + y
        if on_edge((x, y), tip):
            color = OUTLINE
        elif a > 0:
            color = (244, 244, 248) if b >= 5 else (212, 214, 220)
        elif a < 0:
            color = (92, 94, 102)
        else:
            color = (156, 158, 166)
        pixels[(x, y)] = (0, color)
    for y in range(32):
        for x in range(32):
            a, b = x - y, x + y
            if (x, y) in tip or not -1 <= a <= 1 or not 18 <= b <= 62:
                continue
            color = {-1: (58, 38, 20), 0: (210, 164, 106), 1: (132, 88, 48)}[a]
            if b >= 60:
                color = OUTLINE if a != 0 else (90, 92, 100)
            pixels[(x, y)] = (2 if b <= 20 or b >= 58 else 1, color)
    return as_layers(pixels)


def mattock():
    """Axe blade from the iron axe, the lower arm of the pick from the iron pickaxe, pickaxe handle."""
    pick = opaque(vanilla("item/iron_pickaxe.png"))
    axe = opaque(vanilla("item/iron_axe.png"))
    layers = {p: (2, luminance(c)) for p, c in pick.items() if is_wood(c)}
    layers.update({p: (0, luminance(c)) for p, c in pick.items() if not is_wood(c) and p[0] + p[1] >= 16})
    layers.update({p: (1, luminance(c)) for p, c in axe.items() if not is_wood(c)})
    return layers


def split_elytra():
    """Forja's wings from the vanilla elytra icon: the pale membrane is the sheet, the dark frame the harness."""
    pixels = opaque(vanilla("item/elytra.png"))
    return {p: (0 if luminance(c) >= 90 else 1, luminance(c)) for p, c in pixels.items()}


def generate_effect_textures():
    """The bleeding icon: a drop of blood, drawn here so the effect has a face in the inventory."""
    size = 18
    drop = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    rows = [
        "........##........",
        "........##........",
        ".......####.......",
        ".......####.......",
        "......######......",
        "......######......",
        ".....########.....",
        ".....########.....",
        "....##########....",
        "....##########....",
        "...############...",
        "...############...",
        "...############...",
        "....##########....",
        "....##########....",
        ".....########.....",
        "......######......",
        "........##........",
    ]
    for y, row in enumerate(rows):
        for x, cell in enumerate(row):
            if cell != "#":
                continue
            # Darker towards the bottom, so the drop reads as round.
            shade = 1.0 - y / (size * 1.8)
            drop.putpixel((x, y), (int(196 * shade), int(30 * shade), int(26 * shade), 255))
    folder = ASSETS / "textures/mob_effect"
    folder.mkdir(parents=True, exist_ok=True)
    drop.save(folder / "sangrado.png")


def generate_shockwave_textures():
    """What client/ShockwaveRenderer draws the ring with: a floor band, a wall of flame, and plain white.

    All three are white and grey with the shape in the alpha. The renderer puts the colour in, per
    vertex, so the same three serve the orange ring, the violet one and whatever comes after. Both
    patterns are built from waves that are whole turns across the width, because the renderer tiles
    them round the circle and scrolls them: a seam would come round once a second.
    """
    import random as _random
    size = 32

    def clamp(value, low, high):
        return max(low, min(high, value))

    def turns(x, *waves):
        return sum(amp * math.sin(math.tau * x / size * freq + phase) for amp, freq, phase in waves)

    # The band. Row 0 is the front of the ring, row 31 the far end of its tail.
    rng = _random.Random(7)
    band = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for x in range(size):
        reach = clamp(19 + turns(x, (7, 2, 0.6), (4, 5, 2.1), (2, 9, 4.0)), 9, 31)
        for y in range(size):
            if y < 3:
                value, alpha = 255, 255
            elif y < 8:
                value, alpha = 238 - (y - 3) * 6 + rng.randint(-10, 10), 255
            else:
                k = (y - 8) / max(1.0, reach - 8)
                if k >= 1.0:
                    continue
                alpha = (1.0 - k) ** 1.3 * 255 * (0.78 + 0.4 * rng.random())
                value = 215 - 60 * k + rng.randint(-14, 14)
            v = int(clamp(value, 0, 255))
            band.putpixel((x, y), (v, v, v, int(clamp(alpha, 0, 255))))
    # Embers riding in the tail: single bright pixels, which is what stops it reading as a gradient.
    for _ in range(16):
        band.putpixel((rng.randrange(size), rng.randrange(10, 27)), (255, 255, 255, 235))

    # The curtain. Row 31 is its foot on the floor, row 0 the tips.
    rng = _random.Random(11)
    curtain = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for x in range(size):
        height = clamp(19 + turns(x, (8, 3, 1.0), (5, 7, 0.3), (2, 11, 2.2)), 7, 31)
        for y in range(size):
            up = (size - 1) - y
            if up > height:
                continue
            k = up / height
            alpha = (1.0 - k ** 1.6) * 255 * (0.82 + 0.3 * rng.random())
            # Towards the tips the flame breaks up rather than thinning evenly.
            if k > 0.5 and rng.random() < (k - 0.5) * 0.7:
                alpha *= 0.25
            v = int(clamp(255 - 70 * k + rng.randint(-12, 12), 0, 255))
            curtain.putpixel((x, y), (v, v, v, int(clamp(alpha, 0, 255))))

    folder = ASSETS / "textures/entity"
    folder.mkdir(parents=True, exist_ok=True)
    band.save(folder / "onda_anillo.png")
    curtain.save(folder / "onda_cortina.png")
    Image.new("RGBA", (4, 4), (255, 255, 255, 255)).save(folder / "onda_plano.png")
    ring, star = rune_textures()
    ring.save(folder / "onda_runa.png")
    star.save(folder / "onda_runa_centro.png")


# Strokes of the letters round a rune, in a box five across and seven tall, the foot of each towards the middle.
RUNE_LETTERS = [
    [((0, -3), (0, 3)), ((0, 3), (-2, 1)), ((0, 3), (2, 1))],
    [((2, 3), (-1, 0)), ((-1, 0), (2, -3))],
    [((0, -3), (0, 3)), ((0, 0), (-2, 3)), ((0, 0), (2, 3))],
    [((0, 3), (2, 1)), ((2, 1), (0, -1)), ((0, -1), (-2, 1)), ((-2, 1), (0, 3)), ((0, -1), (-2, -3)), ((0, -1), (2, -3))],
    [((-2, -3), (-2, 3)), ((2, -3), (2, 3)), ((-2, 1), (2, -1))],
    [((-2, 3), (2, 1)), ((2, 1), (-2, -1)), ((-2, -1), (2, -3))],
    [((-2, -3), (-2, 3)), ((2, -3), (2, 3)), ((-2, -3), (2, 3)), ((-2, 3), (2, -3))],
    [((-2, -3), (-2, 3)), ((2, -3), (2, 3)), ((-2, 3), (0, 1)), ((0, 1), (2, 3))],
]


def rune_textures():
    """The rune a forged tome leaves on the floor, in two pieces that client/ShockwaveRenderer turns against each other.

    Sixteen pixels to the block at the three blocks the rune reaches, so it is drawn at the grain of the
    floor it lies on. White with the shape in the alpha, like the rest of the wave: the colour is the
    núcleo's. The outside is a band of letters between two circles; the inside is the eight-pointed star
    of the star forge table, because that is the one sign the mod already has for "something was made here".
    """
    from PIL import ImageDraw, ImageFilter
    size = 96
    middle = (size - 1) / 2.0

    def finished(mask):
        halo = mask.filter(ImageFilter.MaxFilter(3))
        image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        for y in range(size):
            for x in range(size):
                if mask.getpixel((x, y)):
                    image.putpixel((x, y), (255, 255, 255, 255))
                elif halo.getpixel((x, y)):
                    image.putpixel((x, y), (255, 255, 255, 72))
        return image

    def circle(draw, radius, width=1):
        draw.ellipse((middle - radius, middle - radius, middle + radius, middle + radius), outline=255, width=width)

    ring = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(ring)
    circle(draw, 47, 2)
    circle(draw, 35)
    letters = 12
    for index in range(letters):
        angle = math.tau * index / letters
        out = (math.cos(angle), math.sin(angle))
        along = (-out[1], out[0])
        for (u0, v0), (u1, v1) in RUNE_LETTERS[index % len(RUNE_LETTERS)]:
            ends = []
            for u, v in ((u0, v0), (u1, v1)):
                reach = 40.5 + v
                ends.append((middle + out[0] * reach + along[0] * u, middle + out[1] * reach + along[1] * u))
            draw.line(ends, fill=255, width=1)
        # a stud between each letter and the next
        between = angle + math.tau / letters / 2.0
        bx, by = middle + math.cos(between) * 40.5, middle + math.sin(between) * 40.5
        draw.rectangle((round(bx) - 0.5, round(by) - 0.5, round(bx) + 0.5, round(by) + 0.5), fill=255)

    star = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(star)
    points = [(middle + math.cos(math.tau * i / 8 - math.pi / 2) * 31.0, middle + math.sin(math.tau * i / 8 - math.pi / 2) * 31.0) for i in range(8)]
    for i in range(8):
        draw.line((points[i], points[(i + 3) % 8]), fill=255, width=1)
    circle(draw, 31)
    circle(draw, 9)
    # the stone in the middle of it: the núcleo, cut square and stood on its corner
    draw.polygon([(middle, middle - 5), (middle + 5, middle), (middle, middle + 5), (middle - 5, middle)], fill=255)
    return finished(ring), finished(star)


def generate_sky_textures():
    """What client/EventSkyRenderer hangs in the sky during an event.

    White and grey with the shape in the alpha, like the shockwave's: the renderer brings the colour,
    so one moon is the blood moon and the spring tide's, and one glow is every halo there is. They are
    drawn at the game's own texel size on purpose — the vanilla moon is a handful of fat pixels, and a
    smooth photographic one hanging next to it would look pasted on.
    """
    import random as _random

    folder = ASSETS / "textures/environment"
    folder.mkdir(parents=True, exist_ok=True)

    def clamp(value, low=0.0, high=1.0):
        return max(low, min(high, value))

    # The aurora's rays: columns of light of uneven strength. Tiles sideways, because a curtain is
    # one texture repeated along its length and slid slowly, which is what makes it shimmer.
    size = 64
    rng = _random.Random(31)
    rays = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for x in range(size):
        turn = math.tau * x / size
        strength = 0.55 + 0.25 * math.sin(turn * 3 + 0.4) + 0.14 * math.sin(turn * 7 + 1.9) + 0.06 * math.sin(turn * 17 + 0.3)
        # Every so often a ray stands out from its neighbours, the way they do.
        if rng.random() < 0.12:
            strength += 0.25
        for y in range(size):
            height = 1.0 - y / (size - 1)   # 1 at the top row, 0 at the foot
            # Bright at the foot, thinning upwards, with a little grain so it is not a clean ramp.
            body = (1.0 - height) ** 0.55 * (0.35 + 0.65 * (1.0 - height))
            alpha = clamp(strength * body * (0.9 + 0.2 * rng.random()))
            value = int(200 + 55 * clamp(strength))
            rays.putpixel((x, y), (value, value, value, int(alpha * 255)))
    rays.save(folder / "aurora.png")

    # A soft round glow: every halo, and the head of a fireball.
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - 31.5, y - 31.5) / 31.5
            alpha = clamp(1.0 - r) ** 2.2
            glow.putpixel((x, y), (255, 255, 255, int(alpha * 255)))
    glow.save(folder / "resplandor.png")

    # The moon, 32 fat pixels across, craters and all. Grey, so it can be blood or tide.
    moon_size = 32
    rng = _random.Random(12)
    moon = Image.new("RGBA", (moon_size, moon_size), (0, 0, 0, 0))
    craters = [(10, 11, 4.2), (20, 8, 2.6), (22, 19, 5.0), (12, 22, 3.0), (17, 15, 1.8), (7, 17, 1.6), (25, 12, 1.5)]
    for y in range(moon_size):
        for x in range(moon_size):
            r = math.hypot(x - 15.5, y - 15.5)
            if r > 15.2:
                continue
            shade = 0.92 - 0.10 * (r / 15.2) ** 2 + rng.uniform(-0.03, 0.03)
            for cx, cy, cr in craters:
                d = math.hypot(x - cx, y - cy)
                if d < cr:
                    shade -= 0.20
                elif d < cr + 1.0:
                    shade += 0.05
            v = int(clamp(shade) * 255)
            moon.putpixel((x, y), (v, v, v, 255))
    moon.save(folder / "luna.png")

    # A plain disc, for whatever has to be put in front of the sun.
    disc = Image.new("RGBA", (moon_size, moon_size), (0, 0, 0, 0))
    for y in range(moon_size):
        for x in range(moon_size):
            if math.hypot(x - 15.5, y - 15.5) <= 15.2:
                disc.putpixel((x, y), (255, 255, 255, 255))
    disc.save(folder / "disco.png")

    # The corona: nothing in the middle (the disc sits there), a hard bright ring, then streamers.
    rng = _random.Random(44)
    corona = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            dx, dy = x - 31.5, y - 31.5
            r = math.hypot(dx, dy) / 31.5
            if r < 0.24:
                continue
            angle = math.atan2(dy, dx)
            streamers = 0.62 + 0.24 * math.sin(angle * 6 + 0.7) + 0.14 * math.sin(angle * 13 + 2.0)
            fall = clamp((1.0 - r) / 0.76) ** 1.7
            ring = clamp(1.0 - abs(r - 0.27) / 0.05)
            alpha = clamp(fall * streamers * 0.9 + ring)
            corona.putpixel((x, y), (255, 255, 255, int(alpha * 255)))
    corona.save(folder / "corona.png")

    # The arcane circle: rings, a graduated edge, an eight-pointed star and eight marks between the
    # points. Drawn with one-pixel lines at 128, which at the size it hangs is the game's texel size.
    big = 128
    runes = Image.new("RGBA", (big, big), (0, 0, 0, 0))

    def plot(px, py, alpha=255):
        if 0 <= px < big and 0 <= py < big:
            old = runes.getpixel((px, py))[3]
            runes.putpixel((px, py), (255, 255, 255, max(old, alpha)))

    def circle(radius, alpha=255):
        steps = int(radius * 8)
        for i in range(steps):
            a = math.tau * i / steps
            plot(int(round(63.5 + math.cos(a) * radius)), int(round(63.5 + math.sin(a) * radius)), alpha)

    def line(x0, y0, x1, y1, alpha=255):
        steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(steps + 1):
            t = i / steps
            plot(int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), alpha)

    for radius in (61, 58, 44, 22):
        circle(radius)
    circle(50, 150)
    # The graduated edge: a tick every five degrees, a longer one every forty-five.
    for i in range(72):
        a = math.tau * i / 72
        inner = 52 if i % 9 == 0 else 55
        line(63.5 + math.cos(a) * inner, 63.5 + math.sin(a) * inner, 63.5 + math.cos(a) * 58, 63.5 + math.sin(a) * 58)
    # Two squares turned against each other make the eight-pointed star.
    for turn in (0.0, math.pi / 4):
        corners = [(63.5 + math.cos(turn + math.pi / 2 * k) * 44, 63.5 + math.sin(turn + math.pi / 2 * k) * 44) for k in range(4)]
        for k in range(4):
            line(*corners[k], *corners[(k + 1) % 4])
    # Eight marks in the band between the rings: three strokes each, never the same twice.
    rng = _random.Random(8)
    for k in range(8):
        a = math.tau * (k + 0.5) / 8
        cx, cy = 63.5 + math.cos(a) * 51, 63.5 + math.sin(a) * 51
        for _ in range(3):
            sx, sy = cx + rng.uniform(-3, 3), cy + rng.uniform(-3, 3)
            ex, ey = cx + rng.uniform(-3, 3), cy + rng.uniform(-3, 3)
            line(sx, sy, ex, ey)
    runes.save(folder / "runas.png")
    Image.new("RGBA", (4, 4), (255, 255, 255, 255)).save(folder / "plano.png")


def generate_book_gui():
    """The open guide book that client/GuideBookScreen draws its pages on.

    The screen used to be five flat rectangles: a brown one, two cream ones, a dark strip for the spine.
    This is the same book at the same size — the pages are exactly where the layout expects them, 150 by
    186 with six pixels of spine between — but made of something: leather with a stitched edge and brass
    corners, a stack of page edges showing under the top sheet, parchment with grain and the odd stain,
    and the shadow that falls into the gutter of any open book. Tabs for the five sections share the sheet.
    """
    import random as _random

    rng = _random.Random(1907)
    sheet = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    # The book overhangs the old rectangle by six pixels all round: that is the cover showing.
    over = 6
    book_w, book_h = 314 + over * 2, 194 + over * 2
    page_w, page_h, spine = 150, 186, 6

    def clamp(v):
        return max(0, min(255, int(v)))

    def leather(x, y):
        grain = rng.randint(-7, 7) + (4 if (x * 7 + y * 3) % 11 == 0 else 0)
        edge = min(x, y, book_w - 1 - x, book_h - 1 - y)
        shade = -26 if edge < 1 else (-10 if edge < 2 else 0)
        return (clamp(96 + grain + shade), clamp(50 + grain * 0.6 + shade * 0.7), clamp(24 + grain * 0.4 + shade * 0.5), 255)

    for y in range(book_h):
        for x in range(book_w):
            # Rounded corners: two pixels off each.
            cx = min(x, book_w - 1 - x)
            cy = min(y, book_h - 1 - y)
            if cx + cy < 2:
                continue
            sheet.putpixel((x, y), leather(x, y))
    # Stitching, a little in from the edge all the way round.
    for x in range(5, book_w - 5):
        if x % 5 < 3:
            sheet.putpixel((x, 3), (206, 164, 108, 255))
            sheet.putpixel((x, book_h - 4), (206, 164, 108, 255))
    for y in range(5, book_h - 5):
        if y % 5 < 3:
            sheet.putpixel((3, y), (206, 164, 108, 255))
            sheet.putpixel((book_w - 4, y), (206, 164, 108, 255))
    # Brass corners.
    for corner_x, corner_y, sx, sy in ((0, 0, 1, 1), (book_w - 1, 0, -1, 1), (0, book_h - 1, 1, -1), (book_w - 1, book_h - 1, -1, -1)):
        for i in range(11):
            for j in range(11 - i):
                if i + j < 2:
                    continue
                x, y = corner_x + sx * i, corner_y + sy * j
                lit = 232 if (i + j) % 7 == 3 or j == 1 or i == 1 else 196
                sheet.putpixel((x, y), (lit, clamp(lit * 0.78), clamp(lit * 0.36), 255))
        sheet.putpixel((corner_x + sx * 4, corner_y + sy * 4), (120, 86, 30, 255))

    # The stack of pages under the top sheet: three edges showing at the sides and along the bottom.
    left_page = over + 4
    top_page = over + 4
    for side in range(2):
        px = left_page + side * (page_w + spine)
        for layer in range(3):
            tone = (226 - layer * 14, 212 - layer * 14, 176 - layer * 14, 255)
            outer = px - 1 - layer if side == 0 else px + page_w + layer
            for y in range(top_page + layer, top_page + page_h + 1 + layer):
                sheet.putpixel((outer, y), tone)
            for x in range(px - (layer + 1 if side == 0 else 0), px + page_w + (layer + 1 if side == 1 else 0)):
                sheet.putpixel((x, top_page + page_h + layer), tone)
        # Parchment: warm, grained, darker towards its edges, and deepest where it dives into the gutter.
        for y in range(page_h):
            for x in range(page_w):
                edge = min(x, y, page_w - 1 - x, page_h - 1 - y)
                vignette = max(0.0, 1.0 - edge / 16.0) ** 2 * 26
                gutter_x = (page_w - 1 - x) if side == 0 else x
                gutter = max(0.0, 1.0 - gutter_x / 14.0) ** 1.6 * 52
                grain = rng.randint(-5, 5)
                fibre = -7 if rng.random() < 0.035 else (5 if rng.random() < 0.03 else 0)
                dark = vignette + gutter - grain - fibre
                sheet.putpixel((px + x, top_page + y), (clamp(246 - dark), clamp(235 - dark * 1.04), clamp(206 - dark * 1.2), 255))
        # A few old stains, soft-edged.
        for _ in range(3):
            sx0, sy0, sr = rng.randrange(12, page_w - 12), rng.randrange(20, page_h - 20), rng.uniform(5, 11)
            for y in range(int(sy0 - sr), int(sy0 + sr) + 1):
                for x in range(int(sx0 - sr), int(sx0 + sr) + 1):
                    d = math.hypot(x - sx0, y - sy0) / sr
                    if d < 1.0 and 0 <= x < page_w and 0 <= y < page_h:
                        r, g, b, a = sheet.getpixel((px + x, top_page + y))
                        k = (1.0 - d) ** 1.5 * 0.10 + (0.05 if 0.82 < d < 1.0 else 0.0)
                        sheet.putpixel((px + x, top_page + y), (clamp(r - 60 * k), clamp(g - 74 * k), clamp(b - 90 * k), a))
    # The spine: darker leather, ridged.
    spine_x = left_page + page_w
    for y in range(2, book_h - 2):
        for x in range(spine):
            ridge = 14 if y % 28 in (0, 1) else 0
            mid = 10 - abs(x - spine / 2 + 0.5) * 5
            sheet.putpixel((spine_x + x, y), (clamp(58 + mid + ridge), clamp(28 + mid * 0.6 + ridge * 0.7), clamp(12 + mid * 0.4 + ridge * 0.4), 255))

    # Section tabs, below the book on the sheet: five colours, resting and lit. 22 wide, 20 tall, the
    # left edge tucked under the page so they read as coming out from between the leaves.
    tab_colours = [(226, 120, 44), (150, 108, 220), (204, 62, 56), (70, 170, 168), (120, 140, 176)]
    for state in range(2):
        for index, (r, g, b) in enumerate(tab_colours):
            ox, oy = index * 24, 212 + state * 22
            width = 22 if state == 1 else 18
            for y in range(20):
                for x in range(width):
                    if x >= width - 2 and (y < 2 or y > 17) and (x - (width - 2)) + (2 - y if y < 2 else y - 17) > 1:
                        continue
                    k = 1.0 if state == 1 else 0.78
                    lit = 1.12 if y == 1 or x == width - 2 else (0.8 if y == 18 else 1.0)
                    fold = 0.7 if x < 3 else 1.0
                    grain = rng.randint(-5, 5)
                    sheet.putpixel((ox + x, oy + y), (clamp(r * k * lit * fold + grain), clamp(g * k * lit * fold + grain), clamp(b * k * lit * fold + grain), 255))
    # The ribbon that hangs out of the bottom of the spine.
    for y in range(24):
        for x in range(4):
            if y > 19 and abs(x - 1.5) < (y - 19) * 0.5:
                continue
            tone = 0.82 if x in (0, 3) else 1.0
            sheet.putpixel((130 + x, 212 + y), (clamp(176 * tone), clamp(34 * tone), clamp(40 * tone), 255))
    folder = ASSETS / "textures/gui"
    folder.mkdir(parents=True, exist_ok=True)
    sheet.save(folder / "libro.png")


def generate_boss_bar():
    """The frame of the Fallen Smith's own boss bar; client/BossBarArt pours the health into it.

    Vanilla's bar is a pink or red stripe that says "a boss" and nothing else. His is a length of
    forged iron with a hammer head at each end and three notches cut in it — at three quarters, a half
    and a quarter, which are the three moments the fight changes — with a window through the middle
    for the metal. The window is the vanilla bar's own 182 by 5, so it sits where that did, and all the
    weight of the frame hangs BELOW it: the boss's name is written straight above the stripe, and the
    first version of this had a rail going through the bottom of the letters.
    """
    import random as _random

    rng = _random.Random(320)
    width = 198
    top, window, rail = 1, 5, 5          # one pixel of lip, the window, then the rail under it
    height = top + window + rail         # 11
    head_up = 3                          # the hammer heads stand this much higher, out beyond the name
    frame = Image.new("RGBA", (256, 32), (0, 0, 0, 0))

    def iron(lit=0):
        base = 74 + lit + rng.randint(-6, 6)
        return (max(0, min(255, base)), max(0, min(255, base - 4)), max(0, min(255, base - 2)), 255)

    for x in range(8, width - 8):
        frame.putpixel((x, head_up), iron(30))
        for row in range(rail):
            lit = (26, 6, 0, -12, -30)[row]
            frame.putpixel((x, head_up + top + window + row), iron(lit))
    # Rivets along the rail.
    for x in range(14, width - 12, 14):
        frame.putpixel((x, head_up + top + window + 2), (156, 152, 146, 255))
        frame.putpixel((x + 1, head_up + top + window + 2), (36, 32, 30, 255))
    # The notches: cut down through the rail, with a brass pin hanging under each.
    for share in (0.25, 0.5, 0.75):
        x = 8 + round(182 * share)
        frame.putpixel((x, head_up), (22, 18, 16, 255))
        for row in range(rail):
            frame.putpixel((x, head_up + top + window + row), (22, 18, 16, 255))
        for dx, dy, tone in ((0, 0, (226, 190, 106)), (-1, 1, (200, 154, 72)), (1, 1, (200, 154, 72)), (0, 1, (240, 214, 140)), (0, 2, (122, 90, 34))):
            frame.putpixel((x + dx, head_up + height - 1 + dy), tone + (255,))
    # A hammer head at each end: taller than the bar, bevelled, with a dark eye for the haft.
    tall = head_up + height
    for side in range(2):
        ox = 0 if side == 0 else width - 8
        for y in range(tall):
            for x in range(8):
                edge = x == 0 or x == 7 or y == 0 or y == tall - 1
                lip = y == 1 or (x == 1 and side == 0) or (x == 6 and side == 1)
                frame.putpixel((ox + x, y), iron(-34 if edge else (40 if lip else 8)))
        for y in range(5, 9):
            frame.putpixel((ox + 3, y), (20, 16, 14, 255))
            frame.putpixel((ox + 4, y), (20, 16, 14, 255))
    folder = ASSETS / "textures/gui"
    folder.mkdir(parents=True, exist_ok=True)
    frame.save(folder / "barra_herrero.png")


def generate_station_gui():
    """The crucible's and the casting box's panels, and the fire that runs round the forge table's star.

    The two panels were drawn once by hand-run code that never made it into this file, and it showed:
    the player's inventory was painted at (22, 114) while both menus put the slots at (15, 122), so
    every stack sat seven pixels left of and eight below its own square, the crucible's output slot was
    drawn five pixels under where the item landed, and the casting box's strainer slot had no square at
    all. They are made here now, from the same constants the menus use, so that cannot drift again.

    The crucible's screen draws the melt as plain rectangles and then lays a piece of this same sheet
    back over them: a copy of the panel with a hole cut in it the shape of the pot. That is what turns a
    rectangle of orange into a pot of metal. The cut-out lives under the panel, from row 198 down, where
    nothing else is. The casting box's sand is left plain: its screen presses the real part into it.
    """
    import math
    import random as _random

    folder = ASSETS / "textures/gui"
    folder.mkdir(parents=True, exist_ok=True)
    etch, etch_light = (150, 138, 118, 255), (228, 220, 202, 255)

    def etched(px, points):
        """A mark cut into the stone: the cut, and the lit edge below and right of it."""
        for x, y in points:
            if px[x + 1, y + 1][:3] != etch[:3]:
                px[x + 1, y + 1] = etch_light
        for x, y in points:
            px[x, y] = etch

    def cutout(sheet, x0, y0, width, height, inside, at=(0, 198)):
        """Copy a piece of the panel to below it, with a hole wherever `inside` says so."""
        px = sheet.load()
        for j in range(height):
            for i in range(width):
                px[at[0] + i, at[1] + j] = (0, 0, 0, 0) if inside(x0 + i, y0 + j) else px[x0 + i, y0 + j]

    # ---- the crucible: two things to melt, the ember under them, the pot, and what it pours.
    pot_x, pot_y, pot_w, pot_h = 58, 24, 64, 54
    belly = 34

    def pot_inset(row):
        if row <= belly:
            return round(row * 4 / belly)
        t = (row - belly) / (pot_h - 1 - belly)
        return 4 + round((1.0 - math.sqrt(max(0.0, 1.0 - t * t))) * 20)

    def in_pot(x, y):
        row = y - pot_y
        if row < 0 or row >= pot_h:
            return False
        inset = pot_inset(row)
        return pot_x + inset <= x < pot_x + pot_w - inset

    crucible = gui_panel(15)
    px = crucible.load()
    gui_slot(px, 29, 25)
    gui_slot(px, 29, 49)
    gui_slot(px, 29, 81)
    etched(px, [(36 + i, 45) for i in range(5)] + [(38, 43 + i) for i in range(5)])
    # The ember gauge beside the fuel slot, and a small flame over it so it says what it measures.
    gui_inset(px, 21, 61, 28, 80, (44, 37, 31, 255))
    etched(px, [(24, 52), (23, 53), (24, 53), (23, 54), (24, 54), (25, 54), (22, 55), (23, 55), (25, 55), (26, 55),
                (22, 56), (26, 56), (22, 57), (23, 57), (25, 57), (26, 57), (23, 58), (24, 58), (25, 58)])
    # The pot. Its wall is whatever is not inside it but touches it: dark outside, lit on the left the
    # way everything else on these panels is lit.
    rng = _random.Random(4410)
    for y in range(pot_y - 3, pot_y + pot_h + 4):
        for x in range(pot_x - 5, pot_x + pot_w + 5):
            if in_pot(x, y):
                depth = (y - pot_y) / pot_h
                shade = 38 - round(14 * depth) + rng.randint(-2, 2)
                px[x, y] = (shade + 6, shade, shade - 4, 255)
                continue
            if y < pot_y:
                continue
            near = min((abs(dx) + abs(dy) for dx in range(-3, 4) for dy in range(-3, 4)
                        if in_pot(x + dx, y + dy)), default=9)
            if near == 1:
                px[x, y] = (62, 58, 60, 255)
            elif near == 2:
                px[x, y] = (176, 172, 170, 255) if x < pot_x + pot_w // 2 else (112, 108, 110, 255)
            elif near == 3:
                px[x, y] = (34, 30, 30, 255)
    # The lip: the wall turned outward at the mouth, and the shadow the mouth throws on the stone above.
    for side in (0, 1):
        for i in range(4):
            x = pot_x - 4 + i if side == 0 else pot_x + pot_w + 3 - i
            px[x, pot_y - 1] = (34, 30, 30, 255)
            px[x, pot_y] = (176, 172, 170, 255) if side == 0 else (112, 108, 110, 255)
            px[x, pot_y + 1] = (34, 30, 30, 255)
    for x in range(pot_x, pot_x + pot_w):
        px[x, pot_y - 1] = (90, 76, 62, 255)
    # The tap and the channel the pour runs down, to the slot it lands in.
    gui_inset(px, 121, 53, 142, 59, (44, 37, 31, 255))
    etched(px, [(136, 49), (137, 50), (138, 51), (136, 62), (137, 61), (138, 60)])
    gui_big_slot(px, 143, 42)
    cutout(crucible, pot_x, pot_y, pot_w, pot_h, in_pot)
    crucible.save(folder / "crisol.png")

    # ---- the casting box: the shape, the steel it is cut from, the strainer, the sand and the mould.
    flask_x, flask_y, flask = 58, 26, 34
    sand_x, sand_y, sand = flask_x + 2, flask_y + 2, flask - 4

    box = gui_panel(15)
    px = box.load()
    gui_slot(px, 33, 33)
    gui_slot(px, 33, 65)
    gui_slot(px, 57, 65)
    rng = _random.Random(4411)
    for j in range(flask):
        for i in range(flask):
            x, y = flask_x + i, flask_y + j
            ring = min(i, j, flask - 1 - i, flask - 1 - j)
            if ring == 0:
                px[x, y] = (52, 34, 18, 255)
            elif ring == 1:
                px[x, y] = (160, 110, 64, 255) if i + j < flask else (104, 68, 36, 255)
            else:
                # Plain sand, a shade darker toward the middle where it has been rammed. The print is not
                # painted here: the screen draws it from the part that is actually in the box, which a
                # picture of a sword could never be for a pickaxe head.
                n = rng.randint(-7, 7)
                packed = -8 if 8 <= i < flask - 8 and 8 <= j < flask - 8 else 0
                px[x, y] = (204 + n + packed, 186 + n + packed, 142 + n + packed, 255)
    # The pour's own channel, from the flask to the mould that comes out of it.
    gui_inset(px, 94, 53, 134, 60, (44, 37, 31, 255))
    etched(px, [(128, 49), (129, 50), (130, 51), (128, 63), (129, 62), (130, 61)])
    gui_big_slot(px, 135, 42)
    box.save(folder / "caja_de_moldeo.png")

    # ---- the forge table's star, lit. White, so the screen can pour either table's colour into it.
    size = 4
    glow = Image.new("L", (256 * size, 256 * size), 0)
    from PIL import ImageDraw, ImageFilter
    draw = ImageDraw.Draw(glow)
    centres = [((x + 9) * size, (y + 9) * size) for x, y in STAR_POINTS]
    cx, cy = (STAR_CENTER[0] + 8.5) * size, (STAR_CENTER[1] + 8.5) * size
    for i in range(5):
        draw.line([centres[i], centres[(i + 2) % 5]], fill=255, width=2 * size)
    radius = 40.5 * size
    draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], outline=255, width=2 * size)
    core = glow.resize((256, 256), Image.LANCZOS)
    halo = glow.filter(ImageFilter.GaussianBlur(3.2 * size)).resize((256, 256), Image.LANCZOS)
    star = Image.new("RGBA", (256, 256), (255, 255, 255, 0))
    spx = star.load()
    for y in range(GUI_H):
        for x in range(GUI_W):
            alpha = min(255, int(core.getpixel((x, y)) * 0.9 + halo.getpixel((x, y)) * 1.5))
            # Nothing under the slots: what lies on the star is drawn over this, and its square stays clean.
            covered = any(px0 - 1 <= x < px0 + 17 and py0 - 1 <= y < py0 + 17 for px0, py0 in STAR_POINTS)
            covered = covered or (STAR_CENTER[0] - 5 <= x < STAR_CENTER[0] + 21 and STAR_CENTER[1] - 5 <= y < STAR_CENTER[1] + 21)
            if alpha > 2 and not covered and 16 < y < 106 and 5 < x < 108:
                spx[x, y] = (255, 255, 255, alpha)
    star.save(folder / "estrella_viva.png")


def generate_particle_sprites():
    """Vapor and gota: the two particles added after the first three, drawn here so they can be redrawn.

    Vapor is four frames of one puff coming apart — dense and small, then wider and thinner, then
    ragged — and the particle walks through them as it ages, which is what stops steam looking like
    a white ball that shrinks. Gota is a bead of molten metal, grey so the particle can take the colour
    of whatever metal is dripping.
    """
    import math
    import random as _random

    folder = ASSETS / "textures/particle"
    folder.mkdir(parents=True, exist_ok=True)
    rng = _random.Random(9127)
    for frame in range(4):
        # Sixteen pixels rather than the eight the others get by on: at eight, a puff coming apart is a
        # checkerboard, and it was one.
        puff = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        radius = 4.6 + frame * 0.7
        density = (1.0, 0.82, 0.6, 0.4)[frame]
        spread = frame * 1.5
        lobes = [(7.5 + rng.uniform(-1.0, 1.0) * spread, 7.5 + rng.uniform(-1.0, 1.0) * spread, rng.uniform(0.75, 1.0))
                 for _ in range(2 + frame)]
        for y in range(16):
            for x in range(16):
                body = 0.0
                for lx, ly, weight in lobes:
                    d = math.hypot(x - lx, y - ly) / (radius * weight)
                    body = max(body, 1.0 - d * d)
                # Nothing may touch the edge of the sprite, or the quad shows as a square.
                edge = min(x, y, 15 - x, 15 - y)
                body *= min(1.0, edge / 2.0)
                if body <= 0.02:
                    continue
                alpha = int(255 * min(1.0, body * 1.2) * density)
                light = 232 + int(23 * body)
                puff.putpixel((x, y), (light, light, min(255, light + 4), alpha))
        puff.save(folder / f"vapor_{frame}.png")
    write_json(ASSETS / "particles/vapor.json", {"textures": [f"forja:vapor_{i}" for i in range(4)]})

    for frame in range(2):
        bead = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        rows = ["...##...", "..####..", ".######.", ".######.", "..####..", "........", "........", "........"] if frame == 0 else \
               ["...#....", "...##...", "..####..", "..####..", "...##...", "........", "........", "........"]
        for y, row in enumerate(rows):
            for x, char in enumerate(row):
                if char == "#":
                    lit = 255 if (x, y) in ((3, 1), (3, 2), (2, 2)) else 214 if y < 3 else 176
                    bead.putpixel((x, y), (lit, lit, lit, 255))
        bead.save(folder / f"gota_{frame}.png")
    write_json(ASSETS / "particles/gota.json", {"textures": [f"forja:gota_{i}" for i in range(2)]})


def generate_cabinet_gui():
    """The parts cabinet's panel: three drawer fronts, nine slots to a drawer.

    It opened as vanilla's grey chest among panels of wood and stone. A drawer is a plank with a pull
    at each end, so that is what each row is: a front of the frame's own wood, bevelled the way a drawer
    sits proud of its carcass, a brass pull left and right, and the slots let into it. Twenty-two pixels
    to a drawer against the inventory's eighteen, which is what makes them read as three things and not
    as a grid.
    """
    import random as _random

    rng = _random.Random(2718)
    panel = gui_panel(15)
    px = panel.load()
    left, right = 10, 196
    wood, wood_light, wood_dark, outline = (132, 88, 50), (168, 118, 70), (84, 54, 28), (40, 26, 14)
    brass, brass_light, brass_dark = (206, 160, 78), (244, 214, 140), (122, 88, 34)
    for drawer in range(3):
        top = 19 + drawer * 22
        bottom = top + 22
        for y in range(top, bottom):
            for x in range(left, right):
                edge_x = min(x - left, right - 1 - x)
                edge_y = min(y - top, bottom - 1 - y)
                if edge_x == 0 or edge_y == 0:
                    px[x, y] = outline + (255,)
                elif edge_y == 1 and y < top + 3 or edge_x == 1 and x < left + 3:
                    px[x, y] = wood_light + (255,)
                elif edge_y == 1 or edge_x == 1:
                    px[x, y] = wood_dark + (255,)
                else:
                    # Grain running the length of the drawer: long streaks, a knot now and then.
                    grain = 9 if (y * 5 + (x // 23)) % 7 == 0 else -7 if (x * 3 + y * 11) % 29 == 0 else 0
                    n = rng.randint(-3, 3)
                    px[x, y] = tuple(max(0, min(255, c + grain + n)) for c in wood) + (255,)
        # The pulls: a brass plate with a bar across it, one at each end of the drawer.
        for side in (0, 1):
            ox = left + 3 if side == 0 else right - 9
            oy = top + 7
            for j in range(8):
                for i in range(6):
                    rim = i in (0, 5) or j in (0, 7)
                    px[ox + i, oy + j] = (brass_dark if rim else brass) + (255,)
            for i in range(1, 5):
                px[ox + i, oy + 3] = brass_light + (255,)
                px[ox + i, oy + 4] = brass_dark + (255,)
        for column in range(9):
            gui_slot(px, 21 + column * 18, top + 3)
    folder = ASSETS / "textures/gui"
    folder.mkdir(parents=True, exist_ok=True)
    panel.save(folder / "armario.png")


def generate_advancement_background():
    """The wall behind the mod's advancements: forge brick, dark, with the fire showing in the joints.

    The tab used vanilla's grey stone, which is the wall behind half the advancement tabs anybody has
    ever installed. This is a sixteen-pixel tile like that one, so it has to survive being repeated a
    few hundred times: two courses of brick, soot-dark so the icons and the lines between them stay the
    brightest things on the page, and a little heat in the mortar — few enough warm pixels that the
    repeat reads as a wall with a fire behind it rather than as a pattern of dots.
    """
    import random as _random

    rng = _random.Random(1911)
    tile = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        course = y // 8
        for x in range(16):
            along = (x + (8 if course else 0)) % 16
            joint = y % 8 == 7 or along == 15
            if joint:
                tile.putpixel((x, y), (20, 16, 16, 255))
                continue
            # Each brick lit along its top and left, in shadow along its bottom and right.
            lit = 8 if y % 8 == 0 or along == 0 else -6 if y % 8 == 6 or along == 14 else 0
            n = rng.randint(-4, 4)
            tile.putpixel((x, y), (50 + lit + n, 44 + lit + n, 46 + lit + n, 255))
    # The fire behind the wall, in two joints of the tile: only ever in the mortar, never on a brick.
    for x, y, heat in ((3, 7, (150, 70, 24)), (4, 7, (110, 48, 18)), (12, 15, (170, 86, 28)), (11, 15, (100, 44, 16))):
        tile.putpixel((x, y), heat + (255,))
    folder = ASSETS / "textures/gui/advancements/backgrounds"
    folder.mkdir(parents=True, exist_ok=True)
    tile.save(folder / "forja.png")


def generate_potential_assets():
    """Master flux and the empty orb: the two things the potential and the extraction table ask for.

    The flux is a pinch of pale, glittering salt in a twist of dark paper: what a smith throws on a weld
    so that it takes. Drawn as a small heap rather than as a lump or an ingot, because everything else in
    the mod that is rare is an ingot or a shard and this has to be told from them in a full inventory.
    The empty orb is the upgrade orb's own sphere with nothing in it: the same glass, no colour.
    """
    rows = [
        "................",
        "................",
        "................",
        ".......ww.......",
        "......wLLw......",
        ".....wLSLLw.....",
        "....wLLLLSLw....",
        "...wLSLLLLLLw...",
        "..pwLLLLSLLLwp..",
        ".pPPwwLLLLwwPPp.",
        ".pPPPPwwwwPPPPp.",
        "..pPPPPPPPPPPp..",
        "...ppPPPPPPpp...",
        ".....pppppp.....",
        "................",
        "................",
    ]
    colours = {
        "w": (190, 214, 226, 255),   # the rim of the heap
        "L": (232, 244, 250, 255),   # the salt
        "S": (255, 236, 170, 255),   # a grain catching the fire
        "P": (70, 52, 44, 255),      # the paper
        "p": (38, 28, 24, 255),
    }
    flux = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char in colours:
                flux.putpixel((x, y), colours[char])
    folder = ASSETS / "textures/item"
    flux.save(folder / "fundente_maestro.png")

    # The empty orb: the upgrade orb's shading, thinned to glass, with its glint kept.
    body = Image.open(folder / "orbe_de_mejora.png").convert("RGBA")
    glint = Image.open(folder / "orbe_de_mejora_brillo.png").convert("RGBA")
    empty = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            r, g, b, a = body.getpixel((x, y))
            if a == 0:
                continue
            edge = r < 100
            # Glass: only the rim and the lit side show; the middle is what is behind it.
            empty.putpixel((x, y), (150, 170, 184, 255) if edge else (206, 224, 236, 70 + (r - 120) // 2))
    empty.alpha_composite(glint)
    empty.save(folder / "orbe_vacio.png")

    for simple in ("fundente_maestro", "orbe_vacio"):
        write_json(ASSETS / f"models/item/{simple}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"forja:item/{simple}"}})
        write_json(ASSETS / f"items/{simple}.json", {"model": {"type": "minecraft:model", "model": f"forja:item/{simple}"}})

    # Star iron is what a meteorite leaves, blaze powder is the Nether and an echo shard is the deep dark:
    # three trips, none of them a matter of luck. Two pinches a craft, and an upgrade asks for one, once.
    write_json(DATA / "recipe/fundente_maestro.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["forja:hierro_estelar", "minecraft:blaze_powder", "minecraft:blaze_powder", "minecraft:echo_shard"],
        "result": {"id": "forja:fundente_maestro", "count": 2},
    })
    # Glass round a splinter of amethyst: the same stuff an upgrade is caught in when a piece is taken apart.
    write_json(DATA / "recipe/orbe_vacio.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" G ", "GAG", " G "],
        "key": {"G": "minecraft:glass", "A": "minecraft:amethyst_shard"},
        "result": {"id": "forja:orbe_vacio", "count": 2},
    })


def generate_extraction_table():
    """The extraction table: its block, its panel and its recipe.

    Dark stone like the greater forge, because it is work of the same order, with one thing on top that
    nothing else in the workshop has: a brass wheel set with gems, an orb in the cradle at its hub. It is
    the table's own screen seen from above, so the block says what opening it will look like.
    """
    import random as _random

    rng = _random.Random(6021)
    block_dir = ASSETS / "textures/block"
    stone, stone_light, stone_dark, seam = (58, 58, 66), (78, 78, 88), (40, 40, 48), (26, 26, 32)
    brass, brass_light, brass_dark = (198, 154, 74), (238, 206, 130), (118, 84, 32)

    def slab(img):
        for y in range(16):
            for x in range(16):
                n = rng.randint(-4, 4)
                img.putpixel((x, y), tuple(max(0, c + n) for c in stone) + (255,))

    top = Image.new("RGBA", (16, 16))
    slab(top)
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            top.putpixel((x, y), stone_dark + (255,))
    # A rivet in each corner, tucked against the border so it keeps clear of the wheel's diagonal gems.
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        top.putpixel((x, y), brass + (255,))
    # The top is the screen seen from above: the table opens onto a wheel (see generate_extraction_panel),
    # so a wheel is what lies on it - a thin brass rim with a gem in each of its eight sockets, a sunken
    # well, and the orb in a brass cradle at the hub. No spokes: at sixteen pixels they were all you saw.
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 5.6 <= d < 6.6:
                top.putpixel((x, y), (brass_light if x + y < 15 else brass_dark) + (255,))
            elif 3.6 <= d < 5.6:
                # The well, a shade under the slab, darker still right under the rim's upper left.
                deep = 20 if x + y < 15 and d > 4.8 else 12
                top.putpixel((x, y), tuple(max(0, c - deep) for c in top.getpixel((x, y))[:3]) + (255,))
            elif 2.6 <= d < 3.6:
                top.putpixel((x, y), (brass_dark if x + y < 15 else brass) + (255,))
            elif d < 2.6:
                # The orb: pale glass, lit from the upper left, a glint where the light lands.
                lit = max(0.0, 1.0 - (((x - 6.2) ** 2 + (y - 6.2) ** 2) ** 0.5) / 4.0)
                top.putpixel((x, y), (int(132 + 110 * lit), int(162 + 88 * lit), int(198 + 57 * lit), 255))
    top.putpixel((6, 6), (255, 255, 255, 255))
    # Eight gems, one a socket, in colours the upgrades really have.
    gems = ((224, 232, 240), (255, 159, 90), (79, 123, 232), (247, 226, 124), (168, 228, 255), (196, 120, 255), (120, 214, 120), (232, 84, 84))
    sockets = ((7, 1), (12, 3), (14, 7), (12, 12), (8, 14), (3, 12), (1, 8), (3, 3))
    for (x, y), gem in zip(sockets, gems):
        top.putpixel((x, y), gem + (255,))
    top.save(block_dir / "mesa_de_extraccion_top.png")

    side = Image.new("RGBA", (16, 16))
    slab(side)
    for x in range(16):
        side.putpixel((x, 0), brass_light + (255,))
        side.putpixel((x, 1), brass + (255,))
        side.putpixel((x, 2), brass_dark + (255,))
        side.putpixel((x, 15), stone_dark + (255,))
    # Two courses of dressed stone under the band.
    for x in range(16):
        side.putpixel((x, 8), seam + (255,))
    for y in range(3, 8):
        side.putpixel((7, y), seam + (255,))
    for y in range(9, 15):
        side.putpixel((3, y), seam + (255,))
        side.putpixel((11, y), seam + (255,))
    for y in range(3, 15):
        for x in range(16):
            r, g, b, a = side.getpixel((x, y))
            if (r, g, b) != seam and side.getpixel((x, y - 1))[:3] == seam:
                side.putpixel((x, y), stone_light + (255,))
    # A rivet at each end of the band.
    for x in (1, 14):
        side.putpixel((x, 1), brass_dark + (255,))
    side.save(block_dir / "mesa_de_extraccion_side.png")

    bottom = Image.new("RGBA", (16, 16))
    slab(bottom)
    bottom.save(block_dir / "mesa_de_extraccion_bottom.png")

    write_json(ASSETS / "models/block/mesa_de_extraccion.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "forja:block/mesa_de_extraccion_top",
            "bottom": "forja:block/mesa_de_extraccion_bottom",
            "side": "forja:block/mesa_de_extraccion_side",
            "particle": "forja:block/mesa_de_extraccion_side",
        },
    })
    write_json(ASSETS / "blockstates/mesa_de_extraccion.json", {"variants": {"": {"model": "forja:block/mesa_de_extraccion"}}})
    write_json(ASSETS / "items/mesa_de_extraccion.json", {"model": {"type": "minecraft:model", "model": "forja:block/mesa_de_extraccion"}})
    write_json(DATA / "loot_table/blocks/mesa_de_extraccion.json", {
        "type": "minecraft:block",
        "random_sequence": "forja:blocks/mesa_de_extraccion",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "forja:mesa_de_extraccion"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    # An orb to hold it, a grindstone to take it off, brass to cradle the orb, and stone to stand on.
    write_json(DATA / "recipe/mesa_de_extraccion.json", {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["LOL", "DGD", "DDD"],
        "key": {"L": "forja:laton", "O": "forja:orbe_vacio", "G": "minecraft:grindstone", "D": "minecraft:polished_deepslate"},
        "result": {"id": "forja:mesa_de_extraccion"},
    })

    # Its panel is a different kind of thing from the benches' and has its own function: generate_extraction_panel.


def generate_extraction_panel():
    """The extraction table's own kind of inventory: a wheel, not a grid.

    Andy asked for a new type of inventory for this table, and it has earned one: every other bench in the
    mod is a place where things are put together, and this is the one place where something is taken off.
    So it does not get the wood-and-sandstone panel of the benches. It gets the stone of its own block —
    dark slate, brass at the corners — and, where the others have a grid or a list, a WHEEL: the piece in
    the hub, eight sockets round it for the upgrades it carries (the screen sets a gem of each upgrade's
    colour in them), and to the right the card of whichever gem is chosen, the tray its price is paid into
    and the cradle the orb comes out in. Wider than the others (236 by 204) to give the wheel its room.

    From the same numbers as menu/ExtractionMenu; the test checks every slot against this picture.
    """
    import math
    import random as _random

    rng = _random.Random(8842)
    W, H = 236, 204
    panel = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    px = panel.load()
    slate, slate_light, slate_dark, outline = (52, 52, 62), (82, 82, 98), (30, 30, 38), (12, 12, 16)
    brass, brass_light, brass_dark = (198, 154, 74), (240, 210, 134), (112, 80, 30)

    for y in range(H):
        for x in range(W):
            n = rng.randint(-3, 3)
            px[x, y] = (slate[0] + n, slate[1] + n, slate[2] + n + 1, 255)
    # The frame: dressed stone, lit from the upper left like everything else in the mod.
    for y in range(H):
        for x in range(W):
            edge = min(x, y, W - 1 - x, H - 1 - y)
            if edge == 0:
                px[x, y] = outline + (255,)
            elif edge <= 3:
                lit = (x < 4 or y < 4) and edge == 1
                shade = (x > W - 5 or y > H - 5) and edge == 1
                base = slate_light if lit else slate_dark if shade else (44, 44, 54)
                course = -6 if (x // 12 + y // 12) % 2 == 0 and edge == 2 else 0
                px[x, y] = tuple(max(0, c + course) for c in base) + (255,)
            elif edge == 4:
                px[x, y] = (slate_dark if (x > W - 6 or y > H - 6) else slate_light) + (255,)
    # The header: a darker course with a line of brass under it.
    for y in range(4, 15):
        for x in range(4, W - 4):
            n = rng.randint(-2, 2)
            px[x, y] = (34 + n, 34 + n, 42 + n, 255)
    for x in range(4, W - 4):
        px[x, 15] = brass_dark + (255,)
        px[x, 16] = brass + (255,) if x % 2 == 0 else brass_light + (255,)
    # Brass corner plates, riveted.
    for cx, cy in ((0, 0), (W - 9, 0), (0, H - 9), (W - 9, H - 9)):
        for y in range(9):
            for x in range(9):
                border = x in (0, 8) or y in (0, 8)
                px[cx + x, cy + y] = (brass_dark if border else brass_light if x + y < 7 else brass) + (255,)
        px[cx + 4, cy + 4] = brass_dark + (255,)
        px[cx + 3, cy + 3] = (255, 240, 190, 255)

    def dark_slot(x, y):
        for j in range(18):
            for i in range(18):
                if i == 17 or j == 17:
                    px[x + i, y + j] = (96, 96, 114, 255)
                elif i == 0 or j == 0:
                    px[x + i, y + j] = (16, 16, 22, 255)
                else:
                    px[x + i, y + j] = (34, 34, 42, 255)

    def disc(cx, cy, radius, colour):
        r = int(math.ceil(radius))
        for y in range(-r, r + 1):
            for x in range(-r, r + 1):
                if x * x + y * y <= radius * radius:
                    px[cx + x, cy + y] = colour + (255,)

    def ring(cx, cy, inner, outer, colour_lit, colour_shade):
        r = int(math.ceil(outer))
        for y in range(-r, r + 1):
            for x in range(-r, r + 1):
                d = math.hypot(x, y)
                if inner <= d < outer:
                    px[cx + x, cy + y] = (colour_lit if x + y < 0 else colour_shade) + (255,)

    # ---- the wheel
    hub_x, hub_y = 56, 64
    disc(hub_x, hub_y, 41.5, (40, 40, 50))
    ring(hub_x, hub_y, 39.5, 41.5, brass_light, brass_dark)
    ring(hub_x, hub_y, 38.5, 39.5, outline, outline)
    ring(hub_x, hub_y, 17.0, 18.5, brass, brass_dark)
    # A spoke to each socket, so an empty one still reads as somewhere a thing goes.
    for k in range(8):
        angle = math.radians(-90 + 45 * k)
        for step in range(19, 23):
            sx = round(hub_x + math.cos(angle) * step)
            sy = round(hub_y + math.sin(angle) * step)
            px[sx, sy] = brass_dark + (255,)
        cx = round(hub_x + math.cos(angle) * 30)
        cy = round(hub_y + math.sin(angle) * 30)
        disc(cx, cy, 7.4, (70, 56, 28))
        disc(cx, cy, 6.4, (20, 20, 26))
        ring(cx, cy, 6.4, 7.4, brass, brass_dark)
    dark_slot(hub_x - 9, hub_y - 9)     # the piece: item at (48, 56)

    # ---- the card of the chosen upgrade
    for y in range(18, 65):
        for x in range(108, 229):
            if y == 18 or x == 108:
                px[x, y] = (14, 14, 18, 255)
            elif y == 64 or x == 228:
                px[x, y] = (96, 96, 114, 255)
            else:
                px[x, y] = (24, 24, 30, 255)
    for x, y in ((109, 19), (227, 19), (109, 63), (227, 63)):
        px[x, y] = brass + (255,)

    # ---- the tray: what it costs, the empty orb, and the cradle the full one comes out in
    for i in range(3):
        dark_slot(110 + i * 20, 69)     # payment: items at (111 + 20 i, 70)
    for x, y in [(170 + i, 78) for i in range(5)] + [(172, 76 + i) for i in range(5)]:
        px[x, y] = brass + (255,)
    dark_slot(177, 69)                  # the empty orb: item at (178, 70)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 4.4 <= d < 5.6:
                px[178 + x, 70 + y] = (58, 58, 72, 255)
    for x in range(197, 203):
        px[x, 78] = brass + (255,)
    for dx, dy in ((201, 76), (202, 77), (201, 80), (202, 79)):
        px[dx, dy] = brass + (255,)
    for y in range(67, 90):
        for x in range(204, 227):
            rim = x in (204, 226) or y in (67, 89)
            if rim:
                px[x, y] = (brass_light if x + y < 292 else brass_dark) + (255,)
    dark_slot(206, 69)                  # the orb that comes out: item at (207, 70)

    # ---- the player's own, in the same stone
    for row in range(3):
        for column in range(9):
            dark_slot(36 + column * 18, 121 + row * 18)
    for column in range(9):
        dark_slot(36 + column * 18, 179)
    panel.save(ASSETS / "textures/gui/mesa_de_extraccion.png")


def generate_wings_textures():
    """One grey wing texture; each material's equipment asset dyes it, the way the armor does."""
    wings = Image.open(io.BytesIO(jar_read("assets/minecraft/textures/entity/equipment/wings/elytra.png"))).convert("RGBA")
    grey = Image.new("RGBA", wings.size, (0, 0, 0, 0))
    for y in range(wings.height):
        for x in range(wings.width):
            r, g, b, a = wings.getpixel((x, y))
            if a:
                # Stretched to white so the dye carries the colour, like the armor plates.
                value = min(255, int(luminance((r, g, b, a)) * 1.8))
                grey.putpixel((x, y), (value, value, value, a))
    folder = ASSETS / "textures/entity/equipment/wings"
    folder.mkdir(parents=True, exist_ok=True)
    grey.save(folder / "alas.png")
    for material, color in MATERIAL_COLORS.items():
        write_json(ASSETS / f"equipment/alas_{material}.json", {
            "layers": {"wings": [{"texture": "forja:alas", "dyeable": {"color_when_undyed": color}}]},
        })


def split_armor(sprite_path, lining):
    pixels = opaque(vanilla(sprite_path))
    return {p: (1 if lining(p, c, pixels) else 0, luminance(c)) for p, c in pixels.items()}


def helmet_lining(p, c, pixels):
    """The dark inside of the helmet shows the padding."""
    return luminance(c) < 60 and all(n in pixels for n in neighbours(p, diagonal=False))


def chest_lining(p, c, pixels):
    """Shoulder tops and the hem."""
    edge = not all(n in pixels for n in neighbours(p, diagonal=False))
    return p[1] == 2 or (p[1] >= 12 and not edge)


def band_lining(rows):
    """Waistband or boot cuffs."""
    return lambda p, c, pixels: p[1] in rows and luminance(c) >= 45


# ---------------------------------------------------------------- the two magic weapons
#
# Andy chose both from drawings (docs/arma_magica): the crescent staff, model A2, and the forged tome,
# model C. They are kept here as they were drawn, a letter to a part, and shaded from their own outline
# with the light at the upper left, the way every hand-drawn sprite of the mod is.

CRESCENT_STAFF = [
    ".......EEEE.....",
    "......EE...E..g.",
    ".....EE.........",
    ".....E....GG....",
    ".....E...GGGG...",
    ".....EE..GGGG.E.",
    "......EE..GG.EE.",
    ".......EEEEEEE..",
    "......SSEEE.....",
    ".....SS.........",
    "....SS..........",
    "...SS...g.......",
    "..SS............",
    ".SS.............",
    "EE..............",
    "E...............",
]
FORGED_TOME = [
    "................",
    "...LTTTTTTTTT...",
    "..LLCTTTTTTTCp..",
    "..LLTTTTTTTTTp..",
    "..LLTTTgggTTTp..",
    "..LLTTgGGGgTTp.g",
    "..LLTTgGGGgTTp..",
    "..LLTTgGGGgTTp..",
    "g.LLTTTgggTTTp..",
    "..LLTTTTTTTTTp..",
    "..LLCTTTTTTTCp..",
    "..LLTTTTTTTTTp..",
    "...Lpppppppppp..",
    "....ppppppppp...",
    "................",
    "................",
]


def drawn(rows, labels):
    """A sprite from its letters: `labels` says which slot a letter belongs to; 'g' glows with the núcleo."""
    def same(x, y, letter):
        return 0 <= x < 16 and 0 <= y < 16 and rows[y][x] == letter
    pixels = {}
    for y, row in enumerate(rows):
        for x, letter in enumerate(row):
            if letter == "g":
                pixels[(x, y)] = (labels["G"], 236)
            elif letter in labels:
                lit = not same(x - 1, y, letter) or not same(x, y - 1, letter)
                dark = not same(x + 1, y, letter) or not same(x, y + 1, letter)
                level = 236 if lit and not dark else 128 if dark and not lit else 190 if lit else 172
                if letter == "G" and lit and not same(x - 1, y, letter) and not same(x, y - 1, letter):
                    level = 255
                pixels[(x, y)] = (labels[letter], level)
    return pixels


def crescent_staff():
    return drawn(CRESCENT_STAFF, {"G": 0, "E": 1, "S": 2})


def forged_tome():
    return drawn(FORGED_TOME, {"G": 0, "T": 1, "C": 2})


def tome_fixed():
    """What no part tints: the leather of the spine and the edges of the pages."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(FORGED_TOME):
        for x, letter in enumerate(row):
            if letter == "p":
                image.putpixel((x, y), (236, 226, 196, 255) if (x + y) % 2 else (212, 200, 168, 255))
            elif letter == "L":
                edge = x == 2 or row[x - 1] != "L"
                image.putpixel((x, y), (150, 98, 58, 255) if edge else (104, 66, 40, 255))
    return image


FIXED_LAYERS = {"grimorio": tome_fixed}

TOOL_LAYERS = {
    "baculo": crescent_staff,
    "grimorio": forged_tome,
    "pico": lambda: split_tool("item/iron_pickaxe.png"),
    "hacha": lambda: split_tool("item/iron_axe.png"),
    "pala": lambda: split_tool("item/iron_shovel.png"),
    "azada": lambda: split_tool("item/iron_hoe.png"),
    "martillo": sledgehammer,
    "lanza": spear,
    "tridente": trident,
    "mazo": mace,
    "guadana": scythe,
    "mangual": flail,
    "flecha": split_arrow,
    "gancho": grapple,
    "barda": lambda: split_horse_armor(None),
    "armadura_de_lobo": lambda: split_wolf_armor(None),
    "guanteletes": gauntlet,
    "picahacha": mattock,
    "espada": split_sword,
    "cincel": chisel,
    "daga": dagger,
    "espadon": greatsword,
    "alas": split_elytra,
    "casco": lambda: split_armor("item/iron_helmet.png", helmet_lining),
    "pechera": lambda: split_armor("item/iron_chestplate.png", chest_lining),
    "grebas": lambda: split_armor("item/iron_leggings.png", band_lining({2, 3})),
    "botas": lambda: split_armor("item/iron_boots.png", band_lining({3, 4})),
}


def normalized_layers(layers, count, size=16):
    """One grayscale image per label, each label stretched so its brightest pixel is white."""
    images = [Image.new("RGBA", (size, size), (0, 0, 0, 0)) for _ in range(count)]
    brightest = [1] * count
    for label, lum in layers.values():
        brightest[label] = max(brightest[label], lum)
    for (x, y), (label, lum) in layers.items():
        factor = min(2.6, 255 / brightest[label])
        g = max(0, min(255, int(lum * factor)))
        images[label].putpixel((x, y), (g, g, g, 255))
    return images


def centered(layers, label):
    part = {p: v for p, v in layers.items() if v[0] == label}
    xs = [p[0] for p in part]
    ys = [p[1] for p in part]
    dx = round(7.5 - (min(xs) + max(xs)) / 2)
    dy = round(7.5 - (min(ys) + max(ys)) / 2)
    return {(x + dx, y + dy): (0, v[1]) for (x, y), v in part.items()}


def whole(sprite_path):
    return {p: (0, luminance(c)) for p, c in opaque(vanilla(sprite_path)).items()}


PART_LAYERS = {
    # The loose parts, without what is only there on the finished thing: the núcleo without its sparks,
    # the setting without the ferrule at the far end of the shaft (it would drag the crescent off centre).
    "nucleo": lambda: centered({p: v for p, v in crescent_staff().items() if CRESCENT_STAFF[p[1]][p[0]] == "G"}, 0),
    "engaste": lambda: centered({p: v for p, v in crescent_staff().items() if p[1] < 9}, 1),
    "tapas": lambda: centered(forged_tome(), 1),
    "cabeza_pico": lambda: centered(split_tool("item/iron_pickaxe.png"), 0),
    "cabeza_hacha": lambda: centered(split_tool("item/iron_axe.png"), 0),
    "cabeza_pala": lambda: centered(split_tool("item/iron_shovel.png"), 0),
    "cabeza_azada": lambda: centered(split_tool("item/iron_hoe.png"), 0),
    "cabeza_martillo": lambda: centered(sledgehammer(), 0),
    "punta_lanza": lambda: centered(spear(), 0),
    "punta_tridente": lambda: centered(trident(), 0),
    "punta_cincel": lambda: centered(chisel(), 0),
    "cabeza_mazo": lambda: centered(mace(), 0),
    "hoja": lambda: centered(split_sword(), 0),
    "guarda": lambda: centered(split_sword(), 2),
    "mango": lambda: whole("item/stick.png"),
    "atadura": lambda: whole("item/lead.png"),
    "forro": lambda: whole("item/leather.png"),
    "membrana": lambda: whole("item/elytra.png"),
    # The head off the weapon itself rather than a ball drawn by hand: a part should be the thing you
    # will see swinging, and the hand-drawn one never matched it.
    "bola": lambda: centered(flail(), 0),
    "cadena": lambda: whole("item/iron_chain.png"),
    # Each part is cut straight out of the weapon, so the piece in your hand is the piece you will
    # see on the glove.
    "nudillos": lambda: centered(gauntlet(), 1),
    "manopla": lambda: centered(gauntlet(), 0),
    "remache": rivet,
    "punta_flecha": arrow_tip,
    "garfio": lambda: centered(grapple(), 0),
    "placa_barda": lambda: whole("item/iron_horse_armor.png"),
    "placa_lobo": lambda: whole("item/wolf_armor.png"),
    "emplumado": fletching,
    "placa_casco": lambda: whole("item/iron_helmet.png"),
    "placa_pechera": lambda: whole("item/iron_chestplate.png"),
    "placa_grebas": lambda: whole("item/iron_leggings.png"),
    "placa_botas": lambda: whole("item/iron_boots.png"),
    "brazos_arco": lambda: {p: (0, lum) for p, (label, lum, _) in split_bow("bow").items() if label == 0},
    "cuerda": lambda: whole("item/string.png"),
    "placa_escudo": lambda: shield_icon(False),
    "borde_escudo": lambda: shield_icon(True),
}


BOW_STATES = ["bow", "bow_pulling_0", "bow_pulling_1", "bow_pulling_2"]
STRING_COLOR = (0x44, 0x44, 0x44)
GRIP_COLORS = {(0x6B, 0x6B, 0x6B), (0x96, 0x96, 0x96)}
FLETCHING_COLORS = {(0xFF, 0xFF, 0xFF), (0xB1, 0xB1, 0xB1), (0xD8, 0xD8, 0xD8)}


def split_bow(state):
    """Limbs (0), string (1), grip wrap (2) and, while pulling, the arrow (3) of a vanilla bow sprite."""
    layers = {}
    for (x, y), c in opaque(vanilla(f"item/{state}.png")).items():
        rgb = c[:3]
        if rgb == STRING_COLOR:
            label = 1
        elif rgb in GRIP_COLORS:
            label = 2
        elif rgb in FLETCHING_COLORS or (state != "bow" and x - y in (0, 1) and rgb != (0x49, 0x36, 0x15)):
            label = 3
        else:
            label = 0
        layers[(x, y)] = (label, luminance(c), c)
    return layers


CROSSBOW_STATES = ["crossbow_standby", "crossbow_pulling_0", "crossbow_pulling_1", "crossbow_pulling_2", "crossbow_arrow", "crossbow_firework"]


def split_crossbow(state):
    """Limbs (0), string (1), stock (2), the trigger joint where limbs meet the stock (3) and, when
    loaded, the arrow or rocket (4) of a vanilla crossbow sprite. The limbs run along the top and
    left edges; the loaded projectile is whatever the drawn sprite has no color for."""
    pixels = opaque(vanilla(f"item/{state}.png"))
    drawn = opaque(vanilla("item/crossbow_pulling_2.png"))
    palette = set(drawn.values())
    layers = {}
    for (x, y), c in pixels.items():
        if drawn.get((x, y)) != c and c not in palette:
            layers[(x, y)] = (4, luminance(c), c)
        elif c[0] == c[1] == c[2]:
            layers[(x, y)] = (1, luminance(c), c)
        elif (y <= 3 and x >= 6) or (x <= 3 and y >= 7):
            layers[(x, y)] = (0, luminance(c), c)
        else:
            layers[(x, y)] = (2, luminance(c), c)
    limbs = {p for p, v in layers.items() if v[0] == 0}
    for p, (label, lum, c) in list(layers.items()):
        if label == 2 and adjacent(p, limbs):
            layers[p] = (3, lum, c)
    return layers


def generate_crossbow_textures(item_dir):
    for state in CROSSBOW_STATES:
        layers = split_crossbow(state)
        tinted = {p: (label, lum) for p, (label, lum, _) in layers.items() if label < 4}
        for label, image in enumerate(normalized_layers(tinted, 4)):
            path = item_dir / "ballesta" / f"{state}_{label}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path)
        projectile = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for p, (label, _, color) in layers.items():
            if label == 4:
                projectile.putpixel(p, color)
        if projectile.getbbox():
            projectile.save(item_dir / "ballesta" / f"{state}_4.png")


ROD_STATES = ["fishing_rod", "fishing_rod_cast"]


def split_rod(state):
    """Shaft (0) and line with its hook (1) of a vanilla fishing rod sprite: the browns are the shaft."""
    pixels = opaque(vanilla(f"item/{state}.png"))
    return {p: (0 if is_wood(c) else 1, luminance(c)) for p, c in pixels.items()}


def generate_rod_textures(item_dir):
    for state in ROD_STATES:
        for label, image in enumerate(normalized_layers(split_rod(state), 2)):
            path = item_dir / "cana" / f"{state}_{label}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path)


def generate_bow_textures(item_dir):
    for state in BOW_STATES:
        layers = split_bow(state)
        tinted = {p: (label, lum) for p, (label, lum, _) in layers.items() if label < 3}
        for label, image in enumerate(normalized_layers(tinted, 3)):
            path = item_dir / "arco" / f"{state}_{label}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path)
        arrow = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for p, (label, _, color) in layers.items():
            if label == 3:
                arrow.putpixel(p, color)
        if arrow.getbbox():
            arrow.save(item_dir / "arco" / f"{state}_3.png")


def generate_gauntlet_skins(item_dir):
    """The skin the 3D gauntlet wears, as opposed to the icon it used to be printed on.

    This is the whole reason the glove looked see-through. A box model samples a rectangle of its
    texture per face, and the rectangles were being taken off the flat item icon — a sprite that is
    mostly empty, because an icon is a shape on a transparent field. The knuckle sheet has 29 opaque
    pixels out of 256, so every plate came out with holes punched clean through it, and no amount of
    moving the boxes about was ever going to close them.

    A skin for a box has to be opaque everywhere, so that is what this is: solid grey, banded by which
    way the face points, with enough grain that it does not read as plastic. Colour still comes from
    the material tint, as it does for every other forged part.
    """
    folder = item_dir / "guanteletes"
    folder.mkdir(parents=True, exist_ok=True)
    # Up, sideways, down. Gentle, because Minecraft already shades a box by face direction and two
    # helpings of the same shadow turn the underside black.
    bands = ((0, 5, 214), (5, 11, 196), (11, 16, 168))
    for slot in range(3):
        skin = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
        for top, bottom, base in bands:
            for y in range(top, bottom):
                for x in range(16):
                    if slot == 0:        # leather: a soft, irregular grain with a seam of stitches
                        value = base - 10 + ((x * 7 + y * 13) % 5) * 5
                        if x == 3 and y % 3 != 2:
                            value -= 26
                    elif slot == 1:      # steel: brushed along the plate, with the odd nick in it
                        value = base + 12 - (y % 2) * 8
                        if (x * 5 + y * 3) % 11 == 0:
                            value -= 22
                    else:                # the rivet heads: small, bright and near enough flat
                        value = base + 22 - ((x + y) % 2) * 6
                    value = max(0, min(255, value))
                    skin.putpixel((x, y), (value, value, value, 255))
        skin.save(folder / f"piel_{slot}.png")


def generate_shield_textures(item_dir):
    folder = item_dir / "escudo"
    folder.mkdir(parents=True, exist_ok=True)
    plate = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            value = 205 if x % 4 else 150
            value -= 10 if (y * 3 + x * 5) % 7 == 0 else 0
            value += 25 if (x % 4 == 1) else 0
            if (x, y) in ((1, 2), (13, 2), (1, 13), (13, 13)):
                value = 250
            plate.putpixel((x, y), (value, value, value, 255))
    plate.save(folder / "placa.png")
    rim = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            value = 235 if y % 4 == 0 else 190 if x % 2 else 175
            rim.putpixel((x, y), (value, value, value, 255))
    rim.save(folder / "borde.png")
    grip = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            value = 215 if (x + y) % 3 else 170
            grip.putpixel((x, y), (value, value, value, 255))
    grip.save(folder / "asa.png")


def shield_icon(rim_only):
    """Part icons for the shield plate and rim: a kite-free round-cornered board seen from the front."""
    layers = {}
    for y in range(1, 15):
        for x in range(3, 13):
            corner = (x in (3, 12)) and (y in (1, 14))
            if corner:
                continue
            edge = x in (3, 12) or y in (1, 14) or ((x in (4, 11)) and (y in (1, 14))) or ((x in (3, 12)) and (y in (2, 13)))
            if rim_only and not edge:
                continue
            lum = 70 if edge else (200 if x % 3 else 150)
            if not rim_only and (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
                lum = 245
            layers[(x, y)] = (0, lum)
    return layers


def generate_item_textures():
    item_dir = ASSETS / "textures/item"
    for folder in list(TYPES) + ["parte"]:
        shutil.rmtree(item_dir / folder, ignore_errors=True)
    generate_bow_textures(item_dir)
    generate_crossbow_textures(item_dir)
    generate_rod_textures(item_dir)
    generate_gauntlet_skins(item_dir)
    generate_shield_textures(item_dir)
    for type_id, slots in TYPES.items():
        if type_id in SPECIAL - {"lanza"}:
            continue
        for slot, image in enumerate(normalized_layers(TOOL_LAYERS[type_id](), len(slots))):
            path = item_dir / type_id / f"{slot}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path)
        if type_id in FIXED_LAYERS:
            FIXED_LAYERS[type_id]().save(item_dir / type_id / "fijo.png")
    for slot, image in enumerate(normalized_layers(spear_in_hand(), 3, 32)):
        image.save(item_dir / "lanza" / f"mano_{slot}.png")
    for part, build in PART_LAYERS.items():
        path = item_dir / "parte" / f"{part}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        normalized_layers(build(), 1)[0].save(path)


# ---------------------------------------------------------------- worn armor (64x32)

BOXES = {
    # name: (u, v, w, h, d) in the humanoid armor texture layout
    "head": (0, 0, 8, 8, 8),
    "leg": (0, 16, 4, 12, 4),
    "body": (16, 16, 8, 12, 4),
    "arm": (40, 16, 4, 12, 4),
}
SIDES = ("front", "back", "left", "right")


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def locate(x, y):
    for box, spec in BOXES.items():
        for face, (fx, fy, fw, fh) in faces(*spec).items():
            if fx <= x < fx + fw and fy <= y < fy + fh:
                return box, face, x - fx, y - fy
    return None


def armor_pixel(layer, box, face, fx, fy, rows):
    """Returns (is_lining, gray value) for one covered pixel of Forja's armor design."""
    side = face in SIDES
    if box == "head":
        if face == "top":
            if fx in (3, 4):
                return False, 236
            return False, 252 if (fx, fy) in ((1, 1), (6, 1), (1, 6), (6, 6)) else 200
        if side and fy == max(rows):
            return True, 0
        if face == "front" and fx in (3, 4) and fy > 2:
            return False, 224
        return False, 228 if fy == 0 else 140 if fy == 2 else 252 if fy == 1 and fx % 3 == 1 else 194 - fy * 4
    if box == "body" and layer == "humanoid":
        if not side:
            return False, 200
        if fy == max(rows) or (face in ("front", "back") and fy == min(rows) and 2 <= fx <= 5):
            return True, 0
        if face == "front":
            if fy == 4:
                return False, 140
            if (fx, fy) in ((1, 1), (6, 1)):
                return False, 252
            if fx in (3, 4) and fy > 4:
                return False, 172
            return False, 150 if fy in (7, 9) else 208 - fy * 3
        if face == "back":
            return False, 145 if fy % 3 == 0 else 195
        return False, 145 if fy == 4 else 182
    if box == "arm":
        if not side:
            return False, 252 if (fx, fy) in ((1, 1), (2, 2)) else 212
        if fy == max(rows):
            return True, 0
        return False, 228 if fy == 0 else 145 if fy == 2 else 252 if fy == 1 and fx == 1 else 196
    if box == "leg" and layer == "humanoid":
        if face == "bottom":
            return False, 110
        if not side:
            return False, 190
        if fy == min(rows):
            return True, 0
        if face == "front" and fy >= 9:
            return False, 226
        return False, 150 if fy == 8 else 252 if fx == 1 and fy == 7 else 188
    if box == "body" and layer == "humanoid_leggings":
        if side and fy <= min(rows) + 1:
            if face == "front" and fx in (3, 4):
                return False, 242  # belt buckle
            return True, 0
        return False, 150 if fy == 10 else 190
    if box == "leg" and layer == "humanoid_leggings":
        if face == "front" and fy in (4, 5):
            return False, 252 if fx in (1, 2) and fy == 4 else 228
        if fy == 2:
            return False, 150
        return False, 165 if side and fx == 0 else 192
    return False, 190


def paint_armor(layer):
    """Paints Forja's armor over the areas vanilla iron armor covers. Returns (plate, lining) images."""
    mask = Image.open(io.BytesIO(jar_read(f"assets/minecraft/textures/entity/equipment/{layer}/iron.png"))).convert("RGBA")
    covered = [(x, y) for y in range(32) for x in range(64) if mask.getpixel((x, y))[3] > 0]
    plate = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    lining = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    columns = {}
    located = {}
    for (x, y) in covered:
        where = locate(x, y)
        if where:
            located[(x, y)] = where
            box, face, fx, fy = where
            columns.setdefault((box, face, fx), []).append(fy)

    for (x, y), (box, face, fx, fy) in located.items():
        is_lining, value = armor_pixel(layer, box, face, fx, fy, columns[(box, face, fx)])
        if is_lining:
            g = 200 + ((fx + fy) % 2) * 24
            lining.putpixel((x, y), (g, g, g, 255))
        else:
            plate.putpixel((x, y), (value, value, value, 255))
    return plate, lining


def generate_armor_textures():
    for layer in ("humanoid", "humanoid_leggings"):
        plate, lining = paint_armor(layer)
        folder = ASSETS / "textures/entity/equipment" / layer
        folder.mkdir(parents=True, exist_ok=True)
        plate.save(folder / "placa.png")
        lining.save(folder / "forro.png")

    shutil.rmtree(ASSETS / "equipment", ignore_errors=True)
    for plate_name, plate_color in MATERIAL_COLORS.items():
        for lining_name, lining_color in MATERIAL_COLORS.items():
            layers = [
                {"texture": "forja:forro", "dyeable": {"color_when_undyed": lining_color}},
                {"texture": "forja:placa", "dyeable": {"color_when_undyed": plate_color}},
            ]
            write_json(ASSETS / f"equipment/{plate_name}_{lining_name}.json", {"layers": {"humanoid": layers, "humanoid_leggings": layers}})


# ---------------------------------------------------------------- forge table and icon

# The kit every bench in the workshop is built from. It is three things, taken from the casting tables
# Andy kept: stone with a colour of its own, ironwork at the foot and the lip, and a front face with a
# feature you can name. Nothing here is decorative — the point is that four benches standing in a row
# are four different blocks at a glance.
BENCH_IRON = (128, 130, 138)
BENCH_IRON_L = (188, 190, 200)
BENCH_IRON_D = (62, 64, 72)
BENCH_GOLD = (214, 175, 84)
BENCH_GOLD_D = (138, 106, 42)
BENCH_COPPER = (196, 116, 72)
BENCH_COPPER_L = (232, 156, 108)
BENCH_COPPER_D = (116, 62, 36)


def bench_shade(colour, factor):
    return tuple(min(255, int(c * factor)) for c in colour[:3]) + (255,)


def bench_banded(base, light, mid, dark):
    """A band of metal across the top of a side, and legs down both ends."""
    image = base.copy()
    for x in range(16):
        image.putpixel((x, 0), light + (255,))
        image.putpixel((x, 1), mid + (255,))
        image.putpixel((x, 2), dark + (255,))
    for y in range(3, 16):
        for x in (0, 1, 14, 15):
            image.putpixel((x, y), (dark if x in (0, 15) else mid) + (255,))
    return image


def bench_bedded(base, rough, rim, deep=0.55):
    """A sunken working bed in the middle of a top face, lit on two sides and shadowed on the other."""
    image = base.copy()
    for y in range(4, 12):
        for x in range(4, 12):
            image.putpixel((x, y), bench_shade(rough.getpixel((x, y)), deep))
    for i in range(4, 12):
        image.putpixel((i, 4), BENCH_IRON_D + (255,))
        image.putpixel((4, i), BENCH_IRON_D + (255,))
        image.putpixel((i, 11), rim + (255,))
        image.putpixel((11, i), rim + (255,))
    return image


def bench_posts(image, light, mid):
    for a, b in ((0, 0), (14, 0), (0, 14), (14, 14)):
        for dx in range(2):
            for dy in range(2):
                image.putpixel((a + dx, b + dy), (light if dx + dy == 0 else mid) + (255,))
    return image


def bench_drawer(image, rough, top_row, height, pull):
    """A drawer of cut stone with a metal pull: the feature that says "things live in here"."""
    for y in range(top_row, top_row + height):
        for x in range(3, 13):
            image.putpixel((x, y), bench_shade(rough.getpixel((x, y)), 0.62))
    for x in range(3, 13):
        image.putpixel((x, top_row), bench_shade(rough.getpixel((x, top_row)), 0.38))
        image.putpixel((x, top_row + height - 1), bench_shade(rough.getpixel((x, top_row + height - 1)), 1.05))
    for x in range(6, 10):
        image.putpixel((x, top_row + height // 2), pull + (255,))
    return image


# ---------------------------------------------------------------- the hot blocks
#
# Everything in the workshop that burns is built from these pieces, so that a crucible, a mould box
# and a dead forge read as the same shop rather than as three mods. The pieces are: a lip, a firebox
# mouth, a bowl sunk into a top face, and rivets.

FORGE_COAL = (30, 27, 28)
FORGE_ASH = (108, 104, 102)
FORGE_ASH_D = (72, 69, 68)


def forge_grain(image, colour, rng, spread=9, box=None):
    x0, y0, x1, y1 = box or (0, 0, 16, 16)
    for y in range(y0, y1):
        for x in range(x0, x1):
            g = rng.randint(-spread, spread)
            image.putpixel((x, y), tuple(max(0, min(255, c + g)) for c in colour) + (255,))
    return image


def forge_lip(image, light, mid, dark, rows=(0, 1, 2)):
    """The band of metal round the top of a side: the thing that says this is a vessel."""
    for x in range(16):
        image.putpixel((x, rows[0]), light + (255,))
        image.putpixel((x, rows[1]), mid + (255,))
        image.putpixel((x, rows[2]), dark + (255,))
    return image


def forge_rivets(image, row, colour, shadow, step=4, start=2):
    for x in range(start, 16, step):
        image.putpixel((x, row), colour + (255,))
        if row + 1 < 16:
            image.putpixel((x, row + 1), shadow + (255,))
    return image


def forge_mouth(image, lit, rng, heat=(255, 150, 50), top=11, bars=(5, 8, 11)):
    """The firebox at the foot: an arch you can see the fire through, with a grate across it.

    This is the one feature every hot block in the mod wears, and it is why they read as working even
    when they are not: a cold mouth is a black arch full of dead coal, and a live one is the same arch
    with the fire coming out of it.
    """
    arch = {}
    for y in range(top, 16):
        inset = 4 if y == top else 3
        arch[y] = range(inset, 16 - inset)
    for y, span in arch.items():
        for x in span:
            if lit:
                low = (y - top) / max(1, 15 - top)
                level = min(1.0, 0.40 + low * 0.80 + (rng.random() - 0.5) * 0.25)
                if level > 0.94:
                    image.putpixel((x, y), (255, 240, 208, 255))
                else:
                    image.putpixel((x, y), tuple(int(c * level) for c in heat) + (255,))
            else:
                dead = rng.random() < 0.18
                image.putpixel((x, y), (74, 40, 30, 255) if dead else FORGE_COAL + (255,))
    # The grate, drawn over the fire so the mouth never reads as a hole.
    for x in bars:
        for y in arch:
            if x in arch[y]:
                image.putpixel((x, y), (46, 42, 44, 255))
    return image


def forge_bowl(image, rim, wall, inner, rng, melt=None):
    """A top face with a pot sunk into it: four square rings stepping down to what is inside.

    Square rings rather than round ones on purpose - at sixteen pixels a circle turns to mush, and a
    stepped square reads as a vessel from any distance.
    """
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            lit_side = x + y < 15
            if d >= 7:
                image.putpixel((x, y), (rim if lit_side else tuple(int(c * 0.72) for c in rim)) + (255,))
            elif d >= 6:
                image.putpixel((x, y), tuple(min(255, int(c * (1.05 if lit_side else 0.86))) for c in wall) + (255,))
            elif d >= 5:
                image.putpixel((x, y), tuple(int(c * 0.70) for c in wall) + (255,))
            elif d >= 4:
                image.putpixel((x, y), tuple(int(c * 0.50) for c in wall) + (255,))
            elif melt is None:
                g = rng.randint(-8, 8)
                image.putpixel((x, y), tuple(max(0, c + g) for c in inner) + (255,))
            else:
                # The melt: brightest in the middle, with a skin of dross drawn on at the edge.
                near = 1.0 - d / 4.0
                level = min(1.0, 0.45 + near * 0.70 + (rng.random() - 0.5) * 0.22)
                if level > 0.95:
                    image.putpixel((x, y), (255, 244, 214, 255))
                else:
                    image.putpixel((x, y), tuple(int(c * level) for c in melt) + (255,))
    if melt is not None:
        for _ in range(5):
            x, y = rng.randrange(5, 11), rng.randrange(5, 11)
            image.putpixel((x, y), tuple(int(c * 0.42) for c in melt) + (255,))
    return image


def generate_block_textures():
    """The work benches: dark stone, ironwork, and a face you can name from across the room.

    Direction A, the one Andy picked, taken from what the casting tables get right. The old versions of
    these were a vanilla plank texture with two lines scratched into them, which is why they read as
    nothing in particular.
    """
    import random
    rng = random.Random(9781)
    block_dir = ASSETS / "textures/block"
    block_dir.mkdir(parents=True, exist_ok=True)

    IRON = (128, 130, 138)
    IRON_L = (188, 190, 200)
    IRON_D = (62, 64, 72)
    GOLD = (214, 175, 84)
    GOLD_D = (138, 106, 42)

    def shade(colour, factor):
        return tuple(min(255, int(c * factor)) for c in colour[:3]) + (255,)

    def banded(base, light, mid, dark):
        """A band of metal across the top of a side, and legs down both ends."""
        image = base.copy()
        for x in range(16):
            image.putpixel((x, 0), light + (255,))
            image.putpixel((x, 1), mid + (255,))
            image.putpixel((x, 2), dark + (255,))
        for y in range(3, 16):
            for x in (0, 1, 14, 15):
                image.putpixel((x, y), (dark if x in (0, 15) else mid) + (255,))
        return image

    def bedded(base, rough, rim, deep=0.55):
        """A sunken working bed in the middle of a top face, with a lit lip on two sides."""
        image = base.copy()
        for y in range(4, 12):
            for x in range(4, 12):
                image.putpixel((x, y), shade(rough.getpixel((x, y)), deep))
        for i in range(4, 12):
            image.putpixel((i, 4), IRON_D + (255,))
            image.putpixel((4, i), IRON_D + (255,))
            image.putpixel((i, 11), rim + (255,))
            image.putpixel((11, i), rim + (255,))
        return image

    def posts(image, light, mid):
        for a, b in ((0, 0), (14, 0), (0, 14), (14, 14)):
            for dx in range(2):
                for dy in range(2):
                    image.putpixel((a + dx, b + dy), (light if dx + dy == 0 else mid) + (255,))
        return image

    slate = vanilla("block/polished_deepslate.png")
    rough_slate = vanilla("block/deepslate.png")

    # ---- mesa de forja: an anvil in the bed, a drawer at the front.
    top = posts(bedded(slate, rough_slate, IRON_L), IRON_L, IRON)
    for y in range(6, 10):
        for x in range(6, 10):
            top.putpixel((x, y), (IRON if (x + y) % 2 else IRON_L) + (255,))
    top.save(block_dir / "mesa_de_forja_top.png")

    side = banded(slate, IRON_L, IRON, IRON_D)
    # Tongs and a hammer on the rack, in the metal of the band so it reads as one workshop.
    for y in range(6, 12):
        side.putpixel((5, y), (IRON_L if y < 10 else IRON) + (255,))
    side.putpixel((5, 12), (226, 228, 236, 255))
    for x in range(8, 13):
        side.putpixel((x, 7), IRON_L + (255,))
        side.putpixel((x, 8), IRON + (255,))
    side.save(block_dir / "mesa_de_forja_side.png")

    front = banded(slate, IRON_L, IRON, IRON_D)
    for y in range(6, 13):
        for x in range(3, 13):
            front.putpixel((x, y), shade(rough_slate.getpixel((x, y)), 0.62))
    for x in range(3, 13):
        front.putpixel((x, 6), shade(rough_slate.getpixel((x, 6)), 0.38))
        front.putpixel((x, 12), shade(rough_slate.getpixel((x, 12)), 1.05))
    for x in range(6, 10):
        front.putpixel((x, 9), IRON_L + (255,))
        front.putpixel((x, 10), IRON_D + (255,))
    front.save(block_dir / "mesa_de_forja_front.png")

    # ---- mesa de forja mayor: the same bench, in blacker stone with gold at the lip.
    black = vanilla("block/polished_blackstone.png")
    rough_black = vanilla("block/blackstone.png")
    top = posts(bedded(black, rough_black, GOLD), GOLD, GOLD_D)
    # Two anvils rather than one: the greater table does the work the other one will not.
    for y in range(5, 8):
        for x in range(5, 11):
            top.putpixel((x, y), (IRON_L if (x + y) % 2 else IRON) + (255,))
    for y in range(9, 11):
        for x in range(6, 10):
            top.putpixel((x, y), (IRON if (x + y) % 2 else IRON_D) + (255,))
    top.save(block_dir / "mesa_de_forja_mayor_top.png")

    side = banded(black, (246, 216, 140), GOLD, GOLD_D)
    for y in range(6, 13):
        side.putpixel((5, y), (IRON_L if y < 10 else IRON) + (255,))
    for x in range(8, 13):
        side.putpixel((x, 6), GOLD + (255,))
        side.putpixel((x, 7), GOLD_D + (255,))
        side.putpixel((x, 9), IRON_L + (255,))
        side.putpixel((x, 10), IRON + (255,))
    side.save(block_dir / "mesa_de_forja_mayor_side.png")

    front = banded(black, (246, 216, 140), GOLD, GOLD_D)
    # Two drawers, banded in gold.
    for row in (5, 10):
        for y in range(row, row + 4):
            for x in range(3, 13):
                front.putpixel((x, y), shade(rough_black.getpixel((x, y)), 0.60))
        for x in range(3, 13):
            front.putpixel((x, row), shade(rough_black.getpixel((x, row)), 0.34))
        for x in range(6, 10):
            front.putpixel((x, row + 2), GOLD + (255,))
    front.save(block_dir / "mesa_de_forja_mayor_front.png")


def generate_parts_table_textures():
    """The parts table: the same dark stone as the forge bench, but everything on it is for cutting.

    Its face is a grindstone wheel, its top is a cutting bed with part outlines chalked on it, and its
    metal is copper rather than iron — so that at a glance, across a workshop, you never reach for the
    wrong bench.
    """
    block_dir = ASSETS / "textures/block"
    slate = vanilla("block/polished_deepslate.png")
    rough = vanilla("block/deepslate.png")

    top = bench_posts(bench_bedded(slate, rough, BENCH_COPPER_L), BENCH_COPPER_L, BENCH_COPPER)
    # Part outlines chalked onto the cutting bed: a blade, and a ring for a binding.
    for i in range(5, 11):
        top.putpixel((i, i), BENCH_COPPER_L + (255,))
    for (x, y) in ((6, 9), (7, 10), (9, 6), (10, 7)):
        top.putpixel((x, y), BENCH_COPPER + (255,))
    top.save(block_dir / "mesa_de_piezas_top.png")

    side = bench_banded(slate, BENCH_COPPER_L, BENCH_COPPER, BENCH_COPPER_D)
    # A saw hanging on the rack: a straight back with teeth under it.
    for x in range(4, 13):
        side.putpixel((x, 7), BENCH_COPPER_L + (255,))
        side.putpixel((x, 8), BENCH_COPPER + (255,))
        if x % 2:
            side.putpixel((x, 9), BENCH_COPPER_D + (255,))
    side.save(block_dir / "mesa_de_piezas_side.png")

    front = bench_banded(slate, BENCH_COPPER_L, BENCH_COPPER, BENCH_COPPER_D)
    # The grindstone wheel, which is what this bench is for.
    for y in range(5, 13):
        for x in range(4, 12):
            dx, dy = x - 7.5, y - 8.5
            if dx * dx + dy * dy <= 14:
                front.putpixel((x, y), bench_shade(rough.getpixel((x, y)), 1.15))
            if 6 <= dx * dx + dy * dy <= 14:
                front.putpixel((x, y), BENCH_COPPER_D + (255,))
    front.putpixel((7, 8), BENCH_COPPER_L + (255,))
    front.putpixel((8, 8), BENCH_COPPER_L + (255,))
    front.save(block_dir / "mesa_de_piezas_front.png")


def generate_saddlery_textures():
    """The saddlery: the same stone bench, topped in leather and bound in brass.

    It is the only bench in the workshop with something soft on it, which is the point: the block that
    works on a living animal should not look like the one you hammer steel on.
    """
    block_dir = ASSETS / "textures/block"
    slate = vanilla("block/polished_deepslate.png")
    rough = vanilla("block/deepslate.png")
    rng = __import__("random").Random(4411)
    leather = (154, 106, 62)
    leather_l = (196, 150, 96)
    leather_d = (104, 68, 38)

    top = bench_posts(slate.copy(), BENCH_GOLD, BENCH_GOLD_D)
    # A leather pad stretched over the top, stitched round the edge.
    for y in range(3, 13):
        for x in range(3, 13):
            g = rng.randint(-9, 9)
            top.putpixel((x, y), tuple(max(0, c + g) for c in leather) + (255,))
    for i in range(3, 13):
        top.putpixel((i, 3), leather_l + (255,))
        top.putpixel((3, i), leather_l + (255,))
        top.putpixel((i, 12), leather_d + (255,))
        top.putpixel((12, i), leather_d + (255,))
    for i in range(4, 12, 2):
        top.putpixel((i, 4), BENCH_GOLD + (255,))
        top.putpixel((i, 11), BENCH_GOLD + (255,))
    top.save(block_dir / "mesa_de_talabarteria_top.png")

    side = bench_banded(slate, (246, 216, 140), BENCH_GOLD, BENCH_GOLD_D)
    # Straps hanging over the side, buckled.
    for x in (5, 10):
        for y in range(4, 13):
            side.putpixel((x, y), leather + (255,))
            side.putpixel((x + 1, y), leather_d + (255,))
        side.putpixel((x, 7), BENCH_GOLD + (255,))
        side.putpixel((x + 1, 7), BENCH_GOLD_D + (255,))
    side.save(block_dir / "mesa_de_talabarteria_side.png")

    front = bench_banded(slate, (246, 216, 140), BENCH_GOLD, BENCH_GOLD_D)
    front = bench_drawer(front, rough, 6, 7, BENCH_GOLD)
    # A saddle horn over the drawer, which is the one shape nothing else in the mod has.
    for (x, y) in ((7, 4), (8, 4), (6, 5), (7, 5), (8, 5), (9, 5)):
        front.putpixel((x, y), leather_l + (255,))
    front.save(block_dir / "mesa_de_talabarteria_front.png")


def generate_cabinet_textures():
    """The cabinet: the same stone, and nothing on it but drawers, because that is all it is."""
    block_dir = ASSETS / "textures/block"
    slate = vanilla("block/polished_deepslate.png")
    rough = vanilla("block/deepslate.png")

    top = bench_posts(slate.copy(), BENCH_COPPER_L, BENCH_COPPER)
    # Parts left out on top of it: a head and a haft.
    for (x, y) in ((5, 5), (6, 5), (7, 5), (5, 6)):
        top.putpixel((x, y), BENCH_IRON_L + (255,))
    for i in range(8, 12):
        top.putpixel((i, i), (150, 106, 62, 255))
    for i in range(1, 15):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            if top.getpixel((x, y))[:3] != BENCH_COPPER_L:
                top.putpixel((x, y), BENCH_COPPER_D + (255,))
    top.save(block_dir / "armario_de_piezas_top.png")

    side = bench_banded(slate, BENCH_COPPER_L, BENCH_COPPER, BENCH_COPPER_D)
    for y in (6, 11):
        for x in range(2, 14):
            side.putpixel((x, y), BENCH_COPPER_D + (255,))
            side.putpixel((x, y - 1), BENCH_COPPER_L + (255,))
    side.save(block_dir / "armario_de_piezas_side.png")

    front = bench_banded(slate, BENCH_COPPER_L, BENCH_COPPER, BENCH_COPPER_D)
    # Three drawers, each with its pull: the block says what it does without a tooltip.
    for row in (4, 8, 12):
        if row + 3 > 15:
            continue
        for y in range(row, min(16, row + 3)):
            for x in range(3, 13):
                front.putpixel((x, y), bench_shade(rough.getpixel((x, y)), 0.62))
        for x in range(3, 13):
            front.putpixel((x, row), bench_shade(rough.getpixel((x, row)), 0.36))
        for x in range(6, 10):
            front.putpixel((x, row + 1), BENCH_COPPER_L + (255,))
    front.save(block_dir / "armario_de_piezas_front.png")


def generate_guide_texture():
    """The guide book: vanilla's book with a deep red cover and a small hammer stamped on it."""
    book = vanilla("item/book.png")
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            r, g, b, a = book.getpixel((x, y))
            if not a:
                continue
            if r > g + 15:
                lum = luminance((r, g, b, a))
                out.putpixel((x, y), (min(255, lum * 2 + 40), lum // 2, lum // 3, 255))
            else:
                out.putpixel((x, y), (r, g, b, a))
    for pos, color in (((7, 5), (200, 200, 208)), ((8, 5), (200, 200, 208)), ((9, 5), (150, 150, 158)),
                       ((8, 6), (120, 84, 48)), ((7, 7), (120, 84, 48)), ((6, 8), (96, 64, 36))):
        out.putpixel(pos, color + (255,))
    path = ASSETS / "textures/item/guia_de_forja.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    out.save(path)


# ---------------------------------------------------------------- table screens and templates

GUI_W, GUI_H = 206, 196
INVENTORY = (22, 114)
STAR_POINTS = [(48, 18), (80, 42), (68, 80), (28, 80), (16, 42)]
STAR_CENTER = (48, 52)


def gui_panel(header_height):
    """Wooden frame with iron corners around a warm stone panel, plus the player's inventory slots."""
    import random
    rng = random.Random(7)
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    px = img.load()
    for y in range(GUI_H):
        for x in range(GUI_W):
            n = rng.randint(-5, 5)
            px[x, y] = (196 + n, 186 + n, 166 + n, 255)
    wood_light, wood, wood_dark, outline = (150, 101, 58, 255), (116, 76, 42, 255), (78, 50, 26, 255), (32, 21, 12, 255)
    for y in range(GUI_H):
        for x in range(GUI_W):
            edge = min(x, y, GUI_W - 1 - x, GUI_H - 1 - y)
            if edge == 0:
                px[x, y] = outline
            elif edge <= 3:
                plank = wood_light if (x < 4 or y < 4) and edge == 1 else wood_dark if (x > GUI_W - 5 or y > GUI_H - 5) and edge == 1 else wood
                grain = -8 if (x * 7 + y * 3) % 11 == 0 else 0
                px[x, y] = tuple(max(0, c + grain) for c in plank[:3]) + (255,)
            elif edge == 4:
                px[x, y] = (150, 140, 122, 255) if (x > GUI_W - 6 or y > GUI_H - 6) else (226, 218, 200, 255)
    if header_height:
        for y in range(4, header_height):
            for x in range(4, GUI_W - 4):
                grain = 10 if (y - 4) % 5 == 0 else -6 if (x + y * 13) % 17 == 0 else 0
                px[x, y] = (106 + grain, 70 + grain, 40 + grain, 255)
        for x in range(4, GUI_W - 4):
            px[x, header_height] = (58, 38, 20, 255)
    iron, iron_light, iron_dark = (128, 132, 142, 255), (200, 204, 214, 255), (66, 68, 76, 255)
    for cx, cy in ((0, 0), (GUI_W - 9, 0), (0, GUI_H - 9), (GUI_W - 9, GUI_H - 9)):
        for y in range(9):
            for x in range(9):
                border = x in (0, 8) or y in (0, 8)
                px[cx + x, cy + y] = iron_dark if border else iron_light if x + y < 6 else iron
        px[cx + 4, cy + 4] = iron_dark
        px[cx + 3, cy + 3] = iron_light
    for row in range(3):
        for column in range(9):
            gui_slot(px, INVENTORY[0] + column * 18 - 1, INVENTORY[1] + row * 18 - 1)
    for column in range(9):
        gui_slot(px, INVENTORY[0] + column * 18 - 1, INVENTORY[1] + 58 - 1)
    return img


def gui_slot(px, x, y):
    for j in range(18):
        for i in range(18):
            if i == 17 or j == 17:
                c = (246, 238, 222, 255)
            elif i == 0 or j == 0:
                c = (60, 50, 40, 255)
            else:
                c = (140, 128, 110, 255)
            px[x + i, y + j] = c


def gui_big_slot(px, x, y):
    """A 26x26 bronze-rimmed slot around an 18x18 slot at (x + 4, y + 4)."""
    for j in range(26):
        for i in range(26):
            ring = min(i, j, 25 - i, 25 - j)
            if ring == 0:
                c = (60, 38, 16, 255)
            elif ring <= 2:
                c = (214, 158, 84, 255) if i + j < 26 else (150, 98, 42, 255)
            elif ring == 3:
                c = (88, 58, 26, 255)
            else:
                c = (126, 112, 94, 255)
            px[x + i, y + j] = c
    gui_slot(px, x + 4, y + 4)


def gui_inset(px, x0, y0, x1, y1, fill=(44, 37, 31, 255)):
    for y in range(y0, y1):
        for x in range(x0, x1):
            if y == y0 or x == x0:
                px[x, y] = (90, 76, 62, 255)
            elif y == y1 - 1 or x == x1 - 1:
                px[x, y] = (240, 230, 212, 255)
            else:
                px[x, y] = fill


def gui_line(px, a, b, color, width=1):
    (x0, y0), (x1, y1) = a, b
    steps = int(max(abs(x1 - x0), abs(y1 - y0))) * 2 + 1
    for k in range(steps + 1):
        t = k / steps
        x = round(x0 + (x1 - x0) * t)
        y = round(y0 + (y1 - y0) * t)
        for dx in range(width):
            for dy in range(width):
                px[x + dx, y + dy] = color


def gui_arrow(px, x, y, length, color=(120, 108, 92, 255)):
    for i in range(length - 5):
        for dy in (-1, 0, 1):
            px[x + i, y + dy] = color
    for i in range(5):
        for dy in range(-4 + i, 5 - i):
            px[x + length - 5 + i, y + dy] = color


def generate_gui_textures():
    folder = ASSETS / "textures/gui"
    folder.mkdir(parents=True, exist_ok=True)

    # Parts table, Piezas tab: pattern grid, template + material -> part.
    parts = gui_panel(22)
    px = parts.load()
    # Three rows of twelve patterns, sixteen to a cell: thirty-six parts since the staff and the tome.
    gui_inset(px, 5, 22, 201, 74, (150, 138, 118, 255))
    gui_slot(px, 21, 84)
    gui_slot(px, 63, 84)
    gui_big_slot(px, 131, 80)
    for i in range(5):
        px[49 + i, 93] = (110, 98, 82, 255)
        px[51, 91 + i] = (110, 98, 82, 255)
    gui_arrow(px, 90, 93, 34)
    parts.save(folder / "mesa_de_piezas.png")

    # Parts table, Desarmar tab: the item slot and an arrow toward the returned parts.
    salvage = gui_panel(22)
    px = salvage.load()
    gui_big_slot(px, 15, 40)
    gui_arrow(px, 44, 53, 16)
    salvage.save(folder / "mesa_de_piezas_desarmar.png")

    # Forge table: five slots in an engraved star around the gear in the center, and the info panel.
    forge = gui_panel(15)
    px = forge.load()
    cx, cy = STAR_CENTER[0] + 8, STAR_CENTER[1] + 8
    centers = [(x + 8, y + 8) for x, y in STAR_POINTS]
    engrave, light = (150, 138, 118, 255), (228, 220, 202, 255)
    for i in range(5):
        a, b = centers[i], centers[(i + 2) % 5]
        gui_line(px, (a[0] + 1, a[1] + 1), (b[0] + 1, b[1] + 1), light, 2)
        gui_line(px, a, b, engrave, 2)
    import math
    for k in range(360):
        for radius, color in ((41, light), (40, engrave)):
            x = round(cx + radius * math.cos(math.radians(k)))
            y = round(cy + radius * math.sin(math.radians(k)))
            if 4 < x < GUI_W - 5 and 16 < y < 104:
                px[x, y] = color
    for x, y in STAR_POINTS:
        gui_slot(px, x - 1, y - 1)
    gui_big_slot(px, STAR_CENTER[0] - 5, STAR_CENTER[1] - 5)
    gui_inset(px, 110, 18, 201, 85)
    forge.save(folder / "mesa_de_forja.png")

    # Forge table, Tecnicas tab: the bare panel, since the three rows are drawn by the screen.
    gui_panel(15).save(folder / "mesa_de_forja_tecnicas.png")


PLANK_TEMPLATE_BASE = (196, 150, 96)


def generate_orb_textures():
    """A shaded grayscale sphere the upgrade color tints, and an untinted glint on top."""
    body = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    glint = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy, radius = 7.5, 8.0, 5.6
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d = (dx * dx + dy * dy) ** 0.5
            if d > radius:
                continue
            if d > radius - 1.0:
                shade = 70
            else:
                # Light from the upper left.
                light = 1.0 - ((dx + 2.2) ** 2 + (dy + 2.2) ** 2) ** 0.5 / (radius * 1.9)
                shade = int(120 + 135 * max(0.0, min(1.0, light)))
            body.putpixel((x, y), (shade, shade, shade, 255))
    for x, y in ((5, 5), (6, 5), (5, 6)):
        glint.putpixel((x, y), (255, 255, 255, 255))
    glint.putpixel((10, 11), (255, 255, 255, 160))
    folder = ASSETS / "textures/item"
    body.save(folder / "orbe_de_mejora.png")
    glint.save(folder / "orbe_de_mejora_brillo.png")


# The portable anvil, in profile, which is the only angle an anvil is recognisable from at sixteen
# pixels: a horn to the left, a face you could hammer on, a waist, and a foot that splays out.
#
#   o outline   L lit   M steel   D shadow
PORTABLE_ANVIL = [
    "................",
    "................",
    "..oooooooooooo..",
    "..oLLLLLLLLLLo..",
    "ooMMMMMMMMMMMMo.",
    "oMMMMMMMMMMMMMo.",
    "oooDDDDDDDDDDo..",
    "....oMMMMMMo....",
    ".....oMMMMo.....",
    ".....oMMMMo.....",
    ".....oMMMMo.....",
    "....oMMMMMMo....",
    "...oLLLLLLLLo...",
    "..oMMMMMMMMMMo..",
    "..oDDDDDDDDDDo..",
    "..oooooooooooo..",
]


def generate_portable_anvil_texture():
    """A little anvil with a strap round its waist, seen from the side so it reads as an anvil.

    The old one was drawn straight on and came out looking like a shelf with a plank across it. An
    anvil is a silhouette, not a texture: horn, face, waist, foot. Once those four are in profile the
    thing is unmistakable at any size, and the strap is what makes it a thing you carry.
    """
    colors = {
        "o": (28, 26, 30, 255),
        "L": (168, 170, 180, 255),
        "M": (104, 106, 116, 255),
        "D": (62, 64, 72, 255),
    }
    anvil = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(PORTABLE_ANVIL):
        for x, char in enumerate(row):
            if char in colors:
                anvil.putpixel((x, y), colors[char])
    # The strap, wrapped round the waist and buckled: wider than the waist, so it reads as wrapped
    # round the far side rather than painted on the front.
    for x in range(3, 13):
        anvil.putpixel((x, 8), (162, 106, 54, 255))
        anvil.putpixel((x, 9), (112, 70, 34, 255))
    anvil.putpixel((9, 8), (222, 198, 136, 255))
    anvil.putpixel((9, 9), (150, 122, 66, 255))
    anvil.save(ASSETS / "textures/item/yunque_portatil.png")


def generate_hollow_plate_texture():
    """A curved piece of dark plate with nothing behind it: the light goes straight through the hole."""
    rows = [
        "................",
        "....oooooooo....",
        "...oLLLLLLLLo...",
        "..oLMMMMMMMMLo..",
        "..oLMDDDDDDMLo..",
        "..oLMD....DMLo..",
        "..oLMD....DMLo..",
        "..oLMD....DMLo..",
        "..oLMD....DMLo..",
        "..oLMDD..DDMLo..",
        "..oLMMDDDDMMLo..",
        "...oLMMMMMMLo...",
        "....oLMMMMLo....",
        ".....oLMMLo.....",
        "......oooo......",
        "................",
    ]
    colors = {
        "o": (32, 34, 40),
        "L": (154, 164, 176),
        "M": (108, 116, 128),
        "D": (62, 68, 78),
    }
    plate = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char in colors:
                plate.putpixel((x, y), colors[char] + (255,))
    # The cold light that was inside the suit, still caught in the plate.
    for x, y in ((7, 6), (8, 6), (7, 7), (8, 7)):
        plate.putpixel((x, y), (120, 220, 226, 90))
    plate.save(ASSETS / "textures/item/placa_hueca.png")


def generate_master_hammer_texture():
    """The Fallen Smith's own hammer: the mod's hammer head in dark iron, with the fire still in it."""
    hammer = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = hammer.load()
    for (x, y), (label, lum) in sledgehammer().items():
        if not (0 <= x < 16 and 0 <= y < 16):
            continue
        if label == 1:
            # The handle, charred.
            shade = int(40 + lum * 0.25)
            px[x, y] = (shade + 20, shade, shade - 6 if shade > 6 else 0, 255)
        else:
            # The head, dark iron with an ember sheen along the lit edge.
            shade = int(30 + lum * 0.45)
            warm = lum > 150
            px[x, y] = (shade + (70 if warm else 0), shade + (20 if warm else 0), shade, 255)
    for x, y in ((6, 5), (7, 4), (8, 5), (7, 6)):
        px[x, y] = (255, 170, 70, 255)
    hammer.save(ASSETS / "textures/item/martillo_del_maestro.png")


# Everything of the mod's that is stone or metal and says so with requiresCorrectToolForDrops.
#
# The tag was written in two places, one list each, and the file is whatever was written last: the three
# benches. Every crucible, tank, channel, spout, casting box and casting table, the wisp lantern, the dead
# forge and the smith's own anvil asked for the right tool and were in no tool's list, so a pickaxe took
# them down slowly and they dropped NOTHING. A foundry is a great deal of alloy to lose to a misclick.
# One list, here, and check_tool_tags() makes sure no block is left out of every list again.
PICKAXE_BLOCKS = [
    "forja:mesa_de_forja", "forja:mesa_de_forja_mayor", "forja:mesa_de_piezas", "forja:mesa_de_extraccion",
    "forja:fragua_apagada", "forja:yunque_del_herrero", "forja:farol_de_pavesa",
    "forja:crisol_de_barro", "forja:crisol_de_hierro", "forja:crisol_de_obsidiana",
    "forja:cuba_de_colada", "forja:cano_de_colada",
    "forja:conducto_de_colada", "forja:conducto_de_acero", "forja:conducto_de_damasco",
    "forja:caja_de_moldeo", "forja:caja_de_moldeo_de_acero", "forja:caja_de_moldeo_de_damasco",
    "forja:mesa_de_losa", "forja:mesa_de_brasa", "forja:mesa_de_almas",
]


def check_tool_tags():
    """Blocks that are in no tool's tag: they break slowly, and drop nothing if they ask for the right tool."""
    import json as _json

    tagged = set()
    for name in ("pickaxe", "axe", "shovel", "hoe"):
        path = RES / f"data/minecraft/tags/block/mineable/{name}.json"
        if path.exists():
            tagged.update(_json.loads(path.read_text(encoding="utf-8"))["values"])
    states = sorted(p.stem for p in (ASSETS / "blockstates").glob("*.json"))
    missing = [f"forja:{state}" for state in states if f"forja:{state}" not in tagged]
    if missing:
        print("BLOCKS IN NO MINEABLE TAG:", *missing)
    return missing


def generate_cabinet_models():
    write_json(ASSETS / "models/block/armario_de_piezas.json", {
        "parent": "minecraft:block/cube",
        "textures": {
            "particle": "forja:block/armario_de_piezas_front",
            "down": "forja:block/armario_de_piezas_top",
            "up": "forja:block/armario_de_piezas_top",
            "north": "forja:block/armario_de_piezas_front",
            "south": "forja:block/armario_de_piezas_front",
            "east": "forja:block/armario_de_piezas_side",
            "west": "forja:block/armario_de_piezas_side",
        },
    })
    write_json(ASSETS / "blockstates/armario_de_piezas.json",
               {"variants": {"": {"model": "forja:block/armario_de_piezas"}}})
    write_json(ASSETS / "models/item/armario_de_piezas.json", {"parent": "forja:block/armario_de_piezas"})
    write_json(ASSETS / "items/armario_de_piezas.json",
               {"model": {"type": "minecraft:model", "model": "forja:block/armario_de_piezas"}})
    write_json(DATA / "loot_table/blocks/armario_de_piezas.json", {
        "type": "minecraft:block",
        "random_sequence": "forja:blocks/armario_de_piezas",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "forja:armario_de_piezas"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    write_json(DATA / "recipe/armario_de_piezas.json", {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["TTT", "ICI", "TTT"],
        "key": {"T": "#minecraft:planks", "I": "minecraft:iron_ingot", "C": "minecraft:chest"},
        "result": {"id": "forja:armario_de_piezas"},
    })


def generate_seal_textures():
    """A seal: a ring of runes drawn in light, inside a contour that stays gold.

    Not a wax blob and not a sphere in a ring: a **circle**, the kind you draw on the floor. Rings
    inside rings, marks set round them at even spacing, and a star in the middle. The circle takes the
    gift's colour and the contour around it does not, so a row of seals reads as the same rite in
    different colours rather than as a row of identical coins.
    """
    import math

    rune = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    edge = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx = cy = 7.5

    def ring(image, radius, colour, width=None):
        """One circle of pixels, walked round by angle rather than sieved by distance.

        Testing every pixel against a radius sounds like the same thing and is not: at sixteen pixels
        it leaves flat runs four wide at the poles and the ring reads as a rounded square. Stepping
        round the circumference puts down exactly one pixel per step and comes out round.
        """
        for step in range(96):
            angle = step * math.tau / 96
            x = int(round(cx + math.cos(angle) * radius))
            y = int(round(cy + math.sin(angle) * radius))
            if 0 <= x < 16 and 0 <= y < 16:
                image.putpixel((x, y), colour)

    def spokes(image, radius, count, colour, length=1.0):
        for i in range(count):
            angle = i * 2 * math.pi / count
            for step in range(2):
                r = radius + step * length
                x = int(round(cx + math.cos(angle) * r))
                y = int(round(cy + math.sin(angle) * r))
                if 0 <= x < 16 and 0 <= y < 16:
                    image.putpixel((x, y), colour)

    # The field the circle is drawn on: nearly black, so every line on it reads as light rather than
    # as paint. Contrast is the whole game at sixteen pixels — the first version had three rings and a
    # star on a mid-grey field and the lot turned to mush.
    for y in range(16):
        for x in range(16):
            if ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 <= 6.2:
                rune.putpixel((x, y), (26, 26, 26, 255))
    # One outer ring, one inner ring, and eight marks set between them. Nothing else fits.
    # One ring and one ring of marks, and that is the lot. Four concentric bands in a disc twelve
    # pixels across have nothing to be concentric in: they touch, and the whole thing turns pale.
    ring(rune, 5.6, (240, 240, 240, 255))
    spokes(rune, 3.8, 8, (214, 214, 214, 255), length=0.0)
    # And the star at the middle: four arms off a lit core, which is the one shape that survives
    # being drawn three pixels across.
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        rune.putpixel((x, y), (255, 255, 255, 255))
    for (x, y) in ((7, 6), (8, 6), (7, 9), (8, 9), (6, 7), (6, 8), (9, 7), (9, 8)):
        rune.putpixel((x, y), (170, 170, 170, 255))

    # And the contour: a band of gold round the whole thing, lit from the top left.
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 6.2 < d <= 7.3:
                shade = 236 if (x - cx) + (y - cy) < -3 else 184 if (x - cx) + (y - cy) < 3 else 122
                edge.putpixel((x, y), (shade, int(shade * 0.78), int(shade * 0.32), 255))
    rune.save(ASSETS / "textures/item/sello.png")
    edge.save(ASSETS / "textures/item/sello_marco.png")


ALLOY_COLORS = {
    # Tempered: copper and what you can mix into it.
    "bronce": 0xC98A3C,
    "laton": 0xE0B94A,
    "peltre": 0xA3A79A,
    # Hot: iron worked properly, and the three the foundry adds on the way up.
    "acero": 0xBFC4CC,
    "electro": 0xF2E29A,
    "acero_refractario": 0xD8C3A0,
    "cinerio": 0xC4562A,
    "voltaico": 0x6FD6E8,
    # Molten: steel taken further than a hearth will take it.
    "damasco": 0xA0A6B4,
    "acero_estelar": 0xC2BFE3,
    "obsidiacero": 0x5B4A77,
    "almacero": 0x77CCD3,
    "vidriacero": 0xA8D8E0,
    # White heat: the obsidian crucible or nothing.
    "solacero": 0xFFC341,
    "lunacero": 0x5A6CC0,
    "acero_vivo": 0xE8231A,
}

# Two alloys are not one colour at all: they run one into another across the bar, left to right.
ALLOY_SIDEWAYS = {
    "almacero": (0x8FB3C4, 0x5FE5E2),        # the steel it is, running into soul blue
    "acero_estelar": (0xDCEAFF, 0xA894C8),   # the pale sky it is, running into silvered purple
}

# And one runs with the light instead: dark end purple, lit end blue.
ALLOY_RAMPS = {
    "lunacero": (0x2A1B4A, 0x6E86D6),
}

# What each metal's surface says it is. Bronze and brass wear nothing on purpose: they are the first
# two you ever make and they read fine by colour alone, so a mark on them would mean nothing.
ALLOY_MARKS = {
    "peltre": "pits",
    "acero": "fold",
    "electro": "patches",
    "damasco": "damask",
    "acero_estelar": "stars",
    "obsidiacero": "facets",
    "acero_refractario": "grain",
    "cinerio": "crack",
    "voltaico": "bolt",
    "vidriacero": "pane",
    "solacero": "sun",
    "lunacero": "craters",
    "acero_vivo": "vein",
}


def generate_alloy_textures():
    """One ingot per alloy: Minecraft's own ingot, in that metal's colour, with that metal's surface.

    The shape is the vanilla iron ingot re-tinted, which is why these sit comfortably in a chest next
    to a real one. What tells them apart is the **mark** on the lit top face — sixteen grey bars would
    otherwise be sixteen grey bars, and steel in particular used to be indistinguishable from iron.

    Two of them do not take a single colour at all: soul steel and star steel run one colour into
    another **across** the bar, left to right, which is its own kind of mark.

    Every mark obeys one rule, and it is the rule that made them read at all: a dark pixel always has
    a light one beside it. Without that the mark sinks into the face of the bar and disappears.
    """
    ingot = vanilla("item/iron_ingot.png")
    values = [luminance(ingot.getpixel((x, y))) for y in range(16) for x in range(16)
              if ingot.getpixel((x, y))[3]]
    low = min(values)
    span = max(1, max(values) - low)
    # Which of the five tones each pixel of the bar sits on, and where the bar is at all.
    grid = {(x, y): (luminance(ingot.getpixel((x, y))) - low) * 4 // span
            for y in range(16) for x in range(16) if ingot.getpixel((x, y))[3]}
    # The lit top face: the only place a mark is allowed, so the silhouette is never touched.
    face = {point for point, step in grid.items() if step >= 3}
    factors = (0.30, 0.52, 0.76, 1.0, 1.30)

    def ramp_of(colour):
        base = ((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF)
        return [tuple(min(255, int(c * f)) for c in base) for f in factors]

    def ramp_between(dark, light):
        """A ramp that travels between two colours instead of dimming one, for moon steel."""
        a = ((dark >> 16) & 0xFF, (dark >> 8) & 0xFF, dark & 0xFF)
        b = ((light >> 16) & 0xFF, (light >> 8) & 0xFF, light & 0xFF)
        return [tuple(int(a[c] + (b[c] - a[c]) * (i / 4)) for c in range(3)) for i in range(5)]

    xs = [x for (x, _) in grid]
    x0, x1 = min(xs), max(xs)

    for name in ALLOY_COLORS:
        sideways = ALLOY_SIDEWAYS.get(name)
        if sideways:
            left, right = sideways
            a = ((left >> 16) & 0xFF, (left >> 8) & 0xFF, left & 0xFF)
            b = ((right >> 16) & 0xFF, (right >> 8) & 0xFF, right & 0xFF)
            bar = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            for (x, y), step in grid.items():
                t = (x - x0) / max(1, x1 - x0)
                mix = tuple(a[c] + (b[c] - a[c]) * t for c in range(3))
                bar.putpixel((x, y), tuple(min(255, int(v * factors[step])) for v in mix) + (255,))
            ramp = ramp_of(ALLOY_COLORS[name])
        else:
            ramp = ramp_between(*ALLOY_RAMPS[name]) if name in ALLOY_RAMPS else ramp_of(ALLOY_COLORS[name])
            bar = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            for (x, y), step in grid.items():
                bar.putpixel((x, y), ramp[step] + (255,))

        def paint(x, y, step, tint=None):
            if (x, y) in face:
                bar.putpixel((x, y), (tint or ramp[max(0, min(4, step))]) + (255,))

        mark = ALLOY_MARKS.get(name)
        if mark == "fold":
            # Steel: one seam along the worked bar, and the dimples the hammer left above it.
            for x in range(4, 13):
                paint(x, 8, 0)
                paint(x, 7, 4)
            for x in (5, 8, 11):
                paint(x, 6, 1)
                paint(x + 1, 6, 4)
        elif mark == "pits":
            for (x, y) in ((5, 6), (9, 7), (12, 6), (7, 9), (11, 9)):
                paint(x, y, 0)
                paint(x + 1, y, 1)
                paint(x, y - 1, 4)
        elif mark == "patches":
            # Electrum: two metals that never quite mixed.
            for (x, y) in face:
                if (x // 2 + y // 2) % 2 == 0:
                    paint(x, y, 4)
                elif (x + y) % 3 == 0:
                    paint(x, y, 1)
        elif mark == "damask":
            for layer, row in enumerate((6, 8, 10)):
                for x in range(2, 15):
                    y = row + (1 if ((x + layer * 2) // 2) % 2 else 0)
                    paint(x, y, 0)
                    paint(x, y - 1, 4)
        elif mark == "stars":
            for (cx, cy) in ((5, 7), (9, 6), (12, 8)):
                paint(cx, cy, 4, (255, 255, 255))
                for (dx, dy) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    paint(cx + dx, cy + dy, 4)
                paint(cx + 1, cy + 1, 0)
        elif mark == "facets":
            for (x, y) in ((4, 7), (6, 9), (9, 6), (11, 8)):
                for i in range(3):
                    paint(x + i, y - i, 0)
                    paint(x + i, y - i - 1, 4)
        elif mark == "grain":
            for (x, y) in face:
                if (x * 7 + y * 3) % 4 == 0:
                    paint(x, y, 1)
                elif (x * 5 + y * 11) % 7 == 0:
                    paint(x, y, 4)
        elif mark == "crack":
            # Cinereous: a crack across the bar with the fire still in it.
            for (x, y) in ((3, 8), (4, 8), (5, 7), (6, 7), (7, 8), (8, 8), (9, 7), (10, 7), (11, 8), (12, 8)):
                paint(x, y, 4, (255, 170, 60))
                paint(x, y - 1, 4, (255, 226, 150))
                paint(x, y + 1, 0)
            for (x, y) in ((5, 9), (9, 9)):
                paint(x, y, 4, (255, 120, 30))
        elif mark == "bolt":
            for (x, y) in ((4, 8), (5, 7), (6, 8), (7, 6), (8, 7), (9, 6), (10, 7), (11, 6), (12, 7)):
                paint(x, y, 4, (250, 255, 255))
                paint(x, y + 1, 0)
            for (x, y) in ((3, 7), (13, 8)):
                paint(x, y, 4, (190, 240, 255))
        elif mark == "pane":
            # Glass steel: a band you can see through.
            for x in range(3, 14):
                paint(x, 7, 4, (232, 250, 255))
                paint(x, 8, 4, (232, 250, 255))
                paint(x, 9, 2)
            for x in range(4, 13, 3):
                paint(x, 7, 4, (255, 255, 255))
        elif mark == "sun":
            for (x, y) in ((8, 7), (7, 7), (9, 7), (8, 6), (8, 8), (6, 6), (10, 8), (6, 8), (10, 6)):
                paint(x, y, 4, (255, 250, 210))
            for (x, y) in ((7, 8), (9, 6)):
                paint(x, y, 0)
        elif mark == "craters":
            for (cx, cy) in ((5, 7), (9, 6), (11, 9)):
                for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    paint(cx + dx, cy + dy, 0)
                paint(cx, cy - 1, 4)
                paint(cx + 1, cy - 1, 4)
                paint(cx + 2, cy + 1, 1)
        elif mark == "vein":
            for x in range(3, 14):
                y = 7 + (1 if x % 4 in (1, 2) else 0)
                paint(x, y, 4, (255, 90, 70))
                paint(x, y - 1, 4, (255, 180, 150))
                paint(x, y + 1, 0)
        bar.save(ASSETS / f"textures/item/{name}.png")


def generate_star_iron_texture():
    """Star iron: a lump of pale metal with the light still in it."""
    rows = [
        "................",
        "......ooo.......",
        ".....oLLLo......",
        "....oLLWLLo.....",
        "...oLWWWWLLo....",
        "..oLLWWWWWLLo...",
        "..oLLWWWWWLLo...",
        ".oLLMWWWWWMLLo..",
        ".oLMMMWWWMMMLo..",
        ".oMMMMMWMMMMMo..",
        "..oMMMMMMMMMo...",
        "..oDMMMMMMMDo...",
        "...oDDMMMDDo....",
        "....oDDDDDo.....",
        ".....ooooo......",
        "................",
    ]
    colors = {"o": (58, 74, 96), "W": (250, 252, 255), "L": (208, 230, 250), "M": (166, 196, 228), "D": (116, 146, 182)}
    ore = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char != ".":
                ore.putpixel((x, y), colors[char] + (255,))
    ore.save(ASSETS / "textures/item/hierro_estelar.png")


def generate_jar_textures():
    """The essence jar: a tall fluted jar with a lid, drawn from the one Andy sent.

    What makes that jar that jar is three things and none of them is the outline: it is **taller than
    it is wide**, the glass is **fluted** — vertical ribs all the way down — and the lid is a pale cap
    that sits on top rather than a cork pushed into a neck. No handle.

    Two sprites, because the jar has to show what it caught: the glass as drawn, and the contents in
    grey and tinted at runtime by the colour of the night in it. A shelf of them reads as the nights
    you were outside for rather than as nine identical bottles.
    """
    folder = ASSETS / "textures/item/jarra"
    folder.mkdir(parents=True, exist_ok=True)
    glass = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    OUTLINE = (52, 62, 74)
    LID = (226, 232, 236)
    LID_D = (168, 178, 186)
    RIB_LIGHT = (232, 244, 250)
    RIB = (198, 222, 234)

    # The lid: a cap wider than the mouth, lit on top.
    for x in range(4, 12):
        glass.putpixel((x, 1), OUTLINE + (255,))
        glass.putpixel((x, 2), LID + (255,))
        glass.putpixel((x, 3), LID_D + (255,))
    for y in (1, 2, 3):
        glass.putpixel((3, y), OUTLINE + (255,))
        glass.putpixel((12, y), OUTLINE + (255,))
    glass.putpixel((3, 1), (0, 0, 0, 0))
    glass.putpixel((12, 1), (0, 0, 0, 0))

    # The body: straight sides, a flat base, and flutes down the whole of it.
    body = [(x, y) for y in range(4, 15) for x in range(3, 13)]
    for (x, y) in body:
        edge = x in (3, 12) or y == 14 or y == 4
        if edge:
            glass.putpixel((x, y), OUTLINE + (255,))
        else:
            # The flutes: every other column catches the light, which is the whole look of that jar.
            glass.putpixel((x, y), (RIB_LIGHT if x % 2 else RIB) + (100 if x % 2 else 78,))
    # The two ribs nearest the near edge read brightest, the way a round fluted body does.
    for y in range(5, 14):
        glass.putpixel((4, y), (246, 252, 255, 190))
        glass.putpixel((11, y), (150, 176, 190, 150))
    glass.save(folder / "vidrio.png")

    # What is in the jar: it fills the body up to the shoulder and has a surface.
    rng = __import__("random").Random(556611)
    inside = [(x, y) for y in range(6, 14) for x in range(4, 12)]
    essence = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y) in inside:
        depth = (y - 6) / 7.0
        level = 238 - int(78 * depth) + rng.randint(-8, 8)
        # The flutes show through what is in it, so the contents are striped the same way.
        if x % 2 == 0:
            level = max(0, level - 22)
        essence.putpixel((x, y), (level, level, level, 235))
    for x in range(4, 12):
        essence.putpixel((x, 6), (255, 255, 255, 250))
    essence.save(folder / "esencia.png")

    for name in ("vidrio", "esencia"):
        write_json(ASSETS / f"models/item/jarra/{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"forja:item/jarra/{name}"}})
    # Empty jars draw nothing inside; a full one names itself "lleno" and carries its colour.
    write_json(ASSETS / "items/jarra.json", {
        "model": {
            "type": "minecraft:composite",
            "models": [
                {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                 "cases": [{"when": "lleno", "model": {
                     "type": "minecraft:model", "model": "forja:item/jarra/esencia",
                     "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 0xFFFFFF}],
                 }}],
                 "fallback": {"type": "minecraft:empty"}},
                {"type": "minecraft:model", "model": "forja:item/jarra/vidrio"},
            ],
        },
    })


def generate_talisman_textures():
    """A talisman: a sphere with something shut inside it, held in a cage of gold bands.

    Two sprites. The stone is drawn in grey and tinted by its material, and it is not a solid marble:
    it is lit **from inside**, with a core that blows out to white and veins running off it toward the
    surface. The gold is a separate sprite that never takes the tint — two meridians and a waist band
    wrapped round the ball, set wide enough apart that the light gets out between them, which is what
    makes it read as a thing holding power rather than as a painted bead.
    """
    import math

    stone = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cage = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy, radius = 7.5, 8.6, 5.6

    def inside(x, y, slack=0.0):
        return ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 <= radius + slack

    # ---- the stone: a ball, then the light shut inside it.
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d > radius:
                continue
            # The surface: how much of it faces the light, from up and to the left.
            lit = 1.0 - (((x - cx + 2.4) ** 2 + (y - cy + 2.4) ** 2) ** 0.5) / (radius * 2.1)
            value = 58 + 128 * max(0.0, min(1.0, lit))
            # The light inside it. It is centred a little above the middle so it agrees with the
            # surface light instead of fighting it, and it blows out to white at the heart.
            glow = max(0.0, 1.0 - (((x - cx + 0.4) ** 2 + ((y - cy + 0.8) * 1.05) ** 2) ** 0.5) / 3.6)
            value += 235 * glow ** 1.9
            # The rim: the ball is lifted off its own outline by a line of bounced light.
            if d > radius - 1.0 and (x - cx) + (y - cy) > 1.5:
                value = max(value, 132)
            if d > radius - 0.5:
                value *= 0.55
            stone.putpixel((x, y), (min(255, int(value)),) * 3 + (255,))
    # The veins: what is inside is under pressure, and it shows where it runs out to the surface.
    for i in range(6):
        angle = i * math.tau / 6 + 0.4
        for step in range(7):
            r = 1.6 + step * 0.62
            x = int(round(cx + math.cos(angle) * r))
            y = int(round(cy + math.sin(angle) * r * 1.02))
            if 0 <= x < 16 and 0 <= y < 16 and stone.getpixel((x, y))[3] and inside(x, y, -0.9):
                value = stone.getpixel((x, y))[0]
                stone.putpixel((x, y), (min(255, value + 66),) * 3 + (255,))

    # ---- the cage: two meridians and a waist band, wrapped round the ball.
    GOLD = (226, 186, 96)
    GOLD_LIT = (250, 224, 150)
    GOLD_DARK = (138, 106, 44)

    def band(points):
        for (x, y) in points:
            if not (0 <= x < 16 and 0 <= y < 16) or not inside(x, y, -0.2):
                continue
            # The gold is lit by the same lamp as everything else in the mod.
            tone = GOLD_LIT if (x - cx) + (y - cy) < -3 else GOLD if (x - cx) + (y - cy) < 3 else GOLD_DARK
            cage.putpixel((x, y), tone + (255,))

    # Two rings round the ball, crossing: one round the waist and one over the top. Drawn as whole
    # ellipses — front arc and back arc both — because that is what tells you the ring goes *round*
    # the thing rather than sitting in front of it. The first attempt drew only the near halves and
    # the gold read as a basket the ball was sitting in.
    def ellipse(ax, ay, near_is_bottom):
        for step in range(96):
            t = step * math.tau / 96
            x = int(round(cx + ax * math.cos(t)))
            y = int(round(cy + ay * math.sin(t)))
            if not (0 <= x < 16 and 0 <= y < 16) or not inside(x, y, -0.2):
                continue
            behind = (math.sin(t) < 0) if near_is_bottom else (math.cos(t) > 0)
            if behind:
                # The far side passes behind the stone: it shows through, but only just.
                if cage.getpixel((x, y))[3] == 0:
                    cage.putpixel((x, y), GOLD_DARK + (205,))
            else:
                tone = GOLD_LIT if (x - cx) + (y - cy) < -2 else GOLD
                cage.putpixel((x, y), tone + (255,))

    # One ring round the waist, and two meridians run **off centre** on either side of it.
    #
    # A meridian through the middle of the ball is the obvious thing to draw and it is wrong here: it
    # lands exactly on the heart, and the light that is supposed to be shut inside ends up behind a
    # gold bar. Set the two of them wide and the cage still closes at the poles while the middle of
    # the ball stays open, which is where you want to be looking.
    ellipse(radius - 0.4, 1.3, near_is_bottom=True)
    for offset in (-3.3, 3.3):
        rib = []
        for y in range(16):
            span = 1.0 - ((y - cy) / radius) ** 2
            if span <= 0:
                continue
            rib.append((int(round(cx + offset * span ** 0.5)), y))
        band(rib)

    # The cap it hangs from, and the cord.
    for x in range(6, 10):
        cage.putpixel((x, 3), GOLD + (255,))
        cage.putpixel((x, 4), GOLD_DARK + (255,))
    cage.putpixel((6, 2), GOLD + (255,))
    cage.putpixel((9, 2), GOLD_DARK + (255,))
    for y in range(0, 3):
        cage.putpixel((7, y), (118, 88, 52, 255))
        cage.putpixel((8, y), (86, 62, 36, 255))
    stone.save(ASSETS / "textures/item/talisman.png")
    cage.save(ASSETS / "textures/item/talisman_marco.png")


def generate_belt_texture():
    """The tool belt: a leather strap with an iron buckle and four loops."""
    colors = {"o": (44, 26, 12), "L": (178, 116, 64), "M": (146, 90, 46), "D": (98, 58, 28), "S": (216, 218, 226)}
    rows = [
        "................",
        "................",
        "..oooooooooooo..",
        ".oLLLLLLLLLLLLo.",
        ".oLMMMMMMMMMMLo.",
        ".oMMSSMMMMSSMMo.",
        ".oMMSSMMMMSSMMo.",
        ".oMMMMMMMMMMMMo.",
        ".oDDMMSSMMSSMDo.",
        ".oDDMMSSMMSSMDo.",
        ".oDDDDDDDDDDDDo.",
        "..oooooooooooo..",
        "................",
        "................",
        "................",
        "................",
    ]
    belt = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char != ".":
                belt.putpixel((x, y), colors[char] + (255,))
    belt.save(ASSETS / "textures/item/cinturon.png")


def generate_temper_ingot_texture():
    """The tempering bar: a bar that has not decided what metal it is, and is still hot at one end.

    It is the vanilla ingot again, which is what every other bar in the mod is, but drained of colour
    and left glowing at the right-hand end. That is the whole item in one picture: it is not any metal
    yet, and it is going back in the fire the moment it touches yours.
    """
    rng = __import__("random").Random(880114)
    ingot = vanilla("item/iron_ingot.png")
    values = [luminance(ingot.getpixel((x, y))) for y in range(16) for x in range(16)
              if ingot.getpixel((x, y))[3]]
    low = min(values)
    span = max(1, max(values) - low)
    bar = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # The five tones of a pale, unassayed metal: grey with the warmth only just showing in it.
    ramp = [(58, 54, 52), (104, 98, 94), (148, 142, 136), (192, 186, 178), (228, 222, 212)]
    # And the fire it came out of, running back up the bar from the right.
    heat = [(150, 52, 22), (208, 86, 24), (244, 140, 34), (255, 196, 96), (255, 244, 208)]
    for y in range(16):
        for x in range(16):
            if not ingot.getpixel((x, y))[3]:
                continue
            step = (luminance(ingot.getpixel((x, y))) - low) * 4 // span
            # How hot this column is: nothing on the left, white on the right, with a ragged front.
            edge = (x - 8.5) / 5.0 + (rng.random() - 0.5) * 0.22
            if edge <= 0.0:
                bar.putpixel((x, y), ramp[step] + (255,))
            else:
                glow = min(1.0, edge)
                cold = ramp[step]
                hot = heat[step]
                bar.putpixel((x, y), tuple(int(c + (h - c) * glow) for c, h in zip(cold, hot)) + (255,))
    # One hammer mark on the lit face, so it reads as something worked rather than something cast.
    for (x, y) in ((5, 6), (6, 6), (5, 7)):
        bar.putpixel((x, y), (36, 34, 32, 255))
    bar.putpixel((6, 7), (236, 230, 220, 255))
    bar.save(ASSETS / "textures/item/lingote_de_temple.png")


def generate_villager_textures():
    """The Forjador wears the weaponsmith's apron dyed charcoal with copper-bright edges."""
    for kind in ("villager", "zombie_villager"):
        base = Image.open(io.BytesIO(jar_read(f"assets/minecraft/textures/entity/{kind}/profession/weaponsmith.png"))).convert("RGBA")
        out = base.copy()
        for y in range(base.height):
            for x in range(base.width):
                r, g, b, a = base.getpixel((x, y))
                if a == 0 or max(r, g, b) - min(r, g, b) < 24:
                    continue
                t = min(1.0, luminance((r, g, b, a)) / 150.0) ** 1.6
                dark, bright = (38, 32, 34), (214, 118, 52)
                out.putpixel((x, y), tuple(int(dark[i] + (bright[i] - dark[i]) * t) for i in range(3)) + (a,))
        path = ASSETS / f"textures/entity/{kind}/profession/forjador.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        out.save(path)


def generate_crack_texture():
    """The break drawn over forged gear that has given out.

    The old one was a thin diagonal scratch with three red pixels beside it, and at sixteen pixels it
    read as damage to the *sprite* rather than to the thing. What makes a crack read as a crack is the
    pair: a black core with a **lit edge on one side of it**, the way a split in metal shows you the
    fresh face inside.

    It is kept short and central on purpose. The overlay knows nothing about the item under it, so
    anything that wanders out to the corners lands on empty space and reads as dirt around the icon
    instead of as a break through it. Ten pixels down the middle sit on every silhouette the mod has.
    """
    crack = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    spine = [(7, 3), (8, 4), (8, 5), (7, 6), (7, 7), (8, 8), (8, 9), (9, 10), (8, 11), (8, 12)]
    # One fork, because a break that does not branch looks like a cut.
    fork = [(6, 7), (5, 8), (4, 8)]
    for x, y in spine + fork:
        crack.putpixel((x, y), (18, 14, 16, 245))
    # The fresh face inside the break: a pale pixel to the left of every other dark one.
    for i, (x, y) in enumerate(spine + fork):
        if i % 2 == 0 and x > 0 and crack.getpixel((x - 1, y))[3] == 0:
            crack.putpixel((x - 1, y), (228, 222, 214, 130))
    crack.save(ASSETS / "textures/item/grieta.png")


def generate_template_textures():
    """A wooden tablet for blank templates, and one carved outline per part to lay over it."""
    folder = ASSETS / "textures/item/plantilla"
    shutil.rmtree(folder, ignore_errors=True)
    folder.mkdir(parents=True, exist_ok=True)
    base = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(1, 15):
        for x in range(1, 15):
            corner = (x in (1, 14)) and (y in (1, 14))
            if corner:
                continue
            edge = x in (1, 14) or y in (1, 14)
            r, g, b = PLANK_TEMPLATE_BASE
            if edge:
                r, g, b = 110, 72, 40
            elif (y + x // 5) % 4 == 0:
                r, g, b = r - 18, g - 16, b - 12
            base.putpixel((x, y), (r, g, b, 255))
    for pos in ((3, 3), (12, 3), (3, 12), (12, 12)):
        base.putpixel(pos, (90, 58, 30, 255))
    base.save(folder / "base.png")
    for part, build in PART_LAYERS.items():
        shape = {p for p in build() if 2 <= p[0] <= 13 and 2 <= p[1] <= 13}
        carved = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for (x, y) in shape:
            carved.putpixel((x, y), (74, 46, 22, 255))
            below = (x + 1, y + 1)
            if below not in shape and 2 <= below[0] <= 13 and 2 <= below[1] <= 13:
                carved.putpixel(below, (236, 196, 140, 255))
        carved.save(folder / f"{part}.png")


def generate_icon():
    colors = [0x5FF0E2, 0xA3A3A3, 0xFFD83D]
    images = normalized_layers(TOOL_LAYERS["pico"](), 3)
    icon = Image.new("RGBA", (16, 16), (40, 32, 28, 255))
    for label, image in enumerate(images):
        c = colors[label]
        for y in range(16):
            for x in range(16):
                g, _, _, a = image.getpixel((x, y))
                if a:
                    icon.putpixel((x, y), ((c >> 16 & 255) * g // 255, (c >> 8 & 255) * g // 255, (c & 255) * g // 255, 255))
    icon.resize((128, 128), Image.NEAREST).save(ASSETS / "icon.png")


# ---------------------------------------------------------------- models and item definitions

def generate_models():
    for part, role in PARTS.items():
        parent = "minecraft:item/generated" if role in ("PLATE", "LINING") else "minecraft:item/handheld"
        write_json(ASSETS / f"models/item/parte/{part}.json", {"parent": parent, "textures": {"layer0": f"forja:item/parte/{part}"}})
        write_json(ASSETS / f"items/{part}.json", {
            "model": {
                "type": "minecraft:model",
                "model": f"forja:item/parte/{part}",
                "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": DEFAULT_COLORS[role]}],
            }
        })

    generate_bow_models()
    generate_crossbow_models()
    generate_rod_models()
    generate_shield_models()
    generate_spear_models()
    for type_id, slots in TYPES.items():
        if type_id in SPECIAL:
            continue
        parent = MODEL_PARENTS.get(type_id, "minecraft:item/generated" if type_id in ARMOR else "minecraft:item/handheld")
        layers = []
        for slot, part in enumerate(slots):
            if type_id == "guanteletes":
                write_json(ASSETS / f"models/item/{type_id}/{slot}.json", gauntlet_model(slot))
            else:
                layer = {"parent": parent, "textures": {"layer0": f"forja:item/{type_id}/{slot}"}}
                if type_id in GRIPS:
                    layer["display"] = GRIPS[type_id]
                write_json(ASSETS / f"models/item/{type_id}/{slot}.json", layer)
            model = {
                "type": "minecraft:model",
                "model": f"forja:item/{type_id}/{slot}",
                "tints": [{"type": "minecraft:custom_model_data", "index": slot, "default": DEFAULT_COLORS[PARTS[part]]}],
            }
            if type_id in THROWABLE and PARTS[part] == "HEAD":
                # While the head is flying the tool is on cooldown: draw it headless.
                model = {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:cooldown",
                    "entries": [{"threshold": 0.0001, "model": {"type": "minecraft:empty"}}],
                    "fallback": model,
                }
            layers.append(model)
        if type_id in FIXED_LAYERS:
            # a layer no part tints, under the rest: the tome's pages and the leather of its spine
            write_json(ASSETS / f"models/item/{type_id}/fijo.json", {"parent": parent, "textures": {"layer0": f"forja:item/{type_id}/fijo"}})
            layers.insert(0, {"type": "minecraft:model", "model": f"forja:item/{type_id}/fijo"})
        write_json(ASSETS / f"items/{type_id}.json", {"model": {"type": "minecraft:composite", "models": layers}})

    for table, planks in (("mesa_de_forja", "polished_deepslate"), ("mesa_de_forja_mayor", "polished_blackstone"),
                          ("mesa_de_piezas", "polished_deepslate")):
        write_json(ASSETS / f"items/{table}.json", {"model": {"type": "minecraft:model", "model": f"forja:block/{table}"}})
        write_json(ASSETS / f"blockstates/{table}.json", {"variants": {"": {"model": f"forja:block/{table}"}}})
        write_json(ASSETS / f"models/block/{table}.json", {
            "parent": "minecraft:block/cube",
            "textures": {
                "down": f"minecraft:block/{planks}",
                "up": f"forja:block/{table}_top",
                "north": f"forja:block/{table}_front",
                "south": f"forja:block/{table}_front",
                "east": f"forja:block/{table}_side",
                "west": f"forja:block/{table}_side",
                "particle": f"forja:block/{table}_side",
            },
        })
    write_json(ASSETS / "models/item/plantilla/base.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "forja:item/plantilla/base"}})
    cases = []
    for part in PARTS:
        write_json(ASSETS / f"models/item/plantilla/{part}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"forja:item/plantilla/{part}"}})
        cases.append({"when": part, "model": {"type": "minecraft:model", "model": f"forja:item/plantilla/{part}"}})
    write_json(ASSETS / "items/plantilla.json", {
        "model": {
            "type": "minecraft:composite",
            "models": [
                {"type": "minecraft:model", "model": "forja:item/plantilla/base"},
                {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases, "fallback": {"type": "minecraft:empty"}},
            ],
        }
    })
    for simple in ("hierro_estelar", "corazon_de_forja", "yunque_portatil", "placa_hueca",
                   "martillo_del_maestro", "ascua", "escoria",
                   *[f"huevo_{mob}" for mob in SPAWN_EGGS], *ALLOY_COLORS):
        write_json(ASSETS / f"models/item/{simple}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"forja:item/{simple}"}})
        write_json(ASSETS / f"items/{simple}.json", {"model": {"type": "minecraft:model", "model": f"forja:item/{simple}"}})
    write_json(ASSETS / "models/item/talisman.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "forja:item/talisman", "layer1": "forja:item/talisman_marco"}})
    write_json(ASSETS / "items/talisman.json", {
        "model": {
            "type": "minecraft:model",
            "model": "forja:item/talisman",
            "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 0xD8D8E0}],
        }
    })
    write_json(ASSETS / "models/item/cinturon.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "forja:item/cinturon"}})
    write_json(ASSETS / "items/cinturon.json", {"model": {"type": "minecraft:model", "model": "forja:item/cinturon"}})
    write_json(ASSETS / "models/item/sello.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "forja:item/sello", "layer1": "forja:item/sello_marco"}})
    write_json(ASSETS / "items/sello.json", {
        "model": {
            "type": "minecraft:model",
            "model": "forja:item/sello",
            "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 0xD8D8E0}],
        }
    })
    write_json(ASSETS / "models/item/orbe_de_mejora.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "forja:item/orbe_de_mejora", "layer1": "forja:item/orbe_de_mejora_brillo"}})
    write_json(ASSETS / "items/orbe_de_mejora.json", {
        "model": {
            "type": "minecraft:model",
            "model": "forja:item/orbe_de_mejora",
            "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 0xC07BFF}],
        }
    })
    wrap_broken_models()
    write_json(ASSETS / "models/item/lingote_de_temple.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "forja:item/lingote_de_temple"}})
    write_json(ASSETS / "items/lingote_de_temple.json", {"model": {"type": "minecraft:model", "model": "forja:item/lingote_de_temple"}})
    write_json(ASSETS / "models/item/guia_de_forja.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "forja:item/guia_de_forja"}})
    write_json(ASSETS / "items/guia_de_forja.json", {"model": {"type": "minecraft:model", "model": "forja:item/guia_de_forja"}})


# The gauntlets are the one thing in the mod you do not hold — you wear it. A flat card turned edge-on
# in the fist reads as a plank; a box the size of a hand, worn over the hand, reads as a gauntlet.
#
# It is built as three models, one per part, so each keeps its own tint: the mitt, the band across the
# knuckles, and the rivets through it. They are drawn stacked by the same composite that stacks the
# flat layers of every other weapon.
# A fist, not a box. The first version of this was one cube with a band across the top and it read as
# a crate — the same lesson the flat sprite taught: what makes a hand a hand is the **silhouette**, so
# the wrist is narrower than the hand, the knuckles stand out as separate bumps with gaps you can see
# between them, and the thumb breaks one side.
GAUNTLET_BOXES = {
    # (from, to, uv) per slot: 0 the mitt, 1 the knuckles, 2 the rivets.
    #
    # The knuckles ride across the top of the hand with leather between them rather than air. The gaps
    # were what made the glove look see-through: they were cut all the way through the model, so from
    # any angle but dead-on you were looking at the grass through the smith's fist. Separating the
    # knuckles is right — three bumps, not one bar — but what separates them has to be **something**,
    # and on a real gauntlet that something is the glove underneath.
    0: [([5, 0, 5], [11, 3, 11], [3, 12, 13, 15]),        # the wrist
        ([4, 3, 4], [12, 12, 12], [3, 5, 13, 12]),        # the hand, up to the knuckle line
        ([2, 4, 7], [4, 9, 11], [3, 7, 7, 12]),           # the thumb, folded across
        ([6, 12, 6], [7, 15, 12], [3, 5, 13, 12]),        # and the leather showing between the plates
        ([9, 12, 6], [10, 15, 12], [3, 5, 13, 12])],
    # Set a pixel proud of the leather at each end, so they stand out without opening a hole.
    1: [([4, 12, 5], [6, 15, 13], [1, 1, 5, 5]),
        ([7, 12, 5], [9, 15, 13], [5, 1, 9, 5]),
        ([10, 12, 5], [12, 15, 13], [9, 1, 13, 5])],
    # The rivets go on the front of each plate rather than on top: on top they needed a gap of their
    # own between them, and that was the hole coming straight back.
    2: [([4, 13, 4], [6, 14, 5], [2, 3, 4, 5]),
        ([7, 13, 4], [9, 14, 5], [5, 3, 7, 5]),
        ([10, 13, 4], [12, 14, 5], [9, 3, 11, 5])],
}

# Worn rather than wielded: no rotation that lays it along the arm, and big enough to cover the fist.
GAUNTLET_DISPLAY = {
    # No turn at all. The -90 that every handheld item uses is there to put a flat sprite edge-on to
    # the camera; this is not a sprite, and that turn sent the knuckles round to face the wearer's own
    # ribs. A fist punches forward, so the model's front stays the front.
    # Scaled to a hand, not to a sword. 0.85 is what every handheld item uses, but a handheld item is a
    # flat sprite: at that scale a **solid** model is 0.85 of a block on every side, and a player's hand
    # is a quarter of a block. It came out three times the size of the fist it is meant to cover and
    # read as a slab hanging off the wrist. Half that covers the hand with the plates still proud of it.
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0.5, 0], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0.5, 0], "scale": [0.45, 0.45, 0.45]},
    # Big enough to read as a fist over the hand, small enough not to fill the screen with leather.
    # And first person turns it right round. The two views want opposite things and both are right:
    # from outside you see the back of someone's fist with the knuckles pointing where they punch, and
    # from inside your own eyes you are looking **at** the back of your own hand, so the knuckles have
    # to face you. Pointing them forward there left nothing on screen but the flat of the mitt.
    # Same reason, and worse up close: at 0.62 the glove filled half the screen and ran off the edge of
    # it. The offsets went too, they were borrowed from the weapons' grip maths and a glove has no grip.
    # Smaller again here than in third person: the first-person camera sits a few inches from the fist,
    # so the same glove that covers a hand from across the room covers the whole arm from inside it.
    #
    # The offsets are vanilla's, and they are not decoration. First person puts the transform origin in
    # the middle of the view, not at the hand — every handheld item carries this same shove right, up
    # and forward to reach the fist. Zeroing them out, which looked like tidying, left the glove
    # hanging in the centre of the screen in front of an arm it was supposed to be worn on.
    "firstperson_righthand": {"rotation": [0, 180, 0], "translation": [1.13, 3.2, 1.13], "scale": [0.34, 0.34, 0.34]},
    "firstperson_lefthand": {"rotation": [0, 180, 0], "translation": [1.13, 3.2, 1.13], "scale": [0.34, 0.34, 0.34]},
    "gui": {"rotation": [25, -40, 0], "translation": [0, 0, 0], "scale": [0.92, 0.92, 0.92]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 13, 0], "scale": [1.0, 1.0, 1.0]},
}


def gauntlet_model(slot):
    """One part of the gauntlet, as boxes rather than as a printed card.

    Each face takes its rectangle from the band of the skin that matches the way it points, and a
    different column per box, so three plates side by side do not repeat the same four pixels.
    """
    texture = f"forja:item/guanteletes/piel_{slot}"
    elements = []
    for index, (start, end, _uv) in enumerate(GAUNTLET_BOXES[slot]):
        column = (index * 3) % 12
        faces = {}
        for name, (top, bottom) in (("up", (0, 5)), ("north", (5, 11)), ("south", (5, 11)),
                                    ("east", (5, 11)), ("west", (5, 11)), ("down", (11, 16))):
            faces[name] = {"uv": [column, top, column + 4, bottom], "texture": "#skin", "tintindex": 0}
        elements.append({"from": start, "to": end, "faces": faces})
    return {
        "parent": "minecraft:block/block",
        "textures": {"skin": texture, "particle": texture},
        "elements": elements,
        "display": GAUNTLET_DISPLAY,
    }


MODEL_PARENTS = {"mazo": "minecraft:item/handheld_mace", "alas": "minecraft:item/generated"}

# Where the hand actually is.
#
# `minecraft:item/handheld` grips the **bottom-left corner of the sprite**, which is where a sword has
# its pommel and a pickaxe the end of its haft. Three of ours do not: the flail's haft stops halfway up
# the left edge with the ball hanging below it, the hook's stick is up in the top right, and a mitt has
# no handle at all. All three came out held by the wrong end, and it is not something an icon can show
# you — only the hand can.
#
# The first attempt at fixing it guessed a translation and shoved both weapons clean out of frame. The
# numbers are not guessable: the transform turns the item edge-on to the camera and then tilts it, so a
# shift **up the sprite** comes out as a shift sideways and down in the hand's own axes, and a shift
# **across the sprite** comes out as depth. Work it through instead.


def grip_display(dx, dy):
    """Handheld transforms for an item whose grip is not at the sprite's bottom-left corner.

    `dy` is how far **up from the bottom of the sprite** the grip sits, `dx` how far across.

    Only the vertical part is corrected in third person, and that is the whole lesson here. The
    sideways part of the correction looked right on paper — the grip really is a few pixels in from the
    edge — and in the game it **detached the weapon from the hand**: it floated beside the player with
    a gap you could see through. Translation here is not in the hand's frame, so moving the item
    sideways moves it away from the fist rather than along it. Sliding it down its own length keeps it
    in the grip, which is the only thing that had to happen.
    """
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, round(4.0 - dy, 2), 0.5],
                                  "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, round(4.0 - dy, 2), 0.5],
                                 "scale": [0.85, 0.85, 0.85]},
        # First person is the other way round: there is no hand worth speaking of, only an item held up
        # in the corner, so the drop is what throws it out of frame and the sideways and depth shifts
        # are what bring it in. Without them the hook hung off the right-hand edge and never appeared.
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [round(1.13 + dy * 0.423, 2), 3.2, round(1.13 - dx, 2)],
                                  "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [round(1.13 - dy * 0.423, 2), 3.2, round(1.13 - dx, 2)],
                                 "scale": [0.68, 0.68, 0.68]},
    }


GRIPS = {
    # The haft's butt sits nine pixels up from the bottom of the sprite.
    "mangual": grip_display(0, 9),
    # No entry for the hook any more: its sprite now puts the handle in the corner the hand already
    # grips, so the vanilla transform is right for it and anything added here would only move it off.
}


def generate_spear_models():
    """Like vanilla spears: the flat icon in inventories and on the ground, the long 32x32 spear in hand."""
    slots = TYPES["lanza"]

    def layered(prefix, parent):
        models = []
        for slot, part in enumerate(slots):
            name = f"{prefix}{slot}"
            write_json(ASSETS / f"models/item/lanza/{name}.json", {"parent": parent, "textures": {"layer0": f"forja:item/lanza/{name}"}})
            models.append({"type": "minecraft:model", "model": f"forja:item/lanza/{name}", "tints": [tint(slot, PARTS[part])]})
        return {"type": "minecraft:composite", "models": models}

    write_json(ASSETS / "items/lanza.json", {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": layered("", "minecraft:item/generated")}],
            "fallback": layered("mano_", "minecraft:item/spear_in_hand"),
        },
        "swap_animation_scale": 1.95,
    })


def wrap_broken_models():
    """Broken forged gear keeps its look everywhere, with cracks over the inventory icon."""
    write_json(ASSETS / "models/item/grieta.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "forja:item/grieta"}})
    crack = {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": "forja:item/grieta"}}],
        "fallback": {"type": "minecraft:empty"},
    }
    for type_id in TYPES:
        path = ASSETS / f"items/{type_id}.json"
        definition = json.loads(path.read_text(encoding="utf-8"))
        normal = definition["model"]
        definition["model"] = {
            "type": "minecraft:condition",
            "property": "minecraft:broken",
            "on_true": {"type": "minecraft:composite", "models": [normal, crack]},
            "on_false": normal,
        }
        write_json(path, definition)


def tint(index, role):
    return {"type": "minecraft:custom_model_data", "index": index, "default": DEFAULT_COLORS[role]}


def generate_bow_models():
    roles = ["HEAD", "EXTRA", "HANDLE"]

    def state_model(state):
        models = []
        for label in range(3):
            write_json(ASSETS / f"models/item/arco/{state}_{label}.json", {"parent": "minecraft:item/bow", "textures": {"layer0": f"forja:item/arco/{state}_{label}"}})
            models.append({"type": "minecraft:model", "model": f"forja:item/arco/{state}_{label}", "tints": [tint(label, roles[label])]})
        if (ASSETS / f"textures/item/arco/{state}_3.png").exists():
            write_json(ASSETS / f"models/item/arco/{state}_3.json", {"parent": "minecraft:item/bow", "textures": {"layer0": f"forja:item/arco/{state}_3"}})
            models.append({"type": "minecraft:model", "model": f"forja:item/arco/{state}_3"})
        return {"type": "minecraft:composite", "models": models}

    write_json(ASSETS / "items/arco.json", {
        "model": {
            "type": "minecraft:condition",
            "property": "minecraft:using_item",
            "on_false": state_model("bow"),
            "on_true": {
                "type": "minecraft:range_dispatch",
                "property": "minecraft:use_duration",
                "scale": 0.05,
                "entries": [
                    {"threshold": 0.65, "model": state_model("bow_pulling_1")},
                    {"threshold": 0.9, "model": state_model("bow_pulling_2")},
                ],
                "fallback": state_model("bow_pulling_0"),
            },
        }
    })


def generate_crossbow_models():
    roles = ["HEAD", "EXTRA", "HANDLE", "EXTRA"]

    def state_model(state):
        models = []
        for label in range(4):
            write_json(ASSETS / f"models/item/ballesta/{state}_{label}.json", {"parent": "minecraft:item/crossbow", "textures": {"layer0": f"forja:item/ballesta/{state}_{label}"}})
            models.append({"type": "minecraft:model", "model": f"forja:item/ballesta/{state}_{label}", "tints": [tint(label, roles[label])]})
        if (ASSETS / f"textures/item/ballesta/{state}_4.png").exists():
            write_json(ASSETS / f"models/item/ballesta/{state}_4.json", {"parent": "minecraft:item/crossbow", "textures": {"layer0": f"forja:item/ballesta/{state}_4"}})
            models.append({"type": "minecraft:model", "model": f"forja:item/ballesta/{state}_4"})
        return {"type": "minecraft:composite", "models": models}

    write_json(ASSETS / "items/ballesta.json", {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:charge_type",
            "cases": [
                {"when": "arrow", "model": state_model("crossbow_arrow")},
                {"when": "rocket", "model": state_model("crossbow_firework")},
            ],
            "fallback": {
                "type": "minecraft:condition",
                "property": "minecraft:using_item",
                "on_false": state_model("crossbow_standby"),
                "on_true": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:crossbow/pull",
                    "entries": [
                        {"threshold": 0.58, "model": state_model("crossbow_pulling_1")},
                        {"threshold": 1.0, "model": state_model("crossbow_pulling_2")},
                    ],
                    "fallback": state_model("crossbow_pulling_0"),
                },
            },
        }
    })


def box(start, end, texture, tint_index, big_faces):
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        uv = [0, 0, 16, 16] if face in big_faces else [0, 0, 2, 16]
        faces[face] = {"texture": texture, "uv": uv, "tintindex": tint_index}
    return {"from": start, "to": end, "faces": faces}


def generate_rod_models():
    roles = ["HANDLE", "EXTRA"]

    def state_model(state):
        models = []
        for label in range(2):
            write_json(ASSETS / f"models/item/cana/{state}_{label}.json", {"parent": "minecraft:item/handheld_rod", "textures": {"layer0": f"forja:item/cana/{state}_{label}"}})
            models.append({"type": "minecraft:model", "model": f"forja:item/cana/{state}_{label}", "tints": [tint(label, roles[label])]})
        return {"type": "minecraft:composite", "models": models}

    write_json(ASSETS / "items/cana.json", {
        "model": {
            "type": "minecraft:condition",
            "property": "minecraft:fishing_rod/cast",
            "on_true": state_model("fishing_rod_cast"),
            "on_false": state_model("fishing_rod"),
        }
    })


def generate_shield_models():
    """A real 3D shield built from cuboids, with the same size and placement as the vanilla shield, so
    vanilla's hand and GUI transforms fit. Plate, rim and handle each take their own tint."""
    plate, rim, grip = "#placa", "#borde", "#asa"
    elements = [
        box([-6, -11, 1], [6, 11, 2], plate, 0, ("north", "south")),
        box([-2, -2, 2], [2, 2, 3], plate, 0, ("north", "south")),
        box([-7, -12, 0.5], [-6, 12, 2.5], rim, 1, ("east", "west")),
        box([6, -12, 0.5], [7, 12, 2.5], rim, 1, ("east", "west")),
        box([-6, 11, 0.5], [6, 12, 2.5], rim, 1, ("up", "down")),
        box([-6, -12, 0.5], [6, -11, 2.5], rim, 1, ("up", "down")),
        box([-1, -3, -5], [1, 3, 1], grip, 2, ("east", "west")),
    ]
    textures = {"placa": "forja:item/escudo/placa", "borde": "forja:item/escudo/borde", "asa": "forja:item/escudo/asa", "particle": "forja:item/escudo/placa"}
    vanilla_shield = json.loads(jar_read("assets/minecraft/models/item/shield.json"))
    vanilla_blocking = json.loads(jar_read("assets/minecraft/models/item/shield_blocking.json"))
    write_json(ASSETS / "models/item/escudo/escudo.json", {
        "gui_light": "front", "textures": textures, "elements": elements, "display": vanilla_shield["display"],
    })
    write_json(ASSETS / "models/item/escudo/escudo_bloqueando.json", {
        "parent": "forja:item/escudo/escudo", "display": vanilla_blocking["display"],
    })
    tints = [tint(0, "PLATE"), tint(1, "EXTRA"), tint(2, "HANDLE")]
    write_json(ASSETS / "items/escudo.json", {
        "model": {
            "type": "minecraft:condition",
            "property": "minecraft:using_item",
            "on_false": {"type": "minecraft:model", "model": "forja:item/escudo/escudo", "tints": tints},
            "on_true": {"type": "minecraft:model", "model": "forja:item/escudo/escudo_bloqueando", "tints": tints},
        }
    })


# ---------------------------------------------------------------- advancements

# id: (parent, icon item, frame, criteria); criteria None means one impossible "done" criterion granted from code.
ADVANCEMENTS = {
    "plantilla": ("root", "forja:plantilla", "task", None),
    "guia": ("root", "forja:guia_de_forja", "task", None),
    "pieza": ("plantilla", "forja:cabeza_pico", "task", None),
    "desarmar": ("pieza", "minecraft:grindstone", "task", None),
    "forja": ("pieza", "forja:pico", "task", None),
    "cambio": ("forja", "forja:mango", "task", None),
    "reparar": ("forja", "minecraft:anvil", "task", None),
    "rasgo": ("forja", "minecraft:emerald", "task", None),
    "netherita": ("forja", "minecraft:netherite_ingot", "goal", None),
    "arsenal": ("forja", "forja:lanza", "challenge", ["arco", "ballesta", "escudo", "lanza", "mazo", "tridente"]),
    "mejora": ("forja", "minecraft:redstone", "task", None),
    "mejora_completa": ("mejora", "minecraft:amethyst_block", "goal", None),
    "maestro": ("mejora_completa", "minecraft:nether_star", "challenge", None),
    "maestria": ("forja", "minecraft:experience_bottle", "challenge", None),
    "roto": ("forja", "minecraft:iron_nugget", "task", None),
    "orbe": ("desarmar", "forja:orbe_de_mejora", "task", None),
    "fusion": ("orbe", "minecraft:amethyst_shard", "goal", None),
    "conjunto": ("forja", "forja:pechera", "goal", None),
    "eco": ("rasgo", "minecraft:echo_shard", "goal", None),
    "parada": ("forja", "forja:escudo", "goal", None),
    "don": ("maestria", "forja:sello", "challenge", None),
    "temple": ("forja", "minecraft:water_bucket", "task", None),
    "perfecta": ("forja", "minecraft:anvil", "goal", None),
    "obra_maestra": ("don", "minecraft:nether_star", "challenge", None),
    "tecnica": ("maestria", "minecraft:blast_furnace", "goal", None),
    "martillo": ("herrero_caido", "forja:martillo_del_maestro", "goal", None),
    "herencia": ("maestria", "minecraft:soul_lantern", "goal", None),
    "encargo": ("trato", "minecraft:emerald_block", "goal", None),
    "elite": ("forja", "minecraft:totem_of_undying", "challenge", None),
    "saqueadores": ("forja", "minecraft:crossbow", "goal", None),
    "tumulo": ("ruina", "minecraft:soul_lantern", "goal",
               {"trigger": "minecraft:player_generates_container_loot",
                "conditions": {"loot_table": "forja:chests/tumulo_del_herrero"}}),
    "taller": ("root", "forja:mesa_de_talabarteria", "task",
               {"trigger": "minecraft:player_generates_container_loot",
                "conditions": {"loot_table": "forja:chests/taller_de_montana"}}),
    "evento": ("root", "forja:jarra", "challenge", None),
    "campamento": ("saqueadores", "minecraft:bell", "goal",
                   {"trigger": "minecraft:player_generates_container_loot",
                    "conditions": {"loot_table": "forja:chests/campamento_saqueadores"}}),
    "estelar": ("rasgo", "forja:hierro_estelar", "goal", None),
    "aleacion": ("forja", "forja:acero", "task", None),
    "fundir": ("desarmar", "minecraft:lava_bucket", "task", None),
    "herrero_caido": ("damasco", "forja:fragua_apagada", "challenge", None),
    "corazon": ("herrero_caido", "forja:corazon_de_forja", "challenge", None),
    "damasco": ("aleacion", "forja:damasco", "goal", None),
    "leyenda": ("forja", "minecraft:nether_star", "goal", "LEGENDS"),
    "trato": ("root", "minecraft:emerald", "task",
              {"trigger": "minecraft:villager_trade",
               "conditions": {"item": {"items": ["forja:plantilla", "forja:orbe_de_mejora", "forja:lingote_de_temple", "forja:mango", "minecraft:map"]}}}),
    "ruina": ("root", "minecraft:cracked_stone_bricks", "task",
              {"trigger": "minecraft:player_generates_container_loot", "conditions": {"loot_table": "forja:chests/forja_abandonada"}}),
    # Killing one while it is fed is the hard way round, because fed is when it hits back hardest.
    "pavesa": ("ruina", "forja:huevo_pavesa", "goal", None),
    # Taking one alive is harder than killing it: the catch only works while it is fed.
    "farol": ("pavesa", "forja:farol_de_pavesa", "challenge", None),
}


# perk id: the two things stamped into the seal besides the gold it is struck on.
SEAL_RECIPES = {
    "filo_eterno": ["minecraft:diamond", "minecraft:obsidian"],
    "cazador": ["minecraft:arrow", "minecraft:spider_eye"],
    "minero": ["minecraft:quartz", "minecraft:lapis_lazuli"],
    "baluarte": ["minecraft:shield", "minecraft:iron_ingot"],
    "viajero": ["minecraft:feather", "minecraft:sugar"],
    "pescador": ["minecraft:nautilus_shell", "minecraft:string"],
    "cargador": ["minecraft:arrow", "minecraft:feather"],
    "jinete": ["minecraft:saddle", "minecraft:leather"],
    "duelista": ["minecraft:blaze_powder", "minecraft:redstone"],
    "tirador": ["minecraft:arrow", "minecraft:wind_charge"],
    "muralla": ["minecraft:iron_block", "minecraft:stone_bricks"],
    "aeronauta": ["minecraft:phantom_membrane", "minecraft:breeze_rod"],
}


def perk_colors():
    """Gift ids and colours straight out of Perk.java."""
    import re

    text = (ROOT / "src/main/java/dev/forja/forge/Perk.java").read_text(encoding="utf-8")
    body = text.split("public enum Perk {")[1].split("/** What you spend")[0] if "public enum Perk {" in text else text
    return {name.lower(): int(color, 16) for name, color in re.findall(r"^\t([A-Z_]+)\(0x([0-9A-Fa-f]{6})", body, re.MULTILINE)}


TALISMANS = {
    "esmeralda": ("minecraft:emerald", 0x3FD46A),
    "diamante": ("minecraft:diamond", 0x5FF0E2),
    "cuarzo": ("minecraft:quartz", 0xEEE6DA),
    "amatista": ("minecraft:amethyst_shard", 0xB57BEA),
    "eco": ("minecraft:echo_shard", 0x2A8C94),
    "prismarina": ("minecraft:prismarine_shard", 0x6CC7B2),
    "neterita": ("minecraft:netherite_ingot", 0x6B5A5A),
}


def generate_jar_recipe():
    """The flask costs a nether star: it is meant to be the thing you have one of."""
    write_json(DATA / "recipe/jarra.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["gsg", "geg", "ggg"],
        "key": {"g": "minecraft:glass", "s": "minecraft:nether_star", "e": "minecraft:echo_shard"},
        "result": {"id": "forja:jarra", "count": 1},
    })


def generate_talisman_recipes():
    """One talisman per gem: eight nuggets around the stone, and a belt out of leather and iron."""
    for name, (gem, color) in TALISMANS.items():
        write_json(DATA / f"recipe/talisman_{name}.json", {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "pattern": ["nnn", "ngn", "nnn"],
            "key": {"n": "minecraft:gold_nugget", "g": gem},
            "result": {
                "id": "forja:talisman",
                "components": {
                    "forja:talisman": name,
                    "minecraft:item_name": {"translate": "item.forja.talisman.de", "with": [{"translate": f"talisman.forja.{name}"}]},
                    "minecraft:custom_model_data": {"colors": [color]},
                    "minecraft:rarity": "uncommon",
                },
            },
        })
    write_json(DATA / "recipe/cinturon.json", {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": ["lll", "isi", "lll"],
        "key": {"l": "minecraft:leather", "i": "minecraft:iron_ingot", "s": "minecraft:string"},
        "result": {"id": "forja:cinturon", "count": 1},
    })


def generate_seal_recipes():
    colors = perk_colors()
    for perk, extras in SEAL_RECIPES.items():
        write_json(DATA / f"recipe/sello_{perk}.json", {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": ["minecraft:gold_ingot"] + extras,
            "result": {
                "id": "forja:sello",
                "components": {
                    "forja:sello": perk,
                    "minecraft:item_name": {"translate": "item.forja.sello.de", "with": [{"translate": f"perk.forja.{perk}"}]},
                    "minecraft:custom_model_data": {"colors": [colors[perk]]},
                    "minecraft:rarity": "uncommon",
                },
            },
        })


def legend_ids():
    """The legend ids straight out of Legends.java, so the advancement cannot drift from the code."""
    import re

    text = (ROOT / "src/main/java/dev/forja/world/Legends.java").read_text(encoding="utf-8")
    return re.findall(r'new Legend\("([a-z_]+)"', text)


def generate_advancements():
    folder = DATA / "advancement"
    shutil.rmtree(folder, ignore_errors=True)
    tables = ["forja:mesa_de_piezas", "forja:mesa_de_forja", "forja:plantilla", "forja:guia_de_forja"]
    write_json(folder / "forja/root.json", {
        "display": {
            "icon": {"id": "forja:mesa_de_forja"},
            "title": {"translate": "advancements.forja.root.title"},
            "description": {"translate": "advancements.forja.root.description"},
            # The mod's own wall (generate_advancement_background), not the grey stone half the tabs anyone has installed share.
            "background": "forja:gui/advancements/backgrounds/forja",
            "show_toast": False,
            "announce_to_chat": False,
        },
        "criteria": {item.split(":")[1]: {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}} for item in tables},
        "requirements": [[item.split(":")[1] for item in tables]],
    })
    for key, (parent, icon, frame, criteria) in ADVANCEMENTS.items():
        if criteria == "LEGENDS":
            # One criterion per legend; finding any of them is enough.
            names = legend_ids()
            write_json(folder / f"forja/{key}.json", {
                "parent": f"forja:forja/{parent}",
                "display": {
                    "icon": {"id": icon},
                    "title": {"translate": f"advancements.forja.{key}.title"},
                    "description": {"translate": f"advancements.forja.{key}.description"},
                    "frame": frame,
                    "show_toast": True,
                    "announce_to_chat": True,
                },
                "criteria": {
                    name: {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"components": {"forja:leyenda": name}}]}}
                    for name in names
                },
                "requirements": [names],
            })
            continue
        if isinstance(criteria, dict):
            write_json(folder / f"forja/{key}.json", {
                "parent": f"forja:forja/{parent}",
                "display": {
                    "icon": {"id": icon},
                    "title": {"translate": f"advancements.forja.{key}.title"},
                    "description": {"translate": f"advancements.forja.{key}.description"},
                    "frame": frame,
                    "show_toast": True,
                    "announce_to_chat": True,
                },
                "criteria": {"done": criteria},
            })
            continue
        names = criteria or ["done"]
        write_json(folder / f"forja/{key}.json", {
            "parent": f"forja:forja/{parent}",
            "display": {
                "icon": {"id": icon},
                "title": {"translate": f"advancements.forja.{key}.title"},
                "description": {"translate": f"advancements.forja.{key}.description"},
                "frame": frame,
                "show_toast": True,
                "announce_to_chat": True,
            },
            "criteria": {name: {"trigger": "minecraft:impossible"} for name in names},
            "requirements": [[name] for name in names],
        })
    # Recipe book: the tables and the guide unlock with iron, blank templates with planks.
    for recipe, item in (("mesa_de_piezas", "minecraft:iron_ingot"), ("mesa_de_forja", "minecraft:iron_ingot"),
                         ("guia_de_forja", "minecraft:book"), ("plantilla", "#minecraft:planks"),
                         ("sello_filo_eterno", "minecraft:gold_ingot"), ("sello_cazador", "minecraft:gold_ingot"),
                         ("sello_minero", "minecraft:gold_ingot"), ("sello_baluarte", "minecraft:gold_ingot"),
                         ("sello_viajero", "minecraft:gold_ingot"), ("sello_pescador", "minecraft:gold_ingot")):
        write_json(folder / f"recipes/misc/{recipe}.json", {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_material": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}},
                "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": f"forja:{recipe}"}},
            },
            "requirements": [["has_material", "has_the_recipe"]],
            "rewards": {"recipes": [f"forja:{recipe}"]},
        })


# ---------------------------------------------------------------- data

# profession: [(level, name, emeralds, gives template, max uses, xp)]
FORJADOR_TRADES = [
    (1, "plantilla", 1, {"id": "forja:plantilla", "count": 3}, 16, 2),
    (1, "lingote_de_temple", 2, {"id": "forja:lingote_de_temple", "count": 2}, 12, 2),
    (1, "mango_de_hueso", 2, {"id": "forja:mango", "components": {
        "forja:material": "hueso",
        "minecraft:custom_model_data": {"colors": [0xF1ECD2]},
        "minecraft:item_name": {"translate": "part.forja.mango.de", "with": [{"translate": "material.forja.hueso"}]},
    }}, 8, 2),
    (2, "plantilla_mango", 3, "mango", 4, 10),
    (2, "plantilla_atadura", 3, "atadura", 4, 10),
    (2, "plantilla_guarda", 4, "guarda", 4, 10),
    (2, "plantilla_forro", 4, "forro", 4, 10),
    (3, "plantilla_brazos_arco", 6, "brazos_arco", 3, 15),
    (3, "plantilla_cuerda", 5, "cuerda", 3, 15),
    (3, "plantilla_cabeza_mazo", 8, "cabeza_mazo", 3, 15),
    (3, "orbe_irrompible", 14, ("irrompible", 0x7A5AA8, 25), 3, 15),
    (4, "orbe_filo", 22, ("filo", 0xE0E8F0, 40), 2, 20),
    (4, "orbe_eficiencia", 22, ("eficiencia", 0xF5F5A0, 40), 2, 20),
    (4, "orbe_proteccion", 22, ("proteccion", 0xC9A27A, 40), 2, 20),
    (5, "orbe_reparacion", 32, ("reparacion", 0x9CFF6B, 50), 1, 30),
    (5, "mapa_forja_abandonada", 10, {"id": "minecraft:map"}, 6, 30, None),
    # The new work of the mod, sold by the smith who lives off it.
    (2, "plantilla_membrana", 5, "membrana", 3, 10),
    (3, "plantilla_punta_flecha", 4, "punta_flecha", 4, 15),
    (3, "plantilla_garfio", 7, "garfio", 3, 15),
    (4, "plantilla_placa_barda", 10, "placa_barda", 2, 20),
    (4, "talisman_cuarzo", 26, {"id": "forja:talisman", "components": {
        "forja:talisman": "cuarzo",
        "minecraft:custom_model_data": {"colors": [0xEEE6DA]},
        "minecraft:item_name": {"translate": "item.forja.talisman.de", "with": [{"translate": "talisman.forja.cuarzo"}]},
        "minecraft:rarity": "uncommon",
    }}, 2, 20),
    (5, "cinturon", 34, {"id": "forja:cinturon"}, 1, 30),
    (4, "mapa_taller", 12, {"id": "minecraft:map"}, 4, 20, None),
    (5, "orbe_aturdimiento", 30, ("aturdimiento", 0xE8D26A, 50), 1, 30),
    (5, "mapa_campamento", 14, {"id": "minecraft:map"}, 3, 30, None),
    # The great castle is rare enough that nobody finds one by walking: the master smith knows where the nearest is.
    (5, "mapa_bastion", 24, {"id": "minecraft:map"}, 2, 30, None),
]

TRADES = {
    "toolsmith": [
        (1, "plantilla", 1, {"id": "forja:plantilla", "count": 2}, 12, 2),
        (2, "plantilla_cabeza_pico", 5, "cabeza_pico", 4, 10),
        (3, "plantilla_cabeza_martillo", 8, "cabeza_martillo", 3, 15),
        (4, "orbe_eficiencia", 20, ("eficiencia", 0xF5F5A0, 30), 2, 20),
        (5, "mapa_forja_abandonada", 12, {"id": "minecraft:map"}, 6, 10, {
            "additional_wants": {"id": "minecraft:compass"},
            "given_item_modifiers": [
                {"function": "minecraft:exploration_map", "destination": "forja:on_forja_abandonada", "decoration": "minecraft:target_x", "search_radius": 100},
                {"function": "minecraft:set_name", "name": {"translate": "filled_map.forja_abandonada"}, "target": "item_name"},
                {"function": "minecraft:filtered", "item_filter": {"items": "minecraft:filled_map", "predicates": {"minecraft:map_id": {}}},
                 "on_fail": {"function": "minecraft:discard"}},
            ],
        }),
    ],
    "weaponsmith": [
        (1, "plantilla", 1, {"id": "forja:plantilla", "count": 2}, 12, 2),
        (2, "plantilla_hoja", 5, "hoja", 4, 10),
        (3, "plantilla_punta_lanza", 7, "punta_lanza", 3, 15),
        (4, "plantilla_punta_tridente", 12, "punta_tridente", 2, 20),
        (4, "orbe_filo", 22, ("filo", 0xE0E8F0, 30), 2, 20),
    ],
    "armorer": [
        (1, "plantilla", 1, {"id": "forja:plantilla", "count": 2}, 12, 2),
        (2, "plantilla_placa_pechera", 6, "placa_pechera", 4, 10),
        (3, "plantilla_placa_escudo", 6, "placa_escudo", 3, 15),
        (4, "orbe_proteccion", 22, ("proteccion", 0xC9A27A, 30), 2, 20),
    ],
}


def trade_gives(gives):
    if isinstance(gives, dict):
        return gives
    if isinstance(gives, str):
        return {
            "id": "forja:plantilla",
            "components": {
                "forja:molde": gives,
                "minecraft:item_name": {"translate": "item.forja.plantilla.de", "with": [{"translate": f"part.forja.{gives}"}]},
                "minecraft:custom_model_data": {"strings": [gives]},
            },
        }
    upgrade, color, percent = gives
    return {
        "id": "forja:orbe_de_mejora",
        "components": {
            "forja:orbe": {"mejora": upgrade, "porcentaje": percent},
            "minecraft:item_name": {"translate": "item.forja.orbe_de_mejora.de", "with": [{"translate": f"upgrade.forja.{upgrade}"}]},
            "minecraft:custom_model_data": {"colors": [color]},
            "minecraft:rarity": "uncommon",
        },
    }


def generate_trades():
    """Village smiths sell blank and engraved templates, and at master level an upgrade orb. The Forjador
    has trade sets of its own."""
    shutil.rmtree(DATA / "villager_trade", ignore_errors=True)
    shutil.rmtree(DATA / "trade_set", ignore_errors=True)
    forge_map = next(extra[0] for level, name, _, _, _, _, *extra in TRADES["toolsmith"] if name == "mapa_forja_abandonada")
    # The same shape as the forge map: the item modifiers turn a blank map into one that points at it.
    workshop_map = {
        "given_item_modifiers": [
            {"function": "minecraft:exploration_map", "destination": "forja:on_taller_de_montana",
             "decoration": "minecraft:red_x", "search_radius": 100},
            {"function": "minecraft:set_name", "name": {"translate": "filled_map.forja.taller_de_montana"}, "target": "item_name"},
            {"function": "minecraft:filtered",
             "item_filter": {"items": "minecraft:filled_map", "predicates": {"minecraft:map_id": {}}},
             "on_fail": {"function": "minecraft:discard"}},
        ],
    }
    camp_map = {
        "given_item_modifiers": [
            {"function": "minecraft:exploration_map", "destination": "forja:on_campamento_saqueadores",
             "decoration": "minecraft:target_point", "search_radius": 100},
            {"function": "minecraft:set_name", "name": {"translate": "filled_map.forja.campamento"}, "target": "item_name"},
            {"function": "minecraft:filtered",
             "item_filter": {"items": "minecraft:filled_map", "predicates": {"minecraft:map_id": {}}},
             "on_fail": {"function": "minecraft:discard"}},
        ],
    }
    bastion_map = {
        "given_item_modifiers": [
            # spacing is 140 chunks: the search has to be allowed to go that far, in blocks-of-chunks as the function counts
            {"function": "minecraft:exploration_map", "destination": "forja:on_bastion_del_gremio",
             "decoration": "minecraft:mansion", "search_radius": 200, "skip_existing_chunks": False},
            {"function": "minecraft:set_name", "name": {"translate": "filled_map.forja.bastion"}, "target": "item_name"},
            {"function": "minecraft:filtered",
             "item_filter": {"items": "minecraft:filled_map", "predicates": {"minecraft:map_id": {}}},
             "on_fail": {"function": "minecraft:discard"}},
        ],
    }
    forjador = []
    for trade in FORJADOR_TRADES:
        if trade[1] == "mapa_forja_abandonada":
            forjador.append(trade[:6] + (forge_map,))
        elif trade[1] == "mapa_taller":
            forjador.append(trade[:6] + (workshop_map,))
        elif trade[1] == "mapa_campamento":
            forjador.append(trade[:6] + (camp_map,))
        elif trade[1] == "mapa_bastion":
            forjador.append(trade[:6] + (bastion_map,))
        else:
            forjador.append(trade)
    for level in range(1, 6):
        write_json(DATA / f"trade_set/forjador/level_{level}.json", {
            "amount": 2.0,
            "random_sequence": f"forja:trade_set/forjador/level_{level}",
            "trades": f"#forja:forjador/level_{level}",
        })
    for profession, trades in list(TRADES.items()) + [("forjador", forjador)]:
        levels = {}
        for level, name, emeralds, gives, uses, xp, *extra in trades:
            trade_id = f"{profession}/{level}/{name}"
            trade = {
                "gives": trade_gives(gives),
                "max_uses": float(uses),
                "reputation_discount": 0.05,
                "wants": {"count": float(emeralds), "id": "minecraft:emerald"},
                "xp": float(xp),
            }
            if extra:
                trade.update(extra[0])
            write_json(DATA / f"villager_trade/{trade_id}.json", trade)
            levels.setdefault(level, []).append(f"forja:{trade_id}")
        for level, values in levels.items():
            namespace = "forja" if profession == "forjador" else "minecraft"
            write_json(RES / f"data/{namespace}/tags/villager_trade/{profession}/level_{level}.json", {"replace": False, "values": values})


class NbtByte:
    """A byte tag; NBT booleans are bytes and entity flags need them."""

    def __init__(self, value):
        self.value = value


class NbtDouble:
    """A double tag, for entity positions."""

    def __init__(self, value):
        self.value = value


def nbt_bytes(root):
    """Big-endian NBT for a structure file: dicts are compounds, strings strings, ints ints, lists tag lists."""
    import struct

    def name(text):
        raw = text.encode("utf-8")
        return struct.pack(">H", len(raw)) + raw

    def tag_type(value):
        if isinstance(value, NbtByte):
            return 1
        if isinstance(value, NbtDouble):
            return 6
        if isinstance(value, bool) or isinstance(value, int):
            return 3
        if isinstance(value, float):
            return 5
        if isinstance(value, str):
            return 8
        if isinstance(value, dict):
            return 10
        return 9

    def payload(value):
        kind = tag_type(value)
        if kind == 1:
            return struct.pack(">b", value.value)
        if kind == 6:
            return struct.pack(">d", value.value)
        if kind == 3:
            return struct.pack(">i", int(value))
        if kind == 5:
            return struct.pack(">f", value)
        if kind == 8:
            return name(value)
        if kind == 10:
            out = b""
            for key, item in value.items():
                out += bytes([tag_type(item)]) + name(key) + payload(item)
            return out + b"\x00"
        element = tag_type(value[0]) if value else 10
        return bytes([element]) + struct.pack(">i", len(value)) + b"".join(payload(item) for item in value)

    return b"\x0a" + name("") + payload(root)


# name: (floor and wall blocks, corner post, roof plank, door, bed color, villager pool)
VILLAGE_FORGES = {
    "forja_de_aldea": (
        ["minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:mossy_cobblestone"],
        ["minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:mossy_stone_bricks"],
        "minecraft:oak_log", "minecraft:oak_planks", "minecraft:oak_door", "minecraft:red_bed", "minecraft:village/plains/villagers",
    ),
    "forja_de_aldea_desierto": (
        ["minecraft:smooth_sandstone", "minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:sandstone"],
        ["minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:sandstone", "minecraft:smooth_sandstone"],
        "minecraft:chiseled_sandstone", "minecraft:smooth_sandstone", "minecraft:acacia_door", "minecraft:orange_bed", "minecraft:village/desert/villagers",
    ),
}


def generate_village_forge():
    """A village smithy in two palettes: 7x7x7, a doorway jigsaw for the street and a villager jigsaw, so
    a villager of the village takes the Parts Table and becomes a Forjador."""
    for name in VILLAGE_FORGES:
        write_village_forge(name)


def write_village_forge(name):
    import gzip
    import random

    floor, walls, post, roof, door_block, bed, villagers = VILLAGE_FORGES[name]
    rng = random.Random(4127)
    blocks = {}

    def put(x, y, z, block, properties=None, nbt=None):
        blocks[(x, y, z)] = (block, properties or {}, nbt)

    for x in range(7):
        for z in range(7):
            put(x, 0, z, rng.choice(floor))
            for y in range(1, 4):
                put(x, y, z, "minecraft:air")
    for x in range(7):
        for z in range(7):
            if not (x in (0, 6) or z in (0, 6)):
                continue
            if x in (0, 6) and z in (0, 6):
                for y in range(1, 4):
                    put(x, y, z, post, {"axis": "y"} if post.endswith("_log") else None)
                continue
            for y in range(1, 4):
                put(x, y, z, rng.choice(walls))
    # Doorway on the street side and two windows.
    put(0, 2, 3, "minecraft:air")
    put(6, 2, 3, "minecraft:air")
    put(3, 2, 0, "minecraft:air")
    # Beams and a flat plank roof with a small chimney.
    for x in range(7):
        for z in range(7):
            inner = 1 <= x <= 5 and 1 <= z <= 5
            put(x, 4, z, roof if inner else post, {"axis": "x"} if not inner and post.endswith("_log") else None)
    put(5, 4, 1, walls[0])
    put(5, 5, 1, walls[0])
    put(5, 6, 1, "minecraft:campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"}, {"id": "minecraft:campfire", "Items": []})
    put(5, 1, 2, "forja:mesa_de_piezas")
    put(5, 1, 4, "forja:mesa_de_forja")
    put(1, 1, 5, "minecraft:anvil", {"facing": "north"})
    put(1, 1, 1, "minecraft:blast_furnace", {"facing": "east", "lit": "false"}, {"id": "minecraft:blast_furnace"})
    put(3, 1, 1, "minecraft:barrel", {"facing": "up", "open": "false"}, {"id": "minecraft:barrel"})
    put(3, 3, 3, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    # A door just inside the entrance and a bed, so the smith has a home as well as a workshop.
    door = {"facing": "east", "hinge": "left", "open": "false", "powered": "false"}
    put(1, 1, 3, door_block, door | {"half": "lower"})
    put(1, 2, 3, door_block, door | {"half": "upper"})
    put(4, 1, 5, bed, {"facing": "north", "occupied": "false", "part": "foot"}, {"id": "minecraft:bed"})
    put(4, 1, 4, bed, {"facing": "north", "occupied": "false", "part": "head"}, {"id": "minecraft:bed"})
    # The street connection and the villager spawn, exactly as vanilla village houses set them up.
    put(0, 1, 3, "minecraft:jigsaw", {"orientation": "west_up"}, {
        "id": "minecraft:jigsaw", "joint": "aligned", "name": "minecraft:building_entrance",
        "target": "minecraft:building_entrance", "pool": "minecraft:empty", "final_state": "minecraft:structure_void",
    })
    put(3, 0, 3, "minecraft:jigsaw", {"orientation": "up_north"}, {
        "id": "minecraft:jigsaw", "joint": "rollable", "name": "minecraft:bottom",
        "target": "minecraft:bottom", "pool": villagers, "final_state": floor[0],
    })

    palette = []
    index = {}
    entries = []
    for (x, y, z), (block, properties, nbt) in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
        key = (block, tuple(sorted(properties.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": block}
            if properties:
                entry["Properties"] = dict(properties)
            palette.append(entry)
        block_entry = {"pos": [x, y, z], "state": index[key]}
        if nbt:
            block_entry["nbt"] = nbt
        entries.append(block_entry)
    root = {"size": [7, 7, 7], "entities": [], "blocks": entries, "palette": palette, "DataVersion": 4903}
    path = DATA / f"structure/{name}.nbt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(nbt_bytes(root), mtime=0))


# name: (random seed, how much of the roof is left, how tall the walls stand)
RUIN_VARIANTS = {
    "forja": (1873, 0.75, [1, 2, 3, 3, 3]),
    "forja_derrumbada": (5521, 0.25, [1, 1, 2, 2, 3]),
    "forja_en_pie": (9044, 1.0, [2, 3, 3, 3, 4]),
}


def generate_structure():
    """An abandoned forge in three states of collapse, with both tables, an anvil and a loot chest."""
    for name in RUIN_VARIANTS:
        write_ruin(name)
    generate_village_forge()
    write_json(DATA / "worldgen/template_pool/forja_abandonada/inicio.json", {
        "elements": [
            {
                "element": {"element_type": "minecraft:single_pool_element", "location": f"forja:forja_abandonada/{name}",
                            "processors": "minecraft:mossify_10_percent", "projection": "rigid"},
                "weight": 1,
            }
            for name in RUIN_VARIANTS
        ],
        "fallback": "minecraft:empty",
    })
    write_json(DATA / "worldgen/structure/forja_abandonada.json", {
        "type": "minecraft:jigsaw",
        "biomes": "#forja:has_structure/forja_abandonada",
        "max_distance_from_center": 80,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"absolute": 0},
        "start_pool": "forja:forja_abandonada/inicio",
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "use_expansion_hack": False,
    })
    write_json(DATA / "worldgen/structure_set/forja_abandonada.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 1873465021, "separation": 12, "spacing": 40,
                      "exclusion_zone": {"chunk_count": 4, "other_set": "minecraft:villages"}},
        "structures": [{"structure": "forja:forja_abandonada", "weight": 1}],
    })
    write_json(DATA / "tags/worldgen/structure/on_forja_maps.json", {"values": [
        "forja:forja_abandonada", "forja:taller_de_montana", "forja:campamento_saqueadores",
    ]})
    for name, structure in (
        ("on_forja_abandonada", "forja:forja_abandonada"),
        ("on_taller_de_montana", "forja:taller_de_montana"),
        ("on_campamento_saqueadores", "forja:campamento_saqueadores"),
        ("on_bastion_del_gremio", "forja:bastion_del_gremio"),
    ):
        write_json(DATA / f"tags/worldgen/structure/{name}.json", {"values": [structure]})
    write_json(RES / "data/minecraft/tags/point_of_interest_type/acquirable_job_site.json", {"replace": False, "values": ["forja:mesa_de_piezas"]})
    write_json(DATA / "tags/worldgen/biome/has_structure/taller_de_montana.json", {"values": [
        "minecraft:meadow", "minecraft:grove", "minecraft:snowy_slopes", "minecraft:windswept_hills",
        "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest", "minecraft:jagged_peaks",
        "minecraft:frozen_peaks", "minecraft:stony_peaks", "minecraft:cherry_grove",
    ]})
    write_json(DATA / "tags/worldgen/biome/has_structure/forja_abandonada.json", {"values": [
        "#minecraft:has_structure/village_plains", "#minecraft:has_structure/village_savanna", "#minecraft:has_structure/village_taiga",
        "#minecraft:has_structure/village_snowy", "#minecraft:has_structure/village_desert", "#minecraft:is_forest",
    ]})

    def counted(item, weight, low, high):
        return {"type": "minecraft:item", "name": item, "weight": weight,
                "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}]}

    write_json(DATA / "loot_table/chests/forja_abandonada.json", {
        "type": "minecraft:chest",
        "pools": [{"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [
            counted("minecraft:iron_ingot", 10, 1, 5),
            counted("minecraft:coal", 10, 2, 8),
            counted("minecraft:leather", 6, 1, 3),
            counted("minecraft:copper_ingot", 6, 2, 6),
            counted("minecraft:gold_ingot", 3, 1, 3),
            {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 1},
            counted("forja:plantilla", 8, 1, 3),
        ]}],
    })


def write_structure_nbt(path_name, size, blocks, entities=None):
    """Turns a block map into a structure NBT file under data/forja/structure."""
    import gzip

    palette = []
    index = {}
    entries = []
    for (x, y, z), (block, properties, nbt) in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
        key = (block, tuple(sorted(properties.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": block}
            if properties:
                entry["Properties"] = dict(properties)
            palette.append(entry)
        block_entry = {"pos": [x, y, z], "state": index[key]}
        if nbt:
            block_entry["nbt"] = nbt
        entries.append(block_entry)
    root = {"size": list(size), "entities": entities or [], "blocks": entries, "palette": palette, "DataVersion": 4903}
    path = DATA / f"structure/{path_name}.nbt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(nbt_bytes(root), mtime=0))


def write_ruin(name):
    import gzip
    import random

    seed, roof_share, wall_heights = RUIN_VARIANTS[name]
    rng = random.Random(seed)
    size = (9, 6, 9)
    blocks = {}

    def put(x, y, z, block, properties=None, nbt=None):
        blocks[(x, y, z)] = (block, properties or {}, nbt)

    for x in range(9):
        for z in range(9):
            put(x, 0, z, rng.choice(["minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:cobblestone", "minecraft:gravel", "minecraft:stone_bricks"]))
            for y in range(1, 6):
                put(x, y, z, "minecraft:air")
    walls = ["minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:stone_bricks"]
    for x in range(9):
        for z in range(9):
            if not (x in (0, 8) or z in (0, 8)):
                continue
            if x in (0, 8) and z in (0, 8):
                for y in range(1, 5):
                    put(x, y, z, "minecraft:dark_oak_log", {"axis": "y"})
                continue
            height = rng.choice(wall_heights) if z != 0 else rng.choice(wall_heights[-2:])
            for y in range(1, height + 1):
                put(x, y, z, rng.choice(walls))
    # Doorway and a window.
    for y in (1, 2, 3):
        put(4, y, 0, "minecraft:air")
    put(8, 2, 4, "minecraft:air")
    # Half of the roof survived.
    for x in range(1, 8):
        for z in range(5, 8):
            if rng.random() < roof_share:
                put(x, 4, z, "minecraft:dark_oak_slab", {"type": "bottom", "waterlogged": "false"})
    for x in range(1, 8):
        put(x, 4, 4, "minecraft:dark_oak_log", {"axis": "x"})
    put(2, 1, 6, "forja:mesa_de_forja")
    put(4, 1, 7, "forja:mesa_de_piezas")
    put(6, 1, 6, "minecraft:chipped_anvil", {"facing": "west"})
    put(7, 1, 2, "minecraft:blast_furnace", {"facing": "west", "lit": "false"})
    put(7, 2, 2, "minecraft:cobblestone_wall", {"east": "none", "north": "none", "south": "none", "up": "true", "waterlogged": "false", "west": "none"})
    put(1, 1, 2, "minecraft:chest", {"facing": "east", "type": "single", "waterlogged": "false"}, {"id": "minecraft:chest", "LootTable": "forja:chests/forja_abandonada"})
    put(1, 1, 3, "minecraft:barrel", {"facing": "up", "open": "false"}, {"id": "minecraft:barrel"})
    put(6, 1, 7, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    for x, y, z in ((1, 3, 7), (7, 3, 7), (1, 3, 1)):
        put(x, y, z, "minecraft:cobweb")

    palette = []
    index = {}
    entries = []
    for (x, y, z), (block, properties, nbt) in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
        key = (block, tuple(sorted(properties.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": block}
            if properties:
                entry["Properties"] = dict(properties)
            palette.append(entry)
        block_entry = {"pos": [x, y, z], "state": index[key]}
        if nbt:
            block_entry["nbt"] = nbt
        entries.append(block_entry)
    # The hermit smith who still works the ruin, under the half of the roof that held.
    hermit = {
        "pos": [NbtDouble(4.5), NbtDouble(1.0), NbtDouble(5.5)],
        "blockPos": [4, 1, 5],
        "nbt": {
            "id": "minecraft:villager",
            "VillagerData": {"type": "minecraft:plains", "profession": "forja:forjador", "level": 2},
            "VillagerDataFinalized": NbtByte(1),
            "Xp": 10,
            "PersistenceRequired": NbtByte(1),
            "CustomName": '{"translate":"entity.forja.villager.ermitano"}',
        },
    }
    guard = {
        "pos": [NbtDouble(6.5), NbtDouble(1.0), NbtDouble(2.5)],
        "blockPos": [6, 1, 2],
        "nbt": {"id": "forja:automata_de_forja", "PersistenceRequired": NbtByte(1)},
    }
    # The suit of plate that never left, standing in the corner where the armour was kept.
    hollow = {
        "pos": [NbtDouble(1.5), NbtDouble(1.0), NbtDouble(7.5)],
        "blockPos": [1, 1, 7],
        "nbt": {"id": "forja:coraza_vacia", "PersistenceRequired": NbtByte(1)},
    }
    # And a wisp over the hearth, which is exactly where one would sit.
    wisp = {
        "pos": [NbtDouble(4.5), NbtDouble(2.6), NbtDouble(4.5)],
        "blockPos": [4, 2, 4],
        "nbt": {"id": "forja:pavesa", "PersistenceRequired": NbtByte(1)},
    }
    root = {"size": list(size), "entities": [hermit, guard, hollow, wisp], "blocks": entries, "palette": palette, "DataVersion": 4903}
    path = DATA / f"structure/forja_abandonada/{name}.nbt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(nbt_bytes(root), mtime=0))



# ---------------------------------------------------------------------------- mob models

# Every mob is boxes, so the same three helpers build all three: one packs the UV atlas, one paints the
# boxes with the wear the mod's things always have, and one writes the geometry file.

def pack_boxes(cubes, width=128):
    """Shelf-packs each cube's box-UV footprint. Hand-numbered UVs are how models end up overlapping."""
    placed = {}
    x = row = shelf = 0
    for name, (_, size, _) in cubes.items():
        w, h, d = (int(round(value)) for value in size)
        box_w, box_h = 2 * d + 2 * w, d + h
        if x + box_w > width:
            row += shelf
            x = 0
            shelf = 0
        placed[name] = (x, row)
        x += box_w + 1
        shelf = max(shelf, box_h + 1)
    height = row + shelf
    return placed, 1 << max(6, (height - 1).bit_length())


def paint_model(cubes, palette, uvs, size, seed, wear=0.6, rust=None, damage=0.0, holed=()):
    """Paints every cube: lit top, plain sides, dark underside, then soot, scratches and rivets.

    Materials whose palette entry carries a fourth colour are emissive: they are painted bright and
    copied into the glowmask GeckoLib reads beside the texture.

    `damage` adds cracks and bitten edges to the metal and rags the hem of the cloth; the cubes named in
    `holed` get holes punched clean through them. Those pixels are left transparent, and since the model
    is drawn without back-face culling you see the inside of the box through the hole.
    """
    import random

    rng = random.Random(seed)
    skin = Image.new("RGBA", size, (0, 0, 0, 0))
    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    for name, (_, cube, material) in cubes.items():
        w, h, d = (int(round(value)) for value in cube)
        u, v = uvs[name]
        colors = palette[material]
        base, light, dark = colors[0], colors[1], colors[2]
        emissive = len(colors) > 3
        box_w, box_h = 2 * d + 2 * w, d + h
        for x in range(u, min(size[0], u + box_w)):
            for y in range(v, min(size[1], v + box_h)):
                top = y < v + d
                column = x - u
                color = light if top else base
                # The seams between the four side faces, and the lip under the box.
                if column in (0, d, d + w, 2 * d + w) or (not top and y >= v + box_h - 1):
                    color = dark
                if not top:
                    # Soot gathers low: the deeper down the face, the dirtier.
                    depth = (y - v - d) / max(1, h)
                    grime = 1.0 - wear * 0.35 * depth
                    color = tuple(int(c * grime) for c in color)
                if rng.random() < 0.06 * wear:
                    color = tuple(max(0, int(c * 0.82)) for c in color)
                elif rng.random() < 0.03 * wear:
                    color = tuple(min(255, int(c * 1.12)) for c in color)
                skin.putpixel((x, y), tuple(color) + (255,))
        # Rivets along the top and bottom of the side faces, where plates would be bolted.
        if material in ("plate", "steel", "iron", "stone", "soot") and h >= 5 and w >= 4:
            for column in range(u + 1, u + box_w - 1, 5):
                for row in (v + d + 2, v + box_h - 3):
                    if 0 <= column < size[0] and 0 <= row < size[1] and skin.getpixel((column, row))[3]:
                        skin.putpixel((column, row), tuple(min(255, int(c * 1.18)) for c in base) + (255,))
        # Rust running down from the top edge, a few streaks per box.
        # Rust bleeds a little way down from a top edge. Long bright streaks read as stripes, not rust,
        # so they are kept short and thin on purpose.
        if rust and material in ("plate", "steel", "iron", "soot"):
            for _ in range(max(1, w // 7)):
                column = u + d + rng.randrange(max(1, w))
                length = rng.randrange(2, max(3, min(h, 6)))
                for step in range(length):
                    row = v + d + step
                    if 0 <= column < size[0] and 0 <= row < size[1] and skin.getpixel((column, row))[3]:
                        mix = 1.0 - step / max(1, length)
                        pixel = skin.getpixel((column, row))
                        skin.putpixel((column, row), tuple(
                            int(pixel[i] * (1 - 0.38 * mix) + rust[i] * 0.38 * mix) for i in range(3)
                        ) + (255,))
        # Cracks that wander down a face, and a top lip that has been chipped away.
        if damage and material in ("plate", "steel", "iron", "soot", "stone"):
            for _ in range(int(round(damage * max(1, w // 6)))):
                cx = u + d + rng.randrange(max(1, w))
                cy = v + d + rng.randrange(max(1, h))
                for _ in range(rng.randrange(3, 4 + int(4 * damage))):
                    if 0 <= cx < size[0] and 0 <= cy < size[1] and skin.getpixel((cx, cy))[3]:
                        pixel = skin.getpixel((cx, cy))
                        skin.putpixel((cx, cy), tuple(int(channel * 0.42) for channel in pixel[:3]) + (255,))
                    cx += rng.choice((-1, 0, 0, 1))
                    cy += rng.choice((0, 1, 1))
            for column in range(u + d, min(size[0], u + d + w)):
                if rng.random() < 0.12 * damage:
                    for row in range(v + d, min(size[1], v + d + rng.randrange(1, 3))):
                        if skin.getpixel((column, row))[3]:
                            skin.putpixel((column, row), tuple(int(channel * 0.55) for channel in dark) + (255,))
        # Cloth does not crack, it tears: the hem comes away in threads.
        if damage and material in ("leather", "cloth"):
            for column in range(u + d, min(size[0], u + d + w)):
                for step in range(rng.randrange(0, 1 + int(3 * damage))):
                    row = v + box_h - 2 - step
                    if v <= row < size[1] and skin.getpixel((column, row))[3]:
                        skin.putpixel((column, row), (0, 0, 0, 0))
        # And what is past mending: a hole through the plate, front and back.
        if name in holed:
            for _ in range(1 + rng.randrange(2)):
                hole_w, hole_h = rng.randrange(2, 5), rng.randrange(2, 4)
                hx = rng.randrange(max(1, w - hole_w))
                hy = rng.randrange(max(1, h - hole_h))
                for face_start in (u + d, u + 2 * d + w):
                    for ox in range(hole_w):
                        for oy in range(hole_h):
                            px, py = face_start + hx + ox, v + d + hy + oy
                            if px < min(size[0], face_start + w) and py < min(size[1], v + box_h):
                                skin.putpixel((px, py), (0, 0, 0, 0))
                                glow.putpixel((px, py), (0, 0, 0, 0))
        if emissive:
            hot = colors[3]
            white = tuple(min(255, int(channel * 0.28 + 255 * 0.72)) for channel in hot)
            for x in range(u, min(size[0], u + box_w)):
                for y in range(v + d, min(size[1], v + box_h)):
                    low = (y - v - d) / max(1, h)
                    heat = min(1.0, 0.22 + low * 0.8 + (rng.random() - 0.5) * 0.36)
                    if heat > 0.88:
                        shade = white
                    else:
                        mix = heat / 0.88
                        shade = tuple(min(255, int(hot[i] * (0.28 + 0.86 * mix))) for i in range(3))
                    skin.putpixel((x, y), shade + (255,))
                    glow.putpixel((x, y), shade + (255,))
    return skin, glow


def geo_bones(cubes, tree):
    """The bone tree, with each bone's cubes looked up by name so the geometry cannot drift from the UVs."""
    uvs = tree["uvs"]

    def cube(name):
        origin, size, _ = tree["cubes"][name]
        return {"origin": origin, "size": size, "uv": list(uvs[name])}

    bones = []
    for entry in tree["bones"]:
        name, parent, pivot, members = entry[:4]
        bone = {"name": name, "pivot": pivot}
        if parent:
            bone["parent"] = parent
        if len(entry) > 4 and entry[4]:
            bone["rotation"] = list(entry[4])
        if members:
            bone["cubes"] = [cube(member) for member in members]
        bones.append(bone)
    return bones


def write_geo(identifier, cubes, bones, atlas, bounds):
    uvs, _ = pack_boxes(cubes, atlas[0])
    # The packer fills rows left to right and then starts a new one, and it will happily start a new
    # row below the bottom of the sheet. Nothing complains: the cube gets a UV that is simply not on
    # the texture, so in game it samples nothing and in a preview it draws nothing. A model can lose a
    # third of itself this way and still look like a model, which is how it goes unnoticed — so it is
    # checked here, by name, rather than left to be spotted by eye.
    spilled = []
    for name, (_, size, _) in cubes.items():
        u, v = uvs[name]
        width, height, depth = (int(round(value)) for value in size)
        if u + 2 * depth + 2 * width > atlas[0] or v + depth + height > atlas[1]:
            spilled.append(name)
    if spilled:
        raise ValueError(
            f"{identifier}: {len(spilled)} de {len(cubes)} cubos no caben en el atlas "
            f"{atlas[0]}x{atlas[1]} y quedarian sin textura ({', '.join(sorted(spilled))}). "
            f"Usa un atlas mayor."
        )
    write_json(ASSETS / f"geckolib/models/entity/{identifier}.geo.json", {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{identifier}",
                "texture_width": atlas[0],
                "texture_height": atlas[1],
                "visible_bounds_width": bounds[0],
                "visible_bounds_height": bounds[1],
                "visible_bounds_offset": [0, bounds[2], 0],
            },
            "bones": geo_bones(cubes, {"cubes": cubes, "uvs": uvs, "bones": bones}),
        }],
    })
    return uvs


def rot(frames):
    return {"rotation": {str(float(t)): list(v) for t, v in frames}}


def pos(frames):
    return {"position": {str(float(t)): list(v) for t, v in frames}}


def scale(frames):
    return {"scale": {str(float(t)): list(v) for t, v in frames}}


def stepped(frames, hold=0.07):
    """Hold-then-snap keyframes: a pose sits still and then jumps to the next one, the way a machine moves."""
    out = []
    for i, (time, value) in enumerate(frames):
        out.append((time, value))
        if i + 1 < len(frames):
            out.append((round(frames[i + 1][0] - hold, 3), value))
    return out


def bone(*parts):
    merged = {}
    for part in parts:
        merged.update(part)
    return merged


# ---------------------------------------------------------------------------- the fallen smith

# Half wrecked and twice the weight: the right side carries the hammer and an anvil-horned pauldron,
# the left side is missing plates, and the forge in his chest shows through a torso that is barely
# joined together.
BOSS_CUBES = {
    "foot_right": ([-17, 0, -9], [14, 6, 17], "soot"),
    "shin_right": ([-15, 5, -6], [12, 13, 13], "soot"),
    "knee_right": ([-14, 17, -7], [11, 6, 14], "iron"),
    "thigh_right": ([-14, 22, -6], [11, 11, 13], "plate"),
    "foot_left": ([4, 0, -9], [13, 6, 17], "soot"),
    "strut_left": ([8, 5, -3], [4, 15, 4], "iron"),
    "strut_brace": ([12, 7, -2], [3, 11, 3], "iron"),
    "thigh_left": ([6, 20, -5], [10, 12, 11], "soot"),
    "hips": ([-13, 31, -8], [25, 8, 15], "iron"),
    "spine": ([-4, 38, -3], [8, 7, 8], "iron"),
    "rib_right": ([-11, 39, -6], [4, 6, 4], "iron"),
    "chest_right": ([-18, 43, -9], [13, 18, 17], "plate"),
    "chest_left": ([5, 43, -7], [10, 11, 13], "iron"),
    "chest_back": ([-5, 41, -3], [10, 20, 11], "iron"),
    "collar": ([-15, 59, -8], [26, 5, 15], "steel"),
    "sill": ([-7, 41, -8], [13, 4, 6], "steel"),
    # The fire sits in front of the back frame, the bars in front of the fire, and the violet plate in
    # front of the bars: when he stops holding back it covers the orange one whole.
    "forge": ([-5, 44, -4], [10, 17, 1], "ember"),
    "forge_hot": ([-6, 43, -5], [12, 19, 1], "ember_hot"),
    "bar_a": ([-4, 45, -6], [2, 16, 2], "iron"),
    "bar_b": ([-1, 45, -6], [2, 16, 2], "iron"),
    "bar_c": ([2, 45, -6], [2, 16, 2], "iron"),
    # Tongues that only come out when the forge is running hot. They sit in front of the collar and of
    # his face, because anywhere behind them is inside his own plate and nothing would be seen at all.
    # They stop under his visor on purpose: a fire that covers his face costs you the one thing on him
    # you need to be able to find in a fight.
    "flame_left": ([-5, 52, -10], [3, 11, 3], "ember_hot"),
    "flame_middle": ([-1, 52, -10], [4, 11, 3], "ember_hot"),
    "flame_right": ([3, 52, -10], [3, 10, 3], "ember_hot"),
    "flame_tip": ([-5, 62, -10], [3, 6, 3], "ember_hot"),
    "flame_chimney": ([15, 74, 4], [6, 7, 6], "ember_hot"),
    "flame_chimney_tip": ([17, 80, 6], [3, 6, 3], "ember_hot"),
    # Coals behind the plates. You only ever see them through the holes punched in the plate.
    "coal_chest": ([-17, 44, -8], [11, 16, 2], "ember"),
    "coal_chest_left": ([6, 44, -6], [8, 9, 2], "ember"),
    "coal_hips": ([-12, 32, -7], [23, 6, 2], "ember"),
    "coal_thigh": ([7, 21, -4], [8, 10, 2], "ember"),
    "coal_shoulder": ([15, 55, -5], [6, 5, 2], "ember"),
    "coal_chest_hot": ([-17.5, 43.5, -8.6], [12, 17, 1], "ember_hot"),
    "coal_chest_left_hot": ([5.5, 43.5, -6.6], [9, 10, 1], "ember_hot"),
    "coal_hips_hot": ([-12.5, 31.5, -7.6], [24, 7, 1], "ember_hot"),
    "coal_thigh_hot": ([6.5, 20.5, -4.6], [9, 11, 1], "ember_hot"),
    "coal_shoulder_hot": ([14.5, 54.5, -5.6], [7, 6, 1], "ember_hot"),
    # The cloak: over the right shoulder, down the right flank, and torn off short on the left.
    "cloak_shoulder": ([-31, 53, -7], [18, 9, 22], "leather"),
    "cloak_flank": ([-30, 22, -3], [10, 32, 19], "leather"),
    "cloak_tail": ([-28, 10, 0], [10, 13, 15], "leather"),
    "cloak_back": ([-22, 24, 12], [34, 32, 3], "leather"),
    "head": ([-9, 60, -7], [16, 10, 14], "mask"),
    "helm_top": ([-10, 69, -8], [18, 4, 15], "steel"),
    "visor_eye": ([-6, 64, -8], [11, 2, 1], "ember"),
    "visor_open": ([-7, 68, -13], [13, 5, 5], "steel"),
    "horn": ([6, 72, -3], [4, 6, 4], "steel"),
    "horn_stub": ([-10, 72, -3], [4, 3, 4], "steel"),
    "arm_right": ([-25, 32, -9], [10, 22, 14], "plate"),
    "fist_right": ([-26, 22, -10], [12, 11, 16], "iron"),
    "hammer_haft": ([-23, 7, -7], [4, 16, 4], "iron"),
    "hammer_head": ([-29, 0, -12], [16, 9, 16], "steel"),
    "hammer_band": ([-30, 2, -13], [18, 4, 18], "iron"),
    "hammer_face": ([-28, 3, -14], [14, 4, 1], "ember"),
    "shoulder_left": ([14, 54, -6], [8, 7, 12], "iron"),
    "chimney": ([15, 61, 3], [6, 13, 6], "iron"),
    "chimney_cap": ([13, 73, 2], [10, 3, 8], "steel"),
    "rod_left": ([16, 38, -3], [4, 17, 4], "iron"),
    "rod_brace": ([20, 41, -2], [3, 12, 3], "iron"),
    "claw_left": ([14, 28, -5], [9, 11, 11], "iron"),
    "hook_left": ([16, 21, -3], [4, 8, 4], "iron"),
}

BOSS_PALETTE = {
    # base, light, dark, and a fourth colour for the ones that glow.
    # The values are spread wide on purpose: a mob painted all in one grey reads as a single blob.
    "soot": ((48, 45, 52), (68, 65, 74), (26, 24, 28)),
    "plate": ((104, 100, 112), (140, 136, 152), (56, 54, 64)),
    "steel": ((170, 172, 184), (208, 210, 222), (104, 106, 116)),
    "iron": ((92, 90, 98), (122, 120, 130), (52, 50, 56)),
    "leather": ((98, 58, 34), (128, 78, 46), (56, 34, 18)),
    "mask": ((70, 68, 82), (98, 96, 112), (38, 36, 46)),
    "ember": ((70, 30, 16), (96, 44, 20), (36, 14, 8), (255, 150, 56)),
    "ember_hot": ((46, 20, 66), (64, 30, 92), (24, 10, 36), (196, 96, 255)),
}

BOSS_RUST = (128, 68, 34)

# Plates that are past mending: these get holes punched clean through them, so you can see daylight
# through the smith where the fire ate its way out.
BOSS_HOLED = (
    "chest_left", "chest_right", "hips", "thigh_left", "shoulder_left", "knee_right", "cloak_tail",
)

BOSS_BONES = [
    # The fifth field is a resting rotation: he stands crooked and looks at you out of the hunch.
    ("root", None, [0, 0, 0], []),
    ("leg_right", "root", [-9, 32, 0], ["foot_right", "shin_right", "knee_right", "thigh_right"]),
    ("leg_left", "root", [10, 32, 0], ["foot_left", "strut_left", "strut_brace", "thigh_left"]),
    ("coal_thigh", "leg_left", [11, 26, -3], ["coal_thigh"]),
    ("coal_thigh_hot", "leg_left", [11, 26, -4], ["coal_thigh_hot"]),
    ("body", "root", [0, 33, 0], ["hips", "spine", "rib_right", "chest_right", "chest_left",
                                  "chest_back", "collar", "sill",
                                  "bar_a", "bar_b", "bar_c"], [5, 0, 4]),
    ("coal_chest", "body", [-11.5, 52, -7], ["coal_chest"]),
    ("coal_chest_hot", "body", [-11.5, 52, -8], ["coal_chest_hot"]),
    ("coal_chest_left", "body", [10, 48.5, -5], ["coal_chest_left"]),
    ("coal_chest_left_hot", "body", [10, 48.5, -6], ["coal_chest_left_hot"]),
    ("coal_hips", "body", [-0.5, 35, -6], ["coal_hips"]),
    ("coal_hips_hot", "body", [-0.5, 35, -7], ["coal_hips_hot"]),
    ("fire", "body", [0, 52, -3], ["forge"]),
    ("fire_hot", "body", [0, 52, -5], ["forge_hot", "flame_left", "flame_middle", "flame_right",
                                       "flame_tip", "flame_chimney", "flame_chimney_tip"]),
    # The cloak hangs off the body and swings a beat behind it.
    ("cloak", "body", [-12, 48, 4], ["cloak_shoulder", "cloak_flank", "cloak_tail", "cloak_back"]),
    ("head", "body", [0, 61, 0], ["head", "helm_top", "visor_eye", "visor_open", "horn", "horn_stub"], [-7, 0, -4]),
    ("arm_right", "body", [-21, 55, 0], ["arm_right", "fist_right", "hammer_haft", "hammer_head",
                                         "hammer_band", "hammer_face"], [0, 0, -3]),
    ("arm_left", "body", [18, 57, 0], ["shoulder_left", "rod_left", "rod_brace", "claw_left", "hook_left"], [0, 0, 4]),
    ("coal_shoulder", "arm_left", [18, 57.5, -4], ["coal_shoulder"]),
    ("coal_shoulder_hot", "arm_left", [18, 57.5, -5], ["coal_shoulder_hot"]),
    ("chimney", "body", [18, 61, 6], ["chimney", "chimney_cap"]),
]


# The coals behind his plate: each one is a pair of bones, the orange one and the violet one, and the
# fire animations scale one of each pair away. Without this the light coming through the cracks stays
# orange while the forge has gone violet, which is exactly wrong.
BOSS_COALS = ("coal_chest", "coal_chest_left", "coal_hips", "coal_thigh", "coal_shoulder")


def coals(hot, length, pulse=False):
    """Scale keyframes for every coal: `hot` picks which half of each pair is lit."""
    out = {}
    for name in BOSS_COALS:
        for bone_name, lit in ((name, not hot), (name + "_hot", hot)):
            if not lit:
                out[bone_name] = scale([(0, [0, 0, 0]), (length, [0, 0, 0])])
            elif pulse:
                out[bone_name] = scale([(0, [1, 1, 1]), (round(length * 0.3, 3), [1.14, 1.14, 1.14]),
                                        (round(length * 0.65, 3), [0.92, 0.92, 0.92]), (length, [1, 1, 1])])
            else:
                out[bone_name] = scale([(0, [1, 1, 1]), (length, [1, 1, 1])])
    return out


def generate_boss_assets():
    """The Fallen Smith: bigger, lopsided, half wrecked, and moving like something wound up rather than alive."""
    atlas = (512, 256)
    uvs = write_geo("herrero_caido", BOSS_CUBES, BOSS_BONES, atlas, (6, 7, 2.8))
    skin, glow = paint_model(BOSS_CUBES, BOSS_PALETTE, uvs, atlas, 771005, wear=0.85, rust=BOSS_RUST,
                             damage=1.0, holed=BOSS_HOLED)
    skin.save(ASSETS / "textures/entity/herrero_caido.png")
    glow.save(ASSETS / "textures/entity/herrero_caido_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/herrero_caido.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # Even standing still he works in steps: a pull of air through the forge, then nothing.
            "idle": {
                "loop": True,
                "animation_length": 5.0,
                "bones": {
                    "body": rot(stepped([(0, [2, 0, 0]), (2.5, [4, 0, 0]), (5, [2, 0, 0])], hold=0.25)),
                    "head": rot(stepped([(0, [6, -5, 0]), (1.6, [6, 5, 0]), (3.2, [8, 0, 0]), (5, [6, -5, 0])], hold=0.2)),
                    "arm_right": rot(stepped([(0, [0, 0, -4]), (2.5, [-4, 0, -4]), (5, [0, 0, -4])], hold=0.25)),
                    "cloak": rot([(0, [0, 0, 1]), (2.5, [0, 0, -2]), (5, [0, 0, 1])]),
                },
            },
            # The walk of something heavy: a step, a pause, and the weight coming down.
            "walk": {
                "loop": True,
                "animation_length": 2.0,
                "bones": {
                    "leg_right": rot(stepped([(0, [-20, 0, 0]), (1.0, [20, 0, 0]), (2.0, [-20, 0, 0])])),
                    "leg_left": rot(stepped([(0, [20, 0, 0]), (1.0, [-20, 0, 0]), (2.0, [20, 0, 0])])),
                    "body": bone(
                        rot(stepped([(0, [4, -4, 0]), (1.0, [4, 4, 0]), (2.0, [4, -4, 0])])),
                        pos(stepped([(0, [0, 0, 0]), (1.0, [0, 1.5, 0]), (2.0, [0, 0, 0])])),
                    ),
                    "arm_right": rot(stepped([(0, [14, 0, -4]), (1.0, [-8, 0, -4]), (2.0, [14, 0, -4])])),
                    "arm_left": rot(stepped([(0, [-16, 0, 4]), (1.0, [16, 0, 4]), (2.0, [-16, 0, 4])])),
                    "cloak": rot([(0, [3, 0, 0]), (1.0, [-3, 0, 0]), (2.0, [3, 0, 0])]),
                },
            },
            # The hammer: wound back slowly, held there for as long as the ring on the floor takes to
            # fill, and dropped in four ticks. It LANDS AT 2.7 s — FallenSmith.WAVE_WINDUP, 54 ticks.
            # The windup was tripled once and this was left at its old 0.9 s, so the hammer hit the
            # floor and the ring left it nearly two seconds later. Change one, change the other.
            "slam": {
                "loop": False,
                "animation_length": 3.7,
                "bones": {
                    "arm_right": rot([(0, [0, 0, -4]), (1.2, [-116, 0, -10]), (2.5, [-130, 0, -10]),
                                      (2.7, [55, 0, 0]), (3.05, [40, 0, 0]), (3.7, [0, 0, -4])]),
                    "body": rot([(0, [2, 0, 0]), (1.2, [-12, 0, 0]), (2.5, [-17, 0, 0]),
                                 (2.7, [26, 0, 0]), (3.05, [20, 0, 0]), (3.7, [2, 0, 0])]),
                    "head": rot([(0, [6, 0, 0]), (1.2, [-3, 0, 0]), (2.5, [-7, 0, 0]), (2.7, [22, 0, 0]), (3.7, [6, 0, 0])]),
                    "arm_left": rot([(0, [0, 0, 4]), (1.2, [-12, 0, 12]), (2.5, [-18, 0, 15]),
                                     (2.7, [-30, 0, 20]), (3.7, [0, 0, 4])]),
                },
            },
            # The basic blow: measured, almost polite next to the rest of him. A short swing of the
            # hammer, the body turning into it, and back to where he was.
            "strike": {
                "loop": False,
                "animation_length": 0.9,
                "bones": {
                    "arm_right": rot(stepped([(0, [0, 0, -3]), (0.25, [-52, 0, -8]), (0.5, [34, 0, 0]),
                                              (0.9, [0, 0, -3])], hold=0.06)),
                    "body": rot(stepped([(0, [5, 0, 4]), (0.25, [2, 10, 4]), (0.5, [9, -8, 4]),
                                         (0.9, [5, 0, 4])], hold=0.06)),
                    "head": rot([(0, [-7, 0, -4]), (0.5, [0, -6, -4]), (0.9, [-7, 0, -4])]),
                    "cloak": rot([(0, [0, 0, 1]), (0.4, [4, 0, -6]), (0.9, [0, 0, 1])]),
                },
            },
            # The hook: the dead arm comes up, the claw is thrown, and the chain hauls it back.
            "hook": {
                "loop": False,
                "animation_length": 1.1,
                "bones": {
                    "arm_left": rot(stepped([(0, [0, 0, 4]), (0.2, [-40, 0, 20]), (0.4, [-96, 0, 6]),
                                             (0.75, [-70, 0, 10]), (1.1, [0, 0, 4])], hold=0.05)),
                    "body": rot(stepped([(0, [5, 0, 4]), (0.4, [3, -14, 4]), (0.75, [7, 6, 4]),
                                         (1.1, [5, 0, 4])], hold=0.05)),
                    "head": rot([(0, [-7, 0, -4]), (0.4, [-10, -10, -4]), (1.1, [-7, 0, -4])]),
                },
            },
            # Not a roar: the whole frame straightens up, holds, and falls back into its hunch.
            "roar": {
                "loop": False,
                "animation_length": 2.2,
                "bones": {
                    "body": rot(stepped([(0, [2, 0, 0]), (0.5, [-16, 0, 0]), (1.7, [-16, 0, 0]), (2.2, [2, 0, 0])], hold=0.12)),
                    "head": rot(stepped([(0, [6, 0, 0]), (0.5, [-34, 0, 0]), (1.7, [-30, 0, 0]), (2.2, [6, 0, 0])], hold=0.12)),
                    "arm_right": rot(stepped([(0, [0, 0, -4]), (0.5, [-46, 0, -38]), (1.7, [-42, 0, -42]), (2.2, [0, 0, -4])], hold=0.12)),
                    "arm_left": rot(stepped([(0, [0, 0, 4]), (0.5, [-46, 0, 38]), (1.7, [-42, 0, 42]), (2.2, [0, 0, 4])], hold=0.12)),
                },
            },
            # The fire while he is still holding back: it breathes, and the violet is not there at all.
            "fire_calm": {
                "loop": True,
                "animation_length": 3.2,
                "bones": {
                    "fire": bone(
                        scale([(0, [1, 1, 1]), (0.8, [1.06, 1.1, 1]), (1.7, [0.93, 0.9, 1]),
                               (2.5, [1.04, 1.06, 1]), (3.2, [1, 1, 1])]),
                        pos([(0, [0, 0, 0]), (1.7, [0, 0.3, 0]), (3.2, [0, 0, 0])]),
                    ),
                    "fire_hot": scale([(0, [0, 0, 0]), (3.2, [0, 0, 0])]),
                    **coals(False, 3.2, pulse=True),
                },
            },
            # And when he stops holding back: the orange goes out, the violet takes the whole opening
            # and the tongues come up out of the forge.
            "fire_rage": {
                "loop": True,
                "animation_length": 1.4,
                "bones": {
                    "fire": scale([(0, [0.1, 0.1, 1]), (1.4, [0.1, 0.1, 1])]),
                    "fire_hot": bone(
                        scale([(0, [1, 1, 1]), (0.35, [1.1, 1.22, 1]), (0.7, [0.96, 0.88, 1]),
                               (1.05, [1.08, 1.16, 1]), (1.4, [1, 1, 1])]),
                        pos([(0, [0, 0, 0]), (0.7, [0, 0.6, 0]), (1.4, [0, 0, 0])]),
                    ),
                    **coals(True, 1.4, pulse=True),
                },
            },
            # One flare, for the moment a blow lands. There are two of them because the flash has to hit
            # whichever fire is burning: flaring the orange bone while it is scaled away shows nothing.
            "fire_flash": {
                "loop": False,
                "animation_length": 0.6,
                "bones": {
                    "fire": bone(
                        scale([(0, [1, 1, 1]), (0.12, [1.5, 1.45, 1]), (0.6, [1, 1, 1])]),
                        pos([(0, [0, 0, 0]), (0.12, [0, 0.8, 0]), (0.6, [0, 0, 0])]),
                    ),
                    "fire_hot": scale([(0, [0, 0, 0]), (0.6, [0, 0, 0])]),
                    **coals(False, 0.6, pulse=True),
                },
            },
            "fire_flash_hot": {
                "loop": False,
                "animation_length": 0.6,
                "bones": {
                    "fire": scale([(0, [0.1, 0.1, 1]), (0.6, [0.1, 0.1, 1])]),
                    "fire_hot": bone(
                        scale([(0, [1, 1, 1]), (0.12, [1.45, 1.5, 1]), (0.6, [1, 1, 1])]),
                        pos([(0, [0, 0, 0]), (0.12, [0, 1.2, 0]), (0.6, [0, 0, 0])]),
                    ),
                    **coals(True, 0.6, pulse=True),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the fallen forge
def generate_forge_heart_texture():
    """The forge heart: a lump of iron with a fire in it that never went out."""
    heart = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 8.0
            d = (dx * dx + dy * dy * 0.85) ** 0.5
            if d > 6.6:
                continue
            if d > 5.4:
                heart.putpixel((x, y), (46, 38, 42, 255))
            elif d > 3.2:
                shade = int(150 - d * 10)
                heart.putpixel((x, y), (shade, int(shade * 0.5), int(shade * 0.35), 255))
            else:
                # The fire in the middle, brightest at the centre.
                glow = 1.0 - d / 3.4
                heart.putpixel((x, y), (255, int(120 + 110 * glow), int(40 + 90 * glow), 255))
    # A crack of light running out of it.
    for y, x in ((3, 7), (4, 7), (5, 8), (12, 7), (13, 7)):
        heart.putpixel((x, y), (255, 214, 140, 255))
    heart.save(ASSETS / "textures/item/corazon_de_forja.png")


def generate_smith_anvil_assets():
    """The anvil of the fallen smith: four boxes, blackened steel, and the heat still in its face.

    The side texture is drawn in three bands because the model takes it in three bands: the top six
    rows are the collar of the face, the middle six are the waist, and the bottom four are the foot.
    Draw it that way and one sixteen-pixel sheet dresses the whole anvil without a seam showing.
    """
    rng = __import__("random").Random(551907)
    STEEL = (58, 54, 62)
    STEEL_L = (104, 100, 112)
    STEEL_D = (30, 27, 34)
    GOLD = (176, 140, 62)
    GOLD_L = (226, 190, 104)
    GOLD_D = (106, 82, 34)

    side = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            # Three bands, each a shade apart, so the boxes of the model read as one piece of iron.
            base = STEEL if y < 6 else (tuple(int(c * 1.18) for c in STEEL) if y < 12 else STEEL_D)
            grain = rng.randint(-8, 8)
            side.putpixel((x, y), tuple(max(0, min(255, c + grain)) for c in base) + (255,))
    # ---- rows 0-5: the collar of the face, which is the only part of it anyone looks at.
    for x in range(16):
        side.putpixel((x, 0), GOLD_L + (255,))
        side.putpixel((x, 1), GOLD + (255,))
        side.putpixel((x, 2), GOLD_D + (255,))
    for x in range(1, 16, 4):
        side.putpixel((x, 4), STEEL_L + (255,))
        side.putpixel((x, 5), STEEL_D + (255,))
    # The crack the smith's own hammer left in it, with the soul light still down inside.
    for step, y in enumerate(range(3, 6)):
        side.putpixel((9 + step % 2, y), (88, 170, 186, 255))
    # ---- rows 6-11: the waist, worn smooth.
    for y in range(6, 12):
        side.putpixel((0, y), STEEL_D + (255,))
        side.putpixel((15, y), STEEL_D + (255,))
    for x in range(2, 15, 3):
        side.putpixel((x, 8), tuple(int(c * 0.80) for c in STEEL) + (255,))
    # ---- rows 12-15: the foot, and the line where it meets the floor.
    for x in range(16):
        side.putpixel((x, 12), STEEL_L + (255,))
        side.putpixel((x, 15), (18, 16, 20, 255))

    top = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            grain = rng.randint(-7, 7)
            top.putpixel((x, y), tuple(max(0, c + grain) for c in STEEL) + (255,))
    # Gold inlaid at both ends of the face: the master's anvil, and the reason it is a trophy.
    for z in (0, 1, 14, 15):
        for x in range(16):
            top.putpixel((x, z), (GOLD_L if z < 2 else GOLD_D) + (255,))
    # The working face, polished by a lifetime of use and still warm in the middle of it.
    for y in range(3, 13):
        for x in range(2, 14):
            heat = 1.0 - (((x - 7.5) / 6.0) ** 2 + ((y - 7.5) / 5.0) ** 2) ** 0.5
            if heat <= 0:
                continue
            polish = 86 + int(70 * heat) + rng.randint(-6, 6)
            top.putpixel((x, y), (polish + int(60 * heat), polish - int(10 * heat), polish - int(24 * heat), 255))
    # The hardy hole, square, and the pritchel hole beside it, round: the two holes every anvil has.
    for y in range(6, 9):
        for x in range(4, 7):
            top.putpixel((x, y), (16, 14, 18, 255))
    for x in range(4, 7):
        top.putpixel((x, 5), STEEL_D + (255,))
    for (x, y) in ((10, 7), (11, 7), (10, 8), (11, 8)):
        top.putpixel((x, y), (16, 14, 18, 255))
    top.putpixel((10, 6), STEEL_D + (255,))
    top.putpixel((11, 6), STEEL_D + (255,))

    folder = ASSETS / "textures/block"
    folder.mkdir(parents=True, exist_ok=True)
    top.save(folder / "yunque_del_herrero_top.png")
    side.save(folder / "yunque_del_herrero_side.png")

    # The model: a foot, two steps of waist, and a face laid along Z. The UVs are picked by hand so
    # each box takes the band of the sheet that was drawn for it.
    def faces(uv_side, uv_end, uv_flat, top_face=None):
        out = {}
        for name in ("north", "south"):
            out[name] = {"uv": uv_end, "texture": "#body"}
        for name in ("east", "west"):
            out[name] = {"uv": uv_side, "texture": "#body"}
        out["down"] = {"uv": uv_flat, "texture": "#body"}
        out["up"] = top_face or {"uv": uv_flat, "texture": "#body"}
        return out

    write_json(ASSETS / "models/block/yunque_del_herrero.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "body": "forja:block/yunque_del_herrero_side",
            "top": "forja:block/yunque_del_herrero_top",
            "particle": "forja:block/yunque_del_herrero_side",
        },
        "elements": [
            # The foot.
            {"from": [2, 0, 2], "to": [14, 4, 14],
             "faces": faces([2, 12, 14, 16], [2, 12, 14, 16], [2, 2, 14, 14])},
            # The two steps that lift the face off it.
            {"from": [4, 4, 3], "to": [12, 5, 13],
             "faces": faces([3, 11, 13, 12], [4, 11, 12, 12], [4, 3, 12, 13])},
            {"from": [6, 5, 4], "to": [10, 10, 12],
             "faces": faces([4, 6, 12, 11], [6, 6, 10, 11], [6, 4, 10, 12])},
            # The face itself, which is the whole point of an anvil.
            {"from": [3, 10, 0], "to": [13, 16, 16],
             "faces": faces([0, 0, 16, 6], [3, 0, 13, 6], [3, 6, 13, 12],
                            top_face={"uv": [0, 0, 16, 16], "texture": "#top"})},
        ],
    })
    write_json(ASSETS / "blockstates/yunque_del_herrero.json", {"variants": {
        "facing=north": {"model": "forja:block/yunque_del_herrero"},
        "facing=east": {"model": "forja:block/yunque_del_herrero", "y": 90},
        "facing=south": {"model": "forja:block/yunque_del_herrero", "y": 180},
        "facing=west": {"model": "forja:block/yunque_del_herrero", "y": 270},
    }})
    write_json(ASSETS / "items/yunque_del_herrero.json", {"model": {"type": "minecraft:model", "model": "forja:block/yunque_del_herrero"}})
    write_json(DATA / "loot_table/blocks/yunque_del_herrero.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "forja:yunque_del_herrero"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "forja:blocks/yunque_del_herrero",
    })


def generate_wisp_lantern_assets():
    """The wisp lantern: a cage with something alive in it, which is the whole point of the block.

    The old one was a wall of fire with four lines drawn over it, and it read as a torch. What makes
    a cage read as a cage is that the frame is the solid thing and the light is behind it: a heavy
    iron corner post at each edge, bars across the opening, and the wisp itself small, bright and
    floating in the middle of a dark box rather than filling it.
    """
    rng = __import__("random").Random(410923)
    IRON = (78, 76, 82)
    IRON_L = (138, 136, 146)
    IRON_D = (38, 36, 40)

    side = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            frame = x < 2 or x > 13 or y < 2 or y > 13
            if frame:
                grain = rng.randint(-8, 8)
                lit_edge = y < 2 or x < 2
                base = IRON_L if lit_edge else IRON
                side.putpixel((x, y), tuple(max(0, c + grain) for c in base) + (255,))
            else:
                side.putpixel((x, y), (20, 17, 22, 255))
    # The wisp: a small hot body low in the cage, with the air glowing around it.
    cx, cy = 7.5, 9.0
    for y in range(2, 14):
        for x in range(2, 14):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < 1.8:
                side.putpixel((x, y), (255, 246, 216, 255))
            elif d < 3.0:
                side.putpixel((x, y), (255, 176, 66, 255))
            elif d < 4.4:
                fade = 1.0 - (d - 3.0) / 1.4
                side.putpixel((x, y), (int(120 + 90 * fade), int(52 + 60 * fade), int(28 + 20 * fade), 255))
    # Sparks coming off it, which is how you know it is not a coal.
    for (x, y) in ((5, 5), (10, 4), (11, 7), (4, 11)):
        side.putpixel((x, y), (255, 214, 128, 255))
    # The bars, drawn last so the light is always behind them.
    for x in (4, 7, 10, 13):
        for y in range(2, 14):
            side.putpixel((x, y), IRON_D + (255,))
            if x + 1 < 14:
                side.putpixel((x + 1, y), (58, 56, 62, 255))
    # And the two hoops that hold the bars top and bottom.
    for y in (2, 13):
        for x in range(2, 14):
            side.putpixel((x, y), (IRON_L if y == 2 else IRON_D) + (255,))

    top = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            grain = rng.randint(-7, 7)
            d = max(abs(x - 7.5), abs(y - 7.5))
            base = IRON_L if d >= 6 else IRON
            top.putpixel((x, y), tuple(max(0, c + grain) for c in base) + (255,))
    # The ring on the lid, where the hook went.
    for y in range(5, 11):
        for x in range(5, 11):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if 1.5 < d <= 2.5:
                top.putpixel((x, y), (IRON_L if x + y < 15 else IRON_D) + (255,))
            elif d <= 1.5:
                top.putpixel((x, y), IRON_D + (255,))
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        top.putpixel((x, y), IRON_L + (255,))
        top.putpixel((x, y + 1 if y < 8 else y - 1), IRON_D + (255,))

    folder = ASSETS / "textures/block"
    folder.mkdir(parents=True, exist_ok=True)
    side.save(folder / "farol_de_pavesa_side.png")
    top.save(folder / "farol_de_pavesa_top.png")
    write_json(ASSETS / "models/block/farol_de_pavesa.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "forja:block/farol_de_pavesa_top",
            "bottom": "forja:block/farol_de_pavesa_top",
            "side": "forja:block/farol_de_pavesa_side",
            "particle": "forja:block/farol_de_pavesa_side",
        },
    })
    write_json(ASSETS / "blockstates/farol_de_pavesa.json", {"variants": {"": {"model": "forja:block/farol_de_pavesa"}}})
    write_json(ASSETS / "items/farol_de_pavesa.json", {"model": {"type": "minecraft:model", "model": "forja:block/farol_de_pavesa"}})
    write_json(DATA / "loot_table/blocks/farol_de_pavesa.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "forja:farol_de_pavesa"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "forja:blocks/farol_de_pavesa",
    })


def generate_dead_forge_assets():
    """The dead forge: the same hearth as every other hot block, with the fire gone out of it.

    It is drawn from the same kit as the crucibles on purpose. A smith who has seen a crucible knows
    at a glance what this is and what is wrong with it: the bowl is full of ash instead of metal, and
    the mouth underneath is cold. One ember is left in it, which is the reason the block exists.
    """
    rng = __import__("random").Random(20260917)
    STONE = (46, 42, 50)
    GOLD = (146, 116, 54)
    GOLD_L = (198, 162, 82)
    GOLD_D = (88, 68, 32)

    side = forge_grain(Image.new("RGBA", (16, 16)), STONE, rng, 12)
    # Gold at the lip, gone dull: this was the smith's own forge before it went out.
    forge_lip(side, GOLD_L, GOLD, GOLD_D)
    for x in range(16):
        side.putpixel((x, 10), GOLD_D + (255,))
    forge_rivets(side, 5, (86, 80, 92), (26, 24, 30), step=6, start=2)
    # The crack that runs down it, with the last of the light behind it.
    for y in range(3, 11):
        x = 6 + (y % 3)
        side.putpixel((x, y), (96, 40, 18, 255))
        side.putpixel((x + 1, y), (40, 22, 16, 255))
    # The mouth, cold, with the ash that fell out of it banked at the foot.
    forge_mouth(side, False, rng, top=11, bars=(5, 8, 11))
    for x in range(3, 13):
        if rng.random() < 0.7:
            side.putpixel((x, 15), (FORGE_ASH_D if rng.random() < 0.5 else FORGE_ASH) + (255,))

    top = forge_bowl(Image.new("RGBA", (16, 16)), GOLD, STONE, (58, 54, 58), rng)
    # A bed of ash with the coals dead in it, and one ember that never went out.
    for y in range(4, 12):
        for x in range(4, 12):
            if max(abs(x - 7.5), abs(y - 7.5)) < 4:
                roll = rng.random()
                if roll < 0.34:
                    top.putpixel((x, y), FORGE_COAL + (255,))
                elif roll < 0.62:
                    top.putpixel((x, y), FORGE_ASH_D + (255,))
                else:
                    top.putpixel((x, y), FORGE_ASH + (255,))
    for (x, y) in ((7, 8), (8, 8), (8, 9)):
        top.putpixel((x, y), (188, 74, 26, 255))
    top.putpixel((7, 9), (255, 150, 56, 255))
    # The grate the coals sit on, which is what keeps it from reading as a hole full of gravel.
    for x in (5, 8, 11):
        for y in range(4, 12):
            if max(abs(x - 7.5), abs(y - 7.5)) < 4:
                top.putpixel((x, y), (32, 30, 34, 255))

    folder = ASSETS / "textures/block"
    folder.mkdir(parents=True, exist_ok=True)
    top.save(folder / "fragua_apagada_top.png")
    side.save(folder / "fragua_apagada_side.png")
    write_json(ASSETS / "models/block/fragua_apagada.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "forja:block/fragua_apagada_top",
            "bottom": "forja:block/fragua_apagada_side",
            "side": "forja:block/fragua_apagada_side",
            "particle": "forja:block/fragua_apagada_side",
        },
    })
    write_json(ASSETS / "blockstates/fragua_apagada.json", {"variants": {"": {"model": "forja:block/fragua_apagada"}}})
    write_json(ASSETS / "items/fragua_apagada.json", {"model": {"type": "minecraft:model", "model": "forja:block/fragua_apagada"}})
    write_json(DATA / "loot_table/blocks/fragua_apagada.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "forja:fragua_apagada"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "forja:blocks/fragua_apagada",
    })
    # The pickaxe tag is not written here any more. It was, and then written again further down with a
    # different list, and the second write won: see PICKAXE_BLOCKS.
    write_json(RES / "data/minecraft/tags/block/needs_diamond_tool.json", {"replace": False, "values": ["forja:fragua_apagada", "forja:yunque_del_herrero"]})


def write_fallen_forge():
    """The fortress of the fallen smith: blackstone walls, lava channels and his own forge in the middle."""
    import gzip
    import random

    rng = random.Random(770913)
    size = (15, 10, 15)
    blocks = {}

    def put(x, y, z, block, properties=None, nbt=None):
        blocks[(x, y, z)] = (block, properties or {}, nbt)

    floor = ["minecraft:blackstone", "minecraft:polished_blackstone", "minecraft:basalt", "minecraft:blackstone", "minecraft:gilded_blackstone"]
    walls = ["minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks", "minecraft:polished_blackstone", "minecraft:blackstone"]
    for x in range(15):
        for z in range(15):
            put(x, 0, z, rng.choice(floor))
            for y in range(1, 10):
                put(x, y, z, "minecraft:air")
    # Outer walls, broken open on one side so the place can be walked into.
    for x in range(15):
        for z in range(15):
            if not (x in (0, 14) or z in (0, 14)):
                continue
            height = 6 if (x in (0, 14) and z in (0, 14)) else rng.choice([3, 4, 5, 5, 6])
            if z == 14 and 5 <= x <= 9:
                height = 0
            for y in range(1, height + 1):
                put(x, y, z, rng.choice(walls))
    # Lava channels along the inside of the walls, which is what makes the place workable.
    for i in range(2, 13):
        put(i, 0, 2, "minecraft:lava", {"level": "0"})
        put(i, 0, 12, "minecraft:lava", {"level": "0"})
        put(2, 0, i, "minecraft:lava", {"level": "0"})
        put(12, 0, i, "minecraft:lava", {"level": "0"})
    # The altar: an obsidian platform with the dead forge on top, under a broken arch.
    for x in range(5, 10):
        for z in range(5, 10):
            put(x, 1, z, "minecraft:obsidian" if (x + z) % 2 == 0 else "minecraft:crying_obsidian")
    put(7, 2, 7, "forja:fragua_apagada")
    for x in (5, 9):
        for z in (5, 9):
            for y in range(2, 5):
                put(x, y, z, "minecraft:polished_blackstone_brick_wall")
            put(x, 5, z, "minecraft:soul_lantern", {"hanging": "false"})
    # His tools, still where he left them: both tables and an anvil.
    put(6, 2, 5, "minecraft:anvil", {"facing": "south"})
    put(8, 2, 5, "forja:mesa_de_forja")
    put(6, 2, 9, "forja:mesa_de_piezas")
    put(8, 2, 9, "minecraft:smithing_table")
    # Two chests, with what he was keeping.
    for pos, facing in (((3, 1, 7), "east"), ((11, 1, 7), "west")):
        put(*pos, "minecraft:chest", {"facing": facing, "type": "single"},
            {"LootTable": "forja:chests/forja_abandonada"})
    # Braziers of soul fire around the altar, for the light and for the warning.
    for x, z in ((4, 4), (10, 4), (4, 10), (10, 10)):
        put(x, 1, z, "minecraft:soul_campfire", {"lit": "true", "facing": "north", "signal_fire": "false", "waterlogged": "false"})
    # Two automatons still minding the place, one on each side of the altar.
    guards = [
        {
            "pos": [NbtDouble(3.5), NbtDouble(1.0), NbtDouble(3.5)],
            "blockPos": [3, 1, 3],
            "nbt": {"id": "forja:automata_de_forja", "PersistenceRequired": NbtByte(1)},
        },
        {
            "pos": [NbtDouble(11.5), NbtDouble(1.0), NbtDouble(11.5)],
            "blockPos": [11, 1, 11],
            "nbt": {"id": "forja:automata_de_forja", "PersistenceRequired": NbtByte(1)},
        },
        {
            "pos": [NbtDouble(7.5), NbtDouble(1.0), NbtDouble(13.5)],
            "blockPos": [7, 1, 13],
            "nbt": {"id": "forja:coraza_vacia", "PersistenceRequired": NbtByte(1)},
        },
        {
            "pos": [NbtDouble(7.5), NbtDouble(1.0), NbtDouble(1.5)],
            "blockPos": [7, 1, 1],
            "nbt": {"id": "forja:coraza_vacia", "PersistenceRequired": NbtByte(1)},
        },
        # Three wisps over the lava, which is the whole reason they are here and why they are fed.
        {
            "pos": [NbtDouble(5.5), NbtDouble(3.2), NbtDouble(7.5)],
            "blockPos": [5, 3, 7],
            "nbt": {"id": "forja:pavesa", "PersistenceRequired": NbtByte(1)},
        },
        {
            "pos": [NbtDouble(9.5), NbtDouble(3.2), NbtDouble(7.5)],
            "blockPos": [9, 3, 7],
            "nbt": {"id": "forja:pavesa", "PersistenceRequired": NbtByte(1)},
        },
        {
            "pos": [NbtDouble(7.5), NbtDouble(4.0), NbtDouble(9.5)],
            "blockPos": [7, 4, 9],
            "nbt": {"id": "forja:pavesa", "PersistenceRequired": NbtByte(1)},
        },
    ]
    write_structure_nbt("fragua_caida/fragua", size, blocks, guards)


def generate_fallen_forge():
    generate_dead_forge_assets()
    generate_smith_anvil_assets()
    generate_wisp_lantern_assets()
    generate_ember_texture()
    generate_slag_texture()
    generate_crucible_assets()
    generate_melt_tank_assets()
    write_fallen_forge()
    write_json(DATA / "worldgen/template_pool/fragua_caida/inicio.json", {
        "elements": [{
            "element": {"element_type": "minecraft:single_pool_element", "location": "forja:fragua_caida/fragua",
                        "processors": "minecraft:empty", "projection": "rigid"},
            "weight": 1,
        }],
        "fallback": "minecraft:empty",
    })
    write_json(DATA / "worldgen/structure/fragua_caida.json", {
        "type": "minecraft:jigsaw",
        "biomes": "#minecraft:is_nether",
        "max_distance_from_center": 80,
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 34}, "max_inclusive": {"absolute": 60}},
        "start_pool": "forja:fragua_caida/inicio",
        "step": "underground_decoration",
        "terrain_adaptation": "beard_thin",
        "use_expansion_hack": False,
    })
    write_json(DATA / "worldgen/structure_set/fragua_caida.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 665110394, "separation": 10, "spacing": 32},
        "structures": [{"structure": "forja:fragua_caida", "weight": 1}],
    })



# ---------------------------------------------------------------------------- mounts
def split_horse_armor(state):
    """Forja's barding from the vanilla sprite: the metal is the plate, the straps the lining."""
    pixels = opaque(vanilla("item/iron_horse_armor.png"))
    return {p: (0 if luminance(c) >= 70 else 1, luminance(c)) for p, c in pixels.items()}


def split_wolf_armor(state):
    """The wolf harness: the scute is the plate and the darker leather the lining."""
    pixels = opaque(vanilla("item/wolf_armor.png"))
    return {p: (0 if luminance(c) >= 80 else 1, luminance(c)) for p, c in pixels.items()}


def generate_mount_textures():
    """One grey barding and one grey harness; each plate material dyes them, the way the armor does."""
    for kind, folder, source in (
        ("barda", "horse_body", "assets/minecraft/textures/entity/equipment/horse_body/iron.png"),
        ("lobo", "wolf_body", "assets/minecraft/textures/entity/equipment/wolf_body/armadillo_scute.png"),
    ):
        try:
            worn = Image.open(io.BytesIO(jar_read(source))).convert("RGBA")
        except KeyError:
            continue
        grey = Image.new("RGBA", worn.size, (0, 0, 0, 0))
        for y in range(worn.height):
            for x in range(worn.width):
                r, g, b, a = worn.getpixel((x, y))
                if a:
                    value = min(255, int(luminance((r, g, b, a)) * 1.7))
                    grey.putpixel((x, y), (value, value, value, a))
        out = ASSETS / f"textures/entity/equipment/{folder}"
        out.mkdir(parents=True, exist_ok=True)
        grey.save(out / f"{kind}.png")
        for material, color in MATERIAL_COLORS.items():
            write_json(ASSETS / f"equipment/{kind}_{material}.json", {
                "layers": {folder: [{"texture": f"forja:{kind}", "dyeable": {"color_when_undyed": color}}]},
            })


def generate_saddlery_assets():
    """The saddlery: the forge table in leather and brass, so it reads as the place for barding."""
    write_json(ASSETS / "items/mesa_de_talabarteria.json", {"model": {"type": "minecraft:model", "model": "forja:block/mesa_de_talabarteria"}})
    write_json(ASSETS / "blockstates/mesa_de_talabarteria.json", {"variants": {"": {"model": "forja:block/mesa_de_talabarteria"}}})
    write_json(ASSETS / "models/block/mesa_de_talabarteria.json", {
        "parent": "minecraft:block/cube",
        "textures": {
            "down": "minecraft:block/polished_deepslate",
            "up": "forja:block/mesa_de_talabarteria_top",
            "north": "forja:block/mesa_de_talabarteria_front",
            "south": "forja:block/mesa_de_talabarteria_front",
            "east": "forja:block/mesa_de_talabarteria_side",
            "west": "forja:block/mesa_de_talabarteria_side",
            "particle": "forja:block/mesa_de_talabarteria_side",
        },
    })

def generate_mount_assets():
    generate_mount_textures()
    generate_saddlery_assets()



# ---------------------------------------------------------------------------- the forge automaton

# A furnace that learned to walk: barrel body, a grated door with the fire behind it, a chimney on the
# shoulder and arms that move on pistons.
AUTOMATON_CUBES = {
    "leg_right": ([-7, 0, -4], [6, 8, 8], "iron"),
    "leg_left": ([1, 0, -4], [6, 8, 8], "iron"),
    "hips": ([-8, 6, -5], [16, 4, 10], "stone"),
    "belly": ([-9, 9, -6], [18, 9, 12], "stone"),
    "chest": ([-8, 18, -5], [16, 8, 10], "stone"),
    "door": ([-6, 11, -7], [12, 7, 1], "ember"),
    "bar_right": ([-5, 11, -8], [2, 7, 1], "iron"),
    "bar_middle": ([-1, 11, -8], [2, 7, 1], "iron"),
    "bar_left": ([3, 11, -8], [2, 7, 1], "iron"),
    "head": ([-4, 26, -4], [8, 6, 8], "iron"),
    "lens": ([-2, 28, -5], [4, 2, 1], "lens"),
    "chimney": ([7, 24, -2], [4, 9, 4], "iron"),
    "chimney_cap": ([6, 32, -3], [6, 2, 6], "stone"),
    "arm_right_upper": ([-13, 17, -3], [5, 7, 6], "stone"),
    "piston_right": ([-12, 13, -2], [3, 5, 3], "iron"),
    "arm_right_lower": ([-14, 4, -4], [6, 9, 7], "stone"),
    "fist_right": ([-16, 0, -5], [9, 5, 9], "iron"),
    "arm_left_upper": ([8, 17, -3], [5, 7, 6], "stone"),
    "piston_left": ([9, 13, -2], [3, 5, 3], "iron"),
    "arm_left_lower": ([8, 4, -4], [6, 9, 7], "stone"),
}

AUTOMATON_PALETTE = {
    "stone": ((118, 116, 112), (150, 148, 144), (76, 74, 72)),
    "iron": ((156, 158, 166), (194, 196, 204), (100, 102, 110)),
    "ember": ((70, 32, 14), (94, 44, 18), (34, 16, 6), (255, 152, 56)),
    "lens": ((26, 60, 64), (36, 80, 84), (16, 36, 38), (150, 232, 238)),
}

AUTOMATON_RUST = (120, 74, 40)

AUTOMATON_BONES = [
    ("root", None, [0, 0, 0], []),
    ("leg_right", "root", [-4, 8, 0], ["leg_right"]),
    ("leg_left", "root", [4, 8, 0], ["leg_left"]),
    ("body", "root", [0, 9, 0], ["hips", "belly", "chest", "door", "bar_right", "bar_middle", "bar_left",
                                 "chimney", "chimney_cap"]),
    ("head", "body", [0, 26, 0], ["head", "lens"]),
    ("arm_right", "body", [-10, 24, 0], ["arm_right_upper", "piston_right", "arm_right_lower", "fist_right"]),
    ("arm_left", "body", [10, 24, 0], ["arm_left_upper", "piston_left", "arm_left_lower"]),
]


def generate_automaton_assets():
    """The automaton: the same machine, built properly, and moving as slowly as its weight deserves."""
    atlas = (128, 128)
    uvs = write_geo("automata_de_forja", AUTOMATON_CUBES, AUTOMATON_BONES, atlas, (3, 4, 1.5))
    skin, glow = paint_model(AUTOMATON_CUBES, AUTOMATON_PALETTE, uvs, atlas, 330114, wear=0.7, rust=AUTOMATON_RUST)
    skin.save(ASSETS / "textures/entity/automata_de_forja.png")
    glow.save(ASSETS / "textures/entity/automata_de_forja_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/automata_de_forja.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # Slow enough that you can watch it think.
            "idle": {
                "loop": True,
                "animation_length": 7.0,
                "bones": {
                    "body": rot([(0, [0, 0, 0]), (3.5, [1, 0, 0]), (7, [0, 0, 0])]),
                    "head": rot([(0, [0, -8, 0]), (2.3, [0, 0, 0]), (4.6, [0, 8, 0]), (7, [0, -8, 0])]),
                    "arm_right": rot([(0, [0, 0, -2]), (3.5, [-3, 0, -2]), (7, [0, 0, -2])]),
                    "arm_left": rot([(0, [0, 0, 2]), (3.5, [3, 0, 2]), (7, [0, 0, 2])]),
                },
            },
            "walk": {
                "loop": True,
                "animation_length": 2.6,
                "bones": {
                    "leg_right": rot([(0, [-14, 0, 0]), (1.3, [14, 0, 0]), (2.6, [-14, 0, 0])]),
                    "leg_left": rot([(0, [14, 0, 0]), (1.3, [-14, 0, 0]), (2.6, [14, 0, 0])]),
                    "arm_right": rot([(0, [8, 0, -2]), (1.3, [-8, 0, -2]), (2.6, [8, 0, -2])]),
                    "arm_left": rot([(0, [-8, 0, 2]), (1.3, [8, 0, 2]), (2.6, [-8, 0, 2])]),
                    "body": bone(
                        rot([(0, [1, -2, 0]), (1.3, [1, 2, 0]), (2.6, [1, -2, 0])]),
                        pos([(0, [0, 0, 0]), (0.65, [0, 0.6, 0]), (1.95, [0, 0.6, 0]), (2.6, [0, 0, 0])]),
                    ),
                },
            },
            "smash": {
                "loop": False,
                "animation_length": 1.6,
                "bones": {
                    "arm_right": rot([(0, [0, 0, -2]), (0.5, [-105, 0, -8]), (0.85, [-108, 0, -8]),
                                      (1.0, [40, 0, 0]), (1.6, [0, 0, -2])]),
                    "body": rot([(0, [0, 0, 0]), (0.5, [-8, 0, 0]), (1.0, [14, 0, 0]), (1.6, [0, 0, 0])]),
                },
            },
            # Coz de escoria: both arms go up together and stay up while the wedge on the floor grows,
            # then come down as one. They LAND AT 1.8 s — ForgeAutomaton.SLAG_WINDUP, 36 ticks. The move
            # asked for an animation called "coz" from the day it was written and there never was one:
            # it stood still for two seconds and then the floor caught fire.
            "coz": {
                "loop": False,
                "animation_length": 2.7,
                "bones": {
                    "arm_right": rot([(0, [0, 0, -2]), (0.9, [-148, 0, -10]), (1.65, [-158, 0, -10]),
                                      (1.8, [36, 0, 0]), (2.1, [28, 0, 0]), (2.7, [0, 0, -2])]),
                    "arm_left": rot([(0, [0, 0, 2]), (0.9, [-148, 0, 10]), (1.65, [-158, 0, 10]),
                                     (1.8, [36, 0, 0]), (2.1, [28, 0, 0]), (2.7, [0, 0, 2])]),
                    "body": bone(
                        rot([(0, [0, 0, 0]), (0.9, [-10, 0, 0]), (1.65, [-13, 0, 0]), (1.8, [18, 0, 0]),
                             (2.1, [14, 0, 0]), (2.7, [0, 0, 0])]),
                        pos([(0, [0, 0, 0]), (0.9, [0, 0.8, 0]), (1.65, [0, 1.0, 0]), (1.8, [0, -0.8, 0]), (2.7, [0, 0, 0])]),
                    ),
                    "head": rot([(0, [0, 0, 0]), (0.9, [-10, 0, 0]), (1.8, [12, 0, 0]), (2.7, [0, 0, 0])]),
                },
            },
            # Venting: it settles back on its heels, the belly opens and something comes out of it. Slow
            # on the wind-up on purpose, because it is the only thing it can do to you at a distance.
            "vent": {
                "loop": False,
                "animation_length": 1.8,
                "bones": {
                    "body": bone(
                        rot([(0, [0, 0, 0]), (0.6, [-16, 0, 0]), (1.0, [10, 0, 0]), (1.8, [0, 0, 0])]),
                        pos([(0, [0, 0, 0]), (0.6, [0, 0, 1.5]), (1.0, [0, 0, -1.0]), (1.8, [0, 0, 0])]),
                    ),
                    "head": rot([(0, [0, 0, 0]), (0.6, [-12, 0, 0]), (1.0, [8, 0, 0]), (1.8, [0, 0, 0])]),
                    "arm_right": rot([(0, [0, 0, -2]), (0.6, [24, 0, -14]), (1.8, [0, 0, -2])]),
                    "arm_left": rot([(0, [0, 0, 2]), (0.6, [24, 0, 14]), (1.8, [0, 0, 2])]),
                },
            },
            # The steam purge: everything opens at once and it drops back down.
            "steam": {
                "loop": False,
                "animation_length": 1.2,
                "bones": {
                    "body": bone(
                        pos([(0, [0, 0, 0]), (0.15, [0, 1.6, 0]), (0.5, [0, -0.6, 0]), (1.2, [0, 0, 0])]),
                        scale([(0, [1, 1, 1]), (0.15, [1.08, 0.94, 1.08]), (0.5, [0.97, 1.03, 0.97]), (1.2, [1, 1, 1])]),
                    ),
                    "arm_right": rot([(0, [0, 0, -2]), (0.15, [0, 0, -40]), (1.2, [0, 0, -2])]),
                    "arm_left": rot([(0, [0, 0, 2]), (0.15, [0, 0, 40]), (1.2, [0, 0, 2])]),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the die guardian

# Guardian de Cuno: a die that stands up. The whole read is a head far too big for what is carrying
# it, because the head **is** the die — the stamp itself, face outwards, with the seal cut into it in
# gold. Squat legs, heavy shoulders, and nothing about it that suggests speed.
CUNE_CUBES = {
    "leg_right": ([-8, 0, -5], [7, 9, 10], "stone"),
    "leg_left": ([1, 0, -5], [7, 9, 10], "stone"),
    "hips": ([-7, 9, -4], [14, 4, 8], "dark"),
    "body": ([-8, 13, -5], [16, 10, 10], "stone"),
    "shoulders": ([-12, 21, -7], [24, 5, 14], "stone"),
    "arm_right": ([-15, 9, -5], [5, 13, 10], "stone"),
    "arm_left": ([10, 9, -5], [5, 13, 10], "stone"),
    "die": ([-10, 26, -8], [20, 13, 16], "stone"),
    "die_face": ([-9, 28, -9], [18, 9, 1], "gold"),
    "die_seal": ([-4, 31, -9.5], [8, 4, 1], "star"),
    "band_low": ([-10.5, 25, -8.5], [21, 2, 17], "gold"),
    "band_high": ([-10.5, 37, -8.5], [21, 2, 17], "gold"),
}

CUNE_BONES = [
    ("root", None, [0, 0, 0], []),
    ("leg_right", "root", [-4.5, 9, 0], ["leg_right"]),
    ("leg_left", "root", [4.5, 9, 0], ["leg_left"]),
    ("body", "root", [0, 11, 0], ["hips", "body", "shoulders"]),
    ("arm_right", "body", [-12.5, 21, 0], ["arm_right"]),
    ("arm_left", "body", [12.5, 21, 0], ["arm_left"]),
    ("head", "body", [0, 26, 0], ["die", "die_face", "die_seal", "band_low", "band_high"]),
]

CUNE_PALETTE = {
    "stone": ((118, 116, 112), (150, 148, 144), (76, 74, 72)),
    "dark": ((92, 94, 102), (120, 122, 130), (58, 60, 66)),
    "gold": ((186, 146, 56), (216, 180, 84), (122, 94, 32)),
    "star": ((38, 46, 88), (58, 70, 120), (24, 28, 54), (196, 224, 255)),
}


def generate_cune_guardian_assets():
    atlas = (256, 256)
    uvs = write_geo("guardian_de_cuno", CUNE_CUBES, CUNE_BONES, atlas, (3, 3, 1.5))
    skin, glow = paint_model(CUNE_CUBES, CUNE_PALETTE, uvs, atlas, 54002, wear=0.5)
    skin.save(ASSETS / "textures/entity/guardian_de_cuno.png")
    glow.save(ASSETS / "textures/entity/guardian_de_cuno_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/guardian_de_cuno.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # Sealed: it does not breathe, it ticks. Stepped keyframes, because a thing held together
            # by somebody else's seals is a mechanism rather than a creature.
            "idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "head": rot(stepped([(0, [0, -6, 0]), (1.0, [0, 0, 0]), (2.0, [0, 6, 0]),
                                         (3.0, [0, 0, 0]), (4.0, [0, -6, 0])])),
                    "body": pos([(0, [0, 0, 0]), (2.0, [0, -0.3, 0]), (4.0, [0, 0, 0])]),
                },
            },
            "walk": {
                "loop": True,
                "animation_length": 1.6,
                "bones": {
                    "leg_right": rot([(0, [-14, 0, 0]), (0.8, [14, 0, 0]), (1.6, [-14, 0, 0])]),
                    "leg_left": rot([(0, [14, 0, 0]), (0.8, [-14, 0, 0]), (1.6, [14, 0, 0])]),
                    "arm_right": rot([(0, [10, 0, 0]), (0.8, [-10, 0, 0]), (1.6, [10, 0, 0])]),
                    "arm_left": rot([(0, [-10, 0, 0]), (0.8, [10, 0, 0]), (1.6, [-10, 0, 0])]),
                    # The die is heavy and arrives late, which is most of what sells the weight.
                    "head": bone(
                        rot([(0, [0, 0, 3]), (0.4, [2, 0, 0]), (0.8, [0, 0, -3]), (1.2, [2, 0, 0]), (1.6, [0, 0, 3])]),
                        pos([(0, [0, 0, 0]), (0.4, [0, -0.6, 0]), (0.8, [0, 0, 0]), (1.2, [0, -0.6, 0]), (1.6, [0, 0, 0])]),
                    ),
                    "body": pos([(0, [0, 0, 0]), (0.4, [0, 0.5, 0]), (0.8, [0, 0, 0]),
                                 (1.2, [0, 0.5, 0]), (1.6, [0, 0, 0])]),
                },
            },
            # The stamp: it leans the whole die back, hangs there, and brings it down flat on the
            # floor. Contact at 2.0s = 40 ticks, which is the longest wind-up in the mod.
            "stamp": {
                "loop": False,
                "animation_length": 3.0,
                "bones": {
                    "body": rot([(0, [0, 0, 0]), (0.8, [-24, 0, 0]), (1.7, [-28, 0, 0]),
                                 (2.0, [34, 0, 0]), (2.4, [10, 0, 0]), (3.0, [0, 0, 0])]),
                    "head": bone(
                        rot([(0, [0, 0, 0]), (0.8, [-30, 0, 0]), (1.7, [-34, 0, 0]),
                             (2.0, [46, 0, 0]), (2.4, [8, 0, 0]), (3.0, [0, 0, 0])]),
                        pos([(0, [0, 0, 0]), (0.8, [0, 2, 3]), (1.7, [0, 3, 4]),
                             (2.0, [0, -6, -6]), (2.4, [0, -1, -1]), (3.0, [0, 0, 0])]),
                    ),
                    "arm_right": rot([(0, [0, 0, 0]), (0.8, [-40, 0, 0]), (1.7, [-46, 0, 0]),
                                      (2.0, [30, 0, 0]), (3.0, [0, 0, 0])]),
                    "arm_left": rot([(0, [0, 0, 0]), (0.8, [-40, 0, 0]), (1.7, [-46, 0, 0]),
                                     (2.0, [30, 0, 0]), (3.0, [0, 0, 0])]),
                    "leg_right": rot([(0, [0, 0, 0]), (1.7, [-8, 0, 0]), (2.0, [6, 0, 0]), (3.0, [0, 0, 0])]),
                    "leg_left": rot([(0, [0, 0, 0]), (1.7, [-8, 0, 0]), (2.0, [6, 0, 0]), (3.0, [0, 0, 0])]),
                },
            },
            # The seals going: it comes apart a little and stops ticking. Played once, when the last
            # lantern goes out, and it is the only thing that says the fight has actually started.
            "unsealed": {
                "loop": False,
                "animation_length": 1.8,
                "bones": {
                    "head": bone(
                        rot([(0, [0, 0, 0]), (0.3, [-8, 0, 5]), (0.7, [6, 0, -4]), (1.8, [0, 0, 0])]),
                        pos([(0, [0, 0, 0]), (0.3, [0, 1.5, 0]), (0.7, [0, -1, 0]), (1.8, [0, 0, 0])]),
                    ),
                    "shoulders": rot([(0, [0, 0, 0]), (0.4, [0, 0, 3]), (0.9, [0, 0, -3]), (1.8, [0, 0, 0])]),
                    "arm_right": rot([(0, [0, 0, 0]), (0.4, [-14, 0, 0]), (1.8, [0, 0, 0])]),
                    "arm_left": rot([(0, [0, 0, 0]), (0.4, [-14, 0, 0]), (1.8, [0, 0, 0])]),
                },
            },
        },
    })


def write_forge_castle():
    """The castillo de forja: a walled keep with the stamping hall and its three seals inside."""
    import random

    rng = random.Random(410277)
    size = (21, 14, 21)
    blocks = {}

    def put(x, y, z, block, properties=None, nbt=None):
        blocks[(x, y, z)] = (block, properties or {}, nbt)

    floor = ["minecraft:deepslate_bricks", "minecraft:deepslate_bricks", "minecraft:polished_deepslate",
             "minecraft:cracked_deepslate_bricks", "minecraft:deepslate_tiles"]
    wall = ["minecraft:deepslate_bricks", "minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks",
            "minecraft:polished_deepslate", "minecraft:deepslate_tiles"]

    # Ground, and the whole box cleared above it.
    for x in range(21):
        for z in range(21):
            put(x, 0, z, rng.choice(floor))
            for y in range(1, 14):
                put(x, y, z, "minecraft:air")

    # Curtain wall, with crenellations and a gap for the gate on the south side.
    for x in range(21):
        for z in range(21):
            if not (x in (0, 20) or z in (0, 20)):
                continue
            gate = z == 20 and 9 <= x <= 11
            for y in range(1, 6):
                if gate and y <= 4:
                    continue
                put(x, y, z, rng.choice(wall))
            if (x + z) % 2 == 0 and not gate:
                put(x, 6, z, "minecraft:deepslate_brick_wall")

    # Buttresses down the curtain, so twenty-one blocks of the same brick is not one flat face.
    for run in range(3, 19, 5):
        for y in range(1, 7):
            put(run, y, 0, "minecraft:deepslate_tiles")
            put(run, y, 20, "minecraft:deepslate_tiles")
            put(0, y, run, "minecraft:deepslate_tiles")
            put(20, y, run, "minecraft:deepslate_tiles")

    # Corner towers, three across. They go up to eleven, well over the wall walk at six: a tower the
    # same height as the wall it is on is not a tower, it is a thicker bit of wall, and the first
    # version of this castle photographed as a grey box for exactly that reason.
    for cx, cz in ((0, 0), (18, 0), (0, 18), (18, 18)):
        for x in range(cx, cx + 3):
            for z in range(cz, cz + 3):
                for y in range(1, 12):
                    edge = x in (cx, cx + 2) or z in (cz, cz + 2)
                    put(x, y, z, rng.choice(wall) if edge else "minecraft:air")
                # A corbelled course under the parapet, which is what makes a tower top read as a
                # top rather than as the place the blocks ran out.
                put(x, 10, z, "minecraft:polished_deepslate")
                if (x + z) % 2 == 0:
                    put(x, 12, z, "minecraft:deepslate_brick_wall")
        # A lit window near the top of each, so the place reads as garrisoned from outside.
        put(cx + 1, 8, cz + (0 if cz == 0 else 2), "forja:farol_de_pavesa")

    # The gate itself: a portcullis you can walk under, and two braziers either side of it.
    for x in range(9, 12):
        put(x, 5, 20, "minecraft:iron_bars", {"east": "true", "west": "true", "north": "false", "south": "false", "waterlogged": "false"})
    put(8, 1, 19, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    put(12, 1, 19, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})

    # The stamping hall in the middle, with its own walls, roof and a south doorway.
    for x in range(5, 16):
        for z in range(4, 15):
            edge = x in (5, 15) or z in (4, 14)
            door = z == 14 and 9 <= x <= 11
            for y in range(1, 8):
                if not edge:
                    put(x, y, z, "minecraft:air")
                elif door and y <= 3:
                    put(x, y, z, "minecraft:air")
                else:
                    put(x, y, z, rng.choice(wall))
            put(x, 8, z, "minecraft:polished_deepslate" if edge else rng.choice(floor))
        # A gilded course round the hall, so it is clearly the room that matters.
        put(x, 7, 4, "minecraft:gilded_blackstone")
        put(x, 7, 14, "minecraft:gilded_blackstone")
    for z in range(4, 15):
        put(5, 7, z, "minecraft:gilded_blackstone")
        put(15, 7, z, "minecraft:gilded_blackstone")

    # Hall floor: a stamped pattern, which is the one bit of decoration that says what happens here.
    for x in range(6, 15):
        for z in range(5, 14):
            put(x, 0, z, "minecraft:polished_deepslate")
    for x in range(8, 13):
        for z in range(7, 12):
            put(x, 0, z, "minecraft:chiseled_deepslate")
    put(10, 0, 9, "minecraft:gilded_blackstone")

    # The louvre over the hall: a raised box with open sides, the way a forge lets its own smoke out.
    # It is also the one thing on the skyline that is not a wall, which is what tells you from a
    # distance that the middle of this place is a workshop and not a keep.
    for x in range(8, 13):
        for z in range(7, 12):
            edge = x in (8, 12) or z in (7, 11)
            for yy in (9, 10):
                if not edge:
                    put(x, yy, z, "minecraft:air")
                elif (x in (8, 12)) and (z in (7, 11)):
                    put(x, yy, z, "minecraft:polished_deepslate")
                else:
                    put(x, yy, z, "minecraft:iron_bars", {"east": "true", "west": "true", "north": "true", "south": "true", "waterlogged": "false"})
            put(x, 11, z, "minecraft:gilded_blackstone" if edge else "minecraft:polished_deepslate")
    put(10, 12, 9, "minecraft:deepslate_brick_wall")

    # The three seals: pillars with an ember lantern on top. While one stands the guardian cannot be
    # hurt, so they are placed apart and in the open — the fight is about crossing the room, not about
    # finding them.
    for sx, sz in ((7, 6), (13, 6), (10, 12)):
        for y in range(1, 4):
            put(sx, y, sz, "minecraft:polished_deepslate")
        put(sx, 4, sz, "forja:farol_de_pavesa")
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            put(sx + dx, 1, sz + dz, "minecraft:deepslate_brick_slab", {"type": "bottom", "waterlogged": "false"})

    # What it is guarding, behind it: the templates, an anvil and a forge table.
    put(10, 1, 5, "minecraft:chest", {"facing": "south", "type": "single"}, {"LootTable": "forja:chests/castillo_de_forja"})
    put(7, 1, 5, "forja:yunque_del_herrero", {"facing": "south"})
    put(13, 1, 5, "forja:mesa_de_forja", {"facing": "south"})

    # The courtyard: a well, a couple of fires and the wreck of whatever came through the gate before.
    put(3, 1, 17, "minecraft:cauldron")
    put(17, 1, 17, "minecraft:campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    for x, z in ((4, 4), (16, 4), (3, 16), (17, 3)):
        put(x, 1, z, rng.choice(["minecraft:cracked_deepslate_bricks", "minecraft:deepslate_brick_slab"]))

    guards = [
        {
            "pos": [NbtDouble(10.5), NbtDouble(1.0), NbtDouble(9.5)],
            "blockPos": [10, 1, 9],
            "nbt": {"id": "forja:guardian_de_cuno", "PersistenceRequired": NbtByte(1)},
        },
        {
            "pos": [NbtDouble(6.5), NbtDouble(1.0), NbtDouble(11.5)],
            "blockPos": [6, 1, 11],
            "nbt": {"id": "forja:tenaza", "PersistenceRequired": NbtByte(1)},
        },
        {
            "pos": [NbtDouble(14.5), NbtDouble(1.0), NbtDouble(11.5)],
            "blockPos": [14, 1, 11],
            "nbt": {"id": "forja:percutor", "PersistenceRequired": NbtByte(1)},
        },
    ]
    write_structure_nbt("castillo_de_forja/castillo", size, blocks, guards)


def generate_forge_castle():
    generate_cune_guardian_assets()
    write_forge_castle()
    write_json(DATA / "worldgen/template_pool/castillo_de_forja/inicio.json", {
        "elements": [{
            "element": {"element_type": "minecraft:single_pool_element",
                        "location": "forja:castillo_de_forja/castillo",
                        "processors": "minecraft:mossify_10_percent", "projection": "rigid"},
            "weight": 1,
        }],
        "fallback": "minecraft:empty",
    })
    write_json(DATA / "worldgen/structure/castillo_de_forja.json", {
        "type": "minecraft:jigsaw",
        "biomes": "#forja:has_structure/castillo_de_forja",
        "max_distance_from_center": 80,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"absolute": 0},
        "start_pool": "forja:castillo_de_forja/inicio",
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "use_expansion_hack": False,
    })
    write_json(DATA / "worldgen/structure_set/castillo_de_forja.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 771204853, "separation": 26, "spacing": 84},
        "structures": [{"structure": "forja:castillo_de_forja", "weight": 1}],
    })
    # Rare and far apart on purpose: it is the only place the templates come out of a chest, and a
    # castle you meet twice in an afternoon is a supply depot rather than a landmark.
    write_json(DATA / "tags/worldgen/biome/has_structure/castillo_de_forja.json", {"values": [
        "#minecraft:is_hill", "#minecraft:is_mountain", "minecraft:savanna_plateau",
        "minecraft:windswept_savanna", "minecraft:stony_peaks",
    ]})
    write_json(DATA / "tags/worldgen/structure/on_castillo_de_forja.json", {"values": ["forja:castillo_de_forja"]})
    write_json(DATA / "loot_table/chests/castillo_de_forja.json", {
        "type": "minecraft:chest",
        "random_sequence": "forja:chests/castillo_de_forja",
        "pools": [
            {
                # The templates, which is the whole reason to come here.
                "rolls": {"type": "minecraft:uniform", "min": 2, "max": 3},
                "entries": [
                    {"type": "minecraft:item", "name": "forja:plantilla", "weight": 10,
                     "functions": [{"function": "minecraft:set_count",
                                    "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                    {"type": "minecraft:item", "name": "forja:sello", "weight": 4},
                    {"type": "minecraft:item", "name": "forja:orbe_de_mejora", "weight": 3},
                ],
            },
            {
                "rolls": {"type": "minecraft:uniform", "min": 3, "max": 5},
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 10,
                     "functions": [{"function": "minecraft:set_count",
                                    "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
                    {"type": "minecraft:item", "name": "forja:acero", "weight": 8,
                     "functions": [{"function": "minecraft:set_count",
                                    "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]},
                    {"type": "minecraft:item", "name": "forja:hierro_estelar", "weight": 3,
                     "functions": [{"function": "minecraft:set_count",
                                    "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}]},
                    {"type": "minecraft:item", "name": "minecraft:lapis_lazuli", "weight": 6,
                     "functions": [{"function": "minecraft:set_count",
                                    "count": {"type": "minecraft:uniform", "min": 3, "max": 9}}]},
                    {"type": "minecraft:item", "name": "minecraft:experience_bottle", "weight": 5,
                     "functions": [{"function": "minecraft:set_count",
                                    "count": {"type": "minecraft:uniform", "min": 2, "max": 5}}]},
                ],
            },
        ],
    })


# ---------------------------------------------------------------------------- the coal hauler

# Cargador de Carbon, variant B: squat and armoured, with the coal built **into** its back under iron
# plates and the fire showing through the gaps rather than standing on top. It reads as a siege engine
# that walks — low, wide, hard to get past — and the glow along its flanks is the only warning of what
# is inside it.
HAULER_CUBES = {
    "body": ([-8, 6, -12], [16, 10, 22], "dark"),
    "chest": ([-9, 6, -15], [18, 11, 5], "dark"),
    "head": ([-5, 4, -21], [10, 8, 7], "dark"),
    "tusk_right": ([-6, 4, -23], [2, 4, 3], "iron"),
    "tusk_left": ([4, 4, -23], [2, 4, 3], "iron"),
    "eye_right": ([-4, 9, -21.5], [2, 1.5, 1], "molten"),
    "eye_left": ([2, 9, -21.5], [2, 1.5, 1], "molten"),
    "leg_fr": ([-7, 0, -11], [5, 7, 6], "dark"),
    "leg_fl": ([2, 0, -11], [5, 7, 6], "dark"),
    "leg_br": ([-7, 0, 5], [5, 7, 6], "dark"),
    "leg_bl": ([2, 0, 5], [5, 7, 6], "dark"),
    "coal_spine": ([-6, 14, -10], [12, 6, 18], "coal"),
    "seam_right": ([-8.5, 15, -10], [2, 4, 18], "molten"),
    "seam_left": ([6.5, 15, -10], [2, 4, 18], "molten"),
    "plate_front": ([-8, 16, -11], [16, 4, 8], "iron"),
    "plate_back": ([-8, 16, -1], [16, 4, 9], "iron"),
    "stack_right": ([-6, 20, -6], [4, 6, 4], "iron"),
    "stack_left": ([2, 20, -6], [4, 6, 4], "iron"),
    "smoke_right": ([-5.5, 25, -5.5], [3, 4, 3], "molten"),
    "smoke_left": ([2.5, 25, -5.5], [3, 4, 3], "molten"),
}

HAULER_BONES = [
    ("root", None, [0, 0, 0], []),
    ("leg_fr", "root", [-4.5, 7, -8], ["leg_fr"]),
    ("leg_fl", "root", [4.5, 7, -8], ["leg_fl"]),
    ("leg_br", "root", [-4.5, 7, 8], ["leg_br"]),
    ("leg_bl", "root", [4.5, 7, 8], ["leg_bl"]),
    ("body", "root", [0, 6, 0], ["body", "chest", "coal_spine", "seam_right", "seam_left",
                                 "plate_front", "plate_back", "stack_right", "stack_left"]),
    ("head", "body", [0, 8, -17], ["head", "tusk_right", "tusk_left", "eye_right", "eye_left"]),
    ("smoke", "body", [0, 25, -4], ["smoke_right", "smoke_left"]),
]

HAULER_PALETTE = {
    "dark": ((92, 94, 102), (120, 122, 130), (58, 60, 66)),
    "iron": ((156, 158, 166), (194, 196, 204), (100, 102, 110)),
    "coal": ((38, 36, 38), (56, 54, 56), (22, 20, 22)),
    "molten": ((96, 40, 12), (128, 56, 16), (52, 22, 8), (255, 196, 92)),
}


def generate_hauler_assets():
    atlas = (128, 128)
    uvs = write_geo("cargador_de_carbon", HAULER_CUBES, HAULER_BONES, atlas, (3, 2, 1))
    skin, glow = paint_model(HAULER_CUBES, HAULER_PALETTE, uvs, atlas, 55005, wear=0.8,
                             rust=(120, 74, 40))
    skin.save(ASSETS / "textures/entity/cargador_de_carbon.png")
    glow.save(ASSETS / "textures/entity/cargador_de_carbon_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/cargador_de_carbon.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            "idle": {
                "loop": True,
                "animation_length": 3.2,
                "bones": {
                    "body": scale([(0, [1, 1, 1]), (1.6, [1.02, 1.03, 1.01]), (3.2, [1, 1, 1])]),
                    "head": rot([(0, [0, -4, 0]), (1.6, [0, 4, 0]), (3.2, [0, -4, 0])]),
                    "smoke": scale([(0, [1, 1, 1]), (1.6, [1.1, 1.3, 1.1]), (3.2, [1, 1, 1])]),
                },
            },
            # Four short legs under a boiler. It rolls rather than trots.
            "walk": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {
                    "leg_fr": rot([(0, [-22, 0, 0]), (0.5, [22, 0, 0]), (1.0, [-22, 0, 0])]),
                    "leg_bl": rot([(0, [-22, 0, 0]), (0.5, [22, 0, 0]), (1.0, [-22, 0, 0])]),
                    "leg_fl": rot([(0, [22, 0, 0]), (0.5, [-22, 0, 0]), (1.0, [22, 0, 0])]),
                    "leg_br": rot([(0, [22, 0, 0]), (0.5, [-22, 0, 0]), (1.0, [22, 0, 0])]),
                    "body": bone(
                        rot([(0, [0, 0, 2.5]), (0.5, [0, 0, -2.5]), (1.0, [0, 0, 2.5])]),
                        pos([(0, [0, 0, 0]), (0.25, [0, 0.4, 0]), (0.5, [0, 0, 0]),
                             (0.75, [0, 0.4, 0]), (1.0, [0, 0, 0])]),
                    ),
                    "head": rot([(0, [3, 0, 0]), (0.5, [-3, 0, 0]), (1.0, [3, 0, 0])]),
                    "smoke": scale([(0, [1.2, 1.4, 1.2]), (0.5, [1.0, 1.9, 1.0]), (1.0, [1.2, 1.4, 1.2])]),
                },
            },
            # The fuse: it plants its feet, swells, and the stacks blow. Blast at 1.8s = 36 ticks.
            "prime": {
                "loop": False,
                "animation_length": 2.4,
                "bones": {
                    "body": bone(
                        scale([(0, [1, 1, 1]), (0.8, [1.1, 1.14, 1.08]), (1.6, [1.18, 1.22, 1.14]),
                               (1.8, [1.4, 1.4, 1.4]), (2.4, [1, 1, 1])]),
                        pos([(0, [0, 0, 0]), (1.6, [0, 1.0, 0]), (1.8, [0, -0.6, 0]), (2.4, [0, 0, 0])]),
                    ),
                    "head": rot([(0, [0, 0, 0]), (0.8, [-14, 0, 0]), (1.8, [18, 0, 0]), (2.4, [0, 0, 0])]),
                    "smoke": scale([(0, [1, 1, 1]), (0.8, [1.6, 2.4, 1.6]), (1.6, [2.2, 4.0, 2.2]),
                                    (1.8, [0.2, 0.2, 0.2]), (2.4, [1, 1, 1])]),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the quencher

# Templador, the version Andy picked: the tank goes on his back and the ladle goes away entirely.
#
# The cart it started as never worked. A two-wheeled cart dragged behind a walking man has to swing
# when he does, and it was welded to his root bone, so it slid about behind him like a sledge. And it
# pushed the ladle — the whole point of the mob — into being a detail hanging off one side.
#
# Taking the ladle off changes what he is, and for the better: the oil now leaves the tank under its
# own pressure, so the <b>stack is the warning</b>. A templador quietly venting is one with pressure
# in it, and that plume is the only notice you get before he purges.
QUENCHER_CUBES = {
    "leg_right": ([-4.5, 0, -2.5], [4, 11, 5], "dark"),
    "leg_left": ([0.5, 0, -2.5], [4, 11, 5], "dark"),
    "hips": ([-4, 11, -3], [8, 4, 6], "leather"),
    "chest": ([-5, 15, -3.5], [10, 9, 7], "leather"),
    "apron": ([-4.5, 8, -5], [9, 11, 1.5], "oil"),
    "head": ([-3.5, 24, -4], [7, 6, 7], "oil"),
    "brim": ([-5, 24, -5.5], [10, 1.5, 10], "oil"),
    "face": ([-2.5, 26, -5], [5, 2, 1], "dark"),
    "arm_right": ([-9, 13, -3], [4, 11, 6], "leather"),
    "arm_left": ([5, 13, -3], [4, 11, 6], "leather"),
    # The tank: tall, narrow and iron, high on the shoulders. Narrow is the point — the outline has to
    # be a man carrying something, not a crate with legs, which is where two earlier passes ended up.
    "tank": ([-3.5, 14, 3.5], [7, 13, 5], "iron"),
    "tank_hoop_low": ([-4, 16.5, 3], [8, 1.5, 6], "brass"),
    "tank_hoop_high": ([-4, 23.5, 3], [8, 1.5, 6], "brass"),
    "strap_right": ([-5, 15, -4], [2, 11, 1.5], "leather"),
    "strap_left": ([3, 15, -4], [2, 11, 1.5], "leather"),
    # And the stack, sooted black, ending just above the hat.
    "stack": ([-2, 27, 4.5], [4, 7, 4], "soot"),
    "stack_band": ([-2.5, 29.5, 4], [5, 1.5, 5], "brass"),
    "stack_lip": ([-2.5, 33, 4], [5, 1.5, 5], "iron"),
}

QUENCHER_BONES = [
    ("root", None, [0, 0, 0], []),
    ("leg_right", "root", [-2.5, 11, 0], ["leg_right"]),
    ("leg_left", "root", [2.5, 11, 0], ["leg_left"]),
    ("body", "root", [0, 13, 0], ["hips", "chest", "apron", "tank", "tank_hoop_low", "tank_hoop_high",
                                  "strap_right", "strap_left"]),
    ("head", "body", [0, 24, -1], ["head", "brim", "face"], [6, 0, 0]),
    ("arm_right", "body", [-7, 23, 0], ["arm_right"]),
    ("arm_left", "body", [7, 23, 0], ["arm_left"]),
    # The stack on its own bone so it can shake when the tank is building pressure.
    ("stack", "body", [0, 27, 6], ["stack", "stack_band", "stack_lip"]),
]

QUENCHER_PALETTE = {
    "dark": ((92, 94, 102), (120, 122, 130), (58, 60, 66)),
    "leather": ((86, 66, 52), (110, 86, 68), (58, 44, 34)),
    "oil": ((32, 40, 34), (48, 58, 50), (20, 26, 22)),
    "iron": ((156, 158, 166), (194, 196, 204), (100, 102, 110)),
    "brass": ((112, 92, 54), (134, 112, 70), (74, 60, 34)),
    "soot": ((64, 62, 62), (84, 82, 82), (44, 42, 42)),
}

# Where the smoke comes out, in blocks from his feet, for the entity to emit from.
QUENCHER_VENT = (0.0, 34.5 / 16.0, 6.5 / 16.0)


def generate_quencher_assets():
    atlas = (128, 128)
    uvs = write_geo("templador", QUENCHER_CUBES, QUENCHER_BONES, atlas, (2.5, 2.5, 1.2))
    skin, glow = paint_model(QUENCHER_CUBES, QUENCHER_PALETTE, uvs, atlas, 55009, wear=0.7)
    skin.save(ASSETS / "textures/entity/templador.png")
    glow.save(ASSETS / "textures/entity/templador_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/templador.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # He is carrying something heavy that is under pressure: he shifts his weight and the
            # stack ticks over with it.
            "idle": {
                "loop": True,
                "animation_length": 3.0,
                "bones": {
                    "body": bone(
                        pos([(0, [0, 0, 0]), (1.5, [0, 0.3, 0]), (3.0, [0, 0, 0])]),
                        rot([(0, [0, 0, 1]), (1.5, [0, 0, -1]), (3.0, [0, 0, 1])]),
                    ),
                    "head": rot([(0, [6, -6, 0]), (1.5, [6, 6, 0]), (3.0, [6, -6, 0])]),
                    "stack": rot([(0, [0, 0, -1.5]), (0.75, [0, 0, 1.5]), (1.5, [0, 0, -1.5]),
                                  (2.25, [0, 0, 1.5]), (3.0, [0, 0, -1.5])]),
                },
            },
            # Walking under a full tank: short steps, and the load leans him back.
            "walk": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {
                    "leg_right": rot([(0, [-26, 0, 0]), (0.5, [26, 0, 0]), (1.0, [-26, 0, 0])]),
                    "leg_left": rot([(0, [26, 0, 0]), (0.5, [-26, 0, 0]), (1.0, [26, 0, 0])]),
                    "arm_right": rot([(0, [22, 0, 0]), (0.5, [-18, 0, 0]), (1.0, [22, 0, 0])]),
                    "arm_left": rot([(0, [-18, 0, 0]), (0.5, [22, 0, 0]), (1.0, [-18, 0, 0])]),
                    "body": bone(
                        rot([(0, [-6, 0, 2]), (0.5, [-6, 0, -2]), (1.0, [-6, 0, 2])]),
                        pos([(0, [0, 0, 0]), (0.25, [0, 0.5, 0]), (0.5, [0, 0, 0]),
                             (0.75, [0, 0.5, 0]), (1.0, [0, 0, 0])]),
                    ),
                    "stack": rot([(0, [0, 0, 3]), (0.5, [0, 0, -3]), (1.0, [0, 0, 3])]),
                },
            },
            # The purge: he braces, the tank shakes harder and harder, and it lets go at 1.1s = 22
            # ticks, which is where DOUSE_WINDUP puts the oil on the ground.
            "douse": {
                "loop": False,
                "animation_length": 2.0,
                "bones": {
                    "body": rot([(0, [0, 0, 0]), (0.5, [-8, 0, 0]), (1.0, [-14, 0, 0]),
                                 (1.1, [12, 0, 0]), (1.5, [2, 0, 0]), (2.0, [0, 0, 0])]),
                    "head": rot([(0, [6, 0, 0]), (1.0, [-4, 0, 0]), (1.1, [16, 0, 0]), (2.0, [6, 0, 0])]),
                    # Shaking, and faster as it fills: the whole warning is here.
                    "stack": bone(
                        rot(stepped([(0, [0, 0, 0]), (0.3, [0, 0, 4]), (0.5, [0, 0, -5]), (0.7, [0, 0, 6]),
                                     (0.85, [0, 0, -7]), (1.0, [0, 0, 8]), (1.1, [0, 0, 0]), (2.0, [0, 0, 0])], hold=0.04)),
                        scale([(0, [1, 1, 1]), (1.0, [1.08, 1.02, 1.08]), (1.1, [0.92, 1.12, 0.92]), (2.0, [1, 1, 1])]),
                    ),
                    "arm_right": rot([(0, [0, 0, 0]), (1.0, [-24, 0, 0]), (1.1, [18, 0, 0]), (2.0, [0, 0, 0])]),
                    "arm_left": rot([(0, [0, 0, 0]), (1.0, [-24, 0, 0]), (1.1, [18, 0, 0]), (2.0, [0, 0, 0])]),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the star core

# Nucleo Estelar: nothing holds it up, and that is the silhouette. A cut stone hanging in the air with
# its own fragments going round it and a broken ring outside those. The two tells Andy asked for live
# on separate bones so the code can drive them: `core` takes the colour as it fills, and `shards`
# spread wider and turn faster the more it is holding.
CORE_CUBES = {
    "core_mid": ([-4, 14, -4], [8, 8, 8], "star"),
    "core_top": ([-2.5, 22, -2.5], [5, 5, 5], "star"),
    "core_cap": ([-1, 27, -1], [2, 3, 2], "star"),
    "core_bottom": ([-2.5, 9, -2.5], [5, 5, 5], "star"),
    "core_tip": ([-1, 6, -1], [2, 3, 2], "star"),
    "shard_a": ([-15, 15, -1.5], [3, 5, 3], "shard"),
    "shard_b": ([12, 15, -1.5], [3, 5, 3], "shard"),
    "shard_c": ([-1.5, 15, -15], [3, 5, 3], "shard"),
    "shard_d": ([-1.5, 15, 12], [3, 5, 3], "shard"),
    "ring_front": ([-11, 9, -12], [22, 1.5, 1.5], "dark"),
    "ring_back": ([-11, 9, 10.5], [22, 1.5, 1.5], "dark"),
    "ring_right": ([-12, 9, -11], [1.5, 1.5, 22], "dark"),
    "ring_left": ([10.5, 9, -11], [1.5, 1.5, 22], "dark"),
}

CORE_BONES = [
    ("root", None, [0, 0, 0], []),
    ("core", "root", [0, 16, 0], ["core_mid", "core_top", "core_cap", "core_bottom", "core_tip"]),
    ("shards", "root", [0, 17, 0], ["shard_a", "shard_b", "shard_c", "shard_d"]),
    ("ring", "root", [0, 10, 0], ["ring_front", "ring_back", "ring_right", "ring_left"], [0, 22, 0]),
]

CORE_PALETTE = {
    "star": ((38, 46, 88), (58, 70, 120), (24, 28, 54), (196, 224, 255)),
    "shard": ((58, 70, 120), (86, 104, 160), (34, 42, 74), (220, 238, 255)),
    "dark": ((92, 94, 102), (120, 122, 130), (58, 60, 66)),
}


def generate_star_core_assets():
    atlas = (128, 128)
    uvs = write_geo("nucleo_estelar", CORE_CUBES, CORE_BONES, atlas, (2, 2, 1))
    skin, glow = paint_model(CORE_CUBES, CORE_PALETTE, uvs, atlas, 54006, wear=0.2)
    skin.save(ASSETS / "textures/entity/nucleo_estelar.png")
    glow.save(ASSETS / "textures/entity/nucleo_estelar_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/nucleo_estelar.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # It hangs, turns and breathes. Nothing about it ever touches the ground.
            "idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "root": pos([(0, [0, 0, 0]), (2.0, [0, 1.5, 0]), (4.0, [0, 0, 0])]),
                    "core": rot([(0, [0, 0, 0]), (4.0, [0, 360, 0])]),
                    "shards": rot([(0, [0, 0, 0]), (4.0, [0, -360, 0])]),
                    "ring": rot([(0, [0, 22, 0]), (2.0, [0, 30, 0]), (4.0, [0, 22, 0])]),
                },
            },
            # Full: the shards fly out and everything speeds up. Played while it is holding damage.
            "charged": {
                "loop": True,
                "animation_length": 1.2,
                "bones": {
                    "core": bone(
                        rot([(0, [0, 0, 0]), (1.2, [0, 360, 0])]),
                        scale([(0, [1.1, 1.1, 1.1]), (0.6, [1.22, 1.22, 1.22]), (1.2, [1.1, 1.1, 1.1])]),
                    ),
                    "shards": bone(
                        rot([(0, [0, 0, 0]), (1.2, [0, -720, 0])]),
                        scale([(0, [1.35, 1.35, 1.35]), (1.2, [1.35, 1.35, 1.35])]),
                    ),
                    "ring": rot([(0, [0, 0, 0]), (1.2, [0, 180, 0])]),
                },
            },
            # Letting go: everything snaps back in as the beam leaves.
            "release": {
                "loop": False,
                "animation_length": 0.9,
                "bones": {
                    "core": scale([(0, [1.25, 1.25, 1.25]), (0.2, [1.5, 1.5, 1.5]),
                                   (0.35, [0.7, 0.7, 0.7]), (0.9, [1, 1, 1])]),
                    "shards": bone(
                        scale([(0, [1.4, 1.4, 1.4]), (0.35, [0.5, 0.5, 0.5]), (0.9, [1, 1, 1])]),
                        rot([(0, [0, 0, 0]), (0.9, [0, 180, 0])]),
                    ),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the broken mould

# Molde Roto: the approved 6C, with the automaton's own furnace built into its belly and worn through
# — middle bar snapped in half, the left one bent, the frame chipped and the stack leaning. It holds
# a **molten** blade up in both hands, which is Andy's call and the better one: a bar of unworked iron
# is something that has not been finished, but a bar of running metal is something that has not
# decided yet, which is exactly what this mob does with it.
MOULD_CUBES = {
    "leg_right": ([-7, 0, -4.5], [6, 9, 9], "dark"),
    "leg_left": ([1, 0, -4.5], [6, 9, 9], "dark"),
    "belly": ([-8, 9, -6], [16, 11, 12], "stone"),
    "door": ([-6, 11, -6.6], [12, 7, 1], "molten"),
    "door_hot": ([-6, 11, -2.9], [12, 7, 1], "white_hot"),
    "bar_right": ([-5, 11, -7.2], [2, 7, 1], "iron"),
    "bar_middle": ([-1, 11, -7.2], [2, 3.5, 1], "iron"),
    "bar_left": ([3, 11, -7.2], [2, 7, 1], "iron"),
    "frame_low": ([-7.5, 9.5, -7.4], [15, 2, 2], "iron"),
    "frame_high": ([-7.5, 18, -7.4], [15, 2, 2], "iron"),
    "frame_right": ([-7.5, 9.5, -7.4], [2, 10.5, 2], "iron"),
    "frame_left": ([5.5, 11.5, -7.4], [2, 8.5, 2], "iron"),
    "hinge": ([-8.5, 12, -6.5], [1.5, 4, 3], "iron"),
    "chest_right": ([-9, 20, -6], [8, 11, 12], "stone"),
    "chest_left": ([1, 20, -6], [8, 11, 12], "stone"),
    "seam": ([-1.5, 20, -6.5], [3, 11, 13], "dark"),
    "spine": ([-3, 20, 5], [6, 12, 3], "dark"),
    "collar": ([-10, 31, -6.5], [20, 3, 13], "iron"),
    "chimney": ([4, 31, 1], [5, 9, 5], "iron"),
    "chimney_cap": ([3, 39, 0], [7, 2, 7], "stone"),
    "head": ([-4, 34, -5], [8, 6, 10], "dark"),
    "eye": ([-4, 37, -6], [8, 2, 1], "molten"),
    "arm_right": ([-12, 16, -5], [4, 15, 8], "stone"),
    "arm_left": ([8, 16, -5], [4, 15, 8], "stone"),
    "hand_right": ([-7.5, 15.5, -9], [6, 6, 6], "dark"),
    "hand_left": ([1.5, 15.5, -9], [6, 6, 6], "dark"),
    "blade_grip": ([-1.5, 15, -8.5], [3, 9, 4], "dark"),
    "blade_guard": ([-5.5, 24, -9], [11, 1.5, 5], "molten"),
    "blade_body": ([-1.5, 25.5, -8.5], [3, 18, 4], "molten"),
    "blade_tip": ([-1, 43.5, -8], [2, 3, 3], "molten"),
}

MOULD_BONES = [
    ("root", None, [0, 0, 0], []),
    ("leg_right", "root", [-4, 9, 0], ["leg_right"]),
    ("leg_left", "root", [4, 9, 0], ["leg_left"]),
    ("body", "root", [0, 9, 0], ["belly", "spine", "collar", "frame_low", "frame_high", "frame_right",
                                 "frame_left", "hinge", "bar_right", "bar_middle"]),
    ("fire", "body", [0, 12, -6], ["door"]),
    # Drawn four units back inside the belly, where the stone hides it. The recast brings it out.
    ("fire_hot", "body", [0, 12, -6], ["door_hot"]),
    ("bar_left", "body", [4, 11, -7], ["bar_left"], [0, 0, -9]),
    ("chimney", "body", [6, 31, 3], ["chimney", "chimney_cap"], [4, 0, -11]),
    ("chest_right", "body", [-1.5, 20, 5], ["chest_right"], [0, -8, 0]),
    ("chest_left", "body", [1.5, 20, 5], ["chest_left", "seam"], [0, 8, 0]),
    ("head", "body", [0, 34, 0], ["head", "eye"]),
    ("arm_right", "body", [-10, 31, 0], ["arm_right"], [14, 0, 7]),
    ("arm_left", "body", [10, 31, 0], ["arm_left"], [14, 0, -7]),
    ("tool", "body", [0, 20, -8], ["hand_right", "hand_left"]),
    ("blank", "tool", [0, 20, -7], ["blade_grip", "blade_guard", "blade_body", "blade_tip"]),
]

MOULD_PALETTE = {
    "stone": ((118, 116, 112), (150, 148, 144), (76, 74, 72)),
    "iron": ((156, 158, 166), (194, 196, 204), (100, 102, 110)),
    "dark": ((92, 94, 102), (120, 122, 130), (58, 60, 66)),
    "molten": ((96, 40, 12), (128, 56, 16), (52, 22, 8), (255, 196, 92)),
    # The strong orange Andy asked for: the fire with the blank in it, a clear step above the dull
    # red it burns at the rest of the time.
    "white_hot": ((255, 132, 24), (255, 176, 64), (214, 92, 12), (255, 238, 170)),
}


def generate_broken_mould_assets():
    atlas = (256, 256)
    uvs = write_geo("molde_roto", MOULD_CUBES, MOULD_BONES, atlas, (2.5, 3.25, 1.6))
    skin, glow = paint_model(MOULD_CUBES, MOULD_PALETTE, uvs, atlas, 56001, wear=0.95,
                             rust=(124, 76, 40), damage=0.55)
    skin.save(ASSETS / "textures/entity/molde_roto.png")
    glow.save(ASSETS / "textures/entity/molde_roto_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/molde_roto.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # The furnace breathes and the blade it is holding never quite settles.
            "idle": {
                "loop": True,
                "animation_length": 3.6,
                "bones": {
                    "body": pos([(0, [0, 0, 0]), (1.8, [0, -0.4, 0]), (3.6, [0, 0, 0])]),
                    "fire_hot": pos([(0, [0, 0, 0]), (3.6, [0, 0, 0])]),
                    "tool": rot([(0, [0, 0, 1.5]), (1.8, [0, 0, -1.5]), (3.6, [0, 0, 1.5])]),
                    "head": rot([(0, [0, -4, 0]), (1.8, [0, 4, 0]), (3.6, [0, -4, 0])]),
                },
            },
            "walk": {
                "loop": True,
                "animation_length": 1.3,
                "bones": {
                    "leg_right": rot([(0, [-18, 0, 0]), (0.65, [18, 0, 0]), (1.3, [-18, 0, 0])]),
                    "leg_left": rot([(0, [18, 0, 0]), (0.65, [-18, 0, 0]), (1.3, [18, 0, 0])]),
                    "body": bone(
                        rot([(0, [0, 2, 0]), (0.65, [0, -2, 0]), (1.3, [0, 2, 0])]),
                        pos([(0, [0, 0, 0]), (0.32, [0, 0.6, 0]), (0.65, [0, 0, 0]),
                             (0.97, [0, 0.6, 0]), (1.3, [0, 0, 0])]),
                    ),
                    "chimney": rot([(0, [4, 0, -11]), (0.65, [4, 0, -14]), (1.3, [4, 0, -11])]),
                    "fire_hot": pos([(0, [0, 0, 0]), (1.3, [0, 0, 0])]),
                },
            },
            # The blank, on its own bone and its own controller. Once the mould has copied a weapon
            # the real item is drawn in its hands by the render layer, so the molten bar has to go
            # somewhere: it shrinks into the fist that was holding it.
            "blank_shown": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {"blank": scale([(0, [1, 1, 1]), (1.0, [1, 1, 1])])},
            },
            "blank_gone": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {"blank": scale([(0, [0.01, 0.01, 0.01]), (1.0, [0.01, 0.01, 0.01])])},
            },
            # Recasting: the blade goes into the furnace, the fire takes, and it comes back out as
            # something else. The swap lands at 1.5s = 30 ticks.
            "recast": {
                "loop": False,
                "animation_length": 2.6,
                "bones": {
                    "tool": bone(
                        pos([(0, [0, 0, 0]), (0.6, [0, -6, 2]), (1.2, [0, -8, 3]),
                             (1.5, [0, -8, 3]), (1.9, [0, 0, 0]), (2.6, [0, 0, 0])]),
                        scale([(0, [1, 1, 1]), (1.2, [0.85, 0.7, 0.85]), (1.5, [0.6, 0.4, 0.6]),
                               (1.9, [1.15, 1.15, 1.15]), (2.6, [1, 1, 1])]),
                    ),
                    "fire_hot": pos([(0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.8, [0, 0, -4]),
                                     (1.9, [0, 0, -4]), (2.2, [0, 0, 0]), (2.6, [0, 0, 0])]),
                    "arm_right": rot([(0, [14, 0, 7]), (0.6, [42, 0, 10]), (1.5, [46, 0, 10]),
                                      (1.9, [14, 0, 7]), (2.6, [14, 0, 7])]),
                    "arm_left": rot([(0, [14, 0, -7]), (0.6, [42, 0, -10]), (1.5, [46, 0, -10]),
                                     (1.9, [14, 0, -7]), (2.6, [14, 0, -7])]),
                    "body": rot([(0, [0, 0, 0]), (0.6, [10, 0, 0]), (1.5, [12, 0, 0]),
                                 (1.9, [-6, 0, 0]), (2.6, [0, 0, 0])]),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the walking anvil

# Yunque Andante: an anvil that got up. The silhouette has to be the anvil first and the creature
# second, which means the three things that make an anvil an anvil and not a block — a face that
# overhangs its waist on every side, a horn tapering off one end and a stepped heel off the other. The
# legs are deliberately too short and too thin for the mass on top of them.
ANVIL_MOB_CUBES = {
    "face": ([-8, 9, -5], [16, 4, 10], "iron"),
    "horn_base": ([-12, 10, -3], [4, 3, 6], "iron"),
    "horn_tip": ([-15, 10.5, -2], [3, 2, 4], "iron"),
    "heel": ([8, 9, -4], [3, 3, 8], "iron"),
    "waist": ([-4, 5, -3], [8, 4, 6], "dark"),
    "seam": ([-4, 8, -3.5], [8, 1, 7], "ember"),
    "foot": ([-6, 3, -4], [12, 2, 8], "stone"),
    "leg_fr": ([-5, 0, -3], [2, 3, 2], "dark"),
    "leg_fl": ([3, 0, -3], [2, 3, 2], "dark"),
    "leg_br": ([-5, 0, 1], [2, 3, 2], "dark"),
    "leg_bl": ([3, 0, 1], [2, 3, 2], "dark"),
}

ANVIL_MOB_BONES = [
    ("root", None, [0, 0, 0], []),
    ("leg_fr", "root", [-4, 3, -2], ["leg_fr"]),
    ("leg_fl", "root", [4, 3, -2], ["leg_fl"]),
    ("leg_br", "root", [-4, 3, 2], ["leg_br"]),
    ("leg_bl", "root", [4, 3, 2], ["leg_bl"]),
    ("body", "root", [0, 3, 0], ["foot", "waist", "seam"]),
    ("head", "body", [0, 9, 0], ["face", "horn_base", "horn_tip", "heel"]),
]

ANVIL_MOB_PALETTE = {
    "stone": ((118, 116, 112), (150, 148, 144), (76, 74, 72)),
    "iron": ((156, 158, 166), (194, 196, 204), (100, 102, 110)),
    "dark": ((92, 94, 102), (120, 122, 130), (58, 60, 66)),
    "ember": ((70, 32, 14), (94, 44, 18), (34, 16, 6), (255, 152, 56)),
}


def generate_walking_anvil_assets():
    atlas = (128, 128)
    uvs = write_geo("yunque_andante", ANVIL_MOB_CUBES, ANVIL_MOB_BONES, atlas, (2, 1.5, 0.75))
    skin, glow = paint_model(ANVIL_MOB_CUBES, ANVIL_MOB_PALETTE, uvs, atlas, 51001, wear=0.8,
                             rust=(120, 74, 40))
    skin.save(ASSETS / "textures/entity/yunque_andante.png")
    glow.save(ASSETS / "textures/entity/yunque_andante_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/yunque_andante.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # It settles on its legs the way heavy furniture does, and the seam breathes with it.
            "idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "body": pos([(0, [0, 0, 0]), (2.0, [0, -0.35, 0]), (4.0, [0, 0, 0])]),
                    "head": rot([(0, [0, 0, -1]), (2.0, [0, 0, 1]), (4.0, [0, 0, -1])]),
                },
            },
            # Walking is four short legs under far too much weight, so the body rocks side to side and
            # the whole thing takes its time.
            "walk": {
                "loop": True,
                "animation_length": 1.4,
                "bones": {
                    "leg_fr": rot([(0, [-18, 0, 0]), (0.7, [18, 0, 0]), (1.4, [-18, 0, 0])]),
                    "leg_bl": rot([(0, [-18, 0, 0]), (0.7, [18, 0, 0]), (1.4, [-18, 0, 0])]),
                    "leg_fl": rot([(0, [18, 0, 0]), (0.7, [-18, 0, 0]), (1.4, [18, 0, 0])]),
                    "leg_br": rot([(0, [18, 0, 0]), (0.7, [-18, 0, 0]), (1.4, [18, 0, 0])]),
                    "body": bone(
                        rot([(0, [0, 0, 3]), (0.7, [0, 0, -3]), (1.4, [0, 0, 3])]),
                        pos([(0, [0, 0, 0]), (0.35, [0, 0.5, 0]), (0.7, [0, 0, 0]),
                             (1.05, [0, 0.5, 0]), (1.4, [0, 0, 0])]),
                    ),
                },
            },
            # Welding: it plants itself, the seam flares and the weld goes out to whatever it is mending.
            "weld": {
                "loop": False,
                "animation_length": 1.6,
                "bones": {
                    "body": bone(
                        pos([(0, [0, 0, 0]), (0.4, [0, -1.2, 0]), (1.0, [0, -1.0, 0]), (1.6, [0, 0, 0])]),
                        scale([(0, [1, 1, 1]), (0.4, [1.06, 0.9, 1.06]), (1.0, [1.02, 1.05, 1.02]),
                               (1.6, [1, 1, 1])]),
                    ),
                    "head": rot([(0, [0, 0, 0]), (0.4, [8, 0, 0]), (1.0, [-6, 0, 0]), (1.6, [0, 0, 0])]),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the striker

# Percutor: a drop hammer that walks. The ram rides above the shoulder in its own frame, cocked and
# waiting, and the only move it has is that ram coming down — so the rest pose tells you what is
# coming, which is exactly what a mob with a long wind-up needs.
STRIKER_CUBES = {
    "hips": ([-4, 12, -3], [8, 4, 6], "dark"),
    "chest": ([-5.5, 16, -3.5], [11, 9, 7], "iron"),
    "vent": ([-3, 18, -4.5], [6, 4, 1], "ember"),
    "head": ([-3, 25, -3], [6, 5, 6], "dark"),
    "visor": ([-3, 26.5, -4], [6, 2, 1], "ember"),
    "leg_right": ([-5, 0, -3], [5, 12, 6], "iron"),
    "leg_left": ([0, 0, -3], [5, 12, 6], "iron"),
    "arm_left": ([5.5, 12, -3], [5, 12, 6], "iron"),
    "frame_front": ([-13, 16, -5], [4, 20, 3], "iron"),
    "frame_back": ([-13, 16, 2], [4, 20, 3], "iron"),
    "frame_top": ([-14, 36, -5], [6, 3, 10], "stone"),
    "ram": ([-12.5, 26, -2], [3, 12, 4], "dark"),
    "ram_head": ([-16, 20, -6], [10, 7, 12], "stone"),
    "ram_band": ([-16.5, 22, -6.5], [11, 2, 13], "iron"),
    "piston_glow": ([-12.5, 32, -2.5], [3, 4, 5], "ember"),
}

STRIKER_BONES = [
    ("root", None, [0, 0, 0], []),
    ("leg_right", "root", [-2.5, 12, 0], ["leg_right"]),
    ("leg_left", "root", [2.5, 12, 0], ["leg_left"]),
    ("body", "root", [0, 14, 0], ["hips", "chest", "vent"]),
    ("head", "body", [0, 25, 0], ["head", "visor"]),
    ("arm_left", "body", [8, 24, 0], ["arm_left"]),
    ("frame", "body", [-11, 16, 0], ["frame_front", "frame_back", "frame_top"]),
    ("ram", "frame", [-11, 32, 0], ["ram", "ram_head", "ram_band", "piston_glow"]),
]

STRIKER_MOB_PALETTE = {
    "stone": ((118, 116, 112), (150, 148, 144), (76, 74, 72)),
    "iron": ((156, 158, 166), (194, 196, 204), (100, 102, 110)),
    "dark": ((92, 94, 102), (120, 122, 130), (58, 60, 66)),
    "ember": ((70, 32, 14), (94, 44, 18), (34, 16, 6), (255, 152, 56)),
}


def generate_striker_assets():
    atlas = (128, 128)
    uvs = write_geo("percutor", STRIKER_CUBES, STRIKER_BONES, atlas, (2.5, 3, 1.5))
    skin, glow = paint_model(STRIKER_CUBES, STRIKER_MOB_PALETTE, uvs, atlas, 53002, wear=0.7,
                             rust=(120, 74, 40))
    skin.save(ASSETS / "textures/entity/percutor.png")
    glow.save(ASSETS / "textures/entity/percutor_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/percutor.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # The ram never quite settles: it creeps up and drops back a little, like a machine idling.
            "idle": {
                "loop": True,
                "animation_length": 3.4,
                "bones": {
                    "ram": pos([(0, [0, 0, 0]), (1.7, [0, 0.8, 0]), (3.4, [0, 0, 0])]),
                    "body": rot([(0, [0, -2, 0]), (1.7, [0, 2, 0]), (3.4, [0, -2, 0])]),
                    "head": rot([(0, [0, 4, 0]), (1.7, [0, -4, 0]), (3.4, [0, 4, 0])]),
                },
            },
            "walk": {
                "loop": True,
                "animation_length": 1.2,
                "bones": {
                    "leg_right": rot([(0, [-20, 0, 0]), (0.6, [20, 0, 0]), (1.2, [-20, 0, 0])]),
                    "leg_left": rot([(0, [20, 0, 0]), (0.6, [-20, 0, 0]), (1.2, [20, 0, 0])]),
                    "arm_left": rot([(0, [16, 0, 0]), (0.6, [-16, 0, 0]), (1.2, [16, 0, 0])]),
                    # The frame and its ram lag behind the body, which is what weight looks like.
                    "frame": rot([(0, [3, 0, 0]), (0.6, [-3, 0, 0]), (1.2, [3, 0, 0])]),
                    "body": pos([(0, [0, 0, 0]), (0.3, [0, 0.7, 0]), (0.6, [0, 0, 0]),
                                 (0.9, [0, 0.7, 0]), (1.2, [0, 0, 0])]),
                },
            },
            # The drop. Long, because it is the whole mob: the ram winds up to the top of its frame,
            # hangs there, and comes down at 1.9s — which is 38 ticks, and what the code waits for.
            "drop": {
                "loop": False,
                "animation_length": 3.0,
                "bones": {
                    "ram": pos([(0, [0, 0, 0]), (0.6, [0, 5.0, 0]), (1.6, [0, 6.0, 0]),
                                (1.9, [0, -9.0, 0]), (2.2, [0, -7.0, 0]), (3.0, [0, 0, 0])]),
                    "frame": rot([(0, [0, 0, 0]), (0.6, [-6, 0, 0]), (1.6, [-8, 0, 0]),
                                  (1.9, [10, 0, 0]), (3.0, [0, 0, 0])]),
                    "body": bone(
                        rot([(0, [0, 0, 0]), (0.6, [-8, 0, 0]), (1.6, [-10, 0, 0]),
                             (1.9, [14, 0, 0]), (3.0, [0, 0, 0])]),
                        pos([(0, [0, 0, 0]), (1.6, [0, 1.2, 0]), (1.9, [0, -1.6, 0]), (3.0, [0, 0, 0])]),
                    ),
                    "head": rot([(0, [0, 0, 0]), (0.8, [-14, 0, 0]), (1.9, [12, 0, 0]), (3.0, [0, 0, 0])]),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the tongs

# Tenaza: a pair of blacksmith's tongs that stood up, folded like a mantis. The whole idea is that it
# does not look like it can reach you: elbows back and high, forearms forward along the body, jaws open
# at chest height. The first attempt had the forearms straight up beside the head, which in any
# language reads as surrender.
TONGS_CUBES = {
    "hinge": ([-4, 15, -3.5], [8, 9, 7], "iron"),
    "bolt": ([-5.5, 19, -4.5], [11, 3, 9], "gold"),
    "head": ([-3, 25, -4.5], [6, 5, 7], "dark"),
    "eye": ([-3, 27, -5.5], [6, 2, 1], "soul"),
    "belly": ([-3.5, 7, -3], [7, 8, 6], "dark"),
    "leg_right": ([-5, 0, -2.5], [4, 8, 5], "iron"),
    "leg_left": ([1, 0, -2.5], [4, 8, 5], "iron"),
    "arm_right": ([-13, 19, 1], [9, 4, 5], "iron"),
    "arm_left": ([4, 19, 1], [9, 4, 5], "iron"),
    "fore_right": ([-13.5, 16, -9], [4, 4, 14], "iron"),
    "fore_left": ([9.5, 16, -9], [4, 4, 14], "iron"),
    "jaw_right_up": ([-15, 18, -15], [7, 3, 7], "dark"),
    "jaw_right_down": ([-15, 12, -15], [7, 3, 7], "dark"),
    "jaw_left_up": ([8, 18, -15], [7, 3, 7], "dark"),
    "jaw_left_down": ([8, 12, -15], [7, 3, 7], "dark"),
}

TONGS_BONES = [
    ("root", None, [0, 0, 0], []),
    ("leg_right", "root", [-3, 8, 0], ["leg_right"]),
    ("leg_left", "root", [3, 8, 0], ["leg_left"]),
    ("body", "root", [0, 12, 0], ["hinge", "bolt", "belly"], [10, 0, 0]),
    ("head", "body", [0, 25, -1], ["head", "eye"], [-6, 0, 0]),
    ("arm_right", "body", [-4, 21, 2], ["arm_right"], [-14, 0, -12]),
    ("fore_right", "arm_right", [-11.5, 21, 4], ["fore_right", "jaw_right_up", "jaw_right_down"], [16, 0, 6]),
    ("arm_left", "body", [4, 21, 2], ["arm_left"], [-14, 0, 12]),
    ("fore_left", "arm_left", [11.5, 21, 4], ["fore_left", "jaw_left_up", "jaw_left_down"], [16, 0, -6]),
]

TONGS_MOB_PALETTE = {
    "iron": ((156, 158, 166), (194, 196, 204), (100, 102, 110)),
    "dark": ((92, 94, 102), (120, 122, 130), (58, 60, 66)),
    "gold": ((186, 146, 56), (216, 180, 84), (122, 94, 32)),
    "soul": ((26, 60, 64), (36, 80, 84), (16, 36, 38), (150, 232, 238)),
}


def generate_tongs_assets():
    atlas = (128, 128)
    uvs = write_geo("tenaza", TONGS_CUBES, TONGS_BONES, atlas, (3, 2, 1))
    skin, glow = paint_model(TONGS_CUBES, TONGS_MOB_PALETTE, uvs, atlas, 53001, wear=0.5)
    skin.save(ASSETS / "textures/entity/tenaza.png")
    glow.save(ASSETS / "textures/entity/tenaza_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/tenaza.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # Coiled and barely moving. The jaws open a fraction and close again, which is the only
            # thing about it that says it is awake.
            "idle": {
                "loop": True,
                "animation_length": 2.8,
                "bones": {
                    "body": pos([(0, [0, 0, 0]), (1.4, [0, 0.4, 0]), (2.8, [0, 0, 0])]),
                    "head": rot([(0, [0, -6, 0]), (1.4, [0, 6, 0]), (2.8, [0, -6, 0])]),
                    "fore_right": rot([(0, [0, 0, 0]), (1.4, [-4, 0, 0]), (2.8, [0, 0, 0])]),
                    "fore_left": rot([(0, [0, 0, 0]), (1.4, [-4, 0, 0]), (2.8, [0, 0, 0])]),
                },
            },
            "walk": {
                "loop": True,
                "animation_length": 0.9,
                "bones": {
                    "leg_right": rot([(0, [-26, 0, 0]), (0.45, [26, 0, 0]), (0.9, [-26, 0, 0])]),
                    "leg_left": rot([(0, [26, 0, 0]), (0.45, [-26, 0, 0]), (0.9, [26, 0, 0])]),
                    "body": bone(
                        rot([(0, [0, 3, 0]), (0.45, [0, -3, 0]), (0.9, [0, 3, 0])]),
                        pos([(0, [0, 0, 0]), (0.22, [0, 0.5, 0]), (0.45, [0, 0, 0]),
                             (0.67, [0, 0.5, 0]), (0.9, [0, 0, 0])]),
                    ),
                    "arm_right": rot([(0, [-8, 0, 0]), (0.45, [4, 0, 0]), (0.9, [-8, 0, 0])]),
                    "arm_left": rot([(0, [-8, 0, 0]), (0.45, [4, 0, 0]), (0.9, [-8, 0, 0])]),
                },
            },
            # The grab: everything that was folded goes out at once. Contact is at 0.9s, 18 ticks.
            "grab": {
                "loop": False,
                "animation_length": 1.8,
                "bones": {
                    "arm_right": rot([(0, [0, 0, 0]), (0.5, [-18, 0, 0]), (0.9, [38, 0, 14]),
                                      (1.2, [30, 0, 10]), (1.8, [0, 0, 0])]),
                    "arm_left": rot([(0, [0, 0, 0]), (0.5, [-18, 0, 0]), (0.9, [38, 0, -14]),
                                     (1.2, [30, 0, -10]), (1.8, [0, 0, 0])]),
                    "fore_right": rot([(0, [0, 0, 0]), (0.5, [24, 0, 0]), (0.9, [-40, 0, 0]),
                                       (1.8, [0, 0, 0])]),
                    "fore_left": rot([(0, [0, 0, 0]), (0.5, [24, 0, 0]), (0.9, [-40, 0, 0]),
                                      (1.8, [0, 0, 0])]),
                    "body": rot([(0, [0, 0, 0]), (0.5, [-12, 0, 0]), (0.9, [16, 0, 0]), (1.8, [0, 0, 0])]),
                    "head": rot([(0, [0, 0, 0]), (0.5, [-10, 0, 0]), (0.9, [14, 0, 0]), (1.8, [0, 0, 0])]),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the greater ember

# Ascua Mayor: the wisp, grown. Same idea — a cage with something burning in it — at nearly twice the
# size, with a second ring around the first and the fire pushing out between the ribs instead of
# sitting inside them. It has to read as the same creature at a glance and as a worse one a second
# later, which is the whole job of an elite version of anything.
ASCUA_CUBES = {
    "ring_top": ([-7, 16, -7], [14, 3, 14], "iron"),
    "ring_bottom": ([-7, 0, -7], [14, 3, 14], "iron"),
    "rib_fr": ([-7, 2, -7], [3, 15, 3], "iron"),
    "rib_fl": ([4, 2, -7], [3, 15, 3], "iron"),
    "rib_br": ([-7, 2, 4], [3, 15, 3], "iron"),
    "rib_bl": ([4, 2, 4], [3, 15, 3], "iron"),
    "hoop_front": ([-9, 6, -8.5], [18, 3, 3], "dark"),
    "hoop_back": ([-9, 6, 5.5], [18, 3, 3], "dark"),
    "hoop_right": ([-9.5, 6, -8], [3, 3, 16], "dark"),
    "hoop_left": ([6.5, 6, -8], [3, 3, 16], "dark"),
    "fire": ([-5, 2, -5], [10, 14, 10], "molten"),
    "crown": ([-3.5, 16, -3.5], [7, 6, 7], "molten"),
    "wing_right": ([-15, 4, -2], [7, 11, 3], "molten"),
    "wing_left": ([8, 4, -2], [7, 11, 3], "molten"),
    "tail": ([-2, 5, 8], [4, 6, 6], "molten"),
}

ASCUA_BONES = [
    ("root", None, [0, 0, 0], []),
    ("cage", "root", [0, 9, 0], ["ring_top", "ring_bottom", "rib_fr", "rib_fl", "rib_br", "rib_bl",
                                 "hoop_front", "hoop_back", "hoop_right", "hoop_left"]),
    ("fire", "root", [0, 9, 0], ["fire", "crown", "tail"]),
    ("wing_right", "root", [-7, 9, 0], ["wing_right"]),
    ("wing_left", "root", [7, 9, 0], ["wing_left"]),
]

ASCUA_PALETTE = {
    "iron": ((156, 158, 166), (194, 196, 204), (100, 102, 110)),
    "dark": ((92, 94, 102), (120, 122, 130), (58, 60, 66)),
    "molten": ((96, 40, 12), (128, 56, 16), (52, 22, 8), (255, 196, 92)),
}


def generate_greater_ember_assets():
    atlas = (128, 128)
    uvs = write_geo("ascua_mayor", ASCUA_CUBES, ASCUA_BONES, atlas, (2, 1.5, 0.75))
    skin, glow = paint_model(ASCUA_CUBES, ASCUA_PALETTE, uvs, atlas, 54005, wear=0.7, rust=(122, 66, 30))
    skin.save(ASSETS / "textures/entity/ascua_mayor.png")
    glow.save(ASSETS / "textures/entity/ascua_mayor_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/ascua_mayor.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # It hangs and breathes. Slower than the wisp on purpose: weight reads as slowness.
            "idle": {
                "loop": True,
                "animation_length": 3.0,
                "bones": {
                    "root": pos([(0, [0, 0, 0]), (1.5, [0, 1.2, 0]), (3.0, [0, 0, 0])]),
                    "cage": rot([(0, [0, 0, 0]), (1.5, [0, 14, 0]), (3.0, [0, 0, 0])]),
                    "fire": scale([(0, [1, 1, 1]), (1.5, [1.08, 1.12, 1.08]), (3.0, [1, 1, 1])]),
                    "wing_right": rot([(0, [0, 0, 12]), (1.5, [0, 0, 26]), (3.0, [0, 0, 12])]),
                    "wing_left": rot([(0, [0, 0, -12]), (1.5, [0, 0, -26]), (3.0, [0, 0, -12])]),
                },
            },
            "fly": {
                "loop": True,
                "animation_length": 0.8,
                "bones": {
                    "wing_right": rot([(0, [0, 0, 18]), (0.4, [0, 0, 52]), (0.8, [0, 0, 18])]),
                    "wing_left": rot([(0, [0, 0, -18]), (0.4, [0, 0, -52]), (0.8, [0, 0, -18])]),
                    "root": rot([(0, [10, 0, 0]), (0.4, [16, 0, 0]), (0.8, [10, 0, 0])]),
                },
            },
            # The dive: rears, flares, drops. Same shape as the wisp's so the two read as family.
            "dive": {
                "loop": False,
                "animation_length": 1.3,
                "bones": {
                    "root": rot([(0, [14, 0, 0]), (0.25, [-26, 0, 0]), (0.4, [-30, 0, 0]),
                                 (0.45, [58, 0, 0]), (0.75, [40, 0, 0]), (1.3, [14, 0, 0])]),
                    "fire": scale([(0, [1, 1, 1]), (0.25, [1.7, 1.7, 1.7]), (0.4, [1.9, 1.9, 1.9]),
                                   (0.45, [1.4, 1.4, 1.4]), (1.3, [1, 1, 1])]),
                    "cage": scale([(0, [1, 1, 1]), (0.4, [1.1, 0.9, 1.1]), (0.45, [0.85, 1.15, 0.85]),
                                   (1.3, [1, 1, 1])]),
                },
            },
            # Splitting: the cage comes apart and the fire goes everywhere. Played as it dies.
            "split": {
                "loop": False,
                "animation_length": 1.0,
                "bones": {
                    "cage": bone(
                        scale([(0, [1, 1, 1]), (0.4, [1.3, 1.3, 1.3]), (1.0, [1.7, 1.7, 1.7])]),
                        rot([(0, [0, 0, 0]), (1.0, [0, 180, 0])]),
                    ),
                    "fire": scale([(0, [1, 1, 1]), (0.35, [1.9, 1.9, 1.9]), (1.0, [0.2, 0.2, 0.2])]),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the living slag

# Escoria Viviente: a lump of what gets skimmed off the top of a melt, still hot enough to move.
# Cooled crust outside, cracked open where it keeps flexing, and the light coming out of the cracks.
# Wider at the bottom than the top so it reads as something that pours rather than something that
# walks — it has no legs and is not pretending to.
ESCORIA_CUBES = {
    "base": ([-7, 0, -6], [14, 5, 12], "crust"),
    "mid": ([-6, 5, -5], [12, 5, 10], "crust"),
    "top": ([-4, 10, -4], [8, 4, 8], "crust"),
    "lump_right": ([-9, 2, -3], [3, 5, 6], "crust"),
    "lump_left": ([6, 3, -4], [3, 4, 5], "crust"),
    "lump_back": ([-3, 4, 5], [6, 4, 3], "crust"),
    "crack_low": ([-7.5, 4, -6.5], [15, 1.5, 13], "molten"),
    "crack_high": ([-6.5, 9, -5.5], [13, 1.5, 11], "molten"),
    "vent": ([-2, 13, -2], [4, 2, 4], "molten"),
    "eye_right": ([-4, 11, -4.5], [2, 2, 1], "molten"),
    "eye_left": ([2, 11, -4.5], [2, 2, 1], "molten"),
}

ESCORIA_BONES = [
    ("root", None, [0, 0, 0], []),
    ("body", "root", [0, 0, 0], ["base", "mid", "crack_low", "lump_right", "lump_left", "lump_back"]),
    ("head", "body", [0, 9, 0], ["top", "crack_high", "vent", "eye_right", "eye_left"]),
]

ESCORIA_PALETTE = {
    "crust": ((58, 50, 46), (80, 70, 64), (34, 29, 26)),
    "molten": ((96, 40, 12), (128, 56, 16), (52, 22, 8), (255, 196, 92)),
}


def generate_living_slag_assets():
    atlas = (128, 128)
    uvs = write_geo("escoria_viviente", ESCORIA_CUBES, ESCORIA_BONES, atlas, (1.5, 1, 0.5))
    skin, glow = paint_model(ESCORIA_CUBES, ESCORIA_PALETTE, uvs, atlas, 54003, wear=0.9)
    skin.save(ASSETS / "textures/entity/escoria_viviente.png")
    glow.save(ASSETS / "textures/entity/escoria_viviente_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/escoria_viviente.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # It does not breathe, it settles: the crust sinks under its own weight and pushes back up.
            "idle": {
                "loop": True,
                "animation_length": 3.6,
                "bones": {
                    "body": scale([(0, [1, 1, 1]), (1.8, [1.05, 0.93, 1.05]), (3.6, [1, 1, 1])]),
                    "head": bone(
                        pos([(0, [0, 0, 0]), (1.8, [0, -0.7, 0]), (3.6, [0, 0, 0])]),
                        rot([(0, [0, -3, 0]), (1.8, [0, 3, 0]), (3.6, [0, -3, 0])]),
                    ),
                },
            },
            # Moving is a series of slumps forward. Nothing here has a leg.
            "walk": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {
                    "body": bone(
                        scale([(0, [1, 1, 1]), (0.25, [1.12, 0.84, 1.08]), (0.5, [0.94, 1.12, 0.96]),
                               (1.0, [1, 1, 1])]),
                        pos([(0, [0, 0, 0]), (0.25, [0, 0, -0.8]), (0.5, [0, 0.6, 0.4]), (1.0, [0, 0, 0])]),
                    ),
                    "head": pos([(0, [0, 0, 0]), (0.3, [0, -0.5, -0.6]), (0.6, [0, 0.4, 0.3]), (1.0, [0, 0, 0])]),
                },
            },
            # Splitting: it swells until the crust cannot hold and comes apart.
            "split": {
                "loop": False,
                "animation_length": 0.9,
                "bones": {
                    "body": scale([(0, [1, 1, 1]), (0.5, [1.35, 1.25, 1.35]), (0.9, [0.3, 0.3, 0.3])]),
                    "head": bone(
                        pos([(0, [0, 0, 0]), (0.5, [0, 2.5, 0]), (0.9, [0, 6.0, 0])]),
                        scale([(0, [1, 1, 1]), (0.9, [0.2, 0.2, 0.2])]),
                    ),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the rust swarm

# Herrumbre: a flake of rust that got up and started eating. Small enough that only the outline
# survives, so it is low to the ground, cut into three segments with daylight between them, and given
# legs long enough to be seen from the side. The first pass had the segments touching and it came out
# as a single orange lump with no creature in it at all.
RUSTBUG_CUBES = {
    "head": ([-2, 1, -7], [4, 2, 3], "rust"),
    "jaw_right": ([-3, 1, -9], [1, 1, 3], "iron"),
    "jaw_left": ([2, 1, -9], [1, 1, 3], "iron"),
    "thorax": ([-3, 1, -3], [6, 3, 5], "rust"),
    "plate": ([-4, 3.5, -2.5], [8, 1, 4], "scale"),
    "abdomen": ([-2.5, 1, 3], [5, 3, 5], "rust"),
    "tail": ([-1, 1.5, 8], [2, 2, 2], "scale"),
    "leg_a": ([-6, 0, -4], [3, 1, 1], "iron"),
    "leg_b": ([3, 0, -4], [3, 1, 1], "iron"),
    "leg_c": ([-6, 0, -1], [3, 1, 1], "iron"),
    "leg_d": ([3, 0, -1], [3, 1, 1], "iron"),
    "leg_e": ([-6, 0, 4], [3, 1, 1], "iron"),
    "leg_f": ([3, 0, 4], [3, 1, 1], "iron"),
}

RUSTBUG_BONES = [
    ("root", None, [0, 0, 0], []),
    ("body", "root", [0, 1, 0], ["thorax", "plate"]),
    ("tail", "body", [0, 1, 3], ["abdomen", "tail"]),
    ("head", "body", [0, 2, -3], ["head", "jaw_right", "jaw_left"]),
    ("legs", "body", [0, 0, 0], ["leg_a", "leg_b", "leg_c", "leg_d", "leg_e", "leg_f"]),
]

RUSTBUG_PALETTE = {
    "rust": ((138, 78, 40), (170, 102, 54), (92, 50, 24)),
    "iron": ((92, 94, 102), (120, 122, 130), (58, 60, 66)),
    "scale": ((158, 96, 46), (188, 124, 66), (104, 58, 26)),
}


def generate_rustbug_assets():
    """The Herrumbre: a thing that eats metal, drawn out of the metal it ate."""
    atlas = (128, 128)
    uvs = write_geo("herrumbre", RUSTBUG_CUBES, RUSTBUG_BONES, atlas, (1, 0.5, 0.25))
    skin, glow = paint_model(RUSTBUG_CUBES, RUSTBUG_PALETTE, uvs, atlas, 51002, wear=0.9,
                             rust=(150, 88, 44))
    skin.save(ASSETS / "textures/entity/herrumbre.png")
    glow.save(ASSETS / "textures/entity/herrumbre_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/herrumbre.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # Never quite still: the plates on its back lift and settle, the way a woodlouse breathes.
            "idle": {
                "loop": True,
                "animation_length": 2.2,
                "bones": {
                    "body": pos([(0, [0, 0, 0]), (1.1, [0, 0.2, 0]), (2.2, [0, 0, 0])]),
                    "tail": rot([(0, [0, 2, 0]), (1.1, [0, -2, 0]), (2.2, [0, 2, 0])]),
                    "head": rot([(0, [0, -5, 0]), (0.7, [0, 5, 0]), (1.5, [0, -3, 0]), (2.2, [0, -5, 0])]),
                },
            },
            # Scuttling, which is quick and jerky and not a walk at all. The whole body rolls with it
            # because six short legs cannot move a body this wide without the body helping.
            "walk": {
                "loop": True,
                "animation_length": 0.5,
                "bones": {
                    "legs": rot([(0, [0, 0, 5]), (0.25, [0, 0, -5]), (0.5, [0, 0, 5])]),
                    "body": bone(
                        rot([(0, [0, 3, 0]), (0.25, [0, -3, 0]), (0.5, [0, 3, 0])]),
                        pos([(0, [0, 0, 0]), (0.12, [0, 0.35, 0]), (0.25, [0, 0, 0]),
                             (0.37, [0, 0.35, 0]), (0.5, [0, 0, 0])]),
                    ),
                    "tail": rot([(0, [0, -6, 0]), (0.25, [0, 6, 0]), (0.5, [0, -6, 0])]),
                },
            },
            # The bite: it rears, the jaws open, and it comes down on whatever it is standing on.
            "bite": {
                "loop": False,
                "animation_length": 0.6,
                "bones": {
                    "body": rot([(0, [0, 0, 0]), (0.2, [-28, 0, 0]), (0.35, [18, 0, 0]), (0.6, [0, 0, 0])]),
                    "head": rot([(0, [0, 0, 0]), (0.2, [-18, 0, 0]), (0.35, [22, 0, 0]), (0.6, [0, 0, 0])]),
                    "tail": rot([(0, [0, 0, 0]), (0.2, [20, 0, 0]), (0.6, [0, 0, 0])]),
                },
            },
        },
    })


# ---------------------------------------------------------------------------- the ember wisp

# A scrap of the forge that got out: a little iron cage with a coal still burning in it. Everything
# that reads at this size is the light, so the cage is thin and most of the model is the fire.
WISP_CUBES = {
    "ring_top": ([-4, 9, -4], [8, 2, 8], "iron"),
    "ring_bottom": ([-4, 0, -4], [8, 2, 8], "iron"),
    "rib_front_right": ([-4, 1, -4], [2, 9, 2], "iron"),
    "rib_front_left": ([2, 1, -4], [2, 9, 2], "iron"),
    "rib_back_right": ([-4, 1, 2], [2, 9, 2], "iron"),
    "rib_back_left": ([2, 1, 2], [2, 9, 2], "iron"),
    "hook": ([-1, 11, -1], [2, 3, 2], "iron"),
    # The coal, and the heat coming off it.
    "core": ([-3, 1, -3], [7, 9, 7], "ember"),
    "flare": ([-2, 10, -2], [5, 4, 5], "ember"),
    "trail": ([-1, 3, 4], [2, 4, 5], "ember"),
    # A spike under the cage, so the silhouette ends in a point instead of a flat box.
    "spike": ([-2, -2, -2], [4, 2, 4], "iron"),
    # Two sheets of flame it beats like wings. Three segments each, and every one of them narrower,
    # shallower and set a little higher than the last: that sweep is the difference between a wing and
    # a board nailed to a lamp, and the old two-segment version was still reading as the board.
    "wing_right_inner": ([-9, 2, -1], [5, 8, 2], "ember"),
    "wing_right_mid": ([-13, 3, -1], [4, 6, 2], "ember"),
    "wing_right_tip": ([-16, 5, 0], [3, 4, 1], "flame"),
    "wing_left_inner": ([4, 2, -1], [5, 8, 2], "ember"),
    "wing_left_mid": ([9, 3, -1], [4, 6, 2], "ember"),
    "wing_left_tip": ([13, 5, 0], [3, 4, 1], "flame"),
}

WISP_PALETTE = {
    "iron": ((74, 70, 72), (102, 98, 100), (42, 40, 42)),
    "ember": ((84, 34, 12), (112, 48, 16), (44, 16, 6), (255, 168, 62)),
    # The tips of the wings are the hottest part of it, so they are their own colour rather than the
    # same coal as the body: a fire is brightest where it is thinnest.
    "flame": ((186, 96, 24), (226, 138, 40), (128, 56, 14), (255, 232, 158)),
}

WISP_BONES = [
    ("root", None, [0, 0, 0], []),
    ("cage", "root", [0, 6, 0], ["ring_top", "ring_bottom", "rib_front_right", "rib_front_left",
                                 "rib_back_right", "rib_back_left", "hook", "spike"]),
    ("fire", "root", [0, 6, 0], ["core", "flare", "trail"]),
    ("wing_right", "root", [-4, 6, 0], ["wing_right_inner", "wing_right_mid", "wing_right_tip"]),
    ("wing_left", "root", [4, 6, 0], ["wing_left_inner", "wing_left_mid", "wing_left_tip"]),
]


def generate_wisp_assets():
    """The Pavesa: small, quick, and almost all of it light."""
    atlas = (128, 128)
    uvs = write_geo("pavesa", WISP_CUBES, WISP_BONES, atlas, (1.5, 1.5, 0.5))
    skin, glow = paint_model(WISP_CUBES, WISP_PALETTE, uvs, atlas, 410922, wear=0.5, rust=(122, 66, 30))
    skin.save(ASSETS / "textures/entity/pavesa.png")
    glow.save(ASSETS / "textures/entity/pavesa_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/pavesa.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # Hanging in the air: the cage turns slowly, the fire in it does not sit still.
            "idle": {
                "loop": True,
                "animation_length": 2.4,
                "bones": {
                    "root": pos([(0, [0, 0, 0]), (0.6, [0, 0.8, 0]), (1.2, [0, 0, 0]),
                                 (1.8, [0, -0.6, 0]), (2.4, [0, 0, 0])]),
                    "cage": rot([(0, [0, 0, 0]), (1.2, [0, 180, 0]), (2.4, [0, 360, 0])]),
                    "fire": scale([(0, [1, 1, 1]), (0.5, [1.12, 1.18, 1.12]), (1.1, [0.9, 0.86, 0.9]),
                                   (1.7, [1.08, 1.1, 1.08]), (2.4, [1, 1, 1])]),
                    "wing_right": rot([(0, [0, 0, 8]), (0.3, [0, 0, -26]), (0.6, [0, 0, 8]),
                                       (0.9, [0, 0, -26]), (1.2, [0, 0, 8]), (1.5, [0, 0, -26]),
                                       (1.8, [0, 0, 8]), (2.1, [0, 0, -26]), (2.4, [0, 0, 8])]),
                    "wing_left": rot([(0, [0, 0, -8]), (0.3, [0, 0, 26]), (0.6, [0, 0, -8]),
                                      (0.9, [0, 0, 26]), (1.2, [0, 0, -8]), (1.5, [0, 0, 26]),
                                      (1.8, [0, 0, -8]), (2.1, [0, 0, 26]), (2.4, [0, 0, -8])]),
                },
            },
            # Moving: it tips into the direction it is going and beats twice as fast.
            "fly": {
                "loop": True,
                "animation_length": 0.6,
                "bones": {
                    "root": rot([(0, [14, 0, 0]), (0.3, [18, 0, 0]), (0.6, [14, 0, 0])]),
                    "cage": rot([(0, [0, 0, 0]), (0.3, [0, 180, 0]), (0.6, [0, 360, 0])]),
                    "wing_right": rot([(0, [0, 0, 20]), (0.15, [0, 0, -40]), (0.3, [0, 0, 20]),
                                       (0.45, [0, 0, -40]), (0.6, [0, 0, 20])]),
                    "wing_left": rot([(0, [0, 0, -20]), (0.15, [0, 0, 40]), (0.3, [0, 0, -20]),
                                      (0.45, [0, 0, 40]), (0.6, [0, 0, -20])]),
                    "fire": pos([(0, [0, 0, 0]), (0.3, [0, 0, 0.8]), (0.6, [0, 0, 0])]),
                },
            },
            # The dive: it tucks the cage in, the fire goes ahead of it and it drops on you.
            "dive": {
                "loop": False,
                "animation_length": 0.9,
                "bones": {
                    "root": rot([(0, [14, 0, 0]), (0.15, [58, 0, 0]), (0.5, [40, 0, 0]), (0.9, [14, 0, 0])]),
                    "fire": bone(
                        scale([(0, [1, 1, 1]), (0.15, [1.5, 1.5, 1.5]), (0.5, [1.2, 1.2, 1.2]), (0.9, [1, 1, 1])]),
                        pos([(0, [0, 0, 0]), (0.15, [0, 0, -2.5]), (0.9, [0, 0, 0])]),
                    ),
                    "wing_right": rot([(0, [0, 0, 20]), (0.15, [0, 0, 62]), (0.9, [0, 0, 20])]),
                    "wing_left": rot([(0, [0, 0, -20]), (0.15, [0, 0, -62]), (0.9, [0, 0, -20])]),
                    "cage": scale([(0, [1, 1, 1]), (0.15, [0.8, 0.8, 0.8]), (0.9, [1, 1, 1])]),
                },
            },
            # Fed: near a fire it swells, and stays swollen while the heat lasts.
            "flare": {
                "loop": True,
                "animation_length": 1.2,
                "bones": {
                    "fire": scale([(0, [1.45, 1.45, 1.45]), (0.4, [1.7, 1.75, 1.7]),
                                   (0.8, [1.35, 1.3, 1.35]), (1.2, [1.45, 1.45, 1.45])]),
                    "cage": bone(
                        rot([(0, [0, 0, 0]), (0.6, [0, 180, 0]), (1.2, [0, 360, 0])]),
                        scale([(0, [1.1, 1.1, 1.1]), (1.2, [1.1, 1.1, 1.1])]),
                    ),
                    "wing_right": rot([(0, [0, 0, 26]), (0.15, [0, 0, -46]), (0.3, [0, 0, 26]),
                                       (0.45, [0, 0, -46]), (0.6, [0, 0, 26]), (0.75, [0, 0, -46]),
                                       (0.9, [0, 0, 26]), (1.05, [0, 0, -46]), (1.2, [0, 0, 26])]),
                    "wing_left": rot([(0, [0, 0, -26]), (0.15, [0, 0, 46]), (0.3, [0, 0, -26]),
                                      (0.45, [0, 0, 46]), (0.6, [0, 0, -26]), (0.75, [0, 0, 46]),
                                      (0.9, [0, 0, -26]), (1.05, [0, 0, 46]), (1.2, [0, 0, -26])]),
                },
            },
        },
    })


def generate_ember_texture():
    """An ascua: a knot of coal that never went out, drawn hot in the middle and crusted at the edge."""
    rng = __import__("random").Random(410924)
    ember = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 8.0
            d = (dx * dx + dy * dy * 1.05) ** 0.5 + rng.random() * 0.9
            if d > 6.2:
                continue
            if d > 5.0:
                shade = 34 + rng.randint(0, 14)
                ember.putpixel((x, y), (shade, shade - 6, shade - 8, 255))
            elif d > 2.6:
                heat = 1.0 - (d - 2.6) / 2.4
                ember.putpixel((x, y), (int(120 + 135 * heat), int(40 + 90 * heat), int(18 + 30 * heat), 255))
            else:
                glow = 1.0 - d / 2.8
                ember.putpixel((x, y), (255, int(170 + 70 * glow), int(80 + 120 * glow), 255))
    # A crack or two, where the crust has split and the fire shows through.
    for y, x in ((4, 6), (5, 6), (11, 9), (12, 9), (8, 12)):
        ember.putpixel((x, y), (255, 226, 150, 255))
    ember.save(ASSETS / "textures/item/ascua.png")


def generate_slag_texture():
    """Escoria: the crust off the top of a melt, cooled. Dull, lumpy, with the last heat in the seams.

    Deliberately ugly next to the ascua it sits beside in the inventory. An ascua is a fire somebody
    kept; escoria is what was skimmed off and thrown away, and the only reason it is worth anything is
    that something got up out of it.
    """
    rng = __import__("random").Random(54003)
    slag = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # An irregular lump rather than a disc: nothing about this was shaped on purpose.
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 8.5
            d = (dx * dx * 1.15 + dy * dy) ** 0.5 + rng.random() * 1.6
            if d > 6.4:
                continue
            shade = 52 + rng.randint(0, 26) - int(d * 2)
            slag.putpixel((x, y), (shade + 6, shade, shade - 4, 255))
    # The seams, where it has not finished cooling.
    for y, x in ((5, 7), (6, 6), (6, 8), (9, 5), (10, 6), (10, 10), (11, 11), (12, 7)):
        heat = rng.random()
        slag.putpixel((x, y), (int(180 + 60 * heat), int(78 + 60 * heat), int(24 + 20 * heat), 255))
    slag.save(ASSETS / "textures/item/escoria.png")


def generate_melt_tank_assets():
    """The tank: an iron frame with glass in it, and five models for how full the glass looks.

    The metal inside is drawn in greyscale and tinted at runtime by the block colour handler, so one
    texture covers every metal there will ever be.
    """
    rng = __import__("random").Random(771221)
    folder = ASSETS / "textures/block"
    folder.mkdir(parents=True, exist_ok=True)
    # The tank is bronze and glass by recipe, so it should be bronze and glass to look at: corner
    # posts, a band top and bottom, rivets, and a pane you can actually see the metal through. The
    # old one was grey ironwork with a hole in it, which is why it read as a quartz block.
    BRONZE = (168, 112, 58)
    BRONZE_L = (216, 158, 92)
    BRONZE_D = (104, 66, 32)
    glass = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            post = x < 2 or x > 13
            band = y < 2 or y > 13
            if post or band:
                grain = rng.randint(-9, 9)
                base = BRONZE_L if (y < 2 or x < 2) else (BRONZE_D if (y > 13 or x > 13) else BRONZE)
                glass.putpixel((x, y), tuple(max(0, min(255, c + grain)) for c in base) + (255,))
            else:
                # The pane: almost clear, so the melt the renderer draws is what you see.
                glass.putpixel((x, y), (214, 226, 232, 38))
    # Rivets on the bands, which is the detail that makes bronze read as bronze and not as orange.
    for x in range(3, 14, 4):
        glass.putpixel((x, 1), BRONZE_L + (255,))
        glass.putpixel((x, 14), BRONZE_D + (255,))
    for y in range(3, 14, 4):
        glass.putpixel((1, y), BRONZE_L + (255,))
        glass.putpixel((14, y), BRONZE_D + (255,))
    # And the highlights that tell you there is glass there at all.
    for x, y in ((4, 4), (5, 4), (4, 5), (11, 10), (11, 11)):
        glass.putpixel((x, y), (255, 255, 255, 120))
    glass.save(folder / "cuba_de_colada.png")

    # The melt. It is painted white-hot and greyscale on purpose: the renderer multiplies it by the
    # metal's own colour, so one texture has to serve gold, obsidian steel and everything between. It
    # tiles top to bottom because the renderer scrolls it, which is what gives it the crawl of lava.
    melt = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            # Two waves at different rates, wrapped so the seam does not show when it scrolls.
            import math as _math
            swirl = _math.sin(y / 16.0 * _math.tau + x * 0.4) * 0.5 + _math.sin(x / 16.0 * _math.tau * 2) * 0.3
            shade = 0.82 + 0.20 * swirl + rng.random() * 0.08
            level = max(0.58, min(1.0, shade))
            melt.putpixel((x, y), (int(255 * level), int(255 * level), int(255 * level), 255))
    # A few darker veins riding on it. They are kept light on purpose: the renderer multiplies this by
    # the metal's colour, and anything dark here comes out as mud rather than as metal.
    for _ in range(12):
        cx, cy = rng.randrange(16), rng.randrange(16)
        for dx in range(rng.randrange(1, 3)):
            for dy in range(rng.randrange(1, 3)):
                melt.putpixel(((cx + dx) % 16, (cy + dy) % 16), (176, 176, 176, 255))
    melt.save(folder / "colada.png")

    # One model: the frame. The metal inside is drawn by client/MeltTankRenderer, which is the only
    # way it can flow, be see-through and take a different colour for every metal.
    write_json(ASSETS / "models/block/cuba_de_colada.json", {
        "parent": "minecraft:block/cube_all",
        # Without this the pane is drawn solid and every tank looks like a painted box.
        "render_type": "minecraft:translucent",
        "textures": {"all": "forja:block/cuba_de_colada", "particle": "forja:block/cuba_de_colada"},
    })
    write_json(ASSETS / "blockstates/cuba_de_colada.json", {"variants": {
        f"level={level}": {"model": "forja:block/cuba_de_colada"} for level in range(5)
    }})
    write_json(ASSETS / "items/cuba_de_colada.json",
               {"model": {"type": "minecraft:model", "model": "forja:block/cuba_de_colada"}})
    write_json(DATA / "loot_table/blocks/cuba_de_colada.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "forja:cuba_de_colada"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "forja:blocks/cuba_de_colada",
    })
    # ---- the casting boxes: a real moulding flask, which is a thing with a shape of its own.
    #
    # A flask is two halves clamped together over a bed of sand, with a cup at one corner you pour
    # into and a runner cut from the cup to the cavity. Draw those and the block stops being a brown
    # square with a hole: you can see where the metal goes in and what is going to come out.
    BOXES = {
        # (frame, light, dark, sand)
        "caja_de_moldeo": ((150, 104, 70), (196, 152, 106), (96, 64, 40), (198, 178, 140)),
        "caja_de_moldeo_de_acero": ((122, 124, 132), (188, 190, 200), (70, 72, 80), (206, 194, 168)),
        "caja_de_moldeo_de_damasco": ((96, 98, 110), (168, 170, 186), (52, 54, 64), (192, 186, 176)),
    }
    for name, (frame, light, dark, sand) in BOXES.items():
        for lit in (False, True):
            side = forge_grain(Image.new("RGBA", (16, 16)), frame, rng, 8)
            top = forge_grain(Image.new("RGBA", (16, 16)), sand, rng, 11)

            # ---- the side: two halves, clamped.
            for x in range(16):
                side.putpixel((x, 0), light + (255,))
                side.putpixel((x, 15), dark + (255,))
            # The parting line the two halves meet on, and the one hairline of light that gets out of
            # a flask with metal still in it.
            for x in range(16):
                side.putpixel((x, 7), dark + (255,))
                side.putpixel((x, 8), (light if not lit else (255, 196, 110)) + (255,))
            # Clamps at the four corners, which is what a flask actually looks like.
            for cx in (1, 12):
                for cy in (2, 11):
                    for dx in range(3):
                        side.putpixel((cx + dx, cy), light + (255,))
                        side.putpixel((cx + dx, cy + 2), dark + (255,))
                    side.putpixel((cx + 1, cy + 1), dark + (255,))
            # And the two handles it is carried by.
            for hy in (5, 10):
                for x in (0, 15):
                    side.putpixel((x, hy), light + (255,))
                    side.putpixel((x, hy + 1), dark + (255,))

            # ---- the top: sand, a pouring cup, a runner, and the cavity it feeds.
            for i in range(16):
                top.putpixel((i, 0), light + (255,))
                top.putpixel((0, i), light + (255,))
                top.putpixel((i, 15), dark + (255,))
                top.putpixel((15, i), dark + (255,))
            glow = (255, 186, 92)
            # The cup: a funnel in the corner, which is where you actually pour.
            for y in range(2, 6):
                for x in range(2, 6):
                    d = max(abs(x - 3.5), abs(y - 3.5))
                    if d >= 1.5:
                        top.putpixel((x, y), tuple(int(c * 0.62) for c in sand) + (255,))
                    else:
                        top.putpixel((x, y), (glow if lit else (48, 40, 32)) + (255,))
            # The runner, cut from the cup down into the mould.
            for step in range(5):
                x, y = 5 + step, 5 + step
                top.putpixel((x, y), (glow if lit else (56, 46, 36)) + (255,))
                top.putpixel((x, y + 1), tuple(int(c * 0.55) for c in sand) + (255,))
            # The cavity: a part lying in the sand, waiting to be a part.
            for y in range(9, 13):
                for x in range(5, 13):
                    edge = y in (9, 12) or x in (5, 12)
                    if edge:
                        top.putpixel((x, y), tuple(int(c * 0.52) for c in sand) + (255,))
                    elif lit:
                        heat = 1.0 - abs(y - 10.5) / 2.4
                        top.putpixel((x, y), (255, int(140 + 90 * heat), int(40 + 50 * heat), 255))
                    else:
                        top.putpixel((x, y), (42, 36, 30, 255))
            # The vent at the far end, so the air has somewhere to go.
            top.putpixel((13, 10), (glow if lit else (56, 46, 36)) + (255,))
            top.putpixel((13, 11), tuple(int(c * 0.55) for c in sand) + (255,))
            suffix = "_lit" if lit else ""
            side.save(folder / f"{name}_side{suffix}.png")
            top.save(folder / f"{name}_top{suffix}.png")
            write_json(ASSETS / f"models/block/{name}{suffix}.json", {
                "parent": "minecraft:block/cube_bottom_top",
                "textures": {
                    "top": f"forja:block/{name}_top{suffix}",
                    "bottom": f"forja:block/{name}_side",
                    "side": f"forja:block/{name}_side{suffix}",
                    "particle": f"forja:block/{name}_side{suffix}",
                },
            })
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {
            "lit=false": {"model": f"forja:block/{name}"},
            "lit=true": {"model": f"forja:block/{name}_lit"},
        }})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"forja:block/{name}"}})
        write_json(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"forja:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"forja:blocks/{name}",
        })
    # Each box is the one below it re-lined in something that stands more heat.
    write_json(DATA / "recipe/caja_de_moldeo.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["P P", "PSP", "PPP"],
        "key": {"P": "forja:peltre", "S": "minecraft:sand"},
        "result": {"id": "forja:caja_de_moldeo", "count": 1},
    })
    write_json(DATA / "recipe/caja_de_moldeo_de_acero.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["R R", "RCR", "RRR"],
        "key": {"R": "forja:acero_refractario", "C": "forja:caja_de_moldeo"},
        "result": {"id": "forja:caja_de_moldeo_de_acero", "count": 1},
    })
    write_json(DATA / "recipe/caja_de_moldeo_de_damasco.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["DRD", "DCD", "DDD"],
        "key": {"D": "forja:damasco", "R": "forja:acero_refractario", "C": "forja:caja_de_moldeo_de_acero"},
        "result": {"id": "forja:caja_de_moldeo_de_damasco", "count": 1},
    })

    # ---- the moulds: a plate of refractory steel per part, with that part's shape sunk into it.
    #
    # One generic sprite told you nothing: a drawer of thirty moulds was thirty identical tiles. The
    # engraved templates already solved this — the shape of the thing is cut into the face — so the
    # moulds and the frames do the same, in steel instead of wood.
    def steel_plate():
        plate = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y in range(2, 15):
            for x in range(1, 15):
                grain = rng.randint(-10, 10)
                plate.putpixel((x, y), (216 + grain, 195 + grain, 160 + grain, 255))
        for x in range(1, 15):
            plate.putpixel((x, 2), (246, 232, 204, 255))
            plate.putpixel((x, 14), (150, 130, 100, 255))
        for y in range(2, 15):
            plate.putpixel((1, y), (246, 232, 204, 255))
            plate.putpixel((14, y), (150, 130, 100, 255))
        return plate

    def sink(shape):
        """The hollow: dark where the metal will sit, with a lit lip on its lower right."""
        carved = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        inside = {point for point in shape if 2 <= point[0] <= 13 and 3 <= point[1] <= 13}
        for (x, y) in inside:
            carved.putpixel((x, y), (70, 58, 44, 255))
        for (x, y) in inside:
            below = (x + 1, y + 1)
            if below not in inside and 2 <= below[0] <= 13 and 3 <= below[1] <= 13:
                carved.putpixel(below, (250, 236, 206, 255))
        return carved

    # Four types are drawn from their own sprites rather than from numbered layers, so their shape is
    # read back off those sprites instead. A frame with no shape in it would be a blank plate, which is
    # the very thing this is fixing.
    def shape_from(parts):
        found = set()
        for name in parts:
            path = ASSETS / f"textures/item/{name}.png"
            if not path.exists():
                continue
            image = Image.open(path).convert("RGBA")
            for y in range(image.height):
                for x in range(image.width):
                    if image.getpixel((x, y))[3]:
                        found.add((x, y))
        return found

    tools = dict(TOOL_LAYERS)
    tools["arco"] = lambda: shape_from(["arco/bow_0", "arco/bow_1", "arco/bow_2"])
    tools["ballesta"] = lambda: shape_from([f"ballesta/crossbow_standby_{i}" for i in range(4)])
    tools["cana"] = lambda: shape_from(["cana/fishing_rod_0", "cana/fishing_rod_1"])
    tools["escudo"] = lambda: shape_from(["escudo/placa", "escudo/borde", "escudo/asa"])

    # `kind` rather than `folder`: this function already has a `folder` and it is not this one.
    for kind, shapes in (("molde", PART_LAYERS), ("marco", tools)):
        out = ASSETS / f"textures/item/{kind}"
        shutil.rmtree(out, ignore_errors=True)
        out.mkdir(parents=True, exist_ok=True)
        steel_plate().save(out / "base.png")
        cases = []
        for name, build in shapes.items():
            sink(set(build())).save(out / f"{name}.png")
            write_json(ASSETS / f"models/item/{kind}/{name}.json",
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"forja:item/{kind}/{name}"}})
            cases.append({"when": name, "model": {"type": "minecraft:model", "model": f"forja:item/{kind}/{name}"}})
        write_json(ASSETS / f"models/item/{kind}/base.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"forja:item/{kind}/base"}})
        item = "molde_de_fundicion" if kind == "molde" else "marco"
        write_json(ASSETS / f"items/{item}.json", {
            "model": {
                "type": "minecraft:composite",
                "models": [
                    {"type": "minecraft:model", "model": f"forja:item/{kind}/base"},
                    {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                     "cases": cases},
                ],
            },
        })

    # ---- the channels: three grades of the same floor channel (design B1).
    #
    # Not a tube. A melt pipe is something you lay in the floor and walk over, with the metal sunk flush
    # into it where you can see it: the whole point of a foundry is being able to read it at a glance,
    # and a 6x6 tube floating in the middle of the block hid the one thing worth looking at.
    #
    # The stone is the same on all three grades and only the metal kerb changes, so a run that mixes
    # grades still reads as one line across the floor instead of as a repair.
    #
    # Geometry, in texels: the slab is 0..4 and the walking surface 4..6. The channel runs down the
    # middle of the block, 5..11 across, with a kerb at 5..6 and 10..11 and the metal between them at
    # 6..10, its surface at 5.5 -- half a texel below your boots.
    stone = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            grain = rng.randint(-10, 10)
            stone.putpixel((x, y), (108 + grain, 108 + grain, 112 + grain, 255))
    # A course of paving, so a long run does not read as one smeared grey ribbon.
    for x in range(16):
        stone.putpixel((x, 0), (86, 86, 90, 255))
        stone.putpixel((x, 15), (132, 132, 136, 255))
    for y in range(16):
        stone.putpixel((0, y), (86, 86, 90, 255))
        stone.putpixel((15, y), (132, 132, 136, 255))
    stone.save(folder / "canal_piedra.png")

    melt = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            # Hotter down the middle of the band, the way it is drawn in the tanks.
            heat = 1.0 - abs(y - 7.5) / 8.0
            grain = rng.randint(-12, 12)
            melt.putpixel((x, y), (
                255,
                max(0, min(255, int(120 + 90 * heat) + grain)),
                max(0, min(255, int(26 + 54 * heat) + grain)),
                255,
            ))
    melt.save(folder / "canal_metal.png")

    PIPES = {
        "conducto_de_colada": ((128, 86, 44), (150, 104, 56), (196, 150, 96)),
        "conducto_de_acero": ((120, 122, 130), (146, 148, 158), (188, 190, 200)),
        "conducto_de_damasco": ((98, 100, 112), (126, 128, 142), (176, 178, 194)),
    }

    def faces(box, textures):
        """One element, textured at world scale.

        Every face takes its UVs from where the box actually is, not from the whole 16x16 sheet: a
        five-texel arm that stretched the sheet over itself banded visibly at every block seam, and a
        long run of channel is nothing but block seams.
        """
        (x0, y0, z0), (x1, y1, z1) = box
        uv = {
            "north": [x0, 16 - y1, x1, 16 - y0],
            "south": [x0, 16 - y1, x1, 16 - y0],
            "east": [z0, 16 - y1, z1, 16 - y0],
            "west": [z0, 16 - y1, z1, 16 - y0],
            "up": [x0, z0, x1, z1],
            "down": [x0, z0, x1, z1],
        }
        return {"from": list(box[0]), "to": list(box[1]),
                "faces": {side: {"uv": uv[side], "texture": textures}
                          for side in ("north", "south", "east", "west", "up", "down")}}

    for name, (body, band, rivet) in PIPES.items():
        kerb = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                shade = band if y % 5 == 0 else body
                grain = rng.randint(-8, 8)
                kerb.putpixel((x, y), tuple(max(0, min(255, c + grain)) for c in shade) + (255,))
        for y in range(0, 16, 5):
            for x in range(2, 16, 5):
                kerb.putpixel((x, y), rivet + (255,))
        kerb.save(folder / f"{name}.png")

        textures = {
            "piedra": "forja:block/canal_piedra",
            "metal": "forja:block/canal_metal",
            "borde": f"forja:block/{name}",
            "particle": "forja:block/canal_piedra",
        }

        # The four pieces below tile the top of the block exactly, with nothing overlapping anything:
        # the block is a 16x16 floor, the channel is the strip x6..10 down the middle of it, and the
        # kerb is the texel on either side of that. The core owns the centre and the four corners; each
        # side owns its own 5-deep slice and is either an arm or a cap.
        #
        # The metal is the same width everywhere on purpose. An earlier cut had the pool at the block
        # centre wider than the arms, and a long run visibly bulged once a block.

        # The core: the slab, the four corners of walking surface, the kerb corners, and the pool.
        write_json(ASSETS / f"models/block/{name}_core.json", {
            "parent": "minecraft:block/block",
            "textures": textures,
            "elements": [
                faces(((0, 0, 0), (16, 4, 16)), "#piedra"),
                faces(((0, 4, 0), (5, 6, 5)), "#piedra"),
                faces(((11, 4, 0), (16, 6, 5)), "#piedra"),
                faces(((0, 4, 11), (5, 6, 16)), "#piedra"),
                faces(((11, 4, 11), (16, 6, 16)), "#piedra"),
                faces(((5, 4, 5), (6, 6, 6)), "#borde"),
                faces(((10, 4, 5), (11, 6, 6)), "#borde"),
                faces(((5, 4, 10), (6, 6, 11)), "#borde"),
                faces(((10, 4, 10), (11, 6, 11)), "#borde"),
                faces(((6, 4, 6), (10, 5.5, 10)), "#metal"),
            ],
        })

        # An arm: where a run leaves this block, a kerb each side and the metal between them, carried
        # one texel into the centre so it meets the pool without a seam.
        write_json(ASSETS / f"models/block/{name}_arm.json", {
            "parent": "minecraft:block/block",
            "textures": textures,
            "elements": [
                faces(((5, 4, 0), (6, 6, 5)), "#borde"),
                faces(((10, 4, 0), (11, 6, 5)), "#borde"),
                faces(((6, 4, 0), (10, 5.5, 6)), "#metal"),
            ],
        })

        # A cap: where no run leaves, the floor carries on over it and the channel is walled off.
        write_json(ASSETS / f"models/block/{name}_cap.json", {
            "parent": "minecraft:block/block",
            "textures": textures,
            "elements": [
                faces(((5, 4, 0), (11, 6, 5)), "#piedra"),
                faces(((6, 4, 5), (10, 6, 6)), "#borde"),
            ],
        })

        # And the riser: a channel cannot climb a wall, so a run going up falls back to a closed tube.
        write_json(ASSETS / f"models/block/{name}_riser.json", {
            "parent": "minecraft:block/block",
            "textures": textures,
            "elements": [faces(((5, 6, 5), (11, 16, 11)), "#borde")],
        })

        write_json(ASSETS / f"blockstates/{name}.json", {"multipart": [
            {"apply": {"model": f"forja:block/{name}_core"}},
            {"when": {"north": "true"}, "apply": {"model": f"forja:block/{name}_arm"}},
            {"when": {"north": "false"}, "apply": {"model": f"forja:block/{name}_cap"}},
            {"when": {"south": "true"}, "apply": {"model": f"forja:block/{name}_arm", "y": 180}},
            {"when": {"south": "false"}, "apply": {"model": f"forja:block/{name}_cap", "y": 180}},
            {"when": {"east": "true"}, "apply": {"model": f"forja:block/{name}_arm", "y": 90}},
            {"when": {"east": "false"}, "apply": {"model": f"forja:block/{name}_cap", "y": 90}},
            {"when": {"west": "true"}, "apply": {"model": f"forja:block/{name}_arm", "y": 270}},
            {"when": {"west": "false"}, "apply": {"model": f"forja:block/{name}_cap", "y": 270}},
            {"when": {"up": "true"}, "apply": {"model": f"forja:block/{name}_riser"}},
        ]})

        # In the hand it is a straight length of channel, which is what you are about to lay.
        write_json(ASSETS / f"models/block/{name}_inventory.json", {
            "parent": "minecraft:block/block",
            "textures": textures,
            "elements": [
                faces(((0, 0, 0), (16, 4, 16)), "#piedra"),
                faces(((0, 4, 0), (16, 6, 5)), "#piedra"),
                faces(((0, 4, 11), (16, 6, 16)), "#piedra"),
                faces(((0, 4, 5), (16, 6, 6)), "#borde"),
                faces(((0, 4, 10), (16, 6, 11)), "#borde"),
                faces(((0, 4, 6), (16, 5.5, 10)), "#metal"),
            ],
        })
        write_json(ASSETS / f"items/{name}.json",
                   {"model": {"type": "minecraft:model", "model": f"forja:block/{name}_inventory"}})
        write_json(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"forja:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"forja:blocks/{name}",
        })
    # ---- the spout: the same channel with its floor cut out, so what reaches it falls through.
    spout = "cano_de_colada"
    spout_textures = {
        "piedra": "forja:block/canal_piedra",
        "metal": "forja:block/canal_metal",
        "borde": "forja:block/conducto_de_colada",
        "particle": "forja:block/canal_metal",
    }
    # The slab is a ring: everything but the mouth at x6..10, z6..10, which goes clean through. Around
    # the mouth, a bronze sleeve, so the hole reads as a fitting and not as a block that failed to load.
    write_json(ASSETS / f"models/block/{spout}_core.json", {
        "parent": "minecraft:block/block",
        "textures": spout_textures,
        "elements": [
            faces(((0, 0, 0), (16, 4, 5)), "#piedra"),
            faces(((0, 0, 11), (16, 4, 16)), "#piedra"),
            faces(((0, 0, 5), (5, 4, 11)), "#piedra"),
            faces(((11, 0, 5), (16, 4, 11)), "#piedra"),
            faces(((5, 0, 5), (6, 4, 11)), "#borde"),
            faces(((10, 0, 5), (11, 4, 11)), "#borde"),
            faces(((6, 0, 5), (10, 4, 6)), "#borde"),
            faces(((6, 0, 10), (10, 4, 11)), "#borde"),
            faces(((0, 4, 0), (5, 6, 5)), "#piedra"),
            faces(((11, 4, 0), (16, 6, 5)), "#piedra"),
            faces(((0, 4, 11), (5, 6, 16)), "#piedra"),
            faces(((11, 4, 11), (16, 6, 16)), "#piedra"),
            faces(((5, 4, 5), (6, 6, 6)), "#borde"),
            faces(((10, 4, 5), (11, 6, 6)), "#borde"),
            faces(((5, 4, 10), (6, 6, 11)), "#borde"),
            faces(((10, 4, 10), (11, 6, 11)), "#borde"),
        ],
    })
    # It wears the bronze channel's own arms and caps: a spout is a channel that gave up holding on.
    write_json(ASSETS / f"blockstates/{spout}.json", {"multipart": [
        {"apply": {"model": f"forja:block/{spout}_core"}},
        {"when": {"north": "true"}, "apply": {"model": "forja:block/conducto_de_colada_arm"}},
        {"when": {"north": "false"}, "apply": {"model": "forja:block/conducto_de_colada_cap"}},
        {"when": {"south": "true"}, "apply": {"model": "forja:block/conducto_de_colada_arm", "y": 180}},
        {"when": {"south": "false"}, "apply": {"model": "forja:block/conducto_de_colada_cap", "y": 180}},
        {"when": {"east": "true"}, "apply": {"model": "forja:block/conducto_de_colada_arm", "y": 90}},
        {"when": {"east": "false"}, "apply": {"model": "forja:block/conducto_de_colada_cap", "y": 90}},
        {"when": {"west": "true"}, "apply": {"model": "forja:block/conducto_de_colada_arm", "y": 270}},
        {"when": {"west": "false"}, "apply": {"model": "forja:block/conducto_de_colada_cap", "y": 270}},
        {"when": {"up": "true"}, "apply": {"model": "forja:block/conducto_de_colada_riser"}},
    ]})
    write_json(ASSETS / f"models/block/{spout}_inventory.json", {
        "parent": "minecraft:block/block",
        "textures": spout_textures,
        "elements": [
            faces(((0, 0, 0), (16, 4, 5)), "#piedra"),
            faces(((0, 0, 11), (16, 4, 16)), "#piedra"),
            faces(((0, 0, 5), (5, 4, 11)), "#piedra"),
            faces(((11, 0, 5), (16, 4, 11)), "#piedra"),
            faces(((5, 0, 5), (6, 6, 11)), "#borde"),
            faces(((10, 0, 5), (11, 6, 11)), "#borde"),
            faces(((6, 0, 5), (10, 6, 6)), "#borde"),
            faces(((6, 0, 10), (10, 6, 11)), "#borde"),
            faces(((0, 4, 0), (16, 6, 5)), "#piedra"),
            faces(((0, 4, 11), (16, 6, 16)), "#piedra"),
        ],
    })
    write_json(ASSETS / f"items/{spout}.json",
               {"model": {"type": "minecraft:model", "model": f"forja:block/{spout}_inventory"}})
    write_json(DATA / f"loot_table/blocks/{spout}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"forja:{spout}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"forja:blocks/{spout}",
    })
    write_json(DATA / f"recipe/{spout}.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["B B", "BCB"],
        "key": {"B": "forja:bronce", "C": "forja:conducto_de_colada"},
        "result": {"id": f"forja:{spout}", "count": 1},
    })

    # Bronze is crafted; the better two are the one below them re-lined, the way the casting boxes go.
    write_json(DATA / "recipe/conducto_de_colada.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["BBB", "   ", "BBB"],
        "key": {"B": "forja:bronce"},
        "result": {"id": "forja:conducto_de_colada", "count": 8},
    })
    write_json(DATA / "recipe/conducto_de_acero.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["AAA", "CCC", "AAA"],
        "key": {"A": "forja:acero", "C": "forja:conducto_de_colada"},
        "result": {"id": "forja:conducto_de_acero", "count": 3},
    })
    write_json(DATA / "recipe/conducto_de_damasco.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["DDD", "CCC", "DDD"],
        "key": {"D": "forja:damasco", "C": "forja:conducto_de_acero"},
        "result": {"id": "forja:conducto_de_damasco", "count": 3},
    })

    # ---- the strainer: a grate, and a real one, with bars you can see between.
    #
    # It used to be a flat clay tile with nine dots punched in it, and it read as a biscuit. A strainer
    # is a grate: a frame with bars crossed over it, and the whole point of it is the gaps. So it is
    # built as geometry rather than drawn as a sprite, and the sheet below is only the metal the bars
    # are cut from — grey on purpose, because the item is tinted by whatever metal it was last
    # infused with.
    metal = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            grain = rng.randint(-10, 10)
            # Top half: the face of a bar, lit. Bottom half: its side, in shadow.
            base = 208 if y < 8 else 150
            metal.putpixel((x, y), (base + grain, base + grain - 4, base + grain - 8, 255))
    # A drawn edge along the top and the bottom of each band, so a bar has an edge at any size.
    for x in range(16):
        metal.putpixel((x, 0), (238, 236, 230, 255))
        metal.putpixel((x, 7), (112, 108, 104, 255))
        metal.putpixel((x, 8), (186, 182, 176, 255))
        metal.putpixel((x, 15), (86, 82, 80, 255))
    # And the pitting the melt leaves on something it has been poured through.
    for _ in range(14):
        metal.putpixel((rng.randrange(16), rng.randrange(1, 15)), (118, 112, 106, 255))
    metal.save(ASSETS / "textures/item/colador.png")

    # The grate itself: a frame and four bars, all of them one plate thick and lying flat.
    def bar(x0, z0, x1, z1, uv):
        return {"from": [x0, 7, z0], "to": [x1, 9, z1],
                "faces": {name: {"uv": uv, "texture": "#metal", "tintindex": 0}
                          for name in ("north", "south", "east", "west", "up", "down")}}

    write_json(ASSETS / "models/item/colador.json", {
        "parent": "minecraft:block/block",
        "textures": {"metal": "forja:item/colador", "particle": "forja:item/colador"},
        "elements": [
            bar(2, 2, 14, 3, [0, 0, 16, 2]),
            bar(2, 13, 14, 14, [0, 0, 16, 2]),
            bar(2, 3, 3, 13, [0, 8, 16, 10]),
            bar(13, 3, 14, 13, [0, 8, 16, 10]),
            bar(3, 6, 13, 7, [2, 2, 14, 4]),
            bar(3, 10, 13, 11, [2, 2, 14, 4]),
            bar(6, 3, 7, 13, [2, 10, 14, 12]),
            bar(10, 3, 11, 13, [2, 10, 14, 12]),
        ],
        "display": {
            # Stood up rather than lying flat: a grate seen edge-on is a line, and in a slot it has
            # to read as a grate.
            "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 14, 0], "scale": [1.0, 1.0, 1.0]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
        },
    })
    # Clay until it is infused, and then whatever metal went through it.
    write_json(ASSETS / "items/colador.json", {
        "model": {
            "type": "minecraft:model",
            "model": "forja:item/colador",
            "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 0xB6825C}],
        },
    })

    # Glass and bronze: the same bronze the clay crucible is bound with, so the two go together.
    write_json(DATA / "recipe/colador.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" C ", "CBC", " C "],
        "key": {"C": "minecraft:clay_ball", "B": "minecraft:brick"},
        "result": {"id": "forja:colador", "count": 1},
    })
    write_json(DATA / "recipe/cuba_de_colada.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["BGB", "G G", "BGB"],
        "key": {"B": "forja:bronce", "G": "minecraft:glass"},
        "result": {"id": "forja:cuba_de_colada", "count": 2},
    })


# ---------------------------------------------------------------------------- the crucibles

# Each tier is the same pot in a different skin: (wall, rim, mouth) plus the colour of what is in it
# when it is running. The mouth is what reads across a room full of them.
# Each pot in its own material, plus the metal it is bound with and the colour of what is in it when
# it is running. Three pots of the same shape in three materials is the whole point: you can tell
# which one a smith owns from across the workshop.
CRUCIBLES = {
    # (wall, metal light, metal mid, metal dark, cold interior, melt)
    "crisol_de_barro": ((150, 94, 62), (214, 150, 92), (168, 110, 62), (104, 66, 34),
                        (56, 40, 32), (255, 150, 56)),
    "crisol_de_hierro": ((124, 124, 130), (196, 198, 208), (140, 142, 150), (72, 74, 80),
                         (40, 40, 44), (255, 178, 70)),
    "crisol_de_obsidiana": ((44, 38, 56), (238, 206, 118), (196, 158, 70), (122, 94, 36),
                            (22, 18, 28), (214, 132, 255)),
}


def generate_crucible_assets():
    """Three pots, lit and unlit, with the melt showing in the mouth of the lit ones."""
    rng = __import__("random").Random(771118)
    folder = ASSETS / "textures/block"
    folder.mkdir(parents=True, exist_ok=True)
    for name, (wall, light, mid, dark, cold, melt) in CRUCIBLES.items():
        clay = name.endswith("barro")
        glassy = name.endswith("obsidiana")
        for lit in (False, True):
            side = forge_grain(Image.new("RGBA", (16, 16)), wall, rng, 10)
            # The wall's own construction, which is the only thing telling the three apart up close.
            if clay:
                # Rope, wound twice round the belly, and the crack every clay pot eventually gets.
                for row in (5, 8):
                    for x in range(16):
                        side.putpixel((x, row), ((188, 158, 104) if x % 2 else (146, 118, 74)) + (255,))
                for step, y in enumerate(range(3, 10)):
                    side.putpixel((11 + (step % 2), y), (84, 52, 30, 255))
            elif glassy:
                # Facets, and two straps of gold holding a thing that would otherwise shatter.
                for y in range(3, 11):
                    for x in range(16):
                        if (x + y) % 5 == 0:
                            side.putpixel((x, y), (86, 72, 116, 255))
                        elif (x - y) % 7 == 0:
                            side.putpixel((x, y), (26, 22, 34, 255))
                for row in (4, 9):
                    for x in range(16):
                        side.putpixel((x, row), (mid if x % 3 else light) + (255,))
            else:
                # Plate, seamed down the middle and riveted, the way a real crucible shell is made.
                for y in range(3, 11):
                    side.putpixel((7, y), dark + (255,))
                    side.putpixel((8, y), light + (255,))
                forge_rivets(side, 4, light, dark, step=5, start=1)
                forge_rivets(side, 9, light, dark, step=5, start=3)
            forge_lip(side, light, mid, dark)
            # The belly band, where the pot is gripped.
            for x in range(16):
                side.putpixel((x, 10), mid + (255,))
                side.putpixel((x, 11), dark + (255,))
            forge_mouth(side, lit, rng, heat=melt if glassy else (255, 150, 50), top=12,
                        bars=(5, 8, 11))
            top = forge_bowl(Image.new("RGBA", (16, 16)), mid, wall, cold, rng,
                             melt=melt if lit else None)
            suffix = "_lit" if lit else ""
            side.save(folder / f"{name}_side{suffix}.png")
            top.save(folder / f"{name}_top{suffix}.png")
            write_json(ASSETS / f"models/block/{name}{suffix}.json", {
                "parent": "minecraft:block/cube_bottom_top",
                "textures": {
                    "top": f"forja:block/{name}_top{suffix}",
                    "bottom": f"forja:block/{name}_side",
                    "side": f"forja:block/{name}_side{suffix}",
                    "particle": f"forja:block/{name}_side{suffix}",
                },
            })
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {
            "lit=false": {"model": f"forja:block/{name}"},
            "lit=true": {"model": f"forja:block/{name}_lit"},
        }})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"forja:block/{name}"}})
        write_json(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"forja:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"forja:blocks/{name}",
        })
    # Each tier is the one below it, re-bound in an alloy that stands more heat. Nothing vanilla goes
    # into a crucible: you have to have made the metal at the table before you can mass-produce it here.
    write_json(DATA / "recipe/crisol_de_barro.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["P P", "P P", "PPP"],
        "key": {"P": "forja:peltre"},
        "result": {"id": "forja:crisol_de_barro", "count": 1},
    })
    write_json(DATA / "recipe/crisol_de_hierro.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["B B", "BCB", "BBB"],
        "key": {"B": "forja:bronce", "C": "forja:crisol_de_barro"},
        "result": {"id": "forja:crisol_de_hierro", "count": 1},
    })
    write_json(DATA / "recipe/crisol_de_obsidiana.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["OAO", "OCO", "OOO"],
        "key": {"O": "forja:obsidiacero", "A": "forja:corazon_de_forja", "C": "forja:crisol_de_hierro"},
        "result": {"id": "forja:crisol_de_obsidiana", "count": 1},
    })


# ---------------------------------------------------------------------------- the mountain workshop
def write_mountain_workshop():
    """A saddler's workshop up in the hills: a stone shed, a pen, the saddlery table and a horse."""
    import random

    rng = random.Random(330219)
    size = (13, 8, 13)
    blocks = {}

    def put(x, y, z, block, properties=None, nbt=None):
        blocks[(x, y, z)] = (block, properties or {}, nbt)

    floor = ["minecraft:cobblestone", "minecraft:stone", "minecraft:andesite", "minecraft:cobblestone", "minecraft:gravel"]
    walls = ["minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone_bricks"]
    for x in range(13):
        for z in range(13):
            put(x, 0, z, rng.choice(floor))
            for y in range(1, 8):
                put(x, y, z, "minecraft:air")
    # The shed: the left half of the plot, walls and a spruce roof.
    for x in range(0, 8):
        for z in range(0, 8):
            edge = x in (0, 7) or z in (0, 7)
            if edge:
                for y in range(1, 4):
                    put(x, y, z, rng.choice(walls))
            put(x, 4, z, "minecraft:spruce_planks")
    for x in range(0, 8):
        put(x, 5, 0, "minecraft:spruce_stairs", {"facing": "north", "half": "bottom", "shape": "straight"})
        put(x, 5, 7, "minecraft:spruce_stairs", {"facing": "south", "half": "bottom", "shape": "straight"})
    # A door and a window so it reads as a building.
    put(3, 1, 7, "minecraft:air")
    put(3, 2, 7, "minecraft:air")
    put(5, 2, 7, "minecraft:glass_pane", {"north": "false", "south": "false", "east": "true", "west": "true"})
    put(0, 2, 3, "minecraft:glass_pane", {"north": "true", "south": "true", "east": "false", "west": "false"})
    # The work: the saddlery, the parts table and a chest of leather and plate.
    put(2, 1, 2, "forja:mesa_de_talabarteria")
    put(4, 1, 2, "forja:mesa_de_piezas")
    put(5, 1, 5, "minecraft:chest", {"facing": "west", "type": "single"}, {"LootTable": "forja:chests/taller_de_montana"})
    put(2, 1, 5, "minecraft:crafting_table")
    put(6, 1, 1, "minecraft:lantern", {"hanging": "false"})
    put(1, 3, 1, "minecraft:lantern", {"hanging": "true"})
    # The pen: fences on the right half, with hay and a trough.
    for x in range(8, 13):
        put(x, 1, 0, "minecraft:oak_fence")
        put(x, 1, 12, "minecraft:oak_fence")
    for z in range(0, 13):
        put(12, 1, z, "minecraft:oak_fence")
    put(8, 1, 6, "minecraft:oak_fence_gate", {"facing": "west", "open": "false", "in_wall": "false", "powered": "false"})
    for z in (2, 3):
        put(10, 1, z, "minecraft:hay_block", {"axis": "y"})
    put(11, 1, 9, "minecraft:cauldron")
    # A saddle on the wall and the shears of the trade, as decoration that is also loot.
    put(1, 2, 1, "minecraft:barrel", {"facing": "up", "open": "false"},
        {"LootTable": "forja:chests/taller_de_montana"})

    stabled = {
        "pos": [NbtDouble(10.5), NbtDouble(1.0), NbtDouble(6.5)],
        "blockPos": [10, 1, 6],
        "nbt": {
            "id": "minecraft:horse",
            "PersistenceRequired": NbtByte(1),
            "Tame": NbtByte(1),
            "Variant": 2,
        },
    }
    write_structure_nbt("taller_de_montana/taller", size, blocks, [stabled])


def generate_mountain_workshop():
    write_mountain_workshop()
    write_json(DATA / "worldgen/template_pool/taller_de_montana/inicio.json", {
        "elements": [{
            "element": {"element_type": "minecraft:single_pool_element", "location": "forja:taller_de_montana/taller",
                        "processors": "minecraft:mossify_10_percent", "projection": "rigid"},
            "weight": 1,
        }],
        "fallback": "minecraft:empty",
    })
    write_json(DATA / "worldgen/structure/taller_de_montana.json", {
        "type": "minecraft:jigsaw",
        "biomes": "#forja:has_structure/taller_de_montana",
        "max_distance_from_center": 80,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"absolute": 0},
        "start_pool": "forja:taller_de_montana/inicio",
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "use_expansion_hack": False,
    })
    write_json(DATA / "worldgen/structure_set/taller_de_montana.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 228401755, "separation": 14, "spacing": 44},
        "structures": [{"structure": "forja:taller_de_montana", "weight": 1}],
    })
    # The chest of a saddler: leather, plate parts and now and then a talisman.
    write_json(DATA / "loot_table/chests/taller_de_montana.json", {
        "type": "minecraft:chest",
        "random_sequence": "forja:chests/taller_de_montana",
        "pools": [
            {
                "rolls": {"type": "minecraft:uniform", "min": 3, "max": 5},
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:leather", "weight": 12,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
                    {"type": "minecraft:item", "name": "minecraft:iron_ingot", "weight": 8,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]},
                    {"type": "minecraft:item", "name": "minecraft:saddle", "weight": 4},
                    {"type": "minecraft:item", "name": "forja:plantilla", "weight": 6,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}]},
                    {"type": "minecraft:item", "name": "forja:lingote_de_temple", "weight": 4},
                    {"type": "minecraft:item", "name": "minecraft:hay_block", "weight": 6,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                    {"type": "minecraft:item", "name": "minecraft:emerald", "weight": 5,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 5}}]},
                ],
            },
            {
                "rolls": 1,
                "entries": [
                    {"type": "minecraft:item", "name": "forja:placa_barda", "weight": 6, "functions": [{
                        "function": "minecraft:set_components",
                        "components": {
                            "forja:material": "hierro",
                            "minecraft:custom_model_data": {"colors": [0xE4E4E4]},
                            "minecraft:item_name": {"translate": "part.forja.placa_barda.de", "with": [{"translate": "material.forja.hierro"}]},
                        },
                    }]},
                    {"type": "minecraft:item", "name": "forja:forro", "weight": 6, "functions": [{
                        "function": "minecraft:set_components",
                        "components": {
                            "forja:material": "cuero",
                            "minecraft:custom_model_data": {"colors": [0xA86B3C]},
                            "minecraft:item_name": {"translate": "part.forja.forro.de", "with": [{"translate": "material.forja.cuero"}]},
                        },
                    }]},
                    {"type": "minecraft:empty", "weight": 6},
                ],
            },
        ],
    })



# ---------------------------------------------------------------------------- tooltip frames
TOOLTIP_STYLES = {
    # id: (frame light, frame dark, background tint)
    "leyenda": ((255, 216, 94), (150, 110, 24), (28, 20, 8)),
    "maestria": ((255, 170, 90), (146, 72, 24), (26, 16, 12)),
    "corazon": ((255, 130, 70), (140, 44, 22), (30, 12, 10)),
    # A masterpiece wears white gold: brighter than a legend, and paler.
    "obra_maestra": ((255, 245, 200), (198, 160, 70), (34, 30, 16)),
}


def generate_tooltip_styles():
    """Tooltip frames of our own: a legend, a mastered piece and the smith's heart each get a border.

    Vanilla resolves forja:<id> into tooltip/<id>_background and tooltip/<id>_frame, both nine-sliced
    from a 100x100 sprite with a ten pixel border, so that is exactly what is drawn here."""
    folder = ASSETS / "textures/gui/sprites/tooltip"
    folder.mkdir(parents=True, exist_ok=True)
    for name, (light, dark, tint) in TOOLTIP_STYLES.items():
        frame = Image.new("RGBA", (100, 100), (0, 0, 0, 0))
        background = Image.new("RGBA", (100, 100), tint + (245,))
        for y in range(100):
            for x in range(100):
                edge = min(x, y, 99 - x, 99 - y)
                if edge >= 10:
                    continue
                corner = min(x, 99 - x) < 10 and min(y, 99 - y) < 10
                if edge in (2, 3):
                    frame.putpixel((x, y), (light if corner else dark) + (255,))
                elif edge in (5, 6):
                    frame.putpixel((x, y), (dark if corner else light) + (255,))
                elif corner and edge == 8:
                    # A small stud in each corner, which is what makes it read as a frame and not a box.
                    frame.putpixel((x, y), light + (255,))
                # The background gets a lighter inner lip so the text does not sit on the border.
                if edge == 9:
                    background.putpixel((x, y), tuple(min(255, c + 26) for c in tint) + (245,))
        frame.save(folder / f"{name}_frame.png")
        background.save(folder / f"{name}_background.png")
        write_json(folder / f"{name}_frame.png.mcmeta", {
            "gui": {"scaling": {"type": "nine_slice", "width": 100, "height": 100, "border": 10, "stretch_inner": True}}
        })
        write_json(folder / f"{name}_background.png.mcmeta", {
            "gui": {"scaling": {"type": "nine_slice", "width": 100, "height": 100, "border": 9}}
        })



# ---------------------------------------------------------------------------- the raiders' camp
# variant: (log, planks, fence, stairs, ladder, ground blocks, tent colours, seed)
CAMP_VARIANTS = {
    "campamento": ("minecraft:spruce_log", "minecraft:spruce_planks", "minecraft:spruce_fence",
                   "minecraft:spruce_stairs", "minecraft:ladder",
                   ["minecraft:coarse_dirt", "minecraft:dirt_path", "minecraft:gravel", "minecraft:coarse_dirt"],
                   ("minecraft:brown_wool", "minecraft:gray_wool"), 660118),
    "campamento_nevado": ("minecraft:dark_oak_log", "minecraft:dark_oak_planks", "minecraft:dark_oak_fence",
                          "minecraft:dark_oak_stairs", "minecraft:ladder",
                          ["minecraft:snow_block", "minecraft:packed_ice", "minecraft:snow_block", "minecraft:gravel"],
                          ("minecraft:white_wool", "minecraft:light_gray_wool"), 771229),
}


def write_raider_camp(name="campamento"):
    """Where the forge raiders live between raids: a palisade, two tents, a watchtower and the plunder."""
    import random

    log, planks, fence, stairs, ladder, ground_blocks, tents, seed = CAMP_VARIANTS[name]
    rng = random.Random(seed)
    size = (15, 9, 15)
    blocks = {}

    def put(x, y, z, block, properties=None, nbt=None):
        blocks[(x, y, z)] = (block, properties or {}, nbt)

    ground = ground_blocks
    for x in range(15):
        for z in range(15):
            put(x, 0, z, rng.choice(ground))
            for y in range(1, 9):
                put(x, y, z, "minecraft:air")
    # A palisade of spruce logs with a gap for the gate.
    for i in range(15):
        for x, z in ((i, 0), (i, 14), (0, i), (14, i)):
            if z == 14 and 6 <= x <= 8:
                continue
            height = 2 if (x + z) % 3 else 3
            for y in range(1, height + 1):
                put(x, y, z, log, {"axis": "y"})
    # Two tents: wool stretched over a frame.
    for tent_x, colour in ((2, tents[0]), (9, tents[1])):
        for x in range(tent_x, tent_x + 4):
            for z in range(3, 7):
                if z in (3, 6) or x in (tent_x, tent_x + 3):
                    put(x, 1, z, fence)
                put(x, 3, z, colour)
            put(x, 2, 3, colour)
            put(x, 2, 6, colour)
        put(tent_x + 1, 1, 4, "minecraft:white_bed", {"facing": "south", "part": "head", "occupied": "false"})
        put(tent_x + 1, 1, 5, "minecraft:white_bed", {"facing": "south", "part": "foot", "occupied": "false"})
    # The fire they sit around, and the pot on it.
    put(7, 1, 7, "minecraft:campfire", {"lit": "true", "facing": "north", "signal_fire": "true", "waterlogged": "false"})
    for x, z in ((6, 6), (8, 8), (6, 8), (8, 6)):
        put(x, 1, z, stairs, {"facing": "north", "half": "bottom", "shape": "straight"})
    # A watchtower with a bell, so the camp reads as a camp from a distance.
    for y in range(1, 7):
        for x, z in ((11, 11), (13, 11), (11, 13), (13, 13)):
            put(x, y, z, log, {"axis": "y"})
    for x in range(11, 14):
        for z in range(11, 14):
            put(x, 7, z, planks)
    # Hung from the planks above it, so it does not pop off when the template lands.
    put(12, 6, 12, "minecraft:bell", {"facing": "north", "attachment": "ceiling"})
    for y in range(1, 7):
        put(12, y, 10, ladder, {"facing": "south"})
    # The plunder: two chests and a barrel of it.
    put(3, 1, 10, "minecraft:chest", {"facing": "east", "type": "single"}, {"LootTable": "forja:chests/campamento_saqueadores"})
    put(5, 1, 12, "minecraft:barrel", {"facing": "up", "open": "false"}, {"LootTable": "forja:chests/campamento_saqueadores"})
    put(4, 1, 11, "minecraft:crafting_table")
    # A cage, because they are raiders.
    for x in range(1, 5):
        put(x, 1, 2, "minecraft:iron_bars")
    for z in range(1, 3):
        put(1, 1, z, "minecraft:iron_bars")

    # The band that lives here. The tags are read once when the chunk loads, and they are given their
    # forged gear then: equipment cannot be written into a structure by hand.
    raiders = []
    crew = (
        (7, 5, "vindicator", ["forja_campamento", "forja_campamento_capitan"]),
        (9, 8, "vindicator", ["forja_campamento"]),
        (5, 9, "pillager", ["forja_campamento"]),
        (12, 6, "pillager", ["forja_campamento"]),
    )
    for x, z, kind, tags in crew:
        raiders.append({
            "pos": [NbtDouble(x + 0.5), NbtDouble(1.0), NbtDouble(z + 0.5)],
            "blockPos": [x, 1, z],
            "nbt": {"id": f"minecraft:{kind}", "PersistenceRequired": NbtByte(1), "Tags": tags},
        })
    write_structure_nbt(f"campamento_saqueadores/{name}", size, blocks, raiders)


def generate_raider_camp():
    for variant in CAMP_VARIANTS:
        write_raider_camp(variant)
    write_json(DATA / "worldgen/template_pool/campamento_saqueadores/inicio.json", {
        "elements": [
            {
                "element": {"element_type": "minecraft:single_pool_element", "location": f"forja:campamento_saqueadores/{variant}",
                            "processors": "minecraft:mossify_10_percent", "projection": "rigid"},
                "weight": 1,
            }
            for variant in CAMP_VARIANTS
        ],
        "fallback": "minecraft:empty",
    })
    write_json(DATA / "worldgen/structure/campamento_saqueadores.json", {
        "type": "minecraft:jigsaw",
        "biomes": "#forja:has_structure/campamento_saqueadores",
        "max_distance_from_center": 80,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"absolute": 0},
        "start_pool": "forja:campamento_saqueadores/inicio",
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "use_expansion_hack": False,
    })
    write_json(DATA / "worldgen/structure_set/campamento_saqueadores.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 990214773, "separation": 12, "spacing": 36,
                      "exclusion_zone": {"chunk_count": 6, "other_set": "minecraft:villages"}},
        "structures": [{"structure": "forja:campamento_saqueadores", "weight": 1}],
    })
    write_json(DATA / "tags/worldgen/biome/has_structure/campamento_saqueadores.json", {"values": [
        "#minecraft:is_forest", "#minecraft:is_taiga", "minecraft:plains", "minecraft:sunflower_plains",
        "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:dark_forest",
        "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:grove",
    ]})
    # What they have taken off other smiths.
    write_json(DATA / "loot_table/chests/campamento_saqueadores.json", {
        "type": "minecraft:chest",
        "random_sequence": "forja:chests/campamento_saqueadores",
        "pools": [
            {
                "rolls": {"type": "minecraft:uniform", "min": 2, "max": 5},
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:emerald", "weight": 10,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 6}}]},
                    {"type": "minecraft:item", "name": "minecraft:iron_ingot", "weight": 10,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 5}}]},
                    {"type": "minecraft:item", "name": "forja:plantilla", "weight": 6},
                    {"type": "minecraft:item", "name": "forja:lingote_de_temple", "weight": 5},
                    {"type": "minecraft:item", "name": "minecraft:gunpowder", "weight": 6,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
                    {"type": "minecraft:item", "name": "minecraft:crossbow", "weight": 3},
                    {"type": "minecraft:item", "name": "minecraft:arrow", "weight": 8,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 4, "max": 12}}]},
                ],
            },
        ],
    })



# ---------------------------------------------------------------- paintings

# id: (blocks wide, blocks tall)
PAINTINGS = {"yunque": (2, 2), "estrella": (2, 1), "martillo": (1, 1)}


def paint_background(image, top, bottom, grain=6):
    """A flat wall of colour with a little noise, dark at the bottom like a smoky workshop."""
    import random

    rng = random.Random(4211)
    px = image.load()
    for y in range(image.height):
        t = y / max(1, image.height - 1)
        base = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(image.width):
            n = rng.randint(-grain, grain)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base) + (255,)


def generate_paintings():
    """Three pictures for the wall, drawn the way the item sprites are: straight into pixels."""
    import math

    folder = ASSETS / "textures/painting"
    folder.mkdir(parents=True, exist_ok=True)

    # The anvil, 2x2: a black anvil against the glow of a forge, with sparks over it.
    anvil = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    paint_background(anvil, (58, 30, 18), (22, 14, 12))
    px = anvil.load()
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 16, y - 21)
            if d < 14:
                glow = max(0, 170 - int(d * 12))
                px[x, y] = (60 + glow, 24 + glow // 2, 14, 255)
    body = []
    for x in range(7, 25):
        body.append((x, 17))
        body.append((x, 18))
    for x in range(9, 23):
        body.append((x, 19))
    for x in range(13, 19):
        for y in range(20, 25):
            body.append((x, y))
    for x in range(8, 24):
        body.append((x, 25))
        body.append((x, 26))
    for x, y in body:
        px[x, y] = (78, 76, 86, 255)
    # A lit top face and a dark line under it, so the anvil reads against the glow.
    for x in range(7, 25):
        px[x, 16] = (150, 150, 160, 255)
        px[x, 17] = (110, 110, 120, 255)
    for x in range(8, 24):
        px[x, 27] = (30, 28, 34, 255)
    for y in range(17, 27):
        for x in (6, 25):
            if 0 <= x < 32:
                px[x, y] = (30, 28, 34, 255) if 17 <= y <= 19 or y >= 25 else px[x, y]
    for x, y in ((11, 9), (14, 7), (19, 8), (22, 11), (16, 5), (9, 12)):
        px[x, y] = (255, 214, 120, 255)
        px[min(31, x + 1), y] = (255, 160, 60, 255)
    anvil.save(folder / "yunque.png")

    # The star, 2x1: the five points of the forge table drawn as a constellation.
    star = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
    paint_background(star, (30, 28, 40), (16, 14, 22), grain=4)
    px = star.load()
    points = [(16 + 11 * math.cos(math.radians(-90 + i * 72)), 8 + 6 * math.sin(math.radians(-90 + i * 72))) for i in range(5)]
    for i in range(5):
        ax, ay = points[i]
        bx, by = points[(i + 2) % 5]
        steps = 40
        for step in range(steps + 1):
            x = round(ax + (bx - ax) * step / steps)
            y = round(ay + (by - ay) * step / steps)
            if 0 <= x < 32 and 0 <= y < 16:
                px[x, y] = (226, 160, 72, 255)
    for ax, ay in points:
        x, y = round(ax), round(ay)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
            if 0 <= x + dx < 32 and 0 <= y + dy < 16:
                px[x + dx, y + dy] = (255, 226, 150, 255)
    star.save(folder / "estrella.png")

    # The hammer, 1x1: the tool itself, small and plain.
    hammer = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paint_background(hammer, (48, 40, 32), (24, 20, 18), grain=5)
    px = hammer.load()
    for x in range(3, 13):
        px[x, 11] = (122, 82, 44, 255)
        px[x, 12] = (86, 56, 30, 255)
    for x in range(4, 12):
        for y in range(4, 9):
            px[x, y] = (150, 152, 160, 255) if y < 6 else (96, 98, 106, 255)
    for x in range(4, 12):
        px[x, 3] = (206, 208, 216, 255)
    for y in range(4, 9):
        px[3, y] = (48, 46, 52, 255)
        px[12, y] = (48, 46, 52, 255)
    hammer.save(folder / "martillo.png")


def generate_painting_data():
    """The variants themselves, and the tag that lets them turn up on a randomly placed painting."""
    for name, (width, height) in PAINTINGS.items():
        write_json(DATA / f"painting_variant/{name}.json", {
            "asset_id": f"forja:{name}",
            "width": width,
            "height": height,
            "title": {"color": "yellow", "translate": f"painting.forja.{name}.title"},
            "author": {"color": "gray", "translate": f"painting.forja.{name}.author"},
        })
    write_json(RES / "data/minecraft/tags/painting_variant/placeable.json",
               {"replace": False, "values": [f"forja:{name}" for name in PAINTINGS]})


# ---------------------------------------------------------------- the empty suit of armour

# Nothing holds it up, so nothing on it quite touches: the helm floats over the gap at the neck, the
# waist is open, and what light there is comes from inside.
HOLLOW_CUBES = {
    # ---- Legs, with a gap at every joint and the light showing through each one.
    "boot_right": ([-5.5, 0, -4], [5, 4, 8], "dark"),
    "glow_ankle_right": ([-4, 3, -2], [2, 3, 3], "soul"),
    "greave_right": ([-5, 5, -3], [4, 6, 6], "steel"),
    "knee_right": ([-5.5, 10, -3.5], [5, 3, 7], "dark"),
    "boot_left": ([0.5, 0, -4], [5, 4, 8], "dark"),
    "glow_ankle_left": ([2, 3, -2], [2, 3, 3], "soul"),
    "greave_left": ([1, 5, -3], [4, 6, 6], "steel"),
    "knee_left": ([0.5, 10, -3.5], [5, 3, 7], "dark"),
    # ---- The hips are their own piece, because the breastplate above them floats free of them.
    "faulds": ([-5, 13, -4], [10, 4, 8], "dark"),
    "tabard": ([-3.5, 6, -5.5], [7, 9, 1], "cloth"),
    # ---- And between hips and chest, nothing but a column of light.
    "spine_light": ([-1.5, 16, -1.5], [3, 4, 3], "soul"),
    "chest": ([-6, 19, -4], [12, 9, 8], "steel"),
    "chest_rune_stem": ([-1, 20, -5], [2, 5, 1], "soul"),
    "chest_rune_head": ([-4, 24, -5], [8, 2, 1], "soul"),
    "collar": ([-5, 27, -4], [10, 2, 8], "dark"),
    # ---- The neck is empty. What wears the suit sits in it, and you can see it.
    "core": ([-2, 28, -2], [4, 6, 4], "soul"),
    # ---- Pauldrons hovering a finger off the shoulder.
    "pauldron_right_top": ([-12, 25, -5.5], [6, 4, 11], "steel"),
    "pauldron_right_low": ([-13, 22, -4.5], [6, 3, 9], "dark"),
    "pauldron_left_top": ([6, 25, -5.5], [6, 4, 11], "steel"),
    "pauldron_left_low": ([7, 22, -4.5], [6, 3, 9], "dark"),
    # ---- Arms in two pieces with the gap lit between them.
    "bracer_right": ([-11, 16, -3], [4, 6, 6], "dark"),
    "glow_elbow_right": ([-10, 14, -1.5], [2, 3, 3], "soul"),
    "gauntlet_right": ([-11, 8, -3], [4, 6, 6], "steel"),
    "bracer_left": ([7, 16, -3], [4, 6, 6], "dark"),
    "glow_elbow_left": ([8, 14, -1.5], [2, 3, 3], "soul"),
    "gauntlet_left": ([7, 8, -3], [4, 6, 6], "steel"),
    # ---- The helm floats above the gap, tilted, lit from the inside.
    "helm": ([-5, 32, -5], [10, 6, 10], "steel"),
    "helm_top": ([-5, 38, -5], [10, 2, 10], "dark"),
    "crest": ([-1.5, 39, -3], [3, 4, 7], "steel"),
    "visor": ([-4, 34, -6], [8, 2, 1], "soul"),
    # ---- The sword is not held: it hangs in the air where the hand should be.
    "sword_blade": ([-14.5, -1, -3], [3, 15, 3], "iron"),
    "sword_guard": ([-16.5, 13, -4], [7, 2, 5], "steel"),
    "sword_grip": ([-14.5, 15, -3], [3, 4, 3], "dark"),
    "sword_pommel": ([-15, 19, -3.5], [4, 2, 4], "soul"),
}

HOLLOW_PALETTE = {
    "steel": ((106, 112, 124), (146, 152, 166), (58, 62, 72)),
    "dark": ((46, 50, 60), (66, 70, 82), (26, 28, 36)),
    "iron": ((146, 152, 164), (186, 192, 204), (88, 92, 102)),
    "cloth": ((96, 40, 48), (128, 58, 66), (52, 22, 28)),
    "soul": ((16, 54, 60), (24, 74, 82), (10, 34, 40), (156, 244, 250)),
}

HOLLOW_BONES = [
    # Nothing in this thing is attached to anything else. Every piece is its own bone so it can
    # drift on its own time, and the sword is not even held: it hangs beside the empty gauntlet.
    ("root", None, [0, 0, 0], []),
    ("leg_right", "root", [-3, 13, 0], ["boot_right", "glow_ankle_right", "greave_right", "knee_right"]),
    ("leg_left", "root", [3, 13, 0], ["boot_left", "glow_ankle_left", "greave_left", "knee_left"]),
    ("hips", "root", [0, 15, 0], ["faulds", "tabard"]),
    ("body", "root", [0, 19, 0], ["spine_light", "chest", "chest_rune_stem", "chest_rune_head", "collar", "core"]),
    ("head", "body", [0, 33, 0], ["helm", "helm_top", "crest", "visor"], [0, 0, -4]),
    ("arm_right", "body", [-9, 26, 0], ["pauldron_right_top", "pauldron_right_low", "bracer_right",
                                        "glow_elbow_right", "gauntlet_right"]),
    ("arm_left", "body", [9, 26, 0], ["pauldron_left_top", "pauldron_left_low", "bracer_left",
                                      "glow_elbow_left", "gauntlet_left"]),
    ("sword", "arm_right", [-13, 14, 0], ["sword_blade", "sword_guard", "sword_grip", "sword_pommel"]),
]


def generate_hollow_assets():
    """Coraza vacia: plate held up by whatever is inside it, and it does not hold it very tightly."""
    atlas = (128, 128)
    uvs = write_geo("coraza_vacia", HOLLOW_CUBES, HOLLOW_BONES, atlas, (3, 4, 1.4))
    skin, glow = paint_model(HOLLOW_CUBES, HOLLOW_PALETTE, uvs, atlas, 880413, wear=0.75, rust=(70, 78, 70))
    skin.save(ASSETS / "textures/entity/coraza_vacia.png")
    glow.save(ASSETS / "textures/entity/coraza_vacia_glowmask.png")

    write_json(ASSETS / "geckolib/animations/entity/coraza_vacia.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            # Possessed, not alive: the helm breathes on one clock, the chest on another, the sword on
            # a third, and none of them agree. The pieces pull apart and settle back.
            "idle": {
                "loop": True,
                "animation_length": 4.6,
                "bones": {
                    "head": bone(
                        pos([(0, [0, 0.5, 0]), (1.5, [0.4, 1.8, 0]), (3.0, [-0.4, 0.8, 0]), (4.6, [0, 0.5, 0])]),
                        rot([(0, [-4, -9, 3]), (1.5, [3, 7, -4]), (3.0, [-5, 10, 4]), (4.6, [-4, -9, 3])]),
                    ),
                    "body": bone(
                        pos([(0, [0, 0, 0]), (2.3, [0, 0.8, 0]), (4.6, [0, 0, 0])]),
                        rot([(0, [0, 2, 0]), (2.3, [0, -2, 0]), (4.6, [0, 2, 0])]),
                    ),
                    "hips": rot([(0, [0, -2, 0]), (1.8, [0, 3, 0]), (4.6, [0, -2, 0])]),
                    "arm_right": bone(
                        rot([(0, [0, 0, -6]), (1.9, [-5, 0, -11]), (4.6, [0, 0, -6])]),
                        pos([(0, [0, 0, 0]), (1.9, [-0.5, 0.6, 0]), (4.6, [0, 0, 0])]),
                    ),
                    "arm_left": bone(
                        rot([(0, [0, 0, 6]), (2.7, [5, 0, 11]), (4.6, [0, 0, 6])]),
                        pos([(0, [0, 0, 0]), (2.7, [0.5, 0.7, 0]), (4.6, [0, 0, 0])]),
                    ),
                    # Nothing is holding it. It turns where it hangs.
                    "sword": bone(
                        pos([(0, [0, 0, 0]), (1.1, [-0.6, 1.2, 0]), (2.6, [0.4, -0.6, 0]), (4.6, [0, 0, 0])]),
                        rot([(0, [0, 0, 0]), (1.1, [6, 14, -4]), (2.6, [-5, -12, 5]), (4.6, [0, 0, 0])]),
                    ),
                },
            },
            # Walking, the armour trails whatever is wearing it: the helm arrives a step late.
            "walk": {
                "loop": True,
                "animation_length": 1.4,
                "bones": {
                    "leg_right": rot([(0, [-24, 0, 0]), (0.7, [24, 0, 0]), (1.4, [-24, 0, 0])]),
                    "leg_left": rot([(0, [24, 0, 0]), (0.7, [-24, 0, 0]), (1.4, [24, 0, 0])]),
                    "arm_right": rot([(0, [16, 0, -6]), (0.7, [-16, 0, -6]), (1.4, [16, 0, -6])]),
                    "arm_left": rot([(0, [-16, 0, 6]), (0.7, [16, 0, 6]), (1.4, [-16, 0, 6])]),
                    "hips": bone(
                        pos([(0, [0, 0, 0]), (0.35, [0, 0.4, 0]), (1.05, [0, 0.4, 0]), (1.4, [0, 0, 0])]),
                        rot([(0, [0, 6, 0]), (0.7, [0, -6, 0]), (1.4, [0, 6, 0])]),
                    ),
                    "body": bone(
                        pos([(0, [0, 0.3, 0]), (0.5, [0, 1.0, 0]), (1.0, [0, 0.2, 0]), (1.4, [0, 0.3, 0])]),
                        rot([(0, [0, -5, 0]), (0.7, [0, 5, 0]), (1.4, [0, -5, 0])]),
                    ),
                    "head": bone(
                        pos([(0, [0, 1.0, 0]), (0.5, [0, 0.3, 0]), (1.0, [0, 1.4, 0]), (1.4, [0, 1.0, 0])]),
                        rot([(0, [0, -8, 0]), (0.7, [0, 8, 0]), (1.4, [0, -8, 0])]),
                    ),
                    "sword": pos([(0, [0, 0.4, 0]), (0.7, [0, -0.4, 0]), (1.4, [0, 0.4, 0])]),
                },
            },
            # The swing: the suit comes apart for a moment and what is inside shows through.
            "cut": {
                "loop": False,
                "animation_length": 0.9,
                "bones": {
                    "arm_right": rot([(0, [0, 0, -6]), (0.2, [-115, 18, -6]), (0.5, [35, -12, -6]), (0.9, [0, 0, -6])]),
                    "sword": bone(
                        rot([(0, [0, 0, 0]), (0.2, [-30, 0, 0]), (0.5, [24, 0, 0]), (0.9, [0, 0, 0])]),
                        pos([(0, [0, 0, 0]), (0.2, [0, -1.5, 0]), (0.9, [0, 0, 0])]),
                    ),
                    "body": bone(
                        rot([(0, [0, 2, 0]), (0.4, [0, -18, 0]), (0.9, [0, 2, 0])]),
                        pos([(0, [0, 0, 0]), (0.25, [0, 1.4, 0]), (0.9, [0, 0, 0])]),
                    ),
                    "hips": rot([(0, [0, 0, 0]), (0.4, [0, 10, 0]), (0.9, [0, 0, 0])]),
                    "head": bone(
                        pos([(0, [0, 0.5, 0]), (0.25, [0, 2.8, 0]), (0.9, [0, 0.5, 0])]),
                        rot([(0, [0, 0, 0]), (0.25, [-12, -24, 0]), (0.9, [0, 0, 0])]),
                    ),
                    "arm_left": pos([(0, [0, 0, 0]), (0.25, [1.4, 0.5, 0]), (0.9, [0, 0, 0])]),
                },
            },
            # The lunge: whatever is inside goes first and the plate is dragged after it, so every piece
            # trails a little and the helm arrives last of all.
            # It is played from the moment it decides, and it does not GO until 1.65 s —
            # HollowArmor.DASH_WINDUP, 33 ticks — so the first 1.65 s is the suit gathering itself:
            # sinking back, arms and sword drawn behind it, while the line it will travel is on the
            # floor. The lunge that used to be the whole animation starts there. (When the windup was
            # tripled this was left alone: it lunged on the spot at once, stood up, and then left.)
            "dash": {
                "loop": False,
                "animation_length": 2.45,
                "bones": {
                    "body": bone(
                        rot([(0, [0, 2, 0]), (0.5, [-12, 0, 0]), (1.6, [-17, 0, 0]),
                             (1.8, [26, 0, 0]), (2.15, [14, 0, 0]), (2.45, [0, 2, 0])]),
                        pos([(0, [0, 0, 0]), (0.5, [0, -0.8, 1.2]), (1.6, [0, -1.1, 1.7]),
                             (1.8, [0, 0.6, -1.6]), (2.45, [0, 0, 0])]),
                    ),
                    "hips": pos([(0, [0, 0, 0]), (1.65, [0, 0, 0]), (1.85, [0, 0, 1.4]), (2.45, [0, 0, 0])]),
                    "head": bone(
                        pos([(0, [0, 0.5, 0]), (0.5, [0, 0.2, 0.6]), (1.65, [0, 0.2, 0.9]),
                             (1.85, [0, 1.6, 2.4]), (2.2, [0, 0.8, -0.8]), (2.45, [0, 0.5, 0])]),
                        rot([(0, [0, 0, -4]), (0.5, [10, 0, -4]), (1.65, [13, 0, -4]), (1.85, [-22, 0, -4]), (2.45, [0, 0, -4])]),
                    ),
                    "arm_right": rot([(0, [0, 0, -6]), (0.5, [-32, 0, -10]), (1.65, [-40, 0, -10]), (1.85, [56, 0, -6]), (2.45, [0, 0, -6])]),
                    "arm_left": rot([(0, [0, 0, 6]), (0.5, [-32, 0, 10]), (1.65, [-40, 0, 10]), (1.85, [56, 0, 6]), (2.45, [0, 0, 6])]),
                    "sword": rot([(0, [0, 0, 0]), (0.5, [26, 0, 0]), (1.65, [34, 0, 0]), (1.85, [-40, 0, 0]), (2.45, [0, 0, 0])]),
                },
            },
            # The wail: the suit comes apart around the thing inside it, holds open, and shuts again.
            "wail": {
                "loop": False,
                "animation_length": 1.6,
                "bones": {
                    "head": bone(
                        pos([(0, [0, 0.5, 0]), (0.35, [0, 4.5, 0]), (1.1, [0, 4.0, 0]), (1.6, [0, 0.5, 0])]),
                        rot([(0, [0, 0, -4]), (0.35, [-36, 0, -4]), (1.6, [0, 0, -4])]),
                    ),
                    "body": bone(
                        pos([(0, [0, 0, 0]), (0.35, [0, 1.6, 0]), (1.6, [0, 0, 0])]),
                        scale([(0, [1, 1, 1]), (0.35, [1.06, 1.06, 1.06]), (1.6, [1, 1, 1])]),
                    ),
                    "hips": pos([(0, [0, 0, 0]), (0.35, [0, -1.2, 0]), (1.6, [0, 0, 0])]),
                    "arm_right": bone(
                        rot([(0, [0, 0, -6]), (0.35, [-24, 0, -34]), (1.1, [-20, 0, -30]), (1.6, [0, 0, -6])]),
                        pos([(0, [0, 0, 0]), (0.35, [-1.6, 0.8, 0]), (1.6, [0, 0, 0])]),
                    ),
                    "arm_left": bone(
                        rot([(0, [0, 0, 6]), (0.35, [-24, 0, 34]), (1.1, [-20, 0, 30]), (1.6, [0, 0, 6])]),
                        pos([(0, [0, 0, 0]), (0.35, [1.6, 0.8, 0]), (1.6, [0, 0, 0])]),
                    ),
                    "sword": pos([(0, [0, 0, 0]), (0.35, [-1.4, 1.6, 0]), (1.6, [0, 0, 0])]),
                },
            },
        },
    })


# mob id: (shell colour, spot colour)
SPAWN_EGGS = {
    "herrero_caido": ((62, 60, 66), (226, 110, 40)),
    "automata_de_forja": ((124, 122, 118), (206, 208, 216)),
    "coraza_vacia": ((78, 76, 86), (120, 220, 226)),
    "pavesa": ((58, 54, 56), (255, 168, 62)),
}


def generate_spawn_eggs():
    """One egg per mob of ours, cut from the shape and the shading of a real Minecraft spawn egg.

    The old ones were an ellipse drawn from an equation with a ring of flat outline round it and dots
    dropped on at random, and next to a vanilla egg in the same creative tab they read as stickers.
    The silhouette and the light of a spawn egg are hand-made things and they are already in the game,
    so this takes them: the zombie egg is stripped down to where its pixels are and how bright each one
    is relative to the rest, and both are repainted in the mob's two colours.
    """
    import random

    source = vanilla("item/zombie_spawn_egg.png")

    def solid(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and source.getpixel((x, y))[3] > 0

    points = [(x, y) for y in range(16) for x in range(16) if solid(x, y)]
    # The rim is where the egg stops, not where the artist happened to use a dark colour.
    rim = {p for p in points
           if any(not solid(p[0] + dx, p[1] + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    body = [p for p in points if p not in rim]
    levels = [luminance(source.getpixel(p)) for p in body]
    low = min(levels)
    span = max(1.0, max(levels) - low)

    folder = ASSETS / "textures/item"
    folder.mkdir(parents=True, exist_ok=True)
    for name, (shell, spots) in SPAWN_EGGS.items():
        rng = random.Random(sum(shell) * 31 + sum(spots))
        egg = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

        def paint(point, colour, floor, reach):
            step = (luminance(source.getpixel(point)) - low) / span
            egg.putpixel(point, tuple(min(255, int(c * (floor + reach * step))) for c in colour) + (255,))

        for point in body:
            paint(point, shell, 0.62, 0.78)
        for point in rim:
            egg.putpixel(point, tuple(int(c * 0.32) for c in shell) + (255,))
        # The markings: small clumps rather than lone pixels, shaded with the shell so they sit in the
        # egg instead of on top of it, and kept apart so the shell still shows between them.
        marked = set()
        for _ in range(260):
            if len(marked) >= 18:
                break
            point = body[rng.randrange(len(body))]
            if point in marked or (point[0] + 1, point[1]) in marked or (point[0], point[1] + 1) in marked:
                continue
            clump = [point]
            if rng.random() < 0.45:
                clump.append((point[0] + 1, point[1]))
            if rng.random() < 0.30:
                clump.append((point[0], point[1] + 1))
            for part in clump:
                if part in body:
                    paint(part, spots, 0.70, 0.62)
                    marked.add(part)
        egg.save(folder / f"huevo_{name}.png")


# ---------------------------------------------------------------- the smith's barrow
def write_barrow():
    """A smith buried with their work: a small vaulted room underground, two suits of plate standing
    guard over the coffin, and what was left in the chest at the foot of it."""
    import random

    rng = random.Random(451207)
    size = (13, 7, 13)
    blocks = {}

    def put(x, y, z, block, properties=None, nbt=None):
        blocks[(x, y, z)] = (block, properties or {}, nbt)

    wall = ["minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:deepslate_tiles",
            "minecraft:deepslate_bricks", "minecraft:polished_deepslate"]
    for x in range(13):
        for z in range(13):
            put(x, 0, z, rng.choice(wall))
            put(x, 6, z, rng.choice(wall))
            for y in range(1, 6):
                # Walls all around, air inside.
                edge = x == 0 or z == 0 or x == 12 or z == 12
                put(x, y, z, rng.choice(wall) if edge else "minecraft:air")
    # Pillars in the corners of the room, and a vaulted line down the middle.
    for x, z in ((2, 2), (10, 2), (2, 10), (10, 10)):
        for y in range(1, 6):
            put(x, y, z, "minecraft:polished_deepslate")
        put(x, 5, z, "minecraft:chiseled_deepslate")
    for x in range(3, 10):
        put(x, 5, 6, "minecraft:polished_deepslate_bricks" if x % 2 else "minecraft:chiseled_deepslate")

    # The coffin: a slab of stone with a smith's things laid on it.
    for x in range(5, 8):
        for z in range(4, 9):
            put(x, 1, z, "minecraft:polished_deepslate")
        put(x, 2, 4, "minecraft:deepslate_tile_slab", {"type": "bottom", "waterlogged": "false"})
        put(x, 2, 8, "minecraft:deepslate_tile_slab", {"type": "bottom", "waterlogged": "false"})
    put(6, 2, 6, "minecraft:smithing_table")
    put(5, 2, 5, "minecraft:skeleton_skull", {"rotation": "8", "powered": "false"})

    # What the smith was buried with.
    put(6, 1, 10, "minecraft:chest", {"facing": "south", "type": "single"}, {"LootTable": "forja:chests/tumulo_del_herrero"})
    put(8, 1, 10, "minecraft:barrel", {"facing": "up", "open": "false"}, {"LootTable": "forja:chests/tumulo_del_herrero"})
    # Their own anvil at the head of the coffin.
    put(6, 2, 3, "forja:yunque_del_herrero")
    for x, z in ((3, 3), (9, 3), (3, 9), (9, 9)):
        put(x, 1, z, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    # A way in, pointing north.
    for y in range(1, 3):
        put(6, y, 0, "minecraft:air")
        put(6, y, 12, "minecraft:air")

    guards = [
        {
            "pos": [NbtDouble(4.5), NbtDouble(1.0), NbtDouble(6.5)],
            "blockPos": [4, 1, 6],
            "nbt": {"id": "forja:coraza_vacia", "PersistenceRequired": NbtByte(1)},
        },
        {
            "pos": [NbtDouble(8.5), NbtDouble(1.0), NbtDouble(6.5)],
            "blockPos": [8, 1, 6],
            "nbt": {"id": "forja:coraza_vacia", "PersistenceRequired": NbtByte(1)},
        },
    ]
    write_structure_nbt("tumulo_del_herrero/tumulo", size, blocks, guards)


def generate_barrow():
    write_barrow()
    write_json(DATA / "worldgen/template_pool/tumulo_del_herrero/inicio.json", {
        "elements": [{
            "element": {"element_type": "minecraft:single_pool_element", "location": "forja:tumulo_del_herrero/tumulo",
                        "processors": "minecraft:mossify_10_percent", "projection": "rigid"},
            "weight": 1,
        }],
        "fallback": "minecraft:empty",
    })
    write_json(DATA / "worldgen/structure/tumulo_del_herrero.json", {
        "type": "minecraft:jigsaw",
        "biomes": "#minecraft:is_overworld",
        "max_distance_from_center": 80,
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -48}, "max_inclusive": {"absolute": 8}},
        "start_pool": "forja:tumulo_del_herrero/inicio",
        "step": "underground_structures",
        "terrain_adaptation": "encapsulate",
        "use_expansion_hack": False,
    })
    write_json(DATA / "worldgen/structure_set/tumulo_del_herrero.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 517733991, "separation": 16, "spacing": 44},
        "structures": [{"structure": "forja:tumulo_del_herrero", "weight": 1}],
    })
    # What a smith is buried with: their templates, their orbs and, now and then, their legend.
    write_json(DATA / "loot_table/chests/tumulo_del_herrero.json", {
        "type": "minecraft:chest",
        "random_sequence": "forja:chests/tumulo_del_herrero",
        "pools": [
            {
                "rolls": {"type": "minecraft:uniform", "min": 3, "max": 5},
                "entries": [
                    {"type": "minecraft:item", "name": "forja:plantilla", "weight": 8,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                    {"type": "minecraft:item", "name": "forja:lingote_de_temple", "weight": 6},
                    {"type": "minecraft:item", "name": "forja:sello", "weight": 3},
                    {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 8,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
                    {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 4,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                    {"type": "minecraft:item", "name": "minecraft:experience_bottle", "weight": 5,
                     "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 8}}]},
                ],
            },
        ],
    })

def generate_data():
    tag = lambda values: {"replace": False, "values": values}
    mc = RES / "data/minecraft/tags/item"
    write_json(mc / "pickaxes.json", tag(["forja:pico", "forja:martillo", "forja:picahacha"]))
    write_json(mc / "axes.json", tag(["forja:hacha", "forja:picahacha"]))
    write_json(mc / "shovels.json", tag(["forja:pala"]))
    write_json(mc / "hoes.json", tag(["forja:azada"]))
    write_json(mc / "swords.json", tag(["forja:espada", "forja:daga", "forja:espadon", "forja:guadana"]))
    write_json(mc / "spears.json", tag(["forja:lanza"]))
    # Forged arrows have to be ammunition, or no bow will take them.
    write_json(mc / "arrows.json", tag(["forja:flecha"]))
    write_json(mc / "head_armor.json", tag(["forja:casco"]))
    write_json(mc / "chest_armor.json", tag(["forja:pechera"]))
    write_json(mc / "leg_armor.json", tag(["forja:grebas"]))
    write_json(mc / "foot_armor.json", tag(["forja:botas"]))
    # The saddlery and the cabinet are still carpentry; the two forge benches are stone now, so an axe
    # would take all day and a pickaxe is what the block asks for.
    write_json(RES / "data/minecraft/tags/block/mineable/axe.json",
               tag(["forja:mesa_de_talabarteria", "forja:armario_de_piezas"]))
    write_json(RES / "data/minecraft/tags/block/mineable/pickaxe.json", tag(PICKAXE_BLOCKS))
    # Upgrades replaced the old enchantments.
    shutil.rmtree(DATA / "enchantment", ignore_errors=True)
    shutil.rmtree(DATA / "tags", ignore_errors=True)
    # After the wipe, or the wipe takes them with it: the item tags every own material points at.
    write_json(DATA / "tags/item/hierro_estelar.json", tag(["forja:hierro_estelar"]))
    write_json(DATA / "tags/item/corazon_de_forja.json", tag(["forja:corazon_de_forja"]))
    write_json(DATA / "tags/item/placa_hueca.json", tag(["forja:placa_hueca"]))
    write_json(DATA / "tags/item/escoria.json", tag(["forja:escoria"]))
    for name in ALLOY_COLORS:
        write_json(DATA / f"tags/item/{name}.json", tag([f"forja:{name}"]))
    shutil.rmtree(RES / "data/minecraft/tags/enchantment", ignore_errors=True)

    write_json(DATA / "recipe/mesa_de_forja_mayor.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["BGB", "DMD", "BBB"],
        "key": {"B": "minecraft:polished_blackstone", "G": "minecraft:gold_ingot",
                "D": "forja:damasco", "M": "forja:mesa_de_forja"},
        "result": {"id": "forja:mesa_de_forja_mayor", "count": 1},
    })
    write_json(DATA / "loot_table/blocks/mesa_de_forja_mayor.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                   "entries": [{"type": "minecraft:item", "name": "forja:mesa_de_forja_mayor"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "forja:blocks/mesa_de_forja_mayor",
    })
    for table, middle in (("mesa_de_forja", "minecraft:crafting_table"), ("mesa_de_piezas", "minecraft:grindstone")):
        write_json(DATA / f"loot_table/blocks/{table}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"forja:{table}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"forja:blocks/{table}",
        })
        write_json(DATA / f"recipe/{table}.json", {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "key": {"I": "minecraft:iron_ingot", "C": middle, "P": "#minecraft:planks"},
            "pattern": ["III", "PCP", "P P"],
            "result": {"id": f"forja:{table}"},
        })
    generate_advancements()
    write_json(DATA / "recipe/plantilla.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"S": "minecraft:stick", "P": "#minecraft:planks"},
        "pattern": ["SP", "PS"],
        "result": {"id": "forja:plantilla", "count": 2},
    })
    generate_seal_recipes()
    generate_talisman_recipes()
    generate_jar_recipe()
    write_json(DATA / "recipe/yunque_portatil.json", {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": ["III", "LBL", " S "],
        "key": {
            "I": "minecraft:iron_ingot",
            "L": "minecraft:leather",
            "B": "minecraft:iron_block",
            "S": "minecraft:stick",
        },
        "result": {"id": "forja:yunque_portatil"},
    })
    write_json(DATA / "recipe/guia_de_forja.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["minecraft:book", "minecraft:iron_ingot"],
        "result": {"id": "forja:guia_de_forja"},
    })


def java_enum_names(path, stop_at=None):
    """The enum constant names of a Java enum file, lowercased, in declaration order."""
    import re

    text = (ROOT / path).read_text(encoding="utf-8")
    if stop_at:
        text = text.split(stop_at)[0]
    return [name.lower() for name in re.findall(r"^	([A-Z][A-Z_0-9]*)\(", text, re.MULTILINE)]


def generate_greater_table_gui():
    """The greater table's panels: the ordinary ones, pulled toward deepslate and violet.

    Recoloured rather than redrawn on purpose. The two menus are the same menu and should look like
    the same menu — what has to change is only enough that you never have to wonder which table you
    walked up to, and a redraw would say "different screen" when the truth is "same screen, better
    table".
    """
    for name in ("mesa_de_forja", "mesa_de_forja_tecnicas"):
        source = ASSETS / f"textures/gui/{name}.png"
        image = Image.open(source).convert("RGBA")
        pixels = image.load()
        for x in range(image.width):
            for y in range(image.height):
                r, g, b, a = pixels[x, y]
                if a == 0:
                    continue
                # Cool it down and lift the blue: the wood browns go to slate, the highlights go violet.
                grey = (r * 299 + g * 587 + b * 114) // 1000
                # The first pass came out a pale lilac, which read as a different mod rather than as
                # the same bench in better stone. Darker, and less of it: deepslate with violet in it.
                pixels[x, y] = (
                    min(255, int(grey * 0.55) + 18),
                    min(255, int(grey * 0.50) + 14),
                    min(255, int(grey * 0.68) + 34),
                    a,
                )
        target = name.replace("mesa_de_forja", "mesa_de_forja_mayor")
        image.save(ASSETS / f"textures/gui/{target}.png")


def check_orphan_items():
    """Item definitions nothing stands behind.

    Nothing here ever clears `items/`, so a definition written for an item that was later renamed or
    dropped stays for ever, and the game warns about its missing model on every start. `guante.json`
    sat there for a day after the part became `manopla`. The language file is the list of what exists,
    so anything in `items/` with no name in it is a leftover.
    """
    lang_path = ASSETS / "lang/es_mx.json"
    if not lang_path.exists():
        return True
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    named = {key.split(".", 2)[2].split(".")[0] for key in lang if key.startswith(("item.forja.", "block.forja."))}
    orphans = sorted(path.stem for path in (ASSETS / "items").glob("*.json") if path.stem not in named)
    if orphans:
        print("ORPHAN ITEM DEFINITIONS (no such item):", *orphans, sep=chr(10) + "  ")
    return not orphans


def check_material_tags():
    """Every material that points at an item tag of ours needs that tag written, or the game crashes."""
    import re

    text = (ROOT / "src/main/java/dev/forja/material/ForgeMaterial.java").read_text(encoding="utf-8")
    wanted = set(re.findall(r'Forja\.id\("([a-z_]+)"\)', text))
    wanted |= set(re.findall(r'alloyTag\("([a-z_]+)"\)', text))
    missing = [name for name in sorted(wanted) if not (DATA / f"tags/item/{name}.json").exists()]
    if missing:
        print("MISSING MATERIAL TAGS:")
        for name in missing:
            print(f"  data/forja/tags/item/{name}.json")
    return not missing


def check_structure_tags():
    """Every biome tag a structure points at, and every destination tag a map trade points at, has to be
    written. A missing one only shows up as a crash on world load, so it is checked here instead."""
    import re

    missing = []
    for path in sorted((DATA / "worldgen/structure").glob("*.json")):
        biomes = json.loads(path.read_text(encoding="utf-8")).get("biomes", "")
        if isinstance(biomes, str) and biomes.startswith("#forja:"):
            tag = biomes[len("#forja:"):]
            if not (DATA / f"tags/worldgen/biome/{tag}.json").exists():
                missing.append(f"data/forja/tags/worldgen/biome/{tag}.json (wanted by {path.name})")
    for path in sorted((DATA / "villager_trade").rglob("*.json")):
        text = path.read_text(encoding="utf-8")
        for tag in re.findall(r'"destination": "forja:([a-z_/]+)"', text):
            if not (DATA / f"tags/worldgen/structure/{tag}.json").exists():
                missing.append(f"data/forja/tags/worldgen/structure/{tag}.json (wanted by {path.name})")
    if missing:
        print("MISSING STRUCTURE TAGS:")
        for name in missing:
            print(f"  {name}")
    return not missing



def generate_casting_tables():
    """The three casting tables: one dark stone each, all of them the same block underneath.

    They are drawn from vanilla dark stone the way the forge table is drawn from dark oak. Each has a
    sunken bed on top, because that is where the frame sits and where the melt lands, and the renderer
    draws both of those on top of this.
    """
    folder = ASSETS / "textures/block"
    folder.mkdir(parents=True, exist_ok=True)
    import random
    rng = random.Random(4181)

    def shade(color, factor):
        return tuple(min(255, int(c * factor)) for c in color[:3]) + (255,)

    def metal_band(image, light, mid, dark):
        """A band across the top of a side face, the way every table in the mod wears one."""
        for x in range(16):
            image.putpixel((x, 0), light)
            image.putpixel((x, 1), mid)
            image.putpixel((x, 2), dark)
        for y in range(3, 16):
            for x in (0, 15):
                image.putpixel((x, y), dark)
        return image

    def legs(image, mid, dark):
        for y in range(3, 16):
            for x in (0, 1, 14, 15):
                image.putpixel((x, y), dark if x in (0, 15) else mid)
        return image

    def rivets(image, xs, y, light, dark):
        for x in xs:
            image.putpixel((x, y), light)
            image.putpixel((x, y + 1), dark)

    # ---- A: losa. Polished deepslate and iron: the sober one, the sibling of the forge table.
    def losa():
        stone = vanilla("block/polished_deepslate.png")
        rough = vanilla("block/deepslate.png")
        iron = (116, 118, 128, 255)
        iron_dark = (58, 59, 66, 255)
        iron_light = (172, 174, 184, 255)

        top = stone.copy()
        for y in range(4, 12):
            for x in range(4, 12):
                top.putpixel((x, y), shade(rough.getpixel((x, y)), 0.52))
        for i in range(4, 12):
            top.putpixel((i, 4), shade(rough.getpixel((i, 4)), 0.34))
            top.putpixel((4, i), shade(rough.getpixel((4, i)), 0.34))
            top.putpixel((i, 11), shade(rough.getpixel((i, 11)), 0.92))
            top.putpixel((11, i), shade(rough.getpixel((11, i)), 0.92))
        # The chisel line a frame is squared against.
        for i in range(5, 11):
            top.putpixel((i, 8), iron_dark)
        top.putpixel((5, 7), iron)
        top.putpixel((10, 9), iron)
        for a, b in ((0, 0), (13, 0), (0, 13), (13, 13)):
            for dx in range(3):
                for dy in range(3):
                    if dx and dy:
                        continue
                    top.putpixel((a + dx, b + dy), iron_light if dx + dy == 0 else iron)
        for i in range(16):
            for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
                if top.getpixel((x, y))[:3] != iron_light[:3]:
                    top.putpixel((x, y), iron_dark)

        side = legs(metal_band(stone.copy(), iron_light, iron, iron_dark), iron, iron_dark)
        for y in range(6, 12):
            side.putpixel((5, y), iron_light if y < 10 else iron)
        side.putpixel((5, 12), (226, 228, 236, 255))
        side.putpixel((4, 6), iron)
        for x in range(8, 13):
            side.putpixel((x, 7), iron_light)
            side.putpixel((x, 8), iron)
        side.putpixel((8, 9), iron_dark)
        side.putpixel((12, 9), iron_dark)
        rivets(side, (3, 12), 4, iron_light, iron_dark)

        front = legs(metal_band(stone.copy(), iron_light, iron, iron_dark), iron, iron_dark)
        for y in range(6, 13):
            for x in range(3, 13):
                front.putpixel((x, y), shade(rough.getpixel((x, y)), 0.62))
        for x in range(3, 13):
            front.putpixel((x, 6), shade(rough.getpixel((x, 6)), 0.38))
            front.putpixel((x, 12), shade(rough.getpixel((x, 12)), 1.05))
        for y in range(6, 13):
            front.putpixel((3, y), shade(rough.getpixel((3, y)), 0.38))
            front.putpixel((12, y), shade(rough.getpixel((12, y)), 1.05))
        for x in range(6, 10):
            front.putpixel((x, 9), iron_light)
            front.putpixel((x, 10), iron_dark)
        rivets(front, (2, 13), 4, iron_light, iron_dark)
        return top, front, side, rough

    # ---- B: brasa. Blackstone and gold off the fallen smith's own floor, with a fire still banked in it.
    def brasa():
        stone = vanilla("block/polished_blackstone.png")
        rough = vanilla("block/blackstone.png")
        gold = (214, 175, 84, 255)
        gold_dark = (138, 106, 42, 255)
        gold_light = (246, 216, 140, 255)

        def ember(heat):
            return (255, int(90 + 130 * heat), int(20 + 60 * heat), 255)

        top = stone.copy()
        for y in range(2, 14):
            for x in range(2, 14):
                grey = 46 + rng.randint(-8, 10)
                top.putpixel((x, y), (grey, grey - 4, grey - 6, 255))
        for y in range(5, 11):
            for x in range(5, 11):
                edge = max(abs(x - 7), abs(y - 7))
                if edge <= 2:
                    top.putpixel((x, y), ember(1.0 - edge * 0.35))
        for i in range(2, 14):
            top.putpixel((i, 1), gold)
            top.putpixel((i, 14), gold_dark)
            top.putpixel((1, i), gold)
            top.putpixel((14, i), gold_dark)
        for i in range(16):
            for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
                top.putpixel((x, y), (24, 20, 22, 255))

        side = metal_band(stone.copy(), gold_light, gold, gold_dark)
        crack = [(5, 4), (5, 5), (6, 6), (6, 7), (5, 8), (6, 9), (6, 10), (7, 11), (7, 12)]
        for x, y in crack:
            side.putpixel((x, y), ember(0.85))
            side.putpixel((x + 1, y), ember(0.35))
        for x, y in crack[::2]:
            side.putpixel((x - 1, y), shade(rough.getpixel((x - 1, y)), 0.6))
        rivets(side, (2, 13), 4, gold_light, gold_dark)

        front = metal_band(stone.copy(), gold_light, gold, gold_dark)
        for x in range(4, 12):
            front.putpixel((x, 6), gold)
            front.putpixel((x, 7), gold_dark)
        for y in range(8, 14):
            for x in range(5, 11):
                front.putpixel((x, y), (20, 16, 18, 255))
        for x in range(5, 11):
            heat = 0.35 + 0.5 * rng.random()
            front.putpixel((x, 12), ember(heat))
            front.putpixel((x, 13), ember(heat * 0.6))
        return top, front, side, rough

    # ---- C: almas. Basalt and obsidian with the light of the hollow suits still in the seams.
    def almas():
        basalt = vanilla("block/basalt_side.png")
        obsidian = vanilla("block/obsidian.png")
        soul = (78, 226, 226, 255)
        soul_dim = (34, 126, 138, 255)
        iron_dark = (48, 46, 58, 255)

        top = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                top.putpixel((x, y), shade(obsidian.getpixel((x, y)), 0.85))
        ring = []
        for i in range(4, 12):
            ring += [(i, 4), (i, 11), (4, i), (11, i)]
        for x, y in ring:
            top.putpixel((x, y), soul_dim)
        for x, y in ((4, 4), (11, 4), (4, 11), (11, 11), (7, 4), (8, 11), (4, 8), (11, 7)):
            top.putpixel((x, y), soul)
        for i in range(16):
            for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
                top.putpixel((x, y), iron_dark)

        def column():
            image = Image.new("RGBA", (16, 16))
            for y in range(16):
                for x in range(16):
                    image.putpixel((x, y), shade(basalt.getpixel((x, y)), 0.9))
            for x in (3, 7, 12):
                for y in range(4, 16):
                    image.putpixel((x, y), soul_dim if (y + x) % 3 else soul)
            for x in range(16):
                image.putpixel((x, 0), (92, 88, 104, 255))
                image.putpixel((x, 1), (62, 60, 74, 255))
                image.putpixel((x, 2), iron_dark)
            for y in range(3, 16):
                for x in (0, 15):
                    image.putpixel((x, y), iron_dark)
            return image

        side = column()
        front = column()
        rune = [(6, 6), (7, 6), (8, 6), (9, 6), (7, 7), (8, 8), (7, 9), (8, 9),
                (6, 11), (7, 11), (8, 11), (9, 11)]
        for x, y in rune:
            front.putpixel((x, y), soul)
            if y + 1 < 16:
                front.putpixel((x, y + 1), soul_dim)
        return top, front, side, obsidian

    for name, build in (("mesa_de_losa", losa), ("mesa_de_brasa", brasa), ("mesa_de_almas", almas)):
        top, front, side, under = build()
        bottom = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                bottom.putpixel((x, y), shade(under.getpixel((x, y)), 0.7))
        top.save(folder / f"{name}_top.png")
        front.save(folder / f"{name}_front.png")
        side.save(folder / f"{name}_side.png")
        bottom.save(folder / f"{name}_bottom.png")
        write_json(ASSETS / f"models/block/{name}.json", {
            "parent": "minecraft:block/cube",
            "textures": {
                "down": f"forja:block/{name}_bottom",
                "up": f"forja:block/{name}_top",
                "north": f"forja:block/{name}_front",
                "south": f"forja:block/{name}_front",
                "east": f"forja:block/{name}_side",
                "west": f"forja:block/{name}_side",
                "particle": f"forja:block/{name}_side",
            },
        })
        # The melt and the frame are drawn on top by the block entity renderer, so both states of the
        # block are the same model; lit only decides the light level and the particles.
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {
            "lit=false": {"model": f"forja:block/{name}"},
            "lit=true": {"model": f"forja:block/{name}"},
        }})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"forja:block/{name}"}})
        write_json(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"forja:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"forja:blocks/{name}",
        })

    # Each table is the stone it is named after around a casting box's worth of ironwork; the two above
    # the first are that one re-cut, so there is one ladder and not three separate crafts.
    write_json(DATA / "recipe/mesa_de_losa.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["DDD", "I I", "DDD"],
        "key": {"D": "minecraft:polished_deepslate", "I": "forja:acero_refractario"},
        "result": {"id": "forja:mesa_de_losa", "count": 1},
    })
    write_json(DATA / "recipe/mesa_de_brasa.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["BGB", "BMB", "BBB"],
        "key": {"B": "minecraft:polished_blackstone", "G": "minecraft:gold_ingot", "M": "forja:mesa_de_losa"},
        "result": {"id": "forja:mesa_de_brasa", "count": 1},
    })
    write_json(DATA / "recipe/mesa_de_almas.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["OPO", "BMB", "BBB"],
        "key": {"O": "minecraft:obsidian", "P": "forja:placa_hueca", "B": "minecraft:basalt", "M": "forja:mesa_de_brasa"},
        "result": {"id": "forja:mesa_de_almas", "count": 1},
    })



def check_enums():
    """This script's tables have to match the Java enums, or assets quietly go missing."""
    problems = []
    for label, java, names in (
        ("ForgeType", "src/main/java/dev/forja/forge/ForgeType.java", list(TYPES)),
        ("PartType", "src/main/java/dev/forja/part/PartType.java", list(PARTS)),
        ("ForgeMaterial", "src/main/java/dev/forja/material/ForgeMaterial.java", list(MATERIAL_COLORS)),
    ):
        declared = java_enum_names(java, stop_at="public enum Trait" if label == "ForgeMaterial" else None)
        # Armor pieces are declared with the ArmorType constructor and the enum also lists other things.
        if label == "ForgeType":
            declared += [name.lower() for name in ("CASCO", "PECHERA", "GREBAS", "BOTAS") if name.lower() not in declared]
        for name in declared:
            if name not in names:
                problems.append(f"{label}.{name} is missing from this script")
        for name in names:
            if name not in declared:
                problems.append(f"{label}.{name} is in this script but not in Java")
    if problems:
        print("ENUM MISMATCH:", *problems, sep=chr(10) + "  ")
    return problems


if __name__ == "__main__":
    # First, not in the middle: this used to live inside generate_models(), which runs near the end,
    # so every model written before it — the moulds, the frames, the strainer's grate — was deleted
    # minutes after being made and the items rendered as missing models in game.
    shutil.rmtree(ASSETS / "models/item", ignore_errors=True)
    generate_item_textures()
    generate_armor_textures()
    generate_wings_textures()
    generate_effect_textures()
    generate_shockwave_textures()
    generate_sky_textures()
    generate_book_gui()
    generate_boss_bar()
    generate_boss_assets()
    generate_automaton_assets()
    generate_rustbug_assets()
    generate_greater_ember_assets()
    generate_living_slag_assets()
    generate_walking_anvil_assets()
    generate_striker_assets()
    generate_tongs_assets()
    generate_hauler_assets()
    generate_quencher_assets()
    generate_star_core_assets()
    generate_broken_mould_assets()
    generate_hollow_assets()
    generate_wisp_assets()
    generate_forge_heart_texture()
    generate_tooltip_styles()
    generate_mount_assets()
    generate_fallen_forge()
    generate_mountain_workshop()
    generate_talisman_textures()
    generate_alloy_textures()
    generate_casting_tables()
    generate_star_iron_texture()
    generate_jar_textures()
    generate_belt_texture()
    generate_block_textures()
    generate_parts_table_textures()
    generate_saddlery_textures()
    generate_cabinet_textures()
    generate_cabinet_models()
    generate_guide_texture()
    generate_gui_textures()
    generate_greater_table_gui()
    generate_station_gui()
    generate_cabinet_gui()
    generate_advancement_background()
    generate_potential_assets()
    generate_extraction_table()
    generate_extraction_panel()
    generate_particle_sprites()
    generate_template_textures()
    generate_orb_textures()
    generate_crack_texture()
    generate_temper_ingot_texture()
    generate_portable_anvil_texture()
    generate_hollow_plate_texture()
    generate_master_hammer_texture()
    generate_seal_textures()
    generate_villager_textures()
    generate_icon()
    generate_spawn_eggs()
    generate_paintings()
    generate_models()
    generate_data()
    # These write into data/forja/tags, which generate_data clears.
    generate_structure()
    generate_forge_castle()
    generate_raider_camp()
    generate_barrow()
    # The great castle is a file of its own (tools/castillo.py); it borrows this one's NBT writer.
    import sys as _sys
    import castillo
    castle_problems = castillo.generate(_sys.modules[__name__])
    generate_painting_data()
    generate_trades()
    problems = check_enums()
    unmineable = check_tool_tags()
    tags_missing = not check_material_tags() or not check_structure_tags() or not check_orphan_items() or bool(unmineable) or bool(castle_problems)
    print("forja assets written", "| tables match the Java enums" if not problems and not tags_missing else "| SOMETHING IS OUT OF SYNC")
