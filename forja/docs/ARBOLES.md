# Árboles de clase

Los árboles pequeños de antes (3 ramas × 3 niveles y la habilidad B) se sustituyeron por **un árbol grande por clase**,
con el esquema que aprobó Andy. Paso 1 (diseño) el 2026-09-30; paso 2 (construido, con las respuestas de Andy) el mismo
día; paso 3 (las tres habilidades finales, una por senda) también el 2026-09-30.

**Dónde viven los números.** Todo el árbol está en **`tools/arboles_datos.py`**: nodos, costes, habilidades, hitos,
tope de nivel, puntos por nivel y curva de experiencia. **`tools/arboles.py`** lo coloca, lo comprueba y escribe:

- `src/main/resources/forja_arboles.json`, que el juego lee al arrancar (`clase/ClassTree`);
- `docs/arboles.json`, una copia para leer;
- las tablas y los cálculos de este documento (entre las marcas `<!-- ... -->`, no se editan a mano);
- con `--imagenes DIR`, los dibujos de los árboles del Guerrero y del Mago.

`tools/generate_lang.py` toma de ahí todos los textos de los nodos, las habilidades, las regiones y los hitos.
**Cambiar un coste, un número o el tope es cambiar `arboles_datos.py` y ejecutar los dos scripts**: no hay que tocar
Java.

Imágenes: `E:\IA\Claude\Forja_capturas_mejoras\arbol\arbol_guerrero.png` y `arbol_mago.png` (dibujadas desde los
datos, con una partida de nivel 50 en dorado y una sola final; las finales sin elegir, en gris con candado). En la
misma carpeta, `hoja_arbol.png`: la hoja de contactos de la prueba de cliente (`FORJA_SOLO=arbol`), y en `ultimas\`,
`hoja_ultimas.png`: la de las habilidades finales.

## Lo que pidió Andy, y lo que decidió

1. Un árbol de 80-100 nodos **por clase**:
   - un núcleo de 6 nodos;
   - 3 ramas: un tronco de 4 nodos que acaba en un notable y se parte en dos caminos de 5 (notable a la mitad y
     **clave** al final), con nodos laterales;
   - 3 sendas entre las ramas, con un notable, que acaban en un **puente** a otra clase, donde los nodos cuestan más;
   - nodos menores (+3 %), notables (un efecto con carácter) y claves (cambian la forma de jugar y tienen precio);
   - solo se coge un nodo pegado a otro que ya tienes.
2. Puntos por nivel **más hitos** (logros, jefes, el primer campeón...), con un **tope de hitos por nivel**.
3. **Quitar la clase Herrero** y poner la forja en todos los árboles. El Herrero desaparece sin más: quien lo era se
   queda sin clase y elige otra. Forjar no da experiencia de clase a nadie.
4. Los puentes se quedan.
5. Reiniciar: el Medallón del olvido, y además la **Vela del olvido**, más barata.
6. Las habilidades se mejoran desde el árbol («Grito de guerra II»).
7. **Tres habilidades por clase**: V, B y **N** (cambiable en Controles). Las seis terceras habilidades, aprobadas.
8. **Temple de campaña** no es una tecla: repara despacio, solo, la pieza forjada de la mano (y la armadura, con la
   II).
9. En el tope se compra **~87 % del árbol**, subiendo el tope de nivel y bajando los puntos por nivel. Los costes se
   quedan como estaban y viven en los datos, para poder cambiar la proporción.
10. Los hitos de la lista, más **el Warden** (como un jefe) y **la ciudad antigua**.
11. **GearScore** cuenta los puntos gastados.
12. Andy (2026-09-30, confirmado): «1 habilidad por cada senda, y solo puedes escoger una habilidad final, pero sí puedes
    mejorar las otras sendas». Cada senda acaba en su propia **habilidad final** (tres por clase, 18 en total, cada una
    con su II); solo se aprende **una**, que va a la tecla N; el resto de las otras dos sendas (menores, notables, el
    puente y lo de la otra clase) se sigue aprendiendo. V II, B y B II salen de las sendas y pasan a las ramas A y B.

## Forma del árbol

Es igual en las seis clases: 101 nodos cada una, contando los 13 de la forja.

```
                   clave              clave           (una de las dos: se excluyen)
                     |  (2 laterales)   |
                  camino 1          camino 2          cada camino: menor, menor, NOTABLE, menor, CLAVE
                         \          /
                          notable                     (fin del tronco)
                             |
                          tronco ×3                   RAMA A (arriba)
                             |
  senda 3 → FINAL    forja – PUERTA A – forja        senda 1 → FINAL
          ↘ puente                                              ↘ puente
                 puerta       |        puerta
                      \    ORIGEN     /               el origen es la clase y su habilidad V
                 puerta       |        puerta
     RAMA C (abajo izq.)  forja – puerta – forja      RAMA B (abajo dcha.)
                             |
                     senda 2 → FINAL (abajo)
                             ↘ puente
