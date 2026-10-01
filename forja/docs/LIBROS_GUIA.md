# Libros de la guía: de un tomo de 283 páginas a una estantería

Diseño para revisar. No se ha tocado código. Petición de Andy (2026-09-29):

> "haz varios libros que expliquen el mod poco a poco. Ver un libro que tiene más de 200 páginas termina asustando,
> y no necesitas saber todo al momento 0 del juego."

Correcciones de Andy durante el diseño, ya incluidas:

- La magia va en el primer libro de combate ("es un golpe muy sencillo"), y los enemigos también (amenaza,
  veteranos, élites, IA y eventos). Es **un solo libro de combate**, más grande que los demás, bien seccionado y con
  un índice fuerte. Desaparecen los libros sueltos "Magia y maná" y "Los enemigos".
- Hay un **libro de inicio**, el que se da al entrar. Es corto y amable: cómo empezar, de qué va Forja, el camino
  del herrero y **cómo conseguir cada uno de los otros libros** (receta o condición, con dibujos). Es el centro que
  apunta a todo lo demás.

Maqueta de las portadas y la estantería: `E:\IA\Claude\Forja_capturas_mejoras\libros\propuesta.png`.

### Respuestas de Andy a la primera revisión (2026-09-29), ya aplicadas en este documento

1. **G** abre la **biblioteca** solo si llevas el Cuaderno del aprendiz. Sin él, G abre el libro de Forja que
   tengas en la mano; con las manos vacías no hace nada y lo dice.
2. **Catálogo:** no es una pantalla aparte con buscador. Es una sección con separador y pestañas dentro de la
   biblioteca: Objetos y piezas, Materiales y rasgos, Mejoras y colores.
3. **Clases:** se abren la primera vez que **abres el libro de Clases**. Antes, K no hace nada más que decir
   "lee el libro de clases". Se construye con el libro V.
4. **El libro II** se llama **"El arte del combate"**. Se fabrica como todos.
5. **El camino** crece solo con un paso: **técnica** (el 10), después de la mesa mayor. No hay pasos de portal ni
   de Herrero.
6. **El tomo completo** se queda como objeto **solo de creativo**.
7. **Bestiario:** fichas en sombra hasta que conoces al monstruo (con el libro II).
8. **Todos los libros se fabrican, siempre**, también la primera vez. No hay libros gratis salvo el Cuaderno que se
   da al entrar, y el Cuaderno también se puede fabricar si se pierde. Las recetas se **aprenden** con el progreso,
   y el Cuaderno enseña cada receta y cuándo se aprende. El cambio de diseño está en 2.2.
9. **La fragua del Nether y el castillo del Guardián de Cuño** se quedan como están. El libro los describe tal como
   son hoy: la fragua de la ruina del Nether abre un marco de portal como el del Bastión, y el castillo tiene su
   propio jefe.
10. **El Forjador vende el libro VI** en su nivel 3.
11. **Orden de construcción:** primero el Cuaderno, el libro I (El yunque) y la biblioteca. En la misma pasada se
    arreglan los textos anticuados de 1.3 y el título que no cabe (1.4). Luego los demás libros, en orden, con una
    entrega por libro.

---

## 1. Auditoría de la guía actual

### 1.1 Cómo se midió

Las páginas no son una estimación: se midieron en el juego. Se añadió un registro temporal a la prueba del cliente
(`FORJA_SOLO=libro`), que imprimió `chapterPage()` de cada capítulo y `pageCount()`. Luego se quitó; el árbol quedó
limpio. El jugador de la prueba no ha dado ningún paso del camino, y "Tu taller" y "Siguiente paso" cambian un poco
según el jugador.

**Total: 283 páginas** (la portada del libro dice "ciento ochenta"). Portada e índice ocupan las páginas 0 a 5.

### 1.2 Capítulo por capítulo

Etapa: **0** = minuto cero; **1** = primeras horas (mesas y estrella); **2** = hierro y combate; **3** = fundición y
aleaciones; **4** = mesa mayor y mejoras a fondo; **5** = exploración y Nether; **6** = el final (Bastión, dimensión y
jefe); **R** = consulta, no lectura.

