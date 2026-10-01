# Aleaciones del Nether y del End

Pedido de Andy (2026-10-01): darle sentido a la ruina «Fragua caída» del Nether con **una aleación que solo se
funde en la fragua de esa ruina**. Ampliado el mismo día: **dos aleaciones del Nether y una del End**. Las dos del
Nether salen solo de la fragua de la Fragua caída, una vez encendida; la del End solo de una fragua del End, en una
ruina pequeña nueva de las islas exteriores. Ninguna puede ser «netherita mejor», las tres tienen que caber en la
escalera de `docs/EQUILIBRIO.md` y cada una lleva un rasgo que no tenga ya otro material ni una mejora.

Este documento es el diseño, escrito antes de programar. Los números viven en el código
(`material/ForgeMaterial.java`, `forge/Alloys.java`, `upgrade/TraitEffects.java`, `block/FarForgeBlock.java`) y la
prueba de equilibrio (`./gradlew runGametest`) los mide igual que a los demás materiales.

## 1. Lo que ya había (lo que se mira antes de decidir)

- **Las aleaciones** (`forge/Alloys.java`) se hacen en la estrella de la mesa de forja o en el crisol, y lo que sale
  depende del calor: templada (fogata), caliente (magma, fuego de almas), fundida (lava) y forja blanca (solo crisol
  de obsidiana o aliento de forja por un tubo). Cada aleación es también un material (`ForgeMaterial`), su lingote se
  registra solo desde `Alloys.ALL` y cualquier pieza acepta cualquier material que pueda ser cabeza
  (`PartType.accepts`). Lo que no se talla en la mesa de piezas se cuela: lingote → crisol → cuba → mesa de colada
  con el molde.
- **Los rasgos ocupados**: afortunado, acuático, ígneo, afilado, del End (teletransporte), llanto (se repara de
  noche), estelar (caídas), resonante, pegajoso, acorazado, vacío, ascua (el fuego lo arregla y pega más), cargado
  (descarga en cadena), animado (para un golpe solo), diáfano (no pesa), solar, nocturno y vivo (las muertes lo curan).
- **Mejoras que se parecen a ideas obvias y por eso se descartan**: Fundición (las herramientas funden lo que
  rompen), Represalia (prende al que te pega), Paso helado (agua), Velocidad de alma, Aspecto ígneo.
- **La escalera** (`docs/EQUILIBRIO.md`, nivel = lo que puede minar): hierro 2, diamante 3, netherita 4. Las
  aleaciones de lava van de 900 a 1900 de durabilidad y de +3,0 a +4,0 de daño; las de forja blanca, 1700 a 2200.
  La netherita es 2031 / +4,0 / armadura 20 / dureza 3. El damasco (la otra que lleva chatarra) 1400 / +4,0.

## 2. Las tres aleaciones

| | **Fatuo** (Nether) | **Magmacero** (Nether) | **Eterio** (End) |
|---|---|---|---|
| Inglés | Wispfire | Magmasteel | Aetherium |
| Dónde | Fragua de almas (Fragua caída) | Fragua de almas (Fragua caída) | Fragua del vacío (Fragua del Vacío, islas exteriores) |
| Receta por tanda | 2 lingotes de hierro + 1 chatarra de netherita + 4 tierra de almas | 2 acero + 4 basalto + 4 piedra negra + 2 crema de magma | 2 acero + 1 caparazón de shulker + 4 fruta de coro reventada + 4 piedra del End |
| Salen | 2 lingotes | 2 lingotes | 2 lingotes |
| Combustible por tanda | 1 polvo de blaze | 1 polvo de blaze | 1 perla de ender |
| Color | 0x3A5466 (acero ahumado; el lingote lleva llamas azules) | 0x6E3A2C (basalto con vetas de magma) | 0x7A5FB0 (violeta de coro) |
| Rasgo | **Espectral** | **Volcánico** | **Flotante** |
| Durabilidad | 1000 | 1600 | 1200 |
| Velocidad de minado | 8,0 | 7,0 | 8,5 |
| Daño de cabeza | +3,0 | +3,0 | +3,0 |
| Nivel | netherita (4) | diamante (3) | netherita (4) |
| Encantabilidad | 18 | 10 | 22 |
| Mango: durabilidad / ataque / minado | ×1,25 / +0,10 / ×1,05 | ×1,55 / −0,20 / ×0,90 | ×1,05 / +0,20 / ×1,10 |
| Armadura (botas, grebas, pechera, casco) | 3 / 6 / 7 / 3 = 19 | 3 / 6 / 8 / 3 = 20 | 2 / 6 / 7 / 3 = 18 |
| Durabilidad de armadura | 30 | 38 | 30 |
| Dureza / resistencia al empuje | 2,0 / 0 | 2,5 / 0,10 | 1,5 / 0 |
| Conjunto (las 4 placas) | +1 de daño y el fuego te dura la mitad | +1 de armadura y +20 % de resistencia al empuje | Saltas más (+0,1) y +1 de dureza; el vacío te devuelve el doble de a menudo |

