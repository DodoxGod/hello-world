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
    "gui.forja.libros.combate.portada": (
        "Pelear con lo que forjas, y contra lo que viene a por ello. Es el libro más largo, así que va en cinco partes, "
        "cada una con su pestaña, y cada una empieza con lo esencial en una página: lee eso y vuelve cuando lo necesites. "
        "El bestiario se escribe solo, criatura a criatura, a medida que las conoces.",
        "Fighting with what you forge, and against what comes for it. It is the longest book, so it comes in five parts, "
        "each with its tab, and each starts with the gist on one page: read that and come back when you need it. The "
        "bestiary writes itself, creature by creature, as you meet them."),
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
    "gui.forja.libros.carta.oculto": ("Libro %s · ???", "Book %s · ???"),
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


# ---- book II, El arte del combate (docs/LIBROS_GUIA.md, 2.3)
BOOKS.update({
    "gui.forja.libro.cap.tu_cuerpo": ("Tu cuerpo", "Your Body"),
    "gui.forja.libro.cap.golpear": ("Golpear", "Striking"),
    "gui.forja.libro.cap.armas": ("Armas y especiales", "Weapons and Specials"),
    "gui.forja.libro.cap.defenderse": ("Defenderte", "Defending"),
    "gui.forja.libro.cap.magia": ("Magia", "Magic"),
    "gui.forja.libro.cap.como_pelean": ("Cómo pelean", "How They Fight"),
    "gui.forja.libro.cap.rangos": ("Los rangos", "The Ranks"),
    "gui.forja.libro.cap.peleas_mundo": ("Peleas del mundo", "Fights of the World"),
    "gui.forja.libro.cap.dificultad": ("La dificultad", "Difficulty"),
    "gui.forja.libro.cap.combate_siguiente": ("Siguiente", "Next"),
    "gui.forja.libro.seccion.combate_cuerpo": ("Tu cuerpo y tus golpes", "Your body and your blows"),
    "gui.forja.libro.seccion.combate_magia": ("Magia y maná", "Magic and mana"),
    "gui.forja.libro.seccion.combate_enemigos": ("Los enemigos", "The enemies"),
    "gui.forja.libro.seccion.combate_cielo": ("El cielo", "The sky"),
    "gui.forja.libro.seccion.combate_bestiario": ("Bestiario", "Bestiary"),
    "gui.forja.libros.en_una_pagina": ("En una página", "On one page"),
    "gui.forja.libros.nuevo": (" · nuevo", " · new"),
    "gui.forja.libros.bestiario.sombras": (
        "Lo que el mod pone en el mundo para pelear. Cada ficha está en sombra hasta que ves a esa criatura por primera "
        "vez; entonces se escribe sola.",
        "What the mod puts in the world to fight. Every page is in shadow until you first see that creature; then it "
        "writes itself."),
    "gui.forja.libros.bestiario.oculto": ("???", "???"),
    "gui.forja.libros.bestiario.sin_ver": ("Aún no la has visto. Su ficha se escribe sola la primera vez que la tengas cerca y a la vista.",
                                           "You have not seen it yet. Its page writes itself the first time it is near and in sight."),

    "gui.forja.libros.combate.cuerpo.resumen": (
        "Todo gasta **estamina**: golpear, esquivar, saltar corriendo, cubrirte y los golpes especiales. Vuelve sola en "
        "cuanto paras un momento. Sin ella pegas flojo y no esquivas. Las armas y la armadura pesadas se notan.",
        "Everything spends **stamina**: striking, dodging, jumping at a run, blocking and special moves. It comes back on "
        "its own as soon as you stop for a moment. Without it you hit soft and cannot dodge. Heavy weapons and armour "
        "show."),
    "gui.forja.libros.combate.estamina.titulo": ("Estamina", "Stamina"),
    "gui.forja.libros.combate.estamina": (
        "La barra tiene **%1$s**. Un golpe cuesta %2$s, una esquiva %3$s y saltar %4$s (%5$s corriendo; sin estamina se "
        "salta igual). Un escudo paga %9$s por cada punto de daño que para, y sin estamina la guardia se rompe. Tras %7$s s "
        "sin gastar vuelve a %6$s por segundo, más despacio con armadura pesada. Con la barra vacía tus golpes hacen el "
        "%8$s%%.",
        "The bar holds **%1$s**. A blow costs %2$s, a dodge %3$s and a jump %4$s (%5$s at a run; out of stamina you still "
        "jump). A shield pays %9$s for every point of damage it stops, and out of stamina the guard breaks. After %7$s s "
        "without spending it comes back at %6$s a second, slower in heavy armour. With the bar empty your blows do "
        "%8$s%%."),
    "gui.forja.libros.combate.especiales": (
        "Los golpes especiales cuestan estamina además de durabilidad, y sin ella no salen: Torbellino %s, Sismo %s, "
        "Siega %s y la embestida de los guanteletes %s.",
        "Special moves cost stamina as well as wear, and without it they do not come out: Whirlwind %s, Quake %s, Reap %s "
        "and the gauntlets' lunge %s."),
    "gui.forja.libros.combate.esquivar.titulo": ("Esquivar", "Dodging"),
    "gui.forja.libros.combate.esquivar": (
        "Con **%1$s**, en el suelo, te apartas de un salto corto hacia donde te mueves (hacia atrás si estás quieto). "
        "Durante %2$s tics nada te toca. Si esquivas un golpe que te iba a dar es una **esquiva perfecta**: el siguiente "
        "golpe en %3$s s es un **contraataque**, un %4$s%% más fuerte, que rompe el doble de postura y te devuelve %5$s "
        "de estamina.",
        "With **%1$s**, on the ground, you hop aside the way you are moving (backwards if you stand still). For %2$s "
        "ticks nothing touches you. Dodge a blow that would have landed and it is a **perfect dodge**: your next blow "
        "within %3$s s is a **counter**, %4$s%% stronger, breaking twice the posture and giving back %5$s stamina."),
    "gui.forja.libros.combate.peso.titulo": ("Peso", "Weight"),
    "gui.forja.libros.combate.peso": (
        "Cada arma pesa según su tipo y sus materiales, de una daga de medio kilo a un martillo de tres y medio: cuanto "
        "más pesa, más tarda en volver al golpe entero. La armadura pesada te frena hasta un %s%% y hace que la estamina "
        "vuelva más despacio; un conjunto ligero te aligera. El peso sale en la descripción de cada pieza.",
        "Every weapon weighs what its type and materials make it, from a half-kilo dagger to a three-and-a-half-kilo "
        "hammer: the heavier, the longer it takes to come back to a full blow. Heavy armour slows you by up to %s%% and "
        "makes stamina return more slowly; a light set lightens you. The weight is on every piece's tooltip."),
    "gui.forja.libros.combate.golpear.resumen": (
        "El golpe que cuenta es el entero. **Mantén** el botón para cargar uno, **encadena** tres enteros para un combo, y "
        "llena la **postura** de un monstruo para dejarlo aturdido y **rematarlo**. Dónde das y con qué, también cuenta.",
        "The blow that counts is the full one. **Hold** the button to charge one, **chain** three full ones for a combo, "
        "and fill a monster's **posture** to stagger it and **finish** it. Where you hit and with what counts too."),
    "gui.forja.libros.combate.cargado.titulo": ("Golpe cargado", "Charged blow"),
    "gui.forja.libros.combate.cargado": (
        "Mantén el botón de atacar tras un golpe: el arma se echa atrás y se carga del todo en unos %1$s s. Al soltarla "
        "pega hasta un %2$s%% más, y a la postura un %3$s%% más. Cuesta %4$s de estamina, y más cuanto más cargues. Se "
        "ve venir, y soltarla demasiado pronto no hace nada.",
        "Hold the attack button after a swing: the weapon draws back and is fully charged in about %1$s s. Let go and it "
        "hits up to %2$s%% harder, and %3$s%% harder on posture. It costs %4$s stamina, more the longer you charge. It can "
        "be seen coming, and letting go too early does nothing."),
    "gui.forja.libros.combate.combo.titulo": ("Combos", "Combos"),
    "gui.forja.libros.combate.combo": (
        "Golpes casi enteros seguidos, con menos de %1$s s entre uno y otro, se encadenan, y el tercero pega un %2$s%% "
        "más y rompe un %3$s%% más de postura. Aporrear el botón rompe la cadena: el ritmo gana a la prisa.",
        "Nearly full blows in a row, less than %1$s s apart, chain together, and the third hits %2$s%% harder and breaks "
        "%3$s%% more posture. Mashing the button breaks the chain: rhythm beats hurry."),
    "gui.forja.libros.combate.postura.titulo": ("Postura y remate", "Posture and finishers"),
    "gui.forja.libros.combate.postura": (
        "Cada monstruo tiene una **postura**, que ves en una barrita bajo la mira. Tus golpes la llenan, los contundentes "
        "más; llena, queda **aturdido** unos %2$s s: no ataca, apenas se mueve y recibe un %1$s%% más. Un golpe cargado o "
        "por la espalda sobre un aturdido es un **remate**: %3$s veces el daño. Aturdir al mismo una y otra vez cuesta "
        "cada vez más.",
        "Every monster has a **posture**, shown as a small bar under your crosshair. Your blows fill it, blunt ones most; "
        "full, it is **staggered** for about %2$s s: it cannot attack, barely moves and takes %1$s%% more. A charged blow "
        "or one from behind on a staggered foe is a **finisher**: %3$s times the damage. Staggering the same one again "
        "and again gets harder each time."),
    "gui.forja.libros.combate.zonas.titulo": ("Dónde das", "Where you hit"),
    "gui.forja.libros.combate.zonas": (
        "Un golpe en la cabeza, con tu hoja o con cualquier flecha, hace un %s%% más. Los golpes al cuerpo se reparten "
        "entre las piezas de armadura de esa altura: por eso un casco no te salva de un tajo a las piernas.",
        "A blow to the head, with your blade or any arrow, does %s%% more. Blows to the body are spread over the pieces of "
        "armour at that height: which is why a helmet does not save you from a cut to the legs."),
    "gui.forja.libros.combate.tipos.titulo": ("Corte, golpe y perforación", "Slash, blunt and pierce"),
    "gui.forja.libros.combate.tipos": (
        "Cada golpe es de **corte**, **contundente** o de **perforación**, y cada arma atraviesa parte de la armadura: la "
        "hoja un %1$s%%, el hacha un %2$s%%, lo contundente un %3$s%% y la lanza un %4$s%%. Las flechas, más cuanto más "
        "rápidas, hasta un %5$s%%.",
        "Every blow is a **slash**, **blunt** or **pierce**, and every weapon goes through part of the armour: a blade "
        "%1$s%%, an axe %2$s%%, blunt weapons %3$s%% and a spear %4$s%%. Arrows more the faster they fly, up to %5$s%%."),
    "gui.forja.libros.combate.resisten": ("Y cada monstruo encaja cada clase a su manera (×1 es normal):",
                                          "And each monster takes each kind its own way (×1 is normal):"),
    "gui.forja.libros.combate.resiste": ("%s: corte %s · golpe %s · perf. %s", "%s: slash %s · blunt %s · pierce %s"),
    "gui.forja.libros.combate.defender.resumen": (
        "Sube el escudo, o la espada, **justo** cuando llega el golpe, no antes: eso es una **parada**. Los monstruos avisan "
        "antes de pegar; aprender su aviso es aprender a parar. Subirlo sin parar a tiempo no abre la ventana.",
        "Raise the shield, or the sword, **right** when the blow arrives, not before: that is a **parry**. Monsters warn "
        "before they strike; learning their warning is learning to parry. Raising it again and again opens no window."),
    "gui.forja.libros.combate.guardia.titulo": ("La guardia del arma", "The weapon's guard"),
    "gui.forja.libros.combate.guardia": (
        "Espada, espadón y daga también se cubren con clic derecho: paran el %1$s%% de lo que para un escudo, y paran de "
        "verdad si el golpe llega en sus primeros %2$s tics. Subir la guardia otra vez antes de %3$s s no abre ventana de "
        "parada. Una parada te devuelve %4$s de estamina.",
        "Sword, greatsword and dagger also guard on right click: they stop %1$s%% of what a shield stops, and really "
        "parry if the blow lands in their first %2$s ticks. Raising the guard again within %3$s s opens no parry window. "
        "A parry gives back %4$s stamina."),
    "gui.forja.libros.combate.magia.resumen": (
        "El **báculo** y el **grimorio** son armas: un proyectil y un área. Gastan **maná**, la barra azul, que vuelve "
        "sola y más deprisa cuando dejas de lanzar. Tócalos para lanzar rápido; mantenlos para cargar y pegar más, gastando "
        "más. El **farol** cura en vez de dañar.",
        "The **staff** and the **tome** are weapons: a bolt and an area. They spend **mana**, the blue bar, which comes "
        "back on its own and faster when you stop casting. Tap to cast fast; hold to charge and hit harder, spending more. "
        "The **lantern** heals instead of hurting."),
    "gui.forja.libros.combate.farol_curandero": ("El Curandero lo usa mucho mejor: el libro de clases lo cuenta.",
                                                 "The Healer uses it far better: the book of classes explains."),
    "gui.forja.libros.combate.enemigos.resumen": (
        "Los monstruos de Forja **avisan** antes de pegar, pegan **por turnos**, te **rodean** y vienen en **grupos** con "
        "un jefe. No ven a través de las paredes, pero te **oyen**. Y cuanto mejor vas equipado, más fuerte pegan.",
        "Forja's monsters **warn** before they strike, strike **in turns**, **surround** you and come in **groups** with a "
        "leader. They cannot see through walls, but they **hear** you. And the better your gear, the harder they hit."),
    "gui.forja.libros.combate.aviso.titulo": ("El aviso", "The warning"),
    "gui.forja.libros.combate.aviso": (
        "Antes de cada golpe se paran y cogen impulso: la pose de su arma lo dice, y cuanto más pesa lo que llevan, más "
        "largo es el aviso. A veces **amagan**: entre un %1$s%% y un %2$s%% de las veces según la dificultad, y más contra "
        "quien para mucho.",
        "Before every blow they stop and wind up: the pose of their weapon tells you, and the heavier what they carry, the "
        "longer the warning. Sometimes they **feint**: between %1$s%% and %2$s%% of the time by difficulty, and more "
        "against someone who parries a lot."),
    "gui.forja.libros.combate.anillo.titulo": ("Turnos y anillo", "Turns and the ring"),
    "gui.forja.libros.combate.anillo": (
        "De base solo pegan %s a la vez; el resto espera su turno en un **anillo** a tu alrededor, llenando los flancos y "
        "la espalda. Tras acertar saltan atrás y dejan el turno a otro. Si retrocedes, corren a su hueco del anillo, y los "
        "arqueros se apartan para tener línea de tiro.",
        "By default only %s strike at once; the rest wait their turn in a **ring** around you, filling your flanks and "
        "back. After landing a blow they hop back and hand the turn on. If you back off they run to their place in the "
        "ring, and archers step aside for a clear line of fire."),
    "gui.forja.libros.combate.grupos.titulo": ("Grupos y capitanes", "Packs and captains"),
    "gui.forja.libros.combate.grupos": (
        "Casi nunca vienen solos: un grupo de %1$s a %2$s, a veces mezclado, y un veterano o un élite con %3$s a %4$s. Con "
        "un élite o un campeón al frente, él **manda** (lleva un estandarte dorado encima): cercar, cargar a la vez, "
        "hostigar, emboscar. Mátalo y los demás se quedan sin órdenes y pierden la moral, aunque alguno se enfurece.",
        "They hardly ever come alone: a pack of %1$s to %2$s, sometimes mixed, and a veteran or an elite with %3$s to "
        "%4$s. With an elite or a champion at the front, he **commands** (a golden banner over him): surround, charge "
        "together, harry, ambush. Kill him and the rest are left without orders and lose heart, though one may fly into a "
        "rage."),
    "gui.forja.libros.combate.sentidos.titulo": ("Lo que ven y oyen", "What they see and hear"),
    "gui.forja.libros.combate.sentidos": (
        "No te ven a través de las paredes: van a donde te vieron u oyeron por última vez y buscan desde ahí. Te **oyen** "
        "correr, picar, abrir puertas y pelear; a través de una pared, a la mitad de distancia; agachado, tus pasos no "
        "suenan. De noche, si te pierden, pueden esperarte **emboscados**.",
        "They cannot see through walls: they go where they last saw or heard you and search from there. They **hear** you "
        "run, mine, open doors and fight; through a wall, at half the distance; crouching, your steps make no sound. At "
        "night, if they lose you, they may lie in **ambush**."),
    "gui.forja.libros.combate.sin_obras": (
        "No construyen ni rompen bloques: una base cerrada del todo es segura. Los zombis sí rompen puertas, y con la regla "
        "mobGriefing apagan antorchas. Contra un pilar tiran flechas que empujan, trepan o te esperan abajo.",
        "They neither build nor break blocks: a fully closed base is safe. Zombies do break doors, and with the mobGriefing "
        "rule they put out torches. Against a pillar they shoot arrows that push, climb, or wait for you at the foot."),
    "gui.forja.libros.combate.equipo.titulo": ("Tu equipo y la presión", "Your gear and pressure"),
    "gui.forja.libros.combate.equipo": (
        "Cuanto mejor es tu equipo (de 0 a 3 tramos), más fuerte pegan, un %1$s%% más por tramo, y más te atacan a la vez. "
        "Y cada golpe que te llevas ayuda al siguiente, la **presión**: atraviesan más armadura, hasta un %2$s%%, aunque "
        "lo pares con el escudo. Baja en cuanto dejan de darte.",
        "The better your gear (tiers 0 to 3), the harder they hit, %1$s%% more per tier, and the more attack you at once. "
        "And every blow you take helps the next, **pressure**: they go through more armour, up to %2$s%%, even when your "
        "shield stops the blow. It drops as soon as they stop hitting you."),
    "gui.forja.libros.combate.tregua": ("Los monstruos de Forja no se hacen daño entre ellos.",
                                        "Forja's monsters do not hurt each other."),
    "gui.forja.libros.combate.rangos.resumen": (
        "Algunos vienen más duros y lo llevan sobre la cabeza: **veterano**, **élite** y **campeón**. Tienen más vida, "
        "ningún golpe normal les quita mucha de una vez, atraviesan más armadura y sueltan más.",
        "Some come tougher and wear it over their heads: **veteran**, **elite** and **champion**. They have more health, no "
        "ordinary blow takes much of it at once, they go through more armour and they drop more."),
    "gui.forja.libros.combate.tope.titulo": ("Lo que aguantan", "What they can take"),
    "gui.forja.libros.combate.tope": (
        "Ningún golpe normal le quita a un monstruo más de una parte de su vida: el %1$s%% a uno corriente, el %2$s%% a un "
        "veterano, el %3$s%% a un élite y el %4$s%% a un campeón. Los remates y los golpes al aturdido pasan de ese tope. Y "
        "sus golpes atraviesan tu armadura: el veterano un %5$s%%, el élite un %6$s%% y el campeón un %7$s%%.",
        "No ordinary blow takes more than a share of a monster's health: %1$s%% from a common one, %2$s%% from a veteran, "
        "%3$s%% from an elite and %4$s%% from a champion. Finishers and blows on the staggered go past that cap. And their "
        "blows go through your armour: a veteran %5$s%%, an elite %6$s%% and a champion %7$s%%."),
    "gui.forja.libros.combate.mundo.resumen": (
        "El mundo empieza peleas por su cuenta: **asedios** a tu forja, **ladrones** que se llevan lo que hiciste, enemigos "
        "que **vuelven**, **duelos** y bandas de **saqueadores**.",
        "The world starts fights on its own: **sieges** of your forge, **thieves** who take what you made, foes who "
        "**come back**, **duels** and bands of **raiders**."),
    "gui.forja.libros.combate.asedio.titulo": ("Asedios", "Sieges"),
    "gui.forja.libros.combate.asedio": (
        "Si tienes una forja cerca, puede venir a por ella una banda de %s a %s zombis y esqueletos con un élite al frente, "
        "desde lejos y a pie. Se quedan a defender lo que toman.",
        "If you have a forge nearby, a band of %s to %s zombies and skeletons with an elite at their head may come for it, "
        "from far off and on foot. They stay to hold what they take."),
    "gui.forja.libros.combate.ladrones.titulo": ("Ladrones", "Thieves"),
    "gui.forja.libros.combate.ladrones": (
        "Un monstruo con la mano vacía que te acierta puede llevarse algo forjado de tu bolsa (nunca lo que llevas puesto o "
        "en la mano) y huir. Si es un arma, pelea con ella. Mátalo y lo recuperas. Un élite o un campeón que se te escapa "
        "**vuelve** más tarde, con su nombre.",
        "A monster with an empty hand that lands a blow may take something forged from your bag (never what you wear or "
        "hold) and run. If it is a weapon, it fights with it. Kill it and you get it back. An elite or a champion that "
        "gets away **comes back** later, with its name."),
    "gui.forja.libros.combate.duelo.titulo": ("Duelos", "Duels"),
    "gui.forja.libros.combate.duelo": (
        "Un élite o un campeón puede **retarte**: ruge y marca un círculo de fuego, y los demás se apartan a mirar. Entra o "
        "pégale para aceptar. Si lo rechazas, sales del círculo o alguien se mete, se enfurecen todos. Si ganas, se "
        "dispersan y suelta más botín; si huyes, te recordará.",
        "An elite or a champion may **challenge** you: it roars and marks a ring of fire, and the rest stand back to "
        "watch. Step in or hit it to accept. Refuse, leave the ring or let someone else join in, and they all go wild. "
        "Win, and they scatter and it drops more; run, and it will remember you."),
    "gui.forja.libros.combate.noche_lluvia": (
        "De noche, a oscuras y a más de 12 bloques, no te pueden seguir con la vista; bajo la lluvia sus flechas vuelan "
        "peor.",
        "At night, in the dark and more than 12 blocks away, they cannot follow you by sight; in the rain their arrows fly "
        "worse."),
    "gui.forja.libros.combate.dificultad.resumen": (
        "Forja tiene su propia **dificultad**, además de la de Minecraft: Aprendiz, Herrero, Maestro y Leyenda. Se cambia "
        "con **/forja dificultad**. Además se **adapta** a cómo te va, y cada noche que sobrevives el mundo se endurece un "
        "poco.",
        "Forja has its own **difficulty**, besides Minecraft's: Apprentice, Smith, Master and Legend. It is changed with "
        "**/forja dificultad**. It also **adapts** to how you are doing, and every night you survive the world gets a "
        "little harder."),
    "gui.forja.libros.combate.dificultad_linea": ("**%s**: vida %s · daño %s · rangos %s · botín %s",
                                                   "**%s**: health %s · damage %s · ranks %s · loot %s"),
    "gui.forja.libros.combate.dificultad_actual": ("La de este mundo: %s.", "This world's: %s."),
    "gui.forja.libros.combate.adaptativa.titulo": ("Se adapta a ti", "It adapts to you"),
    "gui.forja.libros.combate.adaptativa": (
        "Cada monstruo que matas sin que te hayan dado en los últimos segundos la sube un poco; cada vez que mueres, baja. "
        "Mueve el daño de los monstruos hasta un 15 %% y las probabilidades de veteranos y élites hasta un 50 %%. "
        "/forja dificultad dice dónde está.",
        "Every monster you kill without having been hit in the last few seconds raises it a little; every death lowers it. "
        "It moves monster damage by up to 15 %% and the chances of veterans and elites by up to 50 %%. /forja dificultad "
        "shows where it stands."),
    "gui.forja.libros.combate.noches.titulo": ("Las noches", "The nights"),
    "gui.forja.libros.combate.noches": (
        "Cada noche que pasa el mundo cuenta: de noche vienen más en parejas y más veteranos y élites, con un tope. La "
        "noche mil no trae cien zombis, trae mejores.",
        "Every night that passes the world keeps count: at night more come in pairs and more veterans and elites, up to a "
        "cap. Night one thousand does not bring a hundred zombies, it brings better ones."),
    "gui.forja.libros.combate.siguiente": (
        "Con esto sabes pelear. Lo que viene: el calor que convierte una mesa en una fundición, y una clase que haga tuya "
        "tu forma de pelear.",
        "With this you know how to fight. What comes next: the heat that turns a table into a foundry, and a class that "
        "makes your way of fighting your own."),
})
