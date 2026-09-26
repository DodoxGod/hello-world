# Combate de Forja: constantes y paquetes

Referencia para quien simule o automatice el combate de Forja (bots, simuladores, IA de mobs).
Los números son los **valores por defecto** de `config/forja.json` → sección `combate`
(`dev.forja.combat.CombatConfig`); un servidor puede cambiarlos. 1 tick = 1/20 s.

## 1. Constantes

### Armadura (`ArmorMath`, `ArmorCalculator`)
| Qué | Valor |
|---|---|
| Reducción | `armadura_ef / (armadura_ef + K)`, K = `curveK` = **20**, tope `maxReduction` = **0,80** |
| Daño final | `daño × (1 − reducción(armadura × (1 − pen_ef)))` |
| Penetración efectiva | `pen × (T / (T + dureza))`, T = `toughnessScale` = **10** |
| Penetración combinada con Brecha | `1 − (1 − pen_arma) × (1 − brecha)` |
| Durabilidad | factor = `0,6 + 0,4 × (durabilidad restante / máx.)` (`minDurabilityFactor` = 0,6) |
| Normalización por pieza | armadura_pieza × {casco 20/3, peto 20/8, grebas 20/6, botas 20/3} |
| Armadura "natural" (piel de zombi, bonos) | se suma entera, sin zona ni tipo |
| Golpe preciso a la cabeza | ×**1,3** (`headMultiplier`); solo jugadores y proyectiles |

**Zonas** (altura relativa del impacto: 0 = pies, 1 = coronilla) y peso de cada pieza [casco, peto, grebas, botas]:

| Zona | Altura | Pesos |
|---|---|---|
| CABEZA | ≥ 0,78 | 0,70 · 0,30 · 0 · 0 |
| TORSO | ≥ 0,45 | 0,10 · 0,70 · 0,20 · 0 |
| PIERNAS | ≥ 0,15 | 0 · 0,15 · 0,70 · 0,15 |
| PIES | < 0,15 | 0 · 0 · 0,30 · 0,70 |
| TODO (explosiones) | — | 0,25 cada una |

Los jugadores golpean donde apuntan (rayo desde el ojo). Los mobs golpean a la altura `y + 0,6 × su_altura`. Los proyectiles golpean a la altura de su centro.

**Resistencia por material** (`MaterialCombat`). Rigidez `r = clamp(dureza/3 + (defensa_conjunto − 7)/26, 0, 1)`:
- Pieza no forjada:
  - corte = `0,8 + 0,5r`
  - golpe = `0,9 + 0,3(1 − r) + 2·kb`
  - perforación = `0,7 + 0,5r`
- Pieza forjada:
  - corte = `0,8 + 0,5·r_placa`
  - golpe = `(0,9 + 2·kb_placa)(0,8 + 0,4(1 − r_forro))`
  - perforación = `0,7 + 0,5·r_placa`
- Peso del conjunto = `clamp((defensa − 7)/13, 0, 1) × 0,7 + clamp(3·kb, 0, 0,3)`; la placa Diáfana pesa ×0,2.
- Ralentización = `peso × 0,10` (`maxArmorSlow`).

**Penetración por arma** (`pen*`):

| Arma | Penetración |
|---|---|
| Puño | 0 |
| Espada, daga, espadón | 0,10 |
| Hacha, picahacha, guadaña | 0,35 |
| Mazo, martillo, mangual, guanteletes, báculo, maza | 0,25 |
| Lanza, tridente | 0,30 |
| Pico | 0,25 |
| Otras herramientas | 0,05 |
| Tridente lanzado | 0,40 |
| Flecha | `0,05 + 0,12 × velocidad` (bloques/tick), tope 0,50 |
| Flecha cargada | +0,25 de penetración y ×1,5 de daño |

**Tipo de daño**:
- Corte: espadas, hachas, guadaña, arañas, lobos, osos polares, gatos, ocelotes, zorros, phantoms y vexes.
- Perforación: lanzas, tridentes, picos, flechas, arañas de cueva, abejas, lepismas, endermites, hoglins y zoglins.
- Golpe: lo demás.

### Estamina (solo jugadores; no se aplica en creativo ni espectador)
| Qué | Valor |
|---|---|
| Máximo | **100** |
| Regeneración | **1,0**/tick tras **20** ticks sin gastar, × `(1 − min(0,9, peso × 0,5))` |
| Atacar | **12** (si no llega: la barra se vacía y ese golpe hace ×**0,6**) |
| Esquivar | **25** |
| Bloquear con escudo | **3** por punto de daño bloqueado. Si no llega: guardia rota, el escudo tiene **60** ticks de enfriamiento y el golpe entra |
| Parada normal / perfecta | devuelve **15** / **30** |

La parada con escudo forjado no gasta estamina.

### Esquiva
| Qué | Valor |
|---|---|
| Invulnerabilidad | **6** ticks, solo contra daño con atacante o proyectil (no contra caídas ni fuego) |
| Enfriamiento | **15** ticks |
| Impulso (lo aplica el cliente) | horizontal **0,75** en la dirección del movimiento (hacia atrás si no hay dirección) y **0,2** vertical |
| Requisitos | estar en el suelo y tener estamina ≥ 25. El servidor tolera **2** ticks de enfriamiento y **3** de estamina |

### Parada (solo escudos forjados; `CombatUpgrades`)
| Qué | Valor |
|---|---|
| Ventana | `max(3, round(retardo_de_bloqueo_s × 20))` ticks desde que se levanta el escudo, +4 con Baluarte |
| Perfecta | los primeros `max(2, round(ventana × 0,34))` ticks |
| Anti-spam | si se levanta otra vez antes de **10** ticks desde la anterior, no hay ventana. Una parada acertada lo reinicia |
| Efecto | anula el golpe y devuelve `min(8, 2 + golpe_del_escudo)` de daño si el ataque fue cuerpo a cuerpo. Empuja al atacante 1,4 y le pone Lentitud II y Debilidad I durante 60 ticks. Además, la perfecta rompe toda su postura y la normal le quita la mitad |
| Contraataque | durante 20 ticks (40 si fue perfecta), tu siguiente golpe se duplica; contra un rival aturdido añade ×(1 + **2,0**) |
| Proyectil parado | se devuelve al tirador |

### Ataque cargado (jugador)
| Qué | Valor |
|---|---|
| Armas que cargan | las que tienen estilo propio de golpe: espadas, espadón, guadaña (tajo); hachas, picahacha, martillo, mazo, mangual, maza (golpe desde arriba); lanza, tridente, daga (estocada) |
| Cómo | mantener el botón de ataque tras un golpe. La carga empieza a los **6** ticks (`chargeDelayTicks`) y se llena en **14** más (`chargeFullTicks`). Mirar a un bloque, abrir una pantalla o usar un objeto la cancela sin golpear |
| Soltar | si carga < **0,35**, no pasa nada. Si no, golpe al objetivo que se mira (alcance de interacción + 0,5; los bloques tapan) |
| Coste | **20 + 15 × carga** de estamina. Si no llega, el golpe sale con ×0,6 y sin extras |
| Efecto | daño ×(1 + **1,0** × carga); postura ×(1 + **1,5** × carga). No gasta los 12 del ataque normal ni cuenta para el combo |

