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

if ${IS_WINDOWS}; then
    LIB_CP="$(find "${LIB_DIR}" -name '*.jar' | cygpath -f - -w | paste -s -d ';' -)"
else
    LIB_CP="$(find "${LIB_DIR}" -name '*.jar' | paste -s -d ':' -)"
fi

# jdeps discovers platform module dependencies via static analysis. Its output
# is merged with the required-modules list below; on platforms where it works
# (Linux, macOS) it may add modules the list misses, while sort -u deduplicates.
echo "Running jdeps to discover platform module dependencies..." >&2
DISCOVERED_MODULES=$(jdeps \
    --multi-release 25 \
    --ignore-missing-deps \
    --print-module-deps \
    --class-path "${LIB_CP}" \
    "${CLASSES_DIR}" \
    "${LIB_DIR}"/*.jar \
    2>/dev/null) || true

# Every module the Microscope runtime needs. jdeps is unreliable on Windows
# (returns error text instead of module names), so we list all required modules
# explicitly rather than relying on its output. Categories:
#
# Spring Boot + embedded Tomcat:
#   java.desktop         — java.beans.PropertyEditorSupport (property binding)
#   java.instrument      — load-time weaving / agent support
#   java.logging         — Tomcat DirectJDKLog (java.util.logging)
#   java.management      — JMX (Actuator, Tomcat)
#   java.naming          — JNDI (Tomcat StandardServer)
#   java.security.jgss   — SPNEGO/Kerberos (security stack)
#   java.security.sasl   — SASL (security stack)
#   java.sql             — JDBC (DuckDB)
#   java.transaction.xa  — XA transactions (DataSource)
#   java.xml             — XML parsing (Spring context)
#
# Not discovered by jdeps (incubator, reflection, runtime-only):
#   jdk.incubator.vector — incubator module, explicitly required by the app
#   jdk.unsupported      — Netty, protobuf, etc. use sun.misc.Unsafe
#   jdk.jfr              — JFR event API, runtime-only usage paths
#   jdk.management       — Spring Boot Actuator, JMX platform beans
#   java.compiler        — annotation processing at runtime
REQUIRED_MODULES="java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.incubator.vector,jdk.jfr,jdk.management,jdk.unsupported"

ALL_MODULES=$(echo "${DISCOVERED_MODULES},${REQUIRED_MODULES}" \
    | tr ',' '\n' \
    | grep -E '^(java|jdk)\.' \
    | sort -u \
    | paste -s -d ',' -)

echo "Discovered modules: ${ALL_MODULES}" >&2
echo "${ALL_MODULES}"
