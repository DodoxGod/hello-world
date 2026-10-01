# Equilibrio de Forja, medido

> Esta página la escribe `./gradlew runGametest` (prueba `BalanceGameTests.equilibrio`, código en `src/gametest/java/dev/forja/test/balance`). No se edita a mano: se regenera sola cada vez que se pasan las pruebas, así que siempre dice lo que hace el código de hoy. Dificultad medida: **HERRERO**. Esta vez: 33 mobs, 12 tipos de arma, 6586884 peleas simuladas.

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

- **Orden al 100 %** (TTK medio en la muestra, menos es mejor): guanteletes 0,24 s < mangual 0,52 s < daga 0,58 s < mazo 0,61 s < espada 0,68 s < hacha 0,71 s < baculo 0,72 s < guadana 0,72 s < espadon 0,74 s < tridente 0,78 s < lanza 1,10 s < grimorio 1,19 s.
- La más rápida mata en 0,24 s de media y la más lenta en 1,19 s: **4,9 veces** más (guanteletes contra grimorio).
- Ningún tipo de arma está dominado en todo: cada uno gana a los demás contra algún mob en algún escenario.
- **Estamina**: en una pelea larga casi todos los golpes son cansados (mediana 93 % de los golpes de las mejores armas cuerpo a cuerpo al 100 %): se golpea sin parar a ×0,6 en vez de esperar.
- **Los extras de las mejoras no tienen el tope de un golpe**: cada uno es un golpe aparte con su propio tope, y son el 39 % del daño de las mejores armas al 100 % (mediana). Un zombi muere en 2,0 golpes, no en 3.
- **Pocos materiales deciden casi todo**: de 60 mejores armas (12 tipos × 0/50/100 % con y sin pactos), los que más aparecen son iracero (49), vidriacero (48), damasco (45), cuarzo (15). Ver *Hallazgos* (Afilado en cualquier pieza, el mango de vidriacero).
- **Pactos**: al 50 % los dos pactos bajan el TTK medio una mediana de −39 % (daño, techo y carga a la vez, peso 0).
- Consulta *Las siete sospechas* para los veredictos, *Hallazgos que no estaban en la lista* y *Valores atípicos* para el porqué.

## Magia frente al cuerpo a cuerpo

Andy, 2026-09-30: la magia estaba rota. Sin clase mágica debe ser un recurso para un momento, no la mejor arma; un Mago con sus talentos, a la altura del cuerpo a cuerpo pero no por encima. *Sin clase*: el maná vuelve a 0,4/s lanzando y 0,8/s en calma. *Mago*: la clase y todos los nodos de su árbol grande menos las claves (docs/ARBOLES.md: lo que tiene un Mago en el nivel 50). El báculo y el grimorio son los mejores de cada escenario sin clase. Las pruebas (`magiaEnSuSitio`) exigen que, sin clase, la magia no mate antes que la mediana cuerpo a cuerpo ni sostenga más de la mitad de su daño, y que el Mago quede entre la más rápida cuerpo a cuerpo y 1,4 veces la mediana (1,6 el grimorio, cuyo área muerde a todo lo que pisa la runa y aquí pelea contra un solo mob). Contra los grandes, nada mágico puede matar claramente (un 10 %) antes que la más rápida cuerpo a cuerpo.

| Escenario | C/c más rápida | Mediana c/c | Báculo sin clase | Báculo Mago | Grimorio sin clase | Grimorio Mago |
|---|---|---|---|---|---|---|
| 0 % | guanteletes 0,92 | 1,50 | 2,35 (×1,57) | 1,28 (×0,86) | 2,15 (×1,44) | 1,61 (×1,08) |
| 50 % | guanteletes 0,53 | 1,12 | 1,25 (×1,12) | 0,94 (×0,83) | 1,71 (×1,53) | 1,30 (×1,16) |
| 100 % | guanteletes 0,24 | 0,71 | 0,72 (×1,01) | 0,59 (×0,84) | 1,19 (×1,69) | 1,03 (×1,45) |

TTK medio en segundos (entre paréntesis, frente a la mediana cuerpo a cuerpo). Daño por segundo sostenido en 60 s:

| Escenario | Mediana c/c | Báculo sin clase | Báculo Mago | Grimorio sin clase | Grimorio Mago |
|---|---|---|---|---|---|
| 0 % | 14,2 | 1,8 | 7,7 | 2,6 | 9,9 |
| 50 % | 18,0 | 2,7 | 10,9 | 3,1 | 11,6 |
| 100 % | 33,2 | 3,8 | 15,6 | 4,0 | 14,5 |

Los grandes, al 100 % (segundos para matar; «> 120» si no cae en dos minutos):

| Arma | Warden | Herrero Caído |
|---|---|---|
| báculo con Enjambre, sin clase | > 120 | > 120 |
| báculo con Enjambre, Mago | 6,0 | 40,8 |
| grimorio, sin clase | > 120 | > 120 |
| grimorio, Mago | 14,9 | 50,0 |
| guanteletes (la más rápida cuerpo a cuerpo) | 4,8 | 23,9 |

## Herrero Caído

Andy, 2026-09-30: «parece que puedes llegar a estar muy fuerte, o el Herrero Caído es muy débil, hazlo más fuerte». Objetivo: un jugador bien equipado de final de juego, solo, tarda de 3 a 5 minutos de pelea de verdad en Difícil (más en Implacable, menos en Normal y Fácil), y el jefe puede matar a un jugador equipado que se descuida. En Pacífico no hay pelea: un mundo pacífico no guarda ningún monstruo, tampoco a él. Lo mide `SmithFight` y lo exige `BalanceGameTests.herreroEnSuSitio`.

**Qué se mide en el jefe de verdad** (vestido con su mangual y su placa, del tamaño que le da la pelea por nivel, jugadores y equipo, en cada una de sus tres fases y aturdido), por el mismo camino de daño del juego: lo que le quita un golpe de cada arma, lo que les quitan a sus aprendices y a los yunques andantes, y lo que quita cada golpe suyo (el normal, el revés, la onda, el garfio y las estrellas) a un jugador con la armadura de referencia (placa de obsidiacero sobre cuero, Protección al 100 % en las cuatro piezas y Vitalidad en la pechera), recién llegado y con la presión de una pelea larga.

**Qué es modelo** (los números están en `SmithFight`): el daño por segundo de un jugador contra cada fase es la pelea tick a tick de este informe durante 120 s; el tiempo que pasa pegándole es lo que queda tras esquivar cada movimiento suyo cada vez que vuelve (onda 1,5 s, revés 1,0 s, estrellas 1,0 s, garfio desde lejos 1,5 s; un 15 % de moverse y seguirle cuerpo a cuerpo y un 10 % desde lejos; los eventos del cielo de su lado); las pausas son las de la pelea (levantarse, las dos llamadas de aprendices, el aturdido); el Reforjado cuesta 6 s por brasa más la última colada; los aprendices de las dos oleadas (y los guardianes de las brasas) y los seis yunques se matan uno tras otro con la misma arma, pegándoles el 80 % del tiempo. Con dos jugadores se reparten el daño, los aprendices y las brasas; el que no persigue solo esquiva la onda y el cielo. No cuenta la regeneración, las pociones ni las constelaciones que le hacen daño.

Los equipos (todos con herrero de nivel 10, armadura de referencia con Maestría 10, y la puntuación de equipo de `GearScore` que ve el jefe): **cc**: la mejor arma cuerpo a cuerpo al 100 % con Maestría 10, sin clase (guanteletes, equipo 0,82, vida 26); **guerrero**: la misma, Guerrero de nivel 50 (guanteletes, equipo 0,96, vida 36); **estrella** (la referencia): la misma estrellada, Guerrero de nivel 50, armadura estrellada (guanteletes, equipo 1,00, vida 36); **mago**: báculo con Enjambre estrellado, Mago de nivel 50, armadura estrellada (baculo, equipo 0,97, vida 32).

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
| facil | 1:30 – 3:00 | 1:31 | **1:46** | 256 → 360 | 23 / 13 s → 23 / 10 s | 48 s / 42 s | 11 % (estrellas, fase 3) / 11 % (estrellas, fase 3) |
| normal | 2:00 – 4:00 | 2:29 | **3:03** | 512 → 700 | 10 / 7 s → 10 / 5 s | 25 s / 20 s | 13 % (golpe, fase 3) / 15 % (golpe, fase 3) |
| dificil | 3:00 – 5:00 | 2:29 | **4:06** | 512 → 910 | 10 / 7 s → 10 / 4 s | 26 s / 19 s | 15 % (estrellas, fase 3) / 15 % (estrellas, fase 3) |
| extremo | 4:00 – 7:00 | 3:07 | **5:22** | 666 → 1120 | 8 / 5 s → 7 / 3 s | 20 s / 14 s | 17 % (golpe, fase 3) / 19 % (golpe, fase 3) |

### Tiempo de pelea por equipo (minutos: solo / dos jugadores; entre paréntesis, antes)

| Equipo | facil | normal | dificil | extremo |
|---|---|---|---|---|
| cc | 2:04 / 1:37 (1:43 / 0:59) | 3:49 / 3:01 (2:56 / 1:29) | 5:08 / 4:05 (2:56 / 1:29) | 6:42 / 5:15 (3:45 / 1:48) |
| guerrero | 1:52 / 1:27 (1:35 / 0:55) | 3:17 / 2:36 (2:38 / 1:21) | 4:24 / 3:31 (2:38 / 1:21) | 5:46 / 4:32 (3:19 / 1:36) |
| estrella | 1:46 / 1:22 (1:31 / 0:54) | 3:03 / 2:26 (2:29 / 1:18) | 4:06 / 3:16 (2:29 / 1:18) | 5:22 / 4:13 (3:07 / 1:31) |
| mago | 2:22 / 1:50 (1:46 / 1:02) | 4:16 / 3:18 (3:00 / 1:33) | 5:32 / 4:15 (3:00 / 1:33) | 6:52 / 5:16 (3:45 / 1:50) |

### De qué está hecha la pelea de la referencia, sola

*Sin parar*: lo que tardaría pegándole sin esquivar nada, sin pausas ni aprendices (la cuenta de antes, contra un maniquí). *Tope*: golpes del jugador que el tope por golpe recorta en la fase 1.

| Nivel | Vida | Daño/s por fase (1 / 2 / 3 / aturdido) | Tiempo pegándole por fase | Sin parar | Pegándole | Pausas | Reforjado | Aprendices y yunques | Total | Tope |
|---|---|---|---|---|---|---|---|---|---|---|
| facil | 360 | 15,2 / 11,7 / 12,0 / 19,2 | 50 % / 46 % / 30 % | 28 s | 60 s | 12 s | 21 s | 12 s | **106 s** | 0 % |
| normal | 700 | 12,8 / 11,7 / 12,0 / 19,2 | 47 % / 42 % / 30 % | 58 s | 132 s | 12 s | 21 s | 18 s | **183 s** | 0 % |
| dificil | 910 | 12,6 / 11,7 / 12,0 / 19,2 | 44 % / 38 % / 30 % | 75 s | 186 s | 12 s | 27 s | 21 s | **246 s** | 0 % |
| extremo | 1120 | 11,0 / 10,6 / 10,8 / 17,2 | 40 % / 34 % / 30 % | 95 s | 255 s | 12 s | 27 s | 28 s | **322 s** | 0 % |

### Lo que aguanta un jugador delante de él, solo

