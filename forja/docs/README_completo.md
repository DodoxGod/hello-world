# Forja

Mod de Fabric para Minecraft 26.2. Las herramientas, armas y armaduras se hacen pieza por pieza: las
piezas se cortan en la **Mesa de piezas** y se arman en la **Mesa de forja**. Cada pieza puede ser de
un material distinto, y las piezas que pongas deciden qué objeto sale. Los objetos forjados no se
encantan: se les ponen **mejoras** con objetos, y cada mejora sube en porcentaje. Todo viene explicado
en la **Guía de forja**, un libro.

## Mesas

Las dos mesas se hacen igual: 3 lingotes de hierro arriba, tablones a los lados y abajo en las esquinas,
y en medio una **piedra de afilar** (Mesa de piezas) o una **mesa de crafteo** (Mesa de forja).

```
H H H
T X T      H = lingote de hierro, T = tablones, X = piedra de afilar o mesa de crafteo
T   T
```

Las dos mesas tienen su propia interfaz: marco de madera con esquinas de hierro, ranuras con borde y,
en la Mesa de forja, una estrella grabada.

**Plantillas:** la **Plantilla base** se hace con dos palos y dos tablones en diagonal (salen dos). En
la Mesa de piezas se graba con la forma de una de las 32 piezas (Plantilla de cabeza de pico, de hoja...).
Una plantilla grabada es un molde: no se gasta al usarla, y su forma es para siempre.

**Mesa de piezas** (crear y desarmar):

- **Piezas:** pones la plantilla y el material. Sin plantilla la rejilla de formas está apagada; con
  una plantilla base, clic en una forma la graba. Con una plantilla grabada y material suficiente sale la pieza.
- **Desarmar:** pones un objeto forjado y te devuelve sus piezas. Si la cabeza o la placa está muy
  gastada (menos de 50% de durabilidad) se pierde. Cada mejora sale en un **orbe de mejora** con la
  mitad de su porcentaje (Filo 80% → orbe de Filo 40%). Una pieza suelta se recicla en la mitad del
  material que costó (mínimo 1).

**Mesa de forja** (juntar y mejorar), un solo menú con cinco ranuras en estrella y una al centro:

- **Forjar:** piezas en las puntas. En el centro se ve la herramienta que saldría y a la derecha sus
  estadísticas; el botón Forjar gasta las piezas y la deja en el centro. Si faltan piezas te dice cuáles.
- **Mejorar:** herramienta en el centro e ingredientes en las puntas. A la derecha ves el porcentaje
  antes y después, la barra y lo que hace; el botón Mejorar gasta solo lo necesario para llegar al 100%.
- **Cambiar piezas:** herramienta en el centro y piezas en las puntas. Le cambia esas piezas y conserva
  sus mejoras. Cada estadística muestra cuánto sube (verde) o baja (rojo) respecto al objeto actual.
  El tooltip del inventario hace lo mismo contra lo que llevas equipado del mismo tipo (armadura puesta,
  escudo en la mano izquierda, herramienta o arma en la mano).
- **Libros encantados y orbes:** herramienta en el centro y libros encantados u orbes de mejora en las
  puntas. Cada encantamiento que corresponde a una mejora que el objeto acepta la sube al porcentaje de
  su nivel (Filo III de V = 60%, Irrompible III = 100%) y nunca la baja; cada orbe suma su porcentaje.
  Se saltan las mejoras que chocan con las que ya tiene.
- **Fusionar orbes:** sin nada en el centro, dos o más orbes de la misma mejora en las puntas se funden
  en uno que suma sus porcentajes (hasta 100%).
- **Reparar:** herramienta gastada en el centro y su material en las puntas (el de su cabeza o placa).
  Cada objeto repara un cuarto de la durabilidad, como el yunque, y solo gasta lo necesario. Si esos
  mismos objetos sirven para una mejora, gana la mejora.

## Objetos rotos

Lo forjado no desaparece cuando se acaba su durabilidad: queda **roto** en la mano o puesto. Roto no
sirve: armas y armadura pierden su daño y su protección, las herramientas minan como la mano y no
sueltan menas, no se puede usar con clic derecho (arco, ballesta, escudo, lanza) y sus mejoras, rasgos
y encantamientos se apagan. Tampoco gana maestría. Se arregla en la estrella de la Mesa de forja con su
material; Autorreparación y el rasgo Llanto también lo reparan. El tooltip avisa en rojo.

**Kit de reparación** (lingote de hierro + cuero + hilo, sin forma): tómalo con el cursor y haz clic
derecho sobre un objeto forjado del inventario para repararle el 15% de su durabilidad (mínimo 20).
Sirve lejos de la mesa y también saca a un objeto de roto.

## Maestría y conjuntos

- **Maestría:** los objetos forjados ganan experiencia al usarse: herramientas al minar, armas al golpear
  (+1) y al matar (+3), arcos y ballestas al acertar flechas (+2), escudos al bloquear (+2) y armadura al recibir
  golpes (+1). Suben hasta el nivel 10 (50, 150, 300, 500, 750, 1050, 1400, 1800, 2250 y 2750 de
  experiencia). Cada nivel da +3% de durabilidad y además: herramientas +4% de velocidad de minado,
  armas +0.2 de daño, armadura +0.1 de dureza, arcos +3% de tensado y +0.05 de daño de flecha, escudos
  2% más rápidos al cubrir y ante hachas. El tooltip muestra el nivel y el progreso. Cambiar piezas o
  mejorar la conserva; desarmar la pierde. Con maestría 10 el nombre brilla como un objeto épico.
- **Dones:** al llegar a maestría 10, pon el objeto en el centro de la Mesa de forja y un **sello** en una
  punta. Cada sello se fabrica sin forma con un lingote de oro y dos materiales propios, y solo sirve si
  ese don vale para ese objeto. Solo uno por pieza, y es permanente. Hay **12**: Filo eterno, Cazador y
  Duelista (armas), Minero (herramientas), Baluarte y Muralla (escudos y armadura), Viajero y Aeronauta
  (armadura y alas), Pescador (cañas), Cargador (flechas), Jinete (monturas) y Tirador (arcos y
  ballestas). El libro los lista con lo que hace cada uno.
- **Conjunto:** casco, pechera, grebas y botas con placas del mismo material dan +2 de armadura, +2 de
  dureza y un bono del material mientras las llevas puestas. El tooltip de cada pieza cuenta cuántas
  llevas y dice el bono; las piezas rotas no cuentan. La tabla de los 31 materiales con su durabilidad,
  minado, daño, rasgo y bono de conjunto está en [docs/MATERIALES.md](docs/MATERIALES.md), que también
  escribe el test.

## Logros, botín y recetas

