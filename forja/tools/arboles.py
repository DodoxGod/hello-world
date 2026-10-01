"""The big class trees (docs/ARBOLES.md): every node of every class, as data.

Run from forja/:
    python tools/arboles.py                 -> writes docs/arboles.json and the node lists in docs/ARBOLES.md
    python tools/arboles.py --imagenes DIR  -> also draws the Guerrero and Mago trees into DIR

The JSON is what the build step turns into Java (one generated class of nodes), so every number lives here
once: a node's text is a template whose numbers are its "numeros", the same way Talent.numbers works today.
"""
import json
import math
import os
import re
import sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DOCS = os.path.join(ROOT, "docs")

# --------------------------------------------------------------------------------------------- stats
# id -> (label, unit, lower_is_better). unit: pct (shown as %), flat, ticks, centesimas, puntos.
STATS = OrderedDict([
    ("max_health", ("vida", "pct", False)),
    ("move_speed", ("velocidad", "pct", False)),
    ("melee_damage", ("daño cuerpo a cuerpo", "pct", False)),
    ("armor", ("armadura", "flat", False)),
    ("toughness", ("dureza de armadura", "flat", False)),
    ("knockback", ("resistencia al empuje", "pct", False)),
    ("jump", ("salto", "pct", False)),
    ("sneak_speed", ("velocidad agachado", "pct", False)),
    ("fall_damage", ("daño de caída", "pct", True)),
    ("stamina_max", ("estamina máxima", "pct", False)),
    ("stamina_regen", ("regeneración de estamina", "pct", False)),
    ("stamina_cost", ("coste de estamina", "pct", True)),
    ("dodge_distance", ("distancia de esquiva", "pct", False)),
    ("dodge_cooldown", ("espera de esquiva", "pct", True)),
    ("dodge_cost", ("coste de esquiva", "pct", True)),
    ("dodge_iframes", ("invulnerabilidad de esquiva", "ticks", False)),
    ("parry_window", ("ventana de parada", "ticks", False)),
    ("block_cost", ("estamina al parar", "pct", True)),
    ("posture", ("daño de postura", "pct", False)),
    ("damage_taken", ("daño recibido", "pct", True)),
    ("magic_taken", ("daño mágico recibido", "pct", True)),
    ("fire_taken", ("daño de fuego recibido", "pct", True)),
    ("backstab", ("daño por la espalda", "pct", False)),
    ("counter", ("contraataques", "pct", False)),
    ("staggered_bonus", ("daño a aturdidos", "pct", False)),
    ("finisher", ("remates", "pct", False)),
    ("execute", ("daño a enemigos bajo el 35 %", "pct", False)),
    ("projectile_damage", ("daño de proyectiles", "pct", False)),
    ("headshot", ("tiros a la cabeza", "pct", False)),
    ("draw_speed", ("tensado", "pct", False)),
    ("arrow_speed", ("velocidad de flecha", "pct", False)),
    ("spell_damage", ("daño de hechizos", "pct", False)),
    ("spell_cooldown", ("espera de hechizos", "pct", True)),
    ("spell_charge", ("tiempo de carga", "pct", True)),
    ("charge_bonus", ("bono de la carga completa", "pct", False)),
    ("healing", ("curación", "pct", False)),
    ("mana_max", ("maná máximo", "pct", False)),
    ("mana_regen", ("regeneración de maná", "pct", False)),
    ("spell_cost", ("coste de maná", "pct", True)),
    # forging (all exist today except the three marked NEW)
    ("forge_window", ("ventana del golpe perfecto", "centesimas", False)),
    ("potential", ("potencial al forjar", "puntos", False)),
    ("repair", ("reparación por lingote", "pct", False)),
    ("upgrade_bonus", ("% por ingrediente de mejora", "puntos", False)),
    ("smith_weapon", ("daño con martillo, mazo, pico y hacha", "pct", False)),
    ("foundry_speed", ("velocidad de tu fundición", "pct", False)),          # NEW
    ("capacity", ("carga de lo que forjas", "puntos", False)),               # NEW
    ("assembler_potential", ("potencial de lo que monta tu montadora", "puntos", False)),  # NEW
])
NEW_STATS = ["foundry_speed", "capacity", "assembler_potential"]

# What a small node gives ("+3 % de una estadística", Andy), and the equivalent for stats that are not a %.
SMALL = {"armor": 1, "toughness": 0.5, "parry_window": 1, "knockback": 0.05, "mana_regen": 0.20}


def small_value(stat):
    if stat in SMALL:
        return SMALL[stat]
    return -0.03 if STATS[stat][2] else 0.03


def fmt_num(v):
    if abs(v - round(v)) < 1e-9:
        return str(int(round(v)))
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return s.replace(".", ",")


def fmt_mod(stat, v):
    label, unit, _ = STATS[stat]
    sign = "+" if v > 0 else "−"
    a = abs(v)
    if unit == "pct":
        return "%s %s%s %%" % (label, sign, fmt_num(a * 100))
    if unit == "ticks":
        return "%s %s%s tick%s" % (label, sign, fmt_num(a), "" if a == 1 else "s")
    if unit == "centesimas":
        return "%s %s%s" % (label, sign, fmt_num(a))
    return "%s %s%s" % (label, sign, fmt_num(a))


def render(tpl, nums):
    def rep(m):
        i = int(m.group(1))
        if m.group(2):
            return fmt_num(nums[i] * 100) + " %"
        return fmt_num(nums[i])
    return re.sub(r"\{(\d+)(%?)\}", rep, tpl)


# --------------------------------------------------------------------------------------------- node makers
def S(stat, v=None):
    return {"tipo": "menor", "mods": [[stat, small_value(stat) if v is None else v]]}


def N(nombre, texto=None, nums=(), mods=()):
    return {"tipo": "notable", "nombre": nombre, "texto": texto, "numeros": list(nums), "mods": [list(m) for m in mods]}


def K(nombre, texto, nums=(), mods=(), gana="", precio=""):
    return {"tipo": "clave", "nombre": nombre, "texto": texto, "numeros": list(nums), "mods": [list(m) for m in mods],
            "gana": gana, "precio": precio}


def H(slot):
    """A skill node: 'B', 'N' (the skill itself) or 'V2', 'B2', 'N2' (its upgrade)."""
    return {"tipo": "habilidad", "slot": slot}


COST = {"origen": 0, "nucleo": 1, "forja": 1, "menor": 1, "notable": 1, "clave": 2, "puente": 2, "cruzado": 2}
SKILL_COST = {"B": 2, "N": 3, "V2": 2, "B2": 2, "N2": 2, "J": 2, "J2": 2}

# --------------------------------------------------------------------------------------------- classes
CLASSES = OrderedDict()
CLASS_COLORS = {"guerrero": "#C0463A", "asesino": "#8A6BC8", "tanque": "#8C99A6", "mago": "#4F7FE8",
                "curandero": "#5CC46A", "arquero": "#8DBF4A", "forja": "#E8923A"}
CLASS_NAMES = {"guerrero": "Guerrero", "asesino": "Asesino", "tanque": "Tanque", "mago": "Mago",
               "curandero": "Curandero", "arquero": "Arquero"}

# Bridges: a 3-regular graph, so every class is reached from exactly three others and reaches three.
BRIDGES = {
    "guerrero": ["tanque", "asesino", "arquero"],
    "asesino": ["guerrero", "arquero", "mago"],
    "tanque": ["guerrero", "curandero", "mago"],
    "mago": ["asesino", "curandero", "tanque"],
    "curandero": ["tanque", "mago", "arquero"],
    "arquero": ["asesino", "guerrero", "curandero"],
}

# What a bridge into a class gives: the bridge node, a small and a notable, all at 2 points. The same package
# whoever crosses, and none of it touches the damage factors (no spell damage into Guerrero/Asesino, etc.).
BRIDGE_PACKAGES = {
    "guerrero": [("Puente al Guerrero", S("stamina_regen", 0.05)), (None, S("posture", 0.05)),
                 ("Réplica menor", N("Réplica menor", "una parada perfecta te devuelve {0} de estamina", [10]))],
    "asesino": [("Puente al Asesino", S("move_speed", 0.03)), (None, S("dodge_cost", -0.08)),
                ("Puñalada menor", N("Puñalada menor", mods=[["backstab", 0.20]]))],
    "tanque": [("Puente al Tanque", S("max_health", 0.05)), (None, S("armor", 1)),
               ("Represalia menor", N("Represalia menor", "quien golpea tu escudo levantado recibe {0} de daño", [2]))],
    "mago": [("Puente al Mago", S("mana_max", 0.10)), (None, S("mana_regen", 0.50)),
             ("Barrera menor", N("Barrera menor", mods=[["magic_taken", -0.15]]))],
    "curandero": [("Puente al Curandero", S("healing", 0.10)), (None, S("stamina_regen", 0.05)),
                  ("Vendaje", N("Vendaje", "cada {0} s sin recibir daño recuperas {1} de vida", [8, 1]))],
    "arquero": [("Puente al Arquero", S("draw_speed", 0.05)), (None, S("projectile_damage", 0.04)),
                ("Ojo de halcón menor", N("Ojo de halcón menor", mods=[["headshot", 0.15]]))],
}

