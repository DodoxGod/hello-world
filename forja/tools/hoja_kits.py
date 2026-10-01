"""Contact sheet of the ingots (variant A) and the repair kits (variant B), with the FORJA_SOLO=kits screenshots.

    python tools/hoja_kits.py [out.jpg]

Top: every ingot and every kit as committed, at 1x on the slot grey and at 6x, each kit under the name of its metal.
Below: the screenshots FORJA_SOLO=kits takes (build/run/clientGameTest/screenshots/kits_*.png), two to a row.
Writes E:/IA/Claude/Forja_capturas_mejoras/lingotes_kits/hoja_lingotes_kits.jpg unless told otherwise.
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_assets as ga  # noqa: E402

ITEMS = ga.ASSETS / "textures/item"
SHOTS = ga.ROOT / "build/run/clientGameTest/screenshots"
OUT = Path("E:/IA/Claude/Forja_capturas_mejoras/lingotes_kits/hoja_lingotes_kits.jpg")
BACK = (34, 32, 38)
INK = (236, 230, 218)
GOLD = (250, 210, 140)
SLOT = (139, 139, 139)
BIG = 6


def font(size):
    for name in ("arialbd.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def tile(path, label, f):
    """One texture: 1x on a slot, 6x beside it, its name under both."""
    image = Image.open(path).convert("RGBA")
    cell = Image.new("RGB", (16 + 8 + 16 * BIG, 16 * BIG + 22), BACK)
    d = ImageDraw.Draw(cell)
    d.rectangle((0, 40, 19, 59), fill=SLOT)
    cell.paste(image, (2, 42), image)
    big = image.resize((16 * BIG, 16 * BIG), Image.NEAREST)
    d.rectangle((24, 0, 24 + 16 * BIG - 1, 16 * BIG - 1), fill=(58, 56, 64))
    cell.paste(big, (24, 0), big)
    d.text((0, 16 * BIG + 4), label, fill=INK, font=f)
    return cell


def grid(cells, columns, gap=14):
    w, h = cells[0].size
    rows = (len(cells) + columns - 1) // columns
    out = Image.new("RGB", (columns * (w + gap), rows * (h + gap)), BACK)
    for i, cell in enumerate(cells):
        out.paste(cell, ((i % columns) * (w + gap), (i // columns) * (h + gap)))
    return out


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.parent.mkdir(parents=True, exist_ok=True)
    small, title = font(13), font(26)
    ingots = [tile(ITEMS / f"{name}.png", name, small) for name in [*ga.ALLOY_COLORS, "oricalco", "lingote_de_temple"]]
    kits = [tile(ITEMS / f"kit_de_reparacion_{name}.png", name, small) for name in ga.repair_kit_materials()]
    shots = sorted(SHOTS.glob("kits_*.png"))
    a, b = grid(ingots, 9), grid(kits, 10)
    shot_w = 960
    width = max(a.width, b.width, 2 * shot_w + 20) + 40
    shot_rows = (len(shots) + 1) // 2
    height = 70 + a.height + 60 + b.height + 60 + shot_rows * (540 + 40) + 20
    sheet = Image.new("RGB", (width, height), BACK)
    d = ImageDraw.Draw(sheet)
    y = 16
    d.text((20, y), "Forja: lingotes (variante A) y kits de reparacion (variante B)", fill=INK, font=title)
    y += 50
    d.text((20, y), f"Lingotes: {len(ingots)}", fill=GOLD, font=font(20))
    sheet.paste(a, (20, y + 28))
    y += 28 + a.height + 16
    d.text((20, y), f"Kits de reparacion: {len(kits)} (uno por metal que puede ser parte principal)", fill=GOLD, font=font(20))
    sheet.paste(b, (20, y + 28))
    y += 28 + b.height + 20
    for i, path in enumerate(shots):
        x = 20 + (i % 2) * (shot_w + 20)
        top = y + (i // 2) * (540 + 40)
        d.text((x, top), path.stem, fill=GOLD, font=font(18))
        shot = Image.open(path).convert("RGB")
        shot.thumbnail((shot_w, 540))
        sheet.paste(shot, (x, top + 26))
    sheet.save(out, quality=88, optimize=True)
    print(f"{len(ingots)} lingotes, {len(kits)} kits, {len(shots)} capturas; hoja en {out} ({out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
