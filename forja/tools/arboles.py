"""The big class trees (docs/ARBOLES.md): lays out the content of tools/arboles_datos.py and writes it out.

Run from forja/:
    python tools/arboles.py                 -> src/main/resources/forja_arboles.json (what the game reads),
                                               docs/arboles.json (the same), and the generated parts of docs/ARBOLES.md
    python tools/arboles.py --imagenes DIR  -> also draws the Guerrero and Mago trees into DIR

tools/generate_lang.py imports lang_entries() from here, so the node texts reach the language files from the
same data. Costs, points and the level cap live in arboles_datos.py and travel in the JSON: the game reads
them from there, so changing a cost is changing one number and running this script.
"""
import heapq
import json
import math
import os
import re
import sys
import unicodedata
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from arboles_datos import (FORGE_ICONS, KEYSTONE_GLYPHS, BRIDGE_PACKAGES, BRIDGES, CLASSES, COST, FIRST_STEP, FORGE_INNER, FORGE_OUTER, FORGE_TEMPLE_II,  # noqa: E402
                           LEVEL_POINTS_DEN, LEVEL_POINTS_NUM, MAX_LEVEL, MILESTONE_CAP_PER_LEVEL, MILESTONES, NEW_STATS, SKILL_COST,
                           SMALL, STATS, STEP_GROWTH)

ROOT = os.path.dirname(HERE)
DOCS = os.path.join(ROOT, "docs")
RESOURCE = os.path.join(ROOT, "src", "main", "resources", "forja_arboles.json")

CLASS_COLORS = {"guerrero": "#C0463A", "asesino": "#8A6BC8", "tanque": "#8C99A6", "mago": "#4F7FE8",
                "curandero": "#5CC46A", "arquero": "#8DBF4A", "forja": "#E8923A"}
CLASS_NAMES = {"guerrero": ("Guerrero", "Warrior"), "asesino": ("Asesino", "Assassin"), "tanque": ("Tanque", "Tank"),
               "mago": ("Mago", "Mage"), "curandero": ("Curandero", "Healer"), "arquero": ("Arquero", "Archer")}


def level_points(level):
    return LEVEL_POINTS_NUM * max(0, level) // LEVEL_POINTS_DEN


def milestone_points():
    return sum(m[5] for m in MILESTONES)


def budget():
    return level_points(MAX_LEVEL) + milestone_points()


# --------------------------------------------------------------------------------------------- formatting
def fmt_num(v):
    if abs(v - round(v)) < 1e-9:
        return str(int(round(v)))
    return ("%.2f" % v).rstrip("0").rstrip(".").replace(".", ",")


def fmt_mod(stat, v, lang=0):
    label, unit = STATS[stat][lang], STATS[stat][2]
    sign = "+" if v > 0 else "−"
    a = abs(v)
    if unit == "pct":
        return "%s %s%s %%" % (label, sign, fmt_num(a * 100))
    if unit == "ticks":
        return "%s %s%s tick%s" % (label, sign, fmt_num(a), "" if a == 1 else "s")
    return "%s %s%s" % (label, sign, fmt_num(a))


def render(tpl, nums):
    def rep(m):
        i = int(m.group(1))
        return fmt_num(nums[i] * 100) + " %" if m.group(2) else fmt_num(nums[i])
    return re.sub(r"\{(\d+)(%?)\}", rep, tpl)


def to_lang(tpl):
    """A {0}/{0%} template as a Minecraft translation: %1$s..., a literal % doubled."""
    tpl = tpl.replace("%", "%%")
    return re.sub(r"\{(\d+)%{0,2}\}", lambda m: "%%%d$s" % (int(m.group(1)) + 1), tpl)


def formats(tpl, count):
    out = ["num"] * count
    for m in re.finditer(r"\{(\d+)(%?)\}", tpl):
        if m.group(2):
            out[int(m.group(1))] = "pct"
    return out


def slug(text):
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


# --------------------------------------------------------------------------------------------- layout
SPOKES = [("A", 90), ("S1", 30), ("B", -30), ("S2", -90), ("C", -150), ("S3", 150)]
R_RING, R_FORGE_IN, R_FORGE_OUT, R_FORGE_SKILL2 = 2.0, 2.75, 3.65, 4.55
TRUNK_R = [3.3, 4.2, 5.1, 6.1]
PATH_R = [7.1, 8.0, 9.0, 9.9, 11.0]
PATH_SPREAD = 12
SIDE_SPREAD = 8
SENDA_R = [3.3, 4.4, 5.4, 6.4]
BRIDGE_R = [7.5, 8.5, 9.5]
GAPS = [60, 0, -60, -120, 180, 120]  # gap i lies between spoke i and spoke i+1


def polar(r, deg):
    a = math.radians(deg)
    return round(r * math.cos(a), 3), round(r * math.sin(a), 3)


HOOKS = {}


