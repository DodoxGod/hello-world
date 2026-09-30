# El Cementerio entre Estrellas: la nueva pelea del Herrero Caído

Diseño completo del pedido de Andy del 2026-09-29. El Herrero Caído deja de despertarse en el sótano del Bastión:
ahora hay que abrir un portal y cruzar a una dimensión propia, que mezcla las dos ideas que eligió Andy: **El Taller
entre Estrellas** (una meseta de obsidiana en el vacío, bajo constelaciones que dibujan moldes de armas) y **El
Cementerio de Herreros** (una llanura de ceniza con miles de armas clavadas como tumbas, y forjas frías).

**Orden de trabajo (Andy, 2026-09-29): paso a paso.** Este documento recoge el diseño entero, pero en la primera
entrega solo se construye **la dimensión y su aspecto** (terreno, cielo y ambiente) y un comando para ir y volver.
El portal, la aleación, la perla, los cambios del jefe, los aprendices, las fases, la llegada, la persistencia y la
recompensa esperan a que Andy revise la dimensión. Cada sección dice en qué entrega va.

| Entrega | Qué lleva | Estado |
|---|---|---|
| 1 | Dimensión: terreno, arena, cielo, niebla, luz, partículas, sonido y música; `/forja dimension` | hecha y revisada por Andy |
| 1b | Arreglos de la revisión: sol bajo el vacío, tumbas que caen con su suelo, tumbas vistas desde el sur, meseta según la semilla, estrellas fugaces y cielo que se mueve | hecha ("la dimensión está súper bien") |
| 1c | Arreglos de la segunda revisión: el tinte del sol sin saltos, los eventos del mundo en este cielo, respiraderos muertos, constelaciones repartidas, ecos lejanos | en esta rama |
| 2 | Oricalco, perla de oricalco, marco del portal, portal, vuelta, retirada de la invocación vieja | en esta rama |
| 3 | Pelea nueva: llegada, fases a 2/3 y 1/3, aprendices en formación, eventos, reforjado estelar, persistencia, estrella de vuelta, revancha y recompensa | diseño aprobado en parte (ver 6); pendiente |

---

## 1. Cómo se llega (entrega 2)

### 1.1 La aleación nueva: el **oricalco** (hecho)

Un metal de leyenda hecho de **todos los metales propios del mod que se pueden volver a hacer** (Andy, revisión de
la entrega 1b: nada que se acabe ni que solo dé el jefe). Está en `forge/Alloys.java`, en `EXTRA` (no es metal de
equipo) y en `FOUNDRY_ONLY`: son 14 ingredientes y la estrella de la mesa de forja tiene 5 puntas, así que solo lo
hace un **crisol en una línea de fundición**. Dos lingotes van al crisol y los otros doce los saca de las cubas de
su línea, como cualquier aleación de más de dos ingredientes (`MeltNetwork`).

| Dato | Valor |
|---|---|
| Calor | **Fundido** (lava, farol de pavesa, o más) |
| Entra | 1 lingote de cada uno de los **14** metales de abajo |
| Sale | **4** lingotes de oricalco (`forja:oricalco`) |
| Tipo | Metal **solo de colada**: va a las cubas y se cuela, pero no es material de forja |

**Los 14 y de dónde salen (todos renovables):**

| Metal | Viene de | Por qué no se acaba |
|---|---|---|
| Hierro estelar | la lluvia de meteoritos (evento del mundo) y los meteoritos de la dimensión | los eventos vuelven cada pocas noches |
| Placa hueca | la Coraza Vacía | reaparece en las ruinas |
| Escoria | la Escoria Viva | reaparece en las pozas de escoria |
| Bronce, latón | cobre, hierro y oro | gólems de hierro, ahogados, piglins zombificados, trueques |
| Peltre | cobre y ladrillo de resina | el creaking da resina sin fin |
| Electro | oro y amatista | las geodas vuelven a brotar |
| Acero | hierro y carbón | carbón vegetal de los árboles, esqueletos wither |
| Cinerio | acero y ascuas | las pavesas vuelven con el fuego |
| Voltaico | latón, redstone y amatista | las brujas dan redstone |
| Acero estelar | acero y hierro estelar | lo de arriba |
| Obsidiacero | acero y obsidiana | la obsidiana se hace con agua y lava |
| Almacero | acero estelar y placa hueca | lo de arriba |
| Vidriacero | obsidiacero y cuarzo | trueque con los piglins |

**Fuera, a propósito:** el **damasco** y el **solacero** (llevan chatarra de netherita, que se acaba), el
**lunacero** (fragmentos de eco, que se acaban), el **acero vivo** y el **corazón de forja** (solo del Herrero), y el
**acero refractario** (es metal de moldes).

Coste: 4 perlas piden 8 lingotes de oricalco, es decir, **2 tandas**.

### 1.2 La **perla de oricalco** (hecho)

En la **mesa de colada**: una **perla de ender** en la mesa, como si fuera un molde (`CastingTableBlockEntity.pearl`), y
oricalco en una cuba de su línea. La mesa vierte **2 lingotes** (`PEARL_COST`), enfría como siempre (`COOK`) y
sale una **perla de oricalco** (`forja:perla_de_oricalco`, épica, de 16 en 16). La perla de ender se gasta. Vale
**cualquier mesa**: es un baño, no una pieza, y no sale basta ni limpia. Una perla solo acepta oricalco, y el
oricalco solo se cuela sobre perlas.

Texturas: el lingote de oro y la perla de ender vanilla, recoloreados al dorado verdoso del oricalco
(`tools/dimension_assets.py`).

### 1.3 El **marco del portal** en el Bastión (hecho)

- **En el castillo:** donde estaba la fragua apagada, en la Forja Profunda (`bastion/p_2_0_3.nbt`, sitio local
  24, 3, 6), ahora hay un **marco de 5 × 5** sobre el estrado: 4 **ménsulas estelares** (`forja:mensula_estelar`),
  una en el medio de cada lado y mirando al hueco, ladrillo de piedra negra pulida en las esquinas, y el hueco de
  3 × 3 vacío. Se cambió la plantilla directamente (y `tools/castillo_sotanos.py`, para que la próxima vez que se
  genere el castillo salga igual).
- **Encenderlo:** clic derecho en una ménsula con una perla de oricalco en la mano; se engasta (sonido de marco del
  End y chispas) y dice cuántas faltan. **Con las 4**, el hueco se llena de **portal estelar**
  (`forja:portal_estelar`). Con 3 no pasa nada.
