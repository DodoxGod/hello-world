# Fundición 3: metal en mB, almacén, mezcladora y fabricadora

Diseño (2026-10-01) para la idea de Andy. **Solo diseño**: lo implementa un agente Sonnet en las tandas de la sección 14.
Sustituye a `FUNDICION_V2.md` en todo lo que toque el metal (las partes B, fluidos de calor, y C, montadora, siguen vigentes).

**Decisiones de partida (de Andy):** 1 lingote = **144 mB**, 1 pepita = **16 mB**, 1 bloque = **1296 mB**. No hay mundos
que conservar: **sin migración ni compatibilidad**; se cambia, renombra o quita lo que haga falta. La estrella de la mesa
de forja sigue para lo básico. Cada aleación conserva su calor mínimo (`Alloys.Heat`) y las cumbre cuestan exactamente lo
que hoy (1 corazón por lingote, `Alloys.NO_TIER_BONUS`). Las fraguas lejanas siguen haciendo lo suyo.

**La línea en una frase:** el crisol funde cualquier metal → un conducto lo lleva al **almacén** (cubas) → la
**mezcladora** hace aleaciones con lo del almacén y lo devuelve → la **fabricadora** saca metal del almacén y lo vierte
hacia abajo por el **portacolador** al **molde** de la mesa de colada → la pieza cae al **cofre de piezas**.

```
   [crisol]──conducto──[cuba][cuba]──conducto──[mezcladora]      (calor: debajo, tubo de calor o crisol pegado)
                          │
                       conducto
                          │
            [moldero]─[fabricadora]          ← menú: pieza + material + cantidad
                       [portacolador]        ← colador (fino / grueso), con desgaste
                       [mesa de colada]─[cofre de piezas]   ← el molde; la pieza sale al cofre
```

---

## 1. Bloques

### 1.1 Lista

| Bloque (id) | Estado | Trabajo | Calor | Entra | Sale |
|---|---|---|---|---|---|
| Crisol de barro / hierro / obsidiana (`crisol_de_*`) | CAMBIA | Funde **cualquier** metal, mena o pieza en su propio fluido. Ya no hace aleaciones ni tiene ranura de salida | Su tier (TEMPLADA / CALIENTE / FORJA_BLANCA), ascuas o farol; tubo de calor y bloque de debajo como hoy | Ítems fundibles (tolva arriba), ascuas (tolva lateral) | mB por conducto al almacén; ascuas al recuperar piezas (como hoy) |
| Cuba (`cuba_de_colada`, nombre "Cuba de metal") | CAMBIA | Almacén **multifluido**; cubas pegadas = un almacén | Ninguno: aislada, el metal nunca fragua dentro | mB por conducto | mB por conducto |
| Conducto de bronce / acero / damasco (`conducto_*`) | CAMBIA | Lleva metal con **caudal** y pérdida de calor por bloque; puede quedar **tapado** | Pierde calor (tabla 3); un tubo de calor pegado lo anula | — | — |
| Llave de paso (`llave_de_paso`) | IGUAL | Abre/corta la red (clic, redstone) | Como conducto de acero | — | — |
| **Mezcladora** (`mezcladora`) | NUEVO | Hace aleaciones con mB del almacén + aditivos | El de la receta: debajo, tubo de calor o crisol encendido pegado | mB por conducto; aditivos (4 ranuras, tolva arriba/lados) | mB de la aleación por conducto |
| Fragua de almas / del vacío (`FarForgeBlock`) | CAMBIA | Es una **mezcladora especial** que solo hace sus aleaciones; se conecta a la red | Como hoy: combustible en su dimensión o su fluido por tubo (sangre de blaze / aliento de dragón) fuera | mB por conducto o lingotes en el hogar; aditivos en el hogar | mB por conducto (o tolva/encima si no hay red) |
| **Fabricadora** (`fabricadora`) | NUEVO | Elige pieza/herramienta/lingote + material; saca el metal del almacén y lo vierte hacia abajo | ≥ calor de fusión del material (`CrucibleBlockEntity.meltHeat`) | mB por conducto; el molde llega solo desde el moldero | Chorro hacia abajo al portacolador |
| **Portacolador** (`portacolador`) | NUEVO (sustituye al colador puesto) | Sostiene el colador activo + 4 de recambio; decide la calidad | — | Coladores (mano, tolva lateral) | El chorro, a la mesa de debajo |
| Mesa de losa / brasa / almas (`mesa_de_*`) | CAMBIA | Cama del molde. Ya no saca metal de cubas ni tiene contador de calor; conserva `holds` y `luck` | — (el calor es de la fabricadora) | El molde (desde el moldero o a mano), el chorro | La pieza → cofre de piezas pegado, si no tolva debajo, si no se queda |
| **Cofre de piezas** (`cofre_de_piezas`) | NUEVO | Guarda piezas y equipo terminado, ordenado por tipo y material | — | Automático desde mesa/fabricadora pegadas; tolva | Tolva debajo (hacia la montadora) |
| **Moldero** (`moldero`) | NUEVO | Guarda moldes, marcos y lingoteras; la fabricadora los lista | — | Mano, tolva | El molde viaja solo a la mesa y vuelve |
| Caja de moldeo (`caja_de_moldeo*`) | CAMBIA poco | Corta moldes y marcos (igual); el baño de colador saca **1152 mB** (8 × 144) del almacén | Igual | Igual | Igual |
| Caldera, depósito de calor, tubo de calor, montadora, armario de piezas | IGUAL | Los tubos ahora también calientan mezcladora, fabricadora y tapones | — | — | — |
| Caño de colada (`cano_de_colada`) | **SE QUITA** | La fabricadora vierte hacia abajo; ya no hay caídas | | | |
| Colador puesto (`colador` bloque, `StrainerBlock`) | **SE QUITA** | Lo sustituye el portacolador (el ítem `colador` sigue) | | | |

### 1.2 Menús (maquetas)

**Crisol** (ya no hay huecos de aleación):

| Crisol de hierro · CALIENTE · ascuas: 3 | |
|---|---|
| Entrada (6 huecos) | `[Mena de hierro ×12][Lingote de bronce ×4][Pico de acero roto][ ][ ][ ]` |
| Haciendo | Fundiendo mena de hierro → hierro · ▓▓▓▓░░ 62 % |
| Destino | Almacén A (cuba ×4): hierro 1872 mB · conducto de acero, 9 bloques |
| Avisos | "La varilla de blaze no se funde: va a la mezcladora como aditivo" |

**Panel del almacén** (clic con la mano vacía en cualquier cuba; muestra toda la red):

| Metal | Barra | mB | Equivale |
|---|---|---|---|
| Acero | `████████░░` | 1872 | 13 lingotes |
| Hierro | `█████░░░░░` | 960 | 6 lingotes + 6 pepitas |
| Damasco | `█░░░░░░░░░` | 144 | 1 lingote |
| **Total** | | 2976 / 147 456 | 4 cubas, 2 almacenes · [Ver por almacén] |

**Mezcladora**:

| Mezcladora · calor FUNDIDA (tubo: sangre de blaze, ×1,5) | | | |
|---|---|---|---|
| **Aleación** | **Ingredientes** (tienes / pide) | **Calor** | **Estado** |
| ▶ Damasco (→ 144 mB) | acero 1872/144 mB · chatarra de netherita 3/1 | fundida ✔ | listo · alcanza para 3 |
| Obsidiacero (→ 144 mB) | acero 1872/144 · obsidiana 0/1 (o 144 mB fundida) | fundida ✔ | *gris* · falta 1 obsidiana |
| Solacero | damasco 144/144 · vara de blaze 0/3 | forja blanca ✘ | *gris* · faltan 3 varas de blaze; necesita forja blanca |
| Cantidad | `[1] [4] [16] [∞]` | | `[Empezar]` `[Parar]` |
| Progreso | Damasco 2/4 · ▓▓▓░░ · 180/240 t | | Sale a: almacén A |
| Aditivos | `[chatarra ×3][ ][ ][ ]` | | |

**Fabricadora**:

| Fabricadora · CALIENTE · colador: acero fino (31/40 usos) · mesa: brasa | | | |
|---|---|---|---|
| **Pieza** (moldero + mesa) | **Coste** | **Material** (mB en almacén) | **Estado** |
| ▶ Cabeza de pico (molde) | 432 mB | ▶ Acero 1872 · Bronce 432 · Hierro 960 | listo · alcanza para 4 |
| Espada (marco) | 576 mB | Damasco 144 | *gris* · faltan 432 mB de damasco |
| Lingote (lingotera) | 144 mB | cualquiera | listo |
| Cantidad | `[1] [4] [16] [∞]` | | `[Colar]` `[Parar]` |
| Avisos | | | "El colador de bronce no aguanta el acero: la pieza saldrá basta" |

**Cofre de piezas** (pestañas `[Piezas] [Herramientas] [Armas] [Armaduras] [Todo]`, orden `[tipo] [material]`):

| Tipo | Material | Cantidad | Marcas |
|---|---|---|---|
| Cabeza de pico | Acero | 4 | 3 coladas, 1 basta |
| Cabeza de pico | Bronce | 2 | coladas |
| Hoja | Damasco | 1 | colada + mejora |
| Mango | Hierro | 6 | |

**Moldero** (54 huecos; arriba la lista que ve la fabricadora):

| Molde / marco | Cuántos | Dónde está ahora |
|---|---|---|
| Molde: cabeza de pico | 1 | en la mesa (colando) |
| Marco: espada | 1 | moldero |
| Lingotera | 2 | moldero |

---

## 2. Unidades y qué se funde

`J/forge/Mb.java`: `NUGGET = 16`, `INGOT = 144`, `BLOCK = 1296`, `format(mB)` → "13 lingotes", "6 lingotes + 6 pepitas".
**Un fluido = un `ForgeMaterial` no básico** (`!isBasic()`): no hace falta un registro de fluidos nuevo.

| Lo que entra al crisol | mB | Fluido |
|---|---|---|
| Lingote de metal o aleación, hierro estelar, placa hueca, escoria, oricalco, corazón de forja | 144 | su material |
| Diamante, esmeralda, obsidiana, obsidiana llorona (materiales no básicos) | 144 | su material |
| Pepita (hierro, oro, cobre) | 16 | su material |
| Bloque de metal (hierro, oro, cobre, netherita…) | 1296 | su material |
| Mena cruda / mena (bloque) | 144 | su metal (igual que hoy: 1 mena = 1 lingote) |
| Bloque de mena cruda | 1296 | su metal |
| Pieza suelta o equipo forjado | recuperación (sección 9) | cada parte, su material |
| Cualquier otra cosa (carbón, redstone, vara de blaze, chatarra de netherita, cuarzo…) | — | **no se funde**: es un *aditivo* de la mezcladora; el crisol lo rechaza con aviso |

**Ingrediente de receta: METAL o ADITIVO** (`MixerRecipes.form(Part)`, lista fija, decidida por Andy el 2026-10-01):

| Clase | Qué es | En la mezcladora |
|---|---|---|
| **METAL** (suma volumen) | lingotes de cobre, hierro, oro y netherita; **todas las aleaciones** (oricalco incluido); sus fluidos | entra como mB del almacén (o lingotes en el hogar de una fragua lejana); **suma** a la salida |
| **ADITIVO** (se gasta, no suma) | carbón, amatista, ladrillo de resina, arcilla, ascua, redstone, cuarzo, vara de blaze, fragmento de eco, **chatarra de netherita** (es chatarra, no lingote), **hierro estelar** (mineral de la estrella, aún sin refinar), **placa hueca** (reliquia), **escoria**, obsidiana, obsidiana llorona, **corazón de forja**; en las fraguas: tierra de almas, basalto, piedra negra, crema de magma, caparazón de shulker, coro reventado, piedra del End, lágrima de ghast, bloque de magma | ítem en las 4 ranuras de aditivo (u hogar). Un aditivo que el crisol funde (hierro estelar, placa hueca, escoria, obsidiana, obsidiana llorona) vale también como **144 mB de su fluido** del almacén; el corazón, solo entero |

Hierro estelar, placa hueca, escoria, obsidiana y corazón se siguen fundiendo en el crisol para **colar piezas** de esos
materiales; lo de "aditivo" solo dice cómo cuentan en una aleación.

---

## 3. Almacén (modelo) y flujo

### 3.1 Almacén

| Regla | Valor |
|---|---|
| Almacén | Cubas pegadas (cualquier dirección) = **un** almacén, sin bloque maestro (como `MeltDeposit` hoy, renombrado `MetalStore`) |
| Capacidad | **36 864 mB por cuba** (256 lingotes, la de hoy) × cubas; máximo 256 cubas por almacén |
| Contenido | **Varios metales**: mapa `ForgeMaterial → mB` en orden de llegada; se dibuja en capas de colores de abajo arriba repartidas entre las cubas |
| Calor | Ninguno: aislada, **el metal del almacén nunca fragua** (se quitan `HOT/COOLS/WARMS/SET/REMELT_LOSS`) |
| Romper una cuba | El ítem **conserva su parte** (componente `forja:metal_cuba`, como una caja de shulker; tooltip "Acero 512 mB…"); al ponerla se suma al almacén que toque |
| Entrar | Solo fundido (crisol, mezcladora, fragua lejana) por conducto. Sin entrada a mano |
| Salir | Solo por conducto (mezcladora, fabricadora, caja de moldeo). Para sacar lingotes: lingotera en la fabricadora |
| Varios almacenes en una red | El panel suma todos. Llenado: primero el almacén que ya tiene ese metal y sitio (el más cercano); si no, el más cercano con sitio (la regla de "uno a uno" de V2, a nivel de almacén) |
| Sacar | Del almacén más cercano que lo tenga; si no basta, del siguiente |
| Comparador junto a una cuba | 0–15 según lo lleno de **su** almacén (como hoy) |
| Guardado | Cada cuba guarda su parte (mapa), como hoy; el almacén se rehace al cargar |

### 3.2 Flujo por conducto

La red (`MeltNetwork`, inundación cacheada) no cambia de idea: lo que cambia es que **mover metal lleva tiempo** y el
camino tiene un **presupuesto de calor** de 200.