Segundos hasta morir con su vida entera. *Descuidado*: se queda delante y se lo come todo (su golpe cada 1,4 s, el revés, la onda, el garfio y las estrellas cada vez que vuelven, y 2 aprendices pegándole desde la fase 2), con la presión de una pelea larga. *Atento*: esquiva lo avisado y se come la mitad de sus golpes normales y el garfio (el mago, solo los golpes); ∞ si nada le alcanza. Golpe más grande: lo más que quita un solo golpe suyo, en cualquier fase, a ese jugador de su vida, recién llegado y con la armadura gastada por la presión de una pelea larga (la prueba exige menos del 50 % y del 80 %).

| Equipo | Nivel | Vida del jugador | Descuidado (fase 1 / 2 / 3) | Atento (fase 1 / 2 / 3) | Golpe más grande: recién llegado / con presión |
|---|---|---|---|---|---|
| cc | facil | 26 | 14 / 10 / 6 s | 29 s / 30 s / 25 s | 16 % (estrellas, fase 3) / 16 % (estrellas, fase 3) |
| cc | normal | 26 | 5 / 4 / 3 s | 14 s / 13 s / 11 s | 24 % (golpe, fase 3) / 27 % (golpe, fase 3) |
| cc | dificil | 26 | 5 / 4 / 3 s | 14 s / 13 s / 10 s | 24 % (golpe, fase 3) / 27 % (golpe, fase 3) |
| cc | extremo | 26 | 4 / 3 / 2 s | 12 s / 10 s / 8 s | 29 % (golpe, fase 3) / 32 % (golpe, fase 3) |
| guerrero | facil | 36 | 20 / 15 / 9 s | 43 s / 44 s / 37 s | 11 % (estrellas, fase 3) / 11 % (estrellas, fase 3) |
| guerrero | normal | 36 | 8 / 6 / 4 s | 22 s / 20 s / 17 s | 16 % (golpe, fase 3) / 17 % (golpe, fase 3) |
| guerrero | dificil | 36 | 8 / 6 / 4 s | 22 s / 20 s / 16 s | 16 % (golpe, fase 3) / 17 % (golpe, fase 3) |
| guerrero | extremo | 36 | 6 / 4 / 3 s | 17 s / 15 s / 13 s | 19 % (golpe, fase 3) / 21 % (golpe, fase 3) |
| estrella | facil | 36 | 23 / 17 / 10 s | 48 s / 49 s / 42 s | 11 % (estrellas, fase 3) / 11 % (estrellas, fase 3) |
| estrella | normal | 36 | 10 / 7 / 5 s | 25 s / 24 s / 20 s | 13 % (golpe, fase 3) / 15 % (golpe, fase 3) |
| estrella | dificil | 36 | 10 / 7 / 4 s | 26 s / 23 s / 19 s | 15 % (estrellas, fase 3) / 15 % (estrellas, fase 3) |
| estrella | extremo | 36 | 7 / 5 / 3 s | 20 s / 17 s / 14 s | 17 % (golpe, fase 3) / 19 % (golpe, fase 3) |
| mago | facil | 32 | 20 / 15 / 10 s | 42 s / 46 s / 39 s | 7 % (golpe, fase 3) / 7 % (estrellas, fase 3) |
| mago | normal | 32 | 8 / 6 / 4 s | 20 s / 20 s / 17 s | 17 % (golpe, fase 3) / 18 % (golpe, fase 3) |
| mago | dificil | 32 | 8 / 6 / 4 s | 21 s / 21 s / 18 s | 16 % (golpe, fase 3) / 17 % (golpe, fase 3) |
| mago | extremo | 32 | 6 / 4 / 3 s | 17 s / 17 s / 14 s | 20 % (golpe, fase 3) / 22 % (golpe, fase 3) |

El tope por golpe del jefe (`hitCapBoss`) es el 8 % de su vida por golpe normal de un jugador (los remates y los golpes al aturdido lo pasan). Con su vida de ahora recorta el 0 % de los golpes de la referencia en Difícil: no es lo que marca el ritmo de la pelea, sino la red contra un golpe suelto enorme, y se queda como estaba. Un golpe de algo que no es un jugador le sigue haciendo un 33 %, y La forja reclama sigue saltando con 32 de vida intentada por los grandes (lo que era una décima parte de sus 320).

## Las siete sospechas, medidas

| # | Sospecha | Veredicto | Lo medido |
|---|---|---|---|
| 1 | La estamina apenas frena a las armas rápidas: golpear cansado (×0,6) sale mejor que esperar | **Confirmada** | En la pelea larga (60 s) el mejor ritmo de 10 de 10 armas rápidas no espera a la estamina; mediana de golpes cansados 93 %. Daño con estamina / sin estamina: espada 64 %, daga 73 %, espadon 51 %, hacha 59 %, lanza 58 %, mazo 43 %, tridente 55 %, mangual 47 %, guanteletes 66 %, guadana 55 %. |
| 2 | El tope por golpe (45 % de la vida) deja a las mejores armas en un mínimo de 3 golpes contra mobs de 20 de vida, y decide la velocidad | **Confirmada a medias** | Al 100 %, mediana de golpes que el tope recorta contra los vanilla: 47 %. Pero cada extra de mejora es otro golpe con su propio tope: golpes para matar un zombi, mediana 2,0 (2,0–3,0), no 3; los extras son el 39 % del daño de las mejores armas al 100 % (mediana). |
| 3 | Los pactos son ganancia pura para el daño y encima dan sitio (+10 de potencial cada uno) | **Confirmada** | Al 50 % el potencial pasa de 50 a 70 (carga 7 → 12) y el TTK baja en 12 de 12 tipos (hasta −46 %). Al 100 %: mediana −22 %. |
| 4 | Frenesí suma velocidad plana (+0,6), así que casi dobla el mazo | **Confirmada a medias** | Un mazo corriente pasa de 0,60 a 1,20 golpes/s (×2,00); el mejor mazo, de 1,08 a 1,74 (×1,61). Pero su TTK sólo baja 12 % con Frenesí sola, frente a una mediana de 13 % en todos los tipos: la estamina (golpes cansados) y la invulnerabilidad se comen la cadencia extra. |
| 5 | Con el frenesí lleno, Matagigantes, Ejecución y Crítico rinden mucho para lo que pesan | **Confirmada** | Por punto de carga, esas tres superan a Filo en 21 de 36 casos (tipo × mejora). Sin el frenesí, el TTK medio al 100 % sube una mediana de +12 % (techo del frenesí: ×2,5 para las de un solo ingrediente). |
| 6 | Las mejoras de evento no pesan ni tienen techo: poder gratis | **Confirmada a medias** (gratis, pero rinde poco) | Lluvia estelar al 100 % encima de la mejor al 100 % (peso 0): TTK medio −3 % de mediana (−6 % a −1 %). Carnicero y Conductor sólo cuentan con más enemigos cerca, y aquí hay uno. |
| 7 | SwingStyle es sólo animación; el ×1,3 a la cabeza es igual para todos | **Confirmada a medias** | SwingStyle no entra en ningún número de daño salvo en *quién puede cargar*: no cargan baculo, grimorio. Pero no es sólo animación: la IA de los mobs lo lee (`ObsForja`, `RuleBrain`: reaccionan distinto a un tajo, un golpe desde arriba o una estocada), y eso aquí no se mide. Todo a la cabeza: TTK −24 % a +0 % según el tipo, no igual para todos: el tope por golpe y los extras (que no llevan el ×1,3) se comen parte. |

## Hallazgos que no estaban en la lista

- **La invulnerabilidad de vanilla (10 ticks) recorta los golpes rápidos, y los extras la esquivan.** Un golpe a menos de 10 ticks del anterior sólo quita lo que tenga *por encima* del último daño (medido con golpes reales: a los 5 ticks, un golpe igual no hace nada). Pero cada extra de mejora pone `invulnerableTime = 0` y deja como "último daño" el suyo, pequeño, así que el siguiente golpe rápido entra casi entero (medido: con una hoja de damasco, el segundo golpe a los 5 ticks entra). Daño en 60 s con / sin esa regla: hacha 90 %, espada 88 %, daga 85 %, espadon 100 %, lanza 100 %, mazo 100 %, tridente 100 %, mangual 100 %, guanteletes 87 %, guadana 97 %.
- **El tope por golpe no alcanza a los extras.** `CombatHooks.capped` corta cada llamada a `hurtServer`; cada extra de mejora es otra llamada, con su propio tope, así que un golpe con extras puede quitar más del 45 % de la vida (con Ráfaga y Cien manos los guanteletes matan a un esqueleto de un puñetazo). Parte del daño que viene de extras en las mejores armas al 100 %: hacha 38 %, espada 41 %, daga 44 %, espadon 37 %, lanza 39 %, mazo 38 %, tridente 39 %, mangual 37 %, guanteletes 50 %, guadana 39 %, baculo 47 %, grimorio 27 %.
- **Afilado (+3 por golpe mientras el arma está nueva) vale en cualquier pieza**, también en una atadura o una guarda, y se suma a cada golpe sin mirar la velocidad. Cambiar las piezas con Afilado de la mejor al 100 % por netherita sube el TTK: hacha +16 %, espada +32 %, daga +40 %, espadon +3 %, lanza +9 %, mazo +11 %, tridente +8 %, mangual +12 %, guanteletes +8 %, guadana +9 %, baculo +19 %, grimorio +14 %.
- **El mango de vidriacero está en 28 de 36 mejores armas.** Su velocidad de mango (+0,30) más la de su rasgo Diáfano (+0,3 al atributo) le dan el doble que cualquier otro mango. Cambiarlo por acero estelar (el mejor mango sin rasgo): espada +22 %, daga +23 %, espadon +8 %, hacha +21 %, lanza +8 %, mazo +45 %, tridente +3 %, mangual +10 %, guanteletes +14 %, guadana +8 %.
- **El Mestizaje se aplica al atributo real.** Una espada de damasco con mango de vidriacero y guarda de eco recibe 10 % por mezclar rasgos: la ficha enseña 8,70 de daño y el atributo da 8,70.
- **La magia no gasta estamina ni se cansa**, no le afecta la invulnerabilidad (cada proyectil y cada mordisco de runa la ponen a 0) y pasa por encima de la armadura (daño mágico: el yunque andante, con 10 de armadura, pierde 1,15 por punto de rayo del báculo y 0,45 / 0,56 por punto de espada). TTK medio al 100 %: báculo 0,72 s, grimorio 1,19 s, frente a una mediana cuerpo a cuerpo de 0,71 s. La bruja sólo recibe el 15 % de la magia. El enderman no se teletransporta ante el rayo del báculo como ante las flechas; lo esquiva como un golpe (34 %, luego 7 s sin esquivar), y eso el modelo no lo cuenta.

## Mejor conjunto por tipo de arma

Ráfaga: daño en los 3 primeros segundos con la estamina llena. Sostenido: daño por segundo en 60 s. Los dos contra un maniquí neutro (sin armadura, sin tope, sin resistencias, barra de postura de un mob de 20 de vida): lo que el arma pone. TTK medio: media geométrica de los segundos para matar a los mobs de muestra. Cansados / en invulnerabilidad: parte de los golpes de la pelea larga dados sin estamina / que cayeron dentro de los 10 ticks de invulnerabilidad y sólo quitaron lo que superaba al último daño.