### Remate
Golpe cuerpo a cuerpo de un jugador a un mob **aturdido**, si es cargado o viene **por la espalda** (el
atacante está a más de ~110° de donde mira el objetivo: producto escalar < −0,35). Daño ×**2,0**, que se suma al
×1,25 del aturdido. Acaba el aturdimiento y vacía la postura.

### Combos
Golpes con fuerza de carga ≥ **0,9** separados por ≤ **30** ticks encadenan. El tercero hace daño ×**1,3** y
postura ×**1,5**, y el contador vuelve a 0. Un golpe débil (< 0,9) rompe la cadena.

### Contraataque tras esquiva perfecta
Si un golpe con atacante choca con la invulnerabilidad de la esquiva, la esquiva es perfecta:
- devuelve **10** de estamina
- abre un contraataque de **30** ticks: el siguiente golpe hace ×**1,5** de daño y ×**2,0** de postura

Solo cuenta el primer golpe de cada esquiva.

### Parada con el arma
| Qué | Valor |
|---|---|
| Armas | espadas vanilla, espada, espadón y daga forjadas; clic derecho. Con un escudo en la otra mano, se usa el escudo |
| Bloqueo | sin retraso, ángulo de 90°, para el **50 %** del golpe; cuesta estamina como el escudo |
| Parada | los primeros **3** ticks tras levantarla. Para el golpe **entero**, sin coste, con los mismos efectos que la parada del escudo forjado |
| Nota | comparte enfriamiento con el ataque especial del arma (agacharse + clic derecho) |

### Armaduras normales: cota de malla
La fórmula de rigidez trata mal a la malla. Valores fijos: corte ×**1,35**, golpe ×**0,8**, perforación ×**0,95**; el
peso sale de la fórmula. Se pueden cambiar en `materiales` con la clave `cota_de_malla`.

### Postura (solo mobs)
| Qué | Valor |
|---|---|
| Máximo | `vida_máx × 0,6 + 5` (zombi: 17) |
| Llenado por golpe | `daño × {corte 1,0 · golpe 1,5 · perforación 0,6 · otro 0,5}` |
| Vaciado | tras **60** ticks sin golpes, **0,3**/tick |
| Aturdimiento | **40** ticks (menos si se repite, ver Dificultad): Lentitud V y Debilidad II, no ataca ni embiste, recibe ×**1,25** de daño |

### Mobs normales (namespace `minecraft`)
| Qué | Valor |
|---|---|
| Aviso cuerpo a cuerpo | **8** ticks quieto antes del golpe (solo contra jugadores). El golpe entra si la distancia es ≤ `2×ancho_mob + 0,5×ancho_jugador + 0,5` y hay línea de visión |
| Atacantes simultáneos | **2** por jugador (los demás esperan) |
| Embestida del zombi | de **3,5** a **7** bloques de distancia. **12** ticks agachado y luego salto con velocidad horizontal **0,85** y vertical **0,35**. Si alcanza, golpea y pone Lentitud II **30** ticks. Enfriamiento de **100** a **200** ticks |
| Disparo cargado del esqueleto | **1 de cada 3** disparos; tensa **20** ticks más |
| Finta del creeper | **35 %** de las veces, a los **12** ticks de la mecha se para **20** ticks; una vez por encendido |

### Dificultad (fase 0b; paquete `dev.forja.difficulty`)

**Selector** (`dificultad` en la configuración o `/forja dificultad <nivel>`; sin argumento muestra el estado):

| Dificultad | Vida mobs | Daño mobs | Postura | Amenaza | Tope por golpe | Temperatura | Botín |
|---|---|---|---|---|---|---|---|
| APRENDIZ | ×0,8 | ×0,7 | ×0,8 | ×0,5 | ×1,4 | ×1,3 | ×0,8 |
| HERRERO (por defecto) | ×1 | ×1 | ×1 | ×1 | ×1 | ×1 | ×1 |
| MAESTRO | ×1,3 | ×1,25 | ×1,2 | ×1,5 | ×0,85 | ×0,8 | ×1,3 |
| LEYENDA | ×1,7 | ×1,5 | ×1,4 | ×2,2 | ×0,7 | ×0,6 | ×1,7 |

**Niveles de amenaza** (etiqueta de entidad; se decide una vez, la primera vez que el mob hostil entra al mundo):

| Nivel | Etiqueta | Vida | Armadura | Postura | Daño | Tope por golpe | Guardia | Temperatura |
|---|---|---|---|---|---|---|---|---|
| NORMAL | — | ×1 | +0 | ×1 | ×1 | 45 % | no | ×1 |
| VETERANO | `forja_veterano` | ×1,5 | +2 | ×1,3 | ×1,15 | 35 % | no | ×0,9 |
| ELITE | `forja_amenaza_elite` | ×2,5 | +4 | ×1,8 | ×1,3 | 20 % | sí | ×0,75 |
| CAMPEON | `forja_elite` (el élite legendario de Forja: vida ×5, armadura +8, daño ×2, ya existía) | — | — | ×2,5 | — | 12 % | sí | ×0,6 |
| Jefe (Herrero Caído) | — | — | — | — | — | 8 % | sí | — |

- **Sorteo:**
  - p(élite) = min(0,25, 0,03·k)
  - p(veterano) = min(0,5, 0,12·k)
  - k = dificultad.amenaza · (1 + min(2, distancia al spawn/1500)) · (1,5 en el Nether; si no, 1,3 bajo y=0; si no, 1) · noches · (1 + equipo) · adaptativa
  - Los veteranos y élites llevan nombre ("Zombi veterano", "Zombi de élite").
- **Equipo** (puntuación del jugador más cercano a ≤ 128 bloques, 0..1):
  - puntuación = 0,35·clamp((ataque−1)/15) + 0,20·clamp(Σ% mejoras en mano y armadura/400) + 0,15·maestría/10 + 0,10·nivel de herrero/10 + 0,20·clamp(armadura/30)
  - tramo = min(3, ⌊puntuación·4⌋)
  - Cada tramo da al mob +20 % de vida y +1,5 de armadura.
- **Vida final** = base × dificultad.vida × amenaza.vida × (1 + 0,2·tramo).

**En cada golpe** (orden en `CombatHooks.afterArmor`):
1. Multiplicadores del atacante (cansado, cargado, combo, contraataque, flecha cargada, cabeza).
2. Aturdido ×1,25 y remate ×2 (el remate tiene **100** ticks de enfriamiento por objetivo; a un jefe solo se le puede rematar con ≤ 50 % de vida).
3. **Resistencia del mob** al tipo de golpe (`resistenciasMobs`):

| Mob | Corte | Golpe | Perforación |
|---|---|---|---|
| forja:yunque_andante | 0,5 | 1,15 | 0,7 |
| forja:automata_de_forja | 0,8 | 1,1 | 0,7 |
| forja:escoria_viviente | 1,2 | 0,5 | 1,0 |
| forja:herrumbre | 0,8 | 1,25 | 0,4 |
| forja:coraza_vacia | 0,7 | 1,3 | 0,9 |
| forja:percutor | 0,9 | 0,8 | 1,1 |
| forja:guardian_de_cuno | 0,75 | 1,1 | 0,8 |
| esqueleto, stray | 1,0 | 1,3 | 0,7 |
| esqueleto wither | 1,0 | 1,25 | 0,75 |
| araña | 1,15 | 1,0 | 0,9 |
| slime | 1,2 | 0,6 | 1,0 |
| cubo de magma | 1,1 | 0,6 | 1,0 |

