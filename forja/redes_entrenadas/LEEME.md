# Redes entrenadas de los monstruos

Las 10 redes que el mod carga de `config/forja/redes/red_<familia>.json` (copia del 2026-09-29, de la instancia de
Andy). Formato `red_mob_v3` (contrato `docs/red_mob_v3_contrato.json`): 280 entradas, 34 salidas (v3b, con la
cabeza de correr), iteración 24 900, 2 ticks por decisión. Las entrena la sesión "Cómo funcionan los LLM" en el
simulador externo (Rust), que sigue entrenando: puede haber versiones más nuevas.

Para probarlas en el juego de desarrollo: copia estos archivos a `run/config/forja/redes/` (cliente) o a la carpeta
que diga `iaCarpetaRedes` en `config/forja.json`. `MobAi.check` rechaza (y lo dice en el log) cualquier red cuyas
entradas no coincidan en nombre y orden con las del mod; `/forja ia` dice cuántas cargó.

`red_blaze.json` es la red propia del blaze: formato `red_blaze_v1` (contrato `docs/red_blaze_contrato.json`,
explicado en `docs/red_blaze_contrato.md`), 324 entradas, 18 salidas, iteración 24 150, 2 ticks por decisión. Se
copió el 2026-09-29 de `E:\IA\agente\minecraft\combate\forja_blaze\red_blaze.json`. El mod la busca en
`redes_v4/` o en `redes/`.