| Tipo | Escenario | Materiales | Mejoras | Ráfaga (daño/s) | Sostenido (daño/s) | TTK medio (s) | Ritmo sostenido | Cansados | En invulnerabilidad |
|---|---|---|---|---|---|---|---|---|---|
| espada | 0 % | hoja iracero · mango vidriacero · guarda damasco | — | 40,0 | 19,1 | 1,44 | cada 11 ticks | 93 % | 0 % |
| espada | 50 % | hoja iracero · mango vidriacero · guarda damasco | frenesi 50 %, matagigantes 50 %, brecha 50 % | 65,9 | 26,3 | 1,12 | cada 10 ticks | 93 % | 0 % |
| espada | 50 % con pactos | hoja iracero · mango vidriacero · guarda damasco | tormenta 70 %, frenesi 70 %, ejecucion 70 %, matagigantes 70 %, brecha 70 %, pacto_de_sed, pacto_de_vidrio | 114,3 | 47,8 | 0,69 | cada 9 ticks | 94 % | 99 % |
| espada | 100 % | hoja damasco · mango vidriacero · guarda vidriacero | tormenta, critico, frenesi, ejecucion, matagigantes, filo, aspecto_igneo | 70,7 | 32,9 | 0,68 | cada 7 ticks | 95 % | 99 % |
| espada | 100 % con pactos | hoja iracero · mango vidriacero · guarda damasco | tormenta, critico, frenesi, ejecucion, matagigantes, brecha, pacto_de_sed, pacto_de_vidrio | 131,2 | 57,8 | 0,52 | cada 8 ticks | 95 % | 99 % |
| daga | 0 % | hoja iracero · mango cuarzo | — | 34,7 | 18,4 | 1,50 | cada 10 ticks | 93 % | 0 % |
| daga | 50 % | hoja iracero · mango cuarzo | matagigantes 50 %, filo 50 % | 54,7 | 22,1 | 1,12 | cada 10 ticks | 93 % | 0 % |
| daga | 50 % con pactos | hoja vidriacero · mango cuarzo | frenesi 70 %, ejecucion 70 %, matagigantes 70 %, filo 70 %, pacto_de_sed, pacto_de_vidrio | 77,4 | 41,4 | 0,64 | cada 5 ticks | 97 % | 100 % |
| daga | 100 % | hoja vidriacero · mango cuarzo | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 60,3 | 40,2 | 0,58 | cada 5 ticks | 97 % | 100 % |
| daga | 100 % con pactos | hoja vidriacero · mango cuarzo | tormenta, critico, frenesi, ejecucion, matagigantes, filo, pacto_de_sed, pacto_de_vidrio | 90,2 | 58,3 | 0,44 | cada 5 ticks | 97 % | 100 % |
| espadon | 0 % | hoja iracero · hoja iracero · mango vidriacero · guarda damasco | — | 49,3 | 14,2 | 1,46 | cada 10 ticks | 93 % | 0 % |
| espadon | 50 % | hoja iracero · hoja iracero · mango vidriacero · guarda damasco | ejecucion 50 %, matagigantes 50 %, brecha 50 % | 76,3 | 18,0 | 1,12 | cada 19 ticks, carga para rematar, descansando al vaciarse | 0 % | 0 % |
| espadon | 50 % con pactos | hoja iracero · hoja iracero · mango vidriacero · guarda damasco | tormenta 70 %, critico 70 %, ejecucion 70 %, matagigantes 70 %, brecha 70 %, pacto_de_sed, pacto_de_vidrio | 141,4 | 32,5 | 0,66 | cada 19 ticks, carga para rematar, descansando al vaciarse | 0 % | 0 % |
| espadon | 100 % | hoja iracero · hoja iracero · mango vidriacero · guarda damasco | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 97,4 | 35,4 | 0,74 | cada 11 ticks | 93 % | 0 % |
| espadon | 100 % con pactos | hoja iracero · hoja iracero · mango vidriacero · guarda damasco | tormenta, critico, ejecucion, matagigantes, brecha, pacto_de_sed, pacto_de_vidrio | 156,4 | 35,4 | 0,54 | cada 19 ticks, carga para rematar, descansando al vaciarse | 0 % | 0 % |
| hacha | 0 % | cabeza_hacha iracero · mango vidriacero · atadura damasco | — | 45,9 | 14,2 | 1,40 | cada 10 ticks | 93 % | 0 % |
| hacha | 50 % | cabeza_hacha iracero · mango vidriacero · atadura damasco | ejecucion 50 %, matagigantes 50 %, brecha 50 % | 70,3 | 17,4 | 1,10 | cada 16 ticks | 89 % | 0 % |
| hacha | 50 % con pactos | cabeza_hacha iracero · mango vidriacero · atadura damasco | tormenta 70 %, critico 70 %, ejecucion 70 %, matagigantes 70 %, brecha 70 %, pacto_de_sed, pacto_de_vidrio | 130,4 | 30,7 | 0,65 | cada 16 ticks | 89 % | 0 % |
| hacha | 100 % | cabeza_hacha vidriacero · mango vidriacero · atadura damasco | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 76,9 | 32,6 | 0,71 | cada 8 ticks | 95 % | 99 % |
| hacha | 100 % con pactos | cabeza_hacha iracero · mango vidriacero · atadura damasco | tormenta, critico, ejecucion, matagigantes, brecha, pacto_de_sed, pacto_de_vidrio | 144,3 | 33,6 | 0,55 | cada 16 ticks | 89 % | 0 % |
| lanza | 0 % | punta_lanza iracero · mango vidriacero · atadura damasco | — | 29,0 | 10,3 | 2,47 | cada 10 ticks | 93 % | 0 % |
| lanza | 50 % | punta_lanza iracero · mango vidriacero · atadura damasco | frenesi 50 %, matagigantes 50 %, castigo 50 % | 43,5 | 13,7 | 1,62 | cada 13 ticks | 91 % | 0 % |
| lanza | 50 % con pactos | punta_lanza iracero · mango vidriacero · atadura damasco | frenesi 70 %, ejecucion 70 %, matagigantes 70 %, filo 70 %, pacto_de_sed, pacto_de_vidrio | 79,4 | 23,8 | 0,98 | cada 13 ticks | 91 % | 0 % |
| lanza | 100 % | punta_lanza iracero · mango vidriacero · atadura damasco | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 56,3 | 23,2 | 1,10 | cada 11 ticks | 93 % | 0 % |
| lanza | 100 % con pactos | punta_lanza iracero · mango vidriacero · atadura damasco | critico, frenesi, ejecucion, matagigantes, filo, pacto_de_sed, pacto_de_vidrio | 91,7 | 32,2 | 0,79 | cada 11 ticks | 93 % | 0 % |
| mazo | 0 % | cabeza_mazo vidriacero · mango vidriacero · atadura damasco | — | 29,8 | 10,7 | 1,70 | cada 10 ticks | 93 % | 0 % |
| mazo | 50 % | cabeza_mazo vidriacero · mango vidriacero · atadura damasco | ejecucion 50 %, filo 50 % | 29,8 | 10,7 | 1,15 | cada 10 ticks | 93 % | 0 % |
| mazo | 50 % con pactos | cabeza_mazo iracero · mango vidriacero · atadura damasco | critico 70 %, frenesi 70 %, ejecucion 70 %, matagigantes 70 %, brecha 70 %, pacto_de_sed, pacto_de_vidrio | 114,3 | 27,1 | 0,64 | cada 17 ticks | 89 % | 0 % |
| mazo | 100 % | cabeza_mazo iracero · mango vidriacero · atadura damasco | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 76,3 | 22,7 | 0,61 | cada 14 ticks | 91 % | 0 % |
| mazo | 100 % con pactos | cabeza_mazo iracero · mango vidriacero · atadura damasco | tormenta, critico, frenesi, ejecucion, matagigantes, filo, pacto_de_sed, pacto_de_vidrio | 120,0 | 33,5 | 0,48 | cada 14 ticks | 91 % | 0 % |
| tridente | 0 % | punta_tridente iracero · mango vidriacero · atadura damasco | — | 42,6 | 14,3 | 1,52 | cada 15 ticks, carga para rematar | 93 % | 0 % |
| tridente | 50 % | punta_tridente iracero · mango vidriacero · atadura damasco | frenesi 50 %, matagigantes 50 %, castigo 50 % | 68,0 | 21,7 | 1,19 | cada 13 ticks | 91 % | 0 % |
| tridente | 50 % con pactos | punta_tridente iracero · mango vidriacero · atadura damasco | tormenta 70 %, frenesi 70 %, ejecucion 70 %, matagigantes 70 %, perdicion_de_artropodos 70 %, pacto_de_sed, pacto_de_vidrio | 120,9 | 41,1 | 0,69 | cada 11 ticks | 93 % | 0 % |
| tridente | 100 % | punta_tridente iracero · mango vidriacero · atadura damasco | tormenta, critico, frenesi, ejecucion, matagigantes, filo, aspecto_igneo | 85,2 | 33,9 | 0,78 | cada 10 ticks | 93 % | 0 % |
| tridente | 100 % con pactos | punta_tridente damasco · mango vidriacero · atadura vidriacero | tormenta, critico, frenesi, ejecucion, matagigantes, perdicion_de_artropodos, pacto_de_sed, pacto_de_vidrio | 119,5 | 46,3 | 0,63 | cada 10 ticks | 93 % | 0 % |
| mangual | 0 % | bola damasco · cadena vidriacero · mango vidriacero | — | 39,5 | 12,9 | 1,24 | cada 10 ticks | 93 % | 0 % |
| mangual | 50 % | bola damasco · cadena vidriacero · mango vidriacero | critico 50 %, ejecucion 50 %, matagigantes 50 % | 63,5 | 15,4 | 0,89 | cada 16 ticks | 89 % | 0 % |
| mangual | 50 % con pactos | bola iracero · cadena damasco · mango vidriacero | critico 70 %, frenesi 70 %, ejecucion 70 %, matagigantes 70 %, brecha 70 %, pacto_de_sed, pacto_de_vidrio | 138,6 | 36,6 | 0,56 | cada 13 ticks | 91 % | 0 % |
| mangual | 100 % | bola iracero · cadena damasco · mango vidriacero | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 90,9 | 30,2 | 0,52 | cada 13 ticks | 91 % | 0 % |
| mangual | 100 % con pactos | bola vidriacero · cadena damasco · mango vidriacero | tormenta, critico, frenesi, ejecucion, matagigantes, aspecto_igneo, pacto_de_sed, pacto_de_vidrio | 117,7 | 47,8 | 0,46 | cada 10 ticks | 93 % | 0 % |
| guanteletes | 0 % | manopla vidriacero · nudillos iracero · remache damasco | — | 37,6 | 24,9 | 0,92 | cada 5 ticks | 97 % | 100 % |
| guanteletes | 50 % | manopla vidriacero · nudillos iracero · remache damasco | matagigantes 50 %, rafaga 50 %, nudillos_de_hierro 50 % | 67,1 | 38,5 | 0,53 | cada 5 ticks | 97 % | 100 % |
| guanteletes | 50 % con pactos | manopla vidriacero · nudillos iracero · remache damasco | tormenta 70 %, ejecucion 70 %, brecha 70 %, pacto_de_sed, pacto_de_vidrio, rafaga 70 %, nudillos_de_hierro 70 % | 95,4 | 60,5 | 0,28 | cada 5 ticks | 97 % | 100 % |
| guanteletes | 100 % | manopla vidriacero · nudillos iracero · remache damasco | critico, frenesi, ejecucion, matagigantes, filo, rafaga, nudillos_de_hierro | 99,0 | 61,7 | 0,24 | cada 4 ticks | 97 % | 100 % |
| guanteletes | 100 % con pactos | manopla vidriacero · nudillos iracero · remache damasco | tormenta, critico, frenesi, ejecucion, matagigantes, brecha, pacto_de_sed, pacto_de_vidrio, rafaga, nudillos_de_hierro | 147,7 | 100,0 | 0,16 | cada 4 ticks | 97 % | 100 % |
| guadana | 0 % | hoja iracero · mango vidriacero · atadura damasco | — | 42,6 | 13,5 | 1,52 | cada 10 ticks | 93 % | 0 % |
| guadana | 50 % | hoja iracero · mango vidriacero · atadura damasco | critico 50 %, matagigantes 50 %, brecha 50 % | 71,8 | 17,6 | 1,13 | cada 18 ticks, carga para rematar | 91 % | 2 % |
| guadana | 50 % con pactos | hoja iracero · mango vidriacero · atadura damasco | critico 70 %, frenesi 70 %, ejecucion 70 %, matagigantes 70 %, brecha 70 %, pacto_de_sed, pacto_de_vidrio | 127,6 | 39,3 | 0,73 | cada 13 ticks | 91 % | 21 % |
| guadana | 100 % | hoja iracero · mango vidriacero · atadura damasco | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 84,9 | 33,2 | 0,72 | cada 11 ticks | 93 % | 36 % |
| guadana | 100 % con pactos | hoja iracero · mango vidriacero · atadura damasco | tormenta, critico, frenesi, ejecucion, matagigantes, brecha, pacto_de_sed, pacto_de_vidrio | 133,4 | 50,1 | 0,53 | cada 11 ticks | 93 % | 36 % |
| baculo | 0 % | nucleo iracero · engaste prismarina · mango cuarzo | — | 17,6 | 1,8 | 2,35 | cada 10 ticks | 0 % | 0 % |
| baculo | 50 % | nucleo iracero · engaste prismarina · mango cuarzo | castigo 50 %, resonancia 50 % | 25,7 | 2,7 | 1,25 | cada 10 ticks | 0 % | 0 % |
| baculo | 50 % con pactos | nucleo iracero · engaste prismarina · mango cuarzo | ejecucion 70 %, matagigantes 70 %, filo 70 %, pacto_de_sed, pacto_de_vidrio, resonancia 70 % | 28,3 | 3,0 | 1,00 | cada 10 ticks | 0 % | 0 % |
| baculo | 100 % | nucleo iracero · engaste prismarina · mango cuarzo | tormenta, ejecucion, matagigantes, filo, conjuro_veloz, resonancia | 46,5 | 3,8 | 0,72 | cada 8 ticks | 0 % | 0 % |
| baculo | 100 % con pactos | nucleo iracero · engaste prismarina · mango cuarzo | tormenta, ejecucion, matagigantes, filo, pacto_de_sed, pacto_de_vidrio, conjuro_veloz, resonancia | 46,5 | 3,8 | 0,72 | cada 8 ticks | 0 % | 0 % |
| grimorio | 0 % | nucleo iracero · tapas cuarzo · remache prismarina | — | 18,0 | 2,6 | 2,15 | cada 20 ticks | 0 % | 0 % |
| grimorio | 50 % | nucleo iracero · tapas cuarzo · remache prismarina | conjuro_veloz 50 %, resonancia 50 % | 26,7 | 3,1 | 1,71 | cada 18 ticks | 0 % | 0 % |
| grimorio | 50 % con pactos | nucleo iracero · tapas cuarzo · remache eco | ejecucion 70 %, matagigantes 70 %, perdicion_de_artropodos 70 %, pacto_de_sed, pacto_de_vidrio, conjuro_veloz 70 %, resonancia 70 % | 31,9 | 3,5 | 1,38 | cada 17 ticks | 0 % | 0 % |
| grimorio | 100 % | nucleo iracero · tapas cuarzo · remache prismarina | tormenta, ejecucion, matagigantes, filo, resonancia | 30,7 | 4,0 | 1,19 | cada 20 ticks | 0 % | 0 % |
| grimorio | 100 % con pactos | nucleo iracero · tapas cuarzo · remache prismarina | tormenta, ejecucion, matagigantes, filo, pacto_de_sed, pacto_de_vidrio, resonancia | 30,7 | 4,0 | 1,19 | cada 20 ticks | 0 % | 0 % |

