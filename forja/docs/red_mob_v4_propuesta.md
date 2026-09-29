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

## Segunda tanda (2026-09-29, noche): lo adoptado de PROPUESTAS_IA_SIMULADOR.md y las decisiones de Andy

Sigue sin hacer falta un contrato v4: nada de esto cambia qué entradas ve la red ni qué salidas tiene.

| Dónde | Qué | Propuesta |
|---|---|---|
| `Squad.assign` | **Huecos estables:** cada mob guarda su hueco (`slotAngle`, `slotN`, `slotOf`). Si el anillo tiene el mismo tamaño, nadie se mueve. Si cambia de tamaño, el patrón nuevo se ancla en el hueco que tenía el más cercano, cada uno toma el hueco libre más cercano al suyo y los nuevos entran como antes. | 2.1 |
| `SwellGoalMixin`, `TacticGoal.fuse`/`move` | **Creeper decidido:** con la mecha encendida sigue hacia el jugador a 0,5 de velocidad. Con la mecha a ≥ 15 ticks solo la apaga a más de 9 bloques (antes 7). La finta solo la hace si está solo. | 2.5 |
| `Squad.clearLineStep` | **Tiro libre:** el arquero prueba 2 bloques hacia el lado contrario al aliado, luego 2 hacia el otro lado y luego 2 hacia atrás, y va al primero desde el que la línea queda libre (`allyInLineFrom`). Si ninguno sirve, se aparta del aliado igualmente. | 2.4 |
| `MobFamily.network`/`executor`, `MobAi.familyOf` | **Redes para mobs vanilla** (Andy). Blaze con red propia (`red_mob_blaze.md`). Un arquero con ballesta (saqueador, piglin) la carga y la dispara en el ejecutor (`TacticGoal.crossbow`). | — |
| `MeleeAttackGoalMixin`, `MobRing` | **Reglas contra mobs** (Andy): el aviso y los turnos valen contra cualquier objetivo, y los que esperan turno se reparten en un anillo alrededor de un objetivo que no es jugador. Contra jugadores, las redes siguen igual. | — |

Mapeo de redes (Andy, 2026-09-29):

| Red | Mobs |
|---|---|
| zombi (`cuerpo`) | zombi, husk, ahogado, aldeano zombi, esqueleto wither, piglin zombificado, piglin con espada, piglin bruto, vindicador |
| arquero | esqueleto, stray, bogged (con arco), saqueador y piglin con ballesta |
| creeper | creeper |
| araña | araña, araña de cueva |
| `forja_tanque` | los tanques del mod, más devastador, hoglin y zoglin |
| `forja_enjambre` | los del mod, más lepisma y endermita |
| blaze | blaze (red nueva) |

**Más adelante, con red propia:** voladores (phantom, vex), enderman y breeze.

**Solo reglas:** bruja, invocador, ilusionista, creaking, guardián y guardián anciano, warden, slimes y cubos de
magma, ghast y shulker.

Pendiente de medir en el simulador: sectores con cupos (2.2), roles por tipo (2.6) y carrera barata para
recolocarse (2.3). Se dejan para después de copiar esto y medirlo.

## Acciones preparadas para la v4 (MobActions)

Andy aprobó nuevas acciones para los monstruos (2026-09-29). Las decidirá la red con el contrato v4, que aún no
está escrito. El código de las acciones ya está en `ai/MobActions.java`: métodos estáticos que devuelven si han
hecho algo. Nada los llama todavía, ni las reglas ni la red. Cuando llegue la v4, `RuleBrain` y la red llamarán a
los mismos métodos.

Todas están apagadas con `mobActionsV4 = false` en `config/forja.json`. Romper luces necesita además
`mobsBreakLights = true`: los monstruos no rompen ni ponen bloques, y las luces son la única excepción prevista.

| Acción | Qué hace | Bandera | Prueba |
|---|---|---|---|
| `pickUpBetterWeapon` | Busca armas en el suelo a `mobPickupRange` bloques (6) que pueda ver. Si una pega más que la suya (daño de sus atributos), va a por ella; a 1,5 bloques la coge y suelta la suya. El arma cogida cae siempre al morir, así que el jugador la recupera | `mobActionsV4` | `zombiePicksUpBetterSword` |
| `drinkPotion` | Bebe la poción que lleve en una mano: sus efectos pasan al mob y se gasta | `mobActionsV4` | `mobDrinksHealingPotion` |
| `throwSplash` | Lanza la poción arrojadiza de la mano izquierda al objetivo, como la bruja | `mobActionsV4` | `mobThrowsSplashPotion` |
| `eat` | Con menos de media vida, come lo de la mano izquierda y se cura lo que alimenta (pan 5) | `mobActionsV4` | `mobEatsBreadWhenHurt` |
| `throwPearl` | Lanza una perla de la mano izquierda hacia un punto. La perla vanilla ya teletransporta a cualquier dueño, no solo a jugadores. Sin perla no hace nada | `mobActionsV4` | `pearlMovesTheMob` |
| `breakLight` / `nearestLight` | Rompe una antorcha o un farol a 2,5 bloques de sus ojos. `nearestLight` busca la luz más cercana al jugador | `mobActionsV4` y `mobsBreakLights` (apagada) | `breakLightOnlyWithTheFlag` |
| `raiseShieldSmart` | Sube el escudo si la amenaza le apunta con un arco o una ballesta tensos, o prepara un golpe cerca. Si no, lo baja. Usa `MobDefense.raise`, así que la guardia rota sigue abajo | `mobActionsV4` | `shieldUpAgainstADrawnBow` |

