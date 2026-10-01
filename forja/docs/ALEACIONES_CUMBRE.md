# Aleaciones cumbre: iracero, égida y arcanio

Pedido de Andy (2026-10-01): **tres aleaciones nuevas que sean lo mejor del mod**. Cada una lleva **corazón de forja**
y **al menos otras dos aleaciones**, con **tres materiales o más**. Cada una es la mejor en algo distinto (ataque,
defensa, magia y utilidad) y ninguna es la mejor en todo.

Es el diseño, escrito antes de programar, con todos los números decididos. Quien lo implemente no tiene que decidir
nada: si algo no cuadra con el código, se para y se pregunta.

**Revisión (2026-10-01, aprobada por Andy):** las cumbre no se hacen directamente de las aleaciones de hoy, sino de un
**escalón intermedio** de cuatro aleaciones nuevas (2.7): **espectracero**, **corazón de volcán**, **eclipse** y
**astralita**. Cada intermedia son dos aleaciones que ya existen y un material más. Van entre las de fragua lejana y el
corazón de forja: fuertes, pero nunca por encima del corazón en lo suyo. Las recetas de 2.1 ya las usan.

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
| Receta | 1 corazón de forja + 2 corazón de volcán + 1 acero vivo | 1 corazón de forja + 2 espectracero + 2 obsidiacero | 1 corazón de forja + 2 astralita + 2 eclipse |
| Materiales distintos | 3 (2 aleaciones, una intermedia) | 3 (2 aleaciones, una intermedia) | 3 (2 aleaciones, las dos intermedias) |
| Lo que cuesta, abierto | 1 corazón + 1 acero vivo (1 corazón + 2 damasco) + 1 solacero + 2 magmacero + 4 bloques de magma | 1 corazón + 2 fatuo + 1 almacero + 1 lágrima de ghast + 2 obsidiacero | 1 corazón + 2 oricalco + 1 eterio + 2 hierro estelar + 1 lunacero + 2 eterio + 2 obsidiana llorona |
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
- **Cómo se cargan** (lo dirá el libro): el **corazón en un hueco** y **otra aleación en el otro** (iracero: corazón +
  1 acero vivo, corazón de volcán en una cuba; égida: corazón + 2 obsidiacero, espectracero en una cuba; arcanio:
  corazón + 2 astralita, eclipse en una cuba). Con los dos huecos llenos ninguna otra receta casa (el acero vivo pide
  damasco en el hueco, la astralita oricalco).
- Las intermedias de fragua lejana (espectracero, corazón de volcán, eclipse) **entran** en las cubas como cualquier
  lingote de metal (`MeltTankBlockEntity.holds`: material no básico). Lo que el crisol no acepta son los ingredientes
  de cantera de las fraguas lejanas.
- **Colada:** las tres pasan de 1600 de durabilidad, así que piezas solo en **mesa de almas** (como el oricalco).
- Lingotes **épicos y a prueba de fuego**, como el corazón.

### 2.3 Números (`ForgeMaterial`, en este orden, detrás de `ETERIO`)

