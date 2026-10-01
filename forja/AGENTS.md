# Forja: instructions for Codex (and any other coding agent)

Andy speaks Spanish: talk to him in Spanish. Code and comments in English. Docs, lang text and commit messages in Spanish.

## Read first
1. `CLAUDE.md` (in this folder): the project map. It says where everything is, how to build and test, and the step-by-step recipes for common tasks. It was written for Claude, but it applies to you too.
2. `E:\IA\Claude\Forja_traspaso_2026-10-01.md`: the current state, decisions Andy has taken, and what's next.
3. `docs/FUNDICION_V3.md`: **the next job**, the new foundry. It's a complete design approved by Andy, with his answers in section 18. Build it batch by batch, as the doc lists them.

## Rules
- **Don't redesign.** Implement what the docs say. On a real design doubt, stop and ask Andy, with concrete options and a recommendation. Technical choices are yours.
- **Repo:** `E:\IA\Claude\github\hello-world`, working branch `forja-ia-armas`, pushed to `claude/hola-rv9w0u` (`git branch -f claude/hola-rv9w0u HEAD && git push origin claude/hola-rv9w0u`). Push only when Andy asks, or when a batch is done and all tests pass.
- **Tests:**
  - From `forja/` with `JAVA_HOME=E:/IA/Claude/.tooling/jdk/jdk-25.0.4.1+1`, run `./gradlew runGametest`. It must say "All N required tests passed" in `build/run/gameTest/logs/latest.log`.
  - Client tests: `FORJA_SOLO=<section> ./gradlew runClientGameTest --init-script E:/IA/lora/datos/mc_vulkan.init.gradle`.
  - Never run `gradlew --stop`.
- **Balance tests guard thresholds:** fix the content, never the threshold, unless a design doc says so.
- **Test runs rewrite `docs/EQUILIBRIO.md`, `MATERIALES.md` and `MEJORAS.md`:** keep real changes and revert the noise.
- **Line endings:** `git diff --stat` must equal `git diff --stat --ignore-cr-at-eol`.
- **Generated files:**
  - Lang JSONs are generated: edit the Python sources in `tools/`, then run `tools/generate_lang.py`.
  - Assets come from `tools/generate_assets.py`, which is byte-stable.
  - Python: `C:/Users/andye/AppData/Local/Programs/Python/Python310/python.exe`. The `python` on PATH is blocked, so run scripts from files.
- **Changelog:** `CHANGELOG.md` gets an entry for each change, newest first.
- **Deploying to Andy's game:** copy `build/libs/forja-1.0.0.jar` to `C:\Users\andye\AppData\Roaming\ModrinthApp\profiles\forja\mods\` ONLY when Minecraft is closed. Check this with PowerShell; it must print 0:
  `@(Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" | Where-Object { $_.CommandLine -match 'ModrinthApp' }).Count`
- **No existing worlds:** never add migration or backward-compatibility code.
- **Mob AI:** rule changes must be written down in `docs/red_mob_v4_mod_estado.md`, because a separate training chat mirrors them in its simulator.
