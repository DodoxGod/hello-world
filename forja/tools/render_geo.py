# -*- coding: utf-8 -*-
"""Draws a GeckoLib entity model without opening Minecraft.

The item renderer next door only knows axis-aligned boxes in a sixteen-unit frame, which is all an
item model ever is. An entity model is a tree: bones hang off bones, each turns about its own pivot,
and the cubes come along for the ride. Designing eleven mobs by launching a thirteen-minute game test
to look at each one is not designing, it is waiting, so this exists to make looking cheap.

Every face is cut into one quad per texel, every texel is put through the bone chain and the camera,
and the whole lot is sorted back to front in one pass. Slow and obvious beats fast and wrong: there is
no z-buffer to get subtly out of step with itself, and an overhang's underside is drawn because it is
genuinely behind the thing above it rather than because a rule said so.
"""
import json
import math
from pathlib import Path

from PIL import Image

# Bedrock's box unwrap, which is what a `uv: [u, v]` on a cube means. Laid out as
#
#       +------+------+
#       |  up  | down |
#   +---+------+------+---+
#   | E | north|  W   | S |
#   +---+------+------+---+
#
# where north is the face at -Z, the one a mob looks out of.
def _box_uv(u, v, sx, sy, sz):
    return {
        "up": (u + sz, v, sx, sz),
        "down": (u + sz + sx, v, sx, sz),
        "east": (u, v + sz, sz, sy),
        "north": (u + sz, v + sz, sx, sy),
        "west": (u + sz + sx, v + sz, sz, sy),
        "south": (u + sz * 2 + sx, v + sz, sx, sy),
    }


def _rotate(point, pivot, rotation):
    """Bedrock bone rotation: Z, then Y, then X, all about the bone's own pivot."""
    x, y, z = point[0] - pivot[0], point[1] - pivot[1], point[2] - pivot[2]
    rx, ry, rz = (math.radians(a) for a in rotation)
    # X
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    # Y
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    # Z
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return (x + pivot[0], y + pivot[1], z + pivot[2])


def _chain(bone, bones):
    """Every rotation this bone inherits, nearest ancestor last."""
    steps = []
    walk = bone
    while walk is not None:
        rotation = walk.get("rotation")
        if rotation and any(rotation):
            steps.append((tuple(walk.get("pivot", (0, 0, 0))), tuple(rotation)))
        walk = bones.get(walk.get("parent"))
    return steps


def _face_corners(origin, size, inflate, face):
    """The four corners of one face of a cube, in model space, anticlockwise from its texture origin."""
    x0, y0, z0 = (origin[i] - inflate for i in range(3))
    x1, y1, z1 = (origin[i] + size[i] + inflate for i in range(3))
    return {
        # (top-left, top-right, bottom-left) of the face as the texture sees it
        "north": ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0)),
        "south": ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1)),
        "east": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0)),
        "west": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1)),
        "up": ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1)),
        "down": ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0)),
    }[face]


# How much each direction is dimmed, copying Minecraft's own flat shading so a preview reads the way
# the game will. Without it a box is one flat colour and every shape looks like a slab.
_SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def render(geo_path, texture_path, scale=6, yaw=35.0, pitch=28.0, pad=8, shade=True, bones=None):
    """One picture of one model. `yaw` turns it, `pitch` looks down on it."""
    geo = json.loads(Path(geo_path).read_text(encoding="utf-8"))["minecraft:geometry"][0]
    sheet = Image.open(texture_path).convert("RGBA")
    by_name = {b["name"]: b for b in geo["bones"]}

    ry, rp = math.radians(yaw), math.radians(pitch)
    right = (math.cos(ry), 0.0, -math.sin(ry))
    upward = (math.sin(ry) * math.sin(rp), math.cos(rp), math.cos(ry) * math.sin(rp))
    into = (math.sin(ry) * math.cos(rp), -math.sin(rp), math.cos(ry) * math.cos(rp))

    def camera(point):
        sx = sum(point[i] * right[i] for i in range(3))
        sy = -sum(point[i] * upward[i] for i in range(3))
        depth = sum(point[i] * into[i] for i in range(3))
        return sx, sy, depth

    painted = []
    for bone in geo["bones"]:
        if bones and bone["name"] not in bones:
            continue
        steps = _chain(bone, by_name)

        def place(point):
            for pivot, rotation in steps:
                point = _rotate(point, pivot, rotation)
            return point

        for cube in bone.get("cubes", []):
            origin, size = cube["origin"], cube["size"]
            inflate = cube.get("inflate", 0.0)
            u, v = cube.get("uv", (0, 0))
            regions = _box_uv(u, v, size[0], size[1], size[2])
            for face, (u0, v0, tw, th) in regions.items():
                if tw <= 0 or th <= 0:
                    continue
                topleft, topright, bottomleft = _face_corners(origin, size, inflate, face)
                steps_u, steps_v = max(1, int(round(tw))), max(1, int(round(th)))
                for iv in range(steps_v):
                    for iu in range(steps_u):
                        px, py = int(u0) + iu, int(v0) + iv
                        if not (0 <= px < sheet.width and 0 <= py < sheet.height):
                            continue
                        colour = sheet.getpixel((px, py))
                        if colour[3] == 0:
                            continue
                        fu, fv = (iu + 0.5) / steps_u, (iv + 0.5) / steps_v
                        point = tuple(
                            topleft[i] + (topright[i] - topleft[i]) * fu + (bottomleft[i] - topleft[i]) * fv
                            for i in range(3)
                        )
                        sx, sy, depth = camera(place(point))
                        if shade:
                            dim = _SHADE[face]
                            colour = (int(colour[0] * dim), int(colour[1] * dim), int(colour[2] * dim), colour[3])
                        painted.append((depth, sx, sy, colour))

    if not painted:
        return Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    xs = [p[1] for p in painted]
    ys = [p[2] for p in painted]
    # int() the lot: a fractional scale is useful (a four-block mob and a third-of-a-block one cannot
    # share one) but Pillow will not size an image in halves.
    width = int((max(xs) - min(xs)) * scale + pad * 2 + scale)
    height = int((max(ys) - min(ys)) * scale + pad * 2 + scale)
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ox, oy = pad - min(xs) * scale, pad - min(ys) * scale

    # Back to front, in one go.
    painted.sort(key=lambda item: -item[0])
    cell = max(1, int(round(scale)))
    for _, sx, sy, colour in painted:
        x, y = int(ox + sx * scale), int(oy + sy * scale)
        for dy in range(cell):
            for dx in range(cell):
                if 0 <= x + dx < width and 0 <= y + dy < height:
                    canvas.putpixel((x + dx, y + dy), colour)
    return canvas


def turnaround(geo_path, texture_path, scale=6, angles=(30, 120, 210, 300), pitch=25.0, gap=10,
               background=(30, 28, 32, 255)):
    """The same model from four sides, which is the only way to see whether a shape works."""
    shots = [render(geo_path, texture_path, scale=scale, yaw=a, pitch=pitch) for a in angles]
    height = max(s.height for s in shots)
    width = sum(s.width + gap for s in shots) + gap
    strip = Image.new("RGBA", (width, height + gap * 2), background)
    x = gap
    for shot in shots:
        strip.alpha_composite(shot, (x, gap + height - shot.height))
        x += shot.width + gap
    return strip