4. Si un mob hostil golpea a un jugador: × dificultad.daño × amenaza.daño × (1 + 0,15·adaptativa).
5. Postura (con el daño hasta aquí).
6. **Guardia**: élite, campeón o jefe no aturdido → solo el **50 %** llega a la vida.
7. **Presión** si la víctima es un jugador: penetración = max(pen_arma, min(0,7, pen_arma + presión)).
8. Armadura.
9. **Tope por golpe**, solo contra mobs y solo si el atacante es un jugador: daño ≤ vida_máx × tope(nivel o jefe) × dificultad.tope. No se aplica si el mob estaba aturdido o si el golpe es un remate.

**Presión** (solo jugadores):
- Cada golpe recibido de otra entidad hace p = min(0,7, p + 0,07).
- Se vacía a 0,02 por tick tras 40 ticks sin golpes.
- El jugador la ve como una línea roja bajo la barra de estamina.

**Resistencia al aturdimiento**:
- Aturdimientos recientes n; se olvida 1 por cada 200 ticks desde el último.
- Duración = max(12, 40·0,7ⁿ).
- Postura máxima = (vida·0,6 + 5) × amenaza.postura × dificultad.postura × (1 + 0,3·n).
- Resistencia (para observarla) = 1 − 0,7ⁿ.

**Dificultad adaptativa** (por jugador, se guarda):
- a ∈ [−1, 1].
- +0,02 por cada mob hostil matado sin haber recibido daño en 200 ticks.
- −0,15 por cada muerte.
- Daño de los mobs ×(1 + 0,15·a); amenaza ×(1 + 0,5·a).

**Noches** (se cuentan al amanecer, con algún jugador conectado):
- Una aparición **natural** de noche trae un compañero de su tipo con probabilidad min(0,35, 0,04·log₂(1 + noches)). El compañero no trae otro.
- Amenaza ×(1 + min(1,5, 0,03·noches)).
- La cantidad nunca crece más allá de eso.

**Recompensas** (mob hostil muerto por un jugador):
- Tiradas extra de botín = (veterano 0,35; élite o campeón 1,0) × dificultad.botín. La parte entera sale siempre; el resto, con esa probabilidad.
- Maestría del arma y experiencia de herrero: veterano +2, élite +5, campeón +10, × dificultad.botín.

**Mejoras**: el daño extra de varias mejoras sobre el mismo objetivo en el mismo tick cuenta cada vez menos: `daño / (1 + ya_hecho / 4)`.

**Solo mejoras de Forja** (`soloMejorasForja`, por defecto no): los encantamientos vanilla de los objetos no forjados dejan de funcionar.

**CombatAnim 13 PRESSURE** (solo al jugador): a = presión, b = vaciado por tick, ticks = espera antes de vaciarse.

## 2. Paquetes de red

Son payloads personalizados de Fabric: en el juego viajan como `custom_payload`, con el canal como `Identifier`. Los campos van en big-endian (Netty) y `VarInt` es el de Minecraft.

Para que el servidor le mande cosas a un cliente que no es Fabric (por ejemplo mineflayer), ese cliente tiene que anunciar los canales en `minecraft:register` (nombres separados por `\0`). Si no lo hace, `ServerPlayNetworking.canSend` devuelve false y el servidor no le envía nada.

### `forja:animacion_combate` (servidor → cliente)
| Campo | Tipo |
|---|---|
| entidad | VarInt (id de la entidad en red) |
| tipo | byte (índice en la lista de abajo) |
| ticks | short |
| a | float |
| b | float |

| Índice | Tipo | Quién lo recibe | ticks | a | b |
|---|---|---|---|---|---|
| 0 | TELEGRAPH | quien ve al mob | duración del aviso (8) | — | — |
| 1 | LUNGE | quien ve al mob | duración de la preparación (12) | — | — |
| 2 | STAGGER | quien ve a la entidad | duración del aturdimiento (40) | — | — |
| 3 | DODGE | quien ve al jugador y él mismo | 6 + 4 | dirección x | dirección z |
| 4 | PARRY | quien ve al defensor y él mismo | 8 | 1 si fue perfecta | — |
| 5 | GUARD_BREAK | quien ve al jugador y él mismo | 60 | — | — |
| 6 | HIT | solo el atacante (entidad = víctima) | 0 | daño hecho | — |
| 7 | HURT | solo la víctima | 0 | daño recibido | — |
| 8 | POSTURE | quien ve al mob | espera antes de vaciarse (60) | llenado 0..1 | cuánto se vacía por tick (0..1) |
| 9 | CHARGE | quien ve al jugador y él mismo | ticks hasta la carga llena (14), 0 al soltar | 1 empieza / 0 suelta o cancela | — |
| 10 | COMBO | quien ve al jugador y él mismo | paso: 2 (el siguiente remata) o 3 (remató) | — | — |
| 11 | FINISHER | quien ve a la víctima | 10 | — | — |
| 12 | PERFECT_DODGE | quien ve al jugador y él mismo | duración del contraataque (30) | — | — |
| 13 | PRESSURE | solo el jugador | espera antes de vaciarse (40) | presión 0..0,7 | vaciado por tick |

TELEGRAPH marca el inicio del aviso; el golpe llega `ticks` después. POSTURE llega con cada golpe, y el cliente simula el vaciado: `llenado − max(0, t − ticks) × b`.

### `forja:reglas_combate` (servidor → cliente, al entrar)
Campos en orden:

| Campo | Tipo |
|---|---|
| activo | bool |
| esquiva | bool |
| estamina | bool |
| estamina_máx | float |
| coste_esquiva | float |
| enfriamiento_esquiva | VarInt |
| fuerza_esquiva | float |
| impulso_vertical | float |
| ataque_cargado | bool |
| retraso_carga | VarInt |
| carga_llena | VarInt |

### `forja:carga` (cliente → servidor)
Un solo campo: `accion` (byte), que vale 0 CANCELAR, 1 EMPEZAR o 2 SOLTAR. El servidor mide la carga con su propio reloj.

### `forja:esquiva` (cliente → servidor)
Campos: `x` (float) y `z` (float), la dirección horizontal en coordenadas del mundo.

El cliente **aplica el impulso él mismo** antes de mandarlo: velocidad = `(x × 0,75, 0,2, z × 0,75)`. El servidor solo valida y da la invulnerabilidad. Un bot tiene que hacer las dos cosas.

### Estamina propia
Es un attachment sincronizado de Fabric (`forja:estamina`, float) que solo recibe su dueño, y viaja por el canal de sincronización de attachments de Fabric. Para un bot externo es más fácil calcularla con las reglas de arriba, o pedir que el mod la exponga por un canal propio.

## 3. IA de los mobs (fase 1; paquete `dev.forja.ai`)

### Quién piensa y cuándo
- Todo mob hostil con navegación (`PathfinderMob` y `Enemy`) recibe un cerebro (`MobMind`) y el ejecutor
  (`TacticGoal`, prioridad 0, controla movimiento y mirada) al entrar al mundo.
