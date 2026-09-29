"""Assets of the class system (docs/CLASES.md): the texture the two class screens, the toast and the HUD are
drawn from, the old Emblema del olvido and the forged Medallón del olvido. Called from generate_assets.py after
the models are written, the way castillo.py is, and handed that module so it can borrow its helpers.

Where every piece sits in textures/gui/clases.png is mirrored in client/ClassGui.java; move one, move both.
"""

import math
import random

from PIL import Image

TEX = 512
PANEL_W, PANEL_H = 380, 210
HEADER = 22
TREE_V, CHOICE_V = 0, 256
CARD_U, CARD_W, CARD_H = 390, 104, 22
BUTTON_U, BUTTON_V, BUTTON_W, BUTTON_H = 390, 74, 80, 20
HUD_U, HUD_V, HUD = 390, 140, 22
NODE_U, NODE_V, NODE = 0, 472, 26
# The tree's three branches and the skill row (client/TalentTreeScreen.COLUMNS, ROWS, SKILL_Y, SIDE_X).
COLUMNS = (48, 124, 200)
SKILL_Y = 168
SIDE_X = 252

OUTLINE = (32, 21, 12, 255)
WOOD_LIGHT, WOOD, WOOD_DARK = (150, 101, 58), (116, 76, 42), (78, 50, 26)
IRON, IRON_LIGHT, IRON_DARK = (128, 132, 142, 255), (200, 204, 214, 255), (66, 68, 76, 255)
BRONZE_LIGHT, BRONZE_DARK = (214, 158, 84, 255), (150, 98, 42, 255)
GOLD_LIGHT, GOLD_DARK = (255, 222, 118, 255), (196, 146, 40, 255)


def stone(px, x0, y0, w, h, base, rng, spread=5):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            n = rng.randint(-spread, spread)
            px[x, y] = (base[0] + n, base[1] + n, base[2] + n, 255)


def inset(px, x0, y0, x1, y1, fill, rng):
    """A sunken field: shadow on the top and left edges, a lit lip on the bottom and right."""
    stone(px, x0, y0, x1 - x0, y1 - y0, fill, rng, 3)
    for x in range(x0, x1):
        px[x, y0] = (24, 20, 17, 255)
        px[x, y0 + 1] = (34, 29, 25, 255)
        px[x, y1 - 1] = (104, 90, 74, 255)
    for y in range(y0, y1):
        px[x0, y] = (24, 20, 17, 255)
        px[x0 + 1, y] = (34, 29, 25, 255)
        px[x1 - 1, y] = (104, 90, 74, 255)


def panel(img, oy, seed):
    """The mod's frame (a wooden border with iron corners, as ForgeScreen's panels) round a dark forge-stone
    field, with a wooden band along the top for the title."""
    rng = random.Random(seed)
    px = img.load()
    stone(px, 0, oy, PANEL_W, PANEL_H, (56, 48, 42), rng)
    for y in range(PANEL_H):
        for x in range(PANEL_W):
            edge = min(x, y, PANEL_W - 1 - x, PANEL_H - 1 - y)
            if edge == 0:
                px[x, oy + y] = OUTLINE
            elif edge <= 3:
                plank = WOOD_LIGHT if (x < 4 or y < 4) and edge == 1 else WOOD_DARK if (x > PANEL_W - 5 or y > PANEL_H - 5) and edge == 1 else WOOD
                grain = -8 if (x * 7 + y * 3) % 11 == 0 else 0
                px[x, oy + y] = tuple(max(0, c + grain) for c in plank) + (255,)
            elif edge == 4:
                px[x, oy + y] = (40, 34, 30, 255) if (x > PANEL_W - 6 or y > PANEL_H - 6) else (122, 104, 84, 255)
    for y in range(4, HEADER):
        for x in range(4, PANEL_W - 4):
            grain = 10 if (y - 4) % 5 == 0 else -6 if (x + y * 13) % 17 == 0 else 0
            px[x, oy + y] = (106 + grain, 70 + grain, 40 + grain, 255)
    for x in range(4, PANEL_W - 4):
        px[x, oy + HEADER] = (58, 38, 20, 255)
        px[x, oy + HEADER + 1] = (30, 24, 20, 255)
    for cx, cy in ((0, 0), (PANEL_W - 9, 0), (0, PANEL_H - 9), (PANEL_W - 9, PANEL_H - 9)):
        for y in range(9):
            for x in range(9):
                border = x in (0, 8) or y in (0, 8)
                px[cx + x, oy + cy + y] = IRON_DARK if border else IRON_LIGHT if x + y < 6 else IRON
        px[cx + 4, oy + cy + 4] = IRON_DARK
        px[cx + 3, oy + cy + 3] = IRON_LIGHT
    return px, rng