- **Para siempre:** las ménsulas y el portal no se rompen y las perlas no salen.
- **El portal:** como el del End, una lámina en la que se entra y lleva al momento. Solo lleva jugadores. Te deja en
  la plataforma de llegada de la dimensión y **se apunta dónde volver**: justo fuera del marco, del lado por el que
  entraste, en el primer sitio con suelo y hueco para estar de pie. Se guarda en el jugador (`forja:vuelta_estelar`)
  y sobrevive a salir del juego y a morir.
- **Volver:** detrás de la plataforma de llegada, en (0, 80, −37), hay un **pozo encendido**: el mismo marco con
  sus 4 perlas y el mismo portal. Desde la dimensión te devuelve a donde se apuntó. Si no hay nada apuntado, a tu
  punto de reaparición. En la entrega 3 la estrella que cae al morir el jefe será otra forma de volver.
- **Mundos existentes:** la fragua apagada sigue registrada. Ya **no invoca a nadie**: con clic derecho se abre en el
  marco vacío (el anillo en el estrado, un bloque por debajo de ella) y avisa de que cuatro perlas lo encenderán.
  Vale para los castillos ya generados y para la estructura vieja `fragua_caida`.
- **Se quitó:** la invocación en el mundo normal y la ofrenda (8 hierros estelares, 4 damascos, estrella del Nether
  y fragmento de eco). La prueba `theOldForgeNoLongerSummons` lo comprueba.

---

## 2. La dimensión: **El Cementerio entre Estrellas** (entrega 1)

`forja:cementerio_estelar`. Una meseta sola en el vacío, bajo un cielo de estrellas eterno.

### 2.1 Tipo de dimensión

| Dato | Valor | Por qué |
|---|---|---|
| Alto | y 0 a 256 | como el End: espacio de sobra por debajo para ver el vacío |
| Hora | fija, noche eterna (sin línea de tiempo) | no hay día ni noche: el cielo es siempre de estrellas |
| Cielo | tipo "overworld", pero sin sol ni luna (el mixin del cielo los apaga) | las estrellas vanilla y las del mod |
| Luz del cielo | sí, fría: color `#8A95D6`, factor 0,45 | la llanura se ve azulada a la luz de las estrellas |
| Luz ambiente | 0,1 | lo que no toca el cielo sigue en penumbra |
| Camas y anclas | no sirven (no explotan: solo no dejan dormir) | no se puede fijar el punto de reaparición allí |
| Monstruos | no aparecen solos | la pelea es la que es |
| Lluvia | nunca | |

### 2.2 El generador: barato a propósito

Un generador propio (`world/StarYardGenerator`), **sin ruido**: cada columna se calcula con unas pocas sumas de
senos y un hash, sin muestrear nada. **La meseta depende de la semilla del mundo** (Andy, revisión de la entrega 1):
`world/StarYardLayout` saca de la semilla, una sola vez, unas pocas decenas de números (ver 2.3) y luego cada
columna usa esos números y un hash que lleva la semilla. La arena es la misma en todos los mundos. Todo lo que no es la meseta es aire, que es lo más barato que hay. No tiene
estructuras, así que `/locate` no tiene nada que buscar aquí y no puede congelar el juego; el teletransporte carga
chunks casi vacíos. Medido en la entrega 1 (ver 2.9).

### 2.3 La meseta

Coordenadas con el centro de la arena en (0, 0). La superficie base está en **y = 80**.

| Zona | Radio | Suelo | Qué hay |
|---|---|---|---|
| **Arena** (el Yunque) | 0 a 22 | obsidiana con dibujo | llana, sin nada que estorbe |
| Borde de la arena | 22 a 24 | ladrillo de piedra negra pulida y muro bajo | 4 entradas de 5 de ancho (N, S, E, O) |
| **Explanada** (el Taller) | 24 a 34 | obsidiana y piedra negra | 4 pilares con braseros de metal fundido; plataforma de llegada al norte |
| **Cementerio en filas** | 34 a 72 | ceniza | tumbas en anillos alrededor de la arena, cada 4 bloques |
| **Cementerio disperso** | 72 al borde | ceniza, ceniza prensada, basalto | armas sueltas, campos de fosas y forjas frías |
| **Borde** | unos 108 a 192, según la semilla | cantil de obsidiana | se cae al vacío |

**Lo que cambia con la semilla** (lo demás es fijo):

| Qué | Cómo varía |
|---|---|
| Forma del borde | radio base de 138 a 162, más tres ondas de 2–4, 5–8 y 9–14 lóbulos, de 10–16, 5–9 y 2–5 bloques de alto y giro al azar |
| Ríos | de 2 a 4, repartidos alrededor con ±20° de juego (nunca a menos de 70° uno de otro); cada uno serpentea con dos ondas propias (6–11 y 3–5 bloques) |
| Puentes | uno entre 70 y 82 bloques del centro y otro entre 100 y 120 |
| Islotes | de 5 a 9, de 7 a 18 de radio, a entre 25 y 95 bloques del borde y a y 55–120, sin pisarse |
| Tumbas | separación en las filas de 3,0 a 3,6; sueltas del 4 al 6 %; en fosas del 12 al 20 %; una celda de fosas de cada 4 a 6 |
| Forjas frías | celdas de 22 a 26 bloques, una de cada dos |
| Relieve y manchas | las ondas de la ceniza, las manchas de ceniza prensada y los afloramientos de basalto se estiran y desplazan; las manchas van torcidas por otra onda para que no salgan en filas |
| Cada elección suelta | (qué arma, qué inclinación, grietas del suelo…) lleva la semilla en el hash |

- **La arena no cambia:** su suelo, su muro, los pilares y la plataforma de llegada son los mismos en todos los
  mundos (lo comprueba `theSeedShapesThePlateauButNotTheArena`, que también dibuja los mapas de dos semillas).
- **Relieve:** la arena y la explanada son planas. Desde el radio 34 la ceniza ondula unos ±3 bloques con suavidad,
  y llega entera al radio 54.
- **Por debajo:** la meseta es un cono invertido de 4 a 68 bloques de grueso (más gruesa en el centro), con capas de
  ceniza (2), obsidiana (2 o 3: la "losa del Taller", que se ve como una franja negra en los cantiles), piedra negra
  y basalto, y betas de obsidiana llorona.
