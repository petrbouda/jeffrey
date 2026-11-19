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
# Builds a self-contained Microscope distribution: a jlinked JDK runtime bundled
# with microscope.jar and a launcher script. The output is a .tar.gz archive per
# platform, ready for a GitHub release.
#
# Usage: build-jlink-runtime.sh --jar <path> --version <ver> --output <dir>
#
# Requires: jlink and jdeps on PATH (from the target platform's JDK 25+).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

JAR=""
VERSION=""
OUTPUT_DIR=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --jar)     JAR="$2"; shift 2 ;;
        --version) VERSION="$2"; shift 2 ;;
        --output)  OUTPUT_DIR="$2"; shift 2 ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
    esac
done

if [[ -z "${JAR}" || -z "${VERSION}" || -z "${OUTPUT_DIR}" ]]; then
    echo "Usage: $0 --jar <path-to-microscope.jar> --version <version> --output <dir>" >&2
    exit 1
fi

JAR="$(cd "$(dirname "${JAR}")" && echo "$(pwd)/$(basename "${JAR}")")"
if [[ ! -f "${JAR}" ]]; then
    echo "error: ${JAR} does not exist" >&2
    exit 1
fi

# Detect platform and architecture for the archive name.
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
case "${OS}" in
    linux)  PLATFORM="linux" ;;
    darwin) PLATFORM="macos" ;;
    mingw*|msys*) PLATFORM="windows" ;;
    *)      echo "error: unsupported OS: ${OS}" >&2; exit 1 ;;
esac

MACHINE="$(uname -m)"
case "${MACHINE}" in
    x86_64)  ARCH="amd64" ;;
    aarch64|arm64) ARCH="arm64" ;;
    *)       echo "error: unsupported architecture: ${MACHINE}" >&2; exit 1 ;;
esac

ARCHIVE_NAME="microscope-${VERSION}-${PLATFORM}-${ARCH}"
DIST_DIR="${OUTPUT_DIR}/${ARCHIVE_NAME}"

echo "Building Microscope runtime: ${ARCHIVE_NAME}"
echo "  JAR:      ${JAR}"
echo "  Platform: ${PLATFORM}-${ARCH}"

# 1. Discover required JDK modules.
echo ""
MODULES=$(bash "${SCRIPT_DIR}/jlink-modules.sh" "${JAR}")

# 2. Build the custom runtime with jlink.
echo ""
echo "Running jlink..."
rm -rf "${DIST_DIR}"
mkdir -p "${DIST_DIR}"

jlink \
    --add-modules "${MODULES}" \
    --output "${DIST_DIR}/runtime" \
    --no-header-files \
    --no-man-pages \
    --strip-debug \
    --compress zip-6

echo "Runtime size: $(du -sh "${DIST_DIR}/runtime" | cut -f1)"

# 3. Assemble the distribution layout.
mkdir -p "${DIST_DIR}/bin" "${DIST_DIR}/lib"
cp "${JAR}" "${DIST_DIR}/lib/microscope.jar"
if [[ "${PLATFORM}" == "windows" ]]; then
    cp "${SCRIPT_DIR}/microscope-launcher.cmd" "${DIST_DIR}/bin/microscope.cmd"
else
    cp "${SCRIPT_DIR}/microscope-launcher.sh" "${DIST_DIR}/bin/microscope"
    chmod +x "${DIST_DIR}/bin/microscope"
fi

if [[ -f "${REPO_ROOT}/LICENSE" ]]; then
    cp "${REPO_ROOT}/LICENSE" "${DIST_DIR}/"
fi
if [[ -f "${REPO_ROOT}/NOTICE" ]]; then
    cp "${REPO_ROOT}/NOTICE" "${DIST_DIR}/"
fi

# 4. Create the tarball.
echo ""
if [[ "${PLATFORM}" == "windows" ]]; then
    ARCHIVE_FILE="${ARCHIVE_NAME}.zip"
    echo "Packaging ${ARCHIVE_FILE}..."
    WIN_SRC="$(cygpath -w "${OUTPUT_DIR}/${ARCHIVE_NAME}")"
    WIN_DST="$(cygpath -w "${OUTPUT_DIR}/${ARCHIVE_FILE}")"
    powershell.exe -NoProfile -Command "Compress-Archive -Path '${WIN_SRC}' -DestinationPath '${WIN_DST}'"
else
    ARCHIVE_FILE="${ARCHIVE_NAME}.tar.gz"
    echo "Packaging ${ARCHIVE_FILE}..."
    (cd "${OUTPUT_DIR}" && tar czf "${ARCHIVE_FILE}" "${ARCHIVE_NAME}")
fi
ARCHIVE_SIZE="$(du -sh "${OUTPUT_DIR}/${ARCHIVE_FILE}" | cut -f1)"

echo ""
echo "Done: ${OUTPUT_DIR}/${ARCHIVE_FILE} (${ARCHIVE_SIZE})"
