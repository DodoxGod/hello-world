# Clases — diseño

Lo que pidió Andy (2026-09-28), lo que decidí donde había hueco y todos los números. **Los números viven en
el código** (`clase/PlayerClass.java`, `clase/Talent.java`, `clase/ActiveSkill.java`, `clase/ClassProgress.java`,
`clase/ClassDamage.java`, `forge/Relic.java` y `magic/Healing.java`); las pantallas y la guía los leen de ahí,
así que este documento es la foto del día en que se escribió (revisado el 2026-09-29 con las decisiones A–F de
Andy, al final).

## Lo que pidió Andy

- **Clase elegida + árbol**: eliges una clase, sube de nivel con experiencia y cada nivel da puntos para el
  árbol de talentos de esa clase.
- **Cambiar de clase: sí, con un costo** (un objeto caro o un ritual); los puntos se reinician.
- **Asesino**: «más stamina, más esquive, un poquito más de daño, bastante menos vida».
- **Tanque**: «mucha vida, stamina normal, menos movilidad, esquive más reducido, más lento».
- **Curandero**: «cambia el daño de las armas mágicas por curación (sólo cura 1/10 parte del daño)».
- **Guerrero, Mago, Arquero/cazador, Herrero**: diseño mío, en el espíritu del mod.
- *(después)* «añade un arma de curación específicamente»: el **Farol** (más abajo).
- *(2026-09-29)* Las decisiones A–F del final: cambiar de clase empieza en el nivel 1; el Curandero sí daña con
  magia (a un tercio) y cuerpo a cuerpo a la mitad; tope 15; el objeto de cambio se **forja**; y un factor de
  daño por clase (melé, proyectiles, magia). Las teclas K/V/B se aprueban, y se cambian en Controles.

## Reglas generales

- Solo los **jugadores** tienen clase. Nada de esto toca a un monstruo, aunque lleve un báculo.
- Sin clase no pasa nada: el jugador juega como siempre, sin bonos ni penalizaciones.
- La clase, el nivel, la experiencia, los talentos y las esperas de las habilidades se guardan en el jugador
  (adjunto `forja:clase`, persistente, sincronizado con su propio cliente) y **sobreviven a la muerte**.
- Los modificadores de vida, velocidad, daño cuerpo a cuerpo, armadura, dureza, empuje, minado, salto, sigilo,
  caída y fuego son **modificadores de atributo** (`forja:clase_<stat>`), que se quitan y se ponen enteros al
  elegir, cambiar, aprender, reaparecer y entrar al mundo. Lo demás (estamina, esquiva, parada, postura,
  magia, forja) lo leen los sistemas del mod a través de **un único sitio**: `clase/ClassEffects`.
- Todos los porcentajes se **suman** entre la base de la clase y los talentos, y luego se aplican una vez
  (base −30 % de vida + talento +15 % = −15 %).

## Nivel, experiencia y puntos

- **Nivel máximo 15.** Empiezas en el 1 al elegir clase.
- **Un punto por nivel**, contando el 1: 15 puntos en el tope.
- Coste de los nodos: **nivel 1 de rama = 1 punto, nivel 2 = 2, nivel 3 = 3; la habilidad II = 3**. El árbol
  entero cuesta 21: en el tope te quedan 6 sin gastar. No se puede tener todo; hay que elegir.
- Experiencia para pasar del nivel N al N+1: **80 + 40·(N − 1)**.

| Nivel | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Experiencia total | 80 | 200 | 360 | 560 | 800 | 1080 | 1400 | 1760 | 2160 | 2600 | 3080 | 3600 | 4160 | 4760 |

### Qué da experiencia de clase

- **Matar un monstruo** (cualquier clase): `max(1, round(vida máxima / 4))` × amenaza (normal 1, veterano 2,
  élite 4, campeón 8); un jefe ×10. Un zombi da 5.
  - **+50 %** si la muerte es «de tu clase»: cuerpo a cuerpo (Guerrero, Asesino, Tanque), hechizo (Mago),
    proyectil (Arquero).
- **Acciones propias** (solo a esa clase):
  - Guerrero: parada lograda 3; derribar la postura de un monstruo 4.
  - Asesino: esquiva perfecta 3; golpe por la espalda 2.
  - Tanque: 1 por cada 4 de daño parado con escudo o recibido (y sobrevivido).
  - Mago: 1 por hechizo que acierta a un monstruo.
  - Curandero: 1 por cada 2 de vida curada **a otros** (jugadores o tus animales).
  - Arquero: 1 por flecha que acierta, +1 por cada 10 bloques de distancia.
  - Herrero: lo mismo que la Maestría de herrero (forjar 10, cambiar pieza 4, mejorar 8, golpe perfecto 15,
    don 25). El Herrero sube forjando.