- **Islotes:** de 5 a 9 islotes de obsidiana flotan alrededor, a distintas alturas. Algunos llevan una tumba. Son
  lo que se ve a lo lejos.

### 2.4 La arena y la llegada

- **Suelo de la arena:** obsidiana, un disco de obsidiana llorona de radio 3 en el centro (donde caerá el
  Herrero), 8 radios de piedra negra pulida y dos anillos de piedra negra pulida cincelada y ladrillo, a 10 y a 20.
  Todo a y = 80, plano: nada en la arena tapa ni estorba.
- **Muro:** un muro de ladrillo de piedra negra pulida de 1 de alto, en el radio 23, con 4 entradas de 5 de ancho.
- **Pilares:** 4 pilares de 7 de alto en las diagonales (radio 28), con un brasero de metal fundido arriba (luz 15).
  Iluminan el borde de la arena con luz cálida, y el centro queda con la luz fría de las estrellas.
- **Plataforma de llegada:** un círculo de 5 de ladrillo de piedra negra pulida con un anillo de obsidiana llorona,
  en (0, 80, −30), al norte de la arena y mirando hacia ella. Ahí aparece quien cruza el portal (y quien usa
  `/forja dimension`).
- **Sin metal fundido cerca:** no hay metal fundido a menos de 24 bloques del borde de la arena.

### 2.5 El cementerio

- **Tumbas:** armas clavadas en el suelo (`forja:arma_clavada`), cada una un bloque sin colisión con el arma de
  punta, medio hundida. 6 armas: espada, espada negra, hacha, tridente, maza y pico, con las texturas vanilla
  recoloreadas a hierro oxidado y quemado. 4 orientaciones y 3 inclinaciones (0°, 22,5° hacia un lado y hacia el
  otro) por arma.
  - **Se ven bien desde los cuatro lados** (arreglo de la revisión): la cara de atrás de la carta llevaba la misma
    uv que la de delante, y como una cara norte recorre la x al revés, desde el norte el arma salía tumbada. Ahora
    cada cara trasera tiene la uv que pone el mango y la cabeza en el mismo sitio que la delantera.
  - **Caen con su suelo** (arreglo de la revisión): si se quita el bloque de debajo, el arma desaparece sin soltar
    nada, como una antorcha sin pared. Rota a mano tampoco suelta nada. No se puede poner en el aire.
- **En filas (radio 34 a 72):** anillos cada 4 bloques y una tumba cada 3,0–3,6 bloques de anillo (según la
  semilla), mirando a la arena. Falta una de cada 7 (tumbas saqueadas).
- **Dispersas (radio 72 al borde):** del 4 al 6 % de las columnas, y del 12 al 20 % en los "campos de fosas"
  (celdas de 16 × 16, una de cada 4 a 6). Entre **3.000 y 5.100 tumbas** según la semilla (medido en 5 semillas).
  Una de cada cinco tiene un montón de ceniza prensada debajo.
- **Forjas frías:** en celdas de 22 a 26 bloques, una de cada dos tiene una forja en ruinas, lejos de los ríos y de
  la arena (radio 76 al borde menos 14). Salen de 17 a 42 según la semilla. Tres modelos:
  - **Fragua fría:** suelo de 7 × 7 de ladrillo agrietado, pared del fondo rota, un alto horno apagado con su
    chimenea, un yunque dañado y un caldero vacío.
  - **Yunques rotos:** tres yunques (mellado, dañado y entero) alrededor de una piedra de afilar, y dos armas
    clavadas.
  - **Horno hundido:** un ahumador apagado, una hoguera de almas apagada, un montón de bloques de carbón cubiertos
    de ceniza y restos de muro.

  Echan humo y alguna pavesa (partículas), nada más. Son decoración, sin cofres.

- **Respiraderos muertos** (Andy, revisión 1b): de 5 a 9 conos viejos, de donde los herreros sacaban el calor
  para forjar, repartidos por la llanura según la semilla, a más de 70 bloques del borde de la arena y lejos de ríos
  y puentes.
  - Son conos de basalto y piedra negra de 7 a 13 de radio y 6 a 14 de alto, agrietados (toba, basalto liso y
    alguna grieta de magma que brilla poco).
  - Arriba tienen un cráter poco hondo de ceniza prensada y escoria fría (magma).
  - Algunos **humean**: una hoguera enterrada en el centro del cráter.
  - Algunos llevan una **chimenea fría** de ladrillo en la ladera.
  - Otros llevan una **canaleta de cobre** verde que baja por la ladera: por ahí se sacaba el calor.
  - Las forjas frías se apartan de ellos.

### 2.6 El metal fundido (solo decoración)

- **De 2 a 4 ríos** de metal fundido (`forja:metal_fundido`), según la semilla. Nacen en un pilón a 52 bloques del
  centro (una piletita con un chorro que cae de un dintel de obsidiana) y serpentean hasta el borde.
- **Cauce:** 3 de ancho, con el metal un bloque por debajo del suelo. Las dos orillas llevan un **muro** de
  ladrillo de piedra negra pulida (1,5 de alto: no se salta), así que no se puede caer por accidente.
- **Puentes:** dos anillos de puentes (entre 70 y 82, y entre 100 y 120 bloques del centro), de 5 de ancho,
  cerrados con muro por los lados.
- **Cascadas:** donde un río llega al borde, el metal cae por el cantil hacia el vacío y se pierde abajo, a
  y ≈ 4, soltando gotas.
- **Si alguien se mete:** quema como la lava (4 de daño por segundo y fuego) y frena, pero se puede salir andando.
  No se puede romper ni recoger.

### 2.7 El cielo

