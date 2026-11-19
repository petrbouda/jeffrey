#!/usr/bin/env bash
#
# Jeffrey Microscope launcher — uses the bundled JDK runtime.
# Override with JAVA_HOME to use your own JDK instead.
#
# Pass application arguments after the script name:
#   bin/microscope --version
#   bin/microscope --server.port=9090
#
# Extra JVM options (heap, GC, debug):
#   JAVA_OPTS="-Xmx4g" bin/microscope

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}" 2>/dev/null || echo "${BASH_SOURCE[0]}")")" && pwd)"
INSTALL_DIR="$(dirname "${SCRIPT_DIR}")"

if [[ -n "${JAVA_HOME:-}" ]] && [[ -x "${JAVA_HOME}/bin/java" ]]; then
    JAVA_BIN="${JAVA_HOME}/bin/java"
else
    JAVA_BIN="${INSTALL_DIR}/runtime/bin/java"
fi

if [[ ! -x "${JAVA_BIN}" ]]; then
    echo "error: JDK not found at ${JAVA_BIN}" >&2
    echo "       the archive may be incomplete; re-download from the GitHub release." >&2
    exit 1
fi

JAR="${INSTALL_DIR}/lib/microscope.jar"
if [[ ! -f "${JAR}" ]]; then
    echo "error: microscope.jar not found at ${JAR}" >&2
    exit 1
fi

exec "${JAVA_BIN}" \
    --add-modules jdk.incubator.vector \
    ${JAVA_OPTS:-} \
    -jar "${JAR}" \
    "$@"
