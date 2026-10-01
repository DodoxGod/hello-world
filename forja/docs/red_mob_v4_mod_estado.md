# Red de mobs v4: estado en el mod (para la sesión del simulador)

Rama `forja-v4-mod`. Qué entradas y qué salidas del contrato `red_mob_v4_contrato.json` ya están vivas en el mod, con
su significado exacto y sus unidades donde difieren del diseño (`red_mob_v4_diseno.md`), y lo que el simulador tiene
que copiar. Los nombres de las 468 entradas y las 53 salidas **no cambian**: las comprueba
`RedV4GameTests.v4NamesMatchTheContract`. Los números de cada mecánica están en `COMBATE_ESPECIFICACION.md` §5.

Regla general: lo que aún no se calcula vale **0**, como en el paso S1 del simulador. Todo lo nuevo lo usan igual una
red v4 y las reglas (llaman a los mismos ejecutores), así que el comportamiento de reglas mejora ya sin red v4.

## Pasos hechos

| Paso | Qué | Commit |
|---|---|---|
| M0, M1 | sin construir ni cavar; `ObsV4` con v3b + S + R; carga de `redes_v4`; cabezas enmascaradas | anteriores |
| M2 | escudo inteligente y golpe de escudo (G); objetos en el suelo, valor de arma y RECOGER (O); mochila, beber, comer, lanzar, perlas, carga de viento y cambiar de arma (C) | ver `git log` |
| M3 | bloque A; flecha de empuje, zarpazo, garfio; ASEDIAR; antorchas: APAGAR_LUZ, bloque L y `jug_luz` | ver `git log` |
| M4 | oído (sonidos), última posición y estimación en la observación y el ejecutor, caza hasta 48 y 600 ticks, BUSCAR, escondites y EMBOSCAR; bloques P y E | ver `git log` |
| M5 | capitán (de reglas y red `red_capitan_v4`), órdenes, formaciones y puestos, carga sincronizada, moral, furia, abandono; bloques M y Mo; FORMACION y furia | ver `git log` |
| M6 | `WorldMemory` (memoria por jugador y dimensión), bloque W, `/forja ia mundo` | ver `git log` |

## Entradas vivas (M2/M3)

Marco de siempre: "delante" = del mob al jugador en el plano, "derecha" = (−delante_z, delante_x).

### E: `jug_luz` (380)
- `jug_luz/15`: `getMaxLocalRawBrightness` en el bloque de los pies del jugador (cielo con su oscurecimiento y
  bloques), /15. El resto de E está en la sección M4.

### A (387–396): `Heights`, calculado por jugador como mucho cada 10 ticks
- `jug_sobre_suelo/8`: pies − mediana del "suelo" en 8 puntos a 2,5 del jugador. El suelo de un punto es la cara de
  arriba de la caja de colisión más alta desde el bloque por encima de los pies hacia abajo (hasta 16). **Diferencia
  con el diseño:** los puntos donde hay pared a la altura de los pies (almenas, lados de una zanja) no cuentan; si
  todos son pared, vale 0. Recortado a ±2.
- `jug_en_pilar`: en ≥ 6 de las 8 direcciones a 1,5 hay caída ≥ 2,5 desde los pies.
- `jug_en_torre`: sobre el suelo ≥ 2,5 y en ≥ 5 de 8 direcciones hay bloque a la altura del pecho (bloque de los pies
  + 1) a 1, 2 o 3 bloques (el diseño decía 1–2; 3 hace falta para las diagonales de una torre de 3×3).
- `jug_borde/2`: distancia (en pasos de 0,5, hasta 4) al primer punto de las 8 direcciones con caída ≥ 2,5; una pared a
  la altura de los pies corta esa dirección. **Neutro = 4/2 = 2** (sin borde en 4 bloques).
- `jug_caida_empuje/8`: caída desde los pies en el punto 1,5 más allá del jugador en la dirección mob → jugador; 0 si
  ahí hay pared.
- `jug_alcanzable`: cuerpo a cuerpo: dy de pies en (−alto del jugador, alto del mob) (la caja de golpe vanilla solo se
  ensancha en horizontal); arquero y blaze: lo ve; creeper: dy ≤ 3.
- `pared_trepable`: solo con sobre el suelo ≥ 1: la columna que lo sostiene, o una a 1–3 bloques en las 8 direcciones,
  es sólida desde la altura de sus pies hasta el suelo sin hueco de 2 o más.
- `jug_arriba/200`: ticks seguidos con sobre el suelo ≥ 2 (medido en los escaneos, cada ≤ 10 ticks).
- `jug_tira_desde_arriba`: en los últimos 40 ticks, estando arriba, había un proyectil suyo de ≤ 11 ticks a ≤ 6 de él.
- `veo_jug_arriba`: arriba (≥ 2) y un rayo de mis ojos al 60 % de su altura no choca con bloques.

### O (397–416): `GroundItems`
- Los 2 objetos útiles más cercanos a mí, a ≤ 12. Útil = que pueda coger ya (sin retraso de recogida, o soltado por un
  jugador hace > 40 ticks) y con mejora > 0.
- `objetoK_mejora/10`: valor en mis manos − lo que llevo (fórmula en COMBATE §5); escudo = 3, consumible con sitio en
  la mochila = 1, caña = 0,5.
- `objetoK_del_jugador`: el dueño (`getOwner`) del objeto es un jugador (lo tiró o murió).
- Tipo one-hot: `arma_cuerpo`, `arma_distancia` (arco, ballesta **y caña de pescar**), `escudo` (con BLOCKS_ATTACKS y
  sin daño: las espadas de Forja también bloquean y son armas), `consumible`.

### C (417–434): `MobKit`, `MobItems`
- `inv_*`: cuentas de la mochila con los topes del diseño (/2, /2, /3, /3, /2, /3).
- `consumo_enfriamiento/40`: ticks que faltan del enfriamiento común tras el último objeto (20 beber o comer, 40
  poción, 100 perla, 60 carga, 40 cambiar de arma).
- `consumiendo`: hay un objeto en curso (bebiendo, comiendo, cambiando de arma **o en el aviso de un lanzamiento**).
- `yo_ef_negativo`: cualquier efecto de categoría HARMFUL.
- `jug_ef_mejora`: fuerza, velocidad, resistencia o regeneración.
- `perla_destino_ok` y `lanzamiento_ok` valen **0 si no llevo perla** (la primera) **o nada que lanzar** (poción
  arrojadiza o carga de viento, la segunda): solo se calculan para quien puede usarlos.
- `perla_destino_ok`: jugador a ≤ 16 y o bien arriba o en pilar y lo veo (apunto a sus pies + 0,1), o bien hay un
  punto a 2 de él en mi lado con suelo (de 2 por encima a 3 por debajo), sin lava ni caída, a ≤ 16 de mí y con línea
  recta libre de mis ojos a ese punto + 1.
- `lanzamiento_ok`: jugador a 3–10 y libres los dos tramos ojos → (punto medio + 1,5) → su pecho.

### G (435–439): `ShieldPlay`
- `yo_escudo_ticks/20`: ticks con el escudo (mano izquierda) arriba.
- `jug_golpe_en/10`: max(recarga que le falta = (1 − fuerza de ataque) × retraso del arma; 0 si está cargando un golpe
  de Forja, (distancia − alcance del jugador) / 0,28), tope 20 ticks → 2. **Neutro ≈ 0** si el jugador tiene el
  arma recargada y está a su alcance, como dice el diseño.
- `jug_golpe_fuerte`: carga de Forja ≥ 0,8, o su arma tiene especial de área, está recargada y estoy dentro del radio.
- `bloqueo_hace/20`: ticks desde mi último bloqueo o parada / 20; **1 si nunca o hace > 20** (neutro 1).
- `golpe_escudo_listo`: la máscara de la salida 52.

### L (440–451): `Lights`, antorchas escaneadas por jugador como mucho cada 10 ticks
- Solo antorcha, de pared, de almas y de almas de pared, a ≤ 16 del jugador en horizontal y ≤ 8 en vertical, y a ≤ 16
  de mí. **Orden: las 2 más cercanas al jugador** (Manhattan), no a mí: son las que más lo iluminan, y la máscara
  mira la luz 0.
- `luzK_aporte/15`: ver la fórmula en COMBATE §5 (luz de bloque real en sus pies, emite 14 o 10 − Manhattan, las demás
  antorchas y el cielo actual).
- `luzK_alcanzable`: hay un sitio donde estar (su columna o las 4 de al lado, de 0 a 2 por debajo de la antorcha, con
  sitio para el mob y suelo sin lava) a ≤ 4 de mi altura, y no ha fallado una ruta a ella en los últimos 100 ticks.
  **No depende de `mobGriefing`**: la máscara de APAGAR_LUZ sí (se cierra sin `mobGriefing`).

## Salidas vivas (M2/M3)

| Salida | Máscara en el mod | Ejecutor |
|---|---|---|
| 34 SECTOR | tiene hueco (los de anillo: el suyo; arqueros y creepers: su "hueco" donde están) | a su hueco del anillo, por fuera si cruza por delante |
| 35 TIRO_LIBRE | familia arquero y aliado en la línea | paso al punto de tiro libre más cercano (`Squad.clearLineStep`) y tira |
| 39 RECOGER | `mobActionsV4`, objeto0 presente, a ≤ 3 de altura y a la vista (**"con ruta" barato: no se pide ruta**) | ver COMBATE §5 |
| 40 APAGAR_LUZ | torches permitidas (`mobActionsV4`, `mobsBreakLights`, `mobGriefing`), luz0 presente y alcanzable | ver COMBATE §5 |
| 41 ASEDIAR | jugador con sobre el suelo ≥ 2 o en torre | anillo de asedio, COMBATE §5 |
| 43–50 objeto | lo lleva, no hay otro en curso, enfriamiento 0, no aturdido; 4 y 7 `lanzamiento_ok`; 5 `perla_destino_ok`; 6 no en furia; 8 lleva un arma de repuesto (no una caña) y ≥ 40 ticks desde el último cambio | ver COMBATE §5 |
| 52 golpe_escudo | escudo arriba, bloqueo < 20 ticks, alcanza, listo | ver COMBATE §5 |

EMBOSCAR (37) y BUSCAR (38) se abren en M4; FORMACION (36) y furia (51), en M5 (ver abajo).

**Especial 3 (salida 23):** nuevo hueco para la flecha de empuje (esqueleto, stray, bogged, saqueador), el zarpazo
(araña, araña de cueva) y el garfio (zombi, husk, aldeano zombi, ahogado, vindicador, piglin, piglin bruto, piglin
zombificado, esqueleto wither, con una caña en el repuesto). Los movesets que tenían menos huecos se rellenan con un
hueco vacío que se lee como "sin especial" (`esp2_*` no cambia). **Una red v1–v3 no ve el hueco 3** (se lee como vacío
y nunca empieza); una v4 y las reglas sí, así que `esp3_disponible` y `esp3_enfriamiento` de la base v3b ya se mueven
para estos mobs en la v4.

## Lo que el simulador tiene que copiar (M2/M3)

1. Las fórmulas de arriba, en especial: valor de arma, mejora, aporte de luz, sobre el suelo sin puntos en pared, torre
   con paredes a 1–3, `jug_golpe_en` con 0,28 bloques/tick y tope 20.
2. Los tiempos y enfriamientos de la cabeza de objeto (tabla en COMBATE §5), el enfriamiento común y que un aturdido
   corta el consumo sin perder el objeto.
3. El golpe de escudo (4 ticks, empuje 0,8 + 0,15 arriba, −15 de estamina, corta la carga, enfriamiento 60).
4. Las tres herramientas del hueco 3 y sus condiciones.
5. ASEDIAR (anillo de 6–10 fuera de la vista de la cima; arqueros 12–16 con línea) y APAGAR_LUZ (15 ticks, soltarla).
6. Mochila al aparecer por amenaza (COMBATE §5).
7. **Carga de viento:** la de vanilla; la de un monstruo no activa bloques (puertas, botones).
8. **Perla:** 2 de daño al mob al llegar. Tiro a 45° (60° si el destino está a menos de 0,5 más lejos que alto), con
   la velocidad que, simulando el vuelo de un objeto lanzado (cada tick: se mueve, frena ×0,99 y cae 0,03), lo baja en
   el punto; se busca por mitades entre 0,3 y 3 (`MobItems.lobSpeed`: 8 bloques y 2,5 de subida a 45° → 0,615).
