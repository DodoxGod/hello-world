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
| 1b | Arreglos de la revisión: sol bajo el vacío, tumbas que caen con su suelo, tumbas vistas desde el sur, meseta según la semilla, estrellas fugaces y cielo que se mueve | en esta rama |
| 2 | Oricalco, perla de oricalco, marco del portal, portal, retirada de la invocación vieja | pendiente de Andy |
| 3 | Pelea nueva: llegada, fases a 2/3 y 1/3, aprendices en formación, eventos, reforjado estelar, persistencia, estrella de vuelta, revancha y recompensa | diseño aprobado en parte (ver 6); pendiente |

---

## 1. Cómo se llega (entrega 2)

### 1.1 La aleación nueva: el **oricalco**

Un metal de leyenda hecho de **todos** los metales colables propios del mod. Se declara en `forge/Alloys.java`
como las demás aleaciones y se funde en el crisol, que pide a los depósitos de la red (`MeltNetwork`) lo que no cabe
en sus dos huecos, igual que ya hace con cualquier aleación de más de dos ingredientes.

| Dato | Valor |
|---|---|
| Calor | **Forja blanca** (crisol de obsidiana, o aliento de forja en los tubos) |
| Entra | 1 lingote de cada uno de los **17** metales propios de abajo |
| Sale | **4** lingotes de oricalco (5 con el crisol de obsidiana, que da +1 por aleación) |
| Tipo | Metal **solo de colada**, como el acero refractario: no es material de forja, no hace herramientas |

Los 17 metales (todos los `ForgeMaterial` propios que se pueden colar, más el hierro estelar, la placa hueca y la
escoria):

- **Metales base del mod (3):** hierro estelar, placa hueca, escoria.
- **Aleaciones templadas (4):** bronce, latón, peltre, electro.
- **Aleaciones calientes (3):** acero, cinerio, voltaico.
- **Aleaciones fundidas (5):** damasco, acero estelar, obsidiacero, almacero, vidriacero.
- **Aleaciones de forja blanca (2):** solacero, lunacero.

**Fuera, a propósito:**

- el **corazón de forja** y el **acero vivo**: solo salen del propio Herrero Caído, y pedirlos para llegar a él
  sería un círculo;
- el **acero refractario**: es el metal de los moldes, no un material;
- los metales vanilla (hierro, oro, cobre, netherita, diamante, obsidiana…): la aleación es "del mod".

Coste real de una tanda: unos 40 lingotes de metal base repartidos en las 17 aleaciones. Para las 4 perlas hacen
falta 8 lingotes de oricalco, es decir, **2 tandas** en el crisol de obsidiana (10 lingotes, sobran 2).

### 1.2 La **perla de oricalco** (colada sobre una perla de ender)

Se hace en la **mesa de colada**, a la manera del mod:

1. Se pone una **perla de ender** en la mesa como si fuera el molde (es el único objeto que la mesa acepta además de
   moldes y marcos; se añade a `CastingTableBlockEntity.pattern`).
2. La mesa tira de los depósitos como siempre y vierte **2 lingotes de oricalco** sobre ella.
3. Enfría (`COOK = 100` ticks) y sale una **perla de oricalco**. La perla de ender se gasta.

| Dato | Valor |
|---|---|
| Mesa mínima | **Mesa de almas** (el oricalco aguanta como la netherita: `holds` sin límite) |
| Coste | 2 lingotes de oricalco + 1 perla de ender |
| Colada basta o limpia | Da igual: una perla basta sirve igual (no tiene potencial) |
| Se apila | 16 |
| Rareza | Épica |

Textura: la perla de ender vanilla recoloreada al dorado verdoso del oricalco, con un brillo de estrella en el
centro (generador).

### 1.3 El **marco del portal** en el Bastión

El sitio de la fragua apagada, en la Forja Profunda del sótano (rotonda de radio 25, `tools/castillo_sotanos.py`,
`rotunda()`, bloque en el plano (100, −23, 92), pieza `bastion/p_2_0_3.nbt`), deja de invocar al jefe y pasa a ser
el portal:

- El estrado de 3 escalones se queda. Encima, un marco de 5 × 5: en el centro un hueco de 3 × 3 y alrededor 12
  bloques. **4 de ellos**, los del medio de cada lado (N, S, E y O), son **ménsulas estelares**
  (`forja:mensula_estelar`); las 8 esquinas y costados son ladrillo de piedra negra pulida.
- Cada ménsula tiene un hueco para una perla, como el ojo de ender en el portal del End (propiedad `perla`).
- Con clic derecho y una perla de oricalco en la mano, la perla se engasta (sonido de yunque y chispas).
- **Con 4 perlas** el hueco de 3 × 3 se llena de **portal estelar** (`forja:portal_estelar`). Con 3 o menos no pasa
  nada, y la ménsula dice cuántas faltan.
- **Se queda encendido para siempre.** Las perlas no se pueden sacar y las ménsulas son irrompibles
  (`strength -1`), como el marco del End.
- El portal estelar es como el del End: se ve el cielo de la dimensión dentro, y entrar en él te lleva a la
  **plataforma de llegada** de la dimensión (ver 2.4).

**Mundos existentes:** el bloque `forja:fragua_apagada` sigue registrado para que los mundos carguen. Ya no invoca
a nadie: si alguien le da clic derecho, se "abre": se convierte en el marco de 5 × 5 centrado en él, sin perlas, y
dice "La fragua se abre en un marco de estrellas". Los castillos nuevos ya salen con el marco. La fragua apagada de
la estructura vieja `fragua_caida` queda como decoración, con el mismo mensaje.

**Se quita:** la invocación en el mundo normal (`DeadForgeBlock.useWithoutItem` → `FallenSmith.summon`) y la
ofrenda (8 hierro estelar, 4 damasco, estrella del Nether, fragmento de eco). La prueba `oldSummonerNoLongerSummons`
lo comprueba. La rotonda se queda como sala del portal, sin bloqueo de construcción (el bloqueo va con el jefe).

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
- **Estrellas:** por debajo del horizonte se apagan en el resplandor: del todo a −35°.
- **Estrellas:** las 1.500 de vanilla a brillo pleno, y 900 más del mod, de colores (blancas, azules, doradas y
  alguna roja), que titilan despacio.
- **Una franja de nebulosa** (como una vía láctea) de violeta a ámbar, cruzando el cielo. Deriva un poco más
  deprisa que las estrellas, respira (±20 % cada 23 s) y sus nudos más brillantes se deslizan despacio a lo largo.
- **Estrellas fugaces** (Andy, revisión): unas 4 por minuto, al azar, bien por encima del horizonte. Cada una es un
  destello de un cuarto a dos quintos de segundo con una cola corta que se afina.
- **8 constelaciones que dibujan moldes de armas**, repartidas por el cielo: **Espada, Hacha, Martillo, Lanza,
  Escudo, Yunque, Tenazas y Guadaña**. Cada una es un contorno de 8 a 14 estrellas grandes unidas por líneas
  tenues, como el hueco de un molde visto desde arriba.
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
- **Sonido ambiente:** un bucle de viento hueco (el del valle de almas, más grave), y cada poco un sonido suelto:
  un martillo lejano, una campana que resuena, un tintineo de amatista (las estrellas) o escombros que caen.
- **Música:** "El Cementerio entre Estrellas", una lista de pistas vanilla bajadas de tono (el End, "So Below",
  "Echo in the Wind", "Deeper"), con pausas de 3 a 8 minutos. Todo por `sounds.json`, sin audio nuevo.

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

### 3.6 Eventos de la dimensión durante la pelea

Cada **35 a 45 s** la dimensión hace algo, por turnos, y nunca durante una llegada ni un cambio de fase:

1. **Lluvia de hierro estelar** (la de Andy): cae **un meteorito por jugador** con el aviso de siempre (anillo en el
   suelo 34 ticks). Hace el daño de siempre, pero **sin cráter** (la arena no cambia) y deja 3 a 6 de hierro
   estelar. El **pararrayos** funciona igual que en el mundo normal: uno a 12 bloques o menos se lo lleva (y se
   gasta), así que se puede llevar a la pelea y ponerlo **antes** de que empiece (el bloqueo de construcción
   empieza con el jefe).
