# Rendimiento del servidor con muchos mobs

Medido el 26-09-2026 sobre el código de Forja del commit `4c8e822` (antes del castillo del Bastión, de los
arcos forjados de los esqueletos, de los mobs con báculo y del bloque de observaciones `ObsV3`; `ObsV3` solo
corre para redes que piden más de 200 entradas, y las entrenadas hoy piden 200).

## En pocas palabras

- **50 mobs luchando a la vez cuestan 1,5 ms por tick con las reglas y 2,7 ms con las redes** (media; p95
  2,4 y 4,7 ms). El presupuesto de un tick son 50 ms: sobra muchísimo en esta máquina (Ryzen 7 9800X3D).
  En un servidor con la mitad o un tercio de velocidad por hilo seguiría por debajo de 10 ms.
- Con **reglas**, la IA de Forja (cerebros, escuadra, tácticas, especiales) es poca cosa: unos **0,1 ms por
  tick** con 50 mobs. Lo que más pesa de Forja son los propios mobs del mod (su cuerpo vanilla, su IA propia
  y sus partículas), no los cerebros.
- Con **redes**, los cerebros eran lo más caro de todo el tick: **1,09 ms por tick** con 50 mobs. Tras las
  optimizaciones de abajo son **0,64 ms (−41 %)**, con **las mismas decisiones bit a bit** (probado).
- Lo siguiente más caro que pide Forja es **la búsqueda de caminos de `TacticGoal`** (0,35-0,5 ms/tick
  con redes). Bajarla cambia el comportamiento, así que no lo he tocado: está medido abajo para que decidas.
- **Partículas**: unos **55-60 paquetes por tick a cada jugador cercano** con 50 mobs (≈ 1.200 por segundo).
  Casi todos salen de los mobs de Forja, no de la IA; el aceite del Templador ya es el 40 %.

## Cómo repetirlo

```
cd forja
tools/rendimiento.sh <nombre> [carpeta con red_<familia>.json]
```

Tarda alrededor de un minuto (compilar incluido). Deja en `build/rendimiento/<nombre>/`:

- `informe.md`: todas las cifras de cada escenario (lo de este documento sale de ahí);
- `resumen.csv`: una línea por escenario, para comparar pasadas (se van añadiendo, no se borra);
- un `.jfr` por escenario, que se abre con JDK Mission Control.

Por dentro es la prueba `BenchmarkGameTests` (en `src/gametest`), que solo trabaja si existe la variable
`FORJA_RENDIMIENTO` (la carpeta del informe); en la batería normal pasa al instante. Tiene su propio entorno
de pruebas (`forja-test:rendimiento`), así que corre en un lote aparte y nunca comparte el servidor con otra
prueba. El script además filtra para que solo corra ella. Con `FORJA_REDES` apuntando a una carpeta de redes
mide también los escenarios con redes (las que usé: copia de `E:\IA\agente\minecraft\combate\forja_mobs_f2`,
diez `red_mob_v2` de 200 entradas, 128-128, GRU de 64 y 29 salidas; todas aceptadas por `MobAi.check`).

## Qué hace el benchmark

Una arena de piedra plana de 45 × 45 bloques, rehecha en cada escenario (los creepers la agujerean y los
zombis construyen), con los chunks forzados para que todo mob esté siempre activo. Un jugador de prueba
(`FakePlayer`) camina en círculo de radio 3 alrededor del centro (una vuelta cada 20 s), da un golpe de 5 al
mob más cercano cada 10 ticks, y no muere (se cura cada tick y tiene 200 de vida). Los muertos se reponen al
momento para que la horda no baje del número pedido, y cada 20 ticks se devuelve al jugador como objetivo a
quien lo haya soltado. Los no muertos llevan una calabaza para que el día no los queme.

La horda: de cada 10 mobs, 6 vanilla (zombi, esqueleto, araña, zombi, creeper, esqueleto) y 4 de Forja por
turnos (Coraza, Percutor, Tenaza, Autómata, Templador, Escoria, Herrumbre, Guardián de cuño, Pavesa, Ascua
mayor). Sin veteranos ni élites por azar, para que dos pasadas se parezcan.