- **Logros:** pestaña propia de Forja con 42 logros, de grabar la primera plantilla a matar al Herrero
  Caído, pasando por el arsenal, la maestría, los dones, las técnicas, los eventos, los encargos y la
  obra maestra.
- **Botín:** los cofres de herreros de aldea (herramientas, armas y armaduras) guardan plantillas base,
  plantillas grabadas de su oficio y a veces equipo forjado. Mazmorras, minas abandonadas, fortalezas,
  templos y portales en ruinas pueden tener plantillas grabadas y equipo gastado, a veces con una mejora.
  Los tesoros de bastión pueden tener armas de netherita. La pesca de tesoro, las recompensas de las
  cámaras de desafío (más en las ominosas), las ciudades antiguas y del End pueden dar orbes de mejora; las
  antiguas, equipo de eco y las del End, de púrpura. Los piglins rara vez truecan un orbe de Absorción o
  de Aspecto ígneo.
- **Leyendas:** de vez en cuando el botín guarda una pieza con nombre propio (Aliento de Invierno, Veta
  Madre, Sed del Ocaso...), en color épico y con dos mejoras al 100% que suelen formar una sinergia.
  Aparecen en ruinas (12%), tesoros de bastión (8%) y ciudades antiguas o del End (10%).
- **Forja abandonada:** en llanuras, sabanas, taigas, desiertos, zonas nevadas y bosques aparecen
  ruinas de una forja (muros de ladrillo agrietado, techo a medio caer) con una Mesa de forja, una
  Mesa de piezas, un yunque y un cofre con metales, plantillas grabadas, a veces equipo gastado con
  mejoras y orbes. Dentro vive el **Herrero ermitaño**, un Forjador de nivel 2 con el que ya puedes
  comerciar.
- **Monstruos:** zombis, esqueletos y vindicadores que aparecen con armadura o armas de hierro, oro,
  cobre, cuero o diamante tienen un 35% por pieza de llevarla forjada (con forro y mango al azar). Si la
  sueltan puede venir rota. Las piezas encantadas y los arcos de los esqueletos siguen siendo vanilla.
- **Aldeanos:** herreros de herramientas, de armas y armeros venden plantillas base (nivel 1), plantillas
  grabadas de su oficio (niveles 2 y 3: cabeza de pico, de martillo, hoja, punta de lanza, placa de
  pechera y de escudo) y en nivel 4 un orbe de Eficiencia, Filo o Protección al 30%. El herrero de
  herramientas de nivel 5 vende un mapa hacia la forja abandonada más cercana (12 esmeraldas y una brújula).
- **Forja de aldea:** algunas aldeas tienen una herrería con las dos mesas, yunque, alto horno,
  chimenea, puerta y cama (de piedra y roble, o de arenisca y acacia en el desierto); el aldeano que
  vive ahí suele volverse Forjador.
- **Forjador:** un aldeano sin oficio que toma una Mesa de piezas se vuelve Forjador. Vende plantillas
  base, kits de reparación y mangos de hueso (nivel 1), plantillas grabadas de mango, atadura, guarda,
  forro, brazos de arco, cuerda y cabeza de mazo (niveles 2 y 3), orbes de Irrompible, Filo, Eficiencia
  y Protección (niveles 3 y 4), y de maestro un orbe de Reparación al 50% y el mapa de forja abandonada.
- **Piedra de afilar:** no acepta objetos forjados (borraría sus mejoras); para sacarlas, desarma.
- **Recetas:** las mesas y la guía aparecen en el libro de recetas al conseguir hierro o un libro; la
  plantilla, al conseguir tablones. Todos los jugadores reciben la Guía de forja la primera vez que entran.

## Guía de forja

Un libro (receta sin forma: libro + lingote de hierro). Al usarlo se abre con portada, índice clicable
y capítulos: Mesas (con sus recetas dibujadas), Objetos, Piezas, Materiales, Rasgos, Mejoras (por tipo
de objeto), Maestría, Mundo (orbes, objetos rotos, botín, monstruos, aldeanos y adornos) y Estadísticas. Pasar el ratón por cualquier entrada muestra su tooltip completo, con las
estadísticas en color. Se cambia de página con las flechas, la rueda del ratón o el teclado.

## Materiales y aspecto

Madera, piedra, hueso, cuero, cobre, hierro, oro, amatista, diamante, obsidiana y netherita, más nueve
materiales con **rasgo**:

| Material | Rasgo | Efecto |
|---|---|---|
| Esmeralda | Afortunado | armas +1 Botín, herramientas +1 Fortuna, armadura +1 de suerte por pieza |
| Fragmento de prismarina | Acuático | herramientas minan normal bajo el agua, armas Empalamiento II, armadura nada más rápido y aguanta más el aire |
| Vara de blaze (no sirve de cabeza) | Ígneo | armas y herramientas prenden fuego, arcos lanzan flechas de fuego, armadura acorta el fuego |
| Cuarzo | Afilado | armas y herramientas hasta +3 de daño que baja con el desgaste, armadura Espinas I |
| Bloque de púrpura | Del End | herramientas 30% de mandar lo minado al inventario, armas 10% de teletransportar al enemigo, armadura 5% por pieza de esquivar |
| Obsidiana llorona | Llanto | de noche se repara 2 de durabilidad cada 5 s |
| Fragmento de eco | Resonante | herramientas: al romper una mena se contornean las iguales a 6 bloques, a través de las paredes (3 s); armas +1 Brecha; arcos y ballestas +1 Perforación; armadura inmune a Oscuridad y Ceguera |
| Ladrillo de resina | Pegajoso | todo +1 Irrompible; armas y herramientas dan Lentitud II 2 s; flechas Lentitud 3 s; armadura ralentiza 1 s por pieza a quien te golpea |
| Escama de armadillo (no sirve de cabeza) | Acorazado | armadura +1 Protección contra proyectiles y +1 contra explosiones por pieza; armas y herramientas +20% de resistencia al empuje en la mano |


- La armadura con placas de oro calma a los piglins, igual que la de oro de Minecraft.
- Los íconos usan los sprites originales de Minecraft (pico, hacha, pala, azada, espada, maza,
  armaduras de hierro y arco) separados por partes, y cada parte toma el color de su material. El
  martillo y la lanza tienen pixel art propio; la lanza además tiene su versión larga en la mano, como
  las lanzas de Minecraft.
- La armadura puesta tiene su propio diseño en 3D: placas con remaches y bandas, y un forro visible
  en bordes, cuello, cintura y botas. Placa y forro llevan cada uno el color de su material. Admite los
  adornos de armadura de la mesa de herrería.
- La **cabeza** u **hoja** decide el nivel, la velocidad de minado y el daño. El **mango** cambia la
  durabilidad y la velocidad de ataque. La **placa** decide la armadura y la dureza; el **forro** suma
  durabilidad.
