"""Texts of the guide's books (docs/LIBROS_GUIA.md): the starter notebook, book I, the library and the shelf.

Kept apart from generate_lang.py, which reads it, so that the books can grow without touching the lines other
work is editing there. Same shape as its GUI dict: key -> (Spanish, English).
"""

BOOKS = {
    # ---- the books themselves: title, one line on what they are about, when their recipe is learned, cover text
    "item.forja.libro_yunque": ("El yunque", "The Anvil"),
    "item.forja.tomo_de_forja": ("Tomo completo de la forja", "Complete Forge Tome"),
    "gui.forja.libros.cuaderno.titulo": ("Cuaderno del aprendiz", "Apprentice's Notebook"),
    "gui.forja.libros.cuaderno.lema": ("De cero a tu primer pico", "From nothing to your first pickaxe"),
    "gui.forja.libros.cuaderno.cuando": ("siempre: si lo pierdes, se vuelve a hacer", "always: lose it and make another"),
    "gui.forja.libros.cuaderno.portada": (
        "Forja es un mod de herrería: haces tus herramientas, armas y armaduras pieza a pieza, y cada material cambia lo "
        "que dan. Este cuaderno te lleva paso a paso, desde cero, hasta tu primer pico forjado. Luego te dice qué hacer "
        "después y cómo se hacen los demás libros, que irás fabricando cuando te hagan falta.",
        "Forja is a smithing mod: you make your tools, weapons and armour part by part, and every material changes what "
        "they give. This notebook takes you step by step, from nothing, to your first forged pickaxe. Then it tells you "
        "what to do next and how the other books are made, which you will craft as you need them."),
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
    "gui.forja.libro.cap.primeras_mesas": ("Prepara tu taller", "Set Up Your Workshop"),
    "gui.forja.libro.cap.primer_objeto": ("Tu primer pico", "Your First Pickaxe"),
    "gui.forja.libro.cap.como_funciona": ("Cómo funciona", "How It Works"),
    "gui.forja.libro.cap.teclas": ("Tus teclas", "Your Keys"),
    "gui.forja.libro.cap.estanteria": ("Los libros", "The Books"),
    "gui.forja.libro.cap.yunque_sabes": ("Lo que ya sabes", "What You Know"),
    "gui.forja.libro.cap.cortar": ("Qué se corta", "What Gets Cut"),
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
        "En Forja no sacas un pico hecho de la mesa de crafteo: lo **montas con piezas**. Un pico son tres: la **cabeza**, "
        "el **mango** y la **atadura** que las une. Cada pieza puede ser de un material distinto, y el material decide cómo "
        "sale: cuánto dura, lo rápido que pica y lo que pega.",
        "In Forja you do not take a finished pickaxe off the crafting table: you **build it from parts**. A pickaxe is "
        "three: the **head**, the **handle** and the **binding** that holds them together. Each part can be a different "
        "material, and the material decides how it turns out: how long it lasts, how fast it digs and how hard it hits."),
    "gui.forja.libros.palabras.titulo": ("Cinco palabras", "Five words"),
    "gui.forja.libros.palabras.mesa_piezas": (
        "**Mesa de piezas**: el bloque donde cortas las piezas.",
        "**Parts table**: the block where you cut parts."),
    "gui.forja.libros.palabras.plantilla": (
        "**Plantilla**: una tablilla con la forma de una pieza grabada. Le dice a la mesa de piezas qué cortar, y no se "
        "gasta nunca.",
        "**Template**: a board with the shape of one part engraved on it. It tells the parts table what to cut, and it is "
        "never used up."),
    "gui.forja.libros.palabras.pieza": (
        "**Pieza**: una parte de un objeto: cabeza, hoja, mango, atadura, placa...",
        "**Part**: one piece of an item: a head, a blade, a handle, a binding, a plate..."),
    "gui.forja.libros.palabras.mesa_forja": (
        "**Mesa de forja**: el bloque donde juntas las piezas. Su pantalla es una **estrella** de cinco puntas.",
        "**Forge table**: the block where you put the parts together. Its screen is a five-pointed **star**."),
    "gui.forja.libros.palabras.objeto": (
        "**Objeto forjado**: lo que sale de la estrella, sea herramienta, arma o armadura. Crece contigo: se templa, se "
        "mejora y, si se rompe, se repara.",
        "**Forged item**: what comes out of the star, be it a tool, a weapon or armour. It grows with you: it is quenched, "
        "upgraded and, when it breaks, mended."),
    "gui.forja.libros.bienvenida.libros": (
        "Este cuaderno te lleva hasta tu primer pico. Lo demás viene en otros **libros**, que fabricas cuando llegas ahí: "
        "los tienes todos en «Los libros», al final.",
        "This notebook takes you as far as your first pickaxe. The rest comes in other **books**, which you craft when you "
        "get there: they are all under \"The Books\", at the end."),

    # Prepara tu taller: steps 1 to 5.
    "gui.forja.libros.primeras_mesas.necesitas": (
        "Todo se hace en la mesa de crafteo de siempre. Reúne primero **6 lingotes de hierro**, una **piedra de afilar**, "
        "una **mesa de crafteo**, unos **12 tablones** y **4 palos**:",
        "It is all made at the usual crafting table. First gather **6 iron ingots**, a **grindstone**, a **crafting "
        "table**, about **12 planks** and **4 sticks**:"),
    "gui.forja.libros.primeras_mesas.paso1": ("1. La mesa de piezas", "1. The parts table"),
    "gui.forja.libros.primeras_mesas.paso1.desc": (
        "Tres de hierro arriba, la piedra de afilar en el centro y tablones a los lados y abajo.",
        "Three iron along the top, the grindstone in the middle and planks at the sides and below."),
    "gui.forja.libros.primeras_mesas.paso2": ("2. La mesa de forja", "2. The forge table"),
    "gui.forja.libros.primeras_mesas.paso2.desc": (
        "Igual que la anterior, con una mesa de crafteo en el centro.",
        "The same as the one before, with a crafting table in the middle."),
    "gui.forja.libros.primeras_mesas.paso3": ("3. Plantillas en blanco", "3. Blank templates"),
    "gui.forja.libros.primeras_mesas.paso3.desc": (
        "Dos palos y dos tablones dan **dos plantillas**. Cada plantilla lleva una sola forma y un pico necesita tres, así "
        "que haz al menos **cuatro**.",
        "Two sticks and two planks make **two templates**. Each template takes a single shape and a pickaxe needs three, "
        "so make at least **four**."),
    "gui.forja.libros.primeras_mesas.paso4": ("4. Pon las dos mesas", "4. Place both tables"),
    "gui.forja.libros.primeras_mesas.paso4.desc": (
        "Colócalas en el suelo, cerca la una de la otra, y ábrelas con **clic derecho**.",
        "Set them on the ground near each other, and open them with **right click**."),
    "gui.forja.libros.primeras_mesas.paso5": ("5. Graba tu primera plantilla", "5. Engrave your first template"),
    "gui.forja.libros.primeras_mesas.paso5.desc": (
        "Abre la mesa de piezas. Pon una plantilla en blanco en la casilla de la izquierda **(1)**. Arriba se iluminan "
        "todas las formas: haz clic en la **cabeza de pico (2)**, la primera. La plantilla queda grabada:",
        "Open the parts table. Put a blank template in the left-hand slot **(1)**. All the shapes light up above it: click "
        "the **pickaxe head (2)**, the first one. The template is now engraved:"),
    "gui.forja.libros.primeras_mesas.paso5.fin": (
        "Lo grabado no se cambia: para otra forma, usa otra plantilla. Sigue en «Tu primer pico».",
        "An engraving never changes: for another shape, use another template. Carry on in \"Your First Pickaxe\"."),

    # Tu primer pico: the worked example.
    "gui.forja.libros.primer_objeto.intro": (
        "De principio a fin: un **pico de piedra**. Necesitas **3 de roca**, **2 tablones** y tres plantillas grabadas.",
        "From start to finish: a **stone pickaxe**. You need **3 cobblestone**, **2 planks** and three engraved templates."),
    "gui.forja.libros.primer_objeto.metal": (
        "¿Por qué no de hierro? La mesa de piezas solo corta lo que se trabaja en frío: madera, piedra, hueso, cuero, "
        "amatista, cuarzo... Los metales **no se cortan**: se funden y se cuelan, y eso llega después, con la fundición.",
        "Why not iron? The parts table only cuts what is worked cold: wood, stone, bone, leather, amethyst, quartz... "
        "Metals **are not cut**: they are melted and cast, and that comes later, with the foundry."),
    "gui.forja.libros.primer_objeto.paso1": ("1. Graba tres plantillas", "1. Engrave three templates"),
    "gui.forja.libros.primer_objeto.paso1.desc": (
        "Como en «Prepara tu taller»: una con la **cabeza de pico**, otra con el **mango** y otra con la **atadura**.",
        "As in \"Set Up Your Workshop\": one with the **pickaxe head**, one with the **handle** and one with the "
        "**binding**."),
    "gui.forja.libros.primer_objeto.paso2": ("2. Corta la cabeza", "2. Cut the head"),
    "gui.forja.libros.primer_objeto.paso2.desc": (
        "En la mesa de piezas, la plantilla de cabeza de pico a la izquierda **(1)** y **%s de roca** en el centro "
        "**(2)**. La cabeza aparece a la derecha **(3)**: cógela. La roca se gasta y la plantilla se queda.",
        "At the parts table, the pickaxe head template on the left **(1)** and **%s cobblestone** in the middle **(2)**. "
        "The head appears on the right **(3)**: take it. The cobblestone is used up and the template stays."),
    "gui.forja.libros.primer_objeto.paso3": ("3. Corta el mango y la atadura", "3. Cut the handle and the binding"),
    "gui.forja.libros.primer_objeto.paso3.desc": (
        "Cambia la plantilla por la del **mango** y pon **1 tablón**. Luego la de la **atadura**, con otro tablón (o con "
        "1 cuero). Ya tienes las tres piezas:",
        "Swap the template for the **handle** one and put in **1 plank**. Then the **binding** one, with another plank (or "
        "1 leather). You now have all three parts:"),
    "gui.forja.libros.primer_objeto.paso4": ("4. Ponlas en la estrella", "4. Put them on the star"),
    "gui.forja.libros.primer_objeto.paso4.desc": (
        "Abre la mesa de forja. Pon cada pieza en una **punta** de la estrella **(1)**, en el orden que quieras. El panel "
        "oscuro **(2)** te dice qué va a salir y con qué números; si falta una pieza, te dice cuál.",
        "Open the forge table. Put each part on a **point** of the star **(1)**, in any order. The dark panel **(2)** "
        "tells you what will come out and with what numbers; if a part is missing, it says which."),
    "gui.forja.libros.primer_objeto.paso5": ("5. Forja y para el martillo", "5. Forge, and stop the hammer"),
    "gui.forja.libros.primer_objeto.paso5.desc": (
        "Haz clic en **Forjar (1)**: un martillo empieza a correr por la barra de debajo. Vuelve a hacer clic cuando pase "
        "por el **centro iluminado**. El pico aparece en el centro de la estrella **(2)**: cógelo.",
        "Click **Forge (1)**: a hammer starts running along the bar under it. Click again as it crosses the **lit "
        "middle**. The pickaxe appears in the centre of the star **(2)**: take it."),
    "gui.forja.libros.primer_objeto.paso5.martillo": (
        "No hace falta acertar: si fallas, el pico sale igual. Acertar lo hace mejor, y «Cómo funciona» te dice por qué.",
        "You do not have to hit it: miss, and the pickaxe still comes out. Hitting it makes it better, and \"How It "
        "Works\" says why."),
    "gui.forja.libros.primer_objeto.paso6": ("6. Témplalo, si quieres", "6. Quench it, if you like"),
    "gui.forja.libros.primer_objeto.paso6.desc": (
        "Sale **caliente %s segundos**. Con él **en la mano**, métete en **agua**: queda templado y se desgasta menos "
        "para siempre. Si no, se enfría y sirve igual.",
        "It comes out **hot for %s seconds**. With it **in your hand**, step into **water**: it is quenched and wears "
        "more slowly for good. If not, it cools down and works just the same."),
    "gui.forja.libros.primer_objeto.otros": (
        "Ya tienes tu primer pico forjado. Lo demás se hace igual, cambiando las piezas: el hacha, la pala y la espada, "
        "también de piedra y madera.",
        "That is your first forged pickaxe. Everything else is made the same way with other parts: the axe, the shovel "
        "and the sword, also in stone and wood."),
    "gui.forja.libros.primer_objeto.otros.mas": (
        "La armadura y las demás armas se montan igual. Pasa el ratón por una pieza o un objeto para ver sus números.",
        "Armour and the other weapons are put together the same way. Hover over a part or an item to see its numbers."),

    # Cómo funciona: the short version, before book I's long one.
    "gui.forja.libros.como_funciona.material.titulo": ("El material", "The material"),
    "gui.forja.libros.como_funciona.material": (
        "Cada pieza hace su parte. La **cabeza** (o la hoja, o la placa) decide casi todo: cuánto dura, qué minerales "
        "rompe y lo que pega. El **mango** y la **atadura** suman un poco y cambian la velocidad.",
        "Every part does its share. The **head** (or the blade, or the plate) decides almost everything: how long it "
        "lasts, which ores it breaks and how hard it hits. The **handle** and the **binding** add a little and change the "
        "speed."),
    "gui.forja.libros.como_funciona.barras": (
        "Durabilidad según la cabeza",
        "Durability by head"),
    "gui.forja.libros.como_funciona.mango": (
        "Algunos materiales llevan además un **rasgo**, un efecto extra que pasa al objeto. El hierro y el diamante de la "
        "lista se cuelan en la fundición.",
        "Some materials also carry a **trait**, an extra effect that passes to the item. The iron and the diamond in the "
        "list are cast at the foundry."),
    "gui.forja.libros.como_funciona.martillo.titulo": ("El martillo", "The hammer"),
    "gui.forja.libros.como_funciona.martillo": (
        "Donde paras el martillo queda en la pieza. En el **centro** sale **perfecta**: un %s%% más en todos sus números, "
        "para siempre. Cerca del centro sale buena y lejos, normal. Con práctica el centro se ensancha.",
        "Where you stop the hammer stays in the piece. In the **middle** it comes out **perfect**: %s%% more on every "
        "number, for good. Near the middle it comes out good, and far off, plain. With practice the middle gets wider."),
    "gui.forja.libros.como_funciona.potencial.titulo": ("El potencial", "Potential"),
    "gui.forja.libros.como_funciona.potencial": (
        "Cada objeto nace con un **potencial**: hasta dónde podrán subir sus **mejoras** y cuántas le caben. Empieza en "
        "un %s%%, un martillo bueno le suma %s y uno perfecto %s. La primera mesa sube cada mejora como mucho al %s%%.",
        "Every item is born with a **potential**: how far its **upgrades** can go and how many fit on it. It starts at "
        "%s%%, a good press adds %s and a perfect one %s. The first table takes each upgrade to %s%% at most."),
    "gui.forja.libros.como_funciona.temple.titulo": ("El temple", "The quench"),
    "gui.forja.libros.como_funciona.temple": (
        "Lo recién forjado está caliente un minuto. Si en ese rato te metes con él en agua, lava, nieve polvo o sobre un "
        "bloque de miel, se queda con ese temple para siempre: el agua lo hace más duradero, la lava quema lo que golpeas, "
        "la nieve lo frena y la miel lo deja pegado.",
        "What you have just forged stays hot for a minute. Step into water, lava or powder snow with it, or onto a honey "
        "block, and it keeps that quench for good: water makes it last longer, lava burns what you hit, snow slows it "
        "and honey leaves it stuck."),
    "gui.forja.libros.como_funciona.roto.titulo": ("Si se rompe", "When it breaks"),
    "gui.forja.libros.como_funciona.roto": (
        "Lo forjado no desaparece: se queda **roto** y sin efectos hasta que lo reparas. Ponlo en el centro de la estrella, "
        "con su material en una punta.",
        "Forged gear never vanishes: it stays **broken** and powerless until you mend it. Put it in the centre of the "
        "star, with its material on a point."),
    "gui.forja.libros.primeras_mesas.yunque": (
        "¿Y luego? Al grabar tu primera plantilla aprendiste la receta de **El yunque**, un libro y una plantilla. Cuenta "
        "todo esto con calma y cómo mejorar lo que forjas.",
        "What next? Engraving your first template taught you the recipe for **The Anvil**, a book and a template. It goes "
        "over all of this slowly, and how to upgrade what you forge."),
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
        "Si has seguido el Cuaderno del aprendiz, ya tienes las dos mesas y tu primer pico. Este libro explica qué pasó en "
        "cada paso, y lo que viene después: mejorar, desarmar y reparar.",
        "If you followed the Apprentice's Notebook, you already have both tables and your first pickaxe. This book "
        "explains what happened at each step, and what comes next: upgrading, salvaging and mending."),
    "gui.forja.libros.mesas.piezas": (
        "Tiene dos pestañas. En **Piezas** grabas plantillas y cortas: la plantilla a la izquierda, el material en el "
        "centro y la pieza sale a la derecha. En **Desarmar** deshaces un objeto en sus piezas (está en «Desarmar y "
        "reparar»).",
        "It has two tabs. In **Parts** you engrave templates and cut: the template on the left, the material in the "
        "middle, and the part comes out on the right. In **Salvage** you break an item back into its parts (see "
        "\"Salvage and Repair\")."),
    "gui.forja.libros.mesas.forja": (
        "También tiene dos. **Forja** es la estrella: cinco puntas alrededor de un centro. **Técnicas** se abre más "
        "adelante, al subir de nivel de herrero.",
        "It has two as well. **Forge** is the star: five points round a centre. **Techniques** opens later on, as your "
        "smith level rises."),
    "gui.forja.libros.mesas.estrella": (
        "Lo que hace la estrella depende de lo que pongas. Solo piezas en las puntas: **forja** un objeto nuevo. Un objeto "
        "en el centro y piezas: **cambia** esas piezas. Un objeto y un ingrediente: lo **mejora**. Un objeto y su "
        "material: lo **repara**. El botón cambia de nombre para decirte cuál va a hacer.",
        "What the star does depends on what you put on it. Parts alone on the points: it **forges** a new item. An item "
        "in the centre and parts: it **swaps** those parts. An item and an ingredient: it **upgrades** it. An item and "
        "its material: it **mends** it. The button changes its name to tell you which it will do."),
    "gui.forja.libros.mesas.taller.titulo": ("El taller completo", "The whole workshop"),
    "gui.forja.libros.mesas.taller": (
        "Con la mesa de piezas y una **mesa de talabartería** a %2$s bloques o menos de la mesa de forja, tienes un taller "
        "completo: lo que forjas ahí nace con **%1$s de potencial** más. La talabartería es además la única mesa que hace "
        "bardas para caballos y lobos.",
        "With the parts table and a **saddlery** within %2$s blocks of the forge table, you have a whole workshop: what "
        "you forge there is born with **%1$s more potential**. The saddlery is also the only table that makes barding for "
        "horses and wolves."),
    "gui.forja.libros.mesas.mayor": (
        "La mesa de forja monta los %s objetos básicos: herramientas, espada, daga, arco, flechas, caña y armadura. Las "
        "armas de dos manos y de alcance, el escudo y las alas piden la **mesa de forja mayor**, que además da %s de "
        "potencial. Su receta llega con la fundición.",
        "The forge table builds the %s basic items: tools, sword, dagger, bow, arrows, rod and armour. The two-handed "
        "and reach weapons, the shield and the wings call for the **greater forge table**, which also gives %s potential. "
        "Its recipe comes with the foundry."),
    "gui.forja.libros.cortar.intro": (
        "La mesa de piezas solo corta lo que se trabaja **en frío**. Estos son los materiales que acepta; cualquier otro "
        "no entra en la casilla:",
        "The parts table only cuts what is worked **cold**. These are the materials it takes; anything else will not go "
        "into the slot:"),
    "gui.forja.libros.cortar.colar": (
        "Los metales (cobre, hierro, oro y las aleaciones), el diamante, la obsidiana y la netherita **no se cortan: se "
        "cuelan**. Se funden en un crisol y se vierten en un molde, como cuenta «La fundición». Las piezas coladas dan "
        "además más potencial.",
        "Metals (copper, iron, gold and the alloys), diamond, obsidian and netherite **are not cut: they are cast**. They "
        "are melted in a crucible and poured into a mould, as \"The Foundry\" tells. Cast parts also give more potential."),
    "gui.forja.libros.cortar.coste.titulo": ("Lo que cuesta cada pieza", "What each part costs"),
    "gui.forja.libros.estrella.intro": (
        "Las piezas van en las **puntas (1)**, en cualquier orden. El panel de la derecha **(2)** enseña lo que va a salir "
        "y sus números antes de gastar nada; si faltan piezas, dice cuáles. **Forjar (3)** lo hace, y el objeto aparece "
        "en el centro.",
        "The parts go on the **points (1)**, in any order. The panel on the right **(2)** shows what will come out and its "
        "numbers before anything is spent; if parts are missing, it says which. **Forge (3)** does it, and the item "
        "appears in the centre."),
    "gui.forja.libros.estrella.martillo": (
        "Al forjar un objeto nuevo, el botón no forja a la primera: pone a correr un martillo por la barra de debajo. Haz "
        "clic otra vez para pararlo. En el **centro iluminado** sale **perfecto**: un %s%% más en todos sus números y "
        "%s de potencial. En la zona de alrededor sale bueno, con %s de potencial. Más lejos, normal. El centro se "
        "ensancha con tu nivel de herrero. Cambiar piezas, mejorar o reparar no llevan martillo.",
        "When you forge a new item the button does not forge straight away: it sets a hammer running along the bar "
        "under it. Click again to stop it. In the **lit middle** the item comes out **perfect**: %s%% more on every "
        "number and %s potential. In the band around it, it comes out good, with %s potential. Further off, plain. The "
        "middle gets wider with your smith level. Swapping parts, upgrading and mending take no hammer."),
    "gui.forja.libros.estrella.potencial.titulo": ("El potencial", "Potential"),
    "gui.forja.libros.estrella.potencial": (
        "El potencial es el techo de las mejoras de un objeto y lo que caben en él. Sale de cómo se hizo: %s%% de base, "
        "hasta %s por el martillo, %s por cada nivel de herrero, %s por el taller completo, %s en la mesa mayor y hasta %s "
        "si sus piezas son coladas. Lo ves en su tooltip, y después aún sube con la maestría del objeto.",
        "Potential is the ceiling on an item's upgrades and how many fit on it. It comes from how the item was made: %s%% "
        "to start, up to %s from the hammer, %s per smith level, %s for the whole workshop, %s at the greater table and "
        "up to %s if its parts were cast. Its tooltip shows it, and it still grows afterwards with the item's mastery."),
    "gui.forja.libros.estrella.banco.titulo": ("Qué monta esta mesa", "What this table builds"),
    "gui.forja.libros.estrella.banco": (
        "La mesa de forja monta %s objetos: las herramientas, la espada, la daga, el arco, las flechas, la caña y las cuatro "
        "piezas de armadura. Así se juntan tres de ellos:",
        "The forge table builds %s items: the tools, the sword, the dagger, the bow, arrows, the rod and the four pieces "
        "of armour. This is how three of them go together:"),
    "gui.forja.libros.mejorar.pasos": (
        "Para mejorar, el objeto en el **centro (1)**, un ingrediente en una **punta (2)** y **Mejorar (3)**, sin "
        "martillo. Cada ingrediente sube el porcentaje de su mejora: el **azúcar** da Eficiencia a un pico, la "
        "**amatista** da Filo a un arma. Pon un montón y la mesa gasta lo que haga falta.",
        "To upgrade: the item in the **centre (1)**, an ingredient on a **point (2)** and **Upgrade (3)**, no hammer. "
        "Every ingredient raises its upgrade's percentage: **sugar** gives a pickaxe Efficiency, **amethyst** gives a "
        "weapon Filo. Put down a pile and the table uses what it needs."),
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
        "sola **muy despacio**: unos dos minutos para llenarse. Solo el **Mago** y el **Curandero** la recuperan deprisa; "
        "a los demás les vuelve sobre todo matando, así que para ellos la magia es un recurso, no el arma de siempre. "
        "Tócalos para lanzar rápido; mantenlos para cargar y pegar más, gastando más. El **farol** cura en vez de dañar.",
        "The **staff** and the **tome** are weapons: a bolt and an area. They spend **mana**, the blue bar, which comes "
        "back on its own **very slowly**: about two minutes to fill. Only the **Mage** and the **Healer** get it back "
        "quickly; everyone else gets it back mostly by killing, so for them magic is a resource, not the everyday weapon. "
        "Tap to cast fast; hold to charge and hit harder, spending more. The **lantern** heals instead of hurting."),
    "gui.forja.libros.combate.farol_curandero": ("El Curandero lo usa mucho mejor: el libro de clases lo cuenta.",
                                                 "The Healer uses it far better: the book of classes explains."),
    "gui.forja.libros.combate.enemigos.resumen": (
        "Los monstruos de Forja **avisan** antes de pegar, pegan **por turnos**, te **rodean** y vienen en **grupos** con "
        "un jefe. No ven a través de las paredes, pero te **oyen**. Y cuanto mejor vas equipado, más fuerte pegan.",
        "Forja's monsters **warn** before they strike, strike **in turns**, **surround** you and come in **groups** with a "
        "leader. They cannot see through walls, but they **hear** you. And the better your gear, the harder they hit."),
    "gui.forja.libros.combate.aviso.titulo": ("El aviso", "The warning"),
    "gui.forja.libros.combate.aviso": (
        "Antes de cada golpe cogen impulso **sin dejar de venir a por ti**: la pose de su arma lo dice, y cuanto más pesa "
        "lo que llevan, más largo es el aviso y más despacio te siguen. Retroceder andando no basta: **corre**, esquiva, "
        "bloquea o para. A veces **amagan**: entre un %1$s%% y un %2$s%% de las veces según la dificultad, y más contra "
        "quien para mucho.",
        "Before every blow they wind up **still coming for you**: the pose of their weapon tells you, and the heavier what "
        "they carry, the longer the warning and the slower they follow. Walking back is not enough: **run**, dodge, block "
        "or parry. Sometimes they **feint**: between %1$s%% and %2$s%% of the time by difficulty, and more against "
        "someone who parries a lot."),
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
        "Y cada golpe que te llevas ayuda al siguiente, la **presión**: empieza en 0 y sube con cada golpe (con el escudo, "
        "la mitad), y mientras dura los golpes atraviesan más armadura. Un solo golpe nunca te quita más de un %3$s%% de "
        "ella (arma y rango del monstruo juntos); solo una paliza seguida llega al tope, un %2$s%%, tras unos %4$s golpes. "
        "Si pasan %5$s s sin que te den, baja poco a poco: del tope a cero en unos %6$s s. Lo ves en el escudito junto a "
        "tu armadura: entero es armadura que aguanta; se vacía al recibir golpes, pasa de acero a naranja y a rojo, y a "
        "cero **se rompe**, hasta que la presión empieza a bajar.",
        "The better your gear (tiers 0 to 3), the harder they hit, %1$s%% more per tier, and the more attack you at once. "
        "And every blow you take helps the next, **pressure**: it starts at 0 and grows with every blow (half with your "
        "shield), and while it lasts blows go through more armour. A single blow never takes more than %3$s%% of it "
        "(weapon and monster rank together); only sustained punishment reaches the cap, %2$s%%, after about %4$s blows. "
        "After %5$s s without being hit it drops gradually: from the cap to zero in about %6$s s. You can see it in the "
        "little shield beside your armour: full means armour that holds; it empties as you are hit, going from steel to "
        "orange to red, and at zero it **breaks**, until the pressure starts to drop."),
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
        "sus golpes atraviesan tu armadura: el veterano un %5$s%%, el élite un %6$s%% y el campeón un %7$s%%, sumado al "
        "arma y a la presión, pero entre arma y rango, de un golpe, nunca más de un %8$s%%.",
        "No ordinary blow takes more than a share of a monster's health: %1$s%% from a common one, %2$s%% from a veteran, "
        "%3$s%% from an elite and %4$s%% from a champion. Finishers and blows on the staggered go past that cap. And their "
        "blows go through your armour: a veteran %5$s%%, an elite %6$s%% and a champion %7$s%%, added to the weapon's and "
        "to pressure, but weapon and rank together never take more than %8$s%% in one blow."),
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
        "La **dificultad** de Forja es la de Minecraft, con un escalón más por encima de Difícil: **Implacable**. Cada "
        "escalón suma sistemas al anterior. Este mod es duro y no es para todos: si te sobra, baja un escalón. Además se "
        "**adapta** a cómo te va, y cada noche que sobrevives el mundo se endurece un poco.",
        "Forja's **difficulty** is Minecraft's, with one more step above Hard: **Relentless**. Each step adds systems to the "
        "one before. This mod is tough and not for everyone: if it is too much, go down a step. It also **adapts** to how "
        "you are doing, and every night you survive the world gets a little harder."),
    "gui.forja.libros.combate.dificultad.nivel.pacifico": (
        "**%1$s**: el de Minecraft, sin monstruos.",
        "**%1$s**: Minecraft's, with no monsters."),
    "gui.forja.libros.combate.dificultad.nivel.facil": (
        "**%1$s** (vida %2$s · daño %3$s · rangos %4$s · botín %5$s): los monstruos de Minecraft pelean como en Minecraft. "
        "Sí hay veteranos y élites, pero ellos y los monstruos de Forja tienen un %7$s%% menos de aguante, y las cifras "
        "de arriba solo valen para ellos. Las armaduras aguantan como en Minecraft: no hay penetración.",
        "**%1$s** (health %2$s · damage %3$s · ranks %4$s · loot %5$s): Minecraft's monsters fight as in Minecraft. "
        "There are veterans and elites, but they and Forja's monsters have %7$s%% less stamina, and the figures above "
        "only count for them. Armor holds as in Minecraft: there is no penetration."),
    "gui.forja.libros.combate.dificultad.nivel.normal": (
        "**%1$s** (vida %2$s · daño %3$s · rangos %4$s · botín %5$s): las **reglas** de combate de Forja: avisos, turnos, "
        "el anillo y el capitán de reglas; y la **penetración** de armadura: la presión y el mordisco de armas y rangos. "
        "Sin redes entrenadas. Al correr van un +%6$s%% más rápido.",
        "**%1$s** (health %2$s · damage %3$s · ranks %4$s · loot %5$s): Forja's combat **rules**: warnings, turns, the "
        "ring and the rules captain; and armor **penetration**: pressure and the bite of weapons and ranks. No trained "
        "networks. Running, they go %6$s%% faster."),
    "gui.forja.libros.combate.dificultad.nivel.dificil": (
        "**%1$s** (vida %2$s · daño %3$s · rangos %4$s · botín %5$s): todo lo de Normal, y los monstruos piensan con sus "
        "**redes entrenadas**. Al correr van un +%6$s%% más rápido.",
        "**%1$s** (health %2$s · damage %3$s · ranks %4$s · loot %5$s): all of Normal, and the monsters think with their "
        "**trained networks**. Running, they go %6$s%% faster."),
    "gui.forja.libros.combate.dificultad.nivel.extremo": (
        "**%1$s** (vida %2$s · daño %3$s · rangos %4$s · botín %5$s): el máximo. Todo lo de Difícil, con las redes más "
        "nuevas donde las haya, la red del capitán, el capitán entero (visión compartida, sucesión, escolta y órdenes "
        "nuevas), más veteranos y élites y más daño. Al correr van un +%6$s%% más rápido.",
        "**%1$s** (health %2$s · damage %3$s · ranks %4$s · loot %5$s): the most there is. All of Hard, with the newest "
        "networks where there are any, the captain's network, the whole captain (shared vision, succession, escort and "
        "new orders), more veterans and elites and more damage. Running, they go %6$s%% faster."),
    "gui.forja.libros.combate.dificultad.boton": (
        "Se cambia con el botón de dificultad de Minecraft (Opciones, y al crear el mundo), que tras Difícil pasa a "
        "Implacable, o con **/forja dificultad implacable** (dificil, normal…). Un mundo extremo (hardcore) es Difícil o "
        "Implacable: se elige al crearlo.",
        "It is changed with Minecraft's difficulty button (Options, and when creating the world), which goes on from Hard "
        "to Relentless, or with **/forja dificultad implacable** (dificil, normal…). A hardcore world is Hard or Relentless: you "
        "choose when you create it."),
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

