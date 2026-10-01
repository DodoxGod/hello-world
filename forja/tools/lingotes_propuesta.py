"""A proposal, not a change: every Forja ingot redrawn as one family, in two shapes, for Andy to choose.

Run from the project root:  python tools/lingotes_propuesta.py

It reads the committed ingot textures (to show them as they are now) and writes **only** preview files
under E:/IA/Claude/Forja_capturas_mejoras/lingotes_propuesta/: a/<name>.png, b/<name>.png and the
contact sheet hoja_lingotes.jpg. No texture in the mod is touched.

What the redesign keeps from today: each metal's colour (ALLOY_COLORS, the two sideways alloys, moon
steel's travelling ramp, oricalco's ramp, the tempering bar's heat) and the idea that every alloy wears
one small mark. What it changes:

* **One silhouette for the whole family**, drawn here by hand instead of re-tinting Minecraft's iron
  ingot pixel by pixel. Re-tinting carried over the iron ingot's own noise, which is what made marks
  fight with the surface.
* **One light**, from the top left, read off **one five-tone ramp per metal**: 0 outline in shadow,
  1 front face, 2 near end, 3 top face, 4 rim. Shadows lean cool and highlights lean warm, as vanilla's
  do, so a grey metal still looks like metal and not like plastic.
* **A clean bevel**: the edge where the top meets the front is one unbroken line of tone 4, and the
  near corner has its own lit edge.

Variant A keeps vanilla's diagonal ingot (it sits beside an iron ingot in a chest without looking
foreign) and puts each alloy's mark on the lit top face, along the bar.

Variant B is Forja's own bar: a cast bar seen from above, sloping sides, the guild's cartouche
stamped into the top face and the alloy's glyph raised inside it (3 rows by 4 columns). The tempering bar's cartouche is left
empty, because it has not been assayed as anything yet.

Andy chose on 2026-10-01: A for the ingots, B for the repair kits. Both are drawn by tools/lingotes.py and
written by the generators; this sheet stays as the before-and-after.
"""

import io
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import generate_assets as gen  # noqa: E402
import dimension_assets as dim  # noqa: E402

OUT = Path("E:/IA/Claude/Forja_capturas_mejoras/lingotes_propuesta")
ITEMS = gen.ASSETS / "textures/item"

# Every ingot in the mod, in the order the foundry unlocks them, then the two that are not alloys.
INGOTS = list(gen.ALLOY_COLORS) + ["oricalco", "lingote_de_temple"]


# The drawing itself moved to tools/lingotes.py when Andy chose (2026-10-01): A is every ingot, B every
# repair kit. This file is only the sheet that compares them with what was there before.
from lingotes import variant_a, variant_b  # noqa: E402


# ---------------------------------------------------------------- the contact sheet

def vanilla_sprite(path):
    jar = zipfile.ZipFile(gen.CLIENT_JAR)
    return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + path))).convert("RGBA")


PANEL = (198, 198, 198)


def slot(item, scale=3):
    """The item in an inventory slot, at GUI scale 3, with a bit of the grey panel round it."""
    tile = Image.new("RGBA", (22, 22), PANEL + (255,))
    d = ImageDraw.Draw(tile)
    d.rectangle((2, 2, 19, 19), fill=(139, 139, 139, 255))
    d.line((2, 2, 18, 2), fill=(55, 55, 55, 255))
    d.line((2, 2, 2, 18), fill=(55, 55, 55, 255))
    d.line((3, 19, 19, 19), fill=(255, 255, 255, 255))
    d.line((19, 3, 19, 19), fill=(255, 255, 255, 255))
    tile.alpha_composite(item, (3, 3))
    return tile.resize((22 * scale, 22 * scale), Image.NEAREST)


def hotbar(items, scale=3):
    bar = vanilla_sprite("gui/sprites/hud/hotbar.png")
    for i, item in enumerate(items[:9]):
        bar.alpha_composite(item, (3 + 20 * i, 3))
    return bar.resize((bar.width * scale, bar.height * scale), Image.NEAREST)


