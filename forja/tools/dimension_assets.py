"""The Cementerio entre Estrellas' own assets (docs/HERRERO_DIMENSION.md): its blocks, sounds and particle.

Called from generate_assets.py's main, after generate_data (which wipes the tags), the same way
clases_assets.py is. It can also be run on its own, which is what to do on Windows: it writes every file
with LF endings itself, so it never turns the repo's JSON into CRLF the way a plain write_text does there.

    python tools/dimension_assets.py

Every texture is a vanilla one recoloured, never painted from nothing:
  - ceniza            <- sand, turned to a grey ash with a little violet in it
  - ceniza_prensada   <- coarse dirt, turned to a near-black trodden ash
  - metal_fundido     <- lava_still (all its frames), turned to molten gold-white
  - metal_fundido_cae <- lava_flow, likewise, for the falls
  - arma_clavada_*    <- the iron and netherite sword, iron axe, trident, mace and iron pickaxe items,
                         rusted and burnt
"""

import io
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

WEAPONS = {
    "espada": "item/iron_sword.png",
    "espada_negra": "item/netherite_sword.png",
    "hacha": "item/iron_axe.png",
    "tridente": "item/trident.png",
    "maza": "item/mace.png",
    "pico": "item/iron_pickaxe.png",
}

# The three leans of a grave weapon: (uv of the south face, uv of the north face, rotation about z). The
# item sprites run from the handle at bottom left to the head at top right; turned half round the head is at
# the bottom, and 45 degrees then stands it straight up with its head in the ground. The third lean mirrors
# the sprite so it can lean the other way within the angles a model element is allowed.
#
# The two faces of the card need DIFFERENT uvs. A north face runs its u the other way along x from a south
# face, so the same uv on both put the back of the card along the other diagonal: seen from the north
# (looking south, Andy 2026-09-29) every weapon lay flat on its side. Each back face below is the one that
# puts the handle and the head at the same points of the card as its front.
TILTS = [
    ([16, 16, 0, 0], [0, 16, 16, 0], 45.0),
    ([16, 16, 0, 0], [0, 16, 16, 0], 22.5),
    ([0, 16, 16, 0], [16, 16, 0, 0], -22.5),
]

FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def write_json(path, value):
    """JSON the way generate_assets writes it (two spaces, a final newline), with LF for a new file and
    whatever line ending an existing file already has, so a rerun never changes a file's endings."""
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(value, indent=2, ensure_ascii=False) + "\n"
    if path.exists() and b"\r\n" in path.read_bytes():
        text = text.replace("\n", "\r\n")
    path.write_bytes(text.encode("utf-8"))


def whole(gen, path):
    """A vanilla texture with all its frames, not only the first sixteen pixels."""
    return Image.open(io.BytesIO(gen.jar_read("assets/minecraft/textures/" + path))).convert("RGBA")


def lum(pixel):
    r, g, b = pixel[:3]
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0


def ramp(stops, t):
    """A colour along a list of (position, (r, g, b)) stops."""
    t = max(0.0, min(1.0, t))
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if t <= p1:
            f = 0.0 if p1 == p0 else (t - p0) / (p1 - p0)
            return tuple(int(round(a + (b - a) * f)) for a, b in zip(c0, c1))
    return stops[-1][1]


def recolour(image, stops, lo=None, hi=None, jitter=0, seed=0):
    """Each pixel's brightness, stretched to lo..hi, read off the ramp. Alpha is kept."""
    rng = random.Random(seed)
    values = [lum(p) for p in image.getdata() if p[3] > 0]
    lo = min(values) if lo is None else lo
    hi = max(values) if hi is None else hi
    out = Image.new("RGBA", image.size)
    src = image.load()
    dst = out.load()
    for y in range(image.size[1]):
        for x in range(image.size[0]):
            p = src[x, y]
            if p[3] == 0:
                dst[x, y] = (0, 0, 0, 0)
                continue
            t = (lum(p) - lo) / max(1e-6, hi - lo)
            c = ramp(stops, t)
            if jitter:
                n = rng.randint(-jitter, jitter)
                c = tuple(max(0, min(255, v + n)) for v in c)
            dst[x, y] = (*c, p[3])
    return out


