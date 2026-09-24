# FORJA

## Qué es

**Forja** es un **mod** para Minecraft (no un modelo suelto): herramientas, armas y armaduras que se fabrican
**pieza por pieza** en mesas propias (mesa de piezas, mesa de forja, talabartería), cada pieza de un material
distinto. Los objetos forjados no se encantan: llevan **mejoras** con porcentaje. Además trae aleaciones que
dependen del calor bajo la mesa, crisoles y colada de metal, maestría de objeto y de herrero, mobs propios
(varios animados con GeckoLib), un jefe (el **Herrero Caído**, en el Nether), eventos del cielo, un aldeano
Forjador y estructuras (forja abandonada, taller de montaña, castillo de forja, Bastión del gremio...).

Esta carpeta contiene **todos los recursos del mod** (resource pack + datapack integrados), con la misma
estructura que en el proyecto (`src/main/resources`):

| Carpeta | Contenido |
|---|---|
| `assets/forja/models/` | Modelos JSON de bloques y objetos (352) |
| `assets/forja/items/` | Definiciones de objetos de 26.x (130) |
| `assets/forja/blockstates/` | Estados de bloque (23) |
| `assets/forja/geckolib/` | Modelos `.geo.json` y animaciones de GeckoLib de los mobs (30) |
| `assets/forja/textures/` | Texturas `.png` y `.png.mcmeta` (480) |
| `assets/forja/equipment/` | Capas de armadura por combinación de materiales (1 638) |
| `assets/forja/lang/`, `particles/`, `icon.png` | Traducciones, partículas e icono |
| `data/forja/` | Recetas, loot tables, avances, tags, tradeos del Forjador, worldgen y **110 estructuras `.nbt`** |
| `data/minecraft/tags/` | Tags que el mod añade a los de Minecraft |
| `fabric.mod.json`, `forja*.mixins.json` | Descriptor del mod y configuración de mixins |

## Versión

- **Minecraft Java Edition 26.2** (no Bedrock).
- **Fabric**: Loader 0.19.3, Fabric API 0.158.0+26.2, **Java 25**.
- Dependencia obligatoria: **GeckoLib 5.5.5**. Opcionales: JEI y Jade.

## Con qué se hizo

- **Fabric + Gradle (Loom 1.17)**, código en Java.
- Mobs animados con **GeckoLib** (modelos `.geo.json`, el formato de GeckoLib/Blockbench). No hay archivos
  `.bbmodel`: los modelos se escribieron directamente en `.geo.json`.
- Texturas generadas con scripts propios (Python) y retocadas a mano; las estructuras `.nbt` también se generan
  con scripts (`tools/` del proyecto).

## Cómo se instala o se prueba

Estos archivos son **solo los recursos**: el código Java del mod no está en esta carpeta, así que por sí solos no
funcionan como resource pack (todo usa el espacio de nombres `forja`, que solo existe con el mod).

Para jugar con el mod completo:

1. Compilar el proyecto completo con `./gradlew build` (Java 25) → `build/libs/forja-1.0.0.jar`.
2. Instalar **Fabric Loader 0.19.3** para Minecraft **26.2**.
3. Poner en `mods/`: el jar de Forja, **Fabric API** y **GeckoLib 5.5.5** (JEI y Jade opcionales).
4. En el juego, la **Guía de forja** (un libro, o la tecla G) lo explica todo.

Para probarlo en desarrollo: `./gradlew runClientGameTest` abre el juego, recorre las mecánicas y saca capturas.

## Qué falta o qué problemas tiene ahora

- El **Bastión del gremio** (estructura grande de generación natural) está hecho pero **falta visitarlo en un
  mundo normal** para comprobar que aparece bien.
- Hay un **rediseño gráfico en curso** (lista revisada pieza por pieza): varias texturas están aprobadas y otras
  siguen pendientes de elegir entre propuestas.
- Dos ideas de mobs (**Fuelle** y **Sombra del Forjador**) están explicadas pero sin decidir.
- Un mob experimental (*crisol errante*), hecho en una prueba con un modelo local, **no se incluye**: su textura
  salía transparente y su código no compilaba.
