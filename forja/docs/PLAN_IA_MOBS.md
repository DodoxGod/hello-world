# Plan: combate completo e IA de los mobs de Forja

Plan acordado con el usuario (2026-09-25): hacer las 99 ideas de la lista (todas menos la de "decidir cada 5 ticks")
más las mecánicas del jugador que faltaban. Después, la sesión del bot ("Cómo funcionan los LLM",
`E:\IA\agente\minecraft\motor_rust`) mete todo en su simulador ("modo Forja") y entrena las redes de los mobs,
**incluidos los mobs propios de Forja**, para enchufarlas aquí.

Las reglas exactas y los números están en [COMBATE_ESPECIFICACION.md](COMBATE_ESPECIFICACION.md), que se actualiza
en cada fase.

**Estado (2026-09-26): todas las fases hechas (0, 0b y 1–10).** Se prueban con 69 pruebas de servidor y la batería
de cliente, y están documentadas en COMBATE_ESPECIFICACION.md. El contrato final está en red_mob_v2_contrato.json.
Cambio respecto al borrador del §4: `tactica` tiene 9 valores (se añadió PARAPETARSE en la fase 7).

## 1. Principios

1. **Separar la decisión de la ejecución.** Cada mob tiene un *cerebro* que solo decide ("qué hacer ahora") y un
   *ejecutor* que lo hace (navegación, animaciones, avisos, daño). Las reglas y la red son dos cerebros
   intercambiables; el ejecutor es el mismo para los dos.
2. **Decidir a menudo, con compromiso.** El cerebro decide cada *N* ticks, donde *N* sale del archivo de la red
   (`ticks_por_decision`, el mismo con el que se entrenó; M1 usa 2) o vale 1 con las reglas. Nunca 5. Un golpe
   con aviso, una vez empezado, se termina (es lo que lo hace justo y esquivable), salvo por la acción explícita
   de fintar, que solo se puede tomar en la primera mitad del aviso.
3. **Justo antes que difícil.** Todo ataque peligroso tiene un aviso visible y audible. Las redes se premian por
   acertar golpes avisados; un golpe sin aviso no existe en el repertorio.
4. **Siempre hay respaldo.** Si no hay red para un tipo de mob, o falla, decide el cerebro de reglas.
5. **Una red solo para mobs que pelean.** Los que no tienen objetivo no piensan, y ese es el ahorro de CPU que
   vale la pena, no bajar el ritmo.

## 2. Arquitectura (paquete `dev.forja.ai`)

| Pieza | Qué hace |
|---|---|
| `Tactic` | Lo que el cerebro puede elegir: ACERCARSE, RODEAR, FLANQUEAR, GOLPEAR, FINTAR, EMBESTIR, ESPECIAL(k), CUBRIRSE, ESQUIVAR, RETIRARSE, REAGRUPARSE, CUBRIR_ALIADO, PEDIR_AYUDA, RETAR, ESPERAR_TURNO, HUIR, PILAR, EMBOSCAR... |
| `MobMind` | El estado de un mob: su táctica actual y desde cuándo, su personalidad, su memoria de jugadores, su veteranía y su rencor. |
| `Observation` | El vector de números del mob (el de M1 más el bloque Forja, ver §4). Es la misma función para la red y para las reglas. |
| `Brain` | `decide(MobMind, Observation) -> Decision`. Implementaciones: `RuleBrain` (por tipo de mob) y `NetBrain` (carga `red_mob_v2`). |
| `Squad` | Los mobs que pelean contra el mismo jugador: huecos en el anillo, roles (atacante, flanco, distractor, cobertura, reserva), relevos, duelos y retiradas en grupo. |
| `Moveset` | Lo que sabe hacer cada tipo de mob: golpe básico y ataques especiales con aviso (alcance, aviso, daño, área, enfriamiento). Lo implementan los mobs de Forja y adaptadores para los vanilla. |
| `TacticGoal` | El ejecutor: un `Goal` de movimiento y mirada que hace la táctica actual; los ataques pasan por `Moveset`. |
| `PlayerHabits` | Lo que los mobs saben de cada jugador: con qué frecuencia para, hacia dónde esquiva, qué arma y peso lleva, su estamina y si está cargando. Se comparte dentro de un escuadrón. |
| `AiDebug` | `/forja ia ver` (etiqueta con la táctica sobre cada mob), `/forja ia modo reglas\|red`, estadísticas y grabación de combates. |