- El **escudo** es un modelo 3D propio con placa, borde y asa de colores distintos.

## Estadísticas con color

Bajo el nombre de todo objeto forjado van **sus piezas dibujadas en fila**: el icono dice qué pieza es
y el texto de qué está hecha, en el color del material.

Los objetos forjados y las piezas muestran sus estadísticas en el tooltip, en la Mesa de forja y en la
guía de materiales. Cada número se colorea comparándolo con todas las combinaciones de materiales
posibles para ese mismo objeto (o todos los materiales posibles para esa pieza):

**rojo oscuro** (lo peor) → rojo → **blanco** (el promedio) → verde → azul → **morado** (lo mejor).

En las estadísticas donde menos es mejor (tiempo en cubrirse con el escudo, bloqueo por hacha,
preparar la carga de la lanza) la escala se invierte.

## Recetas (según las piezas)

| Objeto | Piezas |
|---|---|
| Pico | cabeza de pico + mango + atadura |
| Hacha | cabeza de hacha + mango + atadura |
| Pala | cabeza de pala + mango + atadura |
| Azada | cabeza de azada + mango + atadura |
| Martillo (mina 3x3) | cabeza de martillo + mango + atadura |
| Picahacha | cabeza de pico + cabeza de hacha + mango |
| Espada | hoja + mango + guarda |
| Daga | hoja + mango | (se puede lanzar con Lanzacabezas) |
| Espadón | hoja + hoja + mango + guarda |
| Lanza | punta de lanza + mango + atadura |
| Mazo | cabeza de mazo + mango + atadura |
| Guadaña | hoja + mango + atadura |
| Casco / Pechera / Grebas / Botas | placa + forro |
| Arco | brazos de arco + cuerda + mango |
| Ballesta | brazos de arco + cuerda + mango + guarda |
| Escudo | placa de escudo + borde de escudo + mango |
| Caña | mango + cuerda |
| Alas | membrana + forro |
| Báculo (mesa mayor) | núcleo + engaste + mango |
| Grimorio (mesa mayor) | núcleo + tapas + remache |

La **lanza** funciona como las de Minecraft (golpe corto y carga al mantener clic derecho); la punta
decide el daño, lo fuerte de la carga y lo rápido que se prepara. El **mazo** tiene el golpe en caída
de la maza; la cabeza multiplica ese daño (hierro x1, netherita x1.2). El **espadón** tiene **Torbellino** (agachado + clic derecho): corta todo a 3.5 bloques, gasta 3 de
durabilidad y tarda 6 s. El **martillo** y el **mazo** tienen **Sismo** (agachado + clic derecho con los
pies en el suelo): lanzan por los aires a todo lo que esté a 4.5 bloques y lo dejan lento, gastan 4 de
durabilidad y tardan 8 s.

La **guadaña** es lenta, barre
como una espada, llega 0.75 bloques más lejos y con clic derecho sobre un cultivo maduro cosecha y
replanta 3x3 (5x5 o 7x7 con Cosechador).

La **caña** dura según el mango y la cuerda, y acepta Cebo y Suerte del mar.

Las **armas mágicas** no tienen elementos ni maná: se montan alrededor de un **núcleo**, y el material del
núcleo es la magia — su color es el del hechizo y su mordida, el daño. El **báculo** de media luna lanza con
clic derecho un **proyectil** recto de ese color (0,7 s de espera). El **grimorio** abre un **área de 3 bloques
de radio a 5 bloques delante de ti**, del color del círculo de su tapa, que golpea al abrirse y **deja una runa
6 s**: muerde dos veces por segundo a lo que la pise, lo ralentiza y destella cada vez que muerde (3,5 s de
espera). **Las mejoras de arma viajan en el hechizo**: el proyectil y la apertura del área cuentan como golpes
del arma (Filo, Escarcha, Vampirismo, Tormenta, maestría…); los mordiscos de la runa no.

Tienen además **ocho mejoras propias**: las dos aceptan **Conjuro veloz** (−40 % de espera), **Sobrecarga** (cada
4.º hechizo sale grande y pega el doble; el 3.º avisa) y **Resonancia** (el hechizo se repite al 50 %). El báculo,
**Prisma** (abanico de tres) y **Buscador** (el proyectil persigue a quien te ataca). El grimorio, **Tinta
indeleble** (la runa dura el doble), **Vórtice** (cada mordisco arrastra hacia el centro) y **Santuario** (tu
propia runa te cura y te endurece). Sinergias: **Enjambre** (Prisma + Buscador: cinco proyectiles que persiguen)
y **Colapso** (Vórtice + Tinta indeleble: la runa estalla al apagarse).

**Nada del mod lleva el brillo de encantamiento**: una pieza forjada enseña de qué está hecha, tenga las
mejoras que tenga. Lo de Minecraft sigue brillando como siempre.

Las **alas** se llevan en el pecho y planean como un elytra. La **membrana** decide cuánto vuelas: la
ligereza del material (la misma que hace un mango rápido) baja tu gravedad y el roce del aire hasta un
12%, así que unas alas de oro o amatista vuelan mucho más lejos que unas de obsidiana, que a cambio
duran seis veces más (111 frente a 713 de durabilidad). Ganan maestría mientras vuelas, admiten el don
Viajero y la mejora **Aerodinámica** (2 elytras), que convierte tu bono de velocidad de movimiento en
empuje al planear: con Presteza al máximo notarás el tirón.

En el **arco**, los brazos deciden qué tan rápido se tensa y el daño extra de la flecha; con brazos de
madera tensa igual que el arco de Minecraft y dura casi lo mismo. La **ballesta** carga como la de
Minecraft (Carga rápida la acelera), dispara flechas y cohetes, y sus brazos suman daño a las flechas
igual que en el arco; se sostiene apuntando cuando está cargada. En el
**escudo**, la placa decide la durabilidad y cuánto tarda en cubrir; el borde, cuánto lo bloquea un
hacha. Si cubres **justo cuando llega el golpe** (los primeros 6 tics tras levantarlo) haces una
**parada perfecta**: el daño vuelve al atacante, sale despedido y queda lento y débil 3 s. Agachado y
con clic derecho **con algo delante**, el escudo da un **golpe de escudo**: empuja y
hace daño (2 más el daño y la dureza de la placa), gasta 2 de durabilidad y tarda 3 s en volver; si no
hay nada delante, cubre como siempre.


## Aleaciones (el calor manda)

La mesa de forja funde metales, pero solo con el calor que tiene **debajo**:

| Mesa | Bajo ella | Aleaciones |
|---|---|---|
| Fría | nada | ninguna |
| Templada | fogata, fuego | bronce, latón, peltre |
| Caliente | magma, fuego de almas, alto horno encendido | acero, electro |
| Fundida | lava, caldero de lava | damasco, acero estelar, obsidiacero, almacero, vidriacero |
| **Forja blanca** | **nada: ningún bloque da este calor** | **solacero, lunacero, acero vivo** |

