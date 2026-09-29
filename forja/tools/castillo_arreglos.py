"""Passes over the whole castle after it is built, for what no single room can see (Andy's review, 2026-09-29).

- Valuables used as masonry: heavy cores, the Fallen Smith's own anvil (his drop), gold, budding amethyst. Anvils are
  kept where a smith would work and nowhere else in bulk.
- Loot: far fewer containers, never two side by side, and a cap per table. The ones that lose their loot become
  plain barrels, which read as the stores they are.
- Anything left hanging in the air: a block with nothing on any side, a lantern with nothing to hang from, a banner
  with no wall behind it, a torch or a carpet on nothing.
- The garrison: a castle this size had 32 monsters in it. Each floor gets a patrol to match its size.
- What the game would not leave as written: lava with a way out, gravel over nothing, a flower on stone.
"""
from castillo import HORIZONTAL, OPPOSITE, _hash, is_full

AIR = ("minecraft:air", {}, None)

# What stands in for each valuable: the same look where it can be had, the same place in the room where not.
CHEAPER = {
    "minecraft:heavy_core": ("minecraft:chiseled_deepslate", {}),
    "forja:yunque_del_herrero": ("minecraft:smithing_table", {}),
    "minecraft:gold_block": ("minecraft:yellow_terracotta", {}),
    "minecraft:raw_gold_block": ("minecraft:orange_terracotta", {}),
    "minecraft:budding_amethyst": ("minecraft:amethyst_block", {}),
    "minecraft:diamond_block": ("minecraft:light_blue_terracotta", {}),
    "minecraft:emerald_block": ("minecraft:green_terracotta", {}),
    "minecraft:iron_block": ("minecraft:smooth_stone", {}),
    "minecraft:netherite_block": ("minecraft:blackstone", {}),
    # takes a netherite ingot to make; the one under the start piece that the tests read is put in after this pass
    "minecraft:lodestone": ("minecraft:chiseled_stone_bricks", {}),
}
ANVILS = ("minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil")
# Where anvils belong: within this many blocks of one of the smithing benches.
BENCHES = ("forja:mesa_de_forja", "forja:mesa_de_piezas", "forja:mesa_de_talabarteria", "minecraft:smithing_table",
           "forja:mesa_de_losa", "forja:mesa_de_brasa", "forja:mesa_de_almas", "forja:fragua_apagada")
ANVIL_NEAR_BENCH = 6
ANVIL_SPACING = 10

# Loot, rarest first: a rarer table wins a spot over a commoner one. Caps are for the whole castle.
LOOT_ORDER = ["camara", "almas", "cripta", "lingotes", "torre_cima", "castillo_de_forja", "bodega", "guardia",
              "carbonera", "campamento_saqueadores", "torre"]
LOOT_CAP = {"camara": 1, "almas": 1, "cripta": 2, "lingotes": 3, "torre_cima": 6, "castillo_de_forja": 6, "bodega": 4,
            "guardia": 3, "carbonera": 2, "campamento_saqueadores": 2, "torre": 14}
LOOT_SPACING = 9

# Things that hang or stand on something and fall off without it.
HANGS = ("lantern", "soul_lantern", "farol_de_pavesa")
STANDS = ("torch", "soul_torch", "candle", "_carpet", "pressure_plate", "flower_pot", "potted_", "_banner", "rail",
          "redstone_wire", "snow")
WALL_MOUNTED = ("wall_banner", "wall_torch", "wall_sign", "ladder", "wall_head", "wall_skull", "tripwire_hook")


def _short(name):
    return name.split(":", 1)[1] if name and ":" in name else (name or "")


def _table(nbt):
    loot = (nbt or {}).get("LootTable")
    if not isinstance(loot, str):
        return None
    return loot.rsplit("/", 1)[-1].removeprefix("bastion_")


def cheaper_decor(world):
    changed = {"valuables": 0, "anvils": 0}
    for position, (name, props, nbt) in list(world.blocks.items()):
        if name in CHEAPER:
            new, new_props = CHEAPER[name]
            world.blocks[position] = (new, dict(new_props), None)
            changed["valuables"] += 1
    benches = [p for p, (name, _, _) in world.blocks.items() if name in BENCHES]
    kept = []
    for position, (name, props, nbt) in sorted(world.blocks.items()):
        if name not in ANVILS:
            continue
        x, y, z = position
        near_bench = any(abs(bx - x) <= ANVIL_NEAR_BENCH and abs(by - y) <= 3 and abs(bz - z) <= ANVIL_NEAR_BENCH
                         for bx, by, bz in benches)
        crowded = any(abs(kx - x) <= ANVIL_SPACING and abs(ky - y) <= 4 and abs(kz - z) <= ANVIL_SPACING for kx, ky, kz in kept)
        if near_bench and not crowded:
            kept.append(position)
            continue
        # a grindstone on the floor keeps the smithy's look for a fraction of the iron
        world.blocks[position] = ("minecraft:grindstone", {"face": "floor", "facing": props.get("facing", "north")}, None)
        changed["anvils"] += 1
    return changed


