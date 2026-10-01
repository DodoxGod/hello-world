"""Writes Forja's language files (es_mx, es_es, en_us). Run from the project root."""

import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forja/lang"

MATERIALS = {
    "madera": ("madera", "Wood"), "piedra": ("piedra", "Stone"), "hueso": ("hueso", "Bone"), "cuero": ("cuero", "Leather"),
    "cobre": ("cobre", "Copper"), "hierro": ("hierro", "Iron"), "oro": ("oro", "Gold"), "amatista": ("amatista", "Amethyst"),
    "diamante": ("diamante", "Diamond"), "obsidiana": ("obsidiana", "Obsidian"), "netherita": ("netherita", "Netherite"),
    "esmeralda": ("esmeralda", "Emerald"), "prismarina": ("prismarina", "Prismarine"), "vara_de_blaze": ("vara de blaze", "Blaze Rod"),
    "cuarzo": ("cuarzo", "Quartz"), "purpur": ("púrpura", "Purpur"), "obsidiana_llorona": ("obsidiana llorona", "Crying Obsidian"),
    "eco": ("eco", "Echo"), "resina": ("resina", "Resin"), "escama": ("escama de armadillo", "Armadillo Scute"),
}
TRAITS = {
    "ascua": (("Ascua", "Ember"), ("El fuego la repara y la enfurece", "Fire mends it and angers it"),
              ("Mientras ardes: +2,5 de daño y repara 3 de durabilidad cada 5 segundos. Armadura: el fuego te dura un 35% menos por pieza.",
               "While you burn: +2.5 damage and 3 durability mended every 5 seconds. Armor: fire burns out 35% sooner per piece.")),
    "cargado": (("Cargado", "Charged"), ("Cada cuarto golpe salta a otro", "Every fourth blow jumps to another"),
                ("Cada golpe guarda una carga; al llegar a 4 el siguiente salta a 2 enemigos a 5 bloques por la mitad del daño.",
                 "Each hit stores a charge; at 4 the next one arcs to 2 enemies within 5 blocks for half the damage.")),
    "animado": (("Animado", "Animate"), ("Para un golpe él solo cada 30 s", "Stops a blow by itself every 30 s"),
                ("Llevada o empuñada, detiene un golpe entero cada 30 segundos, sin tirar dados.",
                 "Worn or held, it stops one blow outright every 30 seconds, with no roll involved.")),
    "diafano": (("Diáfano", "Diaphanous"), ("No pesa nada", "It weighs nothing"),
                ("Armadura: +3% de velocidad y +15% agachado por pieza. Armas y herramientas: +0,3 de velocidad de ataque.",
                 "Armor: +3% speed and +15% crouched per piece. Weapons and tools: +0.3 attack speed.")),
    "solar": (("Solar", "Solar"), ("A pleno sol pega más", "It hits harder in full sun"),
              ("Bajo cielo abierto y de día: +3 de daño, +6 y prende fuego a los no-muertos. Armadura: te cura 0,5 por pieza cada 5 segundos.",
               "Under open sky in daylight: +3 damage, +6 and sets the undead alight. Armor: heals 0.5 per piece every 5 seconds.")),
    "nocturno": (("Nocturno", "Nocturnal"), ("En la oscuridad pega más", "It hits harder in the dark"),
                 ("Donde la luz no llega (nivel 7 o menos): +3,5 de daño. Armadura: Velocidad I, y visión nocturna con las cuatro piezas.",
                  "Where the light does not reach (level 7 or less): +3.5 damage. Armor: Speed I, and night vision with all four pieces.")),
    "vivo": (("Vivo", "Living"), ("Se alimenta de lo que mata", "It feeds on what it kills"),
             ("Cada muerte le repara 25 de durabilidad; si ya está entera, te cura medio corazón.",
              "Every kill mends it 25 durability; when it is already whole, it heals you half a heart.")),
    "afortunado": (("Afortunado", "Lucky"), ("+1 Botín o Fortuna", "+1 Looting or Fortune"),
                   ("Armas: +1 Botín. Herramientas: +1 Fortuna. Armadura: +1 de suerte por pieza.",
                    "Weapons: +1 Looting. Tools: +1 Fortune. Armor: +1 luck per piece.")),
    "acuatico": (("Acuático", "Aquatic"), ("Rinde bajo el agua", "Works well underwater"),
                 ("Herramientas: minan a velocidad normal bajo el agua. Armas: Empalamiento II. Armadura: nadas más rápido y aguantas más sin respirar.",
                  "Tools: mine at full speed underwater. Weapons: Impaling II. Armor: swim faster and hold your breath longer.")),
    "igneo": (("Ígneo", "Fiery"), ("Prende fuego", "Sets things on fire"),
              ("Armas y herramientas: prenden fuego (Aspecto ígneo). Arcos: flechas de fuego. Armadura: el fuego te dura menos.",
               "Weapons and tools: set targets on fire. Bows: flaming arrows. Armor: fire burns out sooner.")),
    "afilado": (("Afilado", "Keen"), ("Más daño mientras está nueva", "More damage while it is new"),
                ("Armas y herramientas: hasta +3 de daño, que baja conforme se desgasta. Armadura: Espinas I.",
                 "Weapons and tools: up to +3 damage, fading as it wears. Armor: Thorns I.")),
    "del_end": (("Del End", "Of the End"), ("Teletransportes del End", "End teleports"),
                ("Herramientas: 30% de mandar lo minado al inventario. Armas: 10% de teletransportar al enemigo. Armadura: 5% por pieza de esquivar un golpe.",
                 "Tools: 30% chance to send drops to your inventory. Weapons: 10% chance to teleport the target. Armor: 5% per piece to dodge a hit.")),
    "llanto": (("Llanto", "Weeping"), ("Se repara sola de noche", "Repairs itself at night"),
               ("De noche repara 2 de durabilidad cada 5 segundos.", "At night it repairs 2 durability every 5 seconds.")),
    "resonante": (("Resonante", "Resonant"), ("Ecos que atraviesan la roca", "Echoes through the rock"),
                  ("Herramientas: al romper una mena se iluminan las iguales a 6 bloques, a través de las paredes. Armas: +1 Brecha. Arcos y ballestas: +1 Perforación. Armadura: inmune a Oscuridad y Ceguera.",
                   "Tools: breaking an ore outlines the same ore within 6 blocks, through walls. Weapons: +1 Breach. Bows and crossbows: +1 Piercing. Armor: immune to Darkness and Blindness.")),
    "pegajoso": (("Pegajoso", "Sticky"), ("Ralentiza y aguanta más", "Slows and lasts longer"),
                 ("Todo: +1 Irrompible. Armas y herramientas: Lentitud II 2 s al golpear. Arcos y ballestas: Lentitud 3 s. Armadura: quien te golpea queda lento 1 s por pieza.",
                  "Everything: +1 Unbreaking. Weapons and tools: Slowness II for 2 s on hit. Bows and crossbows: Slowness for 3 s. Armor: attackers are slowed 1 s per piece.")),
    "acorazado": (("Acorazado", "Plated"), ("Aguanta flechas y explosiones", "Stands up to arrows and blasts"),
                  ("Armadura: +1 Protección contra proyectiles y +1 Protección contra explosiones por pieza. Armas y herramientas: +20% de resistencia al empuje en la mano.",
                   "Armor: +1 Projectile Protection and +1 Blast Protection per piece. Weapons and tools: +20% knockback resistance in hand.")),
}
PARTS = {
    "cabeza_pico": ("Cabeza de pico", "Pickaxe Head"), "cabeza_hacha": ("Cabeza de hacha", "Axe Head"),
    "cabeza_pala": ("Cabeza de pala", "Shovel Head"), "cabeza_azada": ("Cabeza de azada", "Hoe Head"),
    "cabeza_martillo": ("Cabeza de martillo", "Hammer Head"), "hoja": ("Hoja", "Blade"), "mango": ("Mango", "Handle"),
    "atadura": ("Atadura", "Binding"), "guarda": ("Guarda", "Guard"), "placa_casco": ("Placa de casco", "Helmet Plate"),
    "placa_pechera": ("Placa de pechera", "Chestplate Plate"), "placa_grebas": ("Placa de grebas", "Leggings Plate"),
    "placa_botas": ("Placa de botas", "Boots Plate"), "forro": ("Forro", "Lining"), "membrana": ("Membrana", "Membrane"), "bola": ("Bola", "Ball"), "cadena": ("Cadena", "Chain"), "punta_flecha": ("Punta de flecha", "Arrowhead"), "emplumado": ("Emplumado", "Fletching"),
    "garfio": ("Garfio", "Claw"), "placa_barda": ("Placa de barda", "Barding Plate"), "placa_lobo": ("Placa de lobo", "Wolf Plate"),
    "nudillos": ("Nudillos", "Knuckles"), "manopla": ("Manopla", "Mitt"), "remache": ("Remache", "Rivet"),
    "brazos_arco": ("Brazos de arco", "Bow Limbs"), "cuerda": ("Cuerda", "String"),
    "placa_escudo": ("Placa de escudo", "Shield Board"), "borde_escudo": ("Borde de escudo", "Shield Rim"),
    "nucleo": ("Núcleo", "Core"), "engaste": ("Engaste", "Setting"), "tapas": ("Tapas", "Boards"),
    "punta_lanza": ("Punta de lanza", "Spear Tip"), "punta_tridente": ("Punta de tridente", "Trident Head"), "punta_cincel": ("Punta de cincel", "Chisel Edge"), "cabeza_mazo": ("Cabeza de mazo", "Mace Head"),
    # Heavy and light handles and bindings (combat/Grip): a choice, not an upgrade.
    "mango_pesado": ("Mango pesado", "Heavy Handle"), "mango_ligero": ("Mango ligero", "Light Handle"),
    "atadura_pesada": ("Atadura pesada", "Heavy Binding"), "atadura_ligera": ("Atadura ligera", "Light Binding"),
}
TYPES = {
    "pico": ("Pico", "Pickaxe"), "hacha": ("Hacha", "Axe"), "pala": ("Pala", "Shovel"), "azada": ("Azada", "Hoe"),
    "martillo": ("Martillo", "Hammer"), "picahacha": ("Picahacha", "Mattock"), "espada": ("Espada", "Sword"),
    "daga": ("Daga", "Dagger"), "espadon": ("Espadón", "Greatsword"), "casco": ("Casco", "Helmet"),
    "pechera": ("Pechera", "Chestplate"), "grebas": ("Grebas", "Leggings"), "botas": ("Botas", "Boots"),
    "arco": ("Arco", "Bow"), "ballesta": ("Ballesta", "Crossbow"), "escudo": ("Escudo", "Shield"), "lanza": ("Lanza", "Spear"), "tridente": ("Tridente", "Trident"), "cincel": ("Cincel", "Chisel"), "mazo": ("Mazo", "Mace"), "guadana": ("Guadaña", "Scythe"), "mangual": ("Mangual", "Flail"), "flecha": ("Flecha", "Arrow"), "gancho": ("Gancho", "Grappling Hook"), "barda": ("Barda", "Barding"), "armadura_de_lobo": ("Armadura de lobo", "Wolf Armor"), "guanteletes": ("Guanteletes", "Gauntlets"), "cana": ("Caña", "Fishing Rod"), "alas": ("Alas", "Wings"),
    "baculo": ("Báculo", "Staff"), "grimorio": ("Grimorio", "Tome"), "farol": ("Farol de curación", "Healing Lantern"),
}
# name, effect (None for enchantment-backed upgrades, whose effect text is the vanilla enchantment name)
UPGRADES = {
    "lanzacabezas": (("Lanzacabezas", "Head Toss"), ("Lanza la cabeza y mina %s bloques", "Throws the head, mines %s blocks")),
    "veta": (("Veta", "Vein Miner"), ("Rompe vetas de hasta %s bloques", "Breaks veins of up to %s blocks")),
    "excavacion": (("Excavación", "Excavation"), ("Mina en área de %sx%s", "Mines a %sx%s area")),
    "lenador": (("Leñador", "Lumberjack"), ("Tala árboles de hasta %s troncos", "Fells trees of up to %s logs")),
    "fundicion": (("Fundición", "Smelting"), ("%s%% de fundir lo que minas", "%s%% chance to smelt what you mine")),
    "telequinesis": (("Telequinesis", "Telekinesis"), ("%s%% de mandar al inventario lo que minas y lo que cosechas", "%s%% chance to send what you mine and harvest to your inventory")),
    "cosechador": (("Cosechador", "Harvester"), ("Cosecha y resiembra %sx%s", "Harvests and replants %sx%s")),
    "eficiencia": (("Eficiencia", "Efficiency"), None),
    "fortuna": (("Fortuna", "Fortune"), None),
    "toque_de_seda": (("Toque de seda", "Silk Touch"), None),
    "alcance": (("Alcance", "Reach"), ("+%s bloques de alcance, +%s para golpear", "+%s block reach, +%s attack reach")),
    "vampirismo": (("Vampirismo", "Vampirism"), ("Te cura %s%% del daño que haces", "Heals you %s%% of the damage you deal")),
    "decapitador": (("Decapitador", "Beheading"), ("%s%% de soltar la cabeza del enemigo", "%s%% chance to drop the enemy's head")),
    "onda_de_choque": (("Onda de choque", "Shockwave"), ("Salpica %s%% del daño a monstruos cercanos", "Splashes %s%% of the damage to nearby monsters")),
    "tormenta": (("Tormenta", "Storm"), ("%s%% de soltar una descarga eléctrica", "%s%% chance of an electric shock")),
    "escarcha": (("Escarcha", "Frost"), ("Congela y ralentiza %s s", "Freezes and slows for %s s")),
    "veneno": (("Veneno", "Venom"), ("Envenena %s s", "Poisons for %s s")),
    "critico": (("Crítico", "Critical"), ("%s%% de crítico (+50%% de daño)", "%s%% critical chance (+50%% damage)")),
    "frenesi": (("Furia", "Fury"), ("+%s de velocidad de ataque", "+%s attack speed")),
    "filo": (("Filo", "Sharpness"), None),
    "castigo": (("Castigo", "Smite"), None),
    "perdicion_de_artropodos": (("Perdición de artrópodos", "Bane of Arthropods"), None),
    "brecha": (("Brecha", "Breach"), None),
    "aspecto_igneo": (("Aspecto ígneo", "Fire Aspect"), None),
    "empuje": (("Empuje", "Knockback"), None),
    "botin": (("Botín", "Looting"), None),
    "filo_arrasador": (("Filo arrasador", "Sweeping Edge"), None),
    "embestida": (("Embestida", "Lunge"), None),
    "densidad": (("Densidad", "Density"), None),
    "estallido_de_viento": (("Estallido de viento", "Wind Burst"), None),
    "poder": (("Poder", "Power"), None),
    "retroceso": (("Retroceso", "Punch"), None),
    "llama": (("Llama", "Flame"), None),
    "infinidad": (("Infinidad", "Infinity"), None),
    "tension": (("Tensión", "Tension"), ("Tensa %s%% más rápido", "Draws %s%% faster")),
    "rebote": (("Rebote", "Rebound"), ("%s%% de devolver las flechas bloqueadas", "%s%% chance to send blocked arrows back")),
    "puas": (("Púas", "Spikes"), ("%s de daño a quien golpea tu escudo", "Deals %s damage to whoever hits your shield")),
    "reflejos": (("Reflejos", "Reflexes"), ("Levantas el escudo %s%% más rápido", "Raises the shield %s%% faster")),
    "magnetismo": (("Magnetismo", "Magnetism"), ("Atrae objetos a %s bloques", "Pulls items from %s blocks")),
    "vitalidad": (("Vitalidad", "Vitality"), ("+%s corazones de vida", "+%s hearts of health")),
    "resorte": (("Resorte", "Spring"), ("Saltas más y aguantas %s bloques más de caída", "Jump higher, fall %s more blocks safely")),
    "presteza": (("Presteza", "Swiftness"), ("+%s%% de velocidad al caminar", "+%s%% walking speed")),
    "vision_nocturna": (("Visión nocturna", "Night Vision"), ("Visión nocturna activa", "Night vision on")),
    "represalia": (("Represalia ígnea", "Fiery Retaliation"), ("Quema %s s a quien te golpea", "Burns attackers for %s s")),
    "regeneracion": (("Regeneración", "Regeneration"), ("Recupera %s corazones cada 5 s", "Restores %s hearts every 5 s")),
    "proteccion": (("Protección", "Protection"), None),
    "proteccion_contra_fuego": (("Prot. contra fuego", "Fire Protection"), None),
    "proteccion_contra_explosiones": (("Prot. contra explosiones", "Blast Protection"), None),
    "proteccion_contra_proyectiles": (("Prot. contra proyectiles", "Projectile Protection"), None),
    "espinas": (("Espinas", "Thorns"), None),
    "respiracion": (("Respiración", "Respiration"), None),
    "afinidad_acuatica": (("Afinidad acuática", "Aqua Affinity"), None),
    "caida_de_pluma": (("Caída de pluma", "Feather Falling"), None),
    "agilidad_acuatica": (("Agilidad acuática", "Depth Strider"), None),
    "paso_helado": (("Paso helado", "Frost Walker"), None),
    "velocidad_de_alma": (("Velocidad de alma", "Soul Speed"), None),
    "sigilo_veloz": (("Sigilo veloz", "Swift Sneak"), None),
    "irrompible": (("Irrompible", "Unbreaking"), None),
    "reparacion": (("Reparación", "Mending"), None),
    "autorreparacion": (("Autorreparación", "Self-Repair"), ("Repara %s de durabilidad cada 5 s", "Repairs %s durability every 5 s")),
    "recocido": (("Recocido", "Annealing"), ("+%s de potencial: más sitio para todas las demás mejoras", "+%s potential: more room for every other upgrade")),
    "conjuro_veloz": (("Conjuro veloz", "Quick Casting"), ("Conjura %s%% más rápido", "Casts %s%% faster")),
    "sobrecarga": (("Sobrecarga", "Overcharge"), ("Cada %s.º hechizo sale más grande y con +%s%% de daño", "Every %sth spell comes out bigger, with +%s%% damage")),
    "resonancia": (("Resonancia", "Resonance"), ("El hechizo se repite con el %s%% del daño", "The spell repeats at %s%% of its damage")),
    "prisma": (("Prisma", "Prism"), ("Abanico de tres que se reparte el daño del hechizo; cada lateral pesa un %s%% del central",
                                     "A fan of three sharing the spell's damage out; each side bolt weighs %s%% of the middle one")),
    "buscador": (("Buscador", "Seeker"), ("El proyectil gira %s° por tic hacia quien te ataca", "The bolt turns %s° a tick towards what hunts you")),
    "tinta_indeleble": (("Tinta indeleble", "Indelible Ink"), ("La runa dura %s s más", "The rune lasts %s s longer")),
    "vortice": (("Vórtice", "Vortex"), ("Cada mordisco arrastra %s bloques hacia el centro de la runa", "Every bite drags %s blocks towards the middle of the rune")),
    "santuario": (("Santuario", "Sanctuary"), ("Sobre tu propia runa te curas %s corazones por segundo", "On your own rune you mend %s hearts a second")),
    # mana (magic/Mana) and stamina (combat/Stamina), Andy 2026-09-28
    "concentracion": (("Concentración", "Focus"), ("Los hechizos cuestan un %s%% menos de maná", "Spells cost %s%% less mana")),
    "sifon": (("Sifón", "Siphon"), ("Si el hechizo alcanza algo, te devuelve el %s%% del maná que costó", "When the spell hits something, %s%% of the mana it cost comes back")),
    "descarga": (("Descarga", "Discharge"), ("Con la carga llena vuelca todo el maná: +%s%% de daño por cada 10 de maná de más, hasta el doble",
                                              "A full charge pours in all your mana: +%s%% damage for every 10 mana beyond the cost, up to double")),
    "meditacion": (("Meditación", "Meditation"), ("En la mano: el maná vuelve un %s%% más rápido", "In hand: mana comes back %s%% faster")),
    "reserva": (("Reserva", "Reservoir"), ("+%s de maná máximo", "+%s max mana")),
    "flujo": (("Flujo", "Flow"), ("El maná vuelve un %s%% más rápido", "Mana comes back %s%% faster")),
    "filo_arcano": (("Filo arcano", "Arcane Edge"), ("Cada golpe gasta %s de maná y suma un %s%% de daño mágico",
                                                    "Every blow spends %s mana and adds %s%% as magic damage")),
    "estallido_arcano": (("Estallido arcano", "Arcane Burst"), ("El golpe cargado a tope gasta %s de maná y estalla: %s%% del golpe a todo lo que hay a %s bloques",
                                                              "A fully charged blow spends %s mana and bursts: %s%% of the blow to everything within %s blocks")),
    "paso_arcano": (("Paso arcano", "Arcane Step"), ("Esquivar con ella en la mano gasta %s de maná y te lleva un %s%% más lejos",
                                                    "Dodging with it in hand spends %s mana and carries you %s%% further")),
    "aguante": (("Aguante", "Endurance"), ("+%s de estamina máxima", "+%s max stamina")),
    "fuelle": (("Fuelle", "Bellows"), ("La estamina vuelve un %s%% más rápido", "Stamina comes back %s%% faster")),
    "quiebro": (("Quiebro", "Sidestep"), ("La esquiva te lleva un %s%% más lejos", "The dodge carries you %s%% further")),
    "impulso": (("Impulso", "Impetus"), ("La embestida de los guanteletes te lleva un %s%% más lejos", "The gauntlets' lunge carries you %s%% further")),
    "soltura": (("Soltura", "Ease"), ("Saltar, esquivar y los ataques especiales cuestan un %s%% menos de estamina",
                                      "Jumps, dodges and special attacks cost %s%% less stamina")),
    "luz": (("Luz", "Lantern"), ("%s%% de poner una de tus antorchas al minar a oscuras", "%s%% chance to place one of your torches when mining in the dark")),
    "sabiduria": (("Sabiduría", "Wisdom"), ("+%s%% de experiencia de monstruos y +%s por mineral", "+%s%% experience from mobs and +%s per ore")),
    "ejecucion": (("Ejecución", "Execution"), ("+%s%% de daño a enemigos con menos de 30%% de vida", "+%s%% damage to enemies under 30%% health")),
    "matagigantes": (("Matagigantes", "Giant Slayer"), ("+%s%% de daño a enemigos con más vida máxima que tú", "+%s%% damage to enemies with more max health than you")),
    "multidisparo": (("Multidisparo", "Multishot"), None),
    "carga_rapida": (("Carga rápida", "Quick Charge"), None),
    "cebo": (("Cebo", "Lure"), None),
    "aerodinamica": (("Aerodinámica", "Aerodynamics"), ("Cambia %s%% de la reserva de vuelo por velocidad",
                                                        "Trades %s%% of the flight reserve for speed")),
    "lluvia_estelar": (("Lluvia estelar", "Starfall"), ("%s%% de que el golpe traiga una estrella encima", "%s%% chance the blow brings a star down on it")),
    "conductor": (("Conductor", "Conductor"), ("La descarga salta a %s bloques del objetivo", "The charge jumps %s blocks from the target")),
    "siega_de_almas": (("Siega de almas", "Soul Reaping"), ("Cada muerte te cura %s corazones", "Every kill heals you %s hearts")),
    "aurora": (("Aurora", "Aurora"), ("%s%% de anular el daño mágico", "%s%% chance to shrug off magic damage")),
    "punta_afilada": (("Punta afilada", "Sharpened Tip"), ("+%s%% de daño de la flecha", "+%s%% arrow damage")),
    "asta_ligera": (("Asta ligera", "Light Shaft"), ("+%s%% de velocidad de la flecha", "+%s%% arrow speed")),
    "punta_envenenada": (("Punta envenenada", "Poisoned Tip"), ("Envenena %s s", "Poisons for %s s")),
    "punta_ignea": (("Punta ígnea", "Fiery Tip"), ("Prende %s s", "Sets alight for %s s")),
    "punta_perforante": (("Punta perforante", "Piercing Tip"), ("Atraviesa %s cuerpos", "Goes through %s bodies")),
    "pacto_de_sed": (("Pacto de sed", "Pact of Thirst"), ("+%s%% de daño, pero cada golpe te cuesta %s de hambre",
                                                          "+%s%% damage, but every blow costs you %s hunger")),
    "pacto_de_vidrio": (("Pacto de vidrio", "Pact of Glass"), ("+%s%% de daño y %s%% menos de durabilidad",
                                                               "+%s%% damage and %s%% less durability")),
    "pacto_de_sombra": (("Pacto de sombra", "Pact of Shadow"), ("Invisible al agacharte (al 100%%) y %s%% menos de armadura en la pieza",
                                                               "Invisible while crouching (at 100%%) and %s%% less armor on the piece")),
    "pacto_de_la_prisa": (("Pacto de la prisa", "Pact of Haste"), ("Mina un %s%% más rápido y aguanta un %s%% menos",
                                                                   "Mines %s%% faster and lasts %s%% less")),
    "aturdimiento": (("Aturdimiento", "Stun"), ("Deja aturdido %s s a quien golpeas", "Leaves what you hit stunned for %s s")),
    "segunda_cabeza": (("Segunda cabeza", "Second Head"), ("%s%% del golpe a todo lo que rodea al objetivo", "%s%% of the blow to everything around the target")),
    "rafaga": (("Ráfaga", "Flurry"), ("%s%% de que el puñetazo golpee dos veces", "%s%% chance a punch lands twice")),
    "nudillos_de_hierro": (("Nudillos de hierro", "Iron Knuckles"), ("Hasta +%s%% de daño según el frenesí", "Up to +%s%% damage with the frenzy")),
    "herradura": (("Herradura", "Horseshoes"), ("La montura corre un %s%% más", "The mount moves %s%% faster")),
    "peto": (("Peto", "Plating"), ("+%s de armadura para la montura", "+%s armor for the mount")),
    "carnicero": (("Carnicero", "Butcher"), ("+%s%% de daño por cada enemigo cerca", "+%s%% damage for every enemy nearby")),
    "sombra_larga": (("Sombra larga", "Long Shadow"), ("Invisible %s s después de matar", "Invisible for %s s after a kill")),
    "corriente": (("Corriente", "Riptide"), ("En agua o lluvia, usarlo te lanza (%s)", "In water or rain, using it throws you (%s)")),
    "canalizacion": (("Canalización", "Channeling"), ("%s de llamar al rayo en tormenta", "%s chance to call the lightning in a storm")),
    "tempano": (("Témpano", "Ice Floe"), ("%s de congelar a quien te golpea de cerca", "%s chance to freeze whoever hits you up close")),
    "resaca": (("Resaca", "Undertow"), ("El golpe arrastra al enemigo hacia ti (%s)", "The blow drags the enemy towards you (%s)")),
    "sirga": (("Sirga", "Winch"), ("El gancho tira un %s%% más fuerte", "The hook pulls %s%% harder")),
    "soga_larga": (("Soga larga", "Long Rope"), ("+%s bloques de soga: llega más lejos y arrastra más",
                                                 "+%s blocks of rope: it reaches further and hauls harder")),
    "retorno": (("Retorno", "Return"), ("%s%% de que el arma lanzada vuelva a tu mano", "%s%% chance a thrown weapon flies back to your hand")),
    "bumeran": (("Bumerán", "Boomerang"), ("El escudo lanzado empuja y aturde a %s enemigos", "A thrown shield shoves and stuns %s enemies")),
    "desgarro": (("Desgarro", "Rend"), ("Hasta %s heridas de sangrado a la vez", "Up to %s bleeding wounds at once")),
    "propulsion": (("Propulsión", "Propulsion"), ("Impulso de %s al agacharte y saltar, gasta pólvora",
                                                  "A %s burst when you crouch and jump, spends gunpowder")),
    "suerte_del_mar": (("Suerte del mar", "Luck of the Sea"), None),
    "perforacion": (("Perforación", "Piercing"), None),
    "absorcion": (("Absorción", "Absorption"), ("%s%% de ganar 2 corazones de absorción al bloquear", "%s%% chance to gain 2 absorption hearts when blocking")),
    "repulsion": (("Repulsión", "Repulsion"), ("Empuja a quien golpea tu escudo (fuerza %s)", "Pushes back whoever hits your shield (strength %s)")),
    "zancada": (("Zancada", "Stride"), ("Subes escalones de %s bloques sin saltar", "Steps up %s blocks without jumping")),
    "nutricion": (("Nutrición", "Nourishment"), ("%s%% de recuperar hambre cada 5 s", "%s%% chance to restore hunger every 5 s")),
    "sonar": (("Sonar", "Sonar"), ("Hace brillar a los monstruos a %s bloques", "Makes monsters within %s blocks glow")),
    "purificacion": (("Purificación", "Purification"), ("%s%% de quitarte veneno y marchitamiento cada 5 s", "%s%% chance to clear poison and wither every 5 s")),
    "anclaje": (("Anclaje", "Anchor"), ("%s%% menos de empuje, tirones y embestidas incluidos",
                                        "%s%% less knockback, hooks and charges included")),
    "aislante": (("Aislante", "Insulation"), ("%s s menos de fuego encima por segundo",
                                              "%s s of burning shaken off every second")),
    "rescoldo": (("Rescoldo", "Embers"), ("el fuego te cura el %s%% de lo que te haría",
                                           "fire heals you %s%% of what it would have cost you")),
    "temple": (("Temple", "Resolve"), ("los efectos malos se te pasan un %s%% antes",
                                        "bad effects run down %s%% faster")),
}
GUI = {
    "block.forja.mesa_de_forja": ("Mesa de forja", "Forge Table"),
    "container.forja.mesa_de_forja": ("Mesa de forja", "Forge Table"),
    "block.forja.mesa_de_piezas": ("Mesa de piezas", "Parts Table"),
    "container.forja.mesa_de_piezas": ("Mesa de piezas", "Parts Table"),
    # The single guide became the first of the books (docs/LIBROS_GUIA.md); the item kept its id.
    "item.forja.guia_de_forja": ("Cuaderno del aprendiz", "Apprentice's Notebook"),
    "item.forja.plantilla": ("Plantilla base", "Blank Template"),
    "item.forja.plantilla.de": ("Plantilla de %s", "%s Template"),
    "item.forja.orbe_de_mejora": ("Orbe de mejora", "Upgrade Orb"),
    "tooltip.forja.rota": ("¡Roto! No sirve hasta repararlo en la Mesa de forja", "Broken! Useless until repaired at the Forge Table"),
    "gui.forja.rota.aviso": ("¡Se rompió: %s! Repáralo en la Mesa de forja", "%s broke! Repair it at the Forge Table"),
    "item.forja.orbe_de_mejora.de": ("Orbe de %s", "%s Orb"),
    "tooltip.forja.orbe.uso": ("Al desarmar, cada mejora sale en un orbe con la mitad de su porcentaje. Ponlo en la estrella de la Mesa de forja con un objeto para sumárselo.",
                               "Salvaging turns each upgrade into an orb with half its percentage. Put it on the forge star with gear to add it."),
    "gui.forja.desarmar.orbes": ("Orbes ½:", "Orbs ½:"),
    "tooltip.forja.plantilla.base": ("Grábale una forma en la Mesa de piezas", "Engrave a shape on it at the Parts Table"),
    "tooltip.forja.plantilla.molde": ("Molde fijo que no se gasta · usa %s de material", "Fixed mold, never used up · uses %s material"),
    "gui.forja.plantilla.fija": ("Esta plantilla ya está grabada y no se puede cambiar", "This template is already engraved and cannot change"),
    "gui.forja.plantilla.falta": ("Pon una plantilla para elegir la pieza", "Put in a template to pick the part"),
    "gui.forja.plantilla.grabar": ("Elige una forma: la plantilla queda grabada para siempre", "Pick a shape: the template keeps it for good"),
    "gui.forja.plantilla.clic": ("Clic: grabar esta forma en la plantilla", "Click: engrave this shape on the template"),
    "gui.forja.boton.forjar": ("Forjar", "Forge"),
    "gui.forja.boton.reparar": ("Reparar", "Repair"),
    "gui.forja.libros.titulo": ("Libros y orbes:", "Books and orbs:"),
    "gui.forja.reparar.titulo": ("Reparar:", "Repair:"),
    "gui.forja.reparar.durabilidad": ("%s → %s / %s", "%s → %s / %s"),
    "gui.forja.reparar.usa": ("Usa %s de material", "Uses %s material"),
    "advancements.forja.farol.title": ("Fuego enjaulado", "Caged Fire"),
    "advancements.forja.farol.description": ("Mete una pavesa avivada en un farol",
                                              "Get a fed ember wisp into a lantern"),
    "advancements.forja.pavesa.title": ("Apágala", "Put It Out"),
    "advancements.forja.pavesa.description": ("Mata una pavesa mientras está avivada",
                                               "Kill an ember wisp while it is fed"),
    "advancements.forja.root.title": ("Forja", "Forja"),
    "advancements.forja.maestria.title": ("Hecho a mi mano", "Worn to My Hand"),
    "advancements.forja.maestria.description": ("Lleva un objeto forjado a maestría 10", "Raise forged gear to Mastery 10"),
    "advancements.forja.taller.title": ("Talabartero", "Saddler"),
    "advancements.forja.taller.description": ("Encuentra el taller de montaña y su cofre", "Find the mountain workshop and its chest"),
    "advancements.forja.saqueadores.title": ("Sin capitán", "No Captain"),
    "advancements.forja.saqueadores.description": ("Mata al capitán de una banda de saqueadores de forja", "Kill the captain of a band of forge raiders"),
    "advancements.forja.herrero_caido.title": ("El Herrero Caído", "The Fallen Smith"),
    "advancements.forja.herrero_caido.description": ("Derrota al Herrero Caído en el Cementerio entre Estrellas", "Beat the Fallen Smith in the Graveyard Among the Stars"),
    "advancements.forja.corazon.title": ("Corazón de forja", "Forge Heart"),
    "advancements.forja.corazon.description": ("Forja algo con el corazón del Herrero", "Forge something out of the Smith's heart"),
    "advancements.forja.aleacion.title": ("Dos metales", "Two Metals"),
    "advancements.forja.aleacion.description": ("Funde tu primera aleación en la estrella", "Melt your first alloy at the star"),
    "advancements.forja.damasco.title": ("Acero plegado", "Folded Steel"),
    "advancements.forja.damasco.description": ("Funde damasco sobre lava", "Melt damascus over lava"),
    "advancements.forja.evento.title": ("Cazador de cielos", "Sky Catcher"),
    "advancements.forja.evento.description": ("Guarda en una jarra la mejora de un evento", "Keep the upgrade of an event in a jar"),
    "advancements.forja.estelar.title": ("Del cielo", "Out of the Sky"),
    "advancements.forja.estelar.description": ("Forja algo con hierro estelar", "Forge something out of star iron"),
    "advancements.forja.encargo.title": ("A la carta", "Made to Order"),
    "advancements.forja.encargo.description": ("Entrega un encargo al Forjador", "Hand a Forjador the piece it asked for"),
    "advancements.forja.elite.title": ("Cazador de campeones", "Champion Hunter"),
    "advancements.forja.elite.description": ("Mata a un campeón y quédate con su leyenda", "Kill a champion and take its legend"),
    "advancements.forja.temple.title": ("Al agua", "Into the Water"),
    "advancements.forja.temple.description": ("Apaga una pieza recién forjada en agua, lava, nieve polvo o miel", "Put a freshly forged piece out in water, lava, powder snow or honey"),
    "advancements.forja.perfecta.title": ("Golpe limpio", "Clean Strike"),
    "advancements.forja.perfecta.description": ("Detén el martillo en el centro y consigue una forja perfecta", "Stop the hammer dead centre for a perfect forge"),
    "advancements.forja.herencia.title": ("Herencia", "Inheritance"),
    "advancements.forja.herencia.description": ("Pasa el trabajo de una pieza veterana a otra nueva", "Hand the work of a veteran piece down to a new one"),
    "advancements.forja.roto.title": ("Roto, no perdido", "Broken, Not Lost"),
    "advancements.forja.roto.description": ("Gasta un objeto forjado hasta romperlo", "Wear forged gear down until it breaks"),
    "advancements.forja.orbe.title": ("Esencia", "Essence"),
    "advancements.forja.orbe.description": ("Saca un orbe de mejora al desarmar", "Get an upgrade orb from salvage"),
    "advancements.forja.fusion.title": ("Dos en uno", "Two in One"),
    "advancements.forja.fusion.description": ("Fusiona orbes de mejora en la estrella", "Fuse upgrade orbs on the star"),
    "advancements.forja.conjunto.title": ("De punta en blanco", "Suited Up"),
    "advancements.forja.conjunto.description": ("Viste un conjunto completo de armadura forjada del mismo material", "Wear a full set of forged armor of one material"),
    "advancements.forja.eco.title": ("Ecolocalización", "Echolocation"),
    "advancements.forja.eco.description": ("Descubre menas con una herramienta resonante", "Find ores with a resonant tool"),
    "tooltip.forja.maestria": ("Maestría %s · %s/%s exp", "Mastery %s · %s/%s xp"),
    "tooltip.forja.conjunto": ("Conjunto de %s: %s/4", "%s set: %s/4"),
    "tooltip.forja.conjunto.bono": ("Completo: +2 armadura, +2 dureza y %s", "Full: +2 armor, +2 toughness and %s"),
    "gui.forja.guia.conjunto": ("Conjunto completo: %s", "Full set: %s"),
    "conjunto.forja.madera": ("+10% de velocidad", "+10% speed"),
    "conjunto.forja.piedra": ("+20% de resistencia al empuje", "+20% knockback resistance"),
    "conjunto.forja.hueso": ("+10% de velocidad de ataque", "+10% attack speed"),
    "conjunto.forja.cuero": ("caes 3 bloques más sin daño", "fall 3 more blocks safely"),
    "conjunto.forja.cobre": ("+15% de velocidad de minado", "+15% mining speed"),
    "conjunto.forja.hierro": ("+2 corazones", "+2 hearts"),
    "conjunto.forja.oro": ("+2 de suerte", "+2 luck"),
    "conjunto.forja.amatista": ("+1 de daño y +40 de maná máximo", "+1 attack damage and +40 max mana"),
    "conjunto.forja.diamante": ("+2 de dureza extra", "+2 extra toughness"),
    "conjunto.forja.obsidiana": ("las explosiones no te empujan", "explosions don't push you"),
    "conjunto.forja.netherita": ("el fuego te dura la mitad", "fire burns half as long"),
    "conjunto.forja.esmeralda": ("+3 de suerte", "+3 luck"),
    "conjunto.forja.prismarina": ("más aire y nado más rápido", "more air and faster swimming"),
    "conjunto.forja.vara_de_blaze": ("el fuego no te dura", "fire goes out at once"),
    "conjunto.forja.cuarzo": ("+25% de daño de barrido", "+25% sweeping damage"),
    "conjunto.forja.purpur": ("25% menos gravedad y caes 4 bloques más sin daño", "25% less gravity and fall 4 more blocks safely"),
    "conjunto.forja.obsidiana_llorona": ("+3 corazones", "+3 hearts"),
    "conjunto.forja.eco": ("+30% de velocidad agachado y el maná vuelve un 30% más rápido", "+30% sneaking speed and mana comes back 30% faster"),
    "conjunto.forja.resina": ("la arena de almas y la nieve no te frenan", "soul sand and snow don't slow you"),
    "conjunto.forja.escama": ("+3 de armadura extra", "+3 extra armor"),
    "commands.forja.sin_objeto": ("Sostén un objeto forjado en la mano", "Hold forged gear in your main hand"),
    "commands.forja.maestria": ("%s ahora tiene maestría %s", "%s now has Mastery %s"),
    "commands.forja.mejora": ("%s al %s%% en el objeto de tu mano", "%s at %s%% on the gear in your hand"),
    "commands.forja.mejora_invalida": ("La mejora %s no sirve para ese objeto", "The upgrade %s does not fit that gear"),
    "gui.forja.libro.conjunto": ("Conjunto: con casco, pechera, grebas y botas de placas del mismo material ganas +2 de armadura, +2 de dureza y un bono propio de ese material (hierro +2 corazones, madera +10% de velocidad...). El tooltip de cada pieza cuenta cuántas llevas y dice su bono; en Materiales ves el de todos.",
                                 "Set bonus: helmet, chestplate, leggings and boots with plates of the same material give +2 armor, +2 toughness and a bonus of that material (iron +2 hearts, wood +10% speed...). Each piece's tooltip counts how many you wear and names its bonus; Materials lists them all."),
    "tooltip.forja.maestria.max": ("Maestría %s (máxima)", "Mastery %s (max)"),
    "gui.forja.maestria.sube": ("¡%s subió a maestría %s!", "%s reached Mastery %s!"),
    "gui.forja.libro.cap.maestria": ("Maestría", "Mastery"),
    "gui.forja.libro.maestria_intro": ("Los objetos forjados ganan experiencia al usarse: las herramientas al minar, las armas al golpear y al matar, los arcos al acertar flechas, los escudos al bloquear y la armadura al recibir golpes. Cada nivel, hasta el 10, los mejora.",
                                       "Forged gear gains experience from use: tools by mining, weapons by hitting and killing, bows by landing arrows, shields by blocking and armor by taking hits. Every level, up to 10, makes it better."),
    "gui.forja.libro.maestria_bonos": ("Por nivel:\nTodo: +3%% de durabilidad.\nHerramientas: +4%% de velocidad de minado.\nArmas: +0.2 de daño.\nArmadura: +0.1 de dureza.\nArcos: +3%% de tensado y +0.05 de daño de flecha.\nEscudos: 2%% más rápidos al cubrir y ante hachas.",
                                       "Per level:\nAll gear: +3%% durability.\nTools: +4%% mining speed.\nWeapons: +0.2 damage.\nArmor: +0.1 toughness.\nBows: +3%% draw speed and +0.05 arrow damage.\nShields: 2%% faster to raise and against axes."),
    "gui.forja.libro.maestria_niveles": ("Experiencia total por nivel: 50, 150, 300, 500, 750, 1050, 1400, 1800, 2250 y 2750. Cambiar piezas o mejorar conserva la maestría; desarmar la pierde.",
                                         "Total experience per level: 50, 150, 300, 500, 750, 1050, 1400, 1800, 2250 and 2750. Swapping parts or upgrading keeps the mastery; salvaging loses it."),
    "advancements.forja.root.description": ("Herramientas, armas y armaduras pieza por pieza", "Tools, weapons and armor, part by part"),
    "advancements.forja.plantilla.title": ("Molde a medida", "Made to Measure"),
    "advancements.forja.plantilla.description": ("Graba una plantilla en la Mesa de piezas", "Engrave a template at the Parts Table"),
    "advancements.forja.guia.title": ("Lectura obligada", "Required Reading"),
    "advancements.forja.guia.description": ("Abre un libro de la forja", "Open a forge book"),
    "advancements.forja.pieza.title": ("Primera pieza", "First Part"),
    "advancements.forja.pieza.description": ("Corta una pieza con una plantilla", "Cut a part with a template"),
    "advancements.forja.desarmar.title": ("Nada se pierde", "Nothing Goes to Waste"),
    "advancements.forja.desarmar.description": ("Desarma un objeto en la Mesa de piezas", "Salvage gear at the Parts Table"),
    "advancements.forja.forja.title": ("Forjado a mano", "Handforged"),
    "advancements.forja.forja.description": ("Forja un objeto en la estrella de la Mesa de forja", "Forge gear on the Forge Table's star"),
    "advancements.forja.cambio.title": ("Pieza de repuesto", "Spare Part"),
    "advancements.forja.cambio.description": ("Cámbiale una pieza a un objeto forjado", "Swap a part on forged gear"),
    "advancements.forja.reparar.title": ("Como nuevo", "Good as New"),
    "advancements.forja.reparar.description": ("Repara un objeto en la estrella con su material", "Repair gear on the star with its material"),
    "advancements.forja.rasgo.title": ("Material con carácter", "Material with Character"),
    "advancements.forja.rasgo.description": ("Forja un objeto con un material que tenga rasgo", "Forge gear with a trait material"),
    "advancements.forja.netherita.title": ("Obra maestra", "Masterwork"),
    "advancements.forja.netherita.description": ("Forja un objeto con cabeza, hoja o placa de netherita", "Forge gear with a netherite head, blade or plate"),
    "advancements.forja.arsenal.title": ("Arsenal completo", "Full Arsenal"),
    "advancements.forja.arsenal.description": ("Forja un arco, una ballesta, un escudo, una lanza y un mazo", "Forge a bow, a crossbow, a shield, a spear and a mace"),
    "advancements.forja.mejora.title": ("Mejorado", "Improved"),
    "advancements.forja.mejora.description": ("Ponle una mejora a un objeto en la estrella", "Upgrade gear on the star"),
    "advancements.forja.mejora_completa.title": ("Al cien por ciento", "One Hundred Percent"),
    "advancements.forja.mejora_completa.description": ("Lleva una mejora al 100%%", "Take an upgrade to 100%%"),
    "advancements.forja.maestro.title": ("Maestro forjador", "Master Smith"),
    "advancements.forja.maestro.description": ("Ten un objeto con cinco mejoras al 100%%", "Own gear with five upgrades at 100%%"),
    "gui.forja.boton.cambiar": ("Cambiar piezas", "Swap parts"),
    "gui.forja.boton.mejorar": ("Mejorar", "Upgrade"),
    "gui.forja.boton.fusionar": ("Fusionar", "Fuse"),
    "commands.forja.orbe": ("Orbe de %s al %s%% entregado", "Gave a %s orb at %s%%"),
    "filled_map.forja_abandonada": ("Mapa de forja abandonada", "Abandoned Forge Map"),
    "tooltip.forja.comparado": ("Comparado con: %s", "Compared with: %s"),
    "item.forja.lingote_de_temple": ("Lingote de temple", "Tempering Ingot"),
    "item.forja.yunque_portatil": ("Yunque portátil", "Portable Anvil"),
    "item.forja.huevo_herrero_caido": ("Huevo del Herrero Caído", "Fallen Smith Spawn Egg"),
    "item.forja.huevo_automata_de_forja": ("Huevo de autómata de forja", "Forge Automaton Spawn Egg"),
    "item.forja.huevo_coraza_vacia": ("Huevo de coraza vacía", "Hollow Plate Spawn Egg"),
    "item.forja.huevo_pavesa": ("Huevo de pavesa", "Ember Wisp Spawn Egg"),
    "item.forja.huevo_herrumbre": ("Huevo de herrumbre", "Rust Flake Spawn Egg"),
    "item.forja.huevo_ascua_mayor": ("Huevo de ascua mayor", "Greater Ember Spawn Egg"),
    "item.forja.huevo_escoria_viviente": ("Huevo de escoria viviente", "Living Slag Spawn Egg"),
    "item.forja.huevo_yunque_andante": ("Huevo de yunque andante", "Walking Anvil Spawn Egg"),
    "item.forja.huevo_percutor": ("Huevo de percutor", "Striker Spawn Egg"),
    "item.forja.huevo_tenaza": ("Huevo de tenaza", "Tongs Spawn Egg"),
    "item.forja.huevo_cargador_de_carbon": ("Huevo de cargador de carbón", "Coal Hauler Spawn Egg"),
    "item.forja.huevo_templador": ("Huevo de templador", "Quencher Spawn Egg"),
    "item.forja.huevo_nucleo_estelar": ("Huevo de núcleo estelar", "Star Core Spawn Egg"),
    "item.forja.huevo_molde_roto": ("Huevo de molde roto", "Broken Mould Spawn Egg"),
    "item.forja.huevo_guardian_de_cuno": ("Huevo de guardián de cuño", "Cune Guardian Spawn Egg"),
    "item.forja.yunque_portatil.desc": ("Corta piezas y desarma donde estés, pero se gasta", "Cuts parts and takes gear apart anywhere, but it wears out"),
    "gui.forja.libro.yunque_portatil": (
        "El yunque de viaje: abre la mesa de piezas donde estés, con su pestaña de desarmar. Se gasta un "
        "punto por uso y aguanta 128, y no forja nada ni cuenta como taller: la estrella sigue siendo la "
        "estrella.",
        "The travelling anvil: it opens the parts table wherever you are, salvage tab and all. It spends a "
        "point per use and holds 128, and it forges nothing and counts as no workshop: the star is still "
        "the star.",
    ),
    "item.forja.sello": ("Sello de don", "Gift Seal"),
    "item.forja.sello.de": ("Sello de %s", "Seal of %s"),
    "tooltip.forja.sello": ("Ponlo en la estrella junto a un objeto de maestría 10 para grabarle este don",
                            "Put it on the star with a Maestria 10 item to engrave this gift"),
    "entity.forja.villager.forjador": ("Forjador", "Forgesmith"),
    "entity.forja.villager.ermitano": ("Herrero ermitaño", "Hermit Smith"),
    "tooltip.forja.temple": ("Clic derecho sobre un objeto forjado en el inventario: lo repara con el metal de su cabeza (también si está roto)",
                             "Right-click onto forged gear in your inventory: mends it in its head's own metal (broken gear too)"),
    "gui.forja.parada": ("¡Parada perfecta!", "Perfect parry!"),
    "gui.forja.sismo": ("¡Sismo! %s enemigos por los aires", "Quake! %s enemies thrown up"),
    "gui.forja.siega": ("¡Siega! %s enemigos arrastrados", "Reap! %s enemies dragged in"),
    "gui.forja.don.titulo": ("Grabar un don:", "Engrave a gift:"),
    "gui.forja.don.unico": ("Uno por objeto y permanente", "One per item, permanent"),
    "gui.forja.boton.grabar": ("Grabar don", "Engrave gift"),
    "tooltip.forja.don": ("Don: %s", "Gift: %s"),
    "tooltip.forja.don.largo": ("✦ Don de %s: %s", "✦ Gift of %s: %s"),
    "advancements.forja.don.title": ("Un don propio", "A Gift of Its Own"),
    "advancements.forja.don.description": ("Graba un don en un objeto de maestría 10", "Engrave a gift on a Maestria 10 item"),
    "gui.forja.libro.dones.titulo": ("Dones", "Gifts"),
    "gui.forja.libro.dones": ("Al llegar a maestría 10, pon el objeto en el centro de la Mesa de forja y un sello en una punta. Cada sello lleva un don grabado y solo sirve si ese don vale para ese objeto. Se graba uno y ya no se cambia.",
                              "At Maestria 10, put the gear in the middle of the Forge Table and a seal on a point. Each seal carries one gift and only works if that gift fits that item. One gift, and it is permanent."),
    "gui.forja.libro.don_linea": ("%s: %s", "%s: %s"),
    "gui.forja.libro.dones_lista": ("%s: una cuarta parte del desgaste no ocurre. %s: más daño al primer golpe sobre algo intacto. %s: vetas más largas y más experiencia. %s: +1 de armadura y ventana de parada más amplia. %s: caes más suave y andas más rápido. %s: Cebo y Suerte del mar extra.",
                                    "%s: a quarter of the wear never lands. %s: more damage on the first blow against something untouched. %s: longer veins and more experience. %s: +1 armor and a wider parry window. %s: softer landings and a quicker step. %s: extra Lure and Luck of the Sea."),
    "perk.forja.filo_eterno": ("Filo eterno", "Eternal Edge"),
    "perk.forja.filo_eterno.desc": ("una de cada cuatro veces no gasta durabilidad", "one wear in four never lands"),
    "perk.forja.cazador": ("Cazador", "Hunter"),
    "perk.forja.cazador.desc": ("+25% de daño al golpear a algo con toda su vida; +1 de daño de flecha en arcos",
                                "+25% damage on something at full health; +1 arrow damage on bows"),
    "perk.forja.minero": ("Minero", "Miner"),
    "perk.forja.minero.desc": ("las vetas siguen un 50% más y cada mineral da +2 de experiencia",
                               "veins follow 50% further and each ore gives +2 experience"),
    "perk.forja.baluarte": ("Baluarte", "Bulwark"),
    "perk.forja.baluarte.desc": ("+1 de armadura y 4 tics más de ventana de parada; pesa un 30% más, y con escudo bloquear cuesta un 20% más de estamina",
                                 "+1 armor and 4 more ticks of parry window; weighs 30% more, and on a shield blocking costs 20% more stamina"),
    "perk.forja.viajero": ("Viajero", "Wanderer"),
    "perk.forja.viajero.desc": ("-25% de daño de caída y +5% de velocidad", "-25% fall damage and +5% speed"),
    "perk.forja.cargador": ("Cargador", "Quiverful"),
    "perk.forja.cargador.desc": ("un 25%% de las flechas vuelven al carcaj", "a quarter of the arrows come back to the quiver"),
    "perk.forja.jinete": ("Jinete", "Rider"),
    "perk.forja.jinete.desc": ("la montura esquiva un 20%% de los golpes", "the mount turns aside a fifth of the blows"),
    "perk.forja.duelista": ("Duelista", "Duellist"),
    "perk.forja.duelista.desc": ("el frenesí sube al doble y dura el doble", "the frenzy climbs twice as fast and holds twice as long"),
    "perk.forja.tirador": ("Tirador", "Marksman"),
    "perk.forja.tirador.desc": ("lo que sueltes vuela un 15% más rápido", "everything it looses flies 15% faster"),
    "perk.forja.muralla": ("Muralla", "Rampart"),
    "perk.forja.muralla.desc": ("lo que paras vuelve al que golpeó, y a ti no te mueve nadie",
                                "what you block goes back at whoever swung, and nothing shifts you"),
    "perk.forja.aeronauta": ("Aeronauta", "Aeronaut"),
    "perk.forja.aeronauta.desc": ("las alas se recargan tres veces más rápido", "the wings fill again three times as fast"),
    "perk.forja.pescador": ("Pescador", "Angler"),
    "perk.forja.pescador.desc": ("+1 de Cebo y +1 de Suerte del mar", "+1 Lure and +1 Luck of the Sea"),
    "tooltip.forja.leyenda": ("Leyenda de la forja", "A forge legend"),
    "advancements.forja.leyenda.title": ("Nombre propio", "A Name of Its Own"),
    "advancements.forja.leyenda.description": ("Encuentra una pieza legendaria de la forja", "Find one of the forge's legends"),
    "legend.forja.cadena_del_juicio": ("Cadena del juicio", "Chain of Judgement"),
    "legend.forja.punos_del_yunque": ("Puños del yunque", "Fists of the Anvil"),
    "legend.forja.garra_del_abismo": ("Garra del abismo", "Claw of the Deep"),
    "legend.forja.marea_del_ahogado": ("Marea del ahogado", "Drowned Tide"),
    "legend.forja.filo_de_la_guadana": ("Filo de la guadaña", "Edge of the Scythe"),
    "legend.forja.alas_del_alba": ("Alas del alba", "Wings of Dawn"),
    "legend.forja.barda_del_invicto": ("Barda del invicto", "Barding of the Unbeaten"),
    "legend.forja.aliento_de_invierno": ("Aliento de Invierno", "Winter's Breath"),
    "legend.forja.sed_del_ocaso": ("Sed del Ocaso", "Dusk's Thirst"),
    "legend.forja.veta_madre": ("Veta Madre", "Mother Lode"),
    "legend.forja.muralla": ("Muralla", "Bulwark of Old"),
    "legend.forja.paso_del_viento": ("Paso del Viento", "Windstep"),
    "legend.forja.ojo_de_halcon": ("Ojo de Halcón", "Hawk's Eye"),
    "legend.forja.yunque_andante": ("Yunque Andante", "Walking Anvil"),
    "advancements.forja.parada.title": ("Al filo del escudo", "On the Rim"),
    "advancements.forja.parada.description": ("Haz una parada perfecta: bloquea justo cuando llega el golpe", "Land a perfect parry: block right as the blow arrives"),
    "advancements.forja.trato.title": ("Trato hecho", "Deal Struck"),
    "advancements.forja.trato.description": ("Compra una plantilla, un orbe, un kit o un mapa a un herrero", "Buy a template, an orb, a kit or a map from a smith"),
    "advancements.forja.ruina.title": ("Brasas frías", "Cold Embers"),
    "advancements.forja.ruina.description": ("Abre el cofre de una forja abandonada", "Open the chest of an abandoned forge"),
    "gui.forja.orbes.fusion": ("Fusionar orbes:", "Fuse orbs:"),
    "gui.forja.orbes.total": ("Orbe de %s%%", "%s%% orb"),
    "gui.forja.estrella.cambio": ("Cambiar piezas:", "Swap parts:"),
    "gui.forja.estrella.ayuda.1": ("Piezas en las puntas", "Parts on the points"),
    "gui.forja.estrella.ayuda.2": ("forjan la herramienta.", "forge the gear."),
    "gui.forja.estrella.ayuda.3": ("Herramienta al centro +", "Gear in the center +"),
    "gui.forja.estrella.ayuda.4": ("ingredientes = mejora.", "ingredients = upgrade."),
    "gui.forja.libro.plantilla": ("Plantilla base: dos palos y dos tablones dan dos. Ponla en la Mesa de piezas y elige una forma para grabarla. Grabada no se gasta, pero tampoco se puede cambiar.",
                                  "Blank Template: two sticks and two planks make two. Put it in the Parts Table and pick a shape to engrave. Engraved, it is never used up, and it never changes."),
    "gui.forja.libro.indice": ("Índice", "Contents"),
    "gui.forja.libro.intro": ("Con esta guía aprendes a forjar herramientas, armas y armaduras pieza por pieza. Cada pieza puede ser de un material distinto, y cada material cambia lo que da.",
                              "This guide teaches you to forge tools, weapons and armor part by part. Every part can use a different material, and every material changes what it gives."),
    "gui.forja.libro.pasos": ("1. Graba una plantilla y corta piezas en la Mesa de piezas.\n2. Ponlas en la estrella de la Mesa de forja y forja.\n3. Con la herramienta al centro, mejórala con ingredientes.\n4. Si ya no te sirve, desármala en la Mesa de piezas.",
                              "1. Engrave a template and cut parts at the Parts Table.\n2. Put them on the Forge Table's star and forge.\n3. With the gear in the center, upgrade it with ingredients.\n4. When you are done with it, salvage it at the Parts Table."),
    # ------------------------------------------------------------------ the new chapters of the book
    "gui.forja.libro.seccion.taller": ("El taller", "The workshop"),
    "gui.forja.libro.seccion.mejoras": ("Mejoras", "Upgrades"),
    "gui.forja.libro.seccion.pelear": ("Pelear", "Fighting"),
    "gui.forja.libro.seccion.mundo": ("El mundo", "The world"),
    "gui.forja.libro.seccion.referencia": ("Referencia", "Reference"),
    "gui.forja.libro.cap.aleaciones": ("Aleaciones", "Alloys"),
    "gui.forja.libro.cap.fundicion": ("Montar una fundición", "Building a foundry"),
    "gui.forja.libro.cap.temple": ("Temple", "Quench"),
    "gui.forja.libro.cap.herrero": ("Maestría de herrero", "Smith Maestria"),
    "gui.forja.libro.cap.tecnicas": ("Técnicas del herrero", "Smith Techniques"),
    "gui.forja.libro.cap.mi_taller": ("Tu taller", "Your Workshop"),
    "painting.forja.yunque.title": ("La fragua", "The Forge"),
    "painting.forja.yunque.author": ("Forja", "Forja"),
    "painting.forja.estrella.title": ("La estrella", "The Star"),
    "painting.forja.estrella.author": ("Forja", "Forja"),
    "painting.forja.martillo.title": ("El martillo", "The Hammer"),
    "painting.forja.martillo.author": ("Forja", "Forja"),
    "gui.forja.libro.taller_propio_intro": (
        "Esta página no habla del mod, habla de ti: lo que llevas hecho en la estrella y lo que has "
        "elegido por el camino.",
        "This page is not about the mod, it is about you: what you have made at the star and what you "
        "chose along the way.",
    ),
    "gui.forja.libro.taller_maestria": ("Maestría", "Maestria"),
    "gui.forja.libro.taller_tecnica": ("Maestría %s: %s", "Maestria %s: %s"),
    "gui.forja.libro.taller_sin_tecnica": ("Maestría %s: sin elegir", "Maestria %s: not chosen"),
    "gui.forja.libro.taller_cuenta": ("La cuenta", "The tally"),
    "gui.forja.libro.taller_forjadas": ("Piezas nacidas en la estrella: %s", "Pieces born on the star: %s"),
    "gui.forja.libro.taller_perfectas": ("De ellas, perfectas: %s (%s%%)", "Of those, perfect: %s (%s%%)"),
    "gui.forja.libro.taller_mejoradas": ("Mejoras trabajadas: %s", "Upgrades worked in: %s"),
    "gui.forja.libro.cap.primeros_pasos": ("Primeros pasos", "First Steps"),
    "gui.forja.libro.pasos_intro": (
        "Forjar es siempre lo mismo: una plantilla te da una pieza, tres piezas te dan un objeto, y a "
        "partir de ahí el objeto se mejora, se templa y se gana su nombre. Esto es todo el ciclo en seis "
        "pasos; el resto del libro son los detalles.",
        "Forging is always the same: a template gives you a part, three parts give you an item, and from "
        "there the item is upgraded, quenched and earns its name. This is the whole loop in six steps; the "
        "rest of the book is the detail.",
    ),
    "gui.forja.libro.paso1.titulo": ("1. Las dos mesas", "1. The two tables"),
    "gui.forja.libro.paso1": (
        "La mesa de piezas corta y desarma; la mesa de forja es la estrella donde nace el objeto. Con una "
        "plantilla en blanco en la mesa de piezas eliges la forma (cabeza de pico, hoja, mango...) y esa "
        "plantilla se queda así para siempre, pero no se gasta.",
        "The parts table cuts and salvages; the forge table is the star where the item is born. A blank "
        "template on the parts table takes the shape you pick (pickaxe head, blade, handle...) and keeps "
        "it for good, though it is never used up.",
    ),
    "gui.forja.libro.paso2.titulo": ("2. Corta las piezas", "2. Cut the parts"),
    "gui.forja.libro.paso2": (
        "Pon la plantilla grabada y el material en la mesa de piezas: corta lo que se talla (madera, "
        "piedra, hueso, cuero, amatista, cuarzo, prismarina...). El metal no se corta: se funde en el "
        "crisol y se cuela en una mesa de colada, con el molde que prepara la caja de moldeo (libro La "
        "fundición). Cada pieza cuesta una cantidad "
        "distinta y cada material lleva su rasgo; la pieza sale con sus números ya escritos.",
        "Put the engraved template and the material on the parts table: it cuts what can be carved "
        "(wood, stone, bone, leather, amethyst, quartz, prismarine...). Metal is not cut: it is melted in "
        "the crucible and cast on a casting table, in the mould the casting box makes (book The Foundry). "
        "Each part costs a different amount "
        "and every material carries its own trait; the part comes out with its numbers already on it.",
    ),
    "gui.forja.libro.paso3.titulo": ("3. Fórjalo en la estrella", "3. Forge it on the star"),
    "gui.forja.libro.paso3": (
        "Las piezas van en las puntas de la estrella, y al pulsar Forjar el martillo cruza una barra: "
        "suéltalo en el centro y la pieza sale perfecta. Lo que forjas lleva tu firma, sale caliente para "
        "templarlo y ya nace con algo de maestría si eres buen herrero.",
        "The parts go on the points of the star, and pressing Forge sends a hammer across a rail: let it "
        "go in the middle and the piece comes out perfect. What you forge carries your signature, comes "
        "out hot to be quenched, and is already broken in if you are a practised smith.",
    ),
    "gui.forja.libro.paso4.titulo": ("4. Mejóralo", "4. Upgrade it"),
    "gui.forja.libro.paso4": (
        "Con el objeto en el centro, los materiales en las puntas suben sus mejoras por porcentaje: "
        "amatista da Filo, azúcar da Eficiencia, lapislázuli da Fortuna. También valen los libros "
        "encantados y los orbes de mejora, y dos mejoras al 50% pueden despertar una sinergia.",
        "With the item in the middle, materials on the points raise its upgrades by percentage: amethyst "
        "gives Filo, sugar gives Eficiencia, lapis gives Fortuna. Enchanted books and upgrade orbs work "
        "too, and two upgrades at 50% can wake a synergy.",
    ),
    "gui.forja.libro.paso5.titulo": ("5. Maestría, calor y temple", "5. Maestria, heat and quench"),
    "gui.forja.libro.paso5": (
        "Todo lo que haces en la estrella te enseña: subes de maestría, aciertas más el martillo y en "
        "maestría 3, 6 y 9 eliges una técnica. El bloque que pongas debajo de la mesa decide el calor y "
        "con él las aleaciones, y una pieza recién forjada se templa metiéndote en agua, lava, nieve o miel.",
        "Everything you do at the star teaches you: your Maestria rises, your presses land cleaner, and at "
        "Maestria 3, 6 and 9 you pick a technique. The block under the table decides the heat and with it "
        "the alloys, and a freshly forged piece is quenched by standing in water, lava, snow or honey.",
    ),
    "gui.forja.libro.paso6.titulo": ("6. Y a partir de ahí", "6. And from there"),
    "gui.forja.libro.paso6": (
        "Queda el mundo: eventos que dejan mejoras que no se consiguen de otra forma, encargos del "
        "Forjador, talismanes y cinturón, saqueadores que vienen a quitarte lo que has hecho, y al final "
        "el Herrero Caído y su corazón de forja.",
        "Then there is the world: events that leave upgrades nothing else gives, the Forjador's "
        "commissions, talismans and the belt, raiders who come for what you have made, and at the end the "
        "Fallen Smith and his forge heart.",
    ),
    # ------------------------------------------------------------------ the smith's path (ForjaPath)
    "gui.forja.libro.cap.siguiente_paso": ("Siguiente paso", "Next Step"),
    "gui.forja.camino.titulo": ("El camino del herrero", "The smith's path"),
    "gui.forja.camino.intro": (
        "%s pasos, hasta tu primera técnica. Se marcan solos; pulsa uno para ir a él.",
        "%s steps, up to your first technique. They tick themselves off; click one to go to it.",
    ),
    "gui.forja.camino.paso": ("Paso %s de %s", "Step %s of %s"),
    "gui.forja.camino.leer": ("Léelo en «%s», pág. %s", "Read it in %s, p. %s"),
    "gui.forja.camino.portada": ("Tu siguiente paso · pág. %s", "Your next step · p. %s"),
    "gui.forja.camino.completo": ("Camino completo", "Path complete"),
    "gui.forja.camino.completo.desc": (
        "Ya sabes lo que hace falta para valerte. Lo que queda está ahí fuera: eventos, encargos, "
        "saqueadores y, al final, el Herrero Caído.",
        "You know what it takes to fend for yourself. What is left is out there: events, commissions, "
        "raiders and, at the end, the Fallen Smith.",
    ),
    "gui.forja.camino.pista": ("Siguiente paso: %s. Lo explica «%s», en «%s».",
                               "Next step: %s. \"%s\" explains it, in \"%s\"."),
    "gui.forja.camino.pista.completo": ("Has recorrido el camino del herrero: el resto de la guía es tuyo.",
                                        "You have walked the smith's path: the rest of the guide is yours."),
    "gui.forja.camino.plantilla": ("Graba una plantilla", "Engrave a template"),
    "gui.forja.camino.plantilla.desc": (
        "En la **mesa de piezas** (hierro, una piedra de afilar y tablones) pon una **plantilla** en blanco "
        "en la casilla de la izquierda y haz clic en una de las formas de arriba: cabeza de pico, hoja, "
        "mango... Queda grabada para siempre, y la plantilla no se gasta.",
        "At the **parts table** (iron, a grindstone and planks) set a blank **template** in the left-hand "
        "slot and click one of the shapes above it: pickaxe head, blade, handle... It stays engraved for "
        "good, and the template is never used up.",
    ),
    "gui.forja.camino.pieza": ("Corta tu primera pieza", "Cut your first part"),
    "gui.forja.camino.pieza.desc": (
        "Plantilla grabada y material en la misma mesa, y recoge la pieza. Allí sólo se corta lo que se "
        "trabaja en frío: **madera, piedra, hueso, cuero**, cuarzo... El metal no se corta: se **cuela**.",
        "The engraved template and a material on the same table, then take the part. It only cuts what is "
        "worked cold: **wood, stone, bone, leather**, quartz... Metal is not cut: it is **cast**.",
    ),
    "gui.forja.camino.forja": ("Forja tu primera herramienta", "Forge your first tool"),
    "gui.forja.camino.forja.desc": (
        "En la **mesa de forja** (hierro, una mesa de crafteo y tablones) pon las piezas en las puntas de "
        "la estrella: un pico es **cabeza, mango y atadura**. Pulsa Forjar y para el martillo en el centro.",
        "At the **forge table** (iron, a crafting table and planks) set the parts on the points of the "
        "star: a pickaxe is a **head, a handle and a binding**. Press Forge and stop the hammer in the middle.",
    ),
    "gui.forja.camino.temple": ("Témplala", "Quench it"),
    "gui.forja.camino.temple.desc": (
        "Lo recién forjado sale caliente **%s segundos**. Llévalo en la mano o puesto y métete en **agua** "
        "antes de que se enfríe (o en lava, nieve polvo, o sobre miel): el temple se queda en el acero.",
        "What you just forged stays hot for **%s seconds**. Hold it or wear it and step into **water** "
        "before it cools (or lava, powder snow, or onto honey): the quench stays in the steel.",
    ),
    "gui.forja.camino.mejora": ("Mejórala", "Upgrade it"),
    "gui.forja.camino.mejora.desc": (
        "La herramienta al **centro** de la estrella y el ingrediente en las puntas: el **azúcar** le da "
        "Eficiencia a un pico. Esta mesa sube cada mejora hasta el **%s%%**; el resto, la mesa mayor.",
        "The tool in the **centre** of the star and the ingredient on the points: **sugar** gives a pickaxe "
        "Efficiency. This table takes each upgrade to **%s%%**; the rest is the greater table's.",
    ),
    "gui.forja.camino.parada": ("Para un golpe", "Parry a blow"),
    "gui.forja.camino.parada.desc": (
        "Con una espada o un escudo, **levántalo justo cuando llega el golpe**, no antes: los monstruos "
        "avisan antes de pegar. Parar devuelve el golpe, desequilibra al que ataca y te devuelve aguante.",
        "With a sword or a shield, **raise it just as the blow lands**, not before: monsters give warning "
        "before they strike. A parry turns the blow back, unsettles the attacker and gives you stamina back.",
    ),
    "gui.forja.camino.aleacion": ("Funde tu primera aleación", "Melt your first alloy"),
    "gui.forja.camino.aleacion.desc": (
        "Una **fogata** encendida bajo la mesa de forja, el centro vacío y en las puntas **dos lingotes de "
        "cobre y uno de hierro**: sale **bronce**. Cobre y ladrillo de resina dan **peltre**. Lo que hay "
        "bajo la mesa es su calor.",
        "A lit **campfire** under the forge table, the centre empty and **two copper ingots and one iron** "
        "on the points: out comes **bronze**. Copper and a resin brick give **pewter**. What is under the "
        "table is its heat.",
    ),
    "gui.forja.camino.colada": ("Cuela tu primera pieza", "Cast your first part"),
    "gui.forja.camino.colada.desc": (
        "Crisol y caja de moldeo, de **peltre**; cuba, de **bronce**. El crisol quema **ascuas** y funde "
        "en la cuba. Pieza y **%s de acero refractario** en la caja dan su molde; va en una **mesa de "
        "colada**, bajo un **colador**.",
        "Crucible and casting box of **pewter**; tank of **bronze**. The crucible burns **embers** and "
        "melts into the tank. A part and **%s refractory steel** in the box make its mould; it goes on a "
        "**casting table**, under a **strainer**.",
    ),
    "gui.forja.camino.mesa_mayor": ("La mesa de forja mayor", "The greater forge table"),
    "gui.forja.camino.mesa_mayor.desc": (
        "La primera mesa no monta espadones, escudos, manguales ni alas. La **mayor** sí: tu mesa de forja "
        "rodeada de **damasco**, oro y piedra negra pulida. El damasco es acero y chatarra de netherita "
        "sobre una mesa con **lava** debajo.",
        "The first table will not put together greatswords, shields, flails or wings. The **greater** one "
        "will: your forge table ringed with **damascus**, gold and polished blackstone. Damascus is steel "
        "and netherite scrap on a table with **lava** underneath.",
    ),
    "gui.forja.libro.mesa_mayor": (
        "La **mesa de forja mayor**: la misma estrella, todo lo que la primera no monta (espadones, "
        "lanzas, escudos, alas...) y las mejoras hasta el %s%%. El damasco sale de una mesa sobre lava.",
        "The **greater forge table**: the same star, everything the first one will not assemble "
        "(greatswords, spears, shields, wings...) and upgrades all the way to %s%%. Damascus comes off a "
        "table over lava.",
    ),
    "advancements.forja.colada.title": ("Primera colada", "First Pour"),
    "advancements.forja.colada.description": ("Saca una pieza de la fundición", "Take a part out of the foundry"),
    "advancements.forja.mesa_mayor.title": ("La mesa mayor", "The Greater Table"),
    "advancements.forja.mesa_mayor.description": ("Hazte con una mesa de forja mayor", "Get a greater forge table"),
    "gui.forja.libro.cap.combate": ("Combate", "Fighting"),
    "gui.forja.libro.cap.mana": ("Maná y estamina", "Mana and stamina"),
    "gui.forja.libro.mana.intro": ("El báculo y el grimorio gastan **maná**: la barra azul y violeta encima de los corazones, frente a la de estamina. Solo aparece cuando llevas en la mano algo que usa maná o cuando la barra no está llena, y nunca si aún no has tenido un arma mágica. Una muesca en su borde marca lo que cuesta el hechizo más barato del arma que llevas.",
                                   "The staff and the tome spend **mana**: the blue and violet bar above the hearts, across from the stamina bar. It only shows while something that uses mana is in your hand or the bar is not full, and never before you have had a magic weapon. A notch on its lip marks what the cheapest spell of the weapon in your hand costs."),
    "gui.forja.libro.mana.costes.titulo": ("Lo que cuesta", "What it costs"),
    "gui.forja.libro.mana.costes": ("La barra tiene %1$s de maná. Un proyectil del báculo cuesta %2$s y la espera es de %3$s tics; un área del grimorio cuesta %5$s y espera %6$s tics. Una carga llena cuesta un %8$s%% más y pega un %9$s%% más: cargar es la forma de ahorrar, tocar la de gastar deprisa. Se pelea a ráfagas: vacías la barra y esperas a que vuelva.",
                                    "The bar holds %1$s mana. A staff bolt costs %2$s and the wait is %3$s ticks; a tome's area costs %5$s and waits %6$s ticks. A full charge costs %8$s%% more and hits %9$s%% harder: charging is how you save, tapping how you spend fast. You fight in bursts: empty the bar and wait for it to come back."),
    "gui.forja.libro.mana.vacio": ("Sin maná bastante el hechizo no sale: un chisporroteo y la barra destella en rojo. Si soltaste una carga más grande de lo que la barra paga, sale tan fuerte como el maná alcanza.",
                                   "Without enough mana the spell does not come out: a fizzle and a red flash of the bar. Let go of a charge bigger than the bar can pay for and it leaves as strong as the mana allows."),
    "gui.forja.libro.mana.vuelve.titulo": ("Cómo vuelve", "How it comes back"),
    "gui.forja.libro.mana.vuelve": ("Solo, y muy despacio: %1$s por segundo mientras sigues lanzando, y %2$s s después del último hechizo pasa a %3$s por segundo, así que una barra vacía tarda unos %4$s minutos en llenarse. Solo el **Mago** (×%5$s) y el **Curandero** (×%6$s), y sus talentos, lo recuperan a un ritmo útil; a los demás les vuelve sobre todo matando.",
                                    "By itself, and very slowly: %1$s a second while you keep casting, and %2$s s after the last spell it goes up to %3$s a second, so an empty bar takes about %4$s minutes to fill. Only the **Mage** (×%5$s) and the **Healer** (×%6$s), and their talents, get it back at a useful pace; everyone else gets it back mostly by killing."),
    "gui.forja.libro.mana.muertes": ("Matar también devuelve, al maná y a la estamina, pero sin llenar de golpe: lo que vale cada muerte espera en un tramo claro al final de la barra y entra como mucho un %s%% de la barra cada %s tics. Al maná, %s más %s por punto de vida máxima de la víctima, hasta un %s%% de la barra; a la estamina, %s más %s por punto, hasta un %s%%. Lo que no cabe en la barra se pierde.",
                                     "Kills give back too, to mana and to stamina, but never all at once: what each kill is worth waits in a pale stretch at the end of the bar and flows in at most %s%% of the bar every %s ticks. To mana, %s plus %s per point of the victim's max health, up to %s%% of the bar; to stamina, %s plus %s per point, up to %s%%. What does not fit is lost."),
    "gui.forja.libro.mana.muerte_propia": ("Al morir vuelves con la barra llena. El maná se guarda contigo: salir del mundo con la barra vacía no la llena.",
                                           "You come back from death with a full bar. Mana is saved with you: leaving the world with an empty bar does not fill it."),
    "gui.forja.libro.mana.mejoras": ("Mejoras de maná", "Mana upgrades"),
    "gui.forja.libro.mana.linea": ("Va en: %s. Al 100%%: %s", "Goes on: %s. At 100%%: %s"),
    "gui.forja.libro.mana.conjuntos": ("Conjuntos: cuatro piezas de amatista suman %s de maná máximo, y cuatro de eco hacen que vuelva un %s%% más rápido. Reserva y Flujo se suman pieza a pieza.",
                                       "Sets: four pieces of amethyst add %s max mana, and four of echo make it come back %s%% faster. Reservoir and Flow add up piece by piece."),
    "gui.forja.libro.mana.filo": ("Las armas de filo (espada, daga, espadón, guadaña y lanza) admiten magia: Filo arcano, Estallido arcano y Paso arcano gastan maná y, con la barra vacía, no hacen nada. Las contundentes y los guanteletes ya tienen sus golpes de estamina.",
                                  "Edged weapons (sword, dagger, greatsword, scythe and spear) take magic: Arcane Edge, Arcane Burst and Arcane Step spend mana and do nothing with the bar empty. The blunt ones and the gauntlets already have their stamina moves."),
    "gui.forja.libro.estamina.mejoras": ("Mejoras de estamina", "Stamina upgrades"),
    "gui.forja.libro.mana.monstruos": ("Los monstruos que llevan báculo o grimorio no usan maná: esperan lo que se esperaba antes entre hechizo y hechizo.",
                                       "Monsters carrying a staff or a tome use no mana: they wait between spells as long as ever."),
    "gui.forja.libro.cap.pactos": ("Pactos", "Pacts"),
    "gui.forja.libro.cap.potencial": ("Potencial", "Potential"),
    "gui.forja.libro.potencial.intro": (
        "Ninguna mejora llega ya al 100%% porque sí. Cada pieza tiene un **potencial**: hasta dónde pueden subir sus mejoras. Sale de cómo la hiciste, lo ves en su descripción, y nada de lo que sigue **baja** jamás una mejora que ya tengas: sólo impide subirla.",
        "No upgrade goes to 100%% for the asking any more. Every piece has a **potential**: how far its upgrades can be taken. It comes out of how you made it, it is written on the piece, and nothing below ever **lowers** an upgrade you already have: it only stops it rising."),
    "gui.forja.libro.potencial.de_donde": ("De dónde sale", "Where it comes from"),
    "gui.forja.libro.potencial.fuentes": (
        "Toda pieza parte de %s. Las piezas **coladas en la fundición** dan hasta +%s (las cortadas en la mesa, nada; las bastas, tampoco). El martillo: +%s un golpe decente, +%s uno perfecto. Tu nivel de herrero, un punto por nivel. La mesa de forja mayor, +%s; el taller completo, +%s.",
        "Every piece starts at %s. Parts **poured in the foundry** give up to +%s (parts cut at the bench give nothing, and neither do rough ones). The hammer: +%s for a decent blow, +%s for a perfect one. Your smith level, a point a level. The greater forge table, +%s; a whole workshop, +%s."),
    "gui.forja.libro.potencial.despues": (
        "Y después sigue creciendo: un punto por cada nivel de maestría de la pieza, +%s por cada **pacto** que lleve, y hasta +%s con la mejora **Recocido**. Subir el potencial no regala porcentaje: abre sitio, no lo llena.",
        "And it goes on growing: a point for every level of the piece's own mastery, +%s for every **pact** on it, and up to +%s from the **Annealing** upgrade. Raising the potential gives no percentage away: it makes room, it does not fill it."),
    "gui.forja.libro.potencial.topes": ("Los tres topes", "The three ceilings"),
    "gui.forja.libro.potencial.mesas": (
        "**La mesa.** La mesa de forja no pasa del %s%%; el resto es trabajo de la mesa de forja mayor, que llega al %s%%. Una mejora sube hasta el menor de los dos: el de la pieza y el de la mesa.",
        "**The table.** The forge table stops at %s%%; the rest is work for the greater forge table, which goes to %s%%. An upgrade rises to the lower of the two: the piece's and the table's."),
    "gui.forja.libro.potencial.tramos": (
        "**El precio.** Hasta el %s%% cada ingrediente rinde lo que dice. Desde ahí rinde la mitad, y desde el %s%% la cuarta parte: la segunda mitad de una mejora cuesta el triple que la primera.",
        "**The price.** Up to %s%% an ingredient is worth what it says. From there it is worth half, and from %s%% a quarter: the second half of an upgrade costs three times the first."),
    "gui.forja.libro.potencial.fundente": (
        "**El fundente maestro.** Sin él ninguna mejora pasa del %s%%. Va en la estrella junto a los ingredientes y se gasta una pizca la vez que la mejora cruza esa raya. Hierro estelar, polvo de blaze y un fragmento de eco: tres viajes, ninguno de suerte.",
        "**Master flux.** Without it no upgrade passes %s%%. It goes on the star beside the ingredients, and one pinch is spent the time the upgrade crosses that line. Star iron, blaze powder and an echo shard: three journeys, none of them luck."),
    "gui.forja.libro.potencial.carga.titulo": ("La carga", "The load"),
    "gui.forja.libro.potencial.carga": (
        "El potencial decide también **cuánto carga** la pieza. Cada mejora **pesa** de 1 a 4 según lo que aporta, y la pieza aguanta un punto por cada %s de potencial por encima de %s: **%s** la peor que existe, **%s** una perfecta. Lo que ya lleva no se cae nunca; lo que no cabe, no entra. La barra de su descripción lo enseña: un tramo de color por mejora, claro lo libre, oscuro lo que le falta a la pieza.",
        "The potential also decides **how much a piece carries**. Every upgrade **weighs** 1 to 4 by what it is worth, and a piece holds a point for every %s of potential over %s: **%s** for the worst there is, **%s** for a perfect one. What is on it never falls off; what does not fit does not go on. The bar on the piece shows it: a run of colour for each upgrade, light for what is free, dark for what the piece lacks."),
    "gui.forja.libro.potencial.carga.libres": (
        "Los **pactos**, lo que deja el cielo y el **Recocido** no pesan. Una **sinergia** tampoco: es lo que ganas por gastar la carga en una pareja, y pide las dos al %s%%, así que una pieza mala nunca despierta ninguna. La mesa de extracción, además de guardar una mejora, **libera** lo que pesaba.",
        "**Pacts**, what the sky leaves and the **Annealing** weigh nothing. Neither does a **synergy**: it is what spending the load on a pair buys, and it asks for both at %s%%, so a poor piece never wakes one. The extraction table, besides keeping an upgrade, **frees** what it weighed."),
    "gui.forja.libro.potencial.pesan": ("**Pesan %s:** %s.", "**Weigh %s:** %s."),
    "gui.forja.libro.potencial.pesan.resto": ("Todas las demás pesan %s.", "All the others weigh %s."),
    "gui.forja.libro.potencial.todo_o_nada": (
        "**Todo o nada.** %s sólo hacen algo al 100%%, así que ningún tope las frena. Las ligeras (peso 1 y 2) suben enteras en cualquier mesa, sin mirar potencial ni fundente. Las pesadas (peso %s o más) son trabajo de la **mesa de forja mayor**: la mesa normal no acepta ni el primer ingrediente, y no gasta nada.",
        "**All or nothing.** %s do nothing short of 100%%, so no ceiling holds them. The light ones (weight 1 and 2) go all the way at any table, with neither potential nor flux asked. The heavy ones (weight %s and up) are work for the **greater forge table**: a plain bench will not take the first ingredient, and spends nothing."),
    "gui.forja.libro.potencial.fuera": (
        "Los **pactos** y lo que deja el cielo van por fuera de todo esto: llegan al 100%% en cualquier pieza y en cualquier mesa.",
        "**Pacts** and what the sky leaves behind stand outside all of this: they reach 100%% on any piece at any table."),
    "gui.forja.libro.potencial.extraccion": (
        "Quita **una** mejora de una pieza y deja todo lo demás como estaba. Pon la pieza, elige la mejora y paga con sus propios ingredientes: un paso de su receta por cada %s%% que tenga. Con un **orbe vacío** la mejora sale **entera** al orbe; sin él, se pierde. Un pacto no sale nunca.",
        "Takes **one** upgrade off a piece and leaves the rest as it was. Put the piece in, pick the upgrade and pay in its own ingredients: one step of its recipe for every %s%% of it. With an **empty orb** the upgrade comes out **whole**, into the orb; without one it is lost. A pact never comes off."),
    "gui.forja.libro.potencial.orbes": (
        "Un orbe vale lo que costó, no lo que dice. Sobre una pieza sin esa mejora devuelve todo lo que guarda; encima de la misma mejora, o fundido con otro orbe, paga el mismo precio creciente que los ingredientes. Dos orbes del 50%% hacen uno del 75%%. Lo que una pieza no pueda tomar se queda en el orbe.",
        "An orb is worth what it cost, not what it says. On a piece without that upgrade it gives back all it holds; on top of the same upgrade, or fused with another orb, it pays the same rising price ingredients do. Two orbs of 50%% make one of 75%%. Whatever a piece cannot take stays in the orb."),
    "gui.forja.libro.cap.accesorios": ("Talismanes y cinturón", "Talismans and Belt"),
    "gui.forja.libro.cap.eventos": ("Eventos del cielo", "Events in the Sky"),
    "gui.forja.libro.cap.encargos": ("Encargos", "Orders"),
    "gui.forja.libro.cap.amenazas": ("Lo que te busca", "What Comes for You"),
    "gui.forja.libro.cap.bestiario": ("Bestiario", "Bestiary"),
    "gui.forja.libro.bestiario_intro": ("Lo que el mod pone en el mundo para pelear, y lo que deja cada uno.",
                                         "What the mod puts in the world to fight, and what each one leaves behind."),
    "gui.forja.libro.bestiario.automata": ("%s de vida, 12 de armadura, no se le empuja. Guarda las forjas viejas. Al caer se deshace en una a tres piezas (hierro o piedra) y pepitas.",
                                            "%s health, 12 armor, nothing pushes it. It guards the old forges. When it falls it comes apart into one to three parts (iron or stone) and nuggets."),
    "gui.forja.libro.bestiario.coraza": (
        "Una armadura de placas que se levanta sola, con %s de vida y nada dentro. El acero comprado "
        "apenas la toca: un golpe que no venga de algo forjado (o de una flecha forjada) le hace solo el "
        "%s%%. Al caer se deshace en las placas de las que estaba hecha.",
        "A suit of plate that stands up on its own, %s health and nothing inside. Shop steel barely "
        "touches it: a blow that did not come off a star (or from a forged arrow) does only %s%%. When it "
        "falls it comes apart into the plates it was made of.",
    ),
    "gui.forja.libro.coraza_alma": (
        "Romper la coraza no mata lo que había dentro: si hay otra en pie a menos de %s bloques, se "
        "muda a esa, que se levanta con %s de vida de vuelta y %s segundos de prisa y fuerza. Conviene "
        "despejar la sala de una vez, no de una en una.",
        "Breaking the suit does not kill what was inside it: with another one standing within %s blocks "
        "it moves into that one, which comes back with %s health and %s seconds of speed and strength. "
        "Clear the room at once rather than one at a time.",
    ),
    "gui.forja.libro.coraza_visita": (
        "Y no se quedan solo en las ruinas: de noche, si tienes una mesa de forja cerca, hay un %s%% por "
        "minuto de que una se ponga de pie en la oscuridad y venga a verte. Solo una cada vez.",
        "And they do not stay in the ruins: at night, with a forge table nearby, there is a %s%% chance a "
        "minute that one stands up in the dark and comes to see you. Only ever one at a time.",
    ),
    "gui.forja.libro.ataques.herrero": (
        "Sus manos: **onda de yunque** (deja caer el martillo y un anillo de fuego corre por el suelo, "
        "%s de daño en la primera fase y más en las siguientes, te levanta y te prende; se salta) y **garfio** (desde la "
        "segunda fase, tira la garra del brazo muerto y te arrastra hasta el martillo).",
        "What he does with his hands: **anvil wave** (the hammer comes down and a ring of fire runs "
        "along the floor for %s damage in the first stage and more in the later ones, takes you off your feet and sets "
        "you alight; jump it) and "
        "**hook** (from the second stage on, the claw on the dead arm hauls you back onto the hammer).",
    ),
    "gui.forja.libro.ataques.automata": (
        "**Brasa**: si te quedas lejos abre la barriga y escupe fuego. **Vapor**: pegarse a el le hace "
        "soltar la caldera, %s de dano y lentitud a todo lo que tenga al lado, y el aguanta mejor un "
        "momento. Ni de lejos ni encima.",
        "**Ember**: stand off it and it opens its belly and spits fire. **Steam**: crowd it and the "
        "boiler lets go for %s damage and slowness to everything beside it, and it shrugs off blows "
        "for a moment. Neither far nor close.",
    ),
    "gui.forja.libro.ataques.coraza": (
        "**Embestida**: se lanza por el hueco y te corta al pasar, %s de dano. **Lamento**: se abre y "
        "llama; quien lo oye va lento y con el arma pesada, y **las demas corazas de la sala se "
        "levantan curadas**.",
        "**Lunge**: it throws itself across the gap and cuts you on the way through for %s damage. "
        "**Wail**: it opens up and calls; whoever hears it goes slow and heavy-armed, and **every other "
        "suit in the room gets back up healed**.",
    ),
    "gui.forja.libro.bestiario.pavesa": (
        "Un trozo de fragua que se escapó: una jaulita de hierro con una brasa dentro. %s de vida, vuela "
        "y es lo único rápido que hay aquí. **Picado**: se echa encima desde arriba, %s de daño y te "
        "prende. **Se aviva**: junto a fuego, lava o una mesa de forja encendida crece, pega %s más, "
        "quema el doble y pica el doble de seguido; y si la matas avivada, revienta y prende lo que "
        "tenga al lado. Pelea con ella lejos de la lumbre.",
        "A scrap of a forge that got out: a little iron cage with a coal in it. %s health, it flies, and "
        "it is the only quick thing here. **Dive**: it drops on you from above for %s damage and sets "
        "you alight. **It feeds**: next to fire, lava or a lit forge table it swells, hits %s harder, "
        "burns twice as long and dives twice as often; and one killed while fed goes off and sets "
        "whatever is beside it alight. Fight it away from the fire.",
    ),
    "gui.forja.libro.pavesa_visita": (
        "Y vienen solas: si tienes lumbre cerca —una mesa de forja, lava, una hoguera— hay un %s%% por "
        "minuto de que lleguen hasta %s de golpe, de día o de noche. La fragua que las alimenta es lo "
        "mismo que las hace peligrosas.",
        "And they come on their own: with a fire nearby — a forge table, lava, a campfire — there is a "
        "%s%% chance a minute that up to %s of them turn up at once, day or night. The forge that feeds "
        "them is the same thing that makes them dangerous.",
    ),
    "gui.forja.libro.crisol.titulo": ("El crisol", "The Crucible"),
    "gui.forja.libro.crisol": (
        "La mesa de forja alea sosteniendo los ingredientes sobre el calor que haya debajo: bien para un "
        "lingote, inútil para cien. El **crisol** es la otra mitad: **funde mena y metal** hacia las "
        "cubas, alea y devuelve el material de lo que un herrero hizo. Entra por arriba, el combustible "
        "por el lado y sale por abajo, así que se automatiza con tolvas como un horno. Clic derecho abre "
        "su pantalla, que sólo admite lo que el crisol sabe usar.",
        "The forge table alloys by holding the ingredients over whatever heat is under it: fine for one "
        "bar, hopeless for a hundred. The **crucible** is the other half: it **melts ore and metal** into "
        "the tanks, alloys, and gives back the material of whatever a smith made. In from the top, fuel "
        "from the sides, out from the bottom, so it automates with hoppers like a furnace. Right-click "
        "opens its screen, which only takes what the crucible can use.",
    ),
    "gui.forja.libro.crisol.niveles": (
        "Hay tres, y lo que puede hacer cada uno lo decide de qué está hecho: **barro** (%s de calor, %s "
        "de cabida, %s s por colada, devuelve el %s%% de una pieza fundida), **hierro** (%s, %s, %s s, "
        "%s%%) y **obsidiana** (%s, %s, %s s, %s%% y **un lingote de más en cada aleación**). El de barro "
        "hará bronce toda la vida y no tocará la netherita; el de obsidiana lo funde todo.",
        "There are three, and what each can do is decided by what it is made of: **clay** (%s heat, holds "
        "%s, %s s a pour, gives back %s%% of a melted part), **iron** (%s, %s, %s s, %s%%) and "
        "**obsidian** (%s, %s, %s s, %s%% and **an extra bar on every alloy**). The clay one will make "
        "bronze forever and never touch netherite; the obsidian one melts anything.",
    ),
    "gui.forja.libro.cuba": (
        "Y el almacén: la **cuba de colada**, cristal y bronce, de %s cada una. Las que se tocan y llevan "
        "el mismo metal (o ninguno) se funden en **un solo depósito**, con un solo nivel que sube por el "
        "cristal. **Un depósito, un metal.** El crisol vuelca en los depósitos de su red de uno en uno, y "
        "si saca de una cuba sus ingredientes **cuela al doble de velocidad**, porque el metal ya está "
        "líquido. Todo esto lo cuenta paso a paso «Montar una fundición».",
        "And the store: the **melt tank**, glass and bronze, holding %s each. Tanks that touch and carry the "
        "same metal (or none) merge into **one bank**, with a single level rising through the glass. **One "
        "bank, one metal.** The crucible pours into the banks of its network one at a time, and one that "
        "takes its ingredients out of a tank **pours in half the time**, because the metal is already "
        "molten. \"Building a foundry\" goes through all of it step by step.",
    ),
    "gui.forja.libro.caja.titulo": ("La caja de moldeo", "The Casting Box"),
    "gui.forja.libro.caja": (
        "Aquí se preparan los moldes. Mete una **pieza acabada** en la caja con **acero refractario** al lado: "
        "el acero se vuelca sobre ella, **la pieza se destruye** y sale su **molde**. Es lo único del mod "
        "que rompe a propósito algo que hiciste, y debe serlo — un molde vale una pieza porque a partir "
        "de ahí no vuelves a cortar esa pieza a mano.",
        "This is where moulds are made. Put a **finished part** in the box with **refractory steel** beside it: the "
        "steel is poured over it, **the part is destroyed** and its **mould** comes out. It is the only "
        "thing in the mod that deliberately breaks something you made, and it should be — a mould is "
        "worth a part because from then on you never cut that part by hand again.",
    ),
    "gui.forja.libro.caja.colar": (
        "La caja no cuela: el molde va encima de una **mesa de colada**, bajo un colador, y es la mesa la "
        "que se llena con el metal que le cae de un caño o le da una cuba al lado, gastando lo que cuesta "
        "la pieza. El molde no se gasta. Sin plantilla y sin cortar nada: mena al crisol, colada a las "
        "cubas, cubas a la mesa, piezas por abajo.",
        "The box does not cast: the mould goes on top of a **casting table**, under a strainer, and it is the "
        "table that fills with the metal falling from a spout or given by a tank beside it, spending what "
        "the part costs. The mould is not used up. No template and nothing cut: ore into the crucible, melt "
        "into the tanks, tanks to the table, parts out of the bottom.",
    ),
    "gui.forja.libro.caja.niveles": (
        "Y hay tres, según de qué esté hecha, porque un molde de barro revienta con lo duro: **barro** "
        "(peltre y arena) aguanta hasta %s de dureza, **acero** (acero refractario) hasta %s, y "
        "**damasco** aguanta cualquier metal del mod. El límite sale de la dureza del propio material, "
        "así que un metal nuevo cae solo en el nivel que le toca.",
        "And there are three, by what it is built of, because a clay mould cracks on the hard stuff: "
        "**clay** (pewter and sand) holds up to %s hardness, **steel** (refractory steel) up to %s, and "
        "**damascus** holds any metal in the mod. The limit is read off the material's own hardness, so "
        "a new metal lands in the right tier by itself.",
    ),
    "gui.forja.libro.farol": (
        "Y se pueden coger vivas: con un **farol vacío** en la mano, clic derecho sobre una **avivada** "
        "y se mete dentro. Apagada no entra. Puesto bajo una mesa de forja **vale por la lava**, así que "
        "quien sepa cazarlas no vuelve a acarrear lava al taller.",
        "And they can be taken alive: with an **empty lantern** in hand, right-click a **fed** one and "
        "it goes in. A cold one will not. Set under a forge table it **is worth lava**, so a smith who "
        "can catch them never hauls lava into the workshop again.",
    ),
    "gui.forja.libro.farol_precio": (
        "Eso sí: el farol llama a las demás. Un taller con una pavesa enjaulada es un taller que visitan.",
        "One catch: the lantern calls the rest in. A workshop with a caged wisp is a workshop they visit.",
    ),
    "gui.forja.libro.ataques.elite": (
        "**Embate de leyenda**: si te alejas, salta y cae encima. **Segundo aliento**: una sola vez, a "
        "un cuarto de vida, se cura el %s%% y vuelve con fuerza, resistencia y prisa.",
        "**Legend charge**: back away and it jumps the gap and lands on you. **Second wind**: once "
        "only, at a quarter health, it heals %s%% and comes back with strength, resistance and speed.",
    ),
    "gui.forja.libro.ataques.capitan": (
        "**Cerrar filas**: toca el cuerno y la banda entera va más rápido y pega más fuerte %s "
        "segundos. **Carga**: cierra la distancia de un salto. Mátalo a él primero.",
        "**Close ranks**: he winds the horn and the whole band moves faster and hits harder for %s "
        "seconds. **Charge**: he closes the gap in one jump. Kill him first.",
    ),
    "gui.forja.libro.bestiario.capitan": ("Trae %s saqueadores con equipo forjado. Suelta su leyenda a maestría 10 y de dos a cuatro orbes de mejora.",
                                           "He brings %s raiders in forged gear. He leaves his legend at Maestria 10 and two to four upgrade orbs."),
    "gui.forja.libro.yunque": ("Su yunque también se queda: puesto junto a una mesa de forja vale por las otras dos, así que un taller completo cabe en dos bloques.",
                               "His anvil stays too: set next to a forge table it is worth the other two, so a whole workshop fits in two blocks."),
    "gui.forja.libro.bestiario.herrero": ("%s de vida en este nivel para uno solo (más con buen equipo y con cada jugador de más) y tres tramos, que se parten a dos tercios y a un tercio. Suelta su corazón de forja (que además repara cualquier pieza por completo en la estrella), una leyenda, el martillo del maestro, su yunque la primera vez y una Estrella forjada para cada uno que peleó.",
                                           "%s health on this level for one player (more with good gear and with every extra player) and three stages, split at two thirds and one third. He leaves his forge heart (which also puts any piece back together at the star), a legend, the master's hammer, his anvil the first time and a Forged Star for everyone who fought."),

    "gui.forja.libro.aleaciones_intro": ("Dos metales en las puntas de la estrella no hacen nada sobre piedra. La mesa funde con el calor que tiene debajo: pon una fogata, un bloque de magma o lava bajo ella y los mismos ingredientes te darán cosas distintas. Hay %s aleaciones: las más duras solo salen sobre lava, y las tres mejores solo a forja blanca, en el crisol de obsidiana o con aliento de forja por un tubo.",
                                         "Two metals on the points of the star do nothing on bare stone. The table melts with the heat under it: put a campfire, a magma block or lava beneath it and the same ingredients give you different things. There are %s alloys: the hardest only come off lava, and the best three only at white heat, in the obsidian crucible or with forge breath down a pipe."),
    "gui.forja.libro.aleacion_linea": ("%s, %s por tanda", "%s, %s per batch"),
    "gui.forja.libro.aleaciones_comparar": ("Durabilidad de una espada entera", "Durability of a whole sword"),
    "gui.forja.libro.fundir_piezas": (
        "Con la mesa sobre lava, piezas sueltas del mismo material vuelven a ser material: cada una "
        "devuelve la mitad de lo que costó cortarla, redondeando hacia abajo, y la pila entera de una vez. "
        "No es una forma de hacer material, es la salida para las piezas que cortaste mal; un juego que "
        "forma un objeto se forja, no se derrite.",
        "With the table over lava, loose parts of one material go back to being material: each gives "
        "back half of what it cost to cut, rounded down, and the whole pile at once. It is not a way to "
        "make material, it is the way out of the parts you cut wrong; a set that makes an item is forged, "
        "not melted.",
    ),
    "gui.forja.libro.calor.templada": ("Mesa templada", "Warm table"),
    "gui.forja.libro.calor.templada.desc": ("Una fogata o un fuego debajo", "A campfire or a fire underneath"),
    "gui.forja.libro.calor.caliente": ("Mesa caliente", "Hot table"),
    "gui.forja.libro.calor.caliente.desc": ("Magma, fuego de almas o un alto horno encendido", "Magma, soul fire or a lit blast furnace"),
    "gui.forja.libro.calor.fundida": ("Mesa fundida", "Molten table"),
    "gui.forja.libro.calor.fundida.desc": ("Lava, o un caldero de lava", "Lava, or a cauldron of it"),
    "gui.forja.libro.fundicion_intro": (
        "La estrella funde de uno en uno. La fundición funde a paletadas: un crisol que no necesita que estés delante, cubas donde se guarda el metal líquido y conductos para llevarlo. Se construye en este orden.",
        "The star melts one bar at a time. A foundry melts by the shovelful: a pot that does not need you standing over it, tanks to hold the molten metal and pipes to move it. Build it in this order."),
    "gui.forja.libro.fundicion.paso1": ("1. El crisol", "1. The crucible"),
    "gui.forja.libro.fundicion.paso1.desc": (
        "Lo primero. Come ascuas (%s segundos cada una) y le metes mena o lingotes por arriba, con una tolva o en su pantalla. Con cubas en su red (pegadas o por conducto) lo funde todo dentro de ellas, y si ninguna tiene sitio ESPERA; sin ninguna cuba, la mena sale en lingotes por abajo. Lo duro pide un crisol más caliente. Nadie tiene que mirarlo.",
        "First of all. It eats embers (%s seconds apiece) and takes ore or ingots in from above, by hopper or in its screen. With tanks on its network (touching it or down a pipe) everything melts into them, and if none has room it WAITS; with no tank at all, ore comes out below as ingots. The hard metals want a hotter crucible. Nobody has to watch it."),
    "gui.forja.libro.fundicion.crisol_linea": ("· %s: calor %s, cabida %s, %ss por colada", "· %s: %s heat, holds %s, %ss per pour"),
    "gui.forja.libro.fundicion.paso1.farol": (
        "Un farol de pavesa justo debajo lo mantiene encendido sin gastar ascuas. Y mientras arde, arde tan caliente como lo que tenga debajo, si es más que su propio calor: sobre magma, caliente; sobre lava o un farol, fundido.",
        "A wisp lantern directly underneath keeps it lit without spending embers. And while it burns, it burns as hot as what it stands on, if that is hotter than its own heat: on magma, hot; on lava or a lantern, molten."),
    "gui.forja.libro.fundicion.paso2": ("2. Las cubas", "2. The tanks"),
    "gui.forja.libro.fundicion.paso2.desc": (
        "Pega cubas del mismo metal (o vacías) unas a otras, en cualquier dirección, y se FUNDEN en un solo depósito: %s por cuba, hasta %s cubas, un solo metal y un solo nivel, que sube por el cristal de abajo arriba. Si rompes una, derrama lo suyo y el resto sigue siendo depósito con lo que tenía. Dos metales distintos pegados no se mezclan: son dos depósitos. Un comparador al lado de cualquier cuba lee lo lleno que está el depósito entero.",
        "Set tanks of the same metal (or empty ones) against each other, any way round, and they MERGE into one deposit: %s each, up to %s tanks, one metal and one level, rising through the glass from the bottom up. Break one and it spills its own share; the rest stay a deposit with what they held. Two different metals touching do not mix: they are two deposits. A comparator beside any tank reads how full the whole deposit is."),
    "gui.forja.libro.fundicion.paso3": ("3. Los conductos", "3. The pipes"),
    "gui.forja.libro.fundicion.paso3.desc": (
        "No guardan nada: conectan. Crisoles, cubas, caños y mesas unidos por conductos son UNA red, y el metal va de donde sale a donde hace falta aunque estén lejos (hasta %s bloques de conducto). Pero se enfría por el camino, y de qué estén hechos decide cuánto.",
        "They hold nothing: they connect. Crucibles, tanks, spouts and tables joined by pipe are ONE network, and the metal goes from where it comes out to where it is wanted however far apart they are (up to %s blocks of pipe). But it cools on the way, and what they are made of decides how much."),
    "gui.forja.libro.fundicion.conducto_linea": ("· %s: pierde %s de calor por tramo", "· %s: loses %s heat per run"),
    "gui.forja.libro.fundicion.llenado": (
        "Se llena de UNO EN UNO: lo que sale del crisol va a un solo depósito hasta llenarlo, y sólo entonces empieza el siguiente. Primero el que ya tiene ese metal y no está lleno; si no hay, el vacío más cercano por la red. Un depósito con otro metal no se toca nunca. Si no queda ninguno con sitio, el crisol espera y su pantalla lo dice.",
        "It fills ONE AT A TIME: what comes out of the crucible goes into a single deposit until it is full, and only then does the next one start. First the one already holding that metal and not full; failing that, the nearest empty one along the network. A deposit holding another metal is never touched. If none has room left, the crucible waits and its screen says so."),
    "gui.forja.libro.fundicion.llave": (
        "La llave de paso es un tramo de conducto con compuerta: abierta deja pasar, cerrada corta la red en dos justo ahí. Se abre y se cierra con clic derecho, y una señal de redstone la cierra mientras dure (un comparador en una cuba llena puede cerrar su propia entrada). Se ve de lejos: abierta, la rueda roja arriba y el metal pasando por debajo; cerrada, la compuerta roja metida en el canal.",
        "The valve is a length of channel with a gate in it: open it lets the metal through, closed it cuts the network in two right there. Right-click to open or close it, and a redstone signal keeps it shut for as long as it lasts (a comparator on a full tank can close its own inlet). It reads from across the room: open, the red wheel is up high and the metal runs under it; closed, the red gate stands in the channel."),
    "gui.forja.libro.fundicion.cano": (
        "El caño de colada es el final del tramo: tiene el suelo agujereado y lo que le llega CAE, hasta %s bloques, sobre lo que haya debajo. Es la única forma de dar de comer a algo que está en otro piso, y sólo cuela cuando tiene dónde caer: si ves el chorro, el tramo está conectado. Caer cuesta %s de calor por bloque, peor que el peor conducto.",
        "The spout is the end of a run: its floor is open and what reaches it FALLS, up to %s blocks, into whatever is underneath. It is the only way to feed something on another floor, and it only pours when it has somewhere to land: if you can see the stream, the run is connected. Falling costs %s heat a block, worse than the worst pipe there is."),
    "gui.forja.libro.fundicion.paso4": ("4. El calor", "4. Keeping it hot"),
    "gui.forja.libro.fundicion.paso4.desc": (
        "Una cuba sola cuaja en unos %s segundos y ya no sirve para colar. Un farol, lava, magma, fuego o un crisol encendido al lado la mantienen líquida. Si cuaja, un crisol encendido al lado la vuelve a fundir, pero se come el %s%% de lo que había.",
        "A tank left alone sets in about %s seconds and is no use for pouring. A lantern, lava, magma, fire or a lit crucible beside it keeps it liquid. If it does set, a lit crucible beside it melts it again, but it eats %s%% of what was there."),
    "gui.forja.libro.fundicion.paso5": ("5. La caja de moldeo", "5. The casting box"),
    "gui.forja.libro.fundicion.paso5.desc": (
        "Aquí se prepara lo que luego se cuela. Metes una pieza y %s de acero refractario: la pieza se destruye y queda su MOLDE. La caja no llena el molde: eso se hace en una mesa de colada (paso 7). También corta marcos y baña coladores, y las cajas mejores aguantan metales más duros y van más rápido.",
        "This is where what gets poured is made ready. Put in a part and %s refractory steel: the part is destroyed and its MOULD is left. The box does not fill the mould: that happens on a casting table (step 7). It also cuts frames and bathes strainers, and better boxes take harder metals and work faster."),
    "gui.forja.libro.fundicion.paso6": ("6. Los coladores", "6. The strainers"),
    "gui.forja.libro.fundicion.paso6.desc": (
        "El colador se pone como un bloque encima de la mesa de colada, entre el caño y el molde: el metal cae a través de él. Uno de barro aguanta hasta %s de dureza; para uno mejor mételo en la caja de moldeo y déjale caer encima %s de un metal más duro: sale hecho de ese metal. Si lo rompes vuelve tal cual, con su metal.",
        "The strainer is set down like a block on top of the casting table, between the spout and the mould: the metal falls through it. A clay one takes up to %s hardness; for a better one, put it in the casting box and pour %s of a harder metal over it: it comes out made of that metal. Break it and it comes back as it was, metal and all."),
    "gui.forja.libro.fundicion.paso6.basta": (
        "Si el colador no aguanta el metal, se rompe al empezar la colada y sale BASTA: %s%% menos de todo. Sin colador, igual de basta. Con uno que aguanta sale limpia, y una pieza limpia trae además una mejora del %s%%.",
        "If the strainer will not take the metal it breaks as the pour starts and the casting comes out ROUGH: %s%% less of everything. With no strainer at all, just as rough. Through one that holds it comes out clean, and a clean part also carries a %s%% upgrade."),
    "gui.forja.libro.fundicion.paso7": ("7. Las mesas de colada", "7. The casting tables"),
    "gui.forja.libro.fundicion.paso7.desc": (
        "Aquí se cuela todo. Pon encima de la mesa un MOLDE y sale esa pieza; pon un MARCO (lo corta la caja de una herramienta acabada con %s de acero refractario) y sale la herramienta entera. Le cae justo el metal que vale, desde un caño por encima o de una cuba al lado, y en %s segundos la recoges. Con una tolva debajo sale sola, y otra de lado le mete moldes o marcos.",
        "Everything is poured here. Set a MOULD on the table and that part comes out; set a FRAME (the box cuts one off a finished tool with %s refractory steel) and the whole tool comes out. Exactly the metal it is worth falls into it, from a spout above or a tank beside it, and %s seconds later you pick it up. A hopper underneath takes it out by itself, and one at the side feeds it moulds or frames."),
    "gui.forja.libro.fundicion.mesa_linea": ("· %s: aguanta hasta %s, pierde %s de calor por segundo, %s%% de salir perfecta",
                                              "· %s: holds up to %s, loses %s heat a second, %s%% to come out perfect"),
    "gui.forja.libro.fundicion.paso7.frio": (
        "Las tres cuelan igual: cambian en qué metal aguantan y, sobre todo, en guardar el calor. Cada colada gasta %s de calor y una mesa fría no empieza ninguna; la que empieza con las últimas brasas cuaja antes de tiempo y sale BASTA (-%s%% en todo). Ponle un farol de pavesa debajo y se acabó el problema.",
        "All three pour the same: they differ in the metal they will take and, above all, in holding heat. Each pour spends %s heat and a cold table starts none; one started on the last of it sets early and comes out ROUGH (-%s%% on everything). Put a wisp lantern under it and the problem goes away."),
    "gui.forja.libro.fundicion.calor": ("8. Calor por tubos", "8. Heat down a pipe"),
    "gui.forja.libro.fundicion.calor.desc": (
        "Un segundo sistema de tubos que nunca se junta con el del metal. La caldera (%s mB) o el depósito de calor (%s mB) llenan de un fluido los tubos de calor que tocan, y todo lo que pide calor y toca un tubo (crisol, mesa de colada, mesa de forja) recibe el calor de ese fluido: se usa el mejor entre ese y lo que tenga debajo. Un crisol con un tubo caliente no gasta ascuas. Se llenan a mano o con tolva (los cubos vacíos salen por abajo) y un comparador lee lo llenos que están.",
        "A second pipe system that never meets the metal one. The boiler (%s mB) or the heat depot (%s mB) fill the heat pipes they touch with a fluid, and anything that wants heat and touches a pipe (crucible, casting table, forge table) gets that fluid's heat: whichever is better, the pipe or what it stands on. A crucible with a hot pipe burns no embers. Fill them by hand or by hopper (the empty buckets come out below); a comparator reads how full they are."),
    "gui.forja.libro.fundicion.fluido_titulo": ("%s · calor %s", "%s · %s heat"),
    "gui.forja.libro.fundicion.fluido.vapor": (
        "Un cubo de agua (%s mB) en la caldera, con cualquier fuego debajo. Gasta %s mB por tick. Barato, pero sólo funde lo blando (dureza hasta %s: oro, cobre, peltre, latón; el hierro no) y la mesa de colada no pasa del %s%% de calor.",
        "A bucket of water (%s mB) in the boiler, with any fire under it. Spends %s mB a tick. Cheap, but it only melts the soft metals (hardness up to %s: gold, copper, pewter, brass; not iron) and a casting table never gets past %s%% heat."),
    "gui.forja.libro.fundicion.fluido.lava": (
        "Cubos de lava (%s mB) o bloques de magma (%s mB) en el depósito de calor. Gasta %s mB por tick. Lo mismo que la lava debajo de la mesa, pero llevado por tubos a donde haga falta.",
        "Buckets of lava (%s mB) or magma blocks (%s mB) in the heat depot. Spends %s mB a tick. Just what lava under the table gives, but carried by pipe to wherever it is wanted."),
    "gui.forja.libro.fundicion.fluido.sangre_de_blaze": (
        "Varas de blaze (%s mB) o polvo de blaze (%s mB) en la caldera, sin fuego. Gasta %s mB por tick. El crisol funde al %s%% de velocidad y la mesa de colada gana %s de calor por segundo, el doble que con fuego.",
        "Blaze rods (%s mB) or blaze powder (%s mB) in the boiler, no fire needed. Spends %s mB a tick. A crucible melts at %s%% speed and a casting table gains %s heat a second, twice what a fire gives."),
    "gui.forja.libro.fundicion.fluido.aliento_de_forja": (
        "Escoria (%s mB) o un corazón de forja (%s mB) en la caldera, sobre un fuego fuerte (magma, fuego de almas, lava o un farol). Gasta %s mB por tick. Forja blanca sin crisol de obsidiana, y en la mesa de colada una herramienta tiene un %s%% más de salir perfecta.",
        "Slag (%s mB) or a forge heart (%s mB) in the boiler, over a hot fire (magma, soul fire, lava or a lantern). Spends %s mB a tick. White heat without the obsidian crucible, and a tool on a casting table has %s%% more chance of coming out perfect."),
    "gui.forja.libro.fundicion.fluido.salmuera_helada": (
        "Hielo compacto (%s mB) o hielo azul (%s mB) en la caldera. No calienta, enfría: la mesa de colada templa en agua cada herramienta que cuela y la mesa de forja lo recién forjado (gasta %s mB por temple), una mesa sin fuego se enfría el doble de rápido, y un crisol que la toca se apaga.",
        "Packed ice (%s mB) or blue ice (%s mB) in the boiler. It does not heat, it cools: a casting table quenches every tool it pours in water, and a forge table what it has just forged (%s mB a quench); a table with no fire cools twice as fast, and a crucible it touches goes out."),
    "gui.forja.libro.fundicion.calor.uno": (
        "Un tubo lleva un solo fluido, el que más haya en sus calderas: para salmuera y lava a la vez, dos tramos distintos que toquen la misma mesa.",
        "A pipe carries one fluid, whichever its vessels hold most of: for brine and lava at once, lay two runs that touch the same table."),
    "gui.forja.libro.fundicion.paso8": ("9. La fundición sin manos", "9. A foundry that runs itself"),
    "gui.forja.libro.fundicion.paso8.desc": (
        "Nada de esto necesita que estés delante. Una tolva encima del crisol le mete la mena y otra al lado las ascuas; la cuba, con un farol debajo, no cuaja nunca; el caño cuela a través del colador sobre el molde, la mesa se calienta con un farol AL LADO y una tolva debajo se lleva cada pieza a un cofre. Mientras haya metal y calor, la mesa vuelve a colar sola.",
        "None of this needs you standing there. A hopper on top of the crucible feeds it ore and one beside it embers; the tank, on a lantern, never sets; the spout pours through the strainer onto the mould, the table is kept hot by a lantern BESIDE it and a hopper underneath takes every part to a chest. As long as there is metal and heat, the table pours again by itself."),
    "gui.forja.libro.fundicion.paso9": ("10. La montadora", "10. The assembler"),
    "gui.forja.libro.fundicion.paso9.desc": (
        "La estrella de la mesa de forja sin el herrero. Las piezas le entran por tolva, por arriba o por los lados, y monta lo mismo que la mesa, con la misma regla; la pieza sale por abajo. Calidad normal siempre: nunca perfecta, sin firma, con el potencial de un golpe decente. Mejoras, reparaciones, cambios de piezas y técnicas siguen siendo cosa tuya, en la mesa.",
        "The forge table's star without the smith. Parts go in by hopper, from the top or the sides, and it builds what the table builds, by the same rule; the piece comes out underneath. Always a plain press: never perfect, unsigned, with the potential of a decent strike. Upgrades, repairs, swaps and techniques are still yours, at the table."),
    "gui.forja.libro.fundicion.paso9.calor": (
        "Pide calor como la mesa: al menos una fogata, debajo o AL LADO (debajo va la tolva). Con calor templado tarda %s s por pieza; caliente, %s s; con lava o un farol, %s s. Un comparador marca 15 cuando hay una pieza esperando y sube de 1 a 14 mientras monta.",
        "It wants heat like the table: at least a campfire, under it or BESIDE it (the hopper goes underneath). Warm, it takes %s s a piece; hot, %s s; on lava or a lantern, %s s. A comparator reads 15 while a piece is waiting and climbs from 1 to 14 while it works."),
    "gui.forja.libro.fundicion.paso9.marco": (
        "Sin marco, cada punta de la estrella lleva una clase de pieza, como en la mesa. Con un MARCO en el centro sabe qué montar: sólo acepta las piezas de eso, las busca donde estén y nunca se atasca; así saca también espadones, que llevan dos hojas.",
        "With no frame each point of the star takes one kind of part, as at the table. With a FRAME in the centre it knows what to build: it only takes that piece's parts, finds them wherever they lie and never jams; that is also how it makes greatswords, which take two blades."),
    "gui.forja.libro.fundicion.blanca": ("Y para qué todo esto", "And what all this is for"),
    "gui.forja.libro.fundicion.blanca.desc": (
        "El crisol de obsidiana es lo único del mod que arde a forja blanca por sí mismo. No hay bloque que dé ese calor a una mesa, ni técnica que lo lea: sólo el aliento de forja por un tubo, y se paga caro. Estas tres aleaciones se hacen así o no se hacen.",
        "The obsidian crucible is the only thing in the mod that burns at white heat by itself. No block gives a table that heat and no technique reads its way up to it: only forge breath down a pipe, and that is dear. These three alloys are made that way or not at all."),
    "gui.forja.libro.calor.forja_blanca": ("Forja blanca", "White heat"),
    "gui.forja.libro.calor.forja_blanca.desc": (
        "No hay bloque que dé este calor: sólo el crisol de obsidiana o el aliento de forja por un tubo, y sólo así se hacen estas tres",
        "No block gives this heat: only the obsidian crucible or forge breath down a pipe, and only so are these three made"),

    "gui.forja.libro.temple_intro": ("Una pieza recién forjada sale caliente y lo sigue estando %s segundos. Si en ese rato la apagas en algo, ese algo se queda en el acero para siempre: un temple por pieza y no hay manera de cambiarlo. Con ella en la mano o puesta, métete al agua, a la lava, a la nieve polvo o ponte sobre un bloque de miel; lo que llevas en la mochila no se templa.",
                                      "A freshly forged piece comes out hot and stays that way for %s seconds. Put that heat out in something in that time and it stays in the steel for good: one quench per piece, and there is no changing it. Holding it or wearing it, step into water, into lava, into powder snow, or stand on a honey block; what is in your bag is not quenched."),

    "gui.forja.libro.herrero_intro": ("Aparte de la maestría de cada objeto, tú también aprendes. Forjar, cambiar piezas, mejorar y grabar dones suben tu maestría de herrero hasta el nivel %s, y eso te sigue a todas las mesas: la ventana del martillo es más ancha, cada ingrediente da un poco más de porcentaje, y lo que sale de tu estrella nace ya rodado. Míralo con /forja herrero.",
                                       "Beyond the Maestria of each piece, you learn too. Forging, swapping parts, upgrading and engraving gifts raise your smith Maestria up to level %s, and it follows you to every table: the hammer window is wider, every ingredient gives a little more percent, and what leaves your star is already broken in. Check it with /forja herrero."),
    "gui.forja.libro.perfecta.titulo": ("Forja perfecta", "Perfect forge"),
    "gui.forja.libro.perfecta": ("Al forjar una pieza nueva, el botón no forja: empieza a mover un martillo por un carril. Pulsa otra vez y donde lo pares decide el golpe. Si lo clavas en el centro iluminado, la pieza sale perfecta y sube un 5%% en todo, para siempre. El centro se ensancha con tu maestría de herrero.",
                                 "Forging a new piece does not press the button: it starts a hammer running along a rail. Press again, and where you stop it is the strike. Stop it dead in the lit middle and the piece comes out perfect, with every number 5%% higher, for good. That middle gets wider with your smith Maestria."),
    "gui.forja.libro.herencia.titulo": ("Herencia", "Inheritance"),
    "gui.forja.libro.herencia": ("Pon una pieza vieja del mismo tipo (maestría %s o más) en una punta y la nueva al centro: la vieja se gasta y le pasa la mitad (%s%%) de todo lo que había aprendido, y su don si la nueva no tiene ninguno. Es la manera de no tirar una herramienta veterana al cambiar de material.",
                                 "Put an old piece of the same kind (Maestria %s or more) on a point and the new one in the middle: the old one is spent and hands over half (%s%%) of everything it had learned, and its gift if the new one has none. It is how you retire a veteran tool without throwing away what it knew."),
    "gui.forja.libro.firma.titulo": ("Firma y afinidad", "Signature and affinity"),
    "gui.forja.libro.firma": ("Todo lo que sale de tu estrella lleva tu nombre. En tus manos rinde un %s%% más de daño y de minado que en las de cualquier otro; lo que te encuentres o te compres, no.",
                              "Everything that leaves your star carries your name. In your hands it is worth %s%% more damage and mining than in anyone else's; whatever you find or buy is not."),
    "gui.forja.libro.historia.titulo": ("Historia del objeto", "The story of a piece"),
    "gui.forja.libro.historia": ("Cada pieza cuenta lo que ha hecho: bajas, bloques, segundos en el aire y capturas. Está al final del tooltip.",
                                 "Each piece keeps count of what it has done: kills, blocks, seconds in the air and catches. It is at the bottom of the tooltip."),

    "gui.forja.libro.armas_comparadas": ("Daño por segundo, todas en hierro", "Damage per second, all in iron"),
    "gui.forja.libro.armas_comparadas.desc": ("Con cabeza de hierro y mango de madera, sin mejoras: lo que cada arma hace en un segundo si no fallas.",
                                              "With an iron head and a wooden handle, no upgrades: what each weapon does in a second if you do not miss."),
    "gui.forja.libro.parada.titulo": ("Parada y contraataque", "Parry and counter"),
    "gui.forja.libro.parada": ("Un escudo forjado cubre desde el primer tick, no tarda en subir. Lo que decide la placa es la ventana de parada: si el golpe entra en esos primeros ticks, lo paras en vez de bloquearlo. Devuelves parte del daño, lo tiras hacia atrás, las flechas vuelven al arquero y tu siguiente golpe en un segundo vale el doble. Reflejos y la maestría ensanchan la ventana; Baluarte le suma 4 ticks.",
                               "A forged shield covers you from the first tick; it does not need raising. What the plate decides is the parry window: if the blow lands in those first ticks you catch it instead of blocking it. Part of the damage goes back, the attacker is thrown off, arrows fly back at the archer, and your own next blow within a second is worth double. Reflexes and Maestria widen the window; Bulwark adds four ticks."),
    "gui.forja.libro.frenesi.titulo": ("Frenesí", "Frenzy"),
    "gui.forja.libro.frenesi": ("Dos golpes en menos de %2$s segundos abren la barra de frenesí. Cada golpe la sube hasta %1$s, y mientras esté alta tus mejoras rinden por encima de su número: las baratas hasta x2,5 y las caras solo hasta x1,3, así que el frenesí premia el equipo humilde.",
                                "Two blows inside %2$s seconds open the frenzy bar. Every blow after that pushes it up to %1$s, and while it is high your upgrades work past their own numbers: the cheap ones up to x2.5 and the expensive ones only to x1.3, so a frenzy rewards plain gear."),
    "gui.forja.libro.sangrado.titulo": ("Sangrado", "Bleeding"),
    "gui.forja.libro.sangrado": ("Los críticos de daga y guadaña abren heridas: el sangrado se acumula, ignora la armadura y hace daño cada segundo. Desgarro sube cuántas heridas aguanta un mismo objetivo, y cuando no caben más la herida se vuelve marchitamiento.",
                                 "Crits from a dagger or a scythe open wounds: bleeding stacks, ignores armor and costs blood every second. Rend raises how many wounds one target can carry, and when no more fit the wound festers into wither."),
    "gui.forja.libro.especiales.titulo": ("Golpes especiales", "Special moves"),
    "gui.forja.libro.especiales": ("Agáchate y usa: el espadón hace Torbellino, el martillo y el mazo Sismo, y el escudo embiste a lo que tengas delante. El mangual reparte en área y pasa por encima de los escudos; los guanteletes pegan rapidísimo y viven del frenesí.",
                                   "Crouch and use: the greatsword sweeps, the hammer and the mace quake, and the shield bashes whatever is in front of you. The flail spreads its blow and swings past shields; the gauntlets punch fast and live off the frenzy."),
    "gui.forja.libro.lanzar.titulo": ("Armas arrojadizas", "Throwing weapons"),
    "gui.forja.libro.lanzar": ("Agáchate y usa un hacha o una daga para lanzarla: hace dos tercios de su daño y se queda en el suelo, salvo que tenga Retorno. El tridente vuela de punta y vuelve siempre a tu mano; si tienes el inventario lleno, te espera hasta que hagas hueco. El escudo con Bumerán sale volando, empuja y aturde hasta a tres y vuelve solo. Y Lanzacabezas sigue lanzando la cabeza de una herramienta.",
                               "Crouch and use an axe or a dagger to throw it: it bites for two thirds and stays where it lands, unless it has Return. The trident flies point first and always comes back to your hand; if your inventory is full, it waits until you make room. A shield with Boomerang goes out, shoves and stuns up to three and comes back on its own. And Head Throw still throws the head of a tool."),
    "gui.forja.lanzada.espera": ("%s te espera: haz hueco en el inventario", "%s is waiting for you: make room in your inventory"),
    "gui.forja.libro.caballo.titulo": ("Lanza a caballo", "A lance at the gallop"),
    "gui.forja.libro.caballo": ("Una lanza forjada golpea el doble si vas montado y a galope, y suena la corneta cuando entra.",
                                "A forged lance hits twice as hard from the saddle at a gallop, and the horn sounds when it lands."),

    "gui.forja.libro.pactos_intro": ("Cuatro mejoras que dan más de lo que debería dar cualquier mejora, y cobran por ello. Son para quien prefiere pegar mucho más fuerte y vivir con lo que cuesta.",
                                      "Four upgrades that give more than any upgrade should, and charge you for it. They are for the player who would rather hit far harder and live with what it costs."),
    "gui.forja.libro.pacto_ofrenda": ("Se abre una vez ofreciendo %s en la estrella con sus ingredientes.",
                                       "Opened once by offering %s on the star with its ingredients."),
    "gui.forja.libro.pactos_limite": ("Cada pacto se abre una sola vez y queda abierto para siempre. Una pieza lleva %s pactos como mucho.",
                                       "Each pact is opened once and stays open for good. A piece carries %s pacts at most."),
    "gui.forja.libro.pactos_aviso": ("Un pacto no se quita: la piedra de afilar no toca lo forjado y las mejoras no bajan. Piénsalo antes.",
                                      "A pact cannot be undone: the grindstone will not touch forged gear and upgrades never go down. Think first."),

    "gui.forja.libro.talismanes.titulo": ("Talismanes", "Talismans"),
    "gui.forja.libro.talismanes": ("Una gema en un engaste, hecha en la mesa de trabajo con ocho pepitas de oro alrededor de la piedra. No se equipa: basta con llevarla. Solo cuenta el primero que encuentra la mochila, así que elegir cuál viaja contigo es toda la mecánica.",
                                    "A gem in a setting, made at the workbench with eight gold nuggets around the stone. It is not worn: carrying it is enough. Only the first one your pack finds counts, so choosing which stone rides with you is the whole mechanic."),
    "gui.forja.libro.talisman_linea": ("%s: %s", "%s: %s"),
    "gui.forja.libro.cinturon.titulo": ("Cinturón de herramientas", "Tool belt"),
    "gui.forja.libro.cinturon": ("Clic derecho con un pico, hacha, pala o azada sobre el cinturón para colgarla (hasta %s), y con la mano vacía para descolgar la última. Al empezar a picar te pone la buena en la mano y guarda la que llevabas. Cuesta 1 de durabilidad y medio segundo por cambio, y no funciona si algo te está pegando.",
                                 "Right-click a pick, axe, shovel or hoe onto the belt to hang it there (up to %s), and with an empty hand to take the last one back. When you start on a block it puts the right tool in your hand and keeps the one you were holding. It costs the belt one durability and half a second per swap, and it will not work while something is hitting you."),

    "gui.forja.libro.eventos_intro": ("De vez en cuando el cielo hace algo. Cada evento trae una mejora que no existe en ninguna otra parte, y la única manera de quedártela es tener una jarra de esencia y usarla a cielo abierto mientras pasa. La jarra guarda esa noche y toma su color; al vaciarla te da el orbe, que se pone en la estrella como cualquier otro.",
                                       "Now and then the sky does something. Each event carries an upgrade that exists nowhere else, and the only way to keep it is to have an essence jar and use it under the open sky while it happens. The jar keeps that night and takes its colour; emptying it gives you the orb, which goes on the star like any other."),
    "gui.forja.libro.evento_mejora": ("Deja %s: %s", "Leaves %s: %s"),
    "gui.forja.libro.evento.meteoritos": ("Estrellas fugaces y algún bólido cruzan el cielo. De vez en cuando uno cae cerca: el suelo marca dónde va a dar, abre un cráter de basalto y magma y deja hierro estelar dentro, un material ligerísimo que anula el daño de caída.",
                                           "Shooting stars and the odd fireball cross the sky. Now and then one comes down nearby: the ground marks where, it opens a crater of basalt and magma and leaves star iron inside, a feather-light material that takes the fall out of a fall."),
    "gui.forja.libro.evento.tormenta_arcana": ("Un círculo de runas gira despacio sobre tu cabeza y los relámpagos iluminan el suelo un instante antes del trueno. El cielo se carga y salta entre las cosas.",
                                                "A circle of runes turns slowly overhead and sheet lightning lights the ground a moment before the thunder. The sky charges up and jumps between things."),
    "gui.forja.libro.evento.niebla_de_almas": ("La niebla se cierra hasta unas decenas de bloques y dentro flotan luces pálidas, cada una rondando su trozo de suelo. No espera a la noche.",
                                                "The fog closes in to a few dozen blocks, with pale lights adrift in it, each keeping to its own patch of ground. It does not wait for night."),
    "gui.forja.libro.evento.luna_de_sangre": ("Sale una luna roja, llena y el doble de grande, y su luz tiñe la tierra. Todo lo de fuera se crece.",
                                               "A red moon rises, full and twice the size, and its light stains the land. Everything out there gets bolder."),
    "gui.forja.libro.evento.eclipse": ("Un disco tapa el sol y le deja sólo la corona: el mediodía se queda en penumbra y salen las estrellas. Las sombras se alargan.",
                                        "A disc crosses the sun and leaves it only its corona: noon goes to dusk and the stars come out. The shadows stretch."),
    "gui.forja.libro.evento.aurora": ("Cortinas de luz verde y violeta cuelgan sobre el norte y se mecen despacio. Una calma que para la magia.",
                                       "Curtains of green and violet light hang over the north and sway slowly. A calm that turns magic aside."),
    # These three had no entry at all: the book builds the key from the event's id, so the check that
    # looks for the keys the code names never saw them, and the page showed the raw key instead.
    "gui.forja.libro.evento.ventisca": ("La nieve viene de lado y no se ve a treinta bloques: se oye el viento antes de ver nada. El frío se mete en la placa.",
                                         "The snow comes sideways and you cannot see thirty blocks: you hear the wind before you see anything. The cold gets into the plate."),
    "gui.forja.libro.evento.marea_viva": ("La luna se acerca: enorme, pálida y azulada, con su luz derramándose hacia el agua, y la noche es más clara que ninguna. El agua tira de todo.",
                                           "The moon comes close: huge, pale and blue-white, its light spilling down towards the water, and the night is brighter than any other. The water pulls at everything."),
    "gui.forja.libro.evento.lluvia_de_pavesas": ("El horizonte arde todo alrededor, caen ascuas del cielo y de vez en cuando un trozo de fuego baja despacio. Todo lo que tenga lumbre atrae pavesas.",
                                                  "The horizon burns all the way round, embers fall out of the sky and now and then a piece of fire comes slowly down. Everything with a hearth draws wisps."),

    "gui.forja.libro.encargos_intro": ("El Forjador no quiere \"una espada\": quiere una pieza exacta, con el material de cada parte y una mejora ya al %s%% o más. Y paga como lo que cuesta: esmeraldas, un orbe bueno y una plantilla.",
                                        "The Forjador does not want \"a sword\": he wants one exact piece, with the material of every part and an upgrade already at %s%% or more. And he pays what that costs: emeralds, a strong orb and a template."),
    "gui.forja.libro.encargos_como": ("Agáchate y usa sobre él con la mano vacía para oír el encargo, y otra vez llevando la pieza para entregarla. Cambia cada día y cada Forjador pide lo suyo.",
                                       "Crouch and use on him with an empty hand to hear the order, and again holding the piece to hand it in. It changes with the day, and every Forjador asks for his own thing."),

    "gui.forja.libro.elites.titulo": ("Campeones", "Champions"),
    "gui.forja.libro.elites": ("%1$s de cada cien monstruos sale de campeón: con nombre, brillando a través de las paredes, x%2$s de vida y una leyenda en la mano. Mata a uno y suelta su leyenda, y nada más.",
                               "%1$s monsters in a hundred come up a champion: named, glowing through walls, x%2$s health and a legend in hand. Kill one and it gives up its legend, and nothing else."),
    "gui.forja.libro.insignias": ("Los que vienen más duros llevan su rango sobre la cabeza: un galón de bronce el veterano (sobrevivió a tres peleas, o ya nació curtido), dos de plata la élite, dos de oro bajo una estrella el campeón. Veteranos y élites son monstruos corrientes que vinieron más fuertes: atraviesan parte de tu armadura y sueltan una o dos cosas de más.",
                                  "The ones that come tougher wear their rank over their heads: one bronze chevron for a veteran (it survived three fights, or came seasoned), two silver for an elite, two gold under a star for a champion."),
    "gui.forja.libro.automata.titulo": ("Autómata de forja", "Forge automaton"),
    "gui.forja.libro.automata": ("Piedra, hierro y un horno encendido en la barriga, cuidando lo que quedó de los talleres viejos. %s de vida, no se le empuja y no le hacen nada el fuego ni el veneno. Cuando cae se deshace en las piezas de las que está hecho. Hay uno en cada forja abandonada y dos en la fragua del Nether.",
                                 "Stone, iron and a furnace still lit in its belly, minding what is left of the old workshops. %s health, nothing pushes it around and fire and poison do nothing to it. When it falls it comes apart into the parts it was made of. There is one in every abandoned forge and two in the forge in the Nether."),
    "gui.forja.libro.mundo.taller.titulo": ("Taller de montaña", "Mountain workshop"),
    "gui.forja.libro.mundo.taller": ("Arriba, en prados, laderas nevadas y colinas barridas por el viento, hay un taller de talabartero: un cobertizo con la mesa de talabartería, la mesa de piezas, un cofre y un corral con un caballo dentro. Es el sitio donde encontrarás esa mesa sin fabricarla.",
                                     "Up in meadows, snowy slopes and windswept hills there is a saddler's workshop: a shed with the saddlery table, a parts table, a chest and a pen with a horse in it. It is where you find that table without crafting it."),
    "gui.forja.libro.saqueadores.titulo": ("Saqueadores de forja", "Forge raiders"),
    "gui.forja.libro.saqueadores": ("Si llevas equipo forjado encima, de noche puede venir a buscarte una banda: %s saqueadores con equipo forjado y un capitán con una leyenda a maestría 10. Si cae el capitán, suelta su leyenda y varios orbes de mejora.",
                                     "If you are carrying forged gear, at night a band may come looking for you: %s raiders in forged gear and a captain with a legend at Maestria 10. If the captain falls, he leaves his legend and a handful of upgrade orbs."),
    "gui.forja.libro.herrero_caido.titulo": ("El Herrero Caído", "The Fallen Smith"),
    "gui.forja.libro.herrero_caido": ("Ya no se le despierta con una ofrenda. Su fragua apagada es el marco de un portal, en la Forja Profunda del Bastión: con una perla de oricalco en cada una de sus cuatro ménsulas lleva a su propio mundo, el Cementerio entre Estrellas, y allí el Herrero baja del cielo en cuanto llegas. La fragua de la vieja ruina del Nether se abre en un marco igual.",
                                       "He is no longer woken with an offering. His dead forge is the frame of a portal, in the Bastion's Deep Forge: with an orichalcum pearl on each of its four brackets it leads to his own world, the Graveyard Among the Stars, and there the Smith comes down from the sky as soon as you arrive. The forge in the old Nether ruin opens into the same kind of frame."),
    "gui.forja.libro.herrero_caido_fases": ("%s de vida en este nivel para uno solo, mangual de damasco y armadura de obsidiacero. A dos tercios y a un tercio clava el martillo, se vuelve intocable un momento y suelta una onda, y sus aprendices salen de la tierra en anillos a su alrededor. A la mitad se reforja: es inmortal mientras ardan sus brasas estelares (%s en este nivel, y una más por cada jugador de más), y solo las apaga la colada de un brasero volcado de un golpe o de un flechazo. Suelta su corazón de forja, el mejor material del mod, una leyenda y una Estrella forjada para cada uno.",
                                             "%s health on this level for one player, a damascus flail and obsidian steel plate. At two thirds and at one third he drives his hammer in, cannot be touched for a moment and sends out a wave, and his apprentices rise out of the ground in rings around him. At half he reforges: he is immortal while his star embers burn (%s on this level, and one more for every extra player), and only the pour of a brazier, knocked over with a blow or an arrow, puts them out. He leaves his forge heart, the best material in the mod, a legend and a Forged Star for everyone."),
    "gui.forja.libro.herrero_caido_defensa": ("No te lo va a matar otro: si algo más le pega (un warden, un gólem, otro monstruo) se da la vuelta y lo pelea con todo, sus aprendices se le echan encima, y lo que no venga de un jugador le hace un tercio. Tus lobos y tus flechas cuentan como tú. Si le pegas mientras pelea con otra cosa, vuelve a por ti.",
                                               "Nothing else is going to kill him for you: if something else hits him (a warden, a golem, another monster) he turns round and fights it with everything he has, his apprentices go for it, and whatever does not come from a player only does a third. Your wolves and your arrows count as you. Hit him while he is fighting something else and he comes back for you."),
    "gui.forja.libro.herrero_caido_reclama": ("La forja reclama: si en %s s le pegan tres criaturas que no son de nadie, o un gólem, un warden o algo igual de grande le intenta quitar %s de vida (contado ya a un tercio), abre los brazos, arrastra hacia sí todo lo que no es de nadie a %s bloques y lo deshace: no suelta nada ni da experiencia. A los jugadores, sus mascotas y sus aprendices no los toca. Como mucho una vez cada %s s.",
                                               "The forge reclaims: if three creatures that belong to nobody hit him within %s s, or a golem, a warden or something as big tries to take %s of his health (already counted at a third), he opens his arms, drags everything that belongs to nobody within %s blocks in to him and unmakes it: nothing drops and there is no experience. Players, their pets and his apprentices are left alone. At most once every %s s."),
    "gui.forja.libro.cap.mesas": ("Mesas", "Tables"),
    "gui.forja.libro.cap.objetos": ("Objetos", "Gear"),
    "gui.forja.libro.cap.piezas": ("Piezas", "Parts"),
    "gui.forja.libro.cap.materiales": ("Materiales", "Materials"),
    "gui.forja.libro.cap.rasgos": ("Rasgos", "Traits"),
    "gui.forja.libro.cap.mejoras": ("Mejoras", "Upgrades"),
    "gui.forja.libro.cap.estadisticas": ("Estadísticas", "Stats"),
    "gui.forja.libro.cap.mundo": ("Mundo", "World"),
    "gui.forja.libro.cap.cementerio": ("El Cementerio entre Estrellas", "The Graveyard Among the Stars"),
    "gui.forja.libro.cementerio.intro": ("El Herrero Caído ya no despierta en su fortaleza: se le busca en su propio mundo, una meseta sola en el vacío, bajo un cielo de constelaciones que dibujan moldes de armas. Se llega por el portal de la Forja Profunda del Bastión.",
                                         "The Fallen Smith no longer wakes in his fortress: he is sought in his own world, a plateau alone in the void, under a sky whose constellations draw weapon moulds. The way there is the portal in the Bastion's Deep Forge."),
    "gui.forja.libro.cementerio.oricalco.titulo": ("Oricalco", "Orichalcum"),
    "gui.forja.libro.cementerio.oricalco": ("Un lingote de cada metal del mod que se puede volver a hacer: hierro estelar, placa hueca, escoria, bronce, latón, peltre, electro, acero, cinerio, voltaico, acero estelar, obsidiacero, almacero y vidriacero. Dos van al crisol y el resto sale de las cubas de su línea, a calor de fundición. Salen cuatro lingotes.",
                                            "One ingot of every metal of the mod that can be made again: star iron, hollow plate, slag, bronze, brass, pewter, electrum, steel, cinereous, voltaic, star steel, obsidian steel, soul steel and glass steel. Two go in the crucible and the rest come from the tanks on its line, at molten heat. Four ingots come out."),
    "gui.forja.libro.cementerio.perla.titulo": ("Perla de oricalco", "Orichalcum Pearl"),
    "gui.forja.libro.cementerio.perla": ("Pon una perla de ender en una mesa de colada, como si fuera un molde, con oricalco en una cuba de su línea. La mesa le vierte %s lingotes encima y sale una perla de oricalco.",
                                         "Set an ender pearl on a casting table, as if it were a mould, with orichalcum in a tank on its line. The table pours %s ingots over it and out comes an orichalcum pearl."),
    "gui.forja.libro.cementerio.portal.titulo": ("El portal", "The portal"),
    "gui.forja.libro.cementerio.portal": ("En la Forja Profunda del Bastión, donde estaba la fragua apagada, hay un marco con cuatro ménsulas. Pon una perla de oricalco en cada una: con las cuatro se enciende, y se queda encendido para siempre. Una fragua apagada de un mundo viejo se abre en ese marco con un clic.",
                                          "In the Bastion's Deep Forge, where the cold forge stood, there is a frame with four brackets. Set an orichalcum pearl in each: with all four it lights, and it stays lit for good. A cold forge from an older world opens into that frame with a click."),
    "gui.forja.libro.cementerio.vuelta": ("Para volver, el pozo encendido detrás de la plataforma de llegada te deja junto al marco por el que entraste.",
                                          "To come back, the lit well behind the arrival platform leaves you beside the frame you came in by."),
    "gui.forja.libro.cap.sinergias": ("Sinergias", "Synergies"),
    "gui.forja.libro.sinergias_intro": ("Dos mejoras que se llevan bien hacen algo más juntas. Las dos tienen que estar al %s%% o más, y una pieza despierta %s como mucho: las más fuertes. La cuarta duerme hasta que supere a una de ellas.",
                                        "Two upgrades that go together do something more. Both have to be at %s%% or higher, and a piece wakes %s at most: the strongest. A fourth sleeps until it outgrows one of them."),
    "gui.forja.libro.sinergia_par": ("%s + %s", "%s + %s"),
    "tooltip.forja.sinergia": ("✦ %s: %s", "✦ %s: %s"),
    "tooltip.forja.sinergia_cerca": ("Cerca de %s (%s%% de %s%%)", "Close to %s (%s%% of %s%%)"),
    "tooltip.forja.sinergia_dormida": ("%s duerme: ya hay %s sinergias despiertas más fuertes", "%s sleeps: %s stronger synergies are already awake"),
    "synergy.forja.tormenta_helada": ("Tormenta helada", "Frozen Storm"),
    "synergy.forja.tormenta_helada.desc": ("el rayo congela en vez de incendiar: Lentitud II 4 s y escarcha",
                                           "the bolt freezes instead of burning: Slowness II for 4 s and frost"),
    "synergy.forja.sed_de_sangre": ("Sed de sangre", "Bloodthirst"),
    # "%%": a percent sign that ends the text is read as the start of a format with nothing after it.
    "synergy.forja.sed_de_sangre.desc": ("robas el doble de vida a enemigos por debajo del 30%%",
                                         "steal twice the health from enemies below 30%%"),
    "synergy.forja.filon": ("Filón", "Rich Seam"),
    "synergy.forja.filon.desc": ("las vetas siguen un 50% más de bloques", "veins follow 50% more blocks"),
    "synergy.forja.segador": ("Segador", "Reaper"),
    "synergy.forja.segador.desc": ("toda la cosecha va al inventario y siegas un anillo más", "every crop goes to your pockets and you reap one ring wider"),
    "synergy.forja.cazarrecompensas": ("Cazarrecompensas", "Bounty Hunter"),
    "synergy.forja.cazarrecompensas.desc": ("el doble de probabilidad de que caiga la cabeza", "twice the chance of a head dropping"),
    "synergy.forja.fortaleza": ("Fortaleza", "Fortress"),
    "synergy.forja.fortaleza.desc": ("+1 de armadura en esa pieza", "+1 armor on that piece"),
    "synergy.forja.cadena_de_rayos": ("Cadena de rayos", "Chain Lightning"),
    "synergy.forja.cadena_de_rayos.desc": ("el rayo salta a lo que esté a 3,5 bloques del objetivo", "the bolt jumps to whatever is within 3.5 blocks of the target"),
    "synergy.forja.vendaval": ("Vendaval", "Gale"),
    "synergy.forja.vendaval.desc": ("el golpe levanta en vertical al objetivo y a lo que lo rodea, sin empujarlos a los lados",
                                    "the blow lifts the target and everything around it straight up, with no sideways push"),
    "synergy.forja.banco_de_peces": ("Banco de peces", "Shoal"),
    "synergy.forja.banco_de_peces.desc": ("25% de sacar una segunda captura", "a 25% chance of a second catch"),
    "synergy.forja.meteoro": ("Meteoro", "Meteor"),
    "synergy.forja.meteoro.desc": ("agáchate en el aire con el mazo y caes en picado para soltar el golpe donde quieras",
                                   "sneak in mid-air with the mace and you plunge straight down to land the smash where you want"),
    "synergy.forja.justa": ("Justa", "Joust"),
    "synergy.forja.tormenta_de_flechas": ("Tormenta de flechas", "Arrow Storm"),
    "synergy.forja.tormenta_de_flechas.desc": ("la ballesta clava tres virotes que atraviesan", "the crossbow puts three piercing bolts through"),
    "synergy.forja.punta_maldita": ("Punta maldita", "Cursed Tip"),
    "synergy.forja.punta_maldita.desc": ("veneno y fuego juntos acaban en marchitamiento", "poison and fire together end in wither"),
    "synergy.forja.asta_perfecta": ("Asta perfecta", "Perfect Shaft"),
    "synergy.forja.asta_perfecta.desc": ("la flecha sale crítica", "the arrow leaves the string critical"),
    "synergy.forja.halcon": ("Halcón", "Falcon"),
    "synergy.forja.halcon.desc": ("el impulso te lanza y las alas conservan la velocidad", "the burst throws you and the wings keep the speed"),
    "synergy.forja.carga": ("Carga", "Charge"),
    "synergy.forja.carga.desc": ("la montura no se frena ni cede terreno", "the mount neither slows down nor gives ground"),
    "synergy.forja.cantera": ("Cantera", "Quarry"),
    "synergy.forja.cantera.desc": ("los bloques que se lleva el área no gastan la herramienta",
                                   "the blocks the area takes with it cost the tool nothing"),
    "synergy.forja.segundo_aliento": ("Segundo aliento", "Second Wind"),
    "synergy.forja.segundo_aliento.desc": ("por debajo del 30% de vida, dos corazones de absorción y un momento de regeneración",
                                           "below 30% health, two hearts of absorption and a moment of regeneration"),
    "synergy.forja.vigia": ("Vigía", "Watchman"),
    "synergy.forja.vigia.desc": ("el sónar llega la mitad más lejos y lo que marca brilla el doble de tiempo",
                                 "the sonar reaches half again as far and what it marks glows twice as long"),
    "synergy.forja.tempestad": ("Tempestad", "Tempest"),
    "synergy.forja.tempestad.desc": ("con tormenta, saltar con el tridente deja un rayo donde estabas",
                                      "in a storm, leaping with the trident leaves a bolt where you were"),
    "synergy.forja.siega_negra": ("Siega negra", "Black Harvest"),
    "synergy.forja.siega_negra.desc": ("la siega de la guadaña te cura medio corazón por enemigo arrastrado",
                                        "the scythe's reap heals you half a heart per enemy dragged in"),
    "synergy.forja.salamandra": ("Salamandra", "Salamander"),
    "synergy.forja.salamandra.desc": ("el fuego te devuelve entero lo que te quitaba, y te apaga",
                                       "fire gives back everything it took, and puts you out"),
    "synergy.forja.enjambre": ("Enjambre", "Swarm"),
    "synergy.forja.enjambre.desc": ("el abanico del báculo pasa a cinco proyectiles que se reparten el hechizo, y todos persiguen",
                                     "the staff's fan is five bolts wide, sharing the spell out, and every one of them hunts"),
    "synergy.forja.colapso": ("Colapso", "Collapse"),
    "synergy.forja.colapso.desc": ("al apagarse, la runa estalla con el 75%% del daño con que se abrió",
                                    "as it goes out, the rune bursts for 75%% of the damage it opened with"),
    "synergy.forja.firme": ("Firme", "Steadfast"),
    "synergy.forja.firme.desc": ("las botas aguantan mucho más y el lamento de la coraza no te toca",
                                  "the boots hold far harder and the hollow plate's wail does not stick"),
    "synergy.forja.martillo_pilon": ("Martillo pilón", "Pile Driver"),
    "synergy.forja.martillo_pilon.desc": ("el mangual aturde a todo el corro, no solo a quien golpea",
                                          "the flail stuns the whole ring, not just what it hits"),
    "synergy.forja.cien_manos": ("Cien manos", "Hundred Hands"),
    "synergy.forja.cien_manos.desc": ("el doble de ráfaga y el frenesí sube al doble de rápido",
                                      "twice the flurry, and the frenzy climbs twice as fast"),
    "synergy.forja.justa.desc": ("la lanza atraviesa y hiere a lo que esté detrás del objetivo",
                                 "the spear runs through and wounds whatever stands behind the target"),
    "synergy.forja.muro": ("Muro", "Bulwark"),
    "synergy.forja.muro.desc": ("al cubrir, las púas hacen el doble y el empujón es más fuerte",
                                "blocking doubles the spikes and shoves harder"),
    "gui.forja.libro.especial.lanza.titulo": ("Lanza", "Spear"),
    "gui.forja.libro.especial.lanza": ("Golpe corto rápido, y al mantener clic derecho una embestida cargada. La punta decide el daño, la fuerza de la carga y lo rápido que se prepara. Alcance le suma distancia de verdad.",
                                       "A quick jab, and holding right-click charges a lunge. The tip sets the damage, how hard the charge hits and how fast it readies. Reach really does lengthen it."),
    "gui.forja.libro.especial.cincel.titulo": ("Cincel", "Chisel"),
    "gui.forja.libro.especial.cincel": (
        "Hoja y mango, y no sirve para picar: mina despacio y pega menos que un palo. Lo suyo es la "
        "piedra ya colocada: clic derecho sobre un bloque de una familia que conozca (piedra, adoquín, "
        "pizarra, arenisca, cuarzo, piedra negra, prismarina, purpur, ladrillo del Nether, piedra del "
        "End, toba, basalto) y pasa a la siguiente cara de esa familia; agachado va al revés. No gasta "
        "nada más que un punto de durabilidad.",
        "Blade and handle, and it is no good for digging: it mines slowly and hits softer than a stick. "
        "What it is for is stone already in place: right-click a block of a family it knows (stone, "
        "cobble, deepslate, sandstone, quartz, blackstone, prismarine, purpur, nether brick, end stone, "
        "tuff, basalt) and it steps to the next face of that family; crouch to step back. It costs "
        "nothing but a point of durability.",
    ),
    "gui.forja.libro.especial.tridente.titulo": ("Tridente", "Trident"),
    "gui.forja.libro.especial.tridente": (
        "Tres puntas sobre un asta: llega más lejos que una espada, golpea algo más flojo y se lanza "
        "entero agachándote, de punta, y siempre vuelve a tu mano. Sus dos mejoras solo sirven donde hay agua o tormenta: "
        "Corriente te lanza al usarlo dentro del agua o bajo la lluvia, y Canalización llama al rayo "
        "sobre lo que golpeas si la tormenta te ve. La punta cuesta cuatro de material, más que ninguna "
        "otra cabeza.",
        "Three prongs on a shaft: it reaches further than a sword, hits a little softer, and is thrown "
        "whole by crouching, point first, and always comes back to your hand. Its two upgrades only work where there is water or a "
        "storm: Corriente throws you when you use it in water or rain, and Canalizacion calls the "
        "lightning down on what you hit if the storm can see you. Its head costs four of a material, "
        "more than any other.",
    ),
    "gui.forja.libro.especial.mazo.titulo": ("Mazo", "Mace"),
    "gui.forja.libro.especial.mazo": ("Como la maza: cae y el golpe suma el daño de la caída. La cabeza multiplica ese golpe (hierro x1, netherita x1.2) y Densidad o Estallido de viento lo llevan más lejos. Agachado y con clic derecho en el suelo: Sismo, un golpe que lanza por los aires a todo lo que esté a 4 bloques (8 s de espera).",
                                      "Like the mace: fall on something and the hit adds your fall damage. The head multiplies that smash (iron x1, netherite x1.2); Density and Wind Burst push it further. Sneak and right-click on the ground for a Quake that throws everything within 4 blocks into the air (8 s cooldown)."),
    "gui.forja.libro.especial.espadon.titulo": ("Espadón y martillo", "Greatsword and hammer"),
    "gui.forja.libro.especial.espadon": ("Agachado y con clic derecho, el espadón da un Torbellino que corta todo a 3,5 bloques (6 s de espera). El martillo, con los pies en el suelo, hace un Sismo como el del mazo.",
                                         "Sneak and right-click for the greatsword's Whirl, cutting everything within 3.5 blocks (6 s cooldown). The hammer, feet on the ground, makes the same Quake as the mace."),
    "gui.forja.libro.especial.guadana.titulo": ("Guadaña", "Scythe"),
    "gui.forja.libro.especial.guadana": ("Lenta y con más alcance, barre como una espada. Con clic derecho sobre un cultivo maduro cosecha y replanta 3x3, o más con Cosechador. Agachado y clic derecho hace la Siega: arrastra hacia ti todo lo que haya a seis bloques.",
                                         "Slow and long, it sweeps like a sword. Right-click a ripe crop and it reaps and replants 3x3, wider with Harvester."),
    "gui.forja.libro.especial.arcos.titulo": ("Arco y ballesta", "Bow and crossbow"),
    "gui.forja.libro.especial.arcos": ("Los brazos deciden el daño extra de la flecha; en el arco también lo rápido que se tensa. La ballesta carga como la de siempre (Carga rápida la acelera), dispara cohetes y se sostiene apuntando cuando está cargada.",
                                       "The limbs set the arrow's extra damage; on a bow they also set the draw speed. The crossbow charges as usual (Quick Charge speeds it up), shoots rockets and is held up when loaded."),
    "gui.forja.libro.especial.cana.titulo": ("Caña", "Fishing rod"),
    "gui.forja.libro.especial.cana": ("Pesca como la de siempre, pero el mango y la cuerda deciden cuánto aguanta, y gana maestría con cada captura. Acepta Cebo y Suerte del mar, y su don es Pescador.",
                                      "It fishes as usual, but the shaft and the line set how long it lasts, and it earns Maestria with every catch. It takes Lure and Luck of the Sea, and its gift is Angler."),
    "gui.forja.libro.especial.gancho.titulo": ("Gancho", "Grappling Hook"),
    "gui.forja.libro.especial.gancho": ("Garfio, cuerda y mango. Clic derecho y el garfio sale volando: si muerde un bloque, la cuerda te lleva hasta él; si engancha a algo vivo, se lo trae a él. La cuerda decide el alcance (20 bloques con cuero) y el garfio la fuerza del tirón. Sirga (cadenas) tira un 60%% más fuerte. Cae bien cuando te tiras con las alas.",
                                        "Claw, rope and handle. Right-click and the claw flies: bite a block and the rope hauls you to it; catch something alive and it comes to you instead. The rope sets the reach (20 blocks with leather) and the claw the strength of the pull. A winch (chains) pulls 60%% harder. It pairs well with a jump off a cliff in wings."),
    "gui.forja.libro.especial.montura.titulo": ("Barda y armadura de lobo", "Barding and Wolf Armor"),
    "gui.forja.libro.especial.montura": ("Placa y forro, pero solo en la mesa de talabartería (cuero, silla y madera). La barda la llevan caballos, burros y mulas; la armadura de lobo, los lobos. La placa da la armadura, el forro la durabilidad, y el color es el del material como en tu propia armadura. La mesa de forja no las hace, y la talabartería no hace nada más.",
                                         "Plate and lining, but only at the saddlery table (leather, a saddle and wood). Barding goes on horses, donkeys and mules; wolf armor on wolves. The plate gives the armor, the lining the durability, and the colour is the material, the same as your own armor. The forge table will not make them, and the saddlery makes nothing else."),
    "gui.forja.libro.especial.flecha.titulo": ("Flechas", "Arrows"),
    "gui.forja.libro.especial.flecha": ("Punta y emplumado, y salen a puñados. La punta decide el daño y el emplumado la velocidad con que sale de la cuerda. Aceptan cinco mejoras propias: Punta afilada, Asta ligera, Punta envenenada, Punta ígnea y Punta perforante. Las mejoras van a toda la pila.",
                                        "A tip and a fletching, and they come out by the handful. The tip decides the damage and the fletching how fast it leaves the string. They take five upgrades of their own: Sharpened Tip, Light Shaft, Poisoned Tip, Fiery Tip and Piercing Tip. An upgrade goes to the whole stack."),
    "gui.forja.libro.especial.mangual.titulo": ("Mangual", "Flail"),
    "gui.forja.libro.especial.mangual": ("Bola, cadena y mango. Lento y pesado: reparte el 30%% del golpe a todo lo que rodea al objetivo, lo deja aturdido y pasa por encima de los escudos (el 70%% del daño entra igual). Aturdimiento alarga el aturdido, Segunda cabeza sube el reparto, y juntas hacen Martillo pilón: aturde a todo el corro.",
                                         "Ball, chain and handle. Slow and heavy: it spreads 30%% of the blow to everything around the target, leaves it reeling, and swings past shields (70%% of the damage lands anyway). Stun makes the daze longer, Second Head raises the spread, and together they make Pile Driver: the whole ring is left reeling."),
    "gui.forja.libro.especial.guanteletes.titulo": ("Guanteletes", "Gauntlets"),
    "gui.forja.libro.especial.guanteletes": ("Manopla, nudillos y remaches. Pegan poco pero rapidísimo, ganan maestría al doble y viven del frenesí: Nudillos de hierro suma daño según lo alto que esté la barra y Ráfaga hace que el puño golpee dos veces. Cien manos duplica la ráfaga y el frenesí sube al doble. Agachado y clic derecho te lanzas de cabeza: lo primero que encuentres se lleva el puñetazo entero y sale por los aires.",
                                             "The mitt, the knuckle band and its rivets. Little damage per punch but the fastest hands in the mod, they earn Maestria twice as fast and live off the frenzy: Iron Knuckles adds damage as the bar climbs and Flurry makes a punch land twice. Hundred Hands doubles the flurry and the frenzy climbs twice as fast. Crouch and right-click to throw yourself forward: the first thing in the way takes the whole punch and is knocked off its feet."),
    "gui.forja.libro.especial.alas.titulo": ("Alas", "Wings"),
    "gui.forja.libro.especial.alas": ("Membrana y forro: se llevan en el pecho y planean como un elytra. La membrana decide cuánto vuelas: los materiales ligeros (oro, amatista, cuero, hueso) bajan tu gravedad y el roce del aire, y los pesados (obsidiana, piedra) te hunden aunque duren mucho más. Ganan maestría volando y aceptan Aerodinámica, que convierte tu bono de velocidad en empuje al planear.",
                                      "Membrane and lining: worn on the chest, they glide like an elytra. The membrane sets how far you fly: light materials (gold, amethyst, leather, bone) cut your gravity and air drag, heavy ones (obsidian, stone) sink you although they last far longer. They earn Maestria in the air and take Aerodynamics, which turns your speed bonus into push while gliding."),
    "gui.forja.libro.especial.escudo.titulo": ("Escudo", "Shield"),
    "gui.forja.libro.especial.escudo": ("La placa decide la durabilidad y lo que tarda en cubrir; el borde, cuánto lo bloquea un hacha. Si cubres justo cuando llega el golpe haces una parada perfecta: devuelves el daño, empujas y dejas al enemigo lento y débil. Agachado y con clic derecho, si tienes algo delante, das un golpe de escudo (3 s de espera).",
                                        "The plate sets durability and how fast it comes up; the rim sets how long an axe disables it. Block right as the blow lands for a perfect parry: the damage goes back, the attacker is thrown off, slowed and weakened. Sneak and right-click with something in front for a shield bash (3 s cooldown)."),
    "gui.forja.libro.especial.baculo.titulo": ("Báculo", "Staff"),
    "gui.forja.libro.especial.baculo": ("Núcleo, engaste y asta; se forja en la mesa mayor. Con clic derecho lanza un proyectil recto del color de su núcleo, y el material del núcleo decide cuánto daña. Todo lo que mejora el golpe de un arma viaja en el proyectil: Filo lo afila, Escarcha enfría lo que toca, Vampirismo te cura. Cada proyectil gasta maná y la espera entre dos es muy corta (capítulo «Maná y estamina»). Gana maestría con cada impacto.",
                                        "A core, its setting and a shaft, forged at the greater table. Right-click throws a straight bolt in the colour of its core, and the core's material decides how hard it lands. Everything that improves a weapon's blow rides on the bolt: Sharpness hones it, Frost chills what it touches, Vampirism heals you. Every bolt spends mana and the wait between two is very short (chapter 'Mana and stamina'). It earns Maestria with every hit."),
    "gui.forja.libro.especial.grimorio.titulo": ("Grimorio", "Tome"),
    "gui.forja.libro.especial.grimorio": ("Núcleo, tapas y remaches; se forja en la mesa mayor. Con clic derecho abre un área de tres bloques de radio a cinco bloques delante de ti, del color del círculo de su tapa, que es el de su núcleo. Al abrirse golpea como un arma, con todas sus mejoras, y deja una runa seis segundos: muerde dos veces por segundo a lo que la pise y lo ralentiza, y brilla cada vez que muerde. Los mordiscos de la runa no llevan las mejoras. Tiene las suyas: Tinta indeleble, Vórtice y Santuario; el báculo, Prisma y Buscador; y los dos, Conjuro veloz, Sobrecarga y Resonancia.",
                                          "A core, covers and rivets, forged at the greater table. Right-click opens an area three blocks in radius five blocks in front of you, in the colour of the circle on its cover, which is its core's. As it opens it lands as a weapon's blow, with every upgrade on it, and leaves a rune for six seconds: it bites twice a second at whatever stands on it and slows it, and flares each time it bites. The rune's bites do not carry the upgrades. It has its own: Indelible Ink, Vortex and Sanctuary; the staff has Prism and Seeker; and both take Quick Casting, Overcharge and Resonance."),
    "gui.forja.libro.mundo.orbes.titulo": ("Orbes de mejora", "Upgrade orbs"),
    "gui.forja.libro.mundo.orbes": ("Al desarmar, cada mejora sale en un orbe con la mitad de su porcentaje. En la estrella, un orbe junto a un objeto le suma su mejora; dos o más orbes iguales sin objeto al centro se fusionan.",
                                   "Salvaging turns each upgrade into an orb with half its percentage. On the star, an orb next to gear adds its upgrade; two or more matching orbs with nothing in the center fuse."),
    "gui.forja.libro.mundo.rotos.titulo": ("Objetos rotos", "Broken gear"),
    "gui.forja.libro.mundo.rotos": ("Lo forjado no desaparece al gastarse: queda roto, con grietas en el ícono. Roto no daña, no protege, no mina bien ni usa sus mejoras hasta repararlo en la estrella, con un kit de reparación, con Autorreparación o con Llanto.",
                                   "Forged gear never vanishes: it breaks and shows cracks. Broken, it doesn't hurt, protect, mine well or use its upgrades until repaired on the star, with a repair kit, by Self-Repair or by Weeping."),
    "gui.forja.libro.mundo.botin.titulo": ("Botín", "Loot"),
    "gui.forja.libro.mundo.botin": ("En llanuras, bosques y otros biomas de aldea hay forjas abandonadas con las dos mesas, un cofre de plantillas grabadas, equipo y orbes, y un Herrero ermitaño con quien comerciar. Los cofres de herreros guardan plantillas y equipo forjado; mazmorras, minas, fortalezas, templos y portales en ruinas esconden plantillas grabadas y equipo gastado con mejoras; los bastiones, armas de netherita.",
                                    "Plains, forests and other village biomes hide abandoned forges with both tables, a chest of engraved templates, gear and orbs, and a Hermit Smith to trade with. Smith chests hold templates and forged gear; dungeons, mineshafts, strongholds, temples and ruined portals hide engraved templates and worn gear with upgrades; bastions, netherite weapons."),
    "gui.forja.libro.mundo.monstruos.titulo": ("Monstruos", "Monsters"),
    "gui.forja.libro.mundo.monstruos": ("Zombis, esqueletos y vindicadores a veces llevan armadura o armas forjadas en lugar de las normales, y las usan: los esqueletos con arco forjado y los saqueadores con ballesta forjada disparan de verdad, y algunos esqueletos llevan báculo y algunos zombis grimorio. El enderman esquiva el 34 % de los golpes teletransportándose; tras una esquiva así pasa 7 segundos sin poder repetirla.",
                                        "Zombies, skeletons and vindicators sometimes wear forged armor or carry forged weapons instead of plain ones, and use them: skeletons with a forged bow and pillagers with a forged crossbow really shoot, and some skeletons carry a staff and some zombies a tome. An enderman dodges 34% of blows by teleporting away; after such a dodge it cannot do it again for 7 seconds."),
    "gui.forja.libro.mundo.aldeanos.titulo": ("Aldeanos", "Villagers"),
    "gui.forja.libro.mundo.aldeanos": ("Los herreros venden plantillas base, plantillas grabadas de su oficio y, en nivel 4, orbes de Eficiencia, Filo o Protección. El herrero de herramientas maestro vende un mapa hacia una forja abandonada. Un aldeano que toma una Mesa de piezas se vuelve Forjador: plantillas, kits de reparación, orbes y el mapa de forja.",
                                       "Smiths sell blank templates, engraved templates of their trade and, at level 4, Efficiency, Sharpness or Protection orbs. A master toolsmith sells a map to an abandoned forge. A villager who claims a Parts Table becomes a Forgesmith: templates, repair kits, orbs and the forge map."),
    "gui.forja.libro.mundo.adornos.titulo": ("Adornos", "Trims"),
    "gui.forja.libro.mundo.adornos": ("La armadura forjada acepta los adornos de la mesa de herrería. El escudo da un golpe de escudo si te agachas y haces clic derecho. La piedra de afilar no acepta objetos forjados.",
                                      "Forged armor takes smithing table trims. A shield bashes when you sneak and right-click. The grindstone refuses forged gear."),
    "gui.forja.libro.mesa_piezas": ("Mesa de piezas: pon una plantilla grabada y el material, y saca la pieza. En Desarmar recuperas las piezas de un objeto (si la cabeza o la placa está muy gastada, se pierde) y sus mejoras en orbes con la mitad del porcentaje.",
                                    "Parts Table: put in an engraved template and material, and take the part. Salvage gives back a gear's parts (a badly worn head or plate is lost) and its upgrades as orbs with half the percentage."),
    "gui.forja.libro.mesa_forja": ("Mesa de forja: una estrella de cinco puntas. Piezas en las puntas y Forjar: la herramienta sale al centro. Con una herramienta al centro, ingredientes en las puntas la mejoran, piezas le cambian esas partes, su material la repara y los libros encantados y orbes de mejora se vuelven mejoras. Lo forjado no desaparece al gastarse: queda roto y sin efectos hasta repararlo.",
                                   "Forge Table: a five-pointed star. Parts on the points and Forge: the gear appears in the center. With gear in the center, ingredients on the points upgrade it, parts swap those parts, its material repairs it and enchanted books and upgrade orbs turn into upgrades. Forged gear never vanishes when worn out: it stays broken and powerless until repaired."),
    "gui.forja.libro.receta_libro": ("El Cuaderno del aprendiz, por si lo pierdes: un libro y un lingote de hierro.",
                                     "The Apprentice's Notebook, in case you lose it: a book and an iron ingot."),
    "gui.forja.libro.objetos_intro": ("Las piezas que pongas deciden qué objeto sale. Pasa el ratón encima para ver sus estadísticas.",
                                      "The parts you put in decide what comes out. Hover to see the stats."),
    "gui.forja.libro.piezas_intro": ("Cada pieza cuesta material. La cabeza, hoja o placa decide lo principal; mango, atadura y forro suman.",
                                     "Every part costs material. The head, blade or plate decides the most; handle, binding and lining add to it."),
    "gui.forja.libro.pieza_linea": ("Cuesta %s · %s", "Costs %s · %s"),
    "gui.forja.libro.materiales_intro": ("Pasa el ratón sobre un material para ver lo que da como cabeza, mango y placa.",
                                         "Hover a material to see what it gives as a head, a handle and a plate."),
    "gui.forja.libro.rasgos_intro": ("Algunos materiales tienen un rasgo: un efecto extra si cualquier pieza del objeto está hecha de ellos.",
                                     "Some materials carry a trait: a bonus effect when any part of the gear is made of them."),
    "gui.forja.libro.mejoras_intro": ("Pon el objeto al centro de la estrella y los ingredientes en las puntas. Cada ingrediente suma el porcentaje indicado; las que llevan + necesitan esos objetos juntos. Las de un mismo grupo no se juntan.",
                                      "Put the gear in the center of the star and the ingredients on the points. Each ingredient adds the percentage shown; entries with + need those items together. Upgrades of the same group do not mix."),
    "gui.forja.libro.seccion.herramientas": ("Herramientas", "Tools"),
    "gui.forja.libro.seccion.herramientas_y_armas": ("Herramientas y armas", "Tools and weapons"),
    "gui.forja.libro.seccion.armas": ("Armas", "Weapons"),
    "gui.forja.libro.seccion.lanza_mazo": ("Lanza y mazo", "Spear and mace"),
    "gui.forja.libro.seccion.magia": ("Báculo y grimorio", "Staff and tome"),
    "gui.forja.libro.seccion.arcos": ("Arcos y ballestas", "Bows and crossbows"),
    "gui.forja.libro.seccion.pesca": ("Cañas", "Fishing rods"),
    "gui.forja.libro.seccion.flechas": ("Flechas", "Arrows"),
    "gui.forja.libro.seccion.alas": ("Alas", "Wings"),
    "gui.forja.libro.seccion.monturas": ("Monturas", "Mounts"),
    "gui.forja.libro.seccion.escudos": ("Escudos", "Shields"),
    "gui.forja.libro.seccion.armadura": ("Armadura", "Armor"),
    "gui.forja.libro.seccion.todo": ("Para todo", "Any gear"),
    "gui.forja.libro.estadisticas_intro": ("Cada número se compara con todas las combinaciones de materiales posibles de ese objeto o pieza:",
                                           "Every number is compared with all the material combinations that item or part can have:"),
    "gui.forja.libro.estadisticas_invertidas": ("Donde menos es mejor (tiempo en cubrirse, bloqueo por hacha, preparar la carga) la escala se invierte.",
                                                "Where lower is better (raise time, axe disable, charge wind-up) the scale flips."),
    "gui.forja.libro.estadisticas_donde": ("Los colores salen en los tooltips de objetos y piezas, en la Mesa de forja y en los materiales de esta guía.",
                                           "The colors show in gear and part tooltips, at the Forge Table and in this guide's materials."),
    "gui.forja.libro.peor": ("peor", "worst"),
    "gui.forja.libro.promedio": ("promedio", "average"),
    "gui.forja.libro.mejor": ("mejor", "best"),
    "itemGroup.forja": ("Forja", "Forja"),
    "itemGroup.forja.piezas": ("Forja: piezas", "Forja: parts"),
    "entity.forja.cabeza_lanzada": ("Cabeza lanzada", "Thrown Head"),
    "entity.forja.onda_expansiva": ("Onda expansiva", "Shockwave"),
    "gui.forja.pestana.piezas": ("Piezas", "Parts"),
    "gui.forja.pestana.desarmar": ("Desarmar", "Salvage"),
    "commands.forja.kit": ("Forja: recibiste las mesas, plantillas, la guía y el kit de prueba", "Forja: you got the tables, templates, the guide and the test kit"),
    "gui.forja.desarmar.ayuda.1": ("Pon un objeto forjado para", "Put forged gear here for its"),
    "gui.forja.desarmar.ayuda.2": ("recuperar sus piezas, o una", "parts, or a loose part to"),
    "gui.forja.desarmar.ayuda.3": ("pieza para reciclarla.", "recycle it."),
    "gui.forja.desarmar.boton": ("Desarmar", "Salvage"),
    "gui.forja.desarmar.desgastada": ("Cabeza o placa muy gastada: se pierde", "Worn head or plate: it is lost"),
    "gui.forja.stat.tensado": ("Tensado: %s", "Draw speed: %s"),
    "gui.forja.stat.peso": ("Peso: %s kg", "Weight: %s kg"),
    "gui.forja.stat.flecha": ("Daño flecha: %s", "Arrow damage: %s"),
    "gui.forja.stat.planeo": ("Planeo: %s", "Glide: %s"),
    "gui.forja.stat.vuelo": ("Vuelo: %s s", "Flight: %s s"),
    "gui.forja.stat.bloqueo": ("Ventana de parada: %s s", "Parry window: %s s"),
    "effect.forja.sangrado": ("Sangrado", "Bleeding"),
    "gui.forja.frenesi": ("Frenesí %s/%s", "Frenzy %s/%s"),
    "gui.forja.temple": ("Temple de %s", "Quenched in %s"),
    "gui.forja.herrero": ("Maestría de herrero %s (%s de experiencia para el siguiente)", "Smith Maestria %s (%s experience to the next)"),
    # The techniques tab has 194 pixels for this and the long form is 260: it ran out of both sides
    # of the panel. The track under it says how far along you are; this only has to say the numbers.
    "gui.forja.herrero.corto": ("Maestría de herrero %s · faltan %s de experiencia", "Smith mastery %s · %s experience to go"),
    "gui.forja.herrero.maximo": ("Maestría de herrero %s: no hay más que aprender", "Smith Maestria %s: there is nothing left to learn"),
    "gui.forja.herrero.sube": ("Maestría de herrero %s", "Smith Maestria %s"),
    "gui.forja.herrero.tecnica": ("Tienes una técnica que elegir en la mesa de forja.", "You have a technique to choose at the forge table."),
    "tooltip.forja.oxido": ("Pátina %s/%s (panal para fijarla)", "Patina %s/%s (honeycomb to seal it)"),
    "tooltip.forja.oxido.encerado": ("Pátina %s/%s, encerada", "Patina %s/%s, waxed"),
    "tooltip.forja.perfecta": ("Forja perfecta: +5%% en todo", "Perfect forge: +5%% on everything"),
    "tooltip.forja.caliente": ("Caliente: apágala en agua, lava, nieve polvo o miel", "Hot: put it out in water, lava, powder snow or honey"),
    "tooltip.forja.temple": ("Temple de %s: %s", "Quenched in %s: %s"),
    "tooltip.forja.firma": ("Forjado por %s", "Forged by %s"),
    "tooltip.forja.firma.propia": ("Forjado por ti: +2%% de daño y de minado", "Your own work: +2%% damage and mining"),
    "tooltip.forja.historia.bajas": ("Bajas: %s", "Kills: %s"),
    "tooltip.forja.historia.bloques": ("Bloques: %s", "Blocks: %s"),
    "tooltip.forja.historia.vuelo": ("Segundos en el aire: %s", "Seconds in the air: %s"),
    "tooltip.forja.historia.peces": ("Capturas: %s", "Catches: %s"),
    "item.forja.bronce": ("Bronce", "Bronze"),
    "item.forja.laton": ("Latón", "Brass"),
    "item.forja.peltre": ("Peltre", "Pewter"),
    "item.forja.acero": ("Acero", "Steel"),
    "item.forja.electro": ("Electro", "Electrum"),
    "item.forja.damasco": ("Damasco", "Damascus"),
    "item.forja.acero_estelar": ("Acero estelar", "Star Steel"),
    "item.forja.obsidiacero": ("Obsidiacero", "Obsidian Steel"),
    "material.forja.escoria": ("escoria", "slag"),
    "material.forja.bronce": ("bronce", "bronze"),
    "material.forja.laton": ("latón", "brass"),
    "material.forja.peltre": ("peltre", "pewter"),
    "material.forja.acero": ("acero", "steel"),
    "material.forja.electro": ("electro", "electrum"),
    "material.forja.damasco": ("damasco", "damascus"),
    "material.forja.acero_estelar": ("acero estelar", "star steel"),
    "material.forja.obsidiacero": ("obsidiacero", "obsidian steel"),
    "conjunto.forja.bronce": ("+1 de armadura y +1 corazón", "+1 armor and +1 heart"),
    "conjunto.forja.laton": ("+12% de velocidad de ataque", "+12% attack speed"),
    "conjunto.forja.peltre": ("4 bloques más de caída segura y algo más de velocidad", "4 more blocks of safe fall and a little more speed"),
    "conjunto.forja.acero": ("+3 de dureza de armadura", "+3 armor toughness"),
    "conjunto.forja.electro": ("+4 de suerte y +10% de minado", "+4 luck and +10% mining"),
    "conjunto.forja.damasco": ("+2 de daño y más barrido", "+2 damage and a wider sweep"),
    "conjunto.forja.acero_estelar": ("Menos gravedad, 6 bloques de caída segura y +1 de armadura", "Less gravity, 6 blocks of safe fall and +1 armor"),
    "conjunto.forja.obsidiacero": ("Aguantas empujones y explosiones", "You shrug off knockback and blasts"),
    "item.forja.cinerio": ("Cinerio", "Cinereous Steel"),
    "item.forja.voltaico": ("Voltaico", "Voltaic Brass"),
    "item.forja.almacero": ("Almacero", "Soul Steel"),
    "item.forja.vidriacero": ("Vidriacero", "Glass Steel"),
    "item.forja.solacero": ("Solacero", "Sun Steel"),
    "item.forja.lunacero": ("Lunacero", "Moon Steel"),
    "item.forja.acero_vivo": ("Acero vivo", "Living Steel"),
    "material.forja.cinerio": ("cinerio", "cinereous steel"),
    "material.forja.voltaico": ("voltaico", "voltaic brass"),
    "material.forja.almacero": ("almacero", "soul steel"),
    "material.forja.vidriacero": ("vidriacero", "glass steel"),
    "material.forja.solacero": ("solacero", "sun steel"),
    "material.forja.lunacero": ("lunacero", "moon steel"),
    "material.forja.acero_vivo": ("acero vivo", "living steel"),
    "tag.item.forja.cinerio": ("Cinerio", "Cinereous steel"),
    "tag.item.forja.voltaico": ("Voltaico", "Voltaic brass"),
    "tag.item.forja.almacero": ("Almacero", "Soul steel"),
    "tag.item.forja.vidriacero": ("Vidriacero", "Glass steel"),
    "tag.item.forja.solacero": ("Solacero", "Sun steel"),
    "tag.item.forja.lunacero": ("Lunacero", "Moon steel"),
    "tag.item.forja.acero_vivo": ("Acero vivo", "Living steel"),
    "conjunto.forja.cinerio": ("El fuego se te apaga solo y +1 de armadura", "Fire goes out on you at once, and +1 armor"),
    "conjunto.forja.voltaico": ("+15% de velocidad de ataque y +5% de velocidad", "+15% attack speed and +5% movement speed"),
    "conjunto.forja.almacero": ("La parada de Animado baja a 20 s, +2 de dureza y +1 corazón", "The Animate guard drops to 20 s, +2 toughness and +1 heart"),
    "conjunto.forja.vidriacero": ("+10% de velocidad y 4 bloques más de caída segura", "+10% speed and 4 more blocks of safe fall"),
    "conjunto.forja.solacero": ("+2 de daño y +2 de armadura", "+2 damage and +2 armor"),
    "conjunto.forja.lunacero": ("+40% agachado y +2 de daño", "+40% crouched and +2 damage"),
    "conjunto.forja.acero_vivo": ("+4 corazones, +3 de dureza y cada muerte cura el doble", "+4 hearts, +3 toughness and every kill heals twice as much"),
    "calor.forja.fria": ("fría", "cold"),
    "calor.forja.templada": ("templada", "warm"),
    "calor.forja.caliente": ("caliente", "hot"),
    "calor.forja.fundida": ("fundida", "molten"),
    "calor.forja.forja_blanca": ("forja blanca", "white heat"),
    "gui.forja.boton.fundir": ("Fundir", "Melt"),
    "gui.forja.calor": ("Mesa %s", "The table is %s"),
    "gui.forja.calor.crisol": ("Sólo en el crisol de obsidiana", "Only in the obsidian crucible"),
    "filled_map.forja.taller_de_montana": ("Mapa del taller de montaña", "Mountain Workshop Map"),
    "filled_map.forja.campamento": ("Mapa de campamento saqueador", "Raider Camp Map"),
    "filled_map.forja.bastion": ("Mapa del Bastión del Gremio", "Guild Bastion Map"),
    "tecnica.forja.pulso_firme": ('Pulso firme', 'Steady Hand'),
    "tecnica.forja.pulso_firme.desc": ('La ventana del martillo es más ancha, así que una pieza perfecta deja de ser cuestión de suerte.', 'The hammer window is wider, so a perfect piece stops being a matter of luck.'),
    "tecnica.forja.ahorro_de_metal": ('Ahorro de metal', 'Thrift'),
    "tecnica.forja.ahorro_de_metal.desc": ('Cada lingote repara un tercio más, así que mantener una pieza viva cuesta menos.', 'Every ingot mends a third more, so keeping a piece alive costs less.'),
    "tecnica.forja.ojo_para_el_metal": ('Ojo para el metal', 'Eye for Metal'),
    "tecnica.forja.ojo_para_el_metal.desc": ('Cada aleación sale de la estrella con un lingote de más.', 'Every alloy leaves the star with one more ingot than it should.'),
    "tecnica.forja.segunda_templada": ('Segunda templada', 'Second Quench'),
    "tecnica.forja.segunda_templada.desc": ('Una pieza sola en la estrella, sobre fuego caliente, vuelve a estar al rojo y admite otro temple.', 'A piece alone on the star, over real heat, goes red again and takes another quench.'),
    "tecnica.forja.mano_de_orfebre": ('Mano de orfebre', 'Goldsmith Hands'),
    "tecnica.forja.mano_de_orfebre.desc": ('Orbes y libros dan cinco puntos más de su mejora de los que llevan.', 'Orbs and books give five more points of their upgrade than they hold.'),
    "tecnica.forja.fuelle_largo": ('Fuelle largo', 'Long Bellows'),
    "tecnica.forja.fuelle_largo.desc": ('Lees el calor tan bien que fundes aleaciones un paso más frías de lo que pide la hoja.', 'You read the heat well enough to melt alloys one step colder than the sheet says.'),
    "tecnica.forja.herencia_limpia": ('Herencia limpia', 'Clean Inheritance'),
    "tecnica.forja.herencia_limpia.desc": ('La herencia pasa cuatro quintos de lo que sabía la pieza vieja en lugar de la mitad.', 'Herencia passes four fifths of what the old piece knew instead of half.'),
    "tecnica.forja.firma_del_maestro": ('Firma del maestro', 'Master Signature'),
    "tecnica.forja.firma_del_maestro.desc": ('Tu propia obra te responde el doble de bien: cuatro por ciento en vez de dos.', 'Your own work answers to you twice as well: four percent instead of two.'),
    "tecnica.forja.alma_de_forja": ('Alma de forja', 'Soul of the Forge'),
    "tecnica.forja.alma_de_forja.desc": ('Una pieza de cada diez sale de la estrella con una mejora que nadie le puso.', 'One piece in ten leaves the star already carrying an upgrade nobody put there.'),
    "gui.forja.pestana.forja": ("Forja", "Forge"),
    "gui.forja.pestana.tecnicas": ("Técnicas", "Techniques"),
    "gui.forja.tecnica.elige": ("Elige una técnica", "Choose a technique"),
    "gui.forja.tecnica.bloqueada": ("Necesitas maestría %s", "Needs Maestria %s"),
    "gui.forja.tecnica.maestria": ("Se abre en maestría %s", "Opens at Maestria %s"),
    "gui.forja.tecnica.aviso": ("Sólo una de las tres, y es para siempre", "Only one of the three, and it is forever"),
    "gui.forja.jade.calor": ("Calor: %s", "Heat: %s"),
    "gui.forja.jade.fase": ("Fase %s de %s", "Stage %s of %s"),
    "gui.forja.tecnica.tuya": ("Es la que elegiste", "This is the one you took"),
    "gui.forja.tecnica.perdida": ("Elegiste %s en su lugar", "You took %s instead"),
    "gui.forja.tecnica.clic": ("Clic para aprenderla, y es para siempre", "Click to learn it, and it is forever"),
    "gui.forja.tecnica.aprendida": ("Aprendiste %s", "You learned %s"),
    "gui.forja.tecnica.alma": ("Algo tuyo se quedó en la pieza: %s", "Something of yours stayed in the piece: %s"),
    "gui.forja.boton.recalentar": ("Recalentar", "Reheat"),
    "gui.forja.boton.derretir": ("Derretir", "Melt down"),
    "gui.forja.fundir.titulo": ("Derretir piezas", "Melt the parts down"),
    "gui.forja.fundir.devuelve": ("Recuperas %s", "You get back %s"),
    "gui.forja.fundir.aviso": ("Cada pieza devuelve la mitad de lo que costó, y todas deben ser del mismo material",
                               "Each part gives back half of what it cost, and they must all be of one material"),
    "advancements.forja.fundir.title": ("Vuelta al lingote", "Back to the Ingot"),
    "advancements.forja.fundir.description": ("Derrite piezas sobre lava para recuperar material",
                                              "Melt parts down over lava to get the material back"),
    "gui.forja.libro.tecnicas": ("Técnicas del herrero", "Smith techniques"),
    "gui.forja.libro.tecnicas_intro": (
        "La maestría mide cuánto has trabajado; la técnica es la forma que tomó ese trabajo. En maestría "
        "3, 6 y 9 se abre una elección de tres en la pestaña Técnicas de la mesa de forja. Eliges una y "
        "las otras dos se cierran para siempre, así que piénsalo.",
        "Maestria measures how much you have worked; a technique is the shape that work took. At Maestria "
        "3, 6 and 9 a choice of three opens in the Techniques tab of the forge table. You take one and the "
        "other two close for good, so think it over.",
    ),
    "gui.forja.libro.tecnica_linea": ("%s: %s", "%s: %s"),
    "gui.forja.libro.martillo_maestro": (
        "Una técnica es para siempre, con una excepción: el **martillo del maestro** que lleva el Herrero "
        "Caído. Usarlo te devuelve las tres elecciones y se gasta al hacerlo, así que cada jefe vale por "
        "un cambio de idea.",
        "A technique is forever, with one exception: the **master's hammer** the Fallen Smith carries. "
        "Using it gives you back all three choices and is spent doing it, so each boss is worth one "
        "change of mind.",
    ),
    "gui.forja.libro.tecnicas_nivel": ("Maestría %s", "Maestria %s"),
    "advancements.forja.tecnica.title": ("Escuela propia", "A School of Your Own"),
    "advancements.forja.tecnica.description": (
        "Aprende tu primera técnica de herrero", "Learn your first smith technique",
    ),
    "advancements.forja.tumulo.title": ("Lo que se llevó a la tumba", "What They Took With Them"),
    "advancements.forja.tumulo.description": ("Abre el cofre de un túmulo del herrero", "Open the chest in a smith's barrow"),
    "advancements.forja.campamento.title": ("Devolver el favor", "Returning the Favour"),
    "advancements.forja.campamento.description": (
        "Saquea el cofre de un campamento saqueador", "Loot the chest of a raider camp",
    ),
    "gui.forja.libro.tumulo": ("Túmulo del herrero", "Smith's barrow"),
    "gui.forja.libro.tumulo_desc": (
        "Bajo tierra, sin mapa que lo venda: la tumba de un herrero, con su yunque a la cabecera del "
        "ataúd, lo que le enterraron en un cofre y un barril, y dos corazas vacías que llevan ahí desde "
        "entonces. Si la encuentras, el yunque vale por media forja allí mismo.",
        "Underground, and no map sells it: a smith's grave, their anvil at the head of the coffin, what "
        "they were buried with in a chest and a barrel, and two hollow suits that have been standing "
        "there ever since. Find one and the anvil is half a workshop where it stands.",
    ),
    "gui.forja.libro.campamento": ("Campamento saqueador", "Raider camp"),
    "gui.forja.libro.campamento_desc": (
        "Una empalizada de abetos con dos tiendas, una hoguera y una torre con campana. Ahí viven los "
        "saqueadores entre asalto y asalto, con hierro forjado encima y el capitán con una leyenda a "
        "maestría 10. En los cofres está lo que han quitado a otros herreros. El Forjador vende el mapa.",
        "A spruce palisade with two tents, a fire and a bell tower. This is where the raiders live "
        "between raids, in forged iron, the captain carrying a legend at mastery 10. Their chests hold "
        "what they have taken off other smiths. The Forjador sells the map.",
    ),
    "key.forja.guia": ("Abrir la guía de forja", "Open the forge guide"),
    "gui.forja.sin_libro": ("Lleva el Cuaderno del aprendiz para abrir la biblioteca, o un libro de la forja en la mano",
                            "Carry the Apprentice's Notebook to open the library, or hold a forge book"),
    "gui.forja.taller.completo": ("Taller completo: mejoras más rendidoras y ventana de martillo más ancha",
                                  "Whole workshop: upgrades go further and the hammer window is wider"),
    "gui.forja.taller.suelto": ("Mesa suelta: pon cerca la mesa de piezas y la de talabartería",
                                "A table on its own: put the parts table and the saddlery near it"),
    "gui.forja.jei.piezas": ("Forja: piezas de cada objeto", "Forja: parts of each item"),
    "gui.forja.jei.mejoras": ("Forja: mejoras", "Forja: upgrades"),
    "gui.forja.jei.aleaciones": ("Forja: aleaciones", "Forja: alloys"),
    "gui.forja.jei.paso": ("%s%% por objeto", "%s%% per item"),
    "item.forja.hierro_estelar": ("Hierro estelar", "Star Iron"),
    "item.forja.jarra": ("Jarra de esencia", "Essence Jar"),
    "item.forja.jarra.de": ("Jarra de %s", "Jar of %s"),
    "material.forja.estelar": ("hierro estelar", "star iron"),
    "trait.forja.estelar": ("Estelar", "Stellar"),
    "trait.forja.estelar.desc": ("nada hecho de hierro estelar deja que una caída te haga daño", "nothing made of star iron lets a fall hurt you"),
    # The long form the guide's materials chapter prints. These two traits were added outside TRAITS,
    # which is what writes all three forms, and the long one was never written: the book printed the key.
    "trait.forja.estelar.largo": ("Armadura: con una sola pieza puesta, ninguna caída te hace daño. El conjunto completo además te quita un tercio de la gravedad.",
                                   "Armor: with a single piece on, no fall hurts you. The full set also takes a third of your gravity away."),
    "conjunto.forja.estelar": ("Menos gravedad y 8 bloques más de caída segura", "Less gravity and 8 more blocks of safe fall"),
    "block.forja.armario_de_piezas": ("Armario de piezas", "Parts Cabinet"),
    "container.forja.armario_de_piezas": ("Armario de piezas", "Parts Cabinet"),
    # ---- potential: how far a piece's upgrades can go (forge/Potential, docs/POTENCIAL.md)
    "item.forja.fundente_maestro": ("Fundente maestro", "Master Flux"),
    "item.forja.fundente_maestro.desc": ("Lo que pide una mejora para pasar del 90%%: va en la estrella, con los ingredientes",
                                          "What an upgrade asks for to pass 90%%: it goes on the star, with the ingredients"),
    "item.forja.orbe_vacio": ("Orbe vacío", "Empty Orb"),
    "tooltip.forja.potencial": ("Potencial de mejoras: %s%%", "Upgrade potential: %s%%"),
    "gui.forja.potencial.mesa": ("Potencial %s%% · esta mesa llega al %s%%", "Potential %s%% · this table reaches %s%%"),
    "gui.forja.potencial.tope.none": ("Sin tope", "No ceiling"),
    "gui.forja.potencial.tope.station": ("Esta mesa no pasa del %s%%: sigue en la mesa de forja mayor",
                                          "This table stops at %s%%: carry on at the greater forge table"),
    "gui.forja.potencial.tope.potential": ("El potencial de la pieza es %s%%: no admite más",
                                            "The piece's potential is %s%%: it will take no more"),
    "gui.forja.potencial.tope.flux": ("Para pasar del %s%% hace falta fundente maestro en la estrella",
                                       "Past %s%% it needs master flux on the star"),
    "gui.forja.potencial.tope.load": ("A la pieza no le queda carga para otra mejora así",
                                       "The piece has no load left for another upgrade like this"),
    "gui.forja.potencial.tope.greater": ("Todo o nada, y pesada: sólo en la mesa de forja mayor",
                                          "All or nothing, and heavy: only at the greater forge table"),
    "gui.forja.potencial.tope.pacts": ("La pieza ya lleva todos los pactos que admite",
                                        "The piece already carries as many pacts as it will take"),
    # ---- pacts: sealed until the smith offers something rare, once (upgrade/Pacts)
    "gui.forja.pacto.sellado": ("Sellado: ábrelo con %s en la estrella", "Sealed: open it with %s on the star"),
    "gui.forja.pacto.ofrenda": ("Ofrenda: %s", "Offering: %s"),
    "gui.forja.pacto.abierto": ("Has abierto el %s. Ya puedes forjarlo cuando quieras.", "You have opened the %s. You can forge it whenever you like."),
    # ---- the load: what a piece carries against what its potential lets it hold (forge/Potential, client/LoadBar)
    "gui.forja.potencial.corto": ("Potencial %s%%", "Potential %s%%"),
    "gui.forja.carga.corta": ("Carga %s/%s", "Load %s/%s"),
    "gui.forja.carga.peso": ("peso %s", "weight %s"),
    "gui.forja.carga.no_cabe": ("No cabe: pesa %s, carga libre %s", "No room: weighs %s, free load %s"),
    "gui.forja.carga.consejo": ("Más potencial, o libera carga en la mesa de extracción",
                                 "More potential, or free load at the extraction table"),
    "gui.forja.carga.numeros": ("%s/%s", "%s/%s"),
    "gui.forja.potencial.muy_corto": ("Pot. %s%%", "Pot. %s%%"),
    "gui.forja.carga.titulo": ("Carga de mejoras: %s de %s", "Upgrade load: %s of %s"),
    "gui.forja.carga.linea": ("%s · peso %s", "%s · weight %s"),
    "gui.forja.carga.linea.libre": ("%s · no pesa", "%s · weighs nothing"),
    "gui.forja.carga.mas": ("Con potencial %s%%. Una pieza perfecta aguanta %s", "At potential %s%%. A perfect piece holds %s"),
    "gui.forja.carga.libera": ("Quitarla libera %s de carga", "Taking it off frees %s of load"),
    # ---- the extraction table (menu/ExtractionMenu)
    "block.forja.mesa_de_extraccion": ("Mesa de extracción", "Extraction Table"),
    "container.forja.mesa_de_extraccion": ("Mesa de extracción", "Extraction Table"),
    "gui.forja.extraccion.extraer": ("Extraer", "Extract"),
    "gui.forja.extraccion.borrar": ("Borrar", "Erase"),
    "gui.forja.extraccion.pon_pieza": ("Pon una pieza forjada", "Put a forged piece in"),
    "gui.forja.extraccion.sin_mejoras": ("Esta pieza no tiene mejoras", "This piece has no upgrades"),
    "gui.forja.extraccion.elige": ("Elige una mejora", "Pick an upgrade"),
    "gui.forja.extraccion.pacto": ("Un pacto no se deshace", "A pact does not come off"),
    "gui.forja.extraccion.evento_sin_orbe": ("Sólo sale a un orbe vacío", "It only comes out into an empty orb"),
    "gui.forja.extraccion.falta_pago": ("Falta el pago", "The price is not paid"),
    "gui.forja.extraccion.salida_llena": ("Recoge antes el orbe", "Take the orb first"),
    "gui.forja.extraccion.cuesta": ("Quitarla cuesta:", "Taking it off costs:"),
    "gui.forja.extraccion.se_guarda": ("%s %s%% sale entera al orbe", "%s %s%% comes out whole, into the orb"),
    "gui.forja.extraccion.se_pierde": ("Se perderá %s %s%%: no hay orbe vacío", "%s %s%% will be lost: there is no empty orb"),
    "gui.forja.extraccion.potencial_baja": ("El potencial de la pieza bajará a %s%%. Lo que quede por encima no se pierde, pero no podrá subir",
                                             "The piece's potential will drop to %s%%. What stands above it is kept, but cannot be raised"),
    "gui.forja.extraccion.resto_intacto": ("Piezas, calidad, firma, maestría y el resto de mejoras no se tocan",
                                            "Parts, quality, signature, mastery and the other upgrades are left alone"),
    # One line each, and short: the strip between the drawers and the inventory is one line tall.
    "gui.forja.armario.pista": ("Sólo guarda cosas de herrero", "Holds a smith's things only"),
    "gui.forja.armario.no": ("Eso no es cosa de herrero", "That is not a smith's"),
    "gui.forja.libro.armario": (
        "Veintisiete huecos que solo aceptan cosas de forja: piezas, plantillas, orbes, sellos, "
        "talismanes, lo que has forjado y los materiales propios del mod. Lo que le metan las tolvas pasa "
        "por el mismo filtro, así que puesto debajo de una ordena solo.",
        "Twenty-seven slots that take nothing but smith's things: parts, templates, orbs, seals, "
        "talismans, what you have forged and the mod's own materials. Whatever a hopper pushes into it "
        "goes through the same filter, so under one it sorts itself.",
    ),
    "item.forja.martillo_del_maestro": ("Martillo del maestro", "Master's Hammer"),
    "item.forja.martillo_del_maestro.desc": ("Te devuelve tus tres elecciones de técnica, y se gasta al hacerlo",
                                             "Gives you back your three technique choices, and is spent doing it"),
    "gui.forja.martillo.nada": ("No has elegido ninguna técnica todavía.", "You have not taken a technique yet."),
    "gui.forja.martillo.olvidas": ("Olvidas %s técnicas: vuelve a la mesa a elegir.",
                                   "You forget %s techniques: go back to the table and choose again."),
    "advancements.forja.martillo.title": ("Pensándolo mejor", "On Second Thought"),
    "advancements.forja.martillo.description": ("Usa el martillo del maestro para olvidar tus técnicas",
                                                "Use the master's hammer to forget your techniques"),
    "item.forja.placa_hueca": ("Placa hueca", "Hollow Plate"),
    "tag.item.forja.acero": ('Acero', 'Steel'),
    "tag.item.forja.acero_estelar": ('Acero estelar', 'Star steel'),
    "tag.item.forja.bronce": ('Bronce', 'Bronze'),
    "tag.item.forja.corazon_de_forja": ('Corazón de forja', 'Forge heart'),
    "tag.item.forja.damasco": ('Damasco', 'Damascus'),
    "tag.item.forja.electro": ('Electro', 'Electrum'),
    "tag.item.forja.hierro_estelar": ('Hierro estelar', 'Star iron'),
    "tag.item.forja.laton": ('Latón', 'Brass'),
    "tag.item.forja.obsidiacero": ('Obsidiacero', 'Obsidian steel'),
    "tag.item.forja.peltre": ('Peltre', 'Pewter'),
    "tag.item.forja.placa_hueca": ('Placa hueca', 'Hollow plate'),
    "material.forja.hueco": ("placa hueca", "hollow plate"),
    "trait.forja.vacio": ("Vacío", "Hollow"),
    "trait.forja.vacio.desc": ("el acero corriente resbala en ella: 3% por pieza de no recibir el golpe",
                                "plain steel slides off it: 3% per piece to take no hit at all"),
    "trait.forja.vacio.largo": ("Armadura: 3% por pieza de que un golpe dado con un arma sin forjar no te toque. Contra obra forjada no hace nada.",
                                 "Armor: 3% per piece that a blow struck with an unforged weapon never lands. Against forged work it does nothing."),
    "conjunto.forja.hueco": ("Andas más rápido y te agachas casi sin ruido", "You walk faster and crouch almost without sound"),
    "evento.forja.meteoritos": ("Lluvia de meteoritos", "Meteor Shower"),
    "evento.forja.tormenta_arcana": ("Tormenta arcana", "Arcane Storm"),
    "evento.forja.niebla_de_almas": ("Niebla de almas", "Soul Fog"),
    "evento.forja.aurora": ("Aurora", "Aurora"),
    "evento.forja.luna_de_sangre": ("Luna de sangre", "Blood Moon"),
    "evento.forja.eclipse": ("Eclipse", "Eclipse"),
    "evento.forja.ventisca": ("Ventisca", "Blizzard"),
    "evento.forja.marea_viva": ("Marea viva", "Spring Tide"),
    "evento.forja.lluvia_de_pavesas": ("Lluvia de pavesas", "Ember Rain"),
    # Short on purpose: the card that comes down says the rest, and this is only the line left in the log.
    "gui.forja.evento": ("Empieza: %s.", "It begins: %s."),
    "gui.forja.libro.bestiario.herrumbre": ("%s de vida. No te quita vida: te come la armadura. Muerde por casi nada y se lleva un trozo de la durabilidad de lo que mordió, y va primero a por el metal. Vienen varias. Quitarte la placa buena y pelear en camisa es la respuesta.",
        "%s health. It does not take your life, it eats your armour: it bites for almost nothing and takes a chunk of durability off whatever it bit, metal first. They come in numbers. Taking the good plate off and fighting in your shirt is the answer."),
    "gui.forja.libro.bestiario.ascua_mayor": ("%s de vida. Una pavesa crecida: más lenta y más dura. Al morir se deshace en cinco pavesas, ya despiertas y ya a tu lado. Mátala de lejos, o al menos no rodeado.",
        "%s health. A wisp, grown: slower and harder. When it dies it comes apart into five wisps, already awake and already next to you. Kill it at range, or at least not surrounded."),
    "gui.forja.libro.bestiario.escoria_viviente": ("Se parte al morir, y cada trozo deja el suelo ardiendo donde cae: el terreno que limpias es el que ya no puedes pisar. Los trozos más pequeños se enfrían en escoria, y es la única forma de conseguirla.",
        "It splits when it dies, and every piece leaves burning ground where it falls: the floor you clear is the floor you can no longer stand on. The smallest pieces cool into slag, which is the only way to get it."),
    "gui.forja.libro.bestiario.yunque_andante": ("%s de vida. No te persigue: se planta detrás del grupo y suelda, devolviendo vida a corazas, autómatas y percutores tan rápido como se la quitas. O atraviesas a los que pegan para llegar a él, o pegas más fuerte que un soldador.",
        "%s health. It does not chase you: it plants itself behind the group and welds, putting health back into suits, automatons and strikers as fast as you take it off. Go through the ones hitting you to reach it, or out-damage a welder."),
    "gui.forja.libro.bestiario.percutor": ("%s de vida. Un martillo pilón que camina. El ariete tarda en caer y el círculo del suelo es exactamente lo que alcanza; si cae sobre un escudo levantado rompe la guardia y lo deja en enfriamiento largo. No bloquees: sal del círculo.",
        "%s health. A drop hammer that walks. The ram takes its time and the circle on the floor is exactly what it reaches; if it lands on a raised shield it breaks the guard and puts it on a long cooldown. Do not block: step out of the circle."),
    "gui.forja.libro.bestiario.tenaza": ("%s de vida. Lo único del mod que te sujeta: te cierra encima y te deja clavado unos segundos. Casi no hace daño, pero todo lo demás de la sala ya sabe dónde vas a estar. Su alcance no se ve hasta que lo usa.",
        "%s health. The only thing in the mod that holds you: it closes on you and roots you for a few seconds. It barely hurts, but everything else in the room now knows where you will be. Its reach is hidden until it uses it."),
    "gui.forja.libro.bestiario.cargador_de_carbon": ("%s de vida. Casi todo él es combustible. Camina hacia ti, se planta, se hincha y estalla: el círculo que lleva en el suelo es lo que se lleva por delante. Si lo matas antes también revienta, más pequeño y sin aviso. Haz que se comprometa y sal del círculo; el carbón queda en el suelo igual.",
        "%s health. It is mostly fuel. It walks at you, plants its feet, swells and goes off: the circle it carries on the floor is what it takes with it. Killed first it still blows, smaller and with no warning. Make it commit and walk out of the circle; the coal is on the floor either way."),
    "gui.forja.libro.bestiario.templador": ("%s de vida. No te quita vida: te apaga. Su aceite se lleva el fuego, la carga voltaica y, sobre todo, el combo de Frenesí, y el charco sigue apagando a quien lo pise. Mantiene la distancia; ve a por él aunque tengas que dar la espalda a lo demás.",
        "%s health. He takes no health: he puts you out. His oil takes your fire, your voltaic charge and above all your Frenzy combo, and the pool keeps quenching whoever stands in it. He keeps his distance; go and deal with him even if it means turning your back on the rest."),
    "gui.forja.libro.bestiario.nucleo_estelar": ("%s de vida. No pelea hasta que lo obligas: guarda cada golpe que le das y, cuando se llena, te lo devuelve todo de una vez. Pegarle más fuerte es peor. Se pone blanco y sus esquirlas se abren y giran más deprisa cuanto más lleno está: entonces, deja de pegar.",
        "%s health. It does not fight until you make it: it keeps every blow you give it and, when it is full, gives the lot back at once. Hitting it harder is worse. It goes white and its shards swing out and spin faster the fuller it gets: that is when to stop swinging."),
    "gui.forja.libro.bestiario.molde_roto": ("%s de vida. Lleva una barra aún sin forma; si le pegas, la mete en su propio horno y la saca con la forma de tu arma, pegando lo que pega la tuya. Lleva algo sencillo si quieres una pelea sencilla. Muere con esa forma y suelta la plantilla grabada de esa pieza.",
        "%s health. It carries a bar with no shape yet; hit it and it puts the bar in its own furnace and brings it out shaped like your weapon, hitting for what yours hits for. Bring something plain for a plain fight. It dies holding that shape and drops the engraved template for that part."),
    "gui.forja.libro.bestiario.guardian_de_cuno": ("%s de vida. Está sellado: mientras arda un solo farol de pavesa en su sala, nada le hace daño. Tres faroles, en tres pilares, con él y sus dos constructos por medio. Su sellazo es el aviso más largo del mod y deja marca en el suelo.",
        "%s health. It is sealed: while a single ember lantern burns in its hall, nothing hurts it. Three lanterns, on three pillars, with it and its two constructs in between. Its stamp is the longest wind-up in the mod and leaves a mark on the floor."),
    "gui.forja.libro.pagina": ("%s · pág. %s", "%s · p. %s"),
    "gui.forja.cartel.mejora": ("Trae la mejora %s", "Carries the %s upgrade"),
    "gui.forja.cartel.pista": ("Guárdalo en una jarra de esencia, a cielo abierto", "Keep it in an essence jar, under the open sky"),
    "gui.forja.cartel.termina": ("%s se apaga", "%s fades"),
    "gui.forja.evento.meteorito": ("Ha caído un meteorito en %s, %s", "A meteorite came down at %s, %s"),
    "gui.forja.yunque.taller": ("El yunque del maestro completa el taller de %s mesa(s): cada mejora sube un punto más",
                               "The master's anvil completes the workshop for %s table(s): every upgrade climbs a point further"),
    "gui.forja.yunque.solo": ("El yunque no tiene ninguna mesa de forja a %s bloques: ponlo al lado de una",
                             "No forge table within %s blocks of the anvil: set it beside one"),
    "gui.forja.frasco.nada": ("No está pasando nada en el cielo", "Nothing is happening in the sky"),
    "gui.forja.frasco.techo": ("Necesitas cielo abierto", "You need the open sky"),
    "gui.forja.frasco.lleno": ("La jarra guarda %s", "The jar keeps %s"),
    "gui.forja.jarra.vaciada": ("Vacías la jarra: %s", "You empty the jar: %s"),
    "commands.forja.sin_evento": ("No hay ningún evento llamado %s", "There is no event called %s"),
    "commands.forja.sin_tecnica": ("No existe la técnica %s", "There is no technique called %s"),
    "commands.forja.tecnicas_borradas": ("Técnicas olvidadas", "Techniques forgotten"),
    "commands.forja.fundicion": ("Una línea de fundición entera, encendida y en marcha, delante de ti",
                                 "A whole foundry line, lit and running, in front of you"),
    "gui.forja.encargo": ("Encargo: %s con %s, y %s al %s%% o más", "Order: a %s with %s, and %s at %s%% or more"),
    "gui.forja.encargo.paga": ("Paga: %s esmeraldas, un orbe de %s al %s%% y una plantilla", "Pays: %s emeralds, an orb of %s at %s%% and a template"),
    "gui.forja.encargo.hecho": ("El herrero acepta el encargo", "The smith takes the order"),
    "entity.forja.automata_de_forja": ("Autómata de forja", "Forge Automaton"),
    "entity.forja.coraza_vacia": ("Coraza vacía", "Hollow Plate"),
    "entity.forja.pavesa": ("Pavesa", "Ember Wisp"),
    "entity.forja.herrumbre": ("Herrumbre", "Rust Flake"),
    "entity.forja.ascua_mayor": ("Ascua mayor", "Greater Ember"),
    "entity.forja.escoria_viviente": ("Escoria viviente", "Living Slag"),
    "entity.forja.yunque_andante": ("Yunque andante", "Walking Anvil"),
    "entity.forja.percutor": ("Percutor", "Striker"),
    "entity.forja.tenaza": ("Tenaza", "Tongs"),
    "entity.forja.cargador_de_carbon": ("Cargador de carbón", "Coal Hauler"),
    "entity.forja.templador": ("Templador", "Quencher"),
    "entity.forja.nucleo_estelar": ("Núcleo estelar", "Star Core"),
    "entity.forja.molde_roto": ("Molde roto", "Broken Mould"),
    "gui.forja.templado_apagado": ("El aceite apaga tu arma", "The oil quenches your weapon"),
    "gui.forja.molde_copia": ("El molde copia tu %s", "The mould copies your %s"),
    "gui.forja.cargador_prende": ("Un cargador prende su carga", "A hauler lights its load"),
    "entity.forja.guardian_de_cuno": ("Guardián de cuño", "Cune Guardian"),
    "gui.forja.cuno_sellado": ("El cuno esta sellado: quedan %s sellos", "The die is sealed: %s seals left"),
    "gui.forja.cuno_barra_sellado": ("Guardián de cuño — sellado (%s)", "Cune Guardian — sealed (%s)"),
    "gui.forja.cuno_abierto": ("El último sello se apaga", "The last seal goes out"),
    "structure.forja.castillo_de_forja": ("Castillo de forja", "Forge Castle"),
    "entity.forja.capitan_saqueador": ("Capitán saqueador", "Raider Captain"),
    "gui.forja.saqueadores": ("Una banda de saqueadores viene por tu equipo", "A band of raiders is coming for your gear"),
    "gui.forja.coraza_despierta": ("Algo de metal se ha puesto de pie ahí fuera.", "Something made of metal has stood up out there."),
    "gui.forja.pavesas_llegan": ("Algo ha venido a por tu lumbre.", "Something has come for your fire."),
    "gui.forja.ascua_mayor": ("Algo mucho mayor viene ardiendo.", "Something much bigger is burning its way in."),
    "gui.forja.constructo_despierta": ("Algo del taller se ha puesto en pie.", "Something in the workshop has got up."),
    "gui.forja.pavesa_apagada": ("Está apagada: en el farol no aguantaría encendida.",
                                  "It is cold: it would not stay lit in the lantern."),
    "gui.forja.pavesa_atrapada": ("La has metido en el farol. Puesta bajo una mesa de forja vale por la lava.",
                                   "You got it into the lantern. Set under a forge table it is worth lava."),
    "block.forja.farol_de_pavesa": ("Farol de pavesa", "Wisp Lantern"),
    "item.forja.ascua": ("Ascua", "Ember"),
    "item.forja.escoria": ("Escoria", "Slag"),
    "item.forja.acero_refractario": ("Acero refractario", "Refractory Steel"),
    "item.forja.molde_de_fundicion": ("Molde de %s", "%s Mould"),
    "item.forja.molde_de_fundicion.vacio": ("Molde de fundición", "Casting Mould"),
    "block.forja.cuba_de_colada": ("Cuba de colada", "Melt Tank"),
    "block.forja.conducto_de_colada": ("Conducto de colada", "Melt Pipe"),
    # The heat line (docs/FUNDICION_V2.md, part B).
    "block.forja.tubo_de_calor": ("Tubo de calor", "Heat Pipe"),
    "block.forja.caldera": ("Caldera", "Boiler"),
    "block.forja.deposito_de_calor": ("Depósito de calor", "Heat Depot"),
    "fluido.forja.vapor": ("Vapor", "Steam"),
    "fluido.forja.lava": ("Lava", "Lava"),
    "fluido.forja.sangre_de_blaze": ("Sangre de blaze", "Blaze Blood"),
    "fluido.forja.aliento_de_forja": ("Aliento de forja", "Forge Breath"),
    "fluido.forja.salmuera_helada": ("Salmuera helada", "Ice Brine"),
    "gui.forja.caldera.vacia": ("%s vacía · caben %s mB", "%s empty · holds %s mB"),
    "gui.forja.caldera.tiene": ("%s · %s: %s / %s mB", "%s · %s: %s / %s mB"),
    "gui.forja.caldera.sin_fuego": ("le falta fuego debajo (calor %s o más)", "it needs a fire under it (%s heat or more)"),
    "gui.forja.crisol.vapor_blando": ("El vapor sólo funde lo blando: %s pide fuego o un fluido más caliente",
                                      "Steam only melts the soft metals: %s wants a fire or a hotter fluid"),
    "gui.forja.caldera.espera": ("llena, o con otro fluido: espera a que se vacíe", "full, or holding another fluid: it waits until it runs dry"),
    "gui.forja.cuba.vacia": ("Cuba vacía · %s cubas conectadas, cabida %s", "Tank empty · %s tanks joined, holds %s"),
    "gui.forja.cuba.dentro": ("%s · %s/%s en %s cubas", "%s · %s/%s across %s tanks"),
    "gui.forja.cuba.otro": ("Esta cuba lleva %s: un depósito, un metal", "This tank is holding %s: one bank, one metal"),
    "gui.forja.cuba.llena": ("El depósito está lleno", "The bank is full"),
    "block.forja.cano_de_colada": ("Caño de colada", "Melt Spout"),
    "block.forja.llave_de_paso": ("Llave de paso", "Melt Valve"),
    "gui.forja.llave.abierta": ("Llave abierta: el metal pasa", "Valve open: the metal goes through"),
    "gui.forja.llave.cerrada": ("Llave cerrada: la red queda cortada aquí", "Valve closed: the network is cut here"),
    "gui.forja.llave.forzada": ("Llave abierta, pero la redstone la mantiene cerrada",
                                "Valve open, but a redstone signal is holding it shut"),
    "block.forja.conducto_de_acero": ("Conducto de acero", "Steel Melt Pipe"),
    "block.forja.conducto_de_damasco": ("Conducto de damasco", "Damascus Melt Pipe"),
    "item.forja.colador": ("Colador de barro", "Clay Strainer"),
    "item.forja.colador.de": ("Colador de %s", "%s Strainer"),
    "block.forja.colador": ("Colador", "Strainer"),
    "tooltip.forja.colador": ("Aguanta hasta %s de dureza · se pone encima de una mesa de colada",
                              "Holds up to %s hardness · set it on top of a casting table"),
    "tooltip.forja.colada": ("Colada limpia:", "Cleanly poured:"),
    "tooltip.forja.basta": ("Pieza basta: la colada salió mal", "Rough casting: the pour went wrong"),
    "gui.forja.cuba.cuajada": ("Cuajada · hace falta un crisol encendido al lado para refundirla",
                                "Set · it needs a lit crucible beside it to melt again"),
    "gui.forja.cuba.calor": ("%s%% de calor", "%s%% heat"),
    "block.forja.caja_de_moldeo": ("Caja de moldeo", "Casting Box"),
    "block.forja.caja_de_moldeo_de_acero": ("Caja de moldeo de acero", "Steel Casting Box"),
    "block.forja.caja_de_moldeo_de_damasco": ("Caja de moldeo de damasco", "Damascus Casting Box"),
    "tooltip.forja.molde": ("Ponlo encima de una mesa de colada: cuela esta pieza gastando %s de metal",
                            "Set it on a casting table: it casts this part for %s metal"),
    "gui.forja.caja.pon_pieza": ("Pon una pieza, una herramienta o un colador. Los moldes se cuelan en una mesa de colada",
                                  "Put in a part, a tool or a strainer. Moulds are poured on a casting table"),
    "gui.forja.caja.gastara": ("Se gastará la pieza y saldrá su molde", "The part will be spent and its mould comes out"),
    "gui.forja.caja.lista": ("Molde de %s · %s de metal por pieza", "%s mould · %s metal a part"),
    "gui.forja.caja.molde_a_mesa": ("Este molde se cuela encima de una mesa de colada, con un colador puesto",
                                     "This mould is poured on a casting table, with a strainer on top"),
    "gui.forja.caja.vacia": ("Vacía · aguanta hasta %s de dureza", "Empty · holds up to %s hardness"),
    "gui.forja.caja.todo": ("cualquier cosa", "anything"),
    "gui.forja.caja.aguanta": ("Aguanta hasta %s de dureza", "Holds up to %s hardness"),
    "gui.forja.caja.aguanta_todo": ("Aguanta cualquier metal", "Holds any metal"),
    "gui.forja.caja.moldeando": ("Moldeando %s", "Taking the mould of %s"),
    "item.forja.marco": ("Marco de %s", "%s Frame"),
    "item.forja.marco.vacio": ("Marco de colada", "Casting Frame"),
    "tooltip.forja.marco": ("Cuela esta herramienta entera gastando %s de metal",
                             "Casts this whole tool for %s metal"),
    "block.forja.mesa_de_losa": ("Mesa de losa", "Slate Casting Table"),
    "block.forja.mesa_de_brasa": ("Mesa de brasa", "Ember Casting Table"),
    "block.forja.mesa_de_almas": ("Mesa de almas", "Soul Casting Table"),
    "gui.forja.mesa_colada.vacia": ("Pon encima un molde o un marco para colar la pieza o la herramienta entera",
                                     "Set a mould or a frame on it to cast the part or the whole tool"),
    "gui.forja.mesa_colada.espera": ("Marco de %s · necesita %s de metal · %s%% de calor",
                                      "%s frame · needs %s metal · %s%% heat"),
    "gui.forja.mesa_colada.espera_molde": ("Molde de %s · necesita %s de metal · %s%% de calor",
                                            "%s mould · needs %s metal · %s%% heat"),
    "gui.forja.mesa_colada.sin_colador": ("sin colador encima: saldrá basta", "no strainer on top: it will come out rough"),
    "gui.forja.mesa_colada.con_colador": ("%s encima, aguanta hasta %s", "%s on top, holds up to %s"),
    "gui.forja.mesa_colada.colando_de": ("Colando en %s · %s%%", "Pouring %s · %s%%"),
    "gui.forja.mesa_colada.colando": ("Está colando: espera a que cuaje",
                                       "It is pouring: wait for it to set"),
    # ---- the assembler (block/entity/AssemblerMachineBlockEntity, client/AssemblerMachineScreen)
    "block.forja.montadora": ("Montadora", "Assembler"),
    "block.forja.pararrayos": ("Pararrayos de estrellas", "Star Rod"),
    # ---- the Cementerio entre Estrellas (docs/HERRERO_DIMENSION.md, world/StarYard)
    "block.forja.ceniza": ("Ceniza", "Ash"),
    "block.forja.ceniza_prensada": ("Ceniza prensada", "Packed Ash"),
    "block.forja.metal_fundido": ("Metal fundido", "Molten Metal"),
    "block.forja.arma_clavada": ("Arma clavada", "Grave Blade"),
    "commands.forja.dimension.llegas": ("Llegas al Cementerio entre Estrellas", "You arrive at the Graveyard Among the Stars"),
    "commands.forja.dimension.falta": ("El Cementerio entre Estrellas no está cargado en este mundo",
                                       "The Graveyard Among the Stars is not loaded in this world"),
    "gui.forja.cementerio.sin_cama": ("Aquí no se duerme: el cielo no deja de mirar", "Nobody sleeps here: the sky never stops watching"),
    "subtitles.forja.cementerio": ("Un martillo lejano", "A distant hammer"),
    "item.forja.oricalco": ("Lingote de oricalco", "Orichalcum Ingot"),
    "item.forja.estrella_forjada": ("Estrella forjada", "Forged Star"),
    "block.forja.brasa_estelar": ("Brasa estelar", "Star Ember"),
    "block.forja.estrella_de_vuelta": ("Estrella de vuelta", "Star Home"),
    "block.forja.fragua_fria_estelar": ("Fragua fría estelar", "Cold Star Forge"),
    "gui.forja.pelea.cae": ("Una estrella se desprende del cielo...", "A star breaks away from the sky..."),
    "gui.forja.pelea.reforjado": ("El Herrero reaviva su forja: %s brasas le hacen inmortal. Vuelca los braseros de los pilares para que el metal las apague.",
                                  "The Smith rekindles his forge: %s embers make him immortal. Tip the braziers on the pillars so the metal puts them out."),
    "gui.forja.pelea.aturdido": ("¡La forja se apaga! El Herrero queda aturdido: ahora le duele más.", "The forge goes out! The Smith is stunned: now he takes more."),
    "gui.forja.pelea.furia": ("¡El Herrero se enfurece! Su forja arde violeta: es más rápido, pega más fuerte y sus golpes vuelven antes.",
                              "The Smith is enraged! His forge burns violet: he is faster, hits harder and his blows come back sooner."),
    "gui.forja.pelea.estrella_de_vuelta": ("Una estrella cae en la arena: tócala para volver.", "A star falls on the arena: touch it to go back."),
    "gui.forja.pelea.recompensa": ("Recibes una Estrella forjada: póntela en una pieza en la forja mayor.", "You receive a Forged Star: set it in a piece at the greater forge."),
    "gui.forja.pelea.revancha": ("La fragua fría arde de nuevo: el Herrero vuelve.", "The cold forge burns again: the Smith returns."),
    "gui.forja.brasero.volcado": ("¡Un brasero se vuelca! El metal corre hacia el centro.", "A brazier tips over! The metal runs to the middle."),
    "gui.forja.brasero.hierro": ("Hacen falta %s hierros estelares para llenar el brasero", "It takes %s star iron to fill the brazier"),
    "gui.forja.constelacion.herrero": ("%s se enciende en %s: el cielo favorece al Herrero", "%s lights up in %s: the sky favours the Smith"),
    "gui.forja.constelacion.jugadores": ("%s se enciende en %s: el cielo está con vosotros", "%s lights up in %s: the sky is with you"),
    "gui.forja.constelacion.espada": ("La Espada", "The Sword"),
    "gui.forja.constelacion.hacha": ("El Hacha", "The Axe"),
    "gui.forja.constelacion.martillo": ("El Martillo", "The Hammer"),
    "gui.forja.constelacion.lanza": ("La Lanza", "The Spear"),
    "gui.forja.constelacion.escudo": ("El Escudo", "The Shield"),
    "gui.forja.constelacion.yunque": ("El Yunque", "The Anvil"),
    "gui.forja.constelacion.tenazas": ("Las Tenazas", "The Tongs"),
    "gui.forja.constelacion.guadana": ("La Guadaña", "The Scythe"),
    "gui.forja.constelacion.color.0": ("plata", "silver"),
    "gui.forja.constelacion.color.1": ("cobre", "copper"),
    "gui.forja.constelacion.color.2": ("oro", "gold"),
    "gui.forja.constelacion.color.3": ("azul estelar", "star blue"),
    "gui.forja.constelacion.color.4": ("carmesí", "crimson"),
    "item.forja.perla_de_oricalco": ("Perla de oricalco", "Orichalcum Pearl"),
    "block.forja.mensula_estelar": ("Ménsula estelar", "Star Bracket"),
    "block.forja.portal_estelar": ("Portal estelar", "Star Portal"),
    "gui.forja.portal.faltan": ("Faltan %s perlas de oricalco para encender el portal", "%s more orichalcum pearls to light the portal"),
    "gui.forja.fragua.abre": ("La fragua apagada se abre en un marco de estrellas: cuatro perlas de oricalco lo encenderán",
                              "The cold forge opens into a frame of stars: four orichalcum pearls will light it"),
    "gui.forja.libro.pararrayos": ("Pararrayos de estrellas: un meteorito que iba a caer a %s bloques o menos de él cae sobre él, y el aviso en el suelo ya lo marca ahí. No abre cráter y deja el hierro estelar a sus pies. Aguanta %s; el último lo rompe. Se hace con un fragmento de amatista, un lingote de acero, dos de cobre y uno de hierro.",
                                   "Star Rod: a meteorite that would have come down within %s blocks of it comes down on it instead, and the warning on the ground already shows it there. No crater, and the star iron is left at its foot. It takes %s; the last one breaks it. Made from an amethyst shard, a steel ingot, two copper ingots and an iron one."),
    "gui.forja.montadora.hara": ("Montará: %s", "Will make: %s"),
    "gui.forja.montadora.marco": ("El marco pide: %s", "The frame asks for: %s"),
    "gui.forja.montadora.estrella": ("Pon piezas en la estrella, o un marco en el centro",
                                     "Put parts on the star, or a frame in the centre"),
    "gui.forja.montadora.calor": ("Calor %s · %s s por pieza", "%s heat · %s s a piece"),
    "gui.forja.montadora.calor_frio": ("Calor %s: le falta fuego", "%s heat: it needs a fire"),
    "gui.forja.montadora.montando": ("Montando · %s%%", "Assembling · %s%%"),
    "gui.forja.montadora.frio": ("Tiene todo menos calor: una fogata debajo o al lado", "It has all but heat: a campfire under or beside it"),
    "gui.forja.montadora.llena": ("Espera a que saquen la pieza hecha", "Waiting for the finished piece to be taken"),
    "gui.forja.montadora.nada": ("Estas piezas no montan nada", "These parts make nothing"),
    "gui.forja.montadora.talabarteria": ("Las bardas son cosa de la talabartería", "Barding is the saddlery's work"),
    "gui.forja.montadora.faltan": ("Faltan: %s", "Missing: %s"),
    "gui.forja.montadora.vacia": ("Siempre calidad normal: lo perfecto es tuyo",
                                  "Always a plain press: perfect is yours"),
    "block.forja.crisol_de_barro": ("Crisol de barro", "Clay Crucible"),
    "block.forja.crisol_de_hierro": ("Crisol de hierro", "Iron Crucible"),
    "block.forja.crisol_de_obsidiana": ("Crisol de obsidiana", "Obsidian Crucible"),
    "gui.forja.crisol.calor": ("Alcanza %s", "Reaches %s"),
    "gui.forja.crisol.cabida": ("Cabida %s/%s", "Holds %s/%s"),
    "gui.forja.crisol.cuela": ("Colando %s", "Pouring %s"),
    "gui.forja.crisol.funde": ("Fundiendo a %s", "Melting down to %s"),
    "gui.forja.crisol.frio": ("%s pide calor %s", "%s needs %s heat"),
    "gui.forja.crisol.nada": ("Nada que colar", "Nothing to pour"),
    "gui.forja.crisol.vacio": ("Crisol vacío · alcanza calor %s", "Crucible empty · reaches %s heat"),
    "gui.forja.crisol.dentro": ("Dentro: %s · %s%%", "Inside: %s · %s%%"),
    "gui.forja.crisol.dos": ("%s y %s", "%s and %s"),
    "gui.forja.crisol.lleno": ("El crisol no admite más de %s de una vez",
                                "The crucible will not hold more than %s at a time"),
    "gui.forja.crisol.sin_ascua": ("falta ascua", "needs embers"),
    "gui.forja.crisol.refunde": ("Refundiendo una cuba cuajada", "Melting a set tank back down"),
    "gui.forja.crisol.sin_cuba": ("No hay cuba con sitio para fundir %s", "No tank with room to melt %s into"),
    "gui.forja.crisol.frio_metal": ("Fundir %s pide calor %s", "Melting %s needs %s heat"),
    "gui.forja.cuba.no_metal": ("%s no cabe en una cuba: sólo metal fundido", "%s does not go in a tank: molten metal only"),
    "gui.forja.caja.gastara_marco": ("Se gastará la herramienta y saldrá su marco", "The tool will be spent and its frame comes out"),
    "gui.forja.caja.infundiendo": ("Bañando el colador en %s", "Bathing the strainer in %s"),
    "gui.forja.caja.colador_nada": ("Ninguna cuba tiene un metal más duro que este colador",
                                    "No tank holds a metal harder than this strainer"),
    "gui.forja.jade.avivada": ("Avivada: pega más y quema el doble", "Fed: hits harder and burns twice as long"),
    "gui.forja.jade.apagada": ("Apagada", "Cold"),
    "gui.forja.obra_maestra": ("%s es una obra maestra: nada más puede darle un herrero.",
                               "%s is a masterpiece: there is nothing more a smith can give it."),
    "tooltip.forja.obra_maestra": ("Obra maestra", "Masterpiece"),
    "gui.forja.libro.obra_maestra": (
        "Y si una pieza acaba teniéndolo todo —nacida de un golpe perfecto, llevada a maestría 10, con "
        "su don grabado y firmada por ti— la estrella lo nota: se vuelve una obra maestra, sube otro "
        "%s%% en todo y lleva marco propio. No se fabrica; se reconoce.",
        "And if a piece ends up with everything on it - born of a perfect press, worn to Maestria 10, its "
        "gift engraved and signed by you - the star notices: it becomes a masterpiece, gains another %s%% "
        "on everything and wears a frame of its own. It is not made; it is recognised.",
    ),
    "advancements.forja.obra_maestra.title": ("Obra maestra", "Masterpiece"),
    "advancements.forja.obra_maestra.description": (
        "Ten una pieza perfecta, a maestría 10, con don y firmada por ti",
        "Hold a piece that is perfect, at Maestria 10, with a gift and signed by you",
    ),
    "entity.forja.herrero_caido": ("El Herrero Caído", "The Fallen Smith"),
    "entity.forja.aprendiz": ("Aprendiz de la fragua", "Forge Apprentice"),
    "item.forja.corazon_de_forja": ("Corazón de forja", "Forge Heart"),
    "material.forja.corazon": ("corazón de forja", "forge heart"),
    "conjunto.forja.corazon": ("+4 corazones, +4 de dureza y el fuego se te apaga solo", "+4 hearts, +4 toughness and fire goes out on you at once"),
    # Slag had a set bonus and no words for it: the armour's tooltip printed "conjunto.forja.escoria".
    "conjunto.forja.escoria": ("El fuego te dura un 60% menos", "Fire burns out 60% sooner on you"),
    "block.forja.mesa_de_talabarteria": ("Mesa de talabartería", "Saddlery Table"),
    "container.forja.mesa_de_talabarteria": ("Mesa de talabartería", "Saddlery Table"),
    "block.forja.yunque_del_herrero": ("Yunque del Herrero Caído", "Anvil of the Fallen Smith"),
    "block.forja.fragua_apagada": ("Fragua apagada", "Dead Forge"),
    "gui.forja.fragua.pide": ("La fragua pide %s: %s/%s", "The forge asks for %s: %s/%s"),
    "gui.forja.fragua.ya": ("El Herrero ya está despierto", "The Smith is already awake"),
    "gui.forja.fragua.despierta": ("La fragua se enciende y el Herrero Caído se levanta", "The forge lights up and the Fallen Smith rises"),
    "entity.forja.elite": ("%s (%s de élite)", "%s (elite %s)"),
    "item.forja.talisman": ("Talismán", "Talisman"),
    "item.forja.talisman.de": ("Talismán de %s", "Talisman of %s"),
    "item.forja.cinturon": ("Cinturón de herramientas", "Tool Belt"),
    "talisman.forja.esmeralda": ("esmeralda", "emerald"),
    "talisman.forja.esmeralda.desc": ("los aldeanos te tratan como a un héroe", "villagers treat you like a hero"),
    "talisman.forja.diamante": ("diamante", "diamond"),
    "talisman.forja.diamante.desc": ("+1 de armadura", "+1 armor"),
    "talisman.forja.cuarzo": ("cuarzo", "quartz"),
    "talisman.forja.cuarzo.desc": ("15%% de crítico en cada golpe", "a 15%% crit chance on every blow"),
    "talisman.forja.amatista": ("amatista", "amethyst"),
    "talisman.forja.amatista.desc": ("20%% de esquivar proyectiles", "a 20%% chance to dodge projectiles"),
    "talisman.forja.eco": ("eco", "echo"),
    "talisman.forja.eco.desc": ("+25%% de maestría en lo que llevas", "+25%% Maestria on what you carry"),
    "talisman.forja.prismarina": ("prismarina", "prismarine"),
    "talisman.forja.prismarina.desc": ("bajo el agua respiras, ves y trabajas como en tierra",
                                        "underwater you breathe, see and work as if on land"),
    "talisman.forja.neterita": ("netherita", "netherite"),
    "talisman.forja.neterita.desc": ("nada te empuja tan lejos como pretende", "nothing shoves you as far as it means to"),
    "tooltip.forja.talisman": ("Solo cuenta el primero del inventario", "Only the first one in your bag counts"),
    "tooltip.forja.talisman.activo": ("Activo", "Active"),
    "tooltip.forja.cinturon": ("Guarda 4 herramientas y te pone la buena al picar", "Holds 4 tools and hands you the right one as you dig"),
    "tooltip.forja.cinturon.lleva": ("Lleva: %s", "Holding: %s"),
    "tooltip.forja.cinturon.vacio": ("Vacío: clic derecho con un pico, hacha, pala o azada", "Empty: right-click with a pick, axe, shovel or hoe"),
    "temple.forja.agua": ("agua", "water"),
    "temple.forja.agua.desc": ("se desgasta un 10%% menos", "wears 10%% more slowly"),
    "temple.forja.lava": ("lava", "lava"),
    "temple.forja.lava.desc": ("prende 2 s a quien golpeas", "sets what you hit on fire for 2 s"),
    "temple.forja.nieve": ("nieve polvo", "powder snow"),
    "temple.forja.nieve.desc": ("ralentiza 2 s a quien golpeas", "slows what you hit for 2 s"),
    "temple.forja.miel": ("miel", "honey"),
    "temple.forja.miel.desc": ("deja pegado a quien golpeas 3 s", "leaves what you hit stuck for 3 s"),
    "gui.forja.boton.heredar": ("Heredar", "Inherit"),
    "gui.forja.vuelo.agotado": ("Las alas se agotaron", "The wings gave out"),
    "gui.forja.vuelo.sin_polvora": ("Sin pólvora para el impulso", "No gunpowder for the burst"),
    "gui.forja.stat.hacha": ("Bloqueo por hacha: %s", "Axe disable: %s"),
    "gui.forja.stat.carga": ("Daño de carga: %s", "Charge damage: %s"),
    "gui.forja.stat.preparacion": ("Preparar carga: %s s", "Charge wind-up: %s s"),
    "gui.forja.stat.caida": ("Golpe en caída: %s", "Smash damage: %s"),
    "gui.forja.stat.empuje": ("Resist. empuje: %s%%", "Knockback resist: %s%%"),
    "gui.forja.stat.durabilidad_mult": ("Durabilidad: %s", "Durability: %s"),
    "gui.forja.stat.durabilidad_mas": ("Durabilidad: %s", "Durability: %s"),
    "gui.forja.stat.dano_mas": ("Daño: %s", "Damage: %s"),
    "gui.forja.stat.velocidad_mas": ("Vel. ataque: %s", "Attack speed: %s"),
    "gui.forja.stat.minado_mult": ("Vel. minado: %s", "Mining speed: %s"),
    "gui.forja.stat.flecha_mas": ("Daño flecha: %s", "Arrow damage: %s"),
    "gui.forja.stat.dureza_mas": ("Dureza: %s", "Toughness: %s"),
    "gui.forja.con_material": ("Hecha de %s:", "Made of %s:"),
    "gui.forja.guia.como": ("%s:", "%s:"),
    "tooltip.forja.durabilidad": ("Durabilidad: %s/%s", "Durability: %s/%s"),
    "gui.forja.stat.rasgo": ("Rasgo: %s", "Trait: %s"),
    "gui.forja.guia.sin_rasgo": ("Sin rasgo", "No trait"),
    "gui.forja.guia.material_blando": ("Solo para mangos, ataduras, cuerdas, placas y forros", "Only for handles, bindings, strings, plates and linings"),
    # Where each material comes from, and whether the parts table cuts it or the foundry has to pour it:
    # the materials chapter said what every one of them did and never how to get one.
    "gui.forja.guia.origen": ("Se obtiene: %s", "Found: %s"),
    "gui.forja.guia.origen.aleacion": ("Aleación: %s, en mesa %s o en el crisol", "Alloy: %s, on a %s table or in the crucible"),
    "gui.forja.guia.origen.aleacion_blanca": ("Aleación: %s, sólo en el crisol de obsidiana", "Alloy: %s, only in the obsidian crucible"),
    "gui.forja.guia.se_corta": ("Se corta en la mesa de piezas", "Cut on the parts table"),
    "gui.forja.guia.se_cuela": ("No se corta: se cuela en la fundición", "Not cut: poured in the foundry"),
    "material.forja.madera.origen": ("tablas de cualquier madera", "planks of any wood"),
    "material.forja.piedra.origen": ("roca, piedra negra o pizarra abismal labrada", "cobblestone, blackstone or cobbled deepslate"),
    "material.forja.hueso.origen": ("huesos, de los esqueletos", "bones, from skeletons"),
    "material.forja.cuero.origen": ("cuero, de vacas, caballos y llamas", "leather, from cows, horses and llamas"),
    "material.forja.cobre.origen": ("lingotes de cobre: mena de cobre fundida en un horno", "copper ingots: copper ore smelted in a furnace"),
    "material.forja.hierro.origen": ("lingotes de hierro: mena de hierro fundida en un horno", "iron ingots: iron ore smelted in a furnace"),
    "material.forja.oro.origen": ("lingotes de oro: mena de oro fundida en un horno", "gold ingots: gold ore smelted in a furnace"),
    "material.forja.amatista.origen": ("fragmentos de amatista, de las geodas", "amethyst shards, from geodes"),
    "material.forja.diamante.origen": ("diamantes: mena de diamante en lo más hondo, con pico de hierro", "diamonds: diamond ore deep down, with an iron pickaxe"),
    "material.forja.obsidiana.origen": ("obsidiana: agua sobre lava quieta, con pico de diamante", "obsidian: water on still lava, with a diamond pickaxe"),
    "material.forja.netherita.origen": ("lingotes de netherita: restos antiguos del Nether fundidos, con oro", "netherite ingots: Nether ancient debris smelted, with gold"),
    "material.forja.esmeralda.origen": ("esmeraldas: aldeanos, o su mena en las montañas", "emeralds: villagers, or their ore in the mountains"),
    "material.forja.prismarina.origen": ("fragmentos de prismarina, de los guardianes", "prismarine shards, from guardians"),
    "material.forja.vara_de_blaze.origen": ("varas de blaze, de las fortalezas del Nether", "blaze rods, from Nether fortresses"),
    "material.forja.cuarzo.origen": ("cuarzo: mena de cuarzo del Nether", "quartz: Nether quartz ore"),
    "material.forja.purpur.origen": ("bloques de púrpura, de las ciudades del End", "purpur blocks, from End cities"),
    "material.forja.obsidiana_llorona.origen": ("portales en ruinas y trueques con piglins", "ruined portals and piglin bartering"),
    "material.forja.eco.origen": ("fragmentos de eco, en los cofres de las ciudades antiguas", "echo shards, in ancient city chests"),
    "material.forja.resina.origen": ("ladrillos de resina: la resina de los creaking, fundida", "resin bricks: creaking resin, smelted"),
    "material.forja.escama.origen": ("escamas de armadillo: cepíllalos", "armadillo scutes: brush them"),
    "material.forja.corazon.origen": ("sólo el herrero caído, al morir", "only the fallen smith, when he dies"),
    "material.forja.estelar.origen": ("los meteoritos de una lluvia de meteoritos lo dejan en su cráter", "the meteors of a meteor shower leave it in their crater"),
    "material.forja.hueco.origen": ("a veces lo sueltan las corazas vacías al caer", "sometimes dropped by hollow plates when they fall"),
    "material.forja.escoria.origen": ("los últimos trozos de la escoria viviente, junto a la lava", "the last pieces of living slag, beside lava"),
    "tooltip.forja.rasgo": ("Rasgo %s: %s", "Trait %s: %s"),
    # Arrow tips (combat/ArrowTips): how the tip flies and bites, and its special.
    "tooltip.forja.punta.pesada": ("Punta de %s, pesada: %s%% de daño, cae al %s%% y empuja más",
                                   "%s tip, heavy: %s%% damage, drops at %s%% and shoves harder"),
    "tooltip.forja.punta.media": ("Punta de %s: %s%% de daño, cae al %s%%", "%s tip: %s%% damage, drops at %s%%"),
    "tooltip.forja.punta.ligera": ("Punta de %s, ligera: %s%% de daño, vuela plana (cae al %s%%)",
                                   "%s tip, light: %s%% damage, flies flat (drops at %s%%)"),
    "tooltip.forja.punta.dura": ("Dura: atraviesa un %s%% más de armadura", "Hard: goes through %s%% more armour"),
    "tooltip.forja.punta.especial": ("%s: %s", "%s: %s"),
    "flecha.forja.especial.fuego": ("Fuego", "Fire"),
    "flecha.forja.especial.fuego.desc": ("prende al blanco %s s", "sets the target alight for %s s"),
    "flecha.forja.especial.brasa": ("Brasa", "Ember"),
    "flecha.forja.especial.brasa.desc": ("lo prende %s s y pega más a lo que ya arde", "sets it alight for %s s and hits harder what is already burning"),
    "flecha.forja.especial.sangrado": ("Sangrado", "Bleeding"),
    "flecha.forja.especial.sangrado.desc": ("abre una herida que sangra %s s", "opens a wound that bleeds for %s s"),
    "flecha.forja.especial.resina": ("Resina", "Resin"),
    "flecha.forja.especial.resina.desc": ("lo pega: Lentitud II %s s", "gums it up: Slowness II for %s s"),
    "flecha.forja.especial.marea": ("Marea", "Tide"),
    "flecha.forja.especial.marea.desc": ("bajo el agua vuela como en el aire", "flies underwater as it does in air"),
    "flecha.forja.especial.salto": ("Salto", "Blink"),
    "flecha.forja.especial.salto.desc": ("el blanco salta a un sitio a %s bloques, como con una fruta coral (nunca un jefe)",
                                         "the target blinks up to %s blocks away, like a chorus fruit (never a boss)"),
    "flecha.forja.especial.llanto": ("Llanto", "Weeping"),
    "flecha.forja.especial.llanto.desc": ("Debilidad %s s", "Weakness for %s s"),
    "flecha.forja.especial.eco": ("Eco", "Echo"),
    "flecha.forja.especial.eco.desc": ("atraviesa a uno más", "pierces one more"),
    "flecha.forja.especial.estrella": ("Estrella", "Star"),
    "flecha.forja.especial.estrella.desc": ("casi no cae: vuela recta", "barely falls: flies straight"),
    "flecha.forja.especial.hueca": ("Hueca", "Hollow"),
    "flecha.forja.especial.hueca.desc": ("encuentra el hueco de cualquier armadura", "finds the gap in any armour"),
    "flecha.forja.especial.chispa": ("Chispa", "Spark"),
    "flecha.forja.especial.chispa.desc": ("una chispa salta al enemigo más cercano (%s de daño, %s bloques)",
                                          "a spark jumps to the nearest foe (%s damage, %s blocks)"),
    "flecha.forja.especial.buscadora": ("Buscadora", "Seeker"),
    "flecha.forja.especial.buscadora.desc": ("se tuerce hacia lo que te caza", "bends towards whatever hunts you"),
    "flecha.forja.especial.vidrio": ("Vidrio", "Glass"),
    "flecha.forja.especial.vidrio.desc": ("sale más rápida y plana, y se rompe al acertar", "leaves faster and flatter, and shatters on what it hits"),
    "flecha.forja.especial.sol": ("Sol", "Sun"),
    "flecha.forja.especial.sol.desc": ("a pleno sol pega más y quema a los no muertos", "in full sun it hits harder and burns the undead"),
    "flecha.forja.especial.luna": ("Luna", "Moon"),
    "flecha.forja.especial.luna.desc": ("en la oscuridad pega más", "hits harder in the dark"),
    "flecha.forja.especial.viva": ("Viva", "Living"),
    "flecha.forja.especial.viva.desc": ("te cura %s de vida al acertar", "heals you %s health when it hits"),
    "flecha.forja.especial.fortuna": ("Fortuna", "Fortune"),
    "flecha.forja.especial.fortuna.desc": ("un %s%% de las veces sale crítica", "leaves critical %s%% of the time"),
    "flecha.forja.especial.marca": ("Marca", "Mark"),
    "flecha.forja.especial.marca.desc": ("el blanco brilla %s s y se ve a través de las paredes", "the target glows for %s s and shows through walls"),
    "flecha.forja.especial.conductora": ("Conductora", "Conductive"),
    "flecha.forja.especial.conductora.desc": ("en algo mojado (agua o lluvia) la descarga suma %s de daño", "on something wet (water or rain) the shock adds %s damage"),
    "flecha.forja.especial.hechizo": ("Hechizo", "Spell"),
    "flecha.forja.especial.hechizo.desc": ("te devuelve %s de maná al acertar", "gives you back %s mana when it hits"),
    "gui.forja.coste": ("%s: cuesta %s de material", "%s: costs %s material"),
    "gui.forja.coste_tooltip": ("Cuesta %s de material", "Costs %s material"),
    "gui.forja.material_invalido": ("Ese objeto no es un material de forja", "That item is not a forge material"),
    "gui.forja.material_blando": ("%s no sirve para cabezas ni hojas", "%s can't make heads or blades"),
    "gui.forja.faltan": ("Faltan %s de %s", "Need %s more %s"),
    "block.forja.mesa_de_forja_mayor": ("Mesa de forja mayor", "Greater Forge Table"),
    "container.forja.mesa_de_forja_mayor": ("Mesa de forja mayor", "Greater Forge Table"),
    "gui.forja.mesa_mayor.1": ("Este banco no monta %s", "This bench will not assemble %s"),
    "gui.forja.mesa_mayor.2": ("Hace falta la mesa de forja mayor", "It needs the greater forge table"),
    "gui.forja.faltan_piezas": ("Faltan piezas:", "Missing parts:"),
    # What the tables say when they turn something away or when what is on them makes nothing.
    "gui.forja.material_colado": ("%s no se corta: se cuela en la fundición", "%s is not cut: it is cast at the foundry"),
    "gui.forja.estrella.rechaza": ("%s no sirve en la estrella", "%s is no use on the star"),
    "gui.forja.estrella.acepta": ("Van piezas, objetos forjados, ingredientes, orbes y libros",
                                  "It takes parts, forged gear, ingredients, orbs and books"),
    "gui.forja.centro.ocupado": ("Saca %s del centro para seguir", "Take %s out of the center to go on"),
    "gui.forja.piezas.no_forman": ("Estas piezas no forman ningún objeto", "These parts make no item"),
    "gui.forja.piezas.no_forman.pista": ("Cada objeto lleva sus piezas exactas: míralas en la guía",
                                         "Every item takes its exact parts: see them in the guide"),
    "gui.forja.piezas.no_encajan": ("Esas piezas no son de este objeto", "Those parts do not belong to this gear"),
    "gui.forja.piezas.no_encajan.pista": ("Para cambiar una pieza, pon otra del mismo tipo", "To swap a part, put down one of the same kind"),
    "gui.forja.don.falta": ("Un sello graba su don en un objeto de maestría %s que aún no tenga uno, si el don es para él",
                            "A seal engraves its gift on Mastery %s gear that has none yet, if the gift is meant for it"),
    "gui.forja.herencia.falta": ("Para heredar, la pieza vieja necesita maestría %s", "To inherit, the old piece needs Mastery %s"),
    "gui.forja.mesa.no_monta": ("Esta mesa no monta %s", "This table will not assemble %s"),
    "gui.forja.mesa.solo_talabarteria": ("Solo la talabartería monta bardas y arneses", "Only the saddlery assembles barding and harnesses"),
    "gui.forja.mesa.talabarteria_solo_monturas": ("La talabartería solo hace bardas y arneses", "The saddlery only makes barding and harnesses"),
    "gui.forja.stat.durabilidad": ("Durabilidad: %s", "Durability: %s"),
    "gui.forja.stat.armadura": ("Armadura: %s", "Armor: %s"),
    "gui.forja.stat.dureza": ("Dureza: %s", "Toughness: %s"),
    "gui.forja.stat.dano": ("Daño: %s", "Damage: %s"),
    "gui.forja.stat.velocidad": ("Vel. ataque: %s", "Attack speed: %s"),
    "gui.forja.stat.minado": ("Vel. minado: %s", "Mining speed: %s"),
    "gui.forja.stat.nivel": ("Nivel: %s", "Tier: %s"),
    "gui.forja.rol.head": ("Da nivel, velocidad y daño", "Sets tier, speed and damage"),
    "gui.forja.rol.handle": ("Da durabilidad y vel. de ataque", "Adds durability and attack speed"),
    "gui.forja.rol.extra": ("Suma algo de durabilidad", "Adds a little durability"),
    "gui.forja.rol.plate": ("Da armadura, dureza y aspecto", "Sets armor, toughness and look"),
    "gui.forja.rol.lining": ("Da durabilidad y algo de dureza", "Adds durability and some toughness"),
    "gui.forja.mejora.incompatible": ("Incompatible con:", "Incompatible with:"),
    "gui.forja.mejora.maxima": ("Ya está al 100%%", "Already at 100%%"),
    "gui.forja.mejora.progreso": ("%s%% → %s%%", "%s%% → %s%%"),
    "gui.forja.mejora.no_sirve.1": ("Esos objetos no", "Those items don't"),
    "gui.forja.mejora.no_sirve.2": ("mejoran esta pieza.", "upgrade this gear."),
    "gui.forja.mejora.no_sirve.3": ("Mira la guía de forja.", "See the forge guide."),
    "gui.forja.mejora.actuales": ("Mejoras actuales:", "Current upgrades:"),
    "gui.forja.mejora.ninguna": ("Ninguna todavía", "None yet"),
    "gui.forja.mejora.linea": ("%s %s%%", "%s %s%%"),
    "gui.forja.guia.al_maximo": ("Al 100%%: %s", "At 100%%: %s"),
    "gui.forja.guia.para": ("Para: %s", "For: %s"),
    "gui.forja.guia.cualquiera": ("%s (cualquier tipo)", "%s (any kind)"),
    "gui.forja.guia.para.todo": ("Todo", "All gear"),
    "gui.forja.guia.para.armadura": ("Armadura", "Armor"),
    "gui.forja.guia.para.herramientas": ("Herramientas", "Tools"),
    "gui.forja.guia.para.algunas_herramientas": ("Algunas herramientas", "Some tools"),
    "gui.forja.guia.para.armas": ("Armas y hachas", "Weapons and axes"),
    "gui.forja.guia.para.armas_de_filo": ("Armas de filo (espada, daga, espadón, guadaña, lanza)", "Edged weapons (sword, dagger, greatsword, scythe, spear)"),
    "gui.forja.guia.para.herramientas_y_armas": ("Herramientas y armas", "Tools and weapons"),
    "upgrade.forja.nivel": ("%s %s", "%s %s"),
    "upgrade.forja.nivel_al": ("%s: nivel I al %s%%", "%s: level I at %s%%"),
    "upgrade.forja.vision_nocturna.efecto.inactiva": ("Se activa al 100%%", "Turns on at 100%%"),
        "tooltip.forja.parte": ("%s: %s", "%s: %s"),
    "tooltip.forja.coste": ("Hecha con %s de %s", "Made from %s %s"),
    "tooltip.forja.mejoras": ("Mejoras:", "Upgrades:"),
    "tooltip.forja.mejora": (" %s %s%%: %s", " %s %s%%: %s"),
}