| Sección | Capítulo (clave) | Págs. | Etapa | Qué trae | Estado |
|---|---|---:|---|---|---|
| — | Portada + índice | 6 | 0 | Emblema, cartel del siguiente paso, 4 pasos, índice en 5 secciones | Dice "ciento ochenta" en el javadoc; son 283 |
| Taller | Primeros pasos (`primeros_pasos`) | 7 | 0-1 | El ciclo en 6 pasos | **Anticuado** (paso 2, ver 1.3) |
| Taller | Siguiente paso (`siguiente_paso`) | 2 | 0-6 | Tarjeta del paso, lista de los 9 pasos | Bien |
| Taller | Mesas (`mesas`) | 6 | 1 | Mesa de piezas, plantilla, mesa de forja, mesa mayor, armario | Bien |
| Taller | Objetos (`objetos`) | 23 | R | Cada objeto con su receta de piezas y 16 fichas de armas especiales | Bien, pero es consulta |
| Taller | Piezas (`piezas`) | 8 | R | Cada pieza, su coste y qué da | Consulta |
| Taller | Materiales (`materiales`) | 8 | R | Cada material al pasar el ratón | Consulta |
| Taller | Rasgos (`rasgos`) | 9 | R | Rasgos de material | Consulta |
| Mejoras | Mejoras (`mejoras`) | 24 | 1-4 / R | Todas las mejoras por grupo, con receta | Consulta |
| Mejoras | Potencial (`potencial`) | 11 | 4 | Potencial, topes, fundente, carga, mesa de extracción, orbes | Bien |
| Mejoras | Maestría (`maestria`) | 9 | 2-4 | Maestría del objeto, conjunto, dones (lista), obra maestra | Bien |
| Taller | Aleaciones (`aleaciones`) | 15 | 3 | Calor bajo la mesa, aleaciones por calor, fundir piezas, crisol, cuba, caja, gráfica de durabilidad | **Anticuado en parte**: repite la fundición con el modelo viejo |
| Taller | Montar una fundición (`fundicion`) | 25 | 3 | Crisol, cubas, conductos, llenado, llave, calor, caja, coladores, mesas de colada, tubos de calor y 5 fluidos, sin manos, montadora, forja blanca | Al día con fundición 2 |
| Taller | Temple (`temple`) | 2 | 1 | Los 4 temples | Bien |
| Taller | Maestría de herrero (`herrero`) | 4 | 1-4 | Nivel de herrero, forja perfecta, herencia, firma, historia | Bien |
| Taller | Técnicas (`tecnicas`) | 5 | 4 | Martillo del maestro, 9 técnicas en 3 niveles | Bien |
| Referencia | Tu taller (`mi_taller`) | 2 | — | Tu maestría, técnicas y cuenta | Bien |
| Mejoras | Sinergias (`sinergias`) | 10 | 4 | Todas las parejas | Consulta |
| Mejoras | Pactos (`pactos`) | 4 | 4 | Los 4 pactos, ofrendas, límite | Bien |
| Pelear | Combate (`combate`) | 7 | 2 | Gráfica de DPS, parada, frenesí, sangrado, especiales, lanzar, lanza a caballo | **Incompleto**: falta casi todo el combate nuevo |
| Pelear | Maná y estamina (`mana`) | 11 | 2 | Maná, costes, recarga, muertes, 9 mejoras de maná y 5 de estamina | Al día, con dos restos del changelog |
| Pelear | Talismanes y cinturón (`accesorios`) | 4 | 2-4 | Talismanes, yunque de viaje, cinturón | Bien |
| Pelear | Clases (`clases`) | 17 | 1-6 | Cómo elegir (K), niveles, las 7 clases, medallón, farol de curación | Al día |
| Mundo | Eventos del cielo (`eventos`) | 10 | 2-5 | Jarra, los 9 eventos, pararrayos | Al día |
| Mundo | Encargos (`encargos`) | 1 | 2-4 | El Forjador | Bien |
| Mundo | Lo que te busca (`amenazas`) | 8 | 2-6 | "Élites", insignias, autómata, saqueadores, **Herrero Caído** | **Anticuado** (ver 1.3) |
| Mundo | Bestiario (`bestiario`) | 33 | 2-6 | 15 fichas con retrato, más el túmulo y el campamento | Casi al día; la ficha del Herrero se ha quedado corta |
| Mundo | Mundo (`mundo`) | 7 | 1-5 | Orbes, rotos, botín, monstruos, aldeanos, taller de montaña, adornos | Incompleto (monstruos) |
| Mundo | El Cementerio entre Estrellas (`cementerio`) | 4 | 6 | Oricalco, perla, portal y vuelta | **Incompleto**: no dice nada de la pelea. Además rompe la prueba (ver 1.4) |
| Referencia | Estadísticas (`estadisticas`) | 1 | R | Escala de colores | Bien |

Resumen por tipo:

- **Lectura:** unas 190 páginas.
- **Consulta** (objetos, piezas, materiales, rasgos, lista de mejoras, sinergias y estadísticas): unas **83 páginas**.
  Son tablas para pasar el ratón por encima, no para leer seguidas. Son buena parte de lo que asusta.
- **Por etapa:**
  - lo que hace falta en el minuto cero y la primera hora cabe en unas **25 páginas**;
  - la etapa 3 (fundición) son 40;
  - las etapas 5 y 6 son unas 45, repartidas entre "Lo que te busca", "Bestiario", "Mundo" y "Cementerio".

### 1.3 Textos anticuados frente al código de hoy

Cada punto indica la clave de `tools/generate_lang.py`.

1. **El Herrero Caído se invoca como antes** (`gui.forja.libro.herrero_caido`, `herrero_caido_fases` y la fila de la
   ofrenda en `threatsChapter`).
   - La guía dice: "En el Nether hay una fragua-fortaleza… Pon la mano en ella llevando la ofrenda y el Herrero se
     levanta". Las fases salen "bajo tres cuartos, a la mitad, bajo un cuarto".
   - Desde la entrega 2, `DeadForgeBlock.useWithoutItem` ya no lo despierta: abre el marco del portal. La pelea es en
     la dimensión, con fases a 2/3 y 1/3, el Reforjado estelar, los braseros y las constelaciones.
   - El mismo capítulo contradice al del Cementerio, que dice "ya no despierta en su fortaleza".
