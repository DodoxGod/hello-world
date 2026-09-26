# Rediseño gráfico — lista de Andy (2026-09-18)

Revisión de las seis láminas de contacto de todo lo que dibuja el mod. Esto es el pendiente; se va
tachando conforme se aprueba cada diseño. **Nada de esto se toca sin que Andy elija antes entre
propuestas.**

## En marcha

- [x] **Mangual** — HECHO. La textura que aprobaste, cortada en sus tres capas (bola 0, cadena 1,
      mango 2) para que cada parte se tiña con su material. La pieza **bola** ya no es una bola
      dibujada a mano: es la cabeza del arma recortada, así que la pieza y el arma coinciden.
- [x] **Guanteletes** — HECHOS, con la foto de los mitones que mandó Andy, y **partidos en tres
      piezas** a petición suya: **manopla** (HANDLE, el mitón de cuero), **nudillos** (HEAD, la banda
      de metal) y **remache** (EXTRA, los cuatro remaches). Se tiñen por separado, así que se puede
      poner buen acero sobre un guante barato o remachar una banda pobre con oro y se nota.
      *Aprendido*: la primera tanda salieron cajas. Lo que hace que una mano se lea a 16 px no es el
      acolchado — son **nudillos que escalonan**, un **lóbulo de pulgar** rompiendo un lado y una
      **muñeca que se estrecha**. Un rectángulo redondeado con bandas es una caja a cualquier tamaño.
- [x] **Gancho** — HECHO (diseño C2). Era una **V** con un palo debajo y parecía un tirachinas. Ahora
      va al revés, **como un ancla colgando**: rezón de cuatro brazos curvándose hacia arriba, una
      **anilla** donde la cadena se engrilleta al vástago, la cadena subiendo y el palo en diagonal
      arriba. La pieza **garfio** se recorta del arma, como el resto de las piezas del mod.
      *De dónde sale cada cosa*: la cadena es `item/iron_chain` **recta y sin tocar** — puesta en
      diagonal como un pico había que ir pegándola eslabón a eslabón y se volvía un borrón donde
      cruzaba el palo; y el mango es `item/stick`, que ya viene dibujado en diagonal.
      *Y un detalle que no se ve venir*: Minecraft dibuja la cadena **muy oscura** (todo el sprite vive
      entre un quinto y un tercio del blanco) porque en vanilla nunca se tiñe. La nuestra se multiplica
      por el material de la cuerda, así que tal cual salía casi negra fuese del metal que fuese: hay
      que estirarle los niveles al entrar.

## Objetos

- [x] **Lingotes de aleación** — HECHO. Forma del lingote de Minecraft, con una **marca de superficie**
      por metal (acero ya no se confunde con hierro). Bronce y latón lisos a propósito. Almacero y acero
      estelar llevan **degradado de izquierda a derecha**; lunacero, degradado con la luz. Peltre cambió
      de color porque era el mismo gris que el acero. Los colores nuevos van también a `ForgeMaterial`,
      así que las piezas y la colada de esos metales salen del mismo color que su lingote.
- [x] **Grieta** — HECHA. Era un rayón diagonal con tres píxeles rojos al lado. Ahora es una rotura
      corta y centrada: núcleo negro con **filo claro a un lado** (la cara fresca del metal partido) y
      una sola bifurcación. Se quedó corta a propósito — la capa no sabe qué hay debajo, así que todo
      lo que se va a las esquinas cae sobre vacío y parece suciedad alrededor del icono.
- [x] **Huevos de generación** (4) — HECHOS. Eran una elipse sacada de una ecuación con un borde plano
      y puntos tirados al azar. Ahora toman la **silueta y la luz de un huevo de Minecraft de verdad**
      (el de zombi, reducido a dónde están sus píxeles y qué tan claro es cada uno) y se repintan con
      los dos colores del mob, con las manchas en grupitos en vez de píxeles sueltos. Al lado de uno
      vanilla en la misma pestaña ya no cantan.
- [x] **Marcos y moldes** — HECHO. Uno por pieza (32) y uno por herramienta (29), con la forma de lo que
      crean hundida en la placa de acero y un labio iluminado, igual que las plantillas. Arco, ballesta,
      caña y escudo no tienen capas numeradas, así que su silueta se lee de sus propios sprites.
