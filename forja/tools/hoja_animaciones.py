"""Lays the weapon-blow shots of the client test out on one contact sheet.

Run after `FORJA_SOLO=animaciones ./gradlew runClientGameTest`:

    python tools/hoja_animaciones.py

One row per weapon: in third person the blow wound up, landing, the second blow of a pair (dagger,
gauntlets) and the combo finisher; in first person wound up, landing and the second blow. A last row
has the zombies warning, striking, warning three times as long and lowering after a feint. The sheet
is written next to the shots as hoja_animaciones.png.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SHOTS = Path(__file__).resolve().parent.parent / "build" / "run" / "clientGameTest" / "screenshots"
WEAPONS = [
    "espada", "espadon", "guadana", "hacha", "picahacha", "mazo", "martillo", "mangual", "daga",
    "tridente", "lanza", "guanteletes", "baculo", "grimorio", "pico", "pala", "azada",
]
COLUMNS = [
    ("3a_carga", "3ª carga"), ("3b_golpe", "3ª golpe"), ("3c_segundo", "3ª segundo"), ("3d_remate", "3ª remate"),
    ("1a_carga", "1ª carga"), ("1b_golpe", "1ª golpe"), ("1c_segundo", "1ª segundo"),
]
ZOMBIES = [("z_0_quietos", "quietos"), ("z_1_aviso", "aviso 75%"), ("z_2_golpe", "golpe"),
           ("z_3_aviso_largo", "aviso x3, 75%"), ("z_4_amago", "amago")]

# Third-person shots are cropped to the two mannequins (the zombies stand wider apart); first-person
# ones keep the whole frame.
THIRD_CROP = (0.16, 0.12, 0.84, 0.92)
ZOMBIE_CROP = (0.02, 0.1, 0.98, 0.9)
CELL_W, CELL_H = 340, 200
LABEL_W, HEADER_H, GAP = 130, 34, 4
BACKGROUND, INK, MISSING = (24, 24, 28), (235, 235, 235), (48, 48, 54)


def font(size):
    for name in ("arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def cell(path, crop):
    image = Image.open(path).convert("RGB")
    if crop:
        w, h = image.size
        image = image.crop((int(crop[0] * w), int(crop[1] * h), int(crop[2] * w), int(crop[3] * h)))
    image.thumbnail((CELL_W, CELL_H), Image.LANCZOS)
    framed = Image.new("RGB", (CELL_W, CELL_H), BACKGROUND)
    framed.paste(image, ((CELL_W - image.width) // 2, (CELL_H - image.height) // 2))
    return framed


def main():
    rows = [w for w in WEAPONS if any((SHOTS / f"anim_{w}_{c}.png").exists() for c, _ in COLUMNS)]
    has_zombies = any((SHOTS / f"anim_{c}.png").exists() for c, _ in ZOMBIES)
    count = len(rows) + (2 if has_zombies else 0)
    if count == 0:
        raise SystemExit(f"no anim_*.png shots in {SHOTS}")
    width = LABEL_W + len(COLUMNS) * (CELL_W + GAP)
    height = HEADER_H + count * (CELL_H + GAP)
    sheet = Image.new("RGB", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    title, label = font(20), font(18)

    for i, (_, heading) in enumerate(COLUMNS):
        draw.text((LABEL_W + i * (CELL_W + GAP) + 8, 7), heading, fill=INK, font=title)

    y = HEADER_H
    for weapon in rows:
        draw.text((10, y + CELL_H // 2 - 10), weapon, fill=INK, font=label)
        for i, (column, _) in enumerate(COLUMNS):
            x = LABEL_W + i * (CELL_W + GAP)
            path = SHOTS / f"anim_{weapon}_{column}.png"
            if path.exists():
                sheet.paste(cell(path, THIRD_CROP if column.startswith("3") else None), (x, y))
            else:
                draw.rectangle((x, y, x + CELL_W - 1, y + CELL_H - 1), fill=MISSING)
        y += CELL_H + GAP

    if has_zombies:
        draw.text((10, y + 8), "zombis", fill=INK, font=label)
        for i, (_, heading) in enumerate(ZOMBIES):
            draw.text((LABEL_W + i * (CELL_W + GAP) + 8, y + 8), heading, fill=INK, font=title)
        y += HEADER_H
        for i, (column, _) in enumerate(ZOMBIES):
            path = SHOTS / f"anim_{column}.png"
            if path.exists():
                sheet.paste(cell(path, ZOMBIE_CROP), (LABEL_W + i * (CELL_W + GAP), y))
        sheet = sheet.crop((0, 0, width, y + CELL_H))

    out = SHOTS / "hoja_animaciones.png"
    sheet.save(out, optimize=True)
    print(f"{out} ({sheet.width}x{sheet.height}, {len(rows)} weapons)")


if __name__ == "__main__":
    main()
