# Propuestas de IA para el mod, medidas en el simulador (29-09-2026, noche)

Escrito por la sesión del simulador ("Cómo funcionan los LLM") para la sesión del mod. Responde al pedido de Andy de
TRASPASO_2026-09-29.md, "IA de los monstruos" (A creeper, B rodear, C esqueletos y línea de tiro, D correr, amontonarse y
subgrupos, E grupos al aparecer). Todo lo de aquí son PROPUESTAS: el mod no se ha tocado. Los números salen del simulador
(motor Rust que copia RuleBrain, Squad, TacticGoal, MobSprint, SwellGoalMixin, RangedBowAttackGoalMixin y HopBack tal como
estaban en forja-ia-armas hasta d50bdd0/a07dc7c) con la red que se entrena ahora (forja_mobs_v3s, iteración ~27 350;
la que Andy probó en el juego es la 24 900 de la misma rama).

Cómo se midió: `combate/forja_exp/ia_noche/diag_ia.py` (en el repo del simulador), 128 combates por escenario y modo
(reglas del mod / red), jugador de reglas "bueno", kits y dificultades al azar, 1 200 ticks. Escenarios de tamaño fijo:
creeper solo, creeper + 2 zombis, 3/5/8/13 zombis, 2 esqueletos + 3 zombis, mixtos de 8 (4 zombis, 2 esqueletos, creeper,
araña) y 13 (6 zombis, 3 esqueletos, 2 creepers, 2 arañas). Dos formas de aparecer: "repartidos" (lo que hacía siempre el
simulador: cada mob en un punto al azar del mapa) y "agrupados" (como `Scaling.pack`: todo el grupo junto, a 14-18 bloques,
en ±20° de un lado). **En el juego aparecen agrupados**, y casi todo sale peor así.

## 1. Lo medido (red = la que se entrena; reglas = RuleBrain del mod)

### A) Creeper: la red casi no explota en grupos grandes
| agrupados, n = 128 | combates con explosión | a < 3 → mecha | a < 3 → explosión | a < 3 sin encender (pasos) | nunca llega a < 3 | daño de la explosión |
|---|---|---|---|---|---|---|
| creeper + 2 zombis, reglas | 80 % | 6 ticks | 60 ticks | 60 % | 19 % | 4,4 |
| creeper + 2 zombis, red | 63 % | **77 ticks** | 104 ticks | 68 % | 20 % | 7,1 |
| mixto 8, reglas | 88 % | 3 | 61 | 24 % | 8 % | 4,0 |
| mixto 8, red | **11 %** | 70 | 96 | 83 % | 49 % | 4,2 |
| mixto 13, reglas | 89 % | 3 | 52 | 20 % | 12 % | 5,8 |
| mixto 13, red | **12 %** | 36 | 60 | 87 % | 53 % | 12,4 |

(Repartidos, lo mismo: red 11-12 % de combates con explosión en los mixtos frente a 84-89 % de las reglas.) Mechas que se
apagan solas: reglas 30-50 % en grupo y 91 % con el creeper solo (el jugador huye más de 7 bloques o es la finta del 35 %);
la red casi nunca aborta: cuando enciende es porque está segura, y entonces hace más daño (7-12 frente a 4-6).
Por qué la red duda (es del entrenamiento, no del mod): castigo de −3 por explotar sin dañar (el jugador de reglas se
aparta en cuanto oye la mecha: el 40-85 % de las explosiones de las reglas no hacen daño) y el premio de equipo, que el
creeper deja de cobrar en cuanto explota. Aprendió "no explotar salvo con el jugador acorralado". Se corrige en el
simulador (ver §6), pero el mod puede ayudar mucho (§3).

### B) Rodear: poco, y peor cuando llegan agrupados
% del tiempo "rodeado" (≥ 3 a < 6 y mayor hueco < 150°), agrupados / repartidos:
| | 3 zombis | 5 | 8 | 13 | mixto 13 |
|---|---|---|---|---|---|
| reglas | 4,9 / 4,9 | 5,0 / 8,9 | 11,2 / 12,7 | 14,9 / 22,2 | 7,6 / 22,9 |
| red | 7,7 / 10,5 | 13,4 / 14,6 | 17,6 / 24,9 | 19,5 / 24,9 | 18,5 / 38,1 |

