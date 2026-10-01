# Equilibrio de Forja, medido

> Esta página la escribe `./gradlew runGametest` (prueba `BalanceGameTests.equilibrio`, código en `src/gametest/java/dev/forja/test/balance`). No se edita a mano: se regenera sola cada vez que se pasan las pruebas, así que siempre dice lo que hace el código de hoy. Dificultad medida: **HERRERO**. Esta vez: 33 mobs, 12 tipos de arma, 6324310 peleas simuladas.

## Cómo se mide

- **Nada se vuelve a escribir a mano.** Cada arma se forja de verdad (`Assembler`, con sus mejoras escritas como las escribe la forja) y cada número sale del objeto: daño, velocidad, encantamientos ocultos.
- **Cada mob se mide golpeándolo.** Se invoca, se le quita el equipo y la tirada de veterano/élite, se congela y se le dan golpes pequeños con cada tipo de arma (y con Brecha 0..IV y Resonante), con rayos, magia, fuego y marchitez. Lo que pierde de vida por punto de golpe ya incluye su armadura, la penetración del arma, su resistencia al tipo de golpe, la guardia de jefes y élites y el aturdido. El tope por golpe, la barra de postura y lo demás se leen del mob.
- **La pelea se simula tick a tick** con el orden del mod: fuerza del golpe de `Player#attack` (0,2 + 0,8·s²), los 10 ticks de invulnerabilidad de vanilla, estamina (12 por golpe, vuelve a 20/s tras 20 ticks sin gastar, golpe cansado ×0,6), combos (3.º golpe ×1,3), golpe cargado (×2,0, 35 de estamina), postura y aturdido (×1,25, sin tope), remates (×2), tope por golpe, frenesí y el orden exacto de `CombatUpgrades.onWeaponHit` con su ablandado (`CombatUpgrades.softened`). Fuego, veneno, sangrado y marchitez a los ritmos de vanilla.
- **Las pruebas atan el modelo al código real**: los extras de las mejoras dan lo mismo que el manejador real (al 0,2 %, y en media con tiradas al azar), el primer golpe da lo mismo que `Player#attack` en los diez tipos cuerpo a cuerpo (±1 %), el aturdido cae en el mismo golpe que en `Posture`, y un segundo golpe a los 5 ticks se lo traga la invulnerabilidad como en el juego.
- **Búsqueda**: por cada hueco de cada arma se quitan los materiales dominados (Pareto: cabeza por daño y durabilidad, mango por velocidad y durabilidad, atadura/guarda por durabilidad; sólo se comparan materiales con el mismo rasgo). Lo que queda se combina y se agrupa por lo que cambia en una pelea. Las mejoras se eligen con un **knapsack exacto por programación dinámica** sobre la carga (20 + 4·puntos de potencial; pesos de `Potential.weight`, un solo miembro de cada grupo exclusivo, sinergias como pareja) y se **comprueba peleando todos los conjuntos legales**.
- **Vara de medir**: la media geométrica del tiempo para matar (TTK) a 8 mobs de muestra (minecraft:zombie, minecraft:skeleton, minecraft:spider, minecraft:enderman, minecraft:piglin_brute, forja:yunque_andante, forja:percutor, forja:automata_de_forja); el jugador elige, contra cada mob, el ritmo que antes lo mata (cada cuántos ticks golpea, si carga, si espera a tener estamina o descansa hasta llenarla).
- **Escenarios**: 0 % = sin mejoras; 50 % = pieza de potencial 50 (mejoras al 50 %, carga 7); 100 % = potencial 100 con fundente (mejoras al 100 %, carga 20). *Con pactos*: además los dos pactos de arma al 100 %, que suben el potencial 10 cada uno.
- **Supuestos**: el mob está quieto y no se defiende (sin escudo, sin esquiva, sin IA); el jugador sin armadura, no salta (sin críticos de salto), no ataca por la espalda, apunta al centro del mob; el arma nueva (Afilado entero); sin Maestría ni don; de día sin sol directo (los rasgos solar, nocturno y ascua apagados). Contra mobs bajos (arañas, herrumbre, escorias, pavesas) el golpe entra por arriba y cuenta como a la cabeza (×1,3): así pasa también en el juego. El núcleo estelar queda fuera: se come los golpes hasta llenarse y se pelea por fases, así que un golpe suelto no dice nada de él. Las mejoras de área (Onda de choque, Segunda cabeza, Filo arrasador, Conductor, Carnicero, Cadena de rayos...) valen 0 aquí: todo es contra un solo mob. Arcos y ballestas no entran.

## Resumen

- **Orden al 100 %** (TTK medio en la muestra, menos es mejor): guanteletes 0,26 s < mangual 0,57 s < daga 0,58 s < mazo 0,65 s < espada 0,68 s < hacha 0,70 s < baculo 0,71 s < espadon 0,72 s < guadana 0,77 s < tridente 0,81 s < lanza 1,10 s < grimorio 1,24 s.
- La más rápida mata en 0,26 s de media y la más lenta en 1,24 s: **4,7 veces** más (guanteletes contra grimorio).
- Ningún tipo de arma está dominado en todo: cada uno gana a los demás contra algún mob en algún escenario.
- **Estamina**: en una pelea larga casi todos los golpes son cansados (mediana 93 % de los golpes de las mejores armas cuerpo a cuerpo al 100 %): se golpea sin parar a ×0,6 en vez de esperar.
- **Los extras de las mejoras no tienen el tope de un golpe**: cada uno es un golpe aparte con su propio tope, y son el 41 % del daño de las mejores armas al 100 % (mediana). Un zombi muere en 2,0 golpes, no en 3.
- **Pocos materiales deciden casi todo**: de 60 mejores armas (12 tipos × 0/50/100 % con y sin pactos), los que más aparecen son damasco (50), vidriacero (50), corazon (32), eco (26). Ver *Hallazgos* (Afilado en cualquier pieza, el mango de vidriacero).
- **Pactos**: al 50 % los dos pactos bajan el TTK medio una mediana de −38 % (daño, techo y carga a la vez, peso 0).
- Consulta *Las siete sospechas* para los veredictos, *Hallazgos que no estaban en la lista* y *Valores atípicos* para el porqué.

## Magia frente al cuerpo a cuerpo

Andy, 2026-09-30: la magia estaba rota. Sin clase mágica debe ser un recurso para un momento, no la mejor arma; un Mago con sus talentos, a la altura del cuerpo a cuerpo pero no por encima. *Sin clase*: el maná vuelve a 0,4/s lanzando y 0,8/s en calma. *Mago*: la clase y todos los nodos de su árbol grande menos las claves (docs/ARBOLES.md: lo que tiene un Mago en el nivel 50). El báculo y el grimorio son los mejores de cada escenario sin clase. Las pruebas (`magiaEnSuSitio`) exigen que, sin clase, la magia no mate antes que la mediana cuerpo a cuerpo ni sostenga más de la mitad de su daño, y que el Mago quede entre la más rápida cuerpo a cuerpo y 1,3 veces la mediana (1,5 el grimorio, cuyo área muerde a todo lo que pisa la runa y aquí pelea contra un solo mob). Contra los grandes, nada mágico puede matar claramente (un 10 %) antes que la más rápida cuerpo a cuerpo.

| Escenario | C/c más rápida | Mediana c/c | Báculo sin clase | Báculo Mago | Grimorio sin clase | Grimorio Mago |
|---|---|---|---|---|---|---|
| 0 % | guanteletes 1,09 | 1,56 | 2,35 (×1,51) | 1,28 (×0,82) | 2,40 (×1,54) | 1,74 (×1,11) |
| 50 % | guanteletes 0,73 | 1,20 | 1,25 (×1,04) | 0,92 (×0,77) | 1,80 (×1,50) | 1,54 (×1,28) |
| 100 % | guanteletes 0,26 | 0,70 | 0,71 (×1,02) | 0,60 (×0,86) | 1,24 (×1,76) | 0,92 (×1,30) |

TTK medio en segundos (entre paréntesis, frente a la mediana cuerpo a cuerpo). Daño por segundo sostenido en 60 s:

| Escenario | Mediana c/c | Báculo sin clase | Báculo Mago | Grimorio sin clase | Grimorio Mago |
|---|---|---|---|---|---|
| 0 % | 15,2 | 1,9 | 7,7 | 2,3 | 8,9 |
| 50 % | 21,7 | 2,7 | 10,9 | 2,9 | 10,7 |
| 100 % | 32,5 | 4,0 | 16,5 | 3,3 | 13,5 |

Los grandes, al 100 % (segundos para matar; «> 120» si no cae en dos minutos):

| Arma | Warden | Herrero Caído |
|---|---|---|
| báculo con Enjambre, sin clase | > 120 | > 120 |
| báculo con Enjambre, Mago | 5,9 | 40,3 |
| grimorio, sin clase | > 120 | > 120 |
| grimorio, Mago | 17,7 | 53,6 |
| guanteletes (la más rápida cuerpo a cuerpo) | 5,9 | 25,0 |

## Herrero Caído

Andy, 2026-09-30: «parece que puedes llegar a estar muy fuerte, o el Herrero Caído es muy débil, hazlo más fuerte». Objetivo: un jugador bien equipado de final de juego, solo, tarda de 3 a 5 minutos de pelea de verdad en Difícil (más en Extremo, menos en Normal y Fácil), y el jefe puede matar a un jugador equipado que se descuida. En Pacífico no hay pelea: un mundo pacífico no guarda ningún monstruo, tampoco a él. Lo mide `SmithFight` y lo exige `BalanceGameTests.herreroEnSuSitio`.

**Qué se mide en el jefe de verdad** (vestido con su mangual y su placa, del tamaño que le da la pelea por nivel, jugadores y equipo, en cada una de sus tres fases y aturdido), por el mismo camino de daño del juego: lo que le quita un golpe de cada arma, lo que les quitan a sus aprendices y a los yunques andantes, y lo que quita cada golpe suyo (el normal, el revés, la onda, el garfio y las estrellas) a un jugador con la armadura de referencia (placa de obsidiacero sobre cuero, Protección al 100 % en las cuatro piezas y Vitalidad en la pechera), recién llegado y con la presión de una pelea larga.

**Qué es modelo** (los números están en `SmithFight`): el daño por segundo de un jugador contra cada fase es la pelea tick a tick de este informe durante 120 s; el tiempo que pasa pegándole es lo que queda tras esquivar cada movimiento suyo cada vez que vuelve (onda 1,5 s, revés 1,0 s, estrellas 1,0 s, garfio desde lejos 1,5 s; un 15 % de moverse y seguirle cuerpo a cuerpo y un 10 % desde lejos; los eventos del cielo de su lado); las pausas son las de la pelea (levantarse, las dos llamadas de aprendices, el aturdido); el Reforjado cuesta 6 s por brasa más la última colada; los aprendices de las dos oleadas (y los guardianes de las brasas) y los seis yunques se matan uno tras otro con la misma arma, pegándoles el 80 % del tiempo. Con dos jugadores se reparten el daño, los aprendices y las brasas; el que no persigue solo esquiva la onda y el cielo. No cuenta la regeneración, las pociones ni las constelaciones que le hacen daño.

Los equipos (todos con herrero de nivel 10, armadura de referencia con Maestría 10, y la puntuación de equipo de `GearScore` que ve el jefe): **cc**: la mejor arma cuerpo a cuerpo al 100 % con Maestría 10, sin clase (guanteletes, equipo 0,77, vida 26); **guerrero**: la misma, Guerrero de nivel 50 (guanteletes, equipo 0,91, vida 36); **estrella** (la referencia): la misma estrellada, Guerrero de nivel 50, armadura estrellada (guanteletes, equipo 0,95, vida 36); **mago**: báculo con Enjambre estrellado, Mago de nivel 50, armadura estrellada (baculo, equipo 0,92, vida 32).

### Sus números ahora

Vida de base 400 (antes 320), por el nivel, +100 % por cada jugador de más que haya estado en la pelea y +25 % y +1,5 de armadura por tramo de equipo (0 a 3) de quienes le pelean, como cualquier monstruo (`Scaling`, que da +20 %). Pasado el techo de vida del juego (1024), lo que sobra se lo quita a cada golpe (`bulk`). Por fase (1 / 2 / 3): armadura +0 / 3 / 6, dureza +0 / 2 / 4, daño de los golpes avisados ×1 / 1,20 / 1,40 y esperas ×1 / 0,85 / 0,70 (los avisos no cambian). Furia bajo un tercio: +15 % de velocidad y +20 % a su golpe normal. Segunda oleada de aprendices: +40 % de vida y +3 de armadura. Golpes avisados de base: revés 9 (antes 7), onda 10 (antes 8), garfio 5 (antes 4), estrellas 10 (antes 9). Encima, el daño de cada nivel a todos los monstruos (`Ladder`) y el de su tramo de equipo (`GearScore.damageFactor`).

| Nivel | Vida de uno solo sin equipo | Daño avisado | Esperas | Brasas del Reforjado (uno solo) | Guardianes al reforjarse |
|---|---|---|---|---|---|
| facil | 360 | ×0,85 | ×1,15 | 3 | 0 |
| normal | 400 | ×1,00 | ×1,00 | 3 | 0 |
| dificil | 520 | ×1,15 | ×0,90 | 4 | 2 |
| extremo | 640 | ×1,30 | ×0,80 | 4 | 3 |

### La referencia, sola: antes y ahora

| Nivel | Objetivo | Antes | Ahora | Vida del jefe antes → ahora | Descuidado aguanta (fase 1 / 3), antes → ahora | Atento aguanta (fase 1 / 3) | Golpe más grande: recién llegado / con presión |
|---|---|---|---|---|---|---|---|
| facil | 1:30 – 3:00 | 1:31 | **1:52** | 256 → 360 | 23 / 13 s → 23 / 10 s | 48 s / 42 s | 11 % (estrellas, fase 3) / 11 % (estrellas, fase 3) |
| normal | 2:00 – 4:00 | 2:29 | **3:17** | 512 → 700 | 10 / 7 s → 9 / 5 s | 24 s / 19 s | 14 % (golpe, fase 3) / 15 % (golpe, fase 3) |
| dificil | 3:00 – 5:00 | 2:29 | **4:25** | 512 → 910 | 10 / 7 s → 9 / 4 s | 24 s / 18 s | 15 % (estrellas, fase 3) / 15 % (golpe, fase 3) |
| extremo | 4:00 – 7:00 | 3:07 | **5:41** | 666 → 1120 | 8 / 5 s → 7 / 3 s | 20 s / 15 s | 17 % (estrellas, fase 3) / 18 % (golpe, fase 3) |

### Tiempo de pelea por equipo (minutos: solo / dos jugadores; entre paréntesis, antes)

| Equipo | facil | normal | dificil | extremo |
|---|---|---|---|---|
| cc | 2:09 / 1:40 (1:43 / 0:59) | 3:57 / 3:04 (2:56 / 1:29) | 5:15 / 4:04 (2:56 / 1:29) | 6:50 / 5:11 (3:45 / 1:48) |
| guerrero | 1:57 / 1:31 (1:35 / 0:55) | 3:29 / 2:43 (2:38 / 1:21) | 4:41 / 3:37 (2:38 / 1:21) | 6:02 / 4:35 (3:19 / 1:36) |
| estrella | 1:52 / 1:27 (1:31 / 0:54) | 3:17 / 2:34 (2:29 / 1:18) | 4:25 / 3:25 (2:29 / 1:18) | 5:41 / 4:20 (3:07 / 1:31) |
| mago | 2:17 / 1:46 (1:46 / 1:02) | 4:05 / 3:09 (3:00 / 1:33) | 5:18 / 4:04 (3:00 / 1:33) | 6:35 / 5:03 (3:45 / 1:50) |