# ---- the upgrade probe: a slot in the book for a piece, and what goes on it (Andy, 2026-09-30)
BOOKS.update({
    "gui.forja.libro.cap.probador": ("¿Qué le cabe?", "What Fits It?"),
    "gui.forja.libros.probador.intro": (
        "Pon aquí cualquier pieza forjada de tu bolsa y el libro te dice qué mejoras le van, cuáles caben ahora y "
        "cuáles no, y por qué. Solo mira: la pieza no se mueve de tu bolsa.",
        "Put any forged piece from your bag here and the book tells you which upgrades go on it, which fit now and "
        "which do not, and why. It only looks: the piece does not leave your bag."),
    "gui.forja.libros.probador.ranura": ("Ninguna pieza", "No piece"),
    "gui.forja.libros.probador.clic": ("Clic: elige una pieza de tu bolsa", "Click: pick a piece from your bag"),
    "gui.forja.libros.probador.vacio": ("Pulsa la ranura y elige una herramienta, un arma o una pieza de armadura forjada.",
                                        "Click the slot and pick a forged tool, weapon or armour piece."),
    "gui.forja.libros.probador.no_forjado": ("Esa pieza no es forjada: las mejoras solo van en lo que sale de la estrella.",
                                             "That piece is not forged: upgrades only go on what comes off the star."),
    "gui.forja.libros.probador.estado": (
        "Potencial %s · carga %s de %s · pactos %s de %s · sinergias despiertas %s de %s",
        "Potential %s · load %s of %s · pacts %s of %s · synergies awake %s of %s"),
    "gui.forja.libros.probador.caben": ("Caben ahora (%s)", "Fit now (%s)"),
    "gui.forja.libros.probador.no_caben": ("Compatibles, pero no caben (%s)", "Compatible, but do not fit (%s)"),
    "gui.forja.libros.probador.ninguna": ("Ninguna: todo lo compatible cabe.", "None: everything compatible fits."),
    "gui.forja.libros.probador.elige": ("Elige una pieza de tu bolsa", "Pick a piece from your bag"),
    "gui.forja.libros.probador.nada": ("No llevas ninguna pieza forjada.", "You carry no forged piece."),
    "gui.forja.libros.probador.sube": ("%s: %s%% → %s%%", "%s: %s%% → %s%%"),
    "gui.forja.libros.probador.esta": ("%s: %s%%", "%s: %s%%"),
    "gui.forja.libros.probador.efecto": ("Al %s%%: %s", "At %s%%: %s"),
    "gui.forja.libros.probador.mesa": ("La primera mesa sube cada mejora hasta el %s%%; lo que falta, la mesa mayor.",
                                       "The first table takes each upgrade to %s%%; the rest is the greater table's."),
    "gui.forja.libros.probador.razon.full": ("Ya está todo lo alto que llega.", "It is already as high as it goes."),
    "gui.forja.libros.probador.razon.load": ("Falta carga: la pieza no aguanta algo tan pesado.",
                                             "Not enough load: the piece cannot carry something this heavy."),
    "gui.forja.libros.probador.razon.potential": ("Falta potencial: la pieza no da para subirla más.",
                                                  "Not enough potential: the piece cannot take it further."),
    "gui.forja.libros.probador.razon.conflict": ("No se junta con %s, que ya lleva.", "Does not go with %s, which it already has."),
    "gui.forja.libros.probador.razon.pacts": ("Ya lleva dos pactos, los que una pieza aguanta.",
                                              "It already carries two pacts, as many as a piece may."),
    "gui.forja.libros.probador.razon.sealed": ("Pacto sellado: ábrelo antes con su ofrenda en la estrella.",
                                               "Sealed pact: open it first with its offering at the star."),
    "gui.forja.libros.probador.sinergia": ("Con %s despierta «%s».", "With %s it wakes \"%s\"."),
    "gui.forja.libros.probador.sinergia_dormida": ("Con %s haría «%s», pero ya hay tres despiertas: dormiría.",
                                                   "With %s it would make \"%s\", but three are awake already: it would sleep."),
    "gui.forja.libros.probador.orbe": ("solo con el orbe de un evento", "only with an event's orb"),
})