# Texts that went straight into the lang files (combat, duels, difficulty, armour resistances) and were
# never brought back here: listed now, so that running this no longer deletes them.
GUI.update({
    "gui.forja.parada_normal": ("¡Parada!", "Parry!"),
    "gui.forja.parada_tarde": ("Un poco tarde...", "A little late..."),
    "gui.forja.parada_apresurada": ("Demasiado pronto: sube el escudo a tiempo", "Too soon: raise the shield with timing"),
    "key.forja.esquivar": ("Esquivar", "Dodge"),
    "gui.forja.arena_bloqueada": ("No puedes construir ni romper nada mientras el Herrero Caído siga en pie",
                                  "You cannot build or break anything while the Fallen Smith still stands"),
    "gui.forja.asedio": ("¡Asedio! Vienen a por tu forja", "A siege! They are coming for your forge"),
    "gui.forja.robado": ("Te han robado: %s", "Stolen from you: %s"),
    "gui.forja.nemesis": ("%s ha vuelto", "%s is back"),
    "entity.forja.nemesis": ("%s, el que volvió", "%s, who came back"),
    "gui.forja.duelo.reto": ("%s te reta a un duelo: entra en el círculo", "%s challenges you to a duel: step into the ring"),
    "gui.forja.duelo.aceptado": ("Duelo aceptado", "Duel accepted"),
    "gui.forja.duelo.rechazado": ("Rechazaste el duelo: están furiosos", "You refused the duel: they are furious"),
    "gui.forja.duelo.trampa": ("¡Trampa! El duelo se rompe", "Foul play! The duel is off"),
    "gui.forja.duelo.huida": ("Huiste del duelo: te recordará", "You fled the duel: it will remember"),
    "gui.forja.duelo.victoria": ("¡Duelo ganado!", "Duel won!"),
    "commands.forja.dificultad": ("Dificultad: %s (cifras de %s) · noches sobrevividas: %s · adaptativa: %s · tu equipo: %s (tramo %s)", "Difficulty: %s (%s figures) · nights survived: %s · adaptive: %s · your gear: %s (tier %s)"),
    "commands.forja.dificultad.cambiada": ("La dificultad ahora es %s", "The difficulty is now %s"),
    "commands.forja.dificultad.forzada": ("Ojo: config/forja.json fuerza el nivel %s (\"nivel\"), y es el que manda", "Note: config/forja.json forces the %s level (\"nivel\"), and that one rules"),
    "commands.forja.dificultad.no_existe": ("No existe la dificultad %s (pacifico, facil, normal, dificil, implacable)", "There is no difficulty called %s (pacifico, facil, normal, dificil, implacable)"),
    # ---- the difficulty ladder (difficulty/Ladder): Minecraft's button, one step past Hard
    "dificultad.forja.nivel.pacifico": ("Pacífico", "Peaceful"),
    "dificultad.forja.nivel.facil": ("Fácil", "Easy"),
    "dificultad.forja.nivel.normal": ("Normal", "Normal"),
    "dificultad.forja.nivel.dificil": ("Difícil", "Hard"),
    "dificultad.forja.nivel.extremo": ("Implacable", "Relentless"),
    "options.difficulty.forja_extremo": ("Implacable", "Relentless"),
    "options.difficulty.forja_extremo.info": (
        "Lo más duro de Forja: todo lo de Difícil, más las redes nuevas, la red del capitán y el capitán entero, y más "
        "veteranos y élites.",
        "Forja at its hardest: all of Hard, plus the new networks, the captain's network and the whole captain, and more "
        "veterans and elites."),
    "options.difficulty.forja_extremo.hardcore": (
        "Un mundo extremo (hardcore) es Difícil o Implacable: pulsa para cambiar.",
        "A hardcore world is Hard or Relentless: press to switch."),
    "gamerule.forja.extremo": ("Dificultad Implacable de Forja", "Forja's Relentless difficulty"),
    "gamerule.forja.extremo.description": (
        "Con la dificultad en Difícil, el mundo está en Implacable. Lo cambian el botón de dificultad y /forja dificultad.",
        "With the difficulty on Hard, the world is on Relentless. The difficulty button and /forja dificultad change it."),
    "dificultad.forja.aprendiz": ("Aprendiz", "Apprentice"),
    "dificultad.forja.herrero": ("Herrero", "Smith"),
    "dificultad.forja.maestro": ("Maestro", "Master"),
    "dificultad.forja.leyenda": ("Leyenda", "Legend"),
    # ---- names of the ranked monsters (difficulty/Names): a name of its own and a byname of its rank
    "name.forja.nombre.veterano": ("%s %s", "%s %s"),
    "name.forja.nombre.elite": ("%s %s", "%s %s"),
    "name.forja.nombre.campeon": ("%s, %s", "%s, %s"),
    "name.forja.apodo.veterano.0": ("Rompehuesos", "Bonebreaker"),
    "name.forja.apodo.veterano.1": ("Tres Muescas", "Three Notches"),
    "name.forja.apodo.veterano.2": ("Cicatriz", "Scar"),
    "name.forja.apodo.veterano.3": ("Dientes Rotos", "Brokentooth"),
    "name.forja.apodo.veterano.4": ("Piel de Cuero", "Leatherhide"),
    "name.forja.apodo.veterano.5": ("Ojo Tuerto", "One-Eye"),
    "name.forja.apodo.veterano.6": ("Colmillo Mellado", "Chipped Fang"),
    "name.forja.apodo.veterano.7": ("Sangre Vieja", "Old Blood"),
    "name.forja.apodo.veterano.8": ("Cien Heridas", "Hundred Wounds"),
    "name.forja.apodo.veterano.9": ("Mano Rota", "Brokenhand"),
    "name.forja.apodo.veterano.10": ("Pellejo Duro", "Toughhide"),
    "name.forja.apodo.veterano.11": ("Sin Tumba", "Graveless"),
    "name.forja.apodo.elite.0": ("la Hoja Gris", "the Grey Blade"),
    "name.forja.apodo.elite.1": ("Rompescudos", "Shieldbreaker"),
    "name.forja.apodo.elite.2": ("Mano de Hierro", "Ironhand"),
    "name.forja.apodo.elite.3": ("Sin Piedad", "the Merciless"),
    "name.forja.apodo.elite.4": ("Voz de Guerra", "Warvoice"),
    "name.forja.apodo.elite.5": ("Sombra Larga", "Longshadow"),
    "name.forja.apodo.elite.6": ("Hueso Negro", "Blackbone"),
    "name.forja.apodo.elite.7": ("Paso de Trueno", "Thunderstep"),
    "name.forja.apodo.elite.8": ("Filo Amargo", "Bitter Edge"),
    "name.forja.apodo.elite.9": ("Corona de Cuervos", "Crown of Crows"),
    "name.forja.apodo.elite.10": ("Lengua de Acero", "Steeltongue"),
    "name.forja.apodo.elite.11": ("Última Guardia", "Last Watch"),
    "name.forja.apodo.campeon.0": ("Azote de Reinos", "Scourge of Realms"),
    "name.forja.apodo.campeon.1": ("Nunca Vencido", "the Unbeaten"),
    "name.forja.apodo.campeon.2": ("Devoraciudades", "Devourer of Cities"),
    "name.forja.apodo.campeon.3": ("Portador de la Leyenda", "Bearer of the Legend"),
    "name.forja.apodo.campeon.4": ("Heraldo del Fin", "Herald of the End"),
    "name.forja.apodo.campeon.5": ("Trono de Huesos", "Throne of Bones"),
    "name.forja.apodo.campeon.6": ("Fin de Héroes", "Heroes' End"),
    "name.forja.apodo.campeon.7": ("Sangre de Forja", "Forgeblood"),
    "name.forja.apodo.campeon.8": ("Martillo del Ocaso", "Hammer of Dusk"),
    "name.forja.apodo.campeon.9": ("Terror del Valle", "Terror of the Vale"),
    "name.forja.apodo.campeon.10": ("Ruina de Estirpes", "Bane of Bloodlines"),
    "name.forja.apodo.campeon.11": ("Eco de Mil Batallas", "Echo of a Thousand Battles"),
    "entity.forja.amenaza.veterano": ("%s veterano", "Veteran %s"),
    "entity.forja.amenaza.elite": ("%s de élite", "Elite %s"),
    "gui.forja.esquiva_perfecta": ("¡Esquiva perfecta! Contraataca", "Perfect dodge! Strike back"),
    "tooltip.forja.resiste": ("Resiste: %s · %s · %s", "Resists: %s · %s · %s"),
    "tooltip.forja.resiste.corte": ("corte %s", "slash %s"),
    "tooltip.forja.resiste.golpe": ("golpe %s", "blunt %s"),
    "tooltip.forja.resiste.perforacion": ("perforación %s", "pierce %s"),
    "tooltip.forja.peso": ("Peso: %s", "Weight: %s"),
    "tooltip.forja.peso.ligera": ("ligera", "light"),
    "tooltip.forja.peso.media": ("media", "medium"),
    "tooltip.forja.peso.pesada": ("pesada", "heavy"),
    "gui.forja.estamina": ("Estamina", "Stamina"),
})