- **Vacío:** el cielo es casi negro (`#05050C`) por arriba.
- **El fondo del vacío arde** (Andy, 2026-09-29): por debajo del horizonte el cielo pasa del violeta oscuro a un
  **naranja de brasa** justo abajo, como si en el fondo del vacío hubiera lava o un horno. Es un degradado:
  - en el horizonte, violeta oscuro (`#1B1624`), sin brillo;
  - a −30°, granate (`#5A1A12`), a media fuerza;
  - mirando recto abajo, naranja de brasa (`#FF6A1E`), a toda fuerza.
  - **Un sol debajo** (Andy, revisión de la entrega 1: "como si estuvieras encima de un sol"): al fondo, justo
    abajo, un cuerpo enorme y cegador. De fuera a dentro:
    - un halo rojo muy ancho;
    - 28 rayos largos y finos, cada uno de su largo, que parpadean y giran despacio;
    - dos coronas que giran una contra otra;
    - un brillo de calor: 12 manchas de luz que rondan el núcleo y tiemblan;
    - el núcleo, blanco y amarillo, que ciega.

    Todo respira despacio (±8 % cada 7 s).
  - **Se apaga con la altura:** entero hasta 30 bloques por encima de la arena (y = 110) y apagado a 90 por encima
    (y = 170). Tiene que apagarse: el sol cuelga dentro de la distancia que la niebla deja limpia, y desde muy alto
    la meseta queda más lejos que él, así que se dibujaba delante de ella.
  - La **niebla** hace lo mismo: cuanto más miras hacia abajo, más se tiñe de brasa (hasta un 55 % mirando recto
    abajo), y también cuanto más bajo estás (a y = 20 ya es media brasa).
  - **Brasas que suben:** chispas que brillan solas suben despacio desde el fondo (unas 3 por tick alrededor del
    jugador, de 10 a 60 bloques por debajo de él), donde no hay meseta encima o por debajo de y = 8. El cliente lo
    mira en su propio mapa de alturas, porque la forma de la meseta es de la semilla y el cliente no la sabe.
  - **Sin saltos** (Andy, revisión 1b: con una pared delante el cielo era granate y un paso después naranja): el
    tinte de la niebla depende **solo** de cuánto miras hacia abajo y de la altura (`StarChart.emberShare`), y
    además se suaviza en el tiempo (medio segundo). Antes miraba si la cámara estaba sobre el vacío, y un paso fuera
    del borde lo cambiaba de golpe. La prueba `theEmberTintIsSmooth` recorre alturas y ángulos y exige que no salte.
- **Estrellas:** por debajo del horizonte se apagan en el resplandor: del todo a −35°.
- **Estrellas:** las 1.500 de vanilla a brillo pleno, y 900 más del mod, de colores (blancas, azules, doradas y
  alguna roja), que titilan despacio.
- **Una franja de nebulosa** (como una vía láctea) de violeta a ámbar, cruzando el cielo. Deriva un poco más
  deprisa que las estrellas, respira (±20 % cada 23 s) y sus nudos más brillantes se deslizan despacio a lo largo.
- **Estrellas fugaces** (Andy, revisión): unas 4 por minuto, al azar, bien por encima del horizonte. Cada una es un
  destello de un cuarto a dos quintos de segundo con una cola corta que se afina.
- **8 constelaciones que dibujan moldes de armas**: **Espada, Hacha, Martillo, Lanza, Escudo, Yunque, Tenazas y
  Guadaña**. Cada una es un contorno de 8 a 14 estrellas grandes unidas por líneas tenues, como el hueco de un
  molde visto desde arriba.
  - **Repartidas por todo el giro** (Andy, revisión 1b: había ratos sin ninguna): el cielo gira alrededor del eje
    norte-sur, así que están a 45° una de otra alrededor de ese giro, alternando un poco al norte y al sur
    (`world/StarChart`).
  - En cualquier momento hay **al menos 3 a 15° o más sobre el horizonte**. La prueba `theConstellationsAreAlwaysUp`
    lo mira grado a grado en una vuelta entera.
- **La colada del cielo:** cada 40 segundos una constelación "se cuela": un hilo de metal dorado recorre sus líneas
  de un extremo a otro en 6 s, se queda encendida 4 s y se enfría. En la pelea (entrega 3) es el aviso del evento
  *Molde celeste*.
- **El cielo se mueve** (Andy, revisión): estrellas, nebulosa y constelaciones giran alrededor del eje norte-sur,
  **una vuelta cada 12 minutos** (medio grado por segundo; antes era cada 40 minutos y no se notaba). Quieto y
  mirando arriba se ve moverse; en 10 s la hoja de contactos lo muestra.
- **Día o noche:** no hay. Es siempre la misma noche; lo que cambia es la colada, el giro y las estrellas fugaces.

### 2.8 Ambiente

- **Niebla:** una bruma de ceniza violeta oscura (`#1B1624`) que empieza a 48 bloques y cierra a 256. La arena y la
  explanada se ven limpias; el borde y los islotes, velados. En altura (y > 110) la bruma se abre.
- **Luz:** fría de estrellas en todo; cálida (luz de bloque tintada de naranja) junto al metal fundido y los
  braseros.
- **Partículas:** ceniza del mod que cae y deriva por toda la llanura; chispas y gotas en el metal fundido y las
  cascadas; humo en las forjas frías.
- **Más ceniza cuanto más bajo** (Andy, 2026-09-29): la ceniza del ambiente es ligera a la altura de la arena
  (y = 80) y se espesa al bajar: el doble a y = 60, cinco veces a y = 40 y el máximo (8 veces) a y = 20 o menos,
  que es donde están los islotes bajos. Asomado al borde también se ve más ceniza por debajo de la meseta que por
  encima. Todo en el cliente (`StarYardSky`): no cuesta nada al servidor.
- **Sonido ambiente:** un bucle de viento hueco (el del valle de almas, más grave).
- **Ecos de un tiempo ido** (Andy, revisión 1b): cada 8 a 25 s suena algo **lejos**, en un punto al azar a 40–80
  bloques: un martillo en el yunque, una campana, una piedra de afilar, una mesa de herrero, una cadena, un
  tintineo de amatista o escombros.
  - Suena grave (tono 0,5 a 0,75) y apagado por la distancia.
  - **Resuena** 2 o 3 veces más, a los 8, 18 y 30 ticks, cada vez más flojo (×0,55) y un poco más grave, rebotando
    algo más lejos y a un lado, como la cola de un salón enorme.
  - Minecraft no tiene filtro de paso bajo; se usan sonidos que ya resuenan y la distancia hace el resto.
  - Solo en la dimensión (`StarYardSky`, en el cliente).
- **Música:** "El Cementerio entre Estrellas", una lista de pistas vanilla bajadas de tono (el End, "So Below",
  "Echo in the Wind", "Deeper"), con pausas de 3 a 8 minutos. Todo por `sounds.json`, sin audio nuevo.