9. **La poción arrojadiza** apunta a donde estará el jugador: su velocidad × (distancia / 0,7) ticks; la carga de
   viento, igual con 1,5.

## M4: percepción, oído, rastro y emboscadas

### Percepción honesta en la observación (`Perception`)
- **Percibido** (`obj_percibido`): este tick hay rayo de sus ojos a los ojos del jugador, dentro del alcance en que
  piensa, y no lo oculta la regla de la noche (`WorldFights.hiddenByNight`: de noche, a > 12 y con luz < 4 en los pies
  del jugador).
- **Estimación**: donde lo vio por última vez, o donde lo oyó por última vez si es más reciente.
- **Mientras no lo percibe, las 468 entradas** (también las 280 de v3b) se calculan contra un **jugador sustituto**
  puesto en la estimación, con lo que tenía del jugador la última vez que lo percibió: vida, lo que llevaba en las manos
  y la armadura, hacia dónde miraba, si corría o iba agachado, y su estamina. Velocidad 0 y sin usar objeto (arco,
  escudo, comer). Lo que depende de **quién** es el jugador y no de **dónde** está (el grupo que pelea con él, sus
  turnos, el rencor, sus hábitos) se lee del jugador real. Así la posición real **nunca** llega a la red (prueba
  `v4EstimateNeverLeaksTheRealPosition`).
- Un mob que nunca lo vio ni lo oyó (asedio, llamada de ayuda) no tiene estimación y ve al jugador real, como el
  ejecutor.
- **Con el jugador no percibido se cierran** los objetos 4 (poción), 5 (perla para acercarse) y 7 (carga de viento):
  apuntarían al jugador real. Los especiales ya no empezaban sin percibirlo (M1).

### P (338–365)
- `obj_oido`: oyó un sonido del jugador en los últimos 20 ticks.
- `obj_edad/100` y `ultima_edad/200`: ticks desde que lo vio por última vez; **0 mientras lo percibe**; si nunca lo vio,
  400 (2 y 2).
- `ultima_delante/16`, `ultima_derecha/16`, `ultima_dy/4`: dónde lo vio por última vez, en el marco de siempre (el
  marco va hacia la estimación cuando no lo percibe). 0 si nunca lo vio.
- `sonidoK_*`: los 2 últimos sonidos que **este mob** oyó del jugador (el más reciente primero), mientras no tengan
  más de 80 ticks. `fuerza` = radio/16 (6, 10, 12, 16 → 0,375…1). Tipos: movimiento, trabajo, comer, combate.
- `buscando/200`: ticks desde que lo perdió (HonestPerception: 20 ticks sin percibirlo) mientras no lo percibe.

**Sonidos** (`Hearing`): los eventos del juego que provoca el jugador. Radios: paso andando 6, corriendo 12
(agachado no hay evento de paso), caer 10, romper o poner bloque 16, abrir o cerrar puertas y cofres 12, comer o
beber 8, disparar 16, un golpe suyo que hace daño 12. **Sin línea** entre el sonido (+0,5 de altura) y los oídos del
mob, el radio se divide por 2.

### E (366–386)
- `oculto_r3_*`: desde el punto a 3 bloques en cada dirección del marco (a la altura de sus ojos) no hay rayo a los
  ojos del jugador. Cada 10 ticks.
- **Escondite** (cada 10 ticks, y se conserva mientras siga oculto y a ≤ 8): 16 puntos alrededor del mob (8
  direcciones a 3 y 6), en el centro de su bloque, con suelo y sitio, sin rayo desde los ojos del jugador a 1,5 por
  encima. Nota: luz < 4 +2; a 2–5 del camino probable del jugador (su posición y la de dentro de 40 ticks con su
  velocidad) +1; esquina, marco de puerta (paredes en 2 o más lados a la altura de la cabeza) o techo a 2–3 +1; menos
  la distancia al mob / 8.
- `escondite_luz/15`: luz total (cielo y bloques) en el escondite.
- `me_ve_jugador`: rayo de los ojos del jugador a los míos, dentro de su cono de **70° en total** (35° a cada lado) y
  con luz ≥ 4 donde estoy o a < 8.
- `jug_en_pasillo`: pared de 2 de alto a ≤ 1,5 a los dos lados, perpendicular a su mirada.
- `jug_en_puerta`: en una puerta, puerta de valla o trampilla, o en un hueco de 1–2 entre paredes (pies y cabeza) en
  uno de los dos ejes, abierto delante y detrás. **Un pasillo de 1–2 también cuenta como hueco.**
- `jug_bajo_techo`: bloque con colisión a 1–4 por encima de su cabeza.
- `jug_sin_vernos/200`: ticks desde que algún mob del grupo que pelea con él estuvo en su cono con línea
  (`me_ve_jugador`); se mira cada 10 ticks.
- `emboscados/5`: miembros del grupo quietos en su escondite (EMBOSCAR y llegados).
- `yo_emboscado/200`: ticks que llevo quieto en el escondite.

### Salidas nuevas (M4)
- **BUSCAR (38)**, máscara: no lo percibe, lo vio alguna vez y hace < 600 ticks. Ejecutor, igual para cualquier
  cerebro con el jugador perdido: ruta a la estimación; al llegar (< 2), 3 puntos a 6 bloques más allá, a 0° y ±60°
  de la dirección en que iba el jugador (su velocidad cuando lo vio, o la del mob hacia la estimación), uno tras otro
  (80 ticks cada uno como mucho); luego quieto mirando alrededor. Un sonido nuevo reinicia la búsqueda desde él.
- **EMBOSCAR (37)**, máscara: hay escondite. Ejecutor: ruta al escondite a 0,8; a < 1,5 se para y mira hacia el jugador
  (o la estimación). Con el jugador perdido, el escondite se busca desde la estimación.
- **Reglas**: con el jugador perdido, BUSCAR; con la búsqueda acabada y a oscuras (de noche o con luz < 4 donde está),
  EMBOSCAR.

### Caza
- Un mob sigue pensando y conserva su objetivo **hasta 48 bloques y 600 ticks** sin percibirlo ni oírlo (antes 32 y la
  memoria de 3 s de los objetivos vanilla, `TargetGoalMixin`).

## M5: capitán, órdenes, formaciones, moral y furia

### El capitán (`Captain`, `CaptainBrain`)
- **Quién:** el élite o campeón más fuerte del grupo (amenaza, luego vida máxima). **Nunca un veterano** (salvo el
  interino de la sucesión, ver "Capitán 2"). Un grupo sin
  élite ni campeón no tiene capitán. Grupo = los mobs cuyo objetivo es ese jugador (el del `Squad`).
- **Cada 10 ticks** (con el `Squad`): la red `redes_v4/red_capitan.json` (formato `red_capitan_v4`, contrato
  `red_capitan_v4_contrato.json`, 213 → 60) si la hay y encaja, diga lo que diga `iaCapitanReglas`; si no, el
  **capitán de reglas nuevo** (30-09, ver "Capitán de reglas nuevo" abajo; el de diseño §3.4 empeoraba los grupos) con
  `iaCapitanReglas` activado, y sin él un capitán que no da órdenes (miembros libres).
- **Una orden sigue** (su edad y su cuenta atrás) mientras orden, formación y sector no cambien.
- **Muere el capitán:** `sin_mando` = 1 y baja a 0 en 200 ticks; sin órdenes durante ese tiempo aunque quede otro
  élite; después otro élite puede mandar. Es un golpe de moral.
- **Foco 1:** con otro jugador vivo a ≤ 16 del capitán, los miembros sin turno cambian de objetivo a ese.
- **Duelos (idea 96):** un capitán que manda a más de 3 no reta a duelo.
- **Señales:** polvo dorado sobre el capitán cada 20 ticks; con CARGA, grito (celebración de saqueador y cuerno) a la
  mitad de la cuenta (si es ≥ 20) y al llegar a 0, con partículas de enfado.

### Carga sincronizada
- CARGA con `cuenta` 0/10/20/40 ticks. Al llegar a 0 el capitán grita y el jugador admite **un turno más durante 40
  ticks** (`Aggression.maxAttackers` + 1).

### Puestos y formaciones (geometría; ángulos como `Squad.angle`, frente = la media lenta de la mirada del jugador, `ObsV4.front`)
- Dentro de cada puesto, los miembros se reparten por su ángulo actual respecto al frente (de izquierda a derecha);
  en los puestos por lados, la primera mitad va a la izquierda (ángulos negativos) y el resto a la derecha; uno solo,
  al lado en que está.
- **MURO:** frente a 3,5 en ±40°; segunda a 8,5 en ±30°; flancos a 4 en ±100° (+15° por fila); reserva a 12 en ±20°.
- **PINZA:** frente y flanco a 3,5 en ±120° (+12° por fila); segunda a 8,5 en ±120°; reserva a 10 detrás (180° ±20°).
- **CUÑA:** frente a 3,5 en ±20°; segunda a 6 (+1 por fila) en ±35°; flanco a 5 (+1 por fila) en ±60°; reserva a 9
  en ±25°.
- **LIBRE:** el hueco estable del anillo.
- **Órdenes:** CERCAR lleva frente y flanco a ≥ 6 (nadie entra); RETIRADA: 12 más allá, alejándose del jugador;
  REAGRUPAR: en círculo de 2 alrededor del capitán; ESCOLTA: 2,5 del capitán hacia el jugador, a los lados (0,75 +
  0,75 por pareja); EMBOSCADA: su escondite (si no, la formación); ASEDIO: su sitio del anillo de asedio; CARGA: la
  formación hasta el 0 y luego el jugador.
- **Puestos por tipo (reglas, y miembros 9+ con red):** escudo o tanque → frente; arquero, lanzador o bruja →
  segunda; araña, velocidad ≥ 0,3 o corriendo → flanco; el resto → reserva.

### M (298–327)
- **Sin capitán:** `orden_ninguna` = 1 y `formacion_libre` = 1 (el diseño lo dice así; `red_mob_v4_neutros.md` lo
  dejaba en duda: **el simulador tiene que copiar el 1**), el resto a 0.
- `orden_edad/40`, `cuenta_atras/40` (ticks que faltan; 0 también sin CARGA), `orden_sector_*` (vector unitario del
  sector en mi marco; 0, 0 sin sector), `puesto_*` (one-hot; todo 0 sin puesto), `puesto_delante/8`,
  `puesto_derecha/8` (el punto de mi puesto), `capitan_*`.
- `cubierto`: un aliado con escudo o de puesto "frente" por tipo corta la línea de los ojos del jugador a los míos.

### Mo (328–337) y moral
- `moral_grupo` y `moral_propia`: las fórmulas del diseño §4.3; `miedo` y `en_casa` son del propio mob; los intrépidos
  (élite, campeón, jefe) nunca bajan de 0,8 en la propia.
- `bajas_frac` = muertos del grupo / su mayor tamaño en esta pelea; `bajas_recientes/5` = muertes en 200 ticks.
  **Pelea nueva** cuando el grupo lleva 60 ticks sin nadie.
- `aliados_huyendo/5`: otros en RETIRARSE seguido ≥ 40 ticks; `aliados_furia/5`: otros en furia.
- `retirada_ticks/100`: ticks seguidos en RETIRARSE. **A 100 y a > 20 del jugador, suelta el objetivo** (abandona la
  pelea), para cualquier cerebro.

### Furia (`Fury`; decisión 3 de Andy)
- Solo pueden los elegidos al aparecer: el 10 % del grupo del aparecido (líder y compañeros), redondeado hacia abajo,
  **al menos 1 si son 5 o más** (etiqueta `forja_furia`). A los demás se les enmascara.
- `furia_disponible`: elegido, no cobarde, no la usó en esta pelea y **golpe de moral en los últimos 200 ticks**
  (capitán muerto, o muertos ≥ la mitad del mayor tamaño con ≥ 2).
