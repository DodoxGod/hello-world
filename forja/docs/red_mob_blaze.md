# Red del blaze (familia "blaze"): qué tiene que modelar el simulador

Andy (2026-09-29): el blaze es demasiado distinto para compartir red, así que tiene su propia familia.
- **En el mod:** `MobFamily.BLAZE`, archivo `red_blaze.json`. Mientras no haya una red entrenada, el blaze pelea
  con sus metas de vanilla (las reglas); `MobAi.net("blaze")` devuelve null.
- **Contrato (actualizado el 2026-09-29):** ya tiene uno propio, `red_blaze_v1` (`red_blaze_contrato.json`, versión 1,
  explicado en `red_blaze_contrato.md`): 65 entradas y 22 salidas. Una `red_blaze.json` con `red_mob_v3` u otro
  formato ya no se acepta para el blaze.
- **Observación en las redes de los demás:** un blaze aliado aparece como "otro" en el one-hot de aliados
  (`MobFamily.slot()`); el orden de ese one-hot no cambia.

## Lo que el simulador tiene que copiar

| Aspecto | Vanilla (lo que hace el juego) | En el mod |
|---|---|---|
| Vuelo | Flota: sube si está por debajo de la cabeza del objetivo y cae despacio (sin daño por caída, gravedad reducida). No trepa ni salta. | Igual; el ejecutor mueve con la navegación normal y el blaze flota solo. |
| Ataque | Ráfaga de 3 bolas de fuego pequeñas, separadas unos 6 ticks, y después ~60-100 ticks de espera. Hasta ~16 bloques y viéndolo. Cuerpo a cuerpo solo si está pegado. | `TacticGoal.fireballs`: con "usar", ráfaga de `BLAZE_BURST` = 3 bolas cada `BLAZE_BURST_GAP` = 6 ticks, y luego `BLAZE_COOLDOWN` = 60 ticks. Hasta 16 bloques y con línea de visión. |
| Bola de fuego | `SmallFireball`: 5 de daño y prende fuego al impactar (a entidades y bloques). Va recta, sin gravedad, algo dispersa. | La misma entidad, apuntada al centro del objetivo. |
| Inmunidad | Inmune al fuego y a la lava. El agua, la lluvia y las bolas de nieve le hacen daño. | Igual (vanilla). |
| Vida | 20 | 20 |
| Movimiento | Lento (0,23), sin carrera. | Sin carrera: `MobSprint` lo trata como a cualquiera, pero en el aire no corre. |

**Recompensas que sugerimos para el entrenamiento:**
- premiar mantener 6-12 bloques de distancia y altura sobre el jugador;
- castigar entrar en cuerpo a cuerpo;
- premiar las ráfagas que impactan;
- castigar quedar debajo del agua o de la lluvia (si el simulador lo modela).

## Cuándo se usa

La red se carga desde `config/forja/redes_v4/red_blaze.json` o, si no hay, desde `config/forja/redes/red_blaze.json`.
Tiene que traer `"formato": "red_blaze_v1"`, las 65 entradas de `ObsBlaze` en su orden y 22 salidas
(`MobAi.checkBlaze`). Si no, se rechaza, el motivo queda en el registro y el blaze sigue con las reglas. Sin red, todo
sigue como antes.

## Nota tras la sección 7.1 de PROPUESTAS_IA_SIMULADOR.md

El simulador propuso para el blaze un contrato propio. **Ya existe (2026-09-29, aprobado por Andy):**
`red_blaze_contrato.json`, versión 1, con su explicación en `red_blaze_contrato.md`. En el mod:
- `ObsBlaze` da sus 65 entradas: el blaze (altura sobre el suelo, carga y ráfaga), el jugador (también su altura sobre
  el suelo), sus bolas en vuelo, los aliados y el entorno (techo, agua, paredes);
- `BlazeBrain` y `BlazePilot` hacen las 5 cabezas: mover, vertical (mantener, subir, bajar), fuego (esperar, cargar,
  disparar), distancia y táctica (acosar, rodear_alto, retirarse, esperar);
- la ráfaga es la misma que la de las reglas (`BlazePilot.burst`, que ahora usa también `TacticGoal.fireballs`); con la
  red, cada bola necesita ver al jugador y apunta con adelanto.

El simulador está bloqueado por el Control inteligente de aplicaciones de Windows (no deja compilar Rust). Lo tiene
que resolver Andy en su PC.