| Escenario | Qué es | Ticks medidos |
|---|---|---:|
| `calentamiento` | 50 mobs con reglas para que el JIT compile; no cuenta | 300 |
| `reglas_30` / `reglas_50` | 30 / 50 mobs con el cerebro de reglas | 1200 |
| `redes_30` / `redes_50` | los mismos con la red de su familia | 1200 |
| `apagado_50` | referencia: 50 mobs con `enabled = false` (sin combate ni IA de Forja; los mobs del mod siguen con su IA propia) | 1200 |
| `jefe` / `jefe_redes` | el Herrero Caído contra el jugador; a los 250, 500 y 750 ticks se le baja la vida al 70, 45 y 20 %: aprendices, reforja, anillo violeta, yunques y lluvia de estrellas | 900 |

Cada escenario deja 200 ticks (40 el jefe) para que la pelea arranque antes de medir.

Qué se mide en cada uno:

1. **La duración de cada tick del servidor**, de principio a fin (con una fase de eventos antes y otra
   después de las de todos los mods). El servidor de gametest no espera entre ticks, así que es trabajo puro.
2. **Relojes alrededor de las rutas de Forja**, puestos con mixins que viven solo en las pruebas
   (`src/gametest/java/dev/forja/test/mixin`): `MobAi.tick`, `MobAi.think`, `RuleBrain.decide`, `ObsM1.of`,
   `ObsM1.allies`, `ObsForja.full`, `NetBrain.forward/sample`, `MobAi.mask`, `Squad.update`, `TacticGoal`,
   `SpecialGoal`, los `MovementGoals`, `Shockwave.tick` y el tick del Herrero. Son inclusivos (cada uno
   contiene lo que llama).
3. **Cuántas veces** se llama a lo pequeño (`AttackTokens`, `Posture`, `Personality.trait`), cuántas rutas
   pide `TacticGoal` y cuántas búsquedas de camino hay en todo el servidor.
4. **Partículas**: cada `sendParticles` se cuenta (llamadas y partículas), por tipo y, en una de cada ocho,
   por quién la manda.
5. **JFR** del hilo del servidor cada 1 ms: qué parte del tiempo tiene Forja en la pila y dónde.

## Resultados

Cada cifra es la mediana de 3 pasadas alternadas (antes, después, antes, después...) para que la carga de
la máquina afecte igual a las dos versiones. "Antes" es el código de `4c8e822`; "después", el de esta rama.

### El tick entero (ms)

| Escenario | Antes: media · p95 · p99 · máx | Después: media · p95 · p99 · máx |
|---|---|---|
| reglas_30 | 1,16 · 2,04 · 3,69 · 11,6 | 1,09 · 1,91 · 3,55 · 12,3 |
| reglas_50 | 1,75 · 2,74 · 4,06 · 11,4 | 1,49 · 2,36 · 4,01 · 11,4 |
| redes_30 | 1,94 · 3,13 · 4,99 · 9,4 | **1,49** · 2,23 · 3,36 · 8,8 |
| redes_50 | 3,22 · 5,30 · 7,50 · 46,5 | **2,73** · 4,66 · 6,05 · 43,1 |
| apagado_50 (referencia) | 1,60 · 2,61 · 4,01 · 11,4 | 1,39 · 2,36 · 4,18 · 7,6 |
| jefe | 0,23 · 0,49 · 0,84 · 6,2 | 0,24 · 0,49 · 0,78 · 6,4 |
| jefe_redes | 0,27 · 0,54 · 0,93 · 2,6 | 0,28 · 0,53 · 0,82 · 1,8 |

Ojo con el ruido: la máquina tenía a la vez otras sesiones compilando y probando. `apagado_50` no pasa por
nada de lo que cambié y aun así baja 0,2 ms entre versiones; ese es el tamaño del ruido en la media del tick.
Por eso las mejoras se miden con los relojes de cada ruta (tabla siguiente), que no dependen de lo demás.
Los máximos (hasta 46 ms, una vez por escenario) son pausas del recolector de basura y el tick en que se
reponen muchos mobs a la vez, no la IA.

