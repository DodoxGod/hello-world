# Diseño del contrato `red_mob_v4` (mobs que piensan de verdad): borrador para Andy, la sesión del mod y la del simulador

Fecha: 29-09-2026. Es solo diseño: no se ha tocado ningún archivo de código. Parte de lo que hay hoy: el contrato
`red_mob_v3_contrato.json` (v3b: 280 entradas y 34 salidas), `red_mob_v3_1.md`, `red_mob_v4_contrato.propuesta` (296/36),
`PROPUESTAS_IA_SIMULADOR.md`, `red_blaze_contrato.json` (324/18, el precedente de un contrato aparte), el paquete
`dev.forja.ai` de la rama `claude/hola-rv9w0u` (f56c1c5), el motor Rust (`src/combate/forja/`), `NOTAS.md`,
`entrenar_forja.py` y `PROPUESTA_V3_FORJA.md`.

## Resumen

1. **Dos redes:** una por mob (`red_mob_v4`, una por familia, como hoy) y una nueva de **capitán** (`red_capitan_v4`) que
   corre una vez por grupo cada 10 ticks, en el `Squad`. El capitán da **órdenes discretas** (9 órdenes, 4 formaciones,
   sector de ataque, cuenta atrás, foco y el puesto de hasta 8 miembros). Los miembros las **ven como entradas** y deciden
   si obedecen. Matar al capitán deja al grupo sin órdenes y baja la moral.
2. **Red por mob:** 468 entradas = las 280 de v3b en el mismo orden + 188 nuevas en 12 bloques. Tiene 53 salidas = las 34
   de v3b + 2 de la propuesta v4 + 6 tácticas nuevas + una cabeza de objeto (9) + furia + golpe de escudo. La GRU pasa de
   64 a 96. El archivo sube de ~1,9 a ~3,1 MB y el coste a ~1 ms/tick con 50 mobs.
3. **Memoria:** la GRU de cada mob sirve para emboscar, buscar, escudo y pilar. El capitán tiene su propia GRU de 64. La
   **adaptación a cada mundo** (punto 11) **no se aprende en el mod**: el mod guarda estadísticas por jugador (cómo mueren
   los mobs y qué les funciona) y las da como un vector de 16 entradas. Se entrena con campañas de varios combates contra
   el mismo estilo de jugador.
4. **Ningún mob construye ni rompe bloques.** Se quitan `MovementGoals.Builder` (excavar y pilar) y la rotura de puertas
   de vanilla. La única excepción son las fuentes de luz (táctica `APAGAR_LUZ`). Contra un pilar o una torre hay 9
   herramientas sin construir: flecha de empuje, carga de viento, araña que trepa y empuja, garfio o agarre, pociones
   arrojadizas, perla al pilar, creeper al pie, fuego y asedio con emboscada a la bajada.
5. **Percepción honesta:** cuando el mob no ve ni oye al jugador, el bloque `obj_*` lleva la **última posición
   conocida**, no la real, y el ejecutor también navega a esa estimación. Sin esto, oír, rastrear y emboscar no significan
   nada.
6. **Las v3/v3b/v3.1 siguen funcionando:** v4 es otro formato (`"formato": "red_mob_v4"`) y va en otra carpeta
   (`config/forja/redes_v4/`). Sin red v4 o sin red de capitán, todo va como hoy.
7. **Plan de entrenamiento en 7 fases:**
   - migración desde v3s it 29 500 sin cambiar el comportamiento;
   - mecánicas individuales: escudo, armas y consumibles;
   - contra trucos: pilar, torre y antorchas;
   - percepción: oído, rastro y emboscadas;
   - grupo: capitán, formaciones y moral;
   - adaptación por mundo.
8. **Objetivos:**
   - batería estándar (grupos de 4, jugador "bueno"): **sin regresión** respecto a v3s (62,0 de daño/min, muere 0,64);
   - con cada estilo tramposo (pilar, torre, pasillo, oscuridad, huida, tortuga): **+50 % de daño/min** sobre v3s;
   - capitán: +20 % en grupos de 8, y matarlo tiene que costar ≥ 25 %.
9. **Bloqueo previo:** el simulador necesita compilar Rust. Si Smart App Control sigue bloqueando `rustc`, no se puede
   empezar ninguna fase del simulador.

---

## 1. Arquitectura

### 1.1 Piezas

| Pieza | Dónde corre | Cada cuánto | Entradas | Salidas | Tamaño |
|---|---|---|---|---|---|
| `red_mob_v4` (10 familias + blaze aparte) | `MobMind` de cada mob que pelea | 2 ticks (`ticks_por_decision`) | 468 | 53 | 468 → 128 → 128 (tanh) → GRU 96 → 53 |
| `red_capitan_v4` (una sola, para todas las familias) | `Squad`, sobre el capitán del grupo | 10 ticks (`Squad.PERIOD`) | 213 | 60 | 213 → 128 → 128 → GRU 64 → 60 |
| Capitán de reglas (`CaptainRules`) | igual | 10 ticks | lo mismo | lo mismo | — (respaldo e imitación) |
| Vector de mundo (`WorldMemory`) | `SavedData` por jugador | al morir un mob o acabar una pelea | — | 16 números | fuera de la red |

La forma de cálculo es **la misma que ya ejecuta `NetBrain.forward`**: dos densas tanh, luego `GRUCell` y la salida desde
`[h2, memoria]`. `NetBrain` ya lee el tamaño de la GRU del archivo (`memory = gruHh[0].length`), así que el capitán puede
reutilizar la clase. Solo cambian el muestreo y la interpretación de las cabezas.

### 1.2 Por qué un capitán aparte y no "todo en la red de cada mob"

- Las órdenes de grupo (carga sincronizada, retirada ordenada, quién va delante) necesitan **una sola decisión para
  todos**. Si cada mob decide solo, 8 redes no se ponen de acuerdo en el mismo tick sin comunicarse.
- Andy quiere que **matar al capitán desorganice**. Con un cerebro de grupo es literal: sin capitán no hay órdenes.
- Es barato: una pasada cada 10 ticks por grupo, frente a 5 por mob en el mismo tiempo.

### 1.3 Cómo fluyen las órdenes

```
Squad.update (cada 10 ticks, por jugador objetivo)
  ├─ elige el capitán: el líder actual (Squad.leader) si su amenaza ≥ élite (o jefe); si no hay, el grupo va SIN MANDO
  ├─ si hay capitán: ObsCapitan (213) → red_capitan_v4 (o CaptainRules si no hay archivo)
  │     → Orden { orden 0..8, formación 0..3, sector 0..8, cuenta 0..3, foco 0..1, puesto[8] 0..3 }
  ├─ Squad convierte formación + puestos en un PUNTO por miembro (geometría del mod, no de la red)
  └─ guarda en cada MobMind: orden, edad, cuenta atrás, sector, puesto y punto
Cada mob (cada 2 ticks): ObsV4 (468; el bloque M lleva la orden) → red_mob_v4 → Decision
  → TacticGoal ejecuta (la táctica FORMACION va al punto del puesto; las demás, como hoy)
```

- **Órdenes discretas, no un vector aprendido.** Un "mensaje" continuo que se entrena de extremo a extremo exige
  propagar gradientes de los miembros al capitán. Además, no se puede depurar con `/forja ia ver` y es difícil igualar
  mod y simulador. Las órdenes discretas se ven en pantalla, se imitan de un capitán de reglas y se graban. Queda como
  opción para una v4.1 (4 números de "intención" al final del bloque M), no en este contrato.
- **Obedecer no es obligatorio.** Cada miembro ve la orden y su red elige su táctica. Aprende a obedecer porque el
  premio de equipo sube cuando lo hace. Al principio se añade un pequeño premio por obedecer que se retira después (§5).
- **Sin capitán** (grupo sin élite, capitán muerto o sin archivo y sin reglas): orden = NINGUNA, formación = LIBRE. La red
  del mob tiene que pelear bien así, igual que v3b. El 40 % de los episodios de entrenamiento son sin capitán.
- **Capitán muerto:** `sin_mando` = 1 y baja a 0 en 200 ticks. Durante ese tiempo no hay órdenes, aunque quede otro élite.
  Pasados los 200 ticks, otro élite puede tomar el mando. La moral baja (§2.3).

### 1.4 Memoria: qué necesita qué

| Punto | ¿GRU del mob? | ¿Capitán? | ¿Fuera de la red? | Por qué |
|---|---|---|---|---|
| 1 Formaciones | poca | **sí** (elige formación y puestos) | la geometría del puesto (Squad) | el puesto entra como dato; la red solo decide ir o no |
| 2 Capitán | — | **sí**, GRU 64 | respaldo de reglas | planifica en el tiempo: cercar → cuenta atrás → carga |
| 3 Moral | no | ve la moral | **el valor de la moral** (estado, como la postura) | la red decide huir, reagruparse o entrar en furia |
| 4 Emboscadas | **sí** (paciencia, ritmo del jugador) | orden EMBOSCADA | la búsqueda de escondites (caché cada 10 ticks) | esperar sin salir antes de tiempo es algo temporal |
| 5 Oído y rastro | **sí** (patrón de búsqueda) | — | **última posición y sonidos** (entradas explícitas) | no se fía a la GRU lo que el mod puede recordar exacto |
| 6 Antipilar | poca | orden ASEDIO | medidas del pilar (entradas) | cuánto lleva arriba ya es una entrada |
| 7 Recoger armas | no | — | el valor del arma (entrada) | decisión instantánea |
| 8 Consumibles | poca | — | inventario y efectos (entradas) | — |
| 9 Escudo inteligente | **sí** (ritmo de los golpes del jugador) | — | predicción del golpe (entrada) | subir el escudo a tiempo es cuestión de ritmo |
| 10 Antorchas | no | — | luz y aporte de cada fuente (entradas) | — |
| 11 Adaptación por mundo | solo dentro de la pelea | ve el vector | **vector de mundo de 16** (SavedData) | aprender en línea en el mod sería caro, no determinista y explotable |

La GRU del mob sube de 64 a 96 porque cuatro puntos (4, 5, 6 y 9) dependen del tiempo. La ampliación **conserva la
función**: con pesos a cero en las 32 unidades nuevas, z = σ(0) = 0,5 y n = tanh(0) = 0, así que h' = 0,5·h. Empiezan en 0
y se quedan en 0, y sus pesos de salida son 0: la red migrada da exactamente las mismas salidas que la v3b.

### 1.5 Lo que cuesta en el mod

| | v3b hoy | v4 mob | capitán v4 |
|---|---|---|---|
| Parámetros exportados | ~96 900 | ~154 500 (GRU 96) / ~124 000 (GRU 64) | ~92 700 |
| Archivo JSON (≈ 20 bytes por número, como hoy) | 1,94 MB | ~3,1 MB | ~1,85 MB |
| Pasadas por tick con 50 mobs | 25 | 25 | ~0,5 (1 por grupo cada 10) |

