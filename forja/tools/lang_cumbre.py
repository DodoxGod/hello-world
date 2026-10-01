"""Texts of the middle tier and the peak alloys (docs/ALEACIONES_CUMBRE.md, 3.1).

Kept apart from generate_lang.py, lang_libros.py and lang_aleaciones.py so these can grow without touching their lines.
Same shape as their dicts: full key -> (Spanish, English). In a text that takes arguments a literal percent sign is written
"%%", and none ends a text or sits before a letter (generate_lang.check_formats).
"""

CUMBRE = {
    # ---- the middle tier: two alloys and one more thing each
    "item.forja.espectracero": ("Espectracero", "Spectresteel"),
    "material.forja.espectracero": ("espectracero", "spectresteel"),
    "conjunto.forja.espectracero": (
        "+2 de dureza, +4 de vida, el fuego te dura menos y el amparo vuelve cada 15 s",
        "+2 toughness, +4 health, fire lasts less on you and the shelter returns every 15 s"),
    "item.forja.corazon_de_volcan": ("Corazón de volcán", "Volcano Heart"),
    "material.forja.corazon_de_volcan": ("corazón de volcán", "volcano heart"),
    "conjunto.forja.corazon_de_volcan": (
        "+2 de daño y el fuego te dura mucho menos", "+2 damage and fire lasts much less on you"),
    "item.forja.eclipse": ("Eclipse", "Eclipse"),
    "material.forja.eclipse": ("eclipse", "eclipse"),
    "conjunto.forja.eclipse": (
        "+20 de maná máximo y andas agachado más deprisa", "+20 max mana and you sneak faster"),
    "item.forja.astralita": ("Astralita", "Astralite"),
    "material.forja.astralita": ("astralita", "astralite"),
    "conjunto.forja.astralita": ("+30 de maná máximo y +1 de dureza", "+30 max mana and +1 toughness"),

    # ---- the traits of the middle tier
    "trait.forja.amparo": ("Amparo", "Sheltering"),
    "trait.forja.amparo.desc": (
        "De vez en cuando, ningún golpe pasa del 40 % de tu vida", "Now and then, no blow takes more than 40% of your health"),
    "trait.forja.amparo.largo": (
        "Una vez cada 20 s (15 con el conjunto), lo que la armadura deja pasar de un golpe no pasa del 40 % de tu vida "
        "máxima, y el que te pegó arde en llama fatua. Vale llevado o en la mano. No contra caídas, el vacío ni lo que la "
        "armadura no lee. Flechas: Resistencia I 1,5 s al que dispara.",
        "Once every 20 s (15 with the full set), what armour lets through of a blow does not pass 40% of your max health, "
        "and whoever struck you catches wispfire. Worn or held. Not against falls, the void or what armour does not read. "
        "Arrows: Resistance I for 1.5 s to the archer."),
    "trait.forja.ardor": ("Ardor", "Ardour"),
    "trait.forja.ardor.desc": ("Quema más cuanto peor te va", "Burns hotter the worse it goes"),
    "trait.forja.ardor.largo": (
        "Armas: +0,5 de daño por cada 20 % de vida que te falta, hasta +2, y por debajo del 60 % el golpe prende 2 s. "
        "Armadura: si un golpe te deja por debajo del 40 %, Resistencia al fuego 6 s (y te apaga con el conjunto), una vez "
        "cada 30 s. Flechas: +0,25 por cada 20 % que te falta.",
        "Weapons: +0.5 damage for every 20% of health you are missing, up to +2, and under 60% the blow sets fire for 2 s. "
        "Armour: a blow that leaves you under 40% gives Fire Resistance for 6 s (and puts you out with the full set), once "
        "every 30 s. Arrows: +0.25 for every 20% missing."),
    "trait.forja.penumbra": ("Penumbra", "Penumbra"),
    "trait.forja.penumbra.desc": ("La magia sale más barata a oscuras", "Magic comes cheaper in the dark"),
    "trait.forja.penumbra.largo": (
        "En la mano, a oscuras: los hechizos cuestan un 15 % menos. Armas: golpear algo a oscuras da 0,5 de maná. "
        "Armadura: herido a oscuras, 0,25 de maná por punto de daño y pieza, hasta 3. Flechas: ciegan 2 s a lo que está a "
        "oscuras.",
        "In hand, in the dark: spells cost 15% less. Weapons: striking something in the dark gives 0.5 mana. Armour: hurt "
        "in the dark, 0.25 mana per point of damage and piece, up to 3. Arrows: blind what stands in the dark for 2 s."),
    "trait.forja.sideral": ("Sideral", "Sidereal"),
    "trait.forja.sideral.desc": (
        "Hechizos más baratos, más aún bajo las estrellas", "Cheaper spells, cheaper still under the stars"),
    "trait.forja.sideral.largo": (
        "En la mano: los hechizos cuestan un 10 % menos, un 20 % de noche a cielo abierto (no se suma con Místico ni "
        "Penumbra: vale el mejor). Herramientas: 0,1 de maná por bloque. Flechas: 1,5 de maná al que dispara y el objetivo "
        "brilla 3 s.",
        "In hand: spells cost 10% less, 20% at night under open sky (does not add to Mystic or Penumbra: the best one "
        "counts). Tools: 0.1 mana per block. Arrows: 1.5 mana to the archer and the target glows for 3 s."),
    "flecha.forja.especial.velo": ("Velo", "Veil"),
    "flecha.forja.especial.velo.desc": ("Resistencia I %s s al que dispara", "Resistance I for %s s to the archer"),
    "flecha.forja.especial.ardor": ("Ardor", "Ardour"),
    "flecha.forja.especial.ardor.desc": (
        "+%s por cada 20 %% de vida que te falta", "+%s for every 20%% of health you are missing"),
    "flecha.forja.especial.sombra": ("Sombra", "Shadow"),
    "flecha.forja.especial.sombra.desc": ("ciega %s s a lo que está a oscuras", "blinds what is in the dark for %s s"),
    "flecha.forja.especial.astro": ("Astro", "Star"),
    "flecha.forja.especial.astro.desc": ("%s de maná y el objetivo brilla", "%s mana and the target glows"),

    # ---- JEI and book III
    "gui.forja.jei.cumbre": ("Crisol de obsidiana con cubas", "Obsidian crucible with tanks"),
    "gui.forja.libro.cap.aleaciones_cumbre": ("Aleaciones cumbre", "Peak Alloys"),
    "gui.forja.libros.aleaciones_cumbre": (
        "Tres metales que solo salen del corazón del Herrero Caído, fundido con dos aleaciones al calor blanco: crisol de "
        "obsidiana en una línea de fundición, el corazón y una aleación en los huecos y la otra en una cuba. Un corazón por "
        "lingote. Las piezas se cuelan en una mesa de almas. El iracero es el que más pega, la égida la que más aguanta y el "
        "arcanio el de la magia y las herramientas.",
        "Three metals that only come from the Fallen Smith's heart, poured with two alloys at white heat: an obsidian "
        "crucible on a foundry line, the heart and one alloy in its slots and the other in a tank. One heart per ingot. "
        "Parts are cast on a soul table. Wrathsteel hits hardest, aegis lasts longest and arcanium is for magic and tools."),
    "gui.forja.libros.aleaciones_cumbre.rasgo": ("%s: %s", "%s: %s"),
    "gui.forja.libros.aleaciones_cumbre.intermedias": ("Las intermedias", "The middle tier"),
    "gui.forja.libros.aleaciones_cumbre.donde": (
        "El espectracero y el corazón de volcán salen de la fragua de almas del Nether; el eclipse, de la fragua del vacío "
        "del End; la astralita, del crisol de obsidiana con cubas, como las cumbre.",
        "Spectresteel and volcano heart come from the Nether's soul forge; eclipse from the End's void forge; astralite "
        "from the obsidian crucible with tanks, like the peak alloys."),
}
