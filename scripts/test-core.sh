#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_classes="$(mktemp -d "${TMPDIR:-/tmp}/survivalplus-core-tests.XXXXXX")"
trap 'rm -rf -- "$test_classes"' EXIT

if command -v javac >/dev/null 2>&1; then
  compiler=(javac)
elif java -m jdk.compiler/com.sun.tools.javac.Main -version >/dev/null 2>&1; then
  compiler=(java -m jdk.compiler/com.sun.tools.javac.Main)
else
  printf '%s\n' 'Compilador Java 21+ necessário para estes testes sem dependências.' >&2
  exit 1
fi

# Some cloud runtimes retain jdk.compiler but omit ct.sym, so --release is unavailable.
# This isolated suite runs on its own Java runtime; production keeps Maven's Java 25 target.
"${compiler[@]}" -source 21 -target 21 -encoding UTF-8 -d "$test_classes" \
  "$project_root/src/main/java/br/com/survivalplus/core/portal/Bounds.java" \
  "$project_root/src/main/java/br/com/survivalplus/core/portal/PortalGeometry.java" \
  "$project_root/src/test/java/br/com/survivalplus/core/portal/PortalGeometryTest.java"
java -cp "$test_classes" br.com.survivalplus.core.portal.PortalGeometryTest
