# Filo

Mod de Minecraft (Fabric 1.21.1) que rehace el combate: **más difícil, pero justo**. Todo ataque peligroso se anuncia antes de llegar.

> Nombre provisional. Estado: primera versión. La lógica de combate está probada automáticamente en un servidor real de Minecraft; falta probarla jugando.

## Qué cambia

### Nuevo cálculo de armadura
Sin armaduras nuevas: las de siempre protegen de otra forma.
- **Curva sin tope duro:** `reducción = armadura / (armadura + 20)`, con un máximo del 80 %.
- **Tipos de daño:** corte (espadas, hachas, arañas), golpe (puños, mazas, zombis, explosiones) y perforación (flechas, tridentes, picos).
- **Cada material resiste distinto:** el cuero aguanta golpes, la cota de malla el corte, y la netherite un poco de todo.
- **Penetración:** hachas, flechas tensadas y tridentes ignoran parte de la armadura. La **dureza** reduce esa penetración.
- **Zonas del cuerpo:** el casco protege sobre todo la cabeza, el peto el torso, etc. Los golpes precisos a la cabeza (tus flechas, tus espadazos, los de los esqueletos) hacen +30 %.
- **Durabilidad:** una pieza gastada protege hasta un 40 % menos.
- **Peso:** la armadura pesada te frena un poco y hace que la estamina se recupere más lento.

### Estamina, esquiva y parry
- **Estamina** (barra fina sobre la comida): atacar, esquivar y bloquear la gastan. Si atacas sin estamina, haces un 40 % menos de daño.
- **Esquiva** (tecla `Alt izquierdo`, configurable): un salto rápido en la dirección en la que te mueves, o hacia atrás, con unos ticks de invulnerabilidad.
- **Parry:** si levantas el escudo justo antes del golpe, lo anulas, recuperas estamina y aturdes al atacante.
- **Bloqueo:** bloquear con escudo gasta estamina según el daño. Sin estamina, la guardia se rompe.

### Postura de los mobs
Cada golpe llena una barra oculta de equilibrio (los golpes contundentes más). Cuando se llena, el mob queda **aturdido** 2 segundos: no ataca, apenas se mueve y recibe +25 % de daño.

### IA de los mobs cuerpo a cuerpo
- **Aviso:** antes de golpearte, se paran, sueltan partículas y un sonido, y esperan 0,4 s. Si te apartas, el golpe falla. Contra aldeanos y otros mobs atacan como siempre.
- **Turnos:** solo 2 mobs pueden atacarte a la vez; el resto espera su turno.

## Configuración
Todos los números están en `config/filo.json` (se crea al arrancar el juego). Puedes desactivar cada sistema por separado y ajustar la dificultad sin tocar código.

## Descargar
Cada subida a GitHub compila el mod automáticamente. En la pestaña **Actions** → última ejecución → **Artifacts** → `filo-mod` está el `.jar`. Cópialo a la carpeta `mods` junto con [Fabric API](https://modrinth.com/mod/fabric-api).

## Pruebas automáticas
En cada subida, GitHub arranca un servidor real de Minecraft con el mod y ejecuta 10 pruebas (`FiloGameTests`): el cálculo de armadura sin armadura y con hierro, una flecha a la cabeza, la rotura de postura, el ataque sin estamina, la esquiva, el parry, un mob golpeando a un jugador tras avisar y los mobs atacando aldeanos como siempre. Para lanzarlas a mano: `./gradlew runGametest`.

Lo que no cubren: la parte visual (barra de estamina, partículas, sonidos) y la tecla de esquivar, que es del cliente.

## Compilar a mano
```
./gradlew build
```
El `.jar` queda en `build/libs/`.

## Pendiente
- Animaciones nuevas (combos, esquiva) y *hitstop*.
- Ataques especiales por mob (embestida del zombi, disparo cargado del esqueleto, finta del creeper).
- IA de grupo más avanzada (rodear, retirarse) y moral.
- Armas nuevas (daga, maza pesada).