- **Tiempo:** hoy los cerebros con red cuestan 0,64 ms/tick con 50 mobs (`RENDIMIENTO.md`). La v4 hace unas 1,6 veces más
  operaciones, pero `dense` se salta las entradas a 0, y los bloques de objetos, sonidos, luces y pociones suelen ir a 0.
  Estimación: ~1,0 ms de red y ~0,5 ms más de observación: **≤ 2,5 ms/tick con 50 mobs** en total (el 5 % del tick). Hay
  que volver a medirlo con `tools/rendimiento.sh`.
- **Observación cara:** los escondites (rayos de vista), la luz a 8 direcciones y el pilar del jugador se calculan cada
  10 ticks por mob y se guardan, como ya se hace con `cover` cada 20. Los sonidos llegan por eventos (§2.5), no se buscan.
- **Tamaño de los archivos:** se recomienda exportar con 5 cifras significativas (−35 %). La v4 no lo necesita, es solo
  una mejora.

### 1.6 Compatibilidad

- **Archivos:** `config/forja/redes_v4/red_<familia>.json` y `red_capitan.json`, con `"formato": "red_mob_v4"` /
  `"red_capitan_v4"`. Una opción `iaContrato` = `v3` | `v4` | `auto` (auto = v4 si hay archivo). Las v3, v3b y v3.1
  siguen cargándose desde `config/forja/redes/` como hoy.
- **Elegir por formato, no por prefijo.** Los 280 primeros nombres coinciden con v3b, pero en la v4 `obj_*` puede ser una
  estimación (§2.5). `MobAi.check` tiene que mirar `formato` antes de aceptar una red por prefijo de nombres.
- **`yo_arma_alcance`:** en la v4 siempre con la fórmula de `"alcance_v": 2` (el alcance real, `red_mob_v3_1.md`).
- **Mezclas:** un miembro v3b en un grupo con capitán v4 ignora las órdenes, y no pasa nada. Un miembro v4 sin capitán ve
  `tengo_capitan = 0`.
- **Grabación:** `/forja ia grabar` añade `"formato"`, las 468 entradas, la cabeza de objeto y, una vez por período, una
  línea `"capitan"` con su observación y su orden.

---

## 2. Observación del mob: 468 entradas

Marco de siempre: "delante" = del mob al jugador en el plano, "derecha" = (−delante_z, delante_x). Si no se dice otra
cosa, todo va recortado a ±2.

| Bloque | Índices | N | Qué |
|---|---|---|---|
| V3B | 0–279 | 280 | `red_mob_v3_contrato.json` tal cual, en su orden (con `alcance_v` 2) |
| S — sectores | 280–295 | 16 | los de `red_mob_v4_contrato.propuesta`, sin cambios |
| R — alcance mínimo | 296–297 | 2 | `PROPUESTAS_IA_SIMULADOR.md` §8.3 |
| M — mando | 298–327 | 30 | capitán, orden, formación y puesto |
| Mo — moral | 328–337 | 10 | moral, bajas y furia |
| P — percepción | 338–365 | 28 | percibido o no, última posición, 2 sonidos, búsqueda |
| E — emboscada y luz | 366–386 | 21 | escondites, visibilidad, pasillos y puertas |
| A — altura y pilar | 387–396 | 10 | jugador en pilar o torre |
| O — objetos en el suelo | 397–416 | 20 | 2 objetos |
| C — consumibles y efectos | 417–434 | 18 | inventario del mob y efectos |
| G — escudo | 435–439 | 5 | ritmo del escudo |
| L — luces | 440–451 | 12 | 2 fuentes de luz |
| W — mundo | 452–467 | 16 | vector de adaptación |
| **Total** | | **468** | v3b + 188 (+67 %) |

### 2.1 S (280–295) y R (296–297)
- S, sin cambios: `sector_delante`, `sector_izquierda`, `sector_derecha`, `sector_detras`, `hueco_error/pi`,
  `hueco_estable/100`, `aliados_a_1.5/3`, `aliados_mi_sector/4`, `jug_cuadrantes/4`, `jug_enzarzado/4`,
  `creeper_encendido_cerca/7`, `tiro_lado_libre`, `tiro_punto_dist/4`, `grupo_n/13`, `turno_espera/40`,
  `soy_primero_sector`.
- R:
  - `yo_arma_alcance_min/6`: 1/6 con lanza, 0 con el resto;
  - `jug_alcance_min/6`: la lanza del jugador no pega de cerca.

### 2.2 M: mando (298–327)
| i | nombre | definición |
|---|---|---|
| 298 | `tengo_capitan` | 1 si mi grupo tiene capitán vivo |
| 299 | `soy_capitan` | 1 si el capitán soy yo (también decide su propia táctica con su red de mob) |
| 300 | `sin_mando` | 1 al morir el capitán, baja lineal a 0 en 200 ticks |
| 301–309 | `orden_ninguna`, `orden_cercar`, `orden_carga`, `orden_hostigar`, `orden_retirada`, `orden_reagrupar`, `orden_emboscada`, `orden_asedio`, `orden_escolta` | one-hot de la orden vigente (§3.2) |
| 310 | `orden_edad/40` | ticks desde que se dio la orden |
| 311 | `cuenta_atras/40` | ticks que faltan para la carga sincronizada (0 = ¡ya!) |
| 312–313 | `orden_sector_delante`, `orden_sector_derecha` | vector unitario, en mi marco, del sector desde el que el capitán quiere el ataque (0, 0 = ninguno) |
| 314–317 | `formacion_libre`, `formacion_muro`, `formacion_pinza`, `formacion_cuna` | one-hot |
| 318–321 | `puesto_frente`, `puesto_segunda`, `puesto_flanco`, `puesto_reserva` | mi puesto (del capitán o, sin capitán, 0) |
| 322–323 | `puesto_delante/8`, `puesto_derecha/8` | el punto de mi puesto visto desde mí |
| 324 | `cubierto` | 1 si un aliado con escudo o tanque corta la línea del jugador a mí (`Squad.allyInLineFrom` al revés) |
| 325–326 | `capitan_delante/16`, `capitan_derecha/16` | dónde está el capitán |
| 327 | `capitan_vida_frac` | 0 sin capitán |

### 2.3 Mo: moral (328–337)
| i | nombre | definición |
|---|---|---|
| 328 | `moral_grupo` | 0..1 (fórmula en §4.3) |
| 329 | `moral_propia` | 0..1 |
| 330 | `bajas_frac` | muertos del grupo / pico del grupo |
| 331 | `bajas_recientes/5` | muertes del grupo en los últimos 200 ticks |
| 332 | `aliados_huyendo/5` | miembros en RETIRARSE ≥ 40 ticks seguidos |
| 333 | `aliados_furia/5` | miembros en furia |
| 334 | `furia_disponible` | 1 si puedo entrar en furia (máscara de la salida 51) |
| 335 | `yo_furia/200` | ticks de furia que me quedan |
| 336 | `yo_agotado/100` | ticks de agotamiento tras la furia |
| 337 | `retirada_ticks/100` | ticks seguidos en RETIRARSE (a 100 y a > 20 bloques se abandona la pelea, §4.3) |

### 2.4 P: percepción (338–365)
| i | nombre | definición |
|---|---|---|
| 338 | `obj_percibido` | 1 si lo veo ahora (rayo a los ojos, dentro del alcance de seguimiento, y la regla de oscuridad 98) |
| 339 | `obj_oido` | 1 si oí al jugador en los últimos 20 ticks |
| 340 | `obj_edad/100` | ticks desde la última vez que lo vi. **El bloque `obj_*`, los `jug_*` y el marco "delante" se calculan con la estimación (§2.5)** |
| 341–343 | `ultima_delante/16`, `ultima_derecha/16`, `ultima_dy/4` | última posición vista, desde mí |
| 344 | `ultima_edad/200` | ticks desde entonces |
| 345–354 | `sonido0_presente`, `sonido0_delante/16`, `sonido0_derecha/16`, `sonido0_dy/4`, `sonido0_edad/40`, `sonido0_fuerza` (radio/16), `sonido0_movimiento`, `sonido0_trabajo`, `sonido0_comer`, `sonido0_combate` | el sonido más reciente del jugador que oigo (tipo one-hot de 4) |
| 355–364 | `sonido1_*` | el segundo (mismo formato) |
| 365 | `buscando/200` | ticks en BUSCAR |

### 2.5 E: emboscada, visibilidad y luz (366–386)
| i | nombre | definición |
|---|---|---|
| 366–373 | `oculto_r3_<8 direcciones>` | 1 si desde el punto a 3 bloques en esa dirección (mismo orden que `altura_r3_*`) no hay línea de vista a los ojos del jugador |
| 374 | `escondite_presente` | hay un escondite a ≤ 8 (§4.4) |
| 375–376 | `escondite_delante/8`, `escondite_derecha/8` | dónde |
| 377 | `escondite_luz/15` | luz allí |
| 378 | `escondite_dist_jug/16` | distancia de ese escondite al jugador |
| 379 | `me_ve_jugador` | 1 si el jugador podría fijarse en mí ahora: línea de vista, dentro de su cono de 70° y (luz ≥ 4 o a < 8) |
| 380 | `jug_luz/15` | luz en los pies del jugador (`getMaxLocalRawBrightness`); la mía ya es `luz/15` de v3b |
| 381 | `jug_en_pasillo` | jugador con pared (≥ 2 de alto) a ≤ 1,5 a ambos lados en el eje perpendicular a su mirada |
| 382 | `jug_en_puerta` | el jugador está en un hueco de 1–2 de ancho entre paredes o en una puerta |
| 383 | `jug_bajo_techo` | bloque sólido a ≤ 4 sobre su cabeza |
| 384 | `jug_sin_vernos/200` | ticks desde que el jugador tuvo en su cono a algún miembro del grupo |
| 385 | `emboscados/5` | miembros en EMBOSCAR y ocultos |
| 386 | `yo_emboscado/200` | ticks que llevo emboscado |

### 2.6 A: altura y pilar (387–396)
| i | nombre | definición |
|---|---|---|
| 387 | `jug_sobre_suelo/8` | pies del jugador − mediana del suelo en el anillo de 2,5 a su alrededor |
| 388 | `jug_en_pilar` | 1 si en ≥ 6 de las 8 direcciones a 1,5 del jugador hay caída ≥ 2,5 (columna de 1×1 o 2×2) |
| 389 | `jug_en_torre` | 1 si está a ≥ 2,5 sobre el suelo y rodeado de paredes o almenas (≥ 5 de 8 direcciones con bloque a la altura del pecho) |
| 390 | `jug_borde/2` | distancia de sus pies al borde más cercano por el que caería ≥ 2,5 |
| 391 | `jug_caida_empuje/8` | altura que caería si lo empujan 1,5 en la dirección mob→jugador |
| 392 | `jug_alcanzable` | 1 si mi golpe le llega desde el suelo (dy ≤ alcance vertical) |
| 393 | `pared_trepable` | 1 si hay pared continua (sin hueco ≥ 2) desde el suelo hasta la altura del jugador a ≤ 3 de él (arañas) |
| 394 | `jug_arriba/200` | ticks seguidos con `jug_sobre_suelo` ≥ 2 |
| 395 | `jug_tira_desde_arriba` | disparó (arco, ballesta, báculo o lanzado) en los últimos 40 ticks estando arriba |
| 396 | `veo_jug_arriba` | 1 si tengo línea a su cuerpo mientras está arriba |

