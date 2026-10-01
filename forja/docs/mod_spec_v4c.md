# Paso v4c del simulador (30-09): qué necesita el mod

Resumen: **nada nuevo obligatorio.** Todo lo del paso v4c copia lo que el mod ya hace (red_mob_v4_mod_estado.md, rama
`claude/hola-rv9w0u`), salvo cosas que solo existen para entrenar (el jugador de reglas y los premios). Quedan 3 cosas que
conviene **comprobar** en el mod, porque el simulador las da por hechas:

1. **Cambio de familia a mitad de pelea.** En el simulador, un esqueleto con arma de mano (familia "cuerpo") que coge un arco
   del suelo pasa a "arquero", y un arquero que saca la hoja del repuesto (objeto 8, "cambiar de arma") pasa a "cuerpo". Al
   cambiar de familia usa la red de la familia nueva y su memoria (GRU) vuelve a 0. Comprobar que `MobAi.think` hace lo mismo
   cuando `MobFamily.of(mob)` cambia por el arma de la mano (el diseño §4.7 lo pide; mod_estado no lo dice).
2. **El repuesto puede ser un arco.** En el simulador, el arquero que saca la hoja guarda su arco en el repuesto (antes se
   perdía) y puede volver a sacarlo; la máscara de la salida 8 se abre con una hoja o un arco en el repuesto (no con una caña).
   Comprobar que `MobItems` hace el mismo intercambio mano ↔ repuesto con un arco, y que el arco del repuesto se suelta al morir
   (el recogido siempre; el de aparecer con su probabilidad de vanilla).
3. **Arcos en el suelo solo para tiradores.** El simulador copia `GroundItems.valueFor`: un arco (3) o una ballesta (3,8) solo
   valen para un esqueleto o un stray; un zombi nunca coge un arco. Si Andy quiere que un zombi que coge un arco pase a
   arquero (el diseño §4.7 lo insinuaba), eso SÍ sería nuevo en el mod (y en el simulador); hoy ninguno de los dos lo hace.

Lo que es solo del simulador (no hay que tocar el mod):
- Percepción honesta del jugador de REGLAS (cono, luz, oído, memoria): es el rival de entrenamiento, no existe en el juego.
- El jugador de reglas que suelta su arma y vuelve a por ella (en el juego lo hace Andy con Q o al morir; el mod ya deja que los
  mobs la cojan y la suelten al 100 %).
- Los premios `reagrupar` y `cubierto`.
- Las campañas: el W pasa de pelea en pelea como el `WorldMemory` del mod (causas, éxito por forma, estilo al acabar la pelea con
  la media 0,1, confianza = muertes / 50). El olvido por días de juego no se simula dentro de una campaña de 8 peleas.

`red_capitan.json` (de `combate/entrenar_capitan.py`): "formato": "red_capitan_v4", los 213 nombres de
`red_capitan_v4_contrato.json`, 60 salidas, GRU 64; es lo que `MobAi.loadCaptain` + `CaptainBrain.check` aceptan. Va en
`config/forja/redes_v4/red_capitan.json`. Los logits exportados coinciden con los de torch en NetBrainPy (error < 1e-7).