# --- Clases (docs/CLASES.md) ---
GUI.update({
    "gui.forja.clase.elegir": ("Elige tu clase", "Choose Your Class"),
    "gui.forja.clase.cambiar": ("Cambiar de clase", "Change Class"),
    "gui.forja.clase.base": ("De base", "Base stats"),
    "gui.forja.clase.habilidades": ("Habilidades", "Skills"),
    "gui.forja.clase.habilidad_1": ("I · %s %s", "I · %s %s"),
    "gui.forja.clase.habilidad_2": ("II · %s %s · del árbol", "II · %s %s · from the tree"),
    "gui.forja.clase.habilidad_3": ("III · Final %s · elige una de tres", "III · Ultimate %s · pick one of three"),
    "gui.forja.clase.habilidad_3_cuales": ("Al final de cada senda del árbol: %s, %s o %s. Solo puedes tener una.",
                                           "At the end of each path of the tree: %s, %s or %s. You can only have one."),
    "gui.forja.clase.arbol": ("Árbol de clase", "Class Tree"),
    "gui.forja.clase.coste_cambio": ("Cambiar gasta el Medallón del olvido y la nueva clase empieza en el nivel 1 (los hitos se quedan). Tu misma clase solo vacía el árbol y conserva el nivel.",
                                     "Changing spends the Medallion of Oblivion and the new class starts at level 1 (milestones stay). Your own class only empties the tree and keeps the level."),
    "gui.forja.clase.elegida": ("Tu clase ahora es %s. Tu árbol está en la tecla %s.", "Your class is now %s. Your tree is on the %s key."),
    "gui.forja.clase.herrero_retirado": ("La clase Herrero ya no existe: la forja está ahora en el árbol de todas las clases. Elige clase gratis con la tecla %s.",
                                         "The Smith class is gone: the forge is now in every class's tree. Pick a class for free with the %s key."),
    "gui.forja.clase.arbol_ultimas": (
        "Tu árbol cambió: cada senda acaba ahora en su propia habilidad final, y solo se elige una. Conservas la que "
        "tenías; %s puntos de nodos que se movieron vuelven para repartir (tecla %s).",
        "Your tree changed: each path now ends in its own ultimate, and only one can be chosen. You keep the one you "
        "had; %s points of nodes that moved come back to spend (key %s)."),
    "gui.forja.clase.arbol_nuevo": ("Los árboles de clase han crecido: tienes %s puntos para repartir (tecla %s).",
                                    "The class trees have grown: you have %s points to spend (%s key)."),
    "gui.forja.habilidad.sin_aprender": ("Aún no has aprendido esa habilidad: está en tu árbol", "You have not learned that skill yet: it is in your tree"),
    "gui.forja.habilidad.sin_estamina": ("No tienes estamina suficiente", "Not enough stamina"),
    "gui.forja.habilidad.sin_mana": ("No tienes maná suficiente", "Not enough mana"),
    "gui.forja.talento.no.excluded": ("Ya tienes la otra clave de esta rama", "You already have this branch's other keystone"),
    "gui.forja.talento.no.ultimate": ("Ya elegiste otra habilidad final: solo se puede tener una",
                                      "You already chose another ultimate: you can only have one"),
    "gui.forja.clase.toast.punto": ("+%s puntos de árbol (%s)", "+%s tree points (%s)"),
    "commands.forja.clase.info": ("%s: %s de nivel %s · %s de experiencia · %s puntos libres · %s nodos · %s puntos de hitos",
                                  "%s: %s, level %s · %s XP · %s free points · %s nodes · %s milestone points"),
    "key.forja.habilidad_3": ("Habilidad de clase III (final)", "Class skill III (ultimate)"),
    # The big tree screen (client/TalentTreeScreen, docs/ARBOLES.md).
    "gui.forja.arbol.puntos": ("Puntos: %s de %s", "Points: %s of %s"),
    "gui.forja.arbol.hitos_puntos": ("de hitos: %s", "from milestones: %s"),
    "gui.forja.arbol.hitos_espera": ("de hitos: %s (+%s al subir)", "from milestones: %s (+%s later)"),
    "gui.forja.arbol.plan": ("Probando: %s puntos (te quedarían %s)", "Trying: %s points (%s would be left)"),
    "gui.forja.arbol.olvidar": ("Vela del olvido: %s de %s puntos", "Candle of Oblivion: %s of %s points"),
    "gui.forja.arbol.aplicado": ("Aprendidos %s nodos", "Learned %s nodes"),
    "gui.forja.arbol.boton_hitos": ("Hitos", "Milestones"),
    "gui.forja.arbol.boton_quitar": ("Quitar", "Remove"),
    "gui.forja.arbol.boton_descartar": ("Descartar", "Discard"),
    "gui.forja.arbol.boton_aplicar": ("Aplicar", "Apply"),
    "gui.forja.arbol.boton_probar": ("Probar", "Try"),
    "gui.forja.arbol.boton_probando": ("Probando", "Trying"),
    "gui.forja.arbol.buscar": ("Buscar...", "Search..."),
    "gui.forja.arbol.totales": ("Lo que suma tu clase", "What your class adds up to"),
    "gui.forja.arbol.con_ii": ("Con su mejora II:", "With its II:"),
    "gui.forja.arbol.hitos_titulo": ("Hitos: %s de %s puntos", "Milestones: %s of %s points"),
    "gui.forja.arbol.hitos_tope": ("Se gastan como mucho %s por nivel de clase; se quedan al cambiar de clase.",
                                   "At most %s can be spent per class level; they stay when you change class."),
    "gui.forja.arbol.tipo.origen": ("Origen", "Origin"),
    "gui.forja.arbol.tipo.nucleo": ("Núcleo", "Core"),
    "gui.forja.arbol.tipo.forja": ("Forja", "Forge"),
    "gui.forja.arbol.tipo.menor": ("Menor", "Minor"),
    "gui.forja.arbol.tipo.notable": ("Notable", "Notable"),
    "gui.forja.arbol.tipo.clave": ("Clave", "Keystone"),
    "gui.forja.arbol.tipo.habilidad": ("Habilidad", "Skill"),
    "gui.forja.arbol.tipo.puente": ("Puente", "Bridge"),
    "gui.forja.arbol.tipo.cruzado": ("De otra clase", "Cross-class"),
    "gui.forja.arbol.de_clase": ("%s · del %s", "%s · from the %s"),
    "gui.forja.arbol.tipo_coste": ("%s · coste %s", "%s · cost %s"),
    "gui.forja.arbol.de_a": ("%s%s → %s", "%s%s → %s"),
    "gui.forja.arbol.excluye": ("Excluye: %s", "Rules out: %s"),
    "gui.forja.arbol.clic_aprender": ("Clic para aprender", "Click to learn"),
    "gui.forja.arbol.clic_probar": ("Clic para probarlo", "Click to try it"),
    "gui.forja.arbol.mayus_probar": ("Mayús + clic: añadirlo a la prueba", "Shift + click: add it to the plan"),
    "gui.forja.arbol.senda_a": ("%s → %s", "%s → %s"),
    "gui.forja.arbol.ultima_elige": ("Final · elige una", "Ultimate · pick one"),
    "gui.forja.arbol.ultima_elegida": ("Elegida", "Chosen"),
    "gui.forja.arbol.ultima_ninguna": ("Final: elige una", "Ultimate: pick one"),
    "gui.forja.arbol.ultima_info": ("Habilidad final %s: una de tres; solo puedes tener una",
                                    "Ultimate %s: one of three; you can only have one"),
    "gui.forja.arbol.ya_elegiste": ("Ya elegiste %s", "You already chose %s"),
    "gui.forja.arbol.cambiar_ultima": ("Para cambiarla: la Vela del olvido quita la que tienes, o el Medallón vacía el árbol",
                                       "To change it: the Candle of Oblivion takes yours off, or the Medallion empties the tree"),
    "gui.forja.hito.logrado": ("¡Hito! %s: +%s puntos de árbol", "Milestone! %s: +%s tree points"),
    "gui.forja.hito.logrado_espera": ("¡Hito! %s: +%s puntos de árbol (%s esperan a que subas de nivel)",
                                      "Milestone! %s: +%s tree points (%s wait until you level up)"),
    "gui.forja.vela.nada": ("No tienes nada aprendido que olvidar", "You have nothing learned to forget"),
    "gui.forja.vela.no_puede": ("La vela solo quita hasta %s puntos de nodos del borde de lo aprendido",
                                "The candle only takes off up to %s points of nodes at the edge of what you have"),
    "gui.forja.vela.falta": ("Necesitas una Vela del olvido", "You need a Candle of Oblivion"),
    "gui.forja.vela.hecho": ("Olvidaste %s nodos: sus puntos vuelven", "You forgot %s nodes: their points come back"),
    "item.forja.vela_del_olvido": ("Vela del olvido", "Candle of Oblivion"),
    "item.forja.vela_del_olvido.desc": ("Clic derecho: quita hasta 4 puntos de nodos de tu árbol, del borde de lo aprendido.",
                                        "Right-click: takes up to 4 points of nodes off your tree, from the edge of what you have."),
    "gui.forja.libro.clases.intro": (
        "Una **clase** es tu forma de pelear. Elegir la primera es gratis: con el botón de aquí abajo o con la tecla "
        "**%s**. Sin clase juegas como siempre, sin bonos ni penalizaciones. La clase, el nivel y el árbol "
        "**sobreviven a la muerte**. Cada clase trae una habilidad (**%s**), guarda otra en su árbol (**%s**) y "
        "acaba cada senda en una **habilidad final**: eliges una de las tres para **%s**. Las teclas se cambian en Controles, en «Forja: clases».",
        "A **class** is your way of fighting. The first one is free: with the button below or with the **%s** key. "
        "Without a class you play as always, with no bonuses and no drawbacks. Your class, level and tree **survive "
        "death**. Each class comes with one skill (**%s**), keeps another in its tree (**%s**) and ends each path in an "
        "**ultimate**: you pick one of the three for **%s**. The keys "
        "can be changed in Controls, under \"Forja: Classes\".",
    ),
    "gui.forja.libro.clases.niveles": (
        "Nivel máximo %s. Los niveles dan %s puntos para el árbol (uno o dos por nivel) y los **hitos** %s más: jefes, "
        "el primer campeón, el Nether, el End, la forja... De los hitos solo se gastan %s por nivel de clase, y se quedan "
        "aunque cambies de clase. En el tope son %s puntos, y el árbol entero cuesta %s: compras el %s %%. Pasar al "
        "siguiente nivel pide %s de experiencia, y %s más por cada nivel que ya tengas por encima del primero.",
        "Max level %s. Levels give %s points for the tree (one or two a level) and **milestones** %s more: bosses, the "
        "first champion, the Nether, the End, the forge... Only %s milestone points can be spent per class level, and "
        "they stay if you change class. At the top that is %s points, and the whole tree costs %s: you buy %s %%. The "
        "next level takes %s experience, plus %s more for each level you already have past the first.",
    ),
    "gui.forja.libro.clases.arbol": (
        "**El árbol** (tecla del árbol): un origen, un núcleo, tres ramas que acaban en dos **claves** cada una (solo "
        "una de las dos), tres sendas que acaban cada una en una **habilidad final** (solo se elige una) y un **puente** a "
        "otra clase, y la **forja**, igual en todos "
        "los árboles. Solo se aprende lo que toca algo aprendido. Los nodos cuestan %s, las claves %s y lo que hay tras "
        "un puente %s. Arrastra para moverte, la rueda acerca, «Probar» deja planear sin gastar.",
        "**The tree** (the tree key): an origin, a core, three branches that end in two **keystones** each (only one "
        "of the two), three paths that each end in an **ultimate** (only one can be chosen) and a **bridge** to another "
        "class, and the **forge**, the same in "
        "every tree. You only learn what touches something learned. Nodes cost %s, keystones %s and what lies past a "
        "bridge %s. Drag to move, the wheel zooms, \"Try\" lets you plan without spending.",
    ),
    "gui.forja.libro.clases.experiencia": (
        "**Experiencia de clase:** matar monstruos (más si son veteranos, élites, campeones o jefes, y un 50%% más si "
        "los matas a la manera de tu clase) y lo propio de cada una: paradas y posturas rotas para el Guerrero, esquivas "
        "perfectas y puñaladas para el Asesino, daño aguantado para el Tanque, hechizos que aciertan para el Mago, vida "
        "curada a otros para el Curandero, flechas que aciertan (más cuanto más lejos) para el Arquero.",
        "**Class experience:** killing monsters (more for veterans, elites, champions and bosses, and 50%% more when "
        "you kill them your class's way) and each class's own deeds: parries and broken postures for the Warrior, "
        "perfect dodges and backstabs for the Assassin, damage taken for the Tank, spells that land for the Mage, "
        "health healed on others for the Healer, arrows that hit (more the farther they fly) for the Archer.",
    ),
    "gui.forja.libro.clases.habilidades": ("Habilidades: **%s** (%s), **%s** (%s, del árbol) y una final de tres: **%s**, **%s** o **%s** (%s).",
                                           "Skills: **%s** (%s), **%s** (%s, from the tree) and one ultimate of three: **%s**, **%s** or **%s** (%s)."),
    "gui.forja.libro.clases.vela": (
        "El reinicio barato: quita hasta **%s puntos** de nodos del borde de lo aprendido (los que no dejan a otro "
        "suelto). Clic derecho abre el árbol: marcas los nodos y «Quitar» gasta la vela. El árbol entero, o cambiar de "
        "clase, sigue siendo cosa del Medallón.",
        "The cheap reset: it takes up to **%s points** of nodes off the edge of what you have (the ones that leave no "
        "other hanging). Right-click opens the tree: mark the nodes and \"Remove\" spends the candle. The whole tree, or "
        "another class, is still the Medallion's.",
    ),
    "gui.forja.clase.tecla": ("[%s]", "[%s]"),
    "gui.forja.clase.boton_elegir": ("Elegir", "Choose"),
    "gui.forja.clase.boton_cambiar": ("Cambiar", "Change"),
    "gui.forja.clase.cambiada": ("Cambiaste de clase: ahora eres %s y empiezas en el nivel 1.",
                                 "You changed class: you are now %s, starting from level 1."),
    "gui.forja.clase.reiniciada": ("Sigues siendo %s: tus talentos se borran y todos tus puntos vuelven. Conservas el nivel.",
                                   "You are still %s: your talents are wiped and every point comes back. You keep your level."),
    "gui.forja.clase.dano": ("Daño:", "Damage:"),
    "gui.forja.clase.golpe": ("%s %s", "%s %s"),
    "gui.forja.clase.golpe.melee": ("cuerpo a cuerpo", "melee"),
    "gui.forja.clase.golpe.projectile": ("proyectiles", "projectiles"),
    "gui.forja.clase.golpe.magic": ("magia", "magic"),
    "gui.forja.clase.sube": ("%s sube al nivel %s · puntos sin gastar: %s", "%s reaches level %s · unspent points: %s"),
    "gui.forja.clase.aviso": ("Aún no tienes clase. Elige una en el capítulo «Clases» de la guía (%s) o con la tecla %s.",
                              "You have no class yet. Pick one in the guide's \"Classes\" chapter (%s) or with the %s key."),
    "gui.forja.clase.falta_emblema": ("Para cambiar de clase necesitas un Medallón del olvido", "You need a Medallion of Oblivion to change class"),
    "gui.forja.clase.nivel": ("Nivel %s · %s/%s de experiencia", "Level %s · %s/%s XP"),
    "gui.forja.clase.nivel_maximo": ("Nivel %s · máximo", "Level %s · max"),
    "gui.forja.clase.puntos": ("Puntos: %s", "Points: %s"),
    "gui.forja.clase.toast.nivel": ("%s · nivel %s", "%s · level %s"),
    "gui.forja.clase.toast.maximo": ("¡Nivel máximo alcanzado!", "Max level reached!"),
    "gui.forja.clase.toast.elegida": ("Clase elegida. Tu árbol: %s", "Class chosen. Your tree: %s"),
    "gui.forja.habilidad.espera": ("Espera: %s s", "Cooldown: %s s"),
    "gui.forja.habilidad.esperando": ("%s aún no está lista: %s s", "%s is not ready yet: %s s"),
    "gui.forja.habilidad.sin_clase": ("No tienes clase: elige una con la tecla %s", "You have no class: pick one with the %s key"),
    "gui.forja.habilidad.sin_objetivo": ("No hay ningún monstruo a la vista", "No monster in sight"),
    "gui.forja.habilidad.sin_aliado": ("No hay ningún aliado a la vista", "No ally in sight"),
    "gui.forja.habilidad.sin_pieza": ("Necesitas una pieza forjada en la mano", "You need a forged piece in your hand"),
    "gui.forja.talento.aprender": ("Clic para aprender", "Click to learn"),
    "gui.forja.talento.aprendido": ("Talento aprendido: %s", "Talent learned: %s"),
    "gui.forja.talento.necesita": ("Necesita: %s", "Requires: %s"),
    "gui.forja.talento.necesita_rama": ("Necesita un talento de nivel %s de cualquier rama", "Requires a tier %s talent from any branch"),
    "gui.forja.talento.posicion": ("%s · nivel %s · coste %s", "%s · tier %s · cost %s"),
    "gui.forja.talento.posicion_habilidad": ("Habilidad II · coste %s", "Skill II · cost %s"),
    "gui.forja.talento.evasion": ("¡Evasión! El proyectil no te toca", "Evasion! The projectile misses you"),
    "gui.forja.talento.ultimo_bastion": ("¡Último bastión! Sigues en pie", "Last Stand! You are still standing"),
    # "gui.forja.talento.no." + ClassProgress.Refusal. The tree passes the points to "points", the command
    # passes nothing, so it takes no argument.
    "gui.forja.talento.no.no_class": ("No tienes clase", "You have no class"),
    "gui.forja.talento.no.other_class": ("Es un talento de otra clase", "This talent belongs to another class"),
    "gui.forja.talento.no.already": ("Ya aprendido", "Already learned"),
    "gui.forja.talento.no.prerequisite": ("Aún no cumples lo que pide", "Requirements not met yet"),
    "gui.forja.talento.no.points": ("No tienes puntos suficientes", "Not enough points"),
    "commands.forja.clase.ninguna": ("%s no tiene clase", "%s has no class"),
    "commands.forja.clase.desconocida": ("No existe esa clase o ese talento", "No such class or talent"),
    "key.category.forja.clases": ("Forja: clases", "Forja: Classes"),
    "key.forja.clase_arbol": ("Árbol de clase / elegir clase", "Class tree / choose class"),
    "key.forja.habilidad_1": ("Habilidad de clase I", "Class skill I"),
    "key.forja.habilidad_2": ("Habilidad de clase II", "Class skill II"),
    # The old crafted emblem: no recipe any more, kept so the ones already made still work.
    "item.forja.emblema_del_olvido": ("Emblema del olvido (antiguo)", "Emblem of Oblivion (old)"),
    "item.forja.emblema_del_olvido.desc": ("Clic derecho: cambia de clase (empiezas en el nivel 1). Ya no se fabrica: ahora se forja el Medallón del olvido.",
                                           "Right-click: change class (you start at level 1). No longer made: forge the Medallion of Oblivion instead."),
    "item.forja.medallon_del_olvido": ("Medallón del olvido", "Medallion of Oblivion"),
    "item.forja.medallon_del_olvido.desc": ("Clic derecho: cambia de clase y empiezas en el nivel 1. En tu misma clase, reinicia los talentos.",
                                            "Right-click: change class, starting at level 1. On your own class, resets your talents."),
    "gui.forja.reliquia.nucleo": ("%s pide un núcleo de %s", "%s needs a %s core"),
    "advancements.forja.clase.title": ("Un camino propio", "A Path of Your Own"),
    "advancements.forja.clase.description": ("Elige tu primera clase", "Choose your first class"),
    "gui.forja.libro.cap.clases": ("Clases", "Classes"),
    "gui.forja.libro.clases.boton_elegir": ("Elegir clase", "Choose a class"),
    "gui.forja.libro.clases.boton_arbol": ("Ver tu árbol", "See your tree"),
    "gui.forja.libro.clases.dano": ("**Daño:** cuerpo a cuerpo %s · proyectiles %s · magia %s. Se multiplica encima de todo lo demás.",
                                    "**Damage:** melee %s · projectiles %s · magic %s. It multiplies on top of everything else."),
    "gui.forja.libro.clases.medallon": (
        "Se **forja** en la estrella de cualquier mesa de forja, como un arma: un **núcleo de %s**, un engaste y "
        "una cadena, cortados en la mesa de piezas, y un golpe de martillo. El engaste y la cadena pueden ser de "
        "cualquier material y le dan su color. No tiene estadísticas ni se desgasta: se gasta al cambiar.",
        "It is **forged** on the star of any forge table, like a weapon: a **%s core**, a setting and a chain, cut "
        "at the parts table, and one hammer stroke. The setting and the chain can be any material and give it its "
        "colour. It has no stats and does not wear: it is spent when you change.",
    ),
    "gui.forja.libro.clases.cambio": (
        "**Cambiar de clase** cuesta un Medallón del olvido: clic derecho abre la elección y el medallón se "
        "gasta solo al confirmar. La clase nueva **empieza en el nivel 1**, sin experiencia ni talentos. Elegir "
        "tu misma clase no es un cambio: conservas el nivel, los talentos se borran y **todos los puntos "
        "vuelven**. Los fragmentos de eco de las ciudades antiguas son lo que lo hacen caro. Los Emblemas del "
        "olvido de antes siguen sirviendo, pero ya no se fabrican.",
        "**Changing class** costs a Medallion of Oblivion: right-click opens the choice and the medallion is "
        "spent only when you confirm. The new class **starts at level 1**, with no experience and no talents. "
        "Picking your own class is not a change: you keep your level, your talents are wiped and **every point "
        "comes back**. The echo shards from the ancient cities are what make it dear. The old Emblems of "
        "Oblivion still work, but are no longer made.",
    ),
    "gui.forja.libro.farol.curandero": (
        "Cualquiera puede usar el farol, pero en manos de un **Curandero** cura un 50%% más, y sus talentos "
        "(Manos cálidas, Milagro) lo suben más aún; Renuevo, Bendición, Purificar y Vínculo también van en "
        "sus curas. Además, mientras eres Curandero, el proyectil del báculo y el área y la runa del grimorio "
        "**curan** a los aliados que tocan una décima parte de su daño, y al Curandero un tercio de lo que "
        "curan a los demás; a los monstruos sí los dañan, pero a un **tercio**.",
        "Anyone can use the lantern, but in a **Healer's** hands it heals 50%% more, and their talents (Warm "
        "Hands, Miracle) raise it further; Renewal, Blessing, Cleanse and Bond ride on their heals too. What's "
        "more, while you are a Healer the staff's bolt and the tome's area and rune **heal** the allies they "
        "touch for a tenth of their damage, and the Healer for a third of what they heal others; they do hurt "
        "monsters, but at a **third**.",
    ),
    # Not read yet: "gui.forja.libro.farol" is the caged ember wisp's text in the bestiary, and the classes
    # chapter asks for the same key with four numbers. This is the lantern's text for when it gets its own key.
    "gui.forja.libro.clases.farol": (
        "El arma de curación: núcleo, cadena y mango, en cualquier mesa de forja; el núcleo decide el color y "
        "la fuerza. **Toque:** un rayo de hasta %s bloques cura al primer aliado que alcanza (a ti no). "
        "**Carga:** un anillo de %s bloques, %s más con la carga llena, cura a todos los aliados de dentro y "
        "a ti el %s%%. Aliados son los jugadores, tus animales domados y tu equipo; nunca un monstruo. Gasta "
        "maná como el báculo: el toque es barato y la carga cuesta más. Como arma de golpe no sirve.",
        "The healing weapon: core, chain and handle, at any forge table; the core sets its colour and its "
        "strength. **Tap:** a beam of up to %s blocks heals the first ally it reaches (not you). **Charge:** a "
        "ring of %s blocks, %s more at full charge, heals every ally inside and you for %s%%. Allies are "
        "players, your tamed animals and your team; never a monster. It spends mana like the staff: a tap is "
        "cheap and a charge costs more. As a melee weapon it is useless.",
    ),
})