### De qué está hecha la pelea de la referencia, sola

*Sin parar*: lo que tardaría pegándole sin esquivar nada, sin pausas ni aprendices (la cuenta de antes, contra un maniquí). *Tope*: golpes del jugador que el tope por golpe recorta en la fase 1.

| Nivel | Vida | Daño/s por fase (1 / 2 / 3 / aturdido) | Tiempo pegándole por fase | Sin parar | Pegándole | Pausas | Reforjado | Aprendices y yunques | Total | Tope |
|---|---|---|---|---|---|---|---|---|---|---|
| facil | 360 | 13,6 / 11,2 / 11,4 / 17,8 | 50 % / 46 % / 30 % | 30 s | 64 s | 12 s | 21 s | 15 s | **112 s** | 0 % |
| normal | 700 | 11,9 / 11,2 / 11,4 / 17,8 | 47 % / 42 % / 30 % | 61 s | 141 s | 12 s | 21 s | 23 s | **197 s** | 0 % |
| dificil | 910 | 11,6 / 11,2 / 11,4 / 17,8 | 44 % / 38 % / 30 % | 80 s | 199 s | 12 s | 27 s | 28 s | **265 s** | 0 % |
| extremo | 1120 | 10,5 / 10,2 / 10,3 / 16,1 | 40 % / 34 % / 30 % | 99 s | 267 s | 12 s | 27 s | 35 s | **341 s** | 0 % |

### Lo que aguanta un jugador delante de él, solo

Segundos hasta morir con su vida entera. *Descuidado*: se queda delante y se lo come todo (su golpe cada 1,4 s, el revés, la onda, el garfio y las estrellas cada vez que vuelven, y 2 aprendices pegándole desde la fase 2), con la presión de una pelea larga. *Atento*: esquiva lo avisado y se come la mitad de sus golpes normales y el garfio (el mago, solo los golpes); ∞ si nada le alcanza. Golpe más grande: lo más que quita un solo golpe suyo, en cualquier fase, a ese jugador de su vida, recién llegado y con la armadura gastada por la presión de una pelea larga (la prueba exige menos del 50 % y del 80 %).

| Equipo | Nivel | Vida del jugador | Descuidado (fase 1 / 2 / 3) | Atento (fase 1 / 2 / 3) | Golpe más grande: recién llegado / con presión |
|---|---|---|---|---|---|
| cc | facil | 26 | 13 / 10 / 6 s | 27 s / 28 s / 24 s | 16 % (estrellas, fase 3) / 16 % (estrellas, fase 3) |
| cc | normal | 26 | 6 / 4 / 3 s | 14 s / 13 s / 11 s | 23 % (golpe, fase 3) / 26 % (golpe, fase 3) |
| cc | dificil | 26 | 5 / 4 / 3 s | 15 s / 13 s / 11 s | 23 % (golpe, fase 3) / 26 % (golpe, fase 3) |
| cc | extremo | 26 | 4 / 3 / 2 s | 12 s / 10 s / 8 s | 29 % (golpe, fase 3) / 32 % (golpe, fase 3) |
| guerrero | facil | 36 | 21 / 16 / 9 s | 45 s / 46 s / 39 s | 11 % (estrellas, fase 3) / 11 % (estrellas, fase 3) |
| guerrero | normal | 36 | 9 / 6 / 4 s | 23 s / 21 s / 17 s | 15 % (golpe, fase 3) / 16 % (golpe, fase 3) |
| guerrero | dificil | 36 | 8 / 6 / 4 s | 22 s / 20 s / 16 s | 16 % (golpe, fase 3) / 17 % (golpe, fase 3) |
| guerrero | extremo | 36 | 7 / 5 / 3 s | 18 s / 16 s / 13 s | 18 % (golpe, fase 3) / 20 % (golpe, fase 3) |
| estrella | facil | 36 | 23 / 17 / 10 s | 48 s / 49 s / 42 s | 11 % (estrellas, fase 3) / 11 % (estrellas, fase 3) |
| estrella | normal | 36 | 9 / 7 / 5 s | 24 s / 23 s / 19 s | 14 % (golpe, fase 3) / 15 % (golpe, fase 3) |
| estrella | dificil | 36 | 9 / 7 / 4 s | 24 s / 22 s / 18 s | 15 % (estrellas, fase 3) / 15 % (golpe, fase 3) |
| estrella | extremo | 36 | 7 / 5 / 3 s | 20 s / 18 s / 15 s | 17 % (estrellas, fase 3) / 18 % (golpe, fase 3) |
| mago | facil | 32 | 20 / 15 / 10 s | 42 s / 46 s / 39 s | 7 % (golpe, fase 3) / 7 % (estrellas, fase 3) |
| mago | normal | 32 | 8 / 6 / 4 s | 21 s / 21 s / 18 s | 16 % (golpe, fase 3) / 17 % (golpe, fase 3) |
| mago | dificil | 32 | 8 / 6 / 4 s | 20 s / 20 s / 17 s | 16 % (golpe, fase 3) / 18 % (golpe, fase 3) |
| mago | extremo | 32 | 6 / 4 / 3 s | 16 s / 16 s / 14 s | 21 % (golpe, fase 3) / 23 % (golpe, fase 3) |

El tope por golpe del jefe (`hitCapBoss`) es el 8 % de su vida por golpe normal de un jugador (los remates y los golpes al aturdido lo pasan). Con su vida de ahora recorta el 0 % de los golpes de la referencia en Difícil: no es lo que marca el ritmo de la pelea, sino la red contra un golpe suelto enorme, y se queda como estaba. Un golpe de algo que no es un jugador le sigue haciendo un 33 %, y La forja reclama sigue saltando con 32 de vida intentada por los grandes (lo que era una décima parte de sus 320).

## Las siete sospechas, medidas

| # | Sospecha | Veredicto | Lo medido |
|---|---|---|---|
| 1 | La estamina apenas frena a las armas rápidas: golpear cansado (×0,6) sale mejor que esperar | **Confirmada** | En la pelea larga (60 s) el mejor ritmo de 10 de 10 armas rápidas no espera a la estamina; mediana de golpes cansados 93 %. Daño con estamina / sin estamina: espada 65 %, daga 76 %, espadon 58 %, hacha 61 %, lanza 63 %, mazo 54 %, tridente 60 %, mangual 55 %, guanteletes 71 %, guadana 60 %. |
| 2 | El tope por golpe (45 % de la vida) deja a las mejores armas en un mínimo de 3 golpes contra mobs de 20 de vida, y decide la velocidad | **Confirmada a medias** | Al 100 %, mediana de golpes que el tope recorta contra los vanilla: 49 %. Pero cada extra de mejora es otro golpe con su propio tope: golpes para matar un zombi, mediana 2,0 (2,0–3,0), no 3; los extras son el 41 % del daño de las mejores armas al 100 % (mediana). |
| 3 | Los pactos son ganancia pura para el daño y encima dan sitio (+10 de potencial cada uno) | **Confirmada** | Al 50 % el potencial pasa de 50 a 70 (carga 7 → 12) y el TTK baja en 12 de 12 tipos (hasta −51 %). Al 100 %: mediana −20 %. |
| 4 | Frenesí suma velocidad plana (+0,6), así que casi dobla el mazo | **Confirmada a medias** | Un mazo corriente pasa de 0,60 a 1,20 golpes/s (×2,00); el mejor mazo, de 1,05 a 1,54 (×1,48). Pero su TTK sólo baja 16 % con Frenesí sola, frente a una mediana de 12 % en todos los tipos: la estamina (golpes cansados) y la invulnerabilidad se comen la cadencia extra. |
| 5 | Con el frenesí lleno, Matagigantes, Ejecución y Crítico rinden mucho para lo que pesan | Refutada | Por punto de carga, esas tres superan a Filo en 18 de 36 casos (tipo × mejora). Sin el frenesí, el TTK medio al 100 % sube una mediana de +13 % (techo del frenesí: ×2,5 para las de un solo ingrediente). |
| 6 | Las mejoras de evento no pesan ni tienen techo: poder gratis | **Confirmada a medias** (gratis, pero rinde poco) | Lluvia estelar al 100 % encima de la mejor al 100 % (peso 0): TTK medio −3 % de mediana (−6 % a −1 %). Carnicero y Conductor sólo cuentan con más enemigos cerca, y aquí hay uno. |
| 7 | SwingStyle es sólo animación; el ×1,3 a la cabeza es igual para todos | **Confirmada a medias** | SwingStyle no entra en ningún número de daño salvo en *quién puede cargar*: no cargan baculo, grimorio. Pero no es sólo animación: la IA de los mobs lo lee (`ObsForja`, `RuleBrain`: reaccionan distinto a un tajo, un golpe desde arriba o una estocada), y eso aquí no se mide. Todo a la cabeza: TTK −21 % a +0 % según el tipo, no igual para todos: el tope por golpe y los extras (que no llevan el ×1,3) se comen parte. |

## Hallazgos que no estaban en la lista

- **La invulnerabilidad de vanilla (10 ticks) recorta los golpes rápidos, y los extras la esquivan.** Un golpe a menos de 10 ticks del anterior sólo quita lo que tenga *por encima* del último daño (medido con golpes reales: a los 5 ticks, un golpe igual no hace nada). Pero cada extra de mejora pone `invulnerableTime = 0` y deja como "último daño" el suyo, pequeño, así que el siguiente golpe rápido entra casi entero (medido: con una hoja de damasco, el segundo golpe a los 5 ticks entra). Daño en 60 s con / sin esa regla: hacha 90 %, espada 88 %, daga 80 %, espadon 95 %, lanza 93 %, mazo 100 %, tridente 93 %, mangual 100 %, guanteletes 85 %, guadana 93 %.
- **El tope por golpe no alcanza a los extras.** `CombatHooks.capped` corta cada llamada a `hurtServer`; cada extra de mejora es otra llamada, con su propio tope, así que un golpe con extras puede quitar más del 45 % de la vida (con Ráfaga y Cien manos los guanteletes matan a un esqueleto de un puñetazo). Parte del daño que viene de extras en las mejores armas al 100 %: hacha 40 %, espada 41 %, daga 45 %, espadon 39 %, lanza 45 %, mazo 40 %, tridente 42 %, mangual 36 %, guanteletes 52 %, guadana 40 %, baculo 46 %, grimorio 26 %.
- **Afilado (+3 por golpe mientras el arma está nueva) vale en cualquier pieza**, también en una atadura o una guarda, y se suma a cada golpe sin mirar la velocidad. Cambiar las piezas con Afilado de la mejor al 100 % por netherita sube el TTK: hacha +17 %, espada +31 %, daga +41 %, espadon +14 %, lanza +16 %, mazo +10 %, tridente +13 %, mangual +12 %, guanteletes +15 %, guadana +9 %, baculo +29 %, grimorio +13 %.
- **El mango de vidriacero está en 30 de 36 mejores armas.** Su velocidad de mango (+0,30) más la de su rasgo Diáfano (+0,3 al atributo) le dan el doble que cualquier otro mango. Cambiarlo por acero estelar (el mejor mango sin rasgo): espada +13 %, daga +19 %, espadon +13 %, hacha +21 %, lanza +9 %, mazo +5 %, tridente +9 %, mangual +3 %, guanteletes +17 %, guadana +11 %.
- **El Mestizaje sale en la ficha pero no en el golpe.** `ForgeStats.sheet(stack)` suma el 10 % de mezclar rasgos, pero `Assembler.write` escribe los atributos sin él: una espada de damasco con mango de vidriacero y guarda de eco enseña 8,70 de daño y pega 8,00. Pasa igual en armaduras y herramientas (sólo arcos, flechas, escudos y alas leen la ficha con el Mestizaje dentro).
- **La magia no gasta estamina ni se cansa**, no le afecta la invulnerabilidad (cada proyectil y cada mordisco de runa la ponen a 0) y pasa por encima de la armadura (daño mágico: el yunque andante, con 10 de armadura, pierde 1,15 por punto de rayo del báculo y 0,45 / 0,56 por punto de espada). TTK medio al 100 %: báculo 0,71 s, grimorio 1,24 s, frente a una mediana cuerpo a cuerpo de 0,70 s. La bruja sólo recibe el 15 % de la magia. El enderman no se teletransporta ante el rayo del báculo como ante las flechas; lo esquiva como un golpe (34 %, luego 7 s sin esquivar), y eso el modelo no lo cuenta.

## Mejor conjunto por tipo de arma

Ráfaga: daño en los 3 primeros segundos con la estamina llena. Sostenido: daño por segundo en 60 s. Los dos contra un maniquí neutro (sin armadura, sin tope, sin resistencias, barra de postura de un mob de 20 de vida): lo que el arma pone. TTK medio: media geométrica de los segundos para matar a los mobs de muestra. Cansados / en invulnerabilidad: parte de los golpes de la pelea larga dados sin estamina / que cayeron dentro de los 10 ticks de invulnerabilidad y sólo quitaron lo que superaba al último daño.

