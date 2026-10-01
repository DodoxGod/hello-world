"""Lays the shots of the armour pressure indicator out on one contact sheet.

Run after `FORJA_SOLO=presion ./gradlew runClientGameTest`:

    python tools/hoja_presion.py [folder]

The shots (build/run/clientGameTest/screenshots/presion_*.png) are copied to the folder (by default
E:/IA/Claude/Forja_capturas_mejoras/presion) and laid out there as hoja_presion.png: the strip of the HUD over the
hotbar of each, zoomed 3x so the 9 by 10 pixel shield can be seen, with the whole first shot at the top.
"""

import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SHOTS = Path(__file__).resolve().parent.parent / "build" / "run" / "clientGameTest" / "screenshots"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("E:/IA/Claude/Forja_capturas_mejoras/presion")
PANELS = [
    ("presion_01_entero", "sin presión: entero, acero"),
    ("presion_02_medio", "5 golpes: la mitad, naranja"),
    ("presion_03_se_rompe", "10 golpes: se rompe (esquirlas)"),
    ("presion_04_roto", "roto, mientras dura la espera"),
    ("presion_05_recuperando_poco", "baja la presión: se junta, poco"),
    ("presion_06_recuperando_mas", "recuperando: más"),
    ("presion_07_de_nuevo_entero", "de nuevo entero"),
    ("presion_08_oculto_f1", "oculto con F1"),
    ("presion_09_oculto_creativo", "oculto en creativo"),
    ("presion_10_sin_armadura_ni_presion", "oculto: sin armadura ni presión"),
]
# The strip over the hotbar, in the 960 x 540 shots: armour row, hearts, hunger, and the gap between them.
CROP = (236, 372, 616, 440)
ZOOM = 3
COLUMNS = 2
GAP, CAPTION_H, HEADER_H = 8, 26, 44
BACKGROUND, INK = (24, 24, 28), (235, 235, 235)


def font(size):
    for name in ("arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for path in SHOTS.glob("presion_*.png"):
        shutil.copy2(path, OUT / path.name)
    cell_w, cell_h = (CROP[2] - CROP[0]) * ZOOM, (CROP[3] - CROP[1]) * ZOOM
    rows = (len(PANELS) + COLUMNS - 1) // COLUMNS
    width = COLUMNS * cell_w + (COLUMNS + 1) * GAP
    height = HEADER_H + rows * (cell_h + CAPTION_H + GAP) + GAP
    sheet = Image.new("RGB", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    draw.text((GAP, 10), "Indicador de presión de armadura (escudo junto a la armadura)", fill=INK, font=font(22))
    for i, (name, caption) in enumerate(PANELS):
        col, row = i % COLUMNS, i // COLUMNS
        x = GAP + col * (cell_w + GAP)
        y = HEADER_H + row * (cell_h + CAPTION_H + GAP)
        file = OUT / (name + ".png")
        if file.exists():
            image = Image.open(file).convert("RGB").crop(CROP)
            sheet.paste(image.resize((cell_w, cell_h), Image.NEAREST), (x, y))
        draw.text((x + 4, y + cell_h + 3), caption, fill=INK, font=font(18))
    sheet.save(OUT / "hoja_presion.png")
    print("saved", OUT / "hoja_presion.png")


if __name__ == "__main__":
    main()