# name, motto, description, the three branches
CLASSES = {
    "guerrero": (("Guerrero", "Warrior"), ("Cuerpo a cuerpo y aguante", "Melee and endurance"),
                 ("Aguanta, para y rompe. Algo más de vida y de daño, mucha más estamina, más daño de postura y una ventana de parada más generosa. La magia le sale floja (×0,4).",
                  "Endure, parry, break. A little more health and damage, much more stamina, more posture damage and a more forgiving parry window. Magic comes out weak (×0.4)."),
                 (("Aguante", "Endurance"), ("Guardia", "Guard"), ("Quebranto", "Breaker"))),
    "asesino": (("Asesino", "Assassin"), ("Más estamina, más esquiva, menos vida", "More stamina, more dodge, less health"),
                ("Más estamina, más esquiva, un poquito más de daño, bastante menos vida. Esquiva más lejos, más seguido y más barato, y castiga por la espalda. La magia le sale floja (×0,4).",
                 "More stamina, more dodge, a little more damage, a lot less health. Dodges farther, more often and for less, and punishes from behind. Magic comes out weak (×0.4)."),
                (("Sombra", "Shadow"), ("Filo", "Edge"), ("Sigilo", "Stealth"))),
    "tanque": (("Tanque", "Tank"), ("Mucha vida, poca prisa", "Lots of health, no hurry"),
               ("Mucha vida, estamina normal, menos movilidad, esquiva más corta y más lento, y todo su daño ×0,67. A cambio, armadura, aguante al empuje y un escudo que cansa menos.",
                "Lots of health, normal stamina, less mobility, shorter dodges and slower feet, and all its damage ×0.67. In return: armor, knockback resistance and a shield that tires you less."),
               (("Muralla", "Rampart"), ("Coraza", "Plating"), ("Firmeza", "Steadfast"))),
    "mago": (("Mago", "Mage"), ("Báculo y grimorio", "Staff and tome"),
             ("La magia, más fuerte y más seguido: más daño de hechizos, menos espera, más maná y más rápido de recuperar. Lo paga en vida, estamina y daño cuerpo a cuerpo (×0,7).",
              "Magic, harder and sooner: more spell damage, shorter cooldowns, more mana that refills faster. Paid for in health, stamina and melee damage (×0.7)."),
             (("Arcano", "Arcane"), ("Flujo", "Flow"), ("Égida", "Aegis"))),
    "curandero": (("Curandero", "Healer"), ("Su magia cura y apenas hiere", "Their magic heals, and barely harms"),
                  ("El báculo y el grimorio curan a los aliados 1/10 de su daño (y al curandero, un tercio de eso) y dañan a los monstruos a un tercio (×1/3). Cuerpo a cuerpo, la mitad (×0,5). Cura un 50 % más con todo, y su arma es el farol.",
                   "The staff and the tome heal allies for 1/10 of their damage (and the healer for a third of that) and hurt monsters at a third (×1/3). In melee, half (×0.5). Heals 50 % more with everything, and the lantern is their weapon."),
                  (("Sanación", "Healing"), ("Amparo", "Shelter"), ("Fe", "Faith"))),
    "arquero": (("Arquero", "Archer"), ("Arco, ballesta y buenas piernas", "Bow, crossbow and quick feet"),
                ("Cazador a distancia: más daño de proyectiles, tensado más rápido, más velocidad y mejor esquiva, y cae mejor. Un poco menos de vida, y cuerpo a cuerpo pega menos (×0,7).",
                 "A hunter at range: more projectile damage, faster draw, more speed and better dodges, and lands softer. A little less health, and weaker in melee (×0.7)."),
                (("Puntería", "Aim"), ("Tensión", "Draw"), ("Viento", "Wind"))),
}
# "gui.forja.clase.stat." + ClassStat: the formatted number ("+15 %", "−2", "+3") is the argument
CLASS_STATS = {
    "max_health": ("Vida %s", "Health %s"),
    "move_speed": ("Velocidad %s", "Speed %s"),
    "melee_damage": ("Daño cuerpo a cuerpo %s", "Melee damage %s"),
    "armor": ("Armadura %s", "Armor %s"),
    "toughness": ("Dureza de armadura %s", "Armor toughness %s"),
    "knockback": ("Resistencia al empuje %s", "Knockback resistance %s"),
    "mining": ("Velocidad de minado %s", "Mining speed %s"),
    "jump": ("Salto %s", "Jump %s"),
    "sneak_speed": ("Velocidad agachado %s", "Sneaking speed %s"),
    "fall_damage": ("Daño de caída %s", "Fall damage %s"),
    "burning": ("Tiempo ardiendo %s", "Burning time %s"),
    "stamina_max": ("Estamina máxima %s", "Max stamina %s"),
    "stamina_regen": ("Regeneración de estamina %s", "Stamina regeneration %s"),
    "stamina_cost": ("Coste de estamina %s", "Stamina cost %s"),
    "dodge_distance": ("Distancia de esquiva %s", "Dodge distance %s"),
    "dodge_cooldown": ("Espera de esquiva %s", "Dodge cooldown %s"),
    "dodge_cost": ("Coste de esquiva %s", "Dodge cost %s"),
    "dodge_iframes": ("Invulnerabilidad al esquivar %s ticks", "Dodge invulnerability %s ticks"),
    "parry_window": ("Ventana de parada %s ticks", "Parry window %s ticks"),
    "block_cost": ("Estamina al parar con escudo %s", "Shield block stamina %s"),
    "posture": ("Daño de postura %s", "Posture damage %s"),
    "damage_taken": ("Daño recibido %s", "Damage taken %s"),
    "magic_taken": ("Daño mágico recibido %s", "Magic damage taken %s"),
    "fire_taken": ("Daño de fuego y lava %s", "Fire and lava damage %s"),
    "backstab": ("Daño por la espalda %s", "Backstab damage %s"),
    "counter": ("Daño de contraataque %s", "Counterattack damage %s"),
    "staggered_bonus": ("Daño a enemigos aturdidos %s", "Damage to staggered foes %s"),
    "finisher": ("Daño de remate %s", "Finisher damage %s"),
    "execute": ("Daño a enemigos con menos del 35 % de vida %s", "Damage to foes under 35 % health %s"),
    "projectile_damage": ("Daño de proyectiles %s", "Projectile damage %s"),
    "headshot": ("Daño de tiro a la cabeza %s", "Headshot damage %s"),
    "draw_speed": ("Velocidad de tensado %s", "Draw speed %s"),
    "arrow_speed": ("Velocidad de flecha %s", "Arrow speed %s"),
    "spell_damage": ("Daño de hechizos %s", "Spell damage %s"),
    "spell_cooldown": ("Espera de hechizos %s", "Spell cooldown %s"),
    "spell_charge": ("Tiempo de carga %s", "Charge time %s"),
    "charge_bonus": ("Bono de carga completa %s", "Full-charge bonus %s"),
    "healing": ("Curación %s", "Healing %s"),
    "mana_max": ("Maná máximo %s", "Max mana %s"),
    "mana_regen": ("Regeneración de maná %s", "Mana regeneration %s"),
    "spell_cost": ("Coste de maná %s", "Mana cost %s"),
    "forge_window": ("Ventana del golpe perfecto %s", "Perfect-strike window %s"),
    "potential": ("Potencial al forjar %s", "Forging potential %s"),
    "repair": ("Reparación por lingote %s", "Repair per ingot %s"),
    "upgrade_bonus": ("Porcentaje de cada mejora que pones %s", "Share of each upgrade you add %s"),
    "smith_weapon": ("Daño con martillo, mazo, pico y hacha %s", "Hammer, mace, pickaxe and axe damage %s"),
}
# The trees' nodes, skills, regions and milestones: from the tree's file (tools/arboles.py, docs/ARBOLES.md).
TALENTS = {}
SKILLS = {}


