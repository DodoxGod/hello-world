# Castillo — por dónde seguir (ESTE ARCHIVO MANDA sobre cualquier mensaje programado)

Escrito el 2026-09-20 a las 03:00, justo antes de quedarme sin créditos. Andy: «continúa cuando tengas
créditos» (se renuevan a las 5:50; hay un aviso programado en la sesión a las 5:53).

## Lo que Andy ha decidido ya (palabras suyas, 2026-09-20)

> «el estilo quiero que sea estilo **oscuro, casi tétrico, pero con estilo de forja**, pero tiene que tener
> estilo, **no puede estar en el mejor estado, pero debe estar bien, algunas zonas mejor que otras**,
> claramente el castillo no quedaría bien en todos los biomas, por lo que debes pensar en cuáles sí
> agregarlo y **en cuáles sería mejor dejar solo el castillo pequeño**… quiero que esta estructura sea
> **increíble, súper bien hecha, con detalle, cuidada, como si fuera de Minecraft vanilla**.»

Consecuencias:

1. **Ya no hay tres paletas que enseñar**: es la oscura. No hacer la muestra A/B/C; hacer una muestra de
   la paleta oscura (un lienzo de muralla con torre, y un interior) para afinarla con él viendo bloques.
2. **El castillo pequeño (`forja:castillo_de_forja`, 21 × 21) SE QUEDA.** El Bastión es una estructura
   NUEVA con id propio (`forja:bastion_del_gremio`), no lo sustituye. (El plano v3 y PROPUESTA.md decían
   «sustituye conservando el id»: eso queda anulado.) La Sala de cuños del Bastión es una versión grande
   de la del castillito, con los mismos tres sellos y los mismos guardianes.
3. Plano aprobado en lo general: v1 «me parece bien», luego pidió más tamaño (v2) y más salas y todos
   los pisos (v3). **El vigente es `plano_v3_completo.png` / `plano_castillo_v3.py` (82 espacios).**
   No ha contestado aún al v3: si pide cambios, se cambia el script y se vuelve a dibujar.
4. Ritual del jefe en la Fragua Profunda y generación «muy rara + mapa»: no ha dicho nada; seguir con lo
   recomendado (sí, y sí).

## Biomas (lo que pensé; enseñárselo y ajustar)

El Bastión necesita 201 × 201 de terreno razonablemente llano y un paisaje que le siente a una fortaleza
negra:

- **Sí:** `dark_forest` (el tétrico por definición; el patio va empedrado, los árboles no nacen en
  piedra), `taiga`, `old_growth_pine_taiga`, `old_growth_spruce_taiga`, `snowy_taiga`, `snowy_plains`
  (negro sobre blanco: el más vistoso), `swamp` (lúgubre y llano), `badlands` (meseta naranja: es el color
  de la forja) y `plains` (para que exista de verdad alguno que encontrar).
- **No (sólo el castillo pequeño, como hasta ahora):** colinas y montañas (`#is_hill`, `#is_mountain`,
  `stony_peaks`, `savanna_plateau`, `windswept_savanna`): ahí no cabe una planta de 201.
- **Ni uno ni otro:** praderas floridas, cerezos, jungla, playa, océano, desierto, champiñón: el ánimo
  no pega.
- Que no salgan pegados: `exclusion_zone` del conjunto del castillito respecto al del Bastión.
- Rareza: `spacing` ~140, `separation` ~60 (mucho más raro que el castillito: 84/26). Mapa del Forjador
  de nivel maestro (etiqueta de destino propia, como las demás estructuras — ver `check_structure_tags`).

## Paleta oscura «de forja» (punto de partida)

- Muros: degradado por altura y con ruido AGRUPADO (no aleatorio puro): `cobbled_deepslate` en el zócalo
  → `deepslate_bricks` → `deepslate_tiles` arriba; `cracked_*` donde hay ruina; `polished_blackstone_bricks`
  y `blackstone` en la torre del homenaje y la fundición; columnas de `polished_basalt`; bandas de
  `chiseled_deepslate`; `tuff_bricks` como gris claro envejecido para que no sea una masa negra.