| Tipo | Escenario | Materiales | Mejoras | Ráfaga (daño/s) | Sostenido (daño/s) | TTK medio (s) | Ritmo sostenido | Cansados | En invulnerabilidad |
|---|---|---|---|---|---|---|---|---|---|
| espada | 0 % | hoja corazon · mango vidriacero · guarda damasco | — | 35,0 | 18,3 | 1,56 | cada 10 ticks | 93 % | 0 % |
| espada | 50 % | hoja corazon · mango vidriacero · guarda damasco | frenesi 50 %, ejecucion 50 %, matagigantes 50 % | 55,6 | 22,4 | 1,23 | cada 10 ticks | 93 % | 0 % |
| espada | 50 % con pactos | hoja damasco · mango vidriacero · guarda eco | frenesi 70 %, ejecucion 70 %, matagigantes 70 %, filo 70 %, pacto_de_sed, pacto_de_vidrio | 92,6 | 39,3 | 0,74 | cada 8 ticks | 95 % | 99 % |
| espada | 100 % | hoja damasco · mango vidriacero · guarda eco | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 68,1 | 32,5 | 0,68 | cada 7 ticks | 95 % | 99 % |
| espada | 100 % con pactos | hoja damasco · mango vidriacero · guarda eco | tormenta, critico, frenesi, ejecucion, matagigantes, brecha, pacto_de_sed, pacto_de_vidrio | 103,5 | 49,1 | 0,56 | cada 7 ticks | 95 % | 99 % |
| daga | 0 % | hoja damasco · mango vidriacero | — | 29,3 | 16,3 | 1,80 | cada 6 ticks | 96 % | 100 % |
| daga | 50 % | hoja damasco · mango vidriacero | critico 50 %, matagigantes 50 %, castigo 50 % | 47,5 | 22,9 | 1,15 | cada 7 ticks | 95 % | 99 % |
| daga | 50 % con pactos | hoja damasco · mango vidriacero | frenesi 70 %, ejecucion 70 %, matagigantes 70 %, filo 70 %, pacto_de_sed, pacto_de_vidrio | 87,2 | 42,4 | 0,65 | cada 6 ticks | 96 % | 100 % |
| daga | 100 % | hoja damasco · mango vidriacero | tormenta, critico, frenesi, ejecucion, matagigantes, filo, desgarro | 63,6 | 39,4 | 0,58 | cada 5 ticks | 97 % | 100 % |
| daga | 100 % con pactos | hoja damasco · mango vidriacero | tormenta, critico, frenesi, ejecucion, matagigantes, filo, aspecto_igneo, pacto_de_sed, pacto_de_vidrio | 94,9 | 57,8 | 0,43 | cada 5 ticks | 97 % | 100 % |
| espadon | 0 % | hoja corazon · hoja corazon · mango vidriacero · guarda damasco | — | 42,7 | 15,2 | 1,42 | cada 15 ticks | 90 % | 0 % |
| espadon | 50 % | hoja damasco · hoja vidriacero · mango vidriacero · guarda eco | frenesi 50 %, matagigantes 50 %, brecha 50 % | 62,7 | 23,5 | 1,20 | cada 10 ticks | 93 % | 0 % |
| espadon | 50 % con pactos | hoja corazon · hoja damasco · mango vidriacero · guarda eco | critico 70 %, frenesi 70 %, ejecucion 70 %, matagigantes 70 %, brecha 70 %, pacto_de_sed, pacto_de_vidrio | 124,2 | 44,3 | 0,75 | cada 11 ticks | 93 % | 0 % |
| espadon | 100 % | hoja damasco · hoja vidriacero · mango vidriacero · guarda eco | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 83,9 | 34,6 | 0,72 | cada 10 ticks | 93 % | 0 % |
| espadon | 100 % con pactos | hoja corazon · hoja damasco · mango vidriacero · guarda eco | tormenta, critico, ejecucion, matagigantes, brecha, pacto_de_sed, pacto_de_vidrio | 130,0 | 35,9 | 0,57 | cada 14 ticks | 91 % | 0 % |
| hacha | 0 % | cabeza_hacha corazon · mango vidriacero · atadura damasco | — | 39,6 | 15,3 | 1,46 | cada 13 ticks | 91 % | 0 % |
| hacha | 50 % | cabeza_hacha corazon · mango vidriacero · atadura damasco | critico 50 %, matagigantes 50 %, brecha 50 % | 65,3 | 19,2 | 1,19 | cada 13 ticks | 91 % | 0 % |
| hacha | 50 % con pactos | cabeza_hacha vidriacero · mango vidriacero · atadura damasco | critico 70 %, frenesi 70 %, ejecucion 70 %, matagigantes 70 %, brecha 70 %, pacto_de_sed, pacto_de_vidrio | 107,6 | 42,1 | 0,72 | cada 9 ticks | 94 % | 99 % |
| hacha | 100 % | cabeza_hacha vidriacero · mango vidriacero · atadura damasco | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 75,3 | 33,3 | 0,70 | cada 8 ticks | 95 % | 99 % |
| hacha | 100 % con pactos | cabeza_hacha vidriacero · mango vidriacero · atadura damasco | tormenta, critico, frenesi, ejecucion, matagigantes, brecha, pacto_de_sed, pacto_de_vidrio | 114,3 | 51,1 | 0,57 | cada 8 ticks | 95 % | 99 % |
| lanza | 0 % | punta_lanza corazon · mango vidriacero · atadura damasco | — | 24,2 | 10,8 | 2,70 | cada 10 ticks | 93 % | 0 % |
| lanza | 50 % | punta_lanza corazon · mango vidriacero · atadura damasco | matagigantes 50 %, filo 50 % | 35,0 | 11,9 | 1,83 | cada 13 ticks | 91 % | 0 % |
| lanza | 50 % con pactos | punta_lanza corazon · mango vidriacero · atadura damasco | frenesi 70 %, ejecucion 70 %, matagigantes 70 %, filo 70 %, pacto_de_sed, pacto_de_vidrio | 61,6 | 23,7 | 1,13 | cada 10 ticks | 93 % | 0 % |
| lanza | 100 % | punta_lanza corazon · mango vidriacero · atadura damasco | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 49,6 | 21,9 | 1,10 | cada 10 ticks | 93 % | 0 % |
| lanza | 100 % con pactos | punta_lanza corazon · mango vidriacero · atadura damasco | tormenta, critico, frenesi, ejecucion, matagigantes, filo, aspecto_igneo, pacto_de_sed, pacto_de_vidrio | 72,8 | 29,8 | 0,84 | cada 10 ticks | 93 % | 0 % |
| mazo | 0 % | cabeza_mazo corazon · mango vidriacero · atadura damasco | — | 33,5 | 11,3 | 1,50 | cada 10 ticks | 93 % | 0 % |
| mazo | 50 % | cabeza_mazo damasco · mango vidriacero · atadura eco | matagigantes 50 %, filo 50 % | 46,3 | 12,2 | 0,97 | cada 10 ticks | 93 % | 0 % |
| mazo | 50 % con pactos | cabeza_mazo corazon · mango vidriacero · atadura damasco | critico 70 %, ejecucion 70 %, matagigantes 70 %, filo 70 %, pacto_de_sed, pacto_de_vidrio | 93,9 | 21,3 | 0,70 | cada 19 ticks, carga para rematar, descansando al vaciarse | 0 % | 0 % |
| mazo | 100 % | cabeza_mazo damasco · mango vidriacero · atadura vara_de_blaze | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 61,4 | 24,4 | 0,65 | cada 11 ticks | 93 % | 0 % |
| mazo | 100 % con pactos | cabeza_mazo damasco · mango vidriacero · atadura eco | tormenta, critico, frenesi, ejecucion, matagigantes, filo, pacto_de_sed, pacto_de_vidrio | 94,1 | 34,6 | 0,48 | cada 11 ticks | 93 % | 0 % |
| tridente | 0 % | punta_tridente damasco · mango vidriacero · atadura eco | — | 35,7 | 14,9 | 1,59 | cada 12 ticks | 92 % | 0 % |
| tridente | 50 % | punta_tridente corazon · mango vidriacero · atadura damasco | frenesi 50 %, matagigantes 50 %, castigo 50 % | 57,1 | 21,7 | 1,22 | cada 11 ticks | 93 % | 0 % |
| tridente | 50 % con pactos | punta_tridente damasco · mango vidriacero · atadura vara_de_blaze | frenesi 70 %, ejecucion 70 %, matagigantes 70 %, filo 70 %, pacto_de_sed, pacto_de_vidrio | 107,9 | 39,3 | 0,75 | cada 10 ticks | 93 % | 0 % |
| tridente | 100 % | punta_tridente damasco · mango vidriacero · atadura eco | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 74,7 | 30,4 | 0,81 | cada 10 ticks | 93 % | 0 % |
| tridente | 100 % con pactos | punta_tridente damasco · mango vidriacero · atadura eco | critico, frenesi, ejecucion, matagigantes, brecha, aspecto_igneo, pacto_de_sed, pacto_de_vidrio | 125,9 | 45,0 | 0,60 | cada 9 ticks | 94 % | 99 % |
| mangual | 0 % | bola damasco · cadena eco · mango vidriacero | — | 38,1 | 13,6 | 1,18 | cada 10 ticks | 93 % | 0 % |
| mangual | 50 % | bola damasco · cadena eco · mango vidriacero | critico 50 %, ejecucion 50 %, matagigantes 50 % | 61,0 | 16,6 | 0,88 | cada 14 ticks | 91 % | 0 % |
| mangual | 50 % con pactos | bola damasco · cadena eco · mango vidriacero | critico 70 %, frenesi 70 %, ejecucion 70 %, matagigantes 70 %, brecha 70 %, pacto_de_sed, pacto_de_vidrio | 110,4 | 38,1 | 0,56 | cada 11 ticks | 93 % | 0 % |
| mangual | 100 % | bola corazon · cadena damasco · mango vidriacero | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 77,2 | 31,6 | 0,57 | cada 11 ticks | 93 % | 0 % |
| mangual | 100 % con pactos | bola damasco · cadena eco · mango vidriacero | tormenta, critico, frenesi, ejecucion, matagigantes, brecha, pacto_de_sed, pacto_de_vidrio | 116,2 | 46,6 | 0,46 | cada 11 ticks | 93 % | 0 % |
| guanteletes | 0 % | manopla vidriacero · nudillos corazon · remache damasco | — | 30,7 | 20,5 | 1,09 | cada 5 ticks | 97 % | 100 % |
| guanteletes | 50 % | manopla vidriacero · nudillos corazon · remache damasco | matagigantes 50 %, castigo 50 %, rafaga 50 % | 48,5 | 26,5 | 0,73 | cada 5 ticks | 97 % | 100 % |
| guanteletes | 50 % con pactos | manopla vidriacero · nudillos corazon · remache damasco | tormenta 70 %, ejecucion 70 %, brecha 70 %, pacto_de_sed, pacto_de_vidrio, rafaga 70 %, nudillos_de_hierro 70 % | 76,5 | 48,8 | 0,36 | cada 5 ticks | 97 % | 100 % |
| guanteletes | 100 % | manopla vidriacero · nudillos corazon · remache damasco | tormenta, frenesi, ejecucion, matagigantes, filo, rafaga, nudillos_de_hierro | 81,5 | 52,2 | 0,26 | cada 4 ticks | 97 % | 100 % |
| guanteletes | 100 % con pactos | manopla vidriacero · nudillos damasco · remache eco | critico, frenesi, ejecucion, matagigantes, filo, pacto_de_sed, pacto_de_vidrio, rafaga, nudillos_de_hierro | 114,2 | 73,0 | 0,20 | cada 4 ticks | 97 % | 100 % |
| guadana | 0 % | hoja corazon · mango vidriacero · atadura damasco | — | 36,5 | 14,3 | 1,56 | cada 13 ticks | 91 % | 0 % |
| guadana | 50 % | hoja corazon · mango vidriacero · atadura damasco | critico 50 %, matagigantes 50 %, brecha 50 % | 61,4 | 18,5 | 1,21 | cada 13 ticks | 91 % | 21 % |
| guadana | 50 % con pactos | hoja damasco · mango vidriacero · atadura eco | critico 70 %, frenesi 70 %, matagigantes 70 %, filo 70 %, pacto_de_sed, pacto_de_vidrio | 100,0 | 37,8 | 0,79 | cada 11 ticks | 93 % | 36 % |
| guadana | 100 % | hoja damasco · mango vidriacero · atadura eco | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 72,9 | 30,7 | 0,77 | cada 10 ticks | 93 % | 50 % |
| guadana | 100 % con pactos | hoja damasco · mango vidriacero · atadura eco | tormenta, critico, frenesi, ejecucion, matagigantes, filo, pacto_de_sed, pacto_de_vidrio | 112,0 | 45,1 | 0,55 | cada 10 ticks | 93 % | 50 % |
| baculo | 0 % | nucleo corazon · engaste prismarina · mango cuarzo | — | 17,7 | 1,9 | 2,35 | cada 10 ticks | 0 % | 0 % |
| baculo | 50 % | nucleo corazon · engaste prismarina · mango cuarzo | castigo 50 %, resonancia 50 % | 25,8 | 2,7 | 1,25 | cada 10 ticks | 0 % | 0 % |
| baculo | 50 % con pactos | nucleo corazon · engaste eco · mango cuarzo | tormenta 70 %, castigo 70 %, pacto_de_sed, pacto_de_vidrio, conjuro_veloz 70 %, resonancia 70 % | 33,9 | 3,2 | 0,99 | cada 9 ticks | 0 % | 0 % |
| baculo | 100 % | nucleo corazon · engaste prismarina · mango cuarzo | tormenta, matagigantes, filo, conjuro_veloz, sobrecarga, resonancia | 49,6 | 4,0 | 0,71 | cada 8 ticks | 0 % | 0 % |
| baculo | 100 % con pactos | nucleo corazon · engaste prismarina · mango cuarzo | tormenta, matagigantes, filo, pacto_de_sed, pacto_de_vidrio, conjuro_veloz, sobrecarga, resonancia | 49,6 | 4,0 | 0,71 | cada 8 ticks | 0 % | 0 % |
| grimorio | 0 % | nucleo corazon · tapas cuarzo · remache eco | — | 16,4 | 2,3 | 2,40 | cada 20 ticks | 0 % | 0 % |
| grimorio | 50 % | nucleo corazon · tapas cuarzo · remache eco | castigo 50 %, resonancia 50 % | 21,2 | 2,9 | 1,80 | cada 20 ticks | 0 % | 0 % |
| grimorio | 50 % con pactos | nucleo corazon · tapas cuarzo · remache eco | ejecucion 70 %, matagigantes 70 %, filo 70 %, pacto_de_sed, pacto_de_vidrio, resonancia 70 % | 23,7 | 3,2 | 1,46 | cada 20 ticks | 0 % | 0 % |
| grimorio | 100 % | nucleo corazon · tapas cuarzo · remache eco | tormenta, matagigantes, filo, conjuro_veloz, resonancia | 30,6 | 3,3 | 1,24 | cada 16 ticks | 0 % | 0 % |
| grimorio | 100 % con pactos | nucleo corazon · tapas cuarzo · remache eco | tormenta, matagigantes, filo, pacto_de_sed, pacto_de_vidrio, conjuro_veloz, resonancia | 30,6 | 3,3 | 1,24 | cada 16 ticks | 0 % | 0 % |

**Las tres mejores combinaciones de materiales sin mejoras** (TTK medio en la muestra):

- **espada** (3912 combinaciones tras podar cada hueco, 3877 distintas en pelea, 53 en el frente): hoja corazon · mango vidriacero · guarda damasco (1,56 s); hoja damasco · mango vidriacero · guarda eco (1,59 s); hoja damasco · mango vidriacero · guarda prismarina (1,63 s).
- **daga** (376 combinaciones tras podar cada hueco, 376 distintas en pelea, 29 en el frente): hoja damasco · mango vidriacero (1,80 s); hoja corazon · mango cuarzo (1,83 s); hoja damasco · mango eco (1,95 s).
- **espadon** (23787 combinaciones tras podar cada hueco, 23128 distintas en pelea, 109 en el frente): hoja corazon · hoja corazon · mango vidriacero · guarda damasco (1,42 s); hoja damasco · hoja solacero · mango vidriacero · guarda eco (1,47 s); hoja damasco · hoja vidriacero · mango vidriacero · guarda eco (1,47 s).
- **hacha** (3917 combinaciones tras podar cada hueco, 3464 distintas en pelea, 29 en el frente): cabeza_hacha corazon · mango vidriacero · atadura damasco (1,46 s); cabeza_hacha damasco · mango vidriacero · atadura prismarina (1,47 s); cabeza_hacha damasco · mango vidriacero · atadura vara_de_blaze (1,49 s).
- **lanza** (798 combinaciones tras podar cada hueco, 738 distintas en pelea, 75 en el frente): punta_lanza corazon · mango vidriacero · atadura damasco (2,70 s); punta_lanza damasco · mango vidriacero · atadura eco (2,74 s); punta_lanza damasco · mango acero_estelar · atadura vidriacero (2,79 s).
- **mazo** (3934 combinaciones tras podar cada hueco, 3890 distintas en pelea, 52 en el frente): cabeza_mazo corazon · mango vidriacero · atadura damasco (1,50 s); cabeza_mazo damasco · mango vidriacero · atadura eco (1,51 s); cabeza_mazo damasco · mango vidriacero · atadura vara_de_blaze (1,53 s).
- **tridente** (3914 combinaciones tras podar cada hueco, 3879 distintas en pelea, 55 en el frente): punta_tridente damasco · mango vidriacero · atadura eco (1,59 s); punta_tridente corazon · mango vidriacero · atadura damasco (1,60 s); punta_tridente damasco · mango vidriacero · atadura prismarina (1,61 s).
- **mangual** (3927 combinaciones tras podar cada hueco, 3883 distintas en pelea, 52 en el frente): bola damasco · cadena eco · mango vidriacero (1,18 s); bola corazon · cadena damasco · mango vidriacero (1,20 s); bola damasco · cadena vara_de_blaze · mango vidriacero (1,21 s).
- **guanteletes** (4343 combinaciones tras podar cada hueco, 4301 distintas en pelea, 53 en el frente): manopla vidriacero · nudillos corazon · remache damasco (1,09 s); manopla vidriacero · nudillos damasco · remache eco (1,23 s); manopla cuarzo · nudillos corazon · remache prismarina (1,24 s).
- **guadana** (3917 combinaciones tras podar cada hueco, 3879 distintas en pelea, 55 en el frente): hoja corazon · mango vidriacero · atadura damasco (1,56 s); hoja damasco · mango vidriacero · atadura eco (1,59 s); hoja damasco · mango vidriacero · atadura prismarina (1,60 s).
- **baculo** (3870 combinaciones tras podar cada hueco, 3840 distintas en pelea, 54 en el frente): nucleo corazon · engaste prismarina · mango cuarzo (2,35 s); nucleo corazon · engaste vara_de_blaze · mango cuarzo (2,35 s); nucleo corazon · engaste eco · mango cuarzo (2,35 s).
- **grimorio** (3905 combinaciones tras podar cada hueco, 3868 distintas en pelea, 51 en el frente): nucleo corazon · tapas cuarzo · remache eco (2,40 s); nucleo corazon · tapas cuarzo · remache prismarina (2,41 s); nucleo corazon · tapas cuarzo · remache vara_de_blaze (2,41 s).

