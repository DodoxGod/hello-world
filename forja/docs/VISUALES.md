# Mejoras visuales — trabajo en marcha (2026-09-19)

Encargo de Andy: *"continúa añadiendo todo tipo de mejoras visuales al mod, efectos bonitos... los
eventos podrían cambiar cómo se ve el cielo en la noche... que algunas mejoras/sinergias tengan efectos
visuales, que los enemigos tengan mejores efectos, que la forja tenga mejores visuales"*.

Este documento se va llenando conforme cada cosa queda **verificada dentro del juego**, no cuando
compila. Copia de seguridad antes de empezar: `Forja_copia_2026-09-19_visuales`.

---

## 1. El cielo de los eventos — HECHO

### El problema que había

Los nueve eventos son lo más raro del mod y la única forma de conseguir nueve de las mejoras. Y el
cliente **no se enteraba de ninguno**: el evento vivía en dos campos estáticos del servidor
(`current`, `endsAt`) y lo único que cruzaba a la pantalla era una línea de chat y un puñado de
partículas lanzadas al aire sobre cada jugador.

El resultado es que una **luna de sangre** y una **ventisca** se veían exactamente igual: una noche
normal con polvo de color distinto flotando. Toda la atmósfera que el nombre promete no estaba en
ninguna parte.

### Lo que se hizo

**Sincronización** — [`EventSky`](../src/main/java/dev/forja/world/EventSky.java). Un paquete diminuto
(el ordinal del evento y los ticks que le quedan) que sale cuando un evento empieza, cuando termina, y
hacia cualquiera que entre a la partida con uno en marcha. Antes nadie avisaba de que un evento se
había acabado solo: `active()` devolvía null al pasar el reloj y ya está. Ahora hay un sitio que lo
nota y apaga el cielo.

**Estado en el cliente** — [`SkyMood`](../src/main/java/dev/forja/client/SkyMood.java). Todo lo demás
se calcula aquí, porque cambia cada fotograma y no tiene sentido mandar sesenta paquetes por segundo
para decir "un poco más". Dos decisiones que importan:

- **Entra suave**, en cuatro segundos. Un evento que llega no debe dar un tirón de color al mundo.
- **Manda de noche y desaparece a mediodía.** Un cielo diurno teñido se lee como un shader roto; un
  cielo nocturno teñido se lee como que está pasando algo. La rampa es un coseno sobre el reloj del
  día, así que llega con la oscuridad y no en un tick concreto.

**El tinte** — dos mixins de cliente:

- [`SkyRendererMixin`](../src/main/java/dev/forja/mixin/client/SkyRendererMixin.java) sobre
  `extractRenderState`, que es donde el juego decide de qué color iba a ser el cielo. Se espera a que
  lo decida y se le dobla, en vez de dibujar un segundo cielo encima del primero.
- [`FogRendererMixin`](../src/main/java/dev/forja/mixin/client/FogRendererMixin.java) sobre
  `setupFog`. Sin esto el horizonte se queda azul mientras todo lo de arriba se ha puesto rojo, y la
  junta entre los dos es una raya dura cruzando el mundo. La niebla se lleva **menos** color que el
  cielo a propósito: está justo delante del ojo y la misma cantidad ahí cansa en los cien segundos que
  dura un evento.

**Las estrellas.** No todos los eventos les hacen lo mismo, y eso dice más que un color:

| Evento | Estrellas | Por qué |
|---|---|---|
| Meteoritos | ×1.9 | Una lluvia de meteoros sin más estrellas de lo normal es una noche morada |
| Aurora | ×1.5 | La aurora es luz que se añade |
| Tormenta arcana | ×1.3 | |
| Luna de sangre | ×0.8 | |
| Ventisca | ×0.55 | El cielo se cierra |
| Niebla de almas | ×0.45 | |
| Eclipse | ×0.15 | La luz se va, que es lo que un eclipse hace |

### Cómo se comprobó

Una prueba nueva, `shotSkies`, fotografía el cielo a medianoche con cada uno de los nueve eventos desde
la misma cámara, más un control sin evento y una foto después de terminar. Y no se fía de la foto: en
cada paso **le pregunta al cliente qué cree que está pasando** y lo verifica.