- **Salida 51**, máscara: disponible, no aturdido, sin aviso en curso. Efecto: 200 ticks con +25 % de daño y +20 % de
  velocidad; luego 100 ticks agotado (−20 % de velocidad y **sin turno**). En furia se enmascaran defensa 1–2,
  fintar, RETIRARSE, REAGRUPARSE, EMBOSCAR y objeto 6.
- **Reglas:** el agresivo entra siempre que puede; el prudente o astuto con un 2 % por decisión; el cobarde nunca.

### Salidas nuevas (M5)
- **FORMACION (36)**, máscara: capitán vivo y tengo puesto con punto. Ejecutor: al punto; si el camino recto pasa a
  < 3,5 del jugador y la orden es de las que rodean (CERCAR, HOSTIGAR, CARGA), por el anillo (`toRing`); si no, recto.
  Llegado (< 1), quieto mirándolo. Corre si está a > 5 (reglas).
- **furia (51)**: arriba.
- **Reglas que obedecen:** RETIRADA → RETIRARSE; REAGRUPAR y ESCOLTA → FORMACION; EMBOSCADA → EMBOSCAR; ASEDIO →
  ASEDIAR; CARGA → FORMACION hasta el 0 y luego al ataque; CERCAR y HOSTIGAR → FORMACION; un arquero con puesto va a
  él (a > 3) antes de tirar, **salvo sin orden y en LIBRE** (entonces tira desde donde está, como sin capitán).

### Capitán de reglas nuevo (30-09) y `iaCapitanReglas`

**Por qué.** El simulador midió que el capitán de reglas de §3.4 empeoraba los grupos (8 con un élite contra el
jugador "experto": sin capitán 121 de daño por minuto, capitán de reglas 111). En el mod era peor aún: mandaba CERCAR
casi siempre (97 % de las pasadas) y los grupos casi no hacían daño. Causas, en el código:
- CERCAR lleva los puestos de frente y flanco a ≥ 6 del jugador, y un miembro con CERCAR va a su puesto aunque tenga
  turno: nadie entra;
- la CARGA pedía el 60 % en su puesto y el jugador "ocupado" (usando un objeto, cargando un golpe o **con alguien que
  tiene turno sobre él**, y nadie lo tenía porque nadie atacaba) o "de espaldas" (un miembro a ≤ 8 a más de 120° de
  su mirada, y en sus puestos ninguno lo estaba). El grupo se quedaba parado en el anillo;
- los arqueros perseguían su puesto de segunda línea, que se mueve con la mirada del jugador, en vez de tirar.

**Las reglas nuevas** (`Captain.rules`), en este orden:
1. jugador en pilar o torre → ASEDIO;
2. moral del grupo < 0,3 → RETIRADA;
3. **jugador expuesto** (usando un objeto, cargando un golpe, tambaleándose —lentitud II o más, ver "Qué tambalea al
   jugador" abajo—, con < 30 % de vida, o con un miembro a ≤ 6 a
   más de 120° de su mirada), con ≥ 2 de cuerpo a cuerpo a < 10 y sin carga en los últimos 100 ticks tras el turno extra
   de la anterior → **CARGA con cuenta 0** (todos al ataque ya, grito y +1 turno 40 ticks);
4. noche, luz del jugador < 7 y nadie lo percibe → EMBOSCADA;
5. el jugador se aleja del centro del grupo a > 0,08 bloques/tick (y a < 16) → **sin orden, en PINZA**: los que no
   tienen turno rodean por los dos lados, los que lo tienen atacan;
6. si no, **sin orden y LIBRE**: los miembros pelean como sin capitán (anillo y turnos).

Las reglas ya no dan CERCAR ni HOSTIGAR (una red de capitán sí puede; para los miembros significan lo mismo que antes).
Una carga en curso sigue hasta que acaba su turno extra.

**El interruptor.** `iaCapitanReglas` (`CombatConfig`, **activado**). Apagado, el grupo conserva su capitán (el polvo
dorado, la moral, el golpe de moral y `sin_mando` si muere, y no reta a duelos) pero sin órdenes: `orden_ninguna` = 1 y
`formacion_libre` = 1, y los miembros pelean como sin capitán. `iaCapitan` apagado sigue quitando el capitán del todo.
Una `red_capitan.json` cargada manda siempre, diga lo que diga `iaCapitanReglas`.

**La medida en el mod** (`CapitanMedidaGameTests`, solo con `FORJA_CAPITAN_MEDIR=<archivo>`; `FORJA_CAPITAN_N` peleas por
modo, 8 por defecto; `FORJA_FILTRO='forja-test:capitan_medida*' ./gradlew runGametest` corre solo esas). Grupo de 8:
un zombi élite con espada de hierro, 3 zombis (uno con escudo), un husk, una araña y 2 esqueletos con arco, que llegan
desde 11–14 bloques por un lado al azar. El jugador de prueba se queda quieto, se aleja del grupo (0,18/tick) o lo rodea
(0,15/tick) por fases de 25–54 ticks con semilla, mira al mob más cercano y le pega 5 cada 16 ticks si está a ≤ 3,5. Se
le devuelve la vida cada tick y se suma lo que pierde; "muerto" = 20 de daño acumulado. 600 ticks por pelea, las mismas
40 semillas en cada modo (el azar de Minecraft no se fija, así que cada tanda varía unos ±5 de daño por minuto):

| Capitán | Peleas | Daño/min | "Muerto" en 30 s | Tiempo hasta 20 de daño | Órdenes |
|---|---|---|---|---|---|
| sin capitán (`iaCapitan` apagado) | 40 | 37,5 ± 5,0 | 16/40 | 14,8 s | — |
| capitán de reglas viejo (§3.4) | 40 | **0,6 ± 0,3** | 0/40 | — | CERCAR 97 %, CARGA 2 % |
| capitán sin órdenes (`iaCapitanReglas` apagado) | 40 | 35,6 ± 4,0 | 16/40 | 16,5 s | — |

| Capitán (tras el cambio; 2 tandas, 80 peleas) | Daño/min | "Muerto" en 30 s | Tiempo hasta 20 de daño | Órdenes |
|---|---|---|---|---|
| sin capitán | 37,5 ± 3,0 | 36/80 | 16,8 s | — |
| capitán sin órdenes | 38,8 ± 2,7 | 36/80 | 14,9 s | — |
| **capitán de reglas nuevo** | **77,7 ± 3,2** | **71/80** | **13,3 s** | CARGA 24 %, nada 76 % |

Pareado por semilla, el capitán nuevo hace +39,6 ± 3,9 de daño por minuto más que sin capitán y gana en 70 de 80
peleas. Pruebas aparte: sin la pinza, 74,8 (la pinza aporta poco, dentro del ruido); sin el turno extra de la carga,
80,3 (la ventaja viene de que todos entren a la vez, no del turno de más). Por eso `iaCapitanReglas` va **activado**.

**Lo que el simulador tiene que copiar:** estas reglas nuevas en `CaptainRules` (y medir otra vez contra el "experto");
la imitación de la red del capitán debería partir de ellas y no de las de §3.4.

### El anillo libre del mod se atascaba (30-09, segunda tanda; responde a `mod_spec_capitan.md`)

El simulador no reprodujo la ganancia del capitán (sin capitán 249, capitán nuevo 255) y apuntó a un fallo del mod: sin
capitán, solo 37,5 de daño por minuto. `CapitanMedidaGameTests` apunta ahora, por pelea, cuántos mobs hay a ≤ 3,5 del
jugador y cuántos turnos hay ocupados, y con `FORJA_CAPITAN_TRAZA=<archivo>` escribe cada tick de las 2 primeras peleas,
mob por mob (distancia, táctica, turno, aviso, corre, aturdido y metas que corren).

**Lo que se vio (sin capitán, antes del arreglo):** turno libre el **96 %** de los ticks, 0,25 turnos ocupados de 2 de
media, 1,1 mobs a ≤ 3,5. De los mobs sin turno a ≤ 6 del jugador con un turno libre:
- ~20–26 % en **RODEAR**: el modo rodeo (`MobSprint.rodeo`) los mandaba a su hueco del anillo cada vez que el jugador se
  alejaba de ellos, **aunque tuvieran turno libre**. Contra un jugador que se mueve, casi todo el grupo daba vueltas;
- ~10–13 % en **RETIRARSE**: la regla de la postura (> 0,7) los apartaba aunque pudieran golpear;
- ~5–9 % en **FLANQUEAR**: el flanqueador rodeaba hasta la espalda antes de golpear, con turno libre;
- el relevo (ESPERAR tras golpear) también se aplicaba con turno libre;
- **sin capitán**, además, el élite reta a **duelo** (idea 96): el 29 % de los ticks, 3 o más mobs esperando mirando.
  Con capitán no pasa (un capitán que manda a más de 3 no reta). El simulador no tiene duelos.

**El arreglo (`RuleBrain`): un turno libre se usa.** El rodeo, el flanqueo, la retirada por postura y el relevo solo se
aplican a un mob que **no** tiene turno libre (`hasTurn` = tiene uno o hay uno libre). Con turno libre, va al ataque.
Probado con `aFreeTurnIsUsedAgainstAPlayerOnTheMove`.

Por separado (24 peleas por modo, antes del arreglo) se midió qué más frena al grupo en el mod:
- **sin el aviso** de 8 ticks (`telegraph` apagado): sin capitán 133, capitán 204. **Es lo que más frena**: el mob se
  queda quieto 8 ticks y el golpe solo llega si el jugador sigue a ≤ 2,0 de centro a centro (`Reach.landing`); el
  jugador de guion se mueve 2/3 del tiempo a 0,15–0,18 por tick y se sale;
- **sin el empuje** de los golpes del jugador de guion (su golpe de 5 cada 16 ticks empuja al mob como en vanilla):
  sin capitán 63,5, capitán 94. El empuje saca al mob del alcance durante su aviso.
El aviso y el empuje son del juego (decisión de Andy: un golpe avisado que el jugador esquiva no llega), no se tocan.
**Pregunta para el simulador:** ¿su copia del jugador de guion empuja al mob al golpearlo, y el mob del simulador pierde
el golpe si el jugador se aleja durante el aviso? Si no, eso explica la mayor parte de 249 contra ~70.

**Un turno más con grupos grandes** (`iaTurnoGrupoGrande`, activado; `iaGrupoGrandeMin` = 6): con 6 o más mobs a por el
mismo jugador (la cuenta del `Squad`, con o sin capitán), `Aggression.maxAttackers` + 1. Prueba `aBigGroupGetsOneMoreTurn`.

**Medida tras el arreglo** (160 peleas por modo, dos tandas de 80; mismo guion; "muerto" = 20 de daño en 30 s):

| Modo | Turno de grupo grande | Daño/min | Muerto en 30 s | Tiempo a 20 | Turnos ocupados (de 2–3) |
|---|---|---|---|---|---|
| sin capitán (antes del arreglo, 80) | no | 37,5 ± 3,0 | 36/80 | 16,8 s | 0,25 |
| capitán sin órdenes (antes, 80) | no | 38,8 ± 2,7 | 36/80 | 14,9 s | 0,25 |
| capitán nuevo (antes, 80) | no | 77,7 ± 3,2 | 71/80 | 13,3 s | 0,47 |
| sin capitán | no | 67,9 ± 2,4 | 127/160 | 14,0 s | 0,40 |
| capitán sin órdenes | no | 68,9 ± 2,2 | 140/160 | 14,4 s | 0,42 |
| capitán nuevo | no | 105,2 ± 2,8 | 160/160 | 11,7 s | 0,56 |
| **sin capitán** | **sí** | **70,6 ± 2,6** | 129/160 | 13,2 s | 0,42 |
| **capitán sin órdenes** | **sí** | **72,9 ± 2,6** | 138/160 | 13,4 s | 0,43 |
| **capitán nuevo** | **sí** | **114,2 ± 3,0** | 159/160 | 11,1 s | 0,60 |

- El arreglo del anillo: sin capitán **+81 %** (37,5 → 67,9). El capitán nuevo también sube (77,7 → 105,2).
- El turno de grupo grande: capitán +9,0, sin capitán +2,7, sin órdenes +4,0 (todas a favor, la del capitán clara; el
  tiempo hasta 20 de daño baja en los tres). **Activado.**
- Capitán nuevo contra sin capitán, pareado: +37,3 ± 3,8 (gana 128/160) sin el turno de grupo; +43,6 ± 3,7 (131/160) con él.