| Conducto | Caudal | Un lingote tarda | Pierde calor por bloque | Largo máximo |
|---|---|---|---|---|
| Bronce | 12 mB/tick | 12 t | 6 | 33 bloques |
| Acero (y llave de paso) | 36 mB/tick | 4 t | 3 | 66 |
| Damasco | 108 mB/tick | ~1,3 t | 1 | 200 |

- Caudal de un camino = el del bloque más lento. Pérdida = suma por bloque. Un **tubo de calor** con fluido caliente
  pegado a un bloque de conducto deja su pérdida en 0 (así se alargan líneas).
- Camino que se pasa de 200: no se usa, y la máquina dice "conducto demasiado largo y frío (pierde 228 de 200)".
- Un conducto **tapado** (sección 6) corta la red como una llave cerrada.
- El crisol empuja lo fundido al ritmo del camino; si no hay almacén con sitio, **espera** (como en V2).
- Sin recorrer la red cada tick: se reutiliza la caché de `MeltNetwork`; el camino de un trabajo se guarda al empezarlo.

---

## 4. Mezcladora

### 4.1 Qué hace cada sitio (por calor)

| Dónde | Aleaciones | Por qué |
|---|---|---|
| **Estrella de la mesa de forja** (`Alloys.STAR`, nuevo) | bronce, latón, peltre (TEMPLADA); acero, electro, acero refractario, lingote de temple (CALIENTE) | Lo básico del principio, con lingotes en la mano |
| **Mezcladora** | todas las de `Place.ANY` cuyo resultado es un `ForgeMaterial`: las 5 básicas metálicas de arriba (para automatizar), damasco, acero estelar, obsidiacero, cinerio, voltaico, almacero, vidriacero, solacero, lunacero, acero vivo, astralita, oricalco, iracero, égida, arcanio | Las buenas pasan por aquí. Acero refractario y lingote de temple no son materiales: solo mesa |
| **Fragua de almas** | fatuo, magmacero, espectracero, corazón de volcán | `Alloys.PLACES` sin cambios |
| **Fragua del vacío** | eterio, eclipse | ídem |

Cambios en `Alloys`: nuevo `STAR`; `Alloys.match` (la mesa) solo mira `STAR`; se **quitan** `FOUNDRY_ONLY` (ya no
significa nada) y `WHITE_HEAT_ONLY` (pasa a `recipe.heat() == FORJA_BLANCA`). Calores: **iguales**. Las recetas de `STAR`
no cambian en la mesa; las de mezcladora y fraguas cambian según 4.4 (`Recipe.inputs`/`output` de `Alloys.ALL` y `EXTRA`
pasan a ser las ajustadas, para que guía, JEI y máquinas digan lo mismo).

**Receta de la mezcladora (cara a propósito, para que la mesa siga siendo el camino del principio):** en la mesa de
crafteo, forma fija

| | | |
|---|---|---|
| lingote de acero | **lingote de netherita** | lingote de acero |
| obsidiana | **crisol de hierro** | obsidiana |
| lingote de acero | caldero | lingote de acero |

= 4 acero + 1 netherita + 2 obsidiana + 1 crisol de hierro + 1 caldero. No lleva damasco porque el damasco ya **solo** sale
de la mezcladora (sería el huevo y la gallina); el lingote de netherita (4 chatarras + 4 oro) es el freno de Nether. La
fabricadora: 4 acero + 1 crisol de hierro + 1 tolva + 2 bloques de hierro + 1 yunque (cara, pero sin Nether).

### 4.2 Reglas

| Regla | Valor |
|---|---|
| Receta en mB | Cada METAL = cantidad × 144 mB; ADITIVO = ítems en sus 4 ranuras (o 144 mB de su fluido si se funde) |
| **Salida: se conserva el volumen** | Salida = **suma de los mB de METAL** que entran (144 + 144 → 288). Los aditivos no suman. Sin bonus de ningún tipo (se quita el +1 del crisol de obsidiana) |
| **Excepción: corazón** | Recetas con corazón de forja (acero vivo, iracero, égida, arcanio): salida = **(corazones + lingotes de acero vivo de la receta) × 144 mB**; los demás metales se gastan enteros sin sumar. Así cada lingote lleva exactamente un corazón (el suelto o el que ya va dentro del acero vivo) |
| Tanda | Una tanda = la receta una vez. Al **empezar** cada tanda reserva y saca todo (fluidos y aditivos); si falta algo, no empieza |
| Cantidad | `1 / 4 / 16 / ∞` (∞ = hasta que falte algo); se para y dice por qué |
| Calor | `max(Alloys.heatAt(pos), calor del crisol encendido pegado)`; tiene que alcanzar `recipe.heat()` |
| Crisol pegado | Un crisol con combustible **calienta lo que toca** (mezcladora, fabricadora) a su tier mientras eso trabaja, y gasta ascuas a su ritmo normal |
| Tiempo por tanda | TEMPLADA 100 t · CALIENTE 160 t · FUNDIDA 240 t · FORJA_BLANCA 400 t · cumbre (`PEAK`) y oricalco 600 t; × 100 / `speed` del fluido de calor (sangre de blaze 150 → ×0,67) + el tiempo de traer el metal |
| Salida | Al almacén por la regla de llenado; si no cabe, se queda en la mezcladora (hasta 1296 mB) con "almacén lleno" |
| Faltan ingredientes | La fila sale gris con cada falta: "faltan 144 mB de damasco", "falta 1 corazón de forja (aditivo)", "necesita forja blanca (tienes fundida)", "sin almacén conectado", "conducto tapado en x y z" |
| "Alcanza para N" | `min` sobre ingredientes de `tienes / pide`, junto a cada receta lista |

### 4.3 Fraguas lejanas como mezcladoras especiales

- Se conectan a la red como una mezcladora (los conductos las alcanzan). Menú = el de la mezcladora con solo sus recetas.
- **Las dos vías (decisión de Andy):** el hogar sigue aceptando **lingotes a mano** (144 mB cada uno, para usarla en su
  ruina sin almacén) y además saca METAL del almacén por la red. Primero gasta lo del hogar, luego el almacén. ADITIVOS:
  siempre del hogar (`HEARTH = 4`).
- Misma regla de volumen y mismas recetas ajustadas (4.4) por las dos vías.
- Calor y combustible: **sin cambios** (su dimensión o su fluido por tubo). Tanda: `BATCH_TICKS = 200`.
- Salida: al almacén si está conectada; si no, como hoy (tolva debajo o encima de la fragua), en lingotes.

### 4.4 Reequilibrio por conservación de volumen

Regla de ajuste: **lo escaso por lingote se queda como hoy** (chatarra, corazón, hierro estelar, placa hueca, lágrima,
solacero, lunacero, almacero…); lo barato y renovable (acero, hierro, cobre) puede bajar algo. "Sin ajustar" = la receta
de hoy aplicando solo la conservación de volumen. Coste por lingote = entradas ÷ lingotes que salen.