- Solo piensa si su objetivo es un jugador vivo, no creativo ni espectador, a ≤ **32** bloques (`iaAlcance`).
- **Red** si `iaModo` es `auto` (y hay archivo para su familia) o `red`; **reglas** si `reglas`, si no hay red o
  si la red no encaja.
- La red decide cada `ticks_por_decision` ticks (el valor del archivo; 2 si no lo trae). Las reglas deciden cada tick.
- La memoria de la GRU se pone a 0 al aparecer el mob y cada vez que cambia de objetivo.
- **Sorteo**, nunca el máximo:
  - mover ~ softmax(logits[0..8]/T) con la máscara
  - saltar ~ Bernoulli(σ(logits[9]/T))
  - usar ~ Bernoulli(σ(logits[10]/T))
  - T = `iaTemperatura` (1,0) × dificultad.temperatura × amenaza.temperatura
- **Máscara**: un creeper encendido no puede elegir mover 1..8 ni usar; saltar solo en el suelo o en el agua.
- **Archivos**: `config/forja/redes/red_<familia>.json` (o la carpeta `iaCarpetaRedes`); familias `cuerpo`,
  `arquero`, `creeper` y `arana`. Una red se descarta (y el registro dice por qué) si `nombres_obs` no empieza por
  las 102 del mod en el mismo orden, o si pide entradas del bloque Forja que el mod aún no da.

### Familias
| Familia | Tipos |
|---|---|
| cuerpo | zombi, husk, ahogado, aldeano zombi |
| arquero | esqueleto, stray, bogged |
| creeper | creeper |
| arana | araña, araña de cueva |
| otro | los demás |

One-hot de tipo (entradas 33–39): zombie, husk, drowned, skeleton, stray, creeper, spider. Los demás tipos, todo a 0.

### Observación (102, igual que `obs_mob`) — traducción al juego real
| Entrada | En el mod |
|---|---|
| 2 obj_en_alcance | distancia plana entre las cajas ≤ sqrt(2,04) − 0,6, y distancia vertical entre cajas ≤ lo mismo |
| 9, 10 escudo | usando un objeto con `BLOCKS_ATTACKS` (escudos, o armas en guardia); activo si lleva ≥ 5 ticks |
| 11–16 mano | espada = espadas o arma con estilo propio; hacha = hachas; arco = tensando un arco; comida = objeto con `FOOD`; bloques = `BlockItem`; si no, nada |
| 19 desde_ataque | `attackStrengthTicker` del jugador, recortado a 0..40 |
| 27 recarga | la recarga del ejecutor: 20 ticks tras un golpe cuerpo a cuerpo o un disparo |
| 28 arco | ticks tensando del ejecutor |
| 29 mecha | `swell` del creeper /30 |
| 32 herido | `invulnerableTime` > 10 |
| 41 lo ve | rayo de bloques desde los ojos del mob a (jugador, y + 0,6·alto), o y + 1,5 para el creeper |
| 43–58 alturas | "suelo" de una columna = la cara superior del bloque con colisión más alto, buscando desde 4 bloques sobre los pies hasta 8 por debajo, en las columnas que cubre la huella del mob (media anchura ≤ 0,3). Si no hay: pies − 8 |
| 59–66 peligro | lava en la celda del suelo, o caída ≥ 3 |
| 67 agua | agua en alguna de las 16 celdas de suelo, o el mob en el agua |
| 68–100 aliados | otros mobs `Enemy` vivos a ≤ 32 bloques, del más cercano al más lejano; grupo según la familia |

### Controles de la red (modo LIBRE)
- **mover**:
  - 0: parar
  - 1: ruta hacia el jugador; para a ≤ 0,9
  - 5: ruta a un punto 4 bloques más allá, en sentido contrario al jugador
  - 2, 3, 4, 6, 7, 8: punto a 2 bloques en la dirección (k−1)·45° desde "hacia el jugador", girando a la derecha (`MoveControl`)
  - Velocidad 1,0 (0,5 mientras tensa el arco).
  - Las rutas se recalculan como mucho cada 10 ticks.
- **saltar**: salto normal en el suelo o en el agua. La araña, a 2–4 bloques, salta al jugador como
  `LeapAtTargetGoal`: velocidad horizontal = dirección·0,4 + movimiento·0,2, vertical 0,4.
- **usar**:
  - **Cuerpo a cuerpo** (y cualquier otra familia): si alcanza, no tiene recarga y consigue turno (máx. 2 atacantes),
    empieza un **aviso de 8 ticks** quieto; después golpea si está a ≤ 2·ancho + 0,5·ancho_jugador + 0,5 y lo ve.
    Recarga 20. *Diferencia con M1: allí el golpe era inmediato; en Forja siempre hay aviso.*
  - **Arquero**: tensa mientras se pida, lo vea y esté a < 16. A los 20 ticks dispara (`performRangedAttack` con la
    fuerza de 20 ticks); recarga 20. Si deja de pedirlo o lo pierde de vista, destensa.
  - **Creeper**: se enciende si se pide, a < 3 y viéndolo. Encendido sigue mientras esté a < 7 y lo vea; si no, se
    deshincha.
- **fintar** (v2): durante la primera mitad de un aviso, lo cancela; recarga 10.
- Mientras un mob está aturdido, el ejecutor no hace nada. Un aviso empezado se termina antes de obedecer otra
  decisión (salvo la finta).

### Tácticas (cabeza `tactica`; las reglas ya las usan)
| Táctica | Qué hace exactamente |
|---|---|
| LIBRE (0) | los controles de arriba |
| ACERCARSE (1) | con reglas: las metas vanilla de acercarse y atacar (con el aviso de Forja). Con red: igual que LIBRE |
| RODEAR (2) | ruta al punto del anillo: jugador + 3,5·(cos θ, sin θ), θ = su hueco en el anillo (fase 3) o su ángulo actual respecto al jugador; velocidad 1,0; para a < 0,7 del punto |
| FLANQUEAR (3) | igual, radio 2,5, velocidad 1,15, θ = hacia donde mira el jugador + 180° ± 30° (del lado en que ya está el mob) |
| ESPERAR (4) | se mantiene a 4–6 bloques: si está a < 4 se aleja; a > 6 se acerca (velocidad 0,8); en medio, quieto |
| RETIRARSE (5) | ruta a 6 bloques más lejos en línea recta desde el jugador, velocidad 1,2; si no hay ruta, un punto al azar lejos del jugador (8 horizontal, 4 vertical) |
| REAGRUPARSE (6) | ruta al centro de hasta 6 aliados con el mismo objetivo; velocidad 1,0 |
| CUBRIRSE (7) | levanta el escudo de la mano izquierda (si lo tiene) y se acerca a 0,6 hasta 1,5 bloques |

Con cualquier táctica que no sea LIBRE ni ACERCARSE, `usar` sigue funcionando: el mob golpea si alcanza.

### Reglas (fase 1)
- Arqueros y creepers: ACERCARSE (su IA vanilla).
- Los demás:
  - con < 20 % de vida, al menos 2 aliados cerca y el jugador a < 8 → RETIRARSE
  - si los turnos están ocupados y está a < 6 → RODEAR
  - si no → ACERCARSE