```

Cada senda: puerta, 3 nodos propios y, desde el último, dos salidas: recto, su **habilidad final** (un rombo dorado)
con su II detrás; de lado (17° hacia la rama siguiente), el **puente** y los dos nodos de la otra clase. Así el
puente no pasa por la final: se puede cruzar a la otra clase sin haberla elegido.

- **Origen** (gratis): la clase, sus números de base y la habilidad I (V).
- **Núcleo**: 6 puertas alrededor del origen. Todas tocan el origen y se tocan entre sí en anillo. Abren las ramas A
  (arriba), B (abajo a la derecha) y C (abajo a la izquierda), y entre ellas las sendas 1, 2 y 3.
- **Región de la forja**: 13 nodos naranjas, iguales en todas las clases, en los huecos entre las puertas.
- **Ramas** (18 nodos): tronco de 3 menores y un notable; dos caminos de 5; 2 laterales por camino. **Las dos claves
  de una rama se excluyen**: con 103 puntos se puede llegar a casi todo, así que cada rama obliga a elegir una forma de
  jugar (decisión mía para que las claves no se apilen; ver «Equilibrio»). Del segundo nodo del tronco cuelgan además
  las habilidades normales: **V II** en la rama A y **B** (con **B II** detrás) en la rama B; son hojas, no acortan
  ningún camino.
- **Sendas**: 3 nodos propios (la senda 3 con su notable), y al final la **habilidad final** de esa senda con su II, y
  el puente con los dos nodos de la otra clase. **Solo se tiene una final**: al aprender una, las otras dos y sus II
  quedan cerradas (`ClassProgress.Refusal.ULTIMATE`); lo demás de esas sendas sigue abierto.
- Solo se aprende un nodo pegado al origen o a algo aprendido.

### Costes (en los datos: `COST` y `SKILL_COST`)

| Tipo | Puntos |
|---|---|
| Puerta, menor, notable, forja | 1 |
| Clave | 2 |
| Habilidad B, V II, B II (en las ramas) | 2 (B y V II a 5 puntos, B II a 7) |
| Habilidad final (N) | 3 (las tres a 7 puntos del origen) |
| Su II | 2 |
| Puente y nodos de otra clase | 2 cada uno |

**El menor (+3 %)** y sus equivalencias para lo que no es un porcentaje: armadura +1, dureza +0,5, ventana de parada
+1 tick, resistencia al empuje +5 %, regeneración de maná +0,2. La invulnerabilidad de esquiva solo la dan notables.

Todos los nodos juntos cuestan **127 puntos**, pero de las tres finales solo se tiene una: **lo que se puede tener cuesta
117** (127 menos las otras dos finales con su II, 5 cada una), lo mismo que el árbol de antes. **La regla de equidad**:
toda clave está a **11 puntos** del origen y toda final a **7** (lo comprueban `theTreesHoldTogether` y
`tools/arboles.py`), así que ninguna clave ni ninguna final sale más barata que sus hermanas.

## Niveles, puntos e hitos

Andy (2026-09-30): ~87 % del árbol en el tope, subiendo el tope y bajando los puntos por nivel.

- **Tope de nivel: 50.**
- **Puntos de nivel**: `floor(7 × nivel / 5)`, es decir 1 o 2 por nivel (7 cada 5 niveles): 1, 2, 4, 5, 7... y **70
  en el nivel 50**.
- **Experiencia** para pasar del nivel N al N+1: `30 + 5 × (N − 1)`. Los primeros niveles son rápidos (30, 35, 40...);
  llegar al 50 pide **7350** de experiencia, 1,5 veces lo que pedía el 15 de antes (4760).
- **Hitos: 33 puntos**, una vez por jugador. Son del jugador, no de la clase: se quedan al morir, al cambiar de clase y
  sin clase.
- **Tope de hitos: 1 por nivel de clase.** En el nivel 10 se gastan como mucho 10; los 33 desde el nivel 33. Los que
  esperan se ven en la pantalla («+N al subir de nivel»). Así quien cambia de clase con todos los hitos no empieza con
  34 puntos.
- **En el tope: 70 + 33 = 103 puntos, el 88 % de lo que se puede tener** (103 de 117: todo menos dos finales).
- Los hitos se dan **con efecto retroactivo**: al entrar y cada 5 segundos se miran los logros y las estadísticas.

| Hito | Puntos | Cómo se detecta |
|---|---|---|
| Primer élite | 1 | al matarlo (amenaza élite o campeón) |
| Primer campeón | 2 | logro `forja:forja/elite` |
| Sin capitán | 1 | logro `forja:forja/saqueadores` |
| El Herrero Caído | 4 | logro `forja:forja/herrero_caido` |
| El Guardián de Cuño | 4 | estadística de muertes |
| El Warden | 4 | estadística de muertes |
| El Wither | 2 | estadística de muertes |
| El dragón | 3 | logro `minecraft:end/kill_dragon` |
| Guardián anciano | 1 | estadística de muertes |
| Héroe de la aldea | 1 | logro `minecraft:adventure/hero_of_the_village` |
| Al Nether | 1 | logro `minecraft:story/enter_the_nether` |
| Al End | 1 | logro `minecraft:story/enter_the_end` |
| Ciudad antigua | 1 | estar dentro de una (`minecraft:ancient_city`) |
| Entre estrellas | 1 | logro `forja:forja/portal` |
| Golpe limpio | 1 | logro `forja:forja/perfecta` |
| Mano hecha | 1 | maestría de herrero 5 |
| Maestro del gremio | 2 | maestría de herrero 10 |
| Obra maestra | 1 | logro `forja:forja/obra_maestra` |
| Maestro forjador | 1 | logro `forja:forja/maestro` |
| **Total** | **33** | 19 hitos |

## La forja en todos los árboles (sin Herrero)

13 nodos en los huecos entre las puertas, iguales en todas las clases (`forja.*`), 13 puntos en total:

- 6 interiores, cada uno pegado a las dos puertas que tiene al lado;
- 6 exteriores, colgados de los interiores;
- Temple de campaña II, colgado de Temple.

| Nodo | Efecto |
|---|---|
| Ojo del martillo / Golpe de maestro | ventana del golpe perfecto +0,01 cada uno |
| Metal dócil / Alma del metal | potencial al forjar +5 cada uno |
| Remiendo | cada lingote repara +25 % |
| Fuelle | las montadoras a 8 bloques van un 20 % más rápido |
| Ajuste fino | lo que montan las montadoras a 8 bloques nace con potencial +5 |
| Mano firme | +1 % por ingrediente de mejora |
| Carga honda | +2 de carga en lo que forjas (componente `forja:carga_extra` en la pieza) |
| Brazo de herrero | +8 % con martillo, mazo, pico y hacha |
| Forja al rojo | tras una forja perfecta, 60 s de golpes c/c que prenden 3 s y hacen +10 % |
| **Temple de campaña** | la pieza forjada de la mano recupera el 1 % de su durabilidad cada minuto |
| **Temple de campaña II** | el 2 % cada minuto, y también la armadura forjada puesta |

Fuelle y Ajuste fino funcionan por cercanía (el jugador a 8 bloques de la máquina), no por quién la colocó: así no hace
falta guardar un dueño en cada bloque.

**El Herrero, fuera.** Sale de `PlayerClass`, de la elección, de los factores de daño, del libro V y de la experiencia
por forjar. Un guardado que dice «herrero» se lee como **sin clase**: al entrar, un mensaje lo explica y se elige otra
gratis con K. Sus habilidades: Temple de campaña es ahora el nodo pasivo de arriba, y Forja al rojo, un nodo pasivo
tras la forja perfecta.

## Las habilidades: V, B y las tres finales

Teclas **V** (habilidad I, viene con la clase), **B** (rama B) y **N** (la final que elijas), todas `KeyMapping` en
«Forja: clases». La II **sustituye** los números de la habilidad (están enteros en los datos).

| Clase | V | B |
|---|---|---|
| Guerrero | Grito de guerra | Postura de hierro |
| Asesino | Paso sombrío | Marca de muerte |
| Tanque | Provocar | Baluarte |
| Mago | Nova arcana | Concentración |
| Curandero | Pulso sanador | Resurgir |
| Arquero | Salto atrás | Lluvia de flechas |

### Las habilidades finales (tecla N, una de tres)

Cada senda acaba en la suya, con el aire de la senda y de la clase a la que lleva su puente. Las seis N de antes son la
final de la senda 3; las otras doce son nuevas. Fuertes, con esperas de verdad, y hechas con lo que tiene el combate
del mod: postura (rompe guardias y corta avisos, porque un monstruo aturdido suelta su ataque), estamina, maná,
avisos propios (la Saeta letal se ve venir) y los factores de daño de la clase. Cuestan 3 puntos y su II 2; las tres
están a 7 puntos del origen.

| Clase | Senda (puente) | Final | Qué hace | Espera | Coste | II |
|---|---|---|---|---|---|---|
| Guerrero | 1 grito (Tanque) | **Bramido** | los hostiles a 6 bloques: 40 de postura y Debilidad I 5 s; tú, 2 de absorción por cada uno (hasta 10) | 45 s | 30 estamina | 8 bloques, 60 de postura, 3 por cada uno (hasta 12) |
| Guerrero | 2 hierro (Asesino) | **Hendedura** | tajo de arriba abajo al frente (3,5 bloques, 100°): 200 % del arma y 50 de postura | 30 s | 35 estamina | 250 % y 80 de postura |
| Guerrero | 3 torbellino (Arquero) | **Torbellino** | todo a 3 bloques: 80 % del arma y 30 de postura | 30 s | 30 estamina | dos vueltas, 45 de postura |
| Asesino | 1 sombra (Guerrero) | **Danza de sombras** | saltas a la espalda de hasta 4 enemigos a 8 bloques: 100 % del arma a cada uno (cuenta como puñalada); nada te daña mientras bailas | 40 s | 30 estamina | 6 enemigos, 120 % |
| Asesino | 2 marca (Arquero) | **Ejecución** | apareces detrás del que miras (16 bloques): 150 % del arma, el doble si le queda menos del 35 % | 30 s | 25 estamina | 200 %; si muere, media espera |
| Asesino | 3 veneno (Mago) | **Abanico de dagas** | 5 dagas en 60°, 4 de daño y Veneno I 3 s | 25 s | — | 7 dagas, Veneno II |
| Tanque | 1 desafío (Guerrero) | **Golpe sísmico** | los hostiles a 5 bloques: 8 de daño, 50 de postura, saltan, Lentitud II 3 s | 35 s | 30 estamina | 6 bloques, 10 de daño, 70 de postura |
| Tanque | 2 baluarte (Curandero) | **Santuario de acero** | 8 s, un círculo de 4 bloques: Resistencia I y Regeneración I a los aliados dentro y a ti; echa fuera a los hostiles | 60 s | 30 estamina | 10 s, 5 bloques, tú Resistencia II |
| Tanque | 3 embestida (Mago) | **Embestida de escudo** | carga de 6 bloques, 6 de daño, 40 de postura, empuja | 20 s | — | 8 bloques, y aturde |
| Mago | 1 nova (Asesino) | **Relámpago en cadena** | al que miras (16 bloques): 10 de daño mágico, salta a 4 más a 5 bloques, −20 % por salto | 25 s | 35 maná | 12 de daño, 6 saltos |
| Mago | 2 calma (Curandero) | **Prisión de hielo** | los hostiles a 4 bloques de donde miras (24): 6 de daño mágico, 3 s congelados (Lentitud VII) y, si no son jefes, aturdidos | 45 s | 45 maná | 5 bloques, 4 s, 8 de daño |
| Mago | 3 meteoro (Tanque) | **Meteoro** | tras 1,5 s, 12 de daño mágico en 3 bloques y fuego 3 s | 40 s | 40 maná | 16 en 4 bloques |
| Curandero | 1 pulso (Tanque) | **Oleada de vida** | cura 8 (× tu curación) a ti y a los aliados a 10 bloques, Regeneración II 4 s, empuja a los hostiles a 4 | 60 s | 50 maná | cura 10 y quita los efectos negativos |
| Curandero | 2 resurgir (Mago) | **Segunda vida** | el aliado que miras, o tú: 20 s en que el primer golpe mortal le deja con el 40 % de su vida | 120 s | 40 maná | 30 s, 60 % |
| Curandero | 3 luz (Arquero) | **Escudo de luz** | 6 de absorción y Resistencia I 6 s a un aliado o a ti | 35 s | — | 8 de absorción y quita un mal |
| Arquero | 1 salto (Asesino) | **Saeta letal** | apuntas 0,75 s (una línea de luz lo avisa) y la saeta atraviesa todo en 32 bloques: 12 de daño, ×1,5 a lo que tiene menos del 50 % | 30 s | 25 estamina | apuntas 0,5 s, 15 de daño |
| Arquero | 2 lluvia (Guerrero) | **Flecha explosiva** | estalla donde miras (32 bloques): 8 de daño, 40 de postura y empuje a 3 bloques; no rompe bloques | 25 s | 20 estamina | 4 bloques, 10 de daño, 60 de postura |
| Arquero | 3 trampero (Curandero) | **Flecha de red** | Lentitud IV 3 s a lo que hay a 3 bloques de donde miras | 20 s | — | 4 bloques, 4 s, +15 % de tus flechas |

Todas pasan por los factores de daño de su clase: lo que pega con el arma (Hendedura, Torbellino, Danza, Ejecución,
Golpe sísmico, Embestida) es cuerpo a cuerpo; las dagas, la Saeta y la Flecha explosiva, proyectiles; el Relámpago, la
Prisión y el Meteoro, magia (con el daño de hechizos del árbol, como la Nova). La espera de las finales no la toca
ninguna clave.

**Elegir y cambiar.** Aprender una final cierra las otras dos y sus II; el resto de esas sendas se sigue comprando.
Cambiarla es un reinicio: la **Vela del olvido** quita la final como hoja (con su II a la vez, que entonces no cuenta:
3 puntos, dentro de los 4 de una vela) y libera la elección; el **Medallón** vacía el árbol entero.

**Lo que se arregló de paso.** Las absorciones de las habilidades y nodos (Provocar II, Escudo de luz, Égida, Milagro, y
ahora el Bramido) no daban nada: el juego limita la absorción al atributo `max_absorption`, que es 0 sin el efecto de
Absorción. Ahora suben ese tope mientras dura el escudo (`ClassSkills.shield`) y lo quitan al gastarse.

## Puentes

Cada clase da 3 y recibe 3. Al otro lado hay un paquete fijo por clase de destino (puente, menor y notable «menor», 2
puntos cada uno), y **nada de lo que hay tras un puente toca los factores de daño**: el puente al Mago da maná y
defensa, nunca daño de hechizos.

| Clase | Senda 1 → | Senda 2 → | Senda 3 → |
|---|---|---|---|
| Guerrero | Tanque | Asesino | Arquero |
| Asesino | Guerrero | Arquero | Mago |
| Tanque | Guerrero | Curandero | Mago |
| Mago | Asesino | Curandero | Tanque |
| Curandero | Tanque | Mago | Arquero |
| Arquero | Asesino | Guerrero | Curandero |

## Equilibrio

<!-- CALCULOS:INICIO -->
Presupuesto en el tope: **103 puntos** (70 de nivel + 33 de hitos).

| Clase | Nodos | Coste de todos los nodos | Lo que se puede tener (una última) | % de eso con 103 puntos | Claves (puntos para llegar) |
|---|---|---|---|---|---|
| Guerrero | 101 | 127 | 117 | 88 % | Adrenalina 11, Duelista 11, Fortaleza 11, Martillo de guerra 11, Muro de carne 11, Sed de sangre 11 |
| Asesino | 101 | 127 | 117 | 88 % | Fantasma 11, Filo del viento 11, Frenesí 11, Funámbulo 11, Golpe de gracia 11, Sin sombra 11 |
| Tanque | 101 | 127 | 117 | 88 % | Espinas de acero 11, Gigante 11, Imán de golpes 11, Muralla viva 11, Yunque viviente 11, Último bastión 11 |
| Mago | 101 | 127 | 117 | 88 % | Escudo de maná 11, Hechizo encadenado 11, Parpadeo 11, Pozo sin fondo 11, Sangre por maná 11, Todo o nada 11 |
| Curandero | 101 | 127 | 117 | 88 % | Florecer 11, Lazo vital 11, Martillo de la fe 11, Mártir 11, Peregrino 11, Tierra sagrada 11 |
| Arquero | 101 | 127 | 117 | 88 % | Disparo en carrera 11, Flecha perforante 11, Francotirador 11, Halcón 11, Ojo de águila 11, Ráfaga 11 |

Puntos hasta cada habilidad (desde el origen, por el camino más barato):

- Guerrero: Grito de guerra II 5, Postura de hierro 5, Bramido 7, Hendedura 7, Postura de hierro II 7, Torbellino 7, Bramido II 9, Hendedura II 9, Torbellino II 9.
- Asesino: Marca de muerte 5, Paso sombrío II 5, Abanico de dagas 7, Danza de sombras 7, Ejecución 7, Marca de muerte II 7, Abanico de dagas II 9, Danza de sombras II 9, Ejecución II 9.
- Tanque: Baluarte 5, Provocar II 5, Baluarte II 7, Embestida de escudo 7, Golpe sísmico 7, Santuario de acero 7, Embestida de escudo II 9, Golpe sísmico II 9, Santuario de acero II 9.
- Mago: Concentración 5, Nova arcana II 5, Concentración II 7, Meteoro 7, Prisión de hielo 7, Relámpago en cadena 7, Meteoro II 9, Prisión de hielo II 9, Relámpago en cadena II 9.
- Curandero: Pulso sanador II 5, Resurgir 5, Escudo de luz 7, Oleada de vida 7, Resurgir II 7, Segunda vida 7, Escudo de luz II 9, Oleada de vida II 9, Segunda vida II 9.
- Arquero: Lluvia de flechas 5, Salto atrás II 5, Flecha de red 7, Flecha explosiva 7, Lluvia de flechas II 7, Saeta letal 7, Flecha de red II 9, Flecha explosiva II 9, Saeta letal II 9.

Lo que el árbol suma a una estadística (sin la base de la clase): con los 103 puntos puestos solo en ella, y el árbol entero (con la mejor clave de cada rama, porque las dos claves de una rama se excluyen):

| Clase | Estadística | Con 103 puntos | Puntos usados | Árbol entero |
|---|---|---|---|---|
| Guerrero | daño cuerpo a cuerpo | +15 % | 14 | +15 % |
| Guerrero | vida | +49 % | 41 | +49 % |
| Guerrero | daño de postura | +81 % | 23 | +81 % |
| Guerrero | daño recibido | −6 % | 13 | −6 % |
| Asesino | daño cuerpo a cuerpo | +12 % | 9 | +12 % |
| Asesino | daño por la espalda | +45 % | 12 | +45 % |
| Asesino | coste de esquiva | −15 % | 14 | −15 % |
| Asesino | daño a enemigos bajo el 35 % | +40 % | 15 | +40 % |
| Tanque | vida | +66 % | 41 | +66 % |
| Tanque | armadura | +9 | 26 | +9 |
| Tanque | daño recibido | −35 % | 29 | −35 % |
| Tanque | daño cuerpo a cuerpo | +3 % | 1 | +3 % |
| Mago | daño de hechizos | +20 % | 9 | +20 % |
| Mago | espera de hechizos | −6 % | 8 | −6 % |
| Mago | bono de la carga completa | +89 % | 11 | +89 % |
| Mago | maná máximo | +91 % | 13 | +91 % |
| Curandero | curación | +109 % | 33 | +109 % |
| Curandero | vida | +62 % | 46 | +62 % |
| Curandero | regeneración de maná | +270 % | 27 | +270 % |
| Curandero | daño recibido | −15 % | 19 | −15 % |
| Arquero | daño de proyectiles | +15 % | 5 | +15 % |
| Arquero | tiros a la cabeza | +81 % | 23 | +81 % |
| Arquero | tensado | +39 % | 26 | +39 % |
| Arquero | velocidad | +32 % | 35 | +32 % |

Puntos por nivel (de nivel en el tope: 70):

| Nivel | 1 | 2 | 3 | 5 | 10 | 15 | 20 | 25 | 30 | 35 | 40 | 45 | 50 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Puntos de nivel | 1 | 2 | 4 | 7 | 14 | 21 | 28 | 35 | 42 | 49 | 56 | 63 | 70 |
| Hitos que se pueden gastar como mucho | 1 | 2 | 3 | 5 | 10 | 15 | 20 | 25 | 30 | 33 | 33 | 33 | 33 |
| Experiencia total | 0 | 30 | 65 | 150 | 450 | 875 | 1425 | 2100 | 2900 | 3825 | 4875 | 6050 | 7350 |
<!-- CALCULOS:FIN -->

La columna «con 103 puntos» sale de un algoritmo voraz que solo busca esa estadística (y respeta la exclusión de
claves); «árbol entero» es todo lo que el árbol da a esa estadística con la mejor clave de cada rama. Las dos son cotas,
no partidas reales.

**Con el 88 % comprado, las claves de ramas distintas se juntan.** Lo que lo mantiene en su sitio:

- **Una clave por rama** (las dos de una rama se excluyen): como mucho 3 claves, una por rama.
- **Ningún nodo toca los factores de daño (decisión F)**: suman a los porcentajes, que se aplican antes del factor. Un
  Guerrero sigue lanzando a ×0,4 y un Tanque pegando a ×0,67.
- **El daño directo apenas sube**: Guerrero cuerpo a cuerpo +15 %, Asesino +12 %, Arquero proyectiles +15 %, y el Mago
  hechizos **+20 %**, lo mismo que con el árbol viejo, así que `BalanceGameTests.magiaEnSuSitio` sigue valiendo (el
  Mago del análisis suma ahora todos sus nodos menos las claves). Lo que crece es lo condicional (espalda, remates,
  cabeza) y la defensa.
- **El Tanque** era el que más se disparaba al juntar claves. Ajustes:
  - Yunque viviente da −15 % de daño recibido (no −20 %) e Imán de golpes −10 % rodeado (no −15 %);
  - y además hay un **suelo**: la clase nunca quita más del 60 % de un golpe (`ClassEffects.TAKEN_FLOOR` = 0,40),
    sumen lo que sumen los nodos.

  Su vida llega a +66 % con Gigante (126 % con la base, 45 de vida), pero Gigante y Yunque viviente se excluyen:
  vida o resistencia, no las dos.
- **El Curandero** cura hasta +109 % con Mártir, que le cuesta vida por cada cura.
- **Los monstruos lo notan**: `GearScore` cuenta los puntos gastados (`0,08 × gastados / 103`), no el nivel.

### Lo que hacen las finales que pegan (medido)

`UltimasGameTests` lanza cada final, la I y la II, contra un zombi quieto de 200 de vida (para que ninguna lo mate) y
mide lo que le quita por el mismo camino que cualquier golpe (`hurtServer`, armadura, los factores de la clase y sus
nodos), como las sondas de equilibrio miden las armas. Jugador de nivel 50 con solo la final aprendida y una espada de
hierro forjada en la mano. Es lo que recibe **un** enemigo: el Torbellino, el Golpe sísmico, la Hendedura, la Danza, el
Relámpago, la Prisión, el Meteoro, la Saeta y la Flecha explosiva llegan a varios.

| Clase | Final | Daño (I) | Daño (II) | Espera | Daño por segundo de espera (I / II) |
|---|---|---|---|---|---|
| Guerrero | Hendedura | 15,0 | 18,8 | 30 s | 0,50 / 0,63 |
| Guerrero | Torbellino | 6,2 | 6,2 + la segunda vuelta | 30 s | 0,21 / ~0,4 |
| Asesino | Danza de sombras | 8,9 | 9,4 | 40 s | 0,22 / 0,24 |
| Asesino | Ejecución | 12,2 | 16,2 | 30 s | 0,41 / 0,54 |
| Asesino | Abanico de dagas | 4,8 | 6,1 | 25 s | 0,19 / 0,24 |
| Tanque | Golpe sísmico | 6,4 | 8,0 | 35 s | 0,18 / 0,23 |
| Tanque | Embestida de escudo | 4,8 | 4,8 (y aturde) | 20 s | 0,24 / 0,24 |
| Mago | Relámpago en cadena | 11,0 | 13,2 | 25 s | 0,44 / 0,53 |
| Mago | Prisión de hielo | 6,6 | 8,8 | 45 s | 0,15 / 0,20 |
| Mago | Meteoro | 13,2 | 19,6 | 40 s | 0,33 / 0,49 |
| Arquero | Saeta letal | 16,8 | 21,0 | 30 s | 0,56 / 0,70 |
| Arquero | Flecha explosiva | 10,9 | 13,7 | 25 s | 0,44 / 0,55 |

La prueba exige a cada una que haga daño, que no pase de **30 en un golpe** (no matan de una a nada que aguante un poco)
y que no pase de **1 de daño por segundo de espera**: una espada forjada hace unos 5 por segundo, así que una final
suma como mucho un 20 % sobre lo que ya hace el arma en ese tiempo. Su fuerza está en el área, la postura y el
control, no en el daño a un solo enemigo. La Saeta letal salía a 25 con la II (19,6 con la I) y se bajó de 14/18 a
12/15 de daño. Ninguna toca `BalanceGameTests.magiaEnSuSitio` ni las pruebas de jefes (que miden armas, no
habilidades), y las 489 pruebas de servidor siguen en verde. (En la prueba, la segunda vuelta del Torbellino II no
llega a medirse: el jugador de prueba no está en la lista de jugadores del mundo, que es donde la busca.)

## La pantalla del árbol (K)

- **Lienzo**: arrastrar con el botón izquierdo en vacío o con el derecho en cualquier sitio; la rueda acerca y aleja
  hacia el cursor (0,4× a 2×); Inicio o doble clic en vacío vuelve al centro; flechas y +/− también.
- **Dorado**: lo aprendido y sus líneas. Lo que se puede coger ya late en blanco. Lo de otra clase, del color de esa
  clase; la forja, naranja; las claves, con el centro rojo.
- **Tooltips con números**: nombre, tipo y coste, los números y el efecto, el total de antes y después en cada
  estadística («daño de postura +15 % → +18 %»), la clave que excluye y por qué no se puede coger ahora.
- **Probar**: el botón (o Mayús + clic) marca nodos en azul sin gastar nada; arriba sale «Probando: N puntos (te
  quedarían M)», y la columna derecha enseña los totales con el plan. **Aplicar** los aprende en orden (el servidor
  comprueba cada uno) y **Descartar** los borra. El plan se recuerda mientras el juego esté abierto.
- **Buscar**: un campo arriba; apaga lo que no coincide e Intro centra la siguiente coincidencia.
- **Hitos**: el botón abre la lista, con los conseguidos en verde y sus puntos.
- **A la derecha**: las tres habilidades con su tecla (con la II en el tooltip) y lo que suma la clase. En la N, la final
  elegida (en azul si solo está en «Probar»), o «Final: elige una» con los tres iconos pequeños.
- **Las tres finales** se ven como un grupo «elige una»: un rombo con borde dorado (el marco del nodo girado 45°),
  algo más grande que una habilidad, y debajo «Final · elige una» mientras no hay ninguna, o «Elegida» en la tuya. Al
  aprender una (o ponerla en «Probar»), las otras dos y sus II se ponen grises con un **candado** y su tooltip dice
  «Ya elegiste X» y cómo cambiarla. «Probar» no deja meter una segunda final en el plan.
- **Vela del olvido**: abierta desde la vela, la pantalla marca en rojo los nodos que se quitarán y «Quitar» gasta la
  vela.

## Iconos

Cada nodo lleva un icono (`icono` en los datos; `clase/TreeIcon` lo resuelve): `minecraft:x` o `forja:x` es un objeto,
`sprite:x` es `textures/gui/arbol/x.png` (los medallones de las claves y el corazón, de `tools/arbol_iconos.py`) y
`clase:x` es el emblema de la clase. Las tablas están en `tools/arboles_datos.py`: `STAT_ICONS` (menores),
`NOTABLE_ICONS`, `KEYSTONE_GLYPHS` (el dibujo de cada clave), `FORGE_ICONS` y, en cada habilidad, `icono`.
Capturas: `E:\IA\Claude\Forja_capturas_mejoras\arbol\iconos\`.

Las doce finales nuevas llevan icono propio: Bramido, chillador de sculk; Hendedura, hacha de diamante; Danza de
sombras, fragmento de eco; Ejecución, espada de diamante; Golpe sísmico, núcleo pesado; Santuario de acero, magnetita;
Relámpago en cadena, pararrayos; Prisión de hielo, hielo compacto; Oleada de vida, corazón del mar; Segunda vida, nexo
de reaparición; Saeta letal, tridente; Flecha explosiva, dinamita.

**Iconos a cualquier zoom.** Andy (2026-09-30) veía casi todos los nodos como cuadrados de color a un zoom normal: el
icono solo se dibujaba si medía 6 píxeles o más, y un menor (6 de radio × el zoom) se quedaba por debajo en cuanto la
ventana o el zoom eran algo pequeños. Ahora el icono sale **siempre que el nodo mida 5 píxeles o más**
(`TalentTreeScreen.ICON_MIN_NODE`), con un mínimo de 6 píxeles de icono; el cuadrado de color solo queda para el zoom
más lejano. El velo de lo bloqueado es más suave (se ve el icono debajo).

## Reiniciar

- **Medallón del olvido** (sin cambios): cambia de clase (nivel 1, los hitos se quedan) o, en tu misma clase, vacía el
  árbol y conserva el nivel.
- **Vela del olvido** (`forja:vela_del_olvido`, se apila hasta 16): quita hasta **4 puntos** de nodos del borde de lo
  aprendido, los que no dejan a otro suelto. Mesa de trabajo: **vela + 2 fragmentos de amatista + lágrima de ghast**.
  Clic derecho abre el árbol en modo «olvidar»; la vela solo se gasta al quitar.
- **Cambiar de final**: la final es una hoja. La vela la quita junto con su II como si fuera un solo nodo (la II no
  cuenta si su final se va con ella: 3 puntos), y entonces se puede elegir otra (`ClassProgress.forgetCost`).

## Guardados

- `ClassData` guarda los nodos por **id de texto** (`nodos`), los hitos (`hitos`), las tres esperas
  (`habilidad_1..3`; la 3 es la de la final) y la versión del árbol (`arbol` = 3). Todo con `optionalFieldOf`: un
  guardado viejo carga. La final elegida no se guarda aparte: es la que esté aprendida.
- Un guardado sin `arbol` es del árbol pequeño (versión 1). Al entrar, `ClassProgress.migrate`:
  - borra los talentos viejos (su `talentos` se lee y se olvida) y avisa de cuántos puntos hay para repartir;
  - si la clase era «herrero», deja al jugador sin clase y le avisa.
- Un guardado con `arbol` = 2 es del árbol de una sola N. `ClassProgress.toV3` renombra sus nodos
  (`ClassProgress.V3_NAMES`): `s3.4` → `s3.ultima` y `s3.lado` → `s3.ultima_ii` (**la N que tenía sigue siendo su
  final elegida, con su II**), `s1.2` → `a.habilidad_v2`, `s2.2` → `b.habilidad_b`, `s2.lado` → `b.habilidad_b2`, y los
  menores de las sendas 1 y 2 suben un puesto (`s1.3` → `s1.2`...). Lo que queda suelto (V II y B, si no tenía el
  tronco de su rama) se suelta y sus puntos vuelven; un mensaje dice cuántos.
- Comandos: `/forja clase aprender <nodo>`, `puntos`, `hito <id>`, `hitos`, `habilidad 1|2|3`.

## Pruebas

- **Servidor** (`ArbolGameTests`, `UltimasGameTests`, y las de clases y maná adaptadas):
  - los datos (seis árboles, 101 nodos, 127 puntos y 117 que se pueden tener, conexos, enlaces de ida y vuelta, claves a
    11 y emparejadas, finales a 7, todos los ganchos con código);
  - las finales: tres por clase, cada una al final de su senda con su II, el puente sin pasar por ella, V II y B en las
    ramas (`threeUltimatesPerClass`); elegir una cierra las otras y sus II, el resto de sus sendas se sigue
    aprendiendo, la vela la quita con su II y el medallón lo vacía todo (`onlyOneUltimate`); un guardado de la versión 2
    conserva su N (`oldSaveKeepsItsUltimate`); y las 18, I y II, haciendo lo que dicen, con su daño medido
    (`ultimasDel<Clase>`);
  - las reglas (vecindad, puntos, otra clase, exclusión, plan);
  - puntos, hitos y su tope;
  - hitos por estadística y élite;
  - efectos (vida, Inquebrantable, potencial, carga, Temple);
  - Medallón y Vela;
  - migración;
  - las tres habilidades, la II y el Meteoro;
  - GearScore.
- **Cliente** (`FORJA_SOLO=arbol`, `ArbolFootage`): aprender con clics, rueda, arrastre, Probar y Aplicar, tooltip de
  una clave, búsqueda, hitos, el árbol del Mago, la Vela y la tecla N. Hoja de contactos en
  `Forja_capturas_mejoras\arbol\hoja_arbol.png`. Las finales (capturas `ultimas_*`): los iconos al zoom con que se
  abre, las tres por elegir, una en «Probar» con las otras cerradas, una aprendida con un clic y su tooltip, la cerrada
  con «Ya elegiste», la vela quitándola y las seis clases. Hoja en `Forja_capturas_mejoras\arbol\ultimas\`.

## Todos los nodos

Ids sin el prefijo de la clase (`b2.3` es `guerrero.b2.3`):

- `a`, `b` y `c` son las ramas: `tronco_1` a `tronco_4`, y los caminos `a1`/`a2` con los nodos 1 a 5 y
  `lado_1`/`lado_2`;
- `a.habilidad_v2` es V II; `b.habilidad_b` y `b.habilidad_b2`, la habilidad B y su II;
- `s1`, `s2` y `s3` son las sendas: nodos 1 a 3, `ultima` (su final) y `ultima_ii`, y `puente_1` a `puente_3`;
- `nucleo_1` a `nucleo_6` son las puertas: 1 → rama A, 2 → senda 1, 3 → rama B, 4 → senda 2, 5 → rama C, 6 → senda 3.
  Además se conectan con los dos nodos de forja de sus huecos.

<!-- NODOS:INICIO -->
### Región de la forja (igual en los seis árboles)

| Id | Nombre | Tipo | Coste | Efecto | Conexiones |
|---|---|---|---|---|---|
| `ojo_del_martillo` | Ojo del martillo | forja | 1 | ventana del golpe perfecto +0,01 | `golpe_de_maestro`, las dos puertas del núcleo que tiene al lado |
| `metal_docil` | Metal dócil | forja | 1 | potencial al forjar +5 | `alma_del_metal`, las dos puertas del núcleo que tiene al lado |
| `remiendo` | Remiendo | forja | 1 | reparación por lingote +25 % | `temple_de_campana`, las dos puertas del núcleo que tiene al lado |
| `fuelle` | Fuelle | forja | 1 | velocidad de las máquinas cercanas +20 %; las montadoras a 8 bloques de ti trabajan más rápido | `ajuste_fino`, las dos puertas del núcleo que tiene al lado |
| `mano_firme` | Mano firme | forja | 1 | % por ingrediente de mejora +1 | `carga_honda`, las dos puertas del núcleo que tiene al lado |
| `brazo_de_herrero` | Brazo de herrero | forja | 1 | daño con martillo, mazo, pico y hacha +8 % | `forja_al_rojo`, las dos puertas del núcleo que tiene al lado |
| `golpe_de_maestro` | Golpe de maestro | forja | 1 | ventana del golpe perfecto +0,01 | `ojo_del_martillo` |
| `alma_del_metal` | Alma del metal | forja | 1 | potencial al forjar +5 | `metal_docil` |
| `temple_de_campana` | Temple de campaña | forja | 1 | la pieza forjada de tu mano recupera el 1 % de su durabilidad cada minuto | `remiendo`, `temple_de_campana_ii` |
| `ajuste_fino` | Ajuste fino | forja | 1 | potencial de lo que montan las montadoras cercanas +5; lo que montan las montadoras a 8 bloques de ti nace con más potencial | `fuelle` |
| `carga_honda` | Carga honda | forja | 1 | carga de lo que forjas +2 | `mano_firme` |
| `forja_al_rojo` | Forja al rojo | forja | 1 | tras una forja perfecta, 60 s en que tus golpes c/c prenden fuego 3 s y hacen +10 % | `brazo_de_herrero` |
| `temple_de_campana_ii` | Temple de campaña II | forja | 1 | la pieza forjada de tu mano y la armadura forjada que llevas puesta recuperan el 2 % de su durabilidad cada minuto | `temple_de_campana` |

### Guerrero

Base: vida +10 %, daño c/c +5 %, estamina máx. +20 %, regeneración +10 %, postura +15 %, parada +1 tick. Ramas: A Aguante, B Guardia, C Quebranto. Sendas: 1 Senda del grito (puente al Tanque), 2 Senda del hierro (puente al Asesino), 3 Senda del torbellino (puente al Arquero).

- **Grito de guerra** (V): recuperas 40 de estamina; tú y los jugadores a 8 bloques ganáis Fuerza I 8 s; espera 45 s. **II**: recuperas 60 de estamina; tú y los jugadores a 12 bloques ganáis Fuerza I 10 s; espera 45 s.
- **Postura de hierro** (B): 6 s de Resistencia II y Lentitud I; espera 50 s. **II**: 8 s de Resistencia II, sin Lentitud; al acabar recuperas 20 de estamina; espera 50 s.
- **Bramido** (N, final de la senda del grito; una de tres): bramas: los hostiles a 6 bloques reciben 40 de postura y Debilidad I 5 s, y ganas 2 de absorción por cada uno (hasta 10); cuesta 30 de estamina; espera 45 s. **II**: bramas: los hostiles a 8 bloques reciben 60 de postura y Debilidad I 5 s, y ganas 3 de absorción por cada uno (hasta 12); cuesta 30 de estamina; espera 45 s.
- **Hendedura** (N, final de la senda del hierro; una de tres): un tajo de arriba abajo a lo que tienes delante (hasta 3,5 bloques, 100°): el 200 % del daño de tu arma y 50 de postura; cuesta 35 de estamina; espera 30 s. **II**: un tajo de arriba abajo a lo que tienes delante (hasta 3,5 bloques, 100°): el 250 % del daño de tu arma y 80 de postura; cuesta 35 de estamina; espera 30 s.
- **Torbellino** (N, final de la senda del torbellino; una de tres): giras y golpeas todo lo que hay a 3 bloques con el 80 % del daño de tu arma y 30 de postura; cuesta 30 de estamina; espera 30 s. **II**: dos vueltas: golpeas dos veces todo lo que hay a 3 bloques con el 80 % del daño de tu arma y 45 de postura; cuesta 30 de estamina; espera 30 s.

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
| `a.tronco_2` | Vida | menor | 1 | vida +3 % | `a.tronco_1`, `a.tronco_3`, `a.habilidad_v2` |
| `a.tronco_3` | Estamina máxima | menor | 1 | estamina máxima +3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Segundo aliento | notable | 1 | regeneración de estamina +15 %; por debajo del 25 % de estamina, la estamina se regenera el doble | `a.tronco_3`, `a1.1`, `a2.1` |
| `a.habilidad_v2` | Grito de guerra II | habilidad | 2 | recuperas 60 de estamina; tú y los jugadores a 12 bloques ganáis Fuerza I 10 s; espera 45 s | `a.tronco_2` |
| `a1.1` | Vida | menor | 1 | vida +3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Inquebrantable | notable | 1 | por debajo del 30 % de vida, daño recibido −20 % | `a1.2`, `a1.4` |
| `a1.4` | Vida | menor | 1 | vida +3 % | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Muro de carne | **clave** | 2 | vida +20 %; velocidad −8 %; coste de esquiva +25 % *(gana: vida +20 %; precio: velocidad −8 %, coste de esquiva +25 %; excluye a `a2.5`)* | `a1.4` |
| `a1.lado_1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `a1.2` |
| `a1.lado_2` | Daño recibido | menor | 1 | daño recibido −3 % | `a1.4` |
| `a2.1` | Coste de estamina | menor | 1 | coste de estamina −3 % | `a.tronco_4`, `a2.2` |
| `a2.2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Sin resuello | notable | 1 | coste de estamina −15 % | `a2.2`, `a2.4` |
| `a2.4` | Estamina máxima | menor | 1 | estamina máxima +3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Adrenalina | **clave** | 2 | regeneración de estamina +25 %; estamina máxima −25 %; cada golpe que recibes te devuelve 6 de estamina *(gana: 6 de estamina por golpe recibido, regeneración +25 %; precio: estamina máxima −25 %; excluye a `a1.5`)* | `a2.4` |
| `a2.lado_1` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `a2.2` |
| `a2.lado_2` | Velocidad | menor | 1 | velocidad +3 % | `a2.4` |
| `s1.1` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `nucleo_2`, `s1.2` |
| `s1.2` | Vida | menor | 1 | vida +3 % | `s1.1`, `s1.3` |
| `s1.3` | Estamina máxima | menor | 1 | estamina máxima +3 % | `s1.2`, `s1.ultima`, `s1.puente_1` |
| `s1.ultima` | Bramido | habilidad | 3 | bramas: los hostiles a 6 bloques reciben 40 de postura y Debilidad I 5 s, y ganas 2 de absorción por cada uno (hasta 10); cuesta 30 de estamina; espera 45 s | `s1.3`, `s1.ultima_ii` |
| `s1.ultima_ii` | Bramido II | habilidad | 2 | bramas: los hostiles a 8 bloques reciben 60 de postura y Debilidad I 5 s, y ganas 3 de absorción por cada uno (hasta 12); cuesta 30 de estamina; espera 45 s | `s1.ultima` |
| `s1.puente_1` | Puente al Tanque | puente | 2 | vida +5 % *(del Tanque)* | `s1.3`, `s1.puente_2` |
| `s1.puente_2` | Armadura | cruzado | 2 | armadura +1 *(del Tanque)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Represalia menor | cruzado | 2 | quien golpea tu escudo levantado recibe 2 de daño *(del Tanque)* | `s1.puente_2` |
| `b.tronco_1` | Ventana de parada | menor | 1 | ventana de parada +1 tick | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Estamina al parar | menor | 1 | estamina al parar −3 % | `b.tronco_1`, `b.tronco_3`, `b.habilidad_b` |
| `b.tronco_3` | Armadura | menor | 1 | armadura +1 | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Réplica | notable | 1 | una parada devuelve 15 de estamina y tu siguiente golpe en 3 s hace +30 % | `b.tronco_3`, `b1.1`, `b2.1` |
| `b.habilidad_b` | Postura de hierro | habilidad | 2 | 6 s de Resistencia II y Lentitud I; espera 50 s | `b.tronco_2`, `b.habilidad_b2` |
| `b.habilidad_b2` | Postura de hierro II | habilidad | 2 | 8 s de Resistencia II, sin Lentitud; al acabar recuperas 20 de estamina; espera 50 s | `b.habilidad_b` |
| `b1.1` | Armadura | menor | 1 | armadura +1 | `b.tronco_4`, `b1.2` |
| `b1.2` | Estamina al parar | menor | 1 | estamina al parar −3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Parada firme | notable | 1 | estamina al parar −25 % | `b1.2`, `b1.4` |
| `b1.4` | Dureza de armadura | menor | 1 | dureza de armadura +0,5 | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Fortaleza | **clave** | 2 | resistencia al empuje +50 %; estamina al parar −40 %; con un escudo en la otra mano no puedes esquivar *(gana: empuje +50 %, parar −40 %; precio: sin esquiva con escudo; excluye a `b2.5`)* | `b1.4` |
| `b1.lado_1` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `b1.2` |
| `b1.lado_2` | Vida | menor | 1 | vida +3 % | `b1.4` |
| `b2.1` | Ventana de parada | menor | 1 | ventana de parada +1 tick | `b.tronco_4`, `b2.2` |
| `b2.2` | Contraataques | menor | 1 | contraataques +3 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Filo de vuelta | notable | 1 | una parada hace 20 de daño de postura a quien te golpeó | `b2.2`, `b2.4` |
| `b2.4` | Contraataques | menor | 1 | contraataques +3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Duelista | **clave** | 2 | ventana de parada +3 ticks; bloquear sin parar cuesta el doble de estamina *(gana: ventana de parada +3 ticks; precio: bloqueos sin parada ×2 de estamina; excluye a `b1.5`)* | `b2.4` |
| `b2.lado_1` | Daño de postura | menor | 1 | daño de postura +3 % | `b2.2` |
| `b2.lado_2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `b2.4` |
| `s2.1` | Daño de postura | menor | 1 | daño de postura +3 % | `nucleo_4`, `s2.2` |
| `s2.2` | Daño recibido | menor | 1 | daño recibido −3 % | `s2.1`, `s2.3` |
| `s2.3` | Vida | menor | 1 | vida +3 % | `s2.2`, `s2.ultima`, `s2.puente_1` |
| `s2.ultima` | Hendedura | habilidad | 3 | un tajo de arriba abajo a lo que tienes delante (hasta 3,5 bloques, 100°): el 200 % del daño de tu arma y 50 de postura; cuesta 35 de estamina; espera 30 s | `s2.3`, `s2.ultima_ii` |
| `s2.ultima_ii` | Hendedura II | habilidad | 2 | un tajo de arriba abajo a lo que tienes delante (hasta 3,5 bloques, 100°): el 250 % del daño de tu arma y 80 de postura; cuesta 35 de estamina; espera 30 s | `s2.ultima` |
| `s2.puente_1` | Puente al Asesino | puente | 2 | velocidad +3 % *(del Asesino)* | `s2.3`, `s2.puente_2` |
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
| `c1.5` | Martillo de guerra | **clave** | 2 | daño de postura +40 %; daño a aturdidos +15 %; contra lo que no está aturdido haces −10 % *(gana: postura +40 %, aturdidos +15 %; precio: −10 % a no aturdidos; excluye a `c2.5`)* | `c1.4` |
| `c1.lado_1` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `c1.2` |
| `c1.lado_2` | Vida | menor | 1 | vida +3 % | `c1.4` |
| `c2.1` | Remates | menor | 1 | remates +3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Verdugo | notable | 1 | remates +30 % | `c2.2`, `c2.4` |
| `c2.4` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Sed de sangre | **clave** | 2 | daño recibido +10 %; matar cuerpo a cuerpo te cura el 10 % de tu vida máxima *(gana: 10 % de vida por muerte; precio: daño recibido +10 %; excluye a `c1.5`)* | `c2.4` |
| `c2.lado_1` | Remates | menor | 1 | remates +3 % | `c2.2` |
| `c2.lado_2` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `c2.4` |
| `s3.1` | Daño de postura | menor | 1 | daño de postura +3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Carga brutal | notable | 1 | un golpe cargado rompe la postura de un monstruo normal | `s3.1`, `s3.3` |
| `s3.3` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `s3.2`, `s3.ultima`, `s3.puente_1` |
| `s3.ultima` | Torbellino | habilidad | 3 | giras y golpeas todo lo que hay a 3 bloques con el 80 % del daño de tu arma y 30 de postura; cuesta 30 de estamina; espera 30 s | `s3.3`, `s3.ultima_ii` |
| `s3.ultima_ii` | Torbellino II | habilidad | 2 | dos vueltas: golpeas dos veces todo lo que hay a 3 bloques con el 80 % del daño de tu arma y 45 de postura; cuesta 30 de estamina; espera 30 s | `s3.ultima` |
| `s3.puente_1` | Puente al Arquero | puente | 2 | tensado +5 % *(del Arquero)* | `s3.3`, `s3.puente_2` |
| `s3.puente_2` | Daño de proyectiles | cruzado | 2 | daño de proyectiles +4 % *(del Arquero)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Ojo de halcón menor | cruzado | 2 | tiros a la cabeza +15 % *(del Arquero)* | `s3.puente_2` |

