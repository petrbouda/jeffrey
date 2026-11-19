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

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

echo "Extracting ${FAT_JAR}..." >&2
mkdir -p "${WORK_DIR}/exploded"
(cd "${WORK_DIR}/exploded" && jar xf "$(cd / && realpath "${OLDPWD}/${FAT_JAR}")")

CLASSES_DIR="${WORK_DIR}/exploded/BOOT-INF/classes"
LIB_DIR="${WORK_DIR}/exploded/BOOT-INF/lib"

if [[ ! -d "${LIB_DIR}" ]]; then
    echo "error: ${FAT_JAR} does not look like a Spring Boot fat JAR (no BOOT-INF/lib)" >&2
    exit 1
fi

LIB_CP="$(find "${LIB_DIR}" -name '*.jar' | paste -sd ':')"

echo "Running jdeps to discover platform module dependencies..." >&2
DISCOVERED_MODULES=$(jdeps \
    --multi-release 25 \
    --ignore-missing-deps \
    --print-module-deps \
    --class-path "${LIB_CP}" \
    "${CLASSES_DIR}" \
    "${LIB_DIR}"/*.jar \
    2>/dev/null) || true

# Modules that jdeps cannot discover through static analysis:
#   jdk.incubator.vector — incubator module, explicitly required by the app
#   jdk.unsupported      — Netty, protobuf, etc. use sun.misc.Unsafe
#   jdk.jfr              — JFR event API, runtime-only usage paths
#   jdk.management       — Spring Boot Actuator, JMX
#   java.compiler        — annotation processing at runtime
#   java.instrument      — Spring's load-time weaving / agent support
EXTRA_MODULES="jdk.incubator.vector,jdk.unsupported,jdk.jfr,jdk.management,java.compiler,java.instrument"

ALL_MODULES=$(echo "${DISCOVERED_MODULES},${EXTRA_MODULES}" \
    | tr ',' '\n' \
    | sed '/^$/d' \
    | sort -u \
    | paste -sd ',')

echo "Discovered modules: ${ALL_MODULES}" >&2
echo "${ALL_MODULES}"
