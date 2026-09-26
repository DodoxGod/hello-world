# Potencial de mejoras y mesa de extracción — diseño

Propuesta de Andy (transmitida por Codex el 2026-09-20 en `Forja_para_Claude_porcentajes_y_extraccion.md`).
Aquí está lo que Andy dijo, lo que decidí donde había hueco, y los números. **Todos los números viven
en `forge/Potential.java`** para poder tocarlos sin buscar.

## Lo que pidió Andy

1. El **porcentaje máximo de mejoras** de una herramienta sale de cómo se fabricó y puede crecer después:
   piezas (de mesa = sin bono; de fundición = con bono) → miniprueba → calidad → nivel de herrero → mesa
   → potencial base. Algunas mejoras y los pactos lo amplían; al subir de nivel la herramienta sube un poco.
2. «Los pactos/mejoras de evento no afectan al %».
3. En la **mesa básica** las mejoras llegan como mucho al **50 %**.
4. Llegar al **100 %** pide muchos materiales, materiales más difíciles y **un material nuevo**.
5. Tiene que poder **quitarse una mejora concreta**: una mesa que la quita pagando los ingredientes de esa
   mejora, y un **orbe** para guardarla y usarla después.

6. *(2026-09-20, tras probarlo)* El potencial decide también **cuántas mejoras caben**: «una herramienta
   con potencial bajo puede tener pocas mejoras, una con uno muy alto puede tener muchas más». Y acto
   seguido: «algunas mejoras aportan más que otras, por lo que no todas deberían tener el mismo peso…
   teniendo en cuenta las sinergias».
7. *(ídem, regla suya al pie de la letra)* **Mejoras de todo o nada** (Toque de seda, Reparación, Infinidad,
   Multidisparo, Llama, Afinidad acuática, Visión nocturna): las de peso 1-2 se llevan siempre al 100 % en
   la mesa normal (ignoran el tope de la mesa, el potencial y el fundente). Las de peso 3 o más, en la mesa
   normal ni el 1 %: rechazo total sin gastar materiales y aviso de que van en la mesa mayor. En la mesa
   mayor llegan al 100 % sin tope de potencial ni fundente. El peso sí cuenta para la carga. Aplicado en
   `Potential.ceiling()` para que cubra ingredientes, libros, orbes y herencia.

## Decisiones (mías, revisables)

- **Punto 2**, leído así: pactos y mejoras de evento van **por fuera del límite** — siempre pueden llegar al
  100 % — y un pacto además **amplía** el potencial del objeto (+10 cada uno). Es la lectura de Codex y la
  única que cuadra con «los pactos amplían el máximo a cambio de maleficios».
- **Dos límites distintos**: *potencial* (del objeto) y *capacidad* (de la mesa). Se aplica hasta el menor.
  Ninguno de los dos **baja** nunca una mejora ya puesta: sólo impide subirla.
- **Subir el potencial no regala porcentaje**: abre hueco, no lo llena.
- El potencial base se **guarda en el objeto** al forjarlo (no depende de estar junto al fuego).
- **Objetos que ya existían** (sin el dato): potencial base 70. Botín: 60–90. Leyendas: 100.
- **Material nuevo = Fundente maestro**: sin él ninguna mejora normal pasa del 90 %. Se gasta uno por
  mejora, la vez que cruza el 90. Receta exigente pero previsible (hierro estelar + polvo de blaze +
  fragmento de eco), no una probabilidad diminuta. Uno solo para todas las mejoras, no uno por mejora.
- **«Materiales más difíciles»** lo cubren el fundente y los tramos; no escribo cien recetas nuevas.
- **Tramos de coste** (lo que rinde cada ingrediente según por dónde va la mejora):
  0–50 % rinde entero · 50–75 % la mitad · 75–100 % la cuarta parte.
- **Mesa de extracción** (bloque nuevo): herramienta + los ingredientes de la mejora elegida → la quita.
  Con un **orbe vacío** además la guarda **entera** (el desarme sigue devolviendo la mitad: esa es la
  diferencia entre las dos mesas). Coste: los ingredientes de **un paso** de la receta por cada 25 % que
  tenga la mejora (mínimo 1). Si la mejora tiene varias recetas, vale cualquiera. Las de evento, que no
  tienen receta, se extraen pagando **frascos de cristal**… y sólo con orbe (no tiene sentido
  pagar por tirarlas). Los **pactos no se extraen**: «ningún afilador los deshace» ya era su regla.