# Heavy and light handles and bindings (combat/Grip, 2026-09-29). The numbers come from the Java constants.
GUI.update({
    "tooltip.forja.variante.mango_pesado.gana": (
        "Pesado: %1$s golpe cargado · %2$s postura · %3$s empuje",
        "Heavy: %1$s charged blow · %2$s posture · %3$s knockback"),
    "tooltip.forja.variante.mango_pesado.cuesta": (
        "  a cambio: %4$s velocidad · %5$s estamina por golpe",
        "  in exchange: %4$s speed · %5$s stamina per swing"),
    "tooltip.forja.variante.mango_ligero.gana": (
        "Ligero: %1$s velocidad · %2$s estamina por golpe",
        "Light: %1$s speed · %2$s stamina per swing"),
    "tooltip.forja.variante.mango_ligero.cuesta": (
        "  a cambio: %3$s postura · %4$s empuje · %5$s golpe cargado",
        "  in exchange: %3$s posture · %4$s knockback · %5$s charged blow"),
    "tooltip.forja.variante.atadura_pesada.gana": (
        "Pesada: %1$s durabilidad · %2$s estamina al bloquear",
        "Heavy: %1$s durability · %2$s stamina when blocking"),
    "tooltip.forja.variante.atadura_pesada.cuesta": (
        "  guardia rota %3$s · no suelta la carga · a cambio: %4$s velocidad",
        "  broken guard %3$s · keeps its charge · in exchange: %4$s speed"),
    "tooltip.forja.variante.atadura_ligera.gana": (
        "Ligera: %1$s velocidad", "Light: %1$s speed"),
    "tooltip.forja.variante.atadura_ligera.cuesta": (
        "  a cambio: %2$s durabilidad · guardia rota %3$s",
        "  in exchange: %2$s durability · broken guard %3$s"),
    # Any material makes any variant (Andy, 2026-09-30): the variant is the shape, the material the rest.
    "tooltip.forja.variante.materiales": (
        "De cualquier material: el metal se cuela y lo demás se talla en la mesa de piezas",
        "In any material: metal is poured, everything else is cut at the parts table"),
    "tooltip.forja.variante.peso.mango": (
        "Pesa %1$s veces un mango normal de hierro (material %2$s × forma %3$s)",
        "Weighs %1$s times a plain iron handle (material %2$s × shape %3$s)"),
    "tooltip.forja.variante.peso.atadura": (
        "Pesa %1$s veces una atadura normal de hierro (material %2$s × forma %3$s)",
        "Weighs %1$s times a plain iron binding (material %2$s × shape %3$s)"),
    "tooltip.forja.plantilla.a_la_caja": (
        "Para hacerla de metal: en la caja de colada, con acero refractario, la plantilla se vuelve su molde",
        "To make it in metal: in the casting box, with refractory steel, the template becomes its mould"),
    # The same trades, short, for the forge's stat panel: one line a variant part.
    "gui.forja.variante.corto.mango_pesado": ("Pesado: carga %1$s", "Heavy: charge %1$s"),
    "gui.forja.variante.corto.mango_ligero": ("Ligero: estam. %1$s", "Light: stamina %1$s"),
    "gui.forja.variante.corto.atadura_pesada": ("Remachada: dur. %1$s", "Riveted: dur. %1$s"),
    "gui.forja.variante.corto.atadura_ligera": ("At. ligera: dur. %1$s", "Thin wrap: dur. %1$s"),
    # Short texts for the guide. Where they go in "El yunque" is up to the books' own file (tools/lang_libros.py).
    "gui.forja.libros.variantes.titulo": ("Mangos y ataduras: pesados o ligeros", "Handles and bindings: heavy or light"),
    "gui.forja.libros.variantes": (
        "El mango y la atadura tienen tres formas: la normal, una pesada y una ligera. No son mejoras, son una "
        "elección. El **mango pesado** lleva contrapeso: el golpe cargado pega un 20 % más y el golpe tumba más la "
        "guardia y empuja más, pero el arma pesa más, tarda más en llegar al golpe a plena fuerza y cada golpe "
        "cuesta más estamina. El **mango ligero**, fino o hueco, es al revés: rápido y barato, más flojo. La "
        "**atadura pesada** (remaches y bandas) dura más, abarata los bloqueos, te devuelve antes la guardia rota y "
        "no deja que un golpe de escudo te quite la carga; la **ligera** aligera un poco y dura menos. Cualquier "
        "material sirve para cualquier forma: la forma pone el trato y el material pone lo suyo, como en una pieza "
        "normal (durabilidad, rasgo, potencial) y además el peso. Una pieza pesa lo que su material por su forma: un "
        "mango pesado de roble es un contrapeso modesto y uno ligero de netherita sigue pesando. Prueba.",
        "The handle and the binding come in three shapes: the plain one, a heavy one and a light one. They are not "
        "upgrades, they are a choice. A **heavy handle** has a counterweight: the charged blow hits 20 % harder and "
        "every blow shakes the guard more and throws further, but the weapon weighs more, reaches a full-strength "
        "blow later and every swing costs more stamina. A **light handle**, slim or hollow, is the other way round: "
        "quick and cheap, softer. A **heavy binding** (rivets and bands) lasts longer, makes blocks cheaper, gives a "
        "broken guard back sooner and keeps a shield bash from knocking your charge loose; a **light** one is a "
        "little lighter and wears sooner. Any material makes any shape: the shape sets the trade and the material "
        "brings what it brings to a plain part (durability, trait, potential) and the weight too. A part weighs its "
        "material times its shape: a heavy oak handle is a modest counterweight, and a light netherite one still "
        "weighs. Try them."),
    "gui.forja.libros.variantes.hacer": (
        "Cada material se trabaja como siempre. La madera, el hueso, el cuero, los cristales y lo demás que se talla "
        "se cortan en la mesa de piezas con la plantilla grabada en la forma que quieras. El metal se cuela: lleva "
        "esa plantilla a la caja de colada con acero refractario, sale el molde y lo llenas en la mesa de colada.",
        "Every material is worked as always. Wood, bone, leather, the crystals and everything else that is cut are "
        "cut at the parts table with the template engraved in the shape you want. Metal is poured: take that "
        "template to the casting box with refractory steel, out comes the mould, and you fill it on the casting "
        "table."),
})