**La causa principal está en el Squad**: el reparto de huecos se rehace cada 10 ticks empezando por el ángulo del mob más
cercano (`start = angle(members.get(0))`), así que cuando cambia quién está más cerca, TODO el patrón gira y los huecos
saltan. Medido (8 y 13 zombis, agrupados): el hueco de cada mob cambia más de 45° **4,4-5,1 veces cada 100 ticks** (uno de
cada dos repartos). El error medio entre el ángulo de un mob y el de su hueco es 70-78°, y el 60 % del tiempo está a más de
50° de él. Con 13 zombis: tácticas de las reglas acercarse 37 %, rodear 24 %, esperar 15 %, flanquear 12 %.

### C) Esqueletos: nunca disparan a través de un aliado, pero tampoco buscan línea
El simulador copia `Squad.allyInLine` en las reglas (RangedBowAttackGoalMixin) y en la red (TacticGoal.bow): ninguno
suelta la flecha con un aliado en la línea (lo que medimos "con aliado" es 1-11 %, error de nuestra geometría a posteriori).
El problema es lo que hacen mientras tanto (agrupados):
| | línea tapada (tiempo) | disparos por combate | arco destensado sin disparar | esperas con el arco tenso | espera media | se mueve de lado > 0,75 en la espera |
|---|---|---|---|---|---|---|
| 2 esq. + 3 zombis, reglas | 32 % | 8,5 | 0,5 | 589 | 26 ticks | 33 % |
| 2 esq. + 3 zombis, red | 32 % | 4,0 | 1,8 | 309 | 9 | 17 % |
| mixto 13, reglas | 67 % | 6,5 | 1,1 | 860 | 33 | 34 % |
| mixto 13, red | 71 % | 2,4 | **3,6** (88 % con la línea tapada) | 650 | 10 | 18 % |

Las reglas aguantan el arco tenso (hasta 33 ticks) y solo se mueven de lado cuando su zigzag vanilla les toca (cada 30-60
ticks cambia de lado); la red suelta el arco y vuelve a empezar. Con 13 mobs la línea está tapada el 70 % del tiempo.

### D) Correr, amontonarse, subgrupos
| agrupados | corre (% de pasos, cuerpo) | a > 50° de su hueco y corriendo | vecino más cercano | mobs con otro a < 1,5 | delante / lados / detrás (respecto a la mirada) | cuadrantes ocupados (de 4) |
|---|---|---|---|---|---|---|
| 8 zombis, reglas | 21 % | 33 % | 1,94 | 49 % | 55 / 37 / 8 % | 2,5 |
| 8 zombis, red | 21 % | 27 % | 1,90 | 48 % | 51 / 39 / 10 % | 2,6 |
| 13 zombis, reglas | 21 % | 32 % | 1,44 | **67 %** | 55 / 37 / 8 % | 2,6 |
| 13 zombis, red | 21 % | 28 % | 1,41 | **67 %** | 52 / 39 / 9 % | 2,7 |

(El ideal de Andy para 13 = 4 delante, 3 + 3 a los lados, 3 detrás = 31 / 46 / 23 %.) La carrera está casi al máximo que
permite la estamina (40 ticks de carrera, luego 20 de descanso + recuperar: ~25-30 % del tiempo como mucho), pero se gasta
persiguiendo, no yendo al hueco: de los que están lejos de su hueco solo corre un 30 %, porque la regla (b) de MobSprint solo
mira RODEAR/ESPERAR y el 40-47 % del tiempo están en ACERCARSE.

## 2. Propuestas sin cambiar el contrato (Squad, RuleBrain, TacticGoal, MobSprint): lo más rentable