- **Los eventos del mundo también aquí** (Andy, revisión 1b): luna de sangre, eclipse, lluvia de meteoritos,
  aurora, niebla de almas y los demás tiñen este cielo, sus estrellas, la niebla y la luz, y traen sus partículas,
  estelas y dibujos (aurora, sigilo, horizonte en llamas).
  - **Nunca se dibuja una luna ni un sol**: la luna de sangre, la marea viva y el eclipse, que se dibujan sobre
    uno, aquí dejan solo el resto.
  - Como aquí siempre es de noche, el evento se ve con la fuerza de la medianoche.

### 2.9 Para probar: `/forja dimension`

- `/forja dimension` te lleva a la plataforma de llegada, mirando a la arena. Guarda desde dónde viniste.
- `/forja dimension volver` te devuelve a donde estabas (o a tu punto de reaparición si no hay nada guardado).
- Permiso de administrador (nivel 2), como el resto de `/forja`.

---

## 3. La pelea (entrega 3)

### 3.1 Se mantienen

Golpe, onda, garfio, lluvia de estrellas (una estrella por llamada, con su aviso de 20 ticks), "La forja
reclama", el daño de un tercio de lo que no es un jugador (`jefeDanoAjeno` = 0,333) y el bloqueo de construir y
romper a menos de 32 bloques del jefe vivo. La vida sigue en 320.

### 3.2 La llegada: cae del cielo

Cuando un jugador entra en la dimensión y no hay pelea en curso:

1. A los **3 s**, una estrella se desprende del cielo encima de la arena (la constelación del Martillo se apaga).
2. **Cae durante 2 s** (40 ticks) desde y = 200 hasta el disco central, con estela de chispas, fuego y varillas
   del End, y el cielo se aclara un momento.
3. **Impacto:** onda de choque morada (la de `ShockwaveFx`), temblor de pantalla, sonido de yunque y explosión, sin
   daño. El Herrero se levanta del cráter (sin cráter de verdad: la arena no cambia).
4. **1 s después** aparece la barra y empieza la pelea. Mientras cae es invulnerable.

### 3.3 Fases: a 2/3 y a 1/3

| Fase | Vida | Qué cambia |
|---|---|---|
| 1 | 100 % a 66,7 % (320 a 213) | golpe, onda, "La forja reclama". **Sin aprendices.** |
| 2 | 66,7 % a 33,3 % (213 a 107) | primera oleada; se suma el garfio; **a la mitad (160), el Reforjado estelar** (3.7) |
| 3 | 33,3 % a 0 | segunda oleada; lluvia de estrellas, yunques del final |

Las fases viejas (75 %, 50 % y 25 %) desaparecen. Una fase solo se pasa una vez, aunque se cure (no se cura).

### 3.4 La animación de cambio de fase

Al cruzar 2/3 y 1/3:

1. El Herrero clava el martillo (la animación de llamar a los aprendices) y queda **invulnerable 3 s**.
2. Onda morada de fase (la de hoy).
3. **Los aprendices salen de la tierra, como zombis:** aparecen 2,2 bloques bajo el suelo en sus puestos y suben
   hasta la superficie en **40 ticks** (2 s), soltando partículas del bloque que atraviesan y con sonido de tierra
   removida (el de excavar del warden, más agudo). **Mientras suben no se les puede dañar** ni empujar, y no atacan.
4. Salen escalonados: el anillo de dentro primero y cada anillo 8 ticks después del anterior.

### 3.5 Los aprendices: cuántos y dónde

- **Ninguno al empezar.**
- **Cuántos por oleada:** `4 + 3 × (jugadores en la dimensión − 1)`. Se cuentan los jugadores vivos en la
  dimensión al empezar la oleada.
- **Dónde:** polígonos regulares concéntricos alrededor del jefe:
  - el anillo de dentro es un **cuadrado** (4), a 3 bloques;
  - cada anillo siguiente tiene **un vértice más** (pentágono, hexágono…), 2 bloques más afuera (5, 7, 9…);
  - se llenan de dentro afuera;
  - lo que sobra en el último anillo forma **su propio polígono regular, más pequeño**, en ese mismo radio;
  - **pero si sobran 1 o 2** (que no son un polígono), se suman al anillo anterior, que crece: con 10, el
    pentágono pasa a **hexágono** y queda 4 + 6 (Andy, respuesta 3). Con 7 sigue siendo cuadrado + triángulo.
- **Giro:** el primer vértice de cada anillo apunta al sur (+Z). Los anillos impares se giran medio lado para que
  no queden alineados con los de dentro.

| Jugadores | Aprendices | Anillos | Posiciones (x, z) respecto al jefe |
|---|---|---|---|
| 1 | 4 | cuadrado | (0, 3), (3, 0), (0, −3), (−3, 0) |
| 2 | 7 | cuadrado + **triángulo** | lo anterior + (4,3, 2,5), (0, −5), (−4,3, 2,5) |
| 3 | 10 | cuadrado + **hexágono** | cuadrado + (2,5, 4,3), (5, 0), (2,5, −4,3), (−2,5, −4,3), (−5, 0), (−2,5, 4,3) |
| 4 | 13 | cuadrado + pentágono + **cuadrado** | cuadrado + (2,9, 4), (4,8, −1,5), (0, −5), (−4,8, −1,5), (−2,9, 4) + (0, 7), (7, 0), (0, −7), (−7, 0) |

![Formaciones](herrero_dimension_formaciones.png)


Si un puesto cae en un bloque sólido o fuera de la arena, el aprendiz sale en el punto libre más cercano del
mismo anillo.

### 3.6 Las constelaciones en la pelea (propuesta para Andy)

Andy (revisión 1b): durante la pelea **las constelaciones se encienden**. Cada una de las 8 lanza **un evento
suyo**, y el **color** en que se enciende dice **lo fuerte** que es. Los tres eventos aprobados (meteoritos, molde
celeste y tormenta de ceniza) entran en este sistema.

**Cómo avisa:**

1. Se elige una constelación de las que están a **15° o más** sobre el horizonte. Siempre hay al menos 3, por
   `StarChart`.
2. Sus líneas se encienden en su color (1 s).
3. La **colada** de oro recorre el contorno en **3 s**, como la de ahora, con un tintineo que sube.
4. Al acabar la colada, el evento cae sobre la arena.
5. En el suelo, el aviso propio de cada evento (anillos, líneas) aparece al empezar la colada: da 3 s para
   moverse.

**Cada cuánto:** uno cada **30 a 40 s**. Nunca durante la caída del jefe, un cambio de fase ni el Reforjado
estelar, salvo el Martillo (ver 3.7).

**Los 5 colores** (del más flojo al más fuerte):