```java
	// ------------------------------------------------ the peak alloys (docs/ALEACIONES_CUMBRE.md)
	/** Wrathsteel: the heart poured with volcano heart and living steel. The hardest-hitting head in the mod, and it hits harder the closer you are to dying. */
	IRACERO(0xB0142C, alloyTag("iracero"), null, true, 2000, 9.5F, 5.5F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		18, 1.40F, 0.10F, 1.10F, new int[]{3, 6, 8, 3}, 42, 3.0F, 0.05F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.IRACUNDO),
	/** Aegis: the heart poured with spectresteel and obsidian steel. It outlasts everything, and no single blow gets through it whole. */
	EGIDA(0x8E6B3F, alloyTag("egida"), null, true, 2800, 8.0F, 4.0F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		15, 1.85F, -0.25F, 0.90F, new int[]{3, 7, 8, 3}, 60, 5.0F, 0.15F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.INQUEBRANTABLE),
	/** Arcanium: the heart poured with astralite and eclipse. The quickest hand, the best enchanting and spells for less. */
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

### 2.7 El escalón intermedio

Cuatro aleaciones de dos aleaciones existentes y un material más. Cada una alimenta una cumbre y su rasgo es un
**primo flojo** del de esa cumbre (Amparo → Inquebrantable, Ardor → Iracundo, Penumbra y Sideral → Místico).

| | **Espectracero** | **Corazón de volcán** | **Eclipse** | **Astralita** |
|---|---|---|---|---|
| id / enum | `espectracero` / `ESPECTRACERO` | `corazon_de_volcan` / `CORAZON_DE_VOLCAN` | `eclipse` / `ECLIPSE` | `astralita` / `ASTRALITA` |
| es / en | Espectracero / Spectresteel | Corazón de volcán / Volcano Heart | Eclipse / Eclipse | Astralita / Astralite |
| Color | `0x4C8C9E` (acero de almas) | `0xA8401C` (el lingote corre de basalto `0x3A1E18` a lava `0xFF9A2E`) | `0x2B2350` (añil; el lingote corre de `0x120E24` a `0x8C7FE0`) | `0x5C7CFA` (azul de estrella) |
| Receta por tanda | 2 fatuo + 1 almacero + 1 lágrima de ghast | 2 magmacero + 1 solacero + 4 bloques de magma | 2 eterio + 1 lunacero + 2 obsidiana llorona | 2 oricalco + 1 eterio + 2 hierro estelar |
| Salen | 2 | 2 | 2 | 2 |
| Dónde | **fragua de almas** (Fragua caída), en el Nether; 1 polvo de blaze por tanda | **fragua de almas**, en el Nether; 1 polvo de blaze | **fragua del vacío**, en el End; 1 perla de ender | **crisol de obsidiana con cubas** (`FORJA_BLANCA`): 2 oricalco + 1 eterio en los huecos, hierro estelar en una cuba |
| Por qué ahí | el fatuo solo sale de esa fragua y el tema es de almas | el magmacero es de esa fragua; la lava y el sol se juntan en el fuego del Nether | el eterio es del End; el vacío y la luna | es metal del Gremio (oricalco) y no tiene nada de cantera: crisol, como el oricalco; a calor blanco para que no lo haga una mesa sobre lava |
| `Heat` de la receta (lo que lee la guía y `everyAlloyCanBePoured`) | `FUNDIDA` | `FORJA_BLANCA` (lleva solacero) | `FORJA_BLANCA` (lleva lunacero) | `FORJA_BLANCA` |
| `Place` | `ALMAS` | `ALMAS` | `VACIO` | `ANY`; en `WHITE_HEAT_ONLY` y `FOUNDRY_ONLY` |
| Rasgo | **Amparo** | **Ardor** | **Penumbra** | **Sideral** |
| Especialidad (≤ corazón) | defensa: durab. 2000 < 2400, dureza 3,0 < 3,5 | ataque: +4,0 < +4,5 | magia en la oscuridad: encant. 24 ≤ 25 | magia: encant. 25 ≤ 25, minado 9,5 ≤ 9,5 |

Las tres de fragua lejana entran en la prueba `aleacionesDeFraguaEnSuSitio` (recorre todo lo que no es `ANY`):
ninguna es «netherita mejor» (espectracero y eclipse pegan menos de +4,0; corazón de volcán dura menos de 2031).

**`ForgeMaterial`**, detrás de `ETERIO` y **antes** de las cumbre:

```java
	// ------------------------------------------- the middle tier (docs/ALEACIONES_CUMBRE.md, 2.7)
	/** Spectresteel: wispfire and soul steel at the soul forge. Hard to put down, and once in a while it will not let a blow through whole. */
	ESPECTRACERO(0x4C8C9E, alloyTag("espectracero"), null, true, 2000, 7.5F, 3.5F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		18, 1.55F, -0.10F, 0.95F, new int[]{3, 6, 8, 3}, 44, 3.0F, 0.10F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.AMPARO),
	/** Volcano heart: magmasteel and sun steel at the soul forge. It burns hotter the worse things go. */
	CORAZON_DE_VOLCAN(0xA8401C, alloyTag("corazon_de_volcan"), null, true, 1800, 8.5F, 4.0F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		16, 1.40F, 0.05F, 1.05F, new int[]{3, 6, 8, 3}, 40, 2.5F, 0.05F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.ARDOR),
	/** Eclipse: aetherium and moon steel at the void forge. Magic comes cheaper to it where the light does not reach. */
	ECLIPSE(0x2B2350, alloyTag("eclipse"), null, true, 1500, 9.0F, 3.5F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		24, 1.15F, 0.20F, 1.10F, new int[]{3, 6, 7, 3}, 34, 2.0F, 0.0F, SoundEvents.ARMOR_EQUIP_CHAIN, Trait.PENUMBRA),
	/** Astralite: orichalcum, aetherium and star iron in the obsidian crucible. Magic comes cheaper to it, cheapest under the night sky. */
	ASTRALITA(0x5C7CFA, alloyTag("astralita"), null, true, 1700, 9.5F, 3.5F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		25, 1.25F, 0.20F, 1.15F, new int[]{3, 6, 8, 3}, 38, 2.5F, 0.05F, SoundEvents.ARMOR_EQUIP_GOLD, Trait.SIDERAL),