### 2.1 Fatuo y su rasgo **Espectral** — «quema lo que no arde»

Hierro, chatarra de netherita y tierra de almas fundidos al fuego azul. Su identidad: **el fuego que sí sirve en el
Nether**. Todo lo que el mod tiene de fuego (ígneo, ascua, Aspecto ígneo, Represalia) no hace nada a un blaze, un
esqueleto wither, un ghast o un cubo de magma, que no arden. La llama fatua sí.

- **Armas, herramientas y flechas**: el golpe deja al objetivo con **llama fatua 4 s**: 1 de daño mágico cada 2
  segundos, también a lo que es inmune al fuego, y el agua no la apaga. No se acumula: un golpe nuevo la renueva.
- **Armadura**: el que te pega cuerpo a cuerpo se lleva **1 s de llama fatua por pieza**.
- **Conjunto**: +1 de daño y el fuego normal te dura la mitad.

Por qué no es «netherita mejor»: cabeza de +3,0 y 1000 de durabilidad, por debajo del damasco (+4,0 / 1400) y de la
netherita (+4,0 / 2031); armadura 19 con dureza 2 (netherita 20 / 3). Lo que gana es un daño pequeño y constante
(medio punto por segundo) que no para nada ni nadie. En la tabla de equilibrio entra como un daño que dura, igual que
el fuego, el veneno o el sangrado.

*Medido (2026-10-01):* con la primera versión (cabeza +3,5 y 1 de daño por segundo) el informe de equilibrio ponía
el fatuo en 26 de las 60 mejores armas, por delante del corazón de forja: la llama pasa por encima del tope por golpe
y de la invulnerabilidad, así que a ritmo alto valía más que cualquier cabeza. Con +3,0 y un mordisco cada 2 s no
entra en ninguna: es el arma para el Nether, no la mejor arma.

### 2.2 Magmacero y su rasgo **Volcánico** — «la lava se vuelve suelo»

Acero fundido con basalto, piedra negra y crema de magma: pesado, aguanta mucho y no corta tanto. Es el metal de
**armadura y de pico** del Nether.

- **Armadura**: la lava que pisas se enfría en **costra de magma** (`forja:costra_de_magma`) que aguanta mientras
  alguien está encima y vuelve a ser lava entre 4 y 6 s después. Radio 1 con una pieza, 2 con dos o tres, 3 con
  las cuatro. Como el Paso helado sobre el agua, pero sobre lava: cruzar los mares de lava del Nether sin puentes.
- **Herramientas**: la piedra del Nether (netherrack, basalto, piedra negra, bloque de magma, ladrillos del Nether
  y sus variantes; etiqueta `forja:piedra_volcanica`) se pica **un 50 % más deprisa**.
- **Armas**: nada; su sitio es la armadura y el pico.
- **Conjunto**: +1 de armadura y +20 % de resistencia al empuje.

Por qué no es «netherita mejor»: nivel de diamante (no pica netherita antigua), cabeza +3,0, mango lento (−0,20).
Es más duradero que la netherita en un mango (×1,55) pero el obsidiacero lo sigue siendo más (×1,70).

### 2.3 Eterio y su rasgo **Flotante** — «el que golpeas sube; a ti el vacío te devuelve»

Acero, caparazón de shulker, coro reventado y piedra del End, fundidos en una fragua que solo prende en el End.

- **Armas, herramientas y flechas**: el golpe **levanta** al objetivo: Levitación 1 s. A cada objetivo, como mucho
  una vez cada 3 s. No levanta a los jefes (dragón, wither, Herrero Caído, ni nada con la etiqueta de jefes).