- Al subir: sonido de subir de nivel, un aviso (toast) con el icono de la clase y una línea en el chat.

## Elegir y cambiar de clase

- **Elegir la primera vez: gratis, desde la guía o con la tecla del árbol (K).** La guía se entrega al entrar por
  primera vez y se abre con G, así que todo jugador la tiene; el capítulo «Clases» tiene un botón «Elegir clase».
  K abre la elección si no tienes clase y el árbol si ya la tienes. Al entrar sin clase, un aviso en el chat lo
  recuerda. *Por qué no un bloque ni un objeto nuevo:* un bloque obliga a construir antes de jugar la clase, y un
  objeto de elección sería un objeto más en el inventario que solo sirve una vez. La guía ya está.
- La pantalla de elección muestra las siete clases con su descripción, sus números base, sus factores de daño y
  sus dos habilidades.
- **Cambiar: el Medallón del olvido** (no se apila), que se **forja** (decisión D, más abajo). Clic derecho abre
  la misma pantalla en modo «cambio»; al confirmar se gasta el medallón.
  - **Se forja** en la estrella de cualquier mesa de forja, como un arma: un **núcleo de eco** (3 fragmentos de
    eco, de las ciudades antiguas: lo que lo hace caro), un **engaste** y una **cadena**, cortados en la mesa de
    piezas con sus plantillas, y un golpe de martillo (la barra del golpe perfecto, como al forjar). El engaste y
    la cadena pueden ser de cualquier material y le dan su color; el núcleo tiene que ser de eco (con otro
    núcleo la mesa dice «El Medallón del olvido pide un núcleo de Eco»). No tiene estadísticas, ni durabilidad,
    ni Maestría, ni mejoras: se gasta al usarlo. La mesa lo forja aunque esté sobre lava (no lo funde).
  - Al cambiar a **otra** clase: **empiezas en el nivel 1, sin experiencia** (decisión A); los talentos se borran;
    las esperas de las habilidades se reinician; los atributos de la clase vieja se quitan. Se cuenta un cambio
    (`ClassData.changes`).
  - En **tu misma** clase no es un cambio: se conservan el nivel y la experiencia, se borran los talentos y
    vuelven todos los puntos (y no cuenta como cambio).
  - El **Emblema del olvido** de antes ya no tiene receta (se borró `data/forja/recipe/emblema_del_olvido.json`)
    ni sale en la pestaña creativa; se queda registrado para que los que ya existen sigan sirviendo igual.
- En creativo el medallón no se gasta. `/forja clase ...` (operadores) hace todo sin coste.
- **Teclas:** K (árbol / elegir), V (habilidad I) y B (habilidad II) son solo las de fábrica. Son tres
  `KeyMapping` registrados en la categoría «Forja: clases» (`client/ClassClient`), así que se cambian en
  Opciones → Controles. Ningún código compara códigos de tecla con ellas: la pantalla del árbol se cierra con
  `TREE.matches(...)`, y todos los textos que nombran una tecla (el aviso al entrar, «clase elegida», el aviso de
  punto nuevo, «no tienes clase», la guía) la reciben como `Component.keybind(...)` (`ClassProgress.key`), que el
  cliente rellena con la tecla que tenga puesta. La prueba de cliente (`ForjaClientTest.showClasses`, solo
  `clases`) comprueba que las tres están en la lista de Controles, en la categoría de Forja y con K, V y B de
  fábrica.

## Las siete clases

Notación: vida, velocidad y daño son porcentajes sobre el valor base; «espera» es el tiempo entre esquivas;
«ventana de parada» son ticks (20 = 1 s).

### Factores de daño por clase (decisión F)

Andy (2026-09-29). Viven en `clase/ClassDamage.java` como constantes con nombre, y se aplican en **un solo
sitio**: `ClassEffects.dealt`, el gancho por el que pasa todo golpe de un jugador (`combat/CombatHooks.afterArmor`).
El tipo de golpe se decide por la fuente del daño: **magia** si es `magic` o `indirect_magic` (el proyectil del
báculo, el área y la runa del grimorio, la Nova arcana); **cuerpo a cuerpo** si lo que golpea es el propio
jugador (y no es magia ni espinas); **proyectil** si lo que golpea es un proyectil (flechas, la cabeza lanzada,
armas arrojadas).

