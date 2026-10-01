# Aleaciones cumbre: iracero, égida y arcanio

Pedido de Andy (2026-10-01): **tres aleaciones nuevas que sean lo mejor del mod**. Cada una lleva **corazón de forja**
y **al menos otras dos aleaciones**, con **tres materiales o más**. Cada una es la mejor en algo distinto (ataque,
defensa, magia y utilidad) y ninguna es la mejor en todo.

Es el diseño, escrito antes de programar, con todos los números decididos. Quien lo implemente no tiene que decidir
nada: si algo no cuadra con el código, se para y se pregunta.

## 1. De dónde se parte

| Qué | Dónde | Lo que importa aquí |
|---|---|---|
| Materiales | `material/ForgeMaterial.java` | Orden del constructor: `color, inputTag, inputItem, canBeHead, durability, miningSpeed, attackDamageBonus, incorrectBlocksForDrops, enchantability, handleDurability, handleAttackSpeed, handleMiningSpeed, defense[botas, grebas, pechera, casco], armorDurability, toughness, knockbackResistance, equipSound, trait` |
| Recetas | `forge/Alloys.java` | `ALL`, `WHITE_HEAT_ONLY` (solacero, lunacero, acero vivo) y `FOUNDRY_ONLY` (oricalco). El crisol (`CrucibleBlockEntity.alloy`) gasta los **dos huecos** y saca el resto de las **cubas de su línea**; así ya se cuela el oricalco (14 ingredientes) |
| Crisol de obsidiana | `block/CrucibleBlock.Tier.OBSIDIANA` | el único con calor `FORJA_BLANCA` |
| Corazón de forja | `FallenSmith.die` | **uno por Herrero muerto** (también en la revancha del cementerio). Hoy hace el material `CORAZON` y el acero vivo (1 corazón + 2 damasco → 1) |
| Rasgos | `upgrade/TraitEffects.java`, llamados desde `upgrade/CombatUpgrades.java` (`onHit` y `weaponBonus` en `AFTER_DAMAGE`, `guards` en `ALLOW_DAMAGE`, `onKill`), `FieldUpgrades.armorTick`, `magic/Mana.java` (ASTRAL), `combat/CombatHooks.capped` (tope por golpe) |
| Conjuntos y flechas | `upgrade/ArmorSets.bonuses` (switch sin `default`: falla al compilar si falta un material), `combat/ArrowTips.special` y `onHit`, `entity/ForgedArrow.onHitEntity` |

**La cima de hoy** (de `ForgeMaterial` y de `docs/EQUILIBRIO.md`):

| | Daño cabeza | Durab. | Minado | Encant. | Mango dur./ataque/minado | Armadura | Dur. arm. | Dureza | Empuje | Rasgo / conjunto |
|---|---|---|---|---|---|---|---|---|---|---|
| Corazón | **+4,5** | **2400** | 9,5 | 25 | ×1,60 / +0,10 / ×1,15 | 3/7/8/3 = 21 | **48** | **3,5** | 0,10 | Llanto / +8 vida, +4 dureza |
| Acero vivo | +4,5 | 2200 | 9,0 | 24 | ×1,55 / +0,10 / ×1,10 | 21 | 46 | 3,5 | 0,10 | Vivo |
| Solacero / lunacero | +4,0 | 1700 | 9,0 / 8,0 | 20 / 22 | ×1,45 | 20 | 40 | 3,0 | 0,05 | Solar / Nocturno |
| Oricalco | +3,5 | 1650 | 8,5 | **30** | ×1,30 / +0,15 / ×1,10 | 20 | 36 | 2,5 | 0,05 | Astral |
| Obsidiacero | +3,5 | 1900 | 6,0 | 8 | **×1,70** / −0,30 / ×0,85 | 21 | 44 | 2,0 | **0,15** | — |
| Vidriacero / voltaico | +3,0 / +2,0 | 900 / 480 | 8,5 / 9,5 | 18 / 24 | ×0,75–0,90 / **+0,30** / ×1,15 | — | — | — | — | — |
| Oro | +0 | 32 | **12,0** | 22 | | | | | | |

Lo que dice EQUILIBRIO.md y hay que respetar:
- De 60 mejores armas, damasco y vidriacero están en 50 y el corazón en 32. Las cumbre van a entrar; es lo que se pide.
- `ArmaduraGameTests`: ningún conjunto pasa de **netherita con Protección IV + 5 puntos**. Hoy el peor es el
  obsidiacero (21 de armadura, empuje 0,15): +3,9. La dureza no cuenta ahí; la armadura plana del conjunto, sí.
- `equilibrioSinDominados` compara Pareto solo entre materiales del mismo rasgo; los tres rasgos son nuevos.
- `magiaEnSuSitio` mide contra la mediana cuerpo a cuerpo; `herreroEnSuSitio`, la pelea con la mejor arma.

## 2. Las tres aleaciones

### 2.1 Resumen