**La pinza (regla 5), 80 peleas más:**

| Turno de grupo grande | Capitán con pinza (160) | Capitán sin pinza (80) | Pareado contra sin capitán, con / sin pinza |
|---|---|---|---|
| no | 105,2 ± 2,8 | 91,1 ± 3,1 | +37,3 / +29,5 |
| sí | 114,2 ± 3,0 | 100,4 ± 4,8 | +43,6 / +31,2 |

En el mod la pinza **gana** (+14 de daño por minuto en las dos tandas), al revés que en el simulador (−8 %): se queda
(`iaCapitanPinza`, activada; **apagada en la cuarta tanda**, ver abajo). Con el anillo arreglado, el jugador que se aleja deja atrás a los que no tienen turno, y la
pinza los manda por los lados en vez de detrás de él.

**Qué tambalea al jugador (para el simulador).** Los jugadores **no tienen barra de postura** (`Posture` los deja
fuera: la estamina hace ese papel), así que `Posture.isStaggered(jugador)` nunca era verdad y la regla 3 del capitán
no lo veía nunca. Ahora "tambaleándose" = lentitud II o más (`Captain.reeling`). En el juego eso lo ponen:
- la **parada con escudo** de un mob (el escudo subido hace ≤ `PARRY_TICKS`): lentitud II y debilidad I 30 ticks, y un
  empujón de 0,6 hacia atrás (+0,2 arriba); el mob queda con su contraataque listo (aviso de 4);
- la **embestida con agarre** del zombi (`LUNGE`): lentitud II `lungeGrabTicks` (30);
- la **carga del bruto** (`CHARGE`): lentitud III 40 ticks y empujón 1,2 (+0,45 arriba);
- el **empujón del escudo** del Molde Roto (`ThrownHead`): lentitud VI, debilidad III y fatiga III, 30 ticks.
Lo que no pone lentitud pero también castiga: el **golpe de escudo** (`ShieldPlay`, 4 ticks: empuje 0,8 + 0,15, −15 de
estamina, corta la carga), la estamina baja (< 25: un turno más en `Aggression`) y el golpe cansado. La aproximación del
simulador (lentitud + debilidad tras la parada de un mob) coincide con el caso principal; que añada la embestida, la
carga y el empujón del Molde si los tiene.

**Lo que el simulador tiene que copiar de esta tanda:** "un turno libre se usa" (rodeo, flanqueo, retirada por postura
y relevo solo sin turno libre), el turno de grupo grande (≥ 6 → +1) y "tambaleándose" = lentitud II o más.

### Tercera tanda (30-09, responde a `mod_spec_v4e.md`): qué pasa en cada pelea

`CapitanMedidaGameTests` apunta ahora, por pelea:
- los avisos (`CombatStats`, desde `MeleeAttackGoalMixin`): empezados, los que llegan, fintas, cortados antes de acabar
  (aturdido, meta parada u objetivo nuevo sin turno), fuera de alcance al acabar (**empujado**: le pegaron durante el
  aviso; **se movió**: si no), sin vista, y en alcance pero sin daño;
- las embestidas del zombi (`LUNGE`) empezadas y las que tocan;
- el daño que recibe el jugador por tipo (cuerpo a cuerpo, flecha, poción, fuego, otro), las flechas disparadas y las
  que cruzan su caja;
- la velocidad real de un zombi que persigue (a > 4, en el suelo, sin golpe reciente), andando y corriendo;
- la parte de los ticks "al ataque" (decisión ACERCARSE) sin ninguna meta que lo mueva.

**Fallo del banco de pruebas: las flechas no llegaban nunca.** El jugador de prueba (un FakePlayer) no estaba en el
nivel: las flechas lo atravesaban y las pociones lanzadas no le daban. Todas las tablas anteriores del mod (37,5 / 70,6 /
114,2…) son **sin flechas**. Ahora el jugador se añade al nivel (`addNewPlayer`); `FORJA_CAPITAN_JUGADOR=fuera` lo deja
fuera, como antes. Con el mismo código:

| Jugador de prueba (80–160 peleas, antes del arreglo de abajo) | sin capitán | sin órdenes | capitán de reglas |
|---|---|---|---|
| fuera del nivel (como hasta ahora) | 69,6 | 84,5 | 128,2 |
| en el nivel | 155,2 | 162,6 | 182,8 |
| en el nivel, sin empuje | 184,7 | 202,3 | 221,1 |

**Fallo del mod: el zombi se quedaba quieto con la decisión de atacar.** La meta cuerpo a cuerpo de vanilla solo se
plantea empezar una vez cada 20 ticks. Cada vez que el mob pasaba a una táctica (esperar, rodear) o su ruta se acababa
junto a un jugador que se mueve, la meta se paraba; al volver a ACERCARSE se quedaba **sin nada que lo moviera** hasta
20 ticks: el 12 % de sus ticks "al ataque". Ahora, para los mobs de reglas con la decisión ACERCARSE, se vuelve a
mirar cada 4 ticks (`MeleeAttackGoalMixin`). Esos ticks pasan al 2 %. Prueba `aZombieGoingInIsNeverLeftIdle`: el peor
tramo seguido baja de 14–26 ticks a ≤ 12.

**La tabla (160 peleas por modo, código final, jugador en el nivel, por pelea de 30 s salvo el daño por minuto):**

| Medida | sin capitán | sin órdenes | capitán de reglas |
|---|---|---|---|
| daño/min (vida que pierde) | **168,1 ± 3,5** | **176,7 ± 3,3** | **189,9 ± 3,8** |
| daño/min (suma de los golpes que se ven) | 134,8 | 142,3 | 166,9 |
| turnos ocupados (media por tick) | 0,47 | 0,48 | 0,66 |
| mobs a ≤ 3,5 | 1,23 | 1,37 | 2,11 |
| avisos empezados | 17,7 | 19,1 | 28,5 |
| — llegan | 6,2 | 6,7 | 10,3 |
| — fintas | 2,5 | 2,9 | 4,3 |
| — cortados antes de acabar | 2,8 | 3,1 | 2,7 |
| — fuera de alcance, empujado | 1,7 | 1,7 | 2,4 |
| — fuera de alcance, el jugador se movió | 1,8 | 1,8 | 3,3 |
| — sin vista | 0 | 0 | 0 |
| — en alcance y sin daño (sus i-frames) | 2,2 | 2,2 | 4,7 |
| embestidas empezadas / que tocan | 8,7 / 1,0 | 8,4 / 1,1 | 9,8 / 1,2 |
| daño cuerpo a cuerpo | 36,7 | 39,7 | 57,5 |
| flechas disparadas / que dan | 25,1 / 8,0 | 25,0 / 8,1 | 14,1 / 6,6 |
| daño de flecha | 30,7 | 31,5 | 25,9 |
| daño de pociones (sin mochila) | 0 | 0 | 0 |
| daño de fuego (con casco) | 0 | 0 | 0 |
| mobs muertos por el jugador | 2,66 | 3,04 | 3,17 |
| ticks "al ataque" sin nada que lo mueva | 2 % (antes 12 %) | 2 % (14 %) | 2 % (12 %) |

- **Zombi que persigue:** **0,098 bloques/tick andando** y **0,165–0,175 corriendo** (antes del arreglo, 0,085 andando:
  contaba los ticks parado). El simulador usa 0,135 andando: es un **38 % más rápido** que el mod. Estos ticks cuentan
  rodeos, esquinas y acelerones de la ruta, no solo la línea recta.
- **Con mochila** (`FORJA_CAPITAN_ABLACION=mochila`, `MobKit.roll` como al aparecer solo): daño de pociones **1,3–1,5 por
  pelea** (la del élite: 1 poción arrojadiza, 1 de cada 4 de daño y 1 de veneno; los normales solo llevan pan, y los
  veteranos un 15 % de las veces). El simulador da ≈ 6: su escenario lleva más pociones que el mod.
- **El empuje de los golpes del jugador de prueba** (antes de este arreglo): sin él, +19 % sin capitán, +24 % sin
  órdenes, +21 % con el capitán de reglas. En el simulador, −11 % y −17 %: pesa algo más en el mod.
- **Los turnos: por qué el mod los tiene la mitad.** Un turno se toma al empezar el aviso y se suelta al golpear
  (8 ticks + el peso), al fintar, al cortarse (aturdido, meta parada) o al acabar la embestida. Las **embestidas** también
  toman turno (`VanillaSpecials.LUNGE`) y lo tienen hasta ~25 ticks, pero **solo 1 de cada 8 toca** (1,0 de 8,7; en el
  simulador tocan 3,9 por pelea): el zombi salta a donde estaba el jugador y el de guion ya se ha movido. No he
  encontrado ningún turno que se suelte antes de tiempo por un fallo: se sueltan como están diseñados. Lo que más se
  pierde es el aviso (un 35 % llega) y la embestida (un 12 %).
- **Daño sin atribuir:** la vida que pierde el jugador supera en ~25–35 por minuto la suma de los golpes que ve
  `AFTER_DAMAGE`. No sé de dónde viene. Puede ser del propio banco (el jugador no hace su tick y la vida se le devuelve
  cada tick). La comparación entre modos no cambia.

**Para el simulador:**
- la velocidad del zombi al perseguir: 0,098 andando, 0,17 corriendo;
- las embestidas: 8–10 por pelea, 1 de cada 8 toca;
- las flechas: 8 de 25 dan, ~3,8 de daño cada una;
- los avisos: un 35 % llega;
- las pociones: solo el élite y algún veterano llevan una arrojadiza;
- la meta cuerpo a cuerpo mira cada 4 ticks si puede empezar (antes cada 20) para los mobs de reglas que van al ataque.

Con flechas y este arreglo, el mod (168 / 177 / 190) ya supera al simulador v4e (129 / 140 / 155).

### Cuarta tanda (30-09): el daño sin atribuir, los avisos cortados y la pinza

**1. El daño "sin atribuir" no era de ninguna fuente escondida.** Una prueba (`HealthDropMixin`) apunta cada bajada de
vida del jugador de prueba y quién la hace. **Todas** vienen de `hurtServer`:
- el golpe de un mob (`doHurtTarget`: el aviso, `VanillaSpecials.contact`, `TacticGoal`);
- una flecha (`AbstractArrow.onHitEntity`).

No hay fuego, sol, espinas, caída, veneno, asfixia ni daño del propio banco. La diferencia estaba en la cuenta: el
`damageTaken` del evento `AFTER_DAMAGE` de Fabric es la cifra que llega al paso de armadura de vanilla, **antes** de los
multiplicadores de Forja (`CombatHooks.afterArmor`) y del recorte de los i-frames. Ejemplos medidos:

| Golpe | El evento dice | Lo que pierde el jugador |
|---|---|---|
| flecha a la cabeza | 4 | 5,2 (×1,3) |
| flecha de un mob agresivo a la cabeza | 4 | 5,46 (×1,3 × 1,05) |
| golpe del élite | 8 | 10,4 (×1,3) |
| golpe de un mob agresivo | 2 / 3 | 2,1 / 3,15 (×1,05) |
| golpe con los i-frames aún altos | 3 | 1,05 (solo lo que pasa del último) |

Es daño real del juego. Ahora el banco reparte por tipo lo que de verdad baja la vida, y la suma coincide con el daño
por minuto.

**Para el simulador, el daño a un jugador:** base del mob × dificultad (HERRERO 1) × amenaza del mob (normal 1,
veterano 1,15, **élite 1,3**) × `Adaptive` (1 + 0,15·valor) × personalidad (1 + 0,05 por pelea hasta 6; rencor ×1,15;
agresivo ×1,05; en casa ×1,1; furia ×1,25) × equipo del jugador (`GearScore`) × **cabeza ×1,3** (golpe preciso: las
flechas) × aturdido ×1,25. Después, la armadura de Forja.

**2. Avisos cortados: de 2,8 a 0,8 por pelea (el simulador, 0,3).** Por qué se cortaban (40 peleas por modo, antes del
arreglo):

