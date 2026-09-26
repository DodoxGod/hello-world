# Mobs nuevos — lista de Andy (2026-09-19)

Quince propuestas, con el veredicto de Andy sobre cada una. Igual que con el rediseño gráfico: **no se
construye ninguna hasta que elige**, y cuando hay más de una forma posible se le mandan variantes y
escoge él.

Los modelos se dibujan y se miran **fuera del juego** con el renderizador de GeckoLib del scratchpad
(huesos, pivotes, rotaciones y el desplegado UV de Bedrock), validado contra la coraza vacía. Mirar un
modelo cuesta segundos; lanzar el test cuesta trece minutos.

## Aprobados

Los once están **construidos**: entidad, modelo, animaciones, render, IA, aparición y prueba medida.

| # | Nombre | Papel que cubre | Medido en la prueba |
|---|--------|-----------------|---------------------|
| 1 | **Yunque Andante** | Cura: suelda a corazas y autómatas cercanos. No te persigue. | suelda **5.99** a los nuestros, **0.0** a un zombi |
| 2 | **Herrumbre** | Enjambre: come durabilidad en vez de vida. | **14** de desgaste a la pieza tocada, **0** al casco nuevo, **0.94** de vida |
| 3 | **Tenaza** | Control: te agarra y te fija en el sitio. | **2.94** de daño, **0.0** durante el aviso, velocidad **0.0** después |
| 5 | **Percutor** | Rompe guardia con un golpe cenital. | **6.5** al caer, **0.0** durante el aviso, escudo bajado y en enfriamiento |
| 6 | **Molde Roto** | Copia la última arma que usaste y pelea con ella. | pasa de **4.0** a **5.0** con una espada de hierro, copia la **hoja**, ignora una vanilla, suelta **1 plantilla** |
| 9 | **Guardián de Cuño** | Invulnerable hasta romper los sellos. Vive en el **castillo de forja**. | sellado **0.0**; con un sello **0.0**; con ninguno **9.12**. Cuño: **12.79** dentro, **0.0** durante el aviso y **0.0** fuera |
| 11 | **Escoria Viviente** | Se parte en dos al morir; los trozos dejan suelo ardiendo. | **2** mitades vivas, charco encendido |
| 12 | **Cargador de Carbón** | Asedio: revienta en carbón y fuego. | **9.64** dentro del círculo, **0.0** durante la mecha, **0.0** fuera, **6** de carbón en el suelo |
| 13 | **Ascua Mayor** | Pavesa élite; **suelta 5 pavesas** al morir. | **5** pavesas, ya apuntando a quien la mató |
| 14 | **Núcleo Estelar** | Absorbe daño y lo devuelve. | le metimos **24**, guardó **24**, perdió **0.0** de vida y devolvió **30**; el color cambia |
| 15 | **Templador** | Aceite frío: apaga el fuego y **borra lo que llevabas acumulado**. | combo **5 → 0**, vida quitada **0.0**, fuego **0**, deja **1** charco |

## Construidos

### 2 · Herrumbre

El primero que pasa de dibujo a mob. Tapa un agujero real: **todos los demás monstruos del mod amenazan
tu vida, y la vida se recupera sola** — así que una pelea que sobrevives no te ha costado nada, y con
buen equipo se puede cruzar medio mundo sin prestar atención. Éste te cuesta **durabilidad**, que en un
mod donde una pieza lleva dentro sus materiales, sus mejoras y su Maestría es la moneda cara.

Muerde por casi nada de vida (**0.94** medido) y le arranca **14 de desgaste** a una pieza.

Dos decisiones que lo salvan de ser sólo molesto:

- **Va a por la pieza más cerca de romperse**, no a por la mejor. Comerse tu mejor placa haría que la
  única respuesta sensata fuera no ponértela nunca, y eso no es una elección que merezca la pena
  ofrecer. Yendo a por la más gastada, la presión cae sobre tus hábitos de reparación.
- **Sólo aparece a quien lleva metal**: bajo tierra, a oscuras y con al menos dos piezas forjadas
  puestas. Con cuero pasas al lado de un nido y no te enteras. Un enjambre que saliera igualmente sería
  un impuesto por bajar a minar, que todo el mundo tiene que hacer; uno que sólo sale al bien equipado
  es un coste atado a lo que va el mod — y se esquiva quitándote la buena placa, que es una decisión, y
  algo ridícula, y por eso buena.

La prueba mide las tres cosas: `desgaste a la pechera tocada 14, al casco nuevo 0, vida quitada 0.94`.

### Los cinco de la última tanda