- La mejora que amplía potencial (**Recocido**, nueva, +15 al 100 %) no se amplía a sí misma ni cuenta
  para el límite; si se extrae y deja mejoras por encima del nuevo potencial, **no se pierden**: se quedan
  como están y no pueden subir hasta que vuelva a haber hueco.
- **Cambiar una pieza** recalcula la parte de «piezas» del potencial base con las piezas nuevas; lo demás
  (calidad, herrero, mesa) se conserva del forjado original.

## Carga: cuánto lleva una pieza

Cada mejora **pesa** de 1 a 4 (`Potential.weight`) y la pieza tiene una **capacidad** que sale del potencial
(`Potential.capacity`): **(potencial − 20) / 4** → la peor pieza que existe (40 %) aguanta **5 puntos**, una
de 60 % 10, una de 80 % 15 y una perfecta **20**. La suma de pesos de lo que lleva no puede pasar la
capacidad. Pactos, mejoras de evento y Recocido pesan 0 (igual que no responden a ningún tope). Una pieza
anterior al sistema que lleve de más **no pierde nada**: sólo no admite nada nuevo; subir lo que ya lleva
siempre se puede.

| Peso | Qué es | Mejoras |
| --- | --- | --- |
| **4** | definen la pieza | Filo, Vampirismo, Poder, Fortuna, Excavación, Protección, Vitalidad, Reparación |
| **3** | fuertes | Crítico, Furia, Onda de choque, Tormenta, Botín, Alcance, Densidad, Aturdimiento, Nudillos de hierro, Segunda cabeza, Desgarro, Infinidad, Multidisparo, Carga rápida, Tensión, Punta afilada, Eficiencia, Toque de seda, Veta, Leñador, Telequinesis, Regeneración, Presteza, Rebote, Absorción, Irrompible |
| **2** | útiles | todas las demás (es también donde cae una mejora nueva hasta que alguien la rankee) |
| **1** | detalles | Empuje, Decapitador, Perdición de artrópodos, Retroceso, Luz, Zancada, Afinidad acuática, Paso helado, Sirga, Cebo |

Criterio: lo que aporta la mejora **al 100 %** en su papel, con los números de `Upgrade` delante (Vampirismo
cura el 30 % del daño; Crítico es +15 % de daño medio; Filo V es +3). Dentro de un grupo excluyente la
general es la pesada, que es lo que por fin da sentido a Castigo (2) frente a Filo (4).

**Sinergias.** No pesan: son lo que se gana por gastar carga en una pareja temática. Pero el ranking las
cuenta — Tormenta y Onda de choque serían doses por sí solas y son treses porque cada una es mitad de dos
parejas; las parejas más rentables son las más caras de llevar (Fortuna + Veta 7, Excavación + Eficiencia
7, Protección + Vitalidad 8) y las de capricho baratas (Botín + Decapitador 4). Ya exigían las dos mitades
al 50 %, así que una pieza de potencial < 50 no despierta ninguna. El test exige que ninguna pareja pese
más de media pieza perfecta.

**Dónde se ve.** Una sola barra (`client/LoadBar`) en tres sitios — tooltip (bajo las piezas), mesa de forja
(panel de información) y rueda de extracción (cabecera): 20 celdas siempre, para que una pieza pobre sea
visiblemente una barra corta; claras las que el potencial ha abierto, un tramo de color por mejora, en rojo
latiendo la que no cabe. La mesa dice «X pesa N y a la pieza sólo le quedan M».

**Todo o nada** (punto 7 de Andy). `Potential.allOrNothing`: encantamiento de un solo nivel o Visión
nocturna. Antes de esto, con el potencial, esas siete se comían los materiales hasta el tope de la pieza y
no funcionaban jamás en ninguna pieza por debajo de 100. `Limit.GREATER` es el rechazo de las pesadas en
la mesa normal; `Potential.needsFlux` evita que un orbe o un libro de una de ellas gaste fundente.

## Números

| Fuente | Potencial |
| --- | --- |
| Suelo | 40 |
| Piezas de fundición limpias | hasta +20 (proporcional a cuántas piezas lo son; las bastas cuentan 0) |
| Miniprueba | fallo 0 · decente +5 · perfecta +10 |
| Nivel de herrero | +1 por nivel (0–10) |
| Mesa | forja 0 · forja mayor +10 · taller completo +5 |
| **Base máxima al forjar** | **95** |
| Maestría del objeto | +1 por nivel (0–10) |
| Pacto | +10 cada uno |
| Recocido | hasta +15 |
| Tope | 100 |

Capacidad de la mesa: forja **50** · forja mayor **100** · talabartería **100** (es la única para monturas).