# The guide's books (docs/LIBROS_GUIA.md) keep their texts in their own file.
from lang_libros import BOOKS  # noqa: E402
GUI.update(BOOKS)
# The far forges and their alloys (docs/ALEACIONES_NETHER_END.md) keep theirs in their own file too.
from lang_aleaciones import ALEACIONES  # noqa: E402
GUI.update(ALEACIONES)
# The middle tier and the peak alloys (docs/ALEACIONES_CUMBRE.md) too.
from lang_cumbre import CUMBRE  # noqa: E402
GUI.update(CUMBRE)
# Every text of the big class trees comes from their data (tools/arboles_datos.py).
import sys as _sys  # noqa: E402
_sys.path.insert(0, str(Path(__file__).resolve().parent))
from arboles import lang_entries  # noqa: E402
GUI.update(lang_entries())

# The repair kits (forge/RepairKits.java): one per metal, named in build() from the material's own name.
from generate_assets import repair_kit_materials  # noqa: E402
REPAIR_KITS = repair_kit_materials()
GUI.update({
    "tooltip.forja.kit_de_reparacion": ("+%s de uso a piezas cuya parte principal sea de %s",
                                        "+%s durability to pieces whose main part is %s"),
    "tooltip.forja.kit_de_reparacion.uso": ("Ponlo con la pieza en la mesa de crafteo",
                                            "Put it with the piece in a crafting grid"),
    "gui.forja.jei.kit_de_reparacion": (
        "Pon este kit y una pieza forjada en cualquier mesa de crafteo, en cualquier casilla: la pieza sale con "
        "+%s de uso (sin pasar de su máximo) y con todo lo demás igual: piezas, mejoras, potencial, maestría y "
        "nombre. Solo vale si la parte principal de la pieza es de %s: la cabeza de una herramienta, la hoja de "
        "un arma, la placa de una armadura, la que le da nombre.",
        "Put this kit and a forged piece in any crafting grid, in any slots: the piece comes out with +%s "
        "durability (never past its maximum) and everything else the same: parts, upgrades, potential, mastery "
        "and name. It only works if the piece's main part is %s: a tool's head, a weapon's blade, an armour's "
        "plate, the part it is named after."),
})