- Forja: tejados y agujas de **cobre oxidado** (verde sobre negro; la pátina es un sistema del mod),
  `iron_bars`, cadenas, yunques, **lava y magma tras rejas**, vidrieras `orange`/`red_stained_glass`
  («color brasa»), faroles de pavesa, `soul_lantern` sólo en cripta/osario/forja de almas, y **chimeneas
  que humean** (hoguera bajo el tiro: la columna de humo se ve de lejos y dice «forja»).
- Madera: `dark_oak` y `spruce`; `mangrove` como rojo brasa en detalles.
- Estado: torre del homenaje y fundición bien conservadas (las mantienen los autómatas); ala oeste,
  capilla (media bóveda caída) y recinto bajo, peor; Torre Hundida sin tejado; brecha en la muralla este;
  telarañas, almenas que faltan, vigas caídas. Nada de musgo alegre: hollín (`coal_block`, `black_concrete_powder`).
- Variación por generación como en vanilla: `processor_list` propia (p. ej. 12 % de `deepslate_bricks` →
  `cracked_deepslate_bricks`), igual que los bastiones y la ciudad antigua.

## Oficio «como vanilla» (checklist para cada pieza)

Muros con relieve (contrafuertes, zócalo en talud, matacanes con escaleras invertidas, saeteras con
escalera/losa); ninguna superficie lisa de más de 5 bloques; cornisa en cada forjado; arcos con
escaleras; tejados con alero; vigas en los techos; suelos con dibujo; alturas de sala variadas; muebles
con trucos vanilla (trampillas, losas, carteles, marcos, **soportes con equipo forjado del mod**);
iluminación escasa pero suficiente para no generar monstruos donde no toca; cofres con tabla de botín
propia por zona.

## Plan técnico

1. `tools/castillo.py` (nuevo; lo llama `generate_assets.py`, que ya tiene `write_structure_nbt`,
   `nbt_bytes`, las clases `Nbt*` y `write_json`): un lienzo de vóxeles de todo el castillo en
   coordenadas de plano (x este, z sur, y=0 el patio), construido sala a sala con ayudantes (muro,
   arco, almena, torre, tejado, escalera, mueble), y un **troceador automático** en piezas de jigsaw de
   ≤ 48³ con un bloque jigsaw en cada cara compartida y un pool de UN solo elemento por conector
   (disposición fija). `max_distance_from_center: 128`, `size` ≥ 7, `terrain_adaptation: beard_thin`,
   inicio en la pieza central a `WORLD_SURFACE_WG`.
2. Un render isométrico propio y rápido (PIL, color medio de cada textura vanilla del jar de Loom) para
   iterar masas sin abrir el juego; el juego, para la verdad (`FORJA_SOLO=castillo` nuevo en el test:
   `place structure`, hora fija, capturas desde puntos fijos por fuera y sala a sala).
3. Fases, enseñando capturas de cada una: (1) muestra de paleta → (2) murallas, torres, barbacanas y
   foso → (3) torre del homenaje por fuera → (4) alas del recinto alto → (5) recinto bajo → (6) sótanos
   → (7) interiores sala a sala → (8) criaturas, botín, mapa, libro, logro, README/CHANGELOG/memoria.
4. Copia de seguridad antes de empezar: `E:\\IA\\Claude\\Forja_copia_2026-09-20_antes_castillo`.

## Estado de la obra (2026-09-20, mediodía)

Andy dijo «Está bien, inicia» al plano v3 y a lo anterior. Hecho hasta ahora:

- Copia de seguridad `E:\IA\Claude\Forja_copia_2026-09-20_antes_castillo`.
- **`tools/castillo.py`** (herramientas y piezas: `Canvas`, ruido agrupado con `noise_origin`, paleta `masonry`,
  `curtain_wall`, `round_tower`, `building`, `blit` con giro, `resolve` (brazos de muros/barrotes/vallas y
  esquinas de escaleras: una pieza de jigsaw se coloca TAL CUAL está escrita), `validate` (cada bloque y
  propiedad contra los blockstates del jar del cliente), `render` (oblicuo, sólo PIL: numpy no está
  instalado), `cut` (troceado en piezas de ≤ 48 y árbol de uniones)) y **`tools/castillo_obra.py`** (la obra:
  suelo, foso, murallas, torres, barbacanas, puente, torre del homenaje, alas, casas del recinto bajo).
  Los llama `generate_assets.py` (que ahora tarda ~35 s). Renders de trabajo en `docs/castillo/render_*.png`.
