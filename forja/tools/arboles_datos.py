"""The class trees' content (docs/ARBOLES.md): every name, text and number, in Spanish and English.

tools/arboles.py lays it out, checks it and writes src/main/resources/forja_arboles.json (what the game reads)
and docs/arboles.json (the same file, for reading). Texts are templates: {0} is numbers[0] as it is, {0%} is
numbers[0] as a percentage. A notable's or keystone's plain numbers go in its mods (ClassStat ids, summed with
the rest of the class); its text only says what a number cannot.
"""
from collections import OrderedDict

# ------------------------------------------------------------------ the numbers everything else hangs on
MAX_LEVEL = 50
# Points from levels: floor(LEVEL_POINTS_NUM * level / LEVEL_POINTS_DEN), so 1, 2, 4, 5, 7... (1 or 2 a level,
# 7 every 5 levels): 70 at level 50.
LEVEL_POINTS_NUM = 7
LEVEL_POINTS_DEN = 5
# Milestone points one can spend: at most this many per class level.
MILESTONE_CAP_PER_LEVEL = 1
# Experience from level N to N+1: FIRST_STEP + STEP_GROWTH * (N - 1).
FIRST_STEP = 30
STEP_GROWTH = 5

COST = {"origen": 0, "nucleo": 1, "forja": 1, "menor": 1, "notable": 1, "clave": 2, "puente": 2, "cruzado": 2}
SKILL_COST = {"B": 2, "N": 3, "V2": 2, "B2": 2, "N2": 2}

# id -> (Spanish label, English label, unit, lower is better). Units: pct, flat, ticks, centesimas, puntos.
STATS = OrderedDict([
    ("max_health", ("vida", "health", "pct", False)),
    ("move_speed", ("velocidad", "speed", "pct", False)),
    ("melee_damage", ("daño cuerpo a cuerpo", "melee damage", "pct", False)),
    ("armor", ("armadura", "armor", "flat", False)),
    ("toughness", ("dureza de armadura", "armor toughness", "flat", False)),
    ("knockback", ("resistencia al empuje", "knockback resistance", "pct", False)),
    ("jump", ("salto", "jump", "pct", False)),
    ("sneak_speed", ("velocidad agachado", "sneaking speed", "pct", False)),
    ("fall_damage", ("daño de caída", "fall damage", "pct", True)),
    ("stamina_max", ("estamina máxima", "max stamina", "pct", False)),
    ("stamina_regen", ("regeneración de estamina", "stamina regeneration", "pct", False)),
    ("stamina_cost", ("coste de estamina", "stamina cost", "pct", True)),
    ("dodge_distance", ("distancia de esquiva", "dodge distance", "pct", False)),
    ("dodge_cooldown", ("espera de esquiva", "dodge cooldown", "pct", True)),
    ("dodge_cost", ("coste de esquiva", "dodge cost", "pct", True)),
    ("dodge_iframes", ("invulnerabilidad de esquiva", "dodge invulnerability", "ticks", False)),
    ("parry_window", ("ventana de parada", "parry window", "ticks", False)),
    ("block_cost", ("estamina al parar", "block stamina", "pct", True)),
    ("posture", ("daño de postura", "posture damage", "pct", False)),
    ("damage_taken", ("daño recibido", "damage taken", "pct", True)),
    ("magic_taken", ("daño mágico recibido", "magic damage taken", "pct", True)),
    ("fire_taken", ("daño de fuego recibido", "fire damage taken", "pct", True)),
    ("backstab", ("daño por la espalda", "backstab damage", "pct", False)),
    ("counter", ("contraataques", "counterattacks", "pct", False)),
    ("staggered_bonus", ("daño a aturdidos", "damage to staggered foes", "pct", False)),
    ("finisher", ("remates", "finishers", "pct", False)),
    ("execute", ("daño a enemigos bajo el 35 %", "damage to foes under 35 %", "pct", False)),
    ("projectile_damage", ("daño de proyectiles", "projectile damage", "pct", False)),
    ("headshot", ("tiros a la cabeza", "headshots", "pct", False)),
    ("draw_speed", ("tensado", "draw speed", "pct", False)),
    ("arrow_speed", ("velocidad de flecha", "arrow speed", "pct", False)),
    ("spell_damage", ("daño de hechizos", "spell damage", "pct", False)),
    ("spell_cooldown", ("espera de hechizos", "spell cooldown", "pct", True)),
    ("spell_charge", ("tiempo de carga", "charge time", "pct", True)),
    ("charge_bonus", ("bono de la carga completa", "full-charge bonus", "pct", False)),
    ("healing", ("curación", "healing", "pct", False)),
    ("mana_max", ("maná máximo", "max mana", "pct", False)),
    ("mana_regen", ("regeneración de maná", "mana regeneration", "pct", False)),
    ("spell_cost", ("coste de maná", "mana cost", "pct", True)),
    ("forge_window", ("ventana del golpe perfecto", "perfect-strike window", "centesimas", False)),
    ("potential", ("potencial al forjar", "forging potential", "puntos", False)),
    ("repair", ("reparación por lingote", "repair per ingot", "pct", False)),
    ("upgrade_bonus", ("% por ingrediente de mejora", "% per upgrade ingredient", "puntos", False)),
    ("smith_weapon", ("daño con martillo, mazo, pico y hacha", "hammer, mace, pickaxe and axe damage", "pct", False)),
    ("foundry_speed", ("velocidad de las máquinas cercanas", "speed of nearby machines", "pct", False)),
    ("capacity", ("carga de lo que forjas", "load of what you forge", "puntos", False)),
    ("assembler_potential", ("potencial de lo que montan las montadoras cercanas", "potential of what nearby assemblers make", "puntos", False)),
])
NEW_STATS = ["foundry_speed", "capacity", "assembler_potential"]

# ------------------------------------------------------------------ icons (all of them are chosen here)
# An icon is one of: "minecraft:x" / "forja:x" (an item), "sprite:name" (textures/gui/arbol/name.png, made by
# tools/arbol_iconos.py), "clase:id" (that class's emblem: the forged weapon that stands for it). A skill's
# upgrade (II) shows its skill's icon with a gold "II"; a bridge shows its destination class's emblem.
# A small node: one icon per stat.
STAT_ICONS = {
    "max_health": "sprite:corazon", "move_speed": "minecraft:feather", "melee_damage": "minecraft:iron_sword",
    "armor": "minecraft:iron_chestplate", "toughness": "minecraft:netherite_scrap", "knockback": "minecraft:piston",
    "jump": "minecraft:rabbit_foot", "sneak_speed": "minecraft:leather_boots", "fall_damage": "minecraft:slime_ball",
    "stamina_max": "minecraft:bread", "stamina_regen": "minecraft:cooked_beef", "stamina_cost": "minecraft:rotten_flesh",
    "dodge_distance": "minecraft:ender_pearl", "dodge_cooldown": "minecraft:clock", "dodge_cost": "minecraft:gunpowder",
    "dodge_iframes": "minecraft:phantom_membrane", "parry_window": "minecraft:iron_nugget", "block_cost": "minecraft:shield",
    "posture": "minecraft:mace", "damage_taken": "minecraft:leather_chestplate", "magic_taken": "minecraft:amethyst_shard",
    "fire_taken": "minecraft:magma_cream", "backstab": "minecraft:stone_sword", "counter": "minecraft:flint",
    "staggered_bonus": "minecraft:fermented_spider_eye", "finisher": "minecraft:skeleton_skull",
    "execute": "minecraft:wither_skeleton_skull", "projectile_damage": "minecraft:arrow", "headshot": "minecraft:target",
    "draw_speed": "minecraft:bow", "arrow_speed": "minecraft:firework_rocket", "spell_damage": "minecraft:fire_charge",
    "spell_cooldown": "minecraft:glowstone_dust", "spell_charge": "minecraft:blaze_powder",
    "charge_bonus": "minecraft:nether_star", "healing": "minecraft:glistering_melon_slice", "mana_max": "minecraft:lapis_lazuli",
    "mana_regen": "minecraft:experience_bottle", "spell_cost": "minecraft:redstone",
}

