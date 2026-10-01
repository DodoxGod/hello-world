# Armas mágicas — plan de implementación (escrito el 2026-09-20 tras mirar el código)

Lo decidido por Andy está en `ELEGIDO.md`. Esto es CÓMO meterlo en el mod, con los puntos de enganche ya
localizados para no tener que redescubrirlos.

## Piezas y tipos

Reutilizar lo que ya existe para no inflar la mesa de piezas (su rejilla es de 3 × 11 = 33 y **ya hay 33
`PartType`: está llena**; cuatro piezas nuevas obligan a una cuarta fila → `generate_gui_textures` /
`generate_parts_table_textures`, constantes de `ForgeScreen` y posiciones de `ForgeMenu`, y el test
`slotsOffTheirSquares` dirá si cuadra).

| Tipo (`ForgeType`) | Piezas | Notas |
|---|---|---|
| `BACULO` (báculo de media luna) | `NUCLEO` (nueva, HEAD, coste 3) + `ENGASTE` (nueva, EXTRA, coste 2) + `MANGO` (la que hay: es el asta) | Kind nuevo `MAGIA` o `RANGED`; mirar los `switch` sobre `Kind` |
| `GRIMORIO` (grimorio forjado) | `TAPAS` (nueva, PLATE, 4) + `CANTONERAS` (nueva, EXTRA, 2) + `FORRO` (la que hay: el lomo de cuero) + `NUCLEO` (el broche: la misma gema que el báculo) | se usa con la mano; el círculo `*` del sprite brilla con el color del núcleo |

`ForgeType(Kind, piezas, daño, vel. ataque, mult. minado, mult. durabilidad, seg. anular escudo, radio área, cabeza lanzable)`
y `PartType(Role, coste)`. **Las entradas nuevas de los enum van AL FINAL** si el enum viaja por la red por
ordinal (pasó con `Upgrade`); comprobar `ForgeType`/`PartType` (codecs por nombre = da igual).

## Generador (`tools/generate_assets.py`)

- `PARTS` (pieza → papel de capa: HEAD / HANDLE / EXTRA / PLATE / LINING), `TYPES` (tipo → piezas, en el orden de
  las capas), `TOOL_LAYERS` (tipo → función que devuelve `{(x, y): (índice_de_capa, luminancia)}`) y
  `PART_LAYERS` (pieza → sprite suelto, normalmente `centered(sprite_del_tipo(), índice)`).
- Los sprites aprobados están como mapas ASCII en `docs/arma_magica/variantes_baculo.py` (`CRESCENT`: letras S asta,
  E engaste, G núcleo, `+` chispa) y `modelos.py` (`TOME`: T tapas, C cantoneras, L lomo, B broche, `*` círculo,
  `p` páginas). Convertirlos a `{(x, y): (capa, luminancia)}` con el mismo sombreado (`shade_of`). Las páginas y
  el círculo no son piezas: capa fija sin teñir (mirar cómo lo hace otro sprite con partes sin tinte) o teñirlas
  con el núcleo.
- **Minecraft agarra un objeto por la esquina inferior izquierda del sprite**: el báculo ya está dibujado así.
- `check_enums()` exige que `TYPES`/`PARTS` casen con los enum de Java: correr el generador tras tocarlos.

## Java

- `forge/ForgeType.java`, `part/PartType.java`: entradas nuevas.
- Uso con clic derecho: mirar `upgrade/WeaponThrow.java` (gancho, dagas lanzables: se engancha a `use`) y
  `upgrade/HeadThrow.java`; enfriamiento con `player.getCooldowns().addCooldown(stack, ticks)`.
- **Proyectil del báculo**: entidad nueva ligera (ver `entity/` y `registry/ModEntities.java`: `flecha_forjada`,
  `cabeza_lanzada` son proyectiles propios) + su render en `client/`. Color = `ForgeMaterial.color` del núcleo;
  al impactar, daño mágico según `ForgeStats` y los rasgos del material por `TraitEffects` como en un golpe.
- **Área del grimorio**: punto a 5 bloques delante (raycast horizontal + `Shockwave.floorAt`), `Shockwave.burst(...)`
  para el estallido y un charco/runa que dure (`Shockwave.pool` / `telegraph` aceptan color); daño por segundo a
  lo que la pise. Color = el del núcleo (que es el del círculo del libro). Textura de runa nueva en
  `generate_shockwave_textures()` si el charco no basta.
- Mesa: `Station.canForge` (arsenal avanzado = forja mayor). `ForjaCommand` kit. `GuideBookScreen` (lista de
  especiales en ~1479 y capítulo). `generate_lang.py` (nombres de tipo en la línea ~83, piezas, textos del libro).
- Mejoras propias (opcional, después): alcance del hechizo, eco, sobrecarga…

## Test

`checkEverythingForges` recorre todos los `ForgeType`: deberían forjarse sin tocar nada. Añadir una comprobación
de uso (que el báculo dispare y el grimorio deje su área a 5 bloques) y capturas/GIF para Andy.