# ---- pacts and synergies in shadow until opened or awakened, and the probe's groups (Andy, 2026-09-30)
BOOKS.update({
    "gui.forja.libros.pacto_sellado": ("Pacto sellado", "Sealed pact"),
    "gui.forja.libros.pacto_sellado.desc": ("Qué da y qué cobra no se sabe hasta abrirlo. Se abre una vez, ofreciendo lo que pide en la estrella.",
                                            "What it gives and what it costs is not known until it is opened. It opens once, offering what it asks at the star."),
    "gui.forja.libros.pacto_sellado.corto": ("Se abre con una ofrenda en la estrella", "Opens with an offering at the star"),
    "gui.forja.libros.sinergia_dormida.desc": (
        "Una sinergia que aún no has despertado. Aparecerá aquí la primera vez que despierte en algo que lleves: dos "
        "mejoras que se llevan bien, las dos al 50 %% o más.",
        "A synergy you have not woken yet. It appears here the first time it wakes on something you carry: two upgrades "
        "that get on, both at 50 %% or more."),
    "gui.forja.libros.probador.grupo": ("%s (%s)", "%s (%s)"),
    "gui.forja.libros.probador.sinergia_oculta": ("Con %s despertaría una sinergia que aún no conoces.",
                                                  "With %s it would wake a synergy you do not know yet."),
    "gui.forja.libros.probador.ofrenda": ("su ofrenda", "its offering"),
})
BOOKS.update({
    "gui.forja.libros.probador.grupo": ("▸ %s (%s)", "▸ %s (%s)"),
    "gui.forja.libros.probador.grupo_abierto": ("▾ %s (%s)", "▾ %s (%s)"),
    "gui.forja.libros.probador.pliegues": ("Pulsa un grupo para abrirlo: qué hace cada mejora, hasta dónde sube y su receta.",
                                           "Click a group to open it: what each upgrade does, how far it goes and its recipe."),
})