| Clase | Cuerpo a cuerpo | Proyectiles | Magia |
|---|---|---|---|
| Guerrero | ×1 | ×1 | ×0,4 |
| Asesino | ×1 | ×1 | ×0,4 |
| Tanque | ×0,67 | ×0,67 | ×0,67 |
| Mago | ×0,7 | ×1 | ×1 |
| Curandero | ×0,5 | ×1 | ×1/3 |
| Arquero | ×0,7 | ×1 | ×1 |
| Herrero | ×1 | ×1 | ×1 |

**Cómo se combinan con los números de clase.** Los porcentajes de la clase y de sus talentos (daño cuerpo a
cuerpo, de proyectiles, de hechizos, por la espalda, a la cabeza…) **se suman entre sí** y se aplican una vez,
como siempre; el factor **multiplica encima**, al final:
`daño = arma × (1 + suma de porcentajes) × factor`. Un Tanque con una flecha: `× 1 × 0,67`. Un Mago con
Catalizador lanzando: `× (1 + 0,15 + 0,15) × 1`. Un Arquero con la espada: `× 1 × 0,7`. Tres detalles:

- Los porcentajes de **daño cuerpo a cuerpo** son un modificador del atributo de ataque (`forja:clase_melee_damage`,
  ya estaban); el de **hechizos** se pone al lanzar (`Spellcasting`, `spellDamageMultiplier`); el de
  **proyectiles** en `dealt`. El factor va siempre en `dealt`, así que se multiplica con todos ellos.
- Donde un número viejo decía lo mismo que el factor, **se sustituyó** en vez de sumarse: el −10 % cuerpo a
  cuerpo del Mago y el −15 % del Curandero ya no están en su base.
- Como todo el sistema de combate, `dealt` solo corre con la revisión de combate activada (`CombatConfig.enabled`,
  lo normal); sin ella tampoco hay bonos de clase de ningún golpe.

### Tabla por clase (tras las decisiones A–F)

Vida: 20 de base (10 corazones). Estamina: 100 de base (`CombatConfig.staminaMax`). Sin talentos.

| Clase | Vida | Estamina máx. | Melé | Proyectil | Magia | Otros números de base |
|---|---|---|---|---|---|---|
| Guerrero | 22 (+10 %) | 120 (+20 %) | ×1 (+5 % de daño c/c) | ×1 | ×0,4 | regeneración de estamina +10 %, postura +15 %, parada +1 tick |
| Asesino | 14 (−30 %) | 130 (+30 %) | ×1 (+10 % de daño c/c) | ×1 | ×0,4 | velocidad +5 %, esquiva: distancia +35 %, espera −30 %, coste −20 %, invulnerabilidad +2 ticks |
| Tanque | 32 (+60 %) | 100 | ×0,67 | ×0,67 | ×0,67 | armadura +2, empuje +30 %, velocidad −12 %, distancia de esquiva −35 %, coste de esquiva +20 %, estamina al parar −25 % |
| Mago | 18 (−10 %) | 90 (−10 %) | ×0,7 | ×1 | ×1 (+15 % de hechizos) | espera de hechizos −15 %, maná +25 %, regeneración de maná ×6 (+500 %) |
| Curandero | 20 | 100 | ×0,5 | ×1 | ×1/3 (y cura 1/10 del daño entero a aliados) | curación +50 %, maná +15 %, regeneración de maná ×4 (+300 %), regeneración de estamina +10 % |
| Arquero | 18 (−10 %) | 100 | ×0,7 | ×1 (+15 % de proyectiles) | ×1 | velocidad +8 %, tensado +10 %, esquiva +20 %, espera de esquiva −15 %, caída −25 % |
| Herrero | 21 (+5 %) | 100 | ×1 | ×1 | ×1 | minado +15 %, ventana del golpe perfecto +0,01, potencial +5, reparación +25 % |

### Guerrero — cuerpo a cuerpo y aguante

**Base:** vida +10 %, daño cuerpo a cuerpo +5 %, estamina máxima +20 %, regeneración de estamina +10 %,
daño de postura +15 %, ventana de parada +1 tick. **Factor de daño:** magia ×0,4.
**Habilidad I — Grito de guerra (V):** recupera 40 de estamina; tú y los jugadores a 8 bloques ganáis Fuerza I
8 s. Espera 45 s.