Los mismos ingredientes dan cosas distintas según dónde esté la mesa, así que **dónde construyes la forja
importa**. Las aleaciones son materiales completos: se cortan en piezas como cualquier otra cosa.

**La forja blanca no la alcanza ninguna mesa.** No hay bloque que la dé, y la técnica *Fuelle largo* —que
lee el fuego un escalón más caliente— se para en Fundida a propósito. Las tres últimas aleaciones salen
del **crisol de obsidiana** o no salen.

## El crisol (alear a escala)

La mesa alea de una en una. El **crisol** es la otra mitad: entra por arriba, el combustible por el lado
y sale por abajo — la convención del horno — así que se automatiza con tolvas sin aprender nada nuevo.
Clic derecho abre **su propia pantalla**, con la colada dibujada en una pila de piedra: el nivel sube con
lo lleno que esté, toma el color del metal, la superficie ondula, un brillo la cruza y saltan chispas
mientras hay fuego; al lado, la brasa bajando, y en texto qué está colando y qué calor alcanza.

**Nada del sistema es vanilla.** Quema **ascuas**, y solo ascuas: las sueltan las pavesas y las deja el
propio crisol cada vez que funde una herramienta, así que el bucle se paga solo. Un **farol de pavesa
debajo** lo hace funcionar sin gastar nada.

Hace dos cosas: si los dos huecos tienen los ingredientes de una aleación y el crisol da el calor, la
cuela; si no, **funde lo que un herrero haya hecho y devuelve el material**.

| | Barro | Hierro | Obsidiana |
|---|---|---|---|
| Receta | 8 peltre | 8 bronce + crisol de barro | 8 obsidiacero + corazón de forja + crisol de hierro |
| Calor | Templada | Caliente | **Forja blanca** |
| Cabida | 8 | 16 | **32** |
| Por colada | 10 s | 6 s | **3 s** |
| Devuelve al fundir | 50 % | 75 % | **100 %** |
| Aleaciones | bronce, latón, peltre | + acero, electro, cinerio, voltaico | **todas, incluidas las tres de forja blanca, +1 lingote de regalo** |

El de barro hará bronce toda la vida y no tocará el damasco; el de obsidiana lo funde todo, cuesta mucho
más y encima regala un lingote por colada. La cabida es real: **una tolva tampoco puede meterle un stack
a un puchero de barro**.

## La cuba de colada (el almacén)

Cristal y bronce, y guarda **metal fundido**. **Dos cubas que se tocan son un solo depósito** — 256 por
cuba, así que un 3x3x3 son casi 7000 lingotes — y se ve el nivel subir por el cristal (las caras entre
dos cubas no se pintan, el banco se lee como un cuerpo de metal).

- Se llena por **la cuba más baja con sitio** y se saca por **la más alta con metal**, así que romper una
  del medio de una pared no corrompe nada: al tick siguiente son dos depósitos, y la rota suelta lo suyo.
- **Un depósito, un metal.** Hacen falta bancos separados, y un taller acaba pareciendo una fundición.
- **El crisol pegado a una cuba vuelca en ella** en vez de en su hueco de salida.
- **Y saca de ella sus ingredientes colando al doble de velocidad**, porque el metal ya está líquido. Ese
  es el motivo de construirlas: no son más densas que un cofre y nunca pretendieron serlo.
- Por abajo empuja un montón por segundo a lo que tenga debajo (tolva, cofre, armario de piezas).

**Conductos de colada** (8 por 6 de bronce): **no guardan nada** — son una conexión, no un contenedor.
El crisol busca sus cubas a través de ellos como si las tocara (hasta 64 bloques), y un banco se vacía
por el tramo hacia lo que haya al final. Se enganchan solos a conductos, cubas, crisoles, cajas, mesas y
cualquier cosa que guarde objetos.

**Y tienen forma de suelo, no de tubería.** Una losa que se pisa, con el metal hundido a ras en un canal
de 4 píxeles por el centro y un bordillo del metal de su calidad a cada lado. Alumbran. Un tramo que
tiene que subir cae al **tubo cerrado** en ese bloque, porque un canal abierto no lleva nada pared
arriba. La piedra es la misma en las tres calidades y sólo cambia el bordillo, así que un tramo que
mezcle calidades se sigue leyendo como una sola línea de luz cruzando el taller.

**El caño de colada** (4 bronce + 1 conducto) es el final del tramo: tiene el suelo agujereado y lo que
le llega **cae**, hasta 5 bloques, sobre lo que haya debajo —una cuba, una caja, una mesa, otro canal—.
Es la única forma de dar de comer a algo que está **en otro piso**, y **sólo cuela cuando tiene dónde
caer**: si ves el chorro, el tramo está conectado, y si se paró es que algo se movió debajo. Caer cuesta
4 de calor por bloque, peor que el peor conducto.

**Y el metal se enfría.** Una cuba sola **cuaja en unos 100 segundos** y deja de servir para colar; un
farol de pavesa, lava, magma, fuego o un crisol encendido al lado la mantienen líquida. Si cuaja, un
crisol encendido al lado **la vuelve a fundir, comiéndose el 15 %** de lo que había. Eso convierte la
fundición en algo que hay que **mantener caliente**, no en un cofre con otro nombre.

Por eso los conductos tienen **tres calidades**, y sólo la primera se fabrica: las otras dos **se cuelan
en la caja de moldeo**.

| | Bronce | Acero | Damasco |
|---|---|---|---|
| Pierde por tramo | 6 de calor | 3 | **1** |
| Receta | 6 bronce → 8 | 6 acero + 3 conductos → 3 | 6 damasco + 3 de acero → 3 |

Un tendido largo de bronce entrega el metal ya medio cuajado; uno de damasco lo entrega caliente.

La colada se dibuja a mano, no con un modelo: **nivel continuo**, la textura **corre como lava** (hacia
arriba por los costados, de lado por la superficie) y cada metal tiene **su color, su opacidad y su
brillo** — los dos últimos salidos del propio color, así que un metal pálido se lee fino y lustroso y uno
oscuro espeso y mate. Una banda de luz cruza la superficie cada pocos segundos, y alumbra sola.

## La caja de moldeo (piezas sin mesa)

Donde termina la línea. Metes una **pieza acabada** con **acero refractario** al lado y el acero se
vuelca sobre ella: **la pieza se destruye** y sale su **molde**. Es lo único del mod que rompe a
propósito algo que hiciste, y debe serlo — un molde vale una pieza porque a partir de ahí no vuelves a
cortar esa pieza a mano.

