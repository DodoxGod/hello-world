# Castillo de forja

La estructura del **Guardián de Cuño** (idea 9), y la única del mod construida alrededor de una pelea
en vez de alrededor de un cofre.

## Qué es

21 × 14 × 21 de ladrillo de pizarra profunda, en colinas, montañas y mesetas de sabana. Muralla con
almenas, cuatro torres de tres por tres con un farol arriba, y una puerta al sur con rastrillo de
barrotes por el que se pasa por debajo.

Dentro, en el centro, la **sala de estampado**: once por once, con su propio techo, una moldura dorada
corriendo por arriba para que se lea como la habitación que importa, y el suelo con el patrón grabado.

## La silueta

La primera versión se fotografió como **una caja gris con una valla alrededor**, que es el mismo error
que se cometió tres veces con los mobs: lo que la cosa es estaba dentro del contorno en vez de ser el
contorno. Tres arreglos, todos de silueta y ninguno de detalle:

- **Las torres suben a once**, muy por encima del adarve, que está a seis. Una torre de la misma altura
  que su muralla no es una torre, es un trozo de muralla más gordo.
- **Contrafuertes cada cinco bloques** en la cortina. Veintiún bloques del mismo ladrillo seguido son
  una pared, no una fortificación.
- **Una linterna sobre la sala**: una caja elevada de barrotes con reborde dorado, por donde una forja
  saca su propio humo. Es lo único del perfil que no es muralla, y es lo que dice desde lejos que lo
  que hay dentro es un taller y no un torreón.

## Los tres sellos

En la sala hay **tres pilares con un farol de pavesa encima**, separados y a la vista. Mientras quede
uno encendido el guardián **no recibe daño ninguno** — está medido: seis golpes de doce le quitaron
**0.0**, y con dos de tres faroles rotos sigue quitándole 0.0. Rotos los tres, el siguiente golpe entra
(**9.12**).

Del guardián a cada farol sale un **hilo de oro** de partículas. No hay ningún texto que explique el
combate: el hilo lo explica, y lo explica desde el otro lado de la sala.

Las cuatro torres también llevan farol, y eso es a propósito **y estuvo mal una vez**: el guardián
contaba los faroles a su alrededor, así que veía los siete y se podía abrir desde la muralla sin entrar.
Ahora busca desde **el centro de su sala** con alcance 7. La prueba lo comprueba justo así: `faroles 7,
sellos que ve el guardián 3`.

## Lo que guarda

Un cofre detrás de él, con su yunque y su mesa de forja al lado. Es la **única fuente de plantillas en
cofre** de todo el mod: 2-3 tiradas de plantilla, sello u orbe, más una segunda tirada de oro, acero,
hierro estelar, lapislázuli y botellas de experiencia. El propio guardián suelta **tres plantillas
grabadas** de entre seis formas y su sello.

Por eso está lejos: `spacing 84`, `separation 26`. Un castillo que te encuentras dos veces en una tarde
es un almacén, no un sitio.

## Quién más vive ahí

Una **tenaza** y un **percutor**, colocados en la sala en el propio NBT. No es decoración: el guardián
no te puede hacer nada mientras esté sellado, así que la dificultad de los primeros minutos **son
ellos**. Tenaza te clava en el sitio, percutor te rompe la guardia, y tú tienes tres recados que hacer.
