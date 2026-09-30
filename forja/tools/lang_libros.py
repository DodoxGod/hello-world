"""Texts of the guide's books (docs/LIBROS_GUIA.md): the starter notebook, book I, the library and the shelf.

Kept apart from generate_lang.py, which reads it, so that the books can grow without touching the lines other
work is editing there. Same shape as its GUI dict: key -> (Spanish, English).
"""

BOOKS = {
    # ---- the books themselves: title, one line on what they are about, when their recipe is learned, cover text
    "item.forja.libro_yunque": ("El yunque", "The Anvil"),
    "item.forja.tomo_de_forja": ("Tomo completo de la forja", "Complete Forge Tome"),
    "gui.forja.libros.cuaderno.titulo": ("Cuaderno del aprendiz", "Apprentice's Notebook"),
    "gui.forja.libros.cuaderno.lema": ("Empezar y los libros", "Starting, and the books"),
    "gui.forja.libros.cuaderno.cuando": ("siempre: si lo pierdes, se vuelve a hacer", "always: lose it and make another"),
    "gui.forja.libros.cuaderno.portada": (
        "Forja es un mod de herrería: haces tus herramientas, armas y armaduras pieza a pieza, y cada material cambia lo "
        "que dan. Este cuaderno es corto a propósito. Te enseña a empezar, te dice qué hacer después y cómo se hacen los "
        "demás libros, que irás fabricando cuando te hagan falta.",
        "Forja is a smithing mod: you make your tools, weapons and armour part by part, and every material changes what "
        "they give. This notebook is short on purpose. It shows you how to start, tells you what to do next and how the "
        "other books are made, which you will craft as you need them."),
    "gui.forja.libros.yunque.titulo": ("El yunque", "The Anvil"),
    "gui.forja.libros.yunque.lema": ("Piezas, temple y mejoras", "Parts, quench and upgrades"),
    "gui.forja.libros.yunque.cuando": ("al grabar tu primera plantilla", "when you engrave your first template"),
    "gui.forja.libros.yunque.portada": (
        "El taller de las primeras horas: cortar piezas, forjarlas en la estrella, templar lo que sale caliente y "
        "mejorarlo hasta donde llega la primera mesa. Todo lo que se consulta (cada pieza, cada objeto, cada mejora) "
        "está en el catálogo de la biblioteca.",
        "The workshop of the first hours: cutting parts, forging them at the star, quenching what comes out hot and "
        "upgrading it as far as the first table goes. Everything you look up (every part, every item, every upgrade) is "
        "in the library's catalogue."),
    "gui.forja.libros.combate.titulo": ("El arte del combate", "The Art of Combat"),
    "gui.forja.libros.combate.lema": ("Golpes, magia y enemigos", "Blows, magic and enemies"),
    "gui.forja.libros.combate.cuando": ("al forjar tu primer objeto", "when you forge your first item"),
    "gui.forja.libros.combate.portada": ("Pelear con lo que forjas, y contra lo que viene a por ello.",
                                         "Fighting with what you forge, and against what comes for it."),
    "gui.forja.libros.fundicion.titulo": ("La fundición", "The Foundry"),
    "gui.forja.libros.fundicion.lema": ("Calor, aleaciones y colada", "Heat, alloys and casting"),
    "gui.forja.libros.fundicion.cuando": ("con tu primera mejora", "with your first upgrade"),
    "gui.forja.libros.fundicion.portada": ("De una mesa a una línea: el metal se funde, se guarda y se cuela.",
                                           "From one table to a line: metal is melted, kept and cast."),
    "gui.forja.libros.mesa_mayor.titulo": ("La mesa mayor", "The Greater Table"),
    "gui.forja.libros.mesa_mayor.lema": ("Potencial y maestría", "Potential and mastery"),
    "gui.forja.libros.mesa_mayor.cuando": ("al tener la mesa de forja mayor", "when you have the greater forge table"),
    "gui.forja.libros.mesa_mayor.portada": ("Lo que se hace cuando ya se sabe hacer.", "What you do once you know how."),
    "gui.forja.libros.clases.titulo": ("Clases", "Classes"),
    "gui.forja.libros.clases.lema": ("Las siete clases", "The seven classes"),
    "gui.forja.libros.clases.cuando": ("con tu primera parada perfecta", "with your first perfect parry"),
    "gui.forja.libros.clases.portada": ("Tu forma de pelear, o de forjar.", "Your way of fighting, or of forging."),
    "gui.forja.libros.bastion.titulo": ("El Bastión y el Herrero", "The Bastion and the Smith"),
    "gui.forja.libros.bastion.lema": ("Ruinas y el castillo", "Ruins and the castle"),
    "gui.forja.libros.bastion.cuando": ("al abrir el cofre de una forja abandonada, o comprándoselo al Forjador",
                                        "when you open the chest of an abandoned forge, or by buying it from the Forger"),
    "gui.forja.libros.bastion.portada": ("Salir a buscar: lo que hay ahí fuera, y el castillo del Herrero.",
                                         "Going out to look: what is out there, and the Smith's castle."),
    "gui.forja.libros.cementerio.titulo": ("El Cementerio entre Estrellas", "The Graveyard Among the Stars"),
    "gui.forja.libros.cementerio.lema": ("La dimensión y la pelea", "The dimension and the fight"),
    "gui.forja.libros.cementerio.cuando": ("al encender el portal de la Forja Profunda", "when you light the Deep Forge's portal"),
    "gui.forja.libros.cementerio.portada": ("El final.", "The end."),
    "gui.forja.libros.biblioteca.titulo": ("Biblioteca del herrero", "The Smith's Library"),
    "gui.forja.libros.biblioteca.lema": ("Libros, camino y catálogo", "Books, path and catalogue"),
    "gui.forja.libros.biblioteca.cuando": ("llevando el Cuaderno del aprendiz", "carrying the Apprentice's Notebook"),
    "gui.forja.libros.biblioteca.portada": (
        "Todos los libros de la forja en un sitio: los que llevas se abren desde aquí, y de los demás ves la receta y "
        "cuándo la aprendes. Detrás, el catálogo: cada objeto, pieza, material y mejora, para consultar.",
        "Every book of the forge in one place: the ones you carry open from here, and for the rest you see the recipe "
        "and when you learn it. Behind them, the catalogue: every item, part, material and upgrade, to look things up."),
    "gui.forja.libros.tomo.titulo": ("Tomo completo de la forja", "Complete Forge Tome"),
    "gui.forja.libros.tomo.lema": ("Toda la guía", "The whole guide"),
    "gui.forja.libros.tomo.cuando": ("solo en creativo", "creative only"),
    "gui.forja.libros.tomo.portada": ("Toda la guía en un volumen.", "The whole guide in one volume."),

    # ---- chapters and sections
    "gui.forja.libro.cap.bienvenida": ("Bienvenido a la forja", "Welcome to the Forge"),
    "gui.forja.libro.cap.primeras_mesas": ("Tus dos primeras mesas", "Your First Two Tables"),
    "gui.forja.libro.cap.teclas": ("Tus teclas", "Your Keys"),
    "gui.forja.libro.cap.estanteria": ("Los libros", "The Books"),
    "gui.forja.libro.cap.yunque_sabes": ("Lo que ya sabes", "What You Know"),
    "gui.forja.libro.cap.cortar": ("Corta las piezas", "Cutting Parts"),
    "gui.forja.libro.cap.estrella": ("La estrella", "The Star"),
    "gui.forja.libro.cap.mejorar": ("Mejorar", "Upgrading"),
    "gui.forja.libro.cap.desarmar": ("Desarmar y reparar", "Salvage and Repair"),
    "gui.forja.libro.cap.yunque_siguiente": ("Siguiente", "Next"),
    "gui.forja.libro.cap.catalogo": ("Catálogo", "Catalogue"),
    "gui.forja.libro.cap.mesa_mayor": ("La mesa mayor", "The Greater Table"),
    "gui.forja.libro.seccion.cuaderno": ("Empezar", "Starting"),
    "gui.forja.libro.seccion.camino": ("El camino y los libros", "The path and the books"),
    "gui.forja.libro.seccion.yunque_taller": ("El taller", "The workshop"),
    "gui.forja.libro.seccion.yunque_mejorar": ("Mejorar", "Upgrading"),
    "gui.forja.libro.seccion.estanteria": ("Estantería", "Shelf"),
    "gui.forja.libro.seccion.catalogo_objetos": ("Catálogo: objetos y piezas", "Catalogue: items and parts"),
    "gui.forja.libro.seccion.catalogo_materiales": ("Catálogo: materiales y rasgos", "Catalogue: materials and traits"),
    "gui.forja.libro.seccion.catalogo_mejoras": ("Catálogo: mejoras y colores", "Catalogue: upgrades and colours"),

    # ---- the notebook
    "gui.forja.libros.bienvenida": (
        "Aquí nada sale de la mesa de crafteo hecho y derecho. Una **plantilla** te da una **pieza**, tres piezas te dan un "
        "**objeto**, y el objeto es de los materiales que pusiste: una cabeza de diamante sobre un mango de hueso es otra "
        "herramienta que una de hierro sobre madera.",
        "Nothing here comes off the crafting table ready-made. A **template** gives you a **part**, three parts give you an "
        "**item**, and the item is made of the materials you put in: a diamond head on a bone handle is a different tool "
        "from an iron one on wood."),
    "gui.forja.libros.bienvenida.crece": (
        "Y lo que forjas crece contigo: se mejora, se templa, gana maestría al usarlo y, si se gasta, se queda roto "
        "esperando a que lo repares, no desaparece.",
        "And what you forge grows with you: it is upgraded, quenched, gains mastery as you use it and, when it wears out, "
        "it stays broken waiting to be mended; it does not vanish."),
    "gui.forja.libros.bienvenida.libros": (
        "No hace falta saberlo todo al principio. Cada parte del mod tiene su **libro**, y cada libro se fabrica con un "
        "libro y algo de lo que explica. Su receta la aprendes sola cuando llegas ahí. Las encontrarás todas en «Los "
        "libros», al final de este cuaderno.",
        "You do not need to know it all at the start. Each part of the mod has its **book**, and each book is crafted from a "
        "book and something out of what it explains. You learn its recipe on your own when you get there. You will find "
        "them all under \"The Books\", at the end of this notebook."),
    "gui.forja.libros.primeras_mesas.yunque": (
        "En cuanto grabes tu primera plantilla aprenderás la receta de **El yunque**, el libro de todo lo que viene "
        "después: cortar, forjar, templar y mejorar.",
        "As soon as you engrave your first template you will learn the recipe for **The Anvil**, the book of everything "
        "that comes next: cutting, forging, quenching and upgrading."),
    "gui.forja.libros.teclas.intro": ("Las teclas de Forja, tal como las tienes ahora:", "Forja's keys, as you have them bound now:"),
    "gui.forja.libros.teclas.g": (
        "Con este cuaderno encima abre la **biblioteca**: todos tus libros, el camino y el catálogo. Sin él, abre el libro "
        "de la forja que lleves en la mano.",
        "With this notebook on you it opens the **library**: all your books, the path and the catalogue. Without it, it "
        "opens the forge book in your hand."),
    "gui.forja.libros.teclas.esquivar": (
        "**Esquivar**: un salto corto hacia donde te mueves, que gasta estamina. Justo antes de un golpe es una esquiva "
        "perfecta.",
        "**Dodge**: a short hop the way you are moving, which costs stamina. Right before a blow lands it is a perfect "
        "dodge."),
    "gui.forja.libros.teclas.clases": (
        "Las **clases**: tu árbol de talentos, o elegir clase si aún no tienes. %s y %s lanzan sus dos habilidades. Todo "
        "eso lo explica el libro de clases.",
        "**Classes**: your talent tree, or choosing a class if you have none yet. %s and %s use its two skills. The book of "
        "classes explains it all."),
    "gui.forja.libros.teclas.cambiar": ("Todas se cambian en Opciones, Controles.", "All of them can be changed in Options, Controls."),
    "gui.forja.libros.estanteria.intro": (
        "Siete libros después de este. Cada uno se hace en la mesa de crafteo con **un libro y un ingrediente**, y la mesa "
        "no te lo deja hacer hasta que **aprendes su receta**, que llega sola con el progreso. Aquí tienes cada uno: qué "
        "cuenta, su receta y cuándo la aprendes.",
        "Seven books after this one. Each is made at the crafting table from **a book and one ingredient**, and the table "
        "will not make it until you **learn its recipe**, which comes on its own as you progress. Here is each of them: "
        "what it covers, its recipe and when you learn it."),
    "gui.forja.libros.estanteria.biblioteca": (
        "Tus libros. Los que llevas encima se abren con un clic; de los demás tienes la receta y cuándo la aprendes.",
        "Your books. The ones you carry open with a click; for the others you have the recipe and when you learn it."),
    "gui.forja.libros.estanteria.perdido": ("Y si pierdes este cuaderno, se hace otro igual:", "And if you lose this notebook, you can make another:"),

    # ---- book I
    "gui.forja.libros.yunque_sabes": (
        "Ya tienes la mesa de piezas, la mesa de forja y una plantilla grabada: lo contó el Cuaderno del aprendiz. Este "
        "libro sigue desde ahí.",
        "You already have the parts table, the forge table and an engraved template: the Apprentice's Notebook told you "
        "how. This book carries on from there."),
    "gui.forja.libros.yunque_sabes.ruta": (
        "Cuatro pasos del camino se aprenden aquí: cortar una pieza, forjarla, templarla y mejorarla. Cada uno está en su "
        "capítulo, y la portada los va marcando.",
        "Four steps of the path are learned here: cut a part, forge it, quench it and upgrade it. Each one has its chapter, "
        "and the cover ticks them off."),
    "gui.forja.libros.cortar.rasgo": (
        "Cada material lleva su **rasgo**, y la pieza lo pasa al objeto. Pasa el ratón por una pieza para ver sus "
        "números; el resto de piezas, materiales y rasgos están en el catálogo de la biblioteca.",
        "Every material carries its **trait**, and the part passes it on to the item. Hover over a part to see its "
        "numbers; the rest of the parts, materials and traits are in the library's catalogue."),
    "gui.forja.libros.mejorar.tope": (
        "Esta mesa sube cada mejora hasta el **%s%%**. Lo que falta hasta el **%s%%** es trabajo de la mesa de forja "
        "mayor, y cuánto aguanta cada pieza lo decide su potencial: lo cuenta el libro de la mesa mayor.",
        "This table takes each upgrade up to **%s%%**. The rest of the way to **%s%%** is work for the greater forge table, "
        "and how much each piece can take is decided by its potential: the book of the greater table explains it."),
    "gui.forja.libros.mejorar.libros": (
        "Un **libro encantado** en una punta se vuelve la mejora que le corresponde, y un **orbe de mejora** le suma la "
        "suya. Dos mejoras que se llevan bien, las dos al 50%% o más, pueden despertar una **sinergia**.",
        "An **enchanted book** on a point becomes the matching upgrade, and an **upgrade orb** adds its own. Two upgrades "
        "that get on, both at 50%% or more, can wake a **synergy**."),
    "gui.forja.libros.desarmar": (
        "En la pestaña **Desarmar** de la mesa de piezas recuperas las piezas de un objeto que ya no quieres. Si la cabeza "
        "o la placa está muy gastada, esa se pierde. Sus mejoras salen en orbes.",
        "In the parts table's **Salvage** tab you get back the parts of an item you no longer want. If the head or plate is "
        "badly worn, that one is lost. Its upgrades come out as orbs."),
    "gui.forja.libros.reparar": (
        "Para repararlo, ponlo en el centro de la estrella y su material en una punta. También sirven un kit de reparación "
        "o un lingote de temple.",
        "To mend it, put it in the middle of the star and its material on a point. A repair kit or a temper ingot also "
        "does it."),
    "gui.forja.libros.yunque_siguiente": (
        "Con esto ya te vales en el taller. Lo siguiente va por dos lados: pelear con lo que haces, y el calor que convierte "
        "una mesa en una fundición. Sus libros, y cuándo aprendes a hacerlos:",
        "With this you can hold your own in the workshop. What comes next goes two ways: fighting with what you make, and "
        "the heat that turns a table into a foundry. Their books, and when you learn to make them:"),

    # ---- the library and the cards
    "gui.forja.libros.catalogo": (
        "Lo que sigue no es para leerlo de corrido: son tablas para consultar. Cada objeto con sus piezas, cada pieza, cada "
        "material con su rasgo y cada mejora con su receta.",
        "What follows is not for reading straight through: it is tables to look things up in. Every item with its parts, "
        "every part, every material with its trait and every upgrade with its recipe."),
    "gui.forja.libros.catalogo.como": (
        "Pasa el ratón por encima de cualquier cosa para ver todos sus números, y usa las pestañas de la derecha para saltar "
        "de una tabla a otra.",
        "Hover over anything to see all its numbers, and use the tabs on the right to jump from one table to another."),
    "gui.forja.libros.carta.pronto": ("En preparación: se aprenderá %s.", "Being written: it will be learned %s."),
    "gui.forja.libros.carta.abrir": ("Lo llevas: %s páginas, unos %s min. Clic para abrirlo.",
                                     "You carry it: %s pages, about %s min. Click to open it."),
    "gui.forja.libros.carta.tienes": ("Lo llevas encima.", "You are carrying it."),
    "gui.forja.libros.carta.hazlo": ("Ya sabes hacerlo: en la mesa de crafteo.", "You know how to make it: at the crafting table."),
    "gui.forja.libros.carta.aprende": ("Su receta se aprende %s.", "Its recipe is learned %s."),
    "gui.forja.libros.receta": ("Receta: %s y %s", "Recipe: %s and %s"),
    "gui.forja.libros.en_libro": ("En «%s»", "In \"%s\""),
    "gui.forja.libros.info.cuaderno": ("Para empezar · %2$s págs. · %3$s min", "To start with · %2$s pages · %3$s min"),
    "gui.forja.libros.info.biblioteca": ("%2$s págs. con el catálogo", "%2$s pages with the catalogue"),
    "gui.forja.libros.info.libro": ("Libro %1$s · %2$s págs. · %3$s min", "Book %1$s · %2$s pages · %3$s min"),
    "gui.forja.libros.leido": ("leído %s%%", "%s%% read"),
    "gui.forja.libros.pasos_aqui": ("Tus pasos en este libro: %s de %s", "Your steps in this book: %s of %s"),
    "gui.forja.libros.seguir": ("Seguir leyendo en la página %s", "Go on reading at page %s"),
    "gui.forja.libros.aviso_mundo_viejo": (
        "La guía de forja ahora es el Cuaderno del aprendiz. Los demás libros se fabrican: el cuaderno te dice cómo, y "
        "con él encima la G abre la biblioteca.",
        "The forge guide is now the Apprentice's Notebook. The other books are crafted: the notebook tells you how, and "
        "with it on you G opens the library."),

    # ---- the path: its tenth step, and links that cross from one book to another
    "gui.forja.camino.leer_libro": ("Léelo en «%s», en «%s»", "Read it in \"%s\", in \"%s\""),
    "gui.forja.camino.leer_pronto": ("Lo contará «%s», en «%s», aún en preparación", "\"%s\" will tell it, in \"%s\", still being written"),
    "gui.forja.camino.tecnica": ("Elige tu primera técnica", "Choose your first technique"),
    "gui.forja.camino.tecnica.desc": (
        "Forjar, mejorar y cambiar piezas te suben de nivel de herrero. En el nivel **%s** se abre la pestaña "
        "**Técnicas** de la mesa de forja: elige una de tres, para siempre.",
        "Forging, upgrading and swapping parts raise your smith level. At level **%s** the forge table's **Techniques** tab "
        "opens: pick one of three, for good."),
}
