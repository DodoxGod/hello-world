# Resumen de la sesión en la nube — 2026-09-29

Para Andy. Todo está en la rama `claude/hola-rv9w0u` (PR #1). Último commit con el CI en verde (compilación y todas
las pruebas de servidor): `32a1c81`. El detalle de la primera tanda está en `docs/RESUMEN_2026-09-29.md`; aquí va
todo junto.

## Cómo lo he probado sin tu PC

- **Compilar y probar:** en esta máquina no se puede compilar (Gradle no tiene red), así que lo hace GitHub Actions
  en cada push. `runGameTest` va dentro del build.
- **Capturas del juego:** con `[capturas:SECCIÓN]` en el mensaje de un commit, `.github/workflows/capturas.yml`
  arranca el cliente de verdad con pantalla virtual, ejecuta la sección `FORJA_SOLO=SECCIÓN` de las pruebas de
  cliente y devuelve las capturas en el registro. Yo las decodifico, las miro y te hago las hojas.
- **Mini jar del cliente:** el build exporta un jar pequeño del cliente, con los estados de bloque y los colores,
  para que el generador del castillo funcione en la nube.

## 1. El castillo del Herrero (Bastión del gremio)

Dos pasadas al generador (`tools/castillo*.py`).

**Primera pasada:**

| Tu pedido | Qué hice |
|---|---|
| Sitio: nada de montaña encima ni entrada en el mar | Antes de colocarlo mira 81 puntos alrededor. Lo descarta si hay agua, más de 14 bloques de desnivel o el centro fuera de la altura típica (`BastionGround` + `JigsawStructureMixin`). El terreno se despeja hasta el tejado. |
| Bloques con agua dentro | `liquid_settings: ignore_waterlogging` |
| Una sola fragua apagada | Solo queda la del sótano; la del monumento es ahora un alto horno apagado. |
| Bloques flotantes y de "medir" | Fuera los marcadores de encaje que salían en el aire y todo lo que colgaba de nada. |
| Bloques caros y demasiado botín | Núcleos, oro, diamante, magnetita, el yunque del Herrero… se cambian por bloques del mismo aspecto. Quedan 43 cofres en vez de 164, con tope por tabla y nunca juntos. |
| Entradas de 1 bloque | Puertas de 3 de ancho que llegan hasta el techo. |
| Configuraciones imposibles | Se acabó la escalera boca abajo con farol y los yunques en las tumbas. |
| Solape con otras estructuras | Exclusión de 10 chunks con las aldeas, y de 12 entre el castillo pequeño y el grande. |
| Pocos mobs | Guarnición por plantas según el tamaño de la sala: 89 monstruos (antes 32). |
| Soportes de armadura en alfombras o jarrones | Solo quedan contra una pared y sobre suelo firme. |
| Construir y romper en la pelea del jefe | Mientras el Herrero Caído vive, nadie rompe ni pone bloques ni cubos a menos de 32 bloques (salvo en creativo). |

**Segunda pasada:**

- **Uniones:** vallas, paneles, barrotes, muros y escaleras se unen con las reglas del juego. Cambiaron 152
  uniones.
- **Solapes (K):**
  - Un ala se comía un torreón del torreón principal (437 bloques). La fundición hacía lo mismo con otro.
  - La cantera y el cementerio abrían huecos en dos torres.
  - La cúpula de la Forja Profunda se comía las cuatro pilas de templado y el fondo de la cisterna. Eran tus
    "columnas de agua o hielo con cadenas".
- **Cuartos sin techo:** solo quedan agujeros en la casa quemada, que es una ruina. Además, 13 velas de la cripta
  estaban sobre medias losas.
- **Escaleras sin salida:** fuera dos tramos vacíos. Se recolocaron las de la sala de temple, la tumba y la
  nevera, y la puerta del observatorio ya atraviesa todo el muro.
- **Salas a las que no se podía llegar:** ahora se llega a todas estas:
  - las plantas 2 a 5 del torreón;
  - la capilla y la sacristía;
  - el gabinete de orbes, la despensa y el almacén de lingotes;
  - tres torres y varios adarves.