def tree_panel(img):
    px, rng = panel(img, TREE_V, 11)
    # One sunken column per branch, a sunken plate under them for the skill, and the page on the right.
    for cx in COLUMNS:
        inset(px, cx - 34, TREE_V + 26, cx + 34, TREE_V + 152, (42, 36, 32), rng)
    inset(px, COLUMNS[1] - 30, TREE_V + SKILL_Y - 6, COLUMNS[1] + 30, TREE_V + SKILL_Y + 32, (42, 36, 32), rng)
    inset(px, SIDE_X - 6, TREE_V + 26, PANEL_W - 6, TREE_V + PANEL_H - 6, (46, 40, 35), rng)
    # Rivets down the side of the page, as on the forge's panels.
    for y in range(34, PANEL_H - 12, 24):
        for (dx, dy), colour in (((0, 0), IRON_LIGHT), ((1, 0), IRON), ((0, 1), IRON), ((1, 1), IRON_DARK)):
            px[SIDE_X - 10 + dx, TREE_V + y + dy] = colour


def choice_panel(img):
    px, rng = panel(img, CHOICE_V, 12)
    inset(px, 5, CHOICE_V + 25, 116, CHOICE_V + PANEL_H - 6, (42, 36, 32), rng)
    inset(px, 118, CHOICE_V + 24, PANEL_W - 6, CHOICE_V + PANEL_H - 6, (46, 40, 35), rng)


def plate(px, x0, y0, w, h, rim_light, rim_dark, body, rng):
    """A wooden plate with a metal rim lit from the top left: the cards and the buttons."""
    for y in range(h):
        for x in range(w):
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge == 0:
                colour = OUTLINE
            elif edge == 1:
                colour = rim_light if x + y < (w + h) // 2 and (x < 2 or y < 2) else rim_dark
            else:
                n = rng.randint(-4, 4)
                grain = 8 if (y - 2) % 4 == 0 else 0
                colour = (body[0] + n + grain, body[1] + n + grain, body[2] + n + grain, 255)
            px[x0 + x, y0 + y] = colour


def cards_and_buttons(img):
    px = img.load()
    rng = random.Random(13)
    for look, (light, dark, body) in enumerate((
        (BRONZE_DARK, (96, 62, 28, 255), (84, 56, 32)),
        (BRONZE_LIGHT, BRONZE_DARK, (104, 70, 40)),
        (GOLD_LIGHT, GOLD_DARK, (120, 84, 44)),
    )):
        plate(px, CARD_U, look * 24, CARD_W, CARD_H, light, dark, body, rng)
    for look, (light, dark, body) in enumerate((
        (BRONZE_LIGHT, BRONZE_DARK, (116, 76, 42)),
        (GOLD_LIGHT, GOLD_DARK, (140, 94, 50)),
        ((120, 116, 110, 255), (80, 76, 72, 255), (70, 66, 62)),
    )):
        plate(px, BUTTON_U, BUTTON_V + look * 22, BUTTON_W, BUTTON_H, light, dark, body, rng)
    # The HUD slots: a dark iron frame round a sunken square, and the same in gold when the skill is ready.
    for index, (light, dark) in enumerate(((IRON, IRON_DARK), (GOLD_LIGHT, GOLD_DARK))):
        x0 = HUD_U + index * 24
        for y in range(HUD):
            for x in range(HUD):
                edge = min(x, y, HUD - 1 - x, HUD - 1 - y)
                if edge == 0:
                    colour = OUTLINE
                elif edge <= 2:
                    colour = light if x + y < HUD else dark
                else:
                    colour = (34, 30, 28, 220)
                px[x0 + x, HUD_V + y] = colour