- Fase 1 (muestra de paleta, `docs/castillo/muestra_01.png`) y fase 2 en masa (`fase2_render.png`) enviadas.
- Test: `FORJA_SOLO=castillo` (`filmBastion` + `filmWholeBastion`), capturas `forja_50_*` y `forja_51_*`.

**Lo que costó averiguar del jigsaw (no volver a tropezar):**
1. `start_height` = base de la pieza central **+ 1**: el juego baja la pieza inicial un bloque («ground level delta»).
2. Una pieza que asome por debajo del fondo del mundo **no se coloca**, y el mundo plano del test sólo tiene
   3 bloques de fondo: por eso hay una segunda estructura `forja:bastion_prueba`, recortada en y ≥ −3, que es la
   que planta el test. Los sótanos habrá que fotografiarlos colocando sus piezas en alto con `place template`.
3. **Dos uniones no pueden caer en el mismo bloque** (las de dos caras vecinas elegían la misma esquina y una
   pisaba a la otra: faltaba medio castillo). Ahora cada unión va al centro de su cara y hay un conjunto `taken`.
4. `/place structure` empieza en la esquina del chunk y gira el castillo al azar: la pieza central lleva una
   **piedra imán** dos bloques al lado de su esquina (bajo el pavimento) y el test deduce el giro; las cámaras
   se dan en coordenadas del plano (`bastionToWorld`), con `BastionLayout.java` generado por el script.
5. A distancia de dibujado 24 el mundo del test no termina nunca; con 16 y `waitForChunksRender(900)`
   (capturando `AssertionError`) sí.
6. Piezas que sólo comparten aire (puente con un tramo caído) se unen por un bloque de pizarra enterrado.