**Las tres mejores combinaciones de materiales sin mejoras** (TTK medio en la muestra):

- **espada** (7976 combinaciones tras podar cada hueco, 6842 distintas en pelea, 61 en el frente): hoja iracero · mango vidriacero · guarda damasco (1,44 s); hoja damasco · mango vidriacero · guarda eco (1,59 s); hoja arcanio · mango vidriacero · guarda damasco (1,59 s).
- **daga** (684 combinaciones tras podar cada hueco, 652 distintas en pelea, 30 en el frente): hoja iracero · mango cuarzo (1,50 s); hoja damasco · mango vidriacero (1,85 s); hoja vidriacero · mango cuarzo (1,93 s).
- **espadon** (54766 combinaciones tras podar cada hueco, 43277 distintas en pelea, 111 en el frente): hoja iracero · hoja iracero · mango vidriacero · guarda damasco (1,46 s); hoja damasco · hoja iracero · mango vidriacero · guarda vidriacero (1,46 s); hoja damasco · hoja vidriacero · mango arcanio · guarda eco (1,47 s).
- **hacha** (7865 combinaciones tras podar cada hueco, 6075 distintas en pelea, 36 en el frente): cabeza_hacha iracero · mango vidriacero · atadura damasco (1,40 s); cabeza_hacha damasco · mango vidriacero · atadura vidriacero (1,48 s); cabeza_hacha vidriacero · mango vidriacero · atadura damasco (1,50 s).
- **lanza** (1414 combinaciones tras podar cada hueco, 1121 distintas en pelea, 86 en el frente): punta_lanza iracero · mango vidriacero · atadura damasco (2,47 s); punta_lanza corazon · mango vidriacero · atadura damasco (2,80 s); punta_lanza damasco · mango vidriacero · atadura eco (2,82 s).
- **mazo** (7749 combinaciones tras podar cada hueco, 6623 distintas en pelea, 59 en el frente): cabeza_mazo vidriacero · mango vidriacero · atadura damasco (1,70 s); cabeza_mazo damasco · mango vidriacero · atadura vidriacero (2,04 s); cabeza_mazo iracero · mango vidriacero · atadura damasco (2,16 s).
- **tridente** (7866 combinaciones tras podar cada hueco, 6722 distintas en pelea, 63 en el frente): punta_tridente iracero · mango vidriacero · atadura damasco (1,52 s); punta_tridente damasco · mango vidriacero · atadura vidriacero (1,67 s); punta_tridente damasco · mango vidriacero · atadura eco (1,67 s).
- **mangual** (7823 combinaciones tras podar cada hueco, 6733 distintas en pelea, 60 en el frente): bola damasco · cadena vidriacero · mango vidriacero (1,24 s); bola vidriacero · cadena damasco · mango arcanio (1,28 s); bola vidriacero · cadena damasco · mango vidriacero (1,29 s).
- **guanteletes** (8994 combinaciones tras podar cada hueco, 7802 distintas en pelea, 63 en el frente): manopla vidriacero · nudillos iracero · remache damasco (0,92 s); manopla cuarzo · nudillos iracero · remache prismarina (1,02 s); manopla cuarzo · nudillos iracero · remache vara_de_blaze (1,02 s).
- **guadana** (7865 combinaciones tras podar cada hueco, 6756 distintas en pelea, 63 en el frente): hoja iracero · mango vidriacero · atadura damasco (1,52 s); hoja damasco · mango vidriacero · atadura vidriacero (1,63 s); hoja damasco · mango arcanio · atadura vidriacero (1,63 s).
- **baculo** (7893 combinaciones tras podar cada hueco, 6785 distintas en pelea, 62 en el frente): nucleo iracero · engaste prismarina · mango cuarzo (2,35 s); nucleo iracero · engaste vara_de_blaze · mango cuarzo (2,35 s); nucleo iracero · engaste eco · mango cuarzo (2,35 s).
- **grimorio** (8038 combinaciones tras podar cada hueco, 6960 distintas en pelea, 59 en el frente): nucleo iracero · tapas cuarzo · remache prismarina (2,15 s); nucleo iracero · tapas cuarzo · remache vara_de_blaze (2,15 s); nucleo iracero · tapas cuarzo · remache eco (2,15 s).

**Mejor arma de cada tipo** (100 %, sin pactos):

