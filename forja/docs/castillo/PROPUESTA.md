# El Bastión del Gremio — propuesta de castillo

**Estado: PROPUESTA, pendiente de que Andy la apruebe (2026-09-20). Nada construido.**
Andy: «quiero que hagas un castillo con temática del mod, que sea enorme, teniendo varias salas; quiero
que primero me enseñes las salas que piensas agregar, cómo conectarlas, decoraciones, etc».

Plano: `plano_v1.png` (lo dibuja `plano_castillo.py`, guardado en el scratchpad de la sesión; las
coordenadas del plano son bloques, norte arriba, entrada por el sur).

Regla: **sólo contenido que ya existe en el mod** (ni criaturas ni sistemas nuevos — ver la nota sobre el
documento de ideas de Codex en la memoria del proyecto).

## Historia de la propuesta (2026-09-20)

1. **v1** (`plano_v1.png`): un recinto de 101 × 101, 28 espacios. Andy: «me parece bien, pero ¿no crees que
   100 × 100 pueda ser poco?».
2. **v2** (`plano_v2_doble_recinto.png`): doble recinto de 201 × 201 (tope técnico del jigsaw ≈ 250 × 250:
   128 bloques desde el centro). Andy: «si tiene tanto tamaño ¿no debería tener más salas? además quitaste
   los demás pisos, sólo estás haciendo 1».
3. **v3** (`plano_v3_completo.png`, lo dibuja `plano_castillo_v3.py`): **la vigente**. Los 8 niveles
   dibujados (−2, −1, 0, 2, y 3-4-5 de la torre), corte norte-sur con alturas e índice de **82 espacios**.
   Las coordenadas de cada sala están en ese script y son las que hay que construir.

**El mod ya tenía un castillo pequeño** (`castillo_de_forja`, 21 × 14 × 21, de la otra sesión:
`write_forge_castle` / `generate_forge_castle` en `tools/generate_assets.py`): la **Sala de cuños** con el
Guardián de cuño, una Tenaza, un Percutor, los tres sellos (pilares con farol de pavesa: mientras quede
uno en pie el guardián no recibe daño) y el cofre de plantillas (`chests/castillo_de_forja`). El Bastión
lo **sustituye conservando el id** `forja:castillo_de_forja` — mapas, tabla de botín y test siguen
valiendo — y la Sala de cuños pasa a ser la sala 42 del plano v3, a la cabeza del gran salón.

Criaturas del mod y dónde van (todas existentes): Corazas vacías (barbacana, armería, cementerio de noche,
cripta, trofeos, forja de almas), Autómatas + Escoria viviente + Ascua mayor (fundición), Moldes rotos
(galería de moldes), Yunque andante (barbacana interior, esgrima), Herrumbre (Torre del Cobre, Torre
Hundida), Pavesas (su torre), Templadores (Sala del Temple), Cargadores de carbón (carbonera, minas),
Tenazas (guardia de las mazmorras), Núcleo estelar (observatorio), Aprendices de la fragua (casas,
antesala), saqueadores y capitán (recinto bajo), élites (cuartel, salón), Guardián de cuño (cuños, cámara
acorazada) y el Herrero Caído por ritual en la Fragua Profunda.

Cinco entradas: puerta principal, brecha de la muralla este, desagüe de escoria (desde el foso), galería
de minas (junto al nevero) y osario (desde el cementerio).

## Medidas del v1 (las del v3 están en su script)

Muralla 101 × 101 (grosor 5, adarve encima), cuatro torres de 16 × 16 en las esquinas, barbacana de
21 × 18 al sur con puente de 9 × 26 sobre un foso de escoria (magma + basalto) de 6 de ancho. Torre del
homenaje de 41 × 39 al norte, 4 plantas (~40 de alto con el observatorio). Dos niveles de sótano.

## Salas