**Tarde del 2026-09-20 — dónde está la obra.** Hecho y fotografiado en el juego: murallas, 11 torres, 2
barbacanas, foso, puente, torre del homenaje con observatorio, alas, casas del recinto bajo
(`castillo_obra.py`); liza, tiro, cantera, aserradero, huerto, cementerio con panteón, nevero, campamento,
lonja, monumento y pozo (`castillo_bajo.py`); los dos sótanos con la Fragua Profunda y su estrella de
canales de colada (`castillo_sotanos.py`); primeros interiores: gran salón, Sala de cuños (el Guardián sale
«sellado»: la mecánica de los tres sellos funciona tal cual con mis pilares), fundición y taller
(`castillo_interiores.py`). Entidades: `Canvas.entities` + `mob()`; `cut()` las reparte por pieza.
1,0 M de bloques, 52 piezas, el generador tarda ~40 s. Hojas enviadas a Andy: `fase2_render.png`,
`fase2_en_el_juego.png`, `fase3_sotanos.png`, `fase4_interiores_1.png`.
Modos del test: `FORJA_SOLO=castillo` (exterior + interiores sobre `bastion_prueba`) y, además,
`FORJA_CASTILLO=sotanos` (coloca las piezas reales `p_i_0_k` en alto con `place template` y entra en los
sótanos). El test pone `difficulty easy` mientras filma: en pacífico la guarnición desaparece.
**LUZ: HECHO (2026-09-20, 13:00).** `tools/castillo_luz.py` es una PASADA sobre el castillo terminado (la llama
`castillo_obra.build()` al final): calcula la luz de bloque como el juego (BFS desde cada emisor), busca los suelos
techados que quedan por debajo de `NEED` (6; 3 en cripta/osario/forja de almas, que llevan faroles de alma) y cuelga
lo que falte: **aplique** en la pared más cercana (escalera invertida + farol), **farol con cadena** del techo,
**hornacina** tallada en el muro en pasadizos bajos, o **farola** de muro. Apunta cada luz unos bloques hacia dentro
de lo oscuro para no gastar media luz en la pared. ~1470 luces, luz media de suelo 8,9, 16 suelos a cero de 45 764.
No amueblar la luz a mano sala por sala: amueblar, y la pasada rellena. Ojo: con gamma por defecto el deepslate se
traga la luz; por debajo de 6 una sala sale negra en las capturas. Hojas: `fase5_interiores_con_luz.png`,
`fase5_sotanos_con_luz.png`.
**SALAS (13:00-13:30):** `tools/castillo_salas.py` amuebla el ala oeste entera (biblioteca, scriptorium, armería con
dos Corazas vacías entre los soportes, sala de estandartes con la ESCALERA al piso alto, cuartel; arriba biblioteca
alta, esgrima con un Yunque andante y dormitorios), la capilla del Yunque, la sacristía, el comedor y aleaciones
(Molde roto). `castillo_luz.anchor()` sube con cadena hasta el techo todo lo que cuelga de nada. Hoja `fase6_salas.png`.
**32 CHUNKS:** Andy: «pon el renderizado a 32 chunks, que no ves bien lo que estás haciendo» -> `filmWholeBastion` pone
32 y LO GUARDA (`mc.options.save()`: él miró el options.txt de la carpeta de pruebas y vio el 16 viejo); espera 260 tics.
Si abre la ventana del test y la cierra, la pasada muere («Client shutdown from window close callback»). Hoja
`fase6_exterior_32_chunks.png`. Las motas grises sobre las torres son el humo de las chimeneas, no bloques sueltos.
**GENERACIÓN NATURAL (13:30):** `worldgen/structure_set/bastion_del_gremio.json` (spacing 140, separation 60,
`exclusion_zone` de 12 chunks frente a `forja:castillo_de_forja`), etiqueta de destino `on_bastion_del_gremio` y mapa
del Forjador maestro (`mapa_bastion`, 24 esmeraldas, icono de mansión, radio 200). **SIN VERIFICAR EN UN MUNDO NORMAL**:
el mundo del test es plano y de 3 de fondo; falta un `/locate` + visita en un mundo de verdad (terreno irregular con
`terrain_adaptation: none`: mirar si conviene `beard_thin` o enterrar un zócalo más hondo).
**TORRE DEL HOMENAJE, PLANTAS ALTAS (13:30):** `castillo_salas.keep_upstairs`: trono (nivel 1, sobre la Sala de cuños),
Consejo de los Nueve + trofeos (2 Corazas vacías) + mapas (nivel 2), aposentos + estudio + contaduría tras reja con una
Tenaza (nivel 3), observatorio con el Núcleo estelar (azotea). Escaleras: dos carriles contra el muro este (x 124-125 y
121-122, z 40..48) que se alternan por planta. Hoja `fase7_torre_del_homenaje.png`. Test completo en verde 13:25 y jar
13:32 (`build/libs/forja-1.0.0.jar`) con todo lo anterior. Las salas son enormes y se ven VACÍAS: falta densidad
(columnas, tabiques, alfombras, mesas, estanterías, vitrinas) - ése es el siguiente trabajo de verdad.
**13:45:** pilares de basalto + vigas en las plantas 2-4 de la torre (rejilla x 82/94/106/118, z 42..66 cada 8), nivel 4
= almacenes; recinto bajo por dentro (`lower_ward_rooms`: taberna con escalera y habitaciones, encargos, guardia,
caballerizas, talabartería, arquero, polvorín). Hoja `fase8_pilares_y_recinto_bajo.png`. Cámara de la taberna metida en
un aplique: moverla. Las casas del recinto bajo tienen el suelo de pizarra, no de tablones: mirar `building(floor=)`.
**GENERACION NATURAL VERIFICADA (13:45-14:00).** `FORJA_SOLO=mundo` (`visitBastionInTheWild`): mundo normal
(`worldBuilder().setUseConsistentSettings(false)`), `findNearestMapStructure` con la etiqueta del mapa, tp, 32 chunks,
cinco fotos `forja_53_mundo_*`. Sale entero (57 piezas) en taiga nevada, llanura y bosque. Lo que ensenio y ya esta
arreglado en `castillo.py`: `clear_the_site` (aire explicito 24 de alto sobre toda la planta: una colina pisaba el
foso), `underpin` (anillo de roca de 18 bajo foso y muralla: medio foso colgaba sobre un valle) y `no_saplings` (la
tierra a cielo abierto pasa a camino/barro/grava: el mundo planta arboles DESPUES de las estructuras y habia un bosque
en el recinto bajo). Falta volver a mirar lo ultimo. Hojas `fase9_*.png`. `terrain_adaptation` no puede ser otra cosa
que `none`: con barba el limite es 116 del centro y el castillo llega a ~125.
**Pendiente inmediato:** ~~más luz~~ ~~ala oeste, capilla, comedor, aleaciones~~ ~~plantas altas de la torre~~
~~recinto bajo por dentro~~ ~~generación natural~~ · casas de aprendices · densidad
en las salas grandes · nivel 4 de la torre (vacío) · casas del recinto bajo por dentro (taberna, encargos, guardia,
caballerizas, talabartería, arquero, aprendices, polvorín) · torres por dentro (caracoles) · (salieron casi negros: faroles de pared, braseros,
lava tras rejas; tétrico sí, ilegible no); amueblar el resto de salas del plano v3 (armería, biblioteca,
capilla, cuartel, plantas altas de la torre, torres, cripta con sus 9 sarcófagos, mazmorras con celdas y
forjadores presos, estanques del Temple bien hechos, carbonera, forja de almas, cámara acorazada con su
leyenda y el pasadizo secreto); escaleras de caracol en las torres y la de honor entre todas las plantas;
botín por zonas; generación natural.

