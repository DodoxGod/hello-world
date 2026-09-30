# Red del blaze (familia "blaze"): qué tiene que modelar el simulador

Andy (2026-09-29): el blaze es demasiado distinto para compartir red, así que tiene su propia familia.
- **En el mod:** `MobFamily.BLAZE`, archivo `red_blaze.json`. Sin red, el blaze pelea con sus metas de vanilla (las
  reglas); `MobAi.net("blaze")` devuelve null.
- **Contrato (actualizado el 2026-09-29):** el del simulador, `red_blaze_v1` (`red_blaze_contrato.json`, versión 1,
  explicado en `red_blaze_contrato.md`): 324 entradas y 18 salidas. El contrato de 65 entradas y 22 salidas que dejó
  la sesión de la nube se quitó. Una `red_blaze.json` con `red_mob_v3` u otro formato no se acepta para el blaze.
- **Red entrenada:** `redes_entrenadas/red_blaze.json`.
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
Tiene que traer `"formato": "red_blaze_v1"`, las 324 entradas de `ObsBlaze` en su orden y 18 salidas
(`MobAi.checkBlaze`). Si no, se rechaza, el motivo queda en el registro y el blaze sigue con las reglas. Sin red, todo
sigue como antes.

## Nota tras la sección 7.1 de PROPUESTAS_IA_SIMULADOR.md

El simulador hizo el contrato propio del blaze y entrenó su red. En el mod:
- `ObsBlaze` da las 324 entradas: las 280 de `red_mob_v3b` vistas desde el blaze y 44 suyas (alturas, alcance del
  jugador, ráfaga, fuego del jugador, aliados junto al jugador y las 3 bolas de fuego más cercanas a él);
- `BlazeBrain` hace la máscara y sortea las 5 cabezas: mover, vertical (mantener, subir, bajar), usar, adelanto
  (0; 0,5; 1; 1,5) y retirarse;
- `BlazePilot` es el ejecutor: vuelo entre 2 y 5 bloques del suelo, ráfaga con 20 ticks de aviso visible y audible,
  3 bolas cada 6 ticks y 60 de espera, y puntería con adelanto. Los detalles están en `red_blaze_contrato.md`.
- La ráfaga de las reglas (`TacticGoal.fireballs`, sin aviso) no cambia.
