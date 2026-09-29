# Resumen de la sesión en la nube — 2026-09-29

Para Andy. Todo está en la rama `claude/hola-rv9w0u` (PR #1). El detalle de la primera tanda está en
`docs/RESUMEN_2026-09-29.md`; aquí va todo junto, con la segunda tanda (la que mandaste por la sesión "Mod Forja")
y lo que vino después.

**Estado del CI:** `b438486` compila y pasan todas las pruebas de servidor, también las nuevas de `SitioGameTests`.

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

## Capturas

- **La forja reclama:** `docs/capturas_2026-09-29/jefe_reclama.jpg`, en 4 vistas: el aviso, el anillo, el arrastre y cómo queda después, sin los gólems.
- **Mundo:** `docs/capturas_2026-09-29/mundo_locate.jpg`, el castillo encontrado con `/locate`, visto desde arriba y
  desde los cuatro lados.
- **Pendientes:** pararrayos, clases y castillo.

## Estado de las pruebas

- **Pruebas intermitentes:** `flail_zombie_strikes_from_its_reach` y `telegraphed_attack_hits_still_player`
  fallaron una vez cada una y pasaron al repetir.
- **La prueba de la mecha a 8 bloques:** fallaba porque el jugador se alejaba hacia fuera de la zona de la prueba.
  Ahora se aleja en diagonal por dentro, y si falla dice la distancia, si el creeper lo ve y cuánto lleva la mecha.

## Lo que no pude hacer

- **"Antes" de la IA:** no hay capturas de la IA antigua. Hacía falta un commit temporal con la IA vieja y el
  sistema de permisos lo bloqueó.
- **Responder a la sesión "Mod Forja":** desde aquí no puedo mandar mensajes a otra sesión.

## Preguntas para ti

1. **Castillo menos raro de encontrar.** El conjunto está a 110 chunks de separación (unos 1.760 bloques). Con el
   control del terreno, el más cercano puede quedar lejos. ¿Lo bajo a 80 para que haya más?
2. **Sitio del castillo.** ¿Te parecen bien los límites nuevos (desnivel 24, 3 muestras con agua), o prefieres
   más llano aunque salgan menos?
3. **Simulador.** Del documento de propuestas quedan 2.2 (sectores con cupos), 2.3 (carrera barata) y 2.6 (roles
   por tipo). ¿Los hago ya en el mod, o esperamos a que el simulador los mida?
4. **Contrato del blaze.** El simulador propone un contrato propio para el blaze (más entradas y salidas en 3D).
   ¿Lo escribimos como `red_blaze_contrato.json` v1, con su observación y su ejecutor en el mod?
5. **Mob contra mob con red.** El simulador lo deja para después de una v4. ¿De acuerdo?