- **Decoración:**
  - 0 farolas de pie. En su lugar, cadenas del techo, al menos a 7 bloques una de otra para que el gran salón no
    sea un bosque, y apliques en la pared.
  - Las "banderitas" del patio son una barandilla con estandartes.
  - Las celdas de rastrillos tienen muro hasta el techo.
  - Almacenes y salas de guardia con tres distribuciones cada uno.
  - El almacén alto del torreón pasa de 28 pilas de barriles a 10.
- **Otros fallos que encontré:** lava que se escapaba por puertas, grava sobre aire y una flor sobre piedra.

**Imágenes:**
- `docs/castillo/antes_despues_2026-09-29/`: 62 vistas del juego, antes y después de la primera pasada.
- `docs/castillo/segunda_pasada_2026-09-29/juego/`: las mismas 62 vistas, primera pasada frente a segunda.
- `docs/castillo/segunda_pasada_2026-09-29/*.png`: 18 dibujos de corte y planta de las salas que cambiaron.

Las capturas del juego son anteriores al último ajuste de las cadenas.

**Sin arreglar:**
- La sala de los tornos de la entrada, las plantas altas de sus torres y la pasarela de la fundición no tienen
  acceso.
- Algunas uniones con bloques del mod son aproximadas.

## 2. Grupos de mobs

- La mitad de los compañeros pueden ser de otro tipo: zombi, esqueleto, araña o creeper, con sus variantes del
  desierto y la nieve.
- Como mucho, un creeper por grupo.
- Los compañeros nunca son élites, veteranos ni campeones.
- Solo el primero de un grupo de aparición trae compañeros, así que ya no salen 12 zombis con 2 élites.
- Tamaños: de 2 a 3 los normales y de 3 a 6 con un veterano.
- Pruebas: `packsMixKindsAndHaveOneLeader` y `veteransComeInPacks`.

## 3. IA de los mobs (el contrato v3 no cambia)

- **Creeper:** ya no pasa por el aviso de golpe cuerpo a cuerpo, que lo frenaba, le quitaba un turno a los demás
  y lo hacía saltar atrás. Enciende la mecha pegado a ti aunque su red no lo pida, y finta menos (35 % → 15 %).
- **Rodear de verdad:**
  - Solo los de cuerpo a cuerpo ocupan hueco en el anillo.
  - El inicio del anillo no gira durante la pelea.
  - En grupo se abren desde 16 bloques en vez de llegar en fila.
  - Con 13 salen grupos de 3 o 4 por lado: detrás, cada lado y delante.
- **Correr a su sitio:** corren si su hueco está a más de 5 bloques.
- **Esqueletos:** si un aliado les tapa el tiro, dan un paso de 2 bloques hacia el otro lado.
- **Simulador:** lo que tiene que copiar el simulador está en `docs/red_mob_v4_propuesta.md`. No ha hecho falta un
  contrato v4, porque lo que ve la red no cambia.
- **Pruebas:** 6 nuevas en `FormacionGameTests`, entre ellas `thirteenSurroundInFourGroups`.
- **Capturas:** `FORJA_SOLO=cerco`, 13 zombis y 2 esqueletos vistos desde arriba, en
  `docs/capturas_2026-09-29/cerco.jpg`.
- **Élites y capitanes:** conservan salto, segundo aliento y cuerno al recargar el chunk (prueba
  `anEliteKeepsItsMovesAfterAReload`).

## 4. Clases y farol de curación

- **Las clases:** 7 (Guerrero, Asesino, Tanque, Mago, Curandero, Arquero y Herrero), con niveles hasta el 15, 70
  talentos en 3 ramas por clase y 14 habilidades.
- **Cambiar de clase:** con el Emblema del olvido; se conserva el nivel y vuelven los puntos.
- **Farol de curación:**
  - Lo puede usar cualquiera. Un toque lanza un rayo al primer aliado; cargado, suelta un anillo que cura a los
    aliados y al que lo usa a la mitad.
  - Cuesta 12 de maná por toque y un 25 % más cargado, como confirmaste.