| Motivo del corte | sin capitán | sin órdenes | capitán de reglas |
|---|---|---|---|
| RODEAR a mitad de aviso (el mob "con turno" y lejos de su hueco) | 1,4 | 2,05 | 1,38 |
| RETIRARSE | 0,23 | 0,4 | 0,55 |
| ESPERAR | 0,33 | 0 | 0,1 |
| la meta se paró con la decisión ACERCARSE (un especial la quitaba) | 0,3 | 0,17 | 0,65 |
| aturdido | 0,3 | 0,38 | 0,28 |

**El arreglo** (regla de Andy: un golpe avisado es un compromiso que el jugador lee y contesta):
- mientras dura el aviso de la meta cuerpo a cuerpo (`MobMind.warning`), las reglas no cambian de táctica (`MobAi`);
- un especial no empieza a mitad de aviso (`SpecialGoal`).

Solo lo acaban un aturdimiento, la finta en su primera mitad, la muerte o perder el objetivo. Prueba
`aWarnedBlowIsSeenThrough`: un zombi al que se le manda huir en cuanto avisa ya no corta el aviso (sin el arreglo, lo
cortaba).

Tras el arreglo quedan 0,7–0,8 por pelea: aturdido 0,35–0,42, meta parada con ACERCARSE 0,3–0,38, sin objetivo 0,05.

**Antes y después** (160 peleas por modo, jugador en el mundo):

| | sin capitán | sin órdenes | capitán de reglas |
|---|---|---|---|
| avisos cortados, antes → después | 2,8 → 0,79 | 3,1 → 0,71 | 2,7 → 0,81 |
| avisos que llegan, antes → después | 6,2 → 6,1 | 6,7 → 7,2 | 10,3 → 10,7 |
| fuera de alcance al acabar (empujado + se movió), antes → después | 3,5 → 5,3 | 3,5 → 5,7 | 5,7 → 7,2 |
| daño/min, antes → después | 168,1 → 169,2 | 176,7 → 184,1 | 189,9 → 203,8 |

Los avisos que antes se cortaban ahora se terminan, pero la mayoría fallan: el jugador de prueba ya se ha ido o lo ha
empujado. Aun así el daño sube (+0,7 %, +4 %, +7 %).

**3. La pinza, otra vez** (160 peleas, jugador en el mundo, código final):

| | con pinza | sin pinza |
|---|---|---|
| capitán de reglas, daño/min | 203,8 ± 4,1 | 206,3 ± 3,8 |
| capitán contra sin capitán, pareado | +34,6 ± 4,7 (112/160) | +44,3 ± 4,6 (126/160) |

La ventaja de +14 de antes era con el jugador fuera del mundo (sin flechas). Ahora no gana, como en el simulador.
**`iaCapitanPinza` pasa a apagada.** La prueba de la pinza la enciende solo para ella.

**Tabla final** (160 peleas, código final, jugador en el mundo, pinza encendida en esta tanda; por pelea de 30 s, salvo
el daño por minuto):

| Medida | sin capitán | sin órdenes | capitán de reglas |
|---|---|---|---|
| daño/min | 169,2 | 184,1 | 203,8 (206,3 sin pinza) |
| turnos ocupados | 0,47 | 0,50 | 0,68 |
| avisos / llegan / fintas | 17,1 / 6,1 / 2,5 | 19,6 / 7,2 / 3,1 | 28,7 / 10,7 / 4,4 |
| cortados / empujado / se movió / sin daño | 0,79 / 2,6 / 2,7 / 2,0 | 0,71 / 3,0 / 2,7 / 2,3 | 0,81 / 3,3 / 3,9 / 4,9 |
| embestidas / tocan | 8,3 / 1,2 | 8,4 / 1,3 | 10,0 / 1,4 |
| daño cuerpo / flecha | 41,7 / 42,9 | 49,3 / 42,8 | 68,3 / 33,6 |
| flechas que dan (de las disparadas) | 8,2 / 24,8 | 8,3 / 24,9 | 7,0 / 14,2 |
| mobs muertos | 2,6 | 3,1 | 2,9 |
| zombi persiguiendo, andando / corriendo | 0,097 / 0,171 | 0,099 / 0,174 | 0,096 / 0,166 |

El daño de flecha sale ahora más alto que en la tercera tanda: es el mismo número de flechas, pero contado con los
multiplicadores de Forja (cabeza ×1,3).

### Quinta tanda (30-09): cómo empieza un aviso en el mod

#### Las reglas exactas

Hay tres caminos por los que empieza un golpe avisado. `CombatStats.warnStarted` / `warnEnded` los apunta en las pruebas.

**1. "vanilla": la meta cuerpo a cuerpo de vanilla con `MeleeAttackGoalMixin`.** Es el camino de casi todos los mobs de
reglas (zombi, husk, araña…).
- **La meta corre** cuando la decisión de las reglas es ACERCARSE. Vanilla solo mira si puede empezar cada 20 ticks; para
  los mobs de reglas con ACERCARSE, cada 4 (tercera tanda).
  - `canUse`: hace una ruta al objetivo; si no hay ruta, vale estar ya a su alcance.
  - La del zombi se para cuando su ruta se acaba (no sigue a quien no ve). Mientras dura un aviso, no se para.
- **Cada tick que corre**, `checkAndPerformAttack` → el aviso empieza si se cumple todo esto:
  - `canPerformAttack`: `ticksUntilNextAttack` ≤ 0, **a su alcance** y lo ve (`hasLineOfSight`).
    - A su alcance = la caja de ataque del mob corta la caja del jugador (`isWithinMeleeAttackRange`). La caja de ataque
      es la del mob ensanchada en horizontal lo que da un alcance de √2,04 ≈ 1,43 bloques. Con un arma que alarga
      (`Reach.actionExtra`), la de Forja: el alcance de su arma (`MobMixin`).
    - Para un zombi contra un jugador eso es un hueco entre cajas de hasta ~0,83, o ~1,4–1,7 de centro a centro según
      el ángulo.
  - **Un turno libre**: `AttackTokens.tryAcquire(jugador, mob, Aggression.maxAttackers(mob, jugador))`.
  - No estar aturdido, ni mirando un duelo.
- **El aviso:** `MobDefense.windup` = `windupTicks` 8 + el peso (min(14, round(1,5 × kg))); 4 si tiene el contraataque
  listo. Quieto, mirando.
  - Con la probabilidad de finta (`Aggression.feintChance`), es una finta: se corta a mitad del aviso, sin golpe.
- **Al acabar:** golpea si está a ≤ `Reach.landing` (2 × ancho del mob + ½ del jugador + 0,5 + `actionExtra`, de centro a
  centro, unos 2,0 para un zombi) y lo ve. Si le da, `HopBack.afterHit`: un salto atrás de 2,5 a lo sumo cada 120 ticks.
  Suelta el turno y `resetAttackCooldown`: `ticksUntilNextAttack` = 20 × (1 + 0,1 × kg). Tras una finta también.
  - Esa espera solo baja mientras la meta corre.
  - **Arreglado en esta tanda:** el `start()` de vanilla la ponía a 0 cada vez que la meta volvía a empezar, y 1 de cada
    5 avisos llegaba menos de 20 ticks después del golpe anterior del mismo mob. Ahora la espera que queda se conserva
    (`forja$keepTheWait`). Prueba `theWaitAfterABlowSurvivesARestart`.

**2. "tactica": `TacticGoal.strike`.** Es el "usar" de un mob con red, y el de las reglas cuando la decisión es una
táctica con `use` o el mob ha perdido al jugador.
- Empieza si: `mind.cooldown` ≤ 0, `Reach.reaches` (su alcance con arma) y un turno libre (`tryAcquire`).
- El aviso dura lo mismo (`MobDefense.windup`); la finta la decide la red.
- Al acabar, `mind.cooldown = Weight.interval(mob, MELEE_COOLDOWN 20)`; tras una finta, 10. `mind.cooldown` baja 1 por
  tick mientras corre `TacticGoal`.
- **Arreglado en esta tanda:** casi todos los avisos de este camino se cortaban. La meta se paraba en cuanto la decisión
  volvía a ACERCARSE o el mob volvía a ver al jugador. Ahora `TacticGoal` sigue mientras `mind.windup` > 0, y las reglas
  conservan su decisión mientras tanto (`MobAi`).

**3. "especial": `SpecialGoal` (prioridad 1) → `SpecialRunner.ruleStart`.** Cada tick, para cada especial listo (su
enfriamiento `readyAt` pasado y `canStart`), empieza con su `ruleChance`.
- Embestida del zombi (`lunge`, 0,3 por tick): aviso 12, enfriamiento 100–200; a 3,5–7 bloques (+ `actionExtra`), en el
  suelo, lo ve, no élite, sin lanza, y con turno libre (lo toma al avisar y lo suelta al acabar).
- Andanada (0,02), paso atrás (0,15), salto de araña (0,06)…
- No empieza a mitad de un aviso cuerpo a cuerpo (cuarta tanda).

Los arcos de los esqueletos no pasan por aquí: su meta de arco con `RangedBowAttackGoalMixin`.

#### Lo medido (160 peleas por modo, jugador en el mundo, código final de esta tanda)

| Medida (por pelea de 30 s) | sin capitán | sin órdenes | capitán de reglas |
|---|---|---|---|
| daño/min | 172,4 | 176,9 | 193,6 |
| avisos "vanilla" empezados | 16,1 | 16,7 | 23,7 |
| — que llegan / fallan / fintas / cortados | 6,3 / 5,8 / 2,8 / 0,7 | 6,5 / 6,3 / 2,7 / 0,6 | 9,5 / 9,2 / 3,5 / 0,8 |
| avisos "tactica" empezados (llegan / fallan) | 0,8 (0,4 / 0,4) | 1,0 (0,4 / 0,5) | 1,4 (0,5 / 0,9) |
| especiales: embestida / andanada / paso atrás / salto de araña | 8,3 / 3,1 / 0,2 / 0,8 | 8,4 / 3,0 / 0,2 / 0,8 | 9,4 / 1,6 / 4,2 / 0,8 |
| distancia al empezar, de centro a centro (media) | 1,62 | 1,62 | 1,57 |
| — < 1 / 1–1,25 / 1,25–1,5 / 1,5–1,75 / ≥ 1,75 | 9 / 6 / 28 / 20 / 37 % | 8 / 6 / 30 / 19 / 37 % | 10 / 7 / 32 / 18 / 32 % |
| hueco entre cajas al empezar (media) | 0,86 | 0,87 | 0,82 |
| ticks desde "a su alcance" hasta el aviso (media) | 3,9 | 3,5 | 4,7 |
| — 1–2 / 3–5 / 6–10 / 11–20 / 21+ ticks | 67 / 11 / 12 / 7 / 3 % | 70 / 11 / 11 / 6 / 3 % | 62 / 11 / 12 / 11 / 4 % |
| avisos "vanilla" sin estar a su alcance en el tick anterior | 1,8 | 2,2 | 3,4 |
| espera del mismo mob hasta su siguiente aviso tras uno que llega | 64,7 ticks | 66,4 | 58,2 |
| — tras uno que falla | 63,5 | 67,5 | 59,8 |
| — tras una finta | 66,5 | 66,1 | 54,7 |
| mobs avisando a la vez: 0 / 1 / 2 / 3 / 4+ (% de ticks) | 61 / 28 / 8 / 2 / 0,2 | 60 / 29 / 9 / 1,5 / 0,2 | 58 / 28 / 11 / 2,4 / 0,4 |
| turnos ocupados = máximo (% de ticks) | ~8 % | ~9 % | ~10 % |

**Por qué un mob a su alcance no está avisando** (% de sus ticks a su alcance; sin capitán / sin órdenes / capitán):
- avisando: 45 / 46 / 43 %;
- **esperando tras su golpe** (`ticksUntilNextAttack` > 0): 30 / 27 / 33 %;
- **sin la meta cuerpo a cuerpo con la decisión ACERCARSE**: 13 / 13 / 12 %. Su ruta se acabó junto al jugador, la meta
  se paró y aún no ha vuelto a mirar (cada 4 ticks);
- con otra táctica (rodear, esperar, retirarse, flanquear…): ~7 %;
- **turnos llenos: 0,3–0,8 %**. Los turnos casi nunca son el freno;
- aturdido ~1 %; otro ~4–5 %.