```

Armaduras de 19–20 y empuje ≤ 0,10: por debajo de la fila del corazón en `ArmaduraGameTests`.

**Rasgos** (al final de `Trait`, antes de los de las cumbre; constantes en `TraitEffects` salvo las de flecha):

| Rasgo | Dónde | Efecto exacto | Constantes |
|---|---|---|---|
| **Amparo** (`AMPARO`) | armadura o en la mano (pieza entera) | un golpe que la armadura lee y que, ya pasado por ella, quitaría más de `SHELTER_SHARE` de la vida máxima se corta ahí, **una vez cada `SHELTER_COOLDOWN`** (`SHELTER_SET_COOLDOWN` con el conjunto); el que pegó se lleva llama fatua `SOUL_FLAME_ARMOR_TICKS` (`soulFlame`). Mismas exclusiones que Inquebrantable. Si el portador tiene también Inquebrantable, vale el tope más bajo y Amparo solo gasta su espera cuando es él el que corta. Partículas `SOUL` 12, sonido `SoundEvents.SOUL_ESCAPE` | `SHELTER_SHARE = 0.40F`, `SHELTER_COOLDOWN = 400`, `SHELTER_SET_COOLDOWN = 300` |
| | flecha **VELO** | Resistencia I `VEIL_ARROW_TICKS` al que dispara | `ArrowTips.VEIL_ARROW_TICKS = 30` |
| **Ardor** (`ARDOR`) | armas (`weaponBonus`) | `+ARDOR_DAMAGE × wrathSteps(atacante)` (hasta +2,0); con 2 escalones o más (≤ 60 % de vida), el objetivo arde 2 s | `ARDOR_DAMAGE = 0.5F`, `ARDOR_IGNITE_STEPS = 2` |
| | armadura (`onHurt`) | por debajo de `WRATH_ARMOR_THRESHOLD`: Resistencia al fuego `ARDOR_FIRE_TICKS` y, con las 4 piezas, te apaga; una vez cada `ARDOR_COOLDOWN` (mapa propio) | `ARDOR_FIRE_TICKS = 120`, `ARDOR_COOLDOWN = 600` |
| | flecha **ARDOR** | `wrathBonus` con `ARDOR_ARROW_STEP` por escalón; con ≥ 2 escalones del tirador, el objetivo arde 2 s | `ArrowTips.ARDOR_ARROW_STEP = 0.25F` |
| **Penumbra** (`PENUMBRA`) | en la mano | hechizos ×`PENUMBRA_COST` si el que lanza está a oscuras (`inDark`) | `PENUMBRA_COST = 0.85F` |
| | armas | golpe a algo que está a oscuras: `Mana.give(PENUMBRA_HIT_MANA)` al jugador | `PENUMBRA_HIT_MANA = 0.5F` |
| | armadura (`onHurt`) | herido a oscuras: `Mana.give(min(PENUMBRA_HURT_CAP, daño × PENUMBRA_HURT_MANA × piezas))` | `PENUMBRA_HURT_MANA = 0.25F`, `PENUMBRA_HURT_CAP = 3.0F` |
| | flecha **SOMBRA** | objetivo a oscuras: Ceguera `SHADOW_ARROW_TICKS` | `ArrowTips.SHADOW_ARROW_TICKS = 40` |
| **Sideral** (`SIDERAL`) | en la mano | hechizos ×`SIDEREAL_COST`; ×`SIDEREAL_NIGHT_COST` de noche a cielo abierto sin lluvia (`!isBrightOutside() && canSeeSky && !isRaining`) | `SIDEREAL_COST = 0.90F`, `SIDEREAL_NIGHT_COST = 0.80F` |
| | herramientas | `Mana.give(SIDEREAL_BLOCK_MANA)` por bloque con dureza > 0 | `SIDEREAL_BLOCK_MANA = 0.10F` |
| | flecha **ASTRO** | `Mana.give(ASTRO_ARROW_MANA)` al tirador y Brillo `ASTRO_GLOW_TICKS` al objetivo | `ArrowTips.ASTRO_ARROW_MANA = 1.5F`, `ASTRO_GLOW_TICKS = 60` |

**Precio de los hechizos sin acumular:** `TraitEffects.gearSpellCost(Player)` = el **mínimo** de `mysticCost`,
`siderealCost` y `penumbraCost` (cada uno 1,0 si no aplica). Es lo que multiplica `spellCostMultiplier`, no el
producto: un arcanio en una mano y una astralita en la otra son ×0,75, no ×0,675. `Mana.usesMana` es `true` con
`PENUMBRA` y `SIDERAL`.

**Conjuntos** (`ArmorSets`; ninguno da `ARMOR` plano):

| Material | Bonos | Otro sitio |
|---|---|---|
| `ESPECTRACERO` | `ARMOR_TOUGHNESS` +2,0; `MAX_HEALTH` +4; `BURNING_TIME` −0,5 | espera de Amparo 15 s |
| `CORAZON_DE_VOLCAN` | `ATTACK_DAMAGE` +2,0; `BURNING_TIME` −1,0 | te apaga en el rasgo |
| `ECLIPSE` | `SNEAKING_SPEED` +0,3 | `Mana.ECLIPSE_SET_MANA = 20.0F` |
| `ASTRALITA` | `ARMOR_TOUGHNESS` +1,0 | `Mana.ASTRALITE_SET_MANA = 30.0F`; `ECLIPSE` y `ASTRALITA` también en `carriesMana` |

**Arte** (`tools/lingotes.py`). Variante A, en `mark_a`:

```python
    elif name == "espectracero":
        # Spectresteel: a seam of soul blue along the bar and one pale wisp rising off it.
        for (x, y) in axis(7.6):
            if x % 3 != 0:
                put(x, y, colour=(120, 230, 240))
                put(x, y + 1, dark)
        put(10, 4, colour=(220, 252, 255))
        put(10, 5, colour=(95, 211, 224))
    elif name == "corazon_de_volcan":
        # Volcano heart: a white-hot point in the middle of the top, magma round it and a dark crust under it.
        put(8, 5, colour=(255, 244, 200))
        for (x, y) in ((7, 5), (9, 5), (8, 4)):
            put(x, y, colour=(255, 138, 40))
        for (x, y) in ((7, 6), (8, 6), (9, 6)):
            put(x, y, 0)
    elif name == "eclipse":
        # Eclipse: a dark disc in the middle of the top with a thin lit ring on its right.
        for (x, y) in ((7, 5), (8, 5), (7, 6), (8, 6)):
            put(x, y, 0)
        for (x, y) in ((9, 5), (9, 6), (8, 4)):
            put(x, y, colour=(214, 206, 255))
    elif name == "astralita":
        # Astralite: three small stars scattered on the top, white points with a blue shadow under each.
        for (x, y) in ((5, 7), (8, 5), (11, 4)):
            put(x, y, colour=(255, 255, 255))
            put(x, y + 1, colour=(60, 90, 200))