- **espada**: hoja damasco · mango vidriacero · guarda vidriacero; tormenta, critico, frenesi, ejecucion, matagigantes, filo, aspecto_igneo — TTK medio 0,68 s, 32,9 daño/s sostenido. *No se mide aquí:* el barrido (Filo arrasador) y la guardia con parada.
- **daga**: hoja vidriacero · mango cuarzo; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,58 s, 40,2 daño/s sostenido. *No se mide aquí:* lanzar la hoja (Lanzacabezas) y la guardia con parada.
- **espadon**: hoja iracero · hoja iracero · mango vidriacero · guarda damasco; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,74 s, 35,4 daño/s sostenido. *No se mide aquí:* el barrido (Filo arrasador) y la guardia con parada.
- **hacha**: cabeza_hacha vidriacero · mango vidriacero · atadura damasco; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,71 s, 32,6 daño/s sostenido. *No se mide aquí:* romper escudos y talar.
- **lanza**: punta_lanza iracero · mango vidriacero · atadura damasco; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 1,10 s, 23,2 daño/s sostenido. *No se mide aquí:* la carga a la carrera o a caballo (arma cinética) y el alcance.
- **mazo**: cabeza_mazo iracero · mango vidriacero · atadura damasco; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,61 s, 22,7 daño/s sostenido. *No se mide aquí:* el golpe cayendo (Densidad, Estallido de viento).
- **tridente**: punta_tridente iracero · mango vidriacero · atadura damasco; tormenta, critico, frenesi, ejecucion, matagigantes, filo, aspecto_igneo — TTK medio 0,78 s, 33,9 daño/s sostenido. *No se mide aquí:* lanzarlo (Retorno, Corriente, Canalización) y el alcance.
- **mangual**: bola iracero · cadena damasco · mango vidriacero; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,52 s, 30,2 daño/s sostenido. *No se mide aquí:* el área (Segunda cabeza, Martillo pilón), el aturdimiento y el alcance de su cadena.
- **guanteletes**: manopla vidriacero · nudillos iracero · remache damasco; critico, frenesi, ejecucion, matagigantes, filo, rafaga, nudillos_de_hierro — TTK medio 0,24 s, 61,7 daño/s sostenido. *No se mide aquí:* el combo de Nudillos y la Maestría más rápida.
- **guadana**: hoja iracero · mango vidriacero · atadura damasco; tormenta, critico, frenesi, ejecucion, matagigantes, filo — TTK medio 0,72 s, 33,2 daño/s sostenido. *No se mide aquí:* el barrido, el alcance y la cosecha.
- **baculo**: nucleo iracero · engaste prismarina · mango cuarzo; tormenta, ejecucion, matagigantes, filo, conjuro_veloz, resonancia — TTK medio 0,72 s, 3,8 daño/s sostenido. *No se mide aquí:* el abanico de Prisma y los proyectiles que buscan (Buscador).
- **grimorio**: nucleo iracero · tapas cuarzo · remache prismarina; tormenta, ejecucion, matagigantes, filo, resonancia — TTK medio 1,19 s, 4,0 daño/s sostenido. *No se mide aquí:* el área entera de la runa (todo lo que pisa), Vórtice y Santuario.

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
| zombie | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | **0,20** | 0,50 | 0,40 | 0,80 |
| husk | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | **0,20** | 0,50 | 0,40 | 0,80 |
| drowned | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | **0,20** | 0,50 | 0,40 | 0,80 |
| zombie_villager | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | **0,20** | 0,50 | 0,40 | 0,80 |
| skeleton | 0,35 | 0,25 | 0,50 | 0,40 | 0,55 | 0,17 | 0,50 | 0,17 | **0,03** | 0,43 | 0,30 | 0,80 |
| stray | 0,35 | 0,25 | 0,50 | 0,40 | 0,55 | 0,17 | 0,50 | 0,17 | **0,03** | 0,43 | 0,30 | 0,80 |
| bogged | 0,29 | 0,22 | 0,17 | 0,33 | 0,50 | 0,17 | 0,50 | 0,17 | **0,03** | 0,42 | 0,34 | 0,80 |
| wither_skeleton | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,17 | 0,50 | 0,17 | **0,03** | 0,50 | 0,30 | 0,80 |
| creeper | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,42 | 0,50 | 0,42 | **0,06** | 0,43 | 0,38 | 0,80 |
| spider | 0,12 | 0,15 | 0,17 | 0,13 | 0,42 | 0,17 | 0,42 | 0,17 | **0,03** | 0,17 | 0,30 | 0,65 |
| cave_spider | 0,12 | 0,08 | 0,17 | 0,13 | 0,17 | 0,17 | 0,17 | 0,17 | **0,03** | 0,17 | 0,18 | 0,26 |
| pillager | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,29 | 0,50 | 0,29 | **0,14** | 0,43 | 0,40 | 0,80 |
| vindicator | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,29 | 0,50 | 0,29 | **0,14** | 0,43 | 0,40 | 0,80 |
| evoker | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,29 | 0,50 | 0,29 | **0,14** | 0,43 | 0,40 | 0,80 |
| witch | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | 0,29 | **0,20** | 0,50 | 0,90 | 1,89 |
| enderman | 0,40 | 0,50 | 0,50 | 0,40 | 1,00 | 0,50 | 0,50 | 0,50 | **0,22** | 0,50 | 0,74 | 1,00 |
| blaze | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,42 | 0,50 | 0,42 | **0,06** | 0,50 | 0,38 | 0,80 |
| piglin_brute | 0,70 | 0,50 | 0,50 | 0,80 | 1,00 | 0,81 | 0,57 | 0,50 | **0,43** | 0,55 | 0,80 | 1,00 |
| herrero_caido *(Forja)* | 40,91 | 30,28 | 42,60 | 40,20 | 55,28 | 59,79 | 40,35 | 46,30 | **23,89** | 40,31 | > 120 | > 120 |
| automata_de_forja *(Forja)* | 2,55 | 1,95 | 2,40 | 2,25 | 3,87 | 1,44 | 2,50 | 1,13 | **0,67** | 2,20 | 1,50 | 2,00 |
| coraza_vacia *(Forja)* | 4,93 | 4,50 | 4,54 | 4,56 | 4,50 | 3,25 | 4,50 | 3,50 | **1,89** | 4,50 | 31,80 | 4,00 |
| pavesa *(Forja)* | 0,12 | 0,08 | 0,17 | 0,13 | 0,17 | 0,17 | 0,17 | 0,17 | **0,03** | 0,17 | 0,30 | 0,45 |
| herrumbre *(Forja)* | 0,12 | 0,08 | 0,17 | 0,13 | 0,29 | **0,00** | 0,29 | **0,00** | **0,00** | 0,17 | **0,00** | 0,26 |
| ascua_mayor *(Forja)* | 0,35 | 0,42 | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | **0,20** | 0,50 | 0,74 | 1,00 |
| escoria_viviente *(Forja)* | 0,20 | **0,15** | 0,29 | 0,23 | 0,29 | 0,70 | 0,29 | 0,50 | 0,38 | 0,29 | 0,82 | 0,89 |
| yunque_andante *(Forja)* | 2,55 | 1,89 | 2,43 | 2,27 | 2,20 | 1,00 | 1,50 | 0,70 | **0,40** | 2,23 | 1,10 | 1,83 |
| percutor *(Forja)* | 1,75 | 1,50 | 1,54 | 1,80 | 2,20 | 2,25 | 1,21 | 2,03 | **1,00** | 1,58 | 1,98 | 2,70 |
| tenaza *(Forja)* | 0,50 | 0,50 | 0,50 | 0,40 | 1,00 | 0,50 | 0,50 | 0,50 | **0,20** | 0,50 | 0,71 | 1,00 |
| cargador_de_carbon *(Forja)* | 0,70 | 0,50 | 0,50 | 0,45 | 1,00 | 0,63 | 0,50 | 0,50 | **0,20** | 0,50 | 0,80 | 1,00 |
| templador *(Forja)* | 0,35 | 0,25 | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | 0,43 | **0,20** | 0,50 | 0,40 | 0,80 |
| nucleo_estelar *(Forja)* | — | — | — | — | — | — | — | — | — | — | — | — |
| molde_roto *(Forja)* | 1,00 | 0,75 | 1,00 | 0,87 | 1,06 | 1,00 | 1,00 | 1,00 | **0,40** | 1,00 | 1,10 | 1,80 |
| guardian_de_cuno *(Forja)* | 6,69 | 5,07 | 6,28 | 6,04 | 7,91 | 2,50 | 5,46 | 2,41 | **1,42** | 5,69 | 2,80 | 4,34 |

## Tiempo para matar (s), mejores armas al 50 %

En negrita el tipo más rápido contra ese mob. "—": no se le puede hacer daño así (sellos, absorción). "> 120": no muere en dos minutos.

| Mob | espada | daga | espadon | hacha | lanza | mazo | tridente | mangual | guanteletes | guadana | baculo | grimorio |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| zombie | 0,45 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,31** | 0,50 | 0,50 | 0,90 |
| husk | 0,45 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,31** | 0,50 | 0,50 | 0,90 |
| drowned | 0,45 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,31** | 0,50 | 0,50 | 0,90 |
| zombie_villager | 0,45 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,31** | 0,50 | 0,50 | 0,90 |
| skeleton | 0,45 | 0,40 | 0,50 | 0,50 | 0,65 | 0,50 | 0,50 | 0,35 | **0,15** | 0,50 | 0,30 | 0,90 |
| stray | 0,45 | 0,40 | 0,50 | 0,50 | 0,65 | 0,50 | 0,50 | 0,35 | **0,15** | 0,50 | 0,30 | 0,90 |
| bogged | 0,45 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,30 | 0,90 |
| wither_skeleton | 0,45 | 0,40 | 0,50 | 0,50 | 0,65 | 0,50 | 0,50 | 0,35 | **0,15** | 0,50 | 0,30 | 0,90 |
| creeper | 0,45 | 0,40 | 0,50 | 0,50 | 0,70 | 0,50 | 0,50 | 0,50 | **0,31** | 0,50 | 0,80 | 0,90 |
| spider | 0,45 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,35 | **0,15** | 0,35 | 0,50 | 0,80 |
| cave_spider | 0,45 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,35 | **0,15** | 0,35 | 0,30 | 0,80 |
| pillager | 0,45 | 0,40 | 0,50 | 0,50 | 1,00 | 0,85 | 0,50 | 0,50 | **0,31** | 0,50 | 1,00 | 0,90 |
| vindicator | 0,45 | 0,40 | 0,50 | 0,50 | 1,00 | 0,85 | 0,50 | 0,50 | **0,31** | 0,50 | 1,00 | 0,90 |
| evoker | 0,45 | 0,40 | 0,50 | 0,50 | 1,00 | 0,85 | 0,50 | 0,50 | **0,31** | 0,50 | 1,00 | 0,90 |
| witch | 0,45 | 0,40 | 0,50 | 0,50 | 1,00 | 0,85 | 0,50 | 0,50 | **0,38** | 0,50 | 1,50 | 2,60 |
| enderman | 0,90 | 0,80 | 0,95 | 1,00 | 1,50 | 1,00 | 1,00 | 1,00 | **0,54** | 1,00 | 1,50 | 1,80 |
| blaze | 0,45 | 0,40 | 0,50 | 0,50 | 0,70 | 0,50 | 0,50 | 0,50 | **0,31** | 0,50 | 0,80 | 0,90 |
| piglin_brute | 1,00 | 1,00 | 1,00 | 1,00 | 1,65 | 1,85 | 1,00 | 1,00 | **0,75** | 1,00 | 2,00 | 1,80 |
| herrero_caido *(Forja)* | 58,50 | 65,00 | 87,40 | 81,60 | 108,00 | 113,00 | 71,40 | 100,02 | **47,68** | 81,16 | > 120 | > 120 |
| automata_de_forja *(Forja)* | 3,50 | 4,50 | 2,85 | 2,50 | 9,75 | 2,50 | 5,20 | 2,50 | **1,52** | 3,29 | 3,00 | 3,60 |
| coraza_vacia *(Forja)* | 5,50 | 5,50 | 6,00 | 5,00 | 6,60 | **1,50** | 4,80 | 3,48 | 3,75 | 6,40 | 32,25 | 7,05 |
| pavesa *(Forja)* | 0,45 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,35 | **0,15** | 0,35 | 0,30 | 0,80 |
| herrumbre *(Forja)* | 0,45 | 0,40 | 0,50 | 0,50 | 0,50 | **0,00** | 0,50 | **0,00** | **0,00** | 0,35 | 0,30 | 0,45 |
| ascua_mayor *(Forja)* | 0,90 | 0,80 | 0,85 | 0,80 | 1,00 | 1,00 | 1,00 | 0,80 | **0,47** | 0,80 | 1,50 | 1,70 |
| escoria_viviente *(Forja)* | 0,45 | 0,40 | 0,50 | 0,50 | 0,65 | 1,00 | 0,50 | 1,00 | 0,69 | **0,37** | 1,80 | 0,90 |
| yunque_andante *(Forja)* | 3,50 | 4,50 | 2,95 | 2,80 | 4,90 | 1,85 | 2,50 | 1,00 | **0,76** | 3,68 | 2,00 | 2,70 |
| percutor *(Forja)* | 2,45 | 2,40 | 2,50 | 2,50 | 2,50 | 2,85 | 2,50 | 2,50 | **1,97** | 2,50 | 4,50 | 3,60 |
| tenaza *(Forja)* | 0,90 | 0,90 | 0,85 | 0,80 | 1,50 | 1,00 | 1,00 | 1,00 | **0,54** | 1,00 | 1,30 | 1,70 |
| cargador_de_carbon *(Forja)* | 0,90 | 0,90 | 0,95 | 1,00 | 1,50 | 1,50 | 1,00 | 1,00 | **0,56** | 1,00 | 1,50 | 1,80 |
| templador *(Forja)* | 0,50 | 0,50 | 0,50 | 0,50 | 1,00 | 1,00 | 0,50 | 0,80 | **0,40** | 0,80 | 1,00 | 0,90 |
| nucleo_estelar *(Forja)* | — | — | — | — | — | — | — | — | — | — | — | — |
| molde_roto *(Forja)* | 1,45 | 1,60 | **1,00** | **1,00** | 2,50 | 2,00 | 1,50 | 1,80 | 1,07 | 1,25 | 2,30 | 2,70 |
| guardian_de_cuno *(Forja)* | 10,00 | 12,00 | 13,30 | 12,60 | 18,20 | 11,75 | 11,05 | **3,76** | 4,07 | 12,51 | 62,20 | 56,80 |

## Tiempo para matar (s), mejores armas al 0 %

En negrita el tipo más rápido contra ese mob. "—": no se le puede hacer daño así (sellos, absorción). "> 120": no muere en dos minutos.

