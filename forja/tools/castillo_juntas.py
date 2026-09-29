"""How vanilla joins fences, panes, bars, walls, fence gates and stairs to what is beside them.

A jigsaw piece is placed exactly as it is written (the game sets `knownShape` for pool elements and never updates the
shapes afterwards), so the states the game would have worked out when a player placed each block are worked out here,
from the finished castle, with the game's own rules (FenceBlock, IronBarsBlock, WallBlock, FenceGateBlock, StairBlock).
"""
from castillo import HORIZONTAL, OPPOSITE, LEFT_OF

RIGHT_OF = {v: k for k, v in LEFT_OF.items()}
UNSTURDY = ("water", "lava", "fire", "_fence", "_wall", "_bars", "_pane", "_door", "chest", "lantern", "chain", "torch",
            "banner", "_sign", "candle", "cobweb", "ladder", "vine", "button", "lever", "pressure_plate", "head", "skull", "pot",
            "_bed", "bell", "grindstone", "lectern", "cauldron", "stonecutter", "brewing", "rail", "lightning_rod", "anvil",
            "campfire", "carpet", "enchanting_table", "dirt_path", "farmland", "sculk_sensor", "hopper", "amethyst_cluster", "_bud",
            "dead_bush", "allium", "azure_bluet", "cornflower", "oxeye_daisy", "wheat", "carrots", "potatoes", "beetroots",
            "honey_block", "scaffolding", "end_rod", "structure_void", "jigsaw", "powder_snow", "_trapdoor", "farol",
            "conducto_", "cano_", "short_grass", "tall_grass", "fern", "sapling", "sugar_cane", "sea_pickle", "_coral")
# the thin ones whose names are also the start of a full block's
UNSTURDY_EXACTLY = ("air", "cave_air", "snow", "brown_mushroom", "red_mushroom")
# blocks with full faces that fences, panes and walls still refuse (Block.isExceptionForConnection)
EXCEPTIONS = ("leaves", "barrier", "carved_pumpkin", "jack_o_lantern", "melon", "pumpkin", "shulker_box")
# what raises a wall's post whatever its arms say (the block tag minecraft:wall_post_override)
POST_OVERRIDE = ("torch", "_sign", "banner", "pressure_plate")
# bottom faces that cover the middle of the block: what stands on a wall and so has its post raised under it
STANDS_ON_POST = ("chest", "lantern", "chain", "candle", "head", "skull", "pot", "anvil", "brewing", "lightning_rod", "amethyst", "_bud",
                  "campfire", "carpet", "farol")


def short(name):
    return name.split(":", 1)[1] if name else ""


def sturdy(entry, face, below_ground):
    """Whether the face of `entry` looking `face` (north, south, east, west, up, down) is a full square."""
    if entry is None:
        return below_ground                        # unwritten under the courtyard is the world's own ground
    name, props, _ = entry
    s = short(name)
    if s.endswith("_stairs"):
        half = props.get("half", "bottom")
        if face in ("up", "down"):
            return (face == "down") == (half == "bottom")
        facing, shape = props.get("facing", "north"), props.get("shape", "straight")
        if shape.startswith("outer"):
            return False
        if face == facing:
            return True
        if shape == "inner_left":
            return face == LEFT_OF[facing]
        if shape == "inner_right":
            return face == RIGHT_OF[facing]
        return False
    if s.endswith("_slab"):
        kind = props.get("type", "bottom")
        return kind == "double" or (kind == "bottom" and face == "down") or (kind == "top" and face == "up")
    if s == "composter":
        return face != "up"
    if s.endswith("carpet") or s in ("enchanting_table", "dirt_path", "farmland", "sculk_sensor", "campfire", "soul_campfire"):
        return face == "down"
    if s.endswith("_trapdoor"):
        if props.get("open") == "true":
            return face == OPPOSITE.get(props.get("facing", "north"))
        return face == ("up" if props.get("half") == "top" else "down")
    if s.endswith("glass") and "pane" not in s:
        return True
    return s not in UNSTURDY_EXACTLY and not any(part in s for part in UNSTURDY)


def exception(entry):
    return entry is not None and any(part in short(entry[0]) for part in EXCEPTIONS)


def _gate_across(entry, direction):
    """A fence gate joins what is either side of it along its own line (FenceGateBlock.connectsToDirection)."""
    if entry is None or not entry[0].endswith("_fence_gate"):
        return False
    facing = entry[1].get("facing", "north")
    return (facing in ("north", "south")) == (direction in ("east", "west"))


def _wooden(name):
    return name.endswith("_fence") and not name.endswith("nether_brick_fence")


def _bars_family(name):
    return name is not None and (name.endswith("_pane") or name.endswith("_bars"))


def _wall(name):
    return name is not None and name.endswith("_wall")