def nodes(img):
    """Six frames, 26 across: a talent's ring and a skill's octagon, each locked, open and learned."""
    px = img.load()
    looks = (
        ((82, 78, 74, 255), (46, 42, 40, 255), (30, 27, 25, 255)),
        (BRONZE_LIGHT, BRONZE_DARK, (66, 52, 40, 255)),
        (GOLD_LIGHT, GOLD_DARK, (112, 80, 42, 255)),
    )
    c = (NODE - 1) / 2.0
    for skill in (False, True):
        for index, (light, dark, inner) in enumerate(looks):
            x0 = NODE_U + (index + (3 if skill else 0)) * NODE
            for y in range(NODE):
                for x in range(NODE):
                    dx, dy = x - c, y - c
                    if skill:
                        # An octagon: a square with its corners cut off.
                        d = max(abs(dx), abs(dy), (abs(dx) + abs(dy)) / 1.35)
                        outer, rim = 12.6, 10.2
                    else:
                        d = math.hypot(dx, dy)
                        outer, rim = 12.8, 10.4
                    if d > outer:
                        continue
                    if d > outer - 0.9:
                        colour = OUTLINE
                    elif d > rim:
                        colour = light if dx + dy < -2 else dark if dx + dy > 2 else tuple((a + b) // 2 for a, b in zip(light, dark))
                    elif d > rim - 1.0:
                        colour = (20, 16, 14, 255)
                    else:
                        colour = inner
                    px[x0 + x, NODE_V + y] = colour
            if index == 2:
                # Learned: four sparks on the rim, the way the forge marks a perfect piece.
                for sx, sy in ((13, 1), (13, 24), (1, 13), (24, 13)):
                    px[x0 + sx, NODE_V + sy] = (255, 250, 210, 255)


def class_gui(ga):
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    tree_panel(img)
    choice_panel(img)
    cards_and_buttons(img)
    nodes(img)
    folder = ga.ASSETS / "textures/gui"
    folder.mkdir(parents=True, exist_ok=True)
    img.save(folder / "clases.png")


def emblem(ga):
    """The Emblema del olvido: a gold medallion round a disc of echo, with a closed eye across it."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy = 7.5, 8.0
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 5.4:
                # The disc: darker to the lower right, lit to the upper left.
                light = 1.0 - 0.07 * ((x - cx) + (y - cy))
                base = (34, 118, 126)
                image.putpixel((x, y), tuple(max(0, min(255, int(c * light))) for c in base) + (255,))
            elif d <= 7.0:
                shade = 240 if (x - cx) + (y - cy) < -3 else 190 if (x - cx) + (y - cy) < 3 else 128
                image.putpixel((x, y), (shade, int(shade * 0.78), int(shade * 0.3), 255))
            elif d <= 7.6:
                image.putpixel((x, y), (60, 40, 14, 255))
    # The closed eye: a pale lid across the disc, and the lashes under it.
    for x in range(4, 12):
        y = 8 + round(0.12 * (x - 7.5) ** 2) - 1
        image.putpixel((x, y), (206, 246, 240, 255))
    for x in (5, 7, 8, 10):
        image.putpixel((x, 10 if x in (7, 8) else 9 + (1 if x in (5, 10) else 0)), (120, 200, 196, 255))
    # A glint on the gold, and the loop it hangs from.
    image.putpixel((4, 3), (255, 250, 220, 255))
    for (x, y) in ((7, 0), (8, 0), (6, 1), (9, 1)):
        image.putpixel((x, y), (214, 170, 70, 255))
    image.save(ga.ASSETS / "textures/item/emblema_del_olvido.png")
    ga.write_json(ga.ASSETS / "models/item/emblema_del_olvido.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "forja:item/emblema_del_olvido"}})
    ga.write_json(ga.ASSETS / "items/emblema_del_olvido.json",
                  {"model": {"type": "minecraft:model", "model": "forja:item/emblema_del_olvido"}})
    # No recipe any more (Andy, 2026-09-29): the old emblem is kept only so the ones already made still work.
    # What changes class now is forged: the Medallón del olvido, below.
    old_recipe = ga.DATA / "recipe/emblema_del_olvido.json"
    if old_recipe.exists():
        old_recipe.unlink()


# The Medallón del olvido (forge/Relic): a núcleo set in an engaste, hung from a chain. One letter per part, as
# the staff, the tome and the lantern are drawn (generate_assets.drawn): G the núcleo, E the engaste, C the
# chain; 'g' is a glint on the núcleo.
MEDALLION = [
    ".CC.........CC..",
    "..CC.......CC...",
    "...CC.....CC....",
    "....CC...CC.....",
    ".....CCECC......",
    ".....EEEEE......",
    "...EEEGGGEEE....",
    "...EGgGGGGGE....",
    "..EGGGGGGGGGE...",
    "..EGGGGGGGGGE...",
    "..EGGGGGGGGGE...",
    "..EGGGGGGGGGE...",
    "...EGGGGGGGE....",
    "...EEEGGGEEE....",
    ".....EEEEE......",
    "................",
]
# The closed eye of the old emblem, across the núcleo: the lid, and the lashes under it.
MEDALLION_LID = [(4, 9), (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 9)]
MEDALLION_LASHES = [(5, 11), (7, 11), (9, 11)]


def medallion_layers(ga):
    """The three grayscale layers, núcleo, engaste and chain, which the item tints with its parts' materials."""
    pixels = ga.drawn(MEDALLION, {"G": 0, "E": 1, "C": 2})
    for (x, y), (label, level) in list(pixels.items()):
        if label == 0 and MEDALLION[y][x] == "G":
            # The stone is a dome lit from the upper left, darker to the lower right and at its rim.
            rim = MEDALLION[y][x - 1] != "G" and MEDALLION[y][x - 1] != "g" or MEDALLION[y][x + 1] != "G"
            shade = 205 - 11 * ((x - 7) + (y - 9.5)) - (24 if rim else 0)
            pixels[(x, y)] = (0, int(max(96, min(236, shade))))
        elif label == 2:
            # Links: every other pair of pixels along the chain catches the light.
            pixels[(x, y)] = (2, 236 if ((x + y) // 2) % 2 == 0 else 140)
    for at in MEDALLION_LID:
        pixels[at] = (0, 255)
    for at in MEDALLION_LASHES:
        pixels[at] = (0, 96)
    return ga.normalized_layers(pixels, 3)


def medallion(ga):
    folder = ga.ASSETS / "textures/item/medallon_del_olvido"
    folder.mkdir(parents=True, exist_ok=True)
    for index, image in enumerate(medallion_layers(ga)):
        image.save(folder / f"{index}.png")
        ga.write_json(ga.ASSETS / f"models/item/medallon_del_olvido/{index}.json",
                      {"parent": "minecraft:item/generated", "textures": {"layer0": f"forja:item/medallon_del_olvido/{index}"}})
    # Tinted like a forged piece: layer i takes the colour of the part in slot i (forge/Relic.create), and a
    # stack with no colours (a command, a recipe viewer) shows echo, gold and iron.
    defaults = [ga.MATERIAL_COLORS["eco"], ga.MATERIAL_COLORS["oro"], ga.MATERIAL_COLORS["hierro"]]
    ga.write_json(ga.ASSETS / "items/medallon_del_olvido.json", {"model": {
        "type": "minecraft:composite",
        "models": [{
            "type": "minecraft:model",
            "model": f"forja:item/medallon_del_olvido/{index}",
            "tints": [{"type": "minecraft:custom_model_data", "index": index, "default": defaults[index]}],
        } for index in range(3)],
    }})


def generate(ga):
    class_gui(ga)
    emblem(ga)
    medallion(ga)
