# Novedades

## 2026-10-01 — La Fragua del Vacío del End y el eterio

- **Ruina nueva en el End** (`forja:fragua_del_vacio`): pequeña, de ladrillo de piedra del End y púrpura roto, en las
  islas altas de fuera (bioma `end_highlands`), más o menos una cada 384 bloques. Sobre un altar de obsidiana, una
  **fragua del vacío** fría; un yunque astillado, varas del End, un cofre y guardias: dos corazas vacías y un shulker.
- **Un ojo de ender la enciende** y despierta a sus guardianes (se levantan dos corazas vacías más). Funciona como la
  fragua de almas, con **perlas de ender** de combustible, y solo arde en el End. No se rompe.
- **Eterio** (Aetherium), solo ahí: 2 acero + 1 caparazón de shulker + 4 coro reventado + 4 piedra del End → 2
  lingotes. Ligero y rápido en la mano (mango +0,20), cabeza +3,0, 1200 de durabilidad, armadura 18.
- Rasgo nuevo **Flotante**: lo que golpea (arma, herramienta o flecha) **se eleva** 1 s, como con la bala de un shulker,
  una vez cada 3 s por objetivo y nunca un jefe. Con armadura de eterio, **el vacío te devuelve**: si caes al vacío
  vuelves al último suelo firme, con caída lenta, y cada pieza pierde un 10 %; una vez cada 3 minutos (1,5 con el
  conjunto). Conjunto: saltas más y +1 de dureza.
- El cofre trae siempre la nota con la receta y un ojo de ender; a veces un lingote de muestra.
- Lingote y kit de reparación de la familia de `tools/lingotes.py`: dos motas que se levantan de la barra.
- Guía: libro VI, «Las fraguas lejanas», y libro III, «Aleaciones de fragua». JEI: «Fragua del vacío (End)».
- Equilibrio: entra en la sección «Aleaciones de fragua frente a la netherita».
- Pruebas: encender con ojo de ender, fundir solo en el End, la levitación (y no a un jefe), el vacío que te devuelve,
  la plantilla, la estructura en las tierras altas y el botín. Sección de cliente nueva `FORJA_SOLO=fragua_vacio`, con
  su hoja en `Forja_capturas_mejoras/aleacion_end`.

## 2026-10-01 — Magmacero, segunda aleación de la fragua de almas: la lava se vuelve suelo

- **Magmacero** (Magmasteel), solo en la fragua de almas de la Fragua caída: 2 acero + 4 basalto + 4 piedra negra +
  2 crema de magma → 2 lingotes, con el mismo polvo de blaze por tanda. Pesado y duradero (1600, mango ×1,55 pero
  lento), nivel de diamante, cabeza +3,0, armadura 20 con dureza 2,5: metal de armadura y de pico, no de hoja.
- Rasgo nuevo **Volcánico**: con armadura de magmacero, **la lava que pisas se enfría en costra de magma**
  (`forja:costra_de_magma`) que aguanta mientras alguien está encima y vuelve a ser lava entre 4 y 6 s después; radio 1
  con una pieza, 2 con dos o tres y 3 con las cuatro. Los **picos** de magmacero cortan netherrack, basalto, piedra
  negra, magma y ladrillo del Nether un 50 % más deprisa (etiqueta `forja:piedra_volcanica`). La punta de flecha se
  pega: prende y frena 2 s. Conjunto: +1 de armadura y +20 % de resistencia al empuje.
- La nota del cofre de la ruina trae también su receta, y el cofre a veces un lingote de muestra y piedra negra.
- Lingote y kit de reparación de la familia de `tools/lingotes.py`: magma encendido en las juntas del basalto.
- Equilibrio: entra en la sección «Aleaciones de fragua frente a la netherita» (no es netherita mejor y no está en
  ninguna de las mejores armas).
- Pruebas: `theSoulForgeMakesMagmasteelToo`, `magmasteelCoolsTheLavaUnderfoot`, `magmasteelPicksCutNetherStone`; la
  sección `FORJA_SOLO=fragua_caida` filma su armadura junto a la de fatuo y la costra en el canal de lava.

## 2026-10-01 — La Fragua caída enciende una fragua de almas: el fatuo, primera aleación del Nether

- **La ruina del Nether ya no abre un marco de portal** (eso lo hace la Forja Profunda del Bastión). Sobre su altar hay
  una **fragua de almas** fría. Una **vara de blaze** la enciende, y al encenderla despierta lo que la guarda: los
  monstruos de alrededor van a por ti y se levantan dos pavesas más.
- **Encendida, funde lo que ninguna otra fragua funde.** Clic derecho con los ingredientes y con **polvo de blaze** de
  combustible (uno por tanda); cada tanda tarda 10 s y sale encima, o a la tolva o cofre de debajo. Clic con la mano
  vacía para ver qué tiene y qué le falta; agachado, para sacarlo. Solo arde en el Nether y no se rompe.
- **Fatuo** (Wispfire): 2 lingotes de hierro + 1 chatarra de netherita + 4 tierra de almas → 2 lingotes. Rasgo nuevo
  **Espectral**: el golpe deja **llama fatua** 4 s (1 de daño mágico cada 2 s) que **quema a lo que no arde** (blazes,
  esqueletos wither, ghasts, cubos de magma) y que el agua no apaga; la armadura se la pasa a quien te pega; la punta de
  flecha también. Cabeza +3,0, 1000 de durabilidad, armadura 19: por debajo de la netherita en todo lo que la netherita
  mide. Vale para cualquier pieza. Conjunto: +1 de daño y el fuego te dura la mitad.
- Ni la estrella de la mesa, ni la montadora, ni el crisol, ni las cubas hacen fatuo ni aceptan su tierra de almas.
- Uno de los dos cofres de la ruina trae siempre una **nota con la receta** y una vara de blaze para encenderla.
- Su lingote y su **kit de reparación** son de la familia de `tools/lingotes.py` (dos lenguas de fuego de almas en la
  cara de arriba; en el kit, su glifo).
- Mundos viejos: la fragua apagada de una Fragua caída ya generada se convierte en la fragua de almas al tocarla; la de
  los castillos viejos sigue abriéndose en un marco.
- Guía: libro III, capítulo «Aleaciones de fragua»; libro VI, «Las fraguas lejanas» y el texto nuevo de la ruina. JEI
  dice «Fragua de almas (Nether)» en vez de un calor.
- Equilibrio: `docs/EQUILIBRIO.md` tiene una sección «Aleaciones de fragua frente a la netherita» y la prueba
  `aleacionesDeFraguaEnSuSitio` exige que no sean netherita mejor ni estén en más de la mitad de las mejores armas. La
  primera versión (+3,5 y 1 de daño por segundo) salía en 26 de 60 mejores armas; con los números de ahora, en ninguna.
- Diseño completo (las tres aleaciones de fragua): `docs/ALEACIONES_NETHER_END.md`.
- Pruebas: `FraguasLejanasGameTests` (encender y despertar, fundir solo en el Nether y con combustible, nadie más la
  hace, todas las piezas, la llama fatua a un blaze, la plantilla, la nota), `theOldForgeNoLongerSummons` (la ruina ya
  no trae fragua apagada ni abre marco) y `FORJA_SOLO=fragua_caida`, que ahora filma la fragua encendida y el equipo
  de fatuo (hoja en `Forja_capturas_mejoras/aleacion_nether`, `tools/hoja_aleaciones.py`).

## 2026-10-01 — El oricalco, material de forja

- **El oricalco es ahora un material de forja** (`ForgeMaterial.ORICALCO`) y vale para **todas las piezas**: cabezas,
  hojas, puntas, mangos, ataduras, guardas, placas, forros, cuerdas, núcleos... Entra con su lingote de siempre
  (`forja:oricalco`, ahora también etiqueta). Diseño y razones en `docs/HERRERO_DIMENSION.md`, 1.4.
- **Escalón:** nivel de netherita, junto al damasco y el almacero, por debajo del solacero, el lunacero, el acero vivo y
  el corazón. Cabeza 1650 de durabilidad, 8,5 de minado, +3,5 de daño; encantabilidad 30 (la más alta del mod); mango
  ×1,30 / +0,15 / ×1,10; armadura 3/6/8/3 (20, como la netherita), 36 de durabilidad, 2,5 de dureza.
- **Rasgo nuevo, Astral:** +10 % de regeneración de maná por cada pieza puesta y cada mano con oricalco en cualquier
  parte; el doble (+20 %) si la misma pieza lleva también hierro o acero estelar. Conjunto entero: +25 de maná máximo y
  +1 de dureza. Las puntas de flecha de oricalco son de Hechizo (devuelven maná). Una pieza rota no da nada.
- **Se cuela, no se corta:** solo en la **mesa de almas** (la de piedra negra llega a 1600). La mesa de colada ya no
  rechaza el oricalco en moldes y marcos; la perla sigue igual. En las cubas, el canal y el crisol se ve ahora verde
  dorado en vez de gris, y fundir algo de oricalco devuelve oricalco.
- **Kit de reparación de oricalco** (`kit_de_reparacion_oricalco`): `RepairKits.OWN_METALS` / `REPAIR_KIT_OWN` para los
  metales propios cuyo lingote no está en `Alloys.ALL`.
- Nombres, origen, conjunto y rasgo en los tres idiomas; libro VI (portal estelar), «El oricalco en la forja»; JEI,
  página del lingote. `docs/EQUILIBRIO.md`: el oricalco en las tablas de materiales.
- Pruebas: `OricalcoGameTests` (todas las piezas y objetos, colada en mesa de almas y no en la de brasa, Astral con y
  sin hierro estelar, conjunto, pieza rota, kit); `KitsGameTests` exige el kit de oricalco; `MaterialesGameTests` acepta
  materiales que salen del crisol fuera de `Alloys.ALL`. `FORJA_SOLO=oricalco` fotografía piezas, objetos, tooltips y
  el conjunto puesto; `tools/hoja_oricalco.py` hace la hoja.

## 2026-10-01 — Kits de reparación (variante B)

- **Nuevo objeto: el kit de reparación de cada metal** (`forja:kit_de_reparacion_<material>`), con la barra sellada del
  gremio de `hoja_lingotes.jpg` (variante B) como dibujo. Hay uno por cada metal que puede ser la parte principal de una
  pieza: las 15 aleaciones que son material (bronce, latón, peltre, acero, electro, damasco, acero estelar, obsidiacero,
  cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero vivo) y cobre, hierro, oro y netherita: 19.
- **Receta:** 2 lingotes del metal, 1 cuero y 1 cuerda, sin forma. Se aprende (libro de recetas) al tener un lingote de
  ese metal, como las mesas y los sellos.
- **Uso:** el kit y una pieza forjada en cualquier mesa de crafteo, en cualquier casilla: la pieza sale con **+300 de uso**,
  sin pasar del máximo, y con todo lo demás igual (piezas, mejoras, potencial, maestría, nombre, encantamientos). Saca
  de roto a lo que estaba roto. Solo vale si la **parte principal** es de ese metal: la que da nombre a la pieza
  (`ForgedParts.primary()`: la cabeza de las herramientas, la hoja o punta de las armas, la placa de las armaduras). Con
  otro metal, sin desgaste o con algo más en la mesa, no sale nada.
- **Precio:** 150 de uso por lingote. La estrella da un cuarto del máximo por lingote, así que el kit solo sale más barato
  en piezas de menos de 600 de uso (metales baratos: cobre, hierro, oro, bronce, latón, peltre, electro); de acero para
  arriba reparar en la estrella cuesta menos lingotes. El kit es para el camino. Las demás formas de reparar no cambian.
- Los kits salen de la lista de materiales: una aleación nueva en `Alloys.ALL`/`ForgeMaterial` y en `ALLOY_COLORS`
  tiene su kit, receta, textura y nombre sin tocar nada más (`forge/RepairKits.java`, `generate_assets.repair_kit_materials()`).
- JEI: cada kit tiene su página de información. Libro I, «Desarmar y reparar»: cómo se hace y cómo se usa.
- Pruebas: `KitsGameTests` (+300 con datos intactos y tope, otro metal no da nada, una pieza rota vuelve, cada metal tiene
  kit, receta, logro de receta, modelo, textura y nombre). `FORJA_SOLO=kits` fotografía los lingotes y los kits en una
  mesa de crafteo; `tools/hoja_kits.py` hace la hoja.

## 2026-10-01 — Lingotes nuevos: una sola familia (variante A)

- Todos los lingotes del mod (las 16 aleaciones, el oricalco y el lingote de temple) se dibujan ahora a mano con la
  **variante A** que eligió Andy en `hoja_lingotes.jpg`: la silueta diagonal del lingote vanilla, una sola luz desde
  arriba a la izquierda, una rampa de cinco tonos por metal y la marca de cada aleación en la cara de arriba.
- El dibujo vive en `tools/lingotes.py`; lo llaman `generate_alloy_textures()`, `generate_temper_ingot_texture()` y
  `dimension_assets.portal()`. `tools/lingotes_propuesta.py` queda como la hoja de antes y después.
- Una pasada limpia de `generate_assets.py` solo cambia esas 18 texturas.

## 2026-10-01 — "Extremo" pasa a llamarse "Implacable" (Relentless)

- El escalón de dificultad por encima de Difícil se llama ahora **Implacable** (en inglés **Relentless**): «Extremo» chocaba
  con el nombre en español del modo Hardcore de Minecraft, que el propio mod llama «un mundo extremo (hardcore)».
- Solo cambia el nombre que se ve (botón de dificultad, opciones del mundo, regla del mundo, libros y mensajes). Los ids
  siguen igual (`extremo`, `forja:extremo`, claves de idioma), así que los mundos guardados siguen funcionando.
- `/forja dificultad implacable` es el nombre nuevo y el que se sugiere; `/forja dificultad extremo` sigue valiendo.
- Pruebas: `theCommandSetsBoth` prueba los dos nombres; `DificultadFootage` espera «Implacable».

## 2026-10-01 — Segunda pasada visual: Molde Roto, lingotes, ceniza, farol de pavesa y armadura

- **Molde Roto:** su generador vuelve a coincidir con el modelo del agarre a dos manos y pasa por el pintor de los
  demás monstruos: arena de moldeo cocida con línea de partición, hierro ennegrecido y el lingote como metal que corre.
  Sin caras que parpadeen (eran 17; también una del cargador de carbón).
- **Lingotes:** el peltre lleva tres poros limpios y el damasco dos ondas de capas, en vez de manchas y un tablero.
- **Cementerio entre Estrellas:** la ceniza y la ceniza prensada ya no parecen estática de televisión: tonos suaves
  en ondas que se repiten sin costura y alguna mota de hueso quemado.
- **Farol de pavesa:** hierro ennegrecido en todo el marco y dos barrotes finos; antes era el bloque más claro de los
  muros negros del Bastión y la pavesa casi no se veía.
- **Armadura forjada:** las sombras de las bandas ya no llegan casi a negro; en oro, cobre y cuero eran rayas oscuras.
- **Herramientas:** `python tools/generate_assets.py` en limpio ya no cambia nada del repo (`tools/escritura_estable.py`).
- Pruebas: FORJA_SOLO=visual_2 filma la ceniza, los bloques del Bastión y armaduras de diez materiales.

## 2026-10-01 — El garfio del herrero se puede esquivar

Antes la garra del Herrero Caído siempre alcanzaba a su objetivo al soltarla, aunque se hubiera puesto un muro delante,
estuviera lejos o rodara justo en ese tick, y el aviso de 8 ticks (hecho para romper su línea) no servía de nada.

- **Línea rota o fuera de alcance:** sin línea de visión al soltarla, o a más de `HOOK_MAX` + 2 bloques en horizontal, la
  garra falla: la cadena se dibuja hasta el primer bloque que toca, suena un golpe de cadena y no hay daño ni arrastre.
- **Esquiva:** un jugador en los ticks de invulnerabilidad de su esquiva también es fallado.
- Pruebas nuevas en `GarfioGameTests` (línea limpia, muro durante el aviso y esquiva).

## 2026-10-01 — Las pruebas de los blazes, sin carreras contra el reloj

Las nueve pruebas de vuelo del blaze fallaban a veces todas a la vez en la batería completa («el suelo del vuelo
todavía no actualiza entidades on tick 462» o «No sequences finished»), siempre en el mismo segundo.

- **La causa:** el servidor de pruebas no lleva el ritmo de 20 tics por segundo, encadena un tic tras otro
  (`GameTestServer.waitUntilNextTick`), cientos por segundo cuando hay poco que hacer. Los trozos del mundo, en cambio,
  se generan y cargan en otros hilos y al ritmo del reloj. El suelo de vuelo (de −10 a 18) se salía de la caja de 8 × 8
  de la prueba, y sus trozos se esperaban como mucho 400 tics: con la máquina cargada eso era menos de medio segundo.
  Además, una prueba que terminaba soltaba trozos que su vecina seguía usando.
- **El arreglo:** la caja de las pruebas de vuelo es ahora de 29 × 8 × 29 (`gametest/structure/vuelo_blaze.snbt`) y el
  suelo cabe dentro. El marco de pruebas fuerza los trozos de la caja, los guarda hasta el final de la tanda y no
  empieza la prueba hasta que todos actualizan entidades, tarde lo que tarde. Sin espera ni margen de 400 tics.
- `TestChunks` (las otras pruebas que construyen fuera de su caja) suelta un trozo solo cuando ya no lo usa ninguna
  prueba que lo pidió.
- `blazeLeadsAMovingTarget`: el jugador anda por el eje x de la prueba aunque la prueba esté girada, y cada bola del
  blaze mandado se juzga con el adelanto con el que salió (de vez en cuando la red elige otro).
- `zombieLungesAtMidRange`: el zombi sale a 7 bloques, con Lentitud, sobre el suelo del jugador y sin escudo; antes
  pasaba por la franja de la embestida en unos 15 tics y 1 de cada 25 no llegaba a saltar.

## 2026-10-01 — Pase visual de objetos y bloques