2. **El logro "El Herrero Caído"** dice "Despierta y derrota al Herrero Caído en su fragua del Nether". Anticuado por
   la misma razón.
3. **"Élites" es el nombre viejo del campeón** (`elites.titulo`, `elites` y `ataques.elite`).
   - La guía llama "élite" al monstruo con leyenda en la mano, uno de cada cien. Hoy ese es el **campeón**
     (`difficulty/Threat`, logro "Cazador de campeones").
   - Hay tres rangos: veterano, élite y campeón. La élite de verdad (dos galones de plata) solo aparece nombrada en
     `insignias`.
   - "Uno de cada cien" está escrito a mano. El argumento `%1$s` (el `elites` de la configuración) se pasa pero no se
     usa, así que si cambia la configuración la guía miente.
4. **Primeros pasos, paso 2** (`paso2`): dice que el metal "se cuela en la caja de moldeo". Desde "la colada cae por
   el colador", la caja solo prepara el molde; se cuela en la **mesa de colada**.
5. **Capítulo Aleaciones, cuatro textos de la fundición vieja:**
   - `caja.colar`: "la caja cuela la pieza con el metal de las cubas". Ya no.
   - `cuba`: dice "se llena por la cuba más baja y se saca por la más alta" y "empuja un montón por segundo". Es el
     modelo de antes de la red de metal: hoy las cubas se funden en depósitos y se llenan de una en una. Contradice a
     `fundicion.paso2` y a `fundicion.llenado`.
   - `crisol` y `crisol.niveles`: repiten lo que la fundición cuenta mejor.
   - `caja.niveles`: repite lo mismo que la fundición.
6. **`aleaciones_intro`**: "las tres mejores solo en el crisol de obsidiana". Desde fundición 2 también salen con
   aliento de forja por un tubo, como dice el propio capítulo de la fundición.
7. **`automata`**: "dos en la fragua del Nether". La estructura `fragua_caida` sigue generándose, pero su fragua ya
   solo abre un marco de portal, y la guía no lo dice. ¿Qué pinta hoy esa ruina? (pregunta 9).
8. **`mana.costes`** enseña números de la versión anterior al jugador: "la espera es de 6 tics (antes 14)". El
   "antes" es del changelog, no de la guía.
9. **`bestiario.herrero`**: "%s de vida y tres fases. Suelta el corazón de forja… y una leyenda". Faltan la Estrella
   forjada, la Estrella de vuelta, el yunque, el martillo del maestro y la revancha.
10. **`mundo.monstruos`**: solo dice que llevan equipo forjado y que el enderman esquiva. Faltan los esqueletos con
    báculo, los zombis con grimorio, los arcos y ballestas forjados que disparan y el blaze con su propia red.
11. **Portada:** el javadoc de `GuideBookScreen` dice "ciento ochenta páginas". Son 283.

### 1.4 Un fallo que ya está en la rama

La prueba del cliente `FORJA_SOLO=libro` **falla hoy en 8d62013**: `nothing in the guide may be drawn wider than the
page, got [278:Header:149]`. El título "El Cementerio entre Estrellas" mide 149 px y la página tiene 140. Se arregla en
la fase de construcción (con un título partido, o "El Cementerio" en el encabezado).

### 1.5 Sistemas sin ningún texto en la guía

Combate:

- **Estamina básica.** Golpear, esquivar, saltar (4, u 8 corriendo) y cubrirse gastan estamina, y la armadura pesada
  la recupera más despacio. Solo aparecen sus mejoras.
- **Esquivar.** Tiene tecla propia (**Alt izquierdo**, "Esquivar"), y ningún texto de la guía la nombra.
- **Peso** de armas y armadura (`combat/Weight`): qué cuesta y cómo alarga el aviso de los monstruos.
- **Golpe cargado** (`ChargedStrike`), **combos** (el tercer golpe pega más) y **ritmo de parada** (`ParryRhythm`).
- **Postura y aturdimiento** de los monstruos (`Posture`, con su barra `PostureHud`).
- **Zonas de golpe** (`HitZone`), **tipos de daño** y **resistencias de cada monstruo** (`MobResistances`: el yunque
  andante aguanta los filos, la escoria se traga los martillos…).

La IA de los monstruos:

- avisan y fintan;
- pegan por turnos;
- se colocan en anillo y rodean, y corren en modo rodeo;
- los grupos mezclan tipos y tienen un líder;
- los arqueros buscan línea de tiro;
- saltan atrás tras pegar;
- corren con su propia estamina;
- van a donde te vieron por última vez;
- no rompen bloques, salvo puertas;
- la presión;
- el daño según tu equipo (`GearScore`, un 15 % más por tramo);
- la penetración de armadura según el rango;
- entre los monstruos de Forja hay tregua (`Truce`).

Rangos y peleas del mundo:

- **Rangos:** qué hacen el veterano y la élite, el botín de cada rango y los nombres con apodo.
- **Duelos** (`ai/Duels`): el anillo de fuego, aceptar o rechazar, y qué pasa si huyes.
- **Peleas del mundo** (`WorldFights`): asedios a tu forja, ladrones que se llevan lo que hiciste, enemigos con nombre
  que vuelven, y la noche y la lluvia cambiando cómo pelean.

Dificultad:

- **La escalera de dificultad** (`Ladder`): el botón de Minecraft con un escalón más, Extremo, y lo que enciende cada
  nivel (tabla en el capítulo, con sus cifras).
- La **dificultad adaptativa** y `/forja dificultad`.
- Las **noches** que endurecen el mundo (`Nights`).

El final del juego:

- **El Bastión del Gremio:** dónde sale, que el Forjador de nivel 5 vende su mapa, las 11 torres, los sótanos, la
  Forja Profunda, sus unos 90 monstruos y la arena cerrada a construir.
- **El castillo de forja**, la casa del Guardián de Cuño (`docs/CASTILLO.md`). Solo sale el Guardián en el
  bestiario.
- **La pelea del Cementerio** (entrega 3):
  - la estrella que cae y la llegada;
  - las fases a 2/3 y 1/3;
  - los aprendices que salen de la tierra;
  - el Reforjado estelar, sus brasas y los braseros que se vuelcan;
  - las constelaciones mitad y mitad;
  - la lluvia de estrellas con aviso.
- **Las recompensas:** la Estrella forjada (tope 125), la Estrella de vuelta y la fragua fría estelar de la revancha.

Otros:

- **Huevos generadores:** son de creativo; basta con una línea en el catálogo.
- **Las teclas de Forja**, juntas en algún sitio: G (guía), Alt (esquivar) y K, V y B (clases).

---

## 2. La serie: un cuaderno de inicio y siete libros

### 2.1 La idea

- **Libros cortos que se aprenden cuando hacen falta.** Cada libro se fabrica en la mesa de crafteo con un libro y
  algo de lo que explica. Su receta se aprende sola al tocar por primera vez lo que cuenta (un logro de Forja), y
  hasta entonces la mesa no la acepta. Nadie recibe 283 páginas en el minuto cero.
- **La consulta sale de los libros.** Las 83 páginas de tablas (objetos, piezas, materiales, rasgos, lista de
  mejoras, aleaciones, sinergias, dones y estadísticas) pasan a un **Catálogo**. Es una pestaña de la biblioteca (ver
  3.3) con buscador y filtro por lo que ya has desbloqueado. Los libros enlazan a él ("ver en el catálogo"). Así los
  libros solo tienen lectura.
- **El mismo motor.** `GuideBookScreen` ya tiene capítulos, secciones con pestañas, índice, tira de navegación,
  "atrás" y enlaces entre capítulos. Un libro es una lista de capítulos con su portada, así que el trabajo es
  repartir el contenido, no escribir otro visor.
- **Se escribe poco desde cero.** La mayoría de los textos ya existen en `generate_lang.py`. Lo nuevo es el
  Cuaderno, la parte de combate y enemigos del libro II, el Bastión y la pelea del Cementerio.

### 2.2 Los libros

Las páginas son de lectura y se calculan con las medidas de 1.2. Los tiempos cuentan unos 25 segundos por página.

Todos se fabrican. La receta es siempre **un libro y un ingrediente** en la mesa de crafteo, sin forma. Se
**aprende** con un logro de Forja; hasta entonces la mesa no la acepta y el libro de recetas no la enseña. La
misma receta sirve para rehacerlo si se pierde.

| # | Título | Págs. | Portada | Receta | Se aprende… |
|---|---|---:|---|---|---|
| 0 | **Cuaderno del aprendiz** | ~12 | Cuero claro, martillo | libro + lingote de hierro | siempre. Además se da uno al entrar por primera vez |
| I | **El yunque** | ~26 | Naranja, yunque | libro + plantilla | al grabar tu primera plantilla (logro "Molde a medida") |
| II | **El arte del combate** | ~52 + bestiario | Rojo, espada; el lomo más ancho | libro + hueso | al forjar tu primer objeto (logro "Forjado a mano") |
| III | **La fundición** | ~34 | Cobre oscuro, crisol | libro + lingote de cobre | con tu primera mejora (logro "Mejorado") |
| IV | **La mesa mayor** | ~38 | Violeta, mesa mayor | libro + lingote de damasco | con la mesa de forja mayor (logro "La mesa mayor") |
| V | **Clases** | ~20 | Verde, medallón | libro + esmeralda | con tu primera parada perfecta (logro "Al filo del escudo") |
| VI | **El Bastión y el Herrero** | ~26 | Verde azulado, fragua apagada | libro + mapa vacío | al abrir el cofre de una forja abandonada (logro "Brasas frías"). El Forjador lo vende en su nivel 3 |
| VII | **El Cementerio entre Estrellas** | ~16 | Violeta noche, perla de oricalco | libro + hierro estelar | al encender el portal (logro nuevo). También está en un atril de la Forja Profunda |

Por dentro:

- **Cómo se aprende.** `GuideBooks` asocia cada libro a su logro. Cuando el jugador completa ese logro, o al entrar
  si ya lo tenía (mundos viejos), recibe la receta.
