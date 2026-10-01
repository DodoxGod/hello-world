# -*- coding: utf-8 -*-
"""How the mod's monsters look: their skins, their glowmasks, a few shaped cubes and their idle and walk.

The mobs themselves (the cubes, the bone trees, the fight clips) are written by tools/generate_assets.py,
one generate_*_assets() per mob. This module leaves those definitions where they are and runs them again
with three things changed:

* a painter that gives every material its own surface (plate that reads as metal, stone laid in courses,
  slag with lava in its cracks, fire that is hot at the root and thin at the tip) instead of one flat colour
  with salt-and-pepper noise over all of them;
* a few extra cubes per mob, added to the bones that already exist, so no animation loses a bone;
* gentler idle and walk loops where the old ones were a single sway.

The fight clips are not touched: their keyframes land on the ticks the code counts (ForjaClientTest's
checkAttackTimings). The GeckoLib files are written compact, one bone or cube or channel per line.

    python tools/visual_mobs.py                 # writes the mobs' models, skins, glowmasks and animations
    python tools/visual_mobs.py hoja OUT.jpg [--antes DIR] [--mobs a,b] [--titulo T]
                                                # a contact sheet of FORJA_SOLO=visual_mobs's shots

generate_assets.py calls generate() at the end of its own run, so running that one alone gives the same files.
"""
import json
import math
import random
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
FORJA = TOOLS.parent
SHOTS = FORJA / "build" / "run" / "clientGameTest" / "screenshots"


# ---------------------------------------------------------------------------- compact GeckoLib json

def _dump(value):
    return json.dumps(value, ensure_ascii=False, separators=(", ", ": "))


def compact_geo(value):
    """A geometry file with one bone per line and, under it, one cube per line."""
    geometry = value["minecraft:geometry"][0]
    lines = ["{", '  "format_version": ' + _dump(value["format_version"]) + ",", '  "minecraft:geometry": [{',
             '    "description": ' + _dump(geometry["description"]) + ",", '    "bones": [']
    bones = geometry["bones"]
    for i, bone in enumerate(bones):
        head = {k: v for k, v in bone.items() if k != "cubes"}
        tail = "," if i + 1 < len(bones) else ""
        if not bone.get("cubes"):
            lines.append("      " + _dump(head) + tail)
            continue
        lines.append("      " + _dump(head)[:-1] + ', "cubes": [')
        cubes = bone["cubes"]
        for j, cube in enumerate(cubes):
            lines.append("        " + _dump(cube) + ("," if j + 1 < len(cubes) else ""))
        lines.append("      ]}" + tail)
    lines += ["    ]", "  }]", "}"]
    return "\n".join(lines) + "\n"


def compact_animations(value):
    """An animation file with one animation header per line and one bone's channels per line under it."""
    lines = ["{", '  "format_version": ' + _dump(value["format_version"]) + ",", '  "animations": {']
    animations = list(value["animations"].items())
    for i, (name, animation) in enumerate(animations):
        head = {k: v for k, v in animation.items() if k != "bones"}
        lines.append("    " + _dump(name) + ": " + _dump(head)[:-1] + ', "bones": {')
        bones = list(animation.get("bones", {}).items())
        for j, (bone, channels) in enumerate(bones):
            lines.append("      " + _dump(bone) + ": " + _dump(channels) + ("," if j + 1 < len(bones) else ""))
        lines.append("    }}" + ("," if i + 1 < len(animations) else ""))
    lines += ["  }", "}"]
    return "\n".join(lines) + "\n"