**Antes y después de los dos arreglos** (160 peleas; el empezar en el mismo tick que "a su alcance" sale como 1–2 ticks,
porque se mide una vez por tick):

| | sin capitán | sin órdenes | capitán de reglas |
|---|---|---|---|
| avisos "vanilla" empezados | 17,1 → 16,1 | 18,1 → 16,7 | 28,8 → 23,7 |
| avisos a < 20 ticks del golpe anterior del mismo mob | ~4 → 1,0 | ~4 → 1,0 | ~8 → 2,1 |
| avisos "tactica" cortados | ~1,5 → 0 | ~2,6 → 0 | ~2,9 → 0 |
| daño/min | 170,1 → 172,4 | 178,8 → 176,9 | 205,7 → 193,6 |

Los que siguen a < 20 ticks vienen de mezclar caminos: la espera de `TacticGoal` (`mind.cooldown`) y la de la meta
vanilla son distintas.

**Para el simulador:**
- el aviso empieza a 1–2 ticks de estar a su alcance (el 65 %), a 1,6 de centro a centro de media;
- entre dos avisos del mismo mob pasan ~60–65 ticks (20 de espera, más volver a su alcance, el salto atrás, las rutas);
- ~13 % del tiempo a su alcance está sin meta (la meta del zombi se para cuando acaba su ruta);
- los turnos casi nunca limitan (< 1 %).

Con la espera arreglada, el mod baja a 16,1 avisos por pelea sin capitán (el simulador, 12,8).

### Sexta tanda (30-09): la espera entre golpes

#### La regla exacta (código final de esta tanda)

- **Cuánto:** tras cada golpe avisado de cuerpo a cuerpo que **llega, falla o es finta**, el mob no puede empezar otro
  hasta `20 × (1 + 0,1 × kg)` ticks después. Es `resetAttackCooldown` con el peso (`Weight.INTERVAL_PER_KG`), igual por
  los dos caminos: `Weight.interval(mob, MELEE_COOLDOWN 20)` en `TacticGoal`, y 10 tras una finta de red.
- **Una sola espera, en tiempo de juego:** `MobMind.nextBlowAt` = el tick del golpe + la espera.
  - La miran los dos caminos antes de empezar un aviso: la meta cuerpo a cuerpo (`MeleeAttackGoalMixin`) y
    `TacticGoal.strike`.
  - **Corre siempre:** con la meta en marcha, parada, en otra táctica o en un especial.
  - La cuenta propia de la meta de vanilla (`ticksUntilNextAttack`) solo baja mientras la meta corre. Al volver a
    empezar, `forja$keepTheWait` la pone a lo que queda por el reloj, así que en la práctica también es tiempo de juego.
- **Nada la acorta ni la reinicia:**
  - el salto atrás (`HopBack`), los especiales (la embestida tiene su propio enfriamiento de 100–200 y su turno) y los
    turnos no la tocan;
  - el contraataque (aviso de 4 tras bloquear con escudo) solo acorta el **aviso**, no la espera;
  - un aviso **cortado** (aturdido, objetivo perdido) no pone espera.

#### El fallo arreglado: dos esperas

Antes de esta tanda cada camino tenía su propia cuenta: `ticksUntilNextAttack` en la meta de vanilla y `mind.cooldown`
en `TacticGoal`. Un golpe de uno no hacía esperar al otro. Un 11–17 % de los huecos entre dos avisos del mismo mob
duraban **menos de 20 ticks**: la mitad entre caminos distintos y la otra mitad entre dos avisos de vanilla (la meta
empezaba de nuevo tras un aviso de táctica). Ahora hay una sola espera (`nextBlowAt`) y no queda ninguno por debajo de
20. Prueba `oneWaitForBothPaths`: sin el arreglo, avisaba en el tick 5 con 60 de espera pendientes.

#### Lo medido (160 peleas por modo, jugador en el mundo; antes → después del arreglo)

| Medida | sin capitán | sin órdenes | capitán de reglas |
|---|---|---|---|
| daño/min | 165,3 → 166,3 | 172,9 → 173,3 | 197,3 → 188,2 |
| hueco medio entre avisos del mismo mob (ticks) | 70,7 → 71,0 | 64,7 → 68,7 | 57,1 → 59,9 |
| huecos < 20 ticks | 11 % → **0 %** | 14 % → **0 %** | 17 % → **0 %** |
| 20–29 / 30–39 / 40–59 | 32 / 15 / 16 % | 31 / 15 / 15 % | 39 / 14 / 15 % |
| 60–89 / 90–149 / 150+ | 12 / 14 / 12 % | 12 / 16 / 10 % | 10 / 14 / 8 % |
| hueco tras un golpe que llega / falla / finta | 69 / 72 / 73 | 71 / 69 / 64 | 60 / 62 / 55 |

Lo que sigue es del código final.

**El hueco según lo que hizo el mob entre medias** (sin capitán; entre paréntesis, la parte de los huecos):
- solo ir al ataque: **34 ticks** (16 %);
- otra táctica en algún momento (rodear, esperar, retirarse…): 62 (25 %);
- un salto atrás tras su golpe: 50 (37 %);
- un especial, casi siempre la embestida: 140 (23 %).

**Dónde se cuenta la espera** (por hueco, sin capitán):
- ticks con espera pendiente: 31,7. De ellos, 13,8 con la meta de vanilla en marcha y 17,1 con la meta parada; en ese
  rato su contador no baja, pero el reloj de `nextBlowAt` sí;
- `TacticGoal` contando su propia espera: 0,3;
- ticks "recortados" al volver a empezar la meta (el contador salta a lo que queda por el reloj): 6,1. **No es un
  atajo**: es la espera en tiempo de juego;
- acabada la espera, a su alcance y sin avisar: 1,4 ticks por hueco.

**A su alcance y sin avisar** (sin capitán): esperando tras su golpe 30,1 %, avisando 43,1 %, sin meta con ACERCARSE
13,2 %, el resto otras tácticas.

**Para el simulador:**
- la espera tras un golpe (llega, falla o finta) es de 20 × (1 + 0,1 × kg) ticks de juego y corre siempre, esté haciendo
  lo que esté;
- es una sola para los dos caminos, y ni el salto atrás, ni los especiales, ni los turnos la tocan;
- si en el simulador solo cuenta mientras la meta está en marcha, se alarga: la meta está parada ~17 de los ~32 ticks de
  espera. Eso explicaría su 47 % de "esperando" frente al 30 % del mod, y sus 85 ticks entre avisos frente a los 71.

## Capitán 2 (30-09; responde a `mod_spec_capitan2.md`)

Rama `forja-capitan2`. Las seis piezas del simulador (`capitan2.rs`, motor `py_mobs_v4j`) están en el mod con los
números de la especificación. **El contrato de los mobs (`red_mob_v4`) no cambia.** El del capitán tiene revisión 2
(`red_capitan_v4_contrato_v2.json`, 253 → 68) y la v1 (213 → 60) sigue cargando igual.

**Interruptores** (`CombatConfig`), todos **activados**, como propone la especificación (Andy puede apagar cualquiera):
`iaCapitanVision`, `iaCapitanSucesion`, `iaCapitanProteccion`, `iaCapitanOrdenes2` e `iaCapitanVisible`. Para las pruebas y
la medida hay además una forma de fijar las piezas por jugador (`CaptainBrain.overridePieces`), como `CaptainBrain.override`
fija el modo. **Ojo con la protección:** la medida de abajo dice que, contra el jugador de guion, el grupo hace un 35 %
menos de daño con ella (ver "La medida").

### 1. Visión compartida (`Captain.share`, cada pase del `Squad`, con capitán vivo, también interino)
- **Quién da:** de los miembros con `lastSeen` y que lo percibieron hace < 10 ticks (`now − perceivedAt < 10`), el que lo
  percibió más tarde. Da `lastSeen` y el tick en que lo percibió.
- **Quién recibe:** cada miembro que **no lo percibió en el tick anterior** (el pase del `Squad` va antes de que los mobs
  piensen en ese tick, así que "no lo percibe ahora" es `now − perceivedAt > 1`), a ≤ 32 del capitán en 3D, y solo si el
  tick del que da es más nuevo que su estimación (`max(lastSeenAt, lastHeardAt)`).
- **Qué recibe:** `lastHeard` = esa posición y `lastHeardAt` = ese tick, como un sonido oído. Si no tenía foto del jugador
  (`Perception.Snapshot`), una copia de la del que da (solo la tienen los mobs con red v4; los de reglas no guardan foto).
- **Efecto:** su estimación pasa a ser la del grupo, el perdido busca allí (su búsqueda empieza otra vez desde ella),
  el aburrimiento (600 ticks) se reinicia y la observación P lo ve como `obj_oido` = 1. No toca los `sonidoK_*`.
- `compartida` = cuántos la recibieron en el último pase.
- **Para el simulador:** en el mod, `lastSeenAt` se pone al tick en que el mob toma al jugador como objetivo aunque no lo
  vea (`MobAi.think`); un mob que acaba de tomarlo no acepta una foto de antes de ese tick.

### 2. Sucesión: el veterano interino (confirmada por Andy el 30-09)
- En el primer pase con `now − muerte del capitán ≥ 60`, si aún no hubo sucesión en esta pelea, manda **el veterano de más
  vida máxima** (amenaza VETERANO, no jefe), con `interino` = 1. Es la única excepción a "solo élites y campeones".
- **Decide solo en los pases con `now % 20 == 0`** (tiempo de juego). Entre medias, la orden sigue y los puestos y la
  protección se recolocan. Si su primer pase no es múltiplo de 20, hasta el siguiente no hay orden (la de la muerte:
  ninguna, libre).
- **No da CARGA:** la orden de las reglas con CARGA se convierte en "ninguna" (con la formación que digan las reglas y
  cuenta 0). Con red, CARGA y las 3 órdenes nuevas van a 0 en la máscara (también con una red v1).
- **Moral:** −0,15 mientras manda (`Captain.INTERIM_MORALE`), además del −0,4 de `sin_mando` durante los 200 ticks.
  **Consecuencia medida en la prueba:** con 1 baja en un grupo de 5, la moral queda en 1 − 0,16 − 0,4 − 0,15 = 0,29 < 0,3 y
  la regla 2 del capitán manda **RETIRADA**; hacen falta 8 en el grupo para que el interino pueda ordenar otra cosa. El
  simulador debería ver lo mismo.
- **Relevo:** a los 200 ticks de la muerte, un élite o campeón que quede releva al interino (`interino` = 0).
- **Muere el interino:** `sin_mando` y golpe de moral como con cualquier capitán; no hay segunda sucesión (aunque quede
  otro veterano).
- Los mobs ven `tengo_capitan` = 1 y el interino `soy_capitan` = 1; su estandarte es **plateado** en vez de dorado.

### 3. Protección del capitán (`Captain.protect`, cada pase después de colocar los puestos)
- La cabeza `proteccion` de una red v2: 0 = reglas, 1 = sin protección, 2 = retirarse ya. Sin red o con mando 0, reglas.
- **Retirarse** (vida < 35 % o la red pide 2): su punto a **14** del jugador en la dirección jugador → capitán (si están
  encima, la contraria a la mirada lenta) y **suelta su turno** en cada pase.
- **Detrás del frente** (él de cuerpo a cuerpo y ≥ **3** de los demás también): su punto a **7** del jugador en la dirección
  jugador → centro de todos los demás miembros (si cae encima, la contraria a la mirada lenta).
- "De cuerpo a cuerpo" (`Captain.melee`) = ni arquero ni de los que disparan o lanzan por naturaleza, como cuenta la regla
  de la CARGA; los creepers cuentan.
- Con punto, su puesto es `reserva`. **Cualquier cerebro** va a él como FORMACION (`TacticGoal`, como BUSCAR con el jugador
  perdido); las reglas deciden FORMACION. Excepción: "detrás" con turno propio (`holds`) y el jugador a < 3,5, pelea. Llegado
  a < 1, quieto mirando al jugador.
