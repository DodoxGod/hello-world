"""Texts of the far forges and their alloys (docs/ALEACIONES_NETHER_END.md).

Kept apart from generate_lang.py and lang_libros.py, which other work is editing, so these can grow without
touching their lines. Same shape as their dicts: full key -> (Spanish, English).
"""

ALEACIONES = {
    # ---- the alloys as items and materials
    "item.forja.fatuo": ("Fatuo", "Wispfire"),
    "material.forja.fatuo": ("fatuo", "wispfire"),
    "conjunto.forja.fatuo": ("+1 de daño y el fuego te dura la mitad", "+1 damage and fire lasts half as long on you"),
    "item.forja.magmacero": ("Magmacero", "Magmasteel"),
    "material.forja.magmacero": ("magmacero", "magmasteel"),
    "conjunto.forja.magmacero": ("+1 de armadura y +20 % de resistencia al empuje", "+1 armour and +20% knockback resistance"),
    "block.forja.costra_de_magma": ("Costra de magma", "Magma Crust"),
    "item.forja.eterio": ("Eterio", "Aetherium"),
    "material.forja.eterio": ("eterio", "aetherium"),
    "conjunto.forja.eterio": (
        "Saltas más, +1 de dureza y el vacío te devuelve el doble de a menudo",
        "You jump higher, +1 toughness and the void hands you back twice as often"),
    "trait.forja.flotante": ("Flotante", "Floating"),
    "trait.forja.flotante.desc": (
        "Lo que golpea se eleva un momento; a quien lo lleva, el vacío lo devuelve",
        "What it strikes rises for a moment; whoever wears it, the void hands back"),
    "trait.forja.flotante.largo": (
        "Armas, herramientas y flechas levantan al objetivo 1 s (Levitación II), como mucho una vez cada 3 s y nunca a un "
        "jefe. Con una pieza o más de armadura, si caes al vacío vuelves al último suelo firme donde estuviste, con caída "
        "lenta, y cada pieza pierde un 10 % de durabilidad; una vez cada 3 minutos (1,5 con el conjunto).",
        "Weapons, tools and arrows lift the target for 1 s (Levitation II), at most once every 3 s and never a boss. With "
        "one armour piece or more, falling into the void puts you back on the last firm ground you stood on, with slow "
        "falling, and every piece loses 10% durability; once every 3 minutes (1.5 with the full set)."),
    "flecha.forja.especial.levita": ("Levita", "Lift"),
    "flecha.forja.especial.levita.desc": ("lo levanta %s s", "lifts it for %s s"),
    "gui.forja.eterio.vacio": ("El vacío te devuelve...", "The void hands you back..."),
    "block.forja.fragua_del_vacio": ("Fragua del vacío", "Void Forge"),
    "gui.forja.fragua_lejana.vacio": ("La fragua del vacío", "The void forge"),
    "gui.forja.fragua_lejana.donde.vacio": (
        "solo en la fragua del vacío de su ruina, en las islas del End",
        "only at the void forge of its ruin, on the End's islands"),
    "gui.forja.jei.fragua_lejana.vacio": ("Fragua del vacío (End)", "Void forge (End)"),
    "gui.forja.fragua_lejana.fria.vacio": (
        "La fragua del vacío está fría. Un %s la encendería.",
        "The void forge is cold. An %s would light it."),
    "gui.forja.fragua_lejana.enciende.vacio": (
        "La fragua del vacío arde violeta... y lo que la guardaba se levanta.",
        "The void forge burns violet... and what guarded it stands up."),
    "gui.forja.fragua_lejana.no_va.vacio": (
        "%s no va en la fragua del vacío: solo funde eterio y eclipse.",
        "%s does not go in the void forge: it only makes aetherium and eclipse."),
    "gui.forja.fragua_lejana.fuera.vacio": (
        "Lejos del End, la fragua del vacío no prende: aquí no funde nada.",
        "Away from the End the void forge will not take: it makes nothing here."),
    "item.forja.nota_fragua_del_vacio": ("Nota de la fragua del vacío", "Void Forge Note"),
    "item.forja.nota_fragua_del_vacio.eterio": (
        "Eterio: 2 acero, 1 caparazón de shulker, 4 coro reventado, 4 piedra del End",
        "Aetherium: 2 steel, 1 shulker shell, 4 popped chorus, 4 end stone"),
    "item.forja.nota_fragua_del_vacio.fuego": (
        "Un ojo de ender la enciende; las perlas de ender la alimentan",
        "An eye of ender lights it; ender pearls feed it"),
    "gui.forja.libros.fraguas_lejanas.vacio": (
        "En las islas altas del End, más allá del dragón, quedan ruinas pequeñas de piedra del End y púrpura con una "
        "fragua del vacío fría sobre un altar de obsidiana. Un ojo de ender la enciende y despierta a sus guardianes: se "
        "levantan dos corazas vacías más. Funciona como la de almas, con perlas de ender de combustible (una por tanda), "
        "y solo funde eterio y eclipse, y solo en el End. Su cofre guarda la receta.",
        "On the End's high islands, past the dragon, stand small ruins of end stone and purpur with a cold void forge on "
        "an obsidian altar. An eye of ender lights it and wakes its guards: two more empty suits stand up. It works like "
        "the soul forge, with ender pearls as fuel (one per batch), makes only aetherium and eclipse, and only in the End. "
        "Its chest keeps the recipe."),
    "trait.forja.volcanico": ("Volcánico", "Volcanic"),
    "trait.forja.volcanico.desc": (
        "La lava que pisas se enfría en costra; sus picos cortan la piedra del Nether más deprisa",
        "Lava you walk on cools into crust; its picks cut Nether stone faster"),
    "trait.forja.volcanico.largo": (
        "Con armadura, la lava que pisas se enfría en costra de magma (radio 1 con una pieza, 2 con dos o tres, 3 con "
        "las cuatro), que aguanta mientras alguien está encima y vuelve a ser lava unos segundos después. Sus picos cortan "
        "netherrack, basalto, piedra negra, magma y ladrillo del Nether un 50 % más deprisa.",
        "Worn, lava you walk on cools into magma crust (radius 1 with one piece, 2 with two or three, 3 with all four) "
        "that holds while anyone stands on it and turns back to lava a few seconds later. Its picks cut netherrack, "
        "basalt, blackstone, magma and nether brick 50% faster."),
    "flecha.forja.especial.magma": ("Magma", "Magma"),
    "flecha.forja.especial.magma.desc": (
        "se le pega: lo prende y lo frena %s s", "sticks to it: sets it alight and slows it for %s s"),

    # ---- the traits
    "trait.forja.espectral": ("Espectral", "Spectral"),
    "trait.forja.espectral.desc": (
        "Llama fatua: quema 1 cada 2 s, también a lo que no arde, y el agua no la apaga",
        "Wisp flame: burns 1 every 2 s, even what does not burn, and water does not put it out"),
    "trait.forja.espectral.largo": (
        "Armas, herramientas y flechas dejan al objetivo con llama fatua 4 s: 1 de daño mágico cada 2 s, también a "
        "blazes, esqueletos wither, ghasts y cubos de magma. La armadura se la pasa 1 s por pieza a quien te pega.",
        "Weapons, tools and arrows leave the target with wisp flame for 4 s: 1 magic damage every 2 s, blazes, wither "
        "skeletons, ghasts and magma cubes included. Armour gives it to whoever hits you, 1 s per piece."),
    "effect.forja.llama_fatua": ("Llama fatua", "Wisp Flame"),
    "flecha.forja.especial.fatua": ("Fatua", "Wisp"),
    "flecha.forja.especial.fatua.desc": (
        "lo deja con llama fatua %s s, aunque no arda", "leaves it with wisp flame for %s s, even if it does not burn"),

    # ---- the soul forge
    "block.forja.fragua_de_almas": ("Fragua de almas", "Soul Forge"),
    "gui.forja.fragua_lejana.almas": ("La fragua de almas", "The soul forge"),
    "gui.forja.fragua_lejana.donde.almas": (
        "solo en la fragua de almas de la Fragua caída, en el Nether",
        "only at the soul forge of the Fallen Forge, in the Nether"),
    "gui.forja.jei.fragua_lejana.almas": ("Fragua de almas (Nether)", "Soul forge (Nether)"),
    "gui.forja.guia.origen.aleacion_fragua": ("Aleación: %s, %s", "Alloy: %s, %s"),
    "gui.forja.fragua_lejana.fria.almas": (
        "La fragua de almas está fría. Una %s la encendería.",
        "The soul forge is cold. A %s would light it."),
    "gui.forja.fragua_lejana.enciende.almas": (
        "La fragua de almas arde azul... y lo que la guardaba despierta.",
        "The soul forge burns blue... and what guarded it wakes."),
    "gui.forja.fragua_lejana.no_va.almas": (
        "%s no va en la fragua de almas: solo funde fatuo, magmacero, espectracero y corazón de volcán.",
        "%s does not go in the soul forge: it only makes wispfire, magmasteel, spectresteel and volcano heart."),

    "gui.forja.fragua_lejana.fuera.almas": (
        "Lejos del Nether, el fuego de almas no prende: aquí no funde nada.",
        "Away from the Nether the soul fire will not take: it makes nothing here."),
    "gui.forja.fragua_lejana.vieja": (
        "La fragua apagada de esta ruina era una fragua de almas: ahora está fría, y una vara de blaze la encendería.",
        "This ruin's dead forge was a soul forge: it is cold now, and a blaze rod would light it."),
    "gui.forja.fragua_lejana.combustible": ("Combustible: %s de %s tandas", "Fuel: %s of %s batches"),
    "gui.forja.fragua_lejana.combustible_lleno": ("No cabe más combustible (%s tandas)", "No room for more fuel (%s batches)"),
    "gui.forja.fragua_lejana.hogar_lleno": ("El hogar está lleno", "The hearth is full"),
    "gui.forja.fragua_lejana.devuelve": ("Sacas lo que había en el hogar", "You take back what was in the hearth"),
    "gui.forja.fragua_lejana.vacia": ("El hogar está vacío", "The hearth is empty"),
    "gui.forja.fragua_lejana.nada": ("nada", "nothing"),
    "gui.forja.fragua_lejana.hogar": ("En el hogar: %s. Combustible: %s (%s)", "In the hearth: %s. Fuel: %s (%s)"),
    "gui.forja.fragua_lejana.funde": ("Funde %s: %s s para la tanda", "Making %s: %s s to the batch"),
    "gui.forja.fragua_lejana.sin_combustible": ("Lista, pero sin combustible: échale %s", "Ready, but out of fuel: feed it %s"),
    "gui.forja.fragua_lejana.falta": ("Para %s falta: %s", "For %s it still needs: %s"),

    # ---- the note in the Fragua caída's chest
    "item.forja.nota_fragua_de_almas": ("Nota de la fragua de almas", "Soul Forge Note"),
    "item.forja.nota_fragua_de_almas.fatuo": (
        "Fatuo: 2 hierro, 1 chatarra de netherita, 4 tierra de almas",
        "Wispfire: 2 iron, 1 netherite scrap, 4 soul soil"),
    "item.forja.nota_fragua_de_almas.magmacero": (
        "Magmacero: 2 acero, 4 basalto, 4 piedra negra, 2 crema de magma",
        "Magmasteel: 2 steel, 4 basalt, 4 blackstone, 2 magma cream"),
    "item.forja.nota_fragua_de_almas.fuego": (
        "Una vara de blaze la enciende; polvo de blaze la alimenta",
        "A blaze rod lights it; blaze powder feeds it"),

    # ---- book III: the alloys only a far forge makes
    "gui.forja.libro.cap.aleaciones_lejanas": ("Aleaciones de fragua", "Forge Alloys"),
    "gui.forja.libros.aleaciones_lejanas": (
        "Hay aleaciones que ninguna mesa y ningún crisol funden, por mucho calor que les des: solo salen de una fragua "
        "lejana, encendida y en su sitio. Se funden igual que se forjan las demás: el lingote va al crisol, a la cuba y "
        "al molde, y sirve para cualquier pieza.",
        "Some alloys no table and no crucible will melt, however much heat you give them: they only come out of a far "
        "forge, lit and where it belongs. After that they are worked like any other: the bar goes to the crucible, the "
        "tank and the mould, and makes any part."),
    "gui.forja.libros.aleaciones_lejanas.rasgo": ("%s: %s", "%s: %s"),

    # ---- book VI: the far forges' ruins
    "gui.forja.libro.cap.fraguas_lejanas": ("Las fraguas lejanas", "The Far Forges"),
    "gui.forja.libros.fraguas_lejanas.almas": (
        "En la Fragua caída del Nether, sobre el altar, hay una fragua de almas fría. Una vara de blaze la enciende, y "
        "al encenderla despierta todo lo que la guarda: los monstruos de alrededor van a por ti y del altar se levantan "
        "dos pavesas más. Encendida, echa los ingredientes con clic derecho y polvo de blaze de combustible (uno por "
        "tanda); cada tanda tarda 10 s y sale encima, o a la tolva que tenga debajo. Clic con la mano vacía para ver qué "
        "le falta; agachado, para sacar lo del hogar. Funde fatuo, magmacero, espectracero y corazón de volcán. No se rompe "
        "y solo arde en el Nether.",
        "In the Nether's Fallen Forge, on the altar, stands a cold soul forge. A blaze rod lights it, and lighting it "
        "wakes everything that guards it: the monsters around come for you and two more wisps rise from the altar. Lit, "
        "put the ingredients in with a right click and blaze powder as fuel (one per batch); each batch takes 10 s and "
        "comes out on top, or into a hopper under it. Click with an empty hand to see what it is missing; crouch to "
        "take the hearth back. It makes wispfire, magmasteel, spectresteel and volcano heart. It cannot be broken and only "
        "burns in the Nether."),
    "gui.forja.libros.ruinas.nether_almas": (
        "Una fortaleza de piedra negra con canales de lava, dos autómatas, dos corazas vacías, tres pavesas y, en el "
        "centro, la fragua de almas fría del Herrero. Encendida, es la única que funde las aleaciones del Nether. Uno de "
        "sus cofres guarda una nota con la receta.",
        "A blackstone fortress with lava channels, two automatons, two empty suits, three wisps and, in the middle, the "
        "Smith's cold soul forge. Lit, it is the only forge that makes the Nether alloys. One of its chests keeps a note "
        "with the recipe."),
}