| | **Iracero** (ataque) | **Égida** (defensa y aguante) | **Arcanio** (magia y utilidad) |
|---|---|---|---|
| id / enum | `iracero` / `IRACERO` | `egida` / `EGIDA` | `arcanio` / `ARCANIO` |
| Nombre es / en | Iracero / Wrathsteel | Égida / Aegis | Arcanio / Arcanium |
| Color | `0xB0142C` (carmesí; el lingote corre de `0x7A0E1E` a `0xF2B640`) | `0x8E6B3F` (bronce viejo de escudo) | `0xE04FB0` (magenta arcano) |
| Receta | 1 corazón de forja + 1 acero vivo + 2 solacero | 1 corazón de forja + 2 obsidiacero + 2 magmacero | 1 corazón de forja + 2 oricalco + 2 eterio |
| Materiales distintos | 3 (2 aleaciones) | 3 (2 aleaciones) | 3 (2 aleaciones) |
| Salen | **2** lingotes | **1** lingote | **1** lingote |
| Corazones por lingote | 1 (el del acero vivo y el suyo) | 1 | 1 |
| Rasgo | **Iracundo** | **Inquebrantable** | **Místico** |
| Mejor que nadie en | daño de cabeza (+5,5) | durabilidad, mango, armadura (durab. y dureza), golpe máximo recibido | encantabilidad, minado, mango rápido, maná |
| Peor que el corazón en | durabilidad | daño | daño y durabilidad |

**Regla de coste:** un corazón por lingote, como el acero vivo, y encima aleaciones caras. Ninguna cumbre sale más
barata que el acero vivo, que sigue teniendo sentido: no pide más que damasco y es el único con Vivo.

### 2.2 Dónde y cómo se hacen

- **Crisol de obsidiana** (calor `FORJA_BLANCA`) **en una línea de fundición**: dos ingredientes en los dos huecos del
  crisol y el tercero (o los que falten) en cubas de su línea, como el oricalco y el voltaico. Ninguna mesa llega
  (no hay calor blanco bajo una mesa) y la montadora tampoco.
- **Cómo se cargan** (lo dirá el libro): el **corazón en un hueco** y **la otra aleación de cantidad más baja en el
  otro** (iracero: corazón + 1 acero vivo, solacero en una cuba; égida: corazón + 2 obsidiacero, magmacero en una
  cuba; arcanio: corazón + 2 oricalco, eterio en una cuba). Con los dos huecos llenos ninguna otra receta casa (el
  acero vivo pide damasco en el hueco).
- Las aleaciones de fragua lejana (magmacero, eterio) **entran** en las cubas como cualquier lingote de metal
  (`MeltTankBlockEntity.holds`: material no básico). Lo que el crisol no acepta son sus ingredientes de cantera.
- **Colada:** las tres pasan de 1600 de durabilidad, así que piezas solo en **mesa de almas** (como el oricalco).
- Lingotes **épicos y a prueba de fuego**, como el corazón.

### 2.3 Números (`ForgeMaterial`, en este orden, detrás de `ETERIO`)

```java
	// ------------------------------------------------ the peak alloys (docs/ALEACIONES_CUMBRE.md)
	/** Wrathsteel: the heart poured with living steel and sun steel. The hardest-hitting head in the mod, and it hits harder the closer you are to dying. */
	IRACERO(0xB0142C, alloyTag("iracero"), null, true, 2000, 9.5F, 5.5F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		18, 1.40F, 0.10F, 1.10F, new int[]{3, 6, 8, 3}, 42, 3.0F, 0.05F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.IRACUNDO),
	/** Aegis: the heart poured with obsidian steel and magmasteel. It outlasts everything, and no single blow gets through it whole. */
	EGIDA(0x8E6B3F, alloyTag("egida"), null, true, 2800, 8.0F, 4.0F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		15, 1.85F, -0.25F, 0.90F, new int[]{3, 7, 8, 3}, 60, 5.0F, 0.15F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.INQUEBRANTABLE),
	/** Arcanium: the heart poured with orichalcum and aetherium. The quickest hand, the best enchanting and spells for less. */
	ARCANIO(0xE04FB0, alloyTag("arcanio"), null, true, 2100, 12.5F, 4.0F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		40, 1.30F, 0.35F, 1.25F, new int[]{3, 6, 8, 3}, 44, 3.0F, 0.0F, SoundEvents.ARMOR_EQUIP_GOLD, Trait.MISTICO);
```

| Número | Iracero | Égida | Arcanio | Récord anterior | Por qué |
|---|---|---|---|---|---|
| Daño de cabeza | **+5,5** | +4,0 | +4,0 | corazón +4,5 | iracero, un punto sobre el corazón: el ataque es lo suyo. Las otras dos, bajo el corazón y sobre el solacero |
| Durabilidad | 2000 | **2800** | 2100 | corazón 2400 | égida es la que más dura; iracero y arcanio, bajo el corazón y el acero vivo |
| Minado | 9,5 | 8,0 | **12,5** | oro 12,0 | arcanio, la herramienta |
| Encantabilidad | 18 | 15 | **40** | oricalco 30 | arcanio, la magia |
| Mango: durab. | ×1,40 | **×1,85** | ×1,30 | obsidiacero ×1,70 | égida, el mango que no se rompe (y lento) |
| Mango: ataque | +0,10 | −0,25 | **+0,35** | vidriacero/voltaico +0,30 | arcanio, el mango rápido |
| Mango: minado | ×1,10 | ×0,90 | **×1,25** | ×1,15 | arcanio |
| Armadura | 20 | 21 | 20 | corazón 21 | **nadie pasa de 21**: `ArmaduraGameTests` |
| Durab. de armadura | 42 | **60** | 44 | corazón 48 | égida |
| Dureza | 3,0 | **5,0** | 3,0 | corazón 3,5 | égida; la dureza no cuenta en `ArmaduraGameTests` |
| Empuje | 0,05 | 0,15 | 0 | obsidiacero 0,15 | égida igual que el obsidiacero y no más: con 21 de armadura y 0,15 queda en su fila (+3,9 sobre netherita P4) |

