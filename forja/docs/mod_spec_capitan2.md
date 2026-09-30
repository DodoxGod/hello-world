# Capitán 2: lo que el mod tiene que hacer (30-09, simulador py_mobs_v4j)

Resumen: Andy aprobó mejorar el capitán. El simulador ya lo tiene (`src/combate/forja/capitan2.rs`, motor `py_mobs_v4j`, prueba
`pruebas/forja_v4j.py`), cada pieza con su interruptor. Aquí van los números exactos para copiarlo en el mod:

1. **Visión compartida.**
2. **Sucesión** (un veterano interino). **Andy la confirmó el 30-09**: es una excepción a "solo élites y campeones", y solo como
   sucesor.
3. **Protección del capitán.**
4. **Órdenes nuevas**, en el contrato del capitán **revisión 2**: CERRAR_SALIDAS, FOCO_HERIDO y RETIRADA_FALSA.
5. **Capitán visible.**
6. **Red del capitán residual** (contrato `red_capitan_v4` v2).

Con todas las piezas apagadas, el simulador es igual byte a byte a py_mobs_v4i (comprobado). Propuesta de interruptores en
`CombatConfig` (todos **activados** salvo que Andy diga otra cosa): `iaCapitanVision`, `iaCapitanSucesion`,
`iaCapitanProteccion`, `iaCapitanOrdenes2` y `iaCapitanVisible`.

---

## 1. Visión compartida (`iaCapitanVision`)

- **Cuándo:** en cada pase del Squad (cada 10 ticks), con un capitán vivo (también un interino).
- **Quién la da:** de los miembros que percibieron al jugador hace < 10 ticks, el que lo percibió más tarde. Su última posición
  vista es la del grupo.
- **Quién la recibe:** cada miembro que no lo percibe este tick y está a ≤ 32 bloques (3D) del capitán.
- **Qué recibe:** se trata como un sonido oído:
  - `oido` = esa posición;
  - `oido_t` = el tick en que la vio el compañero;
  - solo si es más nueva que su propia estimación (`max(visto_t, oido_t)`);
  - si no tenía foto del jugador (`Perception.Snapshot`), la del compañero.
- **Efecto:**
  - la estimación ("última posición conocida") pasa a ser la del grupo;
  - el que está perdido busca donde lo vio el compañero;
  - el aburrimiento (600 ticks) se reinicia;
  - la observación P la ve como un sonido reciente (`jug_oido`).
- **El capitán** ya lo sabía: `jug_percibido` del capitán es "algún miembro lo percibe".
- **Medida:** en la oscuridad (noche, luz 0), hasta 5 miembros reciben la estimación en un pase.

## 2. Sucesión: un veterano interino (`iaCapitanSucesion`); confirmada por Andy el 30-09

- **Cuándo:** muerto el capitán, a los **60 ticks**. Solo una sucesión por pelea.
- **Quién:** el veterano del grupo (amenaza 1, no jefe) con más vida máxima. Manda como **INTERINO**:
  - **decide cada 20 ticks** (solo los pases con `t % 20 == 0`); entre medias, la orden sigue y los puestos se recolocan;
  - **no puede dar CARGA** (queda "sin orden", con la formación que diga) **ni las órdenes nuevas**. En la máscara de la red
    van a 0;
  - la **moral del grupo lleva −0,15** mientras manda. Es un término más en `Captain.groupMorale`, además del −0,4 de
    `sin_mando` durante los 200 ticks de siempre.
- **Relevo:** si queda un élite o campeón, a los 200 ticks releva al interino (la regla de siempre: `interino = false`).
- **Muerte del interino:** vuelve a haber `sin_mando` y golpe de moral, como con cualquier capitán. No hay una segunda
  sucesión.
- **La observación del capitán:**
  - `capitan_interino` = 1;
  - `sin_mando` sigue bajando.
- **La de los mobs no cambia:** `tengo_capitan` = 1 con el interino.
- **Matar al capitán sigue valiendo la pena:**
  - 60 ticks sin órdenes;
  - golpe de moral (furia y abandono);
  - −0,4 de moral durante 200 ticks y luego −0,15;
  - la mitad de decisiones y sin CARGA.

## 3. Protección del capitán (`iaCapitanProteccion`)

**Por defecto, por reglas.** La red v2 la cambia con la cabeza `proteccion`: 0 = regla, 1 = sin protección, 2 = retirarse ya.