| Mob | espada | daga | espadon | hacha | lanza | mazo | tridente | mangual | guanteletes | guadana | baculo | grimorio |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| zombie | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| husk | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| drowned | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| zombie_villager | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| skeleton | 0,50 | 0,50 | 0,50 | 0,50 | 1,00 | 0,50 | 0,80 | 0,50 | **0,25** | 0,50 | 0,50 | 1,00 |
| stray | 0,50 | 0,50 | 0,50 | 0,50 | 1,00 | 0,50 | 0,80 | 0,50 | **0,25** | 0,50 | 0,50 | 1,00 |
| bogged | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,50 | 1,00 |
| wither_skeleton | 0,50 | 0,50 | 0,50 | 0,50 | 1,00 | 0,50 | 0,75 | 0,50 | **0,25** | 0,50 | 0,50 | 1,00 |
| creeper | **0,50** | **0,50** | **0,50** | **0,50** | 0,95 | 0,85 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| spider | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,50 | 1,00 |
| cave_spider | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,50 | 0,95 |
| pillager | **0,50** | 0,80 | 0,85 | 0,80 | 1,00 | 1,00 | 0,75 | 0,80 | **0,50** | 0,80 | 1,00 | 1,00 |
| vindicator | **0,50** | 0,80 | 0,85 | 0,80 | 1,00 | 1,00 | 0,75 | 0,80 | **0,50** | 0,80 | 1,00 | 1,00 |
| evoker | **0,50** | 0,80 | 0,85 | 0,80 | 1,00 | 1,00 | 0,75 | 0,80 | **0,50** | 0,80 | 1,00 | 1,00 |
| witch | 0,55 | 0,80 | 0,85 | 0,80 | 1,00 | 1,00 | 0,75 | 0,80 | **0,50** | 0,80 | 3,00 | 4,45 |
| enderman | 1,10 | 1,35 | **1,00** | **1,00** | 1,85 | 1,50 | 1,50 | 1,50 | **1,00** | 1,50 | 2,00 | 2,00 |
| blaze | **0,50** | **0,50** | **0,50** | **0,50** | 0,95 | 0,85 | **0,50** | **0,50** | **0,50** | **0,50** | 1,00 | 1,00 |
| piglin_brute | 1,50 | 1,50 | 1,50 | 1,50 | 2,50 | 2,50 | 1,50 | 1,50 | **1,25** | 1,50 | 2,50 | 3,00 |
| herrero_caido *(Forja)* | 74,25 | 76,00 | 99,00 | 92,50 | > 120 | > 120 | 92,25 | 105,00 | **54,75** | 97,00 | > 120 | > 120 |
| automata_de_forja *(Forja)* | 5,50 | 6,00 | 6,55 | 5,60 | 13,00 | 3,70 | 7,20 | 2,50 | **2,25** | 6,30 | 4,00 | 4,00 |
| coraza_vacia *(Forja)* | 2,10 | 6,50 | 7,00 | 6,40 | 7,50 | 1,50 | 5,60 | 1,50 | **1,25** | 6,95 | 47,20 | 8,95 |
| pavesa *(Forja)* | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | 0,50 | **0,25** | 0,50 | 0,50 | 0,95 |
| herrumbre *(Forja)* | 0,50 | 0,40 | 0,50 | 0,50 | 0,50 | **0,00** | 0,50 | **0,00** | **0,00** | 0,50 | 0,50 | 0,45 |
| ascua_mayor *(Forja)* | 1,00 | 1,00 | 1,00 | 1,00 | 1,50 | 1,50 | 1,00 | 1,00 | **0,75** | 1,00 | 2,00 | 2,00 |
| escoria_viviente *(Forja)* | 0,50 | **0,40** | 0,50 | 0,50 | 1,00 | 1,85 | 0,50 | 1,50 | 1,00 | 0,50 | 2,50 | 1,00 |
| yunque_andante *(Forja)* | 5,50 | 6,00 | 6,55 | 5,70 | 7,00 | 2,50 | 3,50 | 1,80 | **1,50** | 6,30 | 3,00 | 3,00 |
| percutor *(Forja)* | 3,00 | 3,50 | **2,50** | **2,50** | 6,55 | 8,20 | **2,50** | 4,50 | 4,00 | **2,50** | 62,20 | 6,45 |
| tenaza *(Forja)* | 1,10 | 1,35 | **1,00** | **1,00** | 1,50 | 1,50 | **1,00** | 1,50 | **1,00** | **1,00** | 2,00 | 2,00 |
| cargador_de_carbon *(Forja)* | 1,20 | 1,50 | 1,50 | 1,50 | 1,95 | 1,85 | 1,50 | 1,50 | **1,00** | 1,50 | 2,50 | 2,45 |
| templador *(Forja)* | 1,00 | 0,90 | 0,85 | 0,80 | 1,00 | 1,00 | 0,75 | 1,00 | **0,50** | 0,80 | 1,50 | 1,00 |
| nucleo_estelar *(Forja)* | — | — | — | — | — | — | — | — | — | — | — | — |
| molde_roto *(Forja)* | 2,10 | 2,00 | 1,85 | 1,80 | 2,50 | 2,50 | 2,50 | 1,80 | **1,50** | 2,50 | 3,00 | 3,00 |
| guardian_de_cuno *(Forja)* | 14,30 | 15,00 | 19,00 | 16,80 | 24,00 | 16,50 | 15,20 | 10,50 | **6,75** | 17,60 | > 120 | 59,45 |

### Comprobación: pelea simulada contra vida ÷ daño sostenido

Para cada pelea de 2 s o más al 100 %, el TTK simulado dividido por la cuenta simple (vida máxima entre el daño por segundo sostenido contra ese mismo mob, con el mismo ritmo). Cerca de 1: la pelea es su daño sostenido. Por debajo: la pelea mata antes de lo que dice su daño sostenido (los primeros golpes van con la estamina llena, un aturdido temprano, Ejecución por debajo del 30 %). Por encima: pausas que la cuenta no ve (la finta de la coraza, esperar a la estamina). Las peleas más cortas no entran: con 2 o 3 golpes el primero cae en el segundo 0 y la cuenta no tiene sentido.

| Tipo | Peleas | Mediana | Rango | La más lejos de 1 |
|---|---|---|---|---|
| espada | 5 | ×0,86 | ×0,67 – ×1,68 | coraza_vacia (4,9 s simulado, 2,9 s estimado) |
| daga | 3 | ×0,96 | ×0,89 – ×1,98 | coraza_vacia (4,5 s simulado, 2,3 s estimado) |
| espadon | 5 | ×0,75 | ×0,61 – ×1,40 | automata_de_forja (2,4 s simulado, 3,9 s estimado) |
| hacha | 5 | ×0,80 | ×0,63 – ×1,62 | coraza_vacia (4,6 s simulado, 2,8 s estimado) |
| lanza | 6 | ×0,81 | ×0,63 – ×1,46 | yunque_andante (2,2 s simulado, 3,5 s estimado) |
| mazo | 4 | ×0,97 | ×0,26 – ×1,30 | guardian_de_cuno (2,5 s simulado, 9,4 s estimado) |
| tridente | 4 | ×0,95 | ×0,38 – ×2,20 | automata_de_forja (2,5 s simulado, 6,6 s estimado) |
| mangual | 4 | ×0,96 | ×0,40 – ×2,19 | guardian_de_cuno (2,4 s simulado, 6,0 s estimado) |
| guanteletes | 1 | ×1,01 | ×1,01 – ×1,01 | herrero_caido (23,9 s simulado, 23,7 s estimado) |
| guadana | 5 | ×0,77 | ×0,63 – ×1,45 | automata_de_forja (2,2 s simulado, 3,5 s estimado) |
| baculo | 2 | ×1,66 | ×0,08 – ×1,66 | guardian_de_cuno (2,8 s simulado, 34,8 s estimado) |
| grimorio | 4 | ×0,16 | ×0,13 – ×0,43 | automata_de_forja (2,0 s simulado, 15,3 s estimado) |

## Dificultad: factores sobre HERRERO

Cada preset multiplica la vida de los monstruos, el tope por golpe y la barra de postura (`ForjaDifficulty`). Aquí, el TTK medio de la mejor arma al 100 % contra **todos** los mobs bajo cada preset, dividido por el de HERRERO.

| Tipo | TTK medio HERRERO (s) | APRENDIZ (vida ×0,8, tope ×1,40) | HERRERO (vida ×1,0, tope ×1,00) | MAESTRO (vida ×1,3, tope ×0,85) | LEYENDA (vida ×1,7, tope ×0,70) |
|---|---|---|---|---|---|
| espada | 0,57 | ×0,53 | ×1,00 | ×1,21 | ×1,43 |
| daga | 0,44 | ×0,66 | ×1,00 | ×1,22 | ×1,73 |
| espadon | 0,69 | ×0,47 | ×1,00 | ×1,17 | ×1,37 |
| hacha | 0,61 | ×0,54 | ×1,00 | ×1,20 | ×1,39 |
| lanza | 0,84 | ×0,62 | ×1,00 | ×1,21 | ×1,53 |
| mazo | 0,59 | ×0,58 | ×1,00 | ×1,37 | ×1,52 |
| tridente | 0,71 | ×0,53 | ×1,00 | ×1,18 | ×1,40 |
| mangual | 0,54 | ×0,59 | ×1,00 | ×1,36 | ×1,56 |
| guanteletes | 0,20 | ×0,65 | ×1,00 | ×1,36 | ×1,90 |
| guadana | 0,70 | ×0,47 | ×1,00 | ×1,15 | ×1,35 |
| baculo | 0,69 | ×0,74 | ×1,00 | ×1,21 | ×1,77 |
| grimorio | 1,16 | ×0,61 | ×1,00 | ×1,27 | ×1,57 |

## Materiales

### Dominados por hueco (Pareto, con el mismo rasgo)

Un material dominado es igual o peor en todo lo que ese hueco usa que otro con el mismo rasgo: en ese hueco, nunca hay razón para elegirlo (fuera de coste y disponibilidad, que aquí no se miden).

| Pieza | Sobreviven | Dominados (→ por cuál) |
|---|---|---|
| hoja | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| mango | oro, amatista, obsidiana, netherita, esmeralda, prismarina, vara_de_blaze, cuarzo, purpur, eco, resina, corazon, peltre, electro, damasco, acero_estelar, obsidiacero, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → hueso, piedra → madera, hueso → peltre, cuero → hueso, cobre → hueso, hierro → netherita, diamante → netherita, obsidiana_llorona → corazon, bronce → hueso, laton → amatista, acero → diamante, estelar → acero_estelar, escoria → vara_de_blaze |
| guarda | netherita, esmeralda, prismarina, vara_de_blaze, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cuero → piedra, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar, escoria → vara_de_blaze |
| cabeza_hacha | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| atadura | netherita, esmeralda, prismarina, vara_de_blaze, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cuero → piedra, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar, escoria → vara_de_blaze |
| punta_lanza | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| cabeza_mazo | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| punta_tridente | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| bola | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| cadena | netherita, esmeralda, prismarina, vara_de_blaze, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cuero → piedra, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar, escoria → vara_de_blaze |
| manopla | oro, amatista, obsidiana, netherita, esmeralda, prismarina, vara_de_blaze, cuarzo, purpur, eco, resina, corazon, peltre, electro, damasco, acero_estelar, obsidiacero, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → hueso, piedra → madera, hueso → peltre, cuero → hueso, cobre → hueso, hierro → netherita, diamante → netherita, obsidiana_llorona → corazon, bronce → hueso, laton → amatista, acero → diamante, estelar → acero_estelar, escoria → vara_de_blaze |
| nudillos | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| remache | netherita, esmeralda, prismarina, vara_de_blaze, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cuero → piedra, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar, escoria → vara_de_blaze |
| nucleo | netherita, esmeralda, prismarina, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, escoria, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar |
| engaste | netherita, esmeralda, prismarina, vara_de_blaze, purpur, eco, resina, corazon, damasco, acero_estelar, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → piedra, piedra → hueso, hueso → cobre, cuero → piedra, cobre → hierro, hierro → amatista, oro → madera, amatista → diamante, diamante → netherita, obsidiana → diamante, cuarzo → damasco, obsidiana_llorona → corazon, bronce → amatista, laton → hierro, peltre → hierro, acero → diamante, electro → esmeralda, obsidiacero → netherita, estelar → acero_estelar, escoria → vara_de_blaze |
| tapas | oro, amatista, obsidiana, netherita, esmeralda, prismarina, vara_de_blaze, cuarzo, purpur, eco, resina, corazon, peltre, electro, damasco, acero_estelar, obsidiacero, hueco, escama, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero_vivo, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, iracero, egida, arcanio | madera → hueso, piedra → madera, hueso → peltre, cuero → hueso, cobre → hueso, hierro → netherita, diamante → netherita, obsidiana_llorona → corazon, bronce → hueso, laton → amatista, acero → diamante, estelar → acero_estelar, escoria → vara_de_blaze |