- **Cómo se bloquea.** Una mezcla en la mesa de crafteo (`CraftingMenuMixin`) deja sin resultado una receta de
  libro que el jugador no conoce. Es lo mismo que hace vanilla con la regla `doLimitedCrafting`, pero solo para los
  libros.
- **El autocrafteador** no tiene jugador, así que no se bloquea.

Total de lectura: unas **225 páginas en 8 libros**, más el bestiario que se va escribiendo. Nadie las tiene todas a la
vez, y el más largo que se abre en las primeras horas tiene 52.

### 2.3 Qué lleva cada libro

**0 · Cuaderno del aprendiz (~12 págs.).** El centro de todo, corto y amable.

1. **Bienvenido a la forja** (1 pág.): de qué va Forja en cinco líneas. Las piezas hacen el objeto, el material
   cambia lo que da, y el objeto crece contigo.
2. **Tus dos primeras mesas** (3 págs.): recetas de la mesa de piezas, la mesa de forja y la plantilla. Graba tu
   primera plantilla (paso 1 del camino).
3. **Tus teclas** (1 pág.): G, la biblioteca; Alt, esquivar; K, V y B, las clases. Todas se cambian en Controles.
4. **El camino del herrero** (2 págs.): los 10 pasos en cuatro tramos, cada uno con el libro que lo explica, y tu
   marca en cada uno.
5. **La estantería** (4 págs.): cada libro con su portada en pequeño, qué cuenta, **cuándo se aprende** y **su receta**
   dibujada. Los que ya tienes salen en color; los que no, en sombra, con la condición.
6. **Siguiente** (1 pág.): "Graba tu primera plantilla y aprenderás la receta de *El yunque*."

**I · El yunque (~26 págs.).** El taller de las primeras horas.

- Lo que ya sabes (1).
- Las mesas y el armario (4).
- Piezas: qué se corta, cuánto cuesta y el rasgo del material (4). Paso 2.
- La estrella: forjar y el martillo perfecto (3). Paso 3.
- El temple (2). Paso 4.
- Mejorar: ingredientes, porcentajes, el tope de esta mesa, libros encantados y orbes (5). Paso 5.
- Desarmar, orbes y reparar; lo roto no se pierde (3).
- Tu firma y la historia del objeto (1).
- Cómo leer los colores, y el catálogo (2).
- Siguiente: la pelea (libro II) y el calor (libro III) (1).

**II · El arte del combate (~52 págs. más el bestiario).** El libro grande, como pidió Andy. Siete partes, cada una con
su pestaña de color. Cada parte empieza con una página **"En una página"**: lo esencial en cinco líneas, para quien
no quiera seguir.

1. **Tu cuerpo** (5): estamina, esquivar (Alt), saltar y correr, y el peso de lo que llevas.
2. **Golpear** (10): golpe cargado, combos, frenesí, sangrado, zonas de golpe, tipos de daño y resistencias, la
   gráfica de DPS, golpes especiales por arma, armas arrojadizas y la lanza a caballo.
3. **Defenderte** (3): bloquear, la parada perfecta y su ritmo, y el golpe de escudo. **Paso 6 del camino.**
4. **Magia y maná** (12): báculo y grimorio ("un golpe más"), cargar, la barra de maná, costes y recarga, la magia en
   las armas de filo, las mejoras de maná y estamina, y el farol de curación en lo básico (el Curandero va en el
   libro V).
5. **Los enemigos** (12):
   - cómo pelean: aviso, finta, turnos, anillo y rodeo, grupos mixtos con líder, arqueros que buscan hueco, el salto
     atrás, que te pierden de vista, que no rompen bloques salvo puertas, y la tregua entre los de Forja;
   - la presión y tu equipo;
   - los rangos: veterano, élite y campeón, con sus insignias, nombres y botín;
   - duelos y peleas del mundo: asedios, ladrones y enemigos con nombre;
   - las noches;
   - la dificultad de Forja y la adaptativa.
6. **El cielo** (10): la jarra, los 9 eventos y el pararrayos.
7. **Bestiario** (se escribe solo): cada monstruo tiene su ficha, pero la página está en blanco, con su silueta y
   "???", hasta que lo ves por primera vez. Así el libro empieza en ~52 páginas y crece a ~85 a tu paso. Las fichas
   del Bastión, el castillo y el Herrero van en sus libros (VI y VII).

**III · La fundición (~34 págs.).** El paso de "una mesa" a "una línea".

- El calor bajo la mesa y tus primeras aleaciones: bronce y peltre (4). Paso 7.
- Fundir piezas sobre lava (1).
- El crisol (3).
- Cubas y depósitos (2).
- Conductos, caño, llenado de uno en uno y llave de paso (4).
- El calor de las cubas (1).
- La caja de moldeo y los moldes (2).
- Coladores y colada basta (2).
- Mesas de colada y marcos (3). Paso 8.
- Calor por tubos y los 5 fluidos (5).
- La fundición sin manos y la montadora (3).
- Forja blanca (1).
- El damasco y la mesa de forja mayor (2). Paso 9.
- Siguiente (1).

Las aleaciones, una a una, van al catálogo. Los textos repetidos del capítulo de aleaciones de hoy desaparecen.