### Asesino

Base: vida −30 %, daño c/c +10 %, velocidad +5 %, estamina +30 %, esquiva: distancia +35 %, espera −30 %, coste −20 %, +2 ticks. Ramas: A Sombra, B Filo, C Sigilo. Sendas: 1 Senda de la sombra (puente al Guerrero), 2 Senda de la marca (puente al Arquero), 3 Senda del veneno (puente al Mago).

- **Paso sombrío** (V): 4 s de invisibilidad y Velocidad II; tu siguiente golpe c/c en ese tiempo hace +60 %; espera 30 s. **II**: 6 s de invisibilidad y Velocidad II; tu siguiente golpe c/c en ese tiempo hace +80 %; espera 30 s.
- **Marca de muerte** (B): el monstruo que miras (hasta 16 bloques) brilla 10 s y recibe +30 % de tus golpes; espera 40 s. **II**: el monstruo que miras (hasta 16 bloques) brilla 10 s y recibe +40 % de tus golpes; si muere marcado, la espera baja un 50 %; espera 40 s.
- **Danza de sombras** (N, final de la senda de la sombra; una de tres): saltas de enemigo en enemigo, hasta 4 a 8 bloques, y golpeas a cada uno por la espalda con el 100 % del daño de tu arma; mientras bailas nada te hace daño; cuesta 30 de estamina; espera 40 s. **II**: saltas de enemigo en enemigo, hasta 6 a 8 bloques, y golpeas a cada uno por la espalda con el 120 % del daño de tu arma; mientras bailas nada te hace daño; cuesta 30 de estamina; espera 40 s.
- **Ejecución** (N, final de la senda de la marca; una de tres): apareces detrás del enemigo que miras (hasta 16 bloques) y le golpeas con el 150 % del daño de tu arma, el doble si le queda menos del 35 % de vida; cuesta 25 de estamina; espera 30 s. **II**: apareces detrás del enemigo que miras (hasta 16 bloques) y le golpeas con el 200 % del daño de tu arma, el doble si le queda menos del 35 % de vida; si muere, la espera baja un 50 %; cuesta 25 de estamina; espera 30 s.
- **Abanico de dagas** (N, final de la senda del veneno; una de tres): lanzas 5 dagas en abanico de 60°: 4 de daño cada una y Veneno I 3 s; espera 25 s. **II**: lanzas 7 dagas en abanico de 60°: 4 de daño cada una y Veneno II 3 s; espera 25 s.

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
| `a.tronco_2` | Espera de esquiva | menor | 1 | espera de esquiva −3 % | `a.tronco_1`, `a.tronco_3`, `a.habilidad_v2` |
| `a.tronco_3` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Danza | notable | 1 | invulnerabilidad de esquiva +1 tick; espera de esquiva −10 % | `a.tronco_3`, `a1.1`, `a2.1` |
| `a.habilidad_v2` | Paso sombrío II | habilidad | 2 | 6 s de invisibilidad y Velocidad II; tu siguiente golpe c/c en ese tiempo hace +80 %; espera 30 s | `a.tronco_2` |
| `a1.1` | Contraataques | menor | 1 | contraataques +3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Coste de esquiva | menor | 1 | coste de esquiva −3 % | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Contraataque | notable | 1 | contraataques +30 % | `a1.2`, `a1.4` |
| `a1.4` | Contraataques | menor | 1 | contraataques +3 % | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Filo del viento | **clave** | 2 | armadura −4; tras una esquiva perfecta, tu siguiente golpe en 2 s hace +50 % *(gana: +50 % tras esquiva perfecta; precio: armadura −4; excluye a `a2.5`)* | `a1.4` |
| `a1.lado_1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `a1.2` |
| `a1.lado_2` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `a1.4` |
| `a2.1` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `a.tronco_4`, `a2.2` |
| `a2.2` | Espera de esquiva | menor | 1 | espera de esquiva −3 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Espejismo | notable | 1 | al esquivar, los monstruos a 6 bloques te pierden de vista (cada 8 s) | `a2.2`, `a2.4` |
| `a2.4` | Coste de esquiva | menor | 1 | coste de esquiva −3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Sin sombra | **clave** | 2 | esquivar no cuesta estamina, pero cada esquiva te quita 1 de vida (nunca por debajo de 1) *(gana: esquivas gratis; precio: 1 de vida por esquiva; excluye a `a1.5`)* | `a2.4` |
| `a2.lado_1` | Velocidad | menor | 1 | velocidad +3 % | `a2.2` |
| `a2.lado_2` | Daño de caída | menor | 1 | daño de caída −3 % | `a2.4` |
| `s1.1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `nucleo_2`, `s1.2` |
| `s1.2` | Coste de esquiva | menor | 1 | coste de esquiva −3 % | `s1.1`, `s1.3` |
| `s1.3` | Velocidad | menor | 1 | velocidad +3 % | `s1.2`, `s1.ultima`, `s1.puente_1` |
| `s1.ultima` | Danza de sombras | habilidad | 3 | saltas de enemigo en enemigo, hasta 4 a 8 bloques, y golpeas a cada uno por la espalda con el 100 % del daño de tu arma; mientras bailas nada te hace daño; cuesta 30 de estamina; espera 40 s | `s1.3`, `s1.ultima_ii` |
| `s1.ultima_ii` | Danza de sombras II | habilidad | 2 | saltas de enemigo en enemigo, hasta 6 a 8 bloques, y golpeas a cada uno por la espalda con el 120 % del daño de tu arma; mientras bailas nada te hace daño; cuesta 30 de estamina; espera 40 s | `s1.ultima` |
| `s1.puente_1` | Puente al Guerrero | puente | 2 | regeneración de estamina +5 % *(del Guerrero)* | `s1.3`, `s1.puente_2` |
| `s1.puente_2` | Daño de postura | cruzado | 2 | daño de postura +5 % *(del Guerrero)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Réplica menor | cruzado | 2 | una parada perfecta te devuelve 10 de estamina *(del Guerrero)* | `s1.puente_2` |
| `b.tronco_1` | Daño por la espalda | menor | 1 | daño por la espalda +3 % | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `b.tronco_1`, `b.tronco_3`, `b.habilidad_b` |
| `b.tronco_3` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Puñalada | notable | 1 | daño por la espalda +30 % | `b.tronco_3`, `b1.1`, `b2.1` |
| `b.habilidad_b` | Marca de muerte | habilidad | 2 | el monstruo que miras (hasta 16 bloques) brilla 10 s y recibe +30 % de tus golpes; espera 40 s | `b.tronco_2`, `b.habilidad_b2` |
| `b.habilidad_b2` | Marca de muerte II | habilidad | 2 | el monstruo que miras (hasta 16 bloques) brilla 10 s y recibe +40 % de tus golpes; si muere marcado, la espera baja un 50 %; espera 40 s | `b.habilidad_b` |
| `b1.1` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `b.tronco_4`, `b1.2` |
| `b1.2` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Ejecutor | notable | 1 | daño a enemigos bajo el 35 % +25 % | `b1.2`, `b1.4` |
| `b1.4` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Golpe de gracia | **clave** | 2 | un golpe c/c mata a un monstruo normal por debajo del 15 % de vida; a los que están por encima del 50 % les haces −15 % *(gana: remate instantáneo bajo el 15 %; precio: −15 % a enemigos sanos; excluye a `b2.5`)* | `b1.4` |
| `b1.lado_1` | Daño por la espalda | menor | 1 | daño por la espalda +3 % | `b1.2` |
| `b1.lado_2` | Velocidad | menor | 1 | velocidad +3 % | `b1.4` |
| `b2.1` | Daño cuerpo a cuerpo | menor | 1 | daño cuerpo a cuerpo +3 % | `b.tronco_4`, `b2.2` |
| `b2.2` | Daño por la espalda | menor | 1 | daño por la espalda +3 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Golpe letal | notable | 1 | matar c/c devuelve 20 de estamina y da Velocidad II 3 s | `b2.2`, `b2.4` |
| `b2.4` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Frenesí | **clave** | 2 | vida −10 %; cada muerte c/c da +8 % de daño c/c 5 s (hasta 4 veces; se pierde al recibir un golpe) *(gana: hasta +32 % encadenando muertes; precio: vida −10 %, se pierde al recibir un golpe; excluye a `b1.5`)* | `b2.4` |
| `b2.lado_1` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `b2.2` |
| `b2.lado_2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `b2.4` |
| `s2.1` | Daño por la espalda | menor | 1 | daño por la espalda +3 % | `nucleo_4`, `s2.2` |
| `s2.2` | Daño a enemigos bajo el 35 % | menor | 1 | daño a enemigos bajo el 35 % +3 % | `s2.1`, `s2.3` |
| `s2.3` | Velocidad | menor | 1 | velocidad +3 % | `s2.2`, `s2.ultima`, `s2.puente_1` |
| `s2.ultima` | Ejecución | habilidad | 3 | apareces detrás del enemigo que miras (hasta 16 bloques) y le golpeas con el 150 % del daño de tu arma, el doble si le queda menos del 35 % de vida; cuesta 25 de estamina; espera 30 s | `s2.3`, `s2.ultima_ii` |
| `s2.ultima_ii` | Ejecución II | habilidad | 2 | apareces detrás del enemigo que miras (hasta 16 bloques) y le golpeas con el 200 % del daño de tu arma, el doble si le queda menos del 35 % de vida; si muere, la espera baja un 50 %; cuesta 25 de estamina; espera 30 s | `s2.ultima` |
| `s2.puente_1` | Puente al Arquero | puente | 2 | tensado +5 % *(del Arquero)* | `s2.3`, `s2.puente_2` |
| `s2.puente_2` | Daño de proyectiles | cruzado | 2 | daño de proyectiles +4 % *(del Arquero)* | `s2.puente_1`, `s2.puente_3` |
| `s2.puente_3` | Ojo de halcón menor | cruzado | 2 | tiros a la cabeza +15 % *(del Arquero)* | `s2.puente_2` |
| `c.tronco_1` | Velocidad agachado | menor | 1 | velocidad agachado +3 % | `nucleo_5`, `c.tronco_2` |
| `c.tronco_2` | Daño de caída | menor | 1 | daño de caída −3 % | `c.tronco_1`, `c.tronco_3` |
| `c.tronco_3` | Velocidad | menor | 1 | velocidad +3 % | `c.tronco_2`, `c.tronco_4` |
| `c.tronco_4` | Paso quedo | notable | 1 | velocidad agachado +20 %; agachado, los monstruos te ven a la mitad de distancia | `c.tronco_3`, `c1.1`, `c2.1` |
| `c1.1` | Salto | menor | 1 | salto +3 % | `c.tronco_4`, `c1.2` |
| `c1.2` | Daño de caída | menor | 1 | daño de caída −3 % | `c1.1`, `c1.3`, `c1.lado_1` |
| `c1.3` | Acróbata | notable | 1 | daño de caída −30 %; salto +10 % | `c1.2`, `c1.4` |
| `c1.4` | Velocidad | menor | 1 | velocidad +3 % | `c1.3`, `c1.5`, `c1.lado_2` |
| `c1.5` | Funámbulo | **clave** | 2 | resistencia al empuje −30 %; tras caer más de 3 bloques, tu siguiente golpe en 2 s hace +40 % *(gana: +40 % al caer sobre el enemigo; precio: resistencia al empuje −30 %; excluye a `c2.5`)* | `c1.4` |
| `c1.lado_1` | Salto | menor | 1 | salto +3 % | `c1.2` |
| `c1.lado_2` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c1.4` |
| `c2.1` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Velocidad | menor | 1 | velocidad +3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Evasión | notable | 1 | 12 % de probabilidad de que un proyectil no te haga nada | `c2.2`, `c2.4` |
| `c2.4` | Velocidad | menor | 1 | velocidad +3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Fantasma | **clave** | 2 | Paso sombrío dura el doble y el primer golpe no te hace visible; pero su espera +50 % *(gana: Paso sombrío ×2; precio: espera de Paso sombrío +50 %; excluye a `c1.5`)* | `c2.4` |
| `c2.lado_1` | Daño de caída | menor | 1 | daño de caída −3 % | `c2.2` |
| `c2.lado_2` | Velocidad agachado | menor | 1 | velocidad agachado +3 % | `c2.4` |
| `s3.1` | Velocidad agachado | menor | 1 | velocidad agachado +3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Veneno en la hoja | notable | 1 | tus golpes por la espalda envenenan (Veneno I 3 s) | `s3.1`, `s3.3` |
| `s3.3` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `s3.2`, `s3.ultima`, `s3.puente_1` |
| `s3.ultima` | Abanico de dagas | habilidad | 3 | lanzas 5 dagas en abanico de 60°: 4 de daño cada una y Veneno I 3 s; espera 25 s | `s3.3`, `s3.ultima_ii` |
| `s3.ultima_ii` | Abanico de dagas II | habilidad | 2 | lanzas 7 dagas en abanico de 60°: 4 de daño cada una y Veneno II 3 s; espera 25 s | `s3.ultima` |
| `s3.puente_1` | Puente al Mago | puente | 2 | maná máximo +10 % *(del Mago)* | `s3.3`, `s3.puente_2` |
| `s3.puente_2` | Regeneración de maná | cruzado | 2 | regeneración de maná +50 % *(del Mago)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Barrera menor | cruzado | 2 | daño mágico recibido −15 % *(del Mago)* | `s3.puente_2` |