### Depuración y datos
- `/forja ia`: estado de las redes.
- `/forja ia ver`: la decisión del mob que miras.
- `/forja ia modo auto|reglas|red`
- `/forja ia temperatura <t>`
- `/forja ia recargar`
- `/forja ia estadisticas`
- `/forja ia grabar` (empieza o para): `config/forja/grabaciones/grabacion_<fecha>.jsonl`, una línea por decisión de red:

```
{"t", "uuid", "tipo", "obs":[…], "mascara":[…], "accion":{"mover","saltar","usar","tactica","especial","defensa","fintar"},
 "logits":[…], "vida_mob", "vida_jugador", "evento": "" | "avisando" | "tensando"}
```


### Leer al jugador (fase 2)
**Hábitos** (`PlayerHabits`, se guardan en el jugador y se conservan al morir):
- **A cada golpe con atacante que le llega:** r ← r + 0,05·(x − r) para parada, esquiva y bloqueo (x = 1 si hizo eso).
  "Recibido" no suma a ninguno. Iniciales: parada 0,1; esquiva 0,1; bloqueo 0,2.
- **A cada esquiva**, con el mob más cercano que lo tiene de objetivo (≤ 8): lado ← lado + 0,1·(s − lado), con
  s = signo(dirección · derecha del mob), y derecha = (−u_z, u_x), u = mob→jugador. Inicial 0.
- **A cada golpe suyo cuerpo a cuerpo:** distancia ← distancia + 0,05·(d − distancia); carga ← carga + 0,05·(cargado − carga).
  Iniciales 3 y 0.
- **Nivel** = clamp(1,2·parada + esquiva + 0,4·bloqueo, 0, 1).

**Turnos** (`Aggression.maxAttackers`), base 2:
- −1 si nivel < 0,2; +1 si nivel > 0,7
- +1 si estamina < 25
- +1 si su esquiva está en enfriamiento
- +1 si come o bebe
- +1 si vida < 30 %
- recortado a [1, base + 2]

Vale para el aviso vanilla, la embestida y el golpe de las redes.

**Fintas vanilla**: al empezar un aviso, con probabilidad min(0,5; 0,6·parada) el golpe es falso. A la mitad del
aviso se cancela sin golpear y el mob vuelve a su recarga.

**Reglas** (solo mobs vanilla cuerpo a cuerpo; los de Forja, arqueros y creepers siguen con lo suyo):
1. Vida < 20 %, ≥ 2 aliados y jugador a < 8 → RETIRARSE.
2. Jugador cargando un golpe, a < 4, y el mob sin turno ni aviso → RETIRARSE.
3. Sin turno y a < 6 → FLANQUEAR si el jugador lleva armadura pesada (peso > 0,5); ESPERAR si lleva un arma de
   golpe desde arriba; RODEAR en otro caso.
4. Si no → ACERCARSE.

FLANQUEAR elige el lado: θ = detrás + signo(hábito lado)·30° si |lado| > 0,3; si no, el lado en el que ya está el mob.

**Bloque Forja de observaciones** (tras las 102; los nombres exactos los da `ObsForja.names()`):

| Grupo | Entradas |
|---|---|
| Jugador | `jug_estamina/100`, `jug_cargando`, `jug_carga`, `jug_guardia/10`, `jug_esquiva_enfriamiento/15`, `jug_esquivando`, `jug_contraataque`, `jug_combo/2`, `jug_peso_armadura`, `jug_estilo_tajo`, `jug_estilo_golpe`, `jug_estilo_estocada`, `jug_estilo_otro`, `jug_presion`, `jug_equipo` |
| Hábitos | `hab_parada`, `hab_esquiva`, `hab_bloqueo`, `hab_lado`, `hab_distancia/8`, `hab_carga`, `hab_nivel`, `turnos_max/4` |
| Dificultad | `dif_aprendiz`, `dif_herrero`, `dif_maestro`, `dif_leyenda` |

Una red v2 puede usar un prefijo de este bloque (sus `nombres_obs` tienen que coincidir con los primeros N del mod).

### Tácticas de grupo (fase 3; `Squad`, cada 10 ticks, por reglas)
- **Escuadrón**: los mobs con cerebro cuyo objetivo es el mismo jugador.
- **Anillo**: n huecos a 360°/n, empezando por el ángulo (desde el jugador, atan2(z, x)) del mob más cercano. Los
  mobs, del más cercano al más lejano, toman el hueco libre con el ángulo más parecido al suyo. RODEAR va a su hueco.
- **Papeles**:
  - arqueros → COBERTURA
  - con ≥ 3 miembros, el de cuerpo a cuerpo más separado de hacia donde mira el jugador → FLANCO, y el más cercano
    de cuerpo a cuerpo que no es el flanco → DISTRACTOR
  - quien tiene turno → ATACANTE
  - los demás → RESERVA
- **Líder**: el de mayor amenaza (luego, más vida máxima).
- **Desbandada**: si muere el líder, o mueren ≥ la mitad del máximo del grupo (≥ 3) en 200 ticks, todos RETIRARSE
  durante 60 ticks.
- **Aliado aturdido** a < 5 del jugador: los demás toman como hueco el ángulo de ese aliado (se interponen).
- **Varios jugadores**: si un grupo tiene ≥ 3 mobs más que otro jugador a ≤ 16 bloques, el miembro sin turno más
  cercano a ese otro jugador cambia de objetivo (uno cada 10 ticks).
- **Pedir ayuda**: un mob hostil con < 50 % de vida herido por un jugador hace que los hostiles sin objetivo a ≤ 24
  bloques tomen a ese jugador (una vez cada 100 ticks por mob).
- **Fuego de cobertura**: el esqueleto no suelta la flecha mientras otro hostil (caja + 0,3) corte la línea de sus
  ojos a los ojos del jugador; aguanta el arco tensado.

**Reglas (orden)**, para mobs vanilla cuerpo a cuerpo:
1. Desbandada → RETIRARSE.
2. Poca vida → RETIRARSE.
3. Jugador cargando → RETIRARSE.
4. Aliado aturdido → RODEAR (su hueco).
5. **Cebo**: sin turno, < 40 % de vida, ≥ 2 aliados y a < 6 → REAGRUPARSE.
6. **Relevo**: golpeó hace < 20 ticks, hay otros esperando y está a < 3 → ESPERAR.
7. **Pinza**: FLANCO a < 120° de la mirada del jugador → FLANQUEAR.
8. **Muro de escudos**: sin turno, a < 8 y con escudo en la mano izquierda → CUBRIRSE.
9. Espera de turno (armadura pesada → FLANQUEAR; arma de golpe desde arriba → ESPERAR; si no → RODEAR).
10. ACERCARSE.

**Bloque Forja**, a continuación del de la fase 2:
- `rol_atacante`, `rol_flanco`, `rol_distractor`, `rol_cobertura`, `rol_reserva`
- `hueco_delante`, `hueco_derecha`: el punto de su hueco desde el mob, en su marco, /8, recortado a ±2
- `turnos_ocupados/4`, `tengo_turno`, `aliado_aturdido`, `desbandada`, `otros_esperan`

### Movesets: ataques especiales con aviso (fase 4; `Special`, `SpecialRunner`, `VanillaSpecials`)
- Cada especial tiene: aviso (ticks), enfriamiento aleatorio en [mín, máx] desde que termina y probabilidad por
  tick de que las reglas lo empiecen si está listo y puede.