# ---- book III, La fundición
BOOKS.update({
    "item.forja.libro_fundicion": ("La fundición", "The Foundry"),
    "item.forja.libro_combate": ("El arte del combate", "The Art of Combat"),
    "gui.forja.libro.cap.fundicion_sabes": ("Lo que ya sabes", "What You Know"),
    "gui.forja.libro.cap.primeras_aleaciones": ("Calor y primeras aleaciones", "Heat and First Alloys"),
    "gui.forja.libro.cap.catalogo_aleaciones": ("Aleaciones", "Alloys"),
    "gui.forja.libro.cap.fundicion_siguiente": ("Siguiente", "Next"),
    "gui.forja.libro.seccion.fundicion_calor": ("El calor", "Heat"),
    "gui.forja.libro.seccion.fundicion_linea": ("La línea de fundición", "The foundry line"),
    "gui.forja.libro.seccion.fundicion_mayor": ("La mesa mayor", "The greater table"),
    "gui.forja.libros.fundicion_sabes": (
        "Hasta aquí la mesa de forja ha trabajado en frío: piezas cortadas, forjadas y mejoradas. Lo que pongas debajo de "
        "ella cambia eso. Con calor, dos metales se vuelven uno, las piezas se funden y el metal se cuela. Es el camino "
        "a las mejores aleaciones y a la mesa de forja mayor.",
        "So far the forge table has worked cold: parts cut, forged and upgraded. What you put under it changes that. With "
        "heat, two metals become one, parts melt back down and metal is cast. It is the road to the best alloys and to "
        "the greater forge table."),
    "gui.forja.libros.fundicion.calor_linea": ("**%s**: %s", "**%s**: %s"),
    "gui.forja.libros.catalogo.aleaciones": ("Cada aleación, por el calor que pide: lo que entra, lo que sale y cuánto.",
                                             "Every alloy, by the heat it asks for: what goes in, what comes out and how much."),
    "gui.forja.libros.fundicion_siguiente": (
        "Con la mesa mayor termina el camino del herrero que se aprende en el taller. Lo que viene: sacarle todo a una "
        "pieza, y salir al mundo a buscar el castillo del Herrero.",
        "With the greater table the part of the smith's path learned in the workshop ends. What comes next: getting "
        "everything out of a piece, and going out into the world to find the Smith's castle."),
})