# A notable, by its Spanish name (the lesser twins in the bridge packages are at the end).
NOTABLE_ICONS = {
    "Segundo aliento": "minecraft:wind_charge", "Inquebrantable": "minecraft:obsidian", "Sin resuello": "minecraft:sugar",
    "Réplica": "minecraft:flint_and_steel", "Parada firme": "minecraft:iron_trapdoor", "Filo de vuelta": "minecraft:stone_sword",
    "Golpe pesado": "minecraft:anvil", "Rompeguardias": "minecraft:iron_pickaxe", "Verdugo": "minecraft:netherite_axe",
    "Carga brutal": "minecraft:piston",
    "Danza": "minecraft:golden_boots", "Contraataque": "minecraft:golden_sword", "Espejismo": "minecraft:glass",
    "Puñalada": "minecraft:iron_sword", "Ejecutor": "minecraft:netherite_sword", "Golpe letal": "minecraft:blaze_powder",
    "Paso quedo": "minecraft:rabbit_hide", "Acróbata": "minecraft:chorus_fruit", "Evasión": "minecraft:snowball",
    "Veneno en la hoja": "minecraft:spider_eye",
    "Escudo pesado": "minecraft:iron_door", "Represalia": "minecraft:cactus", "Bastión": "minecraft:stone_bricks",
    "Piel de hierro": "minecraft:iron_helmet", "Dureza": "minecraft:diamond", "Coloso": "minecraft:cooked_porkchop",
    "Recuperación": "minecraft:golden_carrot", "Raíces": "minecraft:hanging_roots", "Desafío": "minecraft:red_banner",
    "Pisotón": "minecraft:iron_boots",
    "Sobrecarga arcana": "minecraft:end_crystal", "Catalizador": "minecraft:brewing_stand", "Carga profunda": "minecraft:experience_bottle",
    "Mente clara": "minecraft:echo_shard", "Canalización": "minecraft:lapis_block", "Economía arcana": "minecraft:emerald",
    "Barrera": "minecraft:amethyst_cluster", "Paso etéreo": "minecraft:ender_pearl", "Égida": "minecraft:turtle_helmet",
    "Runa de escarcha": "minecraft:blue_ice",
    "Manos cálidas": "minecraft:sweet_berries", "Milagro": "minecraft:enchanted_golden_apple", "Renuevo": "minecraft:oak_sapling",
    "Bendición": "minecraft:honey_bottle", "Purificar": "minecraft:milk_bucket", "Vínculo": "minecraft:lead",
    "Serenidad": "minecraft:blue_orchid", "Aura": "minecraft:beacon", "Voluntad": "minecraft:netherite_ingot",
    "Rocío": "minecraft:glass_bottle",
    "Ojo de halcón": "minecraft:spyglass", "Tiro a la cabeza": "minecraft:target", "Tiro lejano": "minecraft:compass",
    "Mano rápida": "minecraft:string", "Flecha veloz": "minecraft:spectral_arrow", "Tiro certero": "minecraft:crossbow",
    "Zancada": "minecraft:leather_boots", "Rodar": "minecraft:hay_block", "Pluma": "minecraft:feather",
    "Marca del cazador": "minecraft:name_tag",
    "Réplica menor": "minecraft:flint_and_steel", "Puñalada menor": "minecraft:iron_sword", "Represalia menor": "minecraft:cactus",
    "Barrera menor": "minecraft:amethyst_shard", "Vendaje": "minecraft:paper", "Ojo de halcón menor": "minecraft:spyglass",
}

# A keystone, by its Spanish name: the glyph tools/arbol_iconos.py draws on a medallion in its class's colour
# (the sprite is "clave_<slug>"). Six glyphs a class, none repeated inside one.
KEYSTONE_GLYPHS = {
    "Muro de carne": "muro", "Adrenalina": "rayo", "Fortaleza": "torre", "Duelista": "espadas", "Martillo de guerra": "martillo",
    "Sed de sangre": "gota",
    "Filo del viento": "viento", "Sin sombra": "fantasma", "Golpe de gracia": "calavera", "Frenesí": "garra",
    "Funámbulo": "cuerda", "Fantasma": "mascara",
    "Espinas de acero": "pincho", "Muralla viva": "almena", "Yunque viviente": "yunque", "Gigante": "puno",
    "Último bastión": "bandera", "Imán de golpes": "iman",
    "Hechizo encadenado": "cadena", "Todo o nada": "mitad", "Pozo sin fondo": "espiral", "Sangre por maná": "gota_estrella",
    "Parpadeo": "destello", "Escudo de maná": "escudo",
    "Mártir": "cruz", "Florecer": "flor", "Tierra sagrada": "runa", "Lazo vital": "anillos", "Peregrino": "sol",
    "Martillo de la fe": "martillo_luz",
    "Francotirador": "mira", "Ojo de águila": "ojo", "Ráfaga": "rafaga", "Flecha perforante": "perforante",
    "Disparo en carrera": "bota", "Halcón": "ala",
}

# The forging region, by node id: forge items.
FORGE_ICONS = {
    "forja.ojo_del_martillo": "minecraft:clock", "forja.metal_docil": "forja:fundente_maestro", "forja.remiendo": "minecraft:iron_ingot",
    "forja.fuelle": "forja:ascua", "forja.mano_firme": "forja:orbe_de_mejora", "forja.brazo_de_herrero": "forja:martillo_del_maestro",
    "forja.golpe_de_maestro": "forja:sello", "forja.alma_del_metal": "forja:corazon_de_forja",
    "forja.temple_de_campana": "forja:lingote_de_temple", "forja.ajuste_fino": "forja:plantilla", "forja.carga_honda": "minecraft:barrel",
    "forja.forja_al_rojo": "minecraft:lava_bucket", "forja.temple_de_campana_ii": "forja:lingote_de_temple",
}

# The sprites drawn by tools/arbol_iconos.py that are not keystones.
EXTRA_SPRITES = ["corazon"]


def key_sprite(name):
    import re
    import unicodedata
    text = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode()
    return "sprite:clave_" + re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


# A small node: "+3 % of a stat" (Andy), and the equivalent for the stats that are not a percentage.
SMALL = {"armor": 1, "toughness": 0.5, "parry_window": 1, "knockback": 0.05, "mana_regen": 0.20}


def small_value(stat):
    if stat in SMALL:
        return SMALL[stat]
    return -0.03 if STATS[stat][3] else 0.03


def S(stat, v=None):
    return {"tipo": "menor", "mods": [[stat, small_value(stat) if v is None else v]], "icono": STAT_ICONS[stat]}


def N(nombre, texto=None, nums=(), mods=()):
    """A notable: (es, en) name, an optional (es, en) text for what its mods cannot say, its numbers and mods."""
    return {"tipo": "notable", "icono": NOTABLE_ICONS[nombre[0]], "nombre": nombre, "texto": texto, "numeros": list(nums), "mods": [list(m) for m in mods]}


def K(nombre, texto=None, nums=(), mods=(), gana="", precio=""):
    """A keystone: like a notable, plus what it gains and what it costs, in words, for the docs."""
    return {"tipo": "clave", "icono": key_sprite(nombre[0]), "nombre": nombre, "texto": texto, "numeros": list(nums), "mods": [list(m) for m in mods],
            "gana": gana, "precio": precio}


def H(slot):
    """A skill node: 'B' or 'N' (the skill itself), 'V2', 'B2' or 'N2' (its upgrade)."""
    return {"tipo": "habilidad", "slot": slot}


def SK(sid, nombre, texto, nums, espera, texto2, nums2, espera2=None, icono="minecraft:book"):
    """A skill: its id (clase/ActiveSkill), name, text, numbers, cooldown, and the same for its II."""
    return {"id": sid, "nombre": nombre, "plantilla": texto, "numeros": list(nums), "espera": espera, "icono": icono,
            "mejora": {"plantilla": texto2, "numeros": list(nums2), "espera": espera2 if espera2 is not None else espera}}


# ------------------------------------------------------------------ bridges
# A 3-regular graph: every class reaches three others and is reached by three.
BRIDGES = {
    "guerrero": ["tanque", "asesino", "arquero"],
    "asesino": ["guerrero", "arquero", "mago"],
    "tanque": ["guerrero", "curandero", "mago"],
    "mago": ["asesino", "curandero", "tanque"],
    "curandero": ["tanque", "mago", "arquero"],
    "arquero": ["asesino", "guerrero", "curandero"],
}
# What lies past a bridge into a class: the bridge, a small and a notable, 2 points each, the same whoever
# crosses. None of it touches the damage factors (no spell damage into a class that casts at x0.4).
BRIDGE_PACKAGES = {
    "guerrero": [("Puente al Guerrero", "Bridge to the Warrior"), S("stamina_regen", 0.05), S("posture", 0.05),
                 N(("Réplica menor", "Lesser Riposte"), ("una parada perfecta te devuelve {0} de estamina", "a perfect parry gives you back {0} stamina"), [10])],
    "asesino": [("Puente al Asesino", "Bridge to the Assassin"), S("move_speed", 0.03), S("dodge_cost", -0.08),
                N(("Puñalada menor", "Lesser Stab"), mods=[["backstab", 0.20]])],
    "tanque": [("Puente al Tanque", "Bridge to the Tank"), S("max_health", 0.05), S("armor", 1),
               N(("Represalia menor", "Lesser Retaliation"), ("quien golpea tu escudo levantado recibe {0} de daño", "whoever strikes your raised shield takes {0} damage"), [2])],
    "mago": [("Puente al Mago", "Bridge to the Mage"), S("mana_max", 0.10), S("mana_regen", 0.50),
             N(("Barrera menor", "Lesser Barrier"), mods=[["magic_taken", -0.15]])],
    "curandero": [("Puente al Curandero", "Bridge to the Healer"), S("healing", 0.10), S("stamina_regen", 0.05),
                  N(("Vendaje", "Bandage"), ("cada {0} s sin recibir daño recuperas {1} de vida", "every {0} s without taking damage you recover {1} health"), [8, 1])],
    "arquero": [("Puente al Arquero", "Bridge to the Archer"), S("draw_speed", 0.05), S("projectile_damage", 0.04),
                N(("Ojo de halcón menor", "Lesser Hawk Eye"), mods=[["headshot", 0.15]])],
}

# ------------------------------------------------------------------ the six classes
CLASSES = OrderedDict()

