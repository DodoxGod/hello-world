"""Three variants of model A, the staff, for Andy to pick from. Same drawing rules as modelos.py.

    python docs/arma_magica/variantes_baculo.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from modelos import GLOWS, MATERIALS, draw

OUT = Path(__file__).resolve().parent
PARTS = {"S": "asta", "E": "engaste", "G": "núcleo"}

CLAW = ("báculo de garra", PARTS, [
    "...........E....",
    ".........E..E.+.",
    "........E.GG.E..",
    "........EGGGGE..",
    "....+...EGGGG.E.",
    ".........EGG.E..",
    "........SEEEE...",
    ".......SE.......",
    "......SS........",
    ".....ES.........",
    "....SS..........",
    "...SS...........",
    "..ES............",
    ".SS.............",
    "EE..............",
    "E...............",
])
CRESCENT = ("báculo de media luna", PARTS, [
    ".......EEEE.....",
    "......EE...E..+.",
    ".....EE.........",
    ".....E....GG....",
    ".....E...GGGG...",
    ".....EE..GGGG.E.",
    "......EE..GG.EE.",
    ".......EEEEEEE..",
    "......SSEEE.....",
    ".....SS.........",
    "....SS..........",
    "...SS...+.......",
    "..SS............",
    ".SS.............",
    "EE..............",
    "E...............",
])
LANTERN = ("báculo de farol", PARTS, [
    "........EEEE....",
    ".......EE..EE...",
    ".......E....E...",
    "......SE....E.+.",
    "......S....EEE..",
    ".....SS....EGE..",
    ".....S.....GGG..",
    "....SS.....EGE..",
    "....S......EEE..",
    "...SS...........",
    "...S....+.......",
    "..SS............",
    "..S.............",
    ".SS.............",
    ".E..............",
    "E...............",
])
DESIGNS = (CLAW, CRESCENT, LANTERN)
NOTES = (
    ["La garra: tres uñas de metal abrazan el núcleo.", "El más clásico; el núcleo es lo que más se ve,", "así que el elemento se lee de un vistazo."],
    ["La media luna: una hoja curva de metal y el núcleo", "flotando entre sus cuernos, sin tocarla.", "Silueta grande: se distingue de lejos."],
    ["El farol: un cayado del que cuelga una jaula con", "la brasa dentro, como el farol de pavesa del mod.", "El más «de forja» de los tres."],
)
COMBOS = [({"asta": "madera", "engaste": "hierro", "núcleo": "diamante"}, "ascua"),
          ({"asta": "hueso", "engaste": "oro", "núcleo": "diamante"}, "escarcha"),
          ({"asta": "obsidiana", "engaste": "netherita", "núcleo": "diamante"}, "vacío"),
          ({"asta": "cobre", "engaste": "acero", "núcleo": "diamante"}, "savia")]


def sheet():
    big = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 30)
    font = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 20)
    small = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 16)
    width, height = 3 * 470 + 40, 830
    out = Image.new("RGB", (width, height), (24, 22, 20))
    d = ImageDraw.Draw(out)
    d.text((24, 14), "Báculo (modelo A) · tres variantes para elegir    —    el Grimorio (C) queda guardado", font=big, fill=(255, 208, 138))
    for column, design in enumerate(DESIGNS):
        name, parts, rows = design
        x0 = 20 + column * 470
        d.rectangle((x0, 70, x0 + 450, height - 20), fill=(34, 31, 28), outline=(70, 62, 54))
        d.text((x0 + 14, 80), f"A{column + 1} · {name.capitalize()}", font=font, fill=(240, 228, 204))
        d.text((x0 + 14, 108), "piezas: asta + engaste + núcleo", font=small, fill=(190, 178, 158))
        for k, (materials, glow) in enumerate(COMBOS):
            colours = {part: MATERIALS[material] for part, material in materials.items()}
            sprite = draw(design, colours, GLOWS[glow])
            sprite.save(OUT / f"{name.replace(' ', '_')}_{glow}.png")
            if k == 0:
                hero = sprite.resize((352, 352), Image.NEAREST)
                pad = Image.new("RGB", (352, 352), (46, 42, 38))
                pd = ImageDraw.Draw(pad)
                for yy in range(0, 352, 22):
                    for xx in range(0, 352, 22):
                        if (xx // 22 + yy // 22) % 2:
                            pd.rectangle((xx, yy, xx + 21, yy + 21), fill=(52, 48, 43))
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
        for i, line in enumerate(NOTES[column]):
            d.text((x0 + 14, 724 + i * 22), line, font=small, fill=(225, 214, 190))
    out.save(OUT / "baculo_tres_variantes.png")
    print(out.size)


if __name__ == "__main__":
    sheet()