**Dominados en todos los huecos de arma en que caben** (para armas no hay razón para usarlos): madera, piedra, hueso, cuero, cobre, hierro, diamante, obsidiana_llorona, bronce, laton, acero, estelar.

**Nunca en ninguna de las 5 mejores combinaciones de ningún tipo de arma, ni en ninguna mejor arma:** madera, piedra, hueso, cuero, cobre, hierro, oro, amatista, diamante, obsidiana, netherita, esmeralda, purpur, obsidiana_llorona, resina, bronce, laton, peltre, acero, electro, acero_estelar, obsidiacero, estelar, hueco, escama, cinerio, voltaico, almacero, solacero, lunacero, acero_vivo, escoria, oricalco, fatuo, magmacero, eterio, espectracero, corazon_de_volcan, eclipse, astralita, egida. (Para armas; muchos son materiales de armadura, herramienta o principio de partida.)

### Un material de más nivel peor en todo que uno de menos

Nivel = lo que puede minar (`incorrectBlocksForDrops`): madera y oro 0, piedra 1, cobre 1,5, hierro 2, diamante 3, netherita 4. Se compara cada pieza en todas las líneas de su ficha (`ForgeStats.partLines`).

**Fallos claros** (los dos materiales sin rasgo y de los que pueden ser cabeza, en una cabeza o una placa, que es donde el nivel importa, y con dos líneas de ficha o más; esto es lo que vigila la prueba `equilibrioSinDominados`): ninguno.

**En piezas de arma, contando rasgos y mangos** (el rasgo puede ser el precio y un mango es cosa de peso; no son fallos por sí solos, pero merecen una mirada): 

- cobre (1,5) peor que cuarzo (1,0): cabeza_hacha, hoja, punta_lanza, cabeza_mazo
- cobre (1,5) peor que resina (1,0): hoja, punta_lanza, cabeza_mazo
- voltaico (3,0) peor que esmeralda (2,0): hoja, punta_lanza, cabeza_mazo
- piedra (1,0) peor que madera (0,0): mango

## Aleaciones de fragua frente a la netherita

Las aleaciones que solo funde una fragua lejana (docs/ALEACIONES_NETHER_END.md). *¿Netherita mejor?*: igual o mejor que la netherita a la vez en daño de cabeza, durabilidad, armadura del conjunto y dureza (la prueba `aleacionesDeFraguaEnSuSitio` lo prohíbe, y que estén en más de la mitad de las mejores armas). *En las mejores armas*: en cuántas de las mejores armas de cada tipo y escenario, con y sin pactos, entra alguna pieza suya.

| Material | Fragua | Rasgo | Daño de cabeza | Durabilidad | Armadura del conjunto | Dureza | Ataque de mango | En las mejores armas | ¿Netherita mejor? |
|---|---|---|---|---|---|---|---|---|---|
| netherita (referencia) | — | — | 4,0 | 2031 | 20 | 3,0 | 0,00 | 0 de 60 | — |
| fatuo | almas | espectral | 3,0 | 1000 | 19 | 2,0 | 0,10 | 0 de 60 | no |
| magmacero | almas | volcanico | 3,0 | 1600 | 20 | 2,5 | -0,20 | 0 de 60 | no |
| eterio | vacio | flotante | 3,0 | 1200 | 18 | 1,5 | 0,20 | 0 de 60 | no |
| espectracero | almas | amparo | 3,5 | 2000 | 20 | 3,0 | -0,10 | 0 de 60 | no |
| corazon_de_volcan | almas | ardor | 4,0 | 1800 | 20 | 2,5 | 0,05 | 0 de 60 | no |
| eclipse | vacio | penumbra | 3,0 | 1500 | 19 | 2,0 | 0,20 | 0 de 60 | no |

## Mejoras de daño

### Lo que vale cada una sola, al 100 %, sobre la mejor combinación de materiales

Cambio del TTK medio al añadirla sola (negativo es mejor), y entre paréntesis por punto de carga. "·" = no se puede poner en ese tipo.

| Mejora (peso) | espada | daga | espadon | hacha | lanza | mazo | tridente | mangual | guanteletes | guadana | baculo | grimorio |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| filo (4) | −17 % (−4 %) | −22 % (−6 %) | −10 % (−2 %) | −9 % (−2 %) | −27 % (−7 %) | −27 % (−7 %) | −12 % (−3 %) | −14 % (−3 %) | −31 % (−8 %) | −13 % (−3 %) | −14 % (−3 %) | −15 % (−4 %) |
| castigo (2) | +0 % (+0 %) | −5 % (−3 %) | +0 % (+0 %) | +0 % (+0 %) | −16 % (−8 %) | −8 % (−4 %) | −6 % (−3 %) | +0 % (+0 %) | −8 % (−4 %) | +0 % (+0 %) | −8 % (−4 %) | −10 % (−5 %) |
| perdicion_de_artropodos (1) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | −9 % (−9 %) |
| brecha (2) | −11 % (−5 %) | −9 % (−5 %) | −13 % (−6 %) | −8 % (−4 %) | −10 % (−5 %) | −11 % (−5 %) | −7 % (−4 %) | −8 % (−4 %) | −6 % (−3 %) | −7 % (−4 %) | −3 % (−2 %) | −1 % (−1 %) |
| densidad (3) | · | · | · | · | · | +0 % (+0 %) | · | · | · | · | · | · |
| aspecto_igneo (2) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) |
| critico (3) | −9 % (−3 %) | −10 % (−3 %) | −8 % (−3 %) | −6 % (−2 %) | −6 % (−2 %) | −9 % (−3 %) | −7 % (−2 %) | −8 % (−3 %) | −12 % (−4 %) | −13 % (−4 %) | +0 % (+0 %) | −2 % (−1 %) |
| frenesi (3) | −13 % (−4 %) | −5 % (−2 %) | −15 % (−5 %) | −13 % (−4 %) | −19 % (−6 %) | −12 % (−4 %) | −16 % (−5 %) | −13 % (−4 %) | −17 % (−6 %) | −14 % (−5 %) | +0 % (+0 %) | +0 % (+0 %) |
| tormenta (3) | −8 % (−3 %) | −9 % (−3 %) | −5 % (−2 %) | −4 % (−1 %) | −11 % (−4 %) | −5 % (−2 %) | −6 % (−2 %) | −5 % (−2 %) | −15 % (−5 %) | −4 % (−1 %) | −9 % (−3 %) | −6 % (−2 %) |
| ejecucion (2) | −10 % (−5 %) | −14 % (−7 %) | −13 % (−7 %) | −15 % (−8 %) | −12 % (−6 %) | −12 % (−6 %) | −15 % (−8 %) | −14 % (−7 %) | −11 % (−6 %) | −11 % (−6 %) | +0 % (+0 %) | −5 % (−2 %) |
| matagigantes (2) | −16 % (−8 %) | −20 % (−10 %) | −22 % (−11 %) | −24 % (−12 %) | −30 % (−15 %) | −35 % (−17 %) | −20 % (−10 %) | −22 % (−11 %) | −32 % (−16 %) | −25 % (−12 %) | −7 % (−4 %) | −10 % (−5 %) |
| veneno (2) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | +0 % (+0 %) | −2 % (−1 %) | +0 % (+0 %) | +0 % (+0 %) | −8 % (−4 %) | +0 % (+0 %) |
| desgarro (3) | · | +0 % (+0 %) | · | · | · | · | · | · | · | +0 % (+0 %) | · | · |
| nudillos_de_hierro (3) | · | · | · | · | · | · | · | · | −15 % (−5 %) | · | · | · |
| rafaga (2) | · | · | · | · | · | · | · | · | −25 % (−13 %) | · | · | · |
| conjuro_veloz (3) | · | · | · | · | · | · | · | · | · | · | −18 % (−6 %) | −10 % (−3 %) |
| sobrecarga (3) | · | · | · | · | · | · | · | · | · | · | −3 % (−1 %) | −5 % (−2 %) |
| resonancia (4) | · | · | · | · | · | · | · | · | · | · | −45 % (−11 %) | −20 % (−5 %) |

### Programación dinámica contra todos los conjuntos peleados

La programación dinámica es exacta para valores que se suman; en una pelea no se suman (el tope, la invulnerabilidad y el ablandado de los extras cortan), así que se pelean todos los conjuntos legales y se queda el mejor.

