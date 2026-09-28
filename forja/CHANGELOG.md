# Novedades

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
