"""The far forges and their alloys (docs/ALEACIONES_NETHER_END.md): the soul forge of the Fragua caída in the
Nether, the void forge of the End ruin, and what only they make.

Called from generate_assets.py after the tags and the structures it adds to, with that module handed in as ``ga``
so the helpers and the paths are the same ones. The alloys' ingots and repair kits are drawn by tools/lingotes.py
with every other alloy's.
"""
import random

from PIL import Image

GA = None

# ---------------------------------------------------------------------------- colours
#
# The ingots themselves are tools/lingotes.py's, like every other alloy's (variant A, with each of these three
# alloys' own mark on its top face), and so are their repair kits (variant B): nothing to draw here.

MAGMA = (255, 138, 40)
MAGMA_HOT = (255, 214, 120)
SOUL = (95, 211, 224)
SOUL_DEEP = (34, 104, 150)
SOUL_WHITE = (214, 250, 252)


# ---------------------------------------------------------------------------- the soul forge

def soul_forge_textures():
    """The soul forge, cold and lit: the dead forge's hearth in blackstone and soul steel, with the blue fire in it."""
    rng = random.Random(20261001)
    stone = (38, 34, 42)
    rim = (92, 112, 124)
    rim_l = (128, 152, 164)
    rim_d = (52, 62, 72)
    folder = GA.ASSETS / "textures/block"
    folder.mkdir(parents=True, exist_ok=True)
    for lit in (False, True):
        side = GA.forge_grain(Image.new("RGBA", (16, 16)), stone, rng, 10)
        GA.forge_lip(side, rim_l if lit else rim, rim if lit else rim_d, rim_d)
        GA.forge_rivets(side, 4, (110, 126, 136), (22, 20, 26), step=5, start=2)
        # Soul sand packed into the joints: it is what the fire burns on.
        for x in range(16):
            if rng.random() < 0.45:
                side.putpixel((x, 9), (84, 64, 52, 255) if rng.random() < 0.6 else (62, 48, 40, 255))
        # Two runnels down the face; lit, the fire shows through them.
        for x in (3, 12):
            for y in range(5, 10):
                side.putpixel((x, y), (SOUL if lit and y % 2 else (20, 18, 24)) + (255,))
        GA.forge_mouth(side, lit, rng, heat=(70, 214, 232), top=11, bars=(5, 8, 11))
        top = GA.forge_bowl(Image.new("RGBA", (16, 16)), rim, stone, (64, 50, 42), rng,
                            melt=(72, 216, 236) if lit else None)
        if not lit:
            # Soul soil and dead coal, and one blue ember that never went out.
            for y in range(4, 12):
                for x in range(4, 12):
                    if max(abs(x - 7.5), abs(y - 7.5)) < 4 and rng.random() < 0.4:
                        top.putpixel((x, y), (GA.FORGE_COAL if rng.random() < 0.5 else (70, 54, 44)) + (255,))
            top.putpixel((8, 8), SOUL_DEEP + (255,))
            top.putpixel((7, 8), (60, 150, 170, 255))
        if lit:
            # The forge kit's white heat is a yellow white; soul fire is white with blue in it.
            for image in (side, top):
                for y in range(16):
                    for x in range(16):
                        if image.getpixel((x, y))[:3] in ((255, 240, 208), (255, 244, 214)):
                            image.putpixel((x, y), SOUL_WHITE + (255,))
        suffix = "_encendida" if lit else ""
        side.save(folder / f"fragua_de_almas{suffix}_side.png")
        top.save(folder / f"fragua_de_almas{suffix}_top.png")
    bottom = GA.forge_grain(Image.new("RGBA", (16, 16)), stone, rng, 8)
    bottom.save(folder / "fragua_de_almas_bottom.png")