**Mejor arma de cada tipo** (100 %, sin pactos):

- **espada**: hoja damasco · mango vidriacero · guarda eco; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,68 s, 32,5 daño/s sostenido. *No se mide aquí:* el barrido (Filo arrasador) y la guardia con parada.
- **daga**: hoja damasco · mango vidriacero; tormenta, critico, frenesi, ejecucion, matagigantes, filo, desgarro — TTK medio 0,58 s, 39,4 daño/s sostenido. *No se mide aquí:* lanzar la hoja (Lanzacabezas) y la guardia con parada.
- **espadon**: hoja damasco · hoja vidriacero · mango vidriacero · guarda eco; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,72 s, 34,6 daño/s sostenido. *No se mide aquí:* el barrido (Filo arrasador) y la guardia con parada.
- **hacha**: cabeza_hacha vidriacero · mango vidriacero · atadura damasco; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,70 s, 33,3 daño/s sostenido. *No se mide aquí:* romper escudos y talar.
- **lanza**: punta_lanza corazon · mango vidriacero · atadura damasco; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 1,10 s, 21,9 daño/s sostenido. *No se mide aquí:* la carga a la carrera o a caballo (arma cinética) y el alcance.
- **mazo**: cabeza_mazo damasco · mango vidriacero · atadura vara_de_blaze; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,65 s, 24,4 daño/s sostenido. *No se mide aquí:* el golpe cayendo (Densidad, Estallido de viento).
- **tridente**: punta_tridente damasco · mango vidriacero · atadura eco; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,81 s, 30,4 daño/s sostenido. *No se mide aquí:* lanzarlo (Retorno, Corriente, Canalización) y el alcance.
- **mangual**: bola corazon · cadena damasco · mango vidriacero; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,57 s, 31,6 daño/s sostenido. *No se mide aquí:* el área (Segunda cabeza, Martillo pilón), el aturdimiento y el alcance de su cadena.
- **guanteletes**: manopla vidriacero · nudillos corazon · remache damasco; tormenta, frenesi, ejecucion, matagigantes, filo, rafaga, nudillos_de_hierro — TTK medio 0,26 s, 52,2 daño/s sostenido. *No se mide aquí:* el combo de Nudillos y la Maestría más rápida.
- **guadana**: hoja damasco · mango vidriacero · atadura eco; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,77 s, 30,7 daño/s sostenido. *No se mide aquí:* el barrido, el alcance y la cosecha.
- **baculo**: nucleo corazon · engaste prismarina · mango cuarzo; tormenta, matagigantes, filo, conjuro_veloz, sobrecarga, resonancia — TTK medio 0,71 s, 4,0 daño/s sostenido. *No se mide aquí:* el abanico de Prisma y los proyectiles que buscan (Buscador).
- **grimorio**: nucleo corazon · tapas cuarzo · remache eco; tormenta, matagigantes, filo, conjuro_veloz, resonancia — TTK medio 1,24 s, 3,3 daño/s sostenido. *No se mide aquí:* el área entera de la runa (todo lo que pisa), Vórtice y Santuario.

## Mangos y ataduras: normal, pesado y ligero, en cualquier material

Cabeza de hierro y nada más, sin mejoras; el mango y la atadura del material de la fila, en las tres formas: normal, pesada (mango con contrapeso y atadura remachada) y ligera (mango fino y atadura delgada); la espada no lleva atadura, sólo cambia su mango. Cada pieza pesa la densidad de su material por su forma (mango ×2,30 pesado, ×0,35 ligero; atadura ×1,60 y ×0,40), y el trato de la forma va encima. Cada celda: normal / pesado / ligero. Ráfaga, sostenido y TTK medio como arriba (el TTK, sobre los mobs de la búsqueda). Aturdidos/min: veces que la barra de postura del maniquí (un mob de 20 de vida) se llena en la pelea larga. Estamina por golpe: la de un golpe normal (12 de base). Lo que el maniquí no mide y también cuenta: la atadura pesada abarata los bloqueos y no suelta la carga con un golpe de escudo, y con la ligera la guardia rota tarda más en volver.

| Tipo | Material | Peso (kg) | Golpes/s | Durabilidad | Ráfaga (daño/s) | Sostenido (daño/s) | TTK medio (s) | Estamina por golpe | Aturdidos/min |
|---|---|---|---|---|---|---|---|---|---|
| espada | madera | 1,30 / 1,44 / 1,23 | 1,60 / 1,54 / 1,63 | 262 / 262 / 262 | 19,1 / 21,1 / 18,1 | 6,9 / 6,7 / 7,1 | 4,06 / 4,12 / 4,15 | 12,0 / 13,8 / 10,8 | 10,0 / 11,0 / 9,0 |
| espada | hueso | 1,33 / 1,50 / 1,24 | 1,69 / 1,61 / 1,73 | 301 / 301 / 301 | 19,1 / 21,1 / 18,1 | 7,5 / 7,0 / 7,3 | 3,91 / 3,98 / 4,12 | 12,0 / 13,8 / 10,8 | 10,0 / 11,0 / 9,0 |
| espada | hierro | 1,37 / 1,61 / 1,26 | 1,57 / 1,48 / 1,62 | 314 / 314 / 314 | 19,1 / 20,3 / 18,1 | 6,7 / 6,3 / 7,1 | 4,07 / 4,30 / 4,15 | 12,0 / 13,8 / 10,8 | 9,0 / 10,0 / 9,0 |
| espada | netherita | 1,47 / 1,84 / 1,29 | 1,53 / 1,40 / 1,60 | 393 / 393 / 393 | 19,1 / 20,5 / 18,1 | 6,6 / 6,1 / 7,1 | 4,08 / 4,40 / 4,15 | 12,0 / 13,8 / 10,8 | 10,0 / 10,0 / 9,0 |
| espada | vidriacero | 1,32 / 1,49 / 1,24 | 2,19 / 2,11 / 2,23 | 196 / 196 / 196 | 19,1 / 21,1 / 18,1 | 8,5 / 8,5 / 8,5 | 3,65 / 3,60 / 3,79 | 12,0 / 13,8 / 10,8 | 11,0 / 12,0 / 10,0 |
| hacha | madera | 1,60 / 1,85 / 1,44 | 1,00 / 0,95 / 1,04 | 262 / 314 / 223 | 24,3 / 23,3 / 22,8 | 6,3 / 6,3 / 6,8 | 3,93 / 4,18 / 3,83 | 12,0 / 13,8 / 10,8 | 10,0 / 12,0 / 9,0 |
| hacha | hueso | 1,67 / 1,98 / 1,46 | 1,08 / 1,01 / 1,13 | 329 / 395 / 280 | 24,7 / 24,1 / 22,8 | 6,6 / 6,2 / 7,2 | 3,81 / 4,01 / 3,76 | 12,0 / 13,8 / 10,8 | 9,0 / 11,0 / 10,0 |
| hacha | hierro | 1,78 / 2,21 / 1,51 | 0,96 / 0,88 / 1,02 | 360 / 432 / 306 | 23,9 / 19,0 / 22,5 | 6,5 / 5,9 / 6,8 | 4,53 / 4,42 / 3,85 | 12,0 / 13,8 / 10,8 | 10,0 / 11,0 / 9,0 |
| hacha | netherita | 2,03 / 2,68 / 1,60 | 0,91 / 0,81 / 1,00 | 984 / 1181 / 836 | 10,8 / 17,3 / 22,4 | 6,4 / 5,8 / 6,8 | 4,85 / 4,48 / 3,85 | 12,0 / 13,8 / 10,8 | 10,0 / 11,0 / 9,0 |
| hacha | vidriacero | 1,66 / 1,96 / 1,46 | 1,58 / 1,51 / 1,64 | 323 / 388 / 275 | 25,5 / 28,0 / 24,1 | 9,1 / 8,8 / 9,6 | 3,03 / 2,81 / 2,92 | 12,0 / 13,8 / 10,8 | 11,0 / 13,0 / 10,0 |
| mazo | madera | 3,20 / 3,70 / 2,87 | 0,60 / 0,57 / 0,62 | 524 / 629 / 445 | 9,0 / 9,7 / 6,4 | 4,0 / 3,8 / 4,2 | 6,76 / 6,24 / 6,75 | 12,0 / 13,8 / 10,8 | 9,0 / 10,0 / 9,0 |
| mazo | hueso | 3,34 / 3,97 / 2,92 | 0,69 / 0,65 / 0,72 | 658 / 790 / 559 | 10,0 / 10,6 / 6,8 | 4,7 / 4,1 / 4,8 | 5,79 / 5,40 / 5,68 | 12,0 / 13,8 / 10,8 | 10,0 / 10,0 / 9,0 |
| mazo | hierro | 3,57 / 4,42 / 3,01 | 0,58 / 0,53 / 0,61 | 720 / 864 / 612 | 8,8 / 9,2 / 6,4 | 3,8 / 3,7 / 4,1 | 6,96 / 6,73 / 6,85 | 12,0 / 13,8 / 10,8 | 9,0 / 10,0 / 8,0 |
| mazo | netherita | 4,06 / 5,37 / 3,19 | 0,55 / 0,49 / 0,60 | 1969 / 2363 / 1674 | 8,5 / 8,5 / 6,3 | 3,7 / 3,4 / 4,0 | 7,50 / 7,41 / 7,14 | 12,0 / 13,8 / 10,8 | 9,0 / 10,0 / 8,0 |
| mazo | vidriacero | 3,31 / 3,91 / 2,91 | 1,19 / 1,13 / 1,23 | 645 / 774 / 548 | 18,5 / 18,5 / 17,1 | 5,2 / 5,0 / 5,5 | 3,31 / 3,43 / 3,53 | 12,0 / 13,8 / 10,8 | 11,0 / 11,0 / 10,0 |

La prueba mide 120 combinaciones: 4 tipos (también la lanza), 5 materiales y las 7 formas de mango y atadura (mango pesado o ligero solo, atadura pesada o ligera sola, todo pesado y todo ligero).

**Ninguna variante domina:** ninguna gana a la normal de su tipo y su material en ráfaga, sostenido y TTK a la vez sin pagarlo en estamina, postura (aturdidos o equilibrio quitado por segundo) o durabilidad.

**Ninguna combinación gana a todas:** ninguna mezcla de material y forma es mejor que todas las demás de su tipo en todo a la vez.

## Tiempo para matar (s), mejores armas al 100 %

En negrita el tipo más rápido contra ese mob. "—": no se le puede hacer daño así (sellos, absorción). "> 120": no muere en dos minutos.