ASH = [(0.0, (58, 55, 64)), (0.45, (98, 93, 104)), (1.0, (146, 140, 150))]
TRODDEN = [(0.0, (26, 24, 30)), (0.5, (48, 45, 54)), (1.0, (78, 73, 84))]
MOLTEN = [(0.0, (150, 38, 6)), (0.35, (236, 104, 18)), (0.7, (255, 176, 56)), (1.0, (255, 244, 190))]
RUST = [(0.0, (34, 24, 22)), (0.3, (78, 46, 32)), (0.6, (124, 78, 52)), (0.85, (156, 124, 102)), (1.0, (182, 170, 160))]
CHAR = [(0.0, (20, 14, 12)), (0.5, (52, 34, 24)), (1.0, (92, 62, 40))]


def rusted(item):
    """A weapon left in the ash: steel gone to rust, grips burnt black, the odd bright edge left."""
    src = item.load()
    out = Image.new("RGBA", item.size)
    dst = out.load()
    for y in range(item.size[1]):
        for x in range(item.size[0]):
            p = src[x, y]
            if p[3] == 0:
                dst[x, y] = (0, 0, 0, 0)
                continue
            r, g, b = p[:3]
            saturation = (max(r, g, b) - min(r, g, b)) / 255.0
            t = lum(p)
            if saturation > 0.22:
                c = ramp(CHAR, t)
            else:
                c = ramp(RUST, t * 1.05)
                # Spots of deeper rust where the hash says so, never on the brightest edge pixels.
                if t < 0.85 and (x * 7 + y * 13) % 11 == 0:
                    c = tuple(int(v * 0.72) for v in c)
            dst[x, y] = (*c, p[3])
    return out


def textures(gen):
    folder = gen.ASSETS / "textures/block"
    folder.mkdir(parents=True, exist_ok=True)
    recolour(gen.vanilla("block/sand.png"), ASH, jitter=4, seed=11).save(folder / "ceniza.png")
    recolour(gen.vanilla("block/coarse_dirt.png"), TRODDEN, jitter=3, seed=12).save(folder / "ceniza_prensada.png")
    for name, source in (("metal_fundido", "block/lava_still.png"), ("metal_fundido_cae", "block/lava_flow.png")):
        image = whole(gen, source)
        recolour(image, MOLTEN).save(folder / f"{name}.png")
        meta = json.loads(gen.jar_read(f"assets/minecraft/textures/{source}.mcmeta"))
        write_json(folder / f"{name}.png.mcmeta", meta)
    for weapon, source in WEAPONS.items():
        rusted(gen.vanilla(source)).save(folder / f"arma_clavada_{weapon}.png")