### 2.1 Huecos ESTABLES en el Squad (B, D) — la más importante
Hoy `Squad.assign` reparte n huecos desde el ángulo del más cercano cada 10 ticks. Propuesta:
- Guardar en el `MobMind` el hueco (`slotAngle`) y el tamaño del grupo con que se repartió (`slotN`).
- En `assign`: si todos los miembros tienen `slotN == n`, **no tocar nada** (solo el aliado aturdido sigue cambiando
  `ringAngle` para ese mob, sin tocar `slotAngle`).
- Si el grupo cambia de tamaño (muere uno, llega otro): el patrón nuevo de n huecos se ANCLA en el `slotAngle` que tenía el
  más cercano (no en su ángulo actual), cada uno de los que ya tenían hueco toma el libre más cercano al suyo (por orden de
  distancia) y los nuevos entran como hoy (desde el ancla hacia los lados, el de su lado).
- Sin nadie con hueco (grupo nuevo): exactamente el reparto de hoy.
El código del simulador que lo hace está escrito (`combate/forja_exp/ia_noche/parche_rust_propuestas.diff`, función
`huecos_estables`), pero NO se pudo compilar ni medir esta noche (Smart App Control de Windows bloquea ya el compilador de
Rust, `rustc_driver-*.dll`, ver §6). Esperable: el error al hueco baja mucho, y con él el amontonamiento.

### 2.2 Subgrupos por sectores para grupos grandes (D: "con 13, 4 delante, 3 por lado, 3 detrás")
Encima de 2.1 (con 2.1 los n huecos a 2π/n ya dan ~31/46/23 % si se respetan). Para que se note en el juego:
- **Frente estable**: el "delante" no es la mirada instantánea del jugador (gira 45-60°/tick y cambia todo), sino la
  dirección de llegada del grupo, o una media lenta de la mirada (p. ej. media exponencial con 1 s).
- **Cupos por sector** (n ≥ 6): delante round(0,3·n), detrás round(0,23·n), el resto a los dos lados (13 → 4 / 3+3 / 3;
  8 → 2 / 2+2 / 2; 6 → 2 / 1+1 / 2... con detrás ≥ 1). Los huecos de cada sector repartidos dentro de su arco.
- **Dos anillos**: los que tienen turno (máx. 2) atacan desde el de 3,5; los demás esperan en el de fuera (5-6,5) EN SU
  SECTOR. El sector de detrás espera más cerca (4,5) para entrar en cuanto haya turno (pinza real).
- **Quién va detrás**: los más rápidos o los que ya están más a la espalda; las arañas primero; nunca los arqueros.
- **Ir al sector por fuera**: `toRing` con radio r + 3 mientras está a > 90° de su hueco, para no cruzar por el alcance del
  jugador (hoy va a r + 1,5 como mucho).
- Turnos: ceder el turno al del sector de detrás cuando esté listo (hoy da igual quién lo coja).

### 2.3 Correr para llegar al hueco (D)
- MobSprint regla (b): también en ACERCARSE cuando RuleBrain "da la vuelta" (d50bdd0: 2,5 < d < 8 y > 60° de su hueco),
  y en FLANQUEAR. Hoy solo RODEAR y ESPERAR.
- Carrera barata para recolocarse: COST 1/tick (en vez de 2) mientras va a su hueco (a > 50°), y 2 para perseguir. O bien
  guardar un 30 % de la estamina (no perseguir por debajo de 30) para recolocarse.
- Que se vea: hoy es el sprint vanilla (partículas) + 35 %: en un grupo de 13 que se mueve a la vez no se distingue.

### 2.4 Esqueletos que buscan línea de tiro (C)
En `RangedBowAttackGoalMixin.forja$chargedDraw` (reglas) y en `TacticGoal.bow` (red), cuando el arco está tenso y
`Squad.allyInLine`:
- Probar 2 puntos a 2 bloques de lado (perpendicular a la línea al jugador) y 1 a 2 bloques hacia atrás; ir (velocidad 1,0,
  sin destensar) al primero desde el que `allyInLineOf` sea null y lo vea (`ObsM1.sees`). Guardar el punto 20 ticks.