def new_node(nid, tipo, nombre, mods, plantilla, nums, x, y, region, gancho=None, icono=None):
    n = OrderedDict()
    n["id"] = nid
    n["tipo"] = tipo
    n["nombre"] = {"es": nombre[0], "en": nombre[1]}
    n["coste"] = COST.get(tipo, 0)
    n["region"] = region
    n["mods"] = [[s, v] for s, v in mods]
    n["numeros"] = list(nums)
    n["formatos"] = formats(plantilla[0], len(nums)) if plantilla else ["num"] * len(nums)
    n["plantilla"] = {"es": plantilla[0], "en": plantilla[1]} if plantilla else None
    n["gancho"] = gancho
    n["icono"] = icono
    if gancho is not None:
        seen = HOOKS.get(gancho)
        here = (nombre, plantilla, tuple(nums))
        if seen is not None and seen != here:
            raise SystemExit("hook %s means two different nodes" % gancho)
        HOOKS[gancho] = here
    lines = [fmt_mod(s, v) for s, v in mods]
    if plantilla:
        lines.append(render(plantilla[0], nums))
    n["efecto"] = "; ".join(lines)
    n["x"], n["y"] = x, y
    n["conexiones"] = []
    return n


def small_name(stat):
    return (STATS[stat][0].capitalize(), STATS[stat][1].capitalize())


def make(cid, region, spec, nid, x, y, skills):
    t = spec["tipo"]
    if t == "habilidad":
        slot = spec["slot"]
        sk = skills[slot[0]]
        upgrade = slot.endswith("2")
        part = sk["mejora"] if upgrade else sk
        name = (sk["nombre"][0] + (" II" if upgrade else ""), sk["nombre"][1] + (" II" if upgrade else ""))
        n = new_node(nid, "habilidad", name, [], part["plantilla"], part["numeros"], x, y, region, icono=sk["icono"])
        n["coste"] = SKILL_COST[slot]
        n["habilidad"] = slot
        n["efecto"] += "; espera %d s" % part["espera"]
        return n
    if t == "menor":
        stat = spec["mods"][0][0]
        return new_node(nid, "menor", small_name(stat), spec["mods"], None, [], x, y, region, icono=spec["icono"])
    n = new_node(nid, t, spec["nombre"], spec["mods"], spec.get("texto"), spec.get("numeros", []), x, y, region, slug(spec["nombre"][0]), spec["icono"])
    if t == "clave":
        n["gana"], n["precio"] = spec["gana"], spec["precio"]
    return n


def build_forge():
    forge = []
    for i, (fid, name, mods, tpl, nums) in enumerate(FORGE_INNER):
        x, y = polar(R_FORGE_IN, GAPS[i])
        forge.append(new_node(fid, "forja", name, mods, tpl, nums, x, y, "forja", fid.split(".", 1)[1], FORGE_ICONS[fid]))
    for i, (fid, name, mods, tpl, nums) in enumerate(FORGE_OUTER):
        x, y = polar(R_FORGE_OUT, GAPS[i])
        n = new_node(fid, "forja", name, mods, tpl, nums, x, y, "forja", fid.split(".", 1)[1], FORGE_ICONS[fid])
        n["conexiones"].append(FORGE_INNER[i][0])
        forge.append(n)
    fid, name, mods, tpl, nums = FORGE_TEMPLE_II
    x, y = polar(R_FORGE_SKILL2, GAPS[2] + 9)
    n = new_node(fid, "forja", name, mods, tpl, nums, x, y, "forja", fid.split(".", 1)[1], FORGE_ICONS[fid])
    n["conexiones"].append("forja.temple_de_campana")
    forge.append(n)
    return forge