| Receta | Hoy (entradas → sale) | Sin ajustar daría | **V3 (entradas → sale)** | Coste por lingote hoy → V3 |
|---|---|---|---|---|
| Bronce | 2 cobre + 1 hierro → 3 | 3 | igual | igual |
| Latón | 2 cobre + 1 oro → 3 | 3 | igual | igual |
| Peltre (mezcladora; la mesa sigue → 3) | 2 cobre + 1 ladrillo de resina → 3 | 2 | **2 cobre + 1 resina → 2** | cobre 0,67 → 1; resina 0,33 → 0,5 (algo más caro que en la mesa, adrede) |
| Acero | 2 hierro + 2 carbón → 2 | 2 | igual | igual |
| Electro | 2 oro + 1 amatista → 2 | 2 | igual | igual |
| **Damasco** | 2 acero + 1 chatarra → 1 | 2 | **1 acero + 1 chatarra → 1** | chatarra 1 → 1; acero 2 → 1 |
| **Acero estelar** | 1 acero + 1 hierro estelar → 2 | 1 (más caro) | **2 acero + 1 hierro estelar → 2** | estelar 0,5 → 0,5; acero 0,5 → 1 |
| **Obsidiacero** | 2 acero + 1 obsidiana → 1 | 2 | **1 acero + 1 obsidiana → 1** | obsidiana 1 → 1; acero 2 → 1 |
| Cinerio | 2 acero + 4 ascuas → 2 | 2 | igual | igual |
| Voltaico | 2 latón + 4 redstone + 1 amatista → 2 | 2 | igual | igual |
| Almacero | 1 acero estelar + 2 placas huecas → 1 | 1 | igual (la placa es aditivo) | igual |
| Vidriacero | 1 obsidiacero + 4 cuarzo → 1 | 1 | igual | igual |
| Solacero | 1 damasco + 3 varas de blaze → 1 | 1 | igual | igual |
| Lunacero | 1 obsidiacero + 3 ecos → 1 | 1 | igual | igual |
| Acero vivo | 1 corazón + 2 damasco → 1 | 1 (regla del corazón) | igual | igual: 1 corazón por lingote |
| **Astralita** | 2 oricalco + 1 eterio + 2 hierro estelar → 2 | 3 | **2 oricalco + 1 eterio + 4 hierro estelar → 3** | oricalco 1 → 0,67; eterio 0,5 → 0,33; estelar 1 → 1,33 (lo escaso total casi igual) |
| **Oricalco** | 1 de cada uno de 14 (11 metales + estelar, placa, escoria) → 4 | 11 | **1 de cada uno de los 11 metales + 3 hierro estelar + 3 placas huecas + 3 escorias → 11** | estelar, placa y escoria 0,25 → 0,27 (lo que lo frena); cada metal común 0,25 → 0,09 |
| Fatuo (almas) | 2 hierro + 1 chatarra + 4 tierra de almas → 2 | 2 | igual | igual |
| Magmacero (almas) | 2 acero + 4 basalto + 4 piedra negra + 2 crema de magma → 2 | 2 | igual | igual |
| Eterio (vacío) | 2 acero + 1 shulker + 4 coro + 4 piedra del End → 2 | 2 | igual | igual |
| **Espectracero** (almas) | 2 fatuo + 1 almacero + 1 lágrima → 2 | 3 | **1 fatuo + 1 almacero + 1 lágrima → 2** | almacero y lágrima 0,5 → 0,5; fatuo 1 → 0,5 |
| **Corazón de volcán** (almas) | 2 magmacero + 1 solacero + 4 magma → 2 | 3 | **1 magmacero + 1 solacero + 4 magma → 2** | solacero 0,5 → 0,5; magma 2 → 2; magmacero 1 → 0,5 |
| **Eclipse** (vacío) | 2 eterio + 1 lunacero + 2 obsidiana llorona → 2 | 3 | **1 eterio + 1 lunacero + 2 obsidiana llorona → 2** | lunacero 0,5 → 0,5; llorona 1 → 1; eterio 1 → 0,5 |
| Iracero | 1 corazón + 2 corazón de volcán + 1 acero vivo → 2 | 2 (regla del corazón) | igual | igual: 2 corazones → 2 lingotes |
| Égida | 1 corazón + 2 espectracero + 2 obsidiacero → 1 | 1 | igual | igual |
| Arcanio | 1 corazón + 2 astralita + 2 eclipse → 1 | 1 | igual | igual |

Resumen: ninguna receta sale más barata en lo escaso; damasco, obsidiacero, oricalco y las tres de la fragua de dos
aleaciones se ajustan para no multiplicarse; peltre en la mezcladora y acero estelar piden algo más de metal común.

---

## 5. Fabricadora, portacolador, moldes y calidad

### 5.1 La pila

Fabricadora → justo debajo **portacolador** → justo debajo **mesa de colada** (losa/brasa/almas). Si falta alguno: "falta
el portacolador debajo" / "falta la mesa debajo del portacolador". El chorro se ve cayendo dentro del portacolador.

| Regla | Valor |
|---|---|
| Qué cuela | Molde → pieza (`part.cost` × 144 mB); marco → herramienta entera (`CastingFrameItem.cost` × 144); **lingotera** → 1 lingote (144 mB) del material; perla de ender puesta a mano → perla de oricalco (288 mB, como hoy) |
| Material | Cualquiera del almacén que la pieza acepte (`part.accepts`, `CastingFrameItem.castable`) y que la mesa aguante (`Tier.holds`) |
| Calor | ≥ `meltHeat(material)` (TEMPLADA ≤ 320 de durabilidad, CALIENTE ≤ 700, si no FUNDIDA); mismo cálculo de calor que la mezcladora |
| Tiempo | traer el metal (caudal) + **fraguado** 100 t × malla (fina ×1,5, gruesa ×0,5, sin colador ×1) × 100 / `speed` |
| Cantidad | `1 / 4 / 16 / ∞` |
| Salida | La pieza va al **cofre de piezas** que toque la mesa o la fabricadora; si no hay, tolva debajo de la mesa; si no, se queda en la mesa y la fabricadora espera |
| Fluidos de calor | Aliento de forja tocando la fabricadora: +0,20 de perfecta en herramientas (`steadyBonus`, como hoy). Salmuera tocando la mesa: temple al agua (como hoy). Sangre de blaze: fragua ×1,5 más rápido |

### 5.2 Colador: la malla decide la calidad

El ítem `colador` gana dos datos: **malla** (`forja:malla` = FINA | GRUESA) y **desgaste** (`forja:usos`). Su material
(y `holds`) funciona como hoy; el baño en la caja de moldeo conserva la malla y renueva los usos.

| Colador | Pieza (molde) | Herramienta (marco) | Fraguado | Desgaste |
|---|---|---|---|---|
| Ninguno | basta (`ROUGH`, −15 %) | basta | ×1 | — |
| No aguanta el metal (`holds` bajo) | se rompe al empezar → basta | basta | ×1 | se pierde |
| **Fino** | limpia: `COLADA` + una mejora al 15 % (`CAST_PERCENT`), como hoy | limpia; perfecta = `luck` de la mesa **+ 0,10** | ×1,5 | 2 usos por cada 144 mB |
| **Grueso** | limpia: `COLADA`, **sin** la mejora | limpia; perfecta = `luck` **− 0,10** (mín. 0) | ×0,5 | 1 uso por cada 144 mB |
| Lingotera (cualquier colador o ninguno) | — | — | ×1 | no gasta |

