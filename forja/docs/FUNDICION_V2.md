# Fundición 2: redes, llenado, llave de paso, fluidos de calor y automatización

Pedido de Andy (2026-09-28), con sus respuestas:

1. "Sistema que te permite unir todos los contenedores" → **las dos cosas**: una red por conductos y cubas que se fusionan.
2. "Sistema que llena todos los contenedores, llenando 1 por 1 hasta que no haya un contenedor disponible o haya uno
   pero que no es del mismo tipo de material; no se usan todos para 1, se va llenando cada bloque de forma individual
   hasta llenar el contenedor, apenas ahí se inicia a llenar otro."
3. "Llave de paso."
4. "Formas para calentar la forja (tubos de calor que transportan distintos fluidos: vapor, lava infernal, sangre de
   blaze, fluidos de herrero...; al menos 4). Se usan también para fundir y para todo lo que pide calor; unos mejores que
   otros, cambiando cualidades y lo que ofrecen." → **los 4 propuestos y alguno más**.
5. "Alguna forma para automatizar todo el sistema de forja" → **todo, forja incluida**: la fundición sin tocarla y una
   máquina que monta herramientas con calidad normal (el golpe perfecto sigue siendo del jugador).

Todo esto va encima de la colada física (el metal cae del caño, atraviesa el colador y llena el molde o el marco
sobre la mesa de colada; la caja de moldeo solo prepara). Nada de lo que ya funciona se rompe: los mundos viejos cargan.

## A. Red de metal fundido (puntos 1, 2 y 3)

**Red.** Crisoles, cubas, conductos, canales, caños y mesas de colada unidos por conductos forman UNA red. El metal va
de donde sale (crisoles, cubas) a donde se necesita (cubas con sitio, mesas que cuelan), aunque estén lejos. La red se
calcula por inundación a lo largo de conductos y se guarda en caché; se recalcula solo cuando cambia un bloque de la red.

**Cubas que se fusionan.** Cubas del mismo material pegadas (en cualquier dirección) forman un depósito: una sola
capacidad (la suma), un solo metal, un solo nivel que se dibuja repartido entre los bloques de abajo a arriba. Romper
una cuba divide el depósito y cada parte se queda su proporción. Para la red, un depósito es UN contenedor.

**Llenado de uno en uno (punto 2).** Cuando el metal entra en la red, llena UN contenedor hasta arriba y solo entonces
empieza el siguiente. Elige el contenedor así: primero el que ya tiene ese mismo metal y no está lleno; si no hay, el
vacío más cercano (por distancia en la red). Un contenedor con OTRO metal no se toca. Si no queda ninguno con sitio, el
crisol espera (su pantalla ya dice "no hay cuba con sitio").

**Llave de paso (punto 3).** Un bloque de conducto con palanca: abierto deja pasar y cerrado corta la red en dos. Se
abre y se cierra con clic derecho y también con redstone (con señal, cerrada). La red la trata como un conducto que a
veces no está.

## B. Fluidos de calor (punto 4)

Un segundo sistema de conductos, **tubos de calor**, que no se mezcla con el de metal. Llevan un fluido de calor desde
una **caldera** o un **depósito de calor** hasta lo que necesita calor: crisoles, mesas de colada y mesas de forja
(el calor bajo la mesa). Un tubo que toca una de esas cosas le da el calor de su fluido, en lugar de (o además de) lo
que tenga debajo; se usa el mejor de los dos.

| Fluido | De dónde sale | Calor (escala de `Alloys.Heat`) | Qué cambia además |
|---|---|---|---|
| Vapor | Caldera con agua y cualquier fuego debajo | TEMPLADA | Barato; funde poco (solo lo blando); la mesa de colada no pasa de tibia |
| Lava | Cubos de lava en un depósito de calor | FUNDIDA | Lo normal de la lava, pero llevado por tubos |
| Sangre de blaze | Varas de blaze en la caldera | FUNDIDA, más rápida | Funde un 50 % más deprisa y la mesa se calienta el doble de rápido |
| Aliento de forja | Corazón de forja o escoria en la caldera | FORJA_BLANCA | Alcanza la forja blanca sin crisol de obsidiana; las coladas salen "perfectas" más a menudo |
| Salmuera helada (extra) | Hielo compacto y sal/agua en la caldera | FRÍA (enfría) | Enfría: templa lo colado (más dureza/durabilidad) y apaga la forja; sirve de "agua de temple" automática |

Los números concretos (velocidades, gasto por tick, capacidades) los fija el agente, coherentes con lo que ya hay, y
los deja en `CombatConfig`/constantes con su javadoc. Cada fluido tiene su color en el tubo y en la caldera.

## C. Automatización (punto 5)

- **Fundición sin manos:** las tolvas meten mena y combustible en el crisol (ya), la red reparte, las mesas cuelan solas
  con molde o marco y sacan lo colado a una tolva (ya); lo que falte para que funcione sin tocar nada, se completa.
- **Montadora:** una máquina con inventario (entradas de piezas por tolva, salida) que monta herramientas y armas a
  partir de sus piezas, como la mesa de forja pero sin el golpe de martillo: calidad normal siempre (nunca perfecta). Las
  mejoras, reparaciones y todo lo demás de la mesa siguen siendo a mano. Pide calor como la mesa de forja (bajo ella o
  por un tubo de calor).
- Todo con comparadores donde tenga sentido (nivel de un depósito, montadora trabajando).

## Reglas para todo

- Guía (capítulo de fundición, textos por `tools/generate_lang.py`), CHANGELOG, pruebas de servidor y una sección de
  cliente jugada con clics reales y capturas.
- Rendimiento: nada de recorrer la red cada tick; cachés invalidadas por cambios de bloques.
- Mundos viejos: cargan igual (cubas sueltas siguen siendo cubas; un depósito se forma al cargar).