### 2.7 O: objetos en el suelo (397–416)
Por cada uno de los 2 `ItemEntity` útiles más cercanos (≤ 12, del más cercano al más lejano), 10 entradas:
`objetoK_presente`, `objetoK_delante/8`, `objetoK_derecha/8`, `objetoK_dy/4`, `objetoK_mejora/10`, `objetoK_del_jugador`,
`objetoK_arma_cuerpo`, `objetoK_arma_distancia`, `objetoK_escudo`, `objetoK_consumible`.
- **`mejora`** = valor del objeto − valor de lo que llevo, en la misma escala (§4.7).
- **`del_jugador`** = lo soltó un jugador (dueño del `ItemEntity`) o vino de un robo.

### 2.8 C: consumibles y efectos (417–434)
- **Inventario:** `inv_curacion/2`, `inv_mejora/2` (fuerza, velocidad o resistencia al fuego), `inv_arrojadiza/3`,
  `inv_comida/3`, `inv_perla/2`, `inv_carga_viento/3`.
- **Estado del consumo:** `consumo_enfriamiento/40`, `consumiendo`.
- **Mis efectos:** `yo_ef_fuerza`, `yo_ef_velocidad`, `yo_ef_regeneracion`, `yo_ef_negativo`.
- **Efectos del jugador:** `jug_ef_lentitud`, `jug_ef_veneno`, `jug_ef_debilidad`, `jug_ef_mejora` (alguno de fuerza,
  velocidad, resistencia o regeneración).
- **Tiros posibles:**
  - `perla_destino_ok`: hay un punto válido a ≤ 16, a 2 del jugador, con suelo y sin lava ni vacío, o el jugador está en
    un pilar y la perla puede darle;
  - `lanzamiento_ok`: jugador a 3–10 y la parábola del lanzamiento está libre.

### 2.9 G: escudo (435–439)
| i | nombre | definición |
|---|---|---|
| 435 | `yo_escudo_ticks/20` | cuánto llevo con el escudo arriba (bloquea desde 5, vanilla) |
| 436 | `jug_golpe_en/10` | ticks estimados hasta que un golpe suyo pueda llegarme: 0 si ya está cargado y a su alcance, si no max(recarga restante, (distancia − alcance)/velocidad) |
| 437 | `jug_golpe_fuerte` | 1 si su golpe cargado ≥ 0,8, o si su especial de área está listo y yo dentro del radio |
| 438 | `bloqueo_hace/20` | ticks desde mi último bloqueo o parada (1 si nunca o hace > 20) |
| 439 | `golpe_escudo_listo` | máscara de la salida 52 |

### 2.10 L: luces (440–451)
Por cada una de las 2 fuentes rompibles (§4.10) más cercanas, a ≤ 16 del jugador y a ≤ 16 de mí, 6 entradas:
`luzK_presente`, `luzK_delante/8`, `luzK_derecha/8`, `luzK_dy/4`, `luzK_aporte/15`, `luzK_alcanzable`.
- **`aporte`** = cuánto bajaría la luz de bloque en los pies del jugador si se quita esa fuente. Con luz de cielo alta
  (día al aire libre) el aporte efectivo es 0.
- **`alcanzable`** = hay ruta y la luz está a ≤ 2 de altura sobre un suelo accesible.

### 2.11 W: mundo (452–467), el vector de adaptación (§4.11)
- **Causas de muerte** (medias móviles, suman 1): `mundo_muerte_abierto`, `mundo_muerte_flecha`, `mundo_muerte_altura`,
  `mundo_muerte_estrecho`, `mundo_muerte_trampa`, `mundo_muerte_area`, `mundo_muerte_fuego`, `mundo_muerte_otra`.
- **Lo que funciona** (daño al jugador por vida de mob, /10): `mundo_exito_frente`, `mundo_exito_flanco`,
  `mundo_exito_distancia`, `mundo_exito_emboscada`, `mundo_exito_asedio`.
- **Estilo del jugador:** `mundo_jug_pilar` (frecuencia con que se sube), `mundo_jug_huye` (frecuencia con que se aleja a
  > 24 o se encierra).
- **Confianza:** `mundo_confianza` (muertes registradas / 50, tope 1).

---

## 3. Salidas

### 3.1 Red del mob: 53 salidas

| Índices | Cabeza | Tipo | Notas |
|---|---|---|---|
| 0–8 | `mover` | softmax | v1 |
| 9 | `saltar` | Bernoulli | v1 |
| 10 | `usar` | Bernoulli | v1 |
| 11–19, 29–32, **34–41** | `tactica` | **una softmax de 21** | las 9 de v2, las 4 de v3 y 8 nuevas |
| 20–24 | `especial` | softmax | hueco 3 nuevo para arquero y araña (§4.6) |
| 25–27 | `defensa` | softmax | nada, escudo, esquivar |
| 28 | `fintar` | Bernoulli | |
| 33 | `correr` | Bernoulli | v3b |
| 34 | `tactica_sector` | (táctica 13) | de la propuesta v4 |
| 35 | `tactica_tiro_libre` | (táctica 14) | de la propuesta v4 |
| 36 | `tactica_formacion` | (15) | ir a mi puesto |
| 37 | `tactica_emboscar` | (16) | |
| 38 | `tactica_buscar` | (17) | |
| 39 | `tactica_recoger` | (18) | |
| 40 | `tactica_apagar_luz` | (19) | la única que rompe un bloque |
| 41 | `tactica_asediar` | (20) | |
| 42–50 | `objeto` | softmax de 9 | 0 nada, 1 curarse, 2 mejorarse, 3 comer, 4 lanzar_pocion, 5 perla_acercar, 6 perla_escapar, 7 carga_viento, 8 cambiar_arma |
| 51 | `furia` | Bernoulli | entra en furia (se compromete 200 ticks) |
| 52 | `golpe_escudo` | Bernoulli | contraataque bajando el escudo |

- **Acción en el simulador:** 11 columnas: `[mover, saltar, usar, tactica 0..20, especial, defensa, fintar, correr,
  objeto, furia, golpe_escudo]`.
- **`Tactic`** añade al final SECTOR (13), TIRO_LIBRE (14), FORMACION (15), EMBOSCAR (16), BUSCAR (17), RECOGER (18),
  APAGAR_LUZ (19) y ASEDIAR (20). No cambia el orden de las 13 que ya existen.

**Máscaras nuevas** (el índice 0 de cada softmax nunca se prohíbe; una Bernoulli prohibida sale 0). Solo se prohíbe lo
**imposible**, no lo que parece mala idea: eso lo decide la red y lo corrige el premio.

| Salida | Permitida si |
|---|---|
| SECTOR | tiene hueco |
| TIRO_LIBRE | familia arquero y aliado en la línea |
| FORMACION | tiene puesto (`puesto_*` ≠ 0) |
| EMBOSCAR | `escondite_presente` |
| BUSCAR | no percibido (`obj_percibido` = 0) y `ultima_edad` < 600 ticks |
| RECOGER | `objeto0_presente` con ruta |
| APAGAR_LUZ | `luz0_presente` y `luz0_alcanzable` |
| ASEDIAR | `jug_sobre_suelo` ≥ 2 o `jug_en_torre` |
| objeto k | tiene ese objeto, no está consumiendo, enfriamiento 0, no aturdido; 4 y 7 además `lanzamiento_ok`; 5 `perla_destino_ok`; 8 lleva otra arma |
| furia | `furia_disponible`, no aturdido, no avisando |
| golpe_escudo | escudo arriba, `bloqueo_hace` < 20 ticks y alcanza |
| (en furia) | defensa 1–2, fintar, RETIRARSE, REAGRUPARSE, EMBOSCAR y objeto 6 prohibidos |

### 3.2 Red del capitán: 60 salidas

| Índices | Cabeza | Valores |
|---|---|---|
| 0–8 | `orden` | 0 NINGUNA, 1 CERCAR, 2 CARGA, 3 HOSTIGAR, 4 RETIRADA, 5 REAGRUPAR, 6 EMBOSCADA, 7 ASEDIO, 8 ESCOLTA |
| 9–12 | `formacion` | 0 LIBRE (anillo de hoy), 1 MURO, 2 PINZA, 3 CUÑA |
| 13–21 | `sector` | 0 ninguno, 1..8 = dirección desde el jugador respecto a su mirada lenta (frente, frente-derecha, derecha... cada 45°) |
| 22–25 | `cuenta` | 0, 10, 20, 40 ticks hasta la carga (solo con CARGA) |
| 26–27 | `foco` | 0 = mi jugador, 1 = el otro jugador más cercano (con 2 jugadores; si no, máscara) |
| 28–59 | `puesto_k` (k = 0..7) | 4 por miembro: frente, segunda línea, flanco, reserva (máscara: miembro presente) |

- **Las órdenes, en palabras.** No son reglas, sino lo que se ve en el bloque M y cómo el `Squad` cambia la geometría.
  - **CERCAR:** formación en el anillo exterior, nadie entra.
  - **CARGA:** cuenta atrás; al llegar a 0, los miembros ven `cuenta_atras` = 0. Mecánica propuesta (pregunta a Andy):
    +1 turno durante 40 ticks tras un grito visible del capitán.
  - **HOSTIGAR:** los de distancia tiran, los de cuerpo a cuerpo mantienen la formación.
  - **RETIRADA:** los puntos del puesto se alejan 12 del jugador.
  - **REAGRUPAR:** los puestos se centran en el capitán.
  - **EMBOSCADA:** los puestos pasan a los escondites del grupo, lejos de la vista del jugador.
  - **ASEDIO:** los puestos van al anillo de asedio (§4.6).
  - **ESCOLTA:** los puestos se ponen entre el jugador y el capitán.
- **Formaciones** (geometría del `Squad`, con el frente = la mirada lenta del jugador):
  - **MURO:** frente a 3,5 en un arco de ±40° delante; segunda línea a 7–10 detrás del frente y en su sombra (con
    `cubierto` = 1 si se puede); flancos a ±100°; reserva a 12.
  - **PINZA:** dos grupos a ±120°.
  - **CUÑA:** frente estrecho (±20°) y el resto detrás en V.
  - **LIBRE:** los huecos estables de hoy (2.1).