```

Variante B, en `GLYPHS` (distintos de todos, comprobado):

| Metal | Glifo | Lectura |
|---|---|---|
| `espectracero` | `("#..#", ".##.", ".##.")` | un alma |
| `corazon_de_volcan` | `("..#.", ".###", "####")` | un volcán |
| `eclipse` | `(".##.", "##..", ".##.")` | una luna tapada |
| `astralita` | `("#.#.", ".#..", "#.#.")` | una estrella |

`generate_assets.py`: los cuatro colores en `MATERIAL_COLORS` y `ALLOY_COLORS`; `ALLOY_SIDEWAYS["corazon_de_volcan"] =
(0x3A1E18, 0xFF9A2E)`; `ALLOY_RAMPS["eclipse"] = (0x120E24, 0x8C7FE0)`.

## 3. Archivos que tocar

| # | Archivo | Qué |
|---|---|---|
| 1 | `material/ForgeMaterial.java` | detrás de `ETERIO` (su `;` pasa a `,`): primero las cuatro intermedias de 2.7, después las tres cumbre de 2.3. Al final de `Trait`: `AMPARO`, `ARDOR`, `PENUMBRA`, `SIDERAL` y después `IRACUNDO`, `INQUEBRANTABLE`, `MISTICO`. `displayStack` no se toca (`ModItems.alloy` las encuentra) |
| 2 | `forge/Alloys.java` | en `ALL`, detrás de `eterio`, primero bajo `// ---- the middle tier (docs/ALEACIONES_CUMBRE.md, 2.7): two alloys and one more thing.`: `new Recipe("espectracero", Heat.FUNDIDA, List.of(new Part(() -> ModItems.alloy("fatuo"), 2), new Part(() -> ModItems.alloy("almacero"), 1), new Part(() -> Items.GHAST_TEAR, 1)), 2)`; `corazon_de_volcan`, `FORJA_BLANCA`: `alloy("magmacero")` 2, `alloy("solacero")` 1, `Items.MAGMA_BLOCK` 4 → 2; `eclipse`, `FORJA_BLANCA`: `alloy("eterio")` 2, `alloy("lunacero")` 1, `Items.CRYING_OBSIDIAN` 2 → 2; `astralita`, `FORJA_BLANCA`: `ModItems.ORICALCO` 2, `alloy("eterio")` 1, `ModItems.HIERRO_ESTELAR` 2 → 2. Después bajo `// ---- the peak alloys (docs/ALEACIONES_CUMBRE.md): the forge heart and two alloys, white heat on a foundry line.`: `new Recipe("iracero", Heat.FORJA_BLANCA, List.of(new Part(() -> ModItems.CORAZON_DE_FORJA, 1), new Part(() -> ModItems.alloy("corazon_de_volcan"), 2), new Part(() -> ModItems.alloy("acero_vivo"), 1)), 2)`; `egida`: corazón 1, `alloy("espectracero")` 2, `alloy("obsidiacero")` 2 → 1; `arcanio`: corazón 1, `alloy("astralita")` 2, `alloy("eclipse")` 2 → 1. `PLACES`: `"espectracero", Place.ALMAS`, `"corazon_de_volcan", Place.ALMAS`, `"eclipse", Place.VACIO` (`Map.of` admite hasta 10 pares; hay 6). `WHITE_HEAT_ONLY` y `FOUNDRY_ONLY` ganan `astralita`, `iracero`, `egida`, `arcanio`. Nuevos `public static final Set<String> PEAK = Set.of("iracero", "egida", "arcanio")` y `MIDDLE = Set.of("espectracero", "corazon_de_volcan", "eclipse", "astralita")`. Corregir el comentario «These three have two ingredients apiece on purpose» (las cumbre y la astralita usan cubas) |
| 2b | `tools/lang_aleaciones.py` | los textos de la fragua que hoy dicen qué funde (`… solo funde fatuo y magmacero.`, `… solo funde eterio.` y las dos frases del libro VI con «solo funde») pasan a nombrar también espectracero y corazón de volcán, y eclipse |
| 3 | `registry/ModItems.java` | en el bucle de `Alloys.ALL`: si `Alloys.PEAK.contains(id)`, `new Item.Properties().rarity(Rarity.EPIC).fireResistant()`; si `Alloys.MIDDLE.contains(id)`, `rarity(Rarity.RARE).fireResistant()` |
| 4 | `upgrade/TraitEffects.java` | constantes y métodos de 2.4 y 2.7: `wrathSteps`, `unyieldingCount`, `unyieldingShare`, `shelters(LivingEntity)` y su mapa de esperas (Amparo), `mysticCost`, `siderealCost`, `penumbraCost`, `gearSpellCost(Player)`, `workMana(ServerLevel, Player, BlockPos, BlockState, ItemStack)` (Místico y Sideral), y `onHurt(ServerLevel, LivingEntity victim, DamageSource, float damageTaken)` (armaduras de iracero, corazón de volcán, arcanio y eclipse). En `weaponBonus`: Iracundo y Ardor; en `onHit` (atacante): el maná de Penumbra |
| 5 | `upgrade/CombatUpgrades.java` | en `AFTER_DAMAGE`, junto a `TraitEffects.onHit(...)` (línea ~254): `TraitEffects.onHurt(level, victim, source, damageTaken)` |
| 6 | `combat/CombatHooks.java` | en `capped(...)`, **antes** del `return damage` de jugadores: el tope de Inquebrantable y el de Amparo (2.4, 2.7), con su exclusión de fuentes; vale el más bajo. `capped` pasa a `public` (las pruebas lo llaman) |
| 7 | `clase/ClassEffects.java` | `spellCostMultiplier`: después de `concentrating` → `multiplier(...) * TraitEffects.gearSpellCost(player)` |
| 8 | `upgrade/MiningUpgrades.java` | en `PlayerBlockBreakEvents.AFTER`: `TraitEffects.workMana(...)` con la herramienta de la mano principal |
| 9 | `magic/Mana.java` | `ARCANIUM_SET_MANA = 50.0F`, `ASTRALITE_SET_MANA = 30.0F`, `ECLIPSE_SET_MANA = 20.0F` en `maxOf`; `usesMana` con `MISTICO`, `SIDERAL` y `PENUMBRA`; `ARCANIO`, `ASTRALITA` y `ECLIPSE` en `carriesMana` |
| 10 | `upgrade/ArmorSets.java` | las siete ramas de 2.5 y 2.7, con un comentario de una línea cada una como las demás |
| 11 | `combat/ArrowTips.java` | `Special.IRA`, `GUARDIA`, `ARCANA`, `VELO`, `ARDOR`, `SOMBRA`, `ASTRO` (javadoc «Iracero (Iracundo)…» etc.); en `special()`: `IRACUNDO -> IRA`, `INQUEBRANTABLE -> GUARDIA`, `MISTICO -> ARCANA`, `AMPARO -> VELO`, `ARDOR -> ARDOR`, `PENUMBRA -> SOMBRA`, `SIDERAL -> ASTRO`; `description()` con sus números (`VELO_`/`SHADOW_`: ticks / 20; `ARDOR`: `"%.2f"` de `ARDOR_ARROW_STEP`; `ASTRO`: `"%.1f"` de `ASTRO_ARROW_MANA`); `onHit`: `GUARDIA`, `ARCANA`, `VELO`, `ARDOR` (fuego), `SOMBRA`, `ASTRO`; nuevo `public static float wrathBonus(Special, @Nullable Entity owner)` (IRA y ARDOR) |
| 12 | `entity/ForgedArrow.java` | en `onHitEntity`, sumar `ArrowTips.wrathBonus(this.special(), this.getOwner())` a `conditionalBonus` |
| 13 | `client/GearAura.java` | al principio de `moteFor`: `IRACUNDO` → `random < 0.4 ? SMALL_FLAME : null`; `INQUEBRANTABLE` → `random < 0.3 ? WAX_ON : null`; `MISTICO` → `random < 0.6 ? WITCH : null`; después `AMPARO` → `random < 0.4 ? SOUL : null`; `ARDOR` → `random < 0.25 ? LAVA : null`; `PENUMBRA` → `bright ? null : (random < 0.5 ? SQUID_INK : null)`; `SIDERAL` → `bright ? null : END_ROD` |
| 14 | `menu/ForgeMenu.java` | logro `corazon` (línea ~561): también si usa `IRACERO`, `EGIDA` o `ARCANIO` |
| 15 | `compat/ForjaJeiPlugin.java` | en `draw`: si `Alloys.PEAK.contains(recipe.id())` o es `astralita` (`FOUNDRY_ONLY` y `FORJA_BLANCA`) → `gui.forja.jei.cumbre` antes de la rama de `FORJA_BLANCA`; las intermedias de fragua lejana ya salen con su fragua (`!anywhere`). Las entradas caben (3 como mucho) |
| 16 | `GuideBooks.java` | libro III `FUNDICION`, sección `fundicion_mayor`: `List.of("mesa_mayor", "aleaciones_lejanas", "aleaciones_cumbre", "fundicion_siguiente")`; y `"aleaciones_cumbre"` en `TOMO`, sección `taller`, detrás de `"aleaciones_lejanas"` |
| 17 | `client/GuideBookScreen.java` | `case "aleaciones_cumbre" -> this.peakAlloysChapter()` (junto a la línea ~826) y su icono `ModItems.alloy("iracero")` (junto a ~956). `peakAlloysChapter()` como `farAlloysChapter()`: texto `gui.forja.libros.aleaciones_cumbre`; subtítulo `gui.forja.libros.aleaciones_cumbre.intermedias` con una `IconRow` por receta de `MIDDLE` (ingredientes con su cantidad y el lingote) y `gui.forja.libros.aleaciones_cumbre.donde` (dónde se hace cada una); subtítulo `gui.forja.libro.cap.aleaciones_cumbre` con una `IconRow` por receta de `PEAK`; por cada una de las siete, `gui.forja.libros.aleaciones_cumbre.rasgo` con el nombre del rasgo y su `.largo`; al final `ChapterLink("aleaciones_lejanas")`. `farAlloysChapter` ya recoge las tres intermedias de fragua lejana (recorre `!anywhere`) |
| 18 | `tools/generate_assets.py` | `MATERIAL_COLORS` (línea ~82) y `ALLOY_COLORS` (línea ~3692), bajo `# The middle tier and the peak alloys (docs/ALEACIONES_CUMBRE.md)`: `"espectracero": 0x4C8C9E, "corazon_de_volcan": 0xA8401C, "eclipse": 0x2B2350, "astralita": 0x5C7CFA, "iracero": 0xB0142C, "egida": 0x8E6B3F, "arcanio": 0xE04FB0`. `ALLOY_SIDEWAYS`: `"iracero": (0x7A0E1E, 0xF2B640)`, `"corazon_de_volcan": (0x3A1E18, 0xFF9A2E)`; `ALLOY_RAMPS`: `"eclipse": (0x120E24, 0x8C7FE0)`. Con eso salen lingote, kit, receta del kit, tintes y armaduras sin más |
| 19 | `tools/lingotes.py` | 2.6 y 2.7 (siete marcas y siete glifos) |
| 20 | `tools/lang_cumbre.py` (nuevo) | dict `CUMBRE` como `lang_aleaciones.py` (clave → (es, en)); en `tools/generate_lang.py`, detrás de `GUI.update(ALEACIONES)`: `from lang_cumbre import CUMBRE` y `GUI.update(CUMBRE)` |
| 21 | `src/gametest/java/dev/forja/test/MaterialesGameTests.java` | `everyAlloyCanBePoured`: la regla de calor blanco pasa a `(recipe.inputs().size() > 2 && !Alloys.FOUNDRY_ONLY.contains(recipe.id())) \|\| total > capacity` (si no, falla con las cumbre) |
| 22 | `CumbreGameTests.java`, `CumbreFootage.java`, `tools/hoja_cumbre.py` (nuevos) y `src/gametest/resources/fabric.mod.json` | sección 4 |
| 23 | `CHANGELOG.md` | entrada arriba del todo (4.4) |