Con esa salvedad: con redes, el tick entero baja un **15-23 %**; con reglas la diferencia está dentro del
ruido (lo que ahorra el cambio medido con relojes son 0,02 ms/tick).

### Dónde se va el tiempo de la IA (50 mobs)

| Ruta (inclusiva) | Reglas: ms/tick | Redes antes: ms/tick · µs/llamada | Redes después: ms/tick · µs/llamada |
|---|---:|---:|---:|
| `MobAi.tick` (todos los cerebros + escuadra) | 0,087 → 0,066 | 1,093 · 364 | **0,645** · 215 |
| ↳ `MobAi.think` (un mob, 60 por tick) | 0,063 → 0,045 | 1,053 · 17,6 | **0,610** · 9,8 |
| ↳↳ `NetBrain.forward` (23 por tick) | – | 0,506 · 22,3 | **0,259** · 11,4 |
| ↳↳ `ObsM1.of` (las 102 del simulador) | – | 0,387 · 17,0 | **0,223** · 9,8 |
| ↳↳↳ `ObsM1.allies` | 0,003 | 0,183 · 8,1 | **0,094** · 4,1 |
| ↳↳ `ObsForja.full` (las 98 de Forja) | – | 0,094 · 4,1 | **0,071** · 3,1 |
| ↳↳ `RuleBrain.decide` | 0,013 → 0,009 | – | – |
| ↳ `Squad.update` (cada 10 ticks) | 0,005 | 0,005 · 15 | 0,004 · 14 |
| `TacticGoal.tick` | 0,03 | 0,47 · 10,4 | 0,51 · 11,3 (sin tocar; ruido) |
| `SpecialGoal.canUse` (reglas) | 0,01 | 0 | 0 |
| `Shockwave.tick` | 0,004 (11 ondas vivas, 0,35 µs cada una) | 0,005 | 0,005 |

Lo pequeño, por tick con 50 mobs y redes: `Posture.isStaggered` ~50 llamadas, `Posture.fill` ~23,
`AttackTokens.holds` ~28, `tryAcquire` y `free` ~1, `Personality.trait` ~90. Son búsquedas en un mapa;
ni el JFR las ve.

El Herrero (con aprendices, reforja, yunques y onda) apenas cuesta: **0,07-0,09 ms por tick** su tick
entero, cuerpo vanilla incluido; sus golpes pesados 0,003 ms; cada onda viva 1 µs por tick en el servidor
(el anillo lo dibuja el cliente). Con él solo, el tick completo se queda en 0,25 ms.

### Qué parte del tick es de Forja (JFR)

Con 50 mobs (pasadas con más de 800 muestras; con pocas muestras el JFR no es fiable, y en los escenarios
del jefe hubo menos de 20):

| | Reglas | Redes (antes) | Apagado |
|---|---:|---:|---:|
| Con algún marco de Forja en la pila | 52 % | 70 % | 50 % |
| · lógica propia de Forja | 14 % | 51 % | 5 % |
| · cuerpo vanilla de los mobs de Forja (su `tick` llama a `super.tick`) | 38 % | 20 % | 45 % |

Dentro de la lógica de Forja, lo más caro en las hordas era:

- **Con redes**: `TacticGoal.pathTo` 12 % del tick (buscar caminos, ver abajo), `NetBrain.forward` +
  `dense` 16 %, `ObsM1.ground` 5 %, `ObsM1.allies` 4 % (del que el 78 % era ordenar la lista), `ObsM1.sees`
  (rayos de visión) 1-2 %.
- **Con reglas**: `ObsM1.sees` 2 % (cada mob con objetivo lanza un rayo por tick para saber si lo ve),
  `TacticGoal.pathTo` 2 %, `CuneGuardian.findSeals` 0,5-1 %, `Squad.allyInLine` y `VanillaSpecials.sees`
  (volea del esqueleto) < 1 %.