def _covers(entry, test, below_ground):
    """Whether the bottom face of `entry` covers the wall's test shape: 'post' (the middle) or a side's arm."""
    if entry is None:
        return below_ground
    name, props, _ = entry
    s = short(name)
    if sturdy(entry, "down", False):
        return True
    if _wall(name):
        return (props.get("up") == "true" or any(props.get(k, "none") != "none" for k in HORIZONTAL)) if test == "post" \
            else props.get(test, "none") != "none"
    if s.endswith("_fence") or _bars_family(name):
        return True if test == "post" else props.get(test) == "true"
    if test != "post":
        return False
    if s.endswith("_stairs") or s.endswith("_slab") or s.endswith("_trapdoor"):
        return False
    if "lantern" in s and props.get("hanging") == "true":
        return False
    if s == "iron_chain" or s.endswith("chain"):
        return props.get("axis", "y") == "y"
    return any(part in s for part in STANDS_ON_POST)


def resolve_joints(world):
    """Sets every joining state from the neighbours. Returns how many blocks changed, by kind."""
    blocks = world.blocks
    changed = {"fence": 0, "pane": 0, "wall": 0, "gate": 0, "stairs": 0}

    def at(x, y, z):
        return blocks.get((x, y, z))

    # Stairs first: whether a fence or a wall joins a stair depends on the stair's corner.
    stair_positions = [p for p, (n, _, _) in blocks.items() if n.endswith("_stairs")]
    for x, y, z in stair_positions:
        name, props, nbt = blocks[(x, y, z)]
        facing, half = props.get("facing", "north"), props.get("half", "bottom")

        def stair_at(direction):
            dx, dz = HORIZONTAL[direction]
            other = at(x + dx, y, z + dz)
            if other is not None and other[0].endswith("_stairs") and other[1].get("half", "bottom") == half:
                return other[1].get("facing", "north")
            return None

        def can_take(direction):
            return stair_at(direction) != facing

        shape = "straight"
        front = stair_at(facing)
        if front is not None and front not in (facing, OPPOSITE[facing]) and can_take(OPPOSITE[front]):
            shape = "outer_left" if front == LEFT_OF[facing] else "outer_right"
        else:
            back = stair_at(OPPOSITE[facing])
            if back is not None and back not in (facing, OPPOSITE[facing]) and can_take(back):
                shape = "inner_left" if back == LEFT_OF[facing] else "inner_right"
        if props.get("shape") != shape:
            changed["stairs"] += 1
            props = dict(props, shape=shape)
            blocks[(x, y, z)] = (name, props, nbt)

    pending = []
    for (x, y, z), (name, props, nbt) in sorted(blocks.items(), key=lambda item: -item[0][1]):
        s = short(name)
        if not (s.endswith("_fence") or s.endswith("_pane") or s.endswith("_bars") or s.endswith("_wall") or s.endswith("_fence_gate")):
            continue
        new = dict(props)
        new.setdefault("waterlogged", "false")
        if s.endswith("_fence_gate"):
            facing = props.get("facing", "north")
            sides = ("east", "west") if facing in ("north", "south") else ("north", "south")
            new["in_wall"] = "true" if any(_wall((at(x + HORIZONTAL[d][0], y, z + HORIZONTAL[d][1]) or (None,))[0]) for d in sides) else "false"
            kind = "gate"
        else:
            arms = {}
            for side, (dx, dz) in HORIZONTAL.items():
                other = at(x + dx, y, z + dz)
                other_name = other[0] if other else None
                face = sturdy(other, OPPOSITE[side], y < 0) and not exception(other)
                if s.endswith("_fence"):
                    arms[side] = face or (other_name is not None and _wooden(other_name) == _wooden(name) and other_name.endswith("_fence")) \
                        or _gate_across(other, side)
                elif s.endswith("_wall"):
                    arms[side] = _wall(other_name) or face or _bars_family(other_name) or _gate_across(other, side)
                else:
                    arms[side] = face or _bars_family(other_name) or _wall(other_name)
            if s.endswith("_wall"):
                above = at(x, y + 1, z)
                sides = {side: ("tall" if _covers(above, side, y + 1 < 0) else "low") if joined else "none" for side, joined in arms.items()}
                new.update(sides)
                pending.append((x, y, z))       # the post needs the wall above worked out first
                kind = "wall"
            else:
                new.update({side: "true" if joined else "false" for side, joined in arms.items()})
                kind = "fence" if s.endswith("_fence") else "pane"
        if new != props:
            changed[kind] += 1
        blocks[(x, y, z)] = (name, new, nbt)
    # posts from the top down: a wall's post depends on the wall standing on it
    for x, y, z in sorted(pending, key=lambda p: -p[1]):
        name, props, nbt = blocks[(x, y, z)]
        above = at(x, y + 1, z)
        n, s_, e, w = (props[k] for k in ("north", "south", "east", "west"))
        if above is not None and _wall(above[0]) and above[1].get("up") == "true":
            up = True
        elif (n == s_ == e == w == "none") or ((n == "none") != (s_ == "none")) or ((w == "none") != (e == "none")):
            up = True
        elif (n == "tall" and s_ == "tall") or (e == "tall" and w == "tall"):
            up = False
        else:
            up = (above is not None and any(part in short(above[0]) for part in POST_OVERRIDE)) or _covers(above, "post", y + 1 < 0)
        value = "true" if up else "false"
        if props.get("up") != value:
            props = dict(props, up=value)
            blocks[(x, y, z)] = (name, props, nbt)
            changed["wall"] += 1
    return changed