| Evento | Cielo medido |
|---|---|
| sin evento (control) | rgb(4, 5, 8) |
| Meteoritos | rgb(110, 135, 149) |
| Tormenta arcana | rgb(146, 131, 56) |
| Niebla de almas | rgb(63, 115, 117) |
| Aurora | rgb(92, 130, 112) |
| Luna de sangre | rgb(103, 22, 22) |
| Eclipse | rgb(44, 35, 55) |
| Ventisca | rgb(124, 138, 148) |
| Marea viva | rgb(37, 110, 122) |
| Lluvia de pavesas | rgb(146, 97, 39) |
| al terminar | rgb(4, 5, 8) |

Esa última fila es la que importa tanto como las otras: **vuelve a la normalidad solo.**

### Dos fallos que encontró la comprobación

**La prueba mentía, no el código.** Los primeros nueve cielos salieron todos verdes e idénticos, y
parecía que el tinte no distinguía eventos. No era eso: la prueba arrancaba los eventos con
`server.runCommand("forja evento X")`, pero ese comando le pide un **jugador** a su fuente y
`runCommand` habla como la consola — así que los nueve fallaban en silencio y el cielo seguía mostrando
una Aurora que había dejado encendida una prueba anterior. Dos capturas salieron idénticas píxel a
píxel, y eso fue lo que lo delató. La prueba ahora arranca los eventos en el servidor directamente.

**El cielo podía quedarse encendido para siempre.** `WorldEvents.stop()` sin nivel no avisa a nadie, y
las pruebas lo usaban. Ahora el cliente **descuenta su propio reloj**: el paquete lleva los ticks que
quedan y, si el "se acabó" nunca llega — una desconexión, un mundo que se descarga, una prueba que para
un evento a mano — se apaga solo de todas formas. La duración del evento es respaldo suficiente para un
color.

### Detalle técnico que costó encontrar

`Level.getDayTime()` **ya no existe** en 26.2: se llama `getOverworldClockTime()`. Y el color del
cielo y el brillo de las estrellas viven ahora en `SkyRenderState`, que se rellena una vez por
fotograma antes de dibujar nada — que resulta ser el sitio perfecto para meter mano.

---

## 2. Las sinergias, en su propio color — HECHO

Cada una de las 23 sinergias lleva un color escrito desde el día que se creó, y lo único que se hacía
con él era la línea del tooltip. Eso desperdicia lo único que ya las distingue: quien ha llevado **dos**
mejoras al 50 % ha conseguido algo raro y merece que se lo digan en el mundo, no en un menú.

[`Synergy`](../src/main/java/dev/forja/upgrade/Synergy.java) tiene ahora dos formas de dibujarse:

- `spark(...)` — un puñado de motas del color de la sinergia. Deliberadamente pequeño: saltan en golpes
  normales, varias veces por segundo en una pelea, y lo que funciona ahí son unas pocas motas
  reconocibles, no algo que llene la pantalla.
- `ring(...)` — un anillo en el suelo, para las que son de **área**. Cadena de rayos, vendaval, martillo
  pilón: de esas lo que importa es hasta dónde llegaron, y un grupo de motas sobre la víctima no puede
  decir eso. El anillo sí.

Conectadas: cazarrecompensas, muro, sed de sangre, tormenta helada, justa (a lo largo de la línea que
atraviesa), cadena de rayos, vendaval y martillo pilón.

## 3. La forja, encendida — HECHO

El bloque que **da nombre al mod** no tenía ni una partícula: una mesa de trabajo que resultaba estar
caliente, al lado de un crisol que humea, una cuba que corre y un caño que vierte.

- **Mesa de forja y forja mayor** — humo saliendo por arriba, la brasa vista a través de la rejilla, y
  de vez en cuando una **chispa** (la partícula propia del mod). La forja mayor lleva más de todo,
  porque quien se ha construido una ha ganado un bloque que parezca la mejora que fue, y además suena
  bajito cada pocos segundos.
