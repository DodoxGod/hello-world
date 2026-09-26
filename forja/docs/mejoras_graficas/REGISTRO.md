# Mejoras gráficas — registro de la sesión autónoma (2026-09-19)

Andy pidió: revisar el grafo, hacer todas las mejoras gráficas posibles sin consultar, enseñar cada
cambio en el chat (foto o GIF) y arreglar de paso lo que se vea roto. Copia de seguridad previa en
`E:\IA\Claude\Forja_copia_2026-09-19_antes_mejoras_graficas`.

Cómo se prueba sin molestar: `scratchpad/run_solo.ps1 -Solo <seccion>` copia el árbol a una carpeta
aislada y corre ahí el gametest (nunca se compila en el árbol real mientras el juego esté abierto).
Secciones sueltas del test (`FORJA_SOLO=`): `onda`, `cielo`, `libro`, `hud`, `meteorito`.

## Hecho

1. **Eventos del cielo rehechos** (`client/EventSkyRenderer`, `client/SkyMood`, mixins de cielo, niebla
   y lightmap). Antes cada evento era un tinte plano del cielo. Ahora: luna de sangre con halo, eclipse
   con disco y corona (el mundo se oscurece y salen las estrellas de día), auroras en cuatro cortinas,
   sigilo rúnico girando con relámpagos que iluminan el suelo y truenan, estrellas fugaces y bólidos,
   luna enorme de marea viva, horizonte en llamas con fuego cayendo, niebla de almas con luces ancladas
   al terreno, ventisca y niebla que cierran la vista de verdad. La luz del cielo toma el tono del
   evento. Texturas en `textures/environment/` (generador: `generate_sky_textures`).
   Gotchas: el pipeline `eyes` aplica niebla **por vértice** → los sprites grandes se curvan sobre la
   esfera del cielo (rejilla 4x4) y todo cuelga dentro de `clearTo * 0.72`; el sol visible es la mitad
   de su sprite (disco del eclipse = 0.17 R).
2. **Cartel de evento** (`client/EventBannerHud`): baja al empezar (nombre, jarra del color, la mejora
   que trae, cómo guardarla, línea de tiempo) y uno pequeño al acabar. El chat queda en "Empieza: …".
3. **Libro guía** (`client/GuideBookScreen`, textura `textures/gui/libro.png`, generador
   `generate_book_gui`): cuero cosido, latón, pergamino con grano, lomo, cinta; portada con emblema;
   cabeceras-estandarte; **5 pestañas de sección**; **tira de progreso** navegable al pie; historial
   (clic derecho / Retroceso), Inicio → índice; la página se asienta al pasarla. Test: pestaña, tira y
   "atrás" comprobados con clics reales.
4. **Barras del HUD** (`client/HudBars`, `FrenzyHud`, `FlightHud`): pozo con labio, tapas de latón,
   relleno iluminado con destello, halo al llenarse / latido al agotarse, icono de las alas.
5. **Charcos persistentes** (`Shockwave.pool`): escoria y aceite dibujados como mancha con borde (antes
   partículas del servidor cada tick); se apagan con su tiempo. (Ya no siguen el terreno: ver 19.) Anillos de jugador
   (frenesí a tope, maestría 10, sinergias) con la onda nueva.
6. **Fallo no estético:** `items/guante.json` huérfano (aviso de modelo en cada arranque) borrado, y el
   generador avisa ahora de definiciones de ítem sin ítem (`check_orphan_items`).

7. **Cuña del Autómata** (`Shockwave.wedge` / `poolWedge`, arco + `facing` sincronizados): aviso con
   bordes rectos y charco ardiendo con la misma forma. `make()` fija todo antes de entrar al mundo.
8. **Barra de jefe del Herrero** (`client/BossBarArt`, `BossHealthOverlayMixin`, textura
   `gui/barra_herrero.png`): hierro con cabezas de martillo, muescas al 75/50/25 %, metal fundido,
   dorado con tramado al reforjar, violeta en el último cuarto. El raíl va POR DEBAJO (el nombre del
   jefe se escribe justo encima de la franja).
9. **Meteoritos**: marca de aterrizaje (`Shockwave.markFalling`), bólido dibujado encima de la marca
   (cabeza + halo + cola, `ShockwaveRenderer.falling`) y onda de impacto de 11 bloques.
10. **Sonido ambiente por evento** (`SkyMood.ambience`): viento (elytra) en ventisca, fuego, almas, etc.
11. **Movimientos especiales del jugador** (Torbellino, Sismo, Siega) con anillo de alcance.
12. **Bestiario**: 11 mobs nuevos que no estaban en la guía + retrato 3D de cada criatura
    (`GuideBookScreen.Portrait`, usa `InventoryScreen.extractEntityInInventoryFollowsMouse`; la entidad
    necesita `setId` propio o 26.2 lanza "entity ID before ID assignment"). Tildes corregidas en 3 nombres.