**No se tocan:** `ApprenticeKits` (los aprendices no llevan corazón), `Report.java` (EQUILIBRIO.md las mide solas al
estar en `ForgeMaterial.values()`; la sección «Aleaciones de fragua frente a la netherita» gana sola espectracero,
corazón de volcán y eclipse), `FarForgeBlock`/`FarForgeBlockEntity` (leen `Alloys.at(place)`; el hogar de 4
ingredientes distintos y 64 de cada uno basta para las tres), `aleacionesDeFraguaEnSuSitio` (ya recorre todo lo que
no es `ANY`).

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
| `item.forja.espectracero` / `material.forja.espectracero` | Espectracero / espectracero | Spectresteel / spectresteel |
| `item.forja.corazon_de_volcan` / `material.forja.corazon_de_volcan` | Corazón de volcán / corazón de volcán | Volcano Heart / volcano heart |
| `item.forja.eclipse` / `material.forja.eclipse` | Eclipse / eclipse | Eclipse / eclipse |
| `item.forja.astralita` / `material.forja.astralita` | Astralita / astralita | Astralite / astralite |
| `conjunto.forja.espectracero` | +2 de dureza, +4 de vida, el fuego te dura menos y el amparo vuelve cada 15 s | +2 toughness, +4 health, fire lasts less on you and the shelter returns every 15 s |
| `conjunto.forja.corazon_de_volcan` | +2 de daño y el fuego te dura mucho menos | +2 damage and fire lasts much less on you |
| `conjunto.forja.eclipse` | +20 de maná máximo y andas agachado más deprisa | +20 max mana and you sneak faster |
| `conjunto.forja.astralita` | +30 de maná máximo y +1 de dureza | +30 max mana and +1 toughness |
| `trait.forja.amparo` (`.desc`) | Amparo (De vez en cuando, ningún golpe pasa del 40 % de tu vida) | Sheltering (Now and then, no blow takes more than 40% of your health) |
| `trait.forja.amparo.largo` | Una vez cada 20 s (15 con el conjunto), lo que la armadura deja pasar de un golpe no pasa del 40 % de tu vida máxima, y el que te pegó arde en llama fatua. Vale llevado o en la mano. No contra caídas, el vacío ni lo que la armadura no lee. Flechas: Resistencia I 1,5 s al que dispara. | Once every 20 s (15 with the full set), what armour lets through of a blow does not pass 40% of your max health, and whoever struck you catches wispfire. Worn or held. Not against falls, the void or what armour does not read. Arrows: Resistance I for 1.5 s to the archer. |
| `trait.forja.ardor` (`.desc`) | Ardor (Quema más cuanto peor te va) | Ardour (Burns hotter the worse it goes) |
| `trait.forja.ardor.largo` | Armas: +0,5 de daño por cada 20 % de vida que te falta, hasta +2, y por debajo del 60 % el golpe prende 2 s. Armadura: si un golpe te deja por debajo del 40 %, Resistencia al fuego 6 s (y te apaga con el conjunto), una vez cada 30 s. Flechas: +0,25 por cada 20 % que te falta. | Weapons: +0.5 damage for every 20% of health you are missing, up to +2, and under 60% the blow sets fire for 2 s. Armour: a blow that leaves you under 40% gives Fire Resistance for 6 s (and puts you out with the full set), once every 30 s. Arrows: +0.25 for every 20% missing. |
| `trait.forja.penumbra` (`.desc`) | Penumbra (La magia sale más barata a oscuras) | Penumbra (Magic comes cheaper in the dark) |
| `trait.forja.penumbra.largo` | En la mano, a oscuras: los hechizos cuestan un 15 % menos. Armas: golpear algo a oscuras da 0,5 de maná. Armadura: herido a oscuras, 0,25 de maná por punto de daño y pieza, hasta 3. Flechas: ciegan 2 s a lo que está a oscuras. | In hand, in the dark: spells cost 15% less. Weapons: striking something in the dark gives 0.5 mana. Armour: hurt in the dark, 0.25 mana per point of damage and piece, up to 3. Arrows: blind what stands in the dark for 2 s. |
| `trait.forja.sideral` (`.desc`) | Sideral (Hechizos más baratos, más aún bajo las estrellas) | Sidereal (Cheaper spells, cheaper still under the stars) |
| `trait.forja.sideral.largo` | En la mano: los hechizos cuestan un 10 % menos, un 20 % de noche a cielo abierto (no se suma con Místico ni Penumbra: vale el mejor). Herramientas: 0,1 de maná por bloque. Flechas: 1,5 de maná al que dispara y el objetivo brilla 3 s. | In hand: spells cost 10% less, 20% at night under open sky (does not add to Mystic or Penumbra: the best one counts). Tools: 0.1 mana per block. Arrows: 1.5 mana to the archer and the target glows for 3 s. |
| `flecha.forja.especial.velo` / `.desc` | Velo / Resistencia I %s s al que dispara | Veil / Resistance I for %s s to the archer |
| `flecha.forja.especial.ardor` / `.desc` | Ardor / +%s por cada 20 %% de vida que te falta | Ardour / +%s for every 20%% of health you are missing |
| `flecha.forja.especial.sombra` / `.desc` | Sombra / ciega %s s a lo que está a oscuras | Shadow / blinds what is in the dark for %s s |
| `flecha.forja.especial.astro` / `.desc` | Astro / %s de maná y el objetivo brilla | Star / %s mana and the target glows |
| `gui.forja.libros.aleaciones_cumbre.intermedias` | Las intermedias | The middle tier |
| `gui.forja.libros.aleaciones_cumbre.donde` | El espectracero y el corazón de volcán salen de la fragua de almas del Nether; el eclipse, de la fragua del vacío del End; la astralita, del crisol de obsidiana con cubas, como las cumbre. | Spectresteel and volcano heart come from the Nether's soul forge; eclipse from the End's void forge; astralite from the obsidian crucible with tanks, like the peak alloys. |

