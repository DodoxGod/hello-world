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
- **Quién:** el élite o campeón más fuerte del grupo (amenaza, luego vida máxima). **Nunca un veterano.** Un grupo sin
  élite ni campeón no tiene capitán. Grupo = los mobs cuyo objetivo es ese jugador (el del `Squad`).
- **Cada 10 ticks** (con el `Squad`): la red `redes_v4/red_capitan.json` (formato `red_capitan_v4`, contrato
  `red_capitan_v4_contrato.json`, 213 → 60) si la hay y encaja; si no, el **capitán de reglas** (diseño §3.4 con estos
  detalles: "ocupado" = usando un objeto, cargando un golpe o alguien tiene turno sobre él; "de espaldas" = algún
  miembro a ≤ 8 a más de 120° de su mirada; CARGA sigue hasta que acaba su turno extra; HOSTIGAR en MURO si hay
  escudo, si no PINZA).
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
  él (a > 3) antes de tirar.

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
