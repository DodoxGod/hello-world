# Red de mobs v4: entradas cuyo "neutro" quizá no sea 0 (para que lo confirme el simulador)

Fecha: 29-09-2026 (paso M1 del mod). En M1, `ObsV4` da **0** en todos los bloques que aún no se calculan (M, Mo, P, E,
A, O, C, G, L y W), igual que el paso S1 del simulador. No se ha inventado ningún neutro distinto de 0 en el código.
Estas entradas, por lo que dice su significado (`red_mob_v4_diseno.md` §2), tendrían en la práctica un valor distinto
de 0 cuando "no pasa nada". Si el simulador quiere otro neutro, que lo diga y el mod lo copia.

## Bloques aún a 0

| i | Entrada | Por qué 0 no es "nada" | Neutro que parece correcto |
|---|---|---|---|
| 301 | `orden_ninguna` | one-hot de la orden: sin capitán la orden es NINGUNA (§1.3) | 1 |
| 314 | `formacion_libre` | one-hot de la formación: sin capitán la formación es LIBRE (§1.3) | 1 |
| 328 | `moral_grupo` | 0..1; 0 sería un grupo hundido | 1 (moral entera) |
| 329 | `moral_propia` | igual | 1 |
| 338 | `obj_percibido` | 1 si lo veo ahora; en una pelea normal casi siempre lo ve | 1 (o su valor real, que el mod ya sabe: `ObsM1.sees` + regla 98) |
| 379 | `me_ve_jugador` | 0 dice "no me ve", como en una emboscada | ¿1 en pelea abierta? |
| 380 | `jug_luz/15` | 0 es oscuridad total en los pies del jugador | su luz real, o la de `luz/15` de v3b |
| 390 | `jug_borde/2` | distancia al borde: 0 dice "está al borde de una caída" | 2 (tope, sin borde cerca) |
| 392 | `jug_alcanzable` | 1 si mi golpe le llega desde el suelo; en llano siempre | 1 |
| 436 | `jug_golpe_en/10` | 0 dice "su golpe ya está cargado y a su alcance" | ¿el tope (2)? |
| 438 | `bloqueo_hace/20` | el diseño dice "1 si nunca o hace > 20" | 1 |
| 452–459 | `mundo_muerte_*` | "suman 1"; todo a 0 no suma 1 (con `mundo_confianza` = 0 quizá no importa) | ¿1/8 cada una, o `mundo_muerte_otra` = 1? |

## Bloque S (ya se calcula): interpretaciones que conviene confirmar

- **`tiro_punto_dist/4` (292) en un mob que no es arquero:** la propuesta dice "arqueros: …; 2 si ninguno". El mod da
  **0** al que no es arquero (no aplica) y también al arquero con la línea ya libre. Solo da 2 al arquero con un aliado
  en la línea y ningún punto libre.
- **`creeper_encendido_cerca/7` (290):** 2 cuando no hay un creeper con la mecha encendida a menos de 7 (lo dice la
  propuesta; es el único neutro distinto de 0 que el mod pone, porque viene escrito así).
- **Frente de los sectores (280–283):** el mod usa la media lenta (unos 20 ticks, exponencial) de la mirada del jugador,
  no la dirección de llegada del grupo.
- **Hueco de arqueros y creepers:** no tienen hueco en el anillo; el mod usa el "hueco" donde ya están (el mismo que lee
  `hueco_delante`), así que su sector es donde están y `hueco_error/pi` ≈ 0. `hueco_estable/100` es 0 para ellos.
- **Signo de `hueco_error/pi` (284):** `wrap(hueco − mi ángulo)/π` con los ángulos del `Squad` (`atan2(z, x)` de
  Minecraft); positivo = el hueco está hacia la "derecha" del marco (−z, x). Si el simulador usa el signo contrario, hay
  que cambiarlo en uno de los dos.
- **`jug_cuadrantes/4` (288) y `jug_enzarzado/4` (289):** cuentan a todos los mobs que pelean con ese jugador, **incluido
  el propio mob**. `aliados_a_1.5/3` (286) y `aliados_mi_sector/4` (287) no lo cuentan.
- **`tiro_lado_libre` (291):** si valen los dos lados, da −1 (se prueba antes la izquierda).