# ---- book IV, La mesa mayor
BOOKS.update({
    "item.forja.libro_mesa_mayor": ("La mesa mayor", "The Greater Table"),
    "gui.forja.libro.cap.mayor_sabes": ("Lo que cambia", "What Changes"),
    "gui.forja.libro.cap.mayor_siguiente": ("Siguiente", "Next"),
    "gui.forja.libro.seccion.mayor_mejoras": ("Mejoras a fondo", "Upgrades in depth"),
    "gui.forja.libro.seccion.mayor_maestria": ("Maestría y técnicas", "Mastery and techniques"),
    "gui.forja.libro.seccion.mayor_llevar": ("Lo que llevas", "What you carry"),
    "gui.forja.libros.mayor_sabes": (
        "La mesa de forja mayor monta todo lo que la primera no monta (espadones, lanzas, escudos, manguales, alas...) y "
        "lleva las mejoras del %s%% hasta el %s%%. Pero no todas caben en todas las piezas: cada una tiene un potencial y "
        "una carga. Este libro cuenta hasta dónde puede llegar una pieza, y hasta dónde puedes llegar tú.",
        "The greater forge table makes everything the first one will not (greatswords, spears, shields, flails, wings...) "
        "and takes upgrades from %s%% up to %s%%. But not everything fits on every piece: each has a potential and a load. "
        "This book tells how far a piece can go, and how far you can."),
    "gui.forja.libros.mayor_siguiente": (
        "Lo que queda: una clase que haga tuya tu forma de pelear, y salir al mundo a buscar el castillo del Herrero.",
        "What is left: a class that makes your way of fighting your own, and going out into the world to find the "
        "Smith's castle."),
})
BOOKS.update({
    "gui.forja.libros.sinergias_dormidas": ("%s sinergias por despertar", "%s synergies still asleep"),
    "gui.forja.libros.sinergia_dormida.desc": (
        "Aún no las has despertado. Cada una aparece aquí la primera vez que despierta en algo que lleves: dos mejoras "
        "que se llevan bien, las dos al 50 %% o más. El probador de mejoras avisa cuando una pieza está a punto.",
        "You have not woken them yet. Each appears here the first time it wakes on something you carry: two upgrades "
        "that get on, both at 50 %% or more. The upgrade probe says when a piece is close."),
})