def thin_loot(world):
    containers = [(p, v) for p, v in world.blocks.items() if _table(v[2]) is not None]

    def rank(item):
        table = _table(item[1][2])
        return (LOOT_ORDER.index(table) if table in LOOT_ORDER else len(LOOT_ORDER), item[0])

    kept, counts, stripped = [], {}, 0
    for position, (name, props, nbt) in sorted(containers, key=rank):
        table = _table(nbt)
        x, y, z = position
        near = any(abs(kx - x) <= LOOT_SPACING and abs(ky - y) <= 5 and abs(kz - z) <= LOOT_SPACING for kx, ky, kz in kept)
        if not near and counts.get(table, 0) < LOOT_CAP.get(table, 1):
            kept.append(position)
            counts[table] = counts.get(table, 0) + 1
            continue
        world.blocks[position] = ("minecraft:barrel", {"facing": "up", "open": "false"}, None)
        stripped += 1
    return {"kept": len(kept), "stripped": stripped, "by_table": counts}


def _free(world, x, y, z):
    name = world.name(x, y, z)
    return name is None or name == "minecraft:air"


def drop_floating(world, rounds=3):
    """Air for whatever is left hanging. Only above the courtyard's first course, where the site is cleared and an
    unwritten position really is air; below it an unwritten position is the world's own ground."""
    removed = 0
    for _ in range(rounds):
        gone = []
        for (x, y, z), (name, props, nbt) in world.blocks.items():
            if y < 1 or name == "minecraft:air" or name == "minecraft:jigsaw" or nbt is not None and "LootTable" in nbt:
                continue
            short = _short(name)
            if all(_free(world, x + dx, y + dy, z + dz)
                   for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
                gone.append((x, y, z))
            elif short in HANGS and props.get("hanging") == "true" and _free(world, x, y + 1, z):
                gone.append((x, y, z))
            elif short in HANGS and props.get("hanging") != "true" and _free(world, x, y - 1, z):
                gone.append((x, y, z))
            elif any(short.endswith(s) for s in WALL_MOUNTED):
                facing = props.get("facing", "north")
                dx, dz = HORIZONTAL[OPPOSITE[facing]] if facing in HORIZONTAL else (0, 0)
                if not is_full(world.name(x + dx, y, z + dz)):
                    gone.append((x, y, z))
            elif any(s in short for s in STANDS) and "wall" not in short and _free(world, x, y - 1, z):
                gone.append((x, y, z))
        for position in gone:
            world.blocks[position] = AIR
        removed += len(gone)
        if not gone:
            break
    return removed


# The garrison, by level: what haunts the halls, the cellars and the deep.
GARRISON = {
    "hall": ["forja:coraza_vacia", "forja:coraza_vacia", "forja:automata_de_forja", "forja:percutor", "forja:tenaza",
             "minecraft:skeleton", "minecraft:zombie"],
    "cellar": ["forja:tenaza", "forja:herrumbre", "forja:herrumbre", "forja:escoria_viviente", "forja:coraza_vacia",
               "minecraft:skeleton"],
    "deep": ["forja:escoria_viviente", "forja:coraza_vacia", "forja:automata_de_forja", "forja:tenaza"],
}
PATROL_CELL = 9            # rooms are found on a 9 x 9 grid of roofed floor
GARRISON_SIZE = 72         # monsters added on top of the ones the rooms already hold
GARRISON_SPACING = 9
BOSS_ROOM = (100, -25, 92, 16)    # the Deep Forge: left to its master


def garrison(world):
    """Monsters on roofed floor, one to a patch of it, never on top of another entity or inside the boss's room."""
    taken = [tuple(int(v) for v in e["pos"]) for e in world.entities]
    placed = 0
    cells = {}
    for (x, y, z), (name, _, _) in world.blocks.items():
        if not is_full(name) or y + 1 < -26:
            continue
        if not (_free(world, x, y + 1, z) and _free(world, x, y + 2, z)):
            continue
        # a roof over it within eight blocks: inside, not the courtyard or a wall walk
        if not any(not _free(world, x, y + k, z) for k in range(3, 9)):
            continue
        level = "deep" if y < -18 else "cellar" if y < -1 else "hall"
        bx, by, bz, br = BOSS_ROOM
        if level == "deep" and (x - bx) ** 2 + (z - bz) ** 2 < br * br:
            continue
        key = (x // PATROL_CELL, y // 6, z // PATROL_CELL)
        cells.setdefault(key, []).append((x, y + 1, z, level))
    # the roomiest patches first: a hall gets its patrol before a stairwell does
    for key in sorted(cells, key=lambda k: (-len(cells[k]), k)):
        if placed >= GARRISON_SIZE:
            break
        spots = cells[key]
        if len(spots) < 40:                 # half the patch free floor, or it is a corridor or a cupboard
            continue
        x, y, z, level = spots[int(_hash(*key, 230) * len(spots))]
        if any(abs(tx - x) <= GARRISON_SPACING and abs(ty - y) <= 4 and abs(tz - z) <= GARRISON_SPACING for tx, ty, tz in taken):
            continue
        kinds = GARRISON[level]
        entity = kinds[int(_hash(x, y, z, 232) * len(kinds))]
        world.entities.append({"pos": (x + 0.5, float(y), z + 0.5), "nbt": {"id": entity, "PersistenceRequired": 1}})
        taken.append((x, y, z))
        placed += 1
    return placed


def tidy_stands(world):
    """Armour stands only where an armoury would keep one: on a solid floor, in free air, and against a wall. One in
    the middle of a rug, or inside a pot, is what Andy found (2026-09-29)."""
    kept, removed = [], 0
    for being in world.entities:
        if being["nbt"].get("id") != "minecraft:armor_stand":
            kept.append(being)
            continue
        x, y, z = (int(v // 1) for v in being["pos"])
        inside = world.name(x, y, z) not in (None, "minecraft:air")
        floor = is_full(world.name(x, y - 1, z))
        walled = any(is_full(world.name(x + dx, y + 1, z + dz)) for dx, dz in HORIZONTAL.values())
        if inside or not floor or not walled:
            removed += 1
            continue
        kept.append(being)
    world.entities[:] = kept
    return removed


SIDES_AND_UNDER = ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0))
# What falls when there is nothing under it, and the look-alike that does not.
FALLS = {"minecraft:gravel": "minecraft:cobblestone", "minecraft:sand": "minecraft:smooth_sandstone",
         "minecraft:red_sand": "minecraft:smooth_red_sandstone"}
SOIL = ("grass_block", "dirt", "coarse_dirt", "podzol", "rooted_dirt", "farmland", "moss_block", "mud", "mycelium", "dirt_path")
FLOWERS = ("allium", "azure_bluet", "cornflower", "oxeye_daisy", "poppy", "dandelion", "dead_bush", "short_grass", "fern")


def settle(world):
    """What the game would not leave as it was written (a second look, 2026-09-29): lava walled in behind an ember
    window whose wall a door was later cut through runs down the tower; gravel with a stair dug out under it falls;
    a flower on stone pops off. The lava becomes magma, which glows the same and stays; the gravel, a stone that
    looks like it; the flower goes."""
    counts = {"lava": 0, "fall": 0, "flowers": 0}
    for (x, y, z), (name, props, nbt) in list(world.blocks.items()):
        if name == "minecraft:lava":
            if any(_free(world, x + dx, y + dy, z + dz) for dx, dy, dz in SIDES_AND_UNDER):
                world.blocks[(x, y, z)] = ("minecraft:magma_block", {}, None)
                counts["lava"] += 1
        elif name in FALLS:
            under = world.name(x, y - 1, z)
            if under == "minecraft:air" or (under is None and y >= 0):
                world.blocks[(x, y, z)] = (FALLS[name], {}, None)
                counts["fall"] += 1
        elif _short(name) in FLOWERS:
            if _short(world.name(x, y - 1, z)) not in SOIL + ("sand", "red_sand"):
                world.blocks[(x, y, z)] = AIR
                counts["flowers"] += 1
    return counts


def apply(world):
    report = {}
    report.update(cheaper_decor(world))
    report["loot"] = thin_loot(world)
    report["garrison"] = garrison(world)
    report["floating"] = drop_floating(world)
    report["settled"] = settle(world)
    report["stands_removed"] = tidy_stands(world)
    hostile = sum(1 for e in world.entities if e["nbt"]["id"] != "minecraft:armor_stand" and "villager" not in e["nbt"]["id"])
    report["monsters"] = hostile
    print("bastion fixes:", report)
    return report
