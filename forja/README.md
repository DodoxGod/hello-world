# FORJA

## Qué es

**Forja** es un **mod** para Minecraft (no un modelo suelto): herramientas, armas y armaduras que se fabrican
**pieza por pieza** en mesas propias (mesa de piezas, mesa de forja, talabartería), cada pieza de un material
distinto. Los objetos forjados no se encantan: llevan **mejoras** con porcentaje. Además trae aleaciones que
dependen del calor bajo la mesa, crisoles y colada de metal, maestría de objeto y de herrero, mobs propios
(varios animados con GeckoLib), un jefe (el **Herrero Caído**, en el Nether), eventos del cielo, un aldeano
Forjador y estructuras (forja abandonada, taller de montaña, castillo de forja, Bastión del gremio...).

Esta carpeta es el **proyecto completo** y se compila tal cual:

| Ruta | Contenido |
|---|---|
| `src/main/java/` | Código del mod (Java) |
| `src/main/resources/` | Recursos: modelos JSON, modelos y animaciones de GeckoLib, texturas y `.mcmeta`, capas de armadura, traducciones, recetas, loot tables, avances, tags, worldgen, 110 estructuras `.nbt`, `fabric.mod.json` y mixins |
| `src/gametest/` | Pruebas dentro del juego (client gametest) |
| `tools/` | Scripts en Python que generan texturas, traducciones y las estructuras del castillo |
| `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/` | Compilación con Gradle |

## Versión

- **Minecraft Java Edition 26.2** (no Bedrock).
- **Fabric**: Loader 0.19.3, Fabric API 0.158.0+26.2, Loom 1.17, **Java 25**.
- Dependencia obligatoria: **GeckoLib 5.5.5**. Opcionales: JEI y Jade (se descargan solos al compilar, desde el
  maven de Modrinth).

## Con qué se hizo

- **Fabric + Gradle (Loom)**, código en Java.
- Mobs animados con **GeckoLib** (modelos `.geo.json`, el formato de GeckoLib/Blockbench). No hay archivos
  `.bbmodel`: los modelos se escribieron directamente en `.geo.json`.
- Texturas y estructuras generadas con los scripts de `tools/` y retocadas a mano.

## Cómo se compila, instala y prueba

1. Con **Java 25** instalado (JAVA_HOME apuntando a él), dentro de `forja/`:
   ```
   ./gradlew build
   ```
   El mod queda en `build/libs/forja-1.0.0.jar`.
2. Instalar **Fabric Loader 0.19.3** para Minecraft **26.2**.
3. Poner en `mods/`: el jar de Forja, **Fabric API** y **GeckoLib 5.5.5** (JEI y Jade opcionales).
4. En el juego, la **Guía de forja** (un libro, o la tecla G) lo explica todo.

Para probarlo sin instalar nada: `./gradlew runClient` abre el juego con el mod, y `./gradlew runClientGameTest`
abre el juego, recorre las mecánicas y saca capturas.

## Qué falta o qué problemas tiene ahora

- El **Bastión del gremio** (estructura grande de generación natural) está hecho pero **falta visitarlo en un
  mundo normal** para comprobar que aparece bien.
- Hay un **rediseño gráfico en curso** (lista revisada pieza por pieza): varias texturas están aprobadas y otras
  siguen pendientes de elegir entre propuestas.
- Dos ideas de mobs (**Fuelle** y **Sombra del Forjador**) están explicadas pero sin decidir.
- Al compilar salen 19 avisos del compilador (no impiden compilar).
- Un mob experimental (*crisol errante*), hecho en una prueba con un modelo local, **no se incluye**: su textura
  salía transparente y su código no compilaba.