**IV · La mesa mayor (~38 págs.).** Lo que se hace cuando ya se sabe hacer.

- Qué cambia con la mesa mayor (2).
- Potencial (4).
- La carga y el peso de las mejoras (3).
- El fundente maestro (1).
- La mesa de extracción y los orbes (3).
- Sinergias: cómo funcionan, con la lista en el catálogo (3).
- Pactos (4).
- Maestría del objeto y dones (4).
- La obra maestra (1).
- Maestría de herrero y herencia (3).
- Técnicas y el martillo del maestro (4).
- Talismanes, cinturón y yunque de viaje (4).
- Tu taller (2).

**V · Clases (~20 págs.).**

- Qué es una clase y cómo elegirla (2).
- Niveles, puntos y experiencia (2).
- Las siete clases, una o dos páginas cada una (12).
- Cambiar de clase y el medallón (2).
- El farol de curación y el Curandero (2).

**VI · El Bastión y el Herrero (~26 págs.).** Salir a buscar.

- Lo que hay ahí fuera (1).
- Aldeanos, el Forjador y los encargos (3).
- Ruinas (5): la forja abandonada, el taller de montaña, el túmulo, el campamento saqueador y el castillo de forja.
- Leyendas y orbes del mundo (2).
- **La historia del Herrero Caído**, sin mecánicas: quién era y por qué su forja está apagada (2).
- **El Bastión del Gremio** (4): dónde sale, el mapa del Forjador, las torres, los sótanos, la Forja Profunda y lo que
  te espera dentro.
- La fragua del Nether (1).
- Oricalco, la perla y el portal (4).
- Fichas del Bastión y del castillo, que se escriben solas: Guardián de Cuño, Molde Roto… (unas 4).

**VII · El Cementerio entre Estrellas (~16 págs.).** El final.

- El viaje: llegar, volver y lo que no se puede hacer allí, como dormir (2).
- La meseta (1).
- La pelea (7):
  - la estrella que cae;
  - las fases a 2/3 y 1/3;
  - los aprendices que salen de la tierra;
  - el Reforjado estelar y los braseros;
  - la lluvia de estrellas;
  - la forja reclama;
  - las constelaciones mitad y mitad.
- Las recompensas: Estrella forjada, corazón de forja, yunque, martillo del maestro y Estrella de vuelta (3).
- La revancha y la fragua fría estelar (1).
- Ficha del Herrero Caído (2).

### 2.4 De dónde sale cada capítulo de hoy

| Capítulo de hoy | Va a |
|---|---|
| portada, `primeros_pasos`, `siguiente_paso` | 0 (resumido) y cada libro su tramo |
| `mesas`, `temple`, parte de `herrero` (forja perfecta, firma, historia), `mundo.orbes` y `mundo.rotos` | I |
| `combate`, `mana`, `eventos`, `amenazas` (salvo el Herrero), `bestiario` (salvo Bastión y Herrero), `mundo.monstruos` | II |
| `aleaciones` (calor, fundir piezas, forja blanca), `fundicion` | III |
| `potencial`, `maestria`, `sinergias`, `pactos`, `tecnicas`, `herrero` (nivel y herencia), `accesorios`, `mi_taller` | IV |
| `clases` | V |
| `mundo` (botín, aldeanos, taller, adornos), `encargos`, `bestiario` (túmulo, campamento, Guardián, Molde), `cementerio` (oricalco, perla, portal) | VI |
| `cementerio` (vuelta), `amenazas` y `bestiario` (el Herrero) | VII |
| `objetos`, `piezas`, `materiales`, `rasgos`, la lista de `mejoras`, las aleaciones una a una, la lista de sinergias, la lista de dones y `estadisticas` | Catálogo |

---

## 3. Cómo se unen

### 3.1 Cada libro apunta al siguiente

- La última página de cada libro es **"Siguiente"**. Dice qué hacer ahora y qué libro toca después, con su tarjeta:
  portada pequeña, receta dibujada y cuándo se aprende. Ejemplo: "Cuando mejores algo por primera vez aprenderás
  la receta de *La fundición*: un libro y un lingote de cobre".
- La primera página de los libros I a VII es **"Lo que ya sabes"**: tres líneas y enlaces al libro anterior, para
  quien lo abra fuera de orden.
- Al aprender la receta de un libro sale el aviso de vanilla "¡Nuevas recetas!" con el libro. La línea del chat de
  `ForjaPath.hint` dice el paso, el capítulo y el libro: "Lo explica «Temple», en «El yunque»".
- Un enlace a un capítulo de otro libro abre ese libro si lo llevas encima. Si no, dice en qué libro está y cómo se
  hace.

### 3.2 El camino del herrero, repartido

Los 9 pasos de hoy no cambian y se añade uno, **técnica** (respuesta 5). `ForjaPath` pasa a 10 pasos y
`PathGameTests` lo comprueba. Cada paso apunta a un capítulo, y cada capítulo vive en un libro:

| Tramo | Pasos | Libro |
|---|---|---|
| 1 · Aprendiz | plantilla | 0 (Cuaderno) |
| | pieza, forja, temple, mejora | I |
| 2 · Oficial | parada | II |
| 3 · Fundidor | aleación, colada, mesa mayor | III |
| 4 · Maestro | técnica | IV |