- Usos: barro 48; de metal `clamp(durabilidad / 8, 48, 400)`. Al llegar a 0 se rompe **después** de la colada (esa sale
  limpia). El portacolador pone solo el siguiente de sus 4 recambios.
- Recetas: el colador de hoy es **grueso**; **fino** = colador grueso + 4 pepitas de hierro (mesa de crafteo, conserva
  material).
- Potencial (`Potential.atCasting(rough, perfect)`), `Quality.PERFECT_BONUS`, `ROUGH_PENALTY`, `COLADA/COLADAS` y la
  suerte de cada mesa: **sin cambios**.
- Colada interrumpida por falta de calor (sección 6): al reanudar, la pieza sale **basta** ("junta fría").

### 5.3 Moldes y moldero

| Regla | Valor |
|---|---|
| Moldero | 54 huecos; admite moldes, marcos y lingoteras (tolva o mano). Tiene que **tocar la fabricadora o la mesa** |
| Lista | La fabricadora lista lo que hay en el moldero + lo que hay en la mesa |
| Cómo llega | Al empezar, el molde elegido **pasa solo** del moldero a la mesa (partícula del ítem volando); al acabar la cantidad o cambiar de trabajo, vuelve. Si la mesa tiene un molde ajeno puesto a mano, primero entra en el moldero (si no cabe: "moldero lleno") |
| Lingotera | Ítem nuevo `lingotera`: 3 de acero refractario en V (mesa de crafteo). Así se sacan lingotes del almacén |
| Moldes y marcos | Se cortan en la caja de moldeo como hoy (2 y 6 de acero refractario) y **no se gastan** al colar |

---

## 6. El calor importa: parada, tapones, recalentar y limpiar

| Caso | Qué pasa |
|---|---|
| Mezcladora o fabricadora bajo el calor que pide durante **20 t seguidos** | Se **para**. Lo que hay dentro fragua dentro (no se pierde). Estado "fraguada: recaliéntala" |
| Había metal viajando | Cada bloque del camino activo, empezando junto a la máquina y como mucho 16, queda **tapado** con 16 mB de ese metal (descontados de lo que la máquina ya había recibido; si no llega, menos tapones). Estado de bloque `TAPADO=true`; el bloque guarda metal y mB (`MeltFlowBlockEntity`); se ve el metal opaco y gris en la ranura |
| Un tapón | Corta la red como una llave cerrada (invalida la caché por cambio de estado) |
| **Recalentar la máquina** | Al volver el calor, la máquina deshace sus tapones de uno en uno, **1 cada 20 t** desde ella hacia fuera; el metal vuelve a la tanda; luego sigue. Sin pérdida |
| **Calor externo** | Un tubo de calor con fluido o un bloque caliente (lava, magma, fuego, farol) pegado a un tapón con calor ≥ `meltHeat` del metal lo deshace en **100 t**; si no es de ningún trabajo, el metal va al almacén con sitio más cercano |
| **Limpiar a mano** | Mayús + clic derecho con un pico en el tapón: lo quita al instante y suelta **1 rebaba** de ese metal (16 mB). Romper el conducto tapado: suelta el conducto **y** la rebaba. **El metal no se pierde** (decisión de Andy) |
| **Rebaba** | Ítem nuevo `rebaba` con dos componentes: material y mB (`forja:rebaba` = {material, mB}); apila con la misma; tooltip "Rebaba de acero · 16 mB". El crisol la funde al **100 %** en su metal, sin mirar el tier (es metal suelto, no una pieza) |
| Fabricadora a media colada | Al reanudar, la pieza sale **basta** |
| Parar un trabajo | Solo con la máquina caliente: devuelve lo que tiene al almacén. Fraguada, "Parar" está gris |
| Crisol sin calor | Igual que hoy (no funde); lo que ya mandó está en el almacén, que no fragua |

---

## 7. Cofre de piezas

| Regla | Valor |
|---|---|
| Huecos | 108 (dos cofres); solo `PartItem` y equipo con `forja:parts`; cualquier otra cosa se rechaza |
| Entrada | Automática desde la mesa de colada o la fabricadora que toque; tolva por arriba/lados |
| Vista | Agrupada por **tipo** (`PartType` / `ForgeType`) y **material**, con cantidades y marcas (colada, basta, perfecta, mejora). Pestañas Piezas / Herramientas / Armas / Armaduras / Todo; orden por tipo o por material |
| Sacar | Clic toma uno, Mayús+clic el grupo; tolva debajo saca en el orden de la vista (para la montadora) |
| Comparador | 0–15 según lo lleno |

---

## 8. Moldero

Ver 5.3. Además: comparador 0–15; romperlo suelta todo; el molde que esté "en la mesa" sigue siendo del moldero (vuelve).

---

## 9. Recuperación de metal

El crisol funde piezas sueltas y equipo forjado. Cada parte devuelve **su propio material** (hoy todo iba como el
material de la primera parte); los materiales básicos (madera, cuero, hueso…) se pierden. Redondeo hacia abajo a 16 mB.

| Crisol | Pieza sana o sobrante | Pieza rota (`forge/BrokenGear`) | Ejemplo: pico de acero (5 lingotes = 720 mB) sano / roto |
|---|---|---|---|
| Barro | 50 % | 30 % | 352 / 208 mB |
| Hierro | 75 % | 45 % | 528 / 320 mB |
| Obsidiana | 100 % | 60 % | 720 / 432 mB |

Una pieza **basta** cuenta como sana. Sigue saliendo la ascua de cada pieza fundida (como hoy).

---

## 10. Automatización

| Bloque | Tolva entra | Tolva sale | Comparador | Redstone |
|---|---|---|---|---|
| Crisol | arriba: fundibles; lados: ascuas | — | progreso | — |
| Cuba | — | — | lleno de su almacén | — |
| Llave de paso | — | — | — | señal = cerrada (igual) |
| Mezcladora | arriba/lados: aditivos | — | 0 parada, 7 esperando, 15 trabajando | (ver 13) |
| Portacolador | lados: coladores de recambio | — | usos del activo | — |
| Mesa de colada | arriba: molde (si no hay moldero) | abajo: pieza (si no hay cofre) | — | — |
| Cofre de piezas | arriba/lados | abajo, en orden | lleno | — |
| Moldero | arriba/lados | — | lleno | — |
| Montadora | igual que hoy | igual | igual | — |

Línea sin manos (núcleo): tolva → crisol → almacén → mezcladora en ∞ → fabricadora en ∞ → cofre de piezas → tolva →
montadora. Lo que falta para cerrar del todo va en la fase POSTERIOR (sección 13).

---

## 11. Notas de equilibrio