def void_forge_textures():
    """The void forge, cold and lit: end stone and purpur round a hearth that burns violet, the void showing in it."""
    rng = random.Random(20261002)
    stone = (219, 222, 158)
    stone_d = (176, 174, 120)
    purpur = (169, 125, 169)
    purpur_l = (198, 156, 198)
    purpur_d = (110, 76, 110)
    folder = GA.ASSETS / "textures/block"
    for lit in (False, True):
        side = GA.forge_grain(Image.new("RGBA", (16, 16)), stone_d, rng, 8)
        # End stone brick courses.
        for y in range(16):
            for x in range(16):
                if y % 4 == 3 or (x + (4 if (y // 4) % 2 else 0)) % 8 == 7:
                    side.putpixel((x, y), (150, 148, 102, 255))
        GA.forge_lip(side, purpur_l, purpur, purpur_d)
        GA.forge_rivets(side, 4, (234, 230, 186), (120, 116, 80), step=5, start=2)
        GA.forge_mouth(side, lit, rng, heat=(196, 120, 255), top=11, bars=(5, 8, 11))
        top = GA.forge_bowl(Image.new("RGBA", (16, 16)), purpur, stone, (30, 20, 40), rng,
                            melt=(176, 110, 250) if lit else None)
        if lit:
            for image in (side, top):
                for y in range(16):
                    for x in range(16):
                        if image.getpixel((x, y))[:3] in ((255, 240, 208), (255, 244, 214)):
                            image.putpixel((x, y), (244, 232, 255, 255))
        else:
            # The void in the bowl, black with a few points of light in it.
            for (x, y) in ((6, 6), (9, 8), (7, 10), (10, 5)):
                top.putpixel((x, y), (200, 170, 255, 255))
        suffix = "_encendida" if lit else ""
        side.save(folder / f"fragua_del_vacio{suffix}_side.png")
        top.save(folder / f"fragua_del_vacio{suffix}_top.png")
    bottom = GA.forge_grain(Image.new("RGBA", (16, 16)), stone, rng, 8)
    bottom.save(folder / "fragua_del_vacio_bottom.png")


def write_void_ruin():
    """La Fragua del Vacío: a small ruin of end stone brick and broken purpur on the outer islands, with the cold void
    forge on an obsidian altar, an anvil, a chest and its guards: two empty suits and a shulker."""
    rng = random.Random(4422011)
    size = (11, 8, 11)
    blocks = {}

    def put(x, y, z, block, properties=None, nbt=None):
        blocks[(x, y, z)] = (block, properties or {}, nbt)

    floor = ["minecraft:end_stone_bricks", "minecraft:end_stone_bricks", "minecraft:end_stone", "minecraft:purpur_block"]
    for x in range(11):
        for z in range(11):
            put(x, 0, z, rng.choice(floor))
            for y in range(1, 8):
                put(x, y, z, "minecraft:air")
    # Broken purpur pillars at the corners, two still carrying a rod of light.
    for (x, z), rod in (((0, 0), True), ((10, 0), False), ((0, 10), False), ((10, 10), True)):
        height = rng.choice([3, 4, 5])
        for y in range(1, height + 1):
            put(x, y, z, "minecraft:purpur_pillar", {"axis": "y"})
        if rod:
            put(x, height + 1, z, "minecraft:end_rod", {"facing": "up"})
    # What is left of the low walls between them, open on the south side.
    for i in range(1, 10):
        for (x, z) in ((i, 0), (0, i), (10, i), (i, 10)):
            if z == 10 and 4 <= x <= 6:
                continue
            if rng.random() < 0.6:
                put(x, 1, z, "minecraft:end_stone_brick_wall")
                if rng.random() < 0.3:
                    put(x, 2, z, "minecraft:end_stone_brick_wall")
    # The altar: obsidian under the cold void forge, a rod of light on two of its corners.
    for x in range(4, 7):
        for z in range(4, 7):
            put(x, 1, z, "minecraft:obsidian" if (x + z) % 2 == 0 else "minecraft:crying_obsidian")
    put(5, 2, 5, "forja:fragua_del_vacio", {"lit": "false"})
    put(4, 2, 4, "minecraft:end_rod", {"facing": "up"})
    put(6, 2, 6, "minecraft:end_rod", {"facing": "up"})
    # The smith's anvil and the chest with what he left.
    put(3, 1, 5, "minecraft:chipped_anvil", {"facing": "south"})
    put(7, 1, 5, "minecraft:chest", {"facing": "west", "type": "single"}, {"LootTable": "forja:chests/fragua_del_vacio"})
    guards = [
        {"pos": [GA.NbtDouble(2.5), GA.NbtDouble(1.0), GA.NbtDouble(2.5)], "blockPos": [2, 1, 2],
         "nbt": {"id": "forja:coraza_vacia", "PersistenceRequired": GA.NbtByte(1)}},
        {"pos": [GA.NbtDouble(8.5), GA.NbtDouble(1.0), GA.NbtDouble(8.5)], "blockPos": [8, 1, 8],
         "nbt": {"id": "forja:coraza_vacia", "PersistenceRequired": GA.NbtByte(1)}},
        {"pos": [GA.NbtDouble(8.5), GA.NbtDouble(1.0), GA.NbtDouble(2.5)], "blockPos": [8, 1, 2],
         "nbt": {"id": "minecraft:shulker", "PersistenceRequired": GA.NbtByte(1)}},
    ]
    GA.write_structure_nbt("fragua_del_vacio/fragua", size, blocks, guards)
    GA.write_json(GA.DATA / "worldgen/template_pool/fragua_del_vacio/inicio.json", {
        "elements": [{
            "element": {"element_type": "minecraft:single_pool_element", "location": "forja:fragua_del_vacio/fragua",
                        "processors": "minecraft:empty", "projection": "rigid"},
            "weight": 1,
        }],
        "fallback": "minecraft:empty",
    })
    # The outer islands' high ground only: end_highlands is where the islands are thick, so there is always ground.
    GA.write_json(GA.DATA / "tags/worldgen/biome/has_structure/fragua_del_vacio.json",
                  {"replace": False, "values": ["minecraft:end_highlands"]})
    GA.write_json(GA.DATA / "worldgen/structure/fragua_del_vacio.json", {
        "type": "minecraft:jigsaw",
        "biomes": "#forja:has_structure/fragua_del_vacio",
        "max_distance_from_center": 80,
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"absolute": 0},
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "start_pool": "forja:fragua_del_vacio/inicio",
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "use_expansion_hack": False,
    })
    GA.write_json(GA.DATA / "worldgen/structure_set/fragua_del_vacio.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 271828180, "separation": 8, "spacing": 24},
        "structures": [{"structure": "forja:fragua_del_vacio", "weight": 1}],
    })


def void_ruin_loot():
    """The End ruin's chest: the note with the recipe, eyes to light the forge, and some of what it burns and makes."""
    loot_table("fragua_del_vacio",
               [note("nota_fragua_del_vacio", ["eterio", "fuego"]), counted("minecraft:ender_eye", 1, 1, 2)],
               (2, 4),
               [counted("minecraft:ender_pearl", 10, 2, 4),
                counted("minecraft:popped_chorus_fruit", 10, 4, 8),
                counted("minecraft:end_stone", 8, 4, 8),
                counted("minecraft:iron_ingot", 6, 1, 4),
                counted("forja:acero", 5, 1, 2),
                counted("forja:eterio", 3, 1, 2),
                counted("minecraft:shulker_shell", 1, 1, 1)])


def far_forge_block(name):
    """Models, blockstate and item definition for a far forge with its cold and lit faces."""
    for lit in (False, True):
        suffix = "_encendida" if lit else ""
        GA.write_json(GA.ASSETS / f"models/block/{name}{suffix}.json", {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {
                "top": f"forja:block/{name}{suffix}_top",
                "bottom": f"forja:block/{name}_bottom",
                "side": f"forja:block/{name}{suffix}_side",
                "particle": f"forja:block/{name}{suffix}_side",
            },
        })
    GA.write_json(GA.ASSETS / f"blockstates/{name}.json", {"variants": {
        "lit=false": {"model": f"forja:block/{name}"},
        "lit=true": {"model": f"forja:block/{name}_encendida"},
    }})
    GA.write_json(GA.ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"forja:block/{name}"}})