def build_class(cid, cdef, forge):
    nodes = []
    skills = cdef["habilidades"]
    origin = new_node(cid + ".origen", "origen", CLASS_NAMES[cid], [], None, [], 0.0, 0.0, "origen", icono="clase:" + cid)
    origin["efecto"] = "la clase: " + cdef["base"][0] + "; habilidad I (V): " + skills["V"]["nombre"][0]
    nodes.append(origin)
    doors = []
    for i, (sp, ang) in enumerate(SPOKES):
        x, y = polar(R_RING, ang)
        d = make(cid, "nucleo", cdef["puertas"][i], "%s.nucleo_%d" % (cid, i + 1), x, y, skills)
        d["tipo"], d["coste"] = "nucleo", COST["nucleo"]
        d["conexiones"].append(origin["id"])
        doors.append(d)
        nodes.append(d)
    for i, d in enumerate(doors):
        d["conexiones"].append(doors[(i + 1) % 6]["id"])
        d["conexiones"].append(forge[i]["id"])
        d["conexiones"].append(forge[(i - 1) % 6]["id"])

    for i, (sp, ang) in enumerate(SPOKES):
        door = doors[i]
        if sp in ("A", "B", "C"):
            br = cdef["ramas"][sp]
            prev = door["id"]
            region = "rama_" + sp.lower()
            for t, spec in enumerate(br["tronco"]):
                x, y = polar(TRUNK_R[t], ang)
                n = make(cid, region, spec, "%s.%s.tronco_%d" % (cid, sp.lower(), t + 1), x, y, skills)
                n["conexiones"].append(prev)
                nodes.append(n)
                prev = n["id"]
            trunk_end = prev
            keys = []
            for p, path in enumerate(br["caminos"]):
                sgn = 1 if p == 0 else -1
                pang = ang + sgn * PATH_SPREAD
                prev = trunk_end
                ids = []
                for k, spec in enumerate(path["nodos"]):
                    x, y = polar(PATH_R[k], pang)
                    n = make(cid, region, spec, "%s.%s%d.%d" % (cid, sp.lower(), p + 1, k + 1), x, y, skills)
                    n["camino"] = "%s%d" % (sp.lower(), p + 1)
                    n["conexiones"].append(prev)
                    nodes.append(n)
                    prev = n["id"]
                    ids.append(n["id"])
                    if n["tipo"] == "clave":
                        keys.append(n)
                for s, spec in enumerate(path["lados"]):
                    k = 1 if s == 0 else 3
                    x, y = polar(PATH_R[k], pang + sgn * SIDE_SPREAD)
                    n = make(cid, region, spec, "%s.%s%d.lado_%d" % (cid, sp.lower(), p + 1, s + 1), x, y, skills)
                    n["camino"] = "%s%d" % (sp.lower(), p + 1)
                    n["conexiones"].append(ids[k])
                    nodes.append(n)
            # The two keystones of a branch rule each other out: one way of playing per branch.
            keys[0]["excluye"] = keys[1]["id"]
            keys[1]["excluye"] = keys[0]["id"]
        else:
            sd = cdef["sendas"][sp]
            region = "senda_" + sp[1]
            prev = door["id"]
            ids = []
            for k, spec in enumerate(sd["nodos"]):
                x, y = polar(SENDA_R[k], ang)
                n = make(cid, region, spec, "%s.%s.%d" % (cid, sp.lower(), k + 1), x, y, skills)
                n["conexiones"].append(prev)
                nodes.append(n)
                prev = n["id"]
                ids.append(n["id"])
            if "lado" in sd:
                anchor = 1 if sp == "S2" else 3
                x, y = polar(SENDA_R[anchor], ang + 13)
                n = make(cid, region, sd["lado"], "%s.%s.lado" % (cid, sp.lower()), x, y, skills)
                n["conexiones"].append(ids[anchor])
                nodes.append(n)
            target = BRIDGES[cid][int(sp[1]) - 1]
            package = BRIDGE_PACKAGES[target]
            for b, spec in enumerate(package[1:]):
                x, y = polar(BRIDGE_R[b], ang)
                n = make(cid, region, spec, "%s.%s.puente_%d" % (cid, sp.lower(), b + 1), x, y, skills)
                n["tipo"] = "puente" if b == 0 else "cruzado"
                n["coste"] = COST[n["tipo"]]
                n["destino"] = target
                if b == 0:
                    n["icono"] = "clase:" + target
                    n["nombre"] = {"es": package[0][0], "en": package[0][1]}
                    n["gancho"] = slug(package[0][0])
                n["conexiones"].append(prev)
                nodes.append(n)
                prev = n["id"]

    def skill(slot):
        sk = skills[slot]
        m = sk["mejora"]
        return OrderedDict([
            ("id", sk["id"]), ("icono", sk["icono"]), ("nombre", {"es": sk["nombre"][0], "en": sk["nombre"][1]}),
            ("plantilla", {"es": sk["plantilla"][0], "en": sk["plantilla"][1]}), ("numeros", sk["numeros"]),
            ("formatos", formats(sk["plantilla"][0], len(sk["numeros"]))), ("espera", sk["espera"]),
            ("mejora", OrderedDict([("plantilla", {"es": m["plantilla"][0], "en": m["plantilla"][1]}), ("numeros", m["numeros"]),
                                    ("formatos", formats(m["plantilla"][0], len(m["numeros"]))), ("espera", m["espera"])])),
        ])
    return OrderedDict([
        ("nombre", {"es": CLASS_NAMES[cid][0], "en": CLASS_NAMES[cid][1]}), ("color", CLASS_COLORS[cid]),
        ("habilidades", OrderedDict((s, skill(s)) for s in ("V", "B", "N"))),
        ("regiones", OrderedDict(
            [(k, {"es": v["nombre"][0], "en": v["nombre"][1]}) for k, v in cdef["ramas"].items()]
            + [(k, {"es": v["nombre"][0], "en": v["nombre"][1]}) for k, v in cdef["sendas"].items()]
            + [("%s%d" % (k.lower(), p + 1), {"es": path["nombre"][0], "en": path["nombre"][1]})
               for k, v in cdef["ramas"].items() for p, path in enumerate(v["caminos"])])),
        ("puentes", BRIDGES[cid]),
        ("nodos", nodes)])


def symmetrize(out):
    for c in out["clases"].values():
        by = {n["id"]: n for n in c["nodos"]}
        for n in list(c["nodos"]):
            for o in n["conexiones"]:
                if o in by and n["id"] not in by[o]["conexiones"]:
                    by[o]["conexiones"].append(n["id"])
    fb = {n["id"]: n for n in out["nodos_forja"]}
    for n in out["nodos_forja"]:
        for o in n["conexiones"]:
            if o in fb and n["id"] not in fb[o]["conexiones"]:
                fb[o]["conexiones"].append(n["id"])


def build():
    HOOKS.clear()
    forge = build_forge()
    out = OrderedDict()
    out["version"] = 2
    out["nivel_max"] = MAX_LEVEL
    out["puntos_nivel"] = {"numerador": LEVEL_POINTS_NUM, "denominador": LEVEL_POINTS_DEN}
    out["tope_hitos_por_nivel"] = MILESTONE_CAP_PER_LEVEL
    out["experiencia"] = {"primer_paso": FIRST_STEP, "crecimiento": STEP_GROWTH}
    out["costes"] = COST
    out["costes_habilidad"] = SKILL_COST
    out["menor"] = {"porcentaje": 0.03, "equivalencias": SMALL}
    out["estadisticas_nuevas"] = NEW_STATS
    out["hitos"] = [OrderedDict([("id", m[0]), ("nombre", {"es": m[1], "en": m[2]}), ("condicion", {"es": m[3], "en": m[4]}),
                                 ("puntos", m[5]), ("fuente", m[6])]) for m in MILESTONES]
    out["nodos_forja"] = forge
    out["clases"] = OrderedDict((cid, build_class(cid, cdef, forge)) for cid, cdef in CLASSES.items())
    symmetrize(out)
    return out