| Color | Fuerza (×) | Fase 1 | Fase 2 | Fase 3 |
|---|---|---|---|---|
| Plata | 0,6 | 50 % | 25 % | 10 % |
| Cobre | 0,8 | 35 % | 30 % | 20 % |
| Oro | 1,0 | 15 % | 25 % | 30 % |
| Azul estelar | 1,3 | — | 20 % | 25 % |
| Carmesí | 1,7 | — | — | 15 % |

**Las 8 constelaciones y su evento** (los números son a fuerza ×1; se multiplican por el color):

| Constelación | Evento | Qué hace |
|---|---|---|
| **Espada** | *Tajo celeste* (el molde celeste) | El contorno de una espada de 16 bloques se dibuja en el suelo cruzando al jugador que el jefe mira. A los 3 s arde 2 s: **6 de daño** y fuego a quien lo pise (jugadores y aprendices). Si la línea pasa por un pilar, **vuelca su brasero** (ver 3.7). |
| **Hacha** | *Hachazo* | Un cuarto de círculo de radio 12 desde el jefe hacia su objetivo se marca en el suelo; a los 3 s cae: **7 de daño** y empuje fuera. |
| **Martillo** | *Lluvia de hierro estelar* (los meteoritos) | Cae **1 meteorito por jugador**, con el aviso de siempre. Plata y cobre: 1 por jugador; oro y azul: 2; carmesí: 3. Daño de meteorito × fuerza, sin cráter. Deja **hierro estelar**. El pararrayos funciona. |
| **Lanza** | *Lanzas de luz* | Cinco lanzas de luz (plata 3, carmesí 8) caen en puntos marcados alrededor de los jugadores: aviso de 1,5 s y **5 de daño** en 1 bloque. Rápido y preciso. |
| **Escudo** | *Égida* | El jefe gana un escudo de luz que absorbe **20 de daño** (plata 12, carmesí 34) durante 10 s. Es el único evento que le ayuda a él. |
| **Yunque** | *Yunque caído* | Un yunque enorme cae donde está el objetivo, con un anillo de radio 3 durante 2 s: **10 de daño**. |
| **Tenazas** | *Tenazas* | Arrastra a todos los jugadores **4 bloques** hacia el jefe (carmesí 7), sin daño. Le pone la onda a tiro. |
| **Guadaña** | *Tormenta de ceniza* | La niebla se cierra a 20 bloques durante **8 s** (carmesí 14) con viento de 0,06 bloques por tick (× fuerza). El jefe ve menos: persigue hasta 16 bloques en vez de 48. |

Números de partida en `config/forja.json` (`constelacionCada`, `constelacionFuerzas`, `constelacionPesos`…). Cada
evento, con su aviso en el suelo y su color, se puede ver en el cielo desde cualquier punto de la arena.

### 3.7 El Reforjado estelar (aprobado por Andy, con cambios)

**Lo que hay hoy** (`FallenSmith.startReforge`/`reforge`):
- A la mitad de la vida, tras llamar a los aprendices, vuelve a la forja y **nada le hace daño** mientras reforja
  (`hurtServer` lo ignora todo si `reforging > 0`).
- Enciende **3 ascuas** (fuego a 5 bloques), y cada una le cura un 1 % por segundo.
- Dura 160 ticks o hasta que se apagan.

**En la dimensión** (Andy, respuesta 1: inmortal, **sin tiempo**, hasta que le quiten el fuego):

1. **Empieza** a la mitad de la vida (160): se arrodilla en el disco del centro, clava el martillo y enciende su
   **fuego de forja**: **3 + 1 Brasas estelares por jugador extra (hasta 6)**, en un anillo de 9 a 12 bloques
   alrededor de él, unidas a él por haces dorados.
2. **Es inmortal** mientras quede una brasa, **sin límite de tiempo**. No se cura (ya no hace falta: no pasa nada
   hasta que se rompan). Los aprendices y los golpes normales siguen.
3. **Lo único que apaga las brasas es la colada del brasero:** golpear uno de los 4 braseros de los pilares (un
   proyectil, o subiendo) lo **vuelca**. Un chorro de metal fundido baja por el pilar y corre en línea recta hacia
   el centro durante 3 s, de 1 bloque de ancho. **Apaga todas las brasas que toca** y quema a quien lo pise (el
   metal se enfría y desaparece; la arena no cambia). Hay que elegir el brasero cuya línea pase por más brasas.
4. **Los braseros se vacían:** cada uno se vuelca **una vez** y queda vacío. Se rellena de dos maneras, que son
   las otras dos ideas convertidas en ayudas:
   - **Con hierro estelar:** clic derecho en la base del pilar con **4 hierros estelares**. Durante el Reforjado la
     constelación del **Martillo** lanza su lluvia cada 20 s (solo ella), para que siempre haya hierro.
   - **Con el Tajo celeste:** si la línea ardiendo de la Espada cruza un pilar lleno, **lo vuelca sola**: un golpe de
     suerte, o de ingenio, si el jugador cebo se coloca bien.
5. **Cuando se apaga la última brasa:** el escudo estalla (la onda morada de fase) y queda **aturdido 5 s**: no
   ataca y recibe **×1,5 de daño**. Es el premio.
6. **Ya no hay castigo de Temple** ni tiempo que se acabe (Andy quitó el límite).
7. **Una vez por pelea.** La persistencia (3.8) guarda si hubo reforjado, qué brasas quedan y qué braseros están
   vacíos.

Números de partida: `reforjadoBrasasBase` 3, `reforjadoBrasasMax` 6, `reforjadoRellenoHierro` 4,
`reforjadoMartilloCada` 400 ticks, `reforjadoAturdido` 100 ticks, `reforjadoAturdidoDano` 1,5.

### 3.8 Morir y volver

- Un jugador que muere **reaparece en su punto normal** (su cama o el punto de aparición del mundo), porque en la
  dimensión no se puede fijar otro.
- Puede volver a entrar: el portal sigue encendido.
- **La pelea se queda exactamente como estaba:** la vida del jefe, su fase, las oleadas que ya salieron y los
  aprendices vivos. **No se cura** (salvo por sus brasas durante el Reforjado estelar, como hoy).
- **Se para mientras no haya ningún jugador en la dimensión:** no se mueve, no ataca, no cuenta esperas ni
  eventos. En cuanto entra alguien, sigue (sin volver a caer del cielo).
