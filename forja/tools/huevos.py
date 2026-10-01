# -*- coding: utf-8 -*-
"""The spawn eggs, one hand-drawn 16x16 picture per mob.

Since 26.x a vanilla spawn egg is no longer a two-tone tinted egg: each one is its own picture, an egg
with the creature painted on it (the creeper's face, the golem's nose, the vex's wings sticking out of
the shell, the spider's legs under it). These follow the same recipe:

* the shape is the vanilla egg (rows 1-14, ten to twelve pixels wide), and whatever makes the mob that
  mob is allowed to break out of it: the tongs' jaws, the ember wisp's fire wings, the anvil's horn;
* every egg is drawn as a grid of letters. A lower-case letter is a *material*: a ramp of colours taken
  from the mob's own texture, shaded here with the light from the top left like vanilla's, a little
  texel noise so it reads as a surface rather than a gradient, and the darkest step of the ramp on the
  pixels that touch the outside (darker still on the bottom right, as vanilla's rims are). An upper-case
  letter or a digit is a *flat* colour: eyes, glows and other small details that must not be shaded
  away;
* the noise is seeded by the mob's name, so the picture is the same every time it is generated.

Run on its own (`python tools/huevos.py`) it rewrites only the egg textures; generate_assets.py calls
`generate()` too.
"""
import zlib
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
FOLDER = ROOT / "src/main/resources/assets/forja/textures/item"

# The light comes from the top left, as it does on every vanilla egg.
_CX, _CY, _RX, _RY = 7.5, 7.8, 6.0, 7.0


def _noise(name, x, y):
    """A number in [-1, 1) that is always the same for this egg and this pixel."""
    return (zlib.crc32(f"{name}:{x}:{y}".encode()) % 2000) / 1000.0 - 1.0


def _darker(colour, factor):
    return tuple(int(c * factor) for c in colour[:3])