# --------------------------------------------------------------------------------------------- language
def lang_entries():
    """Every text of the trees as {key: (es, en)}, for tools/generate_lang.py."""
    out = build()
    lang = OrderedDict()

    def put(key, es, en):
        lang[key] = (es, en)
    for n in out["nodos_forja"] + [n for c in out["clases"].values() for n in c["nodos"]]:
        if n["gancho"]:
            put("gui.forja.nodo." + n["gancho"], n["nombre"]["es"], n["nombre"]["en"])
            if n["plantilla"]:
                put("gui.forja.nodo." + n["gancho"] + ".efecto", to_lang(n["plantilla"]["es"]), to_lang(n["plantilla"]["en"]))
    for cid, c in out["clases"].items():
        for k, name in c["regiones"].items():
            put("gui.forja.arbol.%s.%s" % (cid, k.lower()), name["es"], name["en"])
        for slot, sk in c["habilidades"].items():
            put("gui.forja.habilidad." + sk["id"], sk["nombre"]["es"], sk["nombre"]["en"])
            put("gui.forja.habilidad." + sk["id"] + ".efecto", to_lang(sk["plantilla"]["es"]), to_lang(sk["plantilla"]["en"]))
            put("gui.forja.habilidad." + sk["id"] + ".ii", sk["nombre"]["es"] + " II", sk["nombre"]["en"] + " II")
            put("gui.forja.habilidad." + sk["id"] + ".ii.efecto", to_lang(sk["mejora"]["plantilla"]["es"]), to_lang(sk["mejora"]["plantilla"]["en"]))
    for m in out["hitos"]:
        put("gui.forja.hito." + m["id"], m["nombre"]["es"], m["nombre"]["en"])
        put("gui.forja.hito." + m["id"] + ".desc", m["condicion"]["es"].replace("%", "%%"), m["condicion"]["en"].replace("%", "%%"))
    for stat in NEW_STATS:
        es, en = STATS[stat][0], STATS[stat][1]
        put("gui.forja.clase.stat." + stat, es.capitalize() + " %s", en.capitalize() + " %s")
    return lang


# --------------------------------------------------------------------------------------------- analysis
def tree_of(out, cid):
    nodes = {n["id"]: dict(n, conexiones=list(n["conexiones"])) for n in out["clases"][cid]["nodos"]}
    for f in out["nodos_forja"]:
        nodes[f["id"]] = dict(f, conexiones=list(f["conexiones"]))
    for n in list(nodes.values()):
        for o in n["conexiones"]:
            if n["id"] not in nodes[o]["conexiones"]:
                nodes[o]["conexiones"].append(n["id"])
    return nodes


def check(out):
    errors = []
    for cid in out["clases"]:
        g = tree_of(out, cid)
        seen, stack = set(), [cid + ".origen"]
        while stack:
            k = stack.pop()
            if k in seen:
                continue
            seen.add(k)
            stack.extend(g[k]["conexiones"])
        if len(seen) != len(g):
            errors.append("%s: %d nodos sin conexión" % (cid, len(g) - len(seen)))
        dist = reach_cost(g, cid + ".origen")
        for n in g.values():
            if n["tipo"] == "clave" and dist[n["id"]] != 11:
                errors.append("%s: la clave %s está a %d puntos" % (cid, n["id"], dist[n["id"]]))
            if not n.get("icono"):
                errors.append("%s: sin icono" % n["id"])
            if n["tipo"] == "clave" and n["nombre"]["es"] not in KEYSTONE_GLYPHS:
                errors.append("%s: la clave %s no tiene dibujo" % (cid, n["nombre"]["es"]))
            for s, _ in n["mods"]:
                if s not in STATS:
                    errors.append("%s: estadística desconocida %s" % (n["id"], s))
    return errors


def reach_cost(g, start):
    dist = {start: 0}
    pq = [(0, start)]
    while pq:
        d, k = heapq.heappop(pq)
        if d > dist.get(k, 1e9):
            continue
        for o in g[k]["conexiones"]:
            nd = d + g[o]["coste"]
            if nd < dist.get(o, 1e9):
                dist[o] = nd
                heapq.heappush(pq, (nd, o))
    return dist