| Tipo | Escenario | Carga | Conjuntos | Elige la PD | TTK (s) | Mejor peleado | TTK (s) | Diferencia |
|---|---|---|---|---|---|---|---|---|
| espada | 50 % | 7 | 144 | frenesi, matagigantes, brecha | 1,12 | frenesi, matagigantes, brecha | 1,12 | +0 % |
| espada | 100 % | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,73 | tormenta, critico, frenesi, ejecucion, matagigantes, filo, aspecto_igneo | 0,72 | +1 % |
| espada | 50 % con pactos | 12 | 470 | critico, frenesi, ejecucion, matagigantes, brecha | 0,71 | tormenta, frenesi, ejecucion, matagigantes, brecha | 0,71 | +1 % |
| espada | 100 % con pactos | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,60 | tormenta, critico, frenesi, ejecucion, matagigantes, brecha | 0,56 | +6 % |
| daga | 50 % | 7 | 177 | critico, ejecucion, matagigantes | 1,20 | matagigantes, filo | 1,12 | +7 % |
| daga | 100 % | 20 | 1268 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,67 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,67 | +0 % |
| daga | 50 % con pactos | 12 | 739 | critico, frenesi, ejecucion, matagigantes, brecha | 0,67 | frenesi, ejecucion, matagigantes, filo | 0,66 | +1 % |
| daga | 100 % con pactos | 20 | 1268 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,52 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,52 | +0 % |
| espadon | 50 % | 7 | 144 | frenesi, ejecucion, matagigantes | 1,14 | ejecucion, matagigantes, brecha | 1,12 | +2 % |
| espadon | 100 % | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, brecha | 0,78 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,78 | +0 % |
| espadon | 50 % con pactos | 12 | 470 | critico, frenesi, ejecucion, matagigantes | 0,81 | tormenta, critico, ejecucion, matagigantes, brecha | 0,69 | +17 % |
| espadon | 100 % con pactos | 20 | 639 | critico, frenesi, ejecucion, matagigantes, filo | 0,69 | tormenta, critico, ejecucion, matagigantes, brecha | 0,59 | +16 % |
| hacha | 50 % | 7 | 144 | frenesi, ejecucion, matagigantes | 1,14 | ejecucion, matagigantes, brecha | 1,10 | +3 % |
| hacha | 100 % | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,76 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,76 | +0 % |
| hacha | 50 % con pactos | 12 | 470 | critico, frenesi, ejecucion, matagigantes | 0,77 | tormenta, critico, ejecucion, matagigantes, brecha | 0,67 | +14 % |
| hacha | 100 % con pactos | 20 | 639 | critico, frenesi, ejecucion, matagigantes, filo | 0,68 | tormenta, critico, ejecucion, matagigantes, brecha | 0,62 | +11 % |
| lanza | 50 % | 7 | 144 | frenesi, matagigantes, castigo | 1,62 | frenesi, matagigantes, castigo | 1,62 | +0 % |
| lanza | 100 % | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 1,12 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 1,12 | +0 % |
| lanza | 50 % con pactos | 12 | 470 | frenesi, ejecucion, matagigantes, filo | 0,98 | frenesi, ejecucion, matagigantes, filo | 0,98 | +0 % |
| lanza | 100 % con pactos | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,80 | critico, frenesi, ejecucion, matagigantes, filo | 0,80 | +0 % |
| mazo | 50 % | 7 | 158 | frenesi, matagigantes, castigo | 1,16 | ejecucion, filo | 1,15 | +1 % |
| mazo | 100 % | 20 | 767 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,74 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,74 | +0 % |
| mazo | 50 % con pactos | 12 | 547 | critico, frenesi, ejecucion, matagigantes, brecha | 0,78 | critico, frenesi, ejecucion, matagigantes, brecha | 0,78 | +0 % |
| mazo | 100 % con pactos | 20 | 767 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,60 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,60 | +0 % |
| tridente | 50 % | 7 | 144 | frenesi, ejecucion, matagigantes | 1,22 | frenesi, matagigantes, castigo | 1,19 | +2 % |
| tridente | 100 % | 20 | 639 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,80 | tormenta, critico, frenesi, ejecucion, matagigantes, filo, aspecto_igneo | 0,79 | +1 % |
| tridente | 50 % con pactos | 12 | 470 | critico, frenesi, ejecucion, matagigantes, brecha | 0,72 | tormenta, frenesi, ejecucion, matagigantes, perdicion_de_artropodos | 0,70 | +2 % |
| tridente | 100 % con pactos | 20 | 639 | tormenta, veneno, critico, frenesi, ejecucion, matagigantes, brecha | 0,66 | tormenta, critico, frenesi, ejecucion, matagigantes, perdicion_de_artropodos | 0,65 | +1 % |
| mangual | 50 % | 7 | 144 | frenesi, ejecucion, matagigantes | 0,95 | critico, ejecucion, matagigantes | 0,90 | +5 % |
| mangual | 100 % | 20 | 639 | tormenta, veneno, critico, frenesi, ejecucion, matagigantes, filo | 0,65 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,65 | +0 % |
| mangual | 50 % con pactos | 12 | 470 | frenesi, ejecucion, matagigantes, filo | 0,63 | critico, frenesi, ejecucion, matagigantes, brecha | 0,59 | +7 % |
| mangual | 100 % con pactos | 20 | 639 | critico, frenesi, ejecucion, matagigantes, filo | 0,58 | tormenta, critico, frenesi, ejecucion, matagigantes, aspecto_igneo | 0,53 | +11 % |
| guanteletes | 50 % | 7 | 242 | matagigantes, rafaga, nudillos_de_hierro | 0,54 | matagigantes, rafaga, nudillos_de_hierro | 0,54 | +0 % |
| guanteletes | 100 % | 20 | 2493 | tormenta, critico, frenesi, matagigantes, filo, rafaga, nudillos_de_hierro | 0,24 | critico, frenesi, ejecucion, matagigantes, filo, rafaga, nudillos_de_hierro | 0,24 | +2 % |
| guanteletes | 50 % con pactos | 12 | 1216 | critico, ejecucion, matagigantes, rafaga, nudillos_de_hierro | 0,33 | tormenta, ejecucion, brecha, rafaga, nudillos_de_hierro | 0,28 | +16 % |
| guanteletes | 100 % con pactos | 20 | 2493 | critico, frenesi, ejecucion, matagigantes, filo, rafaga, nudillos_de_hierro | 0,18 | tormenta, critico, frenesi, ejecucion, matagigantes, brecha, rafaga, nudillos_de_hierro | 0,15 | +14 % |
| guadana | 50 % | 7 | 177 | frenesi, ejecucion, matagigantes | 1,24 | critico, matagigantes, brecha | 1,15 | +8 % |
| guadana | 100 % | 20 | 1268 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,77 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,77 | +0 % |
| guadana | 50 % con pactos | 12 | 739 | critico, frenesi, ejecucion, matagigantes, brecha | 0,75 | critico, frenesi, ejecucion, matagigantes, brecha | 0,75 | +0 % |
| guadana | 100 % con pactos | 20 | 1268 | tormenta, critico, frenesi, ejecucion, matagigantes, filo | 0,62 | tormenta, critico, frenesi, ejecucion, matagigantes, brecha | 0,60 | +2 % |
| baculo | 50 % | 7 | 229 | conjuro_veloz, resonancia | 1,28 | castigo, resonancia | 1,25 | +2 % |
| baculo | 100 % | 20 | 4440 | tormenta, veneno, matagigantes, filo, conjuro_veloz, resonancia | 0,73 | tormenta, ejecucion, matagigantes, filo, conjuro_veloz, resonancia | 0,72 | +1 % |
| baculo | 50 % con pactos | 12 | 1417 | veneno, castigo, conjuro_veloz, resonancia | 1,08 | ejecucion, matagigantes, filo, resonancia | 1,00 | +9 % |
| baculo | 100 % con pactos | 20 | 4440 | tormenta, veneno, matagigantes, filo, conjuro_veloz, resonancia | 0,73 | tormenta, ejecucion, matagigantes, filo, conjuro_veloz, resonancia | 0,72 | +1 % |
| grimorio | 50 % | 7 | 229 | matagigantes, perdicion_de_artropodos, resonancia | 1,76 | conjuro_veloz, resonancia | 1,71 | +3 % |
| grimorio | 100 % | 20 | 4440 | tormenta, matagigantes, filo, conjuro_veloz, sobrecarga, resonancia | 1,23 | tormenta, ejecucion, matagigantes, filo, resonancia | 1,21 | +2 % |
| grimorio | 50 % con pactos | 12 | 1417 | ejecucion, matagigantes, perdicion_de_artropodos, conjuro_veloz, resonancia | 1,42 | ejecucion, matagigantes, perdicion_de_artropodos, conjuro_veloz, resonancia | 1,42 | +0 % |
| grimorio | 100 % con pactos | 20 | 4440 | tormenta, matagigantes, filo, conjuro_veloz, sobrecarga, resonancia | 1,23 | tormenta, ejecucion, matagigantes, filo, resonancia | 1,21 | +2 % |

La PD acierta (a menos de un 0,5 %) en 17 de 48 casos.

**Veces que entra en una mejor arma** (48 armas con mejoras): filo ×22, castigo ×3, perdicion_de_artropodos ×3, brecha ×16, aspecto_igneo ×3, critico ×27, frenesi ×28, tormenta ×27, ejecucion ×40, matagigantes ×44, nudillos_de_hierro ×4, rafaga ×4, conjuro_veloz ×4, resonancia ×8.

**Mejoras de daño que no entran en ninguna mejor arma:** densidad, veneno, desgarro, sobrecarga. Contra un solo mob quieto nunca compensan lo que pesan. Densidad sólo pega cayendo; Sobrecarga es un hechizo de cada 4; el daño en el tiempo (Veneno, Aspecto ígneo) apenas llega antes de que el mob muera y la invulnerabilidad se traga sus puntos sueltos.

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

1. **guanteletes es la más rápida al 100 % (0,24 s).** Porqué: 7,6 de daño a 4,11 golpes/s; ritmo cada 4 ticks; 97 % de los golpes cansado; 1,0 aturdidos por pelea; Afilado +3 en cada golpe.
2. **grimorio es la más lenta al 100 % (1,19 s).** Porqué: 0,4 aturdidos por pelea; Afilado +3 en cada golpe; hechizo de 10,3 cada 20 ticks mientras dura el maná, sin estamina y atravesando armadura.
3. **La mejora que más rinde por punto de carga: matagigantes en mazo** (17 % menos de TTK por punto). Porqué: el frenesí la multiplica por su techo (×2,5 las de un solo ingrediente) en cuanto se encadenan 5 golpes, y pesa poco.
4. **Contra automata_de_forja *(Forja)*, guanteletes mata en 0,7 s y la mediana de los tipos en 2,2 s.** Porqué: cada punto de su golpe le quita 0,76; el de las armas cuerpo a cuerpo, 0,49 a 0,76; 7,6 de daño a 4,11 golpes/s; ritmo cada 4 ticks; 97 % de los golpes cansado; 1,0 aturdidos por pelea; Afilado +3 en cada golpe.
5. **Contra coraza_vacia *(Forja)*, guanteletes mata en 1,9 s y la mediana de los tipos en 4,5 s.** Porqué: cada punto de su golpe le quita 0,95; el de las armas cuerpo a cuerpo, 0,48 a 0,95; 7,6 de daño a 4,11 golpes/s; ritmo cada 4 ticks; 97 % de los golpes cansado; 1,0 aturdidos por pelea; Afilado +3 en cada golpe.
6. **Contra guardian_de_cuno *(Forja)*, guanteletes mata en 1,4 s y la mediana de los tipos en 5,5 s.** Porqué: cada punto de su golpe le quita 0,76; el de las armas cuerpo a cuerpo, 0,49 a 0,76; 7,6 de daño a 4,11 golpes/s; ritmo cada 4 ticks; 97 % de los golpes cansado; 1,0 aturdidos por pelea; Afilado +3 en cada golpe.
7. **Los pactos en guanteletes al 50 %: TTK −46 %.** Porqué: +32 % y +40 % de daño al atributo, peso 0, sin techo, y +20 de potencial que sube el techo de las demás de 50 a 70 % y la carga de 7 a 12. Lo que cuestan (hambre, durabilidad) no es daño.

## Recomendaciones

Sólo propuestas: ningún número se ha tocado. Cada una sale de una medida de arriba; decide Andy.

1. **Estamina**: golpear sin estamina sigue saliendo a cuenta (×0,6). Si la estamina tiene que marcar el ritmo, bajar `tiredDamageMultiplier` (0,3–0,4) o no dejar atacar a 0, o que la estamina vuelva también mientras se ataca despacio.
2. **Invulnerabilidad y extras**: hoy cada extra de mejora deja el "último daño" en el suyo, pequeño, y el siguiente golpe rápido entra casi entero; sin extras, por encima de 2 golpes/s el golpe sólo quita lo que supera al anterior. O se acepta y se documenta, o `extraDamage` guarda y restaura `invulnerableTime` y `lastHurt` del objetivo.
3. **Afilado**: que cuente sólo en la cabeza o la hoja (no en atadura ni guarda), o que sea un porcentaje del golpe en vez de +3 plano, que pesa mucho más en armas rápidas.
4. **Vidriacero**: su +0,3 de velocidad por Diáfano se suma a un mango que ya es el más rápido; que Diáfano sólo quite peso en armadura, o bajar su `handleAttackSpeed`.
5. **Mestizaje**: o se aplica en `Assembler.write` (y entonces se nota en el daño) o se quita de la ficha; hoy promete un número que el arma no tiene.
6. **Frenesí**: que sea un porcentaje de la velocidad del arma (p. ej. +38 %, lo que hoy es para una espada) en vez de +0,6 plano, que dobla el mazo.
7. **Pactos**: que pesen (p. ej. 2) o que no sumen potencial; hoy dan daño, techo y carga a la vez, y su coste no es de combate.
8. **Tope y extras**: los extras son el 39 % del daño y cada uno lleva su propio tope. Si el tope ha de decir "nada muere de un golpe", que los extras de un golpe cuenten contra el mismo tope (sumarlos en `CombatUpgrades` antes de `capped`).
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