- [x] **Kit de reparación** — QUITADO. Clase, objeto, receta, textura, comercio, botín y traducción.
- [x] **Lingote de temple** — HECHO. Se **cola en el crisol** (acero refractario + 2 de arcilla, calor
      CALIENTE, salen 2). Clic derecho sobre equipo forjado en el inventario y lo repara **con el metal
      de su cabeza**: devuelve un cuarto de lo que vale ese material por sí solo, con un suelo de 25
      puntos. Una cabeza de diamante recupera 390; una de madera, 25. Es lo contrario del kit viejo,
      que daba un 15 % plano y por tanto premiaba tener buen material *menos* cuanto mejor era.
      **Supuesto**: "que se haga en la forja" lo leí como el crisol, que es donde nace cada lingote del
      mod. Si querías la mesa de forja, se cambia en una línea.

## Reglas de juego cambiadas (2026-09-18)

- [x] **Dos mesas de forja.** El banco (`mesa_de_forja`) monta **14 tipos**: los cinco de herramienta,
      espada, daga, flecha, caña, arco y las cuatro de armadura. La **mesa de forja mayor**
      (`mesa_de_forja_mayor`, piedra negra + oro + damasco sobre la otra) monta **los 27**, o sea los 13
      que el banco rechaza: martillo, picahacha, espadón, lanza, tridente, mazo, guadaña, mangual,
      guanteletes, ballesta, escudo, gancho y alas. La barda y la armadura de lobo siguen siendo **sólo
      de la talabartería**. La regla vive en `Station.BENCH` y la pantalla dice cuál falta.

- [x] **El metal ya no se corta en la mesa de piezas.** Sólo 12 materiales básicos: madera, piedra,
      hueso, cuero, amatista, prismarina, vara de blaze, cuarzo, púrpur, eco, resina y escama. Todo lo
      demás —metales, aleaciones, diamante, esmeralda y las dos obsidianas— hay que **colarlo**. La regla
      vive en `ForgeMaterial.BASIC` y la aplica `Assembler.partResult`, que es el único sitio donde una
      mesa decide que puede cortar algo.
- [x] **Una colada limpia da una mejora.** La caja de moldeo pone una mejora al 15 % en la pieza si el
      colador aguantó; una colada basta no da nada. La mejora **sube a lo que montes con la pieza**
      (`Assembler.evaluate` las junta, quedándose con la mejor de cada una en vez de sumarlas).
- [x] **Colador** — HECHO. Ya no es una losa de barro con puntos: es **una reja de verdad**, hecha con
      geometría (marco + cuatro barras, nueve huecos) y no con un sprite plano, así que se ve en 3D en
      la mano y en el inventario. Además **se tiñe del metal que lo atravesó**, que es lo que el objeto
      dice que hace desde siempre y nunca se veía: un colador de damasco ahora parece damasco.