- **Escoltas** (jugador a < 10 del capitán): hasta 2 miembros de cuerpo a cuerpo que no tienen turno propio, primero los de
  escudo y luego los más cercanos al capitán; punto a 2 del capitán hacia el jugador y a ±1 de lado (el primero a la
  derecha, (−delante_z, delante_x)); puesto `frente`. **Las de reglas van a su punto (FORMACION) cuando no tienen turno en el
  sentido de siempre de las reglas: ni lo tienen ni hay uno libre.** Con un turno libre atacan ("un turno libre se usa").
  La primera versión las dejaba quietas aunque hubiera turno libre: 99,6 de daño por minuto frente a 119,6 (80 peleas).
- **Arreglo de paso en FORMACION:** una ruta termina a un bloque o así de su punto; el último tramo va recto
  (`MoveControl`), como ya hacía `toRing`. Antes el capitán se quedaba a 1,4 de su puesto y nunca contaba como llegado.
- **Visto en el mod:** con el grupo rodeando al jugador, el centro de los demás cae casi encima de él y la dirección
  "detrás" salta de un pase a otro: el capitán da vueltas a 4–5 del jugador en vez de quedarse a 7. En la medida, de media,
  está a 7,4 (4,6 sin protección).

### 4. Órdenes nuevas (solo una red v2 las da; las reglas y los miembros las obedecen)
Enum `Captain.Order` con 12 valores (los 9 de v1 primero). Los mobs las ven como una de las 9 (`Captain.seenAs`), en el
bloque M y en el bloque O de una red v1: CERRAR_SALIDAS → CERCAR, FOCO_HERIDO → NINGUNA, RETIRADA_FALSA → RETIRADA en la
fase 1 y CARGA en la fase 2 (con `cuenta_atras` 0).
- **CERRAR_SALIDAS:** la salida es la velocidad del jugador (`MobSprint.motion`) si pasa de 0,05 por tick, si no la dirección
  centro del grupo → jugador. Los de cuerpo a cuerpo (el capitán también, salvo que la protección le dé otro punto),
  ordenados por su ángulo respecto a la salida, van repartidos en ±60° a 5 del jugador (uno solo, justo en la salida).
  Los demás, a su punto de formación. Reglas: con turno (propio o libre), al ataque; sin turno, FORMACION, por el anillo si
  la línea recta pasa a < 3,5 del jugador.
- **FOCO_HERIDO:** en cada pase mientras dure, los miembros sin turno propio salvo el capitán cambian de objetivo al
  jugador en la pelea (vivo, ni creativo ni espectador) a ≤ 16 del capitán **con menos fracción de vida que el mío; el más
  herido** si hay varios. Los que quedan ven "ninguna".
- **RETIRADA_FALSA:** fase 1, puntos a **10** del jugador alejándose (la dirección jugador → mob), y las reglas van con
  **FORMACION** (no RETIRARSE: no cuenta para huir ni para abandonar). **Paso a la fase 2:** el mod lo mira **cada tick**
  (`Captain.tick`): a los 40 ticks de darla, o antes si el jugador avanza ≥ 4 bloques en la dirección jugador → centro del
  grupo **tal como era al darla**, medida desde donde estaba al darla. Entonces grito, `chargeAt` = ahora (cero_t), +1
  turno 40 ticks y los puntos se recolocan en el acto (en el jugador). Fase 2: reglas al ataque (con turno o sin él) y
  corriendo (`MobSprint`).
- **Una orden sigue** mientras orden, formación y sector no cambien (la regla de siempre): una red que da RETIRADA_FALSA con
  otra formación la vuelve a empezar desde la fase 1.
- **Máscaras** (con `iaCapitanOrdenes2` y nunca con un interino): CERRAR_SALIDAS con ≥ 2 de cuerpo a cuerpo (el capitán
  cuenta); FOCO_HERIDO con ese jugador más herido; RETIRADA_FALSA con ≥ 3 de cuerpo a cuerpo y moral del grupo ≥ 0,5.

### 5. Capitán visible
- Con cada orden nueva que no sea "ninguna y LIBRE" (ni CARGA, que ya grita a la mitad de la cuenta y en su 0): un grito
  (`VINDICATOR_CELEBRATE`, más agudo para el interino) y una nube del polvo del estandarte sobre su cabeza. Solo visual.
- El estandarte de siempre (polvo dorado cada 20 ticks), plateado para el interino. `/forja ia ver` dice "capitán
  interino", "tras el frente", "se retira" y "escolta".
- Captura: `FORJA_SOLO=capitan2 ./gradlew runClientGameTest` (`V4Footage.filmCaptain2`) →
  `E:\IA\Claude\Forja_capturas_mejoras\capitan2\capitan2_01_detras_con_escoltas.png`: el élite del hacha a 7,4 del
  jugador, apartado de la pelea, con su polvo dorado; las dos escoltas estaban usando turnos libres.

### 6. Contrato `red_capitan_v4` revisión 2 (`CaptainBrain`)
- **Carga:** `"contrato_version": 2` en el archivo (`NetBrain.contractVersion`) → 253 nombres (`CaptainBrain.namesV2`) y 68
  salidas; sin el campo o con 1 → los 213 y 60 de siempre. Otra versión se rechaza con su motivo en `/forja ia`.
- **Entradas 213–232:** la orden de `CaptainBrain.ruleOrder` = `Captain.rules()` ahora, con la CARGA de un interino como
  "ninguna". Con una red cargada se calcula siempre, diga lo que diga `iaCapitanReglas`.
- **233–252**, cómo las calcula el mod:
  - `capitan_tras_frente/8`: (distancia capitán–jugador − la del miembro de cuerpo a cuerpo más cercano al jugador, sin
    contar al capitán) / 8, ±2; 0 si no hay ninguno.
  - `escoltas/2`: miembros (sin el capitán) a < 3 de él, no los que tienen la marca de escolta.
  - `jug_mira_capitan`: sus ojos dentro del cono de 30° de la mirada del jugador (15° a cada lado) y línea libre de ojos a
    ojos. Sin condición de luz.
  - `capitan_golpeado/3`: golpes de un jugador (`AFTER_DAMAGE` con un jugador como causa y sin bloquear) en 100 ticks.
  - `jug_aleja/0.2`: `MobSprint.motion` del jugador proyectada en la dirección centro del grupo → jugador, / 0,2, ±2.
  - `salidas_libres/8`: de 8 direcciones (0°, 45°…) desde el jugador, el punto a 4 bloques cuenta si el bloque a sus pies
    + 1 y el de encima no tienen colisión (sube como mucho 1) y hay línea libre de sus ojos a ese punto a la altura de sus
    ojos. Una caída no cierra la salida.
  - `otro_*`: el otro jugador más cercano al capitán a ≤ 16 (vivo, ni creativo ni espectador); `otro_dist/16` desde el
    capitán. `mi_jug_vida_frac`: la del jugador del grupo.
  - `proteccion_*`: la cabeza en vigor (0 sin red o con mando 0).
- **Mando:** `logits[67] > logits[66]`, sin temperatura ni sorteo. Mando 0: la orden de las reglas tal cual (puestos por
  tipo) y protección 0; **no se sortea ninguna otra cabeza** (el azar del mob capitán no se toca, así que el grupo juega
  exactamente como con el capitán de reglas; prueba `theMandoHeadFollowsOrOverridesTheRules`). Mando 1: se sortean las 14
  cabezas con logits/T y su máscara, como en v1.
- **Cadencia:** cada pase (10 ticks), cada 20 con un interino. **Memoria:** a 0 al cambiar de capitán.

### Pruebas (`Capitan2GameTests`, 8 nuevas; 428 en total, todas en verde)
- `sharedVisionReachesTheBlind`: un zombi encerrado en piedra recibe la posición del grupo (`obj_oido`, estimación,
  `compartida`); sin la pieza, nadie.
- `aVeteranTakesCommandWithoutCharges`: muerto el élite, el veterano de 30 de vida manda a los 60–70 ticks, sus órdenes
  nuevas caen en pases múltiplos de 20, nunca CARGA aunque las reglas la quieran, moral −0,15 exacta, `capitan_interino` = 1;
  muerto él, no manda el otro veterano.
- `theCaptainStaysBehindWithEscorts`: la geometría exacta (7 hacia el centro de los demás, reserva; escoltas: el del escudo
  primero a la derecha, el más cercano después; 14 con < 35 % y suelta el turno), luego llega a su punto y se queda a 6–8;
  sin la pieza, nada.
- `v2NamesMatchTheContract`: los 253 nombres, las 68 salidas y las cabezas del JSON; las 253 entradas de un grupo real son
  finitas y los one-hot suman 1.
- `theMandoHeadFollowsOrOverridesTheRules` (entorno propio): una red v2 recién empezada da en cada pase la orden de las
  reglas exacta (con cargas incluidas) y `decide()` también; con mando 1 manda HOSTIGAR en CUÑA; una v1 sigue cargando y
  mandando; una v2 con los nombres de v1 se rechaza.
- `closingTheWaysOut`, `focusOnTheHurtOne`, `theFalseRetreatTurns` (entornos propios): cada orden nueva con sus puntos,
  su máscara, lo que ven los mobs y cómo obedecen las reglas (`RuleBrain.obey`).
- Las 4 del lote normal pasan 400 de 400 con `FORJA_VERIFICAR=1`.
- `theRulesChargeWhenTheBackIsTurned` (M5) apaga las piezas: prueba el capitán de reglas de antes, y con la protección el
  capitán y sus escoltas no entran en la carga.

### La medida (`CapitanMedidaGameTests`, 160 peleas por modo en dos tandas de 80, jugador en el mundo)
El grupo es el de siempre, con **uno de los zombis veterano en todos los modos** (para que pueda haber sucesión;
`FORJA_CAPITAN_VETERANO=no` lo quita). Modo nuevo **"reglas2"** (`measureRulesCaptain2`): el capitán de reglas con las cinco
piezas; los otros tres, sin ninguna. `FORJA_CAPITAN2_PIEZAS=vision,sucesion,…` elige las de "reglas2". La línea de cada
pelea apunta también `interino`, `protegido`, `escoltas`, `compartida`, `gritos`, `elite_muere` y `capitan_dist`.

| Medida (por pelea de 30 s salvo el daño) | sin capitán | sin órdenes | capitán de reglas | capitán de reglas 2 |
|---|---|---|---|---|
| daño/min | 170,3 ± 3,5 | 177,2 ± 3,1 | **188,2 ± 3,8** | **122,5 ± 2,0** |
| "muerto" (20 de daño) en 30 s | 160/160 | 160/160 | 160/160 | 160/160 |
| tiempo hasta 20 de daño | 8,7 s | 8,0 s | 7,9 s | 10,6 s |
| mobs muertos | 2,71 | 3,20 | 3,16 | 3,29 |
| el élite muere | 98/160 | 71/160 | 19/160 | **0/160** |
| daño cuerpo a cuerpo / flecha | 43,0 / 42,2 | 46,0 / 42,5 | 61,9 / 32,2 | 24,2 / 37,0 |
| avisos / llegan | 15,4 / 6,2 | 16,8 / 6,7 | 24,0 / 9,6 | 15,7 / 5,9 |
| capitán a … del jugador (media) | — | 4,8 | 4,6 | 7,4 |
| órdenes | — | — | CARGA 21 % | CARGA 17 % |
| ticks con el capitán protegido / escoltas × ticks | — | — | — | 486 / 947 |

Pareado por semilla: capitán de reglas − sin capitán **+17,9 ± 5,0** (gana 107/160); capitán 2 − sin capitán **−47,7 ± 4,1**
(gana 33/160); **capitán 2 − capitán de reglas −65,7 ± 3,6** (gana 15/160).

**Cada pieza** (80 peleas, "reglas2" solo):

| Piezas | daño/min |
|---|---|
| todas menos la protección (visión, sucesión, órdenes, visible) | 193,5 ± 4,5 (como el capitán de reglas, 188,2) |
| solo la protección | 127,4 ± 4,5 |

- **La protección se lleva todo el efecto:** el élite (el que más pega: espada de hierro, ×1,3) se queda a 7 y casi nunca
  golpea, y con el jugador a < 10 del capitán (casi siempre) dos zombis más quedan de escolta cuando no hay turno libre. El
  daño cuerpo a cuerpo cae de 62 a 24. A cambio, el élite ya no muere nunca (19/160 → 0/160). Contra el jugador de guion,
  que pega al más cercano, proteger al capitán no compra nada; el simulador la justifica contra un jugador que caza
  capitanes (`mod_spec_capitan2.md` §5), y ese jugador el mod no lo tiene.
