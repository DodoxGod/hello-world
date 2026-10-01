"""Contact sheet of oricalco as a forge material, with the FORJA_SOLO=oricalco screenshots.

    python tools/hoja_oricalco.py [out.jpg]

Top: the oricalco ingot, its repair kit and its pearl as committed, at 1x on the slot grey and at 6x. Below: the
screenshots FORJA_SOLO=oricalco takes (build/run/clientGameTest/screenshots/oricalco_*.png), two to a row.
Writes E:/IA/Claude/Forja_capturas_mejoras/oricalco_material/hoja_oricalco.jpg unless told otherwise.
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_assets as ga  # noqa: E402
from hoja_kits import BACK, GOLD, INK, font, grid, tile  # noqa: E402

ITEMS = ga.ASSETS / "textures/item"
SHOTS = ga.ROOT / "build/run/clientGameTest/screenshots"
OUT = Path("E:/IA/Claude/Forja_capturas_mejoras/oricalco_material/hoja_oricalco.jpg")
SHOT_W = 960


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    title, small = font(26), font(14)
    textures = [tile(ITEMS / f"{name}.png", name, small)
                for name in ("oricalco", "kit_de_reparacion_oricalco", "perla_de_oricalco")]
    top = grid(textures, 3)
    shots = []
    for path in sorted(SHOTS.glob("oricalco_*.png")):
        image = Image.open(path).convert("RGB")
        image = image.resize((SHOT_W, round(image.height * SHOT_W / image.width)), Image.LANCZOS)
        cell = Image.new("RGB", (image.width, image.height + 24), BACK)
        cell.paste(image, (0, 0))
        ImageDraw.Draw(cell).text((4, image.height + 4), path.stem, fill=INK, font=small)
        shots.append(cell)
    if not shots:
        raise SystemExit(f"no screenshots in {SHOTS}: run FORJA_SOLO=oricalco ./gradlew runClientGameTest first")
    bottom = grid(shots, 2)
    width = max(top.width, bottom.width) + 40
    sheet = Image.new("RGB", (width, 70 + top.height + 30 + bottom.height + 20), BACK)
    d = ImageDraw.Draw(sheet)
    d.text((20, 20), "Oricalco, material de forja: lingote, kit y perla; piezas, objetos y conjunto puesto", fill=GOLD, font=title)
    sheet.paste(top, (20, 70))
    sheet.paste(bottom, (20, 70 + top.height + 30))
    out.parent.mkdir(parents=True, exist_ok=True)
    quality = 88
    while True:
        sheet.save(out, quality=quality, optimize=True)
        if out.stat().st_size < 3 * 1024 * 1024 or quality <= 50:
            break
        quality -= 6
    print(f"{out}: {sheet.width}x{sheet.height}, {out.stat().st_size // 1024} KB, quality {quality}")


if __name__ == "__main__":
    main()
