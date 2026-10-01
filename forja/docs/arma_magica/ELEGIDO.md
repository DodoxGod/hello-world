# Armas mágicas nuevas — lo decidido por Andy

1. (2026-09-20) «crea una nueva arma, de tipo mágico, crea 3 modelos y decidiré cuál es la que más me gusta».
   Tres modelos en `tres_modelos.png` (`modelos.py`): A báculo, B cetro rúnico, C grimorio forjado.
2. «C es increíble, pero también me gustó mucho A, haz 3 modelos variantes de A, y guarda C».
   Variantes en `baculo_tres_variantes.png` (`variantes_baculo.py`): A1 garra, A2 media luna, A3 farol.
3. **«A2, quiero que A2 tire proyectiles mágicos y que C tire un área 5 bloques delante del jugador, que el
   área sea del color del círculo del libro y que deje la runa que me dices».**

## Por tanto, DOS armas nuevas, las dos van al mod

### Báculo de media luna (sprite `CRESCENT` de `variantes_baculo.py`)
- Piezas: **asta + engaste + núcleo** (tres capas del sprite: `S`, `E`, `G`; las chispas `+` son adorno).
- Al usarlo **dispara un proyectil mágico**. El color del proyectil es el del núcleo.

### Grimorio forjado (sprite `TOME` de `modelos.py`)
- Piezas: **tapas + cantoneras + lomo + broche** (capas `T`, `C`, `L`, `B`; `*` es el círculo que brilla; `p` las
  páginas, que no se tiñen).
- Al usarlo **lanza un área a 5 bloques delante del jugador**, **del color del círculo del libro** (el del
  broche), y **deja una runa** en el suelo (lo que le propuse: dura unos segundos y hace efecto a lo que la pise).

## Lo que queda a mi criterio (decirle lo que elija, por si quiere cambiarlo)

- De qué depende el color/elemento: del **material del núcleo / del broche** (el mod ya da color y rasgo a cada
  material; los rasgos — Ascua, Cargado… — se aplican al impacto igual que en un golpe).
- Coste: durabilidad por uso + enfriamiento; daño y enfriamiento salen de `ForgeStats` como en las demás armas.
- La zona y la runa se dibujan con `entity/Shockwave` (aviso, estallido, charco), que ya tiñe por color.
- Mesa: forja mayor (como el resto del arsenal avanzado). Plantillas para las piezas nuevas. Capítulo en el
  libro, textos, test (`checkEverythingForges` recorre todos los tipos).

Estado: **HECHO y probado** (2026-09-20, `FORJA_SOLO=magia`). B (cetro rúnico) descartado.

## Cómo quedó (lo que elegí yo, por si Andy quiere cambiarlo)

- Piezas: báculo = **núcleo + engaste + mango** (el asta es el mango de siempre); grimorio = **núcleo + tapas +
  remache** (las cantoneras son los remaches; lomo, páginas y broche van en una capa fija sin teñir). Tres piezas
  nuevas: `NUCLEO`, `ENGASTE`, `TAPAS` (36 en total; la rejilla de la mesa de piezas pasó a 3 × 12).
- Números: proyectil `max(3, 4 + bono × 0,9)`, espera 14 tics, velocidad 1,5 b/tic, vive 40 tics. Área
  `max(3, 5 + bono)`, radio 3, a 5 bloques, espera 70 tics; runa 120 tics, muerde cada 10 con ¼ del daño y
  ralentiza. Cada uso gasta 1 de durabilidad. Las dos son de **mesa mayor**.
- Las mejoras de arma viajan en el proyectil y en la apertura del área (son golpes del arma); los mordiscos de
  la runa no. `magic/Spellcasting` (`land`, `casting`, `blow`), `entity/MagicBolt`, `Shockwave.rune`.
- La runa: aro de doce letras + estrella de ocho puntas de la mesa estelar, girando una contra otra, del color
  del núcleo, con destello a cada mordisco (`onda_runa.png`, `onda_runa_centro.png`, `ShockwaveRenderer.rune`).
- A raíz de esto Andy pidió: **«quiero que nada del mod tenga el brillo por encantamiento»** → hecho
  (`ItemStackMixin.forja$noGlint`).

4. (2026-09-20, tarde) **«también dame mejoras para las armas mágicas»** → HECHO: ocho mejoras (Conjuro veloz,
   Sobrecarga, Resonancia; Prisma y Buscador para el báculo; Tinta indeleble, Vórtice y Santuario para el
   grimorio) y dos sinergias (Enjambre, Colapso). Números y decisiones en el CHANGELOG del mismo día;
   `checkMagicUpgrades` las prueba y `mejora_*.gif` las enseña.

Ideas sin hacer (no pedidas): leyenda mágica, botín con báculos y grimorios, encargos del Forjador con ellos.
