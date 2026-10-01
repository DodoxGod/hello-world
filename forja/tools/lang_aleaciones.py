"""Texts of the far forges and their alloys (docs/ALEACIONES_NETHER_END.md).

Kept apart from generate_lang.py and lang_libros.py, which other work is editing, so these can grow without
touching their lines. Same shape as their dicts: full key -> (Spanish, English).
"""

ALEACIONES = {
    # ---- the alloys as items and materials
    "item.forja.fatuo": ("Fatuo", "Wispfire"),
    "material.forja.fatuo": ("fatuo", "wispfire"),
    "conjunto.forja.fatuo": ("+1 de daño y el fuego te dura la mitad", "+1 damage and fire lasts half as long on you"),

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
        "%s no va en la fragua de almas: solo funde fatuo y magmacero.",
        "%s does not go in the soul forge: it only makes wispfire and magmasteel."),
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
        "le falta; agachado, para sacar lo del hogar. No se rompe y solo arde en el Nether.",
        "In the Nether's Fallen Forge, on the altar, stands a cold soul forge. A blaze rod lights it, and lighting it "
        "wakes everything that guards it: the monsters around come for you and two more wisps rise from the altar. Lit, "
        "put the ingredients in with a right click and blaze powder as fuel (one per batch); each batch takes 10 s and "
        "comes out on top, or into a hopper under it. Click with an empty hand to see what it is missing; crouch to "
        "take the hearth back. It cannot be broken and only burns in the Nether."),
    "gui.forja.libros.ruinas.nether_almas": (
        "Una fortaleza de piedra negra con canales de lava, dos autómatas, dos corazas vacías, tres pavesas y, en el "
        "centro, la fragua de almas fría del Herrero. Encendida, es la única que funde las aleaciones del Nether. Uno de "
        "sus cofres guarda una nota con la receta.",
        "A blackstone fortress with lava channels, two automatons, two empty suits, three wisps and, in the middle, the "
        "Smith's cold soul forge. Lit, it is the only forge that makes the Nether alloys. One of its chests keeps a note "
        "with the recipe."),
}