# ---------------------------------------------------------------------------- the magma crust

def magma_crust():
    """Costra de magma: lava gone dark on top, cracked into plates, the glow still in the cracks."""
    rng = random.Random(661)
    crust = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            g = rng.randint(-10, 10)
            crust.putpixel((x, y), (max(0, 58 + g), max(0, 40 + g), max(0, 36 + g), 255))
    # Cracks between plates, a lit line with its dark edge, the way a cooling skin breaks up.
    cracks = [((0, 5), (5, 4), (9, 7), (15, 6)), ((3, 15), (6, 10), (9, 7)), ((9, 7), (12, 12), (15, 13)),
              ((0, 11), (3, 15))]
    for line in cracks:
        for (x0, y0), (x1, y1) in zip(line, line[1:]):
            steps = max(abs(x1 - x0), abs(y1 - y0))
            for i in range(steps + 1):
                x = round(x0 + (x1 - x0) * i / steps)
                y = round(y0 + (y1 - y0) * i / steps)
                crust.putpixel((x % 16, y % 16), (MAGMA_HOT if rng.random() < 0.25 else MAGMA) + (255,))
                crust.putpixel((x % 16, (y + 1) % 16), (34, 22, 20, 255))
    folder = GA.ASSETS / "textures/block"
    crust.save(folder / "costra_de_magma.png")
    GA.write_json(GA.ASSETS / "models/block/costra_de_magma.json",
                  {"parent": "minecraft:block/cube_all", "textures": {"all": "forja:block/costra_de_magma"}})
    GA.write_json(GA.ASSETS / "blockstates/costra_de_magma.json", {"variants": {"": {"model": "forja:block/costra_de_magma"}}})