- **Armadura**: **el vacío te devuelve**. Si caes al vacío (16 bloques por debajo del fondo del mundo) con una
  pieza o más de eterio, vuelves al último suelo firme donde estuviste, con Caída lenta 6 s, y cada pieza pierde un
  10 % de su durabilidad. Una vez cada 3 minutos (1,5 con el conjunto entero).
- **Conjunto**: saltas más (+0,1 a la fuerza de salto) y +1 de dureza; el vacío te devuelve el doble de a menudo.

Por qué no es «netherita mejor»: +3,0 y 1200, armadura 18 con dureza 1,5. Es la aleación de **llegar y volver**:
explorar las islas exteriores sin miedo a la caída, y pelear contra lo que te empuja al vacío.

### 2.4 Dónde caen en la escalera

Las tres son aleaciones de lava por número (como almacero, vidriacero o acero estelar), por debajo de las de forja
blanca y de la netherita en el número que la netherita define (daño de cabeza y durabilidad). Ninguna domina a otra
del mismo rasgo (son rasgos nuevos) y ninguna es la mejor cabeza ni el mejor mango: el informe de equilibrio las
mide como al resto y la prueba `equilibrioSinDominados` sigue vigilando lo de siempre. La armadura queda dentro del
límite de `ArmaduraGameTests` (netherita con Protección IV más 5 puntos): 18 a 20 de conjunto, como la netherita.

Coste: una tanda de fatuo gasta una chatarra por **dos** lingotes (el damasco, una por uno), a cambio de ir a la
ruina y encenderla; el magmacero gasta dos aceros por dos lingotes y materiales de cantera del Nether; el eterio,
un caparazón de shulker por dos lingotes, que es lo caro del End.

## 3. Las fraguas

### 3.1 La fragua de almas (Nether)

- La Fragua caída ya no lleva la `fragua_apagada` vieja, sino una **fragua de almas apagada**
  (`forja:fragua_de_almas`, estado `lit=false`). Para ruinas de mundos viejos: una `fragua_apagada` dentro de una
  Fragua caída, al tocarla, se convierte en una fragua de almas apagada en vez de abrir un marco de portal.
- **Encenderla**: clic derecho con una **vara de blaze** (se gasta). Se enciende con fuego azul y **despiertan sus
  guardianes**: todo monstruo a 24 bloques va a por quien la encendió, y del altar salen **dos pavesas** más.
- **Encendida**: es un horno de una sola cosa. Clic derecho con un ingrediente lo echa al hogar (hasta 4 cosas
  distintas, 64 de cada); con polvo de blaze lo carga de combustible (hasta 16 tandas). Clic sin nada en la mano
  dice qué tiene, qué está fundiendo o qué le falta; agachado y sin nada en la mano devuelve lo del hogar.
- **Cada tanda**: 10 s (200 ticks) y 1 polvo de blaze. Mientras haya ingredientes y combustible, sigue. Lo que
  sale cae encima de la fragua, o a una tolva o cofre que tenga justo debajo.
- **Solo funde fatuo y magmacero, y solo en el Nether.** Fuera del Nether (si alguien la pone en creativo) está
  encendida pero no funde, y lo dice.
- **No se rompe** (como las ménsulas del portal), así que la fragua es la de la ruina.
- **Calor**: no usa la escala de la mesa (`Alloys.Heat`) ni tubos de calor; su fuego es suyo. Es la única regla
  nueva y es a propósito: si un tubo de lava pudiera encenderla, se podría construir en casa.

### 3.2 La fragua del vacío (End) y su ruina

- Ruina nueva **`forja:fragua_del_vacio`**: 11 × 11, de ladrillo de piedra del End y púrpura roto, sobre las islas
  exteriores (bioma `end_highlands`, así que siempre hay isla debajo), una cada 384 bloques más o menos (separación
  24 chunks). Altar de obsidiana con la **fragua del vacío apagada**, un yunque astillado, varas del End, un cofre
  con `forja:chests/fragua_del_vacio` y **guardias**: dos corazas vacías y un shulker sobre un pilar.
- **Encenderla**: con un **ojo de ender** (se gasta). Despierta igual: todo monstruo a 24 bloques va a por ti y se
  levantan **dos corazas vacías** más.
- Funciona como la de almas, con **perlas de ender** de combustible, y **solo funde eterio y solo en el End**.

### 3.3 Nadie más las hace