### Tanque

Base: vida +60 %, armadura +2, empuje +30 %, velocidad −12 %, esquiva −35 %, coste de esquiva +20 %, estamina al parar −25 %. Ramas: A Muralla, B Coraza, C Firmeza. Sendas: 1 Senda del desafío (puente al Guerrero), 2 Senda del baluarte (puente al Curandero), 3 Senda de la embestida (puente al Mago).

- **Provocar** (V): los monstruos hostiles a 10 bloques te toman como objetivo; Resistencia I 6 s; espera 25 s. **II**: los monstruos hostiles a 14 bloques te toman como objetivo; Resistencia I 9 s y 4 de absorción; espera 25 s.
- **Baluarte** (B): 8 s de Resistencia II para ti y Resistencia I para los jugadores a 6 bloques; espera 60 s. **II**: 10 s de Resistencia II para ti y Resistencia I para los jugadores a 9 bloques; espera 60 s.
- **Golpe sísmico** (N, final de la senda del desafío; una de tres): golpeas el suelo: los hostiles a 5 bloques reciben 8 de daño y 50 de postura, saltan y quedan con Lentitud II 3 s; cuesta 30 de estamina; espera 35 s. **II**: golpeas el suelo: los hostiles a 6 bloques reciben 10 de daño y 70 de postura, saltan y quedan con Lentitud II 3 s; cuesta 30 de estamina; espera 35 s.
- **Santuario de acero** (N, final de la senda del baluarte; una de tres): clavas el escudo: durante 8 s, un círculo de 4 bloques da Resistencia I y Regeneración I a los aliados que están dentro, y a ti, y echa fuera a los hostiles; cuesta 30 de estamina; espera 60 s. **II**: clavas el escudo: durante 10 s, un círculo de 5 bloques da Resistencia I y Regeneración I a los aliados que están dentro, y a ti Resistencia II, y echa fuera a los hostiles; cuesta 30 de estamina; espera 60 s.
- **Embestida de escudo** (N, final de la senda de la embestida; una de tres): cargas 6 bloques al frente; lo que golpeas recibe 6 de daño, 40 de postura y sale empujado; espera 20 s. **II**: cargas 8 bloques al frente; lo que golpeas recibe 6 de daño, 40 de postura, sale empujado y queda aturdido; espera 20 s.

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
| `a.tronco_2` | Ventana de parada | menor | 1 | ventana de parada +1 tick | `a.tronco_1`, `a.tronco_3`, `a.habilidad_v2` |
| `a.tronco_3` | Estamina al parar | menor | 1 | estamina al parar −3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Escudo pesado | notable | 1 | estamina al parar −20 % | `a.tronco_3`, `a1.1`, `a2.1` |
| `a.habilidad_v2` | Provocar II | habilidad | 2 | los monstruos hostiles a 14 bloques te toman como objetivo; Resistencia I 9 s y 4 de absorción; espera 25 s | `a.tronco_2` |
| `a1.1` | Estamina al parar | menor | 1 | estamina al parar −3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Armadura | menor | 1 | armadura +1 | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Represalia | notable | 1 | quien golpea tu escudo levantado recibe 3 de daño | `a1.2`, `a1.4` |
| `a1.4` | Dureza de armadura | menor | 1 | dureza de armadura +0,5 | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Espinas de acero | **clave** | 2 | daño cuerpo a cuerpo −15 %; quien golpea tu escudo recibe además el 30 % del daño parado *(gana: devuelve el 30 % de lo parado; precio: daño c/c −15 %; excluye a `a2.5`)* | `a1.4` |
| `a1.lado_1` | Vida | menor | 1 | vida +3 % | `a1.2` |
| `a1.lado_2` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `a1.4` |
| `a2.1` | Ventana de parada | menor | 1 | ventana de parada +1 tick | `a.tronco_4`, `a2.2` |
| `a2.2` | Estamina al parar | menor | 1 | estamina al parar −3 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Bastión | notable | 1 | ventana de parada +2 ticks | `a2.2`, `a2.4` |
| `a2.4` | Estamina al parar | menor | 1 | estamina al parar −3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Muralla viva | **clave** | 2 | velocidad −5 %; mientras levantas el escudo, los jugadores a 3 bloques detrás de ti reciben −20 % de daño *(gana: proteges a los de detrás; precio: velocidad −5 %; excluye a `a1.5`)* | `a2.4` |
| `a2.lado_1` | Armadura | menor | 1 | armadura +1 | `a2.2` |
| `a2.lado_2` | Vida | menor | 1 | vida +3 % | `a2.4` |
| `s1.1` | Armadura | menor | 1 | armadura +1 | `nucleo_2`, `s1.2` |
| `s1.2` | Vida | menor | 1 | vida +3 % | `s1.1`, `s1.3` |
| `s1.3` | Estamina al parar | menor | 1 | estamina al parar −3 % | `s1.2`, `s1.ultima`, `s1.puente_1` |
| `s1.ultima` | Golpe sísmico | habilidad | 3 | golpeas el suelo: los hostiles a 5 bloques reciben 8 de daño y 50 de postura, saltan y quedan con Lentitud II 3 s; cuesta 30 de estamina; espera 35 s | `s1.3`, `s1.ultima_ii` |
| `s1.ultima_ii` | Golpe sísmico II | habilidad | 2 | golpeas el suelo: los hostiles a 6 bloques reciben 10 de daño y 70 de postura, saltan y quedan con Lentitud II 3 s; cuesta 30 de estamina; espera 35 s | `s1.ultima` |
| `s1.puente_1` | Puente al Guerrero | puente | 2 | regeneración de estamina +5 % *(del Guerrero)* | `s1.3`, `s1.puente_2` |
| `s1.puente_2` | Daño de postura | cruzado | 2 | daño de postura +5 % *(del Guerrero)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Réplica menor | cruzado | 2 | una parada perfecta te devuelve 10 de estamina *(del Guerrero)* | `s1.puente_2` |
| `b.tronco_1` | Armadura | menor | 1 | armadura +1 | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Vida | menor | 1 | vida +3 % | `b.tronco_1`, `b.tronco_3`, `b.habilidad_b` |
| `b.tronco_3` | Dureza de armadura | menor | 1 | dureza de armadura +0,5 | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Piel de hierro | notable | 1 | armadura +2 | `b.tronco_3`, `b1.1`, `b2.1` |
| `b.habilidad_b` | Baluarte | habilidad | 2 | 8 s de Resistencia II para ti y Resistencia I para los jugadores a 6 bloques; espera 60 s | `b.tronco_2`, `b.habilidad_b2` |
| `b.habilidad_b2` | Baluarte II | habilidad | 2 | 10 s de Resistencia II para ti y Resistencia I para los jugadores a 9 bloques; espera 60 s | `b.habilidad_b` |
| `b1.1` | Dureza de armadura | menor | 1 | dureza de armadura +0,5 | `b.tronco_4`, `b1.2` |
| `b1.2` | Daño recibido | menor | 1 | daño recibido −3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Dureza | notable | 1 | dureza de armadura +2; daño recibido −8 % | `b1.2`, `b1.4` |
| `b1.4` | Armadura | menor | 1 | armadura +1 | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Yunque viviente | **clave** | 2 | daño recibido −15 %; resistencia al empuje +100 %; coste de esquiva +100 %; distancia de esquiva −30 % *(gana: daño recibido −15 %, sin empuje; precio: esquiva al doble de coste y −30 % de distancia; excluye a `b2.5`)* | `b1.4` |
| `b1.lado_1` | Vida | menor | 1 | vida +3 % | `b1.2` |
| `b1.lado_2` | Daño de fuego recibido | menor | 1 | daño de fuego recibido −3 % | `b1.4` |
| `b2.1` | Vida | menor | 1 | vida +3 % | `b.tronco_4`, `b2.2` |
| `b2.2` | Vida | menor | 1 | vida +3 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Coloso | notable | 1 | vida +10 % | `b2.2`, `b2.4` |
| `b2.4` | Vida | menor | 1 | vida +3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Gigante | **clave** | 2 | vida +20 %; resistencia al empuje +50 %; estamina máxima −20 %; regeneración de estamina −20 % *(gana: vida +20 %, empuje +50 %; precio: estamina máx. y regeneración −20 %; excluye a `b1.5`)* | `b2.4` |
| `b2.lado_1` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `b2.2` |
| `b2.lado_2` | Armadura | menor | 1 | armadura +1 | `b2.4` |
| `s2.1` | Vida | menor | 1 | vida +3 % | `nucleo_4`, `s2.2` |
| `s2.2` | Dureza de armadura | menor | 1 | dureza de armadura +0,5 | `s2.1`, `s2.3` |
| `s2.3` | Daño recibido | menor | 1 | daño recibido −3 % | `s2.2`, `s2.ultima`, `s2.puente_1` |
| `s2.ultima` | Santuario de acero | habilidad | 3 | clavas el escudo: durante 8 s, un círculo de 4 bloques da Resistencia I y Regeneración I a los aliados que están dentro, y a ti, y echa fuera a los hostiles; cuesta 30 de estamina; espera 60 s | `s2.3`, `s2.ultima_ii` |
| `s2.ultima_ii` | Santuario de acero II | habilidad | 2 | clavas el escudo: durante 10 s, un círculo de 5 bloques da Resistencia I y Regeneración I a los aliados que están dentro, y a ti Resistencia II, y echa fuera a los hostiles; cuesta 30 de estamina; espera 60 s | `s2.ultima` |
| `s2.puente_1` | Puente al Curandero | puente | 2 | curación +10 % *(del Curandero)* | `s2.3`, `s2.puente_2` |
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
| `c1.5` | Último bastión | **clave** | 2 | una vez cada 5 min, un golpe mortal te deja a 1 de vida con Resistencia III 3 s; mientras se recarga, vida máxima −20 % *(gana: sobrevives a un golpe mortal; precio: vida −20 % mientras se recarga; excluye a `c2.5`)* | `c1.4` |
| `c1.lado_1` | Vida | menor | 1 | vida +3 % | `c1.2` |
| `c1.lado_2` | Daño recibido | menor | 1 | daño recibido −3 % | `c1.4` |
| `c2.1` | Vida | menor | 1 | vida +3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Desafío | notable | 1 | los monstruos que provocas quedan con Debilidad I 6 s | `c2.2`, `c2.4` |
| `c2.4` | Daño recibido | menor | 1 | daño recibido −3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Imán de golpes | **clave** | 2 | daño cuerpo a cuerpo −10 %; los hostiles a 6 bloques van siempre a por ti, y con 3 o más cerca recibes −10 % *(gana: −10 % rodeado, atraes a todo; precio: daño c/c −10 %; excluye a `c1.5`)* | `c2.4` |
| `c2.lado_1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c2.2` |
| `c2.lado_2` | Estamina al parar | menor | 1 | estamina al parar −3 % | `c2.4` |
| `s3.1` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Pisotón | notable | 1 | al caer desde 3 bloques o más empujas lo que hay a 3 bloques y le haces 10 de postura | `s3.1`, `s3.3` |
| `s3.3` | Resistencia al empuje | menor | 1 | resistencia al empuje +5 % | `s3.2`, `s3.ultima`, `s3.puente_1` |
| `s3.ultima` | Embestida de escudo | habilidad | 3 | cargas 6 bloques al frente; lo que golpeas recibe 6 de daño, 40 de postura y sale empujado; espera 20 s | `s3.3`, `s3.ultima_ii` |
| `s3.ultima_ii` | Embestida de escudo II | habilidad | 2 | cargas 8 bloques al frente; lo que golpeas recibe 6 de daño, 40 de postura, sale empujado y queda aturdido; espera 20 s | `s3.ultima` |
| `s3.puente_1` | Puente al Mago | puente | 2 | maná máximo +10 % *(del Mago)* | `s3.3`, `s3.puente_2` |
| `s3.puente_2` | Regeneración de maná | cruzado | 2 | regeneración de maná +50 % *(del Mago)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Barrera menor | cruzado | 2 | daño mágico recibido −15 % *(del Mago)* | `s3.puente_2` |