def best_for(g, start, stat, points):
    """Greedy: grow the owned set by the path with the best gain per point for one stat, within the points,
    never taking both keystones of a branch."""
    owned = {start}
    spent, total = 0, 0.0
    sign = -1 if STATS[stat][3] else 1

    def gain(n):
        return sum(sign * v for s, v in n["mods"] if s == stat)
    while True:
        banned = {g[k]["excluye"] for k in owned if g[k].get("excluye")}
        dist, prev = {k: 0 for k in owned}, {}
        pq = [(0, k) for k in owned]
        heapq.heapify(pq)
        while pq:
            d, k = heapq.heappop(pq)
            if d > dist.get(k, 1e9):
                continue
            for o in g[k]["conexiones"]:
                if o in owned or o in banned:
                    continue
                nd = d + g[o]["coste"]
                if nd < dist.get(o, 1e9):
                    dist[o], prev[o] = nd, k
                    heapq.heappush(pq, (nd, o))
        best = None
        for k, d in dist.items():
            if k in owned or d == 0 or spent + d > points:
                continue
            path, cur, gsum = [], k, 0.0
            while cur not in owned:
                path.append(cur)
                gsum += gain(g[cur])
                cur = prev[cur]
            if gsum <= 0:
                continue
            if best is None or gsum / d > best[0]:
                best = (gsum / d, d, gsum, path)
        if best is None:
            break
        spent += best[1]
        total += best[2]
        owned.update(best[3])
    return total, spent


FOCUS = {"guerrero": ["melee_damage", "max_health", "posture", "damage_taken"],
         "asesino": ["melee_damage", "backstab", "dodge_cost", "execute"],
         "tanque": ["max_health", "armor", "damage_taken", "melee_damage"],
         "mago": ["spell_damage", "spell_cooldown", "charge_bonus", "mana_max"],
         "curandero": ["healing", "max_health", "mana_regen", "damage_taken"],
         "arquero": ["projectile_damage", "headshot", "draw_speed", "move_speed"]}


def analysis(out):
    res = {"presupuesto": budget(), "clases": OrderedDict()}
    for cid in out["clases"]:
        g = tree_of(out, cid)
        start = cid + ".origen"
        dist = reach_cost(g, start)
        kinds = {}
        for n in g.values():
            kinds[n["tipo"]] = kinds.get(n["tipo"], 0) + 1
        total_cost = sum(n["coste"] for n in g.values())
        keys = sorted([(dist[n["id"]], n["nombre"]["es"]) for n in g.values() if n["tipo"] == "clave"])
        skills = sorted([(dist[n["id"]], n["nombre"]["es"]) for n in g.values() if n["tipo"] == "habilidad"])
        best = {s: best_for(g, start, s, budget()) for s in FOCUS[cid]}
        # Everything a stat can get from the whole tree, the better keystone of each branch only.
        whole = {}
        for s in FOCUS[cid]:
            sign = -1 if STATS[s][3] else 1
            tot = 0.0
            skipped = set()
            for n in g.values():
                if n.get("excluye") and n["id"] not in skipped:
                    a = sum(sign * v for st, v in n["mods"] if st == s)
                    b = sum(sign * v for st, v in g[n["excluye"]]["mods"] if st == s)
                    skipped.add(n["excluye"] if a >= b else n["id"])
            for n in g.values():
                if n["id"] not in skipped:
                    tot += sum(sign * v for st, v in n["mods"] if st == s)
            whole[s] = tot
        res["clases"][cid] = {"nodos": len(g), "coste_total": total_cost, "tipos": kinds, "claves": keys, "habilidades": skills,
                              "maximos": best, "entero": whole}
    return res


# --------------------------------------------------------------------------------------------- markdown
KIND_ES = {"origen": "origen", "nucleo": "núcleo", "forja": "forja", "menor": "menor", "notable": "notable",
           "clave": "**clave**", "habilidad": "habilidad", "puente": "puente", "cruzado": "cruzado"}


def short(i):
    return i.split(".", 1)[1]


def md_nodes(out):
    L = ["### Región de la forja (igual en los seis árboles)\n", "| Id | Nombre | Tipo | Coste | Efecto | Conexiones |", "|---|---|---|---|---|---|"]
    inner = [f[0] for f in FORGE_INNER]
    for n in out["nodos_forja"]:
        con = ", ".join("`%s`" % short(c) for c in n["conexiones"])
        if n["id"] in inner:
            con = (con + ", " if con else "") + "las dos puertas del núcleo que tiene al lado"
        L.append("| `%s` | %s | %s | %d | %s | %s |" % (short(n["id"]), n["nombre"]["es"], KIND_ES[n["tipo"]], n["coste"], n["efecto"], con))
    L.append("")
    for cid, c in out["clases"].items():
        r = c["regiones"]
        L.append("### %s\n" % c["nombre"]["es"])
        L.append("Base: %s. Ramas: A %s, B %s, C %s. Sendas: 1 %s (puente al %s), 2 %s (puente al %s), 3 %s (puente al %s).\n" % (
            CLASSES[cid]["base"][0], r["A"]["es"], r["B"]["es"], r["C"]["es"],
            r["S1"]["es"], CLASS_NAMES[BRIDGES[cid][0]][0], r["S2"]["es"], CLASS_NAMES[BRIDGES[cid][1]][0],
            r["S3"]["es"], CLASS_NAMES[BRIDGES[cid][2]][0]))
        for slot, sk in c["habilidades"].items():
            L.append("- **%s** (%s): %s; espera %d s. **II**: %s; espera %d s." % (
                sk["nombre"]["es"], slot, render(sk["plantilla"]["es"], sk["numeros"]), sk["espera"],
                render(sk["mejora"]["plantilla"]["es"], sk["mejora"]["numeros"]), sk["mejora"]["espera"]))
        L.append("")
        L.append("| Id | Nombre | Tipo | Coste | Efecto | Conecta con |")
        L.append("|---|---|---|---|---|---|")
        for n in c["nodos"]:
            con = ", ".join("`%s`" % short(x) for x in n["conexiones"])
            extra = ""
            if n["tipo"] == "clave":
                extra = " *(gana: %s; precio: %s; excluye a `%s`)*" % (n["gana"], n["precio"], short(n["excluye"]))
            if n["tipo"] in ("puente", "cruzado"):
                extra = " *(del %s)*" % CLASS_NAMES[n["destino"]][0]
            L.append("| `%s` | %s | %s | %d | %s%s | %s |" % (short(n["id"]), n["nombre"]["es"], KIND_ES[n["tipo"]], n["coste"], n["efecto"], extra, con))
        L.append("")
    return "\n".join(L)


