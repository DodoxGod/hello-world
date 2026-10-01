# ObsV4 revisión 4.1 — "el jugador me apunta" (para la sesión del mod)

Fecha: 30-09. Simulador: `motor_rust/py_mobs_v4l`, `src/combate/forja/mira.rs`. Contrato: `motor_rust/red_mob_v4_contrato.json`
(ya regenerado como revisión 4.1; la 4.0 queda en `red_mob_v4_contrato_v40.json`).

## Por qué

Los mobs esquivan cuando Andy se acerca saltando (el crítico se ve), pero no cuando entra andando despacio con la mira encima
y el golpe cargado. En v4.0 solo había `obj_me_mira(cos)` (ángulo en el plano), `jug_cargando`/`jug_carga` (el golpe cargado
de Forja, no el enfriamiento vanilla) y los alcances. Faltaba "su mira está en MI caja" y "su golpe vanilla está listo".

## Qué cambia en el contrato

- `n_obs` 468 → **472**. Las 468 primeras **no cambian** (ni orden ni cálculo). Se añaden 4 al FINAL, bloque `J` (468, 4).
- `"revision": "4.1"` en el contrato y en el JSON de cada red (`red_<familia>.json`). Una red sin `revision` es 4.0 (468).
  `MobAi.check` sigue eligiendo por `formato: red_mob_v4` y comparando los nombres uno a uno: con 472 nombres → ObsV4 4.1.
- Salidas, cabezas, máscara, acción: **sin cambios** (53).
- Migración 4.0 → 4.1 (en Python): las 4 columnas nuevas de `w1` a 0. Con eso una red migrada ignora el bloque J (mismo forward).

| índice | nombre | valor |
|---|---|---|
| 468 | `jug_apunta_mi_caja` | 1 si el rayo de la mirada del jugador da en MI caja (ver "Rayo"); si no, 0 |
| 469 | `jug_apunta_dist/6` | distancia de los ojos al punto donde el rayo entra en mi caja, / 6, clamp [0, 2]; 0 si los ojos están dentro de la caja; **0 si 468 = 0** |
| 470 | `jug_golpe_listo` | `player.getAttackStrengthScale(0.5F)` (continuo 0..1; 1 = golpe vanilla lleno) |
| 471 | `jug_amenaza` | 1 si 468 = 1 **y** distancia ≤ `alcance` **y** 470 ≥ 0,9; si no, 0 |

**Neutros (el acuerdo): las 4 a 0** cuando:
- el mob no percibe al jugador (`obj_percibido` = 0: con el sustituto de Perception NO se calculan contra la estimación);
- no hay jugador objetivo;
- (en el simulador, sin `forja={"mira": True}`).

Nota: 470 se da aunque el jugador no me apunte (es estado del jugador, como `jug_carga`), siempre que lo perciba.

## Rayo (lo mismo que hace vanilla para el punto de mira, `GameRenderer.pick`)

1. `eye = player.getEyePosition()` (de pie 1,62 sobre los pies; agachado 1,27; vale lo que dé el método).
2. `look = player.getViewVector(1.0F)` (yaw **y pitch**). En el simulador el jugador no tiene pitch: allí el rayo va en su
   dirección horizontal con el pitch hacia la altura de su objetivo (clamp(ojos, pies + 0,1, cabeza − 0,1)). En el mod, el real.
3. `alcance` = el alcance del golpe del jugador: el atributo `entity_interaction_range` con el arma de la mano (= `jug_alcance/6`
   × 6, el mismo valor). **R = max(6, alcance)** (el rayo llega a 6 aunque el alcance sea 3: la red ve venir la amenaza antes
   de estar al alcance).
4. Bloques: `BlockHitResult b = level.clip(new ClipContext(eye, eye + look·R, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))`;
   `Rb = (b.getType() == MISS) ? R : eye.distanceTo(b.getLocation())`.
5. Entidades: `ProjectileUtil.getEntityHitResult(player, eye, eye + look·Rb, player.getBoundingBox().expandTowards(look·Rb).inflate(1),
   e -> !e.isSpectator() && e.isPickable() && e != player, Rb²)` → la entidad **más cercana** por el rayo (cajas
   `getBoundingBox().inflate(getPickRadius())`; si los ojos están dentro de una caja, esa con distancia 0).
   Cuentan TODAS las entidades elegibles (otros mobs, otros jugadores, animales): estar detrás de otro mob tapa.
6. Resultado del tick para ese jugador: `(idEntidad, distancia)` o nada. Para el mob `m`: 468 = (id == m.getId()),
   469 = distancia / 6, 471 = 468 && distancia ≤ alcance && 470 ≥ 0,9.

## Coste

**Un rayo por JUGADOR y tick, no por mob.** Guardar por jugador `(tick, idEntidad, distancia)` (p. ej. un `WeakHashMap<Player, Pick>`
o un campo en el estado de combate del jugador) y calcularlo la primera vez que un mob lo pida en ese tick; cada mob solo compara
su id (O(1)). El rayo es un `clip` de ≤ 6 bloques (unos 10 vóxeles) y una consulta de entidades en la caja barrida (las de
alrededor, típicamente < 15 pruebas de caja). Del orden de microsegundos por jugador y tick; despreciable frente a ObsV4.

## Qué hay en el simulador (para que cuadre)

- `forja={"mira": True}` calcula el bloque J; el entrenamiento `forja_mobs_v4` lo lleva desde el 30-09 (motor py_mobs_v4l).
- El jugador de reglas ahora se acerca a veces DESPACIO (30 %: sin correr a < 8, se para al borde de su alcance + 1 con la mira
  encima 4–20 ticks y el golpe listo, y entra) o SALTANDO (15 %: crítico). Solo simulador.
- Premio (solo entrenamiento): golpe AVISADO = el jugador golpea al mob y en los 10 ticks anteriores tuvo la mira en su caja,
  golpe ≥ 0,9 y a ≤ alcance + 1. Evitado (+0,3) o comido (−0,3). No hace falta nada en el mod.

## Comprobación sugerida en el mod (GameTest)

- Jugador mirando a un zombi a 2,5 (centro a centro, caja de 0,6): 468 = 1, 469 = 2,2/6, 471 = 1 con el golpe lleno.
- Otro zombi detrás en la misma línea: para el de detrás 468 = 0.
- Zombi a 7,7: 0. Zombi a 4: 468 = 1, 471 = 0 (alcance 3).
- Justo después de golpear: 470 = (0 + 0,5) / recarga (p. ej. 0,04 con 12,5 ticks) y 471 = 0.
- Una red 4.0 (sin `revision`) sigue cargando con ObsV4 4.0 (468).

## Aparte: la presión de armadura (f784553) ya está en el simulador

py_mobs_v4l copia la presión nueva (tope 0,60, 0,065 por golpe y la mitad bloqueado, espera 60, vaciado 0,0067, penetración
encadenada arma·rango ≤ 0,30 y con la presión ≤ 0,60). `jug_presion` sigue sin normalizar (0..0,6). Nada que hacer en el mod.