def volcanic_stone_tag():
    """The Nether's own stone, which a Volcánico pick cuts half again as fast (forge/Assembler.VOLCANIC_STONE)."""
    GA.write_json(GA.DATA / "tags/block/piedra_volcanica.json", {"replace": False, "values": [
        "minecraft:netherrack", "minecraft:basalt", "minecraft:polished_basalt", "minecraft:smooth_basalt",
        "minecraft:blackstone", "minecraft:polished_blackstone", "minecraft:polished_blackstone_bricks",
        "minecraft:cracked_polished_blackstone_bricks", "minecraft:chiseled_polished_blackstone",
        "minecraft:gilded_blackstone", "minecraft:magma_block", "minecraft:nether_bricks",
        "minecraft:cracked_nether_bricks", "minecraft:chiseled_nether_bricks", "minecraft:red_nether_bricks",
        "minecraft:crimson_nylium", "minecraft:warped_nylium",
    ]})


# ---------------------------------------------------------------------------- the blue fire's icon

def soul_flame_icon():
    """Llama fatua's effect icon: a tongue of soul fire, 18 pixels like the bleeding drop."""
    rows = [
        "........#.........",
        "........##........",
        ".......###........",
        ".......####.......",
        "......#####.......",
        "......######......",
        ".....###o###......",
        ".....##ooo###.....",
        "....###oooo##.....",
        "....##ooWoo###....",
        "....##oWWWoo##....",
        "....##oWWWWo##....",
        "....##ooWWoo##....",
        ".....##oooo##.....",
        ".....###oo###.....",
        "......######......",
        ".......####.......",
        "..................",
    ]
    icon = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "#":
                icon.putpixel((x, y), SOUL_DEEP + (255,))
            elif c == "o":
                icon.putpixel((x, y), SOUL + (255,))
            elif c == "W":
                icon.putpixel((x, y), SOUL_WHITE + (255,))
    folder = GA.ASSETS / "textures/mob_effect"
    folder.mkdir(parents=True, exist_ok=True)
    icon.save(folder / "llama_fatua.png")


# ---------------------------------------------------------------------------- loot

def counted(name, weight, low, high):
    entry = {"type": "minecraft:item", "name": name, "weight": weight}
    if (low, high) != (1, 1):
        entry["functions"] = [{"function": "minecraft:set_count",
                               "count": {"type": "minecraft:uniform", "min": low, "max": high}}]
    return entry


def note(key, lines):
    """A sheet of paper with the smith's own writing on it: a name and the recipe, as lore."""
    return {"type": "minecraft:item", "name": "minecraft:paper", "functions": [
        {"function": "minecraft:set_name", "target": "item_name", "name": {"translate": f"item.forja.{key}"}},
        {"function": "minecraft:set_lore", "mode": "replace_all",
         "lore": [{"translate": f"item.forja.{key}.{line}", "italic": False, "color": "gray"} for line in lines]},
    ]}


def loot_table(name, guaranteed, rolls, entries):
    pools = [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry]} for entry in guaranteed]
    pools.append({"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "bonus_rolls": 0.0,
                  "entries": entries})
    GA.write_json(GA.DATA / f"loot_table/chests/{name}.json", {
        "type": "minecraft:chest", "pools": pools, "random_sequence": f"forja:chests/{name}"})


def fallen_forge_loot():
    """The Fragua caída's own chest: the note that says what its forge is for, and what lights and feeds it."""
    loot_table("fragua_caida",
               [note("nota_fragua_de_almas", ["fatuo", "magmacero", "fuego"]), counted("minecraft:blaze_rod", 1, 1, 2)],
               (2, 4),
               [counted("minecraft:blaze_powder", 10, 2, 6),
                counted("minecraft:soul_soil", 10, 4, 8),
                counted("minecraft:iron_ingot", 8, 1, 4),
                counted("minecraft:magma_cream", 5, 1, 3),
                counted("minecraft:basalt", 5, 4, 8),
                counted("minecraft:blackstone", 5, 4, 8),
                counted("forja:fatuo", 3, 1, 2),
                counted("forja:magmacero", 3, 1, 2),
                counted("minecraft:netherite_scrap", 1, 1, 1)])


def generate(ga):
    global GA
    GA = ga
    soul_forge_textures()
    far_forge_block("fragua_de_almas")
    soul_flame_icon()
    magma_crust()
    volcanic_stone_tag()
    fallen_forge_loot()
    void_forge_textures()
    far_forge_block("fragua_del_vacio")
    write_void_ruin()
    void_ruin_loot()
    print("aleacion_nether: far forges written")