**12 · Cargador de Carbón.** Lo interesante no es que explote, es *cuándo*. Se te acerca, planta las
patas y **se hincha durante 36 tics** antes de reventar, con el círculo dibujado en el suelo cerrándose
según baja la mecha. Todo el combate es una pregunta: ¿sigues ahí? Matarlo tampoco es escapar — uno que
muere con la carga dentro revienta igual, más pequeño y **sin aviso**, así que rematarlo cuerpo a cuerpo
es su propia trampa. Lo que queda en el suelo es el carbón, que estaba dentro todo el rato.

**15 · Templador.** No te quita vida. Ni un punto: está medido en **0.0** y es lo que lo define. Te tira
un cazo de aceite frío encima y te borra **el Frenesí** (el combo, que es lo único del mod que empuja
una mejora por encima de su propio tope), la **carga** de un arma voltaica y el fuego que llevabas. Y
deja un charco que sigue haciéndolo a quien se quede dentro. Es el mob que puedes ignorar, e ignorarlo
es justo por donde se pierde la pelea: huye de ti, tira por encima de lo que te esté pegando, y la
respuesta correcta es ir a por él dándole la espalda a lo otro.

**14 · Núcleo Estelar.** Pegarle es peor que no pegarle. Cada golpe se le **guarda** en vez de hacerle
daño, y cuando se llena lo devuelve entero a quien se lo metió: 24 dentro, 30 de vuelta. Los dos avisos
que pediste están, y están separados a propósito porque uno solo no basta cuando la respuesta es
*«para»*: **cambia de color** de azul frío a blanco según se llena, y **las esquirlas se le abren y
giran más rápido**. El color falla a oscuras y falla con un daltónico; una silueta que se abre, no.

**6 · Molde Roto.** El único combate del mod donde **tu equipo es la dificultad**. Mira con qué le has
pegado, mete la hoja fundida en el horno de su barriga durante 30 tics y la saca con la forma de la
tuya, pegando lo que pega la tuya (con techo, para que un arma legendaria no acabe la pelea sola). Si le
pegas con algo vanilla no tiene forma que copiar y se queda como estaba. Y muere llevando puesta la
forma que copió: suelta **la plantilla grabada** de esa pieza, que no se gasta nunca.

**9 · Guardián de Cuño.** Una pelea que **no se empieza atacando**. Mientras quede un farol de pavesa
encendido en su sala, todo lo que le pegues hace exactamente nada — sin barra que bajar, con el aviso
escrito en la propia barra de jefe. Tres faroles, en tres pilares, en medio de la sala, con el guardián
y sus dos constructos entre medias. Los sellos son fáciles de romper **a propósito**: la dificultad
nunca es el farol, es lo que te persigue mientras vas a por él. Su único ataque va a juego — levanta el
cuño y lo estampa contra el suelo, **40 tics**, el aviso más largo del mod.

## Descartados por ahora

- **4 · Alado de Escoria** (caza a quien vuela) y **7 · Ladrón de Temple** (te roba una pieza): Andy no
  los nombró al repasar la lista. No están rechazados, están sin pedir.

## Pendientes de explicar mejor

- **8 · Fuelle** y **10 · Sombra del Forjador**: explicados en conversación, sin veredicto todavía.

## Ideas guardadas

### Percutor «verdugo» — el mazo a dos manos

De las tres variantes del percutor Andy eligió la **C** (pistón), pero pidió expresamente guardar la
**B**: un percutor apoyado en un mazo enorme con la cabeza descansando en el suelo, como quien se apoya
en una herramienta que no puede sostener en alto. Lee como verdugo y no como máquina, y el mandoble que
va a soltar se adivina en la pose de reposo.

No encajó aquí por una razón concreta y no por gusto: de frente, el mazo le tapa el cuerpo entero. Si
alguna vez vuelve, lo natural es que sea **otro mob** y no otra pose del mismo — un campeón de los
campamentos saqueadores, por ejemplo, donde se le ve de perfil llegando.

La geometría, para no volver a dibujarla:

```python
PERC_B_CUBES = {
    "hips":       ([-4.5, 13, -3],  [9, 4, 6],    "dark"),
    "chest":      ([-6, 17, -4],    [12, 10, 8],  "iron"),
    "collar":     ([-7, 25, -5],    [14, 3, 10],  "stone"),
    "vent":       ([-3, 19, -5],    [6, 5, 1],    "ember"),
    "head":       ([-4, 28, -4],    [8, 6, 8],    "dark"),
    "visor":      ([-4, 30, -5],    [8, 2, 1],    "ember"),
    "leg_right":  ([-5, 0, -3],     [5, 13, 6],   "iron"),
    "leg_left":   ([0, 0, -3],      [5, 13, 6],   "iron"),
    "arm_right":  ([-11, 14, -8],   [5, 12, 6],   "iron"),
    "arm_left":   ([6, 14, -8],     [5, 12, 6],   "iron"),
    "maul_shaft": ([-2, 6, -11],    [4, 22, 4],   "dark"),
    "maul_head":  ([-7, 0, -14],    [14, 7, 10],  "stone"),
    "maul_band":  ([-7.5, 2, -14.5],[15, 2, 11],  "iron"),
    "maul_cap":   ([-2.5, 28, -11.5],[5, 3, 5],   "iron"),
}
PERC_B_BONES = [
    ("root", None, [0, 0, 0], []),
    ("leg_right", "root", [-2.5, 13, 0], ["leg_right"]),
    ("leg_left", "root", [2.5, 13, 0], ["leg_left"]),
    ("body", "root", [0, 15, 0], ["hips", "chest", "collar", "vent"]),
    ("head", "body", [0, 28, 0], ["head", "visor"]),
    ("arm_right", "body", [-8, 25, -5], ["arm_right"]),
    ("arm_left", "body", [8, 25, -5], ["arm_left"]),
    ("maul", "body", [0, 20, -9], ["maul_shaft", "maul_head", "maul_band", "maul_cap"]),
]
```

## La espada fundida del molde

Idea de Andy y mejor que la mía. Yo había puesto en sus manos un **hierro apagado**, pensando en "pieza
sin terminar". Él prefirió la hoja fundida, y el razonamiento es el que vale: una barra de metal sin
trabajar es algo *a lo que le falta trabajo*, pero una barra de metal **corriendo** es algo que **aún no
ha decidido**. Eso es exactamente lo que hace el mob, y de paso deja cambiar la silueta de la hoja sin
tocar nada más del modelo.

## Lo que se aprendió construyendo

Dos cosas que el dibujo no podía enseñar y la prueba sí.

**Una regla demasiado corta parece un número equivocado.** El núcleo devolvía «20.0 de 30.0» y parecía
un fallo de la fórmula. No lo era: la víctima era el jugador, y un jugador tiene veinte puntos de vida,
así que el rayo de 42 se medía contra un suelo. La regla era corta, no el número. Se cambió la víctima
por un percutor, que tiene ochenta, y se aprovechó para bajar la capacidad a 24 y el retorno a 1.25 —
30 de golpe sigue siendo serio y es esquivable, 42 era una sentencia.

**Los sellos son de la sala, no del guardián.** La primera versión contaba los faroles alrededor del
*guardián*, con 14 de alcance, y en el castillo eso le llegaba a las **cuatro torres**: veía siete
sellos y se podía «abrir» desde el adarve sin entrar. Ahora cuenta desde **donde lo levantaron**, con
alcance 7, que cubre su sala y se para en la pared. Que el guardián se mueva durante la pelea no puede
cambiar lo que lo mantiene entero.

**Las partículas de un retrato sobreviven al mob del retrato.** Dos fotos seguidas salieron con las
chispas de la anterior encima, porque `clearStage` se lleva las entidades y no lo que dejaron en el
aire. Y el `END_ROD` **cae y deja estela**: cuarenta de ellos metidos en el núcleo se convirtieron en
una columna blanca saliendo por debajo. Chispa eléctrica en su lugar, que se queda donde la pones.

## Lo que se aprendió dibujando



Las dos primeras tandas salieron mal por el mismo motivo las dos veces: **sin silueta**. Un mob de
Minecraft se lee a veinte bloques y con mala luz, donde no es más que un contorno.

- El yunque tenía el pie tan ancho como la cara, así que la cintura no se veía y parecía una pila de
  losas. Lo que hace que un yunque sea un yunque es que la cara **vuela** por encima de la cintura, y
  que tiene cuerno a un lado y talón al otro.
- La tenaza eran dos barras rectas saliendo de un torso, que es una grúa. Unas tenazas son **una sola
  pieza articulada en un punto**.
- Al percutor el martillo le medía lo mismo que el brazo, y apoyado en el suelo se leía como una caja
  al lado del bicho en vez de como peso que carga.
- El molde tenía la raja abierta trece grados, que desde fuera es una columna con una junta. A treinta
  y cinco, lo primero que ves es la cavidad, que es para lo que existe.
- El cargador llevaba el fuego **dentro** de una jaula con tapa: lo único que justifica al bicho,
  metido en un cajón. Barras finas y la llama sacada por encima del borde.
- Al núcleo estelar las esquirlas le quedaban a la misma altura que el centro, así que leía como un
  racimo plano sin nada en medio.

El patrón, las tres veces: **lo que el mob es estaba dentro de la silueta en vez de ser la silueta.**