Con el molde dentro, la caja **cuela esa pieza con el metal de las cubas** que alcance (pegadas o por
conducto), gastando lo que cuesta la pieza. El molde no se gasta. Sin plantilla y sin mesa: **mena →
crisol → cubas → caja → piezas por abajo**.

| | Barro (peltre + arena) | Acero (acero refractario) | Damasco (+ damasco) |
|---|---|---|---|
| Aguanta | hasta 320 de dureza | hasta 700 | **cualquier metal** |
| Por pieza | 6 s | 4 s | **2 s** |

El límite sale de la **dureza del propio material**, así que un metal nuevo cae solo en el nivel que le
toca. El **acero refractario** es la única aleación del mod que no es metal de equipo: existe para
cortarse en moldes.

### Coladores (y la única forma de que esto salga mal)

El metal se cuela al caer. Un **colador de barro** aguanta hasta 320 de dureza y nada más, y **no se
fabrica uno mejor: se infusiona**. Pones el que tengas en la ranura de arriba, le dejas caer encima 8 de
un metal más duro y **sale hecho de ese metal**, aguantando lo que aguante él.

Si el colador **no aguanta** lo que le echas, **revienta en la colada** y la pieza sale **basta: un 15 %
menos de todo**, y se nota en el objeto acabado aunque la montes con piezas buenas. Sin colador, igual de
basta. Es el único riesgo de la fundición, y es siempre culpa del herrero.

## Las mesas de colada (la herramienta entera)

La caja de moldeo hace **piezas**. Las mesas hacen **la herramienta**. Son tres, de piedra oscura, y
**las tres cuelan exactamente igual**: sólo se diferencian en lo bien que la piedra aguanta el calor.

**El marco.** Metes una **herramienta acabada** en la caja de moldeo con **6 de acero refractario** y sale
su **marco**: no el hueco de una pieza, sino todos los huecos de una herramienta a la vez. Cuesta la
herramienta, como el molde cuesta la pieza, y no se gasta al colar.

**La mesa no tiene pantalla.** Pones el marco encima —se ve tumbado en el lecho—, **cae justo el metal que
vale esa herramienta** (5 para un pico, ni una gota más) desde las cubas que alcance, y a los 5 segundos
recoges el pico hecho. Lo ves entero desde fuera: el chorro cayendo, el metal subiendo en el lecho y la
forma de la herramienta brillando por debajo. Entra por arriba y sale por abajo, así que se automatiza
con tolvas como todo lo demás.

| | Mesa de losa | Mesa de brasa | Mesa de almas |
|---|---|---|---|
| Piedra | pizarra pulida + hierro | piedra negra + oro | basalto + obsidiana |
| Pierde calor | 4/s | 2/s | **1/s** |
| Tras 25 s sin fuego | 104 | 154 | **177** (de 200) |
| Sale **perfecta** (+5%) | 10% | 25% | **50%** |

**El calor es toda la dificultad.** Cada colada gasta 40 de calor; una mesa fría **no empieza ninguna** —no
toca las cubas siquiera— y la que empieza con las últimas brasas **cuaja antes de tiempo y sale basta**
(-15% en todo), la misma penalización que un colador reventado. Un **farol de pavesa debajo** la mantiene
llena para siempre, así que la piedra buena no es "más rápida": es la que te deja montar la fundición
lejos del fuego.

## Las aleaciones de fundición

Siete metales que la fundición hace y la mesa no del todo. Los cuatro primeros los alcanza cualquier
crisol lo bastante caliente; los tres últimos **sólo el de obsidiana**, porque sólo él llega a forja
blanca.

| Aleación | Calor | Receta | Rasgo |
|---|---|---|---|
| **Cinerio** | Caliente | 2 acero + 4 ascuas | **Ascua**: mientras ardes, +2,5 de daño y se repara 3 cada 5 s; la armadura te apaga el fuego un 35 % antes por pieza |
| **Voltaico** | Caliente | 2 latón + 4 redstone + 1 amatista | **Cargado**: cada golpe guarda carga; al cuarto salta a 2 enemigos a 5 bloques por la mitad del daño |
| **Almacero** | Fundida | 1 acero estelar + 2 placa hueca | **Animado**: llevado o empuñado, **para un golpe entero cada 30 s** sin tirar dados (20 s con el conjunto) |
| **Vidriacero** | Fundida | 1 obsidiacero + 4 cuarzo | **Diáfano**: no pesa — +3 % de velocidad y +15 % agachado por pieza, +0,3 de velocidad de ataque |
| **Solacero** | **Forja blanca** | 1 damasco + 3 varas de blaze | **Solar**: a cielo abierto y de día +3 de daño, **+6 y prende fuego a los no-muertos**; la armadura te cura 0,5 por pieza cada 5 s |
| **Lunacero** | **Forja blanca** | 1 obsidiacero + 3 fragmentos de eco | **Nocturno**: donde la luz no llega, +3,5 de daño; la armadura da Velocidad I, y visión nocturna con las cuatro piezas |
| **Acero vivo** | **Forja blanca** | 1 corazón de forja + 2 damasco | **Vivo**: cada muerte le repara 25 de durabilidad, y si está entera te cura a ti (el doble con el conjunto) |

Solacero y lunacero son el mismo truco mirando a lados distintos: uno vale a mediodía y el otro a
medianoche, y ninguno de los dos pregunta la hora —preguntan **cuánta luz hay donde estás**, que es una
pregunta más justa bajo tierra. Las tres recetas de forja blanca tienen **dos ingredientes a propósito**:
el crisol tiene dos huecos, así que una tercera cosa sería una receta que nada puede colar.

## El taller

- **Temple**: una pieza recién forjada sale caliente un minuto. Si la apagas en agua, lava, nieve polvo o
  miel, ese temple se queda para siempre (menos desgaste, fuego, ralentización o pegajosidad).
- **Forja perfecta**: al forjar algo nuevo, un martillo recorre un carril bajo el botón. Párarlo en el
  centro da +5% en todas las estadísticas, para siempre. El centro se ensancha con tu maestría de herrero.
- **Maestría de herrero** (10 niveles): forjar, cambiar piezas, mejorar y grabar dones te suben de nivel;
  a cambio la ventana del martillo es más ancha, cada ingrediente da más porcentaje y lo que sale de tu
  estrella nace con maestría. `/forja herrero`.
- **Herencia**: una pieza veterana (maestría 5+) se gasta para pasar la mitad de sus mejoras, y su don, a
  una nueva del mismo tipo.
- **Firma y afinidad**: todo lo que forjas lleva tu nombre y rinde un 2% más en tus manos.
- **Cobre**: el equipo con cobre coge pátina en cuatro fases con el uso; el panal la fija y la pátina
  completa da +10% de durabilidad.