- **Maná real:** está unido a `magic/Mana.java`. Las clases cambian el maná máximo, su regeneración y el coste,
  la carga y la espera de los hechizos. Estamina y esquive van por los mismos cálculos que las mejoras.
- **Tu decisión:** el Curandero se cura a sí mismo un tercio de lo que cura a otros con el báculo y el grimorio.
- **Textos:** 268, en español e inglés, y un capítulo "Clases" en la guía.
- **Pruebas:** 6 en `ClasesGameTests`. Capturas en `docs/capturas_2026-09-29/clases.jpg`.

## 5. Animaciones de los mobs

- **Fallo de GeckoLib 5.5.5:** los 14 monstruos del mod, no solo la Coraza Vacía, se quedaban congelados tras su
  primer golpe o especial. Arreglado en todos mediante `MobMoves`, que terminé en vez de descartarlo.
- **Revisión de cada monstruo:** aviso, golpe, especiales, correr, aturdido, daño y muerte. Cada aviso dura lo
  mismo que en el código.
- **Herrero Caído:** ahora tiene animaciones de correr, llamar a los aprendices y la lluvia de estrellas. Cambio
  de juego: la lluvia de estrellas marca el sitio y cae 20 ticks después (antes no avisaba).
- **Pavesa y ascua mayor:** ya no se lanzan en picado antes de acabar su aviso.
- **Capturas:** en `docs/capturas_2026-09-29/animaciones_mobs/`.

## Estado de las pruebas

- En `32a1c81` compila y pasan todas las pruebas de servidor.
- **Pruebas intermitentes:**
  - `flail_zombie_strikes_from_its_reach` (prueba de alcance): falló una vez en `6f0eef0` y pasó en el otro run
    del mismo commit. No está en tu lista de inestables y yo toqué la IA, así que no descarto que tenga que ver. No
    lo he investigado.
  - `telegraphed_attack_hits_still_player`, que sí está en tu lista de inestables: falló una vez y pasó al
    repetir.
- **Pruebas nuevas de esta sesión:** 15, en `ClasesGameTests`, `FormacionGameTests`, `GruposGameTests` y
  `JefeGameTests`.

## Lo que no pude hacer

- **"Antes" de la IA:** no hay capturas de la IA antigua. Hacía falta un commit temporal con la IA vieja y el
  sistema de permisos lo bloqueó, así que el cerco solo tiene el "después", con el recuento por lados.
- **Propuestas del simulador:** `docs/PROPUESTAS_IA_SIMULADOR.md` todavía no existe en la rama. Cuando el chat
  del simulador lo escriba, lo leo y añado lo que valga la pena.

## Preguntas para ti

1. **Redes de los mobs vanilla.** Hoy reutilizan redes así:
   - la del zombi: esqueleto wither, piglin zombificado, piglin bruto, vindicador y hoglin/zoglin;
   - la del arquero: saqueador;
   - la del tanque: devastador;
   - la del enjambre: lepisma y endermita.

   ¿Te vale así? ¿Blaze y voladores con red propia?
2. **Reglas contra otros monstruos.** ¿Usan los monstruos las reglas del mod (avisos, turnos, cerco) también
   contra otros monstruos, o solo contra jugadores, como ahora?
3. **Daño al jefe.** ¿El daño al jefe de lo que no es un jugador se queda en 0,5?
4. **Lluvia de estrellas.** ¿Te parece bien el aviso de 20 ticks antes de que caiga?
5. **Decisiones pendientes de fundición 2, maná y enderman.** Están en el CHANGELOG. Además quedan 5 de las clases
   en `docs/CLASES.md`:
   - cambiar de clase conserva el nivel;
   - el Curandero no hace daño con magia;
   - el tope es 15;
   - el Emblema del olvido cuesta un fragmento de eco;
   - las teclas K, V y B.
6. **Castillo, parte sin arreglar.** ¿Quieres que dé acceso a la sala de los tornos y a la pasarela de la
   fundición, o las dejamos como zonas cerradas?