- [x] **Frasco → jarra de cristal** — HECHA, y con mecánica nueva. El objeto se llama `jarra` y está
      copiado de la foto que mandó Andy: **más alta que ancha**, vidrio **estriado** (costillas
      verticales de arriba a abajo), tapa clara encima y **sin asa**. Las estrías se ven también a
      través de lo que lleva dentro.
      Antes el frasco se convertía en orbe en el mismo clic, así que **nunca contenía nada** y no había
      color que enseñar. Ahora son dos pasos: la jarra atrapa la noche y la guarda (se llama "Jarra de
      aurora", con su color), y al vaciarla sale el orbe de siempre. Un estante de jarras ya se lee
      como las noches en que estuviste fuera — la documentación del propio `WorldEvents` ya describía
      este diseño de dos pasos.
- [x] **Yunque portátil** — HECHO. Estaba dibujado de frente y parecía un estante con una tabla
      encima. Ahora va **de perfil**, que es el único ángulo desde el que un yunque se reconoce a 16
      píxeles: cuerno, cara, cintura y pie. La correa cruzada y su hebilla son lo que lo hacen
      portátil.
- [x] **Sello y marco de sello** — HECHOS, segunda versión. Eran una esfera gris dentro de un anillo de
      oro, que es exactamente lo que también eran el talismán y el orbe: tres objetos distintos que se
      leían como uno. Probé cera con cinta y Andy pidió **círculo de runas + contorno**: campo casi
      negro, **un anillo** brillante, **ocho marcas** alrededor y una estrella encendida en el centro,
      todo teñido con el color del don; el **contorno de oro** va aparte y no se tiñe.
      *Dos cosas aprendidas a 16 px*: cuatro anillos concéntricos en un disco de doce píxeles se tocan
      entre sí y todo queda pálido; y un círculo trazado "probando cada píxel contra un radio" sale
      cuadrado en los polos — hay que recorrer la circunferencia por ángulo.
- [x] **Talismanes** — HECHOS, segunda versión. Andy pidió **esfera con líneas de oro, como si
      contuviera mucho poder**. La piedra está iluminada **desde dentro** —corazón que se quema a
      blanco y venas saliendo hacia la superficie— con la luz normal encima (lado iluminado,
      terminador, luz de rebote en el canto en sombra) para que siga leyéndose como esfera. El oro va
      en su propia capa sin teñir: **dos meridianos y un anillo de cintura** que cierra por detrás (el
      arco de atrás también se dibuja, apagado, que es lo que hace que el anillo *rodee* la bola).
      *Aprendido*: el meridiano obvio pasa por el centro de la bola, que es justo donde está el
      corazón, así que la luz acababa detrás de una barra de oro. Van **descentrados** a propósito.
- [x] **Bloques rehechos.** Dirección elegida: **A (piedra oscura y hierro)** para los bancos y
      **C (piedra y brasa)** para lo que quema. Los dos comparten el mismo kit —material propio,
      herraje arriba y abajo, y una cara con algo que se pueda nombrar— para que el taller entero se
      lea como un solo mod.
  - [x] **Bancos (A)**: mesa de forja (yunque hundido + cajón), mesa de forja mayor (piedra negra, oro,
        dos yunques), mesa de piezas (lecho de corte + rueda de afilar, en cobre), talabartería (cuero
        cosido + pomo de silla, en latón) y armario de piezas (tres cajones).
  - [x] **Crisoles (C)**: el tope es un cuenco hundido en anillos y el costado lleva **boca de fuego con
        reja**; apagados son carbón muerto, encendidos sale el fuego. Barro = soga y grieta, hierro =
        chapa con costura y remaches, obsidiana = facetas con flejes de oro.
  - [x] **Cajas de moldeo (C)**: ahora son un molde de arena de verdad — copa de colada, bebedero,
        cavidad de la pieza y respiradero arriba; línea de partición, mordazas y asas al costado.
  - [x] **Farol de pavesa**: jaula con barrotes y la pavesa **flotando** detrás, en vez de un muro de
        fuego con rayas encima.
  - [x] **Fragua apagada**: el mismo cuenco de los crisoles, lleno de ceniza y carbón muerto, con una
        brasa que no se apagó y la boca fría abajo.
  - [x] **Cuba de colada**: bronce y vidrio, que es de lo que está hecha por receta.
  - [x] **Yunque del herrero**: ya **no es un cubo**. Modelo 3D de cuatro cajas (pie, cintura y cara),
        con collar de oro, agujero cuadrado y punzonera en la cara, y **gira** según cómo lo pongas
        (`SmithAnvilBlock`, con su propia forma de colisión).

## Mobs

- [x] **Pavesa** — MEJORADA. Las alas eran dos tablones planos clavados a un farol; ahora van en
      **tres tramos**, cada uno más estrecho, menos profundo y un poco más alto que el anterior, que es
      lo que convierte una tabla en un ala. Las puntas tienen **color propio** (más claro y más vivo
      que el carbón del cuerpo: un fuego es más brillante donde es más delgado) y la jaula termina en
      **punta** por debajo en vez de en caja plana.

## Aprobado, no tocar

- Las **tres mesas de colada** (losa, brasa, almas).
- Las **plantillas**: son todas la misma placa con un hueco y está bien así.
- **Herrero caído, autómata, coraza vacía** y los enemigos vanilla con goals propios.