- **Mesa de piezas y talabartería** — nada. Son trabajo en frío y no tienen fuego.
- **Yunque del herrero caído** — sin llama y sin humo: sólo **ceniza** levantándose de la cara, y muy de
  vez en cuando una chispa de un golpe que no ha dado nadie. Es decorado, y el decorado que insiste deja
  de ser decorado.

**Forjar** ahora suelta chispas **del color del material** que acabas de trabajar. Antes tiraba llama de
vanilla y punto: el mismo soplido tanto si habías hecho un mango de madera como una cabeza de hierro
estelar. Una obra maestra sigue llevando fuego encima, que era lo único que la separaba de una pieza
normal.

## 4. El tiempo de cada evento — HECHO

El tinte del cielo dice de qué noche se trata; esto dice qué está cayendo.

Antes, el servidor echaba treinta partículas seis bloques por encima de la cabeza de cada jugador una
vez por minuto. Eso es un gesto, no tiempo atmosférico. Ahora lo hace **el cliente**, cada tick, y por
eso puede ser todo lo denso que quiera: sin paquetes, sin tick de servidor, y se apaga solo si el
jugador baja las partículas porque usa el mismo ajuste que todo lo demás.

Y cada evento trae **lo suyo**, no una versión recoloreada de lo mismo:

| Evento | Qué cae |
|---|---|
| Lluvia de pavesas | brasas cayendo del cielo, con peso |
| Ventisca | nieve **de lado**, que es lo que distingue una ventisca de una nevada |
| Niebla de almas | almas a la altura de la rodilla, sin rumbo |
| Aurora | luz alta y quieta, que apenas se mueve |
| Tormenta arcana | chispazos en todas direcciones |
| Luna de sangre | ceniza cayendo despacio |
| Eclipse | el aire manchado |
| Marea viva | burbujas subiendo |
| Meteoritos | pocas, muy rápidas y muy largas |

Sólo **a cielo abierto**. Un evento que se ve a través de un techo de piedra es un fallo, no una
atmósfera.

## 5. Muertes y fases — HECHO

Las tres muertes soltaban su botín, dos hacían ruido, y ya. El autómata tenía un soplido de lava y la
coraza no tenía nada. Una muerte es lo último que hace un mob y el único momento en que el jugador
está mirándolo seguro.

Cada una dice algo distinto, que es la razón de hacerlas por separado:

- **Coraza vacía** — el alma **saliendo**, en columna recta hacia arriba. Es lo único bueno que le pasa
  a una armadura de estas, y hasta ahora la única señal era el botín.
- **Autómata** — una **caldera reventando**: chispas, un anillo de vapor a ras de suelo y la ceniza del
  fuego de su barriga apagándose, que es la parte que dice que no se va a levantar.
- **Pavesa** — una luz que se apaga **tiene que brillar más antes**, o simplemente deja de estar.

Y el **herrero caído**, al entrar en su último cuarto de vida, ya no sólo ruge: un anillo violeta que
sale de él por el suelo, una columna de ceniza, y la fragua del pecho encendida para no apagarse. Era
el único momento del combate que merecía pararte y sólo se notaba mirando la barra de vida.

## 6. Lo que el jugador hace bien — HECHO

Cinco cosas que el mod te pide y que, cuando las conseguías, no se veían.

**Frenesí al máximo.** Es literalmente lo único del mod que empuja una mejora **por encima de lo que
dice su tooltip**, y la única señal era un número en una barra. Ahora el golpe que llega a cinco suelta
un anillo naranja a los pies y un puñado de chispas, una vez y no más hasta que la racha se pierda y la
vuelvas a montar.

**Conjunto completo.** Llevar las cuatro piezas del mismo material es un compromiso real — te quita la
libertad de mezclar placas buscando estadísticas — y pagaba dos puntos de armadura y una línea de
tooltip. Ahora deja **un punto de su propio color** a los pies, una vez por segundo. Poco a propósito:
esto está encendido durante horas, y el trabajo es notarse cuando alguien mira, no cuando no mira.