# ---- Guerrero
CLASSES["guerrero"] = {
    "base": "vida +10 %, daño c/c +5 %, estamina máx. +20 %, regeneración +10 %, postura +15 %, parada +1 tick",
    "habilidades": {
        "V": ("Grito de guerra", "recupera {0} de estamina; tú y los jugadores a {1} bloques ganáis Fuerza I {2} s", [40, 8, 8], 45),
        "V2": ("Grito de guerra II", "recupera {0} de estamina (antes 40); radio {1} (antes 8); Fuerza I {2} s (antes 8)", [60, 12, 10], 45),
        "B": ("Postura de hierro", "{0} s de Resistencia II y Lentitud I", [6], 50),
        "B2": ("Postura de hierro II", "{0} s (antes 6), sin Lentitud, y al acabar recuperas {1} de estamina", [8, 20], 50),
        "N": ("Torbellino", "giras {0} s golpeando todo a {1} bloques con el {2%} del daño de tu arma y {3} de postura; cuesta {4} de estamina", [1, 3, 0.80, 30, 30], 30),
        "N2": ("Torbellino II", "dos vueltas ({0} s) y postura +{1%}", [1.5, 0.50], 30),
    },
    "puertas": [S("stamina_max"), S("stamina_regen"), S("block_cost"), S("max_health"), S("posture"), S("melee_damage")],
    "ramas": {
        "A": {"nombre": "Aguante",
              "tronco": [S("stamina_regen"), S("max_health"), S("stamina_max"),
                         N("Segundo aliento", "regeneración de estamina +{0%}; por debajo del {1%} de estamina se regenera el doble", [0.15, 0.25])],
              "caminos": [
                  {"nombre": "Piel curtida",
                   "nodos": [S("max_health"), S("knockback"),
                             N("Inquebrantable", "por debajo del {0%} de vida, daño recibido −{1%}", [0.30, 0.20]),
                             S("max_health"),
                             K("Muro de carne", None, mods=[["max_health", 0.20], ["move_speed", -0.08], ["dodge_cost", 0.25]],
                               gana="vida +20 %", precio="velocidad −8 %, coste de esquiva +25 %")],
                   "lados": [S("stamina_max"), S("damage_taken")]},
                  {"nombre": "Fondo",
                   "nodos": [S("stamina_cost"), S("stamina_regen"),
                             N("Sin resuello", mods=[["stamina_cost", -0.15]]),
                             S("stamina_max"),
                             K("Adrenalina", "cada golpe que recibes te devuelve {0} de estamina; regeneración de estamina +{1%}; pero estamina máxima −{2%}", [6, 0.25, 0.25],
                               gana="6 de estamina por golpe recibido, regeneración +25 %", precio="estamina máxima −25 %")],
                   "lados": [S("stamina_regen"), S("move_speed")]},
              ]},
        "B": {"nombre": "Guardia",
              "tronco": [S("parry_window"), S("block_cost"), S("armor"),
                         N("Réplica", "una parada perfecta devuelve {0} de estamina y tu siguiente golpe en {1} s hace +{2%}", [15, 3, 0.30])],
              "caminos": [
                  {"nombre": "Muro de escudo",
                   "nodos": [S("armor"), S("block_cost"), N("Parada firme", mods=[["block_cost", -0.25]]), S("toughness"),
                             K("Fortaleza", "con escudo levantado no recibes empuje y parar cuesta −{0%} de estamina; pero con escudo en la otra mano no puedes esquivar", [0.40],
                               gana="sin empuje, parar −40 %", precio="sin esquiva con escudo")],
                   "lados": [S("knockback"), S("max_health")]},
                  {"nombre": "Contragolpe",
                   "nodos": [S("parry_window"), S("counter"),
                             N("Filo de vuelta", "una parada perfecta con arma hace {0} de daño de postura al atacante", [20]),
                             S("counter"),
                             K("Duelista", "ventana de parada +{0} ticks y las paradas perfectas no gastan estamina; pero una parada no perfecta cuesta el doble", [3],
                               gana="parada +3 ticks, perfectas gratis", precio="paradas fallidas ×2 de estamina")],
                   "lados": [S("posture"), S("stamina_regen")]},
              ]},
        "C": {"nombre": "Quebranto",
              "tronco": [S("posture"), S("melee_damage"), S("posture"), N("Golpe pesado", mods=[["posture", 0.20]])],
              "caminos": [
                  {"nombre": "Rompeguardias",
                   "nodos": [S("staggered_bonus"), S("posture"), N("Rompeguardias", mods=[["staggered_bonus", 0.20]]), S("staggered_bonus"),
                             K("Martillo de guerra", "daño de postura +{0%} y tus aturdimientos duran +{1} s; pero contra lo que no está aturdido haces −{2%}", [0.40, 1, 0.10],
                               gana="postura +40 %, aturdimiento +1 s", precio="−10 % a no aturdidos")],
                   "lados": [S("melee_damage"), S("max_health")]},
                  {"nombre": "Verdugo",
                   "nodos": [S("finisher"), S("melee_damage"), N("Verdugo", mods=[["finisher", 0.30]]), S("execute"),
                             K("Sed de sangre", "matar cuerpo a cuerpo cura el {0%} de tu vida máxima; pero no regeneras vida de forma natural", [0.10],
                               gana="10 % de vida por muerte", precio="sin regeneración natural")],
                   "lados": [S("finisher"), S("melee_damage")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": "Senda del grito", "nodos": [S("stamina_regen"), H("V2"), S("max_health"), S("stamina_max")]},
        "S2": {"nombre": "Senda del hierro", "nodos": [S("posture"), H("B"), S("damage_taken"), S("max_health")], "lado": H("B2")},
        "S3": {"nombre": "Senda del torbellino",
               "nodos": [S("posture"), N("Carga brutal", "un golpe cargado al máximo aturde {0} s a los monstruos normales", [0.5]), S("stamina_regen"), H("N")],
               "lado": H("N2")},
    },
}

# ---- Asesino
CLASSES["asesino"] = {
    "base": "vida −30 %, daño c/c +10 %, velocidad +5 %, estamina +30 %, esquiva: distancia +35 %, espera −30 %, coste −20 %, +2 ticks",
    "habilidades": {
        "V": ("Paso sombrío", "{0} s de invisibilidad y Velocidad II; el siguiente golpe c/c hace +{1%}", [4, 0.60], 30),
        "V2": ("Paso sombrío II", "{0} s (antes 4) y el golpe +{1%} (antes 60 %)", [6, 0.80], 30),
        "B": ("Marca de muerte", "el monstruo que miras (hasta {0} bloques) brilla {1} s y recibe +{2%} de tus golpes", [16, 10, 0.30], 40),
        "B2": ("Marca de muerte II", "+{0%} (antes 30 %); si el marcado muere, la espera baja a la mitad", [0.40], 40),
        "N": ("Abanico de dagas", "lanzas {0} dagas en abanico de {1}°: {2} de daño cada una y Veneno I {3} s", [5, 60, 4, 3], 25),
        "N2": ("Abanico de dagas II", "{0} dagas y Veneno II", [7], 25),
    },
    "puertas": [S("dodge_cost"), S("stamina_max"), S("backstab"), S("move_speed"), S("sneak_speed"), S("melee_damage")],
    "ramas": {
        "A": {"nombre": "Sombra",
              "tronco": [S("dodge_cost"), S("dodge_cooldown"), S("dodge_distance"),
                         N("Danza", mods=[["dodge_iframes", 1], ["dodge_cooldown", -0.10]])],
              "caminos": [
                  {"nombre": "Contraataque",
                   "nodos": [S("counter"), S("dodge_cost"), N("Contraataque", mods=[["counter", 0.30]]), S("counter"),
                             K("Filo del viento", "una esquiva que evita un golpe recarga la esquiva y tu siguiente golpe cuenta como contraataque; pero armadura −{0}", [4],
                               gana="esquiva recargada y contraataque seguro", precio="armadura −4")],
                   "lados": [S("stamina_max"), S("dodge_distance")]},
                  {"nombre": "Espejismo",
                   "nodos": [S("dodge_distance"), S("dodge_cooldown"),
                             N("Espejismo", "al esquivar, los monstruos a {0} bloques pierden tu rastro {1} s (cada {2} s)", [6, 1, 8]),
                             S("dodge_cost"),
                             K("Sin sombra", "esquivar no cuesta estamina; pero cada esquiva te quita {0} de vida (nunca por debajo de 1)", [1],
                               gana="esquivas gratis", precio="1 de vida por esquiva")],
                   "lados": [S("move_speed"), S("fall_damage")]},
              ]},
        "B": {"nombre": "Filo",
              "tronco": [S("backstab"), S("melee_damage"), S("execute"), N("Puñalada", mods=[["backstab", 0.30]])],
              "caminos": [
                  {"nombre": "Ejecutor",
                   "nodos": [S("execute"), S("melee_damage"), N("Ejecutor", mods=[["execute", 0.25]]), S("execute"),
                             K("Golpe de gracia", "un golpe c/c a un monstruo normal por debajo del {0%} de vida lo mata (ni jefes, ni campeones); pero haces −{1%} a enemigos por encima del {2%}", [0.15, 0.15, 0.50],
                               gana="remate instantáneo bajo el 15 %", precio="−15 % a enemigos sanos")],
                   "lados": [S("backstab"), S("move_speed")]},
                  {"nombre": "Sangría",
                   "nodos": [S("melee_damage"), S("backstab"),
                             N("Golpe letal", "matar c/c devuelve {0} de estamina y da Velocidad II {1} s", [20, 3]),
                             S("stamina_regen"),
                             K("Frenesí", "cada muerte c/c en {0} s da +{1%} de daño c/c (hasta {2} veces; se pierde al recibir un golpe); pero vida máxima −{3%}", [5, 0.08, 4, 0.10],
                               gana="hasta +32 % encadenando", precio="vida −10 %, se pierde al ser golpeado")],
                   "lados": [S("execute"), S("stamina_regen")]},
              ]},
        "C": {"nombre": "Sigilo",
              "tronco": [S("sneak_speed"), S("fall_damage"), S("move_speed"),
                         N("Paso quedo", "agachado, los monstruos que no te han visto no te detectan a más de {0} bloques; velocidad agachado +{1%}", [8, 0.20])],
              "caminos": [
                  {"nombre": "Acróbata",
                   "nodos": [S("jump"), S("fall_damage"), N("Acróbata", mods=[["fall_damage", -0.30], ["jump", 0.10]]), S("move_speed"),
                             K("Funámbulo", "tras caer de más de {0} bloques, el siguiente golpe en {1} s hace +{2%}; pero resistencia al empuje −{3%}", [3, 2, 0.40, 0.30],
                               gana="+40 % al caer sobre el enemigo", precio="empuje recibido +30 %")],
                   "lados": [S("jump"), S("stamina_max")]},
                  {"nombre": "Evasión",
                   "nodos": [S("dodge_distance"), S("move_speed"),
                             N("Evasión", "{0%} de probabilidad de que un proyectil no te haga nada", [0.12]),
                             S("move_speed"),
                             K("Fantasma", "Paso sombrío dura el doble y el primer golpe no rompe la invisibilidad; pero su espera +{0%}", [0.50],
                               gana="Paso sombrío ×2 y sigue invisible", precio="espera de Paso sombrío +50 %")],
                   "lados": [S("fall_damage"), S("sneak_speed")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": "Senda de la sombra", "nodos": [S("stamina_max"), H("V2"), S("dodge_cost"), S("move_speed")]},
        "S2": {"nombre": "Senda de la marca", "nodos": [S("backstab"), H("B"), S("execute"), S("move_speed")], "lado": H("B2")},
        "S3": {"nombre": "Senda del veneno",
               "nodos": [S("sneak_speed"), N("Veneno en la hoja", "tus golpes por la espalda envenenan (Veneno I {0} s)", [3]), S("dodge_distance"), H("N")],
               "lado": H("N2")},
    },
}

# ---- Tanque
CLASSES["tanque"] = {
    "base": "vida +60 %, armadura +2, empuje +30 %, velocidad −12 %, esquiva −35 %, coste de esquiva +20 %, estamina al parar −25 %",
    "habilidades": {
        "V": ("Provocar", "los monstruos hostiles a {0} bloques te toman como objetivo; Resistencia I {1} s", [10, 6], 25),
        "V2": ("Provocar II", "radio {0} (antes 10), Resistencia I {1} s (antes 6) y {2} de absorción", [14, 9, 4], 25),
        "B": ("Baluarte", "{0} s de Resistencia II para ti y Resistencia I para los jugadores a {1} bloques", [8, 6], 60),
        "B2": ("Baluarte II", "{0} s (antes 8), radio {1} (antes 6)", [10, 9], 60),
        "N": ("Embestida de escudo", "cargas {0} bloques al frente; lo que golpeas recibe {1} de daño, {2} de postura y sale empujado", [6, 6, 40], 20),
        "N2": ("Embestida de escudo II", "{0} bloques y aturde {1} s", [8, 1], 20),
    },
    "puertas": [S("block_cost"), S("max_health"), S("armor"), S("knockback"), S("stamina_max"), S("melee_damage")],
    "ramas": {
        "A": {"nombre": "Muralla",
              "tronco": [S("block_cost"), S("parry_window"), S("block_cost"), N("Escudo pesado", mods=[["block_cost", -0.20]])],
              "caminos": [
                  {"nombre": "Represalia",
                   "nodos": [S("block_cost"), S("armor"),
                             N("Represalia", "quien golpea tu escudo levantado recibe {0} de daño", [3]),
                             S("toughness"),
                             K("Espinas de acero", "Represalia devuelve además el {0%} del daño parado; pero daño c/c −{1%}", [0.30, 0.15],
                               gana="devuelve 30 % de lo parado", precio="daño c/c −15 %")],
                   "lados": [S("max_health"), S("knockback")]},
                  {"nombre": "Bastión",
                   "nodos": [S("parry_window"), S("block_cost"), N("Bastión", mods=[["parry_window", 2]]), S("block_cost"),
                             K("Muralla viva", "con el escudo levantado no te frenas y los aliados a {0} bloques detrás de ti reciben −{1%} de daño; pero no puedes esprintar", [3, 0.20],
                               gana="escudo sin frenar, protege a los de detrás", precio="sin esprintar")],
                   "lados": [S("armor"), S("max_health")]},
              ]},
        "B": {"nombre": "Coraza",
              "tronco": [S("armor"), S("max_health"), S("toughness"), N("Piel de hierro", mods=[["armor", 2]])],
              "caminos": [
                  {"nombre": "Dureza",
                   "nodos": [S("toughness"), S("damage_taken"), N("Dureza", mods=[["toughness", 2], ["damage_taken", -0.08]]), S("armor"),
                             K("Yunque viviente", None, mods=[["damage_taken", -0.20], ["knockback", 1.0], ["dodge_cost", 1.0], ["dodge_distance", -0.30]],
                               gana="daño recibido −20 %, sin empuje", precio="esquiva al doble de coste y −30 % de distancia")],
                   "lados": [S("max_health"), S("fire_taken")]},
                  {"nombre": "Coloso",
                   "nodos": [S("max_health"), S("max_health"), N("Coloso", mods=[["max_health", 0.10]]), S("max_health"),
                             K("Gigante", None, mods=[["max_health", 0.20], ["knockback", 0.50], ["stamina_max", -0.20], ["stamina_regen", -0.20]],
                               gana="vida +20 %, empuje +50 %", precio="estamina máx. y regeneración −20 %")],
                   "lados": [S("knockback"), S("armor")]},
              ]},
        "C": {"nombre": "Firmeza",
              "tronco": [S("stamina_max"), S("stamina_regen"), S("knockback"),
                         N("Recuperación", "recuperas {0} de vida cada {1} s", [1, 5])],
              "caminos": [
                  {"nombre": "Raíces",
                   "nodos": [S("knockback"), S("stamina_max"), N("Raíces", mods=[["knockback", 0.20], ["stamina_max", 0.10]]), S("stamina_regen"),
                             K("Último bastión", "una vez cada {0} min, un golpe mortal te deja a 1 de vida con Resistencia III {1} s; pero mientras espera, vida máxima −{2%}", [5, 3, 0.20],
                               gana="sobrevives a un golpe mortal", precio="vida −20 % mientras recarga")],
                   "lados": [S("max_health"), S("damage_taken")]},
                  {"nombre": "Desafío",
                   "nodos": [S("max_health"), S("stamina_regen"),
                             N("Desafío", "los monstruos que provocas hacen −{0%} de daño {1} s", [0.15, 6]),
                             S("damage_taken"),
                             K("Imán de golpes", "los hostiles a {0} bloques te prefieren siempre y, con {1} o más cerca, recibes −{2%}; pero daño c/c −{3%}", [6, 3, 0.15, 0.10],
                               gana="−15 % de daño rodeado, atraes a todo", precio="daño c/c −10 %")],
                   "lados": [S("stamina_max"), S("block_cost")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": "Senda del desafío", "nodos": [S("armor"), H("V2"), S("max_health"), S("block_cost")]},
        "S2": {"nombre": "Senda del baluarte", "nodos": [S("max_health"), H("B"), S("toughness"), S("damage_taken")], "lado": H("B2")},
        "S3": {"nombre": "Senda de la embestida",
               "nodos": [S("stamina_regen"), N("Pisotón", "al caer desde {0} bloques o más empujas lo que hay a {1} bloques y le haces {2} de postura", [3, 3, 10]), S("knockback"), H("N")],
               "lado": H("N2")},
    },
}

# ---- Mago
CLASSES["mago"] = {
    "base": "vida −10 %, estamina −10 %, hechizos +10 %, espera −10 %, maná +25 %, regeneración de maná ×6",
    "habilidades": {
        "V": ("Nova arcana", "un anillo de {0} bloques: {1} de daño mágico a los hostiles, y los empuja", [5, 6], 20),
        "V2": ("Nova arcana II", "{0} de daño (antes 6), radio {1} (antes 5)", [9, 6], 20),
        "B": ("Concentración", "{0} s con la espera de los hechizos a la mitad y sin coste de maná", [8], 60),
        "B2": ("Concentración II", "{0} s (antes 8)", [11], 60),
        "N": ("Meteoro", "tras {0} s cae un meteoro donde miras (hasta {1} bloques): {2} de daño mágico en {3} bloques y fuego {4} s; cuesta {5} de maná", [1.5, 24, 12, 3, 3, 40], 40),
        "N2": ("Meteoro II", "{0} de daño y radio {1}", [16, 4]),
    },
    "puertas": [S("spell_damage"), S("mana_max"), S("mana_regen"), S("mana_max"), S("magic_taken"), S("max_health")],
    "ramas": {
        "A": {"nombre": "Arcano",
              "tronco": [S("spell_damage"), S("spell_charge"), S("charge_bonus"), N("Sobrecarga arcana", mods=[["charge_bonus", 0.20]])],
              "caminos": [
                  {"nombre": "Catalizador",
                   "nodos": [S("spell_damage"), S("spell_damage"),
                             N("Catalizador", "daño de hechizos +{0%}; un hechizo que mata devuelve {1} de maná", [0.05, 5], mods=[["spell_damage", 0.05]]),
                             S("spell_charge"),
                             K("Hechizo encadenado", "el proyectil del báculo salta a un segundo enemigo a {0} bloques con el {1%} del daño; pero espera de hechizos +{2%}", [5, 0.50, 0.20],
                               gana="rebote al 50 %", precio="espera +20 %")],
                   "lados": [S("spell_damage"), S("mana_max")]},
                  {"nombre": "Sobrecarga",
                   "nodos": [S("charge_bonus"), S("spell_charge"),
                             N("Carga profunda", "mantener la carga {0} s más allá de llena añade +{1%}", [1, 0.15]),
                             S("charge_bonus"),
                             K("Todo o nada", "la carga completa vale +{0%} más; pero los hechizos sin cargar hacen −{1%}", [0.60, 0.40],
                               gana="carga completa +60 %", precio="sin cargar −40 %")],
                   "lados": [S("spell_charge"), S("mana_regen")]},
              ]},
        "B": {"nombre": "Flujo",
              "tronco": [S("mana_regen"), S("spell_cooldown"), S("spell_cost"), N("Mente clara", mods=[["mana_regen", 1.0]])],
              "caminos": [
                  {"nombre": "Canalización",
                   "nodos": [S("mana_max"), S("spell_charge"), N("Canalización", mods=[["spell_charge", -0.20], ["mana_max", 0.25]]), S("mana_max"),
                             K("Pozo sin fondo", "maná máximo +{0%}; pero la regeneración de maná a la mitad", [0.60],
                               gana="maná +60 %", precio="regeneración ×0,5")],
                   "lados": [S("mana_regen"), S("spell_cost")]},
                  {"nombre": "Economía",
                   "nodos": [S("spell_cost"), S("mana_regen"), N("Economía arcana", mods=[["spell_cost", -0.20]]), S("spell_cost"),
                             K("Sangre por maná", "sin maná, los hechizos se pagan con vida ({0} de vida por cada {1} de maná que falte); pero regeneración de maná −{2%}", [1, 5, 0.30],
                               gana="lanzar sin maná", precio="vida por maná, regeneración −30 %")],
                   "lados": [S("spell_cooldown"), S("mana_regen")]},
              ]},
        "C": {"nombre": "Égida",
              "tronco": [S("magic_taken"), S("max_health"), S("dodge_distance"), N("Barrera", mods=[["magic_taken", -0.20]])],
              "caminos": [
                  {"nombre": "Paso etéreo",
                   "nodos": [S("dodge_distance"), S("dodge_cooldown"), N("Paso etéreo", mods=[["dodge_distance", 0.15], ["dodge_cooldown", -0.10]]), S("dodge_cost"),
                             K("Parpadeo", "la esquiva es un salto de {0} bloques que atraviesa monstruos (no paredes) y cuesta {1} de maná en vez de estamina; pero espera de esquiva +{2%}", [5, 15, 0.30],
                               gana="esquiva-teletransporte", precio="cuesta maná, espera +30 %")],
                   "lados": [S("move_speed"), S("stamina_max")]},
                  {"nombre": "Égida",
                   "nodos": [S("max_health"), S("damage_taken"), N("Égida", "cada {0} s ganas {1} de absorción", [30, 4]), S("max_health"),
                             K("Escudo de maná", "el {0%} del daño que recibes lo paga el maná ({1} de maná por punto); pero regeneración de maná −{2%}", [0.30, 2, 0.25],
                               gana="30 % del daño al maná", precio="regeneración −25 %")],
                   "lados": [S("magic_taken"), S("max_health")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": "Senda de la nova", "nodos": [S("mana_regen"), H("V2"), S("mana_max"), S("spell_cost")]},
        "S2": {"nombre": "Senda de la calma", "nodos": [S("mana_max"), H("B"), S("max_health"), S("mana_regen")], "lado": H("B2")},
        "S3": {"nombre": "Senda del meteoro",
               "nodos": [S("magic_taken"), N("Runa de escarcha", "un hechizo a carga completa deja Lentitud I {0} s", [2]), S("max_health"), H("N")],
               "lado": H("N2")},
    },
}

# ---- Curandero
CLASSES["curandero"] = {
    "base": "curación +50 %, maná +15 %, regeneración de maná ×4, regeneración de estamina +10 %",
    "habilidades": {
        "V": ("Pulso sanador", "cura {0} (× tu curación) a ti, a los jugadores y a tus animales a {1} bloques", [4, 8], 30),
        "V2": ("Pulso sanador II", "cura {0} (antes 4) y quita Veneno y Marchitamiento", [6], 30),
        "B": ("Resurgir", "el aliado que miras (hasta {0} bloques) recupera el {1%} de la vida que le falta y Regeneración II {2} s", [16, 0.50, 5], 90),
        "B2": ("Resurgir II", "espera {0} s (antes 90) y Regeneración II {1} s (antes 5)", [70, 8], 70),
        "N": ("Escudo de luz", "el aliado que miras, o tú, gana {0} de absorción y −{1%} de daño {2} s", [6, 0.20, 6], 35),
        "N2": ("Escudo de luz II", "{0} de absorción y quita un efecto negativo", [8], 35),
    },
    "puertas": [S("healing"), S("mana_max"), S("damage_taken"), S("mana_regen"), S("stamina_regen"), S("max_health")],
    "ramas": {
        "A": {"nombre": "Sanación",
              "tronco": [S("healing"), S("healing"), S("spell_cost"), N("Manos cálidas", mods=[["healing", 0.15]])],
              "caminos": [
                  {"nombre": "Milagro",
                   "nodos": [S("healing"), S("healing"),
                             N("Milagro", "curación +{0%}; una cura que llena la vida da {1} de absorción", [0.15, 2], mods=[["healing", 0.15]]),
                             S("healing"),
                             K("Mártir", "curación +{0%}; pero cada cura a otro te cuesta {1} de vida (nunca por debajo de 1)", [0.40, 1],
                               gana="curación +40 %", precio="1 de vida por cura")],
                   "lados": [S("mana_max"), S("spell_charge")]},
                  {"nombre": "Renuevo",
                   "nodos": [S("healing"), S("mana_regen"),
                             N("Renuevo", "lo que curas recibe además Regeneración I {0} s", [3]),
                             S("spell_cost"),
                             K("Florecer", "tus curas curan el {0%} repartido en {1} s en vez de al instante; no se acumulan en el mismo blanco", [1.50, 4],
                               gana="curas ×1,5", precio="sin curas instantáneas")],
                   "lados": [S("healing"), S("max_health")]},
              ]},
        "B": {"nombre": "Amparo",
              "tronco": [S("damage_taken"), S("healing"), S("max_health"),
                         N("Bendición", "curar a alguien por debajo del {0%} de vida le da Resistencia I {1} s", [0.50, 4])],
              "caminos": [
                  {"nombre": "Purificar",
                   "nodos": [S("healing"), S("damage_taken"),
                             N("Purificar", "tus curas quitan Veneno, Marchitamiento, Debilidad y Lentitud"),
                             S("magic_taken"),
                             K("Tierra sagrada", "Pulso sanador deja {0} s un círculo de {1} bloques donde los aliados reciben −{2%}; pero su espera +{3%}", [6, 4, 0.25, 0.50],
                               gana="zona de −25 % de daño", precio="espera de Pulso +50 %")],
                   "lados": [S("max_health"), S("stamina_regen")]},
                  {"nombre": "Vínculo",
                   "nodos": [S("max_health"), S("healing"),
                             N("Vínculo", "te curas el {0%} de lo que curas a otros (además del tercio de la regla)", [0.20]),
                             S("max_health"),
                             K("Lazo vital", "vida máxima +{0%}; pero recibes tú el {1%} del daño del aliado más cercano a {2} bloques", [0.10, 0.25, 8],
                               mods=[["max_health", 0.10]], gana="vida +10 %, proteges a un aliado", precio="te comes el 25 % de su daño")],
                   "lados": [S("damage_taken"), S("mana_max")]},
              ]},
        "C": {"nombre": "Fe",
              "tronco": [S("stamina_regen"), S("mana_regen"), S("max_health"),
                         N("Serenidad", mods=[["mana_regen", 1.0], ["stamina_regen", 0.10]])],
              "caminos": [
                  {"nombre": "Aura",
                   "nodos": [S("max_health"), S("mana_regen"),
                             N("Aura", "tú y tus aliados a {0} bloques recuperáis {1} de vida cada {2} s", [6, 0.5, 3]),
                             S("healing"),
                             K("Peregrino", "el Aura llega a {0} bloques y cura {1} cada {2} s; pero todo tu daño −{3%}", [10, 1, 3, 0.20],
                               gana="Aura doble y más grande", precio="todo tu daño −20 %")],
                   "lados": [S("max_health"), S("stamina_regen")]},
                  {"nombre": "Voluntad",
                   "nodos": [S("max_health"), S("stamina_max"), N("Voluntad", mods=[["max_health", 0.08]]), S("damage_taken"),
                             K("Martillo de la fe", "tus golpes c/c curan a los aliados a {0} bloques el {1%} del daño hecho; pero curación −{2%}", [4, 0.20, 0.20],
                               gana="curar pegando", precio="curación −20 %")],
                   "lados": [S("stamina_max"), S("mana_regen")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": "Senda del pulso", "nodos": [S("healing"), H("V2"), S("mana_max"), S("max_health")]},
        "S2": {"nombre": "Senda del resurgir", "nodos": [S("mana_regen"), H("B"), S("max_health"), S("stamina_regen")], "lado": H("B2")},
        "S3": {"nombre": "Senda de la luz",
               "nodos": [S("max_health"), N("Rocío", "cuando un aliado a {0} bloques baja del {1%} de vida, tu siguiente cura sobre él es +{2%} (cada {3} s)", [16, 0.25, 0.50, 20]),
                         S("mana_max"), H("N")],
               "lado": H("N2")},
    },
}

# ---- Arquero
CLASSES["arquero"] = {
    "base": "vida −10 %, velocidad +8 %, proyectiles +15 %, tensado +10 %, esquiva +20 %, espera de esquiva −15 %, caída −25 %",
    "habilidades": {
        "V": ("Salto atrás", "un salto hacia atrás de unos {0} bloques y Caída lenta {1} s", [6, 2], 12),
        "V2": ("Salto atrás II", "espera {0} s (antes 12) y la siguiente flecha en {1} s hace +{2%}", [8, 3, 0.25], 8),
        "B": ("Lluvia de flechas", "{0} flechas en {1} s sobre un círculo de {2} bloques donde miras (hasta {3}), {4} de daño cada una", [12, 2, 3, 32, 4], 45),
        "B2": ("Lluvia de flechas II", "{0} flechas (antes 12) en un círculo de {1} bloques (antes 3)", [18, 4], 45),
        "N": ("Flecha de red", "una flecha que al impactar atrapa a los monstruos a {0} bloques (Lentitud IV {1} s)", [3, 3], 20),
        "N2": ("Flecha de red II", "radio {0}, {1} s, y los atrapados reciben +{2%} de tus flechas", [4, 4, 0.15], 20),
    },
    "puertas": [S("projectile_damage"), S("draw_speed"), S("arrow_speed"), S("move_speed"), S("dodge_distance"), S("max_health")],
    "ramas": {
        "A": {"nombre": "Puntería",
              "tronco": [S("projectile_damage"), S("headshot"), S("projectile_damage"), N("Ojo de halcón", mods=[["projectile_damage", 0.06]])],
              "caminos": [
                  {"nombre": "Tiro a la cabeza",
                   "nodos": [S("headshot"), S("headshot"), N("Tiro a la cabeza", mods=[["headshot", 0.20]]), S("headshot"),
                             K("Francotirador", "tiros a la cabeza +{0%} y Tiro lejano al doble; pero a menos de {1} bloques haces −{2%}", [0.40, 8, 0.30],
                               gana="cabeza +40 %, lejano ×2", precio="−30 % de cerca")],
                   "lados": [S("draw_speed"), S("arrow_speed")]},
                  {"nombre": "Tiro lejano",
                   "nodos": [S("arrow_speed"), S("draw_speed"),
                             N("Tiro lejano", "+{0%} por bloque más allá de {1}, hasta +{2%}", [0.02, 10, 0.30]),
                             S("arrow_speed"),
                             K("Ojo de águila", "agachado y quieto {0} s, tus flechas vuelan rectas {1} bloques y hacen +{2%}; pero no puedes esquivar {3} s tras disparar", [1, 40, 0.15, 1],
                               gana="tiro recto +15 %", precio="sin esquiva 1 s tras disparar")],
                   "lados": [S("headshot"), S("fall_damage")]},
              ]},
        "B": {"nombre": "Tensión",
              "tronco": [S("draw_speed"), S("draw_speed"), S("arrow_speed"), N("Mano rápida", mods=[["draw_speed", 0.12]])],
              "caminos": [
                  {"nombre": "Ráfaga",
                   "nodos": [S("draw_speed"), S("stamina_cost"), N("Flecha veloz", mods=[["arrow_speed", 0.15]]), S("draw_speed"),
                             K("Ráfaga", "el arco a {0%} de tensión dispara como a tensión completa; pero daño de proyectiles −{1%}", [0.75, 0.20],
                               mods=[["projectile_damage", -0.20]], gana="un tercio más de disparos", precio="proyectiles −20 %")],
                   "lados": [S("arrow_speed"), S("move_speed")]},
                  {"nombre": "Tiro certero",
                   "nodos": [S("arrow_speed"), S("headshot"),
                             N("Tiro certero", "un tiro a tensión completa deja Lentitud II {0} s", [2]),
                             S("draw_speed"),
                             K("Flecha perforante", "las flechas a tensión completa atraviesan hasta {0} enemigos; pero tensado −{1%}", [2, 0.20],
                               mods=[["draw_speed", -0.20]], gana="atraviesa 2", precio="tensado −20 %")],
                   "lados": [S("fall_damage"), S("max_health")]},
              ]},
        "C": {"nombre": "Viento",
              "tronco": [S("move_speed"), S("dodge_distance"), S("dodge_cooldown"), N("Zancada", mods=[["move_speed", 0.05]])],
              "caminos": [
                  {"nombre": "Rodar",
                   "nodos": [S("dodge_cost"), S("dodge_distance"), N("Rodar", mods=[["dodge_cost", -0.20], ["dodge_iframes", 1]]), S("dodge_cooldown"),
                             K("Disparo en carrera", "tensas y disparas esquivando y esprintando sin frenar; pero vida máxima −{0%}", [0.10],
                               mods=[["max_health", -0.10]], gana="disparar en movimiento", precio="vida −10 %")],
                   "lados": [S("move_speed"), S("stamina_max")]},
                  {"nombre": "Pluma",
                   "nodos": [S("fall_damage"), S("jump"), N("Pluma", mods=[["fall_damage", -0.40], ["jump", 0.12]]), S("jump"),
                             K("Halcón", "en el aire tus flechas hacen +{0%}; pero en el suelo −{1%}", [0.25, 0.10],
                               gana="+25 % en el aire", precio="−10 % en el suelo")],
                   "lados": [S("move_speed"), S("fall_damage")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": "Senda del salto", "nodos": [S("headshot"), H("V2"), S("draw_speed"), S("move_speed")]},
        "S2": {"nombre": "Senda de la lluvia", "nodos": [S("arrow_speed"), H("B"), S("dodge_distance"), S("move_speed")], "lado": H("B2")},
        "S3": {"nombre": "Senda del trampero",
               "nodos": [S("fall_damage"), N("Marca del cazador", "la primera flecha que acierta marca {0} s: tus siguientes flechas le hacen +{1%} (no se suma a Marca de muerte)", [8, 0.10]),
                         S("move_speed"), H("N")],
               "lado": H("N2")},
    },
}

# ---- The forging region: the same 13 nodes in every tree (they are what is left of the Herrero class).
FORGE_INNER = [
    ("forja.ojo_del_martillo", "Ojo del martillo", [["forge_window", 0.01]], None, []),
    ("forja.metal_docil", "Metal dócil", [["potential", 5]], None, []),
    ("forja.remiendo", "Remiendo", [["repair", 0.25]], None, []),
    ("forja.fuelle", "Fuelle", [["foundry_speed", 0.20]], "tus cubas, crisoles y montadoras trabajan un {0%} más rápido", [0.20]),
    ("forja.mano_firme", "Mano firme", [["upgrade_bonus", 1]], None, []),
    ("forja.brazo_de_herrero", "Brazo de herrero", [["smith_weapon", 0.08]], None, []),
]
FORGE_OUTER = [
    ("forja.golpe_de_maestro", "Golpe de maestro", [["forge_window", 0.01]], None, []),
    ("forja.alma_del_metal", "Alma del metal", [["potential", 5]], None, []),
    ("forja.temple_de_campana", "Temple de campaña", [], "SKILL_J", []),
    ("forja.ajuste_fino", "Ajuste fino", [["assembler_potential", 5]], None, []),
    ("forja.carga_honda", "Carga honda", [["capacity", 2]], None, []),
    ("forja.forja_al_rojo", "Forja al rojo", [], "tras una forja perfecta, {0} s en que tus golpes c/c prenden fuego {1} s y hacen +{2%}", [60, 3, 0.10]),
]
FORGE_SKILLS = {
    "J": ("Temple de campaña", "la pieza forjada de tu mano recupera el {0%} de su durabilidad; Prisa minera II {1} s", [0.15, 10], 60),
    "J2": ("Temple de campaña II", "el {0%} (antes 15 %) y la armadura forjada que llevas puesta un {1%}", [0.25, 0.05], 60),
}

# The milestones (hitos): once per player, kept through death and class changes.
MILESTONES = [
    ("primer_elite", "Primer élite", "Mata un monstruo de amenaza élite", 1, "nuevo (estadística de muertes por amenaza)"),
    ("campeon", "Primer campeón", "Mata un campeón (logro «Cazador de campeones»)", 2, "forja:forja/elite"),
    ("capitan", "Sin capitán", "Mata al capitán de una banda de saqueadores", 1, "forja:forja/saqueadores"),
    ("herrero_caido", "El Herrero Caído", "Derrota al Herrero Caído", 4, "forja:forja/herrero_caido"),
    ("guardian_de_cuno", "El Guardián de Cuño", "Derrota al Guardián de Cuño", 4, "nuevo: forja:forja/guardian_de_cuno"),
    ("wither", "El Wither", "Mata al Wither", 2, "minecraft:nether/summon_wither no basta: muerte del Wither (estadística)"),
    ("dragon", "El dragón", "Mata al dragón del End", 3, "minecraft:end/kill_dragon"),
    ("guardian_anciano", "Guardián anciano", "Mata a un guardián anciano", 1, "estadística de muertes"),
    ("heroe", "Héroe de la aldea", "Gana una invasión", 1, "minecraft:adventure/hero_of_the_village"),
    ("nether", "Al Nether", "Entra en el Nether", 1, "minecraft:story/enter_the_nether"),
    ("end", "Al End", "Entra en el End", 1, "minecraft:story/enter_the_end"),
    ("forja_profunda", "Entre estrellas", "Enciende el portal de la Forja Profunda", 1, "forja:forja/portal"),
    ("golpe_limpio", "Golpe limpio", "Consigue una forja perfecta", 1, "forja:forja/perfecta"),
    ("maestria_5", "Mano hecha", "Llega a maestría de herrero 5", 1, "nivel de herrero (SmithLevel)"),
    ("maestria_10", "Maestro del gremio", "Llega a maestría de herrero 10", 2, "nivel de herrero (SmithLevel)"),
    ("obra_maestra", "Obra maestra", "Forja una obra maestra", 1, "forja:forja/obra_maestra"),
    ("maestro_forjador", "Maestro forjador", "Ten un objeto con cinco mejoras al 100 %", 1, "forja:forja/maestro"),
]

MAX_LEVEL = 15
MILESTONE_CAP_PER_LEVEL = 2

# --------------------------------------------------------------------------------------------- building
SPOKES = [("A", 90), ("S1", 30), ("B", -30), ("S2", -90), ("C", -150), ("S3", 150)]
R_RING, R_FORGE_IN, R_FORGE_OUT, R_FORGE_SKILL2 = 2.0, 2.75, 3.65, 4.55
TRUNK_R = [3.3, 4.2, 5.1, 6.1]
PATH_R = [7.1, 8.0, 9.0, 9.9, 11.0]
PATH_SPREAD = 12
SIDE_SPREAD = 8
SENDA_R = [3.3, 4.4, 5.4, 6.4]
BRIDGE_R = [7.5, 8.5, 9.5]


def polar(r, deg):
    a = math.radians(deg)
    return round(r * math.cos(a), 3), round(r * math.sin(a), 3)


def build():
    out = {"version": 1, "nodos_forja": [], "clases": OrderedDict(), "hitos": [], "costes": COST, "costes_habilidad": SKILL_COST,
           "menor": {"porcentaje": 0.03, "equivalencias": SMALL}, "estadisticas": {k: {"etiqueta": v[0], "unidad": v[1], "menos_es_mejor": v[2]} for k, v in STATS.items()},
           "estadisticas_nuevas": NEW_STATS, "puentes": BRIDGES, "nivel_max": MAX_LEVEL, "tope_hitos_por_nivel": MILESTONE_CAP_PER_LEVEL}

    # forge nodes, positioned in the gaps between spokes, shared by every class
    forge = []
    gap_angles = [60, 0, -60, -120, 180, 120]  # gap i lies between spoke i and spoke i+1
    for i, (fid, name, mods, tpl, nums) in enumerate(FORGE_INNER):
        x, y = polar(R_FORGE_IN, gap_angles[i])
        forge.append(node(fid, name, "forja", mods, tpl, nums, x, y, "forja"))
    for i, (fid, name, mods, tpl, nums) in enumerate(FORGE_OUTER):
        x, y = polar(R_FORGE_OUT, gap_angles[i])
        if tpl == "SKILL_J":
            n = skill_node(fid, "J", FORGE_SKILLS["J"], x, y, "forja")
        else:
            n = node(fid, name, "forja", mods, tpl, nums, x, y, "forja")
        n["conexiones"].append(FORGE_INNER[i][0])
        forge.append(n)
    x, y = polar(R_FORGE_SKILL2, gap_angles[2] + 9)
    t2 = skill_node("forja.temple_de_campana_ii", "J2", FORGE_SKILLS["J2"], x, y, "forja")
    t2["conexiones"].append("forja.temple_de_campana")
    forge.append(t2)
    out["nodos_forja"] = forge

    for cid, cdef in CLASSES.items():
        out["clases"][cid] = build_class(cid, cdef, forge)
    for mid, name, desc, pts, source in MILESTONES:
        out["hitos"].append({"id": mid, "nombre": name, "condicion": desc, "puntos": pts, "fuente": source})
    symmetrize(out)
    return out


def node(nid, nombre, tipo, mods, tpl, nums, x, y, region, extra=None):
    n = OrderedDict()
    n["id"] = nid
    n["nombre"] = nombre
    n["tipo"] = tipo
    n["coste"] = COST.get(tipo, 0)
    n["region"] = region
    n["mods"] = [list(m) for m in mods]
    n["numeros"] = list(nums)
    n["plantilla"] = tpl
    # A template describes the whole node (its mods included); without one, the mods are the text.
    n["efecto"] = render(tpl, nums) if tpl else "; ".join(fmt_mod(s, v) for s, v in mods)
    n["x"], n["y"] = x, y
    n["conexiones"] = []
    if extra:
        n.update(extra)
    return n


def skill_node(nid, slot, sdef, x, y, region):
    name, tpl, nums, cd = (list(sdef) + [None])[:4]
    n = node(nid, name, "habilidad", [], tpl, nums, x, y, region)
    n["coste"] = SKILL_COST[slot]
    n["habilidad"] = slot
    if cd:
        n["efecto"] += "; espera %d s" % cd
        n["espera"] = cd
    return n


def make(cid, region, spec, nid, x, y, skills):
    if spec["tipo"] == "habilidad":
        return skill_node(nid, spec["slot"], skills[spec["slot"]], x, y, region)
    if spec["tipo"] == "menor":
        st, v = spec["mods"][0]
        return node(nid, STATS[st][0].capitalize(), "menor", spec["mods"], None, [], x, y, region)
    n = node(nid, spec["nombre"], spec["tipo"], spec["mods"], spec.get("texto"), spec.get("numeros", []), x, y, region)
    if spec["tipo"] == "clave":
        n["gana"], n["precio"] = spec["gana"], spec["precio"]
    return n


def build_class(cid, cdef, forge):
    nodes = []
    skills = cdef["habilidades"]
    origin = node(cid + ".origen", CLASS_NAMES[cid], "origen", [], None, [], 0.0, 0.0, "origen")
    origin["efecto"] = "la clase: " + cdef["base"] + "; habilidad I (V): " + skills["V"][0]
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
        d["conexiones"].append(forge[i]["id"])            # forge gap after this spoke
        d["conexiones"].append(forge[(i - 1) % 6]["id"])  # forge gap before it

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
            for p, path in enumerate(br["caminos"]):
                sgn = 1 if p == 0 else -1
                pang = ang + sgn * PATH_SPREAD
                prev = trunk_end
                ids = []
                for k, spec in enumerate(path["nodos"]):
                    x, y = polar(PATH_R[k], pang)
                    n = make(cid, region, spec, "%s.%s%d.%d" % (cid, sp.lower(), p + 1, k + 1), x, y, skills)
                    n["camino"] = path["nombre"]
                    n["conexiones"].append(prev)
                    nodes.append(n)
                    prev = n["id"]
                    ids.append(n["id"])
                for s, spec in enumerate(path["lados"]):
                    k = 1 if s == 0 else 3
                    x, y = polar(PATH_R[k], pang + sgn * SIDE_SPREAD)
                    n = make(cid, region, spec, "%s.%s%d.lado_%d" % (cid, sp.lower(), p + 1, s + 1), x, y, skills)
                    n["camino"] = path["nombre"]
                    n["conexiones"].append(ids[k])
                    nodes.append(n)
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
            for b, (bname, spec) in enumerate(BRIDGE_PACKAGES[target]):
                x, y = polar(BRIDGE_R[b], ang)
                n = make(cid, region, spec, "%s.%s.puente_%d" % (cid, sp.lower(), b + 1), x, y, skills)
                n["tipo"] = "puente" if b == 0 else "cruzado"
                n["coste"] = COST[n["tipo"]]
                n["destino"] = target
                if b == 0:
                    n["nombre"] = bname
                n["conexiones"].append(prev)
                nodes.append(n)
                prev = n["id"]
    return {"nombre": CLASS_NAMES[cid], "color": CLASS_COLORS[cid], "base": cdef["base"],
            "habilidades": {k: {"nombre": v[0], "efecto": render(v[1], v[2]), "plantilla": v[1], "numeros": v[2],
                                "espera": v[3] if len(v) > 3 else None} for k, v in skills.items()},
            "ramas": {k: v["nombre"] for k, v in cdef["ramas"].items()},
            "sendas": {k: v["nombre"] for k, v in cdef["sendas"].items()},
            "nodos": nodes}


def symmetrize(out):
    """Every edge both ways, inside each class tree (forge nodes are shared, so their edges are per class)."""
    for cid, c in out["clases"].items():
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


# --------------------------------------------------------------------------------------------- analysis
def tree_of(out, cid):
    """The whole graph of one class: its nodes plus the forge nodes, with the forge-ring edges added."""
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
    return errors


def reach_cost(g, start):
    """Cheapest points to own each node from the origin (Dijkstra over node costs)."""
    import heapq
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


def best_for(g, start, stat, budget):
    """Greedy: grow the owned set by the path with the best gain per point for one stat, within the budget."""
    import heapq
    owned = {start}
    spent = 0
    total = 0.0
    sign = -1 if STATS[stat][2] else 1

    def gain(n):
        return sum(sign * v for s, v in n["mods"] if s == stat)
    while True:
        dist, prev = {k: 0 for k in owned}, {}
        pq = [(0, k) for k in owned]
        heapq.heapify(pq)
        while pq:
            d, k = heapq.heappop(pq)
            if d > dist.get(k, 1e9):
                continue
            for o in g[k]["conexiones"]:
                if o in owned:
                    continue
                nd = d + g[o]["coste"]
                if nd < dist.get(o, 1e9):
                    dist[o], prev[o] = nd, k
                    heapq.heappush(pq, (nd, o))
        best = None
        for k, d in dist.items():
            if k in owned or d == 0 or spent + d > budget:
                continue
            path, cur, gsum = [], k, 0.0
            while cur not in owned:
                path.append(cur)
                gsum += gain(g[cur])
                cur = prev[cur]
            if gsum <= 0:
                continue
            ratio = gsum / d
            if best is None or ratio > best[0]:
                best = (ratio, d, gsum, path)
        if best is None:
            break
        spent += best[1]
        total += best[2]
        owned.update(best[3])
    return total, spent


def analysis(out):
    budget = MAX_LEVEL + sum(m[3] for m in MILESTONES)
    res = {"presupuesto": budget, "clases": {}}
    focus = {"guerrero": ["melee_damage", "max_health", "posture", "parry_window"],
             "asesino": ["melee_damage", "backstab", "dodge_cost", "execute"],
             "tanque": ["max_health", "armor", "damage_taken", "melee_damage"],
             "mago": ["spell_damage", "spell_cooldown", "charge_bonus", "mana_max"],
             "curandero": ["healing", "max_health", "mana_regen", "damage_taken"],
             "arquero": ["projectile_damage", "headshot", "draw_speed", "move_speed"]}
    for cid in out["clases"]:
        g = tree_of(out, cid)
        start = cid + ".origen"
        dist = reach_cost(g, start)
        total_cost = sum(n["coste"] for n in g.values())
        kinds = {}
        for n in g.values():
            kinds[n["tipo"]] = kinds.get(n["tipo"], 0) + 1
        keys = sorted([(dist[n["id"]], n["nombre"]) for n in g.values() if n["tipo"] == "clave"])
        skills = sorted([(dist[n["id"]], n["nombre"]) for n in g.values() if n["tipo"] == "habilidad"])
        best = {s: best_for(g, start, s, budget) for s in focus[cid]}
        res["clases"][cid] = {"nodos": len(g), "coste_total": total_cost, "tipos": kinds, "claves": keys,
                              "habilidades": skills, "maximos": best}
    return res


# --------------------------------------------------------------------------------------------- markdown
def md_nodes(out):
    L = []
    kind_es = {"origen": "origen", "nucleo": "núcleo", "forja": "forja", "menor": "menor", "notable": "notable",
               "clave": "**clave**", "habilidad": "habilidad", "puente": "puente", "cruzado": "cruzado"}

    def label(g, i):
        return g[i]["nombre"] if g[i]["tipo"] != "menor" else g[i]["id"].split(".", 1)[1]

    L.append("### Región de la forja (igual en los seis árboles)\n")
    L.append("| Id | Nombre | Tipo | Coste | Efecto | Conexiones |")
    L.append("|---|---|---|---|---|---|")
    ring = "las dos puertas del núcleo que tiene al lado"
    for n in out["nodos_forja"]:
        con = ", ".join(c.split(".", 1)[1] for c in n["conexiones"])
        if n["id"] in [f[0] for f in FORGE_INNER]:
            con = (con + ", " if con else "") + ring
        L.append("| `%s` | %s | %s | %d | %s | %s |" % (n["id"].split(".", 1)[1], n["nombre"], kind_es[n["tipo"]], n["coste"], n["efecto"], con))
    L.append("")
    for cid, c in out["clases"].items():
        g = {n["id"]: n for n in c["nodos"]}
        for f in out["nodos_forja"]:
            g[f["id"]] = f
        L.append("### %s\n" % c["nombre"])
        L.append("Base: %s. Ramas: A %s, B %s, C %s. Sendas: 1 %s (puente al %s), 2 %s (puente al %s), 3 %s (puente al %s).\n" % (
            c["base"], c["ramas"]["A"], c["ramas"]["B"], c["ramas"]["C"],
            c["sendas"]["S1"], CLASS_NAMES[BRIDGES[cid][0]], c["sendas"]["S2"], CLASS_NAMES[BRIDGES[cid][1]],
            c["sendas"]["S3"], CLASS_NAMES[BRIDGES[cid][2]]))
        L.append("Habilidades: " + "; ".join("**%s** (%s): %s%s" % (v["nombre"], k, v["efecto"], (", espera %d s" % v["espera"]) if v["espera"] else "")
                                            for k, v in c["habilidades"].items()) + ".\n")
        L.append("| Id | Nombre | Tipo | Coste | Efecto | Conecta con |")
        L.append("|---|---|---|---|---|---|")
        for n in c["nodos"]:
            short = n["id"].split(".", 1)[1]
            con = ", ".join(("`%s`" % x.split(".", 1)[1]) for x in n["conexiones"] if not x.startswith("forja.") or n["tipo"] == "nucleo")
            extra = ""
            if n["tipo"] == "clave":
                extra = " *(gana: %s; precio: %s)*" % (n["gana"], n["precio"])
            if n["tipo"] in ("puente", "cruzado"):
                extra = " *(del %s)*" % CLASS_NAMES[n["destino"]]
            L.append("| `%s` | %s | %s | %d | %s%s | %s |" % (short, n["nombre"], kind_es[n["tipo"]], n["coste"], n["efecto"], extra, con))
        L.append("")
    return "\n".join(L)


def md_analysis(res):
    L = ["Presupuesto en el tope: **%d puntos** (15 de nivel + %d de hitos)." % (res["presupuesto"], res["presupuesto"] - MAX_LEVEL), ""]
    L.append("| Clase | Nodos | Coste del árbol entero | %% del árbol con %d puntos | Claves (puntos para llegar) |" % res["presupuesto"])
    L.append("|---|---|---|---|---|")
    for cid, r in res["clases"].items():
        keys = ", ".join("%s %d" % (n, d) for d, n in r["claves"])
        L.append("| %s | %d | %d | %d %% | %s |" % (CLASS_NAMES[cid], r["nodos"], r["coste_total"], round(100 * res["presupuesto"] / r["coste_total"]), keys))
    L.append("")
    L.append("Puntos hasta cada habilidad (desde el origen, por el camino más barato):\n")
    for cid, r in res["clases"].items():
        L.append("- %s: %s." % (CLASS_NAMES[cid], ", ".join("%s %d" % (n, d) for d, n in r["habilidades"])))
    L.append("")
    L.append("Lo máximo que el árbol suma a una estadística con los %d puntos (todo en esa estadística, sin la base de la clase):\n" % res["presupuesto"])
    L.append("| Clase | Estadística | Máximo del árbol | Puntos usados |")
    L.append("|---|---|---|---|")
    for cid, r in res["clases"].items():
        for s, (tot, sp) in r["maximos"].items():
            unit = STATS[s][1]
            v = (("%s %%" % fmt_num(tot * 100)) if unit == "pct" else fmt_num(tot))
            L.append("| %s | %s | %s%s | %d |" % (CLASS_NAMES[cid], STATS[s][0], "−" if STATS[s][2] else "+", v, sp))
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
def draw(out, cid, path, lit=None):
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
    lit = lit or set()

    def P(n):
        return cx + n["x"] * S, cy - n["y"] * S

    def hexc(h):
        h = h.lstrip("#")
        return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    ccol, fcol, gold = hexc(c["color"]), hexc(CLASS_COLORS["forja"]), (255, 205, 70)

    # region labels
    for sp, ang in SPOKES:
        if sp in ("A", "B", "C"):
            name = "Rama %s · %s" % (sp, c["ramas"][sp])
            x, y = polar(5.4, ang + (24 if sp != "A" else 26))
        else:
            name = "%s → %s" % (c["sendas"][sp], CLASS_NAMES[BRIDGES[cid][int(sp[1]) - 1]])
            x, y = polar(10.6, ang + (9 if sp != "S2" else 0))
        px, py = cx + x * S, cy - y * S
        tw = d.textlength(name, font=f_branch)
        if sp == "S2":
            px, py = cx + 110 + tw / 2, cy + 9.05 * S
        d.text((px - tw / 2, py - 20), name, font=f_branch, fill=(200, 196, 210))
    # edges
    done = set()
    for n in g.values():
        for o in n["conexiones"]:
            key = tuple(sorted((n["id"], o)))
            if key in done:
                continue
            done.add(key)
            a, b = P(n), P(g[o])
            on = n["id"] in lit and o in lit
            d.line([a, b], fill=gold if on else (88, 84, 98), width=9 if on else 4)
    # nodes
    for n in g.values():
        x, y = P(n)
        t = n["tipo"]
        on = n["id"] in lit
        col = ccol
        if t == "forja" or n["id"].startswith("forja."):
            col = fcol
        if t in ("puente", "cruzado"):
            col = hexc(CLASS_COLORS[n["destino"]])
        r = {"origen": 62, "nucleo": 26, "forja": 30, "menor": 21, "notable": 36, "clave": 52, "habilidad": 42, "puente": 38, "cruzado": 28}[t]
        if t == "clave":
            pts = [(x + r * math.cos(math.radians(a)), y + r * math.sin(math.radians(a))) for a in range(0, 360, 60)]
            d.polygon(pts, fill=col, outline=gold if on else (255, 255, 255), width=6)
        elif t == "puente":
            pts = [(x, y - r), (x + r, y), (x, y + r), (x - r, y)]
            d.polygon(pts, fill=col, outline=gold if on else (255, 255, 255), width=5)
        elif t == "habilidad":
            d.rounded_rectangle([x - r, y - r, x + r, y + r], radius=12, fill=col, outline=gold if on else (255, 255, 255), width=6)
            slot = n.get("habilidad", "")
            key = {"V2": "V", "B": "B", "B2": "B", "N": "N", "N2": "N", "J": "J", "J2": "J"}.get(slot, "")
            lvl = "II" if slot.endswith("2") else ""
            label = key + lvl
            tw = d.textlength(label, font=f_name)
            d.text((x - tw / 2, y - 13), label, font=f_name, fill=(20, 20, 20))
        else:
            fill = col if t != "menor" else tuple(int(v * 0.65) for v in col)
            d.ellipse([x - r, y - r, x + r, y + r], fill=fill, outline=gold if on else (230, 230, 230), width=6 if on else (4 if t == "notable" else 2))
        if t == "origen":
            tw = d.textlength(c["nombre"], font=f_name)
            d.text((x - tw / 2, y - 12), c["nombre"], font=f_name, fill=(15, 15, 15))
    # labels
    for n in g.values():
        x, y = P(n)
        t = n["tipo"]
        if t in ("notable", "clave", "habilidad", "puente", "forja") or (t == "cruzado" and n["nombre"] != STATS[n["mods"][0][0]][0].capitalize() if n["mods"] else t == "cruzado"):
            name = n["nombre"]
            font = f_name
            off = {"clave": 60, "habilidad": 50, "puente": 46, "notable": 42, "forja": 36, "cruzado": 34}.get(t, 30)
        elif t in ("menor", "nucleo", "cruzado"):
            s, v = n["mods"][0]
            name = fmt_mod(s, v)
            font = f_small
            off = 26 if t == "menor" else 30
        else:
            continue
        tw = d.textlength(name, font=font)
        tx, ty = x - tw / 2, y + off
        if n["id"] in [f[0] for f in FORGE_INNER] and abs(n["y"]) < 0.5:
            ty = y - off - font.size - 4
        d.rectangle([tx - 3, ty - 1, tx + tw + 3, ty + font.size + 3], fill=(22, 20, 26))
        d.text((tx, ty), name, font=font, fill=gold if n["id"] in lit else ((255, 230, 160) if t == "clave" else (235, 232, 240)))
    # header and legend
    d.text((60, 40), "Árbol del %s" % c["nombre"], font=f_title, fill=ccol)
    r = analysis_cache[cid]
    d.text((60, 120), "%d nodos · el árbol entero cuesta %d puntos · en el tope hay %d (15 de nivel + %d de hitos)" % (
        r["nodos"], r["coste_total"], analysis_cache["_budget"], analysis_cache["_budget"] - 15), font=f_sub, fill=(210, 206, 220))
    d.text((60, 160), "Dorado: una partida de ejemplo con los %d puntos gastados (%s)." % (analysis_cache["_budget"], lit_names.get(cid, "")), font=f_sub, fill=gold)
    lx, ly = 60, H - 300
    items = [("origen", "Origen: la clase y la habilidad V"), ("nucleo", "Núcleo: 6 puertas (1 punto)"), ("forja", "Forja: igual en todas las clases (1)"),
             ("menor", "Menor: +3 % (1)"), ("notable", "Notable (1)"), ("clave", "Clave: cambia el estilo, con precio (2)"),
             ("habilidad", "Habilidad: B y N, y sus mejoras II (2-3)"), ("puente", "Puente a otra clase y sus nodos (2 cada uno)")]
    for i, (t, txt) in enumerate(items):
        x, y = lx + 40 + (i // 4) * 2080, ly + (i % 4) * 62
        col = fcol if t == "forja" else ccol
        if t == "clave":
            pts = [(x + 26 * math.cos(math.radians(a)), y + 26 * math.sin(math.radians(a))) for a in range(0, 360, 60)]
            d.polygon(pts, fill=col, outline=(255, 255, 255))
        elif t == "puente":
            d.polygon([(x, y - 24), (x + 24, y), (x, y + 24), (x - 24, y)], fill=hexc(CLASS_COLORS[BRIDGES[cid][0]]), outline=(255, 255, 255))
        elif t == "habilidad":
            d.rounded_rectangle([x - 22, y - 22, x + 22, y + 22], radius=8, fill=col, outline=(255, 255, 255), width=3)
        else:
            rr = {"origen": 26, "nucleo": 18, "forja": 20, "menor": 14, "notable": 22}[t]
            d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=col if t != "menor" else tuple(int(v * 0.65) for v in col), outline=(230, 230, 230), width=2)
        d.text((x + 44, y - 18), txt, font=f_sub, fill=(220, 216, 228))
    img.save(path, optimize=True)


analysis_cache = {}
lit_names = {}

# Example builds, drawn in gold: the nodes taken, as ids without the class prefix (the cheapest path is added).
EXAMPLES = {
    "guerrero": ("Guardia hasta Duelista, Quebranto hasta Sed de sangre, Grito II, Postura de hierro II, Carga brutal y seis nodos de forja",
                 ["b2.5", "c2.5", "s1.2", "s2.lado", "c2.lado_1", "b2.lado_1", "forja.ojo_del_martillo", "forja.metal_docil", "forja.remiendo", "s3.2", "forja.golpe_de_maestro", "forja.alma_del_metal", "forja.brazo_de_herrero"]),
    "mago": ("Arcano hasta Hechizo encadenado, Flujo hasta Economía arcana, Concentración II, Meteoro II, Barrera y dos nodos de forja",
             ["a1.5", "a1.lado_1", "a1.lado_2", "b2.3", "s2.lado", "s3.lado", "forja.mano_firme", "c.tronco_4", "forja.carga_honda"]),
}


def lit_build(out, cid, wanted):
    g = tree_of(out, cid)
    import heapq
    owned = {cid + ".origen"}
    for w in wanted:
        target = w if w.startswith("forja.") else cid + "." + w
        dist, prev = {k: 0 for k in owned}, {}
        pq = [(0, k) for k in owned]
        while pq:
            dd, k = heapq.heappop(pq)
            if dd > dist.get(k, 1e9):
                continue
            for o in g[k]["conexiones"]:
                if o in owned:
                    continue
                nd = dd + g[o]["coste"]
                if nd < dist.get(o, 1e9):
                    dist[o], prev[o] = nd, k
                    heapq.heappush(pq, (nd, o))
        cur = target
        while cur not in owned:
            owned.add(cur)
            cur = prev[cur]
    return owned, sum(g[k]["coste"] for k in owned)


def main():
    out = build()
    errs = check(out)
    if errs:
        print("\n".join(errs))
        sys.exit(1)
    res = analysis(out)
    with open(os.path.join(DOCS, "arboles.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    write_md(out, res)
    for cid, r in res["clases"].items():
        print(cid, r["nodos"], "nodos, coste", r["coste_total"], r["tipos"])
        print("   claves", r["claves"])
        print("   habilidades", r["habilidades"])
        print("   maximos", {k: (round(v[0], 3), v[1]) for k, v in r["maximos"].items()})
    if "--imagenes" in sys.argv:
        dest = sys.argv[sys.argv.index("--imagenes") + 1]
        os.makedirs(dest, exist_ok=True)
        analysis_cache.update(res["clases"])
        analysis_cache["_budget"] = res["presupuesto"]
        for cid in ("guerrero", "mago"):
            desc, wanted = EXAMPLES[cid]
            lit, cost = lit_build(out, cid, wanted)
            print(cid, "ejemplo:", cost, "puntos")
            lit_names[cid] = "%s; %d puntos" % (desc, cost)
            p = os.path.join(dest, "arbol_%s.png" % cid)
            draw(out, cid, p, lit)
            print(p, os.path.getsize(p) // 1024, "KB")


if __name__ == "__main__":
    main()
