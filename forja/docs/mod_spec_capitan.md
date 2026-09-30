# Capitán de reglas nuevo: lo que dice el simulador (30-09, motor py_mobs_v4d) y qué proponer al mod

Resumen: **el simulador copia el capitán nuevo (61db6e9) y confirma que el viejo era un desastre** (−80 % con el jugador de guion).
**No confirma que el anillo libre sea "demasiado pasivo" en sí**: la carga casi no añade nada en el simulador (+6 de 249), mientras
que en el mod duplica el daño. Lo que sí sale en el simulador: **un turno más** en el anillo libre da ≈ +10 % y **la pinza de las
reglas cuesta** ≈ 8 %. Nada de esto está aplicado en el mod; son propuestas para medir con `CapitanMedidaGameTests`.

## Medidas (n = 128, grupos de 8 con élite, mismas semillas, mobs de REGLAS salvo que se diga)

Jugador de guion copiado de `CapitanMedidaGameTests` (quieto / se aleja 0,18 / rodea 0,15 por fases de 25–54 ticks, mira al más
cercano, pega 5 cada 16 ticks a ≤ 3,5, no muere, 600 ticks, espada y sin armadura). Grupo: élite zombi con espada de hierro, 3
zombis (uno con escudo), husk, araña, 2 esqueletos, llegando de 11–14 por un lado.

| Modo | Daño/min | Pareado vs sin capitán | Gana |
|---|---|---|---|
| sin capitán | 249,1 ± 4,7 | — | — |
| capitán sin órdenes (`iaCapitanReglas` apagado) | 249,2 | +0,0 | — |
| **capitán de reglas nuevo** | **255,0 ± 5,7** | +5,9 ± 5,1 | 67/128 |
| nuevo sin la carga | 234,1 | −15,1 ± 4,6 | 51/128 |
| nuevo sin la pinza | 275,6 | **+26,5 ± 4,6** | 93/128 |
| sin capitán, **+1 turno** (maxAttackers 3) | 275,2 | **+26,1 ± 5,2** | 87/128 |
| sin capitán, +2 turnos | 282,6 | +33,5 ± 5,3 | 88/128 |
| capitán viejo (§3.4) | 49,0 | −200 | 0/128 |

Con las redes v4 de los mobs (forja_mobs_v4, entrenadas con el capitán viejo) cualquier capitán baja el daño, también el que no da
órdenes (sin 267, libre 233, nuevo 236, viejo 125-130): las redes ven "tengo capitán" y actúan como bajo CERCAR. Se están
reentrenando con el capitán nuevo. Contra el jugador de reglas "experto" (el que muere), todo satura (muere en ≈ 13 s): mobs de
reglas sin 115,9, nuevo 112,8, viejo 53,0.

## Propuestas para el mod (no aplicadas; medir antes)

1. **Por qué el anillo libre del mod es tan lento (37,5) frente al del simulador.** En el simulador los turnos se piden justo al
   golpear y se sueltan tras el golpe (igual que `MeleeAttackGoalMixin`), y un grupo libre ya pega fuerte; la carga casi no ayuda.
   En el mod la carga (todos a ACERCARSE a la vez) duplica el daño y el turno extra no importa (80,3 sin él), así que el freno del
   mod está en lo que hacen los que NO tienen turno contra un jugador que se mueve, no en el número de turnos. Medir en
   `CapitanMedidaGameTests` (modo sin capitán) cuántos mobs están a ≤ 3,5 del jugador por tick y cuántos turnos hay ocupados; si
   los turnos están casi siempre libres y los mobs lejos, el fallo es de movimiento (el anillo que se mueve con la mirada del
   jugador, o ESPERAR/RODEAR a < 6 con `rasgo` 1-2), no de turnos.
2. **Un turno más con grupos grandes.** El simulador da +10 % (+26 dmg/min, 87/128 peleas) con `maxAttackers` + 1 en un grupo
   libre de 8. Propuesta: `Aggression.maxAttackers` + 1 cuando ≥ 6 mobs van a por el mismo jugador (opción de `CombatConfig`,
   para medir con y sin).
3. **La pinza de las reglas (regla 5).** En el simulador cuesta ≈ 8 % (sin pinza +26,5 ± 4,6, 93/128); en el mod "aporta poco"
   (74,8 sin ella frente a 77,7, dentro del ruido). Propuesta: medir "sin pinza" con 80 peleas más; si no gana, quitar la regla 5
   (o pedir que el jugador se aleje más rápido, > 0,15/tick, y ≥ 5 miembros).
4. **"Tambaleándose" (Posture.isStaggered del jugador).** El simulador no tiene postura del jugador; usa la lentitud + debilidad
   de la parada de un mob. Si el mod quiere que el simulador lo copie mejor, decir qué tambalea al jugador en el juego.

## Lo que el simulador ya copia (py_mobs_v4d)

- Las 6 reglas en su orden, la carga con cuenta 0 y el descanso de 40 + 100 ticks, la carga en curso que sigue, nunca CERCAR ni
  HOSTIGAR; arqueros de reglas con la orden libre tiran desde donde están; `capitan_reglas` = nuevas / libre / viejas / sin_carga /
  sin_pinza.
- El movimiento del jugador para la pinza: la velocidad o, si es mayor, la estimada por posiciones (como `MobSprint.motion`).
- Ballestas solo para saqueador y piglin (el simulador no tiene ninguno: nadie coge una ballesta); arcos para esqueleto y stray.
- La memoria a 0 al cambiar de familia también al volver a la de antes (Python: `memoria_por_familia`).
- El arco en el repuesto ya lo hacía (v4c).
