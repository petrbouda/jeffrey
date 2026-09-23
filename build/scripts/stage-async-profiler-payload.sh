#!/usr/bin/env bash
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
# Stages async-profiler's libasyncProfiler.so for linux/amd64 and linux/arm64 into the
# jeffrey-jib-payload-jar and jeffrey-jib-payload-native modules (both flavours carry it), ready
# for `mvn -P payload package`.
#
# The version is the async-profiler.version property of jeffrey-jib/pom.xml. It is
# pinned on purpose: this library is loaded into every profiled JVM, so which version ships is a
# decision recorded in the pom, never whatever happened to be the latest release on the day of
# the build. Downloads use `curl -f`, so a missing release or a rate-limited endpoint fails the
# build instead of staging an empty archive.
#
# Usage: build/scripts/stage-async-profiler-payload.sh [path/to/jeffrey-jib]
set -euo pipefail

JIB_DIR="${1:-$(cd "$(dirname "$0")/../.." && pwd)/jeffrey-jib}"
PAYLOAD_MODULES="jeffrey-jib-payload-jar jeffrey-jib-payload-native"

AP_VERSION=$(mvn -q -N -f "${JIB_DIR}/pom.xml" -DforceStdout help:evaluate -Dexpression=async-profiler.version)
if [[ ! "${AP_VERSION}" =~ ^[0-9]+\.[0-9]+ ]]; then
  echo "ERROR: could not read async-profiler.version from ${JIB_DIR}/pom.xml (got '${AP_VERSION}')" >&2
  exit 1
fi
echo "Staging async-profiler ${AP_VERSION}"

WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

# async-profiler names its x86_64 archives "x64"; the payload file names follow the OCI
# platform names JIB uses (libasyncProfiler-linux-<arch>.so).
for AP_ARCH_PAIR in "x64:amd64" "arm64:arm64"; do
  AP_ARCH="${AP_ARCH_PAIR%%:*}"
  OUT_ARCH="${AP_ARCH_PAIR##*:}"
  ARCHIVE="async-profiler-${AP_VERSION}-linux-${AP_ARCH}"
  curl -fsSL --retry 3 \
    "https://github.com/async-profiler/async-profiler/releases/download/v${AP_VERSION}/${ARCHIVE}.tar.gz" \
    | tar -xz -C "${WORK_DIR}"
  for MODULE in ${PAYLOAD_MODULES}; do
    install -Dm644 "${WORK_DIR}/${ARCHIVE}/lib/libasyncProfiler.so" \
      "${JIB_DIR}/${MODULE}/src/main/payload/jeffrey-payload/libasyncProfiler-linux-${OUT_ARCH}.so"
  done
done

for MODULE in ${PAYLOAD_MODULES}; do
  ls -la "${JIB_DIR}/${MODULE}/src/main/payload/jeffrey-payload/"
done