- Por dónde entra: los ticks de los propios mobs de Forja (Guardián de cuño 7 %, Pavesa 5-7 %, Autómata
  5 %, Coraza 4-5 %, Templador 3-5 %, Escoria 3-4 %, Ascua mayor 2-5 %, Percutor 3 %) y, con redes, el
  evento de fin de tick de `MobAi` (34 %).

### Partículas (50 mobs)

| | Reglas | Redes | Apagado |
|---|---:|---:|---:|
| Llamadas a `sendParticles` por tick (= paquetes a cada jugador cercano) | 53 | 59 | 56 |
| Partículas por tick | ≈ 80 | ≈ 90 | ≈ 70-75 |

Por tipo (reglas, por tick): polvo (dust) 26, alma 12, llama 10, humo 7, crítico 3, ceniza 3, chispa 2,
lava 2. Quién las manda (% de las llamadas): **`OilPools.draw` 37-44 %**, `HollowArmor` (aviso de la
embestida) 17-24 %, `Tongs.hold` (la cadena mientras sujeta) 9-16 %, `EmberWisp` 5-7 %, `LivingSlag.tick`
3 %, `SlagPools.draw` 2-3 %. Como `apagado_50` manda lo mismo, casi todo sale de los mobs del mod y no de la
IA. En el combate del jefe son solo 3-4 por tick: el anillo del duelo (`WorldFights.ring`) un tercio y la
forja del pecho (`forgeBreathes`) otro tercio.

## Optimizaciones hechas

Todas mantienen exactamente las mismas decisiones. Cada una tiene su prueba en
`src/gametest/java/dev/forja/test/OptimizationGameTests.java`, que compara el código nuevo con una copia
literal del de antes; comprobé además que las pruebas fallan si se rompe la equivalencia (sumar las
compuertas en otro orden, o desempatar al revés en la ordenación).

| Cambio | Antes | Después | Prueba |
|---|---:|---:|---|
| **`NetBrain.forward`**: las sumas de las compuertas de la GRU se hacen por entrada (como ya hacían las capas densas), con los pesos traspuestos al cargar la red. Cada compuerta suma sus términos en el mismo orden, así que los floats salen idénticos, pero las 192 compuertas ya no esperan cada una a su propia cadena de 192 sumas y el JIT las vectoriza. | 22,3 µs | **11,4 µs** | 17 redes (3 aleatorias del tamaño real, 4 v1 y 10 v2 entrenadas), 300 pasos cada una con la memoria arrastrada: salidas y memoria iguales bit a bit |
| **`ObsM1.allies`**: la distancia de cada mob se calcula una vez y se ordena como número (ordenación por mezcla estable con el orden de `Double.compare`). Antes cada comparación pasaba por dos llamadas de interfaz que el JIT no puede alinear, porque todas las ordenaciones del servidor comparten ese punto. | 8,1 µs | **4,1 µs** | 47 mobs con posiciones repetidas (empates), comparados en el mismo tick y tras empujarse: misma lista en el mismo orden |
| **`ObsM1.ground`**: una posición mutable y un chunk por columna, en vez de un `BlockPos` nuevo y una búsqueda de chunk (con su `ThreadLocal` del perfilador) por bloque. Lee lo mismo que `Level.getBlockState`, aire del vacío incluido. | `ObsM1.of` sin aliados: 9,0 µs | **5,7 µs** | más de 3.000 alturas sobre losas, escaleras, nieve, alfombra, valla, barro, miel, telaraña, andamio, un techo, un hueco y los extremos del mundo |
| **`Personality.trait`**: los nombres de las cuatro etiquetas se escriben una vez, no en cada llamada (la red lo pregunta cuatro veces por decisión). | `ObsForja.full` 4,1 µs · `RuleBrain.decide` 0,26 µs | **3,1 µs · 0,18 µs** | los cuatro rasgos, ninguno y dos a la vez |

En total, con redes y 50 mobs, un mob piensa en **9,8 µs en vez de 17,6** y todos los cerebros juntos
cuestan **0,64 ms por tick en vez de 1,09**.