## Estado

- [x] **Etapa 1** (2026-09-20): `forge/Potential` (todos los números), componentes `POTENCIAL` / `COLADAS`
  (máscara de ranuras coladas) / `COLADA` (en la pieza suelta), mejora `RECOCIDO`, `Upgrade.isPact()`,
  `Station.capacity()`, `UpgradeRecipes.apply(..., Limits)` con tramos + fundente, orbes por **valor**
  (`Potential.value` / `raised`: un orbe devuelve entero lo que guarda sobre una pieza limpia, y paga el
  precio creciente encima de lo que ya hay o al fundirse con otro), libros y orbes con tope y resto en el
  orbe, piezas de la caja de moldeo marcadas, mesas de colada, botín (60–90), leyendas (100), desarme,
  ítems `fundente_maestro` y `orbe_vacio` con receta, textos, línea en el tooltip y en la mesa.
  Test: `FORJA_SOLO=potencial` (`checkPotential`).
- [x] **Etapa 2** (2026-09-20): mesa de extracción — `block/ExtractionTableBlock`, `menu/ExtractionMenu`,
  `client/ExtractionScreen`, `generate_extraction_table` (bloque, panel, receta), test `checkExtraction`
  con clics reales. La herencia (`planInherit`) también pasa por los topes y por el valor.
- [x] **Etapa 3** (2026-09-20): capítulo «Potencial» del libro (los números se leen de `Potential`), README,
  CHANGELOG; las pruebas antiguas que contaban ingredientes hasta el 100 % calculan lo esperado con
  `Potential`, y `maxed()` del test ya no depende de cuánto cueste un 100 %.
- [x] **Inventario propio** (2026-09-20, lo pidió Andy al ver la primera versión, que era una lista sobre el
  panel de siempre): la **rueda** — `generate_extraction_panel` (pizarra y latón, 236×204, ocho engastes),
  `ExtractionScreen` (gemas por mejora, ficha, bandeja con el pago en fantasma, cuna, vuelo de la gema).
  Las posiciones de las casillas están en `ExtractionMenu` y el test las comprueba contra la textura con
  los colores de casilla de ESTE panel (`slotsOffTheirSquares(mc, tex, 0x101016, 0x22222A)`).
- [x] **Carga por pesos y todo-o-nada** (2026-09-20): `Potential.weight / capacity / load / fits /
  allOrNothing / needsFlux`, `Limit.LOAD` y `Limit.GREATER`, `ForgeMenu.Refusal` (lo que un orbe, libro o
  herencia traía y no entró, y por qué), herencia evaluada contra el heredero según crece (y lo que no pesa
  primero), `client/LoadBar`, líneas nuevas en `ForgeScreen` (`Lines.pair` / `Lines.load`), cabecera y peso
  en la rueda, capítulo del libro. Test: `checkLoad` (dentro de `FORJA_SOLO=potencial`).
- [ ] **Por ver con Andy**: los números (sobre todo 40 de suelo y 50 de la mesa básica), si el fundente
  debe empezar en 90 o antes, y si la interpretación del punto 9 es la que quería.

## Validación (test)

- La mesa básica no sube nada normal del 50 % ni baja lo que ya esté por encima.
- Subir potencial no cambia ningún porcentaje instalado.
- Sin fundente nada normal pasa del 90 %; con él sí, y se gasta uno.
- Pactos y mejoras de evento ignoran ambos límites; un pacto sube el potencial.
- Extraer quita **una** mejora y conserva piezas, calidad, firma, historial, maestría y las demás.
- Con orbe vacío sale un orbe con el porcentaje entero; sin orbe no sale nada; nunca se duplica.
- Libros, orbes, fusión, colada y botín respetan las mismas reglas.
- Una espada de 40 lleva Filo (4) y no admite Aspecto ígneo (2): rechazo por carga, sin gastar nada; Empuje
  (1) sí cabe; llena, lo que ya lleva sigue subiendo; un orbe que no cabe sigue siendo un orbe; un pacto
  entra en una pieza llena y los 10 puntos que trae abren justo el hueco que faltaba.
- Toque de seda en la mesa normal: ni un 1 %, ni una telaraña gastada, aviso de mesa mayor; un libro de
  Reparación, igual. Afinidad acuática (ligera) llega al 100 % en la mesa normal en un casco de 40 sin
  fundente. En la mesa mayor Toque de seda llega al 100 % en un pico de 40 con el fundente intacto, y su
  peso cuenta (ya no cabe Eficiencia).
- Ninguna pareja de sinergia pesa más de media pieza perfecta.