Siguiente (lista anterior): detalle exterior (estandartes, vertederos de escoria, brechas), lo que falta del recinto bajo
(liza, campo de tiro, cantera, cementerio, campamento, lonja, huerto, nevero, aserradero), sótanos, y luego
interiores sala a sala con su botín y sus criaturas; al final generación natural (`structure_set` con
`exclusion_zone` frente al castillo pequeño), mapa, libro, logro, README/CHANGELOG.

## Estado del resto (todo cerrado)

Carga por pesos + todo-o-nada: test completo en verde (02:42), jar `build/libs/forja-1.0.0.jar` (02:44),
docs y memoria al día, capturas en `E:\\IA\\Claude\\Forja_capturas_mejoras` (falta añadir al índice los
planos v2 y v3: están en `docs/castillo/`; el script es `carpeta_capturas.py` del scratchpad).

## 2026-09-26 — torres, sótanos, botín y salas vacías (sesión «Mod Forja»)

Andy: «termina el bastión, revisa todas las salas… algunas tienen poca decoración, otras nada o casi nada o llegan a
ser muy feas, mejora todo lo del castillo».

- **Medir antes de tocar**: la auditoría (scratchpad `bastion/auditoria.py`) construye el castillo como el generador y
  cuenta lo que hay a la altura de la vista (suelo+1..3) en cada sala. Antes: 0 muebles en las 11 torres (pisos
  macizos sin escalera), en almas, antesala, bodega, cámara, lingotes, mazmorras y osario; 16 cosas en 541 bloques de
  la sala de cuños; el gabinete de orbes sin amueblar nunca. Sólo 2 tablas de botín para todo el castillo.
- `castillo_torres.py`: escalera de caracol de piedra alrededor de un machón 3×3 en cada torre, que corta cada piso;
  cada piso con su tema (cobre, eco, obsidiana, resina, escama, vidrio, archivo, pavesas, fuelle, vigía; la Hundida en
  ruinas), alfombra alrededor del hueco y estandartes con el color de la torre. Inquilinos: herrumbres en Cobre y
  Hundida, pavesas en la suya.
- `castillo_sotanos_salas.py`: cripta de los Nueve (9 tumbas entre los pilares, altar al norte con el cofre), osario,
  mazmorras (14 celdas, 3 forjadores presos, pozo con escalera de mano desde la sexta celda oeste hasta la cámara),
  guardia de mazmorras, bodega, lingotes, carbonera, antesala, forja de almas y cámara acorazada.
- `castillo_retoques.py`: muebles contra la pared en las salas que otros módulos dejaron desnudas, leyendo el castillo
  construido (hueco libre con piedra detrás, lejos de puertas, escaleras y huecos del suelo). La sala de cuños y la
  Fragua Profunda guardan el centro para la pelea.
- `castillo_botin.py`: 9 tablas nuevas (`bastion_torre`, `_torre_cima`, `_cripta`, `_guardia`, `_bodega`, `_lingotes`,
  `_carbonera`, `_almas`, `_camara`); los talismanes salen ya con gema. `castillo.generate` avisa si un cofre nombra una
  tabla que no existe.
- Fotos: `filmWholeBastion` suma 13 tomas dentro de las torres; `filmBastionCellars` 8 de los sótanos nuevos.

Pendiente: la luz aún deja 15 suelos a 0 (ver `castillo_luz`); casas de aprendices; los tejados a dos aguas la
auditoría los lee como «CIELO».
