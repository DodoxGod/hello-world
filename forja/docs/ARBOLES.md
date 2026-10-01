# Árboles de clase — diseño (paso 1, para que Andy lo revise)

Los árboles pequeños de hoy (3 ramas × 3 niveles y la habilidad B, en `clase/Talent.java`) se sustituyen por **un
árbol grande por clase**, con el esquema que aprobó Andy. Este documento es la propuesta: **todavía no hay código de
juego**. Los nodos, con todos sus números, están en **`docs/arboles.json`**, que genera `tools/arboles.py`. El paso de
construcción generará el Java desde ese archivo, igual que hoy `Talent.numbers` alimenta a la vez el texto y el código.
Las tablas de nodos y los cálculos de este documento también los escribe el script (entre las marcas `<!-- ... -->`):
no se editan a mano.

Imágenes de los dos árboles completos, dibujadas desde el JSON, con una partida de ejemplo de 43 puntos en dorado:
`E:\IA\Claude\Forja_capturas_mejoras\arbol\arbol_guerrero.png` y `arbol_mago.png`.

## Lo que pidió Andy

1. Un árbol de unos 80-100 nodos **por clase**:
   - un núcleo de 6 nodos;
   - 3 ramas: cada una con un tronco de 4 nodos que acaba en un notable y se parte en dos caminos de 5 (notable a la
     mitad y **clave** al final), con nodos laterales;
   - 3 sendas entre las ramas, con un notable, que acaban en un **puente** a otra clase, donde los nodos cuestan más;
   - nodos menores (+3 %), notables (un efecto con carácter) y claves (cambian la forma de jugar y tienen precio);
   - solo se coge un nodo pegado a otro que ya tienes;
   - unos 40-45 puntos en el tope: un poco menos de la mitad del árbol.
2. Puntos: 1 por nivel de clase **más hitos** (logros, jefes, el primer campeón...).
3. Quitar o rehacer el **Herrero** y meter las habilidades de forja en los árboles.
4. Los puentes se quedan.
5. Para reiniciar sigue el Medallón del olvido; quizá un objeto más barato.
6. Las habilidades V y B se mejoran desde el árbol («Grito de guerra II»).
7. *(añadido)* **Tres habilidades por clase**, no dos: una tercera tecla junto a V y B, que se cambia en Controles. La
   tercera se abre y se mejora desde el árbol, en un sitio hondo.

## Forma del árbol

Es igual en las seis clases: 97 nodos cada una, contando los 13 de la forja.

```
                   clave              clave
                     |  (2 laterales)   |
                  camino 1          camino 2      cada camino: menor, menor, NOTABLE, menor, CLAVE
                         \          /
                          notable                 (fin del tronco)
                             |
                          tronco ×3               RAMA A (arriba)
                             |
  senda 3 → puente    forja – PUERTA A – forja    senda 1 → puente
                 puerta       |        puerta
                      \    ORIGEN     /           el origen es la clase y su habilidad V
                 puerta       |        puerta
     RAMA C (abajo izq.)  forja – puerta – forja  RAMA B (abajo dcha.)
                             |
                     senda 2 → puente (abajo)
```

- **Origen** (gratis): la clase misma, sus números de base y la habilidad I (V).
- **Núcleo**: 6 puertas de 1 punto alrededor del origen. Todas tocan el origen y se tocan entre sí en anillo. Cada
  puerta abre una dirección: las ramas A (arriba), B (abajo a la derecha) y C (abajo a la izquierda), y entre ellas
  las sendas 1, 2 y 3.
- **Región de la forja**: 13 nodos naranjas, iguales en todas las clases, en los huecos entre las puertas.
- **Ramas** (18 nodos):
  - tronco de 3 menores y un notable;
  - luego dos caminos de 5 (menor, menor, notable, menor, clave);
  - 2 laterales por camino, colgados del 2.º y del 4.º nodo.
- **Sendas** (7 u 8 nodos): menor, notable, menor, menor; luego el puente y dos nodos de la otra clase. Los notables de
  las sendas son **las habilidades**:
  - la senda 1 lleva V II;
  - la 2 lleva la habilidad B y, al lado, B II;
  - la 3 lleva un notable propio y, al final, la **habilidad N** (la tercera), con N II al lado.
- Solo se aprende un nodo pegado a uno que ya tienes (el origen cuenta). Para quitar un nodo, no puede dejar a otro
  suelto (es lo que hace la Vela, más abajo).

### Costes

| Tipo | Puntos | Qué es |
|---|---|---|
| Puerta del núcleo, menor, notable, forja | 1 | menor: +3 % de una estadística (equivalencias abajo) |
| Clave | 2 | cambia la forma de jugar, con precio |
| Habilidad B, V II, B II, N II, Temple de campaña y Temple II | 2 | |
| Habilidad N (la tercera) | 3 | la más honda: a 7 puntos del origen |
| Puente y nodos de otra clase | 2 cada uno | dan ~2,5 % por punto; los de la clase, 3 % |

**Equivalencias del menor (+3 %)** para lo que no es un porcentaje:

- armadura +1;
- dureza +0,5;
- ventana de parada +1 tick;
- resistencia al empuje +5 % (es un valor de 0 a 1, no un porcentaje del base);
- regeneración de maná +0,2: un +20 % del ritmo sin clase, así que en el Mago el ×6 pasa a ×6,2.

La invulnerabilidad de esquiva solo la dan los notables.

El árbol entero cuesta 119 puntos. Con 43 se gasta el **36 %** de los puntos, unos 35 nodos de 97. Es algo menos que
«un poco menos de la mitad»: si Andy lo quiere más cerca de la mitad, basta con bajar las claves y los puentes a 1
punto (ver las preguntas). Toda clave está a **11 puntos** del origen (puerta 1 + tronco 4 + camino 6), así que
ninguna es más barata que otra. Una partida en el tope lleva 2 o 3 claves.

## Puntos: niveles e hitos

- **1 punto por nivel**, contando el 1: 15 en el nivel 15. No cambia (`ClassProgress.POINTS_PER_LEVEL`).
- **Hitos**: 28 puntos más, cada uno una vez por jugador.
  - **Son del jugador, no de la clase**: se quedan al morir, al cambiar de clase y al usar el Medallón.
  - Por eso la decisión A (cambiar de clase vuelve al nivel 1) sigue doliendo, pero no hunde: lo que hiciste en el
    mundo te lo llevas.
- **Tope de hitos por nivel**: solo puedes gastar **2 puntos de hitos por cada nivel de clase** (2 en el nivel 1, los
  28 en el 14).
  - Así un jugador que cambia de clase con todos los hitos no sale con 29 puntos en el nivel 1, y los hitos tempranos
    se notan desde el principio.
  - Los puntos que pasan del tope se ven en gris en la pantalla: «+6 al subir de nivel».
- **Total en el tope: 15 + 28 = 43.**
- Los hitos se dan **con efecto retroactivo**: al entrar con esta versión se miran los logros que ya tiene el jugador.

| Hito | Condición | Puntos | De dónde sale |
|---|---|---|---|
| Primer élite | mata un monstruo de amenaza élite | 1 | nuevo (contador al matar) |
| Primer campeón | mata un campeón | 2 | logro `forja:forja/elite` («Cazador de campeones») |
| Sin capitán | mata al capitán de una banda de saqueadores | 1 | `forja:forja/saqueadores` |
| El Herrero Caído | derrótalo | 4 | `forja:forja/herrero_caido` |
| El Guardián de Cuño | derrótalo | 4 | logro nuevo `forja:forja/guardian_de_cuno` |
| El Wither | mátalo | 2 | estadística de muertes (el logro vanilla es solo invocarlo) |
| El dragón | mata al dragón del End | 3 | `minecraft:end/kill_dragon` |
| Guardián anciano | mata uno | 1 | estadística de muertes |
| Héroe de la aldea | gana una invasión | 1 | `minecraft:adventure/hero_of_the_village` |
| Al Nether | entra | 1 | `minecraft:story/enter_the_nether` |
| Al End | entra | 1 | `minecraft:story/enter_the_end` |
| Entre estrellas | enciende el portal de la Forja Profunda | 1 | `forja:forja/portal` |
| Golpe limpio | consigue una forja perfecta | 1 | `forja:forja/perfecta` |
| Mano hecha | maestría de herrero 5 | 1 | `SmithLevel` |
| Maestro del gremio | maestría de herrero 10 | 2 | `SmithLevel` |
| Obra maestra | forja una obra maestra | 1 | `forja:forja/obra_maestra` |
| Maestro forjador | un objeto con cinco mejoras al 100 % | 1 | `forja:forja/maestro` |
| **Total** | 17 hitos | **28** | |

Pesan más los dos jefes del mod (8 de 28) y la forja (6): es un mod de forja, y ahora la forja es de todas las clases.
Al conseguir un hito sale un aviso con su icono y «+N puntos de árbol», y un punto dorado en la tecla K.

## El Herrero: recomiendo quitarlo (opción a)

**Recomendación: quitar el Herrero como clase y repartir la forja en una región igual en todos los árboles.**

Por qué:

- **La forja ya tiene su propia progresión, y es mejor que una clase.**
  - La maestría de herrero (10 niveles) ensancha la ventana del martillo y da porcentaje por ingrediente.
  - Las técnicas (maestría 3, 6 y 9) ya son un árbol de forja, con elecciones para siempre.
  - Una clase de Herrero repetía todo eso y obligaba a elegir entre **pelear o forjar**, cuando en este mod todo el
    mundo forja su equipo.
- **Era la clase que nadie elige para jugar.** No tiene habilidades de combate de verdad (Temple de campaña es una
  reparación). En multijugador acababa siendo «el que forja para los demás», y sola no aguanta el Bastión.
- **Una séptima clase rehecha (opción b)** no compensa. Un ingeniero con autómatas o torretas, por ejemplo, es un
  sistema de combate nuevo entero (IA aliada, modelos, equilibrio) solo para llenar un hueco, y sus puentes romperían
  el reparto de 6 clases × 3 puentes. Si Andy la quiere, mejor como clase nueva más adelante, no como arreglo del
  Herrero.
- Con la forja en todos los árboles, **cada clase decide cuánto forja**:
  - nada (0 puntos);
  - unos pocos de paso, porque las puertas del núcleo tocan los nodos de forja;
  - o los 13 nodos (15 puntos), para quien quiera ser el herrero del grupo.

### La región de la forja

13 nodos naranjas en los huecos entre las 6 puertas, con los mismos ids en todas las clases (`forja.*`):

- 6 interiores, cada uno pegado a las **dos puertas** que tiene al lado;
- 6 exteriores, colgados de los interiores;
- Temple II, colgado de Temple.

Entera cuesta 15 puntos.

| Nodo | Efecto | Viene de |
|---|---|---|
| Ojo del martillo | ventana del golpe perfecto +0,01 | Pulso (Herrero) |
| Golpe de maestro | otros +0,01 | Golpe maestro |
| Metal dócil | potencial al forjar +5 | Ojo de metal |
| Alma del metal | otros +5 | Alma del metal (era +10) |
| Remiendo | cada lingote repara +25 % | Remiendo |
| Fuelle | tus cubas, crisoles y montadoras van un 20 % más rápido | **nuevo** |
| Ajuste fino | lo que monta tu montadora nace con potencial +5 | **nuevo** («calidad de la montadora») |
| Mano firme | +1 % por ingrediente de mejora (como un nivel de maestría) | Mano firme |
| Carga honda | +2 de carga en lo que forjas | **nuevo** («carga de mejoras») |
| Brazo de herrero | +8 % con martillo, mazo, pico y hacha | Brazo de herrero (era +15 %) |
| Forja al rojo | tras una forja perfecta, 60 s de golpes c/c que prenden 3 s y hacen +10 % | la habilidad B del Herrero, ahora pasiva |
| **Temple de campaña** (habilidad J) | repara el 15 % de la pieza de la mano; Prisa minera II 10 s; espera 60 s | la habilidad V del Herrero |
| **Temple de campaña II** | el 25 %, y la armadura forjada puesta un 5 % | **nuevo** |

Con la región entera: ventana +0,02, potencial +10 (+15 en la montadora), carga +2 y +1 % por ingrediente. Es menos que
el Herrero completo de hoy (ventana +0,05, potencial +25, reparación +50 %), y es a propósito: **ahora se suma a una
clase de combate**, y lo fuerte de la forja sigue en la maestría y las técnicas.

Lo que hace falta para los nodos nuevos:

- tres estadísticas nuevas: `FOUNDRY_SPEED`, `CAPACITY` y `ASSEMBLER_POTENTIAL`;
- para el Fuelle y el Ajuste fino, saber **quién colocó** la máquina (guardar su UUID al colocarla).

**La habilidad de forja.** Temple de campaña es una habilidad activa, así que necesita tecla. Propongo **J** por
defecto, en «Forja: clases», cambiable como las otras. La alternativa sin tecla nueva es agacharse y usar con la pieza
forjada en la mano. *(Pregunta abierta.)*

### Qué pasa con los Herreros de hoy

- Al entrar, un jugador con clase `herrero` se queda **sin clase**, pero **conserva su nivel y su experiencia** en un
  campo aparte (`nivel_heredado`). Le sale este mensaje: «La clase Herrero ya no existe: la forja está en todos los
  árboles. Elige clase gratis con K; empiezas en tu nivel N».
- Su siguiente elección es **gratis y conserva ese nivel**. Es una excepción a la decisión A, porque el cambio no fue
  cosa suya. No gasta Medallón.
- Sus talentos viejos se pierden y le vuelven todos los puntos, como a todos (ver «Migración»).
- **Experiencia por forjar**: hoy solo el Herrero sube forjando. Propongo que **todas** las clases ganen la mitad de
  lo que ganaba él (forjar 5, cambiar pieza 2, mejorar 4, golpe perfecto 8, don 12), para que forjar no sea tiempo
  perdido para la clase. *(Pregunta abierta.)*