- **Miembros 9 a 13** (grupos grandes): reciben puesto por tipo (la regla de imitación, §3.4).

### 3.3 Observación del capitán: 213 entradas

| Bloque | N | Contenido |
|---|---|---|
| J — jugador | 38 | ver lista abajo |
| G — grupo | 25 | ver lista abajo |
| K — miembros | 8 × 15 = 120 | por miembro, del más cercano al jugador al más lejano (marco: capitán → jugador) |
| W — mundo | 16 | el mismo vector W del mob |
| O — orden actual | 14 | orden actual one-hot (9), `orden_edad/40`, formación actual (4) |

- **J:**
  - distancia y vida: `jug_dist_grupo/16` (al centro del grupo), `jug_vida/20`, `jug_escudo_arriba`;
  - `jug_mano_*` (8): espada, hacha, arco, ballesta, báculo, grimorio, arrojadiza, otro;
  - equipo y estado: `jug_alcance/6`, `jug_area_radio/6`, `jug_estamina/100`, `jug_presion`, `jug_equipo`, `jug_peso/10`;
  - hábitos (7): `hab_parada`, `hab_esquiva`, `hab_bloqueo`, `hab_lado`, `hab_distancia/8`, `hab_carga`, `hab_nivel`;
  - dificultad: `dif_*` (4);
  - terreno y luz: `jug_sobre_suelo/8`, `jug_en_pilar`, `jug_en_torre`, `jug_en_pasillo`, `jug_luz/15`, `noche`;
  - percepción: `jug_percibido` (por algún miembro), `jug_edad/100`, `jug_arriba/200`, `jug_de_espaldas_grupo`.
- **G:**
  - tamaño y familias: `grupo_n/13`, `fam_cuerpo/5`, `fam_arquero/5`, `fam_creeper/5`, `fam_arana/5`, `fam_otro/5`;
  - vida y moral: `vida_media`, `moral_grupo`, `bajas_frac`, `bajas_recientes/5`;
  - presión sobre el jugador: `rodeado`, `cobertura/360`, `jug_enzarzado/4`, `turnos_ocupados/4`,
    `arqueros_con_linea/4`, `creepers_listos/2`;
  - recursos: `especiales_listos/8`, `consumibles/8`;
  - estado del grupo: `ocultos/13`, `en_puesto_frac`, `huyendo/13`, `furia/13`;
  - capitán y tiempo: `capitan_vida_frac`, `capitan_dist_jug/16`, `tiempo/1200`.
- **K**, por miembro: `presente`, `delante/16`, `derecha/16`, `dist_jug/16`, `g_cuerpo`, `g_arquero`, `g_creeper`,
  `g_arana`, `g_otro`, `vida_frac`, `escudo`, `velocidad/0.3`, `oculto`, `en_puesto`, `turno`.

### 3.4 Capitán de reglas (respaldo e imitación)
Es la imitación de la que aprende la red del capitán, y el cerebro cuando no hay `red_capitan.json`:
- **Puestos:** tanques y mobs con escudo → frente; arqueros y lanzadores → segunda línea; arañas y mobs con velocidad
  ≥ 0,3 o que corren → flanco; el resto → reserva.
- **Órdenes, en este orden:**
  1. jugador en pilar o torre → ASEDIO;
  2. moral < 0,3 → RETIRADA;
  3. jugador con escudo y arco, a > 10 → HOSTIGAR;
  4. ≥ 60 % en su puesto y jugador ocupado o de espaldas → CARGA (cuenta 10);
  5. noche, luz del jugador < 7 y nadie visto → EMBOSCADA;
  6. si no, CERCAR con MURO si hay ≥ 1 escudo y ≥ 1 arquero, o con PINZA.

Es un punto de partida, no el objetivo. Andy quiere que la red decida, y el premio de equipo la dejará apartarse de estas
reglas.

**Sustituidas el 30-09:** estas reglas mandaban CERCAR casi siempre y hacían a los grupos peores que sin capitán (en el
simulador y en el mod). Las nuevas, que solo dan una orden cuando ayuda (carga con el jugador expuesto, pinza si se
aleja, retirada, asedio, emboscada), están en `red_mob_v4_mod_estado.md`, "Capitán de reglas nuevo".

---

## 4. Los 11 puntos

Para cada uno se indica qué ve y decide la red, qué hace el ejecutor en el mod, qué tiene que añadir el simulador, y los
premios con sus trampas. Los premios son **propuestas de peso inicial** (× 0,1 por decisión, como hoy) para
`PESOS_V4` en `entrenar_forja.py`. Todos empiezan apagados (0) salvo donde se dice.

### 4.1 Formaciones (escudos delante, arqueros detrás y cubiertos, rápidos por el flanco)
- **Red del mob:** ve M (formación, puesto, punto, `cubierto`), S (sector) y v3b. Decide FORMACION o no, y el escudo
  (defensa 1).
- **Capitán:** elige la formación y el puesto de cada miembro.
- **Ejecutor (mod):**
  - `Squad` convierte formación y puesto en un punto (§3.2) y lo recalcula cada 10 ticks con el "frente lento" de 2.2
    (media de 1 s de la mirada del jugador).
  - FORMACION = ruta al punto (1,0; `wantsRun` si está a > 5), por el anillo de fuera si tiene que cruzar por delante del
    jugador (`toRing` con r + 3).
  - Llegado al punto, mira al jugador. Un miembro de frente con escudo **no** lo sube solo: lo decide la red.
- **Simulador:**
  - geometría de formaciones y puestos (copiar `Squad` cuando llegue);
  - `cubierto` (reutiliza `allyInLineFrom`).
- **Premios:**
  - `formacion`: equipo, +0,02 por tick si ≥ 60 % de los miembros están a < 2 de su puesto con el jugador a < 16;
  - `cubierto`: +0,5 por flecha o proyectil del jugador que para un aliado del frente (bloqueo) mientras un arquero de
    segunda línea tenía línea libre;
  - `arquero_detras`: arquero en segunda línea con línea libre +0,01 por tick.
- **Trampas:** la formación por sí sola no puede dar más que el daño. El premio de formación se reduce a 0 si el grupo no
  hace daño en 200 ticks, así no se quedan "posando". Se retira de forma gradual (1 → 0 en 2 000 iteraciones), como la
  imitación.

### 4.2 Capitán (foco, retirada, carga sincronizada; matarlo desorganiza)
- **Red del capitán:** ve la observación de 213 y decide las 60 salidas (§3.2, §3.3). La red del mob del propio capitán
  sigue decidiendo su movimiento. `soy_capitan` = 1, y conviene que aprenda a no exponerse: la orden ESCOLTA protege al
  capitán.
- **Ejecutor (mod):**
  - `Squad`:
    - designa al capitán: líder con amenaza ≥ élite; opción de configuración para que un veterano pueda serlo;
    - guarda la orden y hace la cuenta atrás;
    - aplica `foco`: con 2 jugadores, cambia el objetivo de los miembros sin turno, como `share`;
    - aplica `sin_mando` al morir el capitán.
  - Señales visibles:
    - un estandarte o una partícula sobre el capitán;
    - un grito al dar la orden CARGA y a la mitad de la cuenta atrás, porque el jugador tiene que poder leerlo;
    - la orden en `/forja ia ver`.
- **Simulador:**
  - capitán en `multi.rs` y `reglas.rs` (líder, desbandada y escuadra ya existen);
  - `CaptainRules`;
  - lote del capitán en `LoteForja`: observación M×213, máscara y acción de 6 columnas + 8 puestos;
  - grito y cuenta atrás;
  - premio de equipo del capitán.
- **Premios:**
  - capitán: la suma del premio de equipo de todos los miembros en sus 10 ticks (daño al jugador + 10 por muerte del
    jugador − pérdidas del grupo × 2) + `carga_sync`: +1 por cada miembro que golpea en los 20 ticks siguientes al 0 de la
    cuenta atrás;
  - miembros: el de equipo de hoy + `obedecer` (+0,02 por decisión con la táctica que corresponde a la orden, 1 → 0 en
    2 000 iteraciones).
- **Trampas:**
  - el capitán no cobra por dar órdenes, solo por su efecto, así que no sirve cambiar de orden cada 10 ticks. Además,
    cada cambio de orden cuesta −0,1;
  - con CARGA, un +1 turno mal medido rompe el equilibrio de los turnos: hay que medirlo antes con los "botones" de
    `py_mobs_perillas`;
  - para comprobar que matar al capitán importa hay una métrica, no un premio (§6.5).

### 4.3 Moral (huir, reagruparse o entrar en furia cuando muere el líder o la mitad)
- **Red del mob:** ve Mo, M (`sin_mando`) y los rasgos. Con eso decide RETIRARSE (huir), REAGRUPARSE o **furia**
  (salida 51). Ya no hay "desbandada = RETIRARSE forzado" para las redes v4: esa regla queda solo para el cerebro de reglas.
- **Ejecutor (mod), la moral como estado** (reglas, igual que la postura):
  - **Moral del grupo:** `moral_grupo` = clamp(1 − 0,8·bajas_frac − 0,4·[capitán muerto hace < 200] − 0,2·miedo
    + 0,2·[jugador < 30 % de vida] + 0,1·en_casa, 0, 1).
  - **Moral propia:** `moral_propia` = clamp(moral_grupo + {agresivo +0,2, prudente 0, astuto 0, cobarde −0,2}
    − 0,3·(1 − vida_frac), 0, 1). Los intrépidos (élite, campeón y jefe) tienen como mínimo 0,8.
  - **Furia disponible:** hubo un "golpe de moral" en los últimos 200 ticks (capitán muerto o bajas_frac ≥ 0,5), el mob no
    es cobarde y no la ha usado en esta pelea.
  - **Furia** (números a aprobar por Andy):
    - dura 200 ticks: +25 % de daño y +20 % de velocidad; sin escudo, esquiva, finta ni retirada;
    - se ve: partículas rojas y un rugido;
    - después, 100 ticks de agotamiento: −20 % de velocidad y no puede tomar turno.
  - **Abandonar la pelea:** `retirada_ticks` ≥ 100 y a > 20 del jugador. El mob suelta el objetivo, sobrevive y suma una
    pelea (veteranía, fase 8). Es el "huir de verdad".
  - El miedo de la fase 8 (3 muertes en 200 ticks) deja de forzar RETIRARSE en las redes v4 y pasa a ser solo una entrada
    (`miedo` de v3b).
- **Simulador:**
  - moral, furia, agotamiento y abandono (salir del combate cuenta como sobrevivir);
  - quitar el RETIRARSE forzado para las redes v4 (opción `forja={"moral_v4": True}`).
