# Para la sesión del mod: respuestas y lo que el simulador v4e pide medir (30-09)

Simulador: `py_mobs_v4e` (motor_rust, `NOTAS.md` § v4e). Medida: el jugador de guion de `CapitanMedidaGameTests`, 128 peleas
por modo, mismas semillas, mobs de reglas (sin red).

## Las dos preguntas

**(a) ¿El jugador de guion del simulador empuja al mob al golpearlo?** No. El guion del simulador (`medir_guion.py`) llamaba
`herir(.., dx=0, dz=0, fuerza=0)`: sin empuje. El jugador de REGLAS del simulador sí empuja (0,4, `golpe.rs`), y la física del
empuje es la de vanilla (`ente::empujar`: v/2 + dir·0,4, arriba min(0,4, vy/2 + 0,4), aire ×0,91). Ya está arreglado en el guion
(0,4 alejándolo del jugador, como `hurtServer`). En el simulador el empuje quita un 11 % sin capitán (145 → 129) y un 17 % con el
capitán de reglas (187 → 155). En el mod, antes del arreglo del anillo, quitaba un 41 % (63,5 → 37,5) y un 17 % (94 → 77,7).

**(b) ¿El mob pierde el golpe si el jugador se aleja durante el aviso?** Sí, siempre lo perdía, pero con otra medida:
- con `alcance_real` (el que usan las pruebas, `medir_guion.py` y los entrenamientos v4: `--alcance-real`) llegaba solo si el
  HUECO entre las cajas era ≤ 0,83 (+ lo que alarga el arma): de centro a centro 1,43–1,68 para un zombi. Más ESTRICTO que el mod
  (≤ 2,0 de centro a centro, `Reach.landing`);
- sin `alcance_real`, ≤ 2,0 de centro a centro, como el mod.
Ahora, con v4e, siempre `Reach.landing` (2·ancho + ½ del jugador + 0,5 + `actionExtra`). El aviso dura lo mismo que en el mod:
8 ticks + el peso (`MobDefense.windup`: + min(14, round(1,5·kg))), 4 el contraataque.

## Lo que había de distinto (y ya se ha copiado)

El escenario de la medida (no son reglas del simulador): el guion no empujaba; los mobs llegaban "veteranos" al azar (peleas ≥ 3
→ ×1,5 vida, ×1,15 daño), con rencor o en casa, escudos al azar y el jugador de tramo de equipo 1 al azar; sin casco (arden al sol
y prenden al jugador al pegarle: ≈ 7 de daño por minuto de fuego); sin duelos (el mod los tiene: sin capitán el élite reta).

Reglas del simulador (efecto pareado sobre el daño por minuto, sin capitán / capitán de reglas): un turno libre se usa (+8 / +2),
turno de grupo grande (+10 / +7), golpe avisado a ≤ 2,0 (+10 / +5), anillo desde 16 (+2 / −11), modo rodeo ×2,3 (+3 / 0),
correr al hueco o al puesto (+4 / −2), caja vanilla para empezar el aviso (−4 / −8), arco vanilla 40 ticks (−2 / −9), rutas cada
10 ticks / MeleeAttackGoal (+6 / −11), movimiento estimado del jugador sin cliente (+11 / −1), `canUse` cada 20 ticks (+5 / +1),
un capitán con > 3 no reta (0 / +17), especiales un tick de cada dos (+3 / −8), embestida desde 3,5 + arma (−3 / −2),
tambaleo = lentitud II (0 / −3), tramo de equipo (daño y turnos; 0 aquí: tramo 0).

| Simulador (reglas) | sin capitán | capitán sin órdenes | capitán de reglas | sin pinza |
|---|---|---|---|---|
| antes (v4d, guion viejo, maestro como red) | 249 | 249 | 255 | 276 |
| v4d, escenario del mod y empuje | 112 | 112 | 130 | 130 |
| v4e | 129 | 140 | 155 | 158 |
| **el mod** | **70,6** | **72,9** | **114,2** | 100,4 |

Con el maestro como red (lo que imitan los entrenamientos) v4e da 155 / 155 / 176 / 166: ahí la pinza gana (+9,6 ± 3,7); con las
reglas de verdad es neutra (−2,5 ± 3,8; en v4d −0,8, antes −20).

## Lo que queda: ≈ ×1,8 sin explicar

El simulador tiene tantos mobs a ≤ 3,5 como el mod (1,0–1,2 de media, el mod 1,1) pero TIENE TURNO casi el doble de tiempo
(0,8–0,9 de media; el mod 0,42 sin capitán tras el arreglo). En 30 s, sin capitán, el simulador da: 4,7 golpes avisados que
llegan (≈ 5 de daño cada uno con el élite), 3,9 embestidas que tocan (≈ 4,7), ≈ 3,5 flechazos (≈ 4) y ≈ 6 de pociones lanzadas.

**Pedimos al mod** (con `FORJA_CAPITAN_MEDIR`, sin capitán y capitán de reglas, 40–80 peleas) por pelea:
1. avisos empezados, avisos que llegan, fintas; embestidas empezadas y que tocan; flechas que dan; daño de pociones u objetos
   lanzados; daño de fuego (con el casco no debería haber);
2. mobs muertos por el jugador;
3. la velocidad media de un zombi que persigue al jugador de guion (andando y corriendo): el simulador usa 0,0611 de aceleración
   (0,135 bloques/tick andando; "medida en 1.21"), y con las cuentas de vanilla (0,23 × 0,23 × 0,98 / 0,454) saldría 0,114. Bajarla
   al 85 % quita ≈ 9 % del daño en el simulador: no es todo, pero hay que saber cuál es la buena;
4. la ablación `sinempuje` repetida tras el arreglo del anillo (en el simulador el empuje pesa menos que en el mod: −11 % frente a −41 %).

## ¿Algo que el mod haga mal?

No hemos encontrado nada claro. Dos cosas para mirar:
- La vanilla `MeleeAttackGoal` del zombi (`followingTargetEvenIfNotSeen = false`) se para al acabar su ruta y no vuelve a mirar
  `canUse` hasta 20 ticks después; cada vez que la decisión cambia de ACERCARSE a una táctica (el turno libre aparece y desaparece
  mientras otros golpean) la meta se para y puede tardar hasta 20 ticks en volver. En el simulador pesa poco (+5 / +1), pero en el
  mod puede ser más (7474b7f ya lo vio con el aviso).
- El anillo desde 16 (SPREAD_RANGE) y las rutas cada 10 ticks le quitan al capitán de reglas en el simulador ≈ 11 cada uno: con un
  jugador que se mueve, los que esperan en el anillo lejos llegan tarde cuando se libera un turno.
