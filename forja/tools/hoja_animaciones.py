"""Lays the weapon-blow shots of the client test out on one contact sheet.

Run after `FORJA_SOLO=animaciones ./gradlew runClientGameTest`:

    python tools/hoja_animaciones.py

Two rows per weapon, third person and first person: just held, then each of its blows wound up and
landing (up to four, in the order a flurry throws them), the combo finisher and a full charge held.
Then a row of zombies warning, striking, warning three times as long and lowering after a feint; a
row for the flail, the dagger, the gauntlets and the tome on armour stands, in zombies' hands
(standing, warning, striking) and the tome cast by the player; the gauntlets in first person, both
fists: at rest and each blow, thrown by one fist and then the other; and last the flail's chain: at
rest, trailing behind as it is carried along and swinging on after, each blow whirled, in flight,
landing on a zombie four blocks off and coming back, let out its full length with nothing to hit, and
in first person trailing as the view turns and thrown at a zombie ahead.
The sheet is written next to the shots as hoja_animaciones.png.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SHOTS = Path(__file__).resolve().parent.parent / "build" / "run" / "clientGameTest" / "screenshots"
WEAPONS = [
    "espada", "espadon", "guadana", "hacha", "picahacha", "mazo", "martillo", "mangual", "daga",
    "tridente", "lanza", "guanteletes", "baculo", "grimorio", "pico", "pala", "azada",
]
# The columns of a weapon's two rows, as the part of the shot's name after the view (3 or 1).
BLOWS = 4
COLUMNS = [("q_quieto", "quieto")]
for number in range(1, BLOWS + 1):
    COLUMNS += [(f"v{number}a", f"golpe {number} prep."), (f"v{number}b", f"golpe {number}")]
COLUMNS += [("d_remate", "remate"), ("k_cargando", "cargando")]
ZOMBIES = [("z_0_quietos", "quietos"), ("z_1_aviso", "aviso 75%"), ("z_2_golpe", "golpe"),
           ("z_3_aviso_largo", "aviso x3, 75%"), ("z_4_amago", "amago")]
EXTRAS = [("x_1_soportes", "soportes"), ("x_2_zombis_quietos", "zombis quietos"), ("x_3_zombis_aviso", "zombis avisando"),
          ("x_4_zombis_golpe", "zombis golpe"), ("x_5_grimorio_1_cerrado", "grimorio 1ª cerrado"),
          ("x_6_grimorio_1_lanzando", "grimorio 1ª lanzando"), ("x_7_grimorio_3_lanzando", "grimorio 3ª lanzando"),
          ("x_8_guanteletes_1_escudo", "guanteletes 1ª + escudo")]
# The gauntlets in first person, both fists: at rest, then each blow in turn, the fists taking turns.
FISTS = [("guanteletes_1q_quieto", "guanteletes 1ª reposo"), ("guanteletes_1v1b", "directo (derecha)"),
         ("guanteletes_1v2b", "gancho (izquierda)"), ("guanteletes_1v3b", "uppercut (derecha)"),
         ("guanteletes_1d_remate", "remate"), ("guanteletes_1k_cargando", "cargando")]
# The flail's chain: a row of its moments, and a row per blow through its stages.
CHAIN = [("m_1_quieto", "quieto"), ("m_2_arrastre", "arrastrado"), ("m_3_vaiven", "vaivén al parar"),
         ("m_6_sin_blanco", "sin blanco: 3 bloques"), ("m_7_1_arrastre", "1ª girando la vista"),
         ("m_8_1_arriba", "1ª preparación"), ("m_8_1_vuelo", "1ª en vuelo"), ("m_8_1_golpe", "1ª golpe")]
CHAIN_BLOWS = [("v1", "por arriba"), ("v2", "de lado"), ("v3", "desde abajo"), ("remate", "remate")]
CHAIN_STAGES = [("1_giro", "girando"), ("2_arriba", "preparado"), ("3_vuelo", "en vuelo"), ("4_golpe", "golpe"),
                ("5_vuelta", "vuelve")]

# Third-person shots are cropped to the two mannequins (the zombies stand wider apart); first-person
# ones keep the whole frame.
THIRD_CROP = (0.16, 0.12, 0.84, 0.92)
WIDE_CROP = (0.02, 0.1, 0.98, 0.9)
CELL_W, CELL_H = 340, 200
LABEL_W, HEADER_H, GAP = 200, 34, 4
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


def strip(sheet, draw, y, title, shots, crop_of, fonts):
    """A labelled row of shots with a heading over each; returns where the next one starts."""
    heading, label = fonts
    draw.text((10, y + 8), title, fill=INK, font=label)
    for i, (_, text) in enumerate(shots):
        draw.text((LABEL_W + i * (CELL_W + GAP) + 8, y + 8), text, fill=INK, font=heading)
    y += HEADER_H
    for i, (column, _) in enumerate(shots):
        path = SHOTS / f"anim_{column}.png"
        x = LABEL_W + i * (CELL_W + GAP)
        if path.exists():
            sheet.paste(cell(path, crop_of(column)), (x, y))
        else:
            draw.rectangle((x, y, x + CELL_W - 1, y + CELL_H - 1), fill=MISSING)
    return y + CELL_H + GAP


def exists(names):
    return any((SHOTS / f"anim_{name}.png").exists() for name in names)


def main():
    rows = [w for w in WEAPONS if exists(f"{w}_{view}{c}" for view in "31" for c, _ in COLUMNS)]
    has_zombies = exists(c for c, _ in ZOMBIES)
    has_extras = exists(c for c, _ in EXTRAS)
    has_fists = exists(c for c, _ in FISTS)
    has_chain = exists(c for c, _ in CHAIN)
    if not rows and not has_zombies and not has_extras:
        raise SystemExit(f"no anim_*.png shots in {SHOTS}")
    strips = has_zombies + has_extras + has_fists + (has_chain * (1 + len(CHAIN_BLOWS)))
    width = LABEL_W + max(len(COLUMNS), len(CHAIN)) * (CELL_W + GAP)
    height = HEADER_H + 2 * len(rows) * (CELL_H + GAP) + strips * (HEADER_H + CELL_H + GAP)
    sheet = Image.new("RGB", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    title, label = font(20), font(18)

    for i, (_, heading) in enumerate(COLUMNS):
        draw.text((LABEL_W + i * (CELL_W + GAP) + 8, 7), heading, fill=INK, font=title)

    y = HEADER_H
    for weapon in rows:
        for view, name in (("3", "3ª"), ("1", "1ª")):
            draw.text((10, y + CELL_H // 2 - 10), f"{weapon} {name}", fill=INK, font=label)
            for i, (column, _) in enumerate(COLUMNS):
                x = LABEL_W + i * (CELL_W + GAP)
                path = SHOTS / f"anim_{weapon}_{view}{column}.png"
                if path.exists():
                    sheet.paste(cell(path, THIRD_CROP if view == "3" else None), (x, y))
                else:
                    draw.rectangle((x, y, x + CELL_W - 1, y + CELL_H - 1), fill=MISSING)
            y += CELL_H + GAP

    if has_zombies:
        y = strip(sheet, draw, y, "zombis", ZOMBIES, lambda column: WIDE_CROP, (title, label))
    if has_extras:
        # First-person shots of the cast keep the whole frame; the rest are the wide scenes.
        y = strip(sheet, draw, y, "en mano", EXTRAS, lambda column: None if "_1_" in column[4:] else WIDE_CROP, (title, label))
    if has_fists:
        y = strip(sheet, draw, y, "puños", FISTS, lambda column: None, (title, label))
    if has_chain:
        first_person = lambda column: None if "_1_" in column else WIDE_CROP
        y = strip(sheet, draw, y, "cadena", CHAIN, first_person, (title, label))
        for blow, name in CHAIN_BLOWS:
            shots = [(f"m_4_{blow}_{stage}", text) for stage, text in CHAIN_STAGES]
            y = strip(sheet, draw, y, f"mangual {name}", shots, lambda column: WIDE_CROP, (title, label))
    sheet = sheet.crop((0, 0, width, y))

    out = SHOTS / "hoja_animaciones.png"
    sheet.save(out, optimize=True)
    print(f"{out} ({sheet.width}x{sheet.height}, {len(rows)} weapons)")


if __name__ == "__main__":
    main()