`Alloys.match` (la estrella de la mesa de forja y la montadora) y el crisol saltan las recetas de fragua; las cubas
no aceptan sus ingredientes como metal. Las tres siguen en `Alloys.ALL` (son materiales y tienen lingote), pero
`Alloys.place(recipe)` dice dónde se hacen. JEI las enseña en su categoría de aleaciones con «Solo en la fragua de
almas, en el Nether» o «Solo en la fragua del vacío, en el End».

### 3.4 El marco de portal se va de la Fragua caída

La fragua de la ruina ya no abre un marco de portal estelar (eso ya lo hace la Forja Profunda del Bastión). La
`fragua_apagada` sigue registrada y sigue abriéndose en un marco en los castillos de mundos viejos, que es para lo que
estaba; en una Fragua caída se convierte en fragua de almas. La prueba `theOldForgeNoLongerSummons` pasa a
comprobar las dos cosas.

## 4. Botín y guía

- **Fragua caída**: uno de sus dos cofres pasa a `forja:chests/fragua_caida`: siempre una **nota del herrero**
  (papel con nombre y las dos recetas escritas) y una o dos varas de blaze para encenderla; además polvo de blaze,
  tierra de almas, basalto, crema de magma, a veces un lingote de fatuo o de magmacero de muestra y rara vez
  chatarra de netherita. El otro cofre sigue siendo de forja abandonada.
- **Fragua del Vacío**: `forja:chests/fragua_del_vacio`: la nota con la receta del eterio, uno o dos ojos de ender,
  perlas, coro reventado, piedra del End, a veces un lingote de eterio y rara vez un caparazón de shulker.
- **Libro III (La fundición)**: capítulo nuevo **«Aleaciones de fragua»** (`aleaciones_lejanas`) con las tres
  recetas, sus rasgos y dónde está cada fragua.
- **Libro VI (El Bastión y el Herrero)**: capítulo nuevo **«Las fraguas lejanas»** (`fraguas_lejanas`) con las dos
  ruinas, cómo se encienden y qué despierta. El texto de la ruina del Nether en «Ruinas» cambia de clave
  (`ruinas.nether_almas`) para no pisar el archivo de los libros 0 y I que se está reescribiendo.
- Los textos nuevos van en `tools/lang_aleaciones.py`, aparte de `lang_libros.py`.

## 5. Arte

- Lingotes y kits de reparación: de la familia de `tools/lingotes.py` como todos (variante A el lingote, B el kit),
  cada uno con su marca: fatuo, dos lenguas de fuego de almas sobre acero ahumado; magmacero, magma encendido en las
  juntas del basalto; eterio, dos motas que se levantan de la barra. El tinte de piezas y armaduras es el color de la
  tabla (`ALLOY_COLORS`), y por estar ahí cada una tiene su kit sin tocar nada más.

En `tools/aleacion_nether.py`, llamado desde `generate_assets.py`:

- Fragua de almas apagada y encendida (piedra negra con boca de fuego de almas; encendida, el hogar azul y luz 13),
  fragua del vacío apagada y encendida (piedra del End y púrpura, encendida con el hogar violeta), costra de magma.
- Partículas: la de almas echa llamas de alma y almas (`forja:alma`); la del vacío, partículas de portal y de vara
  del End; la costra, humo al volver a ser lava.

## 6. Pruebas

- `FraguasLejanasGameTests`: se enciende con su objeto (y no con otro), despierta a los guardias, funde la tanda
  con combustible y sin él no; las otras mesas, el crisol y la fragua equivocada se niegan; fuera de su dimensión no
  funde; las tres valen para **todas** las piezas; los rasgos hacen lo que dicen (llama fatua a un blaze, costra
  sobre lava, levitación, el vacío te devuelve); el botín trae la nota; las plantillas traen su fragua y sus guardias.
- `PortalGameTests.theOldForgeNoLongerSummons`: la plantilla de la Fragua caída ya no trae `fragua_apagada`, y su
  fragua no abre marco.
- El informe de equilibrio las mide solas (están en `ForgeMaterial.values()`); el sangrado de la llama fatua entra
  en la pelea simulada.
- Cliente: `FORJA_SOLO=fragua_caida` filma la fragua de almas encendida y un objeto de fatuo y de magmacero;
  `FORJA_SOLO=fragua_vacio` filma la ruina del End y un objeto de eterio. Hojas de contacto en
  `E:\IA\Claude\Forja_capturas_mejoras\aleacion_nether\` y `...\aleacion_end\`.