| Rama | Nivel 1 (1 punto) | Nivel 2 (2) | Nivel 3 (3) |
|---|---|---|---|
| Aguante | Segundo aliento: regeneración de estamina +20 % | Piel curtida: vida +10 %, resistencia al empuje +10 % | Inquebrantable: por debajo del 30 % de vida, daño recibido −25 % |
| Guardia | Guardia alta: ventana de parada +2 ticks | Parada firme: estamina al parar con escudo −35 % | Réplica: una parada devuelve 15 de estamina y tu siguiente golpe en 3 s hace +40 % |
| Quebranto | Golpe pesado: daño de postura +20 % | Rompeguardias: +25 % de daño a monstruos aturdidos | Verdugo: remates +40 % |

**Habilidad II — Postura de hierro (B, 3 puntos, pide un nodo de nivel 2):** 6 s de Resistencia II y
Lentitud I. Espera 50 s.

### Asesino — «más stamina, más esquive, un poquito más de daño, bastante menos vida»

**Base:** vida −30 %, daño cuerpo a cuerpo +10 %, velocidad +5 %, estamina máxima +30 %, distancia de esquiva
+35 %, espera de esquiva −30 %, coste de esquiva −20 %, invulnerabilidad de la esquiva +2 ticks. **Factor de
daño:** magia ×0,4.
**Habilidad I — Paso sombrío (V):** 4 s de invisibilidad y Velocidad II; el siguiente golpe cuerpo a cuerpo en
esos 4 s hace +60 %. Espera 30 s.

| Rama | Nivel 1 | Nivel 2 | Nivel 3 |
|---|---|---|---|
| Sombra | Pies ligeros: coste de esquiva −20 % | Contraataque: contraataques +50 % | Danza: invulnerabilidad de esquiva +2 ticks, espera de esquiva −20 % |
| Filo | Puñalada: +40 % de daño por la espalda | Ejecutor: +30 % a enemigos por debajo del 35 % de vida | Golpe letal: matar cuerpo a cuerpo devuelve 25 de estamina y da Velocidad II 3 s |
| Sigilo | Paso quedo: velocidad agachado +30 % | Acróbata: daño de caída −40 %, salto +10 % | Evasión: 15 % de probabilidad de que un proyectil no te haga nada |

**Habilidad II — Marca de muerte (B):** el monstruo que miras (hasta 16 bloques) brilla 10 s y recibe +30 % de
tus golpes. Espera 40 s.

### Tanque — «mucha vida, stamina normal, menos movilidad, esquive más reducido, más lento»

**Base:** vida +60 %, armadura +2, resistencia al empuje +30 %, velocidad −12 %, distancia de esquiva −35 %,
coste de esquiva +20 %, estamina al parar con escudo −25 %. Estamina máxima: la normal. **Factor de daño:**
todo ×0,67 (cuerpo a cuerpo, proyectiles y magia).
**Habilidad I — Provocar (V):** los monstruos hostiles a 10 bloques te toman como objetivo; Resistencia I 6 s.
Espera 25 s.

| Rama | Nivel 1 | Nivel 2 | Nivel 3 |
|---|---|---|---|
| Muralla | Escudo pesado: estamina al parar −25 % | Represalia: quien golpea tu escudo recibe 3 de daño | Bastión: ventana de parada +3 ticks |
| Coraza | Piel de hierro: armadura +2 | Dureza: dureza de armadura +2, daño recibido −10 % | Coloso: vida +20 % |
| Firmeza | Recuperación: recuperas 1 de vida cada 5 s | Raíces: resistencia al empuje +30 %, estamina máxima +10 % | Último bastión: una vez cada 5 min, un golpe mortal te deja a 1 de vida con Resistencia III 3 s |

**Habilidad II — Baluarte (B):** 8 s de Resistencia II para ti y Resistencia I para los jugadores a 6 bloques.
Espera 60 s.

### Mago — magia

**Base:** vida −10 %, estamina máxima −10 %, daño de hechizos +15 %, espera de hechizos −15 %, maná máximo
+25 %, regeneración de maná ×6 (+500 %: sin clase mágica el maná vuelve lentísimo, ver *El maná por clase*). **Factor de daño:** cuerpo a cuerpo ×0,7
(sustituye el −10 % cuerpo a cuerpo de antes).
**Habilidad I — Nova arcana (V):** un anillo de 5 bloques a tu alrededor: 6 de daño mágico (con tu bono de
hechizos) a los monstruos hostiles, y los empuja. Espera 20 s.