**Ninguna es mejor en todo:** ninguna es igual o mejor que el corazón en daño, durabilidad, minado, encantabilidad,
mango (durab. y ataque), armadura, durab. de armadura y dureza a la vez; ni que otra cumbre (lo comprueba la
prueba 4.1, `ningunaEsMejorEnTodo`).

### 2.4 Rasgos

Tres valores nuevos de `ForgeMaterial.Trait`, al final de la enum, con su javadoc de una línea:

```java
		/** It hits harder the closer its bearer is to dying; armour of it answers a bad wound with strength. */
		IRACUNDO,
		/** No single blow takes more than a share of its bearer's health; the more of it you carry, the smaller the share. */
		INQUEBRANTABLE,
		/** Spells cost a quarter less with it in hand; armour of it turns wounds into mana, tools turn work into mana. */
		MISTICO;
```

Todas las constantes van en `TraitEffects`, `public static final`, con estos nombres y valores.

**Iracundo (iracero).** Nada parecido: Ejecución mira la vida del *objetivo*, Frenesí/Furia los golpes seguidos.

| Dónde | Efecto exacto | Constantes |
|---|---|---|
| Escalones | `wrathSteps(LivingEntity e)` = `min(WRATH_MAX_STEPS, floor((1 − vida/vidaMax) / WRATH_STEP + 1e-4))`. Vida llena 0; 79 % → 1; 59 % → 2; 39 % → 3; ≤ 20 % → 4 | `WRATH_STEP = 0.20F`, `WRATH_MAX_STEPS = 4` |
| Armas (cuerpo a cuerpo, lanzadas, hechizos de báculo/grimorio: todo lo que pasa por `weaponBonus`) | `+WRATH_DAMAGE × wrathSteps(atacante)` de daño extra; partículas `ParticleTypes.FLAME`, `2 × pasos` | `WRATH_DAMAGE = 1.0F` (hasta +4,0) |
| Armadura (al ser herido por una entidad) | si quedas por debajo de `WRATH_ARMOR_THRESHOLD` de vida y hay ≥ 1 pieza: **Fuerza I** `WRATH_STRENGTH_TICKS`; con las 4 piezas, **Fuerza II**. Una vez cada `WRATH_COOLDOWN` por portador (mapa UUID → tick como `SOUL_GUARDS`, barrido a > 256). Sonido `SoundEvents.RAVAGER_ROAR`, 0,5 / 1,4 | `WRATH_ARMOR_THRESHOLD = 0.40F`, `WRATH_STRENGTH_TICKS = 120`, `WRATH_COOLDOWN = 600` |
| Herramientas | nada | |
| Flecha (especial **IRA**) | daño base extra `WRATH_ARROW_STEP × wrathSteps(tirador)`, solo ese golpe | `ArrowTips.WRATH_ARROW_STEP = 0.5F` (hasta +2,0) |

**Inquebrantable (égida).** Es el tope por golpe que ya tienen los mobs (`CombatHooks.capped`), pero para quien lleva la
égida. Nada en el mod lo hace para un jugador (Animado anula un golpe cada 30 s; Del End esquiva al azar).

| Dónde | Efecto exacto | Constantes |
|---|---|---|
| Cuenta | `unyieldingCount(e)` = piezas de armadura enteras con el rasgo + 1 si la mano principal **o** la secundaria lleva algo entero con el rasgo; máx. 5 | |
| Tope | `unyieldingShare(n)` = `1.0F` si n = 0; si no `UNYIELDING_BASE − UNYIELDING_PER × n`: 1 → 40 %, 2 → 35 %, 3 → 30 %, 4 (conjunto) → 25 %, 5 (conjunto y escudo o arma) → 20 % | `UNYIELDING_BASE = 0.45F`, `UNYIELDING_PER = 0.05F` |
| Qué golpes | lo que queda **después de la armadura** no pasa de `vidaMax × tope`. Solo daño que la armadura lee: se salta si la fuente es `DamageTypeTags.BYPASSES_ARMOR` o `BYPASSES_INVULNERABILITY` (caídas, vacío, `/kill`, hambre, ahogo, magia pura no se topan) | |
| Aviso | cuando el tope corta algo: `ParticleTypes.WAX_ON` 10 alrededor del pecho y `SoundEvents.SHIELD_BLOCK` 0,6 / 0,8 (solo en servidor) | |
| Flecha (especial **GUARDIA**) | al acertar, el tirador gana **Resistencia I** `GUARD_ARROW_TICKS` | `ArrowTips.GUARD_ARROW_TICKS = 60` |

Con 20 de vida y el conjunto: ningún golpe que lea armadura quita más de 5 corazones de 10. Contra el Herrero no
cambia casi nada (su golpe más grande es el 17 % con la armadura de referencia); contra un warden, un creeper o
un devastador, sí: es la armadura para *no morir de un golpe*, no para que nada duela.