| Mob | espada | daga | espadon | hacha | lanza | mazo | tridente | mangual | guanteletes | guadana | baculo | grimorio |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| zombie | 0,35 | 0,25 | 0,45 | 0,40 | 0,45 | 0,50 | 0,45 | 0,50 | **0,20** | 0,45 | 0,40 | 0,80 |
| husk | 0,35 | 0,25 | 0,45 | 0,40 | 0,45 | 0,50 | 0,45 | 0,50 | **0,20** | 0,45 | 0,40 | 0,80 |
| drowned | 0,35 | 0,25 | 0,45 | 0,40 | 0,45 | 0,50 | 0,45 | 0,50 | **0,20** | 0,45 | 0,40 | 0,80 |
| zombie_villager | 0,35 | 0,25 | 0,45 | 0,40 | 0,45 | 0,50 | 0,45 | 0,50 | **0,20** | 0,45 | 0,40 | 0,80 |
| skeleton | 0,35 | 0,25 | 0,45 | 0,40 | 0,65 | 0,17 | 0,45 | 0,17 | **0,02** | 0,45 | 0,30 | 0,80 |
| stray | 0,35 | 0,25 | 0,45 | 0,40 | 0,65 | 0,17 | 0,45 | 0,17 | **0,02** | 0,45 | 0,30 | 0,80 |
| bogged | 0,29 | 0,22 | 0,38 | 0,33 | 0,45 | 0,17 | 0,45 | 0,17 | **0,16** | 0,38 | 0,34 | 0,80 |
| wither_skeleton | 0,35 | 0,25 | 0,45 | 0,40 | 0,54 | 0,17 | 0,45 | 0,17 | **0,06** | 0,45 | 0,30 | 0,80 |
| creeper | 0,35 | 0,25 | 0,45 | 0,40 | 0,45 | 0,50 | 0,45 | 0,42 | **0,20** | 0,45 | 0,40 | 0,80 |
| spider | 0,12 | 0,15 | 0,15 | 0,13 | 0,38 | 0,17 | 0,38 | 0,17 | **0,02** | 0,15 | 0,30 | 0,65 |
| cave_spider | 0,12 | 0,08 | 0,15 | 0,13 | 0,15 | 0,17 | 0,15 | 0,17 | **0,02** | 0,15 | 0,30 | 0,45 |
| pillager | 0,35 | 0,25 | 0,45 | 0,40 | 0,45 | 0,43 | 0,45 | 0,29 | **0,20** | 0,38 | 0,40 | 0,80 |
| vindicator | 0,35 | 0,25 | 0,45 | 0,40 | 0,45 | 0,43 | 0,45 | 0,29 | **0,20** | 0,38 | 0,40 | 0,80 |
| evoker | 0,35 | 0,25 | 0,45 | 0,40 | 0,45 | 0,43 | 0,45 | 0,29 | **0,20** | 0,38 | 0,40 | 0,80 |
| witch | 0,35 | 0,25 | 0,45 | 0,40 | 0,45 | 0,50 | 0,45 | 0,50 | **0,20** | 0,45 | 0,90 | 1,87 |
| enderman | 0,45 | 0,50 | 0,45 | 0,40 | 0,90 | 0,60 | 0,45 | 0,50 | **0,29** | 0,45 | 0,75 | 0,89 |
| blaze | 0,35 | 0,25 | 0,45 | 0,40 | 0,45 | 0,50 | 0,45 | 0,42 | **0,20** | 0,45 | 0,40 | 0,80 |
| piglin_brute | 0,70 | 0,50 | 0,54 | 0,80 | 1,00 | 1,00 | 0,90 | 0,60 | **0,40** | 0,90 | 0,83 | 1,60 |
| herrero_caido *(Forja)* | 39,86 | 29,31 | 40,38 | 39,08 | 55,35 | 55,37 | 42,31 | 45,24 | **25,05** | 40,44 | > 120 | > 120 |
| automata_de_forja *(Forja)* | 2,40 | 1,91 | 2,25 | 2,25 | 4,00 | 1,79 | 2,63 | 1,38 | **0,80** | 2,25 | 1,40 | 2,40 |
| coraza_vacia *(Forja)* | 4,90 | 4,51 | 4,50 | 4,80 | 4,98 | **1,75** | 4,50 | 3,50 | 2,35 | 4,52 | 31,80 | 5,15 |
| pavesa *(Forja)* | 0,12 | 0,08 | 0,15 | 0,13 | 0,15 | 0,17 | 0,15 | 0,17 | **0,02** | 0,15 | 0,30 | 0,65 |
| herrumbre *(Forja)* | 0,12 | 0,08 | 0,15 | 0,13 | 0,26 | **0,00** | 0,26 | **0,00** | **0,00** | 0,15 | **0,00** | 0,26 |
| ascua_mayor *(Forja)* | 0,35 | 0,35 | 0,45 | 0,40 | 0,83 | 0,50 | 0,45 | 0,50 | **0,21** | 0,45 | 0,74 | 0,80 |
| escoria_viviente *(Forja)* | 0,20 | **0,15** | 0,26 | 0,23 | 0,45 | 1,00 | 0,26 | 0,50 | 0,40 | 0,26 | 0,82 | 0,80 |
| yunque_andante *(Forja)* | 2,36 | 1,86 | 2,36 | 2,20 | 2,34 | 1,00 | 1,66 | 1,00 | **0,40** | 2,30 | 1,10 | 1,60 |
| percutor *(Forja)* | 1,75 | 1,50 | 1,80 | 1,75 | 2,25 | 2,10 | 1,41 | 2,00 | **1,18** | 2,00 | 1,97 | 2,40 |
| tenaza *(Forja)* | 0,50 | 0,50 | 0,45 | 0,40 | 0,90 | 0,55 | 0,45 | 0,50 | **0,29** | 0,45 | 0,71 | 0,80 |
| cargador_de_carbon *(Forja)* | 0,70 | 0,50 | 0,45 | 0,50 | 0,90 | 0,75 | 0,54 | 0,50 | **0,42** | 0,50 | 0,80 | 1,60 |
| templador *(Forja)* | 0,35 | 0,25 | 0,45 | 0,40 | 0,45 | 0,50 | 0,45 | 0,50 | **0,20** | 0,45 | 0,40 | 0,80 |
| nucleo_estelar *(Forja)* | — | — | — | — | — | — | — | — | — | — | — | — |
| molde_roto *(Forja)* | 1,00 | 0,75 | 0,90 | 0,82 | 1,43 | 1,00 | 1,00 | 1,00 | **0,53** | 0,96 | 1,13 | 1,60 |
| guardian_de_cuno *(Forja)* | 6,31 | 4,85 | 6,00 | 5,77 | 8,19 | 2,50 | 5,50 | 2,50 | **1,83** | 5,86 | 2,80 | 7,80 |

## Tiempo para matar (s), mejores armas al 50 %

En negrita el tipo más rápido contra ese mob. "—": no se le puede hacer daño así (sellos, absorción). "> 120": no muere en dos minutos.

| Mob | espada | daga | espadon | hacha | lanza | mazo | tridente | mangual | guanteletes | guadana | baculo | grimorio |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| zombie | 0,45 | 0,30 | 0,50 | 0,50 | 1,00 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,50 | 0,80 |
| husk | 0,45 | 0,30 | 0,50 | 0,50 | 1,00 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,50 | 0,80 |
| drowned | 0,45 | 0,30 | 0,50 | 0,50 | 1,00 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,50 | 0,80 |
| zombie_villager | 0,45 | 0,30 | 0,50 | 0,50 | 1,00 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,50 | 0,80 |
| skeleton | 0,40 | 0,30 | 0,50 | 0,50 | 1,00 | 0,50 | 0,50 | 0,35 | **0,18** | 0,50 | 0,30 | 0,80 |
| stray | 0,40 | 0,30 | 0,50 | 0,50 | 1,00 | 0,50 | 0,50 | 0,35 | **0,18** | 0,50 | 0,30 | 0,80 |
| bogged | 0,40 | 0,30 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,18** | 0,50 | 0,30 | 0,80 |
| wither_skeleton | 0,40 | 0,30 | 0,50 | 0,50 | 1,00 | 0,50 | 0,50 | 0,35 | **0,18** | 0,50 | 0,30 | 0,80 |
| creeper | **0,40** | 0,59 | 0,50 | 0,50 | 0,70 | 0,50 | 0,50 | 0,50 | 0,43 | 0,50 | 0,80 | 1,00 |
| spider | 0,40 | 0,23 | 0,50 | 0,35 | 0,50 | 0,50 | 0,50 | 0,35 | **0,18** | 0,37 | 0,50 | 0,80 |
| cave_spider | 0,40 | 0,23 | 0,50 | 0,35 | 0,50 | 0,50 | 0,50 | 0,35 | **0,18** | 0,35 | 0,30 | 0,80 |
| pillager | **0,45** | 0,59 | 0,50 | 0,50 | 1,00 | 0,50 | 0,50 | 0,50 | 0,48 | 0,50 | 0,80 | 1,00 |
| vindicator | **0,45** | 0,59 | 0,50 | 0,50 | 1,00 | 0,50 | 0,50 | 0,50 | 0,48 | 0,50 | 0,80 | 1,00 |
| evoker | **0,45** | 0,59 | 0,50 | 0,50 | 1,00 | 0,50 | 0,50 | 0,50 | 0,48 | 0,50 | 0,80 | 1,00 |
| witch | **0,50** | 0,70 | **0,50** | **0,50** | 1,00 | 0,85 | **0,50** | **0,50** | **0,50** | 0,65 | 1,50 | 2,80 |
| enderman | 0,90 | 1,08 | 1,00 | 1,00 | 1,50 | 1,00 | 1,00 | 1,00 | **0,89** | 1,00 | 1,50 | 2,00 |
| blaze | **0,40** | 0,59 | 0,50 | 0,50 | 0,70 | 0,50 | 0,50 | 0,50 | 0,43 | 0,50 | 0,80 | 1,00 |
| piglin_brute | 1,35 | 1,38 | **1,00** | **1,00** | 1,65 | **1,00** | **1,00** | **1,00** | 1,14 | **1,00** | 2,00 | 2,00 |
| herrero_caido *(Forja)* | 71,50 | 67,20 | 62,50 | 76,70 | 104,50 | 104,50 | 71,50 | 90,97 | **61,94** | 75,63 | > 120 | > 120 |
| automata_de_forja *(Forja)* | 5,00 | 5,41 | 3,50 | 4,04 | 9,10 | 2,50 | 6,00 | 2,40 | **2,25** | 4,34 | 3,00 | 4,00 |
| coraza_vacia *(Forja)* | 6,00 | 6,40 | 5,50 | 6,00 | 6,60 | 4,50 | 5,40 | 3,48 | **1,79** | 6,00 | 32,25 | 7,45 |
| pavesa *(Forja)* | 0,40 | 0,23 | 0,50 | 0,35 | 0,50 | 0,50 | 0,50 | 0,35 | **0,18** | 0,35 | 0,30 | 0,80 |
| herrumbre *(Forja)* | 0,40 | 0,23 | 0,50 | 0,35 | 0,50 | **0,00** | 0,50 | **0,00** | **0,00** | 0,35 | 0,30 | 0,45 |
| ascua_mayor *(Forja)* | 0,80 | 0,99 | 1,00 | 1,00 | 1,00 | 1,00 | 1,00 | 1,00 | **0,69** | 1,00 | 1,50 | 1,95 |
| escoria_viviente *(Forja)* | 0,40 | **0,30** | 0,50 | 0,50 | 0,65 | 1,00 | 0,50 | 1,00 | 1,00 | 0,37 | 1,80 | 1,00 |
| yunque_andante *(Forja)* | 5,00 | 5,33 | 4,00 | 4,55 | 4,55 | **1,00** | 2,55 | **1,00** | 1,19 | 4,72 | 2,00 | 3,00 |
| percutor *(Forja)* | **2,40** | 3,40 | 2,50 | 2,50 | 2,50 | 2,50 | 2,50 | **2,40** | 4,00 | 2,50 | 4,50 | 4,45 |
| tenaza *(Forja)* | 1,00 | 1,08 | 1,00 | 1,00 | 1,50 | 1,00 | 1,00 | 1,00 | **0,86** | 1,00 | 1,30 | 1,80 |
| cargador_de_carbon *(Forja)* | 1,00 | 1,29 | 1,00 | 1,00 | 1,50 | 1,00 | 1,00 | 1,00 | **0,91** | 1,00 | 1,50 | 2,00 |
| templador *(Forja)* | **0,50** | 0,70 | **0,50** | 0,65 | 1,00 | 0,85 | 1,00 | **0,50** | 0,60 | 0,65 | 1,00 | 1,00 |
| nucleo_estelar *(Forja)* | — | — | — | — | — | — | — | — | — | — | — | — |
| molde_roto *(Forja)* | 1,90 | 1,93 | 1,50 | **1,38** | 2,50 | 1,85 | 2,20 | **1,38** | 1,51 | 1,39 | 2,30 | 2,95 |
| guardian_de_cuno *(Forja)* | 13,00 | 12,82 | 10,50 | 12,32 | 18,20 | 5,50 | 11,55 | **3,27** | 6,53 | 12,28 | 47,50 | 57,45 |

## Tiempo para matar (s), mejores armas al 0 %

En negrita el tipo más rápido contra ese mob. "—": no se le puede hacer daño así (sellos, absorción). "> 120": no muere en dos minutos.

| Mob | espada | daga | espadon | hacha | lanza | mazo | tridente | mangual | guanteletes | guadana | baculo | grimorio |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| zombie | **0,50** | 0,60 | **0,50** | **0,50** | 1,00 | 0,85 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| husk | **0,50** | 0,60 | **0,50** | **0,50** | 1,00 | 0,85 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| drowned | **0,50** | 0,60 | **0,50** | **0,50** | 1,00 | 0,85 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| zombie_villager | **0,50** | 0,60 | **0,50** | **0,50** | 1,00 | 0,85 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| skeleton | 0,50 | 0,60 | 0,50 | 0,50 | 1,50 | 0,50 | 1,00 | 0,50 | **0,25** | 0,50 | 0,50 | 1,00 |
| stray | 0,50 | 0,60 | 0,50 | 0,50 | 1,50 | 0,50 | 1,00 | 0,50 | **0,25** | 0,50 | 0,50 | 1,00 |
| bogged | 0,50 | **0,35** | 0,50 | 0,50 | 0,65 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 1,00 |
| wither_skeleton | **0,50** | 0,60 | **0,50** | **0,50** | 1,40 | **0,50** | 1,00 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 |
| creeper | **0,50** | 0,60 | **0,50** | **0,50** | 1,00 | 0,85 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| spider | 0,50 | 0,30 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,50 | 1,00 |
| cave_spider | 0,50 | 0,30 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,50 | 0,95 |
| pillager | 1,00 | 0,70 | **0,50** | 0,65 | 1,00 | 1,00 | 0,65 | 0,70 | 0,75 | 0,65 | 1,00 | 1,00 |
| vindicator | 1,00 | 0,70 | **0,50** | 0,65 | 1,00 | 1,00 | 0,65 | 0,70 | 0,75 | 0,65 | 1,00 | 1,00 |
| evoker | 1,00 | 0,70 | **0,50** | 0,65 | 1,00 | 1,00 | 0,65 | 0,70 | 0,75 | 0,65 | 1,00 | 1,00 |
| witch | 1,00 | 0,90 | 0,70 | **0,65** | 1,00 | 1,00 | 1,00 | 0,80 | 0,75 | 1,00 | 3,00 | 4,45 |
| enderman | 1,50 | 1,50 | **1,00** | 1,30 | 2,50 | 1,50 | 1,50 | 1,40 | 1,25 | 1,50 | 2,00 | 2,00 |
| blaze | **0,50** | 0,60 | **0,50** | **0,50** | 1,00 | 0,85 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| piglin_brute | **1,50** | 2,00 | **1,50** | **1,50** | 2,50 | 1,95 | 1,60 | **1,50** | **1,50** | **1,50** | 2,50 | 3,00 |
| herrero_caido *(Forja)* | 76,50 | 84,30 | 90,75 | 85,80 | > 120 | > 120 | 85,20 | 97,50 | **66,25** | 92,30 | > 120 | > 120 |
| automata_de_forja *(Forja)* | 6,00 | 7,50 | 6,00 | 5,85 | 12,35 | 2,50 | 7,15 | **2,40** | 3,50 | 6,50 | 4,00 | 4,95 |
| coraza_vacia *(Forja)* | 6,50 | 7,00 | 6,60 | 6,60 | 7,50 | **1,50** | 6,00 | 4,50 | **1,50** | 2,50 | 47,20 | 29,50 |
| pavesa *(Forja)* | 0,50 | 0,30 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,50 | 1,00 |
| herrumbre *(Forja)* | 0,50 | 0,30 | 0,50 | 0,50 | 0,65 | **0,00** | 0,50 | **0,00** | **0,00** | 0,50 | 0,50 | 0,45 |
| ascua_mayor *(Forja)* | **1,00** | 1,30 | **1,00** | **1,00** | 1,65 | 1,50 | **1,00** | **1,00** | **1,00** | **1,00** | 2,00 | 2,00 |
| escoria_viviente *(Forja)* | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,50 | **0,50** | 1,40 | 1,25 | **0,50** | 2,50 | 1,95 |
| yunque_andante *(Forja)* | 6,00 | 7,50 | 6,00 | 5,85 | 7,00 | 2,50 | 3,90 | **1,70** | 1,75 | 6,50 | 3,00 | 3,95 |
| percutor *(Forja)* | 3,50 | 6,00 | **2,50** | **2,50** | 7,00 | 6,70 | **2,50** | 3,60 | 5,50 | 3,00 | 62,20 | 9,45 |
| tenaza *(Forja)* | 1,50 | 1,50 | 1,40 | 1,30 | 2,50 | 1,50 | 1,30 | 1,40 | **1,25** | 1,30 | 2,00 | 2,00 |
| cargador_de_carbon *(Forja)* | 1,50 | 1,70 | 1,40 | **1,30** | 2,50 | 1,50 | 1,50 | 1,50 | 1,50 | 1,50 | 2,50 | 3,00 |
| templador *(Forja)* | 1,00 | 1,00 | **0,70** | 0,75 | 1,50 | 1,00 | 1,00 | 1,00 | 0,75 | 1,00 | 1,50 | 1,00 |
| nucleo_estelar *(Forja)* | — | — | — | — | — | — | — | — | — | — | — | — |
| molde_roto *(Forja)* | 2,00 | 2,30 | 2,40 | 2,30 | 4,00 | 2,00 | 2,50 | **1,80** | 1,90 | 2,30 | 3,00 | 3,00 |
| guardian_de_cuno *(Forja)* | 15,00 | 17,40 | 17,25 | 15,60 | 22,50 | 15,00 | 14,40 | 10,00 | **8,50** | 16,90 | > 120 | 84,45 |