- Con red, la cabeza `especial` = k (1..4) lo empieza si está disponible.
- Durante el aviso el mob se queda quieto mirando al jugador. Si lo aturden, el especial se corta (y empieza su
  enfriamiento).

| Mob (huecos) | Especial | Puede si | Aviso | Qué hace | Enfr. | Reglas |
|---|---|---|---|---|---|---|
| zombi, husk, aldeano zombi, ahogado (1) | `lunge` embestida | en el suelo, 3,5–7 bloques, lo ve, no es élite legendario, turno libre (lo toma al empezar) | 12, se agacha | salto hacia el jugador: vel. horizontal 0,85, vertical 0,35; si toca: golpe + Lentitud II 30 ticks; acaba al aterrizar (tick > 3) o a los 25 | 100–200 | 0,3 |
| ahogado con tridente, en el agua (2) | `estocada_tridente` | en el agua, con tridente, 3–8, lo ve | 12, burbujas | impulso hacia los ojos del jugador ×1,0; si toca: golpe + 2; acaba al tocar o a los 12 | 100–180 | 0,06 |
| araña, araña de cueva (1) | `salto_arana` | en el suelo, 2,5–6, lo ve | 10, se agacha | salto 0,7 horizontal, 0,45 vertical; si toca: golpe | 60–120 | 0,06 |
| araña (2) | `telarana` | 4–12, lo ve, el jugador en el suelo | 15, tinta en sus pies (marca fija al empezar) | telaraña en la marca si es aire; se quita a los 100 ticks | 160–260 | 0,02 |
| esqueleto, stray, bogged (1) | `paso_atras` | en el suelo, < 3, con arco | 4 | salto atrás 0,6 horizontal, 0,35 vertical; al tick 5, disparo de fuerza 0,8 si lo ve | 80–140 | 0,15 |
| esqueleto… (2) | `andanada` | 6–16, lo ve, con arco, nadie en la línea | 30, brillo y tensado | 3 flechas de fuerza 1,0 giradas −10°, 0° y +10° | 200–320 | 0,02 |
| enderman (1) | `teletransporte` | 3–16, jugador en el suelo | 12, portal y sonido en el destino | destino = 2 bloques detrás del jugador (según su mirada al empezar); allí, aviso normal de 8 y golpe si alcanza | 160–240 | 0,04 |
| vindicador (1) | `hachazo` | ≤ 3, lo ve | 16, polvo rojo | si está a ≤ 3,5: rompe cualquier guardia levantada (100 ticks de enfriamiento del escudo), golpe + 3 | 100–160 | 0,06 |
| bruja (1) | `pocion_marcada` | 4–12, lo ve | 20, anillo de radio 1,5 en la marca | poción arrojadiza (daño, lentitud o veneno) lanzada a la marca, no al jugador | 80–140 | 0,05 |
| piglin bruto (1) | `carga_bruto` | en el suelo, 4–10, lo ve | 15, rugido y agachado | carrera recta 0,9/tick hasta 15 ticks o chocar; si toca: golpe + 2, empuje 1,2 y vertical 0,45, Lentitud III 40 ticks | 140–220 | 0,05 |
| creeper (1) | `acecho` | 5–12 y el jugador no lo mira (cos < 0,3) | 0 | agachado y en silencio, ruta al jugador a 0,8; humo cada 10 ticks; acaba a los 80, a < 3 o si el jugador lo mira (cos > 0,6) | 200–300 | 0,05 |

**Bloque Forja**, a continuación: `esp1_disponible`, `esp1_enfriamiento` … `esp4_disponible`, `esp4_enfriamiento`
(enfriamiento = lo que falta / máx., 1 si no hay especial en ese hueco), `especial_en_curso`, `avisando`,
`aviso_progreso` (0..1, del golpe normal o del especial).

### Defensa de los mobs (fase 5; `MobDefense`)
- **Escudos al aparecer**: zombis y su familia, con probabilidad `shieldChance` (0,08) × dificultad.amenaza, llevan
  escudo vanilla en la mano izquierda (se suelta con un 5 %).
- **Parada**: golpe cuerpo a cuerpo de un jugador, de frente, con el escudo del mob levantado hace ≤ **4** ticks:
  - se anula
  - el jugador sufre Lentitud II y Debilidad I durante 30 ticks, y empuje 0,6 (vertical 0,2)
  - el mob gana **contraataque** durante 40 ticks: su próximo golpe avisa solo **4** ticks
- **Bloqueo**: el escudo levantado más tiempo para el golpe (vanilla), pero el mob recibe en la postura el
  **70 %** del daño. Si eso lo aturde → **guardia rota**: baja el escudo y no puede levantarlo en **60** ticks.
- **Esquiva**: salto lateral (lado al azar) 0,6 horizontal y 0,25 vertical. Da **6** ticks de invulnerabilidad a
  golpes con atacante; enfriamiento **60**; solo en el suelo.
- **Cabeza `defensa`**: 1 = levantar el escudo (si lo tiene y no está roto) mientras lo pida; 2 = esquivar.
  CUBRIRSE levanta el escudo.

**Reglas** añadidas (antes de las de grupo):
- Jugador cargando a < 4: con escudo → CUBRIRSE; si no, a < 3 y con la esquiva lista → esquiva; si no → RETIRARSE.
- Postura > 70 %, con algún aliado, a < 6 → RETIRARSE (para recuperarla).
- Jugador tensando un arco, con escudo y a > 3 → CUBRIRSE (avanza cubierto).
- El muro de escudos no se forma con la guardia rota.

**Bloque Forja**, a continuación:
- `yo_postura` (0..1), `yo_aturdido/40`, `yo_resistencia_aturdimiento`
- `yo_escudo`, `yo_escudo_arriba`, `yo_guardia_rota`
- `yo_esquiva_lista`, `yo_esquivando`, `yo_contraataque`
- `amenaza_normal`, `amenaza_veterano`, `amenaza_elite`, `amenaza_campeon`, `yo_jefe`

### Mobs de Forja (fase 6; `ForjaTraits`, `ForjaFamily`, `ForjaSpecials`)
**Rasgos**:
- **Postura por tipo** (multiplicador de lo que entra en la postura):
  - Yunque Andante y Autómata: golpe ×1,2; corte y perforación ×0,2. Postura máxima ×2 (yunque) y ×1,8 (autómata).
  - Guardián de Cuño: golpe ×1; el resto ×0,5; máxima ×1,5.
  - **Herrero Caído**: ×0,1 salvo en los **40** ticks después de que caiga uno de sus golpes pesados (revés u onda),
    en los que es ×1,5.
- **Autómata**: recibe el aviso de 8 ticks de Forja y finta con probabilidad min(0,6; 0,8·max(parada, esquiva) del
  jugador).
- **Escoria Viviente**: un golpe de corte ≥ 3 de un jugador la divide si su tamaño es > 1 (máx. 2 veces). Ella baja
  un tamaño y aparece un trozo del mismo tamaño, los dos con la misma proporción de vida. Si trozo y madre pasan
  100 ticks sin golpes, se buscan y, a < 2 bloques, se funden (+1 tamaño).