Los `%%` siguen la regla de `check_formats` de `generate_lang.py`. Después: `python tools/generate_lang.py` y
`python tools/generate_assets.py` (Python310 por ruta completa, desde archivo).

## 4. Pruebas

### 4.1 `src/gametest/java/dev/forja/test/CumbreGameTests.java` (registrar en `fabric-gametest` de `fabric.mod.json`)

| Prueba | Qué comprueba |
|---|---|
| `cumbreRecipesUseTheHeartAndTwoAlloys` | las tres recetas: llevan `CORAZON_DE_FORJA`; ≥ 2 ingredientes que son lingote de otro material (`ForgeMaterial.fromInput` ≠ null y ≠ `CORAZON`); ≥ 3 ingredientes; calor `FORJA_BLANCA`; están en `PEAK`, `WHITE_HEAT_ONLY` y `FOUNDRY_ONLY`; `place == ANY`; salen 2 / 1 / 1 |
| `obsidianCruciblePoursEachPeakAlloy` | por cada una, con el montaje de `FundicionGameTests.crucibleAlloysBesideAnotherBank` pero con `CRISOL_DE_OBSIDIANA`: corazón + la aleación que dice 2.2 en los huecos (acero vivo / obsidiacero / astralita), la tercera en una cuba (`tank.fill`), una cuba vacía; tras `Tier.OBSIDIANA.cook + 10` la cuba vacía tiene el lingote cumbre en la cantidad de la receta y los huecos están vacíos |
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
| `peakIngotsAreEpicAndFireproof` | los tres lingotes: `Rarity.EPIC` y no se queman en lava ni fuego; los cuatro intermedios, `Rarity.RARE` y tampoco |
| `middleRecipesAreTwoAlloysAndOneMore` | las cuatro intermedias: ≥ 2 ingredientes que son lingote de una aleación de `Alloys.ALL` o `ModItems.ORICALCO`, exactamente 1 que no lo es, sin corazón de forja; sus `Place` (`ALMAS`, `ALMAS`, `VACIO`, `ANY`) y salen 2 |
| `theFarForgesMakeTheMiddleTier` | con el montaje de `FraguasLejanasGameTests.theSoulForgeMakesWispfireInTheNetherOnly`: la fragua de almas encendida en el Nether hace una tanda de espectracero y otra de corazón de volcán (2 lingotes cada una, 1 polvo de blaze); la del vacío en el End, una de eclipse; fuera de su dimensión no funden |
| `obsidianCruciblePoursAstralite` | como `obsidianCruciblePoursEachPeakAlloy`: 2 oricalco + 1 eterio en los huecos, hierro estelar en una cuba → 2 astralita; con `CRISOL_DE_HIERRO`, nada |
| `middleTierStaysUnderTheHeart` | espectracero: `durability`, `armorDurability`, `toughness`, `handleDurability` < las del corazón; corazón de volcán: `attackDamageBonus` < el del corazón; eclipse y astralita: `enchantability` ≤ 25 y `miningSpeed` ≤ 9,5; las cuatro, armadura ≤ 20 y cada una por debajo de la cumbre que alimenta en el número de su especialidad |
| `shelterCapsOnceThenWaits` | con 1 pieza de espectracero y vida máx. 20: `capped` con 30 de `mobAttack` → 8,0 y el atacante con llama fatua; otra vez enseguida → 30; tras `SHELTER_COOLDOWN` → 8,0; con el conjunto, la espera es 300; con égida y espectracero a la vez vale el tope más bajo y la espera de Amparo no se gasta |
| `ardorBurnsWhenThingsGoBadly` | espada de corazón de volcán: `weaponBonus` 0 a vida llena y 2,0 a 4/20; a 12/20 el zombi arde y a 16/20 no; armadura con 4 piezas, `onHurt` a 7/20 → Resistencia al fuego y `getRemainingFireTicks() == 0`; otra vez antes de 600 ticks → no |
| `penumbraOnlyInTheDark` | con un báculo de eclipse, `gearSpellCost` 0,85 a luz 0 (bloques de piedra encima) y 1,0 a pleno sol; golpear a un zombi a oscuras da 0,5 de maná y a la luz 0; con 4 piezas, `onHurt` con 4 de daño a oscuras da `min(3, 4 × 0,25 × 4) = 3` |
| `siderealCheaperUnderTheNightSky` | con un báculo de astralita, `gearSpellCost` 0,90 de día y 0,80 de noche a cielo abierto sin lluvia; con arcanio en la otra mano, 0,75 (el mínimo, no el producto); un pico de astralita da 0,1 de maná por bloque |
| `middleSetsAndArrows` | conjuntos de 2.7 (atributos y `Mana.maxOf` +20 / +30); `ArrowTips.special`: `VELO`, `ARDOR`, `SOMBRA`, `ASTRO`; `wrathBonus(ARDOR)` a 4/20 = 1,0 |
| `middleAndPeakMakeEveryPart` | `peakAlloysMakeEveryPart` cubre también las cuatro intermedias |