| Rama | Nivel 1 | Nivel 2 | Nivel 3 |
|---|---|---|---|
| Arcano | Núcleo afinado: daño de hechizos +10 % | Sobrecarga arcana: la carga completa vale +25 % más | Catalizador: daño de hechizos +15 % |
| Flujo | Mente clara: espera −10 %, regeneración de maná +100 % (×7 en total) | Canalización: tiempo de carga −25 %, maná máximo +30 % | Economía arcana: coste de maná −25 %, espera −10 % |
| Égida | Barrera: daño mágico recibido −25 % | Paso etéreo: distancia de esquiva +20 %, espera de esquiva −10 % | Égida: cada 30 s ganas 4 de absorción |

**Habilidad II — Concentración (B):** 8 s con la espera de los hechizos a la mitad (y, con maná, sin coste).
Espera 60 s.

### Curandero — «cambia el daño de las armas mágicas por curación (sólo cura 1/10 parte del daño)»

**Regla de la clase (Andy, 2026-09-28 y 2026-09-29):** mientras eres Curandero, el proyectil del báculo y el área
y la runa del grimorio **curan** a los aliados que tocan (jugadores, tus animales domados, tu equipo) por **1/10
del daño entero** (`Healing.MAGIC_HEAL_SHARE = 0.1F`), y al Curandero un tercio de lo que curan a los demás
(`Healing.SELF_FROM_OTHERS`). **Y además dañan** a todo lo demás, como el de cualquiera, pero a **un tercio**
(`ClassDamage.CURANDERO_MAGIC`, que se pone donde el golpe llega; la décima de curación se calcula sobre el daño
entero, antes de ese tercio). El proyectil cura al primer aliado que toca y se deshace; si antes toca a otra
cosa, la daña y se deshace; nunca daña a un aliado. La runa cura a los aliados que hay dentro y daña al resto. Las
mejoras del arma (Filo, Vampirismo…) no entran en la curación.
**Base:** curación +50 %, maná máximo +15 %, regeneración de maná ×4 (+300 %), regeneración de estamina +10 %. **Factores de daño:**
cuerpo a cuerpo ×0,5 (sustituye el −15 % de antes), magia ×1/3.
**Habilidad I — Pulso sanador (V):** cura 4 (× tu curación) a ti y a los jugadores y tus animales a 8 bloques.
Espera 30 s.

| Rama | Nivel 1 | Nivel 2 | Nivel 3 |
|---|---|---|---|
| Sanación | Manos cálidas: curación +20 % | Renuevo: lo que curas recibe además Regeneración I 3 s | Milagro: curación +30 % |
| Amparo | Bendición: curar a alguien por debajo de la mitad de vida le da Resistencia I 4 s | Purificar: tus curas quitan Veneno, Marchitamiento, Debilidad y Lentitud | Vínculo: te curas el 25 % de lo que curas a otros |
| Fe | Serenidad: regeneración de maná +100 % (×5 en total), regeneración de estamina +15 % | Voluntad: vida +15 % | Aura: tú y tus aliados a 6 bloques recuperáis 0,5 de vida cada 3 s |

**Habilidad II — Resurgir (B):** el aliado que miras (hasta 16 bloques) recupera la mitad de la vida que le falta
(× tu curación) y Regeneración II 5 s. Espera 90 s.

### Arquero / cazador — arcos, ballestas, movilidad y esquive

**Base:** vida −10 %, velocidad +8 %, daño de proyectiles +15 %, tensado +10 % más rápido, distancia de esquiva
+20 %, espera de esquiva −15 %, daño de caída −25 %. **Factor de daño:** cuerpo a cuerpo ×0,7.
**Habilidad I — Salto atrás (V):** un salto hacia atrás de unos 6 bloques y Caída lenta 2 s. Espera 12 s.

| Rama | Nivel 1 | Nivel 2 | Nivel 3 |
|---|---|---|---|
| Puntería | Ojo de halcón: daño de proyectiles +10 % | Tiro a la cabeza: los tiros a la cabeza hacen +25 % | Tiro lejano: +2 % por bloque más allá de 10, hasta +40 % |
| Tensión | Mano rápida: tensado +15 % | Flecha veloz: tus flechas salen un 20 % más rápido | Tiro certero: un tiro a tensión completa deja Lentitud II 2 s |
| Viento | Zancada: velocidad +5 % | Rodar: coste de esquiva −25 %, invulnerabilidad +1 tick | Pluma: daño de caída −50 %, salto +15 % |