- El Cuaderno y la biblioteca enseñan el camino entero.
- Cada libro enseña en su portada **sus propios pasos** (✓ 2/4).
- El cartel "Tu siguiente paso" va en la portada del Cuaderno y de la biblioteca.

### 3.3 La biblioteca y el catálogo

- **G** (respuesta 1):
  - si llevas el Cuaderno del aprendiz encima, abre la **biblioteca**;
  - si no, abre el libro de Forja que tengas en la mano;
  - con las manos vacías y sin Cuaderno, un aviso dice que lleves el Cuaderno.
- **La biblioteca** es un libro más, con el mismo visor y pestañas:
  - **Estantería:** una tarjeta por libro, con su portada pequeña, sus páginas y lo leído, y el sello de "nuevo" si
    no lo has abierto. Un clic abre los libros que llevas encima. De los que no llevas enseña la receta y cuándo se
    aprende.
  - **El camino del herrero.**
  - **Catálogo** (respuesta 2): una página separadora y tres pestañas de consulta.
    - Objetos y piezas.
    - Materiales y rasgos.
    - Mejoras y colores.

    Con los libros III y IV se añadirán las aleaciones una a una, las sinergias y los dones.
- **Clic derecho** con un libro en la mano abre ese libro, como hoy.
- **Estantería del herrero (bloque)** y **atril**: se quedan para más adelante. El atril hará falta para el libro
  VII.

### 3.4 Qué pasa con la guía de hoy en los mundos viejos

- **El objeto `forja:guia_de_forja` conserva su id** y pasa a ser el *Cuaderno del aprendiz*, con portada nueva.
  Las guías que haya en inventarios y cofres se vuelven Cuadernos sin romper nada.
- **Jugadores que ya jugaban:** al entrar se les enseñan las recetas de los libros que ya les tocan según sus logros.
  Se hace en cada entrada y es idempotente. Ningún libro se regala: se fabrican.
- **Un aviso único**, la primera vez tras la actualización, para quien ya tenía la guía: "La guía de forja ahora es
  el Cuaderno del aprendiz. Los demás libros se fabrican: el Cuaderno te dice cómo."
- **El tomo entero** (respuesta 6): el objeto `forja:tomo_de_forja`, solo en creativo, con todo en un volumen como
  hoy.
- El logro "Lectura obligada" (`guia`) se da al abrir cualquier libro de Forja.

---

## 4. Retoques para que no asuste

1. **Páginas y tiempo en la portada**: "26 páginas · 12 min". El índice da las páginas de cada parte.
2. **Marcapáginas**: cada libro se abre donde lo dejaste.
3. **Progreso por libro**:
   - una cinta en la portada y una línea en el lomo con lo leído (las páginas que has visto);
   - la tarjeta de los pasos del camino de ese libro.
4. **Sellos de "nuevo"**:
   - un sello de lacre rojo en los libros sin abrir;
   - un punto "nuevo" en las fichas del bestiario recién escritas;
   - un punto "actualizado" en los capítulos cuyo texto cambió con una versión del mod. Se sabe comparando una huella
     del texto con la última que viste, así que cuando el mod crece, el jugador ve qué leer y no tiene que releerlo
     todo.
5. **"En una página"** al principio de cada parte del libro II, y **"Lo que ya sabes"** al principio de los demás.
6. **Páginas que se escriben solas**: el bestiario y las fichas del Bastión, en sombra hasta que conoces al monstruo.
7. **Portadas distintas**: cada libro con su color, su emblema y su número. En la biblioteca se reconocen de un
   vistazo, como las pestañas de color de hoy.
8. **Nada de números de versiones anteriores** en las páginas (el "antes 14" del maná).
9. **Los títulos caben** (hoy "El Cementerio entre Estrellas" no cabe; ver 1.4).

---

## 5. Pendiente

- Los libros II a VII, en orden, con una entrega cada uno. Hasta que estén, lo suyo solo se lee en el tomo de
  creativo, y el Cuaderno los enseña como "en preparación".
- Con el libro II, el bestiario en sombra. Con el V, las clases que se abren al leer su libro. Con el VI, la venta
  del Forjador. Con el VII, el logro del portal y el atril.
- La estantería del herrero como bloque, si Andy la sigue queriendo.

---

## 6. Estado de la construcción (2026-09-29, primera entrega)

Hecho: el Cuaderno del aprendiz (0), El yunque (I) y la biblioteca, con el catálogo en tres pestañas. También los
textos anticuados de 1.3, el título que no cabía (1.4) y el paso 10 del camino. Páginas medidas en la prueba del
cliente:

| Libro | Páginas | Capítulos (página donde empiezan) |
|---|---:|---|
| Cuaderno del aprendiz | 19 | bienvenida 4, primeras mesas 6, teclas 11, siguiente paso 13, los libros 15 |
| El yunque | 29 | lo que ya sabes 5, mesas 7, cortar 13, la estrella 16, temple 19, mejorar 21, desarmar 24, estadísticas 27, siguiente 28 |
| Biblioteca | 85 | estantería 5, camino 10, catálogo 12 (objetos 13, piezas 36, materiales 44, rasgos 52, mejoras 61, colores 85) |
| Tomo (creativo) | 285 | la guía entera, como antes, más "La mesa mayor" |