- **Premios:**
  - `sobrevivir`: al terminar la pelea, si sigue vivo o ha abandonado, + {cobarde 3, prudente 2, astuto 1, agresivo 0}.
    **Solo** si el grupo sufrió un golpe de moral o si el mob hizo algún daño; si no, todos huirían desde el principio;
  - la furia no tiene premio propio: vale por el daño que hace (w_hecho), y el riesgo lo cobra el castigo por morir;
  - `reagrupar`: +0,3 si, tras reagruparse, ≥ 3 miembros vuelven a golpear en 100 ticks.
- **Trampas:**
  - "huir para no morir" solo tiene sentido si morir cuesta más que el premio de sobrevivir y el daño que deja de hacer.
    Con la personalidad ya sorteada, lo lógico es que el cobarde huya y el agresivo entre en furia. Hay que comprobarlo
    por rasgo (§6.5);
  - `abandonar` cierra el episodio del mob: se le cobra el `−3·(fin por tiempo)` normal si nunca hizo nada.

### 4.4 Emboscadas (esquinas, puertas, cuevas oscuras; esconderse de noche y atacar a la vez)
- **Red del mob:**
  - ve E (escondites, `me_ve_jugador`, `jug_luz`, pasillo, puerta, techo, `jug_sin_vernos`), P y M (orden EMBOSCADA,
    cuenta atrás);
  - decide EMBOSCAR y cuándo salir. Salir es elegir otra táctica: ACERCARSE, CARGA o FLANQUEAR. El salto sincronizado lo
    da la cuenta atrás del capitán.
- **Ejecutor (mod):**
  - **Escondite:** cada 10 ticks, por mob, se prueban 16 puntos (8 direcciones a 3 y 6 bloques) y, si los hay, las
    esquinas de paredes a ≤ 8. El escondite es el punto sin línea de vista del jugador. Se valora:
    - preferir la luz < 4, porque allí no se le ve aunque tenga línea;
    - preferir los puntos a 2–5 de la ruta probable del jugador (su velocidad × 40 ticks);
    - preferir la esquina, el marco de una puerta o una celda con techo.
  - **EMBOSCAR:** ruta al escondite a 0,8 y quieto allí, mirando al punto de salida. Quieto no hace ruido de pasos (§4.5).
  - **Cuevas:** el escondite con techo y luz < 4 cuenta como "oscuro" en `escondite_luz`.
- **Simulador (lo más grande del diseño):**
  - **Mundo:** el combate usa hoy un mapa de alturas de 64×64 con paredes (`tipo 'm'`) y un mapa `techo` que casi no se
    usa. Se propone **mantener el 2,5D** (el vóxel de `superv/` costaría demasiado):
    - plantillas de interior: pasillos de 1–3, puertas, esquinas en L y T y salas, con las medidas del Bastión de
      `PROPUESTA_V3_FORJA.md` §8;
    - cuevas: túneles con techo a 3–4;
    - una capa de **luz por celda**: cielo (15 de día, 4 de noche, 0 bajo techo) + luz de bloque de las antorchas
      (15 − distancia Manhattan, por un BFS que no atraviesa paredes).
  - **Percepción del jugador de reglas** (hoy lo ve todo): se fija en un mob si hay línea de vista y (dentro de su cono de
    70° y (luz ≥ 4 o a < 8)), si lo oye (pasos a < 6 si el mob se mueve, o a < 10 si corre), o si le pega. Parámetros por
    nivel, con ruido. El experto "oye" más lejos y gira la cabeza más a menudo (30 % del tiempo mira atrás).
  - **Jugador viajero:** en el escenario de emboscada el jugador va de A a B por pasillos o cuevas (misión) y solo pelea
    cuando detecta. Sin esto, emboscar no tiene sentido: el jugador de reglas siempre va a por el mob.
- **Premios:**
  - `sorpresa`: w_hecho × 0,5 extra por el daño de un golpe que llega antes de que el jugador se haya fijado en ese mob, o
    en los 10 ticks siguientes;
  - `emboscada_grupo`: equipo, +1 si ≥ 2 miembros golpean en los 20 ticks tras el primer golpe sorpresa.
- **Trampas:**
  - esconderse para siempre: fin por tiempo (−3), y el premio de daño rápido (`rapido`) sigue activo;
  - no se premia "estar escondido" por sí solo, para no enseñar a la red a esconderse en vez de pelear;
  - hay que medir contra el bot PvE (`LoteForja(bot="red")`), no solo contra las reglas de percepción, que se pueden
    explotar;
  - Andy (jugador real) ve a los mobs aunque esté oscuro si tiene brillo alto. Por eso la luz < 4 da ventaja, pero no
    invisibilidad (§7).

### 4.5 Oído y rastro (correr, minar, comer; buscar en la última posición)
- **Red del mob:** ve P (percibido, oído, última posición, 2 sonidos con tipo y fuerza, `buscando`) y decide BUSCAR, a
  dónde (mover en LIBRE hacia el sonido) y cuándo dejarlo.
- **Ejecutor (mod):**
  - **Sonidos:** un `GameEventListener`, como el sculk, por mob que piensa o que caza. Eventos y radios, con los pasos del
    jugador agachado a 0:

    | Tipo | Eventos | Radio |
    |---|---|---|
    | movimiento | `STEP` andando | 6 |
    | movimiento | corriendo | 12 |
    | movimiento | `HIT_GROUND` | 10 |
    | trabajo | `BLOCK_DESTROY`, `BLOCK_PLACE` | 16 |
    | trabajo | `BLOCK_OPEN`/`CLOSE` (puertas, cofres) | 12 |
    | comer o beber | `EAT`, `DRINK` | 8 |
    | combate | `PROJECTILE_SHOOT` | 16 |
    | combate | golpes y escudo | 12 |

    Sin línea de vista, el radio se divide por 2. Se guardan los 2 sonidos más recientes del jugador que se persigue.
  - **Estimación:** mientras `obj_percibido` = 0, el "jugador" de la observación y del ejecutor es la última posición
    conocida, que el sonido más reciente sustituye si es más nuevo. Los números del jugador (estamina, escudo, carga...)
    se congelan en lo último percibido. **El ejecutor navega a la estimación, no a `getTarget()`.** Hoy la navegación
    vanilla va a la posición real, y eso es hacer trampa.
  - **Seguir cazando:** el mob sigue pensando (y conserva su objetivo) hasta 48 bloques y hasta `BORED_TICKS` (600) sin
    percibirlo. Hoy piensa solo con el jugador a ≤ 32.
  - **BUSCAR:**
    1. ruta a la última posición (1,0);
    2. luego al sonido más reciente;
    3. después, 3 puntos a 6 bloques en abanico de ±60° hacia donde iba el jugador;
    4. termina al percibirlo.
  - Sustituye a `MovementGoals.Track` para las redes v4.
- **Simulador:**
  - eventos de sonido del jugador de reglas: pasos al andar o correr, colocar al hacer un pilar, comer con < 40 % de vida
    y disparos;
  - estimación en la observación y en el ejecutor;
  - mapas con esquinas (§4.4) para perderlo de vista;
  - combate más largo (1 800 ticks) en estos escenarios.
- **Premios:**
  - `reencontrar`: +0,5, como mucho una vez cada 200 ticks por mob, cuando el jugador vuelve a ser percibido tras ≥ 40
    ticks perdido y a > 8 de distancia;
  - `perdido`: −0,005 por tick de equipo con el jugador sin percibir por nadie.
- **Trampas:**
  - "perderle a propósito para cobrar el reencuentro": el tope de una vez cada 200 ticks y el castigo por tick lo hacen
    rentable solo si el jugador se esconde de verdad;
  - hay que comprobar en el mod que la estimación **nunca** filtra la posición real: prueba de juego con un jugador detrás
    de una pared, sin ruido.

### 4.6 Contra los trucos del jugador sin construir ni romper: pilar y torre (en detalle)

**Qué se quita:**
- `MovementGoals.Builder` entero (excavar bloques blandos y pilar de tierra) y su registro en `MobAi`;
- la frase "zombies dig and climb their way in" del asedio (`WorldFights.siege`);
- `BreakDoorGoal` de vanilla (los zombis rompen puertas en Difícil);
- las demás roturas y colocaciones de bloques que ya existen están en §7 (preguntas).

**Qué ve la red:** A (pilar, torre, borde, caída si lo empujan, trepable, tiempo arriba, si dispara desde arriba), C
(inventario), P y la orden ASEDIO.

**Las 9 herramientas:**

| # | Herramienta | Quién | Mecánica (ejecutor) | Aviso | Enfriamiento | Máscara |
|---|---|---|---|---|---|---|
| 1 | **Flecha de empuje** (especial, hueco 3 del arquero) | esqueleto, stray, bogged y saqueador | flecha con empuje horizontal 0,9 en la dirección tirador→jugador y +0,15 vertical (como Retroceso II); daño × 0,5 | 20 ticks: la flecha brilla y suena | 160–240 | `jug_sobre_suelo` ≥ 2, o `jug_borde` < 0,6, o `peligro_tras_jugador` (sirve también junto a la lava) |
| 2 | **Carga de viento** (objeto 7) | cualquiera que la lleve (veterano 30 %, élite 60 %: 1–3) | la `WindCharge` de vanilla lanzada al jugador: estallido de radio 2,5, empuje 1,2 y hacia arriba 0,6. **Al levantarlo del suelo, agacharse ya no le protege del borde** | 8: brazo arriba | 60 | `lanzamiento_ok` |
| 3 | **Araña que trepa y empuja** | araña y araña de cueva | trepar ya es vanilla: sube por el lateral de un pilar 1×1 (`pared_trepable`). Nuevo especial en el hueco 3, **zarpazo**: golpe con empuje horizontal 1,0 | 6 | 80–120 | a < 2 del jugador y (`jug_sobre_suelo` ≥ 2 o en el borde) |
| 4 | **Garfio / agarre** | Tenaza (agarre ≤ 9, ya existe); zombi o piglin con caña de pescar recogida (punto 7) | tirón de 1,0 hacia el mob: en la cima de un 1×1, lo baja | 10: se ve el anzuelo | 120–200 | línea de vista y a ≤ 9 (Tenaza) o ≤ 12 (caña) |
| 5 | **Pociones arrojadizas** (objeto 4) | cualquiera con pociones (bruja por reglas) | daño instantáneo, veneno, lentitud o debilidad lanzada en parábola al punto previsto (adelanto 1,0); radio 2 | 10 | 40 | `lanzamiento_ok` |
| 6 | **Perla al pilar** (objeto 5) | élite o campeón con 1–2 perlas | la perla de vanilla: donde choca, aparece el mob. Si choca con el jugador en la cima, el mob aparece en su sitio y los dos se empujan: casi siempre cae uno | 8 | 100 | `perla_destino_ok` |
| 7 | **Creeper al pie** | creeper | la explosión de siempre, **sin romper bloques** (pregunta a Andy). Radio de daño 3: llega a un pilar de ≤ 2–3 de alto y el empuje de la explosión lo saca | mecha | — | como hoy |
| 8 | **Fuego** | blaze (red propia) y lanzadores del mod | bolas de fuego al pilar: arder obliga a moverse o a bajar al agua | propio | propio | propio |
| 9 | **Asedio** (táctica ASEDIAR + orden ASEDIO) | el grupo | anillo de asedio: los de cuerpo a cuerpo a 6–10 de la base, en un punto sin línea desde la cima si lo hay (como OCULTARSE) y repartidos por hueco para cortar la bajada; los arqueros a 12–16 con línea. Se combina con apagar las antorchas de la base (4.10) y con la cuenta atrás: cuando baja (`jug_sobre_suelo` < 1), CARGA | — | — | `jug_sobre_suelo` ≥ 2 o `jug_en_torre` |