| # | Sala | Nivel | Decoración | Enemigos / botín |
|---|---|---|---|---|
| 1 | Puente de escoria | 0 | Puente roto sobre el foso, faroles | — |
| 2 | Barbacana (arriba: sala del rastrillo) | 0 / 2 | Rastrillo de barrotes, matacanes, estandartes con el martillo, torno | 2 Corazas vacías |
| 3 | Patio de armas | 0 | Monumento a la Fragua Muerta, pozo, campo de prácticas con maniquíes | — |
| 4 | Torre del Fuelle (SO) | 0-2 | Fuelles gigantes, cargas de viento | cofre |
| 5 | Torre del Vigía (SE) | 0-2 | Almenara, catalejo | cofre (Sonar / Visión nocturna en orbes) |
| 6 | Torre del Archivo (NO) | 0-2 | Archivo; estantería falsa → escalera secreta a la cámara acorazada | cofre |
| 7 | Torre de las Pavesas (NE) | 0-2 | Brasero abierto arriba, faroles vacíos | Pavesas alimentadas |
| 8 | Adarve | 2 | Camino de ronda con almenas sobre toda la muralla; pasarelas a la torre del homenaje | — |
| 9 | Caballerizas y talabartería | 0 | Mesa de talabartería, caballo con barda, heno | saqueadores |
| 10 | Lonja del gremio | 0 | Puestos de mercado abandonados | saqueadores |
| 11 | Armería | 0 | Armarios de piezas, soportes con armas de cada material, plantillas en marcos | piezas, equipo forjado (potencial 60-90) |
| 12 | Cuartel y comedor | 0 | Literas, cocina, barriles | Capitán saqueador y banda |
| 13 | Gran fundición (doble altura) | 0-1 | Crisol de obsidiana, banco de cubas, canales por el suelo, caños desde una pasarela de grúas, mesas de colada | 2 Autómatas de forja · moldes, aleaciones |
| 14 | Taller del maestro | 0 | Las 5 mesas + yunque del herrero (taller completo), mesa de extracción | plantillas, fundente maestro |
| 15 | Capilla del Yunque (arriba: coro) | 0 / 2 | Altar-yunque, vidrieras color brasa, corazas arrodilladas, faroles de almas | talismanes, sellos |
| 16 | Biblioteca de plantillas (2 pisos) | 0 / 2 | Estanterías, atriles con páginas de historia, libros encantados | libros, mapas a otras estructuras |
| 17 | Gran salón del gremio (doble altura) | 0-1 | Mesas largas, 9 estandartes (uno por técnica), lámparas de farol de pavesa, chimenea, los 3 cuadros del mod | Élites · Guardián de cuño |
| 18 | Trono del Gran Maestre | 0 | Estrado, trono, tapices | cofre |
| 19 | Galería alta y de trofeos | 2 / 3 | Réplicas de las leyendas en vitrinas | — |
| 20 | Sala de mapas | 3 | Mesa redonda con mapa, mesas de cartografía, estandartes de las otras estructuras | mapas |
| 21 | Aposentos del Gran Maestre | 2 | Cama, escritorio, cofre tras un cuadro | martillo del maestro (historia) |
| 22 | Observatorio | 4 | Cúpula abierta, pararrayos, amatista, soporte de la jarra de esencia | hierro estelar, orbes |
| 23 | Carbonera y montacargas | −1 | Bloques de carbón, raíles, montacargas a la fundición | Cargadores de carbón |
| 24 | Mazmorras | −1 | Celdas con Forjadores presos (liberados, comercian); celda 7: palanca oculta | — |
| 25 | Sala del Temple | −1 | Cuatro estanques entre columnas: agua, lava, nieve polvo, miel | Templadores |
| 26 | Cripta de los Nueve Maestros | −1 | 9 sarcófagos con nombre (uno por técnica), fuego de almas | Corazas vacías al abrir el cofre central |
| 27 | Cámara acorazada (secreta) | −2 | Leyenda en pedestal, suelo con trampa | Guardián de cuño · orbes, fundente maestro |
| 28 | La Fragua Profunda | −2 | Sala redonda de 35 de diámetro, la estrella de la mesa de forja en canales de lava, columnas, cadenas, fragua muerta | ritual del Herrero Caído |

## Conexiones

- Puente → barbacana → patio. Del patio salen tres rutas: **ala oeste** (armería → cuartel → mazmorras),
  **ala este** (fundición → taller → carbonera) y **torre del homenaje** (salón → trono → plantas altas).
- La escalera noroeste del salón baja a la cripta → Sala del Temple → Fragua Profunda. El sótano −1 se
  comunica entero (mazmorras ↔ temple ↔ carbonera): **tres caminos al jefe**.
- El adarve une las cuatro torres, la sala del rastrillo y la torre del homenaje por pasarelas.
- La cámara acorazada sólo por secreto: estantería falsa de la Torre del Archivo, o palanca de la celda 7.

## Decisiones pendientes de Andy

1. Estilo: (A) piedra clásica por fuera + pizarra/piedra negra y cobre en las zonas de forja
   *(recomendado)* · (B) todo oscuro · (C) todo piedra clásica musgosa.
2. Estado: en pie pero caído (muralla reventada como entrada alternativa, una torre sin tejado)
   *(recomendado)* o intacto.
3. Jefe: que el ritual del Herrero Caído funcione en la Fragua Profunda *(recomendado: sí)*.
4. Generación: estructura muy rara en llanura/montaña + mapa del Forjador maestro + `/place`.

## Cómo se construiría

Como las otras cinco estructuras: `tools/generate_assets.py` escribe el NBT (`nbt_bytes`). Por tamaño
irá en piezas de jigsaw de ≤ 48³ con un único candidato por conector (disposición fija, no aleatoria),
con un constructor por código (muros, arcos, almenas, tejados, mobiliario) y vistas previas renderizadas
fuera del juego antes de la captura real con el gametest (`/place structure`).