- **Enjambre de Herrumbre**: si un escudo lo para, el escudo pierde 4 de durabilidad extra.
- **Pavesa**: con el jugador a < 4 → RETIRARSE (sigue dejando su rastro de fuego).
- **Coraza Vacía**: la primera vez que un golpe de jugador la dejaría por debajo del 30 %, se queda al 30 % y se hace
  la muerta 60 ticks (quieta e invulnerable). Luego se teletransporta 2 bloques detrás del jugador más cercano
  (≤ 16), avisa y vuelve a pelear.
- **Percutor**: cualquier parada a tiempo lo aturde (no hace falta que sea perfecta).
- **Tenaza**: un golpe contundente ≥ 3 de un jugador le hace soltar lo que sujeta.
- **Núcleo Estelar**: el aviso de su descarga dura 18 × (0,5 + 0,5·vida/vida máx.), mínimo 9.

**Familias de red** (`red_<familia>.json`):

| Familia | Mobs |
|---|---|
| `forja_cuerpo` | Coraza, Percutor, Tenaza, Molde Roto |
| `forja_distancia` | Templador |
| `forja_area` | Cargador de Carbón, Núcleo Estelar, Ascua Mayor, Pavesa |
| `forja_tanque` | Yunque Andante, Autómata, Guardián de Cuño |
| `forja_enjambre` | Herrumbre, Escoria |
| `forja_jefe` | Herrero Caído |

**Especiales** para las redes: arrancan un movimiento propio del mob, que ya trae su aviso; con reglas no se usan,
porque su IA propia ya los lanza.

| Mob | Hueco 1 | Hueco 2 |
|---|---|---|
| Herrero | `reves_herrero` (≤ 4; 60–100) | `onda_herrero` (≤ 12; 160–240) |
| Autómata | `pisoton_automata` (≤ 5; 100–160) | `vapor_automata` (≤ 3; 200–300) |
| Coraza | `embestida_coraza` (3–9; 100–160) | `lamento_coraza` (≤ 16; 300–400) |
| Pavesa | `picado_pavesa` (3–12; 100–160) | |
| Percutor | `martillazo_percutor` (≤ 4; 80–140) | |
| Tenaza | `agarre_tenaza` (≤ 9; 120–200) | |
| Núcleo | `descarga_nucleo` (≤ 20; 120–200) | |
| Cargador | `carga_carbon` (≤ 3; 400) | |
| Yunque | `soldar_yunque` (≤ 10; 200–300) | |

**Bloque Forja**, a continuación:
- `tipo_forja_<id>` (15, en el orden de `ForjaFamily.TYPES`: herrero_caido, automata_de_forja, coraza_vacia,
  pavesa, herrumbre, ascua_mayor, escoria_viviente, yunque_andante, percutor, tenaza, cargador_de_carbon,
  templador, nucleo_estelar, molde_roto, guardian_de_cuno)
- `yo_abierto`, `yo_sujetando`, `yo_tamano/3`

### Movimiento (fase 7; `Terrain`, `MovementGoals`)
- **Peligro** en un punto = lava en la celda del suelo, o caída ≥ 3. El "suelo" de un punto es una sola columna.
- **Nunca un paso al peligro** (52): en LIBRE, las direcciones fijas no se dan si el punto a 1,5 bloques en esa
  dirección es peligroso.
- **Empujar al peligro** (53): cuando el golpe cuerpo a cuerpo de un mob llega a un jugador, si hay peligro a 1,5 o 3
  bloques del jugador (8 direcciones), el jugador recibe un empuje extra de 0,35 (vertical 0,05) hacia allí.
- **Terreno alto** (51, arqueros con reglas): cada 100 ticks (el primero a los 200) busca un punto a 3 o 6 bloques
  (8 direcciones) ≥ 1,9 más alto y sin peligro. Si el jugador está a 8–16 y no está tensando, va allí a 1,1 durante
  hasta 60 ticks.
- **Abrirse paso y pilar** (54, 55; zombis; solo con la regla `mobGriefing`):
  - **Excavar**: bloque blando (hojas, lana, arena, tierra, heno, nieve, grava) a 0,9 bloques hacia el jugador, a la
    altura de los pies o de la cabeza. Lo excava en 20 ticks y lo rompe, soltando el objeto.
  - **Pilar**: si el jugador está ≥ 2,5 más alto y a ≤ 3 en horizontal, salta y pone tierra bajo sí al caer (máx. 4
    bloques). Los bloques se quitan a los 200 ticks.
- **Zigzag** (58) y **parapeto** (59), con el jugador tensando un arco y el mob sin escudo:
  - a > 8 bloques: táctica PARAPETARSE (nueva, índice 8), hacia un punto a 2 o 4 bloques (8 direcciones), a ±1 de
    altura, sin peligro, desde cuyos ojos un bloque tapa los ojos del jugador. Se busca cada 20 ticks; si no hay
    ninguno, RETIRARSE.
  - a 3–8 bloques: LIBRE, alternando mover 2 y 8 cada 10 ticks (zigzag).
- **Distancia de la lanza** (57): un mob con arma de estocada a < 1,8 retrocede (mover 5).
- **Rastro** (60): el mob recuerda dónde vio a su jugador por última vez (rayo de bloques a sus ojos). Si pierde el
  objetivo, va a ese punto durante hasta 200 ticks.
- Paredes (56): lo hace el buscador de rutas vanilla.

### Memoria y personalidad (fase 8; `Personality`, todo en etiquetas de entidad)
- **Rasgo** (61), sorteado al aparecer: agresivo 0,40, prudente 0,25, cobarde 0,15, astuto 0,20. De noche (63) se
  suma 0,3·parada del jugador más cercano (≤ 64) a astuto y 0,3·esquiva a agresivo.
  - **Retirada por vida**: agresivo < 10 %, prudente < 25 %, cobarde < 40 %, astuto < 20 %.
  - **Esperando turno**: prudente y cobarde → ESPERAR; astuto → FLANQUEAR; agresivo → RODEAR.
  - **Fintas vanilla**: astuto +0,15 (máx. 0,65).
- **Peleas sobrevividas** (64): cada vez que un mob vivo pierde a su jugador cuenta +1 (`forja_peleas_N`); a la
  tercera, si era normal, pasa a veterano (con nombre).
- **Rencor** (62): si estaba herido al terminar la pelea, guarda rencor a ese jugador (`forja_rencor_<uuid>`). Sin
  objetivo, cada 20 ticks, si lo ve a < 16, va a por él.
- **Daño a un jugador** × (1 + 0,05·min(peleas, 6)) × 1,15 si le guarda rencor × 1,05 si es agresivo × 1,1 si está
  en casa.
- **Miedo** (65): si un jugador mata 3 hostiles en 200 ticks, los hostiles a ≤ 12 del último (salvo agresivos, élites
  y campeones) → RETIRARSE durante 100 ticks.
- **Intrépidos** (66): élites, campeones y jefes nunca se retiran por desbandada, miedo ni vida.
- **Rivalidad** (67): la llamada de ayuda solo llega a los del mismo bando (mobs de Forja o vanilla).
- **Casa** (68): los que aparecen por estructura, generador, generador de pruebas o generación del mundo guardan su
  punto (`forja_hogar_x_y_z`). A ≤ 24 bloques de él no se retiran y pegan ×1,1. Sin objetivo y a > 16, vuelven (0,9).