- **Mesa de talabartería**: la única que hace **barda** (caballos, burros, mulas, camellos) y **armadura
  de lobo**, y no hace nada más.

## Combate

- **Parada**: el escudo forjado cubre desde el primer tick. La placa decide la **ventana de parada**; si
  el golpe entra ahí, lo devuelves, tiras al atacante, las flechas vuelven al arquero y tu siguiente
  golpe en un segundo vale el doble.
- **Frenesí**: dos golpes en 3 s abren una barra que sube hasta 5. Mientras está alta tus mejoras rinden
  por encima de su número: las baratas hasta x2,5 y las caras solo hasta x1,3.
- **Sangrado**: los críticos de daga y guadaña abren heridas que ignoran la armadura y se acumulan; al
  llenarse se vuelven marchitamiento. **Desgarro** sube el máximo.
- **Armas arrojadizas**: agáchate y usa un hacha o una daga (2/3 de daño, vuelve con **Retorno**); el
  escudo con **Bumerán** empuja y aturde a tres y vuelve solo.
- **Mangual**: lento, reparte el golpe en área, aturde y pasa por encima de los escudos.
- **Guanteletes**: los puños más rápidos del mod, maestría al doble, hechos para el frenesí.
- **Gancho**: el garfio te lleva hasta un bloque o te trae a lo que engancha.
- **Flechas modulares**: punta y emplumado, 4 por forja, con cinco mejoras propias.
- **Pactos**: tres mejoras permanentes que dan mucho y cobran más (daño por hambre, daño por durabilidad,
  invisibilidad por armadura).

## Lo que te busca

- **Élites**: 1% de los monstruos, a partir del día 5 y lejos del spawn, con nombre, x5 de vida y una
  leyenda equipada. Sueltan una sola pieza.
- **Saqueadores de forja**: si llevas equipo forjado, de noche puede venir una banda de cuatro con un
  capitán que lleva una leyenda a maestría 10 y suelta orbes.
- **El Herrero Caído**: en una fragua-fortaleza del Nether. Se despierta poniendo la mano en su fragua
  apagada con 8 hierro estelar, 4 damasco, 1 estrella del Nether y 1 esquirla de eco. 320 de vida y tres
  fases: aprendices, reforja (inmune hasta apagarle las brasas) y lluvia de meteoritos. Suelta el
  **corazón de forja**, el mejor material del mod.
  La fragua que lleva en el pecho dice en qué fase está: **naranja** mientras se contiene (con un
  fogonazo en cada golpe básico) y **morada**, con lenguas de fuego saliéndole por la abertura y por la
  chimenea del hombro, cuando llama a los aprendices, se refunde, tira meteoritos o baja de su último
  cuarto de vida. Por los agujeros de la placa se le ven las brasas de dentro.
  Lo que no viene de un jugador le hace **un tercio**, y si las criaturas del mundo se le echan encima (3
  distintas en 10 s, o gólems y wardens que le intentan quitar un 10 % de la vida) hace **La forja reclama**:
  1,5 s de aviso, arrastra todo lo que no es de nadie a 12 bloques y lo deshace sin botín ni experiencia; nunca
  a jugadores, mascotas ni aprendices; una vez cada 40 s. Su lluvia de estrellas tira una estrella por llamada,
  cada 3 s, con 1 s de aviso.

## Eventos del cielo

Nueve eventos (meteoritos, tormenta arcana, niebla de almas, aurora, luna de sangre, eclipse, ventisca,
marea viva y **lluvia de pavesas**, que las trae tres veces más a menudo y ya avivadas). Cada uno trae una mejora que no existe en ningún otro sitio; con un **frasco de esencia** a
cielo abierto te la quedas como orbe. Los meteoritos además dejan **hierro estelar** en un cráter.
El **pararrayos de estrellas** atrae los meteoritos que iban a caer a 12 bloques o menos: no hay cráter, el
hierro queda a sus pies y aguanta 4.

## Objetos que se llevan

- **Talismanes** (7 piedras): solo cuenta el primero del inventario. Tratos de héroe, +1 armadura,
  crítico, esquivar flechas, maestría más rápida, respirar bajo el agua o aguantar los empujones.
- **Cinturón de herramientas**: guarda cuatro herramientas y te pone la buena al empezar a picar, con
  coste de durabilidad, medio segundo de espera y bloqueado en combate.
- **Encargos del Forjador**: pide una pieza exacta con una mejora al 50% y paga esmeraldas, un orbe bueno
  y una plantilla. Cambia cada día.


## Calor bajo la mesa

El bloque debajo de la mesa de forja decide lo que puede hacer: **fría** (nada), **templada** (hoguera,
fuego, horno encendido), **caliente** (magma, fuego de almas, alto horno) y **fundida** (lava). El calor
manda en las ocho aleaciones y, en lava, deja **derretir piezas sueltas del mismo material** para
recuperar la mitad de lo que costaron.

## Ajustes

`config/forja.json` se escribe solo la primera vez y tiene las cuatro probabilidades que deciden cuánto
te molesta el mundo (por comprobación, de 0 a 1):

```json
{
  "saqueadores": 0.06,
  "elites": 0.01,
  "eventos": 0.02,
  "corazas": 0.05
}
```

El libro lee esos números, así que si los cambias el libro sigue diciendo la verdad.

## Dependencias

- **GeckoLib 5.5.5** (obligatoria): anima al Herrero Caído y al autómata de forja.
- **JEI** (opcional): tres páginas propias con las piezas, las mejoras y las aleaciones.
- **Jade** (opcional): el calor bajo la mesa de forja y la fase del jefe, al mirarlos.

## Potencial de mejoras y mesa de extracción

Cada pieza forjada tiene un **potencial**: hasta dónde pueden subir sus mejoras. Sale de cómo se hizo —
piezas coladas en la fundición (hasta +20), el golpe del martillo (+5 decente, +10 perfecto), tu nivel de
herrero (+1 por nivel), la mesa (+10 la mayor, +5 el taller completo) sobre un suelo de 40 — y crece
después con la maestría de la pieza, con cada pacto (+10) y con la mejora **Recocido** (hasta +15). Se lee
en la descripción de la pieza y en la mesa.

- La **mesa de forja** no sube ninguna mejora del **50 %**; la **mesa de forja mayor** llega al 100 %.
- Por encima del **90 %** hace falta **fundente maestro** en la estrella (hierro estelar + 2 polvo de blaze
  + fragmento de eco): se gasta uno por mejora, la vez que cruza esa raya.