- El Herrero desaparece de:
  - la pantalla de elección;
  - la tabla de factores de la decisión F (se va su fila ×1/×1/×1);
  - el libro V «Clases»: «siete» pasa a «seis» y hay un capítulo nuevo, «La forja en el árbol»;
  - `PlayerClass`: el id `herrero` solo se sigue leyendo para migrar.

## Las tres habilidades de cada clase

Teclas:

- **V**: habilidad I, viene con la clase;
- **B**: habilidad II, en la senda 2;
- **N**: habilidad III, al final de la senda 3;
- **J**: Temple de campaña, en la región de la forja.

N y J son `KeyMapping` nuevos en «Forja: clases», y los textos las nombran con `Component.keybind`, como a V y B. El
HUD gana una tercera y una cuarta casilla de espera.

| Clase | V (I) | V II | B (II) | B II | N (III) | N II |
|---|---|---|---|---|---|---|
| Guerrero | Grito de guerra | 60 de estamina, radio 12, Fuerza 10 s | Postura de hierro | 8 s, sin Lentitud, +20 de estamina al acabar | **Torbellino**: giras 1 s y golpeas todo a 3 bloques con el 80 % del arma y 30 de postura; 30 de estamina; espera 30 s | dos vueltas (1,5 s), postura +50 % |
| Asesino | Paso sombrío | 6 s, golpe +80 % | Marca de muerte | +40 %; si el marcado muere, espera a la mitad | **Abanico de dagas**: 5 dagas en 60°, 4 de daño cada una y Veneno I 3 s; espera 25 s | 7 dagas y Veneno II |
| Tanque | Provocar | radio 14, Resistencia I 9 s y 4 de absorción | Baluarte | 10 s, radio 9 | **Embestida de escudo**: carga de 6 bloques, 6 de daño, 40 de postura y empuje; espera 20 s | 8 bloques y aturde 1 s |
| Mago | Nova arcana | 9 de daño, radio 6 | Concentración | 11 s | **Meteoro**: tras 1,5 s, 12 de daño mágico en 3 bloques y fuego 3 s, hasta 24 bloques; 40 de maná; espera 40 s | 16 de daño, radio 4 |
| Curandero | Pulso sanador | cura 6 y quita Veneno y Marchitamiento | Resurgir | espera 70 s, Regeneración II 8 s | **Escudo de luz**: un aliado (o tú) gana 6 de absorción y −20 % de daño 6 s; espera 35 s | 8 de absorción y quita un efecto negativo |
| Arquero | Salto atrás | espera 8 s; la siguiente flecha en 3 s hace +25 % | Lluvia de flechas | 18 flechas, círculo de 4 | **Flecha de red**: atrapa lo que hay a 3 bloques (Lentitud IV 3 s); espera 20 s | radio 4, 4 s, y +15 % de tus flechas a los atrapados |

Todas pasan por los factores de daño de su clase, como cualquier golpe: el Torbellino es cuerpo a cuerpo, las dagas y
la red son proyectiles, y el Meteoro es magia.

Puntos para llegar: V II y B a 4, B II a 6, N a 7 y N II a 9. Hoy la habilidad B cuesta 6 puntos (pide un nodo de
nivel 2), así que ahora llega antes.

## Puentes

Cada clase tiene 3 puentes y recibe 3. Así ninguna clase es «el puente de todas».

| Clase | Senda 1 → | Senda 2 → | Senda 3 → |
|---|---|---|---|
| Guerrero | Tanque | Asesino | Arquero |
| Asesino | Guerrero | Arquero | Mago |
| Tanque | Guerrero | Curandero | Mago |
| Mago | Asesino | Curandero | Tanque |
| Curandero | Tanque | Mago | Arquero |
| Arquero | Asesino | Guerrero | Curandero |

Al otro lado del puente hay un **paquete fijo por clase de destino**, el mismo venga quien venga: el puente, un menor y
un notable «menor». Cuestan **2 puntos cada uno** (6 en total).

| Destino | Puente | Menor | Notable |
|---|---|---|---|
| Guerrero | regeneración de estamina +5 % | postura +5 % | Réplica menor: una parada perfecta devuelve 10 de estamina |
| Asesino | velocidad +3 % | coste de esquiva −8 % | Puñalada menor: por la espalda +20 % |
| Tanque | vida +5 % | armadura +1 | Represalia menor: 2 de daño a quien golpea tu escudo |
| Mago | maná máximo +10 % | regeneración de maná +0,5 | Barrera menor: daño mágico recibido −15 % |
| Curandero | curación +10 % | regeneración de estamina +5 % | Vendaje: 1 de vida cada 8 s sin recibir daño |
| Arquero | tensado +5 % | proyectiles +4 % | Ojo de halcón menor: tiros a la cabeza +15 % |

Regla: **nada al otro lado de un puente toca los factores de daño (decisión F)**. Por eso el puente al Mago da maná y
defensa, nunca daño de hechizos: un Asesino que cruza sigue pegando con magia a ×0,4.

## Equilibrio

<!-- CALCULOS:INICIO -->
Presupuesto en el tope: **43 puntos** (15 de nivel + 28 de hitos).

| Clase | Nodos | Coste del árbol entero | % del árbol con 43 puntos | Claves (puntos para llegar) |
|---|---|---|---|---|
| Guerrero | 97 | 119 | 36 % | Adrenalina 11, Duelista 11, Fortaleza 11, Martillo de guerra 11, Muro de carne 11, Sed de sangre 11 |
| Asesino | 97 | 119 | 36 % | Fantasma 11, Filo del viento 11, Frenesí 11, Funámbulo 11, Golpe de gracia 11, Sin sombra 11 |
| Tanque | 97 | 119 | 36 % | Espinas de acero 11, Gigante 11, Imán de golpes 11, Muralla viva 11, Yunque viviente 11, Último bastión 11 |
| Mago | 97 | 119 | 36 % | Escudo de maná 11, Hechizo encadenado 11, Parpadeo 11, Pozo sin fondo 11, Sangre por maná 11, Todo o nada 11 |
| Curandero | 97 | 119 | 36 % | Florecer 11, Lazo vital 11, Martillo de la fe 11, Mártir 11, Peregrino 11, Tierra sagrada 11 |
| Arquero | 97 | 119 | 36 % | Disparo en carrera 11, Flecha perforante 11, Francotirador 11, Halcón 11, Ojo de águila 11, Ráfaga 11 |

Puntos hasta cada habilidad (desde el origen, por el camino más barato):

- Guerrero: Grito de guerra II 4, Postura de hierro 4, Temple de campaña 4, Postura de hierro II 6, Temple de campaña II 6, Torbellino 7, Torbellino II 9.
- Asesino: Marca de muerte 4, Paso sombrío II 4, Temple de campaña 4, Marca de muerte II 6, Temple de campaña II 6, Abanico de dagas 7, Abanico de dagas II 9.
- Tanque: Baluarte 4, Provocar II 4, Temple de campaña 4, Baluarte II 6, Temple de campaña II 6, Embestida de escudo 7, Embestida de escudo II 9.
- Mago: Concentración 4, Nova arcana II 4, Temple de campaña 4, Concentración II 6, Temple de campaña II 6, Meteoro 7, Meteoro II 9.
- Curandero: Pulso sanador II 4, Resurgir 4, Temple de campaña 4, Resurgir II 6, Temple de campaña II 6, Escudo de luz 7, Escudo de luz II 9.
- Arquero: Lluvia de flechas 4, Salto atrás II 4, Temple de campaña 4, Lluvia de flechas II 6, Temple de campaña II 6, Flecha de red 7, Flecha de red II 9.

Lo máximo que el árbol suma a una estadística con los 43 puntos (todo en esa estadística, sin la base de la clase):

| Clase | Estadística | Máximo del árbol | Puntos usados |
|---|---|---|---|
| Guerrero | daño cuerpo a cuerpo | +15 % | 14 |
| Guerrero | vida | +46 % | 35 |
| Guerrero | daño de postura | +41 % | 19 |
| Guerrero | ventana de parada | +2 | 6 |
| Asesino | daño cuerpo a cuerpo | +12 % | 9 |
| Asesino | daño por la espalda | +45 % | 12 |
| Asesino | coste de esquiva | −15 % | 16 |
| Asesino | daño a enemigos bajo el 35 % | +40 % | 17 |
| Tanque | vida | +66 % | 43 |
| Tanque | armadura | +9 | 26 |
| Tanque | daño recibido | −40 % | 31 |
| Tanque | daño cuerpo a cuerpo | +3 % | 1 |
| Mago | daño de hechizos | +20 % | 9 |
| Mago | espera de hechizos | −6 % | 8 |
| Mago | bono de la carga completa | +29 % | 9 |
| Mago | maná máximo | +46 % | 26 |
| Curandero | curación | +69 % | 31 |
| Curandero | vida | +59 % | 40 |
| Curandero | regeneración de maná | +270 % | 29 |
| Curandero | daño recibido | −15 % | 19 |
| Arquero | daño de proyectiles | +15 % | 5 |
| Arquero | tiros a la cabeza | +41 % | 21 |
| Arquero | tensado | +39 % | 28 |
| Arquero | velocidad | +32 % | 39 |
<!-- CALCULOS:FIN -->

El máximo de la última tabla sale de un algoritmo voraz sobre el grafo, con los 43 puntos y sin tope de hitos. Es una
cota, no una partida real: quien lo hace no se lleva nada más.

### Las decisiones A–F siguen en pie

- **A** (cambiar de clase vuelve al nivel 1): sigue. Los hitos se conservan, pero con el tope de 2 por nivel.
- **B** (Curandero: magia ×1/3, cuerpo a cuerpo ×0,5): sigue. *Martillo de la fe* cura pegando pero no cambia el
  factor. *Peregrino* resta un 20 % a los porcentajes de daño, antes del factor.
- **C** (tope en el nivel 15): sigue. Los puntos de más vienen de los hitos.
- **D** (Medallón forjado) y **E** (teclas): siguen. Se añaden N y J.
- **F** (factores de daño): **ningún nodo cambia `ClassDamage`**.
  - Los nodos solo suman a los porcentajes, que se aplican antes del factor, como hoy:
    `daño = arma × (1 + suma) × factor`.
  - Las claves que restan daño restan en esa suma: Martillo de guerra, Golpe de gracia, Espinas de acero, Imán de
    golpes, Ráfaga, Peregrino, Todo o nada, Francotirador y Halcón.
  - La fila del Herrero se va.

### Daño frente a hoy

| Clase | Daño directo del árbol, hoy | En el árbol nuevo | Comentario |
|---|---|---|---|
| Guerrero | c/c +0 % (solo condicionales: remates +40, aturdidos +25) | c/c hasta +15 %; remates +36 %, aturdidos +26 % | sube: hay más puntos y más árbol |
| Asesino | c/c +0 % (espalda +40, ejecutar +30) | c/c hasta +12 %; espalda +45 %, ejecutar +40 % | condicionales parecidos; Frenesí hasta +32 % encadenando muertes |
| Tanque | 0 | c/c +3 % | el Tanque no gana daño: gana vida y defensa |
| Mago | hechizos +20 % (Núcleo + Catalizador) | hechizos **+20 %** exacto | igual que hoy, así que `magiaEnSuSitio` se mantiene; espera −6 % (hoy −5 %) |
| Arquero | proyectiles +10 % (cabeza +25, lejano +40) | proyectiles +15 %; cabeza +41 %, lejano +30 % | sube un poco |

Para que la dificultad siga a la par, `GearScore` debería contar los **puntos gastados** además del nivel. Hoy suma
`0,08 × nivel / 15`; propongo `0,08 × puntos gastados / 43`.

### Ninguna clave domina

Todas cuestan 2 y están a 11 puntos. Todas ganan algo grande **y** pagan algo que se nota en el estilo contrario:

- Las de **daño** pagan con daño en otra situación o con cadencia:
  - Martillo de guerra, −10 % a lo que no está aturdido;
  - Golpe de gracia, −15 % a enemigos sanos;
  - Todo o nada, −40 % sin cargar;
  - Francotirador, −30 % de cerca;
  - Halcón, −10 % en el suelo;
  - Hechizo encadenado, +20 % de espera;
  - Flecha perforante, −20 % de tensado;
  - Ráfaga, −20 % de daño por un tercio más de disparos (≈ +7 % de daño por segundo).
- Las de **defensa** pagan con movilidad o recursos: Muro de carne, Gigante, Yunque viviente, Fortaleza y Muralla viva.
- Las de **recursos** pagan con el recurso contrario: Adrenalina, Pozo sin fondo, Sangre por maná, Escudo de maná, Sin
  sombra y Mártir.

Dos combinaciones a vigilar en el paso de pruebas:

- **Escudo de maná** con **Pozo sin fondo** en el mismo Mago: una barra de maná muy grande (Pozo da +60 %) absorbiendo daño. El precio de las dos es
  la regeneración, y se multiplica (×0,5 × 0,75): una barra vacía tarda casi tres veces más en llenarse.
- **Frenesí**: se pierde al recibir un golpe, y el Asesino tiene −30 % de vida.

La vida es la estadística que más sube: Tanque +66 % si gasta todo en vida, Curandero +59 %, Guerrero +46 %. Pero
cuesta casi todo el presupuesto: un Tanque así no lleva ni una clave de daño ni habilidades mejoradas.

En el paso de construcción, `BalanceGameTests` gana una prueba por clase con la «partida máxima» de su estadística de
daño principal, y `docs/EQUILIBRIO.md` gana la tabla.

## La pantalla del árbol (K)