**Contra una torre cerrada o un búnker 1×1 sin huecos** solo quedan las pociones lanzadas por encima, las cargas de
viento y la perla si hay ventana, esperar escondidos (4.4) y apagar las antorchas. **Un jugador completamente encerrado
está a salvo**, igual que en vanilla. Es una decisión de diseño que tiene que confirmar Andy (§7).

**Simulador:**
- **El jugador de reglas con estilo "pilar":** cuando se acercan ≥ 3 mobs, sube la altura de su celda +1 cada 10 ticks
  (salta y coloca, con el sonido de colocar) hasta 3–8, y dispara con arco desde arriba.
- **Estilo "torre":** una plantilla de torre de 4–6 de alto con almenas y techo parcial, con escalera, y el jugador
  arriba.
- **Empuje en el borde y caída:** la caída ya existe (EMPUJAR). Agacharse protege del borde **solo en el suelo**; el
  empuje con componente vertical lo anula (hay que comprobarlo en el juego, §7).
- **Física de los lanzamientos:** carga de viento, perla (gravedad 0,03, rozamiento 0,99), pociones (0,05 y 0,99) y
  caña (tirón).
- **Especiales nuevos:** flecha de empuje y zarpazo.

**Premios:**
- `bajarlo`: equipo, +3 cuando el jugador pasa de `jug_sobre_suelo` ≥ 2 a < 1 (cae o baja), y +w_hecho × 0,5 extra por
  el daño en los 100 ticks siguientes;
- `daño_arriba`: w_hecho × 0,5 extra por daño al jugador mientras está arriba;
- `expuesto`: −0,01 por tick para cada mob con línea de vista desde la cima, a ≤ 20 y sin cobertura, mientras el
  jugador tira desde arriba. Enseña a no quedarse debajo muriendo, que es lo que haría hoy un v3s sin el Builder.

**Trampas:**
- "Bajarlo" se cobra una sola vez por subida y solo si llevaba ≥ 40 ticks arriba, para que no se cobre por saltitos de 1
  bloque.
- La flecha de empuje contra un jugador en el suelo junto a la lava ya la cubre el premio `peligro` de v3 (daño por lava
  o caída tras mi golpe).
- Hay que medir que no aprendan a "disparar empujones" sin sentido cuando el jugador está en llano: la máscara lo impide.

### 4.7 Recoger mejores armas del suelo (también la del jugador)
- **Red del mob:** ve O (2 objetos: posición, `mejora`, `del_jugador`, tipo) y decide RECOGER, o cambiar de arma (objeto 8)
  si lleva otra.
- **Ejecutor (mod):**
  - RECOGER: ruta al objeto (1,1); a ≤ 1 lo coge (`canPickUpLoot` para todos los que piensan) y lo equipa si es un arma
    con `mejora` > 0. Si es un consumible, lo guarda (3 ranuras de mob) y si es un escudo va a la mano izquierda.
  - Tarda 10 ticks, con animación.
  - **Valor de un arma** (una sola función, compartida con el simulador): daño × velocidad de ataque × (1 + 0,15·alcance
    extra) × (1,3 si es forjada con mejoras). Arco y ballesta: daño de flecha × cadencia, y solo son "mejora" para la
    familia arquero, o para un mob sin arma.
  - **Cambio de familia:** si coge un arco, pasa a arquero (`MobFamily.of` por el arma ya existe), cambia de red y su
    memoria vuelve a cero.
  - **El arma del jugador:** los mobs pueden coger lo que el jugador suelta (Q, muerte, robo del ladrón). Al morir, el mob
    la suelta al 100 %, como el ladrón. Nunca desaparece.
- **Simulador:**
  - objetos en el suelo: armas que sueltan los mobs al morir (probabilidad por escenario, más alta que la de vanilla para
    que salga en el entrenamiento);
  - armas sembradas en el mapa;
  - cambio de familia en mitad del combate (hoy fija).
- **Premios:**
  - `mejora_arma`: +0,2 × mejora (tope 1), una vez por objeto, retirado de forma gradual 1 → 0 en 3 000 iteraciones;
  - lo que cuenta de verdad es el daño que hace después.
- **Trampas:**
  - soltar y recoger la misma arma: el premio es una vez por objeto y solo si `mejora` > 0;
  - 40 ticks entre cambios;
  - no recoger en medio del alcance del jugador: no se prohíbe, lo cobra el daño que recibe.

### 4.8 Consumibles (beber, lanzar, comer, perlas)
- **Red del mob:** ve C (inventario, efectos propios y del jugador, `perla_destino_ok`, `lanzamiento_ok`) y decide la
  cabeza `objeto`.
- **Ejecutor (mod):**
  - **Qué llevan:** inventario al aparecer, por amenaza y dificultad:
    - normal: 10 % comida;
    - veterano: +20 % una poción de curación, +15 % una arrojadiza, +30 % carga de viento;
    - élite: curación, arrojadiza, 60 % carga de viento, 40 % perla;
    - campeón: todo, 2 de cada.
  - **Visible:** poción o perla en la mano durante el aviso.
  - **Beber:** 32 ticks, a 0,5 de velocidad. Se interrumpe si lo aturden, y la poción no se pierde. Curación: +4 de vida
    por nivel. Mejora: el efecto de vanilla, 90 s.
  - **Comer:** 32 ticks, +4 de vida directo (los mobs no tienen hambre).
  - **Lanzar poción:** 10 ticks de aviso y parábola al punto previsto.
  - **Perla:**
    - para acercarse: al punto a 2 del jugador en mi lado, o al propio jugador si está en un pilar;
    - para escapar: a un punto a 12–16, en lo posible escondido;
    - 2 de daño al mob.
  - **Carga de viento:** §4.6.
  - Cambiar de arma: 20 ticks.
- **Simulador:**
  - inventario de mob;
  - efectos (fuerza +3 de daño, velocidad +20 %, regeneración, veneno 1 cada 25 ticks, lentitud −15 %, debilidad −4);
  - físicas de lanzamiento y teletransporte;
  - pociones del jugador de reglas: hoy "no existen nubes de pociones del jugador", y esto no lo cambia.
- **Premios:**
  - curarse: w_rec × vida recuperada, es decir, lo contrario del daño recibido;
  - daño y efectos al jugador: w_hecho × daño; veneno y lentitud × 0,5 × duración/100;
  - `desperdicio`: −0,2 por poción que no alcanza a nadie o curación con > 80 % de vida;
  - `perla_util`: +0,5 si golpea en los 40 ticks tras teletransportarse cerca, o si sobrevive tras escapar con < 30 % de
    vida.
- **Trampas:**
  - "beber para no pelear": beber es lento y a media velocidad, y el premio `rapido` sigue activo;
  - no hay premio por usar un objeto porque sí.

### 4.9 Escudo inteligente (subirlo solo ante un golpe cargado o una flecha, bajarlo para contraatacar)
- **Red del mob:** ve G (cuánto lleva arriba, cuándo llega el golpe del jugador y si es fuerte, bloqueo reciente), P0/P1
  de v3b (proyectiles) y `jug_cargando` y `jug_carga`. Decide la defensa (0/1) tick a tick y el `golpe_escudo`.
- **Ejecutor (mod):**
  - la defensa 1 ya existe;
  - lo nuevo es el **golpe de escudo**: tras bloquear (≤ 20 ticks), 4 ticks de aviso, baja el escudo y empuja 0,8 y
    −15 de estamina al jugador, e interrumpe su carga. Enfriamiento 60;
  - **el coste de llevar el escudo arriba ya es real:** vanilla va más lento con el escudo, no golpea mientras bloquea, y
    el bloqueo pasa el 70 % a la postura;
  - con eso basta: no hace falta una regla de "bájalo".
- **Simulador:**
  - golpe de escudo;
  - `jug_golpe_en` y `jug_golpe_fuerte` (el jugador de reglas ya carga y tiene recarga).
- **Premios:**
  - `bloqueo_util`: +0,3 por bloqueo de un golpe cargado (≥ 0,8), de un especial o de una flecha; +0,1 de uno normal;
  - `escudo_ocioso`: −0,005 por tick con el escudo arriba y `jug_golpe_en` > 1 (no viene nada);
  - `contra`: +0,5 si el golpe de escudo, o un golpe con contraataque en los 40 ticks siguientes, acierta.
- **Trampas:**
  - "escudo siempre arriba para cobrar bloqueos": el castigo por tick ocioso, que no golpea y la postura lo compensan;
  - el jugador de reglas bueno o experto deja de cargar contra un mob que bloquea mucho (ya ajusta sus hábitos): eso es
    bueno, se adapta.

### 4.10 Apagar antorchas y luces (la única excepción a "no romper")
- **Red del mob:** ve L (2 fuentes con su aporte a la luz del jugador), `jug_luz`, `luz/15`, `noche` y W. Decide
  APAGAR_LUZ.
- **Ejecutor (mod):**
  - **Lista cerrada de bloques** (etiqueta de datos `forja:luces_rompibles`): antorchas (normal, de alma, de cobre y de
    pared), faroles (normal, de alma y de cobre), velas y calabazas iluminadas. Lo demás (piedra luminosa, farol de mar,
    hoguera, lámparas...) solo si Andy lo decide (§7).
  - **APAGAR_LUZ:** ruta a la fuente (1,0); al alcance (≤ 2 en plano, dy ≤ 2), 15 ticks de aviso (el mob golpea la luz,
    con partículas) y la rompe con `destroyBlock(pos, true)`, que **suelta el objeto** para que el jugador pueda
    recuperarlo.
  - Esta excepción funciona aunque `mobGriefing` esté apagado. Así es más justo con el jugador y más parecido al
    simulador; se puede cambiar en la configuración.
  - **Aviso a la red** (ya está en el mod): con la regla 98, un jugador en luz < 4 y a > 12 **no cuenta como visto por
    los mobs**. Apagar la luz también les ciega a ellos. La red tiene que valorar ese intercambio.
