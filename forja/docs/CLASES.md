# Clases — diseño

Lo que pidió Andy (2026-09-28), lo que decidí donde había hueco y todos los números. **Los números viven en
el código** (`clase/PlayerClass.java`, `clase/Talent.java`, `clase/ActiveSkill.java`, `clase/ClassProgress.java`
y `magic/Healing.java`); las pantallas y la guía los leen de ahí, así que este documento es la foto del día en
que se escribió.

## Lo que pidió Andy

- **Clase elegida + árbol**: eliges una clase, sube de nivel con experiencia y cada nivel da puntos para el
  árbol de talentos de esa clase.
- **Cambiar de clase: sí, con un costo** (un objeto caro o un ritual); los puntos se reinician.
- **Asesino**: «más stamina, más esquive, un poquito más de daño, bastante menos vida».
- **Tanque**: «mucha vida, stamina normal, menos movilidad, esquive más reducido, más lento».
- **Curandero**: «cambia el daño de las armas mágicas por curación (sólo cura 1/10 parte del daño)».
- **Guerrero, Mago, Arquero/cazador, Herrero**: diseño mío, en el espíritu del mod.
- *(después)* «añade un arma de curación específicamente»: el **Farol** (más abajo).

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

- **Elegir la primera vez: gratis, desde la guía o con la tecla K.** La guía se entrega al entrar por primera
  vez y se abre con G, así que todo jugador la tiene; el capítulo «Clases» tiene un botón «Elegir clase». K
  abre la elección si no tienes clase y el árbol si ya la tienes. Al entrar sin clase, un aviso en el chat lo
  recuerda. *Por qué no un bloque ni un objeto nuevo:* un bloque obliga a construir antes de jugar la clase, y un
  objeto de elección sería un objeto más en el inventario que solo sirve una vez. La guía ya está.
- La pantalla de elección muestra las siete clases con su descripción, sus números base y sus dos habilidades.
- **Cambiar: el Emblema del olvido** (objeto nuevo, no se apila). Clic derecho abre la misma pantalla en modo
  «cambio»; al confirmar se gasta el emblema. Vale también para elegir **la misma** clase: es un reinicio de
  puntos.
  - Receta (con forma): `oro · lágrima de ghast · oro` / `diamante · fragmento de eco · diamante` /
    `oro · fragmento de amatista · oro`. El fragmento de eco (ciudades antiguas) es lo que lo hace caro.
  - Al cambiar: **se conserva el nivel y la experiencia**; los talentos se borran y **todos los puntos vuelven**;
    las esperas de las habilidades se reinician; los atributos de la clase vieja se quitan.
- En creativo el emblema no se gasta. `/forja clase ...` (operadores) hace todo sin coste.

## Las siete clases

Notación: vida, velocidad y daño son porcentajes sobre el valor base; «espera» es el tiempo entre esquivas;
«ventana de parada» son ticks (20 = 1 s).

### Guerrero — cuerpo a cuerpo y aguante

**Base:** vida +10 %, daño cuerpo a cuerpo +5 %, estamina máxima +20 %, regeneración de estamina +10 %,
daño de postura +15 %, ventana de parada +1 tick.
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
+35 %, espera de esquiva −30 %, coste de esquiva −20 %, invulnerabilidad de la esquiva +2 ticks.
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
coste de esquiva +20 %, estamina al parar con escudo −25 %. Estamina máxima: la normal.
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

**Base:** vida −10 %, estamina máxima −10 %, daño cuerpo a cuerpo −10 %, daño de hechizos +15 %, espera de
hechizos −15 %, maná máximo +25 % *(gancho de maná)*, regeneración de maná +20 % *(gancho)*.
**Habilidad I — Nova arcana (V):** un anillo de 5 bloques a tu alrededor: 6 de daño mágico (con tu bono de
hechizos) a los monstruos hostiles, y los empuja. Espera 20 s.

| Rama | Nivel 1 | Nivel 2 | Nivel 3 |
|---|---|---|---|
| Arcano | Núcleo afinado: daño de hechizos +10 % | Sobrecarga arcana: la carga completa vale +25 % más | Catalizador: daño de hechizos +15 % |
| Flujo | Mente clara: espera −10 %, regeneración de maná +25 % | Canalización: tiempo de carga −25 %, maná máximo +30 % | Economía arcana: coste de maná −25 %, espera −10 % |
| Égida | Barrera: daño mágico recibido −25 % | Paso etéreo: distancia de esquiva +20 %, espera de esquiva −10 % | Égida: cada 30 s ganas 4 de absorción |

**Habilidad II — Concentración (B):** 8 s con la espera de los hechizos a la mitad (y, con maná, sin coste).
Espera 60 s.

### Curandero — «cambia el daño de las armas mágicas por curación (sólo cura 1/10 parte del daño)»