- Cada ingrediente rinde lo que dice hasta el 50 %, la mitad hasta el 75 % y la cuarta parte hasta el 100 %.
- **Ningún tope baja una mejora que ya esté puesta.** Pactos y mejoras de evento van por fuera de todos.
- Un **orbe** vale lo que costó: devuelve entero lo que guarda sobre una pieza limpia, y paga el precio
  creciente encima de la misma mejora o al fundirse con otro (dos del 50 % hacen uno del 75 %). Lo que una
  pieza no pueda tomar se queda en el orbe.

La **mesa de extracción** (latón, orbe vacío, afiladora y pizarra pulida) quita **una** mejora de una pieza
sin tocar nada más: eliges la mejora y pagas con sus propios ingredientes, un paso de receta por cada 25 %.
Con un **orbe vacío** (4 vidrio + amatista) la mejora sale **entera**; sin él se pierde. Un pacto no sale
nunca. Todos los números están en `forge/Potential.java`; el porqué de cada uno, en `docs/POTENCIAL.md`.

El potencial decide también **cuánto lleva** la pieza: cada mejora pesa de 1 a 4 (Filo 4, Castigo 2,
Empuje 1…) y la pieza aguanta `(potencial − 20) / 4` puntos — 5 la peor, 20 una perfecta. Pactos, mejoras
de evento, Recocido y sinergias no pesan. Las siete mejoras de **todo o nada** (Toque de seda, Reparación,
Infinidad, Multidisparo, Llama, Afinidad acuática, Visión nocturna) no tienen tope de porcentaje: las
ligeras se hacen enteras en cualquier mesa, las pesadas sólo en la mesa de forja mayor. La barra de carga
sale en el tooltip, en la mesa y en la rueda de extracción.

Su pantalla es un tipo de inventario propio, **la rueda**: la pieza en el centro y cada mejora como una
gema engastada alrededor (más grande cuanto más porcentaje; los pactos, oscuros y tachados). Eliges una
gema, sale su ficha y el pago en fantasma en la bandeja, y al extraer la gema vuela hasta la cuna del orbe.

## Armario de piezas

Tablones arriba y abajo, lingote + cofre + lingote en medio. 27 huecos que **solo aceptan cosas del
mod**: piezas, plantillas, orbes, sellos, talismanes, objetos forjados y los materiales propios. Filtra
también lo que le meten las tolvas, así que ordena solo.
Tiene su propia pantalla, tres cajones de madera con tiradores de latón, y la regla vale también **a
mano**: si llevas en el cursor algo que no es de herrero los cajones se apagan y te lo dice.

## Objetos que se llevan encima

El **yunque portátil** (3 lingotes + 2 cueros + bloque de hierro + palo) abre la mesa de piezas donde
estés, con su pestaña de desarmar: 128 usos, uno por apertura. No forja y no cuenta como taller.

## Materiales propios

Además de los 22 materiales de vanilla y las 8 aleaciones, el mod tiene tres que solo salen de él:
**hierro estelar** (lluvia de meteoritos), **corazón de forja** (el Herrero Caído) y **placa hueca** (la
coraza vacía). Son 31 en total.

## Obra maestra

Una pieza **nacida de un golpe perfecto**, **a maestría 10**, **con don grabado** y **firmada por ti** se
convierte en obra maestra: +3% en todo, marco de tooltip propio y aviso por chat. No se fabrica, se
reconoce.

## Técnicas del herrero

La maestría mide cuánto has trabajado; la técnica es la forma que tomó ese trabajo. En **maestría 3, 6 y
9** se abre una elección de tres en la pestaña **Técnicas** de la mesa de forja. Eliges una y las otras dos
se cierran para siempre.

| Maestría | Opciones |
|---|---|
| 3 | **Pulso firme** (ventana del martillo más ancha) · **Ahorro de metal** (cada lingote repara un tercio más) · **Ojo para el metal** (cada aleación da un lingote de más) |
| 6 | **Segunda templada** (recalentar una pieza para volver a templarla) · **Mano de orfebre** (orbes y libros dan 5 puntos más) · **Fuelle largo** (fundes un paso de calor más frío) |
| 9 | **Herencia limpia** (la herencia pasa 4/5 en vez de la mitad) · **Firma del maestro** (tu obra te responde al 4% en vez del 2%) · **Alma de forja** (1 de cada 10 piezas nace con una mejora al 25%) |

La pestaña marca con un punto dorado cuándo tienes una elección pendiente.

Una técnica es para siempre, con una excepción: el **martillo del maestro** que suelta el Herrero Caído
devuelve las tres elecciones y se gasta al hacerlo.

## Estructuras

| Estructura | Dónde | Qué tiene |
|---|---|---|
| Forja abandonada | llanuras, sabana, taiga, nieve, desierto, bosques | las dos mesas, un yunque, un cofre, un Forjador ermitaño, un autómata y una coraza vacía |
| Taller de montaña | prados, laderas, colinas barridas, picos, cerezos | la **mesa de talabartería**, mesa de piezas, cofre de talabartero y un caballo en su corral |
| Fragua caída | Nether | la **fragua apagada** (para despertar al jefe), sus dos mesas, dos autómatas, dos corazas vacías, lava y cofres |
| Campamento saqueador (2 variantes) | bosques, taiga, llanuras, sabana, nieve | empalizada, dos tiendas, hoguera, torre con campana, dos cofres de botín y la banda de cuatro saqueadores con su capitán |
| Túmulo del herrero | bajo tierra, en cualquier bioma del Overworld (y -48 a 8 de altura) | la tumba de un herrero: su yunque, su mesa de herrería, cofre y barril, y dos corazas vacías montando guardia |

El Forjador vende un mapa para cada estructura de superficie, y cada mapa apunta solo a la suya: forja abandonada (nivel 5), taller de montaña (nivel 4) y campamento saqueador (nivel 5).

## Los que guardan las forjas

**Autómata de forja**: piedra, hierro y un horno encendido en la barriga. 70 de vida, 12 de armadura, no
se le empuja y no le hacen nada el fuego, el veneno ni el hambre. Al caer se deshace en **las piezas de
las que está hecho**. Hay uno en cada forja abandonada y dos guardando la fragua del Nether.

**Coraza vacía**: una armadura que se levanta sola, con 45 de vida y una luz fría donde deberían estar
los ojos. **El acero comprado apenas la toca**: un golpe que no venga de algo forjado (o de una flecha
forjada) le hace solo el 35%. Suelta las placas de las que está hecha y, un 40% de las veces, **placa
hueca**. Hay una en cada forja abandonada, dos en la fragua caída, y de noche, si tienes una mesa de
forja cerca, alguna se levanta en la oscuridad y viene a verte.

Romperla no mata lo que llevaba dentro: **si hay otra coraza en pie a menos de 12 bloques, el alma se
muda a esa**, que se levanta con 8 de vida de vuelta, diez segundos de prisa y fuerza, y el mismo
objetivo que tenía la que cayó. Las salas con varias hay que despejarlas de golpe.