- **Simulador:**
  - antorchas como objetos con luz (capa de §4.4);
  - el jugador de reglas pone antorchas: estilo "antorchas", en cuevas o de noche cuando la luz < 7, una cada 8 bloques;
  - romperlas con su aviso.
- **Premios:**
  - `oscuridad`: equipo, +0,3 × (luz del jugador antes − después)/15 al romper, solo si hay jugador a ≤ 16 y es de noche o
    está bajo techo;
  - `daño_oscuro`: w_hecho × 0,2 extra por daño con `jug_luz` < 4.
- **Trampas:**
  - romper antorchas lejos o de día al aire libre: aporte 0, así que premio 0;
  - como mucho 1 premio por fuente y 5 por pelea;
  - no se premia romper por romper.

### 4.11 Adaptación por mundo (el jugador siempre les mata igual y lo evitan)
- **Red del mob y del capitán:** ven W (16 valores). No hay aprendizaje en línea en el mod: la red no cambia de pesos.
  Cambia lo que ve.
- **Ejecutor (mod), `WorldMemory`** (`SavedData` por jugador y dimensión):
  - **Causa de cada muerte de un mob que pelea con él:**
    - abierto: cuerpo a cuerpo en llano;
    - flecha: proyectil del jugador;
    - altura: el jugador estaba arriba (`jug_sobre_suelo` ≥ 2);
    - estrecho: el jugador en pasillo o puerta;
    - trampa: lava, caída, agua o empujón de un bloque;
    - área: barrido, sismo o runa;
    - fuego: fuego o explosión del jugador;
    - otra.
  - **Media móvil:** v ← v + 0,1·(onehot − v).
  - **Éxito por forma de atacar:** daño al jugador por vida de mob, agrupado por la táctica más usada en esa vida: frente
    (ACERCARSE, RODEAR y FORMACION de frente), flanco (FLANQUEAR y SECTOR detrás), distancia, emboscada y asedio. Media
    móvil 0,1.
  - **Estilo:** frecuencia de pilar o torre por pelea y de huida o encierro.
  - **Olvido:** el vector vuelve poco a poco a los valores iniciales con los días de juego (media del estilo "típico", ×0,9
    por día), así un jugador que cambia de estilo no queda marcado para siempre.
  - `/forja ia mundo [ver|borrar]`.
- **Simulador (campañas):**
  - un episodio de entrenamiento es una **campaña de K = 8 combates** con el mismo estilo de jugador (pilar, torre,
    pasillo, antorchas, huidor, tortuga con escudo, barrido de área, o mezcla);
  - el vector W se calcula con la misma fórmula a partir de las muertes de los combates anteriores de la campaña (el
    primero con los valores iniciales);
  - al tener que ser igual que el mod, la fórmula va en el contrato.
- **Premios:** ninguno propio. La adaptación sale sola si el vector predice qué táctica rinde contra ese estilo.
- **Trampas:**
  - si el estilo del jugador de reglas es demasiado fácil de leer, la red "juega a adivinar el estilo" y no a pelear. Por
    eso hay un 30 % de campañas con estilo mezclado o que cambia a mitad;
  - control: la misma red con W a 0 (§6.5).

---

## 5. Plan de entrenamiento

### 5.1 Fases (cada una necesita antes lo suyo en el mod y en el simulador, §6)

| Fase | Qué se activa | Currículo | Imitación | Criterio para pasar |
|---|---|---|---|---|
| **T0 Migración** | contrato v4 con todos los bloques nuevos a 0 salvo S y R | el de v3s (grupos 3–6 al 50 %, agrupados 0,6, grandes 8–13 al 30 %, horda mixta) | la de v3s | **misma salida** que v3s it 29 500 con los bloques a 0 (logits < 1e-5, como `comprobar_red_forja`); batería estándar = v3s ± 3 % |
| **T1 Individual** | escudo (G y golpe de escudo), objetos (O y RECOGER), consumibles (C y objeto) | 1–3 mobs → grupos de 4; inventarios al 50 % | reglas simples: beber con < 40 %, lanzar a 4–8, recoger mejora > 0, escudo si `jug_golpe_fuerte`; peso 0,3 → 0,02 en 400 iteraciones | bloqueos útiles ≥ 2 veces más que v3s; ≥ 50 % de pociones útiles; batería estándar ≥ v3s |
| **T2 Trucos** | A y las 9 herramientas (§4.6); L y APAGAR_LUZ; mundo con luz y antorchas | estilos pilar, torre y antorchas al 40 % y el resto normal; primero pilares de 3, luego hasta 8 | reglas "asedio" del capitán de reglas (sin red de capitán aún: la orden la dan las reglas) | pilar: bajarlo en ≤ 60 s en ≥ 60 % (Maestro); +50 % de daño/min en los estilos |
| **T3 Percepción** | P, E, estimación y sonidos; BUSCAR y EMBOSCAR; interiores y cuevas; jugador viajero | 20 % → 40 % de escenarios de interior o noche; combate de 1 800 ticks | ninguna (el maestro de reglas no sabe emboscar) | reencuentro ≥ 70 % en 200 ticks; golpes sorpresa ≥ 1 por combate de noche; batería estándar ≥ v3s |
| **T4 Grupo** | M, Mo; capitán de reglas → **red de capitán**; formaciones, moral, furia y abandono | 4a: miembros con el capitán de reglas (1 500 iteraciones, `obedecer` activo); 4b: red de capitán desde la imitación de las reglas, miembros congelados 300 iteraciones; 4c: todo junto. 40 % sin capitán | el capitán imita a `CaptainRules` (1 → 0,05) | capitán red ≥ capitán reglas +10 % en grupos de 8; sin capitán ≥ v3s |
| **T5 Mundo** | W y campañas de 8 | 30 % de campañas mezcladas | — | combate 8 ≥ combate 1 +20 %; control W = 0 < +5 % |
| **T6 Pulido** | todo, con el bot PvE como rival al 20 % y los niveles del jugador (experto al 30 %) | — | 0,02 | objetivos de §5.3 |

**¿Por qué este orden?**
- Lo individual va primero porque es fácil de premiar y de medir, y el capitán necesita miembros que ya sepan usar sus
  herramientas.
- Los trucos (T2) van antes que la percepción porque solo necesitan el mapa de alturas y la luz.
- La percepción (T3) necesita la parte más cara del simulador (interiores y percepción del jugador).
- El grupo (T4) va al final porque es el más difícil de entrenar (reparto del mérito entre el capitán y los miembros).

### 5.2 Migración desde v3b (sin perder lo aprendido)
- **Partida:** `forja_mobs_v3s/modelo.pt` (it 29 500, la de Andy) → `forja_mobs_v4/`. **v3s no se toca** y puede seguir
  entrenándose aparte.
- **1.ª capa:** filas 0–279 copiadas; filas 280–467 a **cero**.
- **Capa 2:** copiada.
- **GRU 64 → 96:** pesos de las 64 unidades copiados; las 32 nuevas con todos sus pesos a 0 (§1.4). **Salida:** columnas
  de `h2` y de las 64 unidades copiadas; las de las 32 nuevas, a 0.
- **Logits nuevos:**
  - 8 tácticas nuevas con el modo "relativo" (fila de ACERCARSE − margen 5), que en v2 → v3 dio 0,7–1,8 % de uso
    frente al 0 % del sesgo −3;
  - cabeza de objeto: "nada" = +3, el resto 0;
  - furia y golpe de escudo: logit de p = 0,05.
- **Crítico:** misma migración (1.ª capa con ceros).
- **Estado de Adam:** migrado como en `migrar_opt_v2_a_v3` / `migrar_opt_a_v3b`.
- **Capitán:** red nueva, inicializada por imitación de `CaptainRules` (~200 iteraciones solo de imitación) antes de PPO.
- **Comprobación:** con todas las entradas nuevas a 0, `forward` de la v4 migrada = v3s (< 1e-5).

### 5.3 Cómo se mide
- **Batería estándar (la de siempre, para no ir hacia atrás):** `diag_forja.py --contrato v4`, 15 escenarios, n = 256,
  grupos de 4, jugador "bueno", con las mismas semillas. Tabla por nivel (novato, medio, bueno, experto).
- **Batería de grupos:** `diag_ia.py` agrupados, con 8 y 13 zombis, mixto de 8 y mixto de 13: rodeado %, amontonados,
  explosiones del creeper y línea de tiro tapada.
- **Batería v4 (nueva):** un escenario por estilo (pilar, torre, pasillo/puerta, cueva oscura, antorchas, huidor,
  tortuga, barrido) × {grupo de 4, mixto de 8} × {con capitán, sin capitán}. Métricas:
  - daño/min y proporción de muertes del jugador (las de siempre);
  - tiempo arriba medio y % de veces que se le baja (pilar y torre);
  - golpes sorpresa y reencuentros;
  - bloqueos útiles / ociosos y golpes de escudo que aciertan;
  - pociones útiles y perlas útiles;
  - luz media del jugador;
  - % de obediencia por orden;
  - por rasgo: % de huida, furia y abandono;
  - **efecto de matar al capitán:** daño/min del grupo en los 200 ticks siguientes frente a los 200 anteriores.
- **Campañas:** daño/min por número de combate (1..8), con y sin W.
- **Rivales:** reglas (4 niveles) y bot PvE (`LoteForja(bot="red")`) al 20 %, para no sobreajustar a la percepción de
  las reglas.

**Referencias hoy** (motor `py_mobs_comida`, grupos de 4, jugador "bueno"): reglas 49,5 de daño/min, muere 0,57, rodeado
3,1 %; red v3s it 29 500: 62,0 / 0,64 / 9,4 %.

**Objetivos v4:**

| Medida | Objetivo |
|---|---|
| Batería estándar | ≥ 62 de daño/min y muere ≥ 0,64 (tolerancia −3 %) |
| Estilos tramposos | ≥ +50 % de daño/min sobre v3s en cada uno (v3s sin el Builder, que es lo que habría si solo se quita) |
| Pilar | bajarlo en ≤ 60 s en ≥ 60 % de los combates (Maestro y Leyenda) |
| Capitán | red vs sin capitán +20 % de daño/min en grupos de 8; red vs reglas +10 %; matarlo baja el daño/min del grupo ≥ 25 % durante 200 ticks |
| Moral | cobardes con ≥ 40 % de huida tras un golpe de moral; agresivos con ≥ 30 % de furia |
| Mundo | combate 8 ≥ combate 1 +20 %; con W = 0, < +5 % |
| Rendimiento en el mod | ≤ 2,5 ms/tick con 50 mobs con redes v4 |

- **GPUs** (regla de Andy): 2060 y 3090 libres; la 4080 solo si Andy no juega. T0–T2 en la 3090; T3–T5, al ser más
  pesados, en la 3090 + 2060 (familias repartidas).
