# Redes entrenadas de los monstruos

Las 10 redes que el mod carga de `config/forja/redes/red_<familia>.json` (copia del 2026-09-29, de la instancia de
Andy). Formato `red_mob_v3` (contrato `docs/red_mob_v3_contrato.json`): 280 entradas, 34 salidas (v3b, con la
cabeza de correr), iteración 24 900, 2 ticks por decisión. Las entrena la sesión "Cómo funcionan los LLM" en el
simulador externo (Rust), que sigue entrenando: puede haber versiones más nuevas.

Para probarlas en el juego de desarrollo: copia estos archivos a `run/config/forja/redes/` (cliente) o a la carpeta
que diga `iaCarpetaRedes` en `config/forja.json`. `MobAi.check` rechaza (y lo dice en el log) cualquier red cuyas
entradas no coincidan en nombre y orden con las del mod; `/forja ia` dice cuántas cargó.