## 3. Fases

Cada fase se compila y se prueba (pruebas de servidor y capturas de cliente) antes de pasar a la siguiente, y
actualiza COMBATE_ESPECIFICACION.md.

| Fase | Contenido | Ideas |
|---|---|---|
| 0 | Jugador: ataque cargado, remate, combos de 3, parada con el arma, contraataque tras una esquiva perfecta y arreglo de las resistencias de las armaduras normales | — |
| 0b | Dificultad (ver §6) | D1–D15 |
| 1 | Estructura de la IA: `Tactic`, `MobMind`, `Observation`, `Brain`, `RuleBrain`, `NetBrain`, `TacticGoal`, `Moveset`, depuración y configuración, estadísticas y grabación de combates | 71–90 (sin la 86) |
| 2 | Leer al jugador: `PlayerHabits` y sus observaciones; las reglas las usan | 1–10 |
| 3 | Tácticas de grupo: anillo, pinza, relevo, cobertura, muro de escudos, cebo, retirada en grupo, pedir ayuda, proteger al aturdido y repartir objetivos | 11–20 |
| 4 | Ataques con aviso de los mobs normales: araña, esqueleto, enderman, vindicador, ahogado, bruja, piglin bruto y creeper | 21–30 |
| 5 | Defensa y postura de los mobs: escudos, parada y esquiva de mob, recuperar postura, guardia rota, contraataque y bloquear flechas | 31–40 |
| 6 | Mobs de Forja: el `Moveset` de cada uno y sus posturas especiales | 41–50 |
| 7 | Movimiento: terreno alto, evitar lava y caídas, empujar al vacío, saltar y romper, pilar, zigzag, cubrirse y seguir el rastro | 51–60 |
| 8 | Memoria y personalidad: rasgos, rencor, adaptación por noches, veteranos, miedo, líderes, territorio y curiosidad | 61–70 |
| 9 | Eventos y mundo: asedios, ladrones, hordas de evento, armas robadas, élites que vuelven, duelos, patrullas, noche y clima, y el Herrero que recuerda | 91–100 |
| 10 | Cierre: especificación final, `red_mob_v2` y aviso a la sesión del bot | — |

## 4. Contrato con las redes (`red_mob_v2`, borrador)

Se extiende `red_mob_v1` de M1 sin romperlo. Una red v1 funciona tal cual con las 102 primeras entradas y las 3
cabezas de acción, pero sin las funciones de Forja.

**Observación** = las 102 de M1 (mismo orden y marco: "delante" = hacia el jugador) + un **bloque Forja**. Los índices
finales se fijan al cerrar cada fase; la lista, en borrador:

- **Jugador:**
  - estamina /100
  - cargando un golpe (0/1) y cuánto lleva cargado (0..1)
  - ticks con el escudo o el arma en guardia /10 (para saber si está en su ventana de parada)
  - enfriamiento de su esquiva /15 e invulnerable ahora por esquivar (0/1)
  - tiene un contraataque preparado (0/1)
  - paso de su combo (0..2)/2
  - peso de su armadura (0..1) y estilo de su arma one-hot (tajo, golpe desde arriba, estocada, otro)
- **Hábitos del jugador** (`PlayerHabits`):
  - frecuencia de parada, de esquiva y de bloqueo
  - hacia qué lado esquiva (−1..1)
  - distancia media a la que pelea /8
  - frecuencia con que carga el golpe
  - nivel estimado (0..1)
- **El propio mob:**
  - postura 0..1 y aturdimiento que le queda /40
  - escudo arriba y guardia rota
  - tiene turno de ataque (0/1)
  - aviso en curso (0/1) y cuánto ha avanzado (0..1)
  - por cada especial de su moveset (hasta 4): disponible (0/1) y enfriamiento restante (0..1)
  - personalidad one-hot (agresivo, prudente, cobarde, astuto), veteranía (0..1) y rencor contra este jugador (0/1)