### Mago

Base: vida −10 %, estamina −10 %, hechizos +10 %, espera −10 %, maná +25 %, regeneración de maná ×6. Ramas: A Arcano, B Flujo, C Égida. Sendas: 1 Senda de la nova (puente al Asesino), 2 Senda de la calma (puente al Curandero), 3 Senda del meteoro (puente al Tanque).

- **Nova arcana** (V): un anillo de 5 bloques: 6 de daño mágico a los hostiles, y los empuja; espera 20 s. **II**: un anillo de 6 bloques: 9 de daño mágico a los hostiles, y los empuja; espera 20 s.
- **Concentración** (B): 8 s con la espera de los hechizos al 50 % y sin coste de maná; espera 60 s. **II**: 11 s con la espera de los hechizos al 50 % y sin coste de maná; espera 60 s.
- **Relámpago en cadena** (N, final de la senda de la nova; una de tres): un rayo al enemigo que miras (hasta 16 bloques): 10 de daño mágico, y salta a 4 enemigos más a 5 bloques, un 20 % menos en cada salto; cuesta 35 de maná; espera 25 s. **II**: un rayo al enemigo que miras (hasta 16 bloques): 12 de daño mágico, y salta a 6 enemigos más a 5 bloques, un 20 % menos en cada salto; cuesta 35 de maná; espera 25 s.
- **Prisión de hielo** (N, final de la senda de la calma; una de tres): el hielo atrapa a los hostiles a 4 bloques de donde miras (hasta 24): 6 de daño mágico y 3 s congelados (Lentitud VII; si no son jefes, aturdidos: se corta su ataque); cuesta 45 de maná; espera 45 s. **II**: el hielo atrapa a los hostiles a 5 bloques de donde miras (hasta 24): 8 de daño mágico y 4 s congelados (Lentitud VII; si no son jefes, aturdidos: se corta su ataque); cuesta 45 de maná; espera 45 s.
- **Meteoro** (N, final de la senda del meteoro; una de tres): tras 1,5 s cae un meteoro donde miras (hasta 24 bloques): 12 de daño mágico en 3 bloques y fuego 3 s; cuesta 40 de maná; espera 40 s. **II**: tras 1,5 s cae un meteoro donde miras (hasta 24 bloques): 16 de daño mágico en 4 bloques y fuego 3 s; cuesta 40 de maná; espera 40 s.

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
| `a.tronco_2` | Tiempo de carga | menor | 1 | tiempo de carga −3 % | `a.tronco_1`, `a.tronco_3`, `a.habilidad_v2` |
| `a.tronco_3` | Bono de la carga completa | menor | 1 | bono de la carga completa +3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Sobrecarga arcana | notable | 1 | bono de la carga completa +20 % | `a.tronco_3`, `a1.1`, `a2.1` |
| `a.habilidad_v2` | Nova arcana II | habilidad | 2 | un anillo de 6 bloques: 9 de daño mágico a los hostiles, y los empuja; espera 20 s | `a.tronco_2` |
| `a1.1` | Daño de hechizos | menor | 1 | daño de hechizos +3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Daño de hechizos | menor | 1 | daño de hechizos +3 % | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Catalizador | notable | 1 | daño de hechizos +5 %; un hechizo que mata te devuelve 5 de maná | `a1.2`, `a1.4` |
| `a1.4` | Espera de esquiva | menor | 1 | espera de esquiva −3 % | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Hechizo encadenado | **clave** | 2 | espera de hechizos +20 %; el proyectil del báculo salta a otro enemigo a 5 bloques con el 50 % del daño *(gana: rebote al 50 %; precio: espera de hechizos +20 %; excluye a `a2.5`)* | `a1.4` |
| `a1.lado_1` | Daño de hechizos | menor | 1 | daño de hechizos +3 % | `a1.2` |
| `a1.lado_2` | Daño mágico recibido | menor | 1 | daño mágico recibido −3 % | `a1.4` |
| `a2.1` | Bono de la carga completa | menor | 1 | bono de la carga completa +3 % | `a.tronco_4`, `a2.2` |
| `a2.2` | Tiempo de carga | menor | 1 | tiempo de carga −3 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Carga profunda | notable | 1 | mantener la carga llena 1 s más añade +15 % al hechizo | `a2.2`, `a2.4` |
| `a2.4` | Bono de la carga completa | menor | 1 | bono de la carga completa +3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Todo o nada | **clave** | 2 | bono de la carga completa +60 %; los hechizos sin cargar (menos de un tercio) hacen −40 % *(gana: carga completa +60 %; precio: sin cargar −40 %; excluye a `a1.5`)* | `a2.4` |
| `a2.lado_1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `a2.2` |
| `a2.lado_2` | Velocidad | menor | 1 | velocidad +3 % | `a2.4` |
| `s1.1` | Velocidad | menor | 1 | velocidad +3 % | `nucleo_2`, `s1.2` |
| `s1.2` | Estamina máxima | menor | 1 | estamina máxima +3 % | `s1.1`, `s1.3` |
| `s1.3` | Daño mágico recibido | menor | 1 | daño mágico recibido −3 % | `s1.2`, `s1.ultima`, `s1.puente_1` |
| `s1.ultima` | Relámpago en cadena | habilidad | 3 | un rayo al enemigo que miras (hasta 16 bloques): 10 de daño mágico, y salta a 4 enemigos más a 5 bloques, un 20 % menos en cada salto; cuesta 35 de maná; espera 25 s | `s1.3`, `s1.ultima_ii` |
| `s1.ultima_ii` | Relámpago en cadena II | habilidad | 2 | un rayo al enemigo que miras (hasta 16 bloques): 12 de daño mágico, y salta a 6 enemigos más a 5 bloques, un 20 % menos en cada salto; cuesta 35 de maná; espera 25 s | `s1.ultima` |
| `s1.puente_1` | Puente al Asesino | puente | 2 | velocidad +3 % *(del Asesino)* | `s1.3`, `s1.puente_2` |
| `s1.puente_2` | Coste de esquiva | cruzado | 2 | coste de esquiva −8 % *(del Asesino)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Puñalada menor | cruzado | 2 | daño por la espalda +20 % *(del Asesino)* | `s1.puente_2` |
| `b.tronco_1` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Espera de hechizos | menor | 1 | espera de hechizos −3 % | `b.tronco_1`, `b.tronco_3`, `b.habilidad_b` |
| `b.tronco_3` | Coste de maná | menor | 1 | coste de maná −3 % | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Mente clara | notable | 1 | regeneración de maná +100 % | `b.tronco_3`, `b1.1`, `b2.1` |
| `b.habilidad_b` | Concentración | habilidad | 2 | 8 s con la espera de los hechizos al 50 % y sin coste de maná; espera 60 s | `b.tronco_2`, `b.habilidad_b2` |
| `b.habilidad_b2` | Concentración II | habilidad | 2 | 11 s con la espera de los hechizos al 50 % y sin coste de maná; espera 60 s | `b.habilidad_b` |
| `b1.1` | Daño mágico recibido | menor | 1 | daño mágico recibido −3 % | `b.tronco_4`, `b1.2` |
| `b1.2` | Tiempo de carga | menor | 1 | tiempo de carga −3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Canalización | notable | 1 | tiempo de carga −20 %; maná máximo +25 % | `b1.2`, `b1.4` |
| `b1.4` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Pozo sin fondo | **clave** | 2 | maná máximo +60 %; tu maná se regenera a la mitad *(gana: maná +60 %; precio: regeneración ×0,5; excluye a `b2.5`)* | `b1.4` |
| `b1.lado_1` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `b1.2` |
| `b1.lado_2` | Vida | menor | 1 | vida +3 % | `b1.4` |
| `b2.1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `b.tronco_4`, `b2.2` |
| `b2.2` | Daño mágico recibido | menor | 1 | daño mágico recibido −3 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Economía arcana | notable | 1 | coste de maná −20 % | `b2.2`, `b2.4` |
| `b2.4` | Coste de esquiva | menor | 1 | coste de esquiva −3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Sangre por maná | **clave** | 2 | sin maná, los hechizos se pagan con vida (1 de vida por cada 5 de maná que falte); tu maná se regenera un 30 % más lento *(gana: lanzar sin maná; precio: vida por maná, regeneración −30 %; excluye a `b1.5`)* | `b2.4` |
| `b2.lado_1` | Espera de hechizos | menor | 1 | espera de hechizos −3 % | `b2.2` |
| `b2.lado_2` | Velocidad | menor | 1 | velocidad +3 % | `b2.4` |
| `s2.1` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `nucleo_4`, `s2.2` |
| `s2.2` | Vida | menor | 1 | vida +3 % | `s2.1`, `s2.3` |
| `s2.3` | Vida | menor | 1 | vida +3 % | `s2.2`, `s2.ultima`, `s2.puente_1` |
| `s2.ultima` | Prisión de hielo | habilidad | 3 | el hielo atrapa a los hostiles a 4 bloques de donde miras (hasta 24): 6 de daño mágico y 3 s congelados (Lentitud VII; si no son jefes, aturdidos: se corta su ataque); cuesta 45 de maná; espera 45 s | `s2.3`, `s2.ultima_ii` |
| `s2.ultima_ii` | Prisión de hielo II | habilidad | 2 | el hielo atrapa a los hostiles a 5 bloques de donde miras (hasta 24): 8 de daño mágico y 4 s congelados (Lentitud VII; si no son jefes, aturdidos: se corta su ataque); cuesta 45 de maná; espera 45 s | `s2.ultima` |
| `s2.puente_1` | Puente al Curandero | puente | 2 | curación +10 % *(del Curandero)* | `s2.3`, `s2.puente_2` |
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
| `c1.5` | Parpadeo | **clave** | 2 | distancia de esquiva +50 %; espera de esquiva +30 %; al esquivar te vuelves invisible 1 s, gastando 15 de maná *(gana: esquiva +50 % de distancia e invisible; precio: maná por esquiva, espera +30 %; excluye a `c2.5`)* | `c1.4` |
| `c1.lado_1` | Velocidad | menor | 1 | velocidad +3 % | `c1.2` |
| `c1.lado_2` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c1.4` |
| `c2.1` | Vida | menor | 1 | vida +3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Daño recibido | menor | 1 | daño recibido −3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Égida | notable | 1 | cada 30 s ganas 4 de absorción | `c2.2`, `c2.4` |
| `c2.4` | Vida | menor | 1 | vida +3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Escudo de maná | **clave** | 2 | el 30 % del daño que recibes lo paga el maná (2 de maná por punto); tu maná se regenera un 25 % más lento *(gana: 30 % del daño al maná; precio: regeneración −25 %; excluye a `c1.5`)* | `c2.4` |
| `c2.lado_1` | Daño mágico recibido | menor | 1 | daño mágico recibido −3 % | `c2.2` |
| `c2.lado_2` | Vida | menor | 1 | vida +3 % | `c2.4` |
| `s3.1` | Daño mágico recibido | menor | 1 | daño mágico recibido −3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Runa de escarcha | notable | 1 | tus hechizos dejan Lentitud I 2 s | `s3.1`, `s3.3` |
| `s3.3` | Vida | menor | 1 | vida +3 % | `s3.2`, `s3.ultima`, `s3.puente_1` |
| `s3.ultima` | Meteoro | habilidad | 3 | tras 1,5 s cae un meteoro donde miras (hasta 24 bloques): 12 de daño mágico en 3 bloques y fuego 3 s; cuesta 40 de maná; espera 40 s | `s3.3`, `s3.ultima_ii` |
| `s3.ultima_ii` | Meteoro II | habilidad | 2 | tras 1,5 s cae un meteoro donde miras (hasta 24 bloques): 16 de daño mágico en 4 bloques y fuego 3 s; cuesta 40 de maná; espera 40 s | `s3.ultima` |
| `s3.puente_1` | Puente al Tanque | puente | 2 | vida +5 % *(del Tanque)* | `s3.3`, `s3.puente_2` |
| `s3.puente_2` | Armadura | cruzado | 2 | armadura +1 *(del Tanque)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Represalia menor | cruzado | 2 | quien golpea tu escudo levantado recibe 2 de daño *(del Tanque)* | `s3.puente_2` |

### Curandero

Base: curación +50 %, maná +15 %, regeneración de maná ×4, regeneración de estamina +10 %. Ramas: A Sanación, B Amparo, C Fe. Sendas: 1 Senda del pulso (puente al Tanque), 2 Senda del resurgir (puente al Mago), 3 Senda de la luz (puente al Arquero).

- **Pulso sanador** (V): cura 4 (× tu curación) a ti, a los jugadores y a tus animales a 8 bloques; espera 30 s. **II**: cura 6 (× tu curación) a ti, a los jugadores y a tus animales a 8 bloques, y les quita Veneno y Marchitamiento; espera 30 s.
- **Resurgir** (B): el aliado que miras (hasta 16 bloques) recupera el 50 % de la vida que le falta y Regeneración II 5 s; espera 90 s. **II**: el aliado que miras (hasta 16 bloques) recupera el 50 % de la vida que le falta y Regeneración II 8 s; espera 70 s.
- **Oleada de vida** (N, final de la senda del pulso; una de tres): una oleada cura 8 (× tu curación) a ti y a los aliados a 10 bloques, con Regeneración II 4 s, y empuja a los hostiles a 4 bloques; cuesta 50 de maná; espera 60 s. **II**: una oleada cura 10 (× tu curación) a ti y a los aliados a 10 bloques, con Regeneración II 4 s y sin efectos negativos, y empuja a los hostiles a 4 bloques; cuesta 50 de maná; espera 60 s.
- **Segunda vida** (N, final de la senda del resurgir; una de tres): el aliado que miras (hasta 16 bloques), o tú: durante 20 s, el primer golpe mortal le deja con el 40 % de su vida en vez de matarlo; cuesta 40 de maná; espera 120 s. **II**: el aliado que miras (hasta 16 bloques), o tú: durante 30 s, el primer golpe mortal le deja con el 60 % de su vida en vez de matarlo; cuesta 40 de maná; espera 120 s.
- **Escudo de luz** (N, final de la senda de la luz; una de tres): el aliado que miras (hasta 16 bloques), o tú, gana 6 de absorción y Resistencia I 6 s; espera 35 s. **II**: el aliado que miras (hasta 16 bloques), o tú, gana 8 de absorción y Resistencia I 6 s, y pierde un efecto negativo; espera 35 s.

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
| `a.tronco_2` | Curación | menor | 1 | curación +3 % | `a.tronco_1`, `a.tronco_3`, `a.habilidad_v2` |
| `a.tronco_3` | Coste de maná | menor | 1 | coste de maná −3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Manos cálidas | notable | 1 | curación +15 % | `a.tronco_3`, `a1.1`, `a2.1` |
| `a.habilidad_v2` | Pulso sanador II | habilidad | 2 | cura 6 (× tu curación) a ti, a los jugadores y a tus animales a 8 bloques, y les quita Veneno y Marchitamiento; espera 30 s | `a.tronco_2` |
| `a1.1` | Curación | menor | 1 | curación +3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Curación | menor | 1 | curación +3 % | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Milagro | notable | 1 | curación +15 %; una cura que llena la vida da 2 de absorción | `a1.2`, `a1.4` |
| `a1.4` | Curación | menor | 1 | curación +3 % | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Mártir | **clave** | 2 | curación +40 %; cada cura a otro te cuesta 1 de vida (como mucho una vez por segundo, nunca por debajo de 1) *(gana: curación +40 %; precio: 1 de vida por cura; excluye a `a2.5`)* | `a1.4` |
| `a1.lado_1` | Maná máximo | menor | 1 | maná máximo +3 % | `a1.2` |
| `a1.lado_2` | Tiempo de carga | menor | 1 | tiempo de carga −3 % | `a1.4` |
| `a2.1` | Curación | menor | 1 | curación +3 % | `a.tronco_4`, `a2.2` |
| `a2.2` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Renuevo | notable | 1 | lo que curas recibe además Regeneración I 3 s | `a2.2`, `a2.4` |
| `a2.4` | Coste de maná | menor | 1 | coste de maná −3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Florecer | **clave** | 2 | tus curas curan el 150 % repartido en 4 s en vez de al instante *(gana: curas ×1,5; precio: sin curas instantáneas; excluye a `a1.5`)* | `a2.4` |
| `a2.lado_1` | Curación | menor | 1 | curación +3 % | `a2.2` |
| `a2.lado_2` | Vida | menor | 1 | vida +3 % | `a2.4` |
| `s1.1` | Curación | menor | 1 | curación +3 % | `nucleo_2`, `s1.2` |
| `s1.2` | Maná máximo | menor | 1 | maná máximo +3 % | `s1.1`, `s1.3` |
| `s1.3` | Vida | menor | 1 | vida +3 % | `s1.2`, `s1.ultima`, `s1.puente_1` |
| `s1.ultima` | Oleada de vida | habilidad | 3 | una oleada cura 8 (× tu curación) a ti y a los aliados a 10 bloques, con Regeneración II 4 s, y empuja a los hostiles a 4 bloques; cuesta 50 de maná; espera 60 s | `s1.3`, `s1.ultima_ii` |
| `s1.ultima_ii` | Oleada de vida II | habilidad | 2 | una oleada cura 10 (× tu curación) a ti y a los aliados a 10 bloques, con Regeneración II 4 s y sin efectos negativos, y empuja a los hostiles a 4 bloques; cuesta 50 de maná; espera 60 s | `s1.ultima` |
| `s1.puente_1` | Puente al Tanque | puente | 2 | vida +5 % *(del Tanque)* | `s1.3`, `s1.puente_2` |
| `s1.puente_2` | Armadura | cruzado | 2 | armadura +1 *(del Tanque)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Represalia menor | cruzado | 2 | quien golpea tu escudo levantado recibe 2 de daño *(del Tanque)* | `s1.puente_2` |
| `b.tronco_1` | Daño recibido | menor | 1 | daño recibido −3 % | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Curación | menor | 1 | curación +3 % | `b.tronco_1`, `b.tronco_3`, `b.habilidad_b` |
| `b.tronco_3` | Vida | menor | 1 | vida +3 % | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Bendición | notable | 1 | curar a alguien por debajo del 50 % de vida le da Resistencia I 4 s | `b.tronco_3`, `b1.1`, `b2.1` |
| `b.habilidad_b` | Resurgir | habilidad | 2 | el aliado que miras (hasta 16 bloques) recupera el 50 % de la vida que le falta y Regeneración II 5 s; espera 90 s | `b.tronco_2`, `b.habilidad_b2` |
| `b.habilidad_b2` | Resurgir II | habilidad | 2 | el aliado que miras (hasta 16 bloques) recupera el 50 % de la vida que le falta y Regeneración II 8 s; espera 70 s | `b.habilidad_b` |
| `b1.1` | Curación | menor | 1 | curación +3 % | `b.tronco_4`, `b1.2` |
| `b1.2` | Daño recibido | menor | 1 | daño recibido −3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Purificar | notable | 1 | tus curas quitan Veneno, Marchitamiento, Debilidad y Lentitud | `b1.2`, `b1.4` |
| `b1.4` | Daño mágico recibido | menor | 1 | daño mágico recibido −3 % | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Tierra sagrada | **clave** | 2 | Pulso sanador deja 6 s un círculo de 4 bloques que da Resistencia I a los aliados; pero su espera +50 % *(gana: zona de Resistencia I; precio: espera de Pulso +50 %; excluye a `b2.5`)* | `b1.4` |
| `b1.lado_1` | Vida | menor | 1 | vida +3 % | `b1.2` |
| `b1.lado_2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `b1.4` |
| `b2.1` | Vida | menor | 1 | vida +3 % | `b.tronco_4`, `b2.2` |
| `b2.2` | Curación | menor | 1 | curación +3 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Vínculo | notable | 1 | te curas el 20 % de lo que curas a otros | `b2.2`, `b2.4` |
| `b2.4` | Vida | menor | 1 | vida +3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Lazo vital | **clave** | 2 | vida +10 %; recibes tú el 25 % del daño de los jugadores a 8 bloques *(gana: vida +10 %, proteges a tus aliados; precio: te comes el 25 % de su daño; excluye a `b1.5`)* | `b2.4` |
| `b2.lado_1` | Daño recibido | menor | 1 | daño recibido −3 % | `b2.2` |
| `b2.lado_2` | Maná máximo | menor | 1 | maná máximo +3 % | `b2.4` |
| `s2.1` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `nucleo_4`, `s2.2` |
| `s2.2` | Vida | menor | 1 | vida +3 % | `s2.1`, `s2.3` |
| `s2.3` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `s2.2`, `s2.ultima`, `s2.puente_1` |
| `s2.ultima` | Segunda vida | habilidad | 3 | el aliado que miras (hasta 16 bloques), o tú: durante 20 s, el primer golpe mortal le deja con el 40 % de su vida en vez de matarlo; cuesta 40 de maná; espera 120 s | `s2.3`, `s2.ultima_ii` |
| `s2.ultima_ii` | Segunda vida II | habilidad | 2 | el aliado que miras (hasta 16 bloques), o tú: durante 30 s, el primer golpe mortal le deja con el 60 % de su vida en vez de matarlo; cuesta 40 de maná; espera 120 s | `s2.ultima` |
| `s2.puente_1` | Puente al Mago | puente | 2 | maná máximo +10 % *(del Mago)* | `s2.3`, `s2.puente_2` |
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
| `c1.5` | Peregrino | **clave** | 2 | el Aura llega a 10 bloques y cura 1; pero todo tu daño −20 % *(gana: Aura doble y más grande; precio: todo tu daño −20 %; excluye a `c2.5`)* | `c1.4` |
| `c1.lado_1` | Vida | menor | 1 | vida +3 % | `c1.2` |
| `c1.lado_2` | Regeneración de estamina | menor | 1 | regeneración de estamina +3 % | `c1.4` |
| `c2.1` | Vida | menor | 1 | vida +3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Voluntad | notable | 1 | vida +8 % | `c2.2`, `c2.4` |
| `c2.4` | Daño recibido | menor | 1 | daño recibido −3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Martillo de la fe | **clave** | 2 | curación −20 %; tus golpes c/c curan a los aliados a 4 bloques el 20 % del daño hecho *(gana: curar pegando; precio: curación −20 %; excluye a `c1.5`)* | `c2.4` |
| `c2.lado_1` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c2.2` |
| `c2.lado_2` | Regeneración de maná | menor | 1 | regeneración de maná +20 % | `c2.4` |
| `s3.1` | Vida | menor | 1 | vida +3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Rocío | notable | 1 | tu cura sobre alguien por debajo del 25 % de vida es +50 % (cada 20 s) | `s3.1`, `s3.3` |
| `s3.3` | Maná máximo | menor | 1 | maná máximo +3 % | `s3.2`, `s3.ultima`, `s3.puente_1` |
| `s3.ultima` | Escudo de luz | habilidad | 3 | el aliado que miras (hasta 16 bloques), o tú, gana 6 de absorción y Resistencia I 6 s; espera 35 s | `s3.3`, `s3.ultima_ii` |
| `s3.ultima_ii` | Escudo de luz II | habilidad | 2 | el aliado que miras (hasta 16 bloques), o tú, gana 8 de absorción y Resistencia I 6 s, y pierde un efecto negativo; espera 35 s | `s3.ultima` |
| `s3.puente_1` | Puente al Arquero | puente | 2 | tensado +5 % *(del Arquero)* | `s3.3`, `s3.puente_2` |
| `s3.puente_2` | Daño de proyectiles | cruzado | 2 | daño de proyectiles +4 % *(del Arquero)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Ojo de halcón menor | cruzado | 2 | tiros a la cabeza +15 % *(del Arquero)* | `s3.puente_2` |

### Arquero

Base: vida −10 %, velocidad +8 %, proyectiles +15 %, tensado +10 %, esquiva +20 %, espera de esquiva −15 %, caída −25 %. Ramas: A Puntería, B Tensión, C Viento. Sendas: 1 Senda del salto (puente al Asesino), 2 Senda de la lluvia (puente al Guerrero), 3 Senda del trampero (puente al Curandero).

- **Salto atrás** (V): un salto hacia atrás de unos 6 bloques y Caída lenta 2 s; espera 12 s. **II**: un salto hacia atrás de unos 6 bloques y Caída lenta 2 s; tu siguiente flecha en 3 s hace +25 %; espera 8 s.
- **Lluvia de flechas** (B): 12 flechas en 2 s sobre un círculo de 3 bloques donde miras (hasta 32), 4 de daño cada una; espera 45 s. **II**: 18 flechas en 2 s sobre un círculo de 4 bloques donde miras (hasta 32), 4 de daño cada una; espera 45 s.
- **Saeta letal** (N, final de la senda del salto; una de tres): apuntas 0,75 s (una línea de luz lo avisa) y disparas una saeta que atraviesa todo en 32 bloques: 12 de daño, ×1,5 a lo que tiene menos del 50 % de vida; cuesta 25 de estamina; espera 30 s. **II**: apuntas 0,5 s (una línea de luz lo avisa) y disparas una saeta que atraviesa todo en 32 bloques: 15 de daño, ×1,5 a lo que tiene menos del 50 % de vida; cuesta 25 de estamina; espera 30 s.
- **Flecha explosiva** (N, final de la senda de la lluvia; una de tres): una flecha estalla donde miras (hasta 32 bloques): 8 de daño, 40 de postura y empuje a lo que hay a 3 bloques, sin romper bloques; cuesta 20 de estamina; espera 25 s. **II**: una flecha estalla donde miras (hasta 32 bloques): 10 de daño, 60 de postura y empuje a lo que hay a 4 bloques, sin romper bloques; cuesta 20 de estamina; espera 25 s.
- **Flecha de red** (N, final de la senda del trampero; una de tres): una red donde miras (hasta 24 bloques) atrapa a los monstruos a 3 bloques: Lentitud IV 3 s; espera 20 s. **II**: una red donde miras (hasta 24 bloques) atrapa a los monstruos a 4 bloques: Lentitud IV 4 s, y tus flechas les hacen +15 %; espera 20 s.

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
| `a.tronco_2` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `a.tronco_1`, `a.tronco_3`, `a.habilidad_v2` |
| `a.tronco_3` | Daño de proyectiles | menor | 1 | daño de proyectiles +3 % | `a.tronco_2`, `a.tronco_4` |
| `a.tronco_4` | Ojo de halcón | notable | 1 | daño de proyectiles +6 % | `a.tronco_3`, `a1.1`, `a2.1` |
| `a.habilidad_v2` | Salto atrás II | habilidad | 2 | un salto hacia atrás de unos 6 bloques y Caída lenta 2 s; tu siguiente flecha en 3 s hace +25 %; espera 8 s | `a.tronco_2` |
| `a1.1` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `a.tronco_4`, `a1.2` |
| `a1.2` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `a1.1`, `a1.3`, `a1.lado_1` |
| `a1.3` | Tiro a la cabeza | notable | 1 | tiros a la cabeza +20 % | `a1.2`, `a1.4` |
| `a1.4` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `a1.3`, `a1.5`, `a1.lado_2` |
| `a1.5` | Francotirador | **clave** | 2 | tiros a la cabeza +40 %; Tiro lejano cuenta el doble; a menos de 8 bloques haces −30 % *(gana: cabeza +40 %, lejano ×2; precio: −30 % de cerca; excluye a `a2.5`)* | `a1.4` |
| `a1.lado_1` | Tensado | menor | 1 | tensado +3 % | `a1.2` |
| `a1.lado_2` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `a1.4` |
| `a2.1` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `a.tronco_4`, `a2.2` |
| `a2.2` | Tensado | menor | 1 | tensado +3 % | `a2.1`, `a2.3`, `a2.lado_1` |
| `a2.3` | Tiro lejano | notable | 1 | +2 % por bloque más allá de 10, hasta +30 % | `a2.2`, `a2.4` |
| `a2.4` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `a2.3`, `a2.5`, `a2.lado_2` |
| `a2.5` | Ojo de águila | **clave** | 2 | tensado −20 %; agachado, tus flechas vuelan rectas y hacen +15 % *(gana: tiro recto +15 % agachado; precio: tensado −20 %; excluye a `a1.5`)* | `a2.4` |
| `a2.lado_1` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `a2.2` |
| `a2.lado_2` | Daño de caída | menor | 1 | daño de caída −3 % | `a2.4` |
| `s1.1` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `nucleo_2`, `s1.2` |
| `s1.2` | Tensado | menor | 1 | tensado +3 % | `s1.1`, `s1.3` |
| `s1.3` | Velocidad | menor | 1 | velocidad +3 % | `s1.2`, `s1.ultima`, `s1.puente_1` |
| `s1.ultima` | Saeta letal | habilidad | 3 | apuntas 0,75 s (una línea de luz lo avisa) y disparas una saeta que atraviesa todo en 32 bloques: 12 de daño, ×1,5 a lo que tiene menos del 50 % de vida; cuesta 25 de estamina; espera 30 s | `s1.3`, `s1.ultima_ii` |
| `s1.ultima_ii` | Saeta letal II | habilidad | 2 | apuntas 0,5 s (una línea de luz lo avisa) y disparas una saeta que atraviesa todo en 32 bloques: 15 de daño, ×1,5 a lo que tiene menos del 50 % de vida; cuesta 25 de estamina; espera 30 s | `s1.ultima` |
| `s1.puente_1` | Puente al Asesino | puente | 2 | velocidad +3 % *(del Asesino)* | `s1.3`, `s1.puente_2` |
| `s1.puente_2` | Coste de esquiva | cruzado | 2 | coste de esquiva −8 % *(del Asesino)* | `s1.puente_1`, `s1.puente_3` |
| `s1.puente_3` | Puñalada menor | cruzado | 2 | daño por la espalda +20 % *(del Asesino)* | `s1.puente_2` |
| `b.tronco_1` | Tensado | menor | 1 | tensado +3 % | `nucleo_3`, `b.tronco_2` |
| `b.tronco_2` | Tensado | menor | 1 | tensado +3 % | `b.tronco_1`, `b.tronco_3`, `b.habilidad_b` |
| `b.tronco_3` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `b.tronco_2`, `b.tronco_4` |
| `b.tronco_4` | Mano rápida | notable | 1 | tensado +12 % | `b.tronco_3`, `b1.1`, `b2.1` |
| `b.habilidad_b` | Lluvia de flechas | habilidad | 2 | 12 flechas en 2 s sobre un círculo de 3 bloques donde miras (hasta 32), 4 de daño cada una; espera 45 s | `b.tronco_2`, `b.habilidad_b2` |
| `b.habilidad_b2` | Lluvia de flechas II | habilidad | 2 | 18 flechas en 2 s sobre un círculo de 4 bloques donde miras (hasta 32), 4 de daño cada una; espera 45 s | `b.habilidad_b` |
| `b1.1` | Tensado | menor | 1 | tensado +3 % | `b.tronco_4`, `b1.2` |
| `b1.2` | Coste de estamina | menor | 1 | coste de estamina −3 % | `b1.1`, `b1.3`, `b1.lado_1` |
| `b1.3` | Flecha veloz | notable | 1 | velocidad de flecha +15 % | `b1.2`, `b1.4` |
| `b1.4` | Tensado | menor | 1 | tensado +3 % | `b1.3`, `b1.5`, `b1.lado_2` |
| `b1.5` | Ráfaga | **clave** | 2 | daño de proyectiles −20 %; un arco forjado a 75 % de tensión dispara como a tensión completa *(gana: un tercio más de disparos; precio: proyectiles −20 %; excluye a `b2.5`)* | `b1.4` |
| `b1.lado_1` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `b1.2` |
| `b1.lado_2` | Velocidad | menor | 1 | velocidad +3 % | `b1.4` |
| `b2.1` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `b.tronco_4`, `b2.2` |
| `b2.2` | Tiros a la cabeza | menor | 1 | tiros a la cabeza +3 % | `b2.1`, `b2.3`, `b2.lado_1` |
| `b2.3` | Tiro certero | notable | 1 | un tiro a tensión completa deja Lentitud II 2 s | `b2.2`, `b2.4` |
| `b2.4` | Tensado | menor | 1 | tensado +3 % | `b2.3`, `b2.5`, `b2.lado_2` |
| `b2.5` | Flecha perforante | **clave** | 2 | tensado −20 %; las flechas a tensión completa atraviesan 2 enemigos *(gana: atraviesa 2; precio: tensado −20 %; excluye a `b1.5`)* | `b2.4` |
| `b2.lado_1` | Daño de caída | menor | 1 | daño de caída −3 % | `b2.2` |
| `b2.lado_2` | Vida | menor | 1 | vida +3 % | `b2.4` |
| `s2.1` | Velocidad de flecha | menor | 1 | velocidad de flecha +3 % | `nucleo_4`, `s2.2` |
| `s2.2` | Distancia de esquiva | menor | 1 | distancia de esquiva +3 % | `s2.1`, `s2.3` |
| `s2.3` | Velocidad | menor | 1 | velocidad +3 % | `s2.2`, `s2.ultima`, `s2.puente_1` |
| `s2.ultima` | Flecha explosiva | habilidad | 3 | una flecha estalla donde miras (hasta 32 bloques): 8 de daño, 40 de postura y empuje a lo que hay a 3 bloques, sin romper bloques; cuesta 20 de estamina; espera 25 s | `s2.3`, `s2.ultima_ii` |
| `s2.ultima_ii` | Flecha explosiva II | habilidad | 2 | una flecha estalla donde miras (hasta 32 bloques): 10 de daño, 60 de postura y empuje a lo que hay a 4 bloques, sin romper bloques; cuesta 20 de estamina; espera 25 s | `s2.ultima` |
| `s2.puente_1` | Puente al Guerrero | puente | 2 | regeneración de estamina +5 % *(del Guerrero)* | `s2.3`, `s2.puente_2` |
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
| `c1.5` | Disparo en carrera | **clave** | 2 | vida −10 %; mientras te mueves, tensas un 25 % más rápido *(gana: tensado +25 % en movimiento; precio: vida −10 %; excluye a `c2.5`)* | `c1.4` |
| `c1.lado_1` | Velocidad | menor | 1 | velocidad +3 % | `c1.2` |
| `c1.lado_2` | Estamina máxima | menor | 1 | estamina máxima +3 % | `c1.4` |
| `c2.1` | Daño de caída | menor | 1 | daño de caída −3 % | `c.tronco_4`, `c2.2` |
| `c2.2` | Salto | menor | 1 | salto +3 % | `c2.1`, `c2.3`, `c2.lado_1` |
| `c2.3` | Pluma | notable | 1 | daño de caída −40 %; salto +12 % | `c2.2`, `c2.4` |
| `c2.4` | Salto | menor | 1 | salto +3 % | `c2.3`, `c2.5`, `c2.lado_2` |
| `c2.5` | Halcón | **clave** | 2 | las flechas que disparas en el aire hacen +25 %; las que disparas en el suelo, −10 % *(gana: +25 % en el aire; precio: −10 % en el suelo; excluye a `c1.5`)* | `c2.4` |
| `c2.lado_1` | Velocidad | menor | 1 | velocidad +3 % | `c2.2` |
| `c2.lado_2` | Daño de caída | menor | 1 | daño de caída −3 % | `c2.4` |
| `s3.1` | Daño de caída | menor | 1 | daño de caída −3 % | `nucleo_6`, `s3.2` |
| `s3.2` | Marca del cazador | notable | 1 | la primera flecha que acierta marca 8 s: tus siguientes flechas le hacen +10 % | `s3.1`, `s3.3` |
| `s3.3` | Velocidad | menor | 1 | velocidad +3 % | `s3.2`, `s3.ultima`, `s3.puente_1` |
| `s3.ultima` | Flecha de red | habilidad | 3 | una red donde miras (hasta 24 bloques) atrapa a los monstruos a 3 bloques: Lentitud IV 3 s; espera 20 s | `s3.3`, `s3.ultima_ii` |
| `s3.ultima_ii` | Flecha de red II | habilidad | 2 | una red donde miras (hasta 24 bloques) atrapa a los monstruos a 4 bloques: Lentitud IV 4 s, y tus flechas les hacen +15 %; espera 20 s | `s3.ultima` |
| `s3.puente_1` | Puente al Curandero | puente | 2 | curación +10 % *(del Curandero)* | `s3.3`, `s3.puente_2` |
| `s3.puente_2` | Regeneración de estamina | cruzado | 2 | regeneración de estamina +5 % *(del Curandero)* | `s3.puente_1`, `s3.puente_3` |
| `s3.puente_3` | Vendaje | cruzado | 2 | cada 8 s sin recibir daño recuperas 1 de vida *(del Curandero)* | `s3.puente_2` |

<!-- NODOS:FIN -->