**Regla de la clase (literal de Andy):** mientras eres Curandero, el proyectil del báculo y el área y la runa del
grimorio **curan** a los jugadores y a tus animales domados que tocan, por **1/10 del daño que harían**
(`Healing.MAGIC_HEAL_SHARE = 0.1F`), y **no dañan a los monstruos**: el proyectil los atraviesa y la runa los
ignora. Las mejoras del arma (Filo, Vampirismo…) no entran en esa curación.
**Base:** daño cuerpo a cuerpo −15 %, curación +50 %, maná máximo +15 % *(gancho)*, regeneración de estamina
+10 %.
**Habilidad I — Pulso sanador (V):** cura 4 (× tu curación) a ti y a los jugadores y tus animales a 8 bloques.
Espera 30 s.

| Rama | Nivel 1 | Nivel 2 | Nivel 3 |
|---|---|---|---|
| Sanación | Manos cálidas: curación +20 % | Renuevo: lo que curas recibe además Regeneración I 3 s | Milagro: curación +30 % |
| Amparo | Bendición: curar a alguien por debajo de la mitad de vida le da Resistencia I 4 s | Purificar: tus curas quitan Veneno, Marchitamiento, Debilidad y Lentitud | Vínculo: te curas el 25 % de lo que curas a otros |
| Fe | Serenidad: regeneración de maná +25 %, regeneración de estamina +15 % | Voluntad: vida +15 % | Aura: tú y tus aliados a 6 bloques recuperáis 0,5 de vida cada 3 s |

**Habilidad II — Resurgir (B):** el aliado que miras (hasta 16 bloques) recupera la mitad de la vida que le falta
(× tu curación) y Regeneración II 5 s. Espera 90 s.

### Arquero / cazador — arcos, ballestas, movilidad y esquive

**Base:** vida −10 %, velocidad +8 %, daño de proyectiles +15 %, tensado +10 % más rápido, distancia de esquiva
+20 %, espera de esquiva −15 %, daño de caída −25 %.
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
- **Magia (`magic/Spellcasting`)**: daño (`spellDamageMultiplier`), espera (`spellCooldownMultiplier`) y carga
  (`spellChargeMultiplier`, `chargeBonusExtra`). La conversión del Curandero vive en `magic/Healing`.
- **Dificultad**: `GearScore` suma `0,08 × nivel de clase / 15`: un jugador de clase alta ve monstruos algo más
  duros, como con mejor equipo. Las clases no tocan la presión ni la dificultad adaptativa.
- **Multijugador**: cada jugador tiene su clase; el adjunto se sincroniza solo con su dueño. Las curas y los
  gritos alcanzan a otros jugadores. Nada daña a otro jugador que no dañara ya.
- **Comandos (`/forja clase`)**: `elegir <clase>`, `nivel <n>`, `xp <n>`, `puntos` (desbloquea todo lo
  posible), `aprender <talento>`, `reiniciar`, `quitar`, `info`, `habilidad <1|2>` (sin espera).

## Ganchos para la fusión con el maná

Integración con el maná (hecha al unir la rama, 2026-09-29):

| Gancho | Qué devuelve | Dónde se usa |
|---|---|---|
| `ClassEffects.manaMaxBonus(player)` | fracción a sumar al maná máximo (Mago +0,25, Canalización +0,30, Curandero +0,15) | `Mana.maxOf` |
| `ClassEffects.manaRegenBonus(player)` | fracción a sumar a la regeneración (Mago +0,20, Mente clara +0,25, Serenidad +0,25) | `Mana.regenFactor` |
| `ClassEffects.spellCostMultiplier(player)` | multiplicador del coste (Economía arcana 0,75; Concentración 0) | `Spellcasting.tryCast` (báculo, grimorio y farol) |
| `ClassEffects.staminaMaxMultiplier(player)` | multiplicador de la estamina máxima | `Stamina.maxOf`, con Aguante |
| `ClassEffects.dodgeDistanceMultiplier(player)` | multiplicador de distancia | `CombatClient.tryDodge`, con Quiebro y Paso arcano |

## Decisiones que Andy debería confirmar

1. **¿La conversión del Curandero cura también al que lanza?** Hoy **no**: el proyectil y la runa curan a otros.
   El farol sí cura al que lo usa, pero solo con la carga y a la mitad.
2. Al cambiar de clase **se conserva el nivel** y vuelven todos los puntos. La alternativa es empezar de nuevo
   en el nivel 1.
3. El Curandero **no puede dañar con magia** (ni báculo ni grimorio). Sí puede pegar cuerpo a cuerpo (−15 %).
4. Tope 15 y árbol de 21 puntos: nunca se tiene todo.
5. Elegir la primera vez es gratis desde la guía o la tecla K; el Emblema del olvido cuesta un fragmento de eco.
6. Teclas por defecto: **K** árbol / elección, **V** habilidad I, **B** habilidad II (se cambian en Controles).