def write_compact(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    text = compact_geo(value) if "minecraft:geometry" in value else compact_animations(value)
    # Round trip: the compact text must be the same json, or it is not written.
    if json.loads(text) != value:
        raise ValueError(f"{path.name}: compact json does not round trip")
    # Every GeckoLib file of the mod is committed with CRLF, and the generator before this writes LF: CRLF it is.
    path.write_bytes(text.replace("\n", "\r\n").encode("utf-8"))


# ---------------------------------------------------------------------------- the painter

# What each material of the generator's palettes is made of. A mob can override any of them (MOB_STYLES)
# and any one cube (CUBE_STYLES), e.g. the greater ember's wings are flame while its body is coal.
STYLE = {
    "plate": "metal", "steel": "metal", "iron": "metal", "dark": "metal", "soot": "metal", "mask": "metal",
    "stone": "stone", "coal": "coal", "crust": "slag", "rust": "rust", "scale": "scale",
    "leather": "leather", "cloth": "cloth", "gold": "gold", "brass": "gold", "oil": "oil",
    "ember": "lava", "molten": "lava", "ember_hot": "lava",
    "flame": "flame", "white_hot": "flame",
    "star": "crystal", "shard": "crystal", "lens": "glass", "soul": "glass",
}


def _clamp(value):
    return 0 if value < 0 else 255 if value > 255 else int(round(value))


def _mul(color, k):
    return tuple(_clamp(channel * k) for channel in color[:3])


def _mix(a, b, t):
    t = 0.0 if t < 0 else 1.0 if t > 1 else t
    return tuple(_clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


class Noise:
    """Hash noise that does not depend on the order things are painted in, so one cube changing size
    does not reshuffle the grain of every cube after it (the old painter drew from one shared random)."""

    def __init__(self, seed):
        self.seed = seed & 0xFFFFFFFF

    def at(self, x, y, salt=0):
        n = (int(x) * 374761393 + int(y) * 668265263 + (self.seed + salt * 2654435761) * 1442695041) & 0xFFFFFFFF
        n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
        return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0

    def smooth(self, x, y, size, salt=0):
        gx, gy = x / size, y / size
        x0, y0 = math.floor(gx), math.floor(gy)
        fx, fy = gx - x0, gy - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a, b = self.at(x0, y0, salt), self.at(x0 + 1, y0, salt)
        c, d = self.at(x0, y0 + 1, salt), self.at(x0 + 1, y0 + 1, salt)
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy

    def cells(self, x, y, size, salt=0):
        """Distance to the nearest jittered point and how far the pixel is from the edge between two cells."""
        gx, gy = x / size, y / size
        cx, cy = math.floor(gx), math.floor(gy)
        first = second = 9.0
        owner = (0, 0)
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1):
                px = cx + ox + 0.15 + 0.7 * self.at(cx + ox, cy + oy, salt)
                py = cy + oy + 0.15 + 0.7 * self.at(cx + ox, cy + oy, salt + 7)
                dist = math.hypot(gx - px, gy - py)
                if dist < first:
                    first, second, owner = dist, first, (cx + ox, cy + oy)
                elif dist < second:
                    second = dist
        return first, (second - first) * size, owner


def _faces(u, v, w, h, d):
    """Bedrock's box unwrap: the top row holds up and down, the second the four sides."""
    return [
        ("up", u + d, v, w, d), ("down", u + d + w, v, w, d),
        ("east", u, v + d, d, h), ("north", u + d, v + d, w, h),
        ("west", u + d + w, v + d, d, h), ("south", u + 2 * d + w, v + d, w, h),
    ]


def _side_light(fy, fh):
    """Lit from above: a face is a little brighter at the top than at the bottom."""
    t = fy / max(1, fh - 1)
    return 1.08 - 0.26 * t


def _bevel(color, light, dark, fx, fy, fw, fh, strength=1.0):
    """A caught top edge, a shadowed bottom edge and darker corners, on faces big enough to carry them."""
    if fw < 3 or fh < 3:
        return color
    if fy == 0:
        return _mix(color, _mul(light, 1.06), 0.7 * strength)
    if fy == fh - 1:
        return _mix(color, dark, 0.65 * strength)
    if fx == 0 or fx == fw - 1:
        return _mul(color, 1.0 - 0.12 * strength)
    return color


def _paint_pixel(style, kind, fx, fy, fw, fh, colors, noise, ox, oy, wear):
    """One texel: (skin colour, glow colour or None). (ox, oy) is the texel on the sheet, for noise."""
    base, light, dark = colors[0], colors[1], colors[2]
    hot = colors[3] if len(colors) > 3 else (255, 132, 40)
    side = kind not in ("up", "down")
    ramp = _side_light(fy, fh) if side else (1.06 if kind == "up" else 0.72)
    grain = noise.smooth(ox, oy, 3.0)
    speck = noise.at(ox, oy, 3)

    if style == "metal":
        c = _mul(light if kind == "up" else base, ramp * (0.95 + 0.08 * noise.at(oy, 0, 11)) * (0.96 + 0.06 * grain))
        if kind == "down":
            c = _mul(dark, 0.95 + 0.08 * grain)
        if side:
            # Soot gathers low.
            c = _mul(c, 1.0 - wear * 0.22 * (fy / max(1, fh - 1)))
            # Rivets near the corners of the bigger plates, a lit head over a shadow.
            if fw >= 7 and fh >= 6 and fx in (1, fw - 2) and fy in (1, fh - 3):
                return _mul(light, 1.12), None
            if fw >= 7 and fh >= 6 and fx in (1, fw - 2) and fy in (2, fh - 2):
                return _mul(dark, 0.9), None
            if speck < 0.025 * wear:
                c = _mix(c, light, 0.55)
            c = _bevel(c, light, dark, fx, fy, fw, fh)
        elif kind == "up" and fw >= 4 and fh >= 4 and (fx in (0, fw - 1) or fy in (0, fh - 1)):
            c = _mul(light, 1.08)
        return c, None

    if style == "stone":
        course = 3 if fh < 10 else 4
        row = fy // course
        brick = 5 + (row % 2)
        col = (fx + (row % 2) * 3) // brick
        mortar = fy % course == course - 1 or (fx + (row % 2) * 3) % brick == brick - 1
        tint = 0.88 + 0.2 * noise.at(col + ox // 64 * 31, row + oy // 64 * 17, 5)
        c = _mul(light if kind == "up" else base, ramp * tint * (0.96 + 0.06 * grain))
        if mortar:
            c = _mul(dark, 0.9)
        elif fy % course == 0:
            c = _mix(c, light, 0.35)
        if speck < 0.04:
            c = _mul(c, 0.8)
        if kind == "down":
            c = _mul(dark, 0.9 + 0.1 * grain)
        return c, None

    if style == "coal":
        _, edge, owner = noise.cells(ox, oy, 2.6)
        tint = 0.7 + 0.6 * noise.at(owner[0], owner[1], 9)
        c = _mul(base, tint * ramp)
        if edge < 0.45:
            c = _mul(dark, 0.8)
        elif speck < 0.05:
            c = _mix(light, (190, 200, 220), 0.5)
        return c, None

    if style == "slag":
        c = _mix(dark, light, grain * 0.9 + 0.1 * speck)
        c = _mul(c, ramp)
        if speck < 0.07:
            c = _mul(dark, 0.6)
        # Lava showing through the cracks in the crust: the cracks are what glows at night.
        # Thin, and only in patches: a crust cracked all over reads as a lantern, not as slag.
        _, edge, _ = noise.cells(ox, oy, 3.5, 21)
        depth = fy / max(1, fh - 1) if side else 0.5
        if edge < 0.2 + 0.12 * depth and noise.smooth(ox, oy, 6.0, 22) > 0.6 - 0.12 * depth:
            glow = _mix(hot, (255, 240, 190), 0.3 if edge < 0.15 else 0.0)
            return glow, glow
        return c, None

    if style == "rust":
        c = _mix(dark, light, noise.smooth(ox, oy, 2.5, 4))
        c = _mul(c, ramp)
        if speck < 0.08:
            c = _mul(dark, 0.75)
        elif speck > 0.95:
            c = _mix(c, (196, 132, 74), 0.6)
        if side:
            c = _bevel(c, light, dark, fx, fy, fw, fh, 0.6)
        return c, None

    if style == "scale":
        band = fy % 3
        shift = (fy // 3) % 2 * 2
        c = _mul(base, ramp)
        if band == 0:
            c = _mix(c, light, 0.6)
        elif band == 2:
            c = _mix(c, dark, 0.55)
        if (fx + shift) % 4 == 3:
            c = _mix(c, dark, 0.4)
        return _mul(c, 0.94 + 0.1 * grain), None

    if style == "leather":
        c = _mul(base, ramp * (0.9 + 0.12 * noise.at(ox, 0, 13)) * (0.95 + 0.08 * grain))
        if side and fh >= 5 and fw >= 4 and fy in (1, fh - 2) and fx % 2 == 0:
            c = _mul(light, 1.12)
        if side:
            c = _bevel(c, light, dark, fx, fy, fw, fh, 0.7)
        if kind == "down":
            c = _mul(dark, 0.95)
        return c, None

    if style == "cloth":
        fold = 0.5 + 0.5 * math.sin(2 * math.pi * fx / 4.0 + noise.at(ox // 4, oy // 16, 2) * 3)
        c = _mul(base, ramp * (0.86 + 0.24 * fold) * (0.96 + 0.06 * grain))
        if side and fy == fh - 1:
            c = _mul(dark, 0.9)
        if kind == "down":
            c = _mul(dark, 0.9)
        return c, None

    if style == "gold":
        t = fy / max(1, fh - 1) if side else 0.25
        c = _mix(_mul(light, 1.1), dark, t * 0.8)
        if (fx + fy) % 7 == 0 and side:
            c = _mix(c, (255, 244, 200), 0.45)
        if side and fh >= 6 and fy == 2:
            c = _mul(dark, 0.95)
        if kind == "up":
            c = _mul(light, 1.0 + 0.08 * grain)
            if (fx + fy) % 6 == 0:
                c = _mix(c, (255, 244, 200), 0.4)
        if side:
            c = _bevel(c, light, dark, fx, fy, fw, fh, 0.6)
        return c, None

    if style == "oil":
        c = _mul(base, ramp * (0.9 + 0.2 * grain))
        if side and fy == 1 and noise.at(ox, oy, 6) < 0.6:
            c = _mix(c, (150, 170, 160), 0.35)
        return c, None

    if style == "glass":
        edge = fw >= 3 and fh >= 3 and (fx in (0, fw - 1) or fy in (0, fh - 1))
        if edge:
            return _mul(dark, 0.9), None
        t = fy / max(1, fh - 1)
        g = _mix(_mix(hot, (255, 255, 255), 0.25), hot, t * 0.6)
        g = _mul(g, 0.8 + 0.25 * grain)
        if fx == 1 and fy == 1:
            g = (255, 255, 255)
        return g, g

    if style == "crystal":
        band = ((fx + fy) // 2) % 3
        g = _mix(base, hot, 0.2 + 0.16 * band + 0.14 * grain)
        if side:
            g = _mul(g, 0.9 + 0.12 * (1 - fy / max(1, fh - 1)))
        if fw >= 3 and fh >= 3 and (fx in (0, fw - 1) or fy in (0, fh - 1)):
            g = _mix(g, hot, 0.55)
        if speck > 0.965:
            g = (255, 255, 255)
        return g, g

    if style == "lava":
        # Coal in a fire: dark crusted lumps with the heat showing between them, hotter low down.
        _, edge, owner = noise.cells(ox, oy, 2.3 if min(fw, fh) < 10 else 2.8, 31)
        depth = fy / max(1, fh - 1) if side else (0.4 if kind == "up" else 0.9)
        crust_tint = 0.75 + 0.5 * noise.at(owner[0], owner[1], 17)
        crust = _mix(_mul(base, crust_tint), hot, 0.3 + 0.3 * depth)
        width = 0.5 + 0.7 * depth
        if edge < width:
            heat = 1.0 - edge / width
            g = _mix(hot, (255, 246, 210), heat * 0.6)
            return g, g
        # The crust glows too, but dull: it should not vanish at night.
        dull = _mul(crust, 0.85 + 0.25 * grain)
        return dull, _mul(dull, 0.75)

    if style == "flame":
        # Hot and white at the root, orange through the body, red and ragged at the tip.
        if side:
            tongue = noise.at(ox, 0, 41)
            t = 1.0 - fy / max(1, fh - 1)          # 1 at the top, 0 at the root
            t = t + (tongue - 0.5) * 0.35
            if t < 0.35:
                g = _mix((255, 250, 220), _mix(hot, (255, 240, 190), 0.4), t / 0.35)
            elif t < 0.75:
                g = _mix(_mix(hot, (255, 240, 190), 0.4), hot, (t - 0.35) / 0.4)
            else:
                g = _mix(hot, _mul(base, 1.1), (t - 0.75) / 0.25)
        else:
            g = _mix(hot, (255, 250, 220), 0.5 if kind == "down" else 0.15)
        g = _mul(g, 0.94 + 0.1 * grain)
        return g, g

    return _mul(base, ramp), None


def paint_model_v2(cubes, palette, uvs, size, seed, wear=0.6, rust=None, damage=0.0, holed=(), styles=None,
                   cube_styles=None):
    """The generator's paint_model, redone: same arguments, same box unwrap, same holes, a surface per material."""
    from PIL import Image

    styles = styles or {}
    cube_styles = cube_styles or {}
    noise = Noise(seed)
    rng = random.Random(seed)
    skin = Image.new("RGBA", size, (0, 0, 0, 0))
    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    sp, gp = skin.load(), glow.load()
    for name, (_, cube, material) in cubes.items():
        w, h, d = (int(round(value)) for value in cube)
        u, v = uvs[name]
        colors = palette[material]
        style = cube_styles.get(name) or styles.get(material) or STYLE.get(material, "metal")
        for kind, x0, y0, fw, fh in _faces(u, v, w, h, d):
            for fy in range(fh):
                for fx in range(fw):
                    x, y = x0 + fx, y0 + fy
                    if not (0 <= x < size[0] and 0 <= y < size[1]):
                        continue
                    c, g = _paint_pixel(style, kind, fx, fy, fw, fh, colors, noise, x, y, wear)
                    sp[x, y] = c + (255,)
                    if g is not None:
                        gp[x, y] = g + (255,)
        metal = style in ("metal", "stone")
        # Rust bleeding a little way down from the top edge of the front and back plates.
        if rust and style == "metal":
            for face_start in (u + d, u + 2 * d + w):
                for _ in range(max(1, w // 7)):
                    column = face_start + rng.randrange(max(1, w))
                    length = rng.randrange(2, max(3, min(h, 6)))
                    for step in range(length):
                        row = v + d + step
                        if 0 <= column < size[0] and 0 <= row < size[1] and sp[column, row][3]:
                            mix = 0.42 * (1.0 - step / max(1, length))
                            sp[column, row] = _mix(sp[column, row], rust, mix) + (255,)
        # Cracks that wander down a plate, and a chipped top lip.
        if damage and metal:
            for _ in range(int(round(damage * max(1, w // 6)))):
                cx = u + d + rng.randrange(max(1, w))
                cy = v + d + rng.randrange(max(1, h))
                for _ in range(rng.randrange(3, 4 + int(4 * damage))):
                    if 0 <= cx < size[0] and 0 <= cy < size[1] and sp[cx, cy][3]:
                        sp[cx, cy] = _mul(sp[cx, cy], 0.42) + (255,)
                        # A lit lip on one side of the crack, so it reads as a crack and not a pen line.
                        if cx + 1 < size[0] and sp[cx + 1, cy][3]:
                            sp[cx + 1, cy] = _mul(sp[cx + 1, cy], 1.12) + (255,)
                    cx += rng.choice((-1, 0, 0, 1))
                    cy += rng.choice((0, 1, 1))
            for column in range(u + d, min(size[0], u + d + w)):
                if rng.random() < 0.12 * damage:
                    for row in range(v + d, min(size[1], v + d + rng.randrange(1, 3))):
                        if sp[column, row][3]:
                            sp[column, row] = _mul(colors[2], 0.55) + (255,)
        # Cloth tears at the hem.
        if damage and style in ("leather", "cloth"):
            for column in range(u + d, min(size[0], u + d + w)):
                for step in range(rng.randrange(0, 1 + int(3 * damage))):
                    row = v + d + h - 2 - step
                    if v <= row < size[1] and sp[column, row][3]:
                        sp[column, row] = (0, 0, 0, 0)
        # A hole through the plate, front and back.
        if name in holed:
            for _ in range(1 + rng.randrange(2)):
                hole_w, hole_h = rng.randrange(2, 5), rng.randrange(2, 4)
                hx = rng.randrange(max(1, w - hole_w))
                hy = rng.randrange(max(1, h - hole_h))
                for face_start in (u + d, u + 2 * d + w):
                    for ox in range(hole_w):
                        for oy in range(hole_h):
                            px, py = face_start + hx + ox, v + d + hy + oy
                            if px < min(size[0], face_start + w) and py < min(size[1], v + d + h):
                                sp[px, py] = (0, 0, 0, 0)
                                gp[px, py] = (0, 0, 0, 0)
    return skin, glow


# ---------------------------------------------------------------------------- what each mob gets

# id: (generator function, CUBES table, BONES table, PALETTE table) in generate_assets.py. The Molde Roto is
# not here: its model and clips were reworked by hand on 2026-09-28 and its generator no longer makes them.
MOBS = {
    "herrero_caido": ("generate_boss_assets", "BOSS_CUBES", "BOSS_BONES", "BOSS_PALETTE"),
    "automata_de_forja": ("generate_automaton_assets", "AUTOMATON_CUBES", "AUTOMATON_BONES", "AUTOMATON_PALETTE"),
    "coraza_vacia": ("generate_hollow_assets", "HOLLOW_CUBES", "HOLLOW_BONES", "HOLLOW_PALETTE"),
    "pavesa": ("generate_wisp_assets", "WISP_CUBES", "WISP_BONES", "WISP_PALETTE"),
    "herrumbre": ("generate_rustbug_assets", "RUSTBUG_CUBES", "RUSTBUG_BONES", "RUSTBUG_PALETTE"),
    "ascua_mayor": ("generate_greater_ember_assets", "ASCUA_CUBES", "ASCUA_BONES", "ASCUA_PALETTE"),
    "escoria_viviente": ("generate_living_slag_assets", "ESCORIA_CUBES", "ESCORIA_BONES", "ESCORIA_PALETTE"),
    "yunque_andante": ("generate_walking_anvil_assets", "ANVIL_MOB_CUBES", "ANVIL_MOB_BONES", "ANVIL_MOB_PALETTE"),
    "percutor": ("generate_striker_assets", "STRIKER_CUBES", "STRIKER_BONES", "STRIKER_MOB_PALETTE"),
    "tenaza": ("generate_tongs_assets", "TONGS_CUBES", "TONGS_BONES", "TONGS_MOB_PALETTE"),
    "cargador_de_carbon": ("generate_hauler_assets", "HAULER_CUBES", "HAULER_BONES", "HAULER_PALETTE"),
    "templador": ("generate_quencher_assets", "QUENCHER_CUBES", "QUENCHER_BONES", "QUENCHER_PALETTE"),
    "nucleo_estelar": ("generate_star_core_assets", "CORE_CUBES", "CORE_BONES", "CORE_PALETTE"),
    "guardian_de_cuno": ("generate_cune_guardian_assets", "CUNE_CUBES", "CUNE_BONES", "CUNE_PALETTE"),
}

# Colours that replace the generator's, by mob and material. Six of the monsters were painted out of the same
# three greys ("stone", "iron", "dark"), and side by side they read as one machine in six shapes. Each now has
# a material of its own: the automaton is a walking furnace of firebrick, the die guardian is blackstone and
# gold, the anvil is blued anvil steel, the striker is cast iron and brass, the hauler soot-black iron.
PALETTES = {
    "automata_de_forja": {
        "stone": ((126, 66, 50), (162, 92, 68), (70, 34, 26)),
        "iron": ((88, 90, 98), (128, 130, 140), (48, 48, 54)),
    },
    # Blackened iron round the greater ember's fire: the pale steel it had made the cage the brightest thing on it.
    "ascua_mayor": {
        "iron": ((84, 78, 78), (120, 112, 110), (44, 40, 40)),
        "dark": ((58, 54, 56), (84, 78, 80), (30, 28, 30)),
    },
    "guardian_de_cuno": {
        "stone": ((58, 54, 62), (86, 80, 92), (30, 28, 34)),
        "dark": ((40, 38, 46), (62, 58, 70), (22, 20, 26)),
    },
    "yunque_andante": {
        "iron": ((70, 74, 84), (124, 130, 144), (38, 40, 46)),
        "stone": ((92, 86, 80), (120, 112, 104), (56, 52, 48)),
        "dark": ((52, 54, 60), (76, 78, 86), (28, 28, 32)),
    },
    "percutor": {
        "iron": ((84, 82, 80), (118, 114, 110), (46, 44, 42)),
        "stone": ((150, 116, 60), (196, 160, 90), (96, 70, 32)),
        "dark": ((54, 56, 62), (80, 82, 90), (30, 30, 34)),
    },
    "tenaza": {
        "iron": ((132, 138, 150), (182, 188, 200), (78, 82, 92)),
        "dark": ((50, 52, 58), (76, 78, 86), (26, 26, 30)),
    },
    "cargador_de_carbon": {
        "dark": ((62, 58, 58), (88, 84, 82), (34, 32, 32)),
        "iron": ((100, 102, 110), (140, 142, 152), (58, 60, 66)),
    },
    "templador": {
        # His face behind the smoked glass of a quench mask: a dull amber, the only warm thing on him.
        "dark": ((70, 74, 82), (98, 102, 112), (40, 42, 48)),
    },
}

# Materials painted as something else than their name says, by mob.
MOB_STYLES = {
    "percutor": {"stone": "gold"},
    "ascua_mayor": {"molten": "lava"},
}

# Single cubes painted as something else than their material.
CUBE_STYLES = {
    # The smith's cloak hangs in folds; leather stitching on a slab that size read as a brown wall.
    "herrero_caido": {name: "cloth" for name in ("cloak_shoulder", "cloak_flank", "cloak_tail", "cloak_back")},
    # The fire licking out of the wisp and the greater ember is flame; the coal it burns on stays coal.
    "pavesa": {"flare": "flame", "trail": "flame", "wing_right_inner": "flame", "wing_left_inner": "flame",
               "wing_right_mid": "flame", "wing_left_mid": "flame"},
    "ascua_mayor": {"crown": "flame", "wing_right": "flame", "wing_left": "flame", "tail": "flame"},
    "cargador_de_carbon": {"smoke_right": "flame", "smoke_left": "flame"},
}


def _apply(ga, mob):
    """Points the generator's tables for one mob at their new versions; returns what to put back."""
    function, cubes_name, bones_name, palette_name = MOBS[mob]
    saved = {name: getattr(ga, name) for name in (cubes_name, bones_name, palette_name)}
    palette = dict(saved[palette_name])
    palette.update(PALETTES.get(mob, {}))
    setattr(ga, palette_name, palette)
    cubes = dict(saved[cubes_name])
    bones = [tuple(entry) for entry in saved[bones_name]]
    extra = EXTRA_CUBES.get(mob, {})
    for name, (bone, origin, size, material) in extra.items():
        replaced = name in cubes
        cubes[name] = (origin, size, material)
        if replaced:
            continue
        for i, entry in enumerate(bones):
            if entry[0] == bone:
                members = list(entry[3]) + [name]
                bones[i] = entry[:3] + (members,) + entry[4:]
                break
        else:
            raise ValueError(f"{mob}: no bone {bone} for {name}")
    _settle_coplanar(cubes, bones)
    setattr(ga, cubes_name, cubes)
    setattr(ga, bones_name, bones)
    return saved


SETTLE = 0.04


def _settle_coplanar(cubes, bones):
    """Two cubes of one bone with a face in the same plane fight over it pixel by pixel (the die guardian's lid
    was striped gold and stone, the hauler's back flickered between plate and coal). The cube with the bigger
    face there gives way by a twenty-fifth of a pixel, so the trim laid over a body is the one seen."""
    owner = {}
    for entry in bones:
        for member in entry[3]:
            owner[member] = entry[0]
    names = [name for name in cubes if name in owner]
    shrink = {}
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            if owner[a] != owner[b]:
                continue
            (ao, asz, _), (bo, bsz, _) = cubes[a], cubes[b]
            for axis in range(3):
                others = [k for k in range(3) if k != axis]
                overlap = 1.0
                for k in others:
                    overlap *= max(0.0, min(ao[k] + asz[k], bo[k] + bsz[k]) - max(ao[k], bo[k]))
                if overlap <= 0:
                    continue
                for high in (False, True):
                    pa = ao[axis] + (asz[axis] if high else 0)
                    pb = bo[axis] + (bsz[axis] if high else 0)
                    if abs(pa - pb) > 1e-6:
                        continue
                    area_a = asz[others[0]] * asz[others[1]]
                    area_b = bsz[others[0]] * bsz[others[1]]
                    order = (a, b) if area_a >= area_b else (b, a)
                    # Whichever gives way must keep its rounded size: the unwrap and the painter round it.
                    for loser in order:
                        span = cubes[loser][1][axis]
                        if span > 2 * SETTLE and round(span - SETTLE) == round(span):
                            shrink.setdefault(loser, set()).add((axis, high))
                            break
    for name, faces in shrink.items():
        origin, size, material = cubes[name]
        origin, size = list(origin), list(size)
        for axis, high in faces:
            if size[axis] <= 2 * SETTLE:
                continue
            if not high:
                origin[axis] = round(origin[axis] + SETTLE, 4)
            size[axis] = round(size[axis] - SETTLE, 4)
        cubes[name] = (origin, size, material)


def generate(ga):
    """Writes every mob in MOBS again through the new painter, compact, with its extra cubes and loops."""
    original_paint, original_write = ga.paint_model, ga.write_json

    def write(path, value):
        if "geckolib" in Path(path).parts:
            write_compact(path, value)
        else:
            original_write(path, value)

    try:
        ga.write_json = write
        for mob, (function, *_rest) in MOBS.items():
            styles = MOB_STYLES.get(mob, {})
            cube_styles = CUBE_STYLES.get(mob, {})
            ga.paint_model = lambda *a, _s=styles, _c=cube_styles, **k: paint_model_v2(*a, styles=_s, cube_styles=_c, **k)
            saved = _apply(ga, mob)
            try:
                getattr(ga, function)()
            finally:
                for name, value in saved.items():
                    setattr(ga, name, value)
            loops = LOOPS.get(mob)
            if loops:
                path = ga.ASSETS / f"geckolib/animations/entity/{mob}.animation.json"
                value = json.loads(path.read_text(encoding="utf-8"))
                value["animations"].update(loops())
                write_compact(path, value)
    finally:
        ga.paint_model, ga.write_json = original_paint, original_write
    print("visual_mobs:", len(MOBS), "mobs written")


# Extra cubes: {mob: {cube: (bone, origin, size, material)}}, hung on bones that already exist.
EXTRA_CUBES = {
    "guardian_de_cuno": {
        # The upper band's top sat in the die's top plane and the two fought over every pixel (a striped lid).
        # Half a pixel down, it is a band again.
        "band_high": ("head", [-10.5, 36.5, -8.5], [21, 2, 17], "gold"),
    },
}

# Replacement idle and walk loops: {mob: function returning {name: clip}}.
LOOPS = {}


# ---------------------------------------------------------------------------- contact sheets

COLUMNS = ["dia_frente", "dia_34", "dia_lado", "dia_andando", "dia_aviso", "dia_golpe", "noche_34", "noche_frente"]
CROP = (0.27, 0.12, 0.73, 0.96)


def _shots(folder):
    found = {}
    for path in sorted(Path(folder).glob("visualmob_*.png")):
        stem = path.stem[len("visualmob_"):]
        # visualmob_<mob>_<NN>_<moment>: mob names have no digits, moments can ("dia_34"), so the first
        # two-digit part is the index.
        parts = stem.split("_")
        for k in range(1, len(parts)):
            if parts[k].isdigit() and len(parts[k]) == 2:
                mob, moment = "_".join(parts[:k]), "_".join(parts[k + 1:])
                break
        else:
            continue
        if moment == "ataque":
            moment = "dia_aviso"
        found.setdefault(mob, {})[moment] = path
    return found


def contact_sheet(out, before=None, mobs=None, title=None, cell=(232, 250), quality=82):
    """One row per mob (with a row of the earlier shots above it, given --antes), a column per moment."""
    from PIL import Image, ImageDraw

    after = _shots(SHOTS)
    earlier = _shots(before) if before else {}
    order = mobs or sorted(after)
    label_w, head_h, gap = 150, 26 if not title else 50, 3
    rows = []
    for mob in order:
        if earlier:
            rows.append((mob, "antes", earlier.get(mob, {})))
        rows.append((mob, "despues" if earlier else "", after.get(mob, {})))
    width = label_w + len(COLUMNS) * (cell[0] + gap)
    height = head_h + len(rows) * (cell[1] + gap) + (gap * 4 * len(order) if earlier else 0)
    sheet = Image.new("RGB", (width, height), (22, 22, 26))
    draw = ImageDraw.Draw(sheet)
    if title:
        draw.text((8, 6), title, fill=(240, 240, 240))
    for c, column in enumerate(COLUMNS):
        draw.text((label_w + c * (cell[0] + gap) + 4, head_h - 16), column, fill=(200, 200, 200))
    y = head_h
    previous = None
    for mob, which, shots in rows:
        if earlier and previous is not None and previous != mob:
            y += gap * 4
        previous = mob
        draw.text((6, y + 6), mob, fill=(235, 235, 235))
        if which:
            draw.text((6, y + 22), which, fill=(255, 190, 90) if which == "antes" else (140, 230, 140))
        for c, column in enumerate(COLUMNS):
            x = label_w + c * (cell[0] + gap)
            path = shots.get(column)
            if path is None:
                draw.rectangle((x, y, x + cell[0] - 1, y + cell[1] - 1), fill=(44, 44, 50))
                continue
            image = Image.open(path).convert("RGB")
            w, h = image.size
            box = (int(w * CROP[0]), int(h * CROP[1]), int(w * CROP[2]), int(h * CROP[3]))
            sheet.paste(image.crop(box).resize(cell, Image.LANCZOS), (x, y))
        y += cell[1] + gap
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, quality=quality)
    while out.stat().st_size > 2_900_000 and quality > 40:
        quality -= 8
        sheet.save(out, quality=quality)
    return out


def main(argv):
    if argv and argv[0] == "hoja":
        import argparse

        parser = argparse.ArgumentParser(prog="visual_mobs.py hoja")
        parser.add_argument("out")
        parser.add_argument("--antes")
        parser.add_argument("--mobs")
        parser.add_argument("--titulo")
        args = parser.parse_args(argv[1:])
        mobs = args.mobs.split(",") if args.mobs else None
        print(contact_sheet(args.out, args.antes, mobs, args.titulo))
        return
    sys.path.insert(0, str(TOOLS))
    import generate_assets

    generate(generate_assets)


if __name__ == "__main__":
    main(sys.argv[1:])