La batería de pruebas del servidor sigue verde: 76 pruebas (las 71 de antes, el benchmark y las 4 de
equivalencia). `siege_comes` y `zombie_lunges_at_mid_range` fallan de vez en cuando también sin estos
cambios (lo vi en la rama base antes de tocar nada).

## Lo que no he tocado y te toca decidir

1. **`TacticGoal.pathTo` pide un camino nuevo en cada tick cuando el anterior terminó** (o falló), aunque su
   comentario dice "como mucho cada 10 ticks". Con redes y 50 mobs pide 9-10 caminos nuevos por tick, y 7
   de ellos son de ese caso; un mob que no puede llegar busca (y falla, que es la búsqueda más cara) cada
   tick. Lo probé como experimento, sin dejarlo en el código: si también esperara al décimo tick, **las
   búsquedas de camino de todo el servidor bajan de 6,2 a 3,1 por tick** y `TacticGoal.tick` de 0,36 a
   0,23 ms/tick (con reglas no cambia nada: 0,4 caminos por tick). A cambio, un mob que llega al final de su
   camino se quedaría quieto hasta 9 ticks antes de seguir al jugador. Es un cambio de comportamiento.
2. **El aceite del Templador (`OilPools.draw`)** son el 40 % de todos los paquetes de partículas de una
   horda: cada charco manda `radio × 5` partículas de polvo cada 4 ticks y de humo cada 11, una por
   paquete, y además ya se dibuja entero en el cliente como `Shockwave.pool`. También gasta dos números
   aleatorios del mundo por punto en cada tick aunque ese tick no mande nada. Quitarlo o aclararlo es
   decisión visual.
3. **El aviso de la embestida de la Coraza** (17-24 % de los paquetes) y **la cadena de la Tenaza mientras
   sujeta** (9-16 %) mandan una partícula por paquete a lo largo de una línea, cada tick.
4. **El anillo del duelo (`WorldFights.ring`)**: 24 paquetes de una partícula cada 5 ticks mientras dura un
   duelo. Es lo mismo que el Herrero dejó de hacer con su onda; podría ser un `Shockwave` quieto.
5. **El Guardián de cuño** recorre 15 × 15 × 8 bloques cada 10 ticks buscando sus faroles y rehace el nombre
   de su barra de jefe en cada tick. Con uno o dos guardianes es menos del 1 % del tick; no merece la pena
   salvo que haya muchos.

Nada de lo demás merece trabajo ahora: la escuadra (15 µs cada 10 ticks), la postura y los turnos (búsquedas
en un mapa), las ondas (0,3 µs por onda y tick) y los especiales de las reglas (0,01 ms/tick) están muy por
debajo del ruido.

## Una cosa que no es de Forja

Dentro de cada búsqueda de camino, la mitad del tiempo se va en `IdentityHashMap.get` de
`LandPathTypeRegistry` de la Fabric API (`fabric-content-registries`), que se engancha a la lectura del tipo
de cada nodo. En las hordas es el ~10 % de todo el tick del servidor, vanilla incluido. Forja no puede
arreglarlo; un mod de rendimiento sí podría (por ejemplo, saltarse la búsqueda para los bloques que nadie
ha registrado).

## Límites de estas cifras

- Es el servidor de gametest: no hay jugadores de verdad, así que no se mandan paquetes ni se siguen
  entidades para ningún cliente. Con un jugador cerca, cada mob añade su coste de red vanilla, y cada
  llamada a `sendParticles` es un paquete.
- El jugador es un `FakePlayer`: no corre su propio tick (estamina, esquiva), solo lo que los mobs le hacen.
- La máquina es muy rápida por hilo y estaba cargada con otras sesiones; las medias del tick bailan
  ±0,2 ms entre pasadas. Para comparar versiones, usar los relojes por ruta y varias pasadas alternadas.
- El JFR muestrea peor de lo pedido cuando la máquina está cargada (entre 100 y 1.900 muestras en la misma
  ventana): sus porcentajes solo valen con cientos de muestras.