**Habilidad II — Lluvia de flechas (B):** 12 flechas caen durante 2 s en un círculo de 3 bloques donde miras
(hasta 32 bloques), 4 de daño cada una; no se pueden recoger. Espera 45 s.

### Herrero — la forja

**Base:** vida +5 %, velocidad de minado +15 %, ventana del golpe perfecto +0,01 (la base es 0,05 y cada nivel
de Maestría de herrero suma 0,01), potencial al forjar +5, cada lingote de reparación rinde +25 %.
**Habilidad I — Temple de campaña (V):** repara el 15 % de la durabilidad de la pieza forjada que llevas en la
mano y da Prisa minera II 10 s. Espera 60 s.

| Rama | Nivel 1 | Nivel 2 | Nivel 3 |
|---|---|---|---|
| Yunque | Pulso: ventana del golpe perfecto +0,01 | Golpe maestro: ventana +0,01, potencial +5 | Martillo de oro: ventana +0,02 |
| Crisol | Ojo de metal: potencial +5 | Mano firme: +1 al porcentaje de cada mejora que pones (como la Maestría de herrero) | Alma del metal: potencial +10 |
| Fragua | Remiendo: reparación +25 % | Brazo de herrero: +15 % de daño con martillo, mazo, pico, hacha y picahacha | Piel de fragua: daño de fuego y lava −50 %, quemadura −50 % |

**Habilidad II — Forja al rojo (B):** 10 s en que tus golpes cuerpo a cuerpo prenden fuego 4 s y hacen +20 %.
Espera 60 s.

## El Farol (arma de curación)

Andy: «añade un arma de curación específicamente». Es el **báculo de farol**, el modelo A3 que Andy vio junto a
la media luna (`docs/arma_magica/variantes_baculo.py`): un cayado del que cuelga una jaula con el núcleo dentro.

- **Piezas:** núcleo + cadena + mango (ninguna pieza nueva: la cadena es el gancho y la jaula). Se forja en
  cualquier mesa de forja, como las demás armas. El **núcleo decide el color y la fuerza**, como en el báculo.
- **Toque (clic derecho corto) — Bálsamo:** un rayo del color del núcleo hasta 16 bloques; cura al primer aliado
  que toca `2 + 0,75 × daño del núcleo` (núcleo de hierro: 3,5; de diamante: 4,25; de netherita: 5).
  **No cura a quien lo usa.**
- **Carga (mantener, 25 ticks) — Pulso:** un anillo de radio `4 + 2 × carga` alrededor tuyo; cura
  `(2,5 + 0,75 × daño del núcleo) × (1 + 0,5 × carga)` a cada aliado dentro, y **a ti la mitad**.
  Una carga por debajo de un tercio sale como Bálsamo.
- **Aliados:** jugadores, tus animales domados (lobos, gatos, loros, caballos...) y quien esté en tu equipo.
  **Nunca** a un monstruo hostil ni a animales ajenos.
- **Espera:** 30 ticks. Pose de carga y partículas del núcleo como el báculo y el grimorio.
- **Cualquiera puede usarlo.** El Curandero cura con él un 50 % más de base, y sus talentos de curación suben
  eso hasta +150 %; Renuevo, Bendición, Purificar y Vínculo también se aplican a sus curas.
- **Maná:** gasta maná de la barra de `magic/Mana`: un toque cuesta `Healing.MANA_COST` = 12 y una carga llena un
  25 % más, como el báculo; `ClassEffects.spellCostMultiplier` lo abarata. Sin maná bastante no sale.
- Como golpe es malo (0,5 de daño base, lento, contundente). Las mejoras de arma se le pueden poner, pero no
  afectan a la curación; las mejoras de magia (Conjuro veloz, Sobrecarga...) son solo de báculo y grimorio.

## Cómo encaja con lo que ya hay

- **Estamina (`combat/Stamina`)**: el máximo, la regeneración y el coste de golpes, saltos y cargas se
  multiplican por `ClassEffects.staminaMaxMultiplier / staminaRegenMultiplier / staminaCostMultiplier`. La barra
  del HUD usa el mismo máximo. *Fusión con el maná:* sus mejoras de estamina máxima deben multiplicarse con
  `ClassEffects.staminaMax(player)`, no sustituirlo.