13. **Pantallas de trabajo.** Mesa de forja: la estrella grabada se enciende cuando lo que hay encima
    forja algo (`textures/gui/estrella_viva.png`, blanca, teñida naranja o violeta según la mesa),
    chispas que recorren sus líneas, estallido al golpear (más y más lejos si el golpe es perfecto),
    brillo que cruza el botón, riel del martillo con la zona "decente" visible y cabeza de martillo,
    barra de calor con llama. Crisol: olla con forma (la colada se dibuja como rectángulo y la textura
    se vuelve a poner encima con el hueco de la olla, fila 198 de la hoja), burbujas, resplandor.
    Caja de moldeo: huella en la arena que se llena / se imprime con el progreso.
    **Fallo no estético:** ambos menús ponían el inventario en (15,122) y la textura lo pintaba en
    (22,114) → ítems 7 px a la izquierda y 8 px abajo de su casilla; salida del crisol 5 px movida;
    casilla del colador sin marco; título ilegible (marrón sobre madera); textos largos fuera del panel.
    Las dos texturas no estaban en el generador: ahora sí (`generate_station_gui`) y el test comprueba
    que cada casilla activa cae sobre un cuadro pintado (`slotsOffTheirSquares`). `FORJA_SOLO=pantallas`.
14. **Partículas propias nuevas** (`ModParticles.VAPOR`, `GOTA`; `ForjaParticles.Steam/Drip`;
    `generate_particle_sprites`): vapor de 16 px en 4 fotogramas que se deshace (autómata, temple,
    Templador con presión) con luz mínima para que se vea de noche; gota de metal fundido con el color
    del metal (se lanza con r,g,b en lugar de velocidad) que suelta chispas al caer (caño de colada).
    `FORJA_SOLO=particulas`.
15. **Textos**: faltaban las descripciones del libro de 3 eventos (ventisca, marea viva, lluvia de
    pavesas: salía la clave cruda) y las otras 6 no contaban lo que ahora se ve; bloque entero de
    "Técnicas" y 3 textos más sin tildes.

16. **Tooltip de lo forjado** (`item/PartsStrip`, `client/PartsStripTooltip`, inyección en
    `ItemStackMixin.getTooltipImage`): las piezas dibujadas en fila bajo el nombre, icono + material en
    su color; se parte en filas a los 210 px. Sustituye a una línea de texto por pieza. Se engancha en
    el mixin y no en los ítems porque hay una docena de clases de ítem forjado, cada una hija de una
    clase vanilla distinta. En 26.2 el callback de Fabric se llama `ClientTooltipComponentCallback`.
17. **Armario de piezas** (`menu/CabinetMenu`, `client/CabinetScreen`, `generate_cabinet_gui`): tres
    cajones en vez del cofre gris. **Fallo no estético:** como `ChestMenu`, sus casillas aceptaban
    cualquier cosa a mano; `canPlaceItem` sólo lo miran las tolvas. Ahora `Slot.mayPlace` pregunta
    `PartsCabinetBlockEntity.accepts`, y `quickMoveStack` también (al rellenar un montón existente
    `moveItemStackTo` no pregunta a la casilla).
18. **Textos que salían como clave** y cómo no repetirlo: `GuideBookScreen.rawKeys()` lee por reflexión
    lo que guarda cada elemento del libro (Component o líneas ya partidas) y busca cualquier cosa con
    forma de clave; `checkEverythingIsNamed` pide los 685 nombres/descripciones que el código arma a
    partir de un id. Cazaron: `trait.forja.estelar.largo`, `trait.forja.vacio.largo`,
    `conjunto.forja.escoria`. El generador de idiomas no puede ver esas claves (acepta un prefijo en
    cuanto UNA clave bajo él existe). También: 20 `tag.item.forja.*` para los visores de recetas, y el
    bono del corazón de forja decía «ardes la mitad» cuando el número (-1,0) apaga el fuego del todo.

19. **Revisión de Andy** ("todo me parece súper bien, únicamente…"):
    - Áreas a un nivel + tinte (`ShockwaveRenderer.settle` elige el nivel, `tint` tiñe cara a cara lo
      que sobresale; `Shockwave.floorLevel/floorRaised` en el cliente). El borde fino del charco lleva
      una caída por fuera: a un octavo de bloque de ancho se dibujaba a rayas de lejos.
    - Mesa mayor: `ForgeScreen.tone()` / `accent()` para todo lo que pinta la pantalla.
    - `ForgeMaterial.molten()` única para cuba, canal, mesa de colada, crisol y caño.
    - Técnicas: barra de maestría con pasadores, candados, pulso; texto corto `gui.forja.herrero.corto`.
20. **Fondo de logros** propio (`generate_advancement_background`, `gui/advancements/backgrounds/forja`).
21. Grafo actualizado con `graphify update .` (4088 nodos, 167 comunidades; sólo código, sin LLM).

22. **Potencial y mesa de extracción** (diseño de Andy vía Codex; ver `docs/POTENCIAL.md`). Lo gráfico:
    línea de potencial en el tooltip y en la mesa, avisos en ámbar de por qué una mejora no sube más,
    fundente maestro y orbe vacío (`generate_potential_assets`), bloque de la mesa de extracción
    (`generate_extraction_table`) y su **inventario propio, la rueda** (`generate_extraction_panel`,
    `client/ExtractionScreen`): gemas por mejora, ficha, pago en fantasma, vuelo de la gema a la cuna.
23. **Negritas del libro**: `GuideText.rubric` pone en tinta de rúbrica lo que va entre `**`; 70 textos
    (bestiario incluido) enseñaban los asteriscos.
24. **Fallo no estético gordo**: `mineable/pickaxe` se escribía dos veces y ganaba la segunda lista; crisoles,
    cubas, conductos, caño, cajas y mesas de colada, farol, fragua apagada y yunque del herrero no soltaban
    nada al picarlos. `PICKAXE_BLOCKS` única + `check_tool_tags()`. OJO: `generate_data()` sola borra
    carpetas que otras funciones vuelven a crear — para regenerar datos hay que correr el generador entero.

## Pendiente (por orden)

- (nada pendiente de la lista original)