def font(size):
    for name in ("arialbd.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def contact_sheet(rows, path):
    big = 8
    label_w, cell_gap = 170, 26
    col_w = 16 + 10 + 16 * big + 10 + 66
    width = label_w + 3 * (col_w + cell_gap) + 10
    row_h = 16 * big + 14
    header = 70
    world_bg = (122, 108, 92)
    hot_h = 22 * 3 + 12
    hot_block = 3 * (2 * hot_h + 40)
    height = header + row_h * len(rows) + hot_block + 30
    sheet = Image.new("RGB", (width, height), (34, 32, 38))
    d = ImageDraw.Draw(sheet)
    f_title, f_head, f_row = font(26), font(20), font(16)
    d.text((14, 12), "Forja: lingotes - actual | A (forma vanilla) | B (barra sellada del gremio)", fill=(240, 232, 214), font=f_title)
    titles = ("actual", "A: forma vanilla", "B: barra sellada")
    for i, t in enumerate(titles):
        d.text((label_w + i * (col_w + cell_gap), header - 26), t, fill=(250, 210, 140), font=f_head)
    for r, (name, images) in enumerate(rows):
        y = header + r * row_h
        if r % 2:
            d.rectangle((0, y, width, y + row_h - 1), fill=(42, 40, 47))
        d.text((12, y + row_h // 2 - 10), name, fill=(230, 226, 220), font=f_row)
        for c, image in enumerate(images):
            x = label_w + c * (col_w + cell_gap)
            # 1x on a mid-grey square, so it reads against something.
            d.rectangle((x - 2, y + 54, x + 17, y + 73), fill=(139, 139, 139))
            sheet.paste(image, (x, y + 56), image)
            # 8x on a dark checker, nearest neighbour.
            bx = x + 26
            checker = Image.new("RGB", (16 * big, 16 * big), (60, 58, 66))
            cd = ImageDraw.Draw(checker)
            for cy in range(16):
                for cx in range(16):
                    if (cx + cy) % 2:
                        cd.rectangle((cx * big, cy * big, cx * big + big - 1, cy * big + big - 1), fill=(70, 68, 76))
            sheet.paste(checker, (bx, y + 6))
            scaled = image.resize((16 * big, 16 * big), Image.NEAREST)
            sheet.paste(scaled, (bx, y + 6), scaled)
            s = slot(image)
            sheet.paste(s, (bx + 16 * big + 10, y + 6 + (16 * big - s.height) // 2), s)
    y = header + row_h * len(rows) + 20
    d.text((14, y), "Hotbar (escala GUI 3): toda la familia junta", fill=(240, 232, 214), font=f_head)
    y += 34
    for c, t in enumerate(titles):
        d.text((14, y), t, fill=(250, 210, 140), font=f_row)
        y += 22
        group = [images[c] for _, images in rows]
        for start in (0, 9):
            hb = hotbar(group[start:start + 9])
            # A grassy-earth backdrop, as the hotbar is seen in play.
            d.rectangle((10, y - 4, 10 + hb.width + 8, y + hb.height + 4), fill=world_bg)
            sheet.paste(hb, (14, y), hb)
            y += hot_h
        y += 18 - 22
    sheet = sheet.crop((0, 0, width, min(height, y + 20)))
    sheet.save(path, quality=93, subsampling=0, optimize=True)


def main():
    for folder in ("a", "b"):
        (OUT / folder).mkdir(parents=True, exist_ok=True)
    rows = []
    for name in INGOTS:
        current = Image.open(ITEMS / f"{name}.png").convert("RGBA")
        a, b = variant_a(name), variant_b(name)
        a.save(OUT / "a" / f"{name}.png")
        b.save(OUT / "b" / f"{name}.png")
        rows.append((name, (current, a, b)))
    sheet = OUT / "hoja_lingotes.jpg"
    contact_sheet(rows, sheet)
    print(f"{len(rows)} lingotes; hoja en {sheet} ({sheet.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