- **Escuadrón:**
  - rol one-hot (atacante, flanco, distractor, cobertura, reserva) y dirección de su hueco en el anillo (seno y coseno en el marco del mob)
  - nº de mobs con turno /2
  - hay un aliado aturdido cerca (0/1)
  - hay un duelo en curso y su papel en él (retador o público)
  - miedo del grupo (0..1)
- **Mundo:** noche, lluvia, luz /15, dentro de su territorio.

**Acciones** = las 3 cabezas de M1 (`mover` 0..8, `saltar`, `usar`) + cabezas nuevas:

- `tactica` (índice de `Tactic`): orienta al ejecutor. Mover a un hueco del anillo, retirarse y pilar son tácticas; el `mover` de M1 queda como ajuste fino.
- `especial` 0..4: 0 = ninguno, k = empezar el especial k del moveset si está disponible.
- `defensa` 0..2: nada, cubrirse con el escudo o esquivar a un lado.
- `fintar` 0/1: solo se puede usar en la primera mitad de un aviso.

La máscara anula lo que no se puede hacer (un especial en enfriamiento, fintar fuera de la ventana, cubrirse sin
escudo...). El archivo lleva `ticks_por_decision`, los nombres de las observaciones y la versión del contrato.

### Ajustes acordados con la sesión del bot (2026-09-25)

1. **Sortear, no el máximo.** `NetBrain` sortea cada cabeza con sus probabilidades, como al entrenar, con un
   generador de números aleatorios propio de cada mob y una `temperatura` en la configuración (1,0 por defecto).
2. **Grabación para comparar las observaciones.** `/forja ia grabar` escribe JSONL con una línea por decisión y mob:
   `{t, uuid, tipo, obs:[...], mascara:[...], accion:{mover, saltar, usar, tactica, especial, defensa, fintar},
   probs?, vida_mob, vida_jugador, evento}`.
3. **Normalización dentro del archivo.** La red lleva `nombres_obs` y, si hace falta, la media y la desviación de
   cada entrada. El mod comprueba que `nombres_obs` coincide con su orden; si no coincide, usa las reglas y lo avisa
   en el registro.
4. **El ejecutor, idéntico en el mod y en el simulador.** Pocas tácticas y bien definidas. La especificación da, para
   cada una, cómo se calcula el punto al que va, la velocidad, los radios y cuándo termina.
5. **La personalidad cambia los premios.** Agresivo: más peso al daño hecho. Prudente: más castigo al daño
   recibido. Cobarde: huye antes. Astuto: premio a fintas y flancos. En el entrenamiento se sortea. La veteranía y
   el rencor multiplican la agresividad. La especificación da los pesos.
6. **Roles por reglas.** `Squad` asigna los roles y le llegan a la red como entrada; no los decide la red.
7. **Una red por familia.** Cuerpo a cuerpo, a distancia, explosivo o de área, tanque con postura especial y
   enjambre, con el tipo en one-hot y la máscara de sus especiales. El Herrero Caído tiene su propia red, por fases.
8. **Todo lo que ve la red existe en el simulador.** La especificación da las fórmulas de `PlayerHabits` (media
   móvil, ventana y valor inicial).

## 5. Mobs a entrenar

- **Normales:** zombi, husk, ahogado, esqueleto, stray, creeper y araña (como en M1), más enderman, vindicador, bruja, piglin bruto y zombi con escudo cuando existan sus movesets (fase 4).
- **De Forja:** Yunque Andante, Autómata, Escoria Viviente, Enjambre de Herrumbre, Pavesa, Ascua Mayor, Coraza Hueca, Percutor, Tenaza, Cargador de Carbón, Templador, Núcleo Estelar, Molde Roto, Guardián de Cuño y Herrero Caído (jefe, por fases).