- **Tiempo estimado:** 1–2 días por fase contando las medidas. T4 es la más larga (3–4 días).

---

## 6. Reparto del trabajo (en orden)

### 6.1 Mod (sesión de la nube)
| Paso | Qué | Entrega |
|---|---|---|
| **M0** (ya, no depende de la v4) | quitar `MovementGoals.Builder` y su registro; la frase del asedio; desactivar `BreakDoorGoal` vanilla. Prueba de juego "ningún mob cambia bloques salvo luces" (con la lista de §7 según lo que decida Andy) | commit y aviso |
| **M1** | `ObsV4` (468 nombres; los bloques nuevos a 0 salvo S y R), `Tactic` + 8, `NetBrain` v4 (cabeza de objeto, furia, golpe de escudo, táctica de 21), `MobAi.check` por `formato`, carpeta `redes_v4`, opción `iaContrato`. Genera `red_mob_v4_contrato.json` con `writeContract`, como v2 y v3 | contrato para el simulador |
| **M2** | golpe de escudo (G); objetos en el suelo, valor de arma, RECOGER y cambio de familia (O); inventario de mob, beber, comer, lanzar, perla, carga de viento y cambiar de arma (C) | números en `COMBATE_ESPECIFICACION.md` |
| **M3** | bloque A; flecha de empuje, zarpazo de araña y caña para garfio; ASEDIAR; luces: etiqueta `forja:luces_rompibles`, APAGAR_LUZ, bloque L y `jug_luz` | |
| **M4** | percepción: `GameEventListener` de sonidos, última posición, **estimación en observación y ejecutor**, caza hasta 48 y 600 ticks; BUSCAR; escondites en caché y EMBOSCAR; bloques P y E | prueba "no filtra la posición real" |
| **M5** | capitán: designación, `CaptainRules`, `CaptainBrain` (NetBrain con 60 salidas), órdenes, cuenta atrás, foco, formaciones y puestos en `Squad`, señales visibles; moral, furia (números de Andy), abandono; quitar la desbandada y el miedo forzados para las v4; bloques M y Mo; `red_capitan_v4_contrato.json` | |
| **M6** | `WorldMemory` (`SavedData`), bloque W, `/forja ia mundo` | fórmula exacta en el contrato |
| **M7** | grabación JSONL v4 (con el capitán); rendimiento medido de nuevo (`tools/rendimiento.sh` con redes v4 falsas de su tamaño) | `RENDIMIENTO.md` |

### 6.2 Simulador (esta sesión)
| Paso | Qué | Depende de |
|---|---|---|
| **S0** | poder compilar Rust (Smart App Control, lo decide Andy) | — |
| **S1** | `v4.rs` con el contrato a 0; migración v3b → v4 en `forja_red.py` (GRU 96 con relleno a 0); `comprobar_red_forja.py` v4; prueba de igualdad con v3s. **Entrenar T0** | M1 |
| **S2** | mundo 2,5D: luz por celda, antorchas, plantillas de interior (pasillos, puertas, esquinas, cuevas con techo), pilares y torres | — (puede ir en paralelo con M2) |
| **S3** | jugador de reglas con estilos (pilar, torre, pasillo, antorchas, huidor, tortuga, barrido), percepción (cono, luz, oído) y jugador viajero | S2 |
| **S4** | mecánicas de M2 y M3 copiadas del código: golpe de escudo, objetos, inventario, efectos, lanzamientos, perla, carga de viento, flecha de empuje, zarpazo, garfio y apagar luz. **Entrenar T1 y T2** | M2, M3, S2, S3 |
| **S5** | sonidos, última posición y estimación; BUSCAR y EMBOSCAR. **Entrenar T3** | M4, S3 |
| **S6** | capitán de reglas, órdenes, formaciones, moral, furia y abandono; lote y PPO del capitán (premio de grupo). **Entrenar T4** | M5 |
| **S7** | campañas y vector W. **Entrenar T5 y T6**; batería v4 completa y resumen para Andy | M6 |

Cada paso del simulador copia el código del mod leído con `git show`, sin cambiar la rama. Guarda copias `.antes-v4` y
pasa las pruebas de siempre: `forja_f1`, `v3`, `rodeo`, `finta`, `mobs_1c` 145/145 y `blaze`. Así el modo v3b sigue
igual bit a bit.

---

## 7. Riesgos y preguntas para Andy

## 7.0 Decisiones de Andy (29-09-2026), mandan sobre el resto del documento

1. **Lo que se queda** (no cuenta como construir o romper): la telaraña de la araña, el fuego del Cargador de Carbón y del Herrero Caído, `ThrownHead`, las explosiones del creeper, el enderman que coge bloques y los zombis que rompen puertas. Solo se quitan el Builder (excavar y pilar), y eso ya está hecho en el mod (M0, 6c62cc0).
2. **Luces:** solo **antorchas**: antorcha, antorcha de pared, antorcha de almas y antorcha de almas de pared. Nada de faroles, piedra luminosa, velas ni calabazas. Solo si `mobGriefing` está activado; si no, la máscara de `APAGAR_LUZ` va a 0. El premio de `APAGAR_LUZ` va por el **nivel de luz** (que aparezcan mobs y el sigilo: luz < 8 donde está el jugador o en el camino de los aliados), no por lo oscura que se vea la pantalla.
3. **Furia:** +25 % de daño y +20 % de velocidad durante 10 s, luego 5 s agotado. Solo la puede usar **el 10 % de los mobs de un grupo** (al menos 1 en grupos de 5 o más; se elige al aparecer). A los demás se les enmascara.
4. **Capitán:** solo **élites y campeones**, nunca veteranos. Un grupo sin élite ni campeón no tiene capitán (el Squad sigue con sus reglas). La **carga sincronizada da +1 turno** durante 2 s, con un grito visible.
5. **Búnker cerrado:** está **a salvo**, como en vanilla; una base segura no se debe poder abrir. `ASEDIAR` solo espera y embosca en la salida; en el simulador, "encerrado del todo" termina el combate como empate sin castigo para los mobs.
6. **Objetos de los mobs:** las perlas, cargas de viento y pociones que usan **no se sueltan** al morir. El arma que recogen del suelo (también la del jugador) sí se suelta, como cualquier objeto recogido.
7. **Brillo:** el brillo del juego solo cambia lo que ve Andy en pantalla, no lo que ven los mobs. La percepción del simulador usa el nivel de luz del bloque (como el mod), no el brillo.

**Preguntas originales (ya contestadas arriba):**

1. **¿Qué cuenta como "romper o construir"?** Además del Builder, hoy cambian bloques:
   - la telaraña de la araña (`VanillaSpecials`, temporal, 100 ticks);
   - el fuego del Cargador de Carbón y del Herrero Caído;
   - `ThrownHead` (rompe un bloque);
   - las explosiones del creeper (vanilla, con `mobGriefing`);
   - el enderman que coge bloques (vanilla);
   - los zombis que rompen puertas.

   Propuesta:
   - quitar la rotura de puertas;
   - explosión del creeper sin romper bloques;
   - enderman sin coger bloques;
   - telaraña convertida en un efecto (lentitud fuerte 60 ticks en la marca, con partículas de tela) y sin bloque;
   - el fuego de los mobs del mod, ¿se queda como "efecto" o se quita?
2. **¿Qué luces se pueden romper?** Propuesta: antorchas, faroles, velas y calabazas iluminadas, soltando el objeto.
   ¿También piedra luminosa, farol de mar u hoguera? ¿Aunque `mobGriefing` esté apagado?
3. **Furia:** ¿vale +25 % de daño y +20 % de velocidad durante 10 s y luego 5 s agotado? ¿Pueden entrar en furia los
   normales o solo veteranos y élites?
4. **Capitán:** ¿solo élites y campeones, o también veteranos? ¿Un +1 turno durante 2 s tras la carga sincronizada (con
   grito visible), o sin cambiar los turnos?
5. **Encerrado del todo:** ¿aceptamos que un jugador en un búnker cerrado está a salvo (como en vanilla), o quieres otra
   herramienta sin romper? Por ejemplo, que el grupo "acampe" y aparezcan refuerzos.
6. **Objetos de los mobs:** las perlas, cargas y pociones que llevan los mobs, ¿se sueltan al morir? Si se sueltan, es una
   fuente de perlas y cargas de viento que se puede farmear; propuesta: no se sueltan, salvo el arma del jugador.
7. **Oscuridad:** tu brillo en el juego cambia mucho lo que ves. La percepción del simulador asume que con luz < 4 y a > 8
   cuesta ver. ¿Con qué brillo juegas?

**Riesgos:**
- **Agacharse contra el empuje.** No sabemos si agacharse al borde anula el empuje horizontal en la versión del mod. Hay
  que probarlo con una prueba de juego antes de M3. Si lo anula, las herramientas 1 y 3 pierden mucho, y lo que vale es
  lo que levanta (carga de viento, explosión, perla).
- **Percepción explotable.** La red puede aprender a engañar a la percepción del jugador de reglas y fallar contra Andy.
  Mitigación: parámetros con ruido por nivel, el bot PvE como rival y grabaciones de Andy para comparar.
- **Estimación que se filtra.** Si el mod calcula alguna entrada con la posición real mientras el jugador no se percibe
  (por ejemplo `jug_*` de hábitos, que no importan, o `obj_vel`, que sí), la red "hace trampa" en el juego y no en el
  simulador. Hay una prueba en M4.
- **Mérito del capitán.** Con 10 ticks entre órdenes y efecto retardado, el PPO del capitán aprende despacio. Mitigación:
  imitación de las reglas, λ alto (0,97) para el capitán, y los miembros congelados al principio de T4.
- **Miembros que ignoran órdenes.** Si obedecer no mejora el premio de equipo, las ignoran. Eso significa que las órdenes
  de las reglas no valen, no que haya un fallo. Se mide el % de obediencia por orden.
- **Coste del simulador.** Luz, interiores, percepción y 1 800 ticks: el entrenamiento irá quizá a 1/2 o 1/3 de
  decisiones por segundo. Mitigación: los escenarios caros solo en el 20–40 % de los combates.
- **Mundo 2,5D.** Sin voladizos ni varios pisos de verdad: las cuevas son túneles con techo. Las torres con escalera
  interior se aproximan. Suficiente para esquinas, puertas, pasillos y pilares.
- **Tamaño y tiempo en el mod.** Con 3,1 MB por familia y 11 archivos son ~35 MB en `config/`. Si pesa, se exporta con 5
  cifras (−35 %) o con la GRU de 64 (−20 %).
- **Dos contratos a la vez.** v3b y v4 van por carpetas separadas. Hay que tener cuidado de no copiar redes v4 a
  `config/forja/redes/`: `MobAi.check` por formato lo evita.
- **Compilación de Rust.** Resuelto: Andy desactivó Smart App Control el 29-09.