- **Cuándo:** en cada pase, después de colocar los puestos. Se decide primero si se retira; si no, si se queda detrás.
- **Retirarse** (vida del capitán < **35 %**, o la red pide 2):
  - su punto está a **14** del jugador, en la dirección jugador → capitán. Si están encima, la contraria a la mirada lenta;
  - suelta su turno de ataque.
- **Detrás del frente** (≥ **3** miembros de cuerpo a cuerpo además de él, y él también de cuerpo a cuerpo, es decir, ni
  arquero ni lanzador): su punto está a **7** del jugador, en la dirección jugador → centro de los demás miembros.
- **Puesto:** el capitán protegido tiene el puesto `reserva`.
- **El ejecutor, para cualquier cerebro:** el capitán protegido va a su punto como FORMACION, haga lo que haga su red o sus
  reglas, como BUSCAR con el jugador perdido.
  - **Excepción:** "detrás" con turno y el jugador a < 3,5; ahí pelea normal.
  - Llegado a < 1, se queda quieto mirando al jugador.
- **Escoltas** (solo si el jugador está a < **10** del capitán):
  - hasta **2** miembros de cuerpo a cuerpo sin turno: primero los que llevan escudo, luego los más cercanos al capitán;
  - punto a **2** del capitán hacia el jugador, a **±1** de lado (el primero a la derecha);
  - puesto `frente`;
  - las de reglas obedecen: FORMACION sin turno;
  - las de red solo ven su punto (`puesto_delante/derecha`).
- **La observación del capitán:**
  - `capitan_tras_frente/8`: la distancia del capitán al jugador menos la del miembro de cuerpo a cuerpo más cercano, entre 8
    y recortada a ±2;
  - `escoltas/2`: miembros a < 3 del capitán;
  - `proteccion_*`: el modo en vigor.

## 4. Órdenes nuevas: contrato del capitán revisión 2 (`iaCapitanOrdenes2`)

**Solo con una red v2** (las reglas no las dan). **Los mobs las ven con su contrato de siempre** (no cambia `red_mob_v4`):

| Orden nueva | En el bloque M de los mobs (y en el bloque O de v1) |
|---|---|
| CERRAR_SALIDAS | cercar |
| FOCO_HERIDO | ninguna |
| RETIRADA_FALSA | retirada, y tras el ataque carga (`cuenta_atras` 0) |

### 9 CERRAR_SALIDAS
- **Dirección de escape:** la velocidad del jugador si pasa de 0,05 bloques/tick. Si no, la dirección contraria al centro del
  grupo.
- **Puntos:** los miembros de cuerpo a cuerpo con puesto, ordenados por su ángulo respecto a esa dirección, van repartidos
  en un arco de **±60°** a **5** del jugador. Uno solo va justo en la dirección de escape. Los arqueros, a su puesto de
  formación.
- **Obedecer (reglas):** con turno, atacan; sin turno, FORMACION. Van por el anillo si el camino cruza por delante, como
  CERCAR.
- **Premio `obedecer`:** FORMACION, o LIBRE/ACERCARSE con turno.
- **Máscara:** ≥ 2 de cuerpo a cuerpo en el grupo (capitán incluido) y no interino.

### 10 FOCO_HERIDO
- **Con 2 jugadores:** los miembros sin turno, salvo el capitán, cambian de objetivo al otro jugador que esté en la pelea a
  ≤ 16 del capitán, si tiene menos fracción de vida que el mío. Es como `foco` 1, pero eligiendo por la vida.
- **Máscara:** existe ese jugador más herido, y no interino.

### 11 RETIRADA_FALSA
- **Fase 1** (40 ticks desde que se da): puntos a **10** del jugador, alejándose. Los de reglas van con **FORMACION**, no
  con RETIRARSE, para que no cuente para el abandono ni para "huyendo".
- **Paso a la fase 2:** a los 40 ticks, o antes si el jugador avanza ≥ **4** bloques hacia el centro del grupo (desde donde
  estaba al darla). Entonces hay grito, `cero_t`, y **+1 turno durante 40 ticks**. El interino no daría este turno, pero
  tampoco puede dar esta orden.
- **Fase 2:** todos al ataque, como la CARGA en 0: puntos en el jugador, ACERCARSE, `MobSprint` corriendo.
- **Premio `obedecer`:** en la fase 1, FORMACION o RETIRARSE; en la fase 2, LIBRE o ACERCARSE.
- **Máscara:** ≥ 3 de cuerpo a cuerpo, moral del grupo ≥ 0,5, y no interino.
- **Duración:** la orden sigue mientras la red la repita; si cambia, se acaba.