Las de siempre que las cubren solas y deben seguir pasando: `MaterialesGameTests` (todas; `everyAlloyCanBePoured`
con el cambio de 3.21 y, para las tres de fragua lejana, la regla del hogar de su fragua),
`KitsGameTests.everyMetalHasAKitARecipeAndATexture` (los siete kits salen de `ALLOY_COLORS`),
`FraguasLejanasGameTests.nothingElseMakesTheFarAlloys` y `theFarAlloysMakeEveryPart` (recorren todo lo que no es
`ANY`: ahora seis), `LibrosGameTests`, `ArmaduraGameTests`, `BalanceGameTests`. Ninguna de ellas lista ids a mano;
si alguna los listara, se añaden los nuevos.

### 4.2 Equilibrio: qué umbrales pueden moverse

| Prueba | Qué se espera | Si falla |
|---|---|---|
| `ArmaduraGameTests` (`MAX_OVER_NETHERITE = 5.0`) | égida = fila del obsidiacero (+3,9); iracero y arcanio por debajo. Intermedias: 19–20 de armadura, empuje ≤ 0,10 y sin `ARMOR` plano en el conjunto, así que por debajo de la fila del corazón (+3,2). **No se toca** | si la égida pasa de +5, bajar su empuje a 0,10 (fila del corazón, +3,2). Si pasa una intermedia, bajar su empuje a 0,05 y, si no basta, su pechera de 8 a 7. Nunca subir el umbral |
| `equilibrioSinDominados` | sin cambios: siete rasgos nuevos, ninguno compartido, sin inversiones nuevas (todas tienen rasgo) | parar y avisar |
| `aleacionesDeFraguaEnSuSitio` | ahora mide también espectracero, corazón de volcán y eclipse. Ninguna es «netherita mejor» (2.7). Ninguna debería estar en más de la mitad de las mejores armas: sus rasgos valen 0 en el simulador (se pelea a vida llena, de día y sin hechizos) y sus cabezas no pasan de +4,0 | si el corazón de volcán sale en más de la mitad, bajar su cabeza a +3,5 y medir otra vez; si es otra o sigue, parar y preguntar. El umbral (la mitad) no se mueve |
| `magiaEnSuSitio` (`MAGIC_PLAIN_FLOOR 1.0`, `MAGIC_MAGE_CEILING 1.3`, `MAGIC_TOME_CEILING 1.5`) | el simulador no cuenta Místico, Penumbra ni Sideral (como Astral). La cabeza de iracero hace más rápida la mediana cuerpo a cuerpo (~5–10 % al 100 %): el Mago queda más cerca del techo (hoy báculo ×0,86, grimorio ×1,30). Las intermedias no cambian la mediana: ninguna pasa del corazón | si `MAGIC_TOME_CEILING` o `MAGIC_MAGE_CEILING` fallan **solo** porque bajó la mediana, subirlos a **1,6** y **1,4**; si falla `MAGIC_PLAIN_FLOOR` (báculo sin clase hoy ×1,02 al 100 %), **no** se mueve: se baja la cabeza de iracero a +5,0 y se mide otra vez; si una intermedia entra en el báculo o el grimorio y lo hace fallar, se baja su cabeza 0,5; si sigue, se para y se pregunta |
| `herreroEnSuSitio` | el equipo «cc» toma la mejor arma, que llevará iracero: la pelea baja ~5–10 % (fácil 1:52 → ~1:43, ventana 1:30–3:00). Las intermedias van por debajo del corazón y no deberían mover nada; espectracero y corazón de volcán no llevan corazón y sí existen en la primera pelea | si una ventana se rompe, **no** se mueve: en `SmithFight` se quitan de la búsqueda de armas los tres materiales de `Alloys.PEAK` (salen de su corazón: en la primera pelea no existen). Si aun así se rompe, quitar también las cuatro de `MIDDLE` y avisar |