**Pavesa**: una jaulita de hierro con una brasa dentro, 12 de vida, y **lo único que vuela**. Se echa
encima desde arriba (4 de daño y te prende) y, junto a fuego, lava o una mesa de forja encendida, **se
aviva**: crece, pega 3 más, quema el doble y pica el doble de seguido; matarla avivada prende lo que
tenga al lado. Suelta carbón, polvo de blaze y a veces un orbe. Una en cada forja abandonada y tres
sobre la lava de la fragua caída.

Y se pueden **coger vivas**: con un farol vacío en la mano, clic derecho sobre una pavesa **avivada** y
se mete dentro. El **farol de pavesa** que sale da luz 15 y, **puesto bajo una mesa de forja, vale por la
lava** (calor Fundida), así que quien sepa cazarlas no vuelve a acarrear lava al taller. Es lo único del
mod que se consigue cogiendo algo vivo.

## Detalles que se ven

- **Marcos de tooltip**: las leyendas llevan marco dorado, lo que llega a maestría 10 marco ámbar y lo
  hecho de corazón de forja marco al rojo.
- **Barra de frenesí** en el HUD con cinco muescas, y **barra de vuelo** para las alas.
- **Indicador de calor** bajo el botón de forjar: fría, templada, caliente, fundida.
- **Taller completo**: si la mesa de forja tiene la de piezas y la de talabartería a 6 bloques, las
  mejoras rinden un nivel más y la ventana del martillo es más ancha.
- **Mestizaje**: cada rasgo distinto entre las piezas de un objeto vale +5% en todo.
- El jefe y el autómata **brillan en la oscuridad** por donde les sale el fuego.
- **JEI** (opcional): tres páginas propias con las piezas de cada objeto, las 120 mejoras y las aleaciones.

## Mejoras (120) y sinergias (28)

La lista completa —qué objeto acepta cada mejora, con qué se alimenta y qué hace al 100%— está en
[docs/MEJORAS.md](docs/MEJORAS.md). **La escribe el propio test** al correr `./gradlew runClientGameTest`,
así que no puede quedarse vieja.

Lo que conviene saber de memoria:

- Cada objeto que pones en las puntas sube su porcentaje; las mejoras que piden dos objetos los piden
  juntos, uno de cada uno por paso.
- Las que vienen de un encantamiento dan el nivel equivalente (Filo al 60% = Filo III).
- Las de un mismo grupo no se juntan, igual que en Minecraft: Filo/Castigo/Perdición/Brecha, las cuatro
  Protecciones, Fortuna/Toque de seda, Agilidad acuática/Paso helado.
- Dos mejoras al 50% en la misma pieza despiertan una **sinergia**.
- **Agachado** (shift) se apagan Veta, Excavación, Leñador, el 3x3 del martillo y el Magnetismo.

## Comando de prueba

`/forja kit` (requiere trucos) da las dos mesas, 4 plantillas base, la guía, arcos, una ballesta, escudos (uno con Púas, Rebote y Reflejos), lanzas
(una con Embestida), mazos (uno con Estallido de viento), armas y herramientas con cada rasgo,
armadura de púrpura, un pico gastado para desarmar y los ingredientes de las mejoras de arco, escudo,
lanza y mazo. Lo que no quepa en el inventario cae a tus pies. En el entorno de desarrollo el kit se da solo la primera vez que entras.

Otros comandos de prueba (requieren trucos) sobre el objeto forjado de la mano:

- `/forja maestria <0-10>` pone la maestría en ese nivel.
- `/forja mejora <mejora> <0-100>` pone una mejora a ese porcentaje (0 la quita), si sirve para ese objeto.
- `/forja orbe <mejora> <1-100>` da un orbe de mejora.

## Instalación

Fabric Loader 0.19.3+ y Fabric API 0.158.0+26.2 para Minecraft 26.2. Copia
`build/libs/forja-1.0.0.jar` en la carpeta `mods`.

## Desarrollo

```bash
JAVA_HOME="E:/IA/Claude/.tooling/jdk/jdk-25.0.4.1+1" ./gradlew build
```

- `python tools/generate_assets.py` genera texturas (a partir de los sprites del jar de Minecraft que
  guarda Loom), modelos, las 400 combinaciones de armadura, etiquetas y receta. Los enums de ese script
  tienen que coincidir con `PartType`, `ForgeType` y `ForgeMaterial`.
- `python tools/generate_lang.py` escribe los idiomas y avisa de claves que el código pide y no existen, de
  textos con un `%` que el juego leería como formato roto y de textos con distinto número de `%s` en
  español y en inglés. Las claves que el código arma con un id (`"trait.forja." + id`) no las puede ver:
  de esas se ocupa el test (`checkEverythingIsNamed`, y `rawKeys()` para el libro).
- `./gradlew runClientGameTest` abre el juego en español y comprueba el mod: estadísticas, mejoras
  (porcentajes, bloques, combinaciones, incompatibilidades, niveles de encantamiento, atributos),
  rasgos, los colores de las estadísticas, la lanza y el mazo con sus mejoras, el arco y la ballesta (disparan y
  tensa según el material), el escudo con Púas, Desarmar, los tooltips, las plantillas, la estrella de la Mesa de forja (forjar, mejorar y cambiar piezas), la guía, la armadura puesta, Lanzacabezas, Veta, el martillo, Fundición, Telequinesis, Multidisparo, Zancada, Ejecución, Nutrición, Purificación, Luz y Absorción.
  Guarda capturas en `build/run/clientGameTest/screenshots`.
- Secciones sueltas del test con `FORJA_SOLO=`: `onda`, `cielo`, `libro`, `hud`, `meteorito`, `jefe_reclama`,
  `pararrayos`, `pantallas`
  (mesas, crisol, caja de moldeo, armario y tooltip) y `particulas`. Medio minuto cada una. Lo que se
  juzga a ojo tiene su foto o su GIF en `docs/mejoras_graficas/`, con el registro de cada cambio.
- Las texturas de las pantallas salen todas de `tools/generate_assets.py` (`generate_gui_textures`,
  `generate_station_gui`, `generate_cabinet_gui`, `generate_book_gui`) con las mismas coordenadas que
  los menús; el test exige que cada casilla caiga sobre un cuadro pintado.
- Con la variable de entorno `FORJA_SOLO=onda` corre sólo la sección de la onda expansiva (unos 40 s en
  vez de cinco minutos) y deja las fotos `forja_onda_*`: es para lo que se juzga a ojo y hay que mirar
  una docena de veces. La onda está en `entity/Shockwave` y `client/ShockwaveRenderer`, y sirve para
  cualquier ataque de área: `Shockwave.telegraph(...)` pone el aviso, `fire(...)` suelta el anillo.