CLASSES["guerrero"] = {
    "base": ("vida +10 %, daño c/c +5 %, estamina máx. +20 %, regeneración +10 %, postura +15 %, parada +1 tick",
             "health +10 %, melee +5 %, max stamina +20 %, regeneration +10 %, posture +15 %, parry +1 tick"),
    "habilidades": {
        "V": SK("grito_de_guerra", ("Grito de guerra", "War Cry"),
                ("recuperas {0} de estamina; tú y los jugadores a {1} bloques ganáis Fuerza I {2} s",
                 "recover {0} stamina; you and players within {1} blocks gain Strength I for {2} s"), [40, 8, 8], 45,
                ("recuperas {0} de estamina; tú y los jugadores a {1} bloques ganáis Fuerza I {2} s",
                 "recover {0} stamina; you and players within {1} blocks gain Strength I for {2} s"), [60, 12, 10], icono="minecraft:goat_horn"),
        "B": SK("postura_de_hierro", ("Postura de hierro", "Iron Stance"),
                ("{0} s de Resistencia II y Lentitud I", "Resistance II and Slowness I for {0} s"), [6], 50,
                ("{0} s de Resistencia II, sin Lentitud; al acabar recuperas {1} de estamina",
                 "Resistance II for {0} s, without Slowness; when it ends you recover {1} stamina"), [8, 20], icono="minecraft:iron_block"),
        "N": SK("torbellino", ("Torbellino", "Whirlwind"),
                ("giras y golpeas todo lo que hay a {0} bloques con el {1%} del daño de tu arma y {2} de postura; cuesta {3} de estamina",
                 "you spin and strike everything within {0} blocks for {1%} of your weapon's damage and {2} posture; costs {3} stamina"),
                [3, 0.80, 30, 30], 30,
                ("dos vueltas: golpeas dos veces todo lo que hay a {0} bloques con el {1%} del daño de tu arma y {2} de postura; cuesta {3} de estamina",
                 "two spins: you strike everything within {0} blocks twice for {1%} of your weapon's damage and {2} posture; costs {3} stamina"),
                [3, 0.80, 45, 30], icono="minecraft:iron_axe"),
    },
    "puertas": [S("stamina_max"), S("stamina_regen"), S("block_cost"), S("max_health"), S("posture"), S("melee_damage")],
    "ramas": {
        "A": {"nombre": ("Aguante", "Endurance"),
              "tronco": [S("stamina_regen"), S("max_health"), S("stamina_max"),
                         N(("Segundo aliento", "Second Wind"), ("por debajo del {0%} de estamina, la estamina se regenera el doble",
                                                                  "under {0%} stamina, stamina regenerates twice as fast"), [0.25], [["stamina_regen", 0.15]])],
              "caminos": [
                  {"nombre": ("Piel curtida", "Tanned Hide"),
                   "nodos": [S("max_health"), S("knockback"),
                             N(("Inquebrantable", "Unbreakable"), ("por debajo del {0%} de vida, daño recibido −{1%}", "under {0%} health, damage taken −{1%}"), [0.30, 0.20]),
                             S("max_health"),
                             K(("Muro de carne", "Wall of Flesh"), mods=[["max_health", 0.20], ["move_speed", -0.08], ["dodge_cost", 0.25]],
                               gana="vida +20 %", precio="velocidad −8 %, coste de esquiva +25 %")],
                   "lados": [S("stamina_max"), S("damage_taken")]},
                  {"nombre": ("Fondo", "Deep Lungs"),
                   "nodos": [S("stamina_cost"), S("stamina_regen"), N(("Sin resuello", "Breathless"), mods=[["stamina_cost", -0.15]]), S("stamina_max"),
                             K(("Adrenalina", "Adrenaline"), ("cada golpe que recibes te devuelve {0} de estamina", "every blow you take gives you back {0} stamina"), [6],
                               [["stamina_regen", 0.25], ["stamina_max", -0.25]], gana="6 de estamina por golpe recibido, regeneración +25 %", precio="estamina máxima −25 %")],
                   "lados": [S("stamina_regen"), S("move_speed")]},
              ]},
        "B": {"nombre": ("Guardia", "Guard"),
              "tronco": [S("parry_window"), S("block_cost"), S("armor"),
                         N(("Réplica", "Riposte"), ("una parada devuelve {0} de estamina y tu siguiente golpe en {1} s hace +{2%}",
                                                     "a parry gives back {0} stamina and your next blow within {1} s deals +{2%}"), [15, 3, 0.30])],
              "caminos": [
                  {"nombre": ("Muro de escudo", "Shield Wall"),
                   "nodos": [S("armor"), S("block_cost"), N(("Parada firme", "Firm Block"), mods=[["block_cost", -0.25]]), S("toughness"),
                             K(("Fortaleza", "Fortress"), ("con un escudo en la otra mano no puedes esquivar", "with a shield in your off hand you cannot dodge"), [],
                               [["knockback", 0.50], ["block_cost", -0.40]], gana="empuje +50 %, parar −40 %", precio="sin esquiva con escudo")],
                   "lados": [S("knockback"), S("max_health")]},
                  {"nombre": ("Contragolpe", "Counterblow"),
                   "nodos": [S("parry_window"), S("counter"),
                             N(("Filo de vuelta", "Returning Edge"), ("una parada hace {0} de daño de postura a quien te golpeó", "a parry deals {0} posture damage to whoever struck you"), [20]),
                             S("counter"),
                             K(("Duelista", "Duelist"), ("bloquear sin parar cuesta el doble de estamina", "blocking without a parry costs twice the stamina"), [],
                               [["parry_window", 3]], gana="ventana de parada +3 ticks", precio="bloqueos sin parada ×2 de estamina")],
                   "lados": [S("posture"), S("stamina_regen")]},
              ]},
        "C": {"nombre": ("Quebranto", "Breaker"),
              "tronco": [S("posture"), S("melee_damage"), S("posture"), N(("Golpe pesado", "Heavy Blow"), mods=[["posture", 0.20]])],
              "caminos": [
                  {"nombre": ("Rompeguardias", "Guardbreaker"),
                   "nodos": [S("staggered_bonus"), S("posture"), N(("Rompeguardias", "Guardbreaker"), mods=[["staggered_bonus", 0.20]]), S("staggered_bonus"),
                             K(("Martillo de guerra", "Warhammer"), ("contra lo que no está aturdido haces −{0%}", "against foes that are not staggered you deal −{0%}"), [0.10],
                               [["posture", 0.40], ["staggered_bonus", 0.15]], gana="postura +40 %, aturdidos +15 %", precio="−10 % a no aturdidos")],
                   "lados": [S("melee_damage"), S("max_health")]},
                  {"nombre": ("Verdugo", "Executioner"),
                   "nodos": [S("finisher"), S("melee_damage"), N(("Verdugo", "Executioner"), mods=[["finisher", 0.30]]), S("execute"),
                             K(("Sed de sangre", "Bloodthirst"), ("matar cuerpo a cuerpo te cura el {0%} de tu vida máxima", "a melee kill heals you {0%} of your max health"), [0.10],
                               [["damage_taken", 0.10]], gana="10 % de vida por muerte", precio="daño recibido +10 %")],
                   "lados": [S("finisher"), S("melee_damage")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": ("Senda del grito", "Path of the Cry"), "nodos": [S("stamina_regen"), H("V2"), S("max_health"), S("stamina_max")]},
        "S2": {"nombre": ("Senda del hierro", "Path of Iron"), "nodos": [S("posture"), H("B"), S("damage_taken"), S("max_health")], "lado": H("B2")},
        "S3": {"nombre": ("Senda del torbellino", "Path of the Whirlwind"),
               "nodos": [S("posture"), N(("Carga brutal", "Brutal Charge"), ("un golpe cargado rompe la postura de un monstruo normal", "a charged blow breaks a normal monster's posture")),
                         S("stamina_regen"), H("N")],
               "lado": H("N2")},
    },
}

CLASSES["asesino"] = {
    "base": ("vida −30 %, daño c/c +10 %, velocidad +5 %, estamina +30 %, esquiva: distancia +35 %, espera −30 %, coste −20 %, +2 ticks",
             "health −30 %, melee +10 %, speed +5 %, stamina +30 %, dodge: distance +35 %, cooldown −30 %, cost −20 %, +2 ticks"),
    "habilidades": {
        "V": SK("paso_sombrio", ("Paso sombrío", "Shadow Step"),
                ("{0} s de invisibilidad y Velocidad II; tu siguiente golpe c/c en ese tiempo hace +{1%}",
                 "{0} s of invisibility and Speed II; your next melee blow in that time deals +{1%}"), [4, 0.60], 30,
                ("{0} s de invisibilidad y Velocidad II; tu siguiente golpe c/c en ese tiempo hace +{1%}",
                 "{0} s of invisibility and Speed II; your next melee blow in that time deals +{1%}"), [6, 0.80], icono="minecraft:ink_sac"),
        "B": SK("marca_de_muerte", ("Marca de muerte", "Death Mark"),
                ("el monstruo que miras (hasta {0} bloques) brilla {1} s y recibe +{2%} de tus golpes",
                 "the monster you look at (up to {0} blocks) glows for {1} s and takes +{2%} from your blows"), [16, 10, 0.30], 40,
                ("el monstruo que miras (hasta {0} bloques) brilla {1} s y recibe +{2%} de tus golpes; si muere marcado, la espera baja un {3%}",
                 "the monster you look at (up to {0} blocks) glows for {1} s and takes +{2%} from your blows; if it dies marked, the cooldown drops by {3%}"),
                [16, 10, 0.40, 0.50], icono="minecraft:wither_rose"),
        "N": SK("abanico_de_dagas", ("Abanico de dagas", "Fan of Knives"),
                ("lanzas {0} dagas en abanico de {1}°: {2} de daño cada una y Veneno I {3} s",
                 "you throw {0} knives in a {1}° fan: {2} damage each and Poison I for {3} s"), [5, 60, 4, 3], 25,
                ("lanzas {0} dagas en abanico de {1}°: {2} de daño cada una y Veneno II {3} s",
                 "you throw {0} knives in a {1}° fan: {2} damage each and Poison II for {3} s"), [7, 60, 4, 3], icono="minecraft:iron_sword"),
    },
    "puertas": [S("dodge_cost"), S("stamina_max"), S("backstab"), S("move_speed"), S("sneak_speed"), S("melee_damage")],
    "ramas": {
        "A": {"nombre": ("Sombra", "Shadow"),
              "tronco": [S("dodge_cost"), S("dodge_cooldown"), S("dodge_distance"), N(("Danza", "Dance"), mods=[["dodge_iframes", 1], ["dodge_cooldown", -0.10]])],
              "caminos": [
                  {"nombre": ("Contraataque", "Counterattack"),
                   "nodos": [S("counter"), S("dodge_cost"), N(("Contraataque", "Counterattack"), mods=[["counter", 0.30]]), S("counter"),
                             K(("Filo del viento", "Wind's Edge"), ("tras una esquiva perfecta, tu siguiente golpe en {0} s hace +{1%}",
                                                                    "after a perfect dodge, your next blow within {0} s deals +{1%}"), [2, 0.50],
                               [["armor", -4]], gana="+50 % tras esquiva perfecta", precio="armadura −4")],
                   "lados": [S("stamina_max"), S("dodge_distance")]},
                  {"nombre": ("Espejismo", "Mirage"),
                   "nodos": [S("dodge_distance"), S("dodge_cooldown"),
                             N(("Espejismo", "Mirage"), ("al esquivar, los monstruos a {0} bloques te pierden de vista (cada {1} s)",
                                                         "when you dodge, monsters within {0} blocks lose sight of you (every {1} s)"), [6, 8]),
                             S("dodge_cost"),
                             K(("Sin sombra", "Shadowless"), ("esquivar no cuesta estamina, pero cada esquiva te quita {0} de vida (nunca por debajo de 1)",
                                                             "dodging costs no stamina, but every dodge takes {0} health (never below 1)"), [1],
                               gana="esquivas gratis", precio="1 de vida por esquiva")],
                   "lados": [S("move_speed"), S("fall_damage")]},
              ]},
        "B": {"nombre": ("Filo", "Edge"),
              "tronco": [S("backstab"), S("melee_damage"), S("execute"), N(("Puñalada", "Stab"), mods=[["backstab", 0.30]])],
              "caminos": [
                  {"nombre": ("Ejecutor", "Executor"),
                   "nodos": [S("execute"), S("melee_damage"), N(("Ejecutor", "Executor"), mods=[["execute", 0.25]]), S("execute"),
                             K(("Golpe de gracia", "Coup de Grâce"), ("un golpe c/c mata a un monstruo normal por debajo del {0%} de vida; a los que están por encima del {1%} les haces −{2%}",
                                                                      "a melee blow kills a normal monster under {0%} health; foes over {1%} take −{2%} from you"), [0.15, 0.50, 0.15],
                               gana="remate instantáneo bajo el 15 %", precio="−15 % a enemigos sanos")],
                   "lados": [S("backstab"), S("move_speed")]},
                  {"nombre": ("Sangría", "Bloodletting"),
                   "nodos": [S("melee_damage"), S("backstab"),
                             N(("Golpe letal", "Lethal Blow"), ("matar c/c devuelve {0} de estamina y da Velocidad II {1} s", "a melee kill gives back {0} stamina and Speed II for {1} s"), [20, 3]),
                             S("stamina_regen"),
                             K(("Frenesí", "Frenzy"), ("cada muerte c/c da +{0%} de daño c/c {1} s (hasta {2} veces; se pierde al recibir un golpe)",
                                                       "every melee kill gives +{0%} melee damage for {1} s (up to {2} times; lost when you are hit)"), [0.08, 5, 4],
                               [["max_health", -0.10]], gana="hasta +32 % encadenando muertes", precio="vida −10 %, se pierde al recibir un golpe")],
                   "lados": [S("execute"), S("stamina_regen")]},
              ]},
        "C": {"nombre": ("Sigilo", "Stealth"),
              "tronco": [S("sneak_speed"), S("fall_damage"), S("move_speed"),
                         N(("Paso quedo", "Soft Step"), ("agachado, los monstruos te ven a la mitad de distancia", "while sneaking, monsters see you from half as far"), [0.5],
                           [["sneak_speed", 0.20]])],
              "caminos": [
                  {"nombre": ("Acróbata", "Acrobat"),
                   "nodos": [S("jump"), S("fall_damage"), N(("Acróbata", "Acrobat"), mods=[["fall_damage", -0.30], ["jump", 0.10]]), S("move_speed"),
                             K(("Funámbulo", "Tightrope"), ("tras caer más de {0} bloques, tu siguiente golpe en {1} s hace +{2%}",
                                                            "after a fall of more than {0} blocks, your next blow within {1} s deals +{2%}"), [3, 2, 0.40],
                               [["knockback", -0.30]], gana="+40 % al caer sobre el enemigo", precio="resistencia al empuje −30 %")],
                   "lados": [S("jump"), S("stamina_max")]},
                  {"nombre": ("Evasión", "Evasion"),
                   "nodos": [S("dodge_distance"), S("move_speed"),
                             N(("Evasión", "Evasion"), ("{0%} de probabilidad de que un proyectil no te haga nada", "{0%} chance a projectile does nothing to you"), [0.12]),
                             S("move_speed"),
                             K(("Fantasma", "Ghost"), ("Paso sombrío dura el doble y el primer golpe no te hace visible; pero su espera +{0%}",
                                                       "Shadow Step lasts twice as long and the first blow does not reveal you; but its cooldown +{0%}"), [0.50],
                               gana="Paso sombrío ×2", precio="espera de Paso sombrío +50 %")],
                   "lados": [S("fall_damage"), S("sneak_speed")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": ("Senda de la sombra", "Path of Shadow"), "nodos": [S("stamina_max"), H("V2"), S("dodge_cost"), S("move_speed")]},
        "S2": {"nombre": ("Senda de la marca", "Path of the Mark"), "nodos": [S("backstab"), H("B"), S("execute"), S("move_speed")], "lado": H("B2")},
        "S3": {"nombre": ("Senda del veneno", "Path of Poison"),
               "nodos": [S("sneak_speed"), N(("Veneno en la hoja", "Poisoned Blade"), ("tus golpes por la espalda envenenan (Veneno I {0} s)", "your backstabs poison (Poison I for {0} s)"), [3]),
                         S("dodge_distance"), H("N")],
               "lado": H("N2")},
    },
}

CLASSES["tanque"] = {
    "base": ("vida +60 %, armadura +2, empuje +30 %, velocidad −12 %, esquiva −35 %, coste de esquiva +20 %, estamina al parar −25 %",
             "health +60 %, armor +2, knockback +30 %, speed −12 %, dodge −35 %, dodge cost +20 %, block stamina −25 %"),
    "habilidades": {
        "V": SK("provocar", ("Provocar", "Taunt"),
                ("los monstruos hostiles a {0} bloques te toman como objetivo; Resistencia I {1} s",
                 "hostile monsters within {0} blocks turn on you; Resistance I for {1} s"), [10, 6], 25,
                ("los monstruos hostiles a {0} bloques te toman como objetivo; Resistencia I {1} s y {2} de absorción",
                 "hostile monsters within {0} blocks turn on you; Resistance I for {1} s and {2} absorption"), [14, 9, 4], icono="minecraft:bell"),
        "B": SK("baluarte", ("Baluarte", "Bulwark"),
                ("{0} s de Resistencia II para ti y Resistencia I para los jugadores a {1} bloques",
                 "Resistance II for you and Resistance I for players within {1} blocks, for {0} s"), [8, 6], 60,
                ("{0} s de Resistencia II para ti y Resistencia I para los jugadores a {1} bloques",
                 "Resistance II for you and Resistance I for players within {1} blocks, for {0} s"), [10, 9], icono="minecraft:netherite_chestplate"),
        "N": SK("embestida_de_escudo", ("Embestida de escudo", "Shield Charge"),
                ("cargas {0} bloques al frente; lo que golpeas recibe {1} de daño, {2} de postura y sale empujado",
                 "you charge {0} blocks ahead; whatever you hit takes {1} damage, {2} posture and is knocked back"), [6, 6, 40], 20,
                ("cargas {0} bloques al frente; lo que golpeas recibe {1} de daño, {2} de postura, sale empujado y queda aturdido",
                 "you charge {0} blocks ahead; whatever you hit takes {1} damage, {2} posture, is knocked back and staggered"), [8, 6, 40], icono="minecraft:shield"),
    },
    "puertas": [S("block_cost"), S("max_health"), S("armor"), S("knockback"), S("stamina_max"), S("melee_damage")],
    "ramas": {
        "A": {"nombre": ("Muralla", "Rampart"),
              "tronco": [S("block_cost"), S("parry_window"), S("block_cost"), N(("Escudo pesado", "Heavy Shield"), mods=[["block_cost", -0.20]])],
              "caminos": [
                  {"nombre": ("Represalia", "Retaliation"),
                   "nodos": [S("block_cost"), S("armor"),
                             N(("Represalia", "Retaliation"), ("quien golpea tu escudo levantado recibe {0} de daño", "whoever strikes your raised shield takes {0} damage"), [3]),
                             S("toughness"),
                             K(("Espinas de acero", "Steel Thorns"), ("quien golpea tu escudo recibe además el {0%} del daño parado", "whoever strikes your shield also takes {0%} of the damage blocked"), [0.30],
                               [["melee_damage", -0.15]], gana="devuelve el 30 % de lo parado", precio="daño c/c −15 %")],
                   "lados": [S("max_health"), S("knockback")]},
                  {"nombre": ("Bastión", "Bastion"),
                   "nodos": [S("parry_window"), S("block_cost"), N(("Bastión", "Bastion"), mods=[["parry_window", 2]]), S("block_cost"),
                             K(("Muralla viva", "Living Wall"), ("mientras levantas el escudo, los jugadores a {0} bloques detrás de ti reciben −{1%} de daño",
                                                                  "while your shield is raised, players within {0} blocks behind you take −{1%} damage"), [3, 0.20],
                               [["move_speed", -0.05]], gana="proteges a los de detrás", precio="velocidad −5 %")],
                   "lados": [S("armor"), S("max_health")]},
              ]},
        "B": {"nombre": ("Coraza", "Plating"),
              "tronco": [S("armor"), S("max_health"), S("toughness"), N(("Piel de hierro", "Iron Skin"), mods=[["armor", 2]])],
              "caminos": [
                  {"nombre": ("Dureza", "Hardness"),
                   "nodos": [S("toughness"), S("damage_taken"), N(("Dureza", "Hardness"), mods=[["toughness", 2], ["damage_taken", -0.08]]), S("armor"),
                             K(("Yunque viviente", "Living Anvil"), mods=[["damage_taken", -0.15], ["knockback", 1.0], ["dodge_cost", 1.0], ["dodge_distance", -0.30]],
                               gana="daño recibido −15 %, sin empuje", precio="esquiva al doble de coste y −30 % de distancia")],
                   "lados": [S("max_health"), S("fire_taken")]},
                  {"nombre": ("Coloso", "Colossus"),
                   "nodos": [S("max_health"), S("max_health"), N(("Coloso", "Colossus"), mods=[["max_health", 0.10]]), S("max_health"),
                             K(("Gigante", "Giant"), mods=[["max_health", 0.20], ["knockback", 0.50], ["stamina_max", -0.20], ["stamina_regen", -0.20]],
                               gana="vida +20 %, empuje +50 %", precio="estamina máx. y regeneración −20 %")],
                   "lados": [S("knockback"), S("armor")]},
              ]},
        "C": {"nombre": ("Firmeza", "Steadfast"),
              "tronco": [S("stamina_max"), S("stamina_regen"), S("knockback"),
                         N(("Recuperación", "Recovery"), ("recuperas {0} de vida cada {1} s", "you recover {0} health every {1} s"), [1, 5])],
              "caminos": [
                  {"nombre": ("Raíces", "Roots"),
                   "nodos": [S("knockback"), S("stamina_max"), N(("Raíces", "Roots"), mods=[["knockback", 0.20], ["stamina_max", 0.10]]), S("stamina_regen"),
                             K(("Último bastión", "Last Stand"), ("una vez cada {0} min, un golpe mortal te deja a 1 de vida con Resistencia III {1} s; mientras se recarga, vida máxima −{2%}",
                                                                 "once every {0} min, a lethal blow leaves you at 1 health with Resistance III for {1} s; while it recharges, max health −{2%}"),
                               [5, 3, 0.20], gana="sobrevives a un golpe mortal", precio="vida −20 % mientras se recarga")],
                   "lados": [S("max_health"), S("damage_taken")]},
                  {"nombre": ("Desafío", "Challenge"),
                   "nodos": [S("max_health"), S("stamina_regen"),
                             N(("Desafío", "Challenge"), ("los monstruos que provocas quedan con Debilidad I {0} s", "the monsters you taunt get Weakness I for {0} s"), [6]),
                             S("damage_taken"),
                             K(("Imán de golpes", "Blow Magnet"), ("los hostiles a {0} bloques van siempre a por ti, y con {1} o más cerca recibes −{2%}",
                                                                  "hostiles within {0} blocks always come for you, and with {1} or more around you take −{2%}"), [6, 3, 0.10],
                               [["melee_damage", -0.10]], gana="−10 % rodeado, atraes a todo", precio="daño c/c −10 %")],
                   "lados": [S("stamina_max"), S("block_cost")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": ("Senda del desafío", "Path of Defiance"), "nodos": [S("armor"), H("V2"), S("max_health"), S("block_cost")]},
        "S2": {"nombre": ("Senda del baluarte", "Path of the Bulwark"), "nodos": [S("max_health"), H("B"), S("toughness"), S("damage_taken")], "lado": H("B2")},
        "S3": {"nombre": ("Senda de la embestida", "Path of the Charge"),
               "nodos": [S("stamina_regen"), N(("Pisotón", "Stomp"), ("al caer desde {0} bloques o más empujas lo que hay a {1} bloques y le haces {2} de postura",
                                                                       "landing from {0} blocks or more pushes back everything within {1} blocks and deals {2} posture"), [3, 3, 10]),
                         S("knockback"), H("N")],
               "lado": H("N2")},
    },
}

CLASSES["mago"] = {
    "base": ("vida −10 %, estamina −10 %, hechizos +10 %, espera −10 %, maná +25 %, regeneración de maná ×6",
             "health −10 %, stamina −10 %, spells +10 %, cooldown −10 %, mana +25 %, mana regeneration ×6"),
    "habilidades": {
        "V": SK("nova_arcana", ("Nova arcana", "Arcane Nova"),
                ("un anillo de {0} bloques: {1} de daño mágico a los hostiles, y los empuja", "a ring of {0} blocks: {1} magic damage to hostiles, and it pushes them back"), [5, 6], 20,
                ("un anillo de {0} bloques: {1} de daño mágico a los hostiles, y los empuja", "a ring of {0} blocks: {1} magic damage to hostiles, and it pushes them back"), [6, 9],
                icono="minecraft:fire_charge"),
        "B": SK("concentracion", ("Concentración", "Focus"),
                ("{0} s con la espera de los hechizos al {1%} y sin coste de maná", "for {0} s your spells wait {1%} as long and cost no mana"), [8, 0.50], 60,
                ("{0} s con la espera de los hechizos al {1%} y sin coste de maná", "for {0} s your spells wait {1%} as long and cost no mana"), [11, 0.50],
                icono="minecraft:ender_eye"),
        "N": SK("meteoro", ("Meteoro", "Meteor"),
                ("tras {0} s cae un meteoro donde miras (hasta {1} bloques): {2} de daño mágico en {3} bloques y fuego {4} s; cuesta {5} de maná",
                 "after {0} s a meteor falls where you look (up to {1} blocks): {2} magic damage within {3} blocks and fire for {4} s; costs {5} mana"),
                [1.5, 24, 12, 3, 3, 40], 40,
                ("tras {0} s cae un meteoro donde miras (hasta {1} bloques): {2} de daño mágico en {3} bloques y fuego {4} s; cuesta {5} de maná",
                 "after {0} s a meteor falls where you look (up to {1} blocks): {2} magic damage within {3} blocks and fire for {4} s; costs {5} mana"),
                [1.5, 24, 16, 4, 3, 40], icono="minecraft:magma_block"),
    },
    "puertas": [S("spell_damage"), S("mana_max"), S("mana_regen"), S("mana_max"), S("magic_taken"), S("max_health")],
    "ramas": {
        "A": {"nombre": ("Arcano", "Arcane"),
              "tronco": [S("spell_damage"), S("spell_charge"), S("charge_bonus"), N(("Sobrecarga arcana", "Arcane Overload"), mods=[["charge_bonus", 0.20]])],
              "caminos": [
                  {"nombre": ("Catalizador", "Catalyst"),
                   "nodos": [S("spell_damage"), S("spell_damage"),
                             N(("Catalizador", "Catalyst"), ("un hechizo que mata te devuelve {0} de maná", "a spell that kills gives you back {0} mana"), [5], [["spell_damage", 0.05]]),
                             S("dodge_cooldown"),
                             K(("Hechizo encadenado", "Chain Spell"), ("el proyectil del báculo salta a otro enemigo a {0} bloques con el {1%} del daño",
                                                                       "the staff's bolt leaps to another foe within {0} blocks for {1%} of its damage"), [5, 0.50],
                               [["spell_cooldown", 0.20]], gana="rebote al 50 %", precio="espera de hechizos +20 %")],
                   "lados": [S("spell_damage"), S("magic_taken")]},
                  {"nombre": ("Sobrecarga", "Overcharge"),
                   "nodos": [S("charge_bonus"), S("spell_charge"),
                             N(("Carga profunda", "Deep Charge"), ("mantener la carga llena {0} s más añade +{1%} al hechizo", "holding a full charge {0} s longer adds +{1%} to the spell"), [1, 0.15]),
                             S("charge_bonus"),
                             K(("Todo o nada", "All or Nothing"), ("los hechizos sin cargar (menos de un tercio) hacen −{0%}", "spells cast without charging (under a third) deal −{0%}"), [0.40],
                               [["charge_bonus", 0.60]], gana="carga completa +60 %", precio="sin cargar −40 %")],
                   "lados": [S("stamina_max"), S("move_speed")]},
              ]},
        "B": {"nombre": ("Flujo", "Flow"),
              "tronco": [S("mana_regen"), S("spell_cooldown"), S("spell_cost"), N(("Mente clara", "Clear Mind"), mods=[["mana_regen", 1.0]])],
              "caminos": [
                  {"nombre": ("Canalización", "Channeling"),
                   "nodos": [S("magic_taken"), S("spell_charge"), N(("Canalización", "Channeling"), mods=[["spell_charge", -0.20], ["mana_max", 0.25]]), S("dodge_distance"),
                             K(("Pozo sin fondo", "Bottomless Well"), ("tu maná se regenera a la mitad", "your mana regenerates at half the rate"), [0.50],
                               [["mana_max", 0.60]], gana="maná +60 %", precio="regeneración ×0,5")],
                   "lados": [S("stamina_regen"), S("max_health")]},
                  {"nombre": ("Economía", "Thrift"),
                   "nodos": [S("stamina_max"), S("magic_taken"), N(("Economía arcana", "Arcane Thrift"), mods=[["spell_cost", -0.20]]), S("dodge_cost"),
                             K(("Sangre por maná", "Blood for Mana"), ("sin maná, los hechizos se pagan con vida ({0} de vida por cada {1} de maná que falte); tu maná se regenera un {2%} más lento",
                                                                       "without mana, spells are paid in health ({0} health for every {1} mana missing); your mana regenerates {2%} slower"),
                               [1, 5, 0.30], gana="lanzar sin maná", precio="vida por maná, regeneración −30 %")],
                   "lados": [S("spell_cooldown"), S("move_speed")]},
              ]},
        "C": {"nombre": ("Égida", "Aegis"),
              "tronco": [S("magic_taken"), S("max_health"), S("dodge_distance"), N(("Barrera", "Barrier"), mods=[["magic_taken", -0.20]])],
              "caminos": [
                  {"nombre": ("Paso etéreo", "Ethereal Step"),
                   "nodos": [S("dodge_distance"), S("dodge_cooldown"), N(("Paso etéreo", "Ethereal Step"), mods=[["dodge_distance", 0.15], ["dodge_cooldown", -0.10]]), S("dodge_cost"),
                             K(("Parpadeo", "Blink"), ("al esquivar te vuelves invisible {0} s, gastando {1} de maná", "dodging turns you invisible for {0} s, spending {1} mana"), [1, 15],
                               [["dodge_distance", 0.50], ["dodge_cooldown", 0.30]], gana="esquiva +50 % de distancia e invisible", precio="maná por esquiva, espera +30 %")],
                   "lados": [S("move_speed"), S("stamina_max")]},
                  {"nombre": ("Égida", "Aegis"),
                   "nodos": [S("max_health"), S("damage_taken"), N(("Égida", "Aegis"), ("cada {0} s ganas {1} de absorción", "every {0} s you gain {1} absorption"), [30, 4]), S("max_health"),
                             K(("Escudo de maná", "Mana Shield"), ("el {0%} del daño que recibes lo paga el maná ({1} de maná por punto); tu maná se regenera un {2%} más lento",
                                                                   "{0%} of the damage you take is paid by your mana ({1} mana per point); your mana regenerates {2%} slower"),
                               [0.30, 2, 0.25], gana="30 % del daño al maná", precio="regeneración −25 %")],
                   "lados": [S("magic_taken"), S("max_health")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": ("Senda de la nova", "Path of the Nova"), "nodos": [S("move_speed"), H("V2"), S("stamina_max"), S("magic_taken")]},
        "S2": {"nombre": ("Senda de la calma", "Path of Calm"), "nodos": [S("dodge_distance"), H("B"), S("max_health"), S("max_health")], "lado": H("B2")},
        "S3": {"nombre": ("Senda del meteoro", "Path of the Meteor"),
               "nodos": [S("magic_taken"), N(("Runa de escarcha", "Frost Rune"), ("tus hechizos dejan Lentitud I {0} s", "your spells leave Slowness I for {0} s"), [2]),
                         S("max_health"), H("N")],
               "lado": H("N2")},
    },
}

CLASSES["curandero"] = {
    "base": ("curación +50 %, maná +15 %, regeneración de maná ×4, regeneración de estamina +10 %",
             "healing +50 %, mana +15 %, mana regeneration ×4, stamina regeneration +10 %"),
    "habilidades": {
        "V": SK("pulso_sanador", ("Pulso sanador", "Healing Pulse"),
                ("cura {0} (× tu curación) a ti, a los jugadores y a tus animales a {1} bloques", "heals {0} (× your healing) to you, players and your animals within {1} blocks"), [4, 8], 30,
                ("cura {0} (× tu curación) a ti, a los jugadores y a tus animales a {1} bloques, y les quita Veneno y Marchitamiento",
                 "heals {0} (× your healing) to you, players and your animals within {1} blocks, and cures Poison and Wither"), [6, 8], icono="minecraft:glistering_melon_slice"),
        "B": SK("resurgir", ("Resurgir", "Resurgence"),
                ("el aliado que miras (hasta {0} bloques) recupera el {1%} de la vida que le falta y Regeneración II {2} s",
                 "the ally you look at (up to {0} blocks) gets back {1%} of their missing health and Regeneration II for {2} s"), [16, 0.50, 5], 90,
                ("el aliado que miras (hasta {0} bloques) recupera el {1%} de la vida que le falta y Regeneración II {2} s",
                 "the ally you look at (up to {0} blocks) gets back {1%} of their missing health and Regeneration II for {2} s"), [16, 0.50, 8], 70,
                icono="minecraft:totem_of_undying"),
        "N": SK("escudo_de_luz", ("Escudo de luz", "Shield of Light"),
                ("el aliado que miras (hasta {0} bloques), o tú, gana {1} de absorción y Resistencia I {2} s",
                 "the ally you look at (up to {0} blocks), or you, gains {1} absorption and Resistance I for {2} s"), [16, 6, 6], 35,
                ("el aliado que miras (hasta {0} bloques), o tú, gana {1} de absorción y Resistencia I {2} s, y pierde un efecto negativo",
                 "the ally you look at (up to {0} blocks), or you, gains {1} absorption and Resistance I for {2} s, and loses a harmful effect"), [16, 8, 6],
                icono="minecraft:golden_apple"),
    },
    "puertas": [S("healing"), S("mana_max"), S("damage_taken"), S("mana_regen"), S("stamina_regen"), S("max_health")],
    "ramas": {
        "A": {"nombre": ("Sanación", "Healing"),
              "tronco": [S("healing"), S("healing"), S("spell_cost"), N(("Manos cálidas", "Warm Hands"), mods=[["healing", 0.15]])],
              "caminos": [
                  {"nombre": ("Milagro", "Miracle"),
                   "nodos": [S("healing"), S("healing"),
                             N(("Milagro", "Miracle"), ("una cura que llena la vida da {0} de absorción", "a heal that fills someone up gives {0} absorption"), [2], [["healing", 0.15]]),
                             S("healing"),
                             K(("Mártir", "Martyr"), ("cada cura a otro te cuesta {0} de vida (como mucho una vez por segundo, nunca por debajo de 1)",
                                                      "every heal on someone else costs you {0} health (at most once a second, never below 1)"), [1],
                               [["healing", 0.40]], gana="curación +40 %", precio="1 de vida por cura")],
                   "lados": [S("mana_max"), S("spell_charge")]},
                  {"nombre": ("Renuevo", "Renewal"),
                   "nodos": [S("healing"), S("mana_regen"),
                             N(("Renuevo", "Renewal"), ("lo que curas recibe además Regeneración I {0} s", "whatever you heal also gets Regeneration I for {0} s"), [3]),
                             S("spell_cost"),
                             K(("Florecer", "Bloom"), ("tus curas curan el {0%} repartido en {1} s en vez de al instante", "your heals mend {0%} spread over {1} s instead of at once"), [1.50, 4],
                               gana="curas ×1,5", precio="sin curas instantáneas")],
                   "lados": [S("healing"), S("max_health")]},
              ]},
        "B": {"nombre": ("Amparo", "Shelter"),
              "tronco": [S("damage_taken"), S("healing"), S("max_health"),
                         N(("Bendición", "Blessing"), ("curar a alguien por debajo del {0%} de vida le da Resistencia I {1} s",
                                                        "healing someone under {0%} health gives them Resistance I for {1} s"), [0.50, 4])],
              "caminos": [
                  {"nombre": ("Purificar", "Cleanse"),
                   "nodos": [S("healing"), S("damage_taken"),
                             N(("Purificar", "Cleanse"), ("tus curas quitan Veneno, Marchitamiento, Debilidad y Lentitud", "your heals remove Poison, Wither, Weakness and Slowness")),
                             S("magic_taken"),
                             K(("Tierra sagrada", "Hallowed Ground"), ("Pulso sanador deja {0} s un círculo de {1} bloques que da Resistencia I a los aliados; pero su espera +{2%}",
                                                                       "Healing Pulse leaves a circle of {1} blocks for {0} s that gives allies Resistance I; but its cooldown +{2%}"),
                               [6, 4, 0.50], gana="zona de Resistencia I", precio="espera de Pulso +50 %")],
                   "lados": [S("max_health"), S("stamina_regen")]},
                  {"nombre": ("Vínculo", "Bond"),
                   "nodos": [S("max_health"), S("healing"),
                             N(("Vínculo", "Bond"), ("te curas el {0%} de lo que curas a otros", "you heal yourself {0%} of what you heal others"), [0.20]),
                             S("max_health"),
                             K(("Lazo vital", "Life Link"), ("recibes tú el {0%} del daño de los jugadores a {1} bloques", "you take {0%} of the damage of players within {1} blocks"), [0.25, 8],
                               [["max_health", 0.10]], gana="vida +10 %, proteges a tus aliados", precio="te comes el 25 % de su daño")],
                   "lados": [S("damage_taken"), S("mana_max")]},
              ]},
        "C": {"nombre": ("Fe", "Faith"),
              "tronco": [S("stamina_regen"), S("mana_regen"), S("max_health"), N(("Serenidad", "Serenity"), mods=[["mana_regen", 1.0], ["stamina_regen", 0.10]])],
              "caminos": [
                  {"nombre": ("Aura", "Aura"),
                   "nodos": [S("max_health"), S("mana_regen"),
                             N(("Aura", "Aura"), ("tú y tus aliados a {0} bloques recuperáis {1} de vida cada {2} s", "you and your allies within {0} blocks recover {1} health every {2} s"), [6, 0.5, 3]),
                             S("healing"),
                             K(("Peregrino", "Pilgrim"), ("el Aura llega a {0} bloques y cura {1}; pero todo tu daño −{2%}", "the Aura reaches {0} blocks and heals {1}; but all your damage −{2%}"),
                               [10, 1, 0.20], gana="Aura doble y más grande", precio="todo tu daño −20 %")],
                   "lados": [S("max_health"), S("stamina_regen")]},
                  {"nombre": ("Voluntad", "Will"),
                   "nodos": [S("max_health"), S("stamina_max"), N(("Voluntad", "Will"), mods=[["max_health", 0.08]]), S("damage_taken"),
                             K(("Martillo de la fe", "Hammer of Faith"), ("tus golpes c/c curan a los aliados a {0} bloques el {1%} del daño hecho",
                                                                          "your melee blows heal allies within {0} blocks for {1%} of the damage dealt"), [4, 0.20],
                               [["healing", -0.20]], gana="curar pegando", precio="curación −20 %")],
                   "lados": [S("stamina_max"), S("mana_regen")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": ("Senda del pulso", "Path of the Pulse"), "nodos": [S("healing"), H("V2"), S("mana_max"), S("max_health")]},
        "S2": {"nombre": ("Senda del resurgir", "Path of Resurgence"), "nodos": [S("mana_regen"), H("B"), S("max_health"), S("stamina_regen")], "lado": H("B2")},
        "S3": {"nombre": ("Senda de la luz", "Path of Light"),
               "nodos": [S("max_health"), N(("Rocío", "Dew"), ("tu cura sobre alguien por debajo del {0%} de vida es +{1%} (cada {2} s)",
                                                                 "your heal on someone under {0%} health is +{1%} (every {2} s)"), [0.25, 0.50, 20]),
                         S("mana_max"), H("N")],
               "lado": H("N2")},
    },
}

CLASSES["arquero"] = {
    "base": ("vida −10 %, velocidad +8 %, proyectiles +15 %, tensado +10 %, esquiva +20 %, espera de esquiva −15 %, caída −25 %",
             "health −10 %, speed +8 %, projectiles +15 %, draw +10 %, dodge +20 %, dodge cooldown −15 %, fall −25 %"),
    "habilidades": {
        "V": SK("salto_atras", ("Salto atrás", "Backflip"),
                ("un salto hacia atrás de unos {0} bloques y Caída lenta {1} s", "a leap of about {0} blocks backwards and Slow Falling for {1} s"), [6, 2], 12,
                ("un salto hacia atrás de unos {0} bloques y Caída lenta {1} s; tu siguiente flecha en {2} s hace +{3%}",
                 "a leap of about {0} blocks backwards and Slow Falling for {1} s; your next arrow within {2} s deals +{3%}"), [6, 2, 3, 0.25], 8,
                icono="minecraft:rabbit_foot"),
        "B": SK("lluvia_de_flechas", ("Lluvia de flechas", "Arrow Rain"),
                ("{0} flechas en {1} s sobre un círculo de {2} bloques donde miras (hasta {3}), {4} de daño cada una",
                 "{0} arrows over {1} s on a circle of {2} blocks where you look (up to {3}), {4} damage each"), [12, 2, 3, 32, 4], 45,
                ("{0} flechas en {1} s sobre un círculo de {2} bloques donde miras (hasta {3}), {4} de daño cada una",
                 "{0} arrows over {1} s on a circle of {2} blocks where you look (up to {3}), {4} damage each"), [18, 2, 4, 32, 4],
                icono="minecraft:tipped_arrow"),
        "N": SK("flecha_de_red", ("Flecha de red", "Net Arrow"),
                ("una red donde miras (hasta {0} bloques) atrapa a los monstruos a {1} bloques: Lentitud IV {2} s",
                 "a net where you look (up to {0} blocks) catches the monsters within {1} blocks: Slowness IV for {2} s"), [24, 3, 3], 20,
                ("una red donde miras (hasta {0} bloques) atrapa a los monstruos a {1} bloques: Lentitud IV {2} s, y tus flechas les hacen +{3%}",
                 "a net where you look (up to {0} blocks) catches the monsters within {1} blocks: Slowness IV for {2} s, and your arrows deal them +{3%}"),
                [24, 4, 4, 0.15], icono="minecraft:cobweb"),
    },
    "puertas": [S("projectile_damage"), S("draw_speed"), S("arrow_speed"), S("move_speed"), S("dodge_distance"), S("max_health")],
    "ramas": {
        "A": {"nombre": ("Puntería", "Aim"),
              "tronco": [S("projectile_damage"), S("headshot"), S("projectile_damage"), N(("Ojo de halcón", "Hawk Eye"), mods=[["projectile_damage", 0.06]])],
              "caminos": [
                  {"nombre": ("Tiro a la cabeza", "Headshot"),
                   "nodos": [S("headshot"), S("headshot"), N(("Tiro a la cabeza", "Headshot"), mods=[["headshot", 0.20]]), S("headshot"),
                             K(("Francotirador", "Sniper"), ("Tiro lejano cuenta el doble; a menos de {0} bloques haces −{1%}", "Long Shot counts double; within {0} blocks you deal −{1%}"),
                               [8, 0.30], [["headshot", 0.40]], gana="cabeza +40 %, lejano ×2", precio="−30 % de cerca")],
                   "lados": [S("draw_speed"), S("arrow_speed")]},
                  {"nombre": ("Tiro lejano", "Long Shot"),
                   "nodos": [S("arrow_speed"), S("draw_speed"),
                             N(("Tiro lejano", "Long Shot"), ("+{1%} por bloque más allá de {0}, hasta +{2%}", "+{1%} per block past {0}, up to +{2%}"), [10, 0.02, 0.30]),
                             S("arrow_speed"),
                             K(("Ojo de águila", "Eagle Eye"), ("agachado, tus flechas vuelan rectas y hacen +{0%}", "while sneaking, your arrows fly straight and deal +{0%}"), [0.15],
                               [["draw_speed", -0.20]], gana="tiro recto +15 % agachado", precio="tensado −20 %")],
                   "lados": [S("headshot"), S("fall_damage")]},
              ]},
        "B": {"nombre": ("Tensión", "Draw"),
              "tronco": [S("draw_speed"), S("draw_speed"), S("arrow_speed"), N(("Mano rápida", "Quick Hand"), mods=[["draw_speed", 0.12]])],
              "caminos": [
                  {"nombre": ("Ráfaga", "Volley"),
                   "nodos": [S("draw_speed"), S("stamina_cost"), N(("Flecha veloz", "Swift Arrow"), mods=[["arrow_speed", 0.15]]), S("draw_speed"),
                             K(("Ráfaga", "Volley"), ("un arco forjado a {0%} de tensión dispara como a tensión completa", "a forged bow at {0%} draw shoots as if fully drawn"), [0.75],
                               [["projectile_damage", -0.20]], gana="un tercio más de disparos", precio="proyectiles −20 %")],
                   "lados": [S("arrow_speed"), S("move_speed")]},
                  {"nombre": ("Tiro certero", "True Shot"),
                   "nodos": [S("arrow_speed"), S("headshot"),
                             N(("Tiro certero", "True Shot"), ("un tiro a tensión completa deja Lentitud II {0} s", "a fully drawn shot leaves Slowness II for {0} s"), [2]),
                             S("draw_speed"),
                             K(("Flecha perforante", "Piercing Arrow"), ("las flechas a tensión completa atraviesan {0} enemigos", "fully drawn arrows pierce {0} foes"), [2],
                               [["draw_speed", -0.20]], gana="atraviesa 2", precio="tensado −20 %")],
                   "lados": [S("fall_damage"), S("max_health")]},
              ]},
        "C": {"nombre": ("Viento", "Wind"),
              "tronco": [S("move_speed"), S("dodge_distance"), S("dodge_cooldown"), N(("Zancada", "Stride"), mods=[["move_speed", 0.05]])],
              "caminos": [
                  {"nombre": ("Rodar", "Roll"),
                   "nodos": [S("dodge_cost"), S("dodge_distance"), N(("Rodar", "Roll"), mods=[["dodge_cost", -0.20], ["dodge_iframes", 1]]), S("dodge_cooldown"),
                             K(("Disparo en carrera", "Running Shot"), ("mientras te mueves, tensas un {0%} más rápido", "while you move, you draw {0%} faster"), [0.25],
                               [["max_health", -0.10]], gana="tensado +25 % en movimiento", precio="vida −10 %")],
                   "lados": [S("move_speed"), S("stamina_max")]},
                  {"nombre": ("Pluma", "Feather"),
                   "nodos": [S("fall_damage"), S("jump"), N(("Pluma", "Feather"), mods=[["fall_damage", -0.40], ["jump", 0.12]]), S("jump"),
                             K(("Halcón", "Falcon"), ("las flechas que disparas en el aire hacen +{0%}; las que disparas en el suelo, −{1%}",
                                                     "arrows you shoot in the air deal +{0%}; those you shoot on the ground, −{1%}"), [0.25, 0.10],
                               gana="+25 % en el aire", precio="−10 % en el suelo")],
                   "lados": [S("move_speed"), S("fall_damage")]},
              ]},
    },
    "sendas": {
        "S1": {"nombre": ("Senda del salto", "Path of the Leap"), "nodos": [S("headshot"), H("V2"), S("draw_speed"), S("move_speed")]},
        "S2": {"nombre": ("Senda de la lluvia", "Path of the Rain"), "nodos": [S("arrow_speed"), H("B"), S("dodge_distance"), S("move_speed")], "lado": H("B2")},
        "S3": {"nombre": ("Senda del trampero", "Path of the Trapper"),
               "nodos": [S("fall_damage"), N(("Marca del cazador", "Hunter's Mark"), ("la primera flecha que acierta marca {0} s: tus siguientes flechas le hacen +{1%}",
                                                                                      "the first arrow that hits marks for {0} s: your next arrows deal it +{1%}"), [8, 0.10]),
                         S("move_speed"), H("N")],
               "lado": H("N2")},
    },
}

# ------------------------------------------------------------------ the forging region (what is left of the Herrero)
# (id, name, mods, text, numbers): the same 13 nodes in every tree. Inner ones sit between two doors; the outer
# ones hang off them, and Temple II off Temple.
FORGE_INNER = [
    ("forja.ojo_del_martillo", ("Ojo del martillo", "Hammer's Eye"), [["forge_window", 0.01]], None, []),
    ("forja.metal_docil", ("Metal dócil", "Pliant Metal"), [["potential", 5]], None, []),
    ("forja.remiendo", ("Remiendo", "Patchwork"), [["repair", 0.25]], None, []),
    ("forja.fuelle", ("Fuelle", "Bellows"), [["foundry_speed", 0.20]],
     ("las montadoras a {0} bloques de ti trabajan más rápido", "assemblers within {0} blocks of you work faster"), [8]),
    ("forja.mano_firme", ("Mano firme", "Steady Hand"), [["upgrade_bonus", 1]], None, []),
    ("forja.brazo_de_herrero", ("Brazo de herrero", "Smith's Arm"), [["smith_weapon", 0.08]], None, []),
]
FORGE_OUTER = [
    ("forja.golpe_de_maestro", ("Golpe de maestro", "Master's Stroke"), [["forge_window", 0.01]], None, []),
    ("forja.alma_del_metal", ("Alma del metal", "Soul of the Metal"), [["potential", 5]], None, []),
    ("forja.temple_de_campana", ("Temple de campaña", "Field Tempering"), [],
     ("la pieza forjada de tu mano recupera el {0%} de su durabilidad cada minuto", "the forged piece in your hand mends {0%} of its durability every minute"), [0.01]),
    ("forja.ajuste_fino", ("Ajuste fino", "Fine Fit"), [["assembler_potential", 5]],
     ("lo que montan las montadoras a {0} bloques de ti nace con más potencial", "what assemblers within {0} blocks of you make is born with more potential"), [8]),
    ("forja.carga_honda", ("Carga honda", "Deep Load"), [["capacity", 2]], None, []),
    ("forja.forja_al_rojo", ("Forja al rojo", "Red-Hot Forging"), [],
     ("tras una forja perfecta, {0} s en que tus golpes c/c prenden fuego {1} s y hacen +{2%}",
      "after a perfect forge, for {0} s your melee blows set fire for {1} s and deal +{2%}"), [60, 3, 0.10]),
]
FORGE_TEMPLE_II = ("forja.temple_de_campana_ii", ("Temple de campaña II", "Field Tempering II"), [],
                   ("la pieza forjada de tu mano y la armadura forjada que llevas puesta recuperan el {0%} de su durabilidad cada minuto",
                    "the forged piece in your hand and the forged armor you wear mend {0%} of their durability every minute"), [0.02])

# ------------------------------------------------------------------ milestones (hitos)
# (id, es name, en name, es condition, en condition, points, how it is detected). Once per player, kept through
# death and class changes, and granted retroactively.
MILESTONES = [
    ("primer_elite", "Primer élite", "First Elite", "Mata un monstruo de amenaza élite", "Kill an elite-threat monster", 1, "muerte (Threat.ELITE o más)"),
    ("campeon", "Primer campeón", "First Champion", "Mata un campeón", "Kill a champion", 2, "forja:forja/elite"),
    ("capitan", "Sin capitán", "No Captain", "Mata al capitán de una banda de saqueadores", "Kill the captain of a band of raiders", 1, "forja:forja/saqueadores"),
    ("herrero_caido", "El Herrero Caído", "The Fallen Smith", "Derrota al Herrero Caído", "Defeat the Fallen Smith", 4, "forja:forja/herrero_caido"),
    ("guardian_de_cuno", "El Guardián de Cuño", "The Die Guardian", "Derrota al Guardián de Cuño", "Defeat the Die Guardian", 4, "muerte (forja:guardian_de_cuno)"),
    ("warden", "El Warden", "The Warden", "Mata al Warden", "Kill the Warden", 4, "muerte (minecraft:warden)"),
    ("wither", "El Wither", "The Wither", "Mata al Wither", "Kill the Wither", 2, "muerte (minecraft:wither)"),
    ("dragon", "El dragón", "The Dragon", "Mata al dragón del End", "Kill the Ender Dragon", 3, "minecraft:end/kill_dragon"),
    ("guardian_anciano", "Guardián anciano", "Elder Guardian", "Mata a un guardián anciano", "Kill an elder guardian", 1, "muerte (minecraft:elder_guardian)"),
    ("heroe", "Héroe de la aldea", "Hero of the Village", "Gana una invasión", "Win a raid", 1, "minecraft:adventure/hero_of_the_village"),
    ("nether", "Al Nether", "Into the Nether", "Entra en el Nether", "Enter the Nether", 1, "minecraft:story/enter_the_nether"),
    ("end", "Al End", "Into the End", "Entra en el End", "Enter the End", 1, "minecraft:story/enter_the_end"),
    ("ciudad_antigua", "Ciudad antigua", "Ancient City", "Entra en una ciudad antigua", "Walk into an ancient city", 1, "estructura minecraft:ancient_city"),
    ("forja_profunda", "Entre estrellas", "Among the Stars", "Enciende el portal de la Forja Profunda", "Light the Deep Forge's portal", 1, "forja:forja/portal"),
    ("golpe_limpio", "Golpe limpio", "Clean Strike", "Consigue una forja perfecta", "Land a perfect forge", 1, "forja:forja/perfecta"),
    ("maestria_5", "Mano hecha", "Practised Hand", "Llega a maestría de herrero 5", "Reach smith mastery 5", 1, "SmithLevel"),
    ("maestria_10", "Maestro del gremio", "Guild Master", "Llega a maestría de herrero 10", "Reach smith mastery 10", 2, "SmithLevel"),
    ("obra_maestra", "Obra maestra", "Masterpiece", "Forja una obra maestra", "Forge a masterpiece", 1, "forja:forja/obra_maestra"),
    ("maestro_forjador", "Maestro forjador", "Master Smith", "Ten un objeto con cinco mejoras al 100 %", "Own gear with five upgrades at 100 %", 1, "forja:forja/maestro"),
]