| Punto | Hoy | V3 | ¿Más barato? |
|---|---|---|---|
| Aleaciones | N lingotes → M lingotes | **se conserva el volumen de METAL**; recetas ajustadas en 4.4 | No en lo escaso (tabla 4.4); algo menos de acero en damasco, obsidiacero y las de dos aleaciones |
| +1 lingote de obsidiana | crisol de obsidiana, por colada, salvo cumbre | **se quita** (ni crisol ni aliento) | Más caro con crisol de obsidiana (adrede: decisión de Andy) |
| Corazón de forja | 1 por lingote | aditivo entero; salida = (corazones + acero vivo) × 144 mB | Igual |
| Mena | 1 = 1 lingote | 1 = 144 mB | Igual |
| Piezas coladas | `cost` lingotes | `cost` × 144 mB | Igual |
| Recuperación | 50/75/100 % del material de la 1.ª parte | 50/75/100 % sana, 30/45/60 % rota, cada parte su material | Algo más generosa en piezas mixtas (adrede, lo pidió Andy); rota, más cara |
| Coladores | duran para siempre | se gastan; fino más | Más caro (adrede) |
| Damasco, cinerio, voltaico… en la mesa | sí (sobre lava/magma) | solo mezcladora | Más caro al principio (adrede: "las buenas por la mezcladora") |
| Tapones | — | el metal vuelve como rebaba (picar o romper) | Igual: solo cuesta tiempo |
| Receta de la mezcladora | — | 4 acero + 1 netherita + 2 obsidiana + crisol de hierro + caldero | La mesa sigue siendo el camino del principio |

Las pruebas de equilibrio (`BalanceGameTests`, `ArmaduraGameTests`) no deben moverse: los materiales no cambian.

---

## 12. Libro III (La fundición)

`GuideBooks.FUNDICION`, textos en `tools/lang_libros.py` (o el nuevo `lang_fundicion.py`), capítulos en
`client/GuideBookScreen` (`case "id" ->`, con icono).

| Sección | Capítulos ahora | Capítulos V3 |
|---|---|---|
| `fundicion_calor` | `fundicion_sabes`, `primeras_aleaciones` | `fundicion_sabes` (reescrito: 144/16/1296 mB), `primeras_aleaciones` (solo `STAR`; "las buenas, en la mezcladora") |
| `fundicion_linea` | `fundicion` | `fundicion_crisol` (crisol + qué se funde + recuperación), `fundicion_almacen` (cubas, panel, conductos y caudal), `fundicion_mezcladora` (lista, faltas, el volumen se conserva, aditivos que no suman, regla del corazón), `fundicion_fabricadora` (pila, moldero, lingotera, cofre), `fundicion_colador` (fino/grueso, desgaste, basta), `fundicion_tapones` (calor, tapones, recalentar, picar) |
| `fundicion_mayor` | `mesa_mayor`, `aleaciones_lejanas`, `aleaciones_cumbre`, `fundicion_siguiente` | igual, con `aleaciones_lejanas` (fraguas en la red) y `aleaciones_cumbre` (mezcladora a forja blanca, corazón como aditivo) reescritos |

Se quita el capítulo `fundicion`. También hay que repasar los textos de `tools/lang_aleaciones.py` y `lang_cumbre.py` que
dicen "crisol de obsidiana en una línea" o "en una cuba".

---

## 13. Fase POSTERIOR (breve, última tanda)

| Extra | Diseño |
|---|---|
| Cola de trabajos | Mezcladora y fabricadora: lista de hasta 8 trabajos (receta/pieza + material + cantidad); se hacen en orden, el que no puede se salta con aviso |
| Conductos y chorros del color del metal | `MeltFlowRenderer` tiñe la ranura con `material.color`; el chorro de la fabricadora y las capas de la cuba, del mismo color; tapón = color oscurecido |
| Moldes mejores, menos metal perdido | Toda colada deja **rebaba** (el ítem `rebaba` del núcleo, sección 6): molde de acero refractario 10 % del coste, de damasco 5 %, de vidriacero 0 %. El metal no se pierde, se pierde tiempo; los moldes mejores se cortan en la caja de moldeo de damasco |
| Redstone y comparadores | Panel: elige un metal → comparador 0–15 por lingotes. Mezcladora y fabricadora: señal = pausa; comparador por estado. Fabricadora "mantener N en el cofre" (cuela solo si hay menos) |
| Tolvas por todas partes | Cofre de piezas pegado a la montadora le pasa las piezas que pide su marco; la montadora deja lo montado en el cofre pegado |
| Sonidos y partículas | `ModSounds`: mezcla burbujeando, chorro, tapón que cruje, tapón que se deshace, colador que se rompe; partículas de metal en el chorro y vapor al recalentar |

---

## 14. Tandas para el implementador (Sonnet)

Cada tanda compila, pasa `runGametest` entero y se puede fusionar sola. Cada una: sus assets por `tools/fundicion_v3.py`,
sus textos por `tools/lang_fundicion.py`, su entrada en `CHANGELOG.md`, y registrar sus pruebas en
`src/gametest/resources/fabric.mod.json`. Las pruebas viejas que se rompan se **reescriben** al nuevo modelo, no se borran
sin sustituto.