### 4.3 Cliente: `FORJA_SOLO=cumbre`

`src/gametest/java/dev/forja/test/CumbreFootage.java`, con `film(context, server, connection, x, y, z)` como
`OricalcoFootage`, y la rama `if ("cumbre".equals(solo))` en `ForjaClientTest` junto a la de `"oricalco"`. Capturas
`cumbre_NN_*.png` (prefijo fijo para la hoja):

| # | Captura | Qué |
|---|---|---|
| 01 | `cumbre_01_lingotes` | los siete lingotes (cuatro intermedios y tres cumbre) y sus kits en mesas de crafteo (como `KitsFootage`) |
| 02 | `cumbre_02_crisol` | crisol de obsidiana en línea con dos cubas, corazón y obsidiacero en los huecos, espectracero en la cuba; pantalla del crisol colando «Égida» |
| 02b | `cumbre_02b_intermedias` | las cuatro intermedias en soportes de armadura en fila (conjunto con forro de cuero), cada una con su espada en un marco: espectracero, corazón de volcán, eclipse, astralita |
| 03–05 | `cumbre_03_iracero`, `_04_egida`, `_05_arcanio` | soporte de armadura con el conjunto (placa del metal, forro de cuero), de frente y de espaldas, con espada y pico del metal en marcos al lado |
| 06 | `cumbre_06_mano` | primera persona: espada de iracero y escudo de égida, con las partículas de `GearAura` |
| 07 | `cumbre_07_libro` | libro III abierto en «Aleaciones cumbre» |
| 08 | `cumbre_08_jei` | si JEI está cargado en la prueba de cliente, la receta del arcanio con «Crisol de obsidiana con cubas»; si no, se omite y se dice |

Hoja de contacto: `tools/hoja_cumbre.py`, copia de `tools/hoja_oricalco.py` con texturas `espectracero`, `corazon_de_volcan`, `eclipse`, `astralita`, `iracero`, `egida`,
`arcanio` y sus `kit_de_reparacion_*`, y capturas `cumbre_*.png`. Escribe
`E:\IA\Claude\Forja_capturas_mejoras\aleaciones_cumbre\hoja_cumbre.jpg` y copia allí las capturas sueltas.

### 4.4 `CHANGELOG.md`

Entrada nueva arriba: `## <fecha> — Aleaciones cumbre: iracero, égida y arcanio, y su escalón intermedio`, con una viñeta por aleación (las siete)
(receta, salida, números clave, rasgo), una de dónde se hacen (crisol de obsidiana con cubas, mesa de almas), una de
guía/JEI, una de pruebas (`CumbreGameTests`, `FORJA_SOLO=cumbre`, hoja en `Forja_capturas_mejoras/aleaciones_cumbre`)
y lo que haya cambiado en umbrales según 4.2 (o «ningún umbral cambia»).