# ---- book V, Clases
BOOKS.update({
    "item.forja.libro_clases": ("Clases", "Classes"),
    "advancements.forja.leer_clases.title": ("Tu forma de pelear", "Your Way of Fighting"),
    "advancements.forja.leer_clases.description": ("Abre el libro de clases", "Open the book of classes"),
    "gui.forja.libro.cap.clases_sabes": ("Qué es una clase", "What a Class Is"),
    "gui.forja.libro.cap.clases_siguiente": ("Siguiente", "Next"),
    "gui.forja.libro.seccion.clases_elegir": ("Las clases", "The classes"),
    "gui.forja.libros.clases.cerradas": ("Las clases se abren con su libro: hazlo y léelo (libro y esmeralda).",
                                         "The classes open with their book: make it and read it (a book and an emerald)."),
    "gui.forja.libros.clases_sabes": (
        "Una clase es tu forma de pelear: seis, cada una con sus números, dos habilidades, una final que eliges entre "
        "tres y un árbol grande que crece con la experiencia y con los hitos, y en el que también está la forja. Abrir "
        "este libro por primera vez es lo que las abre: desde ahora puedes elegir la tuya con **%s** o con el botón de "
        "aquí abajo. La primera es gratis.",
        "A class is your way of fighting: six, each with its numbers, two skills, an ultimate you pick out of three and"
        " a big tree that grows with experience and milestones, and where the forge is too. Opening this book for the "
        "first time is what opens them: from now on you can choose yours with **%s** or with the button below. The "
        "first one is free."),
    "gui.forja.libro.clases.ultimas": (
        "**Habilidades finales.** Cada senda del árbol acaba en la suya: tres por clase, fuertes y con esperas largas, y "
        "cada una con su mejora II. Solo puedes tener **una**, y va a la tecla **%s**: al aprender una, las otras dos se "
        "cierran con candado. El resto de esas sendas, el puente y lo que hay detrás se siguen aprendiendo. «Probar» "
        "enseña cómo quedaría. Para cambiarla, una Vela del olvido quita la final con su II de una vez (cuentan como "
        "los %s puntos de una vela), y el Medallón vacía el árbol entero.",
        "**Ultimates.** Each path of the tree ends in its own: three per class, strong and with long cooldowns, each "
        "with its II upgrade. You can only have **one**, and it goes on the **%s** key: once you learn one, the other "
        "two lock with a padlock. The rest of those paths, the bridge and what lies past it can still be learned. "
        "\"Try\" shows how it would look. To change it, a Candle of Oblivion takes the ultimate and its II off at once "
        "(they fit in one candle's %s points), and the Medallion empties the whole tree."),
    "gui.forja.libros.clases_siguiente": (
        "Con tu clase elegida, lo que queda está ahí fuera: las ruinas, el castillo del Herrero y lo que hay más allá.",
        "With your class chosen, what is left is out there: the ruins, the Smith's castle and what lies beyond."),
})