- **Lienzo que se mueve**:
  - arrastrar con el ratón (botón izquierdo en vacío, o derecho en cualquier sitio) mueve el árbol;
  - la rueda acerca y aleja hacia el cursor, de 0,4× a 2×;
  - al abrir, el árbol sale centrado en el origen, con el zoom justo para ver el núcleo y los troncos;
  - **Inicio** (o doble clic en vacío) vuelve al centro;
  - con teclado: flechas para mover, + y − para el zoom.
- **Camino encendido en dorado**:
  - los nodos aprendidos y sus uniones, en dorado;
  - los que ya se pueden coger (pegados y con puntos), con un borde blanco que late;
  - el resto, apagados;
  - los nodos de forja, en naranja; los de otra clase, en el color de esa clase, con su icono pequeño sobre el rombo
    del puente.
- **Formas**: círculo pequeño (menor), círculo grande con marco (notable), hexágono (clave), cuadrado con la tecla
  (habilidad: «B», «N II»...) y rombo (puente). Cada nodo lleva su icono de objeto, como hoy (`Talent.icon`).
- **Tooltip con números**:
  - nombre, tipo y coste;
  - el efecto con sus números (de `numeros`, como hoy);
  - en las claves, «Gana» en verde y «Precio» en rojo;
  - en las habilidades, la espera y la tecla que tenga puesta;
  - debajo, el total que tendrías con ese nodo: «vida: +18 % → +21 %».
- **Probar antes de gastar**:
  - botón «Probar» (o Mayús + clic): los clics marcan nodos en azul sin gastar nada, con un contador «Plan: 7 puntos
    (te quedan 3)»;
  - la columna derecha enseña los totales de la clase con el plan puesto, con las diferencias en verde y en rojo;
  - «Aplicar» lo aprende todo de una vez (el servidor comprueba cada paso) y «Descartar» lo borra;
  - el plan se recuerda mientras no cierres el juego, y sirve también para planear lo que aún no puedes pagar.
- **Buscar**: un campo arriba («buscar: parada, vida, clave...») ilumina los nodos cuyo nombre o efecto lo contiene y
  apaga el resto. Intro salta al siguiente.
- **Cabecera**: clase, nivel, experiencia, puntos libres, puntos de hitos (y los que esperan al tope de nivel), y un
  botón «Hitos» con la lista y los que faltan.
- **Rendimiento**: unos 100 nodos y 110 líneas, dibujados enteros en cada fotograma, sin texturas grandes. Las líneas
  doradas se pintan con `GuiGraphics.fill` en tramos, como en la pantalla de logros.

## Reiniciar

- **Medallón del olvido** (sin cambios): cambia de clase o, en tu misma clase, te devuelve todos los puntos.
- **Propuesta: la Vela del olvido**, un reinicio barato.
  - Devuelve **hasta 4 puntos** quitando nodos «hoja» (los que no dejan suelto a ningún otro).
  - Se usa desde la pantalla: con la vela en el inventario, clic derecho en un nodo lo marca para quitar, y «Quitar»
    gasta la vela.
  - Se hace en la mesa de trabajo con **una vela, dos fragmentos de amatista y una lágrima de ghast**: barata pero del
    Nether, para que no se reparta el árbol cada noche.
  - Se apila hasta 16.
- La primera vez que alguien entra con los árboles nuevos, todo vuelve gratis (ver «Migración»).

## Migración de las partidas

Hoy `ClassData` guarda los talentos como un `int` de bits según la posición del talento en su clase (`mask`, campo
`talentos`), y dos esperas (`habilidad_1` y `habilidad_2`).

- **Formato nuevo**, todo con `optionalFieldOf` para que una partida vieja cargue sin errores:
  - `nodos`: lista de ids de texto (`"guerrero.b2.3"`, `"forja.remiendo"`), así un nodo puede moverse o añadirse sin
    romper nada;
  - `hitos`: lista de ids;
  - `habilidad_3` y `habilidad_forja`: las esperas nuevas;
  - `nivel_heredado`: para los Herreros;
  - `arbol`: la versión (2).
- **Al cargar una partida con `arbol` < 2**:
  1. Si `talentos` ≠ 0, se borran y vuelven **todos** los puntos: el árbol es otro y no hay una traducción justa nodo
     a nodo. Si tenía la habilidad B, se le regala el camino hasta B (puerta de la senda 2, su menor y B: 4 puntos)
     para que no pierda una tecla que usaba; el resto queda libre.
  2. Si es Herrero, ver «Qué pasa con los Herreros de hoy».
  3. Se calculan los hitos retroactivos.
  4. Mensaje: «Los árboles han crecido: tienes N puntos para repartir (K)».
- El enum `Talent` se sustituye por una clase generada desde `arboles.json`. Los ids viejos solo los lee la migración.
- Los comandos `/forja clase aprender <nodo>` y `puntos` pasan a ids de nodo, y se añaden `/forja clase hito <id>` y
  `hitos`.

## Para el paso de construcción

1. `tools/arboles.py` → `clase/TreeNodes.java` (generado), y los textos en `generate_lang.py`.
2. `ClassData` nuevo, con la migración; `ClassProgress` con los hitos y el tope de 2 por nivel.
3. `ClassStat`: `FOUNDRY_SPEED`, `CAPACITY` y `ASSEMBLER_POTENTIAL`, y una suma de «todo tu daño» para Peregrino.
4. Unos 60 efectos especiales (notables y claves con texto) en `ClassEffects` y `ClassSkills`, cada uno con su prueba.
5. Una habilidad nueva por clase (Torbellino, Abanico de dagas, Embestida de escudo, Meteoro, Escudo de luz y Flecha
   de red), las mejoras II y las teclas N y J.
6. La pantalla nueva (se reescribe `TalentTreeScreen`) y su prueba de cliente con capturas.
7. Quitar el Herrero: `PlayerClass`, `ClassDamage`, la elección, el libro V y la experiencia por forjar.

## Preguntas abiertas para Andy

1. ¿Te vale **quitar el Herrero** (opción a) con la región de forja de 13 nodos, o prefieres rehacerlo como otra clase?
2. ¿Tecla **J** para Temple de campaña, o mejor agacharse y usar con la pieza en la mano?
3. ¿Todas las clases ganan **la mitad de la experiencia del Herrero al forjar**?
4. ¿Te parece bien el **tope de 2 puntos de hitos por nivel**, o los hitos se gastan enteros desde el nivel 1?
5. ¿Te valen los **hitos y sus puntos** (28 en total, 8 por los dos jefes)? ¿Falta alguno (el Warden, una ciudad
   antigua, el castillo entero)?
6. ¿La **Vela del olvido** (4 puntos; vela, 2 amatistas y lágrima de ghast), o solo el Medallón?
7. ¿Te gustan las **terceras habilidades** (Torbellino, Abanico de dagas, Embestida, Meteoro, Escudo de luz y Flecha de
   red)?
8. ¿Los Herreros de hoy **conservan el nivel** al elegir clase nueva (excepción a la decisión A)?
9. ¿`GearScore` cuenta los puntos gastados, para que los monstruos se endurezcan con el árbol?
10. Con 43 puntos se gasta el 36 % del árbol (unos 35 nodos de 97). ¿Lo quieres más cerca de la mitad? Bastaría con
    bajar las claves y los puentes a 1 punto.

## Todos los nodos

Los ids van sin el prefijo de la clase (`b2.3` es `guerrero.b2.3`):

- `a`, `b` y `c` son las ramas: `tronco_1` a `tronco_4`, y los caminos `a1`/`a2` con los nodos 1 a 5 y `lado_1`/`lado_2`;
- `s1`, `s2` y `s3` son las sendas: nodos 1 a 4, `lado` y `puente_1` a `puente_3`;
- `nucleo_1` a `nucleo_6` son las puertas: 1 → rama A, 2 → senda 1, 3 → rama B, 4 → senda 2, 5 → rama C, 6 → senda 3.
  Además se conectan con los dos nodos de forja de sus huecos.

<!-- NODOS:INICIO -->
### Región de la forja (igual en los seis árboles)

| Id | Nombre | Tipo | Coste | Efecto | Conexiones |
|---|---|---|---|---|---|
| `ojo_del_martillo` | Ojo del martillo | forja | 1 | ventana del golpe perfecto +0,01 | golpe_de_maestro, las dos puertas del núcleo que tiene al lado |
| `metal_docil` | Metal dócil | forja | 1 | potencial al forjar +5 | alma_del_metal, las dos puertas del núcleo que tiene al lado |
| `remiendo` | Remiendo | forja | 1 | reparación por lingote +25 % | temple_de_campana, las dos puertas del núcleo que tiene al lado |
| `fuelle` | Fuelle | forja | 1 | tus cubas, crisoles y montadoras trabajan un 20 % más rápido | ajuste_fino, las dos puertas del núcleo que tiene al lado |
| `mano_firme` | Mano firme | forja | 1 | % por ingrediente de mejora +1 | carga_honda, las dos puertas del núcleo que tiene al lado |
| `brazo_de_herrero` | Brazo de herrero | forja | 1 | daño con martillo, mazo, pico y hacha +8 % | forja_al_rojo, las dos puertas del núcleo que tiene al lado |
| `golpe_de_maestro` | Golpe de maestro | forja | 1 | ventana del golpe perfecto +0,01 | ojo_del_martillo |
| `alma_del_metal` | Alma del metal | forja | 1 | potencial al forjar +5 | metal_docil |
| `temple_de_campana` | Temple de campaña | habilidad | 2 | la pieza forjada de tu mano recupera el 15 % de su durabilidad; Prisa minera II 10 s; espera 60 s | remiendo, temple_de_campana_ii |
| `ajuste_fino` | Ajuste fino | forja | 1 | potencial de lo que monta tu montadora +5 | fuelle |
| `carga_honda` | Carga honda | forja | 1 | carga de lo que forjas +2 | mano_firme |
| `forja_al_rojo` | Forja al rojo | forja | 1 | tras una forja perfecta, 60 s en que tus golpes c/c prenden fuego 3 s y hacen +10 % | brazo_de_herrero |
| `temple_de_campana_ii` | Temple de campaña II | habilidad | 2 | el 25 % (antes 15 %) y la armadura forjada que llevas puesta un 5 %; espera 60 s | temple_de_campana |

### Guerrero

Base: vida +10 %, daño c/c +5 %, estamina máx. +20 %, regeneración +10 %, postura +15 %, parada +1 tick. Ramas: A Aguante, B Guardia, C Quebranto. Sendas: 1 Senda del grito (puente al Tanque), 2 Senda del hierro (puente al Asesino), 3 Senda del torbellino (puente al Arquero).

Habilidades: **Grito de guerra** (V): recupera 40 de estamina; tú y los jugadores a 8 bloques ganáis Fuerza I 8 s, espera 45 s; **Grito de guerra II** (V2): recupera 60 de estamina (antes 40); radio 12 (antes 8); Fuerza I 10 s (antes 8), espera 45 s; **Postura de hierro** (B): 6 s de Resistencia II y Lentitud I, espera 50 s; **Postura de hierro II** (B2): 8 s (antes 6), sin Lentitud, y al acabar recuperas 20 de estamina, espera 50 s; **Torbellino** (N): giras 1 s golpeando todo a 3 bloques con el 80 % del daño de tu arma y 30 de postura; cuesta 30 de estamina, espera 30 s; **Torbellino II** (N2): dos vueltas (1,5 s) y postura +50 %, espera 30 s.

