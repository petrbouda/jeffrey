#!/usr/bin/env bash
#
# Jeffrey
# Copyright (C) 2026 Petr Bouda
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Discovers the JDK platform modules required by the Microscope fat JAR.
# Outputs a sorted, comma-separated module list suitable for jlink --add-modules.
#
# Usage: jlink-modules.sh <path-to-microscope.jar>

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <path-to-microscope.jar>" >&2
    exit 1
fi

FAT_JAR="$1"
if [[ ! -f "${FAT_JAR}" ]]; then
    echo "error: ${FAT_JAR} does not exist" >&2
    exit 1
fi
FAT_JAR="$(cd "$(dirname "${FAT_JAR}")" && echo "$(pwd)/$(basename "${FAT_JAR}")")"

OS_LOWER="$(uname -s | tr '[:upper:]' '[:lower:]')"
IS_WINDOWS=false
case "${OS_LOWER}" in mingw*|msys*) IS_WINDOWS=true ;; esac

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

echo "Extracting ${FAT_JAR}..." >&2
mkdir -p "${WORK_DIR}/exploded"
(cd "${WORK_DIR}/exploded" && jar xf "${FAT_JAR}")

CLASSES_DIR="${WORK_DIR}/exploded/BOOT-INF/classes"
LIB_DIR="${WORK_DIR}/exploded/BOOT-INF/lib"

if [[ ! -d "${LIB_DIR}" ]]; then
    echo "error: ${FAT_JAR} does not look like a Spring Boot fat JAR (no BOOT-INF/lib)" >&2
    exit 1
fi

# jdeps.exe on Windows cannot read MSYS POSIX paths; convert everything with cygpath.
if ${IS_WINDOWS}; then
    LIB_CP="$(find "${LIB_DIR}" -name '*.jar' | cygpath -f - -w | paste -s -d ';' -)"
    JDEPS_CLASSES="$(cygpath -w "${CLASSES_DIR}")"
    JDEPS_JARS=()
    for jar in "${LIB_DIR}"/*.jar; do
        JDEPS_JARS+=("$(cygpath -w "$jar")")
    done
else
    LIB_CP="$(find "${LIB_DIR}" -name '*.jar' | paste -s -d ':' -)"
    JDEPS_CLASSES="${CLASSES_DIR}"
    JDEPS_JARS=("${LIB_DIR}"/*.jar)
fi

echo "Running jdeps to discover platform module dependencies..." >&2
DISCOVERED_MODULES=$(jdeps \
    --multi-release 25 \
    --ignore-missing-deps \
    --print-module-deps \
    --class-path "${LIB_CP}" \
    "${JDEPS_CLASSES}" \
    "${JDEPS_JARS[@]}" \
    2>/dev/null) || true

# jdeps can silently fail on Windows (path handling, command-line length limits).
# When it discovers nothing, fall back to the standard set of modules that a
# Spring Boot + embedded Tomcat application needs.
if [[ -z "${DISCOVERED_MODULES}" ]]; then
    echo "Warning: jdeps discovered no modules; using Spring Boot baseline" >&2
    DISCOVERED_MODULES="java.base,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto"
fi

# Modules that jdeps cannot discover through static analysis:
#   jdk.incubator.vector — incubator module, explicitly required by the app
#   jdk.unsupported      — Netty, protobuf, etc. use sun.misc.Unsafe
#   jdk.jfr              — JFR event API, runtime-only usage paths
#   jdk.management       — Spring Boot Actuator, JMX
#   java.compiler        — annotation processing at runtime
#   java.instrument      — Spring's load-time weaving / agent support
#   java.desktop         — java.beans.PropertyEditorSupport, used by Spring Boot property binding
#   java.logging         — Tomcat's DirectJDKLog uses java.util.logging directly
EXTRA_MODULES="jdk.incubator.vector,jdk.unsupported,jdk.jfr,jdk.management,java.compiler,java.instrument,java.desktop,java.logging"

ALL_MODULES=$(echo "${DISCOVERED_MODULES},${EXTRA_MODULES}" \
    | tr ',' '\n' \
    | grep -E '^(java|jdk)\.' \
    | sort -u \
    | paste -s -d ',' -)

echo "Discovered modules: ${ALL_MODULES}" >&2
echo "${ALL_MODULES}"