**Místico (arcanio).** Astral es regeneración continua; Místico es precio y maná por acción. Sifón devuelve maná al
*acertar un hechizo*; esto, al *recibir* golpes y al *trabajar*.

| Dónde | Efecto exacto | Constantes |
|---|---|---|
| En la mano (principal o secundaria, entero) | todo hechizo cuesta ×`MYSTIC_COST`. Una vez, aunque lleves dos | `MYSTIC_COST = 0.75F` |
| Armadura (al ser herido por una entidad, jugador, no `Mana.exempt`) | `Mana.give(min(MYSTIC_HURT_CAP, dañoRecibido × MYSTIC_HURT_MANA × piezas))`; partículas `ParticleTypes.ENCHANT` 6 | `MYSTIC_HURT_MANA = 0.5F`, `MYSTIC_HURT_CAP = 6.0F` |
| Herramientas | cada bloque roto con dureza > 0: `Mana.give(MYSTIC_BLOCK_MANA)` | `MYSTIC_BLOCK_MANA = 0.25F` |
| Barra de maná | `Mana.usesMana` es `true` con Místico (como con Astral) | |
| Flecha (especial **ARCANA**) | al acertar: `target.invulnerableTime = 0` y `hurtServer(level, damageSources().indirectMagic(flecha, tirador), ARCANE_ARROW_DAMAGE)`; la armadura no lo para | `ArrowTips.ARCANE_ARROW_DAMAGE = 2.0F` |

### 2.5 Conjuntos (`ArmorSets.bonuses`, además del +2 armadura / +2 dureza de todo conjunto)

| Material | Bonos (`Bonus.add` salvo `%`) | Otro sitio | Por qué |
|---|---|---|---|
| `IRACERO` | `ATTACK_DAMAGE` +3,0; `MOVEMENT_SPEED` +5 % (`Bonus.percent`) | Fuerza II en el rasgo | el mejor bono de daño (damasco, solacero y lunacero dan +2) |
| `EGIDA` | `MAX_HEALTH` +10; `ARMOR_TOUGHNESS` +4,0; `EXPLOSION_KNOCKBACK_RESISTANCE` +1,0 | tope 25 % en el rasgo | **sin `ARMOR` plano** (rompería `ArmaduraGameTests`); la vida más alta (corazón +8) |
| `ARCANIO` | `ARMOR_TOUGHNESS` +2,0; `MOVEMENT_SPEED` +5 % | `Mana.ARCANIUM_SET_MANA = 50.0F` en `Mana.maxOf`; añadir `ARCANIO` a la lista de `Mana.carriesMana` | el mayor maná de un conjunto (amatista +40, oricalco +25) |

### 2.6 Arte (`tools/lingotes.py`)

Variante A, en `mark_a`, detrás de la rama de `"oricalco"`:

```python
    elif name == "iracero":
        # Wrathsteel: a seam of the heart's fire breaking through, gold over a black channel, two sparks thrown off it.
        for (x, y) in axis(7.6):
            zig = 1 if x % 2 == 0 else 0
            put(x, y + zig, colour=(255, 214, 120))
            put(x, y + zig + 1, 0)
        for (x, y) in ((5, 5), (11, 4)):
            put(x, y, colour=(255, 120, 60))
    elif name == "egida":
        # Aegis: a round shield boss in the middle of the top, lit rim over a dark ring, a rivet at each end.
        for (x, y) in ((7, 5), (9, 5), (8, 4)):
            put(x, y, 4)
        put(8, 5, colour=(255, 236, 190))
        for (x, y) in ((7, 6), (9, 6), (8, 6)):
            put(x, y, dark)
        for (x, y) in ((4, 7), (12, 4)):
            put(x, y, colour=(255, 236, 190))
            put(x, y + 1, dark)
    elif name == "arcanio":
        # Arcanium: a small rune of three lit points round a point of enchanting blue, a shadow under each.
        for (x, y) in ((6, 6), (10, 4), (8, 7)):
            put(x, y, colour=(255, 220, 250))
            put(x, y + 1, dark)
        put(8, 5, colour=(120, 230, 255))
```

Variante B (kit), en `GLYPHS`, con un comentario `# The peak alloys (docs/ALEACIONES_CUMBRE.md).`:

| Metal | Glifo (4 × 3) | Lectura |
|---|---|---|
| `iracero` | `(".#.#", "#.#.", "####")` | llamas sobre una base |
| `egida` | `("####", "#..#", ".##.")` | un escudo |
| `arcanio` | `(".##.", "#..#", "#..#")` | un arco de runa |

Los tres son distintos de todos los de `GLYPHS` (comprobado). Si al mirarlos a 1× alguno se pierde, se dice en el
informe y no se cambia sin preguntar.

## 3. Archivos que tocar

