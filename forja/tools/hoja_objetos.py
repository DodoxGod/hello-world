"""Contact sheets of Forja's item and block textures, to judge the pixel art by eye.

    python tools/hoja_objetos.py <folder> [prefix]
    python tools/hoja_objetos.py --comparar <batch> <git revision> <out.jpg>

The second form lays a batch of the visual pass (BATCHES below) out before and after: "antes" read from that
git revision, "después" from the working tree, each at 8x and at 1x and 2x on the slot grey.

Writes <prefix>_objetos.jpg (loose items), <prefix>_forjados.jpg (forged gear in a few material sets),
<prefix>_piezas.jpg (parts, moulds and templates) and <prefix>_bloques.jpg (block textures) into the folder.
Layered items are tinted the way the game tints them: each grayscale layer multiplied by its slot's material colour.
Every texture is drawn at 4x on the inventory slot grey, and at 1x beside it, which is how it is really seen.
"""

import io
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_assets as ga  # noqa: E402

TEX = ga.ASSETS / "textures"
SLOT = (139, 139, 139)
BACK = (40, 40, 46)
INK = (235, 235, 235)
SCALE = 4
CELL_W, CELL_H = 64 * 2 + 30, 64 + 26


def font(size):
    for name in ("arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


FONT = font(12)
TITLE = font(22)


def tinted(path, colour):
    image = Image.open(path).convert("RGBA")
    if colour is None:
        return image
    r, g, b = (colour >> 16) & 255, (colour >> 8) & 255, colour & 255
    px = image.load()
    for y in range(image.height):
        for x in range(image.width):
            pr, pg, pb, pa = px[x, y]
            px[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return image


def first_frame(image):
    if image.height > image.width:
        return image.crop((0, 0, image.width, image.width))
    return image


def cell(image, label):
    image = first_frame(image)
    out = Image.new("RGB", (CELL_W, CELL_H), BACK)
    big = image.resize((64, 64) if image.width <= 16 else (64, 64), Image.NEAREST)
    pad = Image.new("RGBA", (64, 64), SLOT + (255,))
    pad.alpha_composite(big)
    out.paste(pad.convert("RGB"), (4, 4))
    # 1x and 2x beside it, on the slot grey, as the inventory shows them.
    small = Image.new("RGBA", (18, 18), SLOT + (255,))
    small.alpha_composite(image.resize((16, 16), Image.NEAREST), (1, 1))
    out.paste(small.convert("RGB"), (72, 4))
    two = Image.new("RGBA", (34, 34), SLOT + (255,))
    two.alpha_composite(image.resize((32, 32), Image.NEAREST), (1, 1))
    out.paste(two.convert("RGB"), (72, 26))
    ImageDraw.Draw(out).text((4, 70), label[:24], fill=INK, font=FONT)
    return out


def sheet(title, cells, path, columns=10):
    rows = (len(cells) + columns - 1) // columns
    out = Image.new("RGB", (columns * CELL_W, 40 + rows * CELL_H), BACK)
    ImageDraw.Draw(out).text((8, 8), title, fill=INK, font=TITLE)
    for i, c in enumerate(cells):
        out.paste(c, (i % columns * CELL_W, 40 + i // columns * CELL_H))
    path.parent.mkdir(parents=True, exist_ok=True)
    quality = 90
    while True:
        out.save(path, quality=quality)
        if path.stat().st_size < 2_900_000 or quality < 40:
            break
        quality -= 10
    print("wrote", path, out.size, path.stat().st_size // 1024, "KB")


SETS = [
    ("hierro", {"HEAD": "hierro", "PLATE": "hierro", "HANDLE": "madera", "EXTRA": "cuero", "LINING": "cuero"}),
    ("oro", {"HEAD": "oro", "PLATE": "oro", "HANDLE": "hueso", "EXTRA": "hierro", "LINING": "cuero"}),
    ("diamante", {"HEAD": "diamante", "PLATE": "diamante", "HANDLE": "obsidiana", "EXTRA": "oro", "LINING": "cuero"}),
    ("netherita", {"HEAD": "netherita", "PLATE": "netherita", "HANDLE": "vara_de_blaze", "EXTRA": "hierro", "LINING": "cuero"}),
    ("cobre", {"HEAD": "cobre", "PLATE": "cobre", "HANDLE": "madera", "EXTRA": "cuero", "LINING": "cuero"}),
]


def forged_cells():
    cells = []
    for type_id, slots in ga.TYPES.items():
        folder = TEX / "item" / type_id
        layers = sorted(p for p in folder.glob("*.png") if p.stem.isdigit())
        if not layers:
            continue
        for name, roles in SETS:
            image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            for layer in layers:
                index = int(layer.stem)
                part = slots[index] if index < len(slots) else slots[-1]
                colour = ga.MATERIAL_COLORS[roles[ga.PARTS[part]]]
                image.alpha_composite(tinted(layer, colour))
            cells.append(cell(image, f"{type_id} {name}"))
    return cells


def loose_cells():
    cells = []
    for p in sorted((TEX / "item").glob("*.png")):
        cells.append(cell(Image.open(p).convert("RGBA"), p.stem))
    for sub in ("jarra", "medallon_del_olvido", "escudo", "guanteletes", "grimorio"):
        for p in sorted((TEX / "item" / sub).glob("*.png")):
            cells.append(cell(Image.open(p).convert("RGBA"), f"{sub}/{p.stem}"))
    return cells


def part_cells():
    cells = []
    for p in sorted((TEX / "item" / "parte").glob("*.png")):
        colour = ga.MATERIAL_COLORS["hierro" if ga.PARTS.get(p.stem) in ("HEAD", "PLATE") else "madera"]
        cells.append(cell(tinted(p, colour), p.stem))
    for sub in ("molde", "plantilla", "marco"):
        for p in sorted((TEX / "item" / sub).glob("*.png"))[:60]:
            cells.append(cell(Image.open(p).convert("RGBA"), f"{sub}/{p.stem}"))
    return cells


def block_cells():
    return [cell(Image.open(p).convert("RGBA"), p.stem) for p in sorted((TEX / "block").glob("*.png"))]


# What each batch of the visual pass touched: a texture path under textures/, or "forjado:<type>" for a piece of
# gear composed from its layers in four of the material sets.
BATCHES = {
    "1_objetos": ["forjado:cincel", "forjado:farol", "item/parte/punta_cincel.png", "item/cinturon.png",
                  "item/molde/punta_cincel.png", "item/marco/farol.png"],
}


def at_revision(revision, path):
    rel = (ga.ASSETS / "textures" / path).relative_to(ga.ROOT.parent).as_posix()
    data = subprocess.run(["git", "show", f"{revision}:{rel}"], capture_output=True, cwd=ga.ROOT).stdout
    return Image.open(io.BytesIO(data)).convert("RGBA") if data else Image.new("RGBA", (16, 16))


def _tint(image, colour):
    r, g, b = (colour >> 16) & 255, (colour >> 8) & 255, colour & 255
    px = image.load()
    for y in range(image.height):
        for x in range(image.width):
            pr, pg, pb, pa = px[x, y]
            px[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return image


def forged_at(revision, type_id, roles):
    slots = ga.TYPES[type_id]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for index in range(len(slots)):
        path = f"item/{type_id}/{index}.png"
        layer = at_revision(revision, path) if revision else Image.open(TEX / path).convert("RGBA")
        colour = ga.MATERIAL_COLORS[roles[ga.PARTS[slots[index]]]]
        image.alpha_composite(_tint(layer.copy(), colour))
    return image


def pair_cell(before, after, label):
    """Before and after side by side: each at 8x, with 1x and 2x under it."""
    out = Image.new("RGB", (2 * 136 + 24, 136 + 64), BACK)
    for k, image in enumerate((first_frame(before), first_frame(after))):
        x = 4 + k * 148
        big = Image.new("RGBA", (128, 128), SLOT + (255,))
        big.alpha_composite(image.resize((128, 128), Image.NEAREST))
        out.paste(big.convert("RGB"), (x, 22))
        one = Image.new("RGBA", (20, 20), SLOT + (255,))
        one.alpha_composite(image.resize((16, 16), Image.NEAREST), (2, 2))
        out.paste(one.convert("RGB"), (x, 154))
        two = Image.new("RGBA", (36, 36), SLOT + (255,))
        two.alpha_composite(image.resize((32, 32), Image.NEAREST), (2, 2))
        out.paste(two.convert("RGB"), (x + 26, 154))
        ImageDraw.Draw(out).text((x, 4), ("antes" if k == 0 else "después"), fill=INK, font=FONT)
    ImageDraw.Draw(out).text((70, 160), label[:30], fill=INK, font=FONT)
    return out


def compare(batch, revision, path):
    cells = []
    for entry in BATCHES[batch]:
        if entry.startswith("forjado:"):
            type_id = entry.split(":", 1)[1]
            for name, roles in SETS[:4]:
                cells.append(pair_cell(forged_at(revision, type_id, roles), forged_at(None, type_id, roles), f"{type_id} ({name})"))
        else:
            cells.append(pair_cell(at_revision(revision, entry), Image.open(TEX / entry).convert("RGBA"), entry))
    columns = 4
    w, h = cells[0].size
    rows = (len(cells) + columns - 1) // columns
    out = Image.new("RGB", (columns * w, 44 + rows * (h + 8)), BACK)
    ImageDraw.Draw(out).text((8, 8), f"Forja, pase visual: lote {batch} (antes / después)", fill=INK, font=TITLE)
    for i, c in enumerate(cells):
        out.paste(c, (i % columns * w, 44 + i // columns * (h + 8)))
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    out.save(path, quality=90)
    print("wrote", path, out.size, path.stat().st_size // 1024, "KB")


def main():
    if sys.argv[1] == "--comparar":
        compare(sys.argv[2], sys.argv[3], sys.argv[4])
        return
    out = Path(sys.argv[1])
    prefix = sys.argv[2] if len(sys.argv) > 2 else "hoja"
    sheet(f"{prefix}: objetos sueltos", loose_cells(), out / f"{prefix}_objetos.jpg")
    sheet(f"{prefix}: forjados (hierro/madera, oro/hueso, diamante/obsidiana, netherita/blaze, cobre)", forged_cells(),
          out / f"{prefix}_forjados.jpg", columns=10)
    sheet(f"{prefix}: piezas en hierro o madera, moldes, plantillas, marcos", part_cells(), out / f"{prefix}_piezas.jpg")
    sheet(f"{prefix}: texturas de bloque", block_cells(), out / f"{prefix}_bloques.jpg")


if __name__ == "__main__":
    main()
