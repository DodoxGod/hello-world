# Red del blaze (familia "blaze"): qué tiene que modelar el simulador

Andy (2026-09-29): el blaze es demasiado distinto para compartir red, así que tiene su propia familia.
- **En el mod:** `MobFamily.BLAZE`, archivo `red_blaze.json`. Mientras no haya una red entrenada, el blaze pelea
  con sus metas de vanilla (las reglas); `MobAi.net("blaze")` devuelve null.
- **Contrato:** el mismo `red_mob_v3` (280 entradas, 34 salidas). No hace falta un v4.
- **Observación:** un blaze aliado aparece como "otro" en el one-hot de aliados (`MobFamily.slot()`); el orden de
  ese one-hot no cambia.

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

La red se carga desde `config/forja/redes/red_blaze.json`, como las demás. `MobAi.check` la rechaza si sus entradas
no coinciden con las del mod.