- **Esquiva y embestida**: distancia (`dodgeDistanceMultiplier`, en el cliente, que es quien mueve), espera,
  coste e invulnerabilidad (en el servidor, con el mismo margen que ya había). *Fusión:* las mejoras de
  distancia de esquiva del maná se multiplican con `ClassEffects.dodgeDistanceMultiplier`.
- **Postura**: `ClassEffects.postureMultiplier` sobre el daño de postura de tus golpes.
- **Parada**: `ClassEffects.parryWindowBonus` ticks más de ventana con escudo forjado o arma; la parada perfecta
  sigue siendo el primer tercio de la ventana. Réplica y la experiencia del Guerrero se enganchan a la parada.
- **Calidad / golpe perfecto de la forja**: la ventana del Herrero se suma a la de la Maestría y Pulso firme,
  en `ForgeScreen.window()` (cliente, que es quien dibuja la barra).
- **Potencial / carga**: el Herrero suma potencial **al forjar** (`Potential.atForge`); la pieza lo guarda, así
  que cambiar de clase después no se lo quita. La carga (cuántas mejoras caben) sale del potencial como siempre.
- **Mejoras, pactos y sinergias**: las clases **no** cambian los topes por pieza (2 pactos, 3 sinergias
  despiertas, 100 % máximo). Mano firme suma como la Maestría de herrero, bajo los mismos topes.
- **Magia (`magic/Spellcasting`)**: daño (`spellDamageMultiplier`, al lanzar), espera (`spellCooldownMultiplier`)
  y carga (`spellChargeMultiplier`, `chargeBonusExtra`). El factor de magia de la clase (`ClassDamage`) va donde
  el hechizo llega (`ClassEffects.dealt`). La curación del Curandero vive en `magic/Healing`.
- **Daño por clase (`clase/ClassDamage`)**: los factores de la decisión F, en `ClassEffects.dealt`, multiplicados
  encima de todo lo demás (ver «Factores de daño por clase»).
- **Forja (`forge/Relic`)**: el Medallón del olvido es una *reliquia*: la estrella la reconoce por el conjunto de
  tipos de pieza, como a un `ForgeType` (`Assembler.evaluate`), pero no es equipo.
- **Dificultad**: `GearScore` suma `0,08 × nivel de clase / 15`: un jugador de clase alta ve monstruos algo más
  duros, como con mejor equipo. Las clases no tocan la presión ni la dificultad adaptativa.
- **Multijugador**: cada jugador tiene su clase; el adjunto se sincroniza solo con su dueño. Las curas y los
  gritos alcanzan a otros jugadores. Nada daña a otro jugador que no dañara ya.
- **Comandos (`/forja clase`)**: `elegir <clase>`, `nivel <n>`, `xp <n>`, `puntos` (desbloquea todo lo
  posible), `aprender <talento>`, `reiniciar`, `quitar`, `info`, `habilidad <1|2>` (sin espera).

## El maná por clase (Andy, 2026-09-30)

Andy: «el sistema actual es igual a no tener maná, se debe regenerar lentísimo si no tienes la clase». Sin clase
mágica el maná vuelve a 0,4 por segundo mientras lanzas y a 0,8 por segundo tras 5 s sin lanzar
(`CombatConfig.manaRegenPerTick` 0,02, `manaIdleRegenPerTick` 0,04, `manaIdleDelayTicks` 100): una barra vacía de
100 tarda unos dos minutos. La clase multiplica ese ritmo, y también lo que den las mejoras
(`Mana.regenFactor` = (1 + Flujo + eco + Meditación) × (1 + `MANA_REGEN` de la clase)).

| Clase | Multiplicador | Maná máx. | Lanzando (/s) | En calma (/s) | Barra vacía → llena |
|---|---|---|---|---|---|
| Sin clase mágica (las otras cinco) | ×1 | 100 | 0,4 | 0,8 | ~125 s |
| Mago | ×6 | 125 | 2,4 | 4,8 | ~26 s |
| Mago con Mente clara | ×7 | 125 (155 con Canalización) | 2,8 | 5,6 | ~22 s |
| Curandero | ×4 | 115 | 1,6 | 3,2 | ~36 s |
| Curandero con Serenidad | ×5 | 115 | 2,0 | 4,0 | ~29 s |

Las mejoras suman al ritmo base antes de la clase: Flujo +15 % por pieza (antes +25 %), el conjunto de eco +30 %
(antes +40 %) y Meditación en la mano +40 % (antes +60 %). Sin clase y con todo (cuatro piezas de Flujo, eco y
Meditación) se llega a ×2,3: 1,8 por segundo en calma, una barra en ~55 s, menos que un Mago sin nada.