# Oricalco as a forge material (ForgeMaterial.ORICALCO, docs/HERRERO_DIMENSION.md 1.4). A block of its own, so it
# never collides with other work on the material lines above.
TRAITS["astral"] = (
    ("Astral", "Astral"),
    ("El maná vuelve antes, y más junto al hierro estelar", "Mana comes back sooner, more so beside star iron"),
    ("Cada pieza puesta y cada mano que la empuña: el maná vuelve un 10% más rápido, un 20% si la misma pieza lleva "
     "también hierro o acero estelar. Flechas: te devuelven maná al acertar.",
     "Each piece worn and each hand holding it: mana comes back 10% faster, 20% if the same piece also carries star "
     "iron or star steel. Arrows: give you mana back when they hit."),
)
GUI.update({
    "material.forja.oricalco": ("oricalco", "orichalcum"),
    "material.forja.oricalco.origen": (
        "lingotes de oricalco: un lingote de cada uno de los catorce metales renovables, a calor de fundición en el "
        "crisol de una línea",
        "orichalcum ingots: one ingot of each of the fourteen renewable metals, at molten heat in a foundry line's "
        "crucible"),
    "conjunto.forja.oricalco": ("+25 de maná máximo y +1 de dureza", "+25 max mana and +1 toughness"),
    "gui.forja.jei.oricalco": (
        "El metal del Gremio. Se hace en el crisol de una línea de fundición con un lingote de cada uno de los catorce "
        "metales renovables, y se cuela como cualquier metal: en un molde o un marco sobre una mesa de almas. Vale para "
        "todas las piezas. Su rasgo, Astral: el maná vuelve antes con cada pieza puesta o empuñada, y el doble si la "
        "pieza lleva también hierro o acero estelar. Dos lingotes sobre una perla de ender hacen una perla de oricalco.",
        "The Guild's metal. It is made in a foundry line's crucible from one ingot of each of the fourteen renewable "
        "metals, and it is cast like any metal: in a mould or a frame on a soul table. It works for every part. Its "
        "trait, Astral: mana comes back sooner with each piece worn or held, twice as much if the piece also carries "
        "star iron or star steel. Two ingots over an ender pearl make an orichalcum pearl."),
})


