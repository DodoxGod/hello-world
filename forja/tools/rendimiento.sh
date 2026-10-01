#!/usr/bin/env bash
# Forja's server benchmark (docs/RENDIMIENTO.md): runs only BenchmarkGameTests on the gametest server
# and writes the report to build/rendimiento/<name>/informe.md (plus resumen.csv and one .jfr per window).
#
#   tools/rendimiento.sh [name] [networks folder]
#
# With a networks folder (red_<family>.json files), the hordes and the boss are also measured with them.
set -euo pipefail
cd "$(dirname "$0")/.."
name="${1:-$(date +%Y%m%d_%H%M%S)}"
export FORJA_RENDIMIENTO="$(pwd)/build/rendimiento/$name"
if [ -n "${2:-}" ]; then
	export FORJA_REDES="$(cd "$2" && pwd)"
fi
# The filter reaches the game's JVM through the environment: the Gradle daemon passes it on to runGameTest.
export JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=forja-test:benchmark_game_tests_hordes_and_smith"
./gradlew runGameTest --console=plain -q
echo "Informe: $FORJA_RENDIMIENTO/informe.md"