| Tanda | Contenido | Pruebas (`T/`) |
|---|---|---|
| **1. Metal en mB, almacén, crisol y mezcladora** | `Mb`; `MetalStore` multifluido (sustituye `MeltDeposit`), cuba y su renderer por capas, ítem de cuba con su parte; panel; caudal y presupuesto de calor en conductos; crisol reescrito (huecos por tier 3/6/9, cualquier metal, sin salida, sin aleaciones, recuperación nueva, calienta lo que toca); `Alloys.STAR`, `match` de la mesa, fuera `FOUNDRY_ONLY`/`WHITE_HEAT_ONLY`; `MixerRecipes` + mezcladora (bloque, entidad, menú, pantalla); mesa de colada y caja de moldeo adaptadas a sacar mB (`cost × 144`) para que colar siga funcionando; quitar el caño y el colador puesto **todavía no** | `AlmacenGameTests`: `unidadesYTexto` ("1872 mB (13 lingotes)"), `cubasPegadasVariosMetales`, `llenaPrimeroElMismoMetal`, `cubaRotaGuardaSuParte`, `panelSumaDosAlmacenes`, `comparadorDeCuba`, `bronceLargoNoLlega`, `tuboDeCalorAlargaElConducto`, `redNoSeRecorreCadaTick`. `CrisolV3GameTests`: `fundeCualquierMetal`, `esperaSinAlmacen`, `rechazaAditivos`, `recuperacionPorTier`, `piezaMixtaCadaMaterial`, `rotaDevuelveMenos`. `MezcladoraGameTests`: `bronceDeMb`, `cadaAleacionTieneQuienLaHaga` (mesa, mezcladora o fragua), `faltaDiceCuanto`, `calorInsuficienteNoEmpieza`, `seConservaElVolumen` (144 + 144 → 288), `aditivosNoSuman`, `sinBonusNiConObsidiana`, `reglaDelCorazon` (acero vivo 1, iracero 2, égida 1, arcanio 1), `recetasAjustadas` (cada fila de 4.4), `aditivoFundidoVale`, `recetaDeLaMezcladora`, `cantidadInfinitaParaAlFaltar`, `salidaEsperaAlmacenLleno`. Reescribir: `FundicionGameTests`, `CalorGameTests`, `OricalcoGameTests`, `CumbreGameTests`, `MesaGameTests`, `MaterialesGameTests.everyAlloyCanBePoured` |
| **2. Fabricadora, portacolador, moldero, lingotera** | Fabricadora (bloque, entidad, menú, pantalla); portacolador (sustituye `StrainerBlock`, se quita el bloque `colador`); colador con malla y usos, receta del fino; mesa de colada sin calor ni cubas; moldero; lingotera; se quita `cano_de_colada` y la lógica de caídas (`DROP`, `FALL_BLEED`) | `FabricadoraGameTests`: `pilaIncompletaAvisa`, `cuelaPiezaDelMolde`, `cuelaHerramientaDelMarco`, `lingoteraSacaLingotes`, `finoDaMejora`, `gruesoMasRapidoSinMejora`, `sinColadorSaleBasta`, `coladorQueNoAguantaSeRompe`, `coladorSeGastaYSeCambia`, `mesaLimitaElMaterial`, `moldeVaYVuelveDelMoldero`, `faltaMetalDiceCuanto`, `perlaDeOricalco` |
| **3. Calor: parada, tapones; fraguas lejanas en la red** | Parada a los 20 t, tapones (estado, BE, render), deshacer al recalentar y por calor externo, picar con pico, colada interrumpida = basta; `FarForgeBlockEntity` como mezcladora especial (menú, mB del almacén, salida a la red) | `TaponesGameTests`: `sinCalorSeParaYTapa`, `maximoDieciseisTapones`, `taponCortaLaRed`, `recalentarDeshaceUnoPorSegundo`, `tuboDeCalorDeshaceTapon`, `picarDaRebaba`, `romperTapadoDaRebaba`, `rebabaSeFundeEntera`, `coladaInterrumpidaSaleBasta`, `pararSoloEnCaliente`. Reescribir `FraguasLejanasGameTests` (+ `fraguaSacaDelAlmacen`, `fraguaSinRedComoAntes` (lingotes a mano), `fraguaConservaVolumen`) |
| **4. Cofre de piezas, automatización, integración** | Cofre de piezas (bloque, entidad, menú agrupado); salidas automáticas; comparadores de la sección 10; JEI (categorías mezcladora y fabricadora; quitar las del crisol de aleaciones) y Jade; `/forja fundicion` (`command/FoundryDemo`) con la línea nueva; castillo (`tools/castillo*.py`: cambiar `cano_de_colada`/colador por la pila nueva, regenerar `.nbt`); `ForjaPath` si nombra bloques quitados | `CofrePiezasGameTests`: `soloPiezasYEquipo`, `recibeDeLaMesa`, `vistaAgrupada`, `tolvaSacaEnOrden`. `LineaSinManosGameTests`: `menaAPiezaSinTocar` (tolva→crisol→almacén→mezcladora ∞→fabricadora ∞→cofre→montadora). Reescribir `MontadoraGameTests` y lo que falle de `PathGameTests`/`PortalGameTests`/`CombatGameTests`/`MangosGameTests` |
| **5. Libro III y grabación** | Capítulos de la sección 12; `LibrosGameTests` al día; sección de cliente `FundicionV3Footage` + hoja (sección 16); `CumbreFootage`/`VideoVisualFootage` sin bloques quitados | `LibrosGameTests` (capítulos nuevos existen y tienen texto); grabación de cliente |
| (6. Posterior) | Sección 13, cuando Andy lo pida | Una prueba por extra |

---

## 15. Lista exacta de archivos

`J` = `src/main/java/dev/forja`, `T` = `src/gametest/java/dev/forja/test`. Tanda entre paréntesis.

| Archivo | Acción |
|---|---|
| `J/forge/Mb.java` | NUEVO (1): unidades y texto |
| `J/forge/MixerRecipes.java` | NUEVO (1): receta → fluidos mB + aditivos + salida + tiempo; `form(Part)` |
| `J/forge/Recovery.java` | NUEVO (1): % por tier, rota, mB por parte |
| `J/forge/Alloys.java` | CAMBIA (1): `STAR`; `match` solo `STAR`; fuera `FOUNDRY_ONLY`, `WHITE_HEAT_ONLY` y `NO_TIER_BONUS` (ya no hay bonus; la regla del corazón vive en `MixerRecipes`); recetas ajustadas de 4.4 en `ALL`/`EXTRA` |
| `J/block/entity/MetalStore.java` | NUEVO (1), sustituye a `MeltDeposit.java` (BORRAR) |
| `J/block/entity/MeltTankBlockEntity.java`, `J/block/MeltTankBlock.java` | CAMBIAN (1): parte multifluido, sin calor, ítem con su parte, abre el panel |
| `J/block/entity/MeltNetwork.java` | CAMBIA (1–3): almacenes multifluido, camino con caudal/pérdida, tapones como cortes, sin caños |
| `J/block/entity/MeltTransfer.java` | NUEVO (1): un traslado en marcha (camino, caudal, mB en viaje); (3) tapones |
| `J/block/MeltPipeBlock.java`, `J/block/entity/MeltFlowBlockEntity.java` | CAMBIAN (1) caudal por grado; (3) `TAPADO`, metal del tapón, picar |
| `J/block/MeltSpoutBlock.java` | BORRAR (2) |
| `J/block/CrucibleBlock.java`, `J/block/entity/CrucibleBlockEntity.java`, `J/menu/CrucibleMenu.java`, `J/client/CrucibleScreen.java`, `J/client/CrucibleRenderer.java` | CAMBIAN (1): huecos por tier, sin aleaciones ni salida, recuperación, calor a vecinos |
| `J/block/MixerBlock.java`, `J/block/entity/MixerBlockEntity.java`, `J/menu/MixerMenu.java`, `J/client/MixerScreen.java` | NUEVOS (1) |
| `J/menu/StorePanelMenu.java`, `J/client/StorePanelScreen.java` | NUEVOS (1) |
| `J/block/FabricatorBlock.java`, `J/block/entity/FabricatorBlockEntity.java`, `J/menu/FabricatorMenu.java`, `J/client/FabricatorScreen.java` | NUEVOS (2) |
| `J/block/StrainerHolderBlock.java`, `J/block/entity/StrainerHolderBlockEntity.java`, `J/client/StrainerHolderRenderer.java` | NUEVOS (2) |
| `J/block/StrainerBlock.java`, `J/block/entity/StrainerBlockEntity.java` | BORRAR (2) |
| `J/item/StrainerItem.java` | CAMBIA (2): malla, usos, tooltip |
| `J/item/IngotMouldItem.java` (lingotera) | NUEVO (2) |
| `J/item/ScrapItem.java` (rebaba) | NUEVO (3): componente material + mB; el crisol la funde al 100 % |
| `J/block/CastingTableBlock.java`, `J/block/entity/CastingTableBlockEntity.java`, `J/client/CastingTableRenderer.java` | CAMBIAN (1) mB; (2) sin calor ni cubas, recibe de la fabricadora |
| `J/block/MouldStoreBlock.java`, `J/block/entity/MouldStoreBlockEntity.java`, `J/menu/MouldStoreMenu.java`, `J/client/MouldStoreScreen.java` | NUEVOS (2) |
| `J/block/entity/CastingBoxBlockEntity.java` | CAMBIA (1): baño en mB |
| `J/block/FarForgeBlock.java`, `J/block/entity/FarForgeBlockEntity.java` | CAMBIAN (3): en la red, menú de mezcladora |
| `J/block/PartsChestBlock.java`, `J/block/entity/PartsChestBlockEntity.java`, `J/menu/PartsChestMenu.java`, `J/client/PartsChestScreen.java` | NUEVOS (4) |
| `J/forge/HeatSources.java`, `J/forge/HeatConsumer.java` | CAMBIAN (1–3): mezcladora, fabricadora y tapones como consumidores |
| `J/menu/ForgeMenu.java` | CAMBIA (1): la estrella solo `STAR` |
| `J/registry/ModBlocks.java`, `ModItems.java`, `ModMenus.java`, `ModComponents.java` (`metal_cuba`, `malla`, `usos`), `J/block/entity/ModBlockEntities.java` | CAMBIAN (1–4) |
| `J/client/ForjaClient.java`, `J/client/MeltTankRenderer.java`, `J/client/MeltFlowRenderer.java` | CAMBIAN (1–3): pantallas, capas, tapones |
| `J/compat/ForjaJeiPlugin.java`, `J/compat/ForjaJadePlugin.java` | CAMBIAN (4) |
| `J/command/FoundryDemo.java`, `J/ForjaPath.java` | CAMBIAN (4) |
| `J/GuideBooks.java`, `J/client/GuideBookScreen.java` | CAMBIAN (5) |
| `tools/fundicion_v3.py` | NUEVO (1–4): texturas, modelos, blockstates, recetas, loot, tags de los bloques/ítems nuevos; lo llama `generate_assets.py` al final (como `aleacion_nether`) |
| `tools/generate_assets.py` | CAMBIA (1): `import fundicion_v3`; quitar assets del caño y del colador puesto (2) |
| `tools/visual_gui.py` | CAMBIA (1–4): fondos de mezcladora, fabricadora, panel, cofre, moldero, crisol nuevo |
| `tools/lang_fundicion.py` | NUEVO (1–5): `FUNDICION_V3` (clave → (es, en)); `tools/generate_lang.py` lo mezcla (como `lang_cumbre`) |
| `tools/lang_libros.py`, `tools/lang_aleaciones.py`, `tools/lang_cumbre.py` | CAMBIAN (5) |
| `tools/castillo*.py` (los que nombran `cano_de_colada`, `colador`, `mesa_de_*`) | CAMBIAN (4) + regenerar `.nbt` |
| `tools/hoja_fundicion_v3.py` | NUEVO (5) |
| `T/AlmacenGameTests`, `CrisolV3GameTests`, `MezcladoraGameTests` (1), `FabricadoraGameTests` (2), `TaponesGameTests` (3), `CofrePiezasGameTests`, `LineaSinManosGameTests` (4), `FundicionV3Footage` (5) | NUEVOS |
| `T/FundicionGameTests`, `CalorGameTests`, `OricalcoGameTests`, `CumbreGameTests`, `MesaGameTests`, `MaterialesGameTests`, `FraguasLejanasGameTests`, `MontadoraGameTests`, `CumbreFootage`, `VideoVisualFootage`, `ForjaClientTest` (rama `fundicion_v3`) | CAMBIAN |
| `src/gametest/resources/fabric.mod.json` | CAMBIA (1–5) |
| `CHANGELOG.md`, `docs/FUNDICION_V2.md` (nota arriba: "superado por V3 en el metal") | CAMBIAN |