2. **Molde celeste:** una constelación se cuela en el cielo (el hilo dorado, 3 s de aviso) y su molde se
   **proyecta en el suelo de la arena**: el contorno del arma (de 10 a 14 bloques de largo) se dibuja con
   partículas doradas sobre la obsidiana. A los **3 s** el contorno arde **2 s**: 6 de daño y fuego a quien pise la
   línea (jugadores y aprendices, no al jefe). Obliga a moverse y a leer el cielo.
3. **Tormenta de ceniza:** la bruma se cierra a **20 bloques** durante **12 s** y el viento empuja a todos
   (0,06 bloques por tick hacia un lado fijo, que se avisa con la ceniza). El jefe también ve menos: su alcance de
   persecución baja de 48 a 16. Sirve para despistarle o para perderle de vista.

Con los números en `config/forja.json` (`dimensionEventoCada`, `dimensionMoldeDano`, `dimensionCenizaSegundos`…).
Andy aprobó los tres (respuesta 6).

### 3.7 El Reforjado estelar: su invulnerabilidad, rota por la dimensión (propuesta para Andy)

**Lo que hay hoy** (`FallenSmith.startReforge`/`reforge`): a la mitad de la vida, después de llamar a los
aprendices, vuelve a la forja y **nada le hace daño** mientras reforja (`hurtServer` lo ignora todo si
`reforging > 0`). Enciende **3 ascuas** (bloques de fuego a 5 bloques), cada ascua encendida le cura un 1 % por
segundo, y el reforjado dura **160 ticks** (8 s) o hasta que se apaguen las tres. Es corto y se resuelve a
puñetazos contra el fuego.

**Lo que propongo en la dimensión:** que en esa fase **la dimensión sea el arma**. Él es intocable y lo único que
rompe su escudo es lo que la dimensión hace caer, arder o derramarse.

1. **Empieza:** a la mitad (160 de vida), se arrodilla en el disco de obsidiana llorona del centro, clava el
   martillo y el cielo entero se vuelve hacia él: las constelaciones se encienden en oro a la vez. Queda
   **invulnerable** (como hoy) durante **hasta 20 s** (400 ticks).
2. **Las Brasas estelares:** en lugar de 3 fuegos, **3 + 1 por jugador extra (hasta 6)** brasas flotantes (una
   entidad nueva, sin bloque, así que el bloqueo de construir de la arena no molesta), en un anillo de **9 a 12
   bloques** alrededor de él. Cada una está unida a él por un haz dorado. Mientras quede una:
   - es invulnerable;
   - le cura un **1 % por segundo por brasa** (como hoy);
   - **los golpes de los jugadores no les hacen nada.** Solo las rompe la dimensión.
3. **Tres maneras de romperlas, las tres de la dimensión:**
   - **Hierro estelar templado:** al empezar, la dimensión deja caer **un meteorito por brasa**, con su aviso de 34
     ticks, a entre 10 y 16 bloques de ella. Cada uno deja un **hierro estelar ardiente** (un objeto que quema 10 s
     en la mano: 1 de daño por segundo a quien lo lleve). Llevarlo hasta una brasa y tocarla con él (clic derecho)
     la **rompe** ("temple"). El pararrayos, puesto antes de la pelea, sirve para que caigan más cerca.
   - **Molde celeste redirigido:** cada 8 s durante el reforjado, un molde se proyecta en el suelo **centrado en el
     jugador al que mira el Herrero**, y a los 3 s arde. Una brasa que quede bajo una línea que arde **se rompe**.
     El jugador hace de cebo: se coloca para que el molde pase por las brasas, y tiene que salir de la línea a
     tiempo (6 de daño y fuego si no).
   - **La colada del brasero:** golpear uno de los 4 braseros de los pilares (con un proyectil, o subiendo) lo
     **vuelca**: un chorro de metal fundido baja por el pilar y corre en línea recta hacia el centro durante 3 s,
     1 bloque de ancho, **rompiendo las brasas que toca** y quemando a quien pise (jugadores y aprendices; el metal
     se enfría y desaparece, la arena no cambia). Cada brasero se vuelca una vez por reforjado y se vuelve a
     llenar después. Hay que elegir el brasero cuya línea pase por más brasas.