### Comprobación: pelea simulada contra vida ÷ daño sostenido

Para cada pelea de 2 s o más al 100 %, el TTK simulado dividido por la cuenta simple (vida máxima entre el daño por segundo sostenido contra ese mismo mob, con el mismo ritmo). Cerca de 1: la pelea es su daño sostenido. Por debajo: la pelea mata antes de lo que dice su daño sostenido (los primeros golpes van con la estamina llena, un aturdido temprano, Ejecución por debajo del 30 %). Por encima: pausas que la cuenta no ve (la finta de la coraza, esperar a la estamina). Las peleas más cortas no entran: con 2 o 3 golpes el primero cae en el segundo 0 y la cuenta no tiene sentido.

| Tipo | Peleas | Mediana | Rango | La más lejos de 1 |
|---|---|---|---|---|
| espada | 5 | ×0,85 | ×0,65 – ×1,75 | coraza_vacia (4,9 s simulado, 2,8 s estimado) |
| daga | 3 | ×0,96 | ×0,88 – ×1,90 | coraza_vacia (4,5 s simulado, 2,4 s estimado) |
| espadon | 5 | ×0,77 | ×0,61 – ×1,62 | automata_de_forja (2,3 s simulado, 3,7 s estimado) |
| hacha | 5 | ×0,80 | ×0,62 – ×1,80 | coraza_vacia (4,8 s simulado, 2,7 s estimado) |
| lanza | 6 | ×0,83 | ×0,62 – ×1,71 | coraza_vacia (5,0 s simulado, 2,9 s estimado) |
| mazo | 3 | ×0,42 | ×0,25 – ×0,97 | guardian_de_cuno (2,5 s simulado, 10,2 s estimado) |
| tridente | 4 | ×0,96 | ×0,37 – ×2,09 | automata_de_forja (2,6 s simulado, 7,0 s estimado) |
| mangual | 4 | ×0,97 | ×0,30 – ×2,25 | guardian_de_cuno (2,5 s simulado, 8,4 s estimado) |
| guanteletes | 2 | ×1,31 | ×1,02 – ×1,31 | coraza_vacia (2,4 s simulado, 1,8 s estimado) |
| guadana | 6 | ×0,79 | ×0,62 – ×1,60 | coraza_vacia (4,5 s simulado, 2,8 s estimado) |
| baculo | 2 | ×1,73 | ×0,09 – ×1,73 | guardian_de_cuno (2,8 s simulado, 32,9 s estimado) |
| grimorio | 4 | ×0,22 | ×0,12 – ×0,49 | percutor (2,4 s simulado, 19,8 s estimado) |

## Dificultad: factores sobre HERRERO

Cada preset multiplica la vida de los monstruos, el tope por golpe y la barra de postura (`ForjaDifficulty`). Aquí, el TTK medio de la mejor arma al 100 % contra **todos** los mobs bajo cada preset, dividido por el de HERRERO.

| Tipo | TTK medio HERRERO (s) | APRENDIZ (vida ×0,8, tope ×1,40) | HERRERO (vida ×1,0, tope ×1,00) | MAESTRO (vida ×1,3, tope ×0,85) | LEYENDA (vida ×1,7, tope ×0,70) |
|---|---|---|---|---|---|
| espada | 0,57 | ×0,54 | ×1,00 | ×1,21 | ×1,46 |
| daga | 0,44 | ×0,63 | ×1,00 | ×1,21 | ×1,67 |
| espadon | 0,65 | ×0,51 | ×1,00 | ×1,23 | ×1,38 |
| hacha | 0,61 | ×0,54 | ×1,00 | ×1,20 | ×1,40 |
| lanza | 0,85 | ×0,69 | ×1,00 | ×1,21 | ×1,67 |
| mazo | 0,61 | ×0,64 | ×1,00 | ×1,38 | ×1,60 |
| tridente | 0,68 | ×0,59 | ×1,00 | ×1,22 | ×1,50 |
| mangual | 0,57 | ×0,58 | ×1,00 | ×1,35 | ×1,54 |
| guanteletes | 0,24 | ×0,68 | ×1,00 | ×1,31 | ×1,75 |
| guadana | 0,67 | ×0,53 | ×1,00 | ×1,19 | ×1,38 |
| baculo | 0,69 | ×0,76 | ×1,00 | ×1,21 | ×1,76 |
| grimorio | 1,21 | ×0,64 | ×1,00 | ×1,22 | ×1,45 |

## Materiales

### Dominados por hueco (Pareto, con el mismo rasgo)

Un material dominado es igual o peor en todo lo que ese hueco usa que otro con el mismo rasgo: en ese hueco, nunca hay razón para elegirlo (fuera de coste y disponibilidad, que aquí no se miden).

| Pieza | Sobreviven | Dominados (→ por cuál) |
|---|---|---|
| hoja | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| mango | oro, amatista, obsidiana, netherita, esmeralda, prismarina, vara_de_blaze, cuarzo, purpur, eco, resina, corazon, peltre, electro, damasco, acero_estelar, obsidiacero, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo | madera → hueso, piedra → madera, hueso → peltre, cuero → hueso, cobre → hueso, hierro → netherita, diamante → netherita, obsidiana_llorona → corazon, bronce → hueso, laton → amatista, acero → diamante, estelar → acero_estelar, escoria → vara_de_blaze |
| guarda | netherita, esmeralda, prismarina, vara_de_blaze, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo | madera → piedra, piedra → hueso, hueso → cobre, cuero → piedra, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar, escoria → vara_de_blaze |
| cabeza_hacha | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| atadura | netherita, esmeralda, prismarina, vara_de_blaze, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo | madera → piedra, piedra → hueso, hueso → cobre, cuero → piedra, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar, escoria → vara_de_blaze |
| punta_lanza | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| cabeza_mazo | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| punta_tridente | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| bola | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| cadena | netherita, esmeralda, prismarina, vara_de_blaze, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo | madera → piedra, piedra → hueso, hueso → cobre, cuero → piedra, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar, escoria → vara_de_blaze |
| manopla | oro, amatista, obsidiana, netherita, esmeralda, prismarina, vara_de_blaze, cuarzo, purpur, eco, resina, corazon, peltre, electro, damasco, acero_estelar, obsidiacero, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo | madera → hueso, piedra → madera, hueso → peltre, cuero → hueso, cobre → hueso, hierro → netherita, diamante → netherita, obsidiana_llorona → corazon, bronce → hueso, laton → amatista, acero → diamante, estelar → acero_estelar, escoria → vara_de_blaze |
| nudillos | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| remache | netherita, esmeralda, prismarina, vara_de_blaze, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo | madera → piedra, piedra → hueso, hueso → cobre, cuero → piedra, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar, escoria → vara_de_blaze |
| nucleo | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| engaste | netherita, esmeralda, prismarina, vara_de_blaze, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo | madera → piedra, piedra → hueso, hueso → cobre, cuero → piedra, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar, escoria → vara_de_blaze |
| tapas | oro, amatista, obsidiana, netherita, esmeralda, prismarina, vara_de_blaze, cuarzo, purpur, eco, resina, corazon, peltre, electro, damasco, acero_estelar, obsidiacero, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo | madera → hueso, piedra → madera, hueso → peltre, cuero → hueso, cobre → hueso, hierro → netherita, diamante → netherita, obsidiana_llorona → corazon, bronce → hueso, laton → amatista, acero → diamante, estelar → acero_estelar, escoria → vara_de_blaze |

**Dominados en todos los huecos de arma en que caben** (para armas no hay razón para usarlos): madera, piedra, hueso, cuero, cobre, hierro, diamante, obsidiana_llorona, bronce, laton, acero, estelar.

**Nunca en ninguna de las 5 mejores combinaciones de ningún tipo de arma, ni en ninguna mejor arma:** madera, piedra, hueso, cuero, cobre, hierro, oro, amatista, diamante, obsidiana, netherita, esmeralda, purpur, obsidiana_llorona, resina, bronce, laton, peltre, acero, electro, obsidiacero, estelar, hueco, escama, cinerio, voltaico, almacero, lunacero, acero_vivo, escoria. (Para armas; muchos son materiales de armadura, herramienta o principio de partida.)

### Un material de más nivel peor en todo que uno de menos

Nivel = lo que puede minar (`incorrectBlocksForDrops`): madera y oro 0, piedra 1, cobre 1,5, hierro 2, diamante 3, netherita 4. Se compara cada pieza en todas las líneas de su ficha (`ForgeStats.partLines`).

**Fallos claros** (los dos materiales sin rasgo y de los que pueden ser cabeza, en una cabeza o una placa, que es donde el nivel importa, y con dos líneas de ficha o más; esto es lo que vigila la prueba `equilibrioSinDominados`): ninguno.

**En piezas de arma, contando rasgos y mangos** (el rasgo puede ser el precio y un mango es cosa de peso; no son fallos por sí solos, pero merecen una mirada): 

- cobre (1,5) peor que cuarzo (1,0): cabeza_hacha, hoja, punta_lanza, cabeza_mazo
- cobre (1,5) peor que resina (1,0): hoja, punta_lanza, cabeza_mazo
- voltaico (3,0) peor que esmeralda (2,0): hoja, punta_lanza, cabeza_mazo
- piedra (1,0) peor que madera (0,0): mango

## Mejoras de daño

### Lo que vale cada una sola, al 100 %, sobre la mejor combinación de materiales

Cambio del TTK medio al añadirla sola (negativo es mejor), y entre paréntesis por punto de carga. "·" = no se puede poner en ese tipo.

