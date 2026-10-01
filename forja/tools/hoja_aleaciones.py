"""Contact sheets of the far forges and their alloys (docs/ALEACIONES_NETHER_END.md).

Run after the client section that films each one:

    FORJA_SOLO=fragua_caida ./gradlew runClientGameTest   ->  python tools/hoja_aleaciones.py nether
    FORJA_SOLO=fragua_vacio ./gradlew runClientGameTest   ->  python tools/hoja_aleaciones.py end

The shots (build/run/clientGameTest/screenshots/<prefix>*.png) are copied to
E:/IA/Claude/Forja_capturas_mejoras/aleacion_nether (or aleacion_end) and laid out there as a JPEG under 3 MB, with
a strip of the textures this work drew (ingots, forge faces, the effect icon) at 8x across the top.
"""

import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "build" / "run" / "clientGameTest" / "screenshots"
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "forja" / "textures"

SETS = {
    "nether": {
        "prefix": "fragua_caida_",
        "out": Path("E:/IA/Claude/Forja_capturas_mejoras/aleacion_nether"),
        "sheet": "hoja_aleacion_nether.jpg",
        "title": "Forja: la fragua de almas de la Fragua caída y las aleaciones del Nether",
        "textures": ["item/fatuo", "item/magmacero", "item/kit_de_reparacion_fatuo", "item/kit_de_reparacion_magmacero",
                     "block/fragua_de_almas_side", "block/fragua_de_almas_top",
                     "block/fragua_de_almas_encendida_side", "block/fragua_de_almas_encendida_top",
                     "block/costra_de_magma", "mob_effect/llama_fatua"],
    },
    "end": {
        "prefix": "fragua_vacio_",
        "out": Path("E:/IA/Claude/Forja_capturas_mejoras/aleacion_end"),
        "sheet": "hoja_aleacion_end.jpg",
        "title": "Forja: la fragua del vacío del End y el eterio",
        "textures": ["item/eterio", "item/kit_de_reparacion_eterio", "block/fragua_del_vacio_side", "block/fragua_del_vacio_top",
                     "block/fragua_del_vacio_encendida_side", "block/fragua_del_vacio_encendida_top"],
    },
}

LIMIT = 3 * 1024 * 1024


def font(size):
    for name in ("arialbd.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def main():
    which = sys.argv[1] if len(sys.argv) > 1 else "nether"
    spec = SETS[which]
    out = spec["out"]
    out.mkdir(parents=True, exist_ok=True)
    shots = sorted(SHOTS.glob(spec["prefix"] + "*.png"))
    if shots:
        for shot in shots:
            shutil.copy2(shot, out / shot.name)
    else:
        # Another section ran since (each run empties the folder): lay out the copies made last time.
        shots = sorted(out.glob(spec["prefix"] + "*.png"))
    if not shots:
        raise SystemExit(f"no shots {spec['prefix']}* in {SHOTS} or {out}")

    columns = 3
    cell_w = 640
    first = Image.open(shots[0])
    cell_h = round(cell_w * first.height / first.width)
    label_h = 30
    textures = [name for name in spec["textures"] if (TEXTURES / f"{name}.png").exists()]
    strip_h = 16 * 8 + 46 if textures else 0
    rows = (len(shots) + columns - 1) // columns
    header = 56
    width = columns * cell_w + (columns + 1) * 10
    height = header + strip_h + rows * (cell_h + label_h + 10) + 10
    sheet = Image.new("RGB", (width, height), (30, 28, 34))
    draw = ImageDraw.Draw(sheet)
    draw.text((12, 14), spec["title"], fill=(240, 232, 214), font=font(26))

    x = 12
    y = header
    for name in textures:
        image = Image.open(TEXTURES / f"{name}.png").convert("RGBA")
        big = image.resize((image.width * 8, image.height * 8), Image.NEAREST)
        backdrop = Image.new("RGBA", big.size, (139, 139, 139, 255))
        backdrop.alpha_composite(big)
        sheet.paste(backdrop.convert("RGB"), (x, y))
        label = (name.split("/")[-1].replace("fragua_de_almas", "almas").replace("fragua_del_vacio", "vacio")
                 .replace("kit_de_reparacion", "kit"))
        draw.text((x, y + big.height + 4), label.replace("_", " "), fill=(220, 214, 200), font=font(12))
        x += big.width + 14
    y += strip_h

    for index, shot in enumerate(shots):
        column = index % columns
        row = index // columns
        cx = 10 + column * (cell_w + 10)
        cy = y + row * (cell_h + label_h + 10)
        image = Image.open(shot).convert("RGB").resize((cell_w, cell_h), Image.LANCZOS)
        sheet.paste(image, (cx, cy))
        draw.text((cx + 4, cy + cell_h + 4), shot.stem[len(spec["prefix"]):].replace("_", " "), fill=(250, 210, 140), font=font(18))

    path = out / spec["sheet"]
    quality = 88
    while True:
        sheet.save(path, quality=quality, optimize=True)
        if path.stat().st_size <= LIMIT or quality <= 40:
            break
        quality -= 8
    print(f"{len(shots)} capturas; hoja en {path} ({path.stat().st_size // 1024} KB, calidad {quality})")


if __name__ == "__main__":
    main()