4. **Si se rompen todas antes de 20 s:** el escudo estalla (la onda morada de fase), y queda **aturdido 5 s**:
   no ataca y recibe **×1,5 de daño**. Es el premio de usar bien la dimensión.
5. **Si se acaba el tiempo con brasas encendidas:** se levanta curado de lo que haya curado y con **Temple** 30 s:
   recibe un **30 % menos** de daño. Es el castigo de no usarla.
6. **Los eventos normales** (3.6) se paran durante el reforjado: la dimensión está ocupada en él.
7. **Una vez por pelea.** Si alguien muere o sale, la persistencia (3.8) guarda también si ya hubo reforjado y cuántas
   brasas quedan.

Números de partida, en `config/forja.json`: `reforjadoTicks` 400, `reforjadoBrasasBase` 3, `reforjadoBrasasMax` 6,
`reforjadoCuraPorBrasa` 0,01, `reforjadoAturdido` 100 ticks, `reforjadoAturdidoDano` 1,5, `reforjadoTemple` 0,3.

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

### 3.10 Revancha: la fragua fría del centro (Andy, respuesta 4)

- **Al morir el Herrero**, en el centro de la arena (sobre el disco de obsidiana llorona) aparece una **fragua
  fría** (`forja:fragua_fria_estelar`, la fragua apagada de siempre pero con un hueco para una perla), junto a la
  estrella de vuelta. No estorba: está en el mismo sitio donde él cae.
- **Se reaviva** con clic derecho llevando encima:

  | Qué | Cuánto | Por qué |
  |---|---|---|
  | Perla de oricalco (la "llave") | 1 | la misma que abre el portal: 2 lingotes de oricalco sobre una perla de ender |
  | Lingotes de oricalco | 2 | la aleación de todos los metales |
  | Hierro estelar | 16 | lo que dejan los meteoritos de la dimensión (un par de peleas o una lluvia) |
  | Corazón de forja | 1 | el que dejó él la vez anterior: se lo devuelves |

  En total, unas **4 tandas de oricalco por revancha menos las 2 de las perlas del portal**: más o menos la mitad
  de lo que costó abrir el portal, más su corazón.
- **Al reavivarla**, la fragua se enciende, el cielo se oscurece 3 s y el Herrero **vuelve a caer del cielo** (3.2),
  con la vida entera y sin aprendices.
- **Recompensa de la revancha:** la de siempre (el corazón de forja que devolviste vuelve a caer, una leyenda, el
  martillo del maestro; el yunque del Herrero solo la primera vez) y **otra Estrella forjada por participante**.
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

- **La armadura sí sube** (Andy, respuesta 5: puede pasar el tope): +1 de armadura y +0,5 de dureza por pieza
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

### Entrega 1b (arreglos de la revisión, esta rama)

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

**Respuestas (revisión de la entrega 1):**

1. **Nombres aprobados:** El Cementerio entre Estrellas, oricalco, perla de oricalco, Estrella forjada.
2. **Oricalco sin corazón de forja ni acero vivo:** aprobado.
3. **Con 10 aprendices, 4 + 6:** lo que sobra se suma al anillo, que pasa a hexágono (ver 3.5).
4. **Revancha:** al morir aparece una fragua fría en el centro de la arena; se reaviva con materiales y una perla
   de oricalco, y cada participante recibe otra Estrella (ver 3.10).
5. **La Estrella puede pasar el tope de armadura** de netherita P4 + 5; `ArmaduraGameTests` se ajusta para las
   piezas estrelladas (ver 4.1).
6. **Los tres eventos aprobados:** meteoritos, molde celeste y tormenta de ceniza, cada 35 a 45 s.
7. **Las forjas hundidas en el terreno están bien.**

**Por decidir:**

1. **Reforjado estelar (3.7):** ¿te gusta que su invulnerabilidad de la mitad solo se rompa con la dimensión
   (hierro estelar templado, molde celeste redirigido y colada del brasero), con aturdido ×1,5 si lo consigues y
   Temple −30 % si no?
2. **Coste de la revancha (3.10):** 1 perla de oricalco, 2 lingotes de oricalco, 16 hierros estelares y el corazón
   de forja. ¿Te parece bien?
3. **Armadura estrellada (4.1):** +1 de armadura y +0,5 de dureza por pieza. ¿Te vale?
