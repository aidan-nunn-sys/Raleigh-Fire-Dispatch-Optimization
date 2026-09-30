#!/usr/bin/env bash
# Compiles src + test at the Java 8 language level and runs JUnit test classes.
# With no arguments it runs every test in edu.ncsu.csc411.dispatch.
set -euo pipefail
cd "$(dirname "$0")/.."
P=/usr/lib/eclipse/plugins
CP="$P/org.junit_4.13.2.v20240929-1000.jar:$P/org.hamcrest_3.0.0.jar:$P/junit-jupiter-api_5.14.4.jar:$P/org.opentest4j_1.3.0.jar:$P/org.apiguardian.api_1.1.2.jar"
OUT=/tmp/ps3build
rm -rf "$OUT"
mkdir -p "$OUT"
javac --release 8 -encoding UTF-8 -nowarn -cp "$CP" -d "$OUT" $(find src test -name '*.java')
if [ $# -eq 0 ]; then
	set -- $(cd test && find edu/ncsu/csc411/dispatch -name '*Test.java' | sed 's/\.java$//; s#/#.#g' | sort)
fi
java -Djava.awt.headless=true -cp "$OUT:$CP" org.junit.runner.JUnitCore "$@"
