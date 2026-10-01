"""Lays the shots of the v4 mob AI out on one contact sheet.

Run after `FORJA_SOLO=v4 ./gradlew runClientGameTest`:

    python tools/hoja_v4.py [folder]

The shots (build/run/clientGameTest/screenshots/v4_*.png) are copied to the folder (by default
E:/IA/Claude/Forja_capturas_mejoras/v4_mod) and laid out there as hoja_v4.png, a row per scene: the shield and its
bash, a sword off the floor, an ender pearl up a pillar, a torch put out, a player brought down from a pillar, the
ambush and the captain.
"""

import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SHOTS = Path(__file__).resolve().parent.parent / "build" / "run" / "clientGameTest" / "screenshots"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("E:/IA/Claude/Forja_capturas_mejoras/v4_mod")
ROWS = [
    ("Escudo inteligente y golpe de escudo", [("v4_01_escudo_arriba", "escudo arriba"),
                                             ("v4_02_golpe_de_escudo_aviso", "bloquea: aviso del golpe"),
                                             ("v4_03_golpe_de_escudo", "golpe de escudo: empujado")]),
    ("Recoge tu espada", [("v4_04_va_a_por_la_espada", "va a por la espada del suelo"),
                          ("v4_05_con_la_espada", "con la espada de diamante")]),
    ("Perla al pilar", [("v4_06_perla_aviso", "aviso: perla en la mano"), ("v4_07_perla_en_vuelo", "la perla vuela"),
                        ("v4_08_perla_llega", "llega junto al jugador")]),
    ("Apaga la antorcha", [("v4_09_antorcha_encendida", "de noche, antorcha encendida"),
                           ("v4_10_antorcha_golpes", "la golpea"), ("v4_11_antorcha_apagada", "apagada (y soltada)")]),
    ("Bajarlo del pilar", [("v4_12_flecha_de_empuje_aviso", "flecha de empuje: aviso"),
                           ("v4_13_flecha_de_empuje", "sale la flecha"), ("v4_14_bajado_del_pilar", "después (esta toma no lo tiró)"),
                           ("v4_15_carga_de_viento", "carga de viento"), ("v4_16_carga_de_viento_levanta", "estalla en la cima")]),
    ("Emboscada", [("v4_17_emboscada_desde_arriba", "escondido tras el muro (desde arriba)"),
                   ("v4_18_emboscada_vista_jugador", "lo que ve el jugador")]),
    ("Capitán", [("v4_19_capitan_formacion", "formación del capitán"), ("v4_20_capitan_carga", "la carga")]),
]
CELL_W, CELL_H = 420, 236
LABEL_W, HEADER_H, CAPTION_H, GAP = 230, 40, 26, 6
BACKGROUND, INK, MISSING = (24, 24, 28), (235, 235, 235), (48, 48, 54)


def font(size):
    for name in ("arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def find(name):
    exact = SHOTS / (name + ".png")
    if exact.exists():
        return exact
    matches = sorted(SHOTS.glob("*" + name + "*.png"))
    return matches[-1] if matches else None


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    columns = max(len(shots) for _, shots in ROWS)
    width = LABEL_W + columns * (CELL_W + GAP) + GAP
    height = HEADER_H + len(ROWS) * (CELL_H + CAPTION_H + GAP) + GAP
    sheet = Image.new("RGB", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    big, small = font(22), font(15)
    draw.text((GAP * 2, 8), "Forja: IA v4 de los monstruos en el juego (FORJA_SOLO=v4)", fill=INK, font=big)
    found = 0
    for r, (title, shots) in enumerate(ROWS):
        top = HEADER_H + r * (CELL_H + CAPTION_H + GAP)
        draw.text((GAP * 2, top + CELL_H // 2 - 10), title, fill=INK, font=small)
        for c, (name, caption) in enumerate(shots):
            left = LABEL_W + GAP + c * (CELL_W + GAP)
            path = find(name)
            if path is None:
                draw.rectangle([left, top, left + CELL_W, top + CELL_H], fill=MISSING)
                draw.text((left + 10, top + 10), "falta " + name, fill=INK, font=small)
            else:
                found += 1
                shutil.copy(path, OUT / (name + ".png"))
                image = Image.open(path).convert("RGB")
                image.thumbnail((CELL_W, CELL_H))
                sheet.paste(image, (left + (CELL_W - image.width) // 2, top + (CELL_H - image.height) // 2))
            draw.text((left + 4, top + CELL_H + 4), caption, fill=INK, font=small)
    target = OUT / "hoja_v4.png"
    sheet.save(target)
    print(f"{found} capturas, hoja en {target}")


if __name__ == "__main__":
    main()
