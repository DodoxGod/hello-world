"""Three candidate models for the new magic weapon, drawn the way the mod draws its gear: 16 x 16, a layer
to a part, each layer grey until the part's material tints it. Shading is worked out from the outline
(lit from the upper left), which is how the mod's hand-drawn sprites were done.

    python docs/arma_magica/modelos.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
MATERIALS = {
    "hierro": (216, 216, 216), "oro": (250, 214, 74), "diamante": (92, 219, 213), "cobre": (226, 128, 86), "obsidiana": (74, 52, 110),
    "madera": (150, 111, 51), "hueso": (226, 220, 196), "acero": (150, 160, 176), "cuero": (130, 84, 50), "netherita": (78, 70, 74),
}
GLOWS = {"ascua": (255, 150, 40), "escarcha": (120, 210, 255), "vacío": (190, 110, 255), "savia": (120, 230, 120)}

# a letter to a part; '*' is the glow, '+' a loose spark of it, 'p' the pages of the tome (never tinted)
STAFF = ("báculo de ascuas", {"S": "asta", "E": "engaste", "G": "núcleo"}, [
    "............E...",
    "..........E..E.+",
    ".........E.GG.E.",
    ".........EGGGG..",
    ".....+...EGGGGE.",
    "..........EGG.E.",
    ".........SEEEE..",
    "........SS......",
    ".......SS.......",
    "......SS........",
    ".....EE.........",
    "....SS..........",
    "...SS...........",
    "..SS............",
    ".EE.............",
    "E...............",
])
SCEPTRE = ("cetro rúnico", {"M": "mango", "H": "cabeza", "R": "runa"}, [
    "......+...HHH...",
    ".........HHHHH..",
    "........HHRRHHH.",
    "........HRRRRHH+",
    "........HHRRHHH.",
    ".........HHHHH..",
    "........MHHH....",
    ".......MM...+...",
    "......MM........",
    ".....MM.........",
    "....HMM.........",
    "...MM...........",
    "..MM............",
    ".HH.............",
    "HHH.............",
    ".H..............",
])
TOME = ("grimorio forjado", {"T": "tapas", "C": "cantoneras", "L": "lomo", "B": "broche"}, [
    "................",
    "...LTTTTTTTTT...",
    "..LLCTTTTTTTCp..",
    "..LLTTTTTTTTTp..",
    "..LLTTT***TTTp..",
    "..LLTT*BBB*TTp.+",
    "..LLTT*BBB*TTp..",
    "..LLTT*BBB*TTp..",
    "+.LLTTT***TTTp..",
    "..LLTTTTTTTTTp..",
    "..LLCTTTTTTTCp..",
    "..LLTTTTTTTTTp..",
    "...Lpppppppppp..",
    "....ppppppppp...",
    "................",
    "................",
])
DESIGNS = (STAFF, SCEPTRE, TOME)


def shade_of(rows, x, y, letter):
    """Lit from the upper left: an edge that faces the light is pale, one that faces away is dark."""
    def same(dx, dy):
        xx, yy = x + dx, y + dy
        return 0 <= xx < 16 and 0 <= yy < 16 and rows[yy][xx] == letter
    lit = not same(-1, 0) or not same(0, -1)
    dark = not same(1, 0) or not same(0, 1)
    if lit and not dark:
        return 1.18
    if dark and not lit:
        return 0.62
    return 0.92 if lit and dark else 0.85


def draw(design, colours, glow):
    name, parts, rows = design
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, letter in enumerate(row):
            if letter == ".":
                continue
            if letter in "*+":
                k = 1.0 if letter == "*" else 0.85
                image.putpixel((x, y), tuple(min(255, int(v * k)) for v in glow) + (255,))
            elif letter == "p":
                image.putpixel((x, y), (236, 226, 196, 255) if (x + y) % 2 else (212, 200, 168, 255))
            else:
                base = colours[parts[letter]]
                k = shade_of(rows, x, y, letter)
                if parts[letter] in ("núcleo", "runa", "broche"):
                    base = tuple(min(255, int(a * 0.12 + b * 0.95)) for a, b in zip(base, glow))
                    if shade_of(rows, x, y, letter) > 1.0 and rows[y][x - 1] != letter and rows[y - 1][x] != letter:
                        k = 1.6
                image.putpixel((x, y), tuple(max(0, min(255, int(v * k))) for v in base) + (255,))
    return image


def sheet():
    big = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 30)
    font = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 20)
    small = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 16)
    combos = {
        "báculo de ascuas": [({"asta": "madera", "engaste": "hierro", "núcleo": "diamante"}, "ascua"),
                             ({"asta": "hueso", "engaste": "oro", "núcleo": "diamante"}, "escarcha"),
                             ({"asta": "obsidiana", "engaste": "netherita", "núcleo": "diamante"}, "vacío"),
                             ({"asta": "cobre", "engaste": "acero", "núcleo": "diamante"}, "savia")],
        "cetro rúnico": [({"mango": "madera", "cabeza": "acero", "runa": "diamante"}, "ascua"),
                         ({"mango": "hueso", "cabeza": "hierro", "runa": "diamante"}, "escarcha"),
                         ({"mango": "obsidiana", "cabeza": "oro", "runa": "diamante"}, "vacío"),
                         ({"mango": "cuero", "cabeza": "cobre", "runa": "diamante"}, "savia")],
        "grimorio forjado": [({"tapas": "netherita", "cantoneras": "oro", "lomo": "cuero", "broche": "diamante"}, "ascua"),
                             ({"tapas": "acero", "cantoneras": "hierro", "lomo": "cuero", "broche": "diamante"}, "escarcha"),
                             ({"tapas": "obsidiana", "cantoneras": "oro", "lomo": "cuero", "broche": "diamante"}, "vacío"),
                             ({"tapas": "cobre", "cantoneras": "hueso", "lomo": "cuero", "broche": "diamante"}, "savia")],
    }
    width, height = 3 * 470 + 40, 830
    out = Image.new("RGB", (width, height), (24, 22, 20))
    d = ImageDraw.Draw(out)
    d.text((24, 14), "Arma nueva de tipo mágico · tres modelos para elegir", font=big, fill=(255, 208, 138))
    for column, design in enumerate(DESIGNS):
        name, parts, rows = design
        x0 = 20 + column * 470
        d.rectangle((x0, 70, x0 + 450, height - 20), fill=(34, 31, 28), outline=(70, 62, 54))
        d.text((x0 + 14, 80), f"{'ABC'[column]} · {name.capitalize()}", font=font, fill=(240, 228, 204))
        d.text((x0 + 14, 108), "piezas: " + " + ".join(dict.fromkeys(parts.values())), font=small, fill=(190, 178, 158))
        for k, (materials, glow) in enumerate(combos[name]):
            colours = {part: MATERIALS[material] for part, material in materials.items()}
            sprite = draw(design, colours, GLOWS[glow])
            if k == 0:
                hero = sprite.resize((352, 352), Image.NEAREST)
                pad = Image.new("RGB", (352, 352), (46, 42, 38))
                for yy in range(0, 352, 22):
                    for xx in range(0, 352, 22):
                        if (xx // 22 + yy // 22) % 2:
                            ImageDraw.Draw(pad).rectangle((xx, yy, xx + 21, yy + 21), fill=(52, 48, 43))
                pad.paste(hero, (0, 0), hero)
                out.paste(pad, (x0 + 49, 140))
                d.text((x0 + 49, 498), " · ".join(materials.values()) + f" · {glow}", font=small, fill=(190, 178, 158))
            else:
                tile = sprite.resize((128, 128), Image.NEAREST)
                pad = Image.new("RGB", (128, 128), (46, 42, 38))
                pad.paste(tile, (0, 0), tile)
                px = x0 + 14 + (k - 1) * 146
                out.paste(pad, (px, 536))
                d.text((px, 668), glow, font=small, fill=GLOWS[glow])
                d.text((px, 688), ", ".join(list(materials.values())[:2]), font=small, fill=(170, 158, 138))
            sprite.save(OUT / f"{name.split()[0]}_{glow}.png")
    notes = [
        ["A distancia: carga y suelta un proyectil del", "elemento de su núcleo. Sin munición: tiene", "una reserva de poder que se rehace sola."],
        ["Cuerpo a cuerpo + al cargarlo suelta un ANILLO", "(la onda expansiva del mod) alrededor tuyo.", "La runa decide qué hace el anillo."],
        ["Control de zona: abre y traza una RUNA en el", "suelo que dura unos segundos (quema, congela,", "ata…). El broche decide cuál. Se lleva en la otra mano."],
    ]
    for column, lines in enumerate(notes):
        for i, line in enumerate(lines):
            d.text((20 + column * 470 + 14, 724 + i * 22), line, font=small, fill=(225, 214, 190))
    out.save(OUT / "tres_modelos.png")
    print(out.size)


if __name__ == "__main__":
    sheet()