- El Cuaderno salió más largo que los ~12 previstos, porque las tarjetas de los siete libros llevan su receta
  dibujada (5 páginas).
- El yunque, algo más largo que los ~26 previstos.
- Las hojas de contactos están en `E:\IA\Claude\Forja_capturas_mejoras\libros\`: `portadas.png`, `cuaderno.png`,
  `yunque.png` y `biblioteca.png`.

### Segunda entrega (2026-09-30): tarjetas a oscuras y libro II

- **Tarjetas.** La de un libro sin aprender sale a oscuras y sin receta. Con JEI, las recetas no aprendidas se
  esconden.
- **El arte del combate:** 86 páginas con el bestiario entero en sombra, repartidas así:
  - tu cuerpo 5, golpear 9, armas 16, defenderte 22;
  - magia 26, maná 31;
  - cómo pelean 42, rangos 50, peleas del mundo 54, dificultad 58;
  - eventos 61;
  - bestiario 71, siguiente 85.
- **Tamaño.** Es más largo que las ~52 previstas porque el capítulo de maná (11 páginas), los eventos (10) y las
  resistencias de cada monstruo ocupan lo suyo. Cada parte empieza con "En una página", y el índice lleva las cinco
  partes con su pestaña.
- **Bestiario.** Se escribe solo (client/CreatureSightings): una criatura cuenta como vista a 24 bloques o menos y
  con línea de vista. Lo visto se guarda en el cliente.

### Tercera entrega (2026-09-30): el probador por grupos y los libros III a VII

- **Probador.** Andy decidió que las mejoras no se descubren. Las listas del probador van por grupos plegables. Los
  pactos y las sinergias quedan en sombra hasta que se abren o despiertan, en todos los libros menos el tomo.
- **Libro II.** Se quedan sus 86 páginas. En la página de resistencias, los monstruos del mod que aún no has visto
  (el Guardián de Cuño entre ellos) salen como "???".
- **Páginas medidas en la prueba del cliente:**

| Libro | Páginas | Capítulos (página donde empiezan) |
|---|---:|---|
| Cuaderno del aprendiz | 19 | bienvenida 4, primeras mesas 6, teclas 11, siguiente paso 13, los libros 15 |
| I · El yunque | 32 | lo que ya sabes 5, mesas 7, cortar 13 (con mangos pesados y ligeros), estrella 18, temple 21, mejorar 23, probador 26, desarmar 27, estadísticas 30, siguiente 31 |
| II · El arte del combate | 86 | como en la segunda entrega |
| III · La fundición | 38 | lo que ya sabes 4, primeras aleaciones 5, la fundición 10, la mesa mayor 35, siguiente 37 |
| IV · La mesa mayor | 49 | lo que ya sabes 5, potencial 7, sinergias 18, pactos 20, maestría 24, el herrero 33, técnicas 37, tu taller 42, accesorios 44, siguiente 48 |
| V · Clases | 22 | lo que ya sabes 3, clases 5, siguiente 22 |
| VI · El Bastión y el Herrero | 27 | lo que hay ahí fuera 4, mundo 5, encargos 12, ruinas 13, quién era el Herrero 19, el Bastión 21, el portal 24, siguiente 27 |
| VII · El Cementerio entre Estrellas | 18 | el viaje 3, la pelea 5, la recompensa 13, el Herrero Caído 16, fin 17 |
| Biblioteca | 93 | estantería 5, camino 10, catálogo 12 (objetos 13, piezas 36, materiales 45, rasgos 53, aleaciones 62, probador 68, mejoras 69, colores 93) |
| Tomo (creativo) | 286 | la guía entera |

- **Cómo se aprende cada libro:**
  - III, con la primera mejora;
  - IV, al construir la mesa mayor;
  - V, con la primera parada;
  - VI, al encontrar una ruina, o comprado al Forjador de nivel 3 por 12 esmeraldas (una oferta fija que se añade
    a las que salen al azar);
  - VII, al encender el portal estelar o al entrar en su mundo.
- **Clases.** Se abren la primera vez que se abre el libro V (logro `leer_clases`). A quien ya tenía clase en un
  mundo viejo se le da el logro al entrar.
- **Atril y estantería.** El atril del Herrero se pone junto al marco en cuanto el portal se enciende, así que no
  cambia el NBT del Bastión. La estantería guarda los libros en el estado del bloque (un sitio por libro, sin block
  entity). Un comparador cuenta los libros que tiene. No abre la biblioteca: eso sigue siendo la tecla G con el
  Cuaderno.
- **Hojas de contactos**, cada una con copia .jpg de menos de 3 MB, en
  `E:\IA\Claude\Forja_capturas_mejoras\libros\`: `portadas`, `cuaderno`, `yunque`, `combate`, `fundicion`,
  `mesa_mayor`, `clases`, `bastion`, `cementerio`, `biblioteca`, `probador` y `estanteria`.