Luces: solo antorchas (normal, de pared, de almas y de almas de pared), y solo con la regla `mobGriefing`
activada (Andy). Un farol no se rompe nunca.

Pendiente para el contrato v4: las salidas de la red que elijan estas acciones y las entradas que las hagan
posibles (qué lleva en la mano izquierda, armas en el suelo, luces cerca del jugador).

## Estado en el mod (2026-09-29)

**Decisiones de Andy (2026-09-29), que cambian el paso M0 del diseño:**

1. **Los zombis siguen rompiendo puertas.** `BreakDoorGoal` vanilla se queda como está. También se quedan la
   telaraña de la araña, el fuego de los mobs del mod, las explosiones del creeper y el enderman que coge bloques.
   Lo único que no hacen los mobs es construir (pilares, torres) ni cavar: eso ya se quitó con `MovementGoals.Builder`.
   La frase del asedio ("¡Asedio! Vienen a por tu forja") no promete nada de eso y no se toca; solo se corrigió el
   comentario de `WorldFights.siege`, que aún decía que los zombis cavan y trepan.
2. **Luces: solo antorchas** (`torch`, `wall_torch`, `soul_torch`, `soul_wall_torch`) y **solo con la regla
   `mobGriefing` activada**. `MobActions.isLight`/`breakLight` ya lo cumplen; decidir cuándo romperlas es de la v4
   (M3).
3. **Una base cerrada del todo es segura**, como en vanilla: ningún cambio de la IA abre camino a través de bloques.

**Hecho:**

- **M0:** completo con las decisiones de arriba. Sin Builder, sin cambios de bloques salvo lo que Andy deja, y la
  prueba `zombiesNeverBuildNorDig`.
- **Carga de la v4 (parte de M1 que no depende de las respuestas):**
  - opción `iaContrato` = `v3` | `v4` | `auto` (por defecto `auto`) en `config/forja.json`;
  - las v4 se leen de `config/forja/redes_v4/red_<familia>.json` (y `red_capitan.json`, reservado); las v1, v2, v3,
    v3b y v3.1 siguen en `config/forja/redes/`;
  - `MobAi.check` mira el campo `"formato"` antes que los nombres: una v4 en la carpeta de las v3 se rechaza;
  - como aún no hay `ObsV4`, una v4 se detecta, se anota una vez en el registro ("red v4 encontrada para X, aún no
    soportada: se usa v3") y no se usa. La familia sigue con su v3 o con las reglas. `NetBrain.format` guarda el
    formato de cada red.
- **Percepción honesta (parte de M4):** opción `iaPercepcionHonesta` (por defecto activada). Un mob que lleva 20 ticks
  sin percibir a su jugador va a la última posición en la que lo percibió (`MobMind.lastSeen`), no a la real, y a menos
  de 2 bloques de ella se para y espera. Vale para el ejecutor (`TacticGoal`) y para el ataque cuerpo a cuerpo vanilla
  (`MeleeAttackGoal`). Excepciones: a menos de 1,5 bloques lo "siente" y ataca como siempre; los jefes no cambian; y un
  mob que nunca vio a su jugador (asedio, llamada de ayuda) sigue como antes, porque no tiene última posición.
- **Pruebas:** `PercepcionGameTests`:
  - `lostPlayerIsSoughtWhereLastSeen`: jugador tras un muro, el zombi espera donde lo vio y nunca llega a 2 bloques de
    la posición real;
  - `v4NetworkIsDetectedButNotUsed`: una v4 falsa en `redes_v4` se detecta y no se usa.

**Pendiente de las respuestas de Andy (§7 del diseño):** `ObsV4` y las cabezas nuevas de `NetBrain` (M1), furia y
capitán (M3 a M5), la lista exacta de luces y la etiqueta `forja:luces_rompibles` (M3), y el resto de M2 a M7.

**Pendiente sin depender de él:** la estimación en la observación (hoy una red sigue viendo la posición real aunque no
lo perciba), los sonidos, BUSCAR y la caza hasta 48 bloques y 600 ticks (el resto de M4).