| # | Archivo | Qué |
|---|---|---|
| 1 | `material/ForgeMaterial.java` | las tres entradas de 2.3 detrás de `ETERIO` (su `;` pasa a `,`); los tres rasgos de 2.4 al final de `Trait`. `displayStack` no se toca (`ModItems.alloy` las encuentra) |
| 2 | `forge/Alloys.java` | en `ALL`, detrás de `eterio`, bajo `// ---- the peak alloys (docs/ALEACIONES_CUMBRE.md): the forge heart and two alloys, white heat on a foundry line.`: `new Recipe("iracero", Heat.FORJA_BLANCA, List.of(new Part(() -> ModItems.CORAZON_DE_FORJA, 1), new Part(() -> ModItems.alloy("acero_vivo"), 1), new Part(() -> ModItems.alloy("solacero"), 2)), 2)`; `egida`: corazón 1, `alloy("obsidiacero")` 2, `alloy("magmacero")` 2 → 1; `arcanio`: corazón 1, `ModItems.ORICALCO` 2, `alloy("eterio")` 2 → 1. Añadir los tres a `WHITE_HEAT_ONLY` y a `FOUNDRY_ONLY`. Nuevo `public static final Set<String> PEAK = Set.of("iracero", "egida", "arcanio")`. Corregir el comentario de la línea «These three have two ingredients apiece on purpose» (las cumbre usan cubas) |
| 3 | `registry/ModItems.java` | en el bucle de `Alloys.ALL`: si `Alloys.PEAK.contains(id)`, `new Item.Properties().rarity(Rarity.EPIC).fireResistant()` |
| 4 | `upgrade/TraitEffects.java` | constantes y métodos de 2.4: `wrathSteps`, `unyieldingCount`, `unyieldingShare`, `mysticCost(Player)`, `mysticMining(ServerLevel, Player, BlockPos, BlockState, ItemStack)`, y `onHurt(ServerLevel, LivingEntity victim, DamageSource, float damageTaken)` (armadura de iracero y de arcanio). En `weaponBonus`, la rama de Iracundo |
| 5 | `upgrade/CombatUpgrades.java` | en `AFTER_DAMAGE`, junto a `TraitEffects.onHit(...)` (línea ~254): `TraitEffects.onHurt(level, victim, source, damageTaken)` |
| 6 | `combat/CombatHooks.java` | en `capped(...)`, **antes** del `return damage` de jugadores: el tope de Inquebrantable (2.4), con su exclusión de fuentes. `capped` pasa a `public` (la prueba `aegisCapsTheBlow` lo llama) |
| 7 | `clase/ClassEffects.java` | `spellCostMultiplier`: después de `concentrating` → `multiplier(...) * TraitEffects.mysticCost(player)` |
| 8 | `upgrade/MiningUpgrades.java` | en `PlayerBlockBreakEvents.AFTER`: `TraitEffects.mysticMining(...)` con la herramienta de la mano principal |
| 9 | `magic/Mana.java` | `ARCANIUM_SET_MANA = 50.0F` en `maxOf`; `usesMana` con `MISTICO`; `ARCANIO` en `carriesMana` |
| 10 | `upgrade/ArmorSets.java` | las tres ramas de 2.5, con un comentario de una línea cada una como las demás |
| 11 | `combat/ArrowTips.java` | `Special.IRA`, `GUARDIA`, `ARCANA` (con javadoc «Iracero (Iracundo)…»); en `special()`: `IRACUNDO -> IRA`, `INQUEBRANTABLE -> GUARDIA`, `MISTICO -> ARCANA`; `description()`: `IRA` con `String.format(Locale.ROOT, "%.1f", WRATH_ARROW_STEP)`, `GUARDIA` con `GUARD_ARROW_TICKS / 20`, `ARCANA` con `Math.round(ARCANE_ARROW_DAMAGE)`; `onHit`: `GUARDIA` y `ARCANA`; nuevo `public static float wrathBonus(Special, @Nullable Entity owner)` |
| 12 | `entity/ForgedArrow.java` | en `onHitEntity`, sumar `ArrowTips.wrathBonus(this.special(), this.getOwner())` a `conditionalBonus` |
| 13 | `client/GearAura.java` | al principio de `moteFor`: `IRACUNDO` → `random < 0.4 ? SMALL_FLAME : null`; `INQUEBRANTABLE` → `random < 0.3 ? WAX_ON : null`; `MISTICO` → `random < 0.6 ? WITCH : null` |
| 14 | `menu/ForgeMenu.java` | logro `corazon` (línea ~561): también si usa `IRACERO`, `EGIDA` o `ARCANIO` |
| 15 | `compat/ForjaJeiPlugin.java` | en `draw`: si `Alloys.PEAK.contains(recipe.id())` → `gui.forja.jei.cumbre` antes de la rama de `FORJA_BLANCA` (los 4 huecos de entrada caben: `1 + i × 19`) |
| 16 | `GuideBooks.java` | libro III `FUNDICION`, sección `fundicion_mayor`: `List.of("mesa_mayor", "aleaciones_lejanas", "aleaciones_cumbre", "fundicion_siguiente")`; y `"aleaciones_cumbre"` en `TOMO`, sección `taller`, detrás de `"aleaciones_lejanas"` |
| 17 | `client/GuideBookScreen.java` | `case "aleaciones_cumbre" -> this.peakAlloysChapter()` (junto a la línea ~826) y su icono `ModItems.alloy("iracero")` (junto a ~956). `peakAlloysChapter()` como `farAlloysChapter()`: texto `gui.forja.libros.aleaciones_cumbre`, una `IconRow` por receta de `PEAK` (ingredientes con su cantidad y el lingote), y por cada una `gui.forja.libros.aleaciones_cumbre.rasgo` con el nombre del rasgo y su `.largo`; al final `ChapterLink("aleaciones_lejanas")` |
| 18 | `tools/generate_assets.py` | `MATERIAL_COLORS` (línea ~82) y `ALLOY_COLORS` (línea ~3692), bajo `# The peak alloys (docs/ALEACIONES_CUMBRE.md)`: `"iracero": 0xB0142C, "egida": 0x8E6B3F, "arcanio": 0xE04FB0`. `ALLOY_SIDEWAYS["iracero"] = (0x7A0E1E, 0xF2B640)`. Con eso salen lingote, kit, receta del kit, tintes y armaduras sin más |
| 19 | `tools/lingotes.py` | 2.6 |
| 20 | `tools/lang_cumbre.py` (nuevo) | dict `CUMBRE` como `lang_aleaciones.py` (clave → (es, en)); en `tools/generate_lang.py`, detrás de `GUI.update(ALEACIONES)`: `from lang_cumbre import CUMBRE` y `GUI.update(CUMBRE)` |
| 21 | `src/gametest/java/dev/forja/test/MaterialesGameTests.java` | `everyAlloyCanBePoured`: la regla de calor blanco pasa a `(recipe.inputs().size() > 2 && !Alloys.FOUNDRY_ONLY.contains(recipe.id())) \|\| total > capacity` (si no, falla con las cumbre) |
| 22 | `CumbreGameTests.java`, `CumbreFootage.java`, `tools/hoja_cumbre.py` (nuevos) y `src/gametest/resources/fabric.mod.json` | sección 4 |
| 23 | `CHANGELOG.md` | entrada arriba del todo (4.4) |