def md_analysis(res):
    b = res["presupuesto"]
    L = ["Presupuesto en el tope: **%d puntos** (%d de nivel + %d de hitos)." % (b, level_points(MAX_LEVEL), milestone_points()), ""]
    L.append("| Clase | Nodos | Coste del árbol entero | %% del árbol con %d puntos | Claves (puntos para llegar) |" % b)
    L.append("|---|---|---|---|---|")
    for cid, r in res["clases"].items():
        keys = ", ".join("%s %d" % (n, d) for d, n in r["claves"])
        L.append("| %s | %d | %d | %d %% | %s |" % (CLASS_NAMES[cid][0], r["nodos"], r["coste_total"], round(100 * b / r["coste_total"]), keys))
    L.append("")
    L.append("Puntos hasta cada habilidad (desde el origen, por el camino más barato):\n")
    for cid, r in res["clases"].items():
        L.append("- %s: %s." % (CLASS_NAMES[cid][0], ", ".join("%s %d" % (n, d) for d, n in r["habilidades"])))
    L.append("")
    L.append("Lo que el árbol suma a una estadística (sin la base de la clase): con los %d puntos puestos solo en ella, y el árbol "
             "entero (con la mejor clave de cada rama, porque las dos claves de una rama se excluyen):\n" % b)
    L.append("| Clase | Estadística | Con %d puntos | Puntos usados | Árbol entero |" % b)
    L.append("|---|---|---|---|---|")
    for cid, r in res["clases"].items():
        for s, (tot, sp) in r["maximos"].items():
            unit = STATS[s][2]
            sign = "−" if STATS[s][3] else "+"

            def show(v):
                return sign + ("%s %%" % fmt_num(v * 100) if unit == "pct" else fmt_num(v))
            L.append("| %s | %s | %s | %d | %s |" % (CLASS_NAMES[cid][0], STATS[s][0], show(tot), sp, show(r["entero"][s])))
    L.append("")
    L.append("Puntos por nivel (de nivel en el tope: %d):\n" % level_points(MAX_LEVEL))
    marks = [1, 2, 3, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50]
    L.append("| Nivel | " + " | ".join(str(m) for m in marks) + " |")
    L.append("|---" * (len(marks) + 1) + "|")
    L.append("| Puntos de nivel | " + " | ".join(str(level_points(m)) for m in marks) + " |")
    L.append("| Hitos que se pueden gastar como mucho | " + " | ".join(str(min(milestone_points(), m * MILESTONE_CAP_PER_LEVEL)) for m in marks) + " |")
    total = [0]
    for lv in range(1, MAX_LEVEL):
        total.append(total[-1] + FIRST_STEP + STEP_GROWTH * (lv - 1))
    L.append("| Experiencia total | " + " | ".join(str(total[m - 1]) for m in marks) + " |")
    return "\n".join(L)


def write_md(out, res):
    path = os.path.join(DOCS, "ARBOLES.md")
    with open(path, "r", encoding="utf-8", newline="") as f:
        text = f.read()
    for tag, body in (("NODOS", md_nodes(out)), ("CALCULOS", md_analysis(res))):
        a, b = "<!-- %s:INICIO -->" % tag, "<!-- %s:FIN -->" % tag
        i, j = text.index(a) + len(a), text.index(b)
        text = text[:i] + "\n" + body + "\n" + text[j:]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


# --------------------------------------------------------------------------------------------- drawing
_ICONS = {}
CLIENT_JAR = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "fabric-loom", "26.2", "minecraft-client.jar")


def load_icon(icono):
    """A node's icon as a 16x16 RGBA image, from the mod's textures or vanilla's jar; None for a class emblem (a forged
    weapon the game assembles) or a texture that is not there."""
    if icono in _ICONS:
        return _ICONS[icono]
    import io
    import zipfile
    from PIL import Image
    ns, _, name = icono.partition(":")
    found = None
    assets = os.path.join(ROOT, "src", "main", "resources", "assets", "forja", "textures")
    if ns == "sprite":
        paths = [os.path.join(assets, "gui", "arbol", name + ".png")]
    elif ns == "forja":
        paths = [os.path.join(assets, "item", name + ".png")]
    else:
        paths = []
    for path in paths:
        if os.path.exists(path):
            found = Image.open(path).convert("RGBA").crop((0, 0, 16, 16))
    if found is None and ns == "minecraft" and os.path.exists(CLIENT_JAR):
        with zipfile.ZipFile(CLIENT_JAR) as jar:
            for sub in ("item", "block"):
                for suffix in ("", "_top", "_side", "_front"):
                    entry = "assets/minecraft/textures/%s/%s%s.png" % (sub, name, suffix)
                    if entry in jar.namelist():
                        found = Image.open(io.BytesIO(jar.read(entry))).convert("RGBA").crop((0, 0, 16, 16))
                        break
                if found is not None:
                    break
    _ICONS[icono] = found
    return found


