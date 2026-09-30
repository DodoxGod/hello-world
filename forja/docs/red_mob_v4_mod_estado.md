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

## Entradas vivas (M2/M3)

Marco de siempre: "delante" = del mob al jugador en el plano, "derecha" = (−delante_z, delante_x).

### E (solo 380)
- `jug_luz/15`: `getMaxLocalRawBrightness` en el bloque de los pies del jugador (cielo con su oscurecimiento y
  bloques), /15. El resto de E sigue a 0 (llega en M4).

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

Siguen cerradas: FORMACION (36), EMBOSCAR (37), BUSCAR (38) y furia (51); llegan en M4 y M5. Si una red las eligiera
sin máscara, FORMACION se ejecuta como RODEAR, EMBOSCAR como OCULTARSE y BUSCAR como LIBRE.

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
8. **Perla:** 2 de daño al mob al llegar. Tiro parabólico a 45° (60° si el destino está casi tan alto como lejos),
   v² = g·x² / (2·cos²θ·(x·tanθ − dy)) con g = 0,03 y +8 % por el rozamiento.

## Rendimiento

`RedV4PerfGameTests`: 30 mobs mezclados con redes v4 del tamaño del contrato (468 → 128 → 128 → GRU 96 → 53), antorchas
y objetos en el suelo: **1,25–1,42 ms/tick** de IA en total (MobAi.tick con observación, máscara y red, más las metas
que ejecutan); tope 2,5. La observación es la mayor parte (≈ 0,9 ms); la red ≈ 0,21 ms.