---

## 16. Texturas y modelos (generador)

Todo por `tools/fundicion_v3.py` con `escritura_estable` (una pasada limpia no cambia nada). Colores de metal: los de
`MATERIAL_COLORS`/`ALLOY_COLORS` de `generate_assets.py`.

| Bloque/ítem | Aspecto |
|---|---|
| Mezcladora | Tambor de cobre con aros de hierro y una pala que asoma; frente con mirilla que se tiñe del metal que mezcla (renderer) |
| Fabricadora | Prensa de acero con tolva arriba y boquilla abajo; luz de brasa cuando cuela |
| Portacolador | Aro de hierro sobre cuatro patas, el colador dentro dibujado por el renderer con el color de su material; malla fina = rejilla densa, gruesa = abierta |
| Cofre de piezas | Cofre de roble reforzado con herrajes y una cabeza de pico grabada en la tapa |
| Moldero | Estantería de hierro con ranuras; muestra hasta 6 moldes por el renderer |
| Cuba | La de hoy; el renderer dibuja capas de colores |
| Conducto tapado | Overlay de metal fraguado (gris con vetas del color del metal) |
| Ítems | Colador fino/grueso (dos rejillas), lingotera (bandeja de acero refractario con hueco de lingote) |
| GUI | `visual_gui.py`: fondos con el estilo de `ForjaUi` (barras de metal, filas grises para lo que falta) |

---

## 17. Grabación de cliente y hoja

`T/FundicionV3Footage.java` (modelo `OricalcoFootage`), rama `"fundicion_v3"` en `ForjaClientTest`, ejecutar con
`FORJA_SOLO=fundicion_v3 ./gradlew runClientGameTest --init-script E:/IA/lora/datos/mc_vulkan.init.gradle`. Con clics
reales en los menús.

| Captura | Qué se ve |
|---|---|
| `fundicion_v3_01_linea.png` | La línea entera de lado: crisol, cubas, mezcladora, pila de fabricadora, cofre |
| `fundicion_v3_02_crisol.png` | Menú del crisol con mena, lingotes y una pieza rota |
| `fundicion_v3_03_panel.png` | Panel del almacén con 4+ metales |
| `fundicion_v3_04_mezcladora_lista.png` | Lista con filas listas y grises ("faltan 144 mB de damasco") |
| `fundicion_v3_05_mezcladora_trabajando.png` | Progreso y cuba subiendo de nivel |
| `fundicion_v3_06_fabricadora.png` | Menú: pieza, material, cantidad |
| `fundicion_v3_07_chorro.png` | El chorro cayendo por el portacolador al molde |
| `fundicion_v3_08_cofre.png` | Cofre de piezas agrupado |
| `fundicion_v3_09_moldero.png` | Moldero y molde volando a la mesa |
| `fundicion_v3_10_tapon.png` | Conducto tapado y el aviso en la máquina |
| `fundicion_v3_11_fragua_lejana.png` | Fragua de almas conectada a la red |
| `fundicion_v3_12_libro.png` | Capítulo nuevo del libro III |

Hoja: `tools/hoja_fundicion_v3.py` (copia de `hoja_oricalco.py`) junta las capturas en
`E:\IA\Claude\Forja_capturas_mejoras\fundicion_v3\hoja_fundicion_v3.png` y copia allí cada captura.

---

## 18. Decisiones de Andy (2026-10-01)

1. La cuba rota **guarda su metal**, como una caja de shulker (3.1).
2. **Ningún lingote de bonus.** La mezcla **conserva el volumen de METAL** (144 + 144 → 288); los aditivos se gastan sin
   sumar; las recetas con corazón dan un lingote por corazón; las recetas que se abarataban se ajustan (4.2, 4.4).
3. Las aleaciones buenas, **solo en la mezcladora**; la estrella guarda las básicas. La mezcladora es cara de hacer
   (4 acero + netherita + 2 obsidiana + crisol de hierro + caldero, 4.1).
4. Picar o romper un tapón **devuelve su metal** como rebaba (6).
5. Las fraguas lejanas siguen aceptando **lingotes a mano** en el hogar, además de la red (4.3).