Las muertes siguen igual (`killManaBase` 8 + 0,4 por punto de vida máxima de la víctima, hasta un 30 % de la
barra, y entrando como mucho un 3 % cada 5 ticks): un zombi vale 16, un enderman 24. Para quien no es mago son la
fuente de verdad: un zombi devuelve lo que cuestan dos proyectiles, veinte segundos de espera.

## Ganchos para la fusión con el maná

Integración con el maná (hecha al unir la rama, 2026-09-29):

| Gancho | Qué devuelve | Dónde se usa |
|---|---|---|
| `ClassEffects.manaMaxBonus(player)` | fracción a sumar al maná máximo (Mago +0,25, Canalización +0,30, Curandero +0,15) | `Mana.maxOf` |
| `ClassEffects.manaRegenBonus(player)` | fracción a sumar a la regeneración, que multiplica todo lo demás (Mago +5, Mente clara +1, Curandero +3, Serenidad +1) | `Mana.regenFactor` |
| `ClassEffects.spellCostMultiplier(player)` | multiplicador del coste (Economía arcana 0,75; Concentración 0) | `Spellcasting.tryCast` (báculo, grimorio y farol) |
| `ClassEffects.staminaMaxMultiplier(player)` | multiplicador de la estamina máxima | `Stamina.maxOf`, con Aguante |
| `ClassEffects.dodgeDistanceMultiplier(player)` | multiplicador de distancia | `CombatClient.tryDodge`, con Quiebro y Paso arcano |

## Decisiones de Andy

1. ~~¿La conversión del Curandero cura también al que lanza?~~ **Decidido por Andy (2026-09-29):** sí, un tercio
   de lo que cura a los demás (`Healing.SELF_FROM_OTHERS`). El farol sigue curando al que lo usa a la mitad con la
   carga. **Coste del farol:** 12 de maná por toque, confirmado.

**Decididas por Andy el 2026-09-29 (A–F):**

- **A. Cambiar de clase no conserva el nivel.** La clase nueva empieza en el nivel 1 con 0 de experiencia y sin
  talentos (`ClassProgress.choose`); el contador de cambios sigue. Elegir tu misma clase no es un cambio: solo
  reinicia los talentos y conserva el nivel.
- **B. El Curandero sí hace daño mágico, a un tercio, y cuerpo a cuerpo a la mitad.** Su proyectil, su área y su
  runa dañan a los enemigos ×1/3 y siguen curando a los aliados como antes (1/10 del daño entero, y al Curandero
  un tercio de eso). Cuerpo a cuerpo ×0,5 en lugar del −15 %.
- **C. El tope sigue en el nivel 15** (árbol de 21 puntos: nunca se tiene todo).
- **D. El objeto de cambiar de clase se forja con el sistema de forja** (piezas y mesa de forja), no con una
  receta. Es el **Medallón del olvido**: núcleo de eco + engaste + cadena en la estrella, un golpe de martillo.
  *Por qué una «reliquia» (`forge/Relic`) y no un `ForgeType` nuevo:* todo `ForgeType` es una pieza de equipo y el
  mod entero lo trata así (hoja de estadísticas, durabilidad, Maestría, mejoras, marcos de fundición, botín,
  leyendas, las recetas de la guía, y una docena de contratos de las pruebas que recorren todos los tipos). El
  medallón se hace igual —piezas cortadas en la mesa de piezas, la estrella de una mesa de forja, el golpe
  cronometrado— y nada más del equipo le aplica. La receta de mesa de trabajo del Emblema del olvido se borró; el
  emblema viejo sigue funcionando para quien ya lo tenga.
- **E. Teclas:** K (árbol / elegir), V (habilidad I) y B (habilidad II), aprobadas; son `KeyMapping` en «Forja:
  clases» y se cambian en Controles (ver «Elegir y cambiar de clase»).
- **F. Factores de daño por clase:** Arquero y Mago cuerpo a cuerpo ×0,7; Guerrero y Asesino magia ×0,4; Tanque
  todo ×0,67; Curandero magia ×1/3 y cuerpo a cuerpo ×0,5. En `clase/ClassDamage`, aplicados en
  `ClassEffects.dealt` (ver «Factores de daño por clase» y la tabla por clase).

Pendiente de nada: elegir la primera vez sigue siendo gratis desde la guía o con la tecla del árbol.
