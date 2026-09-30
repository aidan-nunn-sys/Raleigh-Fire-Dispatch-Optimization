#!/usr/bin/env bash
# Compiles src at the Java 8 language level and opens the Raleigh dispatch window.
# Pass a main class to run something else, e.g.
#   bash scripts/run-dispatch.sh edu.ncsu.csc411.ps03.dispatch.benchmark.DispatchBenchmark
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=/tmp/ps3app
rm -rf "$OUT"
mkdir -p "$OUT"
javac --release 8 -encoding UTF-8 -nowarn -d "$OUT" $(find src -name '*.java')
java -cp "$OUT" "${1:-edu.ncsu.csc411.ps03.dispatch.ui.DispatchVisualizer}"