# ---- book VI, El Bastión y el Herrero
BOOKS.update({
    "item.forja.libro_bastion": ("El Bastión y el Herrero", "The Bastion and the Smith"),
    "gui.forja.libro.cap.bastion_sabes": ("Lo que hay ahí fuera", "What Is Out There"),
    "gui.forja.libro.cap.ruinas": ("Ruinas", "Ruins"),
    "gui.forja.libro.cap.herrero_historia": ("Quién era el Herrero", "Who the Smith Was"),
    "gui.forja.libro.cap.bastion": ("El Bastión del Gremio", "The Guild's Bastion"),
    "gui.forja.libro.cap.portal_estelar": ("El portal", "The Portal"),
    "gui.forja.libro.cap.bastion_siguiente": ("Siguiente", "Next"),
    "gui.forja.libro.seccion.bastion_mundo": ("El mundo", "The world"),
    "gui.forja.libro.seccion.bastion_ruinas": ("Ruinas e historia", "Ruins and history"),
    "gui.forja.libro.seccion.bastion_castillo": ("El castillo", "The castle"),
    "gui.forja.libros.bastion_sabes": (
        "Fuera del taller hay forjas abandonadas con sus cofres, aldeanos que venden lo que no sabes hacer, encargos que "
        "pagan bien y ruinas con guardianes. Y, en algún sitio no muy lejos del origen del mundo, el castillo del gremio "
        "de herreros, donde empieza el camino hacia el Herrero Caído.",
        "Outside the workshop there are abandoned forges with their chests, villagers who sell what you cannot make, "
        "commissions that pay well and ruins with guardians. And, somewhere not far from the world's origin, the smiths' "
        "guild castle, where the road to the Fallen Smith begins."),
    "gui.forja.libros.ruinas.forja.titulo": ("Forja abandonada", "Abandoned forge"),
    "gui.forja.libros.ruinas.forja": (
        "En llanuras, bosques y otros biomas de aldea: las dos mesas, un cofre con plantillas grabadas, equipo y orbes, y un "
        "autómata que aún la guarda. El Herrero de herramientas maestro y el Forjador venden su mapa.",
        "In plains, forests and other village biomes: both tables, a chest with engraved templates, gear and orbs, and an "
        "automaton still guarding it. The master toolsmith and the Forger sell its map."),
    "gui.forja.libros.ruinas.castillo.titulo": ("El castillo de forja", "The forge castle"),
    "gui.forja.libros.ruinas.castillo": (
        "Un castillo pequeño de ladrillo de pizarra en colinas, montañas y mesetas de sabana: muralla con almenas, cuatro "
        "torres con farol y, en el centro, la sala de estampado del **Guardián de Cuño**. Mientras arda uno de los tres "
        "faroles de pavesa de sus pilares, nada le hace daño. Guarda el mejor cofre de su tamaño.",
        "A small deepslate-brick castle in hills, mountains and savanna plateaus: crenellated wall, four towers with "
        "lanterns and, in the middle, the stamping hall of the **Die Guardian**. While one of the three ember lanterns on "
        "its pillars burns, nothing hurts it. It keeps the best chest of its size."),
    "gui.forja.libros.ruinas.nether.titulo": ("La fragua del Nether", "The Nether forge"),
    "gui.forja.libros.ruinas.nether": (
        "Una fortaleza de piedra negra con canales de lava, dos autómatas y, en el centro, una fragua apagada. Es de antes "
        "del Bastión: un clic en su fragua la abre en un marco de portal como el de la Forja Profunda, que se enciende "
        "igual, con cuatro perlas de oricalco.",
        "A blackstone fortress with lava channels, two automatons and, in the middle, a dead forge. It is older than the "
        "Bastion: a click on its forge opens it into a portal frame like the Deep Forge's, lit the same way, with four "
        "orichalcum pearls."),
    "gui.forja.libros.herrero_historia": (
        "El gremio de herreros levantó el Bastión para guardar su oficio: once torres, una por taller, y bajo ellas la "
        "cripta de los Nueve Maestros. El último maestro mayor quiso forjar con lo que cae del cielo y encendió en la Forja "
        "Profunda un fuego que no era de este mundo.",
        "The smiths' guild raised the Bastion to keep its craft: eleven towers, one for each workshop, and beneath them "
        "the crypt of the Nine Masters. The last master smith wanted to forge with what falls from the sky, and lit in "
        "the Deep Forge a fire that was not of this world."),
    "gui.forja.libros.herrero_historia.dos": (
        "El fuego se lo llevó. Su fragua quedó apagada, sus aprendices con él, y desde entonces trabaja en un cementerio de "
        "armas entre las estrellas. Es el Herrero Caído. Su corazón de forja es el mejor material que existe.",
        "The fire took him. His forge went dead, his apprentices with him, and since then he works in a graveyard of "
        "weapons among the stars. He is the Fallen Smith. His forge heart is the finest material there is."),
    "gui.forja.libros.bastion.donde": (
        "Cada mundo tiene un Bastión del Gremio entre 1.500 y 4.500 bloques del origen, en terreno llano y seco y lejos de "
        "las aldeas, y más repartidos por el mundo. El Forjador de nivel 5 vende su mapa, y /locate lo encuentra.",
        "Every world has a Guild's Bastion between 1,500 and 4,500 blocks from the origin, on flat, dry ground away from "
        "villages, and more scattered over the world. A level 5 Forger sells its map, and /locate finds it."),
    "gui.forja.libros.bastion.dentro.titulo": ("Qué hay dentro", "What is inside"),
    "gui.forja.libros.bastion.dentro": (
        "Once torres con la sala de su taller en cada piso, patio, salones y casas de aprendices; bajo tierra, la cripta, el "
        "osario, mazmorras, la bodega, la cámara acorazada y la Forja Profunda. Unos noventa monstruos lo guardan, y el "
        "botín va por salas: la cámara y la cripta, lo mejor.",
        "Eleven towers with their workshop's room on every floor, a courtyard, halls and apprentices' houses; underground, "
        "the crypt, the ossuary, dungeons, the cellar, the vault and the Deep Forge. Some ninety monsters guard it, and "
        "the loot goes by room: the vault and the crypt hold the best."),
    "gui.forja.libros.bastion.forja.titulo": ("La Forja Profunda", "The Deep Forge"),
    "gui.forja.libros.bastion.forja": (
        "En el fondo del castillo, donde ardía la fragua del maestro, hay un marco de cinco por cinco con cuatro ménsulas "
        "vacías. Es la puerta al mundo del Herrero.",
        "At the bottom of the castle, where the master's forge burned, there is a five-by-five frame with four empty "
        "brackets. It is the door to the Smith's world."),
    "gui.forja.libros.bastion_siguiente": (
        "Con el portal encendido aprendes la receta del último libro, y encuentras uno junto al marco.",
        "With the portal lit you learn the last book's recipe, and find one beside the frame."),
})