**No se tocan:** `ApprenticeKits` (los aprendices no llevan corazón), `Report.java` (EQUILIBRIO.md las mide solas al
estar en `ForgeMaterial.values()`), `aleacionesDeFraguaEnSuSitio` (solo mira las de fragua lejana).

### 3.1 Textos (`tools/lang_cumbre.py`)

| Clave | Español | English |
|---|---|---|
| `item.forja.iracero` / `material.forja.iracero` | Iracero / iracero | Wrathsteel / wrathsteel |
| `item.forja.egida` / `material.forja.egida` | Égida / égida | Aegis / aegis |
| `item.forja.arcanio` / `material.forja.arcanio` | Arcanio / arcanio | Arcanium / arcanium |
| `conjunto.forja.iracero` | +3 de daño y +5 % de velocidad; su ira da Fuerza II | +3 damage and +5% speed; its wrath gives Strength II |
| `conjunto.forja.egida` | +10 de vida, +4 de dureza y no te empujan las explosiones; ningún golpe pasa del 25 % | +10 health, +4 toughness and explosions do not push you; no blow takes more than 25% |
| `conjunto.forja.arcanio` | +50 de maná máximo, +2 de dureza y +5 % de velocidad | +50 max mana, +2 toughness and +5% speed |
| `trait.forja.iracundo` (`.desc`) | Iracundo (Pega más cuanto más cerca estás de morir) | Wrathful (Hits harder the closer you are to dying) |
| `trait.forja.iracundo.largo` | Armas: +1 de daño por cada 20 % de vida que te falta, hasta +4. Armadura: si un golpe te deja por debajo del 40 %, Fuerza I 6 s (II con el conjunto), una vez cada 30 s. Flechas: +0,5 por cada 20 % que te falta. | Weapons: +1 damage for every 20% of health you are missing, up to +4. Armour: a blow that leaves you under 40% gives Strength I for 6 s (II with the full set), once every 30 s. Arrows: +0.5 for every 20% missing. |
| `trait.forja.inquebrantable` (`.desc`) | Inquebrantable (Ningún golpe te quita más de una parte de tu vida) | Unyielding (No single blow takes more than a share of your health) |
| `trait.forja.inquebrantable.largo` | Lo que la armadura deja pasar de un golpe no pasa del 40 % de tu vida máxima con una pieza, y 5 puntos menos por cada pieza más o si la llevas en la mano: 25 % con el conjunto, 20 % con el conjunto y un arma o escudo de égida. No vale contra caídas, el vacío ni nada que la armadura no lea. Flechas: Resistencia I 3 s al que dispara. | What armour lets through of a blow never passes 40% of your max health with one piece, 5 points less for each piece more or if you hold it: 25% with the full set, 20% with the set and an aegis weapon or shield. Not against falls, the void or anything armour does not read. Arrows: Resistance I for 3 s to the archer. |
| `trait.forja.mistico` (`.desc`) | Místico (Hechizos más baratos; heridas y trabajo dan maná) | Mystic (Cheaper spells; wounds and work give mana) |
| `trait.forja.mistico.largo` | En la mano: los hechizos cuestan un 25 % menos. Armadura: cada golpe recibido da 0,5 de maná por punto de daño y pieza, hasta 6. Herramientas: 0,25 de maná por bloque. Flechas: 2 de daño mágico que la armadura no para. | In hand: spells cost 25% less. Armour: every blow taken gives 0.5 mana per point of damage and piece, up to 6. Tools: 0.25 mana per block. Arrows: 2 magic damage armour does not stop. |
| `flecha.forja.especial.ira` / `.desc` | Ira / +%s por cada 20 %% de vida que te falta | Wrath / +%s for every 20%% of health you are missing |
| `flecha.forja.especial.guardia` / `.desc` | Guardia / Resistencia I %s s al que dispara | Guard / Resistance I for %s s to the archer |
| `flecha.forja.especial.arcana` / `.desc` | Arcana / %s de daño mágico que la armadura no para | Arcane / %s magic damage armour does not stop |
| `gui.forja.jei.cumbre` | Crisol de obsidiana con cubas | Obsidian crucible with tanks |
| `gui.forja.libro.cap.aleaciones_cumbre` | Aleaciones cumbre | Peak Alloys |
| `gui.forja.libros.aleaciones_cumbre` | Tres metales que solo salen del corazón del Herrero Caído, fundido con dos aleaciones al calor blanco: crisol de obsidiana en una línea de fundición, el corazón y una aleación en los huecos y la otra en una cuba. Un corazón por lingote. Las piezas se cuelan en una mesa de almas. El iracero es el que más pega, la égida la que más aguanta y el arcanio el de la magia y las herramientas. | Three metals that only come from the Fallen Smith's heart, poured with two alloys at white heat: an obsidian crucible on a foundry line, the heart and one alloy in its slots and the other in a tank. One heart per ingot. Parts are cast on a soul table. Wrathsteel hits hardest, aegis lasts longest and arcanium is for magic and tools. |
| `gui.forja.libros.aleaciones_cumbre.rasgo` | %s: %s | %s: %s |

