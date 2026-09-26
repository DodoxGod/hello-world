"""What the Bastion's chests hold, room by room.

The castle had two loot tables for eighty rooms: every chest in it was either "a forge castle" or "a raiders' camp".
Now a chest says where it is. The stores of a tower hold what a garrison uses; the top of a tower what somebody
carried up there to keep; the larder food; the ingot store ingots; the soul forge what only it makes; and the vault,
at the bottom of everything, what the whole castle was built around.

castillo.generate() calls write(api); missing(world) says which LootTable a chest names that nothing writes.
"""
from pathlib import Path


def counted(item, weight, low, high):
    return {"type": "minecraft:item", "name": item, "weight": weight,
            "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}]}


def one(item, weight):
    return {"type": "minecraft:item", "name": item, "weight": weight}


def pool(low, high, *entries):
    return {"rolls": {"type": "minecraft:uniform", "min": low, "max": high}, "entries": list(entries)}


def chance(p, *entries):
    """A pool that usually gives nothing: one roll, `p` of it something."""
    empty = {"type": "minecraft:empty", "weight": max(1, round(sum(e["weight"] for e in entries) * (1 - p) / p))}
    return {"rolls": 1, "entries": list(entries) + [empty]}


TABLES = {
    # the ground and middle floors of the towers: what the watch uses and eats
    "bastion_torre": [
        pool(3, 6,
             counted("minecraft:arrow", 10, 4, 14), counted("minecraft:bread", 9, 1, 4), counted("minecraft:iron_ingot", 8, 1, 4),
             counted("minecraft:coal", 8, 2, 8), counted("minecraft:torch", 6, 2, 8), counted("forja:acero", 6, 1, 3),
             counted("forja:bronce", 5, 1, 3), counted("forja:laton", 4, 1, 3), counted("forja:peltre", 4, 1, 3),
             counted("forja:escoria", 5, 1, 4), counted("minecraft:leather", 4, 1, 3), counted("minecraft:string", 4, 2, 5)),
        chance(0.35, one("forja:plantilla", 6), one("forja:lingote_de_temple", 4)),
    ],
    # the top of a tower: what somebody carried up there to keep
    "bastion_torre_cima": [
        pool(3, 5,
             counted("minecraft:gold_ingot", 8, 2, 6), counted("forja:acero", 8, 2, 4), counted("forja:hierro_estelar", 4, 1, 2),
             counted("forja:damasco", 4, 1, 2), counted("forja:acero_refractario", 3, 1, 2), counted("minecraft:experience_bottle", 6, 2, 5),
             counted("minecraft:lapis_lazuli", 5, 3, 9), counted("minecraft:diamond", 2, 1, 2)),
        pool(1, 2, counted("forja:plantilla", 10, 1, 3), one("forja:lingote_de_temple", 6), one("forja:orbe_vacio", 4),
             one("forja:sello", 2), one("forja:orbe_de_mejora", 2)),
    ],
    # the Nine Masters were buried with the tools of their rank
    "bastion_cripta": [
        pool(3, 5,
             counted("minecraft:bone", 8, 2, 6), counted("minecraft:gold_nugget", 8, 4, 12), counted("minecraft:candle", 5, 1, 3),
             counted("minecraft:experience_bottle", 6, 2, 6), counted("forja:obsidiacero", 3, 1, 2), counted("forja:damasco", 3, 1, 2),
             counted("minecraft:gold_ingot", 5, 2, 5)),
        pool(1, 2, counted("forja:plantilla", 8, 1, 3), one("forja:lingote_de_temple", 6), one("forja:sello", 4),
             one("forja:orbe_de_mejora", 3), one("forja:talisman", 2)),
    ],
    # the gaolers' table
    "bastion_guardia": [
        pool(3, 5,
             counted("minecraft:bread", 8, 1, 4), counted("minecraft:cooked_beef", 5, 1, 3), counted("minecraft:arrow", 8, 4, 12),
             counted("minecraft:iron_nugget", 8, 3, 10), counted("minecraft:chain", 5, 1, 4), counted("minecraft:leather", 5, 1, 3),
             counted("forja:acero", 5, 1, 2), counted("minecraft:emerald", 4, 1, 4)),
        chance(0.25, one("forja:plantilla", 5), one("minecraft:crossbow", 3)),
    ],
    # the larder
    "bastion_bodega": [
        pool(4, 8,
             counted("minecraft:bread", 10, 2, 6), counted("minecraft:potato", 8, 2, 8), counted("minecraft:beetroot", 6, 2, 6),
             counted("minecraft:apple", 6, 1, 4), counted("minecraft:cooked_salmon", 5, 1, 4), counted("minecraft:cooked_mutton", 5, 1, 4),
             counted("minecraft:wheat", 6, 3, 9), counted("minecraft:honey_bottle", 3, 1, 2), counted("minecraft:sugar", 4, 2, 6),
             counted("minecraft:glass_bottle", 3, 1, 3)),
    ],
    # the ingot store: the castle's metal, counted and stacked
    "bastion_lingotes": [
        pool(4, 7,
             counted("minecraft:iron_ingot", 12, 3, 9), counted("minecraft:copper_ingot", 10, 4, 12), counted("minecraft:gold_ingot", 6, 2, 6),
             counted("forja:acero", 10, 2, 6), counted("forja:bronce", 8, 2, 6), counted("forja:laton", 7, 2, 5),
             counted("forja:peltre", 6, 2, 5), counted("forja:electro", 4, 1, 3), counted("forja:almacero", 2, 1, 2)),
        chance(0.4, one("forja:lingote_de_temple", 6), one("forja:fundente_maestro", 2)),
    ],
    # the coal store
    "bastion_carbonera": [
        pool(3, 6,
             counted("minecraft:coal", 12, 4, 16), counted("minecraft:charcoal", 8, 4, 12), counted("forja:escoria", 8, 2, 6),
             counted("forja:ascua", 5, 1, 3), counted("forja:cinerio", 3, 1, 2), counted("minecraft:flint", 4, 1, 4),
             counted("minecraft:iron_nugget", 5, 3, 9)),
    ],
    # the soul forge: what only a fire like that one makes
    "bastion_almas": [
        pool(3, 5,
             counted("forja:almacero", 6, 1, 3), counted("forja:obsidiacero", 5, 1, 3), counted("forja:acero_vivo", 3, 1, 2),
             counted("minecraft:soul_sand", 5, 2, 6), counted("minecraft:experience_bottle", 6, 2, 6), counted("forja:ascua", 5, 1, 3),
             counted("minecraft:gold_ingot", 5, 2, 5)),
        pool(1, 2, one("forja:orbe_vacio", 6), one("forja:lingote_de_temple", 6), one("forja:fundente_maestro", 3),
             one("forja:orbe_de_mejora", 3), one("forja:sello", 2)),
    ],
    # the vault: what the castle was built around
    "bastion_camara": [
        pool(4, 6,
             counted("minecraft:gold_ingot", 10, 4, 10), counted("minecraft:diamond", 6, 1, 4), counted("minecraft:emerald", 6, 3, 9),
             counted("forja:hierro_estelar", 5, 1, 3), counted("forja:solacero", 3, 1, 2), counted("forja:lunacero", 3, 1, 2),
             counted("forja:acero_estelar", 3, 1, 2), counted("minecraft:experience_bottle", 5, 4, 8), one("minecraft:netherite_scrap", 2)),
        pool(2, 3, counted("forja:plantilla", 8, 2, 4), one("forja:orbe_de_mejora", 6), one("forja:sello", 5),
             one("forja:talisman", 3), one("forja:fundente_maestro", 3)),
    ],
}


# A talisman with no gem in it is a belt buckle: the ones in chests come set, with any stone but netherite.
GEMS = ("esmeralda", "diamante", "cuarzo", "amatista", "eco", "prismarina")


def _set_talismans(api, pools):
    """Each "forja:talisman" entry becomes one entry per gem, the rest of its pool weighed up to keep the odds."""
    out = []
    for entry_pool in pools:
        entries = entry_pool["entries"]
        placeholders = [e for e in entries if e.get("name") == "forja:talisman"]
        if not placeholders:
            out.append(entry_pool)
            continue
        k = len(GEMS)
        rest = [dict(e, weight=e["weight"] * k) for e in entries if e.get("name") != "forja:talisman"]
        for placeholder in placeholders:
            for gem in GEMS:
                colour = api.TALISMANS[gem][1]
                rest.append({"type": "minecraft:item", "name": "forja:talisman", "weight": placeholder["weight"],
                             "functions": [{"function": "minecraft:set_components", "components": {
                                 "forja:talisman": gem,
                                 "minecraft:item_name": {"translate": "item.forja.talisman.de", "with": [{"translate": f"talisman.forja.{gem}"}]},
                                 "minecraft:custom_model_data": {"colors": [colour]},
                                 "minecraft:rarity": "uncommon",
                             }}]})
        out.append(dict(entry_pool, entries=rest))
    return out


def write(api):
    for name, pools in TABLES.items():
        api.write_json(api.DATA / f"loot_table/chests/{name}.json",
                       {"type": "minecraft:chest", "random_sequence": f"forja:chests/{name}", "pools": _set_talismans(api, pools)})


def missing(world, data_dir):
    """LootTables named by the castle's chests and barrels that no file answers to."""
    named = set()
    for value in world.blocks.values():
        nbt = value[2] if len(value) > 2 else None
        if nbt and nbt.get("LootTable"):
            named.add(nbt["LootTable"])
    gone = []
    for table in sorted(named):
        space, path = table.split(":", 1)
        if space == "forja" and path.split("/")[-1] in TABLES:
            continue
        if space == "forja" and not (Path(data_dir) / f"loot_table/{path}.json").exists():
            gone.append(table)
    return gone
