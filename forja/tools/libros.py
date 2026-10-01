"""The guide's books (docs/LIBROS_GUIA.md): item textures, item models and recipes.

Run on its own from the project root (python tools/libros.py) to write only these files; generate_assets.py also
calls generate() so a full run writes the same thing. The recipes of the books after the notebook are learned
through the player's advancements (GuideBooks.java), so on purpose there is no recipe advancement for them here:
vanilla's "you are holding a book" one would teach them to everybody on day one.
"""

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/forja"
DATA = ROOT / "src/main/resources/data/forja"

# item id: (cover colour, stamp pixels and their colours, ingredient that goes in the grid with a book or None)
IRON = (212, 212, 220)
IRON_DARK = (150, 150, 158)
GOLD = (236, 196, 84)
WOOD = (120, 84, 48)
BOOKS = {
    # The notebook keeps the old guide's hammer, on a light leather cover.
    "guia_de_forja": ((0xC9, 0xA4, 0x65), [((7, 4), IRON_DARK), ((8, 4), IRON_DARK), ((9, 4), (96, 96, 104)), ((8, 5), WOOD),
                                          ((7, 6), WOOD), ((6, 7), (82, 54, 30))], "minecraft:iron_ingot"),
    # Book I: an anvil on orange, dark so it reads on the leather.
    "libro_yunque": ((0xC8, 0x64, 0x1E), [((6, 4), (96, 96, 104)), ((7, 4), (110, 110, 118)), ((8, 4), (110, 110, 118)),
                                         ((9, 4), (96, 96, 104)), ((7, 5), (64, 64, 72)), ((8, 5), (64, 64, 72)),
                                         ((6, 6), (80, 80, 88)), ((7, 6), (96, 96, 104)), ((8, 6), (96, 96, 104)), ((9, 6), (80, 80, 88))],
                     "forja:plantilla"),
    # Book II: a sword on red.
    "libro_combate": ((0xA8, 0x32, 0x2C), [((9, 3), (200, 200, 208)), ((8, 4), (200, 200, 208)), ((7, 5), (170, 170, 178)),
                                          ((6, 6), (120, 84, 48)), ((5, 7), (82, 54, 30)), ((6, 5), (150, 120, 60)), ((8, 7), (150, 120, 60))],
                      "minecraft:bone"),
    # Book III: a crucible glowing on copper-brown.
    "libro_fundicion": ((0x7A, 0x4A, 0x2A), [((6, 4), (70, 70, 78)), ((9, 4), (70, 70, 78)), ((6, 5), (90, 90, 98)), ((7, 5), (255, 140, 40)),
                                            ((8, 5), (255, 180, 60)), ((9, 5), (90, 90, 98)), ((7, 6), (70, 70, 78)), ((8, 6), (70, 70, 78))],
                        "minecraft:copper_ingot"),
    # Book IV: a gold-rimmed table on violet.
    "libro_mesa_mayor": ((0x78, 0x50, 0xBE), [((6, 4), (236, 196, 84)), ((7, 4), (236, 196, 84)), ((8, 4), (236, 196, 84)),
                                             ((9, 4), (236, 196, 84)), ((6, 5), (60, 60, 68)), ((9, 5), (60, 60, 68)),
                                             ((7, 5), (90, 90, 98)), ((8, 5), (90, 90, 98))],
                         "forja:damasco"),
    # Book V: a gold medallion on green.
    "libro_clases": ((0x3C, 0x7A, 0x4A), [((7, 3), (200, 200, 208)), ((8, 3), (200, 200, 208)), ((7, 4), (236, 196, 84)),
                                         ((8, 4), (236, 196, 84)), ((7, 5), (236, 196, 84)), ((8, 5), (200, 150, 50)),
                                         ((6, 5), (160, 110, 40)), ((9, 4), (160, 110, 40))],
                     "minecraft:emerald"),
    # Book VI: a tower on teal.
    "libro_bastion": ((0x28, 0x82, 0x7F), [((7, 3), (120, 120, 128)), ((8, 3), (120, 120, 128)), ((7, 4), (90, 90, 98)),
                                          ((8, 4), (90, 90, 98)), ((7, 5), (90, 90, 98)), ((8, 5), (90, 90, 98)),
                                          ((6, 6), (70, 70, 78)), ((9, 6), (70, 70, 78)), ((7, 6), (255, 160, 60))],
                      "minecraft:map"),
    # Book VII: a gold star on the night.
    "libro_cementerio": ((0x34, 0x26, 0x58), [((8, 3), (236, 196, 84)), ((7, 4), (236, 196, 84)), ((8, 4), (255, 240, 170)),
                                             ((9, 4), (236, 196, 84)), ((8, 5), (236, 196, 84)), ((5, 5), (200, 200, 230)),
                                             ((11, 4), (200, 200, 230))],
                         "forja:hierro_estelar"),
    # The creative tome: dark red with a gold star, and no recipe.
    "tomo_de_forja": ((0x7A, 0x2A, 0x20), [((8, 4), GOLD), ((7, 5), GOLD), ((8, 5), GOLD), ((9, 5), GOLD), ((8, 6), GOLD),
                                          ((6, 5), (200, 160, 60)), ((10, 5), (200, 160, 60))], None),
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def cover(book, colour, stamp):
    """Vanilla's book with its red leather dyed the book's colour, keeping the leather's shading, and a stamp."""
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            r, g, b, a = book.getpixel((x, y))
            if not a:
                continue
            if r > g + 15:
                shade = (r * 299 + g * 587 + b * 114) / 1000 / 110.0
                out.putpixel((x, y), tuple(max(0, min(255, round(c * shade))) for c in colour) + (255,))
            else:
                out.putpixel((x, y), (r, g, b, a))
    for pos, pixel in stamp:
        out.putpixel(pos, pixel + (255,))
    return out


def generate(vanilla_book):
    """Every book's texture, model and item definition, and the recipes of the books that have one."""
    for item, (colour, stamp, ingredient) in BOOKS.items():
        path = ASSETS / f"textures/item/{item}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        cover(vanilla_book, colour, stamp).save(path)
        write_json(ASSETS / f"models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"forja:item/{item}"}})
        write_json(ASSETS / f"items/{item}.json", {"model": {"type": "minecraft:model", "model": f"forja:item/{item}"}})
        if ingredient is not None:
            write_json(DATA / f"recipe/{item}.json", {
                "type": "minecraft:crafting_shapeless",
                "category": "misc",
                "ingredients": ["minecraft:book", ingredient],
                "result": {"id": f"forja:{item}"},
            })
    return list(BOOKS)



# The shelf's eight places, in GuideBooks.SHELF's order: each book's spine colour.
SHELF = ["guia_de_forja", "libro_yunque", "libro_combate", "libro_fundicion", "libro_mesa_mayor", "libro_clases", "libro_bastion",
         "libro_cementerio"]
SPINES = [(0xC9, 0xA4, 0x65), (0xC8, 0x64, 0x1E), (0xA8, 0x32, 0x2C), (0x7A, 0x4A, 0x2A), (0x78, 0x50, 0xBE), (0x3C, 0x7A, 0x4A),
          (0x28, 0x82, 0x7F), (0x34, 0x26, 0x58)]


def spine(colour):
    """A book's spine for the shelf: its leather, darker at the edges, and two bands of gilt."""
    out = Image.new("RGBA", (16, 16), colour + (255,))
    for y in range(16):
        for x in range(16):
            shade = 0.72 if x in (0, 15) else 1.0
            out.putpixel((x, y), tuple(round(c * shade) for c in colour) + (255,))
    for y in (3, 12):
        for x in range(16):
            out.putpixel((x, y), (214, 176, 90, 255))
    return out


def generate_blocks():
    """The smith's shelf (a place per book, shown by its spine) and the lectern of book VII."""
    for i, colour in enumerate(SPINES):
        path = ASSETS / f"textures/block/lomo_{i}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        spine(colour).save(path)
    frame = "minecraft:block/oak_planks"
    write_json(ASSETS / "models/block/estanteria_del_herrero.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": frame, "side": "minecraft:block/chiseled_bookshelf_side", "top": "minecraft:block/chiseled_bookshelf_top",
                     "back": "minecraft:block/dark_oak_planks", "frame": frame},
        "elements": [
            {"from": [0, 0, 1], "to": [16, 16, 16], "faces": {
                "north": {"texture": "#back"}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
                "west": {"texture": "#side"}, "up": {"texture": "#top"}, "down": {"texture": "#top"}}},
            {"from": [0, 0, 0], "to": [16, 2, 1], "faces": {d: {"texture": "#frame"} for d in ("north", "up", "down", "east", "west")}},
            {"from": [0, 14, 0], "to": [16, 16, 1], "faces": {d: {"texture": "#frame"} for d in ("north", "up", "down", "east", "west")}},
            {"from": [0, 2, 0], "to": [1, 14, 1], "faces": {d: {"texture": "#frame"} for d in ("north", "east", "west")}},
            {"from": [15, 2, 0], "to": [16, 14, 1], "faces": {d: {"texture": "#frame"} for d in ("north", "east", "west")}},
        ],
    })
    for i in range(8):
        # Place 0 is on the reader's left, which from the north is the east (SelectableSlotContainer).
        right = 15 - i * 1.75
        left = right - 1.75
        write_json(ASSETS / f"models/block/estanteria_libro_{i}.json", {
            "textures": {"spine": f"forja:block/lomo_{i}", "particle": f"forja:block/lomo_{i}"},
            "elements": [{"from": [left + 0.1, 2, 0.2], "to": [right - 0.1, 14, 1], "faces": {
                "north": {"uv": [0, 2, 16, 14], "texture": "#spine"},
                "up": {"uv": [0, 0, 16, 1], "texture": "#spine"},
                "east": {"uv": [0, 2, 1, 14], "texture": "#spine"},
                "west": {"uv": [15, 2, 16, 14], "texture": "#spine"}}}],
        })
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    multipart = []
    for facing, y in rotations.items():
        multipart.append({"when": {"facing": facing}, "apply": {"model": "forja:block/estanteria_del_herrero", "y": y}})
        for i in range(8):
            multipart.append({"when": {"facing": facing, f"libro_{i}": "true"},
                              "apply": {"model": f"forja:block/estanteria_libro_{i}", "y": y}})
    write_json(ASSETS / "blockstates/estanteria_del_herrero.json", {"multipart": multipart})
    write_json(ASSETS / "items/estanteria_del_herrero.json", {"model": {"type": "minecraft:model", "model": "forja:block/estanteria_del_herrero"}})
    write_json(DATA / "recipe/estanteria_del_herrero.json", {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["PPP", "BIB", "PPP"],
        "key": {"P": "#minecraft:planks", "B": "minecraft:book", "I": "minecraft:iron_ingot"},
        "result": {"id": "forja:estanteria_del_herrero"},
    })
    # It drops itself and whatever books it holds.
    pools = [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "forja:estanteria_del_herrero"}],
              "conditions": [{"condition": "minecraft:survives_explosion"}]}]
    for i, book in enumerate(SHELF):
        pools.append({"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"forja:{book}"}],
                      "conditions": [{"condition": "minecraft:block_state_property", "block": "forja:estanteria_del_herrero",
                                      "properties": {f"libro_{i}": "true"}}]})
    write_json(DATA / "loot_table/blocks/estanteria_del_herrero.json", {"type": "minecraft:block", "pools": pools})
    # The lectern of book VII: vanilla's lectern, turned like it.
    write_json(ASSETS / "blockstates/atril_del_herrero.json", {"variants": {
        f"facing={facing}": ({"model": "minecraft:block/lectern", "y": y} if y else {"model": "minecraft:block/lectern"})
        for facing, y in rotations.items()}})
    write_json(ASSETS / "items/atril_del_herrero.json", {"model": {"type": "minecraft:model", "model": "minecraft:block/lectern"}})
    write_json(DATA / "loot_table/blocks/atril_del_herrero.json", {"type": "minecraft:block", "pools": [
        {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "forja:atril_del_herrero"}],
         "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # Both are wood: an axe takes them.
    tag = ROOT / "src/main/resources/data/minecraft/tags/block/mineable/axe.json"
    values = json.loads(tag.read_text(encoding="utf-8"))
    for block in ("forja:estanteria_del_herrero", "forja:atril_del_herrero"):
        if block not in values["values"]:
            values["values"].append(block)
    write_json(tag, values)


if __name__ == "__main__":
    import io
    import zipfile

    jar = Path.home() / ".gradle/caches/fabric-loom/26.2/minecraft-client.jar"
    with zipfile.ZipFile(jar) as client:
        book = Image.open(io.BytesIO(client.read("assets/minecraft/textures/item/book.png"))).convert("RGBA").crop((0, 0, 16, 16))
    print("books written:", ", ".join(generate(book)))
    generate_blocks()
    print("shelf and lectern written")
