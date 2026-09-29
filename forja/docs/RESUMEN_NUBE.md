# Resumen de la sesión en la nube — 2026-09-29

Para Andy. Todo está en la rama `claude/hola-rv9w0u` (PR #1). El detalle de la primera tanda está en
`docs/RESUMEN_2026-09-29.md`; aquí va todo junto, con la segunda tanda (la que mandaste por la sesión "Mod Forja")
y lo que vino después.

**Estado del CI:** ver "Estado de las pruebas" al final.

## Cómo lo he probado sin tu PC

- **Compilar y probar:** en esta máquina no se puede compilar (Gradle no tiene red), así que lo hace GitHub Actions
  en cada push. `runGameTest` va dentro del build.
- **Capturas del juego:** con `[capturas:SECCIÓN]` en el mensaje de un commit, `.github/workflows/capturas.yml`
  arranca el cliente de verdad con pantalla virtual, ejecuta la sección `FORJA_SOLO=SECCIÓN` de las pruebas de
  cliente y devuelve las capturas en el registro. Yo las decodifico, las miro y te hago las hojas.
- **Mini jar del cliente:** el build exporta un jar pequeño del cliente, con los estados de bloque y los colores,
  para que el generador del castillo funcione en la nube.
- **La sesión "Mod Forja":** recibí tus mensajes por ahí, pero desde esta sesión no puedo contestar a otra sesión.
  Todas las respuestas están aquí y en el chat.

## 1. El castillo del Herrero (Bastión del gremio)

Tres pasadas al generador (`tools/castillo*.py`).

**Primera pasada:**

| Tu pedido | Qué hice |
|---|---|
| Sitio: nada de montaña encima ni entrada en el mar | Antes de colocarlo se mira el terreno bajo todo el plano (`BastionGround` + `JigsawStructureMixin`). Ver la sección 2, que lo rehízo para que `/locate` no congele el juego. El terreno se despeja hasta el tejado. |
| Bloques con agua dentro | `liquid_settings: ignore_waterlogging` |
| Una sola fragua apagada | Solo queda la del sótano; la del monumento es ahora un alto horno apagado. |
| Bloques flotantes y de "medir" | Fuera los marcadores de encaje que salían en el aire y todo lo que colgaba de nada. |
| Bloques caros y demasiado botín | Núcleos, oro, diamante, magnetita, el yunque del Herrero… se cambian por bloques del mismo aspecto. Quedan 43 cofres en vez de 164, con tope por tabla y nunca juntos. |
| Entradas de 1 bloque | Puertas de 3 de ancho que llegan hasta el techo. |
| Configuraciones imposibles | Se acabó la escalera boca abajo con farol y los yunques en las tumbas. |
| Solape con otras estructuras | Exclusión de 10 chunks con las aldeas, y de 12 entre el castillo pequeño y el grande. |
| Pocos mobs | Guarnición por plantas según el tamaño de la sala: 89 monstruos (antes 32). |
| Soportes de armadura en alfombras o jarrones | Solo quedan contra una pared y sobre suelo firme. |
| Construir y romper en la pelea del jefe | Mientras el Herrero Caído vive, nadie rompe ni pone bloques ni cubos a menos de 32 bloques (salvo en creativo). |

**Segunda pasada:**

- **Uniones:** vallas, paneles, barrotes, muros y escaleras se unen con las reglas del juego. Cambiaron 152
  uniones.
- **Solapes:**
  - Un ala se comía un torreón del torreón principal (437 bloques). La fundición hacía lo mismo con otro.
  - La cantera y el cementerio abrían huecos en dos torres.
  - La cúpula de la Forja Profunda se comía las cuatro pilas de templado y el fondo de la cisterna. Eran tus
    "columnas de agua o hielo con cadenas".
- **Cuartos sin techo:** solo quedan agujeros en la casa quemada, que es una ruina. Además, 13 velas de la cripta
  estaban sobre medias losas.
- **Escaleras sin salida:** fuera dos tramos vacíos. Se recolocaron las de la sala de temple, la tumba y la
  nevera, y la puerta del observatorio ya atraviesa todo el muro.
- **Salas a las que no se podía llegar:** ahora se llega a las plantas 2 a 5 del torreón, la capilla y la
  sacristía, el gabinete de orbes, la despensa, el almacén de lingotes, tres torres y varios adarves.
- **Decoración:**
  - 0 farolas de pie. En su lugar, cadenas del techo, al menos a 7 bloques una de otra para que el gran salón no
    sea un bosque, y apliques en la pared.
  - Las "banderitas" del patio son una barandilla con estandartes.
  - Las celdas de rastrillos tienen muro hasta el techo.
  - Almacenes y salas de guardia con tres distribuciones cada uno.
  - El almacén alto del torreón pasa de 28 pilas de barriles a 10.
- **Otros fallos que encontré:** lava que se escapaba por puertas, grava sobre aire y una flor sobre piedra.

**Tercera pasada (accesos, lo que pediste en la segunda tanda):**

- **Barbacanas (las dos):** cada torre tiene una puerta de 2 × 3 desde el paso, y otra a la sala de los tornos en
  su primer piso. Una escala sube por el fondo de la torre, cruzando todos los pisos hasta el último, que ya tenía
  puerta a la azotea.
- **Pasarela de la fundición:** una escalera de piedra de 2 de ancho sube desde el suelo, pegada a su extremo sur.
- **Comprobado:** con el recorrido desde la puerta se llega a las salas de los tornos, a los tres pisos de las
  cuatro torres, a las dos azoteas y a la pasarela. Antes no se llegaba a ninguna.

**Imágenes:**

- `docs/castillo/antes_despues_2026-09-29/`: 62 vistas del juego, antes y después de la primera pasada.
- `docs/castillo/segunda_pasada_2026-09-29/juego/`: las mismas 62 vistas, primera pasada frente a segunda.
- `docs/castillo/segunda_pasada_2026-09-29/*.png`: 18 dibujos de corte y planta de las salas que cambiaron.
- `docs/castillo/tercera_pasada_2026-09-29/`: 10 cortes y plantas de antes y después de los accesos. En la prueba
  del cliente hay dos vistas nuevas, `int_tornos` e `int_fundicion_pasarela`.

**Sin arreglar:** algunas uniones con bloques del mod son aproximadas.

## 2. `/locate` del castillo congelaba el juego (urgente)

- **Por qué:** `/locate` pregunta por cientos o miles de sitios en el hilo del servidor. La primera versión del
  control del terreno pedía 81 columnas de ruido por sitio antes de mirar siquiera el bioma.
- **Primer arreglo:**
  - primero el bioma (una muestra);
  - luego el centro y las cuatro esquinas;
  - luego una rejilla de 5 × 5, saliendo en el primer fallo;
  - cada sitio se calcula una vez y se recuerda.
- **Lo que midió el juego de verdad:** aún tardaba **20 s** y el castillo más cercano estaba a 45.000 bloques.
  Valía un sitio de cada dos mil y cada muestra costaba dos columnas.
- **Segundo arreglo:**
  - antes que ninguna columna, se miran los biomas del centro y de las cuatro esquinas: ni mar, ni río, ni
    montaña;
  - una columna solo pregunta por el fondo bajo el agua si su superficie está a nivel del mar;
  - los límites son algo más anchos: desnivel 24 (antes 18), el centro a 6 de la mediana (antes 4) y 3 muestras
    con agua (antes 2).
- **Pruebas:**
  - `SitioGameTests`: mar, ladera, llano, charco frente a lago, y 500 sitios en menos de 5 s;
  - en la prueba del cliente (`mundo`), se busca el castillo como lo hace el mapa del Forjador y luego con
    `/locate`, las dos con un tope de 15 s, y se escribe en el registro cuántos sitios se miraron y por qué se
    descartaron.
- **Resultado:** en un mundo nuevo, buscar el castillo pasó de **20 s a 4,4 s**, y un `/locate` después, **0,3 s**.
  - El control del sitio costó 1,4 s en total: 313 candidatos, 277 descartados por bioma, 34 por el terreno y 2
    válidos.
  - El castillo salió a unos 20.000 bloques, en una taiga nevada y en tierra firme, con un río al oeste y el mar
    justo fuera de la muralla este.
  - Vistas: `docs/capturas_2026-09-29/mundo_locate.jpg`.

## 3. Grupos de mobs

- La mitad de los compañeros pueden ser de otro tipo: zombi, esqueleto, araña o creeper, con sus variantes del
  desierto y la nieve.
- Como mucho, un creeper por grupo.
- Los compañeros nunca son élites, veteranos ni campeones.
- Solo el primero de un grupo de aparición trae compañeros, así que ya no salen 12 zombis con 2 élites.
- Tamaños: de 2 a 3 los normales y de 3 a 6 con un veterano.
- Pruebas: `packsMixKindsAndHaveOneLeader` y `veteransComeInPacks`.

## 4. IA de los mobs (el contrato v3 no cambia)

**Primera tanda:**

- **Creeper:** ya no pasa por el aviso de golpe cuerpo a cuerpo, que lo frenaba, le quitaba un turno a los demás
  y lo hacía saltar atrás. Enciende la mecha pegado a ti aunque su red no lo pida.
- **Rodear de verdad:**
  - solo los de cuerpo a cuerpo ocupan hueco en el anillo;
  - el inicio del anillo no gira durante la pelea;
  - en grupo se abren desde 16 bloques en vez de llegar en fila;
  - con 13 salen grupos de 3 o 4 por lado: detrás, cada lado y delante.
- **Correr a su sitio:** corren si su hueco está a más de 5 bloques.
- **Élites y capitanes:** conservan salto, segundo aliento y cuerno al recargar el chunk (prueba
  `anEliteKeepsItsMovesAfterAReload`).

**Lo adoptado de `PROPUESTAS_IA_SIMULADOR.md`:**

- **Huecos estables (2.1):** cada mob guarda su hueco. Si llega uno nuevo, los que ya estaban no se barajan.
- **Esqueletos que buscan línea de tiro (2.4):** si un aliado les tapa el tiro, prueban 2 bloques hacia un lado,
  luego hacia el otro y luego hacia atrás, y van al primero desde el que ven limpio.
- **Creeper decidido (2.5):**
  - con la mecha encendida sigue hacia ti a media velocidad;
  - con la mecha a medias solo la apaga a más de 9 bloques (antes 7);
  - finta menos (15 %) y solo si está solo.
- **Blaze con familia propia (7.1):** ver abajo.
- **Alcance por arma (8):** ver la sección 5.
- **Pendientes de medir en el simulador:** sectores con cupos (2.2), roles por tipo (2.6) y carrera barata (2.3).

**Tus decisiones de la segunda tanda:**

- **Redes de los mobs vanilla:**

  | Red | Mobs |
  |---|---|
  | zombi (`cuerpo`) | zombi, husk, ahogado, aldeano zombi, esqueleto wither, piglin zombificado, piglin con espada, piglin bruto, vindicador |
  | arquero | esqueleto, stray, bogged (con arco), saqueador y piglin con ballesta (ahora cargan y disparan la ballesta) |
  | creeper | creeper |
  | araña | araña, araña de cueva |
  | `forja_tanque` | los tanques del mod, más devastador, hoglin y zoglin |
  | `forja_enjambre` | los del mod, más lepisma y endermita |
  | blaze | blaze: red propia (`docs/red_mob_blaze.md`). Mientras no haya una entrenada, usa las reglas: ráfagas de 3 bolas |

  Solo reglas: bruja, invocador, ilusionista, creaking, guardianes, warden, slimes, cubos de magma, ghast y
  shulker.
- **Reglas del mod contra otros monstruos:** el aviso del golpe y los turnos valen contra cualquier objetivo. Los
  que esperan turno se reparten en un anillo alrededor del objetivo (`MobRing`). Contra jugadores, las redes siguen
  igual.

**Documentos para el simulador:**

- `docs/red_mob_v4_propuesta.md`: todos los cambios que tiene que copiar el simulador. No hace falta un contrato
  v4.
- `docs/red_mob_blaze.md`: la ficha del blaze.

**Pruebas y capturas:**

- **Pruebas:** en `FormacionGameTests` hay 13 que rodean, un anillo que no gira, el mapeo de redes, reglas contra
  un aldeano, un recién llegado que no baraja a los demás, la mecha a 8 bloques y otras.
- **Capturas:** `FORJA_SOLO=cerco`, 13 zombis y 2 esqueletos vistos desde arriba, en
  `docs/capturas_2026-09-29/cerco.jpg`.

## 5. Alcance de los monstruos por arma, y contrato v3.1

- **En el juego:** un monstruo con espada pega desde más lejos que uno con los puños. Sobre el alcance de su cuerpo
  suma:
  - puños +0;
  - daga +0,2;
  - espada +0,6;
  - hacha, martillo, maza o pico +0,5;
  - espadón +0,9.

  Vale también para las armas vanilla. La guadaña, el tridente, la lanza y el mangual se quedan con el suyo. Cuenta
  para empezar el golpe, para acertarlo y para colocarse. Los números están en `config/forja.json` (`mobReach*`).
- **Lo que ve la red (v3.1):** una red cuyo JSON diga `"alcance_v": 2` recibe en `yo_arma_alcance` el alcance
  completo con el que el mob pega. Las demás siguen con el número del contrato v3. Los nombres, el orden y el
  número de entradas no cambian. Está en `docs/red_mob_v3_1.md`.
- **Pruebas:** `AlcanceGameTests.aSwordReachesFurtherThanAFist` y `yoArmaAlcanceFollowsTheNetworksVersion`, esta
  con una red falsa de cada versión.

## 6. Clases

**Tus decisiones:**

- **A.** Cambiar de clase vuelve al nivel 1, sin experiencia. En tu misma clase solo se reinician los talentos.
- **B.** El Curandero hace un tercio de daño con magia y la mitad cuerpo a cuerpo. Su magia sigue curando igual a
  los aliados, y a sí mismo un tercio de lo que cura a otros.
- **C.** El tope sigue en 15.
- **D.** El **Medallón del olvido** se forja en la mesa de forja: un núcleo de eco (3 fragmentos de eco), un
  engaste y una cadena, y un golpe de martillo. El Emblema de antes ya no tiene receta; los que ya existan siguen
  sirviendo.
- **F.** Factores de daño: Arquero y Mago ×0,7 cuerpo a cuerpo; Guerrero y Asesino ×0,4 en magia; Tanque ×0,67 en
  todo.
- **Teclas:** K, V y B se cambian en Controles, y la prueba del cliente lo comprueba.

**Tabla por clase (tras los cambios):**

| Clase | Vida | Estamina máx. | Cuerpo a cuerpo | Proyectil | Magia | Otros números de base |
|---|---|---|---|---|---|---|
| Guerrero | 22 (+10 %) | 120 (+20 %) | ×1 (+5 % de daño) | ×1 | ×0,4 | regeneración de estamina +10 %, postura +15 %, parada +1 tick |
| Asesino | 14 (−30 %) | 130 (+30 %) | ×1 (+10 % de daño) | ×1 | ×0,4 | velocidad +5 %, esquiva: distancia +35 %, espera −30 %, coste −20 %, invulnerabilidad +2 ticks |
| Tanque | 32 (+60 %) | 100 | ×0,67 | ×0,67 | ×0,67 | armadura +2, empuje +30 %, velocidad −12 %, distancia de esquiva −35 %, coste de esquiva +20 %, estamina al parar −25 % |
| Mago | 18 (−10 %) | 90 (−10 %) | ×0,7 | ×1 | ×1 (+15 % de hechizos) | espera de hechizos −15 %, maná +25 %, regeneración de maná +20 % |
| Curandero | 20 | 100 | ×0,5 | ×1 | ×1/3 (cura a aliados como antes) | curación +50 %, maná +15 %, regeneración de estamina +10 % |
| Arquero | 18 (−10 %) | 100 | ×0,7 | ×1 (+15 % de proyectiles) | ×1 | velocidad +8 %, tensado +10 %, esquiva +20 %, espera de esquiva −15 %, caída −25 % |
| Herrero | 21 (+5 %) | 100 | ×1 | ×1 | ×1 | minado +15 %, ventana del golpe perfecto +0,01, potencial +5, reparación +25 % |

Un golpe es `arma × (1 + suma de los porcentajes de la clase y los talentos) × factor`: los porcentajes se suman
entre sí y los factores multiplican al final.

**Lo demás de las clases:**

- **Farol de curación:** lo puede usar cualquiera. Un toque lanza un rayo al primer aliado; cargado, suelta un
  anillo que cura a los aliados. Cuesta 12 de maná por toque, como aprobaste, y un 25 % más cargado.
- **Maná real:** está unido a `magic/Mana.java`.
- **Textos:** todos en español e inglés, y un capítulo "Clases" en la guía.
- **Detalle:** `docs/CLASES.md`, con los 70 talentos.
- **Pruebas:** `ClasesGameTests`.

## 7. El jefe contra los demás, y el pararrayos

- **Daño de lo que no es un jugador:** un tercio (`jefeDanoAjeno` = 0,333). Una config con el 0,5 de antes pasa
  sola a 0,333.
- **Movimiento nuevo, "La forja reclama":**
  - **Cuándo salta:** si 3 criaturas sin dueño le atacan en 10 s, o si las grandes (gólem, warden…) le quitan
    un 10 %.
  - **Qué hace:** avisa 1,5 s con un anillo morado que se cierra, arrastra hacia él lo que hay a 12 bloques y,
    al clavar el martillo, eso desaparece sin botín ni experiencia.
  - **A quién no toca nunca:** jugadores, mascotas, lo que monta un jugador ni sus aprendices.
  - **Enfriamiento:** 40 s.
- **Tu pregunta de la lluvia de estrellas del jefe:** cae **una** estrella por llamada.
  - Cada 3 s, en su último cuarto de vida, marca el sitio del objetivo y alza el martillo.
  - 20 ticks después cae un solo impacto: 9 de daño en un cuadrado de 6 × 6.
  - Lo de "lluvia" es solo el dibujo de las partículas.
- **Pararrayos de estrellas (`forja:pararrayos`), para la lluvia de meteoritos del cielo:**
  - **Atrae:** un meteorito que iba a caer a 12 bloques o menos cae sobre él, sin cráter, y deja el hierro
    estelar a sus pies.
  - **Aviso:** el anillo del suelo y el chat ya señalan el pararrayos.
  - **Desgaste:** aguanta 4 meteoritos y se ve cómo se gasta.
  - **Más meteoritos:** además del de siempre, cada minuto del evento cae otro cerca de cada jugador con un 50 %
    de probabilidad.
  - **Receta:** amatista arriba, acero entre dos lingotes de cobre y hierro abajo.
- **Pruebas:** `JefeGameTests` y `PararrayosGameTests`.

## 8. Animaciones de los mobs

- **Fallo de GeckoLib 5.5.5:** los 14 monstruos del mod, no solo la Coraza Vacía, se quedaban congelados tras su
  primer golpe o especial. Arreglado en todos con `MobMoves`.
- **Revisión de cada monstruo:** aviso, golpe, especiales, correr, aturdido, daño y muerte. Cada aviso dura lo
  mismo que en el código.
- **Herrero Caído:** animaciones de correr, llamar a los aprendices, la lluvia de estrellas y "La forja reclama".
- **Pavesa y ascua mayor:** ya no se lanzan en picado antes de acabar su aviso.
- **Capturas:** en `docs/capturas_2026-09-29/animaciones_mobs/`.

## 9. Los monstruos no construyen (y lo que sí pueden romper)

- **Fuera:** los pilares de tierra de los zombis y el excavar bloques blandos (`MovementGoals.Builder`).
- **Se queda, como decidiste:**
  - la telaraña de la araña (un bloque que se quita a los 5 s);
  - el fuego del Cargador de carbón y del Herrero;
  - la cabeza lanzada;
  - las explosiones de los creepers;
  - los bloques que coge el enderman;
  - los zombis que rompen puertas.

  La telaraña llegué a quitarla y la he devuelto tal cual.
- **Lo único nuevo que podrán romper, con la v4:** antorchas (normal, de pared, de almas y de almas de pared), y
  solo con `mobGriefing` activado.
- **Pruebas:** `zombiesNeverBuildNorDig` (ni un bloque cambia en 5 s con un zombi bajo un jugador en un pilar).
  Borré `zombieDigsThroughLeaves`: ya no excavan, y en el CI solo pasaba porque las hojas sin tronco se caen solas.

## 10. Dificultad: el buen equipo ya no vuelve inofensivas a las multitudes

| Qué | Antes | Ahora | En `config/forja.json` |
|---|---|---|---|
| Daño de los monstruos según tu tramo de equipo (0-3) | igual | +15 % por tramo (+45 % en el 3) | `mobDamagePerGearTier` |
| Atacantes a la vez | 2 de base, tope 4 | +1 por tramo; +1 en MAESTRO y +2 en LEYENDA (hasta 9) | `attackersPerGearTier`, `attackersMaestro`, `attackersLeyenda` |
| Penetración de armadura por amenaza | 0 | veterano 10 %, élite 20 %, campeón 35 % (cuenta la mayor, con la presión) | `penetrationVeteran/Elite/Champion` |
| Presión por golpe | 0,07 | 0,10 | `pressurePerHit` |
| Espera antes de que baje la presión | 40 ticks | 60 ticks | `pressureDelayTicks` |
| Golpe parado con escudo o desviado | no sumaba | suma la mitad | `pressureBlockedShare` |

Una config con los valores viejos de presión pasa sola a los nuevos.

**Armaduras (revisión):**
- **Diamante y netherita con Protección IV:** paran un 81-84 %. La mayor parte la hace la Protección IV: el
  diamante sin ella para un 47-57 %.
- **Las del mod a mejoras completas:** estaban entre 1 y 4,4 puntos por encima de la netherita con P4.
- **Lo que he bajado:** la armadura de las 4 más altas.
  - corazón y acero vivo: de 24 a 21 en el juego completo;
  - solacero y lunacero: de 23 a 20.

  Ahora la peor queda 4 puntos por encima de la netherita con P4.

| Juego (torso, contundente / cortante) | Antes | Ahora | Frente a netherita P4 |
|---|---|---|---|
| Diamante P4 (vanilla) | 81,1 / 84,3 | igual | |
| Netherita P4 (vanilla) | 82,9 / 84,3 | igual | |
| Corazón | 86,9 / 86,7 | 86,0 / 85,9 | +3,2 / +1,6 |
| Acero vivo | 86,9 / 86,7 | 86,0 / 85,9 | +3,2 / +1,6 |
| Solacero | 86,6 / 87,2 | 85,6 / 86,2 | +2,8 / +1,9 |
| Lunacero | 86,1 / 86,7 | 85,0 / 85,7 | +2,2 / +1,3 |
| Obsidiacero | 86,7 / 85,9 | igual | +3,9 / +1,6 |

- **La prueba:** `ArmaduraGameTests` monta cada juego de verdad, lo golpea y exige como mucho netherita P4 + 5
  puntos.
- **La tabla entera:** 39 materiales, en `docs/EQUILIBRIO.md`.
- **Aviso:** con el don Baluarte en todas las piezas, prensa perfecta y obra maestra, el obsidiacero llega a +5,9.
  No lo he tocado porque es la armadura del Herrero Caído.

## 11. Modo rodeo

- **Qué hace:** si retrocedes, un monstruo que va a su hueco del anillo corre a **×2,3** en vez de ×1,35.
  - **Coste:** 1,4 de aguante por tick, así que le dura unos 3,5 s.
  - **Solo camino de su hueco,** nunca huyendo.
  - **Quién lo usa:** las reglas, y la salida de correr de la red cuando el monstruo tiene hueco.
  - **Ajuste:** `rodeoSpeed` y `rodeoCostPerTick`.
- **Tres fallos que encontré y arreglé:**
  - **Velocidad del jugador:** se leía de lo que manda el cliente, que es 0 tras un teletransporte y en un jugador
    de prueba. Ahora también se calcula por posiciones.
  - **Sin camino:** si no hay camino a un hueco junto a un jugador que se mueve, el monstruo va derecho a él. A un
    hueco lejano va por puntos de paso de 12 bloques.
  - **Huida:** el rodeo también valía para huir. Ahora solo para ir al hueco.
- **Por qué fallaba la prueba en tu PC:** los 4 zombis acababan exactamente a la misma distancia (31,7) porque
  chocaban con el borde del área de la prueba, de 8 bloques. El jugador de prueba lo atraviesa y ellos no. No era
  la IA, así que quité esa prueba de servidor.
  - `theSurroundModeRunsAtTwoPointThree` comprueba el modo en sí.
  - La escena entera va en la prueba del cliente (sección `cerco`, terreno abierto), que exige que al menos dos le
    hayan rodeado al final.
- **Resultado en el juego:** 6 zombis contra un jugador que retrocede a velocidad de carrera.
  - Le siguen a 4-7 bloques.
  - Dos o tres llegan a los lados, en corro.
  - Ninguno se le pone detrás en 5 s.
  - A los 3,5 s se quedan sin aliento, como está diseñado.
  - Vistas: `docs/capturas_2026-09-29/cerco_y_retroceso_1.jpg` y `cerco_retroceso_2.jpg`.
- **Lo que tienes que probar:** si ×2,3 y 3,5 s te parecen bien jugando. Si quieres que te adelanten, se sube
  `rodeoSpeed` o se baja `rodeoCostPerTick`.

## 12. Red v4 (con el chat de entrenamiento)

- **M0:** hecho (sin construir; las puertas siguen como en vanilla, como decidiste).
- **Acciones preparadas (`MobActions`), apagadas hasta que la red las decida:**
  - coger un arma mejor del suelo (también la tuya, que cae al morir);
  - beber, lanzar y comer;
  - perla de ender;
  - romper antorchas;
  - escudo inteligente.
- **Carga de redes:**
  - carpeta `config/forja/redes_v4/`;
  - opción `iaContrato` (`v3`, `v4` o `auto`);
  - se elige por el campo `formato`, no por los nombres;
  - las redes v3, v3b y v3.1 siguen igual.
- **Percepción honesta** (`iaPercepcionHonesta`): un monstruo que te ha perdido de vista 20 ticks va a donde te vio
  por última vez, no a donde estás. Una base cerrada sigue siendo segura.
- **M1, hecho** (`6ca6825`, compiló a la primera y con todas las pruebas en verde):
  - `ObsV4` da las 468 entradas del contrato, nombre por nombre y en orden. Una prueba lo compara con
    `red_mob_v4_contrato.json`.
  - Las 280 de v3b, el bloque S (sectores) y el R (alcance mínimo) son de verdad; el resto va a 0 como en S1.
  - Hay entradas cuyo texto sugiere un valor neutro distinto de 0 (`bloqueo_hace`, `moral_*`, `mundo_muerte_*`...).
    Van a 0 en el código y están apuntadas en `docs/red_mob_v4_neutros.md` para que el simulador las confirme.
  - Una `red_mob_v4` en `config/forja/redes_v4/` que encaja (468 entradas y 53 salidas) manda sobre la v3 de su
    familia.
  - De las salidas, se ejecutan mover, saltar, usar, las 13 tácticas de hoy, especial, defensa, finta y correr.
    Las 8 tácticas nuevas, el objeto, la furia y el golpe de escudo van enmascarados hasta M2-M5.

## Capturas

- **La forja reclama:** `docs/capturas_2026-09-29/jefe_reclama.jpg`, en 4 vistas: el aviso, el anillo, el
  arrastre y cómo queda después, sin los gólems.
- **Mundo:** `docs/capturas_2026-09-29/mundo_locate.jpg`, el castillo encontrado con `/locate`, visto desde arriba y
  desde los cuatro lados.
- **Pararrayos:** `docs/capturas_2026-09-29/pararrayos.jpg`. El meteorito que apuntaba a 6 bloques cae en el
  pararrayos (desgaste 1, sin cráter).
- **Cerco y retroceso:** `docs/capturas_2026-09-29/cerco_y_retroceso_1.jpg` y `cerco_retroceso_2.jpg`.
- **Pendientes:** clases y castillo (tercera pasada).

## Estado de las pruebas

- **`a680dd2`:** 285 de 285 en verde en el CI, y también en tu PC, según la sesión "Mod Forja".
- **`flail_zombie_strikes_from_its_reach`**, inestable en tu PC ("se acercó a 1,91"):
  - **Causa:** un zombi astuto que espera turno flanquea a 2,5 bloques de centro a centro, y eso deja un hueco
    de 1,9. Con el mangual se metía dentro de su propio alcance.
  - **Arreglo:** ahora flanquea desde su alcance (`Reach.standOff`), con la prueba `aFlailZombieFlanksFromItsReach`.
- **`telegraphed_attack_hits_still_player`:** la inestable de tu lista.
  - **Lo que he visto:** falla más o menos una vez de cada dos, con el husk sin ninguna meta en marcha.
  - **Lo que he hecho:** si vuelve a fallar, el mensaje dice ya por qué (metas, camino, alcance, turnos y si te ha
    perdido de vista). Aún no he visto un fallo con esos datos.

## Lo que no pude hacer

- **"Antes" de la IA:** no hay capturas de la IA antigua. Hacía falta un commit temporal con la IA vieja y el
  sistema de permisos lo bloqueó.
- **Responder a la sesión "Mod Forja":** desde aquí no puedo mandar mensajes a otra sesión. Todo lo que le habría
  dicho está en este resumen.

## Preguntas para ti

1. **Castillo menos raro de encontrar.** El conjunto está a 110 chunks de separación (unos 1.760 bloques). ¿Lo bajo
   a 80 para que haya más?
2. **Sitio del castillo.** ¿Te parecen bien los límites nuevos (desnivel 24, 3 muestras con agua), o prefieres
   más llano aunque salgan menos?
3. **Protección IV.** Es lo que de verdad vuelve inofensivas a las multitudes. ¿Limito la mejora de Protección de
   la forja a III, o activo `soloMejorasForja`?
4. **Baluarte.** Con él, el obsidiacero pasa de +5 sobre la netherita P4. ¿Hago que dé dureza en vez de armadura?
5. **Modo rodeo.** ¿Te vale que te sigan y te flanqueen, o quieres que te adelanten? Si es lo segundo, subo
   `rodeoSpeed` o bajo el coste.
6. **Contrato del blaze.** El simulador propone un contrato propio para el blaze. ¿Lo escribimos como
   `red_blaze_contrato.json` v1?