| Id | Nombre | Tipo | Coste | Efecto | Conecta con |
|---|---|---|---|---|---|
| `origen` | Guerrero | origen | 0 | la clase: vida +10 %, daño c/c +5 %, estamina máx. +20 %, regeneración +10 %, postura +15 %, parada +1 tick; habilidad I (V): Grito de guerra | `nucleo_1`, `nucleo_2`, `nucleo_3`, `nucleo_4`, `nucleo_5`, `nucleo_6` |
| `nucleo_1` | Estamina máxima | núcleo | 1 | estamina máxima +3 % | `origen`, `nucleo_2`, `ojo_del_martillo`, `brazo_de_herrero`, `nucleo_6`, `a.tronco_1` |
| `nucleo_2` | Regeneración de estamina | núcleo | 1 | regeneración de estamina +3 % | `origen`, `nucleo_3`, `metal_docil`, `ojo_del_martillo`, `nucleo_1`, `s1.1` |
| `nucleo_3` | Estamina al parar | núcleo | 1 | estamina al parar −3 % | `origen`, `nucleo_4`, `remiendo`, `metal_docil`, `nucleo_2`, `b.tronco_1` |
| `nucleo_4` | Vida | núcleo | 1 | vida +3 % | `origen`, `nucleo_5`, `fuelle`, `remiendo`, `nucleo_3`, `s2.1` |
| `nucleo_5` | Daño de postura | núcleo | 1 | daño de postura +3 % | `origen`, `nucleo_6`, `mano_firme`, `fuelle`, `nucleo_4`, `c.tronco_1` |
| `nucleo_6` | Daño cuerpo a cuerpo | núcleo | 1 | daño cuerpo a cuerpo +3 % | `origen`, `nucleo_1`, `brazo_de_herrero`, `mano_firme`, `nucleo_5`, `s3.1` |
| `a.tronco_1` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `nucleo_1`, `a.tronco_2` |
| `a.tronco_2` | Vida | menor | 1 | vida +3 % | `a.tronco_1`, `a.tronco_3` |
| `a.tronco_3` | Estamina máxima | menor | 1 | estamina máxima +3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Segundo aliento | notable | 1 | regeneración de estamina +15 %; por debajo del 25 % de estamina se regenera el doble | `a.tronco_3`, `a1.1`, `a2.1` |
| `a1.1` | Vida | menor | 1 | vida +3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Inquebrantable | notable | 1 | por debajo del 30 % de vida, daño recibido −20 % | `a1.2`, `a1.4` |
| `a1.4` | Vida | menor | 1 | vida +3 % | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Muro de carne | **clave** | 2 | vida +20 %; velocidad −8 %; coste de esquiva +25 % *(gana: vida +20 %; precio: velocidad −8 %, coste de esquiva +25 %)* | `a1.4` |
| `a1.lado_1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `a1.2` |
| `a1.lado_2` | Daño recibido | menor | 1 | daño recibido −3 % | `a1.4` |
| `a2.1` | Coste de estamina | menor | 1 | coste de estamina −3 % | `a.tronco_4`, `a2.2` |
| `a2.2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Sin resuello | notable | 1 | coste de estamina −15 % | `a2.2`, `a2.4` |
| `a2.4` | Estamina máxima | menor | 1 | estamina máxima +3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Adrenalina | **clave** | 2 | cada golpe que recibes te devuelve 6 de estamina; regeneración de estamina +25 %; pero estamina máxima −25 % *(gana: 6 de estamina por golpe recibido, regeneración +25 %; precio: estamina máxima −25 %)* | `a2.4` |
| `a2.lado_1` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `a2.2` |
| `a2.lado_2` | Velocidad | menor | 1 | velocidad +3 % | `a2.4` |
| `s1.1` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `nucleo_2`, `s1.2` |
| `s1.2` | Grito de guerra II | habilidad | 2 | recupera 60 de estamina (antes 40); radio 12 (antes 8); Fuerza I 10 s (antes 8); espera 45 s | `s1.1`, `s1.3` |
| `s1.3` | Vida | menor | 1 | vida +3 % | `s1.2`, `s1.4` |
| `s1.4` | Estamina máxima | menor | 1 | estamina máxima +3 % | `s1.3`, `s1.puente_1` |
| `s1.puente_1` | Puente al Tanque | puente | 2 | vida +5 % *(del Tanque)* | `s1.4`, `s1.puente_2` |
| `s1.puente_2` | Armadura | cruzado | 2 | armadura +1 *(del Tanque)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Represalia menor | cruzado | 2 | quien golpea tu escudo levantado recibe 2 de daño *(del Tanque)* | `s1.puente_2` |
| `b.tronco_1` | Ventana de parada | menor | 1 | ventana de parada +1 tick | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Estamina al parar | menor | 1 | estamina al parar −3 % | `b.tronco_1`, `b.tronco_3` |
| `b.tronco_3` | Armadura | menor | 1 | armadura +1 | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Réplica | notable | 1 | una parada perfecta devuelve 15 de estamina y tu siguiente golpe en 3 s hace +30 % | `b.tronco_3`, `b1.1`, `b2.1` |
| `b1.1` | Armadura | menor | 1 | armadura +1 | `b.tronco_4`, `b1.2` |
| `b1.2` | Estamina al parar | menor | 1 | estamina al parar −3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Parada firme | notable | 1 | estamina al parar −25 % | `b1.2`, `b1.4` |
| `b1.4` | Dureza de armadura | menor | 1 | dureza de armadura +0,5 | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Fortaleza | **clave** | 2 | con escudo levantado no recibes empuje y parar cuesta −40 % de estamina; pero con escudo en la otra mano no puedes esquivar *(gana: sin empuje, parar −40 %; precio: sin esquiva con escudo)* | `b1.4` |
| `b1.lado_1` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `b1.2` |
| `b1.lado_2` | Vida | menor | 1 | vida +3 % | `b1.4` |
| `b2.1` | Ventana de parada | menor | 1 | ventana de parada +1 tick | `b.tronco_4`, `b2.2` |
| `b2.2` | Contraataques | menor | 1 | contraataques +3 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Filo de vuelta | notable | 1 | una parada perfecta con arma hace 20 de daño de postura al atacante | `b2.2`, `b2.4` |
| `b2.4` | Contraataques | menor | 1 | contraataques +3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Duelista | **clave** | 2 | ventana de parada +3 ticks y las paradas perfectas no gastan estamina; pero una parada no perfecta cuesta el doble *(gana: parada +3 ticks, perfectas gratis; precio: paradas fallidas ×2 de estamina)* | `b2.4` |
| `b2.lado_1` | Daño de postura | menor | 1 | daño de postura +3 % | `b2.2` |
| `b2.lado_2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `b2.4` |
| `s2.1` | Daño de postura | menor | 1 | daño de postura +3 % | `nucleo_4`, `s2.2` |
| `s2.2` | Postura de hierro | habilidad | 2 | 6 s de Resistencia II y Lentitud I; espera 50 s | `s2.1`, `s2.3`, `s2.lado` |
| `s2.3` | Daño recibido | menor | 1 | daño recibido −3 % | `s2.2`, `s2.4` |
| `s2.4` | Vida | menor | 1 | vida +3 % | `s2.3`, `s2.puente_1` |
| `s2.lado` | Postura de hierro II | habilidad | 2 | 8 s (antes 6), sin Lentitud, y al acabar recuperas 20 de estamina; espera 50 s | `s2.2` |
| `s2.puente_1` | Puente al Asesino | puente | 2 | velocidad +3 % *(del Asesino)* | `s2.4`, `s2.puente_2` |
| `s2.puente_2` | Coste de esquiva | cruzado | 2 | coste de esquiva −8 % *(del Asesino)* | `s2.puente_1`, `s2.puente_3` |
| `s2.puente_3` | Puñalada menor | cruzado | 2 | daño por la espalda +20 % *(del Asesino)* | `s2.puente_2` |
| `c.tronco_1` | Daño de postura | menor | 1 | daño de postura +3 % | `nucleo_5`, `c.tronco_2` |
| `c.tronco_2` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `c.tronco_1`, `c.tronco_3` |
| `c.tronco_3` | Daño de postura | menor | 1 | daño de postura +3 % | `c.tronco_2`, `c.tronco_4` |
| `c.tronco_4` | Golpe pesado | notable | 1 | daño de postura +20 % | `c.tronco_3`, `c1.1`, `c2.1` |
| `c1.1` | Daño a aturdidos | menor | 1 | daño a aturdidos +3 % | `c.tronco_4`, `c1.2` |
| `c1.2` | Daño de postura | menor | 1 | daño de postura +3 % | `c1.1`, `c1.3`, `c1.lado_1` |
| `c1.3` | Rompeguardias | notable | 1 | daño a aturdidos +20 % | `c1.2`, `c1.4` |
| `c1.4` | Daño a aturdidos | menor | 1 | daño a aturdidos +3 % | `c1.3`, `c1.5`, `c1.lado_2` |
| `c1.5` | Martillo de guerra | **clave** | 2 | daño de postura +40 % y tus aturdimientos duran +1 s; pero contra lo que no está aturdido haces −10 % *(gana: postura +40 %, aturdimiento +1 s; precio: −10 % a no aturdidos)* | `c1.4` |
| `c1.lado_1` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `c1.2` |
| `c1.lado_2` | Vida | menor | 1 | vida +3 % | `c1.4` |
| `c2.1` | Remates | menor | 1 | remates +3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Verdugo | notable | 1 | remates +30 % | `c2.2`, `c2.4` |
| `c2.4` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Sed de sangre | **clave** | 2 | matar cuerpo a cuerpo cura el 10 % de tu vida máxima; pero no regeneras vida de forma natural *(gana: 10 % de vida por muerte; precio: sin regeneración natural)* | `c2.4` |
| `c2.lado_1` | Remates | menor | 1 | remates +3 % | `c2.2` |
| `c2.lado_2` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `c2.4` |
| `s3.1` | Daño de postura | menor | 1 | daño de postura +3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Carga brutal | notable | 1 | un golpe cargado al máximo aturde 0,5 s a los monstruos normales | `s3.1`, `s3.3` |
| `s3.3` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `s3.2`, `s3.4` |
| `s3.4` | Torbellino | habilidad | 3 | giras 1 s golpeando todo a 3 bloques con el 80 % del daño de tu arma y 30 de postura; cuesta 30 de estamina; espera 30 s | `s3.3`, `s3.lado`, `s3.puente_1` |
| `s3.lado` | Torbellino II | habilidad | 2 | dos vueltas (1,5 s) y postura +50 %; espera 30 s | `s3.4` |
| `s3.puente_1` | Puente al Arquero | puente | 2 | tensado +5 % *(del Arquero)* | `s3.4`, `s3.puente_2` |
| `s3.puente_2` | Daño de proyectiles | cruzado | 2 | daño de proyectiles +4 % *(del Arquero)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Ojo de halcón menor | cruzado | 2 | tiros a la cabeza +15 % *(del Arquero)* | `s3.puente_2` |

### Asesino

Base: vida −30 %, daño c/c +10 %, velocidad +5 %, estamina +30 %, esquiva: distancia +35 %, espera −30 %, coste −20 %, +2 ticks. Ramas: A Sombra, B Filo, C Sigilo. Sendas: 1 Senda de la sombra (puente al Guerrero), 2 Senda de la marca (puente al Arquero), 3 Senda del veneno (puente al Mago).

Habilidades: **Paso sombrío** (V): 4 s de invisibilidad y Velocidad II; el siguiente golpe c/c hace +60 %, espera 30 s; **Paso sombrío II** (V2): 6 s (antes 4) y el golpe +80 % (antes 60 %), espera 30 s; **Marca de muerte** (B): el monstruo que miras (hasta 16 bloques) brilla 10 s y recibe +30 % de tus golpes, espera 40 s; **Marca de muerte II** (B2): +40 % (antes 30 %); si el marcado muere, la espera baja a la mitad, espera 40 s; **Abanico de dagas** (N): lanzas 5 dagas en abanico de 60°: 4 de daño cada una y Veneno I 3 s, espera 25 s; **Abanico de dagas II** (N2): 7 dagas y Veneno II, espera 25 s.

| Id | Nombre | Tipo | Coste | Efecto | Conecta con |
|---|---|---|---|---|---|
| `origen` | Asesino | origen | 0 | la clase: vida −30 %, daño c/c +10 %, velocidad +5 %, estamina +30 %, esquiva: distancia +35 %, espera −30 %, coste −20 %, +2 ticks; habilidad I (V): Paso sombrío | `nucleo_1`, `nucleo_2`, `nucleo_3`, `nucleo_4`, `nucleo_5`, `nucleo_6` |
| `nucleo_1` | Coste de esquiva | núcleo | 1 | coste de esquiva −3 % | `origen`, `nucleo_2`, `ojo_del_martillo`, `brazo_de_herrero`, `nucleo_6`, `a.tronco_1` |
| `nucleo_2` | Estamina máxima | núcleo | 1 | estamina máxima +3 % | `origen`, `nucleo_3`, `metal_docil`, `ojo_del_martillo`, `nucleo_1`, `s1.1` |
| `nucleo_3` | Daño por la espalda | núcleo | 1 | daño por la espalda +3 % | `origen`, `nucleo_4`, `remiendo`, `metal_docil`, `nucleo_2`, `b.tronco_1` |
| `nucleo_4` | Velocidad | núcleo | 1 | velocidad +3 % | `origen`, `nucleo_5`, `fuelle`, `remiendo`, `nucleo_3`, `s2.1` |
| `nucleo_5` | Velocidad agachado | núcleo | 1 | velocidad agachado +3 % | `origen`, `nucleo_6`, `mano_firme`, `fuelle`, `nucleo_4`, `c.tronco_1` |
| `nucleo_6` | Daño cuerpo a cuerpo | núcleo | 1 | daño cuerpo a cuerpo +3 % | `origen`, `nucleo_1`, `brazo_de_herrero`, `mano_firme`, `nucleo_5`, `s3.1` |
| `a.tronco_1` | Coste de esquiva | menor | 1 | coste de esquiva −3 % | `nucleo_1`, `a.tronco_2` |
| `a.tronco_2` | Espera de esquiva | menor | 1 | espera de esquiva −3 % | `a.tronco_1`, `a.tronco_3` |
| `a.tronco_3` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Danza | notable | 1 | invulnerabilidad de esquiva +1 tick; espera de esquiva −10 % | `a.tronco_3`, `a1.1`, `a2.1` |
| `a1.1` | Contraataques | menor | 1 | contraataques +3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Coste de esquiva | menor | 1 | coste de esquiva −3 % | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Contraataque | notable | 1 | contraataques +30 % | `a1.2`, `a1.4` |
| `a1.4` | Contraataques | menor | 1 | contraataques +3 % | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Filo del viento | **clave** | 2 | una esquiva que evita un golpe recarga la esquiva y tu siguiente golpe cuenta como contraataque; pero armadura −4 *(gana: esquiva recargada y contraataque seguro; precio: armadura −4)* | `a1.4` |
| `a1.lado_1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `a1.2` |
| `a1.lado_2` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `a1.4` |
| `a2.1` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `a.tronco_4`, `a2.2` |
| `a2.2` | Espera de esquiva | menor | 1 | espera de esquiva −3 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Espejismo | notable | 1 | al esquivar, los monstruos a 6 bloques pierden tu rastro 1 s (cada 8 s) | `a2.2`, `a2.4` |
| `a2.4` | Coste de esquiva | menor | 1 | coste de esquiva −3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Sin sombra | **clave** | 2 | esquivar no cuesta estamina; pero cada esquiva te quita 1 de vida (nunca por debajo de 1) *(gana: esquivas gratis; precio: 1 de vida por esquiva)* | `a2.4` |
| `a2.lado_1` | Velocidad | menor | 1 | velocidad +3 % | `a2.2` |
| `a2.lado_2` | Daño de caída | menor | 1 | daño de caída −3 % | `a2.4` |
| `s1.1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `nucleo_2`, `s1.2` |
| `s1.2` | Paso sombrío II | habilidad | 2 | 6 s (antes 4) y el golpe +80 % (antes 60 %); espera 30 s | `s1.1`, `s1.3` |
| `s1.3` | Coste de esquiva | menor | 1 | coste de esquiva −3 % | `s1.2`, `s1.4` |
| `s1.4` | Velocidad | menor | 1 | velocidad +3 % | `s1.3`, `s1.puente_1` |
| `s1.puente_1` | Puente al Guerrero | puente | 2 | regeneración de estamina +5 % *(del Guerrero)* | `s1.4`, `s1.puente_2` |
| `s1.puente_2` | Daño de postura | cruzado | 2 | daño de postura +5 % *(del Guerrero)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Réplica menor | cruzado | 2 | una parada perfecta te devuelve 10 de estamina *(del Guerrero)* | `s1.puente_2` |
| `b.tronco_1` | Daño por la espalda | menor | 1 | daño por la espalda +3 % | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `b.tronco_1`, `b.tronco_3` |
| `b.tronco_3` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Puñalada | notable | 1 | daño por la espalda +30 % | `b.tronco_3`, `b1.1`, `b2.1` |
| `b1.1` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `b.tronco_4`, `b1.2` |
| `b1.2` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Ejecutor | notable | 1 | daño a enemigos bajo el 35 % +25 % | `b1.2`, `b1.4` |
| `b1.4` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Golpe de gracia | **clave** | 2 | un golpe c/c a un monstruo normal por debajo del 15 % de vida lo mata (ni jefes, ni campeones); pero haces −15 % a enemigos por encima del 50 % *(gana: remate instantáneo bajo el 15 %; precio: −15 % a enemigos sanos)* | `b1.4` |
| `b1.lado_1` | Daño por la espalda | menor | 1 | daño por la espalda +3 % | `b1.2` |
| `b1.lado_2` | Velocidad | menor | 1 | velocidad +3 % | `b1.4` |
| `b2.1` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `b.tronco_4`, `b2.2` |
| `b2.2` | Daño por la espalda | menor | 1 | daño por la espalda +3 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Golpe letal | notable | 1 | matar c/c devuelve 20 de estamina y da Velocidad II 3 s | `b2.2`, `b2.4` |
| `b2.4` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Frenesí | **clave** | 2 | cada muerte c/c en 5 s da +8 % de daño c/c (hasta 4 veces; se pierde al recibir un golpe); pero vida máxima −10 % *(gana: hasta +32 % encadenando; precio: vida −10 %, se pierde al ser golpeado)* | `b2.4` |
| `b2.lado_1` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `b2.2` |
| `b2.lado_2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `b2.4` |
| `s2.1` | Daño por la espalda | menor | 1 | daño por la espalda +3 % | `nucleo_4`, `s2.2` |
| `s2.2` | Marca de muerte | habilidad | 2 | el monstruo que miras (hasta 16 bloques) brilla 10 s y recibe +30 % de tus golpes; espera 40 s | `s2.1`, `s2.3`, `s2.lado` |
| `s2.3` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `s2.2`, `s2.4` |
| `s2.4` | Velocidad | menor | 1 | velocidad +3 % | `s2.3`, `s2.puente_1` |
| `s2.lado` | Marca de muerte II | habilidad | 2 | +40 % (antes 30 %); si el marcado muere, la espera baja a la mitad; espera 40 s | `s2.2` |
| `s2.puente_1` | Puente al Arquero | puente | 2 | tensado +5 % *(del Arquero)* | `s2.4`, `s2.puente_2` |
| `s2.puente_2` | Daño de proyectiles | cruzado | 2 | daño de proyectiles +4 % *(del Arquero)* | `s2.puente_1`, `s2.puente_3` |
| `s2.puente_3` | Ojo de halcón menor | cruzado | 2 | tiros a la cabeza +15 % *(del Arquero)* | `s2.puente_2` |
| `c.tronco_1` | Velocidad agachado | menor | 1 | velocidad agachado +3 % | `nucleo_5`, `c.tronco_2` |
| `c.tronco_2` | Daño de caída | menor | 1 | daño de caída −3 % | `c.tronco_1`, `c.tronco_3` |
| `c.tronco_3` | Velocidad | menor | 1 | velocidad +3 % | `c.tronco_2`, `c.tronco_4` |
| `c.tronco_4` | Paso quedo | notable | 1 | agachado, los monstruos que no te han visto no te detectan a más de 8 bloques; velocidad agachado +20 % | `c.tronco_3`, `c1.1`, `c2.1` |
| `c1.1` | Salto | menor | 1 | salto +3 % | `c.tronco_4`, `c1.2` |
| `c1.2` | Daño de caída | menor | 1 | daño de caída −3 % | `c1.1`, `c1.3`, `c1.lado_1` |
| `c1.3` | Acróbata | notable | 1 | daño de caída −30 %; salto +10 % | `c1.2`, `c1.4` |
| `c1.4` | Velocidad | menor | 1 | velocidad +3 % | `c1.3`, `c1.5`, `c1.lado_2` |
| `c1.5` | Funámbulo | **clave** | 2 | tras caer de más de 3 bloques, el siguiente golpe en 2 s hace +40 %; pero resistencia al empuje −30 % *(gana: +40 % al caer sobre el enemigo; precio: empuje recibido +30 %)* | `c1.4` |
| `c1.lado_1` | Salto | menor | 1 | salto +3 % | `c1.2` |
| `c1.lado_2` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c1.4` |
| `c2.1` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Velocidad | menor | 1 | velocidad +3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Evasión | notable | 1 | 12 % de probabilidad de que un proyectil no te haga nada | `c2.2`, `c2.4` |
| `c2.4` | Velocidad | menor | 1 | velocidad +3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Fantasma | **clave** | 2 | Paso sombrío dura el doble y el primer golpe no rompe la invisibilidad; pero su espera +50 % *(gana: Paso sombrío ×2 y sigue invisible; precio: espera de Paso sombrío +50 %)* | `c2.4` |
| `c2.lado_1` | Daño de caída | menor | 1 | daño de caída −3 % | `c2.2` |
| `c2.lado_2` | Velocidad agachado | menor | 1 | velocidad agachado +3 % | `c2.4` |
| `s3.1` | Velocidad agachado | menor | 1 | velocidad agachado +3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Veneno en la hoja | notable | 1 | tus golpes por la espalda envenenan (Veneno I 3 s) | `s3.1`, `s3.3` |
| `s3.3` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `s3.2`, `s3.4` |
| `s3.4` | Abanico de dagas | habilidad | 3 | lanzas 5 dagas en abanico de 60°: 4 de daño cada una y Veneno I 3 s; espera 25 s | `s3.3`, `s3.lado`, `s3.puente_1` |
| `s3.lado` | Abanico de dagas II | habilidad | 2 | 7 dagas y Veneno II; espera 25 s | `s3.4` |
| `s3.puente_1` | Puente al Mago | puente | 2 | maná máximo +10 % *(del Mago)* | `s3.4`, `s3.puente_2` |
| `s3.puente_2` | Regeneración de maná | cruzado | 2 | regeneración de maná +50 % *(del Mago)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Barrera menor | cruzado | 2 | daño mágico recibido −15 % *(del Mago)* | `s3.puente_2` |

### Tanque

Base: vida +60 %, armadura +2, empuje +30 %, velocidad −12 %, esquiva −35 %, coste de esquiva +20 %, estamina al parar −25 %. Ramas: A Muralla, B Coraza, C Firmeza. Sendas: 1 Senda del desafío (puente al Guerrero), 2 Senda del baluarte (puente al Curandero), 3 Senda de la embestida (puente al Mago).

Habilidades: **Provocar** (V): los monstruos hostiles a 10 bloques te toman como objetivo; Resistencia I 6 s, espera 25 s; **Provocar II** (V2): radio 14 (antes 10), Resistencia I 9 s (antes 6) y 4 de absorción, espera 25 s; **Baluarte** (B): 8 s de Resistencia II para ti y Resistencia I para los jugadores a 6 bloques, espera 60 s; **Baluarte II** (B2): 10 s (antes 8), radio 9 (antes 6), espera 60 s; **Embestida de escudo** (N): cargas 6 bloques al frente; lo que golpeas recibe 6 de daño, 40 de postura y sale empujado, espera 20 s; **Embestida de escudo II** (N2): 8 bloques y aturde 1 s, espera 20 s.

| Id | Nombre | Tipo | Coste | Efecto | Conecta con |
|---|---|---|---|---|---|
| `origen` | Tanque | origen | 0 | la clase: vida +60 %, armadura +2, empuje +30 %, velocidad −12 %, esquiva −35 %, coste de esquiva +20 %, estamina al parar −25 %; habilidad I (V): Provocar | `nucleo_1`, `nucleo_2`, `nucleo_3`, `nucleo_4`, `nucleo_5`, `nucleo_6` |
| `nucleo_1` | Estamina al parar | núcleo | 1 | estamina al parar −3 % | `origen`, `nucleo_2`, `ojo_del_martillo`, `brazo_de_herrero`, `nucleo_6`, `a.tronco_1` |
| `nucleo_2` | Vida | núcleo | 1 | vida +3 % | `origen`, `nucleo_3`, `metal_docil`, `ojo_del_martillo`, `nucleo_1`, `s1.1` |
| `nucleo_3` | Armadura | núcleo | 1 | armadura +1 | `origen`, `nucleo_4`, `remiendo`, `metal_docil`, `nucleo_2`, `b.tronco_1` |
| `nucleo_4` | Resistencia al empuje | núcleo | 1 | resistencia al empuje +5 % | `origen`, `nucleo_5`, `fuelle`, `remiendo`, `nucleo_3`, `s2.1` |
| `nucleo_5` | Estamina máxima | núcleo | 1 | estamina máxima +3 % | `origen`, `nucleo_6`, `mano_firme`, `fuelle`, `nucleo_4`, `c.tronco_1` |
| `nucleo_6` | Daño cuerpo a cuerpo | núcleo | 1 | daño cuerpo a cuerpo +3 % | `origen`, `nucleo_1`, `brazo_de_herrero`, `mano_firme`, `nucleo_5`, `s3.1` |
| `a.tronco_1` | Estamina al parar | menor | 1 | estamina al parar −3 % | `nucleo_1`, `a.tronco_2` |
| `a.tronco_2` | Ventana de parada | menor | 1 | ventana de parada +1 tick | `a.tronco_1`, `a.tronco_3` |
| `a.tronco_3` | Estamina al parar | menor | 1 | estamina al parar −3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Escudo pesado | notable | 1 | estamina al parar −20 % | `a.tronco_3`, `a1.1`, `a2.1` |
| `a1.1` | Estamina al parar | menor | 1 | estamina al parar −3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Armadura | menor | 1 | armadura +1 | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Represalia | notable | 1 | quien golpea tu escudo levantado recibe 3 de daño | `a1.2`, `a1.4` |
| `a1.4` | Dureza de armadura | menor | 1 | dureza de armadura +0,5 | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Espinas de acero | **clave** | 2 | Represalia devuelve además el 30 % del daño parado; pero daño c/c −15 % *(gana: devuelve 30 % de lo parado; precio: daño c/c −15 %)* | `a1.4` |
| `a1.lado_1` | Vida | menor | 1 | vida +3 % | `a1.2` |
| `a1.lado_2` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `a1.4` |
| `a2.1` | Ventana de parada | menor | 1 | ventana de parada +1 tick | `a.tronco_4`, `a2.2` |
| `a2.2` | Estamina al parar | menor | 1 | estamina al parar −3 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Bastión | notable | 1 | ventana de parada +2 ticks | `a2.2`, `a2.4` |
| `a2.4` | Estamina al parar | menor | 1 | estamina al parar −3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Muralla viva | **clave** | 2 | con el escudo levantado no te frenas y los aliados a 3 bloques detrás de ti reciben −20 % de daño; pero no puedes esprintar *(gana: escudo sin frenar, protege a los de detrás; precio: sin esprintar)* | `a2.4` |
| `a2.lado_1` | Armadura | menor | 1 | armadura +1 | `a2.2` |
| `a2.lado_2` | Vida | menor | 1 | vida +3 % | `a2.4` |
| `s1.1` | Armadura | menor | 1 | armadura +1 | `nucleo_2`, `s1.2` |
| `s1.2` | Provocar II | habilidad | 2 | radio 14 (antes 10), Resistencia I 9 s (antes 6) y 4 de absorción; espera 25 s | `s1.1`, `s1.3` |
| `s1.3` | Vida | menor | 1 | vida +3 % | `s1.2`, `s1.4` |
| `s1.4` | Estamina al parar | menor | 1 | estamina al parar −3 % | `s1.3`, `s1.puente_1` |
| `s1.puente_1` | Puente al Guerrero | puente | 2 | regeneración de estamina +5 % *(del Guerrero)* | `s1.4`, `s1.puente_2` |
| `s1.puente_2` | Daño de postura | cruzado | 2 | daño de postura +5 % *(del Guerrero)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Réplica menor | cruzado | 2 | una parada perfecta te devuelve 10 de estamina *(del Guerrero)* | `s1.puente_2` |
| `b.tronco_1` | Armadura | menor | 1 | armadura +1 | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Vida | menor | 1 | vida +3 % | `b.tronco_1`, `b.tronco_3` |
| `b.tronco_3` | Dureza de armadura | menor | 1 | dureza de armadura +0,5 | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Piel de hierro | notable | 1 | armadura +2 | `b.tronco_3`, `b1.1`, `b2.1` |
| `b1.1` | Dureza de armadura | menor | 1 | dureza de armadura +0,5 | `b.tronco_4`, `b1.2` |
| `b1.2` | Daño recibido | menor | 1 | daño recibido −3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Dureza | notable | 1 | dureza de armadura +2; daño recibido −8 % | `b1.2`, `b1.4` |
| `b1.4` | Armadura | menor | 1 | armadura +1 | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Yunque viviente | **clave** | 2 | daño recibido −20 %; resistencia al empuje +100 %; coste de esquiva +100 %; distancia de esquiva −30 % *(gana: daño recibido −20 %, sin empuje; precio: esquiva al doble de coste y −30 % de distancia)* | `b1.4` |
| `b1.lado_1` | Vida | menor | 1 | vida +3 % | `b1.2` |
| `b1.lado_2` | Daño de fuego recibido | menor | 1 | daño de fuego recibido −3 % | `b1.4` |
| `b2.1` | Vida | menor | 1 | vida +3 % | `b.tronco_4`, `b2.2` |
| `b2.2` | Vida | menor | 1 | vida +3 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Coloso | notable | 1 | vida +10 % | `b2.2`, `b2.4` |
| `b2.4` | Vida | menor | 1 | vida +3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Gigante | **clave** | 2 | vida +20 %; resistencia al empuje +50 %; estamina máxima −20 %; regeneración de estamina −20 % *(gana: vida +20 %, empuje +50 %; precio: estamina máx. y regeneración −20 %)* | `b2.4` |
| `b2.lado_1` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `b2.2` |
| `b2.lado_2` | Armadura | menor | 1 | armadura +1 | `b2.4` |
| `s2.1` | Vida | menor | 1 | vida +3 % | `nucleo_4`, `s2.2` |
| `s2.2` | Baluarte | habilidad | 2 | 8 s de Resistencia II para ti y Resistencia I para los jugadores a 6 bloques; espera 60 s | `s2.1`, `s2.3`, `s2.lado` |
| `s2.3` | Dureza de armadura | menor | 1 | dureza de armadura +0,5 | `s2.2`, `s2.4` |
| `s2.4` | Daño recibido | menor | 1 | daño recibido −3 % | `s2.3`, `s2.puente_1` |
| `s2.lado` | Baluarte II | habilidad | 2 | 10 s (antes 8), radio 9 (antes 6); espera 60 s | `s2.2` |
| `s2.puente_1` | Puente al Curandero | puente | 2 | curación +10 % *(del Curandero)* | `s2.4`, `s2.puente_2` |
| `s2.puente_2` | Regeneración de estamina | cruzado | 2 | regeneración de estamina +5 % *(del Curandero)* | `s2.puente_1`, `s2.puente_3` |
| `s2.puente_3` | Vendaje | cruzado | 2 | cada 8 s sin recibir daño recuperas 1 de vida *(del Curandero)* | `s2.puente_2` |
| `c.tronco_1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `nucleo_5`, `c.tronco_2` |
| `c.tronco_2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `c.tronco_1`, `c.tronco_3` |
| `c.tronco_3` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `c.tronco_2`, `c.tronco_4` |
| `c.tronco_4` | Recuperación | notable | 1 | recuperas 1 de vida cada 5 s | `c.tronco_3`, `c1.1`, `c2.1` |
| `c1.1` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `c.tronco_4`, `c1.2` |
| `c1.2` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c1.1`, `c1.3`, `c1.lado_1` |
| `c1.3` | Raíces | notable | 1 | resistencia al empuje +20 %; estamina máxima +10 % | `c1.2`, `c1.4` |
| `c1.4` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `c1.3`, `c1.5`, `c1.lado_2` |
| `c1.5` | Último bastión | **clave** | 2 | una vez cada 5 min, un golpe mortal te deja a 1 de vida con Resistencia III 3 s; pero mientras espera, vida máxima −20 % *(gana: sobrevives a un golpe mortal; precio: vida −20 % mientras recarga)* | `c1.4` |
| `c1.lado_1` | Vida | menor | 1 | vida +3 % | `c1.2` |
| `c1.lado_2` | Daño recibido | menor | 1 | daño recibido −3 % | `c1.4` |
| `c2.1` | Vida | menor | 1 | vida +3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Desafío | notable | 1 | los monstruos que provocas hacen −15 % de daño 6 s | `c2.2`, `c2.4` |
| `c2.4` | Daño recibido | menor | 1 | daño recibido −3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Imán de golpes | **clave** | 2 | los hostiles a 6 bloques te prefieren siempre y, con 3 o más cerca, recibes −15 %; pero daño c/c −10 % *(gana: −15 % de daño rodeado, atraes a todo; precio: daño c/c −10 %)* | `c2.4` |
| `c2.lado_1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c2.2` |
| `c2.lado_2` | Estamina al parar | menor | 1 | estamina al parar −3 % | `c2.4` |
| `s3.1` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Pisotón | notable | 1 | al caer desde 3 bloques o más empujas lo que hay a 3 bloques y le haces 10 de postura | `s3.1`, `s3.3` |
| `s3.3` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `s3.2`, `s3.4` |
| `s3.4` | Embestida de escudo | habilidad | 3 | cargas 6 bloques al frente; lo que golpeas recibe 6 de daño, 40 de postura y sale empujado; espera 20 s | `s3.3`, `s3.lado`, `s3.puente_1` |
| `s3.lado` | Embestida de escudo II | habilidad | 2 | 8 bloques y aturde 1 s; espera 20 s | `s3.4` |
| `s3.puente_1` | Puente al Mago | puente | 2 | maná máximo +10 % *(del Mago)* | `s3.4`, `s3.puente_2` |
| `s3.puente_2` | Regeneración de maná | cruzado | 2 | regeneración de maná +50 % *(del Mago)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Barrera menor | cruzado | 2 | daño mágico recibido −15 % *(del Mago)* | `s3.puente_2` |

