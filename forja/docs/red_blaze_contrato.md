# Contrato de la red del blaze (`red_blaze_v1`, versión 1)

Andy lo aprobó el 2026-09-29. El archivo que manda es `red_blaze_contrato.json`; este documento lo explica.

- **Por qué un contrato propio:** el blaze vuela y dispara ráfagas. En `red_mob_v3` no cabe sin romperlo
  (PROPUESTAS_IA_SIMULADOR.md §7.1).
- **Dónde va la red:** `config/forja/redes_v4/red_blaze.json` o `config/forja/redes/red_blaze.json`. Primero se mira
  en `redes_v4`.
- **Formato:** el archivo de la red lleva `"formato": "red_blaze_v1"`. Con otro formato (una red v3 o v4 de los
  monstruos, por ejemplo), el mod la rechaza y el blaze sigue con sus reglas (las metas de vanilla).
- **Tamaño:** 65 entradas y 22 salidas. La red es como las de siempre (dos capas tanh, una GRU y la salida), y decide
  cada 2 ticks.
- **Marco:** el de v3. "Delante" es la dirección horizontal del blaze al jugador; "derecha" es 90° a su derecha.
- **Valor neutro:** 0. Una entrada sin nada que medir vale 0.

## Entradas (65)

### YO (0-14): el propio blaze
- Vida, altura sobre el suelo (hasta 16 bloques; /8) y velocidad (vertical, hacia el jugador y de lado).
- Si está posado.
- La ráfaga: carga (0 a 1, en 20 ticks), si está cargado, si hay ráfaga en curso, bolas que le quedan en ella y la
  espera tras la ráfaga (/60).
- Si arde (no le hace nada), si está en el agua, si le llueve y si acaba de recibir daño.

### OBJ (15-38): el jugador
- Distancia horizontal y en 3D, diferencia de altura (negativa si el blaze está más alto) y su velocidad.
- Si pisa suelo y a qué altura del suelo está.
- Vida, escudo (levantado y activo), si lleva arco o ballesta y cuánto lo ha tensado, si lleva arma de cuerpo a cuerpo.
- Si mira al blaze, cuánto hace que golpeó, si corre.
- Si está en el agua (las bolas no le prenden), si arde, si tiene resistencia al fuego.
- Si tiene techo cerca (las bolas desde arriba chocan).
- Si su golpe alcanza al blaze ahora (su alcance real).
- Si el blaze lo ve y si hay un aliado en la línea de tiro.

### BOLAS (39-42): sus bolas de fuego en vuelo
- Cuántas tiene en vuelo.
- De ellas, la que pasa más cerca del jugador: a qué distancia pasa y en cuántos ticks. Sin bolas, 0 (mirar cuántas hay).
- Cuántas bolas le ha devuelto el jugador de un golpe.

### ALIADOS (43-53): otros blazes y aliados de tierra
- Cuántos blazes hay cerca y dónde está el más cercano (delante, derecha y altura).
- Cuántos monstruos de tierra pelean con el mismo jugador, dónde está el más cercano y a qué distancia del jugador.
- Cuántos están pegados al jugador (a 3 bloques o menos): lo están rodeando.

### ENTORNO (54-64): lo que tiene alrededor
- Si tiene techo cerca, si hay agua debajo (antes del suelo) y si hay agua junto a él.
- Paredes a 2 bloques en las 8 direcciones, a la altura de su cuerpo.

## Salidas (22): cinco cabezas
Cada cabeza se muestrea por separado (softmax con temperatura), nunca se coge el máximo. La primera opción de cada
cabeza nunca se prohíbe.

| Cabeza | Índices | Opciones |
|---|---|---|
| mover | 0-8 | quieto, las 8 direcciones de v3 (1 hacia el jugador, 5 alejarse) |
| vertical | 9-11 | mantener, subir, bajar |
| fuego | 12-14 | esperar, cargar, disparar |
| distancia | 15-17 | media (8-12), cerca (5-8), lejos (12-15) |
| tactica | 18-21 | acosar, rodear_alto, retirarse, esperar |

- **mover:** hacia dónde va en horizontal. En el aire el blaze se empuja poco, así que su velocidad se acerca a 0,1
  bloques por tick en esa dirección.
- **vertical:** la velocidad vertical que quiere: 0, +0,12 o −0,10 bloques por tick. Se acerca a ella poco a poco (un 35 %
  cada tick) y la gravedad no cuenta mientras manda la red, así que "mantener" lo deja flotando a esa altura.
  - No sube con un techo a 1,5 bloques o menos, ni por encima de 10 bloques sobre el suelo.
  - No baja a 1 bloque o menos del suelo.
  - Vanilla lo sigue empujando hacia arriba cuando los ojos del jugador están por encima de los suyos.
- **fuego:**
  - "cargar" suma un tick de carga, hasta 20. Al empezar a cargar se ve el aviso (llamas y sonido).
  - "disparar", con la carga completa, sin espera, a menos de 16 bloques y viéndolo, empieza la ráfaga: 3 bolas
    separadas 6 ticks y luego 60 ticks de espera. La carga vuelve a 0.
  - Cada bola solo sale si ve al jugador en ese tick. Apunta con adelanto al movimiento del jugador.
  - "esperar" conserva la carga.
- **distancia:** la banda de distancia que buscan "rodear_alto" y "esperar".
- **tactica:**
  - **acosar:** mover y vertical mandan tal cual.
  - **rodear_alto:** rodea al jugador a la distancia elegida y busca estar 4 bloques por encima de sus pies. La
    cabeza vertical no cuenta.
  - **retirarse:** se aleja del jugador en línea recta; la cabeza vertical cuenta.
  - **esperar:** se queda dentro de la banda de distancia; la cabeza vertical cuenta.
- La cabeza de fuego funciona en las cuatro tácticas.

## Máscara
- subir: techo a más de 1,5 bloques y menos de 10 bloques sobre el suelo.
- bajar: no posado y a más de 1 bloque del suelo.
- cargar: sin espera, sin ráfaga y sin la carga completa.
- disparar: carga completa, sin espera, sin ráfaga, a menos de 16 bloques y viéndolo.

## Premios que sugerimos para el entrenamiento
Juntan los de `red_mob_blaze.md` y los de PROPUESTAS §7.1:
- premiar mantener distancia (8-14 bloques; `red_mob_blaze.md` decía 6-12) y altura sobre el jugador;
- castigar quedar a tiro de espada y el cuerpo a cuerpo;
- premiar las ráfagas que impactan;
- premiar cubrir a los aliados de tierra: quemar al jugador mientras lo rodean;
- castigar quedar en el agua o bajo la lluvia (si el simulador lo modela).

## En el mod
- Observación: `ai/ObsBlaze.java` (`names()` tiene que coincidir con `nombres_obs`, en orden).
- Máscara, cabezas y muestreo: `ai/BlazeBrain.java`. Decisión: `ai/BlazeDecision.java`.
- Ejecutor en 3D: `ai/BlazePilot.java`. La ráfaga (`BlazePilot.burst`) es la misma que usan las reglas.
- Pruebas: `BlazeGameTests`.