**Subir de maestría.** Usaba las partículas verdes del aldeano comerciando. Ahora son **del color de la
pieza que subió**, con chispas; y al llegar a diez, que pasa una sola vez en la vida de un objeto, un
anillo completo y un yunque sonando.

**Veta y excavación.** Son las dos mejoras que más hacen y menos enseñan: das un golpe y once bloques
desaparecen detrás del que mirabas, sin forma de saber si fue la mejora o rompiste lo que no era. Ahora
se dibuja **el hilo** desde el bloque que golpeaste hasta cada uno que se fue con él.

**La parada perfecta.** Es lo más ajustado que te pide el mod — unos pocos ticks al principio de un
bloqueo — y acertarla se veía igual que fallarla. Ahora salta un disco de chispas **en la dirección de
donde vino el golpe**, más un anillo pálido en el plano del escudo, que se lee como el golpe resbalando
por él.

## 7. Romper y colar — HECHO

**Romper una pieza.** Una pieza de Forja **no desaparece** al romperse, que es justamente su gracia, así
que la rotura de vanilla no tiene nada que decir aquí. Ahora se ve el material rindiéndose: una lluvia
de su propio color y el metal enfriándose, con un yunque roto de fondo.

Esto importa más que una rotura normal. Perder una herramienta de vanilla te cuesta la herramienta;
perder una de estas te cuesta los materiales, las mejoras y los niveles de Maestría que llevara — y el
único aviso era una línea de texto en la esquina.

**La mesa de colada** brilla ahora **del color del metal que está enfriando**, con alguna chispa. Una
llena de oro y una llena de hierro se veían idénticas mientras enfriaban, que es precisamente el
momento en que querrías distinguirlas.

## 8. Volar, y la luz de la forja — HECHO

**Estela de las alas.** Volar es lo más aparatoso que puede hacer un jugador en este mod y no dejaba
absolutamente nada detrás: el mismo cielo vacío tanto si planeabas con unas alas de cuero como con unas
de hierro estelar. Ahora sueltan una estela **del material de las alas**, que es el único sitio donde el
mod te enseña de lejos lo que alguien lleva puesto.

Y **se adelgaza según se agota la reserva**. Esa es la parte útil: la barra está en tu pantalla y no le
sirve a nadie más, pero una estela que ha pasado de un chorro a alguna mota suelta dice "ése se va a
caer" a ti y a todo el que mire.

**La propulsión** cuesta una pólvora cada vez y no enseñaba nada a cambio. Ahora suelta un cono de
chispas por detrás, visible desde dentro y desde fuera.

**La forja da luz.** Nivel 8 la normal y 11 la mayor. No mucho — una fragua es un fuego de hogar, no una
antorcha — pero lo justo para que un taller alumbrado sólo por sus propias forjas se lea como un sitio
donde alguien trabaja, y lo justo para que no aparezcan mobs alrededor, que importa más.

**La fragua muerta** echa ahora **ceniza** además de humo. El humo dice "todavía arde"; la ceniza dice
"ardió, una vez, hace mucho", que es la historia de todas las ruinas donde aparece ese bloque.

## 9. El gancho tenía nombre de cuerda y no tenía cuerda — HECHO

El mod la llama **cuerda** en cuatro comentarios y **gancho** en su propio nombre, y no había cuerda
ninguna: un garfio salía volando de ti, se paraba, y después volabas tú hacia él sin nada en medio. Eso
se lee como un fallo antes que como un gancho — y además era la única forma de saber, en el momento, si
el garfio había enganchado algo o no.

Ahora se dibuja entre la mano y el garfio, con **un poco de comba en medio**, que es lo que hace una
cuerda y no hace un láser. Cada dos ticks y con menos eslabones cuanto más lejos, porque a treinta
bloques una línea continua de partículas es una pared, no una cuerda.

**El crisol** arde también del color de su metal, igual que la mesa de colada. Cuatro crisoles
trabajando en un taller ardían con exactamente la misma llama.

## 10. La estrella — HECHO

