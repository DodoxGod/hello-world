# Red de mobs: cambios de ejecución de 2026-09-29 (propuesta para una v4)

`red_mob_v3_contrato.json` **no cambia**: las 280 entradas y las 34 salidas siguen iguales en número, orden y
nombre, y las redes entrenadas se cargan como antes. Lo que cambia es cómo el juego ejecuta algunas decisiones y
cómo reparte los huecos del anillo. El simulador debería imitar estos cambios en el próximo entrenamiento; cuando lo
haga, esto pasa a ser la v4.

## Cambios que el simulador tiene que copiar

| Dónde | Antes | Ahora | Por qué |
|---|---|---|---|
| `Squad.assign` | Todo el grupo recibía un hueco del anillo, también los arqueros y los creepers | Solo los que pelean cuerpo a cuerpo. Los arqueros y los creepers reciben un "hueco" donde ya están, así que `hueco_delante` y `hueco_derecha` valen ~0 para ellos | Ocupaban un hueco en el que no se quedaban y dejaban el cerco con agujeros |
| `Squad.assign` | El anillo empezaba en el ángulo del mob más cercano, recalculado cada 10 ticks | El ángulo de inicio se guarda por jugador mientras dure la pelea (se olvida tras 30 ticks sin usarse) | Los huecos giraban cada vez que otro mob se acercaba y el grupo los perseguía sin llegar |
| `RuleBrain` (solo reglas) | Sin turno, rodeaban a menos de 6 bloques; más lejos, `APPROACH` (en fila) | En grupo, rodean ya desde 16 bloques y nunca dentro de un anillo más ancho que ese radio | Llegaban en columna y los de atrás nunca daban la vuelta |
| `MobSprint.rules` (solo reglas) | Corría en `RODEAR`/`ESPERAR` a más de 50° de su hueco | Además corre en `RODEAR`/`FLANQUEAR` si está a más de 5 bloques de su hueco (`SLOT_FAR`) | Casi no esprintaban para colocarse |
| `TacticGoal.fuse` | Solo encendía la mecha si la red pedía `usar` a menos de 3 bloques | A menos de 2 bloques (`FUSE_ANYWAY`) y viendo al jugador la enciende siempre | Creeper pegado al jugador que no explotaba |
| `MeleeAttackGoalMixin` | El creeper recibía el aviso de golpe, cogía un turno de ataque y saltaba atrás | El creeper queda fuera: su ataque es la mecha | Dudaba, frenaba a 2 bloques y casi nunca explotaba |
| `CombatConfig.creeperFeintChance` | 0.35 | 0.15 | Menos fintas |
| Arqueros (`TacticGoal.bow` y `RangedBowAttackGoalMixin`) | Con un aliado en la línea de tiro aguantaban la flecha quietos | Aguantan y dan un paso de 2 bloques hacia el lado contrario al aliado (`Squad.stepToClearLine`) | No buscaban una línea de tiro libre |

## Lo que no se ha tocado

- Los valores y el orden de `SquadRole`.
- El orden de `Tactic` y de `MobFamily`.
- Las constantes existentes de `MobSprint`. Solo se añade `SLOT_FAR`.

## Para la v4

Si hace falta una entrada nueva, por ejemplo "línea de tiro libre a un paso" para el arquero, se añadiría al final
de la lista, como la v3 hizo con la v2. Este cambio no la necesita.