Andy: «mejora todo lo visual del mod que puedas». Se revisaron todos los objetos y bloques (iconos e instalados en el
mundo) y se rehicieron los que peor se veían. Hojas del antes y el después en
`E:\IA\Claude\Forja_capturas_mejorasisual_objetos\`.

- **Molde de fundición y marco vacíos:** salían con la textura de "modelo que falta" en el inventario creativo y en JEI.
- **Cincel:** hoja que se abre hasta el filo, virola y mango redondo (antes parecía un palo).
- **Farol de curación:** cayado de dos píxeles y un farol con su tapa, su jaula y la luz.
- **Cinturón de herramientas:** un cinturón cerrado con hebilla, no una placa con cuadros.
- **Armadura de lobo:** caparazón de tres placas remachadas con guarda al cuello y correas (antes, el arnés de
  armadillo de vanilla teñido, que salía desvaído).
- **Mesa de forja:** el mismo estilo con contraste: yunque arriba, martillo y tenazas al costado, cajón al frente.
- **Yunque del Herrero Caído:** su cara de arriba ya no parece una cara.
- **Montadora:** la prensa y el yunque de la ventana se leen en el inventario.
- **Cajas de moldeo:** la tapa dibujaba un martillo; ahora copa, bebedero y dos barras.
- Herramientas: los dibujos van en `tools/visual_objetos.py` (los llama `generate_assets.py`), las hojas se hacen con
  `tools/hoja_objetos.py` y `FORJA_SOLO=objetos_visual` saca en el cliente todos los objetos y bloques del mod.

## 2026-10-01 — Repaso visual de pantallas, HUD y partículas

Andy: «mejora todo lo visual del mod que puedas». Hojas de antes y después en
`Forja_capturas_mejoras/visual_gui/`.

- **Hechizos con luz propia:** partícula nueva `forja:destello` (color y tamaño), que nace casi blanca, se asienta en
  su color y se apaga. Sustituye al polvo de vanilla del báculo, el grimorio, la runa, el farol, el maná, las
  sinergias, el golpe arcano, el parry, el frenesí, las habilidades de clase y la cabeza del meteorito: de noche ese
  polvo eran motas marrones y un borrón violeta oscuro. Las chispas y las almas brillan solas.
- **Meteorito:** la cola es fuego y humo fino; ya no deja una columna de cuadros violeta en el cielo.
- **Pantallas:** botón, canal de metal, brillo de casilla y canaleta compartidos (`client/ForjaUi`). La estrella
  encendida ya no tapa el marco de sus puntas, el medidor de calor cierra el panel de datos, el metal corre igual en
  el crisol, la caja de moldeo y la montadora, y la montadora ya no escribe encima del inventario.
- **Elegir/cambiar clase:** el botón baja bajo la lista; el coste del cambio ya no se escribe encima de la tercera
  habilidad (va en el botón, con el medallón, y en su tooltip).
- **HUD por carriles** (`client/HudLayout`): maná y estamina sobre la armadura, luego alas, luego frenesí. Las alas
  cruzaban la fila de armadura, el escudo de integridad y los corazones de absorción.
  Mientras se ve alguna barra, el nombre del objeto en la mano y el mensaje de acción de vanilla suben por encima
  de ellas en vez de cruzarlas.
- **Habilidades V/B/N:** su tecla encima del marco, la espera como persiana y un destello cuando vuelven.
- **Cartel de evento** enmarcado; **guía** con botones de cuero.
- Prueba nueva: `FORJA_SOLO=hud_carriles`.

## 2026-10-01 — Repaso visual de los monstruos

Andy: «mejora todo lo visual del mod que puedas». Esta parte es la de los monstruos. Hojas de antes y después en
`Forja_capturas_mejoras/visual_mobs/`.

- **Pintor nuevo** (`tools/visual_mobs.py`, que `generate_assets.py` llama al final) para los 14 monstruos del generador.
  El Molde Roto se queda como está. Cada material tiene su superficie:
  - metal con bisel, luz de arriba, remaches y hollín;
  - piedra en hiladas;
  - carbón en trozos con brillos;
  - escoria con grietas de lava que brillan de noche;
  - brasa en celdas;
  - llama blanca en la raíz y roja en la punta;
  - cristal en facetas, cuero con costuras y tela con pliegues.
  Se acabó el ruido de sal y pimienta encima de todo.
- **Cada uno con su material.** Seis monstruos estaban pintados con los mismos tres grises. Ahora:
  - el autómata es de ladrillo refractario;
  - el guardián del cuño, de piedra negra y oro;
  - el yunque andante, de acero pavonado;
  - el percutor, de hierro colado y latón;
  - el cargador, de hierro tiznado;
  - la jaula de la ascua mayor, de hierro ennegrecido.
- **Formas:**
  - el templador lleva un cristal ámbar en la cara, brasas en la chimenea y un manómetro de latón (antes no tenía nada
    que brillara y de noche desaparecía);
  - el cargador lleva carbón amontonado, con un trozo encendido;
  - la herrumbre tiene antenas, una cresta y un aguijón;
  - el yunque andante tiene garras y el agujero cuadrado del yunque;
  - el guardián del cuño lleva hombreras y puños de oro.
  No se ha cambiado ningún nombre de hueso.
- **Andares y reposo con curvas suaves** en siete monstruos:
  - el yunque cambia el peso de pata y la cara va con retraso;
  - el autómata cae con cada pisada;
  - el percutor balancea el ariete;
  - las mandíbulas de la tenaza oscilan;
  - la herrumbre mueve la cabeza a tirones;
  - el cargador escarba;
  - el guardián mueve los brazos.
  Los golpes y especiales no se tocan: siguen cayendo en el tick del código.
- **Parpadeo arreglado:** la tapa del guardián del cuño salía a rayas oro y piedra, y al lomo del cargador le pasaba
  igual. Ahora, cuando dos piezas comparten plano, la grande cede 0,04 px.
- **Armadura forjada** (la que llevan los aprendices del Herrero): la placa gris bajo el tinte del material era casi
  plana y teñida parecía tela. Ahora tiene luz de arriba, bordes oscuros y un canto iluminado.
- Los `.geo.json` y `.animation.json` de estos monstruos son compactos: un hueso, cubo o canal por línea.
- Prueba de cliente `FORJA_SOLO=visual_mobs` (`VisualMobsFootage`):
  - cada monstruo de frente, de lado y a tres cuartos;
  - quieto, andando, avisando y golpeando;
  - de día y de noche;
  - además, los aprendices.
  `FORJA_MOBS=` elige cuáles. La hoja se hace con `python tools/visual_mobs.py hoja SALIDA.jpg --antes CARPETA`.

## 2026-10-01 — Árboles de clase: una habilidad final por senda, y se elige una

Andy: «1 habilidad por cada senda, y solo puedes escoger una habilidad final, pero sí puedes mejorar las otras sendas».
Detalle en `docs/ARBOLES.md`, «Las habilidades finales».

- **Tres finales por clase, 18 en total**, una al final de cada senda y con su II. Las seis N de antes son la final de
  la senda 3; las doce nuevas, con el aire de su senda y de la clase de su puente:
  - Guerrero: **Bramido** (postura y Debilidad en área, absorción por enemigo) y **Hendedura** (tajo al frente, 200 %
    del arma y 50 de postura);
  - Asesino: **Danza de sombras** (salta a la espalda de hasta 4 enemigos, intocable mientras baila) y **Ejecución**
    (aparece detrás del que miras; doble bajo el 35 %);
  - Tanque: **Golpe sísmico** (daño, postura, los levanta y ralentiza) y **Santuario de acero** (círculo de Resistencia
    y Regeneración que echa fuera a los hostiles);
  - Mago: **Relámpago en cadena** (salta a 4 enemigos más) y **Prisión de hielo** (congela y aturde: corta el ataque);
  - Curandero: **Oleada de vida** (cura en área y empuja) y **Segunda vida** (el golpe mortal deja al aliado con el 40 %);
  - Arquero: **Saeta letal** (se apunta con un aviso de luz y atraviesa todo) y **Flecha explosiva** (área, postura y
    empuje, sin romper bloques).
- **Solo una**: al aprender una final, las otras dos y sus II se cierran (`Refusal.ULTIMATE`); el resto de esas sendas,
  el puente y lo de la otra clase se siguen aprendiendo. La elegida va a la N. La Vela del olvido la quita junto con su
  II como una sola hoja (3 puntos) y libera la elección; el Medallón lo vacía todo.
- **V II, B y B II** salen de las sendas: V II cuelga del tronco de la rama A, B y B II del de la rama B.
- **Árbol**: 101 nodos; todo junto cuesta 127, pero lo que se puede tener (una final) cuesta 117: con 103 puntos en el
  tope se compra el 88 %, como antes. Toda clave sigue a 11 puntos y toda final está a 7.
- **Pantalla**: las tres finales son rombos con borde dorado y «Final · elige una»; la elegida dice «Elegida» y las
  otras salen grises con candado y el tooltip «Ya elegiste X». «Probar» enseña la elección y no deja meter una segunda.
  A la derecha, la N muestra la final elegida (o la probada, en azul).
- **Iconos a cualquier zoom**: Andy veía casi todo como cuadrados de color. Ahora el icono sale siempre que el nodo mida
  5 píxeles o más, y el velo de lo bloqueado deja verlo. Las doce finales nuevas llevan icono propio.
- **Arreglo**: las absorciones de Provocar II, Escudo de luz, Égida y Milagro no daban nada (el juego limita la absorción
  a `max_absorption`, 0 sin el efecto). Ahora suben ese tope mientras dura el escudo (`ClassSkills.shield`).
- **Guardados**: versión 3. Un árbol de la versión 2 conserva su N como final elegida (con su II); los nodos se
  renombran y lo que se queda suelto devuelve sus puntos, con un aviso.
- Libro V, la pantalla de elegir clase y `CLASES.md` al día; los dibujos del Guerrero y el Mago, regenerados.
- Pruebas: `UltimasGameTests` (datos, una sola final, vela y medallón, migración, y las 18 finales I y II haciendo lo
  que dicen, con su daño medido por el camino real: ninguna pasa de 30 en un golpe ni de 1 por segundo de espera) y
  `FORJA_SOLO=arbol` con las capturas `ultimas_*` (hoja en `Forja_capturas_mejoras\arbol\ultimas\hoja_ultimas.png`).
  `./gradlew runGametest`: 489 en verde.

## 2026-09-30 — Aviso en movimiento: los mobs ya no se paran al avisar

Andy: «cuando los mobs preparan un ataque ya no se pueden mover, por lo que es muy fácil esquivarlos».

- **Durante el aviso de un golpe el mob sigue al jugador** y se gira hacia él (`ai/WindupChase`): a su velocidad de
  acercarse × el factor de peso (1 hasta 1 kg, −0,1 por kg de más, mínimo 0,7), sin correr, y se para a 0,8 dentro
  del alcance de su golpe. En los dos caminos (la meta cuerpo a cuerpo de los mobs de reglas y de los de Forja, y el
  ejecutor de las redes) y en el golpe del enderman tras su teletransporte.
- **No cambia:** la duración del aviso, que el golpe solo entra si al acabar estás a su alcance, el compromiso, las
  fintas, el aturdimiento, la espera tras el golpe y los turnos. Los especiales con aviso propio (embestida, carga,
  movimientos de los jefes) siguen igual; un mob de Forja con un especial cargándose no se mueve.
- **Qué escapa:** retroceder andando ya no basta; correr, esquivar, bloquear o parar, sí.
- Config: `windupChase` (encendido) y `windupChaseSpeed` (1,0).
- Medido con `CapitanMedidaGameTests` (160 peleas por modo): los avisos que fallan porque el jugador se movió bajan de
  1,4–3,4 a 0,3–1,0 por pelea; daño/min +4–8 % en Difícil y +5–10 % en Extremo. La regla exacta, para el simulador, en
  `docs/red_mob_v4_mod_estado.md`, «Aviso en movimiento». El banco acepta `FORJA_CAPITAN_NIVEL`,
  `FORJA_CAPITAN_ABLACION=avisoquieto` y `FORJA_CAPITAN_AVISO_VEL`.
- Guía (libro II, «El aviso»), especificación de combate y documento de la red al día.
- **Pruebas:** `AvisoMovilGameTests` (6): un zombi que avisa alcanza a quien retrocede andando, también por el
  ejecutor; quien corre escapa; una esquiva lateral a tiempo escapa; el escudo sigue parando; las cifras del factor
  de peso. 485 pruebas en verde; ninguna de las anteriores hubo que cambiar.

## 2026-09-30 — El Herrero Caído, más fuerte

Andy: «parece que puedes llegar a estar muy fuerte, o el Herrero Caído es muy débil, hazlo más fuerte». Un Mago o un
cuerpo a cuerpo de nivel 50 lo mataba en unos 20 s de golpes. Ahora un jugador de final de juego bien equipado (la
mejor arma estrellada, Guerrero de nivel 50, armadura estrellada) tarda, solo, **1:52 en Fácil, 3:17 en Normal, 4:25
en Difícil y 5:41 en Extremo** (antes 1:31, 2:29, 2:29 y 3:07), y con dos jugadores 1:27, 2:34, 3:25 y 4:20.

- **Se mide** (`balance/SmithFight`, sección «Herrero Caído» de `docs/EQUILIBRIO.md`): el jefe de verdad, vestido y del
  tamaño de cada pelea, por fase y aturdido; un jugador con la armadura de referencia; tiempo de pelea y supervivencia
  por nivel, solo y con dos, para cuatro equipos, antes y después.
- **Vida:** 400 de base (antes 320) × el nivel (0,9 / 1 / 1,3 / 1,6), +100 % por jugador de más y +25 % por tramo de
  equipo de quien le pelea. Pasado el techo de 1024, lo que sobra se lo quita a cada golpe.
- **Por fase:** más armadura (+3 / +6) y dureza (+2 / +4), golpes avisados ×1,2 / ×1,4 y esperas ×0,85 / ×0,7. Por nivel,
  golpes avisados ×0,85 a ×1,3 y esperas ×1,15 a ×0,8. Los avisos duran lo mismo.
- **Golpes avisados:** revés 9, onda 10, garfio 5, estrellas 10.
- **Furia** bajo un tercio: +15 % de velocidad, +20 % a su golpe normal, la forja violeta hasta el final y aviso en el
  chat.
- **Segunda oleada** de aprendices: +40 % de vida y +3 de armadura.
- **Reforjado:** 4 brasas en Difícil y Extremo, y 2 o 3 aprendices guardianes al empezar.
- **Se mantiene** el tercio de daño ajeno, el bloqueo de la arena, el Reforjado inmortal, el 30 % de penetración y el
  tope por golpe del 8 %. La forja reclama salta ahora con 32 de vida intentada por los grandes (la décima parte de sus
  320 de antes).
- Ningún golpe suyo quita la mitad de la vida a un jugador equipado; uno que se descuida dura de 4 a 10 s delante de él
  en Difícil.
- Guía: libros VI y VII con su vida por nivel, sus brasas, «Su fuerza» y la furia.
- Pruebas: `BalanceGameTests.herreroEnSuSitio` (la ventana de cada nivel) y `PeleaEstelarGameTests.theSmithGrowsWithTheFight`.

## 2026-09-30 — Iconos en los nodos de los árboles de clase

Andy: «se ve bastante bien el árbol, pero le faltan iconos». Cada nodo de los seis árboles lleva ahora su icono, elegido
en los datos (`icono` en `tools/arboles_datos.py`, que sale en `forja_arboles.json`); cambiar uno es editar esa tabla.

- **Menores**: un icono por estadística (`STAT_ICONS`): corazón propio para la vida, pluma para la velocidad, espada
  para el daño, escudo, arco, reloj, lapislázuli...
- **Notables**: un objeto que le pega (`NOTABLE_ICONS`), con las gemelas «menor» de los puentes iguales a las grandes.
- **Claves**: un medallón de 16×16 propio, dorado y del color de su clase, con un dibujo distinto por clave
  (`KEYSTONE_GLYPHS`; los dibuja `tools/arbol_iconos.py` en `textures/gui/arbol/`).
- **Habilidades**: el icono de la habilidad; las mejoras (II) llevan el mismo con una «II» dorada.
- **Origen y puentes**: el emblema de la clase (el arma forjada que la representa); el puente, el de la clase destino.
- **Forja**: objetos del mod (ascua, corazón de forja, sello, orbe de mejora, martillo del maestro...).
- La pantalla (`TalentTreeScreen`) dibuja el icono dentro del nodo a escala (nodos pequeños, iconos pequeños; las claves,
  grandes), atenuado si no se puede aprender aún y oscuro si está bloqueado; con el árbol muy alejado vuelven los
  colores de antes. La cabecera del tooltip lleva el icono junto al nombre.
- `tools/arboles.py --imagenes` compone los iconos en los dibujos de los árboles.
- Pruebas: `ArbolGameTests.everyNodeHasAnIcon` (todos resuelven a un objeto, emblema o textura que existe; claves y
  habilidades sin repetir) y la sección `iconos_*` de `FORJA_SOLO=arbol`.

## 2026-09-30 — Contrato 4.1: el jugador me apunta (bloque J)

Responde a `docs/mod_spec_mira.md`. Las redes v4 con `"revision": "4.1"` reciben 472 entradas: las 468 de siempre y,
al final, `jug_apunta_mi_caja`, `jug_apunta_dist/6`, `jug_golpe_listo` y `jug_amenaza`. Las redes 4.0 (sin campo) siguen
cargando con sus 468, y su contrato queda en `docs/red_mob_v4_contrato_v40.json`.

- **Un rayo por jugador y tick** (`ai/Aim`): la mirada real (ojos, `getViewVector`) hasta max(6, alcance), cortada por
  bloques y por la primera entidad que se pueda elegir; cada mob solo compara su id.
- Solo en Extremo (`Ladder.aimInputs()`) y con el mob percibiendo al jugador; si no, las cuatro valen 0 y no se lanza rayo.
- `docs/red_mob_v4_mod_estado.md`, «Mira (v4.1)»: ninguna regla del mod reacciona a que el jugador salte; qué dispara
  cada esquiva y cada bloqueo, con las cifras.
- Pruebas: `MiraGameTests`.

## 2026-09-30 — Árboles de clase grandes, tres habilidades y sin Herrero

Andy aprobó el diseño de `docs/ARBOLES.md` y pidió construirlo. Todos los números están en `tools/arboles_datos.py`:
`tools/arboles.py` los vuelca a `src/main/resources/forja_arboles.json`, y el juego los lee de ahí (`clase/ClassTree`).

- **Un árbol de 97 nodos por clase:**
  - núcleo de 6 puertas;
  - 3 ramas con 2 claves cada una (las dos de una rama se excluyen);
  - 3 sendas con las habilidades y un puente a otra clase;
  - 13 nodos de forja, iguales en todos los árboles.
- **Costes:** menores y notables 1, claves 2, lo de otra clase 2. El árbol entero cuesta 117.
- **Nivel y puntos:**
  - el tope pasa de 15 a **50**;
  - los niveles dan 1 o 2 puntos (70 en el 50) y los **hitos**, 33 más;
  - los hitos se conservan al cambiar de clase, se dan con efecto retroactivo y solo se gastan 1 por nivel;
  - en el tope son 103 puntos: se compra el 88 % del árbol;
  - la curva de experiencia es `30 + 5·(N−1)`: 7350 hasta el 50.
- **Hitos:** jefes (Herrero Caído, Guardián de Cuño, Warden, Wither, dragón...), el primer élite y el primer campeón,
  el Nether, el End, una ciudad antigua, el portal de la Forja Profunda, la forja perfecta, la maestría 5 y 10, la
  obra maestra y el maestro forjador. La lista, en el botón «Hitos» del árbol y en `/forja clase hitos`.
- **Tres habilidades:** V, B y **N** (cambiable en Controles), cada una con su mejora II en el árbol. Las seis nuevas:
  Torbellino, Abanico de dagas, Embestida de escudo, Meteoro, Escudo de luz y Flecha de red.
- **Nodos con efecto propio:** unos 70 (Inquebrantable, Duelista, Frenesí, Golpe de gracia, Muralla viva, Lazo vital,
  Hechizo encadenado, Florecer, Francotirador, Halcón...), cada uno con sus números en los datos y su código en
  `clase/ClassEffects`, `ClassEvents`, `ClassSkills` y donde actúan (curación, hechizos, esquiva, arcos, la forja).
- **Sin Herrero:** la forja está en todos los árboles (ventana, potencial, reparación, montadoras más rápidas, potencial
  de la montadora, carga de mejoras, Forja al rojo). **Temple de campaña** ya no es tecla: repara poco a poco la pieza
  forjada de la mano (la II también la armadura). Un guardado de Herrero se queda sin clase y elige otra.
- **La pantalla del árbol:**
  - se mueve arrastrando y se acerca con la rueda;
  - lo aprendido sale en dorado;
  - tooltips con números y el total de antes y después;
  - «Probar» planea sin gastar, y «Aplicar» y «Descartar»;
  - buscador y lista de hitos.
- **Vela del olvido** (vela, 2 amatistas y una lágrima de ghast): quita hasta 4 puntos de nodos del borde. El
  Medallón sigue igual.
- **Equilibrio:**
  - ningún nodo toca los factores de daño;
  - la clase nunca quita más del 60 % de un golpe;
  - los nodos pequeños del Mago cambiaron maná por defensa, para que el Mago del nivel 50 siga a la altura de la
    mejor arma cuerpo a cuerpo (`magiaEnSuSitio`);
  - `GearScore` cuenta los puntos gastados.
- **Guardados:** los nodos se guardan por id; un guardado del árbol pequeño carga, pierde sus talentos viejos (los
  puntos vuelven) y avisa.
- **Pruebas:** `ArbolGameTests` (datos, reglas, puntos, hitos, efectos, reinicio, migración, habilidades, GearScore)
  y `FORJA_SOLO=arbol` (la pantalla con el ratón y la tecla N).

## 2026-09-30 — La escalera de dificultad: el botón de Minecraft, con Extremo

Andy: «el mod es difícil y no es para todos los jugadores». La dificultad de Forja ya no es aparte: es la de Minecraft,
con un escalón más. Todo se decide en `difficulty/Ladder.java`, con la tabla de lo que enciende cada nivel.

| | Pacífico | Fácil | Normal | Difícil | Extremo |
|---|---|---|---|---|---|
| IA de los mobs vanilla | vanilla | vanilla | reglas de Forja | + redes v3 | + redes v4 |
| Avisos, turnos, anillo, capitán de reglas | no | no | sí | sí | sí |
| Penetración de armadura y presión | no | no | sí | sí | sí |
| Red del capitán y capitán 2 entero | no | no | no | no | sí |
| Carrera (+35 % × f) | +14 % | +14 % | +14 % | +21 % | +31,5 % |
| Veteranos, élites y mobs de Forja: daño / aguante | ×0,7 / ×0,7 | ×0,7 / ×0,7 | ×1 / ×1 | ×1 / ×1 | ×1,25 / ×1 |
| Cifras | Aprendiz | Aprendiz | Herrero | Herrero | Maestro |
| Escalado por equipo | no | no | sí | sí | sí |

- **El mismo botón.** El de dificultad de Minecraft (Opciones → Opciones del mundo, y crear mundo) pasa de Difícil a
  **Extremo**, y de ahí a Pacífico. Extremo es Difícil más una regla del mundo, `forja:extremo`, que se guarda con el
  mundo y llega a los clientes. Al crear un mundo extremo (hardcore), donde Minecraft bloquea el botón, el botón sigue
  abierto y alterna Difícil y Extremo.
- **Comandos.** `/difficulty` sigue igual (`/difficulty hard` es Difícil). `/forja dificultad extremo` (o pacifico,
  facil, normal, dificil) pone la dificultad y la bandera a la vez; sin nada, dice el nivel y sus cifras.
- **Fácil:** IA de Minecraft. Siguen saliendo veteranos y élites, pero ellos y los monstruos de Forja pegan ×0,7 y
  tienen ×0,7 de aguante (barra de postura y aliento de carrera); un mob vanilla corriente es vanilla del todo. Sin
  penetración ni presión, sin escalado por equipo. Los monstruos de Forja conservan sus reglas.
- **Normal:** las reglas de combate de Forja y la penetración, sin redes; corren menos (+14 %, por debajo de Difícil).
- **Difícil:** más las redes v3 de `config/forja/redes`; la carrera, +21 % (el bono recortado un 40 %).
- **Extremo:** el máximo. Más las redes v4 de `redes_v4` donde las haya, la red del capitán si está, todas las piezas
  del capitán 2, las entradas de puntería v4.1 cuando existan, y las cifras de Maestro (más veteranos y élites, más
  daño). La carrera, +31,5 %. Solo se recorta el bono: andar no cambia. El simulador copia Extremo; la fórmula está en
  `docs/red_mob_v4_mod_estado.md`.
- **Configuración.** `dificultad` (Aprendiz, Herrero...) ya no es la dificultad: ahora es "auto" y solo fuerza las
  cifras, para administradores; `nivel` fuerza el nivel de todo el servidor. Un `config/forja.json` de antes se migra
  una vez: su HERRERO, el valor por defecto, pasa a "auto"; otra elección se queda como forzado y se avisa en el log.
- Libro II, capítulo «La dificultad»: la escalera con sus números.
- Pruebas: `DificultadEscaleraGameTests` (cada nivel enciende justo lo suyo, la carrera por nivel, Fácil y los
  fuertes, la bandera guardada y leída, `/forja dificultad`); las del servidor corren en Extremo con las cifras de
  Herrero (`TestDefaults`). Cliente: `FORJA_SOLO=dificultad ./gradlew runClientGameTest` hace clic en el botón al
  crear el mundo y en las opciones, crea un mundo extremo en Extremo, lo cierra y lo abre otra vez. Capturas en
  `Forja_capturas_mejoras/dificultad`.
- De paso: `BlazeGameTests.blazeObservationHas324FiniteInputs` fallaba a veces porque veía la bola de otra prueba de
  blazes a menos de 48 bloques; ahora solo mira las bolas cuando la suya es la única.

## 2026-09-30 — El tridente lanzado: sin duplicarse y de punta

Andy: al lanzar el tridente, chocaba con el suelo, «se rompía» y quedaba tirado, y además volvía al inventario.

- **La causa:** al acabar el vuelo de un arma sin Retorno contra un bloque, `ThrownHead.stopFlight` soltaba en el
  suelo una *copia* del arma y luego `finish` devolvía al inventario el original que aún llevaba dentro: dos armas de
  un lanzamiento. El tridente no tenía Retorno, así que le pasaba siempre. Y al revés: si el vuelo acababa por tiempo,
  por morir el que lo lanzó o por cambiar de dimensión, el arma desaparecía.
- **El arreglo:** el arma lanzada es la única copia y acaba en un solo sitio. Si cae al suelo, sale de la entidad
  antes de soltarse; si vuelve, la entidad la suelta antes de entregarla. Lo que no puede volver a la mano va a
  `upgrade/ThrowReturns`: al inventario si hay hueco, o se guarda con el mundo a nombre del que la lanzó y se le
  entrega en cuanto esté vivo, conectado y con hueco (con un aviso en pantalla si tiene el inventario lleno). Cubre
  inventario lleno, muerte, desconexión, cambio de dimensión, fin del tiempo, el vacío y un chunk que se descarga.
  La entidad ya no cruza portales.
- **El tridente vuelve siempre**, tenga Retorno o no. Las demás armas lanzadas siguen igual (sin Retorno se quedan
  en el suelo, una sola vez).
- **De punta:** el tridente, la daga y la lanza vuelan con la punta por delante, orientados por su movimiento como
  una flecha y dibujados con dos planos cruzados para verse desde cualquier lado. A la vuelta vienen con el asta por
  delante, como el tridente leal de vanilla. Hachas, cabezas lanzadas y escudos siguen girando.
- Pruebas: `TridenteGameTests` (suelo, alcance máximo, vacío, inventario lleno, muerte en vuelo, daga sin Retorno,
  guardado) y `FORJA_SOLO=tridente` para las capturas.

## 2026-09-30 — Los aprendices del Herrero Caído, con equipo de final de juego

Andy: «los aprendices no tienen armadura, siempre llevan las mismas armas y a veces hasta de madera; es el final del
juego, que lleven equipo muy bueno».

- **Armadura completa.** Casco, pechera, grebas y botas forjados en aleaciones de final de juego (acero estelar,
  solacero, lunacero, obsidiacero, damasco, netherita, acero vivo, almacero; los forros, de las mismas). Ocho conjuntos;
  cada aprendiz lleva uno y a veces el casco o las botas del vecino. Con la placa de verdad, el +8 de armadura de
  los campeones se les quita. Pechera con Protección.
- **Armas distintas.** Espada, espadón, hacha, martillo, maza, lanza, guadaña, daga y mangual, sin repetir en la
  oleada, y arco para uno de cada tres. Nada de madera, piedra, hierro ni cuero: ni en el arma, ni en los mangos, ni en
  el escudo (lo llevan los de una mano: espada, hacha, maza, lanza y daga). Cada arma, entre 2 y 3 mejoras de su
  oficio, del 50 al 90 % (lo que se alcanza sin fundente) y dentro de lo que cabe en la pieza (Potencial 80),
  y Maestría 5.
- **Los arqueros disparan.** Un esqueleto atrofiado no sabe tirar con arco; uno normal sí (y los mixins ya le enseñan
  que un arco forjado es un arco). Por eso el cuerpo sigue al oficio: los de cuerpo a cuerpo salen como esqueletos
  atrofiados y los arqueros como esqueletos. No hay ballesteros: solo un saqueador dispara ballesta.
- **No sueltan nada.** Probabilidad de soltar una pieza: 0 (`ApprenticeKits.PIECE_DROP_CHANCE`) y ya no dan la
  «leyenda» de campeón al morir.
- **Equilibrio.** Siguen siendo campeones: a un jugador con netherita completa le quitan entre 2 y 5,5 puntos de
  vida por golpe, ningún golpe pasa de la base del 30 % de la armadura (`Pressure.total`), y a ellos un golpe de
  jugador no les quita más del 12 % de su vida (tope de campeón).
- Pruebas: `AprendicesGameTests` (armadura completa, armas distintas, sin madera ni piedra, los arqueros disparan,
  equilibrio). Captura: `FORJA_SOLO=dimension FORJA_PELEA_SOLO=1 FORJA_APRENDICES=1 ./gradlew runClientGameTest`.

## 2026-09-30 — Mangos y ataduras de cualquier material

Andy: «todos los materiales para todas las piezas; al final el jugador decide si quiere un mango ligero usando
material pesado, hay que permitirle experimentar».

- **Cualquier material, cualquier forma.** El mango y la atadura pesados y ligeros aceptan todo lo que acepta el normal:
  maderas, hueso, cuero, cristales, metales y los míticos. La variante es la forma (contrapeso o remaches; fino o hueco).
- **Cada material se trabaja como siempre.** Lo que se talla (madera, hueso, cuero, cristales...) se corta en la mesa de
  piezas en cualquier forma. El metal se cuela: la plantilla de cualquier variante va a la caja de colada con acero
  refractario y sale su molde (también sirve una pieza tallada de esa forma, como con las normales).
- **El peso es material × forma.** Cada pieza pesa la densidad de su material (MaterialCombat) por su forma: mango ×2,3
  pesado y ×0,35 ligero, atadura ×1,6 y ×0,4. En una espada de hierro el mango pesado de hierro suma un 18 % y el ligero
  quita un 9 %; de roble, un 11 % y un 5 %; de netherita, un 28 % y un 14 %. Un mango pesado de roble es un contrapeso
  modesto y uno ligero de netherita sigue pesando más que uno ligero de roble.
- Lo demás del material pasa igual que en una pieza normal (durabilidad, rasgo y su encantamiento, potencial de las
  piezas coladas), y el trato de la forma (combat/Grip) va encima, sin cambios.
- **Tooltips:** la pieza suelta dice cuánto pesa en su material («Pesa 0,54 veces un mango normal de hierro (material
  1,55 × forma 0,35)») y su trato con la velocidad que ese peso da o quita; la plantilla, que vale cualquier material.
- **El yunque**, «Mangos y ataduras: pesados o ligeros»: cualquier material sirve y qué cambia.
- Lo guardado sigue cargando: las piezas y herramientas con variantes de antes se leen igual.
- **EQUILIBRIO.md:** tabla compacta de cinco materiales (madera, hueso, hierro, netherita, vidriacero) × tres formas para
  espada, hacha y mazo. La guardia mide 120 combinaciones (también la lanza) y falla si una variante gana a la normal de
  su material en todo, o si una combinación gana a todas las demás de su tipo. La lanza de mango ligero de vidriacero
  ganaba a la normal en daño contando sólo los aturdidos (números enteros); ahora la guardia mira también el equilibrio
  quitado por segundo, que es lo que paga el mango ligero.
- Pruebas: `MangosGameTests` (tres nuevas y dos rehechas: cada variante en cada material, mango ligero de netherita colado y pesado de roble
  tallado montados y desmontados, peso material × forma, rasgos y números que pasan, lo guardado de antes) y
  `FORJA_SOLO=mangos_todos` (mesa, estrella, en mano y una pared de materiales).

## 2026-09-30 — Presión de armadura con balance e indicador en el HUD

- **La presión empieza en 0** y crece con cada golpe que te llevas hasta un **máximo del 60 %** (antes 70 %). Cada golpe
  suma 0,065 (un golpe parado, la mitad): de 0 al tope hacen falta unos **10 golpes** (9,2). Ningún golpe suma más de un
  paso.
- **Recuperación poco a poco.** Si pasan 3 s (60 ticks) sin que te den, baja 0,0067 por tick: del 60 % a 0 en **90 ticks
  (4,5 s)**. Un golpe nuevo parte de donde iba y vuelve a esperar los 3 s.
- **Arma, rango y presión se encadenan** (antes se cogía la mayor entre rango y presión):
  `base = mín(0,30; 1 − (1 − arma)(1 − rango))` y `total = mín(0,60; 1 − (1 − base)(1 − presión))`; después, la dureza
  frena el resultado como antes. El Brecha del arma cuenta como parte del arma.
- **Balance (Andy: "no te pueden quitar el 60 % de la armadura de un golpe").** El arma y el rango de un monstruo juntos
  nunca pasan del 30 % de tu armadura en un golpe (`penetrationBaseMax`); lo de los tres juntos nunca pasa del 60 %
  (`penetrationTotalMax`). Un hacha de campeón (35 % y 35 %, que sin tope serían el 58 %) a un jugador sin presión se lleva
  el 30 %; solo una paliza seguida llega al 60 %.
- **Indicador en el HUD:** un escudito de 9 x 10 píxeles sobre la fila de la armadura, entre su final y el hambre (no tapa
  corazones, maná ni estamina). Muestra cuánta armadura aguanta (`1 − presión / 0,60`): entero con 0 de presión, se vacía
  con cada golpe (con un destello y una estela de lo perdido), pasa de acero a naranja y a rojo, y se rellena poco a poco al
  bajar la presión. A 0 **se rompe**: se parte por una grieta, suena un crujido corto y salen esquirlas; sigue roto mientras
  la presión esté al tope y, en cuanto empieza a bajar, las mitades se juntan. Se oculta sin presión y sin armadura puesta,
  en creativo, en espectador y con F1. La presión ya viajaba al cliente (`CombatAnim.PRESSURE`): no hay mensaje nuevo.
- Se quita la línea roja de presión bajo la barra de estamina (la sustituye el escudo).
- **Configuración:** `pressurePerHit` 0,065, `pressureMax` 0,60, `pressureDrainPerTick` 0,0067, `penetrationBaseMax` 0,30,
  `penetrationTotalMax` 0,60. Los valores por defecto antiguos (0,10, 0,70, 0,02) se migran solos; lo que alguien haya
  escrito a mano se respeta.
- **Guía:** el libro II (El arte del combate) explica la presión nueva, el tope del 30 % y el escudo; la sección de los
  rangos lo menciona.
- **Pruebas:** 8 nuevas en `PresionGameTests` (empieza en 0, sube un paso por golpe y topa en 0,60, parado suma la mitad,
  fórmula encadenada con cifras a mano, los dos topes, hacha de campeón al 30 %, espera de 60 ticks y bajada gradual,
  golpe nuevo en mitad de la bajada) y 2 ajustadas. Captura: `FORJA_SOLO=presion`.

## 2026-09-30 — Capitán 2

- **Visión compartida.** Lo que ve un monstruo del grupo lo sabe el grupo: los que no ven al jugador y están a 32 bloques
  del capitán reciben dónde lo vio el último que lo vio, como si lo hubieran oído. Los que lo habían perdido lo buscan allí.
- **Sucesión (decisión de Andy).** Si muere el capitán, a los 3 segundos manda el veterano con más vida, como capitán
  interino: decide la mitad de veces, nunca ordena cargar y la moral del grupo baja un poco mientras manda. Su
  estandarte es plateado. Solo una sucesión por pelea, y un élite que quede lo releva a los 10 segundos.
- **Protección del capitán.** Se queda a 7 bloques detrás de los suyos, se retira a 14 con menos del 35 % de vida y, con
  el jugador cerca, lleva dos escoltas (primero los de escudo) que atacan si hay un turno libre.
- **Órdenes nuevas** para una red de capitán: cerrar las salidas, ir a por el jugador más herido y la retirada falsa (se
  retiran 2 segundos y vuelven todos a la vez con un grito). Los monstruos las entienden como las órdenes de siempre, así
  que sus redes no cambian.
- **Capitán visible.** Cada orden nueva es un grito y una nube de polvo dorado sobre el capitán.
- **Red del capitán, contrato 2** (253 entradas, 68 salidas): ve la orden que daría el capitán de reglas y solo la cambia
  si su cabeza de "mando" lo pide, así que una red recién empezada juega igual que las reglas. Las redes del contrato 1
  siguen cargando.
- Cada pieza tiene su interruptor (`iaCapitanVision`, `iaCapitanSucesion`, `iaCapitanProteccion`, `iaCapitanOrdenes2`,
  `iaCapitanVisible`), todos activados.
- **Medido en el mod** (160 peleas): sin capitán 170 de daño por minuto, capitán de reglas 188 y capitán 2 **123**. Toda la
  bajada es de la protección (sin ella, 194): el élite, que es el que más pega, se queda atrás. Contra el jugador de prueba
  no compensa. Queda a decisión de Andy si se apaga.
- **Arreglos de paso:** un monstruo que va a su puesto de formación ya no se queda a un bloque y medio; las pruebas de
  cliente ya no fallan al entrar (la medida del capitán miraba al jugador antes de que tuviera número).
- **Pruebas:** 8 nuevas (428 en total). Captura: `FORJA_SOLO=capitan2`.

## 2026-09-30 — Una sola espera entre golpes

- **La espera tras un golpe ya no se salta cambiando de forma de atacar.** Un monstruo podía golpear de dos maneras
  (su ataque normal o una táctica), y cada una llevaba su propia espera. Tras un golpe de una, la otra podía avisar al
  instante: 1 de cada 9 veces, el siguiente aviso llegaba antes de 20 ticks. Ahora la espera es una sola y corre siempre.
- **Medido en el mod** (160 peleas): ya no queda ningún aviso a menos de 20 ticks del anterior del mismo monstruo. El
  daño por minuto queda igual sin capitán (166) y baja un poco con el capitán de reglas (197 → 188).
- **La medida del capitán** ahora apunta, entre dos golpes de cada monstruo, cuánto espera, dónde corre la espera y qué
  hace mientras.
- **Pruebas:** 1 nueva.

## 2026-09-30 — La espera tras un golpe ya no se salta

- **La espera tras un golpe se respeta.** Un monstruo de cuerpo a cuerpo espera un segundo tras cada golpe, y más si
  va cargado. Esa espera se perdía cada vez que volvía a atacar tras un momento haciendo otra cosa, y 1 de cada 5
  avisos llegaba antes de tiempo. Ya no pasa.
- **Un golpe avisado desde una táctica también se termina.** Casi todos se cortaban cuando el monstruo volvía a ver al
  jugador o cambiaba de idea. Ahora se terminan.
- **Medido en el mod** (8 contra el jugador de prueba, 160 peleas): los avisos por pelea bajan de 17 a 16 sin capitán
  y de 29 a 24 con el capitán de reglas. El daño por minuto queda en 172 sin capitán y 194 con el capitán de reglas.
- **La medida del capitán** ahora apunta de dónde sale cada aviso y a qué distancia empieza. También cuánto tarda desde
  que el monstruo llega a su alcance, cuánto espera entre golpes, cuántos avisan a la vez y por qué uno a su alcance no
  avisa. Las tablas están en `docs/red_mob_v4_mod_estado.md`.
- **Pruebas:** 1 nueva.

## 2026-09-30 — Un golpe avisado se termina, y la pinza del capitán, apagada

- **Un golpe avisado se termina.** Los monstruos ya no cortan un golpe avisado a medias para rodear, retirarse,
  esperar o hacer un especial. Solo lo cortan si quedan aturdidos, si es una finta en su primera mitad, si mueren o si
  pierden al jugador. Así se lee y se contesta, como pide el combate. Antes cortaban casi 3 por pelea; ahora, menos de 1.
- **La pinza del capitán de reglas, apagada** (`iaCapitanPinza`). Medida otra vez, ya con flechas: no gana (204 de daño
  por minuto con ella y 206 sin ella).
- **El "daño sin explicar" de la medida era daño real.** Son los multiplicadores de Forja (golpe en la cabeza ×1,3,
  élite ×1,3, personalidad…), que el evento de Fabric no ve. La medida ahora cuenta lo que baja de verdad la vida del
  jugador.
- **Pruebas:** 1 nueva, un golpe avisado se termina aunque el monstruo quiera huir.

## 2026-09-30 — Los zombis ya no se quedan parados antes de atacar

- **El fallo:** un monstruo que volvía a atacar tras esperar o rodear podía quedarse quieto hasta un segundo: el
  ataque cuerpo a cuerpo de vanilla solo mira si puede empezar cada 20 ticks. Pasaba en el 12 % del tiempo que iban al
  ataque. Ahora lo mira cada 4 ticks. Contra el jugador de prueba hacen un 8 % más de daño sin capitán y un 4 % más
  con él.
- **La medida del capitán estaba mal:** el jugador de prueba no estaba en el mundo, así que las flechas lo
  atravesaban. Ahora sí está. Con las flechas, un grupo de 8 hace unos 168 de daño por minuto sin capitán y 190 con el
  capitán de reglas.
- **La medida ahora cuenta más cosas por pelea:**
  - avisos empezados y los que llegan, y por qué fallan los otros (empujado, el jugador se movió, cortado);
  - embestidas, y cuántas tocan;
  - daño por tipo, flechas disparadas y flechas que dan;
  - velocidad del zombi que persigue.

  Opciones: `FORJA_CAPITAN_ABLACION=mochila` da a los monstruos la mochila con la que aparecen;
  `FORJA_CAPITAN_JUGADOR=fuera` deja al jugador fuera del mundo, como antes.
- **Pruebas:** 1 nueva. La de coger la espada del jugador ahora sujeta al zombi mientras la espada aún no se puede
  coger, porque con un turno libre va antes a por el jugador.

## 2026-09-30 — Las pruebas de servidor, estables

Con la máquina cargada (entrenando redes, otras compilaciones) fallaban de vez en cuando unas cuantas pruebas y al
repetir pasaban. Cada una tenía su causa, y ninguna era la carga:

- **El mundo de pruebas cambiaba de hora y de tiempo.** Se guarda entre ejecuciones con su reloj y su clima: unas
  veces era mediodía, otras de noche o lloviendo (zombis que ardían o no, la luz de una antorcha, lluvia sobre los
  blazes). Ahora todas las tandas corren en una mañana despejada que no avanza (`test_environment` `estable`, y
  `minecraft:default` apoyado en él), y una prueba lo comprueba.
- **Unas pruebas se metían en otras.** Las pruebas de una tanda están a 5 bloques: las escuadras pasaban monstruos
  al jugador de la prueba de al lado (`iaRepartirObjetivos`), el jefe devolvía al terminar la probabilidad de
  veteranos y élites a todas las demás, el esqueleto ponía `skeletonChargedEvery = 1` a todos, la antorcha apagaba
  `mobGriefing` para toda la tanda, el Enjambre contaba los proyectiles de los vecinos, las pruebas del Herrero al
  recoger se llevaban los aprendices de la de al lado, dos pruebas anchas sin margen barrían los mobs del vecino y
  los blazes soltaban trozos del mundo que no habían forzado ellos. Ahora hay una base común para el servidor de
  pruebas (`TestDefaults`), la antorcha va en una tanda propia y cada prueba mira y recoge solo lo suyo.
- **Un fallo de verdad (el husk):** tras una finta, un zombi que se metía en la casilla de su objetivo no volvía a
  golpear: el objetivo vanilla se paraba en cuanto terminaba el camino y soltaba el aviso. Un golpe avisado ahora se
  termina mientras el objetivo siga vivo (`MeleeAttackGoalMixin`).
- **Azar y sitio:** el paseo vanilla de los primeros ticks movía al zombi del mangual, al de la percepción y al del
  salto atrás antes de la prueba; el esqueleto salía de élite o se ponía a hacer especiales a mitad del tiro
  cargado; la sala oscura de la antorcha se salía de los trozos que se actualizan y el zombi se quedaba congelado; un
  blaze disparaba justo cuando el jugador se daba la vuelta.
- **Los blazes, congelados:** su suelo de vuelo se sale de la caja de la prueba, y un trozo recién forzado tarda en
  actualizar entidades (con la máquina cargada, 90 tics y más). El blaze nacía ahí y se quedaba quieto: el de vanilla
  no disparaba, el entrenado no subía a su banda. Ahora el vuelo empieza cuando su suelo ya actualiza entidades.
- **Rendimiento v4:** la horda medida no era de 30. Los creepers explotaban y los esqueletos y arañas caían del
  borde de la plataforma (está 24 bloques en el aire): la ventana empezaba con 30 y acababa con 19. Ahora hay borde,
  los creepers no llegan a explotar, los mobs tienen semillas fijas, la red se calienta 300 ticks y el tope se
  compara con la mediana de 10 trozos, corregida por una calibración que mide cuánto más lenta va la máquina. Con la
  horda entera y la red ya caliente mide entre 1,4 y 2,1 ms (tope 2,5).
- Para cazar una prueba: `FORJA_PRUEBAS='forja-test:*torch*' ./gradlew runGametest`, y con `FORJA_VERIFICAR=1`
  corre 400 copias a la vez.

## 2026-09-30 — Los grupos sin capitán ya no dan vueltas: un turno libre se usa

- **El fallo:** contra un jugador que se mueve, los monstruos sin turno daban vueltas hasta su hueco del anillo, el
  flanqueador rodeaba hasta la espalda, y los que tenían la postura cargada o acababan de golpear se apartaban. Lo
  hacían **aunque tuvieran un turno libre para atacar**. Los turnos estaban libres el 96 % del tiempo.
- **Ahora:** quien tiene un turno libre va a por el jugador. Rodear, flanquear, apartarse por la postura y ceder el
  sitio tras golpear quedan para los que esperan turno.
- **Medido en el mod** (8 contra el jugador de prueba, 160 peleas):
  - sin capitán, de 37,5 a **70,6** de daño por minuto;
  - con el capitán de reglas, de 77,7 a **114,2**.
- **Opción nueva `iaTurnoGrupoGrande`** (activada): con 6 o más monstruos a por el mismo jugador, un turno más. El
  número se cambia con `iaGrupoGrandeMin`.
- **Opción nueva `iaCapitanPinza`** (activada): la pinza del capitán cuando el jugador se aleja. En el mod suma unos 14
  de daño por minuto.
- **Arreglado:** el capitán cargaba "con el jugador aturdido", pero los jugadores no tienen barra de postura y eso no
  pasaba nunca. Ahora cuenta como aturdido quien tiene lentitud II o más, como la que deja la parada con escudo de un
  monstruo.
- **Pruebas:** 3 nuevas.
- **La medida del capitán** ahora cuenta cuántos monstruos llegan al jugador y cuántos turnos se usan. Con
  `FORJA_CAPITAN_TRAZA=<archivo>` escribe cada tick, monstruo por monstruo.

## 2026-09-30 — El capitán de reglas ya ayuda, y el paso v4c del simulador

- **El capitán de reglas empeoraba a su grupo.** Mandaba CERCAR casi siempre: los de delante y de los lados esperaban
  a 6 bloques o más y nadie entraba, y la carga casi nunca llegaba. Con él, un grupo de 8 hacía 0,6 de daño por minuto
  y sin él, 37,5.
- **Capitán de reglas nuevo.** Solo da una orden cuando ayuda:
  - **carga** en cuanto el jugador queda expuesto (de espaldas a uno del grupo, usando un objeto, cargando un golpe,
    aturdido o con poca vida): todos entran a la vez, con el grito y un turno más durante 2 s, y luego 5 s de descanso;
  - **pinza** si el jugador se aleja del grupo;
  - **retirada** con la moral baja, **asedio** si se sube a un pilar y **emboscada** de noche a oscuras;
  - si no, **nada**: el grupo pelea como sin capitán, con el anillo y los turnos.
- **Medido en el mod:** 8 contra un jugador de prueba, 80 peleas. Con el capitán nuevo hacen 77,7 de daño por minuto
  (sin capitán, 37,5) y "matan" en 30 s en 71 de 80 peleas (sin capitán, en 36). Las tablas están en
  `docs/red_mob_v4_mod_estado.md`.
- **Opción nueva `iaCapitanReglas`** (activada). Apagada, el capitán sigue ahí (polvo dorado, moral, el golpe de moral
  si muere) pero no da órdenes. Una red de capitán (`red_capitan.json`) manda siempre, esté como esté la opción.
- **Paso v4c del simulador:**
  - un mob que cambia de familia en plena pelea (un esqueleto que saca la hoja o vuelve al arco) cambia de red y su
    memoria vuelve a 0, también si entretanto peleó por reglas;
  - el repuesto puede ser un arco, y se suelta al morir (el recogido, siempre);
  - solo los tiradores cogen arcos del suelo: el arco, esqueletos, strays y bogged; la ballesta, saqueadores y piglins.
    Un ahogado con la mano vacía ya no coge un arco, y un zombi que puede recoger botín tampoco.
- **Arreglado:** un esqueleto que cambiaba el arco por la hoja durante la andanada, el salto atrás con tiro o la flecha
  de empuje tumbaba el servidor. Ahora ese tiro se pierde.
- **Pruebas:** 6 nuevas (3 del capitán y 3 del paso v4c), una rehecha (el capitán ya no forma un muro sin motivo) y la medida `CapitanMedidaGameTests`, que solo corre con
  `FORJA_CAPITAN_MEDIR=<archivo>`. `FORJA_FILTRO='<selector>' ./gradlew runGametest` corre solo las pruebas que casan.

## 2026-09-30 — Los libros III a VII, la estantería del herrero y el atril

La guía ya son ocho libros en la estantería. Cada uno se fabrica, y su receta se aprende con el progreso.

- **III · La fundición** (38 páginas; se aprende con la primera mejora). Primeras aleaciones, la línea de fundición
  entera, y la mesa mayor como meta.
- **IV · La mesa mayor** (49 páginas; se aprende al construir la mesa mayor). Potencial, sinergias y pactos (en
  sombra), maestría, el herrero, técnicas, tu taller y accesorios.
- **V · Clases** (22 páginas; se aprende con la primera parada). Las clases se abren la primera vez que abres el
  libro. Hasta entonces, la K lo avisa y el servidor no deja elegir. Quien ya tenía clase la conserva.
- **VI · El Bastión y el Herrero** (27 páginas; se aprende al encontrar una ruina). También lo vende el Forjador
  de nivel 3, siempre, por 12 esmeraldas. Trata del mundo, los encargos, las ruinas, la historia del Herrero, el
  castillo del Gremio y el portal estelar. El Guardián de Cuño sale en sombra hasta que lo ves.
- **VII · El Cementerio entre Estrellas** (18 páginas; se aprende al encender el portal estelar o al entrar en su
  mundo). Trata del viaje, la pelea y la recompensa, y trae la ficha del Herrero Caído (en sombra hasta verlo). Al
  encenderse el portal aparece junto al marco el **atril del Herrero**, y un clic en él abre el libro.
- **La estantería del herrero**: un sitio por libro. Un clic con un libro de la forja lo guarda en su sitio, y un
  clic en su sitio lo devuelve. Un comparador cuenta los libros que tiene. Al romperla, suelta la estantería y los
  libros.
- **El yunque** explica los mangos y ataduras pesados y ligeros, con el dibujo de ambos mangos.
- **El camino del herrero** gana un paso: técnica.
- **Prueba del cliente:**
  - los ocho libros se maquetan limpios;
  - hay capturas de todos los libros y de la estantería con el atril.
- **Hojas de contactos**, con copia .jpg de menos de 3 MB: `E:\IA\Claude\Forja_capturas_mejoras\libros\`.

## 2026-09-30 — Probador por grupos, y pactos y sinergias en sombra

- **Respuestas de Andy sobre el probador:**
  - **Mejoras:** sin "descubrir". Todas se ven.
  - **Grupos:** las listas del probador van por secciones (herramientas, armas, armadura, para todo...), plegadas:
    cada grupo enseña sus nombres en una o dos líneas, y un clic en su cabecera lo abre con lo que hace cada mejora,
    hasta dónde sube y su receta. Una espada nueva pasa de unas 10 páginas a 2.
  - **Pactos en sombra** hasta que ese jugador los abre con su ofrenda: "Pacto sellado", sin nombre, efecto, receta
    ni descripción. Solo se ve la ofrenda que lo abre. Vale para el capítulo de pactos, la lista de mejoras del
    catálogo y el probador.
  - **Sinergias en sombra** hasta que despiertan en algo que lleves encima: "???" en su capítulo, y en el probador
    "despertaría una sinergia que aún no conoces". Lo despertado se guarda en el cliente.
  - El tomo de creativo lo enseña todo.
- **Libro II:** en la página de resistencias, un monstruo del mod que aún no has visto sale como "???", igual que en
  el bestiario.
- **Prueba del cliente:**
  - sin pactos abiertos, todos en sombra en el catálogo y todos a la vista en el tomo;
  - abierto uno en el servidor, solo ese sale de la sombra;
  - un grupo del probador abierto se maqueta limpio.



## 2026-09-30 — Cada punta de flecha, la suya

Andy: "las flechas son todas iguales". Ahora la punta decide cómo vuela y qué hace (`combat/ArrowTips`), leído
del material:

- **Peso:** una punta densa pega más (hasta +8 % la netherita), sale algo más lenta, cae antes (hasta ×1,6) y
  empuja más; una ligera vuela plana (la madera cae al 60 %) y pega algo menos.
- **Dureza:** una punta dura atraviesa hasta un 30 % más de armadura (diamante, netherita, aceros).
- **Especial**, por el rasgo del material: fuego (escoria), brasa (cinerio), sangrado (cuarzo, damasco), resina
  (Lentitud II), marea (prismarina: vuela bajo el agua), salto (púrpur: el blanco se teletransporta), llanto
  (Debilidad), eco (atraviesa a uno más), estrella (casi no cae), hueca (encuentra el hueco de la armadura),
  chispa (voltaico: salta al enemigo de al lado), buscadora (almacero: se tuerce hacia lo que te caza), vidrio
  (más rápida y plana, se rompe al acertar), sol y luna (más daño a pleno sol o a oscuras), viva (te cura),
  fortuna (a veces sale crítica); y el oro marca (brilla), la amatista devuelve maná y el cobre da una descarga en
  lo mojado.
- **Se ve:** la cabeza de la flecha en vuelo va del color del material, y las especiales dejan una estela de su
  color. El tooltip dice el peso, la caída, la armadura que atraviesa y el especial.
- Frente a vanilla: ninguna punta se aleja más de un 20 % del daño de una flecha llana, y ningún especial dura
  más que el efecto de una flecha con poción.
- Pruebas `FlechasGameTests` (cada punta distinta y cada especial hace lo que dice) y `FORJA_SOLO=flechas`.

## 2026-09-30 — La magia, en su sitio

Andy: las armas mágicas estaban rotas (el báculo mataba tres veces más rápido que el cuerpo a cuerpo, y con
Enjambre un warden caía en un segundo). Ahora, medido en `docs/EQUILIBRIO.md` (*Magia frente al cuerpo a cuerpo*):
sin clase mágica la magia no mata antes que la mediana cuerpo a cuerpo y sostiene menos de una sexta parte de su
daño; un Mago con sus talentos queda entre la más rápida y la mediana.

- **Enjambre y Prisma reparten el hechizo** entre sus proyectiles en vez de copiarlo: los cinco de Enjambre juntos
  hacen lo que uno solo. Los laterales no son golpes del arma (no tiran Tormenta ni Vampirismo cada uno).
- **Báculo:** espera 10 ticks (antes 6), proyectil `2,5 + 0,5 × daño del núcleo` (antes `4 + 0,9 ×`), 10 de
  maná (antes 8). Todos los proyectiles atraviesan la invulnerabilidad de medio segundo.
- **Grimorio:** espera 20 ticks, área `4 + 1,15 × daño del núcleo`, mordisco de la runa al 15 % (antes 25 %),
  20 de maná (antes 30) y **una runa por lector**: la nueva apaga la anterior (antes se amontonaban).
- **Mejoras:** Sobrecarga +50 % al 100 (antes +100 %), Resonancia 20 % (antes 50 %), Conjuro veloz −20 % de
  espera (antes −40 %), Descarga +10 % por cada 10 de maná y como mucho el doble (antes +25 % sin techo). Lo que
  las mejoras de arma y los encantamientos añaden a un hechizo cuenta a la mitad.
- **Farol:** 15 de maná, curas algo menores y a quien lo usa un 35 % del anillo (antes la mitad).
- **Mago:** daño de hechizos +10 %, espera −10 %; Catalizador +10 %, Mente clara −5 % de espera, Economía arcana
  sólo abarata. Los monstruos con báculo o grimorio siguen como estaban.
- Pruebas: `MagiaGameTests` (Enjambre reparte, una runa por lector, los monstruos igual) y
  `BalanceGameTests.magiaEnSuSitio`. El guardián de dominados juzga la magia con el Mago.

## 2026-09-30 — El maná vuelve lentísimo sin clase mágica

Andy: "el sistema actual es igual a no tener maná, se debe regenerar lentísimo si no tienes la clase".

- **Sin clase mágica**, el maná vuelve a 0,4 por segundo mientras lanzas y a 0,8 por segundo tras 5 s sin lanzar
  (antes 6 y 20 tras 2 s): una barra vacía tarda unos dos minutos, no siete segundos. Los valores viejos de
  `config/forja.json` que nadie tocó pasan solos a los nuevos.
- **El Mago** lo recupera ×6 (una barra de 125 en ~26 s) y con Mente clara ×7. **El Curandero**, ×4 (~36 s) y con
  Serenidad ×5. La clase multiplica también lo que den las mejoras.
- **Mejoras de ritmo más flojas**, porque ya no deben convertir a cualquiera en mago: Flujo +15 % por pieza (antes
  +25 %), el conjunto de eco +30 % (antes +40 %) y Meditación +40 % (antes +60 %).
- **Las muertes** siguen igual (8 + 0,4 por punto de vida, hasta un 30 % de la barra, un 3 % cada 5 ticks) y ahora
  son la fuente de maná de quien no es mago: un zombi devuelve dos proyectiles.
- La guía (capítulo del maná y libro II) y `docs/CLASES.md` (tabla *El maná por clase*) lo cuentan. Prueba
  `manaRegenDependsOnTheClass`.

## 2026-09-30 — Mangos y ataduras pesados y ligeros

Andy lo aprobó como una **elección, no una mejora**: el mango y la atadura normales siguen siendo el punto medio.
Números y fórmulas en `combat/Grip`; el balance medido, en `docs/EQUILIBRIO.md` («Mangos y ataduras»).

- **Cuatro piezas nuevas**: mango pesado, mango ligero, atadura pesada y atadura ligera. Van donde va la normal en
  cualquier receta (la estrella, la montadora, cambiar piezas, desarmar) y la pieza recuerda cuál lleva.
  - **Mango pesado** (contrapeso): +20 % golpe cargado, +25 % postura, +30 % empuje. Pesa un 18 % más: el golpe
    a plena fuerza llega más tarde (unos −6 a −8 % de velocidad, según el arma) y cada golpe cuesta un 15 % más de
    estamina (el cargado, un 10 %).
  - **Mango ligero**: pesa un 15 % menos (unos +5 a +6 % de velocidad) y cada golpe cuesta un 10 % menos de
    estamina; −20 % postura, −25 % empuje y −10 % golpe cargado.
  - **Atadura pesada** (remaches y bandas): +20 % durabilidad, −20 % estamina al bloquear, la guardia rota vuelve
    en la mitad de tiempo y un golpe de escudo ya no te quita la carga. Pesa un 8 % más.
  - **Atadura ligera**: pesa un 8 % menos (algo más rápida); −15 % durabilidad y la guardia rota tarda un 35 %
    más en volver.
- **Materiales**: las pesadas se cuelan en metal pesado (cobre, hierro, bronce, acero, escoria, cinerio,
  obsidiacero, netherita); las ligeras se cortan en la mesa de piezas de madera (también la de bambú), hueso o
  cuero. Como ninguna mesa corta una pieza pesada, su **molde sale de la plantilla grabada** en la caja de colada
  (con acero refractario, y la plantilla se gasta).
- Los monstruos que llevan un arma forjada notan el peso (el aviso y la espera, como con cualquier arma).
- **Se ven**: mango más grueso con pomo de contrapeso o más fino; atadura con remaches o más delgada, en la pieza
  suelta, en la plantilla, en el molde y en las herramientas y armas terminadas (select por `custom_model_data`).
- **Tooltips** con el trato en dos líneas (lo que da y lo que cuesta), en la pieza, la plantilla, la pieza
  terminada y la estrella de la forja.
- **Mesa de piezas**: la cuadrícula tiene ahora cuatro filas, un poco más juntas, y la línea de estado va en la
  línea del inventario (antes se escribía encima de la cuarta fila).
- Las piezas guardadas antes cargan como normales, y una normal de hoy se guarda igual que antes (el campo
  `variants` sólo aparece si hay alguna variante).
- Pruebas: `MangosGameTests` (7) y la sección de cliente `FORJA_SOLO=mangos`.

## 2026-09-30 — un Bastión del gremio cerca del origen en todos los mundos

Andy: en un mundo nuevo, `/locate` daba el castillo más cercano en (−15264, ~, 21856). Quiere al menos uno dentro
del cuadrado de (−5000, −5000) a (5000, 5000) en todos los mundos, y en buen sitio.

- **Por qué no salía:** la rejilla da un candidato cada 110 chunks (unos 32 dentro del cuadrado) y casi ninguno
  vale. Medido en 60 semillas, de los 1.943 candidatos dentro del cuadrado: 979 caen fuera de los 8 biomas del
  castillo, 695 están a menos de 10 chunks de un candidato de aldea, 146 tienen mar, río o montaña en una esquina,
  118 tienen demasiado desnivel, 4 tienen agua y solo 1 vale. Solo 1 semilla de 60 tenía castillo en el cuadrado;
  la mediana del más cercano estaba a 31.000 bloques y 21 semillas no tenían ninguno en 35.000.
- **El castillo de casa** (`world/BastionHome`): cada mundo busca una vez, en segundo plano al crearse, el mejor
  sitio entre 1.500 y 4.500 bloques del origen:
  - primero lo barato: los biomas del castillo en el centro y sin mar, río ni montaña en las esquinas, y ningún
    candidato de aldea a menos de 12 chunks;
  - luego ordena los sitios por lo llano que dice el ruido (sin mirar columnas) y mide el terreno de verdad solo en
    los mejores;
  - el terreno se mide igual que en el resto de castillos, pero algo menos exigente: desnivel hasta 20 (antes 14),
    el centro a 6 de la altura típica (antes 4) y como mucho 3 muestras de 25 con agua (igual que antes);
  - se queda con el más llano y seco de los que valen.
  La celda de la rejilla donde cae ese sitio lo reparte en lugar de su candidato al azar, así que `/locate`, el
  mapa del Forjador y la exclusión del castillo pequeño (12 chunks) lo ven como un castillo más. Los demás
  castillos siguen donde estaban: lejos y raros.
- **Medido en 60 semillas:** el castillo más cercano queda a 1.594–4.481 bloques del origen (mediana 3.367), dentro
  del cuadrado en el 100 % de las semillas. Desnivel del sitio elegido: 8 a 20 (mediana 13). La búsqueda tarda
  0,3–1,9 s (mediana 0,76 s) en un hilo de fondo al crear el mundo, y un `/locate` en frío que la incluye tarda
  lo mismo (el límite era 4,4 s).
- **El bioma bajo tierra ya no tira castillos buenos:** vanilla miraba el bioma 25 bloques bajo el patio (a veces
  una cueva frondosa) y descartaba sitios que ya habían pasado todo. Ahora cuenta solo la comprobación de biomas
  del castillo.
- **El terreno se mide más barato:** una sola pasada por la columna da el suelo y si hay agua encima.
- Pruebas: `BastionCercaGameTests.everySeedHasAHomeCastle` (5 semillas: hogar dentro del cuadrado, sitio que la
  estructura acepta, la celda lo reparte, `/locate` lo encuentra en menos de 4,4 s). La medición de las 60 semillas
  se repite con `FORJA_BASTION_SEMILLAS=<archivo>`. La sección de cliente `mundo` comprueba que el castillo
  encontrado queda dentro del cuadrado.

## 2026-09-30 — ¿Qué le cabe? Un probador de mejoras en los libros

- **Capítulo nuevo "¿Qué le cabe?"**, en El yunque (tras "Mejorar") y en la pestaña de mejoras del catálogo de la
  biblioteca. Tiene una ranura: al pulsarla se abre un selector con las piezas forjadas de tu bolsa (también las
  puestas y la de la mano). Empieza con la que llevas en la mano. Solo mira: la pieza no se mueve, no se gasta y no
  cambia.
- **Qué enseña de la pieza:** su potencial, la carga usada, los pactos (de 2) y las sinergias despiertas (de 3). Y
  sus mejoras compatibles en dos grupos:
  - **Caben ahora:** cada una con hasta dónde sube, qué hace a ese porcentaje y su receta o el orbe de evento.
  - **Compatibles, pero no caben:** cada una con el porqué: falta carga, falta potencial, ya está al máximo, no se
    junta con otra que ya lleva, ya lleva dos pactos o el pacto está sellado.

  También dice qué sinergia despertaría con lo que ya lleva, o si dormiría porque ya hay tres despiertas.
- **Las reglas son las de la estrella** (`upgrade/UpgradeFit`): a qué tipo de pieza va (`Upgrade.appliesTo`), los
  grupos exclusivos, `Pacts.fits` y los pactos abiertos, y `Potential.ceiling` en la mesa mayor con fundente.
- **Pruebas:**
  - `LibrosGameTests.theProbeAgreesWithTheStar`: espada, pico y pechera, nuevas, con una mejora de grupo y sin
    carga. Cada mejora que el probador dice que cabe sube de verdad en la estrella con sus ingredientes, y las que
    dice que no, no.
  - En el cliente: la espada lista Filo y no Eficiencia, el pico Eficiencia y no Protección, y la pechera Protección
    y no Filo. La bolsa queda igual y el libro se maqueta limpio con cada pieza. Hay capturas del probador y del
    selector.

## 2026-09-30 — Libros de la guía: libro II, El arte del combate, y tarjetas a oscuras

- **Libros sin aprender, a oscuras** (revisión de Andy). En el Cuaderno y en la biblioteca, la tarjeta de un libro
  cuya receta no has aprendido sale a oscuras: la sombra de un libro, un candado, "Libro II · ???" y solo cuándo se
  aprende, sin receta. Al aprenderla se ilumina, con un sello de nuevo hasta que lo abres. Con JEI, las recetas de
  los libros sin aprender se esconden hasta que se aprenden.
- **El arte del combate** (II, libro + hueso; la receta se aprende al forjar tu primer objeto). Es el libro grande,
  en cinco partes con pestaña. Cada una empieza con lo esencial en una página.
  - **Tu cuerpo y tus golpes:**
    - estamina y lo que la gasta;
    - esquivar (Alt), la esquiva perfecta y el contraataque;
    - peso;
    - golpe cargado, combos, postura y remate, dónde das, corte, golpe y perforación, y las resistencias de cada
      monstruo, leídas de la configuración;
    - armas y golpes especiales;
    - defenderte: parada, guardia del arma y golpe de escudo.
  - **Magia y maná:** báculo, grimorio, el farol de curación y el capítulo del maná.
  - **Los enemigos:**
    - cómo pelean: aviso y finta, turnos y anillo, grupos y capitanes, lo que ven y oyen, que no construyen, tu
      equipo y la presión, y la tregua;
    - los rangos: veterano, élite y campeón, lo que aguantan y lo que atraviesan;
    - peleas del mundo: asedios, ladrones, los que vuelven, duelos y saqueadores;
    - la dificultad de Forja, la adaptativa y las noches.
  - **El cielo:** los eventos.
  - **Bestiario que se escribe solo:** cada criatura está en sombra, con "???", hasta que la ves de cerca y a la
    vista. Entonces aparece su ficha, marcada "nuevo" hasta que la miras. Se guarda en
    `config/forja/libros_leidos.json`. El tomo de creativo las enseña todas.
- El paso "Para un golpe" del camino lleva a "Defenderte", en el libro II.
- En el índice, los títulos largos se escriben más pequeños hasta que caben.
- **Pruebas:**
  - `LibrosGameTests`: la receta del libro II.
  - En el cliente (`FORJA_SOLO=libro`): tarjetas iluminadas u oscuras según lo aprendido, el bestiario en sombra y una
    criatura vista, la maquetación y los enlaces del libro II, y fotos de sus 45 dobles páginas.

## 2026-09-29 — La guía se parte en libros (primera entrega: el Cuaderno, El yunque y la biblioteca)

Andy: "Ver un libro que tiene más de 200 páginas termina asustando". El diseño completo está en
`docs/LIBROS_GUIA.md`.

- **Cuaderno del aprendiz** (0). Es el objeto de la guía de siempre (`forja:guia_de_forja`), con nombre y portada
  nuevos.
  - Se da al entrar por primera vez y se vuelve a hacer con un libro y un lingote de hierro.
  - Cuenta de qué va Forja, las dos primeras mesas, las teclas (G, Alt, K, V y B, con el nombre de la tecla que
    tengas puesta) y el camino del herrero.
  - Enseña cada libro con su receta dibujada y cuándo se aprende.
- **El yunque** (I), un libro nuevo: cortar piezas, la estrella y el martillo perfecto, temple, mejorar en la
  primera mesa, desarmar, orbes y reparar. Se fabrica con un libro y una plantilla.
- **Todos los libros se fabrican.** La receta de cada uno se aprende con un logro de Forja (El yunque, al grabar
  la primera plantilla). Hasta entonces la mesa de crafteo no la hace (`CraftingMenuMixin`). En los mundos viejos,
  las recetas que ya tocan se enseñan al entrar.
- **La biblioteca.** Con el Cuaderno encima, **G** abre la biblioteca:
  - una tarjeta por libro, con su progreso y su receta;
  - el camino del herrero;
  - el **catálogo** (objetos y piezas, materiales y rasgos, mejoras y colores), separado en pestañas.

  Sin el Cuaderno, G abre el libro de la forja que tengas en la mano.
- **El camino del herrero** tiene 10 pasos: el último es elegir tu primera técnica. La pista del chat dice el
  capítulo y el libro. Un enlace a un capítulo de otro libro abre ese libro si lo llevas encima.
- **Para que asuste menos**, cada portada enseña:
  - las páginas y los minutos de lectura;
  - una barra de lo leído;
  - tus pasos de ese libro;
  - "Seguir leyendo".

  Un sello rojo marca el libro que llevas y no has abierto. Lo leído se guarda en `config/forja/libros_leidos.json`.
- **Tomo completo de la forja**: la guía entera en un volumen, como antes, solo en creativo.
- **Textos corregidos:**
  - El Herrero Caído ya no se invoca con una ofrenda en el Nether. Se describen el portal y la pelea nueva, y el
    logro también.
  - El monstruo con una leyenda en la mano es el **campeón**, no la élite. La proporción sale de la configuración,
    y se explican los veteranos y las élites.
  - Se cuela en la mesa de colada, no en la caja de moldeo.
  - Las cubas se describen como depósitos que se llenan de uno en uno.
  - Las tres mejores aleaciones también salen con aliento de forja.
  - El maná ya no enseña los números de antes.
  - El bestiario dice todo lo que suelta el Herrero, y los monstruos del mundo que llevan báculo, grimorio, arco o
    ballesta.
- **Arreglo:** el título "El Cementerio entre Estrellas" no cabía en su página (149 px en 140) y la prueba del libro
  fallaba. Los títulos de capítulo se escriben más pequeños cuando no caben, y el de la portada se parte en líneas.
- **Pruebas:**
  - `LibrosGameTests` (nuevo): recetas de un libro y un ingrediente, la mesa que no da un libro sin aprender, la
    receta que llega con su logro y al entrar, solo el Cuaderno al entrar, y cada paso del camino con su libro.
  - `PathGameTests`, para 10 pasos y la pista con libro.
  - En el cliente (`FORJA_SOLO=libro`), `checkBooks`: maquetación y enlaces de cada libro, G con y sin el
    Cuaderno, un enlace entre libros, y fotos de cada doble página.
- **Los libros II a VII** vienen en las próximas entregas. Hasta entonces, lo suyo se lee en el tomo de creativo, y
  el Cuaderno los enseña como "en preparación".

## 2026-09-29 — El blaze con su red entrenada (red_blaze_v1 del simulador)

- **Contrato:** `docs/red_blaze_contrato.json` es ahora el del simulador, copiado tal cual. Tiene 324 entradas (las
  280 de v3b y 44 del blaze) y 18 salidas en 5 cabezas: mover, vertical, usar, adelanto y retirarse. El contrato de 65
  entradas y 22 salidas de la sesión de la nube se quitó: solo hay un `red_blaze_v1`. La explicación, y en qué se
  aparta el mod del simulador, está en `docs/red_blaze_contrato.md`.
- **Red entrenada:** `redes_entrenadas/red_blaze.json` (iteración 24 150). Se carga de `redes_v4/` o de `redes/`.
- **Con red, el blaze:**
  - flota entre 2 y 5 bloques sobre el suelo y sube y baja despacio;
  - avisa 20 ticks antes de cada ráfaga: se enciende como el blaze cargado de vanilla, con llamas que se le acercan y
    un chisporroteo que sube de tono;
  - dispara 3 bolas cada 6 ticks y espera 60;
  - apunta por delante del jugador que se mueve;
  - se retira subiendo;
  - no ocupa hueco en el anillo de la escuadra.
- **Sin red, o con `iaModo` en reglas,** el blaze es el de vanilla, como antes.
- **Pruebas:** `BlazeGameTests` rehecho:
  - nombres contra el contrato, la red entrenada carga y pasa la comprobación, 324 entradas finitas;
  - con la red, vuelo en su banda, ráfagas de 3 tras el aviso, adelanto sobre un jugador que se mueve y distancia
    de combate;
  - sin red, vanilla;
  - coste: unos 0,025 ms por tick y blaze;
  - bolas que dan a un jugador quieto en 30 s: 18 de 18 con la red, de 0 a 4 de 9 con vanilla.

  `FORJA_SOLO=blaze` saca capturas del vuelo, del aviso y de la ráfaga.

## 2026-09-29 — IA v4 de los monstruos: la memoria del mundo (paso M6)

- **Los monstruos de un mundo recuerdan cómo los matas:** en llano, con flechas, desde arriba, en pasillos, con
  trampas, con área o con fuego; qué forma de atacarte les funciona (de frente, por el flanco, a distancia, emboscados
  o asediando), y si sueles subirte a un pilar o huir. La red v4 lo ve (bloque W). Se olvida poco a poco con los días.
- **`/forja ia mundo`** enseña esa memoria y **`/forja ia mundo borrar`** la borra.
- **`/forja ia ver`** muestra también la orden del capitán y el puesto, el objeto en curso y la furia.
- **Pruebas:** `RedV4MundoGameTests` (4).

## 2026-09-29 — IA v4 de los monstruos: el capitán, formaciones, moral y furia (paso M5)

- **Capitanes:** en un grupo con un élite o un campeón (nunca un veterano), él manda: cercar, cargar, hostigar,
  retirarse, reagruparse, emboscar, asediar o escoltarle. Lleva un estandarte dorado encima.
- **Formaciones:** muro (escudos delante, arqueros detrás, rápidos por los flancos), pinza y cuña.
- **Carga sincronizada:** el capitán cuenta y grita; al grito, 2 s con un turno de ataque más contra ti.
- **Si matas al capitán:** 10 s sin órdenes y la moral cae.
- **Furia:** uno de cada diez de un grupo (al menos uno de cinco) puede enfurecerse tras perder al capitán o a la
  mitad: 10 s más fuerte y rápido, luego 5 s agotado. Se ve (partículas rojas y rugido).
- **Huir de verdad:** un monstruo que huye 5 s a más de 20 bloques deja la pelea.
- **Red v4:** bloques M y Mo, FORMACION y furia; red de capitán `red_capitan_v4` (contrato nuevo en
  `docs/red_capitan_v4_contrato.json`).
- **Pruebas:** `RedV4CapitanGameTests` (8).

## 2026-09-29 — IA v4 de los monstruos: oído, rastro y emboscadas (paso M4)

- **Te oyen:** pasos (corriendo más lejos; agachado, nada), picar y poner bloques, puertas y cofres, comer y beber,
  disparar y golpear. A través de una pared, la mitad de lejos.
- **Te buscan donde creen que estás:** donde te vieron o donde te oyeron por última vez; luego miran en abanico más
  allá. Nunca van a tu posición real si no te perciben.
- **Emboscadas:** de noche o a oscuras, cuando te pierden, esperan quietos en un escondite fuera de tu vista.
- **Te siguen más:** hasta 48 bloques y 30 s sin verte ni oírte (antes, 3 s).
- **Red v4:** bloques P y E; BUSCAR y EMBOSCAR abiertas; mientras no te percibe, la red ve un sustituto en la posición
  estimada.
- **Pruebas:** `RedV4PercepcionGameTests` (6).

## 2026-09-29 — IA v4 de los monstruos: escudo, objetos, mochila y contra el pilar (pasos M2 y M3)

- **Escudo inteligente:** el muro de escudos solo sube el escudo cuando viene algo. Tras bloquear, **golpe de
  escudo**: 4 ticks de aviso, empuja al jugador, le quita 15 de estamina y le corta la carga.
- **Recogen armas del suelo,** también la tuya: la mejor según su valor (daño × velocidad × alcance). La que llevaban
  va a una ranura de repuesto. Lo que recogen se suelta siempre al morir.
- **Mochila:** los veteranos, élites y campeones aparecen con pociones de curación, arrojadizas, cargas de viento y
  perlas. Beben (a media velocidad, se les ve la poción), comen, lanzan pociones, se acercan o escapan con perlas y
  lanzan cargas de viento. Nada de la mochila se suelta al morir.
- **Contra el pilar, sin construir ni romper:** flecha de empuje de los arqueros, zarpazo de la araña que trepa,
  garfio del que lleva una caña, perla al pilar, carga de viento y asedio: esperan alrededor del pie, fuera de la
  vista de la cima, cortando la bajada.
- **Apagan antorchas** (solo antorchas y solo con `mobGriefing`): uno del grupo a la vez, la que más te ilumina; se
  suelta al romperse.
- **Una base cerrada del todo sigue a salvo:** ninguna herramienta rompe ni abre bloques; la carga de viento de un
  monstruo no abre puertas.
- **Red v4:** los bloques A, O, C, G, L y `jug_luz` de la observación ya se calculan, y se abren RECOGER,
  APAGAR_LUZ, ASEDIAR, SECTOR, TIRO_LIBRE, la cabeza de objeto y el golpe de escudo. Las reglas usan todo lo mismo.
- **Configuración:** `mobActionsV4` y `mobsBreakLights` se encienden una vez al cargar (`iaAccionesRevision`).
- **Pruebas:** `RedV4ModGameTests` (20) y `RedV4PerfGameTests` (30 mobs con redes v4 del tamaño del contrato:
  1,2–1,4 ms/tick de IA, tope 2,5).

## 2026-09-29 — La pelea del Cementerio entre Estrellas (entrega 3)

- **Cae del cielo:** al entrar en la dimensión sin pelea en curso, a los 3 s una estrella cae sobre la arena. Tarda
  2 s, suelta una onda morada al tocar el suelo y el Herrero se levanta. Mientras cae y se asienta no se le puede
  dañar.
- **Fases a 2/3 y 1/3,** cada una con su animación: clava el martillo, 3 s invulnerable y onda.
- **Aprendices que salen de la tierra** en polígonos alrededor del jefe: 4 + 3 por jugador extra (con 10, 4 + 6).
  Suben 2 s, anillo a anillo, y no se les puede dañar hasta que salen.
- **Reforjado estelar** a la mitad de la vida: es inmortal mientras ardan sus brasas estelares (3 + 1 por jugador
  extra, hasta 6).
  - Solo las apaga la colada de un brasero: se vuelca con un golpe o una flecha.
  - Cada brasero se vuelca una vez; se rellena con 4 hierros estelares.
  - Mientras dura, el Martillo lanza meteoritos cada 20 s.
  - Al apagarse la última brasa queda aturdido 5 s y recibe ×1,5 de daño.
- **Constelaciones mitad y mitad:** una cada 30–40 s, en 5 colores de fuerza.
  - Espada, Hacha, Escudo (Égida) y Guadaña ayudan al Herrero: aviso en rojo, con campana.
  - Martillo, Lanza, Yunque y Tenazas ayudan a los jugadores: aviso en menta, con amatista.
  - El Martillo tira meteoritos sobre él, la Lanza lanzas de luz, el Yunque cura y las Tenazas apartan a los
    aprendices y sacan al más herido.
- **La pelea se conserva** tras morir, guardar y cargar, y se para cuando no hay nadie en la dimensión.
- **Al morir:** cae la Estrella de vuelta (clic derecho: al portal por el que entraste). Aparece también una fragua
  fría estelar para la revancha, que cuesta 1 perla de oricalco, 3 oricalcos, 1 estrella del Nether y 16 hierros
  estelares.
- **Estrella forjada** para cada participante, en la bolsa o guardada hasta que vuelva.
  - Se pone en la forja mayor en una pieza terminada, una por pieza.
  - Da tope de potencial 125 y +25 de potencial, ×1,12 al daño y al minado y ×1,5 a la durabilidad.
  - En armadura da +1 de armadura y +0,5 de dureza por pieza.
- **Pruebas:** `PeleaEstelarGameTests` nuevo y `ArmaduraGameTests.starredArmourMayPassTheCeiling`.
  `FORJA_SOLO=dimension` filma la pelea entera.

## 2026-09-29 — El portal al Cementerio entre Estrellas (entrega 2) y los arreglos de la segunda revisión (1c)

- **Oricalco:** una aleación de los 14 metales renovables del mod (hierro estelar, placa hueca, escoria, bronce,
  latón, peltre, electro, acero, cinerio, voltaico, acero estelar, obsidiacero, almacero y vidriacero).
  - Se funde a calor de fundición en un crisol de una línea de fundición: 2 lingotes en el crisol y 12 de las cubas.
    Salen 4 lingotes.
  - No lleva nada que se acabe (damasco, solacero, lunacero) ni que solo dé el jefe.
- **Perla de oricalco:** una perla de ender puesta en una mesa de colada, con 2 lingotes de oricalco vertidos
  encima.
- **El portal:** la fragua apagada de la Forja Profunda del Bastión es ahora un marco de 5 × 5 con 4 ménsulas
  estelares.
  - Con una perla de oricalco en cada una se enciende el portal estelar, para siempre.
  - Al entrar te lleva a la plataforma de llegada de la dimensión.
  - Para volver, un pozo encendido detrás de la plataforma te deja junto al marco por el que entraste; se guarda en
    el jugador y sobrevive a morir.
- **La invocación vieja se quita.** En los mundos ya hechos, un clic en una fragua apagada la abre en el marco vacío.
- **Guía:** capítulo nuevo, "El Cementerio entre Estrellas".
- **Arreglos de la revisión de Andy (1c):**
  - El tinte de brasa de la niebla ya no salta: depende solo de hacia dónde miras y de la altura, y se suaviza.
    Antes, un paso fuera del borde pasaba el cielo de granate a naranja de golpe.
  - Los eventos del mundo (aurora, luna de sangre, meteoritos…) también tiñen este cielo, con sus partículas y
    dibujos, pero sin luna ni sol.
  - Respiraderos muertos: de 5 a 9 conos agrietados según la semilla, con cráter de escoria fría. Algunos humean,
    otros llevan chimenea fría o canaleta de cobre.
  - Las 8 constelaciones repartidas por todo el giro del cielo: siempre hay al menos 3 bien arriba.
  - Ecos lejanos: un martillo, una campana o escombros, a 40–80 bloques, graves, que resuenan 2 o 3 veces.
- **Diseño (sin construir):**
  - el Reforjado estelar aprobado: inmortal hasta que la colada del brasero apague su fuego;
  - la revancha con materiales renovables;
  - las 8 constelaciones con sus eventos y los 5 colores de fuerza.
- **Pruebas:** `PortalGameTests` (6) y 3 nuevas en `DimensionGameTests`. `FORJA_SOLO=dimension` enciende el portal
  con clics, cruza y vuelve.

## 2026-09-29 — El Cementerio entre Estrellas: arreglos de la revisión de Andy (entrega 1b)

- **Un sol bajo el vacío:** al fondo, recto abajo, un cuerpo enorme con halo rojo, 28 rayos que giran y parpadean,
  dos coronas que giran una contra otra, un temblor de calor alrededor del núcleo y un núcleo blanco que ciega.
  Se apaga al subir (entero hasta y = 110, apagado a y = 170), porque desde muy alto se dibujaba delante de la meseta.
- **Las armas clavadas caen con su suelo:** si se quita el bloque de debajo desaparecen sin soltar nada; rotas a
  mano tampoco sueltan nada, y no se pueden poner en el aire.
- **Las armas clavadas se ven bien desde el sur:** la cara de atrás de cada arma llevaba la uv de la de delante y,
  vista desde el norte, el arma salía tumbada. Arreglado en las 6 armas y las 3 inclinaciones.
- **La meseta depende de la semilla** (`world/StarYardLayout`): el borde, de 2 a 4 ríos y su recorrido, los
  puentes, de 5 a 9 islotes, las filas y fosas de tumbas, las forjas frías y el relieve cambian con cada mundo. La
  arena es la misma en todos. Sigue sin ruido: 400 chunks de columnas en 0,07 s.
- **El cielo se mueve:** una vuelta cada 12 minutos (antes 40, y no se notaba); la nebulosa deriva algo más deprisa,
  respira y sus nudos se deslizan. **Estrellas fugaces:** unas 4 por minuto.
- **Diseño (sin construir):** las respuestas de Andy (nombres, oricalco, 4 + 6 aprendices, revancha con fragua fría
  en el centro, Estrella que pasa el tope de armadura, eventos) y una propuesta nueva, el **Reforjado estelar**: su
  invulnerabilidad de la mitad solo se rompe con la dimensión (hierro estelar templado, molde celeste redirigido,
  colada del brasero). En `docs/HERRERO_DIMENSION.md`.
- **Pruebas:** `DimensionGameTests` en 5 semillas, `theSeedShapesThePlateauButNotTheArena` y
  `aGraveFallsWithItsGroundAndDropsNothing`; `FORJA_SOLO=dimension` en dos mundos con semillas distintas.

## 2026-09-29 — El Cementerio entre Estrellas, la dimensión del Herrero Caído (primera entrega: la dimensión)

- **Diseño entero** en `docs/HERRERO_DIMENSION.md`: portal con perlas de oricalco en el Bastión, la dimensión, la
  pelea nueva (llegada desde el cielo, fases a 2/3 y 1/3, aprendices que salen de la tierra en polígonos, eventos,
  persistencia, estrella de vuelta) y la recompensa (la Estrella forjada). **Por orden de Andy, esta entrega solo
  construye la dimensión y su aspecto**; lo demás espera a que la revise.
- **La dimensión** (`forja:cementerio_estelar`): una meseta sola en el vacío, de 124 a 176 bloques de radio.
  - En el centro, la **arena**: obsidiana plana de radio 22 con un disco de obsidiana llorona, radios y anillos de
    piedra negra, un muro bajo con 4 entradas y 4 pilares con braseros de metal fundido.
  - Al norte, la **plataforma de llegada** (0, 81, −30), mirando a la arena.
  - Alrededor, la **llanura de ceniza** con unas 4.000 **armas clavadas** como tumbas (en filas alrededor de la
    arena hasta el radio 72, sueltas y en fosas más allá) y unas 37 **forjas frías** en ruinas de tres tipos.
  - **Tres ríos de metal fundido** entre muros, con puentes, que nacen en un pilón a 52 bloques y caen por el borde
    al vacío. Nada de metal fundido a menos de 24 bloques de la arena. Si alguien se mete, quema como la lava.
  - **7 islotes** flotando alrededor para la vista de lejos.
- **El cielo:** sin sol ni luna, noche eterna. Estrellas vanilla y 900 más de colores, una franja de nebulosa y **8
  constelaciones que dibujan moldes de armas** (espada, hacha, martillo, lanza, escudo, yunque, tenazas y guadaña).
  Cada 40 s una se "cuela": un hilo de oro recorre sus líneas. Todo gira despacio (una vuelta cada 40 minutos).
- **El fondo del vacío arde** (Andy): por debajo del horizonte el cielo va del violeta al naranja de brasa, la
  niebla se tiñe de brasa cuanto más abajo miras y más bajo estás, las estrellas de abajo se apagan en el resplandor
  y suben brasas desde el fondo.
- **Ambiente:** bruma de ceniza violeta (de 48 a 256 bloques, que se abre en altura), luz fría de estrellas y cálida
  junto al metal, ceniza que se espesa al bajar (hasta 8 veces a y = 20), y sonido y música propios hechos con
  sonidos vanilla más graves (`sounds.json`). No se puede dormir ni fijar la reaparición allí.
- **Barato:** un generador propio sin ruido (`world/StarYardGenerator`); cada columna son unas sumas de senos y un
  hash. Sin estructuras: `/locate` no tiene nada que buscar.
- **Para probar:** `/forja dimension` te lleva a la plataforma de llegada y `/forja dimension volver` te devuelve.
- **Bloques nuevos** (texturas vanilla recoloreadas con `tools/dimension_assets.py`): ceniza, ceniza prensada,
  metal fundido (sin objeto) y arma clavada (sin objeto). Partícula nueva: brasa.
- **Pruebas:** `DimensionGameTests` (registro, comando, arena llana y despejada, sin metal cerca de la arena, tumbas,
  forjas y cascadas, y 400 chunks de columnas en décimas de segundo) y `FORJA_SOLO=dimension` en el cliente, con
  capturas.

## 2026-09-29 — el blaze tiene su propia red (contrato `red_blaze_v1`)

- **Contrato nuevo** (Andy lo aprobó): `docs/red_blaze_contrato.json`, versión 1, explicado en
  `docs/red_blaze_contrato.md`. 65 entradas y 22 salidas; no toca los contratos v3 ni v4.
- **Lo que ve la red del blaze:** su vida, su altura sobre el suelo y la del jugador, la carga y la ráfaga, al jugador
  (distancia, altura, velocidad, escudo, arco, si le alcanza con la espada, si lo ve), sus bolas en vuelo (cuántas y
  cuánto fallan), los otros blazes y los aliados de tierra, y el techo, el agua y las paredes de alrededor.
- **Lo que hace:** moverse en horizontal, subir, bajar o mantener la altura, cargar y disparar la ráfaga, elegir a qué
  distancia pelear y una táctica (acosar, rodear por arriba, retirarse, esperar).
  - Para volar, mientras manda la red, el mod fija su velocidad vertical cada tick y la cambia poco a poco: "mantener"
    lo deja flotando. No sube bajo un techo ni a más de 10 bloques del suelo, ni baja a menos de 1.
  - La ráfaga es la de siempre (3 bolas, 6 ticks entre ellas, 60 de espera), pero antes tiene que cargar 20 ticks
    (se ven llamas), cada bola necesita ver al jugador y apunta con adelanto.
- **Dónde va:** `config/forja/redes_v4/red_blaze.json` o `config/forja/redes/red_blaze.json`, con
  `"formato": "red_blaze_v1"`. Con otro formato se rechaza y el blaze pelea con sus reglas. Sin red, nada cambia.
- **Pruebas:** `BlazeGameTests`:
  - los nombres de las entradas, los bloques y las cabezas son los del contrato;
  - la observación tiene 65 números válidos;
  - una red de prueba maneja un blaze 40 ticks: decide, sube cuando se le pide y dispara solo viendo al jugador;
  - tras un muro no dispara;
  - una red con otro formato se rechaza.

## 2026-09-29 — respuestas de Andy: castillo más llano y el precio de Baluarte

- **Sitio del castillo más llano** (Andy: "más llano"): el desnivel máximo bajo el plano pasa de 24 a 14 bloques
  y el centro tiene que quedar a 4 de la altura típica (antes 6). Saldrán menos castillos, en terreno más plano.
- **Baluarte tiene precio** (Andy: "algo malo debe de tener"): sigue dando +1 de armadura y 4 ticks más de parada,
  pero la pieza pesa un 30 % más (anda, golpea y recupera estamina más despacio, como cualquier placa más pesada),
  y con un escudo cada bloqueo cuesta un 20 % más de estamina. Prueba: `baluarteIsHeavier`.
- Se quedan como estaban: la Protección IV de la forja y la separación del castillo (110 chunks).

## 2026-09-29 — el mod ya usa las redes v4 de los monstruos (paso M1)

- **Redes v4:** una `red_<familia>.json` con `"formato": "red_mob_v4"` en `config/forja/redes_v4/` se carga y manda
  sobre la v3 de esa familia (con `iaContrato` en `auto` o `v4`). Tiene que traer las 468 entradas del contrato
  (`docs/red_mob_v4_contrato.json`) en su orden y 53 salidas; si no, se dice en el registro y la familia sigue con su v3
  o con las reglas. Con `iaContrato` en `v3` todo sigue como antes. Las redes v3, v3b y v3.1 no cambian.
- **Lo que ve una red v4:**
  - las 280 entradas de la v3b, con el alcance real del arma;
  - los sectores del grupo: dónde está su hueco respecto a hacia dónde mira el jugador, cuántos comparten sector, si es
    el siguiente en atacar desde él, cuánto lleva esperando turno, y para los arqueros hacia dónde apartarse para tener
    la línea de tiro libre;
  - el alcance mínimo de las lanzas, la suya y la del jugador;
  - todo lo demás (capitán, moral, oído, emboscadas, pilares, objetos, pociones, escudo, luces y memoria del mundo) va
    a 0 hasta que llegue su paso. `docs/red_mob_v4_neutros.md` apunta las entradas en las que 0 quizá no sea "nada".
- **Lo que hace una red v4:** moverse, saltar, atacar, las 13 tácticas de siempre, especiales, defensa, fintas y correr.
  Las 8 tácticas nuevas, los objetos, la furia y el golpe de escudo están bloqueados hasta que el mod sepa hacerlos.
- **Pruebas:** `RedV4GameTests`:
  - los nombres de las entradas son los del contrato;
  - la observación v4 tiene 468 números válidos;
  - una red v4 en `redes_v4` se carga;
  - una red v4 falsa lleva a un zombi 40 ticks sin errores.

## 2026-09-29 — los monstruos no ven a través de las paredes, y la carga de las redes v4

- **Percepción honesta:** un monstruo que lleva un segundo sin verte ya no va a donde estás de verdad (la navegación
  vanilla iba a tu posición real a través de las paredes). Va a donde te vio por última vez y te espera allí. Si te
  vuelve a ver, o te tiene a menos de 1,5 bloques, ataca como siempre. Los jefes no cambian. Opción:
  `iaPercepcionHonesta` (activada).
- **Redes v4:** nueva opción `iaContrato` (`v3`, `v4` o `auto`). Las redes v4 irán en `config/forja/redes_v4/`. Las de
  siempre siguen en `config/forja/redes/`. Cada red se elige por su campo `formato`, así que una v4 puesta en la carpeta
  de las v3 se rechaza. El mod aún no sabe usar las v4: si encuentra una, lo dice una vez en el registro y sigue con la
  v3 o con las reglas.
- **Decisiones de Andy:**
  - los zombis siguen rompiendo puertas;
  - los monstruos solo podrán romper antorchas, y solo con `mobGriefing` activado (llegará con la v4);
  - una base cerrada del todo es segura.
- **Pruebas:** `PercepcionGameTests`:
  - un zombi que pierde de vista al jugador tras un muro va a donde lo vio y no rodea el muro hasta él;
  - una red v4 falsa se detecta y no se usa.

## 2026-09-29 — el buen equipo ya no vuelve inofensivas a las multitudes

Andy: "con diamante y Protección IV, y más con la armadura del mod, las multitudes no hacen nada". Cambios:

- **Daño según tu equipo:** los monstruos te pegan un **15 % más por cada tramo de equipo** (`GearScore`, del 0 al
  3): un 45 % más en el tramo 3. Se suma a los multiplicadores de dificultad y adaptativo. Valor:
  `mobDamagePerGearTier`.
- **Más atacantes a la vez:**
  - **+1 por tramo de equipo** (`attackersPerGearTier`);
  - **+1 en MAESTRO y +2 en LEYENDA** (`attackersMaestro`, `attackersLeyenda`).

  El tope sube lo mismo: antes eran 2 de base y como mucho 4; ahora, con tramo 3 en LEYENDA, hasta 9.
- **Penetración de armadura por amenaza:** veterano 10 %, élite 20 % y campeón 35 % (`penetrationVeteran`,
  `penetrationElite`, `penetrationChampion`). Cuenta la mayor entre esta, la del arma y la de la presión.
- **Presión más rápida:**
  - sube 0,10 por golpe (antes 0,07);
  - espera 60 ticks antes de bajar (antes 40);
  - un golpe **parado con escudo o desviado** también suma, la mitad (`pressureBlockedShare` = 0,5).

  Una `config/forja.json` con los valores viejos pasa sola a los nuevos.
- **Modo rodeo:**
  - **Cuándo:** Andy dijo que al retroceder nunca le daban tiempo a rodearle. Ahora, un monstruo que va a su hueco
    del anillo mientras el jugador se aleja (se aparta de él, o el hueco queda delante o a un lado de hacia donde
    va) corre a **×2,3** en vez de ×1,35.
  - **Coste:** 1,4 de su aguante por tick (de 100), así que le dura unos 3,5 s. Después tiene que recuperarse,
    como con la carrera normal.
  - **Quién:** vale para las reglas y para la salida de correr de la red cuando el monstruo tiene hueco.
  - **Ajuste:** `rodeoSpeed` y `rodeoCostPerTick`, para probarlo y ajustarlo.
- **Pruebas:** `DificultadGameTests`:
  - el daño sube con el tramo;
  - más atacantes con más tramo;
  - la élite atraviesa el diamante;
  - un golpe parado suma la mitad de presión;
  - el modo rodeo corre a ×2,3 y paga 1,4 por tick (`theSurroundModeRunsAtTwoPointThree`).

  La escena entera (6 zombis y un jugador que retrocede a velocidad de carrera) se comprueba en la prueba del
  cliente, sección `cerco`: el área de una prueba de servidor mide 8 bloques y los zombis chocaban con su borde.
- **Capturas:** `FORJA_SOLO=cerco` añade las del retroceso (`cerco_retroceso_*`).

## 2026-09-29 — acciones preparadas para la red v4

- Nuevas acciones de los monstruos, listas para que las decida la red con el contrato v4 (Andy): coger un arma
  mejor del suelo, beber pociones, lanzar pociones arrojadizas, comer cuando están heridos, lanzar perlas de ender,
  romper antorchas y subir el escudo cuando les apuntan. Están en `ai/MobActions.java`.
- Nada las usa todavía: ni las reglas ni las redes actuales. Todas se apagan con `mobActionsV4` (por defecto
  `false`) en `config/forja.json`. Romper antorchas necesita además `mobsBreakLights`, también apagada, y la regla
  `mobGriefing`: solo antorchas, nunca faroles ni otras luces. `mobPickupRange` (6) es la distancia a la que buscan armas.
- El arma que coge un monstruo cae siempre al morir, así que si coge la tuya la recuperas.
- Pruebas: `AccionesGameTests`, una por acción. La tabla está en `docs/red_mob_v4_propuesta.md`.

## 2026-09-29 — las armaduras de arriba, a la altura de la netherita

- Andy: "el diamante con Protección IV, y más aún las armaduras del mod, hacen que las multitudes no hagan daño".
  Medido (docs/EQUILIBRIO.md, *Armaduras frente a diamante y netherita con Protección IV*): con Protección IV todo
  conjunto para entre el 75 y el 87 % del golpe de un zombi o un vindicador; el diamante vanilla con Protección IV,
  el 81–84 %, y la netherita, el 83–84 %. Lo que más aprieta es Protección IV, que deja pasar solo un 36 %.
- Cuatro materiales se salían: el **corazón de forja** y el **acero vivo** pasan de 24 a 21 de armadura en el
  conjunto (casco 3, pechera 8, grebas 7, botas 3) y el **solacero** y el **lunacero** de 23 a 20, como la
  netherita. Conservan su dureza y su peso. A tope de mejoras, el conjunto forjado que más se pasa de la netherita
  con Protección IV lo hace por unos 4 puntos (el obsidiacero, que no cambia); antes, el corazón, por 4,4.
- Prueba nueva: `forgedArmourStaysNearNetheriteWithProtection` (`ArmaduraGameTests`) pone cada conjunto en un jugador,
  le pega con 6 y 10 de daño por la armadura del mod y la protección de vanilla, deja la tabla en el registro
  (`[forja-test] armadura:`) y falla si alguno pasa de la netherita con Protección IV más 5 puntos.
- EQUILIBRIO.md: lo escrito a mano al final (la sección de armaduras) ya no se pierde cuando `BalanceGameTests`
  regenera la página.

## 2026-09-29 — los monstruos ya no construyen ni rompen bloques

- Por decisión de Andy, ningún monstruo pone ni rompe bloques:
  - los zombis ya no se hacen pilares de tierra para subir hasta ti, ni excavan hojas, arena o tierra para abrirse
    paso (fuera `MovementGoals.Builder`);
  - **corrección posterior de Andy:** la telaraña de la araña se queda como estaba (un bloque que se quita a los 5 s),
    y también el fuego del Cargador de carbón y del Herrero Caído, la cabeza lanzada, las explosiones de los creepers,
    los bloques que coge el enderman y los zombis que rompen puertas.
- Para bajarte de un pilar, el contrato v4 de la red dará otras salidas: tiros que empujan, arañas que trepan y
  empujones. La única excepción futura será romper antorchas, también con la v4.
- Lo único nuevo que podrán romper, con la v4: antorchas (normal, de pared, de almas y de almas de pared), y solo con la
  regla `mobGriefing` activada (`MobActions.breakLight`).
- Pruebas: `zombiesNeverBuildNorDig` (ni un bloque cambia en 5 s con un zombi bajo un jugador en un pilar); fuera
  `zombieDigsThroughLeaves`.

## 2026-09-29 — /locate del castillo, más barato

- `/locate structure forja:bastion_del_gremio` tardaba 20 s en encontrar un castillo a 45.000 bloques: solo valía uno
  de cada dos mil sitios y cada uno costaba dos columnas de ruido por muestra. Ahora se miran primero los biomas del
  centro y de las cuatro esquinas (ni mar, ni río, ni montaña), cada columna solo pregunta por el agua si su superficie
  está a nivel del mar, y los límites son algo más anchos: desnivel 24 (antes 18), el centro a 6 de la mediana (antes
  4) y 3 muestras con agua (antes 2).
- El registro de la prueba del cliente dice cuántos sitios se miraron y por qué se descartaron (`BastionGround.report`).

## 2026-09-29 — alcance de los monstruos según su arma

- Un monstruo con espada ya pega desde más lejos que uno con los puños (Andy: "un zombie con espada debería poder
  atacar de más lejos que uno con puños"). Sobre el alcance de su cuerpo suma, según lo que lleve: puños +0, daga
  +0,2, espada +0,6, hacha, martillo, maza o pico +0,5 y espadón +0,9. Vale igual para las armas vanilla. La
  guadaña, el tridente, la lanza y el mangual se quedan con el suyo. Los valores están en `config/forja.json`
  (`mobReachFist`, `mobReachDagger`, `mobReachSword`, `mobReachAxe`, `mobReachGreatsword`), para ajustarlos jugando.
- Cuenta para empezar el golpe, para que llegue y para dónde se coloca. La red sigue viendo en `yo_arma_alcance`
  el número del contrato, el mismo con el que se entrenó.

## 2026-09-29 — el jefe contra los demás, y un pararrayos para los meteoritos

- **Daño ajeno al jefe: un tercio.** Lo que no viene de un jugador (un gólem, un warden, otro monstruo) le hace
  1/3 al Herrero Caído (`jefeDanoAjeno` = 0,333; antes 0,5). Tus mascotas y tus flechas siguen contando como tú.
  Una `config/forja.json` que aún tenga el 0,5 de antes pasa sola a 0,333; cualquier otro valor escrito a mano se
  respeta.
- **Nuevo movimiento del Herrero: La forja reclama.** Cuando las criaturas del mundo se le echan encima:
  - **Se dispara** si en los últimos **10 s** (200 ticks) le intentan pegar **3 criaturas distintas** que no son de
    nadie, o si las grandes (**100 de vida o más**: gólem de hierro, devastador, warden, wither) le intentan quitar
    entre todas **un 10 % de la vida** (32, contado ya al tercio: un warden en 3 golpes, un gólem en unos 7).
  - **Aviso de 30 ticks (1,5 s):** abre los brazos, un anillo morado se cierra desde 12 bloques hasta sus pies y
    suena la carga; los últimos **16 ticks** arrastra hacia él todo lo que puede llevarse; en el tick 30 clava el
    martillo y lo que arrastró **desaparece**: sin botín, sin experiencia y sin muerte (se descarta, no se mata).
  - **Alcance:** todo lo que no es de nadie a **12 bloques**, haya atacado o no.
  - **Nunca:** jugadores, mascotas o animales con dueño (lobos, gatos, loros, caballos domados), lo que monta un
    jugador, sus aprendices y el resto del bando de Forja, él mismo y el dragón. Un jugador con su perro no lo
    verá nunca.
  - **Enfriamiento: 40 s** (800 ticks). No empieza en mitad de un golpe suyo ni con la lluvia de estrellas en
    camino; mientras dura no hace otra cosa.
  - Animación propia (`reclaim`, 2,2 s, el martillo abajo justo en el tick 30), en la guía y probada:
    `aCrowdOfGolemsIsReclaimed`, `aPlayersWolvesAreNeverReclaimed`, `aGolemPoundingHimAloneIsReclaimed`.
    Capturas con `FORJA_SOLO=jefe_reclama`.
- **Lluvia de estrellas del jefe (respuesta a tu pregunta):** cae **una** estrella por llamada. Cada 3 s (60 ticks),
  en su último cuarto y con un objetivo, marca el sitio donde está el objetivo, alza el martillo y a los 20 ticks cae
  un solo impacto: 9 de daño mágico a todo lo que esté en un cuadrado de 6×6 bloques centrado en la marca (3 a cada
  lado; salvo su bando). Lo de "estrellas" es el dibujo: una lluvia de partículas sobre un único punto.
- **Pararrayos de estrellas** (`forja:pararrayos`), para la **lluvia de meteoritos** (el evento del cielo, no el
  jefe):
  - Un meteorito que iba a caer a **12 bloques o menos** (en horizontal; el pararrayos puede estar hasta 6 bloques
    más abajo o 24 más arriba, en un tejado) cae **sobre el pararrayos**. Se decide al aparecer, así que el anillo
    de aviso del suelo y el mensaje del chat ya señalan el pararrayos.
  - Al caer no abre cráter: el pararrayos se lo traga y deja el hierro estelar a sus pies. Si alguien lo quita
    mientras cae, el meteorito cae ahí como cualquier otro.
  - **Aguanta 4 meteoritos:** cada uno lo desgasta (se ve: el cristal se apaga y se agrieta, el cobre se pone
    verde) y el cuarto lo rompe sin soltar nada. Quitado a mano conserva el desgaste.
  - **Receta:** fragmento de amatista arriba, acero entre dos lingotes de cobre y un lingote de hierro abajo.
  - **Más meteoritos:** antes solo caía uno por jugador al empezar el evento. Ahora, además, cada minuto del evento
    (dura 5) cae otro cerca de cada jugador con un 50 % de probabilidad: uno seguro y dos más de media. El aviso es
    el de siempre: la bola se ve caer 34 ticks (1,7 s) desde 48 bloques, un anillo marca el suelo donde va a dar y
    el chat dice las coordenadas.
  - Pruebas: `PararrayosGameTests` (lo atrae, se gasta y se rompe al cuarto, fuera de alcance no hace nada).
    Capturas con `FORJA_SOLO=pararrayos`.

## 2026-09-29 — clases, farol de curación, castillo rehecho e IA de grupo

- **Clases** (docs/CLASES.md): Guerrero, Asesino, Tanque, Mago, Curandero, Arquero y Herrero. Se elige una, se sube
  de nivel (hasta 15) con experiencia de su estilo de pelea y cada nivel da un punto para su árbol de talentos. Se
  puede cambiar con el Medallón del olvido, que se forja: se vuelve a empezar en el nivel 1 (decisión A de Andy).
- **Farol de curación:** cualquiera puede usarlo; un toque lanza un rayo que cura al primer aliado, cargado suelta un
  anillo que cura a todos los aliados cerca (y a ti a la mitad). Gasta maná: 12 por toque, un 25 % más cargado. El
  Curandero cura más con él, y su báculo y su grimorio curan a los aliados (1/10 del daño) en vez de herir.
- Las clases cambian la estamina, el esquive, el maná y el coste de los hechizos a través de los mismos cálculos que
  las mejoras (Aguante, Quiebro, Reserva, Flujo...).
- **Castillo del Herrero:** solo en terreno llano y seco y lejos de aldeas, sin bloques anegados, una sola fragua
  apagada (en el sótano), 44 cofres en vez de 164, sin adornos caros ni bloques flotantes, puertas de 3 de ancho,
  90 monstruos y la arena del jefe cerrada a construir y romper mientras vive.
- **Monstruos:** el creeper ya no duda (enciende pegado a ti y finta menos), los grupos rodean de verdad desde lejos
  y corren a su puesto, los esqueletos se apartan para tener línea de tiro, los grupos mezclan tipos con un solo
  líder, y los élites conservan sus movimientos al recargar.

## 2026-09-29 — animaciones para los monstruos del mod

- **Arreglo de base: los monstruos se quedaban congelados.** Tras su primer golpe o su primer especial, todos los
  monstruos del mod (y el fuego del Herrero tras su primer destello) se quedaban para siempre en su pose de
  reposo: GeckoLib deja la animación disparada como la del controlador y no vuelve a preguntar. Ahora cada
  controlador vuelve a lo suyo (andar, correr, quieto...) al acabar; la prueba del cliente lo comprueba en cada
  monstruo, andando tras el golpe y tras sus especiales.
- **Aviso y golpe de verdad.** Antes de un golpe normal avisaban solo con partículas y el golpe se animaba después
  de hacer el daño (o no se animaba). Ahora, mientras avisan, cogen impulso (una pose que llega a su punto justo en
  el tick del golpe, dure lo que dure el aviso) y al golpear sueltan el golpe desde ahí, acierten o no.
- **Aturdidos, heridos y muertos.** Con la postura rota se tambalean (animación propia); al recibir un golpe
  retroceden y se inclinan como los de vanilla; y al morir tienen su propia muerte en vez de caerse de lado.
- **Correr.** Coraza, Autómata, Percutor, Tenaza, Templador, Cargador, Yunque andante y Guardián tienen carrera
  propia (zancada larga, brazos), no solo el andar acelerado.
- Por monstruo:
  - **Herrero Caído**: aviso (martillo arriba) y golpe; tambaleo; muerte (de rodillas y de bruces); **reforja**:
    arrodillado martilleando en la forja mientras se cura (antes no se veía nada: el cliente no sabía que
    reforjaba). Su fuego vuelve a arder tras cada destello. **Llamada a los aprendices**: clava el martillo en
    el suelo cuando salen (antes reutilizaba el rugido). **Lluvia de estrellas con aviso**: antes caía en el
    mismo tick en que la anunciaba; ahora alza el martillo al cielo sobre un círculo marcado en el suelo y las
    estrellas caen ahí 20 ticks después (1 s, lo mismo que dura la animación), así que se puede esquivar;
    mientras las llama no empieza otro golpe. Carrera propia (hoy los jefes no esprintan, pero ya la tiene).
  - **Autómata de Forja**: aviso y puñetazo; tambaleo; carrera; muerte (se apaga y cae de bruces). El escupitajo
    de ascua sale ahora en el empujón adelante (a los 14 ticks), no seis ticks antes.
  - **Coraza Vacía**: aviso y tajo; tambaleo; carrera; muerte (la armadura se desmonta en un montón). **Se hace la
    muerta** de verdad (antes se quedaba de pie "muerta"), se queda en el suelo hasta que se levanta, y **se
    levanta** pieza a pieza cuando reaparece.
  - **Pavesa** y **Ascua Mayor**: su golpe normal no tenía animación: ahora se echan atrás y embisten; tambaleo;
    la Pavesa se aviva y se apaga al morir (la Ascua sigue partiéndose). El **picado** sale en el tick en que
    se lanzan: la Pavesa se encoge y se aviva durante sus 8 ticks de aviso (antes ya iba en picado a los 3), y
    la Ascua cae a los 12 ticks (antes a los 9).
  - **Herrumbre**: aviso y mordisco; tambaleo; muere patas arriba pataleando.
  - **Escoria Viviente**: aviso y golpe (se echa atrás y se desploma encima); tambaleo.
  - **Yunque Andante**: aviso (se encabrita) y golpe; tambaleo; carrera; muerte (se le abren las patas).
  - **Percutor**: aviso y puñetazo con el brazo libre; tambaleo; carrera; muerte (el martinete cae y lo tumba).
  - **Tenaza**: aviso (pinzas abiertas) y pellizco; **sujeta** a quien atrapa con las pinzas cerradas mientras lo
    tiene (antes soltaba la pose al instante); tambaleo; carrera; muerte (se pliega).
  - **Templador**: tambaleo; carrera (huye); muerte (el depósito lo tumba de espaldas).
  - **Núcleo Estelar**: la descarga ahora **se carga** hasta el tick del rayo y **suelta** en el rayo (antes se
    encogía a los 7 ticks, antes de que saliera nada), también cuando está roto y avisa menos (la carga va más
    deprisa); **agotado** se ve (fragmentos caídos, anillo parado); tambaleo; muerte (estalla).
  - **Guardián del Cuño**: aviso y gancho; tambaleo; carrera; muerte (se le cae el troquel). Su animación de
    "desellado" movía un hueso que no existe; ahora mueve el cuerpo.
  - **Cargador de Carbón**: aviso y cabezazo; tambaleo; galope; muerte (se hincha y revienta).
- El Molde Roto no se ha tocado aquí.
- Un golpe solo reinicia su animación si de verdad empieza un golpe nuevo, como en el Molde Roto.
- Herrumbre, Escoria, Pavesa, Ascua y Núcleo no tienen carrera propia: corren con su andar (o su vuelo)
  acelerado, como antes.
- Rendimiento: un solo controlador por monstruo, clips creados una vez, sin crear objetos por fotograma en los
  controladores.

## 2026-09-28 — un huevo para cada monstruo

- Los quince monstruos del mod tienen huevo generador (antes solo cuatro): herrumbre, ascua mayor, escoria
  viviente, yunque andante, percutor, tenaza, cargador de carbón, templador, núcleo estelar, molde roto y
  guardián de cuño se suman al Herrero Caído, el autómata, la coraza vacía y la pavesa. Están juntos en la
  pestaña de Forja y se encuentran en la búsqueda creativa (`forja:huevo`); con el botón central sobre un
  monstruo en creativo se coge su huevo.
- Cada huevo es un dibujo propio de 16×16, como los de vanilla desde 26.x: la forma y la luz de un huevo
  vanilla, los colores del propio monstruo y lo que lo distingue asomando por el huevo (los ojos de brasa del
  Herrero y su cristal, la rejilla de horno del autómata, las alas de fuego de la pavesa, el cuerno del yunque
  y sus chispas, las mandíbulas de la tenaza, la estrella del núcleo, la barra dorada que parte el molde…).
  Los cuatro que ya había se han rehecho: eran el huevo zombi repintado con manchas al azar.
- Los dibujos viven en `tools/huevos.py` (una cuadrícula de letras por monstruo); `generate_assets.py` los usa.

## 2026-09-28 — barra de maná, y mejoras de maná y de estamina

- **Maná.** El báculo y el grimorio gastan maná de una barra azul y violeta encima de los corazones (enfrente de
  la de estamina, sin tapar corazones, armadura, aire, la vida de la montura ni la barra del jefe). Tiene 100. Solo
  sale si alguna vez has llevado algo que usa maná, y entonces mientras lo llevas en la mano o la barra no está
  llena; con F1 o en creativo no se ve. Con un arma mágica en la mano enseña el número (maná/máximo) y una muesca
  donde dejaría la barra el hechizo más barato.
- **Maná y un enfriamiento corto**, como pidió Andy: un proyectil del báculo cuesta 8 y la espera baja de 14 a 6
  tics; un área del grimorio cuesta 30 y la espera baja de 70 a 20. Una carga llena cuesta un 25 % más y pega un
  50 % más (cargar ahorra, tocar gasta deprisa). Se pelea a ráfagas: vacías la barra y esperas a que vuelva.
  Sin maná bastante el hechizo no sale: un chisporroteo y la barra destella en rojo (nada en el chat). Si sueltas
  una carga que la barra no paga, sale tan fuerte como el maná alcanza.
- **Vuelve solo, con el tiempo:** 6 por segundo mientras sigues lanzando y 20 por segundo a partir de 2 s sin
  lanzar (una barra vacía se llena en unos 7 s de calma).
- **Matar recupera, al maná y a la estamina,** pero como mucho un 3 % de la barra cada 5 tics: lo que vale cada
  muerte espera en un tramo claro al final de la barra y va entrando. Al maná, 8 + 0,4 por punto de vida máxima
  de la víctima (hasta un 30 % de la barra); a la estamina, 10 + 0,5 por punto (hasta un 40 %). Lo que no cabe se
  pierde. Los dos números y el ritmo están en la config de combate (`killFlowShare`, `killFlowEveryTicks`...).
- Al morir vuelves con la barra llena. El maná se guarda con el jugador (salir y entrar no la llena).
- Los monstruos con báculo o grimorio no usan maná y esperan lo de siempre entre hechizos (14 y 70 tics).
- **Mejoras de maná** (cada una con su receta en la mesa, su orbe al desarmar y su línea en la guía):
  - Báculo y grimorio: **Concentración** (lapislázuli / bloque de lapislázuli): los hechizos cuestan hasta un 35 %
    menos. **Sifón** (lágrima de ghast + lapislázuli): si el hechizo alcanza algo, devuelve hasta la mitad de lo
    que costó, una vez por hechizo. **Descarga** (carga ígnea + bloque de lapislázuli): con la carga llena vuelca
    toda la barra en el hechizo, +25 % de daño por cada 10 de maná de más, y sale grande. **Meditación** (vela /
    fruta coral reventada): en la mano, el maná vuelve hasta un 60 % más rápido.
  - Armadura, pieza a pieza y sumando: **Reserva** (lapislázuli / bloque): +25 de maná máximo por pieza (+100 con
    las cuatro). **Flujo** (fragmento / bloque de amatista): el maná vuelve un 25 % más rápido por pieza.
    Conjuntos: cuatro piezas de amatista suman +40 de maná máximo (además del +1 de daño) y cuatro de eco hacen
    que vuelva un 40 % más rápido.
  - Armas de filo (espada, daga, espadón, guadaña y lanza): **Filo arcano** (lapislázuli + amatista): cada golpe
    gasta 5 de maná y suma hasta un 40 % del golpe como daño mágico. **Estallido arcano** (lapislázuli + carga de
    viento): el golpe cargado a tope gasta 20 y estalla, hasta un 60 % del golpe a todo lo que hay a 3 bloques del
    objetivo. **Paso arcano** (lapislázuli + perla de ender): esquivar con ella en la mano gasta 15 y te lleva
    hasta un 80 % más lejos, con 3 tics más de invulnerabilidad. Con la barra vacía no hacen nada.
- **Mejoras de estamina:** **Aguante** (filete cocinado, armadura): +15 de estamina máxima por pieza. **Fuelle**
  (cuero, armadura): la estamina vuelve un 20 % más rápido por pieza. **Quiebro** (pata de conejo, botas): la
  esquiva llega un 50 % más lejos. **Impulso** (pistón, grebas): la embestida de los guanteletes te lleva un 60 %
  más lejos, y su golpe alcanza lo mismo. **Soltura** (panal, armadura; cuenta la mejor pieza): saltar, esquivar y
  los ataques especiales cuestan un 35 % menos de estamina.
- La guía tiene un capítulo nuevo, **Maná y estamina**, con todos los números sacados del código. El informe de
  equilibrio simula la barra (ráfaga hasta vaciarla y descanso); el báculo sin mejoras pasa de 20,5 a 42,9 de daño
  por segundo en ráfaga y de 16,2 a 17,9 sostenido; al 100 % la ráfaga sube (87 → 165) y lo sostenido baja
  (76 → 53), que es lo que se buscaba: golpes fuertes y luego esperar.

## 2026-09-28 — el Molde Roto da estocadas

- Con una lanza, un tridente o una daga copiados ya no los alza sobre la cabeza: durante el aviso los recoge a la
  altura de la cadera con la punta por delante y, al golpear, los lanza rectos hacia delante a lo largo del asta,
  con un paso y el cuerpo detrás; luego vuelve a la guardia. La daga también la lleva ahora con la punta al frente.
  Las demás armas siguen con el tajo de arriba abajo.

## 2026-09-28 — el cielo de los eventos deja de parpadear

- **El parpadeo**: la luz del suelo de los eventos se aplicaba una vez por fotograma sobre la del fotograma
  anterior, y el juego solo la recalcula una vez por tick. Entre tick y tick se iba acumulando (a 240 fps, una
  docena de veces) y volvía de golpe en el siguiente: el mundo entero parpadeaba 20 veces por segundo. Se veía en
  todos los eventos que tocan la luz (marea viva, luna de sangre, eclipse, niebla de almas, ventisca, aurora,
  lluvia de pavesas, tormenta arcana), con y sin Sodium, en OpenGL y en Vulkan. Ahora se aplica solo cuando el
  juego la recalcula. La noche de la marea viva queda algo más oscura que antes: antes brillaba de más porque
  el efecto se sumaba varias veces.
- **La luna ovalada**: con un FOV amplio (Andy juega a 102), la luna de la marea viva y la de sangre, y el
  disco del eclipse, salían estirados como óvalos cerca del borde de la pantalla. Ahora se dibujan redondos
  en cualquier parte de la pantalla, y la luna ya no da media vuelta de golpe al pasar por encima.
- La luna, el sol y el eclipse del juego se apagan debajo de los del evento en lugar de quedarse detrás: en el
  eclipse asomaban trozos del sol cerca del borde de la pantalla.
- El borde de la luna de la marea viva ya no tiembla: solo respira el halo, no la luna pixelada.
- Las capas del cielo (halos, coronas, cortinas de la aurora, runas) se dibujan siempre en el mismo orden;
  antes el orden dependía de qué más se dibujaba en ese fotograma y podía cambiar de uno a otro.
- **Con Sodium**, el terreno ya recibe la niebla del evento (ventisca, niebla de almas): antes Sodium copiaba
  la niebla antes de que el evento la cambiara, y el suelo se veía oscuro y nítido dentro de la ventisca.
- Pruebas: `FORJA_SOLO=cielo` fotografía cada evento de noche, al anochecer y de día, mirando al cuerpo de
  frente, arriba, al lado y en el cénit, seis fotogramas seguidos, y falla si la luna o el suelo saltan de un
  fotograma al siguiente. Con `FORJA_SODIUM=1` la prueba de cliente carga Sodium e Iris (solo en desarrollo; Iris
  necesita OpenGL, así que sin el script de Vulkan).

## 2026-09-28 — el Molde Roto agarra el arma

- Antes los puños flotaban delante de la barriga, sueltos de los brazos (que colgaban a los lados y un poco hacia
  atrás), y la copia era el dibujo plano del arma puesto de frente entre los dos puños: de lado no se veía, la
  lanza salía atravesada y el mangual con el palo fuera de las manos. Y no tenía animación de ataque: pegaba quieto.
- Ahora los brazos (hombro, codo y antebrazo) llegan a los dos puños, uno encima del otro, cerrados sobre el mango.
  La copia se agarra por su propia empuñadura, igual que la lleva un jugador: la espada por el puño con la guarda
  encima, el martillo y el hacha por el mango, la lanza y el tridente inclinados hacia delante, el mangual con su
  cadena y su bola colgando de verdad, el grimorio como libro. Se ve de frente, de lado y de tres cuartos.
- Pega de verdad: durante el aviso sube el arma por encima de la cabeza con las dos manos y, al golpear, la baja
  delante de él en un tajo; luego vuelve a la guardia. La copia y el lingote siguen a los brazos en todo (quieto,
  andando, al meterlos en el horno para recolar).
- El lingote al rojo desaparece en cuanto tiene la copia en las manos y vuelve si la pierde.
- Arreglado: después de su primera recolada el Molde Roto ya no volvía a andar (se quedaba con la animación de
  recolar puesta para siempre).

## 2026-09-28 — el enderman esquiva teletransportándose

- Cuando le va a llegar un golpe con alguien detrás, el enderman tiene un **34 %** de probabilidad de esquivarlo
  teletransportándose: aparece a 4–8 bloques, hacia un lado o alejándose de quien le pegó, con el sonido y las
  partículas de siempre, y el golpe no le hace nada (ni daño, ni postura, ni armadura). Sigue sabiendo quién fue y
  va a por él.
- Si la teletransportación sale bien, pasa **7 segundos** sin poder esquivar así. Si no encuentra dónde aparecer
  (encerrado), no hay esquiva ni enfriamiento: se come el golpe y el siguiente vuelve a tirar.
- Qué esquiva: golpes cuerpo a cuerpo de jugadores y de mobs, espinas, explosiones que alguien provocó y la magia
  (el rayo del báculo, el área del grimorio). Las flechas y demás proyectiles, y las pociones lanzadas, siguen como
  en vanilla: las esquiva siempre él solo. Nunca esquiva el daño sin atacante (caída, lava, fuego, cactus, ahogarse,
  el vacío, /kill, tampoco un /damage generic_kill "de" un jugador en creativo), un golpe que la invulnerabilidad de
  después de un golpe se iba a tragar de todas formas, ni estando aturdido (la postura rota es la ocasión del
  remate).
- Va igual con la IA del mod, con la de vanilla o sin IA. En la config de combate: `endermanDodgeChance` (0,34; 0
  lo apaga) y `endermanDodgeCooldownTicks` (140). El enfriamiento no se guarda con el mundo.

## 2026-09-28 — los monstruos cuentan con el alcance del arma

- Un monstruo golpea desde donde llega **su arma**, no desde donde llega su brazo: el golpe empieza y acierta a
  su alcance (el del cuerpo, 0,83 entre cajas, más lo que añada el arma). Mangual 3,83 (antes 0,83), lanza 2,33
  (y su punta no sirve a menos de 1), guadaña 1,58, tridente forjado 1,33, y la mejora Alcance suma lo suyo. Con
  espada, hacha, daga, mazo, martillo o la mano vacía todo sigue igual.
- Con un arma larga ya no se mete en la cara del jugador: se para a su alcance y golpea desde ahí. Con lanza,
  si está demasiado pegado, retrocede hasta tener sitio para la punta. La embestida del zombi empieza más lejos
  con un arma larga (mangual: desde 6,5 en vez de 3,5), para no saltarle encima a quien ya alcanza.
- Esperando su turno, se quedan fuera del alcance **del arma del jugador**: el anillo, la espera y el relevo se
  abren tanto como el arma alarga su brazo (mangual +3: el anillo pasa de 3,5 a 6,5 y la espera de 5 a 8; lanza
  +1,5). También se apartan antes de un golpe cargado y rodean desde más lejos.
- Vale igual para los monstruos que lleva una red y para los de las reglas. Las redes entrenadas cargan igual:
  sus entradas no cambian (obj_en_alcance sigue siendo el alcance del cuerpo, como en el simulador).

## 2026-09-28 — el Herrero Caído se defiende

- El Herrero Caído ya pelea contra lo que le pegue, no solo contra jugadores: un warden, un gólem, lobos, otro
  monstruo. Se da la vuelta y usa todo lo suyo contra ello (revés, onda, garfio, el cielo).
- A quién pelea: un jugador que le pega se lo lleva al momento, pase lo que pase. Otra cosa que le pega se lo lleva
  solo si no está peleando con un jugador que le haya pegado en los últimos 5 s. Y algo que no es un jugador lo
  retiene solo mientras le siga pegando: 5 s sin golpes y se vuelve al jugador más cercano que vea.
- Sus aprendices lo defienden: van a por lo que le pegue (jugador o no) y, si no tienen pelea propia, a por lo que
  él esté peleando. Nunca entre ellos ni contra él. Vale también tras guardar y cargar el mundo.
- Contra el truco de ponerle un warden al lado: lo que no viene de un jugador le hace la mitad
  (`jefeDanoAjeno` en la config de combate, 0,5). Tus lobos domados y tus flechas cuentan como tú y pegan entero;
  el daño sin atacante (lava, caída, los rayos de tus mejoras) no se toca. El botín y el logro siguen igual.
- La lluvia de estrellas de su último cuarto ya no le cae a sus aprendices ni a sus yunques.
- La guía (capítulo del Herrero) lo cuenta.
- Revisado: el Guardián del Cuño y los campeones ya respondían a quien les pegara; ahora hay prueba de ello.

## 2026-09-28 — la red de metal: depósitos, llenado de uno en uno y llave de paso

- **Una sola red.** Crisoles, cubas, caños y mesas de colada unidos por conductos son una red: el metal va de
  donde sale a donde hace falta aunque estén lejos (hasta 1024 bloques de conducto; antes 64). La red se calcula
  una vez y se recuerda; sólo se vuelve a calcular cuando se pone, se rompe o se gira un bloque de ella.
- **Cubas que se funden.** Cubas del mismo metal (o vacías) pegadas en cualquier dirección son un depósito: una
  cabida (la suma), un metal y un solo nivel que sube por el cristal de abajo arriba, igual en toda la capa. Al
  romper una, derrama lo suyo y el resto sigue siendo depósito con lo que tenía; dos metales pegados (de un mundo
  viejo) son dos depósitos. Los mundos viejos cargan tal cual y el depósito se forma al cargar. Un comparador al
  lado de cualquier cuba lee lo lleno que está el depósito entero.
- **Llenado de uno en uno.** Lo que sale del crisol llena UN depósito hasta arriba antes de empezar otro: primero
  el que ya tiene ese metal y no está lleno; si no, el vacío más cercano por la red. Nunca uno con otro metal.
  Si ninguno tiene sitio, el crisol espera y su pantalla dice "no hay cuba con sitio" (sin ninguna cuba en su
  red, la mena sigue saliendo en lingotes por abajo como antes).
- **Llave de paso** (nuevo bloque): un tramo de conducto con compuerta. Abierta deja pasar, cerrada corta la
  red en dos. Se gira con clic derecho y una señal de redstone la cierra mientras dure. Abierta se ve la rueda
  roja arriba y la compuerta levantada; cerrada, la compuerta roja metida en el canal.
- Una cuba cuajada en la red del crisol se refunde ANTES de fundir mena nueva (antes, con una tolva vaciando el
  crisol, la mena salía en lingotes para siempre y la cuba seguía fría).
- Los conductos ya no se enganchan a tolvas ni cofres, ni les dan lingotes: una tolva que tocaba un conducto
  (la que llena el crisol) vaciaba la cuba y le devolvía el metal al crisol. La única salida en lingotes es el
  "grifo": un contenedor justo debajo de una cuba (y no una tolva que vierte en un crisol).
- Con una cuba en la mano se puede poner otra encima de una cuba (antes decía "no es metal").

## 2026-09-28 — la montadora y la fundición sin manos

- Nuevo bloque: la **montadora**. Es la estrella de la mesa de forja sin el herrero: las piezas le entran por
  tolva (por arriba o por los lados) y monta lo mismo que la mesa, con la misma regla; la pieza sale por abajo.
  Siempre calidad normal: nunca perfecta ni obra maestra, sin firma, con el potencial de un golpe decente. Las
  mejoras, reparaciones, cambios de piezas y técnicas siguen siendo a mano, en la mesa.
- Pide calor: al menos una fogata, debajo o al lado (debajo suele ir la tolva). Más calor, más rápido: 5 s por
  pieza templada, 3 s caliente, 2 s con lava o un farol de pavesa.
- Un marco de colada en el centro le dice qué montar: solo acepta las piezas de eso, las busca donde estén y no
  se atasca, y así también saca espadones (dos hojas de la misma pila). Sin marco, una pieza de cada clase por
  punta, como en la mesa. Las bardas siguen siendo de la talabartería.
- Da señal a un comparador: 15 con una pieza esperando, de 1 a 14 mientras monta. Al romperla suelta todo.
- Receta: acero, pistón, redstone, una mesa de forja mayor y una tolva.
- Comprobado que toda la fundición funciona sin tocar nada: mena por tolva al crisol, ascuas de lado, cuba sobre
  un farol, caño por el colador a la mesa con molde, tolva debajo y montadora hasta el cofre.
- La guía (capítulo de fundición) explica la fundición sin manos y la montadora.

## 2026-09-28 — fundición 2: calor por tubos

- **Tubos de calor**: un segundo sistema de tubos, que nunca se junta con los conductos de metal. Llevan un fluido
  de calor desde una **caldera** o un **depósito de calor** hasta lo que pide calor: crisoles, mesas de colada y
  mesas de forja. Lo que toca un tubo recibe el calor de su fluido (el mejor entre ese y lo que tenga debajo). Se
  ve el fluido por la rendija del tubo y la ventana de la caldera, cada uno con su color.
- **Caldera** (4 cubos): hierve lo que le echas, a mano o con tolva (los cubos vacíos salen por abajo). El
  **depósito de calor** (8 cubos) guarda lava. Un comparador lee lo llenos que están.
- Cinco fluidos:
  - **Vapor** (agua en la caldera con cualquier fuego debajo): calor templado, barato; sólo funde lo blando (oro,
    cobre, peltre, latón; el hierro no) y la mesa de colada no pasa del 50 % de calor. 1 mB por tick.
  - **Lava** (cubos de lava o bloques de magma en el depósito): calor fundido, como la lava debajo pero por tubos.
    1 mB por tick.
  - **Sangre de blaze** (varas o polvo de blaze en la caldera): calor fundido, el crisol funde un 50 % más rápido
    y la mesa de colada se calienta el doble. 2 mB por tick.
  - **Aliento de forja** (escoria o corazón de forja en la caldera, sobre fuego fuerte): forja blanca sin crisol de
    obsidiana, y +20 % de que una herramienta colada salga perfecta. 4 mB por tick.
  - **Salmuera helada** (hielo compacto o azul): enfría. La mesa de colada templa en agua cada herramienta que
    cuela, la mesa de forja templa lo recién forjado (100 mB por temple), una mesa sin fuego se enfría el doble y
    un crisol que la toca se apaga.
- Un crisol con un tubo caliente no gasta ascuas. Todo el calor del mod se lee ahora en un solo sitio
  (`Alloys.heatAt`: lo de debajo o un tubo, lo más caliente), también en el crisol y la mesa de colada: un
  crisol que arde sobre lava o magma arde así de caliente, y una fogata debajo de una mesa de colada la calienta.
  Las redes de tubos se guardan en caché: no se recorren cada tick.
- Capítulo de fundición de la guía: paso 8, con la tabla de los cinco fluidos.

## 2026-09-28 — la colada cae por el colador

- Las piezas ya no se cuelan dentro de la caja de moldeo: el molde se pone encima de una mesa de colada, igual
  que un marco, y el metal le cae desde el caño.
- El colador es ahora un bloque: se pone encima de la mesa (entre el caño y el molde), el chorro se ve pasar por
  él, y al romperlo devuelve el mismo colador con su metal.
- Un colador que aguanta el metal da una colada limpia (y la pieza, su mejora del 15%); si no aguanta, se rompe al
  empezar y sale basta; sin colador, basta. Vale igual para los marcos. El calor de la mesa sigue contando.
- Cada mesa aguanta hasta cierta dureza: losa hasta 700, brasa hasta 1600, almas todo.
- La caja de moldeo sigue sacando moldes y marcos y bañando coladores, y avisa de que los moldes van a la mesa.
- La mesa acepta moldes y marcos también por los lados (tolva), suelta lo hecho por debajo y, tras recoger una
  pieza a mano, espera 2 s antes de volver a colar para poder cambiar el molde.

## 2026-09-28 — la forja funciona de verdad

Revisión a fondo de fundición, mesas y materiales, jugando el camino de supervivencia con clics reales.

### Fundición
- Cada hueco acepta solo lo suyo (crisol, cuba, caja de moldeo), también con mayúsculas, arrastre y tolvas; el
  crisol respeta su cabida en la pantalla.
- El crisol funde mena y lingotes a la cuba (con calor según la dureza), y piezas sueltas de vuelta.
- Con una cuba pegada ya no se para tras la primera tanda de aleación; varias cubas se reparten el vertido.
- Romper crisol, cuba, caja, mesa de colada o armario de piezas suelta lo que tenían (antes se perdía).
- La caja de moldeo ya no se para tras una colada y su pantalla acepta herramientas (el marco) y coladores.
- Las pantallas dicen lo que pasa de verdad ("Fundiendo a hierro", "falta ascua", "no hay cuba con sitio"...).
- La mesa de colada sobre un farol ya no saca basta la primera herramienta, y la herramienta sobre la mesa no se ve negra.

### Mesas
- Mayús+clic sobre una pila de piezas ya no la reparte por todas las puntas de la estrella.
- La estrella, la mesa de piezas, la bandeja de extracción y el yunque de viaje solo aceptan lo que usan, y dicen
  por qué rechazan algo (el metal no se corta: se cuela en la fundición).
- Arregladas dos duplicaciones (derretir pilas de piezas sobre lava y desarmar flechas) y desarmar ya no borra la pila.
- Un juego de piezas que forma un objeto se forja en vez de derretirse; el yunque de viaje devuelve lo que tenía.
- Avisos más claros: centro ocupado, maestría que hace falta, qué mesa monta cada objeto.

### Materiales
- Fundir o desarmar escoria ya no da netherita; la escoria, la ascua y el corazón de forja no se queman en la lava.
- Los cofres de la sala de guardia del Bastión ya no salen vacíos y el Túmulo del herrero no tiene huecos.
- La guía dice de dónde sale cada material, si se corta o se cuela, y la receta de cada aleación (hay 16).

## 2026-09-26

Todo en la rama `forja-ia-armas`; las pruebas de servidor pasan (120).

### Bastión del Gremio

- **Las once torres por dentro**: escalera de caracol de piedra alrededor de un machón, y cada piso una
  sala con el tema de su torre (cobre, eco, obsidiana, resina, escama, vidrio, archivo, pavesas, fuelle,
  vigía; la Hundida en ruinas), alfombra alrededor de la escalera y estandartes de su color.
- **Los sótanos**: cripta de los Nueve Maestros (tumbas entre los pilares y altar), osario, mazmorras con
  forjadores presos y un pozo hasta la cámara, guardia, bodega, lingotes, carbonera, antesala, forja de
  almas y cámara acorazada.
- **Salas que estaban desnudas**, amuebladas: sala de cuños, pisos altos de la torre del homenaje,
  taberna, arquero, talabartería, caballerizas, guardia, polvorín, aleaciones, sacristía, gabinete de
  orbes, estandartes en mástiles, taller, Fragua Profunda y las seis casas de aprendices, cada una con su
  oficio.
- **Botín por zonas**: nueve tablas nuevas (torre, cima de torre, cripta, guardia, bodega, lingotes,
  carbonera, forja de almas, cámara).

### Peso

- **Cada arma pesa** según su tipo y sus materiales (una daga medio kilo, un martillo tres y medio; la
  madera aligera, la netherita pesa), y la hoja lo muestra.
- **Jugador**: un arma más pesada tarda más en llegar al golpe al 100 % (la de hierro va como siempre); la
  armadura pesada quita velocidad de ataque y un conjunto que da velocidad la sube. El golpe se ve más
  lento con armas pesadas.
- **Enemigos**: cuanto más peso llevan (arma y armadura), más largo es su aviso y más esperan entre golpes.

### Enemigos y armas

- Los **esqueletos con arco forjado** y los **saqueadores con ballesta forjada** disparan de verdad.
- Algunos **esqueletos llevan báculo** y algunos **zombis grimorio**, y los usan con aviso.
- La **lanza** en manos de un enemigo avisa y espera su turno; el tridente forjado cuenta como tridente.
- **Dos jugadores**: los turnos, las escuadras y las barras de jefe ya no se mezclan entre jugadores.
- El **molde roto** sostiene de verdad la copia del arma que le golpeó; `/forja fundicion` levanta la línea
  de fundición delante de ti.
- Un **arma lanzada** (daga, hacha, tridente) pega con su propio daño (antes hacía 1).

### Animaciones

- **Cada arma tiene su propio golpe**, en tercera y en primera persona, con su remate de combo y su pose
  de carga: tajo de espada, barrido del espadón, siega de la guadaña, martillazo hasta el suelo, el mangual
  girando sobre la cabeza, estocadas de daga y tridente, directos alternos de los guanteletes...
- **Los enemigos avisan con la pose de su arma** (martillo en alto, lanza recogida, espadón abierto) durante
  todo su aviso, y la sueltan en el golpe; si amagan, la bajan.
- **El mangual se agarra por el mango** (la bola cuelga debajo) y **la daga por la empuñadura**, en tercera
  y primera persona, en jugadores, mobs y soportes de armadura.
- **Los guanteletes se llevan puestos** en las dos manos: placas en 3D sobre el puño y la muñeca, con los
  colores de sus materiales, que siguen cada golpe; en primera persona se ven los puños enguantados.
- **El grimorio es un libro de verdad** (el de la mesa de encantamientos, con las tapas de su material, la
  gema y las esquinas): cerrado en la mano, agarrado por el lomo; se abre y pasa las páginas al golpear, al
  lanzar un hechizo, al cargar y mientras un enemigo lo lee, y luego se cierra.
- En el inventario los cuatro se ven como siempre.
- **El mangual en 3D con la bola encadenada**: mango en el puño, una cadena de eslabones y la bola con
  pinchos, teñidos por sus materiales. La cadena cuelga con gravedad y se arrastra detrás de la mano al
  moverla; al atacar, la bola se voltea (por arriba, de lado o desde abajo), sale en arco y recorre la
  distancia del golpe hasta lo que golpea (con nada delante, los 3 bloques de la cadena), y vuelve.
  Jugadores, soportes, zombis y demás mobs (también en su aviso) y primera persona.
- **Más golpes por arma**: cada arma tiene tres o cuatro golpes distintos más el remate (la espada: tajo
  diagonal, tajo horizontal, revés y estocada; el martillo: martillazo, barrido y uppercut...), y una
  racha los va encadenando sin repetir. Quien mira ve el mismo golpe que quien lo da, y el aviso de un
  enemigo muestra el golpe que va a soltar.

### Pactos y sinergias

- **Dos pactos por objeto como mucho**, y **tres sinergias despiertas** por objeto: si una pieza llega a
  una cuarta, despiertan las tres más fuertes (sus dos porcentajes sumados) y la otra duerme hasta
  superar a alguna. El tooltip dice cuál duerme.
- **Cada pacto se abre una vez**, ofreciendo algo raro en la estrella junto a sus ingredientes: sed, un
  tótem de la inmortalidad; vidrio, una estrella del Nether; sombra, un fragmento de eco; prisa, un
  corazón del mar. La ofrenda se gasta y el pacto queda abierto para ese jugador para siempre (también
  tras morir). Sin abrir, la forja enseña el pacto sellado y lo que pide.
- El **molde roto** copia el arma **exactamente**, mejoras incluidas.
- **Todas las armas y herramientas forjadas cargan el golpe** (guanteletes, báculo, grimorio y
  herramientas tenían la pose de carga y nunca la usaban).

### Rangos, mangual y golpes (27-09)

- **Veteranos, élites y campeones se reconocen de un vistazo**: insignia sobre la cabeza (un galón de bronce,
  dos de plata, dos de oro con estrella), visible hasta 25 bloques; nombre propio con apodo de su rango
  ("Karn Rompehuesos", "Morvek la Hoja Gris", "Ulgar, Azote de Reinos") en una placa del metal de su rango.
- **Botín**: el veterano suelta como mucho 1 cosa extra y la élite 2 (antes repetían todo su botín); el
  campeón solo su arma legendaria. El logro pasa a llamarse **Cazador de campeones**.
- **Entrar, pegar y salir**: tras acertar un golpe, cualquier mob cuerpo a cuerpo (arañas incluidas) se agacha un
  instante y salta 2-3 bloques atrás, como mucho cada 6 segundos, y deja su turno a otro; nunca contra un muro,
  por un barranco o a la lava. El creeper da un saltito corto cuando finta su sisseo.
- **Estamina**: saltar cuesta 4 (8 corriendo; sin estamina se salta igual, pero no se recupera ese segundo) y
  los especiales del arma cuestan además de durabilidad: Torbellino 30, Sismo 35, Siega 30, Embestida 20; sin
  la estamina suficiente no salen.
- **Los mobs corren** (+35 %) con una estamina propia (100; 2 por tick corriendo, se recupera 1 por tick tras 1 s
  sin correr; agotados, no vuelven a correr hasta tener 25): para alcanzar a quien se aleja (a 4–12 bloques), para
  llegar a su hueco del anillo y para huir malheridos. No los jefes. Se les ve el polvo del sprint.
- **Animación de correr**: el cuerpo se inclina hacia delante con la cabeza aún hacia el objetivo, zancada más
  larga y un leve bote; brazos que bombean con la mano vacía, el arma llevada baja y adelantada (a dos manos,
  cruzada delante), el zombi con los brazos por delante y la araña más baja y rápida; el creeper se bambolea.
  Los mobs de Forja aceleran su animación de andar (×1,6). Entra y sale en unos ticks; el golpe, el aviso, la
  carga y el aturdimiento mandan sobre ella.
- **Animación de salto**: al saltar hacia el objetivo se estiran hacia delante con las piernas atrás (el zombi
  con los brazos para agarrar, la araña con las patas abiertas, el arma echada atrás lista para el golpe); el
  salto atrás va encogido con la guardia delante; la carga del bruto, con el hombro por delante. Al caer, un
  instante agachados y polvo del suelo.
- **Todos los mobs fintan** un poco (5–20 % según la dificultad), más contra quien para mucho.
- **Mangual en 3D**: mango, cadena de eslabones y bola con pinchos; la bola cuelga, se arrastra y en el golpe
  sale en arco hasta el enemigo con la cadena estirada.
- **3 o 4 golpes distintos por arma** (tajos, reveses, estocadas, golpes desde arriba y desde abajo...), sin
  repetir el mismo dos veces seguidas; todos ven el mismo golpe.

### Magia, grupos y rodear (27-09)

- **Báculo y grimorio cargan con clic derecho**: mantenido, el hechizo se reúne con motas del color del
  núcleo que se acercan a la mano (barra de carga sobre la mira, carillón al llenarse); al soltar sale con
  hasta +50 %. Un toque es el hechizo de siempre. El clic izquierdo con ellos ya no carga.
- **El grimorio al cargar** se abre de golpe y las hojas pasan cada vez más rápido, con las tapas temblando.
- **Casi nunca aparece un monstruo solo**: uno corriente viene con compañeros (grupo de 2 o 3) el 75 % de
  las veces, y un veterano o una élite siempre, en grupos de 3 a 6. Nunca con más de 12 hostiles cerca.
- **Rodean**: los huecos del anillo se llenan desde el lado por el que llegan hacia los lados y la espalda;
  el que tiene turno y está lejos de su hueco da la vuelta antes de golpear, los que esperan lo hacen en su
  hueco (no delante) y todos van por el anillo en vez de cruzar por delante del jugador.

### Guía y logros

- **Camino guiado**: la guía dice cuál es el siguiente paso y te lleva a su capítulo; una pista en el chat
  al avanzar (se puede apagar).
- "Primeros pasos" corregido (el metal se cuela, no se corta; Filo sale de la amatista).
- Los logros **Acero plegado, Del cielo, Corazón de forja y Cazador de élites** ya se pueden conseguir.

## 2026-09-17 (mañana)

Todo probado con `./gradlew runClientGameTest` (todas las comprobaciones pasan) y compilado en
`build/libs/forja-1.0.0.jar`.

### Objetos nuevos

- **Ballesta modular** (brazos de arco + cuerda + mango + guarda): carga como la de Minecraft, dispara
  flechas y cohetes, los brazos suman daño a la flecha y se sostiene apuntando cuando está cargada.
  Mejoras propias: **Carga rápida** (gancho de cuerda) y **Perforación** (pedernal, no se junta con
  Multidisparo).
- **Guadaña** (hoja + mango + atadura): lenta, barre como una espada, llega 0.75 bloques más lejos y con
  clic derecho cosecha y replanta 3x3 (5x5 o 7x7 con Cosechador).
- **Caña modular** (mango + cuerda): dura más que la de Minecraft según el mango y la cuerda, y tiene
  **Cebo** (semillas o bacalao) y **Suerte del mar** (caracola o corazón del mar).
- **Orbes de mejora:** al desarmar, cada mejora sale en un orbe con la mitad de su porcentaje. En la
  estrella, un orbe junto a un objeto le suma su mejora; dos o más orbes iguales sin nada al centro se
  fusionan. También salen en botín y los venden los herreros.
- **Kit de reparación** (lingote de hierro + cuero + hilo): clic derecho con el kit sobre un objeto
  forjado del inventario y le repara el 15% (mínimo 20), incluso si está roto.

## 2026-09-18 (madrugada)

### Armario de piezas

- Bloque nuevo (3 tablones arriba, 3 abajo, lingote + cofre + lingote en medio): **27 huecos que solo
  aceptan cosas del mod** — piezas, plantillas, orbes, sellos, talismanes, objetos forjados y los
  materiales propios (acero, damasco, hierro estelar, placa hueca...). El adoquín se queda fuera.
- Usa la pantalla de cofre de siempre, así que no hay nada nuevo que aprender, y como filtra también lo
  que le meten las tolvas, sirve para ordenar automáticamente la mesa de trabajo.
- Al romperlo suelta lo que tuviera dentro, como un cofre.

### Dos sinergias para lo nuevo

- **Tempestad** (Corriente + Canalización, tridente): con tormenta encima, saltar con el tridente deja
  **un rayo donde estabas**.
- **Siega negra** (Cosechador + Siega de almas, guadaña): la siega te cura medio corazón por enemigo
  arrastrado, hasta tres corazones.
- Ya son 24 sinergias, y las dos leyendas nuevas (Marea del ahogado y Filo de la guadaña) traen
  justamente esas parejas.

### Martillo del maestro

- El Herrero Caído suelta ahora **su propio martillo**. Es lo único en el mundo que **deshace una
  elección de técnica**: al usarlo olvidas las tres y vuelves a elegir en la mesa, y el martillo se gasta
  al hacerlo. Si no has elegido ninguna todavía, no se gasta.
- Logro propio ("Pensándolo mejor") y explicado en el capítulo de técnicas del libro.

### Arreglo: la rejilla de piezas se había quedado pequeña

- La mesa de piezas dibujaba las formas en filas de diez dentro de un hueco hecho para dos filas. Con 32
  piezas, **doce quedaban pintadas encima de las ranuras y del texto**, difíciles o imposibles de elegir.
- Ahora son **tres filas de once** en un hueco más alto, y la fila de ranuras (plantilla + material ->
  pieza) baja para dejarles sitio. Se ven las 32 de un vistazo.

### El índice del libro, con dibujos

- Cada capítulo del índice lleva ahora **el icono de lo que trata** (el libro, la mesa, un pico, un orbe,
  un sello, un frasco...) y los títulos largos se escriben algo más pequeños para no chocar con el
  número de página.

### Túmulo del herrero (quinta estructura)

- Una tumba bajo tierra (entre -48 y 8 de altura, en cualquier bioma del Overworld): sala abovedada de
  ladrillos de pizarra, el ataúd con su mesa de herrería encima, **su yunque a la cabecera**, faroles de
  almas en las esquinas, un cofre y un barril con lo que le enterraron (plantillas, sellos, diamantes,
  botellas de experiencia) y **dos corazas vacías montando guardia**.
- Es rara (separación de 44 chunks) y no se anuncia con ningún mapa: hay que encontrarla cavando.

### La estrella respira

- Las puntas de la estrella con pieza puesta llevan ahora un **halo ámbar** que late despacio, y cuando
  lo que hay encima sirve para algo (forjar, mejorar, reparar, fundir...) el halo se enciende más y el
  centro también. Antes la estrella era un grabado quieto y no decía nada.

### Alarma de balance

- El test comprueba que ninguna arma en hierro baje de 3 de daño por segundo (lanza, 3.3) ni pase de 14
  (guanteletes, 11.6), y que ningún conjunto de armadura pase de 24 antes de mejoras (corazón, 24).
  No es una regla de diseño: es una alarma para cuando una fórmula se descoloque.

### Documentación que no se queda vieja

- El README llevaba una tabla de **75 mejoras** escrita a mano (hay 106) y una tabla de conjuntos con
  20 materiales de 31. Ahora **el propio test escribe** `docs/MEJORAS.md` (las 106 mejoras con lo que
  las alimenta y lo que hacen al 100%, más las 22 sinergias) y `docs/MATERIALES.md` (los 31 materiales
  con durabilidad, minado, daño, rasgo y bono de conjunto).
- El README se queda con lo que hay que saber de memoria y enlaza a esas dos tablas. Pasó de 581 a 449
  líneas y ya no puede mentir.

### Pacto de la prisa

- Cuarto pacto, y el primero para **herramientas**: mina un **45% más rápido** y aguanta un **45% menos**
  (en un pico de hierro: 6.0 -> 8.7 de minado, 300 -> 165 de durabilidad). Se alimenta con azúcar y crema
  de magma.
- Los encargos del Forjador piden ahora también tridentes, cinceles y ballestas.

### Dos leyendas más

- **Marea del ahogado** (tridente de prismarina, con Corriente y Canalización) y **Filo de la guadaña**
  (guadaña de eco, con Cosechador y Siega de almas). Ya son 14 leyendas con nombre en el botín.

### Placa hueca (material 31)

- La **coraza vacía** suelta ahora **placa hueca** (40% de probabilidad, 1-2), un material propio del mod
  que no se consigue de ninguna otra forma.
- Es ligera y algo blanda (760 de durabilidad, 5.5 de minado), y trae el rasgo **Vacío**: el acero
  corriente resbala en ella, con un **3% por pieza** de que un golpe de algo sin forjar no te llegue
  (casi todo lo que hay en el mundo pega sin forjar, así que el número se queda pequeño a propósito).
- Conjunto completo: andas un 8% más rápido y te agachas casi sin ruido.

### Dos talismanes más

- **Prismarina** (fragmento de prismarina + 8 pepitas): bajo el agua respiras, ves y trabajas como en
  tierra.
- **Netherita** (lingote de netherita + 8 pepitas): nada te empuja tan lejos como pretende (+0.2 de
  resistencia al empuje).
- Ya son siete, y siguen contando solo el primero que encuentre la mochila.

### Campamento nevado y avisos

- Segunda variante del campamento saqueador: **de roble oscuro sobre nieve**, con tiendas blancas, para
  llanuras nevadas, taiga nevada y arboledas. Las dos variantes salen por igual.
- Al subir a maestría 3, 6 o 9 el juego te avisa por chat de que **tienes una técnica esperando** en la
  mesa de forja.

### Tres dones más

- **Tirador** (arcos y ballestas): lo que sueltan vuela un 15% más rápido.
- **Muralla** (escudos): lo que paras vuelve al que golpeó, y a ti no te mueve nadie.
- **Aeronauta** (alas): la reserva de vuelo se llena tres veces más rápido.
- Con ellos, arcos, escudos y alas pasan a tener **dos dones donde elegir** a maestría 10. Ya son 12.
- El capítulo de maestría ya no lleva la lista de dones escrita a mano (se había quedado en 6 de 12):
  la saca del código, con el sello de cada uno al lado.

### Obra maestra

- Una pieza que acaba teniéndolo todo —**nacida de un golpe perfecto, llevada a maestría 10, con su don
  grabado y firmada por ti**— se convierte en **obra maestra**: la estrella lo anuncia, sube otro **3%**
  en todo y lleva su propio marco de tooltip (oro blanco). No se fabrica, se reconoce.
- Trae logro propio y está explicada en el capítulo de maestría del libro.

### Y las mejoras nuevas, también

- El test comprueba ahora en el mundo: **Resaca** (el golpe mueve al enemigo 0.52 hacia ti), **Témpano**
  (quien te golpea acaba congelado), **Corriente** (dentro del agua te lanza con fuerza 1.08) y
  **Canalización** (bajo tormenta y a cielo abierto cae el rayo sobre lo que golpeas).
- **Alma de forja**: de 120 piezas forjadas seguidas, 7 nacieron con una mejora que nadie puso.
- **Firma del maestro**: afinidad 1.02 -> 1.04 sobre tu propia obra.
- **Cantera**: romper un muro de 3x3 gasta 9 de durabilidad sin la sinergia y 1 con ella.

### Las técnicas, probadas de verdad

- El test comprueba ahora **el efecto** de cada técnica en la estrella, no solo que se puedan elegir:
  ahorro de metal (224 -> 199 de daño con el mismo lingote), mano de orfebre (10% -> 15%), herencia
  limpia (50% -> 80%), fuelle largo (sobre fogata funde acero), ojo para el metal (2 -> 3 lingotes) y
  segunda templada (la pieza sale al rojo y sin temple).
- **Arreglo**: *Segunda templada* no funcionaba en absoluto. La mesa salía de `updateForge()` antes de
  tiempo cuando no había nada en las puntas, que es justo el caso de recalentar una pieza sola en el
  centro. Ahora solo sale pronto si el centro también está vacío.

### Huevos y orden en el libro

- **Tres huevos de aparición** (Herrero Caído, autómata de forja y coraza vacía), con su dibujo propio,
  en la pestaña creativa: para mirar a los bichos sin buscar una ruina.
- El capítulo de mejoras tenía las de flechas, alas y monturas metidas en "herramientas y armas" porque
  no había sección para ellas; ahora tienen la suya.

### Ajustes en un archivo

- `config/forja.json`, escrito solo la primera vez, con las cuatro probabilidades que deciden cuánto te
  molesta el mundo: saqueadores, élites, eventos y corazas. Sin dependencias.
- El mundo y el libro leen ese archivo, no las constantes, así que cambiarlo cambia las dos cosas.

### Las corazas salen de las ruinas

- De noche, con una mesa de forja a 12 bloques, hay un **5% por minuto** de que una coraza vacía se
  levante en la oscuridad a 18 bloques y venga a por ti. Solo una viva cada vez.
- El cincel conserva al cortar las propiedades que los dos bloques comparten (el eje de un pilar, el
  agua de un bloque inundado).

### Cincel (herramienta nueva)

- **Cincel** (punta de cincel + mango): no sirve para picar (mina despacio y pega menos que un palo), sirve
  para la piedra ya colocada. Clic derecho sobre un bloque de una familia que conozca y pasa a la
  siguiente cara; agachado va al revés. **52 bloques en 13 familias**: piedra, adoquín, pizarra, arenisca
  normal y roja, cuarzo, piedra negra, prismarina, purpur, ladrillo del Nether, piedra del End, toba y
  basalto.
- Cuesta un punto de durabilidad por corte y no consume nada más.

### Coraza vacía (enemigo nuevo)

- Una **armadura vacía** que se levanta sola en las forjas viejas: modelo y animaciones propias de
  GeckoLib, con una luz fría en la ranura del yelmo y por la costura del peto.
- Lo que la hace distinta: **el acero comprado apenas la toca**. Un golpe que no venga de algo forjado
  (o de una flecha forjada) le hace solo el 35%. Probado: espada de hierro 2.3, espada forjada 8.0.
- 45 de vida, 10 de armadura, inmune al fuego, al veneno y al wither. Al caer se deshace en las placas
  de las que estaba hecha y a veces deja un orbe de mejora.
- Hay una en cada forja abandonada y dos más en la fragua caída del Nether.

### Comodidad

- `/forja herrero <nivel>` pone tu maestría de herrero donde quieras, y `/forja tecnica <nombre>` da una
  técnica suelta (o `ninguna` para olvidarlas todas y volver a elegir).
- Los tooltips avisan de las **sinergias a medias**: "Cerca de Filón (30% de 50%)".
- El armero vende la plantilla de **punta de tridente** a nivel 4.

### Dos golpes especiales más

- **Siega** (guadaña, agachado + clic derecho): barre seis bloques alrededor y **arrastra hacia ti** todo
  lo que coge, con poco daño propio. Sirve para juntar a un grupo donde el siguiente golpe los alcance.
- **Embestida** (guanteletes, agachado + clic derecho): te lanzas de cabeza; lo primero que encuentras se
  lleva **una vez y media** el puñetazo, sale por los aires y queda ralentizado.
- Ya son cinco armas con golpe propio: espadón, martillo, mazo, guadaña y guanteletes (más el escudo).

### Derretir piezas

- **Fundir piezas** en la estrella: piezas sueltas del mismo material, sobre una mesa puesta encima de
  lava, vuelven a ser material. Cada pieza devuelve **la mitad de lo que costó** cortarla, redondeando
  hacia abajo, así que es la salida para un cajón de piezas equivocadas, no una forma de hacer material.
- Funciona con cualquier material, aleaciones incluidas (resuelve el lingote por la etiqueta del
  material), y trae su propio logro: **Vuelta al lingote**.

### Tu taller, cuadros y logros

- **Página "Tu taller"** en el libro: tu maestría con su barra, qué técnica elegiste en cada nivel, y la
  cuenta de lo que llevas hecho (piezas nacidas en la estrella, cuántas perfectas y con qué porcentaje,
  y mejoras trabajadas). Los tres contadores son enganches propios, guardados y sincronizados.
- **Tres cuadros**: La fragua (2x2), La estrella (2x1) y El martillo (1x1), dibujados pixel a pixel por
  el generador y metidos en la etiqueta de colocables, así que salen también en cuadros al azar.
- **Logro nuevo** "Devolver el favor" por saquear el cofre de un campamento, y el **arsenal** ahora pide
  también el tridente.

### Tridente y Jade

- **Tridente modular** (punta de tridente + mango + atadura): llega medio bloque más lejos que una
  espada, pega 7.7 de daño por segundo en hierro y se lanza entero agachándote, como el hacha. La punta
  cuesta cuatro de material, más que ninguna otra cabeza.
- Dos mejoras que son solo suyas: **Corriente** (dentro del agua o bajo la lluvia, usarlo te lanza) y
  **Canalización** (bajo tormenta y a cielo abierto, el golpe llama al rayo).
- **Jade** (opcional): mirar una mesa de forja dice el calor que tiene debajo, y mirar al Herrero Caído
  dice en qué fase de las tres está.

### Mundo, accesorios y libro

- Dos eventos nuevos, **Ventisca** y **Marea viva**, con dos mejoras que solo salen de ellos: **Témpano**
  (congela a quien te golpea de cerca) y **Resaca** (el golpe arrastra al enemigo hacia ti). Ya son ocho
  eventos, cada uno con su mejora, y el test lo comprueba.
- **Yunque portátil**: abre la mesa de piezas donde estés (128 usos, uno por apertura). No forja ni
  cuenta como taller.
- **Libro**: capítulo nuevo **Primeros pasos**, el ciclo entero en seis pasos con las imágenes de cada
  cosa, y va el primero del índice. Cada evento y cada amenaza llevan ahora su fila de iconos.

### Sinergias y arreglos

- Tres sinergias nuevas: **Cantera** (Excavación + Eficiencia: el área no gasta la herramienta),
  **Segundo aliento** (Vitalidad + Regeneración: por debajo del 30% de vida, dos corazones de absorción
  y un momento de regeneración) y **Vigía** (Sónar + Visión nocturna: el casco ve más lejos y lo que
  marca brilla el doble). Ya son 22.
- **Arreglo gordo**: el cliente nunca recibía la maestría del herrero, porque el enganche de datos se
  registraba tarde. Eso quería decir que la ventana del martillo nunca se ensanchaba de verdad en
  pantalla. Ahora se registra al arrancar el mod, y el test lo comprueba desde el lado del cliente.

### Técnicas del herrero

- **Nueve técnicas** en tres niveles (maestría 3, 6 y 9): eliges una por nivel y las otras dos se cierran.
- **Pestaña nueva** en la mesa de forja para verlas y elegirlas, con aviso cuando tienes una pendiente,
  descripción de cada una en el tooltip y un capítulo propio en el libro.
- **Segunda templada** trae una acción nueva a la estrella: una pieza sola sobre fuego caliente vuelve a
  ponerse al rojo y admite otro temple, algo que hasta ahora era para siempre.
- **Alma de forja** puede dar una mejora al 25% a una pieza recién nacida, y lo avisa por chat.
- Logro nuevo: **Escuela propia**, por aprender la primera técnica.

### Campamento saqueador

- **Estructura nueva**: el campamento donde viven los saqueadores entre asalto y asalto, con empalizada de
  abetos, dos tiendas, hoguera, torre con campana, cofre y barril de botín, y la banda de cuatro dentro.
- A la banda del campamento **se le da su hierro forjado cuando carga el chunk**, y al capitán su leyenda a
  maestría 10: matarlo suelta la leyenda y los orbes, igual que al de las bandas que salen de noche.
- **Yunque del Herrero Caído**: lo suelta el jefe, alumbra y, puesto junto a la mesa de forja, vale por la
  mesa de piezas y la de talabartería a la vez.
- Los **mapas del Forjador** apuntan cada uno a su estructura (antes los tres compartían destino, así que un
  mapa del taller podía llevarte a una forja), y hay un mapa nuevo para el campamento.
- El generador comprueba los tags de estructura y de destino, para que un tag sin escribir no vuelva a
  reventar la carga del mundo.

### Autómatas, talleres y remates

- **Autómata de forja**: enemigo nuevo con modelo y animaciones de GeckoLib, guardián de las forjas, que
  suelta las piezas de las que está hecho.
- **Taller de montaña**: estructura nueva con la mesa de talabartería, su cofre y un caballo, más un
  mapa que la vende el Forjador.
- **Plugin de JEI** opcional con tres categorías propias (piezas, mejoras y aleaciones).
- **Marcos de tooltip** para leyendas, maestría 10 y corazón de forja; **barra de frenesí** propia en el
  HUD en vez de una barra de jefe; **indicador de calor** en la mesa; humo y brasas en la fragua apagada
  y en el autómata; brillo emisivo en el jefe y el autómata.
- **Taller completo** (las tres mesas juntas) y **mestizaje** (rasgos distintos en un mismo objeto).
- El **corazón de forja** repara por completo cualquier pieza en la estrella.
- **Bestiario** en el libro y comparativa de daño por segundo de todas las armas.
- Balance: los élites solo aparecen a partir del día 5 y lejos del spawn, el mangual pasa el 50% del
  golpe por los escudos (antes 70%) y el jefe tiene 14 de armadura (antes 18).

### Jefe, mundo y dependencias

- **GeckoLib** pasa a ser dependencia (y **JEI** opcional): el **Herrero Caído** tiene modelo y
  animaciones propias (idle, andar, martillazo, rugido), 320 de vida, barra de jefe y tres fases
  (aprendices élite, reforja inmune con brasas que apagar, y lluvia de meteoritos). Vive en una
  **fragua-fortaleza del Nether** nueva y se despierta con una ofrenda en su **fragua apagada**. Suelta
  el **corazón de forja**: 2400 de durabilidad, +4,5 de daño y rasgo Llanto.
- **Saqueadores de forja**: bandas nocturnas con equipo forjado y un capitán con leyenda que suelta orbes.
- **Élites**: 1% de monstruos con leyenda, x5 vida, brillo y una sola pieza de botín; a partir del día 5.
- **Seis eventos del cielo** con mejoras exclusivas, **frasco de esencia** para guardarlas y **hierro
  estelar** (rasgo Estelar) en los cráteres.
- **Encargos del Forjador**: pieza exacta + mejora al 50%, rotan cada día, pagan de verdad.

### Taller

- **Ocho aleaciones** fundidas en la estrella según el calor bajo la mesa (fría, templada, caliente,
  fundida): bronce, latón, peltre, acero, electro, damasco, acero estelar y obsidiacero.
- **Temple**, **forja perfecta** (minijuego de martillo), **maestría de herrero** (10 niveles),
  **herencia**, **firma y afinidad**, **historia del objeto** y **pátina del cobre**.
- **Mesa de talabartería** con **barda** y **armadura de lobo** modulares.

### Combate y objetos

- Parada rehecha (el escudo cubre desde el primer tick) con **contraataque** y flechas devueltas.
- **Frenesí**: barra de combo que empuja las mejoras por encima de su número.
- **Sangrado** como efecto propio, con marchitamiento al llenarse.
- **Mangual**, **guanteletes**, **gancho**, **flechas modulares** y **pactos**.
- **Talismanes** (5) y **cinturón de herramientas**.
- Tres dones nuevos (Cargador, Jinete, Duelista) y cinco sinergias más.
- **Alas** con barra de vuelo, picado, remonte y Propulsión.

### Libro

- Nueve capítulos nuevos (aleaciones con gráfico, temple, maestría de herrero, combate, pactos,
  talismanes, eventos, encargos y amenazas) y tres elementos visuales nuevos: barras comparativas,
  divisores y muestras de color de material.

### Alas

- **Alas modulares** (membrana + forro): se equipan en el pecho y planean como un elytra, con la textura
  teñida del material. La membrana decide el vuelo (menos gravedad y menos roce cuanto más ligero el
  material) y la durabilidad; ganan maestría volando y admiten el don Viajero.
- **Aerodinámica** (mejora de alas, se alimenta con elytras): convierte tu bono de velocidad de
  movimiento en empuje mientras planeas.

### Materiales con rasgo (ahora 20 materiales)

- **Fragmento de eco – Resonante:** las herramientas iluminan las menas iguales a 6 bloques a través de
  las paredes; armas +1 Brecha; arcos y ballestas +1 Perforación; la armadura ignora Oscuridad y Ceguera.
- **Ladrillo de resina – Pegajoso:** todo +1 Irrompible; armas, herramientas y flechas ralentizan; la
  armadura ralentiza a quien te golpea.
- **Escama de armadillo – Acorazado:** la armadura suma Protección contra proyectiles y explosiones; en
  la mano da resistencia al empuje.

### Sistemas

- **Objetos rotos:** lo forjado ya no desaparece al gastarse; queda roto (con grietas en el ícono) y sin
  daño, protección, minado ni mejoras hasta repararlo.
- **Libros encantados en la estrella:** cada encantamiento que corresponde a una mejora la sube al
  porcentaje de su nivel.
- **Bonos de conjunto por material:** además de +2 armadura y +2 dureza, cada material da su propio bono
  (hierro +2 corazones, madera +10% de velocidad, eco +30% agachado, resina ignora el frenado del
  terreno, escama +3 de armadura...).
- **Diferencias de estadísticas:** al cambiar piezas y en el tooltip del inventario se ve cuánto sube o
  baja cada estadística respecto a lo que llevas.
- **Piglins:** la armadura forjada con placas de oro los calma, como la de oro de Minecraft.
- **Dones de maestría:** al nivel 10 grabas un don con un **sello** en la estrella (Filo eterno, Cazador,
  Minero, Baluarte, Viajero o Pescador). Cada sello es un objeto nuevo, se fabrica con oro y dos
  materiales propios del don, y lleva su color.
- **Leyendas:** equipo con nombre propio y dos mejoras al 100% en el botín de ruinas, bastiones y
  ciudades antiguas o del End.
- **Dagas arrojadizas:** la daga acepta Lanzacabezas; la hoja sale volando y vuelve sola.
- **Sinergias (12):** parejas de mejoras al 50%+ con nombre propio: Tormenta helada, Sed de sangre,
  Filón, Muro, Segador, Cazarrecompensas, Fortaleza, Cadena de rayos, Vendaval, Banco de peces, Meteoro
  (el mazo te hace caer en picado) y Justa (la lanza atraviesa), con su capítulo en la guía y una línea
  en el tooltip.
- **Telequinesis** ahora también recoge lo que cosechas con clic derecho, no solo lo que minas.
- **Vendaval** levanta en vertical de verdad: el impulso se aplica al final del tic, después del empujón
  del propio golpe de caída, y borra el desplazamiento lateral.
- **Golpes especiales:** Torbellino del espadón y Sismo del martillo y el mazo (agachado + clic derecho),
  con enfriamiento y coste de durabilidad.
- **Parada perfecta:** cubrir justo cuando llega el golpe devuelve el daño, empuja al atacante y lo deja
  lento y débil; el escudo gana maestría extra.
- **Golpe de escudo:** agachado y con clic derecho, el escudo forjado empuja y daña lo que tengas
  delante (3 s de enfriamiento).
- **Reciclar piezas:** una pieza suelta en Desarmar devuelve la mitad del material.
- **Maestría 10** deja el nombre en color épico.
- La piedra de afilar ya no acepta objetos forjados (borraba las mejoras y daba experiencia gratis).

### Mundo

- **Forja abandonada:** ruina que se genera en biomas de aldea y bosques, con Mesa de forja, Mesa de
  piezas, yunque, cofre propio y el **Herrero ermitaño** (un Forjador de nivel 2). Tiene tres variantes
  (en pie, medio derrumbada y derrumbada).
- **Forja de aldea:** algunas aldeas traen una herrería con las dos mesas, yunque, alto horno, chimenea
  humeante, puerta y cama (piedra y roble, o arenisca y acacia en el desierto); su aldeano suele
  volverse Forjador.
- **Forjador:** profesión de aldeano nueva; un aldeano sin oficio que toma una Mesa de piezas vende
  plantillas, kits, mangos, orbes y el mapa de la forja abandonada.
- **Intercambios de herreros:** plantillas base y grabadas, orbes de Eficiencia, Filo o Protección, y un
  mapa hacia la forja abandonada (herrero de herramientas maestro).
- **Monstruos:** zombis, esqueletos y vindicadores pueden aparecer con equipo forjado.
- **Más botín:** pesca de tesoro, cámaras de desafío, ciudades antiguas y del End, y trueque con piglins.
- **Adornos:** la armadura forjada acepta los adornos de la mesa de herrería.

### Interfaz

- La Mesa de forja muestra una barra de progreso de maestría junto al nombre del objeto.
- Las estadísticas del panel y del tooltip muestran la diferencia contra lo que llevas equipado.

### Guía y logros

- Capítulo **Mundo** en la guía (orbes, objetos rotos, botín, monstruos, aldeanos y adornos) con la
  receta del kit dibujada.
- 25 logros: se añadieron romper un objeto, sacar un orbe, fusionar orbes, vestir un conjunto completo,
  ecolocalizar menas, abrir el cofre de una forja abandonada, comerciar con un herrero, la parada
  perfecta, grabar un don y encontrar una leyenda.
- La pestaña creativa se dividió en **Forja** (mesas, guía, plantillas, kits, orbes y equipo) y
  **Forja: piezas** (cada pieza en cada material).
- Los generadores avisan si algo no cuadra: `generate_lang.py` comprueba que exista toda clave de idioma
  que usa el código y `generate_assets.py` que sus tablas coincidan con los enums de Java.
- `/forja orbe <mejora> <1-100>` para pruebas; el kit de `/forja kit` trae ballesta, guadaña, orbes,
  kits de reparación y los materiales nuevos.

## 2026-09-18 (mañana)

### Modelos de los mobs rehechos

- **Coraza vacía — ahora se lee como una armadura poseída.** Ninguna pieza toca a la otra: hay hueco en
  el cuello, en la cintura, en los codos y en los tobillos, y por cada hueco **se ve la luz que la
  sostiene**. El casco flota sobre el cuello en su propio hueso y va a su ritmo; las caderas son otra
  pieza suelta, así que el peto sube y baja sin ellas. La **espada no la empuña nadie**: cuelga en el
  aire al lado del guantelete vacío y gira sola. Runa encendida en el peto y núcleo de alma en el
  cuello. Caja de golpe 0.8x2.0 -> 0.85x2.6.
- **Autómata de forja:** se queda como estaba (te gustó) pero ya se mueve **lento de verdad**:
  velocidad 0.2 -> 0.16 y animaciones alargadas (reposo 7 s, andar 2.6 s, golpe 1.6 s).
- **Herrero caído — "El Torcido".** De tres diseños se eligió el partido por la mitad: el lado derecho
  sigue siendo un herrero (placa, hombrera, **capa de malla** echada por encima) y el izquierdo es el
  armazón pelado que el fuego se comió — varillas en vez de brazo, garra y gancho en vez de mano, y la
  pierna izquierda reducida a puntal y bota. El casco lleva la **visera colgando abierta** de una
  bisagra rota, se apoya en el martillo como un viejo en un bastón y le sale una **chimenea pequeña**
  por el hombro roto. Caja 1.7x3.7 -> 2.2x4.9. Movimiento a base de fotogramas escalonados (se queda
  quieto y salta a la pose siguiente) en reposo, andar, golpe y rugido; la capa va siempre un tiempo por
  detrás del cuerpo.

### Pintado de texturas

- Los huesos del modelo aceptan una **rotación de reposo**, así que un mob puede estar torcido sin
  gastar una animación en ello.
- Material **`soot`** (placa ennegrecida) para separar por valor la mitad de abajo de un mob de la de
  arriba: una criatura pintada toda del mismo gris se lee como una sola mancha.
- Las vetas de óxido se quedaron en **manchas cortas** en vez de rayas largas, que se leían como
  cebra en vez de como óxido.
- **Desgaste de verdad:** grietas que bajan serpenteando por la placa, bordes superiores mordidos,
  dobladillos de tela deshilachados y **agujeros que atraviesan la placa**. Los agujeros son píxeles
  transparentes, y como el modelo se dibuja sin descartar caras traseras, por ellos se ve el interior
  hueco de la pieza.

### El alma de la coraza

- Romper una **coraza vacía** ya no mata lo que llevaba dentro: si hay otra en pie a menos de **12
  bloques**, el alma salta a esa (se ve el salto en partículas), que se levanta con **8 de vida de
  vuelta y 10 segundos de prisa y fuerza**, y hereda el objetivo de la que cayó. Sin ninguna cerca,
  simplemente se apaga donde estaba. Las salas con varias hay que despejarlas de golpe.
- Apuntado en el bestiario de la guía.

### La fragua del herrero

- **Se ve el fuego por las roturas.** Detrás de cada placa agujereada hay brasas encendidas, así que por
  los agujeros del pecho, la cadera, el muslo y el hombro roto se ve arder lo que tiene dentro. **Y esas
  brasas cambian con la fragua**: cada una es un par de huesos (naranja y morado) y las animaciones de
  fuego apagan uno de los dos, así que cuando el horno se pone morado las grietas también. Además
  laten, tanto en reposo como en el fogonazo del golpe básico.
- **Arde de verdad:** el pintado de lo emisivo pasó de un tono plano a un degradado de fuego — blanco al
  rojo vivo abajo, naranja subiendo y rojo oscuro en los bordes, con parpadeo. Y la boca de la fragua es
  ahora más grande (10x17 en vez de 12x15) y está de verdad hundida detrás de los barrotes.
- **El fuego se anima.** Los huesos aceptan escala, así que la lumbre respira sola: crece, mengua y sube
  un poco, en su **propio controlador de animación**, de modo que sigue ardiendo mientras él golpea,
  ruge o se refunde sin pelearse por ningún hueso.
- **Cambia con el ataque:**
  - *Golpe básico* — animación nueva `strike`, corta y medida (medio giro del cuerpo y el martillo), y
    la fragua da un **fogonazo naranja**.
  - *Golpes fuertes* — al llamar a los aprendices, al refundirse y con la lluvia de estrellas la fragua
    se pone **morada**: el naranja se apaga, una placa violeta cubre toda la boca y **salen lenguas de
    fuego por la abertura y por la chimenea**, con partículas violetas por la boca y por el tiro.
  - En su **último cuarto de vida** se queda morada del todo.
- El cambio de naranja a morado va por dato sincronizado, no por adivinar en el cliente, y suena un
  soplido de fuelle al encenderse y otro al apagarse.

## 2026-09-18 (mediodía)

### Dos ataques nuevos para cada enemigo

**Herrero caído**
- **Onda de yunque**: deja caer el martillo y un anillo de fuego corre por el suelo hasta 9 bloques,
  8 de daño, te levanta y te prende. Se salta. Cada 8 s, cuando estás a media distancia.
- **Garfio**: desde la segunda fase tira la garra del brazo muerto, te hace 4 de daño y **te arrastra
  hasta el martillo**. Cada 11 s, entre 5 y 16 bloques.

**Autómata de forja**
- **Brasa**: si te quedas lejos abre la barriga y escupe fuego. Se toma 14 ticks en cargar, a propósito:
  es su única respuesta a que le dispares desde fuera de su alcance, que hasta ahora era toda la pelea.
- **Vapor**: pegarse a él le hace soltar la caldera — 4 de daño y lentitud II a todo lo que tenga al
  lado, y él aguanta mejor 3 s. Cada 10 s. Ni de lejos ni encima.

**Coraza vacía**
- **Embestida**: se lanza por el hueco (4-11 bloques) y corta 7 al atravesarte. No dirige a mitad de
  salto: el alma va primero y la placa la sigue.
- **Lamento**: se abre y llama. Quien lo oye a 7 bloques va lento y con el arma pesada 6 s, y **todas
  las demás corazas a 14 bloques se levantan con 4 de vida y tu objetivo**.

**Élites**
- **Embate de leyenda**: si te alejas, salta los 4-11 bloques y cae encima, 6 de daño en 2.6.
- **Segundo aliento**: una sola vez, al bajar de un cuarto de vida, se cura el 30 % y vuelve con fuerza,
  resistencia y prisa.

**Capitán saqueador**
- **Cerrar filas**: toca el cuerno y toda la banda a 16 bloques va más rápido, pega más fuerte 8 s y
  se gira hacia lo que él esté peleando. Matarlo a él primero pasó de preferencia a respuesta correcta.
- **Carga**: cierra 5-12 bloques de un salto.

Los élites y el capitán son monstruos de vanilla vestidos, así que sus movimientos son **goals** que se
les añaden al equiparlos (`entity/ai/LeapStrikeGoal`, `SecondWindGoal`, `RallyGoal`), con un accessor
mixin nuevo porque `goalSelector` es protegido.

### Y tres respuestas, porque diez ataques sin contrajuego es solo dificultad

- **Anclaje** (botas, barrotes de hierro / yunque): hasta **60 % menos de empuje**. Cuenta contra el
  empuje normal por resistencia al empuje, y contra los tirones y embestidas del mod, que te mueven
  directamente y se saltarían la resistencia.
- **Aislante** (armadura, arcilla): te quitas hasta **1.5 s de fuego encima por segundo**.
- **Temple** (armadura, polvo de blaze): los efectos malos (lentitud, fatiga, debilidad, ceguera,
  náusea, oscuridad, mala suerte) se te pasan hasta un **50 % antes**. Es la respuesta al lamento.

### Sinergia nueva

- **Firme** (Anclaje + Temple en las botas): aguantan mucho más —hasta un 85 % menos de empuje— y **el
  lamento de la coraza no te toca**. Lo oyes, simplemente no lo contestas.

### Quinto mob: la Pavesa

Un trozo de fragua que se escapó — una jaulita de hierro con una brasa dentro — y **lo único que vuela
en el mod**. Todo lo demás que pone Forja en el mundo es lento y pesado y se contesta con los pies; esta
es lo contrario de las tres cosas: 12 de vida, rápida y te cae de arriba.

- **Picado**: desde 3-12 bloques se echa encima, 4 de daño y te prende 3 s. No corrige a mitad de caída.
- **Se aviva**: junto a fuego, fuego de almas, lava, magma, hoguera, horno encendido o una **mesa de
  forja**, crece, pega 3 más, quema el doble y pica el doble de seguido durante 10 s. Y si la matas
  avivada, revienta y prende lo que tenga a 2 bloques. La respuesta es pelearla lejos de la lumbre.
- Suelta carbón, polvo de blaze (35 %) y un orbe de mejora al 25 % (12 %).
- Hay una sobre el hogar de cada **forja abandonada** y **tres sobre la lava de la fragua caída**.
- Modelo GeckoLib nuevo: jaula de hierro girando, la brasa dentro latiendo y dos hojas de fuego que bate
  como alas; casi todo el modelo es capa emisiva. Cuatro animaciones (reposo, vuelo, picado, avivada) y
  navegación de vuelo de verdad.
- **Vienen solas**: con lumbre cerca (mesa de forja, lava, hoguera o fuego) hay un 8 % por minuto de que
  lleguen **hasta tres de golpe**, de día o de noche, y una sola vez mientras siga habiendo alguna a 48
  bloques. Configurable en `config/forja.json` (`pavesas`).

### Farol de pavesa

Lo único del mod que se consigue **cogiendo algo vivo** en vez de matándolo.

- Con un **farol vacío** en la mano, clic derecho sobre una pavesa **avivada** y se mete dentro. Apagada
  no entra: "en el farol no aguantaría encendida". Y avivada es justo cuando más pega y más pica, así
  que hay que sacar el farol en el peor momento de la pelea, no al final.
- Puesto **bajo una mesa de forja cuenta como lava** (calor Fundida), así que quien sepa cazar una no
  vuelve a acarrear lava al taller. Además da luz 15.
- Logro **Fuego enjaulado** (desafío) por conseguirlo, y **Apágala** por matar una avivada.
- La pavesa cogida no suelta nada ni revienta: se la llevan, no se la mata.

### Noveno evento del cielo: Lluvia de pavesas

- Mientras dura, **vienen tres veces más a menudo y llegan ya avivadas**, haya lumbre cerca o no. Es el
  momento de cazarlas con el farol: avivadas de fábrica, sin tener que provocarlas junto al fuego.
- Su mejora exclusiva, la que se guarda con el **frasco de esencia** a cielo abierto, es **Rescoldo**
  (armadura, crema/bloque de magma): **el fuego te cura el 90 % de lo que te haría**. No hace que
  quemarse sea bueno, lo hace barato — que es justo lo que le pasa a quien trabaja metido en una fragua.

### Sonido y remate

- **Los cinco mobs tenían voz de nadie**: usaban el silencio por defecto de `Mob`, así que solo se les
  oía al golpear. Ahora cada uno suena a lo que es — el herrero gruñe como un ravager a medio tono, el
  autómata pisa como un gólem, la coraza susurra almas y la pavesa chisporrotea como un blaze.
- Sinergia **Salamandra** (Rescoldo + Protección contra fuego en la misma pieza): el fuego te devuelve
  **entero** lo que te quitaba y además **te apaga**.
- El **farol de pavesa** cuenta como lumbre para las que vienen solas: el taller que ya no acarrea lava
  es el taller que visitan.

### Grafo y cuentas

- Grafo de conocimiento reconstruido: **2351 nodos, 6972 aristas, 81 comunidades** (antes 2273 / 6599 / 88).
- El mod va por **110 mejoras, 26 sinergias, 5 mobs propios, 9 eventos y 27 logros**.

## 2026-09-18 (tarde)

### El crisol: fundir y alear a escala, con sistema propio

La mesa de forja alea sosteniendo los ingredientes sobre el calor que haya debajo — bien para un lingote,
inútil para cien. El **crisol** es la otra mitad, y es lo primero del mod pensado para montarse en fila.

- **Se automatiza con tolvas.** Entra por arriba, el combustible por el lado, sale por abajo: exactamente
  la convención del horno, así que una nave de crisoles es tolva y cofre y nada más que aprender.
- **Y tiene pantalla propia.** Clic derecho lo abre. En el centro hay una **pila de piedra con la colada
  dentro**: el nivel sube con lo lleno que esté el puchero, toma el color del metal —empujado hacia el
  naranja, porque el metal fundido no es del color de la barra— la superficie **ondula**, un **brillo la
  cruza** y le **saltan chispas** mientras hay fuego. A la derecha, el canal por donde corre la colada
  hacia la salida. Al lado del combustible, la **brasa bajando y parpadeando**. Y en texto: qué está
  haciendo ("Colando acero", "Fundiendo a hierro", "Damasco pide calor Fundida"), qué calor alcanza y
  cuánta cabida lleva usada.
- **Dos trabajos.** Si los dos huecos de entrada tienen los ingredientes de una aleación y el crisol da
  el calor, la cuela. Si no, funde lo que un herrero haya hecho y **devuelve el material**.
- **Tres niveles, y lo que puede hacer cada uno lo decide de qué está hecho:**

| | Barro (ladrillos + arcilla) | Hierro (sobre el de barro) | Obsidiana (+ chatarra de netherita) |
|---|---|---|---|
| Calor | Templada | Caliente | **Fundida** |
| Cabida | 8 | 16 | **32** |
| Por colada | 10 s | 6 s | **3 s** |
| Devuelve al fundir | 50 % | 75 % | **100 %** |
| Aleaciones | bronce, latón, peltre | + acero, electro | **todas, +1 lingote** |

  El de barro hará bronce toda la vida y no tocará el damasco; el de obsidiana lo funde todo, cuesta
  mucho más y encima regala un lingote por colada. La cabida es real: **una tolva tampoco puede meterle
  un stack a un puchero de barro**.
- Se enciende solo cuando tiene trabajo, da luz 13 mientras cuela y se le ve el metal por la boca y por
  la ranura del costado.

### Y el sistema del crisol no toca nada de vanilla

- **Combustible: `Ascua`**, objeto nuevo del mod. El crisol **no acepta carbón, ni carbón vegetal, ni un
  cubo de lava** — solo ascuas.
- **De dónde salen:** las sueltan las **pavesas** (2-4 si estaban avivadas) y **cada vez que el crisol
  funde una herramienta deja una ascua en el fondo del puchero**. Rompes algo, lo fundes, y lo que sacas
  paga la siguiente fundición: el bucle entero es del mod.
- **Un farol de pavesa debajo del crisol lo hace funcionar sin gastar nada.** Esa es la recompensa de
  cazar una viva en vez de matarla.
- **Recetas sin un solo objeto de vanilla:** el de barro son 8 **peltre**; el de hierro, 8 **bronce**
  sobre el de barro; el de obsidiana, 8 **obsidiacero** y un **corazón de forja** sobre el de hierro.
  Tienes que haber hecho el metal a mano en la mesa antes de poder producirlo en masa.

### Cuba de colada: el almacén de la fundición

Un bloque de cristal y bronce que guarda **metal fundido**. Dos que se tocan por una cara son **un solo
depósito**, y de ahí para arriba: un 3x3x3 son casi 7000 lingotes y se ve el nivel subir por el cristal.

- **Sin bloque maestro ni multiestructura que validar.** Cada cuba guarda lo suyo y la operación recorre
  el racimo: al llenar entra por **la cuba más baja con sitio**, al sacar sale por **la más alta con
  metal**. Por eso romper una del medio de una pared no corrompe nada — al tick siguiente simplemente son
  dos depósitos, y la que rompes suelta solo lo suyo.
- **Un depósito, un metal.** Es la regla que lo hace interesante: hacen falta bancos separados por metal,
  y un taller acaba pareciendo una fundición de verdad.
- **El crisol pegado a una cuba vuelca en ella** en vez de en su hueco de salida, así que una fila de
  crisoles llena una pared de cristal sin una sola tolva.
- **Y al revés: un crisol saca sus ingredientes de la cuba y cuela al doble de velocidad**, porque el
  metal ya está líquido. Ese es el motivo de construirlas — no son más densas que un cofre y nunca
  pretendieron serlo.
- **Sale por abajo:** empuja un montón por segundo al contenedor que tenga debajo (tolva, cofre, armario
  de piezas). A mano: clic derecho con metal mete, clic derecho vacío saca uno (agachado, un montón), y
  siempre te dice qué lleva y cuánto.
- El cristal se dibuja en cinco niveles y **las caras entre dos cubas no se pintan**, así que un banco se
  lee como un solo cuerpo de metal. Alumbra según lo lleno que esté.

### Conductos de colada, y la colada dibujada como lava

**Conducto de colada** (8 por 6 de bronce). Una tubería que **no guarda nada**: es una *conexión*, no un
contenedor. Un conducto con su propio buffer es un conducto que hay que guardar, sincronizar, equilibrar
y depurar, y lo único que una fundición quería de uno es dejar de tener que pegar el crisol al cristal.

- **El crisol busca sus cubas a través de los conductos** como si las tocara: vuelca en ellas y saca de
  ellas sus ingredientes (colando al doble) a hasta 64 bloques de tubería.
- **Un banco de cubas se vacía por el conducto** hacia cualquier contenedor que haya al final del tramo,
  además de hacia lo que tenga justo debajo.
- Se dibuja con núcleo y un brazo por cada lado conectado (blockstate multipart), y se engancha sola a
  conductos, cubas, crisoles y a cualquier cosa que guarde objetos.

**Y la colada ahora se dibuja a mano** (`client/MeltTankRenderer`), que es la única forma de que fluya,
se transparente y cambie de metal a metal:

- **Nivel continuo**, píxel a píxel, en vez de los cuatro escalones que puede dar un modelo.
- **Se mueve como lava**: la textura corre hacia arriba por los costados y de lado por la superficie, a
  velocidades distintas.
- **Color, opacidad y brillo propios de cada metal**, y los dos últimos salen del color en vez de de una
  tabla: un metal pálido se lee **fino y lustroso** (más transparente, reflejo más fuerte) y uno oscuro
  **espeso y mate**. Así el oro no se vierte como la obsidiana, y cualquier metal que se añada mañana
  tiene su aspecto propio sin tocar nada.
- Una **banda de luz cruza la superficie** cada pocos segundos, con la fuerza que le toque al metal.
- Se pinta con luz propia, así que un taller a oscuras se ilumina con lo que lleva dentro.

### La caja de moldeo: la línea termina aquí

**Acero refractario** (aleación nueva, calor Caliente: 2 acero + 2 arcilla → 2). Es la **única aleación
del mod que no es metal de equipo**: existe para cortarse en moldes, y hacerla material metería una
columna entera en la matriz de texturas de armadura para nada. La regla queda escrita en
`Alloys.SHAPING_ONLY` y la prueba la comprueba en vez de dar por hecho lo contrario.

**Caja de moldeo**: un bloque con dos trabajos.

- **Sacar un molde.** Metes una **pieza acabada** con acero refractario al lado: el acero se vuelca
  sobre ella, **la pieza se destruye** y sale su **molde**. Es lo único del mod que rompe a propósito
  algo que hiciste, y debe serlo — un molde vale una pieza porque a partir de ahí no vuelves a cortar
  esa pieza a mano. Cuesta 2 de acero refractario.
- **Colar.** Con el molde dentro, **cuela la pieza con el metal de las cubas que alcance** (pegadas o por
  conducto), gastando exactamente lo que cuesta la pieza. **El molde no se gasta.** Sin plantilla y sin
  mesa: mena → crisol → cubas → caja → piezas por abajo.
- Entra por arriba, el acero por el lado, sale por abajo: la misma convención que el crisol.

**Tres niveles, y el límite sale de la dureza del propio material** —así un metal que añada mañana cae
solo en el nivel que le toque:

| | Barro (peltre + arena) | Acero (acero refractario) | Damasco (+ damasco) |
|---|---|---|---|
| Aguanta | hasta 320 de dureza | hasta 700 | **cualquier metal** |
| Cuela | madera, piedra, cobre, hierro, bronce, latón, peltre, oro… | + amatista, prismarina, púrpur, acero, esmeralda | + diamante, netherita, obsidiana, damasco, acero estelar, eco… |
| Por pieza | 6 s | 4 s | **2 s** |

Con capítulo propio en la guía, y la pantalla te dice **antes de gastar la pieza** qué va a hacer y hasta
qué dureza aguanta la caja.

## 2026-09-18 (noche)

Todo medido en el mundo con `./gradlew runClientGameTest` (**ALL CHECKS PASSED**), no sólo compilado.

### El metal se enfría

La fundición ya no era un cofre con otro nombre, pero tampoco costaba nada tenerla llena. Ahora sí:

- Una cuba llena arranca a **200 de calor** y pierde 2 cada segundo: **cuaja sola en unos 100 segundos** y
  deja de servir para colar. Un **farol de pavesa, lava, magma, fuego o un crisol encendido** al lado la
  mantienen líquida.
- Si cuaja, **un crisol encendido al lado la vuelve a fundir** — pero se come el **15 %** de lo que había.
- Medido: *al llenar 200 de calor, sola cuaja true, sobre farol false, guarda 100 y tras refundir 85*.

### Conductos de tres calidades

Y como el metal se enfría por el camino, de qué esté hecha la tubería importa:

| | Bronce | Acero | Damasco |
|---|---|---|---|
| Pierde por tramo | 6 de calor | 3 | **1** |
| Se consigue | receta | **colado en la caja** | **colado en la caja** |

Medido: *el bronce cuesta 12 de calor, el damasco 2* en el mismo tendido.

### Coladores: lo único que puede salir mal

- El metal **se cuela al caer** en la caja de moldeo. Uno de **barro** aguanta hasta **320** de dureza.
- **No se fabrica uno mejor: se infusiona.** Pones el que tienes en la ranura de arriba, le dejas caer
  encima **8** de un metal más duro y **sale hecho de ese metal**.
- Si **no aguanta**, **revienta en la colada** y la pieza sale **basta: −15 % en todo**, y se arrastra al
  objeto acabado aunque el resto de piezas sean buenas. Sin colador, igual de basta.
- Medido: *el de barro revienta con el diamante true y desaparece true, el bueno cuela limpio true y
  sobrevive true, infusionado aguanta 1561* · *pieza basta: durabilidad limpia 360, basta 306*.

Para que la penalización existiera de verdad hubo que arreglar `ForgeStats.raise()`, que devolvía
temprano con cualquier valor negativo: ahora es `scale()` y va en los dos sentidos, con suelo en −75 %.

### Forja blanca: un calor que ninguna mesa alcanza

Calor nuevo por encima de Fundida. **`heatUnder` no lo devuelve nunca**, pongas lo que pongas debajo, y
`hotter()` —lo que usa *Fuelle largo*— **se para en Fundida a propósito**. Lo único del mod que quema así
es el **crisol de obsidiana**, y ese es el motivo de construirlo.

Medido: *lo más caliente bajo una mesa es FUNDIDA, con fuelle FUNDIDA* · *solacero: el crisol de hierro
saca 0, el de obsidiana 2*.

### Siete aleaciones nuevas (cuatro normales, tres exclusivas)

| Aleación | Calor | Receta | Rasgo |
|---|---|---|---|
| **Cinerio** | Caliente | 2 acero + 4 ascuas | **Ascua**: ardiendo, +2,5 de daño y repara 3 cada 5 s |
| **Voltaico** | Caliente | 2 latón + 4 redstone + 1 amatista | **Cargado**: al cuarto golpe salta a 2 enemigos |
| **Almacero** | Fundida | 1 acero estelar + 2 placa hueca | **Animado**: para un golpe entero cada 30 s |
| **Vidriacero** | Fundida | 1 obsidiacero + 4 cuarzo | **Diáfano**: +3 % de velocidad por pieza, +0,3 de ataque |
| **Solacero** | **Forja blanca** | 1 damasco + 3 varas de blaze | **Solar**: a pleno sol +3, y +6 a los no-muertos |
| **Lunacero** | **Forja blanca** | 1 obsidiacero + 3 fragmentos de eco | **Nocturno**: a oscuras +3,5 de daño |
| **Acero vivo** | **Forja blanca** | 1 corazón de forja + 2 damasco | **Vivo**: cada muerte le repara 25, y te cura |

Las tres de forja blanca tienen **dos ingredientes a propósito**: el crisol tiene dos huecos, así que una
tercera cosa habría sido una receta que nada puede colar. Cada una trae **conjunto de armadura propio**, y
dos de ellos cambian código y no sólo atributos: el de almacero baja la parada a 20 s y el de acero vivo
dobla lo que saca de cada muerte.

Los siete rasgos se comprueban por **lo que hacen**, no por estar en el enum:

*ascua: frío 0.0, ardiendo 2.5 · cargado: salta a 1 y la carga vuelve a 0 · animado: para el primero
true, el segundo false · diáfano: pechera de vidriacero 0.03 de velocidad frente a 0.0 · solar: mediodía
6.0, medianoche 0.0 · nocturno: mediodía 0.0, medianoche 3.5 · vivo: repara 25 y cura 1.0*

### Capítulo de guía: «Montar una fundición»

Ocho bloques que sólo tienen sentido juntos, y el libro no decía por dónde empezar. Ahora hay un capítulo
que **se lee en el orden en que se construye**: crisol → cubas → conductos → calor → caja → coladores →
para qué sirve todo esto. **Todos los números salen del código** (cabidas, segundos por colada, pérdida
por tramo, dureza que aguanta cada colador, penalización de una pieza basta), así que no pueden
desviarse de lo que hacen los bloques.

En JEI, las recetas de forja blanca ya no mienten con «Mesa forja blanca»: dicen **«Sólo en el crisol de
obsidiana»**.

## 2026-09-18 (noche, 2)

Tres mesas de piedra oscura, de los tres diseños que Andy eligió. Probado en el mundo con
`./gradlew runClientGameTest` (**ALL CHECKS PASSED**).

### El marco: la herramienta entera, no la pieza

La caja de moldeo tenía escrito en el código que **no** se podía sacar el molde de una herramienta
acabada («una espada son tres formas a la vez»). Ahora se puede, y sale otra cosa: un **marco**.

- **Herramienta acabada + 6 de acero refractario** en la caja → su **marco**. Cuesta la herramienta, como
  el molde cuesta la pieza, y **no se gasta al colar**.
- La caja no puede llenarlo. Para eso están las mesas.
- Medido: *la caja saca el del pico true, se come la herramienta true, acero restante 0, cuesta 5 de metal
  por colada*.

### Las tres mesas de colada

**Sin pantalla, a propósito.** Pones el marco encima con clic derecho y se ve tumbado en el lecho; las
cubas que alcance le dejan caer **exactamente el metal que vale esa herramienta**; a los 5 segundos
recoges la herramienta hecha, del metal que hubiera en la cuba. Entra por arriba, sale por abajo.

Todo eso se dibuja a mano en `client/CastingTableRenderer`: el **marco tumbado** (el propio modelo del
objeto), el **chorro cayendo** durante el primer tercio de la colada y el **metal subiendo en el lecho**,
con el color, la opacidad y el brillo del metal que sea —los mismos que usan las cubas—. El progreso
**no se sincroniza por paquete**: al cliente se le dice una vez en qué tick empezó la colada y él la
calcula con el reloj del mundo, así que una fila de veinte mesas no cuesta veinte paquetes por tick.

| | Losa (pizarra + hierro) | Brasa (piedra negra + oro) | Almas (basalto + obsidiana) |
|---|---|---|---|
| Pierde calor | 4/s | 2/s | **1/s** |
| Tras 25 s sin fuego | 104 | 154 | **177** (de 200) |
| **Perfecta** (+5%) | 10% | 25% | **50%** |

**Las tres cuelan igual.** Lo único que cambia es la piedra, y la piedra decide dos cosas: cuánto aguanta
el calor y cómo de firme sale la colada. Cada colada gasta 40 de calor, una mesa fría no empieza ninguna
y la que empieza con lo justo **sale basta** (-15%), reusando la penalización que ya tenían los coladores.

Medido: *sale un pico true todo de hierro true, gasta 5 de metal para un coste de 5, el marco se queda
true* · *calor tras 25 s sin fuego: losa 104, brasa 154, almas 177* · *mesa fría: no toca la cuba true y
no saca nada true · con 25 de calor cuela true y sale basta true* · *perfecta: losa 10%, brasa 25%,
almas 50%*.

### De paso

- `MeltTankBlockEntity.reachableFrom` es ahora el **único** sitio donde se buscan cubas (pegadas y por
  conducto); la caja de moldeo tenía su propia copia y las mesas habrían sido la tercera.
- `ForgeType` tiene por fin `displayName()`, que hacía falta en tres sitios a la vez.
- Capítulo de guía **«Montar una fundición»** ampliado con el paso 7, y una foto nueva en las pruebas
  (`forja_33_mesas_de_colada`) con las tres mesas colando a la vez.

## 2026-09-18 (noche, 3)

### Los conductos dejan de ser tuberías

Andy: «quiero que se vea por arriba, no como tubería totalmente». Se le dieron tres formas abiertas y
cuatro variantes de la que eligió; salió la **B1**, y es la que está puesta.

Un conducto ya no es un tubo de 6×6 flotando en el centro del bloque: es **suelo**. Una losa que se pisa
(la forma de colisión es ahora 0..6 en toda la huella), con el metal hundido **a ras** en un canal de 4
píxeles por el centro y un **bordillo del metal de su calidad** a cada lado. Alumbra a nivel 7, porque
lleva metal fundido abierto.

- **La piedra es la misma en las tres calidades** y sólo cambia el bordillo, así que un tramo que mezcle
  bronce y acero se lee como una sola línea y no como un remiendo.
- Un tramo que **sube** cae al tubo cerrado en ese bloque: un canal abierto no lleva nada pared arriba.
- Cuatro piezas en multipart —núcleo, brazo, tapa y subida— que **embaldosan la cara de arriba sin
  solaparse**: el núcleo se queda el centro y las cuatro esquinas, y cada lado es brazo o tapa según
  esté conectado o no.

Dos cosas que se vieron sólo al mirarlo en el juego y que no habrían salido de un render:

- **Las texturas se estiraban.** Cada cara usaba la hoja entera de 16×16, así que un brazo de 5 píxeles
  la comprimía y se veía una banda en cada junta. Ahora las UV salen de dónde está la caja, no de la
  hoja, y un tramo largo tiene la textura a escala del mundo.
- **El metal se ensanchaba una vez por bloque.** La charca del centro era de 6 píxeles y los brazos de
  4, así que la línea hacía barriga en mitad de cada bloque. Ahora mide 4 en todo el recorrido.

### Y las dos recetas que faltaban

Los conductos de acero y de damasco **no se podían conseguir jugando** desde que se añadieron las
calidades: sólo existía la receta del de bronce, y el comentario del código prometía un camino de colada
que nunca se llegó a cablear. Ahora siguen la escalera de las cajas de moldeo: **6 de acero + 3 conductos
de bronce → 3 de acero**, y **6 de damasco + 3 de acero → 3 de damasco**.

Foto nueva en las pruebas: `forja_34_fundicion` (la fundición entera montada: crisol, banco de cubas,
canales, caja de moldeo y dos mesas colando) y `forja_35_canal`, a ras de suelo.

## 2026-09-18 (noche, 5) — el rediseño

Todo probado con `./gradlew runClientGameTest` (todas las comprobaciones pasan).

### Los bloques, rehechos

Las tres mesas de colada eran lo único que Andy no quiso cambiar, así que lo que ellas hacen bien es lo
que hace ahora el resto del taller: **material con color propio**, **herraje arriba y abajo**, y una
**cara delantera con algo que se pueda nombrar**. Dos direcciones, una para cada mitad:

- **Bancos (piedra oscura y hierro)** — mesa de forja (yunque hundido en la superficie + cajón), **mesa
  de forja mayor** (piedra negra, labio de oro, dos yunques), mesa de piezas (lecho de corte con
  siluetas marcadas + rueda de afilar, en cobre), talabartería (cuero cosido + pomo de silla, en latón)
  y armario de piezas (tres cajones).
- **Lo que quema (piedra y brasa)** — los tres crisoles comparten un kit nuevo: el tope es un **cuenco
  hundido en anillos** y el costado lleva **boca de fuego con reja** (carbón muerto apagado, fuego vivo
  encendido). Barro = soga y grieta, hierro = chapa con costura y remaches, obsidiana = facetas con
  flejes de oro.
- **Cajas de moldeo** — ahora son un molde de arena de verdad: **copa de colada**, **bebedero**,
  **cavidad** de la pieza y respiradero arriba; **línea de partición**, mordazas de esquina y asas al
  costado. Colando, la copa-bebedero-cavidad brillan y se escapa una línea de luz por la junta.
- **Farol de pavesa** — jaula con barrotes y la pavesa flotando detrás, en vez de un muro de fuego con
  rayas encima. **Fragua apagada** — el mismo cuenco de los crisoles, lleno de ceniza y carbón muerto,
  con una brasa que no se apagó. **Cuba de colada** — bronce y vidrio, que es de lo que está hecha por
  receta, en vez del bloque de cuarzo que parecía.

### El yunque del herrero deja de ser un cubo

Es el trofeo de la pelea más dura del mod y era un cubo con la cara caliente, que es la única forma que
un yunque no tiene. Ahora es un **modelo de cuatro cajas** —pie, dos escalones de cintura y una cara
ancha— con collar de oro, agujero cuadrado y punzonera en la cara, y la grieta de alma del herrero en
el costado. **Gira** según cómo lo pongas (`SmithAnvilBlock`, con su propia forma de colisión).

### El kit de reparación se va; llega el lingote de temple

El kit reparaba un 15 % plano de lo que fuera, así que **premiaba menos cuanto mejor era tu material**:
un kit devolvía más vida útil a un pico de madera que a uno de corazón de forja, en proporción. Fuera.

En su lugar, el **lingote de temple**: una barra que aún no ha decidido qué metal es, **colada en el
crisol** (acero refractario + 2 de arcilla, calor CALIENTE, salen 2). Clic derecho sobre equipo forjado
en el inventario y lo repara **con el metal de su cabeza** — un cuarto de lo que vale ese material por
sí solo, con suelo de 25. Medido: *un lingote devuelve 390 a una cabeza de diamante y 25 a una de
madera*.

### La jarra de esencia

El frasco se convertía en orbe en el mismo clic, así que **nunca contenía nada**: nueve noches
distintas acababan siendo nueve orbes idénticos. La documentación del propio `WorldEvents` ya describía
el diseño de dos pasos que nunca se implementó, y ahora está: la **jarra** atrapa la noche y la
**guarda** (se llama "Jarra de aurora" y el cristal toma su color), y al vaciarla sale el orbe de
siempre. Un estante de jarras se lee como las noches en que estuviste fuera. Medido: *jarra: guarda
AURORA, color 0x9fe2bf, se llama Jarra de Aurora · al vaciarla sale: AURORA 100%*.

### Objetos que se parecían demasiado entre sí

Sello, talismán y orbe eran **la misma esfera gris dentro del mismo anillo de oro**. Ya no:

- **Colador** — era una losa de barro con nueve puntos. Ahora es **una reja de verdad**, hecha con
  geometría (marco + cuatro barras, nueve huecos), así que se ve en 3D en la mano y en el inventario. Y
  **se tiñe del metal que lo atravesó**, que es lo que el objeto dice que hace desde siempre y nunca se
  veía.
- **Sello** — **cera**: un goterón algo deforme con el emblema hundido (oscuro donde mordió el cuño,
  claro en el labio que levantó) y una **cinta al sesgo**. Al sesgo a propósito: con la cinta simétrica
  el conjunto parecía un bicho con patas.
- **Talismán** — la piedra ya no es una esfera con brillo, es una **talla de seis caras**: planos duros
  con un tono cada uno, que es lo contrario de una esfera y lo que de verdad parece una gema. Garras,
  asa y cordón en el engaste.
- **Yunque portátil** — de perfil, que es el único ángulo desde el que un yunque se reconoce a 16
  píxeles. De frente parecía un estante con una tabla encima.
- **Grieta** — rotura corta y centrada con **filo claro a un lado** (la cara fresca del metal partido).
  Corta a propósito: la capa no sabe qué hay debajo, así que lo que se va a las esquinas cae sobre
  vacío y parece suciedad alrededor del icono.
- **Huevos de generación** — toman la **silueta y la luz de un huevo de Minecraft real** y se repintan
  con los dos colores de cada mob.
- **Mangual** — la textura aprobada, cortada en sus tres capas (bola, cadena, mango). La pieza **bola**
  pasa a ser la cabeza del arma recortada, así que pieza y arma coinciden.
- **Pavesa** — las alas eran dos tablones planos clavados a un farol; ahora van en **tres tramos**, cada
  uno más estrecho, menos profundo y un poco más alto que el anterior. Las puntas tienen color propio,
  más vivo que el carbón del cuerpo, porque un fuego es más brillante donde es más delgado.

### Guanteletes, en tres piezas

Con la foto de unos mitones que mandó Andy, y partidos en **manopla** (el mitón de cuero, HANDLE),
**nudillos** (la banda de metal, HEAD) y **remache** (los cuatro remaches, EXTRA). Cada pieza se tiñe
con su material, así que se puede poner buen acero sobre un guante barato o remachar una banda pobre
con oro, y se ve.

Lo que hace que una mano se lea a dieciséis píxeles no es el acolchado: son **nudillos que escalonan**,
un **lóbulo de pulgar** rompiendo un lado y una **muñeca que se estrecha** hacia el puño. La primera
tanda no tenía ninguna de las tres y salieron tres cajas — un rectángulo redondeado con bandas es una
caja a cualquier tamaño.

Un detalle del troceado: la banda se lleva también **la fila de arriba de la mano**. Sin eso, al
quitarla para el icono de la pieza, la manopla se quedaba con un arco suelto flotando encima.

### Y una guarda que debería haber estado desde el principio

Pasar los guanteletes de dos piezas a tres rompió el comando del kit, que los creaba con dos
materiales escritos a mano. El síntoma fue `Timeout loading world` en la prueba; la causa, un
`IndexOutOfBounds` dentro de la hoja de estadísticas mientras el jugador entraba al mundo, que se
llevaba por delante la carga entera sin decir de quién era la culpa.

`Assembler.create` ahora comprueba que el número de materiales coincida con las ranuras del tipo y, si
no, dice qué tipo, cuántas piezas espera y cuáles recibió. Hay **44 sitios** en el mod que pasan listas
de materiales escritas a mano; ese desajuste no debería volver a salir como un crash anónimo.

### Y el gancho, por fin

El último punto de la lista de Andy. Estaba parado porque no hay ni mano ni garfio dibujados en
vanilla y todo el arte bueno del mod sale de reutilizar sprites enteros — hasta que al mirar otra vez
apareció lo que sí hay: `item/iron_chain` y `item/stick`.

Era una **V** con un palo debajo, o sea un tirachinas. Ahora va al revés, **como un ancla colgando**:
rezón de cuatro brazos curvándose hacia arriba, una anilla donde la cadena se engrilleta al vástago, la
cadena subiendo y el palo en diagonal arriba.

La cadena es la de Minecraft **recta y sin tocar**. El primer intento lo puso en diagonal, como un
pico, y entonces había que ir pegando la cadena eslabón a eslabón a lo largo de la diagonal: donde se
cruzaba con el palo quedaba un borrón. Un gancho no es una herramienta que se blande.

Lo que no se ve venir: Minecraft dibuja la cadena **muy oscura** —el sprite entero vive entre un quinto
y un tercio del blanco— porque en vanilla nunca se tiñe. La nuestra se multiplica por el metal de la
cuerda, así que tal cual salía casi negra fuese del metal que fuese. Hay que estirarle los niveles al
entrar.

### El gancho se duplicaba

Lo vio Andy jugando. Cada uso del gancho dejaba **un gancho de más** en el inventario.

La garra es una entidad aparte que lleva una copia del gancho, y al aterrizar la devolvía "a la mano de
la que salió". Sólo que un gancho **nunca sale de la mano**: lo sigues sosteniendo y lo único que vuela
es la garra, así que la copia se iba derecha al inventario.

La condición era `this.mode != Mode.HEAD`, es decir, deducía *¿salió esto de la mano?* a partir del
modo. Cierto para los dos modos de lanzamiento —lanza y escudo vacían la mano antes de tirar— y
silenciosamente falso para el garfio. Ahora hay un campo explícito, `carriesItem`, que sólo pone a
`true` el constructor de arma lanzada, que es el único que vacía la mano. `stopFlight()` tenía la misma
suposición para el caso de no-retorno: soltaba un `ItemEntity` con otra copia. Con el gancho no se
alcanzaba (su `returns` es `true`), pero era la misma duplicación esperando turno.

Y una prueba nueva, `checkGrapple`, que cuenta los ganchos antes y después, comprueba que no queda nada
forjado por el suelo y que el tirón movió al herrero — para que demuestre que el gancho **sigue
funcionando**, no sólo que no duplica. Verificada revirtiendo el arreglo a propósito: *gancho: antes 1
en mano, despues 2 en inventario · se movio 3.13 bloques en z* y `AssertionError: got 2`. Con el
arreglo, *antes 1, despues 1*.

Eso es lo peligroso de este fallo: todo lo visible del gancho iba bien —salía, mordía, tiraba— mientras
la cuenta subía en silencio.

### Un bug que ya estaba y no se veía

`generate_models()` borraba `models/item` **a mitad de la corrida**, después de que
`generate_fallen_forge()` ya hubiera escrito ahí los modelos de moldes y marcos. Los moldes por pieza y
los marcos por herramienta —hechos esa misma tarde— estaban **sin modelo** en el jar y habrían salido
como cubo rosa en el juego. Las texturas estaban bien; sólo faltaban los `.json`, que es exactamente el
tipo de fallo que compila, pasa las pruebas de datos y sólo aparece al mirarlo. El borrado ahora va
primero, antes de que nada escriba.


## 2026-09-18 (noche, 4)

### Caño de colada

Andy: «una terminal en la que el líquido pueda caer, y que así la animación de caída en las mesas se
aproveche». El chorro de las mesas salía de la nada; ahora tiene de dónde salir.

Un **caño de colada** (4 bronce + 1 conducto) es un canal con **el suelo agujereado**. Lo que le llega
cae, hasta **5 bloques**, sobre lo que haya debajo: una cuba, una caja, una mesa, otro canal.

- Es la única forma de alimentar algo que está **en otro piso** sin apilar bloques hasta él.
- **Sólo cuela cuando tiene dónde caer.** El chorro no se dibuja si no hay nada que lo recoja, así que
  es el único indicador honesto de toda la fundición: si cae metal, el tramo está conectado.
- **Caer cuesta 4 de calor por bloque**, peor que el peor conducto. Un caño alto entrega el metal casi
  cuajado.
- La caída funciona **en los dos sentidos**: el caño encuentra lo que hay debajo, y la mesa de abajo
  encuentra el caño de arriba. Hacía falta lo segundo porque es la mesa la que va a buscar metal, no la
  cuba la que va a buscar mesas — sin eso la mesa nunca habría visto la pasarela sobre su cabeza.
- La mesa **deja de dibujar su propio chorro** cuando tiene un caño encima, o se veían dos.

### Y las calidades de conducto por fin sirven para algo

`bleedBetween` existía desde que se añadieron las tres calidades y **no estaba conectado a nada**: sólo
lo usaba la prueba. Ahora el crisol paga el tramo al verter, así que el metal llega a la cuba con menos
calor cuanto peor y más largo sea el conducto, y un banco al final de un tendido barato de bronce
**cuaja antes** que uno construido contra el puchero. Eso es lo que las calidades prometían desde el
principio.

Medido: *caño: cae en la mesa true a un coste de 28 de calor, la mesa lo ve true, sobre el vacío no cuela
true · cuela una daga true gastando 3*.

### Gotcha de render que sólo se ve en el juego

El chorro cuelga varios bloques por debajo del bloque que lo dibuja, así que Minecraft lo recortaba en
cuanto el caño salía de pantalla: desaparecía justo al mirar el suelo donde cae. `shouldRenderOffScreen`
lo arregla. Dos fotos nuevas: `forja_36_cano` (el caño de lado, cayendo tres bloques a una mesa) y la
pasarela ya metida en `forja_34_fundicion`.

## 2026-09-19 — la onda de yunque, redibujada

### Qué cambia

La **onda de yunque** del Herrero Caído eran 28 partículas que el servidor mandaba cada tick: a nueve
bloques quedaba una llama cada dos, avanzaba a saltos de 20 por segundo y costaba 28 paquetes por tick.
Ahora es **una entidad** (`forja:onda_expansiva`, `entity/Shockwave`) que el servidor anuncia **una sola
vez** — dónde, hasta dónde y cuándo empezó — y que cada cliente dibuja por su cuenta, calculando el
radio en cada frame con el reloj del mundo. Es el mismo truco que ya usaban las mesas de colada.

- **El aviso:** en cuanto sube el martillo hay en el suelo una línea donde va a parar el anillo y un
  resplandor que se va llenando hacia ella. Cuando se tocan, cae el martillo. Sigue al herrero si lo
  empujan y desaparece si muere con el martillo en alto (o si se vuelve a la fragua a media carga, que
  antes dejaba el golpe en pausa y lo soltaba de la nada al salir).
- **El anillo:** una banda en el suelo (labio al blanco vivo, cuerpo, cola que se apaga), una **cortina
  de fuego** de pie sobre el frente, un **anillo eco** más tenue detrás, un **destello** bajo el
  martillo y un rescoldo de seis ticks al final. Naranja normalmente, **violeta** cuando la fragua del
  pecho está desatada. Todo con el render type `eyes` de vanilla, que es el único emisivo y translúcido
  **sin corte de alfa**, así la cola se apaga hasta cero en vez de cortarse al 10 %.
- **Sigue el suelo:** mide la altura de cada columna de bloques bajo su alcance (una vez cada medio
  segundo, no por frame) y cada trozo del anillo se apoya, plano, en la columna más alta que toca. Sube
  escalones y baja a zanjas con el borde limpio. Hicieron falta tres intentos; los dos primeros
  (medir sólo bajo el frente, y mezclar entre muestras) hundían el aviso o lo dejaban hecho jirones.
- **En el cliente y gratis para el servidor:** escombros del **bloque real** que pisa el frente,
  chispas, el **sonido cuando el frente te alcanza** y una **sacudida de cámara** (al caer el martillo,
  según la distancia, y otra al pasarte por encima). La sacudida obedece al ajuste de "efectos de
  distorsión" de Minecraft.
- La **ruptura de la última fase** usa la misma onda (violeta, 12 bloques, inofensiva) en lugar de seis
  círculos de polvo quietos.

### Y el golpe ahora es el dibujo

- `Shockwave.radiusAt` es el único sitio donde está escrita la velocidad del anillo; la usan el golpe y
  el render, así que **el círculo que ves es el círculo que pega**.
- El golpe es **barrido** (a quien cruzó el frente entre el tick anterior y éste, contando el ancho del
  cuerpo) en vez de la banda de ±1,2 de antes, que también alcanzaba a quien el anillo aún no tocaba.
  Con puntos no se notaba; con un borde nítido, sí.
- El anillo sale de **donde cayó el martillo**, no de donde haya caminado el herrero desde entonces.
- **Se salta.** La guía lo decía desde el principio y el código nunca lo comprobó: con los pies a más de
  medio bloque del suelo cuando llega el frente, pasa por debajo. Si aterrizas mientras aún te está
  cruzando, te lleva.

Medido: *onda: el husk a 6.04 bloques cae en el tick 13, con el frente en 5.85* · aviso, frente,
limpieza, cancelación y salto comprobados. `FORJA_SOLO=onda ./gradlew runClientGameTest` corre sólo esta
sección (38 s en vez de 5 min) y deja las fotos `forja_onda_*` (de día, de noche y desde el suelo, sobre
un escalón y una zanja).

### Para reutilizarla

`Shockwave.telegraph(level, dueño, alcance, ticksDeAviso, ticksDeCarrera, color)` → `fire(posición)`, o
`Shockwave.burst(...)` sin aviso. No hace daño a nadie: quien ataca decide a quién pega, con
`radiusAt`. Sirve tal cual para el Sismo del martillo o los anillos de las sinergias, que siguen siendo
polvo. No hay librería de efectos para 26.2 (Veil sólo 1.21.1, Satin hasta 1.21.4, AAA Particles hasta
26.1.2), así que no añade dependencias.

## 2026-09-19 (tarde) — la onda para todos, y las animaciones en su sitio

Andy lo probó en el juego con `/forja onda` y vio dos cosas: que **la animación no cuadraba** con el
golpe, y que **el resto de enemigos seguía con los anillos de puntos**.

### Tres animaciones que iban por libre

Los avisos del martillazo del Herrero, la embestida de la Coraza y la coz del Autómata se habían
triplicado (54, 33 y 36 ticks) **sin tocar sus animaciones**:

- **Herrero, `slam`:** el martillo tocaba el suelo en el tick 18 y el anillo salía en el 54, casi dos
  segundos después. Ahora sube despacio (24), aguanta arriba mientras se llena el aviso (hasta 50) y cae
  en cuatro ticks, **justo en el 54**.
- **Coraza vacía, `dash`:** la animación *era* la embestida, y se reproducía al decidir, no al salir:
  embestía en el sitio, se levantaba y entonces se iba. Ahora los primeros 33 ticks son la armadura
  **recogiéndose** (se hunde hacia atrás, brazos y espada detrás) y la embestida empieza donde empieza.
- **Autómata, `coz`:** el ataque pedía una animación `coz` que **no existía** (GeckoLib lo avisaba en el
  log en cada golpe): se quedaba quieto dos segundos y el suelo ardía. Ya existe: los dos brazos arriba,
  aguantan mientras crece la cuña, y caen en el tick 36.

Y para que no vuelva a pasar, el test lee las animaciones y exige que **cada ataque cargado tenga un
fotograma clave en el tick exacto en que el código da el golpe** (12 ataques de 9 mobs: *animaciones de
ataque: 12 comprobadas, 0 desfasadas*). Cambiar un aviso sin tocar su animación ahora rompe el test.

### El mismo anillo para todos

Todo ataque de área de un enemigo usa ya `Shockwave`, con un solo idioma: **la línea es hasta dónde
llega, el resplandor se llena hacia ella, y cuando se tocan cae el golpe.**

| Enemigo | Ataque | Antes | Ahora |
| --- | --- | --- | --- |
| Percutor | caída del ariete | 10 llamitas a **la mitad** del radio real | aviso + impacto a los 5,5 bloques que pega de verdad |
| Templador | baño de aceite | 22 motas | aviso + salpicadura, verde aceite, casi sin llama |
| Guardián de cuño | sellazo | 16 motas | aviso + impacto, oro del sello |
| Cargador de carbón | mecha | chispas en círculo | aviso que **camina con él** y estalla con ese mismo círculo |
| Autómata | purga de vapor y muerte | 40 nubes al 60 % del alcance | anillo de vapor hasta donde escalda |
| Coraza vacía | lamento | 44 almas a 2 bloques | anillo de alma hasta los 7 que alcanza |
| Capitán saqueador | toque de cuerno | 36 críticos | anillo pálido hasta donde se oye |

- `Shockwave` gana un aviso **anclado a un punto** (no sólo pegado a su dueño) y una **altura de llama**
  (1 = el martillo del Herrero): el vapor, el aceite y un sello no son hogueras, y ese mismo número
  decide cuánto sacude la cámara y si ruge al pasarte. Los anillos que no arden (vapor, alma, aceite)
  sueltan motas de su color en vez de chispas.
- `Windup` es ahora el **dueño del aviso** (`warn` / `takeWarning`): se va si la carga se cancela, si
  empieza otra encima o si el golpe cae sin usarlo. Ocho mobs recordándolo cada uno en tres sitios eran
  veinticuatro ocasiones de dejar un círculo encendido por un golpe que no llega.
- El **Percutor** marcaba la mitad de lo que pegaba: con puntos no se veía; con un borde nítido, quedarse
  justo fuera del círculo y recibir igual habría sido una trampa. El círculo es ya el área real.
- Se queda como estaba la **cuña** de la coz del Autómata (es un cono, no un círculo) y los anillos del
  lado del jugador (sinergias, Sismo, Frenesí), que no son de enemigos.

`/forja onda` pone al Herrero delante, sin IA, y le hace soltar el martillo las veces que se pida.

## 2026-09-19 (noche) — todo lo que se ve

Una sesión entera dedicada a cómo se ve el mod, con los fallos que fueron apareciendo al mirarlo de
cerca. Cada cambio tiene su foto o su GIF en `docs/mejoras_graficas/` (y reunidos, con índice, en
`Forja_capturas_mejoras/`); el registro paso a paso está en `docs/mejoras_graficas/REGISTRO.md`.

### Los eventos del cielo, rehechos

Antes cada evento era **un tinte plano** del cielo. Ahora cada uno es una cosa que se mira:

- **Luna de sangre**: luna roja, llena y el doble de grande, con halo; su luz tiñe la tierra.
- **Eclipse**: un disco tapa el sol y le deja la corona; el mediodía se queda en penumbra y **salen las
  estrellas de día**.
- **Aurora**: cuatro cortinas verdes y violetas meciéndose sobre el norte.
- **Tormenta arcana**: un círculo de runas gira sobre tu cabeza y los relámpagos **iluminan el suelo**
  un instante antes del trueno.
- **Lluvia de meteoritos**: estrellas fugaces y bólidos. El que cae cerca **marca dónde va a dar**, se le
  ve bajar con su cola, y deja una onda de impacto de 11 bloques.
- **Marea viva**: luna enorme, pálida y azulada, con su luz derramándose hacia el agua.
- **Lluvia de pavesas**: el horizonte arde todo alrededor y cae fuego despacio.
- **Niebla de almas** y **ventisca**: cierran la vista **de verdad** (30-44 bloques), con luces pálidas
  ancladas al terreno en la niebla. Ya no esperan a la noche para notarse.
- La **luz del mundo** toma el tono del evento, cada uno tiene su **sonido de ambiente**, y al empezar
  baja un **cartel** con el nombre, la mejora que trae, cómo guardarla y el tiempo que queda.

### El libro guía

- Cuero cosido, latón, pergamino con grano, lomo y cinta; portada con emblema; cabeceras-estandarte.
- **Cinco pestañas de sección**, **tira de progreso** al pie en la que se puede pinchar, historial
  (clic derecho o Retroceso vuelve atrás; Inicio lleva al índice) y la página se asienta al pasarla.
- **Bestiario**: once criaturas que no estaban y **retrato 3D** de todas.
- El capítulo de eventos cuenta ahora lo que se ve en cada uno. Son 207 páginas.

### Las pantallas de trabajo

- **Mesa de forja**: la estrella grabada **se enciende** cuando lo que hay encima forja algo (naranja;
  violeta pálido en la mesa mayor), con chispas recorriendo sus líneas; al golpear, un **estallido** que
  es mayor si el golpe fue perfecto. El botón destella cuando se puede pulsar, el riel del martillo
  enseña también la zona del golpe «decente», y la barra de calor arde.
- **Crisol**: una olla con forma, no un rectángulo; burbujas, resplandor y chispas mientras está encendido.
- **Caja de moldeo**: una caja de arena con la huella, que se llena de metal o se imprime según el trabajo.
- **Armario de piezas**: tres cajones de madera con tiradores de latón en vez del cofre gris de vanilla.
- **Tooltip de lo forjado**: las piezas **dibujadas en fila** bajo el nombre (el icono dice qué pieza
  es; el texto, de qué está hecha, en el color del material) en vez de una línea por pieza.
- Barras de **Frenesí** y **Vuelo** rehechas; **barra de jefe** propia del Herrero caído (hierro, cabezas
  de martillo, muescas al 75/50/25 %, metal fundido, violeta en el último cuarto).

### En el mundo

- **Charcos** de escoria y aceite dibujados como mancha con borde (antes eran
  partículas del servidor cada tick). **Cuña** del pisotón del Autómata con bordes rectos y su charco con
  la misma forma. Anillo de alcance en Torbellino, Sismo y Siega.
- Dos **partículas propias** más: **vapor** (purga del autómata, temple en agua, presión del Templador;
  sale rápido, sube y se deshace) y **gota** de metal fundido con el color del metal que pasa por el
  caño, que suelta chispas donde cae. Antes eran la nube redonda y el «pop» de lava de vanilla.

### Fallos que no eran de aspecto

- **Crisol y caja de moldeo**: los menús ponían el inventario del jugador en (15,122) y la textura lo
  pintaba en (22,114): **cada objeto salía 7 px a la izquierda y 8 abajo de su casilla**. La salida del
  crisol estaba 5 px movida, la casilla del colador no tenía marco, el título era marrón sobre madera
  (ilegible) y los textos largos se salían del panel. Las dos texturas ni siquiera estaban en el
  generador; ahora salen de él con las mismas constantes que los menús.
- **Armario de piezas**: al abrirse como cofre de vanilla, la regla «sólo cosas de herrero» **sólo valía
  para las tolvas**: a mano se podía meter adoquín. Tiene menú propio cuyas casillas preguntan lo mismo
  que se le preguntaba a la tolva, también con Mayús+clic; y si llevas en el cursor algo que no entra, los
  cajones se apagan y lo dice.
- **Textos que salían como clave**: tres eventos sin descripción en el libro, la descripción larga de
  dos rasgos (Estelar, Vacío) y el bono de conjunto de la escoria. El del corazón de forja decía «ardes
  la mitad» y el número apaga el fuego del todo: ahora dice lo que hace.
- **Tildes**: todo el bloque de Técnicas, el Capitán saqueador, el campamento y el Guardián de cuño.
- **Etiquetas de ítems** (20) sin nombre: los visores de recetas enseñaban `forja:acero_refractario`.
- `items/guante.json` huérfano (aviso de modelo en cada arranque).

### Para que no vuelva a pasar (el test)

- Cada casilla activa de cada pantalla tiene que caer **sobre un cuadro pintado en su textura** (mesa de
  piezas en sus dos pestañas, las dos mesas de forja, talabartería, crisol, caja y armario).
- El libro no puede imprimir **ninguna clave de traducción**, y se piden los 685 nombres y descripciones
  que el código construye a partir de un id: el generador de idiomas no podía ver esas claves.
- Secciones sueltas: `FORJA_SOLO=` `onda`, `cielo`, `libro`, `hud`, `meteorito`, `pantallas`,
  `particulas` (medio minuto cada una en vez de siete).

### Tras verlo Andy (misma noche)

Tres cosas que dijo al repasar las capturas, y las tres tenían razón:

- **Las áreas, a un solo nivel.** Charcos, avisos y ondas se tendían sobre el terreno pieza a pieza, y
  sobre cualquier cosa que sobresaliera quedaba un trozo de anillo subido a lo alto de un muro con los
  bordes en el aire. Ahora todo lo plano se dibuja **a una altura**: el suelo más bajo que comparta al
  menos una sexta parte del alcance (no el del centro: el Herrero sobre una tarima subiría el anillo
  entero a la altura del pecho; y no el más común: en un escalón ancho ganaría la mitad de arriba y el
  anillo colgaría sobre la de abajo). Lo que sobresale **se tiñe**: los mismos colores, al 45 %, en sus
  propias caras — la de arriba y cada lado que asome —, esquina por esquina, así que un bloque medio
  dentro sólo se tiñe a medias. Una alfombra o una capa de nieve cuentan como suelo y lo reciben entero.
  La cuenta del golpe no cambia: quien esté subido a ese bloque sigue teniendo que saltar.
- **La mesa de forja mayor habla violeta.** Su panel es el normal recoloreado, pero sólo la textura
  pasaba por esa cuenta: lo que pinta la pantalla (pestañas, botón, las tres filas de técnicas) seguía en
  madera y arenisca, un «Forjar» marrón sobre un banco violeta. `ForgeScreen.tone()` hace la misma
  aritmética para un color, y `accent()` deja el botón en el violeta en que arde su estrella.
- **El metal fundido tiene el color del metal.** Había cinco «empújalo hacia el naranja» (cuba, canal,
  mesa de colada, pantalla del crisol y caño), cada uno a cuatro quintos del camino: oro, acero y cobre
  eran el mismo charco. Una sola función, `ForgeMaterial.molten()`: un tercio del resplandor de cualquier
  cosa así de caliente, dos tercios el metal, y aclarado hasta que casi llena su canal más vivo. El oro
  cuela amarillo, el cobre naranja, el hierro melocotón pálido, el diamante menta, el lunacero lila, el
  acero vivo rojo. El crisol, además, toma el color de **lo que está saliendo** (hierro + carbón ya se ve
  acero). El test exige que ningún par de los que prueba quede a menos de 60 de distancia.
- En la pestaña de **Técnicas**: barra de maestría de herrero con pasadores en los niveles 3, 6 y 9,
  candado en las filas cerradas, pulso dorado cuando hay una elección esperando; y la línea de maestría
  se acorta, que medía 260 px en un panel de 194 y se salía por los dos lados.
- La pestaña de **logros** del mod tiene su propio fondo (ladrillo de forja con brasa en las juntas) en
  lugar de la piedra gris de vanilla.

## 2026-09-20 — el potencial, y la mesa que quita una mejora

Diseño de Andy (lo transmitió Codex en `Forja_para_Claude_porcentajes_y_extraccion.md`). El diseño con sus
números y las decisiones tomadas donde había hueco está en `docs/POTENCIAL.md`; todos los números viven en
`forge/Potential.java`.

### El potencial

Hasta ahora toda mejora llegaba al 100 % en cualquier pieza por el precio de sus ingredientes: un montón de
azúcar era Eficiencia V el primer día. Ahora **cada pieza tiene un potencial** — hasta dónde pueden subir
sus mejoras — que sale de cómo se hizo y que se ve en su descripción.

| De dónde | Cuánto |
| --- | --- |
| Suelo | 40 |
| Piezas coladas limpias en la fundición | hasta +20, repartido por pieza (las de mesa y las bastas, 0) |
| El martillo | fallo 0 · decente +5 · perfecto +10 |
| Nivel de herrero | +1 por nivel |
| Mesa de forja mayor · taller completo | +10 · +5 |
| *Después:* maestría de la pieza | +1 por nivel |
| *Después:* cada pacto | +10 |
| *Después:* mejora **Recocido** (nueva: polvo de blaze + arcilla) | hasta +15 |

- **Tres topes**, y una mejora sube hasta el menor: el **potencial** de la pieza, la **mesa** (la de forja
  no pasa del **50 %**; la mayor llega al 100 %) y, por encima del **90 %**, el **fundente maestro**.
- **Ningún tope baja nunca una mejora que ya esté puesta**: sólo impide subirla. Subir el potencial
  tampoco regala porcentaje: abre sitio, no lo llena.
- **Tramos de coste**: hasta el 50 % cada ingrediente rinde lo que dice, de 50 a 75 la mitad, de 75 a 100
  la cuarta parte. Un ingrediente a caballo de una raya se reparte entre los dos lados, así que un bloque
  echado al 49 % no se cuela entero al precio barato. Filo 0→90 son 40 amatistas donde eran 23.
- **Fundente maestro** (ítem nuevo: hierro estelar + 2 polvo de blaze + fragmento de eco → 2): va en la
  estrella con los ingredientes y se gasta **una** pizca la vez que una mejora cruza el 90 %. Una mejora
  que ya está por encima no vuelve a pedirlo.
- **Pactos y mejoras de evento van por fuera de todos los topes** (así leí «los pactos/mejoras de evento
  no afectan al %»), y cada pacto además ensancha el potencial.
- Las piezas coladas se cuentan **por ranura** (una máscara en la pieza): cambiar una hoja colada por una
  de mesa resta su parte y volver a ponerla la devuelve, ni más ni menos. Al desarmar, las piezas
  recuerdan si eran coladas.
- Lo de antes: piezas sin potencial propio valen 70. El botín trae entre 60 y 90; las leyendas, 100; lo
  que sale entero de una mesa de colada, 40 + piezas + la mano firme de la mesa.
- La mesa lo dice: «Potencial 68 % · esta mesa llega al 50 %», y cuando algo no sube explica **por qué**
  (la mesa, la pieza o el fundente) en vez de callarse.

### Los orbes valen lo que costaron

Un orbe guarda **valor**, no porcentaje. Sobre una pieza sin esa mejora devuelve entero lo que guarda;
encima de la misma mejora, o fundido con otro orbe, paga el mismo precio creciente que los ingredientes:
**dos orbes del 50 % hacen uno del 75 %**, no del 100. Sin esto, dos mitades baratas extraídas y fundidas
habrían sido una mejora entera a mitad de precio. Lo que una pieza no pueda tomar **se queda en el orbe**,
en la estrella, en lugar de perderse. Libros y herencia pasan por los mismos topes.

### Mesa de extracción

Bloque nuevo (latón + orbe vacío + afiladora + pizarra pulida). **Quita una mejora, y sólo esa**: la
pieza, la mejora que eliges de su lista, y como pago **los ingredientes de esa misma mejora** — un paso de
su receta por cada 25 % que tenga; vale cualquiera de sus recetas, y el precio se ve como fantasmas en las
casillas de pago. Con un **orbe vacío** (4 vidrio + amatista → 2) la mejora sale **entera** al orbe: esa es
la diferencia con desarmar, que devuelve la mitad. Sin orbe el botón pasa de «Extraer» a un «Borrar» rojo y
avisa de lo que se pierde. Piezas, calidad, firma, historial, maestría y las demás mejoras no se tocan; se
reescribe sólo lo que la mejora escribió (su encantamiento oculto, su atributo). Las de evento se pagan en
frascos de cristal y sólo salen a orbe. **Un pacto no sale nunca.** Si lo que se quita es el Recocido, el
potencial baja y lo que quede por encima se conserva sin poder subir.

### La carga: el potencial decide también cuánto lleva la pieza

Andy, tras probar el potencial: «quiero que ahora esto determine cuántas mejoras máximas puede tener…» y
en seguida «no todas las mejoras deberían tener el mismo peso… teniendo en cuenta las sinergias». Así que
no son ranuras iguales: cada mejora **pesa de 1 a 4** según lo que aporta al 100 % (ranking hecho con los
números de `Upgrade` delante) y la pieza aguanta **(potencial − 20) / 4** puntos: **5 la peor, 20 una
perfecta**. Una espada mala lleva Filo y un detalle; una perfecta, siete u ocho mejoras elegidas con
cuidado y nunca todas las buenas. Pactos, mejoras de evento y Recocido no pesan; las sinergias tampoco (son
el premio por gastar la carga en una pareja), pero el ranking las cuenta: Tormenta y Onda de choque pesan 3
por ser nudo de dos parejas cada una. Nada se pierde en piezas que ya existan. Una sola barra
(`client/LoadBar`) lo enseña en el tooltip, en la mesa y en la rueda de extracción, que ahora además dice
cuánta carga libera cada gema. Tabla completa y criterio en `docs/POTENCIAL.md`.

### Todo o nada (fallo del potencial, y la regla de Andy que lo arregla)

Siete mejoras sólo hacen algo al 100 % — Toque de seda, Reparación, Infinidad, Multidisparo, Llama,
Afinidad acuática, Visión nocturna — y el potencial las dejaba **muertas para siempre** en cualquier pieza
por debajo de 100, después de comerse los materiales hasta el tope. Regla de Andy, aplicada en
`Potential.ceiling()` para ingredientes, libros, orbes y herencia por igual: las **ligeras** (peso 1-2)
llegan al 100 % en cualquier mesa sin mirar mesa, potencial ni fundente; las **pesadas** (peso ≥ 3) son
trabajo de la mesa mayor — la normal las rechaza enteras, sin gastar nada y diciendo adónde ir — y allí
llegan al 100 % sin potencial ni fundente. El peso cuenta para la carga como el de cualquiera.

De paso: la línea «Potencial N % · esta mesa llega al M %» del panel de la mesa se salía del panel por la
derecha (38 caracteres en 83 píxeles). Ahora son dos cosas cortas en la misma línea — potencial a la
izquierda, carga a la derecha — y la barra debajo.

### Un tipo de inventario nuevo: la rueda

Andy pidió para esta mesa **un tipo de inventario propio**, y lo tiene: todas las demás mesas del mod son
una rejilla o una lista sobre madera y arenisca porque todas *montan* cosas; ésta *quita* una, y se ve
como lo que hace. Panel de **pizarra oscura y latón** (la piedra del propio bloque), más ancho (236×204).
La pieza va en el **cubo de una rueda** y cada mejora que lleva es una **gema engastada en la llanta**, en
el color de la mejora y más grande cuanto más porcentaje tiene; un pacto es una gema oscura y tachada: está
en la pieza, así que está en la rueda, y no sale. Eliges una gema — late, y un hilo de su color la une a la
pieza — y a la derecha aparece su **ficha** (nombre, barra con los cuartos en que se cuenta el precio,
efecto); debajo, la **bandeja** enseña en fantasma lo que cuesta, el orbe vacío y la **cuna** donde sale el
lleno. Al extraer, **la gema deja su engaste y vuela en arco hasta la cuna**. Más de ocho mejoras: la
rueda gira con la rueda del ratón. (`client/ExtractionScreen`, `generate_extraction_panel`.)

### El libro lee sus negritas

Setenta textos del libro estaban escritos con `**esto**` y nada lo leía nunca: el bestiario imprimía
«\*\*Cerrar filas\*\*: toca el cuerno…» con los asteriscos puestos. Ahora lo marcado va en **tinta de
rúbrica** (rojo pardo, como un manuscrito señala un nombre) — color y no negrita, porque el libro se
compone a tres cuartos de tamaño y una negrita ahí es un borrón. (`GuideText.rubric`.)

### Fallo encontrado de paso: media fundición no soltaba nada al picarla

La etiqueta `mineable/pickaxe` se escribía en dos sitios del generador con dos listas, y el archivo es lo
último que se escribe: las tres mesas. **Crisoles, cubas, conductos, caño, cajas y mesas de colada, el
farol de pavesa, la fragua apagada y el yunque del herrero** piden herramienta correcta y no estaban en la
lista de ninguna: se picaban despacio y **no soltaban nada**. Una lista única (`PICKAXE_BLOCKS`) y el
generador avisa de cualquier bloque que no esté en ninguna etiqueta de herramienta.

### Test

`FORJA_SOLO=potencial`: `checkPotential` (los topes contra la mesa real: 45 de potencial en un pico de
mesa, la espada antigua con Filo 80 que la mesa básica ni sube ni baja, 90 sin fundente y una pizca
gastada con él, el pacto por fuera, orbes por valor, la máscara de piezas) y `checkExtraction` (con clics
reales: quita Filo y su encantamiento oculto, saca el orbe al 80 %, gasta 4 amatistas y un orbe vacío, no
toca nada más, rechaza el pacto, espera con la salida llena y sin orbe borra). Las comprobaciones antiguas
que contaban ingredientes hasta el 100 % calculan ahora lo esperado con `Potential`.

## 2026-09-20 (mediodía) — dos armas mágicas, y nada del mod brilla

### Báculo de media luna y grimorio forjado

Andy pidió «una nueva arma, de tipo mágico», eligió entre dibujos (`docs/arma_magica/`) y se quedó con dos:
el **báculo de media luna** (A2) «que tire proyectiles mágicos» y el **grimorio forjado** (C) «que tire un área
5 bloques delante del jugador, del color del círculo del libro, y que deje la runa».

- **Báculo** (`ForgeType.BACULO`): **núcleo + engaste + asta** (el asta es el mango de siempre). Clic derecho:
  un proyectil recto, sin gravedad, del color del núcleo (`entity/MagicBolt`: no tiene modelo, es la estela de
  polvo que el servidor deja en su vuelo). 0,7 s de espera. Daño = `max(3, 4 + bono del núcleo × 0,9)`.
- **Grimorio** (`ForgeType.GRIMORIO`): **núcleo + tapas + remaches**. Clic derecho: abre un área de 3 bloques de
  radio **a 5 bloques delante** (más cerca si hay pared, y sobre el suelo que haya allí), golpea lo que pille
  (`max(3, 5 + bono)`) y **deja una runa 6 s** que muerde dos veces por segundo con un cuarto de ese daño y
  ralentiza. 3,5 s de espera. Las páginas y el broche van en una capa sin teñir (`FIXED_LAYERS`), lo demás se
  tiñe con sus materiales como cualquier otra pieza del mod.
- **El núcleo es la magia**: ni elementos ni maná. El material del núcleo da el color del proyectil, del área y
  de la runa, y su mordida. Un báculo de amatista y uno de vara de blaze son armas distintas sin una línea de
  código entre ellos, que es como funciona el resto del mod.
- **Las mejoras viajan en el hechizo.** Un proyectil y la apertura del área son *golpes del arma que los lanzó*
  (`Spellcasting.land` / `casting()` / `blow()`): llevan Filo y los demás encantamientos de daño
  (`EnchantmentHelper.modifyDamage`), y todo lo que `CombatUpgrades.onWeaponHit` hace con una espada —
  Vampirismo, Escarcha, Veneno, Tormenta, Crítico, Ejecución, frenesí, maestría. Los **mordiscos de la runa no**:
  muerden dos veces por segundo a todo lo que la pise, y doce golpes de rayos y de maestría por lectura harían
  del grimorio la respuesta a todo. La muerte sigue siendo del lector y de su grimorio (historial, Sabiduría,
  Siega de almas), aunque para entonces lleve otra cosa en la mano.
- **La runa es un glifo de verdad** (`Shockwave.rune`, `ShockwaveRenderer.rune`): dos texturas de 96 px — 16 por
  bloque, al grano del suelo — que giran una contra otra: un **aro de doce letras** entre dos círculos y la
  **estrella de ocho puntas de la mesa estelar** con el núcleo en el centro. Se tiñe con el color del núcleo,
  **destella cada vez que muerde** (con el mismo reloj con el que muerde el servidor: el destello es el momento
  de no estar ahí) y se apaga según se le acaba el tiempo. El charco de debajo se dibuja a la mitad para que se
  lea, y de una runa no salen llamas: salen motas de su color.
- Tres piezas nuevas (**núcleo, engaste, tapas**: 36 en total) y la rejilla de la mesa de piezas pasa a **3 filas
  de 12** con celdas de 16. Las dos armas son de **mesa mayor**. Capítulo en el libro (recetas + «Báculo» y
  «Grimorio» en los especiales), `/forja kit` trae una de cada.

### Nada del mod lleva el brillo de encantamiento

Andy: «quiero que nada del mod tenga el brillo por encantamiento». Las mejoras con encantamiento detrás (Filo,
Eficiencia, Protección…) y los materiales con rasgo ponían el velo morado sobre lo único que una pieza forjada
tiene que enseñar: de qué está hecha. Un grimorio de vara de blaze salía morado.

- `ItemStackMixin.forja$noGlint`: `hasFoil()` es `false` para **cualquier objeto del espacio `forja`** —
  herramientas, armas, armadura puesta, alas, tridente lanzado, orbes, talismanes. Se le pregunta a la pila, no
  se escribe en ella, así que **lo forjado antes también lo pierde**. Lo de Minecraft no se toca: una espada de
  diamante encantada sigue brillando.
- El nombre en color de rareza se queda como estaba: es cómo el juego dice «esto lleva algo», y no tapa nada.

### Test

`FORJA_SOLO=magia` (`checkMagic`): forja las dos, dispara el báculo contra una vaca a 5 bloques (un proyectil en
el aire, enfriamiento, 5,8 de daño con núcleo de amatista), abre el grimorio (el área cae a 5,00 bloques, daña,
deja runa y la runa sigue mordiendo), comprueba que **una espada con Filo y un grimorio de vara de blaze no
brillan y una espada vanilla encantada sí**, y que un báculo con **Escarcha y Filo** deja lentitud y quita 9,7
donde sin mejoras quitaría 6,7. Filma el disparo, el área y la runa, la runa desde arriba y las cuatro armas en
la mano (`docs/arma_magica/*.gif`, `runa_desde_arriba.png`, `en_la_mano.png`).

## 2026-09-20 (tarde) — mejoras para las armas mágicas

Andy: «también dame mejoras para las armas mágicas». Todo lo que mejora el golpe de un arma ya viajaba en el
hechizo (Filo, Escarcha, Vampirismo, Tormenta…), así que las nuevas son las que tratan del **conjuro**: cada
cuánto, cuántos, adónde va y qué hace la runa mientras está en el suelo. Ocho mejoras y dos sinergias
(120 mejoras y 28 sinergias en total); sección propia en el libro («Báculo y grimorio»).

| Mejora | Va en | Se alimenta con | Al 100 % | Peso |
|---|---|---|---|---|
| **Conjuro veloz** | báculo y grimorio | polvo de piedra luminosa 2 % / piedra luminosa 8 % | conjura un 40 % más rápido (báculo 14 → 8 tics, grimorio 70 → 42) | 3 |
| **Sobrecarga** | báculo y grimorio | bloque de redstone + amatista, 20 % | cada 4.º hechizo sale **más grande y con +100 % de daño**; el 3.º lo avisa con una nota que sube y un aro del color del núcleo a tus pies | 3 |
| **Resonancia** | báculo y grimorio | fragmento de eco + amatista, 25 % | el hechizo **se repite** al 50 % del daño: un segundo proyectil 6 tics después hacia donde mires, o el área que se abre otra vez | 4 |
| **Prisma** | báculo | cristales de prismarina + cristal, 20 % | **abanico de tres**: los dos de los lados, al 60 % | 3 |
| **Buscador** | báculo | ojo de ender + pluma, 25 % | el proyectil **gira hasta 12° por tic** hacia quien te ataca (sólo hostiles: nunca tu caballo ni el aldeano) | 3 |
| **Tinta indeleble** | grimorio | saco de tinta 5 % / tinta luminosa 15 % | la runa dura **6 s más** (12 en total) | 2 |
| **Vórtice** | grimorio | telaraña + perla de ender, 20 % | cada mordisco **arrastra ~0,9 bloques hacia el centro** de la runa | 3 |
| **Santuario** | grimorio | rodaja de sandía reluciente, 10 % | sobre **tu propia runa** te curas medio corazón por segundo y, desde el 50 %, Resistencia | 3 |

- **Enjambre** (Prisma + Buscador): el abanico pasa a **cinco** proyectiles, y todos persiguen.
- **Colapso** (Vórtice + Tinta indeleble): al apagarse, la runa **estalla con el 75 %** del daño con que se abrió —
  después de haber tenido a su presa reunida en el centro todo ese tiempo.

Decisiones que tomé (por si Andy quiere otra cosa):

- El eco de un proyectil **atraviesa el parpadeo de invulnerabilidad** del primero (si no, contra el objetivo al
  que apuntabas no valdría nada); los proyectiles laterales de Prisma **no**, igual que las tres flechas de un
  Multidisparo no cuentan las tres sobre el mismo blanco: un abanico es para un grupo.
- **Ni el área ni la runa empujan.** El daño mágico empuja desde su origen, que aquí es el centro de la runa: el
  área tiraba a su presa fuera de la runa que iba a dejar, y cada mordisco la alejaba más. Ahora lo golpeado
  sigue haciendo lo que hacía — o, con Vórtice, va hacia el centro.
- La runa cuenta su edad y muerde en las decenas; el cliente la hace destellar en las mismas decenas, así que
  el destello sigue siendo el mordisco dure lo que dure con Tinta indeleble.
- Las mejoras nuevas van **al final** del enum (`Upgrade` viaja por la red por su posición).

Test: `checkMagicUpgrades` (en `FORJA_SOLO=magia` y en la pasada completa) comprueba las ocho y las dos parejas
contra los hechizos de verdad: esperas de 8 y 42 tics, 1/3/5 proyectiles, los cuatro golpes de Sobrecarga
(6,7 · 6,7 · 6,7 · 13,4), proyectil + eco = 10,05, un zombi cuatro bloques fuera de la línea al que sólo acierta
el buscador, otro arrastrado de 2,40 a 0,10 del centro de la runa, Resistencia sobre la propia runa y los 6,0 del
Colapso. `filmMagicUpgrades` (sólo en solitario) graba `docs/arma_magica/mejora_*.gif`.

## 2026-09-20 (tarde, 2) — el Bastión: luz, salas y generación

- **La luz es una pasada, no un mueble** (`tools/castillo_luz.py`). Los primeros interiores salieron negros: tres
  lámparas en un salón de 46 bloques. Ahora, con el castillo terminado, se calcula la luz de bloque como la calcula el
  juego y, donde un suelo techado queda por debajo de 6 (3 entre los muertos), se cuelga lo que falte: **aplique** en
  la pared más cercana, **farol con cadena** del techo, **hornacina** tallada en el muro de un pasadizo bajo o
  **farola**. Cada luz se apunta unos bloques hacia dentro de lo oscuro. ~1 470 luces en 45 000 suelos, luz media 8,9 y
  15 suelos a cero: se lee todo, no aparece nada donde no toca y nada está iluminado «bien». Cripta, osario y forja de
  almas llevan faroles de alma. Lo que colgaba de nada (cadenas bajo un tejado a dos aguas) sube hasta el techo.
- **Salas nuevas** (`tools/castillo_salas.py`): biblioteca, scriptorium, armería (dos de las armaduras no son
  soportes), sala de estandartes con la escalera al piso alto, cuartel, biblioteca alta, sala de esgrima (Yunque
  andante), dormitorios, capilla del Yunque, sacristía, comedor y aleaciones.
- **Generación natural**: conjunto `bastion_del_gremio` (muy raro: 140/60 chunks, nunca a menos de 12 chunks de un
  castillo pequeño) y **mapa del Bastión** en el Forjador de nivel maestro. Pendiente de visitar en un mundo normal.
- El test filma el castillo a **32 chunks** (pedido de Andy) y guarda la opción.
- **Torre del homenaje, plantas altas**: trono del Gran Maestre, Consejo de los Nueve, trofeos, sala de mapas,
  aposentos, estudio, contaduría tras su reja, almacenes y el observatorio con el Núcleo estelar; escaleras entre todas
  las plantas, y dos filas de pilares con vigas en cada una. **Recinto bajo por dentro**: taberna «El Yunque Roto»,
  casa de encargos, cuerpo de guardia, caballerizas, talabartería, taller del arquero y polvorín.
- **El Bastión se genera solo, comprobado en un mundo normal** (`FORJA_SOLO=mundo`): localizado como lo localiza el
  mapa, visitado y fotografiado a 32 chunks en taiga nevada, llanura y bosque. De ahí salieron tres arreglos: aire
  despejado 24 bloques sobre toda la planta, un zócalo de roca de 18 bajo foso y muralla, y suelo pisado en vez de
  tierra a cielo abierto para que el mundo no plante un bosque dentro.
