# Contrato de la red del blaze (`red_blaze_v1`, versión 1)

El archivo que manda es `red_blaze_contrato.json`. Lo escribe el simulador (`motor_rust/src/combate/forja/blaze.rs` y
`combate/forja_blaze.py`) y aquí se copia tal cual, sin tocarlo. Este documento lo explica y dice cómo lo cumple el mod.

El 2026-09-29 la sesión de la nube dejó aquí otro `red_blaze_v1` (65 entradas y 22 salidas, commit f4c1a00) que el
simulador nunca implementó. Se quitó: el único `red_blaze_v1` es el del simulador, con 324 entradas y 18 salidas.

- **Dónde va la red:** `config/forja/redes_v4/red_blaze.json` o `config/forja/redes/red_blaze.json`. Primero se mira
  en `redes_v4`. Hay una copia de la red entrenada en `redes_entrenadas/red_blaze.json`.
- **Formato:** `"formato": "red_blaze_v1"`, las 324 entradas de `ObsBlaze` en su orden y 18 salidas
  (`MobAi.checkBlaze` / `BlazeBrain.check`). Con otra cosa, el mod la rechaza, lo dice en el registro y el blaze sigue
  como en vanilla.
- **Red:** la de siempre (dos capas tanh, una GRU y la salida). Decide cada 2 ticks. La memoria vuelve a 0 al aparecer
  el blaze y al cambiar de objetivo.

## Entradas (324)

- **0-279:** las 280 de `red_mob_v3b` (con correr), en el mismo orden y con el mismo cálculo, vistas desde el blaze
  (`ObsM1`, `ObsForja`, `ObsV3`). El contrato fija para el blaze:
  - no está en los one-hot de tipo (`tipo_*` y `tipo_forja_*` a 0);
  - `yo_recarga/40` es su espera tras la ráfaga;
  - `avisando` y `aviso_progreso` valen 0 (su aviso va en `rafaga_*`);
  - un blaze aliado se ve como "otro" en `aliadoK_g_*`.

  Además, el mod pone a 0 `yo_arco/20` y `yo_fuego` (el blaze no tensa un arco ni arde; en vanilla `Blaze.isOnFire`
  es su marca de "cargado") y da en `yo_vy*5` la velocidad vertical de su vuelo, sin la gravedad de vanilla, que el
  simulador no tiene.
- **280-299, el blaze y el jugador:**
  - alturas sobre el suelo (del blaze y del jugador, /5);
  - ojos del jugador menos el centro del blaze (/4);
  - distancia del golpe del jugador al blaze, de pie y saltando (/6), y si le alcanza con su arma;
  - la ráfaga: cargando, progreso de la carga, en curso, bolas que quedan (/3), espera (/60) y si está lista;
  - si el jugador arde y cuánto fuego le queda (/100);
  - aliados de tierra a 3 bloques o menos del jugador (/5) y distancia del más cercano (/8, 2 si no hay);
  - ticks que tardaría una bola nueva en llegar al jugador (/20);
  - si está mojado (agua o lluvia);
  - sus bolas en vuelo (/3).
- **300-323, las 3 bolas de fuego más cercanas al jugador** (de cualquiera), 8 valores cada una:
  - si hay;
  - posición respecto al jugador en el eje blaze→jugador y a su derecha (/16) y altura (/4);
  - ticks hasta su punto más cercano a la caja del jugador (/20) y a qué distancia pasa (/2);
  - si es suya y si la desvió el jugador.

## Salidas (18) y máscara

| Cabeza | Tipo | Índices | Máscara |
|---|---|---|---|
| mover | softmax | 0-8 (quieto y 8 direcciones, como v3) | siempre |
| vertical | softmax | 9-11 (mantener, subir, bajar) | subir si altura < 4,95; bajar si altura > 2,05 |
| usar | σ | 12 | = `rafaga_lista` |
| adelanto | softmax | 13-16 (0, 0,5, 1, 1,5) | siempre |
| retirarse | σ | 17 | siempre |

El índice 0 de cada softmax nunca se prohíbe; una σ prohibida sale 0. Cada cabeza se sortea con logits/T (nunca el
máximo), con T = iaTemperatura × dificultad × amenaza.

## Ejecutor (`BlazePilot`)

- **Vuelo:** flota entre 2 y 5 bloques sobre el suelo que hay bajo su caja. La velocidad vertical se acerca a su
  objetivo: vy += (objetivo − vy) · 0,3. El objetivo es +0,12 para subir, −0,10 para bajar y 0 para mantener. Por
  debajo de 2 sube; por encima de 5 (sin suelo cerca) cae despacio (−0,05). Sobre el mismo suelo no sale de la banda.
  Mientras la red lo lleva, no actúan la gravedad de vanilla ni su subida hacia el jugador (`BlazeMixin`). No se hace
  daño al caer.
- **En horizontal:** la física del aire de vanilla. v += dir · 0,23 · 0,02 · k (k = 1, o 0,5 mientras carga o dispara),
  y luego vanilla lo mueve y deja 0,91 de la velocidad.
- **Retirarse:** anula mover y vertical: se aleja del jugador y sube.
- **Ráfaga:** con usar y la ráfaga lista (quieto, sin espera, a menos de 16 bloques y viéndolo), hace esto:
  1. 20 ticks de aviso: el blaze se enciende como el cargado de vanilla, con llamas que se le acercan, un bufido al
     empezar y un chisporroteo que sube de tono;
  2. 3 bolas pequeñas cada 6 ticks (la primera al acabar el aviso);
  3. 60 ticks de espera.

  La ráfaga empezada sigue aunque lo pierda de vista. Aturdido, se para donde está.
- **Puntería:** al centro del jugador más adelanto × su velocidad horizontal × los ticks de vuelo de la bola (dos
  vueltas de cálculo).
- **Escuadra:** el blaze no ocupa hueco en el anillo del `Squad` ni turnos de `AttackTokens`.

## Diferencias con el simulador

El mod sigue la física de vanilla donde el simulador la simplifica:
- **La bola:** vanilla aplica la aceleración antes de moverla, así que en el primer tick avanza 0,19 y no 0,1. El
  simulador mueve primero y acelera después. Los ticks de vuelo (`bola_ticks_al_jug`, el adelanto) y el punto más
  cercano (`bola*_t_cercano`, `bola*_fallo`) se calculan en el mod con el orden de vanilla, que es como vuela la bola
  de verdad. La diferencia es de un tick como mucho.
- **Las bolas de otros:** el mod cuenta todas las `Fireball` que llevan menos de 100 ticks en el aire, a 48 bloques o
  menos del jugador. El simulador cuenta todas las de su arena.
- **`jug_dy_ojos`:** usa la altura de los ojos real del jugador. El simulador usa siempre 1,62, también agachado.

Sin red, o con `iaModo` en reglas, el blaze es el de vanilla (`BlazeAttackGoal`: carga 60, 3 bolas, espera 100).