def build(index):
    lang = {}
    for key, names in GUI.items():
        lang[key] = names[index]
    for key, names in MATERIALS.items():
        lang[f"material.forja.{key}"] = names[index]
    for key in REPAIR_KITS:
        metal = lang[f"material.forja.{key}"]
        lang[f"item.forja.kit_de_reparacion_{key}"] = (f"Kit de reparación de {metal}" if index == 0
                                                       else f"{metal.title()} Repair Kit")
    for key, names in PARTS.items():
        lang[f"item.forja.{key}"] = names[index]
        lang[f"part.forja.{key}"] = names[index]
        lang[f"part.forja.{key}.de"] = f"{names[0]} de %s" if index == 0 else f"%s {names[1]}"
    for key, names in TYPES.items():
        lang[f"item.forja.{key}"] = names[index]
        lang[f"item.forja.{key}.de"] = f"{names[0]} de %s" if index == 0 else f"%s {names[1]}"
        lang[f"gui.forja.guia.para.{key}"] = names[index]
    for key, (names, short, long) in TRAITS.items():
        lang[f"trait.forja.{key}"] = names[index]
        lang[f"trait.forja.{key}.desc"] = short[index]
        lang[f"trait.forja.{key}.largo"] = long[index]
    for key, (names, effect) in UPGRADES.items():
        lang[f"upgrade.forja.{key}"] = names[index]
        if effect is not None:
            lang[f"upgrade.forja.{key}.efecto"] = effect[index]
    for key, (names, motto, description, branches) in CLASSES.items():
        lang[f"gui.forja.clase.{key}"] = names[index]
        lang[f"gui.forja.clase.{key}.lema"] = motto[index]
        lang[f"gui.forja.clase.{key}.desc"] = description[index]
    for key, texts in CLASS_STATS.items():
        lang[f"gui.forja.clase.stat.{key}"] = texts[index]
    for key, (names, effect) in list(TALENTS.items()) + list(SKILLS.items()):
        kind = "talento" if key in TALENTS else "habilidad"
        lang[f"gui.forja.{kind}.{key}"] = names[index]
        if effect is not None:
            lang[f"gui.forja.{kind}.{key}.efecto"] = effect[index]
    # Item tags, named after the item each one stands for. A recipe viewer shows a tag by its
    # translation, and without one it prints "forja:acero_refractario" in the middle of a recipe;
    # Fabric warns about exactly that on every start.
    tags = Path("src/main/resources/data/forja/tags/item")
    unnamed = []
    for tag in sorted(tags.glob("*.json")) if tags.is_dir() else []:
        name = lang.get(f"item.forja.{tag.stem}") or lang.get(f"block.forja.{tag.stem}")
        if name is None:
            unnamed.append(tag.stem)
        else:
            lang[f"tag.item.forja.{tag.stem}"] = name
    if unnamed and index == 0:
        print("ITEM TAGS WITH NO ITEM TO NAME THEM AFTER:", *unnamed)
    return lang


def check_java_keys(lang):
    """Every translation key the Java code asks for must exist, or the game shows the raw key."""
    import re

    pattern = re.compile(r'"((?:gui|tooltip|item|part|material|trait|upgrade|commands|advancements|entity|itemGroup|block|filled_map|conjunto|tecnica)\.forja[a-z0-9_.]*)"')
    used = set()
    for source in Path("src").rglob("*.java"):
        for key in pattern.findall(source.read_text(encoding="utf-8")):
            used.add(key)
    # Keys built at runtime end with a dot plus a name the code appends.
    def known(key):
        # A key the code builds by appending an id ends with a dot; then any key under it counts.
        prefix = key if key.endswith(".") else key + "."
        return key in lang or any(k.startswith(prefix) for k in lang)

    missing = sorted(key for key in used if not known(key))
    if missing:
        print("MISSING LANG KEYS:", *missing, sep=chr(10) + "  ")
    return missing


def check_formats(es, en):
    """Texts the game cannot format: a percent sign it will read as a broken placeholder.

    Minecraft reads "%" followed by a letter, or by the end of the text, as a format. "+10% de daño" is
    fine — a space follows — but "por debajo del 30%" is "Unsupported format" and the game falls back to
    printing the template untouched, which goes unnoticed until the text has an argument in it and the
    argument is not filled in. It also has to take the same number of arguments in both languages.
    """
    import re

    spec = re.compile(r"%(?:(\d+)\$)?([A-Za-z%]|$)")
    wrong = []
    for key in es:
        counts = []
        for text in (es[key], en.get(key, "")):
            found = [m.group(2) for m in spec.finditer(text)]
            wrong += [f"{key}: '%{kind}' is not a format" for kind in found if kind not in ("s", "d", "%")]
            counts.append(sum(1 for kind in found if kind in ("s", "d")))
        if counts[0] != counts[1]:
            wrong.append(f"{key}: {counts[0]} arguments in Spanish, {counts[1]} in English")
    if wrong:
        print("BAD FORMATS:", *wrong, sep=chr(10) + "  ")
    return wrong


if __name__ == "__main__":
    LANG.mkdir(parents=True, exist_ok=True)
    for name, index in (("es_mx", 0), ("es_es", 0), ("en_us", 1)):
        (LANG / f"{name}.json").write_text(json.dumps(build(index), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    missing = check_java_keys(build(0))
    check_formats(build(0), build(1))
    print(len(build(0)), "keys", "| all keys used by the code exist" if not missing else f"| {len(missing)} MISSING")
