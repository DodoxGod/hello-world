"""Lays the mod's monsters, filmed by the client test, out on one contact sheet per mob.

Run after `FORJA_SOLO=animaciones_mobs ./gradlew runClientGameTest` (MobAnimationFilm):

    python tools/hoja_animaciones_mobs.py [--antes DIR] [--salida DIR]

Each sheet has a column per moment (standing, walking, running, warning of a blow and landing it, each
special wound up and landing, staggered, hurt, hopping back, dying) and one row of the shots just taken;
with --antes, a second row above it of the same moments from an earlier run (the shots of that run copied
somewhere first), so a change to the animations is looked at as a before and after.
"""

import argparse
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SHOTS = Path(__file__).resolve().parent.parent / "build" / "run" / "clientGameTest" / "screenshots"
NAME = re.compile(r"^mobanim_(?P<mob>.+?)_(?P<index>\d\d)_(?P<moment>.+)\.png$")
# The middle of a 960x540 shot, where the mob stands; the passes go a little wider.
CROP = (0.2, 0.06, 0.8, 0.94)
CELL_W, CELL_H = 256, 226
LABEL_W, HEADER_H, GAP = 90, 30, 4
PER_BAND = 6
BACKGROUND, INK, MISSING = (24, 24, 28), (235, 235, 235), (48, 48, 54)


def shots(folder):
    """{mob: {moment: path}} and each mob's moments in the order they were filmed."""
    found, order = {}, {}
    for path in sorted(Path(folder).glob("mobanim_*.png")):
        match = NAME.match(path.name)
        if not match:
            continue
        mob, moment = match["mob"], match["moment"]
        found.setdefault(mob, {})[moment] = path
        order.setdefault(mob, []).append((int(match["index"]), moment))
    return found, {mob: [m for _, m in sorted(pairs)] for mob, pairs in order.items()}


def font(size):
    for name in ("arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def cell(path):
    if path is None:
        return Image.new("RGB", (CELL_W, CELL_H), MISSING)
    shot = Image.open(path).convert("RGB")
    w, h = shot.size
    box = (int(w * CROP[0]), int(h * CROP[1]), int(w * CROP[2]), int(h * CROP[3]))
    return shot.crop(box).resize((CELL_W, CELL_H), Image.LANCZOS)


def sheet(mob, moments, rows):
    """Bands of up to PER_BAND moments, each band a row per run (before above after)."""
    bands = [moments[i:i + PER_BAND] for i in range(0, len(moments), PER_BAND)] or [[]]
    band_h = HEADER_H + len(rows) * (CELL_H + GAP)
    width = LABEL_W + PER_BAND * (CELL_W + GAP)
    out = Image.new("RGB", (width, HEADER_H + len(bands) * band_h), BACKGROUND)
    draw = ImageDraw.Draw(out)
    draw.text((8, 6), mob, fill=INK, font=font(18))
    small, big = font(13), font(15)
    for number, band in enumerate(bands):
        base = HEADER_H + number * band_h
        for column, moment in enumerate(band):
            draw.text((LABEL_W + column * (CELL_W + GAP) + 4, base + 8), moment.replace("_", " "), fill=INK, font=small)
        for row, (label, found) in enumerate(rows):
            top = base + HEADER_H + row * (CELL_H + GAP)
            draw.text((8, top + CELL_H // 2 - 8), label, fill=INK, font=big)
            for column, moment in enumerate(band):
                out.paste(cell(found.get(moment)), (LABEL_W + column * (CELL_W + GAP), top))
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--antes", help="a folder with the shots of an earlier run")
    parser.add_argument("--salida", default=str(SHOTS), help="where the sheets go")
    parser.add_argument("--despues", default=str(SHOTS), help="the shots of this run")
    args = parser.parse_args()
    after, after_order = shots(args.despues)
    before, before_order = shots(args.antes) if args.antes else ({}, {})
    out = Path(args.salida)
    out.mkdir(parents=True, exist_ok=True)
    for mob in sorted(set(after) | set(before)):
        moments = list(after_order.get(mob, []))
        for moment in before_order.get(mob, []):
            if moment not in moments:
                moments.append(moment)
        rows = []
        if args.antes:
            rows.append(("antes", before.get(mob, {})))
        rows.append(("después" if args.antes else "", after.get(mob, {})))
        target = out / f"hoja_{mob}.png"
        sheet(mob, moments, rows).save(target)
        print(target)


if __name__ == "__main__":
    main()