Aplicar una mejora en la estrella soltaba el destello morado genérico del encantamiento: poner
**Vampirismo** y poner **Escarcha** se veían exactamente igual. Cada `Upgrade` lleva un color propio
desde que se escribieron y, como el de las sinergias, no salía del tooltip.

Ahora la estrella se enciende de ese color, y **el anillo crece con el porcentaje**: el primer orbe
dibuja uno pequeño y el que la lleva a cien dibuja un círculo completo alrededor de la mesa. Se ve
desde el otro lado del taller lo lejos que va una pieza, que es algo que un tooltip no puede hacer.

## 11. La aurora, en condiciones — HECHO

El tinte del cielo y unas motas altas decían "hay algo ahí arriba"; no decían aurora. Lo que hace una
aurora es que la luz está **organizada**: cortinas largas que cuelgan, ondulan despacio y mantienen su
forma mientras lo hacen.

Así que ahora se dibujan tres bandas con su propio vaivén, y cada punto de una banda es **una columna
corta colgando** en vez de un punto suelto. Las columnas son lo que el ojo lee como aurora; partículas
sueltas, por bien colocadas que estén, se leen como nieve a gran altura — que es exactamente lo que
pasó en el primer intento.

Cada cinta se construye a pocos puntos por tick. Las partículas viven un par de segundos, así que un
puñado por tick levanta la cortina entera y la mantiene sin pedirle al juego que dibuje cuatrocientas
de nada.

## 12. Los meteoritos se ven caer — HECHO

El cráter aparecía de la nada: un estruendo, un agujero en el suelo y hierro estelar dentro, sin que el
cielo llegara a mencionarse. Algo que cae del cielo tiene que **verse caer** — era lo más raro que hace
el mod y duraba un tick.

Ahora entra en escena setenta bloques por encima del punto de impacto y baja durante **34 ticks**, con
llama y chispas en la cabeza y una cola de humo detrás — la cola es lo que hace que se lea como algo
cayendo y no como algo colgando. Acelera hacia abajo, que es lo que hace una piedra. Y donde llega, el
cráter de siempre más la polvareda que levanta.

Va en el tick de nivel normal y no en el reloj por minuto del evento, porque una cosa que cae sólo vale
algo si se dibuja **mientras** cae.

## 13. Las ruinas, por dentro — HECHO

Las cinco estructuras están hechas de bloques normales y unos pocos nuestros, y una vez dentro se
parecen todas: la forja abandonada, la fragua caída y el túmulo del herrero son tres historias
distintas contadas con el mismo silencio. [`RuinMood`](../src/main/java/dev/forja/world/RuinMood.java)
le da a cada una su ambiente — lo que flota en el aire y lo que se oye y no está.

| Ruina | Qué pasa dentro |
|---|---|
| Forja abandonada | ceniza que no acaba de posarse, y un martillo muy lejos que calla cuando lo escuchas |
| Fragua caída | ardió, y algo sigue caliente: ceniza, chispas a ras de suelo y fuego lejano |
| Túmulo del herrero | aquí abajo no arde nada; lo que queda son almas |
| Taller de montaña | alto, frío y todavía en uso: ceniza blanca y un yunque de vez en cuando |
| Campamento saqueador | humo de hogueras y una forja que no es de nadie |

Va **muy despacio a propósito**: una vez cada dos segundos por jugador, un puñado de partículas. Una
ruina que está haciendo algo constantemente es un decorado, no una ruina. Lo que se busca es que, de
vez en cuando mientras la registras, el edificio te recuerde lo que pasó ahí.

Y hace **una sola consulta** de estructura en vez de cinco: `getStructureWithPieceAt` lee los datos de
estructura del chunk cada vez que se llama, así que preguntarle cinco veces seguidas cuesta cinco
veces; el predicado se queda con la respuesta.

## 14. Lo que viene

- Efectos para las técnicas y los perks cuando saltan.
- Marca visual para las armas legendarias.
- Ambiente propio dentro de las estructuras en ruinas (más allá de la fragua muerta).
- Los mobs nuevos, cuando estén construidos, con sus propios efectos desde el principio.