- Lo guarda un `SavedData` de la dimensión: estado (esperando, en pelea, derrotado), el UUID del jefe, la fase, las
  oleadas hechas, los participantes (todo jugador que haya estado en la dimensión con la pelea en curso) y, por si
  el jefe se perdiera, su vida. Sobrevive a guardar y cargar el mundo.
- Si alguien sale por la estrella de vuelta o se desconecta, cuenta igual que morir.

### 3.9 Cuando muere: la estrella de vuelta

- Al morir, **una estrella cae del cielo** (la misma caída de la llegada, pero blanca y dorada) en el centro de la
  arena, 3 s después.
- Donde cae queda la **Estrella de vuelta** (`forja:estrella_de_vuelta`): un bloque que brilla, con un haz como un
  faro. Con clic derecho te lleva de vuelta **al portal por el que entraste** (se guarda por jugador; si no hay,
  a tu punto de reaparición).
- Se queda en la arena hasta que llegue la próxima pelea. Los aprendices que queden se deshacen en ceniza.

### 3.10 Revancha: la fragua fría del centro (Andy, respuesta 4; coste renovable)

- **Al morir el Herrero**, en el centro de la arena (sobre el disco de obsidiana llorona) aparece una **fragua
  fría** (`forja:fragua_fria_estelar`), junto a la estrella de vuelta.
- **Se reaviva** con clic derecho llevando encima (Andy: **nada que solo dé el jefe ni nada que se acabe**; si se
  perdiera, nunca se podría volver a llamar):

  | Qué | Cuánto | De dónde sale, sin fin |
  |---|---|---|
  | Perla de oricalco (la llave) | 1 | 2 lingotes de oricalco sobre una perla de ender (endermans) |
  | Lingotes de oricalco | 3 | la aleación de los 14 metales renovables (1.1) |
  | Estrella del Nether | 1 | el Wither, que se puede invocar siempre (calaveras de esqueleto wither) |
  | Hierro estelar | 16 | los meteoritos del mundo y de la dimensión |

  En total, 5 lingotes de oricalco (algo más de una tanda), un Wither y una lluvia de meteoritos. Es tan difícil
  como el corazón de forja que se pedía antes, pero todo se puede volver a conseguir.
- **Al reavivarla**, la fragua se enciende, el cielo se oscurece 3 s y el Herrero **vuelve a caer del cielo** (3.2),
  con la vida entera y sin aprendices.
- **Recompensa de la revancha:** la de siempre (corazón de forja, una leyenda, el martillo del maestro; el yunque del
  Herrero solo la primera vez) y **otra Estrella forjada por participante**.
- **Límite:** una Estrella por pieza, así que repetirla solo sirve para estrellar más piezas.

---

## 4. La recompensa: la **Estrella forjada** (entrega 3)

Lo que da hoy (corazón de forja, una leyenda, el yunque del Herrero y el martillo del maestro) se queda. Además, a
**cada jugador que participó** (ver 3.8) le cae **una Estrella forjada** (`forja:estrella_forjada`), a sus pies o
en el inventario si está en la dimensión, y guardada para cuando entre si no estaba.

### 4.1 Qué hace

Se usa en la **forja mayor** sobre **una pieza terminada** (arma, herramienta, armadura o arma mágica). Una por
pieza, para siempre (componente `ESTRELLADA`):

| Qué | Antes (lo mejor de hoy) | Con la Estrella |
|---|---|---|
| Tope de potencial | 100 | **125** |
| Potencial de la pieza | el que tenga | **+25** (sin pasar de 125) |
| Carga (puntos de mejora) | 20 a potencial 100 | **26** a potencial 125 (+30 %) |
| Bono de daño del material (armas y herramientas) | corazón 4,5 | ×1,12: **5,04** |
| Durabilidad | corazón 2.400 | ×1,5: **3.600** |
| Velocidad de minado (herramientas) | la del material | ×1,12 |
| Armadura | la del material | **+1 de armadura y +0,5 de dureza por pieza** (juego entero: +4 y +2) |
| Aspecto | | un brillo de estrellas en la pieza y el nombre en dorado |

- **La armadura sí sube** (Andy, respuestas 5 y, en la revisión 1b, 3: aprobado): +1 de armadura y +0,5 de dureza por pieza
  estrellada, además de la carga y la durabilidad. Cuánto para de más un juego entero se medirá con
  `ArmaduraGameTests` al construirlo (hoy el de corazón para un 86,0 % del golpe contundente al torso).
- **`ArmaduraGameTests` cambia:** el tope sigue en netherita P4 + 5 puntos para las piezas sin estrella; para un
  juego con estrellas el tope pasa a **netherita P4 + 5 + 2 por pieza estrellada** (+13 con las 4), y una prueba
  nueva comprueba que un juego estrellado queda por encima del mismo sin estrellar y por debajo de ese tope.
- **Cuánto es:** una espada de corazón perfecta pasa de 4,5 a 5,04 de bono y de 20 a 26 puntos de mejora. Con esos
  6 puntos caben una o dos mejoras más (una de peso 4 y una de 2, o tres de 2). Es un salto claro sobre lo mejor que
  hay, pero no multiplica: el mismo arma, un 20 a 30 % más fuerte en total.
- **No se apila:** una sola Estrella por pieza, y no sirve sobre piezas que no sean forjadas del mod.

### 4.2 Textura

Una estrella de cinco puntas de oro y oricalco, hecha con el generador a partir de la estrella del Nether vanilla
recoloreada.

---

## 5. Pruebas previstas

### Entrega 1c y entrega 2 (esta rama)

- **Servidor:**
  - `DimensionGameTests.theConstellationsAreAlwaysUp`: al menos 3 constelaciones a 15° o más en todo el giro.
  - `theEmberTintIsSmooth`: el tinte no salta con la altura ni el ángulo.
  - `everyPlateauHasItsDeadVents`: respiraderos en las 5 semillas, lejos de la arena y de los ríos, y alzados.
  - `PortalGameTests`:
    - el oricalco sale de los 14 metales a calor de fundición, y no si falta uno o está frío; no lleva nada que se
      acabe; las cubas lo guardan;
    - la perla se cuela en una mesa (2 lingotes);
    - 4 perlas encienden el hueco entero y 3 no; las ménsulas no se rompen;
    - la fragua vieja ya no invoca, aunque se lleve la ofrenda entera, y se abre en el marco;
    - la plantilla del castillo tiene el marco (4 ménsulas, ninguna fragua);
    - la vuelta se apunta fuera del marco, del lado por el que se entró, y lleva allí.