def blocks(gen):
    assets = gen.ASSETS
    for name in ("ceniza", "ceniza_prensada"):
        write_json(assets / f"models/block/{name}.json",
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"forja:block/{name}"}})
        write_json(assets / f"blockstates/{name}.json", {"variants": {"": {"model": f"forja:block/{name}"}}})
        write_json(assets / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"forja:block/{name}"}})
        write_json(gen.DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "random_sequence": f"forja:blocks/{name}",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"forja:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
    # Molten metal: lit by itself (the block is emissive), the fall with the flowing sprite on its sides.
    write_json(assets / "models/block/metal_fundido.json",
               {"parent": "minecraft:block/cube_all", "textures": {"all": "forja:block/metal_fundido"}})
    write_json(assets / "models/block/metal_fundido_cae.json", {
        "parent": "minecraft:block/cube_column",
        "textures": {"end": "forja:block/metal_fundido", "side": "forja:block/metal_fundido_cae"},
    })
    write_json(assets / "blockstates/metal_fundido.json", {"variants": {
        "cae=false": {"model": "forja:block/metal_fundido"},
        "cae=true": {"model": "forja:block/metal_fundido_cae"},
    }})
    # The graves: one model per weapon and lean, turned by the blockstate for its four facings.
    variants = {}
    for weapon in WEAPONS:
        for tilt, (uv, back, angle) in enumerate(TILTS):
            model = f"arma_clavada_{weapon}_{tilt}"
            texture = f"forja:block/arma_clavada_{weapon}"
            face = {"uv": uv, "texture": "#arma"}
            write_json(assets / f"models/block/{model}.json", {
                "ambientocclusion": False,
                "textures": {"arma": texture, "particle": texture},
                "elements": [{
                    "from": [0, -2, 8],
                    "to": [16, 14, 8],
                    "rotation": {"origin": [8, 6, 8], "axis": "z", "angle": angle},
                    "shade": False,
                    "faces": {"north": {"uv": back, "texture": "#arma"}, "south": dict(face)},
                }],
            })
            for facing, y in FACING_Y.items():
                entry = {"model": f"forja:block/{model}"}
                if y:
                    entry["y"] = y
                variants[f"arma={weapon},facing={facing},inclinacion={tilt}"] = entry
    write_json(assets / "blockstates/arma_clavada.json", {"variants": variants})


def particle(gen):
    # The rising ember wears the spark's sprites: the same metal, only going up instead of down.
    write_json(gen.ASSETS / "particles/brasa.json", {"textures": ["forja:chispa_0", "forja:chispa_1", "forja:chispa_2"]})


def sounds(gen):
    """Vanilla sounds, pitched down and put together: the dimension's ambience and its music."""
    def s(name, pitch, volume=1.0, stream=False, weight=1):
        entry = {"name": name, "pitch": pitch, "volume": volume}
        if stream:
            entry["stream"] = True
        if weight != 1:
            entry["weight"] = weight
        return entry

    table = {
        "ambient.cementerio.loop": {"sounds": [s("minecraft:ambient/nether/soulsand_valley/ambience", 0.72, 0.7, True)]},
        "ambient.cementerio.mood": {"sounds": [s(f"minecraft:ambient/nether/soulsand_valley/mood{i}", 0.7, 0.8) for i in range(1, 5)]},
        "ambient.cementerio.additions": {
            "subtitle": "subtitles.forja.cementerio",
            "sounds": [
                s("minecraft:random/anvil_land", 0.55, 0.12, weight=2),
                s("minecraft:random/anvil_use", 0.6, 0.1, weight=2),
                s("minecraft:block/bell/resonate", 0.5, 0.25),
                *[s(f"minecraft:block/amethyst/resonate{i}", 0.6, 0.5) for i in range(1, 5)],
                *[s(f"minecraft:ambient/nether/basalt_deltas/debris{i}", 0.7, 0.4) for i in range(1, 4)],
            ],
        },
        "music.cementerio": {"sounds": [
            s("minecraft:music/game/end/the_end", 0.85, 0.7, True),
            s("minecraft:music/game/nether/soulsand_valley/so_below", 0.9, 0.7, True),
            s("minecraft:music/game/echo_in_the_wind", 0.85, 0.7, True),
            s("minecraft:music/game/deeper", 0.85, 0.7, True),
        ]},
    }
    write_json(gen.ASSETS / "sounds.json", table)


def tags(gen):
    tag = lambda values: {"replace": False, "values": values}
    write_json(gen.RES / "data/minecraft/tags/block/mineable/shovel.json", tag(["forja:ceniza", "forja:ceniza_prensada"]))
    # generate_data writes this same file from the same list; written here too so a run of this module
    # alone leaves the tag in step with the list.
    write_json(gen.RES / "data/minecraft/tags/block/mineable/pickaxe.json", tag(gen.PICKAXE_BLOCKS))


ORICALCO = [(0.0, (46, 52, 18)), (0.35, (112, 128, 34)), (0.7, (196, 204, 76)), (1.0, (246, 244, 176))]
STONE_FRAME = [(0.0, (16, 13, 20)), (0.5, (40, 34, 46)), (1.0, (84, 74, 90))]
STARLIGHT = [(0.0, (24, 8, 40)), (0.45, (92, 34, 140)), (0.8, (230, 150, 70)), (1.0, (255, 236, 170))]


def portal(gen):
    """The oricalco bar and pearl, the star portal's bracket and the portal itself (docs/HERRERO_DIMENSION.md, 1)."""
    items = gen.ASSETS / "textures/item"
    blocks = gen.ASSETS / "textures/block"
    items.mkdir(parents=True, exist_ok=True)
    recolour(gen.vanilla("item/gold_ingot.png"), ORICALCO).save(items / "oricalco.png")
    pearl = recolour(gen.vanilla("item/ender_pearl.png"), ORICALCO)
    # A star caught in it: one white-gold pixel and its four neighbours, where the pearl's own glint is.
    glint = ((6, 5, (255, 252, 214)), (5, 5, (236, 226, 150)), (7, 5, (236, 226, 150)), (6, 4, (236, 226, 150)), (6, 6, (236, 226, 150)))
    for x, y, c in glint:
        if pearl.getpixel((x, y))[3] > 0:
            pearl.putpixel((x, y), (c[0], c[1], c[2], 255))
    pearl.save(items / "perla_de_oricalco.png")
    for name in ("oricalco", "perla_de_oricalco"):
        write_json(gen.ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"forja:item/{name}"}})
        write_json(gen.ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"forja:item/{name}"}})
    # The bracket: the end portal frame's shape, in the blackstone of the castle's deep forge, with the pearl for an eye.
    recolour(gen.vanilla("block/end_portal_frame_top.png"), STONE_FRAME).save(blocks / "mensula_estelar_top.png")
    recolour(gen.vanilla("block/end_portal_frame_side.png"), STONE_FRAME).save(blocks / "mensula_estelar_side.png")
    recolour(gen.vanilla("block/end_portal_frame_eye.png"), ORICALCO).save(blocks / "mensula_estelar_perla.png")
    textures = {"particle": "forja:block/mensula_estelar_side", "bottom": "minecraft:block/polished_blackstone",
                "top": "forja:block/mensula_estelar_top", "side": "forja:block/mensula_estelar_side"}
    write_json(gen.ASSETS / "models/block/mensula_estelar.json", {"parent": "minecraft:block/end_portal_frame", "textures": textures})
    filled = dict(textures)
    filled["eye"] = "forja:block/mensula_estelar_perla"
    write_json(gen.ASSETS / "models/block/mensula_estelar_perla.json", {"parent": "minecraft:block/end_portal_frame_filled", "textures": filled})
    turn = {"south": 0, "west": 90, "north": 180, "east": 270}
    variants = {}
    for pearl_set in (False, True):
        for facing, y in turn.items():
            entry = {"model": "forja:block/mensula_estelar" + ("_perla" if pearl_set else "")}
            if y:
                entry["y"] = y
            variants[f"facing={facing},perla={'true' if pearl_set else 'false'}"] = entry
    write_json(gen.ASSETS / "blockstates/mensula_estelar.json", {"variants": variants})
    write_json(gen.ASSETS / "items/mensula_estelar.json", {"model": {"type": "minecraft:model", "model": "forja:block/mensula_estelar"}})
    # The portal: the nether portal's swirl, every frame of it, turned to a violet night with gold in it.
    recolour(whole(gen, "block/nether_portal.png"), STARLIGHT).save(blocks / "portal_estelar.png")
    write_json(blocks / "portal_estelar.png.mcmeta", json.loads(gen.jar_read("assets/minecraft/textures/block/nether_portal.png.mcmeta")))
    face = {"uv": [0, 0, 16, 16], "texture": "#portal"}
    write_json(gen.ASSETS / "models/block/portal_estelar.json", {
        "ambientocclusion": False,
        "textures": {"portal": "forja:block/portal_estelar", "particle": "forja:block/portal_estelar"},
        "elements": [{"from": [0, 12, 0], "to": [16, 12, 16], "shade": False,
                      "faces": {"up": dict(face), "down": dict(face)}}],
    })
    write_json(gen.ASSETS / "blockstates/portal_estelar.json", {"variants": {"": {"model": "forja:block/portal_estelar"}}})


def generate(gen):
    textures(gen)
    blocks(gen)
    particle(gen)
    sounds(gen)
    portal(gen)
    tags(gen)


if __name__ == "__main__":
    here = Path(__file__).resolve().parent
    sys.path.insert(0, str(here))
    import generate_assets
    generate(generate_assets)
    print("dimension assets written")