| Mejora (peso) | espada | daga | espadon | hacha | lanza | mazo | tridente | mangual | guanteletes | guadana | baculo | grimorio |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| filo (4) | −16 % (−4 %) | −37 % (−9 %) | −6 % (−2 %) | −11 % (−3 %) | −32 % (−8 %) | −25 % (−6 %) | −20 % (−5 %) | −11 % (−3 %) | −33 % (−8 %) | −14 % (−3 %) | −23 % (−6 %) | −22 % (−5 %) |
| castigo (2) | +0 % (+0 %) | −16 % (−8 %) | +0 % (+0 %) | +0 % (+0 %) | −20 % (−10 %) | −6 % (−3 %) | −8 % (−4 %) | +0 % (+0 %) | −8 % (−4 %) | +0 % (+0 %) | −8 % (−4 %) | −10 % (−5 %) |
| perdicion_de_artropodos (1) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | −9 % (−9 %) |
| brecha (2) | −9 % (−4 %) | −11 % (−5 %) | −6 % (−3 %) | −6 % (−3 %) | −9 % (−4 %) | −8 % (−4 %) | −8 % (−4 %) | −6 % (−3 %) | −8 % (−4 %) | −8 % (−4 %) | −3 % (−2 %) | −7 % (−4 %) |
| densidad (3) | · | · | · | · | · | +0 % (+0 %) | · | · | · | · | · | · |
| aspecto_igneo (2) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) |
| critico (3) | −7 % (−2 %) | −16 % (−5 %) | −5 % (−2 %) | −6 % (−2 %) | −5 % (−2 %) | −8 % (−3 %) | −6 % (−2 %) | −6 % (−2 %) | −8 % (−3 %) | −12 % (−4 %) | −1 % (−0 %) | −5 % (−2 %) |
| frenesi (3) | −8 % (−3 %) | −16 % (−5 %) | −7 % (−2 %) | −12 % (−4 %) | −14 % (−5 %) | −16 % (−5 %) | −10 % (−3 %) | −10 % (−3 %) | −15 % (−5 %) | −13 % (−4 %) | +0 % (+0 %) | +0 % (+0 %) |
| tormenta (3) | −10 % (−3 %) | −18 % (−6 %) | −3 % (−1 %) | −3 % (−1 %) | −11 % (−4 %) | −3 % (−1 %) | −5 % (−2 %) | −1 % (−0 %) | −13 % (−4 %) | −7 % (−2 %) | −10 % (−3 %) | −7 % (−2 %) |
| ejecucion (2) | −11 % (−5 %) | −10 % (−5 %) | −7 % (−3 %) | −8 % (−4 %) | −11 % (−5 %) | −24 % (−12 %) | −12 % (−6 %) | −11 % (−6 %) | −6 % (−3 %) | −9 % (−5 %) | +0 % (+0 %) | −8 % (−4 %) |
| matagigantes (2) | −18 % (−9 %) | −26 % (−13 %) | −15 % (−8 %) | −18 % (−9 %) | −26 % (−13 %) | −31 % (−16 %) | −18 % (−9 %) | −19 % (−9 %) | −30 % (−15 %) | −18 % (−9 %) | −7 % (−4 %) | −10 % (−5 %) |
| veneno (2) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | −0 % (−0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | −8 % (−4 %) | +0 % (+0 %) |
| desgarro (3) | · | +0 % (+0 %) | · | · | · | · | · | · | · | +0 % (+0 %) | · | · |
| nudillos_de_hierro (3) | · | · | · | · | · | · | · | · | −14 % (−5 %) | · | · | · |
| rafaga (2) | · | · | · | · | · | · | · | · | −21 % (−10 %) | · | · | · |
| conjuro_veloz (3) | · | · | · | · | · | · | · | · | · | · | −18 % (−6 %) | +3 % (+1 %) |
| sobrecarga (3) | · | · | · | · | · | · | · | · | · | · | −5 % (−2 %) | −9 % (−3 %) |
| resonancia (4) | · | · | · | · | · | · | · | · | · | · | −45 % (−11 %) | −25 % (−6 %) |

### Programación dinámica contra todos los conjuntos peleados

La programación dinámica es exacta para valores que se suman; en una pelea no se suman (el tope, la invulnerabilidad y el ablandado de los extras cortan), así que se pelean todos los conjuntos legales y se queda el mejor.

| Tipo | Escenario | Carga | Conjuntos | Elige la PD | TTK (s) | Mejor peleado | TTK (s) | Diferencia |
|---|---|---|---|---|---|---|---|---|
| espada | 50 % | 7 | 144 | tormenta, ejecucion, matagigantes | 1,33 | frenesi, ejecucion, matagigantes | 1,23 | +8 % |
| espada | 100 % | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,77 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,77 | +0 % |
| espada | 50 % con pactos | 12 | 470 | critico, frenesi, ejecucion, matagigantes, brecha | 0,77 | frenesi, ejecucion, matagigantes, filo | 0,74 | +4 % |
| espada | 100 % con pactos | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,61 | tormenta, critico, frenesi, ejecucion, matagigantes, brecha | 0,61 | +0 % |
| daga | 50 % | 7 | 177 | critico, matagigantes, castigo | 1,16 | critico, matagigantes, castigo | 1,16 | +0 % |
| daga | 100 % | 20 | 1268 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,61 | tormenta, critico, frenesi, ejecucion, matagigantes, filo, desgarro | 0,60 | +1 % |
| daga | 50 % con pactos | 12 | 739 | tormenta, critico, matagigantes, filo | 0,68 | frenesi, ejecucion, matagigantes, filo | 0,65 | +5 % |
| daga | 100 % con pactos | 20 | 1268 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,48 | tormenta, critico, frenesi, ejecucion, matagigantes, filo, aspecto_igneo | 0,47 | +1 % |
| espadon | 50 % | 7 | 144 | critico, ejecucion, matagigantes | 1,26 | frenesi, matagigantes, brecha | 1,21 | +4 % |
| espadon | 100 % | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,82 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,82 | +0 % |
| espadon | 50 % con pactos | 12 | 470 | critico, frenesi, ejecucion, matagigantes, brecha | 0,79 | critico, frenesi, ejecucion, matagigantes, brecha | 0,79 | +0 % |
| espadon | 100 % con pactos | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,66 | tormenta, critico, ejecucion, matagigantes, brecha | 0,65 | +1 % |
| hacha | 50 % | 7 | 144 | frenesi, ejecucion, matagigantes | 1,24 | critico, matagigantes, brecha | 1,20 | +3 % |
| hacha | 100 % | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,80 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,80 | +0 % |
| hacha | 50 % con pactos | 12 | 470 | critico, frenesi, ejecucion, matagigantes, brecha | 0,77 | critico, frenesi, ejecucion, matagigantes, brecha | 0,77 | +0 % |
| hacha | 100 % con pactos | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,66 | tormenta, critico, frenesi, ejecucion, matagigantes, brecha | 0,66 | +0 % |
| lanza | 50 % | 7 | 144 | frenesi, matagigantes, castigo | 1,97 | matagigantes, filo | 1,83 | +7 % |
| lanza | 100 % | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 1,17 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 1,17 | +0 % |
| lanza | 50 % con pactos | 12 | 470 | frenesi, ejecucion, matagigantes, filo | 1,13 | frenesi, ejecucion, matagigantes, filo | 1,13 | +0 % |
| lanza | 100 % con pactos | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,86 | tormenta, critico, frenesi, ejecucion, matagigantes, filo, aspecto_igneo | 0,86 | +1 % |
| mazo | 50 % | 7 | 158 | frenesi, ejecucion, matagigantes | 1,07 | matagigantes, filo | 1,02 | +5 % |
| mazo | 100 % | 20 | 767 | tormenta, veneno, critico, frenesi, ejecucion, matagigantes, filo | 0,71 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,71 | +0 % |
| mazo | 50 % con pactos | 12 | 547 | critico, frenesi, ejecucion, matagigantes | 0,75 | critico, ejecucion, matagigantes, filo | 0,74 | +2 % |
| mazo | 100 % con pactos | 20 | 767 | critico, frenesi, ejecucion, matagigantes, filo | 0,59 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,54 | +8 % |
| tridente | 50 % | 7 | 144 | ejecucion, matagigantes, castigo | 1,29 | frenesi, matagigantes, castigo | 1,26 | +2 % |
| tridente | 100 % | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,82 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,82 | +0 % |
| tridente | 50 % con pactos | 12 | 470 | critico, frenesi, ejecucion, matagigantes, brecha | 0,81 | frenesi, ejecucion, matagigantes, filo | 0,76 | +6 % |
| tridente | 100 % con pactos | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,63 | critico, frenesi, ejecucion, matagigantes, brecha, aspecto_igneo | 0,62 | +2 % |
| mangual | 50 % | 7 | 144 | frenesi, ejecucion, matagigantes | 0,94 | critico, ejecucion, matagigantes | 0,89 | +5 % |
| mangual | 100 % | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,66 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,66 | +0 % |
| mangual | 50 % con pactos | 12 | 470 | critico, frenesi, ejecucion, matagigantes, brecha | 0,59 | critico, frenesi, ejecucion, matagigantes, brecha | 0,59 | +0 % |
| mangual | 100 % con pactos | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,53 | tormenta, critico, frenesi, ejecucion, matagigantes, brecha | 0,52 | +1 % |
| guanteletes | 50 % | 7 | 242 | matagigantes, rafaga, nudillos_de_hierro | 0,75 | matagigantes, castigo, rafaga | 0,73 | +3 % |
| guanteletes | 100 % | 20 | 2493 | tormenta, critico, frenesi, matagigantes, filo, rafaga, nudillos_de_hierro | 0,26 | tormenta, frenesi, ejecucion, matagigantes, filo, rafaga, nudillos_de_hierro | 0,26 | +0 % |
| guanteletes | 50 % con pactos | 12 | 1216 | critico, ejecucion, matagigantes, rafaga, nudillos_de_hierro | 0,40 | tormenta, ejecucion, brecha, rafaga, nudillos_de_hierro | 0,36 | +11 % |
| guanteletes | 100 % con pactos | 20 | 2493 | critico, frenesi, ejecucion, matagigantes, filo, rafaga, nudillos_de_hierro | 0,21 | critico, frenesi, ejecucion, matagigantes, filo, rafaga, nudillos_de_hierro | 0,21 | +0 % |
| guadana | 50 % | 7 | 177 | critico, ejecucion, matagigantes | 1,25 | critico, matagigantes, brecha | 1,23 | +1 % |
| guadana | 100 % | 20 | 1268 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,89 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,89 | +0 % |
| guadana | 50 % con pactos | 12 | 739 | critico, ejecucion, matagigantes, filo | 0,86 | critico, frenesi, matagigantes, filo | 0,82 | +5 % |
| guadana | 100 % con pactos | 20 | 1268 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,65 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,65 | +0 % |
| baculo | 50 % | 7 | 229 | conjuro_veloz, resonancia | 1,28 | castigo, resonancia | 1,25 | +2 % |
| baculo | 100 % | 20 | 4440 | tormenta, veneno, matagigantes, filo, conjuro_veloz, resonancia | 0,72 | tormenta, matagigantes, filo, conjuro_veloz, sobrecarga, resonancia | 0,71 | +2 % |
| baculo | 50 % con pactos | 12 | 1417 | veneno, castigo, conjuro_veloz, resonancia | 1,07 | tormenta, castigo, conjuro_veloz, resonancia | 0,99 | +8 % |
| baculo | 100 % con pactos | 20 | 4440 | tormenta, veneno, matagigantes, filo, conjuro_veloz, resonancia | 0,72 | tormenta, matagigantes, filo, conjuro_veloz, sobrecarga, resonancia | 0,71 | +2 % |
| grimorio | 50 % | 7 | 229 | matagigantes, perdicion_de_artropodos, resonancia | 1,85 | castigo, resonancia | 1,80 | +3 % |
| grimorio | 100 % | 20 | 4440 | tormenta, ejecucion, matagigantes, filo, sobrecarga, resonancia | 1,29 | tormenta, matagigantes, filo, conjuro_veloz, resonancia | 1,23 | +4 % |
| grimorio | 50 % con pactos | 12 | 1417 | ejecucion, matagigantes, perdicion_de_artropodos, sobrecarga, resonancia | 1,63 | ejecucion, matagigantes, filo, resonancia | 1,46 | +12 % |
| grimorio | 100 % con pactos | 20 | 4440 | tormenta, ejecucion, matagigantes, filo, sobrecarga, resonancia | 1,29 | tormenta, matagigantes, filo, conjuro_veloz, resonancia | 1,23 | +4 % |

La PD acierta (a menos de un 0,5 %) en 18 de 48 casos.

**Veces que entra en una mejor arma** (48 armas con mejoras): filo ×28, castigo ×6, brecha ×12, aspecto_igneo ×3, critico ×28, frenesi ×30, tormenta ×24, ejecucion ×32, matagigantes ×44, desgarro ×1, nudillos_de_hierro ×3, rafaga ×4, conjuro_veloz ×5, sobrecarga ×2, resonancia ×8.

**Mejoras de daño que no entran en ninguna mejor arma:** perdicion_de_artropodos, densidad, veneno. Contra un solo mob quieto nunca compensan lo que pesan. Densidad sólo pega cayendo; Sobrecarga es un hechizo de cada 4; el daño en el tiempo (Veneno, Aspecto ígneo) apenas llega antes de que el mob muera y la invulnerabilidad se traga sus puntos sueltos.

## Los mobs, medidos

Vida y tope por golpe en HERRERO, sin veteranos ni élites (un veterano es ×1,5 de vida y tope 35 %; un élite ×2,5, tope 20 % y guardia). Factor: vida que pierde por punto de golpe, de pie / aturdido (espada, mazo, lanza; sin Brecha).

| Mob | Vida | Tope por golpe | Barra de postura | Espada | Mazo | Lanza | Báculo | Notas |
|---|---|---|---|---|---|---|---|---|
| zombie | 20 | 9,0 | 17 | 0,92 / 1,15 | 0,93 / 1,16 | 0,93 / 1,17 | 1,00 |  |
| husk | 20 | 9,0 | 17 | 0,92 / 1,15 | 0,93 / 1,16 | 0,93 / 1,17 | 1,00 |  |
| drowned | 20 | 9,0 | 17 | 0,92 / 1,15 | 0,93 / 1,16 | 0,93 / 1,17 | 1,00 |  |
| zombie_villager | 20 | 9,0 | 17 | 0,92 / 1,15 | 0,93 / 1,16 | 0,93 / 1,17 | 1,00 |  |
| skeleton | 20 | 9,0 | 17 | 1,00 / 1,25 | 1,30 / 1,63 | 0,70 / 0,88 | 1,30 |  |
| stray | 20 | 9,0 | 17 | 1,00 / 1,25 | 1,30 / 1,63 | 0,70 / 0,88 | 1,30 |  |
| bogged | 16 | 7,2 | 15 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 |  |
| wither_skeleton | 20 | 9,0 | 17 | 1,00 / 1,25 | 1,25 / 1,56 | 0,75 / 0,94 | 1,25 |  |
| creeper | 20 | 9,0 | 17 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 |  |
| spider | 16 | 7,2 | 15 | 1,49 / 1,87 | 1,30 / 1,63 | 1,17 / 1,46 | 1,00 |  |
| cave_spider | 12 | 5,4 | 12 | 1,30 / 1,63 | 1,30 / 1,63 | 1,30 / 1,63 | 1,00 |  |
| pillager | 24 | 10,8 | 19 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 |  |
| vindicator | 24 | 10,8 | 19 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 |  |
| evoker | 24 | 10,8 | 19 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 |  |
| witch | 26 | 11,7 | 21 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 / 1,25 | 0,15 |  |
| enderman | 40 | 18,0 | 29 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 | se teletransporta ante proyectiles; esquiva el 34 % de los golpes teletransportándose (7 s sin esquivar tras hacerlo), no modelado |
| blaze | 20 | 9,0 | 17 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 |  |
| piglin_brute | 50 | 22,5 | 35 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 |  |
| herrero_caido *(Forja)* | 400 | 32,0 | 245 | 0,30 / 0,75 | 0,31 / 0,78 | 0,32 / 0,79 | 0,50 | jefe: guardia al 50 %, tope por golpe del 8 %, postura sólo tras sus golpes pesados, fases de reforja invulnerables; aquí sin vestir y con su vida de base: la pelea de verdad está en «Herrero Caído» |
| automata_de_forja *(Forja)* | 70 | 31,5 | 85 | 0,52 / 0,65 | 0,76 / 0,95 | 0,49 / 0,62 | 1,10 |  |
| coraza_vacia *(Forja)* | 45 | 20,3 | 32 | 0,48 / 0,60 | 0,95 / 1,18 | 0,67 / 0,83 | 0,45 | se hace el muerto una vez al 30 % (3 s invulnerable, modelado); lo no forjado le hace un 35 % |
| pavesa *(Forja)* | 12 | 5,4 | 12 | 1,19 / 1,49 | 1,21 / 1,51 | 1,21 / 1,52 | 1,00 |  |
| herrumbre *(Forja)* | 8 | 3,6 | 10 | 1,04 / 1,30 | 1,63 / 2,03 | 0,52 / 0,65 | 1,25 |  |
| ascua_mayor *(Forja)* | 40 | 18,0 | 29 | 1,10 / 1,38 | 1,13 / 1,41 | 1,14 / 1,43 | 1,00 |  |
| escoria_viviente *(Forja)* | 27 | 12,2 | 21 | 1,56 / 1,95 | 0,65 / 0,81 | 1,30 / 1,63 | 0,50 | los cortes de 3 o más lo parten en trozos que hay que matar aparte |
| yunque_andante *(Forja)* | 60 | 27,0 | 82 | 0,45 / 0,56 | 1,09 / 1,36 | 0,67 / 0,84 | 1,15 |  |
| percutor *(Forja)* | 80 | 36,0 | 53 | 0,66 / 0,83 | 0,62 / 0,77 | 0,86 / 1,07 | 0,80 |  |
| tenaza *(Forja)* | 34 | 15,3 | 25 | 0,85 / 1,06 | 0,87 / 1,09 | 0,88 / 1,10 | 1,00 |  |
| cargador_de_carbon *(Forja)* | 46 | 20,7 | 33 | 0,99 / 1,24 | 1,03 / 1,29 | 1,04 / 1,31 | 1,00 |  |
| templador *(Forja)* | 26 | 11,7 | 21 | 0,92 / 1,15 | 0,93 / 1,16 | 0,93 / 1,17 | 1,00 |  |
| nucleo_estelar *(Forja)* | 24 | 10,8 | 19 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 / 1,25 | 1,00 | fuera de las tablas: absorbe los primeros 24 de daño y los devuelve; se pelea por fases |
| molde_roto *(Forja)* | 54 | 24,3 | 37 | 0,79 / 0,98 | 0,82 / 1,02 | 0,83 / 1,03 | 1,00 | copia el arma con que le pegan |
| guardian_de_cuno *(Forja)* | 140 | 63,0 | 134 | 0,49 / 0,61 | 0,76 / 0,95 | 0,56 / 0,70 | 1,10 | inmune mientras le queden sellos en su sala |

## Valores atípicos y el porqué

1. **guanteletes es la más rápida al 100 % (0,26 s).** Porqué: 6,0 de daño a 4,06 golpes/s; ritmo cada 4 ticks; 97 % de los golpes cansado; 1,0 aturdidos por pelea; Afilado +3 en cada golpe.
2. **grimorio es la más lenta al 100 % (1,24 s).** Porqué: 0,3 aturdidos por pelea; Afilado +3 en cada golpe; hechizo de 9,2 cada 16 ticks mientras dura el maná, sin estamina y atravesando armadura.
3. **La mejora que más rinde por punto de carga: matagigantes en mazo** (16 % menos de TTK por punto). Porqué: el frenesí la multiplica por su techo (×2,5 las de un solo ingrediente) en cuanto se encadenan 5 golpes, y pesa poco.
4. **Contra automata_de_forja *(Forja)*, guanteletes mata en 0,8 s y la mediana de los tipos en 2,3 s.** Porqué: cada punto de su golpe le quita 0,76; el de las armas cuerpo a cuerpo, 0,49 a 0,76; 6,0 de daño a 4,06 golpes/s; ritmo cada 4 ticks; 97 % de los golpes cansado; 1,0 aturdidos por pelea; Afilado +3 en cada golpe.
5. **Contra coraza_vacia *(Forja)*, mazo mata en 1,8 s y la mediana de los tipos en 4,5 s.** Porqué: cada punto de su golpe le quita 0,95; el de las armas cuerpo a cuerpo, 0,48 a 0,95; 8,0 de daño a 1,62 golpes/s; ritmo cada 11 ticks; 93 % de los golpes cansado; 1,1 aturdidos por pelea; Afilado +3 en cada golpe.
6. **Contra guardian_de_cuno *(Forja)*, guanteletes mata en 1,8 s y la mediana de los tipos en 5,8 s.** Porqué: cada punto de su golpe le quita 0,76; el de las armas cuerpo a cuerpo, 0,49 a 0,76; 6,0 de daño a 4,06 golpes/s; ritmo cada 4 ticks; 97 % de los golpes cansado; 1,0 aturdidos por pelea; Afilado +3 en cada golpe.
7. **Los pactos en guanteletes al 50 %: TTK −51 %.** Porqué: +32 % y +40 % de daño al atributo, peso 0, sin techo, y +20 de potencial que sube el techo de las demás de 50 a 70 % y la carga de 7 a 12. Lo que cuestan (hambre, durabilidad) no es daño.

## Recomendaciones

Sólo propuestas: ningún número se ha tocado. Cada una sale de una medida de arriba; decide Andy.

1. **Estamina**: golpear sin estamina sigue saliendo a cuenta (×0,6). Si la estamina tiene que marcar el ritmo, bajar `tiredDamageMultiplier` (0,3–0,4) o no dejar atacar a 0, o que la estamina vuelva también mientras se ataca despacio.
2. **Invulnerabilidad y extras**: hoy cada extra de mejora deja el "último daño" en el suyo, pequeño, y el siguiente golpe rápido entra casi entero; sin extras, por encima de 2 golpes/s el golpe sólo quita lo que supera al anterior. O se acepta y se documenta, o `extraDamage` guarda y restaura `invulnerableTime` y `lastHurt` del objetivo.
3. **Afilado**: que cuente sólo en la cabeza o la hoja (no en atadura ni guarda), o que sea un porcentaje del golpe en vez de +3 plano, que pesa mucho más en armas rápidas.
4. **Vidriacero**: su +0,3 de velocidad por Diáfano se suma a un mango que ya es el más rápido; que Diáfano sólo quite peso en armadura, o bajar su `handleAttackSpeed`.
5. **Mestizaje**: o se aplica en `Assembler.write` (y entonces se nota en el daño) o se quita de la ficha; hoy promete un número que el arma no tiene.
6. **Frenesí**: que sea un porcentaje de la velocidad del arma (p. ej. +38 %, lo que hoy es para una espada) en vez de +0,6 plano, que dobla el mazo.
7. **Pactos**: que pesen (p. ej. 2) o que no sumen potencial; hoy dan daño, techo y carga a la vez, y su coste no es de combate.
8. **Tope y extras**: los extras son el 41 % del daño y cada uno lleva su propio tope. Si el tope ha de decir "nada muere de un golpe", que los extras de un golpe cuenten contra el mismo tope (sumarlos en `CombatUpgrades` antes de `capped`).
9. **Techo del frenesí**: ×2,5 para las de un ingrediente convierte Matagigantes y Ejecución en el centro de todo; bajar el techo de las de daño (p. ej. ×1,5) o dejar el ×2,5 para las de utilidad.


<!-- escrito a mano: desde aquí esta página no la escribe BalanceGameTests, y al regenerarla se conserva tal cual -->

## Armaduras frente a diamante y netherita con Protección IV (2026-09-29)

Andy: "el diamante con Protección IV, y más aún las armaduras del mod, hacen que las multitudes no hagan daño".
Aquí se mide cuánto para cada armadura forjada comparada con el diamante y la netherita vanilla, las dos con
Protección IV en las cuatro piezas.

**Cómo se mide.** Un golpe de monstruo en el torso (a la altura de los brazos del zombi), de 6 y de 10 de daño,
sin penetración: el puño de un zombi (contundente) y el hacha de un vindicador (cortante). El golpe pasa primero por
la armadura del mod (`ArmorCalculator`, la fórmula armadura / (armadura + 20)) y después por la protección de los
encantamientos de vanilla, que el mod no toca: Protección IV en cuatro piezas son 16 puntos y quitan el 64 % de lo
que queda. Cada conjunto forjado lleva su placa en las cuatro piezas, forro de cuero (el que mejor amortigua un golpe
contundente; el forro no cambia nada contra un filo) y todo lo que dan las mejoras de la forja: Protección al 100 %
(que es Protección IV) en cada pieza, Vitalidad en la pechera (con Protección despierta Fortaleza, +1 de armadura) y
el bono de conjunto (+2 de armadura y el del material). Sin desgaste. La dureza no cuenta aquí porque solo frena la
penetración, y estos golpes no tienen.

**Estos números los he calculado a mano** (un guion de Python que copia `ArmorMath`, `MaterialCombat`,
`ArmorCalculator`, `ForgeStats` y `ArmorSets`). Los que valen son los de la prueba `ArmaduraGameTests`, que hace lo
mismo en un servidor de verdad y deja la tabla en el registro de CI, en las líneas que empiezan por
`[forja-test] armadura:`.

La reducción es la misma con 6 que con 10 de daño: la fórmula del mod es proporcional al golpe. Por eso la tabla
grande da un solo porcentaje por columna; el daño recibido de 6 y de 10 está en la primera tabla y en la de los
cambios.

### Las referencias

| Conjunto | Contundente (zombi) | Daño recibido de 6 / 10 | Cortante (vindicador) | Daño recibido de 6 / 10 |
|---|---|---|---|---|
| diamante vanilla, Protección IV | 81,1 % | 1,14 / 1,89 | 84,3 % | 0,94 / 1,57 |
| netherita vanilla, Protección IV | 82,9 % | 1,03 / 1,71 | 84,3 % | 0,94 / 1,57 |

### Cada conjunto forjado, a tope de mejoras

Porcentaje del golpe que se queda en la armadura y la protección (más es mejor para quien la lleva). Los marcados con
\* son los que han cambiado: antes → **después**. Los demás no cambian. Las dos últimas columnas son puntos de
reducción por encima (+) o por debajo (−) del diamante y de la netherita vanilla con Protección IV.

| Material (placa) | Contundente antes → después | Cortante antes → después | Después − diamante P4 (cont. / cort.) | Después − netherita P4 (cont. / cort.) |
|---|---|---|---|---|
| obsidiacero | 86,7 % | 85,9 % | +5,7 / +1,6 | +3,9 / +1,6 |
| corazon * | 86,9 → **86,0** % | 86,7 → **85,9** % | +5,0 / +1,6 | +3,2 / +1,6 |
| acero_vivo * | 86,9 → **86,0** % | 86,7 → **85,9** % | +5,0 / +1,6 | +3,2 / +1,6 |
| solacero * | 86,6 → **85,6** % | 87,2 → **86,2** % | +4,6 / +1,9 | +2,8 / +1,9 |
| netherita | 85,8 % | 85,7 % | +4,7 / +1,3 | +2,9 / +1,3 |
| obsidiana | 85,8 % | 85,1 % | +4,7 / +0,8 | +2,9 / +0,8 |
| obsidiana_llorona | 85,0 % | 85,7 % | +4,0 / +1,3 | +2,2 / +1,3 |
| damasco | 85,0 % | 85,7 % | +4,0 / +1,3 | +2,2 / +1,3 |
| almacero | 85,0 % | 85,7 % | +4,0 / +1,3 | +2,2 / +1,3 |
| lunacero * | 86,1 → **85,0** % | 86,7 → **85,7** % | +4,0 / +1,3 | +2,2 / +1,3 |
| diamante | 84,2 % | 85,7 % | +3,1 / +1,3 | +1,3 / +1,3 |
| acero_estelar | 83,9 % | 85,2 % | +2,8 / +0,8 | +1,0 / +0,8 |
| vidriacero | 83,5 % | 85,0 % | +2,4 / +0,7 | +0,6 / +0,7 |
| eco | 83,5 % | 84,9 % | +2,4 / +0,5 | +0,6 / +0,5 |
| cinerio | 83,6 % | 84,7 % | +2,5 / +0,3 | +0,7 / +0,3 |
| acero | 83,5 % | 84,8 % | +2,4 / +0,4 | +0,6 / +0,4 |
| escama | 84,1 % | 83,6 % | +3,1 / -0,7 | +1,2 / -0,7 |
| esmeralda | 83,2 % | 83,8 % | +2,2 / -0,6 | +0,4 / -0,6 |
| estelar | 83,2 % | 83,8 % | +2,2 / -0,6 | +0,4 / -0,6 |
| hueco | 83,0 % | 82,4 % | +1,9 / -1,9 | +0,1 / -1,9 |
| amatista | 82,4 % | 82,9 % | +1,4 / -1,5 | -0,4 / -1,5 |
| bronce | 82,6 % | 82,2 % | +1,5 / -2,1 | -0,3 / -2,1 |
| purpur | 82,1 % | 82,4 % | +1,1 / -1,9 | -0,7 / -1,9 |
| prismarina | 82,1 % | 81,8 % | +1,1 / -2,6 | -0,7 / -2,6 |
| voltaico | 81,8 % | 82,0 % | +0,7 / -2,4 | -1,1 / -2,4 |
| hierro | 82,1 % | 81,1 % | +1,1 / -3,2 | -0,7 / -3,2 |
| peltre | 80,9 % | 79,6 % | -0,2 / -4,8 | -2,0 / -4,8 |
| vara_de_blaze | 80,9 % | 79,4 % | -0,2 / -4,9 | -2,0 / -4,9 |
| cuarzo | 80,9 % | 79,4 % | -0,2 / -4,9 | -2,0 / -4,9 |
| laton | 80,9 % | 79,4 % | -0,2 / -4,9 | -2,0 / -4,9 |
| oro | 80,5 % | 78,9 % | -0,5 / -5,4 | -2,3 / -5,4 |
| resina | 80,5 % | 78,9 % | -0,5 / -5,4 | -2,3 / -5,4 |
| electro | 80,5 % | 78,9 % | -0,5 / -5,4 | -2,3 / -5,4 |
| hueso | 79,5 % | 77,8 % | -1,6 / -6,6 | -3,4 / -6,6 |
| cobre | 79,5 % | 77,8 % | -1,6 / -6,6 | -3,4 / -6,6 |
| piedra | 79,0 % | 77,2 % | -2,0 / -7,2 | -3,8 / -7,2 |
| escoria | 79,0 % | 77,2 % | -2,0 / -7,2 | -3,8 / -7,2 |
| madera | 77,3 % | 75,3 % | -3,8 / -9,0 | -5,6 / -9,0 |
| cuero | 77,3 % | 75,3 % | -3,8 / -9,0 | -5,6 / -9,0 |

Daño recibido (de un golpe de 6 / de 10) con los cuatro materiales que han cambiado:

| Conjunto | Contundente | Cortante |
|---|---|---|
| corazón de forja (antes) | 0,79 / 1,31 | 0,80 / 1,33 |
| corazón de forja (después) | 0,84 / 1,40 | 0,84 / 1,41 |
| acero vivo (antes) | 0,79 / 1,31 | 0,80 / 1,33 |
| acero vivo (después) | 0,84 / 1,40 | 0,84 / 1,41 |
| solacero (antes) | 0,80 / 1,34 | 0,77 / 1,28 |
| solacero (después) | 0,86 / 1,44 | 0,83 / 1,38 |
| lunacero (antes) | 0,83 / 1,39 | 0,80 / 1,33 |
| lunacero (después) | 0,90 / 1,50 | 0,86 / 1,43 |

### Lo que dice la tabla

- **Protección IV lo aprieta todo.** Después de la armadura, Protección IV deja pasar solo el 36 %. Por eso todos los
  conjuntos, del cuero al corazón de forja, quedan entre el 75 y el 87 %, cuando el diamante vanilla sin ella para el
  47 % de un puño y el 57 % de un filo. Lo que hace inofensiva a una multitud es sobre todo Protección IV, y eso
  vale igual para el diamante vanilla que para lo forjado. Esta revisión no lo toca (ver *Lo que queda*).
- **A igual armadura, lo forjado para algo más que vanilla**: la netherita forjada, con los mismos números que la de
  vanilla, para 2,9 puntos más contra un puño y 1,3 más contra un filo. Viene del forro de cuero (amortigua los golpes
  contundentes un 20 % más que una pieza vanilla de diamante o netherita), del +2 de armadura del conjunto y de la
  Fortaleza de la pechera. Es lo que se quería que diera la forja; no se ha cambiado.
- **Antes, cuatro materiales se salían**: el corazón de forja y el acero vivo tenían 24 de armadura en el conjunto, y
  el solacero y el lunacero 23, contra 20 de la netherita. Eran los que más se pasaban de la netherita con Protección IV:
  hasta +4,4 puntos (corazón, golpe a la cabeza) a tope de mejoras.
- **Los materiales normales están donde deben**: el diamante forjado queda a +3,1 / +1,3 del diamante vanilla con
  Protección IV; el hierro, el bronce y la amatista rondan el diamante con Protección IV; el cobre, la piedra y el cuero
  quedan por debajo.

### Lo que ha cambiado

En `material/ForgeMaterial.java`, la armadura por pieza (casco, pechera, grebas, botas):

| Material | Antes | Después | Conjunto |
|---|---|---|---|
| corazón de forja | 4 / 9 / 7 / 4 | 3 / 8 / 7 / 3 | 24 → 21 |
| acero vivo | 4 / 9 / 7 / 4 | 3 / 8 / 7 / 3 | 24 → 21 |
| solacero | 4 / 9 / 7 / 3 | 3 / 8 / 6 / 3 | 23 → 20 |
| lunacero | 4 / 9 / 7 / 3 | 3 / 8 / 6 / 3 | 23 → 20 |

El corazón y el acero vivo quedan un punto por encima de la netherita (en las grebas); el solacero y el lunacero,
iguales. Los cuatro conservan su dureza (3 a 3,5), que es lo que les queda por encima de la netherita: solo cuenta
contra golpes que penetran (hachas, flechas, la presión de una multitud), no contra el puño de un zombi. Tampoco
cambia lo que pesan: un conjunto de 20 o más ya pesa lo máximo, así que el peso de sus armas y armaduras es el mismo.
Nada más ha cambiado: ni las mejoras, ni el bono de conjunto, ni la fórmula.

**Resultado**: a tope de mejoras, el conjunto que más se pasa de la netherita con Protección IV, mirando los cuatro
sitios del cuerpo y los dos tipos de golpe, pasa de +4,4 puntos (corazón, a la cabeza, contundente) a +4,0
(obsidiacero, a las piernas, contundente). En el torso: el corazón pasa de +4,0 / +2,4 a +3,2 / +1,6. La prueba
exige que ninguno pase de +5.

### Lo que queda

- **El máximo absoluto**: si además cada pieza tiene el don Baluarte (+1 de armadura), salió de una forja perfecta y es
  obra maestra (+8 % a todo), el obsidiacero llega a +5,9 puntos sobre la netherita con Protección IV (a las piernas,
  contundente; ya estaba ahí antes) y el corazón a +5,2 (antes +5,7; en el torso, 87,8 %). La prueba lo imprime pero no lo exige: no son mejoras, cuesta una
  vida entera de uso por pieza, y el obsidiacero es la armadura del Herrero Caído, así que tocarla cambia al jefe.
  Lo que más pesa ahí es el +1 plano de Baluarte en cascos y botas, que con la normalización por pieza cuenta como
  +6,7 en su zona. Si hay que recortarlo, lo limpio es que Baluarte dé dureza en vez de armadura.
- **Protección IV**: si las multitudes deben seguir doliendo con diamante o netherita con Protección IV, el cambio es
  de la protección de los encantamientos, no de las armaduras. Opciones: que la mejora Protección de la forja llegue
  solo a III, o activar `soloMejorasForja` (los encantamientos vanilla dejan de hacer nada en equipo no forjado).
  Decide Andy.