- Si ninguno sirve: ir hacia el lado del grupo con menos mobs a 8-12 bloques del jugador ("detrás de los zombis pero en
  diagonal"), o subir (MovementGoals.HighGround sin la condición de 100 ticks).
- Con red: hacerlo en el ejecutor (TacticGoal.bow) mientras la red pida "usar"; la red no tiene que aprenderlo. Así además
  deja de soltar el arco (hoy la red destensa 3,6 veces por combate en el mixto de 13, casi siempre con la línea tapada).
- Squad: el arquero con COBERTURA debería tener su propio anillo (8-12) en un sector libre de su lado, no el de 3,5.

### 2.5 Creeper más decidido (A)
Reglas y ejecutor (sin tocar el contrato):
- **No pararse del todo con la mecha**: hoy SwellGoal (vanilla) y TacticGoal.move paran la navegación en cuanto se hincha,
  así que un jugador que se aparta 7 bloques en 1,5 s la apaga siempre (creeper solo: 91 % de mechas apagadas, 15 % de
  combates con explosión). Propuesta Forja: con la mecha encendida sigue hacia el jugador a 0,5 de velocidad (también
  escrito en el parche del simulador como `creeper_anda`, sin medir por lo del compilador).
- **Compromiso**: con la mecha ≥ 15 ticks, solo se apaga a > 9 bloques (no a 7).
- **Carrera para entrar**: MobSprint para el creeper a 3-10 bloques acercándose (no solo cuando el jugador se aleja).
- **Entrar cuando el jugador está ocupado** (RuleBrain, hoy el creeper siempre es APPROACH): con ≥ 3 aliados, esperar a
  6-8 bloques detrás de un aliado (OCULTARSE) hasta que el jugador esté enzarzado (≥ 2 aliados a < 3,5, o bloqueando /
  atacando / comiendo: `jug_ocupado`) o de espaldas, y entonces correr y encender. Es lo que Andy espera de un creeper
  en grupo y hoy ni las reglas ni la red lo hacen.
- Finta de la mecha (35 %): mantenerla solo con el creeper solo o en dificultades altas; en grupo le quita eficacia.

### 2.6 Roles por tipo en los grupos mixtos
Con 2.2, el Squad debería repartir por tipo, no solo por distancia:
- Zombis (y demás cuerpo a cuerpo con escudo): sectores de delante y lados, los del escudo delante.
- Arañas: sector de detrás (saltan y trepan; ya tienen el premio de espalda en la red).
- Esqueletos: anillo exterior 8-12, en el sector con menos aliados entre ellos y el jugador (ver 2.4).
- Creeper: fuera del anillo hasta que el jugador esté rodeado u ocupado (2.5), y entonces por el hueco más grande.

## 3. Grupos al aparecer (E): mixtos y tope de élites
Hoy `Scaling.pack` solo crea compañeros del mismo tipo (`mob.getType().create`), y cada compañero pasa luego por
`sizeUp` (ENTITY_LOAD) y **tira su propia amenaza**: un grupo de veterano de 6 puede traer más élites; y como cada
aparición natural vanilla (hasta 4 zombis) lleva su propio grupo, los grupos se suman hasta `packCrowd` = 12.
Propuestas:
- **Compañeros mixtos**: tabla por tipo del que aparece (noche, superficie): zombi → 60 % zombi, 20 % esqueleto, 10 % araña,
  10 % creeper; esqueleto → 50 % esqueleto, 40 % zombi, 10 % araña; araña → 50 % araña, 30 % zombi, 20 % esqueleto;
  creeper → nunca más de 1 creeper por grupo (el resto zombis). Variantes de bioma (husk, stray) como las elige vanilla.
- **Una amenaza por grupo**: marcar a los compañeros (`forja_companero`) y que `sizeUp` no les tire amenaza (NORMAL);
  como mucho 1 élite y 2 veteranos por grupo, y 1 élite a < 32 bloques salvo eventos.
- **Grupos que no se suman**: si hubo un grupo natural a < 24 bloques en los últimos 30 s, el siguiente viene sin
  compañeros; `packCrowd` por dificultad (Aprendiz 6, Herrero 8, Maestro 10, Leyenda 12).
- Tamaño: 13 de golpe solo en eventos/asedios; con 2.2 un grupo de 8-13 ya es muy peligroso (en el simulador, con 13
  zombis el jugador "bueno" muere en el 92-100 % de los combates).
El simulador ya puede entrenar con grupos agrupados, grandes (8-13) y mixtos (§6); si el mod cambia la composición,
copiaremos la tabla.

## 4. Contrato v4 (solo si hace falta; propuesta en `red_mob_v4_contrato.propuesta`)
Casi todo lo de §2 es de reglas/ejecutor y no rompe las redes: hacerlo primero. Si después se quiere que la red lo
decida, el v4 añadiría AL FINAL (280 → 296 entradas; 34 → 36 salidas) lo que hoy la red no ve:
- su sector asignado (delante/izquierda/derecha/detrás, one-hot 4), error al hueco (signed/π) y "hueco estable desde" /100;
- mobs a < 1,5 de mí (/3) y aliados de mi sector (/4);
- jugador rodeado (cuadrantes ocupados a < 6, /4) y enzarzado (aliados a < 3,5, /4): lo que necesita el creeper;
- creeper aliado encendido cerca (dist/7) para apartarse;
- arquero: lado libre para tirar (−1/0/+1) y distancia a ese punto (/4);
- tamaño del grupo (/13).
Salidas nuevas (tácticas 13 y 14 en la misma softmax): SECTOR (ir a su hueco por fuera, corriendo) y TIRO_LIBRE
(arquero: al punto con línea libre). Migración: ceros en la 1.ª capa para las 16 entradas y sesgo −3 en los 2 logits,
como se hizo de v2 a v3.

## 5. Orden recomendado
1. 2.1 huecos estables (poco código, arregla B y buena parte de D) + 2.5 creeper (compromiso y andar con la mecha).
2. 2.4 esqueletos buscan línea (ejecutor, vale para reglas y red).
3. 3. grupos mixtos y tope de élites (E).
4. 2.2/2.6 sectores y roles por tipo; 2.3 carrera para recolocarse.
5. Solo si hace falta: contrato v4.
Cada cambio, pasarlo al simulador para copiarlo y medirlo (lo copiamos y reentrenamos).

## 6. Lo que se hace en el simulador/entrenamiento (sin el mod), medido esta noche
Nuevo en `entrenar_forja.py` (todo apagado por defecto): premios `creeper_cerca` (castigo por estar a < 3 viéndolo sin
encender), `creeper_bum` (× daño de la explosión), `equipo_creeper` (× premio de equipo del creeper), `--castigo-bum-vacio`
(el −3), `arquero_tapado`, `amontonar`, `hueco`, `correr_hueco`; `--agrupar` (el grupo aparece junto, como Scaling.pack),
`--p-grandes/--grandes 8-13`, escenario mixto `horda_mixta` (5-13 zombis/esqueletos/creepers/arañas) e `--imitacion-fam
creeper:0.3`. Experimentos cortos en la RTX 2060 desde una copia de la red, 150 min, contra un control con los premios de
siempre (x0). Medidos con 128 combates por escenario (agrupados; entre paréntesis, repartidos):
| | control x0 | x1: todo (formación suave) | x5: formación y arqueros ×3 |
|---|---|---|---|
| creeper, mixto 13: combates con explosión | 19 % (19 %) | **80 %** (82 %) | **83 %** (78 %) |
| creeper, mixto 8 | 20 % (26 %) | **76 %** (76 %) | 75 % (79 %) |
| creeper: a < 3 → mecha (ticks) | 37-78 | **3-7** | 3-9 |
| 13 zombis: % rodeado | 12,4 (23,9) | 15,5 (25,6) | **24,5 (34,2)** |
| 13 zombis: mobs con otro a < 1,5 | 71 % (62) | 64 % (57) | **51 % (43)** |
| mixto 13: línea de tiro tapada / arcos destensados por combate | 70 % / 3,6 | 60 % / 2,9 | **51 % / 2,5** |
| mixto 13: daño por minuto al jugador | 135 (178) | 145 (**204**) | 143 (184) |
| familias, grupos de 4, jugador bueno: daño/min | 62,0 | 59,6 | 55,9 |
Conclusiones: el creeper se arregla del todo en el simulador (x1 y x5). Rodear y no amontonarse mejora con los premios
fuertes (x5), pero a costa de ~10 % de daño/min en grupos de zombis solos: sin 2.1/2.2 del mod, la red solo puede rodear
"a mano" y lo paga. Los esqueletos mejoran poco (siguen sin moverse de lado: 11-14 %): hace falta 2.4 en el ejecutor.
Lo que queda de verdad del lado del mod: huecos estables y sectores (2.1/2.2), tiro libre en el ejecutor (2.4), creeper que
no se para con la mecha (2.5) y grupos mixtos con tope de élites (3).

## 7. Blaze y peleas mob contra mob (decisiones de Andy, 29-09)

### 7.1 Blaze con red propia (`red_blaze.json`)
Lo que el simulador necesita (en el motor de Rust, carpeta `combate/forja/`), cuando el documento del blaze de la nube esté listo:
- **Vuelo con altura preferida**: el blaze flota entre 2 y 5 bloques sobre el suelo, sube y baja despacio (sin gravedad normal), y cae poco a poco si no tiene nada debajo. En las observaciones hace falta su altura sobre el suelo y la del jugador.
- **Ráfaga de 3 bolas de fuego**: carga (aviso visible), 3 disparos seguidos y una pausa larga. La red decide cuándo cargar y hacia dónde apuntar (con adelanto al movimiento del jugador). Las bolas se pueden desviar con un golpe, como en vanilla.
- **Inmune al fuego y la lava**; le hacen daño el agua y las bolas de nieve.
- **Contrato propio** (`red_blaze_contrato.json`, v1): no cabe en `red_mob_v3` sin romperlo. Tendría las mismas observaciones de jugador y aliados que v3 y además altura, carga de la ráfaga y bolas en vuelo. Las salidas serían moverse en 3D (subir y bajar), cargar, disparar y retirarse.
- **Premios**: acertar bolas, mantener distancia 8-14, no quedar a tiro de espada, cubrir a los aliados de tierra (quemar al jugador mientras lo rodean).

**Bloqueo actual**: el Control inteligente de aplicaciones de Windows no deja compilar el motor de Rust desde el 29-09 a las 02:00 (bloquea los `build-script-build` nuevos). Hasta que Andy lo resuelva en su PC (desactivarlo o añadir una excepción es decisión suya), no se pueden añadir mecánicas nuevas al simulador; los entrenamientos que ya corren siguen.

### 7.2 Mob contra mob (gólem, lobos, otro mob): ¿vale la pena?
**Sí, pero con prioridad baja** y sin tocar `red_mob_v3`:
- Hoy las redes solo han visto jugadores. Contra un gólem o unos lobos, las REGLAS del mod lo hacen bien, y lo que el jugador ve de esas peleas es poco.
- Donde sí ganaría: el mob **no debería perder la pista del jugador** por pelearse con un lobo o un gólem. Eso se puede arreglar en el `Squad` sin red nueva: el lobo y el gólem son estorbos, y el objetivo sigue siendo el jugador.
- Si más adelante se quiere, se haría así: escenarios en el simulador con un gólem (mucha vida, golpe que lanza hacia arriba, lento) y 1-3 lobos (rápidos, poca vida) como "enemigos no jugadores". La observación del objetivo (la misma que la del jugador) pasa a ser "el enemigo más cercano", con un indicador de 1 bit de si es jugador. Eso sería un contrato v4 (ya hay hueco en `red_mob_v4_contrato.propuesta`).
- **Recomendación**: primero el blaze y los voladores. Mob contra mob, después de v4.