# ---- book VII, El Cementerio entre Estrellas; the shelf and the lectern
BOOKS.update({
    "item.forja.libro_cementerio": ("El Cementerio entre Estrellas", "The Graveyard Among the Stars"),
    "block.forja.estanteria_del_herrero": ("Estantería del herrero", "Smith's Shelf"),
    "block.forja.atril_del_herrero": ("Atril del Herrero", "Smith's Lectern"),
    "advancements.forja.portal.title": ("Entre estrellas", "Among the Stars"),
    "advancements.forja.portal.description": ("Enciende el portal de la Forja Profunda", "Light the Deep Forge's portal"),
    "gui.forja.libro.cap.cementerio_viaje": ("El viaje", "The Journey"),
    "gui.forja.libro.cap.cementerio_pelea": ("La pelea", "The Fight"),
    "gui.forja.libro.cap.cementerio_recompensa": ("La recompensa", "The Reward"),
    "gui.forja.libro.cap.cementerio_herrero": ("El Herrero Caído", "The Fallen Smith"),
    "gui.forja.libro.cap.cementerio_fin": ("Fin", "The End"),
    "gui.forja.libro.seccion.cementerio_mundo": ("Su mundo", "His world"),
    "gui.forja.libro.seccion.cementerio_final": ("Después", "Afterwards"),
    "gui.forja.libros.cementerio.meseta": (
        "Una meseta de ceniza sola en el vacío, sembrada de armas clavadas como tumbas, con forjas frías en ruinas y ríos "
        "de metal fundido que queman como la lava y caen por el borde. En el centro, la arena. Allí no se duerme ni se "
        "fija la reaparición, y siempre es de noche.",
        "A plateau of ash alone in the void, sown with weapons stuck in the ground like graves, with cold ruined forges and "
        "rivers of molten metal that burn like lava and fall over the edge. In the middle, the arena. There is no "
        "sleeping and no setting your spawn there, and it is always night."),
    "gui.forja.libros.cementerio.pelea.resumen": (
        "Cae del cielo en cuanto llegas. Tiene **tres tramos**: a dos tercios y a un tercio clava el martillo y salen sus "
        "aprendices. A la mitad se **reforja** y no muere mientras ardan sus brasas estelares: vuelca los **braseros**. Las "
        "**constelaciones** ayudan a un bando o al otro. Muerto o vencido, la pelea espera tu vuelta.",
        "He falls from the sky as soon as you arrive. He has **three stages**: at two thirds and one third he drives his "
        "hammer in and his apprentices rise. At half he **reforges** and cannot die while his star embers burn: tip the "
        "**braziers** over. The **constellations** help one side or the other. Dead or beaten, the fight waits for you."),
    "gui.forja.libros.cementerio.llegada.titulo": ("La llegada", "The arrival"),
    "gui.forja.libros.cementerio.llegada": (
        "Al entrar en su mundo sin pelea en curso, a los pocos segundos una estrella cae sobre la arena, suelta una onda "
        "morada al tocar el suelo y el Herrero se levanta. Mientras cae y se asienta no se le puede dañar.",
        "Entering his world with no fight under way, a few seconds later a star falls on the arena, sends out a purple wave "
        "as it lands and the Smith rises. While he falls and settles he cannot be hurt."),
    "gui.forja.libros.cementerio.fases.titulo": ("Tramos y aprendices", "Stages and apprentices"),
    "gui.forja.libros.cementerio.braseros.titulo": ("El Reforjado estelar", "The Star Reforging"),
    "gui.forja.libros.cementerio.braseros": (
        "Solo apaga sus brasas la colada de un brasero de la arena: se vuelca con un golpe o una flecha, y cae sobre la "
        "brasa que tiene delante. Cada brasero se vuelca una vez; se rellena con %s hierros estelares. Mientras se "
        "reforja, su martillo llama meteoritos. Al apagarse la última brasa queda aturdido unos segundos y recibe más daño.",
        "Only the pour of one of the arena's braziers puts out his embers: tip it with a blow or an arrow, and it pours on "
        "the ember in front of it. Each brazier tips once; it is refilled with %s star irons. While he reforges, his "
        "hammer calls down meteorites. When the last ember goes out he is stunned for a few seconds and takes more damage."),
    "gui.forja.libros.cementerio.fuerza.titulo": ("Su fuerza", "His strength"),
    "gui.forja.libros.cementerio.fuerza": (
        "Cuánto aguanta depende del nivel: para uno solo y sin equipo, %s de vida en Fácil, %s en Normal, %s en Difícil y "
        "%s en Implacable. Crece un %s %% por cada tramo de equipo de quien le pelea, como los demás monstruos, y otro tanto "
        "entero por cada jugador de más; lo que pase de lo que el juego deja tener a una criatura se lo quita a cada golpe "
        "que recibe. Cada fase le pone más armadura, golpes avisados más fuertes y esperas más cortas, pero los avisos duran "
        "lo mismo. En Difícil y Implacable enciende una brasa más al reforjarse y saca %s guardianes (%s en Implacable).",
        "How much he takes depends on the level: for one player with no gear, %s health on Easy, %s on Normal, %s on Hard "
        "and %s on Relentless. He grows %s %% for every tier of gear of whoever fights him, like every other monster, and as "
        "much again for every extra player; whatever goes past what the game lets a creature have is taken off every blow "
        "he gets instead. Every stage gives him more armour, harder warned blows and shorter waits, but the warnings last "
        "just as long. On Hard and Relentless he lights one more ember when he reforges and calls up %s keepers (%s on "
        "Relentless)."),
    "gui.forja.libros.cementerio.furia": (
        "Bajo un tercio se **enfurece**: la forja de su pecho arde violeta hasta el final, es un %s %% más rápido, su golpe "
        "normal pega un %s %% más y la lluvia de estrellas no para. Los aprendices de la segunda oleada salen con un %s %% más de "
        "vida y más armadura.",
        "Under a third he is **enraged**: the forge in his chest burns violet to the end, he is %s %% faster, his plain blow "
        "hits %s %% harder and the star shower keeps coming. The apprentices of the second wave come up with %s %% more health "
        "and more armour."),
    "gui.forja.libros.cementerio.constelaciones.titulo": ("Las constelaciones", "The constellations"),
    "gui.forja.libros.cementerio.constelaciones": (
        "Cada medio minuto una de las ocho constelaciones del cielo se enciende y actúa. Espada, Hacha, Escudo y Guadaña "
        "ayudan al Herrero (aviso en rojo, con campana); Martillo, Lanza, Yunque y Tenazas ayudan a los jugadores (aviso "
        "en verde menta): meteoritos sobre él, lanzas de luz, curación, y los aprendices apartados.",
        "Every half minute one of the sky's eight constellations lights up and acts. Sword, Axe, Shield and Scythe help "
        "the Smith (a red warning, with a bell); Hammer, Spear, Anvil and Tongs help the players (a mint warning): "
        "meteorites on him, spears of light, healing, and the apprentices pushed aside."),
    "gui.forja.libros.cementerio.golpes.titulo": ("Sus golpes", "His blows"),
    "gui.forja.libros.cementerio.estrella": (
        "Cada uno que pelea se lleva una. En la mesa de forja mayor, sobre una pieza terminada (una por pieza): su "
        "potencial puede llegar a %s y sube %s, el daño y el minado %s y la durabilidad %s. En armadura, +%s de armadura "
        "y +%s de dureza por pieza.",
        "Everyone who fights takes one. At the greater forge table, on a finished piece (one per piece): its potential "
        "can reach %s and rises %s, damage and mining %s and durability %s. On armour, +%s armour and +%s toughness per "
        "piece."),
    "gui.forja.libros.cementerio.botin.titulo": ("Lo que deja", "What he leaves"),
    "gui.forja.libros.cementerio.botin": (
        "Su corazón de forja, una leyenda, el martillo del maestro, su yunque la primera vez, y una **Estrella de vuelta**: "
        "clic derecho y te devuelve al portal por el que entraste.",
        "His forge heart, a legend, the master's hammer, his anvil the first time, and a **Star of Return**: right click "
        "and it takes you back to the portal you came through."),
    "gui.forja.libros.cementerio.revancha.titulo": ("La revancha", "The rematch"),
    "gui.forja.libros.cementerio.revancha": (
        "En el centro de la arena queda una fragua fría estelar. Para volver a pelear, dale una perla de oricalco, tres "
        "oricalcos, una estrella del Nether y dieciséis hierros estelares. La pelea se conserva si mueres o sales, y se "
        "para cuando no queda nadie en su mundo.",
        "A cold star forge is left in the middle of the arena. To fight again, give it an orichalcum pearl, three "
        "orichalcum, a Nether star and sixteen star irons. The fight is kept if you die or leave, and stops when nobody is "
        "left in his world."),
    "gui.forja.libros.cementerio.ficha": ("Su ficha se escribe la primera vez que lo ves.", "His page writes itself the first time you see him."),
    "gui.forja.libros.cementerio.fin": (
        "Aquí termina la guía. Lo que queda es tuyo: piezas mejores, la revancha, el mundo entero. Para tenerlos juntos, "
        "la estantería del herrero guarda los ocho libros, cada uno en su sitio.",
        "Here the guide ends. What is left is yours: better pieces, the rematch, the whole world. To keep them together, "
        "the smith's shelf holds the eight books, each in its place."),
    "gui.forja.libros.estanteria_bloque": (
        "Clic con un libro de la forja y va a su sitio; clic en un libro de la estantería y vuelve a tu mano. Un comparador "
        "cuenta cuántos tiene.",
        "Click with a forge book and it goes to its place; click on a book in the shelf and it comes back to your hand. A "
        "comparator counts how many it holds."),
})