Los `%%` siguen la regla de `check_formats` de `generate_lang.py`. Después: `python tools/generate_lang.py` y
`python tools/generate_assets.py` (Python310 por ruta completa, desde archivo).

## 4. Pruebas

### 4.1 `src/gametest/java/dev/forja/test/CumbreGameTests.java` (registrar en `fabric-gametest` de `fabric.mod.json`)

| Prueba | Qué comprueba |
|---|---|
| `cumbreRecipesUseTheHeartAndTwoAlloys` | las tres recetas: llevan `CORAZON_DE_FORJA`; ≥ 2 ingredientes que son lingote de otro material (`ForgeMaterial.fromInput` ≠ null y ≠ `CORAZON`); ≥ 3 ingredientes; calor `FORJA_BLANCA`; están en `PEAK`, `WHITE_HEAT_ONLY` y `FOUNDRY_ONLY`; `place == ANY`; salen 2 / 1 / 1 |
| `obsidianCruciblePoursEachPeakAlloy` | por cada una, con el montaje de `FundicionGameTests.crucibleAlloysBesideAnotherBank` pero con `CRISOL_DE_OBSIDIANA`: corazón + la aleación de menos cantidad en los huecos, la tercera en una cuba (`tank.fill`), una cuba vacía; tras `Tier.OBSIDIANA.cook + 10` la cuba vacía tiene el lingote cumbre en la cantidad de la receta y los huecos están vacíos |
| `ironCrucibleAndTablesRefuseThem` | el mismo montaje con `CRISOL_DE_HIERRO` no cuela nada; `Alloys.match(points, Heat.FUNDIDA)` con los ingredientes no da ninguna cumbre |
| `peakAlloysMakeEveryPart` | como `FraguasLejanasGameTests.theFarAlloysMakeEveryPart`: cada material vale para cada `PartType` y cada `ForgeType` se arma entero de él |
| `cumbreSuperanAlCorazonEnLoSuyo` | sobre **todos** los `ForgeMaterial.values()`: iracero tiene el `attackDamageBonus` más alto (estricto); égida, la `durability`, `armorDurability`, `toughness` y `handleDurability` más altas (estricto) y `knockbackResistance` ≥ cualquiera; arcanio, `enchantability`, `miningSpeed`, `handleAttackSpeed` y `handleMiningSpeed` más altas (estricto) |
| `ningunaEsMejorEnTodo` | ninguna cumbre es ≥ que el corazón ni que otra cumbre a la vez en daño, durabilidad, minado, encantabilidad, mango (durab. y ataque), armadura del conjunto, durab. de armadura y dureza; y ninguna pasa de 21 de armadura |
| `wrathGrowsAsHealthFalls` | `wrathSteps` a vida 20/16/12/8/4 de 20 → 0/1/2/3/4; un zombi golpeado (`TraitEffects.weaponBonus`) con espada de iracero a vida llena da 0 y a 4 de vida da 4,0 |
| `wrathArmourGivesStrengthOnceInThirtySeconds` | jugador falso con 4 piezas de iracero, `onHurt` con vida 7/20 → Fuerza amplificador 1; otra vez antes de 600 ticks → no se renueva; con 1 pieza → amplificador 0 |
| `aegisCapsTheBlow` | `unyieldingShare` 0..5 → 1,0 / 0,40 / 0,35 / 0,30 / 0,25 / 0,20; con el conjunto (n = 4) y vida máx. 20, `CombatHooks.capped` con 30 de daño de `mobAttack` devuelve 5,0; con `fall()` devuelve 30; sin égida, 30 |
| `mysticCheapensSpellsAndFeedsMana` | `ClassEffects.spellCostMultiplier` con un báculo de arcanio en la mano = 0,75 × el de sin él; con 4 piezas, `onHurt` con 4 de daño da `min(6, 4 × 0,5 × 4) = 6` de maná; un pico de arcanio que rompe piedra da 0,25 |
| `peakSetsGiveTheirBonus` | con cada conjunto: los atributos de 2.5 están; `Mana.maxOf` sube 50 con arcanio; sin `ARMOR` extra en la égida |
| `peakArrowsDoTheirThing` | `ArrowTips.special`: iracero → `IRA`, égida → `GUARDIA`, arcanio → `ARCANA`; `wrathBonus` a 4/20 de vida = 2,0; `GUARDIA` da Resistencia al tirador; `ARCANA` quita ≥ 2 a un zombi con armadura de diamante |
| `peakIngotsAreEpicAndFireproof` | los tres lingotes: `Rarity.EPIC` y no se queman en lava ni fuego |