- **Aburrimiento** (69): 600 ticks sin ver a su objetivo → lo olvidan.
- **Curiosidad** (70): usar de noche un bloque de Forja hace ruido durante 200 ticks; los hostiles sin objetivo a
  ≤ 24 van a mirar (0,9).

**Premios por personalidad** (para el entrenamiento; la red lo ve por `rasgo_*`, `veterania` y `rencor`):

| Rasgo | Daño hecho | Daño recibido | Otros |
|---|---|---|---|
| agresivo | ×1,5 | ×1 | — |
| prudente | ×1 | ×2 | — |
| cobarde | ×1 | ×2,5 | +0,1 por decisión que sigue vivo con < 40 % de vida y a > 8 del jugador |
| astuto | ×1 | ×1 | +0,5 por finta que el jugador intenta parar; +0,5 por golpe con el mob a > 110° de la mirada del jugador |

Veteranía y rencor multiplican el término de daño hecho: ×(1 + 0,1·min(peleas, 6)) y ×1,3 con rencor.

**Bloque Forja**, a continuación:
- `rasgo_agresivo`, `rasgo_prudente`, `rasgo_cobarde`, `rasgo_astuto`
- `veterania` (peleas/3, máx. 1), `rencor`, `miedo`, `intrepido`, `en_casa`
- `noche`, `lluvia`, `luz/15`

### Peleas del mundo (fase 9; `WorldFights`, `Duels`)
- **Asedio** (91):
  - Una vez por minuto, por jugador, de noche: con un bloque de Forja a ≤ 16, probabilidad `siegeChance` (0,03)
    × dificultad.amenaza. También con `/forja ia asedio`.
  - Llegan 6 + noches/10 (máx. 10) mobs a 30 bloques, en abanico (uno de cada tres esqueleto, el resto zombis). El
    primero es élite; todos tienen como casa la forja (o la posición del jugador) y a él como objetivo.
- **Ladrones** (92, 94):
  - Un mob astuto con alguna mano libre que golpea a un jugador tiene un 10 % de robarle un objeto forjado o una
    pieza de la mochila (ranuras 9–35; nunca lo que lleva puesto o en la mano).
  - Si es un arma, la empuña y pelea con ella; si no, la lleva en la otra mano. La suelta al morir (100 %), no
    desaparece solo y huye (RETIRARSE siempre).
- **Hordas de evento** (93): con un evento del cielo activo, los hostiles que aparecen son agresivos y la amenaza
  se multiplica ×1,5.
- **Némesis** (95):
  - Un élite o campeón que termina una pelea vivo queda anotado en el jugador (`tipo|nombre`, máx. 5).
  - Una vez por minuto, de noche, con un 10 % (si no hubo asedio), vuelve el más antiguo: a 26 bloques, con su
    nombre ("X, el que volvió"), como élite (o campeón si lo era), con rencor y a por él. También con
    `/forja ia nemesis`.
- **Duelos** (96):
  - Un élite o campeón a ≤ 10 de su jugador, con ≥ 2 mobs más con el mismo objetivo, reta con probabilidad 0,005
    por decisión.
  - Anillo de 6 bloques de radio en el punto del retador. Los demás mobs MIRAN: táctica ESPERAR a 7–9 bloques, sin
    turno, sin golpe ni especiales.
  - **Aceptar**: entrar en el anillo o golpear al retador desde dentro.
  - **Rechazar**: 100 ticks sin entrar, o dispararle desde fuera. Todos quedan **enfurecidos** (+1 turno).
  - **Trampa**: salir del anillo más de 40 ticks (además, el retador lo recuerda como némesis), golpear a un
    espectador, o que otro jugador golpee al retador. Todos se enfurecen.
  - **Victoria**: los espectadores se asustan, el retador suelta botín extra una vez y el arma gana +10 de maestría.
- **Patrullas** (97): un mob con casa, sin objetivo: a > 16 vuelve (0,9). Cerca de casa, cada ~200 ticks va a un
  punto a 8–12 bloques de ella (0,7).
- **Noche** (98): de noche, un jugador a > 12 bloques con luz < 4 a sus pies no cuenta como visto (vale para el
  rastro y el aburrimiento).
- **Lluvia** (99): bajo la lluvia, las flechas del esqueleto se desvían hasta ±6° en horizontal y ±3° en vertical.
  Los ahogados persiguen fuera del agua también de día.
- **El Herrero recuerda** (100): cuando cae su onda o su revés y el jugador no pierde vida en 10 ticks, se anota un
  fallo en el jugador (`forja_herrero_onda_fallo_N`, `forja_herrero_reves_fallo_N`). Si los fallos de onda superan a
  los de revés en más de 1, abre de cerca (el revés antes que la onda).

**Bloque Forja**, a continuación: `duelo_retador`, `duelo_espectador`, `ladron`, `enfurecido`.

## 4. Contrato final `red_mob_v2` (fase 10)
El contrato exacto está en [red_mob_v2_contrato.json](red_mob_v2_contrato.json). Lo escribe la prueba `writeContract`
(`FORJA_CONTRATO=<archivo> ./gradlew runGameTest`) a partir del propio código, así que no se desincroniza.

**Entradas: 200**
- las 102 de M1 (`ObsM1`), en su orden
- el bloque Forja, 98 entradas (`ObsForja.names()`): jugador, hábitos, dificultad, escuadrón, especiales, el propio
  mob (postura, guardia, esquiva, amenaza), mobs de Forja, personalidad y mundo, y peleas del mundo
- Una red puede tomar solo un prefijo de las 200; sus `nombres_obs` tienen que coincidir con los primeros N.

**Salidas: 29**

| Índices | Cabeza | Tipo |
|---|---|---|
| 0–8 | `mover` | categórica |
| 9 | `saltar` | Bernoulli |
| 10 | `usar` | Bernoulli |
| 11–19 | `tactica` | categórica, índice de `Tactic`: libre, acercarse, rodear, flanquear, esperar, retirarse, reagruparse, cubrirse, parapetarse |
| 20–24 | `especial` | categórica: 0 ninguno, k = hueco k |
| 25–27 | `defensa` | categórica: nada, escudo, esquivar |
| 28 | `fintar` | Bernoulli |

Una red con 11 salidas es v1: tácticas LIBRE y sin cabezas de Forja.

**Máscara** (0 = prohibido; el índice 0 de cada categórica nunca se prohíbe):
- mover 1..8 y usar: prohibidos a un creeper encendido
- saltar: prohibido fuera del suelo y del agua
- especial k: prohibido si no está disponible (en enfriamiento o sin poder empezar)
- defensa 1: prohibida sin escudo o con la guardia rota
- defensa 2: prohibida sin esquiva lista o fuera del suelo
- fintar: prohibido fuera de la primera mitad de un aviso

**Muestreo**: cada cabeza por separado, con temperatura T = `iaTemperatura` × dificultad × amenaza.

**Familias** (10 archivos): `red_cuerpo`, `red_arquero`, `red_creeper`, `red_arana`, `red_forja_cuerpo`,
`red_forja_distancia`, `red_forja_area`, `red_forja_tanque`, `red_forja_enjambre` y `red_forja_jefe`, en
`config/forja/redes/` (o `iaCarpetaRedes`).
