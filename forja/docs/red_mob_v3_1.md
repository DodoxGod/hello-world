# Contrato de la red de mobs: revisión v3.1 (compatible con v3)

`red_mob_v3_contrato.json` no cambia: siguen siendo 280 entradas y 34 salidas, con los mismos nombres y en el mismo
orden. La v3.1 solo cambia **qué número recibe una entrada**, `yo_arma_alcance/6`, y solo para las redes que lo
piden.

## El campo `"alcance_v"`

| En el JSON de la red | `yo_arma_alcance` (antes de dividir entre 6) | Para quién |
|---|---|---|
| sin el campo, o `"alcance_v": 1` | `ObsM1.REACH` (0,83) + lo que suman los atributos del arma (`entity_interaction_range`) + `max(0, max_reach − 3)` de `attack_range`. Es el número del contrato v3. | Las redes entrenadas hasta hoy. |
| `"alcance_v": 2` | El alcance completo con el que el mob pega de verdad: lo anterior más el extra por tipo de arma (`Reach.actionOf`). Con la lanza, su máximo. | Las redes entrenadas con el alcance real (PROPUESTAS_IA_SIMULADOR.md, sección 8). |

- **Lectura:** `NetBrain` lee el campo (`reachVersion`). `MobAi` se lo pasa al `MobMind` antes de construir la
  observación, y `ObsV3.reach(arma, versión)` elige la fórmula.
- **Juego:** el golpe del mob usa siempre el alcance real, sea cual sea la versión (`Reach.actionExtra`, para
  empezar y acertar el golpe y para colocarse). Solo cambia lo que ve la red.

## La tabla (hueco entre las cajas; `CombatConfig`, una sola copia)

| Arma | Extra | Hueco máximo | Dónde está el número |
|---|---|---|---|
| Puño | 0 | 0,83 | `mobReachFist` |
| Daga | +0,2 | 1,03 | `mobReachDagger` |
| Espada (forjada o vanilla) | +0,6 | 1,43 | `mobReachSword` |
| Hacha, martillo, maza, pico, picahacha | +0,5 | 1,33 | `mobReachAxe` |
| Espadón | +0,9 | 1,73 | `mobReachGreatsword` |
| Guadaña | +0,75 | 1,58 | su atributo (`Assembler.GUADANA_REACH`) |
| Tridente | +0,5 | 1,33 | su atributo (`Assembler.TRIDENTE_REACH`) |
| Lanza | +1,5 | 2,33 (y no pega a menos de 1) | su `attack_range` |
| Mangual | +3 | 3,83 | su atributo (`Assembler.MANGUAL_REACH`) |

Un arma que ya alcanza más por sus propios atributos (guadaña, tridente, lanza o mangual) no suma el extra por tipo.

## Pruebas

- `AlcanceGameTests.yoArmaAlcanceFollowsTheNetworksVersion`: una red falsa sin el campo y otra con
  `"alcance_v": 2`. La espada da 0,83 en la v1 y 1,43 en la v2; la lanza da 2,33 en la v2.
- `AlcanceGameTests.aSwordReachesFurtherThanAFist`: la tabla, y que una espada llega donde los puños no.