- **La visión, la sucesión, las órdenes y el visible no cambian nada aquí:** de día todos ven al jugador (0,4 estimaciones
  compartidas por pelea), el élite solo muere en 9 de 80 peleas y casi siempre al final (tick ≥ 497), así que el interino
  manda 0,35 ticks por pelea, y las reglas no dan las órdenes nuevas.
- **Decisión pendiente de Andy:** `iaCapitanProteccion` va activada porque así lo pide la especificación. Con estos números
  convendría apagarla hasta que haya un jugador de prueba que cace capitanes, o que la red v2 aprenda cuándo usarla (su
  cabeza `proteccion` = 1 la quita).

### Lo que el simulador tiene que copiar
1. "Sin turno" para las escoltas de reglas = ni lo tienen ni hay uno libre (con turno libre atacan).
2. RETIRADA_FALSA pasa a la fase 2 mirando cada tick, con el avance medido en la dirección jugador → centro del grupo del
   momento en que se dio; al pasar, los puntos se recolocan en el acto.
3. La visión compartida: "no lo percibe" = no lo percibió en el tick anterior, y la estimación de un mob que acaba de tomar
   al jugador empieza en ese tick.
4. Con el interino y una baja en un grupo de 5, la moral cae por debajo de 0,3 y las reglas mandan RETIRADA.
5. Las definiciones de las entradas 233–252 de arriba (sobre todo `salidas_libres`, `jug_mira_capitan`, `escoltas/2` y
   `capitan_tras_frente/8`).
6. El dato de la medida: con el jugador de guion, la protección cuesta un 35 % del daño del grupo.

## M6: el vector de mundo W (452–467, `WorldMemory`)

- **Dónde:** un adjunto del jugador (se guarda con él y pasa la muerte), un vector por dimensión. `/forja ia mundo`
  (o `ver`) lo enseña y `/forja ia mundo borrar` lo borra en la dimensión.
- **Valores iniciales** (y a los que vuelve al olvidar): las 8 causas a **1/8** cada una (suman 1, como pide el
  diseño; `red_mob_v4_neutros.md` lo dejaba abierto), todo lo demás a 0, confianza incluida.
- **Causa de cada muerte** de un mob que pelea con el jugador (su objetivo es él, o lo mató él), en este orden:
  1. **trampa**: lava, suelo caliente, caída, ahogarse, asfixia en un bloque, cactus, vacío o arbusto de bayas;
  2. **fuego**: cualquier daño de fuego, o una explosión que provocó el jugador;
  3. **otra**: lo mató otra cosa que no es el jugador;
  4. **altura**: el jugador estaba arriba (sobre el suelo ≥ 2);
  5. **flecha**: un proyectil del jugador;
  6. **área**: un golpe del jugador con un arma de área (especial de área o mejora que golpea en área);
  7. **estrecho**: el jugador en un pasillo o una puerta (bloque E);
  8. **abierto**: el resto de golpes del jugador.
  Cada causa: `v ← v + 0,1·(onehot − v)`.
- **Lo que funciona:** al morir un mob (o al abandonar la pelea), el daño que hizo al jugador en su vida / 10 (tope 2)
  va a la media de la forma en que más peleó, contada por decisiones: **frente** (ACERCARSE, RODEAR, FORMACION,
  CUBRIRSE, LIBRE), **flanco** (FLANQUEAR, SECTOR), **distancia** (toda la vida de un arquero, TIRO_LIBRE),
  **emboscada** (EMBOSCAR, OCULTARSE), **asedio** (ASEDIAR). `v ← v + 0,1·(daño/10 − v)`; una vida sin ninguna de
  esas no cuenta.
- **Estilo del jugador**, al acabar cada pelea (su grupo lleva 60 ticks sin nadie): `mundo_jug_pilar` = se subió
  (arriba ≥ 40 ticks seguidos); `mundo_jug_huye` = estuvo a más de 24 de todo el grupo, o nadie lo percibió durante
  200 ticks con la pelea ya empezada hace 200. Media 0,1 de 0/1.
- **Confianza:** muertes registradas / 50, tope 1.
- **Olvido:** por cada día de juego (reloj del mundo / 24 000) que pasa, cada valor se acerca un 10 % a su valor inicial:
  `v ← inicial + 0,9^días·(v − inicial)`; el contador de muertes también se multiplica por 0,9^días.
- La red del capitán ve el mismo vector (su bloque W).

## v4c (30-09): lo que `mod_spec_v4c.md` pedía comprobar

1. **Cambio de familia a mitad de pelea.** Ya cambiaba de red y ponía la memoria (GRU) a 0 cuando `MobAi.familyOf`
   cambiaba por el arma de la mano, pero solo si la familia nueva tenía red. Arreglado: la familia se mira en cada
   decisión, piense con red o con reglas. Un esqueleto con red de arquero que saca la hoja y no hay red de cuerpo pelea
   por reglas; al volver al arco, su memoria empieza de 0 (antes recuperaba la de antes de la hoja). Prueba
   `aChangeOfFamilySwitchesNetworkAndMemory`.
   **Fallo encontrado de paso:** un esqueleto que cambiaba el arco por la hoja durante el aviso de la andanada, del salto
   atrás con tiro o de la flecha de empuje **tumbaba el servidor** ("Invalid weapon firing an arrow"). Ahora el tiro se
   pierde si ya no tiene arco (o ballesta, la flecha de empuje).
2. **El repuesto puede ser un arco.** Ya lo era: el cambio de arma (objeto 8) intercambia mano y repuesto con cualquier
   arma, un arco también, y la máscara se abre con una hoja o un arco (no con una caña). El arco del repuesto se suelta
   al morir: el recogido siempre, el de aparecer con la probabilidad de vanilla. Prueba `aBowInTheSpareSlot`.
3. **Arcos en el suelo solo para tiradores.** No se cumplía del todo: `GroundItems.valueFor` daba valor a un arco a
   cualquier `RangedAttackMob` con la mano vacía (un **ahogado** lo habría cogido), y el recoger de vanilla (un zombi que
   puede recoger botín) cogía arcos. Ahora (`GroundItems.canShoot`):
   - un **arco** solo vale para esqueleto, stray y bogged;
   - una **ballesta** solo para saqueador y piglin (un esqueleto con ballesta pelearía con ella de palo, y un saqueador
     no dispara arcos);
   - a los demás, 0, con la mano vacía o no;
   - el recoger de vanilla deja los arcos en el suelo para quien no los puede usar (`MobMixin`).
   Un zombi que coge un arco y pasa a arquero sigue sin existir (decisión de Andy pendiente). Prueba
   `onlyShootersTakeUpBows`.
   **El simulador tiene que copiar** el cambio de la ballesta: `mod_spec_v4c.md` decía que un arco o una ballesta
   valían para un esqueleto o un stray; ahora la ballesta, solo para saqueador y piglin.

El resto de `mod_spec_v4c.md` no pide nada nuevo al mod. `red_capitan.json` se carga desde
`config/forja/redes_v4/red_capitan.json` como dice, y manda aunque `iaCapitanReglas` esté apagado (prueba
`aCaptainNetworkGivesTheOrders`).

## Escalera de dificultad (30-09): el simulador copia Extremo

Desde la escalera (`difficulty/Ladder.java`, la tabla entera está ahí y en el libro II) cada nivel de Minecraft enciende
sus sistemas. Las redes v4 y la red del capitán **solo corren en Extremo**, así que el simulador copia Extremo:

| | Difícil | **Extremo** (lo que copia el simulador) |
|---|---|---|
| redes | v3 (`redes`) | v4 (`redes_v4`) donde la haya; si no, la v3 |
| capitán | de reglas | `red_capitan.json` si está; si no, el de reglas |
| capitán 2 (visión, sucesión, protección, órdenes 2, visible) | no | sí |
| reglas, avisos, turnos, anillo, penetración y presión, escalado por equipo | sí | sí |
| cifras (`ForjaDifficulty`) | HERRERO | **MAESTRO** |
| carrera, factor f del bono | 0,6 | **0,9** |

**La fórmula exacta de la carrera** (`MobSprint.runMultiplier`, `Ladder.sprintMultiplier`): solo se recorta el bono, la
velocidad al andar no cambia.

```
correr  = base × (1 + 0,35 × f)          Extremo: base × 1,315   (Difícil ×1,21; Normal y Fácil ×1,14)
cerco   = base × (1 + (2,3 − 1) × f)     Extremo: base × 2,17    (el modo rodeo, rodeoSpeed 2,3)
```

En el juego es el sprint de vanilla (modificador ×1,3, `ADD_MULTIPLIED_TOTAL` 0,3) por el modificador `forja:carrera`
(`ADD_MULTIPLIED_TOTAL`) de valor `correr / 1,3 − 1`: en Extremo +0,011538 (cerco +0,669231); en Difícil es negativo,
−0,069231, porque +21 % queda por debajo del +30 % del sprint de vanilla. El aliento no cambia: 100, gasta 2 por tick
(1,4 en el cerco), recupera 1 por tick tras 20 sin correr y vuelve a correr desde 25. Solo en Fácil los fuertes
(veteranos, élites, campeones, monstruos de Forja) tienen el 70 %: 70 de aliento y el 70 % de la barra de postura.

**Las cifras de Extremo (MAESTRO)**: en la observación `dif_maestro` = 1 (y los otros tres `dif_*` a 0); vida ×1,3, daño a
los jugadores ×1,25, postura ×1,2, probabilidad de veterano y élite ×1,5, tope por golpe ×0,85, temperatura de las redes
×0,8, botín ×1,3 y amago mínimo 0,15. En la fórmula del daño de la cuarta tanda, «dificultad (HERRERO 1)» pasa a ser
**1,25**.

Ojo con las medidas anteriores de este documento: se hicieron con HERRERO y la carrera a ×1,35. Las pruebas del servidor
siguen así salvo la carrera: corren con `nivel` = EXTREMO y `dificultad` = HERRERO forzados (`TestDefaults`), o sea,
todos los sistemas de Extremo con las cifras de Herrero y la carrera a ×1,315.

## Rendimiento

`RedV4PerfGameTests` (entorno propio, corre solo, chunks forzados): 30 mobs mezclados (zombis, algunos con escudo y
mochila, esqueletos, arañas, creepers) con redes v4 del tamaño del contrato (468 → 128 → 128 → GRU 96 → 53) y pesos al
azar, antorchas y objetos en el suelo, el jugador dando vueltas: **1,5–2,2 ms/tick** de IA en total (MobAi.tick con
observación, máscara y red ≈ 1,2–1,6; las metas que ejecutan ≈ 0,35–0,55); tope 2,5. La base v3b (`ObsForja.full`)
sigue siendo lo más caro de la observación (≈ 0,5–0,6 ms); la red ≈ 0,2 ms. Las mismas 30 por reglas: 0,2–0,5 ms/tick.
Con redes entrenadas (que no cambian de táctica cada 2 ticks como las de pesos al azar) el ejecutor pedirá menos rutas.

## Pendiente

- **M7** del diseño: grabación JSONL v4 (con la línea del capitán) y `RENDIMIENTO.md` medido con `tools/rendimiento.sh`.
- El simulador: copiar lo de este documento (S4–S7) y entrenar; la red del capitán (`red_capitan_v4_contrato.json`) no
  existe aún: los grupos usan el capitán de reglas.
- Neutros que el mod ya fija y el simulador debe copiar: `orden_ninguna` = 1 y `formacion_libre` = 1 sin capitán;
  `bloqueo_hace/20` = 1 sin bloqueo; `jug_borde/2` = 2 sin borde; causas de muerte a 1/8; `obj_edad` y `ultima_edad` a
  2 si nunca lo vio; `perla_destino_ok` y `lanzamiento_ok` a 0 sin nada que usar.

## Capturas

`FORJA_SOLO=v4 ./gradlew runClientGameTest` (`V4Footage`) y `python tools/hoja_v4.py`: hoja en
`E:/IA/Claude/Forja_capturas_mejoras/v4_mod/hoja_v4.png` (escudo, recoger, perla, antorcha, pilar, emboscada y capitán).