## 5. Capitán visible (`iaCapitanVisible`)

- **En el mod:** el estandarte o el polvo dorado ya están, y hay grito con cada orden nueva que no sea "sin orden y LIBRE".
  Es solo visual.
- **Lo que el simulador añade al jugador de reglas** (para que las redes aprendan que al capitán lo cazan):
  - al empezar la pelea decide si **caza capitanes**, con probabilidad por nivel: novato **15 %**, medio **35 %**, bueno
    **55 %**, experto **75 %**;
  - si caza, su objetivo es el capitán percibido a ≤ **10** en vez del más cercano (salvo rematar a un aturdido);
  - cada orden nueva es un grito: con percepción honesta, el jugador "nota" al capitán si está a ≤ **24**.
- **Medida** (6 peleas por modo): el daño al capitán pasa de 5,7 a 17,8.

## 6. Contrato `red_capitan_v4` revisión 2 (`red_capitan_v4_contrato_v2.json`, en motor_rust/)

- **Carga:** una red con `"contrato_version": 2` tiene 253 entradas y 68 salidas. Una sin ese campo (o con 1) sigue siendo la
  de v1 (213 → 60), y **se tiene que poder cargar igual**.
- **Entradas 0..212:** las de v1, iguales.
- **Entradas 213..252:**
  - **la orden que daría `Captain.rules()` ahora** (el mod la tiene): `regla_orden_*` (12), `regla_formacion_*` (4) y
    `regla_cuenta_*` (4). Con un interino, CARGA pasa a "ninguna";
  - la orden actual si es de las nuevas (3) y `falsa_ataque`;
  - `capitan_interino`, `sin_mando`, `capitan_tras_frente/8`, `escoltas/2`;
  - `jug_mira_capitan`: el jugador lo ve en un cono de 30°;
  - `capitan_golpeado/3`: golpes del jugador al capitán en 100 ticks;
  - `compartida/8`;
  - `jug_aleja/0.2`: la velocidad del jugador alejándose del centro del grupo;
  - `salidas_libres/8`: de 8 direcciones, las que a 4 bloques no suben más de 1 y se ven;
  - `otro_jug_presente`, `otro_vida_frac`, `otro_dist/16`, `mi_jug_vida_frac`;
  - `proteccion_regla`, `proteccion_sin`, `proteccion_retirada`.
  - Los nombres exactos y su orden están en el JSON.
- **Salidas (68), por cabezas:**

  | Cabeza | Índices | Valores |
  |---|---|---|
  | orden | 0..11 | 12 |
  | formacion | 12..15 | 4 |
  | sector | 16..24 | 9 |
  | cuenta | 25..28 | 4 |
  | foco | 29..30 | 2 |
  | puesto0..7 | 31..62 | 4 cada uno |
  | proteccion | 63..65 | 3 |
  | mando | 66..67 | 2 |

- **Cómo se combinan (RESIDUAL):**
  1. **mando = 1 si `logits[67] > logits[66]`**, sin temperatura ni sorteo.
  2. **mando 0** → la orden de `Captain.rules()` tal cual (puestos por tipo) y protección 0 (regla).
  3. **mando 1** → se sortean las otras 14 cabezas como en v1: con logits/T y su máscara, sin prohibir nunca el índice 0.
- **Máscaras nuevas:**
  - CARGA, CERRAR_SALIDAS, FOCO_HERIDO y RETIRADA_FALSA, como arriba;
  - `proteccion` 1 y 2, solo con la pieza de protección;
  - `mando`, siempre las dos.
- **Cadencia:** una decisión cada 10 ticks, o cada 20 con un interino.
- **Memoria:** la GRU a 0 al formarse el grupo.
- **Garantía:** una red recién empezada (filas de `mando` a 0 y sesgo (+6, 0)) es exactamente el capitán de reglas. En el
  simulador, la medida pareada da **64/64 peleas idénticas**.

## 7. Qué pedimos al mod, en orden

1. Contrato v2 en `CaptainBrain`:
   - cargar las dos versiones;
   - dar la orden de las reglas como entrada;
   - la regla de `mando`;
   - las 3 cabezas nuevas.
2. Las órdenes nuevas en `Captain.place` y `RuleBrain.obey`, con los números de arriba.
3. Sucesión, protección y visión compartida.
4. Medir con `CapitanMedidaGameTests`: sin capitán, capitán de reglas y cada pieza sola. Comparar con el simulador.