Cada uno tendrá en la especificación su moveset con números (alcance, aviso, daño, área y enfriamiento) y su
postura, para que el simulador pueda reproducirlo.

## 6. Dificultad (fase 0b)

Aprobado por el usuario (2026-09-26), con sus ajustes. Idea central: **nada muere de un golpe salvo que te lo hayas
ganado** (romper la postura y rematar). La IA no sirve de nada si el mob no vive lo bastante para usarla.

| # | Qué | Cómo |
|---|---|---|
| D1 | Vida y armadura que escalan con tu equipo | Una "puntuación de equipo" del jugador (daño del arma, mejoras, maestría, nivel de herrero). Los mobs que aparecen cerca la usan para subir vida y armadura, por tramos y con un tope |
| D2 | Tope de daño por golpe | Ningún golpe normal quita más de un % de la vida máxima (zombi 45 %, élite 20 %, jefe 8 %). El remate y los golpes a un mob aturdido lo superan |
| D3 | Guardia antes que vida | Élites, tanques y jefes: mientras no se rompe su postura, el daño a la vida se reduce mucho |
| D4 | Rendimiento decreciente de las mejoras | Las mejoras de daño se suman con una curva, no en línea recta |
| D5 | **Presión: penetración que se acumula** | Cada golpe que recibes en poco tiempo suma penetración de armadura a los siguientes golpes que te dan. Se suma a la del arma hasta un **70 % en total** y se vacía si pasas un rato sin que te golpeen. Rodearte o encadenarte golpes atraviesa cualquier armadura |
| D6 | Resistencias por mob | Cada mob resiste tipos de daño distintos (el Yunque, el corte; la Escoria, los golpes; el Enjambre, la perforación) |
| D7 | Aturdimiento con resistencia | Cada aturdimiento del mismo mob en poco tiempo dura menos **y además sube su postura máxima**: cuesta más aturdirlo otra vez. Se recupera con el tiempo |
| D8 | Enfriamiento del remate | Enfriamiento por objetivo; los jefes solo se pueden rematar en fases concretas |
| D9 | Niveles de amenaza | Normal, veterano, élite y campeón, con marca visible; suben vida, postura, especiales y la calidad de la IA (red y temperatura) |
| D10 | Dificultad por distancia y profundidad | Más nivel lejos del spawn, en el Nether y bajo tierra |
| D11 | Dificultad adaptativa | Sube un poco si ganas sin recibir daño y baja si mueres mucho; con límites y visible en la configuración |
| D12 | Noches progresivas **con techo** | Cada noche sobrevivida hace la horda algo más lista y un poco más grande, pero el tamaño crece despacio y tiene un **tope fijo** (configurable). En la noche 1000 no aparecen 100 zombis: a partir del tope sube la calidad (nivel de amenaza, IA, equipo), no la cantidad |
| D13 | Selector de dificultad | Aprendiz, Herrero, Maestro y Leyenda, en la configuración y con `/forja dificultad`; cambia los multiplicadores de todo lo anterior y la temperatura de las redes |
| D14 | Recompensa por riesgo | Más botín, maestría y materiales raros en las dificultades altas |
| D15 | Modo "sin encantamientos mezclados" | Opción que desactiva los encantamientos vanilla que se combinan con las mejoras de Forja |

Todos los números (tramos, topes, curvas, presión y resistencia al aturdimiento) irán a COMBATE_ESPECIFICACION.md,
porque el simulador tiene que reproducirlos para que las redes aprendan con los mismos márgenes.

**Acordado con la sesión del bot para la fase 0b:**
- Observaciones que se añaden al bloque Forja: nivel de amenaza (one-hot de 4), presión acumulada sobre el jugador (0..0,7), resistencia propia al aturdimiento, dificultad elegida (one-hot Aprendiz/Herrero/Maestro/Leyenda) y puntuación de equipo del jugador (0..1).
- La "calidad de la IA" por nivel es la **temperatura de muestreo sobre una misma red** (más baja en los niveles altos), no redes distintas. El nivel también entra como observación, y en el entrenamiento los niveles altos dan más peso a los premios tácticos.