### Mago

Base: vida −10 %, estamina −10 %, hechizos +10 %, espera −10 %, maná +25 %, regeneración de maná ×6. Ramas: A Arcano, B Flujo, C Égida. Sendas: 1 Senda de la nova (puente al Asesino), 2 Senda de la calma (puente al Curandero), 3 Senda del meteoro (puente al Tanque).

Habilidades: **Nova arcana** (V): un anillo de 5 bloques: 6 de daño mágico a los hostiles, y los empuja, espera 20 s; **Nova arcana II** (V2): 9 de daño (antes 6), radio 6 (antes 5), espera 20 s; **Concentración** (B): 8 s con la espera de los hechizos a la mitad y sin coste de maná, espera 60 s; **Concentración II** (B2): 11 s (antes 8), espera 60 s; **Meteoro** (N): tras 1,5 s cae un meteoro donde miras (hasta 24 bloques): 12 de daño mágico en 3 bloques y fuego 3 s; cuesta 40 de maná, espera 40 s; **Meteoro II** (N2): 16 de daño y radio 4.

| Id | Nombre | Tipo | Coste | Efecto | Conecta con |
|---|---|---|---|---|---|
| `origen` | Mago | origen | 0 | la clase: vida −10 %, estamina −10 %, hechizos +10 %, espera −10 %, maná +25 %, regeneración de maná ×6; habilidad I (V): Nova arcana | `nucleo_1`, `nucleo_2`, `nucleo_3`, `nucleo_4`, `nucleo_5`, `nucleo_6` |
| `nucleo_1` | Daño de hechizos | núcleo | 1 | daño de hechizos +3 % | `origen`, `nucleo_2`, `ojo_del_martillo`, `brazo_de_herrero`, `nucleo_6`, `a.tronco_1` |
| `nucleo_2` | Maná máximo | núcleo | 1 | maná máximo +3 % | `origen`, `nucleo_3`, `metal_docil`, `ojo_del_martillo`, `nucleo_1`, `s1.1` |
| `nucleo_3` | Regeneración de maná | núcleo | 1 | regeneración de maná +20 % | `origen`, `nucleo_4`, `remiendo`, `metal_docil`, `nucleo_2`, `b.tronco_1` |
| `nucleo_4` | Maná máximo | núcleo | 1 | maná máximo +3 % | `origen`, `nucleo_5`, `fuelle`, `remiendo`, `nucleo_3`, `s2.1` |
| `nucleo_5` | Daño mágico recibido | núcleo | 1 | daño mágico recibido −3 % | `origen`, `nucleo_6`, `mano_firme`, `fuelle`, `nucleo_4`, `c.tronco_1` |
| `nucleo_6` | Vida | núcleo | 1 | vida +3 % | `origen`, `nucleo_1`, `brazo_de_herrero`, `mano_firme`, `nucleo_5`, `s3.1` |
| `a.tronco_1` | Daño de hechizos | menor | 1 | daño de hechizos +3 % | `nucleo_1`, `a.tronco_2` |
| `a.tronco_2` | Tiempo de carga | menor | 1 | tiempo de carga −3 % | `a.tronco_1`, `a.tronco_3` |
| `a.tronco_3` | Bono de la carga completa | menor | 1 | bono de la carga completa +3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Sobrecarga arcana | notable | 1 | bono de la carga completa +20 % | `a.tronco_3`, `a1.1`, `a2.1` |
| `a1.1` | Daño de hechizos | menor | 1 | daño de hechizos +3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Daño de hechizos | menor | 1 | daño de hechizos +3 % | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Catalizador | notable | 1 | daño de hechizos +5 %; un hechizo que mata devuelve 5 de maná | `a1.2`, `a1.4` |
| `a1.4` | Tiempo de carga | menor | 1 | tiempo de carga −3 % | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Hechizo encadenado | **clave** | 2 | el proyectil del báculo salta a un segundo enemigo a 5 bloques con el 50 % del daño; pero espera de hechizos +20 % *(gana: rebote al 50 %; precio: espera +20 %)* | `a1.4` |
| `a1.lado_1` | Daño de hechizos | menor | 1 | daño de hechizos +3 % | `a1.2` |
| `a1.lado_2` | Maná máximo | menor | 1 | maná máximo +3 % | `a1.4` |
| `a2.1` | Bono de la carga completa | menor | 1 | bono de la carga completa +3 % | `a.tronco_4`, `a2.2` |
| `a2.2` | Tiempo de carga | menor | 1 | tiempo de carga −3 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Carga profunda | notable | 1 | mantener la carga 1 s más allá de llena añade +15 % | `a2.2`, `a2.4` |
| `a2.4` | Bono de la carga completa | menor | 1 | bono de la carga completa +3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Todo o nada | **clave** | 2 | la carga completa vale +60 % más; pero los hechizos sin cargar hacen −40 % *(gana: carga completa +60 %; precio: sin cargar −40 %)* | `a2.4` |
| `a2.lado_1` | Tiempo de carga | menor | 1 | tiempo de carga −3 % | `a2.2` |
| `a2.lado_2` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `a2.4` |
| `s1.1` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `nucleo_2`, `s1.2` |
| `s1.2` | Nova arcana II | habilidad | 2 | 9 de daño (antes 6), radio 6 (antes 5); espera 20 s | `s1.1`, `s1.3` |
| `s1.3` | Maná máximo | menor | 1 | maná máximo +3 % | `s1.2`, `s1.4` |
| `s1.4` | Coste de maná | menor | 1 | coste de maná −3 % | `s1.3`, `s1.puente_1` |
| `s1.puente_1` | Puente al Asesino | puente | 2 | velocidad +3 % *(del Asesino)* | `s1.4`, `s1.puente_2` |
| `s1.puente_2` | Coste de esquiva | cruzado | 2 | coste de esquiva −8 % *(del Asesino)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Puñalada menor | cruzado | 2 | daño por la espalda +20 % *(del Asesino)* | `s1.puente_2` |
| `b.tronco_1` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Espera de hechizos | menor | 1 | espera de hechizos −3 % | `b.tronco_1`, `b.tronco_3` |
| `b.tronco_3` | Coste de maná | menor | 1 | coste de maná −3 % | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Mente clara | notable | 1 | regeneración de maná +100 % | `b.tronco_3`, `b1.1`, `b2.1` |
| `b1.1` | Maná máximo | menor | 1 | maná máximo +3 % | `b.tronco_4`, `b1.2` |
| `b1.2` | Tiempo de carga | menor | 1 | tiempo de carga −3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Canalización | notable | 1 | tiempo de carga −20 %; maná máximo +25 % | `b1.2`, `b1.4` |
| `b1.4` | Maná máximo | menor | 1 | maná máximo +3 % | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Pozo sin fondo | **clave** | 2 | maná máximo +60 %; pero la regeneración de maná a la mitad *(gana: maná +60 %; precio: regeneración ×0,5)* | `b1.4` |
| `b1.lado_1` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `b1.2` |
| `b1.lado_2` | Coste de maná | menor | 1 | coste de maná −3 % | `b1.4` |
| `b2.1` | Coste de maná | menor | 1 | coste de maná −3 % | `b.tronco_4`, `b2.2` |
| `b2.2` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Economía arcana | notable | 1 | coste de maná −20 % | `b2.2`, `b2.4` |
| `b2.4` | Coste de maná | menor | 1 | coste de maná −3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Sangre por maná | **clave** | 2 | sin maná, los hechizos se pagan con vida (1 de vida por cada 5 de maná que falte); pero regeneración de maná −30 % *(gana: lanzar sin maná; precio: vida por maná, regeneración −30 %)* | `b2.4` |
| `b2.lado_1` | Espera de hechizos | menor | 1 | espera de hechizos −3 % | `b2.2` |
| `b2.lado_2` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `b2.4` |
| `s2.1` | Maná máximo | menor | 1 | maná máximo +3 % | `nucleo_4`, `s2.2` |
| `s2.2` | Concentración | habilidad | 2 | 8 s con la espera de los hechizos a la mitad y sin coste de maná; espera 60 s | `s2.1`, `s2.3`, `s2.lado` |
| `s2.3` | Vida | menor | 1 | vida +3 % | `s2.2`, `s2.4` |
| `s2.4` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `s2.3`, `s2.puente_1` |
| `s2.lado` | Concentración II | habilidad | 2 | 11 s (antes 8); espera 60 s | `s2.2` |
| `s2.puente_1` | Puente al Curandero | puente | 2 | curación +10 % *(del Curandero)* | `s2.4`, `s2.puente_2` |
| `s2.puente_2` | Regeneración de estamina | cruzado | 2 | regeneración de estamina +5 % *(del Curandero)* | `s2.puente_1`, `s2.puente_3` |
| `s2.puente_3` | Vendaje | cruzado | 2 | cada 8 s sin recibir daño recuperas 1 de vida *(del Curandero)* | `s2.puente_2` |
| `c.tronco_1` | Daño mágico recibido | menor | 1 | daño mágico recibido −3 % | `nucleo_5`, `c.tronco_2` |
| `c.tronco_2` | Vida | menor | 1 | vida +3 % | `c.tronco_1`, `c.tronco_3` |
| `c.tronco_3` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `c.tronco_2`, `c.tronco_4` |
| `c.tronco_4` | Barrera | notable | 1 | daño mágico recibido −20 % | `c.tronco_3`, `c1.1`, `c2.1` |
| `c1.1` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `c.tronco_4`, `c1.2` |
| `c1.2` | Espera de esquiva | menor | 1 | espera de esquiva −3 % | `c1.1`, `c1.3`, `c1.lado_1` |
| `c1.3` | Paso etéreo | notable | 1 | distancia de esquiva +15 %; espera de esquiva −10 % | `c1.2`, `c1.4` |
| `c1.4` | Coste de esquiva | menor | 1 | coste de esquiva −3 % | `c1.3`, `c1.5`, `c1.lado_2` |
| `c1.5` | Parpadeo | **clave** | 2 | la esquiva es un salto de 5 bloques que atraviesa monstruos (no paredes) y cuesta 15 de maná en vez de estamina; pero espera de esquiva +30 % *(gana: esquiva-teletransporte; precio: cuesta maná, espera +30 %)* | `c1.4` |
| `c1.lado_1` | Velocidad | menor | 1 | velocidad +3 % | `c1.2` |
| `c1.lado_2` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c1.4` |
| `c2.1` | Vida | menor | 1 | vida +3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Daño recibido | menor | 1 | daño recibido −3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Égida | notable | 1 | cada 30 s ganas 4 de absorción | `c2.2`, `c2.4` |
| `c2.4` | Vida | menor | 1 | vida +3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Escudo de maná | **clave** | 2 | el 30 % del daño que recibes lo paga el maná (2 de maná por punto); pero regeneración de maná −25 % *(gana: 30 % del daño al maná; precio: regeneración −25 %)* | `c2.4` |
| `c2.lado_1` | Daño mágico recibido | menor | 1 | daño mágico recibido −3 % | `c2.2` |
| `c2.lado_2` | Vida | menor | 1 | vida +3 % | `c2.4` |
| `s3.1` | Daño mágico recibido | menor | 1 | daño mágico recibido −3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Runa de escarcha | notable | 1 | un hechizo a carga completa deja Lentitud I 2 s | `s3.1`, `s3.3` |
| `s3.3` | Vida | menor | 1 | vida +3 % | `s3.2`, `s3.4` |
| `s3.4` | Meteoro | habilidad | 3 | tras 1,5 s cae un meteoro donde miras (hasta 24 bloques): 12 de daño mágico en 3 bloques y fuego 3 s; cuesta 40 de maná; espera 40 s | `s3.3`, `s3.lado`, `s3.puente_1` |
| `s3.lado` | Meteoro II | habilidad | 2 | 16 de daño y radio 4 | `s3.4` |
| `s3.puente_1` | Puente al Tanque | puente | 2 | vida +5 % *(del Tanque)* | `s3.4`, `s3.puente_2` |
| `s3.puente_2` | Armadura | cruzado | 2 | armadura +1 *(del Tanque)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Represalia menor | cruzado | 2 | quien golpea tu escudo levantado recibe 2 de daño *(del Tanque)* | `s3.puente_2` |

### Curandero

Base: curación +50 %, maná +15 %, regeneración de maná ×4, regeneración de estamina +10 %. Ramas: A Sanación, B Amparo, C Fe. Sendas: 1 Senda del pulso (puente al Tanque), 2 Senda del resurgir (puente al Mago), 3 Senda de la luz (puente al Arquero).

Habilidades: **Pulso sanador** (V): cura 4 (× tu curación) a ti, a los jugadores y a tus animales a 8 bloques, espera 30 s; **Pulso sanador II** (V2): cura 6 (antes 4) y quita Veneno y Marchitamiento, espera 30 s; **Resurgir** (B): el aliado que miras (hasta 16 bloques) recupera el 50 % de la vida que le falta y Regeneración II 5 s, espera 90 s; **Resurgir II** (B2): espera 70 s (antes 90) y Regeneración II 8 s (antes 5), espera 70 s; **Escudo de luz** (N): el aliado que miras, o tú, gana 6 de absorción y −20 % de daño 6 s, espera 35 s; **Escudo de luz II** (N2): 8 de absorción y quita un efecto negativo, espera 35 s.

| Id | Nombre | Tipo | Coste | Efecto | Conecta con |
|---|---|---|---|---|---|
| `origen` | Curandero | origen | 0 | la clase: curación +50 %, maná +15 %, regeneración de maná ×4, regeneración de estamina +10 %; habilidad I (V): Pulso sanador | `nucleo_1`, `nucleo_2`, `nucleo_3`, `nucleo_4`, `nucleo_5`, `nucleo_6` |
| `nucleo_1` | Curación | núcleo | 1 | curación +3 % | `origen`, `nucleo_2`, `ojo_del_martillo`, `brazo_de_herrero`, `nucleo_6`, `a.tronco_1` |
| `nucleo_2` | Maná máximo | núcleo | 1 | maná máximo +3 % | `origen`, `nucleo_3`, `metal_docil`, `ojo_del_martillo`, `nucleo_1`, `s1.1` |
| `nucleo_3` | Daño recibido | núcleo | 1 | daño recibido −3 % | `origen`, `nucleo_4`, `remiendo`, `metal_docil`, `nucleo_2`, `b.tronco_1` |
| `nucleo_4` | Regeneración de maná | núcleo | 1 | regeneración de maná +20 % | `origen`, `nucleo_5`, `fuelle`, `remiendo`, `nucleo_3`, `s2.1` |
| `nucleo_5` | Regeneración de estamina | núcleo | 1 | regeneración de estamina +3 % | `origen`, `nucleo_6`, `mano_firme`, `fuelle`, `nucleo_4`, `c.tronco_1` |
| `nucleo_6` | Vida | núcleo | 1 | vida +3 % | `origen`, `nucleo_1`, `brazo_de_herrero`, `mano_firme`, `nucleo_5`, `s3.1` |
| `a.tronco_1` | Curación | menor | 1 | curación +3 % | `nucleo_1`, `a.tronco_2` |
| `a.tronco_2` | Curación | menor | 1 | curación +3 % | `a.tronco_1`, `a.tronco_3` |
| `a.tronco_3` | Coste de maná | menor | 1 | coste de maná −3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Manos cálidas | notable | 1 | curación +15 % | `a.tronco_3`, `a1.1`, `a2.1` |
| `a1.1` | Curación | menor | 1 | curación +3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Curación | menor | 1 | curación +3 % | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Milagro | notable | 1 | curación +15 %; una cura que llena la vida da 2 de absorción | `a1.2`, `a1.4` |
| `a1.4` | Curación | menor | 1 | curación +3 % | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Mártir | **clave** | 2 | curación +40 %; pero cada cura a otro te cuesta 1 de vida (nunca por debajo de 1) *(gana: curación +40 %; precio: 1 de vida por cura)* | `a1.4` |
| `a1.lado_1` | Maná máximo | menor | 1 | maná máximo +3 % | `a1.2` |
| `a1.lado_2` | Tiempo de carga | menor | 1 | tiempo de carga −3 % | `a1.4` |
| `a2.1` | Curación | menor | 1 | curación +3 % | `a.tronco_4`, `a2.2` |
| `a2.2` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Renuevo | notable | 1 | lo que curas recibe además Regeneración I 3 s | `a2.2`, `a2.4` |
| `a2.4` | Coste de maná | menor | 1 | coste de maná −3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Florecer | **clave** | 2 | tus curas curan el 150 % repartido en 4 s en vez de al instante; no se acumulan en el mismo blanco *(gana: curas ×1,5; precio: sin curas instantáneas)* | `a2.4` |
| `a2.lado_1` | Curación | menor | 1 | curación +3 % | `a2.2` |
| `a2.lado_2` | Vida | menor | 1 | vida +3 % | `a2.4` |
| `s1.1` | Curación | menor | 1 | curación +3 % | `nucleo_2`, `s1.2` |
| `s1.2` | Pulso sanador II | habilidad | 2 | cura 6 (antes 4) y quita Veneno y Marchitamiento; espera 30 s | `s1.1`, `s1.3` |
| `s1.3` | Maná máximo | menor | 1 | maná máximo +3 % | `s1.2`, `s1.4` |
| `s1.4` | Vida | menor | 1 | vida +3 % | `s1.3`, `s1.puente_1` |
| `s1.puente_1` | Puente al Tanque | puente | 2 | vida +5 % *(del Tanque)* | `s1.4`, `s1.puente_2` |
| `s1.puente_2` | Armadura | cruzado | 2 | armadura +1 *(del Tanque)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Represalia menor | cruzado | 2 | quien golpea tu escudo levantado recibe 2 de daño *(del Tanque)* | `s1.puente_2` |
| `b.tronco_1` | Daño recibido | menor | 1 | daño recibido −3 % | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Curación | menor | 1 | curación +3 % | `b.tronco_1`, `b.tronco_3` |
| `b.tronco_3` | Vida | menor | 1 | vida +3 % | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Bendición | notable | 1 | curar a alguien por debajo del 50 % de vida le da Resistencia I 4 s | `b.tronco_3`, `b1.1`, `b2.1` |
| `b1.1` | Curación | menor | 1 | curación +3 % | `b.tronco_4`, `b1.2` |
| `b1.2` | Daño recibido | menor | 1 | daño recibido −3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Purificar | notable | 1 | tus curas quitan Veneno, Marchitamiento, Debilidad y Lentitud | `b1.2`, `b1.4` |
| `b1.4` | Daño mágico recibido | menor | 1 | daño mágico recibido −3 % | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Tierra sagrada | **clave** | 2 | Pulso sanador deja 6 s un círculo de 4 bloques donde los aliados reciben −25 %; pero su espera +50 % *(gana: zona de −25 % de daño; precio: espera de Pulso +50 %)* | `b1.4` |
| `b1.lado_1` | Vida | menor | 1 | vida +3 % | `b1.2` |
| `b1.lado_2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `b1.4` |
| `b2.1` | Vida | menor | 1 | vida +3 % | `b.tronco_4`, `b2.2` |
| `b2.2` | Curación | menor | 1 | curación +3 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Vínculo | notable | 1 | te curas el 20 % de lo que curas a otros (además del tercio de la regla) | `b2.2`, `b2.4` |
| `b2.4` | Vida | menor | 1 | vida +3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Lazo vital | **clave** | 2 | vida máxima +10 %; pero recibes tú el 25 % del daño del aliado más cercano a 8 bloques *(gana: vida +10 %, proteges a un aliado; precio: te comes el 25 % de su daño)* | `b2.4` |
| `b2.lado_1` | Daño recibido | menor | 1 | daño recibido −3 % | `b2.2` |
| `b2.lado_2` | Maná máximo | menor | 1 | maná máximo +3 % | `b2.4` |
| `s2.1` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `nucleo_4`, `s2.2` |
| `s2.2` | Resurgir | habilidad | 2 | el aliado que miras (hasta 16 bloques) recupera el 50 % de la vida que le falta y Regeneración II 5 s; espera 90 s | `s2.1`, `s2.3`, `s2.lado` |
| `s2.3` | Vida | menor | 1 | vida +3 % | `s2.2`, `s2.4` |
| `s2.4` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `s2.3`, `s2.puente_1` |
| `s2.lado` | Resurgir II | habilidad | 2 | espera 70 s (antes 90) y Regeneración II 8 s (antes 5); espera 70 s | `s2.2` |
| `s2.puente_1` | Puente al Mago | puente | 2 | maná máximo +10 % *(del Mago)* | `s2.4`, `s2.puente_2` |
| `s2.puente_2` | Regeneración de maná | cruzado | 2 | regeneración de maná +50 % *(del Mago)* | `s2.puente_1`, `s2.puente_3` |
| `s2.puente_3` | Barrera menor | cruzado | 2 | daño mágico recibido −15 % *(del Mago)* | `s2.puente_2` |
| `c.tronco_1` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `nucleo_5`, `c.tronco_2` |
| `c.tronco_2` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `c.tronco_1`, `c.tronco_3` |
| `c.tronco_3` | Vida | menor | 1 | vida +3 % | `c.tronco_2`, `c.tronco_4` |
| `c.tronco_4` | Serenidad | notable | 1 | regeneración de maná +100 %; regeneración de estamina +10 % | `c.tronco_3`, `c1.1`, `c2.1` |
| `c1.1` | Vida | menor | 1 | vida +3 % | `c.tronco_4`, `c1.2` |
| `c1.2` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `c1.1`, `c1.3`, `c1.lado_1` |
| `c1.3` | Aura | notable | 1 | tú y tus aliados a 6 bloques recuperáis 0,5 de vida cada 3 s | `c1.2`, `c1.4` |
| `c1.4` | Curación | menor | 1 | curación +3 % | `c1.3`, `c1.5`, `c1.lado_2` |
| `c1.5` | Peregrino | **clave** | 2 | el Aura llega a 10 bloques y cura 1 cada 3 s; pero todo tu daño −20 % *(gana: Aura doble y más grande; precio: todo tu daño −20 %)* | `c1.4` |
| `c1.lado_1` | Vida | menor | 1 | vida +3 % | `c1.2` |
| `c1.lado_2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `c1.4` |
| `c2.1` | Vida | menor | 1 | vida +3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Voluntad | notable | 1 | vida +8 % | `c2.2`, `c2.4` |
| `c2.4` | Daño recibido | menor | 1 | daño recibido −3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Martillo de la fe | **clave** | 2 | tus golpes c/c curan a los aliados a 4 bloques el 20 % del daño hecho; pero curación −20 % *(gana: curar pegando; precio: curación −20 %)* | `c2.4` |
| `c2.lado_1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c2.2` |
| `c2.lado_2` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `c2.4` |
| `s3.1` | Vida | menor | 1 | vida +3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Rocío | notable | 1 | cuando un aliado a 16 bloques baja del 25 % de vida, tu siguiente cura sobre él es +50 % (cada 20 s) | `s3.1`, `s3.3` |
| `s3.3` | Maná máximo | menor | 1 | maná máximo +3 % | `s3.2`, `s3.4` |
| `s3.4` | Escudo de luz | habilidad | 3 | el aliado que miras, o tú, gana 6 de absorción y −20 % de daño 6 s; espera 35 s | `s3.3`, `s3.lado`, `s3.puente_1` |
| `s3.lado` | Escudo de luz II | habilidad | 2 | 8 de absorción y quita un efecto negativo; espera 35 s | `s3.4` |
| `s3.puente_1` | Puente al Arquero | puente | 2 | tensado +5 % *(del Arquero)* | `s3.4`, `s3.puente_2` |
| `s3.puente_2` | Daño de proyectiles | cruzado | 2 | daño de proyectiles +4 % *(del Arquero)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Ojo de halcón menor | cruzado | 2 | tiros a la cabeza +15 % *(del Arquero)* | `s3.puente_2` |

### Arquero

Base: vida −10 %, velocidad +8 %, proyectiles +15 %, tensado +10 %, esquiva +20 %, espera de esquiva −15 %, caída −25 %. Ramas: A Puntería, B Tensión, C Viento. Sendas: 1 Senda del salto (puente al Asesino), 2 Senda de la lluvia (puente al Guerrero), 3 Senda del trampero (puente al Curandero).

Habilidades: **Salto atrás** (V): un salto hacia atrás de unos 6 bloques y Caída lenta 2 s, espera 12 s; **Salto atrás II** (V2): espera 8 s (antes 12) y la siguiente flecha en 3 s hace +25 %, espera 8 s; **Lluvia de flechas** (B): 12 flechas en 2 s sobre un círculo de 3 bloques donde miras (hasta 32), 4 de daño cada una, espera 45 s; **Lluvia de flechas II** (B2): 18 flechas (antes 12) en un círculo de 4 bloques (antes 3), espera 45 s; **Flecha de red** (N): una flecha que al impactar atrapa a los monstruos a 3 bloques (Lentitud IV 3 s), espera 20 s; **Flecha de red II** (N2): radio 4, 4 s, y los atrapados reciben +15 % de tus flechas, espera 20 s.

| Id | Nombre | Tipo | Coste | Efecto | Conecta con |
|---|---|---|---|---|---|
| `origen` | Arquero | origen | 0 | la clase: vida −10 %, velocidad +8 %, proyectiles +15 %, tensado +10 %, esquiva +20 %, espera de esquiva −15 %, caída −25 %; habilidad I (V): Salto atrás | `nucleo_1`, `nucleo_2`, `nucleo_3`, `nucleo_4`, `nucleo_5`, `nucleo_6` |
| `nucleo_1` | Daño de proyectiles | núcleo | 1 | daño de proyectiles +3 % | `origen`, `nucleo_2`, `ojo_del_martillo`, `brazo_de_herrero`, `nucleo_6`, `a.tronco_1` |
| `nucleo_2` | Tensado | núcleo | 1 | tensado +3 % | `origen`, `nucleo_3`, `metal_docil`, `ojo_del_martillo`, `nucleo_1`, `s1.1` |
| `nucleo_3` | Velocidad de flecha | núcleo | 1 | velocidad de flecha +3 % | `origen`, `nucleo_4`, `remiendo`, `metal_docil`, `nucleo_2`, `b.tronco_1` |
| `nucleo_4` | Velocidad | núcleo | 1 | velocidad +3 % | `origen`, `nucleo_5`, `fuelle`, `remiendo`, `nucleo_3`, `s2.1` |
| `nucleo_5` | Distancia de esquiva | núcleo | 1 | distancia de esquiva +3 % | `origen`, `nucleo_6`, `mano_firme`, `fuelle`, `nucleo_4`, `c.tronco_1` |
| `nucleo_6` | Vida | núcleo | 1 | vida +3 % | `origen`, `nucleo_1`, `brazo_de_herrero`, `mano_firme`, `nucleo_5`, `s3.1` |
| `a.tronco_1` | Daño de proyectiles | menor | 1 | daño de proyectiles +3 % | `nucleo_1`, `a.tronco_2` |
| `a.tronco_2` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `a.tronco_1`, `a.tronco_3` |
| `a.tronco_3` | Daño de proyectiles | menor | 1 | daño de proyectiles +3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Ojo de halcón | notable | 1 | daño de proyectiles +6 % | `a.tronco_3`, `a1.1`, `a2.1` |
| `a1.1` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Tiro a la cabeza | notable | 1 | tiros a la cabeza +20 % | `a1.2`, `a1.4` |
| `a1.4` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Francotirador | **clave** | 2 | tiros a la cabeza +40 % y Tiro lejano al doble; pero a menos de 8 bloques haces −30 % *(gana: cabeza +40 %, lejano ×2; precio: −30 % de cerca)* | `a1.4` |
| `a1.lado_1` | Tensado | menor | 1 | tensado +3 % | `a1.2` |
| `a1.lado_2` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `a1.4` |
| `a2.1` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `a.tronco_4`, `a2.2` |
| `a2.2` | Tensado | menor | 1 | tensado +3 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Tiro lejano | notable | 1 | +2 % por bloque más allá de 10, hasta +30 % | `a2.2`, `a2.4` |
| `a2.4` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Ojo de águila | **clave** | 2 | agachado y quieto 1 s, tus flechas vuelan rectas 40 bloques y hacen +15 %; pero no puedes esquivar 1 s tras disparar *(gana: tiro recto +15 %; precio: sin esquiva 1 s tras disparar)* | `a2.4` |
| `a2.lado_1` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `a2.2` |
| `a2.lado_2` | Daño de caída | menor | 1 | daño de caída −3 % | `a2.4` |
| `s1.1` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `nucleo_2`, `s1.2` |
| `s1.2` | Salto atrás II | habilidad | 2 | espera 8 s (antes 12) y la siguiente flecha en 3 s hace +25 %; espera 8 s | `s1.1`, `s1.3` |
| `s1.3` | Tensado | menor | 1 | tensado +3 % | `s1.2`, `s1.4` |
| `s1.4` | Velocidad | menor | 1 | velocidad +3 % | `s1.3`, `s1.puente_1` |
| `s1.puente_1` | Puente al Asesino | puente | 2 | velocidad +3 % *(del Asesino)* | `s1.4`, `s1.puente_2` |
| `s1.puente_2` | Coste de esquiva | cruzado | 2 | coste de esquiva −8 % *(del Asesino)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Puñalada menor | cruzado | 2 | daño por la espalda +20 % *(del Asesino)* | `s1.puente_2` |
| `b.tronco_1` | Tensado | menor | 1 | tensado +3 % | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Tensado | menor | 1 | tensado +3 % | `b.tronco_1`, `b.tronco_3` |
| `b.tronco_3` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Mano rápida | notable | 1 | tensado +12 % | `b.tronco_3`, `b1.1`, `b2.1` |
| `b1.1` | Tensado | menor | 1 | tensado +3 % | `b.tronco_4`, `b1.2` |
| `b1.2` | Coste de estamina | menor | 1 | coste de estamina −3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Flecha veloz | notable | 1 | velocidad de flecha +15 % | `b1.2`, `b1.4` |
| `b1.4` | Tensado | menor | 1 | tensado +3 % | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Ráfaga | **clave** | 2 | el arco a 75 % de tensión dispara como a tensión completa; pero daño de proyectiles −20 % *(gana: un tercio más de disparos; precio: proyectiles −20 %)* | `b1.4` |
| `b1.lado_1` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `b1.2` |
| `b1.lado_2` | Velocidad | menor | 1 | velocidad +3 % | `b1.4` |
| `b2.1` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `b.tronco_4`, `b2.2` |
| `b2.2` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Tiro certero | notable | 1 | un tiro a tensión completa deja Lentitud II 2 s | `b2.2`, `b2.4` |
| `b2.4` | Tensado | menor | 1 | tensado +3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Flecha perforante | **clave** | 2 | las flechas a tensión completa atraviesan hasta 2 enemigos; pero tensado −20 % *(gana: atraviesa 2; precio: tensado −20 %)* | `b2.4` |
| `b2.lado_1` | Daño de caída | menor | 1 | daño de caída −3 % | `b2.2` |
| `b2.lado_2` | Vida | menor | 1 | vida +3 % | `b2.4` |
| `s2.1` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `nucleo_4`, `s2.2` |
| `s2.2` | Lluvia de flechas | habilidad | 2 | 12 flechas en 2 s sobre un círculo de 3 bloques donde miras (hasta 32), 4 de daño cada una; espera 45 s | `s2.1`, `s2.3`, `s2.lado` |
| `s2.3` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `s2.2`, `s2.4` |
| `s2.4` | Velocidad | menor | 1 | velocidad +3 % | `s2.3`, `s2.puente_1` |
| `s2.lado` | Lluvia de flechas II | habilidad | 2 | 18 flechas (antes 12) en un círculo de 4 bloques (antes 3); espera 45 s | `s2.2` |
| `s2.puente_1` | Puente al Guerrero | puente | 2 | regeneración de estamina +5 % *(del Guerrero)* | `s2.4`, `s2.puente_2` |
| `s2.puente_2` | Daño de postura | cruzado | 2 | daño de postura +5 % *(del Guerrero)* | `s2.puente_1`, `s2.puente_3` |
| `s2.puente_3` | Réplica menor | cruzado | 2 | una parada perfecta te devuelve 10 de estamina *(del Guerrero)* | `s2.puente_2` |
| `c.tronco_1` | Velocidad | menor | 1 | velocidad +3 % | `nucleo_5`, `c.tronco_2` |
| `c.tronco_2` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `c.tronco_1`, `c.tronco_3` |
| `c.tronco_3` | Espera de esquiva | menor | 1 | espera de esquiva −3 % | `c.tronco_2`, `c.tronco_4` |
| `c.tronco_4` | Zancada | notable | 1 | velocidad +5 % | `c.tronco_3`, `c1.1`, `c2.1` |
| `c1.1` | Coste de esquiva | menor | 1 | coste de esquiva −3 % | `c.tronco_4`, `c1.2` |
| `c1.2` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `c1.1`, `c1.3`, `c1.lado_1` |
| `c1.3` | Rodar | notable | 1 | coste de esquiva −20 %; invulnerabilidad de esquiva +1 tick | `c1.2`, `c1.4` |
| `c1.4` | Espera de esquiva | menor | 1 | espera de esquiva −3 % | `c1.3`, `c1.5`, `c1.lado_2` |
| `c1.5` | Disparo en carrera | **clave** | 2 | tensas y disparas esquivando y esprintando sin frenar; pero vida máxima −10 % *(gana: disparar en movimiento; precio: vida −10 %)* | `c1.4` |
| `c1.lado_1` | Velocidad | menor | 1 | velocidad +3 % | `c1.2` |
| `c1.lado_2` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c1.4` |
| `c2.1` | Daño de caída | menor | 1 | daño de caída −3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Salto | menor | 1 | salto +3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Pluma | notable | 1 | daño de caída −40 %; salto +12 % | `c2.2`, `c2.4` |
| `c2.4` | Salto | menor | 1 | salto +3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Halcón | **clave** | 2 | en el aire tus flechas hacen +25 %; pero en el suelo −10 % *(gana: +25 % en el aire; precio: −10 % en el suelo)* | `c2.4` |
| `c2.lado_1` | Velocidad | menor | 1 | velocidad +3 % | `c2.2` |
| `c2.lado_2` | Daño de caída | menor | 1 | daño de caída −3 % | `c2.4` |
| `s3.1` | Daño de caída | menor | 1 | daño de caída −3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Marca del cazador | notable | 1 | la primera flecha que acierta marca 8 s: tus siguientes flechas le hacen +10 % (no se suma a Marca de muerte) | `s3.1`, `s3.3` |
| `s3.3` | Velocidad | menor | 1 | velocidad +3 % | `s3.2`, `s3.4` |
| `s3.4` | Flecha de red | habilidad | 3 | una flecha que al impactar atrapa a los monstruos a 3 bloques (Lentitud IV 3 s); espera 20 s | `s3.3`, `s3.lado`, `s3.puente_1` |
| `s3.lado` | Flecha de red II | habilidad | 2 | radio 4, 4 s, y los atrapados reciben +15 % de tus flechas; espera 20 s | `s3.4` |
| `s3.puente_1` | Puente al Curandero | puente | 2 | curación +10 % *(del Curandero)* | `s3.4`, `s3.puente_2` |
| `s3.puente_2` | Regeneración de estamina | cruzado | 2 | regeneración de estamina +5 % *(del Curandero)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Vendaje | cruzado | 2 | cada 8 s sin recibir daño recuperas 1 de vida *(del Curandero)* | `s3.puente_2` |

<!-- NODOS:FIN -->