Las de siempre que las cubren solas y deben seguir pasando: `MaterialesGameTests` (todas; `everyAlloyCanBePoured`
con el cambio de 3.21), `KitsGameTests.everyMetalHasAKitARecipeAndATexture`, `LibrosGameTests`,
`ArmaduraGameTests`, `BalanceGameTests`.

### 4.2 Equilibrio: qué umbrales pueden moverse

| Prueba | Qué se espera | Si falla |
|---|---|---|
| `ArmaduraGameTests` (`MAX_OVER_NETHERITE = 5.0`) | égida = fila del obsidiacero (+3,9); iracero y arcanio por debajo. **No se toca** | si la égida pasa de +5, bajar su empuje a 0,10 (fila del corazón, +3,2); no subir el umbral |
| `equilibrioSinDominados` | sin cambios: rasgos nuevos, sin inversiones nuevas | parar y avisar |
| `magiaEnSuSitio` (`MAGIC_PLAIN_FLOOR 1.0`, `MAGIC_MAGE_CEILING 1.3`, `MAGIC_TOME_CEILING 1.5`) | el simulador no cuenta Místico (como Astral). La cabeza de iracero hace más rápida la mediana cuerpo a cuerpo (~5–10 % al 100 %): el Mago queda más cerca del techo (hoy báculo ×0,86, grimorio ×1,30) | si `MAGIC_TOME_CEILING` o `MAGIC_MAGE_CEILING` fallan **solo** porque bajó la mediana, subirlos a **1,6** y **1,4**; si falla `MAGIC_PLAIN_FLOOR` (báculo sin clase hoy ×1,02 al 100 %), **no** se mueve: se baja la cabeza de iracero a +5,0 y se mide otra vez; si sigue, se para y se pregunta |
| `herreroEnSuSitio` | el equipo «cc» toma la mejor arma, que llevará iracero: la pelea baja ~5–10 % (fácil 1:52 → ~1:43, ventana 1:30–3:00) | si una ventana se rompe, **no** se mueve: en `SmithFight` se quitan de la búsqueda de armas los tres materiales de `Alloys.PEAK` (salen de su corazón: en la primera pelea no existen) |
| `aleacionesDeFraguaEnSuSitio` | no las mira (no son de fragua lejana) | — |

### 4.3 Cliente: `FORJA_SOLO=cumbre`

`src/gametest/java/dev/forja/test/CumbreFootage.java`, con `film(context, server, connection, x, y, z)` como
`OricalcoFootage`, y la rama `if ("cumbre".equals(solo))` en `ForjaClientTest` junto a la de `"oricalco"`. Capturas
`cumbre_NN_*.png` (prefijo fijo para la hoja):

| # | Captura | Qué |
|---|---|---|
| 01 | `cumbre_01_lingotes` | los tres lingotes y los tres kits en una mesa de crafteo (como `KitsFootage`) |
| 02 | `cumbre_02_crisol` | crisol de obsidiana en línea con dos cubas, corazón y obsidiacero en los huecos, magmacero en la cuba; pantalla del crisol colando «Égida» |
| 03–05 | `cumbre_03_iracero`, `_04_egida`, `_05_arcanio` | soporte de armadura con el conjunto (placa del metal, forro de cuero), de frente y de espaldas, con espada y pico del metal en marcos al lado |
| 06 | `cumbre_06_mano` | primera persona: espada de iracero y escudo de égida, con las partículas de `GearAura` |
| 07 | `cumbre_07_libro` | libro III abierto en «Aleaciones cumbre» |
| 08 | `cumbre_08_jei` | si JEI está cargado en la prueba de cliente, la receta del arcanio con «Crisol de obsidiana con cubas»; si no, se omite y se dice |

Hoja de contacto: `tools/hoja_cumbre.py`, copia de `tools/hoja_oricalco.py` con texturas `iracero`, `egida`,
`arcanio` y sus `kit_de_reparacion_*`, y capturas `cumbre_*.png`. Escribe
`E:\IA\Claude\Forja_capturas_mejoras\aleaciones_cumbre\hoja_cumbre.jpg` y copia allí las capturas sueltas.

### 4.4 `CHANGELOG.md`

Entrada nueva arriba: `## <fecha> — Aleaciones cumbre: iracero, égida y arcanio`, con una viñeta por aleación
(receta, salida, números clave, rasgo), una de dónde se hacen (crisol de obsidiana con cubas, mesa de almas), una de
guía/JEI, una de pruebas (`CumbreGameTests`, `FORJA_SOLO=cumbre`, hoja en `Forja_capturas_mejoras/aleaciones_cumbre`)
y lo que haya cambiado en umbrales según 4.2 (o «ningún umbral cambia»).