- **Cliente (`FORJA_SOLO=dimension`), con clics de verdad:**
  - una fragua apagada en su estrado: clic derecho, y se abre el marco;
  - 3 perlas puestas a mano, que no lo encienden, y la cuarta, que sí;
  - se entra pisando el portal y se llega a la plataforma; se vuelve por el pozo y se queda a 3,6 bloques del
    marco.
  - Más: el sol con un muro delante y sin él, un respiradero y su cráter, el cielo a 0, 3 y 6 minutos, y tres
    eventos del mundo en este cielo (aurora, luna de sangre, meteoritos).

### Entrega 1b (arreglos de la revisión)

- **Servidor (`DimensionGameTests`)**, ahora en 5 semillas: arena llana y despejada en todas, sin metal fundido
  cerca de la arena en ninguna, tumbas (3.014 a 5.099), forjas (17 a 42), cascadas y al menos 5 islotes en todas;
  `theSeedShapesThePlateauButNotTheArena` (borde, ríos, islotes y forjas cambian; la arena no; la misma semilla da
  las mismas columnas; dibuja los mapas de las semillas 1 y 42); `aGraveFallsWithItsGroundAndDropsNothing`.
- **Cliente (`FORJA_SOLO=dimension`)**, en dos mundos con semillas "cementerio-uno" y "cementerio-dos":
  - el sol visto desde el borde, recto abajo y desde debajo de la meseta;
  - el cielo a los 0 y a los 10 s, y estrellas fugaces;
  - las 72 armas clavadas (6 armas × 3 inclinaciones × 4 orientaciones) vistas desde el norte, el sur, el este y
    el oeste;
  - un arma sobre un bloque de ceniza, y después de quitar la ceniza (comprueba que no queda ni el arma ni un
    objeto);
  - las mismas vistas en las dos semillas, y la meseta vista desde arriba en las dos.

  Hojas en `E:\IA\Claude\Forja_capturas_mejoras\dimension_herrero\entrega_1b\`.

### Entrega 1

- **Servidor (`DimensionGameTests`):** el servidor de pruebas no carga las dimensiones de los datapacks, así que
  aquí se comprueba el generador directamente (es una función pura de la columna):
  - el tipo de dimensión, el bioma y el generador están registrados, y existe `/forja dimension volver`;
  - la arena es plana y sin obstáculos, y la plataforma de llegada es firme;
  - no hay metal fundido a menos de 24 bloques de la arena (salvo los braseros, a 7 de alto);
  - hay entre 2.500 y 6.000 tumbas (salen 4.021), entre 20 y 60 forjas frías (37) y cascadas por el borde;
  - las columnas de 400 chunks se calculan en menos de 3 s (0,07 s medidos).
- **Cliente (`FORJA_SOLO=dimension`):** entra con `/forja dimension` como jugador, comprueba que está en la
  plataforma de llegada, saca 22 capturas y vuelve con `/forja dimension volver`: llegada, arena, arena desde
  arriba, tumbas, la colada de una constelación, las constelaciones, un río con su puente, un manantial, una
  cascada, una forja fría, **el vacío mirado desde el borde y recto abajo**, la meseta desde abajo, la vista de
  lejos desde dos islotes y desde lo alto, y cuatro vistas sin niebla. Hoja de contactos en
  `E:\IA\Claude\Forja_capturas_mejoras\dimension_herrero\hoja_de_contactos.jpg`.

### Entregas 2 y 3

- **Servidor:**
  - el oricalco y la perla se cuelan;
  - 4 perlas encienden el marco y 3 no;
  - el portal lleva a la dimensión;
  - cuántos aprendices y en qué puestos, de 1 a 4 jugadores;
  - fases solo a 2/3 y a 1/3;
  - la pelea se conserva tras morir y tras guardar y cargar;
  - la estrella de vuelta cae al morir el jefe;
  - la recompensa cae una vez por participante;
  - la fragua vieja ya no invoca.
- **Cliente:** encender el portal con clics de verdad, cruzar, ver caer la estrella, un cambio de fase con los
  aprendices saliendo de la tierra, matarlo (con comandos para acelerar) y volver.

---

## 6. Respuestas de Andy y lo que queda por decidir

**Primera revisión (entrega 1):**

1. **Nombres aprobados:** El Cementerio entre Estrellas, oricalco, perla de oricalco, Estrella forjada.
2. **Oricalco sin corazón de forja ni acero vivo:** aprobado. En la revisión 1b se amplió: **todo renovable**, así
   que también quedan fuera el damasco, el solacero y el lunacero (1.1).
3. **Con 10 aprendices, 4 + 6:** lo que sobra se suma al anillo, que pasa a hexágono (3.5).
4. **Revancha con fragua fría en el centro:** aprobada; el coste ya no lleva el corazón de forja (3.10).
5. **La Estrella puede pasar el tope de armadura** (4.1).
6. **Los tres eventos aprobados** y ahora dentro de las constelaciones (3.6).
7. **Forjas hundidas:** bien.

**Segunda revisión (entrega 1b):**

1. **Reforjado estelar aprobado** con cambios: inmortal sin tiempo hasta que la colada del brasero le apague el
   fuego; el hierro y el Tajo celeste pasan a ser ayudas (rellenar o volcar braseros); aturdido 5 s ×1,5 al final
   (3.7).
2. **Revancha sin nada que dé solo el jefe:** perla + 3 oricalcos + estrella del Nether + 16 hierros estelares (3.10).
3. **Armadura estrellada** (+1 y +0,5 por pieza): aprobada.
4. **Constelaciones en la pelea:** diseñadas en 3.6.

**Por decidir:**

1. **Las 8 constelaciones y los 5 colores (3.6):** ¿te gustan los eventos, las fuerzas (×0,6 a ×1,7), los
   repartos por fase y que salga uno cada 30 a 40 s?
2. **Égida (Escudo)** es el único evento que ayuda al jefe. ¿Lo dejas o lo cambias por otro que castigue?
3. **Rellenar un brasero con 4 hierros estelares (3.7):** ¿es un buen precio?
4. **Oricalco solo en la fundición (1.1):** con 14 ingredientes, hace falta una línea con doce cubas. ¿Te vale, o
   prefieres una receta en dos pasos (dos "oricalcos en bruto" de 7 metales cada uno)?