def draw(out, cid, path, lit, caption, res):
    from PIL import Image, ImageDraw, ImageFont
    W = H = 3000
    S = 118.0
    cx, cy = W / 2, H / 2 + 60
    img = Image.new("RGB", (W, H), (22, 20, 26))
    d = ImageDraw.Draw(img)
    F = "C:/Windows/Fonts/"
    f_title = ImageFont.truetype(F + "arialbd.ttf", 64)
    f_sub = ImageFont.truetype(F + "arial.ttf", 30)
    f_name = ImageFont.truetype(F + "arialbd.ttf", 22)
    f_small = ImageFont.truetype(F + "arial.ttf", 16)
    f_branch = ImageFont.truetype(F + "arialbd.ttf", 40)
    g = tree_of(out, cid)
    c = out["clases"][cid]
    inner = [f[0] for f in FORGE_INNER]

    def P(n):
        return cx + n["x"] * S, cy - n["y"] * S

    def hexc(h):
        h = h.lstrip("#")
        return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    ccol, fcol, gold = hexc(c["color"]), hexc(CLASS_COLORS["forja"]), (255, 205, 70)

    for sp, ang in SPOKES:
        if sp in ("A", "B", "C"):
            name = "Rama %s · %s" % (sp, c["regiones"][sp]["es"])
            x, y = polar(5.4, ang + (24 if sp != "A" else 26))
        else:
            name = "%s → %s" % (c["regiones"][sp]["es"], CLASS_NAMES[BRIDGES[cid][int(sp[1]) - 1]][0])
            x, y = polar(10.6, ang + (9 if sp != "S2" else 0))
        px, py = cx + x * S, cy - y * S
        tw = d.textlength(name, font=f_branch)
        if sp == "S2":
            px, py = cx + 110 + tw / 2, cy + 9.05 * S
        d.text((px - tw / 2, py - 20), name, font=f_branch, fill=(200, 196, 210))
    done = set()
    for n in g.values():
        for o in n["conexiones"]:
            key = tuple(sorted((n["id"], o)))
            if key in done:
                continue
            done.add(key)
            on = n["id"] in lit and o in lit
            d.line([P(n), P(g[o])], fill=gold if on else (88, 84, 98), width=9 if on else 4)
    for n in g.values():
        x, y = P(n)
        t = n["tipo"]
        on = n["id"] in lit
        col = fcol if n["id"].startswith("forja.") else ccol
        if t in ("puente", "cruzado"):
            col = hexc(CLASS_COLORS[n["destino"]])
        r = {"origen": 62, "nucleo": 26, "forja": 30, "menor": 21, "notable": 36, "clave": 52, "habilidad": 42, "puente": 38, "cruzado": 28}[t]
        outline = gold if on else (255, 255, 255)
        if t == "clave":
            d.polygon([(x + r * math.cos(math.radians(a)), y + r * math.sin(math.radians(a))) for a in range(0, 360, 60)], fill=col, outline=outline, width=6)
        elif t == "puente":
            d.polygon([(x, y - r), (x + r, y), (x, y + r), (x - r, y)], fill=col, outline=outline, width=5)
        elif t == "habilidad":
            d.rounded_rectangle([x - r, y - r, x + r, y + r], radius=12, fill=col, outline=outline, width=6)
        else:
            fill = col if t != "menor" else tuple(int(v * 0.65) for v in col)
            d.ellipse([x - r, y - r, x + r, y + r], fill=fill, outline=gold if on else (230, 230, 230), width=6 if on else (4 if t == "notable" else 2))
        icon = load_icon(n["icono"])
        if icon is not None:
            size = int(r * (1.7 if t in ("clave", "habilidad") else 1.5))
            img.paste(icon.resize((size, size), Image.NEAREST), (int(x - size / 2), int(y - size / 2)), icon.resize((size, size), Image.NEAREST))
            if t == "habilidad" and n["habilidad"].endswith("2"):
                d.text((x + r * 0.2, y + r * 0.25), "II", font=f_name, fill=(255, 215, 90), stroke_width=3, stroke_fill=(20, 16, 8))
        elif t == "habilidad":
            slot = n["habilidad"]
            label = slot[0] + ("II" if slot.endswith("2") else "")
            tw = d.textlength(label, font=f_name)
            d.text((x - tw / 2, y - 13), label, font=f_name, fill=(20, 20, 20))
        if t == "origen":
            tw = d.textlength(c["nombre"]["es"], font=f_name)
            d.text((x - tw / 2, y - 12), c["nombre"]["es"], font=f_name, fill=(15, 15, 15))
    for n in g.values():
        x, y = P(n)
        t = n["tipo"]
        if t in ("notable", "clave", "habilidad", "puente", "forja") or (t == "cruzado" and n["gancho"]):
            name, font = n["nombre"]["es"], f_name
            off = {"clave": 60, "habilidad": 50, "puente": 46, "notable": 42, "forja": 36, "cruzado": 34}[t]
        elif t in ("menor", "nucleo", "cruzado"):
            s, v = n["mods"][0]
            name, font = fmt_mod(s, v), f_small
            off = 26 if t == "menor" else 30
        else:
            continue
        tw = d.textlength(name, font=font)
        tx, ty = x - tw / 2, y + off
        if n["id"] in inner and abs(n["y"]) < 0.5:
            ty = y - off - font.size - 4
        d.rectangle([tx - 3, ty - 1, tx + tw + 3, ty + font.size + 3], fill=(22, 20, 26))
        d.text((tx, ty), name, font=font, fill=gold if n["id"] in lit else ((255, 230, 160) if t == "clave" else (235, 232, 240)))
    r = res["clases"][cid]
    b = res["presupuesto"]
    d.text((60, 40), "Árbol del %s" % c["nombre"]["es"], font=f_title, fill=ccol)
    d.text((60, 120), "%d nodos · el árbol entero cuesta %d puntos · en el nivel %d con todos los hitos hay %d (%d de nivel + %d de hitos): el %d %%" % (
        r["nodos"], r["coste_total"], MAX_LEVEL, b, level_points(MAX_LEVEL), milestone_points(), round(100 * b / r["coste_total"])), font=f_sub, fill=(210, 206, 220))
    d.text((60, 160), "Dorado: %s." % caption, font=f_sub, fill=gold)
    lx, ly = 60, H - 300
    items = [("origen", "Origen: la clase y la habilidad V"), ("nucleo", "Núcleo: 6 puertas (1 punto)"), ("forja", "Forja: igual en todas las clases (1)"),
             ("menor", "Menor: +3 % (1)"), ("notable", "Notable (1)"), ("clave", "Clave: cambia el estilo, con precio (2); una por rama"),
             ("habilidad", "Habilidad: B y N, y sus mejoras II (2-3)"), ("puente", "Puente a otra clase y sus nodos (2 cada uno)")]
    for i, (t, txt) in enumerate(items):
        x, y = lx + 40 + (i // 4) * 1900, ly + (i % 4) * 62
        col = fcol if t == "forja" else ccol
        if t == "clave":
            d.polygon([(x + 26 * math.cos(math.radians(a)), y + 26 * math.sin(math.radians(a))) for a in range(0, 360, 60)], fill=col, outline=(255, 255, 255))
        elif t == "puente":
            d.polygon([(x, y - 24), (x + 24, y), (x, y + 24), (x - 24, y)], fill=hexc(CLASS_COLORS[BRIDGES[cid][0]]), outline=(255, 255, 255))
        elif t == "habilidad":
            d.rounded_rectangle([x - 22, y - 22, x + 22, y + 22], radius=8, fill=col, outline=(255, 255, 255), width=3)
        else:
            rr = {"origen": 26, "nucleo": 18, "forja": 20, "menor": 14, "notable": 22}[t]
            d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=col if t != "menor" else tuple(int(v * 0.65) for v in col), outline=(230, 230, 230), width=2)
        d.text((x + 44, y - 18), txt, font=f_sub, fill=(220, 216, 228))
    img.save(path, optimize=True)