def paint(name, egg):
    rows = egg["rows"]
    ramps = egg.get("ramps", {})
    flats = egg.get("flats", {})
    grain = egg.get("grain", 0.8)
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        raise ValueError(f"{name}: the grid must be 16 rows of 16 ({[len(r) for r in rows]})")

    def solid(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and rows[y][x] != "."

    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            key = rows[y][x]
            if key == ".":
                continue
            if key in flats:
                image.putpixel((x, y), tuple(flats[key][:3]) + (255,))
                continue
            if key not in ramps:
                raise ValueError(f"{name}: no colour for {key!r} at {x},{y}")
            ramp = ramps[key]
            nx, ny = (x - _CX) / _RX, (y - _CY) / _RY
            if not all(solid(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                # The rim: the darkest step, and darker again where the light does not reach.
                colour = ramp[0] if nx + ny < 0.35 else _darker(ramp[0], 0.72)
                image.putpixel((x, y), tuple(colour[:3]) + (255,))
                continue
            light = -0.5 * nx - 0.7 * ny + 0.45 * (1.0 - min(1.0, nx * nx + ny * ny))
            level = (light + 1.0) / 2.0  # about 0..1
            steps = len(ramp) - 1  # ramp[1:] is the inside
            index = 1 + (level - 0.22) * steps * 1.1 + grain * _noise(name, x, y)
            index = max(1, min(steps, int(round(index))))
            image.putpixel((x, y), tuple(ramp[index][:3]) + (255,))
    return image


# ---------------------------------------------------------------------------------------------------
# The eggs. Each ramp runs from the rim colour to the highlight; the colours come from the mob's texture
# (textures/entity/<mob>.png and its _glowmask).

IRON_DARK = [(24, 20, 24), (39, 33, 39), (54, 50, 58), (72, 68, 78), (92, 90, 100), (116, 116, 126)]
STONE = [(52, 48, 46), (70, 66, 64), (94, 92, 92), (116, 114, 112), (150, 148, 144), (178, 177, 180)]
STEEL = [(62, 63, 67), (90, 90, 95), (121, 123, 131), (150, 152, 160), (194, 196, 204), (222, 224, 232)]

EGGS = {
    # The Fallen Smith: black iron, a leather apron, the forge still burning behind the visor, and the
    # void crystal that grew out of his shoulder.
    "herrero_caido": {
        "rows": [
            "................",
            "......iiii...Q..",
            ".....iiiiii.QP..",
            "....iiiiiiiiPR.Q",
            "...iiiiiiiiiPRQP",
            "...iiiiiiiiiiPP.",
            "..iiKKKKKKKKii..",
            "..iiKEFKKFEKii..",
            "..iiiKKKKKKiii..",
            "..iibbbbbbbbii..",
            "..iibbbbbbbbii..",
            "..iibbbbbbbbii..",
            "...ibbbbbbbbi...",
            "....ibbbbbbi....",
            ".....iiiiii.....",
            "................",
        ],
        "ramps": {
            "i": IRON_DARK,
            "b": [(46, 24, 22), (70, 38, 34), (95, 57, 59), (118, 70, 66), (140, 87, 90)],
        },
        "flats": {
            "K": (30, 24, 28), "E": (249, 152, 56), "F": (255, 225, 199),
            "P": (177, 86, 230), "Q": (238, 210, 255), "R": (95, 47, 116),
        },
    },
    # The Forge Automaton: a block of forge stone with one cyan eye and a furnace for a belly.
    "automata_de_forja": {
        "rows": [
            "................",
            "......ssss.pp...",
            ".....ssssssp....",
            "....ssssssss....",
            "...ssssssssss...",
            "...ssKCCCCKss...",
            "..ssssssssssss..",
            "..ssDDDDDDDDss..",
            "..ssDODOODODss..",
            "..ssDFDFFDFDss..",
            "..ssDODOODODss..",
            "..ssDDDDDDDDss..",
            "...ssssssssss...",
            "....ssssssss....",
            ".....ssssss.....",
            "................",
        ],
        "ramps": {
            "s": STONE,
            "p": [(40, 38, 38), (70, 65, 61), (107, 106, 106), (135, 128, 123)],
        },
        "flats": {
            "K": (46, 42, 40), "C": (140, 217, 222), "D": (46, 40, 38), "O": (241, 143, 52), "F": (255, 214, 150),
        },
    },
    # The Hollow Plate: a suit of dark plate with nobody inside, only a cold cyan light behind the visor
    # and a red tabard hanging in front.
    "coraza_vacia": {
        "rows": [
            "................",
            "......aaaa......",
            ".....aaaaaa.....",
            "....aaaaaaaa....",
            "...aaaaaaaaaa...",
            "...aKKKKKKKKa...",
            "..aaKCWKKWCKaa..",
            "..aaaaaKKaaaaa..",
            "..aaaaaKKaaaaa..",
            "..aCaaaaaaaaCa..",
            "..aaaarrrraaaa..",
            "..aaaarrrraaaa..",
            "...aaarrrraaa...",
            "....aarrrraa....",
            ".....aaaaaa.....",
            "................",
        ],
        "ramps": {
            "a": [(22, 25, 32), (40, 44, 54), (60, 64, 76), (84, 90, 102), (110, 118, 130), (146, 152, 166)],
            "r": [(40, 12, 18), (72, 20, 28), (100, 28, 36), (128, 40, 46)],
        },
        "flats": {"K": (20, 22, 28), "C": (128, 200, 205), "W": (213, 252, 254)},
    },
    # The Ember Wisp: a small black lantern with the fire inside it, and two wings of flame.
    "pavesa": {
        "rows": [
            "................",
            "......kkkk......",
            ".....kkkkkk.....",
            "....kkffffkk....",
            "...kkfggggfkk...",
            "...kkgHHHHgkk...",
            "YV.kkgHWWHgkk.VY",
            "UYVkkgHWWHgkkVYU",
            "VUUkkgHHHHgkkUUV",
            ".VUkkfggggfkkUV.",
            "..kkkffffffkkk..",
            "..kkkkkkkkkkkk..",
            "...kkkkkkkkkk...",
            "....kkkkkkkk....",
            "................",
            "................",
        ],
        "ramps": {
            "k": [(22, 20, 22), (37, 35, 37), (58, 55, 57), (82, 79, 81), (102, 98, 100)],
            "f": [(112, 48, 16), (151, 90, 30), (201, 120, 40), (230, 140, 50)],
            "g": [(201, 134, 51), (235, 150, 55), (250, 170, 65), (255, 190, 90)],
        },
        "flats": {
            "H": (252, 205, 120), "W": (255, 240, 205),
            "U": (250, 170, 65), "V": (201, 120, 40), "Y": (255, 219, 150),
        },
    },
    # The Rust Flake: a small rust beetle that eats armour, legs out to the sides and two iron jaws.
    "herrumbre": {
        "rows": [
            "................",
            "................",
            "......J..J......",
            "......JrrJ......",
            ".....rrrrrr.....",
            "....rrrrrrrr....",
            "..L.rrrdrrrr.L..",
            "...Lrrrdrrrrl...",
            "..LLrrrdrrmrLL..",
            "....rrrdrrrr....",
            "..LLrmrdrrrrLL..",
            "....rrrdrrrr....",
            "..L..rrdrrr..L..",
            "......rrrr......",
            "................",
            "................",
        ],
        "ramps": {
            "r": [(70, 40, 22), (112, 63, 30), (137, 83, 47), (170, 102, 54), (188, 124, 66)],
        },
        "flats": {"J": (121, 123, 131), "L": (58, 60, 66), "l": (58, 60, 66), "d": (90, 49, 23), "m": (121, 123, 131)},
    },
    # The Greater Ember: a grown ember wisp, so a bigger, pale steel lantern, a dark band round its
    # middle and wide wings of yellow fire.
    "ascua_mayor": {
        "rows": [
            "................",
            "......ssss......",
            ".....ssssss.....",
            "....ssggggss....",
            "Y..ssghhhhgss..Y",
            "UY.ssghWWhgss.YU",
            "UUYssghWWhgssYUU",
            "VUUssghhhhgssUUV",
            ".VddddddddddddV.",
            "..dddddddddddd..",
            "..sssghhhhgsss..",
            "..sssgghhggsss..",
            "...sssggggsss...",
            "....ssssssss....",
            ".....ssssss.....",
            "................",
        ],
        "ramps": {
            "s": STEEL,
            "d": [(30, 26, 26), (56, 47, 45), (87, 84, 83), (110, 102, 91)],
            "g": [(128, 56, 16), (200, 140, 60), (227, 174, 82), (251, 194, 91)],
            "h": [(217, 174, 100), (251, 194, 91), (255, 213, 100), (255, 223, 130)],
        },
        "flats": {"W": (255, 238, 209), "Y": (255, 223, 104), "U": (251, 194, 91), "V": (175, 135, 63)},
    },
    # The Living Slag: a heap of black slag cracked through with seams that are still molten.
    "escoria_viviente": {
        "rows": [
            "................",
            "......oooo......",
            ".....oooooo.....",
            "....xxxxxxxx....",
            "...xxxxxxxxxx...",
            "...GGxxxxxGGG...",
            "..xxxGHGGGxxxx..",
            "..xxxxxxxxxxxx..",
            "..xxxxxxxxxxxx..",
            "..GGGxxxxGGHGG..",
            "..xxGGGGGxxxxx..",
            "..xxxxxxxxxxxx..",
            "...xxxxGGGxxx...",
            "....xxxxxxxx....",
            ".....xxxxxx.....",
            "................",
        ],
        "ramps": {
            "x": [(20, 17, 15), (39, 33, 30), (51, 34, 26), (65, 48, 39), (80, 70, 64)],
            "o": [(91, 40, 12), (128, 56, 16), (170, 100, 40), (231, 177, 83), (255, 201, 94)],
        },
        "flats": {"G": (219, 150, 60), "H": (255, 225, 150)},
    },
    # The Walking Anvil: an anvil face and its horn on top, the iron waist under it, and the weld it is
    # always making throwing sparks off to the side.
    "yunque_andante": {
        "rows": [
            "................",
            "................",
            ".LLLLLLLLLLLL...",
            "aaaaaaaaaaaaa...",
            "..MaaaaaaaaM....",
            "...iiaaaaaaii...",
            "..iiiiaaaaOiiT..",
            "..iiiaaaaaaiS..T",
            "..iaaaaaaaaaai..",
            "..iMMMMMMMMMMi..",
            "..iiiiiiiiiiii..",
            "..bbiiiiiiiibb..",
            "...bbbiiiibbb...",
            "....bbbbbbbb....",
            ".....bbbbbb.....",
            "................",
        ],
        "ramps": {
            "a": STEEL,
            "i": [(18, 17, 18), (30, 28, 30), (40, 38, 40), (52, 50, 54), (64, 62, 68)],
            "b": [(46, 25, 14), (76, 42, 24), (100, 60, 32), (125, 78, 40)],
        },
        "flats": {
            "L": (217, 219, 228), "M": (124, 125, 133),
            "O": (255, 167, 61), "S": (241, 143, 52), "T": (255, 214, 130),
        },
    },
    # The Striker: a walking pile hammer, the ram's head on top, the furnace glowing in its chest.
    "percutor": {
        "rows": [
            "RRRRR...........",
            "rrrrr.ssss......",
            "rrrrrssssss.....",
            ".qq.ssssssss....",
            ".qqKOOOOOOOOK...",
            ".qqssssssssss...",
            ".qqsssssssssss..",
            ".qqssKKKKKKsss..",
            ".qqssKOFFOKsss..",
            "..sssKOOOOKsss..",
            "..sssKKKKKKsss..",
            "..ssssssssssss..",
            "...ssssssssss...",
            "....ssssssss....",
            ".....ssssss.....",
            "................",
        ],
        "ramps": {"s": STEEL},
        "flats": {
            "R": (194, 196, 204), "r": (88, 87, 91), "q": (150, 148, 144),
            "K": (54, 50, 50), "O": (253, 153, 56), "F": (255, 226, 199),
        },
    },
    # The Tongs: steel, a cyan visor and a gold belt, and the two iron jaws that shut on you.
    "tenaza": {
        "rows": [
            "................",
            "......ssss......",
            ".....ssssss.....",
            "....ssssssss....",
            "...sKKKKKKKKs...",
            "NNNsKCCKKCCKsNNN",
            "JjNssssssssssNjJ",
            "N.NssssssssssN.N",
            "..NssssssssssN..",
            "N.NGGGGGGGGGGN.N",
            "JjNssssssssssNjJ",
            "NNNssssssssssNNN",
            "...ssssssssss...",
            "....ssssssss....",
            ".....ssssss.....",
            "................",
        ],
        "ramps": {
            "s": STEEL,
            "G": [(110, 90, 40), (152, 124, 60), (191, 168, 112), (241, 201, 94)],
        },
        "flats": {"K": (40, 42, 46), "C": (145, 224, 230), "N": (36, 37, 40), "J": (70, 70, 74), "j": (110, 112, 118)},
    },
    # The Coal Hauler: nearly all fuel. Two stacks glowing on top, a heap of coal with embers in it, and
    # the two ember eyes low in its stone face.
    "cargador_de_carbon": {
        "rows": [
            "....OO....OO....",
            "....ppccccpp....",
            "....pccccccp....",
            "....cccEcccc....",
            "...cEcccccccc...",
            "...cccccccEcc...",
            "..ccEcccccccEc..",
            "..rrrrrrrrrrrr..",
            "..gggggggggggg..",
            "..gggggggggggg..",
            "..ggggOgggOggg..",
            "..gggggggggggg..",
            "...gggggggggg...",
            "....gggggggg....",
            ".....gggggg.....",
            "................",
        ],
        "ramps": {
            "p": [(40, 40, 44), (95, 94, 96), (120, 122, 130), (150, 152, 160)],
            "c": [(18, 15, 15), (30, 26, 26), (39, 34, 34), (59, 53, 53), (80, 74, 72)],
            "r": [(70, 30, 10), (100, 44, 14), (128, 56, 16), (150, 72, 24)],
            "g": [(44, 45, 50), (71, 73, 79), (95, 94, 96), (120, 122, 130), (150, 150, 154)],
        },
        "flats": {"O": (254, 201, 94), "E": (240, 120, 40)},
    },
    # The Quencher: a steel cap, a dark green oilskin hood and apron over a leather coat, and the oil
    # that puts your fire out running down its front.
    "templador": {
        "rows": [
            "................",
            "......cccc......",
            ".....cccccc.....",
            "....cccccccc....",
            "...cKKKKKKKKc...",
            "...hhhhhhhhhh...",
            "..bhhOhhhhOhhb..",
            "..bbbOggggWbbb..",
            "..bbbWggggObbb..",
            "..bbbggggggObb..",
            "..bbbgggggObbb..",
            "..bbbgggggWbbb..",
            "...bbggggggbb...",
            "....bggggggb....",
            ".....bbbbbb.....",
            "................",
        ],
        "ramps": {
            "c": [(62, 63, 67), (98, 93, 88), (124, 125, 133), (154, 155, 162), (180, 182, 190)],
            "h": [(22, 28, 24), (36, 46, 40), (48, 60, 52), (60, 76, 64), (78, 96, 80)],
            "g": [(20, 24, 21), (28, 34, 30), (42, 48, 47), (48, 58, 50), (60, 72, 62)],
            "b": [(44, 32, 24), (80, 60, 46), (104, 80, 62), (126, 98, 74), (150, 120, 84)],
        },
        "flats": {"K": (18, 20, 18), "O": (100, 128, 106), "W": (170, 204, 180)},
    },
    # The Star Core: crystal, white at the heart and deep blue at the edge, its shards turning round it.
    "nucleo_estelar": {
        "rows": [
            "...Z............",
            "..ZAZ.cccc......",
            "...Z.cccccc.....",
            "....cccccccc....",
            "...cccccccccc.B.",
            "...cccccWcccc...",
            "..ccccccWccccc..",
            "..cccccWWWcccc..",
            "..ccccWWSWWccc..",
            "..cccccWWWcccc..",
            "..ccccccWccccc..",
            "...cccccWcccc...",
            ".B.cccccccccc...",
            "....cccccccc....",
            ".....cccccc..Z..",
            ".............Z..",
        ],
        "ramps": {
            "c": [(20, 24, 48), (40, 50, 96), (58, 70, 120), (92, 110, 160), (143, 170, 210), (190, 215, 240)],
        },
        "flats": {"W": (223, 250, 255), "S": (255, 255, 255), "Z": (172, 193, 216), "A": (241, 249, 255), "B": (135, 152, 169)},
    },
    # The Broken Mould: grey stone split in two, and the bar it has not shaped yet glowing through the
    # crack and out of the top.
    "molde_roto": {
        "rows": [
            ".......Y........",
            ".......YV.......",
            "......sYVs......",
            ".....ssYVss.....",
            "...ssssYVssss...",
            "...sssGYVGsss...",
            "..sssssYVsssss..",
            "..ssssKYVKssss..",
            "..sssssKYKssss..",
            "..ssssssKYKsss..",
            "..ssssssKYKsss..",
            "..sssssKYKssss..",
            "...ssssKYKsss...",
            "....sssKKsss....",
            ".....ssssss.....",
            "................",
        ],
        "ramps": {
            "s": [(40, 40, 42), (53, 52, 54), (76, 77, 81), (108, 105, 101), (150, 148, 144), (170, 168, 164)],
        },
        "flats": {"Y": (250, 194, 91), "V": (189, 148, 73), "K": (30, 28, 30), "G": (212, 163, 76)},
    },
    # The Cune Guardian: a coining die of gold for a head, a cold blue slit for a face, stone below.
    "guardian_de_cuno": {
        "rows": [
            "................",
            "......gggg......",
            ".....gggggg.....",
            "....gggggggg....",
            "...gggggggggg...",
            "...gKBBBBBBKg...",
            "..ggKBWWWWBKgg..",
            "..gggggggggggg..",
            "..dddddddddddd..",
            "..ssssssssssss..",
            "..ssssssssssss..",
            "..ssssssssssss..",
            "...ssssssssss...",
            "....ssssssss....",
            ".....ssssss.....",
            "................",
        ],
        "ramps": {
            "g": [(90, 70, 30), (140, 110, 45), (180, 145, 60), (216, 180, 84), (232, 198, 100), (245, 218, 130)],
            "d": [(60, 50, 30), (104, 97, 81), (152, 134, 93), (190, 160, 90)],
            "s": [(46, 45, 43), (70, 68, 65), (108, 106, 103), (120, 117, 111), (150, 148, 144), (165, 164, 160)],
        },
        "flats": {"K": (60, 52, 36), "B": (124, 142, 161), "W": (201, 229, 255)},
    },
}


def generate(folder=FOLDER, names=None):
    folder.mkdir(parents=True, exist_ok=True)
    written = []
    for name, egg in EGGS.items():
        if names and name not in names:
            continue
        paint(name, egg).save(folder / f"huevo_{name}.png")
        written.append(name)
    return written


if __name__ == "__main__":
    import sys

    target = Path(sys.argv[1]) if len(sys.argv) > 1 else FOLDER
    print(generate(target))