def example_build(out, cid, points):
    """A sample end-game build for the picture: everything the points reach, best keystone first, by a fixed order."""
    g = tree_of(out, cid)
    owned = {cid + ".origen"}
    spent = 0
    # Skip one keystone per branch (the second), and buy the cheapest reachable node until the points run out,
    # preferring the class's own nodes over bridges.
    skip = {n["id"] for n in g.values() if n["tipo"] == "clave" and n["id"].endswith("2.5")}
    while True:
        frontier = [n for k in owned for n in (g[o] for o in g[k]["conexiones"]) if n["id"] not in owned and n["id"] not in skip]
        if not frontier:
            break
        frontier.sort(key=lambda n: (n["tipo"] in ("puente", "cruzado"), n["coste"], n["id"]))
        n = frontier[0]
        if spent + n["coste"] > points:
            cheaper = [m for m in frontier if spent + m["coste"] <= points]
            if not cheaper:
                break
            n = cheaper[0]
        owned.add(n["id"])
        spent += n["coste"]
    return owned, spent


def main():
    out = build()
    errs = check(out)
    if errs:
        print("\n".join(errs))
        sys.exit(1)
    res = analysis(out)
    text = json.dumps(out, ensure_ascii=False, indent=1)
    for path in (RESOURCE, os.path.join(DOCS, "arboles.json")):
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(text + "\n")
    write_md(out, res)
    print("presupuesto", res["presupuesto"])
    for cid, r in res["clases"].items():
        print(cid, r["nodos"], "nodos, coste", r["coste_total"], r["tipos"])
        print("   maximos", {k: (round(v[0], 3), v[1]) for k, v in r["maximos"].items()}, "entero", {k: round(v, 3) for k, v in r["entero"].items()})
    if "--imagenes" in sys.argv:
        dest = sys.argv[sys.argv.index("--imagenes") + 1]
        os.makedirs(dest, exist_ok=True)
        for cid in ("guerrero", "mago"):
            lit, cost = example_build(out, cid, res["presupuesto"])
            caption = "una partida en el nivel %d con todos los hitos: %d puntos gastados, una clave por rama" % (MAX_LEVEL, cost)
            p = os.path.join(dest, "arbol_%s.png" % cid)
            draw(out, cid, p, lit, caption, res)
            print(p, os.path.getsize(p) // 1024, "KB")


if __name__ == "__main__":
    main()
